# Backend Review Findings

> **Purpose**: 记录当前仍需处理的 Backend 代码审查发现。
> **Updated by**: 定时项目维护任务。
> **Update rule**: 新发现必须记录文件、行号、问题、严重级别和处理状态；已解决的详细历史归档，当前未解决项保留在本文件。

## Current Status

| Severity | Open | Rule |
|----------|------|------|
| P0 | 0 | 立即修复并复测 |
| P1 | 1 | 优先修复并复测 |
| P2 | 2 | 本轮完整验收阶段处理或明确标记为已跳过 |

- **已整体迁出**（current-status-note）: 逐字迁入 [`2026-10-04_backend-review-resolved-15.md`](../archive/2026-10-04_backend-review-resolved-15.md)（第 263 轮）。

## Open Findings

### P2-24: V17 反馈机制整体未接线 —— 实体还映射了一个不存在的列

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**：本轮从 P1-3 顺藤摸下来，发现 **V17 从未被记录为 finding**。三条实证：
  ①`ObservationFeedbackEntity` 有一个 `@Column(name = "created_at")` 的 `OffsetDateTime` 字段，
  而 `V17__observation_feedback.sql` **从未创建该列**——活体 `information_schema` 确认
  `observation_feedback` 只有 `id / observation_id / signal_type / session_db_id /
  created_at_epoch / metadata` 六列。Hibernate 对 `SELECT f FROM ObservationFeedbackEntity f`
  这类不指定列的 JPQL 会**逐个 SELECT 全部映射列**，因此**任何**触及该实体的查询都会报
  `column "created_at" does not exist`。**已修**：删除该字段与其 getter/setter，
  并在 `createdAtEpoch` 上写明不要在无迁移的情况下加回。
  ②`findByObservationIdOrderByCreatedAtDesc` 的 `@Query` 实际按 `createdAtEpoch` 排序，
  **方法名描述的列根本不存在**，且零调用方。**已修**：改名为
  `findByObservationIdOrderByCreatedAtEpochDesc`，与 P1-3 是同一类「名字与实际不符」的陷阱。
  ③**V17 声明的三项能力全部没有写入方**：活体库 `observation_feedback` **0 行**、
  `generated_by_model` 非空 **0 行**、`relevance_count <> 0` **0 行**；
  `grep setRelevanceCount` 在 `main` 源码中**零命中**。
  即「Thompson Sampling 优化的基础」目前是**纯脚手架**。
- **Severity 说明**：①是**潜伏缺陷**而非启动即崩——注入回错误映射后后端**仍能正常启动**，
  因为该表 0 行、repository 零调用方，Spring Data 不会预校验 JPQL 引用的列。
  它会在**第一次真正使用该实体时**炸掉。③是**未实现特性**而非错误行为。
- **Verification**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Status**：①②✅ **已修复**（2026-10-03，第 219 轮）。
  ③⏸ **记录不实现** —— 接入反馈采集属**新增特性**（需要新的写入路径、信号定义与
  Thompson Sampling 算法），不是修 bug，按既定纪律留待项目决策。
  **注**：`CLAUDE.md:39` 把 V17 标为「✅ Complete」，该文件已被 gitignore，
  并入既有的 `AGENTS.md` / `CLAUDE.md` 开放项，不在本轮静默修改范围内。
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
### P1-1: `CortexSessionContextBridgeAdvisor.adviseStream` 依赖普通 ThreadLocal，流式下既丢捕获又泄漏会话

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `adviseStream` 在**调用线程**上 `begin()`，却把清理放进 `flux.doFinally(...)`。Reactor 的 `doFinally` 运行在**发出终止信号的线程**上；任何真实模型客户端（Reactor Netty / WebClient）都会切线程。产生两个后果：
  1. **捕获被静默丢弃** —— 工具实际执行的线程看不到该 ThreadLocal，`CortexSessionContext.isActive()` 为 false，`CortexToolAspect` 直接 `proceed()` 跳过捕获。`@Tool` 自动捕获在流式下等于失效，且无任何日志。
  2. **会话上下文泄漏** —— `doFinally` 清掉的是信号线程（一个空 ThreadLocal），调用线程的 ThreadLocal 永不清除。线程池复用该线程后，`begin()` 因 conversation id 缺失而提前 return 的那条路径**也不会**清理，于是残留的 `sessionId` 会被下一次请求的 `CortexToolAspect` 当作有效会话使用——工具观察被归到**上一个会话**。这是静默的跨会话数据串号。
- **实测记录**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
### P2-8: 读取侧没有维度路由 —— 写入按维度分列，检索恒定比 `embedding_1024`

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `SearchService` 在 PATH 2 的注释自称 "Semantic search with pgvector
  (dimension-aware)"，第 59 行也确实算出了 `int dim = request.queryVector().length`，
  但该变量**只用于 debug 日志**，实际 SQL 始终与 `embedding_1024` 比较。仓库里
  `semanticSearch768` / `semanticSearch1024` / `semanticSearch1536` 三个方法带有正确的
  分维度 SQL，但**全仓零调用方**（`grep` 主代码与测试均无命中）。因此这是一个
  写侧已实现、读侧未实现的非对称。
- **实测记录**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
### P2-10: 四个 ingest 端点对项目路径的必填性不一致

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 同一族端点对同一个语义字段给出两种契约。实测（对运行中的后端）： | 端点 | 缺失/空白 `project_path`（或 `cwd`） |
  |------|------------------------------------| | `POST /api/ingest/observation` | **400** `Missing required field:
  project_path` | | `POST /api/ingest/tool-use` | **200** `{"status":"accepted"}` | | `POST /api/ingest/user-prompt` |
  **200** `{"status":"ok"}` | | `POST /api/ingest/session-end` | **200** `{"status":"ok"}` |
  `cwd` 省略与发送 `"cwd": ""` 行为相同。
- **实际影响**：仅影响直接使用 HTTP API 的调用方——四家 SDK 均已在客户端拒绝空
  `project_path`（本轮刚为 Python 补齐），因此 SDK 路径不会触发。直接调 API 的调用方
  会得到一条项目路径为空的记录：写入成功、返回 200，但该记录不会出现在任何按项目
  过滤的查询里，**且不会有任何错误提示**。`user-prompt` 的 `prompt_text` 同样缺失即
  接受（仅 `session_id` 被强制）。
- **未修的原因**：收紧另三个端点属于**对外 API 契约变更**，会影响既有直接调用方与
  薄代理 `wrapper.js` 的边界输入，属产品决策；本轮代码方向为 Python SDK，按轮换
  纪律不在本轮动手。
- **Status**: ⏸ 已记录不修（2026-10-02，第 175 轮 API 文档轮发现）。文档方向已在同轮
  于 `docs/API.md` / `docs/API-zh-CN.md` 三个端点各加一段说明，逐条写明「缺失与空串
  都会被接受」「`/api/ingest/observation` 是唯一严格的那个」「SDK 会在客户端拦截」，
  使读者不必自行推断这层差异。API 文档原本对两者的「必填」标注**是正确的**
  （observation 标 ✅、另三个标 ❌），本轮只是补上未言明的后果。

<!-- P2-11 已无条件解决，逐字迁入 2026-10-04_backend-review-resolved-3.md -->
### P2-13: Spring AI 集成无法按用户隔离记忆——会话上下文里没有 userId

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 后端**支持**按用户隔离 ICL 记忆（第 194 轮实测：同一项目下 alice 返 1 条
  经验、bob 返 0 条），`ICLPromptRequest` / `ExperienceRequest` 也都带 `userId`，
  `DefaultMemoryRetrievalService` 更是**已经实现并透传** `userId`。
  但真正把记忆注入 Agent 的两个组件——`CortexMemoryAdvisor` 与
  `CortexMemoryTools` 的两个读方法——**结构上做不到**：
  它们唯一的会话级状态是 `CortexSessionContext`，而那个类里根本没有 `userId` 字段。
  因此在多用户部署中，**自动注入给每个 Agent 的 ICL 上下文是项目级的、所有人相同**。
  手工调用 `client.buildICLPrompt(...)` 并自行设置 `userId` 是可行的——
  受影响的是自动路径。
- **影响面**：与 P2-11（错模板名不被拒绝）不同，这不是静默错值，而是**缺少一个能力**；
  后果是不同用户之间**记忆串味**（用户 A 的偏好会出现在用户 B 的提示里），
  在「每用户独立档案」类应用中属于数据可见性问题。
- **Status**: ⏸**已记录，本轮不实现**。修它需要给 `SessionInfo` 加字段、给 `begin()`
  加重载、把 userId 从调用方一路串到 advisor 与工具，属于**新增能力**而非修 bug；
  且 `CortexSessionContextBridgeAdvisor` 需要知道从何处取 userId（会话 id？应用配置？，
  还是新的 `begin()` 入参），这个选择应由项目决定而不是由巡检轮次决定。
  本轮已做的是**如实记录**：`cortex-mem-spring-integration/README.md` 与 `README-zh-CN.md`
  新增多用户段落，写明自动路径不做用户隔离、哪些端点其实认 `userId`、以及可用的手工做法。

### P2-14: `findNewObservations` 零调用方——增量抽取从未实现，却有索引为它而建

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 增量抽取**没有实现**。后端全文没有 `extraction_state`（0 命中），
  每次运行都取最新的 N 条、**没有「上次抽取之后」的过滤**。`findNewObservations`
  本身实现完好、SQL 正确，但 `backend/src/main` 中**零调用方**、连单测都没引用。
  与 P2-12（`deepRefineProjectMemories` 无调用方）同型：一个从未接线的特性，
  只留下方法、注释和一条为它建的索引。
  一个从未接线的特性，只留下方法、注释和一条为它建的索引。
- **实际行为（与文档描述不同）**：`findBySourceIn` 是 `ORDER BY created_at_epoch DESC LIMIT N`，
  所以新观测**会**进来，但超出上限的旧观测**永远不会被抽取**。既不是文档所称的
  「增量」，也不是「全量重扫」——是「每次重扫最新的 N 条」。
- **影响面**：纯成本与覆盖问题，不会返回错值；但 23.md 曾把它列为
  「primary cost reduction mechanism」，运维据此估算 token 预算会系统性偏低。
- **量化证据（第 202 轮补测）**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Status**: ⏸**已记录，不实现**。接上它需要持久化抽取状态（7.md §7.1 提议用
  `type="extraction_state"` 的观测行承载），属新增特性而非修 bug；且抽取状态的
  过期/重建语义应由项目决定。本轮已做的是**如实记录**：23.md §23.5 策略 3/4/5 全部补上
  「designed, not implemented」声明，并给出真实的候选选取路径与排序方向；
  8.md 第 5 条、0.2.md Gap 3、17.md §17.2 三处同一断言一并更正。
  **第 202 轮续做**：该次清扫**按文件逐个进行**，因此漏掉了同断言的另外两处
  （`00-quick-ref.md:14`、`15.md:211`）。本轮改为**按断言清扫**，并额外发现
  成本模型本身建立在这个不存在的机制上——23.md §23.2/§23.2b/§23.4/§23.5/§23.7
  已整体重写（月度抽取成本 $0.23 → $1.13，提炼占比 97%+ → ~89%），
  `0.3.md`、`structured-extraction.md`、`DEPLOYMENT.md` 三处同源说法一并更正。
  由此另立 **P2-17**（`EXTRACTION_MAX_BATCHES` 失效）与 **P2-18**
  （`reExtractForSession` 绕过全部上限）。

### P2-15: `save_memory` 的共享会话是 check-then-act，并发下必然丢失一次保存

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `mem_sessions.content_session_id` 上有**活体确认**的唯一约束
  （`pg_constraint`: `mem_sessions_content_session_id_key UNIQUE (content_session_id)`），
  而这里是典型的 check-then-act：两个并发的 `save_memory` 调用都会查不到、都会走
  `orElseGet` 去插入，第二次必然撞唯一约束。异常被外层捕获，返回
  `{"success": false, "error": "Failed to save memory: ..."}`。
- **影响面**：**不会写脏数据、也不会假报成功**（安全方向），但一次本该成功的
  记忆保存被报成失败，且信息误导——调用方看到的是「保存失败」而不是「并发冲突，请重试」。
  重试即可成功（此时会话已存在），所以属于瞬时可恢复的伪失败。
  MCP 工具调用可由 agent 并行发起，多客户端同理，因此并发是现实场景而非理论场景。
- **Status**: ⏸**已记录，不实现**。常规修法是捕获 `DataIntegrityViolationException`
  后重新查询会话再继续，但那要在 `orElseGet` 的懒执行路径里插入一次重试，
  改变的是该工具的错误语义与重试行为，属应由项目拍板的契约问题而非巡检轮次的修 bug。
  与 P2-13/P2-14 同一套判断。

### P2-16: Java SDK 没有任何类型化异常，HTTP 状态码只能靠遍历 cause 链取得

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 跨 SDK 错误面严重不对称——Go 有 `APIError` 且 `Unwrap()` 覆盖 11 个哨兵错误、
  Python 有 13 个状态码异常类 + 谓词（共 27）、JS 有 16 个，**Java 为 0**。
  实测（stub 返回 `404 {"error":"Observation not found: abc"}`）：
  抛出的是裸 `java.lang.RuntimeException`，消息为
  `getObservationsByIds failed: Observation not found: abc`（有后端原因、**无状态码**），
  cause 链末端才是 Spring 的 `HttpClientErrorException$NotFound`。
  异常类型本身**没有任何状态访问器**。
- **影响面**：要区分「没有这条观测」（404）与「后端挂了」（5xx）的调用方必须自己写
  cause 链遍历。项目自己的 Java demo 正是为此写了一份
  `examples/cortex-mem-demo/.../DemoErrors.java`（`statusOf` / `messageOf` / `clientStatus`），
  其 javadoc 明确记载了这个痛点——**这是本条最有力的证据**：
  同一仓库内的消费者已经为此付出过实现成本。
- **Status**: ⏸**已记录，不实现**。补齐意味着给本 SDK **新增公开异常类型**
  （如 `CortexMemException` / `APIError`），属新增对外 API 而非修 bug，
  且会改变所有 25 个方法的异常类型，对已有调用方的 `catch` 行为有影响，
  与 P2-13/P2-14/P2-15 同一套判断。本轮已做的是**如实记录**：
  两份 README 的 Error Handling 章节新增「HTTP 状态码不在异常上」小节，
  给出实测的异常形态、可直接复制的 `statusOf` 辅助方法、
  以及「这是与另三家的已知不对称」这一事实。

### P2-17: `EXTRACTION_MAX_BATCHES` 在随附默认值下永远不可能生效

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 该上限被文档当作真实生效的调参手段，但**在随附默认值下它是死的**。
  循环条件是 `i < userObs.size() && i < maxTotal`，其中
  `maxTotal = maxObservationsPerBatch × maxBatchesPerTemplate = 20 × 10 = 200`；
  而 `userObs` 是候选列表按用户分组后的一个切片，候选列表本身已被
  `initialRunMaxCandidates`（默认 100）截断。因此单个用户的观测数**永远 ≤ 100**，
  批次数上限是 `ceil(100/20) = 5`，**永远够不到 10**。
- **精确边界**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **影响**: 运维看到「Batches per template per run: 10」这一行，会合理地以为它是
  抽取成本的主要闸门，实际唯一生效的闸门是候选上限。这是**配置契约层面的误导**，
  修法要么调默认值，要么在 `ExtractionConfig` 里对二者做一致性校验。
- **Status**: ⏸**已记录，不实现**。改变任一默认值的取值范围属对外配置契约变更。
  本轮已在 `23.md` §23.5/§23.7、`docs/structured-extraction.md`、`docs/DEPLOYMENT.md`
  四处**按各自措辞**更正为「随附默认值下不生效」并写明生效条件。

### P2-18: `reExtractForSession` 绕过全部抽取上限，整会话一次性送入 LLM

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 这是结构化抽取的**第二个活入口**，但它**完全不走批处理循环**。
  候选来自 `findByContentSessionIdOrderByCreatedAtEpochAsc(sessionId)`——
  一个无 `LIMIT` 的派生查询，返回该会话的**全部**观测；随后对每个启用模板直接
  `extractByTemplate(template, filtered, priorJson)` **一次调用**，
  整个 `filtered` 列表进同一个 prompt。`initialRunMaxCandidates`、
  `maxObservationsPerBatch`、`maxBatchesPerTemplate` **三个上限一个都不生效**。
- **影响**: 一个含 60 条匹配观测的会话会产生一次输入约为 20 条批量 **3 倍**的
  调用，而所有成本文档的「每次调用」价格都是从 20 条批量推出的。更严重的是
  **无上界**——会话越长，单次 prompt 越大，直到超出模型上下文窗口才失败。
  失败被 `catch (Exception)` 吞掉并记日志（`:161-162`），调用方看到的仍是成功。
- **Status**: ⏸**已记录，不实现**。接入上限会改变该端点的既有行为，属对外契约变更。
  本轮已在 `23.md` §23.4 记录该入口未被任何成本表计价，并在
  `StructuredExtractionService` 的既有注释中保持路径事实不变。

### P2-19: Java SDK 静默吞掉 refinement / extraction 触发失败，另三家都抛错

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `executeWithRetrySilent` 返回 `void`，**任何失败都被吞掉**，只在
  日志里留一条 WARN。调用方拿到的是一个正常返回的 `void`，**无从得知触发失败**——
  精炼没跑、抽取没跑，而调用方以为跑了。
- **另三家都抛错，且是刻意为之**：
  - Go `client_methods.go:189` 甚至写了注释说明理由——
    「NOT fire-and-forget: this is an explicit user action, errors must propagate」；
  - JS `client.ts:300-306` / `:395-399` 走 `requestNoContent`，异常上抛；
  - Python `client.py:543-550` / `:670-677` 走 `_request_no_content`，异常上抛。
- **Java 自己的注释是误导的**：`executeWithRetrySilent` 的 javadoc 写着
  「Matches the Go, Python and JS SDKs」。就**重试与退避策略**而言确实一致
  （±25% 抖动、不重试 4xx/500），但**错误传播**恰恰是三家里 Java 唯一不同的那一点，
  而这正是调用方唯一能感知的部分。注释只对上了次要的一半。
- **同族的非静默差异**（不单独立项）：Java 还对 `submitFeedback` /
  `updateObservation` / `deleteObservation` / `getLatestExtraction` /
  `getExtractionHistory` 做了重试包装（`executeWithRetry` / `...Return`），
  而 Go/JS/Python 只在三个 fire-and-forget 采集方法上重试。这三个写操作
  本身**仍然抛错**，所以不是静默失败，只是重试面更宽——是否扩大属设计选择，
  与上面那条性质不同。
- **Status**: ⏸**已记录，不实现**。改这两处会**改变现有调用方的可观测行为**
  （原本被吞掉的异常会开始上抛），属对外行为契约变更，与 P2-13/P2-15/P2-16
  同一套判断；且需项目先决定这两条触发路径是否应纳入 fire-and-forget 语义。
  本轮代码方向为 Python SDK，已核实 Python 侧行为正确，故只记录。

### P2-20: 全部 22 个数值查询参数都会静默接受十六进制字面量

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: Spring 的默认数字转换会**静默采纳 `0x`/`0X` 十六进制前缀**。
  规则是：先 trim，带十六进制前缀走 `Integer.decode`，否则走 `Integer.valueOf`。
  实测（活体）：`?limit=0x10` → **16 条**、`?lines=0x10` → `{"returnedLines":16}`、
  `?maxObservations=0x10` → 200、`?startEpoch=0x10` → 200、`?offset=0x2` → 200。
  状态码一律 `200`，**响应中没有任何字段表明读的是一个十六进制字面量**。
  顺带定住机制的一点：`?limit=010` 返回 **10 而非八进制 8**——若真是 `Integer.decode`
  一路到底，`010` 会被读成 8，因此是「仅对十六进制前缀走 decode」。
- **危害**：`startEpoch` / `endEpoch` 是**时间戳**，`0x` 前缀会把它们解释成一个
  1970 年附近的 epoch 毫秒值，**静默返回空时间窗而无任何报错**。`lines` 会被读成
  一个行数，`maxObservations` 会被读成一个条数。都没有校验、没有警告。
- **一个必须说明的排查陷阱**：`/api/context/timeline` 对 `?limit=abc`（**根本无法
  解析**）与 `?limit=0x3` 返回**完全相同**的 `400 {"error":"No anchor found"}`——
  那是**解析成功之后**的领域错误。若只看状态码，会误判为「该端点严格拒绝十六进制」
  而漏掉这条。真正的解析失败返回的是 Spring 默认的
  `{"status":400,"error":"Bad Request"}`（如 `/api/context/preview` 所返回的）。
  **状态码不等于原因，必须读响应体。**
- **对照**：布尔参数**不受影响**（`?includeObservations=0x1` → 400）；四家 SDK 把这些
  参数声明为数字类型，**根本无法**把十六进制字面量放到线上，故只影响直接调用
  HTTP API 的代码。
- **Status**: ⏸**已记录，不实现**。收紧会把一批当前的 `200` 变成 `400`，
  属**对外 API 契约变更**，且波及 11 个端点；需项目先决定是否值得。本轮代码方向为
  Backend，已做的是**如实记录**：`docs/API.md` + `-zh-CN` 新增「Query Parameter
  Conventions / 查询参数约定」一节，写明通用规则、完整参数清单与该排查陷阱，
  并把 `limit` 小节改为指向它而非重复叙述。

### P2-21: 健康指示器在真故障时不给原因，而测试钉死了一个不可能发生的分支

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `healthCheck()` 自己 `catch` 后 **`return false`**、**从不向外抛出**，
  故 `health()` 的 `catch` 分支在生产中**不可达**，`withException(e)` 写出的
  `error` 键**永远不会被填充**。
  活体实测（真实 `CortexMemClientImpl`，指向死端口 39999，超时 500ms）：

  ```
  status  = DOWN
  details = {service=Cortex CE Memory Backend, reason=Health check returned false}
  hasErrorKey = false
  ```

  指向真实后端时 `status=UP`。即运维看到后端挂掉只能读到「Health check returned false」——
  **连接被拒 / 超时 / DNS 失败这些真正的原因全部丢失**，因为在客户端被 `log.debug` 吞掉
  （默认不输出）；「后端不可达」与「后报 degraded」两种不同情况给出**完全相同**的文案。
- **测试反而钉死了这个假象**：`CortexMemHealthIndicatorTest.health_whenClientThrows_returnsDown`
  用 **mock** 让 client 抛出并断言 `containsKey("error")`——该状态**真实 client 永远无法产生**，
  故此用例**恒真却毫无保护作用**：让人以为异常路径已覆盖，而生产中恰恰走不到。
  与第 197 轮「夹具传了后端从不下发的值」同类：测试覆盖的是**虚构状态**。
- **核实无误的部分**：`healthCheck()` 判定 `"ok"` 的大小写是对的（后端
  `HealthController.java:62` 返回 `dbReady ? "ok" : "degraded"`，**小写**；
  活体 `GET /api/health` 亦为 `{"status":"ok"}`），null body / 非 `ok` / 异常
  三种情况均正确返回 `false`，UP-DOWN 三分支本身正确——
  **缺陷只在「原因丢失」与「测试虚构」，不在判定逻辑。**
- **Status**: ⏸**已记录，不实现**。要让原因到达指示器，需要 `healthCheck()`
  改为向上抛出（**改变既有方法的行为契约**，所有调用方的 `catch` 都要重审），
  或为 client **新增公开 API**（如 `getLastHealthFailure()`）供指示器读取——
  两者都属对外契约变更，与 P2-13~P2-20 同一套判断，需项目先定方向。
  本轮代码方向为 Java SDK，已做的是**如实记录**与**如实核实**（含一次假设被证伪：
  初判「环境变量形式无法关闭 `capture-enabled`」，改用**真实环境变量**复测后
  证明 `CORTEX_MEM_CAPTURE_ENABLED=false` **有效**——原结论来自
  `withPropertyValues` 不模拟环境变量这一**探针缺陷**）。

### P2-22: `/api/cursor/projects` 的 Swagger 示例把 ISO 字符串写成了 epoch 数字

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 示例写作
  `"{\"projects\":[{\"projectName\":\"my-project\",\"workspacePath\":\"/path\",\"installedAt\":1709000000000}],\"count\":1}"`，
  即把 `installedAt` 标成一个 **epoch 毫秒数字**。实际类型是 **String**：
  `CursorService.CursorProjectEntry(String workspacePath, String installedAt)`
  （`CursorService.java:52-55`），活体返回
  `"installedAt": "2026-03-18T17:50:11.192503Z"`。**照此示例生成的客户端会把该字段
  当数字解析并直接失败**；同一控制器里 `GET /api/cursor/register/{projectName}` 的
  `installedAt` 示例同样是 epoch 数字，需一并核对。
- **Status**: ⏸ **记录不修** —— 修 Swagger 注解虽是小改动，但会改变对外发布的
  OpenAPI 契约内容，属对外契约变更，留待项目决策。**文档层已先行更正**：
  `docs/API.md` 与 `docs/API-zh-CN.md` 的 `GET /api/cursor/projects` 原本**只有
  一个代码块、既无描述也无响应示例**（英文版连中文版那一行描述都没有），现已按
  活体与源码补上完整响应示例，并明确 `installedAt` 是 ISO-8601 字符串、
  `count` 恒等于 `projects.length`（活体 16 == 16，已核对）。
- **复核记录**（原文见 [`2026-10-03_backend-review-provenance.md`](../archive/2026-10-03_backend-review-provenance.md)，逐轮全文另见 `patrol-rotation.md`）

### P2-23: SSE 连接数超限返回 500（应为 503），且没有心跳，死连接最长占用名额 30 分钟

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem 1 —— 状态码语义错误**：第 101 个客户端被拒时，后端**没有任何
  `@ControllerAdvice` / `@ExceptionHandler`**（全仓唯一命中的是
  `config/AsyncConfig.java` 的 `AsyncUncaughtExceptionHandler`，与 MVC 异常无关），
  `IllegalStateException` 直穿到容器默认处理。活体实测（105 条并发裸 socket）：
  **恰好 100 条 `200`，第 101–105 条 `500`**。容量耗尽是「服务暂时不可用」，
  返回 500 会让任何按 5xx 告警的监控在**每次触顶时都误报为服务故障**；应为 503
  （或 429）。且 `stream()` 的 `@ApiResponse` **只声明了 200**，该分支在契约中不存在。
  活体响应 `Content-Length: 0`，**不泄漏内部信息**——问题纯粹在状态码。
- **Problem 2 —— 没有心跳，死连接要等下一次事件才被回收**：清理只发生在
  `SseEmitter` 的 `onCompletion` / `onError` / `onTimeout` 回调，以及
  `broadcast()` 捕获 `IOException` / `IllegalStateException` 时。全仓**没有任何周期性
  心跳广播**——四处 `broadcast()` 调用全部是事件驱动的
  （`IngestionController:272,365`、`SummaryGenerationService:153`、`AgentService:288`），
  `SSEBroadcaster` 自身也没有 `@Scheduled` 清理。因此**服务端毫无活动时，废弃连接会一直
  留在名单里**，直到 `claudemem.sse.timeout-ms`（默认 1800000ms = **30 分钟**）触发
  `onTimeout`。**100 个「连上就断」的客户端即可让所有新 SSE 客户端在最长 30 分钟内
  持续拿到 500**，而此时后端可能一条事件都没产生过。这正是代理与浏览器普遍掐断空闲
  SSE 连接的场景。
- **Status**: ⏸ **记录不修** —— 把 500 改成 503 属**对外契约变更**（客户端与监控
  都会看到不同状态码），按既定纪律留待项目决策；补心跳则会改变流量形态与
  `SseEmitter` 生命周期，同样需要决策。**两者都已写入本条，后端代码一字未改。**
- **复核记录**（原文见 [`2026-10-03_backend-review-provenance.md`](../archive/2026-10-03_backend-review-provenance.md)，逐轮全文另见 `patrol-rotation.md`）

### P2-25: `maxChars` 的 Swagger 描述承诺了一个后端并不存在的「0 = 默认」分支

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 后端**没有**「0 表示默认」的分支。`MemoryController` 第 154 行写的是
  `int maxChars = request.maxChars() != null ? Math.max(100, request.maxChars()) : 4000;`
  —— 判的是 `!= null`，不是 `> 0`。于是显式传 `0` 会走进 `Math.max(100, 0)`，
  得到 **100**，而非描述承诺的 ~4000。客户端作者照此实现「不传就传 0」的惯例，
  会把注入的 ICL 记忆上下文截到 100 字符，**且没有任何错误提示**（HTTP 200）。
- **Reproduction**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
### P2-26: Go SDK 的 `omitempty` 让 `facts` / `concepts` / `extractedData` 无法清空，且静默返回「updated」

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 后端 `PATCH /api/memory/observations/{id}` 的语义是
  「**字段存在但为 `null` → 清空；`[]` → `setFacts([])` 清空；字段缺失 → 不变**」
  （`MemoryController:346-375` 三处分支都实测确认）。而 Go 的 `omitempty` 对
  **长度为 0 的 slice/map 同样生效**，于是 `Facts: []string{}` 被整个从请求体里丢弃，
  与 `nil` 无法区分 —— **Go SDK 结构上无法表达「清空」**。三个字符串字段是指针，
  `Title: ptr("")` 会作为 `"title": ""` 发出，故不受影响。
  表现分两种：只设 `Facts: []string{}` 时 `Validate()` 判 `IsEmpty()` 为真、报
  「at least one field must be provided for update」——**用户明确要清空却被告知没提供字段**；
  同时设了 `Title` 等其他字段时请求照发，`facts` 被静默省略，服务端回
  `200 {"status":"updated"}` ——**静默无操作 + 假成功**。
- **Reproduction**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
### P2-27: Python SDK 无法清空 `extractedData` —— 与 Go 并列最弱，而它的注释把这一点说成了「对齐 Go」

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 后端 `PATCH` 两种清空写法都能落库（实测 `null` → NULL、`{}` → `{}`），
  而 Python **两种都发不出**：`None` 被 `if val is not None` 跳过、`{}` 被上面那句
  `continue` 跳过；探针确认二者的 `to_wire()` **都是 `{}`**、`is_empty()` **都是 True**，
  故**一条已有 extractedData 的观测无法通过 Python SDK 清空它**。
  活体佐证该字段真实在用：38,200 行中非空 **20,780**、NULL **17,420**、**`{}` 为 0**——
  后端自身从不写 `{}`，走这条路会造出库中从未出现过的状态。
- **四家能力阶梯（清空 extractedData）**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。
- **更正（2026-10-04 第 246 轮，本表 JS 行的判定依据当时不成立）**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。
- **该缺陷为何能存活**: `js-sdk/cortex-mem-js/tsconfig.json` 的 `exclude` 含
  `"**/*.test.ts"`，而 `npm run lint` 就是 `tsc --noEmit`——**测试文件根本不参与类型检查**，
  于是类型层与断言层之间的裂缝没有任何自动关卡。已独立立为 **P2-42**。
- **已修（注释，非行为）**: `ObservationUpdate` 类 docstring 原写
  「Only non-None fields are sent to the backend, **matching Go's
  pointer-field-with-omitempty pattern**」。这句有两处不准：Python 用的是
  `Optional[T]` 而非指针；且**对切片字段两家行为恰恰相反**。已改写为逐条说明四个字段
  上两家的实际异同。`is_empty()` 与 `to_wire()` 里那两处 `continue` 的注释也改为
  如实写明「读取时 `{}` 与 `None` 等价，但**写入时 `{}` 是本 SDK 唯一能发的清空形态**，
  所以这里跳过是真实的能力缺口」，并去掉原来那句会误导的
  「an empty dict is semantically equivalent to None (backend stores nothing in JSONB)」。
  **行为一字未改**：428 测试全过，探针输出与改动前逐字相同。
- **Status**: ⏸ **行为记录不修** —— 改行为只有两条路：让 `{}` 发上 wire
  （**改变现有调用方的可观测行为**，`extracted_data={}` 从「不变」变成「落 `{}`」），
  或新增显式清空入口（**新增公开 API**）。按既定纪律留待项目决策。
  **注释层已先行更正**。
- **复核记录**（原文见 [`2026-10-03_backend-review-provenance.md`](../archive/2026-10-03_backend-review-provenance.md)，逐轮全文另见 `patrol-rotation.md`）

### P2-28: `/api/test/all` 丢弃两个子处理器的状态码，故障时仍返回 200

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 同一份「测试连通性」的语义，两个端点给出**互相矛盾的失败信号**。
  类级 `@Profile("!prod")` 门控是正确的（第 26 行），四家 SDK 也都零调用方，
  暴露面有限；但**任何用 `/all` 做巡检的脚本或监控，在提供方完全不可用时仍会看到
  200**，从而永远不会告警。Swagger 注解（第 116 行）**只声明了 200**，
  与实现一致 —— 也就是说**契约本身就是这样声明的**，问题不在契约与实现不符，
  而在这个契约让该端点失去了作为测试端点的意义。
- **实测记录**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
### P2-29: tool-use 去重键不是一次调用的身份，且未被原子强制

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 去重键是 `(content_session_id, tool_name, SHA-256(tool_input))`，
  判定条件额外要求 `status <> 'failed'`。三处各自独立地削弱了它：

  1. **键里没有 `tool_response`。** 哈希只覆盖 `toolInput`，故「同样的工具、
     同样的入参、结果不同」的调用在前一条仍 `pending`/`processing` 时被**直接丢弃**，
     而调用方只拿到一条 "Duplicate tool-use event skipped" 日志加上
     HTTP `200 {"status":"accepted"}`——**与真正入队完全无法区分**。
     对 fire-and-forget 的 SDK 捕获路径而言，调用方只能得出「已记录」这个错误结论。
  2. **`tool_name` 未规范化。** 它是客户端自由文本，却参与键的比较。实测同一
     session 内 `Read` 与 `read` 携带**完全相同的 input 哈希**
     （`45ff9481fce2…`）时**双双入队**，即大小写不同即可绕过去重。
     不过要如实说明规模：全表按 `(session, lower(tool_name), hash)` 精确分组后，
     大小写孪生组**只有 1 个，且就是本次探针**——**生产数据里从未发生过**。
     真正普遍的是命名本身跨客户端不一致（`Read` / `readFile` / `read`、
     `Edit` / `edit` / `write_file` 同时存在），近 30 天仍有 `readFile` 13 次、
     `write_file` 4 次在流入。
  3. **检查与写入不是原子的，而唯一的兜底约束并不存在。**
     `PendingMessageEntity` 声明了
     `@UniqueConstraint(name = "uk_session_tool_input", columnNames = {...})`，
     但 `application.yml:91` 是 `spring.jpa.hibernate.ddl-auto: none`，
     且**全部 18 个 Flyway 迁移中没有任何一条创建该约束**；活体
     `pg_constraint` 查询确认该表只有 pkey、两个 CHECK 和一个 FK，
     手工插入一条完全相同的三元组**成功**（已回滚）。
     后果是 `AgentService` 里那段
     `catch (DataIntegrityViolationException)`——注释写着
     "Duplicate pending message detected (concurrent insert)"——**是死代码**：
     它等待的那个异常永远不会发生。并发请求于是全部通过检查。
- **实测记录**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Reproduction**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **P2-31 已整体迁出**: 逐字迁入 [`2026-10-04_backend-review-resolved-15.md`](../archive/2026-10-04_backend-review-resolved-15.md)（第 263 轮）。
### P2-32: 两个 Dockerfile 都不设 `SERVER_ADDRESS`，默认部署下服务对外不可达；根镜像的 healthcheck 还写死了端口

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 两条独立缺陷，叠加后的失败形态是**最难排查的那种**：

  1. **默认只绑回环。** `application.yml:3` 是
     `address: ${SERVER_ADDRESS:127.0.0.1}`，而**两个 Dockerfile 都没有
     `ENV SERVER_ADDRESS`**。`docker run -p 37777:37777 …` 的端口映射转发到容器的
     外部网卡，而进程只监听容器内的 `127.0.0.1` —— **映射过去没人接**。
     根 `Dockerfile` 文件头自己给的运行示例就是
     `docker run -p 37777:37777 cortex-ce:latest`，**按默认配置这条命令不通**。
  2. **根镜像的 healthcheck 写死了 `37777`。** 它没有 `ENV SERVER_PORT`，
     `HEALTHCHECK` 却硬编码 `http://localhost:37777/api/health`；
     而 `backend/Dockerfile` 写的是 `${SERVER_PORT}` 并配了 `ENV SERVER_PORT=37777`
     —— **两个 Dockerfile 对同一件事的做法不一致**。
- **Impact**: 第 1 条让容器**健康检查通过、服务却对外不可达**（healthcheck 走的是
  容器内回环，进程确实在听，所以它是绿的）。第 2 条则命中部署指南自己在
  `DEPLOYMENT.md:845` 给出的排障建议——「`Port 37777 already in use` → 改 `SERVER_PORT`」——
  于是 `docker run -e SERVER_PORT=8080` 会让 healthcheck 去探测 37777，
  **把一个完全健康的应用判成 unhealthy**。`docker-compose.yml` 因为显式写了
  `SERVER_ADDRESS: 0.0.0.0` 而**恰好绕过了第 1 条**，所以问题只在裸 `docker run` 路径上暴露。
- **实测记录**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
### P2-33: Go demo 的两个端点名与另外三家 demo 不同

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 四家 demo 暴露 23 个端点，其中 **21 个完全同名**，只有两个例外，
  且**例外全在 Go 这一家**：

  | 操作 | JS demo | Python demo | Go demo |
  |------|---------|-------------|---------|
  | 批量取观测 | `/observations/batch`（`app.ts:339`） | `/observations/batch`（`app.py:430`） | **`/batch-observations`**（`main.go:470`） |
  | 直接创建观测 | `/observations/create`（`app.ts:304`） | `/observations/create`（`app.py:415`） | **`/create-observation`**（`main.go:771`） |

  Go demo 的 README（如实记录了自己的路径）与 `scripts/go-sdk-e2e-test.sh`
  （第 567 行确实调 `/batch-observations`）**都与代码一致**——**这不是文档错误，
  而是四家 demo 之间的契约分歧**：照着 JS 或 Python demo 的 curl 抄一遍，
  打到 Go demo 上会得到 **404**。
- **Status**: ⏸ **记录不修** —— 改路由名会同时打断 `scripts/go-sdk-e2e-test.sh`
  与该 demo README 里已发布的示例，属**跨 demo 契约决策**，按既定纪律
  （沿用第 229 轮「四家统一上界与否」的同一判断）留待项目决策。
  **文档层已先行补充**：Go demo 两份 README 现明写这两个端点的**命名与另外三家不同**。
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
### P2-34: `GET /api/logs` 的 Swagger 示例漏掉 `files`，且把绝对路径写成 `/logs`

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 实现用 `Map.of(...)` 返回 **6** 个键
  —— `logs` / `path` / **`files`** / `totalLines` / `returnedLines` / `exists`，
  而注解的示例只有 **5** 个，**漏掉 `files`**；且示例写 `"path":"/logs"`，
  实际返回的是**绝对路径**（本机实测 `/Users/yangjiefeng/.claude-mem/logs`）。
  `/v3/api-docs` 是生成客户端代码的来源，所以这个缺失会传播到任何按 OpenAPI
  生成的 SDK 模型里。
- **实测记录**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
### P2-35: `CortexToolAspect` 结构上无法捕获失败的 `@Tool` 调用，而质量模型恰恰以失败为一档

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 工具方法抛异常时，异常从第 60 行直接向上传播，**捕获整段被跳过**，
  调用方拿到的仍是原始异常（这一点是对的），但**这次工具调用在记忆里不留任何痕迹**。
  **关键在于这与后端的设计意图相反**：`QualityScorer` 明确有
  `FAILURE_BASE = 0.20f` 与 `FeedbackType.FAILURE`（第 24-26、59-61 行），
  即**整个 Evo-Memory 质量模型就是围绕「区分成功与失败」建立的**——
  而这条自动捕获路径**一条 FAILURE 都产不出来**。
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Status**: ⏸ **记录不修** —— 修它会让**所有用户的库里开始出现新的失败观测**，
  改变已存储的数据形态，属**产品决策**而非纯 bug 修复（沿用 P2-24「接入属新增特性
  而非修 bug」的同一判断）。修法：把 `proceed()` 包进 try，catch 后**先记录再重抛**
  （捕获本身已 fire-and-forget，不会掩盖原始异常），并补一条「工具抛异常时仍被捕获」
  的测试。**SDK 代码一字未改。**
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
### P2-36: 三个同级适配器（eino / genkit / langchaingo）对数值选项的校验互不一致，且 genkit 的兜底只护住了 per-call 路径

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 这三个文件是同一个 SDK 里为同一目的写的三块适配层，**却对「非正数怎么办」
  给出三种不同答案**：genkit 有 `if count <= 0 { count = r.count }` 兜底、eino 与
  langchaingo **完全没有校验**。更关键的是 **genkit 的兜底本身是半截的**——它只作用于
  `Retrieve` 收到的**每次调用**的 `input.Count`，而兜底的落点 `r.count` **从未被校验**；
  于是构造函数传入负数时，兜底「回退」到的正是那个负数，**原样发上 wire**。
  测试名 `TestRetrieve_NegativeCount_FallsBackToDefault` 读起来像「负数已被处理」，
  但它把**构造函数传的是合法值 3**、只测 per-call 分支——**真正漏的那条路径无覆盖**。
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **附带一处被丢弃的透明信号**: 后端把**实际生效值**回显在 `ICLPromptResult.maxChars`，
  Go 的 `dto.ICLPromptResult.MaxChars` **确实有这个字段**（`dto/experience.go:46`），
  但全 SDK **无任何非测试代码读它**——`LoadMemoryVariables` 只取 `result.Prompt`。
  即：直接用核心客户端的调用方**能**看到钳制，**走适配器的调用方看不到**。
- **Status**: ⏸ **记录不修** —— 与 P2-30 同族但**不是同一条**：P2-30 记的是核心客户端
  `limit` 的四家分歧，本条记的是**适配层** `count` / `maxChars` 的**块内分歧**。不修的理由：
  ①「负数该等于什么」**没有唯一正确答案**（0 条？不限？回退默认？三个作者都没写），
  单方面选一个就是替项目做产品决策；②只修 genkit 会让 SDK **看起来更不一致**
  （一个有兜底、两个没有），而修 langchaingo 的「回显被丢弃」半边**必然要新增可观测行为**
  （多一行日志或一个新错误），属新增特性。**本轮只补了三个选项注释里的取值范围事实说明**
  （照「文档描述现在而非该有的行为」），**未改任何运行时行为**。
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
### P2-37: 四家 demo 的 `/chat` 在方法、输入位置、响应结构与语义上全部分歧——而这个分歧被 Java demo 自己的 Javadoc 写明后搁置

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 四家 demo 的端点**名字**经第 235 轮集合对拍已确认 23 个里 21 个同名，
  但**方法这一层从未被比对过**。补上后 `/chat` 暴露出四路分歧：

  | Demo | 方法 | 入参位置 | 响应 | 实质 |
  |------|------|----------|------|------|
  | Go | POST（`checkMethod` 强制） | JSON body | `{response, project, timestamp, memoryContext?, experienceCount?}` | 回显 `Received: …`，**不记录** |
  | Python | POST | JSON body | 同上 | 回显，**不记录** |
  | JS | POST | JSON body | 同上 | 回显，**不记录** |
  | **Java** | **GET** | **查询参数** `?message&project&conversationId&useTools` | `{response, project, conversation_id}`，**无 `timestamp`、无 `memoryContext`** | **真实调用 LLM**，经 `CortexMemoryAdvisor` **自动捕获** |

  即四家共用一个端点名，却在**方法、输入载体、响应结构、行为语义**四个维度上各不相同。
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **分歧是「已知且被写下」的**：`ChatController` 自己的 Javadoc 明写
  「The Go, Python and JS demos all answer `POST /chat` with a JSON object」，
  **紧接着就改用 `@GetMapping`**——写下了差异却没有解决。
- **Status**: ⏸ **记录不修** —— 给 Java demo 增加 `POST` 映射属**公开端点契约变更**；
  而「Java demo 的 `/chat` 究竟该是真实 LLM 调用，还是与另三家对齐为薄回显」
  属**demo 定位的产品决策**。**文档层已先行更正**：Go demo README 原先写
  「照抄任一家其余 21 个端点的 curl **只会在这两个上 404**」——**过度承诺**，
  已改为区分「拼写一致」与「可互换」，并补上 `/chat` 的方法分歧与 405 实测输出。
  **四份 demo README 各自对自身 demo 的描述经核实均准确，未改。Demo 代码一字未改。**
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
<!-- P2-38 已无条件解决，逐字迁入 2026-10-04_backend-review-resolved-3.md -->
### P2-39: `POST /api/import` 的外层 `@Transactional` 与逐行 catch 相撞——一行坏数据毁掉整批，逐行统计变成 500

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 该端点**专门收集逐行错误**（`stats.addError(result.message())`）并在响应里
  返回 `imported / skipped / errors` 统计——**但这层设计被事务语义彻底击穿**：
  1. `importSession` 是 `@Transactional`（默认 REQUIRED），**并入** `bulkImport` 的同一个事务；
  2. 行数据触发数据库异常（如 `content_session_id` 超 255 字符）时，异常穿出 `importSession`，
     Spring 的事务拦截器把**共享事务标记为 rollback-only**；
  3. 控制器第 2 层 `catch (Exception e)` **吞掉**该异常并继续循环、继续统计；
  4. 方法返回时提交，Spring 抛 **`UnexpectedRollbackException`（"Transaction silently rolled back"）**
     → 调用方拿到 **HTTP 500**，**逐行统计一个都没送到**，**整批合法行全部回滚丢失**。
  即：端点为「部分成功」设计的响应结构，在最需要它的场景下**完全不起作用**。
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **同一类问题的既有痕迹**：`ImportService.importSession` 第 233-236 行已有一段注释，
  记录过 `project_path` 缺失导致「save 在提交时才失败、调用方只看到
  `Could not commit JPA transaction`」并为此**补了前置校验**。也就是说**这个坑已被踩过一次、
  修过其中一个字段**，而 `content_session_id` 与 `status` 的 `varchar` 宽度**至今未校验**，
  外层事务的 rollback-only 语义**也从未被处理**。
- **附带一处次要观察（不单独立项）**：wire 格式是 **snake_case**
  （`spring.jackson.property-naming-strategy: SNAKE_CASE`），传 camelCase 的
  `contentSessionId` 会得到错误信息 **`"contentSessionId is required"`**——
  该信息**报的是 Java 字段名而非用户实际发来的 wire 字段名**，具有误导性。
  且 `API.md` 对 `/api/import/sessions`、`/summaries`、`/prompts` **只有一句
  「Request body: Array of session objects」，没有任何字段清单或示例**，
  用户无从得知该用 snake_case。
- **Status**: ⏸ **记录不修** —— 两种修法各改一项**已成文的对外契约**：
  ①去掉 `bulkImport` 的 `@Transactional`（与另外四个同族端点一致）→ 放弃
  `@Operation` 明写的 "in a single **atomic** transaction"；
  ②保留原子性但不再吞掉 rollback-only（重新抛出）→ 调用方仍拿不到逐行统计，
  只是从「假 500」变成「真 500」。**真正的修法需要先决定这个端点到底承诺
  「全有或全无」还是「逐行部分成功」——那是产品契约决策**。
  只补 `varchar` 宽度校验**不足以解决**：它只覆盖最常见的一种触发方式，
  而任何未来的数据库异常仍会重演整批丢失，且会让人误以为问题已解决。**后端代码一字未改。**

### P2-40: 四家 SDK 都能写入 prompts 与 summaries，却没有一家读得回来

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 这**不是缺陷而是能力缺口**，但它没有被任何一处写下来，容易被当成疏漏。
  两个端点都**活体可用**、都在 `API.md` 里有完整记载（`/api/prompts` 出现 8 处）、
  WebUI 都在用；而**四家 SDK 没有任何方法能调用它们，四家 demo 也都没有暴露对应端点**
  （三家 demo 里唯一的 "summar" 字样是统计字段 `totalSummaries`，不是端点）。
  与之形成鲜明对比的是**写的一侧齐备**：`POST /api/ingest/session-end`（会话结束即生成摘要）
  与 `POST /api/ingest/user-prompt` 四家**全部**有方法（Go/Python/JS/Java 的
  session-end 与 user-prompt 引用数分别为 3/3、4/3、6/6、2/2）。
  即：**SDK 用户可以产生摘要与提示词，却永远无法把它们读回来**——想读只能自己发 HTTP。
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **一处探针自身出错并先识别再采信**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。
- **Status**: ⏸ **记录不修** —— 补一个方法是**新增公开 API**，按既定纪律
  「新增公开 API 留待项目决策、不单方面实施」。且这不是「某一家漏了」的缺陷：
  **四家完全一致地缺失**，因此它要么是有意的范围划定、要么是共同的疏漏，
  两种解读都指向需要项目层面拍板而非某轮自行补齐。
  **若将来实施**，需注意与既有分页约定对齐：这两个端点与 `/api/observations` 共用
  `Math.min(Math.max(1, limit), MAX_PAGE_SIZE)` 的钳制（`API.md` 已记载），
  且 `hasMore` 是**驼峰**而条目内字段是 **snake_case**——Go 的 DTO 已按此混合约定建模
  （`dto/observations.go:28` 有 ⚠️ 注记），新方法应复用同一约定。**四家 SDK 代码一字未改。**

### P2-41: `platform_source` 等四个字段后端每条观测都在返回、WebUI 也在按它过滤——而四家 SDK 既不暴露、也不接受过滤

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 与 P2-40 同族的能力缺口，但这次卡在**字段**层面而非端点层面。
  后端**每条观测都返回** `platform_source`（V18 为多平台追踪新增）、`content_hash`
  （V8 新增、P2-29 的去重键组成部分）、`relevance_count`（V17 反馈）与 `step_number`；
  `API.md` **把 `platformSource` 作为查询过滤器写进了文档**（4 处）。
  而**四家 SDK 无一在响应 DTO 上暴露这些字段，也无一在请求侧接受 `platformSource` 过滤**。
  实际后果很具体：**SDK 用户无法区分一条观测来自 Claude 还是 Codex/OpenClaw**，
  也无法按平台筛选——而这正是 V18 加这个字段的目的。WebUI 侧的
  `viewer-bundle.js` 已经在按 `platform_source` 过滤，所以「能用」只在浏览器里成立。
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **一处探针自身出错并先识别再采信**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。
- **Status**: ⏸ **记录不修** —— 与 P2-40 同一判断：补字段属**新增公开 API**，
  且**四家完全一致地缺失**，说明要么是有意的范围划定、要么是共同疏漏，
  都需要项目层面拍板而非某轮单方面扩大某一家的 DTO。
  **若将来实施**，注意 `platform_source` 已经是列表端点的**过滤维度**而非纯展示字段，
  补齐时应同时覆盖**响应字段**与**请求过滤参数**两侧，否则只补一半仍然无法按平台检索。
  **四家 SDK 代码一字未改。**

### P2-42: JS SDK 的 `tsconfig.json` 把测试文件排除在类型检查之外——`npm run lint` 查不到测试里的任何类型错误

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 这两项叠加的结果是**整个测试套件从不参与类型检查**。
  `include` 是 `src/**/*`（测试文件确实在里面），但 `exclude` 又把它们摘了出去，
  于是 `tsc` 只检查 `src` 下的非测试源码。**活体证据**：往
  `src/__tests__/client.test.ts` 里注入 `const __bad: string = 42;`，
  `npm run lint` **依然退出 0、无任何输出**。
  后果是类型层与断言层之间可以长期存在裂缝而没有任何自动关卡——
  第 246 轮修的那个 P2-27 能力表错判正是这样活下来的：
  `ObservationUpdate` 声明为 `title?: string`，而**本 SDK 自己的测试**
  （名为 "should accept null fields for PATCH clear semantics" 的用例）
  必须写 `null as unknown as string` 才能表达它声称在测的能力，**却一直全绿**。
- **Status**: ⏸ **记录不修** —— 修法是加一道 `tsconfig.test.json`（`extends` 主配置、
  覆盖 `exclude`）并并入 `lint`。这属**构建配置变更**，且一旦接上就会一次性暴露
  三个测试文件（含 `examples/http-server/parse-int-param.test.ts`）里既有的潜在类型错误，
  影响面超出单轮范围，留待项目决策。
  **注意**：本条**不影响**第 246 轮的修复——`ObservationUpdate` 是 `src/dto/` 下的
  导出源码，**在检查范围内**，`tsc --noEmit` 对它的类型改动有把关；
  受影响的只是「测试文件本身写错类型不会被发现」这一层。
  故第 246 轮的类型层验证改用**直接对 `src/dto/observation.ts` 的探针文件**做双向注入
  （修复后 0 error / 回退后恰好 8 个 / 恢复后 0），而不是依赖 `npm test`。

### P2-43: Python SDK 的两种调用风格对 `None` 的含义相反——dataclass 路径丢弃它、kwargs 路径原样发上 wire

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 同一个 `None` 在两条路径上语义不同，且**只有 kwargs 那条符合 PATCH 的清空语义**。
  - dataclass 路径：`ObservationUpdate(title=None).to_wire()` → **`{}`**（`if val is not None` 跳过），
    `is_empty()` → **True**，于是纯清空请求被当成「空更新」而**根本发不出去**。
  - kwargs 路径：`update_observation(id, title=None)` 里是
    `for kwarg, wire_key in ObservationUpdate._WIRE_FIELDS.items(): if kwarg in kwargs: body[wire_key] = kwargs[kwarg]`
    ——**原样拷贝、不做 None 过滤**，于是 `body = {"title": None}`，
    `if not body` 为假（非空字典），**`{"title": null}` 真的上了 wire 并清空了字段**。
  - 探针实证：`ObservationUpdate(title=None)` → `to_wire() == {}`、`is_empty() == True`；
    而经 demo 活体 `PATCH {"title": null}` → **200** 且 `mem_observations.title` 确实变 NULL。
  - **对 P2-27 的影响**：该条能力表把 Python 判为「✗（`if val is not None`）」——
    这对 dataclass 路径成立，**对 kwargs 路径不成立**。按「按断言清扫」的标准，
    那行只覆盖了一半的调用面，应当标注。
- **Status**: ⏸ **记录不修** —— 两种收法都改变现有调用方的可观测行为：
  ①让 dataclass 路径也发 `None`（原本静默不发请求的 `ObservationUpdate(title=None)` 会突然发出一次 PATCH）；
  ②让 kwargs 路径也过滤 `None`（**本条是真缺陷**：今天经 kwargs 清空是能成功的，
  改掉等于让已经能用的能力失效，且 Python demo 的 PATCH 正是走 kwargs 路径）。
  真正的修法要先决定**哪种写法才是 SDK 想支持的清空方式**，属 API 契约决策。
  **但 P2-27 的能力表应当立即标注 kwargs 路径**——那是文档层的事实更正，已在下方补注。

### P2-44: `CortexSessionContextBridgeAdvisor` 与手动 `begin/end` 不能嵌套——外层作用域会被**静默**销毁

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `CortexSessionContext.begin()` 是裸的 `CURRENT.set(new SessionInfo(...))`、
  `end()` 是裸的 `CURRENT.remove()`——**既无重入保护、也不保存/恢复**。
  advisor 每见到 `CONVERSATION_ID` 就无条件 `begin`，并在 `finally` 里 `end`。
  于是**外层已存在的作用域被覆盖、并在调用返回后被删除**。
  **探针实测**（`CortexSessionContextBridgeAdvisorTest` 旁的一次性用例，未提交）：
  在 `begin("outer-session", "/outer/project")` 已激活时调一次 `adviseCall`，前后状态为 | 时点 | `isActive()` | `getSessionId()` |
    `getProjectPath()` | |---|---|---|---| | 调用前 | `true` | `outer-session` | `/outer/project` | | **调用后** | **`false`** |
    **`unknown-session`** | **（空串）** |
  断言「外层应当存活」**失败**，即缺陷成立。**全程无异常、无告警**——
  此后同一外层作用域里的任何 `@Tool` 调用都会以 `unknown-session` 与空项目路径入库。
  调用**内部**看到的是 advisor 自己的上下文（`/advisor/project|conv-inner`），
  即内层正确、**外层被毁**。
- **为什么不是示例代码的 bug**: `ChatController` 刻意把两条路径二分——
  带 `conversationId` 的请求走 bridge 且**不**手动 `begin`；不带的手动 `begin`，
  而 bridge 因无 `CONVERSATION_ID` 直接透传。**从不嵌套**。故这是**误用场景**，
  而非已交付代码里的活 bug。
- **Status**: ⏸ **记录不修** —— 两种收法都改变现有调用方的可观测行为：
  ①在 `CortexSessionContext` 上加保存/恢复（需要把 private 的 `SessionInfo` 暴露为公开类型，
  属**新增公开 API**）；②已激活时跳过 `begin/end`、让外层胜出（零新增 API，
  但当内外 session 不同时，内层会被记到**外层**的会话上，属跨会话串号）。
  真正的修法要先决定**两者冲突时谁该赢**，属产品决策。
  **已做的零风险部分**：在 `CortexSessionContextBridgeAdvisor` 的类 Javadoc 中
  写明「不可嵌套」这一约束、外层被静默销毁的实测后果、以及 demo 为何二分——
  **纯注释，行为一字未改**。现有 6 个该 advisor 的测试无一覆盖嵌套。

### P2-45: 会话启动的 `projects` 字段能生成多项目上下文、API.md 也写了——而四家 SDK 一律发不出去

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 后端的会话启动契约有 **7** 个字段
  （`session_id` / `project_path` / `cwd` / `user_id` / `projects` / `is_worktree` / `parent_project`），
  **四家 SDK 一律只暴露 3 个**（`session_id`、`project_path`、`user_id`）：
  Go 是 `dto.SessionStartRequest` 结构体，Java 是同名 record，JS 是同名 interface，
  Python 是 `start_session(session_id, project_path, user_id=None)` 三个位置参数。
  **其中 `projects` 是真实生效的能力**——`SessionController` 在它含逗号时走
  `parseProjectsParam` 并生成**多项目上下文**。**活体实测（同一 `project_path`，只差 `projects`）**：
  不带时返回 `"# phase3-acceptance-test — no memories yet"`，
  带上 `"projects":"openclaw,/tmp/phase3-acceptance-test"` 后返回
  `"# openclaw recent context … 📊 25 observations | 📖 6,109 read tokens"`。
  **后果**：SDK 用户永远拿不到多项目上下文，只能自己发 HTTP。
  `API.md` 的 `/api/session/start` 字段表**完整记载**了 `projects`（"Multi-project support,
  comma-separated"），所以这不是未公开特性。
- **Status**: ⏸ **记录不修** —— 补字段是**新增公开 API**，且**四家完全一致地缺失**，
  与 P2-40 / P2-41 同理：要么是有意的范围划定、要么是共同疏漏，都指向项目层面拍板。
  **若将来实施**，注意四个模型的形态各不相同（Go/Java/JS 是对象字段、Python 是位置参数），
  需一并考虑向后兼容。
- **同区域另两处事实（均记录，留待各自轮次处理）**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。

### P2-46: 验收脚本的 `cleanup()` **定义了却从未被调用**——其幻影端点从未生效，而 Test 6 的前提因此早已不成立

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 这条比一般的文档错误重要，因为它**削弱的是我自己每轮据以判断的验收门控**。
  两处缺陷叠加：
  ① `cleanup()` 在全文件**只出现一次**（定义处）——`grep -nE "cleanup|trap|EXIT"` 只有第 45 行，
  `main` 里没有调用，也没有 `trap ... EXIT`，`bash -n` 通过。它是死代码。
  ② 它内部那行清理请求本身也不成立：`DELETE /api/memory/observations?project_path=...`
  **活体 404**。活体 OpenAPI 里该前缀下**只有 `/api/memory/observations/{id}` 一条路径**
  （`patch` 与 `delete`），**没有任何按 `project_path` 批量删除的端点**；
  `/api/observations`（GET 列表）才是脚本真正该用的。or-true 兜底把 404 吞掉，
  因此这个失败**永远不会让脚本失败**。
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Status**: ⏸ **记录不修** —— 属脚本方向，不在本轮（Python SDK）的代码轮换内；
  且**若真把清理接上，Test 6 会切回 `not_found` 分支、累积数据会被删除**，
  属于会改变门控自身行为的改动，需在自己的轮次里单独做 A/B。
- **对既有结论的影响（必须如实记录）**: 第 249–251 轮的「EXTRACTION 25/0/0 全通过」
  **仍是 25 条全部通过**，但 **Test 6 走的是兜底分支**、**Test 14 的断言已因数据累积而恒真**。
  这不使任何一条已记录的修复失效（被修代码路径本就在别处被独立验证），
  但今后引用该数字须带上这两条限定（基线区块已写明）。
- **同族事实（已修）**: 同一幻影端点也出现在**设计文档** `phase-3-design/25.md:699`
  （`demo-v15-extraction-test.sh` 的 Cleanup 段），已改为脚本真正使用的
  「先 `GET /api/observations` 取 id、再逐条 `DELETE /api/memory/observations/{id}`」，
  并实跑验证（观测数 1 → 0）。该脚本本身**行为正确**（`cleanup_test_data` 测试前后各调一次，
  `limit=100` 恰等于 `Constants.MAX_PAGE_SIZE`，不截断），**只有验收脚本是坏的**。

### P2-47: `API.md` 双语把 `is_worktree` / `parent_project` 当正式字段记载并写进示例 body——而两者只进一条 `log.info`

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 文档描述的是**尚未实现的能力**。逐行核实（第 251 轮）：
  `SessionController.java:126-127` 读出 `isWorktree` / `parentProject`，
  两者**此后只出现在 142–144 的 `log.info` 里**；真正建会话的
  `initializeSession(contentSessionId, projectPath, null)`（`:152`）第三参传的是 `null`，
  **worktree 信息不落库、不参与任何逻辑**。
  而本该让该能力真正生效的 `WorktreeDetector` 服务，
  `grep -rn "WorktreeDetector" backend/src/` **除自身文件外零命中**——**零调用者**。
  但 `API.md` 两版都用正式字段表条目（"Whether this is a worktree" /
  "Parent project name (worktree mode)"）记载，并写进示例 body。
  **读者据此会以为传了就有用。**
- **Status**: ⏸ **实现侧记录不修**；**文档侧已于第 254 轮更正**（API 文档方向）。
  改文档而非接上 `WorktreeDetector`，是因为后者属新增行为、同样需项目拍板。
  第 254 轮按「**按断言清扫而非按文件**」把同一断言的**全部**表述处一并更正：
  `API.md` 与 `API-zh-CN.md` 的字段表加注「只被接收并记入日志、不落库、不影响行为」，
  `SessionController` 的请求示例 Javadoc、`@Operation` 描述，
  以及 **`ApiRequests.SessionStartRequest` 上的三个 `@Schema`**——
  **后者才是真正对外的那一份**，活体 `/v3/api-docs` 原本就在输出
  「Flag indicating worktree mode (rare, internal use)」
  与「Parent project path for worktree (rare, internal use)」。
  **三处改动均为描述文本，字段名、类型、状态码、行为一律未变。**

### P2-49: `scripts/start.sh` 把后端 jar 的版本号钉死——而它是 TESTING.md 推荐的启动方式

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 与第 248 轮修掉的「jar 名写错 artifactId」**不是同一类**——那批名字从来不可能产出，
  这批**名字是对的、只把版本钉死了**。今天与磁盘一致，**版本号一变全线失效**，
  且失效方式不同：`start.sh:97` 直接 `Missing $JAR_PATH; rerun with --build` **拒绝启动**。
  而 `docs/TESTING.md:212` 把 `scripts/start.sh` 列为**推荐**启动方式，
  **一次版本变更就让文档推荐的启动路径不可用**。四个脚本 + 两份 `evo-memory-implementation*.md`
  共 6 处（不含已修的 DEVELOPMENT.md 4 处）；当前 jar 存在故**今天不可复现**——
  这是**由版本变更触发的潜伏缺陷**，不是当前故障。
- **Status**: ⏸ **记录不修** —— 属脚本方向，不在 Backend 轮换内。修法直接
  （`JAR_PATH` 改 glob 取首个匹配，或用 `./mvnw spring-boot:run`），但需连带
  `start-all.sh` 的启动顺序与 `.env` 加载一起看，并在真实版本变更下验证一次。

### P2-50: 读 Cursor 注册表失败被当成「空注册表」，而这个空结果**会被写回**
- 已解决条目：正文逐字迁入 [`2026-10-04_backend-review-resolved-13.md`](../archive/2026-10-04_backend-review-resolved-13.md)（第 260 轮；**无条件已解决、无待决问题**，故按第 250 轮先例整体迁出）。
### P2-51: `projects` 只在**值里含逗号**时才生效——传单个值被静默忽略，与不传完全等价

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 判定是 `projectsParam.contains(",")`——**只有含逗号才走多项目分支**。
  传单个值会落进单项目分支、**该值被完全丢弃**，既不报错也不告警，
  返回结果与**根本不传 `projects` 逐字相同**；文档只写「comma-separated」，
  **没说单个值等于不传**。另有 `@Schema` 写 project **paths** 而
  `parseProjectsParam` 的 Javadoc 写 project **names**，对同一值给出两种定义。
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Status**: ⏸ **记录不修** —— 改判定会**改变现有调用方的行为**（原本按 `project_path`
  生成、改后按该值生成），属语义变更，需项目拍板。
  **文档侧已于第 254 轮更正**：`@Schema` 与 API.md / 中文版字段说明均已写明
  「仅当含逗号时生效，单个值会被静默忽略」，并把 paths/names 统一为「project identifiers」。
  另一种修法是**单值时回落到按该值生成**（更符合直觉），同样需决策。


### P2-52: `target/` 里残留 10 个**源码已删**的测试类——其中一个仍在失败，使 `mvn test` 退出非零
- 已解决条目：正文逐字迁入 [`2026-10-04_backend-review-resolved-13.md`](../archive/2026-10-04_backend-review-resolved-13.md)（第 260 轮；**无条件已解决、无待决问题**，故按第 250 轮先例整体迁出）。
### P2-53: Go SDK 的 `WithTimeout` 把「太小的值」重置成**默认最大值**——请求 50ms 实际得到 30s
- 已解决条目：正文逐字迁入 [`2026-10-04_backend-review-resolved-13.md`](../archive/2026-10-04_backend-review-resolved-13.md)（第 260 轮；**无条件已解决、无待决问题**，故按第 250 轮先例整体迁出）。
### P2-54: Python SDK 另有两处裸 TypeError——且既有测试的 docstring 早已写明我踩的那个坑
- 已解决条目：正文逐字迁入 [`2026-10-04_backend-review-resolved-13.md`](../archive/2026-10-04_backend-review-resolved-13.md)（第 260 轮；**无条件已解决、无待决问题**，故按第 250 轮先例整体迁出）。
### P2-55: 四个 demo 为同一件事立了同一份文法契约，却 2:2 分裂——而且**与后端一致的那两家是「碰巧」一致的**

- **Scope**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Problem**: 四个 demo 各自有一段整数解析，文法被写成了同一句话——
  「optional sign, then digits **only** —— no hex, no exponent, no trailing garbage」，
  枚举的例子（`0x10` / `1e3` / `1.5` / `10abc` / `1_0`）**全是 ASCII**。
  Java 侧那句 Javadoc 还额外断言「**The rule implemented here is the one the other three
  demos already agreed on**」，Python 侧写着「A regex pins the grammar ... so all four
  demos match the backend」。**但对同一个输入，四家给出两种答案：**
  `Character::isDigit` 与 Python 的 `\d` 都是**Unicode 感知**的，
  `strconv.Atoi` 与 **JS 的 `\d`（规范定义即 `[0-9]`）是纯 ASCII**。
  这是这条不变式的**第二次**被打破（第 211 轮修过 `?limit=%20` 一族，
  第 252 轮又发现 Python 不校验 `limit` 范围）。
- **Evidence**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **不修的理由**: 修哪一边都是**改动 HTTP 对外契约**，而方向无法由证据确定——
  若收紧 Python + Java，就与它们声称要对齐的后端**背离**；
  若放宽 Go + JS，等于正式认可后端这个由 `Integer.decode` 带来的**意外行为**为契约。
  二者都属「对外契约变更」，按既定规则**记录不单方面实施**。
  实际影响低（同形字符数字是很不可能的输入），但**这条不变式正是四个 demo 存在的理由**，
  故必须留档。**修之前需要一次产品决定**：整数文法是否只认 ASCII。
- **Status**: ⏸ 记录不修（待产品决定：整数文法是否仅限 ASCII 数字）。本轮已把四条代码路径与活体后端全部实测完毕，无需再取证。

### P2-56: Java demo 里四个控制器有三个用了共享校验类，第四个把两个数值参数整个绕过去了——**而那个类的 Javadoc 宣称自己覆盖了所有控制器**

- **Scope**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Problem**: `DemoParams` 的 Javadoc 原本写着「Controllers take the raw `String` and call
  `boundedInt` rather than declaring an `Integer` parameter, **so this rule is the only thing
  that can decide what a value means**」。**这句话对 `ExperiencesController` 是假的**：
  `boundedInt` 的调用点只落在 `SearchController` / `ObservationsController` / `ExtractionController`
  三个文件里，而 `ExperiencesController` 把 `count` 与 `maxChars` **直接绑成 `Integer`**，
  再在方法体里手写 `count < 0 || count > 100` / `maxChars < 0`。
  后果是**同一个进程内部出现两种 400**：`InvalidParamAdvice` 只匹配 `InvalidParam`，
- **Evidence**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Status**: ⏸ 记录不修（Javadoc 已按现状更正；实现待 P2-55 的文法决定）。

### P2-57: Java SDK 的默认 base URL 是四家里唯一用主机名的——而后端**只绑 IPv4 回环**，一个 JVM 开关就能把它变成连不上

- **Scope**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Problem**: 四家 SDK 的默认端点**本应一致**，实测却是 **3:1** 而非对称：

  | SDK | 默认 base URL | 位置 |
  |-----|---------------|------|
  | **Java** | **`http://localhost:37777`** | `CortexMemProperties.java:12`（字段初始值） |
  | Python | `http://127.0.0.1:37777` | `client.py:72`（签名默认值） |
  | Go | `http://127.0.0.1:37777` | `client_impl.go:102` / `:119` |
  | JS | `http://127.0.0.1:37777` | `client-options.ts:68` |

  **Java 是唯一的异类**。而**后端自己**在 `application.yml:3` 写的是
  `address: ${SERVER_ADDRESS:127.0.0.1}`——**只监听 IPv4 回环**。
  本机 `lsof` 亦确认监听项为 `TCP 127.0.0.1:37777`，
  直连 `[::1]:37777` **连接失败**。
  问题在于 `localhost` 是**要解析的主机名**，本机解析顺序实测为
  **`::1` 在前、`127.0.0.1` 在后**。今天能通，**只是因为 HTTP 客户端做了地址族回退**。
- **Evidence**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **不修的理由**: 改一行即可（把默认值换成 `127.0.0.1:37777`），且从证据看方向明确：
  它会让 Java 与另三家及后端自身的 `server.address` 一致，
  且在任何「当前默认值可用」的环境里新默认值同样可用。
  **但它改的是已发布 SDK 的公开默认端点**——唯一会被它影响到的情形，
  是某台机器上 `localhost` 与 `127.0.0.1` 指向**不同的后端**（那本身已是矛盾配置）。
  即便风险极小，它仍属**对外契约变更**，按既定规则**记录不单方面实施**，留待项目拍板。
- **同区域一处文档不一致（记录，未改）**: `python-sdk/cortex-mem-python/cortex_mem/client.py:47`
  的 **Javadoc 示例**写 `CortexMemClient(base_url="http://localhost:37777")`，
  与**该文件第 72 行的真实默认值 `http://127.0.0.1:37777` 矛盾**——
  **示例教用户写的值，与实际默认值不是同一个**。属零行为变化的描述修正，
  但落在 Python SDK 方向，不在本轮（Java SDK），故留待该方向按断言清扫。
  **我在本轮第一次扫这一族时也踩了同一个坑**：grep 命中的是第 47 行的文档示例而非第 72 行的默认值，
  一度得出「Java 与 Python 是 2:2 分裂」的错误结论——**改用排除注释的探针后才看清真实的 3:1**。
- **Status**: ⏸ 记录不修（改公开默认端点属对外契约变更；证据与建议方向已齐备，修复只需一行）。

### P2-58: 四家 SDK 的响应 DTO **同缺**活体观测的 7 个字段——其中 3 个正是 V17 / V18 专门加的，而 Go 的 DTO 在 V17/V18 之后**还被改过**

- **Scope**: 逐字迁入 [`2026-10-04_backend-review-evidence-14.md`](../archive/2026-10-04_backend-review-evidence-14.md)（第 263 轮）。
- **Problem**: 取活体 `GET /api/observations?limit=1` 的一条真实观测（**34 个字段**），
  与四家响应 DTO 声明的字段名逐一比对，**四家同缺同样这 7 个**：

  | 字段 | 来自迁移 | 性质 |
  |------|----------|------|
  | `platform_source` | **V18** `V18__add_platform_source.sql` | **V18 专门新增**（平台来源归属） |
  | `generated_by_model` | **V17** `V17__observation_feedback.sql` | V17 反馈机制 |
  | `relevance_count` | **V17** 同上 | V17 反馈机制 |
  | `content_hash` | V8 | 内部去重列 |
  | `step_number` | V12 | 步骤效率 |
  | `discovery_tokens` | V1 | 统计列 |
  | `embedding_model_id` | V2 | 内部向量元数据 |

  Go 与 JavaScript 的 `encoding/json` / Jackson **默认忽略未知字段**，
  所以这些字段**被服务端发过来、被 SDK 静默丢弃**——不报错、不告警，
  调用方只能看到「SDK 里没这个字段」。
- **Evidence**: 逐字迁入 [`2026-10-04_backend-review-evidence-14.md`](../archive/2026-10-04_backend-review-evidence-14.md)（第 263 轮）。
- **不修的理由**: ①**跨四家**，不属于任何一个方向的轮次；
  ②这 7 个里**性质不同**——`platform_source` 与 V17 两项是**面向使用方的能力**
  （V18 的存在意义就是让调用方知道一条记忆来自哪个平台），
  而 `content_hash` / `embedding_model_id` 很可能与三个向量列一样属**内部列、本就不该暴露**；
  ③**该暴露哪一部分无法由证据确定**。按既定规则**记录不单方面实施**。
- **Status**: ⏸ 记录不修（跨家 + 暴露范围待定；证据与字段来源已逐条落到迁移文件）。

### P2-59: Java demo 十个控制器把后端 4xx 变成 500，**其中两个方向相反**——凭空造 404，和把 404 放大成 500

- **Scope**: 逐字迁入 [`2026-10-04_backend-review-evidence-15.md`](../archive/2026-10-04_backend-review-evidence-15.md)（第 265 轮）。
- **Problem**: 四家 demo 在本机同时起（Java 37778、Go 37779、Python 37780、JS 37781），
  对**同一个请求**打同一句话，结果是**两个相反方向**的分裂：
  ①**放大**——后端 `PATCH /api/session/{sessionId}/user` 对未知 session 返 **404**
  `{"error":"Session not found: no-such-session-xyz-263"}`；
  Python / Go / JS 三个 demo **原样透传 404**，Java demo 返 **500**，
  且 body 是 `{"error":"Failed to update session user: 404 Not Found: \"{\\\"error\\\":...\\\"}\""}`
  ——**后端那段 JSON 被当成字符串二次转义塞进 `error` 字段**，调用方解析出来是一坨带转义的 JSON 文本。
  ②**凭空造**——方向相反。后端 `GET /api/extraction/{templateName}/latest` 在**没有抽取结果**时
  返的是 **HTTP 200** + in-band `{"status":"not_found", ...}`（活体实测，非 404）；
  Python / Go / JS 三个 demo 透传 **200**，Java demo 却判 `!result.isFound()` 后**自己造了个 404**。
  这不是边角：`ExtractionResponse` 的 Javadoc 写明 `user_preference` 是**唯一随包的模板**，
  而任何新项目上它必然处于「还没抽过」的状态——**所以 Java demo 的这条路由在常见路径上就返 404**。
  根因很干净：12 个控制器共 **40 个 `catch (Exception e)` 块**（脚本按花括号深度统计），
  **只有 3 个**走到 `DemoErrors`——`ObservationsController` 2 个、`FeedbackController` 1 个，
  **其余 10 个控制器一个都没有**，一律 `internalServerError()`。
  顺带排除一个伪线索：Go demo 的 `/batch-observations`、`/create-observation` 与另三家不同名，
  是**有意为之**（源码注释写明为避开 Go 1.25+ ServeMux 与 `/observations/{id}` 的路径歧义），
  其 README 也已登记该差异——不是缺陷。
- **已修**: `DemoErrors` 的类 Javadoc 原先写着「**Controllers** use `statusOf` / `messageOf`」，
  在只有 2/12 控制器这么做时读起来像全覆盖声明。已按现状改写为精确表述
  （12 个控制器 / 40 个 catch 块 / 3 个走 helper / 10 个控制器没有），
  并点名 `PATCH /demo/session/user` 作为反例。**零行为变更**，`mvn -o test` 通过。
- **不修的理由**: 修它要改 10 个控制器的 catch 块，**改的是 demo 对外的 HTTP 状态契约**
  （500→404/400，且要决定 `error` 字段是否保留 SDK 前缀文本——Go/JS 加前缀、Python 不加，
  三家自己就不一致）。按既定规则**对外契约变更记录不单方面实施**。
  另注：`ErrorField` 的正则对 Spring 默认错误体（`{"timestamp":...,"error":"Bad Request"}`）
  会取出 `"Bad Request"`，**这条是后端本身就没给解释**，不算信息丢失，故不单列。

### P2-60: `CortexMemoryAdvisor` 的 `projectPath` 默认为空串——不设 `cortex.mem.project-path` 时，被捕获的提示**记下了却再也召回不了**

- **Problem**: `Builder.projectPath` 默认 `""`，自动装配又显式做 `getProjectPath() != null ? … : ""`；
  空串不是 `null`，能通过 `UserPromptRequest.toWireFormat()` 的 null 判断，被当作 `"cwd": ""` 发出。
  **活体实测**：后端返 `200`，行以 `project_path = ''` 落库，只有用空项目查才取得到。
  **规模**：库中 `EMPTY-STRING` 仅 **2 行**（都是我自己的探针），多数形态是 `NULL`
  （**2043 行 / 2011 会话**）——**属既有普遍现象的罕见写法，非 Java 特有缺陷**。
- **已修**: advisor 的 Javadoc 与两份 SDK README 均已按现状写明，**零行为变更**。
- **不修的理由**: 三种改法（`null` / `user.dir` / 拒绝记录）都改已发布 SDK 的公开默认行为；
  其中 `user.dir` 还会把提示归到用户并未选择的项目下，比现状更糟，故**不单方面实施**。
- **Status**: ⏸ 记录不修（文档已更正；行为变更待项目决定）。

### P2-48: gitignored 的 `CLAUDE.md` 端点表 25 条里有 9 条是活体 404 的幻影端点

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 把 `CLAUDE.md` 里形如 `| METHOD | \`/path\` |` 的表格行逐条对拍活体 `/v3/api-docs`：
  **25 条中匹配 16 条，幻影 9 条**，9 条**逐条实测为 404**：
  `POST /api/ingest/session-start`、`POST /api/memory/save`、`POST /api/context/observations`、
  `GET /api/memory/quality-stats`（真实为 `quality-distribution`）、
  `GET|POST /api/modes/active`、`GET /api/sessions`、`GET /api/sessions/{id}`、
  `POST /api/sessions/import`（真实为 `/api/import/sessions`）；
  `/api/ingest` 下实际只有 `observation` / `session-end` / `tool-use` / `user-prompt` 四条。
  其中 `GET /api/sessions` 与幻影 MCP 工具 `__IMPORTANT`、`V17` 标为「✅ Complete」
  早已被观察到，并入既有的 `AGENTS.md` / `CLAUDE.md` 待决项；**其余 7 条是本轮首次精确计量**。
  同区域的另一处漂移：`CLAUDE.md` 的项目结构写「controller/ # 17 controllers」，
  **实测 13 个**（service 写「28+」，实测 29，属「+」的合法范围，不计）。
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Status**: ⏸ **记录不修** —— `CLAUDE.md` 是 **gitignored 的本地文件**
  （`git ls-files` 未跟踪、`git check-ignore` 命中），改动**不会进入版本控制**，
  且「是否取消其 gitignore」本身仍是待用户决策事项；不在本轮静默修改。
  **待该决策落地后**，修法即按 `API.md` 的真实路径逐条更正这 9 行；其中
  `ingest/session-start`、`memory/save`、`context/observations` 三条
  **须先确认是被重命名还是从未存在**——若是后者则是纯粹删除。

## Processing Rules
- SDK/Demo findings are fixed in place with focused compile/test verification.
- Backend findings are fixed in place when small and safe; otherwise they remain here until the complete acceptance stage.
- Every finding must end as a code fix, a documented design decision, or an explicit skipped status. Reporting alone is not a valid resolution.
- After resolution, append the verification result and commit identifier here before moving the detailed entry to an archive.
- **⏸ 条目必须保留 `- **Problem**` 与 `- **Status`**（问题是什么、为什么这么定）。
  **2026-10-04（第 254 轮）经用户明确决策**：`- **Scope**` 与 `- **Evidence**`
  可与实测记录一并迁入归档，各留一行指针——Scope 内多为文件行号与实测细节，
  属可复现证据而非决策推理。此前只允许迁出实测记录（见第 241/244/247/253/254 轮先例），
  **本次是本文件建立以来第一次放宽**，其余适用范围仍未决。
- **逐轮摘要表已于第 258 轮整体移除**（第 219~256 轮全部行）。此前规则是「只保留最近一轮」，
  但本文件顶部从一开始就写着「逐轮叙述不再保留在本区块」，而这张表恰恰违反它自己写下的规则；
  每一行在 `patrol-rotation.md` 与 `doc-review-task.md` 里都有同轮、同等或更完整的叙述。
  **压缩脚本的两条硬约束（第 258 轮用一次销毁换来的）**：
  ①**在原始索引上算出的切割区间不得按升序复用**——每次替换都会让后续区间整体前移，
  必须**单遍顺序重建**或**倒序处理**；
  ②边界断言必须同时覆盖 `- **`、`### ` **与 `## `** 三种，只判前两种会让最后一个块一路吞到文件末尾。
  第 258 轮两条都漏了，结果是 P2-47 整块被销毁且未进归档。**压缩后必须逐条验证**：
  条目数、每条未迁出正文是否逐字仍在、指针数与归档块数是否相等、归档指针是否可解析。

## Archived History

历次压缩批次的完整记录已逐字迁入 [`2026-10-04_backend-review-compression-log.md`](../archive/2026-10-04_backend-review-compression-log.md)（第 252 轮迁出）；各批次在 `docs/archive/README.md` 中亦有逐条登记。
2026-05-07 之前的完整审查日志见 [`2026-09-30_backend-review-findings-history.md`](../archive/2026-09-30_backend-review-findings-history.md)。
