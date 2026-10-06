# Backend Review Findings

> **Purpose**: 记录当前仍需处理的 Backend 代码审查发现。
> **Updated by**: 定时项目维护任务。
> **Update rule**: 新发现必须记录文件、行号、问题、严重级别和处理状态；已解决的详细历史归档，当前未解决项保留在本文件。

## Current Status

| Severity | Open | Rule |
|----------|------|------|
| P0 | 0 | 立即修复并复测 |
| P1 | 1 | 优先修复并复测 |
| P2 | 7 | 本轮完整验收阶段处理或明确标记为已跳过 |

- **已整体迁出**（current-status-note）: 逐字迁入 [`2026-10-04_backend-review-resolved-15.md`](../archive/2026-10-04_backend-review-resolved-15.md)（第 263 轮）。

## Open Findings

### P2-24: V17 反馈机制整体未接线 —— 实体还映射了一个不存在的列

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**：**V17 反馈机制整体未接线**，三条实证：①`ObservationFeedbackEntity` 映射了一个
  `V17` **从未创建**的 `created_at` 列（活体 `information_schema` 只有六列），任何触及该实体的
  查询都会报 `column "created_at" does not exist`；②`findByObservationIdOrderByCreatedAtDesc`
  实际按 `createdAtEpoch` 排序、**方法名描述的列不存在**且零调用方；③V17 声明的三项能力
  **全部没有写入方**，「Thompson Sampling 优化的基础」目前是**纯脚手架**。
- **Severity 说明**：①是**潜伏缺陷**而非启动即崩——该表 0 行、repository 零调用方，Spring Data
  不预校验 JPQL 引用的列，故后端仍能正常启动，会在**第一次真正使用该实体时**炸掉。
  ③是**未实现特性**而非错误行为。
- **实测证据**: 逐字迁入 [`2026-10-05_backend-review-evidence-16.md`](../archive/2026-10-05_backend-review-evidence-16.md)（第 267 轮）——含逐列清单、两个 `0 行 / 0 非空` 计数与 `grep setRelevanceCount` 零命中。
- **Verification**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Status**：①②✅ **已修复**（2026-10-03，第 219 轮）。
  ③⏸ **记录不实现** —— 接入反馈采集属**新增特性**（需要新的写入路径、信号定义与
  Thompson Sampling 算法），不是修 bug，按既定纪律留待项目决策。
  **注**：`CLAUDE.md:39` 把 V17 标为「✅ Complete」，该文件已被 gitignore，
  并入既有的 `AGENTS.md` / `CLAUDE.md` 开放项，不在本轮静默修改范围内。
- **复核记录**: [`…-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮；Problem / Status 留本文件）。
### P1-1: `CortexSessionContextBridgeAdvisor.adviseStream` 依赖普通 ThreadLocal，流式下既丢捕获又泄漏会话

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `adviseStream` 在**调用线程**上 `begin()`，却把清理放进 `flux.doFinally(...)`。Reactor 的 `doFinally` 运行在**发出终止信号的线程**上；任何真实模型客户端（Reactor Netty / WebClient）都会切线程。产生两个后果：
  1. **捕获被静默丢弃** —— 工具实际执行的线程看不到该 ThreadLocal，`CortexSessionContext.isActive()` 为 false，`CortexToolAspect` 直接 `proceed()` 跳过捕获。`@Tool` 自动捕获在流式下等于失效，且无任何日志。
  2. **会话上下文泄漏** —— `doFinally` 清掉的是信号线程（一个空 ThreadLocal），调用线程的 ThreadLocal 永不清除。线程池复用该线程后，`begin()` 因 conversation id 缺失而提前 return 的那条路径**也不会**清理，于是残留的 `sessionId` 会被下一次请求的 `CortexToolAspect` 当作有效会话使用——工具观察被归到**上一个会话**。这是静默的跨会话数据串号。
- **实测记录**: [`…-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
### P1-2: Java demo 的 `?path=` **无任何路径校验**，且服务绑 `*:37778` —— 同网段可读走本机任意文件
- **Scope / Evidence**: `examples/cortex-mem-demo/.../FileReadTool.java:23-25`；三个 HTTP 入口
  `ToolsController.java:36-48`、`SessionLifecycleController.java:104-111`、`:186-217`；
  `src/main/resources/application.yml:2`（**只设 `server.port`，无 `server.address`**）。
- **Problem**: `readFile` 直接 `Files.readString(Path.of(path))`，**无根目录约束、无 `..` 检查、无白名单**；
  `?project=` 只约束**记忆捕获**的项目，**与文件读取无关**。同时 `application.yml` 未设
  `server.address`，Spring Boot 默认绑 `*:37778` —— 而后端显式设了
  `address: ${SERVER_ADDRESS:127.0.0.1}`，两者姿态相反。
- **实测记录**: 逐字迁入 [`2026-10-05_backend-review-evidence-18.md`](../archive/2026-10-05_backend-review-evidence-18.md)（第 269 轮）。
- **Severity 说明**：demo 全局无鉴权是**已知设计**（架构文档写明 "Currently no authentication
  (local development)"），但**「无鉴权」与「可读任意文件」是两件事** —— 前者只暴露记忆 API，
  后者可取走 `~/.ssh/id_rsa`、`~/.aws/credentials`、含密钥的 `.env`，同网段即可触发。
- **Status**: ✅ **已修（第 294 轮，经用户明确授权）** —— 此前多轮 ⏸ 的理由是「加路径约束属收窄已发布端点语义、是对外契约变更」，而这正是需要用户拍板的那一类，**已取得授权**。两步：①`application.yml` 补 `server.address: ${SERVER_ADDRESS:127.0.0.1}`（仍可用环境变量覆盖）；②`FileReadTool` 改为**解析到根目录内**——相对路径按根解析，绝对路径仅当已在根内才接受，逃逸则在**读取之前**拒绝且**不回显内容**，并同时做**词法 `..` 归一化与 `toRealPath()` 符号链接检查**（前者不跟链接、后者对不存在的路径会抛，两者不可互相替代）。三个控制器的 `?path=` 默认值由绝对路径 `/tmp/hello.txt` 改为相对路径 `hello.txt`（沙箱化后原默认值必被拦）。**活体验证**：`hello.txt` 正常返回；`/etc/passwd`、`/Users/<me>/.ssh/id_rsa`、`../pom.xml`、`/etc/../etc/passwd` 四种逃逸**全部在读取前被拒**且响应不含内容；`lsof` 实况 demo 由 `*:37778` 变为 **`127.0.0.1:37778`**，从本机非回环地址 `10.166.1.125:37778` **连接失败**。**+8 条测试**（`FileReadToolTest`，含符号链接逃逸），demo 套件 **25 → 33 全绿**。demo README 端点表下的警告改为如实描述现状。
  **本轮未改任何 Java 代码**，实测用的 demo 进程与探针文件已清理。
### P2-8: 读取侧没有维度路由 —— 写入按维度分列，检索恒定比 `embedding_1024`

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `SearchService` 在 PATH 2 的注释自称 "Semantic search with pgvector
  (dimension-aware)"，第 59 行也确实算出了 `int dim = request.queryVector().length`，
  但该变量**只用于 debug 日志**，实际 SQL 始终与 `embedding_1024` 比较。仓库里
  `semanticSearch768` / `semanticSearch1024` / `semanticSearch1536` 三个方法带有正确的
  分维度 SQL，但**全仓零调用方**（`grep` 主代码与测试均无命中）。因此这是一个
  写侧已实现、读侧未实现的非对称。
- **实测记录**: [`…-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
### P2-10: 四个 ingest 端点对项目路径的必填性不一致

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）；**第 275 轮**删除 Problem 段一处**逐字重复句**并迁出整条原文 → [`2026-10-06_backend-review-evidence-25.md`](../archive/2026-10-06_backend-review-evidence-25.md)；**第 278 轮**再迁出其 Status 的压缩过程原文 → [`2026-10-06_backend-review-evidence-29.md`](../archive/2026-10-06_backend-review-evidence-29.md)。
- **Problem**: 增量抽取**没有实现**。后端全文没有 `extraction_state`（0 命中），
  每次运行都取最新的 N 条、**没有「上次抽取之后」的过滤**。`findNewObservations`
  本身实现完好、SQL 正确，但 `backend/src/main` 中**零调用方**、连单测都没引用。
  与 P2-12（`deepRefineProjectMemories` 无调用方）同型：一个从未接线的特性，只留下方法、注释和一条为它建的索引。
- **实际行为（与文档描述不同）**：`findBySourceIn` 是 `ORDER BY created_at_epoch DESC LIMIT N`，
  所以新观测**会**进来，但超出上限的旧观测**永远不会被抽取**——既不是文档所称的
  「增量」，也不是「全量重扫」，是「每次重扫最新的 N 条」。**纯成本与覆盖问题，不会返回错值**；
  但 23.md 曾把它列为「primary cost reduction mechanism」，运维据此估算 token 预算会系统性偏低。
- **量化证据（第 202 轮补测）**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Status**: ⏸**已记录，不实现**。接上它需要持久化抽取状态（7.md §7.1 提议用
  `type="extraction_state"` 的观测行承载），属新增特性而非修 bug，且过期/重建语义应由项目决定。
  **已做的是如实记录**：23.md §23.5 策略 3/4/5 补上「designed, not implemented」声明并给出真实的
  候选选取路径与排序方向；8.md 第 5 条、0.2.md Gap 3、17.md §17.2 三处同一断言一并更正。
  **按断言清扫的逐处经过**：逐字迁入 [`…-32.md`](../archive/2026-10-06_backend-review-evidence-32.md)（第 282 轮）。
### P2-15: `save_memory` 的共享会话是 check-then-act，并发下必然丢失一次保存

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）；
  **第 276 轮再迁出**另三家的**逐文件行号引用**与其原文注释 → [`2026-10-06_backend-review-evidence-26.md`](../archive/2026-10-06_backend-review-evidence-26.md)。
- **Problem**: `executeWithRetrySilent` 返回 `void`，**任何失败都被吞掉**，只在
  日志里留一条 WARN。调用方拿到的是一个正常返回的 `void`，**无从得知触发失败**——
  精炼没跑、抽取没跑，而调用方以为跑了。
  **另三家都抛错，且是刻意为之**（逐条行号见归档）——Go 写了注释说明理由
  「NOT fire-and-forget: this is an explicit user action, errors must propagate」；
  JS 与 Python 走各自的 no-content 请求路径，异常上抛。
- **Java 自己的注释是误导的**：`executeWithRetrySilent` 的 javadoc 写着
  「Matches the Go, Python and JS SDKs」。就**重试与退避策略**而言确实一致
  （±25% 抖动、不重试 4xx/500），但**错误传播**恰恰是 Java 唯一不同的那一点，
  而这正是调用方唯一能感知的部分。注释只对上了次要的一半。
- **同族的非静默差异**（不单独立项）：Java 还对 `submitFeedback` / `updateObservation` / `deleteObservation` /
  `getLatestExtraction` / `getExtractionHistory` 做了重试包装，Go/JS/Python 只在三个 fire-and-forget
  采集方法上重试。这些写操作本身**仍然抛错**，不是静默失败，只是重试面更宽——是否扩大属设计选择。
- **Status**: ⏸**已记录，不实现**。改这两处会**改变现有调用方的可观测行为**
  （原本被吞掉的异常会开始上抛），属对外行为契约变更，与 P2-13/P2-15/P2-16
  同一套判断；且需项目先决定这两条触发路径是否应纳入 fire-and-forget 语义。
### P2-20: 全部 22 个数值查询参数都会静默接受十六进制字面量

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）；
  **本轮（第 275 轮）再迁出**活体实测清单与排查陷阱原文 → [`2026-10-06_backend-review-evidence-25.md`](../archive/2026-10-06_backend-review-evidence-25.md)。
- **Problem**: Spring 的默认数字转换会**静默采纳 `0x`/`0X` 十六进制前缀**：先 trim，
  带十六进制前缀走 `Integer.decode`，否则走 `Integer.valueOf`。
  **五个端点的活体实测清单**（`?limit=0x10` 返回 16 条、`?lines=0x10`、`?maxObservations=0x10`、
  `?startEpoch=0x10`、`?offset=0x2`，**状态码一律 200 且响应中没有任何字段表明读的是十六进制**）
  与**一处必须说明的排查陷阱**（`/api/context/timeline` 对无法解析的 `?limit=abc` 与 `?limit=0x3`
  返回**完全相同**的 400，只看状态码会漏判；真正的解析失败返回 Spring 默认体）逐字见归档。
- **危害**：`startEpoch` / `endEpoch` 是**时间戳**，`0x` 前缀会把它们解释成一个
  1970 年附近的 epoch 毫秒值，**静默返回空时间窗而无任何报错**。`lines` 会被读成
  一个行数，`maxObservations` 会被读成一个条数。都没有校验、没有警告。
- **对照**：布尔参数**不受影响**（`?includeObservations=0x1` → 400）；四家 SDK 把这些
  参数声明为数字类型，**根本无法**把十六进制字面量放到线上，故只影响直接调用
  HTTP API 的代码。
- **Status**: ⏸**已记录，不实现**。收紧会把一批当前的 `200` 变成 `400`，
  属**对外 API 契约变更**，且波及 11 个端点；需项目先决定是否值得。本轮代码方向为
  Backend，已做的是**如实记录**：`docs/API.md` + `-zh-CN` 新增「Query Parameter
  Conventions / 查询参数约定」一节，写明通用规则、完整参数清单与该排查陷阱，
  并把 `limit` 小节改为指向它而非重复叙述。
### P2-21: 健康指示器在真故障时不给原因，而测试钉死了一个不可能发生的分支

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `healthCheck()` 自己 `catch` 后 **`return false`**、**从不向外抛出**，
  故 `health()` 的 `catch` 分支在生产中**不可达**，`withException(e)` 写出的
  `error` 键**永远不会被填充**。
  活体实测确证：死端口时 `status=DOWN`、`hasErrorKey=false`，真实后端则 `UP` ——
  **连接被拒 / 超时 / DNS 失败等真因全部丢失**，「不可达」与「degraded」文案完全相同 → [`…-36.md`](../archive/2026-10-06_backend-review-evidence-36.md) 第 1 块（第 291 轮逐字迁出）。
- **测试反而钉死了这个假象**：`CortexMemHealthIndicatorTest.health_whenClientThrows_returnsDown`
  用 **mock** 让 client 抛出并断言 `containsKey("error")`——真实 client 永远产生不了该
  状态，故此用例**恒真却毫无保护作用**（与第 197 轮同类：测试覆盖的是虚构状态）。
- **核实无误**：`"ok"` 大小写判定正确，null body / 非 `ok` / 异常三种情况均正确返回
  `false`，UP-DOWN 三分支本身正确 → [`…-36.md`](../archive/2026-10-06_backend-review-evidence-36.md) 第 2 块（第 291 轮逐字迁出）。
- **实测证据**: 逐字迁入 [`2026-10-05_backend-review-evidence-17.md`](../archive/2026-10-05_backend-review-evidence-17.md)（第 269 轮）。
- **Status**: ⏸**已记录，不实现**。要让原因到达指示器，需要 `healthCheck()`
  改为向上抛出（**改变既有方法的行为契约**，所有调用方的 `catch` 都要重审），
  或为 client **新增公开 API**（如 `getLastHealthFailure()`）供指示器读取——
  两者都属对外契约变更，与 P2-13~P2-20 同一套判断，需项目先定方向。
  本轮代码方向为 Java SDK，已做的是**如实记录**与**如实核实**（含一次假设被证伪：
  初判「环境变量形式无法关闭 `capture-enabled`」，改用**真实环境变量**复测后
  证明 `CORTEX_MEM_CAPTURE_ENABLED=false` **有效**——原结论来自
  `withPropertyValues` 不模拟环境变量这一**探针缺陷**）。

### P2-22: `/api/cursor/projects` 的 Swagger 示例把 ISO 字符串写成了 epoch 数字

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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
- **复核记录**（原文见 [`…-provenance.md`](../archive/2026-10-03_backend-review-provenance.md)，逐轮全文另见 `patrol-rotation.md`）

### P2-23: SSE 连接数超限返回 500（应为 503），且没有心跳，死连接最长占用名额 30 分钟

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem 1 —— 状态码语义错误**：SSE 连接数触顶（第 101 个客户端被拒）时，后端**没有任何
  `@ControllerAdvice` / `@ExceptionHandler`**，`IllegalStateException` 直穿到容器默认处理并返回
  **500**；容量耗尽是「服务暂时不可用」，应为 503（或 429）。且 `stream()` 的 `@ApiResponse`
  **只声明了 200**，该分支在契约中不存在。
- **Problem 2 —— 没有心跳，死连接要等下一次事件才被回收**：清理只发生在 `SseEmitter` 的
  `onCompletion` / `onError` / `onTimeout` 回调与 `broadcast()` 捕获异常时，全仓**无任何周期性
  心跳广播**，故废弃连接会一直占住名额，直到 `claudemem.sse.timeout-ms`（默认 **30 分钟**）
  触发 `onTimeout`。
- **实测证据**: 逐字迁入 [`2026-10-05_backend-review-evidence-16.md`](../archive/2026-10-05_backend-review-evidence-16.md)（第 267 轮）——含 105 条并发裸 socket 实测（**恰好 100 条 200、第 101–105 条 500**）、四处 `broadcast()` 调用点行号，以及活体响应 `Content-Length: 0` 不泄漏内部信息。
- **Status**: ⏸ **记录不修** —— 把 500 改成 503 属**对外契约变更**（客户端与监控
  都会看到不同状态码），按既定纪律留待项目决策；补心跳则会改变流量形态与
  `SseEmitter` 生命周期，同样需要决策。**两者都已写入本条，后端代码一字未改。**
- **复核记录**（原文见 [`…-provenance.md`](../archive/2026-10-03_backend-review-provenance.md)，逐轮全文另见 `patrol-rotation.md`）

### P2-25: `maxChars` 的 Swagger 描述承诺了一个后端并不存在的「0 = 默认」分支

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 后端**没有**「0 表示默认」的分支。`MemoryController` 第 154 行写的是
  `int maxChars = request.maxChars() != null ? Math.max(100, request.maxChars()) : 4000;`
  —— 判的是 `!= null`，不是 `> 0`。于是显式传 `0` 会走进 `Math.max(100, 0)`，
  得到 **100**，而非描述承诺的 ~4000。客户端作者照此实现「不传就传 0」的惯例，
  会把注入的 ICL 记忆上下文截到 100 字符，**且没有任何错误提示**（HTTP 200）。
- **Reproduction**: [`…-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
### P2-26: Go SDK 的 `omitempty` 让 `facts` / `concepts` / `extractedData` 无法清空，且静默返回「updated」

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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
- **Reproduction**: [`…-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
### P2-27: Python SDK 无法清空 `extractedData` —— 与 Go 并列最弱，而它的注释把这一点说成了「对齐 Go」

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）；**第 275 轮再迁出**活体行级统计与第 246 轮更正原文 → [`2026-10-06_backend-review-evidence-24.md`](../archive/2026-10-06_backend-review-evidence-24.md)。
- **Problem**: 后端 `PATCH` 两种清空写法都能落库（实测 `null` → NULL、`{}` → `{}`），
  而 Python **两种都发不出**：`None` 被 `if val is not None` 跳过、`{}` 被那句 `continue` 跳过；
  探针确认二者的 `to_wire()` **都是 `{}`**、`is_empty()` **都是 True**，
  故**一条已有 extractedData 的观测无法通过 Python SDK 清空它**。
  活体佐证该字段真实在用、且后端自身从不写 `{}`（38,200 行中非空 20,780、NULL 17,420、
  **`{}` 为 0**）→ [`…-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（含四家能力阶梯表与第 246 轮更正）与 [`…-36.md`](../archive/2026-10-06_backend-review-evidence-36.md) 第 3 块（第 291 轮逐字迁出）。
- **该缺陷为何能存活**: `tsconfig.json` 的 `exclude` 含 `"**/*.test.ts"`，而 `npm run lint` 就是 `tsc --noEmit`
  ——**测试文件根本不参与类型检查**，于是类型层与断言层之间的裂缝没有任何自动关卡。已独立立为 **P2-42**。
- **已修（注释层，行为一字未改）**: `ObservationUpdate` docstring 与 `is_empty()` /
  `to_wire()` 两处 `continue` 注释已按现状改写（428 测试全过）→ [`…-36.md`](../archive/2026-10-06_backend-review-evidence-36.md) 第 4 块（第 291 轮逐字迁出）。
- **Status**: ⏸ **行为记录不修** —— 改行为只有两条路：让 `{}` 发上 wire
  （**改变现有调用方的可观测行为**，`extracted_data={}` 从「不变」变成「落 `{}`」），
  或新增显式清空入口（**新增公开 API**）。按既定纪律留待项目决策。**注释层已先行更正**。
### P2-28: `/api/test/all` 丢弃两个子处理器的状态码，故障时仍返回 200

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 同一份「测试连通性」的语义，两个端点给出**互相矛盾的失败信号**。
  类级 `@Profile("!prod")` 门控是正确的（第 26 行），四家 SDK 也都零调用方，
  暴露面有限；但**任何用 `/all` 做巡检的脚本或监控，在提供方完全不可用时仍会看到
  200**，从而永远不会告警。Swagger 注解（第 116 行）**只声明了 200**，
  与实现一致 —— 也就是说**契约本身就是这样声明的**，问题不在契约与实现不符，
  而在这个契约让该端点失去了作为测试端点的意义。
- **实测记录**: [`…-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
### P2-29: tool-use 去重键不是一次调用的身份，且未被原子强制

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 去重键是 `(content_session_id, tool_name, SHA-256(tool_input))`，
  判定条件额外要求 `status <> 'failed'`。三处各自独立地削弱了它：
  1. **键里没有 `tool_response`。** 哈希只覆盖 `toolInput`，故「同样的工具、同样入参、
     结果不同」的调用在前一条仍 `pending`/`processing` 时被**直接丢弃**，而调用方只拿到
     一条日志加 HTTP `200 {"status":"accepted"}`——**与真正入队完全无法区分**。
  2. **`tool_name` 未规范化。** 它是客户端自由文本却参与键比较，故 `Read` 与 `read`
  **P2-29 第 2 点 · 规模实测**：逐字迁入 [`…-35.md`](../archive/2026-10-06_backend-review-evidence-35.md)（第 291 轮）。
  3. **检查与写入不是原子的，而唯一的兜底约束并不存在。** `PendingMessageEntity` 声明了
     `@UniqueConstraint(name = "uk_session_tool_input")`，但 `ddl-auto: none` 且
  **P2-29 第 3 点 · 迁移约束取证与死代码后果**：逐字迁入 [`…-35.md`](../archive/2026-10-06_backend-review-evidence-35.md)（第 291 轮）。
- **实测证据**: 逐字迁入 [`2026-10-05_backend-review-evidence-19.md`](../archive/2026-10-05_backend-review-evidence-19.md)（第 270 轮）——含具体 input 哈希、近 30 天命名计数、活体 `pg_constraint` 查询结果与手工插入记录。
- **实测记录**: [`…-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Reproduction**: [`…-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **P2-31 已整体迁出**: 逐字迁入 [`2026-10-04_backend-review-resolved-15.md`](../archive/2026-10-04_backend-review-resolved-15.md)（第 263 轮）。
### P2-30: 同一个非法 `limit = -5` 在**四家 SDK 有三种行为**，且 **Go 自身也不一致**（第 271 轮从压缩事故中恢复）
- **Problem**: Java **抛 `IllegalArgumentException`**（且 >100 也抛）、Go 与 JS **静默丢弃**、
  Python **照发**（负数在 Python 是真值）而被后端钳成 **1**。Go 内部亦分裂：`Search` /
  `ListObservations` 静默丢弃而 `GetExtractionHistory` **抛 `ValidationError`**，无理由说明。
  **本条曾于某次压缩中整块销毁且未进归档，第 271 轮据幸存片段恢复**——`Problem` 为
  **据第 133 轮日志的重建，非逐字**。
- **Impact / Status / Reproduction（逐字幸存）** 与事故经过：见
  [`2026-10-05_backend-review-recovered-P2-30.md`](../archive/2026-10-05_backend-review-recovered-P2-30.md)。
- **Status**: ⏸ **记录不修** —— 让 Go 抛错会让**当前能正常返回**的调用方开始失败，属公开 API
  行为变更；四家对齐更属跨 SDK 契约决策。**文档层已先行更正**（Go README 双语）。
### P2-63: 一次压缩把 P2-30 **整块销毁且未进归档**——正是 P2-47 事故的复发，而现行校验规则本该拦住它
- **Scope / Evidence**: `git log -S'### P2-30:' -- docs/drafts/backend-review-findings.md`
  仅两条命中（`1491f5b` 建立 / **`adf4366` 销毁**）；该提交对工作文件 **+41 / −143**，
  diff 中**无任何含 P2-30 的新增行**，其新建归档 `…-reproduction-5.md` 自述内容为
  P1-1 / P2-8 / P2-28 / P2-29 / P2-32 / P2-34，**不含 P2-30**。
- **Problem**: 压缩日志第 141 轮明写迁出 P2-30 的 Reproduction 时「Scope / Problem /
  Evidence / **Status** stayed put」——**故其主体本应留在工作文件中，不是有意归档**。
  `Impact` / `Status` / `Reproduction` / `复核记录` 幸存于两个归档，**`Problem` 彻底丢失**。
  后果：工作文件第 265 行到 P2-32 之间**没有 P2-30**（`sed -n '413,420p'` 可见），
  而 `python-sdk/cortex-mem-python/cortex_mem/client.py:403` 的 docstring 明写邻近站点
  「is recorded as **P2-30**」——**读者被指向一个查不到的条目**。这与 `Processing Rules`
  记录的 **P2-47 事故（第 258 轮）是同一失效模式**，且那次已立为常设断言。
- **Status**: ✅ **已恢复**（第 271 轮）—— 条目骨架已回到工作文件，`client.py:403` 的引用
  重新可解析；`Problem` 标为**重建**而非逐字，`Impact`/`Status`/`Reproduction` 保持指向
  原始归档。**现行断言为何没拦住**：那几条校验针对的是「边界断言覆盖三种行首」与
  「指针数 = 归档块数」，**没有一条检查「工作文件里曾经存在的条目是否还在」**——
  补这一条属规则变更，需项目决策，未自行添加。
### P2-32: 两个 Dockerfile 都不设 `SERVER_ADDRESS`，默认部署下服务对外不可达；根镜像的 healthcheck 还写死了端口

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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
- **实测记录**: [`…-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
### P2-33: Go demo 的两个端点名与另外三家 demo 不同

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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
- **复核记录**: [`…-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮；Problem / Status 留本文件）。
### P2-34: `GET /api/logs` 的 Swagger 示例漏掉 `files`，且把绝对路径写成 `/logs`

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 实现用 `Map.of(...)` 返回 **6** 个键
  —— `logs` / `path` / **`files`** / `totalLines` / `returnedLines` / `exists`，
  而注解的示例只有 **5** 个，**漏掉 `files`**；且示例写 `"path":"/logs"`，
  实际返回的是**绝对路径**（本机实测 `/Users/yangjiefeng/.claude-mem/logs`）。
  `/v3/api-docs` 是生成客户端代码的来源，所以这个缺失会传播到任何按 OpenAPI
  生成的 SDK 模型里。
- **实测记录**: [`…-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
### P2-35: `CortexToolAspect` 结构上无法捕获失败的 `@Tool` 调用，而质量模型恰恰以失败为一档

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 工具方法抛异常时，异常从第 60 行直接向上传播，**捕获整段被跳过**，
  调用方拿到的仍是原始异常（这一点是对的），但**这次工具调用在记忆里不留任何痕迹**。
  **关键在于这与后端的设计意图相反**：`QualityScorer` 明确有
  `FAILURE_BASE = 0.20f` 与 `FeedbackType.FAILURE`（第 24-26、59-61 行），
  即**整个 Evo-Memory 质量模型就是围绕「区分成功与失败」建立的**——
  而这条自动捕获路径**一条 FAILURE 都产不出来**。
- **Status**: ⏸ **记录不修** —— 修它会让**所有用户的库里开始出现新的失败观测**，
  改变已存储的数据形态，属**产品决策**而非纯 bug 修复（沿用 P2-24「接入属新增特性
  而非修 bug」的同一判断）。修法：把 `proceed()` 包进 try，catch 后**先记录再重抛**
  （捕获本身已 fire-and-forget，不会掩盖原始异常），并补一条「工具抛异常时仍被捕获」
  的测试。**SDK 代码一字未改。**
- **复核记录**: [`…-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮；Problem / Status 留本文件）。
### P2-36: 三个同级适配器（eino / genkit / langchaingo）对数值选项的校验互不一致，且 genkit 的兜底只护住了 per-call 路径

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 这三个文件是同一个 SDK 里为同一目的写的三块适配层，**却对「非正数怎么办」
  给出三种不同答案**：genkit 有 `if count <= 0 { count = r.count }` 兜底、eino 与
  langchaingo **完全没有校验**。更关键的是 **genkit 的兜底本身是半截的**——它只作用于
  `Retrieve` 收到的**每次调用**的 `input.Count`，而兜底的落点 `r.count` **从未被校验**；
  于是构造函数传入负数时，兜底「回退」到的正是那个负数，**原样发上 wire**。
  测试名 `TestRetrieve_NegativeCount_FallsBackToDefault` 读起来像「负数已被处理」，
  但它把**构造函数传的是合法值 3**、只测 per-call 分支——**真正漏的那条路径无覆盖**。
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
- **复核记录**: [`…-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮；Problem / Status 留本文件）。
### P2-37: 四家 demo 的 `/chat` 在方法、输入位置、响应结构与语义上全部分歧——而这个分歧被 Java demo 自己的 Javadoc 写明后搁置

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 四家 demo 的端点**名字**经第 235 轮集合对拍已确认 23 个里 21 个同名，
  但**方法这一层从未被比对过**。补上后 `/chat` 暴露出四路分歧：

  | Demo | 方法 | 入参位置 | 响应 | 实质 |
  |------|------|----------|------|------|
  | Go | POST（`checkMethod` 强制） | JSON body | `{response, project, timestamp, memoryContext?, experienceCount?}` | 回显 `Received: …`，**不记录** |
  | Python | POST | JSON body | 同上 | 回显，**不记录** |
  | JS | POST | JSON body | 同上 | 回显，**不记录** |
  | **Java** | **GET** | **查询参数** `?message&project&conversationId&useTools` | `{response, project, conversation_id}`，**无 `timestamp`、无 `memoryContext`** | **真实调用 LLM**，经 `CortexMemoryAdvisor` **自动捕获** |

  即四家共用一个端点名，却在**方法、输入载体、响应结构、行为语义**四个维度上各不相同。
- **分歧是「已知且被写下」的**：`ChatController` 自己的 Javadoc 明写
  「The Go, Python and JS demos all answer `POST /chat` with a JSON object」，
  **紧接着就改用 `@GetMapping`**——写下了差异却没有解决。
- **Status**: ⏸ **记录不修** —— 给 Java demo 增加 `POST` 映射属**公开端点契约变更**；
  而「Java demo 的 `/chat` 究竟该是真实 LLM 调用，还是与另三家对齐为薄回显」
  属**demo 定位的产品决策**。**文档层已先行更正**：Go demo README 原先写
  「照抄任一家其余 21 个端点的 curl **只会在这两个上 404**」——**过度承诺**，
  已改为区分「拼写一致」与「可互换」，并补上 `/chat` 的方法分歧与 405 实测输出。
  **四份 demo README 各自对自身 demo 的描述经核实均准确，未改。Demo 代码一字未改。**
- **复核记录**: [`…-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮；Problem / Status 留本文件）。
<!-- P2-38 已无条件解决，逐字迁入 2026-10-04_backend-review-resolved-3.md -->
### P2-39: `POST /api/import` 的外层 `@Transactional` 与逐行 catch 相撞——一行坏数据毁掉整批，逐行统计变成 500

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 该端点**专门收集逐行错误**（`stats.addError(result.message())`）并在响应里
  返回 `imported / skipped / errors` 统计——**但这层设计被事务语义彻底击穿**：
  1. `importSession` 是 `@Transactional`（默认 REQUIRED），**并入** `bulkImport` 的同一个事务；
  2. 行数据触发数据库异常（如 `content_session_id` 超 255 字符）时，异常穿出 `importSession`，
     Spring 的事务拦截器把**共享事务标记为 rollback-only**；
  3. 控制器第 2 层 `catch (Exception e)` **吞掉**该异常并继续循环、继续统计；
  4. 方法返回时提交，Spring 抛 **`UnexpectedRollbackException`（"Transaction silently rolled back"）**
     → 调用方拿到 **HTTP 500**，**逐行统计一个都没送到**，**整批合法行全部回滚丢失**。
  即：端点为「部分成功」设计的响应结构，在最需要它的场景下**完全不起作用**。
- **旁证与附带观察**: 逐字迁入 [`2026-10-05_backend-review-evidence-20.md`](../archive/2026-10-05_backend-review-evidence-20.md)（第 271 轮）——含「这个坑已被踩过一次、修过其中一个字段而 `varchar` 宽度至今未校验」的既有痕迹，以及 wire 名报 Java 字段名、`API.md` 三个 import 端点无字段清单这两处附带观察。
- **Status**: ⏸ **记录不修** —— 两种修法各改一项**已成文的对外契约**：
  ①去掉 `bulkImport` 的 `@Transactional`（与另外四个同族端点一致）→ 放弃
  `@Operation` 明写的 "in a single **atomic** transaction"；
  ②保留原子性但不再吞掉 rollback-only（重新抛出）→ 调用方仍拿不到逐行统计，
  只是从「假 500」变成「真 500」。**真正的修法需要先决定这个端点到底承诺
  「全有或全无」还是「逐行部分成功」——那是产品契约决策**。
  只补 `varchar` 宽度校验**不足以解决**：它只覆盖最常见的一种触发方式，
  而任何未来的数据库异常仍会重演整批丢失，且会让人误以为问题已解决。**后端代码一字未改。**

### P2-40: 四家 SDK 都能写入 prompts 与 summaries，却没有一家读得回来

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 这**不是缺陷而是能力缺口**，但它没有被任何一处写下来，容易被当成疏漏。
  两个端点都**活体可用**、都在 `API.md` 里有完整记载（`/api/prompts` 出现 8 处）、
  WebUI 都在用；而**四家 SDK 没有任何方法能调用它们，四家 demo 也都没有暴露对应端点**
  （三家 demo 里唯一的 "summar" 字样是统计字段 `totalSummaries`，不是端点）。
  与之形成鲜明对比的是**写的一侧齐备**：`POST /api/ingest/session-end`（会话结束即生成摘要）
  与 `POST /api/ingest/user-prompt` 四家**全部**有方法（Go/Python/JS/Java 的
  session-end 与 user-prompt 引用数分别为 3/3、4/3、6/6、2/2）。
  即：**SDK 用户可以产生摘要与提示词，却永远无法把它们读回来**——想读只能自己发 HTTP。
- **一处探针自身出错并先识别再采信**: [`…-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。
- **Status**: ⏸ **记录不修** —— 补一个方法是**新增公开 API**，按既定纪律
  「新增公开 API 留待项目决策、不单方面实施」。且这不是「某一家漏了」的缺陷：
  **四家完全一致地缺失**，因此它要么是有意的范围划定、要么是共同的疏漏，
  两种解读都指向需要项目层面拍板而非某轮自行补齐。
  **若将来实施**，需注意与既有分页约定对齐：这两个端点与 `/api/observations` 共用
  `Math.min(Math.max(1, limit), MAX_PAGE_SIZE)` 的钳制（`API.md` 已记载），
  且 `hasMore` 是**驼峰**而条目内字段是 **snake_case**——Go 的 DTO 已按此混合约定建模
  （`dto/observations.go:28` 有 ⚠️ 注记），新方法应复用同一约定。**四家 SDK 代码一字未改。**

### P2-41: `platform_source` 等四个字段后端每条观测都在返回、WebUI 也在按它过滤——而四家 SDK 既不暴露、也不接受过滤

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 与 P2-40 同族的能力缺口，但这次卡在**字段**层面而非端点层面。
  后端**每条观测都返回** `platform_source`（V18 为多平台追踪新增）、`content_hash`
  （V8 新增、P2-29 的去重键组成部分）、`relevance_count`（V17 反馈）与 `step_number`；
  `API.md` **把 `platformSource` 作为查询过滤器写进了文档**（4 处）。
  而**四家 SDK 无一在响应 DTO 上暴露这些字段，也无一在请求侧接受 `platformSource` 过滤**。
  实际后果很具体：**SDK 用户无法区分一条观测来自 Claude 还是 Codex/OpenClaw**，
  也无法按平台筛选——而这正是 V18 加这个字段的目的。WebUI 侧的
  `viewer-bundle.js` 已经在按 `platform_source` 过滤，所以「能用」只在浏览器里成立。
- **一处探针自身出错并先识别再采信**: [`…-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。
- **Status**: ⏸ **记录不修** —— 与 P2-40 同一判断：补字段属**新增公开 API**，
  且**四家完全一致地缺失**，说明要么是有意的范围划定、要么是共同疏漏，
  都需要项目层面拍板而非某轮单方面扩大某一家的 DTO。
  **若将来实施**，注意 `platform_source` 已经是列表端点的**过滤维度**而非纯展示字段，
  补齐时应同时覆盖**响应字段**与**请求过滤参数**两侧，否则只补一半仍然无法按平台检索。
  **四家 SDK 代码一字未改。**

### P2-42: JS SDK 的 `tsconfig.json` 把测试文件排除在类型检查之外——`npm run lint` 查不到测试里的任何类型错误

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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
  **验证方法**（首版区间短了 4 行，段尾补入 `-34`）：逐字迁入 [`…-33.md`](../archive/2026-10-06_backend-review-evidence-33.md) 与 [`…-34.md`](../archive/2026-10-06_backend-review-evidence-34.md)（第 282 轮）。

### P2-43: Python SDK 的两种调用风格对 `None` 的含义相反——dataclass 路径丢弃它、kwargs 路径原样发上 wire

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `CortexSessionContext.begin()` 是裸的 `CURRENT.set(new SessionInfo(...))`、
  `end()` 是裸的 `CURRENT.remove()`——**既无重入保护、也不保存/恢复**。
  advisor 每见到 `CONVERSATION_ID` 就无条件 `begin`，并在 `finally` 里 `end`。
  于是**外层已存在的作用域被覆盖、并在调用返回后被删除**。
  **探针实测**（一次性用例，未提交）：外层 `begin("outer-session","/outer/project")` 已激活时
  调 advisor，断言「外层应当存活」**失败**——sessionId 变 `unknown-session`、projectPath 变空串，
  而调用**内部**看到的是 advisor 自己的上下文：**内层正确、外层被毁**，**全程无异常无告警**。
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

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）；**第 278 轮再迁出**两处缺陷的逐行取证（grep 行号、活体 OpenAPI 路径枚举）→ [`2026-10-06_backend-review-evidence-30.md`](../archive/2026-10-06_backend-review-evidence-30.md)。
- **Problem**: 这条比一般的文档错误重要，因为它**削弱的是我自己每轮据以判断的验收门控**。两处缺陷叠加：① `cleanup()` 在全文件**只出现一次**（定义处），`main` 里没有调用、也没有 `trap ... EXIT`，`bash -n` 通过——**它是死代码**；② 它内部那行清理请求 `DELETE /api/memory/observations?project_path=...` **活体 404**（该前缀下只有 `/api/memory/observations/{id}` 一条路径），而 `|| true` 兜底把 404 吞掉，**这个失败永远不会让脚本失败**。
- **Status**: ⏸ **记录不修** —— 属脚本方向，不在本轮（Python SDK）的代码轮换内；且**若真把清理接上，Test 6 会切回 `not_found` 分支、累积数据会被删除**，属于会改变门控自身行为的改动，需在自己的轮次里单独做 A/B。
- **对既有结论的影响（必须如实记录）**: 第 249–251 轮的「EXTRACTION 25/0/0 全通过」**仍是 25 条全部通过**，但 **Test 6 走的是兜底分支**、**Test 14 的断言已因数据累积而恒真**。这不使任何一条已记录的修复失效（被修代码路径本就在别处被独立验证），但今后引用该数字须带上这两条限定（基线区块已写明）。
- **同族事实（已修）**: 同一幻影端点也出现在**设计文档** `phase-3-design/25.md:699`，已改为脚本真正使用的「先 `GET /api/observations` 取 id、再逐条 `DELETE /api/memory/observations/{id}`」并实跑验证（观测数 1 → 0）。该脚本本身**行为正确**，**只有验收脚本是坏的**。
### P2-47: `API.md` 双语把 `is_worktree` / `parent_project` 当正式字段记载并写进示例 body——而两者只进一条 `log.info`

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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
  **按断言清扫的逐处经过**（首版区间短了 4 行，段尾补入 `-34`）：逐字迁入 [`…-33.md`](../archive/2026-10-06_backend-review-evidence-33.md) 与 [`…-34.md`](../archive/2026-10-06_backend-review-evidence-34.md)（第 282 轮）。

### P2-49: `scripts/start.sh` 把后端 jar 的版本号钉死——而它是 TESTING.md 推荐的启动方式

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
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
- 已解决条目：正文 [`…-resolved-13.md`](../archive/2026-10-04_backend-review-resolved-13.md)（第 260 轮无条件已解决，按第 250 轮先例整体迁出）。
### P2-51: `projects` 只在**值里含逗号**时才生效——传单个值被静默忽略，与不传完全等价

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 判定是 `projectsParam.contains(",")`——**只有含逗号才走多项目分支**。
  传单个值会落进单项目分支、**该值被完全丢弃**，既不报错也不告警，
  返回结果与**根本不传 `projects` 逐字相同**；文档只写「comma-separated」，
  **没说单个值等于不传**。另有 `@Schema` 写 project **paths** 而
  `parseProjectsParam` 的 Javadoc 写 project **names**，对同一值给出两种定义。
- **Status**: ⏸ **记录不修** —— 改判定会**改变现有调用方的行为**（原本按 `project_path`
  生成、改后按该值生成），属语义变更，需项目拍板。
  **文档侧已于第 254 轮更正**（`@Schema` 与 API.md / 中文版的逐字改动）→
  [`…-38.md`](../archive/2026-10-06_backend-review-evidence-38.md) 第 2 块（第 292 轮逐字迁出）。

### P2-52: `target/` 里残留 10 个**源码已删**的测试类——其中一个仍在失败，使 `mvn test` 退出非零
- 已解决条目：正文 [`…-resolved-13.md`](../archive/2026-10-04_backend-review-resolved-13.md)（第 260 轮无条件已解决，按第 250 轮先例整体迁出）。
### P2-53: Go SDK 的 `WithTimeout` 把「太小的值」重置成**默认最大值**——请求 50ms 实际得到 30s
- 已解决条目：正文 [`…-resolved-13.md`](../archive/2026-10-04_backend-review-resolved-13.md)（第 260 轮无条件已解决，按第 250 轮先例整体迁出）。
### P2-54: Python SDK 另有两处裸 TypeError——且既有测试的 docstring 早已写明我踩的那个坑
- 已解决条目：正文 [`…-resolved-13.md`](../archive/2026-10-04_backend-review-resolved-13.md)（第 260 轮无条件已解决，按第 250 轮先例整体迁出）。
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
- **不修的理由**: 修哪一边都是**改动 HTTP 对外契约**，而方向无法由证据确定——
  若收紧 Python + Java，就与它们声称要对齐的后端**背离**；
  若放宽 Go + JS，等于正式认可后端这个由 `Integer.decode` 带来的**意外行为**为契约。
  二者都属「对外契约变更」，按既定规则**记录不单方面实施**。
  实际影响低（同形字符数字是很不可能的输入），但**这条不变式正是四个 demo 存在的理由**，
  故必须留档。**修之前需要一次产品决定**：整数文法是否只认 ASCII。
- **Status**: ⏸ 记录不修（待产品决定：整数文法是否仅限 ASCII 数字）。本轮已把四条代码路径与活体后端全部实测完毕，无需再取证。

### P2-56: Java demo 里**三个**控制器把**六个**数值参数整个绕过了共享校验类——**而那条 Javadoc 只点名了其中一个**

- **Scope**: 第 259 轮原始条目（逐字）见 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)；
  **第 282 轮的范围更正与完整活体对拍**（用方 3 个文件 5 处 / 绕过 3 个控制器 6 个参数、六组请求的 HTTP 与 body 原文、
  `-1` 与 `16` 的落库记录）见 [`2026-10-06_backend-review-evidence-31.md`](../archive/2026-10-06_backend-review-evidence-31.md)。
- **Problem**: `DemoParams` 的 Javadoc 原本写着「…**so this rule is the only thing
  that can decide what a value means**」。**这句话对三个控制器是假的**：
  `boundedInt` 的调用点只落在 `SearchController` / `ObservationsController` / `ExtractionController`
  **三个文件、5 处**，而 `ExperiencesController`（`count`、`maxChars`，`Integer`）、
  `MemoryController`（两个端点的 `count` 与 `maxChars`，原生 `int`）、
  `SessionLifecycleController`（`promptNumber`，原生 `int`）**共六个数值参数**全部直接绑定，
  再在方法体里手写范围检查。后果是**同一个进程内部出现两种 400**：`InvalidParamAdvice` 只匹配 `InvalidParam`，
  **无人处理 Spring 的 `MethodArgumentTypeMismatchException`**（`DemoErrors` 只管后端异常），
  故两种 400 的**状态码相同、body 形状完全不同**；且**六个参数全部接受十六进制**，与文法契约相反。
- **Status**: ⏸ 记录不修（第 282 轮已把 `DemoParams` 的 Javadoc 按实测更正为完整枚举，零行为变化；
  实现待 P2-55 的文法决定）。**本条目此前低估了范围**——原文记「第四个控制器、两个参数」，实测为三个控制器、六个参数，原条目已逐字迁档。

### P2-57: Java SDK 的默认 base URL 是四家里唯一用主机名的——而后端**只绑 IPv4 回环**，一个 JVM 开关就能把它变成连不上

- **Scope**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Problem**: 四家 SDK 的默认端点**本应一致**，实测却是 **3:1** 而非对称——**Java 是唯一的异类**，
  用主机名 `localhost:37777` 而另三家一律 `127.0.0.1:37777`；而后端 `application.yml:3` 写的是
  `address: ${SERVER_ADDRESS:127.0.0.1}`，**只监听 IPv4 回环**。本机解析顺序实测 `::1` 在前，
  今天能通**只是因为 HTTP 客户端做了地址族回退**。
- **实测证据**（四家对拍表、文件行号、`lsof` 与 `[::1]` 直连结果）: 逐字迁入
  [`2026-10-05_backend-review-evidence-21.md`](../archive/2026-10-05_backend-review-evidence-21.md)（第 271 轮）。
- **不修的理由**: 改一行即可（把默认值换成 `127.0.0.1:37777`），且从证据看方向明确：
  它会让 Java 与另三家及后端自身的 `server.address` 一致，
  且在任何「当前默认值可用」的环境里新默认值同样可用。
  **但它改的是已发布 SDK 的公开默认端点**——唯一会被它影响到的情形，
  是某台机器上 `localhost` 与 `127.0.0.1` 指向**不同的后端**（那本身已是矛盾配置）。
  即便风险极小，它仍属**对外契约变更**，按既定规则**记录不单方面实施**，留待项目拍板。
- **同区域一处文档不一致：✅ 已解决**（第 271 轮核实）——`client.py` 的 Javadoc 示例原写
  `base_url="http://localhost:37777"`、与真实默认值矛盾，现已改为**显式说明**为何默认值用
  IPv4 字面量并**回指 P2-57**（`client.py:51-56`），该引用有效。原文与当时的探针失误一并归档。
- **Status**: ⏸ 记录不修（改公开默认端点属对外契约变更；证据与建议方向已齐备，修复只需一行）。

### P2-58: 四家 SDK 的响应 DTO **同缺**活体观测的 7 个字段——其中 3 个正是 V17 / V18 专门加的，而 Go 的 DTO 在 V17/V18 之后**还被改过**

- **Scope**: 逐字迁入 [`2026-10-04_backend-review-evidence-14.md`](../archive/2026-10-04_backend-review-evidence-14.md)（第 263 轮）；
  **第 277 轮再迁出** 7 字段 ↔ 迁移来源的逐行对照表与取样方法 → [`2026-10-06_backend-review-evidence-28.md`](../archive/2026-10-06_backend-review-evidence-28.md)。
- **Problem**: 取活体 `GET /api/observations?limit=1` 的一条真实观测（**34 个字段**），
  与四家响应 DTO 声明的字段名逐一比对，**四家同缺同样这 7 个**：
  `platform_source`（**V18 专门新增**）、`generated_by_model` 与 `relevance_count`（**V17**）、
  `content_hash`（V8）、`step_number`（V12）、`discovery_tokens`（V1）、`embedding_model_id`（V2）。
  Go 与 JS 的 `encoding/json` / Jackson **默认忽略未知字段**，故这些字段**被服务端发来、被 SDK 静默丢弃**。
- **不修的理由**: ①**跨四家**，不属于任何单一方向的轮次；②这 7 个里**性质不同**——
  `platform_source` 与 V17 两项是**面向使用方的能力**（V18 的存在意义就是让调用方知道一条记忆来自哪个平台），
  而 `content_hash` / `embedding_model_id` 很可能与三个向量列一样属**内部列、本就不该暴露**；
  ③**该暴露哪一部分无法由证据确定**。按既定规则**记录不单方面实施**。
- **Status**: ⏸ 记录不修（跨家 + 暴露范围待定；证据与字段来源已逐条落到迁移文件）。
### P2-59: Java demo 十个控制器把后端 4xx 变成 500，**其中两个方向相反**——凭空造 404，和把 404 放大成 500

- **Scope**: 逐字迁入 [`2026-10-04_backend-review-evidence-15.md`](../archive/2026-10-04_backend-review-evidence-15.md)（第 265 轮）；**第 274 轮再迁出**字面响应体与控制器普查原文 → [`2026-10-06_backend-review-evidence-23.md`](../archive/2026-10-06_backend-review-evidence-23.md)。
- **Problem**: 四家 demo 在本机同时起（Java 37778、Go 37779、Python 37780、JS 37781），对**同一个请求**打同一句话，
  结果是**两个相反方向**的分裂：①**放大**——后端对未知 session 返 404，Python / Go / JS 三个 demo
  **原样透传**，Java demo 返 **500** 且把后端那段 JSON 当字符串二次转义塞进 `error`；
  ②**凭空造**——方向相反：后端在「还没抽过」时返的是 **HTTP 200** + in-band `status:"not_found"`（活体实测，非 404），
  另三家透传 200，Java demo 判 `!result.isFound()` 后**自己造了个 404**。
  **字面响应体、40 个 catch 块普查与一个已排除的伪线索**（Go demo 路由改名是有意为之）逐字见归档。
  根因很干净：12 个控制器共 40 个 `catch (Exception e)` 块，**只有 3 个**走到 `DemoErrors`，**其余 10 个控制器一个都没有**。
- **已修（零行为变更）**: `DemoErrors` 类 Javadoc 已按现状改写并点名反例（12 控制器 /
  40 catch 块 / 3 个走 helper）→ [`…-36.md`](../archive/2026-10-06_backend-review-evidence-36.md) 第 5 块（第 291 轮逐字迁出）。
- **不修的理由**: 修它要改 10 个控制器的 catch 块，**改的是 demo 对外的 HTTP 状态契约**
  （500→404/400，且要决定 `error` 字段是否保留 SDK 前缀文本——Go/JS 加前缀、Python 不加，
  三家自己就不一致）。按既定规则**对外契约变更记录不单方面实施**。
  另注：`ErrorField` 的正则对 Spring 默认错误体（`{"timestamp":...,"error":"Bad Request"}`）
  会取出 `"Bad Request"`，**这条是后端本身就没给解释**，不算信息丢失，故不单列。
### P2-60: `CortexMemoryAdvisor` 的 `projectPath` 默认为空串——不设 `cortex.mem.project-path` 时，被捕获的提示**记下了却再也召回不了**

- **Problem**: `Builder.projectPath` 默认 `""`，自动装配又显式做 `getProjectPath() != null ? … : ""`；
  空串不是 `null`，能通过 `UserPromptRequest.toWireFormat()` 的 null 判断，被当作 `"cwd": ""` 发出。
  **活体实测与规模**（后端返 200、行以 `project_path = ''` 落库、只有用空项目查才取得到；
  库中 `EMPTY-STRING` 仅 2 行且都是探针，多数形态是 `NULL`，2043 行 / 2011 会话）
  逐字见 [`2026-10-06_backend-review-evidence-27.md`](../archive/2026-10-06_backend-review-evidence-27.md)。
- **已修**: advisor 的 Javadoc 与两份 SDK README 均已按现状写明，**零行为变更**。
- **不修的理由**: 三种改法（`null` / `user.dir` / 拒绝记录）都改已发布 SDK 的公开默认行为；
  其中 `user.dir` 还会把提示归到用户并未选择的项目下，比现状更糟，故**不单方面实施**。
- **Status**: ⏸ 记录不修（文档已更正；行为变更待项目决定）。
### P2-48: gitignored 的 `CLAUDE.md` 端点表 25 条里有 9 条是活体 404 的幻影端点

- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `CLAUDE.md` 的端点表 25 条里有 **9 条幻影端点**，9 条**逐条实测为 404**；
  同区域另一处漂移：项目结构写「controller/ # 17 controllers」，**实测 13 个**。
- **实测证据**: 逐字迁入 [`2026-10-05_backend-review-evidence-16.md`](../archive/2026-10-05_backend-review-evidence-16.md)（第 267 轮）——含 9 条路径逐条清单、`/api/ingest` 实际四条，以及 service「28+」实测 29 属合法范围故不计。
- **Status**: ⏸ **记录不修** —— `CLAUDE.md` 是 **gitignored 的本地文件**
  （`git ls-files` 未跟踪、`git check-ignore` 命中），改动**不会进入版本控制**，
  且「是否取消其 gitignore」本身仍是待用户决策事项；不在本轮静默修改。
  **待该决策落地后**，修法即按 `API.md` 的真实路径逐条更正这 9 行；其中
  `ingest/session-start`、`memory/save`、`context/observations` 三条
  **须先确认是被重命名还是从未存在**——若是后者则是纯粹删除。
### P2-61: `TestController` 的 `@Profile("!prod")` 指向一个本仓库**不存在的 profile**——它承诺的生产隔离从未生效
- **Scope / Evidence**: `backend/src/main/java/com/ablueforce/cortexce/controller/TestController.java:26-27`。
- **Problem**: 类级 `@Profile("!prod")` 与紧邻的 `@Tag` 描述都声称这些端点「Only available in
  non-production environments」。但**本仓库只有 `dev` 与 `prd` 两个 profile**——只有
  `application.yml` / `application-dev.yml` / `application-prd.yml`，**没有 `application-prod.yml`**；
  全仓 `SPRING_PROFILES_ACTIVE` 只出现三个值：`prd`（`docker-compose.yml:52` 默认）、`dev`
  （两个 e2e 脚本）、以及构建说明里「不设」。`!prod` 在这三种情形下**全部匹配**，即该门控
  **永不排除任何东西**；它唯一会生效的场景（`SPRING_PROFILES_ACTIVE=prod`）恰恰是**没有对应
  配置文件**的场景。后果是 `prd` 部署下三个**会实际消耗 LLM / 嵌入配额**的调试端点照常开放。
  活体三端点响应逐字见 [`…-36.md`](../archive/2026-10-06_backend-review-evidence-36.md) 第 6 块（第 291 轮逐字迁出）。文档侧同源两处：`docs/ARCHITECTURE.md` 的
  API 分层表列出 `/api/test/*` 时未提任何 profile 限定；P2-28 的 Problem 段称该门控
  「是正确的」据本条证据需要修正。
- **Status**: ⏸ **记录不修** —— 把它改成 `!prd` 会让三个端点在**默认 compose 部署下消失**，
  属**对外契约变更**；且本轮代码方向为 Python SDK，后端不在本轮范围内。**本轮未改任何后端代码。**
### P2-62: `ProjectFilterService` 的 `~username` 展开**丢弃用户名**、改写到当前用户家目录——Javadoc 说的是另一回事
- **Scope / Evidence**: 逐字迁入 [`2026-10-06_backend-review-evidence-23.md`](../archive/2026-10-06_backend-review-evidence-23.md)（第 274 轮）。
- **Problem**: 原 Javadoc 写 "Handles both `~` (current user) and `~username` (specific user)
  forms"，行内注释写 "expand to that user's home (best effort)"——**代码里不存在 resolve 分支**：
  `replaceFirst("^~" + username, userHome)` 把 `~username` **整段**替换成**当前**用户的家目录，
  用户名被**静默丢弃**；且 `username` 未转义即拼进**正则**。
  **反射实测表**（`~/proj` / `~alice/proj` / `~alice` / `~a.b/proj` 四例）逐字见归档。
- **影响面**：该类**未注册为 Bean**、生产代码**零引用**，类 Javadoc 自陈 "not currently wired
  into any processing pipeline" —— **当前影响为零**；风险是有人照该 Javadoc 接上后静默改写路径。
  **测试头曾声称覆盖 "expandHomeDirectory edge cases"，而全文零个 `~` 用例**。
- **Status**: ⏸ **记录不修** —— 正确的 `~username` 解析属**设计决策**，且**无生产调用方可验证**。
  **已修（零行为变更）**：Javadoc 改为如实描述并附实测结果；测试头的不实声明已更正，并写明
  **为何不补测试**——补了就等于把可疑行为钉死。后端 **167 测试全绿**、`mvn package` EXIT=0。
### P2-71: 12 个读方法的 `or {}` 把「存在但不是 JSON 对象」的响应体**静默变成空结果**，且对标量抛的异常**逃出 SDK 层次**
- **Scope / Evidence**: `client.py` 共 **12 处** `or {}`；四家对照表与 144 次 `from_wire` 实测逐字见 [`…-37.md`](../archive/2026-10-06_backend-review-evidence-37.md)（第 292 轮）。
- **Problem**: `_request_json` **不校验解码结果类型**。`or {}` 于是把**存在且非空**的 `[]`/`null`/`0`/`false`/`""` **静默变成全默认值 DTO**，调用方无法区分「后端确实没有」与「后端回了垃圾」；`"oops"`/`42` 则抛**裸 `AttributeError`**，**不在 `CortexError` 层次内**。四家实测：**Python 是唯一对标量抛非 SDK 异常的**（Go 给类型化错误，JS 对标量静默、对 `null` 抛裸 `TypeError`，Go 与 Python 都对 `null` 静默）。**字段级类型错不是问题**（11/11 逐字段污染全解析成功）。
- **Status**: ⏸ **记录不修** —— 修法是让 `_request_json` 区分「204/零长度」与「存在但非对象」，前者仍返 `None`、后者抛 `CortexError`；但这会把今天**静默返回空结果**的输入改成**抛错**，属错误路径行为变更，且 `or {}` 本身**撑住「后端真的什么都没返回」这一合法场景**、不能简单删。**未按失实陈述改写文档**：docstring 与 Python README 都只声称「**不可解析**的体抛错」，**字面为真**（HTML 确实抛，三家实测均抛），只是未覆盖这一类。

### P2-72: JS SDK 的 9 个解析方法把 2xx 的 `null` 响应体变成**裸 `TypeError`**——而同一 SDK 的另外 5 个方法静默返回默认值
- **Scope / Evidence**: `client.ts` 13 处 `as Record<string, unknown>`（**9 处**整体断言后立即取属性、**2 处**用 `Array.isArray` 真检查）；**84 次调用**矩阵见 [`…-39.md`](../archive/2026-10-06_backend-review-evidence-39.md)（第 293 轮）。
- **Problem**: **P2-71 的 JS 版，而 JS 还自相矛盾**。同一个 2xx + `null` 响应体，**9 个方法抛裸 `TypeError: Cannot read properties of null (reading 'items')`**，**5 个静默返回默认值**；其余四种非对象体（`[]`/字符串/数字/`false`）**14/14 静默**。该 `TypeError` **不在导出的两个异常类内、也不带 `cortex-ce:` 前缀**——而 SDK 每一处自有消息都带，按前缀过滤日志的调用方**看不到它**。**根因是 TypeScript 类型断言运行时是 no-op**，而同文件 234/486 行用的却是真正的 `Array.isArray` 检查。
- **Status**: ⏸ **记录不修**（同 P2-71：加运行时校验属错误路径行为变更）。**跨家**：**Go 是唯一对全部非对象输入都给类型化错误的**。**核实为真、不记**：10 MB 两道守卫与 `utf8ByteLength` 实现正确且 **README 已完整文档化其裸 `Error` 选择及理由**；`clearTimeout` 在 `finally`；HTML 解析失败抛裸 `Error` 属 README 已确立的约定。

### P2-73: 三份配置的 `logging.level.com.claudemem` 全部指向**已经不存在的包**——dev profile 的应用调试日志开关**从未生效**

- **Scope**: `backend/src/main/resources/` 三处——`application.yml:137`（INFO）、`application-dev.yml:33`（**DEBUG**）、`application-prd.yml:18`（INFO）。
- **Problem**: 第 294 轮给 demo 绑回环时顺带发现 `application.yml` 仍在配置 `com.claudemem` 的日志级别，遂全仓追查。
  **该包已不存在**：`backend` 等 **9 个模块中声明 `package com.claudemem` 的文件数为 0**，83 个后端源文件**全部**是 `package com.ablueforce`；
  打包产物 `cortex-ce-0.1.0-beta.jar` 内 `com/claudemem/` 条目 **0**，**活体 37777 进程的 classpath 上也是 0**。
  而真正的 `com.ablueforce` 在**任何**日志配置里都没有级别。**后果按 profile 分级**：
  `prd` 与默认档是 INFO、恰好等于 Spring Boot 默认值，故**看不出任何异常**；**只有 dev profile 是 DEBUG——
  那一档本来就是为调试准备的，它的另外三个 key（`org.springframework.ai` / `org.springframework.web.client` / `org.springframework.http`）
  都是真实第三方包、照常生效，唯独应用自身这一条是死的**。于是用 `--spring.profiles.active=dev` 排障的人能拿到
  Spring AI 的 HTTP 明细日志，却**一条应用 DEBUG 都看不到**，且没有任何迹象指向「配置写错了」。
  **活体双向实测**（37790，两次仅差 `com.claudemem`→`com.ablueforce` 这一处）：

  | | DEBUG 总数 | 来自 `com.ablueforce.cortexce` | 来自 `org.springframework.ai`（未改，作对照） |
  |---|---|---|---|
  | 修复前 | 3 | **0** | 1 |
  | 修复后 | 2228 | **2225** | 1 |

  对照组两次都是 1，**正是它让这个断言有意义**——弱版本「有没有出现 DEBUG」在修复前那次也会通过（总数 3 ≠ 0）。
- **Status**: ✅ **已修**（三处各改一个词）——`com.claudemem` → `com.ablueforce`。
  `application.yml` / `application-prd.yml` 两处 INFO **与默认值相同，行为零变化**；
  `application-dev.yml` 的 DEBUG **自此真正生效**（新增应用 DEBUG 输出），这是该档配置**一直在声称要做的事**。
  **⚠️ 需知悉的副作用**：**本机常驻的 37777 实例正是以 `--spring.profiles.active=dev` 运行的**，
  下次重启它会开始输出应用 DEBUG 日志（约 2200 行量级）。
  **若不希望 dev 档变吵，把 `application-dev.yml` 那一行改成 `INFO` 即可**——那是口味选择，不是缺陷，留给使用方决定。
  验证：完整验收在**含本轮改动的构建**上跑过（见 health-check 报告的新鲜度论证），回归 45/0/1、Phase 3 25/0/0，基线推进。

### P2-74: 四家 SDK 的重试**默认极性三比一不同**——Go / Python / JS 是 fail-closed，**只有 Java 是 fail-open**；而这一分歧此前无处记载

- **Scope**: Java `CortexMemClientImpl.isRetryable`（`:811`）、Go `error.go:201 IsRetryable`、
  Python `error.py is_retryable`、JS `errors.ts:129 isRetryable`。
- **Problem**: 四家在**状态码规则上完全一致**——都重试 **429/502/503/504**，都**不重试 500**
  （Go 侧由 `isTransient` 委派给共享的 `IsRetryable`，见 `client_impl.go:443`）。
  分歧在**无法识别的异常**上：

  | SDK | 未识别的异常 | 措辞 |
  |---|---|---|
  | Go `IsRetryable` | **false** | 「The default is "not retryable": an error is only retryable when it is positively identified as transient」 |
  | Python `is_retryable_error` | **false** | 「Passing anything else returns `False`, matching the **fail-closed** rule」 |
  | JS `isRetryable` | **false** | 末尾 `return false` |
  | **Java `isRetryable`** | **true** | 「Non-HTTP errors (network failures, timeouts) are **always worth retrying**」 |

  **差异由运行时决定、不是疏忽**：Go 有 `net.Error`、JS 有 fetch 的 `TypeError` 可供匹配，
  fail-closed 对它们是安全的；而 `RestTemplate` 把连接失败抛成 `ResourceAccessException`
  ——一个**普通的非 HTTP 异常**——若在 Java 侧也 fail-closed，**恰恰会把它本该覆盖的那类错误静默变成不重试**。
  **跨 SDK 移植重试逻辑时的实际后果**：来自 Java 客户端的一个意外异常会被带退避重试到 `maxRetries` 次才浮现，
  同样的异常在其余三家**首次尝试即失败**。
- **⚠️ 判读纪律**：原先怀疑 Java 那句 `// Matches Go SDK isTransient() for consistent behavior across SDKs`
  是**失实陈述**，**逐字重读后撤回该判断**——该注释紧贴在四个状态码的 return 之上、
  Javadoc 那句紧贴在「排除 500」之上，**两者各自限定的范围内都成立**。
  按既定规则**「遗漏 ≠ 失实」**：此处是**分歧未被记载**，不是**说错了**，故不按失实陈述处理。
- **Status**: ✅ **已按准确描述补注**（`isRetryable` 的 Javadoc 新增一段，写明默认极性的分歧、
  运行时成因与移植后果；行内注释同步改为「四个状态码与 Go 完全一致，**但下面的 fall-through 刻意不一致，原因见 Javadoc**」）。
  **纯注释、零行为变更**（diff 非注释行 **0**）。**行为本身不单方面改动**：
  把 Java 改成 fail-closed 会让网络错误**不再重试**（真实回归），把另三家改成 fail-open 则更差——
  两边都是行为变更，按既定规则**记录不实施**。**若要统一，需要先决定哪种极性为准。**

### P2-75: 单条观测的 PATCH / DELETE **对畸形 id 返 400**，而 API 文档与 OpenAPI 注解都只把 400 写成「请求体字段类型错」

- **Scope**: `MemoryController.java:277`（`@PatchMapping("/observations/{id}")`，`@PathVariable UUID id`）、
  同文件 `:427`（`@DeleteMapping`）；文档侧 `docs/API.md:593` 起的中英双语小节 + 两处 `@ApiResponse` 注解。
- **Problem**: `@PathVariable UUID` 在 id 不可解析时由 Spring 抛转换失败，**返回 400**，
  **与「请求体字段类型错」是完全不同的成因，却共用同一个状态码**。**活体实测**（37777）：

  | 请求 | 实测 |
  |---|---|
  | `PATCH /api/memory/observations/not-a-uuid` | **400** `{"status":400,"error":"Bad Request","path":"…"}` |
  | `DELETE /api/memory/observations/not-a-uuid` | **400** 同上 |
  | `PATCH /api/memory/observations/00000000-…-000000000000`（格式合法、不存在） | **404**（空体） |
  | `POST /api/memory/feedback` `{"observationId":"not-a-uuid"}`（id 在**体**里） | **400** `{"error":"Invalid observationId format: not-a-uuid"}` |

  文档把 `400` 写成「**Invalid field types in request body**（e.g. `title must be a string`）」、
  `404` 写成「Observation with given UUID not found」——**读起来就是「id 写错属于 404」**。
  实际不是：**path 里的 id 写错是 400**。**按 404 当「不存在」来写的客户端会落到通用错误分支。**
  全库检索确认：**中英双语 API 文档从未提及这一情形**（`not-a-uuid` / `malformed id` / `畸形` 均 0 命中）。
  **注意归因**：`MemoryController` 自己的 `@ApiResponse` 注解用的也是同一句窄措辞，
  **所以 API.md 忠实镜像了注解——缺口源自代码里的契约描述，不是文档与代码不一致。**
- **⚠️ 一处差点误记、已撤回**：初判为「后端对畸形 id 返 404、demo 注释失实」——**探针打错了路径**。
  `MemoryController` 类级是 `@RequestMapping("/api/memory")`，真实全路径为
  **`/api/memory/observations/{id}`**；我探的 `/api/observations/{id}` **根本不存在**，
  那个 404 是**路由未匹配**而非 id 校验。改打正确路径后得到 400，
  且**第 102 / 201 轮早已裁决过同一件事**（那句 `Invalid observationId format` **只对 `submitFeedback` 成立**）。
  **若照字面采信，会写下一条完全错误的「失实陈述」。**
- **Status**: ⏸ **记录不修** —— 要改就得同时动**控制器注解与中英双语文档**，
  而注解是**对外发布的 API 契约描述**；且此处是**描述偏窄**（400 的成因少列一种）、
  **不是陈述错误**，按既定规则「**遗漏 ≠ 失实**」不单方面改写对外契约。
  修法若要采纳，最小形态是给两处 `400` 补一句「或 path 中的 id 不是合法 UUID」。
### P2-76: demo 40 个 catch 块里只有 3 个做后端 4xx 直通，`PATCH /demo/session/user` 因此把 404 报成 500

- **Scope / Evidence**: `examples/cortex-mem-demo/.../SessionLifecycleController.java:171-175`（缺陷点）、
  `:151-176`（端点）；已做直通的三个点：`FeedbackController.java:75-79`、
  `ObservationsController.java:324-328` 与 `:352-355`；`DemoErrors.clientStatus` 为共用机制。
- **Problem**: `FeedbackController.java:68-74` 的注释把理由写死了——不直通就会
  「把一个缺失的观测报成 500，也把打错的 id 报成 500，既误报状态又丢掉解释」，
  并写明「The Go, Python and JS demos pass a backend 4xx straight through」。
  **这个机制只落在了 3 个 catch 上；demo 全部 40 个 catch 里其余 37 个仍一律转 500。**
  活体实测（demo 37778，后端 37777）：

  | 请求 | 后端直打 | 经 demo |
  |---|---|---|
  | `PATCH /api/session/no-such/user` | **404** `{"error":"Session not found: …"}` | **500** `{"error":"Failed to update session user: 404 Not Found: \"{…}\""}` |
  | `POST /demo/feedback` 未知 observationId（**对照组**） | 404 | **404** ✓ 直通生效 |

  对照组与缺陷点在**同一个 JVM、同一次运行**内，证明这不是 `clientStatus` 失效或后端行为漂移，
  而是该端点没有使用它。打错一个 session id 得到 500，等于告诉调用方「你把服务器弄坏了」。
  **其余 37 处不可达**：`ManagementController` 与 `MemoryController` 的入参都是自由文本
  （project/task/count/maxChars，demo 自校验），后端按自由文本存不存在记录，
  调用方无法靠输入构造出一个后端 4xx——所以实际暴露面是**这一个端点**，不是 37 个。
- **Status**: ⏸ **记录不修** —— 让已发布 demo 端点的响应码从 500 变成 404 属对外行为变更。
  修法若采纳，最小形态是在 `SessionLifecycleController.updateSessionUser` 的 catch 里
  套用 `FeedbackController` 已有的 6 行模式（同仓库已有现成写法，不需新机制）。
### P2-77: 四个 `/memory/*` 端点把「参数存在但为空」报成 **500**，而「参数缺失」是 400——同一类调用方错误，两个状态码

- **Scope / Evidence**: `examples/cortex-mem-demo/.../MemoryController.java:55-72`（`/memory/experiences`）、
  `:74-84`（`/memory/icl`）、`:121` 起（`/memory/icl/truncated`）与 `/memory/experiences/filtered`；
  对照组同文件外的 `ExperiencesController` 两个 `/demo/*` 端点。
- **Problem**: 四者都用 `@RequestParam String task`（**必填**），且方法体里**都没有空白检查**，
  直接流入 SDK 的 `requireNonBlank`。活体实测（demo 37778）：

  | 请求 | 实测 |
  |---|---|
  | `GET /memory/experiences`（**不传** task） | **400**（Spring 必填校验） |
  | `GET /memory/experiences?task=`（**传空**） | **500** `{"error":"…: task must not be null or blank"}` |
  | `GET /memory/icl?task=` | **500** 同上 |
  | `GET /memory/experiences/filtered?task=` | **500** 同上 |
  | `GET /memory/icl/truncated?task=` | **500** 同上 |
  | `GET /demo/experiences?task=`、`GET /demo/iclprompt?task=` | **400** ✓ 正确的形态 |

  抛的是 SDK 的 `IllegalArgumentException`，被兜底 `catch (Exception e)` 吞成 500；
  `DemoErrors.clientStatus` 对它返回 `null`（本就不该走那条路），所以机制上**不是漏了直通，是缺了入口校验**。
  同一模块的 `/demo/*` 兄弟端点**自己校验了空白并返 400**，形态是对的。
- **⚠️ 一处中间断言先错后改**：初判为「`/memory/experiences` 返 400 而 `/memory/icl` 返 500，是两者不对称」。
  **那个 400 来自「参数缺失」，不是「参数为空」**；补测 `?task=` 后 `/memory/experiences`
  **同样返 500**。原判据站不住，**问题反而更整齐**：四个端点在「空」这一形态上完全一致地错。
- **Status**: ⏸ **记录不修** —— 同 P2-76，响应码变更属对外行为变更。
  修法若采纳，最小形态是在四个方法体开头各加一行空白检查（与同文件 `count`/`maxChars` 的既有写法同构）。
  **注**：`promptNumber` 无范围检查是同族但已单独立项（P2-69），本条只管「空白必填参数报 500」。
### P2-78: CORS 的 `allowedMethods` **漏了 PATCH**——按文档开启 CORS 后，两个 PATCH 端点对浏览器静默失效

- **Scope / Evidence**: `backend/src/main/java/com/ablueforce/cortexce/config/WebConfig.java:52-57`
  （`/api/**` 映射的 `allowedMethods`）；配置项 `claudemem.cors.allowed-origins` 定义于同文件 `:18`。
- **Problem**: 允许方法列表是 `GET, POST, PUT, DELETE, OPTIONS`——**没有 `PATCH`**，
  而活体 `/v3/api-docs` 明确有**两个 PATCH 端点**：
  `PATCH /api/session/{sessionId}/user` 与 `PATCH /api/memory/observations/{id}`（活体方法分布
  `GET 37 / POST 25 / PUT 1 / PATCH 2 / DELETE 2`）。**跨域预检按允许清单判定**，
  清单里没有的方法**直接 403、不带任何 CORS 头**。
  **实测对照（第 311 轮，37790 开启 CORS / 37777 未开启）**：

  | `Access-Control-Request-Method` | 37790（已开启 CORS） | 37777（未开启） |
  |---|---|---|
  | `GET` / `POST` / `PUT` / `DELETE` / `OPTIONS` | **200** + `Allow-Methods` 头 | **403** |
  | **`PATCH`** | **403，无任何 CORS 头** | **403** |

  **对照组是关键**：37777 上 GET 与 PATCH **同为 403**，说明 37790 上 PATCH 的 403
  **不是「CORS 没开」那个基线**，而是**被允许清单单独拒绝**——同一实例上 GET 200 / PATCH 403
  就是判别实验本身。
  **今天不可触发**：`claudemem.cors.allowed-origins` **全仓从未被设置**
  （只在 `@Value` 默认值与 `docs/drafts/spring-ai-integration-plan.md:138` 出现；
  `application*.yml` / `docker-compose.yml` / `.env.example` / 脚本全部零命中），
  默认空值 → `allowedOrigins` 空数组 → **所有预检一律 403，CORS 默认关闭（安全默认成立）**。
  但那份 draft **明确指导浏览器前端用户去配置它**，照做之后恰好丢掉这两个端点。
- **Status**: ✅ **已修（第 311 轮）** —— `allowedMethods` 补入 `"PATCH"`。
  **按既定规则归类为「纯加宽」而单方面实施**：它只对**运维已显式配置的 origin** 生效，
  不改变任何现有客户端的行为（原先被拒的调用变通，早先能通的调用不受影响），
  且不引入有意义的攻击面（能跨域调 GET/POST/PUT/DELETE 的 origin 本就比 PATCH 权限更高）。
  **修后实测**（重启 37790 载入新 jar，`unzip -p ... WebConfig.class` 内确认含 `PATCH`）：
  六个方法**全部 200** 且 `Allow-Methods: GET,POST,PUT,DELETE,PATCH,OPTIONS`；
  另一条 PATCH 端点路径同样 200；**回归检查**：未授权 origin 仍 **403 且 ACAO 头出现 0 次**。
  `mvn -o compile` 与 `package -DskipTests` 均 EXIT=0。
  **按纪律进入连续 3 轮复查计数（第 311 轮为 1/3，第 312、313 轮各复查一遍）。**
- **第 312 轮复查 2/3 的结果：新发现问题，计数重置** —— 见 P2-79。

### P2-79: CORS 的凭据开关**只检查列表第 0 位**是否含 `*`——`*` 出现在别处时**整个 API 返 500**

- **Scope / Evidence**: `backend/src/main/java/com/ablueforce/cortexce/config/WebConfig.java:49`
  （`boolean allowCredentials = origins.length > 0 && !origins[0].equals("*");`），
  与紧邻的注释「Determine if we should allow credentials (only if not using wildcard)」。
- **Problem**: 该行**只判断 `origins[0]`**，而它自己的注释声称「only if not using wildcard」——
  **只要 `*` 出现在列表的任何非首位位置，意图就被违背**。
  Spring 的 `CorsConfiguration.validateAllowCredentials` 在 `allowCredentials=true` 且
  `allowedOrigins` 含 `*` 时**抛 `IllegalArgumentException`**，且该校验**每个请求都会走到**。
  **活体三配置对照（37790，逐次重启、改同一处配置）**：

  | `claudemem.cors.allowed-origins` | 普通请求 `GET /api/stats` | 预检 | 日志异常数 |
  |---|---|---|---|
  | `http://a.example,*`（**星号非首位**） | **500** | 403，无 CORS 头 | **每次请求抛异常** |
  | `*,http://a.example`（星号在首位） | **200** | 200 + **`ACAO: *`** | 0 |
  | `http://a.example`（无星号） | 200 | 200 + ACAO + 凭据 | 0 |

  **失效形态远重于「跨域不工作」：整个 API 返回 500**，连不带 `Origin` 的普通请求也是 500。
  异常原文：`When allowCredentials is true, allowedOrigins cannot contain the special value "*"…`
  **⚠️ 第 314 轮更正本条第二行（且更正方向是加重，不是减轻）**：
  初版记作「200，但**无 ACAO 头** → 浏览器照样拦截；两种错法不同，都错」。
  **那个 ACAO 断言是用坏探针测的**——`curl -o 文件` 存的是**响应体**，却去 grep 响应头，
  读到的永远是空。**第 313 轮已在同一段代码上栽过同一次并当场改正，本条当时未同步更正。**
  **用 `-D` 重测，结论相反**（37790，配置 `*,http://a.example`，原样无空格）：

  | `Origin` | 实测 |
  |---|---|
  | `http://a.example`（列出） | **200** + `Access-Control-Allow-Origin: *`，`ACAC` 头 **0** 次 |
  | `http://zzz-not-listed.example`（**未列出**） | **200** + `Access-Control-Allow-Origin: *` |

  即**第二种写法不是「坏了但安全」，而是对所有 origin 全开**——
  `allowCredentials` 被算成 false，Spring 便直接回显通配，**通配压过同列表里的具体源**。
  **所以本条不是「两种错法都错」，而是「一种把整个 API 打挂、一种把它对全网敞开」**，
  安全权重由此上升（初版低估了自己）。第 1 行的 500 与第 3 行的 ACAO **不受影响**：
  前者的 500 取自 `-w` 的状态码本身（非响应头 grep），后者出自第 311 轮用 `-D` 的测量。
  **今天不可触发**：该配置项全仓从未被设置（与 P2-78 同一条证据）。
- **⚠️ 为什么不能顺手改**：最直觉的修法（`*` 出现在任意位置就把 `allowCredentials` 置 false）
  **恰好制造出上表第二行那种结果**——Spring 回 `Access-Control-Allow-Origin: *`，
  **等于给所有 origin 开口**，比运维显式列出的那一个**更宽**。
  **第 314 轮的实测正是这句话的证据**：该行为并非推测，它就是 `*` 在首位时的实际表现。
  正确修法只有两条，都涉及策略决定：改用 `allowedOriginPatterns`（支持带凭据的通配），
  或**启动期拒绝这份配置并给出清晰错误**（fail fast）。
- **Status**: ⏸ **记录不修** —— 上条已说明「直觉修法是安全放宽」，本条属**安全策略决策**，
  不单方面实施。修法若采纳，建议 `allowedOriginPatterns` + 启动期对 `*` 与具体源混用给出显式告警。
- **对 P2-78 复查计数的影响**：第 312 轮为 P2-78 的第 **2/3** 次复查，
  **但在同一段代码里发现本条新问题 → 按既定纪律计数重置**，从第 312 轮重新记 1/3。

### P2-80: CORS 的 origin 列表**不 trim**——按文档教的「逗号分隔」写，**只有第一个域名生效**

- **Scope / Evidence**: `backend/src/main/java/com/ablueforce/cortexce/config/WebConfig.java:44-46`
  （修复前的 `allowedOrigins.split(",")`）；指导文档 `docs/drafts/spring-ai-integration-plan.md:138`
  「需配置 `claudemem.cors.allowed-origins`（**多个域名用逗号分隔**）」。
- **Problem**: `String.split(",")` **不会去掉分隔符两侧的空白**，于是第 2 个及之后的元素
  带着前导空格，成为字面量 `" https://b.example"`，与永不带前导空格的 `Origin` 头**永不相等**。
  **这不是报错，是静默失效**：
  **活体实测（37790，配置 `http://a.example, http://b.example`，逗号后一个空格）**：

  | 请求头 `Origin` | 实测 |
  |---|---|
  | `http://a.example` | **200** + `Access-Control-Allow-Origin: http://a.example` + `ACAC: true` |
  | `http://b.example` | **403 "Invalid CORS request"**，**无任何 CORS 头** |

  日志中 `IllegalArgumentException` **0 次**——**与 P2-79 是两种不同机制**
  （那条是每个请求抛异常导致全站 500，这条什么都不抛，只是第二个及以后的域名静默失配）。
  **为什么值得记**：文档教的就是「逗号分隔」，而人在逗号后打一个空格是极自然的写法，
  结果**第一个域名之外的全部失效且无任何线索**。
- **⚠️ 一处探针错，先质疑探针再采信数据**：初版探针用 `curl -o 文件` 后去 grep 响应头，
  读到的永远是空（`-o` 存的是**响应体**、`-D` 才是响应头），
  于是 a.example 一度显示「200 但无 ACAO」。**改用 `-D` 倒原始响应头后**，
  `Access-Control-Allow-Origin: http://a.example` **确实存在**——
  **数据没错，是探针错了**；改正后结论反而更硬（b.example 是确凿的 403）。
- **Status**: ✅ **已修（第 313 轮）** —— 新增 `private static String[] parseOrigins(String)`：
  `split(",")` → `trim()` → **丢弃空元素**。
  **按既定规则归类为「已损坏行为」而单方面实施**：它不是安全放宽——
  trim 只让**运维自己写进列表里**的那个域名真正生效，**不可能放行列表之外的任何 origin**
  （`d.example` 与 `evil.example` 实测仍 403）；原行为则是**静默忽略配置的一部分**。
  **修后实测**（重启 37790 载入新 jar，`unzip -p … WebConfig.class | strings` 确认含 `parseOrigins`），
  配置**刻意写成脏的** `'http://a.example, http://b.example , ,http://c.example'`：

  | `Origin` | 实测 |
  |---|---|
  | `http://a.example` / `http://b.example` / `http://c.example` | **200**，**各自回正确的 ACAO** |
  | `http://d.example`（未列出） | **403**，无 ACAO |
  | `http://evil.example`（未列出） | **403**，无 ACAO |
  | `ACRM=PATCH` | **200**，`ACAM=GET,POST,PUT,DELETE,PATCH,OPTIONS`（P2-78 未回退） |
  | 普通请求 `GET /api/stats` | **200** |

  异常数 **0**。`mvn -o compile` 与 `package -DskipTests` 均 EXIT=0。
- **⚠️ 第 314 轮复查 2/3：在本修复里发现一条加宽边 → 复查计数重置为 1/3**
  **实测（37790，配置 `' *'`，即星号前有一个空格）**：

  | `Origin` | 实测 |
  |---|---|
  | `http://anything.example` | **200** + `ACAO: *`，`ACAC` 头 **0** 次 |
  | `https://totally-unrelated.example` | **200** + `ACAO: *` |

  **对照修复前的行为**：`origins[0]` 会是字面量 `" *"`，既不等于 `"*"`（故凭据被算成开启），
  列表里又没有字面 `"*"`（故 Spring 不抛异常）——**结果是没有任何 origin 能匹配，对所有来源一律失效**。
  **trim 之后**它变成干净的 `["*"]`，于是走通配分支 → **对全网回显 `ACAO: *`**。
  即：**本修复把一个「什么都不匹配」的输入，变成了一个「什么都匹配」的输入。**
  **这与 P2-79 里那条「直觉修法」落到同一个结果上**（都是关凭据 → 回 `*`），
  **故本条不能独立结案**：trim 对「多源列表」是纯粹的好处，
  但它与「通配 + 凭据」这条策略问题**在边界上交汇**，
  **必须与 P2-79 一并决策**（`allowedOriginPatterns` 或启动期 fail fast）才能收口。
  **今天的实际风险为 0**：该配置项全仓从未被设置（与 P2-78、P2-79 同一条证据）。
- **✅ 第 316 轮复查 3/3：技术验证完成，本条的代码侧结案。**
  补测了前两轮**未覆盖**的两个边界，均正确：

  | 边界 | 配置 | 实测 |
  |---|---|---|
  | **CRLF**（多行环境变量） | `'http://a.example,\r\nhttp://b.example'` | 两个源**各自精确放行**并回**各自的** ACAO；未列出的 **403**；异常 **0** |
  | **退化输入**（只有分隔符与空白） | `' , '` | `parseOrigins` 丢弃空元素后得**空数组** → **CORS 退回全关的安全默认**（任意 origin 403、无 ACAO），普通请求仍 **200** |

  **三轮合计覆盖**：逗号后空格（313）、制表符 + 前后空白 + 空元素 + 通配（315）、
  CRLF + 退化输入（316）。**`parseOrigins` 在所有非通配输入上行为正确**。
  **仍然开放的不是本条的技术问题**，而是上条那条**通配 + 凭据的策略决策**（与 P2-79 合并待决）。
  **据此本条代码侧结案；若日后决定改通配策略，需连带复看本条的加宽边。**

### P2-81: `run-all-e2e.sh` 声称跑「全部」E2E 脚本并逐条列出 3 个排除项——**实际漏掉 13 个**，其中 7 个的前置与它自己完全相同

- **Scope / Evidence**: `scripts/run-all-e2e.sh:1-3` 与 `:18-20`（修复前的头注释）；
  `:125-138`（实际调用的 10 个套件）。
- **Problem**: 头注释原文是「Run **all local E2E test scripts** in one pass (excluding Docker
  suites and test-llm-provider.sh)」，并另起一节「**Excluded by design (per project convention)**」
  **逐条列出**三个：`docker-e2e-test.sh`、`docker-compose-test.sh`、`test-llm-provider.sh`。
  **两处都不成立**。`scripts/` 下共 **37** 个 `.sh`，其中测试类 **26** 个；
  **从未被它调用的是 16 个** = **头注释已声明的 3 个排除项** + **未声明的 13 个遗漏**：

  | 未声明的遗漏（13） | 声明的前置 | 本质 |
  |---|---|---|
  | `java-sdk-e2e-test.sh` / `python-sdk-e2e-test.sh` / `js-sdk-e2e-test.sh` | **仅「Backend service running (port 37777)」** | **与本脚本自身前置完全相同** |
  | `phase3-acceptance-test.sh` | **仅「Backend running on port 37777」** | 同上 |
  | `go-sdk-unit-test.sh` / `demo-v15-extraction-test.sh` / `performance-test.sh` | 无额外服务迹象 | 同上 |
  | `demo-v14-test.sh` / `demo-v15-test.sh` | 需 Java demo @ 37778 | 额外服务，**排除本身合理** |
  | `go-sdk-e2e-test.sh` | 需 Go demo @ 37779 | 额外服务，**排除本身合理** |
  | `js-demo-e2e-test.sh` / `python-demo-e2e-test.sh` / `codex-watcher-test.sh` | 需各自 demo / npm | 额外服务，**排除本身合理** |

  **⚠️ 本条第一版把 16 写成 13**：口径是用正则从 `run-all-e2e.sh` 全文里抓 `.sh` 名字，
  结果**连「Excluded by design」注释里提到的 3 个也算成了"已调用"**。
  重算后拆成「16 个未调用 = 3 已声明 + 13 未声明」才准确。**下表 13 行与「7 + 6」的拆分自洽。**

  **危害是「静默的假完整」**：照头注释理解，跑完这一条就等于跑完全部验收，
  实际上**三家的 SDK 套件与 Phase 3 验收一次都没执行**且**没有任何提示**。
  **附带的结构事实**：三个适配器（`eino`/`genkit`/`langchaingo`）是**独立 go.mod**，
  故从 `cortex-mem-go` 跑 `go test ./...` **根本不会执行它们**（实测只出 2 个包）。
  **CI 不构成补偿**：唯一 workflow `docker.yml` 只构建推送镜像，不跑任何测试。
- **Status**: ✅ **头注释已修（第 313 轮，零行为变更）** —— 按「失实陈述的修正可修」，
  头注释改为如实写明「跑的是哪 10 个、另外 13 个是什么、为何排除、单独怎么跑」。
  **未把脚本接进编排**：那会改变一条命令实际执行什么（且这些脚本有副作用、
  无法在本轮逐一验证自动运行安全），属需拍板的变更。
  **接不接、接哪些，留待用户决策。**
### P2-82: 三家 SDK 都给响应体设了 **10 MB 上限**，**只有 Python 完全没有**——`requests` 会把整个 body 缓冲进内存

- **Scope / Evidence**: `python-sdk/cortex-mem-python/cortex_mem/client.py:128-140`（`_request`）；
  对照 `go-sdk/cortex-mem-go/client_impl.go:173-175,243-250`
  与 `js-sdk/cortex-mem-js/src/client.ts:655-699`。
- **Problem**: 同一道安全阀，三家形态如下：

  | SDK | 上限 | 形态 |
  |---|---|---|
  | **Go** | **10 MB** | `MaxResponseBytes = 10 << 20`，**导出具名常量**；`io.LimitReader(body, Max+1)` 读超一字节再显式报错 |
  | **JS** | **10 MB** | `const maxSize = 10 * 1024 * 1024`，**三道检查**：读前查声明的 `Content-Length`、读后查 `text.length`、外加 UTF-8 字节感知的第三次 |
  | **Python** | **无** | `_request` 拿到 `requests.Response` 后**直接取 `resp.content`**，全路径零体积判断 |

  `grep -nE 'MAX_RESPONSE|max_response|10 \* 1024 \* 1024|10485760' python-sdk/cortex-mem-python/cortex_mem/*.py`
  **零命中**。`requests` 默认把整个响应体缓冲进内存，故一个超大响应（或拦截它的代理）
  **会在 Python 侧被完整读入**，而 Go 与 JS 会中止并显式报错。
  **这不是「少写了个常量」而是失效形态不同**：同一次异常后端响应，
  Go/JS 得到「响应体超过上限」的可诊断错误，Python 得到一次内存暴涨。
  **佐证这道阀是后加的且必要**：JS 的注释记着它是在实测「多字节字符使 `text.length`
  低估体积达上限的 1.5 倍」之后才补的第三道检查——即**它确实在真实场景下漏过**。
- **Status**: ⏸ **记录不修**，**且第 316 轮已下调定性（见下）** —— 给 Python 加上限会让**原本成功的超大响应变成抛错**，
  属**收窄已发布 SDK 的接受范围**。
- **⚠️ 第 316 轮更正定性：这不是「无人知晓的缺口」，而是**已双语记载的有意权衡**
  **上一轮定性为缺陷是错的**。`python-sdk/cortex-mem-python/README.md:229-238` 有一节
  「**Response Size Limit**」，中文版 `:229-238` 同节同步，内容逐条如下：
  - 明写「There is **no** response size cap in this SDK」；
  - 给出**原因**：「`requests` gives **no portable hook** for a streaming size check」；
  - 写出**后果**：「a very large response is bounded only by the memory available to your process」；
  - 给出**建议**：「Keep `limit` modest when searching or listing large observation sets」。
  **即技术事实不变（Python 无上限、Go/JS 有 10 MiB），但它的性质是「已记载的取舍」，
  不是「静默的缺口」**——与 P2-78（配错了没人知道）不同类。
  **仍然剩下的唯一问题是「要不要改这个取舍」**，那是**产品决策**（是否值得为 Python 引入
  流式读取并承担 `requests` 之外的复杂度），**不是补缺口**，维持不修。
  **对拍参考**：Go README `:227-235` 记着常量名、`10 << 20`、读 `Max+1` 字节与错误原文；
  JS README `:171-179` 记着报错文案并**主动澄清**「文案写 `10MB`，实际上限是 10 MiB = 10,485,760 字节」。
  **三家文档各自准确，双语同步。**
- **未证实的部分，不写**：本轮**未实测**「真的让 Python 侧 OOM」——
  上面是**代码路径层面的断言**（无上限检查、requests 会缓冲），
  **不是**「已复现的内存事故」。

### 加注（不改写归档）：归档条目 **30-2** 的跳过理由**已经过时**

- **原条目**：[`2026-09-30_backend-review-findings-history.md`](../archive/2026-09-30_backend-review-findings-history.md) 第 1894 行（表格行 `30-2`）：
  「`/api/logs` 和 `/api/logs/clear` 端点无认证/授权保护。日志内容可能包含敏感调试信息……
  ⭭ 跳过（**设计决策：服务绑定 localhost，外网不可达**；添加认证属于架构变更）」。
  **归档不得修改**，故在此加注。
- **该前提今天不成立**（第 317 轮实测）：`docker-compose.yml:88` 的端口映射是
  `"${SERVER_PORT:-37777}:37777"`，**没有主机 IP 前缀** → 发布到**所有网卡**；
  `:60` 另设 `SERVER_ADDRESS: 0.0.0.0`（即 P2-70）。后端**唯一的 Servlet Filter 是
  `MdcAutoFilter`**（关联 ID，**不是鉴权**），全仓无 `SecurityFilterChain`
  → **推荐的 Docker 部署下，同网段任何人可读日志、并可 `POST /api/logs/clear` 抹掉它**。
- **与 30-2 的关系**：**这不是新缺陷，是既有记录的前提失效**。
  原条目本身仍然成立（无认证属实），**只是「外网不可达」这条免责理由在 compose 路径下已不适用**。
  危害的措辞可以更准：`clear` 是**破坏性**端点（`LogsController:165` 用
  `Files.writeString(todayLog, "")` 截断），因此「可读」之外还有「可销毁」。
- **未验证的部分，不写**：本轮**没有调用** `POST /api/logs/clear`（它是破坏性的，
  且会毁掉正在用于关联的日志）——截断行为是**读码确认**（`:161-185`），**非实测**。
  **实测的只有只读部分**：`GET /api/logs?lines=3` 返回
  `totalLines=48297 / returnedLines=3 / files=['claude-mem-2026-10-06.log']`，
  且钳位与十六进制前缀解析与 `docs/API.md:2896-2897` **记载一致**（`0`→1、`-5`→1、
  `99999`→10000、`0x10`→16）——**故该控制器与对应文档本身零缺陷**。

### P2-83: `PATCH /api/session/{id}/user` —— **11 处仍用错路径变量名**，是第 157 轮那次清扫的残留

- **Scope / Evidence**: 活体 `/v3/api-docs` 权威写法
  `PATCH /api/session/{sessionId}/user`，`params=['sessionId']`；
  源码 `SessionController.java:294` 的 `@PatchMapping("/{sessionId}/user")`。
- **Problem**: 全仓 `.md` 仍有 **11 处**把该路径写成 **`/api/session/{id}/user`**，
  分布在 **10 个文件**：

  | 文件 | 处数 |
  |---|---|
  | `backend/README.md` | 1 |
  | `cortex-mem-spring-integration/README.md` / `README-zh-CN.md` | 1 + 1 |
  | `js-sdk/cortex-mem-js/README.md` / `README-zh-CN.md` | 1 + 1 |
  | `docs/DEPLOYMENT.md` / `DEPLOYMENT-zh-CN.md` | 1 + 1 |
  | `docs/api-json-naming-convention.md` | 2 |
  | `docs/go-sdk-guide.md` | 1 |
  | `docs/drafts/js-sdk-design.md` | 1 |

  **这不是新错误类**：第 157 轮已把同一处替换在**十个文件、十四处**全部更正
  （见 `patrol-rotation.md` 第 256 轮条目），**这批是当时漏掉的**。
  **危害与当年相同**：读者照抄去拼 URL 或按 `{id}` 做断言替换，会与真实路径模板不符；
  更实际的是**这类文档被生成工具消费时**，变量名错位会造成难以定位的失败。
- **⚠️ 刻意未动的同类写法**：`/api/memory/observations/{id}` 的变量名**确实是 `id`**
  （活体 `params=['id']`，`MemoryController:277`、`:427`），全库 **94 处**全部保留，
  **一个都没改**——第 157 轮已就此事立过规矩：**按断言清扫不等于按前缀清扫**。
- **Status**: ✅ **已修（第 317 轮）** —— 11 处全部改为 `{sessionId}`，
  属**失实陈述的更正**，中英双语一并处理。改后核验三项：
  **非历史文件残留 0 处**；**`/api/memory/observations/{id}` 仍为 94 处（未被误伤）**；
  `git diff --numstat` 恰为 **11 行增 / 11 行删**，无任何附带改动。
  **已刻意排除**：归档文件（不可修改）、`patrol-rotation.md`（历史记录，记的是当时做了什么）、
  health-check / doc-review / findings 三份工作文件（其中提到该字符串的是历史叙述，非断言）。

### P2-84: Go SDK 的 `base_url` 规范化**只去一个**尾斜杠，而另三家去全部 —— 同一份配置在四家里三成一败

- **Scope / Evidence**: 四家实现逐行对照 + 活体实测。
  | SDK | 位置 | 写法 | 去掉几个尾斜杠 |
  |---|---|---|---|
  | Python | `cortex_mem/client.py:86` | `base_url.rstrip("/")` | **全部** |
  | JS/TS | `src/client-options.ts:68` | `.replace(/\/+$/, '')` | **全部** |
  | **Go** | `client_impl.go`（原 `:122`） | `strings.TrimSuffix(cfg.BaseURL, "/")` | **仅一个** |
  | Java | `CortexMemClientImpl` | 交给 `RestClient.baseUrl()` 内部处理 | 不适用 |
- **Problem**: `TrimSuffix` 的语义是「删掉末尾**一个** `/`」。
  故 `WithBaseURL("http://host:37777//")` 规范化后仍是 `http://host:37777/`，
  与 `path` 拼接成 `http://host:37777//api/version`。
  **这不是理论问题——活体实测后端不折叠空路径段**：
  `GET http://127.0.0.1:37777//api/version` → **HTTP 404**，
  响应体为 Spring 的 `{"status":404,"error":"Not Found","path":"//api/version"}`。
  **即该配置下 Go SDK 的每一个请求都是 404**，而**完全相同的配置值在 Python 与 JS 里正常工作**。
  形态与第 300 轮记的「重试极性三对一」同型：**四家里三家行为一致、第四家单独不同**，
  而差异只在**多写一个斜杠**时才显形，故极难在正常使用中察觉。
  **既有测试只覆盖单个尾斜杠**：`client_test.go` 的 `TestNewClient_TrailingSlashNormalization`
  传的是 `server.URL + "/"`；**双斜杠此前无任何测试**。
  **文档侧亦无自陈**：Go SDK README 中英双语 `grep -i "trailing|尾斜杠|末尾斜杠"` **零命中**。
- **Severity**: 低——`http://host//` 属配置笔误，正常输入（无尾斜杠、单个尾斜杠）本就正确。
  但**失败形态是最坏的一种**：不是报错而是**静默的全量 404**，且**只在一家里发生**。
- **Status**: ✅ **已修（第 319 轮）** —— `TrimSuffix` → `TrimRight`。
  属**纯加宽 / 向后兼容修正**：**当前能工作的任何输入行为都不变**，
  受影响的只有那些**本来就 100% 失败**的输入，故可单方面实施。
  **补测一条**（`TestNewClient_DoubledTrailingSlashNormalization`）覆盖此前完全无覆盖的双斜杠。
  **双向注入验证**：保留修复后全绿 → 回退为 `TrimSuffix` 后**恰好**新测试失败、
  既有单斜杠测试仍通过 → 恢复后全绿。**是数据与断言互相印证，不是只跑通就算数。**

### P2-85: Python demo 注释称「四家与后端一致」，但后端**接受十六进制**而四家全部拒绝 —— 实测断言

- **Scope / Evidence**: `python-sdk/cortex-mem-python/examples/http-server/app.py` 的
  `_parse_int_param` 注释（`:141-146`）原文：
  > A regex pins the grammar to "optional sign, then digits" … **so all four demos match the backend**,
  > which rejects "1_0" with 400 (round 211 recheck).
- **Problem**: 该等价断言在**十六进制输入上不成立**。**活体实测**（对一个含 100 条观测的 project，
  后端为本轮自起的 37790 实例）：

  | 查询参数 | 后端实际行为 |
  |---|---|
  | `limit=10` | 返回 **10** 条 |
  | `limit=0x10` | 返回 **16** 条 ← **十六进制** |
  | `limit=0x5` | 返回 **5** 条 |
  | `limit=010` | 返回 **10** 条 ← **十进制**，不是八进制 |
  | `limit=1_0` | **400**（注释所举之例，确为真） |

  Spring 的 `NumberUtils` 把 `0x` 前缀当十六进制，故 `0x10` = 16 ——
  **`0x10`→16 与 `10`→10 不可混淆，属决定性证据**。
  而**四个 demo 全部拒绝** `0x10`：Go demo 自己在 `main.go:65` 写明
  「it still rejects "0x10" and "10abc"」，Python/JS/Java 三家则由 `[+-]?\d+` 这条文法排除。
  **故「四家一致」为真，「四家与后端一致」为假。**
- **按断言清扫的结果**：全库只有**这一处**写了「与后端一致」的等价断言。
  Go demo 那句**本身准确**（它只陈述自己拒绝什么，未声称与后端等价），故**未动**。
- **Severity**: 低——`?limit=0x10` 这类输入现实中几乎不出现，
  且 demo 比后端**更严格**本身不是危害。**但它是本循环迄今第一次在源码注释里发现的失实陈述**，
  而本项目的长期主题正是「宁可少说，不要说错」，故仍予更正。
- **Status**: ✅ **已修（第 320 轮）** —— 改为**限定范围**的准确表述：
  四家在**十进制文法上**互相一致、且与后端一致（并补上实测的 `010`→十进制 10），
  另起一段说明**十六进制是四家共享的刻意分歧**（后端接受、四家拒绝）。
  **纯注释改动**：13 增 / 2 删，经 `git diff -U0` 逐行核验**每一行都以 `#` 开头或为空行**，
  **无任何可执行行变更**；Python **453/453** 全绿。

### P2-86: API 文档把两条 DELETE 路由的路径变量写成 `{uuid}`，活体是 `{id}` —— 第 157/317 轮同类

- **Scope / Evidence**: **活体 `/v3/api-docs` 权威写法**（37777 实例）：
  ```
  DELETE  /api/summary/{id}      params=[('id', 'path')]
  DELETE  /api/observation/{id}  params=[('id', 'path')]
  ```
  源码 `ViewerSessionController.java:43,49` 亦为 `@DeleteMapping("/observation/{id}")` / `("/summary/{id}")`。
- **Problem**: `docs/API.md:1816-1817` 与 `docs/API-zh-CN.md:1812-1813` 把这两条写成
  **`/api/observation/{uuid}` 与 `/api/summary/{uuid}`**，**共 4 处**。
  这批文档由另一进程在 `6e5890d` 中新增，正落在本轮文档轮换方向内。
  **这不是新错误类**：第 157 轮修过 14 处、第 317 轮修过 11 处同一类（`{id}` → `{sessionId}`）。
  **成因可解释但仍为失实**：`deleteObservation(@PathVariable UUID id)` 的**参数类型**是 `UUID`，
  作者据类型写了 `{uuid}`，而**路由变量名**是 `id` —— **类型与变量名是两回事**。
- **危害与当年相同**：读者照抄 `{uuid}` 去拼 URL 或据其做模板断言替换，会与真实路径模板不符；
  文档若被生成工具消费，这类错位会造成难以定位的失败。
- **按断言清扫的结果**：全库（排除归档与 `node_modules` 第三方内容）**恰好这 4 处**，
  分布在 **2 个文件**。**该文件自身即自相矛盾**：`API.md` 全文路径变量普查为
  `{typeId}`×6、`{templateName}`×4、`{projectName}`×4、**`{id}`×3**、`{uuid}`×2、`{sessionId}`×2、
  `{platformSource}`×1、`{contentSessionId}`×1 —— 同一份文档里 `{id}` 与 `{uuid}` 并存。
- **Status**: ✅ **已修（第 321 轮）** —— 4 处改为 `{id}`，中英双语一并处理，属**失实陈述的更正**。
  改后核验：**残留 0**；`git diff --numstat` 恰为 **每文件 2 增 / 2 删**，
  且逐行核验**只有那 4 行路径行变动**、无任何附带改动。
- **⚠️ 刻意未验证的部分**：该节其余断言（SSE `item_deleted`/`session_deleted`、`afterCommit` 时机、
  409 的四个状态、`limit` 钳制、`offset` 下限）**逐条核到源码为真**
  （`ViewerSessionService.java:55,60,106,123,136,149,158`），
  但 **DELETE 是破坏性端点，本轮一律未调用**；404/409 的**运行时**行为**未实测**，仅代码可证。

## Processing Rules
- **第 316 轮新增流程规则（连续三轮教训的归纳）——落笔前先查该模块自己的文档**：
  本循环已**连续三轮**出现同一模式：先凭代码把某处判成「缺口/缺陷」，下一轮才发现**它是被双语文档明确记载的有意设计**。
  | 轮次 | 初判 | 实际 |
  |---|---|---|
  | 312 | Java SDK 无类型化错误（对比 Go 11 哨兵 / Python 12 类 / JS 2 子类） | README `:506` 起有完整 Error Handling 章节，含走 cause 链的读法，**刻意且已记载** |
  | 314 | `build/lib/` 下有陈旧 SDK 源码副本 | `python-sdk/cortex-mem-python/.gitignore:5` 忽略、**0 跟踪文件** → 本地产物 |
  | 316 | Python SDK 无响应体上限（Go/JS 都有 10 MiB） | README（中英双语）有「Response Size Limit」节，写明**有意差异 + 原因 + 后果 + 建议** |
  **规则**：在把某处记成 finding **之前**，先 `grep` 该模块的 README（中英双语）看它**是否已自陈**。
  **「代码里没有」不等于「没人知道」**——本项目在**多处主动记载了权衡**，
  这本身是好实践，不该被当成缺陷反复重提。
  **技术事实与定性要分开**：上述三处**代码事实都没变**，变的是**定性**（缺口 → 已记载的取舍），
  故更正记录时只改定性、保留事实，并把剩下的问题降级为**产品决策**。
- **第 293 轮续记**：把第 292 轮的做法推广到其余 7 类**逐字重复**行（实测记录 6、Reproduction 3、已解决条目 4、复核记录 5+2、探针记录 2），一律只缩短显示名、链接目标不变，**零信息损失**。**至此本文件里已没有可再压缩的重复**：余下每一行要么是决策、要么是问题陈述、要么是指针。
- **第 292 轮补充压缩规则**：第 254 轮把 33 条条目的 Scope / Evidence 整体迁入同一个归档，于是在本文件里留下 **33 条逐字相同**的行。本轮把其中 33 条**完全相同**的改写为短显示名 `…-8.md`（链接目标不变），**零信息损失**；另有 **5 条带第 275 / 278 轮追加内容**的**一行未碰**。与第 291 轮压缩 5 条「复核记录」是同一手法：**同一事实不必逐字重复 N 遍**。
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

### P2-64: Jackson 把 record 上的 `isX()` 当属性序列化——Java SDK 每个 PATCH 都多发一个调用方从未设置过的 `empty` 字段
- **Scope / Evidence**: `cortex-mem-client/.../dto/ObservationUpdate.java:37`（`isEmpty()`）、
  `ExtractionResponse.java:51`（`isFound()`）。
- **Problem**: Jackson 把 record 的**组件**与**符合 JavaBeans 约定的访问器**
  （`getXxx()` 任意类型、`isXxx()` 布尔）当属性序列化，故 `isEmpty()` 被发上 wire 成
  `"empty": false`——**每个 PATCH 都带一个调用方从未设置的字段**，与该类 Javadoc 自称的
  "only explicitly set fields are sent" **直接矛盾**。**实测（修复前，编译产物直接序列化）**：
  `{"title":"T","empty":false}`；`ExtractionResponse` 同理多出 `"found"`。**后端忽略未知键**
  （活体 PATCH 带 `empty` 仍 200 且 title 已更新），故**无功能损坏**，但报文与成文契约不符。
  **第 276 轮更正过宽表述并全量复查 21 个 DTO**：受控实验证明**只有** JavaBeans 约定的
  `isXxx()` / `getXxx()` 泄漏为属性，普通无参方法不可见，**修复完整、无遗漏** → [`…-36.md`](../archive/2026-10-06_backend-review-evidence-36.md) 第 7 块（第 291 轮逐字迁出）。
- **Status**: ✅ **已修（第 273 轮）** —— 两个访问器加 `@JsonIgnore`。修复后实测 `{"title":"T"}`，
  且 null→省略、`facts=[]`→照发等**原有语义全部保持**，`isFound()` 仍正确求值；
  **无任何测试断言该字段**。Java SDK **196/0/0/0** 全绿。
### P2-65: Python SDK 的 `is_retryable` 只收状态码，而 Go/JS 的**同名函数收的是 error**——跨家移植得到一个永远返回 False 的重试判定
- **Scope / Evidence**: `error.py:135-137`（修复前）、`client.py:199`（内部唯一调用点，**用法本就正确**）、`__init__.py:46,93`（**两个名字都在 `__all__` 里公开导出**）。
- **Problem**: Go `IsRetryable(err error)`、JS `isRetryable(err: unknown)` **都只有一个函数且收 error**；
  Python 有**两个**：`is_retryable(status_code)` 与 `is_retryable_error(err)`，而**与 Go/JS 同名的那个收状态码**。
  机械移植的重试循环写成 `is_retryable(e)` 时，**对每个错误都静默返回 False、不抛异常**——实测
  `RateLimitError`(429) 与 `APIError(502/503/504)` 全部 `False`，而 Go/JS 对同样输入返回 `True`。
  **后果是调用方自己的重试循环永不触发且无任何迹象**；不重试的错误返回 False 是对的，故这个坑**只在本该重试时暴露**。
  README 对两个函数**零提及**。
- **Status**: ✅ **已修（第 274 轮）** —— 按「**纯加宽 / 向后兼容即可修**」，把 `is_retryable` 参数**加宽为 `int | BaseException`**：
  收异常转发 `is_retryable_error`，收状态码**行为一行未变**，其它类型 fail-closed 返回 `False`。
  **双向注入的逐条用例明细（7 失败 / 5 对照）**：逐字迁入 [`…-32.md`](../archive/2026-10-06_backend-review-evidence-32.md)（第 282 轮）。
  否则就是本循环反复在抓的「改了测试没回头改这个数」。**未单方面做的**：把两个函数改名以真正对齐 Go/JS 属**改已发布公开 API 的名字**，
  按规则记录不实施。另记**非缺陷**：Go 独有 `IsInternal`(500)，JS 与 Python 无对应谓词——是 Go 多一个。
### P2-66: JS SDK 的 `content`/`narrative` 注释写了一条后端**并不遵循**的优先级规则，而它是四家里唯一不做冲突检测的
- **Scope / Evidence**: `js-sdk/.../dto/observation.ts:56,58`（修复前的两条 JSDoc）、`src/client.ts:363-379`（**原样透传**）；后端依据 `MemoryController.java:317-319` 的 `body.getOrDefault("content", body.get("narrative"))`。
- **Problem**: 原注释称 `content` 走「backend uses "narrative" wire field」、`narrative` 则
  「When both are set, backend processes **whichever is present**」——**两条都与实测不符**。
  `mem_observations` **根本没有 `narrative` 列**（只有 `content`），两个 key 是同一列的两个入口。
  **实测四例**与**四家对拍**（Go `HasConflict()` / Java `IllegalStateException` /
  Python `ValidationError`，**只有 JS 一处检测都没有**）逐字见
  [`…-38.md`](../archive/2026-10-06_backend-review-evidence-38.md) 第 1 块（第 292 轮逐字迁出）。
- **Status**: ✅ **注释已修（第 275 轮，零行为变更）** —— 两条 JSDoc 改为如实描述「`content` 存在时 `narrative`
  一律被忽略，**含 `content` 为 null**」并附实测四例。`tsc --noEmit` 干净、**259/259** 全绿。
  **检测不实施、只记录**：给 JS 补上冲突拒绝是**让原本被接受的调用变成抛错**，属收窄已发布契约；
  JSDoc 已写明「prefer setting exactly one」。
### P2-67: `AsyncConfig` 的类 Javadoc 把「按任务超时」列为它提供的能力——**全后端不存在任何超时机制**
- **Scope / Evidence**: `backend/.../config/AsyncConfig.java` 类 Javadoc 第二条「Timeout handling for async
  methods」与行内注释「values from application.yml with defaults」。
- **Problem**: `getAsyncExecutor()` 只配了 core/max/queue/threadNamePrefix/拒绝处理器/关机等待，**无任何按任务超时**；
  全后端搜 `setTimeout` / `TimeoutInterceptor` / `Future.get(` **零命中**。唯一与时长有关的是
  `await-termination-seconds`，它约束**关机时等运行中任务多久**，不是**任务能跑多久**。
  5 个 `@Async` 方法**任一都没有时间上限**。**影响**：一次卡住的 LLM 调用会**长期占住一个池线程**；
  队列打满后拒绝处理器回退到调用线程执行，**把阻塞带回调用方**——而 `@Async` 的前提正是不阻塞调用方。
  第二处较轻的不实：注释称线程池参数「values from application.yml」，而 **yml 里没有 `claudemem.async` 块**，四个 `@Value` 默认值（10/50/100/60）永远生效。
- **Status**: ✅ **注释已修（第 277 轮，零行为变更）** —— 类 Javadoc 如实列出它真正提供的两件事，
  并写明「**不存在按任务超时**」及其搜索证据、讲清 `await-termination-seconds` 的真实语义、指向 P2-67；
  行内注释注明 yml 无该配置块。`mvn -o compile` EXIT=0、**后端 167 测试全绿**。
  **能力本身只记录不实施**：加真正的超时需先定策略（中断，还是跑完但丢弃结果），属设计决策。
  **该类其余部分核实为真**：`AsyncUncaughtExceptionHandler` 与点名的两个 critical 方法确实存在，
  回退处理器也确有日志与兜底 try/catch。
### P2-68: `updateObservation` 的 Javadoc 说「null 会被忽略」，其下的 `@Operation` 说「null 会清空」——**后者才是真的**
- **Scope / Evidence**: `MemoryController.java:268`（修复前的 Javadoc）与 `:273`（同一方法的 `@Operation`）。
- **Problem**: 同一方法上两处说明**直接相反**：Javadoc 写「**Null values in the body are ignored**」、`@Operation` 写「**null values clear the field**」。**实测站在 `@Operation` 这边**——第 275 轮探针 PATCH `{"content":null,"narrative":"C"}` 落库 **NULL**（narrative 被丢弃），**不是**「忽略」。**危害在于可信度不同**：`@Operation` 是**机器可读的那一份**（`/v3/api-docs`、SDK 生成器、`docs/API.md` 全以它为准），**读源码的人看到的却是 Javadoc**，即恰好相反的指示——「null 被忽略」也正是 P2-26/27/66 一直在绕开的那条错误行为。
- **Status**: ✅ **已修（第 278 轮，零行为变更）** —— Javadoc 改为如实描述并附活体探针证据、写明机器可读的那份一直是对的。**全后端扫过**：错误表述**仅此一处**，正确表述共 **8 处**。`mvn -o compile` EXIT=0。
### P2-69: `SessionLifecycleController` 的 `promptNumber` 是 demo 里**唯一没有范围检查**的数值参数——负数被接受并落库
- **Scope / Evidence**: `SessionLifecycleController.java:82`；活体对拍与落库记录见 [`2026-10-06_backend-review-evidence-31.md`](../archive/2026-10-06_backend-review-evidence-31.md)（第 282 轮）。
- **Problem**: demo 其余五个数值参数（`count` ×3、`maxChars` ×2）都在方法体里写了范围检查，唯独 `promptNumber` 从绑定直接流入 `UserPromptRequest`。实测 `promptNumber=-1` 返回 200「prompt recorded」，且 `mem_user_prompts.prompt_number` 真实落库为 `-1`；`0x10` 落库为 `16`。
- **Status**: ⏸ 记录不修——给已发布端点补范围检查属收窄其接受的值域，是对外契约变更；且与 P2-55 / P2-56 同属一条文法线，应与那次产品决定一并处理。
### P2-70: Compose 把数据库与后端都发布到**所有网卡**，而架构文档的 Network Security 表称二者「仅本地」
- **Scope / Evidence**: `docker-compose.yml:35,88` 两条 `ports:` 与 `:60` 的 `SERVER_ADDRESS`；`docs/ARCHITECTURE.md` Network Security 表（第 291 轮已双语更正）。
- **Problem**: 两条端口映射**均无主机 IP 前缀**，而**不带主机地址的映射默认发布到所有网卡**；compose 另设 `SERVER_ADDRESS: 0.0.0.0`，应用在容器内也绑全网卡；`grep "127.0.0.1:"` **零命中**。**该部署默认关闭鉴权**，故推荐的 Docker 部署可被整个网络访问。该表对**原生**运行准确（`address` 默认 `127.0.0.1`，活体一致），**对 compose 不准确**。
- **Status**: ⏸ 记录不修（文档侧已双语更正并写明原因）。收紧只需给两条映射各加 `127.0.0.1:` 前缀，但那会破坏「从另一台主机访问后端」，**属对外行为变更**。与 P1-2 同族但成因不同：P1-2 是 demo 漏设 `server.address`，此处是 **compose 显式要求**绑全网卡否则映射不通（P2-32 第一条）。
