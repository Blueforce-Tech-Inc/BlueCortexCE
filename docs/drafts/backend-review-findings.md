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
> **逐轮叙述不再保留在本区块。** 每一条发现都在下面 `## Open Findings` 里有**完整条目**
> （Scope / Problem / Reproduction / Status / 复核记录）；逐轮的完整上下文另存于
> `docs/drafts/patrol-rotation.md` 与 `docs/drafts/doc-review-task.md`。
> 本区块曾在 2026-10-03 至 10-04 之间多次越过 `MAX_LINES=1000` 而被迫压缩，
> 逐轮删减并不能根治——**根因是逐轮叙述本就不该放在这里**，故改为只保留下表。
>
> 压缩记录：225（1008 行，10 条已解决条目归档）、232（980 行，P1-3+P1-4 共 79 行归档）、
> 233 / 235（1013 / 1002 行，逐轮摘要并入下表）、236（**本区块结构化**）。
| 轮次 | 条目 | 一句话 |
|------|------|--------|
| 219 / 218 / 224 | P2-24 / P1-3 / P1-4 | 代码侧已修；P1-3、P1-4 已归档，P2-24 仍带 ⏸ 残留故保留 |
| 225–228 | P2-25 / P2-26 / P2-27 / P2-28 | 全部 ⏸ 记录不修（契约或公开 API 变更） |
| 229 | — | 纯 Demo 修复，无新增 finding |
| 230–233 | P2-28 / P2-29 / P2-30 / P2-31 | 全部 ⏸ 记录不修；P2-31 的 Python 半边已修 |
| 234 | P2-32 | ⏸ 记录不修（本机无 Docker，无法验证修复效果） |
| 235 | P2-33 | ⏸ 记录不修（跨 demo 契约决策） |
| 236 | P2-34 | ⏸ 记录不修（改注解即改 OpenAPI 契约）；**人工撰写的 API 文档本来就正确** |
| 237 | P2-35 | ⏸ 记录不修（会让所有用户的库里开始出现失败观测，属产品决策） |

> **本文件已结构性饱和**：第 236 轮移除逐轮叙述后，第 237 轮加入 P2-35 即回到 **1000 行**。
> 27 条中 25 条为 ⏸「记录不修」，按规则**必须保留**（承载决策推理而非历史），**无可归档余量**，
> 故下一条新发现必然再次触发压缩。

**本文件最值得记住的一点**：P2-32、P2-33、P2-34 连续三条的形态完全一样 ——
**机器可读的那一份**（Dockerfile 的默认绑定、demo 的路由名、Swagger 注解的示例）
与**人工撰写的那一份**（compose 文件、demo README、API 文档）不一致或残缺。
三处的**文档层都已先行更正或本来正确**，代码/产物层则因契约变更留待项目决策。

## Open Findings

### P2-24: V17 反馈机制整体未接线 —— 实体还映射了一个不存在的列

- **Scope**: `entity/ObservationFeedbackEntity.java`（已修）、
  `repository/ObservationFeedbackRepository.java`（已修）、
  以及 V17 迁移所声明的三项能力（**均未实现**）。
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
- **复核记录**（原文见 [`2026-10-03_backend-review-provenance.md`](../archive/2026-10-03_backend-review-provenance.md)，逐轮全文另见 `patrol-rotation.md`）

### P1-1: `CortexSessionContextBridgeAdvisor.adviseStream` 依赖普通 ThreadLocal，流式下既丢捕获又泄漏会话

- **Scope**: `cortex-mem-spring-integration/cortex-mem-spring-ai/.../advisor/CortexSessionContextBridgeAdvisor.java:79-99`（`adviseStream`）配合 `context/CortexSessionContext.java:14` 的 `ThreadLocal<SessionInfo>`；消费方为 `aspect/CortexToolAspect.java:43`（仅判断 `isActive()`）。
- **Problem**: `adviseStream` 在**调用线程**上 `begin()`，却把清理放进 `flux.doFinally(...)`。Reactor 的 `doFinally` 运行在**发出终止信号的线程**上；任何真实模型客户端（Reactor Netty / WebClient）都会切线程。产生两个后果：
  1. **捕获被静默丢弃** —— 工具实际执行的线程看不到该 ThreadLocal，`CortexSessionContext.isActive()` 为 false，`CortexToolAspect` 直接 `proceed()` 跳过捕获。`@Tool` 自动捕获在流式下等于失效，且无任何日志。
  2. **会话上下文泄漏** —— `doFinally` 清掉的是信号线程（一个空 ThreadLocal），调用线程的 ThreadLocal 永不清除。线程池复用该线程后，`begin()` 因 conversation id 缺失而提前 return 的那条路径**也不会**清理，于是残留的 `sessionId` 会被下一次请求的 `CortexToolAspect` 当作有效会话使用——工具观察被归到**上一个会话**。这是静默的跨会话数据串号。
- **Reproduction** (deterministic, verified 2026-10-02): 现有 `CortexSessionContextBridgeAdvisorTest.adviseStream_whenConversationIdSet_...` 用 `Flux.just(response)`，在订阅线程同步发射，因此恰好绕开跨线程场景。改用 `Flux.just(response).subscribeOn(Schedulers.boundedElastic())` 后实测：
  ```
  PROBE callingThread    = main
  PROBE emitterThread    = boundedElastic-1
  PROBE visibleOnEmitter = false      <-- 后果 1
  PROBE activeAfter      = true       <-- 后果 2：调用线程仍处于激活态
  PROBE sessionIdAfter   = conv-x     <-- 且绑定的是上一个会话
  ```
- **Options**: (a) 改为 Reactor Context 传播——bridge 用 `contextWrite` 写入会话信息，`CortexSessionContext` 暴露一个 `ThreadLocalAccessor` 并在 starter 中启用 `Hooks.enableAutomaticContextPropagation()`；(b) 退回「只在调用线程的装配窗口内持有上下文」并明确声明流式下不做工具捕获；(c) 把流式会话标识改为显式参数贯穿 `ToolCallingManager`。
- **Status**: ⏸已记录，本轮**不修复**。三个选项都改变并发语义：(a) 会给使用方应用引入全局 Reactor hook 与额外 ThreadLocal 开销，(b) 是功能回退，(c) 需要改 Spring AI 的工具调用链。本轮已做的最小处置是**如实记录限制**：在 `cortex-mem-spring-integration/README.md` 与 `README-zh-CN.md` 的 Design Notes / 设计笔记 中写明该限制与规避方式（需要流式 + `@Tool` 捕获时用同步 `.call()`，或按 conversation id 显式调用 client），避免用户误以为流式下自动捕获可用。
  **复审触发条件**：出现下列任一情况即重新评估——(1) 有用户报告流式下工具观察缺失或串号；(2) 项目决定引入 Reactor 自动上下文传播；(3) Spring AI 版本升级改变了 `StreamAdvisorChain` 的订阅时机（若 `nextStream` 改为在调用线程内完成订阅与执行，本问题自然消失）。
  **第 189 轮后补记（2026-10-03，第 190 轮 Java SDK 轮）——本条记录的后果清单不完整，漏掉了第三条。**
  上述两条后果讲的是 `@Tool` **自动捕获**（`CortexToolAspect`）与上下文泄漏，但 `CortexMemoryTools`
  的两个**读**工具受影响的方式不同：它们不跳过，而是**静默回落到别的项目**。
  `resolveProjectPath()`（`CortexMemoryTools.java:197-205`）在 `CortexSessionContext` 取不到值时
  直接返回构造时传入的 `defaultProjectPath`，该值来自 `cortex.mem.project-path`
  （`CortexMemAutoConfiguration.java:121-122`，未配置则为**空串**），**全程无日志**。
  两种结局都不自我暴露：配置了 `project-path` 时，Agent 拿到的是**另一个项目**的记忆并当成
  当前对话的历史；未配置时，工具发出空项目，而 `retrieveExperiences` **不校验 project**
  （只 `requireNonBlank(request.task())`），后端于是返回 `200` 加空列表，工具报告
  「No relevant past experiences found」——与该项目确实没有历史**无法区分**。
  活体实测（2026-10-03，后端 37777）：`POST /api/memory/experiences` 传 `project: ""` 返回
  `200 []`，同一请求传真实项目路径返回 5 条经验。`buildICLPrompt` 同理。
  受影响的方法：`searchMemories`、`getMemoryContext`（仅这两个调用 `resolveProjectPath()`；
  `updateMemory` / `deleteMemory` 按 id 操作，不涉及项目）。
  双语 README 的 P1-1 段落已由「两个后果」改为「三个后果」并补入上述实测。

### P2-8: 读取侧没有维度路由 —— 写入按维度分列，检索恒定比 `embedding_1024`

- **Scope**: 写入侧 `backend/.../service/AgentService.java:533-535`（`switch (vector.length)`，
  `case 768/1024/1536` 分别调用 `setEmbedding768/1024/1536`）；读取侧
  `backend/.../repository/ObservationRepository.java:268` `hybridSearch`（SQL 硬编码
  `embedding_1024`）与 `backend/.../service/SearchService.java:57-59`。
- **Problem**: `SearchService` 在 PATH 2 的注释自称 "Semantic search with pgvector
  (dimension-aware)"，第 59 行也确实算出了 `int dim = request.queryVector().length`，
  但该变量**只用于 debug 日志**，实际 SQL 始终与 `embedding_1024` 比较。仓库里
  `semanticSearch768` / `semanticSearch1024` / `semanticSearch1536` 三个方法带有正确的
  分维度 SQL，但**全仓零调用方**（`grep` 主代码与测试均无命中）。因此这是一个
  写侧已实现、读侧未实现的非对称。
- **Evidence**（对真实库 `claude_mem_dev` 实测，非源码推断）：以含 3568 行
  `embedding_1024` 数据的真实项目执行 `hybridSearch` 形状的查询，768 维与 1536 维
  查询向量均抛出 `DataException: different vector dimensions 768 and 1024` /
  `1536 and 1024`；1024 维正常返回。
- **实际影响有限且可见**：随附的 `BAAI/bge-m3` 为 1024 维，是唯一开箱可用的配置，
  真实项目中 768/1536 列均为空（实测样本 22559 行中两列皆 0，唯一的非 1024 记录是
  `/tmp/test4` 这条三列同时有值的合成测试夹具）。异常被 `SearchService:74` 捕获后退化为
  全文检索，API 响应如实返回 `strategy: "tsvector"` 与 `fellBack: true`，并记录一条
  WARN。**不是静默失败**，故定为 P2 而非 P1。
- **未修的原因**：正确修复需为 `hybridSearch` 补 768/1536 变体，或在 `SearchService`
  按维度分流；且需先决定同一项目内混合维度数据（既有 1024 又有 768 记录）如何处理。
  这属于 Backend 轮次的设计决策，本轮代码方向为 Java SDK，按轮换纪律不在本轮动手。
- **Status**: ⏸ 已记录不修（2026-10-02，第 173 轮 Java SDK 轮发现）。文档方向已在同轮
  修正 `docs/ARCHITECTURE.md` / `docs/ARCHITECTURE-zh-CN.md` 的 ADR 4：原「Decision 4:
  Multi-Dimension Embeddings」读起来像三种维度端到端可用，现已明确限定为**仅写入侧**，
  并写明退化行为与可观测信号。

### P2-10: 四个 ingest 端点对项目路径的必填性不一致

- **Scope**: `backend/.../controller/IngestionController.java` — `handleObservation`
  （第 319 行 `if (projectPath == null || projectPath.isBlank())` → 400）与
  `handleToolUse`（第 119 行）、`handleUserPrompt`（第 229 行）、`handleSessionEnd`
  三个端点。后三者从 `body.cwd()` 取值后**不做任何校验**。
- **Problem**: 同一族端点对同一个语义字段给出两种契约。实测（对运行中的后端）：
  | 端点 | 缺失/空白 `project_path`（或 `cwd`） |
  |------|------------------------------------|
  | `POST /api/ingest/observation` | **400** `Missing required field: project_path` |
  | `POST /api/ingest/tool-use` | **200** `{"status":"accepted"}` |
  | `POST /api/ingest/user-prompt` | **200** `{"status":"ok"}` |
  | `POST /api/ingest/session-end` | **200** `{"status":"ok"}` |
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

### P2-11: 未配置的抽取模板名不被拒绝，拼错与「尚未抽取」得到同样的 `not_found`

- **Scope**: `backend/.../controller/ExtractionController.java` 的 `getLatestExtraction` 与
  `getExtractionHistory`；模板定义在 `application.yml:39`
  （`app.memory.extraction.templates[].name`，目前**只有一项** `"user_preference"`）。
- **Problem**: 两个端点都不校验 `templateName` 是否存在。实测（对运行中的后端）：

  | `templateName` | `/latest` | `/history` |
  |----------------|-----------|------------|
  | `user_preference` | `200` `status: "not_found"`，`template` 回显 `user_preference` | `200` 空列表 |
  | `user-preferences`（不存在的名字） | `200` `status: "not_found"`，`template` 回显 `user-preferences` | `200` 空列表 |

  **数据字段完全相同**：`status` 相同，四个数据字段（`sessionId` / `extractedData` /
  `createdAt` / `observationId`）均为 `null`，`message` 也相同。两者的**唯一**差异是
  `/latest` 会把请求里的名字原样回显进 `template`——所以严格说并非逐字节相同
  （第 184 轮此处表述有误，第 185 轮据实更正）。`/history` 则完全不回显名字，
  返回的是逐字节相同的 `[]`，连间接线索都没有。
  更值得注意的是文档侧的传播：`ExtractionController` 的两个
  `@Parameter(example = "user-preferences")` 举的正是这个不存在的名字，ZH 文档还额外举了
  `allergy-info`——两者都不存在。这使错误进入了生成的 OpenAPI 规范：任何据此生成的客户端
  都会去请求一个永远 `not_found` 的模板，而且**没有任何信号**表明是名字写错了。
- **实际影响**：`status` 本身不携带任何信息，调用方无法从它区分「这个项目还没抽取过」与
  「模板名拼错了」——除非自己把响应里的 `template` 与想请求的名字比对，而这恰恰是四家 SDK
  都没做的事（Java 的 `isFound()` 只看 `status`，另三家同样只判 status/空列表）。
  `p.observationId` 之类的诊断线索一律没有，`/history` 连回显都没有。
  四家 SDK 均不校验该参数（它只是路径片段），因此 SDK 路径同样触发。
- **Status**: ✅ **已修复（文档与注解层）**（2026-10-03，第 184 轮 Backend 轮）。
  `ExtractionController` 两处 `@Parameter` 的 `example` 改为 `user_preference`，并在
  description 中写明模板名取自 `app.memory.extraction.templates[].name`、目前只随附一个
  模板、以及未配置的名字不会被拒绝（`/latest` 返 `not_found`、`/history` 返空列表）。
  `docs/API.md` 与 `docs/API-zh-CN.md` 中 10 处 `user-preferences` 全部更正为
  `user_preference`（正确名称此前出现 0 次），ZH 版多出的 `allergy-info` 一并删除。
  **第 184 轮的修正不完整，第 185 轮补齐**：当时只搜了 `docs/` 与控制器，
  `git grep` 复查后又在两处发现同一错误名字——`backend/.../dto/ApiResponses.java:158` 的
  `@Schema(example = "user-preferences")`（**这才是真正喂给生成 OpenAPI 响应 schema 的
  示例**，比控制器的 `@Parameter` 更靠后也更隐蔽），以及 Java SDK
  `ExtractionResponse.java:13` 的 Javadoc。两处均已更正。测试夹具中的同名字符串
  （Go `client_test.go` / `dto_test.go`、JS `client.test.ts`）**刻意保留**：那里任意字符串
  都是合法输入，且「服务端原样回显请求值」本身就是一个值得覆盖的场景。
  **未修的部分**：后端仍不校验未知模板名。要让拼错的名字明确失败就得返回 400，而那会改变
  现有调用方看到的行为，属对外契约变更，按纪律留给后续 Backend 轮次决策。
- **同轮核实无误**：四家 SDK 都**正确**地把抽取结果的键建模为**驼峰**
  （`sessionId` / `extractedData` / `createdAt` / `observationId`）——Map 键不受后端全局
  `SNAKE_CASE` 策略影响，Go 更有专门的 `TestExtractionResult_CamelCaseFields` 钉住这一点。
  且四家都能处理 `not_found` 响应中的 `null`（Python 用 `or ""` 与 `_to_dict`/`_to_int`，
  JS 用 `safeStringOr`/`safeRecord(...) ?? {}`/`safeNumberOr`），不存在解析崩溃。
  本轮另修正文档的 `not_found` 示例：它原本只列 3 个键，而**实际响应有 7 个**
  （`sessionId`/`extractedData`/`createdAt`/`observationId` 均为 `null`）——处理器返回的是
  同一个 `GetLatestExtractionResponse` record，只是把四个构造参数置空，故这些键出现在
  JSON 中而非被省略。

### P2-13: Spring AI 集成无法按用户隔离记忆——会话上下文里没有 userId

- **Scope**: `cortex-mem-spring-integration/cortex-mem-spring-ai/.../context/CortexSessionContext.java`
  （`SessionInfo` 仅 `sessionId` / `projectPath` / `promptCounter`，两个 `begin()` 重载都不接受 userId）、
  `.../advisor/CortexMemoryAdvisor.java:119-123`（`buildICLPrompt` 只设 `.project(...)`，
  全文 `userId` 出现 **0** 次）、`.../tools/CortexMemoryTools.java:70-75, 108-112`。
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

- **Scope**: `ObservationRepository.java:619-634`（`findNewObservations(project, sources,
  sinceEpoch, limit)`，javadoc 写「Find new observations since a given epoch for
  **incremental extraction**」）；调用方为 `StructuredExtractionService.java:211`，
  它用的是 `findBySourceIn(projectPath, sources, initialRunMaxCandidates)`；
  `V16__composite_source_index.sql:11` 把 `findNewObservations` 列为新建复合索引服务的查询之一。
- **Problem**: 增量抽取**没有实现**。后端全文没有 `extraction_state`（0 命中），
  每次运行都调用 `findBySourceIn` 取最新的 N 条，**没有「上次抽取之后」的过滤**。
  `findNewObservations` 本身实现完好、SQL 正确（`created_at_epoch > :sinceEpoch`
  且 `ORDER BY created_at_epoch ASC`），但 `backend/src/main` 中**零调用方**——
  连单元测试都没有引用它。这是 P2-12（`deepRefineProjectMemories` 无调用方）的同型：
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

- **Scope**: `mcp/ClaudeMemMcpTools.java:249-258`（`findByContentSessionId("manual-memories")
  .orElseGet(() -> sessionRepository.save(...))`），整段被外层 `catch (Exception)`（:290）覆盖。
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

- **Scope**: `cortex-mem-spring-integration/cortex-mem-client/.../CortexMemClientImpl.java`
  （`executeWithRetry` / `executeWithRetryReturn` 均以
  `throw new RuntimeException(operation + " failed: " + describe(e), e)` 收尾；
  `describe()` 只回传后端 `error` 字段文本，**不含状态码**）；
  整个 `cortex-mem-client` 模块 **18 个类中没有任何错误类型**。
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

- **Scope**: `ExtractionConfig.java:29`（`maxBatchesPerTemplate = 10`，绑定
  `EXTRACTION_MAX_BATCHES`）与 `StructuredExtractionService.java:234-235` 的批处理循环。
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

- **Scope**: `StructuredExtractionService.java:135-160`（`doReExtractForSession`），
  入口为 `SessionController.java:327`（`PATCH /api/session/{id}/user`）。
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

- **Scope**: `CortexMemClientImpl.java:231`（`triggerRefinement`）与 `:432`
  （`triggerExtraction`），二者都走 `executeWithRetrySilent`（`:775-799`）。
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

- **Scope**: 全部声明为 `int`/`long`/`Integer`/`Long` 的 `@RequestParam`，共 **22** 个、
  分布在 **11** 个端点（`ViewerController` 13 处、`ContextController` 7 处、
  `LogsController` 1 处、`ExtractionController` 1 处）。完整清单见
  `docs/API.md` 的「Query Parameter Conventions」一节。
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

- **Scope**: `cortex-mem-starter/.../CortexMemHealthIndicator.java`（`health()` 的
  `catch (Exception e)` 分支与 `withException(e)` 详情）配合
  `cortex-mem-client/.../CortexMemClientImpl.java:339-360`（`healthCheck()`）。
- **Problem**: `healthCheck()` 自己 `catch (Exception e)` 后 **`return false`**，
  **从不向外抛出**。因此 `CortexMemHealthIndicator.health()` 的 `catch` 分支
  在生产中**不可达**，`withException(e)` 写出的 `error` 键**永远不会被填充**。
  活体实测（真实 `CortexMemClientImpl`，指向死端口 39999，超时 500ms）：

  ```
  status  = DOWN
  details = {service=Cortex CE Memory Backend, reason=Health check returned false}
  hasErrorKey = false
  ```

  指向真实后端时 `status=UP`。也就是说运维在 `/actuator/health` 里看到后端挂掉时，
  只能读到「Health check returned false」——**连接被拒 / 超时 / DNS 失败这些真正
  的原因全部丢失**，因为它们在客户端被 `log.debug` 吞掉（默认不输出）。
  「后端不可达」与「后端自报 degraded」两种完全不同的情况，指示器给出**完全相同**的
  文案。
- **测试反而钉死了这个假象**：`CortexMemHealthIndicatorTest.health_whenClientThrows_returnsDown`
  用 **mock** 让 client 抛出，并断言 `containsKey("error")`。这个状态
  **真实 client 永远无法产生**，所以该用例**恒真却毫无保护作用**——
  它让人以为异常路径已被覆盖，而生产中恰恰走不到。
  这与第 197 轮「夹具传了后端从不下发的值」是同一类：测试覆盖的是一个**虚构状态**。
- **核实无误的部分**：`healthCheck()` 判定 `"ok"` 的大小写是对的——后端
  `HealthController.java:62` 返回 `dbReady ? "ok" : "degraded"`（**小写**），
  活体 `GET /api/health` 亦为 `{"status":"ok"}`；null body、非 `ok`、异常三种
  情况均正确返回 `false`，指示器据此 UP/DOWN 的三分支本身也正确。
  **缺陷只在「原因丢失」与「测试虚构」两处，不在判定逻辑。**
- **Status**: ⏸**已记录，不实现**。要让原因到达指示器，需要 `healthCheck()`
  改为向上抛出（**改变既有方法的行为契约**，所有调用方的 `catch` 都要重审），
  或为 client **新增公开 API**（如 `getLastHealthFailure()`）供指示器读取——
  两者都属对外契约变更，与 P2-13~P2-20 同一套判断，需项目先定方向。
  本轮代码方向为 Java SDK，已做的是**如实记录**与**如实核实**（含一次假设被证伪：
  初判「环境变量形式无法关闭 `capture-enabled`」，改用**真实环境变量**复测后
  证明 `CORTEX_MEM_CAPTURE_ENABLED=false` **有效**——原结论来自
  `withPropertyValues` 不模拟环境变量这一**探针缺陷**）。

### P2-22: `/api/cursor/projects` 的 Swagger 示例把 ISO 字符串写成了 epoch 数字

- **Scope**: `backend/.../controller/CursorController.java:143`（`@ApiResponse` 的
  `@Schema(example = ...)`）。
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

- **Scope**: `backend/.../controller/StreamController.java:60-73` 配合
  `backend/.../service/SSEBroadcaster.java:26-34`（`add()` 抛
  `IllegalStateException`）与 `Constants.MAX_SSE_CONNECTIONS = 100`。
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

- **Scope**: `ApiRequests.ICLPromptRequest.maxChars`（`ApiRequests.java:147`）的
  `@Schema(description = "Max prompt length (0 = backend default ~4000)")`，
  该描述原样出现在 `/v3/api-docs` 与 Swagger UI 中。
- **Problem**: 后端**没有**「0 表示默认」的分支。`MemoryController` 第 154 行写的是
  `int maxChars = request.maxChars() != null ? Math.max(100, request.maxChars()) : 4000;`
  —— 判的是 `!= null`，不是 `> 0`。于是显式传 `0` 会走进 `Math.max(100, 0)`，
  得到 **100**，而非描述承诺的 ~4000。客户端作者照此实现「不传就传 0」的惯例，
  会把注入的 ICL 记忆上下文截到 100 字符，**且没有任何错误提示**（HTTP 200）。
- **Reproduction**（2026-10-03，活体 37777，对同一 task）：

  | 请求 `maxChars` | 响应回显 | 实际 prompt 长度 |
  |---|---|---|
  | 省略 | 4000 | 680 |
  | `0` | **100** | **53** |
  | `-5` | 100 | 53 |
  | `100` | 100 | 53 |
  | `4000` | 4000 | 680 |

- **Status**: ⏸ **记录不修** —— 改 `@Schema` 描述即改**对外 OpenAPI 契约**，
  按既定纪律留待项目决策。**文档层已先行更正**（沿用 P2-11 / P2-22 的先例）：
  `docs/API.md` 与 `docs/API-zh-CN.md` 的 `maxChars` 字段表现已写明 100 的下限、
  `0` 与负数被钳到 100、以及「不存在 0 表示默认的路径」，并说明响应会回显实际生效值。
- **关联修复（第 225 轮已实施，属 SDK 侧、非契约变更）**: Java SDK 的
  `ICLPromptRequest.toWireFormat()` 原为 `if (maxChars != null)`，会把 `0` 原样发到
  wire 上，与 Go SDK 的 `json:"maxChars,omitempty"`、Python SDK 的 `if max_chars:`
  **不一致**——那两家会省略 0 从而正确落到后端默认。已改为 `maxChars != null && maxChars > 0`。
  少发一个可选字段不改变 wire 契约，且与另两家对齐。**JS SDK 无防护**（`buildICLPrompt`
  原样透传 req），其 `examples/http-server` 的 `/chat` 默认 `maxChars: req.body.maxChars ?? 0`，
  属 JS/TS SDK 方向的发现，留待该方向轮次处理。
- **复核记录**（原文见 [`2026-10-03_backend-review-provenance.md`](../archive/2026-10-03_backend-review-provenance.md)，逐轮全文另见 `patrol-rotation.md`）

### P2-26: Go SDK 的 `omitempty` 让 `facts` / `concepts` / `extractedData` 无法清空，且静默返回「updated」

- **Scope**: `go-sdk/cortex-mem-go/dto/observation.go` 的 `ObservationUpdate` 三个字段：
  `Facts []string`、`Concepts []string` 均带 `,omitempty`，`ExtractedData map[string]any` 同。
  客户端路径 `client_methods.go:211 UpdateObservation`。
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
- **Reproduction**（2026-10-03，活体 37777，读 `mem_observations` 实际值）：
  设 `{"facts":["alpha","beta"],"concepts":["c1","c2"]}` → DB 为
  `['alpha','beta'] / ['c1','c2']`；发 `{"facts":[],"concepts":[]}` → DB 为 `[] / []`
  （**后端确实接受空数组清空**）；再发 Go `omitempty` 实际产出的 `{"title":"rt226 probe"}`
  → DB **纹丝不动**仍为 `['alpha','beta'] / ['c1','c2']`，HTTP 却是 200 `updated`。
  Go 序列化行为另用探针逐项确认：`ptr("")` → `{"title":""}`、`[]string{}` → `{}`、
  `nil` → `{}`、`map[string]any{}` → `{}`、`["x"]` → `{"facts":["x"]}`。
- **四家对拍**: Go ✗ 无法清空；Java `@JsonInclude(NON_NULL)` 只排除 null、空 list 会发出 ✓；
  Python `if val is not None`（`[]` 非 None）会发出 ✓；JS `JSON.stringify` 保留 `[]` 且
  源码注释明写「null = clear field, undefined = skip」✓。**Go 是唯一的问题家。**
  附带一处文档误导：Python `ObservationUpdate` 的 docstring 写着
  「matching Go's pointer-field-with-omitempty pattern」，但 Go 的 `Facts` **不是指针**，
  两者行为实际不同 —— Python 把一个错误模式当成了对齐基准。
- **Status**: ⏸ **记录不修** —— 对齐只有两条路，都属**公开 API 变更**：把三个字段改成
  `*[]string`（**破坏所有现有调用点**，源码不兼容），或给结构体加自定义 `MarshalJSON`
  （**改变现有代码发上 wire 的内容**，`[]string{}` 从「不变」变成「清空」）。
  按既定纪律留待项目决策。**文档层已先行说明**：`README.md` / `README-zh-CN.md` 新增
  「List And Map Fields Cannot Be Cleared / 列表与映射字段无法清空」小节，写明两种表现、
  指针字段为何不受影响、与其他三家的差异及两条修复路径各自的代价。
  **Go SDK 代码一字未改。**
- **复核记录**（原文见 [`2026-10-03_backend-review-provenance.md`](../archive/2026-10-03_backend-review-provenance.md)，逐轮全文另见 `patrol-rotation.md`）

### P2-27: Python SDK 无法清空 `extractedData` —— 与 Go 并列最弱，而它的注释把这一点说成了「对齐 Go」

- **Scope**: `python-sdk/cortex-mem-python/cortex_mem/dto.py` 的
  `ObservationUpdate.is_empty()` 与 `to_wire()`，两处都有一句
  `if attr == "extracted_data" and isinstance(val, dict) and not val: continue`。
  另含 `ObservationUpdate` 类 docstring 中一句**对 Go 的事实性错误描述**（已修，见下）。
- **Problem**: 后端 `PATCH` 接受两种清空写法并都能落库——实测
  `{"extractedData": null}` → 列变 **NULL**，`{"extractedData": {}}` → 列变 **`{}`**。
  Python **两种都发不出**：`None` 走 `if val is not None` 被跳过，`{}` 走上面那句
  `continue` 被显式跳过。探针确认 `extracted_data=None` 与 `extracted_data={}`
  的 `to_wire()` **都是 `{}`**，且 `is_empty()` **都是 True**。因此
  **「一条已有 extractedData 的观测无法通过 Python SDK 清空它」**。
  活体数据佐证该字段是真实使用的：`mem_observations` 38,200 行中
  `extracted_data` 非空对象 **20,780**、NULL **17,420**、**空对象 `{}` 为 0**——
  后端自身从不写 `{}`，所以走 `{}` 这条路会造出库中从未出现过的状态。
- **四家能力阶梯（清空 extractedData）**:

  | SDK | 能否发 `null` | 能否发 `{}` | 结果 |
  |---|---|---|---|
  | JS | ✓（原样透传给 `JSON.stringify`） | ✓ | 真清空 |
  | Java | ✗（`@JsonInclude(NON_NULL)`） | ✓ | 只能落 `{}` |
  | **Go** | ✗（`omitempty`） | ✗（`omitempty`） | **完全不能** |
  | **Python** | ✗（`if val is not None`） | ✗（显式 `continue`） | **完全不能** |

  注意 Go 与 Python **在 facts/concepts 上能力相反**（Go 因 `omitempty` 丢弃空切片而
  不能清空，Python 因 `[] is not None` 而能清空）——见 P2-26。
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

- **Scope**: `TestController.testAll()`（`TestController.java:118-123`）。
  `testLlm()` 与 `testEmbedding()` 各自会返回 `ResponseEntity.status(500)`
  （第 67、106 行），但 `testAll` 只取 `.getBody()` 塞进 Map，
  **状态码被丢弃**，自身恒返 `200 OK`。
- **Problem**: 同一份「测试连通性」的语义，两个端点给出**互相矛盾的失败信号**。
  类级 `@Profile("!prod")` 门控是正确的（第 26 行），四家 SDK 也都零调用方，
  暴露面有限；但**任何用 `/all` 做巡检的脚本或监控，在提供方完全不可用时仍会看到
  200**，从而永远不会告警。Swagger 注解（第 116 行）**只声明了 200**，
  与实现一致 —— 也就是说**契约本身就是这样声明的**，问题不在契约与实现不符，
  而在这个契约让该端点失去了作为测试端点的意义。
- **Reproduction**（2026-10-03，活体 37777，嵌入密钥失效的状态下）：

  | 端点 | HTTP | 响应体 |
  |------|------|--------|
  | `GET /api/test/llm` | 200 | `{"status":"success", ...}` |
  | `GET /api/test/embedding` | **500** | `{"status":"error","message":"Embedding failed: 401 - ...Token is invalid."}` |
  | `GET /api/test/all` | **200** | 内含**同一个** `embedding.status = "error"` |

  同一故障，一边 500 一边 200，实测复现。
- **Status**: ⏸ **记录不修** —— 让 `/all` 传播子状态码属**对外契约变更**
  （监控与脚本会看到不同状态码），且需同步修改只声明 200 的 Swagger 注解，
  按既定纪律留待项目决策。**文档层已先行更正**：`docs/API.md` 与
  `docs/API-zh-CN.md` 的 Test All 章节现明写「该端点恒返回 200」、给出两种真实
  响应示例（健康 / 嵌入故障各一），并直接告诉巡检脚本应读嵌套 `status` 而非状态码。
- **复核记录**: 第 230 轮代码方向发现（首次审 `TestController`）。取证：活体
  三端点分别 curl 取状态码；读 `TestController.java:118-123` 确认
  `testLlm().getBody()` 丢弃了 `ResponseEntity` 的状态部分；
  `grep -rn "api/test"` 确认四家 SDK **零命中**，而项目自带的
  `scripts/test-llm-provider.sh` **只调用 `/llm` 与 `/embedding`**（第 43、76 行
  正是靠 `%{http_code}` 判定）、**从不调用 `/all`** —— 说明仓库自身也绕开了它。
  **探针自身错一次并先识别再采信**：统计 controller 数量时用
  `ls ... | grep -v Test` 过滤测试文件，结果把 `TestController.java` 一并滤掉，
  数出 12 而记录是 13；改用 `grep -rln "@RestController"` 复核得 13，
  **确认是过滤器缺陷、既有记录无误**，没有据此改写任何结论。

### P2-29: tool-use 去重键不是一次调用的身份，且未被原子强制

- **Scope**: `AgentService.calculateToolInputHash()`（`AgentService.java:466-482`）、
  `AgentService.handleToolUse()` 去重分支（第 147-158 行）、
  `PendingMessageRepository.existsBySessionAndTool()`（第 38-45 行）、
  `PendingMessageEntity` 的 `@UniqueConstraint`（第 8-11 行）。
- **Problem**: 去重键是 `(content_session_id, tool_name, SHA-256(tool_input))`，
  判定条件额外要求 `status <> 'failed'`。三处各自独立地削弱了它：

  1. **键里没有 `tool_response`。** 哈希只覆盖 `toolInput` 字符串。因此一次
     「同样的工具、同样的入参、但结果不同」的调用，在前一条仍处于
     `pending`/`processing` 时会被**直接 `return` 丢弃**，而调用方拿到的是
     `AgentService.java:150` 记录的 "Duplicate tool-use event skipped" 日志加上
     HTTP `200 {"status":"accepted"}`——**与真正入队完全无法区分**。
     对 fire-and-forget 的 SDK 捕获路径（`recordObservation` 等）而言，
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
- **Reproduction**（2026-10-03，活体 37777）:

  | 探针 | 做法 | 结果 |
  |------|------|------|
  | 静默丢弃 | 先直插一行 `status='processing'`、三元组与随后请求一致，再 POST 一次带**全新** `tool_response` 的 tool-use | HTTP **200** `{"status":"accepted"}`，但库里**只有那行预置数据**，新结果**未落库** |
  | 原子性 | 8 个线程并发 POST **完全相同**的 tool-use | 8 个 200，库里**落了 8 行**；`created_at_epoch` 全部落在 **1 ms** 窗口内，且都在最后一行写入后 **58 ms** 才转 `failed`——即 8 次去重检查都在任何一次落库转 `failed` 之前跑完了 |
  | 命名绕行 | 同 session 先 `Read` 后 `read`，input 相同 | 两行都在，input 哈希相同（`45ff9481fce2…`） |

  关于「窗口」要如实补一句：**是否丢弃取决于时序**。上面第一行之所以稳定复现，
  是因为预置行卡在 `processing`；而在本机（嵌入密钥失效、处理毫秒级失败）
  真正背靠背连发两次时，前一条往往已转 `failed`、去重条件不再命中，
  两次**都会**留下。生产环境嵌入/LLM 正常时处理耗时以秒计，窗口远宽于此——
  但这个「取决于时序」本身正是缺陷的一部分：**同一段代码的行为不可预测**。
- **规模**: 排除本次探针后 `mem_pending_messages` 共 **10,682** 行、
  distinct `tool_input_hash` 仅 **7,305**。其中 **2,682 行（25.1%）** 的
  `tool_input` 是空对象 `{}`，**共享同一个哈希**——对这些客户端而言去重键
  实际退化成 `(session, tool_name)`。最大的一组是单个 session 内 `exec`
  工具的 **208 行、全部 `failed`**。全表状态分布：`processed` 7,295 /
  `failed` 3,282 / `skipped` 105。
- **Status**: ⏸ **记录不修** —— 三条修法都改对外行为：
  (a) 把 `tool_response` 纳入哈希 → 幂等重发不再被吸收，削弱崩溃恢复保护；
  (b) 规范化 `tool_name` → 存量数据里 `readFile`/`write_file` 这类异名需迁移；
  (c) 补 `uk_session_tool_input` 唯一约束 → **会直接打断「失败后重试」这条
  合法路径**：应用层检查刻意忽略 `failed` 行，而唯一索引不忽略，于是重试会撞
  约束异常返回 500。要同时做对，需要重新设计「什么算同一次调用」，
  属设计决策，按既定纪律留待项目决策。**文档层已先行更正**：
  `cortex-mem-spring-integration/README.md` 与 `README-zh-CN.md` 现明写
  捕获路径的这一静默丢弃形态。**SDK 与后端代码一字未改。**
- **复核记录**: 第 231 轮代码方向（Java SDK）发现。切入点是 Java SDK 的
  `ObservationRequest.toWireFormat()` 发了 `toolResponse`/`promptNumber`/`source`
  却发现它们**都不参与去重**。取证链：读 `AgentService.java:147-158` 与
  `PendingMessageRepository.java:25,38` 确认键与判定条件；`grep` 确认后端
  **零引用** `tool_use_id`（真正的键不是它）；预置 in-flight 行做确定性丢弃
  复现；并发 8 发验证原子性；`pg_constraint` + 事务内重复插入验证约束不存在；
  `grep -rn "UNIQUE" backend/src/main/resources/db/migration/` 确认无迁移创建。
  **探针自身错一次并先识别再采信**：并发探针脚本在同一次运行里查库，
  读到 0 行、险些据此断言「事件根本没落库」；复查发现是**落库晚于响应返回**，
  稍后重查为 8 行——**先识别为探针时序问题再采信**，未据此改写结论。
  另修正了自己一次统计口径错误：最初按 `(session, hash)` 分组把
  `read`/`edit`/`write` 误称为「大小写孪生」得 307 组，改用
  `(session, lower(tool_name), hash)` 精确分组后为 **1 组**。

### P2-30: 负数 `limit` 在四家 SDK 有三种行为，而 Go 自身也不一致

- **Scope**: Go `client_methods.go:124`（`Search`）、`:145`（`ListObservations`）、
  `:297`（`GetExtractionHistory`）；对照 Java `SearchRequest.java:62` 与
  `ObservationsRequest.java:36`、JS `client.ts:264` 与 `:840`、Python `client.py:508`。
- **Problem**: 同一个非法输入 `limit = -5`，四家给出**三种**结果：

  | SDK | 对 `limit < 0` 的处理 | 位置 |
  |-----|----------------------|------|
  | **Java** | **抛 `IllegalArgumentException`**，另在 `> 100` 时也抛 | `SearchRequest.java:62,65`；`ObservationsRequest.java:36,39` |
  | **Go** | `Search` / `ListObservations` **静默丢弃**（`if req.Limit > 0`） | `client_methods.go:124,145` |
  | **JS** | **静默丢弃**（`req.limit > 0`） | `client.ts:264,840` |
  | **Python** | **照发**（`if limit:`，负数在 Python 里为真值） | `client.py:508` |

  **Go 自身也不一致**：同一个 SDK 的 `GetExtractionHistory`（`client_methods.go:297`）
  对负数 **抛 `ValidationError`**，而两个最常用的检索方法静默丢弃——**同一份代码里两种
  处理，且代码与 README 都没给出任何理由**。
- **Reproduction**（2026-10-03，httptest 抓实际出参，非读码推断）:

  | 调用 | 实际 rawQuery |
  |------|--------------|
  | `ListObservations(Limit: -5)` | `""`（参数被丢弃） |
  | `ListObservations(Limit: 0)` | `""` |
  | `ListObservations(Limit: 100)` | `limit=100` |
  | `Search(Limit: -5)` | `project=%2Fp&query=q`（无 `limit`） |
  | `GetExtractionHistory(limit: -5)` | 返回 `cortex-ce: validation error on limit: limit must not be negative` |

  后端裁定（活体 37777，`ViewerController` 的 `Math.min(Math.max(1, limit), 100)`）：
  `GET /api/observations?limit=-5` → **1 条**；`?limit=0` → **1 条**；不带 `limit` → **20 条**。
  `GET /api/search?...&limit=-5` → **1 条**，不带 → **5 条**。
- **Impact**: 真实伤害在**移植路径**上。Java 是四家中**唯一**会把这个错误告诉调用方的；
  把 Java 代码移植到 Go 或 JS，校验**整个消失**且没有任何提示——Go/JS 的调用方拿到的是
  一页**满额 20 条**、结构完全正常的结果，比报错更难发现；Python 拿到的是被钳成 1 条的
  退化页。典型触发场景是调用方自己算分页（`limit = total - offset` 之类）算出负数。
- **Status**: ⏸ **记录不修** —— 让 Go 对负数抛错，会让**当前能正常返回**的调用方开始失败，
  属公开 API 行为变更；四家对齐更属跨 SDK 契约决策。**文档层已先行更正**：
  Go SDK 两份 README 现明写 `Search` / `ListObservations` / `GetExtractionHistory`
  三者对负数的**不同**处理，并附四家对拍表与后端裁定值。**Go SDK 代码一字未改。**
- **复核记录**: 第 232 轮代码方向（Go SDK）发现，359 测试全过。本轮**四个新角度核实无误**：
  错误分类法（`statusCodeToError` 覆盖 11 个状态码、`IsRetryable` 判定与注释逐条吻合）、
  查询参数编码（走 `url.Values.Encode`，无注入面）、响应体上限（`LimitReader` 多读 1 字节后
  显式报错，**不会**退化成 JSON 截断错误）、DTO 时间字段（建模为 `string`/`int64`，无解析失败面）。
  **一个假设在写成发现前被证伪**：怀疑 `GetObservation` 未找到时返回 `nil, nil` 会让调用方
  空指针崩溃——**四家其实完全一致且都有文档**（Java 返回 `null`、Python 返回
  `Observation | None`、JS 返回 `Observation | null`、Go 返回 `nil` 且接口注释写明），
  **不是缺陷**。取证：临时 httptest 文件
  （跑完即删，工作区无残留）确认 Go 实际出参；`grep` 逐家读源码确认四家行为；
  活体 curl 确认后端钳位值。**探针自身错一次并先识别再采信**：统计根模块测试数时用
  `^--- PASS` 只数顶层用例得 270，与基线 359 不符；改用含子测试的模式逐模块统计得
  **299 + 8 + 13 + 12 + 27 = 359**，**确认是计数口径问题、既有记录无误**。

### P2-31: Go SDK 仍把负数 `maxChars` 发上 wire，注入被钳到 100 字符

- **Scope**: `go-sdk/cortex-mem-go/dto/experience.go:38` 与 `:46` 的
  `MaxChars int \`json:"maxChars,omitempty"\``。
- **Problem**: `omitempty` 只省略 **0**，**负数照发**（`omitempty` 判定的是 Go 零值，
  而 `-5` 不是零值）。后端解析式是 `maxChars != null ? Math.max(100, maxChars) : 4000`
  （`MemoryController.java:154`），**判 null 不判 0**，于是负数落进 `Math.max(100, -5)`
  → **100**。**Python 曾是同一形态**（`if max_chars:` 只跳过 0），本轮已修（见下）。
- **Reproduction**（2026-10-03，活体 37777，同一条 task）：

  | 请求 | 响应回显 `maxChars` | 实际 prompt 长度 |
  |------|--------------------|-----------------|
  | 省略字段 | 4000 | **564** 字符 |
  | `maxChars: 0` | 100 | **53** 字符 |
  | `maxChars: -5` | 100 | **53** 字符 |
  | `maxChars: 4000` | 4000 | 564 字符 |

  **200 OK、无任何错误**，调用方只会看到一份被压到 53 字符的注入。
- **Status**: ⏸ **记录不修** —— Go 里没有 `if x > 0` 这种写法可用；两条路都属
  **公开 API 变更**：把字段改成 `*int`（破坏所有调用点）或写自定义 `MarshalJSON`
  （改变现有 wire 内容）。**本轮已修 Python**：守卫由 `if max_chars:` 改为
  `max_chars > 0`，与第 225 轮的 Java、第 228 轮的 JS 同一处修法。
  **同时更正了两处源码注释**——Java 的 `ICLPromptRequest` 原写「matches the Go SDK
  … and the Python SDK … so all four SDKs agree on what 0 means」，JS 的
  `buildICLPrompt` 原写「and the Go and Python SDKs, which omit 0 as well」：
  **两句都只对 0 成立**，现已写明 Go 是唯一例外、且注明在 P2-31 关闭前**不要再说
  四家一致**。**双向注入验证为真**：把 Python 守卫改回 `if max_chars:` →
  负数用例**恰好 1 条**失败（零值与正值用例理应不失败，正数那条的作用正是防过度修复）；
  恢复后 Python **431** 全过（原 428），Java **192**、JS **239** 均与基线一致。
- **复核记录**: 第 233 轮代码方向（Python SDK）发现。取证：读四家源码确认守卫形态；
  活体 curl 四种取值裁定后端行为；`grep -rn "max_chars" tests/` 确认**无既有测试
  钉死该行为**（故是改正而非与测试冲突）。**顺带核实无误**：Python 五处路径拼接
  （`session_id` / `observation_id` / `template_name`）**全部**用
  `quote(x, safe='')`，与 Go 的 `url.PathEscape`、JS 的 `encodeURIComponent` 一致，
  **四家无一处漏转义**；请求超时恒有设置（下限 0.1s）；重试为线性退避 + ±25% 抖动、
  仅重试瞬时错误。

### P2-32: 两个 Dockerfile 都不设 `SERVER_ADDRESS`，默认部署下服务对外不可达；根镜像的 healthcheck 还写死了端口

- **Scope**: 根 `Dockerfile`（`HEALTHCHECK` 行与文件头注释里的 `docker run` 示例）、
  `backend/Dockerfile`（`HEALTHCHECK` 行）、`backend/src/main/resources/application.yml:3`。
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
- **Reproduction**（2026-10-04）:

  | 事实 | 证据 |
  |------|------|
  | 默认绑回环 | `application.yml:3` 源码 |
  | 该默认值确实生效 | 活体进程 `lsof` 显示 `TCP 127.0.0.1:37777 (LISTEN)` |
  | 对外确实不可达 | 同机 `curl http://10.166.1.125:37777/api/health` → **HTTP=000**（连接失败） |
  | 两个 Dockerfile 都没设 `SERVER_ADDRESS` | 逐文件读，两者只有 `EXPOSE 37777` |
  | compose 显式绕开了 | `docker-compose.yml` 有 `SERVER_ADDRESS: 0.0.0.0` |
  | 两个 healthcheck 不一致 | 根：`http://localhost:37777/…` 且无 `ENV SERVER_PORT`；backend：`http://localhost:${SERVER_PORT}/…` 且有 `ENV SERVER_PORT=37777` |

  **未验证的部分（如实标注）**：本机**没有 Docker**（`which docker` 无输出），
  因此上述容器内行为是**源码与配置层面的推断 + 宿主机 bind 行为的实测**，
  **没有真的构建镜像跑一遍**。修法很直接且与 `backend/Dockerfile` 已有的正确写法一致：
  两个 Dockerfile 都加 `ENV SERVER_ADDRESS=0.0.0.0`；根 `Dockerfile` 再把 healthcheck
  改成 `${SERVER_PORT}` 并补 `ENV SERVER_PORT=37777`。
- **Status**: ⏸ **记录不修** —— 改 Dockerfile 属**部署产物变更**，且本机无 Docker
  **无法验证修复效果**；按既定纪律，不把未验证的改动当作已完成的修复提交。
  修法已在上文写明，留待有 Docker 环境的轮次或项目方实施。
  **文档层无需改动**：部署指南的 compose 片段经**逐键逐值对拍**与真实
  `docker-compose.yml` **完全一致**（差异只有为可读性新增的注释与键序分组，
  无任何键、值或默认值不同），**没有发现错误陈述**。
- **复核记录**: 第 234 轮文档方向（运维/用户指南）发现。取证：`lsof` + 对非回环地址
  `curl` 实测 bind 行为；逐文件读两个 Dockerfile 与 `application.yml`；
  用脚本把 `DEPLOYMENT.md` 里的 compose 片段与真实文件做 `difflib` 逐行对拍。
  **一处刻意不报**：根 Dockerfile 的 healthcheck 依赖 `wget`，而运行阶段是
  Debian 基的 `eclipse-temurin:21-jre`（**非** Alpine）——`wget` 是否存在**本机无法验证**，
  按「没验证的不写」**不下结论**，故未列入本条。

### P2-33: Go demo 的两个端点名与另外三家 demo 不同

- **Scope**: `go-sdk/cortex-mem-go/examples/http-server/main.go:470` 与 `:771`
  的路由注册。
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
- **复核记录**: 第 235 轮代码方向（Demo）发现，方法是对四家 demo 逐个提取路由注册
  后做集合差集——21 个同名、2 个异名，一眼看出不是随机差异而是**成对的同一处分歧**。
  **本轮同时更正了自己在第 229 轮写下的两处事实错误**：那一条把 Go demo 的写入端点
  记作「`main.go:801` 的 `/observations/create`」，而实际是**第 771 行注册的
  `/create-observation`**——`/observations/create` 是 **JS 与 Python** 两家的路径，
  且 801 行是该 handler **函数体内的 `RecordObservation` 调用**而非注册处。
  该错误已同步更正于 `patrol-rotation.md` 与 `doc-review-task.md` 两处轮换记录。
  **第 229 轮的核心结论经复核仍成立**：Go demo 的 `/chat` handler
  （`main.go:170-213`）内 `client.*` 调用**只有 1 个 `BuildICLPrompt`**，
  `RecordObservation` 与 `RecordToolUse` **各 0 次**——`/chat` 确实什么都没记录。

### P2-34: `GET /api/logs` 的 Swagger 示例漏掉 `files`，且把绝对路径写成 `/logs`

- **Scope**: `LogsController.getLogs()` 的 `@ApiResponse` 示例
  （`LogsController.java:81-82`）。**人类撰写的文档是对的**，错的只有注解。
- **Problem**: 实现用 `Map.of(...)` 返回 **6** 个键
  —— `logs` / `path` / **`files`** / `totalLines` / `returnedLines` / `exists`，
  而注解的示例只有 **5** 个，**漏掉 `files`**；且示例写 `"path":"/logs"`，
  实际返回的是**绝对路径**（本机实测 `/Users/yangjiefeng/.claude-mem/logs`）。
  `/v3/api-docs` 是生成客户端代码的来源，所以这个缺失会传播到任何按 OpenAPI
  生成的 SDK 模型里。
- **Reproduction**（2026-10-04，活体 37777，`?lines=3`）:

  ```json
  {"exists": true, "files": ["claude-mem-2026-10-04.log"],
   "logs": "[2026-10-04 01:53:16.718] [INFO ] [SERVIC] …",
   "path": "/Users/yangjiefeng/.claude-mem/logs",
   "returnedLines": 3, "totalLines": 302}
  ```

  6 个键，其中 **`files` 在注解示例里没有**。
- **对比**：`docs/API.md:2055-2065` 与 `docs/API-zh-CN.md:2039-2047` 的示例
  **六个键齐全**、用的是绝对路径，中文版前文还解释了 `files` 数组的语义
  （今天优先、不足才回落昨天）。**两版人工文档都正确，无需改动。**
- **Status**: ⏸ **记录不修** —— 改 `@ApiResponse` 的示例即改**对外 OpenAPI 契约**
  （沿用 P2-11 / P2-22 / P2-25 的同一判断）。**文档层无需更正**：
  人工撰写的两版 API 文档本来就是对的。
- **复核记录**: 第 236 轮代码方向（Backend）首次审 `LogsController`（13 个 controller 里此前未被作为审查对象的一个）。**三个假设在写成发现前被证伪，全部靠实测而非推理**：①**「截断被 appender 持有的日志文件会产生 NUL 空洞」——证伪。** 用 scratch 文件精确复现机制（持久 `FileOutputStream(append=true)` 写 21 字节 → 旁路 `Files.writeString(p,"")` 截断 → appender 再写）：**结果 size=7、NUL=0、内容 `line-4`**，因为**追加模式强制 `O_APPEND`、每次写都落到当前文件末尾**，根本不存在「记住的偏移量」——不做这个实验就会写成一条假发现。②**「appender 写的文件名与控制器读的不一致」——证伪。** `RollingFileAppender` 写 `${APP_NAME}.log` 而控制器读 `claude-mem-{日期}.log`，看着像不匹配； 但磁盘实况显示**正在被写的是带日期的那个**（01:58 仍在增长），`claude-mem.log` 恒 **0 字节**——项目自带 `ClaudeMemLogAppender`（第 222 行）写的正是同一命名。③**路径穿越不成立**：文件名完全由 `LocalDate.now()` 推导，**无任何用户输入进入路径**；`lines` 钳位实测正确（`0`→1、`-5`→1、`99999`→10000），`0x10`→16 属**已记录的 P2-20**。**核实无误**：`API.md` 与 `API-zh-CN.md` 的示例**六个键齐全**、用绝对路径，中文版前文还解释了 `files` 语义——**两版人工文档本来就正确，无需改动**。

### P2-35: `CortexToolAspect` 结构上无法捕获失败的 `@Tool` 调用，而质量模型恰恰以失败为一档

- **Scope**: `CortexToolAspect.interceptToolExecution()`
  （`CortexToolAspect.java:60` 的 `joinPoint.proceed()` **在 try 块之外**，
  try 只包住第 62-73 行的捕获调用）。
- **Problem**: 工具方法抛异常时，异常从第 60 行直接向上传播，**捕获整段被跳过**，
  调用方拿到的仍是原始异常（这一点是对的），但**这次工具调用在记忆里不留任何痕迹**。
  **关键在于这与后端的设计意图相反**：`QualityScorer` 明确有
  `FAILURE_BASE = 0.20f` 与 `FeedbackType.FAILURE`（第 24-26、59-61 行），
  即**整个 Evo-Memory 质量模型就是围绕「区分成功与失败」建立的**——
  而这条自动捕获路径**一条 FAILURE 都产不出来**。
- **Evidence**:
  | 事实 | 证据 |
  |------|------|
  | 失败有独立评分档 | `QualityScorer.java:26` `FAILURE_BASE = 0.20f`；`:61` `FAILURE, // Task failed` |
  | 捕获跳过失败 | `CortexToolAspect.java:60` 的 `proceed()` 不在 try 内，无 catch 兜底 |
  | **零测试覆盖** | `CortexToolAspectTest` 共 **4** 条：context 激活/未激活、大小输入截断/不截断——**无一条让工具抛异常** |
  | 另一条捕获路径同样如此 | 薄代理只有 `PostToolUse` 钩子，**没有「工具失败」钩子**；故两条路径都产不出失败记录 |
- **Status**: ⏸ **记录不修** —— 修它会让**所有用户的库里开始出现新的失败观测**，
  改变已存储的数据形态，属**产品决策**而非纯 bug 修复（沿用 P2-24「接入属新增特性
  而非修 bug」的同一判断）。修法：把 `proceed()` 包进 try，catch 后**先记录再重抛**
  （捕获本身已 fire-and-forget，不会掩盖原始异常），并补一条「工具抛异常时仍被捕获」
  的测试。**SDK 代码一字未改。**
- **复核记录**: 第 237 轮代码方向（Java SDK）。切入点是读 `interceptToolExecution`
  的控制流时发现 `proceed()` 的位置。取证：`CortexToolAspectTest` **逐条枚举 4 条测试**、
  `QualityScorer` 的评分档与枚举**从文件读**（不用正则数）。

## Processing Rules

- SDK/Demo findings are fixed in place with focused compile/test verification.
- Backend findings are fixed in place when small and safe; otherwise they remain here until the complete acceptance stage.
- Every finding must end as a code fix, a documented design decision, or an explicit skipped status. Reporting alone is not a valid resolution.
- After resolution, append the verification result and commit identifier here before moving the detailed entry to an archive.

## Archived History

The complete historical review log through 2026-05-07 is preserved in [`2026-09-30_backend-review-findings-history.md`](../archive/2026-09-30_backend-review-findings-history.md). Do not modify that archive; future resolved history should use a new dated archive when this file reaches the growth threshold again.

Ten entries whose status is unconditionally resolved — P1-2, P2-1, P2-2, P2-3, P2-4, P2-5, P2-6, P2-7, P2-9 and P2-12 — were moved verbatim on 2026-10-03 (round 225) into [`2026-10-03_backend-review-history-resolved.md`](../archive/2026-10-03_backend-review-history-resolved.md), when this file reached 1008 lines against the `MAX_LINES=1000` threshold. That archive records the selection rule and must not be modified.

A second batch — **P1-3 and P1-4, 79 lines moved verbatim** — went into [`2026-10-03_backend-review-history-resolved-2.md`](../archive/2026-10-03_backend-review-history-resolved-2.md) on 2026-10-03 (round 232), when this file stood at 980 lines and adding P2-30 would have crossed the threshold. **P2-24 was deliberately left behind**: it carries a ⏸ remainder even though its first two parts are ✅ fixed, so it still holds live reasoning rather than history. Verbatim equality of both batches was verified by diffing the extracted block against `git show HEAD` before the source lines were removed.

**Provenance note.** On 2026-10-04 (round 237) the `- **复核记录**:` sections of P2-22 through P2-27 were moved verbatim into [`2026-10-03_backend-review-provenance.md`](../archive/2026-10-03_backend-review-provenance.md), each replaced by a one-line pointer. The file is structurally saturated — 27 entries, 25 of them ⏸ — and the ⏸ rule below protects the **decision reasoning** (Scope / Problem / Status), which stayed. `复核记录` is provenance: which round found it and how the evidence was gathered, and the same text is stored verbatim per round in `patrol-rotation.md` and `doc-review-task.md`. **This is the first move of this kind**; if the ⏸ rule is later read to cover provenance too, the sections can be restored from the archive without loss.

Entries carrying a `⏸` "recorded, not fixing" status stay here on purpose: they hold the reasoning behind each decision and are the live record, not history. P2-11 also stays, because its backend half is still undecided even though the documentation and annotation layers were fixed.
