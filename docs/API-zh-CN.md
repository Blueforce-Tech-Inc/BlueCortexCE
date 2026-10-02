# Cortex Community Edition API 文档

> **English Version**: [API.md](API.md)

> **版本**: 0.1.0-beta
> **基础URL**: `http://localhost:37777`
> **协议**: HTTP/1.1, SSE (Server-Sent Events)

> **文档结构说明（中英差异）**：中文版用路径式 H4 标题标注端点（如 ``#### GET `/api/observations` ``），
> 英文版用描述式 H3 标题（如 `### List Observations`）并在标题下方用代码块给出请求行。这是**有意保留**
> 的差异——路径式标题在中文语境下更自然，且两版对每个端点都给出了相同的 HTTP 方法与路径。端点集合已核验
> 完全一致（67 个生效端点，对照 Controller 的 `@*Mapping` 注解逐一比对），因此这只是呈现方式差异，
> 不存在内容缺失。请勿为了"对齐"而单方面修改某一版本。
>
> 中文版路径式 H4 的主要层级是「`##` 章节 → `####` 端点」——即 H4 直接挂在所属章节下，中间不插入
> 描述式 H3；少数端点（如 Extraction、Search、Summaries 等章节）位于描述式 H3 之下。这种层级本身
> 并不统一，是历史沿革，**不是缺失的父级标题**，请勿"补齐"或"统一"。

---

## 目录

1. [概述](#概述)
2. [认证](#认证)
3. [通用响应格式](#通用响应格式)
4. [Session 会话管理](#session-会话管理)
5. [Ingestion 数据摄入](#ingestion-数据摄入)
6. [Memory 记忆管理](#memory-记忆管理)
7. [Extraction 结构化提取](#extraction-结构化提取)
8. [Context 上下文](#context-上下文)
9. [搜索](#搜索)
10. [管理](#管理)
11. [Mode 模式](#mode-模式)
12. [Viewer 查看器](#viewer-查看器)
13. [Import 数据导入](#import-数据导入)
14. [Logs 日志管理](#logs-日志管理)
15. [Health 健康检查](#health-健康检查)
16. [Cursor IDE 集成](#cursor-ide-集成)
17. [SSE 流式推送](#sse-流式推送)
18. [错误码说明](#错误码说明)
19. [Test 测试端点](#test-测试端点)
20. [使用示例](#使用示例)
21. [附录](#附录)
22. [更新日志](#更新日志)

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
  "parent_project": null,
  "user_id": "user-123"
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
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
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
- `400` — `{"error": "Missing required field: session_id"}`（`session_id` 缺失或为空）
- `400` — `{"error": "Missing required field: project_path (or cwd)"}`（`project_path` 和 `cwd` 均缺失或为空）
- `500` — `{"error": "Failed to initialize session"}`（内部错误）

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

> **`cwd` 在此为可选，但仅仅意味着后端不会拒绝它。** 省略它与发送 `"cwd": ""`
> 都会返回 `200 {"status": "accepted"}`，且该记录会以空项目路径入队——因此不会出现在
> 任何按项目过滤的查询结果中。`POST /api/ingest/observation` 是唯一把项目路径视为必填的
> 同级端点，它会返回 `400 Missing required field: project_path`。四家 SDK 均在发送前
> 于客户端拒绝空 `cwd`，因此这只影响直接使用 HTTP API 的调用方。

**响应示例**:
```json
{
  "status": "accepted"
}
```

**错误响应**:
- `400` — `{"error": "Missing required field: session_id"}` 或 `{"error": "Missing required field: tool_name"}`（缺少必填字段）
- `429` — `{"error": "Rate limit exceeded", "retry_after": "45"}`（速率限制）

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

> `cwd` 缺失**或**为空都会被接受，会话仍会正常结束；由它触发的摘要会以空项目路径归档。
> 该端点只强制 `session_id`，而更严格的同级端点 `POST /api/ingest/observation` 会对空项目
> 路径返回 `400`。四家 SDK 均在客户端要求 `cwd`。

**响应示例**:
```json
{
  "status": "ok"
}
```

**错误响应**:
- `400` — `{"error": "Missing required field: session_id"}`（缺少必填字段）

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

> 两个可选字段在**缺失或为空**时都会被接受：仅携带 `session_id` 的请求体会返回
> `200 {"status": "ok"}`，且该提示会以「无文本、无项目」的形式存入。该端点只强制
> `session_id`。`POST /api/ingest/observation` 对项目路径更严格，会返回
> `400 Missing required field: project_path`。四家 SDK 均在客户端同时要求
> `prompt_text` 与 `cwd`。

**响应示例**:
```json
{
  "status": "ok"
}
```

**错误响应**:
- `400` — `{"error": "Missing required field: session_id"}`（缺少必填字段）

---

#### POST `/api/ingest/observation`

直接创建观察（带自动嵌入）。**仅用于测试**。

**请求体字段**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `content_session_id` | string | ✅ | 内容会话 ID（可使用 `session_id` 别名） |
| `project_path` | string | ✅ | 项目路径（可使用 `cwd` 别名） |
| `type` | string | ❌ | 观察类型（如 `feature`、`bugfix`） |
| `title` | string | ❌ | 观察标题 |
| `subtitle` | string | ❌ | 观察副标题 |
| `narrative` | string | ❌ | 观察叙述（可使用 `content` 别名） |
| `facts` | string[] | ❌ | 事实陈述列表 |
| `concepts` | string[] | ❌ | 概念标签列表 |
| `source` | string | ❌ | 来源标识（如 `manual`） |
| `extractedData` | object | ❌ | 结构化提取数据 |
| `files_read` | string[] | ❌ | 已读取文件列表 |
| `files_modified` | string[] | ❌ | 已修改文件列表 |
| `prompt_number` | int | ❌ | 提示编号（用于排序） |

**字段别名**: `session_id` 可替代 `content_session_id`，`cwd` 可替代 `project_path`，`content` 可替代 `narrative`。

**请求示例**:
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

**响应示例**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Feature implementation",
  "type": "feature",
  ...
}
```

**错误响应**:
- `400` — `{"error": "Missing required field: content_session_id (or session_id)"}`（缺少必填字段）
- `400` — `{"error": "Missing required field: project_path"}`（缺少必填字段）

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
| `project` | string | ❌ | 项目路径（用于范围限定）。**省略会返回空数组——并不存在「全部项目」模式**，实践上应视为必填，详见下方说明 |
| `count` | int | ❌ | 返回的最大经验数（默认 4） |
| `source` | string | ❌ | 来源过滤（如 `manual`、`tool_result`） |
| `requiredConcepts` | string[] | ❌ | 概念过滤（仅返回包含这些概念的经验） |
| `userId` | string | ❌ | 用户 ID（多用户隔离）。**省略它会返回该项目中所有用户的经验**，因此漏传的调用方拿到的是一个「看起来正常」的未限定结果而非报错——详见下方说明 |

**响应示例** (`200 OK`): JSON 数组格式的经验对象：
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

**错误响应**:
- `400` — `{"error": "task is required"}`（`task` 字段缺失或为空）

**`userId` 限定的真实行为**：`userId` 是**会话**属性；`mem_observations` 没有用户列，
因此该过滤是先把 id 解析成该用户的会话 id 集合，再只检索这些会话。活体实测（项目内
仅有一条记在 `alice` 名下的观测）：`userId: "alice"` 返回 1 条经验，`userId: "bob"`
返回 **0** 条，而**省略 `userId` 返回 5 条**——所有人的、未限定的。因此传错 id 是
「安全失败」（空结果而非报错），**漏传 id 才是危险的那一种**，因为响应看起来完全正常。
`POST /api/memory/icl-prompt` 行为相同。相比之下 `GET /api/search` 与
`GET /api/observations` **根本不接受 `userId`**，始终是项目级的。

**`project` 标为可选却实际必填的原因**：该值被直接传入仓储查询
（`findBySource` / `findHighQualityObservations`），代码中**没有任何跨全部项目的分支**。
因此缺失、传空串或传不存在的项目都匹配不到任何内容，返回 `200` 加 `[]`——
这一结果与「该项目确实没有经验」**无法区分**。活体实测：同一请求在
`project` 省略、传 `""`、传不存在路径三种情况下均返回 `200 []`，
而传真实项目路径返回 5 条经验。空的 `project` **不会**被拒绝——只有空的 `task` 会。
`POST /api/memory/icl-prompt` 同理，此时返回 `experienceCount: 0` 与 28 字符的空提示。

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
| `project` | string | ❌ | 项目路径（用于范围限定）。与 `POST /api/memory/experiences` 同理：省略会得到空提示，而非跨项目提示 |
| `maxChars` | int | ❌ | 最大提示长度（默认 4000） |
| `userId` | string | ❌ | 用户 ID（多用户隔离） |

**响应示例** (`200 OK`):
```json
{
  "prompt": "# Relevant Experiences\n\n...",
  "experienceCount": 3,
  "maxChars": 4000
}
```

**错误响应**:
- `400` — `{"error": "task is required"}`（`task` 字段缺失或为空）

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

**错误响应**:
- `400` — `{"error": "project is required"}`（`project` 参数缺失或为空）
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

**错误响应**:
- `400` — 请求体字段类型无效（如 `title must be a string`）
- `404` — 给定 UUID 的观察不存在

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

## Observations

> 观察记录列表见 [Viewer 查看器](#viewer-查看器) 章节。

## Extraction 结构化提取

### 触发结构化提取

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

**错误响应** (`500 Internal Server Error`):
```json
{
  "error": "Failed to trigger extraction: Extraction failed and DLQ unavailable for template: user_preference"
}
```

> ⚠️ **注意**: 此端点为同步执行——响应在提取完成后才返回，耗时取决于模板数量和观测数据量。

---

### 获取最新提取结果

#### GET `/api/extraction/{templateName}/latest`

获取指定模板的最新提取结果。

**路径参数**:
- `templateName` - 提取模板名称，取自 `application.yml` 的 `app.memory.extraction.templates[].name`；
  目前**只随附一个模板** `user_preference`（下划线）。传入未配置的名称**不会被拒绝**：
  `/latest` 返回 200 且 `status` 为 `not_found`，`/history` 返回 200 加空列表

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `projectPath` | string | ✅ | 项目路径 |
| `userId` | string | ❌ | 用户 ID（用户级别提取时使用） |

**请求示例**:
```bash
curl "http://localhost:37777/api/extraction/user_preference/latest?projectPath=/Users/dev/myproject&userId=alice"
```

**响应示例**（有数据）:
```json
{
  "status": "ok",
  "template": "user_preference",
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
  "template": "user_preference",
  "sessionId": null,
  "extractedData": null,
  "createdAt": null,
  "observationId": null,
  "message": "No extraction found"
}
```

注意：四个数据字段仍然存在，只是取值为 `null`。处理器返回的是同一个
`GetLatestExtractionResponse` record，只是把那四个构造参数置为 null，因此它们会出现在
JSON 里而非被省略——按 record 结构读取的客户端无论哪种情况都能看到这些键。

---

### 获取历史

#### GET `/api/extraction/{templateName}/history`

获取指定模板的提取历史。

**路径参数**:
- `templateName` - 提取模板名称

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `projectPath` | string | ✅ | 项目路径 |
| `userId` | string | ❌ | 用户 ID |
| `limit` | int | ❌ | 返回数量（默认 10）|

**请求示例**:
```bash
curl "http://localhost:37777/api/extraction/user_preference/history?projectPath=/Users/dev/myproject&limit=5"
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

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `anchor` | string | ❌ | — | 锚点 ID（UUID 或会话 ID） |
| `depth_before` | int | ❌ | 10 | 锚点前的项目数 |
| `depth_after` | int | ❌ | 10 | 锚点后的项目数 |
| `project` | string | ❌ | — | 项目路径 |

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

#### POST `/api/context/semantic`

基于语义搜索返回与查询相关的观察结果，用于逐 prompt 注入。依赖 embedding 服务可用。

**请求体** (application/json):

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `q` | string | ✅ | — | 搜索查询文本（最少 20 个字符） |
| `project` | string | ❌ | cwd | 项目路径 |
| `limit` | int | ❌ | 5 | 最大结果数（1–20） |

**请求示例**:
```bash
curl -X POST "http://localhost:37777/api/context/semantic" \
  -H "Content-Type: application/json" \
  -d '{"q": "How did we handle JWT authentication in the login flow?", "project": "/Users/dev/myproject", "limit": 5}'
```

**响应示例**:
```json
{
  "context": "## Relevant Past Work (semantic match)\n\n1. **JWT token validation** (2026-04-10)\n   Fixed JWT token validation issue...",
  "count": 3
}
```

**说明**:
- `q` 少于 20 字符时返回 `{"context": "", "count": 0}`
- embedding 服务不可用时返回 `{"context": "", "count": 0}`
- 无匹配观察时返回空 context

---

## 搜索

### 搜索记忆

#### GET `/api/search`

语义搜索 + 文本搜索。

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `project` | string | ✅ | — | 项目路径 |
| `query` | string | ❌ | — | 搜索查询 |
| `type` | string | ❌ | — | 类型过滤 |
| `concept` | string | ❌ | — | 概念过滤 |
| `source` | string | ❌ | — | 来源过滤（如 `manual`、`auto`） |
| `limit` | int | ❌ | 20 | 结果数量，静默钳制到 1–100（见 `/api/observations`） |
| `offset` | int | ❌ | 0 | 偏移量 |
| `orderBy` | string | ❌ | — | 排序字段（支持 `created_at_epoch` 或 `createdAtEpoch`，按创建时间降序排列） |

> **这里没有 `userId` 过滤参数，该端点也不按用户隔离。** `userId` 是会话属性，
> 而观测上没有用户列，因此无论由谁记录，search 都会返回该项目下的全部观测。
> ICL 那一侧则认它——`POST /api/memory/experiences` 与 `POST /api/memory/icl-prompt`
> 对不同用户返回不同结果（实测：alice 名下一条观测，alice 查到 1 条、bob 查到 0 条）。
> 多用户部署若需要在此隔离，请为每个用户分配各自的项目路径。
> `GET /api/observations` 与 `POST /api/observations/batch` 同理。

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


## 管理

#### GET `/api/projects`

获取所有已知项目路径列表，支持平台来源分组（V18）。

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
  ],
  "sources": ["claude", "cursor"],
  "projectsBySource": {
    "claude": ["/Users/dev/myproject"],
    "cursor": ["/Users/dev/another-project"]
  }
}
```

---

#### GET `/api/stats`

获取数据库和处理统计信息。可通过 project 查询参数筛选指定项目的统计。

**查询参数**:
| 参数    | 类型   | 必填 | 说明                     |
|---------|--------|------|--------------------------|
| `project` | string | 否   | 项目路径，用于筛选统计信息 |

**请求示例**:
```bash
curl http://localhost:37777/api/stats
# 或指定项目：
curl http://localhost:37777/api/stats?project=/path/to/project
```

**响应示例**（全局统计）:
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

**项目级响应**（`?project=...`）:
```json
{
  "worker": {
    "isProcessing": false,
    "queueDepth": 0
  },
  "database": {
    "totalObservations": 42,
    "totalSummaries": 5,
    "totalSessions": 3,
    "totalProjects": 1,
    "projectPath": "/path/to/project"
  }
}
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
  "CLAUDE_MEM_PROVIDER": "claude",
  "CLAUDE_MEM_MODEL": "claude-sonnet-4-5",
  "CLAUDE_MEM_LOG_LEVEL": "INFO",
  "CLAUDE_MEM_CONTEXT_OBSERVATIONS": 50,
  "CLAUDE_MEM_CONTEXT_FULL_COUNT": 5,
  "CLAUDE_MEM_CONTEXT_FULL_FIELD": "narrative",
  "CLAUDE_MEM_CONTEXT_SESSION_COUNT": 10,
  "CLAUDE_MEM_CONTEXT_OBSERVATION_TYPES": ["bugfix","feature","refactor","discovery","decision","change"],
  "CLAUDE_MEM_CONTEXT_OBSERVATION_CONCEPTS": ["how-it-works","why-it-exists","what-changed","problem-solution","gotcha","pattern","trade-off"],
  "CLAUDE_MEM_CONTEXT_MAX_OBSERVATIONS": 50,
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

## Mode 模式

#### GET `/api/mode`

获取当前活动模式信息。

**请求示例**:
```bash
curl http://localhost:37777/api/mode
```

**响应示例**:
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

---

#### PUT `/api/mode`

设置活动模式。

**请求体** (snake_case):
```json
{
  "mode_id": "code--zh"
}
```

**响应示例**:
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

---

#### GET `/api/mode/types`

获取所有观察类型列表。

**响应示例**:
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

---

#### GET `/api/mode/concepts`

获取所有观察概念列表。

**响应示例**:
```json
[
  {
    "id": "how-it-works",
    "label": "How It Works",
    "description": "Understanding mechanisms"
  }
]
```

---

#### GET `/api/mode/types/{typeId}/validate`

验证观察类型是否有效。

**响应示例**:
```json
{
  "valid": true
}
```

---

#### GET `/api/mode/types/{typeId}/emoji`

获取观察类型的 emoji。

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

获取所有有效观察类型 ID 列表。

**响应示例**:
```json
["bugfix", "feature", "refactor", "change", "discovery", "decision"]
```

有效集合**随模式而定**，取自当前模式定义的 `observation_types` / `observation_concepts`
（见 `GET /api/modes`），因此应以这两个端点为准，而不是下面这份快照——它反映的是
`code` 模式。两个列表都会被原样接受：无法识别的取值不会被拒绝，只是匹配不到任何内容。若要在发送前
校验某个取值，可调用 `GET /api/mode/types/{typeId}/validate` 或
`GET /api/mode/types/{typeId}/emoji`，它们会返回 `{"valid": true}` / `{"valid": false}`——
实测：`types/bugfix/validate` 返回 `{"valid":true}`，
`types/architecture/validate` 返回 `{"valid":false}`，而
`GET /api/search?concept=architecture` 仍是 200 加零条结果。

---

#### GET `/api/mode/concepts/valid`

获取所有有效观察概念 ID 列表。

**响应示例**:
```json
["how-it-works", "why-it-exists", "what-changed", "problem-solution", "gotcha", "pattern", "trade-off"]
```

有效集合**随模式而定**，取自当前模式定义的 `observation_types` / `observation_concepts`
（见 `GET /api/modes`），因此应以这两个端点为准，而不是下面这份快照——它反映的是
`code` 模式。两个列表都会被原样接受：无法识别的取值不会被拒绝，只是匹配不到任何内容。

---

## Viewer 查看器

WebUI 使用的端点，用于查看和搜索记忆。

#### GET `/api/observations`

分页获取观察列表，始终按 `created_at` 降序排列（最新的在前）。

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `project` | string | null | 项目路径过滤 |
| `platformSource` | string | null | 平台来源过滤（如 `claude`、`cursor`） |
| `offset` | int | 0 | 偏移量 |
| `limit` | int | 20 | 每页数量，静默钳制到 1–100 |

`limit` 会被**静默钳制到 1–100**，越界值不是错误——不会返回 `400`。同样的钳制也适用于
`/api/summaries`、`/api/prompts`、`/api/search` 与 `/api/search/by-file`（五个端点都用
`Math.min(Math.max(1, limit), MAX_PAGE_SIZE)`，MCP 的 `search` 工具也镜像同一窗口）。
对运行中的后端实测：

| 请求 | `/api/observations`、`/api/summaries`、`/api/prompts` | `/api/search` |
|------|------------------------------------------------------|----------------|
| `?limit=0` | 1 条 | 1 条 |
| `?limit=-5` | 1 条 | — |
| `?limit=500` | 100 条 | 100 条 |
| `?limit=7` | 7 条 | 7 条 |

`hasMore` 是判断本页被截断的信号；响应中没有任何字段回显实际生效的 limit。
`offset` 同样以 0 为下界。

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
      "session_id": "content-session-uuid",
      "project": "/Users/dev/myproject",
      "type": "feature",
      "title": "Feature implementation",
      "subtitle": "JWT authentication",
      "narrative": "Implemented JWT authentication...",
      "facts": "[\"Uses RS256 algorithm\", \"Token expires in 3600s\"]",
      "concepts": "[\"authentication\", \"security\", \"jwt\"]",
      "files_read": "[\"src/auth/jwt.go\", \"pkg/middleware/auth.go\"]",
      "files_modified": "[\"src/auth/jwt.go\"]",
      "quality_score": 0.85,
      "feedback_type": "SUCCESS",
      "feedback_updated_at": "2026-04-01T10:15:00Z",
      "source": "claude-code",
      "platform_source": "claude",
      "extractedData": {"framework": "Echo", "auth_type": "Bearer"},
      "prompt_number": 42,
      "created_at": "2026-04-01T09:00:00Z",
      "created_at_epoch": 1743488400000,
      "last_accessed_at": "2026-04-07T14:30:00Z",
      "access_count": 5,
      "refined_at": "2026-04-03T08:00:00Z",
      "refined_from_ids": "obs-abc-123,obs-def-456",
      "user_comment": "Core auth module"
    }
  ],
  "hasMore": true
}
```

**响应字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | 观察记录 UUID |
| `content_session_id` | string | Claude Code 内容会话 ID（V13；**不是** `session_id`，实体上有覆盖） |
| `project` | string | 项目路径（`@JsonProperty` 覆盖；**不是** `project_path`） |
| `type` | string | 观察类型（如 `feature`、`bugfix`） |
| `title` | string | 观察标题 |
| `subtitle` | string | 观察副标题 |
| `narrative` | string | 观察正文内容（`@JsonProperty` 覆盖；**不是** `content`） |
| `facts` | string | 事实列表，**JSON 编码的字符串**，如 `"[\"a\", \"b\"]"` |
| `concepts` | string | 概念标签列表，**JSON 编码的字符串**，如 `"[\"auth\", \"jwt\"]"` |
| `files_read` | string | 本次观察中读取的文件列表，**JSON 编码的字符串**，如 `"[]"` |
| `files_modified` | string | 本次观察中修改的文件列表，**JSON 编码的字符串**，如 `"[]"` |
| `refined_from_ids` | string \| null | 本条由精炼产生时为源观察 UUID 的**逗号分隔**串（如 `"obs-abc-123,obs-def-456"`），否则为 `null`。与上面四个字段不同，这是 `TEXT` 列而**非** JSONB——后端用 `,` 拼接 ID 且从不 JSON 编码，因此该值不是 JSON 编码数组 |
| `content_hash` | string | 用于查重的内容哈希 |
| `discovery_tokens` | int | 计入本条观察的 token 数（V17） |
| `quality_score` | float | 精炼过程评定的质量分数（0.0–1.0） |
| `feedback_type` | string | 反馈类型：`SUCCESS`/`PARTIAL`/`FAILURE`/`UNKNOWN` |
| `feedback_updated_at` | string | 最后反馈更新的 ISO-8601 时间戳 |
| `user_comment` | string | 用户提供的评论/注释 |
| `access_count` | int | 该观察记录被检索的次数 |
| `last_accessed_at` | string | 最后访问时间的 ISO-8601 时间戳 |
| `refined_at` | string | 最后精炼时间的 ISO-8601 时间戳 |
| `relevance_count` | int | 该记录被判定为相关并展示的次数（V17） |
| `generated_by_model` | string \| null | 生成该观察的模型（V17） |
| `step_number` | int \| null | 会话内的步骤序号（如有记录） |
| `embedding_model_id` | string \| null | 已存 embedding 的模型 ID（如存在） |
| `source` | string | 来源归属（如 `claude-code`、`manual`） |
| `platform_source` | string | 平台来源，用于多平台跟踪（V18，如 `claude`、`cursor`） |
| `extractedData` | object | LLM 提取的结构化数据（`@JsonProperty` 覆盖；**不是** `extracted_data`） |
| `prompt_number` | int | 会话中的提示词编号 |
| `created_at` | string | ISO-8601 创建时间戳 |
| `created_at_epoch` | long | 创建时间的毫秒时间戳 |
| `embedding_768` / `embedding_1024` / `embedding_1536` | number[] \| null | pgvector 列，维度取决于所配置的 embedding 模型；未写入时为 `null` |

---

### Get Observations by IDs

```
POST /api/observations/batch
```

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

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ids` | string[] | ✅ | 要获取的观察 UUID 列表 |
| `project` | string | ❌ | 可选的项目过滤器 |
| `orderBy` | string | ❌ | 排序字段。仅识别 `created_at_epoch` 与 `createdAtEpoch`（见下方说明） |
| `limit` | int | ❌ | 最大返回结果数 |

**`orderBy` 是两个值的白名单，其他取值被静默忽略。** 处理器只在 `orderBy` 等于
`created_at_epoch` 或 `createdAtEpoch` 时排序；其他任何取值既不会被拒绝、不会记日志，
也不会在响应里回显，因此带 `orderBy: "quality_score"` 的请求会返回 `200`，看上去就像
排序已生效。这一点比「排序键被忽略」通常更严重，因为**`limit` 是在排序之后才应用的**：
控制器先取仓储的行，可选地重排，然后才截断到 `limit`。当 `orderBy` 不被识别时，行保持
`findAllById` 那种未定义的顺序，于是截断返回的是**另一批观察**，而不仅仅是同一批的不同排列。

对 `openclaw` 项目的 8 个 id 配 `limit: 4` 实测：

| `orderBy` | 返回的 id（前 8 位） |
|-----------|---------------------|
| `created_at_epoch` | `118af6be`、`42c0353d`、`7f6dacc7`、`9d275d10` |
| `bogus_column` | `7f6dacc7`、`2b893525`、`42c0353d`、`118af6be` |
| *(省略)* | `7f6dacc7`、`2b893525`、`42c0353d`、`118af6be` |

`2b893525` 在输入中排第六却被返回，而排序结果里排第一的 `9d275d10` 被丢弃。因此把
`orderBy` 读作「按这个字段排序」、并搭配一个小于 id 列表长度的 `limit` 的调用方，可能拿到
一批任意的观察，却收不到任何「所请求的排序从未执行」的信号。若更在意拿到哪几条而不是
它们的顺序，请传全部 id 并在客户端排序，或使用 `orderBy: "created_at_epoch"`。

**响应示例**:
```json
{
  "observations": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "content_session_id": "content-session-uuid",
      "project": "/Users/dev/myproject",
      "type": "feature",
      "title": "Feature implementation",
      "narrative": "Implemented JWT authentication...",
      "facts": "[\"Uses RS256 algorithm\"]",
      "concepts": "[\"authentication\"]",
      "quality_score": 0.85,
      "created_at_epoch": 1743488400000,
      "access_count": 3
    }
  ],
  "count": 1
}
```

---

### Search by File

```
GET /api/search/by-file
```

根据文件/文件夹路径搜索观察。

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `project` | string | (必填) | 项目路径 |
| `filePath` | string | (必填) | 文件/文件夹路径 |
| `isFolder` | boolean | false | 是否为文件夹 |
| `limit` | int | 20 | 结果数量，静默钳制到 1–100（见 `/api/observations`） |
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

#### GET `/api/summaries`

分页获取摘要列表，始终按 `created_at` 降序排列。

**查询参数**: 同 `/api/observations`

**响应格式**: 同 `/api/observations`（但返回摘要对象）

---

#### GET `/api/prompts`

分页获取用户提示列表，始终按 `created_at` 降序排列。

**查询参数**: 同 `/api/observations`

**响应格式**: 同 `/api/observations`（但返回用户提示对象）

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
  "name": "Code Development",
  "description": "Software development and engineering work",
  "version": "1.0.0",
  "observation_types": [...],
  "observation_concepts": [...]
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
  "name": "Code Development"
}
```

---

## Import 数据导入

#### POST `/api/import`

批量导入所有数据类型。

**请求体**（snake_case）:
```json
{
  "sessions": [
    {
      "content_session_id": "content-123",
      "project_path": "/path/to/project",
      "user_prompt": "Add feature",
      "started_at_epoch": 1707878400000,
      "completed_at_epoch": 1707882000000,
      "status": "completed"
    }
  ],
  "observations": [
    {
      "session_id": "content-123",
      "project_path": "/path/to/project",
      "title": "Feature implementation",
      "content": "...",
      "type": "feature",
      "facts_json": "[\"...\"]",
      "concepts_json": "[\"...\"]",
      "created_at_epoch": 1707878400000
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

**三个计数的含义。** 每条记录恰好落入其中一个桶，这个区分很重要——只检查 `success`
的调用方会把被丢弃的记录当成成功：

| 计数 | 含义 |
|------|------|
| `imported` | 该记录已写入。 |
| `skipped` | 该记录与已有行**重复**，因此有意不再写入。没有数据丢失。 |
| `errors` | 该记录被**拒绝**——缺少必填字段、外键无法解析，或写入失败。什么都没写入，`errorMessages` 说明原因。 |

`success` 说的是**请求完成**，不是每条记录都落地。`skipped > 0` 的响应是健康的；
`errors > 0` 意味着有数据丢失，必须读 `errorMessages`。

**校验失败与抛出的异常现在报告方式完全一致**——两者都让 `errors` 加一并都追加到
`errorMessages`。缺少必填字段时（例如会话与摘要导入中 schema 上为 `NOT NULL` 的
`session_id` 与 `project_path`），得到的是点名该字段的消息（如
`"projectPath is required"`），而不是数据库约束错误。

对运行中的后端实测：

```jsonc
// 一条无效记录
{"success":true,"imported":0,"skipped":0,"errors":1,
 "errorMessages":["projectPath is required"]}

// 同一会话导入两次——是重复，不是错误
{"success":true,"imported":0,"skipped":1,"errors":0,"errorMessages":[]}
```

**字段名注意**。后端全局 `jackson.property-naming-strategy` 是 `SNAKE_CASE`，
请求字段必须用 snake_case（`session_id`、`project_path`）。camelCase 字段不会绑定、
会被当作缺失——它产生的是点名缺失字段的校验错误，而不是被静默忽略。

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

## Logs 日志管理

#### GET `/api/logs`

获取应用日志。

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `lines` | int | 1000 | 返回的最大行数 |

`lines` 会被**静默钳制到 1–10000**，越界值不是错误——不会返回 `400`。
对运行中的后端实测：

| 请求 | `returnedLines` |
|------|-----------------|
| `?lines=0` | 1 |
| `?lines=-5` | 1 |
| `?lines=50000` | 10000 |
| `?lines=3` | 3 |

响应中的 `returnedLines` 是实际返回的行数，且**永远不超过**钳制后的 `lines`。
`totalLines` 统计的是被搜索文件的全部行数，可能不止一个文件：该端点优先读今天的
日志，只有当今天的行数不足时才回退到昨天；`files` 数组列出实际读取了哪些文件。

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

清空当日日志文件。如果当日日志文件不存在，返回不同的消息。

**请求示例**:
```bash
curl -X POST http://localhost:37777/api/logs/clear
```

**响应示例**（文件存在时）:
```json
{
  "status": "ok",
  "message": "Today's log file has been cleared",
  "path": "/Users/dev/.claude-mem/logs/claude-mem-2026-03-13.log"
}
```

**响应示例**（无文件可清空时）:
```json
{
  "status": "ok",
  "message": "No log file to clear",
  "path": "/Users/dev/.claude-mem/logs/claude-mem-2026-03-13.log"
}
```

**错误响应**:
- `500` — `{"error": "Failed to clear log file"}`（IO 错误）

---

## Health 健康检查

#### GET `/api/health`

基础健康检查端点，适合负载均衡器和 Kubernetes 探针。

**请求示例**:
```bash
curl http://localhost:37777/api/health
```

**响应示例** (`200 OK`, 数据库正常):
```json
{
  "status": "ok",
  "timestamp": 1707878400000,
  "service": "claude-mem-java"
}
```

**响应示例** (`200 OK`, 数据库不可用，降级模式):
```json
{
  "status": "degraded",
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

**响应示例** (`503 服务未就绪`):
```json
{
  "status": "not_ready",
  "checks": {
    "database": "not_ready",
    "queueDepth": 0,
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
  "java": "24.0.1",
  "springBoot": "3.3.13"
}
```

> **说明**: `java` 字段反映运行时 JVM 版本，随部署环境不同而变化。

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

**响应示例**（已注册）:
```json
{
  "registered": true,
  "projectName": "my-project",
  "workspacePath": "/path/to/project",
  "installedAt": 1709000000000
}
```

**响应示例**（未注册）:
```json
{
  "registered": false,
  "projectName": "my-project"
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

**初始加载事件**（V18 新增平台来源分组）：
```json
{
  "type": "initial_load",
  "projects": ["/path/to/project1", "/path/to/project2"],
  "sources": ["claude", "cursor"],
  "projectsBySource": {
    "claude": ["/path/to/project1"],
    "cursor": ["/path/to/project2"]
  },
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
| `CLAUDEMEM_LLM_PROVIDER` | LLM 提供商（`openai` 或 `anthropic`） | openai |

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
| 2026-10-02 | (unreleased) | 记录五个分页/搜索端点共用的 `limit` 钳制行为（`/api/observations`、`/api/summaries`、`/api/prompts`、`/api/search`、`/api/search/by-file`）。五者都应用 `Math.min(Math.max(1, limit), MAX_PAGE_SIZE)`，MCP 的 `search` 工具也镜像同一窗口，但参数表只写了「（最大 100）」——读起来像「会拒绝」而非「静默钳制」：调用方传 `limit=0` 期望「不限制」，实际只拿到 1 条且无报错。实测：`?limit=0` 与 `?limit=-5` 在三个列表端点均返回 1 条；`?limit=500` 返回 100 条列表与 100 条搜索结果；`?limit=7` 返回 7 条。`/api/search/by-file` 仅确认了下界（`?limit=0` -> 1），因为没有匹配同一路径的多条记录。与今日早些时候记录的 `/api/logs` 钳制属同一类问题；中英文同步 |
| 2026-10-02 | (unreleased) | GET `/api/logs`：补充此前完全未记录的 `lines` 钳制行为。`LogsController` 中为 `Math.min(Math.max(1, lines), 10000)`，因此越界值会被静默钳制、**从不返回 `400`**——实测：`?lines=0` 与 `?lines=-5` 均返回 `returnedLines: 1`，`?lines=50000` 返回 `10000`，`?lines=3` 返回 `3`。同时说明 `returnedLines` 永远不超过钳制后的 `lines`，以及 `totalLines` 统计的是被搜索文件的全部行数（可能不止一个文件：该端点优先读今天的日志，仅当今天行数不足时才回退到昨天，`files` 列出实际读取的文件）。本条初稿曾写「跨日时 `returnedLines` 可能超过 `lines`」，读控制器后发现不成立（`subList(size - validatedLines, size)` 已将其限制住），遂删除而非发布。 |
| 2026-10-02 | (unreleased) | **行为变更：四个单记录导入端点现在把校验失败计入 `errors`，不再计入 `skipped`。** `ImportResult` 有三个工厂（`imported`、`duplicate`、`error`），而 `ImportController` 只按 `imported()` 分支，于是 `error()` 落进了 skip 计数，而 `errors`/`errorMessages` **只接收抛出的异常**。同一类失败因此仅因「抛出」还是「返回」而被报告成两种完全不同的样子。修复前实测：字段未绑定的载荷返回 `{"success":true,"imported":0,"skipped":1,"errors":0,"errorMessages":[]}`，而那条记录已被静默丢弃。现新增 `ImportResult.isError()` 以 `id() == null` 区分（只有 `error()` 不设 id），四处调用点全部改为按它分支。修复后实测：`{"success":true,"imported":0,"skipped":0,"errors":1,"errorMessages":["projectPath is required"]}`。真正的重复**行为不变**，仍计为 skipped。另补：`importSession` 与 `importSummary` 现在校验 `projectPath`（两张表上均为 NOT NULL），缺字段时返回点名该字段的消息，而不是 `Could not commit JPA transaction` 或原始的 PostgreSQL 约束错误。三个计数的语义已补进「Import Observations」小节——正是这个空白让该缺陷长期存活。对 WebUI 零影响：`webui` 的 `POST /api/import` 是写入自有 SQLite store 的独立 worker 路由，从不调用这些端点。 |
| 2026-10-02 | (unreleased) | **实跑核验后修正观察记录响应的字段表。** (1) `facts`、`concepts`、`files_read`、`files_modified` 原标注为 `string[]`，但后端把这些 JSONB 列序列化为 **JSON 编码的字符串**（`"concepts": "[\"auth\"]"`），已用 POST/GET 往返验证。`refined_from_ids` 当时也被归入这一组，但它**不是** JSONB 列——它是存放逗号分隔 UUID 的 `TEXT` 列，见下一条更正。(2) 响应字段原写作 `session_id`，实际 wire 键为 `content_session_id`（V13 `@JsonProperty` 覆盖）——请求侧的 `session_id` 别名仍然有效，未改动；(3) 补齐 10 个线上实际返回但表中缺失的字段：`content_hash`、`discovery_tokens`、`relevance_count`、`generated_by_model`、`step_number`、`embedding_model_id` 及三个 `embedding_*` 向量列。另修正两处响应示例。`POST /api/ingest/observation` 的**请求**侧确实接受真实数组，保持原样未动。该错误类型正是同轮修复的 Python SDK 缺陷的文档根因——它只解析真实数组，因而这些字段一律被读成 `[]` |
| 2026-10-02 | (unreleased) | **更正 `refined_from_ids`——上一条把它与四个 JSONB 列归为一类。** V11 中声明为 `refined_from_ids TEXT`（`COMMENT ON COLUMN … IS 'Comma-separated IDs of merged observations'`），后端唯一的写入方是 `ExtractionStorageService`，用的是 `Collectors.joining(",")` 且从不 JSON 编码；活体抽取记录也证实了这一点：该记录的 wire 值用 `json.loads()` 解析会在第一个 UUID 处抛 `JSONDecodeError`，而同一条记录的 `concepts` 则能正常解码。类型 `string \| null` 本来就对，错的是描述里的「JSON 编码数组」，且与上方三行的示例自相矛盾。这也一并更正了同一天刚写进 `ARCHITECTURE.md`/`ARCHITECTURE-zh-CN.md` 的说法。该错误描述是有代价的：Go SDK 的 `StringList` 假定所有列表列要么是 JSON、要么是 JSON 编码数组，因此只要有一条记录带 `refined_from_ids`，整页观察记录就无法反序列化（同轮已修） |
| 2026-03-31 | 0.1.0-beta | 新增 Extraction (/run, /latest, /history)、Cursor、Mode、Logs、Import、Viewer 章节；修复 Session API 路径；同步英文版完整结构 |
| 2026-03-31 | 0.1.0-beta+ | 补充 Viewer、Management、Mode、Health、Cursor、Logs 参数表和响应示例；同步英文版完整度 |
| 2026-03-31 | 0.1.0-beta++ | 修正 Delete Observation 响应（200 OK with body，非 204 No Content）；同步英文版 Session Start 响应示例 |
| 2026-03-31 | 0.1.0-beta+++ | 修正 Memory Refine（查询参数，非 JSON 请求体）；修正 Feedback 请求字段（observationId/feedbackType，非 session_id/feedback_type）；补充 Experiences 和 ICL Prompt 的 userId 字段；同步英文版 |
| 2026-03-31 | 0.1.0-beta++++ | 新增 Test 测试端点章节（/api/test/llm、/embedding、/all）；补充概述章节（Base URL + Content-Type）；同步 TOC；同步更新日志 |
| 2026-03-31 | 0.1.0-beta+++++ | 补充 Get Session 响应示例/路径参数/错误响应；补充 Update Session User 路径参数/请求体表/响应示例（3 字段）；修正环境变量名（SPRING_DATASOURCE_*、SPRING_AI_OPENAI_*），默认值匹配实际配置；新增 Anthropic 环境变量；同步英文版 |
| 2026-04-01 | 0.1.0-beta+++++ | 补充英文版 Search 章节完整参数类型表、请求示例和响应示例（strategy/fell_back/count）；同步中英文版完整度 |
| 2026-04-01 | 0.1.0-beta++++++ | 修正搜索策略值——实际代码返回 hybrid/tsvector/filter/recent/none（非 vector/text）；更新响应示例和策略说明；同步英文版 |
| 2026-04-01 | 0.1.0-beta+7 | 补充英文版 Ingest 章节（参数表、响应示例、错误响应——中文版已完整但英文版严重缺失）；补充中英文 Quality Distribution 参数表、响应示例及 `unknown` 字段；修正 Batch Get Observations `orderBy` 示例（`created_at` → `created_at_epoch`）；补充英文版 Create Observation 参数表 |
| 2026-04-01 | 0.1.0-beta+8 | 修正 Get Settings 响应格式——实际代码返回 20 个 `CLAUDE_MEM_*` 字段 + modeName/modeDescription（非简单 mode/modeName/modeDescription）；记录所有 CLAUDE_MEM_* 字段及类型和默认值；修正 Update Settings 接受 `CLAUDE_MEM_*` 字段名及 `mode` 简写；同步英文版；修正 PATCH observations 响应 status 值 `ok` → `updated` |
| 2026-04-01 | 0.1.0-beta+9 | 补充英文版 PATCH /api/memory/observations 章节（路径参数、响应示例、错误响应——中文版已完整）；补充英文版 DELETE /api/memory/observations 路径参数 |
| 2026-04-02 | 0.1.0-beta+10 | 补充英文版 Streaming 章节完整事件类型表、事件格式示例、JavaScript 示例和超时说明（原严重缺失）；修正 Get Version 响应示例 Java 版本值（动态字段，添加说明）；补充 `CLAUDEMEM_LLM_PROVIDER` 到环境变量表；同步中文版 |
| 2026-04-02 | 0.1.0-beta+11 | 修正 Cursor Check Registration 响应——`projectPath` → `workspacePath`（匹配实际 wire format），补充缺失的 `installedAt` 字段和未注册响应示例；同步英文版；补充中文 Session Start 请求体示例缺失的 `user_id` 字段 |
| 2026-04-02 | 0.1.0-beta+12 | 拆分 Viewer 超级章节——新增独立 `## 搜索` 和 `## 管理` 章节（与英文版结构一致）；补充 Create Observation 请求体参数表（中文版缺失）；同步 TOC |
| 2026-04-02 | 0.1.0-beta+13 | 修正英文版 PATCH/DELETE /api/memory/observations 路径变量：`{observationId}` → `{id}`（匹配 Controller @PatchMapping/@DeleteMapping）；补充英文版 Bulk Import 响应示例及 stats 格式；同步中文版 |
| 2026-04-02 | 0.1.0-beta+14 | 补充 ICL Prompt 400 错误响应（task 必填）；补充 Quality Distribution 400 错误响应（project 必填）；同步英文版 |
| 2026-04-03 | 0.1.0-beta+15 | 补充 Experiences 端点响应示例（JSON 数组，包含 id/task/strategy/outcome/reuse_condition/quality_score/created_at 字段）和 400 错误响应（task 必填）；同步英文版 |
| 2026-04-03 | 0.1.0-beta+16 | 补充 Session Start 完整错误响应（400 session_id 缺失、400 project_path/cwd 缺失、500 内部错误——与代码一致）；同步英文版；添加中文版底部跨链接 |
| 2026-04-03 | 0.1.0-beta+17 | 修正 Bulk Import 请求体示例——字段名从 camelCase 改为 snake_case（匹配 SNAKE_CASE 命名策略），修正 `narrative`→`content`、`facts`→`facts_json`、`concepts`→`concepts_json`（匹配 ImportService 记录字段），移除不存在的 `id` 字段；补充 PATCH observations 错误响应（400/404——与英文版一致） |
| 2026-04-03 | 0.1.0-beta+18 | 补充 Clear Logs 备选响应（文件不存在时返回 "No log file to clear"）和 500 错误响应；与英文版同步 |
| 2026-04-03 | 0.1.0-beta+19 | Start Session 响应示例补充 sessionId 字段（后端 commit 8f0ed96 新增）；与英文版同步 |
| 2026-04-03 | 0.1.0-beta+20 | Start Session 响应示例字段名修正 `sessionId`→`session_id`（应用使用 SNAKE_CASE 命名策略，实际 wire format 为 `session_id` 而非 `sessionId`）；与英文版同步 |
| 2026-04-03 | 0.1.0-beta+21 | Start Session 响应示例字段顺序修正——`session_id` 应为第一字段（与 DTO record 定义一致），随后是 `context`、`updateFiles`、`session_db_id`、`prompt_number`；中英文两版同步更新；补充 changelog 条目 |
| 2026-04-03 | 0.1.0-beta+22 | Mode: 修正 PUT `/api/modes` HTTP 方法（POST→PUT，与 `@PutMapping` 一致）；修正请求体字段 `mode`→`modeId`（与 ModeSwitchRequest 内部 record 字段一致）；修正 GET `/api/modes` 响应 `id`→`mode_id`；修正 PUT `/api/modes` 响应格式为 ModeResponse DTO（`mode_id`/name/description/version/observation_types/observation_concepts）；与英文版同步 |
| 2026-04-04 | 0.1.0-beta+23 | Search 端点 `orderBy` 字段描述更新为"支持：`created_at_epoch`，用于 MCP 兼容性"（与英文版同步，后端代码已实现）；Batch Get Observations 章节补充参数表（ids/project/orderBy/limit 四个字段），与英文版一致 |
| 2026-04-04 | 0.1.0-beta+24 | 分页 Bug 修复：GET `/api/observations`、`/api/summaries`、`/api/prompts`——此前当 `offset < limit` 时，分页返回错误结果（页索引 = offset/limit，当 offset < limit 时始终为 0，导致 offset 被忽略）。现已修复为真正的 offset 分页（SQL `LIMIT n OFFSET m`）。三个端点现在均按 `createdAt DESC` 排序。 |
| 2026-04-06 | 0.1.0-beta+25 | Search 端点 `orderBy` 参数描述更新为"支持 `created_at_epoch` 或 `createdAtEpoch`"（与 ViewerController 代码一致，两值均接受）；同步英文版 |
| 2026-04-07 | 0.1.0-beta+26 | 补充 ZH 缺失的 Mode 端点：新增 `## Mode 模式` 章节，包含 8 个 ModeController 端点（GET/PUT /api/mode、GET /api/mode/types、GET /api/mode/concepts、GET /api/mode/types/{typeId}/validate、GET /api/mode/types/{typeId}/emoji、GET /api/mode/types/valid、GET /api/mode/concepts/valid）；修正 TOC 章节顺序（Mode 模式移至 管理 之后、搜索 之前）与 body 结构一致；同步英文版 |
| 2026-04-07 | 0.1.0-beta+27 | Viewer Mode 端点修复：GET /api/modes 响应 `mode_id`→`id`（匹配 ViewerController `response.put("id", ...)`）；修复 POST /api/modes HTTP 方法 PUT→POST（匹配 ViewerController `@PostMapping`）；修复 POST /api/modes 请求体 `modeId`→`mode`（匹配 ModeSwitchRequest wire format `@JsonProperty("mode")`）；修复 POST /api/modes 响应为 `{"success": true, "mode": "...", "name": "..."}`（匹配 ViewerController 实际响应）；与英文版同步 |
| 2026-04-08 | 0.1.0-beta+28 | ModeController PUT /api/mode：请求体还原 `mode`→`mode_id`（实测确认 ModeController 内部 ModeSwitchRequest 字段为 `modeId`（无 @JsonProperty），wire 格式为 snake_case `mode_id` 而非 `mode`）；与英文版同步 |
| 2026-04-08 | 0.1.0-beta+29 | List Observations：丰富响应示例，展示 ObservationResponse DTO 的全部 24 个字段（session_id/subtitle/facts/concepts/files_read/files_modified/quality_score/feedback_type/feedback_updated_at/source/extractedData/prompt_number/created_at/last_accessed_at/access_count/refined_at/refined_from_ids/user_comment）；新增 24 字段响应字段说明表；丰富 Batch Get Observations 响应示例；与英文版同步 |
| 2026-04-08 | 0.1.0-beta+30 | Readiness Check：补充缺失的 503 响应体示例 `{"status":"not_ready","checks":{"database":"not_ready","queueDepth":0,"queueStatus":"ready"},"timestamp":...}`（HealthController 实际返回 "not_ready" 而非 "degraded"）；与英文版同步 |
| 2026-04-09 | 0.1.0-beta+31 | 补充 Ingestion 端点缺失的 400 错误响应：POST /api/ingest/session-end（缺少 session_id）和 POST /api/ingest/observation（缺少 content_session_id 或 project_path）；与 IngestionController.java 源码验证一致；与英文版同步 |
| 2026-04-09 | 0.1.0-beta+32 | 补充 GET /api/health 缺失的降级模式响应示例（`status: "degraded"`，数据库不可用时返回，200 OK）；已与 HealthController.java 源码第 62 行验证（`response.put("status", dbReady ? "ok" : "degraded")`）；与英文版结构一致 |
| 2026-04-10 | 0.1.0-beta+33 | 删除英文版错误放置的 `## Recent Work` 顶级章节（原 812-823 行）——包含不应出现在 API 参考文档中的非 API 内容（bug fix 示例、Token Savings Summary）。将三个 Context API 端点文档（`/api/context/recent`、`/api/context/timeline`、`/api/context/prior-messages`）从原 `## Recent Work` 下的 `###` 子节移至 `## Context` 章节下的 `#### GET` 正式子节；更新 curl 示例为 `bash` 代码块格式并补充参数类型列；英文版结构现已与中文版一致 |
| 2026-04-12 | 0.1.0-beta+34 | GET /api/stats：新增可选 `project` 查询参数，支持项目级统计过滤（commit a75ad4c — ViewerController.getStats 新增 `@RequestParam(required=false) String project`，通过 SessionRepository.countByProjectPath 返回过滤后计数）；补充查询参数表、带 `?project=...` 的 curl 示例、项目级响应示例（含额外 `projectPath` 字段）；同步英文版变更 |
| 2026-04-12 | 0.1.0-beta+35 | GET /api/observations、/api/summaries、/api/prompts：补充缺失的排序说明——三个端点均始终按 `created_at` 降序排列（commit cafbae1 使用 OffsetPageRequest + Sort.by(DESC, "createdAt")）；此前文档未说明排序规则；同步英文版变更 |
| 2026-04-23 | 0.1.0-beta+36 | 补充缺失的 `POST /api/context/semantic` 端点（V17）——基于查询的语义上下文搜索，用于逐 prompt 注入；添加请求体字段说明（`q`/必填最少 20 字符、`project`、`limit`）、参数表、curl 示例、响应示例及空查询和 embedding 服务不可用的行为说明；同步英文版变更 |
| 2026-05-03 | 0.1.0-beta+37 | GET `/api/observations`、`/api/summaries`、`/api/prompts` 新增 `platformSource` 查询参数（V18）——平台来源过滤（如 `claude`、`cursor`）；更新中文版 URL 示例和参数表；与英文版同步变更 |
| 2026-05-05 | 0.1.0-beta+38 | 结构重组：Settings 端点（GET+POST /api/settings）从 ## 搜索 移至 ## 管理；Timeline 端点（GET /api/timeline）、SDK Sessions 端点（POST /api/sdk-sessions/batch）、Modes 端点（GET+POST /api/modes）从 ## 搜索 移至 ## Viewer 查看器；## 搜索 现仅含搜索和批量获取端点；与英文版结构对齐 |
| 2026-05-05 | 0.1.0-beta+39 | GET `/api/projects`：更新响应示例，新增 `sources` 和 `projectsBySource` 字段（V18）——对应 ViewerController.getProjects() 返回平台来源列表和分组；SSE `/stream` initial_load 事件：更新示例，新增 `sources` 和 `projectsBySource`（V18）；与英文版同步 |
| 2026-05-05 | 0.1.0-beta+40 | 结构修复：## 搜索 章节从 ## Mode 之后移至 ## Extraction 之后（中文文档此前位置有误）；## 管理（Projects/Stats/Settings）和 ## Mode（ModeController 端点）保持不变；英文版结构未受影响 |
| 2026-05-06 | 0.1.0-beta+41 | ZH API 文档：修复 Extraction 章节孤立的小节标题（### 触发结构化提取/获取最新提取结果/获取历史 为空标题，无实际内容）；重组 Extraction 内容，正确嵌套于各小节之下；ZH 结构现已与英文版一致（Extraction 含3个小节 → 搜索 → Viewer） |
| 2026-05-06 | 0.1.0-beta+42 | GET /api/settings：修正 6 个示例值以匹配 AppSettings.toMap() 实际默认值（PROVIDER openai→claude、MODEL gpt-4o→claude-sonnet-4-5、MAX_OBSERVATIONS 100→50、FULL_FIELD full_content→narrative、OBSERVATION_TYPES/CONCEPTS 从 [] 修正为实际列表）；移除 3 个错误文档化的字段（CLAUDE_MEM_WORKER_PORT/CLAUDE_MEM_WORKER_HOST/CLAUDE_MEM_SKIP_TOOLS）—— 这些字段存在于 AppSettings 但未在 toMap() 中序列化；与英文版同步 |
| 2026-05-06 | 0.1.0-beta+43 | POST /api/extraction/run：补充遗漏的 500 错误响应（`{"error": "Failed to trigger extraction: Extraction failed and DLQ unavailable for template: ..."}`）——后端修复（316c165 F-2）使 DLQ 存储失败从静默事务回滚变为 HTTP 500 错误返回；与英文版同步 |
| 2026-05-07 | 0.1.0-beta+44 | 修复 ZH API 文档严重结构错误：`#### GET /api/search/by-file` 和 `#### POST /api/observations/batch` 错误放置于 `## 搜索` 章节（应为 `## Viewer`）；已将两节移至 Viewer 章节并添加 `### Get Observations by IDs` 和 `### Search by File` 小节标题，与英文版结构对齐 |
| 2026-05-07 | 0.1.0-beta+45 | ZH API 文档：修复 beta+44 不完整修复——`### Get Observations by IDs` 和 `### Search by File` 虽已添加为小节标题，但其子节点 `#### POST /api/observations/batch` 和 `#### GET /api/search/by-file` 仍处于 `####` 级别而非 `###` 级别，形成空标题嵌套；移除两个空 `###` 父标题并将子节点升级为 `###` 级别（URL 以代码块格式展示），与英文版结构完全对齐 |
| 2026-10-02 | 0.1.0-beta+46 | ZH API 文档：3 张查询参数表把数值默认值写进了「必填」列，导致 `limit`/`offset`/`depth_before`/`depth_after` 实际显示为「必填 = 20 / 0 / 10」。修正：Extraction history 的 `limit` 改为 ❌ 并在说明中标注「（默认 10）」（与英文版排版一致）；Search 与 Context Timeline 两张表新增「默认值」列，与英文版逐格对应；与英文版同步 |
---

**文档维护**: 本文档应随 API 变更同步更新。如有疑问，请参考源代码 Controller 类或提交 Issue。

---

*See also: [English Version](API.md)*
