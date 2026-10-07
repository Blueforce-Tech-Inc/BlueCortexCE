# 健康检查历史 22 — 第 323/324/325/326/327 轮（第 331 轮迁出）

> **归档规则**：承 `-19` / `-20` / `-21` 的体例，迁出**较早轮次**的完整报告，
> 工作文件只保留 `## 第 N 轮 — ` 标题与一行自足指针；条目首尾的空行留在工作文件。
>
> **归档文件创建后不得修改。**

> 本批为写入第 337 轮报告后 `docs/drafts/health-check-task.md` 达 **1534 行**
> （行数越线；字节 98863 尚未越线）而迁出 **5 轮**：第 323-327 轮。
> 迁出后工作文件保留第 312-337 轮的标题与指针；其中 312-316 指向 `-21`、
> 317-322 指向 `-22`，均为前两批迁出。
>
> **沿用上一批立下的纪律**：指针必须**自足**（括号配对、以句号收尾）。
> 脚本对本文件现存的全部 **11** 条指针（312-322）逐条校验，**只校验、不改写**。

## 块 1 / 5：第 323 轮报告全文（第 331 轮逐字迁出）

## 第 323 轮 — 2026-10-07T03:42:00+08:00
代码方向：**Backend**（**零缺陷**——审的是全仓最新、覆盖为零的 `ViewerSessionService`）；
文档方向：**架构文档**（doc round 223，**P2-87 已记录不修**）
**P2-85 在本轮结案（3/3）；P2-86 复查 2/3 干净**

### 健康预检

37777 在监听（pid 4029，上轮我启动并保留），health 200。指纹 `a4525469…` / 2218 与基线一致，
工作区干净，HEAD = `028d7f7`。

### 代码方向（Backend）：审 `ViewerSessionService`（181 行，`6e5890d` 新增，零覆盖）

**会话身份这个关键前提查清了**：V1 的 `mem_sessions.content_session_id VARCHAR(255) UNIQUE NOT NULL`
是**全局唯一约束**，而 V18 给 `platform_source` **只加了索引、无唯一约束**。
故删除路径里「先按 `(source, contentSessionId)` 二元组查、再只用 `contentSessionId` 删子表」
**是安全的**——不可能存在两行共享同一 id。这正是该查询形状看着危险、实则无问题的地方。

**六张表的清理完整性逐张核到底**：

| 表 | 机制 |
|---|---|
| `mem_observations` / `mem_summaries` / `mem_user_prompts` | 显式 `deleteByContentSessionId` |
| `observation_feedback` | `ON DELETE CASCADE`（V17 外键） |
| `mem_pending_messages` | `ON DELETE CASCADE`（V1 外键 `session_db_id → mem_sessions(id)`） |
| `mem_sessions` | 显式 `delete` + `flush` |

**无任何孤儿路径。**

**⚠️ 本轮把上轮标注「仅代码可证、未实测」的部分真正实测了。**
上轮因 DELETE 具破坏性而回避的 404/409 运行时行为，本轮**在自建的探针数据上**完成验证：

| 场景 | 结果 |
|---|---|
| 会话仍 `active` 且有 2 条排队消息时删除 | **409**（守卫按设计生效） |
| 正常结束会话后（`completed`、排队消息转 `failed`）删除 | **204** |
| 删除后六张表逐张计数 | **全部 0** |
| 不存在的 session id（负对照） | **404** |
| `DELETE /api/observation/{id}` 真实 id（P2-86 改过的那条路由） | **204**，行消失、无反馈孤儿 |
| 格式正确但不存在的 UUID（负对照） | **404** |

**每次删完都做了负对照**，否则 404 可能来自任何别的原因。
探针数据已**全部清理**（六张表归零确认）。

**顺带实测了文档关于 tool-use 的断言**：`cwd` 省略 → `200 {"status":"accepted"}`、
`cwd:""` → `200`，与 `API.md:376` 的记载一致。
（我第一版探针打到了 `/api/session/start`，它**拒绝**空 project 并返 400——
这解释了库里 9519 条会话 `project_path` 全非空、零条空项目。**打错端点，不是端点坏了。**）

### P2-85 结案（3/3）：四条腿齐了

上两轮验了 Python 与 Java。补上 **JS demo** 这一条：`app.ts:81` 用 `/^[+-]?\d+$/`
按**原始文本**校验，其注释**明确点名** `Number("0x10")` 会返回 16 故不可用作回退。
更关键——它有一条**专属测试** `rejects hex rather than passing the backend decode through`，
docstring 写着「`?limit=0x10` returns 16 rows」。

**那正是我第 320 轮实测到的后端行为。** 即 JS demo 早就写明了正确且有限定的理解，
上轮对 Python 注释的更正，等于把 Python **对齐到 JS 已有的正确表述**。
259 测试全绿。**P2-85 结案。**

### 文档方向（架构文档，doc round 223）：P2-87 —— 版本钉解析不到

核实另一进程对 `ARCHITECTURE.md` 双语的改动。**三处结果分开了**：

**✅ 属实的两处**：`webui` 的 `72e7804b…` **三重吻合**（子模块 `HEAD`、父仓 gitlink、文档字面值）。
Viewer 行新增的 17 条路径**全部活体存在**（19 个操作），双语同步；
且对方**把「15 methods」这个计数删掉了**——新增 3 条路径后旧数字会失效，**删掉是对的**。

**❌ P2-87（记录不修）**：同段落里的**后端参考提交 `ed37a1b2…` 在任何地方都不存在**。

| 查证位置 | 结果 |
|---|---|
| 本仓全部 refs / 历史 | 无 |
| `rev-list --all` 中以 `ed3` 开头 | **零条** |
| reflog / 悬空对象（`git fsck`） | 无 |
| `webui/` 子模块历史 | `could not get object info` |
| origin 远端 | 无 |

该段落的**用途就是让读者能取回这一对版本**，而**配对的另一半完全正常**——现在有一半兑现不了。

**为什么不单方面改写（与 P2-86 的关键区别）**：
P2-86 的正确值是**被权威确定**的（活体 `params=[('id','path')]` 与控制器注解都指向 `{id}`），
所以能照权威直接改。**本条的正确值只能靠推断**：文档所说的「current reviewed capability set」
经 `git log --diff-filter=A` 核实**只由一个提交引入**——`6e5890d`
（`ViewerSessionService` / `ViewerSessionController` / `V19` 三者同为该提交新增）。
**这使 `6e5890d` 成为高度可能的正解，但仍是推断而非判定**——作者也可能指自己某个已 rebase 掉的中间提交。
**写错一个钉比标出一个坏钉更糟**，故记录并附完整证据与建议修法。

### 变更检测

本轮**未改任何代码或既有文档**（仅新增一条 findings 条目）。
指纹仍 `a4525469…` / 2218，**与基线一致** → **不跑完整验收、不推进基线**。
37777 保留运行；37778 早已释放。

### 下一轮

代码方向：**Java SDK**（轮换回到起点）；文档方向：**运维/用户指南**（doc round 224）。

## 块 2 / 5：第 324 轮报告全文（第 331 轮逐字迁出）

## 第 324 轮 — 2026-10-07T04:20:00+08:00
代码方向：**Java SDK**（**P2-88 —— `retrieval-enabled=false` 静默关掉全部 @Tool 捕获**，记录不修）；
文档方向：**运维/用户指南**（doc round 224，**P2-89 —— 部署指南迁移清单双语都停在 V18**，记录不修）
**另修巡检文件自身一处结构缺陷：连续五轮的轮次标题被粘在行尾，使压缩规则形同虚设**

### 健康预检

37777 在监听（pid 4029，本会话早前启动并保留），health 200；37778 / 37790 / 37791 全部空闲。
指纹 `a4525469…` / 2218 **与基线一致**。工作区在本轮开始时干净，HEAD = `bb5bd0e`。

### 代码方向（Java SDK）：审 `CortexMemAutoConfiguration` 的条件装配组合

先按第 316 轮立的规则 grep 了 Java SDK 的 README（中英双语）——这已是该规则第四次执行，
本模块的**属性表与故障排查表都覆盖了本轮要查的四个开关**，故直接进入条件装配本身。

**先否掉一个假设**：`cortexToolAspect` 依赖 `observationCaptureService`，两者同受
`capture-enabled` 门控（`:60` / `:148`），**不存在缺失依赖**。Advisor 与 Tools 直接用
client，不依赖 retrieval service。

然后在 `SpringAiAdvisorConfiguration` 上发现一处**未文档化的耦合**：该配置类整类带
`@ConditionalOnProperty(name="retrieval-enabled")`（`:80`），而 `cortexSessionContextBridgeAdvisor`
（`:83-92`）声明在它**内部**。临时 `ApplicationContextRunner` 探针实测三列：

| Bean | 对照：全默认 | `retrieval=false`+`capture=true`+`bridge=true` | 对照：`retrieval=true`+`bridge=false` |
|---|---|---|---|
| `MemoryRetrievalService` | true | **false** | true |
| `CortexToolAspect` | true | **true** | true |
| `CortexSessionContextBridgeAdvisor` | true | **false** | false |
| `CortexMemoryAdvisor` | true | **false** | true |

**第三列是本轮设计的关键**：它让「两个顾问同生共死」这一竞争解释被排除——
`bridge=false` 时 `CortexMemoryAdvisor` 存活，故第二列里两个顾问的消失确实源于 `retrieval-enabled`。

后果链每一环都单独落实：捕获切面**仍在**（第二列 `true`），但 `CortexToolAspect.java:54`
首行即 `if (!CortexSessionContext.isActive()) return joinPoint.proceed();`，`isActive()` 就是
`CURRENT.get() != null`，而**桥接顾问是生产代码里 `begin()` 的唯一自动调用点**
（全模块 `main` 下 `CortexSessionContext.begin(` 只命中它的 `:81` 与 `:100`，其余全是 Javadoc 与测试）。
负对照**已预先存在于测试套件**：`CortexToolAspectTest:60 whenContextInactive_toolExecutesWithoutCapture`。

影响面已收窄到**选项 A**（依赖 `CONVERSATION_ID` 的纯 ChatClient 用法）；走选项 B 自行
`begin/end` 者不受影响。**记为 P2-88 不修** —— 按「遗漏 ≠ 失实」，README 那几句本身没说错，
且修法形状有歧义（补文档 vs 解耦 Bean），后者属对外契约变更，需作者确认。

### 文档方向（运维/用户指南，doc round 224）：审 `DEPLOYMENT.md` 双语

**先查后判**：文档里 `curl http://localhost:37777/api/health`（`:89`、`:677`）看着可疑——
`AGENTS.md` 与各处惯例都用 `/actuator/health`。**实测两个都返 200**（`/api/health` 与
`/actuator/health`），故**不是缺陷**，不动。

逐条核到一处真实缺项：§4.1 迁移清单**中英双语都停在 V18**，而磁盘上已有
`V19__viewer_session_indexes.sql`。计数三个口径都给出来：EN 表 16 条、ZH 表 16 条
（`diff` 为空）、磁盘 17 个，差集恰为 **V19**。编号断档本身不是缺陷 —— V9 / V10 确实无文件。
顺带核实同表「V1 = 5 core tables」**为真**（`grep -c CREATE TABLE` 实测 5），未借机改动。
**记为 P2-89 不修** —— 无完整性声明，属遗漏；且迁移表与 API 路径模板一样应由一次成体系的扫描统一修。

### 巡检文件自身的结构缺陷：第 319–323 轮的标题全部粘在行尾

**这一条是本轮真正的收获，且是查压缩脚本适用性时撞出来的。**
写本轮压缩脚本前，我先确认 `docs/drafts/health-check-task.md` 是否还能按轮号切块
（该规则承第 306 轮确立：匹配 `## 第 N 轮 — ` 提取）。结果 `grep -nE "^## 第 [0-9]+ 轮"`
**只列到第 318 轮**，可文件尾部明明是第 323 轮的内容。

逐层查到根因：**五轮的标题都还在，只是被粘在了上一轮末行的行尾**——

```
代码方向：**Go SDK**（轮换前进）；文档方向：**架构文档**（doc round 219）。## 第 319 轮 — …
```

共 5 处（319–323）。**危害不是排版难看**：压缩规则的两道防线会**同时失效**——
①按 `^## 第 N 轮` 匹配不到边界；②「块内无嵌套 `## 第 N 轮` 标题」这道断言因为块内
**确实没有**这种标题而**顺利通过**。等于给一次错误切割开了绿灯。这正是 P2-63
（一次压缩整块销毁 P2-30 而未进归档）那类事故的**结构前提**，且当时的校验规则本该拦住它。

已修复：在 5 处标题前补换行（1247 → 1252 行），随后按压缩规则的原样断言复验 ——
**12 轮（312–323）连续、块内无嵌套标题、验收基线块与指纹行完好**。修复为纯结构恢复，
时间戳是当时自记的原值，未推断任何内容。

### 压缩（第 324 轮）

写入 P2-88 与 P2-89 后 findings 达 **1610 行 / 157093 字节**，**同时越过 1500 行与 150000 字节
双阈值**（`doc-growth-check.sh` 退出码 **2**）。把**已完全结案**的 **5 条整条**（`P2-24` /
`P2-63` / `P2-73` / `P2-74` / `P2-81`）逐字迁入新归档 `evidence-41`，工作文件各留标题与一行指针。
做法与 `-40` 不同：`-40` 迁的是仍需留在工作文件的问题陈述之外的 Status 段，本批迁的是整条。

**终验**：删除前快照**双向**比对 —— **内容行丢失 0**；新增行 **26 条逐条归因**
（5 条保留标题的刻意重复 + 5 条指针 + 16 条归档结构行），**残余 0**。比对用**多重集（Counter）**
而非逐位 zip（脚本会插入指针行，逐位比对会把每一行都报成差异）。
1610 → **1500 行 / 146297 字节**，`doc-growth-check.sh` 复跑退出码 **0**。

**本轮三处仪器错，全部在提交前拦下，且都是「断言错、数据对」**：
①边界断言误把多行 bullet 的**续行**当作非法边界而中止 —— 真正该断言的是「切口落在下一个标题行」；
②归档块解析器把块内**空行**全吞掉，谎报 LOST；
③独立复验脚本里 `or` 子句被括号放到了列表推导式**外面**，成永假死代码，使归档头的空引用行
被误判为无法归因。**另修归档头一处真实缺陷**：首版写成 `>（标题 …` 缺 `>` 后空格导致引用块断行 ——
按「归档不可改」**删除重建**。

### 变更检测

本轮**未改任何代码或既有文档**（仅新增两条 findings、新建一份归档、改归档索引）。
指纹仍 `a4525469…` / 2218，**与基线一致** → **不跑完整验收、不推进基线**。
探针（`ZZProbeP288Test`）测完即删；37777 保留运行，其余端口空闲。

### 下一轮

代码方向：**Go SDK**（轮换前进）；文档方向：**SDK README**（doc round 225）。

## 块 3 / 5：第 325 轮报告全文（第 331 轮逐字迁出）

## 第 325 轮 — 2026-10-07T05:05:00+08:00
代码方向：**Go SDK**（**零缺陷**——四个假设全部被实测推翻，其中**一个是本轮我自己先判错的**）；
文档方向：**SDK README**（doc round 225，**P2-90 —— Go SDK README 的测试总数双语 7 处过期**）
**压缩不再贴线：上一批压回 1500 行整、余量为 0，本批一次多迁 4 条**

### 健康预检

37777 在监听（pid 4029，本会话早前启动并保留），health 200；37778 / 37790 / 37791 空闲。
指纹 `a4525469…` / 2218 **与基线一致**。工作区干净，HEAD = `38cd602`。

### 代码方向（Go SDK）：四个假设，四次被推翻

P2-84 已在第 319 轮结案，故本轮换面审 `client_methods.go`（381 行，此前只按点读过）。

**① 10 MiB 响应上限是否有绕过路径？** `client_methods.go:256` 有一处独立的
`json.Unmarshal`，看着像第二条解码路径。追 `data` 的来源 —— 与 `client_impl.go:277/314/333/348`
同源于 `doRequest`，而上限就在该函数内（`:252-260`）。**无绕过，假设不成立。**

**② `HealthCheck` 硬性要求 `status == "ok"`，与后端是否一致？** 活体 `/api/health` 实测返回
`{"service":"claude-mem-java","status":"ok","timestamp":...}` → **一致**。
（对照 `/actuator/health` 返的是 `"status":"UP"`，若当初写死 actuator 端点才会出问题。）

**③ `GetExtractionHistory` 注释称「`limit=0` 会被后端钳到 1，而非用默认值」。**
先做活体探针 —— **四组全返 `[]`**，计数恒为 0。**这个探针不成立**（该测试项目无任何抽取记录，
无法区分钳制行为），改读后端源码取权威值：`ExtractionController.java:117` `defaultValue="10"`，
`:123-124` `if (limit < 1) limit = 1; if (limit > 100) limit = 100;` → **注释属实**。

**④ `doFireAndForget` 的注释称后端「在 30 秒窗口内去重」，据此论证重试幂等。**
这一条我**先判错了**：读到 `existsBySessionAndTool` 的 JPQL
（`PendingMessageRepository.java:38-40`）里**没有任何时间条件**，且全部迁移无任何
`DELETE`/`TRUNCATE` 该表，调用处（`AgentService.java:150-158`）也无时间条件 ——
当场几乎要记成 finding。**全库 grep「30-second」把我拦住了**：后端其实有**两套**去重机制 ——
①ingest 侧 `mem_pending_messages`，无窗口；②保存侧 `mem_observations.content_hash`，
`AgentService.java:239-241` 有**真实的 30 秒窗口**且哈希的正是 title/narrative/facts/concepts。
注释点名的方法是 **`saveObservation`（`:232` 声明）**，即第二套 —— **注释完全正确**。
教训照旧：**先怀疑仪器，再采信读数**，这次要怀疑的读数是我自己的。

另外核实 `GetObservation` 未找到时返回空值：Go `(nil,nil)`、Python `None`、JS `null`
**三家一致**，且 Python docstring 明写「Cross-SDK parity: Go GetObservation(id), JS
getObservation(id)」—— **已文档化的对齐，不是缺陷**。

### 文档方向（SDK README，doc round 225）：P2-90

Go SDK README 双语共 **7 处**硬编码测试总数 `362`（EN `:13`/`:181`/`:192`，ZH `:13`/`:177`/`:187`）。

**计数必须连同口径一起给**（顶层 `^--- PASS` 与子测试 `^    --- PASS` 是两回事）：

| 模块 | 顶层 | +子测试 | 合计 | README |
|---|---|---|---|---|
| 根 `.` | 207 | 29 | **236** | 235 ❌ |
| 根 `./dto` | 67 | 0 | 67 | 67 ✅ |
| `eino` / `genkit` / `langchaingo` | 8 / 13 / 12 | 0 | 8 / 13 / 12 | ✅ |
| `examples/http-server` | 3 | 24 | 27 | 27 ✅ |
| **合计** | **310** | **53** | **363** | 362 ❌ |

**差值完全归因，且是我自己留下的**：`7a78266`（第 319 轮 P2-84 修复）向 `client_test.go`
新增了**恰好一个**顶层测试 `TestNewClient_DoubledTrailingSlashNormalization`
（全库 `git show | grep -E '^\+func Test'` 只命中这一条）→ core 235→236、根模块 302→303、
总数 362→363。**修 P2-84 时改了代码与测试，却没回头更新 README 的计数。**

一处比其余六处更值得记：`README.md:13` 写的是「**Comprehensive tests** — 362 tests」，
**无日期的现在时断言**，现已失实；而 `:192` 明写「Measured on 2026-10-04」—— 按字面**属实**。
**不单方面改**：正确值虽实测权威，但这是 7 处双语硬编码、任何新增测试的提交都会让它再次失效
（第 319 轮就是这么漏的），手改只是把周期性劳动再推一轮；真正该定的是**形态**
——改成不写死数字，还是明接受它会过期并在同批更新。

**连带记录不修**：本地 gitignored 的 `CLAUDE.md:52/:268` 记着 **372**（278 core + 61 dto），
出处是 `:407` 一条 **2026-04-17** 的条目，与实测差 **9**、两个分项也都对不上。
按 **P2-48 已确立的先例**（CLAUDE.md 是 gitignored 本地文件，一律记录不修）。

### 压缩（第 325 轮）

写入 P2-90 后 findings 达 **1548 行 / 150073 字节**，**再次越过双阈值**。
上一批只把行数压回 **1500 整、余量为 0** —— 本轮不再贴线，一次多迁 **4 条**：
`P2-83` / `P2-84` / `P2-85` / `P2-86`（分别结案于第 317 / 319 / 320 / 321 轮）。

**终验**：删除前快照**双向**比对 —— 逐条正文**逐字在归档**、特征行**确已离开工作文件**、
**内容行丢失 0**、新增行 **26 条全部归因**（4 标题重复 + 4 指针 + 18 结构行）、**残余 0**；
归档块 **4** == 指针 **4**。1548 → **1441 行 / 141923 字节**，退出码 **0**。

### 变更检测

本轮**未改任何代码或既有文档**（仅新增一条 findings、两份归档登记）。Go SDK **零缺陷**，
按既定纪律**不制造修改**。指纹仍 `a4525469…` / 2218，**与基线一致** → **不跑完整验收、不推进基线**。
37777 保留运行，其余端口空闲。

### 下一轮

代码方向：**Python SDK**；文档方向：**设计文档**（doc round 226）。

## 块 4 / 5：第 326 轮报告全文（第 331 轮逐字迁出）

## 第 326 轮 — 2026-10-07T05:40:00+08:00
代码方向：**Python SDK**（**零缺陷**）；文档方向：**设计文档**（doc round 226，
`docs/drafts/js-sdk-design.md`，**零缺陷**）
**本轮只读：两个方向都未发现可据以修改的问题，故一处未改**

### 健康预检

37777 在监听（pid 4029，本会话早前启动并保留），health 200；其余验证端口空闲。
指纹 `a4525469…` / 2218 **与基线一致**。工作区干净，HEAD = `cc9944b`。

### 代码方向（Python SDK）：导出面与跨家语义

P2-43 / P2-54 / P2-71 / P2-82 均已覆盖，故本轮换到零覆盖的两块。

**`error.py` 全 289 行 + `__init__.py` 全 110 行。**
`is_retryable_error` 的 docstring 有一条跨家断言：「Go matches net.Error plus
`io.ErrUnexpectedEOF`」。查 Go 侧 `error.go:243` 实为 `io.ErrUnexpectedEOF` **或 `io.EOF`**
两条 —— Python 只列了前者。**属遗漏非失实**（Go 确实匹配了它说的那个），
按「遗漏 ≠ 失实」**不记**。

**导出面逐项对账，不靠眼看**：用 AST 把 `dto.py` 的 19 个、`error.py` 的 28 个公开符号
与 `__init__.py` 的实际导入做双向差集 —— dto 侧**零缺漏**；error 侧只差一个
`raise_for_status`（client 内部用的助手，刻意不导出）。`__all__` 与导入集合**双向完全相等**
（既无「列了却没导入」也无「导入了却没列」）。再补一次**运行时**验证（AST 不等于真能导入）：
`import cortex_mem` 成功、48 条 `__all__` 全部可解析、`from cortex_mem import *` 正常。

**重试谓词语义抽查**：`is_retryable(429)=True`、`(500)=False`；
异常重载 `RateLimitError→True`、`ServerError(500)→False`、`ValidationError→False` —— 与
P2-65 修复后的预期一致。**跨家抽查**：`close()` 两侧都尊重所有权
（Go `ownsHTTPClient` / Python `_owns_session`），不会关掉调用方持有的连接池。

### 文档方向（设计文档，doc round 226）：`docs/drafts/js-sdk-design.md`

248 行，逐条与磁盘对照，**全部可验证断言成立**：

| 断言 | 核对结果 |
|---|---|
| 目录结构（4 个 src + 9 个 dto + 2 个测试 + 2 个示例） | **逐项吻合**，无多无缺 |
| 「There is no `CHANGELOG.md`」 | 确认无此文件 |
| `package.json` 的 `main`/`module`/`types`/`exports` | **与文档 JSON 块逐字一致** |
| 「没有 `"type": "module"`，故 CJS 输出叫 `index.js`」 | `type` **确实缺失** |
| tsup `format: ['cjs','esm']` | 吻合 |
| 26 个方法 = 25 HTTP + `close`；另有 4 logger + `toString` | 实测公开方法 **31**（26+4+1），**精确吻合** |
| 26 行端点映射表 | **25 个 HTTP 方法端点逐条吻合**（`getObservation` 复用 batch） |
| wire format：`SessionStartRequest` 用 `project_path`，其余三个用 `cwd` | **精确吻合**（四个 interface 逐一验过） |
| camelCase 字段（`extractedData`/`requiredConcepts`/`maxChars`/`observationId`/`feedbackType`） | 全部吻合 |

**文档 161-164 行那条「后端没有单条观测读取端点」的论断**带否定对照实测：

| 请求 | 结果 | 作用 |
|---|---|---|
| `GET /api/observations/{id}` | **404** | 证实文档断言 |
| `GET /api/memory/observations/{id}` | **405** | 证实文档断言 |
| `DELETE /api/memory/observations/{id}` | 404 | **否定对照** —— 证明上面的 405 不是「路由不存在」的假象 |
| `POST /api/observations/batch` | 200 | **否定对照** —— 批量端点本身正常 |

### 一处探针错误（提交前识别，未造成假 finding）

第一次扫 JS SDK 端点时漏掉了 `/api/settings`。原因是我的正则
`request(?:JSON<[^>]*>|NoContent)?` 被 `requestJSON<Record<string, unknown>>` 里
**内嵌的 `>` 截断**。是**探针错、数据对** —— `client.ts:541` 确实调 `GET /api/settings`。
若当时不核对「25 个方法怎么只抓到 24 个端点」而直接采信，就会报出一个并不存在的缺陷。

### 变更检测

本轮**未改任何代码或既有文档**。两个方向均零缺陷，按既定纪律**不制造修改**。
指纹仍 `a4525469…` / 2218，**与基线一致** → **不跑完整验收、不推进基线**。
37777 保留运行。

### 下一轮

代码方向：**JS/TS SDK**；文档方向：**架构文档**（doc round 227）。

## 块 5 / 5：第 327 轮报告全文（第 331 轮逐字迁出）

## 第 327 轮 — 2026-10-07T06:15:00+08:00
代码方向：**JS/TS SDK**（**零缺陷**，259 测试全绿）；文档方向：**架构文档**（doc round 227，
`docs/ARCHITECTURE.md`，**零缺陷**）
**连续第二轮只读：两个方向都没有可据以修改的问题**

### 健康预检

37777 在监听（pid 4029，本会话早前启动并保留），health 200；其余验证端口空闲。
指纹 `a4525469…` / 2218 **与基线一致**。工作区干净，HEAD = `536be7a`。

### 代码方向（JS/TS SDK）：零覆盖的 `wire-helpers.ts`

`src/dto/wire-helpers.ts`（125 行）此前从未被任何 finding 覆盖，是整个 DTO 层的
强制转换入口，故整份通读并**用真实模块做受控实验**（不是自己复刻一份实现）。

实验查出两处「相邻输入行为相反」，逐条查证后**两处都不成立**：

**① `safeStringOrStringList`：`"abc"` → `["abc"]`，而 `"123"` → `undefined`。**
根因是 `JSON.parse("123")` 成功但不产出数组，于是走不到「解析失败 → 逗号切分」的兜底。
看起来像 bug，实则 `client.test.ts:2790-2796` **明确钉住了它并写出理由**：
「fallback is not reached: only a *failed* parse falls back to splitting.
The documented contract is arrays, JSON-encoded arrays, and unparseable strings;
a JSON scalar is none of those, so it reads as "absent"」——
并且同一个 describe 里另有两条测 `null`/`undefined`/`42`/`{"a":1}`/`[]`/`"a"`。
**有意设计且有测试固定，不记。**

**② `safeStringArray` 的 docstring 称嵌套数组「is flattened the same way」。**
实测 `String(['b'])=='b'` 与文档举例一致，但 `String(['b','c'])=='b,c'`（**逗号并成一条**，
不是展平）。不过该函数自己的 docstring（57-59 行）已写明这条路径
**「Unreachable through the API: the backend rejects a non-string element in a JSONB list
column at the request boundary, and no stored row contains one. The behaviour is pinned by
tests」**。措辞不够精确、但既已声明不可达且测试固定 → 按「宁少勿错」**不记**。

**一处仪器错误（提交前识别）**：先想用 `npx tsx` 跑探针，撞上 npm 缓存损坏
（`ENOTEMPTY`，与上一轮同源）。改用项目本地 `node_modules/.bin/tsx` 仍报
「does not provide an export named 'safeNumber'」——**先怀疑仪器**：用
Node 原生类型剥离直接 import 同一文件，8 个导出齐全含 `safeNumber`，
证明是 `tsx` 坏了（与 npm 缓存损坏一致），**模块本身无问题**。最终用 Node 原生加载真实模块取数。

全量测试：`npx vitest run` → **259 passed**（client 246 + truncated-body 5 + parse-int-param 8），
与既有记录一致。

### 文档方向（架构文档，doc round 227）：`docs/ARCHITECTURE.md`

1332 行，核 Technology Stack、Security Architecture 两段的全部可验证断言：

| 断言 | 核对 |
|---|---|
| Java 21 | `<java.version>21</java.version>` ✓ |
| Spring Boot 3.3.13 | `spring-boot-starter-parent` `3.3.13` ✓ |
| PostgreSQL 16 | `pgvector/pgvector:pg16` ✓ |
| Proxy = Node.js/axios | `proxy/package.json` → `axios ^1.6.0` ✓ |
| JSONB 列**以 JSON 编码字符串上线**，非数组 | **活体实测**：取一条真观测，`facts` 的 python 类型是 `str`、值 `"[\"Dedup fact\"]"`，`concepts`/`files_read`/`files_modified` 同 ✓ |
| 该 getter 是 `ObservationEntity.getFactsJson()` 返回 `String` | `ObservationEntity.java:282`，带 `@JsonProperty("facts")` ✓ |
| 隐私标签剥离在 `proxy/tag-stripping.js`，四种标签**整体移除** | 文件在；`private` 16 处、`claude-mem-context` 3、`system_instruction` 4、`system-instruction` 3 ✓ |
| Compose 两条端口映射**无主机 IP**、且 `SERVER_ADDRESS: 0.0.0.0` | `docker-compose.yml:35` `${POSTGRES_PORT:-5433}:5432`、`:88` `${SERVER_PORT:-37777}:37777`、`:60` `SERVER_ADDRESS: 0.0.0.0` ✓ |
| 「Currently no authentication (local development)」 | `pom.xml` 中 Spring Security 引用 **0**、唯一过滤器是 `MdcAutoFilter`（日志 MDC，非认证）、**无 `SecurityConfig`** ✓ |
| `.env.example` 存在 | ✓ |

**跨文档印证**：架构文档说这些列以字符串上线，JS SDK 的 `dto/observation.ts:151-154` 注释
说正因如此才需要 `safeStringOrStringList` —— 两处互相印证且都对。
P2-70 当时加注的 Network Security 表与现状**仍然一致**。

### 变更检测

本轮**未改任何代码或既有文档**。两方向均零缺陷，按既定纪律**不制造修改**。
指纹仍 `a4525469…` / 2218，**与基线一致** → **不跑完整验收、不推进基线**。
本轮无新增 findings，故 findings 文件无需压缩（1441 行 / 141923 字节）。
37777 保留运行。

### 下一轮

代码方向：**Demo**；文档方向：**运维/用户指南**（doc round 228）。

