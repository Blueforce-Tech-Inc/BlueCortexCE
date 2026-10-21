# Backend Review Evidence 87 — 第 386 轮本轮新条目的整条正文 + P2-112 证据段迁出（第 386 轮迁出）

> **归档规则**：承 `-62` ~ `-86` 的体例，迁出**本轮新写**的条目全文；
> 并按第 254 轮规则，把 **P2-112**（⏸ 待决）的证据 bullets 整体迁入，
> 工作文件里只留 `- **Scope**` 一行 + 一行 `- **Evidence**` 指针。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：227 行 / 17327 字节。**
>
> 本批迁出 **P2-159 / P2-160 / P2-161** 三条新条目，并迁出 **P2-112** 的证据段。
>
> **本轮代码方向为 Python SDK，文档方向为 SDK README（doc round 285）。**
>
> 本轮的两条主线：
> ①**一条跨家断言在四家 README 里重复了 8 次，其中 6 次失实** —— 「另三家强制的是完全相同
>   一组规则」，而 Java 独有第 384 轮刚发现的绝对路径校验；
> ②**同族里没被钉住的那一个**（第三次出现，形状与 P2-147 / P2-156 同型）——
>   Python 的三个 fire-and-forget 捕获方法里，只有 `record_observation` 的吞错契约有用例。
>
> **容量**：findings 写入前只剩 **158 行 / 1068 字节** ⇒ 本轮**再次启用 C8**
> （第 254 轮规则的应用，第 384 轮首次用于 P2-88，**本轮第二次用于 P2-112**）：
> 归档侧持有 P2-112 的证据段 **43 行 / 3730 字节**，工作文件真正迁出 **43 行 / 3730 字节**
> 并换上一行指针。

---

## P2-112（⏸ 待决）—— 证据段迁出，正文逐字保留

### P2-112: `cortex-mem-starter` 的 `base-url` 一旦是空串就让整个 Spring 应用启动失败 —— 而同一仓库的后端对完全相同的模式早已显式处理过

- **Scope**: → [scope-9][a6]、[scope-10][a7]

- 不对称（本条的要害）: 同样是「没有有效 URL」的三种状态，两种安全、一种致命：

  | 配置 | 结果 |
  |---|---|
  | 完全不设 `base-url` | 自动装配不激活，应用正常启动（无记忆功能） |
  | `base-url=false` | 自动装配不激活，应用正常启动 |
  | `base-url=`（空串 / null / 空白 / `${ENV:}`） | 整个应用启动失败 |

  最后一行恰是生产环境最常见的写法 `cortex.mem.base-url: ${CORTEX_MEM_URL:}`。
  用户想表达「没配就等于没装」，得到的却是整个应用起不来；且错误信息
  `baseUrl must not be null or blank` 不指向任何配置项名，排查者只会去翻 Java 源码。
- 仓库内已有同一模式的正确处理: `backend/src/main/java/com/ablueforce/cortexce/config/SpringAiConfig.java:32,42-46`
  用的是完全相同的 `@ConditionalOnProperty(name="api-key")` 形状，却显式判空并降级：
  `log.warn("... property exists but is empty — ChatModel bean not created. ...")` 后 `return null`。
  而后端自己的 `application-dev.yml:8` 正是
  `api-key: ${SPRING_AI_OPENAI_API_KEY:${OPENAI_API_KEY:}}` ——
  同一份仓库里，一个模式已按「空即跳过」处理，另一个模式没有。
- 为什么会变成这样: 那条 blank 校验来自 `cf78f5a`（2026-03-31），提交标题是
  `fix(java-sdk): add defensive validation for retry config and timeout properties` ——
  **baseUrl 根本不在该提交的意图里**，是被顺手加进去的。它在
  `new CortexMemClientImpl(properties)` 的手工构造路径上是合理的防御；但在
  starter 自动装配路径上，它把「属性存在但为空」从「不激活」变成了「启动炸弹」。
- 范围已钉死，不夸大: 同一批探针另测了其余属性在 yml 写 null 时的表现 ——
  引用类型（`connect-timeout` / `read-timeout` / `retry.backoff` / `project-path`）绑定 null 时
  保留默认值，安全；基本类型（`retry.max-attempts` / `default-experience-count`）绑定 null 时
  抛 `A null value cannot be assigned to a primitive type` 同样启动失败，但那是
  Spring Boot Binder 对基本类型的通用行为，任何用 `int` 的 `@ConfigurationProperties` 都如此，
  **不是本 SDK 独有**。`base-url` 才是**本 SDK 特有**的那一个。
- **README 未记载**（`cortex-mem-spring-integration/README.md:190` 只给出默认值
  `http://localhost:37777`，未提空值会怎样）；`CortexMemProperties` 里 `baseUrl` 也没有
  `@NotBlank` 一类的必填声明。
- 为什么记录不修: 修法形状有歧义（三条互斥路线，见下），且改的是自动装配的激活条件，
  属对外可见的行为变更；按「对外契约变更不单方面实施」记录。
- 建议修法（待确认，三选一）:
  1. 照搬后端先例（推荐）：把 `@ConditionalOnProperty` 换成「属性存在、非 `false`、且非空白」的条件，
     空值时 `log.warn` 一行（措辞可复用后端那句）后不激活整个自动装配。
  2. 保留失败但给出可定位的错误：异常信息带上属性名（如 `cortex.mem.base-url`）
     与「该属性为空即不应启用记忆集成」的提示。
  3. 降级为默认值：空值时回落到 `http://localhost:37777` —— 但这会让应用在未配置的情况下
     悄悄去连一个可能不存在的后端，不推荐。
- 测试侧同缺: `CortexMemAutoConfigurationTest` 现有 4 例覆盖 base-url 有 / 无，
  没有「base-url 为空」一例 —— 上述组合从未被断言过。
- Severity: 中高（整个应用无法启动，触发写法极常见；但只需一个属性写空，不涉及安全或数据损坏）。

---

### P2-159: 四家 SDK README 的「必填参数规则集**完全相同**」—— Python / Go / JS 三家（双语共 6 处）都说「另三家强制的是完全相同的一组规则」，而 **Java 多一道 `projectPath` 必须是绝对路径**的检查

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **断言逐处定位（按断言清扫，8 处，其中 3 家�� 6 处失实）**:

  | 文件 | 行 | 原文 | 判定 |
  |------|----|------|------|
  | `python-sdk/cortex-mem-python/README.md` | 270 | 「The Go, Java and JS SDKs enforce exactly the same set.」 | ❌ **失实** |
  | `python-sdk/cortex-mem-python/README-zh-CN.md` | 254 | 「Go、Java、JS 三家 SDK 强制的是完全相同的一组规则。」 | ❌ **失实** |
  | `js-sdk/cortex-mem-js/README.md` | 244 | 「The Go, Java and Python SDKs enforce exactly the same set.」 | ❌ **失实** |
  | `js-sdk/cortex-mem-js/README-zh-CN.md` | 230 | 「Go、Java、Python 三家 SDK 强制的是完全相同的一组规则。」 | ❌ **失实** |
  | `go-sdk/cortex-mem-go/README.md` | 290 | 「The Java, Python and JS SDKs enforce exactly the same set.」 | ❌ **失实** |
  | `go-sdk/cortex-mem-go/README-zh-CN.md` | 276 | 「Java、Python、JS 三家 SDK 强制的是完全相同的一组规则。」 | ❌ **失实** |
  | `cortex-mem-spring-integration/README.md` | 387 | 「The Go, Python and JS SDKs enforce exactly the same set.」 | ✅ **仍为真**（另三家彼此确实相同） |
  | `cortex-mem-spring-integration/README-zh-CN.md` | 388 | 「Go、Python、JS 三家 SDK 强制的是完全相同的一组规则。」 | ✅ **仍为真** |

- **失实的机制**：Java 的 `CortexMemClientImpl.java:878-885` 有 `requireAbsolutePath(value, fieldName)`
  —— 用 `Paths.get(value).isAbsolute()` 判定，非绝对路径抛
  `IllegalArgumentException("<field> must be an absolute path (got: …)")`；
  三个捕获方法都调它（`recordObservation` `:126`、`recordSessionEnd` `:142`、`recordUserPrompt` `:158`）。
  **Go / Python / JS 三家都没有任何等价的绝对路径校验**
  （全仓 grep `absolute` / `isabs` / `isAbsolute` 只命中 Java 这一处，另两处是无关命中）。
  ⇒ 一个**相对路径**在 Python / Go / JS 里被接受，在 Java 里被拒。
- **写入即假（不是事后漂移）**：`git log -S` 判定——
  - Java 那道校验由 `f98eddf`（**2026-04-15**，`fix(java-sdk): add absolute path validation for cwd in recordObservation/recordUserPrompt/recordSessionEnd`）引入；
  - 三家的这句断言由 `6902968`（**2026-10-03**，`docs: document the required-argument contract in all four SDK READMEs`）写下。
  ⇒ **断言写下时，Java 的额外校验已经存在了近 6 个月** ⇒ 写入即假。
- **表本身没有错**：四张「必填参数」表程序化求交，**方法集合完全相同（各 18 行）**，
  必填项措辞的差异**只来自命名风格**（`req.SessionID` / `req.session_id` / `request.sessionId()`；
  `id` / `observation_id` / `observationId`；`req.ProjectPath` / `req.cwd`）。
  ⇒ 失实的不是表，而是那句**跨家断言**：它的功能是告诉读者「四家客户端检查的东西一样，
  跨家移植不会踩到不同的拒绝条件」，而 Java 恰恰多拒一类输入。
- **修法（本轮只改 Python 双语两处；Go / JS 各 2 处结转）**：把断言改准而不是加一句说明 ——
  「要求的**参数集合**完全相同，只有一处例外：Java 侧在三个捕获方法上还额外要求
  `project_path` 是绝对路径，另三家都不查」；并点明「相对路径在这里被接受、在那里被拒绝」。

**探针自身的两处错**：
① 第一版对拍四张表时**用原始方法名求交**，结果四张表「两两毫无交集」——
   真相是命名风格不同（snake_case / PascalCase / camelCase）⇒ 判定必须**先归一化**再求交；
② 第一版只查了 EN 版 ⇒ 漏掉 ZH 版；按断言清扫后才知道同一句在仓库里有 **8** 处。

---

### P2-160: Python SDK 的**三个** fire-and-forget 捕获方法里，只有 `record_observation` 的「吞错」契约被用例钉住 —— 另两个改成「抛错」路径后，全量 **457** 个测试**仍然全绿**

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **事实**：Python 的三个捕获方法都走 `cortex_mem/client.py:189` 的 `_fire_and_forget`
  （`record_observation` `:324`、`record_session_end` `:353`、`record_user_prompt` `:389`），
  该助手在耗尽尝试后**吞掉异常并正常返回**。
  `tests/test_client.py` 里与 `_fire_and_forget` 相关的 **7** 个用例
  （`test_fire_and_forget_swallows_error` / `..._no_retry_on_non_retryable` / `..._retries_on_429` /
  `..._retries_on_503` / `..._exhausts_retries` / `..._connection_error_retries`）
  **全部只驱动 `record_observation`** ⇒
  `record_session_end` 与 `record_user_prompt` **各自一个「后端失败 ⇒ 吞错」的用例都没有**
  （它们只有 happy-path 用例与校验用例）。
- **缺口证明（变异实验，两个变量各自单独注入）**：

  | 变异 | 注入 | 结果 |
  |------|------|------|
  | **1** | `record_session_end` 的 `self._fire_and_forget("RecordSessionEnd", …)` 换成 `self._request_no_content("POST", "/api/ingest/session-end", …)` | **457 passed** —— 全绿 |
  | **2** | `record_user_prompt` 同样换成 `_request_no_content` | **457 passed** —— 全绿 |

  ⇒ 把「吞错」改成「抛错」是**对外行为契约变更**（调用方原本收不到异常，改后会收到），
  而**没有任何用例会因此变红**。
  这与 **P2-147**（JS）、**P2-156**（Java）同型：**同族里没被钉住的那一个**。
- **修法（只加测试，实现一行未改）**：新增 **3** 个用例，每个都断言**两半** ——
  ①后端错误被吞掉（不抛）；②请求**确实发出**（`len(responses.calls) == 1`；
  「不抛」在「客户端压根没发请求」时也会成立，故必须同时钉住调用次数）。
  另加一个「重试耗尽后仍吞掉」的用例（3 次 502 ⇒ `len(responses.calls) == 3`）。

### 变异测试（证明新用例有牙）

| 变异 | 结果 |
|------|------|
| 变异 1 + 2 同时注入 | **3 failed, 457 passed** —— 失败的**正是**本轮新增的 3 个用例，其余全绿 |

变异后 `cortex_mem/client.py` 按 **sha256**（`9879f8a14d4c1cbbcd488a14326851c4a436df298340c9bea9adb20731aa56a7`）
逐字节还原。

---

### P2-161: 四家 SDK 对「**哪些调用算异步后台任务**」的判定不一致 —— Java 吞掉 **5** 个方法的错误（3 个捕获 + `triggerRefinement` + `triggerExtraction`），另三家只吞 **3** 个；而**没有任何一份文档点出这条分歧**

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **事实（逐家读实现，四个 SDK 全部实测枚举，非静态推断）**：

  | 方法 | Java | Python | Go | JS |
  |------|------|--------|-----|-----|
  | `recordObservation` / `record_observation` / `RecordObservation` / `recordObservation` | **吞** | **吞** | **吞** | **吞** |
  | `recordUserPrompt` | **吞** | **吞** | **吞** | **吞** |
  | `recordSessionEnd` | **吞** | **吞** | **吞** | **吞** |
  | `triggerRefinement` | **吞** | 抛 | 抛 | 抛 |
  | `triggerExtraction` | **吞** | 抛 | 抛 | 抛 |

  - Java：`CortexMemClientImpl.java:235`（`triggerRefinement`）与 `:436`（`triggerExtraction`）
    都走 `executeWithRetrySilent` ⇒ 错误被吞、只记一条 warn。
  - Python：`client.py:600-607`（`trigger_refinement`）与 `:745-752`（`trigger_extraction`）
    直接调 `self._request_no_content(...)` ⇒ 错误**向上抛**。
  - Go：`client_methods.go:184-191`（`TriggerRefinement`，注释明写
    「NOT fire-and-forget: this is an explicit user action, errors must propagate.」）与
    `:267-273`（`TriggerExtraction`）都直接 `doRequestNoContentWithParams` ⇒ 抛。
  - JS：`client.ts:343-349`（`triggerRefinement`）与 `:438-444`（`triggerExtraction`）
    都调 `this.requestNoContent(...)` ⇒ 抛。
- **分歧是「有意」的，但四家的「有意」不一致**：Go 的注释把 `TriggerRefinement` 归为
  「用户的显式动作，错误必须传播」；Java 的 `triggerExtraction` Javadoc 则写
  「extraction is an async background task and its outcome should not block the caller's pipeline」
  ⇒ **两家的分类理由正好相反**。Java 的吞错行为来自 2026-03-18 的一次修复
  （原 Javadoc 声明 fire-and-forget 而实现用 `executeWithRetry` 会抛，改为 `executeWithRetrySilent`）。
- **后果（诚实界定）**：从 Python / Go / JS 移植到 Java（或反向）时，
  这两个方法的**失败可见性**会静默改变：Java 侧调用方收到「成功」而实际失败，
  另三家的调用方会收到异常。**没有任何一份 README 说这件事**：
  - Java README 只写了「另三家只重试那 3 个捕获」——**说的是重试，不是吞错**；
  - Python / Go / JS README 连「Java 吞 5 个」都没提。
- **与 P2-113 的关系（不是重复立条）**：P2-113 讲的是**失败默认可见性**
  （Python / Java 默认记日志、Go / JS 默认无痕）；本条讲的是**哪些方法会吞错**。
  两者是同一子系统的**两条不同轴**，故并列记录、互不覆盖。
- **修法（对外行为契约，本轮**只记录不实施**）**：三条候选 ——
  ①**只补文档**：在 Java README 点明「另三家的 `triggerRefinement` / `triggerExtraction` 会抛，
  Java 侧吞掉」；②**改 Java 对齐另三家**（属对外可见行为变更）；
  ③**改另三家对齐 Java**（同样属对外可见行为变更，且会去掉 Go 的显式注释所声明的意图）。
  需作者/用户决策。

---

## 压缩记录

本轮 findings 写入前余量 **158 行 / 1068 字节**，而本轮要写入 3 条新条目 ⇒ **第二次启用 C8**（第 254 轮规则的应用）：把 **P2-112**（⏸ 待决）的证据 bullets 整体迁入本归档，
工作文件保留 `- **Scope**` 一行（与本仓其余条目体例一致）并换上一行 `- **Evidence**` 指针。

**口径（三个数互不相同，别混用）**：

- 归档侧持有的 P2-112 证据段：**43 行 / 3730 字节**（逐字）。
- 工作文件真正迁出：**43 行 / 3730 字节**，换成一行指针（约 120 字节）。
- 本轮 findings 总体：**1342 行 / 148932 字节 → 1308 行 / 147382 字节**
  （净 **-34 行 / -1550 字节**），而这已经是**新增 3 条条目之后**的净值。

**硬证明**（脚本实测）：条目标题 148 → 151（+3，既有 148 条逐字不变且顺序不变）、
链接目标**只增 a45**、其余 69 个目标的**引用次数逐个不变**、迁出的 43 行**逐字**仍在
本归档里、P2-112 的决策段（Severity / 建议修法 / Status）**逐字保留**。

**探针自身的三处错**（都记在这里，避免下次重犯）：

1. 第一版对拍四张「必填参数」表时**用原始方法名求交**，结果四张表「两两毫无交集」——
   真相是命名风格不同（snake_case / PascalCase / camelCase）⇒ 判定必须**先归一化**再求交。
2. 第一版只查了 EN 版 ⇒ 漏掉 ZH 版；**按断言清扫**后才知道同一句在仓库里有 **8** 处。
3. 找「可迁出的证据段」时第一版按 `- **Severity**` 定位决策段起点，漏掉了那些
   **没有 Severity 字段**的条目；改成「第一个出现任一决策字段」才找齐。
