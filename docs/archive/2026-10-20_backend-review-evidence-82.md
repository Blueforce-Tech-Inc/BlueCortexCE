# Backend Review Evidence 82 — 第 381 轮本轮新条目的整条正文（第 381 轮迁出）

> **归档规则**：承 `-62` ~ `-81` 的体例，迁出**本轮新写**的条目全文。
> 本轮 findings 尚有 **3038** 字节余量（上一轮未再压缩），
> 故本轮**未再压缩**。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：158 行 / 12905 字节。**
>
> 本批迁出 **P2-147 / P2-148** 两条新条目，并附 **P2-146 的 JS 侧结转**记录。
>
> **本轮代码方向为 JS/TS SDK，文档方向为 SDK README（doc round 280）。**
>
> 本轮是上一轮的**同型续作**：上一轮在 Python 侧发现「文档描述的是一个已被客户端校验
> 淘汰掉的机制」，并把 Go / JS / Java 三家记为结转。本轮轮到 JS 侧 ⇒ 同一句话在
> JS 的双语 README 里**逐字同型**存在，本轮把它修掉；顺带查出**第二处**同型的
> 「无日期测量断言」，以及**一处测试覆盖缺口**（`tool_name` 是同族里唯一没被钉住的那个）。
---
### P2-147: JS SDK 的 `recordObservation` 必填校验里，`tool_name` 是**同族里唯一没有被任何测试钉住**的那一个 —— 且没有任何用例钉住「校验发生在请求发出**之前**」

- **Problem**：`js-sdk/cortex-mem-js/src/client.ts` 的 `recordObservation` 做**三项**必填校验
  （`client.ts:179-181`：`session_id` / `cwd` / `tool_name`），而测试套件里
  `describe('recordObservation')` 只钉住了**其中两项**：
  | 必填字段 | 实现 | 测试 |
  |---|---|---|
  | `session_id` | `client.ts:179` | ✅ 空值 + 纯空白（`client.test.ts:244/250`） |
  | `cwd` | `client.ts:180` | ✅ 空值（`client.test.ts:256`） |
  | **`tool_name`** | `client.ts:181` | ❌ **一个用例都没有** |
  这正是「**缺口即嫌疑名单**」的形状：同族对象**逐个**被钉住，**唯独漏掉的那一个**
  就是嫌疑对象。而它恰恰是三个里**最要紧**的一个 ——
  `recordObservation` 是 fire-and-forget，后端对空 `tool_name` 的 400 会被**吞掉**。
  **第二个缺口**：现有的四个校验用例只断言「抛错」，**没有任何用例断言「请求没发出去」**。
  两者可以同时成立而互不相干 —— 校验若被挪到请求**之后**，
  抛错照样发生，而观测**已经被送出并吞掉**了。
  （这与上一轮 Python 侧的发现**完全同型**：Python 的既有测试
  `test_record_observation_empty_tool_name_raises` 也只钉住了异常。）
- **Scope / Evidence**：
  - **变异测试（证明新用例能失败、且旧用例钉不住顺序）**：
    把 `client.ts` 里 `recordObservation` 的 `validateRequired('tool_name', …)`
    **挪到 `doFireAndForget` 之后**，再跑 `recordObservation` 相关用例：
    ```
    FAIL  CortexMemClient > recordObservation > should validate before sending the request
    AssertionError: expected "spy" to not be called at all, but actually been called 1 times
    Tests  1 failed | 6 passed | 241 skipped (248)
    ```
    ⇒ **新用例失败**（`fetchMock` 被调用了 1 次），
    而**其余 6 个用例（含同批新增的「空 tool_name 抛错」）全部通过**
    ⇒ **只断言异常的用例，钉不住顺序**。
    变异后按 sha256 **逐字节还原**（`194520e8…` 前后一致、`git diff` 为空）。
  - **修法（只加测试，不动实现）**：
    ① 补 `should throw on missing tool_name`（填上同族里空着的那一格）；
    ② 补 `should validate before sending the request`：
    断言 `ValidationError` **之外**，再断言 `expect(fetchMock).not.toHaveBeenCalled()` ——
    测试文件的 `beforeEach` 已经给 `client` 配好了 `fetchMock`
    （`client.test.ts:150-156`），因此这条断言不需要额外的脚手架。
  - **本轮其余核实为真（JS SDK 代码，逐条）**：
    - **264 → 266 个测试**：README 首屏的分解「246 client + 5 truncated-body +
      8 http-server example + 5 http-server error contract」在修前**逐项精确相符**
      （`npm test` 实测 **264**）；本轮加 2 个用例后为 **248 + 5 + 8 + 5 = 266**。
    - **选项下限**与 README 的选项表逐条相符（`client-options.ts:88-90`：
      `timeout` ≥ **100ms**、`maxRetries` ≥ **1**、`retryBackoff` ≥ **100ms**）。
    - **默认 logger 是 no-op**（`client-options.ts:91`）⇒ README 首屏
      「capture errors … reach your logs only if you supply a `logger`」为真。
    - **14 个错误判定函数**逐个为真（`errors.ts`：`isClientError` = `400≤s<500`、
      `isServerError` = `s≥500`、`isRetryable` = 429/502/503/504 **或** `TypeError`
      **或** `name === 'AbortError'`），且对 `ValidationError` 返回 `false`
      ⇒ 不会把客户端校验错误当成可重试。
    - **`limit` 的语义三家一致**：JS（`client.ts:487`）、Python、Go **都**是
      「`> 0` 才发送、为 0 则省略走后端默认、负数抛 `ValidationError`」；
      后端 `ExtractionController.java:117-124` 把 limit 钳制在 **1..100**、默认 **10**
      ⇒ 显式发 `limit=0` 会被钳成 **1**（不是「用默认」），三家都靠「不发」来避开它。
    - **search 的查询参数与后端逐字相符**（`project`/`query`/`type`/`concept`/`source`/
      `limit`/`offset`/`orderBy`，见 `ViewerController.java:290-306`；
      `orderBy` 后端**同时接受** `created_at_epoch` 与 `createdAtEpoch`，与
      `dto/search.ts:25` 的注释一致）。
    - **`ObservationUpdate` 的 8 个字段**里 `content` 与 `narrative` 是**别名**
      （`dto/observation.ts:66-98`）⇒ 后端可清空的列是 **7** 个
      （title / content·narrative / subtitle / source / facts / concepts / extractedData），
      与后端 PATCH 的 `@Parameter` 宣告**逐项相符**
      ⇒ README 的「seven clearable fields」为真。
    - **10 MiB 响应上限**的双重校验成立：先看 `Content-Length`（`client.ts:664-669`），
      再按 **UTF-8 字节数**复核（`client.ts:704`）；快路径 `text.length * 3 > maxSize`
      作为提前退出（UTF-8 每码元最多 3 字节）**成立**。
    - `buildURL` 用**字符串拼接**而非 `new URL(path, base)`，以保住 `baseURL` 的路径前缀
      （`client.ts:585-617`）⇒ 与 Go / Python / Java 三家一致，**为真**。
- **Status**：✅ **已修（381 轮，JS 侧）—— 只加测试，实现一行未改**：
  新增 **2** 个用例（`should throw on missing tool_name` /
  `should validate before sending the request`），
  **变异测试**证明它们**能失败**，且证明既有用例**钉不住顺序**；
  变异后按 sha256 **逐字节还原**。全文逐字见
  [`…-82`](../archive/2026-10-20_backend-review-evidence-82.md)。

### P2-148: JS SDK 双语 README 的「16 个不同项目」是一条**无日期**的测量断言 —— 而活体实测（2026-10-21）是 **15**；同一实例、同一端点、同一页大小，Python README 却写「15」且**带日期**

- **Problem**：JS SDK 的双语 README 都在讲「`listObservations` 的项目过滤会**放宽**而非清空」，
  并举了一个数字作证据：
  | 文档 | 原文 | 日期 |
  |---|---|---|
  | JS `README.md`（EN） | "Verified live against a populated backend: `GET /api/observations` with no `project` returned **16** distinct projects inside a single 100-item page." | **无** ❌ |
  | JS `README-zh-CN.md`（ZH） | 「活体实测：省略 `project` 的 `GET /api/observations` 在一个 100 条的单页里返回了 **16 个不同项目**的数据。」 | **无** ❌ |
  | Python `README.md`（EN） | "Verified live **on 2026-10-20** against a populated backend: … returned **15** distinct projects … That count grows with the database, so it is a dated measurement rather than a live value" | **有** ✅ |
  | Python `README-zh-CN.md`（ZH） | 「2026-10-20 活体实测：…… 返回了 **15 个不同项目**的数据。该数字随数据库的增长而变，故它是**带日期的实测值**而非实时值」 | **有** ✅ |
  ⇒ **两处问题**：① **无日期的测量断言** —— 本项目的体例是「带日期的快照不算失实陈述，
  **无日期**的现在时断言算」；JS 那句既无日期、又没写「该数字随数据库增长」这一限定；
  ② **数字与活体不符** —— 本轮活体实测（**2026-10-21**）：
  ```
  GET http://127.0.0.1:37777/api/observations?limit=100
  items = 100   distinct projects = 15   hasMore = True
  ```
  即 **15**，不是 16（同一个实例、同一个端点、同一个 100 条单页）。
  差异的成因是**数据库在长**（验收测试一直在写行），所以这个数字**本来就不该是断言** ——
  它必须是**带日期的实测值**。Python 的两版已经这么写了，JS 的两版没有。
- **Scope / Evidence**：
  - **活体实测**：见上（2026-10-21，37777 常驻实例，`items = 100`、
    `distinct projects = 15`）。
  - **阳性对照**：同一端点**带**字面量 `?project=` 仍返回空（`IS NULL` 而非空串），
    与 README 早已写明的那句一致 ⇒ 本轮改的只是**数字与日期**，不是这个行为。
  - **修法**（只改文档，零可执行行）：JS 双语 README 的该段改成与 Python 两版**同体例**：
    加上日期 **2026-10-21**、把数字改成实测的 **15**、并补上
    「该数字随数据库的增长而变，故它是**带日期的实测值**而非实时值 —— 要点是**跨项目的广度**，
    而不是确切数字」。
    **Python 的两版不动** —— 它们带日期、当时为真，按「带日期的快照不算失实陈述」不属失实。
- **Status**：✅ **已修（381 轮，JS 侧，零可执行行）** —— JS 双语 README 的该段改为
  带日期的实测值（2026-10-21 / **15**）+「随数据库增长」的限定，与 Python 两版同体例。
  全文逐字见 [`…-82`](../archive/2026-10-20_backend-review-evidence-82.md)。

### P2-146 的 JS 侧结转（第 381 轮）：同一句失实陈述在 JS 的双语 README 里逐字同型存在，本轮修掉

- **本段是 P2-146（380 轮立条）的跨家结转，不是新条目。** P2-146 原文逐字见
  [`…-81`](../archive/2026-10-20_backend-review-evidence-81.md)。
- **背景**：P2-146 记的是「四家 SDK 的 README（**4 家 × 2 语 = 8 份**）都用**现在时**写
  「空 `tool_name` 会被后端以 400 拒绝、SDK 记一条日志后吞掉」——
  而**客户端校验早在请求发出之前就拦下了它**，实测请求数 **0**」。
  **380 轮只实施了 Python 侧**（双语 README 恢复反事实措辞 + 2 个回归测试 + 计数 453→455），
  原文明写「**Go / JS / Java 三家同型结转**」。
  **本轮是 JS/TS SDK + SDK README 方向 ⇒ 结转 JS 侧**。
- **JS 侧核实**：JS 的三个捕获方法确实**都**在 `doFireAndForget` **之前**做客户端校验
  （`client.ts:179-181` / `193-194` / `206-208`），`validateRequired` 连**纯空白**都拒
  （`client.ts:579-583`）⇒ JS 侧与 Python 侧**同型**。
  JS 的 README EN 第 273-279 行、ZH 第 256-260 行的同一句话**逐字同型**存在。
- **本轮已做（纯文档，零可执行行）**：
  ① `README.md`（EN）与 `README-zh-CN.md`（ZH）的该段**恢复反事实措辞**
  （`Left unchecked, an empty tool_name would come back as …` /
  「若不做检查，`tool_name` 为空时后端会返回……」），
  并把**现在真正发生的事**写清楚：「Both are caught client-side instead, so neither call
  reaches the wire: the SDK throws a `ValidationError` and sends no request at all.」/
  「这两个空值现在都由客户端拦下，请求根本不会发出：SDK 抛 `ValidationError`，不发送任何请求。」；
  ② 两版 README 首屏的测试数 **264 → 266**（246 → **248** client，日期 2026-10-20 → **2026-10-21**）；
  ③ 另加 P2-147 的 **2** 个回归测试钉住「校验发生在请求之前」。
  **Go / Java 两家仍结转**（属各自的 SDK README 方向）。
- **验收**：JS SDK **266/266** 全绿、`tsc --noEmit` 干净、`npm run build` 成功；
  回归 **45/0/1** 与 Phase3 **25/0/0** 均 exit 0，跑在**含当前后端代码的新鲜实例 37790**
  （jar 01:06 构建、pid 82326；**jar 比全部后端源文件都新**，且含第 378 轮的
  `ModeController$ActiveModeSwitchRequest`）。指纹 `602d4994…` → `f810e889…`
  （2223 条记录数不变）。
  全文逐字见 [`…-82`](../archive/2026-10-20_backend-review-evidence-82.md)。
