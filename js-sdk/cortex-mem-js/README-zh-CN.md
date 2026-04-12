> English version: [README.md](./README.md)

# @cortex-mem/js-sdk

[Cortex CE](https://github.com/abforce/cortex-ce) 记忆系统的 JavaScript/TypeScript 客户端 SDK。

## 特性

- **零依赖** —— 使用内置 `fetch` API（Node 18+、浏览器、Deno、Bun）
- **完整 TypeScript 支持** —— 所有 DTO 的完整类型定义
- **25 个 API 方法** —— 覆盖 Go/Java SDK 的所有端点
- **212 个单元测试** —— 全面覆盖 Wire 格式和客户端行为
- **双格式 CJS + ESM** —— 同时支持 CommonJS 和 ES Modules
- **Fire-and-forget 捕获** —— 非阻塞的观察记录，内置重试机制

## 安装

```bash
npm install @cortex-mem/js-sdk
```

## 快速开始

```typescript
import { CortexMemClient } from '@cortex-mem/js-sdk';

const client = new CortexMemClient({
  baseURL: 'http://localhost:37777',
  timeout: 10_000,
});

// 启动会话 — 请保留请求中的 session_id，以便后续调用使用
const SESSION_ID = 'my-session';
const session = await client.startSession({
  session_id: SESSION_ID,
  project_path: '/path/to/project',
});
// session.response 仅包含 session_db_id（数据库 UUID），不包含您传入的 session_id

// 记录观察（fire-and-forget）
await client.recordObservation({
  session_id: SESSION_ID, // 复用您已有的 session_id
  cwd: '/path/to/project',
  tool_name: 'Read',
  tool_input: { file: 'main.go' },
});

// 检索经验
const experiences = await client.retrieveExperiences({
  task: 'How to parse JSON?',
  project: '/path/to/project',
  count: 3,
});

// 构建 ICL prompt
const icl = await client.buildICLPrompt({
  task: 'How to parse JSON?',
  project: '/path/to/project',
});

// 搜索
const results = await client.search({
  project: '/path/to/project',
  query: 'JSON parsing',
  limit: 5,
});

// 结束会话
await client.recordSessionEnd({
  session_id: SESSION_ID,
  cwd: '/path/to/project',
});

client.close();
```

## API 参考

### 客户端选项

| 选项 | 默认值 | 说明 |
|------|--------|------|
| `baseURL` | `http://127.0.0.1:37777` | 后端 URL |
| `apiKey` | — | Bearer Token 认证 |
| `timeout` | `30000` | 请求超时（毫秒） |
| `maxRetries` | `3` | Fire-and-forget 操作最大重试次数 |
| `retryBackoff` | `500` | 基础重试退避（毫秒） |
| `logger` | 空操作 | 自定义日志器 |
| `fetch` | 全局 `fetch` | 自定义 fetch 实现 |
| `headers` | `{}` | 额外请求头 |

### 方法

#### 会话

| 方法 | HTTP | 说明 |
|------|------|------|
| `startSession(req)` | `POST /api/session/start` | 启动或恢复会话 |
| `updateSessionUserId(sessionId, userId)` | `PATCH /api/session/{id}/user` | 更新会话用户 |

#### 捕获（fire-and-forget）

| 方法 | HTTP | 说明 |
|------|------|------|
| `recordObservation(req)` | `POST /api/ingest/tool-use` | 记录工具使用观察 |
| `recordSessionEnd(req)` | `POST /api/ingest/session-end` | 信号会话结束 |
| `recordUserPrompt(req)` | `POST /api/ingest/user-prompt` | 记录用户提示 |

#### 检索

| 方法 | HTTP | 说明 |
|------|------|------|
| `retrieveExperiences(req)` | `POST /api/memory/experiences` | 检索相关经验 |
| `buildICLPrompt(req)` | `POST /api/memory/icl-prompt` | 构建 ICL prompt |
| `search(req)` | `GET /api/search` | 语义搜索 |
| `listObservations(req)` | `GET /api/observations` | 分页列出观察 |
| `getObservation(id)` | `POST /api/observations/batch` | 通过 ID 获取单个观察（未找到返回 `null`） |
| `getObservationsByIds(ids)` | `POST /api/observations/batch` | 批量获取 |

#### 管理

| 方法 | HTTP | 说明 |
|------|------|------|
| `triggerRefinement(projectPath)` | `POST /api/memory/refine` | 触发记忆精炼 |
| `submitFeedback(req)` | `POST /api/memory/feedback` | 提交观察反馈 |
| `updateObservation(id, update)` | `PATCH /api/memory/observations/{id}` | 更新观察 |
| `deleteObservation(id)` | `DELETE /api/memory/observations/{id}` | 删除观察 |
| `getQualityDistribution(project)` | `GET /api/memory/quality-distribution` | 获取质量分布 |

#### 提取

| 方法 | HTTP | 说明 |
|------|------|------|
| `triggerExtraction(project)` | `POST /api/extraction/run` | 触发提取 |
| `getLatestExtraction(projectPath, templateName, userId?)` | `GET /api/extraction/{templateName}/latest` | 最新提取结果 |
| `getExtractionHistory(projectPath, templateName, userId?, limit?)` | `GET /api/extraction/{templateName}/history` | 提取历史 |

#### 系统

| 方法 | HTTP | 说明 |
|------|------|------|
| `healthCheck()` | `GET /api/health` | 健康检查 |
| `getVersion()` | `GET /api/version` | 后端版本 |
| `getProjects()` | `GET /api/projects` | 列出项目 |
| `getStats(project?)` | `GET /api/stats` | 统计信息 |
| `getModes()` | `GET /api/modes` | 模式设置 |
| `getSettings()` | `GET /api/settings` | 当前设置 |
| `close()` | — | 关闭客户端 |

### 错误处理

```typescript
import { CortexMemClient, APIError, isNotFound, isRateLimited } from '@cortex-mem/js-sdk';

try {
  await client.startSession({ session_id: '', project_path: '/tmp' });
} catch (err) {
  if (err instanceof APIError) {
    console.error(`HTTP ${err.statusCode}: ${err.message}`);
  }
  if (isNotFound(err)) { /* 404 */ }
  if (isRateLimited(err)) { /* 429 — 延迟后重试 */ }
}
```

## Wire 格式

SDK 使用与后端 API 完全一致的 JSON 字段名。字段命名因端点而异：

**Session：**
- `session_id`、`project_path` (snake_case) — `SessionStartRequest`
- `user_id` (snake_case) — `SessionStartRequest` 可选字段

**Observation（采集）：**
- `session_id`、`cwd`、`tool_name` (snake_case) — `ObservationRequest`
- `extractedData` (camelCase) — 后端 `@JsonProperty` 覆盖

**Experience 和 ICL：**
- `requiredConcepts`、`userId` (camelCase) — `ExperienceRequest`、`ICLPromptRequest`

**Feedback：**
- `observationId`、`feedbackType` (camelCase) — `FeedbackRequest`

详见 [JS SDK 设计文档](../../docs/drafts/js-sdk-design.md)。

## 开发

```bash
# 安装依赖
npm install

# 构建
npm run build

# 运行测试
npm test

# 类型检查
npm run lint
```

## 许可证

MIT
