# Health Check History Archive 18（第 368 / 369~370 / 371 / 372 / 373 / 374 轮，2026-10-21）

> **Archived**: 2026-10-21，第 389 轮压缩迁移，自 `docs/drafts/health-check-task.md` 第 102–653 行。
> **Rule**: 归档文件创建后不得修改。后续历史达阈值时使用新的日期命名归档。
>
> 以下为第 368 / 371 / 372 / 373 / 374 轮轮报告的**逐字原文**（含其间的空行）。
> 迁移原因：本文件写入第 389 轮报告后行数逼近 1500 行上限（迁出前 **1477 行 / 149501 字节**），
> 而第 389 轮的新报告仍需续写。
> 第 369~370 轮的指针行（第 204 行）一并逐字迁入本文件，**原位保留**其指向
> [`2026-10-20_health-check-history-16.md`](2026-10-20_health-check-history-16.md) 的链接。

> **第 368 轮：Python SDK（代码）+ API 文档（文档，doc round 268）**：
> **健康预检**：`/api/health` → `{"service":"claude-mem-java","status":"ok"}`（HTTP 200）、
> `/actuator/health` → **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的），
> 且 `find backend/src/main -newer <jar>` **零命中** ⇒ 后端源码自 jar 构建以来未变，在跑实例即当前后端。
> **代码方向（Python SDK）承 P2-122 跨家结转（✅ 已修，三处）**：Python 的 apiKey 措辞与 Go 修复前**完全同型** ——
> `cortex_mem/client.py:68` 的 docstring（`api_key: Sent as ``Authorization: Bearer <key>`` on every request.`）
> 与 `README.md:175` / `README-zh-CN.md:171` 的属性表行，**都只说「发 Bearer」而未提后端从不校验它**。
> 三处现已改写为「每次请求以 `Authorization: Bearer <key>` 发出，**当前后端不校验这个头**（它没有任何鉴权过滤器），
> 故配置它今天保护不了任何东西」，并指向架构文档的「认证」一节。
> **新立 P2-125（✅ 已修，双语）**：Python README 用一个**无日期的活体读数**支撑「`list_observations` 会放宽项目过滤」
> 这个结论 —— 原文写「不带 `project` 时，一个 100 条的单页里返回了 **16 个不同项目**」。
> **2026-10-20 活体复测（连测三次，稳定）实测为 15**（**不是 16**）；反向对照：库里
> `SELECT count(DISTINCT project_path) FROM mem_observations` = **2276** —— 即该读数是**库内容的函数**，
> 随项目数增长必然继续漂移（同一端点带默认 limit 时返回 20 条、distinct 为 4，说明读数随取回条数变化）。
> 现已**加上测量日期 2026-10-20**、更正为当日实测值，并明写「该数字随数据库的增长而变，故它是带日期的实测值
> 而非实时值 —— 要点是跨项目的广度，而不是确切数字」。**「要不要让后端在行为层收敛」留待用户决策**。
> **Python SDK 其余部分逐条核实为真**：**23 个端点路径**对活体 **66 路径**（4 条 f-string 模板按**占位符逐位**匹配）
> **零幻影**；**重试语义精确** —— `max_retries=3` 是**总尝试次数**（`range(1, max_retries+1)`）、下限 1、
> `retry_backoff=0.5` 单位**秒**、下限 0.1、jitter `uniform(-0.25, 0.25)`、只重试
> **429/502/503/504 + 网络错误**（`is_retryable` 的集合逐字吻合），且 docstring 声称的
> 「Matches Go SDK's doFireAndForget」**逐条为真**（Go 侧 `baseDelay*attempt` 线性 + `base/4` 的 ±25% jitter 同）；
> **README 方法表 26 = 26 零幻影零遗漏**（实现 26 个 public 方法）；
> **「四家强制完全相同的一组必填规则」这条跨家断言为真** —— 从 Go / JS / Java 的**实现**里分别提取
> 必填参数集合（Go 是 `strings.TrimSpace(x)==""` 的**裸参数**形态、JS 是 `this.validateRequired('x',…)`、
> Java 是 `requireNonBlank(expr,"x")` 的**第二参数**形态），归一后与 Python README 的 18 行表
> **逐条吻合**（17 个有必填参数的方法，无遗漏无多余），四家的 `limit < 0` 值域校验也都存在；
> **空更新的错误消息四家逐字相同**（`at least one field must be provided for update`），
> 且 `ValidationError` 的字符串格式四家逐字相同（`cortex-ce: validation error on <field>: <msg>`）；
> `pyproject.toml` 的强制依赖确实只有 `requests>=2.28`（flask 是可选 demo 依赖）。
> **Python SDK 自带测试 453 通过 / 0 失败**（改动前后各跑一次）。
> **文档方向（API 文档，doc round 268）新立 P2-126（✅ 已修，双语）**：**三个 ingest 端点的请求体字段表
> 一个都没列活体 OpenAPI 声明的 6 个字段** —— `POST /api/ingest/tool-use` 缺 **4** 个
> （`platform_source` / `source` / `extractedData` / `prompt_number`）、`POST /api/ingest/user-prompt`
> 缺 **1** 个（`platform_source`）、`POST /api/ingest/observation` 缺 **1** 个（`platform_source`）。
> 其中 `platform_source`（**V18 Platform Source Tracking** 引入）是**确实生效**的字段 ——
> 三个 ingest 处理器**都**调用 `applyPlatformSource(session, body.platformSource())`
> （`IngestionController.java:141 / 255 / 352`），而它**此前在请求侧完全没有任何记载**；
> 另三个（tool-use 的 `source` / `extractedData` / `prompt_number`）则是**接受却被静默丢弃**的 ——
> `handleToolUse`（`:110-150`）**从不读** `body.source()` / `body.extractedData()`，
> 且调用 `processToolUseAsync(..., cwd, **null**)` 时把提示序号**写死为 `null`**。
> **活体实测（带阳性对照与反向对照）**：tool-use 请求带 `prompt_number: 42` ⇒ pending 行落库 **NULL**；
> **阳性对照** —— 同一字段在 `POST /api/ingest/user-prompt` 上落库为 **42** ⇒ 探针**确实读得到**这个值，
> 故那个 NULL 是**丢弃**而非探针盲区；落库的 `tool_input` 里也不含 `source` / `extractedData` 的内容。
> **已实施（双语，纯文档、零行为变更）**：三张请求体表**共补 6 行**，`tool-use` 另**新增一段实测说明**，
> 逐条写明哪三个字段被忽略、为什么（`processToolUseAsync(..., null)` 写死）、两个同级端点如何照用它们，
> 以及 `platform_source` 写入**会话**且已存值不被覆盖（唯一例外：请求值非 `claude` 时替换已存的 `claude`）。
> **新立 P2-127（⏸ 记录不实施）**：**活体 OpenAPI 把 `POST /api/ingest/user-prompt` 的 `prompt_text` 标为
> required，而后端只强制 `session_id`** —— `ApiRequests.java:86` 的 `requiredMode = REQUIRED` 是 spec 的来源，
> 而 `handleUserPrompt`（`:213-235`）**只**校验 `session_id`。**活体实测（带阳性对照与阴性对照）**：
> 只带 `session_id` → **200** `{"status":"ok"}`（spec 说这该 400）；带上 `prompt_text` → **200** 且落库
> `'p368 control'`（**阳性对照**）；完全不带 `session_id` → **400** `Missing required field: session_id`
> （**阴性对照**）。**即：活体 spec 失实，而 API 文档是对的**（文档早已写明两个可选字段缺省或为空都被接受）。
> **三选一见归档正文**（①去掉注解的 REQUIRED，**纯放宽**，属后端代码方向、建议下个后端轮次实施；
> ②让实现真的强制，**收紧**、会推翻文档已记载的行为；③维持现状）。
> **容量（本轮的一个重要结论）**：findings 的字节余量只剩 **33 字节**，本轮实测了「按现行规则还能不能继续压缩」
> ⇒ **不能**：把待决条目的 42 段 Scope/Evidence 迁入归档后，字节**不降反升**（149967 → 151177，**+1210**）——
> **42 段里 41 段本身就是指针行**（`- **Scope**: → [scope-9](...)`），换成指向新归档的指针**更长**。
> 该次压缩已**精确反推**（用删除前快照双向验证：42 段原文逐字可回读、Problem/Status 段数不变、标题数不变），
> 并把这次尝试作为**新实证**留档（`scope-evidence-13`），在 P2-115 下加了指针。
> **故 P2-115 的阈值上调已从「建议」变成「下一轮写入的前置条件」**。本轮另按第 292 轮规则压缩了
> **两条已结案条目**（P2-119 / P2-121）的 Status 行（**头必须是原 Status 的逐字前缀**，有断言守住），
> findings 最终 **149085 字节 / 1432 行**，余量 915 字节。
> **变更检测**：指纹 `1c6b8586…` → **`7aa6567b850e1423691938ca61eb451e1970bda05ef3ef3b209552a40ca6d264`**，
> 记录数仍 **2218** ⇒ **代码有变化**（Python SDK 的 docstring 改动被计入；`.md` 与 `docs/` 被指纹排除），
> **完整验收门控触发**。**新鲜度豁免适用**（本轮改的是 Python SDK 与 API 文档，后端源码零变化
> ⇒ 在跑的 37777 即当前后端）；为稳妥仍另起 **37790**（pid 23177）跑验收，37777 未重启。
> **回归测试**：`SERVER_URL=http://127.0.0.1:37790 bash scripts/regression-test.sh --skip-build` → **45/0/1**，**exit 0**。
> **EXTRACTION 验收**：`BACKEND_URL=http://127.0.0.1:37790 EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh`
> → **25/0/0**，**exit 0**。验收后已停 37790，含密钥的 `/tmp/p368-env.sh` 与键名清单**已逐个确认删除**
> （12 个环境变量提取时**取值全程未打印**）。
> **构建与重启**：Python SDK `python3.11 -m pytest tests/ -q` → **453 通过 / 0 失败**；
> 本轮**未改后端** ⇒ **无需重建 jar、无需重启 37777**。
> **连续 3 轮检查（修复后，定稿态连跑）**：**第 1 轮**（Python SDK 的承重断言逐条对回源码：默认参数、
> 下限、重试语义、跨家 `Matches Go SDK's doFireAndForget` 的每个字、23 条端点对活体零幻影、
> apiKey 措辞改前改后基线）；**第 2 轮**（README 方法表 vs 实现 26=26、必填参数四家逐条吻合、
> 双语表行集合一致、Go/JS 的 10 MiB 上限、空更新消息四家逐字相同、Python 实现与 README 表逐条吻合）；
> **第 3 轮**（API 文档逐端点请求体字段名与必填标注对活体 OpenAPI：除 P2-127 这条**已记录**的 spec 失实外，
> 无其它差异；无「活体有请求体、文档缺表格」的端点）。**三轮均未发现新问题、且期间未再修改**（只动了 /tmp 探针），
> 计数成立。
> **本轮自查出并修正的判据自身出错（8 处，全部是探针/脚本的 bug，未误改任何被测文件）**：
> ①端点核验的正则把**句末句号**吞进路径（13 条假幻影）；②未处理 **f-string 模板**（`/api/session/{quote(…)}/user`
> 被截断）⇒ 改为**占位符逐位**匹配活体；③普通字面量的正则把 f-string 里的内容**重复计了一次**；
> ④Go 的必填提取只认 `req.` 前缀，漏掉**裸参数**形态（`strings.TrimSpace(sessionID)`）；
> ⑤Go 的 `GetObservationsByIds` 是**元素级** id 校验，被误读成方法级字段；
> ⑥JS 的 `validateRequired` 形态与 Go/Java **各不相同**，三种都要抓；⑦Java 的 `requireNonBlank(expr,"x")`
> 字段名在**第二**个参数；⑧**值域注记**的参数（`limit must not be negative`）被误归入必填集合。
> 另有两处**本轮新发现的机制性问题**：⑨`index("\n- **Status**")` 返回的是**前一行末尾的换行**位置，
> `src[:j]` 不含它 ⇒ 替换时**换行被吃掉**、标题行与 Status 行被拼成一行（**已加结构断言**）；
> ⑩探针行的清理必须考虑**异步写入** —— tool-use 的处理是 `@Async`，清理之后其线程仍会建 session
> （本轮实测「首次清理后仍残留 2 行」⇒ 改为**清完等一拍再复查、必要时二次清理**）。
> **commit**：`patrol: round 368 - the api key nobody checks and the fields nobody reads`。
> **未解决问题**：①**P2-115 已成写入前置条件**（findings 余量 915 字节，阈值上调
> `MAX_LINES=2000` / `MAX_BYTES=200000` 需用户决策，否则下一轮写不下新条目）；
> ②P2-127（活体 OpenAPI 的 `prompt_text` required 三选一）；③P2-126（tool-use 那三个字段要不要真用起来的三选一）；
> ④P2-125（「省略 project 就跨项目返回」要不要在行为层收敛）；⑤P2-123（Go 重试范围/后端幂等三选一）；
> ⑥P2-124（限流器三选一）；⑦P2-90（Go README 测试数的形态）；⑧P2-122（服务端鉴权三选一）；
> ⑨P2-120（batch 端点改严三选一）；⑩P2-119（demo 监听地址三选一）；⑪P2-121（13 条断链的目标归属）；
> ⑫P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 /
> P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status 段 / 另四份零覆盖设计稿 ——
> **均已记录在 `docs/drafts/backend-review-findings.md`，不在本轮范围内，本轮不因此停下**。


> **第 369~370 轮的历史轮报告已逐字迁入** [`2026-10-20_health-check-history-16.md`](../archive/2026-10-20_health-check-history-16.md)（第 371 轮压缩迁移）。
> **第 371 轮：Backend（代码）+ 架构文档（文档，doc round 271）**：
> **健康预检**：`/api/health` → `{"service":"claude-mem-java","status":"ok"}`（HTTP **200**）；
> 37777 由 pid **53884** 在跑（**不是**本轮启动的）；37778~37781、37790 轮前均空闲；
> 轮初 `find backend/src/main -newer <jar>` 零命中 ⇒ 轮初在跑实例即当前后端（但**本轮改的是后端自身**，
> 故豁免路径**最终不适用**，见下）。
> **代码方向（Backend）新立 P2-130（已修，纯 `@Schema` 描述文本、零可执行行）**：
> **活体 OpenAPI 里 tool-use 的 `source` / `extractedData` / `prompt_number` 不说自己被丢弃** ——
> 而**同一个 DTO 文件**早已为「接受但不持久化」的字段确立了体例：`SessionStartRequest` 的
> `is_worktree`（`ApiRequests.java:33-34`）、`parent_project`（`:36`）、`platform_source`（`:38`）
> **都**注明「只写进应用日志、不落库、不影响任何行为」，**唯独 tool-use 这三个没跟上**；
> 其中 `prompt_number` 的描述还宣称「**Prompt number for ordering**」—— 而 tool-use 路径
> **根本不用它排序**。于是**活体 spec 里的三句话与代码的实际行为相反**。
> **活体实测（每条比较都带阳性对照）**：`POST /api/ingest/tool-use` 带 `prompt_number=42`、
> `source="p371src-tooluse"`、`extractedData={"p371":1}` ⇒ `mem_pending_messages.prompt_number`
> 为 **NULL**，`mem_observations.source` 与 `mem_observations.extracted_data` 命中 **0 行**；
> **阳性对照**（证明探针读得到、且字段本身存在）：`user-prompt` 带 `prompt_number=7` ⇒
> `mem_user_prompts.prompt_number` 落库 **7**；`observation` 带 `source="p371src-obs"`、
> `extractedData={"p371ctrl":2}`、`prompt_number=99` ⇒ `mem_observations.source` =
> `p371src-obs`、`extracted_data` = `{"p371ctrl": 2}`、`prompt_number` = **99**
> ⇒ **同名字段在别的端点确实生效**，所以 tool-use 上的 NULL 与 0 行是**字段级丢弃**，
> 不是「字段不存在」也不是「探针读不到」。
> **静态取证**：`handleToolUse`（`IngestionController.java:110-160`）只读
> `sessionId`/`toolName`/`toolInput`/`toolResponse`/`cwd`/`platformSource` 六个字段，
> 并以**硬编码 `null`** 作为 `processToolUseAsync(...)` 的 prompt number 位；
> `body.promptNumber()` 全后端只出现在 `:231`（user-prompt 处理器），
> `body.source()`/`body.extractedData()` 只出现在 `:345`/`:346`（observation 处理器）；
> `ToolUseRequest` 全后端只被 `IngestionController:109`（OpenAPI 声明）与 `:110`（处理器）引用。
> **已修**：三处描述按本文件既有体例改写，并各自点名「同名字段在 `POST /api/ingest/observation`
> （以及 `user-prompt` 的 `prompt_number`）**确实**落库」。**只碰 `@Schema` 的 description 字符串，
> 零可执行行、零行为变化**。
> **同轮系统性核对（活体 11 个带 `required` 的 schema）**：逐个「先带全部必填字段跑成功对照、
> 再缺该字段跑判定」—— **8 个真的强制**（`session/start` 与 `ingest/session-end` 的 `session_id`、
> `ingest/tool-use` 的 `tool_name`、`ingest/observation` 的 `content_session_id`、
> `memory/experiences` 与 `memory/icl-prompt` 的 `task`、`cursor/register` 的 `workspacePath`、
> `modes` 的 `mode`），**只有 2 个是装饰性的**：`prompt_text`（见 P2-127）与
> `content_session_id`/`project_path` —— 后者属**别名二选一**（服务端要
> `content_session_id` 或 `session_id` 之一、`project_path` 或 `cwd` 之一），
> 而 spec 只写了规范名、**描述已注明别名** ⇒ **spec 比服务端更严，不是失实**，故**不记缺陷**。
> **P2-127 选项①已实施**：删除 `UserPromptRequest.promptText` 上的
> `requiredMode = Schema.RequiredMode.REQUIRED`。**为什么这不需要用户决策**：
> `requiredMode` 只是 springdoc 的 schema 元数据、**不参与运行时校验** ⇒ **服务端行为一字未变**
> （实测：只带 `session_id` 仍 **200**，不带仍 **400**）；改动方向是**去掉**一个 required 标记，
> **只可能让 spec 更松**，**不可能让按 spec 生成的客户端出错**；改完后活体 spec 与服务端真实校验
> **一致**，失实陈述消失。**选项②（收紧）与③（维持现状）仍待决**，本轮只做选项①。
> **文档方向（架构文档，doc round 271）更正一处排他性断言**：第 262 轮的对照表里写着
> 「`wrapper.js` 只有四个：`session-start` / `tool-use` / `session-end` / `user-prompt`」——
> **这句话被 wrapper.js 自己的一套 `cursor` 子命令命名空间证伪**：它另外派发
> `cursor session-init` / `cursor context` / `cursor observation` / `cursor file-edit` /
> `cursor summarize`（见 `printCursorHelp()` 与 `proxy/CURSOR-INTEGRATION.md`）。
> 双语已把断言**限定为 Claude Code 钩子命令集**并点名 cursor 命名空间，同时点明
> `cursor context` **不是** `context-get`。
> **该文档其余断言逐条核实为真**：**Crash Recovery 框的 7 条** —— `tool_input_hash`
> 确实存在（V6 迁移 + 实体 + 仓储 + 活体 information_schema 四处一致）、
> `status` 五值与 `message_type` 两值与库的 **CHECK 约束逐字一致**、
> `session_db_id` 的 **CASCADE 外键**属实、「Find messages with status='pending'」属实、
> 「dedup already happened at enqueue time」属实（`AgentService:150` 的 `existsBySessionAndTool`）；
> 而「**runs on startup + periodic**」这一条**做了判定性实验**：手工插入一条 `status='pending'`
> 的行模拟崩溃遗留，启动新实例后日志显示 `PendingMessageProcessor initialized` 在 **16:11:16.676**、
> 「Found 1 pending messages to process」在 **16:11:19.373** ⇒ **首轮扫描确实发生在启动时**
> （`@Scheduled(fixedRate)` 的 `initialDelay` 默认 0），不是只有 5 分钟周期。
> 另核实为真的还有：Security/Authentication 段、Data Privacy（四类标签确为
> `.replace(…, '')` **彻底删除而非占位替换**、整段私密跳过）、Network Security 表、
> Scalability 表（限流器 **10/60s**、**唯一**调用点 `IngestionController:132`、键格式
> `tool-use:{contentSessionId}`、`ContextCacheService` 的 `claudemem.cache.refresh-interval-seconds`
> 默认 60）、Semantic Search 的线格式（四个 JSONB 列活体确为 **JSON 编码字符串**，
> `refined_from_ids` 确为 **TEXT 逗号分隔**）、Hook Event Flow 表、Data Flow 图
> （round 262 的四处更正仍成立）、以及「`proxy/` 下没有任何地方调用 `/api/context/*`」。
> **探针自身错 4 处、全部先识别再采信**：①探针误用 `/api/ingest/session-start` —— 该路径
> **确实是 404**，且是**文档已声明、回归脚本专门断言**的 404（`ARCHITECTURE.md:316`、
> `scripts/regression-test.sh:737-759`），真实路径是 `SessionController` 的 `/api/session/start`
> ⇒ **不是缺陷**；②第一次 `\d mem_pending_messages` 被 `head -20` **截断**，导致
> `tool_input_hash` 看起来不存在 —— 实际存在，是探针截断而非事实；③SQL 状态探针把
> `INSERT 0 1` 一并吞进 uuid 变量 → 「状态已变」的读数是探针假象，
> **决定性证据改用日志时间戳**；④`/api/observations` 的响应键是 `items` 而非
> `observations`/`content` ⇒ 首个活体探针读到 0 行。
> **是否检测到代码变化**：**是** —— 指纹 `7681d629…` → **`723475ad…`**，记录数仍 2220。
> **完整验收**：**已执行**（指纹变化 + **改的是后端自身** ⇒ 门控要求全量，且
> **新鲜度豁免不适用**）。先 `mvn -o package -DskipTests` 重建 jar（**EXIT=0**），并核验新措辞
> 确在 jar 内 —— 注意注解在**嵌套 record 类**里（`ApiRequests$ToolUseRequest.class`），
> 外层 `ApiRequests.class` 里搜不到；核验还发现 `ApiRequests$UserPromptRequest.class` 里
> **仍保留**「Prompt number for ordering」—— 那是 **user-prompt 自己的**字段，**该保留**，
> 说明改动没有误伤。另起实例 **37790**（pid 54759，detached）复验新鲜度：该实例的
> `UserPromptRequest.required` = `['session_id']`，而**仍在跑的老实例 37777 仍是**
> `['prompt_text', 'session_id']` ⇒ **确实只改了这一个字段**。
> **回归测试**：`SERVER_URL=http://127.0.0.1:37790 bash scripts/regression-test.sh --skip-build`
> → **45 通过 / 0 失败 / 1 跳过**，exit **0**。
> **EXTRACTION 验收**：`BACKEND_URL=http://127.0.0.1:37790 EXTRACTION_ENABLED=true
> bash scripts/phase3-acceptance-test.sh` → **25 通过 / 0 失败 / 0 跳过**，exit **0**。
> **服务启动动作与最终端口**：37777 由 pid 53884 在跑（**非本轮启动，未动**）；
> 另起验收实例 **37790**（pid 54759），验收后**已停**；37778~37781 全程空闲。
> 含密钥的临时文件 `/tmp/p371-env.sh` 已 `mavis-trash` 删除并确认；本轮其余临时文件逐个扫描
> 无密钥残留（两处命中经查是文档正文里的 `Bearer` 与 `ta**sk-tracke**ring`）。
> **构建与重启**：**改了后端 ⇒ 未豁免**：已重建 jar 并用新实例跑验收；
> **37777 本轮未重启**（它是既有服务，新代码由 37790 承载验收）。
> **Backend 发现与修复数量**：**1 修**（P2-130）+ **1 实施既有条目的选项**（P2-127 选项①）。
> **连续 3 轮检查**：**通过**（定稿态连跑，期间零改动）——
> 第 1 轮：jar 内新措辞三路交叉核验（`grep -a` / `unzip -p` / `javap` 常量池）；
> 第 2 轮：活体 spec 新旧实例对拍 + required 强制性 11 项系统核对；
> 第 3 轮：Crash Recovery 的 7 条断言逐条实测（含启动扫描的判定性实验）。
> **commit**：`patrol: round 371 - the spec that described a field the tool-use path throws away`。
> **未解决问题**：①**P2-115 仍是写入前置条件**（findings 余量仅 **40 字节**！阈值上调
> `MAX_LINES=2000` / `MAX_BYTES=200000` 需用户决策；本轮已把三种零损失压缩手法用到只剩一种）；
> ②**P2-127 选项②（收紧 `prompt_text`）与③（维持现状）**待决；
> ③**P2-126**（tool-use 那三个字段要不要真用起来）待决 —— 本轮只把**它们被丢弃这件事写进了 spec**，
> **没有让它们开始生效**；④P2-129（Java 的 404/405 体三选一）；⑤P2-128（Java 下限对齐／
> Go+Java README 补下限／补下限回归测试）；⑥P2-125、⑦P2-123、⑧P2-124、⑨P2-90、⑩P2-122、
> ⑪P2-120、⑫P2-119、⑬P2-121；⑭P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 /
> P2-100 / P1-1 / P2-79+P2-80 / P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status /
> 另三份零覆盖设计稿 —— **均已记录在 `docs/drafts/backend-review-findings.md`，不在本轮范围内，
> 本轮不因此停下**。


> **第 372 轮：Java SDK（代码）+ 运维/用户指南（文档，doc round 272）**：
> **健康预检**：`/api/health` → **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的）；
> 37778~37781、37790 轮前均空闲。
> **代码方向（Java SDK）新立 P2-131（已修，README 双语 + 注释，零可执行行）**：
> **SDK 的 README 教用户走一条后端会丢字段的路。** README 的「V14 Features」有两节
> —— 「Source Attribution」「Structured Data Extraction」—— 例子都用 `recordObservation` 设
> `source` / `extractedData`；而 `recordObservation` 发往 `POST /api/ingest/tool-use`，
> **正是后端把这两个字段连同 `prompt_number` 一起丢弃的那个端点**。同一份 README 的
> 「Backend API Alignment」表还写着 `recordObservation()` | `POST /api/ingest/tool-use` |
> **✅ source, extractedData**。于是「来源归属」与「结构化数据提取」两个**标题级承诺**，
> 在 SDK 唯一的创建路径上**无一兑现**；SDK 的 `ObservationRequest` 注释（「V14: source attribution」
> 「V14: structured key-value data」）同样没提这件事。
> **活体实测（每条比较都带阳性对照）**：`tool-use` 带 `prompt_number=42`、`source="p371src-tooluse"`、
> `extractedData={"p371":1}` ⇒ `mem_pending_messages.prompt_number` 为 **NULL**，
> `mem_observations.source` 与 `mem_observations.extracted_data` 命中 **0 行**；
> **阳性对照**：`user-prompt` 带 `prompt_number=7` ⇒ 落库 **7**；`observation` 带
> `source="p371src-obs"`、`extractedData={"p371ctrl":2}`、`prompt_number=99` ⇒ 三者**全部落库**
> ⇒ 是**字段级丢弃**，不是字段不存在、也不是探针读不到。
> **「能兑现的那条路」也实测存在**：`PATCH /api/memory/observations/{id}` 带
> `{"source":"p371-patch-src","extractedData":{"p371patch":1}}` ⇒ 200，数据库直读得到
> `source=p371-patch-src`、`extracted_data={"p371patch": 1}` ⇒ **PATCH 确实落库**，
> 而那正是 Java SDK `updateObservation` 走的路（`ObservationUpdate` 确实带这两个字段）；
> 另核实 `cortex-mem-spring-integration` 里**零命中** `/api/ingest/observation`
> ⇒ 在本客户端里 PATCH 是唯一能落库这两个字段的路。
> **已修**：两语 README 的那两节改用 `updateObservation`（PATCH）并给出取 id 的写法
> （`listObservations(...)` → `.items().get(0).id()`），各加一段实测说明 tool-use 路径会丢弃这三个字段；
> 能力表的 `recordObservation` 行由 **✅ source, extractedData** 改为 **⚠️ …接受但不落库**，
> 表后加一段 ⚠️ 注（指出 `updateObservation` 才是落库的那条路）；`ObservationRequest` 的两处行内注释
> 与类 Javadoc 补上「tool-use 路径会丢弃、请用 updateObservation 落库」。
> **只改 README 与注释，零可执行行、零行为变化**；Java SDK 测试 **196 通过 / 0 失败**。
> **跨家结转**：Go 的 `dto.ObservationRequest`、Python 的 `record_observation`、JS 的
> `ObservationRequest` **都发这三个字段**、且有同型注释 ⇒ 本轮只修 Java 侧，其余留待各自轮次。
> **Java SDK 其余部分核实为真**：`CortexToolAspect` 的 Javadoc 称
> 「the four cases in `CortexToolAspectTest`」⇒ 实测**恰 4** 个 `@Test`，且用例名与
> 「capture-when-active / no-capture-when-inactive / truncation in both directions」**逐条吻合**。
> **文档方向（运维/用户指南，doc round 272）新立 P2-132（已修，TESTING.md 双语 + changelog 补注）**：
> **一张标题写着「Test」的表，三个变量名不副实。** 表里把**后端**的 `DB_USERNAME` / `DB_PASSWORD`
> 写成了测试脚本的环境变量（它们是 `application.yml:80-81` 读的**后端**变量；脚本读的是
> `DB_USER` / `DB_PASS`，`scripts/README.md:758` 自己就写着「Note: the variable is DB_PASS, not
> DB_PASSWORD」并给出 `DB_NAME=claude_mem_dev`）；`DB_NAME` 的默认值写成了 docker-compose 的
> `claude_mem`（而 `regression-test.sh:42` 的默认是 **`claude_mem_dev`**，两个 Docker 套件另有
> `claude_mem_compose_test` / `claude_mem_test`）；并且**漏掉了 `DB_HOST`** ——
> 而 `regression-test.sh:41` 至今仍在读它。文档自己的 changelog 记着 **2026-05-04** 那次
> 「修正 `DB_USER`→`DB_USERNAME`、`DB_PASS`→`DB_PASSWORD`、`DB_NAME` 默认值
> `claude_mem_dev`→`claude_mem`」——**对后端正确，对测试脚本全错**，且把仍在被读的 `DB_HOST`
> 当作「不存在的」删掉了。**已修**：第 6 节的表改成四列（新增 **Read by**），逐行标明
> **由谁读取**与真实默认值，补回 `DB_HOST` / `DB_USER` / `DB_PASS` 三行，并加一段导语点明
> 「导出 `DB_USERNAME` 不会让测试脚本用上它」；**changelog 以新增行补充** 2026-05-04 那一条
> （**不改写它**——它当时对后端是对的，只是没注意到这张表讲的是测试变量）。双语同步更新。
> **同方向新立 P2-133（已修，DEPLOYMENT.md 双语）**：**默认值只在 compose 下成立。**
> 第 5.1 节把 `SPRING_PROFILES_ACTIVE` 的默认值写成 `prd`，而 `prd` **只是 `docker-compose.yml:52`
> 的默认**；原生 JAR 运行不设该变量时**没有任何激活的 profile**（Spring 打印
> `No active profile set, falling back to 1 default profile: "default"`），此时第 5.3 / 5.4
> 两张表列出的「默认」**全部不成立** —— 真正生效的是 `SpringAiConfig` 的 `@Value` 兜底值。
> **实测（无 profile 启动 JAR，只给两个 key）**：日志
> `Creating OpenAI-compatible ChatModel: baseUrl=https://api.deepseek.com, model=deepseek-chat`、
> `Creating EmbeddingModel: baseUrl=https://api.siliconflow.cn, model=BAAI/bge-m3,
> dimensions=1024` ⇒ 生效值是 **dev 那一列**。**更尖锐的是：这种状态下那几个「可调」变量本身也失效** ——
> **正反对照各跑一次**：① 只设短别名 `OPENAI_BASE_URL=https://alias-only.invalid` ⇒ 日志仍是
> `https://api.deepseek.com` ⇒ **短别名失效**（别名只定义在 prd / dev 的 yml 里）；
> ② 设 `SPRING_AI_OPENAI_BASE_URL=https://full-name.invalid` ⇒ 日志变成 `https://full-name.invalid`
> ⇒ **完整名的 base URL 生效**，但同批设的 `SPRING_AI_OPENAI_CHAT_MODEL=full-name-model`
> ⇒ 仍是 `deepseek-chat` ⇒ **模型名被忽略**；③ 设
> `SPRING_AI_OPENAI_EMBEDDING_BASE_URL=https://emb-full.invalid` ⇒ 生效，但同批的
> `SPRING_AI_OPENAI_EMBEDDING_MODEL` 与 `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=768`
> ⇒ 仍是 `BAAI/bge-m3` 与 `1024` ⇒ **被忽略**。
> **根因在属性名上**：`SpringAiConfig` 读模型与维度用的是 `spring.ai.openai.chat.options.model`
> 与 `spring.ai.openai.embedding.options.dimensions`，而 Spring 的宽松绑定把
> `SPRING_AI_OPENAI_CHAT_MODEL` 映到 `spring.ai.openai.chat.model`
> —— **各差一段**，值到不了字段；两个 base URL 读的是 `spring.ai.openai.base-url` /
> `spring.ai.openai.embedding.base-url`，**恰好对得上**，所以它们仍然生效。
> 在 prd / dev 下不咬人，因为那两个 yml **自己**把变量名写了出来 —— **映射变量名的是 profile，不是代码**。
> **已修**：第 5.1 节该行注明「`prd` **仅 Docker Compose 下**」并指向第 5.4 节；第 5.4 节新增
> 一段与一张表，写明**无 profile** 时实际生效的值与**哪些变量会被静默忽略**及其根因，
> 结论是「**请显式设置 `SPRING_PROFILES_ACTIVE`**」。双语同步更新，**后端代码一行未动**。
> **该方向其余断言逐条核实为真**：`run-all-e2e.sh` 的 **10 套**与文档表格逐条对得上（1~5 在 §1、
> 6~10 在 §3；Docker 两套与 `test-llm-provider.sh` 确实被排除）、**5/10** 的跳过条件与
> `is_streamable_mcp` 逐字一致、`phase3-acceptance-test.sh` 的 **15 个**测试函数、
> `.gitmodules` 的 webui 子模块、`application-prd.yml` / `application-dev.yml` 的五组默认值
> 与文档表格**逐项吻合**（含「短别名只定义在 prd、dev 识别 `OPENAI_*` 但没有 `EMBEDDING_*` 别名」）、
> compose 的 `DB_NAME`/`DB_USERNAME`/`POSTGRES_PORT` 默认值与第 5.2 节一致。
> **探针自身错 3 处、全部先识别再采信**：①读回 observation 时用了 `GET /api/memory/observations/{id}`
> ⇒ **405**（该端点只接 PATCH/DELETE）⇒ 改用数据库直读作权威判据；
> ②环境变量普查用 `\$VAR` 模式，把两个 API key 报成「0 个脚本读取」——脚本用的是
> `\${VAR:-default}` 形式，模式没匹配上 ⇒ 改用按变量名 grep 重做；
> ③findings 写入脚本把**字节数**当成了「原文」⇒ `AttributeError`（**文件未写**，断言拦住了）
> ⇒ 改为保留原文串再比对。
> **容量**：本轮写入前 findings 只剩 **40 字节**，先做了**五处零信息损失替换**才腾出空间 ——
> ①措辞统一 **3 条规则**（35 + 24 + 1 处）；②轮次标记 **1 条**（107 处）；
> ③归档指针**显示名**去 `.md` **1 条**（98 处，第 293 轮体例：**目标 URL 一字不动**，且缩短后仍能在
> target 里**唯一定位，0 例外**）。逐条实测 210 + 288 + 6 + 428 + 294 = 共省 **1226 字节**；
> **指针 target、条目标题与结构性标签
> （`- **Scope / Evidence**:` 等）逐项对拍未变**。写入后余 **231 字节** ——
> **P2-115 的阈值上调仍是下一轮写入的前置条件**。
> **是否检测到代码变化**：**是** —— 指纹 `723475ad…` → **`ddc17125…`**，记录数仍 2220。
> **完整验收**：**已执行**（指纹变化 ⇒ 门控要求全量）。本轮**未改后端**
> （`find backend/src/main -newer <jar>` 零命中 ⇒ 新鲜度豁免适用），仍另起新实例
> **37790** 跑验收：回归 **45 通过 / 0 失败 / 1 跳过** exit **0**、
> EXTRACTION **25 通过 / 0 失败 / 0 跳过** exit **0**；验收后**已停 37790**，
> 含密钥的临时文件已 `mavis-trash` 删除并确认。
> **服务启动动作与最终端口**：37777 由 pid 53884 在跑（**非本轮启动，未动**）；37790 为本轮验收实例
> （已停）；37778~37781 全程空闲。
> **构建与重启**：**未重建 jar、未重启 37777**（本轮改的是 Java SDK 的注释与文档，后端未动）。
> **Java SDK 发现与修复数量**：**1 修**（P2-131）。
> **文档方向发现与修复数量**：**2 修**（P2-132 / P2-133）。
> **连续 3 轮检查**：**通过**（定稿态连跑，期间零改动）——
> 第 1 轮：Java SDK 全量测试 196/0 + `mvn -o test` exit 0（改注释前后各一次）；
> 第 2 轮：活体三段实测（tool-use 丢弃 / user-prompt 与 observation 生效 / PATCH 落库）+ 库内直读复核；
> 第 3 轮：profile 三态的正反对照（短别名失效 / 完整名 base URL 生效 / 模型名与维度被忽略）。
> **commit**：`patrol: round 372 - the SDK that taught users to set a field the server drops`。
> **未解决问题**：①**P2-115 仍是写入前置条件**（findings 余量 **231 字节**，本轮已把压缩杠杆用到
> 只剩「粗体标记」这类观感性改动；阈值上调 `MAX_LINES=2000` / `MAX_BYTES=200000` 需用户决策，
> 只需同步 `docs/drafts/cron-combined-task.md:14`）；
> ②**P2-131 的跨家结转**：Go / Python / JS 三家同样发这三个字段且有同型表述，**本轮未动**；
> ③**P2-126**（tool-use 那三个字段要不要真用起来）待决 —— 本轮只让**文档不再教错**，
> **没有让它们开始生效**；④P2-127 选项②（收紧 `prompt_text`）与③（维持现状）；
> ⑤P2-129（Java 的 404/405 体三选一）；⑥P2-128（Java 下限对齐／Go+Java README 补下限／
> 补下限回归测试）；⑦P2-125、⑧P2-123、⑨P2-124、⑩P2-90、⑪P2-122、⑫P2-120、⑬P2-119、⑭P2-121；
> ⑮P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 /
> P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status / 另三份零覆盖设计稿 ——
> **均已记录在 `docs/drafts/backend-review-findings.md`，不在本轮范围内，本轮不因此停下**。


> **第 373 轮：Go SDK（代码）+ API 文档（文档，doc round 273）**：
> **健康预检**：`/api/health` → **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的）；
> 37778~37781、37790 轮前均空闲。
> **代码方向（Go SDK）新立 P2-134（已修，Go SDK + 3 测试）**：
> **两个错误分类函数对「裸哨兵」这一形态整体失效。** Go 没有异常类，错误只有两种形态 ——
> **哨兵错误**（`ErrBadRequest` 等）与 `*APIError`（带 `StatusCode`）。`error.go` 里
> **15** 个 `Is*(err error) bool` 中有 **13** 个是状态分类函数（另两个是 `IsValidationError`
> 与 `IsRetryable`），这 13 个里有 **11** 个带 `errors.Is(err, ErrXxx)` 回退 ——
> **唯独 `IsClientError` 与 `IsServerError` 没有**，它们只认 `*APIError` 形态。
> **后果实测**：**7 个 4xx 哨兵**（`ErrBadRequest`/`ErrNotFound`/`ErrConflict`/
> `ErrUnauthorized`/`ErrForbidden`/`ErrUnprocessable`/`ErrRateLimited`）在 `IsClientError` 下
> **全判 false**；**3 个 5xx 哨兵**（`ErrBadGateway`/`ErrServiceUnavailable`/`ErrGatewayTimeout`）
> 在 `IsServerError` 下**全判 false**；连 `IsInternal` **注释里写明的**
> 「Is a 5xx server error (500, 502, 503, 504, etc)」也只认 `ErrInternal` 一个
> （`IsInternal(ErrBadGateway)` = false）⇒ **注释失实**。
> **每条比较都带阳性对照**（活体 Go 程序，`replace` 指向本仓库 SDK）：
> ①**同一哨兵喂给「自己的」判定函数** ⇒ `IsBadRequest(ErrBadRequest)` = **true**、
> `IsBadGateway(ErrBadGateway)` = **true** ⇒ 探针看得见结果；②**换成 `*APIError` 形态** ⇒
> `IsClientError(&APIError{404})` = true、`IsServerError(&APIError{503})` = true、
> `IsInternal(&APIError{502})` = true ⇒ **形态对就判对**。⇒ 失败只出在这两个函数的回退缺失上。
> **跨家对照**：Python 的 `is_client_error` / JS 的 `isClientError` **docstring 明写**
> 「(Go: IsClientError, JS: isClientError)」—— 三家被**显式文档化为并行 API**，
> 而 Python/JS 用「带状态码的错误对象」实现、在自己的形态上判对，**只有 Go 的哨兵形态判不出来**。
> **已修**：`IsClientError` 补 7 个 4xx 哨兵回退，`IsInternal` 补 3 个 5xx 哨兵回退
> （`IsServerError` 是 `IsInternal` 的别名，随之修正）。这是**纯加宽** ——
> 只会 false→true，**不存在任何 true→false 的翻转**；且经检索确认这三个函数在 SDK 内部
> **零调用点**（Python/JS 也都只是对外导出），重试判定走的是独立的 `IsRetryable` ⇒
> **不影响任何内部行为**。**补测** 3 个测试函数（含**阴性对照**：普通 error 两边都不该匹配）；
> **「能失败」已证**：把测试拷到**修复前**的 `error.go` 上跑，报 **13 处**失败
> （7 个 4xx + 3 个 5xx 各两条断言），阴性对照前后**都通过**。
> **最强佐证（本轮写报告时才发现，此前一直只当成「读代码看出来的」）**：仓库**早就有一套
> `Test*_SentinelFallback` 测试**，体例统一为「本函数对自己的哨兵返回 true、对另一个哨兵返回
> false」；HEAD 上**已有 12 条**（`IsNotFound`/`IsBadRequest`/`IsUnauthorized`/`IsForbidden`/
> `IsConflict`/`IsRateLimited`/`IsUnprocessable`/`IsInternal`/`IsServiceUnavailable`/`IsBadGateway`/
> `IsGatewayTimeout`/`IsRetryable`），而**缺的恰好就是 `IsClientError` 与 `IsServerError` 这两条**
> ⇒ **测试套件的缺口与实现缺陷精确重合**：12 个分类器被逐个钉住了哨兵形态，唯独两个真坏的没人钉。
> 本轮补的两条是**把这个既有套件补齐**，而非另起体例；既有的 `TestIsInternal_SentinelFallback`
> 断言 `IsInternal(ErrBadRequest)` 为 false，本轮修改**不与它冲突**。
> **P2-131 的跨家结转在 Go 侧已修（零可执行行）**：`dto/observation.go` 的
> `Source`/`ExtractedData`/`PromptNumber` 三处注释改写为「tool-use 路径会丢弃这三个字段、
> 请用 `UpdateObservation`（PATCH /api/memory/observations/{id}）落库」，
> 与第 372 轮 Java 侧**同一事实、同一措辞**；Go README 本就**未**提及这三个字段，无需改文档。
> **Go SDK README 双语的测试数同步 369 → 372**：本轮新增 3 个测试函数，实测（口径 = `=== RUN`
> 计数，与 README 自己给的逐模块命令一致）根模块 **303 → 306**（core **236 → 239** + dto 67）、
> 适配器与示例模块 66 不变 ⇒ 总数 **369 → 372**；改了 **7 处行、14 个数字**，
> 算术 `239 + 67 + 8 + 13 + 12 + 33 = 372` 自洽。
> **Go SDK 其余部分核实为真（系统性对拍，非抽查）**：①**26 组方法/路径**与活体 OpenAPI
> **逐条对上**，无幽灵端点、无错方法（`path` 变量三处由**赋值点回填**后才对拍，不靠手判）；
> ②**7 个带请求体的 DTO**，SDK 发出的字段**全部**在活体 schema 的 properties 里，
> **零个多余字段**；③`SearchRequest.Source` 声称是 V14 来源过滤器 ⇒ **实测为真**
> （`source=llm_extraction` 返回的 20 条全是 `llm_extraction`、`strategy` 由 `recent` 变 `filter`，
> **阴性对照**：不存在的值返回 **0 条**）；④`OrderBy` 声称支持 `created_at_epoch`/`createdAtEpoch`
> ⇒ 在 `recent` 策略下**无法被否证**（默认本就降序，四个取值结果逐条相同）；
> ⑤`ObservationsRequest` 少活体的 `platformSource`/`contentSessionId` ⇒ **遗漏非失实**，
> 且双语一致，不记；⑥响应 DTO 少活体的 **10** 个键 ⇒ 属 **P2-58 已有的同型**（四家同缺），
> 不另立条。
> **文档方向（API 文档，doc round 273）新立 P2-135（记录不修）**：
> **API 文档两版都是对的，错的是活体 OpenAPI 自己 —— 而且是「少声明」这个方向。**
> 本轮的角度是新的：**把 API 文档的「必填」列、错误码表、查询参数表逐表对拍活体 OpenAPI**。
> **两处少声明**：①`POST /api/session/start` 的活体 `required` 只有 `['session_id']`，
> 而运行时**拒绝**任何缺 `project_path` 的请求（错误原文
> `Missing required field: project_path (or cwd)`，`cwd` 是别名，实测 `session_id`+`cwd` → **200**）；
> 源码上 `SessionStartRequest.projectPath` **没有** `requiredMode = REQUIRED` 注解，
> 而同一文件的 `ObservationCreateRequest` 的两个字段**都有**。②`GET /api/memory/quality-distribution`
> 的活体 `responses` 只有 200/500，而实测 `project` 为空或全空白时**确实返回 400**
> （`{"error": "project is required"}`），**API 文档 EN/ZH 两版都写了这条 400**。
> **实测记录**（**新鲜实例**跑的 —— 常驻的 37777 是**旧 jar**）：空体 → 400 缺 `session_id`；
> `{session_id}` → 400 缺 `project_path`；`{user_id}` → 400 缺 `session_id`；
> `{project_path}` → 400 缺 `session_id`；`{session_id,user_id}` → 400 缺 `project_path`
> ⇒ **两个字段都被运行时强制**；**阳性对照**：带齐 `session_id` + `project_path` → **200**、
> 带 `session_id` + `cwd` → **200**。`quality-distribution` 侧：不带 `project` → 400（Spring 默认体）、
> `?project=` 与 `?project=%20` → 400 `project is required`、不存在的项目 → **200**（零命中但仍 200）
> ⇒ 该 400 只在**参数缺失/空白**时出现。
> **文档侧核对结论（本方向零改动）**：必填列逐端点对拍，两版各 **6** 个带请求体表的端点，
> 除 `session/start` 外**全部一致**；错误码表两版各 **11** 个端点可对拍，**唯一差异**就是
> `quality-distribution` 的 400（同为**文档有、spec 无**）；查询参数表两版各 11 / 15 个端点，
> **零处必填性不符**；端点覆盖两版各 **65** 个章节、活体 **71** 个操作，**覆盖集合相同**
> ⇒ **API 文档 EN/ZH 两版都不需要改，要改的是后端注解**。
> **为什么不实施**：给 `projectPath` 加 `requiredMode = REQUIRED`、给 quality-distribution 加一条
> `@ApiResponse(responseCode = "400")`，都会**改变对外发布的 spec**（前者让 spec 变严 ——
> 而 `project_path` 与 `cwd` 是别名，只给 `cwd` 的请求运行时是 200、spec 就会说缺必填，
> 这与同一 DTO 文件里 `ObservationCreateRequest` 的既有做法一致；后者让 spec 多一个响应码），
> 按既定纪律（对外契约变更不单方面实施）**记录不修**，**后端代码一行未动**。
> **探针自身错 6 处、全部先识别再采信**：①**探测的是 37777 那个旧 jar**（第 371 轮改过
> OpenAPI 注解，老实例仍带 `prompt_text` 的 REQUIRED）⇒ 误报「user-prompt 必填不一致」，
> 改用**新鲜实例 37790** 后一致；②请求体表的解析器把**别的端点的表**算到了 `session/start` 头上
> （ZH 用「请求字段」标签而非「请求体」，且 4000 字符窗口跨了章节）⇒ 改为**按端点分节**后一致；
> ③查询参数表只认 `✅/❌`，而另一些表用 `Yes/No/Required/必填/(必填)` ⇒ 误报 `timeline`、
> `by-file` 的必填性不符；④ZH 的 `context/preview` 表第三列是**默认值**不是必填
> （`(必填)`/`true`）⇒ 误报 `includeObservations` 必填性不符；⑤EN 的端点正则只认代码块式，
> 漏掉 `#### GET \`/path\`` 混合体例（EN 文档第 14 行自己写了这个体例）⇒ 误报「仅 ZH 有」
> 5 个 context 端点，实为**两版体例不同、覆盖相同**；⑥压缩脚本第一次匹配时，
> 文件里**跨行未闭合的粗体**把 `- **Problem**:` 的收尾 `**` 与更远的 `**` 配成一对，
> 守卫失效 ⇒ **断言拦下、文件未写** ⇒ 改为**先掩码结构性标签、压缩后还原**。
> **容量**：本轮写入前 findings 只剩 **231 字节**，先做**两处零信息损失压缩**才腾出空间 ——
> C1「状态词」粗体去标记 **33** 处、C2「ASCII 码」粗体去标记 **70** 处，
> **只删 `**` 标记、一个字未改**；硬证明是**去掉全文所有 `*` 后字符流与压缩前逐字相同**，
> 且**结构性标签、条目标题、归档指针的 target 与显示名逐项对拍未变**。共省 **412 字节**，
> 写入后余 **21 字节** —— **P2-115 的阈值上调仍是下一轮写入的前置条件**。
> **是否检测到代码变化**：**是** —— 指纹 `ddc17125…` → **`7548a6fd…`**，记录数仍 2220
> （本轮改的都是既有文件，未增删文件）。
> **完整验收**：**已执行**（指纹变化 ⇒ 门控要求全量）。本轮**未改后端**
> （`find backend/src/main -newer <jar>` 零命中 ⇒ 新鲜度豁免适用），仍另起新实例
> **37790** 跑验收：回归 **45 通过 / 0 失败 / 1 跳过** exit **0**、
> EXTRACTION **25 通过 / 0 失败 / 0 跳过** exit **0**；验收后**已停 37790**，
> 含密钥的临时文件已 `mavis-trash` 删除并确认。
> **服务启动动作与最终端口**：37777 由 pid 53884 在跑（**非本轮启动，未动**）；37790 为本轮验收实例
> （已停）；37778~37781 全程空闲。
> **构建与重启**：**未重建 jar、未重启 37777**（本轮改的是 Go SDK 与文档，后端未动）。
> **Go SDK 发现与修复数量**：**2 修**（P2-134 新立；P2-131 跨家结转）。
> **文档方向发现与修复数量**：**1 记不修**（P2-135）；**API 文档本身零改动**（核对后确认两版都正确）。
> **连续 3 轮检查**：**通过**（定稿态连跑，期间零改动）——
> 第 1 轮：Go SDK 全量测试（`go vet` 干净、9 个 module 逐个 `=== RUN` 计数 372）+ 活体探针复跑；
> 第 2 轮：新测试在**修复前**的 `error.go` 上失败 13 处（**「能失败」得证**）、修复后全绿；
> 第 3 轮：文档方向四条对拍（必填 / 错误码 / 查询参数 / 端点覆盖）在**新鲜实例**上复跑，
> 并逐条核对 EN/ZH 两版结果一致。
> **commit**：`patrol: round 373 - the two classifiers that only understood one shape of error`。
> **未解决问题**：①**P2-115 仍是写入前置条件**（findings 余量 **21 字节**，本轮已把压缩杠杆用到
> 只剩「再删一处非结构性粗体」这类观感性改动；阈值上调 `MAX_LINES=2000` / `MAX_BYTES=200000`
> 需用户决策，只需同步 `docs/drafts/cron-combined-task.md:14`）；
> ②**P2-135 的两种修法**（spec 变严 vs 运行时放宽）需项目决定；
> ③**P2-131 的跨家结转余下 Python / JS 两家**（本轮未动）；④P2-127 选项②（收紧 `prompt_text`）
> 与③（维持现状）；⑤P2-126；⑥P2-129；⑦P2-128；⑧P2-125；⑨P2-123；⑩P2-124；⑪P2-90；
> ⑫P2-122；⑬P2-120；⑭P2-119；⑮P2-121；
> ⑯P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 /
> P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status / 另三份零覆盖设计稿 ——
> **均已记录在 `docs/drafts/backend-review-findings.md`，不在本轮范围内，本轮不因此停下**。


> **第 374 轮：Python SDK（代码）+ SDK README（文档，doc round 274）**：
> **健康预检**：`/api/health` → **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的）；
> 37778~37781、37790、37791 轮前均空闲；HEAD 干净；指纹 `7548a6fd…`。
> **代码方向（Python SDK）：P2-131 的跨家结转在 Python 侧已修（零可执行行）**：
> `cortex_mem/client.py` 的 `record_observation` docstring 写明后端 tool-use 处理器**只读**
> `session_id`/`cwd`/`tool_name`/`tool_input`/`tool_response`/`platform_source`，
> `source`/`extracted_data`/`prompt_number` **被接受后丢弃**，并指向真正落库的
> `update_observation`（`PATCH /api/memory/observations/{id}`）——措辞与第 372 轮 Java 侧、
> 第 373 轮 Go 侧**同一事实、同一说法**。SDK README EN/ZH 的 Wire Format 段同注。
> **JS 侧仍待结转**。
> **代码方向（Python SDK）：新立 P2-136（记录不修）**：
> **同一个根因在第二个表面上露了出来 —— 四家 demo 的 create 端点。**
> 四家 demo 都有 create 端点，接收 `source` 与 `extractedData`（Python/Go/JS 还收
> `prompt_number`），交给 SDK 的 `record_observation`/`RecordObservation`/`recordObservation`
> —— 那条路发往 `POST /api/ingest/tool-use`，**后端在这条路径上只读六个字段**，这三个字段
> **被接受后丢弃**，而 demo 一律返回 `{{"status": "recorded"}}`。
> Python demo（`examples/http-server/app.py`）**为 `extractedData` 的类型校验写了 12 行注释**
> （区分「显式 null」与「不是对象」），说明作者**本就是打算把它入库的**；
> Go demo `main.go:823`、JS demo `app.ts:327`、Java demo `ObservationsController.java:185`
> 同型。
> **活体实测带两条阳性对照**（新鲜实例 37790，jar 与 HEAD 同源）：
> **被测路径** tool-use 的 `source` 命中 **0 行**、`prompt_number=42` 命中 **0 行**；
> **阳性对照 B**（同一 SDK 的 `update_observation` → PATCH）`source` 命中 **1 行**；
> **阳性对照 C**（`POST /api/ingest/observation` 直接创建）`source` 命中 **1 行**、
> 同会话 `prompt_number=42` 命中 **1 行** ⇒ **字段级丢弃**。
> **探针自身的诚实说明（这一条必须记，否则读数会被误读）**：被测会话里
> **总行数也是 0** —— 本机 LLM chat 端点密钥失效（401），异步抽取根本没产出 observation，
> **所以单看行数分不清「字段被丢」与「压根没成行」**；真正定案的是**静态签名**：
> 把 `handleToolUse` 方法体整段抽出后 grep 那三个字段**零命中**，
> 而 `processToolUseAsync` 的签名里**根本没有 source/extractedData 两个参数**、
> prompt number 位被**硬编码 `null`** ⇒ 字段在**类型层面就无处可传**，与 LLM 是否可用无关。
> 两条阳性对照则证明「列存在、查询可用、写路径可用」。
> **四家 SDK 都没有暴露 `POST /api/ingest/observation`**（四家源码零命中）⇒ demo 想真落库
> **没有 SDK 方法可用**；三条修法（① SDK 新增公开 API ② demo 改走直接创建路径
> ③ demo 明确拒收这两个字段）**全是对外契约变更** ⇒ 按纪律**记录不修**。
> **本轮已做的部分（注释级，零可执行行、demo 契约未动）**：Python 侧把事实写进四处 ——
> SDK 客户端 `record_observation` docstring、demo create 端点注释、demo README 的端点表与
> 表下说明、SDK README EN/ZH 的 Wire Format 段。Go / JS / Java 的 demo 侧属**跨家结转**。
> **Python SDK 其余部分核实为真（系统性核对，非抽样）**：
> **25 个方法 / 23 个端点**全部对得上真实后端路由（端点表逐条与后端控制器对拍）；
> **15 个错误判定函数**全部基于 `isinstance(err, APIError)`、**无哨兵概念**
> ⇒ 第 373 轮 Go 的 P2-134 **在 Python 无同型**；
> **同族测试缺口普查**（第 373 轮立的角度）在此**零缺口**：15 个判定函数**逐个有测试**，
> 对照 Go 的 13 个分类器缺 2 个 ⇒ **同一把尺子量两家，结论相反**。
> **文档方向（SDK README，doc round 274）**：
> README 自身的数字按**它自己的口径**实测 —— **453 个测试**，拆为
> **226 client + 140 DTO + 87 demo**，**逐文件核实**（226 / 140 / 87，三数相加即 453）；
> 跨家断言全部成立 —— Java 重试 **25 个方法中的 10 个**（按十个调用点逐一核实：
> 三个 capture + 两个抽取读 + 三个变更 + `triggerRefinement` + `triggerExtraction`）、
> Go 的 `MaxResponseBytes = 10 << 20`、JS 默认超时 `30_000` **毫秒**（与 README 的单位提示相符）；
> `refined_from_ids` 是 `TEXT`、另外四列是 `jsonb`（读 `information_schema`）。
> README 自称「Verified live」的三条断言**重新实测全部成立**：
> search 的 `?project=` 返 **200 空集**而省略参数返 **400**；observations 的字面量
> `?project=` 返 **0 条**而省略参数返 **100 条、跨 16 个项目**；
> ICL 端点对省略 / 空串 / 不存在的项目一律 **200 []**，对真实项目返 **2 条经验**。
> demo 自身的 README 也审了：**26 条路由对 26 行端点表**，唯一差异是 Flask 的 `<obs_id>`
> 与 README 的 `{id}` 写法。
> **探针自身出错 1 处，先识别再采信**：第一次的 ICL 三态探针在 shell 里拼 `"project":` 时
> 没给值，四个 400 **全是探针自己的非法 JSON**，不是端点的行为 —— 改用 Python 构造请求体
> 后 README 的断言才被真正验证。**上一轮那个「旧 jar」的坑本轮没有重蹈**：
> 活体对拍全部打在**新鲜实例 37790** 上。
> **容量**：写入前 findings 只剩 **21 字节**，本轮做了**三处零信息损失压缩** ——
> C3a「CJK 短词组·频次 ≥ 2」粗体去标记 **86** 处、C3b「CJK ≤ 4 字且唯一」粗体去标记
> **127** 处（**只删 `**` 标记、一个字未改**）、C4「归档指针显示名去 `.md`」**5** 处
> （**链接 target 一字不动**，第 293 轮既有体例），共省 **867** 字节；
> 硬证明：显示名归一化后去掉全文所有 `*`，字符流与压缩前**逐字相同**；
> 结构性标签、条目标题、指针 target 逐项对拍未变。写入后余 **307 字节**，
> **P2-115 的阈值上调仍是下一轮写入的前置条件**。
> **变更检测与完整验收**：指纹 `7548a6fd…` → **`fb46b66e…`**（记录数仍 2220）⇒ **门控全量**。
> 新鲜度：本轮**未改后端**，`find backend/src/main -newer <jar>` **零命中** ⇒ 豁免成立；
> 仍另起新鲜实例 37790 跑验收（pid 39499，验收后已停，**含密钥的 `/tmp/p374-env.sh`
> 已 mavis-trash 删除并确认**）。回归 **45/0/1**（exit 0）+ EXTRACTION 验收 **25/0/0**（exit 0）。
> **本轮新增归档**：`2026-10-20_backend-review-evidence-75.md`（92 行 / 7531 字节，
> 承 `-62` ~ `-74` 体例，含 P2-136 全文与 P2-131 跨家结转进度）。
> **待决事项（均已记录在 `docs/drafts/backend-review-findings.md`，不因此停下）**：
> ①P2-115（**最要紧，写入前置条件**）：findings 字节上限贴顶，建议 `MAX_LINES=2000` /
> `MAX_BYTES=200000`，只需同步 `docs/drafts/cron-combined-task.md:14`；
> ②P2-136 三条修法择一；③P2-135 两种修法（spec 变严 vs 运行时放宽）；
> ④P2-131 跨家结转余 **JS 一家**；⑤P2-127 选项②③；⑥P2-126；⑦P2-129；⑧P2-128；
> ⑨P2-125；⑩P2-123；⑪P2-124；⑫P2-90；⑬P2-122；⑭P2-120；⑮P2-119；⑯P2-121；
> ⑰P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 /
> P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status / 另三份零覆盖设计稿 ——
> **均不在本轮范围内，本轮不因此停下**。


