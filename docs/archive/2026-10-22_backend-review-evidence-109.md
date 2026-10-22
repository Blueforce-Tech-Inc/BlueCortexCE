# 后端审查证据归档 -109（2026-10-22）

> 第 408 轮（Java SDK 代码方向 + 设计文档文档方向，doc round 307）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1340 行 / 148117 字节**）。
> 本文档体量：**165 行 / 15097 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。
> 头部数字用**解析求解** `D = base + len(str(D))` 得到并与磁盘实测比对（逐位迭代每轮漂移 1 字节、永不收敛，故不用）。

## 本轮新写的一条（正文逐字保留在本归档）

### P2-214: Java SDK 的 **20** 个 query 槽里 **9** 个改名**完全不可见**，且 `/api/health` 的路径**零断言** —— 端点面钉住了，绑定名与路径没钉住

- **Scope**: → [scope-9][a6]、[scope-10][a7]；本条的**逐条坐标**（实现里 20 处 `queryParam(` 的行号、旧断言形态与计数、活体 spec 逐槽对照）见下。
- **Problem**: `CortexMemClientImpl` 用**手写字符串**拼 query 串，共 **20** 处 `queryParam(`，分布在 **8** 个端点：
  `refine`{project}、`quality-distribution`{project}、`extraction latest`{projectPath,userId}、
  `extraction history`{projectPath,limit,userId}、`extraction run`{projectPath}、
  `search`{project,query,type,concept,source,limit,offset,orderBy}、
  `observations`{project,offset,limit}、`stats`{project}。
  旧测试对这些参数名的断言**全部是包含式**：
  `contains("project=")`×**4**、`contains("userId=")`×**3**、`contains("projectPath=")`×**2**、
  `contains("limit=")`×**2**、`contains("source=")`×**1**、`contains("query=")`×**1**
  （`CortexMemClientImplTest` 的 `getPath()` 上）。
  ⇒ **包含式断言对「改名」几乎无效**：只有名字恰好是 `project` / `projectPath` 的槽被钉住，
  别的名字改了照样全绿；而 `doesNotContain("limit=")` 这类**反向断言对改名更是完全免疫**
  ——它只对「是否发送」有效。
  ⇒ 症状与 P2-209（JS SDK 出站 query 参数名）、P2-212（demo 端点绑定名）、P2-213（后端 `@RequestParam`）
  **同族**：参数名写错**不报错**，后端只绑定它声明过的名字，那个参数**静默失效**。
  另有一条**同类但层次不同**的缺口：`/api/health` 的**路径本身**在**整个 client 测试树里零断言**
  ——四个 health 测试只断言布尔结果，而 mock server 对任何路径都会响应
  ⇒ 把 `.uri("/api/health")` 改成别的路径，整套测试依然全绿。
- **实测（修复前）：变异扫描 8 处（每端点 1 个）⇒ 6 红 2 绿。**
  红的 6 个：refine.`project`、search.`project`、observations.`project`、stats.`project`、
  latest.`projectPath`、run.`projectPath`；
  **绿的 2 个**：`quality-distribution`.`project` 与 `history`.`projectPath`（旧套件对其**零反应**）。
  ⇒ 静态计数与变异互证：**20 槽中 11 槽被钉住（且仅 `contains` 形态），9 槽改名完全不可见**
  （其中 2 槽只有 `doesNotContain` 反向断言，反向断言对改名免疫）。
- **修法**：新增 `CortexMemQueryParamsTest`（**12** 例）—— 8 个端点的**完整 query 键集合**
  （**20** 槽，**按集合相等**比对，不是包含式）＋ 2 个**条件省略族**
  （`limit`/`offset` 为 0 不发、`userId` 为 null 不发）＋ 覆盖下限 ＋ **可证伪自检**。
  期望值**手写**（从被测物自身推导 ⇒ 恒真断言），并与活体 OpenAPI spec 逐槽核对。
  另在 `CortexMemClientImplTest` 加 `healthCheck_callsTheHealthEndpoint`（**1** 例），
  钉住 `/api/health` 的**方法与路径**。
  ⇒ Java SDK 套件 **313 → 326 / 326**（client **260 → 273**，spring-ai **46**，starter **7**；
  口径为 surefire XML 逐类 `tests=` 求和）。**SDK 主源码零改动。**
- **过程中自查抓到的一处自身错误（记录在案）**：新测试最初把 `/api/memory/refine` 的**方法**标成 `GET`，
  而 SDK 实际发的是 **POST** ⇒ **标签不是测量**。成因很具体：`assertKeys` 当时只校验路径与 query 键、
  **不校验 HTTP 方法**，所以标签漂移不会让任何东西变红。
  ⇒ 修法两条：①`assertKeys` 增加方法断言；②每个端点的身份收敛成**单个常量**
  （`"<METHOD> <path template>"`），同时充当期望表的键、`@DisplayName` 与断言查表键
  ⇒ 三者**结构上无法漂移**。
  修后的负控：把 `REFINE` 常量由 `POST` 改成 `GET` ⇒ **恰好 1 红**，报错为
  `HTTP method of GET /api/memory/refine expected: "GET" but was: "POST"`，其余 11 例绿。
  ⚠️ 期间还发生过一次**误判**：我把期望表的键改成 `POST` 却忘了改调用点，
  第一次「负控」看到的红其实是**表与调用点不一致**造成的，而不是我以为的方法标签变异
  ⇒ 由此得出一条通式：**负控必须确认「红在预期的断言上」且「成因是变异本身」**，
  否则「恰好 1 红」也可能是别的原因。
- **负控（3 槽改名）**：把 **3 个旧套件完全看不见**的槽改名
  （`quality-distribution`.`project` → `xZZ2`、`history`.`projectPath` → `xZZ4`、
  `search`.`concept` → `xZZ9`）⇒ 新测试**恰好 3 红**，红在
  `qualityDistributionSendsExactlyProject` /
  `extractionHistorySendsExactlyProjectPathLimitAndUserId` /
  `searchSendsExactlyTheDocumentedParameters` 三个测试上，其余 9 例绿
  ⇒ 这 3 个槽**旧套件零反应**。
  `/api/health` 的**阳性对照**：把路径改成 `/api/xZZhealth` ⇒ **整个 client 套件仍 272 / 272 全绿**
  ⇒ 缺口属实。全部变异按字节还原（`git checkout` 后 `grep -c xZZ` = **0**）。
- **另一处失实陈述的注释（已修，未改行为）**：`cortex-mem-starter` 的
  `CortexMemHealthIndicator.java:10` 写「Reports UP when the backend's /actuator/health responds successfully」，
  而代码走 `client.healthCheck()` ⇒ `CortexMemClientImpl.java:346` 请求的是 **`/api/health`**。
  ⇒ 注释改为 `/api/health`，并注明「不是后端自身的 `/actuator/health`」。
- **连带记录（不单列 finding）**：Java SDK 的 `ObservationsRequest` 只有 `project` / `offset` / `limit`
  三个字段，而后端 `GET /api/observations` 还接受 `platformSource` 与 `contentSessionId`
  ⇒ **该 SDK 无法按平台过滤观测**。这是**能力缺口**而非缺陷，且补字段属**对外契约变更**
  ⇒ 记录待决，本轮不实施。
- **Status**: ✅ **已修（408 轮，Java SDK 侧，只加测试 + 改一处注释；SDK 主源码零改动）**。

## 本轮文档方向的证据（P2-211 第 408 轮标注的逐字依据）

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **形状清点（按断言，不靠眼估）**：脚本按 `git ls-files` 追踪文件逐行分类（排除 `docs/archive/`、
  `node_modules/`、`target/`、`build/`、`dist/`），把 `{template}` 分成三种形态：
  ①**URL 路径变量**（形如 `/api/.../{template}/latest`）**24** 处；
  ②**观测类型名模板**（`extracted_{template}` / `dlq_{template}`）**19** 处；
  ③**两者都不是**（叙述性提及）**4** 处。
  ⇒ **P2-211 记的「设计稿 19 处」经复核是对的**（go 15 + js 2 + python 2）——
  先前怀疑「把观测类型名模板误算成路径变量」**不成立**：
  `docs/drafts/phase-3-design/*.md`（**12** 处）与 `docs/structured-extraction*.md`（**4** 处）
  里的 `{template}` 是**观测类型名模板**，**不改**。
  ⇒ 形态 ①的 24 处按归属拆开：设计稿 **19**（本轮已修 18，刻意留 1）、
  API 文档 **2**（`docs/api-json-naming-convention.md:122/123`，属 API 文档方向）、
  **SDK 源码注释/测试名** **8**（go dto 2、js src 2、js test 2、python src 2，属各 SDK 代码方向）、
  **Java SDK 实现内 URI 模板变量** **2**（`CortexMemClientImpl.java:375/398`，**不是**对外契约）、
  **e2e 脚本的测试标签** **10**（`scripts/js-sdk-e2e-test.sh`；实测其 `curl` 用的是具体名
  `user_preference`，`{template}` 只出现在 `info`/`pass`/`fail`/`echo` 的**描述字符串**里 ⇒ **不是缺陷**）。
- **本轮实施（设计文档方向）**：路径变量 **18** 处改为 `{templateName}`
  （go 稿 **14**、js 稿 **2**、python 稿 **2**），另 python 稿的
  **`{session_id}` → `{sessionId}`** **1** 处（属 `{id}` vs `{sessionId}` 同类）⇒ 共 **19** 处。
  go 稿里**刻意保留 1 处** `{template}`：那是逐字引用 Java SDK 实现的
  `.path("/api/extraction/{template}/latest")`，其中 `{template}` 是 **RestTemplate 的 URI 模板变量名**
  （实现内部占位符），对外路径变量名是 `templateName`，展开后的 URL 相同
  ⇒ 与第 407 轮同一判断：**不改实现**，在该处加注说明。
- **另修正设计稿里把真实端点写错路径的拼法**：共 **14 个**不同路径、**18 处**出现
  （`/api/types` → `/api/mode/types`、`/api/concepts` → `/api/mode/concepts`、
  `/api/concepts/valid` → `/api/mode/concepts/valid`、`/api/types/valid` → `/api/mode/types/valid`、
  `/api/types/{typeId}/emoji` → `/api/mode/types/{typeId}/emoji`、
  `/api/types/{typeId}/validate` → `/api/mode/types/{typeId}/validate`、
  `/api/register` → `/api/cursor/register`、`/api/register/{projectName}` → `/api/cursor/register/{projectName}`、
  `/api/register/{project}` → `/api/cursor/register/{projectName}`、
  `/api/context/{projectName}` → `/api/cursor/context/{projectName}`、
  `/api/context/{projectName}/custom` → `/api/cursor/context/{projectName}/custom`、
  `/api/inject` → `/api/context/inject`、`/api/preview` → `/api/context/preview`、
  `/api/test/observation` → `/api/ingest/observation`）。
  ⇒ **判定基准是「起草当天的后端」**：用 `git show aac016a`（2026-03-24，草案同日）
  取当时的控制器路径集（**59** 条）⇒ 其中 **13** 个路径**当时根本不存在**（历史近邻存在 ⇒ 是拼错），
  另 **1** 个（`/api/concepts`）当时存在、后来才迁到 `/api/mode/concepts`
  ⇒ 两种都改为活体正确拼法（**活体 spec 逐个验证过目标路径存在且方法吻合**）。
- **§2 / §3 两节「当时为真、后来变陈旧」**（追加式审计链只可标注，**不抹除**）：
  §2「GetLatestExtraction 的双重参数传递」称模板名「同时出现在 URL path 与 query param 中」，
  并以 Java SDK 当时的实现作验证 —— 那一刻 Java SDK **确实**多发一个 `templateName` 查询参数，
  同日 `1772b33`（*fix(sdk): remove redundant templateName query param from extraction APIs*）
  把它移除（理由：后端只把 `templateName` 声明为 `@PathVariable`，多余的同名查询参数会被静默忽略）。
  §3「HealthCheck 端点差异」称 Java SDK 用 `/actuator/health` —— 起草时**确实**如此，
  同日 `a0df30a`（*fix(sdk): Java healthCheck endpoint*）改为 `/api/health`，与 Go SDK 对齐。
  ⇒ 两节改写为现状，并各加一段**带日期的历史说明**保留原轨迹；
  同时该节的 Query Params 列原先把 `templateName` 列为**查询参数**，一并改正
  （活体 spec：`GET /api/extraction/{templateName}/latest` 的 query 只有 `projectPath` / `userId`，
  `history` 另有 `limit`）。
  ⇒ **交叉印证**：Java SDK **自身的测试**早已写明「templateName should NOT be duplicated as query param」
  并断言 `doesNotContain("templateName=")` ⇒ 设计稿的说法与 SDK 自身测试**直接矛盾**。
- **对拍（活体 spec）**：把三份设计稿的**全部** `/api/...` 路径与活体 OpenAPI spec 逐条对拍，
  **路径变量名也比**（不只比 URL 形状）⇒ go 稿匹配 **41 → 52 / 61**、js **24 / 25**、python **23 / 23**。
  js 剩下 1 处（`GET /api/observations/{id}`）经**读上下文**判定为**故意的 404 引用**
  （该句用它证明后端**没有**单条观测读端点）⇒ **不是缺陷**。
  go 稿剩下 9 处中 **6** 处是**当年也从未存在的提案**（`/api/context/{project}`、`/api/embedding`、
  `/api/llm`、`/api/import/all`、`/api/memory/clear`、`/api/mode/{id}`）⇒ 已在该表的表头下加一段
  **带日期的核对说明**，指明表头「后端有但 Java SDK 未封装的端点」对这 **6** 行**并不成立**
  （它们是本设计提出的候选端点），而**不**把它们改写成别的端点 —— 那会把规划意图改掉。
  另 3 处是路径族的前缀提及（`/api/extraction/`、`/api/session/`）。
- **旁证：不是实现缺陷** ⇒ 四家**已实现** SDK 的字面路径与活体 spec 逐条核对
  （go **23**、js **19**、python **23**、java **23** 条）⇒ **零不符**
  ⇒ 设计稿里的未匹配路径确属草案提案，**不是**活体缺陷。
- **探针自身的错（三处，全部在采信其结论之前被拦下）**：
  ①「历史基线」若拿**当前** spec 比对，会把「后来迁移过的端点」误判为拼错
  ⇒ 改用 `git show aac016a` 取**起草当天**的控制器路径集；
  ②近邻匹配只按**末段**比对，会把 `/api/test/llm` 与 `/api/llm` 判成「拼错」，实际是**提案**
  ⇒ 近邻只作线索，定性**靠读上下文**；
  ③变量名比较时把 `sorted()` 返回的 **list** 与 **set** 直接 `!=` 比较
  ⇒ **相同的**名字被误报为「分歧」（`['id'] != {'id'}` 为真）
  ⇒ 修正后 VAR-NAME 分歧从 **6** 降到 **1**（即刻意保留的那处）。
  ⇒ 另加**阳性对照**：探针必须能把一个注入的假路径报成「不在 spec 里」，否则不许采信。
- **P2-211 仍未结案**：余 **2** 处（API 文档 **2**：`docs/api-json-naming-convention.md:122/123`）
  与 `{id}` vs `{sessionId}` 类 **3** 处。

## 本轮的压缩记账

- 工作文件侧：按第 **292 / 293** 轮杠杆缩短 **24** 条已结案条目的 Status 行，
  **净省 3012 字节**（每条新行由**原行的逐字切片**拼成：原括号内的**全部** token
  ——轮次、方向、限定语——与**全部**归档链接**逐字保留**，只删尾部的描述性散文）。
  ⇒ **一条含多个轮次的条目被主动跳过**（原行记着「372 / 373 / 374 / 375 轮」四个轮次，
  取第一个会丢事实）⇒ 规则收紧为「括号内只出现**一个**『轮』才可压缩」。
  ⇒ 另加**前置条件**：只有**原文自己已声明「全文逐字见某归档」**的条目才可压缩
  ——否则被删掉的 token 无法从归档里复核。
  ⇒ 压缩后 7 项硬校验全过：条目标题数不变、`Status` 行数不变、无重复分隔符、
  归档指针未被截断、归档链接数不变、归档名计数不变、字节变化可复算。
- findings 终值：**1340 行 / 148117 字节**（余量 **1883 字节**）。
