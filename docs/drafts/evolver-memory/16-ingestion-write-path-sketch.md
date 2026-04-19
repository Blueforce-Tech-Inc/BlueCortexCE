# BlueCortexCE：摄入 / 写入链速写（Java 瘦代理 → Postgres）

> **角色**：与 [`14`](./14-context-output-pipeline-sketch.md)（**读出**）对称，帮助 Agent 从 **Hook → `IngestionController` → `AgentService`** 快速定位观察落库与异步队列。  
> **Worker（SQLite + Chroma）写入**：见 [`12`](./12-bluecortex-api-memory-surface.md) §2，**不**在本篇展开。  
> **wrapper→Java 集成绳**（与 Worker 并列）：[`15`](./15-runtime-integration-surfaces.md) §4。  
> **最后更新**：2026-04-19

---

## 1. 总览

```
wrapper.js (瘦代理)
  POST /api/ingest/tool-use      → IngestionController → AgentService.processToolUseAsync (@Async)
  POST /api/ingest/session-end   → …                    → SummaryGenerationService.completeSessionAsync
  POST /api/ingest/user-prompt   → …                    → UserPromptRepository + SessionManagementService
  （session-start 在 SessionController，非本类）
```

重处理：**快速 ACK**；工具使用路径经 **`mem_pending_messages`** 持久化后可重试。

---

## 2. `IngestionController` 与 wrapper 映射

文件：`backend/src/main/java/com/ablueforce/cortexce/controller/IngestionController.java`

```32:44:backend/src/main/java/com/ablueforce/cortexce/controller/IngestionController.java
/**
 * Ingestion controller — receives hook events from the thin proxy (wrapper.js).
 *
 * This controller handles ONLY events that come from Claude Code hooks.
 * All endpoints are fire-and-forget: they return immediately with 200 OK,
 * and heavy processing is done asynchronously via AgentService.
 *
 * Endpoint mapping (wrapper.js -> this controller):
 *   wrapper.js session-start  -> /api/session/start (SessionController)
 *   wrapper.js tool-use       -> /api/ingest/tool-use
 *   wrapper.js session-end    -> /api/ingest/session-end
 *   wrapper.js user-prompt    -> /api/ingest/user-prompt
 */
```

---

## 3. 工具使用 → 观察（主链）

**HTTP**：校验与限流后 **`agentService.processToolUseAsync(...)`**（不阻塞响应）。

```138:149:backend/src/main/java/com/ablueforce/cortexce/controller/IngestionController.java
        java.util.UUID sessionDbId = sessionManagementService.findByContentSessionId(contentSessionId)
            .map(SessionEntity::getId)
            .orElse(null);

        // Fire and forget — async observation extraction
        agentService.processToolUseAsync(
            sessionDbId, contentSessionId,
            toolName, toolInput, toolResponse, cwd, null
        );

        return ResponseEntity.ok(Map.of("status", "accepted"));
```

**异步体内**（摘要）：`ensureSession` / 会话过旧守卫 → **去重**（`pendingMessageRepository.existsBySessionAndTool`）→ 写入 **`PendingMessageEntity`** → **`callLlmAndSaveObservation`** → **`saveObservation`**（`content_hash` 短窗去重、落 `mem_observations`、后续嵌入等见 [`10`](./10-aspect-bluecortex-implementation-map.md) §2）。

```115:186:backend/src/main/java/com/ablueforce/cortexce/service/AgentService.java
    @Async
    public void processToolUseAsync(UUID sessionDbId, String contentSessionId,
                                     String toolName, String toolInput, String toolResponse,
                                     String cwd, Integer promptNumber) {
        // ... session resolve, age guard, pending dedup ...
            pending = pendingMessageRepository.save(pending);
            // ...
            boolean saved = callLlmAndSaveObservation(
                contentSessionId, toolName, toolInput, toolResponse, cwd, promptNumber, pending);
```

---

## 4. 其它摄入端点（简述）

| 路由 | 作用 |
|------|------|
| `POST /api/ingest/user-prompt` | 记录用户 prompt（长度截断）；见 `handleUserPrompt` |
| `POST /api/ingest/session-end` | `summaryGenerationService.completeSessionAsync` — 会话结束与摘要 |
| `POST /api/ingest/observation` | 测试/直连写入观察（注释标明非 wrapper 主路径） |

---

## 5. 相关文档

- 数据平面（Worker ∥ Java）：[`12`](./12-bluecortex-api-memory-surface.md) §2  
- 实现能力表：[`10`](./10-aspect-bluecortex-implementation-map.md) §2  
- 读出链 + 模板转义（写入 LLM 侧）：[`14`](./14-context-output-pipeline-sketch.md)（§4）  
- 运行时进程：[`15`](./15-runtime-integration-surfaces.md)  
- 总导航：[`../memory-research-hub.md`](../memory-research-hub.md)
