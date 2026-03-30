# Cortex Community Edition API Documentation

> **中文版**: [API-zh-CN.md](API-zh-CN.md)

> **Version**: 0.1.0-beta
> **Base URL**: `http://localhost:37777`
> **Protocol**: HTTP/1.1, SSE (Server-Sent Events)

This document describes the REST API for Cortex Community Edition backend.

## Table of Contents

- [Overview](#overview)
- [Authentication](#authentication)
- [Sessions](#sessions)
- [Messages](#messages)
- [Memory](#memory)
- [Observations](#observations)
- [Extraction](#extraction)
- [Context](#context)
- [Search](#search)
- [Management](#management)
- [Health & Version](#health--version)
- [Ingest](#ingest)
- [Cursor](#cursor)
- [Streaming](#streaming)
- [Error Codes](#error-codes)

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

### Get Session

```
GET /api/session/{sessionId}
```

### Update Session User

```
PATCH /api/session/{sessionId}/user
Content-Type: application/json

{
  "user_id": "user-123"
}
```

## Ingest

### Record Tool Use

```
POST /api/ingest/tool-use
Content-Type: application/json

{
  "session_id": "content-session-id",
  "tool_name": "Edit|Write|Read|Bash",
  "tool_input": {...},
  "tool_response": {...},
  "cwd": "/path/to/project"
}
```

### Record User Prompt

```
POST /api/ingest/user-prompt
Content-Type: application/json

{
  "session_id": "content-session-id",
  "prompt_text": "User prompt text",
  "prompt_number": 1,
  "cwd": "/path/to/project"
}
```

### Signal Session End

```
POST /api/ingest/session-end
Content-Type: application/json

{
  "session_id": "content-session-id",
  "cwd": "/path/to/project",
  "last_assistant_message": "optional assistant message"
}
```

### Create Observation Directly

```
POST /api/ingest/observation
Content-Type: application/json

{
  "content_session_id": "session-123",
  "project_path": "/path/to/project",
  "type": "feature",
  "title": "Added new API endpoint",
  "narrative": "Created a new REST endpoint for...",
  "facts": ["fact1", "fact2"],
  "concepts": ["api", "rest"],
  "source": "manual",
  "extractedData": {"key": "value"},
  "files_read": ["src/main/java/..."],
  "files_modified": ["src/main/java/..."]
}
```

## Memory

### Trigger Memory Refinement

```
POST /api/memory/refine
Content-Type: application/json

{
  "project_path": "/path/to/project"
}
```

### Update Observation

```
PATCH /api/memory/observations/{observationId}
Content-Type: application/json

{
  "quality_score": 0.95,
  "source": "manual",
  "extractedData": {"key": "value"}
}
```

### Delete Observation

```
DELETE /api/memory/observations/{observationId}
```

### Get Experiences (ExpRAG)

```
POST /api/memory/experiences
Content-Type: application/json

{
  "project": "/path/to/project",
  "query": "database optimization",
  "limit": 5
}
```

### Get ICL Prompt

```
POST /api/memory/icl-prompt
Content-Type: application/json

{
  "task": "database optimization",
  "project": "/path/to/project",
  "maxChars": 4000
}
```

### Get Quality Distribution

```
GET /api/memory/quality-distribution?project=/path/to/project
```

### Submit Feedback

```
POST /api/memory/feedback
Content-Type: application/json

{
  "session_id": "session-123",
  "feedback_type": "SUCCESS",
  "comment": "Task completed successfully"
}
```

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

Query parameters:

| Parameter | Required | Description |
|-----------|----------|-------------|
| `project` | Yes | Project path to search within |
| `query` | No | Search query text for semantic search. If empty, returns filter-only results |
| `type` | No | Filter by observation type |
| `concept` | No | Filter by observation concept |
| `source` | No | Filter by source |
| `limit` | No | Max results (default 20, max 100) |
| `offset` | No | Pagination offset (default 0) |

## Management

### Get Projects

```
GET /api/projects
```

### Get Project Statistics

```
GET /api/stats?project=/path/to/project
```

### Get Settings

```
GET /api/settings
```

### Update Settings

```
POST /api/settings
```

## Mode

### Get Current Mode

```
GET /api/mode
```

### Set Active Mode

```
PUT /api/mode
Content-Type: application/json

{
  "modeId": "code"
}
```

Switches the active mode at runtime. Supports base modes (e.g., "code") and inherited modes (e.g., "code--zh").

### List Observation Types

```
GET /api/mode/types
```

### List Observation Concepts

```
GET /api/mode/concepts
```

### Validate Type

```
GET /api/mode/types/{typeId}/validate
```

### Get Type Emoji

```
GET /api/mode/types/{typeId}/emoji
```

### List Valid Types

```
GET /api/mode/types/valid
```

### List Valid Concepts

```
GET /api/mode/concepts/valid
```

## Viewer

### List Observations

```
GET /api/observations?project=/path/to/project&type=tool_use&limit=50&offset=0
```

### Get Observations by IDs

```
POST /api/observations/batch
Content-Type: application/json

{
  "ids": ["obs-1", "obs-2", "obs-3"]
}
```

### List Summaries

```
GET /api/summaries?project=/path/to/project&limit=50&offset=0
```

### List Prompts

```
GET /api/prompts?project=/path/to/project&limit=50&offset=0
```

### Get Timeline

```
GET /api/timeline?project=/path/to/project
```

### Search by File

```
GET /api/search/by-file?project=/path/to/project&file=src/Main.java
```

### Get Processing Status

```
GET /api/processing-status
```

### Batch Get SDK Sessions

```
POST /api/sdk-sessions/batch
Content-Type: application/json

{
  "session_ids": ["session-1", "session-2"]
}
```

### List Modes

```
GET /api/modes
```

### Create Mode

```
POST /api/modes
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

### Clear Logs

```
POST /api/logs/clear
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

### Get Version

```
GET /api/version
```

## Cursor

Cursor IDE integration endpoints for automatic context file updates.

### Register Project

```
POST /api/cursor/register
Content-Type: application/json

{
  "projectName": "my-project",
  "projectPath": "/path/to/project"
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

{
  "content": "# Custom Context\n\n..."
}
```

### Check Registration

```
GET /api/cursor/register/{projectName}
```

## Streaming

### SSE Stream

```
GET /stream
```

Server-Sent Events endpoint for real-time updates.

## Error Codes

| Code | Description |
|------|-------------|
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 429 | Rate Limit Exceeded |
| 500 | Internal Server Error |
| 503 | Service Unavailable (health/readiness) |

---

*See also: [Chinese Version](API-zh-CN.md)*
