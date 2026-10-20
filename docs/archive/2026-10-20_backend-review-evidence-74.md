# Backend Review Evidence 74 — 第 373 轮本轮新条目的整条正文（第 373 轮迁出）

> **归档规则**：承 `-62` ~ `-73` 的体例，迁出**本轮新写**的条目全文（写作时 findings 的
> 字节余量只剩 231 字节，本轮先做了**两处零信息损失压缩**才腾出空间 —— C1「状态词」粗体
> 去标记 33 处、C2「ASCII 码」粗体去标记 70 处，**只删 `**` 标记、一个字未改**；硬证明：
> 去掉全文所有 `*` 后字符流与压缩前逐字相同），工作文件只保留标题与
> `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：128 行 / 11309 字节。**
>
> 本批迁出 **P2-134 / P2-135** 两条新条目。
>
> **本轮代码方向为 Go SDK，文档方向为 API 文档（doc round 273）。**
>
> **两条的共同点是「分类/声明」与「实际行为」脱节** —— 一处是 Go SDK 的错误分类函数对
> **裸哨兵**这一形态整体失效，一处是活体 OpenAPI **少声明**了运行时真实强制的项。
> 两处都先用**阳性对照**证明探针本身没问题（同一哨兵喂给 `APIError` 形态即判对；
> 同一端点带齐字段即 200），再据此认定是实现/声明的问题。
---

### P2-134: Go SDK 的 `IsClientError`/`IsServerError` 不认裸哨兵 —— 4xx/5xx 哨兵全判 false

### P2-134: Go SDK 的 `IsClientError`/`IsServerError` 不认裸哨兵 —— 4xx/5xx 哨兵全判 false
- **Problem**: Go 没有异常类，只��**哨兵错误**（`ErrBadRequest` 等）与 `*APIError`（带
  `StatusCode`）两种形态。`error.go` 里共 **15** 个 `Is*(err error) bool`，其中 **13** 个是状态分类
  函数（另两个是 `IsValidationError` 与 `IsRetryable`）；这 13 个里有 **11** 个带
  `errors.Is(err, ErrXxx)` 回退，**唯独 `IsClientError` 与 `IsServerError` 没有**，
  于是它们只认 `*APIError` 形态。结果：
  - `IsClientError(ErrBadRequest / ErrNotFound / ErrConflict / ErrUnauthorized /
    ErrForbidden / ErrUnprocessable / ErrRateLimited)` —— **7 个 4xx 哨兵全部返回 false**；
  - `IsServerError(ErrBadGateway / ErrServiceUnavailable / ErrGatewayTimeout)` —— **3 个 5xx
    哨兵全部返回 false**；
  - 连 `IsInternal` 的**注释**也成了失实陈述：它写着「Is a 5xx server error (500, 502, 503,
    504, etc)」，但 `IsInternal(ErrBadGateway / ErrServiceUnavailable / ErrGatewayTimeout)`
    **全部返回 false**（它的回退只认 `ErrInternal` 一个）。
  这与函数自身的名字、`IsInternal` 的注释、**同文件另外 11 个状态分类函数的写法**，以及
  **Python / JS 的同型函数**全部矛盾：Python 的 `is_client_error` / JS 的 `isClientError`
  用「带状态码的错误对象」实现，其 docstring 明写「(Go: IsClientError, JS: isClientError)」
  —— 三家被**显式文档化为并行 API**，而 Python/JS 的对应实现在自己的错误对象形态上判对，
  **只有 Go 的哨兵形态判不出来**。
- **实测记录**（活体 Go 程序，`replace` 指向本仓库 SDK，**每个比较都带阳性对照**）：
  - 裸哨兵：`IsClientError(ErrBadRequest)` = **false**、`IsServerError(ErrBadGateway)` = **false**、
    `IsInternal(ErrBadGateway)` = **false**；而 `IsBadRequest(ErrBadRequest)` = **true**、
    `IsBadGateway(ErrBadGateway)` = **true**（**阳性对照**：同一哨兵喂给「自己的」判定函数即判对
    ⇒ 探针看得见结果，失败只出在这两个函数的回退缺失上）；
  - `*APIError` 形态：`IsClientError(&APIError{404})` = true、`IsServerError(&APIError{503})` =
    true、`IsInternal(&APIError{502})` = true（**阳性对照**：形态对就判对）。
  - 跨家对照：Python `is_client_error(BadRequestError())` 与 JS `isClientError(new APIError(400))`
    均返回 **true**，且 Python 的测试已覆盖该路径。
- **根因**：不是设计选择，而是**遗漏** —— `IsClientError` 的实现体最后一行是 `return false`，
  `IsInternal` 的回退只写了 `errors.Is(err, ErrInternal)`。
- **最强佐证（写本条时才发现，此前一直只当成「读代码看出来的」）**：本仓库**早就有一套
  `Test*_SentinelFallback` 测试**，体例统一为「本函数对自己的哨兵返回 true、对另一个哨兵
  返回 false」；HEAD 上**已有 12 条**（`IsNotFound` / `IsBadRequest` / `IsUnauthorized` /
  `IsForbidden` / `IsConflict` / `IsRateLimited` / `IsUnprocessable` / `IsInternal` /
  `IsServiceUnavailable` / `IsBadGateway` / `IsGatewayTimeout` / `IsRetryable`），
  而**缺的恰好就是 `IsClientError` 与 `IsServerError` 这两条** ——
  **测试套件的缺口与实现缺陷精确重合**：12 个分类器被逐个钉住了哨兵形态，
  唯独两个真坏的没人钉 ⇒ 这个缺陷**是被「逐个钉哨兵形态」这件事漏掉的**，不是无人察觉。
  本轮补的两条正是**把这个既有套件补齐**，而非另起一套体例；
  既有的 `TestIsInternal_SentinelFallback` 断言 `IsInternal(ErrBadRequest)` 为 false，
  本轮的修改**不与它冲突**。
- **修复与影响面**：`IsClientError` 补 7 个 4xx 哨兵回退，`IsInternal` 补 3 个 5xx 哨兵回退
  （`IsServerError` 是 `IsInternal` 的别名，随之修正）。这是**纯加宽**：只会把 false 变成
  true，不存在任何 true→false 的翻转；且经检索确认 `IsInternal` / `IsClientError` /
  `IsServerError` 在 SDK 内部**零调用点**（Python/JS 也都只是对外导出），故**不影响任何
  内部行为**（重试判定走的是独立的 `IsRetryable`）。
- **补测**：`client_test.go` 新增 3 个测试函数（`TestIsClientError_SentinelFallback`、
  `TestIsServerError_SentinelFallback`、`TestErrorClassification_GenericErrorIsNeither`，
  最后一个是**阴性对照**：普通 error 两边都不该匹配）。
  **「能失败」已证**：把这三个测试拷到**修复前**的 `error.go` 上跑，`TestIsClientError_
  SentinelFallback` 报 **7 处**失败、`TestIsServerError_SentinelFallback` 报 **6 处**失败，
  而阴性对照在修复前后**都通过** —— 测试确实能失败，不是恒绿。
- **同方向跨家结转**：Java / Python / JS 三家的同型函数**没有这个缺陷**
  （Python/JS 的错误对象带状态码），故**无需结转**；但本条暴露的「同族函数逐个核对」
  这一角度值得对另两家各做一次（见本轮未决项）。
### P2-135: 活体 OpenAPI 两处**少声明**运行时行为 —— `session/start` 少 `project_path`、`quality-distribution` 少 400

### P2-135: 活体 OpenAPI 两处**少声明**运行时行为 —— `session/start` 少 `project_path`、`quality-distribution` 少 400
- **Problem**: 本轮文档方向做的是「**API 文档的必填标注 / 错误码表 vs 活体 OpenAPI**」
  对拍。结论是：**API 文档两版（EN / ZH）都是对的，错的是活体 OpenAPI 自己** ——
  它有两处**少声明**了运行时真实强制的行为。这是 P2-127（spec 把 `prompt_text` 标成必填、
  而运行时只强制 `session_id`，**spec 比服务端严**）与 P2-120（spec 声明了一个不可达的 400，
  **spec 比服务端松**）的**第三种方向**：**spec 比服务端松**，且这次是**两处**。
  - ① `POST /api/session/start`：活体 spec 的 `required` 只有 `['session_id']`，
    但运行时**拒绝**任何缺 `project_path` 的请求 —— 错误信息原文
    `Missing required field: project_path (or cwd)`，而 `cwd` 是 `project_path` 的别名
    （实测 `session_id` + `cwd` → **200**）。源码上，`SessionStartRequest.projectPath`
    **没有** `requiredMode = Schema.RequiredMode.REQUIRED` 注解（`ApiRequests.java:23-24`），
    而同一文件的 `ObservationCreateRequest.contentSessionId` / `projectPath` **都带**该注解。
  - ② `GET /api/memory/quality-distribution`：活体 spec 的 `responses` 只有 200 与 500，
    但实测 `project` 为空或全空白时**确实返回 400**
    （`{"error": "project is required"}`，`MemoryController.java:178-179`），
    而 API 文档 EN / ZH **两版都写了这条 400**。
- **实测记录**（**新鲜实例**跑的，不是常驻的旧 jar —— 这一点本身是本轮的一个探针教训）：
  - `POST /api/session/start` 传空体 → 400 `Missing required field: session_id`；
    传 `{session_id}` → 400 `Missing required field: project_path (or cwd)`；
    传 `{user_id}` → 400 缺 `session_id`；传 `{project_path}` → 400 缺 `session_id`；
    传 `{session_id, user_id}` → 400 缺 `project_path` ⇒ **两个字段都被运行时强制**。
  - `GET /api/memory/quality-distribution` 不带 `project` → 400（Spring 默认错误体）；
    `?project=` 与 `?project=%20` → 400 `{"error": "project is required"}`；
    `?project=/tmp/p373-no-such-project` → **200**（零命中但仍是 200）⇒ 该 400 只在
    **参数缺失/空白**时出现，不是「查不到数据」。
  - **阳性对照**：`POST /api/session/start` 带 `session_id` + `project_path` → **200**；
    带 `session_id` + `cwd` → **200**。
- **文档侧的核对结论（本方向未改一个字）**：
  - `docs/API.md` 与 `docs/API-zh-CN.md` 的「必填」列与活体 spec 的 `required` **逐端点对拍，
    两版各 6 个带请求体表的端点，除 `session/start` 外全部一致**；而 `session/start` 那一处
    恰恰是 **spec 少声明、文档正确**。
  - 错误响应表对拍：两版各 **11** 个端点可对拍，**唯一差异**就是 `quality-distribution`
    的 400 —— 同样是**文档有、spec 无**。
  - 查询参数表对拍：两版各 11 / 15 个端点可对拍，**零处必填性不符**。
  - 端点覆盖：两版各 **65** 个端点章节，活体 **71** 个操作，两版覆盖集合**相同**。
  ⇒ **API 文档两版都不需要改**；要改的是后端的注解。
- **为什么不实施**：给 `projectPath` 加 `requiredMode = REQUIRED`、给 quality-distribution
  加一条 `@ApiResponse(responseCode = "400")`，都会**改变对外发布的 spec**（前者让 spec
  变严、后者让 spec 多一个响应码），按既定纪律（对外契约变更不单方面实施）**记录不修**。
  另注：`project_path` 与 `cwd` 是别名关系，若把 `project_path` 标成必填，spec 就会
  「比运行时严」（只给 `cwd` 的请求运行时是 200、spec 会说缺必填）——
  这与同一 DTO 文件里 `ObservationCreateRequest` 的既有做法一致（标规范字段必填、
  在描述里注明别名），修法与取舍需项目决定。
- **本轮新立的同向问题**：另三处（`ingest/observation` 的 `content`/`cwd`/`session_id`、
  `session/start` 的 `platform_source`、`context/preview` 的 `platformSource`）是
  **文档表格漏写活体已有的字段** —— 属**遗漏**而非失实，且与 P2-126 同型，
  **不另立新条**，已在 P2-126 下追加本轮证据。
