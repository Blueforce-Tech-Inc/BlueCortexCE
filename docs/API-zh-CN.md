# Cortex Community Edition API 文档

> **English Version**: [API.md](API.md)

> **版本**: 0.1.0-SNAPSHOT
> **基础URL**: `http://localhost:37777`
> **协议**: HTTP/1.1, SSE (Server-Sent Events)

---

## 目录

1. [认证](#认证)
2. [通用响应格式](#通用响应格式)
3. [错误码说明](#错误码说明)
4. [API 端点](#api-端点)
   - [健康检查](#健康检查)
   - [Session 管理](#session-管理)
   - [Context 上下文](#context-上下文)
   - [Ingestion 数据摄入](#ingestion-数据摄入)
   - [Viewer 查看器](#viewer-查看器)
   - [Memory 记忆](#memory-记忆)
   - [Mode 模式](#mode-模式)
   - [Logs 日志](#logs-日志)
   - [Import 导入](#import-导入)
   - [SSE 流式推送](#sse-流式推送)
5. [使用示例](#使用示例)

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

## API 端点

---

### 健康检查

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
  "version": "0.1.0-SNAPSHOT",
  "service": "claude-mem-java",
  "java": "21.0.2",
  "springBoot": "3.3.13"
}
```

---

### Session 管理

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

### Context 上下文

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

### Ingestion 数据摄入

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
| `debug` | boolean | ❌ | 调试模式（返回额外信息） |

**响应示例**:
```json
{
  "status": "ok"
}
```

**调试模式响应**:
```json
{
  "status": "ok",
  "debug_session_id": "content-session-id",
  "debug_last_assistant_message": "Task completed successfully"
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
  "files_read": ["/src/auth.ts"],
  "files_modified": ["/src/middleware.ts"],
  "prompt_number": 1
}
```

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

### Viewer 查看器

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
  "strategy": "semantic",
  "fell_back": false,
  "count": 10
}
```

**策略说明**:
- `semantic`: 语义向量搜索（使用 pgvector）
- `text`: 文本搜索回退（LIKE/ILIKE）

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

获取当前设置。

**请求示例**:
```bash
curl http://localhost:37777/api/settings
```

**响应示例**:
```json
{
  "mode": "code",
  "modeName": "Code Mode",
  "modeDescription": "Development workflow mode",
  ...
}
```

---

#### POST `/api/settings`

保存设置。

**请求体**:
```json
{
  "mode": "code--zh",
  ...
}
```

**响应示例**:
```json
{
  "success": true
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

### Memory 记忆

#### POST `/api/memory/save`

手动保存记忆/观察。

**请求体**:
```json
{
  "text": "Important discovery about authentication flow...",
  "title": "Auth flow insight",
  "project": "/Users/dev/myproject"
}
```

**字段说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `text` | string | ✅ | 记忆内容 |
| `title` | string | ❌ | 标题（默认取文本前 60 字符） |
| `project` | string | ❌ | 项目路径（默认 "manual-memories"） |

**响应示例**:
```json
{
  "success": true,
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Auth flow insight",
  "project": "/Users/dev/myproject",
  "message": "Memory saved as observation #550e8400-e29b-41d4-a716-446655440000"
}
```

**错误响应**:
```json
{
  "success": false,
  "error": "text is required and must be non-empty"
}
```

---

#### POST `/api/memory/refine`

触发记忆精炼（异步）。

**请求体**:
```json
{
  "project_path": "/path/to/project"
}
```

**响应示例**:
```json
{
  "status": "triggered",
  "message": "Memory refinement started"
}
```

#### POST `/api/memory/experiences`

获取经验（ExpRAG）。

**请求体**:
```json
{
  "project": "/path/to/project",
  "query": "database optimization",
  "limit": 5
}
```

#### POST `/api/memory/icl-prompt`

获取上下文学习提示。

**请求体**:
```json
{
  "project": "/path/to/project",
  "query": "database optimization"
}
```

#### GET `/api/memory/quality-distribution`

获取质量分布统计。

**响应示例**:
```json
{
  "project": "/path/to/project",
  "high": 10,
  "medium": 5,
  "low": 2
}
```

#### POST `/api/memory/feedback`

提交反馈。

**请求体**:
```json
{
  "session_id": "session-123",
  "feedback_type": "SUCCESS",
  "comment": "Task completed successfully"
}
```

---

### Mode 模式

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

### Logs 日志

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

### Import 导入

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

### SSE 流式推送

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

#### 3. 保存手动记忆
```bash
curl -X POST http://localhost:37777/api/memory/save \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Important insight: JWT tokens expire after 24 hours",
    "title": "JWT expiration",
    "project": "/Users/dev/myproject"
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
    'text': 'Important insight about authentication',
    'title': 'Auth insight',
    'project': '/Users/dev/myproject'
}
response = requests.post(f'{BASE_URL}/api/memory/save', json=data)
print('Saved:', response.json())
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
| `DB_URL` | 数据库 URL | jdbc:postgresql://127.0.0.1/claude_mem_dev |
| `DB_USERNAME` | 数据库用户名 | postgres |
| `DB_PASSWORD` | 数据库密码 | (required) |
| `OPENAI_API_KEY` | LLM API Key | (required) |
| `OPENAI_BASE_URL` | LLM API Base URL | https://api.openai.com |
| `OPENAI_MODEL` | LLM 模型 | gpt-4o |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | 嵌入 API Key | (required) |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | 嵌入模型 | BAAI/bge-m3 |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | 嵌入维度 | 1024 |

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
    url: jdbc:postgresql://127.0.0.1/claude_mem_dev
    username: postgres
    password: ${DB_PASSWORD}
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
| 2026-03-13 | 0.1.0 | 初始 API 文档 |

---

**文档维护**: 本文档应随 API 变更同步更新。如有疑问，请参考源代码 Controller 类或提交 Issue。
