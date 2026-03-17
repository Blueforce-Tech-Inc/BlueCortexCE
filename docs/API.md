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
- [Search](#search)
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
