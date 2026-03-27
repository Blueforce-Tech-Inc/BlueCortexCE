# @cortex-mem/js-sdk

JavaScript/TypeScript client SDK for the [Cortex CE](https://github.com/abforce/cortex-ce) memory system.

## Features

- **Zero dependencies** — Uses the built-in `fetch` API (Node 18+, browsers, Deno, Bun)
- **Full TypeScript support** — Complete type definitions for all DTOs
- **26 API methods** — Covers all endpoints from the Go/Java SDKs
- **Dual CJS + ESM** — Works with CommonJS and ES Modules
- **Fire-and-forget capture** — Non-blocking observation recording with internal retry

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

// Start session
const session = await client.startSession({
  session_id: 'my-session',
  project_path: '/path/to/project',
});

// Record observation (fire-and-forget)
await client.recordObservation({
  session_id: session.session_id,
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
  session_id: session.session_id,
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
| `maxRetries` | `3` | Max retries for fire-and-forget ops |
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
| `getObservationsByIds(ids)` | `POST /api/observations/batch` | Batch get by IDs |

#### Management

| Method | HTTP | Description |
|--------|------|-------------|
| `triggerRefinement(projectPath)` | `POST /api/memory/refine` | Trigger memory refinement |
| `submitFeedback(id, type, comment?)` | `POST /api/memory/feedback` | Submit observation feedback |
| `updateObservation(id, update)` | `PATCH /api/memory/observations/{id}` | Update observation |
| `deleteObservation(id)` | `DELETE /api/memory/observations/{id}` | Delete observation |
| `getQualityDistribution(project)` | `GET /api/memory/quality-distribution` | Get quality stats |

#### Extraction

| Method | HTTP | Description |
|--------|------|-------------|
| `triggerExtraction(project)` | `POST /api/extraction/run` | Trigger extraction |
| `getLatestExtraction(project, template, userId?)` | `GET /api/extraction/{template}/latest` | Latest extraction |
| `getExtractionHistory(project, template, userId?, limit?)` | `GET /api/extraction/{template}/history` | Extraction history |

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
