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

### Create Session

```
POST /api/sessions
```

### Get Session

```
GET /api/sessions/{sessionId}
```

### List Sessions

```
GET /api/sessions
```

### Delete Session

```
DELETE /api/sessions/{sessionId}
```

## Messages

### Send Message

```
POST /api/sessions/{sessionId}/messages
```

### Get Messages

```
GET /api/sessions/{sessionId}/messages
```

## Memory

### Get Memory

```
GET /api/memory/{sessionId}
```

### Search Memory

```
POST /api/memory/search
```

### Trigger Memory Refinement

```
POST /api/memory/refine
Content-Type: application/json

{
  "project_path": "/path/to/project"
}
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

### List Observations

```
GET /api/observations?project=/path/to/project&type=tool_use&limit=50&offset=0
```

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `project` | string | Project path filter (required) |
| `type` | string | Observation type filter |
| `limit` | int | Max results to return |
| `offset` | int | Pagination offset |
| `session_id` | string | Filter by session |

### Get Observations by IDs

```
POST /api/observations/batch
Content-Type: application/json

{
  "ids": ["obs-1", "obs-2", "obs-3"]
}
```

### Update Observation

```
PATCH /api/memory/observations/{observationId}
Content-Type: application/json

{
  "quality_score": 0.95,
  "content": "Updated observation content"
}
```

### Delete Observation

```
DELETE /api/memory/observations/{observationId}
```

## Extraction

### Trigger Extraction

```
POST /api/extraction/run?project=/path/to/project
```

Triggers structured data extraction from conversation observations.

### Get Latest Extraction

```
GET /api/extraction/latest?project=/path/to/project&template=user_preference&userId=user-123
```

### Get Extraction History

```
GET /api/extraction/history?project=/path/to/project&template=user_preference&userId=user-123&limit=10
```

## Search

### Search Memory

```
POST /api/memory/search
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

### Get Memory Modes

```
GET /api/modes
```

### Get Settings

```
GET /api/settings
```

## Health & Version

### Health Check

```
GET /api/health
```

Response:

```json
{"service":"cortex-ce","status":"ok"}
```

### Get Version

```
GET /api/version
```

## Ingest

### Record User Prompt

```
POST /api/ingest/user-prompt
Content-Type: application/json

{
  "sessionId": "session-123",
  "projectPath": "/path/to/project",
  "content": "User prompt text"
}
```

### Signal Session End

```
POST /api/ingest/session-end
Content-Type: application/json

{
  "sessionId": "session-123",
  "projectPath": "/path/to/project"
}
```

## WebUI

### Get WebUI Status

```
GET /webui/status
```

### Stream Events

```
GET /webui/stream
```

## Error Codes

| Code | Description |
|------|-------------|
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 500 | Internal Server Error |

---

*See also: [Chinese Version](API-zh-CN.md)*
