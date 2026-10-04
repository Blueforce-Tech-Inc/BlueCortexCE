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
> 历次压缩的批次与理由统一记在文末 `## Archived History`，**此处不再重复**
> （两处原本记着同一批压缩事件，每次压缩都要改两遍）。
| 轮次 | 条目 | 一句话 |
|------|------|--------|
| 219–233 | P1-3 / P1-4 / P2-24～P2-31 | 代码侧已修的已归档或 ✅；其余 ⏸ 记录不修（契约或公开 API 变更）。**逐条见下方完整条目**，此处不再逐轮铺开 |
| 234–237 | P2-32～P2-35 | 全部 ⏸ 记录不修（Docker 绑定、demo 路由名、OpenAPI 示例、AOP 漏捕获失败） |
| 238 | P2-36 | ⏸ 记录不修（三个同级适配器数值选项校验分歧；「负数该等于什么」无唯一答案） |
| 239 | — | Python `count` 真缺陷**已修**（负数静默返空）；自查更正三份中文版 README 陈旧数字 |
| 240 | — | JS `count` 真缺陷**已修**（`0` 亦照发，比 Python 更重）；API 文档 `count` 语义缺口双语音补 |
| 241 | P2-37 / P2-38 | ⏸ 记录不修（四家 demo `/chat` 方法分歧）；Java `count` 构造器/builder 校验分裂，**修法已写明、留待 Java SDK 方向** |
| 242 | P2-39 | ⏸ 记录不修（`POST /api/import` 外层 `@Transactional` + 逐行 catch = **一行坏数据毁掉整批**，且逐行统计变 500） |
| 243 | P2-38 | ✅ **已修**（紧凑构造器统一校验，4 条新测试，139→143；`ICLPromptRequest` 复核本就正确） |
| 244 | P2-40 | ⏸ 记录不修（四家 SDK **能写 prompts 与 summaries、却都读不回来**；新增公开方法属产品决策） |
| 245 | P2-41 | ⏸ 记录不修（`platform_source` / `content_hash` 等**四家一致不暴露**，SDK 用户无法按平台区分观测） |
| 246 | P2-27 表更正 + P2-42 | ✅ **JS `ObservationUpdate` 类型已修**（八个字段可空，此前 `null` 在 `strict` 下**编译不过**）；⏸ 记录不修（`tsconfig` 排除测试文件，`lint` 查不到测试里的类型错误） |
| 247 | Demo + P2-43 | ✅ **JS / Python demo 的 `extractedData` 守卫已修**（把「清空」与「类型错误」混为一谈，拒掉后端接受的请求）；⏸ 记录不修（Python 两种调用风格对 `None` 语义相反） |
| 248 | Backend + 运维指南 | ✅ **`CursorController.updateContext` 已修**（传 `projectName` 而非 `workspacePath`，对着 1,632 条观测写出「no memories yet」；双实例 A/B：163 → 17,750 字节）；✅ **十处构建命令的 jar 名已修**（三种错名，改为通配符） |
| 249 | Java SDK + API 文档 | ⏸ 记录不修（**P2-44**：`CortexSessionContextBridgeAdvisor` 与手动 `begin/end` 不可嵌套，外层作用域被静默销毁，已补 Javadoc 约束）；✅ **`/api/context/recent` 的 `limit` 已钳制到 [1,20]**（负数此前打到 PostgreSQL 返 **HTTP 500**；双实例 A/B：500 → 200、4610 条无上界 → 20 条）；✅ API.md/中文版 62/62 路径零幻影、query 参数零漂移 |

> 历次压缩的批次与理由统一记在文末 `## Archived History`（最新一批见 batch 3），
> **此处不再重复**——两处原本记着同一批压缩事件，每次压缩都要改两遍。

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
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
### P1-1: `CortexSessionContextBridgeAdvisor.adviseStream` 依赖普通 ThreadLocal，流式下既丢捕获又泄漏会话

- **Scope**: `cortex-mem-spring-integration/cortex-mem-spring-ai/.../advisor/CortexSessionContextBridgeAdvisor.java:79-99`（`adviseStream`）配合 `context/CortexSessionContext.java:14` 的 `ThreadLocal<SessionInfo>`；消费方为 `aspect/CortexToolAspect.java:43`（仅判断 `isActive()`）。
- **Problem**: `adviseStream` 在**调用线程**上 `begin()`，却把清理放进 `flux.doFinally(...)`。Reactor 的 `doFinally` 运行在**发出终止信号的线程**上；任何真实模型客户端（Reactor Netty / WebClient）都会切线程。产生两个后果：
  1. **捕获被静默丢弃** —— 工具实际执行的线程看不到该 ThreadLocal，`CortexSessionContext.isActive()` 为 false，`CortexToolAspect` 直接 `proceed()` 跳过捕获。`@Tool` 自动捕获在流式下等于失效，且无任何日志。
  2. **会话上下文泄漏** —— `doFinally` 清掉的是信号线程（一个空 ThreadLocal），调用线程的 ThreadLocal 永不清除。线程池复用该线程后，`begin()` 因 conversation id 缺失而提前 return 的那条路径**也不会**清理，于是残留的 `sessionId` 会被下一次请求的 `CortexToolAspect` 当作有效会话使用——工具观察被归到**上一个会话**。这是静默的跨会话数据串号。
- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。
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
- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。
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
- **Reproduction**: 原始实测记录已归档 → [`2026-10-04_backend-review-reproduction-4.md`](../archive/2026-10-04_backend-review-reproduction-4.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
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
- **Reproduction**: 原始实测记录已归档 → [`2026-10-04_backend-review-reproduction-4.md`](../archive/2026-10-04_backend-review-reproduction-4.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
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
  | JS | ✓（原样透传给 `JSON.stringify`）**（类型于第 246 轮修正，见下注）** | ✓ | 真清空 |
  | Java | ✗（`@JsonInclude(NON_NULL)`） | ✓ | 只能落 `{}` |
  | **Go** | ✗（`omitempty`） | ✗（`omitempty`） | **完全不能** |
  | **Python** | ✗（`if val is not None`） | ✗（显式 `continue`） | **完全不能** |

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
- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。
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
- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。
  对负数 **抛 `ValidationError`**，而两个最常用的检索方法静默丢弃——**同一份代码里两种
  处理，且代码与 README 都没给出任何理由**。
- **Reproduction**: 原始实测记录已归档 → [`2026-10-04_backend-review-reproduction-4.md`](../archive/2026-10-04_backend-review-reproduction-4.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
### P2-31: Go SDK 仍把负数 `maxChars` 发上 wire，注入被钳到 100 字符

- **Scope**: `go-sdk/cortex-mem-go/dto/experience.go:38` 与 `:46` 的
  `MaxChars int \`json:"maxChars,omitempty"\``。
- **Problem**: `omitempty` 只省略 **0**，**负数照发**（`omitempty` 判定的是 Go 零值，
  而 `-5` 不是零值）。后端解析式是 `maxChars != null ? Math.max(100, maxChars) : 4000`
  （`MemoryController.java:154`），**判 null 不判 0**，于是负数落进 `Math.max(100, -5)`
  → **100**。**Python 曾是同一形态**（`if max_chars:` 只跳过 0），本轮已修（见下）。
- **Reproduction**: 原始实测记录已归档 → [`2026-10-04_backend-review-reproduction-4.md`](../archive/2026-10-04_backend-review-reproduction-4.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
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
- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。
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
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
### P2-34: `GET /api/logs` 的 Swagger 示例漏掉 `files`，且把绝对路径写成 `/logs`

- **Scope**: `LogsController.getLogs()` 的 `@ApiResponse` 示例
  （`LogsController.java:81-82`）。**人类撰写的文档是对的**，错的只有注解。
- **Problem**: 实现用 `Map.of(...)` 返回 **6** 个键
  —— `logs` / `path` / **`files`** / `totalLines` / `returnedLines` / `exists`，
  而注解的示例只有 **5** 个，**漏掉 `files`**；且示例写 `"path":"/logs"`，
  实际返回的是**绝对路径**（本机实测 `/Users/yangjiefeng/.claude-mem/logs`）。
  `/v3/api-docs` 是生成客户端代码的来源，所以这个缺失会传播到任何按 OpenAPI
  生成的 SDK 模型里。
- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。
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
- **Evidence**: 活体/比对证据已逐字迁入 [`2026-10-04_backend-review-reproduction-6.md`](../archive/2026-10-04_backend-review-reproduction-6.md)（P2-35）。
- **Status**: ⏸ **记录不修** —— 修它会让**所有用户的库里开始出现新的失败观测**，
  改变已存储的数据形态，属**产品决策**而非纯 bug 修复（沿用 P2-24「接入属新增特性
  而非修 bug」的同一判断）。修法：把 `proceed()` 包进 try，catch 后**先记录再重抛**
  （捕获本身已 fire-and-forget，不会掩盖原始异常），并补一条「工具抛异常时仍被捕获」
  的测试。**SDK 代码一字未改。**
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
### P2-36: 三个同级适配器（eino / genkit / langchaingo）对数值选项的校验互不一致，且 genkit 的兜底只护住了 per-call 路径

- **Scope**: `eino/retriever.go:37`（`WithRetrieverCount`）、
  `genkit/retriever.go:54`（`WithRetrieverCount`）、
  `langchaingo/memory.go:32`（`WithMemoryMaxChars`）、`dto/experience.go:12,38`。
- **Problem**: 这三个文件是同一个 SDK 里为同一目的写的三块适配层，**却对「非正数怎么办」
  给出三种不同答案**：genkit 有 `if count <= 0 { count = r.count }` 兜底、eino 与
  langchaingo **完全没有校验**。更关键的是 **genkit 的兜底本身是半截的**——它只作用于
  `Retrieve` 收到的**每次调用**的 `input.Count`，而兜底的落点 `r.count` **从未被校验**；
  于是构造函数传入负数时，兜底「回退」到的正是那个负数，**原样发上 wire**。
  测试名 `TestRetrieve_NegativeCount_FallsBackToDefault` 读起来像「负数已被处理」，
  但它把**构造函数传的是合法值 3**、只测 per-call 分支——**真正漏的那条路径无覆盖**。
- **Evidence**: 活体/比对证据已逐字迁入 [`2026-10-04_backend-review-reproduction-6.md`](../archive/2026-10-04_backend-review-reproduction-6.md)（P2-36）。
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

- **Scope**: `examples/cortex-mem-demo/.../ChatController.java:61`（`@GetMapping("/chat")`）、
  `go-sdk/cortex-mem-go/examples/http-server/main.go:170`、
  `python-sdk/cortex-mem-python/examples/http-server/app.py:195`、
  `js-sdk/cortex-mem-js/examples/http-server/app.ts:122`。
- **Problem**: 四家 demo 的端点**名字**经第 235 轮集合对拍已确认 23 个里 21 个同名，
  但**方法这一层从未被比对过**。补上后 `/chat` 暴露出四路分歧：

  | Demo | 方法 | 入参位置 | 响应 | 实质 |
  |------|------|----------|------|------|
  | Go | POST（`checkMethod` 强制） | JSON body | `{response, project, timestamp, memoryContext?, experienceCount?}` | 回显 `Received: …`，**不记录** |
  | Python | POST | JSON body | 同上 | 回显，**不记录** |
  | JS | POST | JSON body | 同上 | 回显，**不记录** |
  | **Java** | **GET** | **查询参数** `?message&project&conversationId&useTools` | `{response, project, conversation_id}`，**无 `timestamp`、无 `memoryContext`** | **真实调用 LLM**，经 `CortexMemoryAdvisor` **自动捕获** |

  即四家共用一个端点名，却在**方法、输入载体、响应结构、行为语义**四个维度上各不相同。
- **Evidence**: 活体/比对证据已逐字迁入 [`2026-10-04_backend-review-reproduction-6.md`](../archive/2026-10-04_backend-review-reproduction-6.md)（P2-37）。
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
### P2-38: Java SDK 的 `ExperienceRequest` 两条构造路径校验不一致——构造器把非正数 `count` 原样发上 wire

- **Scope**: `cortex-mem-spring-integration/.../dto/ExperienceRequest.java`
  的 `Builder.count()`（第 53-55 行）与公开构造器（第 32-40 行）、
  `toWireFormat()`（第 85-104 行）。
- **Problem**: 同一个类有**两条校验强度不同的构造路径**：
  `Builder.count(Integer)` 显式拒绝非正数（`count must be positive (got N)`），
  而**公开构造器完全不校验**，`toWireFormat()` 又**无条件**执行
  `map.put("count", count != null ? count : 4)` —— 于是经构造器传入的 `0` 或负数
  **原样上线**，而后端 `ExpRagService` 对 `count <= 0` **返回空列表且 HTTP 200**，
  调用方拿到「零条相关记忆」而**无法与真实的空结果区分**。
- **Evidence**: 活体/比对证据已逐字迁入 [`2026-10-04_backend-review-reproduction-6.md`](../archive/2026-10-04_backend-review-reproduction-6.md)（P2-38）。
- **严重度低于 P2-36 的姊妹项**：与第 239/240 轮修掉的 Python、JS 不同，
  Java 的 **builder 路径是受保护的**，README 推荐的也正是 builder；
  **只有公开构造器这条路漏**。但「同一个类两条路径校验不一致」本身仍是缺陷。
- **Status**: ✅ **已修（第 243 轮，Java SDK 方向）** —— 改用**紧凑构造器**校验，
  它同时覆盖规范构造器、两个便捷构造器与 builder，**任何构造路径都绕不过**；
  `Builder.count()` 改为复用同一处 `requirePositiveCount()`，避免消息重复漂移。
  `null` 仍然合法（`toWireFormat()` 仍映射为后端默认 4），**合法输入的 wire 行为一字未变**。
  **按断言清扫确认 `ICLPromptRequest` 本就无此问题**：它的守卫在**序列化层**
  （`toWireFormat()` 只在 `maxChars > 0` 时下发），任何构造路径都绕不过——
  这也正是本条的根因：`ExperienceRequest` 走的是**下发**而非丢弃，
  只能依赖构造期校验，而那个校验当初只写在了 builder 上。
  **4 条新测试**（三条构造路径拒绝非正数 / 正数 1 仍上线 / null 仍映射为 4），
  Java client **139 → 143**，总测试数 **192 → 196**（两份 README 已同步）。
  **双向注入验证为真**：移除紧凑构造器（保留 builder 侧校验，即修复前状态）后
  **恰好 1 条失败**——即构造器路径那条，而 builder、正值 1、null 默认三条对照
  **理应不失败**。
- **复核记录**: 第 241 轮 Demo 方向**计划外发现**。起因是文档方向核对四家 SDK README 时
  注意到：它们详述了 `limit` 负数（P2-30），却对 `count` / `maxChars` 非正数**只字未提**——
  而那正是第 239、240 轮连续出缺陷、第 238 轮记为 P2-36 的字段。**一处自我修正**：
  最初假设「Java 是连续第三家同型缺陷」，读代码时发现 `Builder.count()` **有校验**，
  遂把结论收窄为「构造器与 builder 校验分裂」，并用探针把两条路径并排实测后才落笔
  ——**没有把更耸动的说法直接写进记录**。

### P2-39: `POST /api/import` 的外层 `@Transactional` 与逐行 catch 相撞——一行坏数据毁掉整批，逐行统计变成 500

- **Scope**: `ImportController.bulkImport()`（`@Transactional` + 逐行 `try/catch`）、
  `ImportService.importSession()`（同样 `@Transactional`，REQUIRED 并入外层）、
  `mem_sessions.content_session_id varchar(255)` 与 `status varchar(50)`。
- **Problem**: 该端点**专门收集逐行错误**（`stats.addError(result.message())`）并在响应里
  返回 `imported / skipped / errors` 统计——**但这层设计被事务语义彻底击穿**：
  1. `importSession` 是 `@Transactional`（默认 REQUIRED），**并入** `bulkImport` 的同一个事务；
  2. 行数据触发数据库异常（如 `content_session_id` 超 255 字符）时，异常穿出 `importSession`，
     Spring 的事务拦截器把**共享事务标记为 rollback-only**；
  3. 控制器第 2 层 `catch (Exception e)` **吞掉**该异常并继续循环、继续统计；
  4. 方法返回时提交，Spring 抛 **`UnexpectedRollbackException`（"Transaction silently rolled back"）**
     → 调用方拿到 **HTTP 500**，**逐行统计一个都没送到**，**整批合法行全部回滚丢失**。
  即：端点为「部分成功」设计的响应结构，在最需要它的场景下**完全不起作用**。
- **Evidence**: 活体/比对证据已逐字迁入 [`2026-10-04_backend-review-reproduction-6.md`](../archive/2026-10-04_backend-review-reproduction-6.md)（P2-39）。
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

- **Scope**: 后端 `ViewerController` 的 `GET /api/summaries` 与 `GET /api/prompts`；
  四家 SDK 的全部公开方法（Go `client.go`/`client_methods.go`、Python `client.py`、
  JS `client.ts`、Java `CortexMemClient`）。
- **Problem**: 这**不是缺陷而是能力缺口**，但它没有被任何一处写下来，容易被当成疏漏。
  两个端点都**活体可用**、都在 `API.md` 里有完整记载（`/api/prompts` 出现 8 处）、
  WebUI 都在用；而**四家 SDK 没有任何方法能调用它们，四家 demo 也都没有暴露对应端点**
  （三家 demo 里唯一的 "summar" 字样是统计字段 `totalSummaries`，不是端点）。
  与之形成鲜明对比的是**写的一侧齐备**：`POST /api/ingest/session-end`（会话结束即生成摘要）
  与 `POST /api/ingest/user-prompt` 四家**全部**有方法（Go/Python/JS/Java 的
  session-end 与 user-prompt 引用数分别为 3/3、4/3、6/6、2/2）。
  即：**SDK 用户可以产生摘要与提示词，却永远无法把它们读回来**——想读只能自己发 HTTP。
- **Evidence**: 活体/比对证据已逐字迁入 [`2026-10-04_backend-review-reproduction-6.md`](../archive/2026-10-04_backend-review-reproduction-6.md)（P2-40）。
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

- **Scope**: 后端 `ViewerController` 的观测列表响应与 `platformSource` 查询参数；
  四家 SDK 的 `Observation` 响应 DTO 与列表请求参数。
- **Problem**: 与 P2-40 同族的能力缺口，但这次卡在**字段**层面而非端点层面。
  后端**每条观测都返回** `platform_source`（V18 为多平台追踪新增）、`content_hash`
  （V8 新增、P2-29 的去重键组成部分）、`relevance_count`（V17 反馈）与 `step_number`；
  `API.md` **把 `platformSource` 作为查询过滤器写进了文档**（4 处）。
  而**四家 SDK 无一在响应 DTO 上暴露这些字段，也无一在请求侧接受 `platformSource` 过滤**。
  实际后果很具体：**SDK 用户无法区分一条观测来自 Claude 还是 Codex/OpenClaw**，
  也无法按平台筛选——而这正是 V18 加这个字段的目的。WebUI 侧的
  `viewer-bundle.js` 已经在按 `platform_source` 过滤，所以「能用」只在浏览器里成立。
- **Evidence（活体 + 四家逐文件比对）**:
  | 事实 | 证据 |
  |------|------|
  | 后端每条观测都带这三个字段 | 活体 `GET /api/observations?limit=1` → `platform_source='claude'`、`content_hash='1ed602d868bef3f8'`、`relevance_count=0` |
  | 文档把它当过滤器 | `API.md` 中 `platformSource` 出现 **4** 处，含列表端点参数 |
  | 四家 SDK 都不接受该过滤器 | 对四家 SDK 源码 `grep -i platformsource\|platform_source` → **零命中** |
  | 四家响应 DTO 都没有该字段 | `Observation` 字段清单逐个列出：Go / JS / Java / Python **均无** |
  | `narrative` 则四家都有 | Go / JS / Java / Python **均暴露**——说明这不是「响应 DTO 一律精简」，而是有选择 |
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

- **Scope**: `js-sdk/cortex-mem-js/tsconfig.json` 的 `exclude`（含 `"**/*.test.ts"`），
  以及 `package.json` 里 `lint` 脚本就是 `tsc --noEmit`。
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

- **Scope**: `python-sdk/cortex-mem-python/cortex_mem/client.py` 的
  `update_observation()` kwargs 分支，与同文件 `dto.py` 的 `ObservationUpdate.to_wire()`。
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

- **Scope**: `cortex-mem-spring-integration/cortex-mem-spring-ai` 的
  `CortexSessionContextBridgeAdvisor.adviseCall/adviseStream` 与
  `CortexSessionContext.begin/end`。**与 P1-1 无关**：P1-1 是流式下 ThreadLocal 跨线程丢失，
  本条是**单线程内的嵌套**，两条路径独立。
- **Problem**: `CortexSessionContext.begin()` 是裸的 `CURRENT.set(new SessionInfo(...))`、
  `end()` 是裸的 `CURRENT.remove()`——**既无重入保护、也不保存/恢复**。
  advisor 每见到 `CONVERSATION_ID` 就无条件 `begin`，并在 `finally` 里 `end`。
  于是**外层已存在的作用域被覆盖、并在调用返回后被删除**。
  **探针实测**（`CortexSessionContextBridgeAdvisorTest` 旁的一次性用例，未提交）：
  在 `begin("outer-session", "/outer/project")` 已激活时调一次 `adviseCall`，前后状态为
  | 时点 | `isActive()` | `getSessionId()` | `getProjectPath()` |
  |---|---|---|---|
  | 调用前 | `true` | `outer-session` | `/outer/project` |
  | **调用后** | **`false`** | **`unknown-session`** | **（空串）** |
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

**Provenance note, batch 2.** On 2026-10-04 (round 238) the same treatment was applied to **P2-28 through P2-31**, moved verbatim into [`2026-10-04_backend-review-provenance-2.md`](../archive/2026-10-04_backend-review-provenance-2.md) when P2-36 pushed this file to 1035 lines. A **separate** file was used because batch 1 declares itself immutable. Verbatim equality against `git show HEAD` was verified before any source line was removed, and the working file dropped to 993. Round 238 also removed the duplicated compression log from `Current Status`, which duplicated this section's history and had to be updated twice per compression.

Entries carrying a `⏸` "recorded, not fixing" status stay here on purpose: they hold the reasoning behind each decision and are the live record, not history. P2-11 also stays, because its backend half is still undecided even though the documentation and annotation layers were fixed.

**Provenance note, batch 3.** On 2026-10-04 (round 241) the same treatment was applied to **P2-24 and P2-32 through P2-37** — seven sections, 40 lines — moved verbatim into [`2026-10-04_backend-review-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md) when P2-37 pushed the file to 1033 lines, the ninth compression it has needed. Verbatim equality against `git show HEAD` was verified first.

**Reproduction note, batch 4 — the first move of a section other than `复核记录`.** The first three batches had exhausted every `复核记录` section, yet the file still stood at 1029. A **measured transcript** — a captured wire body, a live curl result, a table of row counts — is *reproducible evidence*, not the reasoning behind a decision, so the **Reproduction** sections of **P2-25, P2-26, P2-30 and P2-31** (89 lines) moved verbatim into [`2026-10-04_backend-review-reproduction-4.md`](../archive/2026-10-04_backend-review-reproduction-4.md), each replaced by a one-line pointer. **Scope / Problem / Evidence / Status stayed put.** Every transcript names its date, endpoint and technique, so it is reproducible on demand. This brought the file to **944 lines** — the first compression in four rounds that left real headroom. **This extends the rule rather than merely applying it, so it is flagged for project decision**: if the ⏸ rule is meant to protect the evidence too, the four sections are restorable from the archive without loss.
