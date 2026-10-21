# 后端审查证据归档 -93（2026-10-21）

> 第 392 轮（Python SDK + SDK README，doc round 291）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1243 行 / 149778 字节**）。
> 本文档体量：**157 行 / 15078 字节**。

## 本轮新写的两条（正文逐字保留在本归档）

### P2-179: Python SDK 的**客户端校验守卫用例未挂 mock**，而测试的 `BASE` 指向**常驻后端** ⇒ 守卫一旦失效，**8 个捕获类**用例会真的向**常驻数据库写行**，且该写入**不被任何断言覆盖**

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-93`][a51]（13 个未 mock 用例的逐处定位、socket 间谍受控实验、变异三次各自命中的面、阴性对照 0 红、密封装饰器的双路径诊断）
- **Status**: ✅ **已修（392 轮）** —— 新增 `tests/test_error_contract.py`（**11** 例，逐字钉住完整消息、精确类型、`field`、不发请求、README 一致性）+ 把 13 个守卫用例改为**密封**；SDK 主源码零改动。

- **Problem**：Python SDK 的测试里，`BASE = "http://localhost:37777"` —— 那是**常驻后端**。
  而 **13** 个「客户端校验守卫」用例（`test_client.py`）**没有**挂 `@responses.activate`，
  也没有注册任何 mock ⇒ `responses` 库在未激活时**完全不拦截**，请求会真的走 `requests` 发出去。
  这 13 个用例断言的恰恰是「SDK 在发请求**之前**就拒绝」—— 断言本身是对的，
  但**一旦守卫回归**，后果分两类：
  - **8 个捕获类**（`record_observation` × 3 / `record_session_end` × 2 / `record_user_prompt` × 3）：
    守卫消失 ⇒ 请求真的 POST 到常驻后端 ⇒ **向常驻数据库写入一行捕获记录**。
    而 `_fire_and_forget` 的结尾是 `# Fire-and-forget: swallow all errors` ⇒
    **错误被吞掉，没有任何断言覆盖这次写入**。测试会红（`pytest.raises` 落空），
    但**那次写库已经发生了，且不在任何断言的视野里**。
  - **5 个非捕获类**（`update_observation` / `search` / `retrieve_experiences` / `build_icl_prompt` /
    `get_extraction_history`）：真实的读写请求，只是没有写库。
- **受控实验（不是读码推断）**：用 `socket.socket.connect` 间谍替换真实连接，
  在**不激活** `responses` 的条件下调用 `update_observation`：
  ```
  间谍捕获到的真实连接目标: [('::1', 37777, 0, 0), ('127.0.0.1', 37777)]
  ```
  ⇒ 请求**未被拦截**，确实要打到常驻后端（先试 IPv6 `::1`，再回退 IPv4）。
  这与 Go SDK 第 387 轮在 `newValidationClient` 的注释里记下的是**同一族陷阱**：
  「Pointing those tests at the default client (http://127.0.0.1:37777, the resident backend)
  made that regression a live write into the resident database rather than a test failure.」
- **同时发现的第二个缺口：空更新的错误契约零钉住。**
  - `test_update_with_no_fields_raises_validation_error` 断言
    `assert "at least one field" in exc_info.value.message` —— **子串**匹配，
    ⇒ 前缀 `cortex-ce: validation error on update: ` **从未被断言过**，改了它没有一个测试会红。
  - `test_update_observation_extracted_data_empty_raises` 用
    `pytest.raises(..., match="at least one field")` —— 那是**正则搜索**，不是相等比较，同族的弱点。
  - 而 Python README 把这句消息当**代码块**发布 ⇒ **文档发布的字符串与运行时返回的字符串之间没有任何绑定**。
- **修法（已实施，SDK 主源码零改动）**：
  1. 新增 `python-sdk/cortex-mem-python/tests/test_error_contract.py`（**11** 个用例）：
     - 逐字断言空更新的**完整消息**（`cortex-ce: validation error on update: at least one field must be provided for update`），
       在**三种调用形态**下都是同一个：纯空（`update_observation("o1")`）、
       数据类的 `extracted_data={}`、数据类的 `content=None`；
     - 断言**精确类型** `ValidationError`、`field == "update"`、`is_validation_error(err) is True`；
     - 断言**必填参数那条是另一个错**（`cortex-ce: validation error on observation_id: observation_id is required`），
       且 `field == "observation_id"` —— 两者都抛 `ValidationError`，**只有消息能把它们分开**；
     - 断言 content/narrative 冲突的**另一条**完整消息与 `field == "content|narrative"`；
     - 每个用例都断言**不发请求**（`responses.calls` 为空）；
     - 4 个 README 一致性用例（见 P2-180）。
  2. 把 13 个守卫用例改为**密封**：新增装饰器 `hermetic_client_guard`
     （`tests/test_client.py`），它在 `@responses.activate` 下运行用例，并在**两条路径**上
     断言 `len(responses.calls) == 0` —— 用例**正常返回**时断言一次，
     用例**抛异常**时（`pytest.raises` 落空）也断言一次，并把请求数写进失败信息。
     ⇒ 守卫回归时，失败信息会直接说「the mock recorded 1 request(s)」。
- **「不发请求」这条断言本身必须能失败**：`TestNoRequestAssertionIsFalsifiable` 两个用例证明它能 ——
  一个用 socket 间谍证明**未注册的请求不会离开进程**（`responses` 真的拦截：
  `Connection refused by Responses - the call doesn't match any registered mock`），
  一个证明 `responses.calls` 的计数**确实能大于 0**。
- **变异测试（单变量，每次改完按 sha256 逐字节还原）**：
  | 变异 | 改动 | 命中的测试 |
  |------|------|------------|
  | A | `cortex_mem/error.py` 的运行时前缀改为 `… validation error CHANGED on {field}: ` | **6** 个红：5 个精确消息 + 1 个 README 跟随绑定 |
  | B | `cortex_mem/client.py` 的**空更新守卫**抽掉（`raise` → `pass`） | **7** 个红（3 个既有用例 + 4 个新用例），且请求被 mock 拦截为 `PATCH http://localhost:37777/api/memory/observations/o1` |
  | C | `cortex_mem/client.py` 的**捕获类 `tool_name` 守卫**抽掉 | **2** 个红，失败信息为「the mock recorded 1 request(s)」，被拦截的请求是 `POST http://localhost:37777/api/ingest/tool-use` |
  | 阴性对照 | `cortex_mem/client.py` 只加一行注释（行为零变化） | **0 红**（471 passed） |
  ⇒ 被变异的文件（`cortex_mem/error.py` / `cortex_mem/client.py`）按 sha256 **逐字节还原**。
- **⭐ 本轮自己踩了一次「常量当事实」的坑**：第一版的 README 一致性用例把文档与
  **常量** `EMPTY_UPDATE_MESSAGE` 比对 ⇒ 变异 A 只让 5 个精确消息用例变红，
  **README 用例没红**（文档仍含旧串 ⇒ 仍"通过"）⇒ 改成了从**活调用**取运行时值再比对，
  变异 A 随即命中 **6** 个。这与第 391 轮 Go SDK 侧记下的同一条教训是同一件事。
- **后果**：Python SDK 单测 **460 → 471**（client 231 + DTO 140 + demo 89 + 错误契约 11），
  9 个 module 之外的两个 `examples/` 无测试文件。

### P2-180: Python README 双语 **2 处**把空更新消息**截断引用**（缺 ` for update`），**2 处**宣称另三家「消息完全相同」—— 四家的**前缀**各不相同

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-93`][a51]（4 处逐处坐标与原文、四家对拍、修后片段）
- **Status**: ✅ **已修（392 轮）** —— 双语 4 处改准并补四家对拍表；「另三家消息相同」是 **P2-163** 的 Python 侧结转，一并修。

- **Problem**：Python README 双语共 **4** 处失实，逐处如下（改前原文）：

  | # | 位置 | 失实点 |
  |---|------|--------|
  | 1 | `README.md:254-255` | 「The same rule and message apply in the Go, Java and JS SDKs」——**消息**并不同（Go 用自己的前缀，Java 完全没有前缀） |
  | 2 | `README.md:119` | 括注里只引了 `at least one field must be provided`——**截断了消息**（缺 ` for update`），照抄这段的人匹配不上 |
  | 3 | `README-zh-CN.md:244` | 「Go、Java、JS 三家规则与消息完全相同」——同 #1 |
  | 4 | `README-zh-CN.md:118` | 同 #2 的中文版 |

  ⇒ #1 / #3 是 **P2-163**（四家 README 的「空更新错误消息完全相同」）的 **Python 侧结转**；
  #2 / #4 是**新发现**的一类失实：**文档引用的不是运行时返回的那个字符串**。
- **四家对拍（全部取自源码，不是从文档转述）**：

  | SDK | 错误类型 | 完整消息 |
  |-----|----------|----------|
  | Python | `ValidationError` | `cortex-ce: validation error on update: at least one field must be provided for update` |
  | JS | `ValidationError` | `cortex-ce: validation error on update: at least one field must be provided for update` |
  | Go | `*dto.ObservationUpdateValidationError` | `cortex-ce: ObservationUpdate validation error: at least one field must be provided for update` |
  | Java | `IllegalArgumentException` | `at least one field must be provided for update`（**无前缀**） |

  ⇒ **规则**四家相同（空更新一律在客户端被拒、不发请求）；**类型 / 前缀 / 消息主体**是三层，必须分开陈述。
- **修法（已实施，纯文档、零可执行行）**：双语两处段落点名正确类型、保留 Python 的完整消息代码块、
  **新增四家对拍表**、并加一段说明「Python 这条与必填参数那个不是同一个类型」的对应关系；
  两处括注改为引用**完整消息**。
- **⭐ 按断言清扫**：JS EN/ZH 早已有这张四家对拍表（`js-sdk/cortex-mem-js/README.md:222-233`）；
  Go EN/ZH 已于第 391 轮补上；**Python EN/ZH 是本轮补的最后一家**；
  Java 侧两处仍结转（不在本轮方向内）。
- **新增的文档护栏（4 个 README 一致性用例）**：
  1. `test_readmes_publish_the_exact_runtime_message` —— 把文档发布的字符串与
     **运行时实际返回的字符串**比对（**从活调用取值**，不是与常量比对 ⇒ 运行时消息一改测试立刻红）。
  2. `test_every_published_body_carries_a_registered_prefix` —— 扫**词干**
     `at least one field must be provided`（不是完整 body ⇒ **截断引用也会被抓住**），
     要求每一处都带**已登记的前缀**（Python/JS 的、Go 的），裸消息只允许出现在点名 Java 的行上。
  3. `test_readmes_name_the_per_sdk_error_types` —— 双语各点名四家的类型与 Go 的前缀。
  4. `test_readmes_do_not_claim_cross_sdk_sameness` —— 两条失实断言**不得回潮**（中英各一个字符串）。

## 迁移段：P2-115 的两段带日期实测

> 本段原为 **P2-115**（⏸ 待决条目）正文里的两段**带日期的证据**。按 **C8**
> （⏸ 待决条目的证据段整体迁入归档，工作文件侧留一行 `- **Evidence**` 指针）
> 迁入本归档；**标题与 `- **Status**` 一律留在工作文件**，因为那是决策本身。
> 下面的正文**逐字保留**。

- 第 368 轮新证据：实测按现行规则已无法再压缩（迁出待决条目 42 段后字节反升 +1210，42 段里 41 段本身是指针行，该次压缩已精确反推）⇒ 本条阈值上调已成下一轮写入的前置条件（余量仅 33 字节）。详见 [scope-13](../archive/2026-10-20_backend-review-scope-evidence-13.md)。
- 第 370 轮的压缩手段现状：本轮用掉三种零信息损失的手法，各有边界 —— ①删已结案 Status 尾部与头部重复的「（第 N 轮）」（28 条，省 479B，已用尽）；②按第 293 轮体例把归档指针的显示名缩短为 `…-NN.md`（**目标 URL 一字未动**，181 处对拍全等，省 1720B）；③已结案 Status 的「—— 」分隔符未动（动它就破坏「头是原 Status 逐字前缀」）。①已用尽、③不能用 ⇒ 下一次写入仍会触顶（当前余 1057B），阈值上调仍待决策。

## 迁移段：第 292 / 293 轮的两段压缩规则

> 本段原为 `## Processing Rules` 里的两段**历史压缩轮次记录**。工作文件侧已合并为**一行**，
> 规则本身（**同一事实不必逐字重复 N 遍** / 只缩短显示名、链接目标一字不动 / 缩短掉的每个 token
> 都要能断言逐字存在于该行链接的归档里）**仍然有效并保留在工作文件**；逐轮明细在此**逐字保留**。

- 第 293 轮续记：把第 292 轮的做法推广到其余 7 类逐字重复行（实测记录 6、Reproduction 3、已解决条目 4、复核记录 5+2、探针记录 2），一律只缩短显示名、链接目标不变，零信息损失。至此本文件里已没有可再压缩的重复：余下每一行要么是决策、要么是问题陈述、要么是指针。
- 第 292 轮补充压缩规则：第 254 轮把 33 条条目的 Scope / Evidence 整体迁入同一个归档，于是在本文件里留下 33 条逐字相同的行。本轮把其中 33 条完全相同的改写为短显示名 `…-8.md`（链接目标不变），零信息损失；另有 **5 条带第 275 / 278 轮追加内容**的一行未碰。与第 291 轮压缩 5 条「复核记录」是同一手法：同一事实不必逐字重复 N 遍。

## 压缩记录（三个数不可混用，方向相反）

| 口径 | 数值 |
|------|------|
| 归档侧持有量（本轮迁入 + 新写的正文） | 见本文档头部「本文档体量」一行（**唯一来源，勿在此重复数字**） |
| 工作文件侧**迁出**量 | **P2-115 证据 918 字节 + 292/293 段 818 字节 = 1736 字节** |
| 工作文件侧**写入**量（两条新条目 + 3 处结转追加 + 1 个链接定义） | 见 `git diff --numstat` |
| 工作文件**全轮净变化** | 起点 **1232 行 / 149313 字节** → 终值 **1243 行 / 149778 字节** |

- **工作文件本轮写入前只剩 687 字节余量**（阈值 150000 字节 / 1500 行）⇒ 必须先腾空间。
- **本轮第三次启用 C8，但对象是「⏸ 待决条目的带日期证据段」**：P2-115 自身的结论就是
  「按现行规则已无法再压缩」（第 368 / 370 轮两段实测，已逐字迁入本归档）。
  ⇒ 压缩的可行面只剩**决策段之外**的证据段与**历史压缩轮次记录**。
- ⭐ **压缩记账的三个数**：**归档侧持有量**（本文档体量）、**工作文件侧迁出量**（1736 字节）、
  **工作文件全轮净变化**（**+465 字节**）—— 三者**不是同一个数**：
  本轮迁出 1736 字节，却同时写入了 1236 字节的新条目正文与 402 字节的结转追加 ⇒ 净变化**仍是正数**。
  把「迁出量」当成「净变化」会把一个**仍在增长**的文件误报成在收缩。
- **C6 彻底枯竭**：正文里重复 ≥2 次的内联归档路径字面量**一个都没有**（全文扫描 0 命中）。
- ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。
