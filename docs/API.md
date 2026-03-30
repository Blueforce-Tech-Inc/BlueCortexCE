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

**Field aliases**: `session_id` is accepted as an alias for `content_session_id`, `cwd` for `project_path`, and `content` for `narrative`.

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
  "title": "Updated title",
  "source": "manual",
  "extractedData": {"key": "value"}
}
```

Supported fields: `title`, `content` (or `narrative`), `subtitle`, `source`, `facts`, `concepts`, `extractedData`. Null values clear the field; absent fields are left unchanged.

### Delete Observation

```
DELETE /api/memory/observations/{observationId}
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
  "requiredConcepts": ["how-it-works"]
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
| `orderBy` | No | Order by field (accepted for MCP compatibility, not yet fully implemented) |

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
GET /api/observations?project=/path/to/project&limit=50&offset=0
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
GET /api/search/by-file?project=/path/to/project&filePath=/src/auth.ts&isFolder=false&limit=20
```

Query parameters:

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `project` | Yes | — | Project path |
| `filePath` | Yes | — | File or folder path to search for |
| `isFolder` | No | false | If true, match folder prefix |
| `limit` | No | 20 | Max results (max 100) |
| `debug` | No | false | Enable debug logging |

### Get Processing Status

```
GET /api/processing-status
```

### Batch Get SDK Sessions

```
POST /api/sdk-sessions/batch
Content-Type: application/json

{
  "contentSessionIds": ["session-1", "session-2"]
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

Switches the active mode at runtime. Supports base modes (e.g., "code") and inherited modes (e.g., "code--zh").

**Request Body**:
```json
{
  "mode": "code--zh"
}
```

**Response**:
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
  "contentSessionId": "string",
  "projectPath": "string",
  "userPrompt": "string",
  "startedAtEpoch": 1707878400000,
  "completedAtEpoch": 1707882000000,
  "status": "active|completed|skipped",
  "cachedContext": "string",
  "contextRefreshedAtEpoch": 1707878400000
}
```

#### Observation
```json
{
  "id": "uuid",
  "content_session_id": "string",
  "projectPath": "string",
  "title": "string",
  "subtitle": "string",
  "narrative": "string",
  "type": "bugfix|feature|refactor|discovery",
  "facts": ["string"],
  "concepts": ["string"],
  "filesRead": ["string"],
  "filesModified": ["string"],
  "createdAtEpoch": 1707878400000,
  "promptNumber": 1,
  "discoveryTokens": 150,
  "embeddingModelId": "bge-m3"
}
```

#### Summary
```json
{
  "id": "uuid",
  "session_id": "string",
  "projectPath": "string",
  "request": "string",
  "completed": "string",
  "learned": "string",
  "nextSteps": "string",
  "createdAtEpoch": 1707878400000
}
```

#### UserPrompt
```json
{
  "id": "uuid",
  "contentSessionId": "string",
  "projectPath": "string",
  "promptText": "string",
  "promptNumber": 1,
  "createdAtEpoch": 1707878400000
}
```

---

### Configuration

#### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Service port | 37777 |
| `DB_URL` | Database URL | jdbc:postgresql://127.0.0.1/claude_mem_dev |
| `DB_USERNAME` | Database username | postgres |
| `DB_PASSWORD` | Database password | (required) |
| `OPENAI_API_KEY` | LLM API Key | (required) |
| `OPENAI_BASE_URL` | LLM API Base URL | https://api.openai.com |
| `OPENAI_MODEL` | LLM model | gpt-4o |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | Embedding API Key | (required) |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | Embedding model | BAAI/bge-m3 |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | Embedding dimensions | 1024 |

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
    url: jdbc:postgresql://127.0.0.1/claude_mem_dev
    username: postgres
    password: ${DB_PASSWORD}
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
| 2026-03-13 | 0.1.0 | Initial API documentation |

---

*See also: [Chinese Version](API-zh-CN.md)*
