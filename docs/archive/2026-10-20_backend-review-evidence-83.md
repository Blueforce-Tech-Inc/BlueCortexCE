# Backend Review Evidence 83 — 第 382 轮本轮新条目的整条正文（第 382 轮迁出）

> **归档规则**：承 `-62` ~ `-82` 的体例，迁出**本轮新写**的条目全文。
> 本轮 findings 尚有 **2410** 字节 / **13** 行余量，
> 但行数与字节**同时逼近**双阈值 ⇒ 本轮**做了两轮压缩**，见文末「压缩记录」。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：191 行 / 15222 字节。**
>
> 本批迁出 **P2-149 / P2-150 / P2-151** 三条新条目。
>
> **本轮代码方向为 Demo，文档方向为四家 demo 的 README（doc round 281）。**
>
> 本轮的形状与前两轮**不同**：前两轮是「文档说的不是实现真正做的事」，
> 本轮是**同一个毛病在 demo 这一层的三种变体** ——
> ① 文档里的**计数**与脚本/代码的实况脱节；
> ② 文档里的**跨家比较**把范围写大了（而那 21 个恰恰是差异最大的那批）；
> ③ 文档里的**行号指针**已经失效。
> 另加一条**代码方向**的实质交付：把 demo 的**暴露面**用测试钉住，
> 让「26 个端点」这类计数**从此不能悄悄漂移**。
---
### P2-149: Go demo README 的 P2-33 段落里**两处**失实陈述 —— ①「另外 21 个端点在**四家**里拼写完全相同」，而这 21 个恰恰是**与 Java demo 拼写不同**的那批（四家路径交集只有 `/chat`）；② `main.go:470` / `main.go:771` 两处行号引用**已失效**（实际在 492 / 793）

- **Problem**：`go-sdk/cortex-mem-go/examples/http-server/README.md` 的 P2-33 段落
  （承上一轮 P2-33）有两处独立的失实陈述。
  **① 范围与计数都错的那句**：
  > "The other 21 endpoints are spelled identically across all four."

  本轮把四家 demo 的**路径集**程序化提取后求交，结论与这句话**方向相反**：
  | 口径 | 实测 |
  |---|---|
  | Go / Python / JS 三家的路径交集 | **22** 条（Go 24 − 2 个改名） |
  | **四家**（含 Java demo）的路径交集 | **1** 条 —— **只有 `/chat`** |
  | Go∩Python∩JS 中 **Java demo 拼写不同**的 | **21** 条 |
  ⇒ 那 **21** 条，恰恰是**与 Java demo 拼写不同**的那批（`/search` → `/demo/search`、
  `/version` → `/demo/manage/version`、`/observations` → `/demo/observations`、
  `/experiences` → `/memory/experiences`、`/ingest/prompt` → `/demo/ingest/prompt` …）。
  Java demo 走的是 `/demo/…` 与 `/memory/…` 两套前缀，**四家里拼写完全相同的路径只有 `/chat`**。
  故「21 个在四家里拼写完全相同」= **写入即假**：作者把「另外三家相同」的范围**写成了「四家」**。
  **写入时点的实测**（`git show 13cd231`）：Java demo **当时就已经**是
  `/demo/*` 与 `/memory/*` 前缀 ⇒ 这个范围错误**不是事后漂移**。
  **② 失效的行号引用**：
  > "The names here are the ones the code registers (`main.go:470` and `main.go:771`)"

  实测两处注册在 **492** 与 **793**；`main.go:470` 落在 `/observations/{id}` 的 DELETE 分支里，
  `main.go:771` 落在 `/session/user` 的请求体解析里 —— **读者按指针会落到完全无关的代码**。
  但 `git show 13cd231:…/main.go` 显示**写入时行号是准确的**（470 / 771 恰为那两处注册）
  ⇒ 这一条是**事后漂移**（main.js 此后长过），**与 ① 的定性不同**，处置也不同。
- **Scope / Evidence**：
  - **四家路径集提取**（口径与硬证明）：
    - Go：`mux.HandleFunc("…")`；Python：`@app.route/get/post/patch/delete("…")`（**多行** `methods=` 也要收，
      否则少算 3 条）；JS：`app.get/post/patch/delete('…')`；Java：**类级 `@RequestMapping` 前缀 + 方法级映射**。
    - 路径参数统一归一化（`<observation_id>` / `<obs_id>` / `:id` / `{id}` → `{id}`），
      否则同一插槽会算成不同路径。
    - **排除机制核对**：Java demo **没有** `server.servlet.context-path`、
      **没有** `WebMvcConfigurer` 路径前缀、**没有**多值 `@RequestMapping`
      （`grep` 三项零命中）⇒ 它的 42 条路径确实只以 `/demo/…`、`/memory/…`、`/chat` 出现，
      **不存在**「同一路径另有别名」的可能。
  - **阳性对照**：JS demo 的端点集与 README 表做**集合级**对拍 ——
    代码 **26** 条、README **26** 行，**双向差集均为空**（逐字相等）；
    编造端点 `(GET, /definitely-not-a-real-route)` 在两边都 **MISS** ⇒ 对拍判据有效。
  - **按断言清扫**：带 `文件:行号` 的交叉引用**只有** Go demo README 这一处
    （另三家 demo README 零命中）⇒ 处置范围只此一处。
- **Status**：✅ **已修（382 轮，文档，零可执行行）**：
  ① 该句改为「The other **22** paths are spelled identically across the **Go, Python and JS** demos —
  the Java demo prefixes them (`/demo/…`, `/memory/…`), so `/chat` is the only path spelled the same
  in all four, and even that one is mapped with a different **method** (below).」；
  ② 行号更正为 `main.go:492` 与 `main.go:793`。
  全文逐字见 [`…-83`](../archive/2026-10-20_backend-review-evidence-83.md)。

### P2-150: Java demo README 的「Phase 3 acceptance tests (20 tests)」—— 脚本实测是 **25** 个，且该计数无任何测试钉住

- **Problem**：`examples/cortex-mem-demo/README.md` 的 E2E 段写：
  > "# Phase 3 acceptance tests (20 tests, all pass with EXTRACTION_ENABLED=true)"

  实测 `scripts/phase3-acceptance-test.sh` 的**运行时**计数是 **25**
  （第 378~382 轮连续五轮实跑均为 `Passed: 25 / Failed: 0 / Skipped: 0`，exit 0），
  而 README 写 **20** ⇒ 一个**无日期的现在时计数断言**与实况脱节。
  **写法的性质**值得记一笔：这句话里的 **20** 与脚本的**静态** `pass` 调用点数也对不上 ——
  写入该句的 commit `f09f692` 时脚本已有 **25** 个 `pass` 调用点 ⇒ **写入即假**，
  而非脚本长大后才漂移。
  **同类**：同段的另两个计数**核实为真**、未动 ——
  `demo-v14-test.sh` 有 **4** 个测试函数（`test_basic_experiences` / `test_icl_truncated` /
  `test_experiences_filtered` / `test_memory_health`），
  `demo-v15-test.sh` 有 **3** 个（`test_extraction_latest` / `test_extraction_history` /
  `test_experiences_with_user`）⇒ README 的「4 tests」「3 tests」都对。
  **按断言清扫**：四家 demo README 里带「N tests」的**计数断言只有**这一处
  ⇒ 处置范围只此一处。
- **Scope / Evidence**：
  - **运行时计数是权威**：静态 `pass` 调用点数**不等于**运行时数（HEAD 有 **31** 个调用点，
    运行时只有 **25** 个）—— 因为部分调用点在条件分支里（如 LLM 依赖的
    「Bonus」分支）不触发 ⇒ **只能以实跑读数为准**。
  - **历史各 commit 的静态调用点数**：`ca8d719` **21** → `b340986` **25** →
    `f09f692` **25**（README 写「20」的那次）→ `cccb92a` **30** → HEAD **31**。
  - **阳性对照**：「all pass with EXTRACTION_ENABLED=true」为真 ——
    第 380/381/382 轮以 `EXTRACTION_ENABLED=true` 实跑，`25/0/0`、exit 0，
    其中 LLM 路径的 `Test 14: Re-extraction updated with 1173 preferences (LLM re-extraction working)` 亦 PASS。
- **Status**：✅ **已修（382 轮，文档，零可执行行）**：该行改为
  「Phase 3 acceptance tests (25 tests, measured 2026-10-21; the script's own "Passed:" line is
  the current count) — all pass with EXTRACTION_ENABLED=true」。
  **为什么加日期与「以脚本读数为准」**：这个数字**会随脚本长大而变**，
  写成裸数字就是下一个会被漂移的断言；给出取数方式，它才不会第二次失实。
  全文逐字见 [`…-83`](../archive/2026-10-20_backend-review-evidence-83.md)。

### P2-151: Python demo 的**暴露面**（26 个端点）没有被任何测试钉住 —— README 首屏的「26」是一条**无日期裸计数**，与 Java demo 的「20 tests」同型

- **Problem**：`python-sdk/cortex-mem-python/examples/http-server/` 的 README 首屏写
  「exposing **26** REST endpoints」，而**没有任何测试钉住这个 26** ——
  `tests/test_demo.py` 的 **87** 个用例覆盖的是**行为**（校验、错误处理、路由分支），
  覆盖的是「每个端点做什么」，**不是「有哪些端点」**。
  ⇒ 首屏那个数是**无人看守的裸计数**：加一个端点、删一个端点、或像 Java demo 那样
  脚本长大，它都会**静默地变假**（P2-150 就是它已经变假的实例）。
  **顺带修正一个容易踩空的前提**：Python demo **确实有**单测，只是**文件放在 SDK 的
  `tests/` 里**（`test_demo.py` 直接 `import app`）—— 「demo 目录下没有测试文件」是**错的**。
  查证时若只看 demo 目录，就会得出「这个 demo 零测试」的错误结论。
- **Scope / Evidence**：
  - **暴露面实测**（Flask `url_map`，排除 Flask 自带的 `static` 规则与 `HEAD`/`OPTIONS`）：
    **26** 条（方法, 路径），与 README 表的 **26** 行**逐条相等**。
    路径参数有**两种写法**（GET 用 `<observation_id>`、PATCH/DELETE 用 `<obs_id>`），
    README 统一写 `{id}` ⇒ 对拍必须归一化，否则同一插槽会算成两条。
  - **修法（只加测试，不改 demo 实现）**：在 `tests/test_demo.py` 新增 `TestDemoSurface`，
    **两个**用例：
    ① `test_surface_matches_readme_table` —— 端点集与 README 表**双向**相等
    （既抓「代码有、文档无」，也抓「文档有、代码无」）；
    ② `test_readme_headline_count_matches_the_routes` —— 首屏那个数**与路由数**相等。
    ⇒ README 的计数**从此由测试看守**，不再是无日期裸计数。
  - **三向变异测试（证明它们能失败）**：
    | 注入 | 结果 |
    |---|---|
    | demo 里加一条未登记的路由 | ❌ `endpoints missing from the README table: [('GET', '/undocumented-mutation-route')]` |
    | README 首屏把 26 改成 25 | ❌ `the README says 25 endpoints, the demo exposes 27` |
    | README 里加一行幻影端点（`app.py` 已还原） | ❌ `README rows with no endpoint behind them: [('GET', '/phantom-route')]` |
    ⇒ 三个方向**各自单独**验证过（第一版把「幻影行」与「缺行」一起注入，
    前一个断言先触发，**遮住**了后一个方向 ⇒ 单独重跑才拿到该方向的读数）。
    全部注入按 sha256 **逐字节还原**。
  - **Python SDK 的测试数随之变化**：455 → **457**（`228 client + 140 DTO + **89** demo`），
    两版 README 首屏已同步。
- **Status**：✅ **已修（382 轮，代码方向，**只加测试**）** ——
  `tests/test_demo.py` 新增 `TestDemoSurface`（**2** 个用例），三向变异测试证明能失败；
  两版 SDK README 的计数 **455 → 457**。
  全文逐字见 [`…-83`](../archive/2026-10-20_backend-review-evidence-83.md)。

### 本轮其余核实为真（四家 demo，逐条）

- **JS demo 的端点面**：代码 **26** 条、README **26** 行，**双向差集为空**（集合逐字相等）；
  README 首屏「26」与源码注释「Exposes 26 REST endpoints」均与实况相符 ⇒ **为真**。
- **Python demo 的端点数**：**26**（方法, 路径），与 README 表逐条相符 ⇒ **为真**。
- **Java demo 的端点表**：README 的 **14** 行**全部真实存在**
  （`/demo/projects`、`/demo/session/start`、`/demo/session/lifecycle`、`/memory/experiences`、
  `/memory/icl`、`/memory/icl/truncated`、`/memory/experiences/filtered`、
  `/demo/extraction/{latest,history,run}`、`/memory/health`、`/chat`、`/demo/tool`、`/actuator/health`）。
  该 demo 实有 **44** 个端点（去重后 42 条路径 + 方法展开）⇒ 表是**精选子集**，
  但**没有一行是假的** ⇒ 按「**遗漏 ≠ 失实**」**不处置**。
- **Java demo 的 `FileReadTool` 路径围栏**：README 称「词法检查**和**符号链接检查都跑，
  由 `FileReadToolTest` 钉住」—— 核实为真：`FileReadToolTest` 有 **8** 个用例
  （含 `refusesSymlinkPointingOutsideRoot`），`FileReadToolRootEdgeCaseTest` 另有 **7** 个
  （根目录自身是符号链接、根目录不存在、前缀相同的兄弟目录、`.` 段等）⇒ **覆盖周密，无缺口**。
- **Go demo 的端点表**：**26** 行 ↔ 代码 **24** 条路径 × 方法展开 = **26** 个（方法, 路径）对
  ⇒ 相符（「/」是 404 兜底路由，不作为端点登记）。

### 压缩记录（findings 文件已逼近**双**阈值）

- **第一轮 C5**（重复归档链接改引用式，目标与显示名逐字不变）：
  5 个目标、**21** 处内联链接改引用式 ⇒ 省 **727** 字节。
  **硬证明**：① (显示名, 目标) 配对多重集**完全相同**；
  ② 去掉链接后的正文**逐字相同**（唯一差异是**追加的定义块本身**，原有 1481 行逐字未变）；
  ③ 每个目标文件**确实存在**。
- **第二轮 C5**（再转 8 个目标、16 处）曾省 728 字节，但**行数涨到 1504 ⇒ 越过 1500 的行上限**
  ⇒ **回退**（用字节换回 8 行），并把 P2-149 与原 P2-150 合并为一条以再省 2 行。
- **探针自身错 1 处**：压缩脚本里 `int(x[1:])` 把捕获组（纯数字）当成了含 `a` 的串
  ⇒ `ValueError` **在写盘前中止**（文件未变）；修正为 `int(x)` 后重跑。
  同理，**硬证明的第一次版本也为 False** —— 证明函数只认内联写法，
  转换后成了引用式自然匹配不到；改用「能解析引用式 + 剥掉定义块后比对」才拿到真读数。
  ⇒ **证明本身出错时，先怀疑证明，再怀疑被证明物**。
- **C3d（去粗体标记）已近枯竭**：全文 **758** 处成对 `**`，按其口径（唯一散文词组、
  长度 ≤ 12、无标识符/路径）只剩 **65** 处可去 ⇒ 仅省 **260** 字节。
- **折行重排（换行位置）**只找到 **2** 段合格（24 行），收益小且要动叙述正文 ⇒ **不做**。
- **终值**：**1494 行 / 149328 字节**，余量 **6 行 / 672 字节** ⇒
  **行阈值与字节阈值同时逼近**，**P2-115（上调 `MAX_LINES` / `MAX_BYTES`）已从「余量紧张」
  升级为「下一轮就会撞线」**。

### 验收

- **Python SDK 457/457** 全绿（`228 client + 140 DTO + 89 demo`）。
- **回归 45/0/1** 与 **Phase3 25/0/0**，均 exit 0，跑在**含当前后端代码的新鲜实例 37790**
  （jar 01:06、pid 82326；**jar 比全部后端源文件都新**，且含第 378 轮的
  `ModeController$ActiveModeSwitchRequest`）。
- 指纹 `f810e889…` → **`386fe87f…`（本轮实测）**，记录数仍 2223。
- 全文逐字见 [`…-83`](../archive/2026-10-20_backend-review-evidence-83.md)。
