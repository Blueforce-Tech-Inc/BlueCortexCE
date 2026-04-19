# BlueCortexCE：与记忆相关的 API 表面（速查）

> **角色**：给 Agent 的**一页速查**：读出路径、**写入/索引**、HTTP 与双栈。  
> **最后更新**：2026-04-19（§2 链至 `15` §4 wrapper）

---

## 1. 三条读出路径（概念层，与 [`10`](./10-aspect-bluecortex-implementation-map.md) §3 一致）

| 路径 | HTTP | Java（`backend/`）核心 | Worker（`webui`）侧 |
|------|------|------------------------|-------------------|
| **时间线注入** | `GET /api/context/inject`、`POST /api/context/generate` 等 | `ContextService.generateContext*` → type/concept + summary 时间线 | 本地 SQLite + `ContextBuilder.generateContext`（不经过 Java 时由 worker 自算） |
| **按 query 的语义注入** | `POST /api/context/semantic` | `ContextController.semanticContext`：`embed` → **`SearchService.search`** → pgvector 混合策略 | `SearchRoutes.handleSemanticContext`：`SearchManager` + **Chroma**（注释写明与 Java 同路径语义） |
| **搜索（列表）** | `GET /api/search` | `ViewerController` → `SearchService` | Worker 亦有搜索路由（见 `SearchRoutes` / `SearchManager`）；**js-sdk** 默认对接 Java 风格 API |

**要点**：`semantic` 与 `search` 在 **Java** 侧共用 **`SearchService`**（PostgreSQL/pgvector）。**Bun Worker** 侧同名 `semantic` 走 **Chroma**，与 Java **不是同一进程、同一存储**——读 trace / 对照 Evolver 时必须先确认流量落在 **哪一进程**（判别法见 [`15-runtime-integration-surfaces.md`](./15-runtime-integration-surfaces.md)）。**注意**：OpenClaw **Java** 插件配置项虽名 `workerPort`，自检用 **`/actuator/health`**，目标为 **Spring**，不是 Bun Worker。

---

## 2. 写入与索引（数据平面，调研摘要）

| 平面 | 主存储 | 向量 / 语义索引 | 典型入口（代码锚点） |
|------|--------|-----------------|----------------------|
| **Worker（Claude Code 默认）** | **SQLite**（`SessionStore` / `observations` 表） | **Chroma**（`ChromaSync.syncObservation`，在事务提交后 fire-and-forget） | `webui/src/services/worker/agents/ResponseProcessor.ts`（`storeObservations` → `syncAndBroadcastObservations`） |
| **Java 胖服务器** | **PostgreSQL** `mem_observations` | **pgvector**（`embedding_*` + HNSW） | Hook **瘦代理** → `IngestionController` 等 → `AgentService.saveObservation`；或直接 `POST /api/ingest/observation` |

**结论**：两条链路 **并行**，**没有**在代码里看到「写入 SQLite 即自动同步 Postgres」的统一双写。**瘦代理** `proxy/wrapper.js` 还会向 **Java** 投递 `/api/ingest/*` 等（与 Worker **并列**，见 [`15`](./15-runtime-integration-surfaces.md) §4）。对照 Evolver 的 `memory_graph.jsonl` 时：Worker 侧更接近 **本地文件型 + 向量侧车**；Java 侧更接近 **关系库 + pgvector**。若产品要「单一真源」，需在架构层显式定义同步或只保留一条主链（见 [`11`](./11-research-backlog.md)）。

---

## 3. 仓库内调用方（调研摘要）

| 组件 | 记忆相关调用 | 备注 |
|------|----------------|------|
| **`webui` `session-init` Hook** | `POST /api/context/semantic`（`workerHttpRequest`） | `CLAUDE_MEM_SEMANTIC_INJECT` 为 `true`（默认）且 `prompt.length >= 20` 时注入 `additionalContext`；见 `webui/src/cli/handlers/session-init.ts` |
| **`openclaw-plugin`** | `GET /api/context/inject`、`/api/context/recent`、`/api/context/timeline`、`GET /api/search` | **未**发现 `semantic` 字符串匹配 |
| **`js-sdk/cortex-mem-js`** | `GET /api/search` | `client.ts` |
| **`proxy/wrapper.js`** | `POST` → Java `…/api/search/by-file` 等 | 文件关联时间线 |

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
