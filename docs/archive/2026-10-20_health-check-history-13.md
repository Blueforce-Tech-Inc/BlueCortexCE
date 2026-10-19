# Health Check History Archive 13（第 360~361 轮，2026-10-19 ~ 2026-10-20）

> **Archived**: 2026-10-20，第 365 轮压缩迁移，自 `docs/drafts/health-check-task.md` 第 52–213 行。
> **Rule**: 归档文件创建后不得修改。后续历史达阈值时使用新的日期命名归档。
>
> 以下为第 360 / 361 轮轮报告的**逐字原文**（含其间的空行）。
> 迁移原因：本文件行数逼近 1500 行上限（迁出前 **1469 行 / 93130 字节**），
> 而第 362~364 轮的新报告与第 365 轮报告仍需续写。
>
> **留在本区块下方未迁的三段**（不是历史叙述，是**基线的使用前提**，引用 `45/0/1` 或 `25/0/0` 时须一并阅读）：
> ①「第 264 轮起必须一并引用的第二重限定」；②「`accepted_commit` 的含义」；
> ③「第 251 轮起必须一并阅读的限定」。
>
> 本归档实测 **174 行 / 17127 字节**。

> **第 360 轮：Java SDK（代码）+ 设计文档（文档，doc round 260）**：
> **前提已变**——距上一轮 **12 天**（2026-10-07 → 10-19），仓库被另一进程推进了
> **5 个 commit**（`399c478` byte-buddy 1.15.11 pin、`95de35b` docker-e2e build context、
> `53851b4` health-check 第三批压缩把第 312~359 轮归档进 `history-11`、
> `f4c6765` 性能测试基线、`67083a7`+`7149578` fix-progress 记录），
> 且**第 359 轮报告已被归档**，`patrol-state.json` 却仍停在 359 轮。
> 故本轮**先重建状态认知**再动手，未凭旧认知行动。**/api/health** → `{"status":"ok"}`，
> 37777 由 pid **10078** 在跑（**不是**上一轮的 62714 —— 另一进程起的）。
> **代码方向（Java SDK）**：Java SDK 近 12 天**零提交**；`mvn -o clean test` 基线
> **196 全绿**（client 143 + spring-ai 46 + starter 7），BUILD SUCCESS。
> **P2-112（记录不修）**：`cortex-mem-starter` 的 `base-url` **一旦是空串就让整个 Spring 应用启动失败** ——
> `@ConditionalOnProperty(name="base-url")` 把「属性存在但为空」判为已设置，于是
> `CortexMemClientImpl:61` 的 `baseUrl must not be null or blank` 直接中止启动。
> 受控实验（临时 `ApplicationContextRunner` 探针 + **真实 `application.yml`** 经
> `YamlPropertySourceLoader` 装载，探针编译到 `/tmp`，仓库零改动）：
> `base-url: ${CORTEX_MEM_URL:}` / `base-url:`（YAML null）/ `base-url: ""` / `base-url: "   "`
> **四种写法全部** `STARTUP_FAILURE`；而**不设**与 **`base-url=false`** 都**不激活**、应用正常启动 ——
> **同样是「没有有效 URL」，两种安全、一种致命**，且致命的那种恰是生产最常见的
> `${ENV:}` 写法，错误信息还不指向任何配置项名。
> **同一仓库的后端对完全相同的模式早已显式处理**：`SpringAiConfig.java:32,42-46`
> 用同样的 `@ConditionalOnProperty(name="api-key")`，却判空后 `log.warn` 并 `return null`，
> 而后端自己的 `application-dev.yml:8` 正写着
> `api-key: ${SPRING_AI_OPENAI_API_KEY:${OPENAI_API_KEY:}}` —— **一个模式已按「空即跳过」处理，另一个没有**。
> 那条 blank 校验来自 `cf78f5a`，标题是
> `add defensive validation for **retry config and timeout properties**` ——
> **baseUrl 不在该提交意图里**，是被顺手加的。**范围已钉死不夸大**：引用类型属性
> （`connect-timeout`/`read-timeout`/`retry.backoff`/`project-path`）绑 null **保留默认值，安全**；
> 基本类型（`retry.max-attempts`/`default-experience-count`）绑 null 崩，但那是
> **Spring Binder 对基本类型的通用行为**，非本 SDK 独有；`base-url` 才是本 SDK 特有的那一个。
> **不单方面实施**（修法三条互斥：照搬后端先例 / 保留失败但给出可定位错误 / 回落默认值，
> 且改的是**自动装配的激活条件**，属对外可见行为变更）。
> **顺带复认 P2-88**：7 组属性的完整 bean 矩阵实测与该条表格**逐格一致**
> （`retrieval-enabled=false` ⇒ `MemoryRetrievalService`/`CortexMemoryAdvisor`/
> `CortexSessionContextBridgeAdvisor` 三者皆灭，而 `CortexToolAspect` 仍在）。
> **文档方向（设计文档，doc round 260）**：`docs/drafts/cortex-mem-integration-capture-analysis.md`
> （145 行，**此前零覆盖**）逐条核完 —— §1 五行的端点/类名/方法名/Auto? 判定、
> §2 两个配置项、§6 四处实现位置、§7 的 `-Plocal` profile 与
> `examples/cortex-mem-demo/e2e/run-e2e.sh`（**真实路径确在**，非幻影）**全部为真**；
> 其中 `POST /api/session/start` 与 `/api/prompts` 两个**曾被我怀疑是幻影端点**，
> 经**活体请求**判定为真（`/api/session/start` 返 400；返 404 的是 `/api/ingest/session-start`）——
> **两条怀疑全数落空**。**唯一失实**：§4.1「**Config**: `cortex.mem.capture-enabled`」
> **配置项名有误**（用户提示捕获的实际开关是 `cortex.mem.capture-user-prompt-enabled`，
> 由 `CortexMemAutoConfiguration:101` → `Builder.captureEnabled()` →
> `captureUserPromptIfActive()` 的 `if (!captureEnabled) return;` 确认），
> 且**与该文件自己 §2 矛盾**；因 §4.1 属「改进机会」里的历史提案（§3.1/§5/§6 均已标已实现），
> 按该文件体例**加「已实现后的更正」注记**、**不改写历史**。
> **另给 P2-88 补了一条证据**：这份设计稿 §1 把 Tool use 标 `✅ Yes`、Notes 写
> 「AOP intercepts `@Tool` methods when `CortexSessionContext` is active」——
> **正是被 `retrieval-enabled` 连带关掉的那一环**，同样未提；其 §2 更用了与双语 README
> **完全相同**的「independently」措辞，故修法一旦定下来**两处都要同步**。
> **变更检测**：指纹 `903928c8…` / **2218** 与基线**完全一致** —— 本轮**零代码改动**
> （P2-112 只记录不实施），**代码指纹未变化，跳过重复测试验收**，基线**不推进**。
> **压缩**：findings 字节余量仅 3173B，迁出 **4 条**已修条目
> （`P2-103`/`P2-104`/`P2-110`/`P2-111`）到 `2026-10-19_backend-review-evidence-58.md`，
> 1380 行 / 146827 字节 → 1353 行 / 142466 字节 → 写入 P2-112 后 1409 行 / 147615 字节。
> **终验六项全过**：四个归档块**逐字**等于删除前快照的对应切片（4/3/6/6 行）、去向行 0、
> 指针 4 与归档块 4 配对、链接全部可解析、归档头声明的 **39 行 / 8121 字节**与实测一致、
> 未迁出的 98 个标题全部仍在。**四条并不相邻**，故切割区间**不得按升序复用**
> （每次替换让后续区间整体前移），本批改用**单遍顺序重建**。
> **本轮仪器错误四处，全部由验证而非通读发现**：
> ①**探针首版漏了 `ConfigurationPropertiesAutoConfiguration`** —— 绑定后处理器未注册，
> 于是无论设什么 `base-url`，读回的全是**默认值**；**差点据此断言「绑定根本没工作」**，
> 而真实 Spring Boot 应用里它是工作的。补上该自动配置后 `retry.max-attempts=5`、
> `retry.backoff=2s`、`project-path`、`default-experience-count` 全部正确绑定。
> ②**注入上下文里的 `AGENTS.md` 是过时版本** —— 系统提示那份把 `POST /api/ingest/session-start`
> 列为端点，而**磁盘上**的 `AGENTS.md:140` 写的是正确的 `/api/session/start`；
> 差点据注入内容去「修正」一个没坏的文档。**一切以磁盘为准**。
> ③**上轮 `patrol-state.json` 的 `nextDirection` 写错**（第 359 轮是 Backend，
> 按轮换顺序下一个应是 **java-sdk**，却写成 `python`）—— 已更正。
> ④**归档索引的管道检测器数的是所有竖线字符**，把已转义的也算进去，
> 于是一行第 267 轮就转义正确的表格被误报成损坏，**差一次编辑就改坏一张健康的表**；
> 正确口径是数**未转义**管道（改后直方图 `{5: 123}`、零错配零孤儿）。
> 另有一条**写前断言再次拦下**：本批归档索引的描述文本里原写了「反引号包裹的竖线」这一符号，
> 未转义管道计数因此为 6 而非 5 —— GFM 在代码 span 内照样切开单元格，与 P2-99 同型。



> **第 361 轮：Go SDK（代码）+ 架构文档（文档，doc round 261）**：
> **前提**：距上一轮 12 天的状态重建已在第 360 轮完成，本轮承接；Go SDK 与架构文档近 12 天**零提交**。
> **/api/health** → `{"status":"ok"}`，37777 由 pid **10078** 在跑（另一进程起的，非上轮进程）。
> **代码方向（Go SDK）**：**P2-113（记录不修）** —— **四家 SDK 的 fire-and-forget 失败「默认可见性」是 2:2 分裂：
> Python 与 Java 默认会记日志，Go 与 JS 默认完全无痕。**
> `go-sdk/cortex-mem-go/client_impl.go:91-96` 的 `nopLogger` **四个方法全是空实现**，
> `:107` 的 `DefaultClientConfig()` 默认就是它；而 `doFireAndForget`（`:393-441`）在**三处**失败分支上
> 调用 `c.config.Logger.Warn(...)` —— **默认配置下这些警告全部落进空实现**。
> JS 同型（`client-options.ts:86` 的默认 logger 同样是空对象，`client.ts:756-779` 调 `logger.warn`）；
> Python 用**标准 `logging`**（`client.py:186-211` 的 `logger.warning`），Java 用 SLF4J `log.warn`。
> **实测（本轮亲跑，非静态推断）**：临时 Go 探针（`/tmp` 独立 module + `replace` 指向本仓库 SDK，
> **仓库零改动**）指向**关闭端口** `127.0.0.1:9` 使请求层必然失败 ——
> **场景 A（默认 logger）**：`RecordObservation` 返回 **`err=<nil>`** 且**零输出**（重试 3 次全败仍一声不吭）；
> **场景 B（同一调用装 `slog` logger）**：**3 条 WARN** 清晰可见（`failed, retrying` ×2 + `failed after retries` ×1）。
> **场景 B 即阳性对照**，它证明探针**能**看见输出，故场景 A 的「无输出」为真、**不是探针坏了**。
> **Python 对照实跑**：同一场景输出 **2 条** WARNING，返回 `None`。
> 由此确定：**两家的 fire-and-forget 对调用方都是「永远成功」**，差别**只在失败是否留痕**。
> **影响面诚实界定**：只在**请求层失败**时才有影响；**不是**「后端收下却没落库」——那种情况
> **四家都看不见**（实测：后端 `POST /api/ingest/tool-use` 返回 `200 {"status":"accepted"}` 后
> 异步加工失败、库里 0 条，而 `go-sdk` 的 `examples/basic` 照样打印
> 「Observation recorded successfully」并以 **0** 退出 —— 那是**已知环境限制**，LLM 密钥失效，与本条无关）。
> Go README `:116` **双语都记着** `WithLogger` 默认 *(nop)*，故「默认静默」本身是**有记载的自觉设计**、
> 本条**不是失实陈述**；但**后果未记载**，且 **Python README 明确告诉用户「the SDK logs it」**，
> **四家里只有 Go/JS 默认无痕这一差异在 Go README 里从未被提及**。
> **不单方面实施**（改默认 logger 会让原本安静的库突然产生输出，属对外可见行为变更；
> 补文档落在 SDK README 方向，不属本轮代码方向）。
> **复认 P2-90 的前提**：`go test -count=1 -json` **逐个 module 逐个包**统计得
> **顶层 310 + 子测试 53 = 363**，与该条一致（根 `.` 236、`./dto` 67、eino 8、genkit 13、langchaingo 12、
> `examples/http-server` 27；另 4 个 example module **零测试文件**）。
> **复认 P2-36**：三个适配器的数值校验仍不一致（仅 `genkit/retriever.go:115` 有 `count <= 0` 兜底，
> eino 与 langchaingo **完全无校验**）。
> **文档方向（架构文档，doc round 261）**：**P2-114（已修，零行为变更）** ——
> **根 README 的架构图把薄代理标为 `(Express)`，而同一仓库的 `ARCHITECTURE.md` 与中文版
> 早在 2026-10-01 就已改正，根 README 是那次漏改的那一份。**
> 证据：`proxy/package.json` 的 `dependencies` **只有 `axios`**，`proxy.js` 用 Node 内置
> `http.createServer`（`:328`），**全文 `express` 出现 0 次**；**活体验证**本轮亲启 `node proxy.js`，
> 启动横幅自报 `Listening: http://127.0.0.1:37778` / `Java API: http://127.0.0.1:37777`，
> 横幅与日志里 `express` **0 次**（验毕已停，37778 恢复空闲）。
> 归档 `2026-10-01_health-check-history.md:154` 逐字记载那次修复的范围是
> 「ARCHITECTURE.md/ZH 两处…（**中英四处同步**）」，磁盘现状与该记载一致
> （`ARCHITECTURE.md:86-87` 已作 `(Node.js)`、`:282` 记 `Dependencies (axios)`；
> `ARCHITECTURE-zh-CN.md:85` 同为 `(Node.js)`、`:941` 技术栈表为 `Node.js/axios`）——
> **即「修掉一个说法 ≠ 修掉复述它的每个说法」的又一次复发**。
> **顺带修正的第二处**：原图把 `wrapper.js` 与 Thin Proxy 画成**串联两框**，而实际二者是**同一层里的两个组件** ——
> `wrapper.js` 是 hook 调用的 CLI 入口、**直连后端 37777**（`wrapper.js:84` 的 `javaUrl` 默认值），
> `proxy.js` 是**可选**的本地聚合 HTTP 服务（37778）；**阳性对照**：`wrapper.js` 里 `37777` 命中 **12** 次，
> `37778` 与 `proxy.js` 各 **0** 次（同一 grep 看得见前者，故两个 0 是读数而非探针失灵）。
> `ARCHITECTURE.md:166-171` 的画法（两者并列于同一 Thin Proxy 框内、proxy.js 标 optional）是**正确体例**，
> 本轮按其对齐；**未改 ASCII 框体**（重画会让相邻框对齐全部位移，收益不抵排版风险），以文字补足事实。
> **本轮一并复验为零缺陷的相邻断言**：根 README 的 hook 配置（第 131~134 行）与 `wrapper.js --help`
> 输出**逐条吻合**（`commandToHookMap`，`wrapper.js:1489-1494`）；`/stream` 端点**确实存在**
> （`StreamController` 的 `@RequestMapping("/stream")`，活体 spec 亦有）；badge 的 Spring Boot 3.3
> 与 pom 的 3.3.13 相符；「Java 21」与 pom 的 `release 21` 相符。
> **变更检测**：指纹 `903928c8…` / **2218** 与基线**完全一致** —— 本轮**零代码改动**
> （P2-113 记录不实施，P2-114 只改 `.md`），**代码指纹未变化，跳过重复测试验收**，基线**不推进**。
> **压缩：本轮连做四批**，因 findings 的字节余量已近于零 ——
> ①迁出 8 条已修条目到 `-59`；②按第 254 轮**经用户决策**的规则迁出 **42 条**待决条目的
> `- **Scope / Evidence**` 段到 `scope-evidence-9`（**Problem 与 Status 一律留在工作文件**）；
> ③按第 292 轮「零信息损失、只缩短显示名」规则缩短 27 条已修条目的 Status 行
> （**归档链接与轮号逐条不变，已断言**）；④本轮新写的三条整体迁出到 `-61`（正文逐字保留）。
> **已结案条目早已全部归档、待决条目的 Scope/Evidence 亦已迁尽**，余下的是各条目的
> **决策推理正文**，按规则**不得迁出** —— 故压缩手段已到尽头，
> **根本解是 P2-115 所记的上调阈值**（建议 2000 行 / 200000 字节，比例与第 293 轮一致；
> **实现无需改脚本**，两个阈值本就是环境变量），**待用户决策**。
> **本轮仪器错误九处，全部由验证而非通读发现**：
> ①**Go 测试计数脚本首版重复计数** —— 在 `dto` 目录跑 `go test ./...`，而 dto 不是独立 module，
> 模式又匹配回整个根 module，于是两行都显示 303；**这正是 P2-90 当初警告过的坑**，
> 改为**逐 module 逐包**指定后才得到正确的 310+53=363。
> ②**把「basic example 观测不落库」误当成 SDK 缺陷的边缘** —— 查库后确认 0 条，
> 而全库最近一条观测是 **11.8 天前**的，根因是后端异步加工所依赖的 LLM 密钥失效（**已知环境限制**）。
> ③**两处「疑似幻影端点」怀疑落空** —— `POST /api/session/start` 与 `/api/prompts` 经**活体请求**判定为真
> （前者返 400；返 404 的是 `/api/ingest/session-start`）。
> ④**缩短 Status 行时会把「⏸ 待与 P2-83/P2-86 同批清扫」这个已被推翻的旧状态引用一起丢掉** ——
> 断言拦下，加「含 ⏸ 的行不缩短」的守卫。
> ⑤**缩短脚本的验证逻辑自身有 bug**（`old_links` 统计了所有匹配行而 `kept_links` 只统计实际缩短的行）——
> 断言拦下，两边改用同一判定。
> ⑥**`/tmp` 被清空**，上一轮的迁移脚本连同 `/tmp/p359/*` 一并消失，只能重写。
> ⑦**指针行写得太长**，42 条 Scope 指针原为 111 字节/条，几乎没省下空间（149767 → 149667），
> 改短后（49 字节/条）才真正腾出 1722 字节。
> ⑧**wrapper.js 命令分发的两处误判** —— `session-start` 的 `case` 出现在 `case 'cursor':` 之后，
> 一度像是 cursor 子命令；实为「无 stdin 事件」的兜底分支，真正主路径是 `commandToHookMap`。
> ⑨**注入上下文里的 `AGENTS.md` 仍是过时版本**（第 360 轮已记，本轮复核依然如此）。
