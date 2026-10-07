# 健康检查历史 22 — 第 317/318/319/320/321/322 轮（第 331 轮迁出）

> **归档规则**：承 `-19` / `-20` / `-21` 的体例，迁出**较早轮次**的完整报告，
> 工作文件只保留 `## 第 N 轮 — ` 标题与一行自足指针；条目首尾的空行留在工作文件。
>
> **归档文件创建后不得修改。**

> 本批为写入第 331 轮报告后 `docs/drafts/health-check-task.md` 达 **1579 行**
> （行数越线；字节 104461 尚未越线）而迁出 **6 轮**：第 317–322 轮。
> 迁出后工作文件保留第 312–331 轮的标题与指针，其中 312–316 早于第 326 轮迁入 `-21`。
>
> **同轮一处仪器错误的记录**：我曾用 `grep … | cut -c1-120` 看 312–316 那五条指针，
> 把**被截断的显示**当成了整行，得出「以 `（第 326 轮；` 结尾、括号未闭合」的结论，
> 并准备「顺手修掉」。读整行才发现实为 `（第 326 轮；第 312 轮）。` —— **本来就好好的**。
> 脚本里为此保留了一个**只校验、不改写**的断言：它匹配到 **0** 处待修，
> 这个 0 本身就是「那些指针从未坏过」的证据。**缺陷在读取手段，不在文件。**

## 块 1 / 6：第 317 轮报告全文（第 331 轮逐字迁出）

## 第 317 轮 — 2026-10-06T22:35:00+08:00
代码方向：**Backend**（零缺陷 + 一条**既有记录的加注**）；文档方向：**SDK README**（doc round 217，**P2-83 已修，11 处**）
**本轮起执行第 316 轮新立的流程规则：记 finding 前先查该模块 README 双语 —— 两次都命中**

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `2eadb49a…` / 885 与基线一致，工作区干净。

### 流程规则第一次实战：查在先，避免了一条重复记录

本轮第一个目标是 `LogsController`（208 行，历史零覆盖）。
它有个天然高危形态——「按名字取文件」——但读码即知 `getLogFile(dayOffset)` 的文件名
**由 `LocalDate` 生成**、`dayOffset` 只在循环里取 0/1，**无任何用户可控路径 → 无穿越面**。

按规则先查是否已记载 → **命中**：归档 `30-2` 早已记「`/api/logs` 与 `/api/logs/clear` 无认证」。
**但该条的跳过理由已过时**：原文是「跳过（设计决策：**服务绑定 localhost，外网不可达**）」，
而今天 `docker-compose.yml:88` 的 `"${SERVER_PORT:-37777}:37777"` **无主机 IP 前缀**
（发布到所有网卡）+ `:60` 设 `SERVER_ADDRESS: 0.0.0.0`（P2-70），
且后端**唯一的 Servlet Filter 是 `MdcAutoFilter`**（关联 ID，非鉴权），全仓无 `SecurityFilterChain`。
→ **已加注**（归档不可改）：**不是新缺陷，是既有记录的前提失效**；
危害措辞可更准——`clear` 是**破坏性**的（`:165` 用 `Files.writeString(todayLog, "")` 截断），
故除了「可读」还有「可销毁」。
**未验证的部分不写**：**没有调用**破坏性的 `POST /api/logs/clear`，截断行为是**读码确认**。

**只读部分实测且全部正确**：`GET /api/logs?lines=3` → `totalLines=48297 / returnedLines=3 /
files=['claude-mem-2026-10-06.log']`；钳位 `0`→1、`-5`→1、`99999`→10000、
十六进制前缀 `0x10`→**16** —— 与 `docs/API.md:2896-2897` 的记载**完全一致**。
**故该控制器与对应文档本身零缺陷。**

### 文档方向（SDK README，doc round 217）—— P2-83：第 157 轮清扫的残留 11 处

顺 `docs/go-sdk-guide.md:293` 的 `PATCH /api/session/{id}/user` 发现路径变量名错误。
**活体权威**：`/v3/api-docs` 为 `PATCH /api/session/{sessionId}/user`，`params=['sessionId']`；
源码 `SessionController.java:294` 的 `@PatchMapping("/{sessionId}/user")`。

**这不是新错误类**：第 157 轮已把同一处替换在**十个文件、十四处**全部更正，
**这批是当时漏掉的**。按断言清扫得 **11 处 / 10 文件**：

`backend/README.md`、`cortex-mem-spring-integration/README.md` + `-zh-CN.md`、
`js-sdk/cortex-mem-js/README.md` + `-zh-CN.md`、`docs/DEPLOYMENT.md` + `-zh-CN.md`、
`docs/api-json-naming-convention.md`（**2 处**）、`docs/go-sdk-guide.md`、`docs/drafts/js-sdk-design.md`。

**⚠️ 按断言清扫 ≠ 按前缀清扫**：`/api/memory/observations/{id}` 的变量名**确实是 `id`**
（活体 `params=['id']`），全库 **94 处全部保留、一个未改**——第 157 轮就此事立过规矩。
**刻意排除**：归档（不可改）、`patrol-rotation.md`（历史记录）、三份工作文件（其中是历史叙述非断言）。

**改后核验三项**：非历史文件残留 **0**；`/api/memory/observations/{id}` 仍为 **94**（未误伤）；
`git diff --numstat` 恰为 **11 增 / 11 删**，无附带改动。
**探针教训**：第一次 grep 的输出被终端**截断**，差点少算一处（`docs/drafts/js-sdk-design.md`）；
改用脚本计数后才发现真实值是 **11 而非 10**。

### 变更检测

本轮**只改 `.md`**（11 处文档更正 + findings）。指纹仍 `2eadb49a…` / 885，**与基线一致**
→ **不跑完整验收、不推进基线**。本轮未启动任何额外进程，**37777 未受影响**。

### 下一轮

代码方向：**Java SDK**（轮换回到起点）；文档方向：**设计文档**（doc round 218）。

## 块 2 / 6：第 318 轮报告全文（第 331 轮逐字迁出）

## 第 318 轮 — 2026-10-06T23:15:00+08:00
代码方向：**Java SDK**（零缺陷，**一个自带假设被自己推翻、一个探针差点造成假发现**）；
文档方向：**设计文档**（doc round 218，**两处失实已修 + 一处归档登记漏补**）
**本轮第二次执行第 316 轮新立规则：记 finding 前先查该模块 README 双语**
**并第三次撞上同一件事——注入的上下文不是证据，磁盘才是**

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `2eadb49a…` / 885，与基线一致。

**工作区在本轮进行中变脏**：会话开始时 `git status --short` 为空，约六分钟后突然出现 **8 个已修改文件**
（`.claude/skills/upstream-sync/SKILL.md` 等，内容是「upstream 定位」文档改写，与本任务无关）。
按既定纪律**未 stash、未回退、未提交**，本轮**只暂存自己改的文件**，提交时逐个核对。
**这正是「本仓库有他人未提交 WIP」那条约束要防的情形，实际发生了。**

### 代码方向（Java SDK）：零缺陷，但过程里有两件事值得记下来

**接手时带着一个上一轮留下的假设**：`MemoryRetrievalService` 接口 Javadoc 写
`@return ... empty on failure`，而 `DefaultMemoryRetrievalService` **一个 catch 都没有**——
看上去是接口失实。**读下去发现 catch 在下一层**：`CortexMemClientImpl` 的
`retrieveExperiences`（`:191`）、`buildICLPrompt`（`:221`）、`getQualityDistribution`（`:289`）
各自 `catch (Exception)` 并返回 `List.of()` / `ICLPromptResult("", 0)` / 全零 `QualityDistribution`，
与 README `:526-531` 的「Returns on failure」表**逐项对应**。
`@return ... empty on failure` **传递性为真，接口 Javadoc 零缺陷**。**该假设作废。**

**这一层同时印证了 README 那段设计的自洽性**：类注释在 `:167-178` 明写
「这四个方法的降级是刻意的，其余方法不是——`listObservations` / `getObservationsByIds` / `getProjects`
要传播，因为空结果无法与「确实没匹配到」区分」，且 `:326-338` 单独为 `healthCheck` 重新论证
（它的唯一调用方是 actuator，而 `Health.down().withException` 会把栈trace 挂到每一次轮询上）。
`:523-535` 还把第 158 轮 `ClassCastException` 事故写进了注释。**注释密度高于多数商业库。**

**一个探针差点造成假发现（先怀疑仪器，再采信读数）**：
`CortexToolAspect.buildInputMap` 依赖 `Parameter#getName()` 取真实参数名，
而真实参数名要求 `-parameters` 编译标志。父 pom 确实有 `<parameters>true</parameters>`，
但**继承是推断**。首版探针用 `javap -p -c` 找 `MethodParameters`，得到三个类全部 **ABSENT**——
**若是就此采信，会写下「`-parameters` 未生效，`CortexToolAspect` 记录的工具入参键全是 `arg0`/`arg1`」
这条完全成立、且足以单方面开工修的假 finding。**
**错在仪器**：`javap -c` 不打印属性表，只有 `-v` 才打印。改用 `javap -v` 后：
`CortexMemoryTools` 的 `searchMemories` 实测参数名为 **`task` / `count`**，
另两个模块的 `MethodParameters` 出现次数 11 / 2 / 4，**三模块全部生效**。**未产生任何修改。**

**其余核实为真的项**：测试数按模块 surefire 报告 XML 汇总 = **143 + 46 + 7 = 196**，与记录一致；
`ExperienceRequest.toWireFormat` 发 `requiredConcepts` / `userId`、`ICLPromptRequest` 发 `maxChars`
（camelCase），`UserPromptRequest` 发 `session_id` / `prompt_text` / `cwd` / `prompt_number`（snake_case）
—— **两套命名混用是对的**，逐条对着后端 `MemoryController.java:117-118,151-152` 的 record 访问器
与 `ApiRequests.java:28` 的 `@JsonProperty("user_id")` 核实，**零不匹配**；
`updateSessionUserId` 的 `/api/session/{sessionId}/user` 与 `SessionController.java:294` 一致。

### 文档方向（设计文档，doc round 218）：两处失实 + 一处「遗漏≠失实」+ 一处归档漏登记

取 `docs/drafts/python-sdk-design.md`（**2026-03-27 的待审批 DRAFT，全库最陈旧的设计文档**）。
按规则先查 Python SDK README 双语 —— **未命中**（README 只在参数表 `:166` 写对默认值，
未就 `localhost` 风险自陈），故可立 finding。

**DOC-1（已修）** **`base_url` 被标成默认值，而真实默认是 IPv4 字面量。**
`:125` 原文 `base_url="http://localhost:37777",  # 默认`；
实现 `client.py:78` 是 `base_url: str = "http://127.0.0.1:37777"`。
**且这个字面量是刻意选的**：`client.py:53-58` 的类 docstring 就写着理由并指向 **P2-57**
（后端只绑 `127.0.0.1`，`localhost` 多数系统先解析 `::1`，主机名形式依赖客户端回退）。
**第 162 轮修的正是 `client.py` 里的同一处，漏掉了设计文档。**
另两处示例（`:53` Quick Start、`:90` context manager）同样在教主机名形式，一并对齐，
并在 §3.1 后加注说明实施后的默认值与理由。**§3.1 其余五个默认值逐项与实现相符，未改。**

**DOC-2（已修）** **`health_check` 的异常类型写窄了。** 原注 `raises APIError on failure`；
实现有三条失败路径，**只有第一条**（HTTP 4xx/5xx，`raise_for_status`）抛 `APIError`，
另两条——**200 但 `status != "ok"`**、**响应体读不出 JSON 对象**——抛的是**基类 `CortexError`**。
按该文档 §6 自己列出的层次，`except APIError:` **捕不到后两种**，而后两种恰恰是就绪探针要发现的状态。
实现 docstring 已写对（"A health check that cannot confirm the backend answered is not a health check"），
**SDK README 的方法表对该方法异常行为只字未提，故无第二处可对照**。已改注并加三行对照表。

**一处「遗漏 ≠ 失实」，刻意不补**：§3.2 标题写「26 个方法」，AST 枚举实现**确实是 26** 个公开方法；
但清单只列出 **25** 个 API 方法 + `__enter__`/`__exit__`，少列 `get_observation`。
**标题数字是对的，缺的是一项 → 遗漏而非失实，不制造修改。**

**DOC-3（已修）** **`history-18` 归档从未登记进 `docs/archive/README.md`。**
核对压缩历史时按上下文去找 `2026-10-06_health-check-history-19.md`（上下文称第 310 轮创建、装第 294–300 轮），
**磁盘上该文件不存在**；实际是 **`history-18`** 装着第 294–300 轮（其头 8 行自述「第 310 轮迁出」）。
**上下文摘要与磁盘不符，以磁盘为准。** 随后 `grep -c` 发现 `history-18` 在归档 README 里**零命中**——
十八份 health-check 归档里**独此一份无登记**，第 310 轮建了档却忘了加行。本轮补上。

**我自己的一个错**：首次落笔时把两处加注的日期写成 **2026-10-07**。
巡检用的是**逻辑时间轴**（真实 `date` 与 git 提交时间约 19:0x，条目时间已推进到 22:35），
两条加注都属 **2026-10-06**。**写入后自检 U+FFFD 时顺带扫日期，当场发现并改正。**

### 压缩（第 318 轮）

写报告前工作文件已 **1446 行**，本轮报告约 60 行将越过 `MAX_LINES=1500`，故先迁出。
**按轮号**匹配 `## 第 N 轮 — ` 提取**第 301–306 轮共 6 块 / 383 行**，工作文件保留第 307–317 轮。
**终验 5 组全过**：删除前快照双向比对（应迁出**丢失 0**、应保留**丢失 0**、**多余 0**）；
「被移走集合 == 归档集合」**双向等集**；归档切片**逐字相同且保持原序**；
轮次并集 **307–317 ∪ 301–306 == 全部 17 轮、无交叠**；验收基线块仍在工作文件内。
比对用**多重集（Counter）而非逐位 zip**（本脚本会插入归档头）。1447 → **1063 行**，归档 390 行。
写入后**另用一段独立脚本从磁盘重读复验**，不依赖压缩脚本自身输出。

**一处自曝**：首版收尾断言写成 `assert not (removed - Counter())`，
而 `src - dst` **本身就等于被移走的那批**，减空 Counter 必然非空 —— **是断言错、数据对**，
改正为双向等集后一次通过。

### 变更检测

本轮**未改任何代码**（Java SDK 侧只读；`mvn -q -o test` 196/196 全绿作旁证）。
指纹仍 `2eadb49a…` / 885，**与基线一致** → **不跑完整验收、不推进基线**。
本轮未启动任何额外进程，**37777 未受影响**（仅跑了测试，未起服务）。

### 下一轮

代码方向：**Go SDK**（轮换前进）；文档方向：**架构文档**（doc round 219）。

## 块 3 / 6：第 319 轮报告全文（第 331 轮逐字迁出）

## 第 319 轮 — 2026-10-06T23:52:00+08:00
代码方向：**Go SDK**（**P2-84 已修**——四家里三成一败的尾斜杠规范化）；文档方向：**架构文档**（doc round 219，**零缺陷**）
**本轮第三次执行第 316 轮规则、第二次撞上「仪器先错」——两次都在提交前拦下**

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `2eadb49a…` / 885 与基线一致，工作区干净。

### 代码方向（Go SDK）：P2-84 —— `base_url` 尾斜杠规范化，四家里三成一败

取覆盖最薄处入手：三个 adapter 与五个 example 均为**独立 module**，根模块的 `go test ./...` 一个都不跑。
逐个读完 adapter，**三处都把 P2-36 写进了注释**（eino 的 `WithRetrieverCount`、genkit 的同款、langchaingo 的 `WithMemoryMaxChars`），
并各自论证了「负值不被钳位」的后果——**无缺陷**。

**真缺陷在 `NewClient` 的 URL 规范化**，形态是第 300 轮「重试极性三对一」的同型：**四家里三家一致、第四家单独不同**：

| SDK | 位置 | 写法 | 去几个尾斜杠 |
|---|---|---|---|
| Python | `cortex_mem/client.py:86` | `base_url.rstrip("/")` | **全部** |
| JS/TS | `src/client-options.ts:68` | `.replace(/\/+$/, '')` | **全部** |
| **Go** | `client_impl.go`（原 `:122`） | `strings.TrimSuffix(...)` | **仅一个** |
| Java | `CortexMemClientImpl` | 交给 `RestClient.baseUrl()` | 不适用 |

`TrimSuffix` 的语义是「删末尾**一个** `/`」。故 `WithBaseURL("http://host:37777//")` 规范化后仍是 `http://host:37777/`，
拼出 `//api/version`。**这不是推断，是活体实测**：`GET http://127.0.0.1:37777//api/version` → **HTTP 404**，
响应体为 Spring 的 `{"status":404,"error":"Not Found","path":"//api/version"}` —— **后端不折叠空路径段**。
**即该配置下 Go SDK 的每个请求都是 404，而完全相同的配置值在 Python 与 JS 里正常工作。**
**失败形态是最坏的一种**：不报错、静默全量 404，且**只在一家里发生**。

**既有测试只覆盖单个尾斜杠**（`TestNewClient_TrailingSlashNormalization` 传 `server.URL + "/"`），
**双斜杠此前零覆盖**；**Go SDK README 中英双语 `grep -i "trailing|尾斜杠"` 也零命中**（按第 316 轮规则先查过，未自陈）。

**已修**：`TrimSuffix` → `TrimRight`。属**纯加宽 / 向后兼容修正**——当前能工作的任何输入行为都不变，
受影响的只有**本来就 100% 失败**的输入，故可单方面实施。**补测一条**覆盖双斜杠。
**双向注入验证**：保留修复全绿 → 回退为 `TrimSuffix` 后**恰好**新测试失败、
**既有单斜杠测试仍通过** → 恢复后全绿。**是数据与断言互相印证，不是「跑通就算数」。**

**⚠️ 计数 1/3**：按纪律，任何代码修改须**连续 3 轮**深入检查无新问题方可结案。

**探针自身错一次、先识别再采信**：想验证五个 example 能否编译，在**根模块**里 `go build ./examples/basic`，
五个全部报 `main module does not contain package …`，看起来像「示例全烂」。
**实为仪器错**：`find` 显示 `examples/*` 下**每个都有独立 `go.mod`**，从根模块构建本就不该成功。
改到各自目录后**五个全部 BUILD OK**——**未产生任何假 finding**。

**已在案、不重复记录**（第 316 轮规则第三次生效）：`go-sdk-unit-test.sh` 头注释称
「runs tests for ALL submodules」，而树里有 **9 个 `go.mod`**、脚本只跑 4 个。逐一核对后判定**不构成缺陷**：
该注释紧邻的前一句已把范围枚举为「root + eino/genkit/langchaingo」，
且 `docs/TESTING.md:47` 精确写出 `root + dto + eino + genkit + langchaingo` **五个包**——
脚本 4 次调用（root 的 `./...` 含 dto）**与之完全吻合**，第 315 轮的核实是对的。
真正没被任何编排跑到的 `examples/http-server`（**27 个测试**）**早在第 164/313 轮就在案**，故本轮不重复立项。

**其余核实为真**：测试数按 `go test -json` 逐模块实测 = root **302**（含 dto **67**）+ eino **8** + genkit **13**
+ langchaingo **12** + examples/http-server **27**，全绿；`go vet` 五模块零告警；
`MaxResponseBytes` 10 MiB 用 `io.LimitReader(+1)` 读一字节超限**显式报错而非静默截断**；
BaseURL 单尾斜杠、超时下限（100ms，注释记录了「曾误设为默认 30s」的实测与修法）均正确。

### 文档方向（架构文档，doc round 219）：零缺陷

取 `docs/ARCHITECTURE.md` 中前几轮未核的两节。**行号引用逐条精确命中**（这一类正是第 246 轮抓出
「行号随被引源码同批改动而失效」的地方，故必须逐条核）：

| 文档断言 | 实测 |
|---|---|
| `OffsetPageRequest.java:107-114`，`instanceof` 在 **109** | **109 精确命中**，`equals` 确比 **4 个**字段 |
| `SummaryGenerationService:83`（`@Async`） | 精确 |
| `MemoryRefineService:91`（`@Async`） | 精确 |
| `SummaryGenerationService:108` 用 `"\n"` 拼接 | 精确（该行即拼接语句） |
| `prompts/{init,observation,summary,continuation}.txt` | **4 个全在** |
| `getFactsJson()` 带 `@JsonProperty("facts")` 返回 `String` | 精确（`:281-282`） |

**API Layers 表逐层对拍活体 `/v3/api-docs`**（62 路径 / 67 操作）：Viewer's **「15 methods」精确吻合**
（2+1+1+1+1+2+2+2+1+1+1=15）；Context 的 **7 个子路径**与 `paths=7 ops=7` 一致；
Ingestion 4/4、Session 3/3、Import 5/5、Logs 2/2、Test 3/3、Extraction 3/3 全部吻合；
Cursor 的 `(register, check/unregister, context, projects)` 与实际 5 路径 6 操作一致；
**`/api/mode/*`（7 路径 8 操作）与 `/api/modes`（2 操作）是两个不同控制器**，表里分列两行**并非自相矛盾**。

**SSE 承重契约核实为真**：`SSEBroadcaster.broadcast(Object data, String eventName)` 在 `:55`，
`.data(data)` 在 `:65`，**全文件无 `.name(...)` 调用**，且其 javadoc `:48` 明写
「eventName 仅供文档与数据路由」。文档说的「帧里没有 `event:` 字段、路由键是 payload 内的 `type`」**属实**。

### 变更检测

**本轮改了代码**（`client_impl.go` 一行 + `client_test.go` 一条测试），指纹 `2eadb49a…` → **`4544d35e…`**。
**新鲜度闸门照办**：Go SDK 虽不在后端依赖图内（`backend/pom.xml` 零命中），**仍不据此推断豁免**——
本轮从当前源码重建 jar，另起 **37790**（pid 74384，本轮自起）跑验收。
回归 **45 / 0 / 1**、`EXTRACTION_ENABLED=true` Phase 3 **25 / 0 / 0**，**两项均与基线一致**。
验收通过后推进基线至 `4544d35e…`。**验证结束后已停止 37790，37777 未受影响。**

### 下一轮

代码方向：**Python SDK**（轮换前进）；文档方向：**运维/用户指南**（doc round 220）。

## 块 4 / 6：第 320 轮报告全文（第 331 轮逐字迁出）

## 第 320 轮 — 2026-10-07T02:15:00+08:00
代码方向：**Python SDK**（**P2-85 已修**——本循环第一次在源码注释里抓到失实陈述）；
文档方向：**运维/用户指南**（doc round 220，**零缺陷**——核实的是另一进程刚写的内容）
**本轮开头发现仓库状态被另一进程大幅改动，三次「先怀疑仪器」全部拦下**

### 健康预检：三项与上轮全不同

37777 **已停止**（无监听者）。HEAD 从我的 `7a78266` 前进到 `6e5890d feat: align viewer APIs with paired WebUI`，
**26 个文件、+855/-283**，含**新增迁移 `V19__viewer_session_indexes.sql`** 与
`ViewerSessionService.java`（181 行）。检查无近期编辑、HEAD 稳定 → 对方已收工。
另注意到 `docs/DEVELOPMENT.md` 双语被同批改动，**正落在本轮轮换范围内**，故一并核实。

### 代码方向（Python SDK）：P2-85 —— 注释称「四家与后端一致」，实测后端接受十六进制

453 测试全绿。取 Python demo 的 `_parse_int_param`，其注释自称
「A regex pins the grammar to 'optional sign, then digits' … **so all four demos match the backend**」。
**该等价断言在十六进制上不成立。活体实测**（对本轮自起的 37790，一个含 100 条观测的 project）：

| 查询参数 | 后端实际行为 |
|---|---|
| `limit=10` | **10** 条 |
| `limit=0x10` | **16** 条 ← 十六进制 |
| `limit=0x5` | **5** 条 |
| `limit=010` | **10** 条 ← 十进制，非八进制 |
| `limit=1_0` | **400**（注释所举之例，确为真） |

Spring 的 `NumberUtils` 把 `0x` 前缀当十六进制。**`0x10`→16 与 `10`→10 不可混淆，属决定性证据。**
而**四家 demo 全部拒绝** `0x10` —— Go demo 自己在 `main.go:65` 写明
「it still rejects "0x10" and "10abc"」。**故「四家一致」为真，「四家与后端一致」为假。**

**按断言清扫**：全库**只有这一处**写了该等价断言；Go 那句**本身准确**（只陈述自己拒绝什么），
故**未动**。**已修**：改为限定范围的准确表述，并补上实测的 `010`→10 与十六进制分歧。
**纯注释改动**，经 `git diff -U0` **逐行核验每一行都以 `#` 开头或为空行**，无可执行行变更；453/453 全绿。
**Severity 低**（`?limit=0x10` 现实中几乎不出现，且 demo 比后端更严格本身无害），
但这是**本循环第一次在源码注释里发现失实陈述**，与本项目长期主题正相关，故仍更正。

### P2-84 复查 2/3：零回归

取上一轮没碰的角度做受控实验（临时探针，验证后即删）。**8 个用例前后对照**：

| 输入 | TrimSuffix（上轮前） | TrimRight（上轮后） |
|---|---|---|
| 无尾斜杠 | `/api/health` | 同 |
| 单个 `/` | `/api/health` | 同 |
| **`//`** | **`//api/health`** ❌ | **`/api/health`** ✅ |
| **`///`** | **`///api/health`** ❌ | **`/api/health`** ✅ |
| `/prefix` | `/prefix/api/health` | 同 |
| `/prefix/` | `/prefix/api/health` | 同 |
| `/`（仅斜杠） | 报错 | 同 |
| `///`（仅斜杠） | 报错 | 同 |

**恰好 2 例变化，且都是从坏变好；路径前缀完整保留；退化无主机输入行为不变。零回归。**
顺带实测到旧版**连错误都不报**（`err=false`）——它照发双斜杠路径，
「静默」这个词得到第二重佐证。**计数 2/3。**

### 三次「先怀疑仪器」，全部拦下

1. **`CODE_RECORD_COUNT` 从 885 暴涨到 2218** —— 一次注释改动不可能增加 1333 条，
   第一反应是指纹算法出问题。**读脚本自身的注释后才看清**：它本就刻意收录 submodule 嵌套工作树
   （"so local WebUI edits cannot evade change detection"）。
   **实情是 `webui` 于 01:49:00 被 checkout（2152 个文件）**，此前状态是 `SUBMODULE_MISSING`（记 1 条）。
   **故这是真实的状态变化，不是仪器故障**——若不读那段注释，就会把一个真实变化误判成指纹损坏。
2. **`/api/projects` 的项目列表解析失败** —— 我按 `projects` / `data` 两个键名猜，实际嵌在
   `projectsBySource` 下。是**探针错**，换成有数据的 project 后正常。
3. **`psql` 找库名失败** —— `.env` 里是未展开的 `$DB_NAME`。**放弃这条支线**：
   Flyway 的 "Schema public is up to date. No migration necessary." 本身即权威证据，
   不必绕道数据库复验 V19。

### 文档方向（运维/用户指南，doc round 220）：零缺陷

核实另一进程刚写入的 `docs/DEVELOPMENT.md` 双语三处改动，**全部属实**：
`ViewerSessionController.java` **确实存在**且 `@RequestMapping("/api")`（与「WebUI 会话目录/删除」的描述相符）；
迁移范围写 **`V1–V8, V11–V19`**，实测目录恰为 `V1…V8 V11…V19`、**V9/V10 确认缺席**；
中英双语**同步**（`:376` / `:404`）；`37 shell scripts` 实测 `ls scripts/*.sh` = **37**，双语一致。

### 变更检测

指纹 `2eadb49a…` → **`a4525469…`**（记录数 885 → 2218，见上）。
**新鲜度闸门照办**：37777 已停，故从当前源码重建 jar、另起 **37790**（pid 78024，本轮自起）跑验收。
**为免基线与实际验证状态之间留缺口，改动后重跑了一次完整门控**：
回归 **45 / 0 / 1**、`EXTRACTION_ENABLED=true` Phase 3 **25 / 0 / 0**，**两项均与基线一致**。
V19 已由对方先前运行时应用（Flyway 报 "up to date"）。
基线推进至 `a4525469…`，并**在基线块内加注**说明记录数变化的成因与新旧基线不可直接比较。
**验证结束后已停止 37790；37777 仍为停止状态，本轮未触碰它。**

### 下一轮

代码方向：**JS/TS SDK**（轮换前进）；文档方向：**API 文档**（doc round 221）。

## 块 5 / 6：第 321 轮报告全文（第 331 轮逐字迁出）

## 第 321 轮 — 2026-10-07T02:38:00+08:00
代码方向：**JS/TS SDK**（**零缺陷**）；文档方向：**API 文档**（doc round 221，**P2-86 已修，4 处**）
**P2-84 在本轮结案（3/3）** —— 用「真实消费方」这个全新角度拿到决定性证据

### 健康预检

指纹 `a4525469…` / 2218 **与基线完全一致**，工作区干净，HEAD = `d31dc85`（上轮我自己的提交）。
**37777 仍处停止状态**（自另一进程结束后已逾 1.5 小时，HEAD 稳定、无近期编辑）→ **本轮起由我恢复**
（pid 4029，用上轮构建的 jar；`git log d31dc85..HEAD -- backend/` 为空，jar 确为当前）。
**先核对 jar 是否过期再启动**，而不是假定。

### 代码方向（JS/TS SDK）：零缺陷

259 测试全绿。取前几轮未细看的 `src/client-options.ts`（配置面，亦是 P2-84 涉及的那处）逐行核：
**所有默认值既有声明文档也有实际应用**，`timeout` / `maxRetries` / `retryBackoff` 三处下限
（100 / 1 / 100）均与文档一致；`.replace(/\/+$/, '')` 去除**全部**尾斜杠
——这是「该语义正确」的**第五个独立佐证**（Python、JS、本轮修好的 Go，加上 Java 交由 `RestClient`）。

**版本号对拍**：`SDK_VERSION = '1.0.0'`（`client-options.ts:62`）与 `package.json` 的 `version`
**一致**，且确在 `client.ts:614` 的 User-Agent 中使用。

**取一个此前无人查过的角度——超时控制器与重试循环的交互**：若 `AbortController` 建在循环外，
首次超时后所有重试会因 signal 已 aborted 而立即失败。实测代码**正确**：
`controller` 与 `timer` 建在 `doFetch` 内（`:635-636`），`clearTimeout` 在 `finally`（`:700`），
而每次重试都经 `requestNoContent` → `doFetch` 重新走一遍，**每次尝试都是全新的 controller 与 timer**。
`doFireAndForget`（`:756-793`）本身也正确：可重试判定、±25% 抖动线性退避、吞错语义。
**无残留问题。**

### P2-84 结案（3/3）：真实消费方层面的前后对照

前两轮验的是客户端内部。本轮换角度——**demo 才是真正会吃到这个配置的地方**，
而 `examples/http-server/main_test.go` 对 `CORTEX_BASE_URL` / `WithBaseURL` **零覆盖**。

以 `CORTEX_BASE_URL="http://127.0.0.1:37777//"` 起 demo，**同一环境变量、两个二进制**：

| 二进制 | 结果 |
|---|---|
| 上轮之前（`TrimSuffix`） | **HTTP 503** `{"error":"unhealthy: cortex-ce: API error 404: Not Found"}` |
| 上轮之后（`TrimRight`） | **HTTP 200** `{"service":"go-sdk-http-server","status":"ok",...}` |

**这里还多出一个此前没意识到的后果**：故障的表现是 **「后端不健康」**——
demo 把自己的配置笔误**归因给了后端**。排查的人会去查数据库、查服务进程，而真正的原因在启动命令的环境变量里。
**这比「静默 404」还要误导一层**，是本轮最有价值的新认知。

验证后已复原修复、复跑 Go 测试全绿、删净临时二进制、37791 已释放、**工作区回到干净**。

### 文档方向（API 文档，doc round 221）：P2-86 —— 又是路径变量名

另一进程在 `6e5890d` 里给 `API.md` 双语新增了 Viewer 会话目录与删除端点，
**正落在本轮轮换方向内**，故逐条取证。响应结构**逐字段吻合**
（`content_session_id` / `project` / `platform_source` / `custom_title` / `started_at_epoch` /
`item_count` + `hasMore`，实测与文档示例完全一致）。

**但两条 DELETE 路由的路径变量写错了**：

```
活体 /v3/api-docs： DELETE /api/summary/{id}     params=[('id','path')]
                    DELETE /api/observation/{id} params=[('id','path')]
文档写的：          DELETE /api/observation/{uuid}
                    DELETE /api/summary/{uuid}
```

**成因可解释但仍为失实**：`deleteObservation(@PathVariable UUID id)` 的**参数类型**是 `UUID`，
作者据类型写了 `{uuid}` —— 而**路由变量名是 `id`；类型与变量名是两回事**。
**这不是新错误类**：第 157 轮修过 14 处、第 317 轮修过 11 处同一类。

**按断言清扫**：全库（排除归档与 `node_modules` 第三方内容）**恰好 4 处 / 2 文件**。
**该文件自身即自相矛盾**——`API.md` 全文路径变量普查中 `{id}` 出现 **3** 次、`{uuid}` **2** 次并存。
**已修**，改后残留 0、`git diff --numstat` 恰为每文件 **2 增 / 2 删**，
逐行核验**只有那 4 行路径行变动**。

**该节其余断言逐条核到源码为真**：`limit` 以 `Math.min(Math.max(1,limit),MAX_PAGE_SIZE)` 钳制
（对应文档「silently clamped to 1–100」）、`offset` 以 `Math.max(0,offset)` 下限、
409 的四个状态恰为 `active`/`queued`/`processing`/`summarizing`、
SSE 载荷为 `session_deleted` 与 `item_deleted`、`afterCommit` 钩子在 `ViewerSessionService:158`。

**⚠️ 刻意未实测的部分**：**DELETE 是破坏性端点，本轮一律未调用**；
404 / 409 的**运行时**行为**仅代码可证、未实测**，如实记下。

### 变更检测

本轮**只改 `.md`**（4 处路径变量更正 + findings）。指纹仍 `a4525469…` / 2218，**与基线一致**
→ **不跑完整验收、不推进基线**。
本轮启动的 37777（pid 4029）与临时 demo 均已记录；**37777 保留运行**供下轮取证。

### 下一轮

代码方向：**Demo**（轮换前进）；文档方向：**设计文档**（doc round 222）。

## 块 6 / 6：第 322 轮报告全文（第 331 轮逐字迁出）

## 第 322 轮 — 2026-10-07T03:00:00+08:00
代码方向：**Demo**（**零缺陷**）；文档方向：**设计文档**（doc round 222，**零缺陷**）
**本轮两处复核都用活体实测坐实，且两次都靠对照排除「另有他因」**

### 健康预检

37777 在监听（**pid 4029，上轮由我启动并保留**），health 200。
指纹 `a4525469…` / 2218 **与基线一致**，工作区干净，HEAD = `aa6039b`。

### 代码方向（Demo）：零缺陷

40 测试全绿（surefire 汇总：5 文件 / 40 用例 / 0 失败 0 错误 0 跳过），与记录一致。

**P2-85 复查 2/3 —— 用活体实测把上轮的更正坐实。**
上轮那条更正说的是「十六进制是后端接受、四家 demo 都更严格」。**Java demo 那一侧此前只读过源码，没验过。**
起 demo（37778）并与后端（37777）对同一组输入：

| 输入 | demo | backend | |
|---|---|---|---|
| `limit=3` | 200 | 200 | 一致 |
| **`limit=0x3`** | **400** | **200** | **唯一分歧** |
| `limit=010` | 200 | 200 | 一致（十进制） |
| `limit=1_0` | 400 | 400 | 一致 |
| `limit=10abc` | 400 | 400 | 一致 |

**五项输入里恰好只有十六进制一处分歧**——与上轮更正后的措辞逐字对应。
`DemoParams.boundedInt`（`:85-113`）的源码也确实如此：手工拆「可选符号 + 全数字」，
`:96-97` 的注释明写 **"no hex, no exponent, no trailing garbage"**。
**顺带证明第 320 轮「Go demo 那句未动」的判断是对的**——它只声称自己拒绝什么，确实如此。

**取一个新角度看文件读取工具的路径边界**（这是该 demo 里唯一带安全语义的面，
且已有 `FileReadToolRootEdgeCaseTest` 说明历史上关注过）：
`resolveWithinRoot` 是**两道互补检查**——`normalize()` 词法去 `../`，
`startsWith(root)` 按路径分量比较（故 `/root-evil` 不会误判为在 `/root` 内），
文件存在时再用 `toRealPath()` 解符号链接并二次比对。注释明写两道检查**互不替代**及原因。
**活体实测穿越全部被挡**：

| 尝试 | 结果 |
|---|---|
| `/etc/hosts` | `Error: path is outside the allowed root …` |
| `../../../../../../etc/hosts` | 同上 |
| `/etc/passwd` | 同上 |
| `demo.log`（根内不存在） | `Error: …/demo.log`（**是「不存在」而非「越界」**） |

最后一行值得单列：**未把「根内不存在的文件」误报成「越界」**，两类失败被区分开了。

**探针错一次**：第一版按惯例猜了 `/demo/tools/read`，四发全 404。
查控制器才知真实路由是 `/demo/tool`——**是我猜的路径不存在，不是端点坏了**。

### 文档方向（设计文档，doc round 222）：零缺陷

取 `docs/drafts/spring-ai-integration-plan.md`（1512 行，轮换记录里最少被审的设计文档）。

**§7.1 配置属性：11 项逐条对上实现。** `base-url`/`project-path`/`connect-timeout`/`read-timeout`/
`retry.max-attempts`/`default-experience-count`/`memory-tools-enabled`/`context-bridge-enabled`/
`capture-enabled`/`capture-user-prompt-enabled`/`retrieval-enabled` 的**名称与默认值全部吻合**
`CortexMemProperties`。文档标 `base-url` 为「必需」——`CortexMemAutoConfiguration:39` 的
`@ConditionalOnProperty(prefix="cortex.mem", name="base-url")` 证实**不配就不装配任何 Bean**，说法准确。

**§7.2 环境变量**：文档给的是 `CORTEX_MEM_BASE_URL` / `CORTEX_MEM_PROJECT_PATH`，
用环境变量名去搜代码**零引用**——**这正是第 183、281 两轮各中一次的 relaxed binding 陷阱**。
按那两轮留下的判据（「凡文档写了环境变量、代码搜不到，一律先换成小写点号属性名」），
换成 `cortex.mem.base-url` 后确认与 `@ConfigurationProperties(prefix = "cortex.mem")`（`:45`）一致。**不是幻影。**

**§2.1 核心 API 端点：10 条全部活体命中**（取 `/v3/api-docs` 作非破坏性权威，
4 条 ingest + 3 条 memory POST + quality-distribution GET + `/api/health` GET，全部 OK）。

**CORS 那条断言实测坐实，且做了对照**：文档称「后端默认关闭 CORS」。
核到 `WebConfig.java:18` 的 `@Value("${claudemem.cors.allowed-origins:}")` **默认空**，
且**全仓 yml/properties 中根本没有配置该项**（grep 无命中）。实测三连：

| 请求 | 结果 |
|---|---|
| 不带 `Origin` | **200** |
| `Origin: https://evil.example` | **403**，无 `Access-Control-Allow-Origin` |
| `Origin: http://127.0.0.1:37777`（自身） | **200** |

**第一行是对照组**——没有它，403 可能来自任何别的原因（鉴权、限流、参数）。
有了它才能断言 403 **确由 Origin 头触发**，且默认姿态是**关闭-安全**的：外来源被拒、同源放行。

### 变更检测

本轮**未改任何文件**（全为只读检查与活体探测；起过的 37778 demo 已停止、端口已释放）。
指纹仍 `a4525469…` / 2218，**与基线一致** → **不跑完整验收、不推进基线**。
37777（pid 4029）保留运行。

### 下一轮

代码方向：**Backend**（轮换前进）；文档方向：**架构文档**（doc round 223）。

