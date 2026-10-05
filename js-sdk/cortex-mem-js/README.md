> 中文版: [README-zh-CN.md](./README-zh-CN.md)

# @cortex-mem/js-sdk

JavaScript/TypeScript client SDK for the [Cortex CE](https://github.com/Blueforce-Tech-Inc/BlueCortexCE) memory system.

## Features

- **Zero runtime dependencies** — Uses the built-in `fetch` API (Node 18+, browsers, Deno, Bun)
- **Full TypeScript support** — Complete type definitions for all DTOs
- **25 API methods** — Covers all endpoints from the Go/Java SDKs
- **259 unit tests** — Full coverage of wire format and client behavior (246 client + 5 truncated-body + 8 http-server example)
- **Dual CJS + ESM** — Works with CommonJS and ES Modules
- **Best-effort capture** — Retries transient failures; capture errors are swallowed after retries, and reach your logs only if you supply a `logger` — the default one is a no-op

## Installation

```bash
npm install @cortex-mem/js-sdk
```

## Quick Start

```typescript
import { CortexMemClient } from '@cortex-mem/js-sdk';

const client = new CortexMemClient({
  baseURL: 'http://localhost:37777',
  timeout: 10_000,
});

// Start session — keep session_id from the request to use in subsequent calls
const SESSION_ID = 'my-session';
const session = await client.startSession({
  session_id: SESSION_ID,
  project_path: '/path/to/project',
});
// The three response fields are top-level on the returned object — there is
// no nested `response` property:
//   session.session_db_id, session.context, session.prompt_number
// (session_id and updateFiles also arrive on the wire but are not part of the
// SessionStartResponse type.)

// Record observation (fire-and-forget)
await client.recordObservation({
  session_id: SESSION_ID, // reuse the session_id you already have
  cwd: '/path/to/project',
  tool_name: 'Read',
  tool_input: { file: 'main.go' },
});

// Retrieve experiences
const experiences = await client.retrieveExperiences({
  task: 'How to parse JSON?',
  project: '/path/to/project',
  count: 3,
});

// Build ICL prompt
const icl = await client.buildICLPrompt({
  task: 'How to parse JSON?',
  project: '/path/to/project',
});

// Search
const results = await client.search({
  project: '/path/to/project',
  query: 'JSON parsing',
  limit: 5,
});

// End session
await client.recordSessionEnd({
  session_id: SESSION_ID,
  cwd: '/path/to/project',
});

client.close();
```

## API Reference

### Client Options

| Option | Default | Description |
|--------|---------|-------------|
| `baseURL` | `http://127.0.0.1:37777` | Backend URL. A **path prefix is kept**: `'http://host/memory'` sends requests to `http://host/memory/api/…`, matching the Go, Python and Java SDKs. This is what you need behind a reverse proxy or a path-based gateway. Trailing slashes are stripped. |
| `apiKey` | — | Bearer token for auth |
| `timeout` | `30000` | Request timeout (ms) |
| `maxRetries` | `3` | Total **attempts** for fire-and-forget ops (3 = 3 requests, i.e. 2 retries) |
| `retryBackoff` | `500` | Base retry backoff (ms) |
| `logger` | no-op | Custom logger |
| `fetch` | global `fetch` | Custom fetch implementation |
| `headers` | `{}` | Extra request headers |

### Methods

#### Session

| Method | HTTP | Description |
|--------|------|-------------|
| `startSession(req)` | `POST /api/session/start` | Start or resume session |
| `updateSessionUserId(sessionId, userId)` | `PATCH /api/session/{id}/user` | Update session user |

#### Capture (fire-and-forget)

| Method | HTTP | Description |
|--------|------|-------------|
| `recordObservation(req)` | `POST /api/ingest/tool-use` | Record tool-use observation |
| `recordSessionEnd(req)` | `POST /api/ingest/session-end` | Signal session end |
| `recordUserPrompt(req)` | `POST /api/ingest/user-prompt` | Record user prompt |

#### Retrieval

| Method | HTTP | Description |
|--------|------|-------------|
| `retrieveExperiences(req)` | `POST /api/memory/experiences` | Retrieve relevant experiences |
| `buildICLPrompt(req)` | `POST /api/memory/icl-prompt` | Build ICL prompt |
| `search(req)` | `GET /api/search` | Semantic search |
| `listObservations(req)` | `GET /api/observations` | List observations (paginated) |
| `getObservation(id)` | `POST /api/observations/batch` | Get single observation by ID (returns `null` if not found) |
| `getObservationsByIds(ids)` | `POST /api/observations/batch` | Batch get by IDs |

#### Management

| Method | HTTP | Description |
|--------|------|-------------|
| `triggerRefinement(projectPath)` | `POST /api/memory/refine` | Trigger memory refinement |
| `submitFeedback(req)` | `POST /api/memory/feedback` | Submit observation feedback (`FeedbackRequest`) |
| `updateObservation(id, update)` | `PATCH /api/memory/observations/{id}` | Update observation |
| `deleteObservation(id)` | `DELETE /api/memory/observations/{id}` | Delete observation |
| `getQualityDistribution(project)` | `GET /api/memory/quality-distribution` | Get quality stats |

#### Extraction

| Method | HTTP | Description |
|--------|------|-------------|
| `triggerExtraction(project)` | `POST /api/extraction/run` | Trigger extraction |
| `getLatestExtraction(projectPath, templateName, userId?)` | `GET /api/extraction/{templateName}/latest` | Latest extraction |
| `getExtractionHistory(projectPath, templateName, userId?, limit?)` | `GET /api/extraction/{templateName}/history` | Extraction history |

#### System

| Method | HTTP | Description |
|--------|------|-------------|
| `healthCheck()` | `GET /api/health` | Health check |
| `getVersion()` | `GET /api/version` | Backend version |
| `getProjects()` | `GET /api/projects` | List projects |
| `getStats(project?)` | `GET /api/stats` | Statistics |
| `getModes()` | `GET /api/modes` | Mode settings |
| `getSettings()` | `GET /api/settings` | Current settings |
| `close()` | — | Close client |
| `toString()` | — | Debug representation for logging |

### Error Handling

```typescript
import { CortexMemClient, APIError, isNotFound, isRateLimited } from '@cortex-mem/js-sdk';

try {
  await client.startSession({ session_id: '', project_path: '/tmp' });
} catch (err) {
  if (err instanceof APIError) {
    console.error(`HTTP ${err.statusCode}: ${err.message}`);
  }
  if (isNotFound(err)) { /* 404 */ }
  if (isRateLimited(err)) { /* 429 — retry after delay */ }
}
```

### Response Size Limit

Responses larger than **10 MiB** (`10 * 1024 * 1024`) are rejected:

```
cortex-ce: response body exceeds 10MB limit
```

(The message says `10MB`; the cap is 10 MiB — 10,485,760 bytes.)

Two checks guard the body, both throwing a plain `Error` (not an `APIError`, since
the failure is local rather than an HTTP status):

1. **Before reading** — a declared `Content-Length` above the cap throws without
   the body being buffered at all.
2. **After reading** — a backstop for servers that omit the header.

The second check counts the body's **UTF-8 byte length**, not the string's
character count, so a multi-byte body cannot slip past on a code-unit count.
(This was previously compared with `String.length`, which counts UTF-16 code
units — a 15 MB body measured 5.2 M code units and was accepted.) The count is
computed by walking the string rather than with `TextEncoder`, which would
allocate a second buffer as large as the body.

This is a guard, not a streaming reader: the body is still materialized as a
single string.

### Empty Updates Are Rejected

`updateObservation` throws a `ValidationError` when the update sets no field, and no
request is sent. The same rule and message apply in the Go, Java and Python SDKs:

```
cortex-ce: validation error on update: at least one field must be provided for update
```

It matters because a PATCH that sets nothing is a silent no-op on the wire: without
the check, a caller who assembled an empty update from user input would see the call
resolve and could not tell that nothing was written.

### Three States Per Field: Skip, Set, Clear

`ObservationUpdate` gives every field three distinct states, and all three are spelled
out in the type — omit the key, pass a value, or pass `null`:

```ts
// skip: subtitle is left alone (the key is never sent)
await client.updateObservation(id, { title: 'New title' });

// set
await client.updateObservation(id, { concepts: ['auth'] });

// clear: stored as SQL NULL
await client.updateObservation(id, { source: null, extractedData: null });
```

`null` is a value, not an absent field, so every field is typed `T | null` rather than
merely optional. Under this package's `"strict": true` the narrower `T | undefined`
spelling made the clear semantic unreachable from TypeScript — a caller had to write
`null as unknown as string` to reach behaviour the client already implemented and the
backend already honours. The backend stores SQL NULL for all seven clearable fields.

This SDK is the only one of the four that can clear a string field to NULL: Java omits
nulls via `@JsonInclude(NON_NULL)`, Go via `omitempty`, and Python skips `None`. For
the `facts` and `concepts` lists, `[]` and `null` are different outcomes — `[]` stores an
empty array, `null` stores NULL — and both reach the wire, because `JSON.stringify` has
no `omitempty`.

### Required Arguments Are Checked Client-Side

Every argument below must be non-empty. The SDK throws a `ValidationError` and sends no
request. The Go, Java and Python SDKs enforce exactly the same set. This SDK names the
project argument `cwd` because that is the wire field; the other three call it
`project_path`.

| Method | Required arguments |
|--------|--------------------|
| `startSession` | `req.session_id`, `req.project_path` |
| `updateSessionUserId` | `sessionId`, `userId` |
| `recordObservation` | `req.session_id`, `req.cwd`, `req.tool_name` |
| `recordSessionEnd` | `req.session_id`, `req.cwd` |
| `recordUserPrompt` | `req.session_id`, `req.prompt_text`, `req.cwd` |
| `retrieveExperiences` | `req.task` |
| `buildICLPrompt` | `req.task` |
| `search` | `req.project` |
| `getObservation` | `id` |
| `getObservationsByIds` | `ids` — non-empty, at most 100, no blank element |
| `triggerRefinement` | `projectPath` |
| `submitFeedback` | `req.observationId`, `req.feedbackType` |
| `updateObservation` | `observationId`, plus at least one field to change |
| `deleteObservation` | `observationId` |
| `getQualityDistribution` | `projectPath` |
| `triggerExtraction` | `projectPath` |
| `getLatestExtraction` | `projectPath`, `templateName` |
| `getExtractionHistory` | `projectPath`, `templateName`; `limit` must not be negative |

The project argument is `cwd` on the three capture request objects because that is
the wire field there, and a positional `projectPath` on the management methods. The
other three SDKs call it `project_path` throughout.

The checks are not decoration, and the capture methods are the clearest case.
`recordObservation` is fire-and-forget, so it swallows whatever the backend replies:
an empty `tool_name` comes back as `400 Missing required field: tool_name`, the SDK
logs it and resolves, and the caller concludes the observation was captured when the
server had just rejected it. An empty `cwd` is quieter still, because the backend
*accepts* it — the record is queued against no project and then appears in no
project-scoped query, with no error anywhere.

`search` has the same shape of hazard: the SDK always sends `project`, and
`GET /api/search?project=` answers `200` with an empty result set, so a caller who
forgot the argument would read "no matches" rather than "your call was malformed".

`retrieveExperiences` and `buildICLPrompt` are the one place where the table above
is not a complete guide, because the hazard is on the *other* field. Both accept
`req.project` and neither validates it, while `search` does validate its project.
For these two that is the wrong way round: `POST /api/memory/experiences` and
`POST /api/memory/icl-prompt` pass the value straight into the repository query
with no cross-project branch, so a missing or empty project matches nothing and
returns `200` with an empty result instead of an error. Verified live: omitting
`project`, sending `""`, and sending a non-existent path all return `200 []`,
while a real path returns experiences. Since `project` is optional in the
TypeScript request type, omitting it type-checks fine and silently searches
nothing.

`listObservations` and the argument-free getters (`getStats`, `getProjects`,
`getModes`, `getSettings`, `getVersion`, `healthCheck`) require nothing. `getStats`
takes an optional project filter.

That is a statement about required arguments, not about blast radius, and on
this one method the two point in opposite directions. `listObservations` is the
only retrieval method whose project filter **widens** instead of emptying: omit
`project` — and pass an empty string if you like, because all four SDKs convert
that to omission — and no `project` is sent at all, so the backend answers with
observations from **every project on the instance**. Verified live against a
populated backend: `GET /api/observations` with no `project` returned 16
distinct projects inside a single 100-item page. A *direct* HTTP call with a
literal `?project=` is the opposite case and returns nothing, because the
repository query tests `IS NULL` rather than blank — worth knowing before you
bypass the SDK.

So this is the mirror image of the two ICL endpoints above: there a blank
project silently empties the result, here it silently over-broadens it. In a
multi-tenant deployment that is cross-tenant exposure rather than a missing
answer, and no client-side check will catch it, because the call is
well-formed.

## Wire Format

The SDK uses JSON field names that match the backend API exactly. Field naming varies by endpoint:

**Session:**
- `session_id`, `project_path` (snake_case) — `SessionStartRequest`
- `user_id` (snake_case) — optional in `SessionStartRequest`

**Observation (capture):**
- `session_id`, `cwd`, `tool_name` (snake_case) — `ObservationRequest`
- `extractedData` (camelCase) — backend `@JsonProperty` override

**Experience & ICL:**
- `requiredConcepts`, `userId` (camelCase) — `ExperienceRequest`, `ICLPromptRequest`

**Feedback:**
- `observationId`, `feedbackType` (camelCase) — `FeedbackRequest`

**Observation (read) — list columns come in more than one shape:**
`facts`, `concepts`, `filesRead`, `filesModified` and `refinedFromIds` are always
returned as `string[]`, whichever way they arrive on the wire — but they do not
arrive the same way, and the difference is worth knowing.

The first four are JSONB columns that the backend serializes as **JSON-encoded
strings** for the WebUI, so a live observation carries
`concepts: '["allergy","peanut"]'` rather than a JSON array. The parser decodes
both shapes; a string that is not valid JSON degrades to a comma-separated split.

`refinedFromIds` is not one of them. It is a `TEXT` column holding
comma-separated UUIDs (`"uuid-1,uuid-2"`) — nothing JSON-encodes it, the backend
joins the ids with `,` — so the comma-separated split is the only path that
applies to it.

See [JS SDK Design Document](../../docs/drafts/js-sdk-design.md) for architecture and implementation details.

## Development

```bash
# Install dependencies
npm install

# Build
npm run build

# Run tests
npm test

# Type check
npm run lint
```

## License

MIT
