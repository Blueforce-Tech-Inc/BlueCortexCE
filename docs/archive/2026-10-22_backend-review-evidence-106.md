# 后端审查证据归档 -106（2026-10-22）

> 第 405 轮（JS/TS SDK 代码方向 + 用户指南/运维指南文档方向，doc round 304）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1358 行 / 149278 字节**）。
> 本文档体量：**243 行 / 19348 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。

## 本轮新写的三条（正文逐字保留在本归档）

### P2-209: JS SDK 的**出站 query 参数名**零断言 —— 后端**静默忽略**未知 query 参数，参数名写错不报错、只是不生效

- **Scope**: → [scope-9][a6]
- **Problem**: 第 399 轮把这一家的**出站请求体**键集合一次性钉死了（`src/__tests__/wire-format.test.ts`），
  但 **query 串**这一半从头到尾没人钉过 —— 而 query 参数名错了**不会报错**：
  Spring 只绑定它声明过的名字，多出来的名字**连一个字都不吭**（活体 spec 里
  `GET /api/search` 的 `orderBy` 是个普通 query 参数，没有 `required` 约束）。
  ⇒ 症状不是「报错」，而是「那个参数**没生效**」，排序悄悄不排序了。
  ⇒ 既有测试对 query 的断言**形态**只有一种：`expect(url).toContain('offset=10')` /
  `not.toContain('offset=')`（`src/__tests__/client.test.ts:532` / `:544` / `:625` / `:1015` / `:1093`）
  —— **只问「某个参数在不在 / 值对不对」，从不问「键集合是哪些」**。
- **活体实测（2026-10-22，新鲜实例 37790）**：
  - `GET /api/search` 分别带 `orderBy=createdAtEpoch`、带 `order_by=createdAtEpoch`、以及**不带**排序键
    —— 三次响应**逐字节相同**，都是 **200**。
  - 必填参数改名会响一点：`GET /api/extraction/{templateName}/history` 少传 `projectPath` → **400**。
- **实测（修复前）**：对 **19** 个 query 参数槽（跨 **7** 个端点）逐个改名
  （**按方法块定位**，只改发送该参数的那一个方法），跑整套件：**钉住 15 / 未钉住 4**。

  | 端点 | 未被钉住的 query 参数 | 为什么没钉住 |
  |------|----------------------|------------|
  | `GET /api/search` | `orderBy` | 既有断言全是 `toContain` 式，**从没有一条问过排序键叫什么** |
  | `GET /api/observations` | `offset` | 只有**反向**断言「`offset=0` 时不发这个参数」（`client.test.ts:625`），**没有**正向断言「`offset=10` 时要发」 |
  | `GET /api/extraction/{templateName}/history` | `projectPath` | 兄弟端点 `/latest` 的同名参数**被钉住**，`/history` 这条**没有** |
  | `GET /api/extraction/{templateName}/history` | `userId` | 同上 |

  ⇒ 后两条是**一对**：同一个文件的 `/latest` 两个参数都钉住了，`/history` 两个都没钉住。
  ⇒ **19 个参数名与后端全部吻合** ⇒ 本条记的是**覆盖缺口**，不是行为缺陷
  （与第 399 轮在请求体上查到的是同一类）。
- **Status**: ✅ **已修（405 轮，JS/TS SDK 侧，只加测试）** —— 新增 `src/__tests__/query-params.test.ts`（**7** 例）钉死七个端点的**完整 query 键集合**并与后端 `@RequestParam` 绑定；负控：改名变异钉住 **15 → 19 / 19**（未钉住 **4 → 0**）；正文与证据逐字见归档 [`…-106`][a64]。

### P2-210: 运维指南把抽取端点写成「`GET`/`POST`」+ `{t}` —— 而活体只有 `GET`、变量名是 `{templateName}`；根指南另有两处幻影端点

- **Scope**: → [scope-9][a6]
- **Problem**: `docs/DEPLOYMENT.md:670` 与 `docs/DEPLOYMENT-zh-CN.md:636` 的**同一行**写着
  `` `GET`/`POST /api/extraction/{t}/latest`, `/history` `` —— 三处与活体不符：
  1. **方法**：活体 spec 里 `/api/extraction/{templateName}/latest` 与 `/history` **只有 `GET`**。
     活体实测 `POST …/latest?projectPath=/p` → **405 Method Not Allowed**，
     同一 URL 的 `GET` → **200**（**存在性阳性对照**：405 不是「路径不存在」，是「方法不对」）。
  2. **路径变量名**：活体是 `{templateName}`，不是 `{t}`。
  3. 同一张表里**其余**行（`/api/memory/experiences`、`/api/memory/icl-prompt`、
     `/api/search`、`/api/observations`）的方法与路径都核对无误 —— **只有这一行错**。
  ⇒ 另在根指南 `CLAUDE.md` 查到两处**幻影端点**：
  - `:178` `GET /api/sessions/{id}` → 活体 **404**（真实是 `GET /api/session/{sessionId}`，**单数**；
    活体 spec 里 `/api/sessions` 只有 `GET /api/sessions` 与 `DELETE /api/sessions/{platformSource}/{contentSessionId}`）。
  - `:179` `POST /api/sessions/import` → 活体 **404**（真实是 `POST /api/import/sessions`；
    实测 **415** ⇒ **端点存在**，只是本探针没带 content-type —— 415≠404 就是存在性阳性对照）。
  ⇒ **核实为真、未改动**的部分：该行「认 `userId` / 按用户限定」的主张**成立**
  —— `StructuredExtractionService:171-189` 走 `resolveSessionId(pattern, projectPath, userId)` 逐用户限定。
  ⇒ ⚠️ **`CLAUDE.md` 被 `.gitignore:71` 忽略** ⇒ 那两处修正**只在本机生效、不入库**，
  别的 clone 拿不到（与第 154 轮记下的 `AGENTS.md` 同一处境）。
- **Status**: ✅ **已修（405 轮，文档方向）** —— DEPLOYMENT 双语各一处改为 `GET /api/extraction/{templateName}/latest`、`/history`；`CLAUDE.md` 两处幻影端点改正（活体 **404**）。⏸ `CLAUDE.md` 被 `.gitignore` 忽略 ⇒ 该两处修正**只在本机生效**；正文与证据逐字见归档 [`…-106`][a64]。

### P2-211: 路径变量名与活体 spec 不一致（**纯命名**，URL 形状不变）—— 活体是 `{templateName}` 与 `{sessionId}`

- **Scope**: → [scope-9][a6]
- **Problem**: 活体 spec 的路径变量名是 `{templateName}`（抽取）与 `{sessionId}`（会话），
  而活文档里另有两套写法。**纯命名问题**：URL 形状不变（`{template}` 与 `{templateName}`
  在 URL 里都是同一个占位段），但照抄文档写死路径、或据此生成客户端代码的人会拼错。
  - `{template}` 共 **25** 处（活文档，**不含** `docs/archive/`）：
    | 文件 | 处数 |
    |------|------|
    | `docs/drafts/go-sdk-design.md` | **15** |
    | `docs/drafts/js-sdk-design.md` | **2** |
    | `docs/drafts/python-sdk-design.md` | **2** |
    | `docs/api-json-naming-convention.md` | **2**（`:122` / `:123`） |
    | `cortex-mem-spring-integration/README.md` | **2**（`:822` / `:823`） |
    | `cortex-mem-spring-integration/README-zh-CN.md` | **2**（`:782` / `:783`） |
  - `{id}` vs `{sessionId}` 共 **3** 处：
    | 位置 | 写的 | 说明 |
    |------|------|------|
    | `docs/api-json-naming-convention.md:100` | `GET /api/session/{id}` | 同文件 `:94` 正文自己写的是 `GET /{sessionId}` ⇒ **自相矛盾** |
    | `backend/README.md:125` | `GET /api/session/{id}` | 下一行 `:126` 写的是 `{sessionId}` ⇒ **同一张表内自相矛盾** |
    | `proxy/README.md:221` | `GET /api/session/{id}` | 活体路径变量是 `{sessionId}`（spec 里 `path` 位置、必填） |
  ⇒ **核实为真、不是问题**的部分：
    - `docs/api-json-naming-convention.md:71` / `:95` / `:253` / `:258` 是**故意陈述**
      （「没有这个端点」/「`PATCH /api/session/{id}` 返回 405」）—— 讲的是**不存在的**路径，写法正确。
    - `docs/api-json-naming-convention.md:101` / `:107` 与 `backend/README.md:126` 写的是 `{sessionId}`，正确。
    - `docs/drafts/hermes-memory/60-evolution/87-….md:74` 提到 `/api/session/{id}/compress`
      是**假设性的后续引入**（「Phase 2 引入 … 后可能面临」），不是对现状的断言。
    - 归档与追加式审计链（`docs/drafts/doc-review-task.md`、`docs/drafts/patrol-rotation.md`）
      里的同名片段是**历史记录**，不可改。
  ⇒ 这四类文件分别属 **API 文档 / SDK README / Backend / 设计文档**四个方向，
  按「一轮一方向」留给对应轮次，**本轮只记录不修**。
- **Status**: ⏸ **待决（405 轮记，跨方向，本轮只记录不修）** —— `{template}` 共 **25** 处、 `{id}` vs `{sessionId}` 共 **3** 处；属 API 文档 / SDK README / Backend / 设计文档方向，留给对应轮次；正文与证据逐字见归档 [`…-106`][a64]。

## P2-209 的证据：JS SDK 出站 query 参数的完整键集合

### 七个端点的 query 键集（手写期望值）

| 端点 | 完整键集 | 槽数 |
|------|----------|------|
| `GET /api/search` | `concept` `limit` `offset` `orderBy` `project` `query` `source` `type` | **8** |
| `GET /api/observations` | `limit` `offset` `project` | **3** |
| `GET /api/extraction/{templateName}/latest` | `projectPath` `userId` | **2** |
| `GET /api/extraction/{templateName}/history` | `limit` `projectPath` `userId` | **3** |
| `GET /api/stats` | `project` | **1** |
| `POST /api/memory/refine` | `project` | **1** |
| `GET /api/memory/quality-distribution` | `project` | **1** |
| 合计 | | **19** |

> 期望键集**全部手写** —— 从 SDK 自己的 query 构造代码推导 ⇒ **恒真断言**，
> 比没有断言更糟（与第 403 / 404 轮同一条纪律）。

### 与后端契约的绑定（读后端控制器源码）

新测试**读后端自己的控制器源码**取出每个端点声明的 `@RequestParam` 名，
再断言「SDK 发出的每个键都必须是后端声明过的键」（**子集**关系，不是相等）：

| 后端坐标 | 端点 | 声明的 query 参数 |
|----------|------|------------------|
| `ViewerController.java:97-112` | `GET /api/observations` | `project` `platformSource` `contentSessionId` `offset` `limit` |
| `ViewerController.java:225-232` | `GET /api/stats` | `project` |
| `ViewerController.java:282-307` | `GET /api/search` | `project`(req) `query` `type` `concept` `source` `limit` `offset` `orderBy` |
| `ExtractionController.java:57-63` | `GET {templateName}/latest` | `projectPath`(req) `userId` |
| `ExtractionController.java:113-121` | `GET {templateName}/history` | `projectPath`(req) `userId` `limit` |
| `MemoryController.java:74-76` | `POST /refine` | `project`(req) |
| `MemoryController.java:175-177` | `GET /quality-distribution` | `project`(req) |

⇒ SDK 发的 19 个名字**全部**在对应端点的声明列表里 ⇒ 记的是**覆盖缺口**。

### 新增测试的七个用例（三族）

| 用例 | 作用 |
|------|------|
| `sends exactly the expected keys — every endpoint` | 七个端点的**完整键集合**（一次报全部差异，不在第一个错处停下） |
| `sorts by the key the backend declares, not by a near miss` | `orderBy` 必须发、且 `order_by` **不许**发 |
| `sends the extraction history parameters the sibling endpoint also sends` | `/history` 的 `projectPath` 与 `userId` 必须发 |
| `sends a positive offset for the observations endpoint` | `offset=10` 必须发（补上只有反向断言的那个缺口） |
| `reads the backend controllers and finds every endpoint` | 后端控制器能被解析出、且每个端点都有 `@RequestParam` |
| `sends no parameter the backend does not declare` | SDK 发的每个键都必须是后端声明过的 |
| `reports an extra key and a missing key` | **自检**：比较函数确实能报出「多一个键」与「改一个键名」 |

### 负控（证明「这条断言有牙」）

对 **19** 个 query 参数槽逐个改名（**按方法块定位**），跑整套件：
修复前 **15** 个转红、**4** 个仍全绿 ⇒ 修复后 **19 / 19** 全红（未钉住 **4 → 0**）。

## 收尾自查：给「后端源码缺失」补一条可读失败

收尾复读新测试时发现一处**可读性缺口**：Family 2 读后端控制器源码，若这个 SDK 被**单独检出**
（`backend/` 不在），`readFileSync` 会抛一个裸 `ENOENT`，读起来像「路径常量写错了」，
而不是「这个检查需要仓库里的后端源码」。
⇒ 补 `backendControllerDir()`：目录不存在时抛一条**说明来由**的错误（Python 轮
`test_wire_format.py` 的 `backend_root()` 是同一处理）。
⇒ **负控**：把路径指到不存在的目录 ⇒ 套件 **2 失败 / 5 通过**（失败的正是 Family 2 的两例），
报错文本是那条自定义消息（**不是**裸 ENOENT）；随后按字节还原（`md5` 比对一致）。
⇒ 关键性质：**后端源码缺失时这个检查会红，不会静悄悄跳过** ——
一个「看不见东西就当通过」的校验器是最贵的一种失败。

## P2-210 的证据：运维指南与根指南的端点陈述逐条核验

| 文档写的 | 活体 | 结论 |
|----------|------|------|
| `GET`/`POST /api/extraction/{t}/latest`, `/history`（`DEPLOYMENT.md:670` / `DEPLOYMENT-zh-CN.md:636`） | spec：**只有** `GET /api/extraction/{templateName}/latest` 与 `…/history`；实测 `POST` → **405**、`GET` → **200** | **方法与变量名都错**，已改为 `GET /api/extraction/{templateName}/latest`、`/history` |
| `GET /api/sessions/{id}`（`CLAUDE.md:178`） | 活体 **404**；真实是 `GET /api/session/{sessionId}` | **幻影端点**，已改 |
| `POST /api/sessions/import`（`CLAUDE.md:179`） | 活体 **404**；真实是 `POST /api/import/sessions`，实测 **415** ⇒ 端点存在 | **幻影端点**，已改 |
| 「认 `userId` / 按用户限定」（同一行的语义主张） | `StructuredExtractionService:171-189` 走 `resolveSessionId(pattern, projectPath, userId)` | **核实为真，未改** |
| 同一张表里其余各行的方法与路径 | 逐条与 spec 对拍 | **全部正确，未改** |

### `CLAUDE.md` 的特殊性

`git check-ignore -v CLAUDE.md` → `.gitignore:71:CLAUDE.md` ⇒ 它**根本不在版本控制里**
（`git ls-files --error-unmatch CLAUDE.md` 报 `did not match any file(s) known to git`；
`git log --diff-filter=D -- CLAUDE.md` 显示它是在 V13 那个提交里从索引移除的）。
⇒ 那两处修正**只在本机生效、不随提交传播**；这与第 154 轮记下的 `AGENTS.md` 是同一处境。

## P2-211 的证据：路径变量命名不一致的逐处定位

活体 spec 的路径变量（`GET /api/session/{sessionId}` 的 `sessionId` 是 `path` 位置、**必填**；
抽取端点的占位段是 `{templateName}`）：

| 类别 | 活文档处数 | 分布 |
|------|-----------|------|
| `{template}`（应为 `{templateName}`） | **25** | 设计稿 **19**（`go-sdk-design.md` 15 / `js-sdk-design.md` 2 / `python-sdk-design.md` 2）、API 文档 **2**、Java SDK README 双语 **4** |
| `{id}`（应为 `{sessionId}`） | **3** | `api-json-naming-convention.md:100`、`backend/README.md:125`、`proxy/README.md:221` |

⇒ 另有一类**自相矛盾**的写法值得单列：`api-json-naming-convention.md` 的表（`:100`）写 `{id}`，
而同文件正文（`:94`）写 `{sessionId}`；`backend/README.md` 的表（`:125`）写 `{id}`，
而下一行（`:126`）写 `{sessionId}` —— 同一个文件里离得最近的两行就是两种写法。

## 本轮探针/判据自身的四处错（全部在写成发现之前拦下）

1. **扫描序列内污染**：首轮扫描里 `listObservations::offset` 读 **RED**，后续复跑读 **GREEN**
   ⇒ 判定为**序列内污染**（疑 vitest 转换缓存把上一槽的变异串到了下一槽），
   改为**逐槽隔离复验 2×**（4 个未钉住 + 4 个阳性对照全稳定）才采信。
2. **两个变异串没打上**：源码形态是 `{ project: req.project }` 与 `project: projectPath,`，
   与预期的字面形态不同 ⇒ 变异没生效，两槽被误读成「已钉住」。
3. **Java `@RequestParam` 可以不带参数**（`@RequestParam String project`），
   解析器按「注解后必跟括号」写 ⇒ 漏抓 ⇒ 解析结果错误。
4. **`@Parameter(description="…(max 100)")` 里的括号**打断了按 `(` 配平的解析 ⇒ 描述串被当成签名。

## 验收

| 项目 | 读数 |
|------|------|
| JS SDK 全量（`npx vitest run`） | **395** 通过 / **0** 失败（改前 **388** ⇒ 本轮 **+7**） |
| 负控（改名变异） | 修复前钉住 **15** / 未钉住 **4** → 修复后 **19 / 19** ⇒ 断言有牙 |
| 回归 | **46 / 0 / 1**，exit 0，跑在新鲜 37790（pid **27949**） |
| Phase3 验收 | **25 / 0 / 0**，exit 0，同一实例 |
| 新鲜度闸门 | `find backend/src -newer <jar>` 为空（后端源码未动） |
| 指纹 | `a7ae8cc2…` → `5f08ba9a…`，记录数 2244 → **2245** |

## 压缩记账

| 口径 | 数值 |
|------|------|
| 工作文件**写入前** | **1345 行 / 149498 字节**（余量 **502** 字节） |
| C8（⏸ 条目的证据段） | **已用尽**（第 400 轮用尽，本轮复核：**44** 条 ⏸ 条目只剩指针行，无可迁出的正文） |
| C10 的 Scope/Evidence 一支 | **已用尽**（本轮实测：带 `- **Scope**` / `- **Evidence**` 行的**已结案**条目为 **0** 条） |
| 本轮实际用的杠杆 | **第 292/293 轮的「缩短已结案条目的 Status 行」**：缩短 **18** 条（`P2-10` / `P2-22` / `P2-131` / `P2-138` / `P2-145` / `P2-152` / `P2-157` / `P2-166` / `P2-171` / `P2-172` / `P2-175` / `P2-180` / `P2-193` / `P2-196` / `P2-197` / `P2-202` / `P2-205` / `P2-206`），共省 **1739** 字节 |
| 缩短的**构造方式** | 每条新行都由**原行的逐字切片**拼成（保留段 / 删除段 / 新增指针三段式），**没有任何一处是重打的** ⇒ 不可能凭空造出 token |
| token 校验 | 每条缩短后的 Status 的**事实 token**（反引号字面量、「」字面量、数字）都**逐字**存在于**该行链接的全部归档**里（多归档取并集）；链接目标在抽 token **之前**先摘掉，避免文件名里的日期数字变成假 token |
| 顺带修正 | `P2-205` / `P2-206` 两条此前**没有**归档指针（正文已按 C10 迁入 `-104` / `-105`，工作文件却没写指向哪里）⇒ 补上指针，token 校验才有确定的检索目标 |
| 工作文件**写入后** | **1358 行 / 149278 字节**（余量 **722** 字节） |
| 工作文件**净变化** | **1345 行 / 149498 字节** → **1358 行 / 149278 字节**（**+13 行 / −220 字节**） |
| 本归档体量 | **243 行 / 19348 字节** |

⇒ 本轮**未改阈值**：余量从 **502** 字节回到 **722** 字节；
⇒ 但 C8 与 C10 的 Scope/Evidence **两支都已用尽**，可用的杠杆只剩下
⇒ **缩短已结案条目的 Status 行**这一支 —— 本轮已用掉 **18** 条（共 **1739** 字节），
⇒ 下一轮写入仍需继续吃这一支，而这一支的存量有限；
⇒ **P2-115 仍是待用户决策的流程契约变更**（建议 `MAX_LINES=2000` / `MAX_BYTES=200000`，
只同步 `docs/drafts/cron-combined-task.md:14`）。

> ⇒ 与归档 `-104` / `-105` 的记账纪律一致：**迁出量、写入量、净变化是三个数，不可混用**。
> ⇒ 压缩后的每条 Status，其**事实 token** 都**逐字**存在于它链接的归档里 ——
> 校验是**脚本做的**（按段重建 + 逐 token 核对），不是人眼扫的。

## 本轮链接定义

```
[a64]: ../archive/2026-10-22_backend-review-evidence-106.md
```
