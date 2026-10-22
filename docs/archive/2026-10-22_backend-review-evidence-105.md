# 后端审查证据归档 -105（2026-10-22）

> 第 404 轮（Python SDK 代码方向 + 架构文档文档方向，doc round 303）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1345 行 / 149498 字节**）。
> 本文档体量：**210 行 / 17007 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。

## 本轮新写的两条（正文逐字保留在本归档）

### P2-207: Python SDK 的**出站请求体键集合**零断言 —— 十个带 body 的端点无一被钉住，且 40 个 wire 键名里有 **11** 个从未被任何测试固定

- **Scope**: → [scope-9][a6]
- **Problem**: Python SDK 的出站请求体**全部在客户端内联构造**（`client.py` 里逐个方法手写
  `body` 字典），**没有一个请求 DTO** —— 与 Java / Go 两家「DTO 带 `json` 标签」的结构不同，
  但缺口是同一种：往 body 里加一个键，wire 就变了，而后端**静默忽略**未知 JSON 属性。
  既有测试对请求体的断言**形态**只有三种，**没有一种断言「键集合是哪些」**：

  | 形态 | 断言写法 | 处数 |
  |------|----------|------|
  | A | 整个 body 精确等值（`json.loads(body) == {...}`） | **1**（`tests/test_client.py:959`，只覆盖 `update_observation` 的 `extracted_data={}` 一例） |
  | B | `assert "<key>" in body` | **19** |
  | C | `assert body["<key>"] == <value>` | **22** |
  | D | 键集合比较（`set(body) == {...}`） | **0** |

  ⇒ 形态 D 为 **0**，而形态 B / C 只问「某个键在不在 / 值对不对」，**不问「还有没有别的键」**。
- **实测（修复前）**：
  - **加字段变异**（给 9 个带 body 的端点各加一个**必然上线**的键，跑整套件）：
    **9 / 9 全绿** ⇒ 完整键集合在**任何地方**都没被钉住。
    （PATCH 观测端点未纳入这一格：它已有形态 A 的那一处精确等值断言。）
  - **改键名变异**（把 40 个 wire 键逐个改名，跑整套件）：钉住 **29**、**未钉住 11**。
    未钉住的 11 个是：

    | 端点 | 未被钉住的 wire 键 |
    |------|--------------------|
    | `POST /api/ingest/tool-use` | `tool_input`、`tool_response`、`prompt_number` |
    | `POST /api/ingest/session-end` | `cwd`、`last_assistant_message` |
    | `POST /api/ingest/user-prompt` | `prompt_number` |
    | `POST /api/memory/experiences` | `project` |
    | `POST /api/memory/icl-prompt` | `project` |
    | `POST /api/memory/feedback` | `comment` |
    | `PATCH /api/memory/observations/{id}` | `subtitle`、`facts` |

- **Status**: ✅ **已修（404 轮，Python SDK 侧，只加测试）** —— 新增 `tests/test_wire_format.py`（**4** 个用例）钉死十个端点的**完整键集合**；负控：加字段 **9 / 9 全绿 → 全红**，改键名钉住 **29 → 40**（未钉住 **11 → 0**）；正文与证据逐字见归档 [`…-105`][a63]。

### P2-208: 架构文档「Java 21+ Features Used」块的**三处**引用失实 —— 两处行号已漂移、一个方法名从未存在

- **Scope**: → [scope-9][a6]
- **Problem**: `docs/ARCHITECTURE.md` 与 `docs/ARCHITECTURE-zh-CN.md`（**双语各一份**，
  行号完全相同）的「Java 21+ Features Used」代码块里有三处对后端源码的引用与**现状**不符。
  三处的**性质不同**，必须分开记：

  | # | 文档写的 | 现状 | 性质 |
  |---|----------|------|------|
  | 1 | `ApiRequests.ToolUseRequest` 的逐字摘录**8** 个组件 | 真实 record 有 **9** 个组件（`ApiRequests.java:51-76`），第 9 个是 `@JsonProperty("platform_source") String platformSource`（在 `tool_response` 之后、`source` 之前，**第 63 行**） | **漂移**：V18 platform-source 迁移（提交 `6e5890d`）加的；`git show 6e5890d^:…` 实测迁移前该 record **确实只有 8** 个 ⇒ 摘录当年是准的 |
  | 2 | `SummaryGenerationService:83` + 方法名 `generateSummaryAsync` | `@Async` 在 **86** 行，方法名是 **`completeSessionAsync`**（**86-87**）；**83** 行是 javadoc 的 `@param` 行 | 行号是**漂移**（迁移前实测 `@Async` 确在 **83**）；但 `generateSummaryAsync` 这个方法名**在后端源码里从未存在过** —— `git log --oneline -S 'generateSummaryAsync' --all -- backend/` **返回空**（全仓只在这两份架构文档里出现）⇒ **出生即假** |
  | 3 | `SummaryGenerationService:108`（"唯一在 Java 内拼装的提示词…用字符串拼接加 `\n`"） | 拼接语句在 **114** 行（`String systemPrompt = "You are a memory observer agent. …:\n\n" + digest;`）；**108** 行是注释 `// Build digest for LLM` | 行号是**漂移**（迁移前实测拼接确在 **108**） |

  ⇒ 另有一处**唯一性断言**已经不成立：文档写「**唯一**在 Java 内拼装的提示词」，
  而 `StructuredExtractionService` 的四个抽取提示词同样**在 Java 内拼装**
  （`buildSystemPrompt` **665**、`buildUserPrompt` **681**、
  `buildAppendOnlySystemPrompt` **387**、`buildAppendOnlyUserPrompt` **414**，都用 `StringBuilder`）。
  ⇒ 文档自己的**唯一性**是「唯一用 `+` 拼一个带 `\n` 的字面量」，
  而**带 `\n` 字面量的 `+` 拼接在后端并不唯一**（`LlmQualityScorer:50-54`、`ExpRagService:188`、
  `ContextService:907/998`、`StructuredExtractionService:325` 都有）⇒ 只能按「谁拼、拼什么」如实描述。
- **Status**: ✅ **已修（404 轮，文档方向，双语各一份）** —— `SummaryGenerationService:83` → **86**、`generateSummaryAsync` → `completeSessionAsync`、`:108` → **114**；`ToolUseRequest` 摘录补回第 **9** 个组件 `platform_source`；正文与证据逐字见归档 [`…-105`][a63]。

## 按 C10 从工作文件迁入的两条（逐字保留在本归档）

> 这两条是**已结案**条目。按 C10，工作文件只留**标题 + 一行 Status**，
> 下面的 `- **Scope**` / `- **Evidence**` / `- **Status**` 是**迁移前**的原文，逐字保留。

### P2-205

### P2-205: Go SDK 的**出站请求体键集合**零断言 —— 十个带 body 的端点无一被钉住，且 40 个 wire 键名里有 **4** 个从未被任何测试固定

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-104`][a62]（九个请求 DTO 加字段变异的逐条读数、40 个 wire 键改名的逐条读数、十个端点的完整键集对拍表）
- **Status**: ✅ **已修（403 轮，Go SDK 侧，只加测试；SDK 主源码零改动）** —— 新增 `wire_format_test.go`（**3** 个顶层用例、**33** 个子用例）：把**十个**带 body 的端点的**完整键集合**一次性钉死（因 Go 的 `omitempty` 使键集随载荷变化，分「全量」与「最小」两档），并把 SDK 发出的每个键与后端 `ApiRequests` 的 `@JsonProperty` 声明绑定。负控：加字段变异由 **9 / 9 全绿** 变为 **9 / 9 全红**；改键名由钉住 **36** 变为钉住 **40**（未钉住 **4** → **0**）。

### P2-206

### P2-206: 设计文档的验收脚本给 `/api/ingest/tool-use` 传 `project_path` —— 而该端点只读 `cwd`，未知键被**静默忽略**

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-104`][a62]（42 个带 body 的 curl 块的端点↔键对拍表、三个用例的活体读数、权威脚本从未走 tool-use 的证据）
- **Status**: ✅ **已修（403 轮，文档方向，设计文档一处）** —— `26.md` 的 **11** 处 tool-use 请求体全部改用 `cwd`（改后 `project_path` 残留 **0** 处；同文档里 `/api/session/start` 与 `/api/ingest/observation` 的 **11** 处 `project_path` **未动**，那两个端点确实接受它），并在 `26.1 Test Infrastructure` 补一段写明三个摄取钩子读 `cwd`、另两个端点读 `project_path`，以及「未知键会被静默忽略」这个坑。

## P2-207 的证据：Python SDK 出站请求体的完整键集合

### 新增测试的四个用例

| 用例 | 作用 |
|------|------|
| `test_wire_format_complete_key_set` | 十个端点**全量**载荷的完整键集合 |
| `test_wire_format_minimal_key_set` | 十个端点**最小**载荷的完整键集合（该 SDK 省略空的可选字段，故键集随载荷变化） |
| `test_wire_format_sent_keys_are_declared_by_the_backend` | SDK 发出的每个键都必须是后端**声明过**的键（子集关系，不是相等） |
| `test_wire_format_assertion_is_falsifiable` | **自检**：拿真正捕获到的 body 注入「多一个键」与「改一个键名」两种破坏，断言比较函数**确实**报出差异 |

### 与后端契约的绑定

- 八个端点读后端 `dto/ApiRequests.java` 里对应 record 的 `@JsonProperty` 名
  （`SessionStartRequest` / `SessionUserUpdateRequest` / `ToolUseRequest` / `SessionEndRequest` /
  `UserPromptRequest` / `ExperienceRequest` / `ICLPromptRequest` / `FeedbackRequest` /
  `BatchObservationsRequest`）。
- `PATCH /api/memory/observations/{id}` 在后端收的是**裸 `Map`**，它认的键集只能从
  `MemoryController` 的 `body.containsKey("…")` 里读出（**8** 个：`title`、`content`、`narrative`、
  `subtitle`、`source`、`facts`、`concepts`、`extractedData`）。
- 期望键集**全部手写** —— 从 SDK 自己的 body 构造代码推导 ⇒ **恒真断言**，
  比没有断言更糟（与 Go 轮同一条纪律，见归档 `-104` 第 5 条）。

### 十个端点的键集（手写期望值）

| 端点 | 全量键集 | 最小键集 |
|------|----------|----------|
| `POST /api/session/start` | `session_id` `project_path` `user_id` | `session_id` `project_path` |
| `PATCH /api/session/{id}/user` | `user_id` | `user_id` |
| `POST /api/ingest/tool-use` | `session_id` `cwd` `tool_name` `tool_input` `tool_response` `prompt_number` `source` `extractedData` | `session_id` `cwd` `tool_name` |
| `POST /api/ingest/session-end` | `session_id` `cwd` `last_assistant_message` | `session_id` `cwd` |
| `POST /api/ingest/user-prompt` | `session_id` `prompt_text` `cwd` `prompt_number` | `session_id` `prompt_text` `cwd` |
| `POST /api/memory/experiences` | `task` `project` `count` `source` `requiredConcepts` `userId` | `task` |
| `POST /api/memory/icl-prompt` | `task` `project` `maxChars` `userId` | `task` |
| `POST /api/observations/batch` | `ids` | `ids` |
| `POST /api/memory/feedback` | `observationId` `feedbackType` `comment` | `observationId` `feedbackType` |
| `PATCH /api/memory/observations/{id}` | `title` `subtitle` `narrative` `facts` `concepts` `source` `extractedData` | `title` |

> PATCH 观测端点**没有** `content` 这个 wire 键：该 SDK 的 `content` 与 `narrative` 是
> **同一后端字段的两个别名**，两者都映射到 `"narrative"`（`ObservationUpdate._WIRE_FIELDS`），
> 与 Go SDK 的两个变体（Go 另有 `content` 键）不同。

## P2-208 的证据：架构文档三处引用的逐条核验

| 文档写的 | 实测 | 结论 |
|----------|------|------|
| `ToolUseRequest` 摘录 **8** 个组件 | 真实 record **9** 个（`ApiRequests.java:51-76`，`platform_source` 在 **63** 行）；`git show 6e5890d^:…` 实测迁移前 **8** 个 | **漂移**，摘录已补回第 9 个 |
| `SummaryGenerationService:83` | `@Async` 在 **86** 行，方法 `completeSessionAsync` 在 **87** 行；**83** 行是 javadoc `@param`；`git show 6e5890d^:…` 实测迁移前 `@Async` 确在 **83** | **漂移**，已改为 86 |
| 方法名 `generateSummaryAsync` | `git log --oneline -S 'generateSummaryAsync' --all -- backend/` **返回空**；该类唯一的 `@Async` 方法是 `completeSessionAsync`（**86-87**） | **出生即假**，已改名 |
| `SummaryGenerationService:108` | 拼接语句在 **114** 行；**108** 行是注释；`git show 6e5890d^:…` 实测迁移前拼接确在 **108** | **漂移**，已改为 114 |
| 「**唯一**在 Java 内拼装的提示词」 | `StructuredExtractionService` 的四个抽取提示词同样在 Java 内拼装（**387** / **414** / **665** / **681**） | **唯一性不成立**，已改为如实表述 |

### 同段落里**核实无误、未改动**的部分

- `MemoryRefineService:91` —— `@Async` 确在 **91** 行，方法 `refineMemory` 在 **92** 行 ✅
- `dto/OffsetPageRequest.java:107-114` —— `equals` 确在 **107-114**，**109** 行确为 `instanceof` 行，
  且确比较**四个**字段 ✅
- `src/main/resources/prompts/{init,observation,summary,continuation}.txt` —— 四个文件都在 ✅
- 「提示词是外部资源，**不是文本块**」—— 全仓 `"""` 只出现在 JPQL `@Query` 注解里，
  没有任何提示词文本块 ✅

### 按断言清扫

`generateSummaryAsync` 与 `SummaryGenerationService` 在**活文档**里只出现在
`docs/ARCHITECTURE.md` 与 `docs/ARCHITECTURE-zh-CN.md` 这两处；
其余命中全在**带日期的归档快照**（不可改）与**追加式审计链**
（`docs/drafts/doc-review-task.md`）里 —— 后者记载「219 轮时 `SummaryGenerationService:83` 与 `:108`
**精确命中**」，与本次的漂移结论**一致**（V18 迁移发生在其之后）。

## 本轮探针/判据自身的四处错（全部在写成发现之前拦下）

1. **204 响应不能带 body**：新测试首版用 `responses.add(…, json={…}, status=204)` 注册 mock，
   `requests` 读响应时抛 `ChunkedEncodingError`（`Response may not contain content`）⇒ 3 个用例红。
   ⇒ 改为 `body=""` 注册。**教训**：状态码决定响应体是否合法，不是「反正不解析」。
2. **改键名变异必须按方法块定位**：`"user_id"` 在 `client.py` 的多个方法里都出现，
   整文件替换会**一次改掉多个端点**，失败归因不清 ⇒ 改为在
   `    def <name>(` 到下一个 `\n    def ` 的块内替换。
3. **`_WIRE_FIELDS` 的属性名 ≠ wire 名**：`content` / `narrative` 都映射到 `"narrative"`，
   `extracted_data` 映射到 `"extractedData"` ⇒ 按属性名替换改的是**校验用的属性名**，
   不是 wire 名 ⇒ 必须改**值**一侧。
4. **测「修复前」必须把新测试物理移开**：新测试自己就钉住键集合，
   带着它测「修复前」必然全红，测不出缺口 ⇒ `mv tests/test_wire_format.py /tmp/…`，
   测完再移回（两次扫描前后都用 `git status` 核对 `client.py` / `dto.py` 已被逐字还原）。

## 验收

| 项目 | 读数 |
|------|------|
| Python SDK 全量（`python3.11 -m pytest tests/ -q`） | **487** 通过 / **0** 失败（改前 **483** ⇒ 本轮 **+4**） |
| 负控（加字段） | 修复前 **9 / 9 全绿** → 修复后 **9 / 9 全红** ⇒ 断言有牙 |
| 负控（改键名） | 修复前 **40** 键钉住 **29** → 修复后 **40 / 40** ⇒ 断言有牙 |
| 回归 | **46 / 0 / 1**，exit 0，跑在新鲜 37790（pid **97662**） |
| Phase3 验收 | **25 / 0 / 0**，exit 0，同一实例 |
| 新鲜度闸门 | `find backend/src -newer <jar>` 为空（后端源码未动） |
| 指纹 | `4289527c…` → `a7ae8cc2…`，记录数 2243 → **2244** |

## 压缩记账

| 口径 | 数值 |
|------|------|
| 工作文件**写入前** | **1340 行 / 149907 字节**（余量 **93** 字节） |
| C8（⏸ 条目的证据段） | **已用尽**（第 400 轮用尽，本轮复核无剩余） |
| C10 的 Scope/Evidence 一支 | **近乎用尽**：本轮压缩前，工作文件里只剩 **P2-205** / **P2-206** 两条还带 `- **Scope**` / `- **Evidence**` 行 |
| 本轮实际用的杠杆 | ① **C10 的 Scope/Evidence 一支**：把 P2-205 / P2-206 的 `- **Scope**` / `- **Evidence**` **逐字**迁入本归档（**411** 字节 / **4** 行）；② **第 292/293 轮的「缩短已结案条目的 Status 行」**：缩短 **P2-200** / **P2-201** / **P2-202** / **P2-203** / **P2-193** / **P2-194** 共 **6** 行，并同步缩短本轮新写两条的 Status 行 |
| 工作文件**写入后** | **1345 行 / 149498 字节**（余量 **502** 字节） |
| 工作文件**净变化** | **1340 行 / 149907 字节** → **1345 行 / 149498 字节**（**+5 行 / −409 字节**） |
| 本归档体量 | **210 行 / 17007 字节** |

⇒ 本轮**未改阈值**：余量从 **93** 字节回到 **502** 字节，但 C8 与 C10 的 Scope/Evidence
⇒ **两支都已用尽**，下一轮写入将再次无空间可用；
⇒ **P2-115 仍是待用户决策的流程契约变更**（建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，
只同步 `docs/drafts/cron-combined-task.md:14`）。

> ⇒ 与归档 `-104` 的记账纪律一致：**迁出量、写入量、净变化是三个数，不可混用**。
> ⇒ 压缩后的每条 Status，其**事实 token**（数字、标识符、引号内的字面量）都**逐字**存在于
> 本归档里 —— 迁移前原文已逐字保存在上方，校验脚本逐 token 核对过。

## 本轮链接定义

```
[a63]: ../archive/2026-10-22_backend-review-evidence-105.md
```
