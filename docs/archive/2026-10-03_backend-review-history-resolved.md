# Backend Review Findings — 已解决条目归档（第一批）

> **本文件是归档，创建后不得修改。**
> 从 `docs/drafts/backend-review-findings.md` 逐字迁出，**未改写、未合并、未删减**。
> 迁出时点：2026-10-03，第 225 轮（Java SDK + API 文档）——该文件行数达 **1008**，
> 触发 `doc-growth-check.sh` 的 1000 行阈值（`MAX_LINES=1000`；同批 `MAX_BYTES=102400`
> 当时未触发，文件为 88217 字节）。

## 迁出依据

只迁出状态为**纯已解决**的条目（`Status` 为「已修复」「已跳过」「已当场修复并复测并
复测」「部分修复」）。**一律保留**在活动文件中的有三类：

1. `⏸` 记录不修 / 不实现的条目 —— 它们承载「为什么决定不修」的依据，是活的决策记录
   （P2-8、P2-10、P2-13~P2-23、P2-25、P1-1）。
2. 近三轮刚修复的 P1/P2 —— 修复虽已完成，但结论需与相邻未决项对照阅读
   （P1-3、P1-4、P2-24）。
3. **半开的条目** —— P2-11 虽标注「已修复（文档与注解层）」，但活动文件的 Current Status
   明确记载「后端本身仍不校验未知模板名……留待后续决策」，故**不迁出**。

## 迁出条目清单

| 条目 | 状态 |
|------|------|
| P2-1 | ✅已跳过（2026-09-30） |
| P2-2 | ✅已修复（2026-10-02） |
| P2-3 | ✅已修复（2026-10-02） |
| P2-4 | ✅已跳过（2026-10-02），保留为工具类 |
| P1-2 | ✅ 已修复（2026-10-02，第 166 轮 Backend 集中修复） |
| P2-5 | ✅已修复（2026-10-02） |
| P2-6 | ✅ 已当场修复并复测（2026-10-02，第 166 轮） |
| P2-7 | ✅ 已当场修复并复测（2026-10-02，第 172 轮） |
| P2-9 | ✅ **已修复并复测**（2026-10-03，第 178 轮 Backend 轮，commit `b3c0865`） |
| P2-12 | ✅ **部分修复**（2026-10-03，第 189 轮 Backend 轮） |

以下为逐字原文。

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
