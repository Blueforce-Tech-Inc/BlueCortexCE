# API Response JSON Key Naming Convention

> **Purpose**: Reference for SDK developers — which JSON key case each API endpoint uses
> **Maintainer**: CortexCE development team
> **Last verified**: 2026-03-29 (actual curl test against live service)

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
| `GET /api/observations` | `PagedResponse<ObservationEntity>` | **snake_case** |
| `GET /api/observations/{id}` | `ObservationEntity` | **snake_case** |

Key fields: `id`, `content_session_id`, `project_path`, `type`, `title`, `content`, `facts`, `concepts`, `files_read`, `files_modified`, `content_hash`, `discovery_tokens`, `created_at_epoch`, `quality_score`, `feedback_type`, `last_accessed_at`, `access_count`, `refined_at`, `source`, `extracted_data`

### Sessions

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `POST /api/session/start` | `Map.of(...)` | **mixed** — see below |
| `GET /api/session/info` | `Map.of(...)` | **mixed** — see below |
| `PATCH /api/session/{id}` | Entity update response | **snake_case** |

Session start response keys: `context` (snake), `updateFiles` (**camelCase** — WebUI compat), `session_db_id` (snake), `prompt_number` (snake)

### Memory (MemoryController)

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `POST /api/memory/experiences` | `List<Experience>` | **snake_case** |
| `POST /api/memory/icl-prompt` | `Map.of(...)` | **camelCase** |

ICL prompt response keys: `prompt`, `experienceCount`, `maxChars`

### Extraction (ExtractionController)

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `GET /api/extraction/{template}/latest` | `Map.of(...)` | **camelCase** |
| `GET /api/extraction/{template}/history` | `List<Map.of(...)>` | **camelCase** |
| `POST /api/extraction/run` | `Map.of(...)` | **camelCase** |

Extraction response keys: `status`, `template`, `sessionId`, `extractedData`, `createdAt`, `observationId`

### Health (HealthController)

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `GET /api/health` | `Map.of(...)` | **camelCase-ish** (literal keys) |

Health response keys: `service`, `status`, `timestamp`

### Search (ViewerController)

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `GET /api/search` | `Map.of(...)` | **snake_case** (explicit HashMap keys) |

Search response keys: `results`, `query`, `project`, `source`, `result_count`, `algorithm`

### Ingestion (IngestionController)

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `POST /api/ingest/tool-use` | `Map.of(...)` | **camelCase** |
| `POST /api/ingest/observation` | `ObservationEntity` | **snake_case** |
| `POST /api/ingest/session-end` | `Map.of(...)` | **camelCase** |
| `POST /api/ingest/user-prompt` | `Map.of(...)` | **camelCase** |

### Viewer UI (ViewerController)

| Endpoint | Return Type | Naming |
|----------|-------------|--------|
| `GET /api/summaries` | `PagedResponse<SummaryEntity>` | **snake_case** |
| `GET /api/prompts` | `PagedResponse<UserPromptEntity>` | **snake_case** |
| `GET /api/stats` | `Map.of(...)` | **camelCase** |
| `GET /api/projects` | `Map.of(...)` | **camelCase** |

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

## Lessons Learned

1. Jackson's `PropertyNamingStrategy.SNAKE_CASE` only applies to **Java bean properties**, not to `Map.of()` keys
2. Many controller endpoints use `Map.of()` directly and thus return **camelCase** keys despite the global SNAKE_CASE setting
3. The `Experience` record uses camelCase Java fields (`reuseCondition`, `qualityScore`, `createdAt`) but serializes as snake_case due to the global strategy — this caused a bug in the Java SDK (fixed in commit `0ead447`)
4. When in doubt, **check the backend code** and **curl the actual endpoint** — don't guess
