# 后端审查证据归档 -103（2026-10-22）

> 第 402 轮（Java SDK 代码方向 + SDK README 文档方向，doc round 301）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1338 行 / 149682 字节**）。
> 本文档体量：**169 行 / 15880 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。

## 本轮新写的两条（正文逐字保留在本归档）

### P2-203: Java SDK 的**出站请求体键集合**零断言 —— 十个带 body 的端点无一被钉住

- **Scope**: → [scope-9][a6]
- **Problem**: Java SDK 的请求体是**手写**的：6 个请求 DTO 各自一个 `toWireFormat()`
  手工 map（`SessionStartRequest` / `ObservationRequest` / `SessionEndRequest` /
  `UserPromptRequest` / `ExperienceRequest` / `ICLPromptRequest`），加客户端里 4 处
  裸 map / record（`submitFeedback` 的 `HashMap`、`updateSessionUserId` 的
  `Map.of("user_id", …)`、`updateObservation` 的 `ObservationUpdate` record、
  `getObservationsByIds` 的 `Map.of("ids", …)`）。
  而既有测试对请求体的断言**形态**只有一种：**包含某个值或某个键名** ——
  `contains("s1", "/proj", "Read")`、`contains("\"source\":\"manual\"")`、
  `contains("maxChars")`、`doesNotContain("maxChars")`、
  `contains("obs-uuid").contains("SUCCESS").doesNotContain("comment")`。
  全仓 **8** 处 `containsKey` 断言**全部落在响应上**（`startSession` / `getVersion` /
  `getProjects` / `getModes` / `getStats` / `search` 的回退体 / `getSettings` / 健康检查明细），
  **出站请求体的键集合一处都没有被断言**。
  ⇒ 在任意一个 `toWireFormat()` 里把键改名，或在后端 DTO 上加一个字段，wire 就变了，
  而套件**全绿**：既有断言只在找某个特定的值，键集换了照样找得到。
- **Status**: ✅ **已修（402 轮，Java SDK 侧，测试 + 注释；行为零改动）** —— 既有断言全是「包含某个值/某个键名」，**8 处 `containsKey` 全在响应上**；新增 `CortexMemWireFormatTest`（**4** 例）把十个端点的**完整键集合**一次性钉死，并把 README 发布的 wire 名与 SDK 实际的 `@JsonProperty` 绑定。另改正 `ObservationResponse` 类 Javadoc 两处失实：称「默认 Jackson SNAKE_CASE 命名策略」（**默认是 camelCase**；那五个字段恰好是单词故无后果，但**新加多词字段会踩坑**）、称用它反序列化「`GET /api/memory/observations/{id}`」（**该端点不存在**）与「search results」（**search 返回裸 `Map`**）。

### P2-204: Java SDK README 的跨家断言「Go, Python and Java all decode these the same way」**失实** —— 四家里只有 Java 不拆 `refined_from_ids`，且这句漏掉 JS

- **Scope**: → [scope-10][a7]
- **Problem**: `cortex-mem-spring-integration/README.md` 的 Wire Format 节写着
  「Go, Python and Java all decode these the same way; see the Go and Python SDK READMEs
  for the same table in their own idioms.」（中文版同义句：「Go、Python、Java 三家的
  解码方式一致」）。这句话两处不成立：
  ① **对 `refinedFromIds` 不成立** —— 四家把它暴露成的东西**根本不同**：
  Go 是 `type StringList []string`（带自定义 `UnmarshalJSON`，逗号串会拆），
  Python 是 `refined_from_ids: list[str]`（`_to_str_list` 拆），
  JS 是 `refinedFromIds?: string[]`（`safeStringOrStringList` 拆），
  **只有 Java 是 `String refinedFromIds`**（`mapToObservationResponse` 里用的是 `str()`
  而不是同一文件里那个会拆逗号串的 `strList()`）。
  ② **它把读者指向的那张表，恰恰写着相反的形态** —— 按它去查 Go / Python 的 README，
  看到的是**列表**。
  另外这句只点了三家，**漏了 JS** —— 而另外三份 README 都把四家当同一族来写。
- **Status**: ✅ **已修（402 轮，文档方向，双语各一处）** —— `refinedFromIds` 条目改为「**这是四家唯一不一致的一处**：Go / Python / JS 会拆成 ID 列表（`[]string` / `list[str]` / `string[]`），从那三家移植过来的代码在这里必须自己拆」；结尾跨家句子改为「四个 JSONB 列表列在四家里解码一致……`refinedFromIds` 是唯一例外」。⏸ **行为本身不改**：`String` 改 `List<String>` 是破坏性对外 API 变更，留待项目决策。

## 按 C10 从工作文件迁入的三条（逐字保留在本归档）

工作文件侧各留标题 + 一行 Status（指针为 `…-103`）。

### P2-196

### P2-196: JS SDK 的出站 wire 字段名：十个带 body 的端点，**请求体的键集合**无一被断言 —— 而 README 把这些名字当契约发布

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-100`][a58]（10 个带 body 的端点的实测键集合与后端 `@JsonProperty` 的逐条对拍、既有 **20** 处 body 断言（**14** 处 `JSON.parse(opts.body)` + **6** 处 `String(opts.body)`）的逐处归属、README 发布名与实测名的差集、README 对 session-end **零发布**这一遗漏、变异复验 M0~M5 的逐条读数）
- **Status**: ✅ **已修（399 轮，JS SDK 侧，只加测试 + 改文档，SDK 主源码零改动）** —— 新增 `src/__tests__/wire-format.test.ts`（**15** 例），逐端点钉住实际 JSON 键集合，并把 README 的「Wire Format / Wire 格式」段与实测键集合**双向**绑定。

### P2-197

### P2-197: `content` 与 `narrative` 是同一个后端字段的两个名字，而 **JS 是四家里唯一不检测这个冲突的 SDK** —— 它的双语 README **全篇 0 次**提及这两个字段

- **Scope**: → [scope-10][a7]
- **Evidence**: → 归档 [`…-100`][a58]（三段活体实测与查库读回、另三家的拒绝点行号、JS README 两语的 0 次提及计数、本机 DeepSeek 凭据失效导致工具使用路径静默丢行的排查与独立复验）
- **Status**: ✅ **已修（399 轮，文档方向，两语各一段）+ 钉进测试**；Go / Java 两家 README 的同一处遗漏**只记录不实施**（属它们各自轮次的文档方向）。

### P2-200

### P2-200: Java demo README 的健康端点账目写成「**七条**」—— 而同一段自己列的是 2 真 + 6 静态 = **八条**，表里也是八行

- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-101`][a59]（demo 全部 **8** 个健康类端点的代码与文档逐条对账、README 表行与代码推导集的**三方相等**、新测试 `DemoHealthAccountingTest` 的 **3** 个用例、变异 M0~M4 的逐条读数，以及新测试自身两处判据错）
- **Status**: ✅ **已修（400 轮，Demo 侧，测试 + 文档；demo 实现零改动）** —— README 标题的「seven」改为「eight」，并补一段点明「八个里只有**七个**路径带 health 字样」；新增 `DemoHealthAccountingTest`（**3** 例）—— 代码推导集（按 handler 名 `health` / `getMemoryHealth` 读注解）＝ README 表行 ＝ 手写声明集，**三方相等**；标题里的数字必须等于服务数；标着「Static beacon」的行必须恰好是那六个静态端点。**变异 M1 把标题改回「seven」⇒ 恰好 1 红**（当年那个错就是它）。

## P2-203 的证据：十个出站请求体的键集合

十个端点逐条把 SDK 实际发出去的键集与后端 `ApiRequests` 的 `@JsonProperty` 对拍：

| 端点 | SDK 发出的键 | 后端 DTO 声明的键 | 判定 |
|------|-------------|------------------|------|
| `POST /api/session/start` | `session_id` `project_path` `user_id` | `SessionStartRequest`：同上 + `cwd` `projects` `is_worktree` `parent_project` | SDK 是子集 ✓ |
| `POST /api/ingest/tool-use` | `session_id` `tool_name` `cwd` `tool_input` `tool_response` `prompt_number` `source` `extractedData` | `ToolUseRequest`：同上 + `platform_source` | 一致 ✓（后端对 `source` / `extractedData` / `prompt_number` 是**接受但丢弃**，SDK 的 Javadoc 也这么写） |
| `POST /api/ingest/session-end` | `session_id` `cwd` `last_assistant_message` | `SessionEndRequest`：`session_id` `cwd` | SDK 多发一个被忽略的字段 ✓ |
| `POST /api/ingest/user-prompt` | `session_id` `prompt_text` `cwd` `prompt_number` | `UserPromptRequest`：完全相同 | 一致 ✓ |
| `POST /api/memory/experiences` | `task` `project` `count` `source` `requiredConcepts` `userId` | `ExperienceRequest`：完全相同 | 一致 ✓ |
| `POST /api/memory/icl-prompt` | `task` `project` `maxChars` `userId` | `ICLPromptRequest`：完全相同 | 一致 ✓ |
| `POST /api/memory/feedback` | `observationId` `feedbackType` `comment` | `FeedbackRequest`：完全相同 | 一致 ✓ |
| `PATCH /api/memory/observations/{id}` | `title` `subtitle` `content` `narrative` `facts` `concepts` `source` `extractedData` | `MemoryController` 读 `title` `content`/`narrative` `subtitle` `source` `facts` `concepts` `extractedData` | 一致 ✓（`content` 与 `narrative` 互斥，builder 会拒绝同时设） |
| `PATCH /api/session/{sessionId}/user` | `user_id` | `SessionUserUpdateRequest`：`@JsonProperty("user_id")` | 一致 ✓ |
| `POST /api/observations/batch` | `ids` | `BatchObservationsRequest`：`ids` + `project` `orderBy` `limit` | SDK 是子集 ✓ |

⇒ **十个端点全部吻合** —— 逐条对拍的结果是「**当前没有功能缺陷**」。
⇒ 本条记的是**覆盖缺口**（键集零断言），不是行为缺陷；这与第 399 轮在 JS SDK 上
查到的是同一类（那边是「请求体的键集合无一被断言」）。

### 顺带改正的两处类 Javadoc 失实（`ObservationResponse`）

| 原文 | 实情 |
|------|------|
| 「Other fields use the default Jackson SNAKE_CASE naming strategy」 | **Jackson 的默认是 camelCase，不是 snake_case**。那五个未标注的字段（`id` `type` `title` `subtitle` `source`）恰好都是单词，两种策略下同名 ⇒ **当下无后果**；但**新加一个多词字段**时，若照这句注释理解就会以为它自动变成 snake_case，实际不会 —— 且客户端是自己按**字面键**从原始 map 里取值的（`raw.get("id")` 等），根本不经过任何命名策略 |
| 「This DTO is used by the client to deserialize observation responses from `GET /api/observations`, `GET /api/memory/observations/{id}`, and search results」 | **`GET /api/memory/observations/{id}` 这个端点不存在** —— 活体 spec 里 `/api/memory/observations/{id}` 只有 `patch` 与 `delete`；客户端的单条查询 `getObservation(id)` 包的是 `POST /api/observations/batch`。而 **search 返回的是裸 `Map<String,Object>`**，不经过这个 DTO |

## P2-204 的证据：四家 SDK 的 `refined_from_ids`

| SDK | 暴露的类型 | 是否拆分 | 代码坐标 |
|-----|-----------|---------|---------|
| Go | `StringList`（`[]string`） | ✅ 拆 | `dto/observation.go:26`（`type StringList []string`）+ 自定义 `UnmarshalJSON` |
| Python | `list[str]` | ✅ 拆 | `cortex_mem/dto.py:570`、`:671`（`_to_str_list(...)`） |
| JS | `string[]` | ✅ 拆 | `src/dto/observation.ts:146`、`:185`（`safeStringOrStringList(...)`） |
| **Java** | **`String`** | ❌ **不拆** | `ObservationResponse.java`（`@JsonProperty("refined_from_ids") String refinedFromIds`）＋ `CortexMemClientImpl.mapToObservationResponse` 里用的是 `str()` 而非同文件那个会拆逗号串的 `strList()` |

⇒ **四家里只有 Java 不拆**。而 Java 的 README 恰恰声称三家解码方式一致。

## 两个方向的负控（都做过，都证明「这条断言有牙」）

| 负控 | 手法 | 读数 |
|------|------|------|
| **出站键集侧** | 把 `SessionStartRequest.toWireFormat()` 的 `map.put("project_path", …)` 临时改成 `"projectPath"` | **恰好 1 红**，红在 `everyOutboundBodyHasExactlyThePublishedKeySet`，diff 精确指出 `POST /api/session/start` 的键集从 `["user_id","session_id","project_path"]` 变成 `["user_id","session_id","projectPath"]`，其余 **9** 个端点与另外 **3** 个用例保持绿 |
| **文档侧** | 把 README 里 `refinedFromIds` 条目中的「Go, Python and JS split the」临时改成「the other three split the」 | **恰好 1 红**，红在 `refinedFromIdsBulletNamesTheSdkFamiliesThatSplitIt`，报错「README.md must name the SDKs that expose refined_from_ids as a list」；另外 **3** 个用例保持绿 |

⇒ 两次负控后均**按字节还原**（`md5` 比对：`SessionStartRequest.java` = `fe02228237ad362d4149c202a5d74678`；
`README.md` = `c6de43c511fdc8ab14e3a4facd28042f`；`git diff` 对这两个文件为空）。

## 本轮探针/判据自身的四处错（全部在写成发现之前拦下）

1. **第一版 `@JsonProperty` 读错了位置** —— 用 `RecordComponent.getAnnotation()` 取，
   而 `@JsonProperty` **不是** `@RecordComponent` 目标 ⇒ 永远取不到 ⇒ 六个已发布的名字
   全部比对失败。**正解**：Jackson 读的是 **accessor 方法**上的注解 ⇒
   `ObservationResponse.class.getMethod(name).getAnnotation(JsonProperty.class)`。
2. **Mock 响应漏了 Content-Type** ⇒ `UnknownContentType: no suitable HttpMessageConverter
   found for response type Map<String, String> and content type [application/octet-stream]`。
   既有测试的写法是 `.addHeader("Content-Type", "application/json")`。
3. **Wire Format 段的标题是本地化的** —— 中文版是 `## Wire 格式` 而不是 `## Wire Format`
   ⇒ 解析器只认英文时中文版整段取不到。**解析器要同时认两种标题。**
4. **两处正则/转述把注释里的词当成了键名** —— ①`ICLPromptRequest.toWireFormat()` 的键被读成
   `unset`（那是**注释**里的 "treated as unset"）；②后端 `ApiRequests` 的首版正则**漏掉了尾部字段**
   （`userId` / `comment` / `prompt_number`），一度把三处「对不齐」误判成缺陷。
   ⇒ **键名必须从字面量里取，注释里的词不算数；正则抽字段必须回读原文件核对。**

## 验收

| 项目 | 读数 |
|------|------|
| Java SDK 单测 | **313 / 313**（surefire `tests=` 逐类求和：client **260** ＋ spring-ai **46** ＋ starter **7**；改前为 **309** ⇒ 本轮 **+4**） |
| 新测试单独跑 | `mvn -o test -pl cortex-mem-client -Dtest=CortexMemWireFormatTest` ⇒ **4 / 4** |
| 负控（出站键集侧） | **1 红** / 3 绿 ⇒ 断言有牙 |
| 负控（文档侧） | **1 红** / 3 绿 ⇒ 断言有牙 |
| 回归 | **46 / 0 / 1**，exit 0，跑在新鲜 37790（pid **55057**） |
| Phase3 验收 | **25 / 0 / 0**，exit 0，同一实例 |
| 新鲜度闸门 | `find backend/src -newer <jar>` 为空（后端源码未动，jar 仍是 10-22 06:20 那份） |
| 指纹 | `b9910eb2…` → `e2955606…`，记录数 2241 → **2242** |

## 压缩记账

| 口径 | 数值 |
|------|------|
| 工作文件侧**迁出**（P2-196 / P2-197 / P2-200 三条的 `- **Scope**` ＋ `- **Evidence**`，按 C10 逐字迁入本归档） | 净减 **866** 字节（实测：150548 → 149682） |
| 工作文件**写入前**余量 | **1472** 字节 |
| 写入 P2-203 ＋ P2-204 ＋ 链接定义后 | **150548** 字节 ⇒ **超支 548** |
| 工作文件**写入后**余量 | **318** 字节 |
| 工作文件**净变化** | **1331 行 / 148528 字节** → **1338 行 / 149682 字节**（**+7 行 / +1154 字节**） |

⇒ 本轮**未改阈值**：余量虽只剩 **318** 字节，但 C10 这次还有余量可压；
⇒ **P2-115 仍是待用户决策的流程契约变更**（建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，
只同步 `docs/drafts/cron-combined-task.md:14`）。

> ⇒ 与归档 `-93` 的记账纪律一致：**迁出量、写入量、净变化是三个数，不可混用**。
