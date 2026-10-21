# Health Check History Archive 19（第 375~389 轮，2026-10-22）

> **Archived**: 2026-10-22，第 397 轮压缩迁移，自 `docs/drafts/health-check-task.md` 第 94–792 行。
> **Rule**: 归档文件创建后不得修改。后续历史达阈值时使用新的日期命名归档。
>
> 以下为第 375 / 376 / 377 / 378 / 379 / 383 / 384 / 385 / 386 / 387 / 388 / 389 轮轮报告的**逐字原文**（含其间的空行）。
> 迁移原因：本文件写入第 397 轮报告后字节数逼近 150000 字节上限（迁出前 **1396 行 / 149509 字节**），
> 而第 397 轮的新报告仍需续写。
> 十二个报告块在原文件中**连续无夹缝**（第 94–792 行），其间**没有**任何其他内容；
> 三段「基线的使用前提」在更早的位置（第 47 / 71 / 77 / 82 行），**未迁**。
>
> 本归档实测 **712 行 / 77601 字节**。

> **第 375 轮：JS/TS SDK（代码）+ 设计文档（文档，doc round 275）**：
> **健康预检**：`/api/health` → **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的）；
> 37778~37781、37790、37791 轮前均空闲；HEAD 干净；指纹 `fb46b66e…`。
> **代码方向（JS/TS SDK）：P2-131 的跨家结转在 JS 侧已修 ⇒ 四家全部结转完毕**（零可执行行）：
> `src/client.ts` 的 `recordObservation` JSDoc、`src/dto/observation.ts` 的 `ObservationRequest`
> 类型注释与三个字段的行内注释、README EN/ZH 的 Wire Format 采集段、demo 的 create 端点注释与
> demo README —— 全部写明**后端 tool-use 处理器只读那六个字段、这三个字段被接受后丢弃、
> 请用 `updateObservation`（PATCH）落库**。**JS 侧的补法与 Go 同型**：它的 `ObservationRequest`
> 是**接口**（不是类），坑落在**类型注释**上，不是字段声明上。
> **P2-136 的 JS 侧结转**（零可执行行、demo 契约未动）：JS demo 的 `POST /observations/create`
> 与 Python / Go / Java 三家**同型** —— 收 `source`/`extractedData`/`prompt_number` 交给
> `recordObservation`（tool-use 路径 ⇒ **从不落库**）却返回 `{ status: 'recorded' }`。
> **代码方向（JS/TS SDK）：其余部分核实为真（系统性核对，非抽样）**：
> **23 个请求点 vs 活体 OpenAPI：23/23 全对**（活体 66 路径 / 71 操作）。判据是
> **动词 + 路径形状**，不是逐字比对 —— 因为 **URL 模板里的变量名不必与 SDK 的局部参数名同名**
> （SDK 写 `observationId`、活体写 `{id}`，而落进 URL 的是**值**）；**阳性对照**：故意编造的
> 端点正确 MISS ⇒ 判据看得见差异。**同族测试缺口普查**（第 373 轮立的角度）：JS 的
> **14 个错误判定函数逐个有测试 ⇒ 零缺口**（对照 Go 的 13 个分类器缺 2 个、Python 的 15 个零缺口）。
> `tsc --noEmit` 干净；**264 个测试全绿**；README 自称的「**264**」与构成
> 「246 client + 5 truncated-body + 8 http-server 示例 + 5 http-server 错误契约」**实测相符**。
> **文档方向（设计文档，doc round 275）：新立 P2-137（已修，零可执行行）**：
> `docs/drafts/js-sdk-design.md` 的「30-Second Example」里写着
> 「the response does not echo it back, so it is not readable from the returned object」——
> **活体实测回显**：新鲜实例 37790 上 `POST /api/session/start` 返回
> `{"session_id":"p375-docprobe-1","context":"…","updateFiles":[],"session_db_id":"17b09c13-…","prompt_number":1}`，
> **响应里有 `session_id`，值就是请求里送进去的那个** ⇒ 第一句**为假**。
> 第二句**为真**：`SessionStartResponse` 只有 `session_db_id`/`context?`/`prompt_number` 三个字段、
> **不含 `session_id`** ⇒ 在本包自己的类型下确实读不到。
> ⇒ **建议是对的、给出的理由是错的**：文档把「**类型没暴露**」说成了「**wire 没带**」。
> **这与本 SDK 自己的 README 直接矛盾** —— README 写的是「session_id and updateFiles also arrive on
> the wire but are not part of the SessionStartResponse type」⇒ **同一件事、两份文档、两种说法，
> 而 README 那份是对的**。
> **为什么不算「带日期的快照」**（这一步是本条的关键判据，不能省）：该文档头部是
> `Date: 2026-03-27` / `Status: Under Development`，其「Version Strategy」表描述的是**已发布的
> `@cortex-mem/js-sdk` v1.0.0** ⇒ 那个日期是**版本戳**，不是挂在某句断言上的测量戳。
> 另外**查了后端历史**：**2026-03-28 时点的 `SessionController` 确实只返回 4 个键**
> （`context`/`updateFiles`/`session_db_id`/`prompt_number`，**没有** `session_id`）
> ⇒ 这句话在署期**当时为真**，是后端后来把 `contentSessionId` 加进响应体才变假的。
> **但那是一段已经过去的历史** —— 文档仍在描述**今天这个包**，而包的行为以今天为准；
> 且文档**自己**的 README（同一份代码、同一句话的事实）写的是现在时且正确 ⇒ 判为失实陈述。
> **修复**：把注释改成「响应**确实**在 wire 上回显 `session_id`（活体响应带 5 个键），
> 但 `SessionStartResponse` **不暴露**它 ⇒ 在本包自己的类型下读不到，请自己留一份」，
> **「自己留一份」这个建议本身一字未改**。
> **按断言清扫**：`go-sdk-design.md` 与 `python-sdk-design.md` **没有**这一条 ⇒ **范围只此一处**。
> **设计文档的其余断言逐条核实为真**：「零强制依赖」（`package.json` 的 `dependencies` 为 **None**）、
> 「无 `"type": "module"` 故 CJS 产物叫 `index.js`」、`exports` 三键与文档**逐字一致**、
> 「`tsup` 配 `format: ['cjs','esm']`」、「没有 `CHANGELOG.md`」、「另暴露 4 个 logger 方法与
> `toString()`，不属于 API 面」、「API Method Mapping (26 methods)」的 26 行与客户端的 26 个方法
> **集合完全相同**；以及「后端**没有**单条观察读端点」——`GET /api/observations/{id}` 活体 **404**、
> `GET /api/memory/observations/{id}` 活体 **405**（**阳性对照**：`GET /api/observations?limit=1`
> → 200 命中 1 条）。
> **探针自身出错 3 处，先识别再采信**：端点对拍的第一版把「活体有、SDK 没用到的 **53** 个操作」
> 混进了未命中清单；第二版把路径变量当成普通路径（**5** 处假 MISS）；第三版按**形状**回配才得到
> 23/23。**阳性对照**每次都正确 MISS。
> **容量**：写入前 findings 只剩 **307 字节**，本轮做了**两处零信息损失压缩** —— C3b 把
> 「CJK 唯一短词组」的长度上界由 4 放宽到 **8**（**120** 处）、C3c「CJK 重复长词组」（**4** 处），
> **都只删 `**` 标记、一个字未改**（硬证明：去掉全文所有 `*` 后字符流与压缩前**逐字相同**），
> 共省 **496** 字节；**写入后又把 P2-137 的 Status 行缩短 286 字节** —— 细节**逐字**留在归档里，
> 并以五处锚点短语（`当时为真`/`按断言清扫`/`SessionStartResponse`/`2026-03-28`/`类型没暴露`）
> **断言归档确实含有**。写入后余 **119** 字节；**P2-115 的阈值上调仍是下一轮写入的前置条件**。
> **变更检测与完整验收**：指纹 `fb46b66e…` → **`4c500255…`**（记录数仍 2220）⇒ **门控全量**。
> 新鲜度：本轮**未改后端**，`find backend/src/main -newer <jar>` **零命中** ⇒ 豁免成立；
> 仍另起新鲜实例 37790 跑验收（pid 79609，验收后已停，**含密钥的 `/tmp/p375-env.sh`
> 已 mavis-trash 删除并确认**）。回归 **45/0/1**（exit 0）+ EXTRACTION 验收 **25/0/0**（exit 0）。
> **本轮新增归档**：`2026-10-20_backend-review-evidence-76.md`（112 行 / 8789 字节，
> 承 `-62` ~ `-75` 体例，含 P2-137 全文、P2-131 收官与 P2-136 的 JS 侧结转）。
> **待决事项（均已记录在 `docs/drafts/backend-review-findings.md`，不因此停下）**：
> ①P2-115（**最要紧，写入前置条件**）：findings 字节上限贴顶，建议 `MAX_LINES=2000` /
> `MAX_BYTES=200000`，只需同步 `docs/drafts/cron-combined-task.md:14`；
> ②P2-136 三条修法择一（SDK 新增公开 API / demo 改路径 / demo 拒收）；
> ③P2-135 两种修法（spec 变严 vs 运行时放宽）；④P2-127 选项②③；⑤P2-126；⑥P2-129；⑦P2-128；
> ⑧P2-125；⑨P2-123；⑩P2-124；⑪P2-90；⑫P2-122；⑬P2-120；⑭P2-119；⑮P2-121；
> ⑯P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 /
> P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status / 另三份零覆盖设计稿 ——
> **均不在本轮范围内，本轮不因此停下**。

> **第 376 轮：Demo（代码）+ 架构文档（文档，doc round 276）**：
> **健康预检**：`/api/health` → **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的）；
> 37778~37781、37790、37791 轮前均空闲；HEAD 干净；指纹 `4c500255…`。
> **代码方向（Demo）：P2-136 的跨家结转在 Go 与 Java 两侧已修 ⇒ 四家 demo 至此全部注明**
> （零可执行行）：Go demo `examples/http-server/main.go` 的 `POST /create-observation` 与
> Java demo `ObservationsController.java` 的 `POST /demo/observations/create` 各补上与
> Python（374 轮）、JS（375 轮）两侧**同一句**的注释 —— 后端 tool-use 处理器只读那六个字段、
> 这三个字段被接受后丢弃、真正落库的是同级的 `PATCH /…/{id}`。**demo 契约未动**。
> **新立 P2-138（已修，demo README，零可执行行）**：Go demo README 的端点表把
> `/create-observation` 写成「**Direct** observation creation」—— 这个词把读者引向错误的心智模型，
> **两层都不对**：①它**不是**后端的「直接创建」端点 —— 后端**确实**有一个真正意义上「直接创建」的
> `POST /api/ingest/observation`（**同步落库**、`discoveryTokens = 0`、**落** `source` / `extractedData` /
> `prompt_number`），而 demo 这个走 `/api/ingest/tool-use`（**fire-and-forget 异步入队**、经 LLM 抽取、
> **不落**那三个字段）⇒ **两个语义完全不同的端点被同一个形容词盖住**；②它也**不「直接」** ——
> tool-use 路径要经 LLM 抽取才产出 observation，是**异步**的。
> **按断言清扫**：Python 与 JS 的 demo README 写的是「Record observation」，
> **Java demo 的 README 根本没有端点表**（全篇零「observations」字样）⇒ **四家里只有 Go 这家这么写**，
> **范围只此一处**。已改为「Record observation (tool-use path)」并补表下说明。
> **文档方向（架构文档，doc round 276）：核实为真，未改一个字**：
> `docs/ARCHITECTURE.md` 与 `docs/ARCHITECTURE-zh-CN.md` 的「API Layers → REST API」表逐路径与
> 活体 OpenAPI 对拍 —— **EN 31 个 / ZH 31 个路径 token，两版集合完全相同**；**零幻影端点**
> （每个 token 都在活体 OpenAPI 的 66 条路径里，含通配行的基路径本身）；反向**两版都覆盖活体 65/66** 条
> —— 唯一未单列的 `/api/logs/clear` 在 Logs 行的**描述**里以「clear」写明 ⇒ **表是完整的**。
> **探针自身出错 1 处，先识别再采信**：第一版把「活体有、表未单列」的三条算成未覆盖 ——
> `/api/import` 与 `/api/mode` 是**通配行的基路径本身**（`/api/import/*` 的 base 就是
> `POST /api/import`），`/api/logs/clear` 在描述里；第二版在 ZH 侧用错了表头 ⇒ 抽出 **0** 个 token，
> 改用各自表头后 EN/ZH 才是可比的 31 对 31。
> **各 demo 自身按自己的方式核实**：Go demo `gofmt -l` **零命中**、`go vet ./...` **通过**；
> Java demo `mvn compile` **exit 0**，且产物 class 文件时间戳确认**真的重编过**（不是缓存命中）。
> **容量**：写入前 findings 只剩 **119 字节**，本轮做了一条**统一规则**的零信息损失压缩 ——
> C3d「唯一散文词组」（含汉字、无反引号、无 snake_case / camelCase 标识符、无路径、无代码符号、
> 长度 ≤ 12，**全篇只出现一次**）去标记 **166** 处，**只删 `**` 标记、一个字未改**
> （硬证明：去掉全文所有 `*` 后字符流与压缩前**逐字相同**），共省 **664** 字节；**写入后又把两条
> Status 行缩短 95 字节** —— 细节**逐字**留在归档里，并以五处锚点短语断言归档确实含有。
> 写入后余 **70** 字节；**P2-115 的阈值上调仍是下一轮写入的前置条件**。
> **变更检测与完整验收**：指纹 `4c500255…` → **`323dea9e…`**（记录数仍 2220）⇒ **门控全量**。
> 新鲜度：本轮**未改后端**，`find backend/src/main -newer <jar>` **零命中** ⇒ 豁免成立；
> 仍另起新鲜实例 37790 跑验收（pid 76963，验收后已停）。回归 **45/0/1**（exit 0）+
> EXTRACTION 验收 **25/0/0**（exit 0）。
> **本轮新增归档**：`2026-10-20_backend-review-evidence-77.md`（74 行 / 5847 字节，
> 承 `-62` ~ `-76` 体例，含 P2-138 全文、P2-136 的 Go 与 Java 侧结转）。
> **待决事项（均已记录在 `docs/drafts/backend-review-findings.md`，不因此停下）**：
> ①P2-115（**最要紧，写入前置条件**）：findings 字节上限贴顶，建议 `MAX_LINES=2000` /
> `MAX_BYTES=200000`，只需同步 `cron-combined-task.md:14`；
> ②P2-136 三条修法择一（SDK 新增公开 API / demo 改路径 / demo 拒收）；
> ③P2-135 两种修法（spec 变严 vs 运行时放宽）；④P2-127 选项②③；⑤P2-126；⑥P2-129；⑦P2-128；
> ⑧P2-125；⑨P2-123；⑩P2-124；⑪P2-90；⑫P2-122；⑬P2-120；⑭P2-119；⑮P2-121；
> ⑯P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 /
> P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status / 另三份零覆盖设计稿 ——
> **均不在本轮范围内，本轮不因此停下**。

> **第 377 轮：Backend（代码）+ 用户指南/运维指南（文档，doc round 277）**：
> **健康预检**：`/api/health` → **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的，且其进程起于
> 2026-10-20 02:17、jar 重建于 20:57 ⇒ **是陈旧 jar**，不可用于任何对拍）；37778~37781、37790、37791
> 轮前均空闲；HEAD 干净；指纹 `323dea9e…`。
> **代码方向（Backend）：P2-139 —— 一个用来回答「配置对不对」的端点，永远回答「对」**：
> `LlmService.chatCompletionWithUsage` 把 provider 的**每一个**异常都 catch 掉并返回
> `LlmResponse.empty()`（**content = 空串**）⇒ 异常永远到不了 `TestController.testLlm()`，
> 那里的 catch 是**死代码**、`ResponseEntity.status(500)` **永不触发**。
> **活体实测（本机 LLM chat 密钥已失效）**：
> | 端点 | 修复前 | 修复后 |
> |------|--------|--------|
> | `GET /api/test/llm` | **200** `{"status":"success","message":"LLM (DeepSeek) is working!","response":""}` | **500** `{"status":"error","message":"LLM (DeepSeek) failed: 401 - {...authentication_error...}"}` |
> | `GET /api/test/embedding`（**同族对照，本轮未改**） | 500 `{"status":"error","message":"Embedding failed: 401 - {...Token is invalid.}"}` | 同左 |
> | `GET /api/test/all` | 200，内含 `llm.status = "success"` | 200，内含 `llm.status = "error"` |
> ⇒ **同族两个端点、同一类故障，一边老实说 500、一边说「is working!」**。后端日志有对应的
> `ERROR ... LLM call failed: 401 - {...Your api key: ****13fc is invalid...}`（实测 **6** 条）
> ⇒ **失败是可见的，只是没被端点报告出来**。
> **最坏后果：仓库自己的巡检脚本被骗**。`scripts/test-llm-provider.sh` 读的是 **`.status` 字段**
> （不是状态码）⇒ **LLM 密钥 401 完全失效时仍打印 `[PASS] LLM test passed` 并 `return 0`**（**假阳性**）；
> 修复后同一脚本逻辑对同一故障输出 `[FAIL] HTTP request failed with status: 500`。
> **契约方向**：双语 `docs/API.md` / `docs/API-zh-CN.md` 与活体 OpenAPI 都明写失败返 **500** +
> `{"status":"error","message":"LLM (DeepSeek) failed: ..."}` ⇒ **契约对、实现违约** ⇒ 属「已损坏行为」，可修。
> **与 P2-28 的区别（不是重复立条）**：P2-28 记的是 `/api/test/all` **丢弃子状态码**、恒返 200，
> 其实测记录里 **LLM 密钥当时是有效的** ⇒ 那时 `/api/test/llm` 报 success **是真的**；
> 本条根因不同（`LlmService` 吞异常），层面也不同（`/api/test/llm` **自己**把失败说成成功）。
> **修法（改动面最小，既有 8 个调用方行为完全不变）**：`LlmService` **纯新增**
> `chatCompletionReportingFailure`（同样的调用，但**不吞异常**），`chatCompletion` 与
> `chatCompletionWithUsage` **一字未改**；`TestController.testLlm()` 改用它并显式判空响应。
> **既有调用方逐个核实过**：它们**各自都已处理「空响应」**（`MemoryRefineService` 有 `isEmpty` 保护、
> `LlmQualityScorer` 的 `parseAnalysisResponse` 有 `isBlank` 保护、`StructuredExtractionService` 的
> `parseJsonResponse` 对空串**抛异常**、`AgentService` 的 `XmlParser.parseObservation("")`
> **抛 `DataValidationException`**）⇒ 吞异常在那些路径上是**被下游接住的**，
> 只有诊断端点无人接住。**这也是选「新增方法」而不是「改 `LlmService` 语义」的原因**：改语义会连带改变
> `AgentService.isRetryableException` 的判定输入（它按**异常消息文本**匹配 `API_KEY_INVALID` /
> `PERMISSION_DENIED` 等），而 401 的消息 `Your api key: **** is invalid` **一条都匹配不上**
> ⇒ 会被判成**可重试** ⇒ **在失效密钥上无限重试**。本轮**不改**那条路径。
> **回归测试（3 个）**：`providerFailure_isReportedAsErrorWith500`（500 + `status:"error"` + 消息含 401）、
> `providerSuccess_isStillReportedAsSuccessWith200`（**防止「改成永远抛」也能过**）、
> `chatCompletion_stillDegradesProviderFailureToEmptyString`（**钉住修复没有泄漏到那 8 个调用方**）。
> **阳性对照（证明测试钉得住）**：把 `TestController` 还原成修复前的调用后重跑，
> `providerFailure_isReportedAsErrorWith500` **确实失败**（`Tests run: 3, Failures: 1`、`BUILD FAILURE`）
> ⇒ 这条性质**会失败**，不是恒不响的空测试。
> **文档方向（用户指南/运维指南，doc round 277）：两条同形状的缺陷**：
> ① **P2-140 —— 目录重命名后 13 处 `java/` 前缀路径没跟上**：本仓库后端在 `backend/`、
> **没有 `java/` 这一层**（`ls -d java` → 不存在）。最实的一处：
> `test-llm-provider.sh --config` 实测输出「**No .env file found**」—— 路径错**且** `PROJECT_ROOT`
> 用 `dirname(dirname(...))` **多退一层**、指向仓库**父目录**（其余 **9** 个脚本都用
> `$(cd "$SCRIPT_DIR/.." && pwd)`）；另一处 `create-distribution.sh` 整条路径假设都是 `java/` 前缀，
> 实测**直接报错退出**（`cp: .../java/docker-compose.yml: No such file or directory`）。
> 已逐处改为真实布局并实测通过（`--config` 读到 `backend/.env` 且**密钥仍被打码**；
> `create-distribution.sh` 产出 `README.md` + `docker-compose.yml` + `proxy/` 共 **7** 个文件）；
> 五个改过的 shell 脚本 `bash -n` **全部 OK**。
> **按断言清扫**：全仓 `java/` 命中 **13** 处 ⇒ 范围就是这些；`docs/drafts/` 与 `webui/docs/drafts/`
> 的命中是**历史报告正文**与**带日期的快照** ⇒ 按「带日期的快照不算失实陈述」**不改**。
> ② **P2-141 —— `test-all.sh` 只测 4 个模块就宣布「All tests passed!」**：该 SDK 实有 **9** 个 `go.mod`
> （`find . -name go.mod` 实测），而脚本的列表是**手写**的 `("." "genkit" "eino" "langchaingo")`
> ⇒ 漏掉 `examples/http-server` 的 **33** 个测试 —— 而这**正是该 SDK README 点名要防的腐化**。
> 已改为**从 `go.mod` 自动发现**（新增模块不会再漏）⇒ 实测 **9/9** 全绿（修前 4/4）。
> 顺带订正双语 `DEVELOPMENT.md` 的陈旧数字 **335/302 → 372/306**（**335** 是第 164 轮实测、
> **362→363** 是第 319 轮订正、**372** 是 2026-10-20 实测 ⇒ **查清来历后订正**，非本轮新造）。
> **其余核实为真**：`DEVELOPMENT.md` 的 **6** 个 `cd` 目标**全部存在**；`DEPLOYMENT.md` 的端口（**37777**）、
> `SERVER_PORT` 默认值、`CLAUDEMEM_RATE_LIMIT_*` 默认值（**10 / 60 / 300**）与实现**逐项相符**；
> Go SDK README 的 **372** 分解**逐项精确相符**（core 239 + dto 67 = 306 根模块，+ genkit 13 + eino 8
> + langchaingo 12 + `examples/http-server` 33 = 372）；四个 `examples/` 模块实测确为 `[no test files]`。
> **同族漏改**：`RateLimitService` 的类 Javadoc 与内层类注释仍写着「**sliding window**」，
> 而实现是**固定窗口**（`if (now - windowStart >= windowSeconds) { count.set(0); windowStart = now; }`
> —— **整窗重置**）⇒ **失实陈述**，本轮已订正注释（并注明内层类名 `SlidingWindow` 是历史遗留）。
> 这是第 373 轮那条（当时只改了文档侧）的**同族漏改**。
> **探针自身错 3 处，全部先识别再采信**：①**源码解析式对拍**连续三版都错（路径模板里的 `{` 被当成方法体起点、
> 基路径取文件内第一个 `@RequestMapping`、`skip_annotations` 吞掉普通代码 ⇒ 只解析出 **12** 个路由）
> ⇒ **放弃源码解析路线**，改用**活体多形态穷举**（每端点 3 种路径变体 × 3 种请求体）；
> ②活体穷举第一版**类型错**（拿 `int` 状态码与 `str` 声明集合比）⇒ **30** 个操作全部误报；
> ③`test-llm-provider.sh` 的 `BASE_URL` **硬编码**、传环境变量**不生效** ⇒ 首次复测得出了
> 「修复没生效」的**错误结论**（**差点据此改回代码**）。
> **容量**：写入前 findings 只剩 **70** 字节，本轮做了一条**新杠杆**的零信息损失压缩 ——
> **C3e「唯一散文长词组」**（含汉字、无反引号、无 snake_case/camelCase 标识符、无路径、无代码符号、
> 长度 13~40、**全篇只出现一次**、且**不含换行**；**跨行的 64 处必须排除**，去掉 `**` 会破坏
> Markdown 缩进与列表结构）去标记 **162** 处，**只删 `**` 标记、一个字未改**；
> 硬证明：去掉全文所有 `*` 后字符流与压缩前**逐字相同**（脚本内三重断言）。共省 **648** 字节；
> 另把 **6** 条旧条目的 Status 行缩短（细节**逐字**留在各自归档内并以**锚点短语断言**归档确实含有）
> 与 **P2-116 的「第 363 轮的更正」段（1143 字节）** 逐字迁入本轮归档。
> **变更检测与完整验收**：指纹 `323dea9e…` → **`4306466b…`**（记录数 2220 → 2221）⇒ **门控全量**。
> 新鲜度：本轮**改了后端** ⇒ 豁免**不适用** ⇒ 重新构建 jar（**22:16:37**）并另起新鲜实例 **37790** 跑验收
> （`find backend/src/main -newer <jar>` **零命中**）。后端全量 `mvn -o test` **170/0/0**（原 167 + 新增 **3**）；
> `go test ./...` 全绿；`bash test-all.sh` **9/9**；回归 **45/0/1**（exit 0）+ EXTRACTION 验收 **25/0/0**（exit 0）。
> **本轮新增归档**：`2026-10-20_backend-review-evidence-78.md`（**274** 行 / **22096** 字节，
> 承 `-62` ~ `-77` 体例，含 P2-139 / P2-140 / P2-141 全文与 P2-116 的更正段）。
> **待决事项（均已记录在 `docs/drafts/backend-review-findings.md`，不因此停下）**：
> ①P2-115（**最要紧，写入前置条件**）：findings 字节上限贴顶，建议 `MAX_LINES=2000` /
> `MAX_BYTES=200000`，只需同步 `cron-combined-task.md:14`；
> ②P2-136 三条修法择一（SDK 新增公开 API / demo 改路径 / demo 拒收）；
> ③P2-135 两种修法（spec 变严 vs 运行时放宽）；④P2-127 选项②③；⑤P2-126；⑥P2-129；⑦P2-128；
> ⑧P2-125；⑨P2-123；⑩P2-124；⑪P2-90；⑫P2-122;⑬P2-120;⑭P2-119;⑮P2-121;
> ⑯P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 /
> P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status / 另三份零覆盖设计稿 ——
> **均不在本轮范围内，本轮不因此停下**。

> **第 378 轮：Java SDK（代码）+ API 文档（文档，doc round 278）**：
> **健康预检**：`/api/health` → **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的），
> 37778~37781 / 37790 / 37791 轮前均空闲；HEAD 干净；指纹 `4306466b…`（**2221** 条记录）。
> **代码方向（Java SDK）：逐条核实为真，一行未改**：
> ① **零覆盖缺口** —— 接口的 **25** 个方法**逐个**有测试（`CortexMemClientImplTest` 里 **101** 个 `@Test`）；
> ② README 的**降级行为表 12/12** 与实现逐条相符（哪些返空值、哪些带 `error`/`fell_back` 标记、哪些**抛出**）；
> ③ README 的 **196 = 143 client + 46 spring-ai + 7 starter** 逐项精确相符，且 `mvn -o test` 全绿；
> ④ SDK 打的 **23** 个路径 vs 活体 OpenAPI 的 **66** 路径 **23/23** 全对
> （**阳性对照**：编造一个端点 ⇒ 正确 MISS）。
> **文档方向（API 文档，doc round 278）：三条新条目，同属一类形状 ——
> 机器可读契约（活体 OpenAPI）说的不是实现真正做的事**：
> ① **P2-142 —— `PUT /api/mode` 的请求体 schema 串到了另一个端点**：
> `ModeController` 的内层 record 与 `dto.ApiRequests` 的 record **同名**（`ModeSwitchRequest`），
> 而 **springdoc 按简单类名解析请求体 schema** ⇒ 两个 record **归并成一个**，
> 活体 `/v3/api-docs` 里只剩一个 `ModeSwitchRequest`，**两个端点都指向它**；它宣告必填的 `mode`，
> 而实现只认 `mode_id`。**活体实测（新鲜实例 37790）**：
> | 请求体 | `PUT /api/mode` | `POST /api/modes` |
> |--------|------------------|-------------------|
> | `{"mode_id":"code--zh"}` | **200** | 400「Mode ID is required」 |
> | `{"mode":"code"}` | **400** | **200** |
> ⇒ **按 OpenAPI 生成的客户端会把 `mode` 发给 `PUT /api/mode`，必然收到 400**。
> 同一端点的 **3** 处响应示例也全是 camelCase，而 `GET /api/mode` 的活体键是
> `mode_id`/`name`/`description`/`version`/`observation_types`/`observation_concepts`（snake_case）。
> **全仓仅此一处同名 record 归并**（`grep` 全部 `record` 声明，简单名重复的只有它）。
> ② **P2-143 —— `ObservationEntity` 的 schema 双向失配**：
> **5 个幽灵属性**（`filesRead`/`filesModified`/`contentHash`/`discoveryTokens`/`embeddingModelId`，
> 实现**从不输出**）** + 3 个真实属性**（`content_hash`/`discovery_tokens`/`embedding_model_id`，
> OpenAPI**根本没宣告**）。根因：**springdoc 解析 schema 用它自己的 mapper，不套用**全局
> `spring.jackson.property-naming-strategy: SNAKE_CASE` ⇒ 没有 `@JsonProperty` 的多词字段，
> 声明出来是 camelCase、运行时输出是 snake_case。**只给另外两个实体也查过**：
> `SummaryEntity`/`UserPromptEntity` 的多词字段**全部**带 `@JsonProperty` ⇒ 无此问题；
> `SessionEntity`/`PendingMessageEntity` 同形态但**没进 OpenAPI** ⇒ 无对外失实。
> ③ **P2-144 —— `PUT /api/mode` 接受不存在的 `mode_id`，并让响应自相矛盾**：
> `ModeService.loadMode` 在模式文件找不到时**兜底** `catch (Exception e)` ⇒ `setActiveMode` **永不抛**
> ⇒ controller 的 400 分支是**死代码** ⇒ 活体 OpenAPI 宣告的「Invalid mode ID」**永远不发生**；
> 而 `ModeResponse.modeId` 在成功分支填的是**请求的 id**：实测 `{"mode_id":"bogus"}` → **200**，
> 响应体 `{"mode_id":"bogus","name":"Code Development",…}`（名字/描述/版本全是默认模式 `code` 的，
> 唯独 `mode_id` 回显了没生效的 `"bogus"`），而紧接着 `GET /api/mode` 仍是 `mode_id=code`
> ⇒ **响应体自相矛盾**。**唯一可达的 400 是空体**（`mode_id` 为空/缺失 ⇒ **0 字节**），
> 而 OpenAPI 宣告的却是 camelCase 的 JSON 体 ⇒ **双重失实**。
> **本轮已修（三条全部零可执行行）**：内层 record 改名 `ActiveModeSwitchRequest`（解除归并）、
> 分量加 `@JsonProperty("mode_id")`（**显式钉住 wire 名**，运行时输出逐字不变 —— 全局 SNAKE_CASE 本就产出
> `mode_id`）、3 处响应示例改成与实况逐字相符、400 宣告改成「空体（0 字节）」并把 `@Operation` 描述改真；
> 实体侧 3 个字段补 `@JsonProperty`（值与策略产出**逐字相同**）、2 个 List getter 标 `@JsonIgnore`
> （wire 上那两个键归 JSON 字符串 getter 所有）。**一个必须避开的坑**：给 `filesRead` 字段补
> `@JsonProperty("files_read")` **会炸** —— Jackson 会把 List getter 与 JSON 字符串 getter 并成同一属性，
> 触发 `Conflicting getter definitions`。
> **回归测试（7 个，两重阳性对照）**：`ModeControllerOpenApiContractTest`（**5**）钉住「两个 schema 不归并」
> 「各自只宣告自己接受的那个字段」「宣告名 == 序列化名」「400 不宣告任何体」「示例用 wire 名」；
> **阳性对照①**去掉 `@JsonProperty` ⇒ 宣告名变回 `modeId`（`expected: <[mode_id]> but was: <[modeId]>`）；
> **阳性对照②**改回同名 record ⇒ 两个 schema 归并成 `[ModeSwitchRequest]`（`expected: not equal but was: <[ModeSwitchRequest]>`）。
> `ObservationEntityWireFormatTest`（**2**）里有一条是**不变量**：「OpenAPI 宣告的属性集 == 实体实际序列化的键集」；
> **阳性对照**：修前该不变量失败并**逐个点名**那 5 个幽灵属性；
> 另一条钉住 `files_read`/`files_modified`/`facts`/`concepts` 仍是 **JSON 编码字符串**而非数组。
> **文档侧顺带修**：双语 API 文档的 Start Session 请求体表**漏了 `platform_source`**（V18 引入且**确实落库**，
> 活体 DB 实测 `platform_source=codex`），两版那句「三个端点都照用」改为「**四个**」并补活体实测佐证；
> 「Set Active Mode」响应示例仍是旧名（`代码模式`/`开发工作流模式`）⇒ 双向订正为活体实测值
> （`Code Development (Chinese)`/`Software development and engineering work`）。
> `docs/swagger-annotation-guidelines.md` 补 **Q4**：请求 DTO 的**简单类名必须全仓唯一**、
> 字段 wire 名**必须显式 `@JsonProperty`** —— 两条都是本轮缺陷反推出来的。
> **探针自身错 3 处，全部先识别再采信**：①第一版引用式链接转换的正则只认 `[`X`]` 形式，
> **漏掉**显示名为 `[scope-9]` 这种**不带反引号**的 **42** 次与 **15** 次两处（对拍不通过才发现）；
> ②证据 token 校验最初把**归档文件名里的日期数字**当成证据（`-41` 归档名里的 `07`）；
> ③去掉 400 的 content 块后，springdoc **回退到返回类型**并**自动生成了 camelCase 的 `ModeResponse` schema**
> ⇒ **这是本轮自己引入的新失实陈述**，在下一轮活体对拍时被抓到并堵掉（改用显式的空示例，
> 渲染结果回到「无 body 声明」，且 `ModeResponse` **不再出现**在 schema 集合里）。
> **容量**：写入前 findings 只剩 **809** 字节，本轮做**两处零信息损失压缩** ——
> ①第 292/293 轮杠杆「缩短**已结案**条目的 Status 行」缩短 **7** 行（**只删细节、不删结论**，
> 且每个被删细节的 token 都**断言逐字存在于本归档**；硬证明：条目标题数 **132** 不变、归档链接数 **196** 不变、
> 行数 **1454** 不变、字节 **149191 → 147254**）；②**新杠杆 C5「重复引用的归档链接改用 Markdown 引用式定义」**
> —— **链接目标与显示名逐字不变**，硬证明：改动前后 (显示名, 目标) 配对的多重集**完全相同**、
> 去掉链接后的正文**逐字相同**、每个目标文件**确实存在**（**199** 条链接**零断链**复验通过）；
> 合计省 **4041** 字节 ⇒ 余量恢复到 **4850** 字节 / **26** 行。
> **变更检测与完整验收**：指纹 `4306466b…` → **`9381e653…`**（记录数 2221 → 2223）⇒ **门控全量**。
> 新鲜度：本轮**改了后端** ⇒ 豁免**不适用** ⇒ 重新构建 jar（**23:41:53**）并另起新鲜实例 **37790**
> （pid **51204**）跑验收。后端全量 `mvn -o test` **177/0/0**（原 170 + 新增 **7**）；
> 回归 **45/0/1**（exit 0）+ EXTRACTION 验收 **25/0/0**（exit 0）。
> **活体 OpenAPI 对拍**：端点 **66 → 66**、schema **55 → 56**（只多出重命名后的那一个）、
> `POST /api/modes` 的请求体 schema **逐字未变**、`ObservationEntity` 宣告集与活体键集**逐字相等**。
> **本轮新增归档**：`2026-10-20_backend-review-evidence-79.md`（**165** 行 / **13110** 字节，
> 承 `-62` ~ `-78` 体例，含 P2-142 / P2-143 / P2-144 全文）。
> **待决事项（均已记录在 `docs/drafts/backend-review-findings.md`，不因此停下）**：
> ①P2-115（**最要紧，写入前置条件**）：findings 字节上限贴顶（本轮已用压缩把余量恢复到 **4850** 字节），
> 建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，只需同步 `cron-combined-task.md:14`；
> ②P2-144 三条修法择一（拒绝非法 `mode_id` / 回显真正生效的模式 / 维持现状）；
> ③P2-136 三条修法择一（SDK 新增公开 API / demo 改路径 / demo 拒收）；
> ④P2-135 两种修法（spec 变严 vs 运行时放宽）；⑤P2-127 选项②③；⑥P2-126；⑦P2-129；⑧P2-128；
> ⑨P2-125；⑩P2-123；⑪P2-124；⑫P2-90；⑬P2-122；⑭P2-120；⑮P2-119；⑯P2-121；
> ⑰P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 /
> P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status / 另三份零覆盖设计稿 ——
> **均不在本轮范围内，本轮不因此停下**。

> **第 379 轮：Go SDK（代码）+ SDK README（文档，doc round 279）**：
> **健康预检**：`/api/health` → **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的，且其 jar 起于
> 2026-10-20 20:57 ⇒ **不含第 378 轮的后端修复**，不可用于对拍）；37778~37781 / 37790 / 37791 轮前均空闲；
> HEAD 干净（`71b03b9` = `origin/main`）；指纹 `9381e653…`（**2223** 条记录）。
> **代码方向（Go SDK）**：**24** 个请求点与活体 OpenAPI 的 **71** 个操作**逐个对得上**
> （判据是**动词 + 路径形状**；**阳性对照**：编造端点正确 MISS）；**DTO 的 json tag 与活体响应键逐字相符**
> （`observations`/`strategy`/`fell_back`/`count`、`items`/`hasMore`、`observations`/`count`）；
> `gofmt` 干净、`go vet` 干净、`go build` OK、`test-all.sh` **9/9** 全绿；
> README 的**方法表 27 行与接口 27 个方法逐个相符**（无缺、无幻影）。
> **文档方向（SDK README，doc round 279）：P2-145 —— 同一句话在两种语言里给了两个数**：
> | 文件 | 原文 | 方法数 |
> |------|------|--------|
> | `README.md`（EN） | "The checks are not decoration, and the **two** capture methods are the clearest case." | **2** ❌ |
> | `README-zh-CN.md`（ZH) | 「这些检查不是装饰。其中**三个** capture 方法最能说明问题」 | **3** ✅ |
> 两行**出自同一个 commit** `6902968`（`git log -L` 逐行核对）。
> **实现站在中文那一边**：`client.go` 的捕获方法**三个**，`client_methods.go:53/65/80` 显示三个**全都**走
> `doFireAndForget` ⇒ **三者都是 fire-and-forget**（吞掉后端拒绝、记一条日志、返回 nil）。
> **更要紧的是同一处遗漏**：Go 的**接口注释**只给 `RecordObservation` 标了 `(fire-and-forget)`：
> ```go
> // RecordObservation records a tool-use observation (fire-and-forget).
> // RecordSessionEnd signals session end.                 ← 没标
> // RecordUserPrompt records a user prompt.               ← 没标
> ```
> **跨家对照**：JS（`recordSessionEnd`/`recordUserPrompt` **都**标了，README 还有
> `#### Capture (fire-and-forget)` 分组标题）与 Python（`record_session_end`/`record_user_prompt` **都**标了，
> README 有 `### Capture (fire-and-forget)` 分组标题）两家**齐**；**Go 缺两个**，**Java 三个全缺**（同型，结转）。
> **本轮已修（Go 侧，零可执行行）**：英文「two」改「three」并点明三者**都是** fire-and-forget；
> 中文版同步加同样点明；两版方法表的 `Capture` 组标注 (fire-and-forget)；`client.go` 接口注释给另两个方法补标注。
> **P2-128 的 Go 侧同批结转**：Go 的四个下限本就由 **5** 个测试钉住（本轮实测那 3 个内部测试全 PASS），
> 缺的是**文档** ⇒ 双语 README 选项表补齐四行下限（与 JS / Python 措辞同体例）+ 三个 Option 的 doc 注释补下限。
> **一个「差点记成缺陷」的候选已被证伪并留档**：README 首屏的「**25 methods**」曾被本轮判为陈旧计数
> （它自己的方法表列了 **27** 个）—— 查 `git log -L` 后**推翻**：`57353ac`（"fix: JS/Go SDK README API method
> count 26→25 (close is lifecycle, not API)"）明确记了计数口径 ⇒ 27 − 2（`Close`/`String`）= **25**，**是真的**；
> 方法表 27 行与接口 27 个方法**逐个相符** ⇒ **不是缺陷，不改**。
> **测试计数也逐项复核为真**：三种口径实测 —— 顶层 `--- PASS` 根模块 **277** / `examples/http-server` **5**，
> 含子测试 **306** / **33**，`func Test` 声明 **210** / **5**；只有**含子测试**的那一种与 README 的分解吻合
> （根模块 **306** = core 239 + dto 67、`examples/http-server` **33**）⇒ **372 = 306 + 8 + 13 + 12 + 33** 精确相符。
> **探针自身错 2 处，全部先识别再采信**：①第一版「Go 请求点」的正则把 Go 字符串字面量的**引号**也带进了路径
> （`"/api/health"`）⇒ 对拍全线 MISS；②第二版凭**猜的动词**（`PUT /api/session/{id}/user`、
> `GET /api/memory/observations/{id}`）判成两处 MISS，实测源码用的是 **PATCH** 与 **PATCH/DELETE**，
> 活体 OpenAPI 也只有 PATCH ⇒ **是探针猜错，不是 SDK 错**。
> **自检抓到一处本轮自己写错的数**：归档索引行里把「根模块」与它的子包 `dto` **重复相加**写成 **382**，
> 逐口径复算后改为 **372**（含子测试口径）并注明顶层口径为 **315**，**写盘前改正**。
> **容量**：findings 尚有 **4850** 字节余量（上一轮腾出的）⇒ **本轮未再压缩**；
> 新增条目与 P2-128 的状态更新共占 **1074** 字节，余量 **3776** 字节 / **24** 行。
> **变更检测与完整验收**：指纹 `9381e653…` → **`581d954a…`**（记录数 2223 → 2223）⇒ **门控全量**。
> 新鲜度：本轮**未改后端**，但 37777 的 jar **不含第 378 轮的后端修复** ⇒ 仍另起新鲜实例 **37790**
> （jar **23:44**、pid **82690**）跑验收。后端全量 `mvn -o test` 保持 **177/0/0**；
> 回归 **45/0/1**（exit 0）+ EXTRACTION 验收 **25/0/0**（exit 0）。
> **本轮新增归档**：`2026-10-20_backend-review-evidence-80.md`（**126** 行 / **10552** 字节，
> 承 `-62` ~ `-79` 体例，含 P2-145 全文与 P2-128 的 Go 侧结转记录）。
> **待决事项（均已记录在 `docs/drafts/backend-review-findings.md`，不因此停下）**：
> ①P2-115（**最要紧，写入前置条件**）：findings 字节上限贴顶（本轮已用压缩把余量恢复到 **3776** 字节），
> 建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，只需同步 `cron-combined-task.md:14`；
> ②P2-144 三条修法择一（拒绝非法 `mode_id` / 回显真正生效的模式 / 维持现状）；
> ③P2-128 剩余两项（Java 要不要对齐 100ms 下限 / Java README 要不要补下限 / Python·JS·Java 要不要补下限回归测试）；
> ④P2-136 三条修法择一（SDK 新增公开 API / demo 改路径 / demo 拒收）；
> ⑤P2-135 两种修法（spec 变严 vs 运行时放宽）；⑥P2-127 选项②③；⑦P2-126；⑧P2-129；⑨P2-128；
> ⑩P2-125；⑪P2-123；⑫P2-124；⑬P2-90；⑭P2-122；⑮P2-120；⑯P2-119；⑰P2-121；
> ⑱P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 /
> P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status / 另三份零覆盖设计稿 ——
> **均不在本轮范围内，本轮不因此停下**。

> **第 380~382 轮的巡检报告未写入本文件**（本文件被 `.gitignore` 忽略，故不随提交留档）——
> 三轮的逐字内容分别在 [`…-81`](../archive/2026-10-20_backend-review-evidence-81.md)、
> [`…-82`](../archive/2026-10-20_backend-review-evidence-82.md)、
> [`…-83`](../archive/2026-10-20_backend-review-evidence-83.md) 三个归档里（基线区块的 `note` 已逐轮记录摘要）。

> **第 383 轮：Backend（代码）+ API 文档（文档，doc round 282）**：
> **健康预检**：`/api/health` → **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的，jar 起于
> 2026-10-20 02:17 ⇒ **不含第 378 轮的后端修复**，不可用于对拍）；37778~37781 / 37790 / 37791 轮前均空闲；
> HEAD 干净（`b7d3cf9` = `origin/main`）；指纹 `386fe87f…`（**2223** 条记录）。
> **代码方向（Backend）**：**P2-152**（活体 OpenAPI 的 `FeedbackRequest` 描述谎报「未实现」——
> **事后漂移**，活体 OpenAPI 全量扫描仅此一处）与 **P2-153**（**51** 个 schema 宣告 `type: string` 却带
> 对象/数组 example；**PATCH 请求体**按 spec 发字符串 ⇒ 活体 **400**）—— 详见基线 `note` 与归档 `-84`。
> **文档方向（API 文档，doc round 282）**：**P2-154**（双语四处计数写「67」⇒ 活体 **71** 个操作 /
> **66** 条路径；**事后漂移**）。
> **探针自身的三处错，全部先识别再采信**：①**机制探针**先后否掉三种写法（`implementation=` 会吃掉
> example 且 type 仍是 string；`@ArraySchema` + `implementation = Map.class` 让 `items` 变成
> `{"type":"string"}` —— 那是**新造**的失实）⇒ 只在一次构建里同时试了四种写法再批量；
> ②批量改完后 `GET /api/timeline` 仍是 `type: string` —— 原因正是**探针 C 的残留**
> `implementation = List.class` 忘了清，而探针**早已预测**了这个失败模式；
> ③**不变量测试**最初只读 `Content.schema()`、漏了 `Content.array()` ⇒ 数组分支整个漏掉
> （补上后，数组分支的变异测试才真的能失败）。
> **校验脚本自身错了两次**：压缩的硬证明里，①把「目标字符串多重集」当不变量 —— 定义块每个归档**只列一次**，
> 而行内写法重复 N 次 ⇒ 重数**本就不该相等**；正确的不变量是「每个链接用法**解析后**指向的文件的多重集相等」；
> ②`find` 的相对路径写错导致「jar 不新鲜」的**假警报**。
> **容量**：findings 写入前只剩 **6 行 / 672 字节** ⇒ 本轮新增 **C6**（被引用 ≥2 次的 **28** 个归档路径转
> 引用式，显示名与目标不变，零信息损失，省 **1750** 字节）与 **C7**（**38** 处多行 `- **Status**` 的续行
> 并回一行，**字符流守恒**，让出 **136 行**、省 272 字节）；另缩短 4 条已结案 Status 行
> （逐 token 断言被删细节**逐字存在于该行链接的归档**里）。终值 **1394 行 / 149693 字节**。
> **与第 382 轮「折行重排不做」的区别**：那轮的对象是**叙述正文**且只找到 2 段合格；本轮的对象是
> **Status 元数据行**，不动问题陈述与决策推理。
> **变更检测与完整验收**：指纹 `386fe87f…` → **`db704ad3…`**（记录数 2223 → **2224**，本轮新增 1 个
> 后端测试文件）⇒ **门控全量**。新鲜度：本轮**改了后端**，另起新鲜实例 **37790**（jar **09:53**、
> pid **32562**；`find backend/src -newer <jar>` **为空** ⇒ jar 比全部后端源文件都新）。
> 后端全量 `mvn test` **178/178**；回归 **45/0/1**（exit 0）+ EXTRACTION 验收 **25/0/0**（exit 0）。
> **本轮新增归档**：`2026-10-20_backend-review-evidence-84.md`（**398** 行 / **22748** 字节，
> 承 `-62` ~ `-83` 体例，含 P2-152 / P2-153 / P2-154 全文与压缩记录）。
> **待决事项（均已记录在 `docs/drafts/backend-review-findings.md`，不因此停下）**：
> ①P2-115（**最要紧**）：findings 写入前只剩 6 行 / 672 字节，本轮靠 C6/C7 两个杠杆把余量恢复到
> **106 行 / 307 字节**；建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，只需同步 `cron-combined-task.md:14`；
> ②P2-144 三条修法择一；③P2-145 的 Java 侧；④P2-146 的 Go / Java 侧；⑤P2-128 剩余两项；
> ⑥P2-136；⑦P2-135；⑧P2-127；⑨P2-126；⑩P2-129；⑪P2-125；⑫P2-123；⑬P2-124；⑭P2-90；⑮P2-122；
> ⑯P2-120；⑰P2-119；⑱P2-121；⑲P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 /
> P1-1 / P2-79+P2-80 / P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status / 另三份零覆盖设计稿 ——
> **均不在本轮范围内，本轮不因此停下**。

> **第 384 轮：Java SDK（代码）+ SDK README（文档，doc round 283）**：
> **健康预检**：`/api/health` → **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的，jar 起于
> 2026-10-20 02:17 ⇒ 不含第 378 轮的后端修复，不可用于对拍）；37778~37781 / 37790 / 37791 轮前均空闲；
> HEAD 干净（`fe9a5d7` = `origin/main`）；指纹 `db704ad3…`（**2224** 条记录）；`doc-growth-check` exit 0。
> **代码方向（Java SDK）**：**P2-155**（Java 独有的「绝对路径」校验零覆盖 + 零记载）与
> **P2-156**（「吞错 vs 抛错」契约零覆盖）—— 本轮**只加测试、实现一行未改**，共 **17** 个新用例
> （9 + 8），另有 **P2-145 的 Java 侧**：接口三个捕获方法的 Javadoc 全部补上 fire-and-forget 说明，
> 并区分「后端失败被吞」与「客户端校验先抛」——详见基线 `note` 与归档 `-85`。
> **文档方向（SDK README，doc round 283）**：**P2-146 的 Java 侧**（双语「空 tool_name」同型句改反事实
> 措辞 + 写明「客户端拦下、不发请求」）、**P2-157**（Design Notes 自相矛盾）与 **P2-155 的文档侧**
> （补「Java 独有规则」一段）；另把双语首屏的测试计数 **196 → 213**（client **143 → 160**）并加上
> **测量日期与取数方式**（各模块 surefire 报告）。
> **探针自身的三处错，全部先识别再采信**：①数「client 模块的测试数」时用单文件
> `grep -c "@Test"` 得 **118**，而 surefire 实收 **115** —— 差在**同一个文件里还有第二个顶层测试类**
> `CortexMemErrorMessageTest`（3 个用例）⇒ **静态 `@Test` 计数只在逐文件与运行时分层对齐时才等于运行数**；
> ②第一版把两个变异叠在一起注入 ⇒ 失败归因不清，拆成两次单变量注入后才各自点名；
> ③变异 3 在变异 2 尚未还原时跑 ⇒ 报 2 红而不是 1 红（结论无误，但归因必须单变量）。
> **校验脚本自身错了一次**：归档 `-85` 首版把「腾出 5355 字节 / 65 行」写成净效果，而 `- **Scope**`
> 一行实际**留在了工作文件**（与本仓其余条目体例一致）⇒ 真实口径是「归档侧持有 65 行 / 5355 字节，
> 工作文件真正迁出 64 行 / 5307 字节」。按「归档错了只能删除重建」的规矩**删除重建了一版**，
> 并把跨两个文件（归档引用 findings 终值、findings 引用归档）做成**联动定点**：两边数字互校通过。
> **变更检测与完整验收**：指纹 `db704ad3…` → **`878412fd…`**（记录数 **2224** 不变）⇒ **门控全量**。
> 新鲜度：本轮**未改后端**（改的是 Java SDK 与 README），仍另起新鲜实例 **37790**（jar **10:03**、
> pid **16151**；`find backend/src -newer <jar>` **为空** ⇒ jar 比全部后端源文件都新）。
> Java SDK 全量 `mvn test` **213/213**（client 160 + spring-ai 46 + starter 7）；后端全量 `mvn test`
> **178/178**；回归 **45/0/1**（exit 0）+ EXTRACTION 验收 **25/0/0**（exit 0）。
> **本轮新增归档**：`2026-10-20_backend-review-evidence-85.md`（**266** 行 / **19146** 字节，
> 承 `-62` ~ `-84` 体例，含 P2-155 / P2-156 / P2-157 全文 + P2-88 证据段 + 压缩记录）。
> **待决事项（均已记录在 `docs/drafts/backend-review-findings.md`，不因此停下）**：
> ①P2-115（**最要紧**）：findings 写入前只剩 106 行 / 307 字节，本轮靠 C8 恢复到
> **161 行 / 2334 字节**；建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，只需同步 `cron-combined-task.md:14`；
> ②P2-144 三条修法择一；③**P2-145 的 Java 侧本轮已结转完成**、P2-146 的 **Go 侧仍结转**；
> ④P2-128 剩余两项；⑤P2-136；⑥P2-135；⑦P2-127；⑧P2-126；⑨P2-129；⑩P2-125；⑪P2-123；⑫P2-124；
> ⑬P2-90；⑭P2-122；⑮P2-120；⑯P2-119；⑰P2-121；⑱P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 /
> P2-88 / P2-100 / P1-1 / P2-79+P2-80 / P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status /
> 另三份零覆盖设计稿 —— **均不在本轮范围内，本轮不因此停下**。


> **第 385 轮：Go SDK（代码）+ SDK README（文档，doc round 284）**：
> **健康预检**：`/api/health` → **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的，jar 起于
> 2026-10-20 02:17 ⇒ 不含第 378 轮的后端修复，不可用于对拍）；37778~37781 / 37790 / 37791 轮前均空闲；
> HEAD 干净（`014a0f4` = `origin/main`）；指纹 `878412fd…`（**2224** 条记录）；`doc-growth-check` exit 0。
> **代码方向（Go SDK）**：**P2-158** —— **35** 个校验用例用裸 `cortexmem.NewClient()`（默认打本机
> **常驻实况后端** 37777），全仓**零「请求数」断言** ⇒ 校验的**顺序**没被钉住；且后端**不校验 `cwd`**
> ⇒ 一旦校验回归，捕获方法的用例会**往实况库写行**，而旧断言照样绿。本轮**只改测试、实现一行未改**：
> 35 个用例改到 hermetic server（收到请求即判失败）+ 1 个阳性对照。
> **文档方向（SDK README，doc round 284）**：**P2-146 的 Go 侧结转完成**（双语反事实措辞 + 写明
> 「SDK 返回 `ValidationError`、一个请求也不发」+ Go 独有的「fire-and-forget 只吞后端错误」细节）；
> 另把双语三处测试计数 **372 → 373** 并同步日期与取数方式。
> **变异测试**：把 `toolName` 校验挪到请求**之后** ⇒ 该用例失败且失败来自**新守卫**
> （`client-side validation must run before the request, but the server received 1 request(s)`），
> **旧断言没有触发**（错误消息一字不变）；再把该用例临时改回旧写法 ⇒ **照样 PASS**（0.25s ＝ 一次真实
> 网络往返）。变异后按 sha256 逐字节还原。
> **探针自身的一处错**：数「哪些用例没用 httptest」时，第一版差点把**函数体内出现
> `cortexmem.NewClient()` 但同时带 `WithBaseURL`** 的用例也算进去 ⇒ 判定条件必须写成**函数级**的，
> 不能只 grep 一个词。
> **变更检测与完整验收**：指纹 `878412fd…` → **`65671383…`**（记录数 **2224** 不变）⇒ **门控全量**。
> 新鲜度：本轮**未改后端**（改的是 Go SDK 测试与 README），仍另起新鲜实例 **37790**（jar **10:03**、
> pid **64345**；`find backend/src -newer <jar>` **为空** ⇒ jar 比全部后端源文件都新）。
> Go SDK `test-all.sh` **9/9** 模块全绿、**373** 个测试；后端全量 `mvn test` **178/178**；
> 回归 **45/0/1**（exit 0）+ EXTRACTION 验收 **25/0/0**（exit 0）。
> **本轮新增归档**：`2026-10-20_backend-review-evidence-86.md`（**93** 行 / **6606** 字节，
> 承 `-62` ~ `-85` 体例，含 P2-158 全文与压缩记录）。
> **待决事项（均已记录在 `docs/drafts/backend-review-findings.md`，不因此停下）**：
> ①P2-115（**最要紧**）：findings 写入前余量 161 行 / 2334 字节，本轮未压缩，终值
> **1342 行 / 148932 字节**（余 158 行 / 1068 字节）；建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，
> 只需同步 `cron-combined-task.md:14`；②P2-144 三条修法择一；③**P2-146 四家已全部结转完成**；
> ④P2-128 剩余两项；⑤P2-136；⑥P2-135；⑦P2-127；⑧P2-126；⑨P2-129；⑩P2-125；⑪P2-123；⑫P2-124；
> ⑬P2-90；⑭P2-122；⑮P2-120；⑯P2-119；⑰P2-121；⑱P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 /
> P2-88 / P2-100 / P1-1 / P2-79+P2-80 / P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status /
> 另三份零覆盖设计稿 —— **均不在本轮范围内，本轮不因此停下**。


> **第 386 轮：Python SDK（代码）+ SDK README（文档，doc round 285）**：
> **健康预检**：`/api/health` → **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的，jar 起于
> 2026-10-20 02:17 ⇒ 不含第 378 轮的后端修复，不可用于对拍）；37778~37781 / 37790 / 37791 轮前均空闲；
> HEAD 干净（`55e748b` = `origin/main`）；指纹 `878412fd…`（**2224** 条记录）；`doc-growth-check` exit 0。
> **代码方向（Python SDK）**：**P2-160** —— 三个 fire-and-forget 捕获方法里，**7** 个 `_fire_and_forget`
> 用例全部只驱动 `record_observation`，另两个方法的「吞错」契约**一个用例都没有**；本轮**只加测试**，
> 新增 **3** 个用例（2 个吞错 + 1 个重试耗尽后仍吞错），每个都断言「请求确实发出」＋「错误被吞掉」。
> **文档方向（SDK README，doc round 285）**：**P2-159**（四家 README 同一句断言共 **8** 处、**6** 处失实；
> 本轮改 Python 双语两处，**Go / JS 各 2 处结转**）、**P2-161**（四家「哪些方法吞错」的集合不一致，
> 无文档点出 ⇒ 只记录不实施），另把双语首屏计数 **457 → 460** 并同步日期与取数方式。
> **缺口证明（变异实验）**：把 `record_session_end` 与 `record_user_prompt` 改成抛错路径后，
> 全量 **457** 个测试**仍然全绿**（两个变量各自单独注入）；补完新用例后重放同样变异 ⇒
> **恰好 3 个新用例红、其余 457 绿**。
> **探针自身的三处错**：①对拍四张「必填参数」表时**用原始方法名求交**，四张表「两两毫无交集」——
> 真相是命名风格不同（snake_case / PascalCase / camelCase）⇒ 判定必须**先归一化**再求交；
> ②第一版只查 EN 版 ⇒ 按断言清扫后才知道同一句在仓库里有 **8** 处；
> ③找「可迁出的证据段」时按 `- **Severity**` 定位决策段起点，漏掉没有该字段的条目。
> **校验脚本自身错了一次**：归档 `-87` 首版的**头部**用了 f-string，而头部里的占位符被写成了
> `{{lines_no}}` ⇒ 渲染成**字面量** `{lines_no}`，归档「看起来完全正常」却宣称着一个它没有的数字。
> 按「归档错了只能删除重建」**删除重建了一版**并独立回读校验。
> **变更检测与完整验收**：指纹 `878412fd…` → **`619cf7b9…`**（记录数 **2224** 不变）⇒ **门控全量**。
> 新鲜度：本轮**未改后端**（改的是 Python SDK 测试与 README），仍另起新鲜实例 **37790**（jar **10:03**、
> pid **17075**；`find backend/src -newer <jar>` **为空** ⇒ jar 比全部后端源文件都新）。
> Python SDK `pytest` **460/460**；后端全量 `mvn test` **178/178**；回归 **45/0/1**（exit 0）+
> EXTRACTION 验收 **25/0/0**（exit 0）。
> **本轮新增归档**：`2026-10-20_backend-review-evidence-87.md`（**227** 行 / **17327** 字节，
> 承 `-62` ~ `-86` 体例，含 P2-159 / P2-160 / P2-161 全文 + P2-112 证据段 + 压缩记录）。
> **待决事项（均已记录在 `docs/drafts/backend-review-findings.md`，不因此停下）**：
> ①P2-115（**最要紧**）：findings 写入前余量 158 行 / 1068 字节，本轮用 C8 腾出后终值
> **1308 行 / 147382 字节**（余 192 行 / 2618 字节）；建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，
> 只需同步 `cron-combined-task.md:14`；②P2-144 三条修法择一；③**P2-161 本轮新增**（四家吞错集合不一致，
> 三条候选修法）；④P2-128 剩余两项；⑤P2-136；⑥P2-135；⑦P2-127；⑧P2-126；⑨P2-129；⑩P2-125；
> ⑪P2-123；⑫P2-124；⑬P2-90；⑭P2-122；⑮P2-120；⑯P2-119；⑰P2-121；⑱P2-112 / P2-113 / P2-93 /
> P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 / P2-82 / P2-81 / CI / LICENSE /
> 13 条旧条目缺 Status / 另三份零覆盖设计稿 —— **均不在本轮范围内，本轮不因此停下**。


> **第 387 轮：JS/TS SDK（代码）+ SDK README（文档，doc round 286）**：
> **健康预检**：HEAD == origin/main == `7135e66`、worktree 干净；`/api/health` → **200**；
> 37778~37781 / 37790 / 37791 **全空闲**；指纹 `619cf7b9…` == 基线；`doc-growth-check` exit 0。
> **代码方向（JS/TS SDK）**：本轮共 **5** 条，全部在 JS SDK 内。
> ① **P2-162（已修，JS 侧，只加测试，实现一行未改）** —— 同族里唯一没被钉住的那一个（**第四次**出现同型）。
> 三个捕获方法都走 `doFireAndForget`（`client.ts:182`/`:195`/`:209` → `:764`），而 `fire-and-forget` 区块的
> **6** 个用例**全部只驱动 `recordObservation`**。**缺口证明**：把另两个改成抛错路径后全量 **266** 个测试
> **仍然全绿**（两个变量各自单独注入）。修法：新增 **3** 个用例，每个都**同时**断言「请求确实发出」与
> 「错误被吞掉」。**变异测试**：变异 A（`recordSessionEnd` 改抛错）⇒ 恰好 **1** 红；变异 B（`recordUserPrompt`
> 改抛错）⇒ 恰好 **2** 红；两次按 sha256 `194520e8…` 逐字节还原。
> ② **P2-165（已修，JS 侧，只加测试）** —— ⭐ 同族的**第四种形态**：不是「某个方法没测」，而是
> 「**某个字段**没被钉住」。旧用例只做 `.rejects.toThrow('at least one field')` **子串匹配**，前缀与错误类型
> 都没钉；而 P2-163 修好后 README 明确写死了这三样 ⇒ 文档会漂移而测试不会响。**缺口证明**（截短消息 /
> 换 `APIError` 两处改坏都全绿）。修法：新增 **1** 个用例断言错误类型 + `field` + 完整消息逐字相等 + 不发请求。
> **变异测试**：变异 1 ⇒ 恰好 **1** 红且**两个旧用例仍绿**（归因分离成立）；变异 2 ⇒ 恰好 **1** 红。
> ③ **P2-166（已修，JS 侧）** —— 「**只有捕获路径重试**」零钉住。**缺口证明**：给 `startSession`（非捕获，
> 走 `requestJSON`）加重试 ⇒ 全量 **270** 个测试**仍然全绿**。四家重试矩阵实测：JS / Python / Go 各只重试
> **3** 个捕获方法，**Java 重试 10 个**（另有 `triggerRefinement`、`submitFeedback`、`updateObservation`、
> `deleteObservation`、`getLatestExtraction`、`getExtractionHistory`、`triggerExtraction`）⇒ 跨家移植时
> `maxRetries` 管到的调用范围不同。修法：加 **3** 个用例（含**阳性对照**）＋ 双语 README 新增「重试策略」一节。
> **变异测试**：变异 1（`startSession` 加重试）⇒ 恰好 **1** 红；变异 2（`submitFeedback` 加重试）⇒ 恰好 **1** 红。
> **文档方向（SDK README，doc round 286）**：两条跨家断言在 JS README 里失实，**按断言清扫**逐处找齐。
> ④ **P2-163（已修，JS 双语 2 处；Go / Python / Java 各 2 处结转）** —— 四家 README 都写「空更新的**错误消息
> 完全相同**」，按断言清扫共 **8** 处，**8 处全失实**：消息**主体**逐字相同，但**错误类型与前缀**每家不同，
> Java 甚至是**无前缀**的 `IllegalArgumentException`。**写入即假**：8 处同源于 `f188553`（**2026-10-02**），
> 该提交当时 Go 与 Java 的实际形态**已经**是现在这样。修法是**把断言改准**（四行对照表），**不改任何 SDK 行为**。
> ⑤ **P2-164（已修，JS 双语 4 处）** —— 「另三家统一称项目参数为 `project_path`」**两种读法下都失实**：
> 四家捕获方法的 **wire 字段都是 `cwd`**（程序化对拍：Go `json:"cwd"`、Python `"cwd": project_path`、
> Java `map.put("cwd", projectPath)`），SDK 层叫法是 Go `ProjectPath` / Python `project_path`、
> Java `projectPath` ⇒ 只有 Python 真这么叫；`:271` 那句还带「throughout」而另三家的管理类方法用的是
> `project` 或 `projectPath`。**写入即假**：四处均来自 `6902968`（**2026-10-03**，与 P2-159 同一提交）。
> ⭐ 本轮**差点判错一处**：Java 请求 DTO **没有** `@JsonProperty`、record 组件名是 `projectPath`，
> 若走 Jackson 默认策略，wire 上会是 camelCase 而后端只读 `cwd` ⇒ 捕获会**静默丢掉项目**；
> 查下去才发现 Java SDK **不走 Jackson 序列化 DTO**，而是调用**手写的 `toWireFormat()`** ⇒ wire 名是 `cwd` ✅。
> ⭐ **「没匹配上」≠「不成立」** —— 先怀疑自己的判据，再怀疑被证明物。
> **文档增长**：findings 终值 **1274 行 / 148339 字节**（余 226 行 / 1661 字节），`doc-growth-check` exit 0。
> **第三次启用 C8**（第 254 轮规则）：P2-93 / P2-95 / P2-90 三条 ⏸ 条目的证据段整体迁入归档 -88
> （归档侧 **316 行 / 22370 字节**；工作文件真正迁出 36 / 25 / 26 行，全文件净 **−34 行 / −1521 字节**）。
> **变更检测**：指纹 `619cf7b9…` → `de4b1e50…`（记录数 **2224** 不变）⇒ **有代码变化** ⇒ 跑了完整验收。
> **新鲜度**：本轮**未改后端**（改的是 JS SDK 测试与 README），仍另起新鲜实例 **37790**（jar **13:13**、
> pid **57379**；`find backend/src -newer <jar>` **为空** ⇒ jar 比全部后端源文件都新）。
> JS SDK `vitest` **273/273**（client **255** + truncated-body 5 + http-server 示例 8 + 错误契约 5）；
> 后端全量 `mvn -o test` **178/178**；回归 **45/0/1**（exit 0）+ EXTRACTION 验收 **25/0/0**（exit 0）。
> ⭐ **一次偶发失败（如实记录，原因未确定）**：收尾复跑时**第一次**全量跑出现
> `examples/http-server/error-contract.test.ts` 的 **5** 个用例全红 —— 该文件**本轮一行未改**
> （`git diff` 只含 `src/__tests__/client.test.ts`）。**5 个同时红**指向 `beforeAll` 层
> （`app.listen(0)` 起 express server），且这些断言**不依赖后端** —— demo 默认打 37777，
> 而 37777 在 14:29~14:32 **零日志行** ⇒ 那些请求根本没到后端，故与后端异常无关。
> 随后**连跑 22 次全量**（含单独跑该文件 1 次）**全部 273/273 绿** ⇒ **未能复现，不作断言**。
> 本轮**改动的** `client.test.ts` 在这 22 次里**每次都全绿**；验收结论不受影响
> （回归 + Phase3 跑在**独立的 37790** 上，与 JS SDK 测试无关）。
> **本轮新增归档**：`2026-10-20_backend-review-evidence-88.md`（**316** 行 / **22370** 字节，
> 承 `-62` ~ `-87` 体例，含 P2-162 ~ P2-166 全文 + P2-93 / P2-95 / P2-90 证据段 + 压缩记录）。
> **待决事项（均已记录在 `docs/drafts/backend-review-findings.md`，不因此停下）**：
> ①P2-115（**最要紧**）：findings 终值 **1274 行 / 148339 字节**（余 226 行 / 1661 字节）；
> 建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，只需同步 `cron-combined-task.md:14`；
> ②P2-144 三条修法择一；③P2-161（三条候选）；④**P2-163 本轮新增**：Go / Python / Java 侧各 2 处结转；
> ⑤**P2-166 本轮新增**：Java README 那句「另三家只重试捕获路径」**本身也不完整**（只提「两个抽取读方法」，
> 漏了另外 5 个）—— 留给 Java 侧；⑥P2-128 剩余两项；⑦P2-136；⑧P2-135；⑨P2-127；⑩P2-126；⑪P2-129；
> ⑫P2-125；⑬P2-123；⑭P2-124；⑮P2-90；⑯P2-122；⑰P2-120；⑱P2-119；⑲P2-121；⑳P2-112 / P2-113 /
> P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 / P2-82 / P2-81 / CI / LICENSE /
> 13 条旧条目缺 Status / 另三份零覆盖设计稿 —— **均不在本轮范围内，本轮不因此停下**。

> **第 388 轮：Demo（代码）+ Java demo README（文档，doc round 287）**：
> **健康预检**：HEAD == origin/main == `6548428`、worktree 干净；`/api/health` → **200**；
> 37778~37781 / 37790 / 37791 **全空闲**；指纹 `de4b1e50…` == 基线；`doc-growth-check` exit 0。
> **代码方向（Demo）**：本轮共 **4** 条，全部在 demo 侧（**主源码一行未动**，加的是测试与文档；改的两个脚本只改**判定**）。
> ① **P2-167（已修，Demo 侧，只加测试）** —— Java demo 的暴露面（12 个 Controller、**44** 条 `(verb, path)`）
> **零测试钉住**；原有 5 个测试文件共 **40** 个用例里**只有 `GET /memory/health`** 有端点级测试。修法：新增
> `DemoEndpointSurfaceTest`（**3** 个用例）—— 44 条**集合相等**、无重复、钉住哪些 Controller 有 `/health`
> 兄弟端点；用**反射读注解**，不起 Spring 上下文、不连后端 ⇒ 可离线跑。变异 B / C 各恰好 **1** 红，
> 按 sha256 逐字节还原，git diff `src/main/` 为空。
> ⭐ 手打的预期集**两处都错**，被测试当场抓住，再用第二条独立来源（读注解）核对后确认「预期集错、实现对」。
> ② **P2-168（已修，Demo 侧，测试 + 文档；端点行为只记不改）** —— **2×2 活体对照矩阵**（demo 指向活后端 37777
> vs 死后端 `http://127.0.0.1:1`）：6 个静态 `/demo/*/health` 端点在**后端完全宕机**时与正常时**响应逐字相同**
> （200 `{"status":"ok",…}`）⇒ 拿它们做存活探针的监控在后端不可达时仍报「健康」；而 `/memory/health`（503
> `unavailable`）与 `/actuator/health`（503 `DOWN`）能区分 —— 同一进程、**字段同名、语义相反**。
> `MemoryController.getMemoryHealth` 的 Javadoc 早已写明第 183 轮那个坑（「用静默降级的方法当存活信号」），
> 但当时只修了 1 个、另外 6 个原样留着且**零测试零文档**。修法：新增 `StaticHealthEndpointsTest`（**7** 个用例，
> 钉住「恒 200、恒 `status:ok`、**client 零交互**」＋**阳性对照**）＋ README 补 6 条并写明
> **七条里只有两个真检查后端**。⭐ 改这 6 个端点的**行为**属对外契约变更 ⇒ **只记不实施**。
> ③ **P2-169（已修，脚本层，零行为变更）** —— `demo-v15-test.sh` 的 `curl -sf` 把**业务合法的 404** 判成失败 ⇒
> **第一个测试必然失败**；且 `-s` 使失败消息**恒为空**、不可诊断。修法：抽 `fetch()` 显式拆状态码与响应体；
> `latest` 接受 **200 与 404** 并**加断言**「404 的 body 必须含 `No extraction found`」；另两个要求 200；
> 失败消息带状态码与完整响应体。修前 **2/0/1**（exit 1）⇒ 修后 **3/0/0**（exit 0）。
> ④ **P2-170（已修，脚本层，零行为变更）** —— 两个脚本的 `check_demo` 打 `/actuator/health`，而该路径**聚合后端
> 健康** ⇒ 后端一挂就返 **503** ⇒ 脚本报「Demo is NOT running」并**跳过整个套件** ⇒ **后端故障被伪装成
> 「demo 没启动」**（假红，与 P2-168 的假绿同根反向）。修法：判据改为 `/demo/observations/status`。
> **文档方向（Java demo README，doc round 287）**：端点表补 6 条静态 health 端点并标注「静态、不问后端」；
> 新增「表是精选示例（44 条 / 12 Controller）」段；新增「**七条里只有两个真检查后端**」段；新增
> **§3.1「How this demo's paths differ from the other three demos」**。
> **其余核实为真**：端点表 **14** 条逐条活体打全部路由正确；`demo-v14-test.sh` 有 **4** 个测试函数
> （`TESTS_PASSED++` 只 3 处 ⇒ 计数口径陷阱）而 README 写 **4 tests**、为真；Python demo 与 JS demo 的
> `(verb, path)` 集合**完全相同**（26 = 26，零差集）；Go demo 路径拼写有 **2** 处不同（**P2-33 已记录**）；
> Java demo 独有 `/demo` 前缀与 `GET /chat`（另三家是 `POST /chat`）⇒ 活体验证 `POST /chat` 到 Java demo
> 返 **405**。⭐ Go 探针有 2 处盲区（`/` 是兜底 404 handler、`/observations/{id}` 用 switch 而非
> `checkMethod`）⇒ 已读源码确认，不据此下断言。
> **文档增长**：findings 终值 **1251 行 / 149671 字节**（余 249 行 / **329 字节**），`doc-growth-check` exit 0。
> **第四次启用 C8**（第 254 轮规则）：P2-79 / P2-82 / P2-75 三条 ⏸ 条目的证据段整体迁入归档 -89
> （归档侧 **96 行 / 7306 字节**；工作文件真正迁出 23 / 22 / 11 行，全文件净 **−23 行 / +1332 字节**）。
> **变更检测**：指纹 `de4b1e50…` → `c408cd68…`（记录数 **2224 → 2226**）⇒ **有代码变化** ⇒ 跑了完整验收。
> **新鲜度**：本轮**未改后端**（改的是 demo 测试、demo README 与两个脚本），仍另起新鲜实例 **37790**
> （jar **13:13**、pid **81646**；`find backend/src -newer <jar>` **为空**）。
> demo 测试 **50/50** 全绿（改前 40）；回归 **45/0/1**（exit 0）+ EXTRACTION 验收 **25/0/0**（exit 0）。
> **本轮新增归档**：`2026-10-20_backend-review-evidence-89.md`（**345** 行 / **25623** 字节，
> 承 `-62` ~ `-88` 体例，含 P2-167 ~ P2-170 全文 + P2-79 / P2-82 / P2-75 证据段 + 压缩记录）。
> **待决事项（均已记录在 `docs/drafts/backend-review-findings.md`，不因此停下）**：
> ①P2-115（**最要紧**）：findings 终值 **1251 行 / 149671 字节**（余 249 行 / **329 字节**）——
> **余量已不足一轮的写入量**；建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，只需同步 `cron-combined-task.md:14`；
> ②**P2-168 本轮新增**：是否让 demo 的 6 个静态 `/demo/*/health` 端点改为真检查后端（对外契约变更，只记不实施）；
> ③P2-144 三条修法择一；④P2-161（三条候选）；⑤P2-163（Go / Python / Java 侧各 2 处结转）；
> ⑥P2-166（Java README 那句「另三家只重试捕获路径」**本身也不完整**，只提「两个抽取读方法」、漏了另外 5 个）；
> ⑦P2-159 的 Go 侧结转；⑧P2-128 剩余两项；⑨P2-136；⑩P2-135；⑪P2-127；⑫P2-126；⑬P2-129；
> ⑭P2-125；⑮P2-123；⑯P2-124；⑰P2-90；⑱P2-122；⑲P2-120；⑳P2-119；㉑P2-121；㉒P2-112 / P2-113 /
> P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 / P2-82 / P2-81 / CI / LICENSE /
> 13 条旧条目缺 Status / 另三份零覆盖设计稿 —— **均不在本轮范围内，本轮不因此停下**。

> **第 389 轮：Backend（代码）+ API 文档（文档，doc round 288）**：
> **健康预检**：HEAD == origin/main == `0843432`、worktree 干净、`/api/health` → **200**、37778~37781/37790/37791
> 轮前均空闲、指纹 `c408cd68…` == 基线、`doc-growth-check.sh` exit 0。
> ① **P2-171（已修，Backend 侧，只加测试，主源码零改动）** —— 后端是本仓最大的暴露面（14 个 Controller、
> 66 条路径、**71** 个操作），而它的**端点表面零测试钉住**：`src/test/java` 下 **16** 个测试类、**178** 个用例里
> `grep -roh '"/api/[a-z0-9/{}-]*"'` **零命中** ⇒ 改一个 `@GetMapping` 路径**没有任何测试会红**，而手写的
> `docs/API.md` 与 springdoc 运行时 OpenAPI 会**同时**变成另一个样子，**没有任何东西把两者连起来**。
> 修法：新增 `BackendEndpointSurfaceTest`（**3** 个用例）—— 71 条 `(verb, path)` **集合相等**（多一条少一条都红，
> **不是** `containsAll`）、无重复、扫描本身**非空跑**（Controller ≥ 14 / 71 条 / 66 路径）；用**反射读注解**，
> 不起 Spring 上下文、不连数据库 ⇒ 可离线跑。变异 A（`/api/stats` → `/api/stats-extra`）与变异 B
> （新增 `/api/p389-extra`）**各恰好 1 红**；`ViewerController.java` 按 sha256 `c5c0f9d4…` **逐字节还原**，
> `git diff src/main/java/` 为空。后端单测 **178 → 181** 全绿。
> ② **P2-172（已修，文档方向，ZH 侧 2 张表，零可执行行）** —— `docs/API-zh-CN.md:994`（`/api/context/preview`
> 的 `project`）与 `:1767` / `:1768`（`/api/search/by-file` 的 `project` / `filePath`）把 **「(必填)」写在了
> 「默认值」列** ⇒ 必填参数**没有默认值**，该单元格**自相矛盾**，且与 EN 的 5 列 `Required` 表结构不同。
> 修法：改成 ZH 文档**自己已有的** 5 列体例（`:1072` / `:1167` 先例），必填 `✅`、可选 `❌`、必填项默认值 `—`；
> 修后 `(必填)` 出现在默认值列 **3 → 0**，EN/ZH 参数表**行级零分歧**。
> ⭐ 其余 4 张缺「必填」列的表（`/api/context/recent`、`/api/observations`、`/api/sessions`、`/api/logs`）
> 参数**全部可选 ⇒ 不丢信息**，按「宁少勿错」未改。
> ③ **P2-173（⏸ 只记录不实施）** —— `/api/context/preview` 的 `platformSource` 在活体 OpenAPI（10 个查询
> 参数之一）与代码里**都真实存在**（`ContextController.java:343/409` → `ContextService.java:371-383`
> **切换两条仓储查询**），但中英双语文档**全篇 0 次提及** ⇒ 按「遗漏 ≠ 失实」**只记录、不单方面补表**，
> 三条候选修法写进归档。
> **探针/流程自身三处错（全部由自检当场暴露）**：①第一版参数表解析器的请求行正则**排除带查询串的形式**
> ⇒ 只匹配到 2 张表，「零失配」是**空的**；②词表只认 `Yes` / `No` 而文档用 `✅` / `❌`；③「必填」列有时在
> 第 3 列、有时在第 2 列。修好后覆盖 **16 张 EN 表 / 13 张 ZH 表 / 101 个参数格**，并再次注入样本证明**能红**。
> **其余核实为真**：活体 OpenAPI **66 paths / 71 operations**，API.md 与 API-zh-CN.md **均覆盖全部 71 个操作**
> （0 遗漏）；OpenAPI `required` 标志逐个实测，省略后**全部返 400**，无一虚标；`/api/observations`、
> `/api/summaries`、`/api/prompts`、`/api/sessions` 的 `limit` 一致钳制到 **1–100**。
> **压缩**：**第四次启用 C8** —— 14 条 ⏸ 待决条目的 18 个证据块整体迁入归档 -90（归档侧持有 **9335 字节**；
> 本轮起点 **1251 行 / 149671 字节**，全轮净 **-30 行 / -227 字节**）；findings 终值 **1221 行 / 149444 字节**（余 556 字节）。
> **C6 已彻底枯竭**（重复 ≥2 次且 ≥25 字符的字面量全仓仅 2 个，共省 64 字节）⇒ 零信息损失杠杆用尽，
> **P2-115 的阈值上调从「余量紧张」升级为「下轮必撞线」**。本文件同步压缩：第 368 / 369~370 / 371 / 372 /
> 373 / 374 轮的历史轮报告逐字迁入 `2026-10-20_health-check-history-18.md`（第 389 轮压缩迁移）。
> **验收**：后端 **181/181** 全绿；回归 **45/0/1** 与 EXTRACTION 验收 **25/0/0** 均 exit 0，跑在**新鲜实例
> 37790**（jar 16:39、pid 56071；`find backend/src -newer <jar>` 为空 ⇒ jar 比全部后端源文件都新）。
> **本轮新增归档**：`2026-10-20_backend-review-evidence-90.md`（**327** 行 / **24061** 字节，承 `-62` ~ `-89`
> 体例，含 P2-171 ~ P2-173 全文 + 18 个证据块 + 压缩记录）。
> **待决事项（均已记录在 `docs/drafts/backend-review-findings.md`，不因此停下）**：
> ①**P2-115（最要紧）**：findings 终值 **1221 行 / 149444 字节**，**余 556 字节** ⇒ **下轮必撞线**；
> 建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，只需同步 `cron-combined-task.md:14`；
> ②**P2-173 本轮新增**：`/api/context/preview` 的 `platformSource` 是否补进中英双语的参数表（只记录不实施）；
> ③P2-168（demo 的 6 个静态 health 端点是否改真检查，对外契约变更）；④P2-144 三条修法择一；⑤P2-161；
> ⑥P2-163（Go / Python / Java 侧各 2 处结转）；⑦P2-166；⑧P2-159 的 Go 侧结转；⑨P2-128 剩余两项；
> ⑩P2-136；⑪P2-135；⑫P2-127；⑬P2-126；⑭P2-129；⑮P2-125；⑯P2-123；⑰P2-124；⑱P2-90；⑲P2-122；
> ⑳P2-120；㉑P2-119；㉒P2-121；㉓P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 /
> P1-1 / P2-79+P2-80 / P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status / 另三份零覆盖设计稿 ——
> **均不在本轮范围内，本轮不因此停下**。
