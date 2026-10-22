# 后端审查证据归档 -104（2026-10-22）

> 第 403 轮（Go SDK 代码方向 + 设计文档文档方向，doc round 302）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1340 行 / 149907 字节**）。
> 本文档体量：**227 行 / 19338 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。

## 本轮新写的两条（正文逐字保留在本归档）

### P2-205: Go SDK 的**出站请求体键集合**零断言 —— 十个带 body 的端点无一被钉住，且 40 个 wire 键名里有 **4** 个从未被任何测试固定

- **Scope**: → [scope-9][a6]
- **Problem**: Go SDK 的出站请求体与 Java SDK 同形：**手工构造**，8 个请求 DTO 各一组
  `json:"..."` 标签（`SessionStartRequest` / `SessionEndRequest` / `UserPromptRequest` /
  `ObservationRequest` / `ObservationUpdate` / `ExperienceRequest` / `ICLPromptRequest` /
  `BatchObservationsRequest` / `FeedbackRequest` 共 9 个），加客户端里 1 处裸 map
  （`updateSessionUserId` 的 `map[string]string{"user_id": …}`）。
  而既有测试对请求体的断言**形态**只有一种：**包含某个值或某个键名** ——
  `body["session_id"] != "sess-1"`、`body["tool_input"] == nil`、`body["project_path"] != nil`，
  加上「某个键**不该**在」的少数几处（`project_path` / `type` / `content` / `sessionId` /
  `cwd` / `userId`）。**没有任何一处断言「键集合是哪些」**。
  ⇒ 在任意一个 DTO 上加一个字段，wire 就变了，而套件**全绿**。
- **Status**: ✅ **已修（403 轮，Go SDK 侧，只加测试；SDK 主源码零改动）** —— 新增
  `wire_format_test.go`（**3** 个顶层用例、**33** 个子用例）：把**十个**带 body 的端点的
  **完整键集合**一次性钉死（因 Go 的 `omitempty` 使键集随载荷变化，分「全量」与「最小」两档），
  并把 SDK 发出的每个键与后端 `ApiRequests` 的 `@JsonProperty` 声明绑定。

### P2-206: 设计文档的验收脚本给 `/api/ingest/tool-use` 传 `project_path` —— 而该端点只读 `cwd`，未知键被**静默忽略**

- **Scope**: → [scope-9][a6]
- **Problem**: `docs/drafts/phase-3-design/26.md`（Phase 3 验收测试计划）的 **11** 处
  `POST /api/ingest/tool-use` 请求体写的是 `"project_path"`。
  而后端 `ApiRequests.ToolUseRequest` **只声明 `cwd`**（无 `project_path` 别名），
  `handleToolUse` 也只读 `body.cwd()`（`IngestionController.java:119`）。
  ⚠️ 未知 JSON 属性被**静默忽略** ⇒ 请求照旧返回 **200 `{"status":"accepted"}`**，
  **没有任何机制提示键写错了**。
  ⇒ 后果实测：排队消息的 `cwd` 记成 **NULL**；若会话未预建，会话的 project_path 落成
  **占位值 `openclaw`**（`SessionManagementService.java:54` / `:68` 的兜底），
  观测即被归到**错误的项目**下。
  ⇒ 而 `26.md` 自己写的流程**总是先建会话**，会话带着正确的 `project_path`
  （`/api/session/start` **确实**接受 `project_path`），观测的 project_path 又会
  **回落到会话的路径**（`AgentService.java:393`）—— 所以**文档自己声明的通过判据依然全过**，
  这个错误因此从未暴露。
- **Status**: ✅ **已修（403 轮，文档方向，设计文档一处）** —— `26.md` 的 **11** 处
  tool-use 请求体全部改用 `cwd`（改后 `project_path` 残留 **0** 处；同文档里
  `/api/session/start` 与 `/api/ingest/observation` 的 **11** 处 `project_path` **未动**，
  那两个端点确实接受它），并在 `26.1 Test Infrastructure` 补一段写明三个摄取钩子读 `cwd`、
  另两个端点读 `project_path`，以及「未知键会被静默忽略」这个坑。

## 按 C10 从工作文件迁入的六条（逐字保留在本归档）

> 这六条是**已结案**条目。按 C10，工作文件只留**标题 + 一行 Status**，
> 下面的 `- **Scope**` / `- **Evidence**` / `- **Status**` 是**迁移前**的原文，逐字保留。

### P2-203

### P2-203: Java SDK 的**出站请求体键集合**零断言 —— 十个带 body 的端点无一被钉住

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-103`][a61]（十个出站体的逐条键集、与后端 `ApiRequests` 的 `@JsonProperty` 对拍表、两个方向的负控读数）
- **Status**: ✅ **已修（402 轮，Java SDK 侧，测试 + 注释；行为零改动）** —— 既有断言全是「包含某个值/某个键名」，**8 处 `containsKey` 全在响应上**；新增 `CortexMemWireFormatTest`（**4** 例）把十个端点的**完整键集合**一次性钉死，并把 README 发布的 wire 名与 SDK 实际的 `@JsonProperty` 绑定。另改正 `ObservationResponse` 类 Javadoc 两处失实：称「默认 Jackson SNAKE_CASE 命名策略」（**默认是 camelCase**；那五个字段恰好是单词故无后果，但**新加多词字段会踩坑**）、称用它反序列化「`GET /api/memory/observations/{id}`」（**该端点不存在**）与「search results」（**search 返回裸 `Map`**）。

### P2-204

### P2-204: Java SDK README 的跨家断言「Go, Python and Java all decode these the same way」**失实** —— 四家里只有 Java 不拆 `refined_from_ids`，且这句漏掉 JS

- **Scope**: → [scope-10][a7]
- **Evidence**: → 归档 [`…-103`][a61]（四家 `refined_from_ids` 的真实类型与解码路径逐条对拍、README 交叉引用实际指向的形态）
- **Status**: ✅ **已修（402 轮，文档方向，双语各一处）** —— `refinedFromIds` 条目改为「**这是四家唯一不一致的一处**：Go / Python / JS 会拆成 ID 列表（`[]string` / `list[str]` / `string[]`），从那三家移植过来的代码在这里必须自己拆」；结尾跨家句子改为「四个 JSONB 列表列在四家里解码一致……`refinedFromIds` 是唯一例外」。⏸ **行为本身不改**：`String` 改 `List<String>` 是破坏性对外 API 变更，留待项目决策。

### P2-202

### P2-202: P2-22 的文档层更正**不完整** —— 同一节里 Note 明写「不是 epoch 数字」，示例却就是 epoch 数字（双语各一处）

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-102`][a60]（活体注册表 **25** 条 `installedAt` 全为 ISO-8601 字符串的原始读数、全仓 `installedAt` 清扫表、两个方向的负控逐条读数）
- **Status**: ✅ **已修（401 轮，文档方向 + Backend 代码方向）** —— 两版参考文档的「检查注册」示例改为 ISO-8601 字符串；新增 `CursorApiContractTest`（**8** 例）把「已发布的 OpenAPI example ↔ 运行时 record 组件类型」与「两版参考文档 ↔ 运行时」双向钉住；两个 Swagger 示例一并改正（见 P2-22）。

### P2-201

### P2-201: 运维/用户指南的 Go SDK 测试数停在 **372 / 306**（第 377 轮口径），实测是 **441 / 371**

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-101`][a59]（Go SDK 九个 module 的**两种计数口径**实测、`test-all.sh` 与 `go test ./...` 的覆盖差异、DEVELOPMENT 双语两行的定位与全仓引用清扫）
- **Status**: ✅ **已修（400 轮，文档方向，DEVELOPMENT 双语各一处）** —— 两处数字改为 **441**（含子测试）与 **371**（根模块，含子测试），各补一段写明口径与实测日期（2026-10-22）。与 **P2-90** 同族（测试总数要不要彻底不写死，仍待决；本轮沿用「同步数值 + 标注日期与推导」）。
- **本轮标注（403 轮）**: 本条记的是**第 400 轮**当时改成的数值；第 403 轮又因新增
  `wire_format_test.go` 同步为 **477**（含子测试）与 **407**（根模块，含子测试），
  顶层用例 **340**。**历史陈述本身仍为真**，此处只作标注、不改写。

### P2-193

### P2-193: Python SDK 客户端的 **35 处**必填参数守卫里，**3 处**从未被任何测试钉住 —— 而 README 用一张十八行的表向调用方承诺这些参数「非空即拒绝、且不发出任何请求」

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-99`][a57]（35 处守卫的逐条单变量变异与读数、3 处零钉住的定位与成因、探针自身的四处错、变异复验 M0~M9 的逐条读数）
- **Status**: ✅ **已修（398 轮，Python SDK 侧，只加测试 + 改文档，SDK 主源码零改动）** —— 新增 `tests/test_required_arguments.py`（**12** 个顶层用例，覆盖 **35** 处守卫）：逐条钉住 field + 消息 + 完整字符串 + **请求数为 0**，另加 README 表 ↔ 运行时绑定、README 承诺的每个参数 ↔ 守卫集合的包含关系、暴露面计数（**25**）、默认 base URL 绑定与「守卫计数防漂移」。复验：原先 **3** 处零钉住 → **3** 处全部转红（M1~M3 各红），M0 阴性对照 **0** 红，M4~M9 各红。

### P2-194

### P2-194: 四家 SDK README 的跨家断言「四家强制的是**同一组**参数、只有**一处**例外」是**失实陈述** —— 漏报了**第二处**差异：**Python 是唯一放行纯空白参数的 SDK**

- **Scope**: → [scope-10][a7]
- **Evidence**: → 归档 [`…-99`][a57]（四家同探针实测读数、Python 内部「只有 ids 元素去空白、其余 30 余处不去」的不一致、八处断言的逐句清点与行号）
- **Status**: ✅ **已修（398 轮，文档方向，四份 README 双语共 8 处）** —— 八处一律改为「**两处**差异」并点名纯空白这条（Go / Java / JS 三家把纯空白也当空值拒，Python 只拒空串）。Python 那两段**钉进测试**（删掉就红）。**行为本身不改**：收窄接受集是对外契约变更，按规则只记录不单方面实施。

## P2-205 的证据：Go SDK 出站请求体的完整键集合

| 端点 | Go SDK 发出的键（全量载荷） | 后端 `ApiRequests` 声明的键 | 判定 |
|------|----------------------------|---------------------------|------|
| `POST /api/session/start` | `session_id` `project_path` `user_id` | `SessionStartRequest`：同上 + `cwd` `projects` `is_worktree` `parent_project` `platform_source` | SDK 是子集 ✓ |
| `POST /api/ingest/tool-use` | `session_id` `cwd` `tool_name` `tool_input` `tool_response` `prompt_number` `source` `extractedData` | `ToolUseRequest`：同上 + `platform_source` | SDK 是子集 ✓ |
| `POST /api/ingest/session-end` | `session_id` `cwd` `last_assistant_message` | `SessionEndRequest`：完全相同 | 一致 ✓ |
| `POST /api/ingest/user-prompt` | `session_id` `prompt_text` `cwd` `prompt_number` | `UserPromptRequest`：同上 + `platform_source` | SDK 是子集 ✓ |
| `POST /api/memory/experiences` | `task` `project` `count` `source` `requiredConcepts` `userId` | `ExperienceRequest`：完全相同 | 一致 ✓ |
| `POST /api/memory/icl-prompt` | `task` `project` `maxChars` `userId` | `ICLPromptRequest`：完全相同 | 一致 ✓ |
| `POST /api/observations/batch` | `ids` | `BatchObservationsRequest`：`ids` + `project` `orderBy` `limit` | SDK 是子集 ✓ |
| `POST /api/memory/feedback` | `observationId` `feedbackType` `comment` | `FeedbackRequest`：完全相同 | 一致 ✓ |
| `PATCH /api/memory/observations/{id}`（`content` 变体） | `title` `subtitle` `content` `facts` `concepts` `source` `extractedData` | `MemoryController` 读 `title` `content`/`narrative` `subtitle` `source` `facts` `concepts` `extractedData` | SDK 是子集 ✓ |
| `PATCH /api/memory/observations/{id}`（`narrative` 变体） | `title` `subtitle` `narrative` `facts` `concepts` `source` `extractedData` | 同上 | SDK 是子集 ✓ |
| `PATCH /api/session/{id}/user` | `user_id` | `SessionUserUpdateRequest`：`@JsonProperty("user_id")` | 一致 ✓ |

> `content` 与 `narrative` 是同一个后端字段的两个别名，`UpdateObservation` 拒绝同时设置
> （`Validate()` → `HasConflict()`），故完整键集**分两个变体**钉。

### 三个方向的变异读数（单变量，每次都按 sha256 逐字节还原）

| 变异 | 手法 | 修复前 | 修复后 |
|------|------|--------|--------|
| **加字段** | 给 9 个请求 DTO 各加一个**无 `omitempty`** 的字段（必然上 wire），跑整个模块套件 | **9 / 9 全绿** | **9 / 9 全红** |
| **改键名** | 把 DTO 声明的 **40** 个 wire 键逐个改名，跑整个模块套件 | 钉住 **36** / 未钉住 **4** | 钉住 **40** / 未钉住 **0** |

修复前那 **4** 个「未钉住」的键：`UserPromptRequest.prompt_number`、
`ObservationUpdate.content`、`ObservationUpdate.facts`、`FeedbackRequest.comment`。

> ⭐ `ObservationUpdate.content` 是**文档明写有别名冲突语义**的字段（与 `narrative` 互斥，
> `HasConflict()` 会拦），而它此前**没有任何测试固定它的 wire 名**。

## P2-206 的证据：设计文档的出站 body 键审计

审计口径：把 `docs/drafts/phase-3-design/*.md` 里**每一个**带 `-d '{…}'` 的 curl 块解析出来，
配对它自己那一条命令里的 URL，再把 body 的键与后端 `ApiRequests` 的 `@JsonProperty` 声明对拍。

| 口径 | 数值 |
|------|------|
| 带 `-d` body 的 curl 块 | **42** |
| 对上契约并被审计 | **39** |
| 未纳入审计（`/api/session/{id}/user` 两处 ＋ demo 的 `/memory/icl-prompt` 一处） | 3 |
| **契约违规** | **11**，全部是 `/api/ingest/tool-use` 传 `project_path` |

### 活体实测（新鲜 37790，jar 比全部后端源码新）

| 用例 | 请求体 | HTTP | `mem_sessions.project_path` | `mem_pending_messages.cwd` |
|------|--------|------|---------------------------|---------------------------|
| **A**：`project_path`（文档写法） | `{"session_id":"p403-case-a","project_path":"/tmp/p403-ext-test","tool_name":"chat","tool_response":"hello"}` | **200** `{"status":"accepted"}` | **`openclaw`**（占位值） | **NULL** |
| **B**：`cwd`（契约键，阳性对照） | `{"session_id":"p403-case-b","cwd":"/tmp/p403-ext-test","tool_name":"chat","tool_response":"hello"}` | **200** `{"status":"accepted"}` | `/tmp/p403-ext-test` | `/tmp/p403-ext-test` |
| **C**：文档自己的流程（先建会话，再发 tool-use） | 同 A，但会话已由 `/api/session/start` 建好 | **200** | `/tmp/p403-ext-test`（会话带着） | **NULL** |

⇒ **A 与 B 的差别就是那一个键名**；**C 证明文档声明的通过判据依然全过** ——
这正是该错误从未暴露的原因（会话先建 ＋ `AgentService.java:393` 的回落到会话路径）。

### 权威脚本本来就没走这条路径

`scripts/phase3-acceptance-test.sh`（`26.md` 自己指定的「完成定义」）用的是
`POST /api/ingest/observation`（**接受** `project_path`），**从未**调用
`/api/ingest/tool-use`。⇒ `26.md` 里的 tool-use 片段是该流程唯一的书面来源，
也就成了错误唯一存活的地方。

## 本轮探针/判据自身的五处错（全部在写成发现之前拦下）

1. **文档审计第一版把多行 curl 的端点归属判错了**：用「往前 4 行找 URL」的方式取端点，
   于是 `26.md` 的 tool-use 片段被归到**上一个** curl 的 `/api/session/start`，
   报出 **3** 个假违规（`25.md:840`、`26.md:160/167/178`），
   同时漏掉 **3** 个真违规（真数 **11**，第一版只报 **8**）。⇒ 改成**按 curl 块解析**。
2. **第二版的 URL 识别与块切分两处都错**：`startswith("curl")` 的守卫恰好把
   `curl …` 开头的行排除掉，只留下 `$(curl …)` 形态 ⇒ 只找到 **12 / 42** 个块；
   URL 正则也没认 `http://127.0.0.1:37777` 与 `${DEMO_URL}` ⇒ 2 处假「未找到端点」。
3. **键名变异扫描改错了结构体**：替换用的是「文件里第一个同名字符串」，
   而 `dto/session.go` 里第一个 `json:"prompt_number"` 属于 **`SessionStartResponse`**
   （**响应**字段，第 20 行），不是 `UserPromptRequest`（第 48 行）——
   改响应字段对出站 body 毫无影响 ⇒ 报出一个假的「未钉住」。
   ⇒ 改成**按结构体块**定位后重跑。
4. **负控与测试套件撞车**：变异扫描还在改文件时跑了一次全量套件，
   读到的是**扫描中途**的 `zz_wrong_project` ⇒ 一次假失败。⇒ 串行执行。
5. **自检缺失**：新写的键集合测试若把「期望键集」从 DTO 自己的 `json` 标签推导出来，
   就是**恒真断言** ⇒ 期望值**全部手写**，并用「加字段变异」证明它会红。

## 验收

| 项目 | 读数 |
|------|------|
| Go SDK 全量（**9** 个 module，`test-all.sh`） | **9 / 9** 模块通过，**0** 失败 |
| Go SDK 测试数（`go test -json` 按 `(package, test)` 对，**含子测试**） | **477**（改前 **441** ⇒ 本轮 **+36**）；顶层用例 **340**（改前 **337**） |
| 根模块（`go test ./...` 在 `go-sdk/cortex-mem-go`，**含子测试**） | **407**（改前 **371** ⇒ 本轮 **+36**） |
| 负控（加字段） | 修复前 **9 / 9 全绿** → 修复后 **9 / 9 全红** ⇒ 断言有牙 |
| 负控（改键名） | 修复前 **40** 键钉住 **36** → 修复后 **40 / 40** ⇒ 断言有牙 |
| 设计文档 body 键审计 | 修复前 **11** 处违规 → 修复后 **0** |
| 回归 | **46 / 0 / 1**，exit 0，跑在新鲜 37790（pid **59165**） |
| Phase3 验收 | **25 / 0 / 0**，exit 0，同一实例 |
| 新鲜度闸门 | `find backend/src -newer <jar>` 为空（后端源码未动） |
| 指纹 | `e2955606…` → `4289527c…`，记录数 2242 → **2243** |

## 压缩记账

| 口径 | 数值 |
|------|------|
| 工作文件**写入前** | **1338 行 / 149682 字节**（余量 **318** 字节） |
| C8（⏸ 条目的证据段） | **已用尽**（第 400 轮用尽，本轮复核无剩余） |
| C10 的 Scope/Evidence 一支 | **已用尽**：全部 **193** 条的 `- **Scope**` / `- **Evidence**` **都已是归档指针** |
| 本轮实际用的杠杆 | **C10 的「已结案条目正文整体迁入归档，工作文件只留标题 + 一行 Status」**：把 **6** 条已结案条目（P2-203 / P2-204 / P2-202 / P2-201 / P2-193 / P2-194）的 `- **Scope**` / `- **Evidence**` **逐字**迁入本归档，并**缩短**其 Status 行 |
| 工作文件**写入后** | **1340 行 / 149907 字节**（余量 **93** 字节） |
| 工作文件**净变化** | **1338 行 / 149682 字节** → **1340 行 / 149907 字节**（**+2 行 / +225 字节**） |
| 本归档体量 | **227 行 / 19338 字节** |

⇒ 本轮**未改阈值**：余量虽只剩 **93** 字节，但 C10 这一支本轮还有余量可压；
⇒ **P2-115 仍是待用户决策的流程契约变更**（建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，
只同步 `docs/drafts/cron-combined-task.md:14`）。

> ⇒ 与归档 `-103` 的记账纪律一致：**迁出量、写入量、净变化是三个数，不可混用**。
> ⇒ 压缩后的每条 Status，其**事实 token**（数字、标识符、引号内的字面量）都**逐字**存在于
> 本归档里 —— 迁移前原文已逐字保存在上方，校验脚本逐 token 核对过。

## 本轮链接定义

```
[a62]: ../archive/2026-10-22_backend-review-evidence-104.md
```
