# 后端审查证据归档 -99（2026-10-22）

> 第 398 轮（Python SDK + SDK README，doc round 297）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1308 行 / 146411 字节**）。
> 本文档体量：**219 行 / 19381 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。

## 本轮新写的三条（正文逐字保留在本归档）

### P2-193: Python SDK 客户端的 **35 处**必填参数守卫里，**3 处**从未被任何测试钉住

- **Scope**: → [scope-9][a6]
- **Status**: ✅ **已修（398 轮，Python SDK 侧，只加测试 + 改文档，SDK 主源码零改动）** —— 新增
  `tests/test_required_arguments.py`（**12** 个顶层用例），把 35 处守卫逐条钉住。

### P2-194: 四家 SDK README 的跨家断言漏报了**第二处**差异

- **Scope**: → [scope-10][a7]
- **Status**: ✅ **已修（398 轮，文档方向，四份 README 双语共 8 处）** —— 八处一律改为「**两处**差异」。

### P2-195: `update_observation` 的 content/narrative 冲突在「构造后修改」这条路径上前缀重复

- **Scope**: → [scope-9][a6]
- **Status**: ⏸ **只记录不实施** —— 消息是对外可观测的契约；两条路径的当前字符串已用测试钉住。

## 普查怎么做的

对 `python-sdk/cortex-mem-python/cortex_mem/client.py`（867 行，sha256 `9879f8a1…`）做**逐守卫单变量变异**：
把一处守卫的条件改成**恒假**（`if False and (<cond>):`），跑**整个 Python SDK 套件**，再把文件**按字节**还原。
判据是「这道守卫若不再触发，原本全绿的套件还全绿吗」—— 全绿就说明**没有任何测试钉住它**。

变异用「条件恒假」而不是「删掉整块」：删块可能让控制流变成与守卫无关的样子
（Python 里删掉一层 `if` 只会少一层缩进，通常仍能编译，但**编译失败的构建不能叫「绿」**——
它什么都没测），恒假变异保持结构不变。

**普查结果**

- **35** 处会抛 `ValidationError` 的守卫。
- **32** 处：变红 ⇒ 已有测试钉住。
- **3** 处：**仍然全绿** ⇒ 零钉住。

三处零钉住的守卫：

| # | 位置 | 所属方法 | 条件 | 判读 |
|---|------|----------|------|------|
| 2 | `client.py:240` | `start_session` | `not project_path` | 零钉住（同一方法的 `session_id` 已被钉住） |
| 31 | `client.py:762` | `get_latest_extraction` | `not project_path` | 零钉住（同一方法的 `template_name` 已被钉住） |
| 33 | `client.py:790` | `get_extraction_history` | `not project_path` | 零钉住（同一方法的 `template_name` 已被钉住） |

**成因形态很整齐**：**每个方法只测了其中一个守卫**，另一个成了漏网的那道。
`start_session` 测了 `session_id` 没测 `project_path`；两个 extraction 方法测了 `template_name`
没测 `project_path`。而 README 用一张**十八行**的表向调用方承诺这些参数「非空即拒绝、且不发出任何请求」
⇒ 这三条承诺当时**没有测试背书**。

## 四家「纯空白参数」的同探针实测

Python 的守卫一律写作 `if not x` —— 它拒**空串**，不拒**纯空白**。
另三家的写法是：Go `strings.TrimSpace(...) == ""`、Java `value.isBlank()`、JS `!value || !value.trim()`。
四家都用**各自的真实 SDK** 实测（不是读码推论）：

| SDK | 纯空白必填参数 | 纯空白 `ids` 元素 | 判定方式 |
|-----|----------------|-------------------|----------|
| Python | **放行，请求确实发出（1 个）** | **拒绝**（`ids[1] is empty`） | 真实 client + `responses` mock 计数 |
| Go | 拒绝（`projectPath is required`） | 拒绝（`ids[1]: id must not be empty`） | 真实 SDK client（`replace` 指向本仓库） |
| JS | 拒绝（`project_path: is required`） | 拒绝（`ids[1]: is empty`） | 真实 `src/index.ts`（`tsx` 直跑） |
| Java | 拒绝（`isBlank()` 为 true） | — | 源码 `requireNonBlank` + `jshell` 实测 `isBlank` |

⇒ **Python 是四家里唯一放行纯空白参数的 SDK**，而且**它自己内部也不一致**：
只有 `get_observations_by_ids` 的元素检查做了 `.strip()`（`if not id_ or not id_.strip()`），
其余 30 余处一律 `if not x`。

## 探针自身的四处错（这一轮的教训比结论更值钱）

1. **脚本放在 `/tmp` ⇒ 导入的是 4 月 25 日装进 site-packages 的旧副本，不是工作树。**
   那个副本的 `client.py` **连捕获方法上的 `project_path` / `tool_name` 守卫都没有**，
   于是探针测出来「捕获方法没有守卫」—— 看起来像产品缺陷，其实是探针量错了对象。
   差点据此写出一条假 finding。**修法**：探针里先 `sys.path.insert(0, SDK)`，
   并**断言** `cortex_mem.__file__` 落在工作树内；跑 pytest 则用 `python -m pytest` 且 `cwd=SDK`
   （`-m` 会把 cwd 放进 `sys.path`）。
2. **`responses` 未激活时完全不拦截** —— 请求会真的打到常驻后端 37777。
   所以「不发请求」的用例必须**密封**在 mock 里，且断言 `len(responses.calls) == 0`。
3. **JS 的方法都是 `async`** —— 客户端守卫在 `async` 函数里抛出时表现为 **rejected promise**，
   探针不 `await` 就会变成未捕获 rejection，Node 直接把进程带走，
   报错长得像「环境坏了」而不是「守卫触发了」。同理 `tsx` 把包外的 `.ts` 按 CJS 编译，
   **顶层 await 是语法错误**，得放进 `async main()`。
4. **JS 的 `startSession` 收的是对象**（`{session_id, project_path}`）不是位置参数 ——
   第一版探针按位置传，结果**第一道**守卫（`session_id`）先触发，
   读数看起来像「JS 也放行空白」。改正后才拿到「JS 拒绝」的真实读数。

⇒ 通用形态：**探针读数异常时，先怀疑探针，再怀疑被测物**。

## content/narrative 冲突的**两条路径**，消息不同

| 路径 | 触发点 | `field` | `str(exc)` |
|------|--------|---------|-----------|
| A：构造时同时给 `content` 与 `narrative` | `ObservationUpdate.__post_init__`（**client 都没进**） | `content\|narrative` | `cortex-ce: validation error on content\|narrative: content and narrative cannot both be set — …` |
| B：构造时只给一个，**之后再赋值** | `ObservationUpdate.to_wire()`（在 client 内部） | `update` | `cortex-ce: validation error on update: ` **`cortex-ce: validation error on content\|narrative: `** `content and narrative cannot both be set — …` |

路径 B 的**前缀重复了一次**，且 `field` 从 `content|narrative` 变成了 `update`（丢了具体字段）。
成因在 `client.py:679` 的 `except ValueError as e: raise ValidationError(str(e), field="update")` ——
`str(e)` 已经是**格式化过的完整字符串**，再包一层就重复了。

**处置**：**只记录不实施**。这条消息是对外可观测的契约，按消息匹配的调用方会受影响，
改前缀属对外契约变更 ⇒ 记为 finding（P2-195），不单方面实施。
但两条路径的**当前**完整字符串都用测试钉住了 ⇒ **不会悄悄漂移**。

## 新增测试：`tests/test_required_arguments.py`

**12** 个顶层用例，覆盖 **35** 处守卫：

- 逐条钉住 `field` + `message` + 渲染出的完整字符串 + **请求数为 0**（35 处守卫全在表里）；
- 「README 发布的必填参数表 ↔ 运行时」绑定（两份 README 各自独立解析，方法集合必须相等）；
- 「README 承诺的每个参数 ↔ 该方法上真实存在的守卫」绑定（逐行取反引号里的参数名）；
- 暴露面计数（**25** 个 API 方法，`close` 是生命周期成员不算）；
- 「README 发布的默认 base URL ↔ 运行时」绑定（**两份 README 分别比**）；
- 「守卫计数防漂移」：`client.py` 里 `raise ValidationError(` 的处数必须等于本文件的用例数 ——
  以后有人加了守卫却不加用例，这个测试就红；
- 纯空白行为**按现状**钉住（放行且请求确实发出），并要求两份 README **点名**这条差异；
- content/narrative 两条路径的逐字消息；
- **阳性对照**：故意发一次请求，证明「请求数为 0」这个断言**能失败**。

## 变异复验：新测试自己也过了变异检验

| 变异 | 内容 | 期望 | 实测 |
|------|------|------|------|
| M0 | 只加一行注释（阴性对照） | 0 红 | **0 红**（483 passed） |
| M1 | `start_session` 的 `project_path` 守卫恒假 | 红 | **1 红**（`test_every_guard_is_pinned`） |
| M2 | `get_latest_extraction` 的 `project_path` 守卫恒假 | 红 | **1 红** |
| M3 | `get_extraction_history` 的 `project_path` 守卫恒假 | 红 | **1 红** |
| M4 | `client.py` 少一处 `raise ValidationError` | 红 | **3 红**（含新的计数防漂移用例） |
| M5 | `start_session` 的守卫改成去空白 | 红 | **1 红**（纯空白行为用例） |
| M6 | README 的必填参数表删掉一行 | 红 | **1 红** |
| M7 | README 删掉那句纯空白差异说明 | 红 | **1 红** |
| M8 | README（英）的默认 base URL 改一个数字 | 红 | **1 红** |
| M9 | README（中）的默认 base URL 改一个数字 | 红 | **1 红** |

⇒ **3** 处原先零钉住的守卫**全部转红**。

**M8/M9 这一格还抓出了新测试自己的一个洞**：第一版的「默认 base URL ↔ 运行时」用例
在一个循环里读两份 README 并**保留最后一份的值**，于是英文版被改错时被中文版的正确值**掩盖**，
测试照样绿。**这正是「校验器正常返回绿灯是最贵的一种失败」** ⇒ 改成**两份 README 各自独立比对**，
M8 / M9 才各自转红。

## 验收

- Python SDK **483/483**（231 client + 140 DTO + 89 demo + 11 error-contract + 12 required-arguments）；
  Go SDK **441/441**（九个模块 9/9 绿）；JS SDK **373/373**；Java SDK **309/309**（surefire XML 逐类相加）；
- 回归 **46/0/1**、Phase3 **25/0/0**，均 exit 0，跑在**新鲜实例 37790**（pid 68657；
  `find backend/src -newer <jar>` 为空 ⇒ jar 比全部后端源码都新）；
- 指纹 `a74d0cb3…` → `4a4d8ca9…`，记录数 2237 → **2238**。

## 本轮顺带改准的另外三份 README（连带修正）

四份 README 的跨家断言都写着「四家强制的是同一组参数、只有**一处**例外」，
而纯空白这条差异**从来没被任何一份 README 记载**。本轮把**八处**（四家 × 双语）一律改成
「**两处**差异」并点名纯空白这条。Go / Java / JS 三家的 READMEs 属于它们各自轮次的文档方向，
本轮作为**连带修正**一并改准（同一处失实、同一句改法），**未**在它们的测试里加钉住用例
（那属于各自轮次）。Go 侧有一个用例**禁用**了几个特定短语，本轮的改法没有触到它们。

## 本轮迁入本归档的九条（工作文件侧已按 C10 压缩）

> 下面九条的**正文逐字**从 `docs/drafts/backend-review-findings.md` 迁入本归档；
> 工作文件侧只剩标题 + 一行 Status（含本归档指针）。

### P2-184: `docs/go-sdk-demo-guide.md` 的**三处数字**失实 —— 「27 个 SDK 方法都暴露成 HTTP 端点」（实为 **25**）、「24 endpoints」（demo 自己的表是 **26** 行）、「All 24 endpoints 每个都被测到」（实为 **21**）

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Evidence**: → 归档 [`…-95`][a53]（接口 27 个方法的逐个归属、demo 实际调用的 SDK 方法集合、e2e 脚本命中路径的逐条抽取、四家 demo 脚本的横向对拍、36 个编号测试的核对）
- **Status**: ✅ **已修（394 轮，文档方向，零可执行行）** —— 三处数字改准，并把「哪几个没被测到」「别家覆盖到哪」逐项点名写进表格单元格。

### P2-185: 后端暴露面的钉住有**结构盲区** —— 只扫 `@RestController`，传输层 6 个操作从未被任何测试钉住；而那份测试的 javadoc 宣称钉住的是「服务器发布的完整 (method, path) 集合」

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Evidence**: → 归档 [`…-96`][a54]（活体端点逐个探测、`/v3/api-docs` 与钉住集合的双向差集、actuator 链接页原文、四个单变量变异与阴性对照的逐条读数）
- **Status**: ✅ **已修（395 轮，Backend 侧，只加测试 + 改注释，后端主源码零改动）** —— 新增 `BackendTransportEndpointSurfaceTest`（**3** 例，从 `application.yml` **推导**传输层应有哪些操作并与钉住集合相等）；`BackendEndpointSurfaceTest` 的 javadoc 失实陈述改准为真实口径。

### P2-186: API 文档双语的端点清单**从未与代码绑定**（「verified identical」是无机制的手工断言），且口径只覆盖 springdoc 的 REST 层 —— 漏掉 6 个传输层操作，其中 `GET /sse` 是集成指南教给用户的地址

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Evidence**: → 归档 [`…-96`][a54]（两语解析器与活体 OpenAPI 的三方对拍、解析器自身两处错、六个坏样本、活体检查器四个单变量变异与两处退出码）
- **Status**: ✅ **已修（395 轮，文档方向，两语同步）** —— 两语各补一节「Transport Endpoints / 传输层端点」（6 个操作 + 产生它们的配置 + 实测日期），四处计数改准为「REST 71/66 + 传输 6/6 = 77/72」；新增 `scripts/check-api-doc-surface.py` 与**运行中服务器**逐条比对，接入回归 Test 27。

### P2-187: 验收基线区块与轮次报告**连续两轮未更新** —— 第 393/394 轮都跑过完整验收，而 `health-check-task.md` 的基线仍停在第 392 轮，那两轮的综合报告也都没追加

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-96`][a54]（基线区块与轮次报告的逐轮核对、两轮 commit 与指纹的对照、影响范围分析）
- **Status**: ✅ **已修（395 轮，流程侧）** —— 基线更新到第 395 轮并追加本轮报告；第 393/394 两轮以**标注**方式补记（不改写历史正文）。**机制缺口本身只记录不实施**（与 **P2-100** 同型：无脚本强制、纯靠人工）。

### P2-188: Java SDK 客户端的 **50 处参数守卫**里，**21 处**从未被任何测试钉住 —— 而 README 用一张十八行的表向调用方承诺这些参数「非空白即拒绝、且不发出任何请求」

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-97`][a55]（50 处守卫的逐条单变量变异与读数、21 处零钉住的逐条清单、探针自身的四个坑、复验的 20/21 读数、唯一那处死代码的定位与措辞比对）
- **Status**: ✅ **已修（396 轮，Java SDK 侧，只加测试 + 改文档，SDK 主源码与后端零改动）** —— 新增 `CortexMemClientRequiredArgumentsTest`（**59** 例）：逐条钉住 README 承诺的每一道守卫（异常类型 + 消息 + **请求数为 0**），另加 4 条 DTO 层用例、5 个阳性对照、2 条「README 表 ↔ 运行时」绑定与 1 条暴露面计数（25 个 API 方法）。复验：**21 处零钉住 → 20 处被新测试钉住**；唯一例外是 `search` 的 `project` 守卫（`SearchRequest` 紧凑构造器以**逐字相同**的措辞先拒 ⇒ 该守卫为**死代码**），属契约面，**只记录不删**。

### P2-189: Java README 的跨家断言「Go、Python 和 JS 三家 SDK 强制的是**完全相同的一组**规则」是**失实陈述** —— Java 另有一道「`projectPath` 必须是绝对路径」的规则，而同一份文件 26 行后自己写着「有一条规则只有 Java 有」

- **Scope**: → [scope-10][a7]
- **Evidence**: → 归档 [`…-97`][a55]（八句跨家断言的逐句清点、三家源码里绝对路径校验的零命中实测、双语改前改后的原文对照）
- **Status**: ✅ **已修（396 轮，文档方向，两语各一处）** —— 改为与 Go 英文版同构的措辞（「同一组『非空白』检查——只有一处例外：**Java 还额外要求 `projectPath` 必须是绝对路径**」）；另补一句实测过的行为（请求对象为 `null` 时抛 `NullPointerException`，与空白检查是**两道**不同的检查）。

### P2-190: Java README 首屏的单元测试数**已过期** —— 写着「250（197 + 46 + 7），measured 2026-10-21」，而本轮加测后实际是 **309**

- **Scope**: → [scope-10][a7]
- **Evidence**: → 归档 [`…-97`][a55]（三个模块各自 surefire XML 的逐类计数、maven 汇总行与 XML 的一致性对拍、加测前后的数量差）
- **Status**: ✅ **已修（396 轮，文档方向，两语各一处）** —— 改为「309（256 client + 46 spring-ai + 7 starter），measured 2026-10-22」。数法为**运行时实测**：maven 汇总行与 surefire XML 两种口径都读出 256 / 46 / 7，**互相吻合**才写进去。

### P2-191: Go SDK 客户端的 **36 处守卫**里，**2 处**零钉住 —— 一处是 `GetObservation` 的空 `id` 校验，另一处是「空 BaseURL 回落到默认值」，而**后者有一个以它命名的测试、却什么都没断言**

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-98`][a56]（36 处守卫的逐条单变量变异与读数、两处零钉住的定位、M0~M5 的变异复验读数、Go 与 Java 两侧钉住面的横向对照表、计数口径与「九个 module」的坑）
- **Status**: ✅ **已修（397 轮，Go SDK 侧，只加测试 + 改文档，SDK 主源码零改动）** —— 新增 `required_arguments_test.go`（**46** 个运行时用例）与一条内部测试（**1** 例）：逐条钉住 README 承诺的每一道守卫（错误类型 + `Field` + 消息全文 + **请求数为 0**），另加 **6** 个阳性对照、**2** 条「README 表 ↔ 运行时」绑定、**1** 条暴露面计数（25 个 API 方法）、**1** 条「README 发布的默认 base URL ↔ 运行时」绑定与 **1** 条散文钉住（该段必须同时点名两种错误类型）。复验：**2 处零钉住 → 2 处被新测试钉住**（M0 阴性对照 **0 红**，M1~M5 各红）。**横向对照**：同一套方法 Go 侧 **36** 处守卫已有 **34** 处被钉住，Java 侧 **50** 处只有 **29** 处 ⇒ Java 那 **21** 处缺口是 Java 自己的，不是 Go 的。

### P2-192: Go README 的「必填参数在客户端校验」段对**整张表**声明返回 `ValidationError`，但表里 `UpdateObservation` 那一行的空更新抛的是**另一个 Go 类型** —— 而同一份文件 40 行前自己写明了这个区别

- **Scope**: → [scope-10][a7]
- **Evidence**: → 归档 [`…-98`][a56]（两处自相矛盾段落的行号与原文、`IsValidationError` 对空更新返回 false 的既有实测、删掉例外说明后的红→绿读数）
- **Status**: ✅ **已修（397 轮，文档方向，两语各一处）** —— 该段改为点名例外（`UpdateObservation` 的空更新抛 `*dto.ObservationUpdateValidationError`，且 `IsValidationError` 对它返回 false），并把这条说明**钉进测试**（该段必须同时点名两种错误类型，否则红）。

