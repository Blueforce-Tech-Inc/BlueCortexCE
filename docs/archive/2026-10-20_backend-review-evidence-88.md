# 后端审查证据归档 -88（2026-10-20）

> 第 387 轮（JS/TS SDK + SDK README，doc round 286）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1274 行 / 148339 字节**）。
> 本文档体量：**316 行 / 22370 字节**。

## 本轮的两条主线

① **三处「契约零测试钉住」（代码方向，JS 侧）** —— 形状与 P2-147 / P2-156 / P2-160 同型：
   不是「某个方法没测」，而是**某条契约**没人钉。每一处都用**缺口证明**定位
   （把行为改坏 ⇒ 全量测试仍全绿），再用**变异测试**证明新用例能红。

② **两条跨家断言在 JS README 里失实（文档方向）** —— 按**断言清扫**找出散落位置
   （同一句断言在仓库里出现多处时逐处找齐、逐处判定），用 `git log -S` 判定
   「写入即假」还是「事后漂移」。

---

## P2-162: JS SDK 的**三个** fire-and-forget 捕获方法里，只有 `recordObservation` 的「吞错」契约被用例钉住 —— 另两个改成「抛错」路径后，全量 **266** 个测试**仍然全绿**

- **Scope**: → [scope-9][a6]
- **实现（三个是同族）**：`src/client.ts` 的 `recordObservation`(`:177`) / `recordSessionEnd`(`:191`) /
  `recordUserPrompt`(`:204`) **都**走 `doFireAndForget`(`:764`)；该方法内部自带重试循环，
  失败时 `return`（吞错）—— 三者的「后端失败 ⇒ 吞错」是同一份契约。
- **缺口（用例只覆盖了同族里的一个）**：`src/__tests__/client.test.ts` 的 `fire-and-forget`
  区块共 **6** 个用例（`should not throw on recordObservation failure`、
  `should swallow non-retryable errors immediately`、`should retry on transient 5xx (503)`、
  `should not retry on 500 (non-retryable)`、`should retry on timeout AbortError`、
  `maxRetries should count total attempts`）—— **全部只驱动 `recordObservation`**。
  `recordSessionEnd` 与 `recordUserPrompt` 的吞错契约**一个用例都没有**。
- **缺口证明（把行为改坏，全量仍全绿）**：把这两个方法改成直接
  `await this.requestNoContent(...)`（抛错路径）⇒ 全量 **266** 个测试**仍然全绿**。
  两个变量**各自单独注入**（不叠加），保证归因是单变量。
- **后果**：`recordSessionEnd` / `recordUserPrompt` 的吞错契约一旦回归，测试**不会响**。
  而捕获路径的吞错是**设计意图**（Agent 管道不能被记忆写入失败打断）——
  Go 的注释写死了这条（「NOT fire-and-forget: this is an explicit user action, errors must
  propagate.」说的是**非**捕获路径），Java 的 Javadoc 写的是
  「A backend failure is logged, never thrown to the caller — the capture path must not
  break the caller's pipeline.」
- **修法（本轮已修，JS 侧，只加测试，实现一行未改）**：新增 **3** 个用例 ——
  ① `recordSessionEnd` 对 500 吞错；② `recordUserPrompt` 对 500 吞错；
  ③ `recordUserPrompt` 对 503 恒失败（`maxRetries: 3`）吞错。
  每个用例都**同时**断言两半：「请求确实发出」（`toHaveBeenCalledOnce()` / `callCount === 3`）
  与「错误被吞掉」（await 不抛）。
  ⭐ **只断言「不抛」是不够的**：客户端若压根没发请求，「不抛」同样成立 ⇒ 每次失败都会被
  记成「吞错成功」。
- **变异测试（证明新用例真能红）**：
  - 变异 A：`recordSessionEnd` 改抛错 ⇒ 恰好 **1** 红（新用例），其余 **268** 绿；
  - 变异 B：`recordUserPrompt` 改抛错 ⇒ 恰好 **2** 红（两个新用例），其余 **267** 绿。
  两次变异按 sha256 `194520e8b10325649dd3614c00826a770b7619682437398a29162cf008343c7a`
  **逐字节还原**（还原后 sha256 一致，`git diff` 只剩测试文件）。
- **与 P2-160 的关系**：Python 侧**完全同型**（7 个 `_fire_and_forget` 用例全部只驱动
  `record_observation`），第 386 轮已修（3 个用例）。本条是 JS 侧结转。
  Go / Java 侧待各自轮到时结转。

---

## P2-163: 四家 SDK README 的「空更新错误消息**完全相同**」—— **8 处全失实**：四家的**错误类型与前缀各不相同**，只有消息**主体**逐字相同

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **按断言清扫（同一断言散落 8 处 = 4 家 × 双语）**：

  | README | 展示的消息 | 声称相同的对象 | 判定 |
  |---|---|---|---|
  | `js-sdk/cortex-mem-js/README.md:204` | `cortex-ce: validation error on update: …` | Go, Java, Python | Go ❌、Java ❌、Python ✅ |
  | `js-sdk/cortex-mem-js/README-zh-CN.md:197` | 同上 | Go, Java, Python | Go ❌、Java ❌、Python ✅ |
  | `python-sdk/cortex-mem-python/README.md:258` | 同上 | Go, Java, JS | Go ❌、Java ❌、JS ✅ |
  | `python-sdk/cortex-mem-python/README-zh-CN.md:247` | 同上 | Go, Java, JS | Go ❌、Java ❌、JS ✅ |
  | `go-sdk/cortex-mem-go/README.md:252` | `cortex-ce: ObservationUpdate validation error: …` | Java, Python, JS | 三家**全 ❌** |
  | `go-sdk/cortex-mem-go/README-zh-CN.md:242` | 同上 | Java, Python, JS | 三家**全 ❌** |
  | `cortex-mem-spring-integration/README.md:377` | `at least one field must be provided for update` | Go, Python, JS | 三家**全 ❌** |
  | `cortex-mem-spring-integration/README-zh-CN.md:381` | 同上 | Go, Python, JS | 三家**全 ❌** |

  ⇒ **8 处全部至少有一半失实**（每家展示的都是自己那家的完整消息，然后声称另三家相同）。
- **四家的实际形态（逐个读实现，不靠文档）**：

  | SDK | 错误类型 | 完整消息 | 来源 |
  |---|---|---|---|
  | JS | `ValidationError` | `cortex-ce: validation error on update: at least one field must be provided for update` | `client.ts:379` + `errors.ts:14` |
  | Python | `ValidationError` | 同上 | `client.py:701` + `error.py:38` |
  | Go | `dto.ObservationUpdateValidationError` | `cortex-ce: ObservationUpdate validation error: at least one field must be provided for update` | `dto/observation.go:143` + `:162` |
  | Java | `IllegalArgumentException` | `at least one field must be provided for update` | `CortexMemClientImpl.java:306` |

- **规则那一半四家都成立** ✅：空更新被拒、**不发请求**、消息**主体**
  `at least one field must be provided for update` 逐字相同。
  程序化对拍：四家源码里该主体各命中 1 次，主体集合大小 = **1**。
  ⭐ 失实的**不是**「主体相同」，而是把它写成了「**完整消息**相同」。
- **写入即假（`git log -S` 判定）**：8 处**同源于一个提交** `f188553`
  （`fix(python-sdk): reject an update that sets no field, like the other SDKs`，
  **2026-10-02 21:11:55**）。在该提交当时：
  - Go 的 `ObservationUpdateValidationError.Error()` **已经**是
    `"cortex-ce: ObservationUpdate validation error: " + e.Message`；
  - Java **已经**是 `throw new IllegalArgumentException("at least one field must be provided for update")`；
  - JS / Python **已经**是 `cortex-ce: validation error on ${field}: ${message}`。

  ⇒ 写下这句话时四家就已各不相同 ⇒ **写入即假**，不是事后漂移。
- **修法（本轮只改 JS 双语 2 处；Go / Python / Java 各 2 处结转）**：把断言改准 ——
  「**规则**相同（空更新在任何请求发出之前就被拒）、**消息主体**在四家逐字相同，
  但**完整字符串**与**错误类型**各不相同」（四行对照表），并点明两条实务后果：
  「按整条消息匹配的调用方需要先剥掉前缀」+「按错误类型匹配的调用方要知道 Java 抛的是
  `IllegalArgumentException` 而不是 `ValidationError`」。
  ⭐ 修的是**失实陈述**（文档描述了一个从未成立的事实），属可单方面修的例外；
  **不改任何 SDK 行为**。

---

## P2-164: JS SDK 双语 README 说「另三家统一称项目参数为 `project_path`」—— **两种读法下都失实**：四家捕获方法的 **wire 字段都是 `cwd`**，而 SDK 层叫法是 Go `ProjectPath` / Python `project_path` / Java `projectPath`

- **Scope**: → [scope-9][a6]
- **四处断言**：`js-sdk/cortex-mem-js/README.md:245-246`（「the other three call it
  `project_path`」）、同文件 `:271`（「The other three SDKs call it `project_path`
  throughout.」）、`README-zh-CN.md:230`（「Go、Java、Python 三家 SDK 强制的是完全相同的一组
  规则」的后半句那类措辞）、`README-zh-CN.md:254`（「另外三家 SDK 统一称其为 `project_path`」）。
  ⭐ 与 **P2-159** 是同一族跨家断言但**不是同一句** —— P2-159 讲「强制哪些参数」，
  本条讲「同一个字段叫什么」。
- **事实（程序化对拍，逐家读实现）**：四家捕获方法的 **wire 项目字段都是 `cwd`**：

  | SDK | 捕获方法的 wire 字段 | 来源 |
  |---|---|---|
  | JS | `cwd` | `dto/observation.ts:24`、`dto/session.ts:40`、`:54` |
  | Go | `json:"cwd"` | `dto/observation.go:88`、`dto/session.go:34`、`:47` |
  | Python | `"cwd": project_path` | `client.py:311`、`:349`、`:385` |
  | Java | `map.put("cwd", projectPath)` | `ObservationRequest.java:68`、`SessionEndRequest.java:35`、`UserPromptRequest.java:42` |

  ⭐ **Java 这一格是本轮差点判错的地方**：Java 的请求 DTO **没有** `@JsonProperty`，
  record 组件名是 `projectPath` ⇒ 若走 Jackson 默认策略，wire 上会是 camelCase 的
  `projectPath`，而后端只读 `cwd` ⇒ 捕获会**静默丢掉项目**。
  查下去才发现 Java SDK **不走 Jackson 序列化 DTO**，而是调用**手写的 `toWireFormat()`**
  （`CortexMemClientImpl.java:128`/`:143`/`:159` 三处 `.body(request.toWireFormat())`），
  它显式 `map.put("cwd", projectPath)` ⇒ wire 名是 `cwd` ✅ **与后端完全匹配**。
  ⭐ **「没匹配上」≠「不成立」** —— 先怀疑自己的判据，再怀疑被证明物。
- **SDK 层的叫法（另一半事实）**：JS `cwd`、Go `ProjectPath`、Python `project_path`、
  Java `projectPath` ⇒ 「另三家统一称其为 `project_path`」**对 Go 与 Java 都失实**
  （只有 Python 真的这么叫）。
- **两种读法都失实**：按 **wire** 读 ⇒ 另三家在捕获方法上发的也是 `cwd`，不是 `project_path`；
  按 **SDK 参数名** 读 ⇒ Go 是 `ProjectPath`、Java 是 `projectPath`。
  而 `:271` 那句还带一个「throughout」⇒ 更强地失实：另三家的**管理类**方法用的是
  `project`（Go/Python）或 `projectPath`（Java），从来不是 `project_path`。
- **写入即假**：四处均来自 `6902968`（`docs: document the required-argument contract in
  all four SDK READMEs`，**2026-10-03**）—— 与 **P2-159 是同一个提交**。
- **修法（本轮已修，JS 双语 4 处）**：改成「四家在捕获方法上发的 **wire 字段都是 `cwd`**；
  差别只在 **SDK 层的叫法** —— Go `ProjectPath`、Python `project_path`、Java `projectPath`」。
  ⭐ 顺带去重：表格前那两段与表格后那两段说了同一件事，本轮把前者并入后者。

---

## P2-165: JS 侧空更新的**完整消息与错误类型**零测试钉住 —— 文档写死了那句话，测试却只做子串匹配

- **Scope**: → [scope-9][a6]
- **缺口**：`src/__tests__/client.test.ts` 的 `should reject empty update` 与
  `should reject update with all-undefined fields` 两例都写成
  `.rejects.toThrow('at least one field')` ⇒ **只钉住消息主体**；
  `cortex-ce: validation error on update: ` 这个**前缀**与 `ValidationError` 这个
  **错误类型**都**零钉住**。
- **为什么本轮要紧**：P2-163 修好后，JS README **明确写死了**这三样（错误类型列 + 完整消息列），
  而这三条断言**没有任何测试钉住** ⇒ 文档会漂移而测试不会响。
  ⭐ 这与 P2-147 / P2-156 / P2-160 是同一族的**第四种形态**：
  前三种是「某个方法没测」，这一种是「**某个字段**（完整消息 / 错误类型）没被钉住」。
- **缺口证明（两处，改坏都全绿）**：
  - 把 `client.ts:379` 的消息截短成 `at least one field must be provided` ⇒ 全量 **270** 个测试**仍全绿**
    （旧用例子串仍匹配）；
  - 把错误类型从 `ValidationError` 换成 `APIError` ⇒ 同样全绿。
- **修法（本轮已修，JS 侧，只加测试）**：新增 **1** 个用例，同时断言
  ① `err instanceof ValidationError`；② `err.field === 'update'`；
  ③ `err.message` **逐字等于** README 写的那串；
  ④ 不发请求（`not.toHaveBeenCalled()`）。
- **变异测试（归因分离的关键）**：
  - 变异 1（截短消息）⇒ 恰好 **1** 红（**新用例**），其余 **269** 绿，
    而**两个旧用例仍然全绿** ⇒ 证明旧用例确实看不见这个漂移；
  - 变异 2（换成 `APIError`）⇒ 恰好 **1** 红，其余 **269** 绿。
  两次变异按 sha256 `194520e8b10325649dd3614c00826a770b7619682437398a29162cf008343c7a` 还原。

---

## P2-166: JS SDK 的「**只有捕获路径重试**」这条契约**零测试钉住**，而 JS README 也**从未说明**它 —— 而 Java 侧的重试范围**大得多**

- **Scope**: → [scope-9][a6]
- **事实（四家重试矩阵，逐个读实现）**：

  | SDK | 重试的方法 | 数量 |
  |---|---|---|
  | JS | 3 个捕获：`doFireAndForget`（`client.ts:182`/`:195`/`:209`） | **3** |
  | Python | 3 个捕获：`_fire_and_forget`（`client.py:324`/`:353`/`:389`） | **3** |
  | Go | 3 个捕获：`doFireAndForget`（`client_methods.go:53`/`:65`/`:80`） | **3** |
  | **Java** | 3 捕获 + `triggerRefinement`(`:235`) + `submitFeedback`(`:258`) + `updateObservation`(`:308`) + `deleteObservation`(`:320`) + `getLatestExtraction`(`:371`) + `getExtractionHistory`(`:394`) + `triggerExtraction`(`:436`) | **10** |

  ⇒ Java 重试 **7** 个非捕获方法，另三家 **0** 个。
  JS 侧的机制：`doFetch`（`client.ts:634`）与 `requestJSON`（`:713`）/ `requestNoContent`（`:730`）
  **都没有重试**；重试循环**只存在于** `doFireAndForget`（`:764`）内部。
- **缺口证明**：给 `startSession`（非捕获，走 `requestJSON`）加一个重试循环
  ⇒ 全量 **270** 个测试**仍然全绿**。
  ⇒ 「非捕获方法不重试」这条契约**零测试钉住**。
- **文档缺口**：Java README 早已写明这条差异
  （`cortex-mem-spring-integration/README.md:887`：「The other three SDKs retry **only**
  the capture path」），但 **JS / Go / Python 三家的 README 都没写**
  ⇒ 跨家移植时这条差异对 JS 用户不可见。
  ⏸ **附带发现（留给 Java 侧）**：Java README 那句只提「两个抽取读方法在 Java 独有」，
  **漏掉了另外 5 个**（`submitFeedback`/`updateObservation`/`deleteObservation`/
  `triggerRefinement`/`triggerExtraction`）⇒ Java README 那句本身也不完整。
- **修法（本轮已修，JS 侧）**：
  ① **加测试**：新增 **3** 个用例 —— `startSession` 对 503 只发 **1** 次并抛 `APIError`；
  `submitFeedback`（走 `requestNoContent` 这**另一条**内部路径）对 503 只发 **1** 次并抛
  `APIError`；**阳性对照**：同一个 mock 下 `recordObservation` 对 503 发 **3** 次。
  ⭐ 阳性对照是必需的：否则「只发 1 次」可能只是因为重试逻辑坏了。
  ② **补文档**：JS 双语 README 新增「重试策略 / 重试策略」一节，写明 `maxRetries`
  **只作用于捕获路径**，其余方法只发一次请求；并点明 **Java 还重试那 7 个方法**，
  跨家移植时 `maxRetries` 管到的调用范围不同。
- **变异测试**：变异 1（`startSession` 加重试）⇒ 恰好 **1** 红；
  变异 2（`submitFeedback` 加重试）⇒ 恰好 **1** 红。两次变异按
  sha256 `194520e8b10325649dd3614c00826a770b7619682437398a29162cf008343c7a` 还原。
- **与 P2-161 的关系（不是重复立条）**：P2-161 是**吞错**轴（哪些方法的失败被吞掉），
  本条是**重试**轴（哪些方法会重试）—— 同一子系统的**两条不同轴**，并列记录、互不覆盖。
  与 **P2-113**（失败默认可见性）也不同：P2-113 讲的是「失败默认可见还是可见」，
  本条讲的是「同一个失败**会不会再发一次**」。

---

## P2-93 的证据段（⏸ 条目，按第 254 轮规则整体迁出）

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文。

- **后端语义（决定性，两行）**:
  ```java
  if (request.comment() != null) {      // MemoryController.java:253
      obs.setUserComment(request.comment());
  }
  ```
  即 **`comment` 缺失 → 保留旧值；`comment` 存在（哪怕是空串）→ 覆盖**。
  清空评论的**唯一**表达方式就是显式发 `""`。
- **四家的写法（关键差异只在判空方式）**:

  | SDK | 判空写法 | `""` 是否上 wire | 能否清空 |
  |---|---|---|---|
  | **Go** | `json:"comment,omitempty"` | ❌ 丢弃 | **否** |
  | **JS** | `if (req.comment)`（`""` 为假值） | ❌ 丢弃 | **否** |
  | **Python** | `if comment:`（`""` 为假值） | ❌ 丢弃 | **否** |
  | **Java** | `if (comment != null)` | ✅ 发送 | **是** |

  **Java 是唯一正确的那个，而且它的注释明确写出了这个区分**：
  「only include 'comment' when non-null / Backend only updates userComment when comment != null
  (preserves existing otherwise)」—— 说明该语义**是被设计过的**，只是另外三家没跟上。
- **活体实测（三段式，含有效负对照）**（观测 `32ca7c75-…`，直查 `mem_observations.user_comment`）:

  | 步骤 | 请求体 | 库中 `user_comment` |
  |---|---|---|
  | 置入评论 | `{observationId, feedbackType, comment:"restored"}` | `restored` |
  | **C：Go/JS/Python 体**（省略 comment） | `{observationId, feedbackType}` | **`restored`（未清空）** |
  | **B：Java 体**（`comment:""`） | `{observationId, feedbackType, comment:""}` | **空（已清空）** |

  **第一版把 C 跑在了已被 B 清空的状态上**，那样的 C 无法区分「保留」与「也清空了」，
  读数会假阳。**重置评论后重跑**才得到上表 —— C 是真正的负对照。
- **危害：完全静默。两次都返回 HTTP 200 `{"status":"ok","observationId":"…"}`**，
  调用方**无从得知评论没被清空。与 P2-26 相比这里连半信号都没有**
  （P2-26 至少在只设 `Facts` 时会抛「at least one field must be provided」）。
- **与 P2-26 / P2-27 的关系：同属「空值被省略 → 无法表达清空」这一族，但不同端点、不同范围**：
  P2-26 是 Go 的 PATCH observations、P2-27 是 Python 的 `extractedData`，而本条是
  `feedback` 端点且**一次命中三家**。机制也不同：Go 靠 `omitempty`，JS/Python 靠**真值判断**。

---

## P2-95 的证据段（⏸ 条目，按第 254 轮规则整体迁出）

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文。

- **原文形态**（`:641-644` 可见散文之后又直接接回 SQL）：

  ```
  );                                     <- DDL 到此结束
  > **`created_at_epoch` is the authoritative ordering column - not `created_at`.**
  > ... 活体实测 38,088 行观测中 19,711 行（51.8%）`created_at IS NULL` ...
  > 命名为 `…OrderByCreatedAtDesc` 看起来合理，却会**静默地把结果取反**。
                                          <- 散文到此结束（但仍在围栏内！）
  -- Vector indexes (HNSW, V2)            <- SQL 又继续
  CREATE INDEX idx_obs_embedding_768 ...
  ```

- **后果：渲染器把围栏内的一切当字面代码**显示。这段本该是醒目提示的内容，
  变成一堵等宽字体的文字墙 —— 而它恰恰是**全篇最要紧的运维警告之一**
  （排序列取错会静默取反结果，无任何报错）。
- **范围是孤例对，不是系统性问题**：对 `docs/**/*.md`（排除 `docs/archive/`）
  跑「围栏内是否出现 `>` 起始行」的扫描，全库只有这两个文件命中。
- 内容本身是对的：散文里的数字（51.8% / 99.98%）与结论均已在此前轮次核实，
  不存在失实陈述，所以这不是「照权威改」的情形。

---

## P2-90 的证据段（⏸ 条目，按第 254 轮规则整体迁出）

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文。

- **实测（本轮亲跑，Go 1.25.14 darwin/arm64，9 个模块逐个 `go test -count=1 -v`）**。
  **必须区分两个口径**，否则会把子测试重复计数：

  | 模块 | 顶层测试 `^--- PASS` | + 子测试 `^    --- PASS` | 合计 |
  |---|---|---|---|
  | 根 `.` | 207 | 29 | **236** |
  | 根 `./dto` | 67 | 0 | **67** |
  | `eino` | 8 | 0 | **8** |
  | `genkit` | 13 | 0 | **13** |
  | `langchaingo` | 12 | 0 | **12** |
  | `examples/http-server` | 3 | 24 | **27** |
  | 其余 4 个 `examples/` | 0 | 0 | 0（`[no test files]`） |
  | **合计 | 310 | 53 | 363** |

  与 README 对照：`dto 67` / `eino 8` / `genkit 13` / `langchaingo 12` /
  `examples/http-server 27` / 「适配器再加 60」**六项全部吻合**，
  唯独 **`core` 235 应为 236**，故根模块 `302 → 303`、总数 `362 → 363`。
- **差值完全归因，不是我的计数口径问题**：`7a78266`（2026-10-06，**第 319 轮 P2-84 修复**）
  向 `go-sdk/cortex-mem-go/client_test.go` 新增了**恰好一个**顶层测试
  `TestNewClient_DoubledTrailingSlashNormalization`（`git show 7a78266 -- '*.go' | grep -E '^\+func Test'`
  全库只命中这一条）。
  **即：这个过期数字是我自己在第 319 轮修 P2-84 时留下的，当时改了代码与测试却没回头更新 README 的计数。**
- **一处比其余六处更值得记**: `README.md:13`（zh `:13`）Features 段写的是
  「**Comprehensive tests** — 362 tests with wire format verification」，
  **没有日期**，是现在时断言；`:192` 那处则明写「Measured on 2026-10-04」——
  后者按字面**属实（2026-10-04 当天确实是 362），前者则是无日期的失实**。
