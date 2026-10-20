# Backend Review Evidence 76 — 第 375 轮本轮新条目的整条正文（第 375 轮迁出）

> **归档规则**：承 `-62` ~ `-75` 的体例，迁出**本轮新写**的条目全文（写作时 findings 的
> 字节余量只剩 **307** 字节，本轮先做了**两处零信息损失压缩**才腾出空间 ——
> C3b「CJK 唯一短词组」把长度上界由 4 放宽到 **8**，去标记 **120** 处；
> C3c「CJK 重复长词组（频次 ≥ 2、长度 9~20）」去标记 **4** 处；
> **两处都只删 `**` 标记、一个字未改**；硬证明：去掉全文所有 `*` 后字符流与压缩前**逐字相同**），
> 工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：112 行 / 8789 字节。**
>
> 本批迁出 **P2-137** 一条新条目，并记录 **P2-131 跨家结转收官**与 **P2-136 的 JS 侧结转**。
>
> **本轮代码方向为 JS/TS SDK，文档方向为设计文档（doc round 275）。**
>
> **P2-131 的四家结转在本轮收官。** 同一根因（tool-use 端点「接受但不落库」的三个字段）
> 在**四个表面**各留一个坑：活体 OpenAPI 的 schema（P2-130）、SDK 的 DTO 与 README
> （P2-131，四家）、demo 的 HTTP 端点（P2-136，四家）。**四家现已全部注明。**
>
> **本轮的方法学纪律仍是两条**：
> ① **探针自身出错必须先识别再采信** —— 端点对拍的第一版把「活体有、SDK 没用到的操作」
> 混进了未命中清单（**53** 个），第二版把路径变量当成普通路径（**5** 处假 MISS），
> 第三版按**形状**回配才得到 23/23；**阳性对照**（编造的端点）每次都正确 MISS。
> ② **「带日期的快照」与「活文档里的现在时断言」要分开** —— P2-137 那句话在署期当天
> **确实为真**（后端 2026-03-28 时点只返回 4 个键），是后来才变假的；
> 而文档描述的是**今天这个已发布的包**，且**它自己的 README 写的是现在时且正确** ⇒ 判为失实陈述。
---

### P2-137: JS SDK 设计文档说 `POST /api/session/start` 的响应**不回显** `session_id` —— 活体实测**回显**

- **Problem**: `docs/drafts/js-sdk-design.md` 的「30-Second Example」里写着三行注释：
  `// Start session. Keep the session_id you chose — the response does not echo it`
  `// back, so it is not readable from the returned object.`
  两句断言：① 响应**不回显** `session_id`；② 因此从返回对象读不到。
  **活体实测（新鲜实例 37790）**：`POST /api/session/start` 返回
  `{"session_id":"p375-docprobe-1","context":"...","updateFiles":[],"session_db_id":"17b09c13-…","prompt_number":1}`
  —— **响应里有 `session_id`，且值就是请求里送进去的那个** ⇒ 断言 ① **为假**。
  断言 ② **为真**：`SessionStartResponse` 这个 TypeScript 接口只有
  `session_db_id` / `context?` / `prompt_number` 三个字段、**不含 `session_id`**
  ⇒ 在本包自己的类型下确实读不到。
  ⇒ **建议是对的，给出的理由是错的**：文档把「**类型没暴露**」说成了「**wire 没带**」。
  **这与本 SDK 自己的 README 直接矛盾** —— README 写的是
  「(session_id and updateFiles also arrive on the wire but are not part of the
  SessionStartResponse type.)」⇒ **同一件事、两份文档、两种说法，而 README 那份是对的**。
- **为什么不算「带日期的快照」**：该文档头部是 `Date: 2026-03-27` / `Status: Under Development`，
  且其「Version Strategy」表描述的是**已发布的 `@cortex-mem/js-sdk` v1.0.0**
  ⇒ 那个日期是**版本戳**，不是挂在某句断言上的测量戳。
  另外查了后端历史：**2026-03-28 时点的 `SessionController` 确实只返回 4 个键**
  （`context` / `updateFiles` / `session_db_id` / `prompt_number`，**没有** `session_id`）
  ⇒ 这句话在署期**当时为真**，是后端后来把 `contentSessionId` 加进响应体才变假的。
  **但那是一段已经过去的历史**：文档仍在描述今天这个包，而包的行为以今天为准；
  且文档**自己**的 README（同一份代码、同一句话的事实）写的是现在时且正确。
- **修复**：注释改成「响应**确实**在 wire 上回显 `session_id`（活体响应带
  `session_id` / `session_db_id` / `context` / `updateFiles` / `prompt_number` 五个键），
  但 `SessionStartResponse` **不暴露**它 ⇒ 在本包自己的类型下读不到，请自己留一份」，
  **「自己留一份」这个建议本身一字未改**。属**失实陈述的文档修正**（不涉及契约变更）。
- **按断言清扫**：`go-sdk-design.md` 与 `python-sdk-design.md` **没有**这一条
  （前者的方法映射表只列请求体，后者只有签名示例）⇒ **范围只此一处**。

### P2-131 的跨家结转收官（JS 侧已修 ⇒ 四家全部完成）

- 第 372 轮修 Java 侧，第 373 轮修 Go 侧，第 374 轮修 Python 侧，
  **第 375 轮修完 JS 侧** —— `js-sdk/cortex-mem-js`：
  - `src/client.ts` 的 `recordObservation` JSDoc；
  - `src/dto/observation.ts` 的 `ObservationRequest` 类型注释与三个字段的行内注释；
  - `README.md` 与 `README-zh-CN.md` 的「Wire Format」采集段；
  - `examples/http-server/app.ts` 的 create 端点注释与 `examples/http-server/README.md`。
- 四家定稿措辞一致：**指出后端处理器只读哪六个字段、这三个字段被接受后丢弃、
  并指向真正落库的 `updateObservation` / `UpdateObservation` / `update_observation`（PATCH）**。
- **JS 侧的特殊之处**：它**没有** Java 那样的 404/405 错误体分裂问题（本轮未涉及），
  它的 `ObservationRequest` 是**接口**（不是类），故坑落在**类型注释**而不是字段声明上 ——
  补法与 Go 的 `dto/observation.go` 同型。

### P2-136 的 JS 侧结转（demo 的 create 端点）

- `js-sdk/cortex-mem-js/examples/http-server/app.ts` 的 `POST /observations/create`
  与 Python / Go / Java 三家**同型**：接收 `source` / `extractedData` / `prompt_number`
  交给 `recordObservation`（tool-use 路径 ⇒ **从不落库**），却返回 `{ status: 'recorded' }`。
- 本轮**只加注释**（零可执行行、demo 契约未动），与第 374 轮 Python 侧同一处理。

### 本轮其余部分：核实为真

- **JS SDK 的 23 个请求点 vs 活体 OpenAPI：23/23 全对**（66 路径 / 71 操作）。
  判据是**动词 + 路径形状**（路径变量按段归一），不是逐字比对 —— 因为
  **URL 模板里的变量名不必与 SDK 的局部参数名同名**（SDK 写 `observationId`、
  活体写 `{id}`，落进 URL 的是**值**）。
  **阳性对照**：故意编造的端点正确 MISS ⇒ 判据看得见差异。
- **测试缺口普查（沿用第 373 轮立的角度）**：JS 的 **14 个错误判定函数逐个有测试 ⇒ 零缺口**
  （对照 Go 的 13 个分类器缺 2 个、Python 的 15 个零缺口）。
- **JS SDK 自身**：`tsc --noEmit` 干净；**264 个测试全绿**
  （246 client + 5 truncated-body + 8 http-server 示例 + 5 http-server 错误契约）。
- **JS README 的测试数**「**264**」与「246 + 5 + 8 + 5」的构成 —— **实测相符**。
- **设计文档的其余断言逐条核实为真**：
  「零强制依赖」（`package.json` 的 `dependencies` 为 **None**）、
  「无 `"type": "module"` 故 CJS 产物叫 `index.js` 而非 `index.cjs`」、
  `exports` 块（`types` / `import` / `require` 三键与文档**逐字一致**）、
  「`tsup` 配 `format: ['cjs','esm']`」、「没有 `CHANGELOG.md`」、
  「客户端另暴露 4 个 logger 方法与 `toString()`，它们不属于 API 面」、
  「API Method Mapping (26 methods)」的 26 行与客户端的 26 个方法**集合完全相同**、
  「后端**没有**单条观察读端点」——`GET /api/observations/{id}` 活体 **404**、
  `GET /api/memory/observations/{id}` 活体 **405**（**阳性对照**：`GET /api/observations?limit=1` → 200 命中 1 条）。

### 本轮其它方向：设计文档方向（doc round 275）

- 审的是 `docs/drafts/js-sdk-design.md`（与本轮代码方向同一家的设计稿）。
- 除 P2-137 外，**该文档的每一条可核断言都核实为真**（逐条见上）。
- 本轮在设计稿的「Wire Format Notes」段**补了 tool-use 丢弃那三个字段的说明**
  —— 与 P2-131 的四家定稿同一事实、同一措辞。
