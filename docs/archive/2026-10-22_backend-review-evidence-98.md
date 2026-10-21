# 后端审查证据归档 -98（2026-10-22）

> 第 397 轮（Go SDK + SDK README，doc round 296）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1307 行 / 148349 字节**）。
> 本文档体量：**181 行 / 12514 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。

## 本轮新写的两条（正文逐字保留在本归档）

### P2-191: Go SDK 客户端的 **36 处守卫**里，**2 处**零钉住 —— 一处是 `GetObservation` 的空 `id` 校验，另一处是「空 BaseURL 回落到默认值」，而**后者有一个以它命名的测试、却什么都没断言**

- **Scope**: → [scope-9][a6]
- **Status**: ✅ **已修（397 轮，Go SDK 侧，只加测试 + 改文档，SDK 主源码零改动）** —— 新增
  `required_arguments_test.go`（**46** 个运行时用例）与一条内部测试（**1** 例），把 README 承诺的每一道守卫
  逐条钉住，并把两语 README 的「必填参数」表与运行时行为**绑在一起**（表与代码不一致即红）。

**普查怎么做的**

对 `client_methods.go` 与 `client_impl.go` 做**逐守卫单变量变异**：把一处守卫的条件改成**恒假**
（`if false && <cond>`），跑**全部九个模块**的套件（`test-all.sh` 按 go.mod 发现模块），再把文件**按字节**还原。
判据是「这道守卫若不再触发，原本全绿的套件还全绿吗」—— 全绿就说明**没有任何测试钉住它**。

**为什么用「条件恒假」而不是「删掉整块」**：有一处守卫位于 `for ... range ids` 循环内，
删掉它的块会让循环变量变成未使用 ⇒ **编译不过** ⇒ **编译失败的构建不能叫「绿」**（它什么都没测）。

**普查结果**

- **34** 处：变红 ⇒ 已有测试钉住。
- **2** 处：**仍然全绿** ⇒ 零钉住。

零钉住的两处：

| # | 文件 | 行 | 所属方法 | 条件 | 判读 |
|---|------|----|----------|------|------|
| 16 | `client_methods.go` | 152 | `GetObservation` | `strings.TrimSpace(id) == ""` | 零钉住 |
| 32 | `client_impl.go` | 125 | `NewClient` | `cfg.BaseURL == ""` | 零钉住 |

**第一处**：`GetObservation` 的空 `id` 校验。README 的表里写着「`GetObservation`｜`id`」，
但**没有任何测试**碰过它 —— 删掉它，**九个模块仍然全绿**。

**第二处更值得记**：`NewClient` 里 `if cfg.BaseURL == ""` 的回落。
仓库里**有一个以它命名的测试** —— `TestNewClient_EmptyBaseURL_UsesDefault` ——
但它**什么都没断言**：建了客户端、注释了一句「The client should have defaulted to
http://127.0.0.1:37777」、然后 `_ = client`，接着就 Close 了。
**名字承诺了一件测试没做的事**，比没有测试更危险：它让人以为这条路径被覆盖着。

⇒ 修法：回落值只存在**未导出的** `httpClient.config` 上，**外部测试包根本看不见**，
所以断言必须写在**内部测试包**里（`config_internal_test.go` 已经是这个模式）。
新断言把回落值与 `DefaultClientConfig().BaseURL` 比对，而外部测试再把
`DefaultClientConfig().BaseURL` 与 **README 发布的那串**比对 ⇒ **三处由构造相等，而不是三份字面量**。

**与 Java 侧（396 轮）的横向对照**

同一套方法在两个 SDK 上的结果差得很远，值得记下来：

| | Go SDK（本轮） | Java SDK（396 轮） |
|---|----------------|--------------------|
| 守卫总数 | **36** | **50** |
| 已有测试钉住 | **34** | **29** |
| 零钉住 | **2** | **21** |

⇒ Go 侧的覆盖本来就扎实得多；**Java 侧那 21 处零钉住是 Java 自己的缺口，不是 Go 的**。
但 Go 侧剩下的 **2 处**正好落在「表里写着、没人测」的位置上 —— 形式与 Java 侧一样。

**新测试怎么证明自己有用**

新测试不是「跑绿了就算」——它本身也过了变异检验：

| 变异 | 内容 | 读数 |
|------|------|------|
| M0 | 阴性对照：只加一行注释 | **0 红** |
| M1 | `GetObservation` 的空 `id` 守卫条件恒假 | **2 红**（父用例 + 子用例） |
| M2 | 空 BaseURL 回落条件恒假 | **1 红**（内部测试） |
| M3 | README 的必填参数表**删掉一行** | **1 红**（表 ↔ 运行时绑定） |
| M4 | README 发布的默认 base URL 改一个数字 | **1 红**（默认值绑定） |
| M5 | README 必填参数段**删掉那句例外说明** | **2 红**（散文钉住） |

**阳性对照**：新测试里有 **6** 个「参数合法时确实恰好发出一个请求」的用例。
没有它们，「没有发出请求」这个断言可能**空洞成立**（一个从不发请求的客户端也能满足它）。

### P2-192: Go README 的「必填参数在客户端校验」段对**整张表**声明返回 `ValidationError`，但表里 `UpdateObservation` 那一行的空更新抛的是**另一个 Go 类型** —— 而同一份文件 40 行前自己写明了这个区别

- **Scope**: → [scope-10][a7]
- **Status**: ✅ **已修（397 轮，文档方向，两语各一处）** —— 该段改为点名两处例外：
  「One row of the table is the exception: the empty-update case of `UpdateObservation` throws
  `*dto.ObservationUpdateValidationError` instead, and `IsValidationError` returns false for it」。

**为什么算失实陈述**

- Go README 的「空更新会被拒绝」一节（`:246`）写着：**「在 Go 里，这与必填参数检查抛出的错误不是同一个类型」**，
  并明确 `cortexmem.IsValidationError(err)` 对空更新返回 **false**。
- 同一份文件的「必填参数在客户端校验」一节（`:309`）却写着 **「Every argument below must be non-empty.
  The SDK returns a `ValidationError` and sends no request」** —— 而**它自己那张表**的
  `UpdateObservation` 行就写着「plus at least one field to change」。

⇒ 同一份文件里，40 行前说「不是同一个类型」，40 行后对自己表里的一行说「是 `ValidationError`」——
**两处自相矛盾**，而按前者写代码的人会捕到错的类型。
⇒ 修法：给该段加一处例外说明，并把这条说明**钉进测试**
（`TestRequiredArgumentsSectionNamesBothErrorTypes`：该段必须同时点名两种类型）。

## 附录 A：Go SDK 客户端守卫普查全表（36 处）

列含义：`#` = 守卫序号；`文件` / `行` = 守卫位置；`所属方法` = 该行**所在的方法**（由 pristine 源码现算，非手抄）；
`条件` = 被改成恒假的那个条件；`red` = 变异后**全模块套件**的失败数（**0 = 零钉住**）。

| # | 文件 | 行 | 所属方法 | 条件 | red |
|---|------|----|----------|------|-----|
| 1 | `client_methods.go` | 21 | `StartSession` | strings.TrimSpace(req.SessionID) == "" | 1 |
| 2 | `client_methods.go` | 24 | `StartSession` | strings.TrimSpace(req.ProjectPath) == "" | 1 |
| 3 | `client_methods.go` | 31 | `UpdateSessionUserId` | strings.TrimSpace(sessionID) == "" | 1 |
| 4 | `client_methods.go` | 34 | `UpdateSessionUserId` | strings.TrimSpace(userID) == "" | 1 |
| 5 | `client_methods.go` | 44 | `RecordObservation` | strings.TrimSpace(req.SessionID) == "" | 3 |
| 6 | `client_methods.go` | 47 | `RecordObservation` | strings.TrimSpace(req.ProjectPath) == "" | 1 |
| 7 | `client_methods.go` | 50 | `RecordObservation` | strings.TrimSpace(req.ToolName) == "" | 1 |
| 8 | `client_methods.go` | 59 | `RecordSessionEnd` | strings.TrimSpace(req.SessionID) == "" | 1 |
| 9 | `client_methods.go` | 62 | `RecordSessionEnd` | strings.TrimSpace(req.ProjectPath) == "" | 1 |
| 10 | `client_methods.go` | 71 | `RecordUserPrompt` | strings.TrimSpace(req.SessionID) == "" | 1 |
| 11 | `client_methods.go` | 74 | `RecordUserPrompt` | strings.TrimSpace(req.PromptText) == "" | 1 |
| 12 | `client_methods.go` | 77 | `RecordUserPrompt` | strings.TrimSpace(req.ProjectPath) == "" | 1 |
| 13 | `client_methods.go` | 88 | `RetrieveExperiences` | strings.TrimSpace(req.Task) == "" | 3 |
| 14 | `client_methods.go` | 99 | `BuildICLPrompt` | strings.TrimSpace(req.Task) == "" | 1 |
| 15 | `client_methods.go` | 106 | `Search` | strings.TrimSpace(req.Project) == "" | 3 |
| 16 | `client_methods.go` | 152 | `GetObservation` | strings.TrimSpace(id) == "" | 0 |
| 17 | `client_methods.go` | 166 | `GetObservationsByIds` | len(ids) == 0 | 4 |
| 18 | `client_methods.go` | 169 | `GetObservationsByIds` | len(ids) > 100 | 1 |
| 19 | `client_methods.go` | 173 | `GetObservationsByIds` | strings.TrimSpace(id) == "" | 2 |
| 20 | `client_methods.go` | 184 | `TriggerRefinement` | strings.TrimSpace(projectPath) == "" | 4 |
| 21 | `client_methods.go` | 195 | `SubmitFeedback` | strings.TrimSpace(observationID) == "" | 1 |
| 22 | `client_methods.go` | 198 | `SubmitFeedback` | strings.TrimSpace(feedbackType) == "" | 1 |
| 23 | `client_methods.go` | 212 | `UpdateObservation` | strings.TrimSpace(observationID) == "" | 4 |
| 24 | `client_methods.go` | 225 | `DeleteObservation` | strings.TrimSpace(observationID) == "" | 3 |
| 25 | `client_methods.go` | 234 | `GetQualityDistribution` | strings.TrimSpace(projectPath) == "" | 1 |
| 26 | `client_methods.go` | 268 | `TriggerExtraction` | strings.TrimSpace(projectPath) == "" | 2 |
| 27 | `client_methods.go` | 276 | `GetLatestExtraction` | strings.TrimSpace(projectPath) == "" | 1 |
| 28 | `client_methods.go` | 279 | `GetLatestExtraction` | strings.TrimSpace(templateName) == "" | 1 |
| 29 | `client_methods.go` | 291 | `GetExtractionHistory` | strings.TrimSpace(projectPath) == "" | 1 |
| 30 | `client_methods.go` | 294 | `GetExtractionHistory` | strings.TrimSpace(templateName) == "" | 1 |
| 31 | `client_methods.go` | 297 | `GetExtractionHistory` | limit < 0 | 2 |
| 32 | `client_impl.go` | 125 | `NewClient` | cfg.BaseURL == "" | 0 |
| 33 | `client_impl.go` | 139 | `NewClient` | cfg.MaxRetries < 1 | 2 |
| 34 | `client_impl.go` | 152 | `NewClient` | cfg.Timeout < 100*time.Millisecond | 1 |
| 35 | `client_impl.go` | 155 | `NewClient` | cfg.ConnectTimeout < 100*time.Millisecond | 1 |
| 36 | `client_impl.go` | 158 | `NewClient` | cfg.RetryBackoff < 100*time.Millisecond | 1 |

**读法**：`red = 0` 的两行就是本轮新测试补上的覆盖面。

**形态分布**：参数校验 **31** 处（`client_methods.go`）、
配置归一化 **5** 处（`client_impl.go`：BaseURL 回落、`MaxRetries` 下限、
三个超时的 **100ms** 下限）。

## 附录 B：普查的做法，以及它自己踩过的坑

**做法**：从 `git show HEAD:<path>` 取**不可变基准** → 每次只把**一处**守卫的条件改成恒假 →
在 SDK 根目录跑 `bash test-all.sh`（**按 go.mod 发现全部九个模块**，不是只跑 `go test ./...`）
→ 读输出里的 `--- FAIL:` 行 → 立即还原。

**坑一：Go SDK 是九个独立 module，`go test ./...` 只覆盖两个。**
根目录的 `./...` 只到 **core + dto**；`eino` / `genkit` / `langchaingo` /
`examples/http-server` 各有自己的 go.mod，必须**在各自目录**跑。
`test-all.sh` 按 go.mod 发现模块，本轮直接用它，避免漏跑。

**坑二：编译不过的构建不能算「绿」。**
第一版变异想删掉整块守卫，其中一处位于 `for ... range ids` 内 ⇒ 循环变量未使用 ⇒ **编译失败**
⇒ 读数会是「包失败」而不是「用例红」。**改用「条件恒假」**后，所有形态都编译得过。

**坑三：Go 的计数口径必须用 `go test -json`，且以 (Package, Test) 为键。**
本轮根模块 **371**（core **304** + dto **67**），其余四个模块 **70**（eino 8 + genkit 13 +
langchaingo 12 + `examples/http-server` 37），合计 **441**。
静态数 `func Test` 声明数与运行时的子用例数不是一回事。

**坑四：拷进隔离副本后必须逐字节核对**（396 轮已记，仍适用）。

## 压缩记录

本轮**未迁出任何条目**：按 **C10** 复查，已结案条目里可压缩的非标题内容只剩 **1886** 字节
（且全是最近两轮的 `Scope` / `Evidence` 导航指针），压缩手段**已彻底枯竭**
⇒ 压缩杠杆 **C6 / C7 / C10** 已用尽（**这是第三次复测同一结论**）。

全轮净变化：工作文件 **1294 行 / 145594 字节** → **1307 行 / 148349 字节**（净 **+13 行 / +2755 字节**）。
本轮新增两条（**P2-191 / P2-192**）的正文全部在本归档，工作文件侧只留标题与指针。
