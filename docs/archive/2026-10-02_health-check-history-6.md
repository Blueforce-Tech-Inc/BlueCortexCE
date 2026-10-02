# 巡检与健康检查历史（第 158~162 轮）

> **Archived from**: `docs/drafts/health-check-task.md`
> **Archived on**: 2026-10-02（活动文件达 1000 行阈值，写入前压缩迁移）
> **Contents**: 第 158~162 轮（5 条），按轮号提取并逐块断言自报轮号
> **Rule**: 本文件创建后不得修改。

## 第 158 轮 — 2026-10-02（JS SDK + SDK README）

**本轮的核心是一条贯穿四个 SDK 的缺陷，跨三轮才收口。** 后端把
`mem_observations` 的 JSONB 列序列化为 **JSON 编码的字符串**（为 WebUI），
而非 JSON 数组。第 157 轮在 Python 上发现，第 158 轮在 JS 与 Java 上收口。

### 代码方向：JS SDK

**JS-1（P1）`parseObservation` 用 `safeStringArray` 解析四个 JSONB 字段。**

`safeStringArray` 只接受真实数组，对字符串返回 `undefined`。活体取证（修复前）：

```
WIRE concepts  : "[\"allergy\",\"peanut\"]" (typeof string)
SDK concepts   : undefined
SDK facts      : undefined
SDK filesRead  : undefined
```

与 Python 不同，JS 调用方**连「本来就没有」和「解析失败」都分不清**——都是
`undefined`。修复用的正确工具本来就在同一个文件里：`refinedFromIds` 一直用
`safeStringOrStringList`（双形态），只是另外四个字段没用。改为统一使用，
并移除随之不再使用的 `safeStringArray` import。

**JS-2（既有夹具再次掩盖缺陷）。** 原 `parseObservation` 夹具写的是
`facts: ['f1','f2']`——真实数组，而非真实 wire 形态，因此永远不可能发现此问题。
新用例采用真实响应体形态，并同时校验同一响应体中的标量字段
（`projectPath`、`content`），确保「什么都不返回」的解析器无法蒙混过关。
另一个用例 `should handle type mismatches in wire fields` 拿字符串当「错误类型」
的例子并断言 `undefined`——其对数字/对象的防御意图仍然正确，故保留那两类，
字符串移出（与第 157 轮 Python 的处理方式一致）。

退回 `parseObservation` 到 `safeStringArray` 后，219 个测试中**恰好 1 个失败**。
JS 216 → **219** 全绿；tsc 干净、CJS + DTS 构建成功；`js-sdk-e2e-test.sh` **27/27**。
活体复验：`listObservations` 现在返回 `["allergy","peanut"]`。

**JS-3 收口第 156 轮推迟的 `maxRetries` 文案。** 选项注释与两版 README 都写
"Maximum retries"，而循环与日志字段计的是 attempts。实测 1→1、2→2、3→3。
**改文案不改行为**（Go/Python/Java 三家都计 attempts），并加测试钉住 1/2/3 三档。

### 跨方向修复：Java SDK（本轮方向之外，已完成）

**JAVA-1（P1，本项目至今最严重的 SDK 缺陷）`listObservations` 对**任何**项目都返回空列表。**

`mapToObservationResponse` 直接强转：

```java
(List<String>) raw.get("facts")
```

而线上 `facts` 是 `String`，强转抛 `ClassCastException`；`listObservations`
外层是 `catch (Exception e) { return new PagedObservationResponse(List.of(), false); }`
——**异常被吞掉，整页观察全部消失**。活体探针（修复前）：

```
WARN CortexMemClientImpl -- Failed to list observations: class java.lang.String
     cannot be cast to class java.util.List
PROBE itemCount  = 0        （该项目实际有 20 条）
```

修复后同一探针返回 `itemCount = 20`。`getObservationsByIds`、`getObservation`、
`search` 共用该映射方法，受影响方式相同。

**为何跨方向修**：证据、根因与修复方案当时均已在手，该缺陷使 Java SDK 观察读取的
**整个表面静默返回空**，留给后续轮次等于明知存在全量数据丢失还再放两个周期。
本轮自身方向（JS SDK）已先行完成并修复。

**既有测试同样无法发现**：`listObservations` 系列测试全部 enqueue 空 `items` 数组，
`getObservation_returnsFirstObservation` 用的 observation 则**完全不含列表字段**。
新测试用真实响应体，并同样校验标量字段。退回四个强转后，90 个 client 测试中
**恰好 1 个失败**。Java SDK 175 → **177**（client 122 → 124），三模块全绿。

**跨 SDK 现状（本缺陷收口后）**：Go 一直正确（`StringList`），Python、JS、Java
本轮均已修复。

### 文档方向：SDK README

`maxRetries` 语义已在两版 JS README 订正；JS 与 Python 两版 README 新增
「JSONB 列表列的 wire 形态」说明，注明后端以 JSON 编码字符串下发、SDK 两种形态
都能解析、非 JSON 字符串按逗号切分降级。Go SDK 早在代码注释中记录了该形态。

### 验收

指纹 `eb7ba4ea…` → `61c62a6f…`（JS 与 Java 代码均有改动）→ 完整验收：
`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**；
`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
全通过，基线推进至 `1a3ca8b` / `61c62a6f…`。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录）。
3. **AGENTS.md 幻影端点**（第 154 轮记录）——该文件被 gitignore。
4. **Java SDK 其余方法的失败返回无统一约定**（第 155 轮记录）。
5. **三 SDK 一致缺口**（Observation 9 字段、`projectPath`、`/api/projects` V18 字段）。
6. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
7. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围。
8. push 权限阻塞（`wubuku` 403）。
9. 并行巡检进程争写状态文件。
10. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：JS SDK 完成（新循环第十一轮），下一方向 Demo；
文档审查轮换推进：SDK README 完成（五十九轮），下一方向 设计文档。

## 第 159 轮 — 2026-10-02（Demo + 设计文档）

### 代码方向：Demo（Java Demo，14 个类，此前未深审）

**DEMO-1（P1）`POST /demo/feedback` 把后端的 404 变成了 500，且丢弃了真实原因。**

后端对不存在的 `observationId` 返回 404 与
`{"error":"Observation not found: <id>"}`；但 SDK 把该异常包成
`RuntimeException("submitFeedback failed")` 并把真实原因留在 cause 里，
于是 Demo 端既够不到状态码也够不到消息，一律 catch 成 500。活体对比：

| | 状态码 | 响应体 |
|---|---|---|
| 修复前 | **500** | `{"error":"Submit feedback failed: submitFeedback failed"}` |
| 修复后 | **404** | `{"error":"Observation not found: 00000000-…"}` |

两处同时出错：**状态码谎报发生了什么**，消息又**丢掉了后端的解释**——
"submitFeedback failed" 只是 SDK 的通用包装文案，调用方拿不到任何可据以行动的信息。

其余三个 Demo 早就是对的（Go 把 NotFound 映射为 404，Python 与 JS 直接透传
后端状态），Java 是唯一的例外；而且它**与自己也不一致**：
`ExtractionController` 已经会返 404 并透传后端消息，
`ObservationsController` 也已经有私有的 `isNotFound()` 因果链遍历。

修复：新增 `DemoErrors`（包级私有，提供 `isNotFound`/`statusOf`/`messageOf`），
`FeedbackController` 改用它；`ObservationsController` 的私有副本改为委托，
不再保留同一段遍历的第二份实现。`messageOf` 优先取后端 JSON 的 `error` 字段，
无该字段时回落到 HTTP 状态行，因果链里完全没有 HTTP 异常时返回 `null`
（让调用方可以回落到 SDK 的通用消息）。

新增 `DemoErrorsTest` 5 例：嵌套因果链、非 404 状态、链中无 HTTP 异常、
空 body 回落状态行、`error` 字段中的引号反转义。

**顺带确认第 158 轮的 SDK 修复同时修复了 Demo**：重启 Demo 并 `mvn install`
新 client 后，`/demo/observations` 的条目数由 **0 → 3**，对含非空 concepts 的
记录读出 `['allergy','peanut']`（此前整页为空）。这说明该 SDK 缺陷的影响面
一直延伸到 Demo 的主列表端点，而 `demo-v14-test.sh` 的 4 个用例只覆盖
`/memory/*`，从未触碰 observations 端点——所以它 4/4 全绿也发现不了。

**活体验证**（Demo 起于 37778，本轮结束后已停止）：不存在的 id → 404 且带后端原因；
真实 observation → 仍 200 `{"status":"submitted"}`，正常路径无回归；
`demo-v14-test.sh` **4/4**。

**一处自我纠错**：测试初版用了本 Spring 版本不存在的
`RestClientResponseException` 构造器，改为 `(String, HttpStatusCode, String,
HttpHeaders, byte[], Charset)` 重载。

**核实后确认无误、未改**：`/demo/manage/refine` 对未知项目返 200（后端同样 200，
一致）；`/demo/extraction/latest` 对未知项目已正确返 404 并透传后端消息；
observations 的 limit/offset 边界、batch 的 ids 校验、content/narrative 别名冲突、
各类字段类型校验均按预期返回 400。另注：Java Demo 的基路径不统一
（`MemoryController`/`ProjectsController`/`ToolsController` 在根路径，其余 8 个在
`/demo` 下），这与 `demo-v14-test.sh` 和 README 的既有约定一致，改动会破坏该脚本，
故不动，仅记录。

### 文档方向：设计文档

**DOC-1** `phase-3-design/21.md` 的 21.2 把「LlmService 可用性守卫缺失」当作
未决问题并给出补丁，但**该守卫早已实现**：
`StructuredExtractionService.runExtraction()` 第 79 行正是该节提议的守卫
（仅日志级别由 `log.debug` 改为 `log.warn`）；第 116 行
`reExtractForSession()` 还有第二处，折叠在既有的 enabled 判断里。

第二处此前无人记录，而它更要紧：`reExtractForSession` 是 PATCH userId 的
重抽取路径，因此**无 LLM 的服务器上改 user_id 会被跳过而不是抛异常**。

同时更正了失效的行号引用（"LlmService.java line 43-44" 现为 46-47），
原分析折叠进 `<details>` 保留推理过程，标题加上与相邻 21.3 一致的
`~~…~~ — RESOLVED` 标记。

**DOC-2 核实后确认无误、未改**：21.1 的 10 个前置仓库方法**全部存在且各只有一处定义**
（findBySourceIn、findNewObservations、findByTypeGlobal、findByTypeLike、
findByContentSessionIdAndType、chatCompletionStructured、findByUserId、
findSessionIdsByUserIdAndProject）；`SessionEntity` 确有 `@Column(name = "user_id")`；
无任何迁移提到 `user_id`——这正是 21.1 所称的「managed via Hibernate DDL」。
无其他文档交叉引用 21.2。

### 验收

指纹 `61c62a6f…` → `ea585f34…`（Demo 代码有改动）→ 完整验收：
`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**；
`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
全通过，基线推进至 `671ea1a` / `ea585f34…`。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录）。
3. **AGENTS.md 幻影端点**（第 154 轮记录）——该文件被 gitignore。
4. **Java SDK 其余方法的失败返回无统一约定**（第 155 轮记录）。
5. **Java SDK `executeWithRetry` 丢弃后端错误消息**（本轮新记录）——它抛出
   `RuntimeException(operation + " failed", cause)`，后端 `error` 字段只能由调用方
   遍历 cause 链取得。本轮已在 Demo 侧解决，但 SDK 侧根治更合理，留待 Java SDK 轮次。
6. **三 SDK 一致缺口**（Observation 9 字段、`projectPath`、`/api/projects` V18 字段）。
7. **Java Demo 的 `/observations/*` 无 E2E 覆盖**（本轮新记录）——
   `demo-v14-test.sh` 只测 `/memory/*`，正是它让第 158 轮的整页丢失缺陷得以长期潜伏。
8. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
9. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围。
10. push 权限阻塞（`wubuku` 403）。
11. 并行巡检进程争写状态文件。
12. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Demo 完成（新循环第十二轮），下一方向 Backend；
文档审查轮换推进：设计文档完成（六十轮），下一方向 架构文档。

## 第 160 轮 — 2026-10-02（Backend + 架构文档）

**本轮最重要的产出是一个此前不存在的 Backend 缺陷（P1-2），由一次"我的载荷发错了"的意外取证牵出。**

### 代码方向：Backend

**BACK-1（P2，已修）`SummaryRepository.findByContentSessionId` 无序返回，调用方取首行。**

`ImportService:378-382` 取 `existing.get(0).getId()` 并把它作为「重复项的 id」报给导入方。
`mem_summaries.content_session_id` 只有普通索引、**没有唯一约束**（V1:109、V13:73），
结构上允许多行。实测规模：分页扫 `/api/summaries`，**896 个不同 session id 中有 212 个
拥有多于一条 summary，单个会话最多 33 条**。无 `ORDER BY` 时 PostgreSQL 不保证行序，
`get(0)` 可能在两次调用间返回不同的 id。

修复：查询加 `ORDER BY s.createdAtEpoch DESC`，与既有
`idx_summaries_created (created_at_epoch DESC)` 索引同向，排序由索引提供。语义不变
（调用方只是想拿到某个已存在的 summary id），签名未变，唯一调用方无需改动。

**如实定性**：该症状**无法通过公开 API 观测**——`/api/import/summaries` 只返回计数，
从不返回重复项 id。因此这是「已修复的潜在非确定性」，与第 154 轮上下文缓存那次的
定级一致。未添加仓储层测试：后端测试全是纯单测、**无 `@DataJpaTest` 基建**，
要证明排序需引入 testcontainers 或嵌入式库，分量超出本次修复。

**BACK-2（P1，记录待修）导入端点把校验失败报成「成功跳过」。**

四个单记录循环（`ImportController:208/229/290/349`，覆盖 sessions、summaries、
user prompts 及两个独立端点）都是：

```java
if (result.imported()) { …imported… } else { …skipped… }
```

但 `ImportService.ImportResult` 有**三个**工厂方法而非两个：
`imported(id)`、`duplicate(id)`、`error(message)`。于是 `else` 分支把
`error(...)` 一并吞进 skip 计数；而响应的 `errors` / `errorMessages` **只接收抛出的异常**
（`catch (Exception e) { errors.add(e.getMessage()); }`）。**校验失败因此长得和成功一模一样。**

取证经过：我先用 camelCase 载荷探测，拿到

```json
{"success":true,"imported":0,"skipped":1,"errors":0,"errorMessages":[]}
```

而记录其实被静默丢弃了。原因是我自己踩的坑——后端全局 `SNAKE_CASE`，`sessionId` 并不绑定。
换成 `session_id` 后走到真实路径，FK 违例是**抛出**的，于是被正确上报：

```json
{"imported":0,"skipped":0,"errors":1,"errorMessages":["…violates foreign key constraint…"]}
```

**同一类失败，仅因发生在「抛出」还是「返回」两条路径上，报告方式就完全不同。**
影响是导入/迁移期间的**静默数据丢失**：字段名拼错、大小写用错或漏掉必填字段的客户端，
会收到一个声称一切正常的成功响应。`ImportResult.duplicate(id)` 与
`ImportResult.error(message)` 可由 `id() == null` 区分（只有 error 工厂不设 id）。

**正确范式就在本仓库内**：`ImportService.importObservations`（307-318 行）显式用
`result.addError("sessionId is required")` 区分校验失败，`BulkImportResult` 自带 `errors`
字段——只有这四个单记录循环偏离。

未当场修复的原因：需改 4 处调用点且依赖 `id == null` 这一隐式约定，Backend 轮次按规则
对复杂项只记录。已在 findings.md 记为 P1-2，并写明复审触发条件。

**BACK-3（核实后确认不是缺陷）`RateLimitService` 的 DNS/头注入路径当前不可达。**

`isValidIpAddress` 对含冒号的字符串调用 `InetAddress.getByName`，**可能触发 DNS 解析**；
若 `X-Forwarded-For` 可控且在限流关键路径上，这会是延迟放大面。核查调用链后确认**不可达**：
`getRemoteAddr()` 仅由 `generateFallbackKey()` 调用，后者仅在 `key` 为空时触发，而
`tryAcquire` 的唯一调用方 `IngestionController:132` 恒传 `"tool-use:" + contentSessionId`
（非空）。故**不作为活动缺陷**，仅在 findings 中记为潜在项：一旦将来有调用方传入可空 key，
就会继承该行为。差点把它当成真缺陷记进去——这正是先核实再动手的价值。

### 文档方向：架构文档

**未发现缺陷**（全部为实跑核验结论，非「看起来没问题」）：

- **六张表的列逐条比对，DROP 感知后完全一致**：`mem_sessions` 17/17、
  `mem_observations` 35/35、`mem_summaries` 15/15、`mem_user_prompts` 8/8、
  `mem_pending_messages` 18/18、`observation_feedback` 6/6——**零幻影列、零遗漏列**。
  首轮比对时出现 4 个「多出」的列（`memory_session_id`×3、`embedding`、`embedding_3072`），
  追查后确认分别由 V13 与 V2/V7 `DROP` 掉，文档未收录它们是对的。
- 文档列出的 **9 个索引全部存在**；「composite index 取代单列 `idx_obs_source`」的
  说法属实（V16:13 `DROP INDEX IF EXISTS idx_obs_source`）。索引节是示例性列举而非
  穷举（迁移共 45 个索引），文档未作穷举声明，故不构成缺陷。
- 中英两版标题层级序列**逐位一致**（各 54 个）；Import 端点仅有高层描述，
  无响应形状断言，无可失实之处。

### 验收

指纹 `ea585f34…` → `fc1cf137…`（Backend 代码有改动）→ 完整验收：
`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**
（跳过项为 `build_app` 的 "Skipping build"，故摘要 Total 显示 46）；
`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
全通过，基线推进至 `06729b9` / `fc1cf137…`。

另：本轮写入前活动文件已 962 行 / 82513 字节，追加本轮后必然突破 1000 行阈值，
故先执行归档——第 149~157 轮（9 条）移入
`docs/archive/2026-10-02_health-check-history-5.md`，并更新 `docs/archive/README.md`。
活动文件回落到 431 行 / 24725 字节。归档时发现**第 157 轮的区块曾被错误插入到
第 156 轮正文之中**（当时用 `rindex` 匹配「下一方向」字样，命中了 156 轮正文内的
同一措辞），导致其物理位置排在 159 轮之后；归档脚本改为**按轮号而非物理位置**
提取，顺序已纠正，内容未作任何改动。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）。
2. **P1-2 导入端点把校验失败报成成功跳过**（本轮新增，四处调用点，见 findings.md）。
3. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录）。
4. **AGENTS.md 幻影端点**（第 154 轮记录）——该文件被 gitignore。
5. **三 SDK 一致缺口**（Observation 9 字段、`projectPath`、`/api/projects` V18 字段）。
6. **Java Demo 的 `/observations/*` 无 E2E 覆盖**（第 159 轮记录）。
7. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
8. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围。
9. push 权限阻塞（`wubuku` 403）。
10. 并行巡检进程争写状态文件。
11. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Backend 完成（新循环第十三轮），下一方向 Java SDK；
文档审查轮换推进：架构文档完成（六十一轮），下一方向 用户指南。

---

## 第 161 轮 — 2026-10-02 17:32 — Java SDK + 用户指南

### 代码方向：Java SDK

**JAVA-2（P1，已修）`executeWithRetry` / `executeWithRetryReturn` 把后端错误消息丢弃。**

两处抛出处都是 `throw new RuntimeException(operation + " failed", lastException)`，
日志也用 `lastException.getMessage()`；而 `RestClientResponseException.getMessage()`
**不携带**后端的 `{"error": "..."}` 响应体——第 159 轮活体观测到的
`{"error":"Submit feedback failed: submitFeedback failed"}` 正是这样来的，
真实原因只能靠遍历 cause 链取得。SDK 里其实**已有** `tryExtractErrorMessage()`，
但只被 `getStats` 一处使用，两个重试封装从未调用过它。

修复：新增 `describe(Throwable)`——遍历 cause 链找 `RestClientResponseException`，
取不到消息时回落类名；两个抛出变体的日志与异常消息、以及 `executeWithRetrySilent`
的两处 warn 都改用它。**原异常仍作为 cause**，调用方对状态码的判读不受影响。

活体验证（`mvn install` 后 Java Demo 起于 37778，本轮结束已停）：

```
404 -> {"error":"Observation not found: 00000000-0000-0000-0000-000000000000"}
400 -> {"error":"Submit feedback failed: Invalid observationId format: not-a-uuid"}
```

第二行就是本次改变的那一行（此前为 `submitFeedback failed`）。第一行本轮无变化——
Demo 的 `DemoErrors`（第 159 轮）已从 cause 链取到后端消息并透传。两处互补而非重复：
Demo 负责状态码映射，SDK 负责消息来源。

新增 `CortexMemErrorMessageTest` 3 例（后端 error 字段进入抛出消息、非 JSON body 仍产出消息、
cause 仍为 `RestClientResponseException`）。退回两个抛出变体后，3 个中**恰好 1 个失败**。
Java SDK 177 → **180 全绿**（client 124→127 / spring-ai 46 / starter 7）。

**核实未记为缺陷**：`maxRetries` 恒为 `Math.max(1, ...)`，故 `lastException` 为 null 时的
NPE 路径**不可达**；`isRetryable` 正确排除 500，与 Go SDK 一致。

### 文档方向：用户指南

**DOC-1（P1，已修）`docs/go-sdk-demo-guide.md` 端口全错。**

指南称 Go Demo "starts on port 8080" 并给出 29 处 `localhost:8080` 的 curl，而
`main.go:878-880` 从 `PORT` 环境变量读端口、默认 **37779**，指南的 How to Run 段
**从未提及 `PORT` 存在**。故障排查段错得更糟：声称 Demo "hardcodes port 8080"，
并要求**直接改源码里的 `addr` 变量**——而端口本就可由环境变量配置，
读者会为一件不需要改代码的事去改代码。已全部改为 37779，补 `PORT` /
`CORTEX_BASE_URL` 说明，修正启动横幅为
`🚀 Go SDK HTTP server starting on :37779 (backend: http://127.0.0.1:37777)`，
把端口冲突的处置改为设环境变量而非改源码。

改前先核实（避免把文档改成一个同样失实的形状）：

- 24 个已注册 `mux.HandleFunc` 路由与指南的 "All 24 Endpoints" 章节**完全一致**；
- Demo 调用的 SDK 方法**恰好是 `Client` 接口的全部 27 个**，无缺无多；
- 开篇 "exposing all 27 SDK methods as REST endpoints" 措辞不严谨——其中 25 个是 API 方法
  分布在 24 条路由上，`Close` / `String` 是生命周期与调试方法、没有路由。已改写；
- Go Demo 自己的 README 早已全程用 37779，两文档现已一致。

结构核验：指南 679 行、130 个围栏（偶数）、无残缺表格行；24 处 curl 目标全部为 37779，
剩余 3 处 `8080` 字样均为有意保留的替代端口示例。

### 验收

指纹 `fc1cf137…` → `4418ac7e…`（Java SDK 有代码改动）→ 完整验收：
`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**；
`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
全通过，基线推进至 `8b9fa6b` / `4418ac7e…`。

第 159 轮记录的「Java SDK `executeWithRetry` 丢弃后端错误消息」本轮**关闭**。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）。
2. **P1-2 导入端点把校验失败报成成功跳过**（第 160 轮记录，四处调用点，见 findings.md）。
3. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录）。
4. **AGENTS.md 幻影端点**（第 154 轮记录）——该文件被 gitignore。
5. **三 SDK 一致缺口**（Observation 9 字段、`projectPath`、`/api/projects` V18 字段）。
6. **Java Demo 的 `/observations/*` 无 E2E 覆盖**（第 159 轮记录）。
7. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
8. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围。
9. push 权限阻塞（`wubuku` 403）。
10. 并行巡检进程争写状态文件。
11. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Java SDK 完成（新循环第十四轮），下一方向 Go SDK；
文档审查轮换推进：用户指南完成（六十二轮），下一方向 架构文档。

---

## 第 162 轮 — 2026-10-02 17:55 — Go SDK + 架构文档

### 代码方向：Go SDK

**GO-3（P1，已修）`IsRetryable` 把无法识别的错误一律当作可重试。**

函数末尾是一句裸 `return true`，注释写着「Non-HTTP, non-sentinel errors are network/transport
errors — always retryable」。也就是说**凡是不认识的错误都报成瞬时错误**。对客户端真正
会产生的错误逐类实测：

| 错误 | 修复前 IsRetryable | 是否应当重试 |
|---|---|---|
| `ValidationError` | **true** | 否 |
| `context.Canceled` | **true** | 否 |
| `context.DeadlineExceeded` | **true** | 否（调用方已放弃） |
| BaseURL 无法解析 | **true** | 否 |
| `json.Marshal` 失败 | **true** | 否 |
| 响应超过 MaxResponseBytes | **true** | 否 |
| 连接被拒（真正瞬时） | true | 是 |

**四家 SDK 里只有 Go 会让调用方去重试一个校验失败。** Python 的 `is_retryable_error`
与 JS 的 `isRetryable` 都是**正向识别**瞬时错误、其余一律 false，而 Python 的 docstring
恰恰写着「Matches Go's IsRetryable(err) for cross-SDK parity」——parity 声明的方向反了。

修复：默认改为**不可重试**。`net.Error` 覆盖 `*url.Error`、`*net.OpError` 与
`context.DeadlineExceeded`；`io.ErrUnexpectedEOF` 覆盖响应中途断连。

**`io` 那一支不是可选的，且是探针测出来而非推理出来的**：我用 hijack 让一个承诺
Content-Length 的服务端立刻断开，得到的错误是裸的 `io.ErrUnexpectedEOF`，
**`errors.As(net.Error)` 为 false**。若只按 `net.Error` 判定，就会把一个真正瞬时的
失败悄悄改成不可重试——这正是「先核实再动手」救回一次真实回归。

**两个既有测试把缺陷钉死成了预期**：`TestIsRetryable_GenericError` 与
`TestIsRetryable_NetworkErrors` 都传 `errors.New("connection refused")` 并断言 true，
永远只能确认那个默认。已改用 `http.Client.Do` 真正返回的形状（对着一个真实被拒的连接
记录下完整因果链：`*fmt.wrapError -> *url.Error -> *net.OpError -> *os.SyscallError ->
syscall.Errno`），并覆盖 DNS、超时、deadline、响应截断。新增
`TestIsRetryable_PermanentClientErrors` 钉住新默认。

退回验证：A) 默认翻回 `return true` → 恰好 1 个测试失败；B) 删掉 `io` 分支 → 恰好 2 个失败；
C) 删掉显式 `ValidationError` 守卫 → **0 个失败**。C 的结果是诚实的：那个守卫并非承重，
因为新默认已经覆盖它。已在代码注释里写明「removing it leaves every test green」，
不夸大它的作用。

**顺带修掉一处我自己第 156 轮引入、且已提交的 gofmt 违规**：`gofmt -l` 报出
`client_impl.go`，是第 156 轮改 `MaxRetries` 注释破坏了结构体注释对齐——该轮报告写的
「gofmt 干净」是在改注释**之前**跑的，顺序错了。现 `gofmt -l` 无输出。

**跨方向（因我的改动而失实，故同批修正）**：Python `is_retryable_error` 的 docstring
逐字描述了 Go 的旧行为（「Go treats non-HTTP errors as always retryable」），我的修复会让它
变假，故一并更正。**仅注释**，Python 389 测试全绿。

Go SDK：核心 228 → **230**（计入子测试；+3 新增 −1 删除）、dto 61、eino 8、genkit 13、
langchaingo 12；gofmt 与 vet 干净；go-sdk-e2e **39/39**（Go Demo 起于 37779，本轮结束已停）。

### 文档方向：架构文档

**DOC-1（P1，已修）「Java 21+ Features Used」整段代码片段全部虚构。**

| 片段 | 实际情况 |
|---|---|
| `public record ObservationDto(...)` | **不存在**。真实 record 是 `ApiRequests.ToolUseRequest` 等 |
| `if (entity instanceof ObservationEntity o)` | **全仓库无此写法**。真实用法是 `o instanceof Pageable that`、`toolInputObj instanceof String s` |
| 文本块 `String prompt = """..."""` | 提示词是**外部资源** `src/main/resources/prompts/*.txt`；唯一在 Java 内拼装的 `SummaryGenerationService:108` 用的是字符串拼接加 `\n` |

三处都换成了真实代码并标注文件与行号。

**其中 `ObservationDto` 不只是虚构，还是个主动陷阱**：它把 `facts` / `concepts` 建模为
`List<String>`，而这**正是第 157–158 轮跨三个 SDK 修掉的那个形状**——照抄这段的开发者会
重新踩进同一个坑。已在两版都补上一条经核实的警告：四个 JSONB 列在 wire 上是 **JSON 编码的
字符串**（`facts: "[\"allergy\",\"peanut\"]"`），因为 `ObservationEntity` 的 getter 标注
`@JsonProperty` 返回 `String` 供 WebUI 的 `JSON.parse` 使用；这是**承重契约而非缺陷**。
同时在「语义搜索」下新增 Wire format 小节，让交叉引用成立（先核实过该小节原本不存在，
否则就是我自己造的死链）。

**顺带核实无误未改**：Java 21（`<java.version>21`）、Spring Boot 3.3.13、
`pgvector/pgvector:pg16`、Node/axios 依赖、MCP 传输表（`/sse` + `/mcp/message` 默认 SSE、
`/mcp` 为 STREAMABLE）与五个工具名（`search`/`timeline`/`get_observations`/`save_memory`/
`recent`）——全部与 `application.yml` 及 `mcp/` 源码一致。「Thin Proxy 性能需求」表标注为
Target（目标）而非实测值，不构成可证伪声明，未动。

结构核验：两版各 **49 个标题、层级序列逐位一致**、围栏平衡、**所有页内锚点均可解析**
（含本轮新增的两个）。校验脚本首轮报出 4 个死锚点，查证后确认是我脚本的假阳性
（GitHub 的 slug 不合并连续空格，`Proxy + Fat` → `proxy--fat`），修正校验器后归零。

### 验收

指纹 `4418ac7e…` → `e0617744…`（Go SDK 有代码改动）→ 完整验收：
`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**；
`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
全通过，基线推进至 `a2ddb13` / `e0617744…`。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）。
2. **P1-2 导入端点把校验失败报成成功跳过**（第 160 轮记录，四处调用点，见 findings.md）。
3. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录）。
4. **AGENTS.md 幻影端点**（第 154 轮记录）——该文件被 gitignore。
5. **三 SDK 一致缺口**（Observation 9 字段、`projectPath`、`/api/projects` V18 字段）。
6. **Java Demo 的 `/observations/*` 无 E2E 覆盖**（第 159 轮记录）。
7. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
8. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围。
9. push 权限阻塞（`wubuku` 403）。
10. 并行巡检进程争写状态文件。
11. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Go SDK 完成（新循环第十五轮），下一方向 Python SDK；
文档审查轮换推进：架构文档完成（六十三轮），下一方向 API 文档。

---
