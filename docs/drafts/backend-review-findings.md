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
> **逐轮叙述不再保留在本区块**：每条发现在 `## Open Findings` 里有完整条目；
> 逐轮上下文另存于 `patrol-rotation.md` 与 `doc-review-task.md`，历次压缩批次记在文末 `## Archived History`。
| 轮次 | 条目 | 一句话 |
|------|------|--------|
> 第 219–250 轮的逐轮摘要已移除——它们本就不该放在本区块（见上方说明），
> 且每条都在 `patrol-rotation.md` 与 `doc-review-task.md` 里有**同轮全文**。
| 251 | Python SDK + 设计文档 | ✅ **`build_icl_prompt(max_chars=None)` 的裸 `TypeError` 已修**（第 233 轮把 `if max_chars:` 改成 `if max_chars > 0:` 后，该方法**唯一不对 None 安全的参数**；Java SDK 本就是 `!= null && > 0`）；✅ **`phase-3-design/25.md` 的幻影清理端点已改**（`DELETE /api/memory/observations?project_path=…` **活体 404**，改为脚本真正用的「取 id + 逐条删」，并实跑验证 1 → 0）；⏸ 记录不修（**P2-46**：验收脚本 `cleanup()` **定义了从未被调用**、内含幻影端点，Test 6 的 `not_found` 分支早已是死代码；**P2-47**：API.md 双语把只进日志的 `is_worktree`/`parent_project` 当正式字段记载） |
| 252 | Demo + 架构文档 | ✅ **Python demo 的 `/extraction/history` 补上了它唯一缺失的范围校验**（解析了 `limit` 却从不校验，四家里只有它对 `limit=101` 返 200、另三家均 400）；✅ **JS demo 去掉了后端根本没有的 `maxChars` 100000 上界**（后端只有下界 `Math.max(100, …)`，四家里只有它对 `100001` 返 400）；✅ 架构文档的控制器图、服务图、迁移树**双语全部核实准确**（13 控制器 / 31 服务 / 16 迁移，零幻影零遗漏）；⏸ 记录不修（**P2-48**：gitignored 的 `CLAUDE.md` 端点表 **25 条中 9 条是活体 404** 的幻影端点） |
| 253 | Backend + 运维/用户指南 | ✅ **读 Cursor 注册表失败不再被当成「空注册表」**（那个空结果**会被 register/unregister 写回**，实测一次注册返回 200 success 并把 16 个已注册项目全部丢弃；已改为与写路径对称地抛异常，修复后同一序列得 500 且注册表未被覆盖）；✅ **`DEVELOPMENT.md` 四处版本钉死的 jar 名改为通配**（其中文版本本就是通配，EN 侧会在版本变更后失效）；⏸ 记录不修（**P2-49**：`start.sh` 钉死版本号且是 TESTING.md 推荐的启动方式，版本变更即拒绝启动；**P2-50** 同区域：数据目录有 `CLAUDE_MEM_DATA_DIR` 与 `claudemem.data-dir` 两个互不相干的键） |
| 254 | Java SDK + API 文档 | ✅ **`is_worktree` / `parent_project` / `projects` 三处错误描述在文档与活体 OpenAPI 上一并更正**（P2-47；`@Schema` 才是真正对外的那一份，三处均为描述文本、字段与行为未变）；✅ **10 个已删源码的陈旧测试类被清出**（其中 P2-44 的 `NestingProbeTest` 仍在失败，使 `mvn test` 退出非零；`mvn clean test` 后 **196 = 143+46+7** 与 README 逐字吻合）；✅ Java SDK 空安全核实为真（`maxChars != null && > 0`、primitive `limit` 不可能为 null）；⏸ 记录不修（**P2-51**：`projects` 只在值中含逗号时生效，单个值被静默忽略、与不传等价） |
| 255 | Go SDK + SDK README | ✅ **`WithTimeout(50ms)` 此前实际得到 30 秒**（钳「下限」却赋「默认最大值」，比请求值长 600 倍且方向相反；**同一段代码的 `RetryBackoff` 用同一常量做地板、Python 是 `max(0.1, timeout)`**——Go 是四家里唯一把下限做成上限的）；已改为 100ms 地板，+3 条测试（根模块 299 → 302、覆盖率 95.2% → 95.7%），**双向注入回退后恰好 1 条失败**；✅ **两份 Go README 测试数已双语同步 359 → 362** |

> 历次压缩的批次与理由统一记在文末 `## Archived History`，
> **此处不再重复**——两处原本记着同一批压缩事件，每次压缩都要改两遍。

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
- **Verification**（2026-10-03）：修复后 `mvn package` 通过、后端启动干净、
  日志中 `QuerySyntaxException` / `column does not exist` **零命中**。
  SQL 层双向证明（各自独立连接，避免事务中止干扰）：
  含幽灵列的 6 列 SELECT → `FAILS: column "created_at" does not exist`；
  修复后的 5 列 SELECT → **OK**。
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
- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。
### P2-8: 读取侧没有维度路由 —— 写入按维度分列，检索恒定比 `embedding_1024`

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `SearchService` 在 PATH 2 的注释自称 "Semantic search with pgvector
  (dimension-aware)"，第 59 行也确实算出了 `int dim = request.queryVector().length`，
  但该变量**只用于 debug 日志**，实际 SQL 始终与 `embedding_1024` 比较。仓库里
  `semanticSearch768` / `semanticSearch1024` / `semanticSearch1536` 三个方法带有正确的
  分维度 SQL，但**全仓零调用方**（`grep` 主代码与测试均无命中）。因此这是一个
  写侧已实现、读侧未实现的非对称。
- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。
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
- **量化证据（第 202 轮补测）**：在真实库上按 `refined_from_ids` 统计，
  18,373 次带输入的抽取共涉及 **3,885 个不同观测**，其中 **3,880 个（99.9%）
  被送入 LLM 超过一次**，**单个观测最多被重复发送 689 次**。
  这把「会重复」从代码推断变成了实测幅度。（另一条独立的量化视角：
  当前候选窗口与全部历史输入的交集为 0，说明窗口确实只随时间前移——
  旧观测是**掉出**窗口而非被去重排除。）
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
- **精确边界**（避免说成「无条件失效」）：它并非任何时候都无效。当
  `EXTRACTION_MAX_CANDIDATES > EXTRACTION_BATCH_SIZE × EXTRACTION_MAX_BATCHES`
  （随附默认下为 200）时它才开始起作用。所以**单独调高它没有任何效果**，
  必须同时调高候选上限；单独调低到 ≤5 才有效。
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
- **Reproduction**: 原始实测记录已归档 → [`2026-10-04_backend-review-reproduction-4.md`](../archive/2026-10-04_backend-review-reproduction-4.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
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
- **Reproduction**: 原始实测记录已归档 → [`2026-10-04_backend-review-reproduction-4.md`](../archive/2026-10-04_backend-review-reproduction-4.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
### P2-27: Python SDK 无法清空 `extractedData` —— 与 Go 并列最弱，而它的注释把这一点说成了「对齐 Go」

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 后端 `PATCH` 两种清空写法都能落库（实测 `null` → NULL、`{}` → `{}`），
  而 Python **两种都发不出**：`None` 被 `if val is not None` 跳过、`{}` 被上面那句
  `continue` 跳过；探针确认二者的 `to_wire()` **都是 `{}`**、`is_empty()` **都是 True**，
  故**一条已有 extractedData 的观测无法通过 Python SDK 清空它**。
  活体佐证该字段真实在用：38,200 行中非空 **20,780**、NULL **17,420**、**`{}` 为 0**——
  后端自身从不写 `{}`，走这条路会造出库中从未出现过的状态。
- **四家能力阶梯（清空 extractedData）**: JS 两种都能发（`null` 原样透传，类型于第 246 轮修正）→ **真清空**；
  Java 只能发 `{}`（`@JsonInclude(NON_NULL)`）→ 只能落 `{}`；Go 两种都不能（`omitempty`）→ **完全不能**；
  **Python 两种都不能**（`if val is not None` / 显式 `continue`）→ **完全不能**。

  （**第 247 轮补注**：`✗` 只对 Python 的 **dataclass 调用路径**成立。
  `update_observation(id, title=None)` 这种 **kwargs 写法不经过 `to_wire()`**，
  而是逐字段原样拷贝，故 `None` 会真的发上 wire 并清空字段——探针与 demo 活体均已确认。
  即 Python **能**经 kwargs 清空、**不能**经 dataclass 清空。详见 **P2-43**。）

  注意 Go 与 Python **在 facts/concepts 上能力相反**（Go 因 `omitempty` 丢弃空切片而
  不能清空，Python 因 `[] is not None` 而能清空）——见 P2-26。
- **更正（2026-10-04 第 246 轮，本表 JS 行的判定依据当时不成立）**: 该行原以
  「原样透传给 `JSON.stringify`」为由把 JS 判为「能发 `null`」。**运行时确实透传，但类型不允许**：
  `ObservationUpdate` 当时声明为 `title?: string` 等，在本包自身的 `"strict": true` 下
  `{ title: null }` 是**编译错误**——实测 `tsc` 报 `TS2322: Type 'null' is not assignable
  to type 'string | undefined'`，八个字段全中。**本 SDK 自己的测试就是证据**：
  `client.test.ts` 里那两个名为 "should accept null fields for PATCH clear semantics" /
  "should accept all-null fields" 的用例，必须写 `null as unknown as string` 才能表达这个能力。
  故该行在第 246 轮之前实际应记作「**运行时可、类型不可达**」，Python `dto.py` 里
  「JS can send `null`」那句同样只是运行时成立。**第 246 轮已把八个字段放宽为 `T | null`**，
  现在该行按字面成立。四家的**净能力**（能否真正把字符串字段清空为 NULL）也随之明确：
  **只有 JS 能**，Java / Go / Python 三家都只能落 `{}` 或空串——上表未反映这一点。
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
- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。
### P2-29: tool-use 去重键不是一次调用的身份，且未被原子强制

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 去重键是 `(content_session_id, tool_name, SHA-256(tool_input))`，
  判定条件额外要求 `status <> 'failed'`。三处各自独立地削弱了它：

  1. **键里没有 `tool_response`。** 哈希只覆盖 `toolInput`，故「同样的工具、
     同样的入参、结果不同」的调用在前一条仍 `pending`/`processing` 时被**直接丢弃**，
     而调用方只拿到一条 "Duplicate tool-use event skipped" 日志加上
     HTTP `200 {"status":"accepted"}`——**与真正入队无法区分**。
     HTTP `200 {"status":"accepted"}`——**与真正入队完全无法区分**。
     对 fire-and-forget 的 SDK 捕获路径而言，调用方只能得出「已记录」这个错误结论。
     调用方只能得出「已记录」这个错误结论。
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
- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。
  对负数 **抛 `ValidationError`**，而两个最常用的检索方法静默丢弃——**同一份代码里两种
  处理，且代码与 README 都没给出任何理由**。
- **Reproduction**: 原始实测记录已归档 → [`2026-10-04_backend-review-reproduction-4.md`](../archive/2026-10-04_backend-review-reproduction-4.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
### P2-31: Go SDK 仍把负数 `maxChars` 发上 wire，注入被钳到 100 字符

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `omitempty` 只省略 **0**，**负数照发**（`omitempty` 判定的是 Go 零值，
  而 `-5` 不是零值）。后端解析式是 `maxChars != null ? Math.max(100, maxChars) : 4000`
  （`MemoryController.java:154`），**判 null 不判 0**，于是负数落进 `Math.max(100, -5)`
  → **100**。**Python 曾是同一形态**（`if max_chars:` 只跳过 0），本轮已修（见下）。
- **Reproduction**: 原始实测记录已归档 → [`2026-10-04_backend-review-reproduction-4.md`](../archive/2026-10-04_backend-review-reproduction-4.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
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
- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。
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
- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。
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
- **一处探针自身出错并先识别再采信**：初版探针想用 `dto.SummariesResponse` 去解析
  活体响应，编译失败——**这个类型根本不存在**，而这恰恰印证了「没有 summaries 方法」
  这一判断本身（同一次探针的另一个版本甚至编译不过）。改为直接统计四家 SDK 里
  非测试代码对 `summar(y|ies)` 的引用数（排除 `totalSummaries` 等统计字段），
  四家**均为 0**。
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
- **一处探针自身出错并先识别再采信**：首版探针把「DTO 解析后的对象」当 dict 处理
  （Python DTO 是 dataclass），于是**每个键都被报成丢弃**、看起来像一片灾难；
  抽查区反而暴露了真问题（`content_hash` 属性不存在），促使改用
  `dataclasses.fields()` 内省。修正后 15 个「丢弃」里**大部分只是改名**
  （`content_session_id`→`session_id`、`project`→`project_path`、`hasMore`→`has_more`、
  `springBoot`→`spring_boot`、`extractedData`→`extracted_data`），
  真正缺失的才是上面那几个——**若不复核就会写成一条夸大的假发现**。
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
- **同区域另两处事实（均记录，留待各自轮次处理）**:
  ①**`is_worktree` / `parent_project` 只进日志**——`SessionController` 读了两者后
  **仅用于一条 `log.info`**，不落库、不参与 `initializeSession`；而本该让 worktree 真正生效的
  `WorktreeDetector` 服务**在 `backend/src/` 内零调用者**（除自身文件外无任何引用）。
  活体佐证：带 `is_worktree:true` + `parent_project` 的请求与不带时的响应**完全相同**。
  **但 `API.md` 把两者作为正式字段记载并写进了示例 body**（「Whether this is a worktree」、
  「Parent project name (worktree mode)」），**文档描述的是尚未实现的能力**。
  属 API 文档方向的问题，留待下一轮 API 文档审查更正。
  ②**`CLAUDE.md` 的 Go 测试数与 Go README 互相矛盾**：CLAUDE.md 写「372 unit tests
  (278 core + 61 dto + …)」，Go README 写 359 并给出**实测吻合的分解**（根模块 299 =
  core 232 + dto 67，另加 eino 8 + genkit 13 + langchaingo 12 + `examples/http-server` 27）；
  两者 core/dto 拆分矛盾且 CLAUDE.md 漏了 27 条。**以 Go README 为准**（分解经复核成立），
  更正留待项目决策。①②已分别独立立为 **P2-47** 与 P2-48（`CLAUDE.md` 的端点表幻影）。

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

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 读失败返回**空 Map**，而这两个调用方都是「读 → 改 → 写回」，
  **一个读失败于是成了注册表的新内容**。同类的写路径 `writeRegistryUnlocked` 却**抛异常**——
  **读写不对称，且不对称的那一侧是破坏性的**。
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Status**: ✅ **已修** —— `readRegistryUnlocked` 在**文件存在但无法解析**时改为抛
  `UncheckedIOException`，与 `writeRegistryUnlocked` 对称；**「文件不存在 = 空的」保持不变**
  （那才是真正的空）。方法 Javadoc 写明了为什么不能返回空：返回空会被写回。
  调用方本就 `catch (Exception)` 并返回 500，无需改动。
- **同区域新发现（记录不修）**: **数据目录有两个互不相干的键**——`CursorService` 用
  `@Value("${claudemem.data-dir:…}")`，`AppSettings` 用 `CLAUDE_MEM_DATA_DIR`。
  设后者只会挪走 `settings.json`，**`cursor-projects.json` 仍落在 `~/.claude-mem/`**——
  本轮第一次起隔离实例就这么把 4 个探针写进了真实注册表（原始 16 条未丢，已清理）；
  正确写法是 `-Dclaudemem.data-dir=...`。两键并存、语义重叠、文档未说明，需项目拍板收敛。


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

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: Maven 不会因为源文件被删而清理 `target/test-classes`，
  于是 **`mvn test` 会继续编译目录里已有的陈旧 class 并执行它们**。
  这类 class 是历轮排查留下的探针（源码在确认结论后按惯例删除、未提交），
  **它们在源码里不存在，却仍在测试阶段运行**。本轮的 `NestingProbeTest` 正是
  第 249 轮为 P2-44 写的探针，它**断言外层作用域应当存活**——
  而那正是 P2-44 记录的**未修缺陷**，所以它**必然失败**。
  后果有二：① 源码全绿的工作区上 `mvn test` **退出非零**（实测 `mvn -o clean test`
  前后分别是「失败 1」与「全过」）；② 测试计数被抬高（见 Evidence）。
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Status**: ✅ **已处置** —— 执行 `mvn -o clean test`，陈旧类清除、计数回到 196、
  退出码 0。**未改任何源码或脚本**：这十个 class 都不在版本控制内，
  `clean` 即是正解；`mvn clean` 本就是文档与 CI 的标准起手式。
  **遗留的纪律问题（比缺陷本身更值得记）**：本会话早前跑 Java SDK 测试时用了
  `mvn … | tail; echo $?` —— **`$?` 取的是 `tail` 的退出码、恒为 0**，
  于是**一次真实的测试失败被完美地掩盖了**。
  「管道会吞掉上游退出码」是 Bash 的基本事实，本轮**又一次**靠人工核对才发现
  （与第 252 轮 `grep -P`、第 253 轮行号偏移同属「探针自身出错」一类）。


### P2-53: Go SDK 的 `WithTimeout` 把「太小的值」重置成**默认最大值**——请求 50ms 实际得到 30s

- **Scope**: `go-sdk/cortex-mem-go/client_impl.go` 的 `NewClient` 配置归一化段。
- **Problem**: 归一化写的是
  `if cfg.Timeout < 100*time.Millisecond { cfg.Timeout = 30 * time.Second }`——
  **触发条件是「太小」，赋的却是「默认值里的最大值」**。于是调用方
  `WithTimeout(50*time.Millisecond)` 得到 **30 秒**，比要求的值长 **600 倍**，
  且方向正好相反：想用短超时给健康探针兜底的人，拿到的是最长的那个。
  `ConnectTimeout` 同样（`10 * time.Second`）。
  **两处证据把意图钉死为「地板」而非「重置」**：
  ①**同一段代码的下一行** `RetryBackoff` 用的是**同一个触发常量**而赋值
  `100 * time.Millisecond`——它才是地板；②**Python SDK** 同一概念是
  `self._timeout = max(0.1, timeout)`，注释写「Minimum 100ms to prevent immediate timeout」。
  三家对照：Java 的 `readTimeout` **完全不钳制**、Python 钳到 0.1s 地板、**Go 钳到 30s 天花板**——
  **Go 是唯一把下限做成上限的一家**。`DefaultClientConfig` 本身就已是 30s / 10s，
  所以这段归一化**只会在调用方显式传小值时触发**，而那正是它要服务的场景。
- **Evidence**: 修复前实测（探针直接读归一化后的 `httpClient.config`）：
  请求 `0 / 10ms / 50ms` → 实际 `30s / 30s / 30s`；请求 `100ms` → `100ms`；
  请求 `5s` → `5s`；**同段对照** `RetryBackoff(10ms)` → `100ms`。
  修复后 `0 / 10 / 50 / 99 / 100ms` → 全部 `100ms`，`250ms` 与 `5s` 原样透传。
- **Status**: ✅ **已修** —— 两处改为地板 `100 * time.Millisecond`，与 `RetryBackoff`
  及 Python SDK 一致；注释改写为说明「为什么是地板」，并记录修复前的实测。
  **新增 3 条测试**（`config_internal_test.go`，**必须是内部测试包**：
  归一化结果存在未导出的 `httpClient` 上，外部测试包 `cortexmem_test` **完全无法观察**
  ——**这正是该缺陷能存活的原因：没有任何测试断言过归一化路径**）：
  地板与透传、两个对照组（`RetryBackoff` 地板、默认值 30s/10s/500ms 不变）。
  **双向注入**：回退到修复前的 `= 30 * time.Second` / `= 10 * time.Second` 后
  **恰好 1 条失败**（`TestClientTimeoutIsFlooredNotReset`），
  **两条对照组在两种状态下都不失败**。根模块 **299 → 302**，
  覆盖率 **95.2% → 95.7%**，全模块 `test-all.sh` 九个模块全绿。
  **两份 README 的测试数已双语同步 359 → 362**（根模块 299 → 302、core 232 → 235、
  实测日期 2026-10-03 → 2026-10-04）。


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
- **逐轮摘要表只保留最近一轮**；更早的轮次在 `patrol-rotation.md` 与 `doc-review-task.md`
  中有同轮全文，无需在此重复（第 254 轮据此移除了第 219~250 轮）。

## Archived History

历次压缩批次的完整记录已逐字迁入 [`2026-10-04_backend-review-compression-log.md`](../archive/2026-10-04_backend-review-compression-log.md)（第 252 轮迁出）；各批次在 `docs/archive/README.md` 中亦有逐条登记。
2026-05-07 之前的完整审查日志见 [`2026-09-30_backend-review-findings-history.md`](../archive/2026-09-30_backend-review-findings-history.md)。
