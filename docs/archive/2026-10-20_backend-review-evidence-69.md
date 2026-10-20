# Backend Review Evidence 69 — 第 368 轮本轮新条目的整条正文（第 368 轮迁出）

> **归档规则**：承 `-62` ~ `-68` 的体例，迁出**本轮新写**的条目全文（写作时 findings 的
> 字节余量只剩 2084 字节），工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：90 行 / 9670 字节。**
>
> 本批迁出 **P2-126** / **P2-127** 两条新条目。
>
> **本轮代码方向为 Python SDK，文档方向为 API 文档（doc round 268）。**
>
> **本批两条的共同点**：都是**活体 OpenAPI 与实现/文档不一致**，但方向相反 ——
> P2-126 是**文档比 spec 更不完整**（已补文档），P2-127 是**spec 比实现更严**（只记录）。

---

### P2-126: API 文档的三个 ingest 端点请求体表**遗漏了活体 OpenAPI 声明的 6 个字段**——其中 `platform_source` 是 V18 引入且**确实生效**的，而 tool-use 的另三个是**接受却被静默丢弃**的
- **Problem**: `docs/API.md` 与 `docs/API-zh-CN.md` 里三个 ingest 端点的**请求体字段表**
  **一个都没列**活体 OpenAPI 声明的 6 个字段：
  `POST /api/ingest/tool-use` 缺 **4** 个（`platform_source`、`source`、`extractedData`、`prompt_number`）、
  `POST /api/ingest/user-prompt` 缺 **1** 个（`platform_source`）、
  `POST /api/ingest/observation` 缺 **1** 个（`platform_source`）。
  其中 `platform_source`（**V18 Platform Source Tracking** 引入）是**确实生效**的字段 ——
  三个 ingest 处理器都调用 `applyPlatformSource(session, body.platformSource())`
  （`IngestionController.java:141 / 255 / 352`），而它**完全没出现在请求侧文档里**。
  另外三个（tool-use 的 `source` / `extractedData` / `prompt_number`）则是**接受却被静默丢弃**的 ——
  这层语义此前**任何地方都没写**。
- **Evidence**:
  - **活体 OpenAPI 对拍**（`/v3/api-docs`，66 路径）：逐端点解析 `requestBody` schema 的属性与
    `required` 列表，与文档的字段表比对 —— 补全前 3 个端点共报出 6 个「活体有、文档无」的字段。
  - **源码**：`handleToolUse`（`IngestionController.java:110-150`）**从不读** `body.source()` 与
    `body.extractedData()`，且调用 `agentService.processToolUseAsync(..., cwd, **null**)` 时把
    提示序号**写死为 `null`**；`body.promptNumber()` 只在 user-prompt（`:231`）与
    observation（`:358`）里被使用。`body.source()` / `body.extractedData()` 只在
    `handleObservation`（`:345-346`）被使用 ⇒ **tool-use 的这三个字段是死的**。
  - **活体实测（带阳性对照与反向对照）**：`tool-use` 请求带 `prompt_number: 42` ⇒ pending 行的
    `prompt_number` 落库为 **NULL**；**阳性对照**——同一字段在 `POST /api/ingest/user-prompt`
    上落库为 **42** ⇒ 探针**确实读得到**这个值，故那个 NULL 是**丢弃**而非探针盲区；
    落库的 `tool_input` 里也不含 `source` / `extractedData` 的内容。
- **Impact**: 文档的**字段清单不完整**——按文档组装的请求体，调用方无从得知 `platform_source`
  存在（而它是 V18 平台来源追踪的入口），也无从得知 tool-use 上那三个字段是**收下就丢**的。
  活体 OpenAPI 仍然声明了这 6 个字段，所以**文档比活体 spec 更不完整**。
- **Fix（已实施，双语，纯文档、零行为变更）**：三张请求体表**各补上缺失的行**（共 6 行），
  `tool-use` 另**新增一段实测说明**，逐条写明哪三个字段被忽略、为什么（`processToolUseAsync(..., null)`
  写死）、以及两个同级端点如何照用这些字段；`platform_source` 则写明其写入**会话**、
  已存值不被覆盖（唯一例外：请求值非 `claude` 时替换已存的 `claude`）。
- **Not fixed（待用户决策）**: 让 `tool-use` **真的用上**那三个字段（把 `body.source()` /
  `body.extractedData()` / `body.promptNumber()` 传进 `processToolUseAsync`）属**对外行为变更**
  （落库内容会变），按既定纪律**只记录不单方面实施**。
  **三选一**：①**只更正文档**（**已实施**）；②**让 tool-use 真的用上这三个字段**（行为变更，
  需评估对既有观测数据的影响）；③**让 DTO 不再声明这三个字段**（即从活体 OpenAPI 移除它们，
  属**纯收窄**的契约表面变化，与①配套）。
  **任一选项都应先补后端对 `POST /api/ingest/tool-use` 请求体字段的测试**（现为零覆盖）。
- **Status**: ⏸ **tool-use 那三个字段要不要真用起来留待用户决策**（属对外行为变更）—— **文档缺口本轮已修**（双语，纯文档）：三个 ingest 端点的请求体表共补 **6 行**（tool-use 4 + user-prompt 1 + observation 1），其中 `platform_source`（V18 引入、**确实生效**，三个处理器都调 `applyPlatformSource`）此前**请求侧完全没有记载**，而 tool-use 的 `source` / `extractedData` / `prompt_number` 是**接受却被静默丢弃**的（`processToolUseAsync(..., null)` 写死），现已逐条写明并补上实测说明。**三选一见归档正文**。 条目全文已逐字迁入 [`…-69.md`](../archive/2026-10-20_backend-review-evidence-69.md)（第 368 轮）。
### P2-127: 活体 OpenAPI 把 `POST /api/ingest/user-prompt` 的 `prompt_text` 标为 **required**，而后端**只强制 `session_id`** —— 活体 spec 失实，而 API 文档是对的
- **Problem**: 活体 OpenAPI（`/v3/api-docs`）把 `POST /api/ingest/user-prompt` 请求体的
  `prompt_text` 列为 **required**，而**后端从不强制它** —— 端点只强制 `session_id`。
  于是**活体 spec 与实现不符**，而**API 文档是对的**（文档早已写明两个可选字段缺省或为空都被接受）。
  这与 P2-120 同型：**契约表面**声明了实现不会做的事。
- **Evidence**:
  - **DTO**：`ApiRequests.java:86` 给 `prompt_text` 标了
    `@Schema(description = "User prompt text", requiredMode = Schema.RequiredMode.REQUIRED)`
    —— 这正是活体 spec 里 `required: [prompt_text, session_id]` 的来源。
  - **处理器**：`IngestionController.handleUserPrompt`（`:213-235`）**只**校验 `session_id`
    （`:235` 起返回 `400 Missing required field: session_id`），**没有任何** `prompt_text` 的非空检查。
  - **活体实测（带阳性对照与阴性对照）**：
    只带 `session_id` 的请求 → **200** `{"status":"ok"}`（spec 说这该 400）；
    带上 `prompt_text` → **200**，且落库 `prompt_text` = `'p368 control'`（**阳性对照**：探针确实读得到这个值）；
    完全不带 `session_id` → **400** `{"error":"Missing required field: session_id"}`（**阴性对照**：
    端点确实只强制 `session_id`）。省略 `prompt_text` 时落库为**空串**（不是 NULL），
    与文档所述「stored with no text」相符。
  - **文档已是对的**：`docs/API.md` 的该端点写着「Both optional fields are accepted when missing
    **or** empty: a body carrying only `session_id` returns `200 {"status": "ok"}`」，ZH 同。
- **Impact**: **活体 OpenAPI 是对外契约表面**。任何据此生成的客户端（OpenAPI Generator、代理网关、
  契约测试）都会把 `prompt_text` 当成必填，而服务端并不需要它 —— 契约与实现不一致，
  且**不一致的方向是「比实现更严」**，故现网调用不会坏，但新客户端会被误导。
- **Not fixed（待用户决策）**: 改**后端注解**或改**后端实现**都属**对外行为契约变更**
  （前者改活体 spec 输出、后者改实际行为），按既定纪律**只记录不单方面实施**。
  **三选一**：
  ①**改 DTO 注解**：去掉 `prompt_text` 的 `requiredMode = REQUIRED`，使活体 spec 与实现一致
     （**纯放宽/向后兼容修正**，零行为变更；但改的是**后端源码**，属后端代码方向，建议在下一个
     后端轮次实施并重建 jar）；
  ②**改实现**：真正强制 `prompt_text` 非空（**收紧**，属破坏性变更 —— 会推翻 API 文档已记载的行为，
     并与四家 SDK 的现状相抵触：四家 SDK 均在客户端要求 `prompt_text`）；
  ③**维持现状**：spec 继续说 required 而实现不强制（即 P2-120 同型的 spec 失实），仅在本条留档。
  **任一选项都应先补后端对该端点的测试**（现为零覆盖）。
- **Status**: ⏸ **改后端注解、还是让实现真的强制、还是维持现状，留待用户决策**（属对外行为契约变更）—— **本轮只记录不实施**：活体 OpenAPI 把 `prompt_text` 标为 required（`ApiRequests.java:86` 的 `requiredMode = REQUIRED`），而处理器**只校验 `session_id`**；活体实测（只带 `session_id` → **200**，不带 `session_id` → **400**，阳性对照落库 `'p368 control'`）证实 **spec 失实、API 文档是对的**。选项①（去掉注解的 REQUIRED，**纯放宽**）属**后端代码方向**，建议下个后端轮次实施；选项②（实现真的强制）是**收紧**，会推翻文档已记载的行为；选项③维持现状。**三选一见归档正文**。 条目全文已逐字迁入 [`…-69.md`](../archive/2026-10-20_backend-review-evidence-69.md)（第 368 轮）。
