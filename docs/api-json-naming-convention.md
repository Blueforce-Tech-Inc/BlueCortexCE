# API Response JSON Key Naming Convention

> **Purpose**: Reference for SDK developers — which JSON key case each API endpoint uses
> **Maintainer**: CortexCE development team
> **Last verified**: 2026-10-02 (actual curl test against live service; the previous
> verification on 2026-03-29 had drifted — see the Corrections section at the bottom)

## ⚠️ The Problem

The backend sets `jackson.property-naming-strategy: SNAKE_CASE` globally, but this **only affects Java bean properties** (Entity/Record fields). Responses built with `Map.of()` use the literal string keys as-is — Jackson does NOT re-case Map keys.

This means **the API has two different naming conventions** depending on the return type.

## Key Rules

### Rule 1: Entity/Record serialization → snake_case

When an endpoint returns an Entity or Record object directly, Jackson applies the global SNAKE_CASE strategy:

```
Java field:      contentSessionId  →  JSON key: "content_session_id"
Java field:      projectPath       →  JSON key: "project_path"
Java field:      qualityScore      →  JSON key: "quality_score"
Java field:      createdAt         →  JSON key: "created_at"
Java field:      lastAccessedAt    →  JSON key: "last_accessed_at"
Java field:      filesRead         →  JSON key: "files_read"
Java field:      filesModified     →  JSON key: "files_modified"
Java field:      contentHash       →  JSON key: "content_hash"
Java field:      discoveryTokens   →  JSON key: "discovery_tokens"
```

⚠️ **This is the default, not a guarantee.** A field carrying an explicit
`@JsonProperty` override is not re-cased. `ObservationEntity` overrides three of them —
`project`, `narrative` and `extractedData` — so that entity is a mix, not uniform
snake_case. Always check the entity before assuming.

### Rule 2: Map.of() responses → keys as-written

When an endpoint builds a `Map.of("key", value)`, the keys are used verbatim:

```
Map.of("sessionId", ...)     →  "sessionId"     (NOT "session_id")
Map.of("updateFiles", ...)   →  "updateFiles"   (NOT "update_files")
Map.of("extractedData", ...) →  "extractedData"  (NOT "extracted_data")
Map.of("createdAt", ...)     →  "createdAt"      (NOT "created_at")
```

### Rule 3: PagedResponse.hasMore → explicit @JsonProperty

The `PagedResponse` record uses `@JsonProperty("hasMore")` to override the global SNAKE_CASE for WebUI compatibility. **Do NOT change this.**

### Rule 4: Experience record → snake_case

The `ExpRagService.Experience` record is serialized as an Entity (not Map), so it gets SNAKE_CASE:

```
Java field:      reuseCondition    →  "reuse_condition"
Java field:      qualityScore      →  "quality_score"
Java field:      createdAt         →  "created_at"
```

## Endpoint-by-Endpoint Reference

### Observations (ViewerController)

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `GET /api/observations` | `PagedResponse<ObservationEntity>` | **mixed** — envelope camelCase, items snake_case |
| `POST /api/observations/batch` | `BatchGetObservationsResponse` | **mixed** — envelope camelCase, items snake_case |

There is **no `GET /api/observations/{id}` endpoint** — a single observation is fetched
by id through `POST /api/observations/batch` with `{"ids":["..."]}`.

Envelope: `items`, `hasMore` (camelCase, WebUI compat — the backend sends no `total`,
`offset` or `limit`, so SDKs must not fabricate them).

Observation item keys, exactly as returned (verified live):

`id`, `content_session_id`, **`project`** (not `project_path`), `type`, `title`, `subtitle`,
**`narrative`** (not `content`), `facts`, `concepts`, `files_read`, `files_modified`,
`content_hash`, `discovery_tokens`, `prompt_number`, `step_number`, `created_at`,
`created_at_epoch`, `quality_score`, `feedback_type`, `feedback_updated_at`,
`last_accessed_at`, `access_count`, `refined_at`, `refined_from_ids`, `user_comment`,
`source`, **`extractedData`** (not `extracted_data`), `platform_source`,
`generated_by_model`, `relevance_count`, `embedding_model_id`, `embedding_768`,
`embedding_1024`, `embedding_1536`

The three highlighted keys are the ones a snake_case assumption gets wrong: `project` and
`narrative` are `@JsonProperty` overrides on the entity, and `extractedData` is the
V14 camelCase override.

### Sessions

`SessionController` exposes exactly three mappings: `POST /start`, `GET /{sessionId}`,
`PATCH /{sessionId}/user`. There is no `/api/session/info` and no `PATCH /api/session/{id}`.

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `POST /api/session/start` | `Map.of(...)` | **mixed** — see below |
| `GET /api/session/{id}` | `Map.of(...)` | **mixed** — see below |
| `PATCH /api/session/{sessionId}/user` | `Map.of(...)` | **camelCase** |

Session start response keys (verified live): `session_id` (snake), `session_db_id`
(snake), `prompt_number` (snake), `context` (single word), `updateFiles` (**camelCase** —
WebUI compat), `source` (absent when not V18-tagged).

`PATCH /api/session/{sessionId}/user` response keys: `status`, `sessionId`, `userId` (all camelCase).

### Memory (MemoryController)

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `POST /api/memory/experiences` | `List<Experience>` | **snake_case** |
| `POST /api/memory/icl-prompt` | `Map.of(...)` | **camelCase** |

ICL prompt response keys (verified live): `prompt`, `experienceCount`, `maxChars`

### Extraction (ExtractionController)

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `GET /api/extraction/{template}/latest` | `Map.of(...)` | **camelCase** |
| `GET /api/extraction/{template}/history` | `List<Map.of(...)>` | **camelCase** |
| `POST /api/extraction/run` | `Map.of(...)` | **camelCase** |

Extraction response keys (verified live): `status`, `template`, `message`, `sessionId`,
`extractedData`, `createdAt`, `observationId`

### Health (HealthController)

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `GET /api/health` | `Map.of(...)` | **literal keys** |

Health response keys (verified live): `service`, `status`, `timestamp`

### Search (ViewerController)

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `GET /api/search` | `Map.of(...)` | **camelCase** (literal keys) |

Search response keys (verified live): `observations`, `count`, `strategy`, `fell_back`
— note `fell_back` is the only snake_case key in the object. There is no `results`,
`query`, `project`, `source`, `result_count` or `algorithm` in the response.

### Ingestion (IngestionController)

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `POST /api/ingest/tool-use` | `Map.of(...)` | **camelCase** |
| `POST /api/ingest/observation` | `ObservationEntity` | **mixed** (see Observations above) |
| `POST /api/ingest/session-end` | `Map.of(...)` | **camelCase** |
| `POST /api/ingest/user-prompt` | `Map.of(...)` | **camelCase** |

### Viewer UI (ViewerController)

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `GET /api/summaries` | `PagedResponse<SummaryEntity>` | **snake_case** items |
| `GET /api/prompts` | `PagedResponse<UserPromptEntity>` | **snake_case** items |
| `GET /api/stats` | `Map.of(...)` | **camelCase** |
| `GET /api/projects` | `Map.of(...)` | **camelCase** |

Summary item keys (verified live): `id`, `session_id`, `project`, `request`, `notes`,
`files_read`, `files_edited`, `learned`, `investigated`, `completed`, `next_steps`,
`prompt_number`, `created_at`, `created_at_epoch`, `platform_source`

Prompt item keys (verified live): `id`, `content_session_id`, `project`, `prompt_text`,
`prompt_number`, `created_at`, `created_at_epoch`, `platform_source`

`GET /api/stats` returns `worker`, `database`. `GET /api/projects` returns `projects`,
plus the V18 additions `sources` and `projectsBySource`.

### WebUI Compatibility Contract (DO NOT CHANGE)

These fields must stay camelCase for the WebUI submodule:

| Endpoint | Field | Reason |
|----------|-------|--------|
| `/api/observations`, `/api/summaries`, `/api/prompts` | `hasMore` | `usePagination.ts` reads `data.hasMore` |
| `/api/session/start`, `/api/context/generate` | `updateFiles` | `proxy.js` reads `javaResponse.data.updateFiles` |
| `/api/ingest/observation`, ObservationEntity | `extractedData` | `@JsonProperty("extractedData")` on entity |

## SDK Implementation Guide

### For Java SDK (Jackson)

```java
// Entity fields: use @JsonProperty for explicit mapping
@JsonProperty("reuse_condition") @JsonAlias("reuseCondition")
private String reuseCondition;

// Map responses: keys are used as-is, no re-casing
```

### For Go SDK

```go
// Entity responses: use snake_case json tags
type Experience struct {
    ReuseCondition string  `json:"reuse_condition"`
    QualityScore   float64 `json:"quality_score"`
    CreatedAt      string  `json:"created_at"`
}

// Map responses: use the exact key names from the API
// e.g., ICL prompt: "experienceCount", "maxChars"
```

### For Python SDK

```python
# Entity responses: use snake_case field names
# Map responses: use exact key names from API
```

### For JS/TS SDK

```typescript
// Entity responses: use snake_case property names
// Map responses: use exact key names from API
// Note: JS conventions naturally use camelCase, but API uses snake_case for entities
```

## How to Verify

When adding a new endpoint or field, **always verify the actual JSON output**:

```bash
# Test entity serialization
curl -s "http://127.0.0.1:37777/api/observations?limit=1" | python3 -m json.tool

# Test map response
curl -s "http://127.0.0.1:37777/api/session/start" -X POST \
  -H "Content-Type: application/json" \
  -d '{"session_id":"test","project_path":"/tmp/test"}' | python3 -m json.tool

# Test extraction
curl -s "http://127.0.0.1:37777/api/extraction/user_preferences/latest?projectPath=/tmp/test" | python3 -m json.tool
```

**DO NOT assume the naming convention. Always verify with curl.**

## Corrections (2026-10-02 re-verification)

The previous verification stamp was 2026-03-29. Re-running every claim in this document
against a live backend found the following drift. They are listed so the next reviewer
does not trust the older claims without re-checking:

| Claimed before | Reality (verified by curl) |
|----------------|---------------------------|
| `GET /api/observations/{id}` exists and returns an `ObservationEntity` | **404.** No such endpoint. Single observations are fetched via `POST /api/observations/batch` |
| Observation item key `project_path` | `project` |
| Observation item key `content` | `narrative` |
| Observation item key `extracted_data` | `extractedData` |
| `GET /api/session/info` exists | **404.** `SessionController` maps only `POST /start`, `GET /{sessionId}`, `PATCH /{sessionId}/user` |
| `PATCH /api/session/{id}` returns an entity update response | **405 Method Not Allowed.** The PATCH is on `/{sessionId}/user` and returns `{status, sessionId, userId}` |
| `GET /api/search` returns `results`, `query`, `project`, `source`, `result_count`, `algorithm` in snake_case | Returns `observations`, `count`, `strategy`, `fell_back`. `fell_back` is the only snake_case key |
| `GET /api/observations` is uniformly snake_case | The envelope is camelCase (`items`, `hasMore`); only the items are snake_case |

The `observations` mistake is the one most likely to be repeated: `ObservationEntity`
carries `@JsonProperty` overrides for `project`, `narrative` and `extractedData` precisely
because they do **not** follow the global SNAKE_CASE strategy, so the entity is a mix.

## Lessons Learned

1. Jackson's `PropertyNamingStrategy.SNAKE_CASE` only applies to **Java bean properties**, not to `Map.of()` keys
2. Many controller endpoints use `Map.of()` directly and thus return **camelCase** keys despite the global SNAKE_CASE setting
3. The `Experience` record uses camelCase Java fields (`reuseCondition`, `qualityScore`, `createdAt`) but serializes as snake_case due to the global strategy — this caused a bug in the Java SDK (fixed in commit `0ead447`)
4. When in doubt, **check the backend code** and **curl the actual endpoint** — don't guess
