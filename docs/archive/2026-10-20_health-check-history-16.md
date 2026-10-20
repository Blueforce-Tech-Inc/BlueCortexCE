# Health Check History Archive 16（第 369 / 370 轮，2026-10-20）

> **Archived**: 2026-10-20，第 371 轮压缩迁移，自 `docs/drafts/health-check-task.md` 第 194–367 行。
> **Rule**: 归档文件创建后不得修改。后续历史达阈值时使用新的日期命名归档。
>
> 以下为第 369 / 370 轮轮报告的**逐字原文**（含其间的空行）。
> 迁移原因：本文件写入第 371 轮报告后行数达到 **1505 行**，已越过 `doc-growth-check.sh` 的
> 1500 行上限（该检查会在 `lines > MAX_LINES` 时返回 `COMPACTION_REQUIRED`、退出码 2），
> 而第 372 轮的新报告仍需续写。
>
> **留在工作文件未迁的段落**（不是历史叙述，是**基线的使用前提**，引用 `45/0/1` 或 `25/0/0` 时须一并阅读）：
> ①「第 264 轮起必须一并引用的第二重限定」；②「`accepted_commit` 的含义」；
> ③「第 251 轮起必须一并阅读的限定」。
>
> **未解决事项未被移出**：本归档两轮报告末尾的「未解决问题」是**逐轮副本**，
> 权威登记处是 `docs/drafts/backend-review-findings.md`（各轮报告自己就这么写），
> 且工作文件里保留**当前轮（第 371 轮）**的完整未解决清单。
>
> 本归档实测 **191 行 / 19696 字节**。

> **第 369 轮：JS/TS SDK（代码）+ SDK README（文档，doc round 269）**：
> **健康预检**：`/api/health` → HTTP **200**；37777 由 pid **53884** 在跑（**不是**本轮启动的），
> 且 `find backend/src/main -newer <jar>` **零命中** ⇒ 后端源码自 jar 构建以来未变，在跑实例即当前后端。
> **代码方向（JS/TS SDK）承 P2-122 跨家结转（✅ 已修，三处）**：JS 的 apiKey 措辞与 Go 修复前**完全同型** ——
> `src/client-options.ts:17` 的选项注释（`API key for authentication (sent as Bearer token)`）
> 与 `README.md:88`（`Bearer token for auth`）/ `README-zh-CN.md:86`（`Bearer Token 认证`），
> **都只说「发 Bearer」而未提后端从不校验它**。三处现已改写为「以 `Authorization: Bearer <key>` 发出，
> **当前后端不校验这个头**（它没有任何鉴权过滤器），故配置它今天保护不了任何东西」，并指向架构文档的「认证」一节
> （`docs/ARCHITECTURE.md:1253` / `docs/ARCHITECTURE-zh-CN.md:1222`，**已核实两处章节真实存在**）。
> **新立 P2-128（✅ JS 侧已修，双语）**：四家 SDK 的**参数下限**（调用方传一个过小的值会怎样）
> **只有 Python 的 README 写了**，Go / Java 两家**一个字都没提**；而**四家实现里 Java 根本没有 100ms 下限**。
> **四家的下限全部经运行时实测**，不是读代码推断：Go `go test -run "TestClientTimeoutIsFlooredNotReset|
> TestClientRetryBackoffFloor|TestClientTimeoutDefaultsUnchanged" -v` → **3 个测试全 PASS**
> （`config_internal_test.go:19/48/62`，是第 255 轮「超时被静默改成默认 30s」的回归钉子）；
> Python 读私有字段：`timeout=0.01/0/-1` → **0.1**，`max_retries=0/-5` → **1**，`retry_backoff=0.01/0` → **0.1**；
> JS 调 `resolveConfig`：`timeout=1/0/-1` → **100**，`maxRetries=0/-5` → **1**，`retryBackoff=1/0/-1` → **100**；
> Java 反射读 `CortexMemClientImpl` 的私有字段：`maxAttempts=0/-5` → **1**，
> `backoff=1ns` → **`PT0.000000001S`（原样，未被抬到 100ms）**、`backoff=1ms` → **`PT0.001S`（原样）**、
> `backoff=0` / `backoff=-5ms` → **`PT0.5S`（非正数回落默认 500ms，而非抬到 100ms）**、
> `readTimeout=1ms` → **`PT0.001S`（无任何下限）**、`readTimeout=0` → **`PT0S`**、
> `readTimeout=-1ms` → **`PT-0.001S`（负数被静默接受）**。
> 附带实测：Java 的 `readTimeout<=0` 会让**每一次**请求都失败（`Invalid duration: PT0S`），
> 而 `getVersion()`（`:597-607`）把异常吞掉并返回 `Map.of("service","unknown","version","unknown")`
> ⇒ **误配被伪装成成功**；对照 `connectTimeout=-1ms` 在构造期就抛 `IllegalArgumentException`
> （`:64-66`）——**同一份配置里，负数超时一个被拒一个被收**。
> （`getVersion` 的兜底是**有意设计**，紧邻的 `getProjects`（`:611-623`）明确写着「改为传播」，
> 故**不作为缺陷记录**，此处只用它证明「误配被伪装成成功」这一后果。）
> **四家里只有 Go 被测试钉住**，Python / JS / Java 三家**改掉下限不会让任何测试变红**。
> **已实施**：JS SDK 双语 README 补齐三行下限（`timeout` / `retryBackoff` 「下限 `100`」、
> `maxRetries` 「低于 `1` 的值会被抬到 `1`」），这三行此前**一个字都没有**。
> **Go / Java 两家属跨家结转**；Java 要不要对齐另三家（属**对外行为契约变更**）留三选一，
> 连同「要不要给 Python / JS / Java 补下限回归测试」一并见归档正文。
> **JS SDK 其余部分逐条核实为真**：**27 个 public 方法**与 README 方法表 **27 = 27 零幻影零遗漏**，
> 且 EN / ZH 两张表列的是**同一组**方法；**README 必填参数表 18 行**与实现**逐条吻合**
> （`validateRequired` 的标量形态 + `getObservationsByIds` 的数组形态，**两种都要认**）；
> **重试语义精确** —— `maxRetries` 是**总尝试次数**（`attempt <= maxRetries`）、线性 + ±25% jitter、
> 只重试 **429/502/503/504 + TypeError/AbortError**（`errors.ts:129-147` 逐字吻合）；
> **10 MiB 上限**是 `10 * 1024 * 1024`（含 Content-Length 预检 + 读后长度 + UTF-8 字节复核），
> README 诚实指出「消息写 10MB、实际是 10 MiB（10,485,760 字节）」；空更新消息与四家**逐字相同**；
> 三态语义（skip / set / clear）文档与实现一致。
> **JS SDK 自带测试 259 通过 / 0 失败**（246 client + 5 truncated-body + 8 http-server，
> 构成由 `vitest --reporter=verbose` 实跑统计）；`npm run lint` 与 `npm run build` 均 exit 0。
> **探针自身错两处并已修正（先识别再采信）**：①`getObservationsByIds` 用的是**数组形态**校验
> （`!ids || ids.length === 0` / `ids.length > 100` / 逐元素 `!ids[i] || !ids[i].trim()`），
> 不是 `validateRequired` —— 旧探针因此报「方法不在实现的必填校验里」；
> ②多出的 `debug/error/info/warn` 是 **`Logger` 接口声明**（`client.ts:116-121`，在 class 之外），
> 不是 client 的方法 —— 旧探针把方法集合从整个文件取，因而误判。
> 另修旧 probe1 的 ⑨ 段：它是 **print-only**（不参与失败判定）且关键词表**没有 "raised to"**
> —— 正是本轮改后的措辞，等于「跑通了却没在看」，已升级为硬断言。
> **两个探针都补了阳性对照**（注入坏样本后必须报错）：改错的措辞、删掉的数组校验、
> 幻影方法、未列出的公有方法、把 public 改成 private —— **六种坏样本全部被探针抓住**，
> 证明探针**能失败**，不是只会输出绿灯。
> **文档方向（SDK README，doc round 269）**：JS SDK 双语 README 的 apiKey 行与三行参数下限已改（见上）。
> **文档增长检查**：`bash scripts/doc-growth-check.sh` exit **0**（findings 149781 字节 / 1436 行，
> 归档 README 109315 字节 / 178 行，均在 1500 行 / 150000 字节阈值内）。
> **是否检测到代码变化**：**是** —— 指纹 `7aa6567b…` → **`997266b9…`**（记录数仍 2218），
> 变化来自 `js-sdk/cortex-mem-js/src/client-options.ts`（本轮改的注释，`.md` 被指纹排除）。
> **完整验收**：**已执行**（指纹变化 + 本轮改了代码 ⇒ 门控要求全量）。
> **回归测试**：`SERVER_URL=http://127.0.0.1:37790 bash scripts/regression-test.sh --skip-build`
> → **45 通过 / 0 失败 / 1 跳过**，exit **0**。
> **EXTRACTION 验收**：`BACKEND_URL=http://127.0.0.1:37790 EXTRACTION_ENABLED=true
> bash scripts/phase3-acceptance-test.sh` → **25 通过 / 0 失败 / 0 跳过**，exit **0**。
> **服务启动动作与最终端口**：37777 由 pid 53884 在跑（**非本轮启动，未动**）；
> 另起验收实例 **37790**（pid 77927，detached，`nohup … > log 2>&1 < /dev/null & disown`），
> 验收后**已停**（只停本轮自己启动的进程）；最终 37777 仍是 pid 53884、health **200**。
> **构建与重启**：**未重建 jar、未重启 37777**（本轮改的是 JS SDK 与 README，
> `find backend/src/main -newer <jar>` 零命中 ⇒ 新鲜度豁免适用）。
> **Backend 发现与修复数量**：**0**（本轮代码方向是 JS/TS SDK，Backend 方向下轮审）。
> **连续 3 轮检查**：**通过**（定稿态连跑，期间零改动）——
> 第 1 轮 `/tmp/p369-js-probe.py` ALL PASS、第 2 轮 `/tmp/p369-js-probe2.py` ALL PASS、
> 第 3 轮 `npm run lint` + `npm test`（259）+ `npm run build` + 改后措辞对拍，全部 exit 0。
> **commit**：`patrol: round 369 - the parameter floors only one SDK documents`。
> **未解决问题**：①**P2-115 仍是写入前置条件**（findings 余量 219 字节，阈值上调
> `MAX_LINES=2000` / `MAX_BYTES=200000` 需用户决策；本轮的新条目靠"指针比全文短"挤进来）；
> ②P2-128（Java 要不要对齐另三家的下限／Go 与 Java README 要不要补下限／要不要补下限回归测试，三选一）；
> ③P2-127（活体 OpenAPI 的 `prompt_text` required 三选一）；④P2-126（tool-use 那三个字段要不要真用起来的三选一）；
> ⑤P2-125（「省略 project 就跨项目返回」要不要在行为层收敛）；⑥P2-123（Go 重试范围/后端幂等三选一）；
> ⑦P2-124（限流器三选一）；⑧P2-90（Go README 测试数的形态）；⑨P2-122（服务端鉴权三选一）；
> ⑩P2-120（batch 端点改严三选一）；⑪P2-119（demo 监听地址三选一）；⑫P2-121（13 条断链的目标归属）；
> ⑬P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 /
> P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status 段 / 另四份零覆盖设计稿 ——
> **均已记录在 `docs/drafts/backend-review-findings.md`，不在本轮范围内，本轮不因此停下**。

> **第 370 轮：Demo（代码）+ 设计文档（文档，doc round 270）**：
> **健康预检**：`/api/health` → `{"service":"claude-mem-java","status":"ok"}`（HTTP **200**）；
> 37777 由 pid **53884** 在跑（**不是**本轮启动的），且 `find backend/src/main -newer <jar>` **零命中**
> ⇒ 后端源码自 jar 构建以来未变，在跑实例即当前后端。37778~37781、37790 轮前均空闲。
> **代码方向（Demo）新立 P2-129（Go 与 JS 已修）**：**四家 demo 的 404 / 405 响应分裂成三种形态**。
> **活体实测**（Java 37778 / Go 37779 / Python 37780 / JS 37781 四家同时在跑）：
> 方法不匹配 ⇒ Go **405** JSON、Python **405** JSON、JS **404 + text/html**（`Cannot POST /health`）、
> Java **405** JSON；未知路径 ⇒ Go **404 + text/plain**（`404 page not found`）、Python **404** JSON、
> JS **404 + text/html**、Java **404** JSON。**阳性对照**（先证明探针看得见差异）：
> 同一家的 `GET /health` → 200 vs `POST /health` → 405/404，**两读数不同** ⇒ 不是探针失灵。
> **不是「框架默认所以无妨」**：Go demo 自己的 README 明文写着
> 「**Every error is `{"error": "<message>"}`**」并把「Python demo 与 JS demo follow the same rule」
> 当作**跨家断言**；而 **Python demo 的 docstring 恰恰写着「a wrong method answers 405 (Java, Go) or 404 (JS)」**
> —— **两份文档互相矛盾，且都不是当前代码的行为**。`scripts/mcp-e2e-test.sh` 开头的注释
> 「SSE mode: /sse returns 200, /mcp returns 404」是**独立于四家 demo 的第二来源**。
> **已修（只影响错误路径，且方向与已声明契约一致，非发明新契约）**：
> ① Go 新增 `registerFallback(mux)`（`main.go:141-160`）注册 `"/"` 兜底，
> 未知路径从 `text/plain` 变成 `{"error": "not found: <path>"}`；**并逐条验证 13 个原有端点
> 与带参路径无一被遮蔽**（`/health` 200、`/quality` 400 等读数与改前一致）；
> ② JS 新增终止处理器（`app.ts:504-575`），按 `app.router.stack`（Express 5 的**文档化属性**）
> 取出路由表，判定「路径存在但动词不对」→ **405 + `Allow` 头 + JSON**，否则 → **404 + JSON**；
> ③ Go demo README 的错误响应表补一行「Unknown path → 404」，JS demo README **新增**
> 「Error responses」段（此前只有 Go 的 README 有这段），Python 的过期 docstring 已更正。
> **Java 的 404/405 体是 Spring 默认形状**（JSON 但非 `{"error": …}`），属**对外契约变更，未单方面实施**，留三选一。
> **修后复测：四家状态码与 Content-Type 完全一致** —— 方法不匹配**四家全部 405**，
> 未知路径**四家全部 404**，未知路径的 Content-Type **四家全部 `application/json`**。
> **测试**：新增 Go `examples/http-server/fallback_test.go`（2 个测试 / 5 个子用例）与
> JS `examples/http-server/error-contract.test.ts`（5 个测试，断言响应体**不含 `<!DOCTYPE html>`**
> 且 `Allow` 头列出可行方法）；JS SDK 套件 **264 通过 / 0 失败**（原 259 + 本轮 5），
> Go SDK 九个 module 全绿（**369**，原 363）。
> **文档方向（设计文档，doc round 270）更正 `docs/drafts/mcp-server-transport-analysis.md` 四处**：
> ① **2.2 的 YAML 片段漏了实际存在的 `streamable-http: mcp-endpoint: /mcp` 一块**
> （`application.yml:118-119` 里有它）—— 省略它会让读者以为「换协议还要顺手加一段配置」；
> ② **5.4 的验证命令 `POST /sse` 活体返回 404**（2026-10-20 实测）—— SSE 里 `/sse` 是**用 GET 建流**的端点，
> 消息要发往 `/mcp/message?sessionId=...`，而这条命令与**本文 2.5 节自己描述的握手流程都不一致**；
> 已按 2.5 的流程改写并**活体复验**（实测取到 `event:endpoint` → `/mcp/message?sessionId=…`，
> 响应从 SSE 流上读回）；
> ③ **5.4 结果表里「`/mcp` 端点注册 ✅ 200 OK」与本文 5.2 节、E2E 脚本注释互相矛盾**
> （SSE 模式下 `/mcp` **按设计**就是 404，5.2 整节讲的就是这个）—— 已更正并标注，
> 并写明 5.4 的**决定**（`protocol: SSE`，2026-03-19）**不因这次更正而改变**，改的只是可复现的过程与结果；
> ④ **7.1 的「STREAMABLE 已验证通过 (14/14)」没有日期**，会被读成当前数字 —— 已补日期
> （本文撰写时 2026-03-19 的 STREAMABLE 分支跑分）并写明**当前 SSE 分支实测 12/12**
> （2026-10-20 活体复验，`scripts/mcp-e2e-test.sh` → Passed 12 / Failed 0 / Skipped 0，协议判定 SSE），
> 且**本轮未复验 STREAMABLE 分支**（需先切 `protocol`，不动生产配置）。
> **该设计稿的其余断言逐条核实为真**：Spring AI **1.1.2**（`pom.xml:22`）、MCP starter 是 webmvc、
> `protocol: SSE`、两个端点、`type: SYNC`、5 个工具（`search` / `timeline` / `get_observations` /
> `save_memory` / `recent`，`ClaudeMemMcpTools.java` 逐个命中）；
> **5.2 的「`ServerProtocol` 枚举共有三个有效值」用 `javap` 对着实际的 1.1.2 jar 核实为真**
> （恰好 `SSE` / `STREAMABLE` / `STATELESS` 三个，不是「至少三个」）。
> **测试数同步（本轮改动导致的自身过期，照 P2-90 体例加日期）**：JS SDK README 双语 **259 → 264**、
> Go SDK README 双语 **363 → 369**（`examples/http-server` 27 → 33）。
> **文档增长检查**：`bash scripts/doc-growth-check.sh` exit **0**（findings 149268 字节 / 1440 行、
> health-check-task 77589 字节 / 1292 行、archive README 112167 字节 / 180 行，均在阈值内）。
> **是否检测到代码变化**：**是** —— 指纹 `997266b9…` → **`7681d629…`**，记录数 2218 → **2220**
> （新增两个测试文件）；变化来自 `main.go` / `app.ts` / `app.py`（`.md` 被指纹排除）。
> **完整验收**：**已执行**（指纹变化 + 本轮改了代码 ⇒ 门控要求全量）。
> **回归测试**：`SERVER_URL=http://127.0.0.1:37790 bash scripts/regression-test.sh --skip-build`
> → **45 通过 / 0 失败 / 1 跳过**，exit **0**。
> **EXTRACTION 验收**：`BACKEND_URL=http://127.0.0.1:37790 EXTRACTION_ENABLED=true
> bash scripts/phase3-acceptance-test.sh` → **25 通过 / 0 失败 / 0 跳过**，exit **0**。
> **服务启动动作与最终端口**：37777 由 pid 53884 在跑（**非本轮启动，未动**）；
> 另起验收实例 **37790**（pid 21450，detached），验收后**已停**；
> 四家 demo（37778 Java / 37779 Go / 37780 Python / 37781 JS）本轮启动、验收后**逐一按端口停掉**；
> 最终 37777 仍是 pid 53884、health **200**，37778~37781/37790 全空。
> **构建与重启**：**未重建 jar、未重启 37777**（本轮改的是 Demo 与文档，
> `find backend/src/main -newer <jar>` 零命中 ⇒ 新鲜度豁免适用）。
> **Backend 发现与修复数量**：**0**（本轮代码方向是 Demo，Backend 方向下轮审）。
> **连续 3 轮检查**：**通过**（定稿态连跑，期间零改动）——
> 第 1 轮 Go demo 的 `gofmt` / `go vet` / 全量测试 + Go SDK 九个 module 全绿；
> 第 2 轮 JS SDK 的 `npm run lint` / `npm test`（264）/ `npm run build` 全过；
> 第 3 轮 MCP 设计稿断言探针 ALL PASS + 跨家 demo 状态码复测全一致。
> **探针自身错并已先识别再采信（本轮 3 处）**：①第一版跨家对比把「无 body」当成「畸形 JSON」——
> 改用真正的畸形字节（`{invalid`）后 JS 报的是解析器消息而非字段缺失；
> ②设计稿探针把「GET /sse」用 `urlopen().read()` 取，于是**长连接流必然超时**（读数 0 而非 200），
> 改用 `curl` 只取状态码；③设计稿探针查「旧命令已移除」时把**更正注里的逐字引用**也算成残留 ——
> 而**逐字引用旧命令正是「更正只能用标注」的要求**，故改成只查代码块。
> **commit**：`patrol: round 370 - the demo that answered a typo with HTML`。
> **未解决问题**：①**P2-115 仍是写入前置条件**（findings 余量 732 字节，阈值上调
> `MAX_LINES=2000` / `MAX_BYTES=200000` 需用户决策；本轮已把三种零损失压缩手法用到只剩一种）；
> ②**P2-129**（Java 的 404/405 体要不要也拉齐成 `{"error": …}`，三选一）；
> ③P2-128（Java 要不要对齐另三家的参数下限／Go 与 Java README 要不要补下限／要不要补下限回归测试，三选一）；
> ④P2-127（活体 OpenAPI 的 `prompt_text` required 三选一）；⑤P2-126（tool-use 那三个字段要不要真用起来的三选一）；
> ⑥P2-125（「省略 project 就跨项目返回」要不要在行为层收敛）；⑦P2-123（Go 重试范围/后端幂等三选一）；
> ⑧P2-124（限流器三选一）；⑨P2-90（Go README 测试数的形态）；⑩P2-122（服务端鉴权三选一）；
> ⑪P2-120（batch 端点改严三选一）；⑫P2-119（demo 监听地址三选一）；⑬P2-121（13 条断链的目标归属）；
> ⑭P2-112 / P2-113 / P2-93 / P2-95 / P2-91 / P2-87 / P2-88 / P2-100 / P1-1 / P2-79+P2-80 /
> P2-82 / P2-81 / CI / LICENSE / 13 条旧条目缺 Status 段 / 另四份零覆盖设计稿 ——
> **均已记录在 `docs/drafts/backend-review-findings.md`，不在本轮范围内，本轮不因此停下**。
