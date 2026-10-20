# Backend Review Evidence 81 — 第 380 轮本轮新条目的整条正文（第 380 轮迁出）

> **归档规则**：承 `-62` ~ `-80` 的体例，迁出**本轮新写**的条目全文。
> 本轮 findings 尚有 **3776** 字节余量（上一轮用两处零信息损失压缩腾出），
> 故本轮**未再压缩**。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：137 行 / 12308 字节。**
>
> 本批迁出 **P2-146** 一条新条目，并附 **P2-145 / P2-128 之外的跨家结转说明**。
>
> **本轮代码方向为 Python SDK，文档方向为 SDK README（doc round 279）。**
>
> 本轮的形状与前两轮**同源**：前两轮修的都是「文档说的不是实现真正做的事」，
> 而本轮更朴素 —— **文档说的是一个早已不存在的机制**，且**四家 SDK、两种语言、八份 README**
> 全都这么说。修法也最朴素：把反事实的措辞**恢复回去**，并写明现在真正发生的事。
>
> **一句话的形状**：四家 README 都在解释「为什么客户端要校验这些必填参数」，
> 理由写成了「不校验的话，后端会 400，而 fire-and-forget 会把这个 400 吞掉」。
> 这句话的**理由是对的**，但它用的是**现在时**，读起来像在描述**当前行为** ——
> 而当前行为是**客户端在请求发出之前就抛 `ValidationError`**，请求数 **0**。
> **写入即假，不是事后漂移**：客户端校验（`94f3beb`，2026-10-02）**早于**
> README 段落（`6902968`，2026-10-03）**一天**，且这两个 commit **出自同一条 PR 线**。
---
### P2-146: 四家 SDK 的 README 都写「空 `tool_name` 会被后端以 400 拒绝、SDK 记一条日志后吞掉」—— 而客户端校验早在请求发出**之前**就拦下了它，请求数实测为 **0**

- **Problem**：`python-sdk/cortex-mem-python/README.md`（EN 第 297-303 行）与 `README-zh-CN.md`
  的「这些检查不是装饰」段落，用**现在时**描述了一段**早已不存在**的机制：
  | 文档 | 原文（节选） |
  |------|--------------|
  | EN | "an empty `tool_name` comes back as `400 Missing required field: tool_name`, the SDK logs it and returns `None`, and **the caller concludes** the observation was captured when the server had just rejected it" |
  | ZH | 「`tool_name` 为空时后端返回 `400 Missing required field: tool_name`，SDK 记一条日志后返回 `None`，调用方于是认为观测已记录，而服务器刚刚拒绝了它」 |
  两句话都断言**同一件事**：① 请求发到了后端；② 后端回了 400；
  ③ SDK 把这个 400 吞掉并返回 `None`；④ 调用方因此误以为已记录。
  **实测只有 ③④ 成立，而 ①② 不成立**：`client.py:307-308` 的校验在
  `_fire_and_forget` **之前**抛 `ValidationError`，**请求根本没发出去**（实测请求数 **0**）。
  同一段里第二句「`project_path` 为空更隐蔽，因为后端**接受**它」同理 ——
  `project_path` 也由客户端校验拦下（`client.py:305-306`），同样**零请求**。
  **这不是事后漂移，是写入即假**：客户端校验由 `94f3beb`（"fix(python-sdk): validate the
  required fields the other three SDKs already check"，2026-10-02）加入，
  README 段落由 `6902968`（"docs: document the required-argument contract in all four SDK
  READMEs"，2026-10-03）写入 ⇒ **校验早了一天**，两个 commit 同属一次「补必填参数契约」的工作。
  **而且同一份 README 自己就自相矛盾**：上方第 272-291 行的必填参数表**正确地**把
  `tool_name` / `project_path` 列为必填，下方第 297 行却描述它们被发到了后端。
  **跨家同型**：同一句话在 **4 家 SDK × 2 种语言 = 8 份 README** 里**逐字同型**存在
  （锚点 `Missing required field: tool_name`，每份命中 **1** 处）：
  | 家 | 客户端校验实现 | README 里的断言 |
  |---|---|---|
  | Python | `client.py:307` `raise ValidationError("tool_name is required")` | ❌ 同型失实 |
  | Go | `client_methods.go:51` `&ValidationError{Field: "toolName", ...}`（`RecordObservation` **在发请求前**返回） | ❌ 同型失实 |
  | JS | `client.ts:181` `this.validateRequired('tool_name', req.tool_name)` | ❌ 同型失实 |
  | Java | `CortexMemClientImpl.java:127` `requireNonBlank(request.toolName(), "toolName")` | ❌ 同型失实 |
  ⇒ **四家 SDK 的行为是对的（四家都做客户端校验），四家 README 的描述是错的**。
  **反事实前提本身为真**（这正是那段文字的**用意**）：后端
  `IngestionController.java:122-127` 对空 `session_id` / 空 `tool_name` 确实回
  `400 Missing required field: ...`，而对 `cwd` **不校验** ⇒ 空 cwd 会被**接受并入队**。
  所以这段文字的**理由正确、时态错误**。
- **Scope / Evidence**：
  - **受控实验（假传输层，可数请求数）** —— 把这句话拆成两个可分别测量的半句：
    | 编号 | 读数 | 结果 |
    |---|---|---|
    | A | 空 `tool_name` ⇒ 抛 `ValidationError`；**请求数** | `ValidationError` / **0 次** ✅ |
    | B | **阳性对照**：合法 `tool_name` ⇒ 传输层收到几次请求 | **恰好 1 次** POST `/api/ingest/tool-use` ✅ |
    | C | 空 `project_path` ⇒ 抛 `ValidationError`；请求数 | `ValidationError` / **0 次** ✅ |
    | D | 后端真的回 **400** ⇒ 被吞掉吗；记日志吗；重试几次 | 返回 `None` / **有**一条
    `RecordObservation failed with non-retryable error, giving up: ... (attempt 1/3)` / **1 次**（400 不可重试）✅ |
    | E | **阳性对照**：后端回 **503** ⇒ 重试几次 | **3 次**（`max_retries`）后吞掉 ✅ |
    结论：**H1「空 `tool_name` 会到后端并回来 400」为假**（请求数 0）；
    **H2「SDK 记日志并吞掉」为真** —— 但只在后端**真的拒绝**时成立，
    而空 `tool_name` **永远到不了那一步**。A/B 对照说明「0 次」不是探针瞎了
    （B 证明探针**看得见**请求）；D/E 对照说明「1 次 vs 3 次」区分得开。
  - **变异测试（证明新测试能失败，且旧测试钉不住顺序）**：
    把 `tool_name` 的校验**移到 `_fire_and_forget` 之后** ⇒
    **新增**的 `test_record_observation_empty_tool_name_sends_no_request` **失败**
    （`assert 1 == 0`），而**既有的** `test_record_observation_empty_tool_name_raises`
    **仍然通过** ⇒ 旧测试只钉住了「抛异常」，**钉不住「异常发生在请求之前」**。
    这正是本轮新增测试的**唯一**价值所在。变异后按 sha256 **逐字节还原**
    （`9879f8a1…` 前后一致、`git diff` 为空）。
  - **探针自身的两处错（先识别，再采信）**：
    ① 第一次跨家扫描用的正则**只吃单行形式**，因此**漏掉 2 份** README
    （Python EN 与 JS EN 的那句话**在源文件里跨行**）⇒ 改用**单行锚点**
    `Missing required field: tool_name` 重扫，才得到完整的 **8 份**。
    ② 第一次捕获日志时把 handler 挂在**手打**的 logger 名 `cortex_mem.client` 上，
    而 SDK 实际用的是 **`cortex_mem`** ⇒ 捕获到 **0 条**记录，
    **看上去像「SDK 不记日志」这个产品缺陷**；实际控制台**明明打了**
    `RecordObservation failed with non-retryable error, giving up: ...`。
    改成挂在 **root logger** 上（与名字无关）后即捕获到该记录 ⇒
    **这是探针的错，不是产品的错**（记在此处以免下次把探针的错当成发现）。
  - **本轮其余核实为真（Python SDK 代码 + README，逐条）**：
    - **25 个 API 方法**：`client.py` 共 **26** 个公开 `def`，减去 `close`（生命周期方法）= **25**；
      README 的**方法表逐行数**与之一致（Session 2 + Capture 3 + Retrieval 6 + Management 5 +
      Health/Extraction/Version 9 = 25）。
    - **测试数 453 → 455**：README 首屏的「453 unit tests（226 client + 140 DTO + 87 demo）」
      用的是 **collected 项**口径；本轮实测 `def test_` 声明数是 **221 / 140 / 87**，
      而 `pytest --collect-only` 是 **226 / 140 / 87 = 453** ⇒ 与 README **逐项精确相符**
      （与 Go SDK 那轮「顶层 315 vs 含子测试 372」是**同一种口径陷阱**，此处踩中的是**同一条**）。
      本轮新增 **2** 个测试 ⇒ 计数变成 **455 = 228 + 140 + 87**，**两版 README 的首屏已同步**。
    - **`error.py` 的 15 个判定函数**逐个核对为真（`is_client_error` = `400<=s<500`、
      `is_server_error` = `s>=500`、`is_retryable` = 429/502/503/504、`is_retryable_error`
      对未识别异常 fail-closed）⇒ **Go 的哨兵回退缺陷（P2-134）在 Python 无对应物**
      （Python 用**异常类**、无哨兵值）。
    - **必填参数表 18 行**与代码里的 **18 处** `raise ValidationError` **逐条相符**
      （含 `get_observations_by_ids` 的「非空 / 至多 100 / 无空元素」与
      `get_extraction_history` 的「`limit` 不得为负」）。
    - **三个下限**与 README 的选项表逐条相符（`timeout` ≥ **0.1**、`max_retries` ≥ **1**、
      `retry_backoff` ≥ **0.1**，实现见 `client.py:91-93`）。
    - **重试范围**：`_fire_and_forget` **只被 3 个方法调用**
      （`RecordObservation` / `RecordSessionEnd` / `RecordUserPrompt`，`client.py:324/353/389`）
      ⇒ README 的「重试只作用于 fire-and-forget 捕获」为真；README 顺带断言的
      「Java SDK 重试 25 个方法中的 **10** 个」**本轮也核实为真** ——
      `CortexMemClientImpl` 里 `executeWithRetry*` 的调用点**恰好 10 处**
      （5 处静默吞错 + 3 处传播 + 2 个抽取读），与 Java README 的分解**逐项相符**。
    - **`get_observation` 不是 `GET /api/memory/observations/{id}`**：活体 OpenAPI 该路径
      **只有 DELETE / PATCH**（GET 会 405），而 Python 的 `get_observation` 走的是
      `POST /api/observations/batch`（`get_observations_by_ids`）⇒ **无缺陷**，不是遗漏。
- **Status**：✅ **已修（380 轮，Python 侧）—— 文档 + 2 个回归测试**：
  ① EN / ZH 双语 README 的该段落**恢复反事实措辞**（`Left unchecked, … would come back as …`
  / 「若不做检查，……后端会返回……」），并把**现在真正发生的事**写清楚：
  「两个空值现在都由客户端拦下，请求根本不会发出：SDK 抛 `ValidationError`，不发送任何请求」；
  措辞与 `client.py` 里 `record_observation` **已有的 docstring**（它一直用的是
  「a 400 that fire-and-forget **would** swallow」）**对齐**；
  ② 两版 README 首屏的测试数 **453 → 455**（226 → **228** client），否则改完立刻又是新的失实陈述；
  ③ 新增 **2** 个回归测试钉住「校验发生在请求发出**之前**」
  （`test_record_observation_empty_tool_name_sends_no_request` /
  `test_record_observation_empty_project_path_sends_no_request`），
  **变异测试证明它们能失败**（把校验挪到请求之后 ⇒ 新测试失败、旧测试仍通过）；
  ④ 既有测试 `test_record_observation_empty_tool_name_raises` 的 docstring 里
  「the backend **answers**… and fire-and-forget **swallows**」也一并改为反事实措辞
  （`would answer` / `would swallow`），使**同一文件内**不再有两种时态并存。
  **Go / JS / Java 三家同型结转**（共 6 份 README，属各家的 SDK README 方向，本轮未实施）。
  **验收**：Python SDK **455/455** 全绿；回归 **45/0/1** 与 Phase3 **25/0/0** 均 exit 0，
  跑在**含当前后端代码的新鲜实例 37790**（jar 00:24 构建、pid 37552；37777 的常驻实例
  起于 2026-10-20 02:17、**仍加载旧 jar**，故不复用）。指纹 `581d954a…` → `602d4994…`
  （2223 条记录数不变，改动落在已有的 `.py` 与 `.md` 文件内）。
  全文逐字见 [`…-81`](../archive/2026-10-20_backend-review-evidence-81.md)。
