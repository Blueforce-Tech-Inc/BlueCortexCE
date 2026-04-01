# Cortex Community Edition API 文档

> **English Version**: [API.md](API.md)

> **版本**: 0.1.0-beta
> **基础URL**: `http://localhost:37777`
> **协议**: HTTP/1.1, SSE (Server-Sent Events)

---

## 概述

本文档描述 Cortex Community Edition 后端的 REST API。API 遵循 RESTful 原则，支持同步请求和 Server-Sent Events (SSE) 流式响应。

### 基础 URL

```
http://localhost:37777
```

### Content-Type

所有请求和响应使用 JSON 格式：

```
Content-Type: application/json
```

---

## 目录

1. [概述](#概述)
2. [认证](#认证)
3. [通用响应格式](#通用响应格式)
4. [错误码说明](#错误码说明)
5. [Health 健康检查](#health-健康检查)
6. [Session 会话管理](#session-会话管理)
7. [Context 上下文](#context-上下文)
8. [Ingestion 数据摄入](#ingestion-数据摄入)
9. [Extraction 结构化提取](#extraction-结构化提取)
10. [Viewer 查看器](#viewer-查看器)
11. [Memory 记忆管理](#memory-记忆管理)
12. [Mode 模式管理](#mode-模式管理)
13. [Logs 日志管理](#logs-日志管理)
14. [Import 数据导入](#import-数据导入)
15. [Cursor IDE 集成](#cursor-ide-集成)
16. [SSE 流式推送](#sse-流式推送)
17. [Test 测试端点](#test-测试端点)
18. [使用示例](#使用示例)
19. [附录](#附录)
20. [更新日志](#更新日志)

---

## 认证

**当前版本无认证要求**。所有端点在 `localhost:37777` 上开放访问。

> ⚠️ **生产环境警告**: 如果暴露到公网，请添加认证层（如 API Key、JWT 等）。

---

## 通用响应格式

### 成功响应

```json
{
  "status": "ok",
  "data": { ... }
}
```

### 分页响应

```json
{
  "items": [...],
  "hasMore": true
}
```

### 错误响应

```json
{
  "error": "Error message",
  "status": "failed",
  "code": "ERROR_CODE"
}
```

---

## 错误码说明

### HTTP 状态码

| 状态码 | 含义 | 说明 |
|--------|------|------|
| 200 | OK | 请求成功 |
| 201 | Created | 资源创建成功 |
| 400 | Bad Request | 请求参数错误 |
| 401 | Unauthorized | 未授权 |
| 403 | Forbidden | 禁止访问 |
| 404 | Not Found | 资源不存在 |
| 429 | Too Many Requests | 速率限制触发 |
| 500 | Internal Server Error | 服务器内部错误 |
| 503 | Service Unavailable | 服务不可用（数据库连接失败等） |

### 业务错误码

| 错误码 | 说明 |
|--------|------|
| `MISSING_FIELD` | 缺少必填字段 |
| `INVALID_FORMAT` | 字段格式错误 |
| `NOT_FOUND` | 资源不存在 |
| `RATE_LIMIT_EXCEEDED` | 速率限制触发（10 次/60秒） |
| `DB_ERROR` | 数据库操作失败 |
| `LLM_ERROR` | LLM 服务调用失败 |
| `EMBEDDING_ERROR` | 向量嵌入生成失败 |

---


---

## Health 健康检查

#### GET `/api/health`

基础健康检查端点，适合负载均衡器和 Kubernetes 探针。

**请求示例**:
```bash
curl http://localhost:37777/api/health
```

**响应示例**:
```json
{
  "status": "ok",
  "timestamp": 1707878400000,
  "service": "claude-mem-java"
}
```

---

#### GET `/api/readiness`

就绪检查端点，检查服务是否完全准备好接收流量。

**请求示例**:
```bash
curl http://localhost:37777/api/readiness
```

**响应示例**:
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

**状态码**:
- `200` - 服务就绪
- `503` - 服务未就绪（数据库连接失败等）

---

#### GET `/api/version`

获取服务版本信息。

**请求示例**:
```bash
curl http://localhost:37777/api/version
```

**响应示例**:
```json
{
  "version": "0.1.0-beta",
  "service": "claude-mem-java",
  "java": "21.0.2",
  "springBoot": "3.3.13"
}
```

---

## Session 会话管理

#### POST `/api/session/start`

初始化或恢复会话，生成上下文注入和 CLAUDE.md 更新。

**请求体**:
```json
{
  "session_id": "content-session-id",
  "project_path": "/path/to/project",
  "cwd": "/path/to/project",
  "projects": "project1,project2",
  "is_worktree": false,
  "parent_project": null
}
```

**字段说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `session_id` | string | ✅ | Claude Code 内容会话 ID |
| `project_path` | string | ✅ | 项目路径 |
| `cwd` | string | ❌ | 当前工作目录 |
| `projects` | string | ❌ | 多项目支持（逗号分隔） |
| `is_worktree` | boolean | ❌ | 是否为 worktree |
| `parent_project` | string | ❌ | 父项目名称（worktree 模式） |
| `user_id` | string | ❌ | 用户 ID（Phase 3 多用户支持） |

**响应示例**:
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

**错误响应**:
```json
{
  "error": "Missing required field: session_id"
}
```

---

#### GET `/api/session/{sessionId}`

根据内容会话 ID 获取会话信息。

**路径参数**:
- `sessionId` - 内容会话 ID

**请求示例**:
```bash
curl http://localhost:37777/api/session/abc-123-def
```

**响应示例**:
```json
{
  "session_db_id": "550e8400-e29b-41d4-a716-446655440000",
  "content_session_id": "mem-abc-123",
  "project_path": "/Users/dev/myproject",
  "status": "active",
  "started_at": "2026-03-13T10:15:00Z"
}
```

**错误响应**:
```json
{
  "error": "Session not found",
  "session_id": "abc-123-def"
}
```

---

#### PATCH `/api/session/{sessionId}/user`

更新会话关联的用户 ID。

**路径参数**:
- `sessionId` - 内容会话 ID

**请求体**:
```json
{
  "user_id": "user-123"
}
```

**请求示例**:
```bash
curl -X PATCH http://localhost:37777/api/session/abc-123-def/user \
  -H "Content-Type: application/json" \
  -d '{"user_id": "user-123"}'
```

**响应示例**:
```json
{
  "status": "ok",
  "sessionId": "abc-123-def",
  "userId": "user-123"
}
```

---

## Context 上下文

#### GET `/api/context/inject`

生成用于注入到 Claude Code 会话的上下文。

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `projects` | string | ❌ | 项目路径列表（逗号分隔） |

**请求示例**:
```bash
curl "http://localhost:37777/api/context/inject?projects=/Users/dev/myproject"
```

**响应示例**:
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

---

#### POST `/api/context/generate`

为单个项目生成上下文。

**请求体**:
```json
{
  "project_path": "/path/to/project"
}
```

**响应示例**:
```json
{
  "context": "# Recent Work\n\n..."
}
```

---

#### GET `/api/context/preview`

预览项目上下文（返回纯文本格式，用于 UI 显示）。

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `project` | string | (必填) | 项目路径 |
| `observationTypes` | string | "" | 观察类型过滤（逗号分隔） |
| `concepts` | string | "" | 概念过滤（逗号分隔） |
| `includeObservations` | boolean | true | 是否包含观察 |
| `includeSummaries` | boolean | true | 是否包含摘要 |
| `maxObservations` | int | 50 | 最大观察数量 |
| `maxSummaries` | int | 2 | 最大摘要数量 |
| `sessionCount` | int | 10 | 查询的最近会话数 |
| `fullCount` | int | 5 | 显示完整详情的观察数 |

**请求示例**:
```bash
curl "http://localhost:37777/api/context/preview?project=/Users/dev/myproject&maxObservations=20"
```

**响应示例** (text/plain):
```text
# Claude-Mem Context

Generated: 2026-03-13 10:15


**Type**: bugfix | **Concepts**: authentication
Fixed JWT token validation issue...

---
Token Savings Summary
- Total observations: 45
- Read tokens: 10,500
- Saved tokens: 95,000 (90%)
```

---

#### GET `/api/context/recent`

获取最近会话上下文摘要。

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `project` | string | (cwd) | 项目路径 |
| `limit` | int | 3 | 返回的会话数量 |

**请求示例**:
```bash
curl "http://localhost:37777/api/context/recent?project=/Users/dev/myproject&limit=5"
```

**响应示例**:
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

---

#### GET `/api/context/timeline`

获取时间线上下文（支持锚点查询）。

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `anchor` | string | ❌ | 锚点 ID（UUID 或会话 ID） |
| `depth_before` | int | 10 | 锚点前的项目数 |
| `depth_after` | int | 10 | 锚点后的项目数 |
| `project` | string | ❌ | 项目路径 |

**请求示例**:
```bash
curl "http://localhost:37777/api/context/timeline?anchor=obs-123&project=/Users/dev/myproject"
```

**响应示例**:
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

---

#### GET `/api/context/prior-messages`

获取上一个会话的消息（用于上下文连续性）。

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `project` | string | ✅ | 项目路径 |
| `currentSessionId` | string | ❌ | 当前会话 ID（用于排除） |

**请求示例**:
```bash
curl "http://localhost:37777/api/context/prior-messages?project=/Users/dev/myproject"
```

**响应示例**:
```json
{
  "userMessage": "Add authentication feature",
  "assistantMessage": "I'll implement the authentication feature..."
}
```

---

## Ingestion 数据摄入

这些端点由 Claude Code hooks（通过 `wrapper.js`）调用，用于异步处理事件。

#### POST `/api/ingest/tool-use`

记录工具使用事件，触发异步 LLM 处理生成观察。

**请求体**:
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

**字段说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `session_id` | string | ✅ | 内容会话 ID |
| `tool_name` | string | ✅ | 工具名称（Edit, Write, Read, Bash） |
| `tool_input` | object/string | ❌ | 工具输入参数 |
| `tool_response` | object/string | ❌ | 工具响应 |
| `cwd` | string | ❌ | 当前工作目录 |

**响应示例**:
```json
{
  "status": "accepted"
}
```

**错误响应**:
```json
{
  "error": "Rate limit exceeded",
  "retry_after": "45"
}
```

**速率限制**: 10 次/60秒/会话

---

#### POST `/api/ingest/session-end`

结束会话，触发异步摘要生成。

**请求体**:
```json
{
  "session_id": "content-session-id",
  "last_assistant_message": "Task completed successfully",
  "cwd": "/path/to/project"
}
```

**字段说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `session_id` | string | ✅ | 内容会话 ID |
| `last_assistant_message` | string | ❌ | 最后的助手消息 |
| `cwd` | string | ❌ | 当前工作目录 |

**响应示例**:
```json
{
  "status": "ok"
}
```

---

#### POST `/api/ingest/user-prompt`

记录用户提示。

**请求体**:
```json
{
  "session_id": "content-session-id",
  "prompt_text": "Add authentication feature",
  "prompt_number": 1,
  "cwd": "/path/to/project"
}
```

**字段说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `session_id` | string | ✅ | 内容会话 ID |
| `prompt_text` | string | ❌ | 提示文本 |
| `prompt_number` | int | ❌ | 提示编号（默认 1） |
| `cwd` | string | ❌ | 当前工作目录 |

**响应示例**:
```json
{
  "status": "ok"
}
```

---

#### POST `/api/ingest/observation`

直接创建观察（带自动嵌入）。**仅用于测试**。

**请求体**:
```json
{
  "content_session_id": "mem-abc-123",
  "project_path": "/path/to/project",
  "title": "Feature implementation",
  "subtitle": "Added authentication",
  "narrative": "Implemented JWT authentication...",
  "type": "feature",
  "facts": ["JWT tokens configured", "Middleware added"],
  "concepts": ["authentication", "security"],
  "source": "manual",
  "extractedData": {"key": "value"},
  "files_read": ["/src/auth.ts"],
  "files_modified": ["/src/middleware.ts"],
  "prompt_number": 1
}
```

**字段别名**: `session_id` 可替代 `content_session_id`，`cwd` 可替代 `project_path`，`content` 可替代 `narrative`。

**响应示例**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Feature implementation",
  "type": "feature",
  ...
}
```

---

## Extraction 结构化提取

Phase 3 结构化数据提取端点，从会话观察中提取结构化数据（如用户偏好、过敏信息等）。

#### POST `/api/extraction/run`

触发结构化数据提取。

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `projectPath` | string | ✅ | 项目路径 |

**请求示例**:
```bash
curl -X POST "http://localhost:37777/api/extraction/run?projectPath=/Users/dev/myproject"
```

**响应示例**:
```json
{
  "status": "ok",
  "projectPath": "/Users/dev/myproject",
  "message": "Extraction completed"
}
```

> ⚠️ **注意**: 此端点为同步执行——响应在提取完成后才返回，耗时取决于模板数量和观测数据量。

---

#### GET `/api/extraction/{templateName}/latest`

获取指定模板的最新提取结果。

**路径参数**:
- `templateName` - 提取模板名称（如 `user-preferences`、`allergy-info`）

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `projectPath` | string | ✅ | 项目路径 |
| `userId` | string | ❌ | 用户 ID（用户级别提取时使用） |

**请求示例**:
```bash
curl "http://localhost:37777/api/extraction/user-preferences/latest?projectPath=/Users/dev/myproject&userId=alice"
```

**响应示例**（有数据）:
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

**响应示例**（无数据）:
```json
{
  "status": "not_found",
  "template": "user-preferences",
  "message": "No extraction found"
}
```

---

#### GET `/api/extraction/{templateName}/history`

获取指定模板的提取历史。

**路径参数**:
- `templateName` - 提取模板名称

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `projectPath` | string | ✅ | 项目路径 |
| `userId` | string | ❌ | 用户 ID |
| `limit` | int | 10 | 返回数量 |

**请求示例**:
```bash
curl "http://localhost:37777/api/extraction/user-preferences/history?projectPath=/Users/dev/myproject&limit=5"
```

**响应示例**:
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

> ⚠️ 响应为 JSON 数组（非对象），每个元素包含 `sessionId`、`extractedData`、`createdAt`、`observationId` 字段。

---

## Viewer 查看器

WebUI 使用的端点，用于查看和搜索记忆。

#### GET `/api/observations`

分页获取观察列表。

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `project` | string | null | 项目路径过滤 |
| `offset` | int | 0 | 偏移量 |
| `limit` | int | 20 | 每页数量（最大 100） |

**请求示例**:
```bash
curl "http://localhost:37777/api/observations?project=/Users/dev/myproject&limit=10"
```

**响应示例**:
```json
{
  "items": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Feature implementation",
      "type": "feature",
      "narrative": "Implemented JWT authentication...",
      "projectPath": "/Users/dev/myproject",
      "createdAtEpoch": 1707878400000,
      ...
    }
  ],
  "hasMore": true
}
```

---

#### GET `/api/summaries`

分页获取摘要列表。

**查询参数**: 同 `/api/observations`

**响应格式**: 同 `/api/observations`（但返回摘要对象）

---

#### GET `/api/prompts`

分页获取用户提示列表。

**查询参数**: 同 `/api/observations`

**响应格式**: 同 `/api/observations`（但返回用户提示对象）

---

#### GET `/api/projects`

获取所有已知项目列表。

**请求示例**:
```bash
curl http://localhost:37777/api/projects
```

**响应示例**:
```json
{
  "projects": [
    "/Users/dev/myproject",
    "/Users/dev/another-project"
  ]
}
```

---

#### GET `/api/stats`

获取数据库和处理统计信息。

**请求示例**:
```bash
curl http://localhost:37777/api/stats
```

**响应示例**:
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

---

#### GET `/api/processing-status`

获取当前处理状态。

**请求示例**:
```bash
curl http://localhost:37777/api/processing-status
```

**响应示例**:
```json
{
  "isProcessing": false,
  "queueDepth": 5
}
```

---

#### GET `/api/search`

语义搜索 + 文本搜索。

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `project` | string | ✅ | 项目路径 |
| `query` | string | ❌ | 搜索查询 |
| `type` | string | ❌ | 类型过滤 |
| `concept` | string | ❌ | 概念过滤 |
| `source` | string | ❌ | 来源过滤（如 `manual`、`auto`） |
| `limit` | int | 20 | 结果数量 |
| `offset` | int | 0 | 偏移量 |
| `orderBy` | string | null | 排序字段 |

**请求示例**:
```bash
curl "http://localhost:37777/api/search?project=/Users/dev/myproject&query=authentication&limit=10"
```

**响应示例**:
```json
{
  "observations": [...],
  "strategy": "hybrid",
  "fell_back": false,
  "count": 10
}
```

**搜索策略说明**:
- `hybrid`: 组合搜索——pgvector 语义搜索 + PostgreSQL tsvector 全文搜索
- `tsvector`: PostgreSQL 全文搜索回退（pgvector 不可用时使用）
- `filter`: 纯过滤搜索（无查询文本，基于 type/concept/source 过滤条件）
- `recent`: 默认列表（无查询、无过滤，返回最新观察）
- `none`: 所有搜索方法均失败（返回空结果）

---

#### GET `/api/search/by-file`

根据文件/文件夹路径搜索观察。

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `project` | string | (必填) | 项目路径 |
| `filePath` | string | (必填) | 文件/文件夹路径 |
| `isFolder` | boolean | false | 是否为文件夹 |
| `limit` | int | 20 | 结果数量 |
| `debug` | boolean | false | 调试模式 |

**请求示例**:
```bash
curl "http://localhost:37777/api/search/by-file?project=/Users/dev/myproject&filePath=/src/auth&isFolder=true"
```

**响应示例**:
```json
{
  "observations": [...],
  "count": 5,
  "filePath": "/src/auth",
  "isFolder": true
}
```

---

#### POST `/api/observations/batch`

批量获取观察详情。

**请求体**:
```json
{
  "ids": ["id1", "id2", "id3"],
  "project": "/Users/dev/myproject",
  "orderBy": "created_at_epoch",
  "limit": 100
}
```

**响应示例**:
```json
{
  "observations": [...],
  "count": 3
}
```

---

#### GET `/api/timeline`

获取按日期分组的观察时间线。

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `project` | string | ✅ | 项目路径 |
| `startEpoch` | long | ❌ | 开始时间戳（默认 90 天前） |
| `endEpoch` | long | ❌ | 结束时间戳（默认现在） |
| `anchorId` | string | ❌ | 锚点观察 ID |
| `depthBefore` | int | ❌ | 锚点前项目数 |
| `depthAfter` | int | ❌ | 锚点后项目数 |
| `query` | string | ❌ | 查询（用于查找锚点） |

**请求示例**:
```bash
curl "http://localhost:37777/api/timeline?project=/Users/dev/myproject"
```

**响应示例**:
```json
[
  {
    "date": "2026-03-13",
    "count": 15,
    "ids": ["id1", "id2", ...]
  },
  {
    "date": "2026-03-12",
    "count": 8,
    "ids": ["id3", "id4", ...]
  }
]
```

**错误响应**:
```json
{
  "error": "Date range exceeds 1 year maximum"
}
```

---

#### POST `/api/sdk-sessions/batch`

批量查询会话信息（用于导出脚本）。

**请求体**:
```json
{
  "contentSessionIds": ["mem-1", "mem-2", "mem-3"]
}
```

**响应示例**:
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

---

#### GET `/api/settings`

获取当前设置，返回所有 `CLAUDE_MEM_*` 配置字段及活跃模式信息。

**请求示例**:
```bash
curl http://localhost:37777/api/settings
```

**响应示例**:
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

> **注意**: 具体字段值取决于当前 `settings.json` 和环境变量覆盖。`modeName` 和 `modeDescription` 由活跃的 Mode 配置注入。

---

#### POST `/api/settings`

保存设置。支持任意 `CLAUDE_MEM_*` 前缀字段。如果 `mode` 或 `CLAUDE_MEM_MODE` 变更，同时更新活跃模式。

**请求体**:
```json
{
  "CLAUDE_MEM_MODE": "all",
  "CLAUDE_MEM_MODEL": "gpt-4o-mini"
}
```

> **注意**: 也可以使用 `"mode": "all"` 作为 `"CLAUDE_MEM_MODE": "all"` 的简写。

**响应示例**:
```json
{
  "success": true
}
```

**错误响应** (`500`):
```json
{
  "success": false,
  "error": "Failed to save settings: ..."
}
```

---

#### GET `/api/modes`

获取当前活动模式配置。

**请求示例**:
```bash
curl http://localhost:37777/api/modes
```

**响应示例**:
```json
{
  "id": "code",
  "name": "Code Mode",
  "description": "Development workflow mode",
  "version": "1.0.0",
  "observationTypes": [...],
  "observationConcepts": [...]
}
```

---

#### POST `/api/modes`

设置活动模式。

**请求体**:
```json
{
  "mode": "code--zh"
}
```

**响应示例**:
```json
{
  "success": true,
  "mode": "code--zh",
  "name": "代码模式"
}
```

---

## Memory 记忆管理

#### POST `/api/memory/refine`

触发记忆精炼（异步）。

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `project` | string | ✅ | 项目绝对路径 |

**响应示例** (`200 OK`):
```json
{
  "status": "triggered",
  "project": "/Users/dev/my-project",
  "message": "Memory refinement event has been published"
}
```

**错误响应** (`400 Bad Request`):
```json
{
  "error": "project is required"
}
```

#### POST `/api/memory/experiences`

获取经验（ExpRAG）。

**请求体**:
```json
{
  "task": "database optimization",
  "project": "/path/to/project",
  "count": 5,
  "source": "manual",
  "requiredConcepts": ["how-it-works"],
  "userId": "user-123"
}
```

**字段说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `task` | string | ✅ | 任务或问题描述，用于查找相关经验 |
| `project` | string | ❌ | 项目路径（用于范围限定） |
| `count` | int | ❌ | 返回的最大经验数（默认 4） |
| `source` | string | ❌ | 来源过滤（如 `manual`、`tool_result`） |
| `requiredConcepts` | string[] | ❌ | 概念过滤（仅返回包含这些概念的经验） |
| `userId` | string | ❌ | 用户 ID（多用户隔离） |

#### POST `/api/memory/icl-prompt`

获取上下文学习提示。

**请求体**:
```json
{
  "task": "database optimization",
  "project": "/path/to/project",
  "maxChars": 4000,
  "userId": "user-123"
}
```

**字段说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `task` | string | ✅ | 当前任务/问题（用于上下文检索） |
| `project` | string | ❌ | 项目路径（用于范围限定） |
| `maxChars` | int | ❌ | 最大提示长度（默认 4000） |
| `userId` | string | ❌ | 用户 ID（多用户隔离） |

#### GET `/api/memory/quality-distribution`

获取质量分布统计（高/中/低/未知观察数量）。

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `project` | string | ✅ | 项目绝对路径 |

**响应示例** (`200 OK`):
```json
{
  "project": "/Users/dev/my-project",
  "high": 10,
  "medium": 20,
  "low": 5,
  "unknown": 3
}
```

**错误响应** (`500`):
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

#### POST `/api/memory/feedback`

提交反馈。

**请求体**:
```json
{
  "observationId": "550e8400-e29b-41d4-a716-446655440000",
  "feedbackType": "SUCCESS",
  "comment": "Task completed successfully"
}
```

**字段说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `observationId` | string | ✅ | 要提供反馈的观察 UUID |
| `feedbackType` | string | ✅ | 反馈类型（如 `SUCCESS`、`FAILURE`） |
| `comment` | string | ❌ | 可选的反馈评论 |

**响应示例** (`200 OK`):
```json
{
  "status": "ok",
  "observationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**错误响应**:
- `400` — 缺少 `observationId` 或 `feedbackType`，或 UUID 格式无效
- `404` — 观察不存在

#### PATCH `/api/memory/observations/{id}`

部分更新观察（仅更新请求体中包含的字段，null 值清空字段，未包含的字段保持不变）。

**路径参数**:
- `id` - 观察 UUID

**请求体**:
```json
{
  "title": "Updated title",
  "source": "manual",
  "extractedData": {"key": "value"}
}
```

**响应示例** (`200 OK`):
```json
{
  "status": "updated",
  "id": "550e8400-e29b-41d4-a716-446655440000"
}
```

支持的字段: `title`, `content`（或 `narrative`）, `subtitle`, `source`, `facts`, `concepts`, `extractedData`。null 值清空字段，缺失字段保持不变。

#### DELETE `/api/memory/observations/{id}`

删除观察。

**路径参数**:
- `id` - 观察 UUID

**响应** (`200 OK`):
```json
{
  "status": "deleted",
  "id": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

## Mode 模式管理

#### GET `/api/mode`

获取当前活动模式信息。

**请求示例**:
```bash
curl http://localhost:37777/api/mode
```

**响应示例**:
```json
{
  "modeId": "code",
  "name": "Code Mode",
  "description": "Development workflow mode",
  "version": "1.0.0",
  "observationTypes": [
    {
      "id": "bugfix",
      "label": "Bug Fix",
      "emoji": "🐛",
      "workEmoji": "🔧"
    }
  ],
  "observationConcepts": [
    {
      "id": "how-it-works",
      "label": "How It Works",
      "emoji": "⚙️"
    }
  ]
}
```

---

#### PUT `/api/mode`

切换活动模式。

**请求体**:
```json
{
  "modeId": "code--zh"
}
```

**响应示例**:
```json
{
  "modeId": "code--zh",
  "name": "代码模式",
  "description": "开发工作流模式",
  ...
}
```

---

#### GET `/api/mode/types`

获取当前模式的观察类型列表。

**请求示例**:
```bash
curl http://localhost:37777/api/mode/types
```

**响应示例**:
```json
[
  {
    "id": "bugfix",
    "label": "Bug Fix",
    "emoji": "🐛",
    "workEmoji": "🔧"
  }
]
```

---

#### GET `/api/mode/concepts`

获取当前模式的观察概念列表。

**请求示例**:
```bash
curl http://localhost:37777/api/mode/concepts
```

**响应示例**:
```json
[
  {
    "id": "how-it-works",
    "label": "How It Works",
    "emoji": "⚙️"
  }
]
```

---

#### GET `/api/mode/types/{typeId}/validate`

验证观察类型 ID 是否有效。

**请求示例**:
```bash
curl http://localhost:37777/api/mode/types/bugfix/validate
```

**响应示例**:
```json
{
  "valid": true
}
```

---

#### GET `/api/mode/types/{typeId}/emoji`

获取观察类型的 emoji 和标签。

**请求示例**:
```bash
curl http://localhost:37777/api/mode/types/bugfix/emoji
```

**响应示例**:
```json
{
  "emoji": "🐛",
  "workEmoji": "🔧",
  "label": "Bug Fix"
}
```

---

#### GET `/api/mode/types/valid`

获取所有有效的观察类型 ID。

**请求示例**:
```bash
curl http://localhost:37777/api/mode/types/valid
```

**响应示例**:
```json
["bugfix", "feature", "refactor", "discovery"]
```

---

#### GET `/api/mode/concepts/valid`

获取所有有效的观察概念 ID。

**请求示例**:
```bash
curl http://localhost:37777/api/mode/concepts/valid
```

**响应示例**:
```json
["how-it-works", "architecture", "best-practice"]
```

---

## Logs 日志管理

#### GET `/api/logs`

获取应用日志。

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `lines` | int | 1000 | 返回的最大行数 |

**请求示例**:
```bash
curl "http://localhost:37777/api/logs?lines=500"
```

**响应示例**:
```json
{
  "logs": "[2026-03-13 10:15:00] [INFO] [WORKER] Processing request...\n[2026-03-13 10:15:01] [DEBUG] [DB] Query executed in 23ms\n...",
  "path": "/Users/dev/.claude-mem/logs",
  "files": ["claude-mem-2026-03-13.log"],
  "totalLines": 1523,
  "returnedLines": 500,
  "exists": true
}
```

**日志格式**:
```
[timestamp] [LEVEL] [COMPONENT] [correlationId?] message
```

**示例**:
```
[2026-03-13 14:30:45.123] [INFO ] [WORKER] [obs-1-5] → Processing request
[2026-03-13 14:30:45.456] [DEBUG] [DB    ] [obs-1-5]     Query executed in 23ms
[2026-03-13 14:30:45.789] [ERROR] [HOOK  ]              ✗ Hook failed
```

---

#### POST `/api/logs/clear`

清空当日日志文件。

**请求示例**:
```bash
curl -X POST http://localhost:37777/api/logs/clear
```

**响应示例**:
```json
{
  "status": "ok",
  "message": "Today's log file has been cleared",
  "path": "/Users/dev/.claude-mem/logs/claude-mem-2026-03-13.log"
}
```

---

## Import 数据导入

#### POST `/api/import`

批量导入所有数据类型。

**请求体**:
```json
{
  "sessions": [
    {
      "id": "session-uuid",
      "contentSessionId": "content-123",
      "projectPath": "/path/to/project",
      "userPrompt": "Add feature",
      "startedAtEpoch": 1707878400000,
      "completedAtEpoch": 1707882000000,
      "status": "completed"
    }
  ],
  "observations": [
    {
      "id": "obs-uuid",
      "sessionId": "mem-123",
      "projectPath": "/path/to/project",
      "title": "Feature implementation",
      "narrative": "...",
      "type": "feature",
      "facts": ["..."],
      "concepts": ["..."],
      "createdAtEpoch": 1707878400000
    }
  ],
  "summaries": [...],
  "prompts": [...]
}
```

**响应示例**:
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

---

#### POST `/api/import/sessions`

仅导入会话。

**请求体**: 会话数组

**响应示例**:
```json
{
  "success": true,
  "imported": 10,
  "skipped": 2,
  "errors": 0,
  "errorMessages": []
}
```

---

#### POST `/api/import/observations`

仅导入观察。

**请求体**: 观察数组

**响应示例**:
```json
{
  "success": true,
  "imported": 45,
  "skipped": 5,
  "errors": 0,
  "errorMessages": []
}
```

---

#### POST `/api/import/summaries`

仅导入摘要。

**请求体**: 摘要数组

**响应格式**: 同 `/api/import/sessions`

---

#### POST `/api/import/prompts`

仅导入用户提示。

**请求体**: 用户提示数组

**响应格式**: 同 `/api/import/sessions`

---

## Cursor IDE 集成

Cursor IDE 集成端点，用于自动上下文文件更新。

#### POST `/api/cursor/register`

注册 Cursor 项目。

**请求体**:
```json
{
  "projectName": "my-project",
  "workspacePath": "/path/to/project"
}
```

**说明**: 注册后，当新观察被记录时，会自动更新 `.cursor/rules/claude-mem-context.mdc` 文件。

---

#### DELETE `/api/cursor/register/{projectName}`

取消注册 Cursor 项目。

---

#### GET `/api/cursor/projects`

获取所有已注册的 Cursor 项目列表。

---

#### POST `/api/cursor/context/{projectName}`

为已注册项目生成并更新 Cursor 上下文文件。

---

#### POST `/api/cursor/context/{projectName}/custom`

写入自定义上下文到 Cursor 文件。

**请求体**:
```json
{
  "context": "# Custom Context\n\n..."
}
```

---

#### GET `/api/cursor/register/{projectName}`

检查项目是否已注册。

**响应示例**:
```json
{
  "registered": true,
  "projectName": "my-project",
  "projectPath": "/path/to/project"
}
```

---

## SSE 流式推送

#### GET `/stream`

Server-Sent Events 端点，实时推送事件到 WebUI。

**请求示例**:
```javascript
const eventSource = new EventSource('http://localhost:37777/stream');

eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('Event:', data.type, data);
};
```

**事件类型**:

| 类型 | 说明 |
|------|------|
| `initial_load` | 初始加载（包含项目列表） |
| `processing_status` | 处理状态更新 |
| `new_observation` | 新观察创建 |
| `new_summary` | 新摘要创建 |
| `new_prompt` | 新用户提示 |

**事件格式**:
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

**初始加载事件**:
```json
{
  "type": "initial_load",
  "projects": ["/path/to/project1", "/path/to/project2"],
  "timestamp": 1707878400000
}
```

**处理状态事件**:
```json
{
  "type": "processing_status",
  "isProcessing": false,
  "queueDepth": 5
}
```

**超时**: 30 分钟（可配置）

---

## Test 测试端点

> ⚠️ 仅在非生产环境可用（`@Profile("!prod")`）。用于验证 AI 模型配置和连接性。

#### GET `/api/test/llm`

测试 LLM 连接性。发送简单提示并返回模型响应。

**请求示例**:
```bash
curl http://localhost:37777/api/test/llm
```

**响应示例** (`200 OK`):
```json
{
  "status": "success",
  "message": "LLM (DeepSeek) is working!",
  "response": "Hello from DeepSeek!"
}
```

**错误响应** (`500`):
```json
{
  "status": "error",
  "message": "LLM (DeepSeek) failed: ..."
}
```

---

#### GET `/api/test/embedding`

测试 Embedding 连接性。生成测试向量并返回维度信息。

**请求示例**:
```bash
curl http://localhost:37777/api/test/embedding
```

**响应示例** (`200 OK`):
```json
{
  "status": "success",
  "message": "Embedding (SiliconFlow BGE-M3) is working!",
  "dimensions": 1024
}
```

**未配置时** (`200 OK`):
```json
{
  "status": "disabled",
  "message": "Embedding is not configured (no API key)",
  "hint": "Set spring.ai.openai.embedding.api-key in application-dev.yml"
}
```

---

#### GET `/api/test/all`

同时运行 LLM 和 Embedding 测试。

**请求示例**:
```bash
curl http://localhost:37777/api/test/all
```

**响应示例** (`200 OK`):
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

## 使用示例

### cURL 示例

#### 1. 健康检查
```bash
curl http://localhost:37777/api/health
```

#### 2. 搜索观察
```bash
curl "http://localhost:37777/api/search?project=/Users/dev/myproject&query=authentication&limit=5"
```

#### 3. 直接创建观察
```bash
curl -X POST http://localhost:37777/api/ingest/observation \
  -H "Content-Type: application/json" \
  -d '{
    "content_session_id": "manual-session",
    "project_path": "/Users/dev/myproject",
    "type": "discovery",
    "title": "JWT 过期洞察",
    "narrative": "JWT token 24 小时后过期",
    "facts": ["JWT token 24 小时后过期"],
    "concepts": ["authentication"],
    "source": "manual"
  }'
```

#### 4. 获取上下文预览
```bash
curl "http://localhost:37777/api/context/preview?project=/Users/dev/myproject&maxObservations=10"
```

#### 5. 批量获取观察
```bash
curl -X POST http://localhost:37777/api/observations/batch \
  -H "Content-Type: application/json" \
  -d '{
    "ids": ["obs-1", "obs-2", "obs-3"],
    "project": "/Users/dev/myproject"
  }'
```

#### 6. 获取日志
```bash
curl "http://localhost:37777/api/logs?lines=100"
```

---

### JavaScript 示例

#### 1. 使用 fetch API
```javascript
// 健康检查
const response = await fetch('http://localhost:37777/api/health');
const data = await response.json();
console.log('Health:', data);

// 搜索观察
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

#### 2. SSE 事件流
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

#### 3. 批量导入
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

### Python 示例

#### 1. 使用 requests 库
```python
import requests

BASE_URL = 'http://localhost:37777'

# 健康检查
response = requests.get(f'{BASE_URL}/api/health')
print('Health:', response.json())

# 搜索观察
params = {
    'project': '/Users/dev/myproject',
    'query': 'authentication',
    'limit': 10
}
response = requests.get(f'{BASE_URL}/api/search', params=params)
results = response.json()
print(f"Found {results['count']} observations")

# 保存记忆
data = {
    'content_session_id': 'manual-session',
    'project_path': '/Users/dev/myproject',
    'type': 'discovery',
    'title': '认证洞察',
    'narrative': '关于认证的重要发现',
    'facts': ['关于认证的重要发现'],
    'concepts': ['authentication'],
    'source': 'manual'
}
response = requests.post(f'{BASE_URL}/api/ingest/observation', json=data)
print('Created:', response.json())
```

#### 2. 使用 SSE 客户端
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

## 附录

### 数据模型

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

### 配置

#### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `SERVER_PORT` | 服务端口 | 37777 |
| `SPRING_DATASOURCE_URL` | 数据库 URL | jdbc:postgresql://127.0.0.1/claude_mem_dev |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 | postgres |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | (required) |
| `SPRING_AI_OPENAI_API_KEY` | LLM API Key | (required) |
| `SPRING_AI_OPENAI_BASE_URL` | LLM API Base URL | https://api.deepseek.com |
| `SPRING_AI_OPENAI_CHAT_MODEL` | LLM 模型 | deepseek-chat |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | 嵌入 API Key | (required) |
| `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | 嵌入 API Base URL | https://api.siliconflow.cn |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | 嵌入模型 | BAAI/bge-m3 |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | 嵌入维度 | 1024 |
| `SPRING_AI_ANTHROPIC_API_KEY` | Anthropic API Key（可选） | — |
| `SPRING_AI_ANTHROPIC_BASE_URL` | Anthropic API Base URL | https://api.anthropic.com |
| `SPRING_AI_ANTHROPIC_CHAT_MODEL` | Anthropic 模型 | claude-sonnet-4-5 |

> **注意**: 旧版变量名（`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL`）仍作为 fallback 支持。

#### application.yml 配置

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

### 常见问题

#### Q: 速率限制如何工作？
A: 每个 `session_id` 在 60 秒内最多 10 次工具使用请求。超过限制返回 429 状态码。

#### Q: SSE 连接超时怎么办？
A: 默认超时 30 分钟。客户端应处理连接断开并自动重连。

#### Q: 如何调试 API 请求？
A: 1) 检查日志文件 `~/.claude-mem/logs/claude-mem-{date}.log`
   2) 使用 `debug=true` 参数（部分端点支持）

#### Q: 导入时如何避免重复？
A: 所有导入端点都有自动去重检查，基于唯一标识符（如 `contentSessionId`、`id` 等）。

---

## 更新日志

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-03-31 | 0.1.0-beta | 新增 Extraction (/run, /latest, /history)、Cursor、Mode、Logs、Import、Viewer 章节；修复 Session API 路径；同步英文版完整结构 |
| 2026-03-31 | 0.1.0-beta+ | 补充 Viewer、Management、Mode、Health、Cursor、Logs 参数表和响应示例；同步英文版完整度 |
| 2026-03-31 | 0.1.0-beta++ | 修正 Delete Observation 响应（200 OK with body，非 204 No Content）；同步英文版 Session Start 响应示例 |
| 2026-03-31 | 0.1.0-beta+++ | 修正 Memory Refine（查询参数，非 JSON 请求体）；修正 Feedback 请求字段（observationId/feedbackType，非 session_id/feedback_type）；补充 Experiences 和 ICL Prompt 的 userId 字段；同步英文版 |
| 2026-03-31 | 0.1.0-beta+++++ | 补充 Get Session 响应示例/路径参数/错误响应；补充 Update Session User 路径参数/请求体表/响应示例（3 字段）；修正环境变量名（SPRING_DATASOURCE_*、SPRING_AI_OPENAI_*），默认值匹配实际配置；新增 Anthropic 环境变量；同步英文版 |
| 2026-03-31 | 0.1.0-beta++++ | 新增 Test 测试端点章节（/api/test/llm、/embedding、/all）；补充概述章节（Base URL + Content-Type）；同步 TOC；同步更新日志 |
| 2026-04-01 | 0.1.0-beta+++++ | 补充英文版 Search 章节完整参数类型表、请求示例和响应示例（strategy/fell_back/count）；同步中英文版完整度 |
| 2026-04-01 | 0.1.0-beta++++++ | 修正搜索策略值——实际代码返回 hybrid/tsvector/filter/recent/none（非 vector/text）；更新响应示例和策略说明；同步英文版 |
| 2026-04-01 | 0.1.0-beta+7 | 补充英文版 Ingest 章节（参数表、响应示例、错误响应——中文版已完整但英文版严重缺失）；补充中英文 Quality Distribution 参数表、响应示例及 `unknown` 字段；修正 Batch Get Observations `orderBy` 示例（`created_at` → `created_at_epoch`）；补充英文版 Create Observation 参数表 |
| 2026-04-01 | 0.1.0-beta+8 | 修正 Get Settings 响应格式——实际代码返回 20 个 `CLAUDE_MEM_*` 字段 + modeName/modeDescription（非简单 mode/modeName/modeDescription）；记录所有 CLAUDE_MEM_* 字段及类型和默认值；修正 Update Settings 接受 `CLAUDE_MEM_*` 字段名及 `mode` 简写；同步英文版；修正 PATCH observations 响应 status 值 `ok` → `updated` |
| 2026-03-13 | 0.1.0 | 初始 API 文档 |

---

**文档维护**: 本文档应随 API 变更同步更新。如有疑问，请参考源代码 Controller 类或提交 Issue。
