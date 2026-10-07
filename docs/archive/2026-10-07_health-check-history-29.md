# 健康检查历史 27 — 第 334 轮（第 347 轮迁出）

> **归档规则**：承 `-19` ~ `-26` 的体例，迁出**较早轮次**的完整报告，
> 工作文件只保留 `## 第 N 轮 — ` 标题与一行自足指针；条目首尾的空行留在工作文件。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数取自 `doc-growth-check.sh` 的实测值，不手写。

> 本批为写入第 347 轮报告后 `docs/drafts/health-check-task.md` 达 **1531 行**
> （行数越线；字节尚未越线）而迁出 **1 轮**：第 334 轮。
>
> 工作文件里第 312~331 轮已是一行指针（更早各轮在 `-19` ~ `-26` 中），
> **第 334 轮是最早仍保留完整报告的一轮**，故只迁这一轮即可取回余量。
> 轮次边界按**轮号**匹配 `## 第 N 轮 — ` 定位，**不按行号相邻**——第 342 轮教训。

## 块 1 / 1：第 334 轮全文（第 347 轮逐字迁出）

## 第 334 轮 — 2026-10-07T08:44:00+08:00

代码方向：**Demo**（**零缺陷**）；文档方向：**API 文档**（doc round 234 —— **零缺陷**，
本轮改用「文档字段 ↔ 活体响应」对拍）

### 健康预检

37777 在监听（pid 4029，保留），health 200。指纹 `5d960e9b…` / 2218 **与基线一致**，
工作区干净，HEAD = `771ac91`。

### 代码方向（Demo）：三个零覆盖文件核完 + 全模块测试实跑

`CortexMemDemoApplication.java`(25 行)、`DemoProperties.java`(62 行)、`SessionStartClient.java`(42 行)。

**`DemoProperties.java` 触发了一次「疑似 P1-2 未修」的判断，随即被推翻。**
它的 Javadoc（`:31-34`）称「Root directory **the file read tool is confined to**」，
而 findings 里的 P1-2 明写该工具「**无根目录约束、无 `..` 检查、无白名单**」。
读 `FileReadTool.java` 才发现它**早已修好**：`resolveWithinRoot()` 先 `root.resolve().normalize()`
再 `startsWith(root)`，并对已存在路径补 `toRealPath()` 做 **symlink 逃逸检查**；
类 Javadoc 自己就写着「Recorded as P1-2」。`application.yml` 里 `address: ${SERVER_ADDRESS:127.0.0.1}`
与 `file-read-root: ${user.dir}` **两处都已补上且各自注明 P1-2**。
**P1-2 两半均已结案**，findings 条目正文陈旧但 Status 与归档指针已标明，属既定体例。

`DemoProperties.resolveProjectPath` 的实现与其 Javadoc（「Falls back to key if not in map」）
**逐字相符**。`SessionStartClient` 是 42 行的 SDK 委托壳，无逻辑。

**实证**：实跑 `mvn -o test`（EXIT=0），读 surefire 报告取准确计数 ——
`DemoErrorsTest` 8 + `DemoParamsTest` 13 + `FileReadToolRootEdgeCaseTest` 7 +
`FileReadToolTest` 8 + `MemoryHealthControllerTest` 4 = **40 个测试，0 失败 / 0 错误 / 0 跳过**。
日志里的堆栈是被断言的预期异常（工具读失败时 `log.warn`），非缺陷。

### 文档方向（API 文档，doc round 234）：字段 ↔ 活体对拍

第 329 轮验的是**端点存在性**（71 个端点、0 幽灵），本轮验**字段**。

**双语层：零漂移。** 抽两份文档全部表格首列的字段名做集合差 ——
EN 115 / ZH 105，**ZH 独有 0 个**；EN 独有的 10 个全是**表头词**
（`Field`/`Type`/`Date`/`Endpoint`…，ZH 里被翻译），**没有一个是真字段**。

**文档 ↔ 活体：先出 18 条「缺失」，逐条查证后全部归零。** 分四类：

| 归因 | 字段 | 判定 |
|---|---|---|
| 我的口径漏了**查询参数** | `anchor`/`offset`/`lines`/`debug`/`query`… | 仪器缺陷，非文档问题 |
| 表格里的 **SSE 事件名 / 示例值**，不是字段 | `initial_load`/`new_observation`/`processing_status`/`bogus_column` | 误报 |
| 后端用 `Map` 接 body，OpenAPI 未声明属性 | `q`（`POST /api/context/semantic`） | 后端 Javadoc `ContextController.java:463` 明写 `Request: { "q": … }`，**文档正确** |
| `content_hash`/`discovery_tokens`/`embedding_model_id` | OpenAPI schema 未声明 | **活体响应里三个都在**，文档正确 |

最后对「List Observations」小节做整节抽取（35 个字段名）与活体响应的对账：
**「文档有而活体无」= 0**。活体响应共 **34 个键**，与 `docs/api-json-naming-convention.md`
逐字列出的那 34 个**完全吻合**（含它显式点名的三个陷阱键 `project`/`narrative`/`extractedData`）。

**一处看着像矛盾、实则不是**：`platformSource` / `contentSessionId` 在文档里是**驼峰**，
而响应体是蛇形 `platform_source` / `content_session_id`。但它们出现在**查询参数表**而非
响应字段表里 —— 活体 `/v3/api-docs` 确认 `GET /api/observations`、`/api/summaries`、
`/api/prompts` 的参数名**就是** `platformSource` / `contentSessionId`（V18 刻意如此）。
**响应体蛇形、查询参数驼峰，是本仓既有约定，文档写的对。**
我第一次下结论时把这四行当成了响应字段表，**前提就错了**。

### 变更检测

本轮**未改任何代码或既有文档**，未新增 finding。指纹 `5d960e9b…` / 2218 **与基线一致**
→ **不跑完整验收、不推进基线**。37777 保留运行。

### 本轮踩到的仪器错误（三处）

①解析活体 `/api/observations` 响应时找的是 `observations`/`data` 键，
实际是 **`items`** → 一度误报「活体 0 条观测」，改用正确键后得 3 条、34 个键；
②字段对拍**只比对了 `components.schemas.properties`**，漏掉路径参数，
虚报 8 个「文档有而活体无」，补上 39 个查询参数后归零；
③把**查询参数表**的 `platformSource`/`contentSessionId` 当成**响应字段**，
据此差点写出一条「文档与活体命名不符」的**错误 finding**——
读活体规格才确认文档是对的。

另外一处**方法论提醒**：我用 `?platformSource=` 与 `?platform_source=` 各打一次，
**两次都返回数据**——因为未知查询参数被静默忽略，而返回的那条恰好就是 `claude` 源。
**那不是有判别力的对照**；权威是 `/v3/api-docs` 的参数声明，不是「看起来没报错」。

### 下一轮

代码方向：**Backend**；文档方向：**SDK README**（doc round 235）。
