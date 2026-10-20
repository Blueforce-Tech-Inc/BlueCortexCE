# Backend Review Evidence 72 — 第 371 轮本轮新条目的整条正文（第 371 轮迁出）

> **归档规则**：承 `-62` ~ `-71` 的体例，迁出**本轮新写**的条目全文（写作时 findings 的
> 字节余量只剩 732 字节），工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：88 行 / 7700 字节。**
>
> 本批迁出 **P2-130** 一条新条目，并记录 **P2-127** 的第 371 轮更正。
>
> **本轮代码方向为 Backend，文档方向为架构文档（doc round 271）。**
>
> **本条与前几条不同**：它不是「文档漏写」，而是**活体 OpenAPI 的描述文本在撒谎** ——
> 而**撒谎的样式正是这个 DTO 文件自己已经建立的体例**（`is_worktree` / `parent_project` /
> `platform_source` 三处都注明「只进日志、不落库、无效果」），**唯独 tool-use 的三个字段没写**，
> 其中 `prompt_number` 还宣称「for ordering」，而 tool-use 路径**根本不用它排序**。
---

### P2-130: 活体 OpenAPI 里 tool-use 的 `source` / `extractedData` / `prompt_number` **不说自己被丢弃** —— 而同一 DTO 文件的既有体例都注明了「只进日志、不落库、无效果」，`prompt_number` 甚至宣称「for ordering」
- **Problem**: `ApiRequests.ToolUseRequest` 的三个字段在**活体 OpenAPI**（`/v3/api-docs`）里的描述是：
  `source` = "Source attribution (e.g., 'manual', 'tool_result')"、
  `extractedData` = "Structured extracted data (JSON object)"、
  `prompt_number` = "Prompt number for ordering"。
  三处都**只字未提**这三个字段在 tool-use 路径上**被接受但被丢弃**；其中 `prompt_number` 的描述
  还宣称了一个**这个端点不提供的用途**（排序）。
  **对照本文件自己的体例**：`SessionStartRequest` 的 `is_worktree`（`ApiRequests.java:33-34`）、
  `parent_project`（`:36`）、`platform_source`（`:38`）**都**注明了「只写进应用日志、不落库、
  不影响任何行为」——**同一个文件、同一批注解，已经为「接受但不持久化」的字段确立了写法**，
  唯独 tool-use 这三个没有跟上。于是**活体 spec 里的三句话与代码的实际行为相反**。
- **Scope / Evidence**:
  - **活体实测（带阳性对照）**：`POST /api/ingest/tool-use` 带 `prompt_number=42`、
    `source="p371src-tooluse"`、`extractedData={"p371":1}` ⇒ `mem_pending_messages.prompt_number`
    为 **NULL**；`mem_observations.source` 与 `mem_observations.extracted_data` 命中 **0 行**。
  - **阳性对照（证明探针读得到、且字段本身存在）**：`POST /api/ingest/user-prompt` 带
    `prompt_number=7` ⇒ `mem_user_prompts.prompt_number` 落库为 **7**；
    `POST /api/ingest/observation` 带 `source="p371src-obs"`、`extractedData={"p371ctrl":2}`、
    `prompt_number=99` ⇒ `mem_observations.source` = `p371src-obs`、
    `extracted_data` = `{"p371ctrl": 2}`、`prompt_number` = **99**。
    ⇒ **同名字段在别的端点确实生效**，所以 tool-use 上的 NULL 与 0 行是**字段级丢弃**，
    不是「字段不存在」，也不是「探针读不到」。
  - **静态取证**：`handleToolUse`（`IngestionController.java:110-160`）只读
    `sessionId` / `toolName` / `toolInput` / `toolResponse` / `cwd` / `platformSource`，
    并以**硬编码 `null`** 作为 `processToolUseAsync(...)` 的 prompt number 位；
    `body.promptNumber()` 全后端只出现在 `:231`（user-prompt 处理器），
    `body.source()` / `body.extractedData()` 只出现在 `:345` / `:346`（observation 处理器）；
    `ToolUseRequest` 在全后端只被 `IngestionController:109`（OpenAPI 声明）与 `:110`（处理器）引用。
  - **同轮的系统性核对（活体 11 个带 `required` 的 schema）**：逐个「先带全部必填字段跑成功对照、
    再缺该字段跑判定」，结果是 **8 个真的强制**（`session/start` 的 `session_id`、
    `ingest/session-end` 的 `session_id`、`ingest/tool-use` 的 `tool_name`、
    `ingest/observation` 的 `content_session_id`、`memory/experiences` 与 `memory/icl-prompt` 的 `task`、
    `cursor/register` 的 `workspacePath`、`modes` 的 `mode`），**只有 2 个是装饰性的**：
    `prompt_text`（见 P2-127）与 `content_session_id` / `project_path`（**别名二选一**：
    服务端要求 `content_session_id` 或 `session_id` 之一、`project_path` 或 `cwd` 之一，
    而 spec 只写了规范名、**描述已注明别名** ⇒ **spec 比服务端更严，不是失实**，故不记缺陷）。
- **Status**: ✅ **已修（第 371 轮）** —— 三个描述按本文件既有体例改写为
  「Accepted but not persisted / not used on this endpoint —— the tool-use path never reads it /
  records a null prompt number」，并各自点名「同名字段在 `POST /api/ingest/observation`
  （以及 `user-prompt` 的 `prompt_number`）**确实**落库」。
  **改动只碰 `@Schema` 的 description 字符串，零可执行行、零行为变化**；
  已重建 jar 并在新实例上复验：活体 spec 的三段描述已是新文案。

## P2-127 的第 371 轮更正（选项①已实施）

- **原状态（第 368 轮）**：⏸ **改后端注解、还是让实现真的强制、还是维持现状，待决**
  （对外行为契约变更）—— 活体 OpenAPI 把 `prompt_text` 标为 required，而处理器**只校验 `session_id`**；
  活体实测（只带 `session_id` → **200**，不带 → **400**）证实 **spec 失实、API 文档是对的**。
  选项①（去掉注解 REQUIRED，**纯放宽**）属后端代码方向，建议下个后端轮次实施；选项②是**收紧**；
  选项③维持现状。**三选一见归档正文**
  （`2026-10-20_backend-review-evidence-69.md`，第 368 轮）。
- **第 371 轮实施选项①**：删除 `ApiRequests.UserPromptRequest.promptText` 上的
  `requiredMode = Schema.RequiredMode.REQUIRED`，并把描述改成
  「Optional — only session_id is validated. A request without this field is accepted and the
  prompt text is stored as an empty string.」
- **为什么这不需要用户决策**：这是**纯放宽 + 失实陈述修正**的组合 ——
  ① `requiredMode` 只是 springdoc 的 schema 元数据，**不参与运行时校验**，故**服务端行为一字未变**
  （实测：只带 `session_id` 仍 **200**，不带仍 **400**）；
  ② 改动方向是**去掉**一个 required 标记，**只可能让 spec 更松**，**不可能让按 spec 生成的客户端出错**；
  ③ 改完之后活体 spec 与服务端的真实校验**一致**，失实陈述消失。
- **复验（2026-10-20，新实例 37790，重建后的 jar）**：
  `UserPromptRequest.required` = `['session_id']`（改前为 `['prompt_text', 'session_id']`）；
  同一个进程里 `ToolUseRequest.required` = `['session_id', 'tool_name']`、
  `ObservationCreateRequest.required` = `['content_session_id', 'project_path']` **均未变**。
  对照组：在跑的老实例（37777）仍返回 `['prompt_text', 'session_id']` ⇒ **确实只改了这一个字段**。
- **仍然待决**：**选项②**（让实现真的强制 `prompt_text`，属**收紧**，会让现存的
  「不带 `prompt_text` 也能写库」的调用开始 400）与**选项③**（维持现状）**均未实施**，
  仍需项目决策；本轮只做了选项①。
