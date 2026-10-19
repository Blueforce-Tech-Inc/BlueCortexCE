# Health Check History Archive 14（第 362~364 轮，2026-10-20）

> **Archived**: 2026-10-20，第 366 轮压缩迁移，自 `docs/drafts/health-check-task.md` 第 60–285 行。
> **Rule**: 归档文件创建后不得修改。后续历史达阈值时使用新的日期命名归档。
>
> 以下为第 362 / 363 / 364 轮轮报告的**逐字原文**（含其间的空行）。
> 迁移原因：本文件行数逼近 1500 行上限（迁出前 **1407 行 / 88662 字节**），
> 而第 365 / 366 轮的新报告仍需续写。
>
> **留在本区块下方未迁的三段**（不是历史叙述，是**基线的使用前提**，引用 `45/0/1` 或 `25/0/0` 时须一并阅读）：
> ①「第 264 轮起必须一并引用的第二重限定」；②「`accepted_commit` 的含义」；
> ③「第 251 轮起必须一并阅读的限定」。
>
> 本归档实测 **241 行 / 24467 字节**。

> **第 362 轮：Python SDK（代码）+ 用户指南（文档，doc round 262）**：
> **前提**：**/api/health** → `{"status":"ok"}`，37777 由 pid **10078** 在跑（**11 天 21 小时**，非本会话起的），
> 37778/37790 均空闲。Python SDK 近期 **38 次提交**（活跃），`pytest tests/ -q` → **453 passed**（与权威数一致）。
> **代码方向（Python SDK）—— P2-116（本轮新立，行为记录不修 + 注释层已修，零行为变更）**：
> **`update_observation` 的两种「文档声明等价」的调用风格，实测在「空值」上不等价。**
> `client.py:662-664` 的 kwargs 路径是 `body[wire_key] = kwargs[kwarg]`——
> **既不查 `None` 也不查空 dict**；而 dataclass 路径的 `to_wire()` **两处都过滤**
> （`if val is not None` 跳过 `None`、`continue` 跳过空 `extracted_data`）。
> 后端 PATCH 语义是「**显式 null ⇒ 清除该字段，缺省 ⇒ 不变**」
> （`MemoryController.java:311-395` 三处同型：title / content+narrative / extractedData），
> 故 kwargs 路径**确实清空了这两列**。
> **实测（本轮亲跑，三重独立证据 + 双对照 + 阳性对照）**：
> ①**桩 session 截获 wire body**（`/tmp` 独立脚本，**仓库零改动**）：
> A) `ObservationUpdate(extracted_data={})` → `ValidationError`、**无请求**；
> B) `extracted_data={}` → `{"extractedData": {}}`；
> C) `ObservationUpdate(content=None)` → `ValidationError`、**无请求**；
> D) `content=None` → `{"narrative": null}`；
> E/F) `title="T"` 与 G/H) `facts=[]`（两种风格）→ **两两一致** ✓。
> **阳性对照 4 项全部按预期失败**（未知字段 / content+narrative 冲突〔kwargs 与 dataclass 各一〕/ 空更新）
> ⇒ 探针**有能力**失败，故 A/C 的「不发请求」为真、**不是探针失灵**。
> ②**活体端到端**（`POST /api/ingest/observation` 建观测 → SDK kwargs 路径 PATCH → `psql` 查 `mem_observations`）：
> 初始 `content='P362-NARRATIVE-SENTINEL'`、`extracted_data={"n": 7, "probe": "keep-me"}`；
> dataclass `content=None` → 抛错、**库两列均不变**（对照）；
> **kwargs `content=None` → `content` 列变 NULL**；**kwargs `extracted_data={}` → `extracted_data` 列变 `{}`**；
> dataclass `extracted_data={}` → 抛错、**库不变**（对照）；探针行**残留 0**。
> ③**后端侧独立确证**（curl 直连 PATCH 同一 id）：`{"narrative": null}` → `content` 变 NULL、
> `{"extractedData": {}}` → `extracted_data` 变 `{}`，均返 `200 {"status":"updated"}`
> ⇒ **后端语义与实现一致，问题不在后端**。
> **而 SDK 自己的注释与既有结论都断言「不能清空」**：`dto.py` 类 docstring 写
> "Only non-None fields are sent to the backend"、`is_empty()` 写 "this SDK sends neither."、
> `to_wire()` 写 "this SDK can send neither ``{}`` nor ``null``, so ``extracted_data``
> **cannot be cleared through this client at all**"；
> 归档 `2026-10-04_backend-review-evidence-11.md:21-23` 的**四家能力阶梯表**把 Python 判为
> 「**两种都不能** → **完全不能**」——**那两条机制只存在于 dataclass 路径的 `to_wire()`**。
> **同一发布包内自相矛盾**：`README.md:258-260`（中文版 `:246`）**已经写对**
> （「以关键字参数传入 `extracted_data={}` 即可发送一个显式空的 JSONB 值」），
> 而同一份 README 的 `:114` 又写「只有非 None 字段会被发送到后端」——**与它自己下面第 258 行矛盾**。
> **处置**：**注释层已修（零行为变更）**——`dto.py` 三处 docstring 与 `client.py` 的
> `update_observation` docstring 改为如实描述**两条路径的差异**（并写明「**要清空字段请用关键字参数形式**」），
> `README.md`/`README-zh-CN.md:114` 的绝对化表述改为限定于**数据类形式**并指明例外，**双语同步**。
> **零行为变更由 AST 机器证明**：剥离 docstring 后两文件 **AST 逐字相同**
> （dto.py 97357 字符 / client.py 69004 字符），字符串字面量数 **358 → 358** / **262 → 262**，
> pytest **453 passed**。**行为层记录不修**：两种收法都改变现有调用方的可观测行为
> ——①让 kwargs 路径也过滤 `None`/`{}`（**等于让今天已经能用的清空能力失效**，
> 而 **Python demo 的 PATCH 走的正是 kwargs 路径**，`examples/http-server/app.py:489`）；
> ②新增显式清空入口（**新增公开 API**）。真正的修法要先决定**哪种写法才是 SDK 想支持的清空方式**，
> 属 API 契约决策——**与 P2-26 的判断一致，不重复立条**。
> **并查实一句「承诺过的补注从未落盘」**：P2-26 条目末段明写「对 P2-27 的影响：……**对 kwargs 路径不成立**
> ……**应当标注**」并称「**已在下方补注**」——**但 P2-27 条目（工作文件第 179–194 行）根本没有那句补注**，
> 逐行核对确认只有 Problem / 已修（注释层）/ Status 三段，**无任何提及 kwargs 路径的字样**；
> 而 P2-27 的 Status 仍把「让 `{}` 发上 wire」列为**将来**的选项（称其会「从「不变」变成「落 `{}`」）——
> **它今天经 kwargs 已经在落 `{}`**，故该 Status 的**事实前提亦不成立**。
> **追加式审计链不可重写**，故更正以**带轮号的标注**就地补在 P2-27 条目内，
> **归档里的能力阶梯表不动**（归档文件创建后不得修改）。
> **顺带核实（不另立条）**：`/api/projects` 活体返回 `projects` / `sources` / `projectsBySource` 三个键，
> 而**四家 SDK**（Go `dto/management.go:14-16`、JS `src/dto/misc.ts:24-26`、Python、Java）的
> `ProjectsResponse` **都只有 `projects`** ⇒ 平台来源分组四家一致缺失，与 P2-58 同族；
> `projects` 本身是全量（后端 `findAllProjects()`），丢的只是**分组**、不丢项目。
> **文档方向（用户指南，doc round 262）—— 四个角度全部核实为真、零缺陷**：
> ①**迁移表逐行对磁盘**：DEPLOYMENT 双语各 **17 行**，与 `db/migration/` 的 **17 个 `.sql`**
> **双向差集皆空**，版本号 `[1..8, 11..19]`、**V9/V10 确认缺席**，双语各有「本表是阅读辅助、
> 不是权威来源」的时效声明（EN 写 "trust the directory listing over this table"，
> ZH 写「请以目录为准」——**两句同义**）。
> ②**章节交叉引用全部解析**：DEPLOYMENT 的 `§4.2`/`Section 5.8`、TESTING 的 `Section 3`×2/`5`/`6`
> 逐条对应到真实标题。
> ③**反引号路径 token 仓库级核对**：TESTING 双语各 41 个、DOCKER_README 双语各 5 个、DEVELOPMENT 双语各 15 个、
> DEPLOYMENT 双语各 22 个 token，**未命中仅 7 个**——6 个是「**要你自己创建**的 IDE 配置文件」
> （`.vscode/launch.json` 等；`.gitignore:34,41` 确实忽略它们，文档写的是 "Create `.vscode/launch.json`"），
> 1 个 `plugin/modes/` 经查是 `ModeService.resolveModesDir():167,178` 的**真实回退路径**
> （用户自建目录，仓库内本就不存在）——**均非缺陷**。
> ④**端口表**与代码一致：37777（`application.yml:2` 的 `${SERVER_PORT:37777}`）、
> 5433（`docker-compose.yml:35` 的 `${POSTGRES_PORT:-5433}:5432`），双语一致。
> **变更检测**：指纹 `903928c8…` → **`53db7bac…`**（记录数仍 **2218**）——本轮改了 `.py`，
> 故**按门控跑了完整验收**：回归 **45/0/1**、`EXTRACTION_ENABLED=true` 验收 **25/0/0**（均退出 0）。
> **新鲜度闸门**：本轮改的是 **Python SDK 而非后端** ⇒ 豁免路径适用；并**另行核实**
> **后端源码自 jar 构建（2026-10-08 02:37:42）以来零改动**（`95de35b` 只动了 `backend/pom.xml`
> 的依赖钉与一个脚本，**`backend/src/` 提交为空**）⇒ 在跑的 37777 即当前后端，验收有效。
> **压缩**：findings 字节余量 8109B，**新条目写完即整体迁出**到
> `2026-10-20_backend-review-evidence-62.md`（**114 行 / 10512 字节**，头部数字**迭代至不动点**后写盘），
> 工作文件留标题与 `- **Status**` 一行含指针；**P2-27 的更正标注就地补在该条目内**。
> 终值 **1429 行 / 144507 字节**（余 5493B）。
> **本轮仪器错误三处，全部由验证而非通读发现**：
> ①**把两次运行的观测搞混** —— 首次运行因 `psql` 路径写错，在**第一行** DB 读取就崩掉，
> 建出来的观测**从未被 PATCH 过**；我却拿它去证明「SDK 的 PATCH 没生效」，
> 差点把「**样本从未被处理**」误当成「后端没清」。改用正确的列名
> （表里是 `content`，**没有 `narrative` 列**）并重跑才拿到真读数。
> ②**路径存在性探针的解析基准太naive** —— 首版只试「仓库根」与「文档所在目录」，
> 把 `scripts/regression-test.sh`、`V11__observation_quality.sql`、`pom.xml` 等
> **100 余个真实引用**全报成缺失；改成仓库级索引（按 basename/后缀在 `git ls-files` 里找）后才是真读数。
> ③**AST 校验器自身有 bug** —— `visit_Module` 覆盖后**没调 `generic_visit`**，
> 嵌套的 ClassDef/FunctionDef 的 docstring 未被剥离，于是**把正确的改动报成有差异**；
> 修正校验器后才是「AST 逐字相同」这个真结论。**两次都是仪器错、文档与代码对。**



> **第 363 轮：JS/TS SDK（代码）+ API 文档（文档，doc round 263）**：
> **前提**：**/api/health** → `{"status":"ok"}`，37777 由 pid **10078** 在跑（**11 天 22 小时**），
> 37778~37781/37790 均空闲。JS SDK 近期 **12 次提交**，`vitest run` → **259 passed**（3 个测试文件），
> `tsc --noEmit` 退出 0。
> **本轮先做了一件必须先做的事——查 P2-116 是不是重复立条**：上一轮新立的 P2-116
> （「两种 `update_observation` 调用风格不等价」）在**本轮开头被查出是 P2-43 的重复**：
> **P2-43 早在第 247 轮就立了同一发现**，标题同义（「Python SDK 的两种调用风格对 `None` 的含义相反
> ——dataclass 路径丢弃它、kwargs 路径原样发上 wire」），其 Problem 的机制描述与 Status 的两条收法
> 与 P2-116 **逐条相同**。**第 362 轮立条时未先按主题搜全文，是一次重复立条**——
> 按本文件自身的纪律这一步本该先做。**已在 P2-116 条目内就地标注**（不改写历史正文），
> 并在 P2-43 条目内补上它欠的那句补注。**缺陷计数不因 P2-116 增加**。
> **代码方向（JS/TS SDK）—— P2-117（已修，零行为变更）**：
> **JS SDK 的源码注释与双语 README 三处，仍在断言一件本仓库自己早已证伪过的事。**
> `src/dto/observation.ts:47-49`（修前）写「This makes JS **the only SDK** that can clear a
> string field to NULL: Java omits nulls via `@JsonInclude(NON_NULL)`, Go via `omitempty`,
> and **Python skips `None`**」；`README.md:233-234` 写「This SDK is **the only one of the four**…；
> and **Python skips `None`**」；`README-zh-CN.md:222-223` 写「四家 SDK 中**只有本家能把字符串字段
> 清空为 NULL**……Python 直接跳过 `None`」。**而证伪就在本仓库的 findings 里**：
> **P2-43（第 247 轮）** 已实测「Python SDK 两种调用风格对 `None` 语义相反」，其 Problem 明写
> 「**故 P2-27 能力表把 Python 判「✗」只对 dataclass 路径成立**」；**第 362 轮**又用 **SDK 本身**
> 做了活体端到端复验（kwargs `content=None` 后 `mem_observations.content` 列**实为 NULL**，`psql` 直查）。
> **证伪发生在 2026-10-04，16 天后这三处一字未改**——因为当时的处置只落在 Python 一侧，
> 而复述在**另一个 SDK 的源码与用户文档里**，两边从未被对拍。
> **本轮独立核实了断言里的另外两半（不沿用他人结论）**：Java 的 `ObservationUpdate` 是带
> `@JsonInclude(JsonInclude.Include.NON_NULL)` 的 **record**（`cortex-mem-client/.../dto/ObservationUpdate.java:19`）
> ⇒ **null 被省略、发不出 null** ✓；Go 的 `ObservationUpdate` 是 `Title *string` + `json:"title,omitempty"`
> （`go-sdk/cortex-mem-go/dto/observation.go:100-109`）⇒ **指针没有 null 的表达** ✓。
> **故错的只有 Python 那一半**，Java/Go 两半为真、**保留**。三处已改为如实描述
> （「这两家都发不出 null；Python 的数据类形式跳过 `None`、关键字参数形式原样发上 wire，
> 故 Python 也能清空为 NULL，**本家并非四家中唯一**」），**双语同步**。
> **按断言清扫**：全仓（排除 `dist/` 与 `node_modules/`）对该措辞**零命中**；
> **`dist/` 是 gitignore 的构建产物，但发布到 npm 的包里就带着那句错话**，
> 故已 `npm run build` 重建，**产物里已含更正文本**；`tsc --noEmit` **退出 0**、`vitest` **259 passed**。
> **文档方向（API 文档，doc round 263）—— P2-118（已修，纯文档）+ 其余核验零缺陷**：
> **端点清单双向对拍**：活体 `/v3/api-docs` **66 条路径**，**双语各 66 条全部有记载、遗漏 0**。
> **正文零幻影端点**：文档内 91 个路径 token 中 25 个「活体没有」的候选**逐条查原文后全部证伪**——
> 7 个是**文件路径示例**（`/Users/dev/myproject`、`/path/to/project/CLAUDE.md`、`/src/auth.ts` 等）、
> 1 个是**错误响应示例**（`/api/memory/observations/not-a-uuid`）、1 个是 **OpenAPI 自身的路径**
> （`/v3/api-docs`）、1 个是 **changelog 里的历史散文**（即 P2-118），其余 15 个是**正则截断出的片段**
> （如 `…/{templateName}/latest` 的后半截 `/latest`）——**与第 160 轮同型：逐条查原文后全部证伪**，
> 故不是 25 个缺陷。
> **P2-118 的真缺陷**：**API 文档 2026-10-07 的 changelog 行把请求体表归在
> `POST /api/memory/observations` 名下，而该端点根本不存在**——活体 `/api/memory/observations/{id}`
> **只有 `PATCH` 与 `DELETE`**，创建观测的端点是 `POST /api/ingest/observation`；
> 那张「自带 `Required` 列」的请求体表实际在 `API.md:509`，属于 **`POST /api/ingest/observation`** 段（`:501`）。
> **全库检索该错名只出现在这两行**。按**更新日志的既有体例**（第 120 轮「两版 API changelog 各补一条
> **带日期的记录**」）**不改写 2026-10-07 那行**（它是带日期的历史记录），
> 而是在**双语 changelog 各补一条 2026-10-20 的更正行**，写明正确端点、并写明原行实质内容不变、
> 只是端点名写错。
> **变更检测**：指纹 `53db7bac…` → **`3d1e270e…`**（记录数仍 **2218**）——本轮改了 `.ts`，
> 故**按门控跑了完整验收**：回归 **45/0/1**、`EXTRACTION_ENABLED=true` 验收 **25/0/0**。
> **新鲜度闸门**：本轮改的是 **JS SDK 而非后端** ⇒ 豁免路径适用；后端源码自 jar 构建
> （2026-10-08 02:37:42）以来**仍零改动**（上轮已核实，本轮未动后端）。
> **压缩**：findings 字节余量 5493B，**两条新条目写完即整体迁出**到
> `2026-10-20_backend-review-evidence-63.md`（**96 行 / 8249 字节**，头部数字**迭代至不动点**后写盘），
> 工作文件各留标题与 `- **Status**` 一行含指针；**P2-43 与 P2-116 的标注就地留在工作文件**
> （更正必须落在工作文件，归档与历史正文一律不改写）。终值 **1453 行 / 148372 字节**（余 1628B，
> **已再次贴顶**——P2-115 的阈值上调更显必要，仍待用户决策）。
> **本轮探针自身三处出错，全部在写入前拦下**：
> ①**路径正则只认 `/api/` 开头** ⇒ 活体的 `/stream` 被误报成「文档遗漏」，
> 而中文版 `API-zh-CN.md:2388` 明明写着 `#### GET \`/stream\``——改用「任意首斜杠路径」后遗漏为 0；
> ②**遗漏检测的阳性对照写错**：`.replace(..., 1)` 只替换了 `/api/modes` 的 **12 处出现中的 1 处**，
> 于是对照「失败」——**失败的是对照本身**，重写为全部替换后 **3/3 触发**；
> ③**路径正则的 lookbehind 未排除 `}`**，把 `…/{templateName}/latest` 的后半截 `/latest`
> 当成独立路径，制造出 15 个假「幻影」。
> **另记一条本轮的重大自我更正**：**第 362 轮的 P2-116 是重复立条**（P2-43 早已立过），
> 已按纪律就地标注、不改写历史、缺陷计数不增加——**「重复立条」与「错误归因」一样，
> 必须在下一轮就被认出来并标上，而不是等它变成别人的引用。**


> **第 364 轮：Demo（代码）+ SDK README（文档，doc round 264）**：
> **前提**：/api/health → `{"status":"ok"}`；37777 由 pid **10078** 在跑（**不是**本轮启动的），
> 后端源码自 jar 构建（2026-10-08 02:37:42）以来零改动，故在跑实例即当前后端。
> **代码方向（Demo）新立 P2-119（记录不修）**：**四家 demo 的监听地址 1:3 分裂** ——
> Java demo 随 P1-2（第 294 轮**经用户授权**）硬化为**只绑回环**，而 **Go / Python / JS 三家至今绑所有网卡、
> 零鉴权**，并把「只绑回环的后端」变成**同网段可达**的 API。
> **证据（四家 demo 与后端同机同起，同一条 `lsof`）**：Java `IPv6 TCP 127.0.0.1:37778`、
> Go `IPv6 TCP *:37779`、Python `IPv4 TCP *:37780`、JS `IPv6 TCP *:37781`、
> 后端 `IPv6 TCP 127.0.0.1:37777`（**阳性对照**：同一工具在同一台机上能区分绑定地址，故上列不是探针失效）。
> Python demo 自己的启动横幅即自证 `Running on all addresses (0.0.0.0)` 并列出 `http://10.166.1.125:37780`。
> **连通性判据看退出码而非计时**（第 190 轮教训）：demo 连 LAN IP **exit 28**（SYN 被丢，
> `time_connect=0.0016s`）、后端连 LAN IP **exit 7**（连接被拒）、demo 连回环 **200**；
> `socketfilterfw --getglobalstate` → `Firewall is enabled. (State = 1)` ⇒ **今天挡住它的是主机防火墙，
> 不是应用本身**。**鉴权**：三份源码对 `authorization|apikey|api_key|bearer|token|auth` 各命中 **0**。
> **demo 确实代访后端**（否则「代理放大」只是推断）：经 Python demo 的 `/stats` 返回**后端真实库计数**
> `totalObservations/totalProjects/totalSessions/totalSummaries`；四家 `/version` 均转发后端版本。
> **为何只记录不修**：把默认改成回环 = **收窄**可观测面 = **对外行为契约变更**，
> 且 P1-2 那次同类硬化是**经用户授权**才实施的 ⇒ 按既定纪律先记后定。
> **代码方向其余部分核实为真、零缺陷**：四家 demo 的**请求体字段名逐字段对拍** ——
> `/chat`(project,message,userId,maxChars)、`/feedback`(observationId,feedbackType,comment)、
> `/ingest/prompt`、`/ingest/session-end`、`/observations/create`(8 字段)、`/session/start`、
> `/session/user`、`/observations/batch`(ids)、PATCH `/observations/{id}`(8 字段) ——
> **四家逐字一致**；demo README 的端点表 **26/26** 准确（Python 声称的「26 REST endpoints」与代码**逐条吻合**）；
> Go README 第 83 行的 `37778` 经查**是故意打 Java demo 演示 405**，非端口写错。
> **文档方向（SDK README，doc round 264）四角度核查零缺陷**：
> ①Java README 的 Backend API Alignment 表 **25 行端点逐条对活体全部存在**，
> V14/Phase 3 标注亦逐条为真（`SearchRequest.source` ✓、`ExperienceRequest` 的 source/requiredConcepts/userId ✓、
> `ICLPromptRequest` 的 maxChars/userId ✓、`ObservationRequest` 的 source/extractedData ✓）；
> ②八份双语 SDK README 的 `/api/` 字面量对活体 66 路径**幻影 0**；
> ③四对中英数字经**段落级**比对**全部对等**；
> ④安装坐标逐条对清单吻合（npm `@cortex-mem/js-sdk`、go module 路径、pip 路径、Maven artifact）。
> **三处探针/判据自身出错，均在采信前拦下**：
> ①端点抽取器按「引号紧邻 `/api/`」匹配 → **0 命中**（实际 demo 不硬编码路径，走 SDK 客户端），
> 遂改为「读 demo 源码 + 读 SDK 方法调用」；
> ②双语数字按**行**过滤关键词，而「Java 重试 10/25 个方法」那句的 keywords 在**下一行** ⇒ 误报 ZH 多出「10」「204」，
> 改**段落级**后全对（EN 侧原文确为 `retries **10** of its`）；
> ③压缩脚本的「续行逐行不在工作文件」判据对**空行恒假**（空串是任何字符串的子串）、
> 且 `  |---|---|---|` 分隔行在文件别处也存在 ⇒ 误报 3 处，改**整段切片**判定后 15 条全绿。
> **压缩**：findings 字节余量仅 1628B，先把 **15 条待决条目的 Scope 续行**（指针之后的续行，
> 全是文件路径/行号/代码片段/受控实验记录，**非决策推理**）迁入
> `2026-10-20_backend-review-scope-evidence-10.md`，工作文件保留指针行并**追加 `-10` 引用**（不得断链），
> **148372 → 145125 字节（省 3247B）**；本轮新写的 **P2-119** 写完即整体迁入
> `2026-10-20_backend-review-evidence-64.md`（63 行 / 6937 字节，头部数字**迭代至不动点**），
> 工作文件留标题与 `- **Status**` 一行含指针 ⇒ **146234 字节 / 1408 行**。
> **另**：health-check-task.md 行数逼近上限，本轮一并把第 299~341 轮的历史轮报告
> 迁入 `2026-10-20_health-check-history-12.md`，**三段「必须与基线一并阅读的限定」留在原处**
> （它们是基线的**使用前提**，不是历史叙述）。
> **变更检测**：指纹 `3d1e270e…` / 记录数 **2218**，与基线**逐字一致**（本轮只改 `.md`，不计入指纹）
> ⇒ **代码指纹未变化，跳过重复测试验收**（纯 `.md` 改动，按规则不跑完整回归/EXTRACTION 验收、**不推进基线**）。
> **回归测试 / EXTRACTION 验收**：**本轮未执行**（跳过原因同上）；上一基线仍为 回归 **45/0/1** + EXTRACTION **25/0/0**。
> **Backend 发现与修复数**：**新立 1 条（P2-119，记录不修）**，**修复 0 条**（本轮代码方向是 Demo，Backend 未改）。
> **构建与重启**：**无需构建、无需重启**（零代码改动）；本轮自行启动的四个 demo（37778/37779/37780/37781）
> **已全部停止**，**只停了本轮自己启动的进程**；后端 37777（pid 10078，非本轮启动）**未受影响**，复核仍 HTTP 200。
> **连续 3 轮深入检查**：本轮**零代码修改**，故 3 轮计数不适用（无改动可检查）；**文档层**已按纪律连续复核
> 四轮（端点对拍 → 字段对拍 → 数字对拍 → 双语对拍），**每轮均未发现新问题**。
> **commit**：`patrol: round 364 - three of the four demos still listen on every interface`。
> **未解决问题**：①P2-119（demo 监听地址，待用户决策三选一）；②P2-115（findings 字节上限，建议
> `MAX_LINES=2000` / `MAX_BYTES=200000`，**无需改脚本**，只需同步 `cron-combined-task.md:14` 阈值正文）；
> ③P2-112 / P2-113 / P2-90 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 /
> P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status 段 / 另四份零覆盖设计稿 —— **均已记录在
> `docs/drafts/backend-review-findings.md`，不在本轮范围内，本轮不因此停下**。
