# 文档审查与改进 — 任务指令

> **Purpose**: 定义文档准确性、完整性、双语一致性和轮换审查规则。
> **Updated by**: 定时项目维护任务。
> **Update rule**: 每轮只审查一个方向；结果写入统一健康检查报告；历史达到增长阈值时归档。

## Current Review Rotation

- **轮换顺序**: API 文档 → SDK README → 设计文档 → 架构文档 → 用户指南
- **历史**: 第六十九–八十一轮见 [`2026-10-03_doc-review-history-69-81.md`](../archive/2026-10-03_doc-review-history-69-81.md)；
  第八十二–九十三轮见 [`2026-10-03_doc-review-history-82-93.md`](../archive/2026-10-03_doc-review-history-82-93.md)
  第九十四–一百零七轮见 [`2026-10-03_doc-review-history-94-107.md`](../archive/2026-10-03_doc-review-history-94-107.md)
  第一百零八–一百一十九轮见 [`2026-10-03_doc-review-history-108-119.md`](../archive/2026-10-03_doc-review-history-108-119.md)
  （四批均因本文件逼近 102400 字节压缩阈值而移出，内容逐字保留）
- **历史**: 第一百三十四–一百五十六轮见 [`2026-10-04_doc-review-history-134-156.md`](../archive/2026-10-04_doc-review-history-134-156.md)（本轮因逼近 102400 字节阈值而移出，内容逐字保留）
- **最近完成**: 设计文档（2026-10-04 一百五十七轮，一项，十一处）。**DOC-1（已修）** **一个端点路径变量在同一份文档里有两种写法——一处对、五行之外错，而错的那些散在十个文件里。** `22.md` 写 `PATCH /api/session/{id}/user`，而**活体映射是 `{sessionId}`**；**同一文件第 118 行与第 121 行又写对了**，即该文档自相矛盾。**按断言清扫**把同一处替换在**十个文件、十四处**全部找齐并更正（`0.1.md`×2、`0.3.md`、`00-quick-ref.md`、`8.md`、`9.md`、`22.md`、`23.md`×2、`24.md`×2、`25.md`、`99-changelog.md`、`phase-3-design-walkthrough.md`）。**刻意未动** `/api/memory/observations/{id}`——**那个端点的变量名确实是 `id`**，按断言清扫不等于按前缀清扫。改后全文统一为 `{sessionId}` **19 处**、`{id}` **0 处**，端点引用对拍**零幻影**。**本轮其余部分核实为真**：7 处 `Xxx.java:行号` 引用逐条精确命中（这一方向正是第 246 轮抓出「行号随被引源码同批改动而失效」之处，故仍逐条核对）。**Code direction（Python SDK）修掉第 251 轮的同族两处** 第 251 轮修了 `build_icl_prompt(max_chars=None)` 后，本轮把 client 里**每一个数值参数**扫了一遍同族隐患：六个里**四个已经安全**（它们用真值判断，传 None 正常返回），**两处不安全的**——`retrieve_experiences(count=None)` 与 `get_extraction_history(limit=None)` 各抛一个**裸 TypeError 逃出 SDK**，而不是 SDK 自己的 `ValidationError`。**四家对照**：Java/Go 是 primitive（None 不可能）、JS 用 `!== undefined` 显式防住、**只有 Python 会炸**。**过程中被既有测试当场抓住一次自我犯错**：我第一版改成 `if count:`（照搬同文件其它处的真值写法），`test_retrieve_experiences_drops_negative_count` **立刻失败**——**负数在 Python 里是真值**，`-1` 会被发上 wire；**而那条测试的 docstring 早就写着**「A truthiness test is not enough: every non-zero int is truthy in Python.」**警告一直躺在仓库里，我读到了那个方法却没读它的注释。**最终形式与第 251 轮一致（`is not None and > 0`），两处都补了「真值判断在这里是错的」注释。**+2 条测试**（439 → **441**），**双向注入**回退后**恰好 2 条失败**，两条既有对照（负数丢弃、`count=1` 仍上 wire）**两种状态下均不失败**；活体复核 `count=None`/`limit=None` 均 OK、`count=-1` 正确丢弃、**`limit=-1` 正确抛 ValidationError**。**压缩**：第 254 轮规则要求保留 Problem 与 Status，但**无 ⏸、无待决问题的条目没有推理可留**，故把 **P2-54 / P2-50 / P2-52 / P2-53** 四条**无条件已解决**条目的 Status 段整体迁出（**Problem 段全部留在工作文件**），findings **1029 → 989**。
- **最近完成**: 架构文档（2026-10-04 一百五十八轮，三处，**全在受版本控制的 `AGENTS.md`**）。**DOC-1（已修）** **一张「不可修改的硬契约」表，把字段名挂在一个**从来就没返回过该字段的端点**上，而真正返回它的端点**就在同一张表里、却没被列出**；表下面那条用来自查的命令，会返回零命中、看起来像「没人用，可以改」。**换角度的理由**：第 153 轮已把控制器图 / 服务图 / 事件类 / 迁移树 / LLM 配置表逐条核实为真（13 控制器、双语各 31 服务、16 迁移、V9/V10 正确缺席），故本轮改扫**架构方向里尚未被核过、且直接约束后续所有改动**的部分——`AGENTS.md` 的 WebUI 子模块 API 契约表。三处断言为假：

  1. **`/api/context/generate` 的 `updateFiles` —— 危险的那一类。** 活体 `POST /api/context/generate` 只返回 `{context}`，**源码定性更彻底**：`ContextController.java:293` 返回 `new GenerateContextResponse(context)`，而该 record（`ApiResponses.java:76-80`）**只有 `context` 一个字段**，故它从来不可能返回过 `updateFiles`。真正产出该字段的是 **`GET /api/context/inject`**（`ContextController.java:154` 的 `ContextInjectResponse`，`ApiResponses.java:26-32`，其 `@Schema` 明写「MUST stay camelCase for proxy.js compatibility」），而 **WebUI 的 6 处调用（`cli/handlers/user-message.ts:30`、`cli/handlers/context.ts:43`、`services/transcripts/processor.ts:357`、`worker-service.ts:262`、`CursorHooksInstaller.ts:107/408`）全都打的是 `inject`、没有一处打 `generate`**。危害是具体的：一个想改名的 agent 查表，看到表里写的是**另一个端点**，于是判定「可以改」——**表保护的是一个虚构，暴露的是真身**。
  2. **`/api/settings` 的 `observation_types/concepts` —— 字段根本不存在。** 活体返回 **20 个 `CLAUDE_MEM_*` 键 + `modeName` + `modeDescription`**，无 `observation_types`、无 `concepts`（语义相近的是 `CLAUDE_MEM_CONTEXT_OBSERVATION_TYPES` / `..._CONCEPTS`，属第一张表的行）。该行**什么也没保护**。已替换为该端点**真正以 camelCase 产出、且 `webui/src/` 与 `proxy/` 均零引用**的 `modeName`/`modeDescription`（`ViewerController.java:450-451` 直接 `response.put`）——**替身行同样是实测的，不是推断的**。
  3. **自查命令扫错了目录。** 文档给的 `grep -rn "hasMore\|updateFiles\|observationTypes" webui/src/` 里，**`updateFiles` 在 `webui/src/` 零命中**（消费方是 `proxy/proxy.js:293`，在 webui 树之外）——**照文档执行会得到一个「安全」的假象**。已改为同时扫 `webui/src/ proxy/`，并注明原因。

  **同表另外两行核实为真、予以保留**（按断言清扫，非按文件）：`hasMore` 在 `/api/observations`、`/api/summaries`、`/api/prompts` **活体三处均返回**，且 `webui/src/ui/viewer/hooks/usePagination.ts:8/20/42` 与 `App.tsx:145` 确有引用；`/api/session/start` 的 `updateFiles` 活体确在（响应键 `session_id, context, updateFiles, session_db_id, prompt_number`）；`/api/settings` 的 `CLAUDE_MEM_*` 活体确在。第二张表第一行 `/api/modes` 的**理由也成立**——`ContextConfigLoader.ts:22` 与 `sdk/parser.ts:57` 两处均经 `ModeManager.getInstance().getActiveMode()` 这个**本地单例**取 `mode.observation_types`，**不从 API 响应读**，故「可安全改名」的判断准确（顺带把该行字段名补全为 `observation_types/observation_concepts`，原文写的 `concepts` 在活体里是 `observation_concepts`）。**架构文档其余部分本轮新角度核实为真**：`§Current Limits` 的具体断言逐条落到源码——`RateLimitService` 的 10 请求/60 秒（`claudemem.rate-limit.max-requests:10` / `window-seconds:60`）、**唯一调用点** `IngestionController.java:132` 守卫 `POST /api/ingest/tool-use`、key 确为 `tool-use:{contentSessionId}`，与该表「只是估算、不由后端强制」的措辞完全一致；MCP `save_memory` 经 `ClaudeMemMcpTools.java:238` **直连 service/repository、不走 HTTP**，故它与 REST 侧的 404 无关、工具本身未坏。**记录不修的能力缺口**：**四家 SDK 无一暴露 `platformSource`**，而后端在 `/api/observations` 接受该参数、WebUI 也确实向 `/api/context/inject` 传它；更进一步，**没有任何一家 SDK 调用 `/api/context/inject`**（即真正产出 `updateFiles` 的那个端点）。四家一致缺失 = 产品能力缺口而非某家缺陷，按既有先例记录不修。**Code direction（JS/TS SDK）**：逐项核实为真——`isRetryable` 的 429/502/503/504 + 传输层错误集合与 Go `IsRetryable` **完全一致**；8 个数值参数**全部**用 `!== undefined` 显式守卫（第 234、263、290、291、478、483、886、887 行），**四家里最干净，第 251/256 轮的 None 隐患在此无对应物**；10MB 上限先读 Content-Length 再按字节兜底并对 ASCII 留了免费早退；响应解析层刻意不给缺失字段补 0；**8 个调用端点的 query 参数名与活体 OpenAPI 逐条对拍、零不匹配**（首版探针把路径变量也算进「后端独有」，是探针自身的错，已识别后重做）。**唯一真缺陷**：`retrieveExperiences` 头上**重复了两遍的 Javadoc**（`client.ts:208-214`）——它会进 `.d.ts`、进每个编辑器的悬浮提示。已删其一，`tsc --noEmit` 干净、**250/250** 全绿。**变更检测**：改了 `.ts`，指纹 `674bde42…` → **`0b8d9c71…`**，按门控跑完整验收：回归 **45/0/1**、`EXTRACTION_ENABLED=true` 验收 **25/0/0**（带 P2-46 限定；Test 15 再次确认后端对负 `limit` 是**钳制**而非拒绝，这也正是本 SDK 在客户端先抛 `ValidationError` 的原因）。
- **最近完成**: 运维/用户指南（2026-10-04 一百五十九轮，**核实无误、未改**，附**两个被实测推翻的自信假设**）。**DOC-0（本轮零改动）** 取的新角度是**环境变量端到端对拍**（此前几轮扫的是脚本引用、jar 版本号、数字与日期）。**结论之所以值得写下来，正因为有两个我相当有把握的假设被实测推翻了**——按经验，若不先测就会把**正确文档改成错误文档**，这与本项目一直在清理的「幻影端点」恰好是镜像：

  1. **「限流三变量的写法不对」——错的是我。** `docs/DEPLOYMENT.md:563-565`（中文版 `:548-550`）把 `CLAUDEMEM_RATE_LIMIT_MAX_REQUESTS` 等三条列为环境变量，**我的推断是**：Spring relaxed binding 对环境变量「点→下划线、连字符去掉」，故正确写法应为 `CLAUDEMEM_RATELIMIT_MAXREQUESTS`，而文档写的这份**绑不上、会静默失效**。**实测 A/B（隔离实例 37790，同一序列数第几次 tool-use 返 429）**：`CLAUDEMEM_RATE_LIMIT_MAX_REQUESTS=3` → **第 4 次 429**；`CLAUDEMEM_RATELIMIT_MAXREQUESTS=3` → **同样第 4 次 429**。**两种写法都生效，文档是对的。**
  2. **「`.env.dev` 的 embedding base URL 少了 `/v1/embeddings`」——同样错的是我。** `docs/DEPLOYMENT.md:639` 写 `https://api.siliconflow.cn`，而快启动与 CLAUDE.md 用带路径的长形式。**全仓计数后结论相反**：`backend/src/main/resources/application-dev.yml:15`（**后端自己的默认值**）、`backend/.env.example:30`、`scripts/docker-compose-test.sh:153`、`scripts/docker-e2e-test.sh:165`，以及 `README`/`README-zh-CN`/`CONTRIBUTING`×2/`DEVELOPMENT`×2/`API`×2/`ARCHITECTURE`×2/DOCKER_README×2/`memory/2026-03-19.md` 共 12 份文档**全部用短形式**；**长形式只出现在 gitignored 的 `CLAUDE.md:210`**——即那个已记录 9 条幻影端点的文件。**故 §5.8 与后端自身默认值一致，无误。**

  **其余部分核实为真**：§2.1 快速启动 `.env` 的 **12 个变量全部**被 `docker-compose.yml` 真实引用（**零幻影**）；compose 额外引用的 9 个是快速启动有意省略的；§5 参考表覆盖了 compose 实际读取的 **11 个变量中的 10 个**，唯一缺的 `IMAGE_NAME` 在 `DOCKER_README.md:68`（及中文版 `:68`）有专门的环境变量表、且 `DEPLOYMENT.md:139` 内联展示了其默认值，属合理分工而非缺失。**首版探针自身出错一次并已识别**：「后端读取、文档未声明」一栏刷出 33 条 `claudemem.mode`、`app.memory.refine-enabled` 之类——**那是 Spring 属性名，不是环境变量名**，两者在 relaxed binding 下不是一回事（且 `DB_NAME` / `POSTGRES_*` 是 compose 变量、`SPRING_PROFILES_ACTIVE` 是 Boot 内置），**该栏整体作废后重做**。

- **最近完成**: API 文档（2026-10-04 一百六十轮，**核实无误、未改**，含**三处探针假阳性**与**一处主动不下的结论**）。**DOC-0（本轮零改动）** 新角度 = **端点清单双向对拍 + 字段级对拍**。①**端点幻影**：首版抓到 3 条幻影（`DELETE /api/memory/observations`、`PATCH /api/memory/observations`、`PUT /api/modes`），**逐条查原文后全部证伪** —— 前两条来自 changelog 里「Enriched English **PATCH** /api/memory/observations section」「Fixed EN **PATCH**/DELETE /api/memory/observations path variable」这类**描述历史修改的散文**（正文里真正生效的两条在 `API.md:564` 与 `:599`，路径变量是 `{id}`，正确）；`PUT /api/modes` **全文 grep 根本搜不到**，是正则跨行产生的假阳性。**排除 changelog 区块后重扫：双语各 66 条端点引用，幻影 0，且活体端点无一在文档中缺席。** ②**字段级对拍**：取全局最近一条真实观测（34 个字段）与 `API.md` 的字段表逐条比对，**仅三个 `embedding_768/1024/1536` 未列**——**而这恰恰是正确的做法**（公共 API 参考不该暴露 pgvector 存储列），**不作为缺陷**。**并且本轮主动放弃了一个诱人但无证据的结论**：三个向量列确实出现在响应里，故我一度想记「每条观测都要序列化三份向量」；**实测本行三者皆为 `null`**（`psql` 本机不可用、无法统计全表非空率），**证据不足以支撑该结论，故不写**——这正是「宁少勿错」的落地。**Code direction（Java SDK）立 P2-57（记录不修）**：**四家 SDK 里只有 Java 的默认 base URL 用主机名**（`CortexMemProperties.java:12` 为 `http://localhost:37777`），而 Python / Go / JS **全部**是 `http://127.0.0.1:37777`；**后端自身** `application.yml:3` 写的是 `address: ${SERVER_ADDRESS:127.0.0.1}`、**只绑 IPv4 回环**，`lsof` 实测监听项即 `TCP 127.0.0.1:37777`，直连 `[::1]:37777` **失败**。本机 `localhost` 解析顺序实测为 **`::1` 在前、`127.0.0.1` 在后**，今天能通**只是因为客户端做了地址族回退**。**决定性对照（JDK 自带 HttpClient）**：默认参数下两者都 200；加 `-Djava.net.preferIPv6Addresses=true`（**有文档、用户在双栈部署中确会设置**）后，`localhost` **抛 ConnectException**、`127.0.0.1` **仍 200**——**用户什么都没改，行为就翻转了**。**不修的理由**：改一行即可且方向明确，但改的是**已发布 SDK 的公开默认端点**，属对外契约变更，按既定规则记录不单方面实施。**本轮最该记的是探针自己出错**：首轮 grep 命中的是 `client.py:47` 的 **Javadoc 用法示例**（写 `localhost`）而非 `:72` 的**真实默认值**（`127.0.0.1`），一度得出「Java 与 Python 是 2:2 分裂」的**错误结论**；改用**排除注释行**的探针后才看清真实的 **3:1**——**而正是这个错误一度会把一条 Java 专属缺陷误记成跨家问题**。**同文件另记一处待该方向清扫**：`client.py:47` 的示例与 `:72` 的默认值矛盾（示例教用户写的值与实际默认值不是同一个），属零行为变化的描述修正，但落在 Python SDK 方向，本轮不越界。

- **最近完成**: SDK README（2026-10-04 一百六十一轮，两处，**一处数字陈旧、一处数字被误判**）。**DOC-1（已修，双语）** **Python SDK 的测试数停在 435，而实际已经是 441 —— 差的 6 条正是最近三轮修 bug 时加的。** 按第 255 轮的纪律（**数字必须连同计数命令一起核对**）逐家实测：`pytest tests/ -q` → **441 passed**；逐文件 `214 client + 140 DTO + 87 demo`。README 原文写「435 unit tests（211 client + 140 DTO + 84 demo）」，**算术自洽但整体陈旧** —— 三轮分别加了 2 条（第 251 轮 `build_icl_prompt`）、3 条（第 252 轮 demo 的 `limit` 校验）、2 条（第 256 轮两处 `None`），**每次都改了测试却没回头改这个数**。中英双语各一行，已同步为 **441（214 + 140 + 87）**。**同区域另两条断言核过之后决定不动**：①「25 methods」—— AST 枚举出 `CortexMemClient` 有 **26** 个公开方法，但第 26 个是 `close()`，而**紧接着的下一条 bullet 就写着 context manager**，故 25 指 API 方法数，**准确**；②第 183–184 行「the Java SDK retries **10** of its 25 methods」—— 我一度以为它错了（探针数出 11），**两次探针都错**：一次是方法切分把**文件最后一个方法**的切片延伸到了文件尾、把内部辅助函数 `executeWithRetry` 的**定义**算了进去；截断到 Internal 段之前后精确得到 **10 / 25，与文档完全一致**。**Code direction（Go SDK）立 P2-58（记录不修）**：取活体一条真实观测（**34 个字段**）与四家响应 DTO 逐一比对，**四家同缺同样 7 个**（`platform_source`、`generated_by_model`、`relevance_count`、`content_hash`、`step_number`、`discovery_tokens`、`embedding_model_id`）；Go 与 JS 的 `encoding/json` / Jackson **默认忽略未知字段**，故这些字段**被服务端发来、被 SDK 静默丢弃**。**决定性的是日期**：V17 / V18 迁移均提交于 **2026-04-16**，而 `go-sdk/cortex-mem-go/dto/observation.go` **最后改动是 2026-10-02** —— **DTO 在迁移之后被改过，却仍没补上这两批新列**，所以不是「没跟上」而是**改完仍然漏了**。另 4 个字段来自 V1/V2/V8/V12，其中 `content_hash` / `embedding_model_id` 很可能与三个向量列一样属内部列、本就不该暴露，**故「该暴露哪一部分」无法由证据确定**，记录不单方面实施。**压缩：两轮三次边界/控制流错误，本轮全部靠 diff 而非肉眼发现**（详见 `docs/archive/README.md` 新登记行）。

- **最近完成**: 设计文档（2026-10-04 一百六十二轮，**三处，全部为「加说明」而非「改历史」**）。**DOC-1（已修）** **三份设计文档把 6 个端点当作现有 API 呈现，而 6 个活体全部 404 —— 但正确处置是加注、不是改写，因为其中一处是带日期的历史决策记录，而那个决策本身是落地了的、只是路径不同。** 扫全部 **31 个** `phase-3-design/*.md` 的端点引用，抓到 6 种幻影，**逐条实请求确认全部 404**（`/api/ingest/session`、`/api/ingest/session/{sessionId}/userId`、`/api/extraction/{tpl}/search`、`/api/extraction/status`、`/api/extraction/allergy_info`、`/api/extraction/allergy_info/search`）。**三处性质不同，处置也不同**：

  1. **`20.md` §20.9** —— 标题写明 **「✅ RESOLVED」**、正文写明 **「DECISION (2026-03-22)」**，是**决策记录**。`userId` 的处理方式（创建时可选、之后可补）**已按此实现**，但两条 `/api/ingest/session…` 路径**从未存在**；实际落在 `POST /api/session/start` 与 `PATCH /api/session/{sessionId}/user`。**改写它等于抹掉「决策成立、路径不同」这个事实本身**，故**只加一条注**，并把真实路径连同 body 字段写清。
  2. **`19.md`** —— 一处抽取 API 清单列了 6 条，**实有 3 条**（`{templateName}/latest`、`{history}`、`POST /run`），`{templateName}/search` 与 `/status` 从未实现。同样**保留原清单 + 加注**。
  3. **`7.md`** —— 「示例查询」把模板名 `allergy_info` 当成路径用（`/api/extraction/allergy_info/...`）。**它其实是模板名、不是路由**；取结果应用 `latest` / `history` 后在客户端自行筛 `extractedData`。**加注**。

  **写入前逐条核实、不凭记忆**：两条真实路径的 body 字段核到 `ApiRequests.java:43` 的 `@JsonProperty("user_id")`；并实请求 `PATCH /api/session/r261-probe-nonexistent/user` 得 **业务 404「Session not found」而非路由 404** —— 证明端点存在且 body 被接受。**本轮零处「按断言清扫」式的改写** —— 与第 256 轮（14 处 `{sessionId}` 逐字更正）不同，**这批断言在各自语境里都是对的**（记录决策 / 记录设计），只是缺少「哪些已实现」的交代。

  **Code direction（Python SDK）修完上一轮留下的待办**：`client.py` 的类 docstring 示例写 `CortexMemClient(base_url="http://localhost:37777")`，而**下方第 72 行的真实默认值是 `http://127.0.0.1:37777`** —— 示例教的值与默认值不是同一个，且**恰好是 P2-57 证明会在 `preferIPv6Addresses` 下失败的主机名形式**。已改为不带参数的默认写法，并补一段说明为何用 IPv4 字面量而非主机名。**其余部分核实为真**：重试判定与另三家同集、**不可解析的 body 会抛错而非退化成空结果**、**docstring 声明的「绝不改动调用方 session」在代码里确实成立**（`_owns_session` 控制关闭、headers 逐请求不写入 `session.headers`，避免 API key 泄漏到调用方的其它流量）。**一处跨家差异不立 finding**：Python **无响应体上限**而 Go/JS 有 10MB，但 SDK 自带 README 已写明该差异**及其原因**（`requests` 无可移植的流式大小钩子），属**有据的设计决策**而非缺陷。**上一轮 P2-58 里我标注「大概率是探针问题」的一条已闭环**：Python 通过显式 wire 映射 `"extracted_data" → "extractedData"` 正常处理。**变更检测**：改了 `.py`，指纹 `6b856f89…` → **`ec1c529c…`**，按门控跑完整验收：回归 **45/0/1**、`EXTRACTION_ENABLED=true` 验收 **25/0/0**（带 P2-46 限定）、Python **441/441**。

- **最近完成**: 架构文档（2026-10-04 一百六十三轮，**四处，双语，文档自相矛盾**）。**DOC-1（已修，双语）** **同一份文档里，上方那张逐事件映射表早就写对了，下方那张事件流图却错了四处 —— 其中一个是根本不存在的命令。** 第 153 轮与第 259 轮已核实过本文件的控制器图、服务图、迁移树、`§Current Limits` 与 MCP 工具接线，故本轮取**从未核过的 `## Data Flow → Complete Event Flow`**。逐条落到源码：

  | 图中写的 | 实际 | 证据 |
  |----------|------|------|
  | `session-start` → **Ingestion** Controller | → **Session** Controller | `SessionController.java:47,108`（`@RequestMapping("/api/session")` + `@PostMapping("/start")`）；`IngestionController` 只有 `/api/ingest/*` 四条 |
  | PostToolUse → `wrapper.js observation` | → `wrapper.js tool-use` | `wrapper.js:39-61` 头注释与 `ENDPOINTS` 表 |
  | 上下文注入 → `wrapper.js context-get` | **该命令不存在** | 全文 `grep -c context-get` = **0**；`wrapper.js` 只有四个命令；**`proxy/` 下没有任何地方调用 `/api/context/*`** —— 上下文是随 `/api/session/start` 的响应回来的（`wrapper.js:532`） |
  | SessionEnd → `wrapper.js summarize` | → `wrapper.js session-end` | 同上 |

  **最值得记的是「文档自相矛盾」这个形态**：`ARCHITECTURE.md:283-301`（中文版同段）**本来就已经写对了**，甚至明确注明「`session-start` 由 `SessionController` 提供，而非 `IngestionController`：`POST /api/ingest/session-start` 返 **404**（活体验证，2026-10-03）」——**同一份文件的三百行之外，那张图还在画着错误答案**。这说明**校验过某一处不等于该断言在全文唯一**，与第 260 轮「按断言清扫而非按文件」是同一条纪律的另一面。**四处修正 + 图后加一条对照说明**（双语），写明每处「图中说的 vs 实际的」与证据位置，使下一个读者不必重新推导。

  **过程中我自己制造并当场发现两个问题**：①把说明文字拆成两行导致 `wrapper.js` 方框**底部边框丢失**；②中文版同一格因 **CJK 字符占两个显示列**而**超框 8 列**（改前 21、框宽 13）——两者都在提交前用显示宽度校验（`east_asian_width`）发现并修正。**锚点校验脚本自己错了两次**：先把全角标点当 CJK 字母保留（GitHub 实际删除标点），又把参照格切错位置；**两次都是文档对、探针错**。改正后两语种各 16 个内部锚点**全部解析**，含我新加的两个。

  **Code direction（JS/TS SDK）**：`src/index.ts` 的「Wire helpers（安全类型转换工具）」一组导出了 8 个中的 **6 个**，漏掉的 `safeStringOrStringList` 恰恰是**唯一处理列表列真实 wire 形态的那一个**（JSON 数组**或**逗号分隔串），且被主解析器 `parseObservation` 用了 **5 次**（`facts`/`concepts`/`files_read`/`files_modified`/`refined_from_ids`）。**它为何能存活：没有任何测试从包入口导入 wire helpers** —— 既有单测直接引 `../dto/wire-helpers` 绕过 barrel，**甚至导了 barrel 里根本没有的 `firstNonNullOr`**。已补导出 + **9 条直接单测**（从 `'../index'` 导入，**同时钉住导出面与行为**）。**我第一版测试预期是错的、被 runner 立刻抓住**：我以为 JSON 编码标量会落到逗号切分；追实现发现 `JSON.parse` 成功、得到非列表、返回 `undefined`，**这正是文档契约所述**，故改测真实行为。**双向注入还揭示了「谁抓得住」**：撤掉导出后 **`tsc` 退出 0**（`tsconfig` 的 `exclude` 含 `**/*.test.ts`，即既有 P2-42），**真正拦住它的是运行时** —— vitest 撤掉时退出 1、恢复时退出 0。**变更检测**：改了 2 个 `.ts`，指纹 `ec1c529c…` → **`9ce7fd25…`**，按门控跑完整验收：回归 **45/0/1**、`EXTRACTION_ENABLED=true` 验收 **25/0/0**（带 P2-46 限定）、JS **259/259**（250 + 9）。

- **最近完成**: 运维/用户指南（2026-10-04 一百六十四轮，**一处数字陈旧，其余五类断言复核为准确**）。**DOC-1（已修，双语）** **`./test-all.sh` 计 359 个 Go 测试——实测是 335，少 24。** 用 `go test -json` 逐模块数 `Action=pass` 且带 `Test` 的条目：根模块 **302**（已含 dto 子包）、genkit **13**、eino **8**、langchaingo **12**，合计 **335**。**独立佐证**：`go-sdk/cortex-mem-go/README.md:13,190-193` **同日**实测写的是 **362**（core 235 + dto 67 + eino 8 + genkit 13 + langchaingo 12 + `examples/http-server` 27），且明确写了「`test-all.sh` 跳过的正是各 adapter 与 example 模块」——**362 − 27 = 335**，与我的实测完全吻合。故这是**口径差**不是算错：脚本 `MODULES=("." "genkit" "eino" "langchaingo")` **不含** `examples/http-server`（该目录自带 `go.mod`，根模块的 `./...` 也进不去）。双语已改为 335，并给 `go test ./...` 补上实测的 302。

  | 文档断言 | 实测 | 判定 |
  |----------|------|------|
  | `phase3-acceptance-test.sh` 有 15 个 test functions | `log_test "Test N"` 恰 **15** 处（Test 1–15） | **准确** |
  | `run-all-e2e.sh` 跑 10 个本地套件 | `run_suite "N/10 …"` 恰 **10** 处 | **准确** |
  | Flyway 迁移区间「V1–V8, V11–V18」 | 实际 **16** 个文件，**确无 V9/V10** | **准确** |
  | 5432（原生 / `docker run`）与 5433（compose 宿主）分工 | `docker-compose.yml:35` 为 `"${POSTGRES_PORT:-5433}:5432"`；`TESTING.md:94-100,205-207` 两处均已说明 | **准确** |
  | 六份指南引用的脚本与仓库路径 | 逐条 `-e` 存在性检查，**全部存在** | **准确** |

  **被排除的两个「看似幻影」**：①`backup.sh` / `disaster_recovery.sh` 在文档里是**要你自己写的那段脚本正文**，不是对仓库文件的引用；②`./test-all.sh` 虽写成相对路径，但它在 `cd go-sdk/cortex-mem-go` **之后**执行，该文件真实存在且可执行——**第一次查仓库根目录时它显示为 MISSING，那是我的查法错了，不是文档错了**。

  **本轮的角度与上轮不同**：第 259 轮对运维/用户指南做的是「环境变量端到端对拍」，本轮改做「**文档里的命令与数字逐条实测**」——脚本存在性、测试计数、版本区间、端口分工、路径引用五类，全是可直接验证的硬事实，**不需要凭理解下判断**。

- **下一方向**: API 文档（一百六十五轮）
- **新增待决**: `docs/drafts/` 下 3 个文件超 50KB（`go-sdk-design.md` 195KB 等），50KB 规范原文仅约束 `phase-3-design/` 子目录，需明确适用范围或安排拆分
- **Pending 状态**: 文档问题清单已清空（0 项待处理）
- 完成本轮后必须把“最近完成”和“下一方向”更新在本节；详细历史保存在归档文件中。

## Pending Doc Issues

- ~~**DOCKER_README 中英文结构漂移**~~（2026-09-30 用户指南轮发现，**同日已解决**）：两文件已统一为相同的 14 个 H2 结构且顺序一一对应（H3/H4 数量一致）。变更：EN Commands 合并了 ZH 健康检查/日志查看/停止服务的命令组并新增 3 个故障排查小节（服务启动失败/数据库连接问题/端口冲突，译自 ZH）；ZH 删除了与「使用 Dockerfile 构建」重叠的「构建本地镜像」节（本地镜像构建命令并入前者），测试覆盖从 4 行表格改为与脚本实际验证项一致的 11 项清单（`docker-e2e-test.sh` 中无 MCP 测试项，原表格的 "MCP 服务测试" 声明不实）；生产注意事项统一为 4 条（ZH 原 6 条中 2 条与安全建议重复）。更正：先前记录称 "EN 缺本地开发节" 不准确 —— EN `## Development` 即其对应节。
- ~~**API 文档中英标题风格分歧**~~（2026-09-30 发现，**2026-10-02 关闭**）：EN 用描述式 H3 + 请求行代码块，ZH 用路径式 H4。**已决策为有意差异，不再统一**：路径式标题在中文语境下更自然，两版对每个端点都给出相同方法与路径，2026-10-02 用脚本核验 67 个生效端点 EN/ZH 集合完全相等。已在 `API.md` 与 `API-zh-CN.md` 顶部各加一段结构说明（含“不要单方面对齐”的告诫），避免后续轮次反复重开此项。

## 执行规则

- 每次唤醒只审查一个方向，总时长不超过 15 分钟。
- 发现文档问题直接修复并提交；发现代码问题必须当场修复，或记录到 `docs/drafts/backend-review-findings.md`。
- 每个发现的问题必须有落点，不能只写在报告中。
- 开始时检查 `http://127.0.0.1:37777/api/health`；服务未运行时遵循 `docs/drafts/cron-combined-task.md` 的启动流程，后端固定使用专用端口 `37777`。
- 本轮方向、发现、修复、验证和 commit 写入 `docs/drafts/health-check-task.md` 的综合报告。

## 质量标准

### 双语对照

- 重要文档必须有英文版和中文版。
- 中文版使用 `-zh-CN` 后缀，例如 `API-zh-CN.md`。
- 中英文内容、章节顺序和示例必须一致，并在顶部互相链接。

### 准确性和完整性

- 端点路径、HTTP 方法、参数、返回格式、版本和依赖必须通过实际代码或构建配置验证。
- API 文档覆盖所有 Controller 端点；SDK README 覆盖所有公共 API 方法；设计文档与实际 Service/Entity 一致。
- 示例必须可运行，链接和锚点必须有效；无法确认的信息不要写入。

## 审查范围

1. **API 文档**：`docs/API.md` 与 `docs/API-zh-CN.md`，对照 Controller 和 OpenAPI 注解。
2. **SDK README**：Java、Go、Python、JS/TS SDK README，核对源码签名和示例。
3. **设计文档**：`docs/drafts/phase-3-design/`（拆分后的子文档，入口 `index.md`；根 `phase-3-design.md` 仅为指针文件）与 `phase-3-design-walkthrough.md`，对照实际实现和测试脚本。引用具体章节时应指向对应子文档（如 §2.2 → `2.md`），不要只指向指针文件。
4. **架构文档**：`docs/ARCHITECTURE.md`、中文版本、`backend/README.md` 和根 README。
5. **运维/用户指南**：部署、配置、测试、故障排查和 Docker 文档。

## 活动文档维护

每轮开始和结束执行 `bash scripts/doc-growth-check.sh`。任一活动文档超过 1000 行或 102400 字节时，保留规则、状态和未解决项，将已解决历史归档到 `docs/archive/YYYY-MM-DD_<descriptive-name>.md`，更新 `docs/archive/README.md`，并重新检查。归档文件创建后不得修改。

## Archived History

完整审查历史截至 2026-05-05 保存在 [`2026-09-30_doc-review-history.md`](../archive/2026-09-30_doc-review-history.md)。
