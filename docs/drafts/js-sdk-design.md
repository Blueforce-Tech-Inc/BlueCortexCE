# JavaScript/TypeScript Client SDK Design Document

> **Version**: v1.0 DRAFT
> **Date**: 2026-03-27
> **Status**: Under Development
> **Author**: Cortex CE Team

---

## Executive Summary

### Core Decisions

1. **Zero forced dependencies** — Core package uses only the fetch API (built-in for Node 18+ and all modern browsers)
2. **Idiomatic TypeScript** — interfaces, optional chaining, async/await, generics
3. **npm-publishable** — Dual CJS + ESM output via `tsup`
4. **Type-safe** — Complete TypeScript type definitions for all DTOs
5. **Go/Java SDK equivalent** — Covers all 26 API methods from the Go SDK

### Directory Structure

```
@cortex-mem/js-sdk/
├── package.json
├── tsconfig.json
├── tsup.config.ts
├── src/
│   ├── index.ts              # Public API exports
│   ├── client.ts             # CortexMemClient interface + implementation
│   ├── client-options.ts     # ClientOptions type and defaults
│   ├── errors.ts             # Error types (APIError, error predicates)
│   ├── dto/
│   │   ├── index.ts          # DTO barrel export
│   │   ├── wire-helpers.ts   # Shared wire-format coercion helpers
│   │   ├── session.ts        # SessionStartRequest/Response, SessionEndRequest
│   │   ├── observation.ts    # ObservationRequest, ObservationUpdate, Observation
│   │   ├── experience.ts     # ExperienceRequest, Experience, ICLPromptRequest/Result
│   │   ├── search.ts         # SearchRequest, SearchResult
│   │   ├── management.ts     # QualityDistribution, FeedbackRequest, BatchObservations
│   │   ├── extraction.ts     # ExtractionResult
│   │   └── misc.ts           # VersionResponse, ProjectsResponse, StatsResponse, etc.
│   └── __tests__/
│       ├── client.test.ts    # Unit tests with vitest
│       └── truncated-body.test.ts
├── examples/
│   ├── basic.ts              # Basic usage example
│   └── http-server/          # Demo REST server mirroring the Go/Python demos
├── README.md
```

There is no `CHANGELOG.md`; release history is tracked in git.

## Design Principles

### 1. Zero Forced Dependencies

The SDK uses only the global `fetch` API, available in:
- Node.js 18+ (built-in)
- All modern browsers
- Deno, Bun, Cloudflare Workers

No `node-fetch`, `axios`, or other HTTP libraries required.

For Node.js < 18, users can polyfill with `node-fetch` or `undici`.

### 2. Idiomatic TypeScript

```typescript
// Interfaces for DTOs (not classes)
interface SessionStartRequest {
  session_id: string;
  project_path: string;
  user_id?: string;
}

// Async/await for all operations
const session = await client.startSession({
  session_id: 'my-session',
  project_path: '/path/to/project',
});

// Optional chaining for responses
const prompt = result?.prompt ?? '';
```

### 3. Dual CJS + ESM Output

```json
{
  "exports": {
    ".": {
      "types": "./dist/index.d.ts",
      "import": "./dist/index.mjs",
      "require": "./dist/index.js"
    }
  },
  "main": "./dist/index.js",
  "module": "./dist/index.mjs",
  "types": "./dist/index.d.ts"
}
```

`tsup` is configured with `format: ['cjs', 'esm']`. Because `package.json` has no
`"type": "module"`, the CJS output is named `index.js`, not `index.cjs` — matching
`main`/`require` above.

### 4. Error Handling

Unlike the Go SDK (which returns `error`), the JS SDK throws exceptions:

```typescript
try {
  await client.startSession(req);
} catch (err) {
  if (isNotFound(err)) { /* 404 */ }
  if (isRateLimited(err)) { /* 429 */ }
}
```

This aligns with JavaScript/TypeScript conventions (try/catch) rather than Go conventions (error return values).

### 5. Fire-and-Forget for Capture Operations

Capture operations (RecordObservation, RecordSessionEnd, RecordUserPrompt) use fire-and-forget:
- Internal retry with linear backoff + jitter
- Failures are logged, not thrown
- Matches Go SDK behavior

## API Method Mapping (26 methods)

| # | Method | HTTP | Endpoint |
|---|--------|------|----------|
| 1 | `startSession` | POST | `/api/session/start` |
| 2 | `updateSessionUserId` | PATCH | `/api/session/{sessionId}/user` |
| 3 | `recordObservation` | POST | `/api/ingest/tool-use` |
| 4 | `recordSessionEnd` | POST | `/api/ingest/session-end` |
| 5 | `recordUserPrompt` | POST | `/api/ingest/user-prompt` |
| 6 | `retrieveExperiences` | POST | `/api/memory/experiences` |
| 7 | `buildICLPrompt` | POST | `/api/memory/icl-prompt` |
| 8 | `search` | GET | `/api/search` |
| 9 | `listObservations` | GET | `/api/observations` |
| 10 | `getObservationsByIds` | POST | `/api/observations/batch` |
| 11 | `getObservation` | POST | `/api/observations/batch` |
| 12 | `triggerRefinement` | POST | `/api/memory/refine` |
| 13 | `submitFeedback` | POST | `/api/memory/feedback` |
| 14 | `updateObservation` | PATCH | `/api/memory/observations/{id}` |
| 15 | `deleteObservation` | DELETE | `/api/memory/observations/{id}` |
| 16 | `getQualityDistribution` | GET | `/api/memory/quality-distribution` |
| 17 | `healthCheck` | GET | `/api/health` |
| 18 | `triggerExtraction` | POST | `/api/extraction/run` |
| 19 | `getLatestExtraction` | GET | `/api/extraction/{template}/latest` |
| 20 | `getExtractionHistory` | GET | `/api/extraction/{template}/history` |
| 21 | `getVersion` | GET | `/api/version` |
| 22 | `getProjects` | GET | `/api/projects` |
| 23 | `getStats` | GET | `/api/stats` |
| 24 | `getModes` | GET | `/api/modes` |
| 25 | `getSettings` | GET | `/api/settings` |
| 26 | `close` | — | (cleanup idle connections) |

`getObservation(id)` is a convenience wrapper over `getObservationsByIds([id])`
that returns `null` instead of an empty batch. The backend has **no**
single-observation read endpoint — `GET /api/observations/{id}` answers **404** and
`GET /api/memory/observations/{id}` answers **405** (that path is PATCH/DELETE
only) — so the batch call is the only way to fetch one.

Counting note: 25 of these are HTTP-backed plus `close()`, giving 26. The client
also exposes four logger methods (`debug`/`info`/`warn`/`error`) and `toString()`;
those are not part of the API surface.

## Wire Format Notes

Critical wire format details (inherited from Go SDK):

- `SessionStartRequest`: uses `project_path` (NOT `cwd`)
- `SessionEndRequest`, `UserPromptRequest`, `ObservationRequest`: uses `cwd` (NOT `project_path`)
- `ExperienceRequest`: `requiredConcepts`, `userId` are camelCase
- `ICLPromptRequest`: `maxChars`, `userId` are camelCase
- `ObservationRequest.extractedData`: camelCase
- `ObservationUpdate.extractedData`: camelCase
- `Experience`: all fields camelCase (`qualityScore`, `reuseCondition`)
- Search/ListObservations: GET with query params (not POST body)
- Observations batch: POST with `ids` array
- `triggerRefinement`: uses query param `project` (not body)
- `getQualityDistribution`: uses query param `project` (not body)
- `FeedbackRequest`: `observationId`, `feedbackType` are camelCase

## Quick Start

### Installation

```bash
npm install @cortex-mem/js-sdk
```

### 30-Second Example

```typescript
import { CortexMemClient } from '@cortex-mem/js-sdk';

const client = new CortexMemClient({
  baseURL: 'http://localhost:37777',
  timeout: 10000,
});

// Start session. Keep the session_id you chose — the response does not echo it
// back, so it is not readable from the returned object.
const SESSION_ID = 'my-session-001';
const session = await client.startSession({
  session_id: SESSION_ID,
  project_path: '/path/to/project',
});
// session.session_db_id, session.context, session.prompt_number

// Record observation (fire-and-forget)
await client.recordObservation({
  session_id: SESSION_ID,
  cwd: '/path/to/project',
  tool_name: 'Read',
  tool_input: { file: 'main.go' },
});

// Retrieve experiences
const experiences = await client.retrieveExperiences({
  task: 'How to handle errors in Go?',
  project: '/path/to/project',
  count: 3,
});

// Build ICL prompt
const icl = await client.buildICLPrompt({
  task: 'How to handle errors in Go?',
  project: '/path/to/project',
});

// End session
await client.recordSessionEnd({
  session_id: SESSION_ID,
  cwd: '/path/to/project',
});

client.close();
```

## Version Strategy

| Package | Version |
|---------|---------|
| `@cortex-mem/js-sdk` | v1.0.0 |
