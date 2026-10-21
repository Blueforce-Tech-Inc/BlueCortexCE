# 后端审查证据归档 -94（2026-10-21）

> 第 393 轮（JS/TS SDK + SDK README，doc round 292）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1240 行 / 146043 字节**）。
> 本文档体量：**215 行 / 22268 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。

## 本轮新写的两条（正文逐字保留在本归档）


### P2-181: JS SDK 的 **32** 处客户端必填参数守卫里 **16** 处**零测试钉住** —— README 发布的 18 行「必填参数」表有 **8** 行**完全没有任何守卫覆盖**

- **Scope**: → [scope-9][a6]
- **Status**: ✅ **已修（393 轮，JS 侧，只加测试 + 改测试，SDK 主源码零改动）** —— 新增 `src/__tests__/required-arguments.test.ts`（**97** 例）+ 密封 `client.test.ts` 里唯一未注入 mock 的那一个构造；JS SDK **273 → 370**。

- **Problem**：JS README 发布了一张 18 行的「必填参数」表，并用一句话把整张表绑成契约 ——
  「Every argument below must be non-empty. The SDK throws a `ValidationError` and sends no request.」
  这张表是**发布出去的契约**，但**没有任何东西把它和代码连起来**。

- **逐守卫变异扫描（不是读码推断）**：`src/client.ts` 共有 **32** 处客户端守卫
  （`validateRequired` 调用点 **27** 处 + `getObservationsByIds` 的 3 条 + `updateObservation` 的空更新 1 条
  + `getExtractionHistory` 的 `limit` 1 条）。逐个**单独**抽掉（每次只动一行、跑全量、看红绿、
  立刻按 sha256 还原），结果：
  - **16** 处抽掉后**全量仍然全绿** ⇒ 这 16 处守卫**零测试钉住**；
  - 另 **16** 处抽掉后至少 1 个用例红 ⇒ 已钉住。
  ⇒ **32 处守卫里恰好一半没有任何测试覆盖**。

- **按 README 那张表逐行看**：
  - **完全没有任何守卫覆盖**的有 **8** 行：`retrieveExperiences` / `getObservation` /
    `triggerRefinement` / `submitFeedback` / `deleteObservation` / `getQualityDistribution` /
    `triggerExtraction` / `getLatestExtraction`；
  - **至少有一处没被钉住**的有 **13** 行（上面 8 行 + `startSession` / `updateSessionUserId` /
    `recordUserPrompt` / `updateObservation` / `getExtractionHistory`）；
  - **每一行都被钉住**的只有 **5** 行：`recordObservation` / `recordSessionEnd` /
    `buildICLPrompt` / `search` / `getObservationsByIds`。

- **探针自身的一处错（必须先识别再采信）**：第一版用「用例正文里出现 `ValidationError` 字样」
  来统计守卫覆盖，得到「18 行里只有 2 行有断言」——那是**启发式判据不完整**（既有用例断言的是
  消息子串，如 `rejects.toThrow('session_id')`，正文里**不出现**该字样）。改用**逐守卫变异**后，
  真实数字是 **16** 处未钉住，而不是 24 处。⇒ **静态判据不能替代变异实测**。

- **第二处缺口：全仓唯一没注入 mock 的构造**。`client.test.ts` 里 110 个
  `new CortexMemClient(` 构造中，**109** 个注入了 mock fetch，只有 `client.test.ts:2635`
  那一个用的是**默认 `baseURL`** —— `http://127.0.0.1:37777`，**那是常驻后端**。
  把 `validateRequired` 改成空操作后，该用例**真的向 37777 发了一次请求**，
  拿回一个真实的 `APIError: cortex-ce: API error 400: Missi…`（`mem_sessions` 行数未变 ——
  后端以 400 拒了空 `session_id`，但**往返是真的**）。
  ⇒ 与 **P2-179**（Python）/ **P2-158**（Go）同族：守卫用例没密封 ⇒ 守卫回归就会打实况。

- **修法（只加测试 + 改测试，SDK 主源码零改动）**：
  - 新增 `src/__tests__/required-arguments.test.ts`（**97** 个运行时用例）：
    - **32** 条守卫用例逐条断言 `err instanceof ValidationError` + `err.field` **逐字相等** +
      `err.message` **逐字等于** `cortex-ce: validation error on <field>: …` + **不发请求**；
    - **18** 个**阳性对照**：同一个方法在参数齐备时**确实发出且只发出一个请求**
      ⇒ 「不发请求」这条断言不会因为「这方法压根不发请求」而**空转成立**；
    - README↔运行时**双向**一致性：解析 EN 与 ZH 两张表，与守卫集**逐项相等**；
      外加「18 行 + 7 个无参方法 == 25 个 API 方法」的**暴露面**断言；
    - README 发布的空更新消息与**运行时活调用**返回值**逐字相等**（不是与常量比对）。
  - 把 `client.test.ts:2635` 改为注入一个「一旦被调用就 reject」的 fetch 间谍 + 断言
    `not.toHaveBeenCalled()` ⇒ 该用例**被密封**。

- **变异验证**：把**原先 16 处未钉住的守卫**逐个单独抽掉，**每一个都恰好命中它自己的那条新用例**。
  另给**文档校验器**注入三个坏样本：删掉 EN 表的一行（**3** 红）、把 ZH 表的 `req.project`
  改成 `req.proj`（**1** 红）、把 EN 发布的空更新消息截断（**1** 红）⇒ **校验器能失败**。
- **阴性对照**：纯还原后全量 **370/370** 全绿。
- `src/client.ts` 全程**未改**（sha256 `194520e8b10325649dd3614c00826a770b7619682437398a29162cf008343c7a`
  逐字节一致）。

### P2-182: JS SDK 双语 README 的「必填参数」表**从未与代码绑定**，且 `limit` 负值的**四家分歧**在 JS 侧**没有记载** —— Go README 早已写明这张对照表

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Status**: ✅ **已修（393 轮，文档方向，双语）** —— EN 与 ZH 各补一段 + 一张四家对照表，并加 **6** 个用例钉住（JS 侧行为 4 + 双语互不漂移 2）。

- **Problem ①：发布出去的表与代码之间零绑定**。README 的 18 行表是**契约**，
  但**没有任何东西**把它和代码连起来：SDK 里改一个字段名、增删一处守卫、换一个方法名，
  表都**不会**变红；而反过来，表里写错一个字段名也不会有人发现。
  ⇒ 与 **P2-181** 同源但不同轴：P2-181 是「**代码**的守卫没被钉住」，本条是「**文档**的表没被钉住」。

- **Problem ②：`limit` 负值的四家分歧在 JS README 里没有记载**。表里 `getExtractionHistory` 一行写着
  「`limit` must not be negative」，但同一 SDK 的 `search` 与 `listObservations` 是**静默丢弃** ——
  两者都用 `limit > 0` 拼查询。而 **Go README 早已写明这张四家对照表**，
  **JS README 没有** ⇒ 照 Go 的表抄进 JS 是可行的，但必须自己核一遍。
  ⚠️ **照抄会抄错**：Go 那张表写「Java **throws** `IllegalArgumentException`」——
  这**只对 `SearchRequest.Builder.limit()` 成立**；Java 的**规范 record 构造器并不校验 `limit`**，
  而 `CortexMemClientImpl.search` 也是 `limit > 0` 才发 ⇒ 走构造器时 Java 与 Go / JS 一样**静默丢弃**。

- **四家实测（全部读源码，不靠互相转述）**：

  | SDK | `search` / `listObservations` 收到 `limit < 0` | 依据 |
  |---|---|---|
  | Java | `Builder.limit()` **抛** `IllegalArgumentException`；规范构造器**不校验**，客户端静默丢弃 | `dto/SearchRequest.java` 的 `Builder.limit()`；`CortexMemClientImpl.java` 的 `if (request.limit() != null && request.limit() > 0)` |
  | Go | 静默丢弃 → 走后端默认页 | `client_methods.go:124` 与 `:145` 的 `if req.Limit > 0` |
  | JS | 静默丢弃 → 走后端默认页 | `client.ts` 的 `buildSearchParams` 与 `listObservations`：`req.limit > 0` |
  | Python | 原样发出 → 后端钳到 1 | `client.py` 的 `if limit:`（负数在 Python 里为真） |

  后端侧：`/api/search` 与 `/api/observations` 都是 `@RequestParam(defaultValue = "20") int limit`
  + `Math.min(Math.max(1, limit), MAX_PAGE_SIZE)`（**100**）⇒ 收到负数会被**钳成 1**，
  收不到则用默认的 **20**。

- **修法**：EN 与 ZH 各补一段，说明「表里那条 `limit` 说明只适用于一个方法」，
  附上上面那张四家对照表（并把 Go 那张表里 Java 一行的**不精确**之处写准），
  再加一句「从 Java 移植过来的调用方会悄无声息地丢掉这道检查」。
  配套 **6** 个用例：**4** 个钉住 JS 侧行为（wire 级 —— `search` / `listObservations` 的
  负 limit **不进 URL**、正 limit 进 URL、`getExtractionHistory` 的负 limit **抛错且不发请求**），
  **2** 个钉住 EN 与 ZH 两张对照表**互不漂移**。

## P2-159 的更正记录（追加式：不改写历史正文）

- **追加（393 轮）**：本条记的「**Go / JS 各 2 处结转**」里的 **JS 部分是陈旧记录** ——
  JS 侧那 2 处其实**早在第 387 轮就已修完**，与本条同一批的 commit `6548428`
  （`patrol: round 387 - pin the JS capture and retry contracts and correct two false cross-SDK claims`）
  同一 commit 内把 JS 英文版的「enforce exactly the same set」改成
  「require exactly the same set of arguments — with one difference」，
  ZH 侧同批改为「要求的**参数集合**完全相同」。
  ⇒ **P2-159 至此四家全部结转完成**（Python 386 轮 / JS 387 轮 / Go 391 轮）。
  ⭐ 这条是「**追加式文档/审计链上，写之前先确认那句话描述的是「当前状态」**」的又一例：
  本条的 Status 写「Go / JS 各 2 处结转」时，**JS 那部分在写下当天就已经是假的了**。

## 压缩记录：12 条已结案条目的正文整体迁入本归档

本轮 findings 写入前只剩 **222 字节**余量（阈值 150000 字节 / 1500 行）⇒ 必须先腾空间。
C8 对 ⏸ 待决条目的**证据段**已在前几轮迁完（剩下的是必须留在工作文件的决策段），
C6 已枯竭 ⇒ 本轮改用文件自己写下的那条流程：「After resolution, append the verification
result and commit identifier here before **moving the detailed entry to an archive**」。
下面 **12** 条都是**已结案**条目（`✅`），其**正文整体迁入本归档**，
工作文件侧只留**标题行** + 一行 `- **Status**`（含本归档指针）。
**每条工作文件侧 Status 里的每个 token 都逐字存在于本归档**（378 轮那条可断言化的规则）。

### P2-146: 四家 SDK 的 README 都写「空 `tool_name` 会被后端以 400 拒绝、SDK 记一条日志后吞掉」—— 而客户端校验早在请求发出**之前**就拦下了它，请求数实测为 **0**

- **Status**: ✅ 已修（380 轮，Python 侧，文档 + 2 个回归测试）—— EN/ZH 双语 README 恢复反事实措辞并写明「客户端拦下、不发请求」；两版首屏测试数 **453 → 455**；新增 2 个测试钉住「校验发生在请求之前」；**Go / JS / Java 三家同型结转**（8 份 README）。**JS 侧 381 轮结转完成**（双语 README 同型句已改为反事实措辞 + 首屏计数 264 → 266，另补测试缺口 P2-147）。**Java 侧 384 轮结转完成**（双语 README 同型句改为反事实措辞 + 写明「客户端抛 `IllegalArgumentException`、一个请求也不发」；另补 3 个「请求数 0」用例钉住「校验发生在请求之前」）。**Go 侧 385 轮结转完成**（双语 README 同型句改为反事实措辞 + 写明「SDK 返回 `ValidationError`、一个请求也不发」；并补一条 **Go 独有**的细节：fire-and-forget 只吞**后端**错误，客户端校验错误仍**返回给调用方**）。**四家至此全部结转完成**。。全文逐字见 [`…-81`](../archive/2026-10-20_backend-review-evidence-81.md)；Go 侧见 [`…-86`][a44]。
### P2-153: 活体 OpenAPI 有 **51** 个 schema 宣告 `type: string` 却带对象/数组 example —— 自相矛盾；PATCH 的请求体按 spec 发字符串活体 400

- **Status**: ✅ 已修（383 轮，Backend 代码方向）—— **两套机制**：M1 `@Schema(example=…)` 不写 `type` ⇒ 解析成 `string`（**51** 处）；M2 `@ApiResponse` 不写 `content` + `ResponseEntity<Object>` ⇒ 推成 `object` 而实际是数组。机制经**四种写法探针**选定（`implementation=` 会吃掉 example 且 type 仍是 string，被实测否掉）；3 个数组响应另带 `items`（**49** 个对象的 example 逐字未变）。新增不变量测试（反射扫全部控制器，**178** 个测试），**两次变异测试**各证明其能失败。全文逐字见 [`…-84`][a42]。
### P2-157: Java SDK 双语 README 的 Design Notes **自相矛盾** —— 同一节里相邻两条，把 5 个静默吞错的方法分别写成「propagate」与「swallow」

- **Status**: ✅ 已修（384 轮，文档方向，零可执行行）—— **写入即假**（`1fa843e`(2026-10-02) 写下时实现已是 `executeWithRetrySilent`；`20e3661`(10-03) 又补入正确那条 ⇒ 文档内自相矛盾）；两处改为「5 个传播 + 5 个静默是例外」，并修正 `:824` 的「never throw」。全文逐字见 [`…-85`][a43]。**同型已在 Go 侧随 P2-146 结转时一并修掉**：Go README 的「Required Arguments」段本就写着「sends no request」，而隐患段却说请求会出去并被 400 拒绝 ⇒ **同一文档内自相矛盾**；**四家 README 都有这一形状**。
### P2-163: 四家 SDK README 的「空更新错误消息**完全相同**」—— **8 处全失实**：四家的**错误类型与前缀各不相同**，只有消息**主体**逐字相同

- **⏸ 仍待结转**: **Java 侧 2 处**。

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Evidence**: → 归档 [`…-88`][a46]（按断言清扫的 8 处定位表、四家实际错误类型与完整消息、规则那一半四家都成立的程序化对拍、`git log -S` 判定「写入即假」的提交证据）；本条的「修法 / 根因 / 后果」正文已逐字迁入 [`…-91`][a49]
- **Status**: ✅ **已修（387 轮，JS 侧）** —— Go / Python / Java 侧 6 处**结转**。 —— **追加（391 轮）：Go 侧 2 处已修**（类型改写 `*dto.ObservationUpdateValidationError` + 补四家对拍表；另 2 处表格行漏前缀见 **P2-178**）。 —— **追加（392 轮）：Python 侧 2 处已修**（双语改为「规则四家相同、消息主体逐字相同、完整字符串不同」+ 补四家对拍表；另 2 处截断引用见 **P2-180**）。
### P2-164: JS SDK 双语 README 说「另三家统一称项目参数为 `project_path`」—— **两种读法下都失实**：四家捕获方法的 **wire 字段都是 `cwd`**，SDK 层叫法是 Go `ProjectPath` / Python `project_path` / Java `projectPath`


- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-88`][a46]（四家 wire 字段的程序化对拍表、Java 侧「`toWireFormat()` 而非 Jackson 序列化」的关键辨析 —— 差点误判成「捕获静默丢项目」、两种读法各自的失实点、「throughout」的更强失实）
- **修法（本轮已修，JS 双语 4 处）**: 改成「四家在捕获方法上发的 **wire 字段都是 `cwd`**；差别只在 **SDK 层的叫法**」；并顺带去掉表格前后重复的同一段叙述。
- **Status**: ✅ **已修（387 轮）** —— 与 P2-159 同源于 `6902968`（2026-10-03），属**写入即假**。
### P2-165: JS 侧空更新的**完整消息与错误类型**零测试钉住 —— 文档写死了那句话，测试却只做子串匹配


- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-88`][a46]（两个旧用例只做子串匹配的定位、两处缺口证明的读数、新用例的四项断言、两次变异的归因分离）
- **修法（本轮已修，JS 侧，只加测试）**: 新增 **1** 个用例，断言 `err instanceof ValidationError` + `err.field === 'update'` + `err.message` **逐字等于** README 写的那串 + 不发请求。变异 1（截短消息）⇒ 恰好 **1** 红且**两个旧用例仍绿**；变异 2（换成 `APIError`）⇒ 恰好 **1** 红。
- **Status**: ✅ **已修（387 轮）** —— ⭐ 这是同族的**第四种形态**：前三族是「某个方法没测」，这一族是「**某个字段**没被钉住」。
### P2-166: JS SDK 的「**只有捕获路径重试**」这条契约**零测试钉住**，而 JS README 也**从未说明**它 —— 而 Java 侧的重试范围**大得多**（10 个方法 vs 3 个）


- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-88`][a46]（四家重试矩阵与逐个实现坐标、JS 侧重试只存在于 `doFireAndForget` 的机制说明、缺口证明、3 个新用例含阳性对照、两次变异、Java README 那句本身也不完整的附带发现）；本条的「修法 / 根因 / 后果」正文已逐字迁入 [`…-91`][a49]
- **Status**: ✅ **已修（387 轮）** —— 与 P2-161 **不是重复立条**：P2-161 是**吞错**轴，本条是**重试**轴；与 P2-113（失败默认可见性）也不同。
### P2-167: Java demo 的 **44** 个端点暴露面**零测试钉住** —— 12 个 Controller、44 条 `(verb, path)`，此前**一个都没被断言过**


- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-89`][a47]（12 个 Controller 的端点清单、44 条的完整集合、测试文件的覆盖盘点、三次变异的红绿计数与还原校验）
- **修法（本轮已修，Demo 侧，只加测试，实现零改动）**: 新增 `DemoEndpointSurfaceTest`（**3** 个用例）—— ① 断言 **44** 条 `(verb, path)` 的**集合相等**（多一条或少一条都红，不是包含关系）；② 断言无重复的 `(verb, path)`；③ 钉住「哪些 Controller 有 `/health` 兄弟端点」。
- **Status**: ✅ **已修（388 轮）** —— demo 测试 **40 → 50**（暴露面 3 + 静态 health 7），全绿。
### P2-168: demo 里 **6** 个静态 `/demo/*/health` 端点**恒返 200 `status:"ok"`** —— 后端完全宕机时仍报「健康」；与唯一的真健康检查 `/memory/health` **字段同名、语义相反**


- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-89`][a47]（2×2 活体对照矩阵：后端死/活各打 7 个 health 端点、逐字节相同的读数、第 183 轮那次修复的 Javadoc 原文、6 个静态端点的实现坐标）；本条的「修法 / 根因 / 后果」正文已逐字迁入 [`…-91`][a49]
- **Status**: ✅ **已修（388 轮）** —— ⭐ 这是「**存活信号不能被别的东西代理**」这一族的**第三个实例**，方向与第 183 轮那次相反（那次是**假绿**，这 6 个是**恒绿**）。
### P2-170: 两个 demo e2e 脚本的 `check_demo` 用**后端**健康当 **demo** 存活的代理 ⇒ 后端宕机时误报「demo 没在跑」并**跳过整个套件** ⇒ 后端故障被伪装成「demo 没启动」


- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-89`][a47]（活体读数：后端死时 `/actuator/health` 返 503 而 `/demo/observations/status` 返 200、修正前后脚本在同一条命令下的两种结局、两次变异的对照）；本条的「修法 / 根因 / 后果」正文已逐字迁入 [`…-91`][a49]
- **Status**: ✅ **已修（388 轮）** —— ⭐ 与 **P2-168** 同族但**方向相反**（那是**假绿**，这是**假红**）；⭐ `scripts/run-all-e2e.sh` **不跑**这两个脚本 ⇒ 影响面是 `docs/TESTING.md`、Java demo README 与 `scripts/README.md` 三处把它当「会通过」的流程推荐。
### P2-171: 后端的**端点暴露面**（**71** 个操作 / 66 条路径）**零测试钉住** —— 16 个后端测试类里**一个 `"/api/..."` 字面量都没有**


- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-90`][a48]（71 条 `(verb, path)` 完整集合、活体 OpenAPI 与反射扫描对拍、两次变异）
- **修法（本轮已修，Backend 代码方向，只加测试）**: 新增 `BackendEndpointSurfaceTest`（**3** 个用例）—— 71 条**集合相等**（多一条少一条都红）、无重复、扫描**非空跑**。反射读注解，可离线跑。
- **Status**: ✅ **已修（389 轮）** —— 后端单测 **178 → 181** 全绿；变异 A（`/api/stats` → `/api/stats-extra`）与变异 B（新增 `/api/p389-extra`）各恰好 **1** 红，`src/main` 按 sha256 逐字节还原。⭐ 同族第三例：Python demo（26）与 Java demo（44）此前各已补一条，**后端这块最大的面此前是唯一没管的**。
### P2-172: ZH 的参数表把 **「(必填)」写在「默认值」列** —— 必填参数**没有默认值**，该单元格自相矛盾，且与 EN 表结构不同


- **Scope**: → [scope-9][a6]
- **Evidence**: → 归档 [`…-90`][a48]（两张错列表原文、EN 对照表、ZH 自己的 5 列体例先例、全量扫描：其余 4 张缺列的表参数**全部可选**）
- **修法（本轮已修，文档方向，ZH 侧 2 张表）**: 改成 ZH 文档自有的 5 列体例 `参数 | 类型 | 必填 | 默认值 | 说明`，必填项的默认值写成 `—`。
- **Status**: ✅ **已修（389 轮）** —— ⭐ 是第 160 轮那条（ZH 参数表把默认值写进必填列）的**镜像变体**：那次是「默认值跑进必填列」，这次是「必填跑进默认值列」。修后 `(必填)` 出现在默认值列的次数 **3 → 0**，EN/ZH 参数表**行级零分歧**。

## 本轮压缩记账

| 项 | 值 |
|---|---|
| 工作文件**起点** | **1243 行 / 149778 字节** |
| 迁入本归档的 12 条已结案条目正文 | **8628 字节** |
| 本轮写入（两条新条目 + P2-159 更正） | **1367 字节** |
| 工作文件**终值** | **1240 行 / 146043 字节** |
| 全文件**净变化** | **-3 行 / -3735 字节** |

- ⭐ **压缩记账要分清三个数**：**归档侧持有量**（本文档体量）、**工作文件侧迁出量**、
  **全文件净变化** —— 三者**方向相反、不可混用**。本轮迁出的量远大于写入量，
  净变化**仍是负数**。
- ⭐ **归档头体量是自指的**（头里写着本文档自己的行数与字节数）⇒ 用**每轮从模板出发**的
  定点求解器迭代，收敛判据是**独立回读校验**（声明值 == 磁盘实测值、无残留占位符），
  **不是**「文本没变」。
