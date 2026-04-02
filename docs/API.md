# Cortex Community Edition API Documentation

> **中文版**: [API-zh-CN.md](API-zh-CN.md)

> **Version**: 0.1.0-beta
> **Base URL**: `http://localhost:37777`
> **Protocol**: HTTP/1.1, SSE (Server-Sent Events)

This document describes the REST API for Cortex Community Edition backend.

## Table of Contents

- [Overview](#overview)
- [Authentication](#authentication)
- [Response Formats](#response-formats)
- [Sessions](#sessions)
- [Ingest](#ingest)
- [Memory](#memory)
- [Extraction](#extraction)
- [Context](#context)
- [Search](#search)
- [Management](#management)
- [Mode](#mode)
- [Viewer](#viewer)
- [Import](#import)
- [Logs](#logs)
- [Health & Version](#health--version)
- [Cursor](#cursor)
- [Streaming](#streaming)
- [Error Codes](#error-codes)
- [Test Endpoints](#test-endpoints)
- [Usage Examples](#usage-examples)
- [Appendix](#appendix)
- [Changelog](#changelog)

## Overview

The API follows RESTful principles and supports both synchronous requests and Server-Sent Events (SSE) for streaming responses.

### Base URL

```
http://localhost:37777
```

### Content-Type

All requests and responses use JSON format:

```
Content-Type: application/json
```

## Authentication

**No authentication is required** for the current version. All endpoints are open on `localhost:37777`.

> ⚠️ **Production Warning**: If exposing to the public internet, add an authentication layer (e.g., API Key, JWT).

## Response Formats

### Success Response

```json
{
  "status": "ok",
  "data": { ... }
}
```

### Paginated Response

```json
{
  "items": [...],
  "hasMore": true
}
```

### Error Response

```json
{
  "error": "Error message",
  "status": "failed",
  "code": "ERROR_CODE"
}
```

## Sessions

### Start Session

```
POST /api/session/start
Content-Type: application/json

{
  "session_id": "content-session-id",
  "project_path": "/path/to/project",
  "cwd": "/path/to/project",
  "projects": "project1,project2",
  "is_worktree": false,
  "parent_project": null,
  "user_id": "user-123"
}
```

**Request Fields**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `session_id` | string | ✅ | Claude Code content session ID |
| `project_path` | string | ✅ | Project path |
| `cwd` | string | ❌ | Current working directory |
| `projects` | string | ❌ | Multi-project support (comma-separated) |
| `is_worktree` | boolean | ❌ | Whether this is a worktree |
| `parent_project` | string | ❌ | Parent project name (worktree mode) |
| `user_id` | string | ❌ | User ID for Phase 3 multi-user support |

**Response** (`200 OK`):
```json
{
  "context": "# Recent Work\n\n...",
  "updateFiles": [
    {
      "path": "/path/to/project/CLAUDE.md",
      "content": "# Claude-Mem Context\n\n..."
    }
  ],
  "session_db_id": "550e8400-e29b-41d4-a716-446655440000",
  "prompt_number": 1
}
```

### Get Session

```
GET /api/session/{sessionId}
```

Retrieves session details by content session ID (the ID used by Claude Code).

**Path Parameters**:
- `sessionId` — Content session ID

**Request Example**:
```bash
curl http://localhost:37777/api/session/abc-123-def
```

**Response** (`200 OK`):
```json
{
  "session_db_id": "550e8400-e29b-41d4-a716-446655440000",
  "content_session_id": "mem-abc-123",
  "project_path": "/Users/dev/myproject",
  "status": "active",
  "started_at": "2026-03-13T10:15:00Z"
}
```

**Error Response** (`404 Not Found`):
```json
{
  "error": "Session not found",
  "session_id": "abc-123-def"
}
```

### Update Session User

```
PATCH /api/session/{sessionId}/user
Content-Type: application/json

{
  "user_id": "user-123"
}
```

Updates the user ID associated with a session.

**Path Parameters**:
- `sessionId` — Content session ID

**Request Body**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `user_id` | string | ✅ | User ID to associate with the session |

**Request Example**:
```bash
curl -X PATCH http://localhost:37777/api/session/abc-123-def/user \
  -H "Content-Type: application/json" \
  -d '{"user_id": "user-123"}'
```

**Response** (`200 OK`):
```json
{
  "status": "ok",
  "sessionId": "abc-123-def",
  "userId": "user-123"
}
```

## Ingest

### Record Tool Use

```
POST /api/ingest/tool-use
Content-Type: application/json
```

Records a tool use event from Claude Code hooks (via `wrapper.js`). Triggers async LLM processing for observation extraction.

**Request Body**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `session_id` | string | ✅ | Content session ID |
| `tool_name` | string | ✅ | Tool name (`Edit`, `Write`, `Read`, `Bash`) |
| `tool_input` | object/string | ❌ | Tool input parameters |
| `tool_response` | object/string | ❌ | Tool response |
| `cwd` | string | ❌ | Current working directory |

**Request Example**:
```json
{
  "session_id": "content-session-id",
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/file.ts",
    "old_string": "...",
    "new_string": "..."
  },
  "tool_response": "File updated successfully",
  "cwd": "/path/to/project"
}
```

**Response** (`200 OK`):
```json
{
  "status": "accepted"
}
```

**Error Responses**:
- `400` — Missing required field: `session_id` or `tool_name`
- `429` — Rate limit exceeded (10 requests per 60 seconds per session)

**Rate Limit**: 10 requests per 60 seconds per `session_id`.

### Record User Prompt

```
POST /api/ingest/user-prompt
Content-Type: application/json
```

Records a user prompt event from Claude Code hooks. Automatically ensures the session exists before recording.

**Request Body**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `session_id` | string | ✅ | Content session ID |
| `prompt_text` | string | ❌ | Prompt text (truncated if exceeds max length) |
| `prompt_number` | int | ❌ | Prompt number for ordering (default: 1) |
| `cwd` | string | ❌ | Current working directory |

**Request Example**:
```json
{
  "session_id": "content-session-id",
  "prompt_text": "User prompt text",
  "prompt_number": 1,
  "cwd": "/path/to/project"
}
```

**Response** (`200 OK`):
```json
{
  "status": "ok"
}
```

**Error Responses**:
- `400` — Missing required field: `session_id`

### Signal Session End

```
POST /api/ingest/session-end
Content-Type: application/json
```

Signals the end of a session and triggers async summary generation.

**Request Body**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `session_id` | string | ✅ | Content session ID |
| `cwd` | string | ❌ | Current working directory |
| `last_assistant_message` | string | ❌ | Last assistant message for summary generation |

**Request Example**:
```json
{
  "session_id": "content-session-id",
  "cwd": "/path/to/project",
  "last_assistant_message": "Task completed successfully"
}
```

**Response** (`200 OK`):
```json
{
  "status": "ok"
}
```

### Create Observation Directly

```
POST /api/ingest/observation
Content-Type: application/json
```

Directly creates an observation with automatic embedding generation.

**Request Body**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `content_session_id` | string | ✅ | Content session ID (or use `session_id` alias) |
| `project_path` | string | ✅ | Project path (or use `cwd` alias) |
| `type` | string | ❌ | Observation type (e.g., `feature`, `bugfix`) |
| `title` | string | ❌ | Observation title |
| `subtitle` | string | ❌ | Observation subtitle |
| `narrative` | string | ❌ | Observation narrative (or use `content` alias) |
| `facts` | string[] | ❌ | List of factual statements |
| `concepts` | string[] | ❌ | List of concept tags |
| `source` | string | ❌ | Source attribution (e.g., `manual`) |
| `extractedData` | object | ❌ | Structured extracted data |
| `files_read` | string[] | ❌ | List of files read |
| `files_modified` | string[] | ❌ | List of files modified |
| `prompt_number` | int | ❌ | Prompt number for ordering |

**Field aliases**: `session_id` is accepted as an alias for `content_session_id`, `cwd` for `project_path`, and `content` for `narrative`.

**Request Example**:
```json
{
  "content_session_id": "session-123",
  "project_path": "/path/to/project",
  "type": "feature",
  "title": "Added new API endpoint",
  "subtitle": "REST endpoint implementation",
  "narrative": "Created a new REST endpoint for...",
  "facts": ["fact1", "fact2"],
  "concepts": ["api", "rest"],
  "source": "manual",
  "extractedData": {"key": "value"},
  "files_read": ["src/main/java/..."],
  "files_modified": ["src/main/java/..."]
}
```

**Response** (`200 OK`):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Added new API endpoint",
  "type": "feature"
}
```

## Memory

### Trigger Memory Refinement

```
POST /api/memory/refine?project=/path/to/project
```

Triggers memory refinement for a project. Refinement re-evaluates observation quality and updates the quality distribution.

**Query Parameters**:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `project` | string | ✅ | Absolute project path to trigger refinement for |

**Response** (`200 OK`):
```json
{
  "status": "triggered",
  "project": "/Users/dev/my-project",
  "message": "Memory refinement event has been published"
}
```

**Error Response** (`400 Bad Request`):
```json
{
  "error": "project is required"
}
```

### Update Observation

```
PATCH /api/memory/observations/{id}
Content-Type: application/json
```

Partially updates an observation. Only fields present in the request body are updated; null values clear the field, absent fields are left unchanged.

**Path Parameters**:
- `id` — Observation UUID

**Request Body**:
```json
{
  "title": "Updated title",
  "source": "manual",
  "extractedData": {"key": "value"}
}
```

Supported fields: `title`, `content` (or `narrative`), `subtitle`, `source`, `facts`, `concepts`, `extractedData`. Null values clear the field; absent fields are left unchanged.

**Response** (`200 OK`):
```json
{
  "status": "updated",
  "id": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Error Responses**:
- `400` — Invalid field types in request body (e.g., `title must be a string`)
- `404` — Observation with given UUID not found

### Delete Observation

```
DELETE /api/memory/observations/{id}
```

**Path Parameters**:
- `id` — Observation UUID

**Response** (`200 OK`):
```json
{
  "status": "deleted",
  "id": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Get Experiences (ExpRAG)

```
POST /api/memory/experiences
Content-Type: application/json

{
  "task": "database optimization",
  "project": "/path/to/project",
  "count": 5,
  "source": "manual",
  "requiredConcepts": ["how-it-works"],
  "userId": "user-123"
}
```

**Request Fields**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `task` | string | ✅ | Task or question to find relevant experiences for |
| `project` | string | ❌ | Project path for scoping |
| `count` | int | ❌ | Max experiences to return (default: 4) |
| `source` | string | ❌ | Filter by source (e.g., `manual`, `tool_result`) |
| `requiredConcepts` | string[] | ❌ | Filter to experiences containing these concepts |
| `userId` | string | ❌ | User ID for multi-user isolation |

**Response** (`200 OK`): JSON array of experience objects:
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "task": "database optimization",
    "strategy": "Use connection pooling with HikariCP",
    "outcome": "Query latency reduced by 40%",
    "reuse_condition": "When optimizing database-heavy services",
    "quality_score": 0.85,
    "created_at": "2026-03-13T10:15:00Z"
  }
]
```

**Error Responses**:
- `400` — `{"error": "task is required"}` (missing or blank `task` field)

### Get ICL Prompt

```
POST /api/memory/icl-prompt
Content-Type: application/json

{
  "task": "database optimization",
  "project": "/path/to/project",
  "maxChars": 4000,
  "userId": "user-123"
}
```

**Request Fields**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `task` | string | ✅ | Current task/question for context retrieval |
| `project` | string | ❌ | Project path for scoping |
| `maxChars` | int | ❌ | Max prompt length (default: 4000) |
| `userId` | string | ❌ | User ID for multi-user isolation |

**Response** (`200 OK`):
```json
{
  "prompt": "# Relevant Experiences\n\n...",
  "experienceCount": 3,
  "maxChars": 4000
}
```

**Error Responses**:
- `400` — `{"error": "task is required"}` (missing or blank `task` field)

### Get Quality Distribution

```
GET /api/memory/quality-distribution?project=/path/to/project
```

Returns the quality distribution (high/medium/low/unknown counts) for observations in a project.

**Query Parameters**:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `project` | string | ✅ | Absolute project path |

**Response** (`200 OK`):
```json
{
  "project": "/Users/dev/my-project",
  "high": 10,
  "medium": 20,
  "low": 5,
  "unknown": 3
}
```

**Error Responses**:
- `400` — `{"error": "project is required"}` (missing or blank `project` param)
- `500`:
```json
{
  "project": "/Users/dev/my-project",
  "error": "Failed to get quality distribution: ...",
  "high": 0,
  "medium": 0,
  "low": 0,
  "unknown": 0
}
```

### Submit Feedback

```
POST /api/memory/feedback
Content-Type: application/json

{
  "observationId": "550e8400-e29b-41d4-a716-446655440000",
  "feedbackType": "SUCCESS",
  "comment": "Task completed successfully"
}
```

**Request Fields**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `observationId` | string | ✅ | UUID of the observation to provide feedback for |
| `feedbackType` | string | ✅ | Feedback type (e.g., `SUCCESS`, `FAILURE`) |
| `comment` | string | ❌ | Optional feedback comment |

**Response** (`200 OK`):
```json
{
  "status": "ok",
  "observationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Error Responses**:
- `400` — Missing `observationId` or `feedbackType`, invalid UUID format
- `404` — Observation not found

## Observations

> Observations are listed under the [Viewer](#viewer) section.

## Extraction

Phase 3 structured data extraction endpoints — extract structured data (e.g., user preferences, allergy info) from session observations.

### Trigger Extraction

```
POST /api/extraction/run?projectPath=/path/to/project
```

Manually triggers structured data extraction. Runs **synchronously** — the response is returned after extraction completes.

**Query Parameters**:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `projectPath` | string | ✅ | Absolute project path |

**Response** (`200 OK`):
```json
{
  "status": "ok",
  "projectPath": "/Users/dev/myproject",
  "message": "Extraction completed"
}
```

### Get Latest Extraction

```
GET /api/extraction/{templateName}/latest?projectPath=/path/to/project&userId=user-123
```

Returns the most recent extraction result for a given template name and project.

**Path Parameters**:
- `templateName` — Extraction template name (e.g., `user-preferences`)

**Query Parameters**:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `projectPath` | string | ✅ | Absolute project path |
| `userId` | string | ❌ | User ID for user-scoped extractions |

**Response** (`200 OK`, found):
```json
{
  "status": "ok",
  "template": "user-preferences",
  "sessionId": "session-123",
  "extractedData": { "preferredLanguage": "en", "theme": "dark" },
  "createdAt": 1707878400000,
  "observationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response** (`200 OK`, not found):
```json
{
  "status": "not_found",
  "template": "user-preferences",
  "message": "No extraction found"
}
```

### Get Extraction History

```
GET /api/extraction/{templateName}/history?projectPath=/path/to/project&userId=user-123&limit=10
```

Returns historical extraction results in reverse chronological order. The limit is clamped between 1 and 100.

**Query Parameters**:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `projectPath` | string | ✅ | Absolute project path |
| `userId` | string | ❌ | User ID for user-scoped extractions |
| `limit` | int | ❌ | Max entries to return (default: 10) |

**Response** (`200 OK`): JSON array of extraction records:
```json
[
  {
    "sessionId": "pref:abc123:alice",
    "extractedData": { "preferredLanguage": "en", "theme": "dark" },
    "createdAt": 1707878400000,
    "observationId": "550e8400-e29b-41d4-a716-446655440000"
  }
]
```

## Context

### Inject Context

Generate context for injection into Claude Code sessions.

```
GET /api/context/inject?projects=/Users/dev/myproject
```

Query parameters:

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projects` | No | Comma-separated list of project paths |

Response:

```json
{
  "context": "# Recent Work\n\n## Recent Changes\n...",
  "updateFiles": [
    {
      "path": "/Users/dev/myproject/CLAUDE.md",
      "content": "# Claude-Mem Context\n\n..."
    }
  ]
}
```

### Generate Context

Generate context for a single project.

```
POST /api/context/generate
Content-Type: application/json

{
  "project_path": "/path/to/project"
}
```

Response:

```json
{
  "context": "# Recent Work\n\n..."
}
```

### Preview Context

Preview project context as plain text (for UI display).

```
GET /api/context/preview?project=/Users/dev/myproject&maxObservations=20
```

Query parameters:

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `project` | Yes | — | Project path |
| `observationTypes` | No | "" | Comma-separated observation type filter |
| `concepts` | No | "" | Comma-separated concept filter |
| `includeObservations` | No | true | Include observations |
| `includeSummaries` | No | true | Include summaries |
| `maxObservations` | No | 50 | Max observations |
| `maxSummaries` | No | 2 | Max summaries |
| `sessionCount` | No | 10 | Recent sessions to query |
| `fullCount` | No | 5 | Observations with full detail |

Response (text/plain):

```text
# Claude-Mem Context

Generated: 2026-03-13 10:15

## Recent Work

### Bug fix for authentication
**Type**: bugfix | **Concepts**: authentication
Fixed JWT token validation issue...

---
Token Savings Summary
- Total observations: 45
- Read tokens: 10,500
- Saved tokens: 95,000 (90%)
```

### Recent Context

Get recent session context summary.

```
GET /api/context/recent?project=/Users/dev/myproject&limit=5
```

Query parameters:

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `project` | No | cwd | Project path |
| `limit` | No | 3 | Number of sessions to return |

Response:

```json
{
  "content": [
    {
      "type": "text",
      "text": "# Recent Session Context\n\nShowing last 3 session(s)..."
    }
  ],
  "count": 3
}
```

### Timeline Context

Get timeline context with anchor-based query.

```
GET /api/context/timeline?anchor=obs-123&project=/Users/dev/myproject
```

Query parameters:

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `anchor` | No | — | Anchor ID (UUID or session ID) |
| `depth_before` | No | 10 | Items before anchor |
| `depth_after` | No | 10 | Items after anchor |
| `project` | No | — | Project path |

Response:

```json
{
  "anchor": {
    "id": "obs-123",
    "title": "Feature implementation",
    "timestamp": 1707878400000
  },
  "before": [...],
  "after": [...]
}
```

### Prior Messages

Get messages from the previous session (for context continuity).

```
GET /api/context/prior-messages?project=/Users/dev/myproject
```

Query parameters:

| Parameter | Required | Description |
|-----------|----------|-------------|
| `project` | Yes | Project path |
| `currentSessionId` | No | Current session ID (for exclusion) |

Response:

```json
{
  "userMessage": "Add authentication feature",
  "assistantMessage": "I'll implement the authentication feature..."
}
```

## Search

### Search Memory

```
GET /api/search?project=/path/to/project&query=search+terms&limit=10&type=bugfix&concept=how-it-works&source=manual
```

**Query Parameters**:

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `project` | string | ✅ | — | Project path to search within |
| `query` | string | ❌ | — | Search query text for semantic search. If empty, returns filter-only results |
| `type` | string | ❌ | — | Filter by observation type (e.g., `bugfix`, `feature`) |
| `concept` | string | ❌ | — | Filter by observation concept (e.g., `how-it-works`, `architecture`) |
| `source` | string | ❌ | — | Filter by source (e.g., `manual`, `auto`) |
| `limit` | int | ❌ | 20 | Max results (max 100) |
| `offset` | int | ❌ | 0 | Pagination offset |
| `orderBy` | string | ❌ | — | Order by field (accepted for MCP compatibility, not yet fully implemented) |

**Request Example**:
```bash
curl "http://localhost:37777/api/search?project=/Users/dev/myproject&query=authentication&limit=10"
```

**Response** (`200 OK`):
```json
{
  "observations": [...],
  "strategy": "hybrid",
  "fell_back": false,
  "count": 10
}
```

**Search Strategies**:
- `hybrid` — Combined pgvector semantic search + PostgreSQL tsvector full-text search
- `tsvector` — PostgreSQL full-text search fallback (used when pgvector is unavailable)
- `filter` — Filter-only search (no query text; results based on type/concept/source filters)
- `recent` — Default listing (no query, no filters; returns most recent observations)
- `none` — All search methods failed (returns empty results)

## Management

### Get Projects

```
GET /api/projects
```

Returns all known project paths.

**Response** (`200 OK`):
```json
{
  "projects": [
    "/Users/dev/myproject",
    "/Users/dev/another-project"
  ]
}
```

### Get Project Statistics

```
GET /api/stats
```

Returns database and processing statistics.

**Response** (`200 OK`):
```json
{
  "worker": {
    "isProcessing": false,
    "queueDepth": 5
  },
  "database": {
    "totalObservations": 1234,
    "totalSummaries": 56,
    "totalSessions": 78,
    "totalProjects": 3
  }
}
```

### Get Settings

```
GET /api/settings
```

Returns current application settings with all `CLAUDE_MEM_*` configuration fields, plus active mode information.

**Response** (`200 OK`):
```json
{
  "CLAUDE_MEM_MODE": "code",
  "CLAUDE_MEM_PROVIDER": "openai",
  "CLAUDE_MEM_MODEL": "gpt-4o",
  "CLAUDE_MEM_LOG_LEVEL": "INFO",
  "CLAUDE_MEM_CONTEXT_OBSERVATIONS": 50,
  "CLAUDE_MEM_CONTEXT_FULL_COUNT": 5,
  "CLAUDE_MEM_CONTEXT_FULL_FIELD": "full_content",
  "CLAUDE_MEM_CONTEXT_SESSION_COUNT": 10,
  "CLAUDE_MEM_CONTEXT_OBSERVATION_TYPES": [],
  "CLAUDE_MEM_CONTEXT_OBSERVATION_CONCEPTS": [],
  "CLAUDE_MEM_CONTEXT_MAX_OBSERVATIONS": 100,
  "CLAUDE_MEM_CONTEXT_SHOW_READ_TOKENS": true,
  "CLAUDE_MEM_CONTEXT_SHOW_WORK_TOKENS": true,
  "CLAUDE_MEM_CONTEXT_SHOW_SAVINGS_AMOUNT": true,
  "CLAUDE_MEM_CONTEXT_SHOW_SAVINGS_PERCENT": true,
  "CLAUDE_MEM_CONTEXT_SHOW_LAST_SUMMARY": true,
  "CLAUDE_MEM_CONTEXT_SHOW_LAST_MESSAGE": true,
  "CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED": false,
  "CLAUDE_MEM_EXCLUDED_PROJECTS": [],
  "CLAUDE_MEM_DATA_DIR": "",
  "modeName": "Code",
  "modeDescription": "Tracks code evolution"
}
```

> **Note**: The exact field values depend on the current `settings.json` and environment variable overrides. `modeName` and `modeDescription` are injected from the active Mode configuration.

### Update Settings

```
POST /api/settings
```

Persists settings to the settings file. Supports any `CLAUDE_MEM_*` prefixed field. If `mode` or `CLAUDE_MEM_MODE` is changed, also updates the active mode.

**Request Body**:
```json
{
  "CLAUDE_MEM_MODE": "all",
  "CLAUDE_MEM_MODEL": "gpt-4o-mini"
}
```

> **Note**: You can also use `"mode": "all"` as a shorthand for `"CLAUDE_MEM_MODE": "all"`.

**Response** (`200 OK`):
```json
{
  "success": true
}
```

**Error Response** (`500`):
```json
{
  "success": false,
  "error": "Failed to save settings: ..."
}
```

## Mode

### Get Current Mode

```
GET /api/mode
```

Returns the current active mode information.

**Response** (`200 OK`):
```json
{
  "mode_id": "code",
  "name": "Code Development",
  "description": "Software development and engineering work",
  "version": "1.0.0",
  "observation_types": [
    {
      "id": "bugfix",
      "label": "Bug Fix",
      "description": "Something was broken, now fixed",
      "emoji": "🔴",
      "work_emoji": "🛠️"
    }
  ],
  "observation_concepts": [
    {
      "id": "how-it-works",
      "label": "How It Works",
      "description": "Understanding mechanisms"
    }
  ]
}
```

### Set Active Mode

```
PUT /api/mode
Content-Type: application/json
```

Switches the active mode at runtime. Supports base modes (e.g., "code") and inherited modes (e.g., "code--zh").

**Request Body** (snake_case):
```json
{
  "mode_id": "code--zh"
}
```

**Response** (`200 OK`):
```json
{
  "mode_id": "code--zh",
  "name": "代码模式",
  "description": "开发工作流模式",
  "version": "1.0.0",
  "observation_types": [...],
  "observation_concepts": [...]
}
```

### List Observation Types

```
GET /api/mode/types
```

**Response** (`200 OK`):
```json
[
  {
    "id": "bugfix",
    "label": "Bug Fix",
    "description": "Something was broken, now fixed",
    "emoji": "🔴",
    "work_emoji": "🛠️"
  }
]
```

### List Observation Concepts

```
GET /api/mode/concepts
```

**Response** (`200 OK`):
```json
[
  {
    "id": "how-it-works",
    "label": "How It Works",
    "description": "Understanding mechanisms"
  }
]
```

### Validate Type

```
GET /api/mode/types/{typeId}/validate
```

**Response** (`200 OK`):
```json
{
  "valid": true
}
```

### Get Type Emoji

```
GET /api/mode/types/{typeId}/emoji
```

**Response** (`200 OK`):
```json
{
  "emoji": "🐛",
  "workEmoji": "🔧",
  "label": "Bug Fix"
}
```

### List Valid Types

```
GET /api/mode/types/valid
```

**Response** (`200 OK`):
```json
["bugfix", "feature", "refactor", "discovery"]
```

### List Valid Concepts

```
GET /api/mode/concepts/valid
```

**Response** (`200 OK`):
```json
["how-it-works", "architecture", "best-practice"]
```

## Viewer

### List Observations

```
GET /api/observations?project=/path/to/project&limit=20&offset=0
```

Returns a paginated list of observations, optionally filtered by project.

**Query Parameters**:

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `project` | string | No | null | Project path filter (returns all if omitted) |
| `offset` | int | No | 0 | Pagination offset (0-based) |
| `limit` | int | No | 20 | Items per page (max 100) |

**Response** (`200 OK`):
```json
{
  "items": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Feature implementation",
      "type": "feature",
      "narrative": "Implemented JWT authentication...",
      "project": "/Users/dev/myproject",
      "created_at_epoch": 1707878400000
    }
  ],
  "hasMore": true
}
```

### Get Observations by IDs

```
POST /api/observations/batch
Content-Type: application/json
```

Retrieves multiple observations by their UUIDs. Supports optional project filtering, ordering, and result limit.

**Request Body**:
```json
{
  "ids": ["obs-1", "obs-2", "obs-3"],
  "project": "/Users/dev/myproject",
  "orderBy": "created_at_epoch",
  "limit": 100
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `ids` | string[] | ✅ | List of observation UUIDs to retrieve |
| `project` | string | ❌ | Optional project filter |
| `orderBy` | string | ❌ | Sort order (e.g., `created_at_epoch`) |
| `limit` | int | ❌ | Max results to return |

**Response** (`200 OK`):
```json
{
  "observations": [...],
  "count": 3
}
```

### List Summaries

```
GET /api/summaries?project=/path/to/project&limit=20&offset=0
```

Returns a paginated list of session summaries. Query parameters and response format are the same as List Observations (returns summary objects instead).

### List Prompts

```
GET /api/prompts?project=/path/to/project&limit=20&offset=0
```

Returns a paginated list of user prompts. Query parameters and response format are the same as List Observations (returns user prompt objects instead).

### Get Timeline

```
GET /api/timeline?project=/path/to/project
```

Returns observations grouped by date for the viewer UI timeline. Supports date range and anchor-based queries. Date range is limited to 1 year maximum.

**Query Parameters**:

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `project` | string | Yes | — | Project path |
| `startEpoch` | long | No | 90 days ago | Start timestamp |
| `endEpoch` | long | No | now | End timestamp |
| `anchorId` | string | No | — | Anchor observation ID |
| `depthBefore` | int | No | — | Items before anchor |
| `depthAfter` | int | No | — | Items after anchor |
| `query` | string | No | — | Query to find anchor |

**Response** (`200 OK`):
```json
[
  {
    "date": "2026-03-13",
    "count": 15,
    "ids": ["id1", "id2"]
  }
]
```

**Error Response** (`400 Bad Request`):
```json
{
  "error": "Date range exceeds 1 year maximum"
}
```

### Search by File

```
GET /api/search/by-file?project=/path/to/project&filePath=/src/auth.ts&isFolder=false&limit=20
```

**Query Parameters**:

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `project` | string | Yes | — | Project path |
| `filePath` | string | Yes | — | File or folder path to search for |
| `isFolder` | boolean | No | false | If true, match folder prefix |
| `limit` | int | No | 20 | Max results (max 100) |
| `debug` | boolean | No | false | Enable debug logging |

**Response** (`200 OK`):
```json
{
  "observations": [...],
  "count": 5,
  "filePath": "/src/auth",
  "isFolder": true
}
```

### Get Processing Status

```
GET /api/processing-status
```

**Response** (`200 OK`):
```json
{
  "isProcessing": false,
  "queueDepth": 5
}
```

### Batch Get SDK Sessions

```
POST /api/sdk-sessions/batch
Content-Type: application/json
```

Batch query session information for export scripts.

**Request Body**:
```json
{
  "contentSessionIds": ["session-1", "session-2"]
}
```

**Response** (`200 OK`):
```json
[
  {
    "id": "session-uuid",
    "content_session_id": "content-123",
    "project": "/Users/dev/myproject",
    "user_prompt": "Add feature",
    "started_at_epoch": 1707878400000,
    "completed_at_epoch": 1707882000000,
    "status": "completed"
  }
]
```

### List Modes

```
GET /api/modes
```

Returns the current active mode configuration.

**Response** (`200 OK`):
```json
{
  "id": "code",
  "name": "Code Development",
  "description": "Software development and engineering work",
  "version": "1.0.0",
  "observation_types": [...],
  "observation_concepts": [...]
}
```

### Create Mode (Set Active Mode)

```
POST /api/modes
```

Switches the active mode at runtime. Supports base modes (e.g., "code") and inherited modes (e.g., "code--zh").

**Request Body**:
```json
{
  "mode": "code--zh"
}
```

**Response** (`200 OK`):
```json
{
  "success": true,
  "mode": "code--zh",
  "name": "代码模式"
}
```

## Import

### Bulk Import

```
POST /api/import
Content-Type: application/json

{
  "sessions": [...],
  "observations": [...],
  "summaries": [...],
  "prompts": [...]
}
```

Bulk import all data types in a single request. Includes duplicate checking.

**Response** (`200 OK`):
```json
{
  "success": true,
  "stats": {
    "sessionsImported": 10,
    "sessionsSkipped": 2,
    "observationsImported": 45,
    "observationsSkipped": 5,
    "summariesImported": 8,
    "summariesSkipped": 1,
    "promptsImported": 12,
    "promptsSkipped": 0,
    "errors": 0
  }
}
```

### Import Observations

```
POST /api/import/observations
Content-Type: application/json
```

Request body: Array of observation objects.

Response:

```json
{
  "success": true,
  "imported": 45,
  "skipped": 5,
  "errors": 0,
  "errorMessages": []
}
```

### Import Sessions

```
POST /api/import/sessions
Content-Type: application/json
```

Request body: Array of session objects.

Response: Same format as Import Observations.

### Import Summaries

```
POST /api/import/summaries
Content-Type: application/json
```

Request body: Array of summary objects.

Response: Same format as Import Observations.

### Import Prompts

```
POST /api/import/prompts
Content-Type: application/json
```

Request body: Array of prompt objects.

Response: Same format as Import Observations.

## Logs

### Get Logs

```
GET /api/logs
```

**Query Parameters**:

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `lines` | int | No | 1000 | Maximum lines to return |

**Response** (`200 OK`):
```json
{
  "logs": "[2026-03-13 10:15:00] [INFO] [WORKER] Processing request...\n...",
  "path": "/Users/dev/.claude-mem/logs",
  "files": ["claude-mem-2026-03-13.log"],
  "totalLines": 1523,
  "returnedLines": 1000,
  "exists": true
}
```

### Clear Logs

```
POST /api/logs/clear
```

Clears today's log file.

**Response** (`200 OK`):
```json
{
  "status": "ok",
  "message": "Today's log file has been cleared",
  "path": "/Users/dev/.claude-mem/logs/claude-mem-2026-03-13.log"
}
```

## Health & Version

### Health Check

```
GET /api/health
```

Response (healthy):

```json
{"status":"ok","timestamp":1709000000000,"service":"claude-mem-java"}
```

Response (degraded, DB unreachable):

```json
{"status":"degraded","timestamp":1709000000000,"service":"claude-mem-java"}
```

### Readiness Check

```
GET /api/readiness
```

Checks if the service is fully ready to receive traffic.

**Response** (`200 OK`, ready):
```json
{
  "status": "ready",
  "checks": {
    "database": "ready",
    "queueDepth": 5,
    "queueStatus": "ready"
  },
  "timestamp": 1707878400000
}
```

**Status Codes**:
- `200` — Service ready
- `503` — Service not ready (e.g., database connection failed)

### Get Version

```
GET /api/version
```

**Response** (`200 OK`):
```json
{
  "version": "0.1.0-beta",
  "service": "claude-mem-java",
  "java": "24.0.1",
  "springBoot": "3.3.13"
}
```

> **Note**: The `java` field reflects the runtime JVM version and varies by deployment environment.

## Cursor

Cursor IDE integration endpoints for automatic context file updates.

### Register Project

```
POST /api/cursor/register
Content-Type: application/json
```

Registers a project for automatic Cursor context file updates. When new observations are recorded, the `.cursor/rules/claude-mem-context.mdc` file is automatically updated.

**Request Body**:
```json
{
  "projectName": "my-project",
  "workspacePath": "/path/to/project"
}
```

### Unregister Project

```
DELETE /api/cursor/register/{projectName}
```

### List Registered Projects

```
GET /api/cursor/projects
```

### Update Context

```
POST /api/cursor/context/{projectName}
```

Generates fresh context from observations and writes to `.cursor/rules/claude-mem-context.mdc`.

### Write Custom Context

```
POST /api/cursor/context/{projectName}/custom
Content-Type: application/json
```

**Request Body**:
```json
{
  "context": "# Custom Context\n\n..."
}
```

### Check Registration

```
GET /api/cursor/register/{projectName}
```

**Response** (`200 OK`, registered):
```json
{
  "registered": true,
  "projectName": "my-project",
  "workspacePath": "/path/to/project",
  "installedAt": 1709000000000
}
```

**Response** (`200 OK`, not registered):
```json
{
  "registered": false,
  "projectName": "my-project"
}
```

## Streaming

### SSE Stream

```
GET /stream
```

Server-Sent Events endpoint for real-time updates pushed to the Viewer WebUI.

**Request Example** (JavaScript):
```javascript
const eventSource = new EventSource('http://localhost:37777/stream');

eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('Event:', data.type, data);
};
```

**Event Types**:

| Type | Description |
|------|-------------|
| `initial_load` | Initial load (includes project list) |
| `processing_status` | Processing status update |
| `new_observation` | New observation created |
| `new_summary` | New summary created |
| `new_prompt` | New user prompt |

**Event Format**:
```json
{
  "type": "new_observation",
  "observation": {
    "id": "obs-uuid",
    "title": "Feature implementation",
    ...
  }
}
```

**Initial Load Event**:
```json
{
  "type": "initial_load",
  "projects": ["/path/to/project1", "/path/to/project2"],
  "timestamp": 1707878400000
}
```

**Processing Status Event**:
```json
{
  "type": "processing_status",
  "isProcessing": false,
  "queueDepth": 5
}
```

**Timeout**: 30 minutes (configurable via `claudemem.sse.timeout-ms`)

## Error Codes

### HTTP Status Codes

| Code | Description |
|------|-------------|
| 200 | OK |
| 201 | Created |
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 429 | Rate Limit Exceeded |
| 500 | Internal Server Error |
| 503 | Service Unavailable (health/readiness) |

### Business Error Codes

| Code | Description |
|------|-------------|
| `MISSING_FIELD` | Required field is missing |
| `INVALID_FORMAT` | Field format is invalid |
| `NOT_FOUND` | Resource not found |
| `RATE_LIMIT_EXCEEDED` | Rate limit triggered (10 req/60s per session) |
| `DB_ERROR` | Database operation failed |
| `LLM_ERROR` | LLM service call failed |
| `EMBEDDING_ERROR` | Embedding generation failed |

---

## Test Endpoints

> ⚠️ Only available in non-production environments (`@Profile("!prod")`). Use to validate AI model configuration and connectivity.

### Test LLM Connectivity

```
GET /api/test/llm
```

Sends a simple prompt to the configured LLM provider and returns the response.

**Response** (`200 OK`, success):
```json
{
  "status": "success",
  "message": "LLM (DeepSeek) is working!",
  "response": "Hello from DeepSeek!"
}
```

**Response** (`500`, failure):
```json
{
  "status": "error",
  "message": "LLM (DeepSeek) failed: ..."
}
```

### Test Embedding Connectivity

```
GET /api/test/embedding
```

Generates a test embedding vector and returns its dimensionality.

**Response** (`200 OK`, success):
```json
{
  "status": "success",
  "message": "Embedding (SiliconFlow BGE-M3) is working!",
  "dimensions": 1024
}
```

**Response** (`200 OK`, not configured):
```json
{
  "status": "disabled",
  "message": "Embedding is not configured (no API key)",
  "hint": "Set spring.ai.openai.embedding.api-key in application-dev.yml"
}
```

### Test All

```
GET /api/test/all
```

Runs both LLM and Embedding connectivity tests and returns combined results.

**Response** (`200 OK`):
```json
{
  "llm": {
    "status": "success",
    "message": "LLM is working!"
  },
  "embedding": {
    "status": "success",
    "dimensions": 1024
  }
}
```

---

## Usage Examples

### cURL Examples

#### 1. Health Check
```bash
curl http://localhost:37777/api/health
```

#### 2. Search Observations
```bash
curl "http://localhost:37777/api/search?project=/Users/dev/myproject&query=authentication&limit=5"
```

#### 3. Create Observation Directly
```bash
curl -X POST http://localhost:37777/api/ingest/observation \
  -H "Content-Type: application/json" \
  -d '{
    "content_session_id": "manual-session",
    "project_path": "/Users/dev/myproject",
    "type": "discovery",
    "title": "JWT expiration insight",
    "narrative": "JWT token expires after 24 hours",
    "facts": ["JWT token expires after 24 hours"],
    "concepts": ["authentication"],
    "source": "manual"
  }'
```

#### 4. Preview Context
```bash
curl "http://localhost:37777/api/context/preview?project=/Users/dev/myproject&maxObservations=10"
```

#### 5. Batch Get Observations
```bash
curl -X POST http://localhost:37777/api/observations/batch \
  -H "Content-Type: application/json" \
  -d '{
    "ids": ["obs-1", "obs-2", "obs-3"],
    "project": "/Users/dev/myproject"
  }'
```

#### 6. Get Logs
```bash
curl "http://localhost:37777/api/logs?lines=100"
```

---

### JavaScript Examples

#### 1. Using fetch API
```javascript
// Health check
const response = await fetch('http://localhost:37777/api/health');
const data = await response.json();
console.log('Health:', data);

// Search observations
const searchResponse = await fetch(
  'http://localhost:37777/api/search?' + new URLSearchParams({
    project: '/Users/dev/myproject',
    query: 'authentication',
    limit: 10
  })
);
const searchResults = await searchResponse.json();
console.log('Found:', searchResults.count, 'observations');
```

#### 2. SSE Event Stream
```javascript
const eventSource = new EventSource('http://localhost:37777/stream');

eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);

  switch (data.type) {
    case 'new_observation':
      console.log('New observation:', data.observation.title);
      break;
    case 'processing_status':
      console.log('Processing:', data.isProcessing, 'Queue:', data.queueDepth);
      break;
  }
};

eventSource.onerror = (error) => {
  console.error('SSE Error:', error);
  eventSource.close();
};
```

#### 3. Bulk Import
```javascript
const importData = {
  sessions: [...],
  observations: [...],
  summaries: [...],
  prompts: [...]
};

const response = await fetch('http://localhost:37777/api/import', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(importData)
});

const result = await response.json();
console.log('Imported:', result.stats);
```

---

### Python Examples

#### 1. Using requests
```python
import requests

BASE_URL = 'http://localhost:37777'

# Health check
response = requests.get(f'{BASE_URL}/api/health')
print('Health:', response.json())

# Search observations
params = {
    'project': '/Users/dev/myproject',
    'query': 'authentication',
    'limit': 10
}
response = requests.get(f'{BASE_URL}/api/search', params=params)
results = response.json()
print(f"Found {results['count']} observations")

# Create observation
data = {
    'content_session_id': 'manual-session',
    'project_path': '/Users/dev/myproject',
    'type': 'discovery',
    'title': 'Authentication insight',
    'narrative': 'Important finding about authentication',
    'facts': ['Important finding about authentication'],
    'concepts': ['authentication'],
    'source': 'manual'
}
response = requests.post(f'{BASE_URL}/api/ingest/observation', json=data)
print('Created:', response.json())
```

#### 2. Using SSE Client
```python
import sseclient

def listen_to_stream():
    response = requests.get(
        'http://localhost:37777/stream',
        stream=True
    )
    client = sseclient.SSEClient(response)

    for event in client.events():
        import json
        data = json.loads(event.data)
        print(f"Event: {data['type']}")
        if data['type'] == 'new_observation':
            print(f"  Title: {data['observation']['title']}")
```

---

## Appendix

### Data Models

#### Session
```json
{
  "id": "uuid",
  "session_id": "string",
  "project": "string",
  "user_prompt": "string",
  "started_at_epoch": 1707878400000,
  "completed_at_epoch": 1707882000000,
  "status": "active|completed|skipped",
  "cached_context": "string",
  "context_refreshed_at_epoch": 1707878400000
}
```

#### Observation
```json
{
  "id": "uuid",
  "content_session_id": "string",
  "project": "string",
  "title": "string",
  "subtitle": "string",
  "narrative": "string",
  "type": "bugfix|feature|refactor|discovery",
  "facts": ["string"],
  "concepts": ["string"],
  "files_read": ["string"],
  "files_modified": ["string"],
  "created_at_epoch": 1707878400000,
  "prompt_number": 1,
  "discovery_tokens": 150,
  "embedding_model_id": "bge-m3"
}
```

#### Summary
```json
{
  "id": "uuid",
  "session_id": "string",
  "project": "string",
  "request": "string",
  "completed": "string",
  "learned": "string",
  "next_steps": "string",
  "created_at_epoch": 1707878400000
}
```

#### UserPrompt
```json
{
  "id": "uuid",
  "content_session_id": "string",
  "project": "string",
  "prompt_text": "string",
  "prompt_number": 1,
  "created_at_epoch": 1707878400000
}
```

---

### Configuration

#### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Service port | 37777 |
| `SPRING_DATASOURCE_URL` | Database URL | jdbc:postgresql://127.0.0.1/claude_mem_dev |
| `SPRING_DATASOURCE_USERNAME` | Database username | postgres |
| `SPRING_DATASOURCE_PASSWORD` | Database password | (required) |
| `SPRING_AI_OPENAI_API_KEY` | LLM API Key | (required) |
| `SPRING_AI_OPENAI_BASE_URL` | LLM API Base URL | https://api.deepseek.com |
| `SPRING_AI_OPENAI_CHAT_MODEL` | LLM model | deepseek-chat |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | Embedding API Key | (required) |
| `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | Embedding API Base URL | https://api.siliconflow.cn |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | Embedding model | BAAI/bge-m3 |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | Embedding dimensions | 1024 |
| `SPRING_AI_ANTHROPIC_API_KEY` | Anthropic API Key (optional) | — |
| `SPRING_AI_ANTHROPIC_BASE_URL` | Anthropic API Base URL | https://api.anthropic.com |
| `SPRING_AI_ANTHROPIC_CHAT_MODEL` | Anthropic model | claude-sonnet-4-5 |
| `CLAUDEMEM_LLM_PROVIDER` | LLM provider (`openai` or `anthropic`) | openai |

> **Note**: Legacy variable names (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL`) are still supported as fallbacks.

#### application.yml Configuration

```yaml
server:
  port: 37777

claudemem:
  sse:
    timeout-ms: 1800000  # 30 minutes
  log:
    dir: ${user.home}/.claude-mem/logs

spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://127.0.0.1/claude_mem_dev}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD}
```

---

### FAQ

#### Q: How does rate limiting work?
A: Each `session_id` is limited to 10 tool-use requests within 60 seconds. Exceeding the limit returns a 429 status code.

#### Q: What to do when SSE connection times out?
A: Default timeout is 30 minutes. Clients should handle disconnection and auto-reconnect.

#### Q: How to debug API requests?
A: 1) Check log files at `~/.claude-mem/logs/claude-mem-{date}.log`
   2) Use the `debug=true` parameter (supported on some endpoints)

#### Q: How to avoid duplicates during import?
A: All import endpoints have automatic deduplication based on unique identifiers (e.g., `contentSessionId`, `id`).

---

## Changelog

| Date | Version | Changes |
|------|---------|---------|
| 2026-03-31 | 0.1.0-beta | Added Extraction (/run, /latest, /history), Cursor, Mode, Logs, Import, Viewer sections; Added Usage Examples, Appendix, Changelog; Synced with Chinese version |
| 2026-03-31 | 0.1.0-beta+ | Enriched Viewer, Management, Mode, Health, Cursor, Logs sections with parameter tables and response examples; synced with Chinese version completeness |
| 2026-03-31 | 0.1.0-beta++ | Added Session Start response example; corrected Delete Observation response (200 OK with body, not 204 No Content); synced Chinese changelog |
| 2026-03-31 | 0.1.0-beta+++ | Corrected Memory Refine (query param, not JSON body); corrected Feedback request fields (observationId/feedbackType, not session_id/feedback_type); added userId field to Experiences and ICL Prompt; synced Chinese version |
| 2026-03-31 | 0.1.0-beta++++ | Added Test endpoints section (/api/test/llm, /embedding, /all); added Overview section to Chinese version; synced TOC; synced changelog |
| 2026-03-31 | 0.1.0-beta+++++ | Added Get Session response example/path params/error response; added Update Session User path params/request body table/response example (3 fields); corrected environment variable names (SPRING_DATASOURCE_*, SPRING_AI_OPENAI_*) with defaults matching actual config; added Anthropic env vars; synced Chinese version |
| 2026-04-01 | 0.1.0-beta+++++ | Enriched Search section with full parameter types table, request example, and response example (strategy/fell_back/count); synced with Chinese version completeness |
| 2026-04-01 | 0.1.0-beta++++++ | Corrected Search strategy values — actual code returns hybrid/tsvector/filter/recent/none (not vector/text); updated response example and strategy description; synced Chinese version |
| 2026-04-01 | 0.1.0-beta+7 | Enriched English Ingest section with parameter tables, response examples, and error responses (was severely incomplete vs Chinese); enriched English Quality Distribution with parameter table, response example, and `unknown` field; enriched Chinese Quality Distribution with parameter table and `unknown` field; corrected `orderBy` example in Batch Get Observations (`created_at` → `created_at_epoch`); added parameter table to Create Observation (EN) |
| 2026-04-01 | 0.1.0-beta+8 | Corrected Get Settings response format — actual code returns 20 `CLAUDE_MEM_*` fields + modeName/modeDescription (not simple mode/modeName/modeDescription); documented all CLAUDE_MEM_* fields with types and defaults; corrected Update Settings to accept `CLAUDE_MEM_*` field names with `mode` shorthand; synced Chinese version |
| 2026-04-01 | 0.1.0-beta+9 | Enriched English PATCH /api/memory/observations section with path parameter, response example (`status:updated`), and error responses (was severely incomplete vs Chinese); added path parameter to English DELETE /api/memory/observations |
| 2026-04-02 | 0.1.0-beta+10 | Enriched English Streaming section with event types table, event format examples, JavaScript example, and timeout info (was severely incomplete vs Chinese); corrected Java version in Get Version response example (dynamic field, added note); added `CLAUDEMEM_LLM_PROVIDER` to environment variables table; synced Chinese version |
| 2026-04-02 | 0.1.0-beta+11 | Fixed Cursor Check Registration response — `projectPath` → `workspacePath` (matches wire format), added missing `installedAt` field and not-registered response example; synced Chinese version |
| 2026-04-02 | 0.1.0-beta+12 | Split Chinese Viewer mega-section into separate Search and Management sections (matching English structure); added missing parameter table to Chinese Create Observation endpoint; synced TOC |
| 2026-04-02 | 0.1.0-beta+13 | Fixed EN PATCH/DELETE /api/memory/observations path variable: `{observationId}` → `{id}` (matches controller @PatchMapping/@DeleteMapping); added Bulk Import response example with stats format; synced Chinese version |
| 2026-04-02 | 0.1.0-beta+14 | Added ICL Prompt 400 error response (task required); added Quality Distribution 400 error response (project required); synced Chinese version |
| 2026-04-03 | 0.1.0-beta+15 | Added Experiences endpoint response example (JSON array of Experience objects with id/task/strategy/outcome/reuse_condition/quality_score/created_at fields) and 400 error response (task required); synced Chinese version |
| 2026-03-13 | 0.1.0 | Initial API documentation |

---

*See also: [Chinese Version](API-zh-CN.md)*
