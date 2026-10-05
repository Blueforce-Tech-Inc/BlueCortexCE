> English version: [README.md](./README.md)

# @cortex-mem/js-sdk

[Cortex CE](https://github.com/Blueforce-Tech-Inc/BlueCortexCE) 记忆系统的 JavaScript/TypeScript 客户端 SDK。

## 特性

- **零依赖** —— 使用内置 `fetch` API（Node 18+、浏览器、Deno、Bun）
- **完整 TypeScript 支持** —— 所有 DTO 的完整类型定义
- **25 个 API 方法** —— 覆盖 Go/Java SDK 的所有端点
- **259 个单元测试** —— 全面覆盖 Wire 格式和客户端行为（246 客户端 + 5 截断响应体 + 8 http-server 示例）
- **双格式 CJS + ESM** —— 同时支持 CommonJS 和 ES Modules
- **尽力而为的捕获** —— 对暂时性故障重试；重试耗尽后记录日志并吞掉采集错误

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
// 三个响应字段是返回对象上的**顶层**字段——不存在嵌套的 `response` 属性：
//   session.session_db_id、session.context、session.prompt_number
// （wire 中的 session_id 与 updateFiles 也会到达，但不在 SessionStartResponse 类型内）。

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
| `baseURL` | `http://127.0.0.1:37777` | 后端 URL。**路径前缀会被保留**：`'http://host/memory'` 会把请求发到 `http://host/memory/api/…`，与 Go、Python、Java 三家一致——**反向代理或基于路径的网关后面就用这个**。尾部斜杠会被去掉。 |
| `apiKey` | — | Bearer Token 认证 |
| `timeout` | `30000` | 请求超时（毫秒） |
| `maxRetries` | `3` | Fire-and-forget 操作的总**尝试**次数（3 = 发 3 次请求，即首次之后的 2 次重试） |
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
| `toString()` | — | 生成用于日志的调试表示 |

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

### 响应体大小上限

超过 **10 MiB**（`10 * 1024 * 1024`）的响应会被拒绝：

```
cortex-ce: response body exceeds 10MB limit
```

（错误文案写的是 `10MB`；实际上限是 10 MiB，即 10,485,760 字节。）

响应体有两道检查，都会抛出普通 `Error`（而不是 `APIError`，因为这是本地失败而非
HTTP 状态）：

1. **读取前** —— 若 `Content-Length` 已声明超过上限，直接抛出，响应体完全不会被缓冲。
2. **读取后** —— 兜底检查，应对未返回该头的服务端。

第二道检查统计的是响应体的 **UTF-8 字节长度**，而非字符串的字符数，因此多字节响应体
不会因为码元计数而溜过去。（此前比较的是 `String.length`，它统计 UTF-16 码元——一个
15 MB 的响应体只有 520 万码元，会被直接放行。）字节数靠遍历字符串得出而非使用
`TextEncoder`，因为后者会额外分配一个与响应体等大的缓冲区。

这是防护而非流式读取：响应体仍会作为一个完整字符串物化。

### 空更新会被拒绝

`updateObservation` 在更新未设置任何字段时抛 `ValidationError`，且不会发出任何请求。Go、Java、Python 三家规则与消息完全相同：

```
cortex-ce: validation error on update: at least one field must be provided for update
```

这一点很重要：不设置任何字段的 PATCH 在 wire 上是一次静默 no-op。若没有这道检查，调用方用用户输入拼出一个空更新后会看到调用 resolve，却无法得知其实什么都没写入。

### 每个字段有三种状态：跳过、设置、清空

`ObservationUpdate` 的每个字段都有三种彼此不同的状态，且三种都写在类型里——省略该键、传值、传 `null`：

```ts
// 跳过：subtitle 保持原样（该键根本不会被发送）
await client.updateObservation(id, { title: 'New title' });

// 设置
await client.updateObservation(id, { concepts: ['auth'] });

// 清空：落库为 SQL NULL
await client.updateObservation(id, { source: null, extractedData: null });
```

`null` 是一个值，而不是「字段缺失」，所以每个字段的类型都是 `T | null` 而不只是可选。
在本包自身的 `"strict": true` 下，更窄的 `T | undefined` 写法会让清空语义**在 TypeScript 里无法触达**
——调用方必须写 `null as unknown as string` 才能走到客户端早已实现、后端也早已接受的行为。
实测后端对七个可清空字段全部按 SQL NULL 落库。

四家 SDK 中**只有本家能把字符串字段清空为 NULL**：Java 用 `@JsonInclude(NON_NULL)` 省略 null，
Go 用 `omitempty`，Python 直接跳过 `None`。对 `facts` 与 `concepts` 两个列表字段，`[]` 与 `null`
的结果不同——`[]` 存空数组，`null` 存 NULL——且两者都会上 wire，因为 `JSON.stringify` 没有 `omitempty`。

### 必填参数在客户端校验

下表中的参数都必须非空。SDK 抛出 `ValidationError` 且不发出任何请求。Go、Java、Python 三家 SDK 强制的是完全相同的一组规则。

| 方法 | 必填参数 |
|------|----------|
| `startSession` | `req.session_id`、`req.project_path` |
| `updateSessionUserId` | `sessionId`、`userId` |
| `recordObservation` | `req.session_id`、`req.cwd`、`req.tool_name` |
| `recordSessionEnd` | `req.session_id`、`req.cwd` |
| `recordUserPrompt` | `req.session_id`、`req.prompt_text`、`req.cwd` |
| `retrieveExperiences` | `req.task` |
| `buildICLPrompt` | `req.task` |
| `search` | `req.project` |
| `getObservation` | `id` |
| `getObservationsByIds` | `ids`——非空、至多 100 个、元素不得为空 |
| `triggerRefinement` | `projectPath` |
| `submitFeedback` | `req.observationId`、`req.feedbackType` |
| `updateObservation` | `observationId`，外加至少一个待修改字段 |
| `deleteObservation` | `observationId` |
| `getQualityDistribution` | `projectPath` |
| `triggerExtraction` | `projectPath` |
| `getLatestExtraction` | `projectPath`、`templateName` |
| `getExtractionHistory` | `projectPath`、`templateName`；`limit` 不得为负 |

项目参数在三个 capture 请求对象上叫 `cwd`（因为那才是 wire 字段名），在管理与抽取类
方法上则是位置参数 `projectPath`。另外三家 SDK 统一称其为 `project_path`。

这些检查不是装饰。其中三个 capture 方法最能说明问题：`recordObservation` 是
fire-and-forget，会吞掉后端返回的一切——`tool_name` 为空时后端返回
`400 Missing required field: tool_name`，SDK 记一条日志后 resolve，调用方于是认为
观测已记录，而服务器刚刚拒绝了它。`cwd` 为空则更隐蔽，因为后端**接受**它：记录会以
空项目路径入队，随后不出现在任何按项目过滤的查询里，全程没有任何错误提示。

`search` 属于同一类隐患：SDK 总会发送 `project`，而 `GET /api/search?project=` 会
返回 `200` 加一个空结果集，因此漏传参数的调用方读到的是「没有匹配」而不是「你的调用
不合法」。

`retrieveExperiences` 与 `buildICLPrompt` 是上面那张表**唯一不完整**的地方，而且
隐患在**另一个**字段上：两者都接受 `req.project` 且**都不校验**，而 `search` 反而
校验 project。对这两个方法来说这个顺序恰好是反的——
`POST /api/memory/experiences` 与 `POST /api/memory/icl-prompt` 会把该值直接传入
仓储查询且没有跨全部项目的分支，因此缺失或为空的项目匹配不到内容，返回 `200` 加
空结果而不是报错。活体实测：省略 `project`、传 `""`、传不存在的路径三者都返回
`200 []`，而真实项目路径才会返回经验。由于 `project` 在 TypeScript 的请求类型里
是可选的，省略它能通过类型检查，却会静默地什么也没检索到。

`listObservations` 与无参的 getter（`getStats`、`getProjects`、`getModes`、
`getSettings`、`getVersion`、`healthCheck`）没有必填参数；`getStats` 接受一个可选的
项目过滤条件。

这句说的是**必填参数**，不是**影响范围**——而在 `listObservations` 上两者指向相反
方向。`listObservations` 是唯一一个项目过滤会**放宽**而非**清空**的检索方法：省略 `project`
（传空串也一样，四家 SDK 都会把它转成省略），SDK 就不会发出 `project` 参数，
后端随即返回**该实例上全部项目**的观测。活体实测：省略 `project` 的
`GET /api/observations` 在一个 100 条的单页里返回了 **16 个不同项目**的数据。
而**直连 HTTP** 带上字面量 `?project=` 则是相反的情形、会返回空——因为仓储查询判的是
`IS NULL` 而非空串；绕过 SDK 直连前值得知道这一点。

因此它与上面那两个 ICL 端点恰好互为镜像：那里空项目**静默清空**结果，这里空项目
**静默放宽**结果。在多租户部署中，这是**跨租户数据外泄**而不是「少给了一条答案」，
而且没有任何客户端校验能拦住它——因为这个调用本身是合法的。

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

**Observation（读取）—— 列表列的到达形态不止一种：**
无论线上以何种形态到达，`facts`、`concepts`、`filesRead`、`filesModified`、
`refinedFromIds` 始终以 `string[]` 返回。但它们**到达的方式并不相同**，这个区别值得知道。

前四个是 JSONB 列，后端为 WebUI 把它们序列化成 **JSON 编码的字符串**，因此线上的一条
observation 到达时是 `concepts: '["allergy","peanut"]'`，而不是 JSON 数组。
解析器两种形态都能处理；若字符串不是合法 JSON，则按逗号切分降级。

`refinedFromIds` 不在其列。它是存放逗号分隔 UUID（`"uuid-1,uuid-2"`）的 `TEXT` 列
—— 完全没有 JSON 编码，后端用 `,` 拼接 ID —— 因此对它而言按逗号切分是唯一适用的路径。

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
