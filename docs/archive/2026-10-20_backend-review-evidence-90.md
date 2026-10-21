# 后端审查证据归档 -90（2026-10-21）

> 第 389 轮（Backend + API 文档，doc round 288）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1221 行 / 149444 字节**）。
> 本文档体量：**327 行 / 24061 字节**。

## 本轮的两条主线

① **一条「机器可读契约与手写契约之间没有任何东西把两者连起来」的缺口** —— 后端是本仓最大的暴露面（14 个 Controller、66 条路径、**71** 个操作），而它的**端点表面零测试钉住**：16 个后端测试类里**一个 `"/api/..."` 字面量都没有**。这与 Python demo（26 个，第 382 轮）、Java demo（44 个，第 388 轮）是同族，而**后端这块最大的面此前是唯一没管的**。

② **一份「必填」被写进「默认值」列的中文参数表** —— 必填参数**没有默认值**，该单元格自相矛盾；且它是第 160 轮那条（默认值跑进必填列）的**镜像变体**。同一次扫描还带出一处**只记录不实施**的遗漏：`/api/context/preview` 的 `platformSource`在活体 OpenAPI 与代码里都真实存在（**会切换到另一条仓储查询**），却在中英双语文档里**全篇 0 次提及**。

本轮的**全部改动零行为变更**：后端主源码一行未动（只加了一个测试文件），文档只改了 2 张表。

---

## P2-171: 后端的**端点暴露面**（14 个 Controller、66 条路径、**71** 个操作）**零测试钉住** —— 16 个后端测试类里**一个 `"/api/..."` 字面量都没有**

- **Scope**: → [scope-9][a6]
- **暴露面（程序化提取，不是手数的）**：14 个 `@RestController`、**71** 条 `(verb, path)`、**66** 条路径。
  注解共 **72** 处映射，减去 `ViewerController.java:214` 那条**整段注释掉**的 `GET /api/concepts` ⇒ **71** 条在服务，
  与活体 `/v3/api-docs` 的 **66 paths / 71 operations** **逐条相等**。
- **缺口（零测试钉住）**：`src/test/java` 下 **16** 个测试文件、**178** 个用例里
  `grep -roh '"/api/[a-z0-9/{}-]*"' src/test/java` **零命中** ⇒ **没有任何一个用例断言过端点存在**。
  后果具体：改一个 `@GetMapping` 的路径，**没有任何测试会红**，而 `docs/API.md`（手写）与
  springdoc 生成的 OpenAPI（运行时）会**同时**变成另一个样子，**没有任何东西把两者连起来**。
- **修法（本轮已修，Backend 代码方向，只加测试，实现零改动）**: 新增 `BackendEndpointSurfaceTest`（**3** 个用例）：
  ① 71 条 `(verb, path)` 的**集合相等**（多一条少一条都红，**不是** `containsAll`）；
  ② 无重复的 `(verb, path)`；③ 扫描本身**非空跑**（Controller ≥ 14、71 条、66 条路径）。
  用**反射读注解**，不起 Spring 上下文、不连数据库 ⇒ 可离线跑。
- **变异测试（两次，各恰好 1 红）**:
  - 变异 A：`ViewerController` 的 `@GetMapping("/stats")` → `"/stats-extra"` ⇒ **恰好 1** 红
    （`exposedSurfaceIsPinned`），差异恰为 `GET /api/stats` → `GET /api/stats-extra`；
  - 变异 B：新增 `@GetMapping("/p389-extra")` ⇒ **恰好 1** 红（同一用例）。
  两次后 `ViewerController.java` 按 sha256 `c5c0f9d4bca481311a11d5f62390120d741d6a1fa861df5d20d1840ec07b62cf` **逐字节还原**，
  `git diff src/main/java/` 为空。
- **Status**: ✅ **已修（389 轮）** —— 后端单测 **178 → 181** 全绿。
  ⭐ 同族第三例：Python demo（26 个端点，第 382 轮）、Java demo（44 个，第 388 轮）此前各已补一条暴露面测试，
  **后端这块最大的面此前是唯一没管的**。

## P2-172: ZH 的参数表把 **「(必填)」写在「默认值」列** —— 必填参数**没有默认值**，该单元格自相矛盾，且与 EN 表结构不同

- **Scope**: → [scope-9][a6]
- **两处错列表的原文（活体 37790，2026-10-21）**:

  | 位置 | 端点 | 错在哪 |
  |---|---|---|
  | `docs/API-zh-CN.md:994` | `GET /api/context/preview` | `\| \`project\` \| string \| (必填) \| 项目路径 \|` —— **「(必填)」落在「默认值」列** |
  | `docs/API-zh-CN.md:1767` | `GET /api/search/by-file` | 同上 |
  | `docs/API-zh-CN.md:1768` | `GET /api/search/by-file` | `filePath` 同一形态 |

  EN 对照表同两个端点用的是 **5 列** `Parameter | Type | Required | Default | Description`，
  `project`（与 `filePath`）标 **`Yes`**、默认值写 **`—`**。
- **为什么说它自相矛盾**：「默认值」列的语义是「不传时用这个值」；而**必填参数根本不允许省略**，
  也就**没有默认值**。把「(必填)」写进那一列，等于宣称「这个参数的默认值是『必填』」。
- **ZH 文档自己的体例**：`docs/API-zh-CN.md` 的 **17** 张参数表里，
  **9** 张是 `参数 | 类型 | 必填 | 说明`，**2** 张是 5 列的 `参数 | 类型 | 必填 | 默认值 | 说明`
  （`:1072` 的 `/api/context/timeline`、`:1167` 的 `/api/search`），**6** 张是 `参数 | 类型 | 默认值 | 说明`。
  ⇒ **5 列体例在本文档里已有先例**，这 2 张表只是没照做。
- **全量扫描的其余 4 张**（`/api/context/recent` `:1034`、`/api/observations` `:1534`、
  `/api/sessions` `:1822`、`/api/logs` `:2156`）**也缺「必填」列**，但它们的参数**全部可选**
  （活体 OpenAPI 全部 `required: false`，EN 对应表全部写 `No`）⇒ **不丢信息**，
  按「宁少勿错」与「遗漏 ≠ 失实」**未改**。
- **修法（本轮已修，文档方向，ZH 侧 2 张表，零可执行行）**: 改成 5 列体例
  `参数 | 类型 | 必填 | 默认值 | 说明`，必填项标 `✅`、可选标 `❌`，必填项的默认值写 `—`
  —— 与 EN 表**逐行对应**，也与 ZH 自己的 `:1072` / `:1167` **逐字同构**。
- **Status**: ✅ **已修（389 轮）** —— ⭐ 是第 160 轮那条（ZH 参数表把**默认值**写进**必填**列）的
  **镜像变体**：那次是「默认值跑进必填列」，这次是「**必填跑进默认值列**」—— **同族两个方向**。
  修后 `(必填)` 出现在「默认值」列的次数 **3 → 0**；EN/ZH 参数表**行级零分歧**。

## P2-173: `/api/context/preview` 的 `platformSource` 参数在活体 OpenAPI 与代码里**都真实存在**，但中英双语文档**全篇 0 次提及**

- **Scope**: → [scope-9][a6]
- **活体契约（37790，2026-10-21）**：`GET /api/context/preview` 的 OpenAPI 参数表里有
  `platformSource`（`in: query`, `required: false`, `example: "claude"`）—— 它是**该端点 10 个查询参数之一**。
- **代码里它真的起作用**：`ContextController.previewContext`（`:343`）把它传给
  `contextService.generateContextWithFilters(..., platformSource)`（`:409`），
  而该方法在 `ContextService.java:371-383` **按它是否为空切换两条不同的仓储查询**：
  `findByTypeAndConceptsWithSessionLimit` ⇄ `findByTypeAndConceptsWithSessionLimitForPlatformSource`
  （摘要侧同理，`findByProjectPathOrderByCreatedAtEpochDesc` ⇄ `findByProjectPathAndPlatformSourceOrderByCreatedAtEpochDesc`）。
  ⇒ **它不是装饰性参数**。
- **文档侧全篇 0 次**：`grep -n platformSource docs/API.md` 命中 **7** 处，
  **全部属于 `/api/observations` / `/api/summaries` / `/api/prompts` / `/api/sessions` 与一条 changelog**，
  **无一处属于 `/api/context/preview`**；ZH 侧同样 5 处，**全部不属于该端点**。
  该端点的**参数表、散文、请求示例、changelog** 里**都没有**它。
- **后果**：读 API 文档的人**无从得知**这个参数存在 ⇒ **机器可读契约有、手写契约没有**。
  （对照：作者给 `/api/observations` 等三次新增 `platformSource` 时，**都同步改了参数表**并记进 changelog
  `2026-05-03 | 0.1.0-beta+37`。）
- **处置（⏸ 只记录不实施，判据「遗漏 ≠ 失实」）**: 补一行参数表是**抄权威**（活体 OpenAPI 与代码都已确定该参数存在且可选），
  但 changelog 表明作者对同一参数的另外三次新增**都同步改了表** ⇒ 本条更像「**加参数时漏了表**」而不是「有意不写」。
  三条候选修法：①EN/ZH 两张 preview 表各补一行（与机器可读契约对齐）；
  ②在 preview 端点的 `@Parameter` 描述里注明「文档未收录」并加 changelog；
  ③若该参数本就不该对外，则从端点上移除（**对外契约变更**，须作者裁定）。
- **Status**: ⏸ **只记录不实施** —— 文档完整性问题，等作者裁定。

---

## 本轮迁出的 ⏸ 条目证据段（按第 254 轮规则迁出）

### P2-87 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  | 查证位置 | 结果 |
  |---|---|
  | 本仓全部 refs / 历史 | 无 |
  | `git rev-list --all` 中以 `ed3` 开头的提交 | **零条** |
  | reflog | 无 |
  | 悬空 / 不可达对象（`git fsck`） | 无 |
  | `webui/` 子模块历史 | `fatal: could not get object info` |
  | origin 远端（本地与其已同步） | 无 |

  故这段话**有一半无法兑现**：读者按它去 checkout 会直接失败。

---

### P2-21 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

- 测试反而钉死了这个假象：`CortexMemHealthIndicatorTest.health_whenClientThrows_returnsDown`
  用 mock 让 client 抛出并断言 `containsKey("error")`——真实 client 永远产生不了该
  状态，故此用例恒真却毫无保护作用（与第 197 轮同类：测试覆盖的是虚构状态）。
- 核实无误：`"ok"` 大小写判定正确，null body / 非 `ok` / 异常三种情况均正确返回
  `false`，UP-DOWN 三分支本身正确 → [`…-36`][a4] 第 2 块（第 291 轮逐字迁出）。

---

### P2-91 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

- **而且冲突已经真实发生过一次**：巡检自己的归档记录了第 160 轮的
  「**P1-2 导入端点把校验失败报成成功跳过**」（`2026-10-02_health-check-history-6.md:322,409,520`
  与 `history-7.md:102,200,291`）。该条目**如今已不在工作 findings 里**（`grep 导入端点` 零命中），
  已被修复或改号，而**现存的 `P1-2` 是另一个主题**。
  故 `P1-2` 在本仓至少指代过：①导入端点校验、②Java demo 路径穿越、③队列健康指示器。

---

### P2-27 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

- 该缺陷为何能存活: `tsconfig.json` 的 `exclude` 含 `"**/*.test.ts"`，而 `npm run lint` 就是 `tsc --noEmit`
  ——**测试文件根本不参与类型检查，于是类型层与断言层之间的裂缝没有任何自动关卡。已独立立为 P2-42**。
- **已修（注释层，行为一字未改）**: `ObservationUpdate` docstring 与 `is_empty()` /
  `to_wire()` 两处 `continue` 注释已按现状改写（428 测试全过）→ [`…-36`][a4] 第 4 块（第 291 轮逐字迁出）。

- **第 362 轮的事实更正（本条标题与 Problem 的一半已被推翻；P2-26 承诺的「下方补注」即本段）**:
  **本条成立，但只对 dataclass 路径成立。** `client.update_observation` 的 **kwargs 路径
  既不查 `None` 也不查空 dict**，故 `content=None` 真的把 `{"narrative": null}` 送上 wire、
  `extracted_data={}` 真的把 `{"extractedData": {}}` 送上 wire；而后端 PATCH 语义是
  **「显式 null ⇒ 清除该字段」——故 Python 其实能清空这两列，只是只有 kwargs 路径能**。
  **本轮活体端到端实测**（建观测 → SDK kwargs 路径 PATCH → `psql` 查 `mem_observations`）：
  kwargs `content=None` 后 **`content` 列为 NULL**、kwargs `extracted_data={}` 后
  **`extracted_data` 列为 `{}`**；两条 dataclass 路径均抛 `ValidationError`、**不发请求**、
  **库两列不变**（对照组）；后端侧另用 curl 直连 PATCH 独立确证同型结果。
  **因此本条 Problem 里「Python 两种都发不出」「**一条已有 extractedData 的观测无法通过
  Python SDK 清空它**」为失实（对 kwargs 路径），Status 里「让 `{}` 发上 wire……**从「不变」
  变成「落 `{}`」**」这一表述的事实前提亦不成立——它今天经 kwargs **已经在落 `{}`**。**
  归档 `2026-10-04_backend-review-evidence-11.md` 的**四家能力阶梯表**（「Python 两种都不能 →
  完全不能」）同样只覆盖了 dataclass 路径；**归档文件创建后不得修改，故更正只落在本处**。
  **原文不改写（追加式审计链只可标注），新证据与正文见 P2-116**。

---

### P2-43 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  - 探针实证：`ObservationUpdate(title=None)` → `to_wire() == {}`、`is_empty() == True`；
    而经 demo 活体 `PATCH {"title": null}` → 200 且 `mem_observations.title` 确实变 NULL。
  - 对 P2-27 的影响：该条能力表把 Python 判为「✗（`if val is not None`）」——
    这对 dataclass 路径成立，对 kwargs 路径不成立。按「按断言清扫」的标准，
    那行只覆盖了一半的调用面，应当标注。

- **第 363 轮的补注（本条目在第 247 轮写下的「已在下方补注」，直到本轮才真正落地）**:
  那句补注从未出现在本条目里。 第 247 轮与 P2-26 都写着「P2-27 的能力表应当立即标注
  kwargs 路径……已在下方补注」，而本条目从那时起只有 Problem / Status 两段，
  无任何提及 kwargs 路径的字样；本轮已就地补上（见上方第 362 轮的事实更正）。
  本条的证伪也从未被同步到 JS SDK 的复述点——`js-sdk/cortex-mem-js/src/dto/observation.ts`
  与其**双语 README** 至今仍写着「四家里只有 JS 能把字符串字段清空为 NULL、Python 跳过 `None`」，
  而那句话在第 247 轮就已经被本条目证伪了。本轮已更正（P2-117）。
  另：本条的证伪在第 362 轮被**用 SDK 本身**（而非 demo）做了活体端到端复验——
  kwargs `content=None` 之后 `mem_observations.content` 列**实为 NULL**（`psql` 直查），
  见 P2-116。

---

### P2-90 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

- **连带记录（不单列 finding）**：本地 gitignored 的 `CLAUDE.md` 记着
  **372**（`:52` 与 `:268`「278 core + 61 dto + 13 genkit + 12 langchaingo + 8 eino」），
  出处是 `:407` 一条 **2026-04-17** 的 Recent Work 条目（「Go SDK test count corrected to 372」）。
  与实测 363 差 **9**，且两个分项（278 / 61）也都对不上（实测 236 / 67）——
  即**约半年未更新。按 P2-48 已确立的先例**（`CLAUDE.md` 是 gitignored 的本地文件，
  其问题一律记录不修），本轮**只记录不修**。

- **第 370 轮补注（同日漂移）**：本轮给 `examples/http-server` 加了 6 个测试用例（2 个顶层 + 4 个子用例），Go 侧 `=== RUN` 计数实测从 **363 变成 369**（`examples/http-server` 27 → **33**），**Go README 双语已同步**。**这正是本条「形态问题」最直接的证据**：**同一个测量日（2026-10-20）里数字就变了**——上次第 367 轮复测是 363，本轮同一���再测是 369，故「写死一个数」注定很快过期。

---

### P2-36 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

- **附带一处被丢弃的透明信号: 后端把实际生效值**回显在 `ICLPromptResult.maxChars`，
  Go 的 `dto.ICLPromptResult.MaxChars` **确实有这个字段**（`dto/experience.go:46`），
  但全 SDK **无任何非测试代码读它**——`LoadMemoryVariables` 只取 `result.Prompt`。
  即：直接用核心客户端的调用方**能看到钳制，走适配器的调用方看不到**。

---

### P2-46 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

- 对既有结论的影响（必须如实记录）: 第 249–251 轮的「EXTRACTION 25/0/0 全通过」仍是 25 条全部通过，但 Test 6 走的是兜底分支、Test 14 的断言已因数据累积而恒真。这不使任何一条已记录的修复失效（被修代码路径本就在别处被独立验证），但今后引用该数字须带上这两条限定（基线区块已写明）。

- 同族事实（已修）: 同一幻影端点也出现在设计文档 `phase-3-design/25.md:699`，已改为脚本真正使用的「先 `GET /api/observations` 取 id、再逐条 `DELETE /api/memory/observations/{id}`」并实跑验证（观测数 1 → 0）。该脚本本身行为正确，只有验收脚本是坏的。

---

### P2-62 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

- 影响面：该类未注册为 Bean、生产代码零引用，类 Javadoc 自陈 "not currently wired
  into any processing pipeline" —— 当前影响为零；风险是有人照该 Javadoc 接上后静默改写路径。
  **测试头曾声称覆盖 "expandHomeDirectory edge cases"，而全文零个 `~` 用例**。

---

### P2-77 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

- ⚠️ 一处中间断言先错后改：初判为「`/memory/experiences` 返 400 而 `/memory/icl` 返 500，是两者不对称」。
  那个 400 来自「参数缺失」，不是「参数为空」；补测 `?task=` 后 `/memory/experiences`
  同样返 500。原判据站不住，问题反而更整齐：四个端点在「空」这一形态上完全一致地错。

---

### P2-93 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

- **与 P2-26 / P2-27 的关系：同属「空值被省略 → 无法表达清空」这一族，但不同端点、不同范围**：
  P2-26 是 Go 的 PATCH observations、P2-27 是 Python 的 `extractedData`，而本条是
  `feedback` 端点且**一次命中三家**。机制也不同：Go 靠 `omitempty`，JS/Python 靠**真值判断**。

---

### P2-44 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

- 为什么不是示例代码的 bug: `ChatController` 刻意把两条路径二分——
  带 `conversationId` 的请求走 bridge 且不手动 `begin`；不带的手动 `begin`，
  而 bridge 因无 `CONVERSATION_ID` 直接透传。从不嵌套。故这是误用场景，
  而非已交付代码里的活 bug。

---

### P2-39 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

- **旁证与附带观察**: 逐字迁入 [`…-20`](../archive/2026-10-05_backend-review-evidence-20.md)（271 轮）——含「这个坑已被踩过一次、修过其中一个字段而 `varchar` 宽度至今未校验」的既有痕迹，以及 wire 名报 Java 字段名、`API.md` 三个 import 端点无字段清单这两处附带观察。

---

### P2-57 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

- 同区域一处文档不一致：✅ 已解决（第 271 轮核实）——`client.py` 的 Javadoc 示例原写
  `base_url="http://localhost:37777"`、与真实默认值矛盾，现已改为显式说明为何默认值用
  IPv4 字面量并回指 P2-57（`client.py:51-56`），该引用有效。原文与当时的探针失误一并归档。

---


---

## 压缩记录（第 389 轮）

- **第四次启用 C8**（⏸ 待决条目的证据段整体迁入归档；工作文件留 `- **Scope**` 一行 + 一行 `- **Evidence**` 指针，
  `- **Problem**` 的核心判断、决策段（⚠️ / Severity / 建议修法 / 不修的理由）与 `- **Status**` 留在工作文件）。
  本轮共迁 **14** 条 ⏸ 条目的 **18** 个证据块：**P2-87 / P2-21 / P2-91 / P2-27 / P2-43 / P2-90 / P2-36 /
  P2-46 / P2-62 / P2-77 / P2-93 / P2-44 / P2-39 / P2-57**。

  | 记法 | 字节 |
  |---|---|
  | **归档侧持有量**（18 个迁出块在本归档内的体量） | **9335** 字节 |
  | **本轮起点**（第 389 轮开始时的 HEAD） | **1251** 行 / **149671** 字节（余 **329** 字节） |
  | **本轮终值**（本归档写盘时的实测值） | **1221** 行 / **149444** 字节（余 **556** 字节） |
  | **全轮净变化**（起点 → 终值，含 3 条新条目、文档修正、C8 迁移与收尾去重） | **-30** 行 / **-227** 字节 |

  **四个数不可混用**（第 384 轮记过一次教训）：**归档侧持有量**（9335 字节）是「迁出的内容有多大」，
  **全轮净变化**（-227 字节）是「整个文件瘦了多少」—— 两者方向相反，因为迁移同时**写入**了 3 条新条目、
  文档修正与一行指针，又**迁出**了 9335 字节的证据段。把 9335 当成「省下的空间」会得到相反的结论。
- **C6 已彻底枯竭**：本轮实测工作文件里的**行内**归档链接 **26** 处而 **26** 个目标**互不相同**
  （每个只被引用 1 次）⇒ 引用式转换一条也换不掉；另用正则扫「重复出现 ≥2 次且长度 ≥25 的字面量」，
  全仓只找到 **2** 个（`2026-10-04_backend-review-resolved-3.md` 与 `/api/memory/observations/`），
  合计只能省 **64** 字节 ⇒ **零信息损失的杠杆已用尽**。
- **未动 `## Processing Rules`**：压缩杠杆的**定义**是指令不是证据，留在工作文件。
- **C8 的边界纪律（连续两轮各遇到一次）**：
  ①迁出时若正文含「上表第二行」「已在下方补注」这类**指代**，必须把指代一并改成可解析的
  （P2-79 的 ⚠️ 段、P2-43 的 Status 段本轮各改一处）；
  ②**被迁出的证据段里若含更正**（P2-27 的第 362 轮事实更正、P2-43 的第 363 轮补注），
  工作文件侧必须留一句**自足的更正陈述**（本轮分别留 5 行与 1 行），
  否则工作文件会只剩被推翻的 Problem 而看不到推翻它的那个更正。
- **一次自己踩到的坑（探针/流程自身出错）**：本轮写「按断言清扫」的检查器时，
  第一版解析器**只匹配到 2 张参数表**（请求行正则排除了带查询串的形式）⇒ 它报的「零失配」**是空的**。
  加**自检**（故意翻转一个 `required` 标志，断言检查器必须报错）后立刻暴露。
  随后又暴露两处：词表只认 `Yes/No` 而文档实际用 **`✅/❌`**；「必填」列有时在**第 3 列**、有时在**第 2 列**。
  ⇒ **三次都是「校验器正常返回空/绿灯」这一族**。修好词表与列定位后覆盖 **16** 张 EN 表 / **13** 张 ZH 表、
  **101** 个参数格，并再次用注入样本证明它**能红**。
- **归档文件创建后不得修改**：头部两处体量数字**迭代至不动点**后写盘；写盘后**独立回读校验**
  （占位符已全部替换、头部两处数字互校、3 个新编号齐备、18 个迁出块逐字与 `git show HEAD:` 的原文相同、
  且**没有整块仍留在工作文件里**——后者是**双向验证**，防止把仍在工作文件的行误当成迁出。
  ⚠️ 判据必须是**整块连续串**而非逐行：像 `  |---|---|` 这类通用表格分隔行仍会出现在**别的**条目里，
  逐行比对会误报残留。逐行复核的实测残留为 **1** 行，即 `  |---|---|`（属 P2-88 那张表，与本轮迁出无关）。）。
