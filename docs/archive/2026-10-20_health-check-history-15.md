# Health Check History Archive 15（第 365~367 轮，2026-10-20）

> **Archived**: 2026-10-20，第 369 轮压缩迁移，自 `docs/drafts/health-check-task.md` 第 79–352 行。
> **Rule**: 归档文件创建后不得修改。后续历史达阈值时使用新的日期命名归档。
>
> 以下为第 365 / 366 / 367 轮轮报告的**逐字原文**（含其间的空行）。
> 迁移原因：本文件写入第 369 轮报告后行数逼近 1500 行上限（迁出前 **1565 行 / 108055 字节**），
> 而第 370 轮的新报告仍需续写。
>
> **留在本区块下方未迁的三段**（不是历史叙述，是**基线的使用前提**，引用 `45/0/1` 或 `25/0/0` 时须一并阅读）：
> ①「第 264 轮起必须一并引用的第二重限定」；②「`accepted_commit` 的含义」；
> ③「第 251 轮起必须一并阅读的限定」。
>
> **未解决事项未被移出**：本归档三轮报告末尾的「未解决问题」是**逐轮副本**，
> 权威登记处是 `docs/drafts/backend-review-findings.md`（各轮报告自己就这么写），
> 且工作文件里保留**当前轮（第 369 轮）**的完整未解决清单。
>
> 本归档实测 **292 行 / 32162 字节**。
> **第 365 轮：Backend（代码）+ 设计文档（文档，doc round 265）**：
> **健康预检**：`/api/health` → `{"status":"ok"}`（HTTP 200）；37777 由 pid **10078** 在跑（**不是**本轮启动的），
> 后端源码自 jar 构建（2026-10-08）以来零改动，故在跑实例即当时的后端。
> **代码方向（Backend）新立 P2-120（✅ 已修，零行为变更）**：**活体 OpenAPI 给
> `POST /api/observations/batch` 声明了一个不可达的 400，并把 `ids` 标为必填** ——
> 而处理器 `ViewerController.batchGetObservations` 的**四条返回路径全是 200**：
> `ids` 缺失/空 → 200 空；过滤空白后为空 → 200 空；`UUID.fromString` 抛异常 → `log.warn` + 置 null + `filter`
> **静默丢弃**；剔除后为空 → 200 空。声明的 400 连 body 形状都不对（`ErrorResponse` = `{"error":...}`，
> 而唯一的真 400 来自 Jackson 拒收 `ids` 类型，body 是 Spring 默认体 `{timestamp,status,error,path}`）。
> `ids` 的「必填」**只是 OpenAPI 声明**（`@Schema(requiredMode=REQUIRED)`），**不是** Bean Validation ——
> 全仓 `@NotBlank` 只在 `SessionEntity` 的两个字段上。
> **活体实测（带阳性对照，库中 39,177 条观测）**：真实 uuid → `200 count=1`（**阳性对照**，证明端点与该 id 都有效）；
> `{"ids":["not-a-uuid"]}` → **`200 count=0`**；`{"ids":[真实,"not-a-uuid"]}` → **`200 count=1`**
> （**部分结果，调用方拿不到任何错误信号**）；`{"ids":[]}` 与 `{}` → `200 count=0`；
> `{"ids":""}` → 400 但 body 形状与声明不同。**实质危害不是「缺一个 400」，是「静默的错误答案」**：
> 发 10 个 id、其中 3 个格式非法，会拿到 7 条观测且不报任何错，与「那 3 条恰好不存在」**完全不可区分**。
> **已实施的三处修正（可执行行 0，全是注解与文档）**：①`ViewerController` 删掉那条**不可达**的 400
> `@ApiResponse`（并加 6 行说明注释）；②`ApiRequests.BatchObservationsRequest.ids` 去掉
> `requiredMode = REQUIRED`、描述改为记录宽容语义；③`docs/API.md` 与 `docs/API-zh-CN.md` 的 `ids`
> 必填标注 ✅ → ❌，各补一段**实测行为说明**（含 2 列表）。
> **零行为变更的机器证明**：改前改后**整份 OpenAPI 逐字对拍**，爆炸半径恰为 **1 个端点 + 1 个 schema**，
> 其余 **65 条路径**与全部其它 schema **逐字未变**；**行为一字未变**（五种探针的码与 count 与改前完全相同）。
> **为什么不收紧行为**：仓库自身两个 E2E 脚本**钉住宽容行为** —— `scripts/js-sdk-e2e-test.sh:211` 发
> `{"ids":["nonexistent-id"]}`（**非法 UUID**）并要求 2xx（`curl -sf` 在 4xx 上失败），
> `scripts/java-sdk-e2e-test.sh:270` 对 demo 发非法 UUID 亦然；改严需**同时改后端与脚本**且属
> **对外行为契约变更**，按既定纪律只记录不单方面实施 ⇒ **留三选一待用户决策**（维持宽容+文档说明〔已实施〕／
> 改严并同步改两个脚本／只对缺失为空报 400），任一选项都应**先补后端对该端点的测试**（现**零覆盖**）。
> 四家 SDK 都在客户端拦空 ids（Java `CortexMemClientImpl:555`、Python `client.py:575`、JS `client.ts:310`、
> Go `client_methods.go:166`）；MCP `ClaudeMemMcpTools.java:174` 也拦，且它**直查 repository**、不受端点影响。
> **文档方向（设计文档，doc round 265）**：首次审**四份零覆盖设计稿**
> （`sdk-improvement-research` / `memory-research-hub` / `screenpulse-implementation-plan` /
> `mcp-server-transport-analysis`）—— 端点字面量对活体 66 路径**幻影 0**（2 个候选**逐条查原文后全部证伪**：
> `/api/sessions/init` 文档明写是 **Worker** 的、`/api/context` 是**路径前缀**）；
> 这四份的 `文件:行号` 引用数为 **0**，该角度**不适用** ⇒ 改用**内部链接**角度：
> `docs/` 下 **506 个** md、**1,702 条**相对链接，断链 **33 条**。
> **已修 15 条**：`evolver-memory-analysis.md` 被拆成目录（`fb4262c` 删旧文件、`8ef2d53` 拆目录）导致的 **3 条短链** ——
> 而 `evolver-memory/AGENT.md` 的约束 1 与约束 5 **明文要求该路径保持为短入口**（并把 `wc -c` 它列为例行自检），
> 故**按 Hermes 同型体例补回短入口**（新建 `docs/drafts/evolver-memory-analysis.md`，**15 行** / 1272 字节，
> 自身 10 条链接全可解析），3 条短链**自愈且未改任何既有正文**；另目录前缀重复 1 处、`./` 误指仓库根 4 处、
> 缺目录前缀 8 处、**编号从未存在**的 `70-`→`82-` 1 处 ⇒ **断链 33 → 18**。
> **新立 P2-121（⏸ 记录不修）**：余下 **13 条断链的目标根本不存在**（`105-shell-hooks-extensibility-architecture.md`、
> `78-cross-cutting-architectural-patterns-synthesis.md`、`02-bluecortexce-recommendations.md` 从未在此路径存在；
> `01-intro-toc-memory-through-curriculum.md`、`07-idle-through-skillpublisher-v14.md` 已被上游**自动拆成多篇**，
> **被引章节归属需逐条内容判断**）—— 机械链到任一现存文件都可能指错章节，**只记录不猜**。
> 另 3 条在 `docs/archive/` 内（归档不可改）、2 条是探针误报（`screenpulse-implementation-progress.md:56,88`
> 的 `[text](url)` 是散文）—— **扫描必须剔除代码围栏**（Swift/Rust 源码里的 `"[\(title)](\(url))"` 会被误判）。
> **压缩**：本轮新写的 **P2-120 / P2-121** **写完即整体迁入** `docs/archive/2026-10-20_backend-review-evidence-65.md`
> （**118 行 / 12600 字节**，头部数字**迭代至不动点**），工作文件各留标题 + `- **Status**` 一行含指针
> （Status 96→98、Problem 47 不变、标题 110→112、findings **146234 → 148842 字节 / 1408 → 1413 行**）；
> health-check-task.md 行数逼近 1500 行上限，本轮一并把**第 360 / 361 轮**的历史轮报告逐字迁入
> `2026-10-20_health-check-history-13.md`，**三段「必须与基线一并阅读的限定」留在原处**
> （它们是基线的**使用前提**，不是历史叙述）。
> **本轮自查出并修正的三处「判据自身出错」**（均在采信前拦下）：①生成归档的脚本把「短入口 17 行」写死，
> 而新建短入口**实为 15 行**（17 是 Hermes 样板的行数，被误套）—— 归档**尚未提交**，按「错误的归档只能删除重建」
> 的规矩**删除重建**，并把该行数改为**脚本实测**而非写死；②脚本里「归档正文无裸管道」这条判据对**任何**
> markdown 表格都恒假（本归档正文本就有两张表），报的是**假 FAIL** —— 换成真正有意义的判据
> （表格块内各行**未转义**管道数必须一致 + 必须有合法分隔行），并把「Status 段数不变」这条
> 与脚本行为自相矛盾的判据改为「Problem 段数不变、Status 段数 +2」；③校验 `patrol-rotation.md`
> History 行时，我的判据**没有区分「本轮新增」与「历史存量」**，且**没有先剔除已转义的 `\|`** ——
> 于是对 **260 条旧体例行**（4 管道、行尾带收尾竖线，GFM 渲染与现行体例**等价**）与
> **1 条合规行**（正文里合法的 `\|`）报出假 FAIL。**重新逐行核实后的真实结论**：
> **401 条 History 行的正文全部没有裸管道**（表格结构完好），其中 260 条是旧体例、141 条是现行体例
> （体例自 2026-10-03 起切换，第 344 轮那一条是切换后仍用旧体例的**孤例**）——
> **旧体例行一律不改写**（追加式审计链的历史正文不得重写），只把判据改成
> 「**本轮新增行**须满足现行体例 + **全表**正文无裸管道」。
> **文档增长检查**：findings **148842 / 150000 字节**（余 1158B）、health-check-task.md 迁出后回落、
> 其余三份在限额内 ⇒ **本轮结束时检查通过**。
> **代码变化检测**：指纹 `3d1e270e…` → **`0449958ad0e456a78471f42e9ded5130f46cd389953e593ef45cf0b78370aa2d`**，
> 记录数仍 **2218** ⇒ **检测到代码变化**（本轮改的是后端注解 + 两份 API 文档 + 13 个文档的链接）。
> **完整验收**：**已执行**（改动落在**后端自身**，新鲜度豁免路径**不适用**）—— `mvn -o -q clean package -DskipTests`
> 重建 jar（02:11，81109043 字节）；**先在 37790 起新实例**（pid 33255；从 pid 10078 用 `ps eww` 提取 12 个环境变量、
> 写入 600 权限 `/tmp/p365-env.sh`，**取值全程未打印**）跑验收；**再按任务书「Backend 的改动才要求重启 Backend」
> 用同一 jar、同一环境、同一命令行形式重启 37777**（旧 pid 10078 → 新 pid **53884**）。
> **回归测试**：**45 / 0 / 1**（46 总，**exit 0**）；**EXTRACTION 验收**（`EXTRACTION_ENABLED=true`）：**25 / 0 / 0**（**exit 0**）。
> 新 37777 上复核：活体 **66 路径**、batch 响应码 `['200']`、`required: None`、行为仍 `200 count=0`。
> 已停 37790；**含密钥的 `/tmp/p365-env.sh` 已逐个确认删除**。
> **Backend 发现与修复数**：**新立 1 条（P2-120，已修，零行为变更）**、**修复 1 条**、**记录不修 1 条（P2-121，文档方向）**。
> **构建与重启**：jar 已重建（02:11）；**37777 已重启**（pid 10078 → **53884**），健康 200；
> 37778~37781 / 37790 **全空**（**只停了本轮自己启动的进程**）。
> **连续 3 轮检查**：本轮**改了代码**（注解层），连续 3 轮深入检查以**同一份逐字对拍 + 五种探针复跑**计，
> **第 1 轮**（OpenAPI 逐字对拍 + 行为探针）发现**无新问题**、**第 2 轮**（契约三方互查：OpenAPI / API 文档 / 代码）
> **无新问题**、**第 3 轮**（四家 SDK + MCP 的调用前提复查）**无新问题**且**未再修改** ⇒ 计数成立。
> **commit**：`patrol: round 365 - the batch endpoint promised a 400 it could never return`。
> **未解决问题**：①P2-120（batch 端点是否改严，三选一，**任一选项都应先补后端测试**，现零覆盖）；
> ②P2-121（13 条断链的目标归属，需内容判断）；③P2-119（demo 监听地址三选一）；
> ④P2-115（**最要紧的容量项**：findings 字节上限贴顶 148842/150000，余 1158B，建议
> `MAX_LINES=2000` / `MAX_BYTES=200000`，**无需改脚本**——本就是环境变量——只需同步 `cron-combined-task.md:14` 阈值正文）；
> ⑤P2-112 / P2-113 / P2-90 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 /
> P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status 段 / 另四份零覆盖设计稿 —— **均已记录在
> `docs/drafts/backend-review-findings.md`，不在本轮范围内，本轮不因此停下**。


> **第 366 轮：Java SDK（代码）+ 架构文档（文档，doc round 266）**：
> **健康预检**：`/api/health` → `{"service":"claude-mem-java","status":"ok"}`（HTTP 200）；
> 37777 由 pid **53884** 在跑（**不是**本轮启动的），且 `find backend/src/main -newer <jar>` **零命中**
> ⇒ 后端源码自 jar 构建（02:15）以来未变，在跑实例即当前后端。
> **代码方向（Java SDK）新立 P2-122（✅ 已修，零行为变更）**：**四家 SDK 都能配 API 密钥，
> 但后端从不校验它** —— 密钥今天是**零保护**，而 Java SDK 的 javadoc 直接断言
> 「API key for authentication (sent as Bearer token). **Null/blank = no auth**」，
> **后半句为假**（配了密钥也仍然 no auth）。
> **服务端根本没有鉴权机制（三路静态证据）**：①`backend/pom.xml` **无 `spring-boot-starter-security`**（零命中）；
> ②`backend/src/main/java/` 全量搜 `Authorization` → **0 命中**；
> ③搜 `SecurityFilterChain` / `WebSecurityConfigurer` / `@PreAuthorize` / `OncePerRequestFilter` /
> `HandlerInterceptor` → **只命中 `MdcAutoFilter`**，而它读的是 `X-Correlation-ID` / `X-Session-ID`
> 并把 correlationId **回写到响应头**，**不读** `Authorization`。
> **客户端侧四家都发 `Bearer <key>`**（逐家核到行）：Java `CortexMemClientImpl:87-91`
> （且仓库自带测试 `CortexMemClientImplTest:450` `apiKey_sendsAuthorizationHeader` 断言该头**确实发出**）、
> Go `client_impl.go:240`、Python `client.py:110`、JS `client.ts:621`。
> **活体实测（带阳性对照）**：`GET /api/stats` 不带 `Authorization` → `200`；
> 带**垃圾 Bearer** → `200` 且**响应体逐字相同**；带**垃圾 Basic** → `200` 且**逐字相同**；
> **阳性对照**：`X-Correlation-ID: p366-control-9f3a` 被 `MdcAutoFilter` **回写进响应头**
> ⇒ 同一探针**确实能读出请求头带来的响应差异**，故上面三条「无差异」是**真观察**而非探针失灵；
> 对照：未知路径 → `404` ⇒ 探针能区分响应。
> **已实施四处修正（可执行行 0，全是注释与文档）**：①`CortexMemProperties.apiKey` 的 javadoc
> 改为写明「后端不校验该头、今天保护不了任何东西」；②`CortexMemClientImpl` 里「Bearer token auth」
> 的措辞（它括注的「matches JS/Go SDK behavior」**属实**，四家确实都发）；
> ③Java SDK README 的 `cortex.mem` 属性表**此前完全没有 `api-key` 这一行**（该表 12 行，
> `api-key`/`apikey`/`authentic`/`bearer` 零命中，而另三家的 README 都写了），已**补一行**；
> ④`docs/ARCHITECTURE.md` 与 `-zh-CN.md` 的 `### Authentication` 段**双语各补一段**。
> **服务端鉴权本身未实施**：给后端加鉴权 = 改变**所有**现有部署的对外契约
> （今天无密钥的部署会立刻 401）= **对外行为契约变更**，按既定纪律只记录不单方面实施，
> **三选一见归档正文**（①维持现状+文档说真话〔已实施〕／②后端实现「可选鉴权」〔向后兼容的新增能力〕
> ／③移除客户端 `apiKey` 选项〔破坏性〕），**任一选项都应先补服务端对该头的测试**（现为零覆盖）。
> **与既有条目不重复**：findings 里「加注（不改写归档）」那条已记录**后端无鉴权**本身及 compose 暴露面
> （**服务端**视角）；本轮是**客户端**视角——四家都提供一个服务端不认的开关，故另立并写明两者关系。
> **Java SDK 其余部分逐条核实为真**：SDK 的 **23 个端点字面量**对活体 **66 路径**（**71 操作**）
> **幻影 0**；接口 **25** 个方法 = 实现 **25** 个 public 方法（**无实现独有**）；
> README 的「**10 个方法会重试**」= 实测 **10 个**重试调用点（5 静默 + 3 抛错 + 2 返回值），
> 其余 **15** 个不重试 ⇒ 数字**精确**。
> **文档方向（架构文档，doc round 266）**：**「API Layers」表逐层对活体核验 —— 双语全部零幻影、零遗漏**：
> 13 层的路径字面量在活体**逐条存在**，且各层描述里的枚举与活体逐条吻合
> （Ingestion 4、Session 3、Context 7、Memory 6、Mode 6+`/api/mode`、Extraction 3、Cursor 5、
> Import 5、Logs 2、Health 3、Test 3、Viewer 18 条路径全在、Stream `/stream`）。
> **全篇端点字面量清扫**（不只表里那节）：双语各 **33 个** `/api/` 字面量，唯一「活体不存在」的是
> `/api/ingest/session-start`——而那是**刻意标注为 404 的反例**（`ARCHITECTURE.md:316` 明写
> 「returns 404 (verified against a running backend, 2026-10-03)」），**本轮活体复核仍是 404**。
> **技术栈断言逐条为真**：Spring Boot **3.3.13**（`backend/pom.xml` parent 版本一致）、
> PostgreSQL **16**（`docker-compose.yml:25` = `pgvector/pgvector:pg16`）、
> 代理 **Node.js/axios**（`proxy/package.json` 依赖 axios，`proxy.js:23` 与 `wrapper.js:75` 两处 import）。
> `wrapper.js` 的**四条命令**断言为真（`session-start`/`session-end`/`user-prompt`/`tool-use`，`:1465-1474`）、
> 「`proxy/` 下无任何 `/api/context/*` 调用」为真（零命中）。
> **本轮文档方向唯一改动**：`### Authentication` 段**双语各补一段**（四家 SDK 都发
> `Authorization: Bearer <key>`，而后端**从不读这个头**，故配置密钥**今天保护不了任何东西**）。
> **压缩**：开篇先把 **7 条已结案条目**的 Status 缩成标准短形式（其全文逐字在归档里 ⇒ **零信息损失**，
> **省 3062B**）；本轮新写的 **P2-122 写完即整体迁入** `2026-10-20_backend-review-evidence-66.md`
> （**78 行 / 7165 字节**，头部数字**运行时实测**且**迭代至不动点**）；
> findings **148842 → 146784 字节 / 1413 → 1416 行**。
> **本轮自查出并修正的三处「判据自身出错」**（均在采信前拦下）：①查 JS SDK 是否发 `Authorization` 时
> **用错了路径**（`js-sdk/src/` 不存在，实际在 `js-sdk/cortex-mem-js/src/`）⇒ 那个「0 命中」是
> **探针失效**而不是事实；改正路径后确认 JS **确实**发（`client.ts:621`）⇒ 顺带确认 Java 那句
> 「matches JS/Go SDK behavior」**属实**（若不纠错，就会凭空立一条不存在的「JS 不发」缺陷）；
> ②校验 API Layers 表时**没剥查询串**，把 `/api/stats?project=...` 判成幻影 ⇒ 改正后**零幻影**；
> ③生成归档的脚本把「迁出前体量」**写死**成 145780 字节，而真正的前态是 **148842**
> （压缩批次曾被 `git checkout` 回退过）—— 归档里会留下一处**失实数字**，已改为**运行时实测**
> （同第 365 轮「17 行」那次的教训）。
> **代码变化检测**：指纹 `0449958a…` → **`4ae598530764d8d8dd0c1667b5dcd359008c41fc17bf63f902efb4fafb071156`**，
> 记录数仍 **2218** ⇒ **检测到代码变化**（改了 2 个 Java 源文件的注释 + Java SDK README）。
> **完整验收**：**已执行**（指纹变化 ⇒ 必须跑）。**新鲜度豁免适用**——本轮改的是 **Java SDK** 而非后端，
> 且后端源码自 jar 构建以来**零变化**；为稳妥仍**另起 37790 新实例**（pid 29519，从 pid 53884 用 `ps eww`
> 提取 12 个环境变量、写入 600 权限 `/tmp/p366-env.sh`，**取值全程未打印**）跑验收。
> **回归测试**：**45 / 0 / 1**（46 总，**exit 0**）；**EXTRACTION 验收**（`EXTRACTION_ENABLED=true`）：
> **25 / 0 / 0**（**exit 0**）。
> **Backend 发现与修复数**：本轮代码方向为 Java SDK，**后端未改**；**新立 1 条（P2-122，已修，零行为变更）**、
> **修复 1 条**、**记录不修 1 条**（服务端鉴权本体，留待用户决策）。
> **构建与重启**：Java SDK `mvn -o clean test` → **196 通过 / 0 失败 / 0 错误 / 0 跳过**
> （client 143 + spring-ai 46 + starter 7）；**后端无需重建、无需重启**（后端源码未变）。
> 37790 是**本轮自己**启动的，验收后**只停本轮自己启动的进程**；37777（pid 53884，非本轮启动）**未受影响**。
> **连续 3 轮检查**：本轮**改了代码**（注释层），连续 3 轮深入检查 ——
> **第 1 轮**（端点/契约面）：SDK 23 个端点对活体 66 路径，幻影 0；接口 25 vs 实现 25；
> **第 2 轮**（数字/行为面）：重试调用点实测 10 个（5 静默 + 3 抛错 + 2 返回值），
> README 的「10 / 其余 15」**精确**；**第 3 轮**（跨家一致性 + 服务端落点）：四家 `Bearer <key>` 逐家核到行、
> 服务端三路静态证据 + 活体实测 + 阳性对照 + 404 对照 ⇒ **三轮均未发现新问题、且未再修改**，计数成立。
> **commit**：`patrol: round 366 - four SDKs can send an API key the backend never checks`。
> **未解决问题**：①P2-122 服务端鉴权本体三选一（任一选项都应先补服务端测试）；②P2-115（**最要紧的容量项**：
> findings 字节上限 146784/150000，余 3216B，建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，
> **无需改脚本**——本就是环境变量——只需同步 `cron-combined-task.md:14` 阈值正文）；③P2-120（batch 端点改严三选一）；
> ④P2-119（demo 监听地址三选一）；⑤P2-121（13 条断链的目标归属）；⑥另三份 README（Go/Python/JS）的
> `apiKey` 措辞属**各自轮换方向**，本轮只记不改；⑦P2-112 / P2-113 / P2-90 / P2-93 / P2-95 / P2-91 / P2-87 /
> P2-88 / P2-100 / P1-1 / P2-79+P2-80 / P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status 段 /
> 另四份零覆盖设计稿 —— **均已记录在 `docs/drafts/backend-review-findings.md`，不在本轮范围内，本轮不因此停下**。


> **第 367 轮：Go SDK（代码）+ 用户指南（文档，doc round 267）**：
> **健康预检**：`/actuator/health` → HTTP **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的），
> 且 `find backend/src/main -newer <jar>` **零命中** ⇒ 后端源码自 jar 构建以来未变，在跑实例即当前后端。
> **代码方向（Go SDK）新立 P2-123（✅ 已修，零行为变更）**：
> `go-sdk/cortex-mem-go/client_impl.go` 的 `doFireAndForget` **承重注释**（第 214 轮写入）**四个层面都不准**：
> ①**链上的处理器是 `handleToolUse`**（`@PostMapping("/tool-use")`），**不是** `handleObservation`
> （那是 `@PostMapping("/observation")` 的处理器，**不在 tool-use 链上**）；
> ②**归因错了机制**——第一道防线是 pending 层 `existsBySessionAndTool`
> （JPQL 条件 `contentSessionId AND toolName AND toolInputHash AND status <> 'failed'`，
> **无任何时间窗**），30 秒内容哈希窗口只是**第二道**防线，且它比的是**落库时刻**
> （`windowStart = now - 30000`，比的是两次 observation 的保存时刻）；
> ③「**cannot create a duplicate**」**过强**——pending 检查是 check-then-insert，
> 而 `PendingMessageEntity` 上声明的 `@UniqueConstraint` 因 `ddl-auto: none` **在库里并不存在**
> （库里 `uk_session_tool_input` 约束数**实测 0**）；
> ④**范围被夸大**——`doFireAndForget` 服务**三条**捕获操作，另两条后端**不幂等**。
> **活体实测（带反向对照）**：`POST /api/ingest/user-prompt` 同 body 连发两次 → **2 行**；
> 反向对照（只发一次）→ **1 行** ⇒ 纯插入，**无任何服务端去重**。
> **已实施（可执行行 0，纯注释）**：把该注释按三条分别重写——链改对、机制写对（两道防线的次序与各自边界）、
> 范围写对（另两条不幂等）、保证强度写对（「保证不绝对」并指向 P2-29）。
> **服务端幂等化与 SDK 重试范围收窄未实施**：属**对外行为契约变更**，按既定纪律只记录不单方面实施，
> **三选一见归档正文**（①只更正注释〔已实施〕／②让后端幂等〔`/api/ingest/user-prompt` 按
> `(content_session_id, prompt_number)` 去重或 upsert；`/api/ingest/session-end` 已有 summary 时跳过〕／
> ③SDK 侧收窄重试范围〔只对 `RecordObservation` 重试，四家一起改〕），
> **任一选项都应先补后端对这两条路径的幂等性测试**（现为零覆盖）。
> **P2-122 跨家结转（✅ 已修）**：Go SDK 的 `WithAPIKey` 注释、header 设置处注释，
> 以及 `README.md:110` 与 `README-zh-CN.md:110` 的 `WithAPIKey` 行
> （原措辞「Bearer token for authentication / Bearer Token 认证」**未提后端不校验**），
> 现已改写为「每次请求以 `Authorization: Bearer <key>` 发出，**当前后端不校验这个头**」。
> **P2-90 复认并更正（✅ 已修，双语）**：本轮**亲跑复测**逐模块 `=== RUN` 计数 =
> 根 **303**（core 236 + dto 67）、genkit 13、eino 8、langchaingo 12、`examples/http-server` 27
> ⇒ **合计 363**（与 P2-90 记的 `7a78266` 归因吻合）。双语 **6 处**数字 362/302/235 → **363/303/236**；
> 无日期断言**加测量日期**，带日期快照同步为 **2026-10-20**，并注明「计数是带日期的实测值而非实时值」。
> **「彻底不写死数字」的形态问题仍留待用户决策**。
> **Go SDK 其余部分逐条核实为真**：**23 个端点字面量**对活体 **66 路径**（4 条 `%s` 占位符归一后）
> **幻影 0**；`gofmt -l` **无输出**、`go vet ./...` **exit 0**、`go test ./...` **exit 0**（**310 顶层 + 53 子测试 = 363**）。
> **文档方向（用户指南，doc round 267）**：**四角度机械扫描全通过**——指南提到的脚本/迁移路径**全存在**、
> 端点**零幻影**、迁移表双语 **17/17 逐字吻合**（磁盘 17 个）、端口**全部可溯源**。
> **新立 P2-124（✅ 已修，双语）**：DEPLOYMENT 限流段把 `CLAUDEMEM_RATE_LIMIT_WINDOW_SECONDS`
> 写成「**Sliding-window length in seconds / 滑动窗口长度（秒）**」，而实现是**固定窗口**
> （`tryIncrement` 到期**整窗重置**）；**活体边界突发实测**：批 0（t+0.1s，1 个）→ 批 A（t+58.1s，9 个全 200）→
> 批 B（t+60.6s，**10 个全 200**，前 10 个才 2.5 秒大）→ 批 C 429 ⇒ **约 2.5 秒内放行 20 个请求**。
> 同段还把 `retry_after` 说成「窗口长度」，实际是**当前窗口的剩余秒数**
> （`getRemainingSeconds()` = `windowSeconds - (now - windowStart)`），实测第 11 次读到 **`"retry_after":"60"`**、
> 2 秒后再读 **`58`** ⇒ 随时间递减。两处均已在双语更正并新增实测说明段。
> **限流段其余断言逐条为真**：只有 **1 个**调用点（`IngestionController.java:132`）、只作用于 tool-use、
> 另三个 ingest 端点与全部读端点**不限流**、活体 10 次成功 + 第 11 次 429、13 次 `GET /api/search` 全 200。
> **压缩**：P2-96 / P2-106 的 Status **证据尾巴**（1254B + 1181B）迁入
> `2026-10-20_backend-review-scope-evidence-11.md`（29 行/4287B），工作文件保留结论句 + 指针
> （**头 + 尾巴 == 原 Status 逐字**有机器断言）；P2-123 / P2-124 全文迁入
> `2026-10-20_backend-review-evidence-67.md`（129 行/13574B）。findings **146784 → 147021B / 1416 → 1421 行**（余 2979B）。
> **变更检测**：指纹 `4ae59853…` → **`1c6b85862d5351fa5b70759de62759b1711c2465febac45529bf523e30eb1671`**，
> 记录数仍 **2218** ⇒ **代码有变化**（Go SDK 注释改动被计入；`.md` 与 `docs/` 被指纹排除），
> **完整验收门控触发**。**新鲜度豁免适用**（本轮改的是 Go SDK，后端源码零变化 ⇒ 在跑的 37777 即当前后端）；
> 为稳妥仍另起 **37790**（pid 56497）跑验收，37777 未重启。
> **回归测试**：`SERVER_URL=http://127.0.0.1:37790 bash scripts/regression-test.sh --skip-build` → **45/0/1**，**exit 0**。
> **EXTRACTION 验收**：`BACKEND_URL=http://127.0.0.1:37790 EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh`
> → **25/0/0**，**exit 0**。验收后已停 37790，探针行已清理。
> **构建与重启**：Go SDK `gofmt -d .` **无输出**、`go vet ./...` **exit 0**、`go test ./...` **exit 0**；
> 本轮**未改后端** ⇒ **无需重建 jar、无需重启 37777**。
> **连续 3 轮检查（修复后）**：**第 1 轮**（Go SDK 最终注释里的每条断言逐条对回源码/活体：40 余条，含
> `handleToolUse` 绑定、`handleObservation` 不在链上、`existsBySessionAndTool` 的 JPQL **无时间窗**、
> `status <> 'failed'`、`SHA-256(toolInput)`、30 秒窗口比的是**保存时刻**、user-prompt/summary 纯插入、
> `completeSessionForSummary` 总返回 session、库里 `uk_session_tool_input` 约束数 **0**、`ddl-auto: none` 逐字核对、
> 三个 `doFireAndForget` 调用方与其目标路径、默认 `MaxRetries=3`（**总尝试次数**语义）与 `500ms`/`±25%`）；
> **第 2 轮**（Go README 双语：数字与**本轮实测**逐项对拍、算术自洽、旧数字零残留、apiKey 措辞、
> 模块结构断言）；**第 3 轮**（DEPLOYMENT 改动的**活体复核**：10+1 门限、`retry_after` 递减、
> 边界翻倍、只守一个端点、探针行清理）。
> **三轮均未发现新问题、且未再修改** ⇒ 计数成立。**中途因第 3 轮的活体复测间隔读数（2.5s / 3.3s / 2.2s）
> 触发了 DEPLOYMENT 的一次措辞收紧**（把单次读数绑回那一次运行，一般结论改为「几秒」），
> 故在**定稿状态**下把三轮**从头连跑一遍**（期间零改动）后才认定计数成立。
> **本轮自查出并修正的判据自身出错（4 处，均为探针 bug、注释与文档均未被误改）**：
> ①第 1 轮探针用**带引号**的串去匹配 Java 方法名、且正则 `ResponseEntity<[^>]*>` **跨不过嵌套泛型**
> `ResponseEntity<Map<String, String>>` ⇒ 4 处假 FAIL；
> ②第 1 轮探针用**固定距离窗口**（600 字符）跨 `@PostMapping` 与方法定义，而两者之间隔着 **19 行注解块** ⇒ 又 2 处假 FAIL；
> ③第 1 轮探针把 Go 接收者写成 `*client`，实际是 `*httpClient` ⇒ 3 处假 FAIL；
> ④第 2 轮探针找 ZH 里的字面串「9 个独立 Go 模块」，原文是「9 个独立的 Go module」 ⇒ 1 处假 FAIL；
> ⑤第 3 轮探针写成 `ra1 < 60`，而实测值恰为 **60** ⇒ 恒假判据。
> 另有 2 处**上一轮**遗留的探针错同批修正：Go 端点核验**未处理 `%s` 占位符**（4 条假幻影）、
> 指南端口核验用**写死白名单**（把 `8080`/`5434` 判成「仓库未定义」）、`/api/search` 探针**漏必填
> `project` 参数**（13 次全 400 = 探针问题）。
> **commit**：`patrol: round 367 - the retry comment named a handler that is not on the path`。
> **未解决问题**：①P2-123 Go SDK 重试安全性三选一（任一选项都应先补后端幂等性测试）；
> ②P2-124 限流器三选一（①只更正文档〔已实施〕／②改成真滑动窗口／③把类名与 javadoc 的 "sliding" 改成 "fixed"）；
> ③P2-90 Go README 测试总数的**形态**（是否彻底不写死数字）；
> ④P2-122 服务端鉴权三选一；⑤P2-115（**最要紧的容量项**：findings 字节上限贴顶 **147021/150000**，余 **2979B**，
> 建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，**无需改脚本**——本就是环境变量——只需同步
> `cron-combined-task.md:14` 阈值正文）；⑥P2-120（batch 端点改严三选一）；⑦P2-119（demo 监听地址三选一）；
> ⑧P2-121（13 条断链的目标归属）；⑨P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 /
> P1-1 / P2-79+P2-80 / P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status 段 / 另四份零覆盖设计稿 ——
> **均已记录在 `docs/drafts/backend-review-findings.md`，不在本轮范围内，本轮不因此停下**。


