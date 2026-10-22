# 后端审查证据归档 -108（2026-10-22）

> 第 407 轮（Backend 代码方向 + SDK README 文档方向，doc round 306）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1334 行 / 150082 字节**）。
> 本文档体量：**155 行 / 11304 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。

## 本轮新写的一条（正文逐字保留在本归档）

### P2-213: 后端 **68** 个 query 参数名**无一被钉住** —— 端点面钉住了，绑定名没钉住

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Problem**: Spring 按**注解里声明的名字**绑定 query 参数；裸 `@RequestParam`（不写 name）则按
  **编译参数名**绑定。后端 14 个 controller 一共 **68** 处 `@RequestParam`（分布：
  `ViewerController` **36**、`ContextController` **19**、`ExtractionController` **6**、
  `ViewerSessionController` **4**、`MemoryController` **2**、`LogsController` **1**），
  而**整个后端测试套件里没有一处断言过 query 参数名** ——
  `grep -rn "RequestParam|\"query\"" backend/src/test` 的**唯一**命中是
  `OpenApiExampleTypeContractTest` 里的 `method.getParameters()`，而它读的是 `@ApiResponse` 的 example，
  **与 query 参数无关**。
  ⇒ 症状与第 405 轮 P2-209（JS SDK 出站 query 参数名）、第 406 轮 P2-212（demo 端点绑定名）**同族**：
  参数名错了**不报错**，Spring 只绑定它声明过的名字，多出来的名字**连一个字都不吭**
  ⇒ 那个参数**没生效**，而调用方**无从得知**。
  ⇒ 但这一层比前两层更要紧：后端是**契约的源头** —— 绑定名一变，
  **活体 OpenAPI spec 发布的参数名**、**API 参考文档的参数表**、**四家 SDK 实际发出的 query 串**
  三处会**同时**与事实脱节，而**没有任何一处会变红**。
- **实测（修复前）：9 个控制器各改一个绑定名 ⇒ 后端套件仍 192 / 192 全绿。**
  变异手法取**最小作用域** —— 只给注解加 `name = "xZZ"`（`@RequestParam(name = "xZZ", …)`），
  **不动方法体**（仍可编译），一次构建覆盖 6 个 controller 各一个参数槽
  （`ViewerController` / `MemoryController` / `LogsController` / `ViewerSessionController` /
  `ExtractionController` / `ContextController`）。
- **阳性对照（三道，缺一不可）**：
  1. **字节码级**：`javap -v` 读编译产物，`ContextController` 的
     `RuntimeVisibleParameterAnnotations` 里明确出现
     `org.springframework.web.bind.annotation.RequestParam(name="xZZ6", required=false, defaultValue="")`
     ⇒ 变异**确实编译进了产物**。
  2. **活体契约级**：用变异后的 jar 起实例读 `/v3/api-docs` ⇒ **6** 个端点的参数名**全变成了**
     `xZZ1`…`xZZ6`（`GET /api/observations → ['xZZ1', 'platformSource', 'contentSessionId', 'offset', 'limit']`、
     `GET /api/logs → ['xZZ3']`、`GET /api/context/inject → ['xZZ6']` …）
     ⇒ **对外契约真的变了**，而**测试全绿**。
  3. **还原对照**：还原源码后重新打包起实例 ⇒ 参数名回到 `project` / `lines` / `projects` …。
- **修法（只加测试，后端主源码零改动）**：新增
  `backend/src/test/java/com/ablueforce/cortexce/controller/BackendQueryParamContractTest.java`
  （**4** 例）：
  1. **每个端点的完整 query 键集合**按集合相等比对（**19** 个端点、共 **68** 个槽）；
  2. **每个端点的 path 变量名**按集合相等比对（**15** 个端点）；
  3. **防漏闸门**：任何**带参数**的端点若不在期望表里即失败（新增端点必须登记），
     反向亦然（期望表里的端点必须真的存在）；
  4. **可证伪自检**：端点数与槽数的**覆盖下限**（≥19 / ≥15 / ≥80），
     并断言「一个**故意写错**的期望值**必须**比对不等」—— 否则「永远相等」的比对会让前三例
     在任何代码上都通过。
  ⇒ 后端套件 **192 → 196 / 196**。
- **期望值手写，不从被测物推导**（从自身代码推导 ⇒ 恒真断言）：
  期望表由**两个独立来源**交叉核对得出 ——
  ①**临时探针**（反射打印，**跑完即删**）读出的端点 → 参数名清单；
  ②**活体 OpenAPI spec**（新鲜 37790）的同一张清单。
  ⇒ 二者 **19 / 19** 端点、**68 / 68** 槽**完全吻合**，且与源码里 `@RequestParam` 的
  **静态计数**（19+6+1+2+36+4 = **68**）三方一致。
- **负控（单变量）**：只把 `LogsController` 的 `lines` 改成 `xZZ3` ⇒ 新测试**恰好 1 红**，
  红在 `everyEndpointBindsExactlyTheExpectedQueryParameters`，
  报错精确到 `query parameter names of GET /api/logs drifted ==> expected: <[lines]> but was: <[xZZ3]>`，
  其余 **3** 例保持绿 ⇒ 断言**有牙**且**不误伤**。
- **定性**：**覆盖缺口**，不是行为缺陷 —— 后端现有绑定与活体 spec **完全一致**
  （19 端点 / 68 槽逐条吻合），缺的只是「有人钉住它」这件事。
- **附带事实（已如实记录、**不改行为**）**：后端 68 处 `@RequestParam` **绝大多数是裸注解**，
  绑定名依赖**编译参数名**；字节码里确有 `MethodParameters`（`javap` 读 `LogsController` 得
  `Name: lines`）⇒ 当前构建是安全的，但**没有测试钉住这一点** ——
  若哪天 `-parameters` 失效（例如编译插件配置被覆盖），Spring 会因
  「Name for argument of type [java.lang.String] not specified」而**启动即失败**。
  本轮**不新增构建期守卫**（属另一族问题，且需要改 `pom.xml` 的编译配置）。
- **Status**: ✅ **已修（407 轮，Backend 侧，只加测试；后端主源码零改动）** —— 新增
  `BackendQueryParamContractTest`（**4** 例）钉死 **19** 个端点的**完整 query 键集合**（**68** 槽）
  与 **15** 个端点的 path 变量名，并带**防漏闸门**与**可证伪自检**；负控：改一个绑定名 ⇒
  **恰好 1 红**且报错精确到端点；期望值由**探针**与**活体 spec** 两个独立来源交叉核对后**手写**；
  正文与证据逐字见归档 [`…-108`][a66]。

### P2-211 的第 407 轮标注：Java SDK README 双语 **4** 处已修

- **原条目**：第 405 轮记的「路径变量名与活体 spec 不一致（**纯命名**，URL 形状不变）」，
  计数为 `{template}` 共 **25** 处（设计稿 **19**、API 文档 **2**、Java SDK README 双语 **4**）、
  `{id}` vs `{sessionId}` 共 **3** 处；当时的处置是「⏸ **待决（405 轮记，跨方向，本轮只记录不修）**」，
  并写明「属 API 文档 / SDK README / Backend / 设计文档方向，**留给对应轮次**」。
- **第 407 轮的文档方向正是 SDK README** ⇒ 本轮实施其中**属 Java SDK README 的那 4 处**。
- **对拍方法（可复现）**：把 Java SDK 两份 README 里**全部** `/api/...` 路径（各 **24** 条去重后）
  与**活体 OpenAPI spec** 逐条对拍，**路径变量名也比**（不只比 URL 形状）：

  | README | 修前 | 修后 |
  |--------|------|------|
  | `cortex-mem-spring-integration/README.md` | `L822` / `L823` 变量名不符 | 0 条不符 |
  | `cortex-mem-spring-integration/README-zh-CN.md` | `L782` / `L783` 变量名不符 | 0 条不符 |

  修前恰好 **4** 条变量名不符（`{template}` vs 活体的 `{templateName}`），
  其余 **20** 条**精确吻合**（含 `sessionId` / `contentSessionId` / `projectPath` / `userId` 等）。
- **改法**：四处 `{template}` → **`{templateName}`**（各 2 行，方法表内）。
  ⇒ 属「**失实陈述的文档修正**」：后端发布的是 `{templateName}`，README 写的是 `{template}`；
  URL 形状不变（读者照抄仍可用），但**手写契约与机器可读契约的名字不一致**。
  ⇒ 另核实：SDK **接口 Javadoc** 写的本来就是 `{templateName}`
  （`CortexMemClient.java:109` / `:118`），与活体一致 ⇒ 只有 README 与实现里的 URI 模板是 `{template}`。
- **不属对外契约、故不改的一处**：Java SDK **实现**里
  `CortexMemClientImpl.java:375` / `:398` 写 `.path("/api/extraction/{template}/latest")`。
  那是 **RestTemplate 的 URI 模板变量**，展开后发出的是
  `/api/extraction/<实际值>/latest` ⇒ **后端按位置匹配，收到的路径与活体完全一致**，
  **行为零影响** ⇒ 属实现内部命名，**不改**（改动对行为无益且会波及既有测试）。
  此处如实记录，避免下一轮误把它当成对外契约缺陷。
- **本条仍未结案**：余下 **21** 处（设计稿 **19** + API 文档 **2**）与 `{id}` vs `{sessionId}` 的 **3** 处，
  分别留给 API 文档 / Backend / 设计文档方向。
- **Status**: ✅ **部分已修（407 轮，SDK README 方向）** —— Java SDK README 双语 **4** 处已改；
  **本条其余部分仍为 ⏸**，留给其余三个方向（追加式审计链只可标注，原文一字未改）；
  证据逐字见归档 [`…-107`][a65] 与本归档 [`…-108`][a66]。

## 按 C8 从工作文件迁入的证据段（逐字保留在本归档）

> 迁出的是 **⏸ 待决条目**的**证据 bullets**（第 254 / 384 轮规则）；
> `- **Problem**` / `- **Status**` / `- **Severity**` / `- **⚠️ 为什么不单方面改**` /
> `- **建议修法**` 一律**留在工作文件**。

### P2-23: 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  - 实测证据: 逐字迁入 [`…-16`][a24]（267 轮）——含 105 条并发裸 socket 实测（恰好 100 条 200、第 101–105 条 500）、四处 `broadcast()` 调用点行号，以及活体响应 `Content-Length: 0` 不泄漏内部信息。

### P2-48: 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  - 实测证据: 逐字迁入 [`…-16`][a24]（267 轮）——含 9 条路径逐条清单、`/api/ingest` 实际四条，以及 service「28+」实测 29 属合法范围故不计。

### P2-57: 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  - 实测证据（四家对拍表、文件行号、`lsof` 与 `[::1]` 直连结果）: 逐字迁入
    [`…-21`](../archive/2026-10-05_backend-review-evidence-21.md)（271 轮）。

### P2-46: 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  - **同族事实（已修，全文见归档）**: 同一幻影端点也出现在设计文档 `phase-3-design/25.md:699`，已改为脚本真正使用的写法。

### P2-30: 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  - **Impact / Status / Reproduction（逐字幸存）** 与事故经过：见
    [`…-30`](../archive/2026-10-05_backend-review-recovered-P2-30.md)。


## 本轮链接定义

```
[a66]: ../archive/2026-10-22_backend-review-evidence-108.md
```
