# 后端审查证据归档 -92（2026-10-21）

> 第 391 轮（Go SDK + SDK README，doc round 290）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**155 行 / 13979 字节**）。
> 本文档体量：**155 行 / 13999 字节**。

## 本轮新写的两条（正文逐字保留在本归档）

### P2-177: Go SDK 的**两个校验错误类型**零测试钉住 —— 既有用例断言二选一，故「返回哪个类型」都成立；README 发布的**完整消息**也无人逐字钉住

- **Scope**: → [scope-9][a6]
- **Problem**: Go SDK 有**两个**校验错误类型，它们是不同的 Go 类型，`errors.As` 永不互穿：

  | 类型 | 定义处 | 消息格式 | 用途 |
  |------|--------|----------|------|
  | `cortexmem.ValidationError` | `error.go:13` | `cortex-ce: validation error on <field>: <msg>` | 必填参数（`sessionID` / `projectPath` / `toolName` …） |
  | `dto.ObservationUpdateValidationError` | `dto/observation.go:137` | `cortex-ce: ObservationUpdate validation error: <msg>` | **仅** `ObservationUpdate`（空更新与 content/narrative 冲突） |

  ⇒ `cortexmem.IsValidationError(err)` 对 dto 那个类型返回 **false**，`dto.IsObservationUpdateValidationError(err)`
  对 cortexmem 那个类型返回 **false**。**两个 helper 都是「非此即彼」的判别器，而 SDK 的主 helper
  恰好对另一半场景返 false** —— 调用方写 `if cortexmem.IsValidationError(err) { /* 客户端错，别重试 */ }`
  就会在「空更新」这个场景上**漏判**。
- **既有断言的缺口（逐处定位）**：
  - `client_test.go:4460` `TestUpdateObservation_UsesValidationError` 断言的是
    `!cortexmem.IsValidationError(err) && !dto.IsObservationUpdateValidationError(err)` 才算失败 ——
    也就是**二选一即可通过** ⇒ 它对「返回了哪个类型」**完全无感**：把实现改成返回
    `*cortexmem.ValidationError`，这个用例照样绿。
  - `client_test.go:4236` `TestUpdateObservation_EmptyUpdate_Rejected` 只做
    `strings.Contains(err.Error(), "at least one field")` 的**子串**匹配 ⇒ **改前缀测试仍全绿**。
  - `dto/dto_test.go:379` `TestObservationUpdate_Validate_Empty` 断言了**类型**，但**不断言消息**。
  ⇒ 三处合起来的结果：**错误类型与完整消息**都没有被任何用例逐字钉住。
- **文档侧放大**：Go README 把空更新消息当**代码块**发布（`README.md` 与 `README-zh-CN.md` 各一处），
  还把 `*dto.ObservationUpdateValidationError` 写成了 `ValidationError` ⇒ 文档发布的是一个
  **别的类型**，且没有任何测试把「文档发布的那句话」与「运行时返回的那句话」绑在一起。
- **修法（已实施）**：新增 `go-sdk/cortex-mem-go/error_contract_test.go`（包 `cortexmem_test`，
  **9** 个顶层用例 / **17** 个运行时用例），**只加测试、SDK 主源码零改动**：
  1. `TestUpdateObservation_EmptyUpdate_ExactErrorContract` —— 逐字断言完整消息、精确类型
     `*dto.ObservationUpdateValidationError`、`Field == "update"`、
     `dto.IsObservationUpdateValidationError(err)` 为 true；并用 `newValidationClient(t)`
     断言**不发请求**（该 helper 在 cleanup 里检查命中数，非零即红）。
  2. `TestUpdateObservation_EmptyUpdate_IsNotACortexmemValidationError` —— 断言
     `cortexmem.IsValidationError(err)` 与 `errors.As(err, **cortexmem.ValidationError)`
     **都是 false**，即两个类型**互不冒充**。
  3. `TestUpdateObservation_EmptyObservationID_ExactErrorContract` —— 另一侧：逐字断言
     `cortex-ce: validation error on observationID: observationID is required`、类型
     `*cortexmem.ValidationError`、`Field == "observationID"`、
     `cortexmem.IsValidationError` 为 true、`dto.IsObservationUpdateValidationError` 为 false。
  4. `TestUpdateObservation_ObservationIDIsCheckedBeforeTheUpdate` —— **顺序**：两个参数同时非法时，
     先报 `observationID`（`client_methods.go:212` 的检查早于 `update.Validate()`）。
  5. `TestUpdateObservation_ContentNarrativeConflict_ExactErrorContract` —— 第二个更新级错误：
     同一 dto 类型，但 `Field == "content|narrative"`、消息逐字不同。
  6~9. 四个 **README 一致性**用例（见 P2-178）。
- **变异测试（单变量，每次改完按 sha256 逐字节还原）**：
  | 变异 | 改动 | 命中的测试 |
  |------|------|------------|
  | A | `dto/observation.go` 的运行时前缀改为 `… validation error CHANGED: ` | **4** 个红：两个运行时契约 + 两个 README 一致性 |
  | B | `README.md` 的 `Facts: []string{}` 表格行退回失实形态 | **2** 个红，**且只有 EN 子测试红、ZH 仍绿**（单变量归因隔离） |
  | C | `error.go` 的 `IsValidationError` 改成永远返回 true | **2** 个红：恰好是断言它为 false 的两个 |
  | 阴性对照 | `error.go` 只加一行注释（行为零变化） | **0 红** |
  ⇒ 四个被变异的文件（`dto/observation.go` / `error.go` / `README.md` / `README-zh-CN.md`）
  全部按 sha256 **逐字节还原**（`ALL MATCH`）。
- **后果**：Go 侧单元测试总数 **373 → 390**（根模块 307 → **324**：core 240 → **257**，dto 67 不变；
  适配器与示例模块 eino 8 + genkit 13 + langchaingo 12 + `examples/http-server` 33 不变）。

### P2-178: Go README 双语 **2 处**把空更新消息渲染成**漏前缀**的形态 —— 与运行时返回的字符串**不是同一个**；另 2 处的「消息完全相同 + 类型写错」是 **P2-163 的 Go 侧结转**，本轮一并修

- **Scope**: → [scope-9][a6]
- **Problem**: Go README 双语共 **4** 处**失实**，逐处如下（改前原文）：

  | # | 位置 | 失实点 | 实际 |
  |---|------|--------|------|
  | 1 | `README.md:248-249` | 「returns a **`ValidationError`**」+「The same rule and message apply in the Java, Python and JS SDKs」 | 类型是 `*dto.ObservationUpdateValidationError`；另三家的**前缀各不相同** |
  | 2 | `README.md:274` | 表格行渲染成 `ValidationError: at least one field must be provided for update` | 运行时返回 `cortex-ce: ObservationUpdate validation error: at least one field must be provided for update` |
  | 3 | `README-zh-CN.md:239` | 同 #1（中文版） | 同 #1 |
  | 4 | `README-zh-CN.md:262` | 同 #2（中文版） | 同 #2 |

  ⇒ #1 / #3 是 **P2-163**（四家 README 的「空更新错误消息完全相同」）的 **Go 侧结转**；
  #2 / #4 是**新发现**的一类失实：**文档发布的字符串与运行时返回的字符串不是同一个**。
- **四家对拍（全部取自源码，非文档）**：

  | SDK | 错误类型 | 完整消息 | 发请求？ |
  |-----|----------|----------|----------|
  | Go | `*dto.ObservationUpdateValidationError` | `cortex-ce: ObservationUpdate validation error: at least one field must be provided for update` | 否 |
  | JS | `ValidationError`（field `update`） | `cortex-ce: validation error on update: at least one field must be provided for update` | 否 |
  | Python | `ValidationError`（field `update`） | `cortex-ce: validation error on update: at least one field must be provided for update` | 否 |
  | Java | `IllegalArgumentException` | `at least one field must be provided for update`（**无前缀**） | 否 |

  **规则**四家相同（空更新一律在客户端被拒、不发请求）；**类型**、**前缀**、**消息主体**三层
  必须分开陈述 —— 只有**消息主体**是共用的。
- **修法（已实施，纯文档、零可执行行）**：
  - 双语两处段落改为：点名 `*dto.ObservationUpdateValidationError`、把 Go 的完整消息保留在代码块、
    新增一张**四家对拍表**（类型 + 完整消息）、并加一段说明「在 Go 里这与必填参数检查抛出的
    `cortexmem.ValidationError` **不是同一个类型**，`cortexmem.IsValidationError` 对空更新返回 false」。
  - 双语两处表格行的消息**补全前缀**。
  - ⭐ **按断言清扫**：JS EN/ZH 与 Python EN 早已把四家对拍表写对（`js-sdk/cortex-mem-js/README.md:226-231`）；
    Go / Python 中文版与 Go 英文版**无**「另三家消息相同」这句断言 ⇒ 本轮**只改 Go 这 4 处**。
- **新增的文档护栏（4 个 README 一致性用例）**：
  1. `TestREADME_PublishedEmptyUpdateMessageMatchesRuntime` —— 把 README 里发布的字符串与
     **运行时实际返回的字符串**比对（不是与常量比对 ⇒ 运行时消息一改，测试立刻红），
     并要求每一处消息体都带**已登记的前缀**（Go 的 / JS 与 Python 的；裸消息只允许出现在点名 Java 的行上）。
  2. `TestREADME_FactsRowPublishesTheFullMessage` —— 单独钉住那两行表格（EN/ZH 共用 `Facts: []string{}` 标记）。
  3. `TestREADME_NamesThePerSDKErrorTypes` —— 要求双语各点名四家的错误类型与 JS/Python 前缀。
  4. `TestREADME_DoesNotClaimCrossSDKSameness` —— 两条失实断言**不得回潮**（中英各两个字符串）。
- **顺带修掉**：Go README 双语首屏与 Testing 段硬编码的单元测试总数（**8** 处）在本轮加完
  **9** 个顶层用例后立刻过期（373 → **390**，根模块 307 → **324**，core 240 → **257**）⇒
  按 `go test -json` 实测值逐处改准，测量日期仍为 **2026-10-21**（同一天）。
  该数字的**形态问题**（写死即过期）仍记在 **P2-90**，本轮**只同步数值、不改形态**。

## 迁移段：第 317 轮的「加注（不改写归档）：归档条目 30-2 的跳过理由已经过时」

> 本段原为工作文件里的一个**独立加注块**（**1910 字节 / 21 行**，位于 P2-82 与 P2-83 之间）。
> 本轮按 **C8**（⏸ 决策块里的证据段整体迁入归档）把它迁入本归档，**工作文件侧保留核心判断与一行指针**。
> 下面的正文**逐字保留**，唯一的改动是把引用式链接 `[`…-history`][a22]` 展开成可解析的行内路径。

### 加注（不改写归档）：归档条目 **30-2 的跳过理由已经过时**

- **原条目**：[`…-history`](../archive/2026-09-30_backend-review-findings-history.md) 第 1894 行（表格行 `30-2`）：
  「`/api/logs` 和 `/api/logs/clear` 端点无认证/授权保护。日志内容可能包含敏感调试信息……
  ⭭ 跳过（**设计决策：服务绑定 localhost，外网不可达**；添加认证属于架构变更）」。
  **归档不得修改**，故在此加注。
- **该前提今天不成立**（第 317 轮实测）：`docker-compose.yml:88` 的端口映射是
  `"${SERVER_PORT:-37777}:37777"`，**没有主机 IP 前缀 → 发布到所有网卡**；
  `:60` 另设 `SERVER_ADDRESS: 0.0.0.0`（即 P2-70）。后端**唯一的 Servlet Filter 是
  `MdcAutoFilter`（关联 ID，不是鉴权**），全仓无 `SecurityFilterChain`
  → **推荐的 Docker 部署下，同网段任何人可读日志、并可 `POST /api/logs/clear` 抹掉它**。
- **与 30-2 的关系**：**这不是新缺陷，是既有记录的前提失效**。
  原条目本身仍然成立（无认证属实），**只是「外网不可达」这条免责理由在 compose 路径下已不适用**。
  危害的措辞可以更准：`clear` 是**破坏性**端点（`LogsController:165` 用
  `Files.writeString(todayLog, "")` 截断），因此「可读」之外还有「可销毁」。
- **未验证的部分，不写**：本轮**没有调用** `POST /api/logs/clear`（它是破坏性的，
  且会毁掉正在用于关联的日志）——截断行为是**读码确认**（`:161-185`），**非实测**。
  **实测的只有只读部分**：`GET /api/logs?lines=3` 返回
  `totalLines=48297 / returnedLines=3 / files=['claude-mem-2026-10-06.log']`，
  且钳位与十六进制前缀解析与 `docs/API.md:2896-2897` **记载一致**（`0`→1、`-5`→1、
  `99999`→10000、`0x10`→16）——**故该控制器与对应文档本身零缺陷**。

## 压缩记录（三个数不可混用，方向相反）

| 口径 | 数值 |
|------|------|
| 归档侧持有量（本轮迁入 + 新写的正文） | 见本文档头部「本文档体量」一行（**唯一来源，勿在此重复数字**） |
| 工作文件侧**迁出**量（第 317 轮加注块） | **21 行 / 1910 字节** |
| 工作文件侧**写入**量（本轮新条目 + 3 处补注 + 1 个链接定义） | `git diff --numstat` 实测 **+22 行** |
| 工作文件**全轮净变化** | **+1 行 / +867 字节**：起点 **1231 行 / 148446 字节** → 终值 **1232 行 / 149313 字节** |

- **工作文件本轮写入前只剩 1554 字节余量**（阈值 150000 字节 / 1500 行）⇒ 必须先腾空间。
- **C8 第四次启用，但对象换了**：本轮之前 14 条 ⏸ 的证据段在前四轮已迁完，C8 对 ⏸ 条目已耗尽；
  本轮改为迁移**一个独立的加注块**（第 317 轮的「30-2 跳过理由已过时」，**21 行 / 1910 字节**）
  —— 工作文件侧留「与 30-2 的关系」的核心判断 + 一行 `- **Evidence**` 指针，**证据段逐字在本归档**。
- ⭐ **压缩记账的三个数**：**归档侧持有量**（本文档体量）、**工作文件侧迁出量**（1910 字节）、
  **工作文件全轮净变化**（+867 字节）—— 三者**不是同一个数、方向也不相同**：
  本轮迁出 1910 字节，却同时写入了新条目正文与补注，净变化仍是**正数**（+867）。
  把「迁出量」当成「净变化」会把一个**仍在增长**的文件误报成在收缩。
- **C6 彻底枯竭**：正文里重复 ≥2 次的内联归档路径字面量**一个都没有**（全文扫描 0 命中）。
- ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。
