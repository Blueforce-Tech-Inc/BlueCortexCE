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

- **最近完成**: API 文档（2026-10-04 一百六十五轮，**一处成体系的补全，双语**）。**DOC-1（已修，双语）** **数值参数只记了「怎么解析」，从未记「取值范围怎么处理」——而后者才是崩溃所在。** `Query Parameter Conventions` 一节原本详尽记录了解析（十六进制前缀、前导 `+`、空白 trim、唯一产生 `400` 的情形），却在范围处理上只留了一句含糊的「`0xff` → 255 (then clamped, if the endpoint clamps)」并把责任推给「按端点分别记录」——**而实际并没有任何端点记录**。已补一张逐参数的范围处理表，**负值**与**上界**两列都写实，并点明**七个参数完全没有上界**（`?maxObservations=5000` 返回该项目持有的全部内容，无 `MAX_PAGE_SIZE` 式保护）。表内 9 行、其中 4 行为「无上界」，中英文逐格对应。

  | 端点 | 参数 | 负值 | 上界 |
  |------|------|------|------|
  | `/api/observations` 等 5 个分页端点 | `limit` | 上钳到 1 | 下钳到 100 |
  | `/api/context/recent` | `limit` | 上钳到 1 | 下钳到 20 |
  | `/api/extraction/{t}/history` | `limit` | 上钳到 1 | 下钳到 100 |
  | `/api/logs` | `lines` | 上钳到 1 | 下钳到 10 000 |
  | `/api/context/preview` | `maxObservations`、`maxSummaries` | 下钳到 0 | **无** |
  | `/api/context/preview` | `fullCount` | 下钳到 0 | 内部封顶 100 |
  | `/api/context/preview` | `sessionCount` | 被忽略（会话限定查询仅在 > 0 时才走） | **无** |
  | `/api/context/timeline` | `depth_before`、`depth_after` | 下钳到 0 | **无** |
  | `/api/timeline` | `depthBefore`、`depthAfter` | 下钳到 0 | **无** |

  表下另附一段「2026-10-04 之前这四行是**崩溃**的」，写明旧行为（负 `maxObservations` 触发 PostgreSQL `LIMIT must not be negative`、负 `maxSummaries` 触发 `IllegalArgumentException`、preview 把两者报成 **200** + body `Error: Failed to generate context preview`、两个 timeline 端点抛未处理 500），**并指向变更记录**——文档描述系统**现在**的行为，同时不把历史抹掉。按仓库惯例双语各加一条 `(unreleased)` 变更记录。

  **本轮自述数字当场改过一次**：变更记录里先写「四个没有上界的参数」，用脚本按逗号／顿号逐格统计后实为 **7 个**（分布在 4 行），已改正——**数字必须连同计数命令一起核对**。

- **最近完成**: SDK README（2026-10-05 一百六十六轮，**一处，双语**）。**DOC-1（已修，双语）** **「捕获需要会话 ID」写了，紧接着的「项目路径」一个字没写——而后者才决定这条提示日后查不查得回来。** `cortex-mem-spring-integration/README.md` / `-zh-CN.md` 的捕获小节原先只交代会话 ID 的两种给法（Spring AI 会话 ID / `CortexSessionContext`），**没有把项目路径作为独立前提提出来**；配置表里 `project-path` 一行连默认值都没写，只写着「用于记忆隔离」。已补一节，写明两者**互不替代**：会话 ID 决定提示**是否被记录**，项目路径决定它**归入哪个项目**，而所有按项目检索的查询都按后者过滤；`CortexSessionContext.begin(sessionId, projectPath)` 同时提供两者，走会话 ID 那条路则必须搭配 `project-path` 或 builder 的 `.projectPath(...)`。附实测报文：

  ```text
  POST /api/ingest/user-prompt  {"session_id":"s1","cwd":"","prompt_text":"..."}
    -> 200 {"status":"ok"}，行以 project_path = '' 落库
  ```

  并说明该行**只有用空项目查才取得到**、任何真实项目路径都查不到，且点明 `NULL` 才是真实库中的多数形态（**2043 行 / 2011 会话**，而 `EMPTY-STRING` 仅 2 行）。配置表 `project-path` 行同步补上后果。ICL 检索路径不受影响，亦已写明。

  **刻意没加锚点链接**：先写了个猜的 `#java-sdk--spring-ai-integration`，随即按「坏链正是我在抓的东西」删掉——宁可指向"上文一节"也不留一个可能是死的锚点。

  **测试数复核为准确、未动**：README 声称 196（143 client + 46 spring-ai + 7 starter），`mvn -o test` 实测 **143 / 46 / 7，BUILD SUCCESS**，分解逐项吻合。

- **最近完成**: 设计文档（2026-10-05 一百六十七轮，**四个角度全扫，零缺陷**）。**DOC-1（核实无误、未改）** **行号引用**：全文仅 7 处 `文件:行号`，逐处核到源码——`AgentService.java:241` 正是传 `projectPath` 的调用点、`ExtractionStorageService.java:49` 正是 `@Transactional`、`:127` 正是 DLQ 构造、`StructuredExtractionService.java:211` 与 `:315` 正是 `findBySourceIn` 与 `extractAppendOnly` 的调用点；余下 2 处（`ObservationRepository.java:425`/`:644`）指向查询串的**收尾行而非声明行**，**差一行**，但实质主张（三参数签名、`findNewObservations` 存在）**均正确**，故不修。**表行数**：`18.md` 声称 `extracted_user_preference` **18,373** 行，对活体库逐条核**分毫不差**，`dlq_*` / `extraction_state` / `extraction_audit` **均 0**，四个数字全对。**内部链接** 11 条全部可解析，**跨文件锚点 0 条**。**方法名级扫描**：99 个 `foo()` 形态标识符，**真幻影 0 个**。

  最后一类是本轮最值得记的：初筛报出 26 个「未找到」，逐条读上下文后发现**绝大多数是文档本就在说明它不存在**——「appear **0 times** in `backend/src/`」「have no definitions and no callers anywhere」「neither `CostConfig` nor `BudgetExceededException` exists as a class」「SUPERSEDED」「IMPLEMENTATION NOTE: … pseudocode」——或属外部库（`pg_try_advisory_lock`、`BeanOutputConverter.getJsonSchema()`）。唯一值得追的 `resolveOutputClass()` / `buildSchemaHint()` 出现在 `99-changelog.md` 的 **2026-03-21 设计文档版本记录**里，而**当前的 `2.md` §2.3 仍在定义并使用这两个方法**，故该 changelog 条目**准确且自洽**——设计伪代码里的方法名与实现里的不同本属正常，**不加注**（承第 261 轮「历史决策记录只加注、不改写」的判断）。

  **初筛本身是探针错**：我的否定词表没覆盖文档实际使用的措辞，于是把「文档正在说明它不存在」误报成「文档声称它存在」。**没有据此改任何一处。**

- **最近完成**: 架构文档（2026-10-05 一百六十八轮，**七个角度全扫，零缺陷**）。**DOC-1（核实无误、未改）** **行号引用**：全文仅 2 处 `文件:行号`，逐处核到源码——`SessionController.java:47` 正是 `@RestController`、`:108` 正是 `@PostMapping(value = "/start")`；`dto/OffsetPageRequest.java:107-114` 正是 `equals` 方法体、instanceof 落在 109 行，与文中自述「the instanceof line itself is 109」**完全一致**。**组件计数**：服务层图中 29 条与 `service/` 下**实测 29 个类一一对应**（`XmlParser` 标注在 util、`ClaudeMemMcpTools` 标注在 mcp，两处标注也都对）；仓储 6 ↔ 6；事件类图写「`PendingMessageEvent` + Listener + Publisher」「`MemoryRefineEvent` + Listener + Publisher」，`event/` 下**恰是这 6 个文件**。**端点数**：`ContextController` 声称「7 endpoints incl. /semantic」，`@*Mapping` 实测**恰 7**；Viewer 行声称「15 methods」，对活体 `/v3/api-docs` 逐路径数操作数 = **15，精确**（含 `/api/settings` 与 `/api/modes` 各 2 个操作才算得满）。**配置摘录**：`server.port/address`、`threads.virtual.enabled`、datasource 三层嵌套默认值、`jpa.ddl-auto: none` / `open-in-view: false`、`claudemem.llm.provider` 与实际 `application.yml` **逐项吻合**；紧随其后的 dev/prd 差异注记（`prd` 走 `api.openai.com` + `gpt-4o` + `text-embedding-3-small` @1536）实测属实。**模式数**：文中称 32 个 profile（`code` + 30 个 `code--*` + `email-investigation`），`modes/` 下**恰 32 个 json**、`code--` 前缀**恰 30**、其余恰为 `code.json` 与 `email-investigation.json`。**安全章节**：`proxy/tag-stripping.js` 存在且四种标签全在其中；compose 端口映射 `"${POSTGRES_PORT:-5433}:5432"` 在第 35 行；`.env.example` 存在。**目录树**：`proxy/` 列的 10 项**逐项存在**，含第 248 轮曾断言「仓库根本没有 `java/` 目录」时容易误判的 `proxy/java/proxy/test-full-flow.mjs`——那次说的是**仓库根**，此处确实有；`wrapper.js` 的真实事件→端点映射与文档 `ENDPOINTS` 表**逐项一致**。

  **两个探针自身出错、先识别再采信**：①数 Go 的方法数时用 `awk '/^type Client interface/,/^}/'` 截取，把**嵌套接口**的 `String() string` 也算了进去得 27；改用「从第 9 行起、遇行首 `}` 即止」重新截取后为 **25**。②核端点存在性时拿文档里的 `/api/cursor/`、`/api/test/` 这类**前缀**去和活体的**全路径**做等值比较，得「活体无对应者」——实际两处都在（各 3 条路径）；改按前缀匹配后，文档 32 条 `/api` 引用中唯一在活体不存在的只有 `/api/ingest/session-start`，而**文档自己已两处写明它返回 404**，属**已声明**而非漂移。

  **本轮唯一的真缺陷在后端而非文档**，已记为 **P2-61**：`TestController` 的类级 `@Profile("!prod")` 指向本仓库**不存在的 profile**（只有 `dev` 与 `prd`），该门控在项目实际使用的每一种 profile 下**都匹配、永不排除任何东西**，而 `@Tag` 描述却声称「Only available in non-production environments」。**架构文档 `ARCHITECTURE.md:880` 列出 `/api/test/*` 时不带任何 profile 限定，据此反而是准确的**——因为门控确实从不生效，本轮**未改该行**；代码方向为 Python SDK，后端不在本轮范围内，故 ⏸ 记录不修。

- **最近完成**: 运维/用户指南（2026-10-05 一百六十九轮，**五个角度全扫，零缺陷**）。**DOC-1（核实无误、未改）** **`TESTING.md`**：§3 表中 16 个脚本 + §3.5 的 3 个工具**逐个存在（20/20）**；`phase3-acceptance-test.sh` 的「15 test functions」实测**恰 15** 个且函数名可逐个列出；`run-all-e2e.sh` 的「10 local E2E suites」实测编号**恰为 1/10…10/10**；`regression-test.sh` 的五个选项**全部存在**、`--help` 实跑退出 0；§7 的 MCP 自动探测与 `mcp-e2e-test.sh:97` 的判定条件 `[ "$sse_status" = "200" ] && [ "$mcp_status" = "404" ]` **逐字一致**，活体实测 `/sse` 200 / `POST /mcp` 404 **正是文档描述的 SSE 态**；§8「CI/CD Integration」**只列 `docker.yml` 且未声称跑测试**，与已记录的「本仓库无任何 CI 跑测试」一致。**`DEPLOYMENT.md` §2.4** 与 `docker-compose.yml` 逐行 diff：**键值集合完全一致**（剥注释 + 排序后 `diff` 为空），差异**纯为注释与排版**；§4.1 迁移表 **16 行**文件名与 `db/migration/` 下 16 个文件**逐字吻合**，且**未虚列 V9/V10**；V1 的「5 core tables」实测**恰 5** 张表；§4.3 的容器名与库名均与 compose 一致。**双语同步**：两份 TESTING 的 15/10、两份 DEPLOYMENT 的迁移表 16 行，**两版计数逐项相同**。

  **一个探针命中的是文档已经解释过的事**：扫 §5 的 49 个环境变量名时，`CLAUDEMEM_RATE_LIMIT_*` 三个在后端配置中查无此物——但**文档自己就写着**「none of its keys appear in `application.yml`」，并说明 `RateLimitService` 直接从 `@Value` 默认值读取。实测 `max-requests:10` / `window-seconds:60` / `cleanup-interval-seconds:300`，**三个默认值与文档表格逐项吻合**。**探针错、文档对，未据此改任何一处**（与第 166、167 轮同型）。

- **最近完成**: API 文档（2026-10-05 一百七十轮，**四个角度全核，零缺陷**）。**DOC-1（核实无误、未改）** **端点覆盖率**：把活体 `/v3/api-docs` 的 **62 条**路径逐条在 `API.md` / `API-zh-CN.md` 中查找，**两版各 0 条缺失**。首次扫描曾报「4 条未显式写出」（`/api/context/prior-messages`、`/api/context/recent`、`/api/context/timeline`、`/stream`），但那是**我的正则只认单行内联的 `METHOD /path` 写法**——这四条其实分别以 `#### GET \`…\`` 四级标题和代码块形式记载，**探针错、文档对**。**第 264 轮新增的逐参数范围表**（9 行）逐行实测：①`limit` 在五个分页端点为 `Math.min(Math.max(1, limit), Constants.MAX_PAGE_SIZE)`，`MAX_PAGE_SIZE = 100`，活体 `limit=0`/`-5` → 1 条、`500` → 100 条；②`/api/context/recent` 为 `Math.min(Math.max(1, limit), 20)`；③`/api/extraction/{t}/history` 为 `if (limit<1) limit=1; if (limit>100) limit=100;`；④`/api/logs` 为 `Math.min(Math.max(1, lines), 10000)`——**四种写法不同，行为与表内断言逐项吻合**（前两条我一度用 `grep Math\.` 漏检，实为探针匹配方式单一）。⑤「下钳到 0」的六行经**补齐必填 `project`、把 epoch 区间缩到 7 天、给 `/api/context/timeline` 带上真实 `anchor`** 后实测**全值域 200**（`-5 / 0 / 1 / 5 / 5000`），第 264 轮的修复仍成立。**三次探针自身出错、先识别再采信**：缺必填 `project` 让四个请求齐返 400（几乎可以写成「第 264 轮修复已回退」的假发现）；epoch 区间 578 天触发 `Date range exceeds 1 year maximum`；缺 `anchor` 触发业务 400 `No anchor found`。**决定性的是对照**——合法值 `depthBefore=5` 同样返 400，一度让「负值致 400」看起来成立，直到跑出对照才确认 400 与 depth 无关。**中英范围表各 11 行，同步。**

- **最近完成**: SDK README（2026-10-05 一百七十一轮，**四个角度全核，零缺陷**）。**DOC-1（核实无误、未改）** **跨四家一致性**是本轮的新角度：Python README 声称「`max_retries` counts attempts, matching Java's `retry.max-attempts` and the Go and JS names — **all four default to 3**」，逐个核到代码——Java `CortexMemProperties` 的 `maxAttempts = 3`、Go `client_impl.go:105` 的 `MaxRetries: 3`（其代码注释自嘲 "despite the name"，与 README 的「Total **attempts**」表述一致）、JS `maxRetries ?? 3`、Python `max_retries = 3`。**单位换算说明亦属实**：Python 用秒、Go/Java 用 `Duration`、JS 用毫秒；Go README 的 `WithTimeout 30s` 对应 Java `readTimeout`、`WithConnectTimeout 10s` 对应 `connectTimeout`，对应关系由 README 自己写明。**三家的安装命令**与实际逐字吻合：JS `@cortex-mem/js-sdk` = `package.json` 的 `name`；Go `…/go-sdk/cortex-mem-go` = `go.mod` 的 `module`；Python `pip install -e ./python-sdk/cortex-mem-python`，且 `from cortex_mem import` 对应包目录名。

  **一处看着像缺陷、查源码后确认是忠实镜像，故未改**：Go README 的分类表有一行字面叫 **「P1」**，装着 `GetProjects` / `GetStats` / `GetModes` / `GetSettings`——与本仓库 P0/P1/P2 的严重级用词撞名，初看像是把优先级标签误当成分类。**但 `client.go:96` 与 `client_methods.go:325` 的段头正是 `// ==================== P1 Management ====================`，其后恰是这四个方法**，README 是在镜像源码自己的分组。改它反而会让文档与源码脱节。**又一次「先识别再采信」——这次是识别出「看起来可疑」的部分其实没问题。**

- **最近完成**: 设计文档（2026-10-05 一百七十二轮，**两个角度全核，零缺陷**）。**DOC-1（核实无误、未改）** **内部链接**：对 `docs/drafts/phase-3-design/` 全部子文档加 `phase-3-design.md` / `phase-3-design-walkthrough.md` 扫非 http 链接，**25 条全部可解析，零断链**。**`文件:行号` 引用仍是第 166 轮那 7 处，无新增、无漂移**，抽查 5 处精确命中：`AgentService.java:241` 正是去重查询、`ExtractionStorageService.java:49` 正是 `@Transactional`、`:127` 正是 `dlq.setType("dlq_" + templateName)`、`StructuredExtractionService.java:211` 正是 `observationRepository.find…`、`:315` 正是 `return extractAppendOnly(template, candidates, priorJson)`。本轮**没有新角度可换**——第 166 轮已做过行号、表行数、链接、方法名四个角度且均为零缺陷，故不制造修改。

- **下一方向**: 架构文档（一百七十三轮）
- **新增待决**: ①`docs/drafts/` 下 3 个文件超 50KB（`go-sdk-design.md` 195KB 等），50KB 规范原文仅约束 `phase-3-design/` 子目录，需明确适用范围或安排拆分；②**本仓库无任何 CI 跑测试**——`.github/workflows/` 下只有 `docker.yml`，做 checkout + QEMU/Buildx + 推多架构镜像，**不跑测试**，workflows 中 `go test` 零命中；接 CI 需决定跑哪些套件与是否 provisioning 数据库/密钥，属基础设施决策；③**`backend-review-findings.md` 第 271 轮收在 1000/1000 整**（`lines > 1000` 才判越线），**下一条 finding 必然再次触发 `COMPACTION_REQUIRED`**；④**P2-63 暴露的规则缺口**：现行压缩断言只查边界行首与「指针数=归档块数」，**没有一条检查「工作文件里曾存在的条目是否还在」**——补这一条属规则变更，需项目决策
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
