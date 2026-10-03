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
> 第 225 轮新增 **P2-25**（`maxChars` 的 Swagger 描述承诺「0 = backend default ~4000」，
> 而后端判的是 `!= null`、不存在该分支，传 0 实得 **100** 字符注入），**记录不修**——
> 改注解即改对外 OpenAPI 契约；**文档层已先行更正**（API 文档双语写明 100 下限与 0 的真实行为）。
> 同轮**已在 Java SDK 侧修复**：`ICLPromptRequest.toWireFormat()` 只判 `null`、会把 0 发上 wire，
> 与 Go（`omitempty`）、Python（`if max_chars:`）不一致，已改为 `maxChars > 0` 才发送；
> **JS SDK 无此防护**，其 example `/chat` 默认传 0，属 JS/TS 方向的发现。
> **P2 Open 计数仍为 2**（P2-8、P2-10）。
> 第 224 轮新增 **P1-4 并已修复**（`/api/context/semantic` 的 `@RequestBody` 简写因本文件
> 第 18 行的 Swagger `RequestBody` import 而解析成**错误的注解**，Spring 遂把裸 `Map`
> 当 `@ModelAttribute`，绑定必抛 `No primary or single unique constructor found`，
> **该端点自上线起 100% 返 500**——包括 `docs/API.md:1085` 那段可复制的 curl 示例；
> 全仓另七个 controller 有同样 import 冲突、全部写成全限定名，**此方法是唯一例外**）。
> 同方法第二处 `q` / `project` 的裸强转一并改为 `instanceof` 守卫（相邻 `limit` 早已如此）。
> **双向注入验证**两处均可复可消；`docs/API.md` 字段表本来就正确、未改。
> **零测试覆盖、零 SDK 暴露**是其长期存活的直接原因。完整验收 45/0/1 + 25/0/0 通过。
> P1 Open 计数仍为 1（P1-1）。
> 第 219 轮新增 **P2-24**：代码侧两处已修——`ObservationFeedbackEntity` 映射了一个
> **V17 从未创建的 `created_at` 列**（任何触及该实体的 JPQL 都会报
> `column "created_at" does not exist`；属**潜伏缺陷**，表 0 行、repository 零调用方，
> 故从未在运行时暴露），以及 `findByObservationIdOrderByCreatedAtDesc` 方法名描述了一个
> 不存在的列（实际按 epoch 排序，零调用方）——两者均为 P1-3 同一类「名字与实际不符」陷阱。
> **未实现部分记录不修**：V17 声明的 Thompson Sampling 基础**完全没有写入方**
> （`observation_feedback` 0 行、`generated_by_model` 0 行、`relevance_count` 恒 0、
> `setRelevanceCount` 零命中），接入属新增特性而非修 bug。
> **P2 Open 计数仍为 2**（P2-8、P2-10）——P2-24 的未实现部分按既定纪律不计入 Open。
> 第 218 轮新增 **P1-3 并已修复**（三个 Spring Data 派生方法按可空且 52% 为 NULL 的
> `created_at` 排序，「取最近 N 条」实际返回最旧的数据；活体实测使 timeline 锚点上下文
> **完全失效**、生成的 CLAUDE.md 漏掉最近 8 天工作、「上次会话的下一步」取错行）。
> 改名为 `…OrderByCreatedAtEpochDesc` 并同步 7 处调用方与 8 处测试 stub，**双向注入验证**
> 症状可复可消；未改任何 DTO / 端点 / 字段，不属对外契约变更。完整验收 45/0/1 + 25/0/0 通过。
> **遗留不修**：`created_at` 本身的稀疏性（数据层变更，会改变既有行取值）留待项目决策。
> P1 Open 计数仍为 1（P1-1）。
> 第 184 轮新增 P2-11（未配置的抽取模板名不被拒绝，拼错的名字与「尚未抽取」得到同样的
> `not_found`、只有 `/latest` 的 `template` 回显字段暴露了差异，且该错误名字已传播进 Swagger 注解
> 与双语 API 文档），已在**注解与文档层**修复并复测；**后端本身仍不校验**未知模板名，
> 返回 400 属对外契约变更，留待后续决策。
> P2 Open 计数仍为 2（P2-8、P2-10）。
> 第 212 轮新增 P2-23（SSE 连接数超 100 返回 500 而非 503，且全仓无心跳广播，
> 死连接最长占用名额 30 分钟），**记录不修**——改状态码属对外契约变更，补心跳会
> 改变流量形态与 emitter 生命周期，均留待项目决策。
> 第 210 轮新增 P2-22（`/api/cursor/projects` 的 Swagger 示例把 ISO-8601 时间戳写成 epoch
> 数字，客户端照此生成会解析失败），**记录不修**——改注解即改对外 OpenAPI 契约；
> 文档层已先行更正（`API.md` / `API-zh-CN.md` 该节原本连响应示例都没有）。
> 第 189 轮新增 P2-12（`MemoryRefineService.deepRefineProjectMemories` 无调用方，
> 且其注释谎称自己由 SessionEnd 与定时任务共同触发——正是第 187 轮那处「定时抽取」
> 虚构描述的代码侧残留），注释已改为如实说明，方法与配置键均**刻意不动**，记为已处理。
> 第 195 轮新增 P2-13（`CortexSessionContext` 没有 `userId` 字段，导致
> `CortexMemoryAdvisor` 与 `CortexMemoryTools` **结构上无法**按用户隔离注入给 Agent 的
> 记忆，尽管后端与 SDK 下层都支持），⏸已记录不实现，README 已如实写明，P2 Open 计数仍为 2。
> 第 197 轮新增 P2-14（`ObservationRepository.findNewObservations` 零调用方——它注释明写
> 「for incremental extraction」，而增量抽取从未实现；V16 迁移还专门为它建了复合索引），
> ⏸已记录不实现，四份设计文档的相应断言已更正。
> 第 200 轮新增 P2-15（`save_memory` 创建共享 manual-memories 会话是 check-then-act，
> 并发下第二次插入必撞唯一约束、整次保存被报成失败），⏸已记录不实现。
> 第 201 轮新增 P2-16（Java SDK 无任何类型化异常，HTTP 状态码只能靠遍历 cause 链取得；
> Go 16 / Python 27 / JS 16 而 Java 为 0，项目自己的 Java demo 已为此写了 DemoErrors），
> ⏸已记录不实现，两份 README 已补上取状态码的可复制做法。P2 Open 计数仍为 2。
> 第 202 轮新增两条，均由「按断言清扫设计文档的成本模型」牵出：**P2-17**
> （`EXTRACTION_MAX_BATCHES` 在随附默认值下**永远不可能生效**——候选已被
> `EXTRACTION_MAX_CANDIDATES=100` 截断，单用户最多 5 批 × 20，够不到 10；
> 单独调高它无效，需同时提高候选上限）与 **P2-18**
> （`reExtractForSession`，即 `PATCH /api/session/{id}/user` 这个**第二个活入口**，
> **绕过全部三个上限**，把整会话观测一次性送入 LLM，**无上界**，
> 超窗失败被 `catch` 吞掉而调用方仍见成功）。两者皆 ⏸已记录不实现
> （均属对外契约变更），P2 Open 计数仍为 2。
> 第 203 轮新增 **P2-19**（Java SDK 的 `triggerRefinement` / `triggerExtraction`
> 走 `executeWithRetrySilent`，**把失败全部吞掉**，调用方拿到正常返回的 `void`
> 而无从得知精炼/抽取根本没跑；Go / JS / Python 三家**都抛错**，且 Go 在
> `client_methods.go:189` 写明理由「NOT fire-and-forget: this is an explicit
> user action, errors must propagate」。Java 自己的 javadoc 声称
> 「Matches the Go, Python and JS SDKs」——重试策略确实一致，但**错误传播**
> 恰恰是它唯一不同的地方，而这正是调用方能感知的部分），⏸已记录不实现
> （改这两处会改变现有调用方可观测到的行为）。P2 Open 计数仍为 2。
> 第 206 轮新增 **P2-20**（**全部 22 个**数值查询参数、11 个端点都静默接受十六进制
> 字面量：Spring 默认转换对 `0x`/`0X` 前缀走 `Integer.decode`。实测 `?lines=0x10`
> 返回 `{"returnedLines":16}`、`?limit=0x10` 返回 16 条，状态码一律 `200` 且响应中
> 无任何迹象。**危害最大的是时间戳**：`/api/timeline` 的 `startEpoch`/`endEpoch`
> 会被解释成 1970 年附近的 epoch 而**静默返回空时间窗**。关键机制细节：
> `?limit=010` 返回 **10 而非八进制 8**，说明仅十六进制前缀走 decode。**排查陷阱**：
> `/api/context/timeline` 对无法解析的 `?limit=abc` 与 `?limit=0x3` 返回**完全相同**的
> `400 {"error":"No anchor found"}`——那是解析成功后的领域错误，只看状态码会误判为
> 「该端点严格拒绝十六进制」而漏掉这条；真正的解析失败返回 Spring 默认的
> `{"status":400,"error":"Bad Request"}`。**状态码不等于原因，必须读响应体**。
> 收紧会把一批 `200` 变成 `400`、波及 11 个端点，属对外契约变更），⏸已记录不实现，
> 但已新增「Query Parameter Conventions / 查询参数约定」一节完整记录（**并因此修正了
> 上一轮把该行为写成「五个 `limit` 端点的属性」的范围过窄**——**修掉一个说法 ≠ 修掉
> 这个说法**，第 205 轮刚写下的内容本轮就发现范围划小了）。P2 Open 计数仍为 2。
> 第 207 轮新增 **P2-21**（`CortexMemHealthIndicator` 在真故障时**不给原因**：
> `healthCheck()` 自己 `catch` 后 `return false`、**从不抛出**，故指示器的
> `catch` 分支在生产中**不可达**、`withException(e)` 的 `error` 键**永不填充**。
> 活体实测（真实 client 指向死端口）`status=DOWN` 但
> `details={service=..., reason=Health check returned false}`、`hasErrorKey=false`
> ——「不可达」与「degraded」两种情况文案完全相同，真正的连接错误被客户端
> `log.debug` 吞掉。**更值得记的是测试钉死了假象**：
> `health_whenClientThrows_returnsDown` 用 mock 制造 client 抛出的状态并断言
> `containsKey("error")`，而**真实 client 永远产生不了该状态**——与第 197 轮
> 「夹具传后端从不下发的值」同类，**测试覆盖的是一个虚构状态**。核实无误的部分：
> `"ok"` 的大小写正确（后端返回小写 `dbReady ? "ok" : "degraded"`），
> 三分支判定本身无误，**缺陷只在「原因丢失」与「测试虚构」**。修复需改
> `healthCheck()` 的行为契约或新增公开 API，⏸已记录不实现），P2 Open 计数仍为 2。
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
- **复核记录**：第 219 轮代码方向，从 P1-3 的同类线索（时间戳列）出发扩展。
  取证：`information_schema.columns` 确认列集；活体 `count(*)` 确认三张表全为 0；
  `grep -rn "ObservationFeedback" main 源码` 确认除实体与 repository 外**零引用**；
  `grep -n "V17" AGENTS.md CLAUDE.md` 确认其完成度声明。

### P1-3: 四个派生查询按 `created_at` 排序，而该列有 52% 为 NULL —— 「最近」返回的是最旧的数据

- **Scope**: `ObservationRepository.findByProjectPathOrderByCreatedAtDesc`（两个重载）与
  `SummaryRepository.findByProjectPathOrderByCreatedAtDesc`。调用方共 7 处：
  `ClaudeMdService:55,108`、`TimelineService:118`、`ContextService:360,904,933,961`。
  本轮已全部改名为 `…OrderByCreatedAtEpochDesc`。
- **Problem**: 派生方法名里的 `CreatedAt` 让 Spring Data 生成 `ORDER BY created_at DESC`，
  而该列**可空且实际大面积为 NULL**——只有 `ImportService` 会显式 `setCreatedAt(...)`，
  主捕获路径 `AgentService:248` 只设 `createdAtEpoch`，且**没有 `@PrePersist`、没有 JPA auditing**
  兜底。PostgreSQL 在 `DESC` 下把 NULL 排在**最后**，于是「取最近 N 条」实际取回的是
  **最旧的那批非 NULL 行**。活体库实测：`mem_observations` 38,088 行中 **19,711 行（51.8%）**
  `created_at IS NULL`；`mem_summaries` 6,590 行中 **6,589 行（99.98%）** 为 NULL。
  **同一文件里 15 处手写 `@Query` 一律用 `created_at_epoch`**，唯独这三个派生方法不是——
  这正是它们显得「本该正确」的原因。
- **Reproduction**（2026-10-03，修复前，活体 37777）：对本项目取最新一条观测
  `b3ce4c56…`（`created_at_epoch` = 2026-04-09 19:59）作为 timeline 锚点——
  ①`GET /api/timeline?anchorId=b3ce4c56…&project=<repo>` 返回
  **`{"observations": [], "anchor_id": "b3ce4c56…"}`**：锚点在该项目 1,362 条观测里按
  `created_at` 排序**落在 500 条窗口之外**，`findAnchorIndex` 返回 -1，**整个锚点上下文功能静默失效**，
  且响应里连 `anchor_index` 字段都没有（走的是提前返回分支，看起来像「这个锚点没有上下文」）。
  ②同一项目 `POST /api/session/start` 生成的 `updateFiles[].content` 里，
  `## Recent Work` 的日期是 **2026-04-01 10:58 / 04-01 12:04 / 04-02 01:24**，
  而该项目真实的最近观测是 **2026-04-09 19:59**——**最近 8 天的全部工作不出现在 CLAUDE.md 里**。
  ③`ContextService.generateContinuation` 取 `summaries.get(0)` 作为「上次会话的下一步」，
  取到的也是错的行。
- **Fix**: 三个方法改名为 `…OrderByCreatedAtEpochDesc`（排序到 `created_at_epoch`），
  7 处调用方与 `TimelineServiceTest` 的 8 处 stub 同步改名，并在方法上写明**为什么不能用
  `created_at`**——避免下次有人「统一风格」改回去。**未改任何 DTO、端点、字段或序列化**，
  返回结构完全不变，故不属对外契约变更。
- **Verification**（双向，2026-10-03）：修复后 `GET /api/timeline?anchorId=b3ce4c56…` 返回
  **6 条观测、`anchor_index: 0`**，CLAUDE.md 首条日期变为 **2026-04-09 19:59**；
  **把三个方法名注入回 `CreatedAt` 并重新构建后，两个症状同时复现**
  （timeline 回到 `observations: []`、CLAUDE.md 回到 2026-04-01），确认修复被真实钉住。
  `mvn package` 通过；`TimelineServiceTest` 的 11 个 error 为**既有问题**（JDK 25 下
  Mockito inline 无法 mock `EmbeddingService`），已用 `git stash` 在修复前的代码上复现确认与本次无关。
- **Status**: ✅ **已修复**（2026-10-03，第 218 轮）。指纹变化（`.java`），完整验收
  45/0/1 + EXTRACTION 25/0/0 全通过。**遗留（未修，需项目决策）**：`created_at` 本身仍是
  稀疏的——`ExpRagService:237` 已为此写了「null 则回退到 epoch」的补丁，`ContextService:917`
  仍把可能为 null 的 `createdAt` 直接放进响应。**补 `DEFAULT`/`@PrePersist` 属数据层变更**，
  且会改变既有行的取值，超出「修排序」的范围，故记录不修。
- **复核记录**: 第 218 轮代码方向发现（Backend）。取证：`information_schema` 无关，
  直接对活体库 `count(*) FILTER (WHERE created_at IS NULL)`；`grep -rn "setCreatedAt"`
  确认只有 `ImportService` 与 `ExtractionStorageService` 会赋值；`grep '@PrePersist|EnableJpaAuditing'`
  零命中；`grep -rn "ORDER BY created_at"` 确认 15 处手写查询全用 epoch 列。

### P1-4: `/api/context/semantic` 的 `@RequestBody` 解析成了 Swagger 注解 —— 该端点自上线起 100% 返 500

- **Scope**: `ContextController.semanticContext(...)`（第 421 行起）。同方法的
  `q` / `project` 裸强转为第二处缺陷，一并修复。
- **Problem**: 该文件第 18 行 `import io.swagger.v3.oas.annotations.parameters.RequestBody`
  是为下面几个方法的 OpenAPI `@ApiResponse` 内容块而引入的。于是方法参数上**简写**的
  `@RequestBody` 解析到的是 **Swagger 那个注解**，不是 Spring 的。Spring 拿到一个
  没有任何可识别 body 注解的裸 `Map`，按 `@ModelAttribute` 处理，绑定时抛
  `No primary or single unique constructor found for interface java.util.Map`，
  **任何请求都返回 500**——包括 `docs/API.md:1085` 里那段可以直接复制粘贴的
  curl 示例。**全仓另外七个 controller 存在同样的 import 冲突，全部写成全限定名**
  （同文件的 `/generate` 在第 271 行也是），**此方法是唯一的例外**。
  第二处：`body.get("q")` 与 `body.get("project")` 是裸 `(String)` 强转，wire 传
  `{"q": 123}` 即 `ClassCastException` → 500；而**相邻的 `limit` 字段早已用
  `instanceof Number` 守卫**，同一方法内两种写法并存。
- **Reproduction**（2026-10-03，修复前，活体 37777）：`docs/API.md:1085` 的示例请求
  → **500**；`{"q": 123, "project": "<repo>"}` → **500**；`{"q": ["a"], "project": "<repo>"}`
  → **500**；`{"q": "valid long query...", "project": 99}` → **500**；`{}` → **500**。
  修复后上述全部 → **200**。
- **Verification**（双向，2026-10-03）：**注入 1**——把注解改回简写，文档示例立即复现
  **500**；**注入 2**——恢复 `q` / `project` 的裸强转，`{"q": 123, ...}` 立即复现
  **500**。两次注入均确认修复被真实钉住。同批回归
  `/api/context/{recent,preview,generate}` 三个兄弟端点**无回归**。
- **Status**: ✅ **已修复**（2026-10-03，第 224 轮）。注解改为
  `@org.springframework.web.bind.annotation.RequestBody` 并加注释记录该陷阱；
  `q` / `project` 改为 `instanceof` 守卫（非字符串视为缺失，落到与「缺失 q」相同的
  「query 不足 20 字符」答案）。`mvn package` 通过；指纹变化（`.java`），完整验收
  回归 45/0/1 + EXTRACTION 25/0/0 全通过。`docs/API.md` 的字段表（`q` 为 string、
  min 20 字符）**本来就是正确的**，未改，端点现已真正可达。
- **复核记录**: 第 224 轮代码方向发现（Backend）。取证：`grep -rn "import io.swagger.v3.oas.annotations.parameters.RequestBody"`
  在八个 controller 中命中，逐一检查其方法参数写法确认只有 `ContextController` 用简写；
  `grep -rn "@org.springframework.web.bind.annotation.RequestBody"` 确认其余七处均为全限定名；
  `mvn package` 后对活体 37777 逐例发请求复现。**零测试覆盖、零 SDK 暴露**是该缺陷
  长期存活的直接原因——`grep -rn "context/semantic"` 在四家 SDK 与全部测试中零命中。

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
- **复核记录**: 第 210 轮文档方向发现。取证：活体 `GET /api/cursor/projects` →
  `{"count":16,"projects":[{"workspacePath":...,"installedAt":"...","projectName":...}]}`，
  `installedAt` 类型实测为 `str`；`CursorService.java:52` 记录分量为 `String installedAt`。


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
- **复核记录**: 第 212 轮代码方向发现。取证：`grep` 全仓确认无 MVC 层异常处理器；
  裸 socket 并发 105 条得到 `{200: 100, 500: 5}` 的首行分布；第 101 条的完整响应头为
  `HTTP/1.1 500` + `Content-Length: 0`；`grep '@Scheduled'` 列出全部四个定时任务
  （ContextCacheService / MemoryRefineService / PendingMessageProcessor /
  StaleMessageRecoveryTask），**均不触及 SSEBroadcaster**。

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
- **复核记录**: 第 225 轮文档方向发现。取证：`curl /v3/api-docs` 读出该描述原文；
  读 `MemoryController:154` 得真实解析式；上表六组取值逐条实测；`grep "0 = backend default"`
  确认**全仓仅此一处**这样的错误描述；`ExpRagService:188` 复核了 DOC-1 的字符串拼接。


## Processing Rules

- SDK/Demo findings are fixed in place with focused compile/test verification.
- Backend findings are fixed in place when small and safe; otherwise they remain here until the complete acceptance stage.
- Every finding must end as a code fix, a documented design decision, or an explicit skipped status. Reporting alone is not a valid resolution.
- After resolution, append the verification result and commit identifier here before moving the detailed entry to an archive.

## Archived History

The complete historical review log through 2026-05-07 is preserved in [`2026-09-30_backend-review-findings-history.md`](../archive/2026-09-30_backend-review-findings-history.md). Do not modify that archive; future resolved history should use a new dated archive when this file reaches the growth threshold again.

Ten entries whose status is unconditionally resolved — P1-2, P2-1, P2-2, P2-3, P2-4, P2-5, P2-6, P2-7, P2-9 and P2-12 — were moved verbatim on 2026-10-03 (round 225) into [`2026-10-03_backend-review-history-resolved.md`](../archive/2026-10-03_backend-review-history-resolved.md), when this file reached 1008 lines against the `MAX_LINES=1000` threshold. That archive records the selection rule and must not be modified.

Entries carrying a `⏸` "recorded, not fixing" status stay here on purpose: they hold the reasoning behind each decision and are the live record, not history. P2-11 also stays, because its backend half is still undecided even though the documentation and annotation layers were fixed.
