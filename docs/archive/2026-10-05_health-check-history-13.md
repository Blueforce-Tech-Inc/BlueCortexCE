# Health-check history, rounds 261–266

> **来源**: `docs/drafts/health-check-task.md` 中第 **261–266** 轮巡检报告，**逐字迁出**，
>   工作文件保留第 267 轮及以后。
> **触发原因**: 写入第 272 轮后工作文件达 **1033 行**、越过 `MAX_LINES=1000`，
>   `doc-growth-check.sh` 返回 `COMPACTION_REQUIRED`（退出码 2，会打断 `&&` 链导致 commit 被跳过）。
> **依据**: 承第 250 轮确立的规则——**按轮号**匹配 `## 第 N 轮 — ` 提取（不按物理位置），
>   并**逐块断言自报轮号与标题一致**；断言「最后一个迁出块的结束位置 == 第一个保留块的起始位置」。
> 前序归档为 `2026-10-05_health-check-history-12.md`（第 255–260 轮）。
> **本文件创建后不得修改。**

---
## 第 261 轮 — 2026-10-04 19:46 — Python SDK + 设计文档

**方向**：代码 Python SDK · 文档 设计文档（doc round 162）

### 健康预检

| 项 | 状态 |
|----|------|
| 后端 37777 | UP（全程未重启） |
| 37778–37781 / 37790 | 全空闲 |
| 工作区 | 干净，HEAD `ac4e232` |

### 代码方向（Python SDK）：修完上一轮留下的描述矛盾；其余核实为真

**已修**：`client.py` 类 docstring 示例写 `base_url="http://localhost:37777"`，
而**下方第 72 行真实默认值是 `http://127.0.0.1:37777`** —— 示例教的值与默认值不同，
且**恰是 P2-57 证明会在 `-Djava.net.preferIPv6Addresses=true` 下失败的主机名形式**。
已改为默认写法 + 一段为何用 IPv4 字面量的说明。**零行为变化。**

**核实为真**：重试状态集与另三家一致；**不可解析的 body 抛错而非退化成空结果**
（docstring 记录了改前它会如何把网关的 HTML 错误页变成「格式良好的空结果」）；
**docstring 声明的「绝不改动调用方 session」在代码里确实成立** ——
`_owns_session` 控制关闭、`headers` 逐请求写入而非写入 `session.headers`，
避免 API key 泄漏到调用方的其它流量。

**一处跨家差异不立 finding**：Python **无响应体上限**，Go/JS 有 10MB；
但 SDK 自带 README 已写明差异**及原因**（`requests` 无可移植的流式大小钩子），
属**有据的设计决策**。**上一轮 P2-58 的疑点闭环**：Python 经显式 wire 映射
`"extracted_data" → "extractedData"` 正常处理，我上轮的探针结论确属误报。

### 文档方向（设计文档）：三处幻影端点，全部「加注」而非「改写」

扫全部 **31 个** `phase-3-design/*.md`，抓到 6 种幻影端点，**逐条实请求确认全部 404**：

| 文件 | 幻影 | 性质 |
|---|---|---|
| `20.md` §20.9 | `POST /api/ingest/session`、`PATCH /api/ingest/session/{sessionId}/userId` | **历史决策记录**（「✅ RESOLVED」「DECISION (2026-03-22)」），决策已落地但**路径不同** |
| `19.md` | `GET /api/extraction/{tpl}/search`、`GET /api/extraction/status` | 抽取 API 清单，6 条中实有 3 条 |
| `7.md` | `GET /api/extraction/allergy_info[/search]` | **把模板名当成路径** |

**处置一律为「保留原文 + 加一条事实性说明」**，理由是**这三处都在记录决策或设计**，
不是描述今天的 API —— 改写会抹掉「决策成立、路径不同」这一事实本身。
与第 256 轮（14 处 `{sessionId}` 逐字更正）**做法相反且是有意的**：
那批断言在各自语境里都是错的，这批不是。

**写入前逐条核实**：真实路径的 body 字段核到 `ApiRequests.java:43` 的
`@JsonProperty("user_id")`；实请求 `PATCH /api/session/r261-probe-nonexistent/user`
得**业务 404「Session not found」而非路由 404**，证明端点存在且 body 被接受。

### 变更检测与验收

```
指纹 6b856f89f2863a87fb502e12ba95b9a8b1ed9d64585137e1d77dfbb5ca20cae9
  →  ec1c529c42e71f64b05991bf8971653df2009c08e1e249f9b0a6a74dd3cbbb53
```

| 套件 | 结果 |
|------|------|
| `bash scripts/regression-test.sh --skip-build` | **45 / 0 / 1**（共 46） |
| `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` | **25 / 0 / 0**（带 P2-46 限定） |
| Python SDK `pytest` | **441 / 441** |

## 第 262 轮 — 2026-10-04 20:28 — JS/TS SDK + 架构文档

**方向**：代码 JS/TS SDK · 文档 架构文档（doc round 163）

### 健康预检

| 项 | 状态 |
|----|------|
| 后端 37777 | UP（全程未重启） |
| 37778–37781 / 37790 | 全空闲 |
| 工作区 | 干净，HEAD `2f29e0f` |

### 代码方向（JS/TS SDK）：补上一个被漏掉的导出 + 9 条直接单测

**缺陷**：`src/index.ts` 的「Wire helpers」一组导出 8 个中的 **6 个**，漏掉
`safeStringOrStringList` —— **唯一处理列表列真实 wire 形态的那一个**
（JSON 数组**或**逗号分隔串），被 `parseObservation` 用了 **5 次**。

**它为何能存活**：既有单测直接引 `../dto/wire-helpers` **绕过 barrel**，
**甚至导了 barrel 里根本没有的 `firstNonNullOr`** —— **没有任何测试从包入口
导入 wire helpers**，故无从发现。

**已修**：补导出 + **9 条直接单测**（从 `'../index'` 导入，**同时钉住导出面与行为**）。

**我第一版测试预期是错的、被 runner 立刻抓住**：以为 JSON 编码标量会落到逗号切分；
追实现发现 `JSON.parse` 成功 → 得到非列表 → 返回 `undefined`，**正是文档契约所述**。改测真实行为。

**双向注入 + 「谁抓得住」**：

| 撤掉导出后 | 结果 |
|---|---|
| `tsc --noEmit` | **退出 0**（`tsconfig` 的 `exclude` 含 `**/*.test.ts`，即既有 **P2-42**） |
| `vitest run` | **退出 1**，9 条失败 |
| 恢复后 `vitest run` | 退出 0，**259/259** |

### 文档方向（架构文档）：文档自相矛盾，四处已双语修正

`ARCHITECTURE.md:283-301` **本来就写对了**（含「`POST /api/ingest/session-start` 返 404」的活体注记），
**三百行之外的 Data Flow 图却错了四处**：

| 图中写的 | 实际 | 证据 |
|----------|------|------|
| `session-start` → **Ingestion** Controller | **Session** Controller | `SessionController.java:47,108` |
| PostToolUse → `wrapper.js observation` | `wrapper.js tool-use` | `wrapper.js:39-61` |
| 上下文注入 → `wrapper.js context-get` | **该命令不存在** | `grep -c context-get` = **0**；`proxy/` 下**无任何** `/api/context/*` 调用；上下文随 `/api/session/start` 响应返回 |
| SessionEnd → `wrapper.js summarize` | `wrapper.js session-end` | 同上 |

**图后加一条双语对照说明**，写明每处「图中说的 vs 实际的」与证据位置。

**我自己制造并当场发现两个问题**：①说明文字拆两行导致方框**底边框丢失**；
②中文版同格因 **CJK 占两个显示列**而**超框 8 列**（改前 21、框宽 13）——
均用 `east_asian_width` 显示宽度校验后修正。
**锚点校验脚本自己错了两次**（全角标点当字母保留、参照格切错）——**两次都是文档对、探针错**；
改正后两语种各 16 个内部锚点全部解析。

### 变更检测与验收

```
指纹 ec1c529c42e71f64b05991bf8971653df2009c08e1e249f9b0a6a74dd3cbbb53
  →  9ce7fd2583ea258630817865fa6e9e3f157ea9a442f3ffdd7e7765ddcb569e55
```

| 套件 | 结果 |
|------|------|
| `bash scripts/regression-test.sh --skip-build` | **45 / 0 / 1**（共 46） |
| `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` | **25 / 0 / 0**（带 P2-46 限定） |
| JS SDK `npm test` | **259 / 259**（250 + 9） |

## 第 263 轮 — 2026-10-04T22:06:01+08:00

### 健康预检

- 工作区进入本轮时干净，`HEAD = 0fa1081`；指纹与基线**一致**（`9ce7fd25…`），无待验收代码。
- `GET /api/health` → `{"service":"claude-mem-java","status":"ok"}`；`37778`–`37781`、`37790` 全空闲。
- `doc-growth-check.sh` 五份文档全 OK，退出 0。

### 代码方向（Demo）：四家 demo 的错误映射 2:2:1:1 分裂——**且两个方向相反**

新角度取「同一个请求打四家 demo」，四家在 `37778`/`37779`/`37780`/`37781` 同时起：

| 请求 | 后端实际 | Java | Python | Go | JS |
|------|----------|------|--------|----|----|
| `PATCH /demo/session/user` 未知 session | **404** `{"error":"Session not found: …"}` | **500** | 404 | 404 | 404 |
| `GET …/extraction/latest` 无抽取结果 | **200** + in-band `status:"not_found"` | **404（凭空造）** | 200 | 200 | 200 |

两条都实测；Java 那条还把后端 JSON **二次转义**塞进 `error` 字段。
根因：12 个控制器共 **40 个 `catch (Exception e)`**，**只有 3 个**走 `DemoErrors`
（`ObservationsController` 2 个、`FeedbackController` 1 个），其余 10 个控制器一律 500。
**已修**：`DemoErrors` 类 Javadoc 原先的「**Controllers** use …」在 2/12 时读起来像全覆盖声明，
已按现状改写为精确表述并点名反例；**零行为变更**，`mvn -o test` 通过。
**记为 P2-59，⏸ 记录不修**——修它要改 10 个控制器的对外 HTTP 状态契约。

**排掉两个伪线索**（都是**我错了，代码/文档是对的**）：
①Go demo 的 `/batch-observations`、`/create-observation` 与另三家不同名，实为**有意为之**
（源码注释写明避开 Go 1.25+ ServeMux 与 `/observations/{id}` 的路径歧义，README 也已登记）；
②后端对未知模板返 400/404 的两次「实测」，**都是探针把路径打错**
（真实为 `/api/session/{id}/user` 与 `projectPath` 必填），改正后结论翻转。

### 文档方向（运维/用户指南，doc round 164）

新角度取「文档里的命令与数字逐条实测」：

- **已修**：`DEVELOPMENT.md` / `-zh-CN.md` 称 `./test-all.sh` 计 **359** 个 Go 测试。
  用 `go test -json` 逐模块实测为 **302（根，含 dto）+ 13（genkit）+ 8（eino）+ 12（langchaingo）= 335**，
  差 24；已改为 335，并给 `go test ./...` 补上实测的 302。
  **独立佐证**：`go-sdk/cortex-mem-go/README.md:13,190-193` **同日**（2026-10-04）实测写的是 **362**
  （core 235 + dto 67 + eino 8 + genkit 13 + langchaingo 12 + `examples/http-server` 27），
  并已写明「`test-all.sh` 跳过的正是各 adapter 与 example 模块」——
  **362 − 27 = 335**，与我的实测完全吻合。故这是**口径差**而非我算错：脚本的 `MODULES` 不含 `examples/http-server`。
- **复核为准确、未动**：`phase3-acceptance-test.sh` 的 15 个 test functions（`log_test "Test N"` 恰 15 处）；
  `run-all-e2e.sh` 的 10 个本地套件（1/10–10/10）；Flyway 迁移区间「V1–V8, V11–V18」与实际 16 个文件吻合（确无 V9/V10）；
  5432/5433 端口分工；六份指南引用的全部脚本与仓库路径均存在。

### findings 压缩（第二十三 / 二十四批）

写入 P2-59 后 findings 达 1023 行、`COMPACTION_REQUIRED`（退出码 2）。
迁出 P2-58 的 `Scope`+`Evidence`（19 行）→ `evidence-14`；P2-31 整条（8 行，无条件已解决）
与 `## Current Status` 的 9 行历史注记 → `resolved-15`。**1023 → 991 行**。

**压缩脚本自己错了三次，全部靠干跑与终验发现，没有一次进 git**：
①`find_entry` 误用段落级边界切条目，**每条都被截成只剩标题**；
②`Current Status` 起点落在表格上而非历史注记；
③首次统计 `DemoErrors` 调用点时**只数了 `clientStatus` 赋值行**（6 处报成 3 处），
又按行**目测**把 6 处误判为分布在 4 个 catch 块（实为 3 个）——
改正为脚本按花括号深度统计后得到权威值 3。
**终验**：条目 47（HEAD 47 − P2-31 + P2-59）、**HEAD 每条非空行或仍在工作文件或逐字在归档，丢失 0 行**、
指针数 2+2 与归档块数 2+2 相等、归档链接全部可解析。

### 变更检测与验收

```
指纹 9ce7fd2583ea258630817865fa6e9e3f157ea9a442f3ffdd7e7765ddcb569e55
  →  f41e3df8bb3282e5540a7c3c8ea00203a372dd1ade73fe97eb7e8fde3283ba52
```

| 套件 | 结果 |
|------|------|
| `bash scripts/regression-test.sh --skip-build` | **45 / 0 / 1**（共 46） |
| `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` | **25 / 0 / 0**（带 P2-46 限定） |
| `examples/cortex-mem-demo` `mvn -o test` | **通过**（EXIT=0） |

**限定**：本轮唯一的代码改动是 `DemoErrors` 的 Javadoc 注释（零行为变更），
且验收跑在**未含本轮改动的 37777** 上（本会话全程未重启该进程）。

**记录但未改（沿用 240 余轮既定做法，不擅自扩大范围）**：`docs/drafts/` 下另有三个
**早期遗留的状态文件**——`patrol-last_direction`（**已跟踪**，内容仍是 `Java SDK`，
最后提交 2026-04-26）、`patrol-last-direction` 与 `patrol-state.md`（**均未跟踪**，
内容分别停在 `Java SDK` / `last_direction: JS SDK`、next `Demo`）。
现行轮换状态的**唯一权威来源**是 `patrol-state.json` 与 `patrol-rotation.md`。
已用 `grep` 确认**没有任何脚本读取这三个文件**（`patrol-rotation.md` 里的
`patrol-last-direction` 只是叙述性提及）。留待项目决定是否清理。

## 第 264 轮 — 2026-10-04T23:28:58+08:00

### 健康预检

工作区干净、`HEAD = b180fa4`；`GET /api/health` 正常；五份活动文档全 OK，退出 0。

### 一个必须先说清楚的前提：37777 跑的是**比当天修复早 13 小时的旧二进制**

`ps -o lstart= -p 42492` → **2026-10-03 19:57:37**；
而 `dc52c8c`（`/api/context/recent` 负值钳制修复）提交于 **2026-10-04 08:38:57**。

两个测试脚本都**只等** `37777` 就绪、**从不重建或重启**它，所以本会话此前的验收
一直在打这个陈旧进程。**本轮据此一度把一个早已修好的 500 当成了新缺陷**——
`/api/context/recent?limit=-1` 在 37777 上返 500，而源码里那道 `Math.max(1, limit)` 钳制
明明在位，其注释还写着「Reproduced 3/3 before this guard」。**是旧二进制，不是回归。**
在 `37790` 起当前构建复测，同一请求返 **200**。已把核对方法写成「进程新鲜度闸门」常设规则。

### 代码方向（Backend）：四个数值参数在负值上崩溃，其中两个把崩溃报成 200

新角度取「把 12 个带限额/深度参数的端点逐个枚举，核对是否钳制」。

| 端点 | 参数 | 负值实际结果 | 判定 |
|------|------|--------------|------|
| `/api/context/review` → `/api/context/preview` | `maxObservations` | SQL `LIMIT must not be negative`，被 catch 包成 **200** + body `Error: Failed to generate context preview` | **已修** |
| `/api/context/preview` | `maxSummaries` | `Stream.limit(-1)` → `IllegalArgumentException`，message 字面就是 `-1`，同样被包成 200 | **已修** |
| `/api/context/timeline` | `depth_before` / `depth_after` | `subList(fromIndex(1) > toIndex(0))` → 未处理 **500** | **已修** |
| `/api/timeline` | `depthBefore` / `depthAfter` | 同上 → 未处理 **500** | **已修** |
| `/api/context/preview` | `sessionCount` | `if (sessionCount > 0)` 把负值导向不带会话限定的查询 | 安全，不动 |
| `/api/context/preview` | `fullCount` | 消费方是 `for (i = 0; i < limit; i++)`，limit 为负时循环不执行 | 安全，不动 |
| 其余 6 个端点的 `limit` / `lines` | — | 已有 `[1, N]` 钳制 | 不动 |

**修法统一为「下钳到 0」而非 1**：这四者上 `0` 本就有既定含义
（空结果 / 仅返回锚点那一条），故 0、1、2、10、5000 的行为**逐字不变**，
只有原本崩溃的输入改变——属「已损坏行为可修 / 纯加宽修正」，不需要项目拍板。
`TimelineService` 一处修好两个 timeline 端点（两者都汇聚到 `getTimelineMap`）。
**刻意不加上界**：把 5000 压到 `MAX_PAGE_SIZE` 会改变合法请求的结果，属契约变更。

**双向注入验证（对照很强）**：`/api/context/timeline` 的 depth 取 0/1/10 分别返回
**1/2/5 条**观测，肉眼可分；`maxObservations` 取 0/1/2/5000 渲染结果亦各不相同。
修复后负值与 0 完全一致，可选取值输出不变，日志中异常数 **0**。

**我自己制造并当场发现四个错误，全部靠干跑与二次核对拦下**：
①首轮 grep 只找 `Math.min`/`Math.max`，把用 `if (limit < 1) limit = 1;` 写法的
`ExtractionController` 误判为「未钳制」——**结论反了，代码是对的**；
②测 timeline 时把参数名写成 `anchorId`，而 `/api/context/timeline` 的参数叫 `anchor`，
于是连正常的 `depth=10` 都返 400，差点误判成「负值之外还坏了」；
③首次统计 `DemoErrors` 那类问题时按行**目测** catch 块归属，改为脚本按花括号深度统计后
才发现目测值是错的；
④`sessionCount` 的第一次实测选了 **0 条 summary 的项目**，恰好被
`allSummaries.isEmpty()` 的守卫挡住，**表现与「没问题」完全相同**——
换到有 summary 的项目才确认它在 preview 路径上确实安全（`> 0` 分支挡住了）。

另清掉一处**既有损坏**（非本轮造成，源自 `4303504`）：P2-29 正文有四行重复、
两版措辞分叉，且「对 fire-and-forget…」成了无主语的孤句。已按较完整的措辞各留一次，
并对全文扫描同类相邻重复，**其余 0 处**。

### 文档方向（API 文档，doc round 165）

`API.md` / `-zh-CN.md` 此前只记录了数值参数的**解析**规则（十六进制前缀、静默接受），
**从未记录取值范围处理**。已在 *Query Parameter Conventions* 增补范围处理表，
逐参数写明负值与上界，并点明**七个参数完全没有上界**
（`?maxObservations=5000` 返回该项目持有的全部内容）。双语各 9 行、结构逐格对应，
并按仓库惯例各加一条 `(unreleased)` 变更记录。

**表内自述数字当场改过一次**：先写「四个没有上界的参数」，脚本统计实为
**7 个**（分布在 4 行），已改正——数字连同计数命令一起核对。

### 变更检测与验收

```
指纹 f41e3df8bb3282e5540a7c3c8ea00203a372dd1ade73fe97eb7e8fde3283ba52
  →  8d05b7d5947291b321345acc9fa83899798898b6e7c7d7a820316cb963861478
```

| 套件 | 结果 |
|------|------|
| `SERVER_URL=http://127.0.0.1:37790 bash scripts/regression-test.sh --skip-build` | **45 / 0 / 1**（共 46） |
| `BACKEND_URL=http://127.0.0.1:37790 EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` | **25 / 0 / 0**（带 P2-46 限定） |
| `mvn -o package -DskipTests`（backend） | **EXIT=0** |

**本次验收首次真正覆盖本轮改动**：目标是本轮自行构建并启动的 `37790`，
而非陈旧的 `37777`。这是对既有规则的加强，脚本本身未改。

**遗留结构压力**：`backend-review-findings.md` 已回到 990 行 / 47 条，
**Scope+Evidence 杠杆彻底耗尽（仅剩 2 行）**。本轮的门控问题因此**没有**写进 findings，
而是作为常设规则落在本文——它是**流程/工具**问题，而该文件自述只收 Backend 代码缺陷。

## 第 265 轮 — 2026-10-05T20:44:27+08:00

### 健康预检

工作区干净、`HEAD = d8e53c5`；37777 健康。**上轮新写的「进程新鲜度闸门」首次在下一轮生效**：
进程启动 `17:51:24` 晚于最新后端提交 `17:50:12`，闸门成立，验收目标有效。
五份活动文档全 OK，退出 0。

### 代码方向（Java SDK）：一个默认值为空串的属性，让捕获到的提示**记下了却再也召回不了**

审 `cortex-mem-spring-ai` 的三个拦截面（advisor / aspect / tools）——P1-1 覆盖的是 bridge advisor，
P2-21 覆盖的是健康指示器，这三个此前未逐一读过。

**P2-60（记录不修，文档已更正）**：`CortexMemoryAdvisor.Builder.projectPath` 默认 `""`，
而 `CortexMemAutoConfiguration` 又显式做 `getProjectPath() != null ? … : ""`——
**不设 `cortex.mem.project-path` 时，空串一路走到线上**。空串不是 `null`，
能通过 `UserPromptRequest.toWireFormat()` 的 null 判断，被当作 `"cwd": ""` 发出。
**活体实测**：后端返 `200 {"status":"ok"}`，行以 `project_path = ''` 落库；
该行**只有用空项目查才取得到**，任何真实项目路径都查不到。
**已修**：advisor 的 Javadoc 与两份 SDK README 均按现状写明，**零行为变更**。
**规模必须说准，否则会把这轮结论夸大**：查库后 `EMPTY-STRING` 只有 **2 行**（都是我自己的探针），
真正的多数形态是 `NULL`（**2043 行 / 2011 会话**，即完全不传 `cwd` 的客户端）——
**这是既有普遍现象的罕见写法，不是 Java 特有缺陷**。
**不修的理由**：三种改法（默认 `null` / 用 `user.dir` / 缺项目时拒绝记录）都改已发布 SDK 的公开默认行为，
其中 `user.dir` 还会把提示**归到用户并未选择的项目**下，比现状更糟。

**另两处核实无误、顺带补了如实的类注释（零行为变更）**：
①`CortexToolAspect` 的 `joinPoint.proceed()` 在捕获块**之前**调用，故**抛异常的工具完全不被记录**；
且这不是它能选的——`ObservationRequest` 与后端 `ToolUseRequest` **都没有 status 字段**，
`failed` 这个后端去重逻辑明确判定的值在写入面上根本无法表达。该分支**无测试覆盖**，已写进类注释。
②`CortexMemoryTools.searchMemories` 的「1-10」**确实被强制**（≤0 回落默认、>10 钳到 10），
但 `defaultCount` 那条分支**不经过** `Math.min(count,10)`——配了 >10 的默认值即绕过该区间，
而后端 `MemoryController:115` 同样不设上限（实测 `count=50` 照单接受）。

**测试数复核**：README 声称 196（143 client + 46 spring-ai + 7 starter），
`mvn -o test` 实测 **143 / 46 / 7，BUILD SUCCESS**，分解逐项吻合，**未改**。

**本轮我自己的探针错了四次，全部靠二次核对拦下**：
①探针发的是 `project_path`，而后端要的是 **`cwd`**，于是两行落库成 `NULL` 而非空串——
差点据此得出「空串被转成 NULL、问题不存在」的相反结论；
②取经验端点时用了不存在的 `GET /api/experiences`（真实为 `POST /api/memory/experiences`）；
③统计「可整体迁出的已解决条目」时**只读 `Status` 首行**，漏掉 P2-10 首行里的 `⏸`；
④改用 `body[i+1:]` 又**跳过了 `Status` 标题行本身**，而 `⏸` 正在那一行——
两次简化都指向「可以归档一条待决条目」，改正后候选归零。**四次都是探针错，文件是对的。**

### 文档方向（SDK README，doc round 166）

`cortex-mem-spring-integration/README.md` / `-zh-CN.md` 原先只写「捕获需要会话 ID」，
**紧接着的项目路径要求一个字没提**，而 `project-path` 那一行连默认值都没写。
已补一节说明两者是**独立**的两件事（会话 ID 决定**是否记录**，项目路径决定**归入哪个项目**），
附实测报文与召回限制，并点明 `NULL` 才是真实库中的多数形态；配置表的 `project-path` 行同步补上后果。
双语逐段对应。**刻意没加锚点链接**——我先写了个猜的锚点，随即当作「坏链正是我在抓的东西」删掉了。

### findings 压缩与容量（第十四批）

写入 P2-60 后 findings 达 **1007 行**、越过 `MAX_LINES=1000`。此时
**Scope+Evidence 杠杆已彻底耗尽**（仅剩 2 行），且修正扫描后
**「无条件已解决可整体迁出」的候选为 0**。故只把 P2-59 的 `Scope`（3 行文件引用）迁入
`2026-10-04_backend-review-evidence-15.md`、留一行指针（**-2 行**），
其余靠把 P2-60 自身写紧凑（把两条「核实无误」的事实移回本报告，它们本就不该占 finding 的行）。
**1007 → 1000 行整**，恰好合格，余量为零——**下轮写入任何新 finding 都会立刻再次越线**。

**顺带修正一处归属歧义**：上轮为 P2-31 留的指针写作「已整体迁出（entry）」，
且紧贴下一个标题，读起来像是属于 P2-29。已改写为指名 **P2-31**（归属纠正）。
**没有**顺手补那个空行：全文共有 **12 处**指针紧贴标题的同型写法，
在 CommonMark 下 ATX 标题本就能打断列表、**渲染无误**，属风格不一致；
只改一处反而让文件更不统一，且补满 12 处需要 +12 行、当前容量付不起。

### 变更检测与验收

```
指纹 8d05b7d5947291b321345acc9fa83899798898b6e7c7d7a820316cb963861478
  →  699a945080b2d1d5377b03b434496b82741251f75681132693cabc81dccd06ea
```

| 套件 | 结果 |
|------|------|
| `bash scripts/regression-test.sh --skip-build` | **45 / 0 / 1**（共 46） |
| `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` | **25 / 0 / 0**（带 P2-46 限定） |
| `cortex-mem-spring-integration` `mvn -o test` | **143 / 46 / 7 = 196**，BUILD SUCCESS |

**指纹虽变，但已逐行核实 3 个 Java 文件的 37 处增补**全部是 `//` 行注释与 Javadoc
（`git diff` 过滤后仅余注释行，**0 删除、0 可执行代码变更**），故按闸门规则仍完整跑了验收。

## 第 266 轮 — 2026-10-05T21:05:00+08:00

### 健康预检

工作区干净、`HEAD = c35f3a6`；37777 健康且**新鲜度闸门成立**（进程 17:51:24 晚于最新后端提交 17:50）。
五份活动文档全 OK，退出 0。指纹 `699a9450…` **与基线一致且未变** → 按门控**不跑完整验收、不推进基线**。

### 代码方向（Go SDK）：核实无误、未改

- **上下文取消**：全模块**只有一个**请求构造点（`client_impl.go:219`）且正确使用
  `http.NewRequestWithContext`；唯一的重试循环（`doFireAndForget:394`）在**开始前**与
  **每次退避中**都用 `select` 同时监听 `ctx.Done()` 与 `time.After`，**可中断**。
  全模块的 `time.Sleep` 只出现在 `examples/*/main.go`（演示代码，非 SDK）。
- **全量测试按 README 自己给的命令实测，9 个 module 逐个跑**：

  | module | pass | fail |
  |--------|------|------|
  | `.`（core+dto） | **302** | 0 |
  | `genkit` / `eino` / `langchaingo` | **13 / 8 / 12** | 0 |
  | `examples/http-server` | **27** | 0 |
  | 其余 4 个 `examples/*` | 0（**确无测试文件**） | 0 |
  | **合计** | **362** | **0** |

  与 README 写的「core 235 + dto 67 + eino 8 + genkit 13 + langchaingo 12 + `examples/http-server` 27 = 362」
  **逐项吻合**，且「另四个 examples 无测试文件」也分毫不差。**未改**。

### 一个差点被我当成缺陷的东西——**是文档已经写明的设计**

`test-all.sh` 的 `MODULES` 只有 `.`/genkit/eino/langchaingo，**不含 `examples/http-server`**，
故那 27 个测试不被它跑到；`go-sdk-unit-test.sh` 同样只有 4 个目标。看起来像「27 个测试无人执行」。
**但 Go SDK 的 README（双语）早就精确写明了这一点**：9 个独立 module、`test-all.sh` 只覆盖 362 里的 302、
被跳过的四个正是「最易在上游框架升级时腐化」的、并**直接给出跑全部九个的命令**。
**这是已声明的覆盖范围设计，不是缺陷**——我若照着「发现」去改 runner，反而会与 README 的既定说明相矛盾。
（先核实那 27 个测试本身**全绿**才敢下这个判断；若它们是红的，「无人执行」才真的是问题。）

### 文档方向（设计文档，doc round 167）：四个角度全扫、**零缺陷**

1. **文件:行号引用**（7 处，全文仅此 7 处）逐处核到源码：`AgentService:241` 正是传 `projectPath` 的调用点、
   `ExtractionStorageService:49` 正是 `@Transactional`、`:127` 正是 DLQ 构造、
   `StructuredExtractionService:211`/`:315` 正是 `findBySourceIn` 与 `extractAppendOnly` 的调用点。
   其中 2 处（`ObservationRepository:425`/`:644`）指向查询串的收尾行而非声明行，**差一行**，
   但实质主张（三参数签名、`findNewObservations` 存在）**均正确**，不修。
2. **`18.md` 声称的表行数**对活体库逐条核：`extracted_user_preference` = **18,373**（分毫不差）、
   `dlq_*` / `extraction_state` / `extraction_audit` **均 0**。**四个数字全对**。
3. **内部链接** 11 条全部可解析；**跨文件锚点链接 0 条**（文档只用纯文件链接）。
4. **方法名级扫描**：99 个形如 `foo()` 的标识符中，**真幻影 0 个**。
   初筛报 26 个「未找到」，逐条读上下文后发现**绝大多数是文档本就在说明它不存在**
   （「appear **0 times** in `backend/src/`」「have no definitions and no callers」
   「neither `CostConfig` nor `BudgetExceededException` exists as a class」「SUPERSEDED」
   「IMPLEMENTATION NOTE: … pseudocode」）或属外部库（`pg_try_advisory_lock`、`BeanOutputConverter.getJsonSchema()`）。
   唯一值得追的 `resolveOutputClass()`/`buildSchemaHint()` 出现在 `99-changelog.md` 的
   **2026-03-21 设计文档版本记录**里，而**当前的 `2.md` §2.3 仍在定义并使用这两个方法**——
   设计伪代码里的方法名与实现里的不同本属正常，故该 changelog 条目**准确且自洽**，不加注。

**本轮四个角度都是「核实无误」**，据此**未写新 finding**——这同时让已满的 findings 文件得以喘息一轮。

### 需要项目决策的两件事（本轮新增一项）

1. **`backend-review-findings.md` 已达 1000/1000**，所有压缩杠杆耗尽，下一条 finding 必然越线。是否拆分？
2. **本仓库没有任何 CI 跑测试**：`.github/workflows/` 下**只有一个** `docker.yml`，
   它做 checkout + QEMU/Buildx + 登录 GHCR + 构建推送多架构镜像，**不跑任何测试**；
   全仓库 `grep "go test"` 在 workflows 中**零命中**。也就是说 Go 的 362、Java 的 196、
   Python 的 441、JS 的 259 全部**只靠人工触发**。这是**基础设施问题**，
   且真正接上 CI 需要决定「跑哪些套件、要不要 provisioning 数据库与密钥」，
   **不在代码审查轮次的动手范围内**，故只记录不实施。
