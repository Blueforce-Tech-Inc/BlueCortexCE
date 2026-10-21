# Backend Review Evidence 85 — 第 384 轮本轮新条目的整条正文 + P2-88 证据段迁出（第 384 轮迁出）
> **归档规则**：承 `-62` ~ `-84` 的体例，迁出**本轮新写**的条目全文；
> 并按第 254 轮规则，把 **P2-88**（⏸ 待决）的 `- **Scope**` 与全部证据 bullets 整体迁入，
> 工作文件里只留一行指针（`- **Problem**` 与 `- **Status**` 一律留在工作文件）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：266 行 / 19146 字节。**
>
> 本批迁出 **P2-155 / P2-156 / P2-157** 三条新条目，并迁出 **P2-88** 的证据段
> （受控实验表、后果链五环、README 双语缺记载的逐行定位、第 360 轮设计稿补注、次生后果）。
>
> **本轮代码方向为 Java SDK，文档方向为 SDK README（doc round 283）。**
>
> 本轮的形状是**同一条契约的两端都没被钉住**：Java SDK 的捕获路径上有一道**Java 独有**的
> 「`projectPath` 必须是绝对路径」校验——它**零测试覆盖**、且在双语 README 里**零记载**；
> 而 README 靠「吞错 vs 抛错」把 25 个方法分成两类，这条契约同样**零测试钉住**，
> 文档自己却在同一节里**自相矛盾**（相邻两条 bullet 对同一批方法给了相反的说法）。
>
> 本轮**只加测试、实现一行未改**（17 个新用例），并用**三次变异测试**证明它们能失败。

---

## P2-88（⏸ 待决）—— 证据段迁出，正文逐字保留

### P2-88: `retrieval-enabled=false` 会**静默关掉全部 @Tool 捕获**，而 README 把该开关记作只管检索

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **受控实验**（临时 `ApplicationContextRunner` 探针，测完即删；三列而非两列，
  第三列用来排除「两个顾问同生共死」这一竞争解释）：

  | Bean | 对照：全默认 | `retrieval=false` + `capture=true` + `bridge=true` | 对照：`retrieval=true` + `bridge=false` |
  |---|---|---|---|
  | `CortexMemClient` | true | true | true |
  | `ObservationCaptureService` | true | true | true |
  | `MemoryRetrievalService` | true | **false** | true |
  | `CortexToolAspect` | true | **true** | true |
  | `CortexSessionContextBridgeAdvisor` | true | **false** | false |
  | `CortexMemoryAdvisor` | true | **false** | true |

  第三列是关键：**`bridge=false` 时 `CortexMemoryAdvisor` 存活**，证明第二列里
  两个顾问的消失确实源于 `retrieval-enabled`，而非二者在任何情况下都绑定生死。
- **后果链（每一环都已独立落实，非静态推断）**:
  1. 第二列实测：`capture-enabled=true` 时 `CortexToolAspect` **仍然存在**（捕获切面在），
     但 `CortexSessionContextBridgeAdvisor` **不存在**（`begin()` 的自动来源没了）。
  2. `CortexToolAspect.java:54` 首行即守卫
     `if (!CortexSessionContext.isActive()) return joinPoint.proceed();`
  3. `CortexSessionContext.java:50-51` —— `isActive()` 就是 `CURRENT.get() != null`，
     而 `CURRENT` 仅由 `begin()` 写入。
  4. **桥接顾问是生产代码里 `begin()` 的唯一自动调用点**：
     全模块 `main` 下 `CortexSessionContext.begin(` 只命中
     `CortexSessionContextBridgeAdvisor.java:81`（call 路径）与 `:100`（stream 路径），
     其余全部命中均为 Javadoc 或测试。
  5. 负对照**已预先存在于测试套件**：`CortexToolAspectTest:60`
     `whenContextInactive_toolExecutesWithoutCapture` 已经断言了「未激活 ⇒ 不捕获」。

  净效果：设 `retrieval-enabled=false` 以节省检索开销的用户，会在**没有任何日志、异常或
  启动失败的情况下失去全部** `@Tool` 捕获 —— 切面照常织入、照常执行，只是全部空转。
- **影响范围（已收窄，不要过度声张）: 只影响选项 A**（README `:152` 所述、
  依赖 `CONVERSATION_ID` 走桥接顾问的纯 ChatClient 用法）。走**选项 B** 的应用自行调用
  `CortexSessionContext.begin/end` 者不受影响 —— 桥接顾问此时本就只做透传
  （类 Javadoc `:40-52` 已自陈二者不可嵌套）。
- **README 未记载此耦合（双语同缺）**:
  - `cortex-mem-spring-integration/README.md:199`（zh `:206`）把 `context-bridge-enabled`
    记作一个独立开关，只说「创建桥接顾问 / 使 @Tool 捕获无需手动上下文」，
    **完全未提** `retrieval-enabled=false` 也会把它一并关掉。
  - `README.md:197` 把 `retrieval-enabled` 记作「Enable memory retrieval」，未提任何副作用。
  - 对照：`README.md:196` 明确为 `capture-user-prompt-enabled` 写了
    “Independent of capture-enabled.” —— **「独立」二字只给了这一项，没给桥接**。
  - `README.md:796`（zh `:775`）故障排查表把 `retrieval-enabled=false` 列为
    「无 ICL 上下文」的已知原因，却**未**列为「@Tool 捕获全部消失」的原因；
    同表 `:794` 的「Tool calls not captured」只给了自调用一条原因。
- **第 360 轮补充：受影响的不止 README，零覆盖设计稿同样未记载**:
  `docs/drafts/cortex-mem-integration-capture-analysis.md` 的 §1「**Current** Capture Matrix」
  把 Tool use 一行标为 `✅ Yes`（自动捕获），Notes 写「AOP intercepts `@Tool` methods
  **when `CortexSessionContext` is active**」——**这正是被 `retrieval-enabled` 连带关掉的那一环**，
  文档同样未提；其 §2 更用了与 README **完全相同**的措辞
  「`capture-enabled` controls @Tool capture; `capture-user-prompt-enabled` controls
  user prompt capture **independently」。故「独立」这个误导性措辞在仓库内至少两处**
  （双语 README + 本设计稿）出现，而修法一旦定下来，**两处都要同步**。
  （该设计稿的其余可验证断言本轮逐条复验为真：§1 五行的端点/类名/方法名/Auto? 判定、
  §2 的两个配置项、§6 的四处实现位置、§7 的 `-Plocal` profile 与
  `examples/cortex-mem-demo/e2e/run-e2e.sh` 全部存在；其中 `POST /api/session/start`
  与 `/api/prompts` 两个曾被怀疑是幻影端点，经**活体请求**判定为真
  ——`/api/session/start` 返 400、`/api/ingest/session-start` 才是 404。）
- **⚠️ 次生后果：用户提示捕获同样被牵连，README 的「独立」措辞更易误导**:
  同一张表里 `CortexMemoryAdvisor` 也随 `retrieval-enabled=false` 一起消失（见上表第二列），
  故 `capture-user-prompt-enabled` 控制的**用户提示捕获**一并失效。
  `README.md:196` 对该开关写的是「Enable user prompt auto-capture (CortexMemoryAdvisor).
  **Independent of capture-enabled.」—— 这句就其字面而言为真**（它确实独立于
  `capture-enabled`），但读者极易顺势外推为独立于其余全部开关，而实际它挂在
  `retrieval-enabled` 下。**这是措辞引发的错误推论，不是陈述本身失实。**

---

## P2-155 —— 全文

### P2-155: Java SDK 捕获路径上有一道**Java 独有**的「`projectPath` 必须是绝对路径」校验 —— 它**零测试覆盖**，且在双语 README 里**零记载**

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **事实**：`CortexMemClientImpl.java:878-885` 的 `requireAbsolutePath(value, fieldName)`，
  用 `java.nio.file.Paths.get(value).isAbsolute()` 判定；非绝对路径抛
  `IllegalArgumentException("<field> must be an absolute path (got: <value>)")`。
  三个捕获方法都调它：`recordObservation`（`:126`）、`recordSessionEnd`（`:142`）、
  `recordUserPrompt`（`:158`）。
- **跨家对照：这道校验是 Java 独有的**。Go / Python / JS 三家**都没有**等价的绝对路径校验
  （全仓 grep `absolute` / `isabs` / `isAbsolute` 只命中 Java 的这一处，另两处是无关命中）。
  ⇒ 这是捕获路径上**唯一一条无法原样移植**的规则。
- **零测试覆盖（且是唯一没被钉住的那一个）**：`CortexMemClientImplTest` 里
  `absolute` / `relative` 两个词出现 **0 次**。同族的「非空」校验（`sessionId` /
  `projectPath` / `toolName` / `promptText`）有 12 个用例钉着，唯独这道**没有**。
- **零记载**：双语 README 里 `absolute` 出现 **0 次**；「Required Arguments Are
  Checked Client-Side」那张表只列「非空」规则，并写着「The Go, Python and JS SDKs
  enforce exactly the same set」——**读者据此以为捕获侧的规则四家完全一致**。
- **静默丢数据的实际后果（不是抛错，而是记录丢失）**：
  - `CortexSessionContextBridgeAdvisor` 把配置里的 `projectPath` **原样**交给
    `CortexSessionContext.begin()`（`:81` call 路径 / `:100` stream 路径），**不检查是否为绝对路径**。
  - `CortexToolAspect.java:74-84` 用 `CortexSessionContext.getProjectPath()` 构造
    `ObservationRequest`，外面套 try/catch ⇒ 相对路径时 `IllegalArgumentException`
    被降级成一条 warning（`Failed to record tool observation: projectPath must be an
    absolute path …`）。
  - `CortexMemoryAdvisor.java:162-172` 同样是 try/catch 包住 `recordUserPrompt`。
  ⇒ **`cortex.mem.project-path` 填一个相对值，会让每一次 `@Tool` 捕获与每一次用户提示
  捕获都只留下一条 warning**，Agent 管道照常运行，记忆则从未被写入。
  这正是 P2-88 同型的「静默丢数据」形状。

### 修法（只加测试 + 补文档，实现一行未改）

- 新增 **9** 个用例（`CortexMemClientImplTest`）：
  1. 三个捕获方法 × 相对路径 ⇒ 抛 `IllegalArgumentException`（消息含 `projectPath` 与 `absolute`）；
  2. 三个捕获方法 × 相对路径 ⇒ **`server.getRequestCount() == 0`**（钉住「校验发生在请求之前」，
     只断言异常的用例钉不住顺序）；
  3. 三个捕获方法 × 绝对路径 ⇒ `getRequestCount() == 1`（**阳性对照**，
     否则上面两组也会在「客户端干脆不调后端」时全绿）。
- 绝对路径用 `Paths.get("/proj").toAbsolutePath().toString()` 构造，**不写死 `"/proj"`** ——
  Windows 上 `"/proj"` 不是绝对路径，写死会让这组用例在 Windows 上必然红。
- 双语 README 的「必填参数」节后补一段「Java 独有的绝对路径规则」，写明后果与配置项。

### 变异测试（证明新用例有牙）

| 变异 | 注入 | 结果 |
|------|------|------|
| **1** | 删掉 `recordObservation` 里的 `requireAbsolutePath(...)` 一行 | **2 个新用例红**（`recordObservation_relativeProjectPath_throws`、`…_sendsNoRequest`），其余 **113** 个仍绿 |
| **2** | `recordObservation` 的 `executeWithRetrySilent` 改成 `executeWithRetry` | `recordObservation_serverError_swallowsError` 红，其余 **114** 绿 |
| **3** | `submitFeedback` 的 `executeWithRetry` 改成 `executeWithRetrySilent` | `submitFeedback_serverError_propagatesException` 红（变异 2 尚未还原，故同时 2 红） |

三次变异均按 **sha256 逐字节还原**（`f97b37ee62c15d128b2ef40f50d5f190bef82d5fc6a77d327f94af50fb910b5f`）。

---

### P2-156: Java SDK 的「**吞错 vs 抛错**」契约 —— README 用它划分方法族，却**零测试钉住**（8 个方法一个都没测）

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **事实**：两个重试助手决定了这件事——`executeWithRetrySilent`（`:777-802`）在耗尽尝试后
  **只记一条 warn 并正常返回**，而 `executeWithRetry`（`:704-730`）**向上抛出**。
  按调用点分类（实测 grep 全部调用点，**不是静态推断**）：

  | 类别 | 方法 | 助手 | README 说的 |
  |------|------|------|------------|
  | 静默吞错 | `recordObservation`、`recordUserPrompt`、`recordSessionEnd`、`triggerRefinement`、`triggerExtraction` | `executeWithRetrySilent` | ✅ 一致 |
  | 传播 | `submitFeedback`、`updateObservation`、`deleteObservation` | `executeWithRetry` | ✅ 一致 |
  | 传播（不重试） | `startSession`、`updateSessionUserId` | 直接调用 | ✅ 一致 |

- **零覆盖**：改前的 `CortexMemClientImplTest` 里，「Error Propagation Tests」一节只有
  **2** 个用例（`startSession_serverError_propagatesException`、
  `updateSessionUserId_serverError_propagatesException`）—— 恰好是**既传播又不重试**的那两个。
  ⇒ **5 个静默方法与 3 个传播重试方法，一个都没有用例**。
  而 README 的 Design Notes 正是靠这条契约把 25 个方法分成两类；P2-157 里那处自相矛盾
  也正因为文档自己没把这条契约钉死。
- **跨家对照**：Go / Python / JS 三家的 README 也都写同一条契约，但那是**文档**；
  本条说的是 **Java 的测试套件没有把它钉住** ⇒ 一次重构就能静默改掉契约而没有任何红灯。

### 修法（只加测试，实现一行未改）

新增 **8** 个用例（`CortexMemClientImplTest`），每个都断言**两半**：

- 请求**确实发出**（`server.getRequestCount() == 1`）——否则「不抛异常」也会在
  「客户端干脆没调后端」时全绿；
- 结果是**文档写的那一个**（5 个静默 ⇒ `doesNotThrowAnyException`；3 个传播 ⇒ `assertThatThrownBy`）。

### 变异测试（证明新用例有牙）

| 变异 | 注入 | 结果 |
|------|------|------|
| **2** | `recordObservation`：`executeWithRetrySilent` → `executeWithRetry` | `recordObservation_serverError_swallowsError` 红（它该吞的却抛了），其余 **114** 绿 |
| **3** | `submitFeedback`：`executeWithRetry` → `executeWithRetrySilent` | `submitFeedback_serverError_propagatesException` 红（它该抛的却吞了） |

两次变异均按 sha256 逐字节还原。

---

### P2-157: Java SDK 双语 README 的 Design Notes **自相矛盾** —— 同一节里相邻两条，把 5 个静默吞错的方法分别写成「propagate」与「swallow」

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **事实**：`cortex-mem-spring-integration/README.md:849-852`（zh `:817-819`）写：

  > Write and mutation methods propagate instead of degrading: `startSession`,
  > `updateSessionUserId`, `submitFeedback`, `updateObservation`, `deleteObservation`,
  > `triggerRefinement`, `triggerExtraction`, and the capture calls `recordObservation`,
  > `recordUserPrompt`, `recordSessionEnd`.

  **紧接着的下一条**（`:853-858`，zh `:820-828`）写：

  > Five capture/async calls swallow their errors once the attempts are exhausted
  > (`recordObservation`, `recordUserPrompt`, `recordSessionEnd`, `triggerRefinement`,
  > `triggerExtraction`)

  ⇒ **同一个名单里的 5 个方法，在相邻两条 bullet 里一个被说成「传播」、一个被说成「吞掉」。**
  中文版是**逐字同型**的同一条矛盾。
- **哪一条是真的**：**第二条**。实测五个方法全部走 `executeWithRetrySilent`
  （`CortexMemClientImpl.java:128 / 143 / 159 / 235 / 436`），异常被吞、只记 warn。
  第一条列的 10 个方法里有 **5 个**（`triggerRefinement`、`triggerExtraction` + 三个捕获）
  是**假的**。
- **写入即假（不是事后漂移）**：`git log -S` 判定——
  - 第一条（错的）来自 `1fa843e`（**2026-10-02**，`docs(java-sdk): document the exact
    graceful-degradation contract`）；
  - 第二条（对的）来自 `20e3661`（**2026-10-03**，`docs(java-sdk): state the retry scope
    precisely instead of implying all reads retry`）。
  - 在 `1fa843e` 那个 commit 上取同一文件的实现，**五个方法已经是 `executeWithRetrySilent`**
    ⇒ **写下那一刻就是假的**，不是后来漂移的。
- **顺带修正的第三处**：`README.md:824` 的「Fire-and-forget capture: Capture operations
  log failures but never throw」——「never throw」只对**后端失败**成立，客户端校验是会抛的。
  已改为「log backend failures but never throw them」并补一句「客户端校验是例外」。
- **修法（纯文档，零可执行行）**：第一条改为「5 个传播 + 5 个捕获/异步调用是例外并吞掉错误」，
  并指向下一条的重试范围说明（避免重复列举）；中文版同改。

---

## 压缩记录

本轮 findings 的字节余量只剩 **307 字节**（1394 行 / 149693 字节，双阈值 1500 / 150000），
而本轮要写入 3 条新条目 + 2 条结转的 Status 更新。故按第 254 轮规则把
**P2-88 的证据段**整体迁入本归档，工作文件里留指针。

**口径（三个数字互不相同，别混用）**：

- 本归档持有的 P2-88 证据段：**65 行 / 5355 字节**（逐字，含 `- **Scope**` 一行）。
- 工作文件**保留** `- **Scope**` 一行（48 字节，与本仓其余条目体例一致），
  真正迁出的是其余 **64 行 / 5307 字节**，换成一行 `- **Evidence**` 指针。
- 本轮 findings 总体：**1394 行 / 149693 字节 → 1339 行 / 147666 字节**
  （净 **-55 行 / -2027 字节**），而这已经是**新增 3 条条目之后**的净值。

**硬证明**（脚本实测）：条目标题 145 → 148（+3，既有 145 条逐字不变且顺序不变）、
链接目标**只增 a43**、其余 68 个目标的**引用次数逐个不变**、迁出的 64 行**逐字**仍在
本归档里、决策段（⚠️ 为什么不单方面改 / Severity / 建议修法）**逐字保留**。

**本轮新增的两个杠杆（与第 383 轮的 C6 / C7 区分）**：

- **C8**：**⏸ 待决条目的证据段整体迁出**（第 254 轮规则的应用，本轮首次用于
  P2-88）——`- **Problem**`（标题）、`- **⚠️ 为什么不单方面改**`、`- **Severity**`、
  `- **建议修法**`、`- **Status**` **一律留在工作文件**；`- **Scope**` 也留（体例一致），
  其余证据 bullets 迁出。
- **C9**：**归档指针行**统一写成「一行说清迁走了什么 + 指向归档」，使指针行本身
  也可回溯（`→ 归档 [..-85][a43]（受控实验表、后果链五环、…）`）。

**探针自身的三处错**（都记在这里，避免下次重犯）：

1. 第一次数「client 模块的测试数」时用了 `grep -c "@Test"` 单文件 ⇒ **118**，
   而 surefire 实收 **115**。差在 `CortexMemClientImplTest.java` 里**还有第二个
   顶层测试类** `CortexMemErrorMessageTest`（3 个用例）——**一个文件两个类**。
   ⇒ 静态 `@Test` 计数**只有在逐文件与运行时分层对齐时**才等于运行数。
2. 第一版变异测试把「`requireAbsolutePath` 那一行」与「`executeWithRetrySilent`
   那一行」一起改 ⇒ 两个变异叠在一起，失败归因不清。拆成两次单独注入后才各自点名。
3. 变异 3 是在**变异 2 尚未还原**的情况下跑的 ⇒ 报 2 红而不是 1 红。
   结论无误（点名的那条确实红），但**归因必须单变量**，故归档里如实记下。
