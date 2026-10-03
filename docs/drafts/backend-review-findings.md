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
> 第 184 轮新增 P2-11（未配置的抽取模板名不被拒绝，拼错的名字与「尚未抽取」得到同样的
> `not_found`、只有 `/latest` 的 `template` 回显字段暴露了差异，且该错误名字已传播进 Swagger 注解
> 与双语 API 文档），已在**注解与文档层**修复并复测；**后端本身仍不校验**未知模板名，
> 返回 400 属对外契约变更，留待后续决策。
> P2 Open 计数仍为 2（P2-8、P2-10）。
> 第 189 轮新增 P2-12（`MemoryRefineService.deepRefineProjectMemories` 无调用方，
> 且其注释谎称自己由 SessionEnd 与定时任务共同触发——正是第 187 轮那处「定时抽取」
> 虚构描述的代码侧残留），注释已改为如实说明，方法与配置键均**刻意不动**，记为已处理。
> 第 195 轮新增 P2-13（`CortexSessionContext` 没有 `userId` 字段，导致
> `CortexMemoryAdvisor` 与 `CortexMemoryTools` **结构上无法**按用户隔离注入给 Agent 的
> 记忆，尽管后端与 SDK 下层都支持），⏸已记录不实现，README 已如实写明，P2 Open 计数仍为 2。
> 第 197 轮新增 P2-14（`ObservationRepository.findNewObservations` 零调用方——它注释明写
> 「for incremental extraction」，而增量抽取从未实现；V16 迁移还专门为它建了复合索引），
> ⏸已记录不实现，四份设计文档的相应断言已更正。P2 Open 计数仍为 2。
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
> 状态 ⏸已记录不修，P2 计数为 2；**已于第 178 轮 Backend 轮修复并复测**，P2 计数回到 2。
> 第 175 轮新增 P2-10（四个 ingest 端点对项目路径的必填性不一致，仅 `/api/ingest/observation`
> 严格），⏸已记录不修。四家 SDK 均已在客户端拦截，故只影响直接调用
> HTTP API 的用户；收紧契约属对外变更，留待 Backend 轮次决策。

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

### ✅ P2-9（已修复，第 178 轮）: `MEMORY_QUALITY_THRESHOLD` 是死配置键，注释描述的过滤器实际被硬编码冻结

- **Scope**: `backend/src/main/resources/application.yml:16`
  （`app.memory.quality-threshold: ${MEMORY_QUALITY_THRESHOLD:0.6}`）。
- **Problem**（**末段结论已被第 178 轮推翻，见下方「第 178 轮修正」——「没有任何代码读取它」
  只对 `grep "app.memory"` 这个检索式成立，检索式漏掉了不含该前缀的写法**）：该键的注释声称
  「Quality threshold for retrieval filtering (Phase 1) /
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
- **Status**: ✅ **已修复并复测**（2026-10-03，第 178 轮 Backend 轮，commit `b3c0865`）。
  原判断「无行为影响、仅误导」是**错的**——见下方「第 178 轮修正」。修复内容：
  `ExpRagService` 的 `private static final float MIN_QUALITY_THRESHOLD = 0.6f` 改为
  `@Value("${app.memory.quality-threshold:0.6}") private float minQualityThreshold`，
  调用点 `ExpRagService:104` → `findHighQualityObservations` 同步更新；该常量已从全仓移除。
  `application.yml:15-21` 的失实注释一并改写。默认值不变，故默认配置下行为与修复前完全一致。
  部署指南 §5.5 的说明同步改为「现已生效，默认 0.6，控制 ExpRagService 经验检索」。
- **第 178 轮修正（此前判断有误）**：第 174 轮只搜了 `grep -rn "app.memory" --include=*.java`，
  于是认定该键「没有任何代码读取」；第 177 轮又找到 `MemoryRefineService` 的两处 `0.6f`
  字面量，便把它归为「该键本该生效的值被硬编码在两处」。两轮都**只找到了 0.6 这个数字，
  没有找到真正使用该数字做过滤的站点**。第 178 轮沿调用链反查发现第三处、也是注释真正
  描述的那一处：

  | 站点 | 原写法 | 说明 |
  |------|--------|------|
  | `MemoryRefineService:217` | `findStaleObservations(…, 0.6f, …)` | 精炼候选门槛，与本键无关 |
  | `MemoryRefineService:287` | `findStaleObservations(…, 0.6f, …)` | 同上 |
  | `ExpRagService:27` | `private static final float MIN_QUALITY_THRESHOLD = 0.6f` | **注释所描述的 quality-aware 经验检索过滤器**，唯一调用方为 `ExpRagService:104` → `findHighQualityObservations`，被冻结为常量 |

  `ExpRagService` 正是 `POST /api/memory/experiences` 的实现，「quality-aware retrieval」
  一词直指它。所以该键并非「无害的死配置」，而是**看起来可调、实际改了毫无反应的旋钮**——
  运维按注释调参会发现结果不变，且没有任何日志提示。
- **根因（第 177 轮设计文档轮补充）**：这个键不是「写了没接」，而是**本该生效的值被硬编码
  在两处**。`MemoryRefineService.deepRefineProjectMemories:217` 与 `refineProject:287` 都向
  `observationRepository.findStaleObservations(projectPath, …, 0.6f, …)` 传入字面量 `0.6f`，
  而同一方法里的 `deleteThreshold`、`staleDays`、`cooldownDays` 都是 `@Value` 注入的。
  也就是说 0.6 这个数字**恰好等于** `MEMORY_QUALITY_THRESHOLD` 的默认值，但改环境变量不会有
  任何效果。这比「未使用的配置项」更具体：它说明接线漏了一处，且现有值与配置默认值巧合一致，
  因此在默认配置下**看不出**任何异常——只有主动改环境变量的运维才会踩到。
  文档方向已在同轮把部署指南 §5.5 的说明从「未被使用」升级为写明这一硬编码事实。
  **（第 178 轮补充：这一段当时只数到 2 处，实际是 3 处，遗漏的正是 `ExpRagService` 那一处。）**
- **第 178 轮活体验证**（`POST /api/memory/experiences`，`{"task":"t","project":"openclaw","count":15}`）：
  | 阈值 | 返回条数 | 最小质量分 | 质量分布 |
  |------|---------|-----------|---------|
  | 默认 `0.6` | 15 | **0.95** | `1.0×5, 0.98, 0.95×9` |
  | `MEMORY_QUALITY_THRESHOLD=0.99` | 15 | **0.80** | 主路径 `1.0×6, 0.95×2`，其余 8 项经 fallback 路径以 0.80–0.85 进入 |

  修复前不可能出现该差异：`MIN_QUALITY_THRESHOLD` 是 `static final`，无任何注入路径，
  且 `quality-threshold` 在 Java 源码中出现次数为 0。
- **同轮核实无误**：`0.3.md` 的代码片段、`23.md` 的成本算术、`type NOT LIKE 'extracted_%'`
  三项（见第 177 轮）经复核仍与实现一致。

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

### P2-12: `deepRefineProjectMemories` 无调用方，且其注释谎称自己有两个触发点

- **Scope**: `backend/.../service/MemoryRefineService.java:203`（方法）与 `:238-240`（注释）。
- **Problem**: 该方法在**全代码库没有任何调用方**。除自身定义外，仅有两处提到它，
  且都是 `StructuredExtractionService` 的**注释**（第 113、120 行），把
  `tryExecuteWithProjectLock` 与它并列为 `projectLocks` 的两个加锁点。
  真正可达的是 `tryExecuteWithProjectLock`（由
  `StructuredExtractionService.reExtractForSession` 在 `SessionController:327`
  经 `PATCH /api/session/{id}/user` 调用）。
  让它显得像活代码的是方法体内那句注释——
  「deepRefineProjectMemories is triggered from both SessionEnd hook and scheduled
  task」——**这句是假的**。定时任务 `scheduledRefineAll` 调的是 `quickRefine`，
  不是它；`quickRefine` 的唯一调用方也正是 `scheduledRefineAll`。
  第 187 轮已据此修正六份设计文档里「定时抽取」的虚构描述，本条是同一问题的代码侧残留。
- **为什么值得记而不只是删掉**: 方法体本身写得没问题——它最后确实调用
  `runExtraction`，代码审查时看这一段会认为「触发器 1 存在」。问题在于没人调用它，
  所以第 184 轮那类「顺着方法体核对文档」的检查根本不会发现这一层。
- **Status**: ✅ **部分修复**（2026-10-03，第 189 轮 Backend 轮）。
  注释已改写为如实说明「本方法当前无调用方」、指出原注释为何不实、列出真正可达的路径，
  并说明其中的 `0.6f` 与活路径 `findRefineCandidates` 里的另一处 `0.6f` 都不可配置。
  **刻意不删方法、不加配置键**：删除会让 `StructuredExtractionService` 的两处注释指向空气；
  而新增配置键属**对外契约变更**——`docs/DEPLOYMENT.md` §5.5 与 ZH 版**已经准确记录**
  「`MemoryRefineService.findStaleObservations` 中另有一处字面量 `0.6`……不由本值驱动」，
  加键反而要求改写这两处已正确的文档。P2 Open 计数仍为 2（P2-8、P2-10），本条记为已处理。

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
- **Status**: ⏸**已记录，不实现**。接上它需要持久化抽取状态（7.md §7.1 提议用
  `type="extraction_state"` 的观测行承载），属新增特性而非修 bug；且抽取状态的
  过期/重建语义应由项目决定。本轮已做的是**如实记录**：23.md §23.5 策略 3/4/5 全部补上
  「designed, not implemented」声明，并给出真实的候选选取路径与排序方向；
  8.md 第 5 条、0.2.md Gap 3、17.md §17.2 三处同一断言一并更正。

## Processing Rules

- SDK/Demo findings are fixed in place with focused compile/test verification.
- Backend findings are fixed in place when small and safe; otherwise they remain here until the complete acceptance stage.
- Every finding must end as a code fix, a documented design decision, or an explicit skipped status. Reporting alone is not a valid resolution.
- After resolution, append the verification result and commit identifier here before moving the detailed entry to an archive.

## Archived History

The complete historical review log through 2026-05-07 is preserved in [`2026-09-30_backend-review-findings-history.md`](../archive/2026-09-30_backend-review-findings-history.md). Do not modify that archive; future resolved history should use a new dated archive when this file reaches the growth threshold again.
