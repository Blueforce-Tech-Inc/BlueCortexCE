# Cortex Community Edition API Documentation

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
- [Search](#search)
- [Management](#management)
- [Health & Version](#health--version)
- [Ingest](#ingest)
- [WebUI](#webui)
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

[Chinese Version](API-zh-CN.md)

### API Key

Include your API key in the request header:

```
Authorization: Bearer YOUR_API_KEY
```

## Sessions

### Start Session

```
POST /api/session/start
Content-Type: application/json

{
  "session_id": "content-session-id",
  "cwd": "/path/to/project",
  "user_id": "user-123",
  "last_user_message": "Hello"
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
  "project": "/path/to/project",
  "query": "database optimization"
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

### Trigger Extraction

```
POST /api/extraction/run?project=/path/to/project
```

Triggers structured data extraction from conversation observations.

### Get Latest Extraction

```
GET /api/extraction/{templateName}/latest?project=/path/to/project&userId=user-123
```

### Get Extraction History

```
GET /api/extraction/{templateName}/history?project=/path/to/project&userId=user-123&limit=10
```

## Search

### Search Memory (Hybrid)

```
GET /api/search?project=/path/to/project&query=search+terms&limit=10&source=manual
```

### Search Memory (Vector)

```
POST /api/memory/search
Content-Type: application/json

{
  "project": "/path/to/project",
  "query": "search terms",
  "limit": 10
}
```

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

### Import Observations

```
POST /api/import/observations
```

### Import Sessions

```
POST /api/import/sessions
```

### Import Summaries

```
POST /api/import/summaries
```

### Import Prompts

```
POST /api/import/prompts
```

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
