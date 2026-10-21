# 后端审查证据归档 -97（2026-10-22）

> 第 396 轮（Java SDK + SDK README，doc round 295）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1294 行 / 145594 字节**）。
> 本文档体量：**211 行 / 15293 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。

## 本轮新写的三条（正文逐字保留在本归档）

### P2-188: Java SDK 客户端的 **50 处参数守卫**里，**21 处**从未被任何测试钉住 —— 而 README 用一张十八行的表向调用方承诺这些参数「非空白即拒绝、且不发出任何请求」

- **Scope**: → [scope-9][a6]
- **Status**: ✅ **已修（396 轮，Java SDK 侧，只加测试 + 改文档，SDK 主源码与后端零改动）** —— 新增
  `CortexMemClientRequiredArgumentsTest`（**59** 例），把 README 承诺的每一道守卫逐条钉住，并把
  两语 README 的「必填参数」表与运行时行为**绑在一起**（表与代码不一致即红）。

**普查怎么做的**

对 `CortexMemClientImpl`（1056 行，sha256 `f97b37ee62c15d128b2ef40f50d5f190bef82d5fc6a77d327f94af50fb910b5f`）做**逐守卫单变量变异**：
把一处守卫整行删掉，跑**整个 client 模块套件**，再把文件**按字节**还原。
判据是「删掉这道守卫后，原本全绿的套件还全绿吗」—— 全绿就说明**没有任何测试钉住它**。

守卫按形态分四类，共 **50** 处：`requireNonBlank`、`requireAbsolutePath`、
`Objects.requireNonNull`、`if (...) { throw new IllegalArgumentException(...); }`
—— 后一类含 **3** 处构造器校验与 **2** 处 helper 体。

**普查结果（改测试之前）**

- **29** 处：删掉后套件变红 ⇒ 已有测试钉住。
- **21** 处：删掉后套件**仍然全绿** ⇒ **零钉住**。

零钉住的 21 处按形态分：

| 形态 | 处数 | 所在方法 |
|------|------|----------|
| `if (...) { throw ... }` | 3 | 构造器（`baseUrl` 空白 / `connectTimeout` 为负 / `readTimeout` 超过 24.8 天） |
| `Objects.requireNonNull` | 10 | startSession、recordObservation、recordSessionEnd、recordUserPrompt、retrieveExperiences、buildICLPrompt、updateObservation、search、listObservations、getObservationsByIds |
| `requireNonBlank` | 8 | startSession、retrieveExperiences、buildICLPrompt、getQualityDistribution、getExtractionHistory、search |

**这不是「守卫不存在」，是「守卫在，但改了没人发现」**：这 21 处守卫的行为在改动前后完全不变，
只有**回归保护**缺失。所以修法是**加测试**，不是改实现。

**同族里唯一没被钉住的那一个**

`getLatestExtraction` 的 `projectPath` / `templateName` 两道守卫**被钉住**，
`getExtractionHistory` 的**同两道**守卫**零钉住** —— 同名同型的两个方法，只差一个，
差别只在测试有没有碰过它。

**两层校验，README 没说**

SDK 在**两处**拒绝空白必填参数，且措辞不同：

| 层 | 位置 | 措辞 |
|----|------|------|
| DTO | `SessionStartRequest.Builder.build()` | `sessionId is required (set via sessionId())` |
| DTO | `SessionStartRequest.Builder.build()` | `projectPath is required (set via projectPath())` |
| DTO | `SearchRequest.Builder.build()` | `project is required (set via project())` |
| DTO | `SearchRequest` 紧凑构造器 | `project must not be null or blank` |
| 客户端 | `requireNonBlank` / `requireAbsolutePath` | `sessionId must not be null or blank` |

⇒ README 那张「必填参数」表把两层混成了一层。新测试**分别**钉住两层（DTO 4 例 + 客户端 44 例）。

**一处死代码（只记录，不动）**

`CortexMemClientImpl:453` 的 `requireNonBlank(request.project(), "project")` **完全不可达**：
`SearchRequest` 的紧凑构造器用**逐字相同**的措辞先拒了。删掉客户端这行，行为**一模一样**
（复验读数 red = 0，和改动前一致）。这属于契约面（公开方法签名与异常类型不变），**不擅自删除**，只在此记录。

**新测试怎么证明自己有用**

新测试不是「跑绿了就算」——它本身也过了变异检验：把上述 **21** 处零钉住的守卫
逐个删掉，**新测试必须变红**。读数：**20** 处变红，
唯一仍为 0 的是上面那处死代码（`search` 的 `requireNonBlank(request.project(), "project");`）。

**阳性对照**：新测试里有 **5** 个「参数合法时确实恰好发出一个请求」的用例。
没有它们，「没有发出请求」这个断言可能**空洞成立**（一个从不发请求的客户端也能满足它）。

### P2-189: Java README 的跨家断言「Go、Python 和 JS 三家 SDK 强制的是**完全相同的一组**规则」是**失实陈述** —— Java 另有一道「`projectPath` 必须是绝对路径」的规则，而同一份文件 26 行后自己写着「有一条规则只有 Java 有」

- **Scope**: → [scope-10][a7]
- **Status**: ✅ **已修（396 轮，文档方向，两语各一处）** —— 改为与 Go 英文版同构的措辞
  （「同一组『非空白』检查——只有一处例外：**Java 还额外要求 `projectPath` 必须是绝对路径**」）；
  另补一句实测过的行为（请求对象为 `null` 时抛 `NullPointerException`，与空白检查是**两道**不同的检查）。

**为什么算失实陈述**

跨家断言共 **8** 句（JS / Python / Go 各 2 句 × 两语，加 Java 双语 2 句）。
其中 **6** 句（JS / Python / Go）都带「一处例外」的限定，**唯独 Java 的双语两句没有**。
而 Java 自己的 README 在 26 行后写着「One capture-side rule is Java-only」——
同一份文件里，一处说三家完全相同，另一处说有一条规则只有 Java 有，**自相矛盾**。

实测确认：三家的源码里**没有**任何绝对路径校验 ——
Go SDK 源码零命中、Python SDK 源码零命中、
JS SDK 只在 `node_modules` 的类型声明里有 `path.isAbsolute`，自己的源码零命中。
⇒ **绝对路径是 Java 独有的**。

### P2-190: Java README 首屏的单元测试数**已过期** —— 写着「250（197 + 46 + 7），measured 2026-10-21」，而本轮加测后实际是 **309**

- **Scope**: → [scope-10][a7]
- **Status**: ✅ **已修（396 轮，文档方向，两语各一处）** —— 改为
  「309（256 client + 46 spring-ai + 7 starter），measured 2026-10-22」。

**数法**：数法为**运行时实测**，且用**两种口径**互相印证 ——
maven 汇总行与 surefire XML 的逐类 `tests=` 属性。两者都读出 **256 / 46 / 7**，**吻合**才写进去。

| 模块 | maven 汇总行 | surefire XML（逐类求和） | 类别数 |
|------|--------------|--------------------------|--------|
| `cortex-mem-client` | `Tests run: 256, Failures: 0, Errors: 0, Skipped: 0` | **256** / 0 / 0 / 0 | 5 |
| `cortex-mem-spring-ai` | `Tests run: 46, Failures: 0, Errors: 0, Skipped: 0` | **46** / 0 / 0 / 0 | 8 |
| `cortex-mem-starter` | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0` | **7** / 0 / 0 / 0 | 2 |

加测前是 197 / 46 / 7 = **250**，本轮新增 **59** 例 ⇒ 256 / 46 / 7 = **309**。

## 附录 A：Java SDK 客户端守卫普查全表（50 处）

列含义：`#` = 守卫序号；`行` = 守卫所在行号；`方法` = 该行**所在的方法**（由 pristine 源码现算，非手抄）；
`原套件 red` = 删掉该守卫后**原** client 模块套件的失败数（**0 = 零钉住**）；
`新测试 red` = 删掉该守卫后**新测试**的失败数（`—` = 未复验）。

| # | 行 | 方法 | 守卫 | 原套件 red | 新测试 red |
|---|----|------|------|-----------|-----------|
| 1 | 61 | `构造器` | `if (baseUrl == null \|\| baseUrl.isBlank()) {` | 0 | 1 |
| 2 | 65 | `构造器` | `if (connectTimeout == null \|\| connectTimeout.isNegative()) {` | 0 | 1 |
| 3 | 70 | `构造器` | `if (readTimeoutMs > Integer.MAX_VALUE) {` | 0 | 1 |
| 4 | 104 | `startSession` | `Objects.requireNonNull(request, "request must not be null");` | 0 | 1 |
| 5 | 105 | `startSession` | `requireNonBlank(request.sessionId(), "sessionId");` | 0 | 1 |
| 6 | 106 | `startSession` | `requireNonBlank(request.projectPath(), "projectPath");` | 0 | 1 |
| 7 | 123 | `recordObservation` | `Objects.requireNonNull(request, "request must not be null");` | 0 | 1 |
| 8 | 124 | `recordObservation` | `requireNonBlank(request.sessionId(), "sessionId");` | 2 | — |
| 9 | 125 | `recordObservation` | `requireNonBlank(request.projectPath(), "projectPath");` | 1 | — |
| 10 | 126 | `recordObservation` | `requireAbsolutePath(request.projectPath(), "projectPath");` | 2 | — |
| 11 | 127 | `recordObservation` | `requireNonBlank(request.toolName(), "toolName");` | 1 | — |
| 12 | 139 | `recordSessionEnd` | `Objects.requireNonNull(request, "request must not be null");` | 0 | 1 |
| 13 | 140 | `recordSessionEnd` | `requireNonBlank(request.sessionId(), "sessionId");` | 1 | — |
| 14 | 141 | `recordSessionEnd` | `requireNonBlank(request.projectPath(), "projectPath");` | 2 | — |
| 15 | 142 | `recordSessionEnd` | `requireAbsolutePath(request.projectPath(), "projectPath");` | 2 | — |
| 16 | 154 | `recordUserPrompt` | `Objects.requireNonNull(request, "request must not be null");` | 0 | 1 |
| 17 | 155 | `recordUserPrompt` | `requireNonBlank(request.sessionId(), "sessionId");` | 1 | — |
| 18 | 156 | `recordUserPrompt` | `requireNonBlank(request.promptText(), "promptText");` | 1 | — |
| 19 | 157 | `recordUserPrompt` | `requireNonBlank(request.projectPath(), "projectPath");` | 2 | — |
| 20 | 158 | `recordUserPrompt` | `requireAbsolutePath(request.projectPath(), "projectPath");` | 2 | — |
| 21 | 185 | `retrieveExperiences` | `Objects.requireNonNull(request, "request must not be null");` | 0 | 1 |
| 22 | 186 | `retrieveExperiences` | `requireNonBlank(request.task(), "task");` | 0 | 2 |
| 23 | 215 | `buildICLPrompt` | `Objects.requireNonNull(request, "request must not be null");` | 0 | 1 |
| 24 | 216 | `buildICLPrompt` | `requireNonBlank(request.task(), "task");` | 0 | 2 |
| 25 | 234 | `triggerRefinement` | `requireNonBlank(projectPath, "projectPath");` | 1 | — |
| 26 | 248 | `submitFeedback` | `requireNonBlank(observationId, "observationId");` | 1 | — |
| 27 | 249 | `submitFeedback` | `requireNonBlank(feedbackType, "feedbackType");` | 1 | — |
| 28 | 282 | `getQualityDistribution` | `requireNonBlank(projectPath, "projectPath");` | 0 | 2 |
| 29 | 302 | `updateObservation` | `requireNonBlank(observationId, "observationId");` | 1 | — |
| 30 | 303 | `updateObservation` | `Objects.requireNonNull(update, "update must not be null");` | 0 | 1 |
| 31 | 305 | `updateObservation` | `if (update.isEmpty()) {` | 1 | — |
| 32 | 319 | `deleteObservation` | `requireNonBlank(observationId, "observationId");` | 1 | — |
| 33 | 369 | `getLatestExtraction` | `requireNonBlank(projectPath, "projectPath");` | 1 | — |
| 34 | 370 | `getLatestExtraction` | `requireNonBlank(templateName, "templateName");` | 1 | — |
| 35 | 389 | `getExtractionHistory` | `requireNonBlank(projectPath, "projectPath");` | 0 | 1 |
| 36 | 390 | `getExtractionHistory` | `requireNonBlank(templateName, "templateName");` | 0 | 1 |
| 37 | 391 | `getExtractionHistory` | `if (limit < 0) {` | 1 | — |
| 38 | 419 | `updateSessionUserId` | `requireNonBlank(sessionId, "sessionId");` | 1 | — |
| 39 | 420 | `updateSessionUserId` | `requireNonBlank(userId, "userId");` | 1 | — |
| 40 | 435 | `triggerExtraction` | `requireNonBlank(projectPath, "projectPath");` | 1 | — |
| 41 | 452 | `search` | `Objects.requireNonNull(request, "request must not be null");` | 0 | 1 |
| 42 | 453 | `search` | `requireNonBlank(request.project(), "project");` | 0 | 0 |
| 43 | 495 | `listObservations` | `Objects.requireNonNull(request, "request must not be null");` | 0 | 1 |
| 44 | 546 | `getObservation` | `requireNonBlank(observationId, "observationId");` | 2 | — |
| 45 | 557 | `getObservationsByIds` | `Objects.requireNonNull(ids, "ids must not be null");` | 0 | 1 |
| 46 | 558 | `getObservationsByIds` | `if (ids.isEmpty()) {` | 1 | — |
| 47 | 561 | `getObservationsByIds` | `if (ids.size() > 100) {` | 1 | — |
| 48 | 565 | `getObservationsByIds` | `if (ids.get(i) == null \|\| ids.get(i).isBlank()) {` | 3 | — |
| 49 | 866 | `requireNonBlank` | `if (value == null \|\| value.isBlank()) {` | 23 | — |
| 50 | 882 | `requireAbsolutePath` | `if (!java.nio.file.Paths.get(value).isAbsolute()) {` | 6 | — |

**读法**：`原套件 red = 0` 的行就是本轮新测试补上的覆盖面。
`新测试 red` 仍为 `0` 的唯一一行是 `search` 的 `project` 守卫 —— 那处是死代码（见上）。

## 附录 B：普查的做法，以及它自己踩过的五个坑

**做法**：从 `git show HEAD:<path>` 取**不可变基准**（逐字节 sha256 校验）→ 每次只删**一处**守卫 →
在 client 模块跑 `mvn -o test` → 读最后一个 `Tests run:` 汇总行的 `Failures + Errors` → 立即还原。

**坑一：读正在被改写的源文件会读到撕裂内容。**
第一版普查脚本在后台跑，我同时把新测试文件放进模块并读了 impl 的中途状态，
据此得出「`recordObservation` 缺 `projectPath` 守卫」的**假结论**。
`git show HEAD:` 的基准真相是：第 125 行确为 `requireNonBlank(projectPath)`，第 126 行才是
`requireAbsolutePath`。⇒ **扫描进程正在改写的文件不可读。**

**坑二：扫描期间往同模块加测试文件，会让后续所有读数变成编译错误。**
新测试第一版漏了一个 import，从第 16 例起全部 `COMPILATION ERROR`。
**编译失败的构建不能叫「绿」** —— 它什么都没测。作废、从干净基线重跑。

**坑三：变异 harness 的工作目录必须按「祖先目录名」定位。**
第一版用 `SRC.parents[5]`，指向 `.../src` 而不是 `.../cortex-mem-client` ⇒ mvn 找不到 POM ⇒
**50 个变异全部假报 exit=1**（手工复现真实读数是 197/197 全绿）。
⇒ 按目录**名字**找祖先（`next(p for p in SRC.parents if p.name == "cortex-mem-client")`）。

**坑四：读管道后的退出码要用 `PIPESTATUS`。**
`$?` 读到的是管道末端 `tail` 的 0，不是 `mvn` 的退出码。

**坑五（附带）：拷贝进隔离副本后必须逐字节核对。**
新测试改完三处后拷进 `/tmp/p396-sdkcopy/`，`cp` 之后**没有**复核内容，
副本里跑出来的仍是**改动前**的行为（两处断言红）。`md5` 对拍才发现副本是旧的。
⇒ **拷贝 = 未验证的输入**；`md5` 对拍是最低成本的一步。

## 压缩记录

本轮**未迁出任何条目**：按 **C10** 复查，已结案条目里可压缩的非标题内容只剩 **774** 字节
（且全是上一轮的 `Scope` / `Evidence` 导航指针），压缩手段**已彻底枯竭** ⇒ 压缩杠杆 **C6 / C7 / C10** 已用尽。

全轮净变化：工作文件 **1275 行 / 142603 字节** → **1294 行 / 145594 字节**（净 **+19 行 / +2991 字节**）。
本轮新增三条（**P2-188 / P2-189 / P2-190**）的正文全部在本归档，工作文件侧只留标题与指针。
