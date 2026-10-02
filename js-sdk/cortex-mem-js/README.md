> 中文版: [README-zh-CN.md](./README-zh-CN.md)

# @cortex-mem/js-sdk

JavaScript/TypeScript client SDK for the [Cortex CE](https://github.com/Blueforce-Tech-Inc/BlueCortexCE) memory system.

## Features

- **Zero runtime dependencies** — Uses the built-in `fetch` API (Node 18+, browsers, Deno, Bun)
- **Full TypeScript support** — Complete type definitions for all DTOs
- **25 API methods** — Covers all endpoints from the Go/Java SDKs
- **224 unit tests** — Full coverage of wire format and client behavior
- **Dual CJS + ESM** — Works with CommonJS and ES Modules
- **Best-effort capture** — Retries transient failures; capture errors are logged and swallowed after retries

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
// session.response exposes session_db_id, context and prompt_number
// (session_id and updateFiles from the wire are not part of this SDK type).

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
| `baseURL` | `http://127.0.0.1:37777` | Backend URL |
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
