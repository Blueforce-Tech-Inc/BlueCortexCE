# BlueCortexCE：与记忆相关的 API 表面（速查）

> **角色**：给 Agent 的**一页速查**：读出路径、**写入/索引**、HTTP 与双栈。  
> **最后更新**：2026-04-19（§3.1 `workerHttpRequest` 索引）

---

## 1. 三条读出路径（概念层，与 [`10`](./10-aspect-bluecortex-implementation-map.md) §3 一致）

| 路径 | HTTP | Java（`backend/`）核心 | Worker（`webui`）侧 |
|------|------|------------------------|-------------------|
| **时间线注入** | `GET /api/context/inject`、`POST /api/context/generate` 等 | `ContextService.generateContext*` → type/concept + summary 时间线 | 本地 SQLite + `ContextBuilder.generateContext`（不经过 Java 时由 worker 自算） |
| **按 query 的语义注入** | `POST /api/context/semantic` | `ContextController.semanticContext`：`embed` → **`SearchService.search`** → pgvector 混合策略 | `SearchRoutes.handleSemanticContext`：`SearchManager` + **Chroma**（注释写明与 Java 同路径语义） |
| **搜索（列表）** | `GET /api/search` | `ViewerController` → `SearchService` | Worker 亦有搜索路由（见 `SearchRoutes` / `SearchManager`）；**js-sdk** 默认对接 Java 风格 API |

**要点**：`semantic` 与 `search` 在 **Java** 侧共用 **`SearchService`**（PostgreSQL/pgvector）。**Bun Worker** 侧同名 `semantic` 走 **Chroma**，与 Java **不是同一进程、同一存储**——读 trace / 对照 Evolver 时必须先确认流量落在 **哪一进程**（判别法见 [`15-runtime-integration-surfaces.md`](./15-runtime-integration-surfaces.md)）。**注意**：OpenClaw **Java** 插件配置项虽名 `workerPort`，自检用 **`/actuator/health`**，目标为 **Spring**，不是 Bun Worker。

### 1.1 `POST /api/context/semantic`：HTTP 契约与双栈差异（调研）

两端均实现为 **`{ "q", "project"?, "limit"? }` → `{ "context", "count" }`**，且 **`q` 长度不足 20 字符**时返回空块（Worker：`SearchRoutes.ts` `handleSemanticContext`；Java：`ContextController.semanticContext`）。**`limit`** 在 **1–20** 内钳位。

| 维度 | Bun Worker | Java Spring |
|------|------------|-------------|
| 语义检索 | `SearchManager` → Chroma `queryChroma` → SQLite 补水 | `embeddingService.embed` → `SearchService.search` → pgvector |
| 注入正文段落字段 | `obs.narrative`（SQLite 行） | `ObservationEntity.getContent()`（列名 `content`，JSON 别名 **`narrative`**） |
| 不可用时的行为 | `catch` 后 `{ context: '', count: 0 }` | `embeddingService` 不可用时直接空块；异常同理 |
| **`project` 缺省** | 由调用方传入；Chroma 侧可按 `project` 过滤 | `null`/blank 时默认 **`System.getProperty("user.dir")`** |

**结论**：**HTTP 形状**可对齐联调；**排序与命中**仍取决于 **向量模型 + 索引集合**（各栈只覆盖**写入本栈**的观察），与 [`11`](./11-research-backlog.md) 中「双栈语义一致性」条目同一问题域。

---

## 2. 写入与索引（数据平面，调研摘要）

| 平面 | 主存储 | 向量 / 语义索引 | 典型入口（代码锚点） |
|------|--------|-----------------|----------------------|
| **Worker（Claude Code 默认）** | **SQLite**（`SessionStore` / `observations` 表） | **Chroma**（`ChromaSync.syncObservation`，在事务提交后 fire-and-forget） | `webui/src/services/worker/agents/ResponseProcessor.ts`（`storeObservations` → `syncAndBroadcastObservations`） |
| **Java 胖服务器** | **PostgreSQL** `mem_observations` | **pgvector**（`embedding_*` + HNSW） | Hook **瘦代理** → `IngestionController` 等 → `AgentService.saveObservation`；或直接 `POST /api/ingest/observation` |

**会话「开局」别混路径**：Claude Code **`session-init` Hook** 先调 **Bun Worker** **`POST /api/sessions/init`**（`webui/src/cli/handlers/session-init.ts` → `SessionRoutes` 内 `handleSessionInitByClaudeId`），走的是 **SQLite** 会话栈；**`proxy/wrapper.js` / OpenClaw** 的 session-start 调 **Java** **`POST /api/session/start`**（`SessionController`，缓存 + `generateContext`，见 [`17-session-lifecycle-java-sketch.md`](./17-session-lifecycle-java-sketch.md)）。**速览表**（栈 / 路由 / 锚点）见 [`15-runtime-integration-surfaces.md`](./15-runtime-integration-surfaces.md) §5。同一次本机 trace 里两种调用**可能都出现**（Hook→Worker 与 wrapper→Java 并行），排查时以 **URL 路径**为准。

**结论**：两条链路 **并行**，**没有**在代码里看到「写入 SQLite 即自动同步 Postgres」的统一双写。**瘦代理** `proxy/wrapper.js` 还会向 **Java** 投递 `/api/ingest/*` 等（与 Worker **并列**，见 [`15`](./15-runtime-integration-surfaces.md) §4）。对照 Evolver 的 `memory_graph.jsonl` 时：Worker 侧更接近 **本地文件型 + 向量侧车**；Java 侧更接近 **关系库 + pgvector**。若产品要「单一真源」，需在架构层显式定义同步或只保留一条主链（见 [`11`](./11-research-backlog.md)）。

---

## 3. 仓库内调用方（调研摘要）

| 组件 | 记忆相关调用 | 备注 |
|------|----------------|------|
| **`webui` `session-init` Hook** | **`POST /api/sessions/init`**（建会话/隐私门闩）；随后可选 **`POST /api/context/semantic`**；再可选 **`POST /sessions/{sessionDbId}/init`**（SDK agent） | 均经 `workerHttpRequest` 打 **Bun Worker**（`http://` + `CLAUDE_MEM_WORKER_HOST` + `:` + `CLAUDE_MEM_WORKER_PORT`，见 [`15`](./15-runtime-integration-surfaces.md) **§2.1**）；入口 `session-init.ts`；路由 `SessionRoutes.ts` |
| **`webui/openclaw`（TS）** | **`POST /api/sessions/init`**；`GET /api/context/inject`、`/api/context/recent` 等 | **`workerPost` / `workerGetText`** → **Worker**（与 [`15`](./15-runtime-integration-surfaces.md) §2 一致） |
| **OpenCode 插件** | **`POST /api/sessions/init`**；`POST /api/sessions/observations` 等 | **Worker**（`opencode-plugin/index.ts`） |
| **`openclaw-plugin`（Java）** | **`POST /api/session/start`**（`session_start` / `after_compaction`）；`GET /api/context/inject` 等；**ingest** `workerPostFireAndForget` | **Java**（端口配置名 `workerPort` 实为 Spring）；见 [`15`](./15-runtime-integration-surfaces.md) §2 |
| **`js-sdk/cortex-mem-js`** | **`POST /api/session/start`**；**`POST /api/ingest/*`**；`GET /api/search` 等 | **Java**（`client.ts` 基址） |
| **`proxy/wrapper.js`** | **`callJavaApi`**：`/api/session/start`、`/api/ingest/*`、`…/api/search/by-file` 等 | **Java** |
| **Codex watcher** | **`POST /api/session/start`** 等 | **Java**（`codex-watcher/src/api.ts`） |

### 3.1 其它 **`workerHttpRequest`** 命中 Worker 的入口（非穷尽）

与 §3「产品级集成」互补：下列模块同属 **Hook / CLI / 侧车** 路径，默认仍走 [`15`](./15-runtime-integration-surfaces.md) **§2.1** 的基址（**不是** `JAVA_API_URL`）。

| 类别 | 代码锚点 | 代表性 HTTP（概念） |
|------|----------|---------------------|
| **SessionStart `context` Hook** | `webui/src/cli/handlers/context.ts` | **`GET /api/context/inject`**（`projects`、`platformSource`；可选 `colors`） |
| **`user-message` Hook** | `webui/src/cli/handlers/user-message.ts` | **`GET /api/context/inject`**（`project` + 可选 `colors`） |
| **会话收尾** | `summarize.ts`、`session-complete.ts` | **`/api/sessions/summarize`**、**`status`**、**`complete`** |
| **观察写入（Hook）** | `observation.ts`、`file-edit.ts` | **`POST /api/sessions/observations`** |
| **按文件取观察** | `file-context.ts` | **`GET /api/observations/by-file`** |
| **Cursor 规则刷新 / 安装** | `webui/src/services/integrations/CursorHooksInstaller.ts` | **`GET /api/readiness`**、**`/api/context/inject`**（写入 `.cursor/rules/...`） |
| **MCP 侧车** | `webui/src/servers/mcp-server.ts` | **`/api/search`**、**`/api/timeline`**、**`POST /api/observations/batch`**、**`/api/corpus*`**、自检 **`/api/health`**（工具名映射见该文件常量表） |
| **Transcript 处理器** | `webui/src/services/transcripts/processor.ts` | **`/api/sessions/summarize`** 等 |
| **Markdown 工具链** | `webui/src/utils/claude-md-utils.ts` | 若干 **`workerHttpRequest`**（辅助查询/写回，以源码为准） |

**排查**：若 trace 里出现上表路径但**不确定**端口上是 Worker 还是 Java，仍回到 [`15`](./15-runtime-integration-surfaces.md) **§3** 做 health 探测。

---

## 4. 其它相关端点（节选）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/context/preview` | WebUI Viewer：`useContextPreview` → worker/Java（视部署） |
| GET | `/api/context/timeline` | 时间轴片段 |
| GET | `/api/search/by-file` | Java `ViewerController`；worker 侧另有 observations-by-file 类路由 |

---

## 5. 与 Evolver 对照时的读法

- **时间线**：对齐「裁剪 narrative + 事件序」；来源可能是 **本地 SQLite 上下文生成** 或 **Java `generateContext`**，取决于集成方式。
- **语义块**：Claude Code 默认 Hook 路径上 **已有** `semantic` 注入（经 **worker**）；与 Evolver「按需检索再写入 prompt」**产品形态相近**，但存储为 **Chroma（worker）** 或 **Postgres（Java）** 二选一，勿混为一谈。
- **策略树**：Java 见 `SearchService.java`；Worker 见 `SearchManager` 与 `SearchRoutes`。

---

## 6. 相关文档

- 实现锚点 + 缺口：[`10-aspect-bluecortex-implementation-map.md`](./10-aspect-bluecortex-implementation-map.md)  
- Java 产出链速写：[`14-context-output-pipeline-sketch.md`](./14-context-output-pipeline-sketch.md)  
- Java 摄入 / 写入链速写：[`16-ingestion-write-path-sketch.md`](./16-ingestion-write-path-sketch.md)  
- Java 会话 start / end：[`17-session-lifecycle-java-sketch.md`](./17-session-lifecycle-java-sketch.md)  
- 运行时集成（Worker/Java、**wrapper→Java**）：[`15-runtime-integration-surfaces.md`](./15-runtime-integration-surfaces.md)  
- 可接力课题：[`11-research-backlog.md`](./11-research-backlog.md)  
- 总导航：[`../memory-research-hub.md`](../memory-research-hub.md)  
- Hermes 注入面 / 安全：[`../hermes-memory/20-recommendations/04-ce-injection-and-context-api-surface.md`](../hermes-memory/20-recommendations/04-ce-injection-and-context-api-surface.md) · [`05-ce-context-security-gap-inventory.md`](../hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md)
