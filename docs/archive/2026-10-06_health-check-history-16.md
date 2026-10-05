# Health Check History 16 — 第 279–288 轮巡检报告（第 293 轮迁出）

> **归档规则**：巡检报告按轮次保留；达增长阈值时把最早的若干轮逐字迁入此处。
> **归档文件创建后不得修改。**

> 本批为 `docs/drafts/health-check-task.md` 越过 `MAX_LINES=1000`（**1043 行 / 81537 字节**）而迁出 **10 轮**（第 279–288 轮），工作文件保留第 289–293 轮。
> 按**轮号**匹配 `## 第 N 轮 — ` 提取，并逐块断言**块内无嵌套轮次标题**（10/10 通过）。

## 第 279 轮 — 2026-10-06T02:20:00+08:00

### 健康预检

HEAD=`665b5aa`、工作区干净、37777 健康、仅 37777 在监听、指纹 `ebe1be42…` 与基线一致、五份活动文档退出 0。

### 代码方向（Go SDK）：**三个角度全部零缺陷**

**角度一：`errors.Is` 链在穿过包装后是否仍成立。** AGENTS.md 称 `error.go` 有 11 个 sentinel 的 `Unwrap`，
实测（写了一个独立模块直接 import 本地 SDK）：

- **11 个 sentinel 全部经 `Unwrap()` 解析成功**（`statusCodeToError` 覆盖 400/401/403/404/409/422/429/500/502/503/504）；
- **扛得住两层 `fmt.Errorf("%w")` 包装**；
- **未映射的状态码（402/418/451/501/599）不误匹配任何 sentinel**。

**角度二：15 个 `Is*` 谓词是否都能解包。** 全量扫描发现 `IsServerError` 是**唯一**函数体里没有 `errors.As` 的——
**探针错**：它只是 `IsInternal` 的别名，而 `IsInternal` 用了 `errors.As`。**实测确认包装后的 503 → true、404 → false**。

**角度三：ctx 传播。** 全 SDK **只有一个请求构造点**（`client_impl.go:219`），
用的是 `http.NewRequestWithContext`，**零裸 `http.NewRequest`**——单点收口使 ctx 传播成为**结构性保证**。
**生产代码零 `context.Background()` / `context.TODO()`**（测试里 207 处属正常）。
35 个方法中 29 个收 ctx，**不收的 6 个是 `Close()`、`String()` 与 4 个 `nopLogger`，均不发请求**。

**结论：Go SDK 本轮零缺陷，不制造修改。**

### 文档方向（架构文档，doc round 180）：**零缺陷，第 272 轮的判定重新核实成立**

结构对拍：H1 **6/6**、H2 **13/13**、H3 **23/23**、H4 **12/12**、表格字符 **418/418** 全等；
围栏 44/42 差 2，与第 272 轮记录一致（EN 把表 DDL 拆成更多块、ZH 合并）。

**重新核实而不是沿用旧结论**：抽取两文件**全部 sql 块、剥掉行内注释与 markdown 引用行后逐行比对**——
**EN 140 行 / ZH 140 行，逐行完全一致**。DDL 零信息损失，唯一差异是围栏边界。
DDL 之后那段关于 `created_at_epoch` 的说明，中英两版位置相同、数字相同
（观测 19,711/38,088 = 51.8%，摘要 6,589/6,590 = 99.98%），**属合理本地化**。

**三个探针错误，均先识别再采信**：
① 分节统计按**英文标题**索引中文标题 → 全部落成 `None`；
② 用「数据库」做 ZH 侧锚点 → 该节标题不同，**匹配到 0 个 sql 块**；
③ 数字对拍的正则只覆盖英文措辞（`of`），中文是「38,088 行中有 19,711 行」→ 返回空。
**三处都是探针口径问题，文档本身三次都是对的。**

### 变更检测

**纯读与报告，无任何文件改动**（`git status` 干净），指纹 `ebe1be42…` **未变**且与基线一致
→ 按门控**不跑完整验收、不推进基线**。这是本批第一个真正的零改动轮次。

## 第 280 轮 — 2026-10-06T02:35:00+08:00

### 健康预检

HEAD=`06e17b4`、工作区干净、37777 健康、仅 37777 在监听、指纹 `ebe1be42…` 与基线一致、五份活动文档退出 0。

### 代码方向（Python SDK）：**三个角度全部零缺陷**

**角度一：调用方自带 session 的归属与 API key 隔离。**
`CortexMemClient.__init__` 接受 `session=`（docstring 承诺「**never mutated**、**does not close a session
it did not create**，共享一个 session 永不泄漏 API key 或 SDK 的 User-Agent」）。**逐条实测**：

- 调用方自带的 session 在 `client.close()` 后**仍可用**（未被关闭）✅
- SDK 自建的 session 在 `close()` 后**被关闭**（`_owns_session` 标志生效）✅
- **`session.headers` 未被写入任何键**，`Authorization` 不在其中；而 SDK 仍**逐请求**发送鉴权头 ✅

**角度二：三个数值参数的下钳。** `timeout` / `max_retries` / `retry_backoff` 全部下钳：
`0 / 0.05 / -5 → 0.1`（超时与退避）、`0 / -5 → 1`（重试次数）。**全部实测吻合。**

**角度三：`close()` 之后还有没有方法能继续发请求。**
**用 AST 解析**（正则探针只找到 14 个 public 方法，**不完整**——README 的 25 API 方法 + `close` 实为 **26** 个，
与第 161 轮结论一致）。**不动点迭代**求出「直接或经委托有 `_assert_not_closed` 守卫」的方法集：
**25 个 API 方法全部有守卫，唯一没有的是 `close()` 本身——这是正确的**，关闭必须幂等。

**结论：Python SDK 本轮零缺陷，不制造修改。**

### 文档方向（运维/用户指南，doc round 181）：**零缺陷**

`backend/README.md`（439 行）与 `docs/DEVELOPMENT.md`（1441 行）**此前从未深查**，本轮补上：

- **零幻影脚本**（`backend/README.md`）；`install.sh` 的命中是 **nvm 安装 URL 的一部分**（正则误报）；
- **端口一致**：`backend/README.md` 全文只出现 `:37777`，与活体监听一致；
- `DEVELOPMENT.md:385` 的「**29 services**」与活体 service 目录**实测 29 个**吻合；
- `DEVELOPMENT.md:549` 的 `./test-all.sh`（**在 Go SDK 目录、不在 `scripts/`，我的探针只查了后者**）
  **确实存在**，且其 `MODULES=("." "genkit" "eino" "langchaingo")` **与文档声明的四个模块逐项一致**；
  同行的「this is what 335 counts」= **302 + 13 + 8 + 12**，与第 263 轮确立的口径吻合；
- `./mvnw` 用法：代码块内**先 `cd backend` 再执行**，且 `backend/mvnw` 确实存在（11790 字节、可执行）。

**两个探针错误，均先识别再采信**：① 把 nvm 的 URL 当成了仓库脚本；② 只在 `scripts/` 里找脚本，
漏掉了 Go SDK 目录下的 `test-all.sh`。**两处文档都是对的。**

### 变更检测

**无任何文件改动**（`git status` 干净），指纹 `ebe1be42…` **未变**且与基线一致
→ 按门控**不跑完整验收、不推进基线**。**连续第二个零改动轮次。**

## 第 281 轮 — 2026-10-06T02:54:24+08:00

- **轮次**: 281 | **代码方向**: JS/TS SDK | **文档方向**: 架构文档（doc round 182）
- **起点**: `HEAD` = `3720f47`，工作区干净；仅 37777 在监听（java pid 42092，`127.0.0.1:37777`，启动于 2026-10-05 17:51:24）
- **基线**: `accepted_commit: ed11dee` / `code_fingerprint: ebe1be42…`

### 健康预检

活体 `/v3/api-docs` 可取（86744 字节），实测 **67 操作 / 62 路径**，与基线精确一致。
指纹 `ebe1be42…` **未变**，与基线记录相同。

### 代码方向（JS/TS SDK）—— 两个角度，零缺陷

- **测试基线**：`npm test` → **259 passed**（client 246 + truncated-body 5 + demo `parseIntParam` 8），**与基线精确一致**；
  `npm run lint`（`tsc --noEmit`）干净；`npm run build` 成功产出 CJS/ESM/DTS。
- **角度一 · close 守卫覆盖**：27 个 public 方法（25 个 API 方法 + `close` + `toString`），
  按「守卫集合求不动点」迭代（而非单遍）后，**25 个 API 方法全部有 `assertNotClosed` 守卫**，
  唯一无守卫的是 `close` 本身——**正确**，关闭必须幂等。与第 280 轮 Python 的结论完全一致。
- **角度二 · barrel 导出面**：`index.ts` 导出 67 项、各模块声明 72 项，**5 项未从包入口 re-export**：
  `ResolvedClientConfig`、`resolveConfig`（`src/client-options.ts`）、
  `firstNonNullOr`（`src/dto/wire-helpers.ts`）、
  `parseObservationConceptArray`、`parseObservationTypeArray`（`src/dto/misc.ts`）。
  **判定用的是构建产物、不是静态阅读**：tsup 构建出的 `dist/index.d.ts` 共 **840 行**，
  在其中逐个 grep 这 5 个名字——`ResolvedClientConfig`、`resolveConfig`、
  `parseObservationTypeArray`、`parseObservationConceptArray` 均为 **0 次出现**（被 tree-shake 内联；
  `private readonly config` 的类型在 `.d.ts` 里被 TS 抹去），
  `firstNonNullOr` 仅出现 **1 次、且在第 297 行的一句 JSDoc 注释里**，不是类型引用。
  → **没有一个泄漏进公开类型面，故非缺陷**。
  旁证：两个 README 与全部 `.md` 对这 5 个名字**零提及**；同轮的 `safeStringOrStringList`
  （第 262 轮补的）确实已导出并带注释说明，**是首版探针把块内注释与名字粘连导致的误报，本轮已剔除**。
  第 216 轮归档曾就其中两个名字记过同样的结论，本轮**重新用构建产物验证而非沿用**。

### 文档方向（架构文档，doc round 182）—— DOC-1 已修（双语）

- **DOC-1（P2，已修）** 根 README「Build & Test / 构建与测试」的三条 `mvn` 命令**一条也跑不通**。
  **实测旧写法**：`mvn compile` 于仓库根 → `MissingProjectException: there is no POM in this directory`，
  退出码 1（三条同命）。仓库根**无 `pom.xml`**，Maven 工程在 `backend/`
  （`backend/pom.xml` 4457 字节、`backend/mvnw` 11790 字节且可执行）。
  **修法最小且与本 README 自身口径一致**：Option 2 早就是 `cd BlueCortexCE/backend` 紧接 `mvn clean install`，
  故仅在代码块首行加 `cd backend` 并补一句说明，中英双语**各 +3 行、完全对称**。
  **正向验证**：按改后代码块原样执行 → `BUILD SUCCESS`、退出码 0；**反向对照**即上面的旧写法失败。
  **该坑仓库里已写明过**：`docs/DEVELOPMENT.md:539-541` 专门解释了 wrapper 与目录的对应关系，根 README 是唯一漏掉目录前提的地方。
- **核实为真、未改**：核心端点表六行对活体 OpenAPI 逐条命中；环境变量表九行——`application.yml`
  **无任何 `spring.ai` 块**（六个关键词全零命中），即 AI 配置全部由环境变量直供，与 README 指向 `.env.example` 的口径吻合；
  六个相对链接目标全部存在；`Project Structure` 只列四项目录而四个 SDK 缺席，但 `ARCHITECTURE.md:130-137` 架构图**四 SDK 齐全**，
  判为**示意性子集、非错误断言**，按既有先例不修。
- **三次探针自身出错，均在写入前拦下**：①`ls pom.xml` 退出码 1 打断 `&&` 链，**`mvn compile` 根本没跑**就下了结论；
  ②用**环境变量名**去 grep `application.yml` 得 0 命中，实为 relaxed binding 下 yml 用小写点号键——**检查形式错**；
  ③`^VAR=` 锚点漏掉 `.env.example:70` 注释形式的 `MEMORY_REFINE_ENABLED=false`，一度判成「文档介绍示例文件里没有的变量」。
  **三处文档都是对的。**

### 结构校验

`README.md` 与 `README-zh-CN.md`：代码围栏各 **30 / 30** 平衡、`U+FFFD=0`；
改动 diff 恰好 **2 文件 6 insertions**，中英对称。`docs/archive/README.md` 未改动。

### 变更检测

改动**仅两个 `.md`**，指纹 `ebe1be42…` **未变**且与基线一致
→ 按门控**不跑完整验收、不推进基线**。

## 第 282 轮 — 2026-10-06T03:43:45+08:00

- **轮次**: 282 | **代码方向**: Demo | **文档方向**: SDK README（doc round 183）
- **起点**: `HEAD` = `c8683ac`（第 281 轮），工作区干净；仅 37777 在监听
- **基线（跑验收前）**: `accepted_commit: ed11dee` / `code_fingerprint: ebe1be42…`

### 健康预检

活体 `/api/health` → `{"service":"claude-mem-java","status":"ok"}`；`/v3/api-docs` 67 操作 / 62 路径。
指纹 `ebe1be42…`，与基线一致。

### 代码方向（Demo）—— P2-56 范围更正（已修文档）+ 新立 P2-69

- **本轮唯一真缺陷是「已记录条目的范围陈述被实测推翻」**。`DemoParams` 的类 Javadoc 写着
  「**Not every controller is**：SearchController、ObservationsController、ExtractionController 用它，
  而 `ExperiencesController` 把两个参数绑成 `Integer`」——**把 `ExperiencesController` 表述成唯一例外**；
  P2-56 条目的标题与 Problem 同样写着「四个控制器有三个用、第四个绕过两个参数」。
- **实测（demo 由本轮从当前源码重建并启动于 37778，pid 54973）**：
  `boundedInt` 调用点为 **3 个文件 5 处**；而**绕过方是 3 个控制器 6 个参数**——
  `ExperiencesController`（`count`、`maxChars`，`Integer`）、`MemoryController`（两个端点的 `count` 与 `maxChars`，原生 `int`）、
  `SessionLifecycleController`（`promptNumber`，原生 `int`）。
  **六组活体对拍全部实测**：`1_0` 在六个参数上一律返回 **Spring 默认 400 体**，
  `0x10` 在六个参数上一律被**接受**（对照组 `limit=0x10` 被 `{"error":"limit must be an integer"}` 拒绝）。
- **已修**：`DemoParams` 类 Javadoc 按实测改写为完整枚举（**diff 过滤后零非注释行**，纯 Javadoc）。
- **新立 P2-69（⏸ 记录不修）**：`promptNumber` 是 demo 里**唯一没有范围检查**的数值参数，
  `promptNumber=-1` 返回 200，且 PostgreSQL `mem_user_prompts.prompt_number` 真实落库为 `-1`（`0x10` 落库为 `16`）。
  不修理由：补范围检查 = 收窄已发布端点的值域，属对外契约变更。
- **刻意不报**：`MemoryController` 是唯一不在 `/demo` 前缀下的控制器（路径为 `/memory/*`）——
  查 demo README 后确认**文档写的正是 `/memory/*`**，代码与文档一致，属既有意图布局。
- **五次探针自身出错，均在写入前拦下**：①`ls pom.xml` 退出码 1 打断 `&&` 链，命令根本没跑；
  ②用**环境变量名** grep `application.yml` 得 0 命中（实为 relaxed binding 的小写点号键）；
  ③`^VAR=` 锚点漏掉 `.env.example` 里注释形式的变量；
  ④`/demo/experiences` 的 400 来自 project 缺失而非数字绑定——Spring 先绑定再进方法体，
  `0x10` 被当作 16 接受后才轮到 project 检查，**补齐 project 才隔离出绑定行为**；
  ⑤`/demo/experiences/icl` 404 是**我的路径写错**，真实路径是 `/demo/iclprompt`。

### 压缩（第三十九 / 四十次）与一次被验证脚本抓到的边界错误

findings 已到 **980 行 / 102342 字节**，**双阈值余量分别只有 20 行 / 58 字节**，加 P2-69 必然越线。
按第 267 轮确立的杠杆（保留 Problem、只迁出可复现的实测细节）新建归档 `-31`（P2-56 更正 + P2-69 取证）、
`-32`（P2-65 / P2-14 明细）、`-33`（P2-47 / P2-42 明细）。

**本轮抓到的真错误**：迁移 `P2-47` 与 `P2-42` 的 Status 段时**两处区间都短了 4 行**
（`P2-47` 实为 698–711 却只迁 700–707；`P2-42` 实为 601–613 却只迁 605–609），
致段尾共 **8 行既未留在工作文件、也未进归档——即内容丢失**。
**该错误由「用删除前快照双向比对」的验证步骤抓到；若不做这一步，丢失会静默通过。**
按「错误的归档只能删除重建」，`-34` 删除重建两次方为正确版本。

**压缩终验**：应迁出 **29 行**逐字在归档（通过）；应保留 **863 行丢失 0**（通过）；
三个归档**块数 = 工作文件提及行**（2/2、2/2、2/2）；**101 个归档链接断链 0**；条目数 59（快照与当前一致，P2-69 已含在内）。
`980 行 / 102342 字节` → **`970 行 / 102369 字节`**，doc-growth 退出 0。

### 文档方向（SDK README，doc round 183）—— 三角度零缺陷

- **角度一 · API 方法覆盖率**（质量标准里写着「SDK README 覆盖所有公共 API 方法」，**此前从未系统核实**）：
  Python **25/25**、JS **25/25**、Go **25/25**、Java **25/25**，**四家双语 README 均零缺口**。
- **角度二 · 公开错误类型**：Go README 未提 12 个 sentinel、Python README 未提 7 个异常类——
  查原文后**判为非缺陷**：Go 的 `## Error Handling` 教的是 `IsNotFound(err)` 谓词用法，
  Python 教的是异常捕获示例，**两者都未声称穷举**，写法差异不判缺陷。JS 两个错误类均有覆盖。
- **角度三 · 环境变量零幻影**：Java SDK README 的四个 `CORTEX_MEM_*` 用 grep 环境变量名查得 **0 引用**——
  实为 Spring relaxed binding 下 `cortex.mem.*` 的标准环境变量形态，
  四个全部核到 `CortexMemProperties` 的真实字段（`baseUrl` / `projectPath` / `captureEnabled` / `captureUserPromptEnabled`）。
  **这是本轮第二次踩同一个 relaxed binding 陷阱**（第一次在第 281 轮的 `SPRING_AI_OPENAI_*`）。

### 进程新鲜度闸门

本轮改动在 `examples/cortex-mem-demo/`，而 `backend/pom.xml` 对 `cortex-mem-demo` **零引用**，
故该改动**不可能进入后端二进制**。37777 启动于 2026-10-05 17:51:24，其后触及 `backend/` 的提交共三个
（`ae21c6b` P2-62、`ed11dee` P2-67、`665b5aa` P2-68），**逐个核实非注释行数均为 0**（纯 Javadoc 修正），
字节码不受影响。**故 37777 对本次验收是新鲜的。**

### 验收

指纹 `ebe1be42…` → **`7c7dc3fa…`**（改了 `.java`），按门控跑完整验收：
回归 **45 通过 / 0 失败 / 1 跳过**（`Passed` 计数非不变量）、
`EXTRACTION_ENABLED=true` Phase 3 验收 **25/0/0**（带 P2-46 限定：Test 6 走兜底分支、Test 14 断言因数据累积而恒真）。
**全部通过 → 基线推进**至 `accepted_commit: c8683ac` / `code_fingerprint: 7c7dc3fa…` / `accepted_at: 2026-10-06T03:43:45+08:00`。

### 进程清理

本轮自行启动的 demo（pid 54973）已停止，**37778 已释放**；37777 未被触碰。

## 第 283 轮 — 2026-10-06T03:57:00+08:00

- **轮次**: 283 | **代码方向**: Backend | **文档方向**: 用户指南（doc round 184）
- **起点**: `HEAD` = `9febe67`（第 282 轮），工作区干净；仅 37777 在监听（pid 42092）

### 健康预检

活体 `/api/health` → ok；`/v3/api-docs` 67 操作 / 62 路径；指纹 `7c7dc3fa…`，与第 282 轮推进后的基线一致。
controller 13 / service 29，与历轮记录一致。

### 代码方向（Backend）—— 两个角度，零缺陷

- **角度一 · 实体 ↔ 库结构逐列对拍**：六个 `@Entity` 的全部 `@Column` 在活体 `information_schema` 中**全部存在**，
  **幻影列 0**。反向的「库有、实体无」共 8 列，逐个查清**全部正当**：
  6 个 `@Id` 字段（`@Column(columnDefinition=…)` 无显式 `name`，JPA 默认映射到字段名——**是我的正则漏判**）；
  `mem_observations.search_vector` 是 PostgreSQL **`GENERATED ALWAYS` 生成列**
  （`information_schema.is_generated = ALWAYS`，JPA 不能写，实体第 118 行专门注释了「read-only from JPA perspective」，
  仅在 `ObservationRepository` 的原生 SQL 中被引用）；`observation_feedback.observation_id` **确实被映射**——
  走 `@ManyToOne` + `@JoinColumn`，**不是** `@Column`（**探针错误**）。
  顺带核实该实体的 Javadoc 记着 P2-24 那个「映射了不存在的 `created_at` 列」的旧坑及其教训，写法准确。
- **角度二 · 仓储层原生 SQL 的表引用**：`nativeQuery = true` 共 **29 条**查询，
  剥掉 `--` 注释并排除函数调用与 CTE 后共 **30 处表引用**，**零幻影表**。

### 文档方向（用户指南，doc round 184）—— DOC-1 已修（双语）

- **DOC-1（P2，已修，双语）** EN / ZH 两份 `DOCKER_README` 各有**唯一一处**裸 `docker run`，
  都设了 `-p 37777:37777` 与 `SPRING_PROFILES_ACTIVE=prd`，**都没有 `-e SERVER_ADDRESS`**，却被当作可用的独立部署方案印出。
  链条逐环核实：`application.yml` 为 `address: ${SERVER_ADDRESS:127.0.0.1}`；**`application-prd.yml` 不覆盖 `server.address`**；
  两个 Dockerfile 均无 `ENV SERVER_ADDRESS`。故容器内进程只监听回环，而端口映射转发到容器**外部网卡**——
  **最难排查的形态：容器正常启动、healthcheck 也是绿的**（走容器内回环，进程确实在听），**宿主机却连不上**。
  `docker-compose.yml:60` 显式写了 `SERVER_ADDRESS: 0.0.0.0`，compose 路径恰好绕过，**只在裸 run 路径暴露**。
  已在两处示例补 `-e SERVER_ADDRESS=0.0.0.0` 并写明原因，围栏 40/40 平衡、`U+FFFD=0`。
- **与 P2-32 的边界**：P2-32 记的是**实现侧**（两个 Dockerfile 该设该变量），此前只在根 `Dockerfile` 的文件头注释被提到，
  **用户指南这一处从未被记过**，本轮属其**文档侧补全**，沿用 P2-11 / P2-22 的「文档层先行更正」先例；
  **Dockerfile 本身按 P2-32 仍记录不修**（本机无 Docker，改完无法验证修复效果）。
- **如实说明**：本机无 Docker，本轮为**静态链路核验，未实跑容器**，不冒充实测。

### 探针自身的四次错误（均写入前拦下）

①把 **JPQL 当原生 SQL**（`PendingMessageEntity` / `completedAtEpoch` 是实体名与字段路径，JPQL 本就如此）；
②正则匹配**任意接收者**，把 `nopLogger` 的 `Debug`/`Info`/`Warn` 当成 `*Client` 的方法；
③把表值函数 `jsonb_array_elements_text(...)` 当成表；
④**没剥 SQL 注释**，注释里的 `refinement` 被判成幻影表。
**结论**：剥注释 + 排除函数调用与 CTE，应作为此类扫描的默认前置步骤。

### 变更检测

改动**仅 `.md`**（两份 DOCKER_README + 三份 drafts），指纹 `7c7dc3fa…` **未变**且与基线一致
→ 按门控**不跑完整验收、不推进基线**。

### 进程清理

本轮未启动任何进程；37777 未被触碰。

## 第 284 轮 — 2026-10-06T04:12:00+08:00

- **轮次**: 284 | **代码方向**: Java SDK | **文档方向**: API 文档（doc round 185）
- **起点**: `HEAD` = `c6850d4`（第 283 轮），工作区干净；仅 37777 在监听

### 健康预检

活体 `/api/health` → ok；`/v3/api-docs` 67 操作 / 62 路径；指纹 `7c7dc3fa…`，与第 282 轮推进的基线一致。

### 代码方向（Java SDK）—— 两个角度，零缺陷

- **角度一 · HTTP 状态码的可恢复性**：Java SDK 不做「状态码 → 异常类」映射，而是把原异常作为 `cause` 抛出
  （`executeWithRetryReturn` 末行 `throw new RuntimeException(operation + " failed: " + describe(lastException), lastException)`），
  `describe()` 自身也沿 cause 链找 `RestClientResponseException`。
  README 的 `## Error Handling` **已经**给出 `statusOf(Throwable)` 辅助方法逐层遍历因果链取状态码——
  **我本要报的「缺口」文档里已有**。仍**实跑验证该文档写法确实可用**（见下方探针）。
- **角度二 · `getObservation` 的跨家一致性**：接口 Javadoc 断言
  「Cross-SDK parity: Go GetObservation(id), Python get_observation(id), JS getObservation(id)」。
  四家逐一核到实现，**全部一致**——Java 返回 `null`、Python 返回 `None`、JS 返回 `null`、
  Go 是 `return nil, nil // Not found — no error, just nil`；四家都是「批量端点 + 取第一条、空则空值」。
  接口 Javadoc 的「or null if not found」**与实现相符**。
- **顺带实测**：`getObservation` 对不存在的 UUID 返回 null 而不抛异常，与文档一致。

### 文档方向（API 文档，doc round 185）—— 零缺陷

- **用本轮代码方向拿到的活体证据直接对拍**：`GET /api/memory/observations/{id}` 在活体是 **405 Method Not Allowed**
  （该路径只有 `delete` 与 `patch`，且各自声明 404/400）。`docs/API.md` 与 `-zh-CN` **两版都只把该路径写成
  PATCH 与 DELETE，没有任何 GET 单条观测的记载——零幻影 GET，文档正确。**
- **端点清单重扫（放宽反引号、排除 changelog 区块）**：英文版 **66 条端点字面量、幻影 0**；
  中文版 69 条、首扫 3 条幻影**逐条查原文后全部证伪**——
  两条落在 `## 更新日志`（2862 行起）的**发布说明表格**里（2886 / 2890 行，描述的是历史文档改动），
  `PUT /api/modes` **全文 grep 都搜不到**，是跨行正则假阳性。**与第 160 轮的判定完全一致。**

### 探针自身的四次错误（均写入前拦下）

①`new CortexMemClient(...)`——该类型是**接口**，须用 `CortexMemClientImpl(CortexMemProperties)`；
②**裸 curl `GET /api/memory/observations/{id}` 得 405** 便疑心 SDK，实为**打错了对象**——
`getObservation` 走的是批量端点 `getObservationsByIds`，单条 GET 与它无关；
③正则只认内联 `METHOD /path`，**没吃中文版的 `#### GET \`/path\`` 反引号形态**，导致中文版只数出 30 条（实际 69 条）；
④changelog 切分用了英文标题 `## changelog`，而中文版标题是 **`## 更新日志`**，切分未生效。
**第 ③④ 条的修法（放宽反引号、按各语言实际标题切分 changelog）应作为此类扫描的默认前置步骤。**

### 变更检测

**本轮零文件改动**（探针文件均在 `/tmp`），`git status` 干净，指纹 `7c7dc3fa…` **未变**
→ 按门控**不跑完整验收、不推进基线**。**零改动轮。**

### 进程清理

本轮未启动任何服务进程；仅跑了两个一次性 Java 探针（编译产物在 `/tmp`）。

## 第 285 轮 — 2026-10-06T04:26:00+08:00

- **轮次**: 285 | **代码方向**: Go SDK | **文档方向**: 设计文档（doc round 186）
- **起点**: `HEAD` = `95bcc40`（第 284 轮），工作区干净；仅 37777 在监听

### 健康预检

活体 `/api/health` → ok；`/v3/api-docs` 67 操作 / 62 路径；指纹 `7c7dc3fa…`，与基线一致。

### 代码方向（Go SDK）—— 两个角度，零缺陷

- **角度一 · 并发安全（Go 特有）**：`httpClient` 结构体只有两个字段——`config *ClientConfig` 与
  `ownsHTTPClient bool`，**构造后不再被写**；全包 `sync.` / `atomic.` / `Mutex` **零命中**，
  即**没有任何需要加锁的可变状态**，并发共享同一 client 无数据竞争。
  **`config` 虽是指针但不构成别名风险**：`NewClient` 是 `cfg := DefaultClientConfig()` 建**全新配置**再逐个套 Option，
  调用方持有的配置不会被别名、事后改动也看不见。
  `Close()` 只在自建 HTTP client 时调 `Transport.CloseIdleConnections()`，**该方法并发安全且可重复调用**，
  与「关闭必须幂等」的要求一致（外部传入的 client 不被误关，字段注释已写明理由）。
- **角度二 · 空 / 截断响应体**：`doRequestJSON` 直接 `json.Unmarshal`，空体会报 parse error——
  但**逐个查过调用点后确认无真实暴露面**：唯一关心此形态的是 `HealthCheck`，而它**显式处理**了
  （非 JSON 体一律报 `unhealthy`），且注释写明设计理由——**「认不出来的健康检查不是健康检查」**，
  因为调用方拿它当 readiness 闸门。与 JS 在第 260 轮做的 truncated-body 修复同源，Go 侧本就严谨。

### 文档方向（设计文档，doc round 186）—— 两个角度，零缺陷

- **角度一 · 设计文档 DDL ↔ 真实迁移对拍**：设计文档只有 3 个 `sql` 块（15.md / 19.md / 20.md）。
  15.md 与 20.md 的 `ALTER TABLE mem_sessions ADD COLUMN user_id VARCHAR(255)` +
  `CREATE INDEX idx_mem_sessions_user_id` 与 **`V15__add_user_id_to_sessions.sql` 逐字一致**，
  活体库亦确认已应用（`user_id varchar(255)` + 索引存在）。
  19.md 那段 `idx_extraction_state_source` 针对的是**尚未实现的 `extraction_state`**（P2-14 已记为
  designed-not-implemented），设计文档写**提案**正当。
- **角度二 · 端点清单对拍（用第 284 轮修正后的探针）**：排除 `99-changelog.md` 后 **68 条端点字面量，
  首扫 10 条「活体不存在」**——**逐条查原文后全部已被裁定**，且裁定批注本身就是对的：
  `20.md` 两条 `/api/ingest/session…`（445 行起的「活体核对」）、
  `19.md` 两条（100 行起）、`7.md` 两条 `allergy_info`（189 行起），
  **都是第 261 轮留下的内联批注**，写明「从未实现、实请求 404」并给出实际端点。
  **逐条复核该批注本身**：所称 5 条实际端点（`POST /api/session/start`、`PATCH /api/session/{sessionId}/user`、
  `GET /api/extraction/{templateName}/latest`、`…/history`、`POST /api/extraction/run`）**活体全部存在**；
  所称 5 条未实现端点**全部不存在**，且实请求复核 `/api/extraction/status`、`/api/ingest/session` **均返 404**。
  → **未经裁定的幻影端点 0 条。**

### 探针自身的一处局限（写入前识别）

端点扫描**不认识文档自带的内联核对批注**，因此把已被逐条裁定并附实请求证据的历史设想又扫了一遍。
**这不是文档缺陷，是探针的判据不足**——正确结论是「未经裁定的幻影端点 0 条」，
而不是「10 条幻影端点」。同类扫描应把「已被内联批注裁定」计入排除条件。

### 变更检测

**本轮零文件改动**，`git status` 干净，指纹 `7c7dc3fa…` **未变**
→ 按门控**不跑完整验收、不推进基线**。**零改动轮。**

### 进程清理

本轮未启动任何进程。

## 第 286 轮 — 2026-10-06T04:41:00+08:00

- **轮次**: 286 | **代码方向**: Python SDK | **文档方向**: 架构文档（doc round 187）
- **起点**: `HEAD` = `ca32be6`（第 285 轮），工作区干净；仅 37777 在监听

### 健康预检

活体 `/api/health` → ok；`/v3/api-docs` 67 操作 / 62 路径；指纹 `7c7dc3fa…`，与基线一致。

### 代码方向（Python SDK）—— 两个角度，零缺陷

- **角度一 · 并发维度（此前只验过凭据维度）**：第 280 轮验过「共享 session 不泄漏 API key」，
  但**从未核过并发**。`requests.Session` 官方声明非线程安全，故查 SDK 是否**主动制造**了额外风险：
  ①构造器 docstring 的三条承诺——「never mutated」「不关闭非自建 session」「共享不泄漏 API key 与 User-Agent」——
  **全部是凭据与生命周期维度，没有一条声称线程安全**，故不构成虚假陈述；
  ②`_request`（唯一请求点）把 `headers` 与 `timeout` 作为**每次调用的参数**传入 `session.request(...)`，
  **不触碰 session 对象**（不写 `session.headers`、不改 `session.auth`），
  即 SDK 的用法就是 requests 的标准形式，**没有叠加任何额外风险**。
  → **非缺陷**（已考量的非发现：`requests.Session` 的线程安全由调用方自行负责，SDK 未承诺也未破坏）。
- **角度二 · 静默失败**：`_fire_and_forget` 的两条失败路径——非重试错误**立刻 `logger.warning`
  并点名操作名与「第 N/M 次尝试」后吞掉**；重试耗尽后**再发一条 `logger.warning`** 同样点名操作名。
  **不存在任何一条失败路径不发出可观测信号**，且方法名与 docstring「error swallowing」、
  README 的「Fails are logged but not raised」三处一致。**非缺陷。**

### 文档方向（架构文档，doc round 187）—— 两个角度，零缺陷

- **角度一 · 全部 `文件:行号` 引用**：两版各 **2 处**，逐条核到源码**精确命中**——
  `SessionController.java:47` 正是 `@RequestMapping("/api/session")`，
  `dto/OffsetPageRequest.java:107` 正是 `public boolean equals(Object o) {`（与文中自述的「instanceof 落在 109 行」相容）。
- **角度二 · 端点数声明**：`ContextController → /api/context/* (7 endpoints incl. /semantic)` 对活体
  `/api/context*` 前缀逐路径数操作数 = **7，精确**。
  `Viewer` 行声明的 **15 methods**，按**文档自己列举的 13 条路径**逐条对活体数操作数：
  **13/13 全部吻合**（`/api/settings` 与 `/api/modes` 各 2 个操作），**合计 15 = 声明值，精确**。

### 探针自身的一次错法（写入前识别）

首版按**前缀**归组统计 Viewer，得到 **16** 而非 15——因为把 `/api/health` 与 `/api/version`
（属别的控制器）算了进去。**正确口径是「按文档自己列举的那张表逐条数」**，
不是「按路径前缀扫」。这与第 255 轮确立的「数字必须连同计数口径一起核对」同源。

### 变更检测

**本轮零文件改动**，`git status` 干净，指纹 `7c7dc3fa…` **未变**
→ 按门控**不跑完整验收、不推进基线**。**零改动轮。**

### 进程清理

本轮未启动任何进程。

## 第 287 轮 — 2026-10-06T05:02:00+08:00

- **轮次**: 287 | **代码方向**: JS/TS SDK | **文档方向**: 运维/用户指南（doc round 188）
- **起点**: `HEAD` = `743db28`（第 286 轮），工作区干净；仅 37777 在监听

### 健康预检

活体 `/api/health` → ok；`/v3/api-docs` 67 操作 / 62 路径；指纹 `7c7dc3fa…`，与基线一致。

### 代码方向（JS/TS SDK）—— 一处缺陷已修（双语）

- **DOC-1 性质的代码文档缺陷（P2，已修，双语）** `doFireAndForget` 的两条失败路径**都**发
  `logger.warn` 并点名操作名与尝试次数，**不存在静默吞异常**——与第 286 轮 Python 的结论同构。
  **但真正的差异在默认 logger**：`resolveConfig` 的默认是
  `{ debug() {}, info() {}, warn() {}, error() {} }`，**空实现**。
  而 JS README 的特性条目宣称「capture errors are **logged** and swallowed after retries」。
  **四家对照（本轮新证据）**：
  ①Python 的默认 logger 是 `logging.getLogger("cortex_mem")`，**实跑探针证实 WARNING 确实输出到 stderr**
  （Python 的 `lastResort` 处理器）——即**Python 默认可见**；
  ②Go 的默认是 `nopLogger{}`、**JS 的默认是空对象**，两家**默认静默**；
  ③**四份 README 里只有 JS 声称「logged」**，Python 与 Go 的同一句都只说「swallow errors」。
  → 即**同一份特性声明，在默认配置下与实际行为相反**。
  已在双语改为「重试耗尽后吞掉错误，只有自己传入 `logger` 时才会输出日志——默认的那个是空操作」，
  与 Go 的措辞对齐。**未改代码**：把默认 logger 改成会输出属**行为变更**（所有用户会突然看到控制台输出），
  按规则记录不单方面实施；**本轮修的是失实陈述**。

### 文档方向（运维/用户指南，doc round 188）—— 一处缺陷已修（双语）

- **DOC-2（P2，已修，双语）** `DEPLOYMENT.md` 的排障表在 `Port 37777 already in use` 行建议
  「**Change `SERVER_PORT`** or stop conflicting service」——**这条建议在默认部署路径上是有害的**。
  **链条逐环核实（非推断）**：
  ①`docker-compose.yml` **无 `build:` 段**，后端用**预构建镜像**
  `${IMAGE_NAME:-ghcr.io/…/cortex-ce:main}`；
  ②`.github/workflows/docker.yml` 的 build-push 步骤是 `context: .` 且**无 `file:` 覆盖**，
  故发布镜像由**根 `Dockerfile`** 构建；
  ③根 `Dockerfile` 的 `HEALTHCHECK` 写死 `http://localhost:37777/api/health`，且**无 `ENV SERVER_PORT`**
  （对照 `backend/Dockerfile` 用的是 `${SERVER_PORT}` 并配了 `ENV SERVER_PORT=37777`）；
  ④照建议改 `SERVER_PORT` 后，应用听新端口而 Docker 仍探测 37777 →
  **把一个完全健康的应用判成 unhealthy**。
  → 与 P2-32 同源，但**结论更强**：P2-32 记的是「根 Dockerfile 的问题」，
  本轮证实它**命中默认部署路径**（compose 用的就是该镜像），因此这条排障建议必须改。
  已双语改为「停止冲突服务。在已发布镜像上**只改 `SERVER_PORT` 行不通**：其健康检查被写死在 37777…」。
  **Dockerfile 与 CI 本身按 P2-32 仍记录不修**（本机无 Docker，无法验证修复效果）。

### 结构校验

四份文件各改 1 行，**中英完全对称**；围栏 14/14 与 94/94 全部平衡、`U+FFFD=0`；
改动表格行**均为 4 个管道 + 行尾竖线**（Markdown 表格格式正确）；doc-growth 退出 0。

### 变更检测

改动**仅四个 `.md`**，指纹 `7c7dc3fa…` **未变**且与基线一致
→ 按门控**不跑完整验收、不推进基线**。

### 进程清理

本轮未启动任何进程。

## 第 288 轮 — 2026-10-06T05:24:00+08:00

- **轮次**: 288 | **代码方向**: Demo | **文档方向**: 架构文档（doc round 189）
- **起点**: `HEAD` = `2acc5cc`（第 287 轮），工作区干净；仅 37777 在监听

### 健康预检

活体 `/api/health` → ok；`/v3/api-docs` 67 操作 / 62 路径；指纹 `7c7dc3fa…`，与基线一致。

### 代码方向（Demo）—— 两个角度，零缺陷

- **角度一 · `CortexSessionContext` 的异常安全（ThreadLocal 泄漏面）**：
  demo 里 **6 对** `begin`/`end` 分布在 5 个文件，**逐对检查异常安全**：
  `IngestController`、`SessionLifecycleController` ×3（含一处**嵌套 try/finally**）、`ChatController`、`ToolsController`。
  **每一对都有 `finally { CortexSessionContext.end(); }`**，即处理器中途抛异常时上下文一定被清理，
  不会残留到同一线程的下一次请求。`begin` 的位置不一致（`IngestController` 在 try 内、其余在 try 外），
  但**两种写法都安全**——`begin` 本身只是 ThreadLocal 赋值，抛异常时尚未绑定任何东西。**零缺陷。**
- **附带核实一处疑似遗漏**：`ChatController` 有两条分支——**带 `conversationId`** 的那条（第 93-104 行）
  **没有**手写 `begin`/`end`，不带的那条才有。一度疑为遗漏，实为**设计**：
  前者把 `CONVERSATION_ID` 传给 **context bridge advisor**（`context-bridge-enabled` 默认 true），
  由其自动 begin/end；后者无 id 故手写。**两条路径都拿得到会话上下文。**
- **角度二 · 文件读取失败的语义**：`FileReadTool.readFile` **捕获所有异常并返回 `"Error: …"` 字符串**，
  故文件不存在时 HTTP 端点拿到的是 **200 + 错误文本**而非 404。这是同一个 `@Tool` 同时服务于
  LLM（需要优雅降级，抛异常会打断 agent 轮次）与 HTTP 端点（语义上应是 4xx）的结果。
  **判为非缺陷**：①**没有任何文档声称它应返 404**，不是失实陈述；
  ②demo README **已把 P1-2 写得极其完整且诚实**（不沙箱化、绑全网卡、不要在共享网段跑、
  并写明「这是 throwaway demo 的有意选择」）；③改响应语义属**对外契约变更**，按规则记录不单方面实施。

### 文档方向（架构文档，doc round 189）—— 两个角度，零缺陷

- **角度一 · 技术栈表的版本声明逐项核实**：Spring Boot **3.3.13** = `backend/pom.xml:10`；
  Java **21** = `<java.version>21</java.version>`；PostgreSQL **16** = 活体 `PostgreSQL 16.8`
  且 compose 固定 `pgvector/pgvector:pg16`；pgvector **0.8** = 活体 `vector 0.8.1`（文档写的是小版本系列，准确）；
  Flyway = `flyway-core` 在依赖里；Maven；Proxy = `proxy/package.json` 依赖 `axios ^1.6.0`。
  **七项全部为真。**
- **角度二 · 「Java 21+ Features Used」的两处强声称**：①`ApiRequests.ToolUseRequest`
  确为 `public record` 且带 `@JsonProperty` 蛇形名（文档片段省略了交错的 `@Schema`，**但未声称逐字**）；
  ②`OffsetPageRequest.java:107-114` 的 `equals` 与文档片段**逐字一致**，
  **109 行正是 instanceof 行**（与文中自述相容），且**确实比较四个字段**。
  文档还自记了「早期版本只显示两个字段、读起来像忽略了 offset 和 sort」的更正历史。

### 变更检测

**本轮零文件改动**，`git status` 干净，指纹 `7c7dc3fa…` **未变**
→ 按门控**不跑完整验收、不推进基线**。**零改动轮。**

### 进程清理

本轮未启动任何进程。
