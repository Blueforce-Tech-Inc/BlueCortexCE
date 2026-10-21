## Open Findings

### P2-24: V17 反馈机制整体未接线 —— 实体还映射了一个不存在的列

- **Status**: ✅ 已修（324 轮） —— 全文逐字见 [`…-41`][a5]（324 轮）
### P1-1: `CortexSessionContextBridgeAdvisor.adviseStream` 依赖普通 ThreadLocal，流式下既丢捕获又泄漏会话

- **Scope / Evidence**: [`…-8`][a2]（254 轮）。
- **Problem**: `adviseStream` 在调用线程上 `begin()`，却把清理放进 `flux.doFinally(...)`。Reactor 的 `doFinally` 运行在发出终止信号的线程上；任何真实模型客户端（Reactor Netty / WebClient）都会切线程。产生两个后果：
  1. 捕获被静默丢弃 —— 工具实际执行的线程看不到该 ThreadLocal，`CortexSessionContext.isActive()` 为 false，`CortexToolAspect` 直接 `proceed()` 跳过捕获。`@Tool` 自动捕获在流式下等于失效，且无任何日志。
  2. 会话上下文泄漏 —— `doFinally` 清掉的是信号线程（一个空 ThreadLocal），调用线程的 ThreadLocal 永不清除。线程池复用该线程后，`begin()` 因 conversation id 缺失而提前 return 的那条路径也不会清理，于是残留的 `sessionId` 会被下一次请求的 `CortexToolAspect` 当作有效会话使用——工具观察被归到上一个会话。这是静默的跨会话数据串号。
- **实测记录**: [`…-12`][a1]（259 轮）。
### P1-2: Java demo 的 `?path=` 无任何路径校验，且服务绑 `*:37778` —— 同网段可读走本机任意文件
- **Status**: ✅ 已修（323 轮） —— 全文逐字见 [`…-40`][a15]
### P2-8: 读取侧没有维度路由 —— 写入按维度分列，检索恒定比 `embedding_1024`

- **Scope / Evidence**: [`…-8`][a2]（254 轮）。
- **Problem**: `SearchService` 在 PATH 2 的注释自称 "Semantic search with pgvector
  (dimension-aware)"，第 59 行也确实算出了 `int dim = request.queryVector().length`，
  但该变量只用于 debug 日志，实际 SQL 始终与 `embedding_1024` 比较。仓库里
  `semanticSearch768` / `semanticSearch1024` / `semanticSearch1536` 三个方法带有正确的
  分维度 SQL，但全仓零调用方（`grep` 主代码与测试均无命中）。因此这是一个
  写侧已实现、读侧未实现的非对称。
- **实测记录**: [`…-12`][a1]（259 轮）。
### P2-10: 四个 ingest 端点对项目路径的必填性不一致
- **Status**: ⏸ 已记录不修（2026-10-02，第 175 轮 API 文档轮发现）。文档方向已在同轮 于 `docs/API.md` / `docs/API-zh-CN.md` 三个端点各加一段说明，逐条写明「缺失与空串 都会被接受」「`/api/ingest/observation` 是唯一严格的那个」「SDK 会在客户端拦截」， 使读者不必自行推断这层差异。API 文档原本对两者的「必填」标注是正确的 （observation 标 ✅、另三个标 ❌），本轮只是补上未言明的后果。 全文逐字见 [`…-53`][a9]（352 轮）。

<!-- P2-11 已无条件解决，逐字迁入 2026-10-04_backend-review-resolved-3.md -->
### P2-13: Spring AI 集成无法按用户隔离记忆——会话上下文里没有 userId
- **Status**: ⏸已记录，本轮不实现。修它需要给 `SessionInfo` 加字段、给 `begin()` 加重载、把 userId 从调用方一路串到 advisor 与工具，属于新增能力而非修 bug； 且 `CortexSessionContextBridgeAdvisor` 需要知道从何处取 userId（会话 id？应用配置？， 还是新的 `begin()` 入参），这个选择应由项目决定而不是由巡检轮次决定。 本轮已做的是如实记录：`cortex-mem-spring-integration/README.md` 与 `README-zh-CN.md` 新增多用户段落，写明自动路径不做用户隔离、哪些端点其实认 `userId`、以及可用的手工做法。 全文逐字见 [`…-53`][a9]（352 轮）。

### P2-14: `findNewObservations` 零调用方——增量抽取从未实现，却有索引为它而建
- **Status**: ⏸已记录，不实现。接上它需要持久化抽取状态（7.md §7.1 提议用 `type="extraction_state"` 的观测行承载），属新增特性而非修 bug，且过期/重建语义应由项目决定。 已做的是如实记录：23.md §23.5 策略 3/4/5 补上「designed, not implemented」声明并给出真实的 候选选取路径与排序方向；8.md 第 5 条、0.2.md Gap 3、17.md §17.2 三处同一断言一并更正。 按断言清扫的逐处经过：逐字迁入 [`…-32`](../archive/2026-10-06_backend-review-evidence-32.md)（282 轮）。 全文逐字见 [`…-53`][a9]（352 轮）。
### P2-15: `save_memory` 的共享会话是 check-then-act，并发下必然丢失一次保存
- **Status**: ⏸已记录，不实现。常规修法是捕获 `DataIntegrityViolationException` 后重新查询会话再继续，但那要在 `orElseGet` 的懒执行路径里插入一次重试， 改变的是该工具的错误语义与重试行为，属应由项目拍板的契约问题而非巡检轮次的修 bug。 与 P2-13/P2-14 同一套判断。 全文逐字见 [`…-53`][a9]（352 轮）。

### P2-16: Java SDK 没有任何类型化异常，HTTP 状态码只能靠遍历 cause 链取得
- **Status**: ⏸已记录，不实现。补齐意味着给本 SDK 新增公开异常类型 （如 `CortexMemException` / `APIError`），属新增对外 API 而非修 bug， 且会改变所有 25 个方法的异常类型，对已有调用方的 `catch` 行为有影响， 与 P2-13/P2-14/P2-15 同一套判断。本轮已做的是如实记录： 两份 README 的 Error Handling 章节新增「HTTP 状态码不在异常上」小节， 给出实测的异常形态、可直接复制的 `statusOf` 辅助方法、 以及「这是与另三家的已知不对称」这一事实。 全文逐字见 [`…-54`][a30]（353 轮）。

### P2-17: `EXTRACTION_MAX_BATCHES` 在随附默认值下永远不可能生效
- **Status**: ⏸已记录，不实现。改变任一默认值的取值范围属对外配置契约变更。 本轮已在 `23.md` §23.5/§23.7、`docs/structured-extraction.md`、`docs/DEPLOYMENT.md` 四处按各自措辞更正为「随附默认值下不生效」并写明生效条件。 全文逐字见 [`…-54`][a30]（353 轮）。

### P2-18: `reExtractForSession` 绕过全部抽取上限，整会话一次性送入 LLM
- **Status**: ⏸已记录，不实现。接入上限会改变该端点的既有行为，属对外契约变更。 本轮已在 `23.md` §23.4 记录该入口未被任何成本表计价，并在 `StructuredExtractionService` 的既有注释中保持路径事实不变。 全文逐字见 [`…-55`][a31]（353 轮）。

### P2-19: Java SDK 静默吞掉 refinement / extraction 触发失败，另三家都抛错
- **Status**: ⏸已记录，不实现。改这两处会改变现有调用方的可观测行为 （原本被吞掉的异常会开始上抛），属对外行为契约变更，与 P2-13/P2-15/P2-16 同一套判断；且需项目先决定这两条触发路径是否应纳入 fire-and-forget 语义。 全文逐字见 [`…-55`][a31]（353 轮）。
### P2-20: 全部 22 个数值查询参数都会静默接受十六进制字面量

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Problem**: Spring 的默认数字转换会**静默采纳 `0x`/`0X` 十六进制前缀**：先 trim，
  带十六进制前缀走 `Integer.decode`，否则走 `Integer.valueOf`。
  五个端点的活体实测清单（`?limit=0x10` 返回 16 条、`?lines=0x10`、`?maxObservations=0x10`、
  `?startEpoch=0x10`、`?offset=0x2`，状态码一律 200 且响应中没有任何字段表明读的是十六进制）
  与一处必须说明的排查陷阱（`/api/context/timeline` 对无法解析的 `?limit=abc` 与 `?limit=0x3`
  返回完全相同的 400，只看状态码会漏判；真正的解析失败返回 Spring 默认体）逐字见归档。
- 危害：`startEpoch` / `endEpoch` 是时间戳，`0x` 前缀会把它们解释成一个
  1970 年附近的 epoch 毫秒值，静默返回空时间窗而无任何报错。`lines` 会被读成
  一个行数，`maxObservations` 会被读成一个条数。都没有校验、没有警告。
- 对照：布尔参数不受影响（`?includeObservations=0x1` → 400）；四家 SDK 把这些
  参数声明为数字类型，根本无法把十六进制字面量放到线上，故只影响直接调用
  HTTP API 的代码。
- **Status**: ⏸已记录，不实现。收紧会把一批当前的 `200` 变成 `400`， 属**对外 API 契约变更**，且波及 11 个端点；需项目先决定是否值得。本轮代码方向为 Backend，已做的是如实记录：`docs/API.md` + `-zh-CN` 新增「Query Parameter Conventions / 查询参数约定」一节，写明通用规则、完整参数清单与该排查陷阱， 并把 `limit` 小节改为指向它而非重复叙述。
### P2-21: 健康指示器在真故障时不给原因，而测试钉死了一个不可能发生的分支

- **Scope**: → [scope-9][a6]
- **Problem**: `healthCheck()` 自己 `catch` 后 `return false`、从不向外抛出，
  故 `health()` 的 `catch` 分支在生产中不可达，`withException(e)` 写出的
  `error` 键永远不会被填充。
  活体实测确证：死端口时 `status=DOWN`、`hasErrorKey=false`，真实后端则 `UP` ——
  **连接被拒 / 超时 / DNS 失败等真因全部丢失**，「不可达」与「degraded」文案完全相同 → [`…-36`][a4] 第 1 块（第 291 轮逐字迁出）。
- 测试反而钉死了这个假象：`CortexMemHealthIndicatorTest.health_whenClientThrows_returnsDown`
  用 mock 让 client 抛出并断言 `containsKey("error")`——真实 client 永远产生不了该
  状态，故此用例恒真却毫无保护作用（与第 197 轮同类：测试覆盖的是虚构状态）。
- 核实无误：`"ok"` 大小写判定正确，null body / 非 `ok` / 异常三种情况均正确返回
  `false`，UP-DOWN 三分支本身正确 → [`…-36`][a4] 第 2 块（第 291 轮逐字迁出）。
- 实测证据: 逐字迁入 [`…-17`](../archive/2026-10-05_backend-review-evidence-17.md)（269 轮）。
- **Status**: ⏸已记录，不实现。要让原因到达指示器，需要 `healthCheck()` 改为向上抛出（改变既有方法的行为契约，所有调用方的 `catch` 都要重审）， 或为 client **新增公开 API**（如 `getLastHealthFailure()`）供指示器读取—— 两者都属对外契约变更，与 P2-13~P2-20 同一套判断，需项目先定方向。 本轮代码方向为 Java SDK，已做的是如实记录与如实核实（含一次假设被证伪： 初判「环境变量形式无法关闭 `capture-enabled`」，改用真实环境变量复测后 证明 `CORTEX_MEM_CAPTURE_ENABLED=false` 有效——原结论来自 `withPropertyValues` 不模拟环境变量这一探针缺陷）。

### P2-22: `/api/cursor/projects` 的 Swagger 示例把 ISO 字符串写成了 epoch 数字

- **Scope**: → [scope-9][a6]
- **Problem**: 示例写作
  `"{\"projects\":[{\"projectName\":\"my-project\",\"workspacePath\":\"/path\",\"installedAt\":1709000000000}],\"count\":1}"`，
  即把 `installedAt` 标成一个 epoch 毫秒数字。实际类型是 String：
  `CursorService.CursorProjectEntry(String workspacePath, String installedAt)`
  （`CursorService.java:52-55`），活体返回
  `"installedAt": "2026-03-18T17:50:11.192503Z"`。**照此示例生成的客户端会把该字段
  当数字解析并直接失败**；同一控制器里 `GET /api/cursor/register/{projectName}` 的
  `installedAt` 示例同样是 epoch 数字，需一并核对。
- **Status**: ⏸ 记录不修 —— 修 Swagger 注解虽是小改动，但会改变对外发布的 OpenAPI 契约内容，属对外契约变更，留待项目决策。文档层已先行更正： `docs/API.md` 与 `docs/API-zh-CN.md` 的 `GET /api/cursor/projects` 原本**只有 一个代码块、既无描述也无响应示例**（英文版连中文版那一行描述都没有），现已按 活体与源码补上完整响应示例，并明确 `installedAt` 是 ISO-8601 字符串、 `count` 恒等于 `projects.length`（活体 16 == 16，已核对）。
- 复核记录（原文见 [`…-provenance`][a23]，逐轮全文另见 `patrol-rotation.md`）

### P2-23: SSE 连接数超限返回 500（应为 503），且没有心跳，死连接最长占用名额 30 分钟

- **Scope**: → [scope-9][a6]
- Problem 1 —— 状态码语义错误：SSE 连接数触顶（第 101 个客户端被拒）时，后端**没有任何
  `@ControllerAdvice` / `@ExceptionHandler`**，`IllegalStateException` 直穿到容器默认处理并返回
  500；容量耗尽是「服务暂时不可用」，应为 503（或 429）。且 `stream()` 的 `@ApiResponse`
  只声明了 200，该分支在契约中不存在。
- Problem 2 —— 没有心跳，死连接要等下一次事件才被回收：清理只发生在 `SseEmitter` 的
  `onCompletion` / `onError` / `onTimeout` 回调与 `broadcast()` 捕获异常时，全仓**无任何周期性
  心跳广播**，故废弃连接会一直占住名额，直到 `claudemem.sse.timeout-ms`（默认 30 分钟）
  触发 `onTimeout`。
- 实测证据: 逐字迁入 [`…-16`][a24]（267 轮）——含 105 条并发裸 socket 实测（恰好 100 条 200、第 101–105 条 500）、四处 `broadcast()` 调用点行号，以及活体响应 `Content-Length: 0` 不泄漏内部信息。
- **Status**: ⏸ 记录不修 —— 把 500 改成 503 属对外契约变更（客户端与监控 都会看到不同状态码），按既定纪律留待项目决策；补心跳则会改变流量形态与 `SseEmitter` 生命周期，同样需要决策。两者都已写入本条，后端代码一字未改。
- 复核记录（原文见 [`…-provenance`][a23]，逐轮全文另见 `patrol-rotation.md`）

### P2-25: `maxChars` 的 Swagger 描述承诺了一个后端并不存在的「0 = 默认」分支

- **Scope / Evidence**: [`…-8`][a2]（254 轮）。
- **Problem**: 后端没有「0 表示默认」的分支。`MemoryController` 第 154 行写的是
  `int maxChars = request.maxChars() != null ? Math.max(100, request.maxChars()) : 4000;`
  —— 判的是 `!= null`，不是 `> 0`。于是显式传 `0` 会走进 `Math.max(100, 0)`，
  得到 100，而非描述承诺的 ~4000。客户端作者照此实现「不传就传 0」的惯例，
  会把注入的 ICL 记忆上下文截到 100 字符，且没有任何错误提示（HTTP 200）。
- **Reproduction**: [`…-12`][a1]（259 轮）。
### P2-26: Go SDK 的 `omitempty` 让 `facts` / `concepts` / `extractedData` 无法清空，且静默返回「updated」

- **Scope / Evidence**: [`…-8`][a2]（254 轮）。
- **Problem**: 后端 `PATCH /api/memory/observations/{id}` 的语义是
  「**字段存在但为 `null` → 清空；`[]` → `setFacts([])` 清空；字段缺失 → 不变**」
  （`MemoryController:346-375` 三处分支都实测确认）。而 Go 的 `omitempty` 对
  **长度为 0 的 slice/map 同样生效**，于是 `Facts: []string{}` 被整个从请求体里丢弃，
  与 `nil` 无法区分 —— Go SDK 结构上无法表达「清空」。三个字符串字段是指针，
  `Title: ptr("")` 会作为 `"title": ""` 发出，故不受影响。
  表现分两种：只设 `Facts: []string{}` 时 `Validate()` 判 `IsEmpty()` 为真、报
  「at least one field must be provided for update」——用户明确要清空却被告知没提供字段；
  同时设了 `Title` 等其他字段时请求照发，`facts` 被静默省略，服务端回
  `200 {"status":"updated"}` ——静默无操作 + 假成功。
- **Reproduction**: [`…-12`][a1]（259 轮）。
### P2-27: Python SDK 无法清空 `extractedData` —— 与 Go 并列最弱，而它的注释把这一点说成了「对齐 Go」

- **Scope**: → [scope-9][a6]
- **Problem**: 后端 `PATCH` 两种清空写法都能落库（实测 `null` → NULL、`{}` → `{}`），
  而 Python 两种都发不出：`None` 被 `if val is not None` 跳过、`{}` 被那句 `continue` 跳过；
  探针确认二者的 `to_wire()` **都是 `{}`**、`is_empty()` 都是 True，
  故**一条已有 extractedData 的观测无法通过 Python SDK 清空它**。
  活体佐证该字段真实在用、且后端自身从不写 `{}`（38,200 行中非空 20,780、NULL 17,420、
  **`{}` 为 0**）→ [`…-11`][a10]（含四家能力阶梯表与第 246 轮更正）与 [`…-36`][a4] 第 3 块（第 291 轮逐字迁出）。
- 该缺陷为何能存活: `tsconfig.json` 的 `exclude` 含 `"**/*.test.ts"`，而 `npm run lint` 就是 `tsc --noEmit`
  ——**测试文件根本不参与类型检查，于是类型层与断言层之间的裂缝没有任何自动关卡。已独立立为 P2-42**。
- **已修（注释层，行为一字未改）**: `ObservationUpdate` docstring 与 `is_empty()` /
  `to_wire()` 两处 `continue` 注释已按现状改写（428 测试全过）→ [`…-36`][a4] 第 4 块（第 291 轮逐字迁出）。
- **第 362 轮的事实更正（本条标题与 Problem 的一半已被推翻；P2-26 承诺的「下方补注」即本段）**:
  **本条成立，但只对 dataclass 路径成立。** `client.update_observation` 的 **kwargs 路径
  既不查 `None` 也不查空 dict**，故 `content=None` 真的把 `{"narrative": null}` 送上 wire、
  `extracted_data={}` 真的把 `{"extractedData": {}}` 送上 wire；而后端 PATCH 语义是
  **「显式 null ⇒ 清除该字段」——故 Python 其实能清空这两列，只是只有 kwargs 路径能**。
  **本轮活体端到端实测**（建观测 → SDK kwargs 路径 PATCH → `psql` 查 `mem_observations`）：
  kwargs `content=None` 后 **`content` 列为 NULL**、kwargs `extracted_data={}` 后
  **`extracted_data` 列为 `{}`**；两条 dataclass 路径均抛 `ValidationError`、**不发请求**、
  **库两列不变**（对照组）；后端侧另用 curl 直连 PATCH 独立确证同型结果。
  **因此本条 Problem 里「Python 两种都发不出」「**一条已有 extractedData 的观测无法通过
  Python SDK 清空它**」为失实（对 kwargs 路径），Status 里「让 `{}` 发上 wire……**从「不变」
  变成「落 `{}`」**」这一表述的事实前提亦不成立——它今天经 kwargs **已经在落 `{}`**。**
  归档 `2026-10-04_backend-review-evidence-11.md` 的**四家能力阶梯表**（「Python 两种都不能 →
  完全不能」）同样只覆盖了 dataclass 路径；**归档文件创建后不得修改，故更正只落在本处**。
  **原文不改写（追加式审计链只可标注），新证据与正文见 P2-116**。
- **Status**: ⏸ **行为记录不修** —— 改行为只有两条路：让 `{}` 发上 wire （**改变现有调用方的可观测行为**，`extracted_data={}` 从「不变」变成「落 `{}`」）， 或新增显式清空入口（**新增公开 API）。按既定纪律留待项目决策。注释层已先行更正**。
### P2-28: `/api/test/all` 丢弃两个子处理器的状态码，故障时仍返回 200

- **Scope / Evidence**: [`…-8`][a2]（254 轮）。
- **Problem: 同一份「测试连通性」的语义，两个端点给出互相矛盾的失败信号**。
  类级 `@Profile("!prod")` 门控是正确的（第 26 行），四家 SDK 也都零调用方，
  暴露面有限；但**任何用 `/all` 做巡检的脚本或监控，在提供方完全不可用时仍会看到
  200，从而永远不会告警。Swagger 注解（第 116 行）只声明了 200**，
  与实现一致 —— 也就是说**契约本身就是这样声明的**，问题不在契约与实现不符，
  而在这个契约让该端点失去了作为测试端点的意义。
- **实测记录**: [`…-12`][a1]（259 轮）。
### P2-29: tool-use 去重键不是一次调用的身份，且未被原子强制

- **Scope / Evidence**: [`…-8`][a2]（254 轮）。
- **Problem**: 去重键是 `(content_session_id, tool_name, SHA-256(tool_input))`，
  判定条件额外要求 `status <> 'failed'`。三处各自独立地削弱了它：
  1. **键里没有 `tool_response`。** 哈希只覆盖 `toolInput`，故「同样的工具、同样入参、
     结果不同」的调用在前一条仍 `pending`/`processing` 时被**直接丢弃**，而调用方只拿到
     一条日志加 HTTP `200 {"status":"accepted"}`——**与真正入队完全无法区分**。
  2. **`tool_name` 未规范化。** 它是客户端自由文本却参与键比较，故 `Read` 与 `read`
  **P2-29 第 2 点 · 规模实测**：逐字迁入 [`…-35`][a27]（291 轮）。
  3. **检查与写入不是原子的，而唯一的兜底约束并不存在。** `PendingMessageEntity` 声明了
     `@UniqueConstraint(name = "uk_session_tool_input")`，但 `ddl-auto: none` 且
  **P2-29 第 3 点 · 迁移约束取证与死代码后果**：逐字迁入 [`…-35`][a27]（291 轮）。
- **实测证据**: 逐字迁入 [`…-19`](../archive/2026-10-05_backend-review-evidence-19.md)（270 轮）——含具体 input 哈希、近 30 天命名计数、活体 `pg_constraint` 查询结果与手工插入记录。
- **实测记录**: [`…-12`][a1]（259 轮）。
- **Reproduction**: [`…-12`][a1]（259 轮）。
- **P2-31 已整体迁出**: 逐字迁入 [`…-15`](../archive/2026-10-04_backend-review-resolved-15.md)（263 轮）。
### P2-30: 同一个非法 `limit = -5` 在**四家 SDK 有三种行为，且 Go 自身也不一致**（第 271 轮从压缩事故中恢复）
- **Problem**: Java **抛 `IllegalArgumentException`**（且 >100 也抛）、Go 与 JS **静默丢弃**、
  Python **照发（负数在 Python 是真值）而被后端钳成 1**。Go 内部亦分裂：`Search` /
  `ListObservations` 静默丢弃而 `GetExtractionHistory` **抛 `ValidationError`**，无理由说明。
  **本条曾于某次压缩中整块销毁且未进归档，第 271 轮据幸存片段恢复**——`Problem` 为
  **据第 133 轮日志的重建，非逐字**。
- **Impact / Status / Reproduction（逐字幸存）** 与事故经过：见
  [`…-30`](../archive/2026-10-05_backend-review-recovered-P2-30.md)。
- **Status**: ⏸ **记录不修** —— 让 Go 抛错会让**当前能正常返回**的调用方开始失败，属公开 API 行为变更；四家对齐更属跨 SDK 契约决策。**文档层已先行更正**（Go README 双语）。
### P2-63: 一次压缩把 P2-30 **整块销毁且未进归档**——正是 P2-47 事故的复发，而现行校验规则本该拦住它
- **Status**: ✅ **已修（324 轮）** —— 全文逐字见 [`…-41`][a5]
### P2-32: 两个 Dockerfile 都不设 `SERVER_ADDRESS`，默认部署下服务对外不可达；根镜像的 healthcheck 还写死了端口

- **Scope / Evidence**: [`…-8`][a2]（254 轮）。
- **Problem: 两条独立缺陷，叠加后的失败形态是最难排查的那种**：

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
- **Impact: 第 1 条让容器健康检查通过、服务却对外不可达**（healthcheck 走的是
  容器内回环，进程确实在听，所以它是绿的）。第 2 条则命中部署指南自己在
  `DEPLOYMENT.md:845` 给出的排障建议——「`Port 37777 already in use` → 改 `SERVER_PORT`」——
  于是 `docker run -e SERVER_PORT=8080` 会让 healthcheck 去探测 37777，
  **把一个完全健康的应用判成 unhealthy**。`docker-compose.yml` 因为显式写了
  `SERVER_ADDRESS: 0.0.0.0` 而**恰好绕过了第 1 条**，所以问题只在裸 `docker run` 路径上暴露。
- **实测记录**: [`…-12`][a1]（259 轮）。
### P2-33: Go demo 的两个端点名与另外三家 demo 不同

- **Scope**: → [scope-9][a6]
- **Problem: 四家 demo 暴露 23 个端点，其中 21 个完全同名**，只有两个例外，
  且**例外全在 Go 这一家**：

  | 操作 | JS demo | Python demo | Go demo |
  |------|---------|-------------|---------|
  | 批量取观测 | `/observations/batch`（`app.ts:339`） | `/observations/batch`（`app.py:430`） | **`/batch-observations`**（`main.go:470`） |
  | 直接创建观测 | `/observations/create`（`app.ts:304`） | `/observations/create`（`app.py:415`） | **`/create-observation`**（`main.go:771`） |

  Go demo 的 README（如实记录了自己的路径）与 `scripts/go-sdk-e2e-test.sh`
  （第 567 行确实调 `/batch-observations`）**都与代码一致**——**这不是文档错误，
  而是四家 demo 之间的契约分歧**：照着 JS 或 Python demo 的 curl 抄一遍，
  打到 Go demo 上会得到 **404**。
- **Status**: ⏸ **记录不修** —— 改路由名会同时打断 `scripts/go-sdk-e2e-test.sh` 与该 demo README 里已发布的示例，属**跨 demo 契约决策**，按既定纪律 （沿用第 229 轮「四家统一上界与否」的同一判断）留待项目决策。 **文档层已先行补充：Go demo 两份 README 现明写这两个端点的命名与另外三家不同**。
- **复核记录**: [`…-provenance-3`][a11]（第 241 轮；Problem / Status 留本文件）。
### P2-34: `GET /api/logs` 的 Swagger 示例漏掉 `files`，且把绝对路径写成 `/logs`

- **Scope / Evidence**: [`…-8`][a2]（254 轮）。
- **Problem**: 实现用 `Map.of(...)` 返回 **6** 个键
  —— `logs` / `path` / **`files`** / `totalLines` / `returnedLines` / `exists`，
  而注解的示例只有 **5 个，漏掉 `files`**；且示例写 `"path":"/logs"`，
  实际返回的是**绝对路径**（本机实测 `/Users/yangjiefeng/.claude-mem/logs`）。
  `/v3/api-docs` 是生成客户端代码的来源，所以这个缺失会传播到任何按 OpenAPI
  生成的 SDK 模型里。
- **实测记录**: [`…-12`][a1]（259 轮）。
### P2-35: `CortexToolAspect` 结构上无法捕获失败的 `@Tool` 调用，而质量模型恰恰以失败为一档

- **Scope**: → [scope-9][a6]
- **Problem: 工具方法抛异常时，异常从第 60 行直接向上传播，捕获整段被跳过**，
  调用方拿到的仍是原始异常（这一点是对的），但**这次工具调用在记忆里不留任何痕迹**。
  **关键在于这与后端的设计意图相反**：`QualityScorer` 明确有
  `FAILURE_BASE = 0.20f` 与 `FeedbackType.FAILURE`（第 24-26、59-61 行），
  即**整个 Evo-Memory 质量模型就是围绕「区分成功与失败」建立的**——
  而这条自动捕获路径**一条 FAILURE 都产不出来**。
- **Status**: ⏸ **记录不修 —— 修它会让所有用户的库里开始出现新的失败观测**， 改变已存储的数据形态，属**产品决策**而非纯 bug 修复（沿用 P2-24「接入属新增特性 而非修 bug」的同一判断）。修法：把 `proceed()` 包进 try，catch 后**先记录再重抛** （捕获本身已 fire-and-forget，不会掩盖原始异常），并补一条「工具抛异常时仍被捕获」 的测试。**SDK 代码一字未改。**
- **复核记录**: [`…-provenance-3`][a11]（第 241 轮；Problem / Status 留本文件）。
### P2-36: 三个同级适配器（eino / genkit / langchaingo）对数值选项的校验互不一致，且 genkit 的兜底只护住了 per-call 路径

- **Scope**: → [scope-9][a6]
- **Problem: 这三个文件是同一个 SDK 里为同一目的写的三块适配层，却对「非正数怎么办」
  给出三种不同答案**：genkit 有 `if count <= 0 { count = r.count }` 兜底、eino 与
  langchaingo **完全没有校验。更关键的是 genkit 的兜底本身是半截的**——它只作用于
  `Retrieve` 收到的**每次调用**的 `input.Count`，而兜底的落点 `r.count` **从未被校验**；
  于是构造函数传入负数时，兜底「回退」到的正是那个负数，**原样发上 wire**。
  测试名 `TestRetrieve_NegativeCount_FallsBackToDefault` 读起来像「负数已被处理」，
  但它把**构造函数传的是合法值 3、只测 per-call 分支——真正漏的那条路径无覆盖**。
- **附带一处被丢弃的透明信号: 后端把实际生效值**回显在 `ICLPromptResult.maxChars`，
  Go 的 `dto.ICLPromptResult.MaxChars` **确实有这个字段**（`dto/experience.go:46`），
  但全 SDK **无任何非测试代码读它**——`LoadMemoryVariables` 只取 `result.Prompt`。
  即：直接用核心客户端的调用方**能看到钳制，走适配器的调用方看不到**。
- **Status**: ⏸ **记录不修 —— 与 P2-30 同族但不是同一条**：P2-30 记的是核心客户端 `limit` 的四家分歧，本条记的是**适配层** `count` / `maxChars` 的**块内分歧**。不修的理由： ①「负数该等于什么」**没有唯一正确答案**（0 条？不限？回退默认？三个作者都没写）， 单方面选一个就是替项目做产品决策；②只修 genkit 会让 SDK **看起来更不一致** （一个有兜底、两个没有），而修 langchaingo 的「回显被丢弃」半边**必然要新增可观测行为** （多一行日志或一个新错误），属新增特性。**本轮只补了三个选项注释里的取值范围事实说明** （照「文档描述现在而非该有的行为」），**未改任何运行时行为**。
- **复核记录**: [`…-provenance-3`][a11]（第 241 轮；Problem / Status 留本文件）。
### P2-37: 四家 demo 的 `/chat` 在方法、输入位置、响应结构与语义上全部分歧——而这个分歧被 Java demo 自己的 Javadoc 写明后搁置

- **Scope**: → [scope-9][a6]
- **Problem: 四家 demo 的端点名字**经第 235 轮集合对拍已确认 23 个里 21 个同名，
  但**方法这一层从未被比对过**。补上后 `/chat` 暴露出四路分歧：

  | Demo | 方法 | 入参位置 | 响应 | 实质 |
  |------|------|----------|------|------|
  | Go | POST（`checkMethod` 强制） | JSON body | `{response, project, timestamp, memoryContext?, experienceCount?}` | 回显 `Received: …`，**不记录** |
  | Python | POST | JSON body | 同上 | 回显，**不记录** |
  | JS | POST | JSON body | 同上 | 回显，**不记录** |
  | **Java | GET | 查询参数** `?message&project&conversationId&useTools` | `{response, project, conversation_id}`，**无 `timestamp`、无 `memoryContext` | 真实调用 LLM**，经 `CortexMemoryAdvisor` **自动捕获** |

  即四家共用一个端点名，却在**方法、输入载体、响应结构、行为语义**四个维度上各不相同。
- **分歧是「已知且被写下」的**：`ChatController` 自己的 Javadoc 明写
  「The Go, Python and JS demos all answer `POST /chat` with a JSON object」，
  **紧接着就改用 `@GetMapping`**——写下了差异却没有解决。
- **Status**: ⏸ **记录不修** —— 给 Java demo 增加 `POST` 映射属**公开端点契约变更**； 而「Java demo 的 `/chat` 究竟该是真实 LLM 调用，还是与另三家对齐为薄回显」 属**demo 定位的产品决策**。**文档层已先行更正**：Go demo README 原先写 「照抄任一家其余 21 个端点的 curl **只会在这两个上 404**」——**过度承诺**， 已改为区分「拼写一致」与「可互换」，并补上 `/chat` 的方法分歧与 405 实测输出。 **四份 demo README 各自对自身 demo 的描述经核实均准确，未改。Demo 代码一字未改。**
- **复核记录**: [`…-provenance-3`][a11]（第 241 轮；Problem / Status 留本文件）。
<!-- P2-38 已无条件解决，逐字迁入 2026-10-04_backend-review-resolved-3.md -->
### P2-39: `POST /api/import` 的外层 `@Transactional` 与逐行 catch 相撞——一行坏数据毁掉整批，逐行统计变成 500

- **Scope**: → [scope-9][a6]
- **Problem**: 该端点**专门收集逐行错误**（`stats.addError(result.message())`）并在响应里
  返回 `imported / skipped / errors` 统计——**但这层设计被事务语义彻底击穿**：
  1. `importSession` 是 `@Transactional`（默认 REQUIRED），**并入** `bulkImport` 的同一个事务；
  2. 行数据触发数据库异常（如 `content_session_id` 超 255 字符）时，异常穿出 `importSession`，
     Spring 的事务拦截器把**共享事务标记为 rollback-only**；
  3. 控制器第 2 层 `catch (Exception e)` **吞掉**该异常并继续循环、继续统计；
  4. 方法返回时提交，Spring 抛 **`UnexpectedRollbackException`（"Transaction silently rolled back"）**
     → 调用方拿到 **HTTP 500**，**逐行统计一个都没送到**，**整批合法行全部回滚丢失**。
  即：端点为「部分成功」设计的响应结构，在最需要它的场景下**完全不起作用**。
- **旁证与附带观察**: 逐字迁入 [`…-20`](../archive/2026-10-05_backend-review-evidence-20.md)（271 轮）——含「这个坑已被踩过一次、修过其中一个字段而 `varchar` 宽度至今未校验」的既有痕迹，以及 wire 名报 Java 字段名、`API.md` 三个 import 端点无字段清单这两处附带观察。
- **Status**: ⏸ **记录不修 —— 两种修法各改一项已成文的对外契约**： ①去掉 `bulkImport` 的 `@Transactional`（与另外四个同族端点一致）→ 放弃 `@Operation` 明写的 "in a single **atomic** transaction"； ②保留原子性但不再吞掉 rollback-only（重新抛出）→ 调用方仍拿不到逐行统计， 只是从「假 500」变成「真 500」。**真正的修法需要先决定这个端点到底承诺 「全有或全无」还是「逐行部分成功」——那是产品契约决策**。 只补 `varchar` 宽度校验**不足以解决**：它只覆盖最常见的一种触发方式， 而任何未来的数据库异常仍会重演整批丢失，且会让人误以为问题已解决。**后端代码一字未改。**

### P2-40: 四家 SDK 都能写入 prompts 与 summaries，却没有一家读得回来

- **Scope**: → [scope-9][a6]
- **Problem**: 这**不是缺陷而是能力缺口**，但它没有被任何一处写下来，容易被当成疏漏。
  两个端点都**活体可用**、都在 `API.md` 里有完整记载（`/api/prompts` 出现 8 处）、
  WebUI 都在用；而**四家 SDK 没有任何方法能调用它们，四家 demo 也都没有暴露对应端点**
  （三家 demo 里唯一的 "summar" 字样是统计字段 `totalSummaries`，不是端点）。
  与之形成鲜明对比的是**写的一侧齐备**：`POST /api/ingest/session-end`（会话结束即生成摘要）
  与 `POST /api/ingest/user-prompt` 四家**全部**有方法（Go/Python/JS/Java 的
  session-end 与 user-prompt 引用数分别为 3/3、4/3、6/6、2/2）。
  即：**SDK 用户可以产生摘要与提示词，却永远无法把它们读回来**——想读只能自己发 HTTP。
- **一处探针自身出错并先识别再采信**: [`…-11`][a10]（258 轮）。
- **Status**: ⏸ **记录不修 —— 补一个方法是新增公开 API**，按既定纪律 「新增公开 API 留待项目决策、不单方面实施」。且这不是「某一家漏了」的缺陷： **四家完全一致地缺失**，因此它要么是有意的范围划定、要么是共同的疏漏， 两种解读都指向需要项目层面拍板而非某轮自行补齐。 **若将来实施**，需注意与既有分页约定对齐：这两个端点与 `/api/observations` 共用 `Math.min(Math.max(1, limit), MAX_PAGE_SIZE)` 的钳制（`API.md` 已记载）， 且 `hasMore` 是**驼峰而条目内字段是 snake_case**——Go 的 DTO 已按此混合约定建模 （`dto/observations.go:28` 有 ⚠️ 注记），新方法应复用同一约定。**四家 SDK 代码一字未改。**

### P2-41: `platform_source` 等四个字段后端每条观测都在返回、WebUI 也在按它过滤——而四家 SDK 既不暴露、也不接受过滤

- **Scope**: → [scope-9][a6]
- **Problem: 与 P2-40 同族的能力缺口，但这次卡在字段**层面而非端点层面。
  后端**每条观测都返回** `platform_source`（V18 为多平台追踪新增）、`content_hash`
  （V8 新增、P2-29 的去重键组成部分）、`relevance_count`（V17 反馈）与 `step_number`；
  `API.md` **把 `platformSource` 作为查询过滤器写进了文档**（4 处）。
  而**四家 SDK 无一在响应 DTO 上暴露这些字段，也无一在请求侧接受 `platformSource` 过滤**。
  实际后果很具体：**SDK 用户无法区分一条观测来自 Claude 还是 Codex/OpenClaw**，
  也无法按平台筛选——而这正是 V18 加这个字段的目的。WebUI 侧的
  `viewer-bundle.js` 已经在按 `platform_source` 过滤，所以「能用」只在浏览器里成立。
- **一处探针自身出错并先识别再采信**: [`…-11`][a10]（258 轮）。
- **Status**: ⏸ **记录不修 —— 与 P2-40 同一判断：补字段属新增公开 API**， 且**四家完全一致地缺失**，说明要么是有意的范围划定、要么是共同疏漏， 都需要项目层面拍板而非某轮单方面扩大某一家的 DTO。 **若将来实施**，注意 `platform_source` 已经是列表端点的**过滤维度**而非纯展示字段， 补齐时应同时覆盖**响应字段与请求过滤参数**两侧，否则只补一半仍然无法按平台检索。 **四家 SDK 代码一字未改。**

### P2-42: JS SDK 的 `tsconfig.json` 把测试文件排除在类型检查之外——`npm run lint` 查不到测试里的任何类型错误

- **Scope**: → [scope-9][a6]
- **Problem**: 这两项叠加的结果是**整个测试套件从不参与类型检查**。
  `include` 是 `src/**/*`（测试文件确实在里面），但 `exclude` 又把它们摘了出去，
  于是 `tsc` 只检查 `src` 下的非测试源码。活体证据：往
  `src/__tests__/client.test.ts` 里注入 `const __bad: string = 42;`，
  `npm run lint` 依然退出 0、无任何输出。
  后果是类型层与断言层之间可以长期存在裂缝而没有任何自动关卡——
  第 246 轮修的那个 P2-27 能力表错判正是这样活下来的：
  `ObservationUpdate` 声明为 `title?: string`，而**本 SDK 自己的测试**
  （名为 "should accept null fields for PATCH clear semantics" 的用例）
  必须写 `null as unknown as string` 才能表达它声称在测的能力，却一直全绿。
- **Status**: ⏸ 记录不修 —— 修法是加一道 `tsconfig.test.json`（`extends` 主配置、 覆盖 `exclude`）并并入 `lint`。这属构建配置变更，且一旦接上就会一次性暴露 三个测试文件（含 `examples/http-server/parse-int-param.test.ts`）里既有的潜在类型错误， 影响面超出单轮范围，留待项目决策。 验证方法（首版区间短了 4 行，段尾补入 `-34`）：逐字迁入 [`…-33`][a25] 与 [`…-34`][a26]（282 轮）。

### P2-43: Python SDK 的两种调用风格对 `None` 的含义相反——dataclass 路径丢弃它、kwargs 路径原样发上 wire

- **Scope**: → [scope-9][a6]
- **Problem**: 同一个 `None` 在两条路径上语义不同，且只有 kwargs 那条符合 PATCH 的清空语义。
  - dataclass 路径：`ObservationUpdate(title=None).to_wire()` → `{}`（`if val is not None` 跳过），
    `is_empty()` → True，于是纯清空请求被当成「空更新」而根本发不出去。
  - kwargs 路径：`update_observation(id, title=None)` 里是
    `for kwarg, wire_key in ObservationUpdate._WIRE_FIELDS.items(): if kwarg in kwargs: body[wire_key] = kwargs[kwarg]`
    ——原样拷贝、不做 None 过滤，于是 `body = {"title": None}`，
    `if not body` 为假（非空字典），**`{"title": null}` 真的上了 wire 并清空了字段**。
  - 探针实证：`ObservationUpdate(title=None)` → `to_wire() == {}`、`is_empty() == True`；
    而经 demo 活体 `PATCH {"title": null}` → 200 且 `mem_observations.title` 确实变 NULL。
  - 对 P2-27 的影响：该条能力表把 Python 判为「✗（`if val is not None`）」——
    这对 dataclass 路径成立，对 kwargs 路径不成立。按「按断言清扫」的标准，
    那行只覆盖了一半的调用面，应当标注。
- **第 363 轮的补注（本条目在第 247 轮写下的「已在下方补注」，直到本轮才真正落地）**:
  那句补注从未出现在本条目里。 第 247 轮与 P2-26 都写着「P2-27 的能力表应当立即标注
  kwargs 路径……已在下方补注」，而本条目从那时起只有 Problem / Status 两段，
  无任何提及 kwargs 路径的字样；本轮已就地补上（见上方第 362 轮的事实更正）。
  本条的证伪也从未被同步到 JS SDK 的复述点——`js-sdk/cortex-mem-js/src/dto/observation.ts`
  与其**双语 README** 至今仍写着「四家里只有 JS 能把字符串字段清空为 NULL、Python 跳过 `None`」，
  而那句话在第 247 轮就已经被本条目证伪了。本轮已更正（P2-117）。
  另：本条的证伪在第 362 轮被**用 SDK 本身**（而非 demo）做了活体端到端复验——
  kwargs `content=None` 之后 `mem_observations.content` 列**实为 NULL**（`psql` 直查），
  见 P2-116。
- **Status**: ⏸ 记录不修 —— 两种收法都改变现有调用方的可观测行为： ①让 dataclass 路径也发 `None`（原本静默不发请求的 `ObservationUpdate(title=None)` 会突然发出一次 PATCH）； ②让 kwargs 路径也过滤 `None`（本条是真缺陷：今天经 kwargs 清空是能成功的， 改掉等于让已经能用的能力失效，且 Python demo 的 PATCH 正是走 kwargs 路径）。 真正的修法要先决定哪种写法才是 SDK 想支持的清空方式，属 API 契约决策。 但 P2-27 的能力表应当立即标注 kwargs 路径——那是文档层的事实更正，已在下方补注。

### P2-44: `CortexSessionContextBridgeAdvisor` 与手动 `begin/end` 不能嵌套——外层作用域会被静默销毁

- **Scope**: → [scope-9][a6]
- **Problem**: `CortexSessionContext.begin()` 是裸的 `CURRENT.set(new SessionInfo(...))`、
  `end()` 是裸的 `CURRENT.remove()`——**既无重入保护、也不保存/恢复**。
  advisor 每见到 `CONVERSATION_ID` 就无条件 `begin`，并在 `finally` 里 `end`。
  于是外层已存在的作用域被覆盖、并在调用返回后被删除。
  探针实测（一次性用例，未提交）：外层 `begin("outer-session","/outer/project")` 已激活时
  调 advisor，断言「外层应当存活」失败——sessionId 变 `unknown-session`、projectPath 变空串，
  而调用内部看到的是 advisor 自己的上下文：内层正确、外层被毁，全程无异常无告警。
- 为什么不是示例代码的 bug: `ChatController` 刻意把两条路径二分——
  带 `conversationId` 的请求走 bridge 且不手动 `begin`；不带的手动 `begin`，
  而 bridge 因无 `CONVERSATION_ID` 直接透传。从不嵌套。故这是误用场景，
  而非已交付代码里的活 bug。
- **Status**: ⏸ 记录不修 —— 两种收法都改变现有调用方的可观测行为： ①在 `CortexSessionContext` 上加保存/恢复（需要把 private 的 `SessionInfo` 暴露为公开类型， 属**新增公开 API**）；②已激活时跳过 `begin/end`、让外层胜出（零新增 API， 但当内外 session 不同时，内层会被记到外层的会话上，属跨会话串号）。 真正的修法要先决定两者冲突时谁该赢，属产品决策。 已做的零风险部分：在 `CortexSessionContextBridgeAdvisor` 的类 Javadoc 中 写明「不可嵌套」这一约束、外层被静默销毁的实测后果、以及 demo 为何二分—— 纯注释，行为一字未改。现有 6 个该 advisor 的测试无一覆盖嵌套。

### P2-45: 会话启动的 `projects` 字段能生成多项目上下文、API.md 也写了——而四家 SDK 一律发不出去

- **Scope**: → [scope-9][a6]
- **Problem**: 后端的会话启动契约有 7 个字段
  （`session_id` / `project_path` / `cwd` / `user_id` / `projects` / `is_worktree` / `parent_project`），
  四家 SDK 一律只暴露 3 个（`session_id`、`project_path`、`user_id`）：
  Go 是 `dto.SessionStartRequest` 结构体，Java 是同名 record，JS 是同名 interface，
  Python 是 `start_session(session_id, project_path, user_id=None)` 三个位置参数。
  **其中 `projects` 是真实生效的能力**——`SessionController` 在它含逗号时走
  `parseProjectsParam` 并生成多项目上下文。**活体实测（同一 `project_path`，只差 `projects`）**：
  不带时返回 `"# phase3-acceptance-test — no memories yet"`，
  带上 `"projects":"openclaw,/tmp/phase3-acceptance-test"` 后返回
  `"# openclaw recent context … 📊 25 observations | 📖 6,109 read tokens"`。
  后果：SDK 用户永远拿不到多项目上下文，只能自己发 HTTP。
  `API.md` 的 `/api/session/start` 字段表完整记载了 `projects`（"Multi-project support,
  comma-separated"），所以这不是未公开特性。
- **Status**: ⏸ 记录不修 —— 补字段是**新增公开 API**，且四家完全一致地缺失， 与 P2-40 / P2-41 同理：要么是有意的范围划定、要么是共同疏漏，都指向项目层面拍板。 若将来实施，注意四个模型的形态各不相同（Go/Java/JS 是对象字段、Python 是位置参数）， 需一并考虑向后兼容。
- 同区域另两处事实（均记录，留待各自轮次处理）: 逐字迁入 [`…-11`][a10]（258 轮）。

### P2-46: 验收脚本的 `cleanup()` 定义了却从未被调用——其幻影端点从未生效，而 Test 6 的前提因此早已不成立

- **Scope**: → [scope-9][a6]
- **Problem**: 这条比一般的文档错误重要，因为它削弱的是我自己每轮据以判断的验收门控。两处缺陷叠加：① `cleanup()` 在全文件只出现一次（定义处），`main` 里没有调用、也没有 `trap ... EXIT`，`bash -n` 通过——它是死代码；② 它内部那行清理请求 `DELETE /api/memory/observations?project_path=...` 活体 404（该前缀下只有 `/api/memory/observations/{id}` 一条路径），而 `|| true` 兜底把 404 吞掉，这个失败永远不会让脚本失败。
- **Status**: ⏸ 记录不修 —— 属脚本方向，不在本轮（Python SDK）的代码轮换内；且**若真把清理接上，Test 6 会切回 `not_found` 分支、累积数据会被删除**，属于会改变门控自身行为的改动，需在自己的轮次里单独做 A/B。
- 对既有结论的影响（必须如实记录）: 第 249–251 轮的「EXTRACTION 25/0/0 全通过」仍是 25 条全部通过，但 Test 6 走的是兜底分支、Test 14 的断言已因数据累积而恒真。这不使任何一条已记录的修复失效（被修代码路径本就在别处被独立验证），但今后引用该数字须带上这两条限定（基线区块已写明）。
- 同族事实（已修）: 同一幻影端点也出现在设计文档 `phase-3-design/25.md:699`，已改为脚本真正使用的「先 `GET /api/observations` 取 id、再逐条 `DELETE /api/memory/observations/{id}`」并实跑验证（观测数 1 → 0）。该脚本本身行为正确，只有验收脚本是坏的。
### P2-47: `API.md` 双语把 `is_worktree` / `parent_project` 当正式字段记载并写进示例 body——而两者只进一条 `log.info`

- **Scope**: → [scope-9][a6]
- **Problem**: 文档描述的是尚未实现的能力。逐行核实（251 轮）：
  `SessionController.java:126-127` 读出 `isWorktree` / `parentProject`，
  两者**此后只出现在 142–144 的 `log.info` 里**；真正建会话的
  `initializeSession(contentSessionId, projectPath, null)`（`:152`）第三参传的是 `null`，
  worktree 信息不落库、不参与任何逻辑。
  而本该让该能力真正生效的 `WorktreeDetector` 服务，
  `grep -rn "WorktreeDetector" backend/src/` 除自身文件外零命中——零调用者。
  但 `API.md` 两版都用正式字段表条目（"Whether this is a worktree" /
  "Parent project name (worktree mode)"）记载，并写进示例 body。
  读者据此会以为传了就有用。
- **Status**: ⏸ 实现侧记录不修；**文档侧已于第 254 轮更正**（API 文档方向）。 改文档而非接上 `WorktreeDetector`，是因为后者属新增行为、同样需项目拍板。 按断言清扫的逐处经过（首版区间短了 4 行，段尾补入 `-34`）：逐字迁入 [`…-33`][a25] 与 [`…-34`][a26]（282 轮）。

### P2-49: `scripts/start.sh` 把后端 jar 的版本号钉死——而它是 TESTING.md 推荐的启动方式

- **Scope**: → [scope-9][a6]
- **Problem**: 与第 248 轮修掉的「jar 名写错 artifactId」不是同一类——那批名字从来不可能产出，
  这批名字是对的、只把版本钉死了。今天与磁盘一致，版本号一变全线失效，
  且失效方式不同：`start.sh:97` 直接 `Missing $JAR_PATH; rerun with --build` 拒绝启动。
  而 `docs/TESTING.md:212` 把 `scripts/start.sh` 列为推荐启动方式，
  一次版本变更就让文档推荐的启动路径不可用。四个脚本 + 两份 `evo-memory-implementation*.md`
  共 6 处（不含已修的 DEVELOPMENT.md 4 处）；当前 jar 存在故今天不可复现——
  这是由版本变更触发的潜伏缺陷，不是当前故障。
- **Status**: ⏸ 记录不修 —— 属脚本方向，不在 Backend 轮换内。修法直接 （`JAR_PATH` 改 glob 取首个匹配，或用 `./mvnw spring-boot:run`），但需连带 `start-all.sh` 的启动顺序与 `.env` 加载一起看，并在真实版本变更下验证一次。

### P2-50: 读 Cursor 注册表失败被当成「空注册表」，而这个空结果会被写回
- 已解决条目：正文 [`…-resolved-13`][a12]（第 260 轮无条件已解决，按第 250 轮先例整体迁出）。
### P2-51: `projects` 只在值里含逗号时才生效——传单个值被静默忽略，与不传完全等价

- **Scope**: → [scope-9][a6]
- **Problem**: 判定是 `projectsParam.contains(",")`——只有含逗号才走多项目分支。
  传单个值会落进单项目分支、该值被完全丢弃，既不报错也不告警，
  返回结果与**根本不传 `projects` 逐字相同**；文档只写「comma-separated」，
  没说单个值等于不传。另有 `@Schema` 写 project paths 而
  `parseProjectsParam` 的 Javadoc 写 project names，对同一值给出两种定义。
- **Status**: ⏸ 记录不修 —— 改判定会改变现有调用方的行为（原本按 `project_path` 生成、改后按该值生成），属语义变更，需项目拍板。 **文档侧已于第 254 轮更正**（`@Schema` 与 API.md / 中文版的逐字改动）→ [`…-38`](../archive/2026-10-06_backend-review-evidence-38.md) 第 2 块（第 292 轮逐字迁出）。

### P2-52: `target/` 里残留 10 个源码已删的测试类——其中一个仍在失败，使 `mvn test` 退出非零
- 已解决条目：正文 [`…-resolved-13`][a12]（第 260 轮无条件已解决，按第 250 轮先例整体迁出）。
### P2-53: Go SDK 的 `WithTimeout` 把「太小的值」重置成默认最大值——请求 50ms 实际得到 30s
- 已解决条目：正文 [`…-resolved-13`][a12]（第 260 轮无条件已解决，按第 250 轮先例整体迁出）。
### P2-54: Python SDK 另有两处裸 TypeError——且既有测试的 docstring 早已写明我踩的那个坑
- 已解决条目：正文 [`…-resolved-13`][a12]（第 260 轮无条件已解决，按第 250 轮先例整体迁出）。
### P2-55: 四个 demo 为同一件事立了同一份文法契约，却 2:2 分裂——而且与后端一致的那两家是「碰巧」一致的

- **Scope**: → [scope-9][a6]
- **Problem**: 四个 demo 各自有一段整数解析，文法被写成了同一句话——
  「optional sign, then digits only —— no hex, no exponent, no trailing garbage」，
  枚举的例子（`0x10` / `1e3` / `1.5` / `10abc` / `1_0`）**全是 ASCII**。
  Java 侧那句 Javadoc 还额外断言「**The rule implemented here is the one the other three
  demos already agreed on**」，Python 侧写着「A regex pins the grammar ... so all four
  demos match the backend」。但对同一个输入，四家给出两种答案：
  `Character::isDigit` 与 Python 的 `\d` 都是Unicode 感知的，
  `strconv.Atoi` 与 **JS 的 `\d`（规范定义即 `[0-9]`）是纯 ASCII**。
  这是这条不变式的第二次被打破（第 211 轮修过 `?limit=%20` 一族，
  第 252 轮又发现 Python 不校验 `limit` 范围）。
- 不修的理由: 修哪一边都是**改动 HTTP 对外契约**，而方向无法由证据确定——
  若收紧 Python + Java，就与它们声称要对齐的后端背离；
  若放宽 Go + JS，等于正式认可后端这个由 `Integer.decode` 带来的意外行为为契约。
  二者都属「对外契约变更」，按既定规则记录不单方面实施。
  实际影响低（同形字符数字是很不可能的输入），但这条不变式正是四个 demo 存在的理由，
  故必须留档。修之前需要一次产品决定：整数文法是否只认 ASCII。
- **Status**: ⏸ 记录不修（待产品决定：整数文法是否仅限 ASCII 数字）。本轮已把四条代码路径与活体后端全部实测完毕，无需再取证。

### P2-56: Java demo 里三个控制器把六个数值参数整个绕过了共享校验类——而那条 Javadoc 只点名了其中一个

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Problem**: `DemoParams` 的 Javadoc 原本写着「…**so this rule is the only thing
  that can decide what a value means**」。这句话对三个控制器是假的：
  `boundedInt` 的调用点只落在 `SearchController` / `ObservationsController` / `ExtractionController`
  三个文件、5 处，而 `ExperiencesController`（`count`、`maxChars`，`Integer`）、
  `MemoryController`（两个端点的 `count` 与 `maxChars`，原生 `int`）、
  `SessionLifecycleController`（`promptNumber`，原生 `int`）共六个数值参数全部直接绑定，
  再在方法体里手写范围检查。后果是同一个进程内部出现两种 400：`InvalidParamAdvice` 只匹配 `InvalidParam`，
  **无人处理 Spring 的 `MethodArgumentTypeMismatchException`**（`DemoErrors` 只管后端异常），
  故两种 400 的状态码相同、body 形状完全不同；且六个参数全部接受十六进制，与文法契约相反。
- **Status**: ⏸ 记录不修（第 282 轮已把 `DemoParams` 的 Javadoc 按实测更正为完整枚举，零行为变化； 实现待 P2-55 的文法决定）。本条目此前低估了范围——原文记「第四个控制器、两个参数」，实测为三个控制器、六个参数，原条目已逐字迁档。

### P2-57: Java SDK 的默认 base URL 是四家里唯一用主机名的——而后端只绑 IPv4 回环，一个 JVM 开关就能把它变成连不上

- **Scope**: → [scope-9][a6]
- **Problem**: 四家 SDK 的默认端点本应一致，实测却是 3:1 而非对称——Java 是唯一的异类，
  用主机名 `localhost:37777` 而另三家一律 `127.0.0.1:37777`；而后端 `application.yml:3` 写的是
  `address: ${SERVER_ADDRESS:127.0.0.1}`，只监听 IPv4 回环。本机解析顺序实测 `::1` 在前，
  今天能通只是因为 HTTP 客户端做了地址族回退。
- 实测证据（四家对拍表、文件行号、`lsof` 与 `[::1]` 直连结果）: 逐字迁入
  [`…-21`](../archive/2026-10-05_backend-review-evidence-21.md)（271 轮）。
- 不修的理由: 改一行即可（把默认值换成 `127.0.0.1:37777`），且从证据看方向明确：
  它会让 Java 与另三家及后端自身的 `server.address` 一致，
  且在任何「当前默认值可用」的环境里新默认值同样可用。
  但它改的是已发布 SDK 的公开默认端点——唯一会被它影响到的情形，
  是某台机器上 `localhost` 与 `127.0.0.1` 指向不同的后端（那本身已是矛盾配置）。
  即便风险极小，它仍属对外契约变更，按既定规则记录不单方面实施，留待项目拍板。
- 同区域一处文档不一致：✅ 已解决（第 271 轮核实）——`client.py` 的 Javadoc 示例原写
  `base_url="http://localhost:37777"`、与真实默认值矛盾，现已改为显式说明为何默认值用
  IPv4 字面量并回指 P2-57（`client.py:51-56`），该引用有效。原文与当时的探针失误一并归档。
- **Status**: ⏸ 记录不修（改公开默认端点属对外契约变更；证据与建议方向已齐备，修复只需一行）。

### P2-58: 四家 SDK 的响应 DTO 同缺活体观测的 7 个字段——其中 3 个正是 V17 / V18 专门加的，而 Go 的 DTO 在 V17/V18 之后还被改过

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Problem**: 取活体 `GET /api/observations?limit=1` 的一条真实观测（34 个字段），
  与四家响应 DTO 声明的字段名逐一比对，四家同缺同样这 7 个：
  `platform_source`（V18 专门新增）、`generated_by_model` 与 `relevance_count`（V17）、
  `content_hash`（V8）、`step_number`（V12）、`discovery_tokens`（V1）、`embedding_model_id`（V2）。
  Go 与 JS 的 `encoding/json` / Jackson 默认忽略未知字段，故这些字段被服务端发来、被 SDK 静默丢弃。
- 不修的理由: ①跨四家，不属于任何单一方向的轮次；②这 7 个里性质不同——
  `platform_source` 与 V17 两项是面向使用方的能力（V18 的存在意义就是让调用方知道一条记忆来自哪个平台），
  而 `content_hash` / `embedding_model_id` 很可能与三个向量列一样属内部列、本就不该暴露；
  ③该暴露哪一部分无法由证据确定。按既定规则记录不单方面实施。
- **Status**: ⏸ 记录不修（跨家 + 暴露范围待定；证据与字段来源已逐条落到迁移文件）。
### P2-59: Java demo 十个控制器把后端 4xx 变成 500，其中两个方向相反——凭空造 404，和把 404 放大成 500

- **Scope**: 逐字迁入 [`…-15`](../archive/2026-10-04_backend-review-evidence-15.md)（265 轮）；第 274 轮再迁出字面响应体与控制器普查原文 → [`…-23`](../archive/2026-10-06_backend-review-evidence-23.md)。
- **Problem**: 四家 demo 在本机同时起（Java 37778、Go 37779、Python 37780、JS 37781），对同一个请求打同一句话，
  结果是两个相反方向的分裂：①放大——后端对未知 session 返 404，Python / Go / JS 三个 demo
  原样透传，Java demo 返 500 且把后端那段 JSON 当字符串二次转义塞进 `error`；
  ②凭空造——方向相反：后端在「还没抽过」时返的是 HTTP 200 + in-band `status:"not_found"`（活体实测，非 404），
  另三家透传 200，Java demo 判 `!result.isFound()` 后自己造了个 404。
  字面响应体、40 个 catch 块普查与一个已排除的伪线索（Go demo 路由改名是有意为之）逐字见归档。
  根因很干净：12 个控制器共 40 个 `catch (Exception e)` 块，只有 3 个走到 `DemoErrors`，其余 10 个控制器一个都没有。
- 已修（零行为变更）: `DemoErrors` 类 Javadoc 已按现状改写并点名反例（12 控制器 /
  40 catch 块 / 3 个走 helper）→ [`…-36`][a4] 第 5 块（第 291 轮逐字迁出）。
- 不修的理由: 修它要改 10 个控制器的 catch 块，改的是 demo 对外的 HTTP 状态契约
  （500→404/400，且要决定 `error` 字段是否保留 SDK 前缀文本——Go/JS 加前缀、Python 不加，
  三家自己就不一致）。按既定规则对外契约变更记录不单方面实施。
  另注：`ErrorField` 的正则对 Spring 默认错误体（`{"timestamp":...,"error":"Bad Request"}`）
  会取出 `"Bad Request"`，这条是后端本身就没给解释，不算信息丢失，故不单列。
### P2-60: `CortexMemoryAdvisor` 的 `projectPath` 默认为空串——不设 `cortex.mem.project-path` 时，被捕获的提示记下了却再也召回不了

- **Problem**: `Builder.projectPath` 默认 `""`，自动装配又显式做 `getProjectPath() != null ? … : ""`；
  空串不是 `null`，能通过 `UserPromptRequest.toWireFormat()` 的 null 判断，被当作 `"cwd": ""` 发出。
  活体实测与规模（后端返 200、行以 `project_path = ''` 落库、只有用空项目查才取得到；
  库中 `EMPTY-STRING` 仅 2 行且都是探针，多数形态是 `NULL`，2043 行 / 2011 会话）
  逐字见 [`…-27`](../archive/2026-10-06_backend-review-evidence-27.md)。
- 已修: advisor 的 Javadoc 与两份 SDK README 均已按现状写明，零行为变更。
- 不修的理由: 三种改法（`null` / `user.dir` / 拒绝记录）都改已发布 SDK 的公开默认行为；
  其中 `user.dir` 还会把提示归到用户并未选择的项目下，比现状更糟，故不单方面实施。
- **Status**: ⏸ 记录不修（文档已更正；行为变更待项目决定）。
### P2-48: gitignored 的 `CLAUDE.md` 端点表 25 条里有 9 条是活体 404 的幻影端点

- **Scope**: → [scope-9][a6]
- **Problem**: `CLAUDE.md` 的端点表 25 条里有 9 条幻影端点，9 条逐条实测为 404；
  同区域另一处漂移：项目结构写「controller/ # 17 controllers」，实测 13 个。
- 实测证据: 逐字迁入 [`…-16`][a24]（267 轮）——含 9 条路径逐条清单、`/api/ingest` 实际四条，以及 service「28+」实测 29 属合法范围故不计。
- **Status**: ⏸ 记录不修 —— `CLAUDE.md` 是 gitignored 的本地文件 （`git ls-files` 未跟踪、`git check-ignore` 命中），改动不会进入版本控制， 且「是否取消其 gitignore」本身仍是待用户决策事项；不在本轮静默修改。 待该决策落地后，修法即按 `API.md` 的真实路径逐条更正这 9 行；其中 `ingest/session-start`、`memory/save`、`context/observations` 三条 须先确认是被重命名还是从未存在——若是后者则是纯粹删除。
### P2-61: `TestController` 的 `@Profile("!prod")` 指向一个本仓库不存在的 profile——它承诺的生产隔离从未生效
- **Scope**: → [scope-9][a6]
- **Problem**: 类级 `@Profile("!prod")` 与紧邻的 `@Tag` 描述都声称这些端点「Only available in
  non-production environments」。但**本仓库只有 `dev` 与 `prd` 两个 profile**——只有
  `application.yml` / `application-dev.yml` / `application-prd.yml`，**没有 `application-prod.yml`**；
  全仓 `SPRING_PROFILES_ACTIVE` 只出现三个值：`prd`（`docker-compose.yml:52` 默认）、`dev`
  （两个 e2e 脚本）、以及构建说明里「不设」。`!prod` 在这三种情形下全部匹配，即该门控
  永不排除任何东西；它唯一会生效的场景（`SPRING_PROFILES_ACTIVE=prod`）恰恰是**没有对应
  配置文件**的场景。后果是 `prd` 部署下三个**会实际消耗 LLM / 嵌入配额**的调试端点照常开放。
  活体三端点响应逐字见 [`…-36`][a4] 第 6 块（第 291 轮逐字迁出）。文档侧同源两处：`docs/ARCHITECTURE.md` 的
  API 分层表列出 `/api/test/*` 时未提任何 profile 限定；P2-28 的 Problem 段称该门控
  「是正确的」据本条证据需要修正。
- **Status**: ⏸ 记录不修 —— 把它改成 `!prd` 会让三个端点在默认 compose 部署下消失， 属对外契约变更；且本轮代码方向为 Python SDK，后端不在本轮范围内。本轮未改任何后端代码。
### P2-62: `ProjectFilterService` 的 `~username` 展开丢弃用户名、改写到当前用户家目录——Javadoc 说的是另一回事
- **Scope**: → [scope-9][a6]
- **Problem**: 原 Javadoc 写 "Handles both `~` (current user) and `~username` (specific user)
  forms"，行内注释写 "expand to that user's home (best effort)"——代码里不存在 resolve 分支：
  `replaceFirst("^~" + username, userHome)` 把 `~username` 整段替换成当前用户的家目录，
  用户名被静默丢弃；且 `username` 未转义即拼进正则。
  反射实测表（`~/proj` / `~alice/proj` / `~alice` / `~a.b/proj` 四例）逐字见归档。
- 影响面：该类未注册为 Bean、生产代码零引用，类 Javadoc 自陈 "not currently wired
  into any processing pipeline" —— 当前影响为零；风险是有人照该 Javadoc 接上后静默改写路径。
  **测试头曾声称覆盖 "expandHomeDirectory edge cases"，而全文零个 `~` 用例**。
- **Status**: ⏸ 记录不修 —— 正确的 `~username` 解析属设计决策，且无生产调用方可验证。 已修（零行为变更）：Javadoc 改为如实描述并附实测结果；测试头的不实声明已更正，并写明 为何不补测试——补了就等于把可疑行为钉死。后端 167 测试全绿、`mvn package` EXIT=0。
### P2-71: 12 个读方法的 `or {}` 把「存在但不是 JSON 对象」的响应体静默变成空结果，且对标量抛的异常**逃出 SDK 层次**
- **Scope**: → [scope-9][a6]
- **Problem**: `_request_json` 不校验解码结果类型。`or {}` 于是把存在且非空的 `[]`/`null`/`0`/`false`/`""` **静默变成全默认值 DTO**，调用方无法区分「后端确实没有」与「后端回了垃圾」；`"oops"`/`42` 则抛**裸 `AttributeError`**，**不在 `CortexError` 层次内**。四家实测：Python 是唯一对标量抛非 SDK 异常的（Go 给类型化错误，JS 对标量静默、对 `null` 抛裸 `TypeError`，Go 与 Python 都对 `null` 静默）。字段级类型错不是问题（11/11 逐字段污染全解析成功）。
- **Status**: ⏸ 记录不修 —— 修法是让 `_request_json` 区分「204/零长度」与「存在但非对象」，前者仍返 `None`、后者抛 `CortexError`；但这会把今天静默返回空结果的输入改成抛错，属错误路径行为变更，且 `or {}` 本身撑住「后端真的什么都没返回」这一合法场景、不能简单删。未按失实陈述改写文档：docstring 与 Python README 都只声称「不可解析的体抛错」，字面为真（HTML 确实抛，三家实测均抛），只是未覆盖这一类。

### P2-72: JS SDK 的 9 个解析方法把 2xx 的 `null` 响应体变成**裸 `TypeError`**——而同一 SDK 的另外 5 个方法静默返回默认值
- **Scope**: → [scope-9][a6]
- **Problem**: P2-71 的 JS 版，而 JS 还自相矛盾。同一个 2xx + `null` 响应体，**9 个方法抛裸 `TypeError: Cannot read properties of null (reading 'items')`**，5 个静默返回默认值；其余四种非对象体（`[]`/字符串/数字/`false`）14/14 静默。该 `TypeError` **不在导出的两个异常类内、也不带 `cortex-ce:` 前缀**——而 SDK 每一处自有消息都带，按前缀过滤日志的调用方看不到它。根因是 TypeScript 类型断言运行时是 no-op，而同文件 234/486 行用的却是真正的 `Array.isArray` 检查。
- **Status**: ⏸ 记录不修（同 P2-71：加运行时校验属错误路径行为变更）。跨家：Go 是唯一对全部非对象输入都给类型化错误的。核实为真、不记：10 MB 两道守卫与 `utf8ByteLength` 实现正确且 **README 已完整文档化其裸 `Error` 选择及理由**；`clearTimeout` 在 `finally`；HTML 解析失败抛裸 `Error` 属 README 已确立的约定。

### P2-73: 三份配置的 `logging.level.com.claudemem` 全部指向已经不存在的包——dev profile 的应用调试日志开关从未生效

- **Status**: ✅ 已修 —— 全文逐字见 [`…-41`][a5]（324 轮）。

### P2-74: 四家 SDK 的重试默认极性三比一不同——Go / Python / JS 是 fail-closed，只有 Java 是 fail-open；而这一分歧此前无处记载
- **Status**: ✅ 已修（324 轮） —— 全文逐字见 [`…-41`][a5]
### P2-75: 单条观测的 PATCH / DELETE 对畸形 id 返 400，而 API 文档与 OpenAPI 注解都只把 400 写成「请求体字段类型错」

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Problem**: `@PathVariable UUID` 在 id 不可解析时由 Spring 抛转换失败，返回 400，
  与「请求体字段类型错」是完全不同的成因，却共用同一个状态码；
  path 里的 id 写错是 **400** 而**不是 404**，按 404 当「不存在」来写的客户端会落到通用错误分支。
  活体实测表、API 文档对 400 与 404 的措辞原文、全库检索结果见归档。
  注意归因：`MemoryController` 自己的 `@ApiResponse` 注解用的也是同一句窄措辞，
  **所以 API.md 忠实镜像了注解——缺口源自代码里的契约描述，不是文档与代码不一致。**
- **Evidence**: → 归档 [`…-89`][a47]（活体实测四行表、API 文档对 400 与 404 的措辞原文、全库检索「从未提及畸形 id」的结果、⚠️ 一处差点误记已撤回的全文）
- ⚠️ 一处差点误记、已撤回（**全文见归档**）: 初判为「后端对畸形 id 返 404、demo 注释失实」——**探针打错了路径**
  （真实全路径是 `/api/memory/observations/{id}`，我探的 `/api/observations/{id}` 根本不存在，
  那个 404 是路由未匹配而非 id 校验）；改打正确路径后得到 400，
  且**第 102 / 201 轮早已裁决过同一件事**（那句 `Invalid observationId format` **只对 `submitFeedback` 成立**）。
- **Status**: ⏸ 记录不修 —— 要改就得同时动控制器注解与中英双语文档， 而注解是对外发布的 API 契约描述；且此处是描述偏窄（400 的成因少列一种）、 不是陈述错误，按既定规则「遗漏 ≠ 失实」不单方面改写对外契约。 修法若要采纳，最小形态是给两处 `400` 补一句「或 path 中的 id 不是合法 UUID」。
### P2-76: demo 40 个 catch 块里只有 3 个做后端 4xx 直通，`PATCH /demo/session/user` 因此把 404 报成 500

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Problem**: `FeedbackController.java:68-74` 的注释把理由写死了——不直通就会
  「把一个缺失的观测报成 500，也把打错的 id 报成 500，既误报状态又丢掉解释」，
  并写明「The Go, Python and JS demos pass a backend 4xx straight through」。
  **这个机制只落在了 3 个 catch 上；demo 全部 40 个 catch 里其余 37 个仍一律转 500。**
  活体实测（demo 37778，后端 37777）：

  | 请求 | 后端直打 | 经 demo |
  |---|---|---|
  | `PATCH /api/session/no-such/user` | 404 `{"error":"Session not found: …"}` | 500 `{"error":"Failed to update session user: 404 Not Found: \"{…}\""}` |
  | `POST /demo/feedback` 未知 observationId（对照组） | 404 | 404 ✓ 直通生效 |

  对照组与缺陷点在同一个 JVM、同一次运行内，证明这不是 `clientStatus` 失效或后端行为漂移，
  而是该端点没有使用它。打错一个 session id 得到 500，等于告诉调用方「你把服务器弄坏了」。
  其余 37 处不可达：`ManagementController` 与 `MemoryController` 的入参都是自由文本
  （project/task/count/maxChars，demo 自校验），后端按自由文本存不存在记录，
  调用方无法靠输入构造出一个后端 4xx——所以实际暴露面是这一个端点，不是 37 个。
- **Status**: ⏸ 记录不修 —— 让已发布 demo 端点的响应码从 500 变成 404 属对外行为变更。 修法若采纳，最小形态是在 `SessionLifecycleController.updateSessionUser` 的 catch 里 套用 `FeedbackController` 已有的 6 行模式（同仓库已有现成写法，不需新机制）。
### P2-77: 四个 `/memory/*` 端点把「参数存在但为空」报成 500，而「参数缺失」是 400——同一类调用方错误，两个状态码

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Problem**: 四者都用 `@RequestParam String task`（必填），且方法体里都没有空白检查，
  直接流入 SDK 的 `requireNonBlank`。活体实测（demo 37778）：

  | 请求 | 实测 |
  |---|---|
  | `GET /memory/experiences`（不传 task） | 400（Spring 必填校验） |
  | `GET /memory/experiences?task=`（传空） | 500 `{"error":"…: task must not be null or blank"}` |
  | `GET /memory/icl?task=` | 500 同上 |
  | `GET /memory/experiences/filtered?task=` | 500 同上 |
  | `GET /memory/icl/truncated?task=` | 500 同上 |
  | `GET /demo/experiences?task=`、`GET /demo/iclprompt?task=` | 400 ✓ 正确的形态 |

  抛的是 SDK 的 `IllegalArgumentException`，被兜底 `catch (Exception e)` 吞成 500；
  `DemoErrors.clientStatus` 对它返回 `null`（本就不该走那条路），所以机制上不是漏了直通，是缺了入口校验。
  同一模块的 `/demo/*` 兄弟端点自己校验了空白并返 400，形态是对的。
- ⚠️ 一处中间断言先错后改：初判为「`/memory/experiences` 返 400 而 `/memory/icl` 返 500，是两者不对称」。
  那个 400 来自「参数缺失」，不是「参数为空」；补测 `?task=` 后 `/memory/experiences`
  同样返 500。原判据站不住，问题反而更整齐：四个端点在「空」这一形态上完全一致地错。
- **Status**: ⏸ 记录不修 —— 同 P2-76，响应码变更属对外行为变更。 修法若采纳，最小形态是在四个方法体开头各加一行空白检查（与同文件 `count`/`maxChars` 的既有写法同构）。 注：`promptNumber` 无范围检查是同族但已单独立项（P2-69），本条只管「空白必填参数报 500」。
### P2-78: CORS 的 `allowedMethods` **漏了 PATCH**——按文档开启 CORS 后，两个 PATCH 端点对浏览器静默失效

- **Status**: ✅ 已修（323 轮） —— 全文逐字见 [`…-40`][a15]（323 轮）

### P2-79: CORS 的凭据开关只检查列表第 0 位是否含 `*`——`*` 出现在别处时**整个 API 返 500**

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Problem**: 该行**只判断 `origins[0]`**，而它自己的注释声称「only if not using wildcard」——
  **只要 `*` 出现在列表的任何非首位位置，意图就被违背**。
  Spring 的 `CorsConfiguration.validateAllowCredentials` 在 `allowCredentials=true` 且
  `allowedOrigins` 含 `*` 时**抛 `IllegalArgumentException`**，且该校验每个请求都会走到。
  失效形态远重于「跨域不工作」：整个 API 返回 500，连不带 `Origin` 的普通请求也是 500。
  异常原文：`When allowCredentials is true, allowedOrigins cannot contain the special value "*"…`
  ⚠️ 第 314 轮曾更正本条第二行，且**更正方向是加重不是减轻** —— 全文见归档。
- **Evidence**: → 归档 [`…-89`][a47]（活体三配置对照表、第 314 轮更正全文 —— 初版 ACAO 断言是用**坏探针**读出来的、`-D` 重测后的第二张表与「两种错法都错」⇒「一种打挂、一种对全网敞开」的结论订正）
- ⚠️ 为什么不能顺手改：最直觉的修法（`*` 出现在任意位置就把 `allowCredentials` 置 false）
  恰好制造出**归档里那张对照表第二行**那种结果——Spring 回 `Access-Control-Allow-Origin: *`，
  等于给所有 origin 开口，比运维显式列出的那一个更宽。
  第 314 轮的实测正是这句话的证据：该行为并非推测，它就是 `*` 在首位时的实际表现。
  正确修法只有两条，都涉及策略决定：改用 `allowedOriginPatterns`（支持带凭据的通配），
  或启动期拒绝这份配置并给出清晰错误（fail fast）。
- **Status**: ⏸ 记录不修 —— 上条已说明「直觉修法是安全放宽」，本条属安全策略决策， 不单方面实施。修法若采纳，建议 `allowedOriginPatterns` + 启动期对 `*` 与具体源混用给出显式告警。
- 对 P2-78 复查计数的影响：第 312 轮为 P2-78 的第 2/3 次复查，
  但在同一段代码里发现本条新问题 → 按既定纪律计数重置，从第 312 轮重新记 1/3。

### P2-80: CORS 的 origin 列表不 trim——按文档教的「逗号分隔」写，只有第一个域名生效

- **Status**: ✅ 已修 —— 逐字迁入 [`…-40`][a15]（323 轮）；其余正文已逐字迁入 [`…-44`](../archive/2026-10-07_backend-review-evidence-44.md)（331 轮）。

### P2-81: `run-all-e2e.sh` 声称跑「全部」E2E 脚本并逐条列出 3 个排除项——实际漏掉 13 个，其中 7 个的前置与它自己完全相同

- **Status**: ✅ 已修（324 轮） —— 全文逐字见 [`…-41`][a5]（324 轮）
### P2-82: 三家 SDK 都给响应体设了 10 MB 上限，只有 Python 完全没有——`requests` 会把整个 body 缓冲进内存

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Problem**: 同一道安全阀，Go / JS 都设 **10 MB** 上限、**Python 完全没有** ⇒ `requests` 会把整个 body 缓冲进内存；
  这不是「少写了个常量」而是**失效形态不同**：同一次异常后端响应，Go/JS 得到「响应体超过上限」的
  可诊断错误，Python 得到一次内存暴涨。三家形态表与探针读数见归档。
- **Evidence**: → 归档 [`…-89`][a47]（三家形态表、`grep` 零命中的读数、JS 第三道检查的由来、Python README「Response Size Limit」节的逐条引文、Go/JS README 对拍、⚠️ 第 316 轮更正定性全文）
- **Status**: ⏸ 记录不修，且第 316 轮已下调定性（见下） —— 给 Python 加上限会让原本成功的超大响应变成抛错， 属收窄已发布 SDK 的接受范围。
- ⚠️ 第 316 轮更正定性（**全文见归档**）: 这不是「无人知晓的缺口」，而是**已双语记载的有意权衡**
  —— 技术事实不变（Python 无上限、Go/JS 有 10 MiB），但性质是「已记载的取舍」不是「静默的缺口」；
  剩下的唯一问题是「要不要改这个取舍」，属产品决策，**维持不修**。
- **未证实的部分，不写**：本轮**未实测**「真的让 Python 侧 OOM」——
  上面是**代码路径层面的断言**（无上限检查、requests 会缓冲），
  **不是**「已复现的内存事故」。

### 加注（不改写归档）：归档条目 **30-2 的跳过理由已经过时**

- **原条目**：[`…-history`][a22] 第 1894 行（表格行 `30-2`）：
  「`/api/logs` 和 `/api/logs/clear` 端点无认证/授权保护。日志内容可能包含敏感调试信息……
  ⭭ 跳过（**设计决策：服务绑定 localhost，外网不可达**；添加认证属于架构变更）」。
  **归档不得修改**，故在此加注。
- **该前提今天不成立**（第 317 轮实测）：`docker-compose.yml:88` 的端口映射是
  `"${SERVER_PORT:-37777}:37777"`，**没有主机 IP 前缀 → 发布到所有网卡**；
  `:60` 另设 `SERVER_ADDRESS: 0.0.0.0`（即 P2-70）。后端**唯一的 Servlet Filter 是
  `MdcAutoFilter`（关联 ID，不是鉴权**），全仓无 `SecurityFilterChain`
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

- **Status**: ✅ **已修（325 轮）** —— 全文逐字见 [`…-42`](../archive/2026-10-07_backend-review-evidence-42.md)（325 轮）

### P2-84: Go SDK 的 `base_url` 规范化**只去一个**尾斜杠，而另三家去全部 —— 同一份配置在四家里三成一败
- **Status**: ✅ **已修（361 轮）** —— 全文逐字见 [`…-59`][a3]
### P2-85: Python demo 注释称「四家与后端一致」，但后端**接受十六进制**而四家全部拒绝 —— 实测断言
- **Status**: ✅ **已修（361 轮）** —— 全文逐字见 [`…-59`][a3]
### P2-86: API 文档把两条 DELETE 路由的路径变量写成 `{uuid}`，活体是 `{id}` —— 第 157/317 轮同类
- **Status**: ✅ **已修（361 轮）** —— 全文逐字见 [`…-59`][a3]
### P2-87: 架构文档的「后端参考提交」`ed37a1b2…` **在任何地方都解析不到**，版本配对声明半边失效

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Problem**: 该段落的功能是**让读者能取回这一对版本**。**配对的另一半完全正常**：
  `72e7804b…` 经三重核实**全部吻合** —— 子模块自身 `HEAD`、父仓库记录的 gitlink、
  以及文档中的字面值。**而 `ed37a1b2…` 在任何地方都不存在**，逐项排查结果：

  | 查证位置 | 结果 |
  |---|---|
  | 本仓全部 refs / 历史 | 无 |
  | `git rev-list --all` 中以 `ed3` 开头的提交 | **零条** |
  | reflog | 无 |
  | 悬空 / 不可达对象（`git fsck`） | 无 |
  | `webui/` 子模块历史 | `fatal: could not get object info` |
  | origin 远端（本地与其已同步） | 无 |

  故这段话**有一半无法兑现**：读者按它去 checkout 会直接失败。
- **⚠️ 为什么不单方面改写（与 P2-86 的关键区别）**：
  P2-86 的正确值是**被权威确定的** —— 活体 `/v3/api-docs` 的 `params=[('id','path')]`
  与控制器注解都指向 `{id}`，因此可以照权威直接改。
  **本条的正确值只能靠推断**：文档所说的「current reviewed capability set」
  （viewer 会话目录 + 删除 + V19）经 `git log --diff-filter=A` 核实**只由一个提交引入**，
  即 **`6e5890d`**（`ViewerSessionService.java` / `ViewerSessionController.java` /
  `V19__viewer_session_indexes.sql` 三者同为该提交新增）。
  **这使 `6e5890d` 成为高度可能的正解，但仍是推断而非判定** ——
  作者也可能指的是自己某个已 rebase 掉的中间提交。故**记录并附证据，不猜改他人刚写的版本钉**：
  **写错一个钉比标出一个坏钉更糟**。
- **Severity: 中（不高，但这是架构文档里唯一一处读者会直接照着执行的断言**，
  且该断言的**用途就是可执行性**）。
- **建议修法**（待作者或用户确认）：把两处 `ed37a1b2…` 改为 `6e5890d`，
  或改写为不钉具体 hash 的表述（例如只保留 WebUI 一侧的 hash 并注明后端以父仓库当前 `main` 为准）。
- **Status**: ⏸ **记录不修** —— 需作者意图确认，见上。

### P2-88: `retrieval-enabled=false` 会**静默关掉全部 @Tool 捕获**，而 README 把该开关记作只管检索

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Evidence**: → 归档 [`…-85`][a43]（受控实验表、后果链五环、README 双语缺记载的逐行定位、第 360 轮设计稿补注、次生后果）
- **⚠️ 为什么不单方面改**:
  - 按**「遗漏 ≠ 失实」**：README 那几句**本身没有说错** —— `retrieval-enabled`
    确实是「启用记忆检索」，桥接开关确实创建桥接顾问。**这是省略，不是失实陈述**，
    故不适用「失实陈述的文档修正可修」这条例外。
  - 更关键的是**修法形状本身有歧义**：最小修法是文档补一句耦合说明；
    但更像是作者意图的修法是**把桥接 bean 移出被检索门控的那个类**（或拆分配置类）——
    那属于**对外契约变更**，必须由作者决策。若我先写下「此耦合是有意的」这种文档，
    作者一旦选择解耦，那行文档立刻变成新的失实陈述。
  - **测试侧同缺**：`CortexMemAutoConfigurationTest` 现有 4 例（base-url 有/无、
    默认服务注册、capture 关闭），**唯独没有 `retrieval-enabled=false` 用例**，
  上述组合从未被断言过 —— 补一个用例属低风险，但同样要跟着「耦合该保留还是该解」的
  决定走，故一并待决。
- **Severity**: 中（静默丢数据 + 与文档理解相反，但需用户主动设 `retrieval-enabled=false`
  才触发，且有选项 B 绕行；不涉及安全或正确性破坏）。
- **建议修法**（待作者或用户确认，二选一）:
  1. **解耦**（推荐，若该耦合非有意）：把 `cortexSessionContextBridgeAdvisor` 移出
     `SpringAiAdvisorConfiguration`，或拆出一个只受 `context-bridge-enabled` 门控的
     配置类；同时补 `retrieval-enabled=false` 的装配用例。
  2. **记为有意**：在双语 README 的属性表与故障排查表中补明该耦合并说明影响面。
- **Status**: ⏸ **记录不修** —— 需作者确认该耦合是否为有意设计；证据全文见 [`…-85`][a43]。

### P2-89: 部署指南的迁移清单**双语都停在 V18**，漏掉磁盘上已存在的 `V19__viewer_session_indexes.sql`
- **Status**: ✅ **已修（345 轮）** —— 全文逐字见 [`…-52`](../archive/2026-10-07_backend-review-evidence-52.md)

### P2-90: Go SDK README 的测试总数**双语共 7 处**硬编码 `362`，实测已是 `363` —— 差值恰为第 319 轮我自己新增的那 1 个测试

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Evidence**: → 归档 [`…-88`][a46]（六个模块的两种计数口径对照表、差值归因到 `7a78266`、「一处比其余六处更值得记」的无日期失实定位）
- **⚠️ 为什么不单方面改**:
  正确值虽是实测得来、权威确定，但**这是 7 处双语硬编码，且任何一次新增测试的提交都会让它再次失效**
  ——第 319 轮就是这样漏的。手改 7 处等于把一个**会周期性重复劳动的维护点**再推后一轮，
  而真正该定的是**形态问题**：要么改写成不写死数字（只留可运行命令，让读者自己跑），
  要么就明确接受它会过期并在同一次改动里同步。后者属文档策略决策。
- **Severity**: 低（差 1 / 362，不影响任何行为、命令或结论；且带日期的那处按字面为真）。
- **建议修法（待决）：推荐前者** —— 把 `README.md:13` / `:192` 与 `README-zh-CN.md:13` / `:187`
  的硬编码总数改为「运行下方命令得到当前计数」，只保留**分模块口径**（dto/eino/genkit/
  langchaingo/http-server 五个数仍需人工维护但变动频率低得多）；若维持现状，则规定
  「凡新增或删除测试的提交，必须同批更新这 7 处」，并加一条门控断言。
- **连带记录（不单列 finding）**：本地 gitignored 的 `CLAUDE.md` 记着
  **372**（`:52` 与 `:268`「278 core + 61 dto + 13 genkit + 12 langchaingo + 8 eino」），
  出处是 `:407` 一条 **2026-04-17** 的 Recent Work 条目（「Go SDK test count corrected to 372」）。
  与实测 363 差 **9**，且两个分项（278 / 61）也都对不上（实测 236 / 67）——
  即**约半年未更新。按 P2-48 已确立的先例**（`CLAUDE.md` 是 gitignored 的本地文件，
  其问题一律记录不修），本轮**只记录不修**。
- **Status**: ⏸ **形态问题（「要不要彻底不写死数字」）仍留待用户决策** —— **数字部分本轮已修（第 367 轮，双语，纯文档）**：本轮**亲跑复测**六个模块（`=== RUN` 计数，含子测试）得根模块 **303**（core 236 + dto 67）+ eino 8 + genkit 13 + langchaingo 12 + `examples/http-server` 27 ⇒ **合计 363**，与本条记的归因（`7a78266` 新增恰好一个顶层测试）**完全吻合**；**双语的 6 处数字已改：无日期的失实断言改为实测值并加上测量日期，带日期的快照同步为 2026-10-20 的实测。本行替换了此前的「⏸ 记录不修」**——那一条针对的就是形态问题。
- **第 370 轮补注（同日漂移）**：本轮给 `examples/http-server` 加了 6 个测试用例（2 个顶层 + 4 个子用例），Go 侧 `=== RUN` 计数实测从 **363 变成 369**（`examples/http-server` 27 → **33**），**Go README 双语已同步**。**这正是本条「形态问题」最直接的证据**：**同一个测量日（2026-10-20）里数字就变了**——上次第 367 轮复测是 363，本轮同一���再测是 369，故「写死一个数」注定很快过期。

### P2-91: 后端源码里的 `P1-1` / `P1-2` / `P2-1` 标签与本仓库 findings 的编号**含义冲突**，而 `P1-2` 在本仓历史上已指代过三个不同主体

- **Scope**: → [scope-9][a6]、[scope-10][a7]
  三者**内部自洽**：标签的优先级序号与主题一一对应（P1-1 恢复、P1-2 监控、P2-1 缓存），
  显然是一份**功能优先级清单**，不是缺陷编号。
- **冲突是实打实的，不是理论风险**：本仓库 `docs/drafts/backend-review-findings.md` 里
  `P1-1` / `P1-2` 指的是另外的缺陷（P1-2 = 「Java demo 的 `?path=` **无任何路径校验**」）。
  于是在本仓 `grep P1-2` 得到的是**互不相关**的主体。
- **而且冲突已经真实发生过一次**：巡检自己的归档记录了第 160 轮的
  「**P1-2 导入端点把校验失败报成成功跳过**」（`2026-10-02_health-check-history-6.md:322,409,520`
  与 `history-7.md:102,200,291`）。该条目**如今已不在工作 findings 里**（`grep 导入端点` 零命中），
  已被修复或改号，而**现存的 `P1-2` 是另一个主题**。
  故 `P1-2` 在本仓至少指代过：①导入端点校验、②Java demo 路径穿越、③队列健康指示器。
- **⚠️ 为什么不单方面改**（判据同 P2-87）:
  - 源码那三处注释**本身没有说错**——它们在各自那套编号里是对的。
  - 真正的问题不在「哪一处错了」，而在这套编号**在全仓库任何地方都没有定义**：
    穷举 `*.md` / `*.txt` / `*.yml` 找不到任何 `P1-2` / `P2-1` 的定义或优先级表，
    唯一提到 stale-message recovery 的是 gitignored 的 `CLAUDE.md:357`（一句功能描述，非编号定义）。
  - 因此**正确的处置只能靠推断**——它们可能指上游 claude-mem 的 issue 号、某份已废弃的本地计划、
    或就是作者自己的功能优先级。**写错一个编号的含义比标出一个冲突更糟**，故记录附证据、不猜改。
- **Severity: 低（不影响任何行为；但它是唯一一处会让人 grep 出错误结论**的编号体系冲突，
  且已在历史上实际误导过一次）。
- **建议修法**（待作者确认编号来源后择一）:
  1. 在源码那三处补一句来源说明（如「功能优先级，非缺陷编号」），成本最低；
  2. 若确认是上游 issue 号，改成 `claude-mem#P1-2` 之类带前缀的形式，与 findings 的 `P1-2` 彻底分开；
  3. 顺带在 findings 的 Processing Rules 里加一句「本文件编号仅在本文件内有效」。
- **Status**: ⏸ **记录不修** —— 编号来源需作者确认，见上。

### P2-92: `CortexMemoryTools` 的注释说 `defaultCount` **未被钳制，而它上面两行的构造函数恰恰钳了**
- **Status**: ✅ **已修（361 轮）** —— 全文逐字见 [`…-59`][a3]
### P2-93: `submitFeedback` 的空评论在 Go / JS / Python 里被静默丢弃 —— **四家中只有 Java 能清空它**

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Evidence**: → 归档 [`…-88`][a46]（后端两行语义、四家判空写法对照表、活体三段式实测与负对照复位、危害链、与 P2-26 / P2-27 的关系辨析）
- **危害：完全静默。两次都返回 HTTP 200 `{"status":"ok","observationId":"…"}`**，
  调用方**无从得知评论没被清空。与 P2-26 相比这里连半信号都没有**
  （P2-26 至少在只设 `Facts` 时会抛「at least one field must be provided」）。
- **与 P2-26 / P2-27 的关系：同属「空值被省略 → 无法表达清空」这一族，但不同端点、不同范围**：
  P2-26 是 Go 的 PATCH observations、P2-27 是 Python 的 `extractedData`，而本条是
  `feedback` 端点且**一次命中三家**。机制也不同：Go 靠 `omitempty`，JS/Python 靠**真值判断**。
- **Severity: 低（清空评论是低频操作，且无文档承诺它可行），但跨三家、静默、且与 Java 不一致**
  —— 与 P2-65（`is_retryable` 跨家签名不一致）同族。
- **不单方面修（判据：遗漏 ≠ 失实，且修法改的是已发布 SDK 的线上行为**）:
  API 文档（`API.md:817` / `API-zh-CN.md`）只写「Optional feedback comment」，
  **从未声称可清空**；四家 README 也都没这么写。故不存在失实陈述可照权威直接改。
  而修法（Go 改 `*string`、JS/Python 改显式 `is not None` 判断）会**改变现有调用方实际发出的报文**
  —— 对从未设置过评论的用户，`""` 会突然开始上 wire。属**对外契约变更**，按规则记录不实施。
  **建议作者先裁定语义**：`comment: ""` 到底该不该表示「清空」？
  若该，三家同步改；若不该，则应在 API 文档补一句「空评论不生效」，把这条从隐性变成显性。
- **Status**: ⏸ **记录不修** —— 待作者裁定空串语义，见上。

### P2-94: `go-sdk-design.md` 的 module 路径**多了一节** —— 45 处指向一个不存在的模块，Quick Start 照抄必然失败
- **Status**: ✅ **已修（361 轮）** —— 全文逐字见 [`…-59`][a3]
### P2-95: 架构文档把 17 行关键警告关在 ```sql 围栏**里面** —— 它渲染成代码，而不是提示

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Evidence**: → 归档 [`…-88`][a46]（原文形态逐行、渲染后果、全库「围栏内出现 `>` 起始行」的孤例对扫描结果、内容正确性核实）
- 修法是明确且零信息损失的（EN）：在 `:624` 前插入一行 ` ``` `，
  在 `:640` 后插入一行 ` ```sql `；ZH 同样处理 `:616` / `:629`。
  作者写引用块的本意显然是「打断 DDL 放一个提示」，围栏只是没关。
- 不单方面改（判据：这是已发布文档的呈现结构，不是代码行为，也不是失实陈述）：
  改的是读者实际看到的渲染结果，属对外呈现契约；且这两节的内容此前已逐条核实过，
  单方面重构其版式超出「修正错误陈述」的范围。
  建议按上面那 4 行一次改完（双语各一处），改动量极小、无信息变化。
- Severity: 低（内容正确，纯渲染；但削弱的是一条会导致静默错误的关键警告）。
- **Status**: ⏸ 记录不修 —— 待作者按上述 4 行改完，见上。




### P2-96: `DemoErrors` 自陈的 catch 块数写错了 1 —— 而第 328 轮「核实四项计数全对」那次核实本身是错的
- **Status**: ⚠️ 第 340 轮的修复本身是错的，已于第 346 轮更正（纯注释零行为变更） —— 第 340 轮把 `forty` 改成 `forty-one`，该方向改反了：（证据与仪器结论逐字见 [`…-11`][a41]）
### P2-97: JS SDK README 的方法表里 **3 行参数名写成 `project`**，而源码、同表另外 3 行、以及 Python SDK 全都是 `projectPath` —— 同一个 bug 在 2026-04-01 修过一次，只改了 2 行

- **Status**: ✅ 已修（340 轮） —— 全文逐字见 [`…-46`](../archive/2026-10-07_backend-review-evidence-46.md)（340 轮）
### P2-98: `docs/archive/README.md` 的登记表漏了 6 份已存在的归档 —— 而第 318 轮刚为同一件事给 `health-check-history-18` 补过一次登记
- **Status**: ✅ 已修（361 轮） —— 全文逐字见 [`…-59`][a3]
### P2-99: 登记表里有一行把**裸 `|` 写进了代码 span**，GFM 会把这一行的单元格从中间切断
- **Status**: ✅ 已修（361 轮） —— 全文逐字见 [`…-59`][a3]
### P2-100: `patrol-rotation.md` 的当前位置第三次停摆——两次「补齐 + 承诺自此随每轮更新」都没能阻止复发

- **Scope**: → [scope-9][a6]
- **Problem**: 该文件顶部 Update rule 明写「每轮完成后同步更新本文件的当前位置和历史摘要」，
  而它已三次违反同一条规则：

  | 更正轮次 | 停摆区间 | 停滞轮数 | 当时的修法 |
  |---|---|---|---|
  | 第 237 轮 | 第 184 → 237 轮 | 53 | 人工补齐 |
  | 第 318 轮 | 第 237 → 318 轮 | 81 | 人工补齐 + 加注「自此随每轮更新」 |
  | 第 340 轮 | 第 323 → 340 轮 | 17 | 人工补齐（本轮） |

  第 318 轮那行加注写着「本段自此随每轮更新」——而实际只又跟了 2 轮（322、323）就再停。
  三次同因、同法、同复发，说明问题不在执行而在机制：靠每轮自觉补一句，
  而每轮的固定动作清单里没有这一条，漏掉不会有任何东西报警。
  `History` 表最后一行即第 323 轮，第 324–339 轮共 16 行缺失（第 340 轮的行本轮已补上）。
- Severity: 低。`patrol-state.json` 是机器可读的唯一状态源、且始终同步正确，
  轮换行为不受影响；但 `cron-combined-task.md:18` 明确把本文件列为判定当前方向的依据之一，
  一份停在 17 轮前的文档在那里是实打实的误导源。
- **Status**: ⏸ 当前位置与本轮 History 行已同步、16 行历史不补。`Current Position` 段本轮已更新为 第 340 轮（Demo → next Backend），并加注第三次更正。 17 行 History 不补：逐轮叙述的权威全文在 `health-check-task.md` 及其归档里， 凭归档转写 17 行摘要属重建而非记录，正是本仓库「宁少勿错」要避免的那类事。 真正的修法在机制，二选一待作者定：①把「同步本文件」写进每轮的固定动作清单 （或做成脚本里的一步，让漏掉会失败）；②把它降格为周期性摘要并改掉 Update rule 的措辞， 承认它不是逐轮台账。

### P2-101: 架构文档让读者去 `backend-review-findings.md` 找 P1-3——而那条目早已被压缩迁出，那份文件里根本没有它

- **Status**: ✅ 已修（341 轮） —— 全文逐字见 [`…-48`][a28]（341 轮）

### P2-102: 2026-10-03 那次把 `created_at` 改成可空的修正，只改了 16 个同型字段里的 1 个 —— 余下 15 个仍声明为非空，而后端一律发 `null`

- **Status**: ✅ 已修（341 轮） —— 全文逐字见 [`…-48`][a28]（341 轮）

### P2-103: `ExpRagService` 把 `created_at` 为 NULL 的成因写成「pre-migration data」——而当前的捕获路径就在持续制造 NULL
- **Status**: ✅ 已修（360 轮） —— 全文逐字见 [`…-58`][a14]
### P2-104: 架构文档 REST API 表的最后一行被挤到表外 —— 2026-10-03 插入的 SSE 引用块落在表格中间，GFM 只渲染到 Health 行为止
- **Status**: ✅ 已修（360 轮） —— 全文逐字见 [`…-58`][a14]
### P2-105: Demo README 的 `/actuator/health` 行同样被挤到表外 —— 与 P2-104 同一类，靠全库扫描才发现
- **Status**: ✅ 已修（344 轮） —— 全文逐字见 [`…-50`](../archive/2026-10-07_backend-review-evidence-50.md)
### P2-106: 全库 98 行表格的单元格数与表头不符 —— 多数是代码 span 里的裸 `|` 未转义，而渲染器根本检测不到这一类
- **Status**: ⏸ 记录不修（批量，逐文件核对成本高于本轮；本条按 Archived History 段的「标题紧跟 Status」体例排版以省 1 行） （证据与仪器结论逐字见 [`…-11`][a41]）

### P2-107: `DemoParams` 的类注释称 `MemoryController` 是唯一在 `/demo` 前缀外的控制器 —— 活体实测有两个，且它自己引用的 README 就列出了第二个
- **Status**: ✅ 已修（359 轮） —— 全文逐字见 [`…-57`][a16]
### P2-108: `python-sdk-design.md` 的两份目录树都停在设计日 —— 漏 `tests/test_demo.py` 与 `cortex_mem/py.typed`，并列出一个并不存在的 `LICENSE`
- **Status**: ✅ 已修（359 轮） —— 全文逐字见 [`…-57`][a16]
### P2-109: `TimelineService` 的地板注释断言了一个真实执行顺序到不了的异常——`subList` 永不先抛，`PageRequest` 先抛
- **Status**: ✅ 已修（359 轮） —— 全文逐字见 [`…-57`][a16]

### P2-110: `SearchService` 的策略树注释列了一个代码里不存在的维度（`files`），并把 filter 路径说成「via tsvector」
- **Status**: ✅ 已修（360 轮） —— 全文逐字见 [`…-58`][a14]
### P2-111: `ContextService` 的 `@param types` 写「empty = all types」，而实现把它展开成六项硬编码白名单——同段的 `concepts` 却是真·全量
- **Status**: ✅ 已修（360 轮） —— 全文逐字见 [`…-58`][a14]
### P2-112: `cortex-mem-starter` 的 `base-url` 一旦是空串就让整个 Spring 应用启动失败 —— 而同一仓库的后端对完全相同的模式早已显式处理过

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Evidence**: → 归档 [`…-87`][a45]（starter 空串 base-url 的复现与影响面、后端早已显式处理过同样模式的对照、README 未记载的逐行定位）
- **Status**: ⏸ 记录不修 —— 修法形状需作者或用户确认，见上。

### P2-113: 四家 SDK 的 fire-and-forget 失败默认可见性 2:2 分裂 —— Python 与 Java 默认会记日志，Go 与 JS 默认完全无痕
- **Status**: ⏸ 记录不修 —— 修法形状需作者或用户确认，见上。 全文逐字见 [`…-61`][a17]（361 轮）。**同子系统的另一条轴见 P2-161**（哪些方法吞错，四家集合不一致）。
### P2-114: 根 README 的架构图把薄代理标为 (Express)，而同一仓库的 `ARCHITECTURE.md` 与中文版早在 2026-10-01 就已改正 —— 根 README 是那次漏改的那一份
- **Status**: ✅ 已修（第 361 轮，零行为变更，纯文档） —— 架构图标注由 `(Express)` 改为 `(Node.js)`； 全文逐字见 [`…-61`][a17]
### P2-115: `backend-review-findings.md` 的字节上限已不可能靠压缩解决 —— 建议把阈值按第 293 轮同比例上调至 **2000 行 / 200000 字节**
- **Status**: ⏸ 记录不修 —— 属流程契约变更，需用户决策。 全文逐字见 [`…-61`][a17]（361 轮）。
- 第 368 轮新证据：实测按现行规则已无法再压缩（迁出待决条目 42 段后字节反升 +1210，42 段里 41 段本身是指针行，该次压缩已精确反推）⇒ 本条阈值上调已成下一轮写入的前置条件（余量仅 33 字节）。详见 [scope-13](../archive/2026-10-20_backend-review-scope-evidence-13.md)。
- 第 370 轮的压缩手段现状：本轮用掉三种零信息损失的手法，各有边界 —— ①删已结案 Status 尾部与头部重复的「（第 N 轮）」（28 条，省 479B，已用尽）；②按第 293 轮体例把归档指针的显示名缩短为 `…-NN.md`（**目标 URL 一字未动**，181 处对拍全等，省 1720B）；③已结案 Status 的「—— 」分隔符未动（动它就破坏「头是原 Status 逐字前缀」）。①已用尽、③不能用 ⇒ 下一次写入仍会触顶（当前余 1057B），阈值上调仍待决策。
### P2-116: Python SDK 的两种 `update_observation` 调用风格不等价 —— kwargs 路径真能清空 `narrative` / `extractedData`，而 SDK 自己的注释与 P2-27 都断言「不能清空」
- **Status**: ⏸ 行为记录不修（两种收法都改变现有调用方的可观测行为：让 kwargs 路径也过滤 `None`/`{}` 等于让今天已经能用的清空能力失效；新增显式清空入口等于新增公开 API）——**真正的修法要先决定「哪种写法才是 SDK 想支持的清空方式」，属 API 契约决策**，与 P2-26 的判断一致、不重复立条；注释层的失实陈述本轮已修（Python SDK 的 docstring 与双语 README，零行为变更由 AST 逐字证明）。全文逐字见 [`…-62`](../archive/2026-10-20_backend-review-evidence-62.md)（362 轮）。
### P2-117: JS SDK 的源码注释与双语 README 仍断言「四家里只有 JS 能把字符串字段清空为 NULL，Python 跳过 `None`」——而该断言早在第 247 轮就被本仓库自己证伪
- **Status**: ✅ 已修（363 轮） —— 全文逐字见 [`…-63`][a32]
### P2-118: API 文档 2026-10-07 的 changelog 行把请求体表归在 `POST /api/memory/observations` 名下——而该端点根本不存在
- **Status**: ✅ 已修（363 轮） —— 全文逐字见 [`…-63`][a32]
### P2-119: 四家 demo 的监听地址 1:3 分裂 —— Java demo 随 P1-2 硬化为只绑回环，**Go / Python / JS 三家至今绑所有网卡、零鉴权**，并把「只绑回环的后端」变成了同网段可达的 API
- **Status**: ⏸ 记录不修 —— 把默认改成回环 = 收窄可观测面（远程可达性消失）= 对外行为契约变更；且 P1-2 那次同类硬化是经用户授权才实施的（294 轮）。按既定纪律（对外契约变更不单方面实施）先记后定。建议修法（待决策）的三条与「本轮未实施任何代码改动」那段说明见归档正文。 全文逐字见 [`…-64`](../archive/2026-10-20_backend-review-evidence-64.md)（364 轮）。

### P2-120: 活体 OpenAPI 给 `POST /api/observations/batch` 声明了一个不可达的 400，并把 `ids` 标为必填 —— 而处理器每个分支都返回 200，非法 UUID 还被静默丢弃
- **Status**: ✅ 已修（365 轮） —— 全文逐字见 [`…-65`][a33]
### P2-121: `docs/` 下 13 条相对链接的目标根本不存在 —— 上游自动拆分后未更新，且「哪一篇承接了被引章节」需要内容判断，不宜机械改
- **Status**: ⏸ 记录不修 —— 13 条断链的目标在仓库里不存在任何候选（被引文件的逐条清单见归档正文）。机械改到任一现存文件都可能指错章节，故只记录不猜。同批已修的 15 条另计（见归档正文），二者不可混为一谈。 全文逐字见 [`…-65`][a33]（365 轮）。

### P2-122: 四家 SDK 都能配 API 密钥，但后端从不校验它 —— 密钥今天是零保护，而三份 README 把它写成「for auth」，Java SDK 的 javadoc 更直接断言「Null/blank = no auth」
- **Status**: ⏸ 服务端鉴权记录不实施（给后端加鉴权 = 对外行为契约变更，按纪律先记后定）—— 客户端侧四处失实陈述已修（注释与文档，可执行行 0）：Java SDK 的 javadoc 与实现措辞、README 属性表**补 `api-key` 行**、架构文档双语 `### Authentication` 段。三选一见归档正文。全文逐字见 [`…-66`][a34]（366 轮）。

### P2-123: Go SDK 的 `doFireAndForget` 把「重试 POST 是安全的」写成承重注释，而它点名了不在链上的处理器、归因了错误的机制、覆盖了三条里只有一条受保护的操作
- **Status**: ⏸ **服务端幂等化与 SDK 重试范围收窄留待用户决策**（对外行为契约变更）—— 注释层失实陈述已修（Go SDK 一个文件，可执行行 0）。全文逐字见 [`…-66`][a34]。
### P2-124: 部署指南的限流段把 `CLAUDEMEM_RATE_LIMIT_WINDOW_SECONDS` 写成「滑动窗口」，而实现是固定窗口 —— 边界处上限可以翻倍；同段还把 `retry_after` 说成「窗口长度」，实际是当前窗口的剩余秒数
- **Status**: ⏸ 限流器要不要改成真滑动窗口留待用户决策（属行为变更）—— 失实陈述已修（双语，纯文档）：变量表的「滑动窗口」改为「固定窗口、到期整窗重置」；`retry_after` 改为「当前窗口的剩余秒数」。三选一见归档正文。全文逐字见 [`…-67`](../archive/2026-10-20_backend-review-evidence-67.md)（367 轮）。


### P2-125: Python SDK README 用一个无日期的活体读数（跨项目返回的 distinct project 数）支撑「`list_observations` 会放宽项目过滤」这个结论，而该读数已经漂移
- **Status**: ⏸ **「省略 project 就跨项目返回」要不要在行为层收敛待决**（对外行为契约变更）—— 失实陈述已修（双语）：无日期的活体读数 16 实测漂移到 15，已**加日期 2026-10-20** 并注明「带日期的实测值」。全文逐字见 [`…-68`](../archive/2026-10-20_backend-review-evidence-68.md)（368 轮）。


### P2-126: API 文档的三个 ingest 端点请求体表遗漏了活体 OpenAPI 声明的 6 个字段——其中 `platform_source` 是 V18 引入且确实生效的，而 tool-use 的另三个是接受却被静默丢弃的
- **Status**: ⏸ **tool-use 那三个字段要不要真用起来待决**（对外行为变更）—— 文档缺口已修（双语）：三个 ingest 端点请求体表共补 6 行，其中 `platform_source`（V18 引入、确实生效）。三选一见归档正文。全文逐字见 [`…-69`][a35]。
### P2-127: 活体 OpenAPI 把 `POST /api/ingest/user-prompt` 的 `prompt_text` 标为 required，而后端**只强制 `session_id`** —— 活体 spec 失实，而 API 文档是对的
- **Status**: ✅ **选项①已实施（371 轮）**：去掉 `prompt_text` 的 REQUIRED 注解（纯放宽，零运行时影响）⇒ 活体 spec 的 `required` 已变为 `['session_id']`。全文逐字见 [`…-69`][a35]。

### P2-128: 四家 SDK 的参数下限只有 Python 的 README 写了；Java 的 `retry.backoff` / `read-timeout` 根本没有 100ms 下限，且只有 Go 有测试钉住
- **Status**: ⏸ **Java 要不要对齐另三家的下限、Java README 要不要补下限，待决**（对外行为契约）—— **Go 侧已于 379 轮结转**（纯文档/注释、零可执行行：Go 的四个下限本就由 **5** 个测试钉住且本轮实测全 PASS，本轮把双语 README 的选项表补齐四行下限、并给三个 Option 的 doc 注释补上下限）；**Java 侧仍待决**。JS 已修（双语补三行下限）：只有 Python 写了；Java 的 `retry.backoff` 无 100ms 下限（实测 1ns 原样）、`read-timeout` 无任何下限（0 原样）；四家里只有 Go 有测试钉住。三选一见归档正文。 原文逐字见 [`…-70`](../archive/2026-10-20_backend-review-evidence-70.md)（369 轮）；Go 侧全文见 [`…-80`][a39]。

### P2-129: 四家 demo 的 404/405 响应分裂成三种形态 —— JS 对方法不匹配答 404 + HTML、Go 对未知路径答 text/plain，而 Go demo 自己的 README 写着「Every error is {"error": "<message>"}」
- **Status**: ⏸ **Java 的 404/405 体要不要也拉齐，待决**（对外契约）—— Go 与 JS 已修：四家现状态码与 Content-Type 全部一致（405/404 + application/json）；三选一见归档正文。 全文逐字见 [`…-71`](../archive/2026-10-20_backend-review-evidence-71.md)（370 轮）。

### P2-130: 活体 OpenAPI 里 tool-use 的 `source` / `extractedData` / `prompt_number` 不说自己被丢弃 —— 而同一 DTO 文件的既有体例都注明了「只进日志、不落库、无效果」，`prompt_number` 甚至宣称「for ordering」
- **Status**: ✅ 已修（第 371 轮，纯 `@Schema` 描述文本、零可执行行）—— tool-use 的 `source`/`extractedData`/`prompt_number` 落 NULL/0 行。全文逐字见 [`…-72`](../archive/2026-10-20_backend-review-evidence-72.md)。
### P2-131: Java SDK README 的 V14 示例用错端点 —— `source`/`extractedData` 被后端丢弃
- **Status**: ✅ 已修（372 轮 Java / 373 轮 Go / 374 轮 Python / 375 轮 JS，README 双语 + 注释，零可执行行）—— tool-use 落 NULL/0 行，`updateObservation` 才落库。全文逐字见 [`…-73`][a18]、[`…-75`][a37]、[`…-76`][a19]。
### P2-132: TESTING.md 第 6 节把后端的 `DB_USERNAME`/`DB_PASSWORD` 写成了测试变量
- **Status**: ✅ 已修（372 轮，双语 + changelog 补注）—— 脚本读 `DB_USER`/`DB_PASS`，`scripts/README.md` 自己写着「the variable is DB_PASS, not DB_PASSWORD」。见 [`…-73`][a18]。
### P2-133: DEPLOYMENT.md 的 profile 默认值只在 compose 下成立，原生运行全不成立
- **Status**: ✅ 已修（372 轮，双语）—— 无 profile 时实测生效值是 dev 那一列，model/dimensions 被静默忽略。见 [`…-73`][a18]。
### P2-134: Go SDK 的 `IsClientError`/`IsServerError` 不认裸哨兵 —— 4xx/5xx 哨兵全判 false
- **Status**: ✅ 已修（373 轮，Go SDK + 3 测试）—— 13 个状态分类里仅它俩无哨兵回退；旧码失败 13 处。见 [`…-74`][a36]。
### P2-135: 活体 OpenAPI 两处少声明运行时行为 —— `session/start` 少 `project_path`、`quality-distribution` 少 400
- **Status**: ⏸ 记录不修 —— 对外契约变更；**EN/ZH 两版 API 文档均正确**。见 [`…-74`][a36]。
### P2-136: 四家 demo 的 create 端点把 `source`/`extractedData` 送进 tool-use 路径 ⇒ 从不落库
- **Status**: ⏸ 记录不修 —— 对外契约变更（三条修法见归档正文）；四家 demo 侧已全部注明（374 Python / 375 JS / 376 Go 与 Java；零可执行行）。见 [`…-75`][a37]、[`…-76`][a19] 与 [`…-77`][a38]。
### P2-137: JS SDK 设计文档说 `POST /api/session/start` 的响应不回显 `session_id` —— 活体实测回显
- **Status**: ✅ 已修（375 轮，设计文档，零可执行行）—— 活体响应确实回显 `session_id`；建议对、理由错。全文逐字见 [`…-76`][a19]。
### P2-138: Go demo README 把 tool-use 端点写成「**Direct** observation creation」—— 而后端**确实**有一个「直接创建」端点
- **Status**: ✅ 已修（376 轮，demo README，零可执行行）—— demo 把 `POST /api/ingest/tool-use` 与 `POST /api/ingest/observation` 写成同一个词，两个语义完全不同的端点。全文逐字见 [`…-77`][a38]。
### P2-139: `LlmService` 吐掉全部异常并返回空内容 —— 于是 `/api/test/llm` 在 LLM 完全失效时**谎报** `status: "success"`，仓库自带的巡检脚本据此打印「LLM test passed」
- **Status**: ✅ 已修（377 轮，Backend）—— `LlmService` 吞 provider 异常 ⇒ `/api/test/llm` 谎报 success；契约对、实现没兑现。全文逐字见 [`…-78`][a20]。
### P2-140: 四处 `java/` 前缀的路径在目录重命名后没跟上 —— 而其中一处让 `--config` **永远读不到配置**
- **Status**: ✅ 已修（377 轮，文档/脚本）—— 13 处 `java/` 陈旧路径；`test-llm-provider.sh --config` 实测「No .env file found」。全文逐字见 [`…-78`][a20]。
### P2-141: Go SDK 的 `./test-all.sh` 只测 **4** 个模块就宣布「All tests passed」—— 漏掉了 `examples/http-server` 的 **33** 个测试
- **Status**: ✅ 已修（377 轮，文档/脚本）—— `test-all.sh` 手写模块列表 ⇒ 漏 `examples/http-server` 的 33 个测试仍打印「All tests passed!」。全文逐字见 [`…-78`][a20]。
### P2-142: 活体 OpenAPI 给 `PUT /api/mode` 宣告的是**另一个端点**的请求体 schema —— 它宣告的必填字段 `mode` 自己从不接受（实测 400），而它真正接受的 `mode_id` 根本没宣告；同一端点的 3 处响应示例也全是 camelCase
- **Status**: ✅ 已修（378 轮，零可执行行）—— 内层 record 改名（解除 springdoc 同名归并）+ 显式 `@JsonProperty("mode_id")` + 3 处示例与 400 宣告改真；5 个测试含两重阳性对照。全文逐字见 [`…-79`][a21]。
### P2-143: 活体 OpenAPI 的 `ObservationEntity` **双向失配** —— 5 个幽灵属性（实现从不输出）+ 3 个真实属性（`content_hash`/`discovery_tokens`/`embedding_model_id`）根本没宣告
- **Status**: ✅ 已修（378 轮，零可执行行）—— 3 个字段补 `@JsonProperty`、2 个 List getter 标 `@JsonIgnore`；不变量测试「宣告集 == 实际序列化集」钉住，阳性对照逐个点名那 5 个幽灵属性。全文逐字见 [`…-79`][a21]。
### P2-144: `PUT /api/mode` 接受不存在的 `mode_id` 并静默回落到默认模式，响应里的 `mode_id` **回显请求值**而非真正生效的模式 —— 而它宣告的那个 400 分支是**死代码**
- **Status**: ⏸ **行为要不要改留待用户决策**（对外行为契约变更）—— 三条候选修法见归档正文；失实陈述已修（400 宣告改「空体」+ `@Operation` 描述改真，零可执行行）。全文逐字见 [`…-79`][a21]。
### P2-145: Go SDK 英文 README 写「the **two** capture methods」—— 同一 commit 的中文版写「三个」，实现里也确实是三个；且接口注释只给其中一个标了 fire-and-forget
- **Status**: ✅ 已修（379 轮，Go 侧，零可执行行）—— 英文「two」改「three」并点明三个捕获方法**都是** fire-and-forget；两版方法表的 Capture 组标注 (fire-and-forget)；**Java 侧同型结转已于 384 轮结转完成**（`CortexMemClient` 三个捕获方法的 Javadoc 全部补上 fire-and-forget 说明，并区分「后端失败被吞」与「客户端校验先抛」）。**P2-128 的 Go 侧**同批结转（四个下限的文档）。全文逐字见 [`…-80`][a39]。
### P2-146: 四家 SDK 的 README 都写「空 `tool_name` 会被后端以 400 拒绝、SDK 记一条日志后吞掉」—— 而客户端校验早在请求发出**之前**就拦下了它，请求数实测为 **0**
- **Status**: ✅ 已修（380 轮，Python 侧，文档 + 2 个回归测试）—— EN/ZH 双语 README 恢复反事实措辞并写明「客户端拦下、不发请求」；两版首屏测试数 **453 → 455**；新增 2 个测试钉住「校验发生在请求之前」；**Go / JS / Java 三家同型结转**（8 份 README）。**JS 侧 381 轮结转完成**（双语 README 同型句已改为反事实措辞 + 首屏计数 264 → 266，另补测试缺口 P2-147）。**Java 侧 384 轮结转完成**（双语 README 同型句改为反事实措辞 + 写明「客户端抛 `IllegalArgumentException`、一个请求也不发」；另补 3 个「请求数 0」用例钉住「校验发生在请求之前」）。**Go 侧 385 轮结转完成**（双语 README 同型句改为反事实措辞 + 写明「SDK 返回 `ValidationError`、一个请求也不发」；并补一条 **Go 独有**的细节：fire-and-forget 只吞**后端**错误，客户端校验错误仍**返回给调用方**）。**四家至此全部结转完成**。。全文逐字见 [`…-81`](../archive/2026-10-20_backend-review-evidence-81.md)；Go 侧见 [`…-86`][a44]。
### P2-147: JS SDK 的 `recordObservation` 必填校验里，`tool_name` 是**同族里唯一没有被任何测试钉住**的那一个 —— 且没有任何用例钉住「校验发生在请求发出**之前**」
- **Status**: ✅ 已修（381 轮，JS 侧，**只加测试、实现一行未改**）—— 补「空 `tool_name` 抛错」+「校验发生在请求之前（`fetchMock` 零调用）」两个用例；**变异测试**证明新用例失败而**旧用例仍通过** ⇒ 只断言异常的用例钉不住顺序。全文逐字见 [`…-82`][a40]。
### P2-148: JS SDK 双语 README 的「16 个不同项目」是**无日期**的测量断言 —— 活体实测（2026-10-21）是 **15**；Python 两版同一条却写「15」且**带日期**
- **Status**: ✅ 已修（381 轮，JS 侧，零可执行行）—— 双语 README 改为带日期的实测值（**2026-10-21 / 15**）+「随数据库增长」的限定，与 Python 两版同体例；**Python 两版不动**（带日期、当时为真）。全文逐字见 [`…-82`][a40]。
### P2-149: Go demo README 的 P2-33 段落里**两处**失实陈述 —— ①「另外 21 个端点在**四家**里拼写完全相同」，而这 21 个恰恰是**与 Java demo 拼写不同**的那批（四家路径交集只有 `/chat`）；② `main.go:470` / `main.go:771` 两处行号引用**已失效**（实际在 492 / 793）
- **Status**: ✅ 已修（382 轮，文档，零可执行行）—— ①改为「另外 **22** 条路径在 **Go / Python / JS** 三家拼写相同；Java demo 带前缀，故四家里拼写相同的只有 `/chat`」；②行号更正为 `main.go:492` / `main.go:793`（**事后漂移**）。全文逐字见 [`…-83`][a13]。
### P2-150: Java demo README 的「Phase 3 acceptance tests (20 tests)」—— 脚本实测是 **25** 个，且该计数无任何测试钉住
- **Status**: ✅ 已修（382 轮，文档，零可执行行）—— 改为「25 tests, measured 2026-10-21；脚本自己的 `Passed:` 行是当前值」；同段另两个计数（V14 **4**、V15 **3**）核实为真、未动。全文逐字见 [`…-83`][a13]。
### P2-151: Python demo 的**暴露面**（26 个端点）没有被任何测试钉住 —— README 首屏的「26」是一条**无日期裸计数**，与 Java demo 的「20 tests」同型
- **Status**: ✅ 已修（382 轮，代码方向，**只加测试**）—— `tests/test_demo.py` 新增 `TestDemoSurface` 两个用例：端点集与 README 表**双向**相等、首屏计数与路由数相等；三向**变异测试**证明它们能失败；两版 README 计数 **455 → 457**。全文逐字见 [`…-83`][a13]。
### P2-152: 活体 OpenAPI 的 `FeedbackRequest` 请求体描述仍写「not yet implemented」—— 而该端点早已实现并返回 200
- **Status**: ✅ 已修（383 轮，零可执行行）—— **事后漂移**（`4953b27` 实现端点时只改了控制器、没改 DTO 的 `@Schema`）；描述改为如实说明写入哪三个字段。活体 OpenAPI 全量扫描：**唯一一处**此类失实。全文逐字见 [`…-84`][a42]。
### P2-153: 活体 OpenAPI 有 **51** 个 schema 宣告 `type: string` 却带对象/数组 example —— 自相矛盾；PATCH 的请求体按 spec 发字符串活体 400
- **Status**: ✅ 已修（383 轮，Backend 代码方向）—— **两套机制**：M1 `@Schema(example=…)` 不写 `type` ⇒ 解析成 `string`（**51** 处）；M2 `@ApiResponse` 不写 `content` + `ResponseEntity<Object>` ⇒ 推成 `object` 而实际是数组。机制经**四种写法探针**选定（`implementation=` 会吃掉 example 且 type 仍是 string，被实测否掉）；3 个数组响应另带 `items`（**49** 个对象的 example 逐字未变）。新增不变量测试（反射扫全部控制器，**178** 个测试），**两次变异测试**各证明其能失败。全文逐字见 [`…-84`][a42]。
### P2-154: API 文档双语的端点/操作计数写「**67**」—— 活体是 **71** 个操作、**66** 条路径；同一份文档另一处却写「66 paths」
- **Status**: ✅ 已修（383 轮，文档方向，零可执行行）—— **事后漂移**（`3d415a7` 当天正好 67 个映射）；四处计数改为「**71** 个操作 / **66** 条路径，2026-10-21 实测」并给出**取数方式**与计数规则的例外（`ViewerController.java:214` 那个注释掉的路由）。两版端点**集合**仍完全一致。全文逐字见 [`…-84`][a42]。
### P2-155: Java SDK 捕获路径上有一道**Java 独有**的「`projectPath` 必须是绝对路径」校验 —— **零测试覆盖**、且在双语 README 里**零记载**
- **Status**: ✅ 已修（384 轮，Java 侧，**只加测试** + 文档补记）—— 9 个新用例（3 抛错 + 3「请求数 0」+ 3 阳性对照）；**变异测试**删掉 `requireAbsolutePath` ⇒ 2 个新用例红、其余 113 仍绿；双语 README 补「Java 独有规则」一段。全文逐字见 [`…-85`][a43]。
### P2-156: Java SDK 的「**吞错 vs 抛错**」契约 —— README 用它划分方法族，却**零测试钉住**（8 个方法一个都没测）
- **Status**: ✅ 已修（384 轮，Java 侧，**只加测试**）—— 8 个新用例（5 静默 + 3 传播），每个都断言「请求确实发出」+「结果是文档写的那一个」；**两次变异测试**（静默↔传播互换）各证明其能失败。全文逐字见 [`…-85`][a43]。
### P2-157: Java SDK 双语 README 的 Design Notes **自相矛盾** —— 同一节里相邻两条，把 5 个静默吞错的方法分别写成「propagate」与「swallow」
- **Status**: ✅ 已修（384 轮，文档方向，零可执行行）—— **写入即假**（`1fa843e`(2026-10-02) 写下时实现已是 `executeWithRetrySilent`；`20e3661`(10-03) 又补入正确那条 ⇒ 文档内自相矛盾）；两处改为「5 个传播 + 5 个静默是例外」，并修正 `:824` 的「never throw」。全文逐字见 [`…-85`][a43]。**同型已在 Go 侧随 P2-146 结转时一并修掉**：Go README 的「Required Arguments」段本就写着「sends no request」，而隐患段却说请求会出去并被 400 拒绝 ⇒ **同一文档内自相矛盾**；**四家 README 都有这一形状**。
### P2-158: Go SDK 的 **35** 个校验用例用裸 `NewClient()`（默认打**常驻实况后端** 37777），而全仓**零「请求数」断言** ⇒ 校验的**顺序**没被钉住，且一旦校验回归就会**往实况数据库写行**
- **Status**: ✅ 已修（385 轮，Go 侧，**只改测试**）—— 35 个用例改到 hermetic server（收到请求即判失败）+ 1 个阳性对照；**变异测试**把 `toolName` 校验挪到请求之后 ⇒ **新守卫报错、旧断言仍通过**；该用例临时改回旧写法后**照样 PASS**（一次真实网络往返）。全文逐字见 [`…-86`][a44]。
### P2-159: 四家 SDK README 的「必填参数规则集**完全相同**」—— Python / Go / JS 三家（双语共 6 处）都说「另三家强制的是完全相同的一组规则」，而 **Java 多一道 `projectPath` 必须是绝对路径**的检查
- **Status**: ✅ 已修（386 轮，Python 侧，文档，零可执行行）—— **写入即假**（Java 那道校验 2026-04-15 引入，断言 2026-10-03 写下）；按断言清扫共 **8** 处，其中 **6** 处失实；Python 双语两处改为「参数集合相同，只有一处例外：Java 额外要求绝对路径」，**Go / JS 各 2 处结转**。全文逐字见 [`…-87`][a45]。
### P2-160: Python SDK 的**三个** fire-and-forget 捕获方法里，只有 `record_observation` 的「吞错」契约被用例钉住 —— 另两个改成「抛错」路径后，全量 **457** 个测试**仍然全绿**
- **Status**: ✅ 已修（386 轮，Python 侧，**只加测试**）—— 3 个新用例（2 个吞错 + 1 个重试耗尽后仍吞错），每个都断言「请求确实发出」；**变异测试**把两个捕获方法改成抛错路径 ⇒ 改前全量 **457** 个测试**仍全绿**、改后**恰好 3 个新用例红**。全文逐字见 [`…-87`][a45]。
### P2-161: 四家 SDK 对「**哪些调用算异步后台任务**」的判定不一致 —— Java 吞掉 **5** 个方法的错误（3 个捕获 + `triggerRefinement` + `triggerExtraction`），另三家只吞 **3** 个；而**没有任何一份文档点出这条分歧**
- **Status**: ⏸ **只记录不实施**（对外行为契约变更）—— 四家「哪些方法吞错」的集合不一致（Java 5 / 另三家 3），**无文档点出**；三条候选修法见归档正文。与 **P2-113**（默认可见性）是同一子系统的两条不同轴，并列记录。全文逐字见 [`…-87`][a45]。
### P2-162: JS SDK 的**三个** fire-and-forget 捕获方法里，只有 `recordObservation` 的「吞错」契约被用例钉住 —— 另两个改成「抛错」路径后，全量 **266** 个测试**仍然全绿**

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-88`][a46]（三个捕获方法的同族关系与实现坐标、6 个用例只覆盖其中一个的逐条定位、缺口证明、3 个新用例的「双半断言」、两次变异的红绿计数与还原校验）
- **修法（本轮已修，JS 侧，只加测试，实现一行未改）**: 新增 **3** 个用例（`recordSessionEnd` 吞错 / `recordUserPrompt` 吞错 / `recordUserPrompt` 重试耗尽后仍吞错），每个都**同时**断言「请求确实发出」与「错误被吞掉」—— 只断言「不抛」在「客户端压根没发请求」时也会全绿。变异 A（`recordSessionEnd` 改抛错）⇒ 恰好 **1** 红；变异 B（`recordUserPrompt` 改抛错）⇒ 恰好 **2** 红；两次按 sha256 `194520e8…` 逐字节还原。
- **Status**: ✅ **已修（387 轮）** —— JS SDK 测试 **266 → 269**（本条 +3）。

### P2-163: 四家 SDK README 的「空更新错误消息**完全相同**」—— **8 处全失实**：四家的**错误类型与前缀各不相同**，只有消息**主体**逐字相同

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Evidence**: → 归档 [`…-88`][a46]（按断言清扫的 8 处定位表、四家实际错误类型与完整消息、规则那一半四家都成立的程序化对拍、`git log -S` 判定「写入即假」的提交证据）
- **修法（本轮只改 JS 双语 2 处；Go / Python / Java 各 2 处结转）**: 把断言改准 —— **规则**相同（空更新在任何请求发出之前就被拒）、**消息主体**四家逐字相同，但**完整字符串**与**错误类型**各不相同；并点明两条实务后果（按整条消息匹配要先剥前缀；按错误类型匹配要知道 Java 抛的是 `IllegalArgumentException`）。⭐ 修的是**失实陈述**，**不改任何 SDK 行为**。
- **Status**: ✅ **已修（387 轮，JS 侧）** —— Go / Python / Java 侧 6 处**结转**。

### P2-164: JS SDK 双语 README 说「另三家统一称项目参数为 `project_path`」—— **两种读法下都失实**：四家捕获方法的 **wire 字段都是 `cwd`**，SDK 层叫法是 Go `ProjectPath` / Python `project_path` / Java `projectPath`

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-88`][a46]（四家 wire 字段的程序化对拍表、Java 侧「`toWireFormat()` 而非 Jackson 序列化」的关键辨析 —— 差点误判成「捕获静默丢项目」、两种读法各自的失实点、「throughout」的更强失实）
- **修法（本轮已修，JS 双语 4 处）**: 改成「四家在捕获方法上发的 **wire 字段都是 `cwd`**；差别只在 **SDK 层的叫法**」；并顺带去掉表格前后重复的同一段叙述。
- **Status**: ✅ **已修（387 轮）** —— 与 P2-159 同源于 `6902968`（2026-10-03），属**写入即假**。

### P2-165: JS 侧空更新的**完整消息与错误类型**零测试钉住 —— 文档写死了那句话，测试却只做子串匹配

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-88`][a46]（两个旧用例只做子串匹配的定位、两处缺口证明的读数、新用例的四项断言、两次变异的归因分离）
- **修法（本轮已修，JS 侧，只加测试）**: 新增 **1** 个用例，断言 `err instanceof ValidationError` + `err.field === 'update'` + `err.message` **逐字等于** README 写的那串 + 不发请求。变异 1（截短消息）⇒ 恰好 **1** 红且**两个旧用例仍绿**；变异 2（换成 `APIError`）⇒ 恰好 **1** 红。
- **Status**: ✅ **已修（387 轮）** —— ⭐ 这是同族的**第四种形态**：前三族是「某个方法没测」，这一族是「**某个字段**没被钉住」。

### P2-166: JS SDK 的「**只有捕获路径重试**」这条契约**零测试钉住**，而 JS README 也**从未说明**它 —— 而 Java 侧的重试范围**大得多**（10 个方法 vs 3 个）

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-88`][a46]（四家重试矩阵与逐个实现坐标、JS 侧重试只存在于 `doFireAndForget` 的机制说明、缺口证明、3 个新用例含阳性对照、两次变异、Java README 那句本身也不完整的附带发现）
- **修法（本轮已修，JS 侧）**: ① **加测试** 3 个用例（`startSession` 对 503 只发 1 次 + 抛 `APIError` / `submitFeedback` 对 503 只发 1 次 + 抛 `APIError` / **阳性对照**：同一 mock 下 `recordObservation` 对 503 发 3 次）；② **补文档** 双语 README 新增「重试策略」一节，写明 `maxRetries` 只作用于捕获路径，并点明 Java 还重试那 7 个非捕获方法。
- **Status**: ✅ **已修（387 轮）** —— 与 P2-161 **不是重复立条**：P2-161 是**吞错**轴，本条是**重试**轴；与 P2-113（失败默认可见性）也不同。

### P2-167: Java demo 的 **44** 个端点暴露面**零测试钉住** —— 12 个 Controller、44 条 `(verb, path)`，此前**一个都没被断言过**

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-89`][a47]（12 个 Controller 的端点清单、44 条的完整集合、测试文件的覆盖盘点、三次变异的红绿计数与还原校验）
- **修法（本轮已修，Demo 侧，只加测试，实现零改动）**: 新增 `DemoEndpointSurfaceTest`（**3** 个用例）—— ① 断言 **44** 条 `(verb, path)` 的**集合相等**（多一条或少一条都红，不是包含关系）；② 断言无重复的 `(verb, path)`；③ 钉住「哪些 Controller 有 `/health` 兄弟端点」。
- **Status**: ✅ **已修（388 轮）** —— demo 测试 **40 → 50**（暴露面 3 + 静态 health 7），全绿。

### P2-168: demo 里 **6** 个静态 `/demo/*/health` 端点**恒返 200 `status:"ok"`** —— 后端完全宕机时仍报「健康」；与唯一的真健康检查 `/memory/health` **字段同名、语义相反**

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-89`][a47]（2×2 活体对照矩阵：后端死/活各打 7 个 health 端点、逐字节相同的读数、第 183 轮那次修复的 Javadoc 原文、6 个静态端点的实现坐标）
- **后果**: 拿这 6 个端点做存活探针的监控，在**后端完全不可达**时也会报「健康」；而同一进程里 `/memory/health` 与 `/actuator/health` 明明返 **503**。
- **修法（本轮已修）**: ① **加测试** `StaticHealthEndpointsTest`（**7** 个用例）钉住「恒 200、恒 `status:ok`、**client 零交互**」，并附**阳性对照**（同一 mock 在 `/memory/health` 上**确实**被观察到）；② **补文档**：Demo README 端点表补这 6 条并标注「静态、不问后端」，另加一段写明**七条里只有两个真检查后端**。⭐ 改这 6 个端点的**行为**属对外契约变更 ⇒ 只记不实施。
- **Status**: ✅ **已修（388 轮）** —— ⭐ 这是「**存活信号不能被别的东西代理**」这一族的**第三个实例**，方向与第 183 轮那次相反（那次是**假绿**，这 6 个是**恒绿**）。

### P2-169: `scripts/demo-v15-test.sh` 用 `curl -sf` ⇒ 把**业务合法的 404** 判成失败 ⇒ 它的**第一个测试必然失败**；且 `-s` 让失败消息**恒为空**（不可诊断）

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-89`][a47]（`-sf` 两个缺陷的活体读数、`TEST_PROJECT` 下零行的数据库查询、脚本的 JSON 校验本意分析、变异前后计数）
- **根因**: `/demo/extraction/latest` 对「无抽取结果」返 **404 `{"error":"No extraction found"}`** —— 这是**正确**的业务语义；而脚本的 `TEST_PROJECT` 几乎不可能有抽取数据（实测该 project 下 `mem_observations` **0 行**）⇒ `curl -f` 遇 404 短路 ⇒ **第一个测试恒失败**。脚本后半段有 JSON 校验 ⇒ 作者**本意**是接受非 2xx 的 JSON 响应，`-f` 与本意冲突。
- **修法（本轮已修，脚本层，零行为变更）**: 抽出 `fetch()` 显式拆状态码与响应体；`latest` 接受 **200 与 404**，并**加断言**「404 的 body 必须说明缺什么」；另两个要求 **200**。失败消息带 HTTP 状态码与完整响应体。
- **Status**: ✅ **已修（388 轮）** —— 修前 **2/0/1**（exit 1），修后 **3/0/0**（exit 0）；⭐ 判定是**更精确**而非更松：5xx 仍判失败，变异证明 404 的 body 不合要求时**仍红**。

### P2-170: 两个 demo e2e 脚本的 `check_demo` 用**后端**健康当 **demo** 存活的代理 ⇒ 后端宕机时误报「demo 没在跑」并**跳过整个套件** ⇒ 后端故障被伪装成「demo 没启动」

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-89`][a47]（活体读数：后端死时 `/actuator/health` 返 503 而 `/demo/observations/status` 返 200、修正前后脚本在同一条命令下的两种结局、两次变异的对照）
- **根因**: `check_demo` 打 `curl -sf "${DEMO_URL}/actuator/health"`，而该路径**聚合后端健康** ⇒ 后端一挂就返 **503** ⇒ 脚本报「Demo is NOT running」并 `exit 1` ⇒ **后端故障被伪装成「demo 没启动」**，排查方向被直接带偏。
- **修法（本轮已修，脚本层，零行为变更）**: 判据改为 `/demo/observations/status` —— demo **自身**的静态端点，**不依赖后端**。修正后打同一个（后端死的）实例：脚本从「跳过」变成「1 通过 / 2 失败」，失败消息直接指出是后端 I/O 错误。
- **Status**: ✅ **已修（388 轮）** —— ⭐ 与 **P2-168** 同族但**方向相反**（那是**假绿**，这是**假红**）；⭐ `scripts/run-all-e2e.sh` **不跑**这两个脚本 ⇒ 影响面是 `docs/TESTING.md`、Java demo README 与 `scripts/README.md` 三处把它当「会通过」的流程推荐。



## Processing Rules
- 第 384 轮新增两个压缩杠杆：**C8**（**⏸ 待决条目**的证据 bullets 整体迁入归档，工作文件留 `- **Scope**` 一行 + 一行 `- **Evidence**` 指针；`- **Problem**`（标题）、`- **⚠️ 为什么不单方面改**`、`- **Severity**`、`- **建议修法**`、`- **Status**` 一律留在工作文件——第 254 轮规则的应用，本轮首次用于 P2-88：归档侧持有证据段 **65 行 / 5355 字节**，工作文件真正迁出 **64 行 / 5307 字节**，全文件净 **−55 行 / −2027 字节**）；**C9**（归档指针行写成「一行说清迁走了什么 + 指向归档」，指针本身可回溯）。**探针自身的三处错**：①单文件 `grep -c "@Test"` 得 **118** 而 surefire 实收 **115** —— 差在**同一个文件里有第二个顶层测试类** `CortexMemErrorMessageTest`（3 个），故静态 `@Test` 计数只在逐文件与运行时分层对齐时才等于运行数；②两个变异叠在一起导致失败归因不清，拆成单变量后才各自点名；③变异 3 在变异 2 尚未还原时跑，报 2 红而非 1 红。硬证明与不动点见归档 `-85`。
- 第 383 轮新增两个压缩杠杆：**C6**（被引用 ≥2 次的 **28** 个归档路径转引用式，显示名与目标不变，零信息损失，省 **1750** 字节；只出现 1 次的 26 处保持行内）；**C7**（**38** 处多行 `- **Status**` 的续行并回一行，**字符流守恒**，让出 **136 行**、省 272 字节）。**与 382 轮「折行重排不做」的区别**：那轮的对象是叙述正文，本轮是 Status 元数据行。硬证明与脚本自身的两处错见归档 `-84` 的「压缩记录」。终值 **1386 行 / 147578 字节**。
- 第 378 轮续记压缩杠杆：**缩短已结案条目的 Status 行**（第 292/293 轮杠杆）时，
  把「被删掉的细节」做成**可断言的形式** —— 拆出每个反引号/引号/数字 token，
  逐个断言**逐字存在于该行链接的归档**里；比对 token 前**必须先摘掉链接目标**，
  否则文件名里的日期数字会变成假 token（第一版就踩了：`-41` 归档名里的 `07`）。
  硬证明四件套：条目标题数不变、归档链接数不变、行数不变、字节变化可复算。
- 第 316 轮新增流程规则（连续三轮教训的归纳）——落笔前先查该模块自己的文档：
  本循环已连续三轮出现同一模式：先凭代码把某处判成「缺口/缺陷」，下一轮才发现它是被双语文档明确记载的有意设计。
  | 轮次 | 初判 | 实际 |
  |---|---|---|
  | 312 | Java SDK 无类型化错误（对比 Go 11 哨兵 / Python 12 类 / JS 2 子类） | README `:506` 起有完整 Error Handling 章节，含走 cause 链的读法，刻意且已记载 |
  | 314 | `build/lib/` 下有陈旧 SDK 源码副本 | `python-sdk/cortex-mem-python/.gitignore:5` 忽略、0 跟踪文件 → 本地产物 |
  | 316 | Python SDK 无响应体上限（Go/JS 都有 10 MiB） | README（中英双语）有「Response Size Limit」节，写明**有意差异 + 原因 + 后果 + 建议** |
  规则：在把某处记成 finding 之前，先 `grep` 该模块的 README（中英双语）看它是否已自陈。
  「代码里没有」不等于「没人知道」——本项目在多处主动记载了权衡，
  这本身是好实践，不该被当成缺陷反复重提。
  技术事实与定性要分开：上述三处代码事实都没变，变的是定性（缺口 → 已记载的取舍），
  故更正记录时只改定性、保留事实，并把剩下的问题降级为产品决策。
- 第 293 轮续记：把第 292 轮的做法推广到其余 7 类逐字重复行（实测记录 6、Reproduction 3、已解决条目 4、复核记录 5+2、探针记录 2），一律只缩短显示名、链接目标不变，零信息损失。至此本文件里已没有可再压缩的重复：余下每一行要么是决策、要么是问题陈述、要么是指针。
- 第 292 轮补充压缩规则：第 254 轮把 33 条条目的 Scope / Evidence 整体迁入同一个归档，于是在本文件里留下 33 条逐字相同的行。本轮把其中 33 条完全相同的改写为短显示名 `…-8.md`（链接目标不变），零信息损失；另有 **5 条带第 275 / 278 轮追加内容**的一行未碰。与第 291 轮压缩 5 条「复核记录」是同一手法：同一事实不必逐字重复 N 遍。
- SDK/Demo findings are fixed in place with focused compile/test verification.
- Backend findings are fixed in place when small and safe; otherwise they remain here until the complete acceptance stage.
- Every finding must end as a code fix, a documented design decision, or an explicit skipped status. Reporting alone is not a valid resolution.
- After resolution, append the verification result and commit identifier here before moving the detailed entry to an archive.
- **⏸ 条目必须保留 `- **Problem**` 与 `- **Status`**（问题是什么、为什么这么定）。
  **2026-10-04（254 轮）经用户明确决策**：`- **Scope**` 与 `- **Evidence**`
  可与实测记录一并迁入归档，各留一行指针——Scope 内多为文件行号与实测细节，
  属可复现证据而非决策推理。此前只允许迁出实测记录（见第 241/244/247/253/254 轮先例），
  **本次是本文件建立以来第一次放宽**，其余适用范围仍未决。
- **逐轮摘要表已于第 258 轮整体移除**（第 219~256 轮全部行）。此前规则是「只保留最近一轮」，
  但本文件顶部从一开始就写着「逐轮叙述不再保留在本区块」，而这张表恰恰违反它自己写下的规则；
  每一行在 `patrol-rotation.md` 与 `doc-review-task.md` 里都有同轮、同等或更完整的叙述。
  **压缩脚本的两条硬约束（第 258 轮用一次销毁换来的）**：
  ①**在原始索引上算出的切割区间不得按升序复用**——每次替换都会让后续区间整体前移，
  必须**单遍顺序重建或倒序处理**；
  ②边界断言必须同时覆盖 `- **`、`### ` **与 `## `** 三种，只判前两种会让最后一个块一路吞到文件末尾。
  第 258 轮两条都漏了，结果是 P2-47 整块被销毁且未进归档。压缩后必须逐条验证：
  条目数、每条未迁出正文是否逐字仍在、指针数与归档块数是否相等、归档指针是否可解析。

## Archived History

历次压缩批次的完整记录已逐字迁入 [`…-log`](../archive/2026-10-04_backend-review-compression-log.md)（第 252 轮迁出）；各批次在 `docs/archive/README.md` 中亦有逐条登记。
2026-05-07 之前的完整审查日志见 [`…-history`][a22]。

### P2-64: Jackson 把 record 上的 `isX()` 当属性序列化——Java SDK 每个 PATCH 都多发一个调用方从未设置过的 `empty` 字段
- **Status**: ✅ 已修（330 轮） —— 全文逐字见 [`…-43`][a8]
### P2-65: Python SDK 的 `is_retryable` 只收状态码，而 Go/JS 的同名函数收的是 error——跨家移植得到一个永远返回 False 的重试判定
- **Status**: ✅ 已修（330 轮） —— 全文逐字见 [`…-43`][a8]
### P2-66: JS SDK 的 `content`/`narrative` 注释写了一条后端并不遵循的优先级规则，而它是四家里唯一不做冲突检测的
- **Status**: ✅ 已修（330 轮） —— 全文逐字见 [`…-43`][a8]
### P2-67: `AsyncConfig` 的类 Javadoc 把「按任务超时」列为它提供的能力——全后端不存在任何超时机制
- **Status**: ✅ 已修（330 轮） —— 全文逐字见 [`…-43`][a8]
### P2-68: `updateObservation` 的 Javadoc 说「null 会被忽略」，其下的 `@Operation` 说「null 会清空」——后者才是真的
- **Status**: ✅ 已修（330 轮） —— 全文逐字见 [`…-43`][a8]
### P2-69: `SessionLifecycleController` 的 `promptNumber` 是 demo 里唯一没有范围检查的数值参数——负数被接受并落库
- **Status**: ⏸ 记录不修（第 282 轮定） —— 结论与证据均未变，本轮无待办动作；全文逐字见 [`…-51`][a29]（344 轮）。
### P2-70: Compose 把数据库与后端都发布到所有网卡，而架构文档的 Network Security 表称二者「仅本地」
- **Status**: ⏸ 记录不修（第 291 轮定，文档侧已双语更正） —— 结论与证据均未变，本轮无待办动作；全文逐字见 [`…-51`][a29]（344 轮）。

<!-- 归档链接的引用式定义：目标与行内写法逐字相同，只为避免同一路径重复书写 50 字节 -->
[a1]: ../archive/2026-10-04_backend-review-evidence-12.md
[a2]: ../archive/2026-10-04_backend-review-scope-evidence-8.md
[a3]: ../archive/2026-10-19_backend-review-evidence-59.md
[a4]: ../archive/2026-10-06_backend-review-evidence-36.md
[a5]: ../archive/2026-10-07_backend-review-evidence-41.md
[a6]: ../archive/2026-10-19_backend-review-scope-evidence-9.md
[a7]: ../archive/2026-10-20_backend-review-scope-evidence-10.md
[a8]: ../archive/2026-10-07_backend-review-evidence-43.md
[a9]: ../archive/2026-10-07_backend-review-evidence-53.md
[a10]: ../archive/2026-10-04_backend-review-evidence-11.md
[a11]: ../archive/2026-10-04_backend-review-provenance-3.md
[a12]: ../archive/2026-10-04_backend-review-resolved-13.md
[a13]: ../archive/2026-10-20_backend-review-evidence-83.md
[a14]: ../archive/2026-10-19_backend-review-evidence-58.md
[a15]: ../archive/2026-10-07_backend-review-evidence-40.md
[a16]: ../archive/2026-10-07_backend-review-evidence-57.md
[a17]: ../archive/2026-10-19_backend-review-evidence-61.md
[a18]: ../archive/2026-10-20_backend-review-evidence-73.md
[a19]: ../archive/2026-10-20_backend-review-evidence-76.md
[a20]: ../archive/2026-10-20_backend-review-evidence-78.md
[a21]: ../archive/2026-10-20_backend-review-evidence-79.md
[a22]: ../archive/2026-09-30_backend-review-findings-history.md
[a23]: ../archive/2026-10-03_backend-review-provenance.md
[a24]: ../archive/2026-10-05_backend-review-evidence-16.md
[a25]: ../archive/2026-10-06_backend-review-evidence-33.md
[a26]: ../archive/2026-10-06_backend-review-evidence-34.md
[a27]: ../archive/2026-10-06_backend-review-evidence-35.md
[a28]: ../archive/2026-10-07_backend-review-evidence-48.md
[a29]: ../archive/2026-10-07_backend-review-evidence-51.md
[a30]: ../archive/2026-10-07_backend-review-evidence-54.md
[a31]: ../archive/2026-10-07_backend-review-evidence-55.md
[a32]: ../archive/2026-10-20_backend-review-evidence-63.md
[a33]: ../archive/2026-10-20_backend-review-evidence-65.md
[a34]: ../archive/2026-10-20_backend-review-evidence-66.md
[a35]: ../archive/2026-10-20_backend-review-evidence-69.md
[a36]: ../archive/2026-10-20_backend-review-evidence-74.md
[a37]: ../archive/2026-10-20_backend-review-evidence-75.md
[a38]: ../archive/2026-10-20_backend-review-evidence-77.md
[a39]: ../archive/2026-10-20_backend-review-evidence-80.md
[a40]: ../archive/2026-10-20_backend-review-evidence-82.md
[a41]: ../archive/2026-10-20_backend-review-scope-evidence-11.md
[a42]: ../archive/2026-10-20_backend-review-evidence-84.md

[a43]: ../archive/2026-10-20_backend-review-evidence-85.md
[a44]: ../archive/2026-10-20_backend-review-evidence-86.md
[a45]: ../archive/2026-10-20_backend-review-evidence-87.md
[a46]: ../archive/2026-10-20_backend-review-evidence-88.md
[a47]: ../archive/2026-10-20_backend-review-evidence-89.md
