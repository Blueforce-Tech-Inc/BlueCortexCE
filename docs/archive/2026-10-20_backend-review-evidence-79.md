# Backend Review Evidence 79 — 第 378 轮本轮新条目的整条正文（第 378 轮迁出）

> **归档规则**：承 `-62` ~ `-78` 的体例，迁出**本轮新写**的条目全文（写作时 findings 的
> 字节余量只剩 **809** 字节，本轮先做了一次**零信息损失压缩**才腾出空间 ——
> 第 292/293 轮杠杆「缩短**已结案**条目的 Status 行」：缩短 **7** 行，
> **只删细节、不删结论**，且每个被删细节的 token 都**断言逐字存在于本归档**；
> 硬证明：条目标题数 **132** 不变、归档链接数 **196** 不变、行数 **1454** 不变、
> 文件字节 **149191 → 147254**），
> 工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：165 行 / 13110 字节。**
>
> 本批迁出 **P2-142 / P2-143 / P2-144** 三条新条目。
>
> **本轮代码方向为 Java SDK，文档方向为 API 文档（doc round 278）。**
>
> Java SDK 方向**逐条核实为真**（25 个接口方法逐个有测试，降级行为表 12/12 与实现相符，
> 打的 23 个路径与活体 OpenAPI 的 66 路径 23/23 全对，阳性对照：编造端点正确 MISS）；
> 本轮的三条**都在文档方向**（API 文档 + 机器可读契约），且**同属一类形状**：
> **活体 OpenAPI 说的不是实现真正做的事**。
> ① **P2-142**：`PUT /api/mode` 的请求体 schema **串到了另一个端点** ——
>    两个同名 record（`ModeSwitchRequest`）被 springdoc 按**简单类名**归并成一个 schema，
>    于是它宣告必填的 `mode`，而自己只认 `mode_id`；同一端点的 3 处响应示例也全是 camelCase。
> ② **P2-143**：`ObservationEntity` 的 schema **双向失配** ——
>    **5 个幽灵属性**（实现从不输出）+ **3 个真实属性**（`content_hash`/`discovery_tokens`/
>    `embedding_model_id`）根本没宣告；根因是 springdoc **不套用**全局 SNAKE_CASE 策略。
> ③ **P2-144**：`PUT /api/mode` 接受不存在的 `mode_id`，静默回落到默认模式，
>    响应里的 `mode_id` **回显请求值**而非真正生效的模式 ⇒ 响应体自相矛盾；
>    `loadMode` 兜底吞掉全部异常 ⇒ controller 的 400 分支是**死代码**。
>
> **共同形状**：三条都**不会报错**，都**正常退出**，都**给出一份看起来很正式的契约** ——
> 按它生成的客户端会发出实现必然拒绝的字段、把读不到的值当成 null、相信一个没生效的切换。
> 这是本轮最值得记住的一类缺陷。
---

### P2-142: 活体 OpenAPI 给 `PUT /api/mode` 宣告的是**另一个端点**的请求体 schema —— 它宣告的必填字段 `mode` 自己从不接受（实测 400），而它真正接受的 `mode_id` 根本没宣告；同一端点的 3 处响应示例也全是 camelCase

- **Problem**：`backend/src/main/java/com/ablueforce/cortexce/controller/ModeController.java` 的
  `PUT /api/mode` 用**内层 record** `ModeSwitchRequest`（分量 `modeId`，**无** `@JsonProperty`），
  而 `backend/src/main/java/com/ablueforce/cortexce/dto/ApiRequests.java` 里还有一个
  **同名** record `ModeSwitchRequest`（`@JsonProperty("mode")`），给 `POST /api/modes` 用。
  **springdoc 按简单类名解析请求体 schema** ⇒ 两个 record **归并成一个 schema**，
  活体 `/v3/api-docs` 里只剩一个 `ModeSwitchRequest`，**两个端点都指向它**：
  ```json
  {"required": ["mode"], "type": "object",
   "properties": {"mode": {"type": "string", "description": "Mode ID to activate (e.g., 'code', 'code--zh')"}},
   "description": "Mode switch request"}
  ```
  但 `ModeController` 的实现只认 `mode_id`（全局 SNAKE_CASE 把 `modeId` 变成 `mode_id`）——
  实测（活体 37790）：
  | 请求体 | `PUT /api/mode` | `POST /api/modes` |
  |--------|------------------|-------------------|
  | `{"mode_id":"code--zh"}` | **200** | 400「Mode ID is required」 |
  | `{"mode":"code"}` | **400** | **200** |
  ⇒ **按 OpenAPI 生成的客户端会把 `mode` 发给 `PUT /api/mode`，必然收到 400。**
  同一端点的 3 处响应示例（`GET` 200 / `PUT` 200 / `PUT` 400）也全写 camelCase
  （`modeId`/`observationTypes`/`observationConcepts`），而活体实况是 snake_case
  （`GET /api/mode` 实测键：`mode_id`/`name`/`description`/`version`/`observation_types`/`observation_concepts`）。
- **Scope / Evidence**：
  - 活体 OpenAPI **修复前**（37777 快照）：`/api/mode put` 与 `/api/modes post` 的
    `requestBody` **同指** `#/components/schemas/ModeSwitchRequest`；
    **修复后**：`/api/mode put` → `ActiveModeSwitchRequest`（`mode_id`，required），
    `/api/modes post` → `ModeSwitchRequest`（`mode`，required）—— **后者逐字未变**。
  - **全仓仅此一处同名 record 归并**：`grep` 全部 `record` 声明，简单名重复的只有 `ModeSwitchRequest`。
  - 端点总数 **66 → 66** 未变；schema 数 **55 → 56**（只多出重命名后的那一个）。
  - **顺带发现并已修**：同端点的 `@ApiResponse` 宣告了一个 **400 + JSON 体**
    （`{"modeId":"invalid","name":"error",…}`），而实现里**唯一可达的 400 是空体**
    （`mode_id` 为空/缺失 ⇒ `ResponseEntity.badRequest().build()` ⇒ **0 字节**），
    且那段 JSON 体的分支是**死代码**（见 P2-144）。已把 400 的宣告改成「空体」并把描述改真。
  - 文档侧同批修：双语 API 文档的「Set Active Mode」响应示例仍写旧的
    `"name": "代码模式"` / `"description": "开发工作流模式"`，而活体 `code--zh` 实测是
    `"Code Development (Chinese)"` / `"Software development and engineering work"` ⇒ 双向订正。
- **Status**：✅ 已修（378 轮）—— 三处改动，**可执行行 0**：
  ① 内层 record 改名 `ActiveModeSwitchRequest`（解除 springdoc 同名归并，wire 格式不变）；
  ② 分量加 `@JsonProperty("mode_id")`（**显式钉住 wire 名**，运行时输出逐字不变 ——
     全局 SNAKE_CASE 本来就产出 `mode_id`）；
  ③ 3 处响应示例与 400 宣告改成与实况逐字相符。
  新增 `ModeControllerOpenApiContractTest`（**5** 个测试），含**两重阳性对照**：
  去掉 `@JsonProperty` ⇒ 宣告名变回 `modeId`（测试失败）；
  改回同名 record ⇒ 两个 schema 归并成 `[ModeSwitchRequest]`（测试失败）。
  活体复验：`mode_id` → 200、`mode` → 400、`POST /api/modes` 逐字未变。全文逐字见
  [`…-79`](../archive/2026-10-20_backend-review-evidence-79.md)。

### P2-143: 活体 OpenAPI 的 `ObservationEntity` **双向失配** —— 5 个幽灵属性（实现从不输出）+ 3 个真实属性（`content_hash`/`discovery_tokens`/`embedding_model_id`）根本没宣告

- **Problem**：`ObservationEntity` 是 JPA 映射类，JSON 名来自两处：显式 `@JsonProperty`
  与全局 `spring.jackson.property-naming-strategy: SNAKE_CASE`。
  **springdoc 解析 schema 用的是它自己的 mapper，不套用那条命名策略** ⇒
  凡是没有 `@JsonProperty` 的多词字段，**声明出来的是 camelCase，运行时输出的是 snake_case**。
  实测（活体 37777 / 37790，`GET /api/observations`）：
  | 方向 | 字段 |
  |------|------|
  | **幽灵属性**（OpenAPI 宣告、实现从不输出） | `filesRead`、`filesModified`、`contentHash`、`discoveryTokens`、`embeddingModelId` |
  | **真实属性**（实现输出、OpenAPI 没宣告） | `content_hash`、`discovery_tokens`、`embedding_model_id` |
  ⇒ 按 OpenAPI 生成的客户端**读不到那 3 个真实字段**（恒为 null），**又白等那 5 个不存在的字段**。
  另有 2 个字段是**两套名字并存**：`files_read`/`files_modified` 在 schema 里**既**是 string
  （`getFilesReadJson()` 产出）**又**多出一份 array（`filesRead`，来自字段与 List getter）。
- **Scope / Evidence**：
  - 活体 OpenAPI 修复前 `ObservationEntity` **36** 个属性；活体响应 **34** 个键；
    修复后两者**逐字相等**（`wire-only: []`、`openapi-only: []`）。
  - 根因定位：`ObservationEntity` 里 `contentHash`/`discoveryTokens`/`embeddingModelId`
    **没有** `@JsonProperty`（相邻的 30 来个字段都有）；`filesRead`/`filesModified`
    的字段没有，但 `getFilesReadJson()`/`getFilesModifiedJson()` 上有
    `@JsonProperty("files_read")`/`("files_modified")` ⇒ springdoc 把
    「字段 + List getter」与「JSON 字符串 getter」当成**两个**属性。
  - **只给 `ObservationEntity` 之外的两个实体也查过**：`SummaryEntity`/`UserPromptEntity`
    的多词字段**全部**带 `@JsonProperty` ⇒ 无此问题；
    `SessionEntity`/`PendingMessageEntity` 有同样形态的字段，但**没进 OpenAPI** ⇒ 无对外失实。
  - **一个必须避开的坑**：给 `filesRead` 字段补 `@JsonProperty("files_read")` **会炸** ——
    Jackson 会把「List getter」与「JSON 字符串 getter」并成同一个属性，
    触发 `Conflicting getter definitions for property "files_read"`。
    实测：改字段不行，**改 getter（标 `@JsonIgnore`）才行**，因为 wire 上那个键本就归
    `getFilesReadJson()` 所有。
- **Status**：✅ 已修（378 轮）—— **可执行行 0**：
  ① `contentHash`/`discoveryTokens`/`embeddingModelId` 三个字段补 `@JsonProperty`
  （值与 SNAKE_CASE 的产出**逐字相同** ⇒ 运行时输出不变）；
  ② `getFilesRead()`/`getFilesModified()` 标 `@JsonIgnore`（wire 上那两个键归 JSON 字符串
  getter 所有，List 版本从不落到输出 ⇒ 运行时输出不变）。
  新增 `ObservationEntityWireFormatTest`（**2** 个测试）：一条是**不变量**——
  「OpenAPI 宣告的属性集 == 实体实际序列化的键集」；另一条钉住
  `files_read`/`files_modified`/`facts`/`concepts` 仍是 **JSON 编码字符串**而非数组。
  **阳性对照**：修前该不变量测试失败并逐个点名那 5 个幽灵属性。全文逐字见
  [`…-79`](../archive/2026-10-20_backend-review-evidence-79.md)。

### P2-144: `PUT /api/mode` 接受不存在的 `mode_id` 并静默回落到默认模式，响应里的 `mode_id` **回显请求值**而非真正生效的模式 —— 而它宣告的那个 400 分支是**死代码**

- **Problem**：`ModeService.loadMode(String)` 在模式文件找不到时**兜底**：
  ```java
  try {
      return loadModeFile(modeId);
  } catch (Exception e) {
      logHappyPath("Mode file not found: {}, falling back to 'code'", modeId);
      …
      return loadMode(DEFAULT_MODE);
  }
  ```
  ⇒ `setActiveMode` **永不抛异常** ⇒ `ModeController.setActiveMode` 的 `catch` 分支
  （400 + `ModeResponse` 体）**不可达** ⇒ 活体 OpenAPI 宣告的
  「Invalid mode ID」**永远不发生**。
  而 `ModeResponse.modeId` 在成功分支里填的是**请求的 id**，不是真正生效的模式：
  实测（活体 37790）`PUT /api/mode {"mode_id":"bogus"}` ⇒ **200**，
  响应体 `{"mode_id":"bogus","name":"Code Development",…}` —— 名字/描述/版本全是
  默认模式 `code` 的，唯独 `mode_id` 回显了没生效的 `"bogus"`；
  紧接着 `GET /api/mode` 实测仍是 `mode_id=code`。
  ⇒ **响应体自相矛盾**：它声称当前模式是 `bogus`，而系统里根本没有这个模式。
- **Scope / Evidence**：
  - 可达/不可达逐条实测（活体 37790）：
    | 请求 | 结果 |
    |------|------|
    | `{"mode_id":""}` / `{"mode_id":"   "}` / `{}` | **400**，**响应体 0 字节** |
    | `{"mode_id":"bogus"}` | **200**，回显 `mode_id=bogus`，实际生效的是 `code` |
    | `{"mode_id":"code--zh"}` | **200**，生效 `code--zh` |
  - 活体 OpenAPI 修复前宣告的 400 体是 `{"modeId":"invalid","name":"error",…}` ——
    **既不是可达的 0 字节体，键名也是 camelCase** ⇒ 双重失实。
  - 双语 API 文档的「Set Active Mode」段**只写了 200**，未写 400 ⇒ 散文侧无冲突。
- **Status**：⏸ **行为要不要改留待用户决策**（对外行为契约变更，按纪律先记后定）——
  三条候选修法：**①** 端点改为拒绝不存在的 `mode_id`（返 400，与它自己的 `@ApiResponse`
  原本的承诺一致，但会把今天的「静默回落」变成硬失败）；**②** 保持回落，但让响应的
  `mode_id` 回显**真正生效**的模式（不再自相矛盾）；**③** 维持现状并把契约写死。
  **失实陈述已修**（纯注解/文档，零可执行行）：400 的宣告改为「空体（0 字节）」，
  `@Operation` 描述改为「未知 `mode_id` 不会被拒绝，默认模式保持生效且响应回显请求 id」。
  全文逐字见 [`…-79`](../archive/2026-10-20_backend-review-evidence-79.md)。
