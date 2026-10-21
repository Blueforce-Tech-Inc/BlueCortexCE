# Backend Review Evidence 84 — 第 383 轮本轮新条目的整条正文（第 383 轮迁出）

> **归档规则**：承 `-62` ~ `-83` 的体例，迁出**本轮新写**的条目全文。
> 本轮 findings 写入前**行数与字节同时逼近双阈值**（1494 行 / 149328 字节，余 6 行 / 672 字节），
> 故本轮**做了两处新压缩**（C6 / C7），见文末「压缩记录」。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：398 行 / 22748 字节。**
>
> 本批迁出 **P2-152 / P2-153 / P2-154** 三条新条目。
>
> **本轮代码方向为 Backend，文档方向为 API 文档（doc round 282）。**
>
> 本轮的形状是**同一个毛病的两种机制**：机器可读的契约（`/v3/api-docs`）在
> **51 个 schema** 上自相矛盾 —— 一边宣告 `type: string`，一边带着对象或数组的
> `example`；而人读的文档在**四处**把端点计数写成了一个早已过时的数字。

---

## P2-152: 活体 OpenAPI 的 `FeedbackRequest` 请求体描述仍写「not yet implemented」—— 而该端点早已实现并返回 200

### 事实

`backend/src/main/java/com/ablueforce/cortexce/dto/ApiRequests.java:171`：

```java
@Schema(description = "Feedback submission request (not yet implemented)")
public record FeedbackRequest(
    @Schema(description = "UUID of the observation to provide feedback for", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("observationId") String observationId,
    ...
```

这段类级 `@Schema` 的描述会**原样进入活体 OpenAPI**：

```json
"FeedbackRequest": {
  "required": ["feedbackType", "observationId"],
  "type": "object",
  "properties": { ... },
  "description": "Feedback submission request (not yet implemented)"
}
```

而 `POST /api/memory/feedback` **是已实现的端点**。活体实测（2026-10-21，37790）：

| 步骤 | 结果 |
|------|------|
| `POST /api/memory/feedback` body `{"observationId":"<真实 id>","feedbackType":"SUCCESS","comment":"p383 probe"}` | **HTTP 200** `{"status":"ok","observationId":"938d781b-2698-4c50-aec0-41dd2ae0410f"}` |
| 回读该观测 | `feedback_type = SUCCESS`、`user_comment = p383 probe`、`feedback_updated_at = 2026-10-20T19:04:08.479515Z` |

⇒ 活体 spec 对着一个**能用的**端点说「尚未实现」。而这条描述正是 SDK 生成器
读到的文字。

### 定性：**事后漂移**（不是写入即假）

用 `git log -S` 追：

| commit | 日期 | 事实 |
|--------|------|------|
| `7a04284` | 2026-03-17 | 端点加入，**当时确实返 501**：`@Operation(summary = "Submit manual feedback (not yet implemented)")`、`@ApiResponse(responseCode = "501", …)`，方法体 `return ResponseEntity.status(501).body(Map.of("status", "not_implemented", …))` |
| `4359b90` | 2026-03-27 | `refactor(api): add Request DTO classes for type-safe endpoint parameters` —— 新建 `ApiRequests` 并把这句**当时为真**的措辞抄进了 DTO 的 `@Schema` |
| `4953b27` | 2026-03-31 | `fix: implement feedback API (501→200) + batch empty IDs returns 200` —— **实现了端点**，把控制器上的 `@Operation` / `@ApiResponse` 全部改真（501 那几行被删掉），**但没碰 `ApiRequests.java`** |

`git merge-base --is-ancestor 4359b90 4953b27` ⇒ 成立：DTO 那句写下时端点确实还返 501，
**是 `4953b27` 把它变成了假话**。⇒ **事后漂移**（与第 380 轮 P2-146 的「写入即假」定性相反，处置也不同）。

### 按断言清扫

全库（排除 `node_modules`、归档与 `docs/drafts/screenpulse-*`）搜
`not yet implemented` / `not_implemented` / `501 Not Implemented` ⇒

- `backend/.../dto/ApiRequests.java:171` —— **活体代码里唯一一处**；
- `docs/drafts/screenpulse-implementation-progress.md:168` —— 无关文档（ScreenPulse 的待办表）；
- `docs/archive/2026-09-30_backend-review-findings-history.md:1287` —— 历史归档，记着
  「`POST /api/memory/feedback` 返回 501 "not yet implemented" — E2E 测试期望正常响应 | P1 | ✅已修复」。

**活体 OpenAPI 全量扫描**（所有 operation 的 summary/description + 所有 component schema 的 description）
匹配 `not yet|not implemented|not supported|no longer|deprecated|placeholder|TODO|coming soon|currently returns`
⇒ **命中恰好 1 处**：`FeedbackRequest`。（另一处命中是 `DELETE /api/cursor/register/{projectName}`
的正常散文「will no longer be automatically updated」—— 假阳性。）

四家 SDK 的 `submitFeedback` 注释**都不带**这句失实措辞；`docs/API.md` 与
`docs/API-zh-CN.md` 的「Submit Feedback」小节**也都写的是 200 OK + 400/404 错误响应**，两处均为真。

### 修法

只改这一行描述（**零可执行行**）：

```java
@Schema(description = "Feedback submission request. The endpoint is implemented: it persists feedback_type, "
    + "user_comment and feedback_updated_at on the observation.")
```

**不删**这段（理由是对的，只是时态错），改成如实说明现在发生的事 —— 与第 380 轮
P2-146 的修法同构：**恢复事实、而不是删掉事实**。

---

## P2-153: 活体 OpenAPI 有 51 个 schema 宣告 `type: string` 却带着对象/数组 example —— 自相矛盾；其中 PATCH 的请求体按 spec 发字符串会被活体 400

### 事实

`/v3/api-docs` 里，**51 处** schema 同时写着 `"type": "string"` 和一个**对象或数组**的 `example`。
例（`GET /api/health` 的 200）：

```json
"schema": { "type": "string", "example": {"status": "ok", "timestamp": 1709000000000, "service": "claude-mem-java"} }
```

⇒ 文档在**同一处**断言了两件互斥的事：`type: string` 意味着 body 是一条 JSON 字符串，
而 `example` 是一个 JSON 对象。OpenAPI 规定 `example` 必须是 schema 类型的实例。

**最刺眼的一例是请求体** —— `PATCH /api/memory/observations/{id}`：

```json
"requestBody": { "required": true, "content": { "application/json": { "schema": {
  "type": "string",
  "example": {"title": "Updated title", "content": "Updated narrative", "concepts": ["how-it-works","architecture"]}
} } } }
```

处理器绑定的是 `Map<String, Object> body`（`MemoryController.java:304`）。活体实测（2026-10-21）：

| 请求体 | 结果 |
|--------|------|
| `{"title":"p383 probe"}`（对象，即 example 展示的那个） | **HTTP 200** `{"status":"updated","id":"938d781b-…"}`，回读 `title = 'p383 probe'` |
| `"{\"title\":\"p383 probe\"}"`（**JSON 字符串**，即 spec 宣告的那个） | **HTTP 400** `{"timestamp":…,"status":400,"error":"Bad Request","path":"/api/memory/observations/…"}` |

⇒ **按 spec 生成的客户端会发字符串，然后被端点拒绝**。这不是排版问题，是可复现的功能性断裂。

### 两套机制

| 机制 | 触发条件 | 产出 | 命中数 |
|------|----------|------|--------|
| **M1** | `@Schema(example = …)` **不写 `type`**（也不写 `implementation`） | springdoc 解析成 `type: string`，`example` 原样保留 ⇒ 与对象/数组 example 自相矛盾 | **51** |
| **M2** | `@ApiResponse(responseCode = "200")` **不写 `content`**，且方法返回 `ResponseEntity<Object>` | springdoc 依返回类型推成 `type: object`，而处理器实际返回 **JSON 数组** | **1**（`GET /api/extraction/{templateName}/history`） |

M2 的活体证据：`GET /api/extraction/user_preference/history?projectPath=/tmp` ⇒ **HTTP 200 `[]`**（数组），
而活体 spec 宣告 `{"type": "object"}`（200 / 400 / 500 三个都是）。

**M1 的 51 处**按形态分：**49** 个对象、**2** 个数组（`GET /api/timeline`、
`POST /api/sdk-sessions/batch`）。另有 **10 处 `example` 是标量**（`"bugfix"`、`"how-it-works"`、
SSE 的 `event: data:…`、`"true"`、`"code"`、`"1.0"`、`"Tracks code evolution"`、空体 `""`）
—— 这些 `type: string` **本就正确**，一个都没动。

**M2 的范围**：把全部 14 个控制器里「`@ApiResponse` 不带 `content`」的声明逐个列出（**13** 处），
只有 `ExtractionController.getExtractionHistory` 的三个是 `ResponseEntity<Object>` 且实际返回数组；
其余或是 `Map<String,Object>`（对象，正确）、或是 `String`（文本，正确）、或是
`PagedResponse<…>`（springdoc 能推准）。

### 机制怎么选：先探针，再批量

**没有一次改完 51 处**。先在**一次构建**里同时试了四种写法，看活体产出：

| 变体 | 写法 | 活体产出 | 判定 |
|------|------|----------|------|
| **A** | `@Schema(type = "object", example = "…")` | `{"type":"object","example":{…}}` | ✅ **type 对、example 也在** |
| **B** | `@Schema(implementation = Map.class, example = "…")` | `{"type":"string"}` | ❌ **example 被丢掉，type 仍是 string** |
| **C** | `@Schema(implementation = List.class, example = "…")` | `{"type":"string"}` | ❌ 同上 |
| **D/E** | `@ArraySchema(arraySchema = @Schema(example = "…"), schema = @Schema(implementation = Map.class))` | `{"type":"array","items":{"type":"string"}}` | ❌ **items 的 type 是假的**（元素其实是对象）且 example 丢失 |
| **F** | `@ArraySchema(arraySchema = @Schema(type = "array", example = "…"), schema = @Schema(type = "object"))` | `{"type":"array","items":{"type":"object"}}` | ✅ **合法数组 schema**（但 inline example 被舍弃） |

⇒ **B/C/D/E 四种都试过并被实测否掉**，只有 A 与 F 可用。**先探针再批量**在此直接省掉了一次
「改 51 处再发现机制错了」的返工。

**探针自身还出了一次错**：变体 C 的残留 `implementation = List.class` 忘了清，
于是 `GET /api/timeline` 在批量改完后**仍然是 `{"type":"string"}`** —— 而这**正是探针 B/C 早已
预测的失败模式**（`implementation` 会吃掉 type 并丢 example）。清掉残留后即恢复。

### 修法与取舍

- **49** 个对象形态：`@Schema(type = "object", example = …)`。
- **3** 个数组响应（`GET /api/timeline`、`POST /api/sdk-sessions/batch`、
  `GET /api/extraction/{templateName}/history`）：改用仓库既有的
  `@Content(array = @ArraySchema(arraySchema = @Schema(type = "array", example = …), schema = @Schema(type = "object")))`
  写法（`/api/memory/experiences`、`/api/mode/types/valid` 本来就是这么写的），
  产出 `{"type":"array","items":{"type":"object"}}`。
- **M2**：给 `getExtractionHistory` 的 200 补上同一个数组声明。

**取舍据实记录**：变体 F **舍弃了 3 个数组响应的 inline example**
（`GET /api/timeline`、`POST /api/sdk-sessions/batch` 原本有，
`GET /api/extraction/{templateName}/history` 原本就没有）。
理由：能留住数组 example 的注解写法**无法同时带上 `items`**，而 OpenAPI 3.0 规定
`type: array` **必须**有 `items` ⇒ 取**合法**的数组 schema。三个端点的示例在
`docs/API.md` / `docs/API-zh-CN.md` 里都有。

**前后对比（活体 `/v3/api-docs` 实测）**：

| 指标 | 改前 | 改后 |
|------|------|------|
| `type: string` 却带对象/数组 example 的 schema 数 | **51** | **0** |
| type 发生变化的 schema 数 | — | **50** |
| example **内容**发生变化的 schema 数 | — | **0** |
| example **丢失**的 schema 数 | — | **2**（两个数组响应，理由见上） |
| example **新增**的 schema 数 | — | **0** |
| 活体路径数 | 66 | 66 |

### 新增不变量测试

`backend/src/test/java/com/ablueforce/cortexce/controller/OpenApiExampleTypeContractTest.java`
（**1** 个用例，**不连数据库、不起 Spring 上下文**）：用
`ClassPathScanningCandidateComponentProvider` 扫出控制器包里全部 `@RestController`，
逐个方法收集**请求体**（方法级与参数级的 Swagger `@RequestBody`）与**响应体**
（`@ApiResponse` / `@ApiResponses`）里带 `example` 的 schema，断言
**example 的 JSON 形态 == 该 schema 实际宣告的 type**（`type` 为空时 springdoc 取 `string`）。

**阳性对照**：断言本轮实际扫到的 schema 数 **≥ 40** —— 没有这一条，一个坏掉的扫描器
会报「零违规」而测试**因为错误的原因通过**（这正是「跑通了 ≠ 看到了」的形态）。

**变异测试 1**（对象分支）：把 `IngestionController` 的 `@Schema(type = "object", …)` 去掉
⇒ 测试失败并**点名**：`IngestionController#handleToolUse — example is a JSON object but the
schema declares type "string"`。变异后按 sha256 **逐字节还原**。

**变异测试 2**（数组分支）：把 `ExtractionController` 的 `arraySchema = @Schema(type = "array", …)`
改成 `arraySchema = @Schema(example = …)` ⇒ 测试失败并**点名**：
`ExtractionController#getExtractionHistory — example is a JSON array but the schema declares
type "string"`。同样按 sha256 逐字节还原。

**扫描器自身的一处盲区已补**：最初的实现只读 `Content.schema()`，而**数组响应走
`Content.array()`**（`@ArraySchema`）⇒ 数组分支本会被整个漏掉。第二版同时读
`Content.array()` 的 `arraySchema()`（数组本身）与 `schema()`（元素），两个分支各有一条
变异测试证明能失败。

**测试数**：后端 **177 → 178**，全绿（`files=18 tests=178 failures=0 errors=0 skipped=0`）。

---

## P2-154: API 文档双语的端点/操作计数写「67」—— 活体是 71 个操作、66 条路径；同一份文档另一处却写「66 paths」

### 事实

`docs/API.md:2517`（「Error Codes → HTTP Status Codes」节）：

> Verified against a live backend (`GET /v3/api-docs`, **67 operations**) and against the source.

`docs/API-zh-CN.md:2504` 同句：「下表已对活体后端（`GET /v3/api-docs`，**67 个操作**）与源码逐条核对。」

另有两处在**文档结构说明**里：`docs/API.md:17`「The endpoint sets are verified identical
(**67 live endpoints**, checked against the `@*Mapping` annotations in the controllers)」、
`docs/API-zh-CN.md:11`「端点集合已核验完全一致（**67 个生效端点**，对照 Controller 的
`@*Mapping` 注解逐一比对）」。

活体实测（2026-10-21，37790，`/v3/api-docs`）：

| 量 | 实测 |
|----|------|
| 路径数 | **66** |
| 操作数（每个 `paths` 条目下的 `get`/`put`/`post`/`delete`/`patch` 键） | **71** |

⇒ **67 既不是路径数也不是操作数**。而**同一份文档的 2026-10-20 更新日志行**写的是
「已对活体 `/v3/api-docs` 核实：共 **66** 条路径」—— 两处数字互相打架。

### 定性：**事后漂移**

`git log -S "67 operations" -- docs/API.md` ⇒ `3d415a7 chore: sync project docs and pending work`
（**2026-10-06**）。把该 commit 的控制器取出来数 `@(Get|Post|Put|Patch|Delete)Mapping`：

| 版本 | 控制器里的映射注解数 | 活体操作数 |
|------|--------------------|------------|
| `3d415a7`（2026-10-06） | **67** | 67（当日） |
| `HEAD`（2026-10-21） | **71**（含 **1** 个注释掉的 ⇒ **70**？见下） | **71** |

⇒ 写下「67」的那天，后端**正好**有 67 个映射、活体**正好** 67 个操作 ⇒ **当时为真**，
此后端点增加、计数漂移 ⇒ **事后漂移**（不是写入即假）。

### 计数规则本身有一个坑

文档自己写明口径是「对照 Controller 的 `@*Mapping` 注解逐一比对」。实测：

- 控制器包内 `@(Get|Post|Put|Patch|Delete)Mapping` 共 **72** 处；
- 其中 **1** 处是**就地注释掉**的路由：`ViewerController.java:213-218` 的
  `//    @GetMapping("/concepts")`（该块上方写着「NOTE: Temporarily disabled」）；
- **72 − 1 = 71** = 活体操作数 ✓。

⇒ 计数规则要写明**排除那个被注释掉的路由**，否则下一个人照着数会得到 72。

### 另一半断言核实为真

同一句里的「两版端点集完全一致」：把 `docs/API.md` 与 `docs/API-zh-CN.md` 里所有
「方法 + 路径」token 程序化提取后求差集 ⇒ **EN 74 / ZH 74，双向差集为空** ⇒ **为真**。
两版所载端点**集合**没有分叉，**只有计数过时**。

### 同段其余断言全部核实为真

| 断言 | 实测 |
|------|------|
| `201` —— `HttpStatus.CREATED` 零出现 | `grep` 全后端 `HttpStatus.CREATED` = **0** ✓ |
| `401` —— 本 API 不做鉴权 | `HttpStatus.UNAUTHORIZED` = **0**，后端无任何鉴权代码 ✓ |
| `403` —— 应用不发；CORS 预检可能发 | `HttpStatus.FORBIDDEN` = **0** ✓ |
| `404` / `500` —— 有 | ✓ |
| `429` —— **只有** `POST /api/ingest/tool-use` | 活体 spec：`tool-use` 声明 429，其余三个 ingest 端点（`observation` / `user-prompt` / `session-end`）只有 200/400 ✓；双语两处说法一致 ✓ |
| `503` —— `HttpStatus.SERVICE_UNAVAILABLE` 零出现 | = **0** ✓ |
| 「没有全局 `@ControllerAdvice`」 | `grep ControllerAdvice|ExceptionHandler|ResponseEntityExceptionHandler` 全后端 = **0** ✓ |

### 修法

四处计数改为**带日期的实测值 + 取数方式 + 计数规则的例外**：

> **71 live operations across 66 paths, measured 2026-10-21** — count the
> `get`/`put`/`post`/`delete`/`patch` keys under each entry of `paths` in `/v3/api-docs`;
> the same 71 are the controllers' `@*Mapping` annotations, minus the route commented
> out in place at `ViewerController.java:214`

（中文版同义。）这样**不会第二次失实**：数字带着日期与取数方式，下一次端点增减时
读者知道该重数、该数什么、以及那个坑在哪。

双语各加 **2** 条 **2026-10-21** 更新日志行（一条记 OpenAPI 的 type 修正，一条记本条计数修正），
沿用本文件「只加日期行、不改写历史行」的体例。

---

## 压缩记录（第 383 轮）

findings 文件写入前：**1494 行 / 149328 字节**，对阈值（1500 行 / 150000 字节）
**余 6 行 / 672 字节** ⇒ 两条新条目根本写不下。本轮新增两个压缩杠杆。

### C6：归档链接转引用式

把**被引用 ≥2 次**的归档路径从行内写法改成引用式（`[显示名][aN]`，**显示名与链接目标都不变**，
只把目标集中到文末的定义块）：

- 被转换的归档 **28** 个，共 **65** 处行内引用；
- 只出现 **1** 次的 **26** 处**保持行内写法**（单独转反而多花 6 字节：定义块 52 字节 vs 行内 46 字节）；
- 零信息损失；**省 1750 字节**。

### C7：Status 行折行合并

本文件有 **38** 处多行 `- **Status**`（2~8 行），把每处的续行并回一行：
**136 行**因此让出，字节只省 **272**（只去掉换行与行首两格缩进）。

**字符流守恒**：去掉全部空白后，改前改后**逐字相同**（脚本断言为真）。

**与第 382 轮「折行重排不做」的区别**：382 轮的对象是**叙述正文**，且当时只找到 **2** 段合格；
本轮的对象是 **Status 元数据行**，**不动任何问题陈述或决策推理**。

### 硬证明（全部脚本断言为真）

| 断言 | 结果 |
|------|------|
| 条目标题序列 | **141 → 141，逐字相同** |
| 链接**显示名**（`…-N`）序列 | **136 → 136，逐字相同** |
| 链接**用法**数 | **207 → 207** |
| 链接**解析后**的目标多重集 | **完全相同** |
| 目标**集合** | **67 → 67，逐字相同** |
| 核心正文（定义块与链接语法抽象后） | **逐字相同** |
| 既有别名 `a1`~`a13` 及其目标 | **未变** |
| 41 个定义文件是否都存在 | **全部存在** |
| 26 处行内链接是否都可解析 | **全部可解析** |
| 有无未定义的引用 | **无** |

**校验脚本自身也错了两次**（都改正后才下结论）：

1. 第一次把「目标字符串多重集」当作不变量 ⇒ 报不等。其实**定义块每个归档只列一次**，
   而行内写法重复 N 次 ⇒ 路径字符串的重数**本就不该相等**。正确的不变量是
   **「把每个链接用法解析成它实际指向的文件后，多重集相等」** —— 重做后为真。
2. 第一次只认 `Content.schema()`、漏了 `Content.array()` ⇒ 数组分支**整个漏掉**。
   补上后，数组分支的变异测试才真的能失败。

### 终值

**1386 行 / 147578 字节**（余 **114 行 / 2422 字节**）。

---

## 本轮其余核实为真（未改一个字）

- **API 文档的钳制表逐条活体复验**：`/api/observations` `limit=0/-5/500/0x10` ⇒
  `1 / 1 / 100 / 16` 条（`0x` 前缀被静默采纳，文档已记）；`/api/sessions` `limit=0/-5/500`
  ⇒ `1 / 1 / 100`；`/api/context/recent` `limit` 钳制 **[1, 20]**；
  `/api/extraction/{templateName}/history` `limit=0/-5/500` ⇒ **200 空数组**（钳制在代码里，
  `ExtractionController.java` 内 `if (limit < 1) limit = 1; if (limit > 100) limit = 100;`）；
  `/api/logs` `lines=0/-5/50000` ⇒ `returnedLines = 1 / 1 / 10000`。
- **文档状态码与活体 spec 的逐端点对拍**：唯一「文档列了而 spec 没宣告」的是
  `GET /api/memory/quality-distribution` 的 400 —— **P2-135 已记**（对外契约变更，未修），
  本轮不重复立条。
- **请求体字段表 vs 活体 schema 属性**：`/api/ingest/observation` 看似少了
  `session_id` / `cwd` / `content` 三个别名 —— 核对后文档用**散文**写明
  「Field aliases: `session_id` … `cwd` … `content`」，**不处置**；
  `/api/observations/batch` 看似多了 `created_at_epoch` / `bogus_column` —— 那是同节
  观测 wire 格式表与说明散文的行，**不是请求体表**，属**校验脚本分段不当**。
- **PATCH 端点可更新字段**：实现恰好处理 `title` / `content`|`narrative` / `subtitle` /
  `source` / `facts` / `concepts` / `extractedData` **7** 个，与文档所列**逐字相符**。

---

## 验收

- **后端单测 178/178** 全绿（`177 → 178`，新增 1 个不变量测试）。
- **回归 45/0/1**（`Passed: 45 / Failed: 0 / Skipped: 1 / Total: 46`），**exit 0**。
- **Phase3 25/0/0**，**exit 0**。
- 两者都跑在**含本轮后端改动的新鲜实例 37790**（jar 09:53、pid 32562；
  `find backend/src -newer <jar>` **为空** ⇒ jar 比全部后端源文件都新）。
- 活体 `/v3/api-docs`：**66** 条路径、**71** 个操作。
- 指纹 `386fe87f…` → `db704ad391d3d5cfd4f623d6aa42435bddf14e4dd4716a337b175ac5ae079a90`（记录数 2224）。
- 全文逐字见 [`…-84`](../archive/2026-10-20_backend-review-evidence-84.md)。
