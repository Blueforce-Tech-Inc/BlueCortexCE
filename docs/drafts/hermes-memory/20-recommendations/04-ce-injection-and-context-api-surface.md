# BlueCortexCE 记忆注入面与 Context API — 与 Hermes 对照

> **目的**：把 Hermes 的「memory-context 围栏 + 注入位置」与**本仓库真实集成点**对齐，便于做 P0 安全、缓存与产品取舍；不重复 Hermes 长篇机制推导。  
> **Hermes 机制**（user 消息侧注入、fence、`sanitize_context`）：[`../40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md`](../40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md)  
> **行动优先级**：[`03-borrowing-synthesis-executable-priorities.md`](03-borrowing-synthesis-executable-priorities.md)  
> **Evolver → CE 落地锚点**（schema、混合检索、缺口）：[`../../evolver-memory/10-aspect-bluecortex-implementation-map.md`](../../evolver-memory/10-aspect-bluecortex-implementation-map.md)  
> **会话首跳**（Worker `POST /api/sessions/init` vs Java `POST /api/session/start`）：[`../../evolver-memory/15-runtime-integration-surfaces.md`](../../evolver-memory/15-runtime-integration-surfaces.md) §5  
> **日期**：2026-04-19

---

## 1. Hermes（内置型）— 仅记结论

- Prefetch 结果经 `build_memory_context_block` 包进 **当前 API 回合的 user message 副本**（`run_agent` 内临时 `api_msg`），并带 `<memory-context>` 与 system note；**不落库为「用户原文」**。  
- `sanitize_context` 先剥离内容里伪造的 fence 标签，再包装，降低逃逸与注入。  
- **权衡**：system prompt 可保持冻结以利 prefix cache；动态 recall 走 user 侧，每轮可变。

---

## 2. BlueCortexCE（旁路型）— 消费侧集成点

| 集成点 | 注入落点（相对对话消息） | 代码入口（本仓库） |
|--------|--------------------------|-------------------|
| OpenClaw Java 插件 | **System**（`appendSystemContext`） | [`openclaw-plugin/src/index.ts`](../../../../openclaw-plugin/src/index.ts)（`before_prompt_build`） |
| OpenClaw / worker 链（TS） | **System** | [`webui/openclaw/src/index.ts`](../../../../webui/openclaw/src/index.ts)（`before_prompt_build`） |
| Claude Code Hook（会话初始化路径） | **Hook 附加上下文**（`UserPromptSubmit` 的 `additionalContext`） | [`webui/src/cli/handlers/session-init.ts`](../../../../webui/src/cli/handlers/session-init.ts)：先 **`POST /api/sessions/init`**（Bun Worker / SQLite 会话栈）；在满足条件时再 **`POST /api/context/semantic`**（Worker → Chroma，非 Java pgvector） |
| Spring AI | **System**：`SystemMessage` 插入 instructions 首部 | [`cortex-mem-spring-integration/cortex-mem-spring-ai/src/main/java/com/ablueforce/cortexce/ai/advisor/CortexMemoryAdvisor.java`](../../../../cortex-mem-spring-integration/cortex-mem-spring-ai/src/main/java/com/ablueforce/cortexce/ai/advisor/CortexMemoryAdvisor.java)（`enrichRequest` / `buildICLPrompt`） |

**与 Hermes 的差异**：CE 多条路径把记忆放进 **system** 或 **hook 附加字段**，而不是 Hermes 默认的「user 字符串尾部拼接」。**P0 仍建议**：在 **后端拼装**与/或 **插件写入模型前** 做与 Hermes 同类的消毒与围栏，避免观察正文闭合宿主侧标签或冒充指令边界。

### 2.1 会话「首跳」与注入面分开记

**Claude Code 默认 Hook 不经 Java `POST /api/session/start`**；该路由由 **wrapper、`js-sdk`、OpenClaw Java 插件** 等打 **Spring**（配置里端口名常仍叫 `workerPort`）。首跳对照表、Worker 其它调用方与排查顺序见 [`../../evolver-memory/15-runtime-integration-surfaces.md`](../../evolver-memory/15-runtime-integration-surfaces.md) §5；**各集成客户端默认打 Worker 还是 Java** 的汇总表见同文 **§2**（与 [`12-bluecortex-api-memory-surface.md` §3](../../evolver-memory/12-bluecortex-api-memory-surface.md) 调用方表互补）。**Hook / MCP / Cursor** 等打到 Worker 的细路径见 [`12` §3.1](../../evolver-memory/12-bluecortex-api-memory-surface.md)。数据平面（SQLite+Chroma ∥ Postgres+pgvector）见 [`12`](../../evolver-memory/12-bluecortex-api-memory-surface.md) §2。

---

## 3. 后端 `/api/context` 面（Java）

控制器：[`backend/src/main/java/com/ablueforce/cortexce/controller/ContextController.java`](../../../../backend/src/main/java/com/ablueforce/cortexce/controller/ContextController.java)（`@RequestMapping("/api/context")`）。

| HTTP | 路径 | 用途（简述） |
|------|------|----------------|
| GET | `/inject` | 插件主路径：返回 JSON（含可注入 `context`、`updateFiles` 等） |
| GET | `/recent` | 近期观察摘录 |
| GET | `/timeline` | 时间线窗口 |
| POST | `/generate` | LLM 综合生成（与 Honcho dialectic **思想**对照见 [`02-bluecortexce-recommendations.md`](02-bluecortexce-recommendations.md)） |
| GET | `/preview` | 文本预览 |
| GET | `/prior-messages` | 会话先验消息 |
| POST | `/semantic` | 按当前 prompt 的语义检索（**Spring** 路径；OpenClaw **Java** 插件等使用。**Claude Code Hook** 默认打 **Worker** 同名路由 → Chroma，见 §2 与 [`../../evolver-memory/12-bluecortex-api-memory-surface.md`](../../evolver-memory/12-bluecortex-api-memory-surface.md) §1） |

拼装与检索实现：`ContextService`、`SearchService`、`ObservationRepository`（混合检索与 schema 见 Evolver 对照稿 `10` 第 2 节）。

---

## 4. 后续探索清单（给实现 / code review）

1. **统一围栏**：是否在 `ContextService` 出口或各插件单点，对「写入模型的记忆块」采用固定 fence + strip 用户/观察中的同形序列（对齐 Hermes `memory_manager.sanitize_context` 语义）。  
2. **prefix cache**：CE 偏 system 注入时，与 Hermes「user 侧动态」的缓存行为不同；调优时在客户端观测实际 token 分区。  
3. **多入口一致性**：`inject` / `semantic` / `generate` 是否共享同一套脱敏与长度上限，避免一路收紧、另一路漏网。

**安全现状盘点**（Java 层已有 vs Hermes 类扫描）：[`05-ce-context-security-gap-inventory.md`](05-ce-context-security-gap-inventory.md)。
