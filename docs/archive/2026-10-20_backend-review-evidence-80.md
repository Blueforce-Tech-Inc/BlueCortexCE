# Backend Review Evidence 80 — 第 379 轮本轮新条目的整条正文（第 379 轮迁出）

> **归档规则**：承 `-62` ~ `-79` 的体例，迁出**本轮新写**的条目全文。
> 本轮 findings 尚有 **4850** 字节余量（上一轮用两处零信息损失压缩腾出），
> 故本轮**未再压缩**。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：126 行 / 10552 字节。**
>
> 本批迁出 **P2-145** 一条新条目，并附 **P2-128 的 Go 侧结转**记录。
>
> **本轮代码方向为 Go SDK，文档方向为 SDK README（doc round 279）。**
>
> 本轮的形状与上一轮**同源但更轻**：上一轮修的是「机器可读契约说的不是实现真正做的事」，
> 本轮是**同一段文字在两种语言里自相矛盾**，而**代码站在中文那一边**。
> **P2-145**：Go SDK 的**英文** README 写「the **two** capture methods are the clearest case」，
> 而**同一 commit**（`6902968`）写下的**中文版**说的是「其中**三个** capture 方法最能说明问题」——
> 实现里也确实**三个**（`RecordObservation` / `RecordSessionEnd` / `RecordUserPrompt`，
> 三个方法**全都**走 `doFireAndForget`，即吞掉后端错误、返回 nil）。
> 更要紧的是：Go 的**接口注释**只给 `RecordObservation` 标了 `(fire-and-forget)`，
> 另两个方法没标 ⇒ 在**同一份列表、同一种体例**里，读起来像是「只有它是 fire-and-forget」；
> 而 **JS 与 Python 两家都把三个方法逐个标了**，README 里还各有一个
> 「Capture (fire-and-forget)」分组标题。**Java 侧三个方法一个都没标**（同型，结转）。
> **另一个近失实的候选已被证伪**：README 首屏的「25 methods」经查**是真的** ——
> `57353ac` 明确记了计数口径「close 是生命周期方法，不算 API 方法」，
> 即 27 个接口方法 − 2 个生命周期方法（`Close` / `String`）= **25 个 API 方法**，
> 且它自己的方法表 27 行与接口逐个相符。⇒ **不是缺陷，不改**。
> **测试计数也是真的**：本轮实测三种口径（顶层 `--- PASS`、含子测试、`func Test` 声明数），
> 只有**含子测试**的那一种与 README 的分解吻合（根模块 306 = core 239 + dto 67、
> `examples/http-server` 33）⇒ README 的 **372** 逐项精确相符，**不改**。
---

### P2-145: Go SDK 英文 README 写「the **two** capture methods」—— 同一 commit 的中文版写「三个」，实现里也确实是三个；且接口注释只给其中一个标了 fire-and-forget

- **Problem**：`go-sdk/cortex-mem-go/README.md` 与 `README-zh-CN.md` 里**同一句话**的两个语言版本
  对捕获方法的数量给出了**互相矛盾**的说法，而且**英文那句是错的**：
  | 文件 | 原文 | 方法数 |
  |------|------|--------|
  | `README.md`（EN） | "The checks are not decoration, and the **two** capture methods are the clearest case." | **2** ❌ |
  | `README-zh-CN.md`（ZH） | 「这些检查不是装饰。其中**三个** capture 方法最能说明问题」 | **3** ✅ |
  两行**出自同一个 commit** `6902968`（"docs: document the required-argument contract in all four SDK READMEs"），
  `git log -L` 逐行核对确认。**实现站在中文那一边**：`go-sdk/cortex-mem-go/client.go` 的接口里
  捕获方法**三个**（`RecordObservation` / `RecordSessionEnd` / `RecordUserPrompt`），
  而 `client_methods.go:53/65/80` 显示**三个全都**走 `doFireAndForget` ——
  即三者**都**是 fire-and-forget：**吞掉后端的拒绝、记一条日志、返回 nil**。
  英文那句把三个说成两个 ⇒ 一个**现在时的计数断言**与实现不符。
  **更要紧的是同一处遗漏**：Go 的**接口注释**只给 `RecordObservation` 标了 `(fire-and-forget)`：
  ```go
  // RecordObservation records a tool-use observation (fire-and-forget).
  // RecordSessionEnd signals session end.                 ← 没标
  // RecordUserPrompt records a user prompt.               ← 没标
  ```
  在**同一份列表、同一种体例**里，只标第一个，读者会读成「只有它是 fire-and-forget」——
  而实现上三者都是。**跨家对照**：
  | 家 | 接口注释标注 fire-and-forget 的捕获方法 | README 的分组标题 |
  |---|---|---|
  | JS | `recordSessionEnd` ✅、`recordUserPrompt` ✅、`recordObservation` ✅ | `#### Capture (fire-and-forget)` ✅ |
  | Python | `record_session_end` ✅、`record_user_prompt` ✅、`record_observation` ✅ | `### Capture (fire-and-forget)` ✅ |
  | **Go** | 只有 `RecordObservation` ❌ | **无分组**，方法表只有一行 `Capture` |
  | **Java** | **三个都没标** ❌ | 无分组 |
  ⇒ **Go 与 Java 两家缺一半或全缺，JS 与 Python 两家都齐**。
- **Scope / Evidence**：
  - **证伪过程（本轮最大的一个「差点记成缺陷」）**：README 首屏的
    「**Full API coverage** — 25 methods」曾被本轮判为**陈旧计数**（它自己的方法表列了 **27** 个）。
    查 `git log -L` 后**推翻了**：`57353ac`（"fix: JS/Go SDK README API method count 26→25
    (close is lifecycle, not API)"）写明了计数口径 —— `Close` 是生命周期方法、不算 API 方法 ⇒
    27 − 2（`Close` / `String`）= **25 个 API 方法**，**是真的**；
    且方法表 27 行与接口 27 个方法**逐个相符**（无缺、无幻影）。
    ⇒ **不是缺陷，不改**（记在此处是为了不再重复怀疑）。
  - **测试计数的三种口径实测**（本轮只做计数核对，未改一个字）：
    | 口径 | 根模块 | `examples/http-server` |
    |------|--------|------------------------|
    | 顶层 `--- PASS` 行 | 277 | 5 |
    | **含子测试**（`^\s*--- PASS`） | **306** = core 239 + dto 67 | **33** |
    | `func TestXxx` 声明数 | 210 | 5 |
    只有**含子测试**的那一种与 README 的分解吻合 ⇒ **372 = 306 + 8 + 13 + 12 + 33** 逐项精确相符。
  - **Go 的四个下限已由测试钉住**（本轮实测全 PASS）：
    `TestClientTimeoutIsFlooredNotReset`（同时钉 `Timeout` 与 `ConnectTimeout`）、
    `TestClientRetryBackoffFloor`、`TestClientTimeoutDefaultsUnchanged`，
    外加 `client_test.go` 的 `TestMaxRetries_ZeroClampsToOne` / `..._NegativeClampsToOne`。
  - **Go SDK 的 24 个请求点与活体 OpenAPI 的 71 个操作逐个对得上**（判据是**动词 + 路径形状**；
    **阳性对照**：编造端点正确 MISS）；**DTO 的 json tag 与活体响应键逐字相符**
    （`observations`/`strategy`/`fell_back`/`count`、`items`/`hasMore`、`observations`/`count`）。
- **Status**：✅ **已修（379 轮，Go 侧）—— 零可执行行**：
  ① `README.md` 的「two」改为「**three**」，并把整句改成点明
  `RecordObservation` / `RecordSessionEnd` / `RecordUserPrompt` **三者都是** fire-and-forget
  （吞掉后端返回、报成功）；`README-zh-CN.md` 同步加同样的点明（原句的「三个」本就正确）；
  ② 两版方法表的 `Capture` 组标注 **(fire-and-forget)**（与 JS / Python 的分组标题同体例）；
  ③ `client.go` 的接口注释给 `RecordSessionEnd` 与 `RecordUserPrompt` 补上 `(fire-and-forget)`。
  **Java 侧同型结转**（三个方法一个都没标；属 Java SDK 方向，本轮未实施）。
  **P2-128 的 Go 侧**（四个下限的文档）同批结转，详见下一段。全文逐字见
  [`…-80`](../archive/2026-10-20_backend-review-evidence-80.md)。

### P2-128 的 Go 侧结转（第 379 轮）：Go 的四个下限本来就由测试钉住，缺的是**文档** —— 本轮把双语 README 的选项表与三个 Option 的 doc 注释补齐

- **本段是 P2-128（369 轮立条）的跨家结转，不是新条目。** P2-128 原文逐字见
  [`…-70`](../archive/2026-10-20_backend-review-evidence-70.md)。
- **背景**：P2-128 记的是「四家 SDK 的参数下限**只有 Python 的 README 写了**」，
  并给出三条修法，**①「只补文档」（推荐）**、
  ②把 Java 对齐另三家的 100ms 下限（**对外行为契约变更**）、
  ③维持现状。**369 轮只实施了 JS 侧**（双语补三行下限），
  原文明写「**Go / Java 两家属跨家结转，留待后续轮次或用户决策**」。
  **本轮是 Go SDK + SDK README 方向 ⇒ 结转 Go 侧**。
- **Go 的真实下限**（实现位置 `client_impl.go:136/149/152/155`，**运行时实测**）：
  | 参数 | 下限 | 由哪个测试钉住 |
  |------|------|----------------|
  | `WithMaxRetries` | **1**（低于 1 抬到 1） | `TestMaxRetries_ZeroClampsToOne`、`..._NegativeClampsToOne` |
  | `WithTimeout` | **100ms**（**抬**到下限，不是回落默认值） | `TestClientTimeoutIsFlooredNotReset` |
  | `WithConnectTimeout` | **100ms** | 同上（同一测试同时钉两个） |
  | `WithRetryBackoff` | **100ms** | `TestClientRetryBackoffFloor` |
  本轮实测：`go test -run "TestClientTimeoutIsFlooredNotReset|TestClientRetryBackoffFloor|TestClientTimeoutDefaultsUnchanged" -v ./...`
  ⇒ **3 个测试全 PASS**。`config_internal_test.go` 之所以是**内部测试包**，
  是因为这些值在 `NewClient` 里归一化后存在**未导出**的 `httpClient.config` 上，
  外部测试包根本观察不到 —— 注释里写明了「**第 255 轮那个缺陷就是靠这条路活下来的**」。
- **本轮已做（纯文档/注释，零可执行行）**：
  ① `README.md` / `README-zh-CN.md` 的**选项表**补齐四行下限
  （`WithTimeout` / `WithConnectTimeout` / `WithRetryBackoff` 标「下限 `100ms`」，
  `WithMaxRetries` 标「低于 `1` 的值会被抬到 `1`」）——**与 JS / Python 两家的措辞同体例**；
  ② `client_impl.go` 里 `WithTimeout` / `WithConnectTimeout` / `WithRetryBackoff` 三个
  **doc 注释**补上下限说明（`WithMaxRetries` 的 doc 注释原本就写了，故未动）。
  **Java 侧仍待决**（`retry.backoff` 无 100ms 下限、`read-timeout` 无任何下限，
  且 Java 的 README 属性表四项全无下限；**行为**要不要对齐属对外契约变更）。
  **另一条独立小项仍待决**：Python / JS / Java 三家的下限**没有任何测试钉住**，
  Go 那 3 个（加 `MaxRetries` 那 2 个）可以直接照抄。
