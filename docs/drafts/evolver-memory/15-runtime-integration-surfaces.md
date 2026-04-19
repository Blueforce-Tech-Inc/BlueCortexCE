# BlueCortexCE：运行时集成面（Bun Worker vs Java Spring）

> **角色**：减少 Agent 在「同名 `/api/context/*`、默认端口又常是 37777」时的误判：**先分清请求落在哪个进程**，再谈 Chroma vs pgvector。  
> **配套**：[`12-bluecortex-api-memory-surface.md`](./12-bluecortex-api-memory-surface.md)、[`14-context-output-pipeline-sketch.md`](./14-context-output-pipeline-sketch.md)（Java 链）、[`10`](./10-aspect-bluecortex-implementation-map.md) §3。  
> **最后更新**：2026-04-19（§5 Worker init vs Java `17`）

---

## 1. 两个常见的 HTTP 服务

| 进程 | 典型入口 | 健康检查 | 语义检索存储 |
|------|-----------|----------|----------------|
| **Bun Worker**（`webui` `WorkerService`） | `webui/src/services/worker-service.ts`（`listen` 使用 `getWorkerPort()`） | **`GET /api/health`**（见 `webui/src/shared/worker-utils.ts`） | **`SearchRoutes` → `SearchManager` → Chroma**（`SearchRoutes.ts` 注释写明按 prompt 查 Chroma） |
| **Java Spring Boot**（`backend/`） | `server.port` 默认与 Worker **相同数字** `37777`（`application.yml`：`SERVER_PORT:37777`） | **`GET /actuator/health`**（OpenClaw 插件自检使用） | **`ContextController.semanticContext` → `SearchService` → PostgreSQL/pgvector**（见 `14`） |

**端口冲突提示**：两栈默认都瞄准 **37777**——通常一次部署**只会有一个**监听该端口；另一个需改端口或根本不启动。读日志/trace 时**不要**假设「37777 一定是 Chroma」或「一定是 Java」，用下节判别。

---

## 2. 集成路径 → 实际打到哪里

| 客户端 | 代码锚点 | 实际目标（设计意图） | `POST /api/context/semantic` |
|--------|-----------|----------------------|------------------------------|
| **Claude Code Hooks** | `webui/src/cli/handlers/session-init.ts` → `workerHttpRequest('/api/context/semantic')` | **Bun Worker**（`buildWorkerUrl` = `CLAUDE_MEM_WORKER_HOST` + `CLAUDE_MEM_WORKER_PORT`） | **Chroma 路径** |
| **OpenClaw Java 插件** | `openclaw-plugin/src/index.ts`：`workerGetText(workerPort, "/actuator/health")`、`/api/context/inject` | **Java 后端**（配置项名叫 `workerPort`，实为 **Spring 端口**） | **若调用则走 Java**（与 Worker 版 **同源路径、异进程**） |
| **产品架构长文** | `docs/ARCHITECTURE-zh-CN.md`（PostgreSQL 为真源） | 多指 **Java + Postgres** 部署 story | 以实际监听 37777 的进程为准 |

---

## 3. 调研时建议的「第一步」

1. `curl -sS http://127.0.0.1:<port>/api/health` — 若 200 且 JSON 像 worker，则为 **Bun Worker**。  
2. `curl -sS http://127.0.0.1:<port>/actuator/health` — 若 200，则为 **Spring**。  
3. 再打开对应仓库路径：`SearchRoutes.ts`（Worker）或 `ContextController.java`（Java）。

---

## 4. 瘦代理 `proxy/wrapper.js` → Java（第三条「集成绳」）

Node **wrapper** 通过 **`JAVA_API_URL`**（默认 `http://127.0.0.1:37777`，见 `proxy/wrapper.js` 内 `CONFIG.javaUrl`）调用 **Spring**，与 **Bun Worker** 的 `workerHttpRequest` **不是同一管道**。一次完整会话 trace 里可能**同时**出现：wrapper→Java（ingestion）、Hook→Worker（SQLite+Chroma、语义注入）。

| 用途（概念） | 转发到 Java 的示例路径（摘自 `wrapper.js` 注释与 `callJavaApi`） |
|--------------|------------------------------------------------------------------|
| 会话 / 上下文 | `/api/session/start`；`/api/cursor/register`；`/api/cursor/context/...` |
| Ingestion | `/api/ingest/tool-use`、`/api/ingest/user-prompt`、`/api/ingest/session-end` |
| 文件关联检索 | `GET …/api/search/by-file`（直接对 `javaUrl` 发请求） |

**与 [`12`](./12-bluecortex-api-memory-surface.md) §2 的关系**：Java 侧 **Postgres** 观察多经 **ingestion** 进入；Worker 侧 **SQLite** 经 **ResponseProcessor** 写入。二者 **并行**，勿从「wrapper 只打一端」推断另一端无数据。

---

## 5. 会话「首跳」：Worker `POST /api/sessions/init` vs Java `POST /api/session/start`

与 [`17-session-lifecycle-java-sketch.md`](./17-session-lifecycle-java-sketch.md)（**仅 Java**）成对阅读：Claude Code **默认 Hook** 不经 Java `session/start`，而是先打 **Worker**。

| 栈 | 路由 | 作用（概念） | 锚点 |
|----|------|--------------|------|
| **Bun Worker** | **`POST /api/sessions/init`** | 按 `contentSessionId` 建/复用 SDK 会话行、递增 prompt 号、隐私检查等 | `webui/src/cli/handlers/session-init.ts`；`SessionRoutes` 内 `handleSessionInitByClaudeId`（注释写明 `new-hook` 使用） |
| **Java Spring** | **`POST /api/session/start`** | 初始化会话 + **上下文缓存**命中则 `generateContext` 等 | 见 [`17`](./17-session-lifecycle-java-sketch.md) |

**同一会话后续（Worker）**：`POST /sessions/{sessionDbId}/init`（启动 SDK/OpenRouter 等 agent）、`POST /api/context/semantic`（§2 表）。**勿**与 Java `17` 混为一条调用链。

---

## 6. 相关文档

- 总导航：[`../memory-research-hub.md`](../memory-research-hub.md)  
- HTTP / 数据平面：[`12`](./12-bluecortex-api-memory-surface.md)  
- Java 产出链：[`14`](./14-context-output-pipeline-sketch.md)  
- Java 摄入 / 写入链：[`16`](./16-ingestion-write-path-sketch.md)  
- Java 会话 start / end：[`17`](./17-session-lifecycle-java-sketch.md)  
- Hermes 注入面（与宿主进程无关，但插件侧需对齐）：[`../hermes-memory/20-recommendations/04-ce-injection-and-context-api-surface.md`](../hermes-memory/20-recommendations/04-ce-injection-and-context-api-surface.md)
