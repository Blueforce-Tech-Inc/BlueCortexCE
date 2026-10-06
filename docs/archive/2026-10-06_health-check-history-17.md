# Health Check History 17 — 第 289–293 轮巡检报告（第 306 轮迁出）

> **归档规则**：巡检报告按轮次保留；达增长阈值时把最早的若干轮逐字迁入此处。
> **归档文件创建后不得修改。**

> 本批为 `docs/drafts/health-check-task.md` 越过 `MAX_LINES=1500`（**1554 行 / 107217 字节**）而迁出 **5 轮**（第 289–293 轮），工作文件保留第 294–306 轮。
> 按**轮号**匹配 `## 第 N 轮 — ` 提取，并逐块断言**块内无嵌套轮次标题**（18/18 通过）。
> 验证用**多重集（Counter）**比对而非逐位 zip——插入行会让逐位比对把每一行都报成差异。

## 第 289 轮 — 2026-10-06T05:49:00+08:00

- **轮次**: 289 | **代码方向**: Backend | **文档方向**: SDK README（doc round 190）
- **起点**: `HEAD` = `1c4738b`（第 288 轮），工作区干净；仅 37777 在监听

### 健康预检

活体 `/api/health` → ok；指纹 `7c7dc3fa…`，与基线一致。

### 代码方向（Backend）—— JSONB 列的无损往返，实测通过

**角度**：四个 JSONB 列（`facts`/`concepts`/`files_read`/`files_modified`）+ `extracted_data`
经 JPA 实体映射后能否无损往返。此前只从 **SDK 解析侧**验过，**未从后端实体侧验过落库形态**。

- **写入走 PATCH**（不依赖 LLM），载荷刻意带转义风险：
  `facts = ["含中文与\"引号\"", "back\\slash", "emoji 🚀"]`、
  `extractedData = {"nested":{"deep":[1,2,{"k":"v"}]}, "nullField":null, "bool":true, "num":3.5}`。
- **活体直查 PostgreSQL 原始存储，逐字比对**：
  `facts=["含中文与\"引号\"", "back\\slash", "emoji 🚀"]` —— **完全一致**；
  `extracted_data={"num": 3.5, "bool": true, "nested": {"deep": [1, 2, {"k": "v"}]}, "nullField": null}` —— **完全一致**。
  **中文、转义引号、反斜杠、emoji、嵌套对象、显式 null、布尔、浮点全部无损**。**零缺陷。**
- **`files_read` 未被写入 —— 是探针错，不是缺陷**：我发的是 camelCase 的 `filesRead`，
  而 **PATCH 端点按设计不支持这两列**：Java SDK 的 `ObservationUpdate.Builder` 只有
  title/subtitle/content/narrative/facts/concepts/source/extractedData **八项，没有 filesRead/filesModified**；
  后端 `@Operation` 描述**也明确枚举了支持的七个字段**，不含这两列。故忽略未知键是正确行为。
- **API 层的形态已核实且文档正确**：`GET /api/observations` 返回的
  `facts` 是 **JSON 编码字符串**（`"[\"含中文…`）而非真数组——这是四家 SDK 都已知并各有解析器的既有形态；
  `docs/API.md` **两种形态都写对了**：请求体示例（ingest）用真数组、响应示例用 JSON 编码字符串，与线上逐字一致。

### 文档方向（SDK README，doc round 190）—— 零缺陷

Go README 的 `## Error Handling` 教的是 `cortexmem.IsNotFound(err)` / `IsBadRequest(err)` 的 if-else 示范，
实际代码有 **15 个** `Is*` 谓词，README 提及 2 个。**逐条比对：README 提到的两个都真实存在，零幻影**；
未提及的 13 个属**示意性示例而非穷举清单**（README 未声称穷举），
与第 183 轮对 Python/JS 错误类覆盖的判定**同构**，故**非缺陷**。

### 本轮探针的四次自我修正（均写入前拦下）

①`mem_observations` 被我当成有 `tool_name` 列（那在 `mem_pending_messages`）→ SQL 报错；
②首次 POST 返回 `accepted` 但查不到队列行，**不能据此断定数据丢失**——重跑并高频轮询才定位到真实情况；
③把 `filesRead` 当成可 PATCH 的字段（端点按设计不支持）；
④回读时先猜响应键是 `observations`/`data`/`content`，实际是 **`items`**，连续三次猜错才改对。

### 变更检测

**本轮零文件改动**，`git status` 干净，指纹 `7c7dc3fa…` **未变**
→ 按门控**不跑完整验收、不推进基线**。**零改动轮。**

### 环境限制（如实记录）

本环境 **LLM chat 端点密钥失效**：走 `POST /api/ingest/tool-use` 的观测在队列中转为
`status=failed`、`retry_count=0`（本轮探针即如此），故 JSONB 往返改用不依赖 LLM 的 PATCH 路径完成。
**这是环境限制，不是被记录的缺陷。**

### 进程清理

本轮未启动任何进程。探针写入了 1 条观测（`b3379a6a-…`）并修改了其 JSONB 列；
队列中留 1 条 `failed` 探针消息与 1 条 `accepted` 的空记录，均为测试数据。

## 第 290 轮 — 2026-10-06T06:12:00+08:00

- **轮次**: 290 | **代码方向**: Java SDK | **文档方向**: 设计文档（doc round 191）
- **起点**: `HEAD` = `c68a3f5`（第 289 轮），工作区干净；仅 37777 在监听

### 健康预检

活体 `/api/health` → ok；指纹 `7c7dc3fa…`，与基线一致。

### 代码方向（Java SDK）—— 一个「疑似缺陷」被证明是探针假象

**起因**：怀疑 `CortexMemProperties` 配了 `connectTimeout` 却不生效——这正是第 277 轮 P2-67
（`AsyncConfig` 文档宣称有超时能力而全后端没有）的同类温床。
**第一步就证伪了怀疑本身**：grep 只命中 getter/setter，是因为实际用的是**小写局部变量** `connectTimeout`
（`CortexMemClientImpl` 75–77 行 `HttpClient.newBuilder().connectTimeout(connectTimeout)`、
78–79 行 `setReadTimeout`）——**探针错，不是缺陷**。

但「接线了」不等于「生效」，故做了受控实验，**结果一度指向缺陷**：
指向不可路由地址时，300ms / 8000ms / 默认 10s 三种配置的失败耗时**几乎相同（均约 5 秒）**，
单次尝试（关闭重试）后依旧如此——**表面看就是「连接超时配置无效」**。

**真正的诊断来自看异常本身，而不是看计时**：异常是 `HttpServerErrorException$BadGateway: 502`，
**不是连接超时**。环境存在**系统级透明代理**，出站连接被代理接管，代理连不上上游后约 5 秒返回 502——
**到代理的连接是成功的，connect 超时根本没有机会触发**。清掉 `HTTP_PROXY` 等环境变量后仍是 502，
证实它不是环境变量来的。**故该实验在本机无法成立。**

**改用不依赖网络的决定性仪器**：`java.net.http.HttpClient` 自带 `connectTimeout()` 访问器，
直接反射读回 SDK 构造出的那个实例：

| 配置 | 读回的实际值 |
|---|---|
| `connectTimeout=300ms` | `Optional[PT0.3S]` |
| `connectTimeout=25000ms` | `Optional[PT25S]` |

**配置确实生效且精确对应。零缺陷。** 顺带发现 `healthCheck()` 对两个不可达地址都**不抛异常**——
查证后确认是 README 记载的**优雅降级**设计（供 Spring AI 的 `@Tool` 与健康指示器调用，
后端不可用时不应打断 agent 轮次或拖垮应用），**不是遗漏**。

### 文档方向（设计文档，doc round 191）—— 零缺陷

`18.md` 的文件处置表声明 `extracted_{template}` 现有 **18,373** 行（`extracted_user_preference`），
活体库 `WHERE type LIKE 'extracted_%'` 实测**恰好 18,373**，**逐位吻合**（第 167 轮的核验至今仍成立）。

### 本轮探针的五次自我修正（全部写入前拦下）

①用属性名 `ConnectTimeout` grep 而实际代码用小写局部变量；②**只看计时就差点判定「超时配置无效」**——
真正拦住它的是**打印异常类型**这一步；③用 `healthCheck()` 计时，但它降级不抛异常，测的不是连接；
④`sed -i ''` 表达式写法在本机 bash 失败并**损坏了源文件**，改用 write 重写；
⑤反射找 `requestFactory` 字段失败，实际字段名是 `clientRequestFactory`，且需要遍历父类。

### 变更检测

**本轮零文件改动**，`git status` 干净，指纹 `7c7dc3fa…` **未变**
→ 按门控**不跑完整验收、不推进基线**。**零改动轮。**

### 进程清理

本轮未启动任何服务进程；Java 探针编译产物全在 `/tmp/jprobe290`。

## 第 291 轮 — 2026-10-06T06:38:00+08:00

- **轮次**: 291 | **代码方向**: Go SDK | **文档方向**: 架构文档（doc round 192）
- **起点**: `HEAD` = `346ce03`（第 290 轮）；仅 37777 在监听（java pid 42092），37778–37781、37790 全空闲

### 健康预检

活体 `/api/health` → ok；指纹 `7c7dc3fa…`（`CODE_RECORD_COUNT=883`），与基线 `accepted_commit: c8683ac` 一致；OpenAPI 67 操作 / 62 路径；`mem_observations` 38775 行。

### 代码方向（Go SDK）—— 两个角度，零缺陷

Go 客户端在本轮之前只被审过「并发」一个角度（第 285 轮：结构体两字段、全包无锁、无共享可变状态）。本轮换两个从未查过的角度。

**角度一：响应体管理。** `grep` 全包的请求构造与执行点，只有 `client_impl.go` 两处（219 行 `http.NewRequestWithContext`、234 行 `Do`）——**整个包只有一条请求路径**。因此响应体管理只需审一处：`defer resp.Body.Close()` 在 **238 行**，位置紧随 `if err != nil` 检查之后，即**失败路径与成功路径都被覆盖**，不存在「返回前漏关」的分支。**三个子适配器（eino / genkit / langchaingo）没有独立的请求路径**——它们不自己发 HTTP，全部经由核心客户端，故也无从漏关。

**角度二：溢出检测。** 这是 Go 里最容易出静默故障的地方（无界 `io.ReadAll` 直接吃掉内存）。实测写法是 `io.LimitReader(resp.Body, MaxResponseBytes+1)`（**243 行**）——**读上限 +1 字节**，随后 247–250 行显式判 `len(respBody) > MaxResponseBytes` 并报错。上限常量 `MaxResponseBytes = 10 << 20`（**173–175 行**）。**关键在那个 +1**：只用 `LimitReader(n)` 的话，超长响应会被**静默截断成 n 字节**，随后表现为解析器一句令人困惑的 `unexpected end of JSON input`——**故障现场与根因完全无关**。代码里两行注释正好写明了这一点，**说明这是有意设计而非侥幸**。

**判定：零缺陷，本轮未改任何 Go 代码。** 零缺陷轮不制造修改。

### 文档方向（架构文档，doc round 192）—— 一处已修（P2-70，双语）

**DOC-1（P2-70）**：Network Security 表把「胖服务器」与「PostgreSQL」两行都写成「仅本地 / Local only」。

**这个判断本身不假，但对另一条部署路径失真**：①`application.yml:3` 是 `address: ${SERVER_ADDRESS:127.0.0.1}`，默认绑回环，**活体 37777 进程实测绑 `127.0.0.1:37777`**——原生路径准确；②`docker-compose.yml:35` 的 `"${POSTGRES_PORT:-5433}:5432"` 与 `:88` 的 `"${SERVER_PORT:-37777}:37777"` **两条映射都没有主机 IP 前缀**，而**不带主机地址的端口映射默认发布到所有网卡**；③`grep "127.0.0.1:"` 在 compose 里**零命中**，排除「别处已收紧」的可能；④`:60` 另设 `SERVER_ADDRESS: 0.0.0.0`；⑤ARCHITECTURE 自己的 Authentication 段写着「Currently no authentication / 当前无认证」——**默认关闭鉴权**。

五项叠加的结论是具体的：**推荐的 Docker 部署下，同网段任何主机可直连后端与数据库**。文档侧已双语更正表格并加说明块（含收紧写法）。**代码侧记录不实施**（P2-70）：给两条映射各加 `127.0.0.1:` 前缀会**破坏「从另一台主机访问后端」这一现成用法**，属对外行为变更。

**本轮其余部分逐条核实为真**（修了一处不等于其余免检）：compose `:35` 端口映射与文档表述逐字吻合；`application.yml:79` 的 `jdbc:postgresql://127.0.0.1/claude_mem_dev` 逐字吻合；活体 PostgreSQL（OrbStack pid 14871）监听 `*:5432`，与「原生在 5432」一致；`proxy/tag-stripping.js` 四个标签**全部存在**（`<private>` 11 / `claude-mem-context` 3 / `system_instruction` 4 / `system-instruction` 3），`isEntirelyPrivate()` 存在且带完整 docstring。

### findings 第四十一次压缩

写完 P2-70 后 findings 达 **969 行 / 103138 字节**，**字节项越线 738 字节**。注意这正是第 278 轮已经吃过一次的教训：**行数与字节是两个独立阈值**，969 行远未到 1000 行，按行数判会误判为合规。

- **七块逐字迁出**（按工作文件位置升序）：P2-21 活体实测与判定逻辑核实、P2-27 活体行级统计与注释层已修明细、P2-59 `DemoErrors` Javadoc 已修明细、P2-61 活体三端点响应与两处文档同源失实、P2-64 第 276 轮受控实验与 21 个 DTO 全量复查。
- **另有一类不属迁出的压缩**：P2-24 / P2-33 / P2-35 / P2-36 / P2-37 五条**逐字相同**的「复核记录」行改写为紧凑形式。改写时**发现并修正了其中一句已被作废的断言**——原文称「Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件」，而 **Scope / Evidence 已于第 254 轮迁出**，该说法自那时起即为假。
- **两条硬约束照例执行**：12 个区间**倒序处理**（每次替换只影响其上方，区间不会前移）；边界断言同时覆盖 `- **` / `### ` / `## ` 三种行首，形式为「区间内除首行外不得出现结构行 + 区间末行之后必须紧邻结构行 + 首行不得是标题」。
- **边界断言第一次写错并当场修正**：初版断言「区间末行之后不得是结构行」，被三处迁移区间直接顶回——那三处本就应该结束在下一条 `- **Status**` 之前。**探针自身出错先于结论修正**，未据此改动任何文件。
- **终验 7 组全过**：实际文件 == 快照按本轮区间重算的结果（区间外**零改动**）；条目数 60 → 60 且编号顺序完全一致；七块迁出内容**逐字在归档中**；指针行数 **7 == 归档块数 7** 且覆盖块 1..7 各一次；三个归档链接目标均可解析；五处紧凑复核记录到位且旧失实断言清零；无残留的同批行号引用。
- **顺带修掉两处失效行号引用**（均在迁出内容里，工作文件中已改用章节名/段名定位）：`docs/ARCHITECTURE.md:880` 因该文件**同批**被 P2-70 编辑，按「行号引用不得与被引用文件同批提交」必须放弃行号；`P2-28 第 401 行` 的行号**当时即已失效**——删除前快照里 P2-28 占 353–362 行，那句断言实际在其 Problem 段。
- **修正 Current Status 表**：本轮新增 P2-70 后该表 P2 Open 仍写 **2**，已改为 **3**（P2-8 / P2-10 / P2-70）。计数是**增量推得**的（前值 2 + 本轮新增 1 条 open），不是凭记忆。
- 终值 **954 行 / 101842 字节**（净 -1296），`doc-growth-check.sh` 退出 0。

### 变更检测

本轮改动**全部是 `.md`**（`docs/ARCHITECTURE.md`、`docs/ARCHITECTURE-zh-CN.md`、`docs/archive/README.md`、`docs/drafts/backend-review-findings.md`，新增两个归档）。指纹重算仍为 `7c7dc3fa…` / 883 条，**与基线一致** → 按既定规则**不跑完整验收、不推进基线**。

### 本轮留下但未处理的一条观察

`docs/drafts/backend-review-findings.md` 里有 **9 条早期条目完全没有 `- **Status**` 段**（P1-1、P2-8、P2-25、P2-26、P2-28、P2-29、P2-32、P2-34、P2-59），与本文件 Processing Rules 里「⏸ 条目必须保留 Problem 与 Status」相抵触。同时 Current Status 表的 **Open 计数口径在文件里没有成文定义**——这正是它这次差点漏同步的原因。**本轮不擅自重构这 9 条**：改写它们不产生任何可验证收益，且「不因零缺陷而制造修改」同理适用于结构整理。**记入报告待用户裁决口径**。

### 下一轮

代码方向：**Python SDK**；文档方向：**SDK README**（doc round 193）。

## 第 292 轮 — 2026-10-06T07:26:00+08:00

- **轮次**: 292 | **代码方向**: Python SDK | **文档方向**: SDK README（doc round 193）
- **起点**: `HEAD` = `e654aa6`（第 291 轮）；仅 37777 在监听（java pid 42092）

### 健康预检

活体 `/api/health` → ok；指纹 `7c7dc3fa…`（883 条），与基线一致。

### 代码方向（Python SDK）—— 一处新发现（P2-71），且它只有跑起来才看得见

**角度一：重试边界。** 构造器收 `max_retries` / `retry_backoff`，读码看它们**只被 `_fire_and_forget` 使用**（193–221 行），普通读方法一次都不重试。这是**可疑形状**——正是第 277 轮 P2-67（`AsyncConfig` 文档宣称有超时而全后端没有）的同类温床。但**两处文档都是诚实的**：构造器 docstring 明写「Attempts for **fire-and-forget captures**」，README 也明写「Retries apply to the fire-and-forget captures only」。**受控实验印证**（一次性本地 server 精确计数到达请求）：`max_retries=4` 时，503 下 `list_observations` **只发 1 次**、`record_session_end` **发满 4 次**，400 下后者**只发 1 次**即放弃。**文档与行为完全一致，不是缺陷。**

**角度二：错误响应体解析。** `_extract_error_message` 四种形态逐一实测：空体、HTML 错误页、非法 UTF-8、JSON `error` 键、JSON 数组、纯 JSON 数字——**全部**落成 SDK 自己的异常类型，无一逃逸。

**角度三（新发现所在）：DTO 反序列化。** `dto.py` 通篇 `data.get(...)`（41 处）配 `_to_str` / `_to_str_list` / `_to_dict` 三个防御性转换函数，**先假设它足够强**，然后用受控实验打一遍：16 个 `from_wire` × 9 种恶意载荷 = **144 次调用**。

- **字段级类型错确实被防住了**：`SearchResult` 逐字段污染 11 个字段（`id=1`、`facts='not-a-list'`、`concepts=7`、`quality_score='high'`…），**11/11 全部解析成功**。
- **但顶层不是 dict 时逃逸 59 次**，且**全部**是 `AttributeError: 'X' object has no attribute 'get'`。端到端确认可达：`_request_json` **不校验 `resp.json()` 的结果类型**，直接交给 `from_wire`；而 **12 个读方法一律带 `or {}`**（10 处在 `from_wire` 实参、2 处在 `_request_json(...)` 那一侧）。于是 2xx + `[]`/`null`/`0`/`""` → **静默变成全默认值 DTO**；2xx + `"oops"`/`42` → **裸 `AttributeError`**，**不在 `CortexError` 层次内**，调用方的 `except CortexError:` 接不住。**立为 P2-71，⏸ 记录不修**（见下）。

**四家实测对照**（同一台一次性 server，各跑 5 种 2xx 载荷）：

| 2xx 响应体 | Go | Python | JS |
|---|---|---|---|
| HTML 错误页 | 抛 `failed to parse` | 抛 `CortexError` | 抛 `Error` |
| `[]` | 抛 `cannot unmarshal array` | **静默空结果** | **静默空结果** |
| `null` | **无错、零值** | **静默空结果** | **抛裸 `TypeError`** |
| `"oops"` / `42` | 抛 `cannot unmarshal …` | **抛裸 `AttributeError`** | **静默空结果** |

**Go 侧 `json.Unmarshal([]byte("null"), &struct)` 是合法 no-op 这条，是单独写程序实测的**（零值、无错），不是从记忆推的。**Python 是三家里唯一对标量抛非 SDK 异常的**；而 `null` 是 Go 与 Python **共同的静默空洞**。

**为什么不修**：修法是让 `_request_json` 区分「204/零长度」与「存在但非对象」，前者仍返 `None`、后者抛 `CortexError`——但这会把今天**静默返回空结果**的若干输入改成**抛错**，属错误路径上的行为变更；且 `or {}` 本身**撑住「后端真的什么都没返回」这一合法场景**，不能简单删掉。

**不按失实陈述改写文档**：`_request_json` 的 docstring 与 Python README 的「Malformed Response Bodies」段都只声称「**不可解析**的体抛错」——**字面为真**（HTML 确实抛，三家实测均抛），只是**未覆盖**「可解析但不是对象」这一类。**遗漏 ≠ 失实**，不据此改写。

**探针自身出错三处，全部先识别再采信**（否则会记成三个不存在的缺陷）：①参数名写成 `limit=`，实际签名是 `count=`，四种载荷一律报「unexpected keyword argument」；②方法名写成 `list_projects()`，实际叫 `get_projects()`；③断言要求 200+HTML 体的异常消息含 "JSON" 字样，而实际消息是 `failed to parse …: Expecting value…`——**行为正确，是我断言写错**。

### 文档方向（SDK README，doc round 193）—— 零缺陷

**Python README 的重试段逐条对源码与实测核过，无一失实**：①`max_retries` 是「总尝试次数」——README 明写「3 = 3 requests」；②「Retries apply to the fire-and-forget captures only — `record_observation` / `record_session_end` / `record_user_prompt`」——本轮实验**实测印证**；③「Go and JS retry that same set of three and nothing else」——Go 的 `doFireAndForget` 恰好 **3 个调用点**、JS 恰好 **3 个**，方法名逐条相同；④「the Java SDK retries **10** of its 25 methods」——Java 的 `executeWithRetry*` **恰好 10 个调用点**，且它点名的「两个抽取读、三个变更、`trigger_refinement` / `trigger_extraction`」= 2+3+2 与实际调用点**逐条吻合**；⑤「all four default to 3」——四家默认值实测均为 3；⑥单位差异「Go and Java take a `time.Duration` / Spring `Duration`, JS takes **milliseconds**」——Go `time.Duration`、Java `java.time.Duration`、JS 注释明写 "in milliseconds"、Python `float` 秒，**四家全对**，「`500ms` 是 `0.5` 不是 `500`」亦对。

**Malformed Response Bodies 段的跨家断言「Go and JS raise on the same input」实测为真**——三家在 HTML 这一行确实都抛（正是该段自己举的那个例子）。

**扩到 Go README 做同维度交叉核对，8 项参数表与 `DefaultClientConfig()` 逐项吻合**（BaseURL / APIKey / Timeout 30s / ConnectTimeout 10s / MaxRetries 3 / RetryBackoff 500ms / Logger nop / HTTPClient auto-built），且「matches Java SDK `readTimeout`/`connectTimeout`」与 Java 的 `Duration.ofSeconds(30)` / `ofSeconds(10)` 一致。

### findings 第四十二次压缩 + 两条新归档

- **新立 P2-71** 正文约 1.9 KB（一版比预估多一倍：中文三字节，估算严重偏低），随即**两次触发字节闸门**。
- **第四十二次压缩**两块逐字迁出（`P2-66` 四例实测与四家对拍、`P2-51` 第 254 轮文档侧更正记录）。**边界断言两次拦下原本会切在块中间的行**：P2-66 的「危害」一句与 P2-51 的「另一种修法」一句都在区间之后仍属同一块，故**两处区间都扩到块边界**才落刀。
- **另有一类零信息损失的原地压缩**：**33 条逐字相同**的「Scope / Evidence」行（第 254 轮整体迁入同一归档所致，33 × 168 B = 5544 B）改写为短显示名 `…-8.md`，**-1045 字节**；另有 **5 条带第 275 / 278 轮追加内容的行一行未碰**。手法与第 291 轮压缩 5 条「复核记录」相同：**同一事实不必逐字重复 N 遍**。
- **验证**：以删除前快照做**多重集双向比对**——删除项只有 `LONG × 33`、新增项只有 `SHORT × 33 + 规则说明 × 1`，**其余每一行都逐字来自快照**；条目 61 条编号与顺序不变；链接目标存在。
- **又一次探针自身写错**：第一版验证脚本按**行位置** zip 比对，而插入 1 行后其后所有行整体错位，于是报出「124 处差异」——**换成多重集比对后真相是 2 类改动**。逐位比对在这类「同长替换 + 单行插入」的场景下**必然误报**。
- 终值 **955 行 / 101761 字节**，`doc-growth-check.sh` 退出 0。Current Status 表 P2 Open 同步 **3 → 4**。

### 变更检测

本轮改动**全部是 `.md`**。指纹仍为 `7c7dc3fa…` / 883 条 → **不跑完整验收、不推进基线**。

### 给用户的信号（第三次提出）

**findings 已连续三轮每加一条发现就触发一次压缩**（第 291 轮 P2-70、第 292 轮 P2-71）。当前 955 行 / 101761 字节，余量 **639 字节**——**约等于一条中短条目**。按现状，下一条发现几乎必然再触发一次压缩，而每次压缩都要付出「快照 + 倒序替换 + 双向验证 + 边界断言」的成本。**建议二选一**：拆分文件（按 P1 / P2 / 已解决分册），或**提高字节上限**并接受文件继续变大。

### 下一轮

代码方向：**JS/TS SDK**；文档方向：**运维/用户指南**（doc round 194）。

## 第 293 轮 — 2026-10-06T08:04:00+08:00

- **轮次**: 293 | **代码方向**: JS/TS SDK | **文档方向**: 运维/用户指南（doc round 194）
- **起点**: `HEAD` = `09a731f`（第 292 轮）；仅 37777 在监听

### 健康预检

活体 `/api/health` → ok；指纹 `7c7dc3fa…`（883 条），与基线一致。

### 代码方向（JS/TS SDK）—— 新立 P2-72，且它揭示 JS **自相矛盾**

**先说差点误报的部分**：初判「JS SDK 没有基类异常、6 处抛裸 `Error`」像是缺陷。**读 README 后不成立**——「Response Size Limit」段**已完整文档化**那两处裸 `Error` 并给出理由（"since the failure is local rather than an HTTP status"），且 HTML 解析失败抛裸 `Error` 属同一约定。**文档已确立的约定不是缺陷。**

**真正的新发现来自第 292 轮埋下的对照**：那个 `TypeError` **既不在导出的两个异常类内、也不带 `cortex-ce:` 前缀**——而 SDK 每一处自有消息都带（`errors.ts:14`/`:38`、`client.ts:567/688/697/718`）。按前缀过滤日志的调用方**完全看不到它**，且它把内部属性名（`'items'`/`'observations'`/`'version'`）直接漏给调用方。**立为 P2-72，⏸ 记录不修。**

**系统实测**（一次性 server 控 2xx 响应体，14 个公开读方法 × 6 种载荷 = **84 次调用**）：

| 2xx 响应体 | 结果 |
|---|---|
| HTML 错误页 | **14/14 抛裸 `Error`** |
| `null` | **9/14 抛裸 `TypeError`**，**5/14 静默返回默认值** |
| `[]` / `"oops"` / `42` / `false` | **56/56 静默** |

**同一 SDK 内部分裂的根因查清了**：`client.ts` 有 13 处 `as Record<string, unknown>`——**TypeScript 类型断言运行时是 no-op**；其中 **9 处**整体断言后立即取属性（`null` 即抛），另 **2 处**（234、486 行）用的却是**真正的 `Array.isArray` 运行时检查**（对 `null` 安全）。**类型断言与运行时检查在同一个文件里并存**，这直接造成 9 抛 5 静默。

**跨家**：**Go 是唯一对全部非对象输入都给类型化错误的**；JS 与 Python 各有一半输入落到「静默」、另一半落到「非 SDK 异常」。

**本轮核实为真、不记为缺陷**（四项）：①10 MB 上限两道守卫——`Content-Length` **读之前**先查（超限则 body 根本不被缓冲）、读之后按 **UTF-8 字节**兜底，**注释还写明了为何不能用 `String.length`**（15 MB 体在 UTF-16 码元计数下只有 5.2 M 而被误放行）；②`utf8ByteLength` 正确处理代理对；③`clearTimeout` 在 `finally`，无泄漏；④README 对裸 `Error` 的说明准确。

**探针自身出错两处**（先识别再采信）：①按位置传参调用 `startSession('s1','/tmp/p')` 等 4 个方法，而它们收**单个对象**，于是四种载荷一律抛请求侧 `ValidationError`——**那不是响应解码问题**；②用 `e.constructor.name` 判定异常类型，而 `dist` 里类名被混淆成 `_ValidationError`，**把 SDK 自己的异常误判成非 SDK 异常**。两处都修正后矩阵才成立。

### 文档方向（运维/用户指南，doc round 194）—— 零缺陷

- **7 个 `scripts/*.sh|py` 引用全部存在，零幻影**。扫描自己抓到我一个路径错误：`DOCKER_README.md` 在**仓库根**而非 `docs/`。
- **5 个活体 URL 全部 200**：`/actuator/health`、`/api/health`、`/api/stats`、`/actuator/metrics/http.server.requests`、`/api/search?...`。**`localhost` 先解析到 `::1` 而后端只绑 IPv4**，但 curl 会回退到 IPv4——**非缺陷**（与 P2-57 同族，curl 场景不受影响）。
- **两个计数按脚本自己的约定核对，均为真**：`phase3-acceptance-test.sh` 定义 `test_*` 函数**恰好 15 个**（55–623 行），对上 TESTING.md 的「15 test functions」；`run-all-e2e.sh` 的 `run_suite` **恰好 10 个**且自标 `1/10`–`10/10`。**又一次印证「数字必须连同计数口径一起核对」**——我第一版按 `test_N()` 定义去数得 0，读了脚本自己的结构才发现是 `test_<名字>()`。

### findings 第四次触发字节闸门（连续第三轮）

新增 P2-72 实耗 **1934 B**（又一次远超预估——中文三字节）。先做**第三类零信息损失的机械压缩**：其余 7 类**逐字重复**行（实测记录 6、Reproduction 3、已解决条目 4、复核记录 5+2、探针记录 2）一律只缩短显示名、链接目标不变，**-876 B**；再逐字收紧 P2-72 正文。终值 **961 行 / 102341 字节，余量 59 字节**。

**这一条必须讲清楚**：我系统扫过全文件找「仍内联（非指针）的实测明细块」，**候选数为 0**——**剩下的每一行要么是决策、要么是问题陈述、要么是指针**。也就是说，**在不丢信息的前提下，这个文件已经压无可压**。连续三轮（291/292/293）每加一条发现就要压一次，而压缩本身要付出「快照 + 倒序替换 + 双向验证 + 边界断言」的全套成本。**这已经不是可以靠本轮顺手处理的问题，需要一个结构决定。**

### 变更检测

全部为 `.md`。指纹仍 `7c7dc3fa…` / 883 条 → **不跑完整验收、不推进基线**。

### 下一轮

代码方向：**Demo**；文档方向：**API 文档**（doc round 195）。

