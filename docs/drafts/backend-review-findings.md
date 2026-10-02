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

> **Open 只统计尚未处理的条目**（⏸已记录不修 / 📌待修）。标记为 ✅已修复 或 ✅已跳过 的条目
> 保留在本文件作为可追溯的历史，但**不计入** Open。
> P1-2（导入端点把校验失败报成成功跳过）已于 2026-10-02 第 166 轮 Backend 集中修复并复测通过，
> 降级为已解决条目。第 166 轮另新增 P2-6（缺 `projectPath` 校验导致 opaque 错误），**已当场修复**，
> 故 P2 计数仍为 0；之所以仍登记条目，是因为它改变了 API 响应的 `errorMessages` 内容，属调用方可见变更。
> 第 172 轮新增 P2-7（DLQ 记录混入精炼流水线，因 `type` 改名后排除条件失效），同样**已当场修复并复测**，
> P2 计数仍为 0。
> 第 173 轮新增 P2-8（读取侧无维度路由，检索恒定比 `embedding_1024`），状态 ⏸已记录不修，
> 故 P2 计数为 1。降级行为对调用方可见（`strategy` / `fellBack`），且仅在非 1024 维配置下触发。
> 第 174 轮新增 P2-9（`MEMORY_QUALITY_THRESHOLD` 为死配置键，注释承诺的检索过滤器不存在），
> 同样 ⏸已记录不修，P2 计数为 2。该项无行为影响，仅误导；文档已如实标注。

## Open Findings

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


### P2-1: Java Demo `/refine` response differs from other SDK demos

- **Scope**: `examples/cortex-mem-demo/ManagementController.java` and the Go/Python/JS demo equivalents
- **Problem**: Java returns `{"status":"refinement triggered","project":"..."}`, while the other demos return `{"status":"refined"}`. The Java response is more informative, but the cross-SDK demo contract is inconsistent.
- **Status**: ✅已跳过（2026-09-30）。这是各 Demo 自己的管理接口响应文案差异，不是共享后端 API 契约；Java Demo 的响应包含更多上下文，保持现状可读性更好。本轮不改代码，也不新增跨 Demo 契约测试。

### P2-2: Rate-limit fallback key is unique per call, disabling the limit

- **Scope**: `backend/src/main/java/com/ablueforce/cortexce/service/RateLimitService.java:264` (`generateFallbackKey`)
- **Problem**: When `tryAcquire` receives a null/empty key it falls back to
  `"fallback:" + hash + ":" + UUID.randomUUID().substring(0, 8)`. The random suffix makes every
  call produce a distinct key, so each request lands in a fresh `SlidingWindow` with count=1 and
  is always allowed. The rate limit is silently a no-op on that path, and the 300-second
  cleanup then has to evict these one-shot entries.
- **Current impact**: none on live traffic — the only caller (`IngestionController:132`) builds
  `"tool-use:" + contentSessionId` *after* validating `session_id` is non-blank
  (`IngestionController:122-127`), so the fallback is never reached today. It is a latent defect
  for any future caller that passes null/empty.
- **Proposed fix**: drop the random suffix and keep `"fallback:" + hash(ip-or-thread + minute
  bucket)`. The hash is already privacy-preserving and stable within a minute, which is the
  granularity the comment claims to use.
- **Status**: ✅已修复（2026-10-02）。`generateFallbackKey()` 去掉 `UUID.randomUUID()` 后缀，只保留
  `"fallback:" + hex(hash(identifier + minuteBucket))`。键在同一分钟内稳定，滑动窗口重新生效；
  隐私性不变（仍只暴露哈希），同时不再产生一次性窗口条目。同步移除随之失效的 `UUID` import。
  编译验证：本轮 `mvn clean package -DskipTests` 通过，回归 + EXTRACTION 验收全部通过。

### P2-3: `RateLimitService` javadoc shows a method overload that does not exist

- **Scope**: `backend/src/main/java/com/ablueforce/cortexce/service/RateLimitService.java:21`
- **Problem**: The class-level usage example reads
  `rateLimitService.tryAcquire("user:123", 10, 60)`, but the class only exposes
  `tryAcquire(String key)`; the limits come from `claudemem.rate-limit.*` configuration.
  Anyone copying the example gets a compile error and may conclude per-call limits are supported.
- **Proposed fix**: correct the example to `tryAcquire("user:123")` and state that the threshold
  and window are configured via `claudemem.rate-limit.max-requests` / `window-seconds`.
- **Status**: ✅已修复（2026-10-02）。示例改为 `tryAcquire("user:123")`，并写明阈值与窗口来自
  `claudemem.rate-limit.max-requests` / `window-seconds` 配置。

### P2-4: `ProjectFilterService` is dead code kept alive only by its own unit tests

- **Scope**: `backend/src/main/java/com/ablueforce/cortexce/service/ProjectFilterService.java`
- **Problem**: The class is not annotated `@Service`/`@Component` and has no production caller —
  `grep` over `backend/src/main` finds references only inside the class itself. Its ~40 unit
  tests in `ProjectFilterServiceTest` pass and give the impression of covered behaviour, but no
  request path uses `shouldInclude` or `isUnsafeDirectory`. The class comment already admits it
  "is not currently wired into any processing pipeline".
- **Options**: (a) wire it into the pipeline that should filter project paths (e.g. CLAUDE.md
  generation or observation file capture) — a design change needing its own acceptance; or
  (b) delete the class and its test until a consumer exists, so the suite does not imply coverage
  that no runtime path provides.
- **Status**: ✅已跳过（2026-10-02），保留为工具类。核查依据：`backend/src/main` 内除自身外无任何
  引用，且全仓 `main` 源码没有任何目录遍历（`Files.walk` / `walkFileTree` / `Files.list`）——
  当前设计里根本不存在“项目文件扫描”这条链路，接入等于凭空新增一条管线。删除则要连带删掉 40 个
  正确的工具契约测试，收益为负。类注释已明确声明未接入流水线，故保持原样。
  **复审触发条件**：一旦后端出现目录遍历/CLAUDE.md 写入路径过滤的需求，改为接入本类而非另写一套。

### P1-2: Import endpoints report validation failures as successful skips

- **Scope**: `backend/src/main/java/com/ablueforce/cortexce/controller/ImportController.java:208, 229, 290, 349` (the single-record loops for sessions, summaries, user prompts and the two standalone `/import/*` endpoints).
- **Problem**: Every loop does `if (result.imported()) { …imported… } else { …skipped… }`, but
  `ImportService.ImportResult` has **three** factories, not two: `imported(id)`, `duplicate(id)`
  and `error(message)`. The `else` branch therefore swallows `error(...)` into the skip counter,
  while the response's `errors` / `errorMessages` only ever receive **thrown** exceptions
  (`catch (Exception e) { errors.add(e.getMessage()); }`). A request that fails validation comes
  back looking completely healthy.
- **Reproduction** (verified 2026-10-02 against the live backend): the backend serialises with the
  global SNAKE_CASE strategy, so `sessionId` does not bind from camelCase JSON and
  `importSummary` returns `error("sessionId is required")`. The endpoint answers:

  ```
  POST /api/import/summaries  [{"sessionId":"r160-unique-…", …}]
  {"success":true,"imported":0,"skipped":1,"errors":0,"errorMessages":[]}
  ```

  `success: true`, zero errors, and the record was silently dropped. A second probe with
  `session_id` (snake_case) took the real path and reported a genuine failure correctly, because
  the FK violation is **thrown** rather than returned:
  `{"imported":0,"skipped":0,"errors":1,"errorMessages":["…violates foreign key constraint…"]}`.
  So thrown errors are surfaced and returned errors are not — the same failure class reported
  two different ways depending on where it originated.
- **Impact**: silent data loss during import/migration. A client that mistypes a field name, uses
  the wrong casing, or omits a required field gets a success response and no indication that
  anything was dropped. `ImportResult.duplicate(id)` is distinguishable from
  `ImportResult.error(message)` by `id() == null` (only the error factory leaves it null).
- **The correct pattern already exists in this codebase**: `ImportService.importObservations`
  (`ImportService.java:307-318`) explicitly separates validation failures with
  `result.addError("sessionId is required")` and `BulkImportResult` carries its own `errors`
  field. Only the single-record loops deviate.
- **Proposed fix**: give the four loops a three-way branch —
  `if (result.imported()) … else if (result.id() == null) { errors.add(result.message()); errorsCount++; } else { skipped++; }`
  — and mirror the observations path's naming so all import routes report alike. A cleaner
  alternative is to add an explicit discriminator to `ImportResult` (e.g. a `kind()` accessor)
  rather than relying on the implicit null-id convention, but that changes a public record and so
  is a larger blast radius than this round's Backend mandate.
- **Status**: ✅ 已修复（2026-10-02，第 166 轮 Backend 集中修复）。复审触发条件已满足——
  用 camelCase 载荷复测，`errors` 现为非零。采纳了本条自己提出的「更干净的替代方案」：
  给 `ImportResult` 增加显式判别方法 `isError()`（`!imported && id == null`），而不是让 4 处
  调用点各自依赖 `id == null` 的隐式约定；`isError()` 上写了完整 javadoc 说明三种工厂的判别方式与
  「抛出/返回不对称」的根因。四处循环（session/summary 的批量循环与两个独立端点）全部改为三路分支。
  修复后活体矩阵：缺 `session_id` → `errors:1 ["sessionId is required"]`；缺 `project_path` →
  `errors:1 ["projectPath is required"]`；正常导入 → `imported:1`；同一会话导入两次 →
  `skipped:1, errors:0`（**重复仍计为 skipped，行为不变**）；混合批次 → `imported:1, errors:2`。
  校验先于重复判定，故「既无效又是重复」的记录报为 error 而非 skip。
  改前已核对 `webui/`：其 `POST /api/import` 是写自有 SQLite store 的独立 worker 路由，
  从不调用后端这四个端点，故对 WebUI 零影响。
  回归 45/0/1 + EXTRACTION 25/0/0，基线 `038f93f` / `d5ce380a…`。

### P2-5: `SummaryRepository.findByContentSessionId` returns rows in undefined order

- **Scope**: `backend/src/main/java/com/ablueforce/cortexce/repository/SummaryRepository.java:59-60`,
  consumed by `ImportService.java:378-382`.
- **Problem**: The JPQL had no `ORDER BY`, and the caller takes `existing.get(0).getId()` and
  reports it as the duplicate's id. `mem_summaries.content_session_id` carries a plain index
  (`V1__init_schema.sql:109`, `V13:73`), **not** a unique constraint, so multiple summaries per
  session are structurally allowed — and they exist in quantity. Measured on this instance by
  paging `/api/summaries`: of 896 distinct session ids, **212 had more than one summary**, up to
  33 rows for a single session. With no ordering guarantee, `get(0)` can return a different id
  between calls, so the duplicate id reported to an importer is non-deterministic.
  Note the symptom is **not** observable through the public API: `/api/import/summaries` returns
  only counts (`imported`/`skipped`/`errors`), never the duplicate id.
- **Status**: ✅已修复（2026-10-02）。查询加 `ORDER BY s.createdAtEpoch DESC`，与既有
  `idx_summaries_created (created_at_epoch DESC)` 索引同向，排序可由索引提供；语义不变
  （原本只是想给出一个已存在的 summary id），仅消除不确定性。签名未变，唯一调用方
  `ImportService:378` 无需改动。JPQL 命名查询在 Spring Data 启动期校验，重启后端日志中
  `QuerySyntaxException` / `Validation failed for query` 计数为 0。未添加仓储层测试：后端测试
  全为纯单测，无 `@DataJpaTest` 基建，引入需要 testcontainers 或嵌入式库，超出本修复的分量。
  残留效率观察（未处理）：该查询为取一个 id 却会把最多 33 行全部载入，可改为
  `Pageable`/`LIMIT 1`，但那会变更签名，属于独立优化。

### P2-6: `importSession` / `importSummary` do not validate `projectPath`, producing opaque errors

- **Scope**: `backend/src/main/java/com/ablueforce/cortexce/service/ImportService.java`
  (`importSession`, `importSummary`).
- **Problem**: both methods validate their id field but not `projectPath`, which is `NOT NULL`
  on `mem_sessions` and `mem_summaries`. Omitting it therefore fails at write time rather than
  validation time, and the caller is told something about the database instead of the field.
  Live before the fix:
  `POST /api/import/sessions` → `{"errors":1,"errorMessages":["Could not commit JPA transaction"]}`
  `POST /api/import/summaries` → `{"errors":1,"errorMessages":["could not execute statement
  [ERROR: null value in column \"project_path\" of relation \"mem_summaries\" violates not-null
  constraint…"]}`
  Neither message names `projectPath`.
- **Fix**: both methods now return `ImportResult.error("projectPath is required")` up front.
  Live after: `{"errors":1,"errorMessages":["projectPath is required"]}`.
- **The correct pattern was already in this codebase**: `ImportService.importObservations` already
  validates `sessionId` and `title` explicitly with `result.addError(...)`.
- **Status**: ✅ 已当场修复并复测（2026-10-02，第 166 轮）。之所以仍登记为条目：它改变了 API 响应的
  `errorMessages` 内容（原先是数据库报错，现在是字段名），属于调用方可见的行为变更。

### P2-7: DLQ records were reaching the refinement pipeline (the exclusion no longer matched)

- **Scope**: `backend/.../repository/ObservationRepository.java` — `findLowQualityObservations`,
  `findStaleObservations`, `findOverdueForRefine`, each of which carried
  `AND type != 'extraction_failed'`.
- **Problem**: `ExtractionStorageService.storeDLQ` writes dead-letter rows as
  `type = "dlq_" + templateName` with `source = "dlq"` (into a dedicated `dlq:extraction`
  session). Nothing anywhere writes the type `extraction_failed` any more — a repo-wide
  search finds it only in those three SQL conditions, in documentation, and in archived
  review history. So the exclusion matched nothing, and a DLQ row passed
  `type NOT LIKE 'extracted_%'` unchecked. `storeDLQ` also never sets `quality_score`,
  `refined_at` or `last_accessed_at`, so all three are NULL — which is exactly the profile
  `findStaleObservations` (`last_accessed_at IS NULL OR …`, `quality_score IS NULL OR …`)
  and `findOverdueForRefine` (`refined_at IS NULL OR …`) select for.
  `MemoryRefineService` feeds both query results straight into the refine pipeline, whose
  steps include merging, LLM rewriting and `deleteLowQualityObservations`. A failure record
  could therefore be rewritten, merged away, or deleted — defeating the purpose of a dead
  letter queue. `findLowQualityObservations` was unaffected only because
  `quality_score < :threshold` is NULL for those rows.
- **Reachability**: the DLQ only fills when extraction fails, and `EXTRACTION_ENABLED`
  defaults to false. The live database currently has no DLQ session at all
  (`POST /api/sdk-sessions/batch` with `dlq:extraction` returns `[]`), so this was not
  demonstrated against real data — it is established from the source and from the column
  nullability. Treat it as a latent data-loss path, not an active one.
- **Fix**: the three conditions now read `AND COALESCE(source, '') != 'dlq'`. The
  `COALESCE` is load-bearing and was the main hazard: `mem_observations.source` is nullable
  (`V14__observation_source_and_extracted_data.sql` adds it as `TEXT` with no NOT NULL) and
  about half of all observations have no source — a live sample of 50 returned 26 with a
  source and 24 without — so a bare `source != 'dlq'` evaluates to NULL for those rows and
  would have silently dropped every ordinary observation out of refinement as well.
- **Status**: ✅ 已当场修复并复测（2026-10-02，第 172 轮）。回归 45/0/1 与 EXTRACTION 25/0/0
  均通过；回归套件的 `test_memory_refine_api` 走的就是这条 refine 端到端路径。
- **Untestable here**: the backend has no `@DataJpaTest` infrastructure, so the three
  queries cannot be exercised in isolation — the live acceptance run is the only signal,
  and it cannot cover the DLQ case because no DLQ data exists.
- **Also corrected**: six design documents and both user-facing feature docs described the
  DLQ as `type=extraction_failed` with a scheduled retry task. No such task exists
  (no `@Scheduled` DLQ job) and `findByTypeGlobal` has no callers. Documentation corrected
  in round 172; see `docs/structured-extraction.md` and `docs/drafts/phase-3-design/11.md`.

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

### P2-9: `MEMORY_QUALITY_THRESHOLD` 是死配置键，注释描述的过滤器并不存在

- **Scope**: `backend/src/main/resources/application.yml:16`
  （`app.memory.quality-threshold: ${MEMORY_QUALITY_THRESHOLD:0.6}`）。
- **Problem**: 该键的注释声称「Quality threshold for retrieval filtering (Phase 1) /
  Observations with quality below this are filtered out」，但**后端没有任何代码读取它**。
  逐项核实：`grep -rn "app.memory" --include=*.java backend/src/main` 只返回
  `MemoryRefineService` 的四个注入点（`refine.delete-threshold`、`refine.cooldown-days`、
  `refine.stale-days`、`refine-enabled`）与 `ExtractionConfig` 的
  `@ConfigurationProperties(prefix = "app.memory.extraction")`，**没有 `quality-threshold`**；
  全仓 `grep -rn "quality-threshold" backend/src` 仅命中 application.yml 自身一行。
  检索侧确实存在一个形如 `quality_score < :threshold` 的 SQL
  （`ObservationRepository.findLowQualityObservations:525`），但它的入参来自
  `deleteThreshold`（`MemoryRefineService:153`、`:281`），**不是** `quality-threshold`——
  这正是容易误判的地方：名字与语义都相近，但绑定的不是同一个键。
- **实际影响**：无害但有误导性。设置该变量不改变任何行为；一个按注释理解它的运维
  会以为检索有质量下限保护，实际没有。
- **Status**: ⏸ 已记录不修（2026-10-02，第 174 轮运维/用户指南轮发现）。文档方向已在同轮
  如实修正 `docs/DEPLOYMENT.md` / `docs/DEPLOYMENT-zh-CN.md` §5.5：新增的调参表把
  `MEMORY_QUALITY_THRESHOLD` 标注为「**当前未被使用**」，并说明设置它不产生效果。
  代码侧未改动，因为删除或接线都属于产品决策（要么删键，要么补上注释承诺的过滤器），
  且本轮代码方向为 Go SDK。
- **同轮核实无误**：其余八个新收录的变量均确认被真实读取——`MEMORY_REFINE_DELETE_THRESHOLD`
  用于 `MemoryRefineService:111` 的删除分支、`MEMORY_REFINE_COOLDOWN_DAYS` / `_STALE_DAYS`
  用于 `:43` / `:46`，四个 `EXTRACTION_*` 经 `ExtractionConfig` 的松散绑定
  （`initial-run-max-candidates` → `initialRunMaxCandidates`）正确对应。

## Processing Rules

- SDK/Demo findings are fixed in place with focused compile/test verification.
- Backend findings are fixed in place when small and safe; otherwise they remain here until the complete acceptance stage.
- Every finding must end as a code fix, a documented design decision, or an explicit skipped status. Reporting alone is not a valid resolution.
- After resolution, append the verification result and commit identifier here before moving the detailed entry to an archive.

## Archived History

The complete historical review log through 2026-05-07 is preserved in [`2026-09-30_backend-review-findings-history.md`](../archive/2026-09-30_backend-review-findings-history.md). Do not modify that archive; future resolved history should use a new dated archive when this file reaches the growth threshold again.
