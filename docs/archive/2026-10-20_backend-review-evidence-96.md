# 后端审查证据归档 -96（2026-10-21）

> 第 395 轮（Backend + API 文档，doc round 294）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1275 行 / 142603 字节**）。
> 本文档体量：**277 行 / 24895 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。

## 本轮新写的三条（正文逐字保留在本归档）

### P2-185: 后端暴露面的钉住有**结构盲区** —— 只扫 `@RestController`，传输层 6 个操作从未被任何测试钉住；而那份测试的 javadoc 宣称钉住的是「服务器发布的完整 (method, path) 集合」

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Status**: ✅ **已修（395 轮，Backend 侧，只加测试 + 改注释，后端主源码零改动）** —— 新增
  `BackendTransportEndpointSurfaceTest`（**3** 例），从 `application.yml` **推导**传输层应有哪些操作并与
  钉住集合相等；`BackendEndpointSurfaceTest` 的 javadoc 失实陈述改准为真实口径。

**探针怎么做的，以及它看不见什么**

`BackendEndpointSurfaceTest` 的发现机制是：扫描 `com.ablueforce.cortexce.controller` 包里带
`@RestController` 的类（本轮实测 **14** 个），反射读出每个方法的 `@GetMapping` / `@PostMapping` /
`@PutMapping` / `@DeleteMapping` / `@PatchMapping`，再加上类级 `@RequestMapping` 前缀，规范化后得到
`(method, path)` 集合。**这个机制只能看见注解声明的路由**：由框架自己注册的端点（Spring Boot
actuator、Spring AI MCP 传输）一个也进不来。

**活体实测**（2026-10-21，新鲜实例 37790，jar 23:13，pid 10752；`find backend/src -newer <jar>` 为空）

- `GET /v3/api-docs` → **71** 个操作、**66** 条路径。与钉住集合**双向差集为零** ⇒ 注解层被钉得是准的。
- 但服务器还会应答这些**注解里没有**的操作，逐个探测（状态码为实测值）：

  | 操作 | 状态码 | 判读 |
  |------|--------|------|
  | `GET /sse` | **200** | MCP 的 SSE 传输；响应首事件是 `event:endpoint`，其 `data` 给出带会话的 `/mcp/message?sessionId=…` |
  | `POST /mcp/message` | **400** | 路由**已注册**（无 session 才会 400）；同一路径 `GET` 返 404，因为它是 POST-only |
  | `GET /actuator` | **200** | actuator 链接页，JSON `_links` 列出本实例暴露的 actuator 端点 |
  | `GET /actuator/health` | **200** | actuator 健康 |
  | `GET /actuator/info` | **200** | actuator 构建信息 |
  | `GET /actuator/metrics` | **200** | actuator 指标 |
  | `POST /mcp` | **404** | streamable-HTTP 传输在 `protocol: SSE` 下**不启用**（`application.yml` 里配了但未激活） |

- actuator 链接页的 `_links` 原文键值：`self`(templated=false)、`health`(false)、`health-path`(**true**)、
  `info`(false)、`metrics-requiredMetricName`(**true**)、`metrics`(false) ⇒ 暴露的是 health / info / metrics
  三个，两个 templated 键是子路径模板，不是独立端点。
- ⇒ **服务器实际应答 77 个操作、72 条路径**；而当时唯一钉住暴露面的测试钉的是 **71 / 66**。
  差的那 **6** 个，正是上表里除 `/mcp` 之外的六条。

**失实陈述**

`BackendEndpointSurfaceTest` 的 javadoc 第一句写的是「Pins the backend's *exposed surface*: the exact set
of `(HTTP method, path)` pairs **the server publishes**」—— 与实测相差 6 个操作，是**失实陈述**。
同一段还写「the backend is the largest surface of **the three**」，而这一族如今是五家（四家 demo + 后端），
也是过期表述。两处都已在**注释**里改准（失实陈述的注释修正，可直接改）；生产代码**零改动**。

**为什么这 6 个端点值得钉**

`GET /sse` 是**面向用户的集成地址**：`proxy/CLAUDE-CODE-INTEGRATION.md:142`（及 :163）与
`proxy/CURSOR-INTEGRATION.md:103` 都教用户执行
`claude mcp add --transport sse cortexce http://127.0.0.1:37777/sse`。而该路径来自
`application.yml` 的 `spring.ai.mcp.server.sse-endpoint` —— **全仓零测试钉住它**：
在 `backend/src/test` 里搜 `"/sse"` / `"/actuator` / `"/mcp`，本轮改动前**零命中**（唯一相关的
`ClaudeMemMcpToolsTest` 覆盖的是 MCP **工具**的两个参数：`orderBy` 白名单与 `limit` 钳制，与传输端点无关）。
⇒ 改一下配置，用户被教着去连的地址就悄悄挪了，没有任何东西变红。

**修法**（只加测试 + 改注释）

新增 `backend/src/test/java/com/ablueforce/cortexce/controller/BackendTransportEndpointSurfaceTest.java`
（**3** 例）：① 传输层集合与**钉住集合**相等（不是包含）；② **配置确实读到了**（协议必须是
SSE/STREAMABLE/STATELESS 之一、两个 SSE 端点必须以 `/` 开头、暴露列表必须含 `health`）—— 配置读歪了
必须报错，不能悄悄给出空集合而通过；③ 钉住集合**非空洞**（6 个操作、6 条路径）。
关键是「期望值从 `application.yml` **推导**」而不是手抄在旁边：MCP 端点由
`spring.ai.mcp.server.protocol` 与两个端点键推出，actuator 端点由
`management.endpoints.web.exposure.include` 与 `management.endpoints.web.discovery.enabled`（缺省 true）
推出 ⇒ **改配置就会变红**，而不是让期望值落单。

**变异实测**（每次只动一个变量，跑完按 sha256 逐字节还原 `application.yml`）

| 变异 | 内容 | 结果 |
|------|------|------|
| M-A1 | `sse-endpoint: /sse` → `/mcp-sse` | **1 红**（集合相等那一条） |
| M-A2 | `include: health,metrics,info` → `health,metrics` | **2 红**（集合相等 + 计数非空洞） |
| M-A3 | `protocol: SSE` → `STREAMABLE` | **2 红** |
| M-A4 | 阴性对照：只加一行注释 | **0 红**（3/3 绿） |

`application.yml` 变异前后 sha256 均为 `fb2594f1881f3ecfc2deeeb35514e7d954f1e4bf0a38c093dac65de0e2c9c6f1`。
后端 `mvn -o test` 由 **181** 例增到 **184** 例，全绿（exit 0）。

### P2-186: API 文档双语的端点清单**从未与代码绑定**（「verified identical」是无机制的手工断言），且口径只覆盖 springdoc 的 REST 层 —— 漏掉 6 个传输层操作，其中 `GET /sse` 是集成指南教给用户的地址

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Status**: ✅ **已修（395 轮，文档方向，两语同步）** —— 两语各补一节「Transport Endpoints /
  传输层端点」（6 个操作 + 产生它们的配置 + 实测日期），四处计数改准为
  「REST 71/66 + 传输 6/6 = 77/72」；新增 `scripts/check-api-doc-surface.py` 与**运行中服务器**
  逐条比对，接入回归 Test 27。

**问题一：那句「verified identical」没有任何机制**

`docs/API.md` 开头的结构说明写着「The endpoint sets are **verified identical**（71 live operations
across 66 paths, measured 2026-10-21 …）」，中文版同句。这是一句**手工断言**：build 里没有任何东西
比较两版文档记载的端点与代码/服务器实际提供的端点。改一个端点、加一个端点、或让两语漂移，
文档都会静默过期，而**没有一个东西会红**。这与 **P2-182**（JS SDK README 的「必填参数」表从未与代码
绑定）同族，但对象是本项目**最主要**的 API 参考。

**问题二：口径只覆盖了 REST 层**

那句计数是「`/v3/api-docs` 里 `paths` 每个条目下的 `get`/`put`/`post`/`delete`/`patch` 键」⇒ 也就是
springdoc 发布的 REST 层（**71 / 66**）。服务器实际应答 **77 / 72**（见 **P2-185** 的活体实测），
多出的 6 个是 MCP 传输与 actuator。其中 `GET /sse` 是**用户可见的集成面**（见 P2-185），它在两版
API 文档里**一个字都没有**，在测试里也**一个都没有**。

**注意：REST 层的清单本身并没有写错**

本轮先做了一次独立核对：解析两版文档的端点清单并与活体 OpenAPI 对拍 ⇒ **EN 71 = ZH 71 = 活体 71，
双向差集为零**。⇒ 本条**不是**「文档数字错了」，而是「**没有机制**」+「**口径不完整**」。

**探针自身的两处错（先识别再采信）**

第一版解析器只认「独立成行的请求行」，结果报 EN 66 / ZH 66，与活体的 71 差 5 —— 拆开看是**两类**：
① **13 条**请求行带 query string（如 `GET /api/observations?project=…`）没被剥离；② **18 条**端点
根本不用请求行，而是用**路径式 H4 标题**（`#### GET `/api/context/recent``）—— 英文版在 `## Context` 一节、
几乎全篇的中文版都用这一种。⇒ **两种发布形式必须同时解析**，且 query 必须剥离。
改正后：EN 71 = ZH 71 = 活体 71。

**修法**

1. 两版各补一节 **Transport Endpoints / 传输层端点**：6 个操作逐个给出方法、路径、用途，写明
   MCP 传输的配置来源（`spring.ai.mcp.server`：`protocol: SSE`、`sse-endpoint: /sse`、
   `sse-message-endpoint: /mcp/message`）与 actuator 的暴露来源
   （`management.endpoints.web.exposure.include`：`health`、`metrics`、`info`），并写明 `/mcp` 在
   `SSE` 协议下**不被提供**、以及实测日期。
2. 结构说明与「HTTP 状态码」两节（两语共四处）的计数改准为「REST 71/66 + 传输 6/6 = 77/72」，
   写明日期与取数方式，并点名校验脚本。
3. 新增 `scripts/check-api-doc-surface.py`：**把两版文档与运行中的服务器逐条比对** ——
   REST 层读 `/v3/api-docs`；actuator 端点**从活体 `/actuator` 链接页发现**（所以以后再暴露一个
   actuator 端点会自动被算进来，不必改脚本）；MCP 传输**从后端自己的 `application.yml` 推导**再
   **活体探测**（所以改配置不会让脚本误报）；最后要求两版文档与这个并集**逐条相等**、彼此也相等。
   接入 `scripts/regression-test.sh`，成为 **Test 27**。

**检查器自己能不能失败（阳性对照）**

`--self-test` 覆盖 6 个坏样本：引用少一个端点 / 引用多一个端点 / 两语漂移 / 完全一致（应当不报）/
解析器读到两种发布形式 / 解析器剥离 query string。⇒ **6/6 通过**（"SELF-TEST PASSED"）。

**活体变异实测**（每次都从**干净**副本开始）

| 变异 | 内容 | 退出码 |
|------|------|--------|
| M-B0 | 阴性对照：未改动的副本 | **0** |
| M-B1 | EN 删掉一个 REST 端点 | **1** |
| M-B2 | ZH 改一个路径 | **1** |
| M-B4 | EN 删掉一个**传输层**端点（`GET /sse`） | **1** |
| M-B3 | 服务器不可达 | **2**（不是静默通过） |

⚠️ **第一版变异台有污染**：第一次跑 M-B1 时只还原了 EN 没还原 ZH，于是报出的红其实是上一轮 ZH 变异
留下的；**每例都从干净副本重来**后读数才作数。**退出码也要用 `PIPESTATUS` 取**——第一次用 `$?`
读到的是管道末端的 `tail` 的退出码（恒为 0），差点把「红」读成「绿」。

**改文档前后的红→绿**：加新章节之前，检查器**如实报红**并逐条点名两版各缺 6 个传输层端点；补齐之后
**转绿**（两版各 77 条，与活体逐条相等）。

### P2-187: 验收基线区块与轮次报告**连续两轮未更新** —— 第 393/394 轮都跑过完整验收，而 `health-check-task.md` 的基线仍停在第 392 轮，那两轮的综合报告也都没追加

- **Scope**: → [scope-9][a6]
- **Status**: ✅ **已修（395 轮，流程侧）** —— 基线更新到第 395 轮并追加本轮报告；第 393/394 两轮以
  **标注**方式补记（不改写历史正文）。**机制缺口本身只记录不实施**（与 **P2-100** 同型：无脚本强制、
  纯靠人工）。

**核对**

`docs/drafts/health-check-task.md` 顶部唯一的「Latest Automated Acceptance Baseline」区块写的是
`accepted_at: 2026-10-21T21:20:00+08:00`、`accepted_commit: e2f293c`、指纹 `4917e47a…` ⇒ **第 392 轮**。
而第 393 轮（JS/TS SDK）与第 394 轮（Demo）**都跑过完整验收**（新鲜 37790、回归 45/0/1、
EXTRACTION 25/0/0、exit 0），产出指纹 `30b194db…` 与 `0854994c…` —— 两者都**没有**写进基线区块。
同样地，在 `docs/drafts/health-check-task.md` 里搜「第 393 轮」「第 394 轮」**零命中** ⇒ 那两轮的
**综合报告也没追加**（该文件里最新的轮次报告是第 392 轮）。⇒ 审计链上有**两轮**的洞。

**影响范围**

- 对**验收门控**本身无害：统一任务第 4 节拿基线指纹与当前指纹比，第 393/394 轮指纹都不同 ⇒
  完整验收**照跑**了，并没有因为基线过期而漏跑。
- 但基线区块的**自我描述**是「唯一」「最新一次成功验收」，它实际描述的是两次之前的验收 ⇒
  任何引用「上一次成功验收」的人（人或下一轮）拿到的都是过期值。
- 与 **P2-100**（`patrol-rotation.md` 的 Current Position 段连续四轮未同步）**同型**：该写的地方
  写了、规则也写了，但**没有任何断言**保护它，全靠人工记得。

**本轮的处置**

- 基线区块更新为第 395 轮的实测值（`accepted_at`、`accepted_commit` = 本轮开始时的 HEAD
  `76051f0`、指纹 `43cb1fcf…`、记录数 2235）。
- 追加本轮综合报告；第 393/394 两轮以**标注**方式补记其 commit、指纹与验收结果 ——
  **不改写历史正文**（追加式文档只能用标注更正）。
- **机制缺口本身只记录不实施**：给「基线区块与轮次报告的同步」加脚本强制，属于新增机制，
  按既定规矩只记录不单方面实施。

## 本轮迁出的已结案条目（正文逐字保留在本归档）

### P2-159: 四家 SDK README 的「必填参数规则集**完全相同**」—— Python / Go / JS 三家（双语共 6 处）都说「另三家强制的是完全相同的一组规则」，而 **Java 多一道 `projectPath` 必须是绝对路径**的检查
- **Status**: ✅ 已修（386 轮，Python 侧，文档，零可执行行）—— **写入即假**（Java 那道校验 2026-04-15 引入，断言 2026-10-03 写下）；按断言清扫共 **8** 处，其中 **6** 处失实；Python 双语两处改为「参数集合相同，只有一处例外：Java 额外要求绝对路径」，**Go / JS 各 2 处结转**。全文逐字见 [`…-87`][a45]。 —— **追加（391 轮）：Go 侧 2 处已修**（双语改为「同一组非空检查 + Java 多一道 `projectPath` 绝对路径」）。 —— **追加（393 轮）：JS 侧 2 处**其实**早在第 387 轮就已修完**（commit `6548428` 同一 commit 内把「enforce exactly the same set」改成「require exactly the same set of arguments — with one difference」，ZH 侧同批改为「要求的**参数集合**完全相同」），本条记的「Go / JS 各 2 处结转」里的 **JS 部分是陈旧记录**；⇒ **P2-159 至此四家全部结转完成**。

### P2-177: Go SDK 的**两个校验错误类型**零测试钉住 —— 既有用例断言二选一，故「返回哪个类型」都成立；README 发布的**完整消息**也无人逐字钉住

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-92`][a50]（四家错误类型与完整消息的源码对拍、两处既有断言的缺口定位、9 个新顶层用例清单、3 次变异各自命中的测试与 1 次阴性对照 0 红）
- **Status**: ✅ **已修（391 轮）** —— 新增 `error_contract_test.go`（**9** 个顶层用例 / **17** 个运行时用例），只加测试、SDK 主源码零改动；Go 侧总数 **373 → 390**。

### P2-178: Go README 双语 **2 处**把空更新消息渲染成**漏前缀**的形态 —— 与运行时返回的字符串**不是同一个**；另 2 处的「消息完全相同 + 类型写错」是 **P2-163 的 Go 侧结转**，本轮一并修

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-92`][a50]（4 处失实的逐处坐标与原文、四家对拍表、修后片段）
- **Status**: ✅ **已修（391 轮）** —— 双语 4 处改准（类型写 `*dto.ObservationUpdateValidationError`、补四家对拍表、表格行补全前缀），并加 README 一致性测试钉住「文档发布的消息 == 运行时返回的消息」。

### P2-179: Python SDK 的**客户端校验守卫用例未挂 mock**，而测试的 `BASE` 指向**常驻后端** ⇒ 守卫一旦失效，**8 个捕获类**用例会真的向**常驻数据库写行**，且该写入**不被任何断言覆盖**

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-93`][a51]（13 个未 mock 用例的逐处定位、socket 间谍受控实验、变异三次各自命中的面、阴性对照 0 红、密封装饰器的双路径诊断）
- **Status**: ✅ **已修（392 轮）** —— 新增 `tests/test_error_contract.py`（**11** 例，逐字钉住完整消息、精确类型、`field`、不发请求、README 一致性）+ 把 13 个守卫用例改为**密封**；SDK 主源码零改动。

### P2-181: JS SDK 的 **32** 处客户端必填参数守卫里 **16** 处**零测试钉住** —— README 发布的 18 行「必填参数」表有 **8** 行**完全没有任何守卫覆盖**

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-94`][a52]（逐守卫变异扫描的 32 行读数、探针自身的一处错、唯一未密封构造的受控实验、16 处复验各自命中的用例、三个文档坏样本、阴性对照）
- **Status**: ✅ **已修（393 轮，JS 侧，只加测试 + 改测试，SDK 主源码零改动）** —— 新增 `src/__tests__/required-arguments.test.ts`（**97** 例）+ 密封 `client.test.ts` 里唯一未注入 mock 的那一个构造；JS SDK **273 → 370**。

### P2-182: JS SDK 双语 README 的「必填参数」表**从未与代码绑定**，且 `limit` 负值的**四家分歧**在 JS 侧**没有记载** —— Go README 早已写明这张对照表

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Evidence**: → 归档 [`…-94`][a52]（四家 limit 处理的逐家源码坐标、Java 规范构造器不校验 limit 的辨析、后端默认值与钳制区间、6 个新用例的断言清单）
- **Status**: ✅ **已修（393 轮，文档方向，双语）** —— EN 与 ZH 各补一段 + 一张四家对照表，并加 **6** 个用例钉住（JS 侧行为 4 + 双语互不漂移 2）。

### P2-183: Go demo 与 JS demo 的**端点暴露面**零测试钉住 —— 而 Go demo 带着**三份**互不校验的副本（24 个 handler / README 26 行表 / 启动打印 26 行）

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Evidence**: → 归档 [`…-95`][a53]（四家 demo 的暴露面测试盘点、Go demo 三份副本的逐份定位、六个单变量变异的红绿计数、阴性对照、还原校验、探针自身的一处错）
- **Status**: ✅ **已修（394 轮，Demo 侧，只加测试，两个 demo 的源码都零改动）** —— Go demo 新增 `examples/http-server/surface_test.go`（**4** 例）+ JS demo 新增 `examples/http-server/surface.test.ts`（**3** 例）。

### P2-149: Go demo README 的 P2-33 段落里**两处**失实陈述 —— ①「另外 21 个端点在**四家**里拼写完全相同」，而这 21 个恰恰是**与 Java demo 拼写不同**的那批（四家路径交集只有 `/chat`）；② `main.go:470` / `main.go:771` 两处行号引用**已失效**（实际在 492 / 793）

- **Status**: ✅ 已修（382 轮，文档，零可执行行）—— ①改为「另外 **22** 条路径在 **Go / Python / JS** 三家拼写相同；Java demo 带前缀，故四家里拼写相同的只有 `/chat`」；②行号更正为 `main.go:492` / `main.go:793`（**事后漂移**）。本条正文已逐字迁入 [`…-95`][a53]。

### P2-142: 活体 OpenAPI 给 `PUT /api/mode` 宣告的是**另一个端点**的请求体 schema —— 它宣告的必填字段 `mode` 自己从不接受（实测 400），而它真正接受的 `mode_id` 根本没宣告；同一端点的 3 处响应示例也全是 camelCase
- **Status**: ✅ 已修（378 轮，零可执行行）—— 内层 record 改名（解除 springdoc 同名归并）+ 显式 `@JsonProperty("mode_id")` + 3 处示例与 400 宣告改真；5 个测试含两重阳性对照。全文逐字见 [`…-79`][a21]。

### P2-176: 四家 SDK README 首屏都把**单元测试数**当卖点硬编码，而这个数字**没有任何机制**保持同步 —— 本轮给 Java SDK 加 37 个用例后，Java 侧立刻从 213 变成过期断言（实测 250）

- **Status**: ✅ **已修（390 轮）** —— Java README 双语两处改为 **250**（197 + 46 + 7）；另三家实测一致，未改。⭐「自动对齐」属新增机制，只记录不实施。 —— **追加（392 轮）：Python 侧同样立刻过期**（460 → **471**），已按 `pytest --collect-only` 实测改准。本条正文已逐字迁入 [`…-95`][a53]。

### P2-130: 活体 OpenAPI 里 tool-use 的 `source` / `extractedData` / `prompt_number` 不说自己被丢弃 —— 而同一 DTO 文件的既有体例都注明了「只进日志、不落库、无效果」，`prompt_number` 甚至宣称「for ordering」
- **Status**: ✅ 已修（第 371 轮，纯 `@Schema` 描述文本、零可执行行）—— tool-use 的 `source`/`extractedData`/`prompt_number` 落 NULL/0 行。全文逐字见 [`…-72`](../archive/2026-10-20_backend-review-evidence-72.md)。

### P2-153: 活体 OpenAPI 有 **51** 个 schema 宣告 `type: string` 却带对象/数组 example —— 自相矛盾；PATCH 的请求体按 spec 发字符串活体 400

- **Status**: ✅ **已修（383 轮，Backend 代码方向）** —— **两套机制**：M1 `@Schema(example=…)` 不写 `type` ⇒ 解析成 `string`（**51** 处）；M2 `@ApiResponse` 不写 `content` + `ResponseEntity<Object>` ⇒ 推成 `object` 而实际是数组。本条正文已逐字迁入 [`…-95`][a53]。

### P2-168: demo 里 **6** 个静态 `/demo/*/health` 端点**恒返 200 `status:"ok"`** —— 后端完全宕机时仍报「健康」；与唯一的真健康检查 `/memory/health` **字段同名、语义相反**

- **Status**: ✅ **已修（388 轮）** —— ⭐ 这是「**存活信号不能被别的东西代理**」这一族的**第三个实例**，方向与第 183 轮那次相反（那次是**假绿**，这 6 个是**恒绿**）。本条正文已逐字迁入 [`…-94`][a52]。

### P2-143: 活体 OpenAPI 的 `ObservationEntity` **双向失配** —— 5 个幽灵属性（实现从不输出）+ 3 个真实属性（`content_hash`/`discovery_tokens`/`embedding_model_id`）根本没宣告
- **Status**: ✅ 已修（378 轮，零可执行行）—— 3 个字段补 `@JsonProperty`、2 个 List getter 标 `@JsonIgnore`；不变量测试「宣告集 == 实际序列化集」钉住，阳性对照逐个点名那 5 个幽灵属性。全文逐字见 [`…-79`][a21]。

### P2-146: 四家 SDK 的 README 都写「空 `tool_name` 会被后端以 400 拒绝、SDK 记一条日志后吞掉」—— 而客户端校验早在请求发出**之前**就拦下了它，请求数实测为 **0**

- **Status**: ✅ **已修（380 轮，Python 侧，文档 + 2 个回归测试）** —— EN/ZH 双语 README 恢复反事实措辞并写明「客户端拦下、不发请求」；**四家至此全部结转完成**。本条正文已逐字迁入 [`…-94`][a52]。

### P2-174: Java SDK 的**重试范围**（README 明写「25 个方法里 10 个会重试」）**零测试钉住** —— 整个模块把 `retry.max-attempts` 设为 1，于是「会重试」与「不会重试」发出去的请求数**完全一样**

- **Status**: ✅ **已修（390 轮）** —— Java SDK client 模块单测 **160 → 197**，三模块合计 **213 → 250**（197 + 46 + 7）全绿；SDK 主源码零改动。本条正文已逐字迁入 [`…-95`][a53]。

## 压缩记录

本轮按 **C10**（已结案条目正文整体迁入归档，工作文件只留标题 + 一行 Status 含归档指针）迁出
**16** 条已结案条目：`P2-142` / `P2-143` / `P2-146` / `P2-149` / `P2-153` / `P2-159` / `P2-168` /
`P2-174` / `P2-176` / `P2-177` / `P2-178` / `P2-179` / `P2-181` / `P2-182` / `P2-183`
—— 正文**逐字**保留在本归档，工作文件侧只留标题与一行 `- **Status**`（含本归档指针）。
**三个数方向相反、不可混用**：归档侧持有量 = 本文档体量；工作文件侧**迁出**量；**全轮净变化**。

全轮净变化：工作文件 **1261 行 / 145050 字节** → **1275 行 / 142603 字节**（净 **+14 行 / −2447 字节**）。
本轮新增三条（**P2-185 / P2-186 / P2-187**）的正文全部在本归档，工作文件侧只留标题与三行指针。
