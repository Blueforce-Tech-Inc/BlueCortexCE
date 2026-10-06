# Health Check History 19 — 第 301–306 轮巡检报告（第 318 轮迁出）

> **归档规则**：巡检报告按轮次保留；达增长阈值时把最早的若干轮逐字迁入此处。
> **归档文件创建后不得修改。**

> 本批为 `docs/drafts/health-check-task.md` 越过 `MAX_LINES=1500` 而迁出
> **6 轮**（第 301–306 轮），工作文件保留第 307 轮起。

## 第 301 轮 — 2026-10-06T12:55:00+08:00

代码方向：**Go SDK**（零缺陷）；文档方向：**API 文档**（doc round 201，一项记录不修 P2-75）

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `0f0ca2c7…` / 885，**与基线一致**。doc-growth 全 OK。

### 代码方向（Go SDK）—— 无 sentinel 的状态码，实测不丢信息

**11 个 sentinel**（`ErrBadRequest` / `ErrUnauthorized` / `ErrForbidden` / `ErrNotFound` / `ErrConflict` /
`ErrUnprocessable` / `ErrRateLimited` / `ErrInternal` / `ErrBadGateway` / `ErrServiceUnavailable` /
`ErrGatewayTimeout`）覆盖 400/401/403/404/409/422/429/500/502/503/504，**每个都有非测试引用**。

**缺口是 405 / 413 / 418 这类没有 sentinel 的状态**——而 **405 是后端真会返的**
（活体：`GET /api/ingest/tool-use` → **405**，已实测）。
关键问题：调用方还能不能拿到状态码？

**读码**：`APIError` 自带 `StatusCode`，`Unwrap()` 才去映射 sentinel，
未命中的落到 `fmt.Errorf("cortex-ce: unknown error %d")`。
**但静态结论不能只靠读码**——写了临时 Go 测试实测：

| 注入状态 | `errors.As` 取回 | `Error()` | 是否误匹配 sentinel |
|---|---|---|---|
| 405 / 413 / 418 | ✅ `StatusCode` 精确 | `cortex-ce: API error 405: …` | ✅ 正确地一个都不匹配 |
| 404 / 400 / 429 | — | — | ✅ `errors.Is` 经 Unwrap 链正常命中 |

**结论：11 个 sentinel 是便利层，不是读取状态的唯一途径**；未映射状态**零信息丢失**。
**零缺陷。** 探针测试文件已删除。

### 文档方向（API 文档，doc round 201）—— 一项记录不修（P2-75）

**本轮最大的收获是一次被自己推翻的假设。**

初判：「后端对畸形观测 id 返 404，而 demo 注释声称 400，是失实陈述」。
探针打 `PATCH /api/observations/not-a-uuid` → **404**，看起来坐实了。

**但那是探针打错了路径**：`MemoryController` 类级是 `@RequestMapping("/api/memory")`，
真实全路径是 **`/api/memory/observations/{id}`**；我探的 `/api/observations/{id}` **根本不存在**，
那个 404 是**路由未匹配**、不是 id 校验。改打正确路径后：

| 请求 | 实测 |
|---|---|
| `PATCH /api/memory/observations/not-a-uuid` | **400** `{"error":"Bad Request"}`（Spring 转换失败） |
| `DELETE /api/memory/observations/not-a-uuid` | **400** 同上 |
| `PATCH /api/memory/observations/0000…0000`（合法但不存在） | **404** |
| `POST /api/memory/feedback`（id 在**体**里） | **400** `{"error":"Invalid observationId format: not-a-uuid"}` |

**第 102 / 201 轮早已裁决过同一件事**（那句 `Invalid observationId format` **只对 `submitFeedback` 成立**），
demo 的注释**准确**。**若照字面采信，会写下一条完全错误的「失实陈述」。**

**真正成立的发现（P2-75）**：文档把 `400` 写成「**Invalid field types in request body**」、
把 `404` 写成「Observation with given UUID not found」，**读起来就是「id 写错属于 404」**；
实际 **path 里的 id 写错是 400**（成因完全不同、却共用同一状态码）。
**按 404 当「不存在」写的客户端会落到通用错误分支。** 全库检索确认中英双语 API 文档**从未提及**这一情形。
**归因要准**：`MemoryController` 自己的 `@ApiResponse` 注解用的也是同一句窄措辞，
**API.md 是忠实镜像了注解——缺口源自代码里的契约描述**。
**记为 ⏸ 记录不修**：要改得同时动注解与中英双语文档，而注解是**对外发布的 API 契约描述**；
且这是**描述偏窄**（400 少列一种成因）、**不是陈述错误**，按「遗漏 ≠ 失实」不单方面改写对外契约。

### 变更检测

本轮**未改任何代码**（探针测试已删，仅 `docs/drafts/` 下的工作文件）。
指纹仍 `0f0ca2c7…` / 885，**与基线一致** → **不跑完整验收、不推进基线**。

### 下一轮

代码方向：**Python SDK**；文档方向：**SDK README**（doc round 202）。

## 第 302 轮 — 2026-10-06T13:20:00+08:00

代码方向：**Python SDK**（零缺陷）；文档方向：**SDK README**（doc round 202，零缺陷）

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `0f0ca2c7…` / 885，与基线一致。doc-growth 全 OK。

### 代码方向（Python SDK）—— 状态码→异常类映射，17 例实测

**12 个类**：`CortexError` / `APIError` / `ValidationError` + 9 个 `APIError` 子类。
其中 8 个是**固定状态码**的单参构造（`__init__(self, message=...)`），
**只有 `ServerError` 需要传入 `status_code`**——这是刻意的，因为 **5xx 跨多个码**。

**实测**（直接调用 `raise_for_status`）：

| 类别 | 状态码 | 结果 |
|---|---|---|
| 2xx 不抛 | 200 / 201 / 204 / 299 | **4/4 正确** |
| 固定码映射 | 400/401/403/404/405/409/422/429 | **8/8 类与码都精确** |
| 5xx | 500 / 502 / 503 / 504 | **4/4 `ServerError`，且各自保留原码、未塌成 500** |
| 未映射兜底 | 402 / 418 / 301 / 100 | **4/4 裸 `APIError` 且码精确** |

**17 例中 16 例一次通过**；唯一报出的「不符」是**我探针的预期写错了**——
我把 599 归进「未映射」期待裸 `APIError`，但 599 属 5xx、**`ServerError(599)` 才是对的**。
**探针错，不是代码错。**

**值得一提的强项**：Python **有 405 专有类 `MethodNotAllowedError`**，
而第 301 轮刚记录 Go **没有 405 sentinel**（经 `errors.As` 取回）。**跨 SDK 不对称，且两侧都不丢信息。**
本轮零改动。

### 文档方向（SDK README，doc round 202）—— 零缺陷

第 193 轮做过 Python+Go、第 196 轮做过 Java+JS，本轮取**错误处理面**这个尚未查过的角度。

**①Python README 的「Malformed Response Bodies」——实测精确成立**。它声称
「`APIError` 只用于 HTTP 状态；**存在但不可解析**的响应体（如代理返回的 HTML 错误页、状态却是 200）
抛 `CortexError`」。起一个只回 HTML+200 的本地服务器实测：

- 抛出类型**恰为 `CortexError`**（不是子类），
- `isinstance(e, APIError)` = **False**（与文档「不是 APIError」一致），
- 消息为 `cortex-ce: failed to parse /api/observations response: Expecting value: line 1 column 1 (char 0)`。

**②Go README 一个 sentinel 都没提（0 处），但教的是谓词用法**——
`cortexmem.IsNotFound(err)` / `cortexmem.IsBadRequest(err)`。**两个谓词都真实存在、已导出、语义与用法吻合**
（`go doc` 确认：`IsNotFound returns true if the error is a 404.`）。
**这与第 285 轮的裁决一致**：教的是另一种有效写法、且从不声称穷举，**故 nothing is false，未改**。
中英双语 README 均 0 处 sentinel，**一致**。

**③JS README 提及 2 个类 + 2 个谓词，逐一核实存在且从 `index.ts` 正确导出**：
`isNotFound` / `isRateLimited` 已定义并 re-export；`APIError` / `ValidationError` 在导出清单内。

**④一处探针错**：我第一次用 `find -path "*sdk*"` 找四份 SDK README，**漏了 Java 那份**——
它在 `cortex-mem-spring-integration/README.md`，目录名不含 "sdk"。改正路径后确认存在。

**本轮零改动（四份 README 本身）。**

### 变更检测

本轮**未改任何代码**（临时探针已删）。指纹仍 `0f0ca2c7…` / 885，**与基线一致**
→ **不跑完整验收、不推进基线**。

### 下一轮

代码方向：**JS/TS SDK**；文档方向：**设计文档**（doc round 203）。

## 第 303 轮 — 2026-10-06T13:50:00+08:00

代码方向：**JS/TS SDK**（零缺陷）；文档方向：**设计文档**（doc round 203，一项已修，四处）

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `0f0ca2c7…` / 885，与基线一致。工作区干净。doc-growth 全 OK。

### 代码方向（JS/TS SDK）—— 请求路径 100% 覆盖那唯一一处守卫

本轮问的是**超时、取消信号与体积守卫是否覆盖全部 25 个异步方法**。

**全 SDK 只有 1 处 `this.config.fetch(`（`client.ts:639`）**，位于私有 `doFetch` 内——
`AbortController`（`:635-636`）、`signal`（`:643`）、`clearTimeout`（`:701`）与两道体积守卫全在这一处。
**关键问题是：有没有方法绕过它。**

一开始数字看着刺眼：**2 处 `this.doFetch(` 对 25 个公开方法**。查下去才发现那是**漏斗不是绕过**——
`requestJSON:711` 与 `requestNoContent:728` **各自调用 `doFetch`**，
而 `doFireAndForget`（`:756`）接收的是调用方传入的函数体，那个函数体内部仍走前两者。
账目最终对得上：`requestJSON` 16 处 + `requestNoContent` 8 处 + `doFireAndForget` 3 处 = **27 个入口调用**，
对应 25 个方法，差额来自三处 fire-and-forget 的嵌套与两处二次调用。
**结论：25 个方法全部汇入唯一那处带守卫的 `doFetch`，覆盖率 100%，零缺陷。**

**探针错三处，全部先识别再采信**：
①第一版按缩进切方法体，把多个方法吞成一块、只认出 2 个走 `doFetch`，一度像有 23 个方法绕过；
②改用大括号配对后，发现真正的入口 helper 是 `requestJSON`/`requestNoContent` 而非 `doFetch`——
**我先猜错了 helper 名字**，是直接读 `healthCheck` 的方法体才纠正的；
③频次表用 `this\.[a-zA-Z]+\(` 统计，**匹配不到 `this.requestJSON<HealthResponse>(` 这种泛型调用**，
于是 `requestJSON` 一项整条从表里消失、只剩 `requestNoContent` 的 8——改正正则允许 `<...>` 后才出现 16。
**若照字面采信第 ①③ 版，会记下「23 个方法绕过守卫」和「只有 8 个方法走请求路径」两条完全错误的结论。**

### 文档方向（设计文档，doc round 203）—— 一处基线挂错，且**扩散到了正式文档**

**①`phase-3-design-walkthrough.md` 引用的 3 个文件全部存在，6 处「Documented in: Section X.Y」锚点全部可定位**
（`20.md` §20.2/§20.3/§20.9、`2.md` §2.2、`24.6.md` §24.6、`23.md` §23），符合
doc-review 任务里「引用具体章节应指向对应子文档」的要求。

**②真正的问题：一个正确的数字被挂在了错误的基线上**

walkthrough 两处（`:108` 与 `:270`）都写着：
「keeping token costs **~20% lower than full-prior approaches**」。

回 `23.md` §23.4b 的表（`:204`）核对：

| 方案 | 先验 Token | 新观测 | 合计 |
|---|---|---|---|
| Current (truncated prior) | ~500 | ~2000 | **~2500** |
| Current (full prior) | ~5000 | ~2000 | **~7000** |
| Append-only (proposed) | 0 | ~2000 | **~2000** |

**~20% 算得没错**：(2500−2000)/2500 = **20%**，且成本 $0.0005→$0.0004 同为 20%，
**基线是「当前那个截断先验实现」**。而对 full-prior 是 (7000−2000)/7000 = **~71%**。
**同一句把 20% 挂在 full-prior 上——数字对、基线错。** 中文版更直白：「Token 成本比**完整先前方案**低约 20%」。

**③「修掉一个说法 ≠ 修掉复述它的每个说法」——又一次**

改完 walkthrough 后全库搜同一措辞，**发现它已扩散到正式文档**
`docs/structured-extraction.md:449`，且 `docs/structured-extraction-zh-CN.md:447` 有对应中文版。
**已修四处**：walkthrough 两处 + 正式文档中英各一处，一律改为
「~20% lower than **today's truncated-prior path**（~2500 → ~2000）and **~71%** lower than a full-prior one（~7000）」，
并给出出处 `§23.4b of 23.md`。
**复核**：以**原文精确串**搜索（而非子串）确认中英原文措辞**零残留**——
先前一次用子串搜出的「命中」其实是我改好后的文本本身，属**检查的假阳性**。

### 变更检测

本轮**只改 `.md`**（4 处文档措辞 + `docs/drafts/` 工作文件）。指纹仍 `0f0ca2c7…` / 885，
**与基线一致** → **不跑完整验收、不推进基线**。

### 下一轮

代码方向：**Demo**（轮转回到 Demo）；文档方向：**架构文档**（doc round 204）。

## 第 304 轮 — 2026-10-06T14:15:00+08:00

代码方向：**Demo**（零缺陷）；文档方向：**架构文档**（doc round 204，零缺陷）

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `0f0ca2c7…` / 885，与基线一致。doc-growth 全 OK。

### 代码方向（Demo）—— 一条看着像注入的路径，查清后不是

`DemoProperties.resolveProjectPath` 在 key 不在 `demo.projects` 映射里时，
**直接把 key 本身当路径返回**（Javadoc 明写「treat as path」）。
5 个调用点用它把 `?project=` 解析成 `project_path`。
而 `SessionManagementService` 只在 `null`/空白时回落到 `"openclaw"`，
**其余原样当记忆隔离命名空间用**。

于是 `?project=../../etc` 会凭空造出一个名叫 `../../etc` 的记忆命名空间——
**看着像注入，值得查。**

**决定性的一问：这个值会不会流到任何文件操作？**
全量搜 demo 的文件系统调用（`Paths.get` / `new File(` / `Files.` / `toRealPath` / `normalize`），
**全部集中在 `FileReadTool`**，而它的根**只**来自 `demoProperties.getFileReadRoot()`——
**是另一个独立配置项**（P1-2 引入）。**`projectPath` 到不了任何文件系统调用。**

**结论：不是安全缺陷。** 它只能在后端造出一个任意字符串的命名空间（记忆检索会多一个查不到的分区），
**读不到任何文件**；且 demo 自第 294 轮起已绑回环。**记为「已查清的非问题」**，
以免日后某轮再把它当新发现重提。**零改动。**

### 文档方向（架构文档，doc round 204）—— 零缺陷

第 199 轮查的是双语结构、版本与行号，本轮换到**端点计数与组件名**。

**①API Layers 表的「15 methods」——对着活体 OpenAPI 数出来的**

文档 Viewer 行列出 13 条路径模式却写「**15 methods**」，乍看自相矛盾。
拉活体 `/v3/api-docs`（**62 paths / 67 operations**）按 Viewer 前缀逐条数：
**恰好 15 个操作**——因为 `/api/settings` 与 `/api/modes` **各含 GET+POST 两个操作**
（13 路径 − 2 重复 + 4 = 15）。**数字精确吻合。**

**②`/api/test/*` 与文档所列 (llm, embedding, all) 逐条对上**，活体恰 3 个操作。

**③文档断言的 13 个类名全部存在，零幻影**
（`Service`/`Controller`/`Repository`/`Entity`/`Tool`/`Advisor`/`Aspect`/`Config`/`Manager`/`Filter`/`Handler` 后缀）。

**本轮零改动（架构文档本身）。**

### 变更检测

本轮**未改任何代码**（仅 `docs/drafts/` 工作文件）。指纹仍 `0f0ca2c7…` / 885，**与基线一致**
→ **不跑完整验收、不推进基线**。

### 下一轮

代码方向：**Backend**；文档方向：**运维/用户指南**（doc round 205）。

## 第 305 轮 — 2026-10-06T14:40:00+08:00

代码方向：**Backend**（零缺陷）；文档方向：**运维/用户指南**（doc round 205，零缺陷）

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `0f0ca2c7…` / 885，与基线一致，工作区干净。doc-growth 全 OK。

### 代码方向（Backend）—— 限流覆盖面查清，AGENTS.md 那句属实

**`tryAcquire` 在整个后端只有 1 个生产调用点**（`IngestionController:132`），
只守 `POST /api/ingest/tool-use`；另三个 ingest 端点
（`session-end:168` / `user-prompt:212` / `observation:296`）**没有任何限流**。
另两个看似该用的方法 `getRemainingRequests`、`reset`、`resetAll` **生产调用数均为 0**。

**而 AGENTS.md 写的是「Rate limiting: 10 requests/60s per session (IPv6-aware)」**——
听着像全局生效，值得查。逐条查清：

- **「per session」属实**：唯一的生产键就是 `"tool-use:" + contentSessionId`。
- **「IPv6-aware」也属实，但它是**空键时的隐私兜底**（`generateFallbackKey():254` → `getRemoteAddr():276`），
  IP 校验 `:209-237` 双栈都支持、`X-Forwarded-For` 也防注入；
  **而生产键恒为非空**，所以这条 IPv6 路径**在生产中实际不可达**——但它是正确的兜底代码，**不是缺陷**。
- **API 文档没有夸大**：`docs/API.md:407/409` 的限流说明**正好位于 `### POST /api/ingest/tool-use`（`:358`）之下**、
  下一标题 `### Record User Prompt`（`:411`）之前——**限定到了具体端点，准确**；
  FAQ（`:2846`）更精确：「limited to 10 **tool-use** requests」。

**结论：零缺陷。** 三个零调用方法是服务上预留的公开 API（供将来使用/测试），
不构成缺陷；IPv6 兜底路径不可达但实现正确。

### 文档方向（运维/用户指南，doc round 205）—— 零缺陷

本轮从**跨文档引用**入手：`ARCHITECTURE.md` 在第 199 轮把 prd/dev 差异指向「deployment guide's §5.4」，
本轮核这个指向及其内容。

**①§5.4 存在且引用有效**（`DEPLOYMENT.md:482`「Embedding Model Configuration」）。

**②prd/dev 五组默认值逐条对上**（prd 侧第 199 轮已验，本轮复核 dev 侧）：
`https://api.openai.com` ↔ `https://api.deepseek.com`、`gpt-4o` ↔ `deepseek-chat`、
`https://api.openai.com` ↔ `https://api.siliconflow.cn`、
`text-embedding-3-small` ↔ `BAAI/bge-m3`、`1536` ↔ `1024`。

**③「短别名只定义在 prd」——属实**：`EMBEDDING_API_KEY`/`BASE_URL`/`MODEL`/`DIMENSIONS` 四个别名
确在 `application-prd.yml:10/11/13/14`，**`application-dev.yml` 里一个都没有**。

**④「§5.8 的开发示例钉了全部五个值」——属实**：§5.8（`:623`）存在，那五个变量在其中出现 **5 次**。

**⑤§5.6 数据持久化路径——两个变量都真被插值**，且默认值与文档逐字相符：
`docker-compose.yml:33` 的 `${POSTGRES_DATA_PATH:-postgres_data}` 与
`:86` 的 `${LOGS_PATH:-claude-mem-logs}`；文档内联的 compose 片段（`:126`/`:182`）与真实文件一致。

**本轮零改动（运维/用户指南本身）。**

### 变更检测

本轮**未改任何代码**。指纹仍 `0f0ca2c7…` / 885，**与基线一致** → **不跑完整验收、不推进基线**。

### 下一轮

代码方向：**Java SDK**（轮转回到起点）；文档方向：**API 文档**（doc round 206）。

## 第 306 轮 — 2026-10-06T15:05:00+08:00

代码方向：**Java SDK**（零缺陷，但**两度差点误报**）；文档方向：**API 文档**（doc round 206，零缺陷）

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `0f0ca2c7…` / 885，与基线一致，工作区干净。doc-growth 全 OK。

### 代码方向（Java SDK）—— 两次「以为有 bug」都被证伪

**①自动配置结构规范**：类级 `@ConditionalOnProperty(prefix="cortex.mem", name="base-url")`；
**每个 `@Bean` 都带 `@ConditionalOnMissingBean`**（用户自定义优先）；
可选依赖用 `@ConditionalOnClass(name=...)` 隔离。
四个开关的**默认极性与属性类完全一致**：`captureEnabled` / `retrievalEnabled` / `contextBridgeEnabled`
是 `matchIfMissing=true`（opt-out），`memoryToolsEnabled` 是 `havingValue="true"`（opt-in）——
`CortexMemProperties:20-27` 的默认值逐条对得上。

**②第一次疑似缺陷（错）**：`capture-user-prompt-enabled` 在任何条件注解里都看不到，
而 `CortexMemoryAdvisor.captureUserPromptIfActive`（`:148`）判的是 `if (!captureEnabled) return;`
——**看起来这个专用开关根本没被读**。**追到调用点才发现**：
`CortexMemAutoConfiguration:101` 是 `.captureEnabled(properties.isCaptureUserPromptEnabled())`
——**属性确实被接上了**，只是经构造参数注入到 advisor 里那个**同名字段**。
**「没接上」是我的误判**：我只看了字段名，没追注入点。README 那句「Independent of capture-enabled」**属实**。

**③核实两个开关真独立**（不是同一个开关的两种叫法）：
`capture-enabled` 只管 `ObservationCaptureService` bean（→ `CortexToolAspect` → `@Tool` 捕获）；
`capture-user-prompt-enabled` 注入 advisor，而 advisor 的 `captureEnabled` **全类只在 `:148` 用一次**、
就是 `recordUserPrompt` 之前那道闸。**两者互不影响，与文档一致。**

**遗留观察（非缺陷）**：advisor 的字段名叫 `captureEnabled`、语义却是「是否捕获用户提示」，
**命名易误导**——本轮我本人就被它带偏了一次。不改代码，仅记录。

**本轮零改动。**

### 文档方向（API 文档，doc round 206）—— 端点清单 67/67 全覆盖、零幻影

用活体 `/v3/api-docs`（**67 operations**）与 `docs/API.md` 双向对拍。

**①首次扫描报「3 个 live 端点没进文档」**（`/api/context/recent`、`/api/context/timeline`、
`/api/context/prior-messages`）。**这是探针错**——`API.md:14` **自己声明了标题约定**：
「`## Context` 段是例外：每个端点用 path 式 H4，标题里的路径带反引号」。
我的正则要求方法与路径之间直接是空格，**没匹配上那层反引号**。
**「先读文件自己声明的结构约定再写正则」——这条本会话已踩过不止一次。**
按约定改正后重扫：**live 但未记录 = 0**。

**②反向扫报出 1 个幻影 `PUT /api/modes`，也是假的**：它来自 **`:2897` 的变更日志行**，
内容是「曾把 POST 误写成 PUT 并已更正」的历史记录，而代码现在用的是 **`@PostMapping`**
（活体 `PUT /api/modes` 返 **405**）。**扫描器把变更日志当成了现行声明**——与第 284 轮踩过的坑同类。
**零幻影。**

**③本轮零改动（API 文档本身）。**

### 变更检测

本轮**未改任何代码**。指纹仍 `0f0ca2c7…` / 885，**与基线一致** → **不跑完整验收、不推进基线**。

### 下一轮

代码方向：**Go SDK**；文档方向：**SDK README**（doc round 207）。
