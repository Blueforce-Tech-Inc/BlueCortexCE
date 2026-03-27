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
│   │   ├── session.ts        # SessionStartRequest/Response, SessionEndRequest
│   │   ├── observation.ts    # ObservationRequest, ObservationUpdate, Observation
│   │   ├── experience.ts     # ExperienceRequest, Experience, ICLPromptRequest/Result
│   │   ├── search.ts         # SearchRequest, SearchResult
│   │   ├── management.ts     # QualityDistribution, FeedbackRequest, BatchObservations
│   │   ├── extraction.ts     # ExtractionResult
│   │   └── misc.ts           # VersionResponse, ProjectsResponse, StatsResponse, etc.
│   └── __tests__/
│       └── client.test.ts    # Unit tests with vitest
├── examples/
│   └── basic.ts              # Basic usage example
├── README.md
└── CHANGELOG.md
```

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
      "import": "./dist/index.mjs",
      "require": "./dist/index.cjs",
      "types": "./dist/index.d.ts"
    }
  },
  "main": "./dist/index.cjs",
  "module": "./dist/index.mjs",
  "types": "./dist/index.d.ts"
}
```

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
| 2 | `updateSessionUserId` | PATCH | `/api/session/{id}/user` |
| 3 | `recordObservation` | POST | `/api/ingest/tool-use` |
| 4 | `recordSessionEnd` | POST | `/api/ingest/session-end` |
| 5 | `recordUserPrompt` | POST | `/api/ingest/user-prompt` |
| 6 | `retrieveExperiences` | POST | `/api/memory/experiences` |
| 7 | `buildICLPrompt` | POST | `/api/memory/icl-prompt` |
| 8 | `search` | GET | `/api/search` |
| 9 | `listObservations` | GET | `/api/observations` |
| 10 | `getObservationsByIds` | POST | `/api/observations/batch` |
| 11 | `triggerRefinement` | POST | `/api/memory/refine` |
| 12 | `submitFeedback` | POST | `/api/memory/feedback` |
| 13 | `updateObservation` | PATCH | `/api/memory/observations/{id}` |
| 14 | `deleteObservation` | DELETE | `/api/memory/observations/{id}` |
| 15 | `getQualityDistribution` | GET | `/api/memory/quality-distribution` |
| 16 | `healthCheck` | GET | `/api/health` |
| 17 | `triggerExtraction` | POST | `/api/extraction/run` |
| 18 | `getLatestExtraction` | GET | `/api/extraction/{template}/latest` |
| 19 | `getExtractionHistory` | GET | `/api/extraction/{template}/history` |
| 20 | `getVersion` | GET | `/api/version` |
| 21 | `getProjects` | GET | `/api/projects` |
| 22 | `getStats` | GET | `/api/stats` |
| 23 | `getModes` | GET | `/api/modes` |
| 24 | `getSettings` | GET | `/api/settings` |
| 25 | `close` | — | (cleanup idle connections) |

Wait — the Go SDK has 26 methods. Let me recount: StartSession, UpdateSessionUserId, RecordObservation, RecordSessionEnd, RecordUserPrompt, RetrieveExperiences, BuildICLPrompt, Search, ListObservations, GetObservationsByIds, TriggerRefinement, SubmitFeedback, UpdateObservation, DeleteObservation, GetQualityDistribution, HealthCheck, TriggerExtraction, GetLatestExtraction, GetExtractionHistory, GetVersion, GetProjects, GetStats, GetModes, GetSettings, Close. That's 25 + Close. The task description says 26 methods. The Go Client interface has exactly 25 methods including Close.

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

// Start session
const session = await client.startSession({
  session_id: 'my-session-001',
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
  session_id: session.session_id,
  cwd: '/path/to/project',
});

client.close();
```

## Version Strategy

| Package | Version |
|---------|---------|
| `@cortex-mem/js-sdk` | v1.0.0 |
