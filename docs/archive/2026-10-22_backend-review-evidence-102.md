# 后端审查证据归档 -102（2026-10-22）

> 第 401 轮（Backend 代码方向 + API 文档方向，doc round 300）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1331 行 / 148528 字节**）。
> 本文档体量：**223 行 / 16284 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。

## 本轮新写的一条（正文逐字保留在本归档）

### P2-202: P2-22 的文档层更正**不完整** —— 同一节里 Note 明写「不是 epoch 数字」，示例却就是 epoch 数字（双语各一处）

- **Scope**: → [scope-9][a6]
- **Problem**: `docs/API.md` 与 `docs/API-zh-CN.md` 的「检查注册」示例仍把 `installedAt` 写成 epoch 数字 `1709000000000`（`API.md:2425`、`API-zh-CN.md:2416`），而**同一节上方**那条 Note 明写「`installedAt` 是 **ISO-8601 时间戳字符串**，**不是** epoch 数字」——**文档与自己矛盾**。P2-22 的 Status 声称「文档层已先行更正」，实际只更正了 `GET /api/cursor/projects` 一处；它自己写下的「`GET /api/cursor/register/{projectName}` 示例同样是 epoch 数字，**需一并核对**」从未被执行。另：`CursorController`（`wc -l` **338** 行）与 `CursorService`（`wc -l` **333** 行）合计 **671** 行，对 `API.md` / `API-zh-CN.md` / `proxy/CURSOR-INTEGRATION-zh-CN.md` 发布的契约**零测试覆盖**（改动前的 **18** 个测试文件里对这两个类的提及数为 **0**）。
- **Status**: ✅ **已修（401 轮，文档方向 + Backend 代码方向）** —— 两版参考文档的「检查注册」示例改为 ISO-8601 字符串；新增 `CursorApiContractTest`（**8** 例）把「已发布的 OpenAPI example ↔ 运行时 record 组件类型」与「两版参考文档 ↔ 运行时」双向钉住；两个 Swagger 示例一并改正（见 P2-22）。

## P2-22 的正文逐字迁入本归档

- **Scope**: → [scope-9][a6]
- **Problem**: 示例写作
  `"{\"projects\":[{\"projectName\":\"my-project\",\"workspacePath\":\"/path\",\"installedAt\":1709000000000}],\"count\":1}"`，
  即把 `installedAt` 标成一个 epoch 毫秒数字。实际类型是 String：
  `CursorService.CursorProjectEntry(String workspacePath, String installedAt)`
  （`CursorService.java:52-55`），活体返回
  `"installedAt": "2026-03-18T17:50:11.192503Z"`。**照此示例生成的客户端会把该字段
  当数字解析并直接失败**；同一控制器里 `GET /api/cursor/register/{projectName}` 的
  `installedAt` 示例同样是 epoch 数字，需一并核对。
- **Status**: ✅ **已修（401 轮，Backend 代码 + 文档方向）** —— 四个发布点（两个 Swagger 示例 + 两版参考文档的「检查注册」示例）一律改为 ISO-8601 字符串；本条正文与全部证据逐字见归档 [`…-102`][a60]。

## 证据零：常驻实例跑着旧 jar —— 活体 OpenAPI 读数必须与新鲜实例对拍

本轮开局先撞上一个**读数陷阱**，它直接改变了后面所有「活体 OpenAPI」结论的可信度：

| 读数来源 | 内联 schema（带 type + example） | 其中 `type: string` 却带对象/数组 example |
|-----------|------------------------------|--------------------------------------|
| 常驻 **37777**（pid 53884，起于 2026-10-20 02:17） | **55** | **52** |
| 新鲜 **37790**（jar 建于 2026-10-22 06:20） | **52** | **0** |

- 常驻 37777 的 `/v3/api-docs` 报 **52** 处「`type: string` 带对象/数组 example」，
  看上去正是 **P2-153**（383 轮）声称已修掉的 51 处。
- 但源码全仓 `@Schema(type = "string"` 出现 **0** 处（`grep -rn` 于 `backend/src/main`），
  而 `MemoryController.java:70-72` 已写 `type = "object"` —— 与 P2-153 修完的样子一致。
- ⇒ 那 **52** 处是**常驻实例陈旧**造成的读数假象，不是源码缺陷。
- 数字也对得上：新实例的内联 schema 是 **52** 而旧实例是 **55**，差 **3** —— 正是 P2-153
  变更日志里写明的「三个数组响应的内联 example 被舍弃」。

⇒ **教训（比发现本身值钱）**：**常驻实例可能跑着旧 jar**。读活体 OpenAPI 前必须与
新鲜实例对拍，否则会把「旧 jar 的行为」写成「源码的缺陷」。本轮据此放弃了一整条
「52 处 schema 矛盾」的方向，转而去核对源码与新实例。

> ⚠️ 本条同时说明：**不要**把「活体返回 52」当作事实写进任何归档 —— 它是某个进程的属性，
> 不是仓库的属性。

## 证据一：`installedAt` 的真实类型（源码）

| 位置 | 事实 |
|------|------|
| `CursorService.java:52-55` | `public record CursorProjectEntry(String workspacePath, String installedAt)` —— `installedAt` 声明为 **`String`** |
| `CursorService.java:194-197` | `registerProject` 填的是 **`Instant.now().toString()`** ⇒ ISO-8601 字符串 |
| `CursorController.java:150-156` | `getProjects()` 把 `e.getValue().installedAt()` **原样**放进响应 map |
| `CursorController.java:331-336` | `checkRegistered()` 把 `entry.installedAt()` **原样**放进响应 map |

⇒ 两处发布示例都把一个 **`String`** 字段写成 JSON **数字**。

## 证据二：活体读数（只读，未改任何状态）

`GET http://127.0.0.1:37790/api/cursor/projects`（新鲜实例，**未调用任何写端点**），
逐条程序化核对（不手打数字）：

```
count field = 25   actual len = 25   equal = True
non-string installedAt = 0
ISO-8601 shaped = 25 / 25
value 2026-03-18T17:50:11.192503Z present = True
keys of one entry = ['installedAt', 'projectName', 'workspacePath']
```

⇒ 活体注册表 **25** 条记录，`installedAt` **无一例外**是 ISO-8601 字符串；
`count` 恒等于 `projects.length`；**文档里引用的那个值 `2026-03-18T17:50:11.192503Z`
确实就在活体注册表里**（不是编出来的示例值）。

## 证据三：全仓 `installedAt` 清扫 —— 四个发布点，与三处「epoch 数字是对的」

`grep -rn "installedAt"` 覆盖 `docs/`、`proxy/`、`backend/src/`（排除 `docs/archive/`，那里是冻结的历史）：

| 落点 | 原文 | 判定 |
|------|------|------|
| `docs/API.md:2381` | `"installedAt": "2026-03-18T17:50:11.192503Z"` | ✅ 已是 ISO-8601 |
| `docs/API.md:2388` | Note：「`installedAt` is an **ISO-8601 timestamp string**, not an epoch number.」 | ✅ 正确 |
| `docs/API.md:2425` | `"installedAt": 1709000000000` | ❌ **失实**，与上方 Note 直接矛盾 |
| `docs/API-zh-CN.md:2375` | `"installedAt": "2026-03-18T17:50:11.192503Z"` | ✅ 已是 ISO-8601 |
| `docs/API-zh-CN.md:2382` | 注意：「`installedAt` 是 **ISO-8601 时间戳字符串**，不是 epoch 数字。」 | ✅ 正确 |
| `docs/API-zh-CN.md:2416` | `"installedAt": 1709000000000` | ❌ **失实**，与上方注意直接矛盾 |
| `proxy/CURSOR-INTEGRATION-zh-CN.md:227` | `"installedAt": "2026-02-14T10:00:00Z"` | ✅ 已是 ISO-8601 |
| `proxy/CURSOR-INTEGRATION.md` | 无 `installedAt` | 遗漏（英文版整段没提这个字段），非失实 |
| `backend/…/CursorController.java:143` | Swagger example `…\"installedAt\":1709000000000…` | ❌ **失实**（`GET /api/cursor/projects`） |
| `backend/…/CursorController.java:317` | Swagger example `…\"installedAt\":1709000000000…` | ❌ **失实**（`GET /api/cursor/register/{projectName}`） |

⇒ **四个发布点**（两个 Swagger 示例 + 两版参考文档的「检查注册」示例）写错，
两个 Note 写对 ⇒ 同一节内部自相矛盾。

**同一模式的清扫（按断言清扫，不止改一处）**：全仓还有 6 处 `1709000000000`，
逐个核对后确认**都是对的**，因为那些字段在运行时**本来就是 epoch 毫秒**：

| 落点 | 运行时来源 | 判定 |
|------|-----------|------|
| `HealthController.java:56` / `:88` | `response.put("timestamp", System.currentTimeMillis())` | ✅ epoch 数字正确 |
| `StreamController.java:59` | `"timestamp", System.currentTimeMillis()` | ✅ epoch 数字正确 |
| `ExtractionController.java:108` | `obs.getCreatedAtEpoch()` | ✅ epoch 数字正确 |
| `ViewerController.java:729` | 字段名自带 `_epoch`（`started_at_epoch` / `completed_at_epoch`） | ✅ epoch 数字正确 |
| `docs/API.md:2274` / `:2280` | 同 `HealthController` | ✅ epoch 数字正确 |

⇒ **同一个数字在不同字段上有的对有的错** —— 判定依据只能是**该字段的运行时类型**，
不是数字长得像不像 epoch。

## 证据四：改这两个注解示例为什么不是「对外契约变更」

P2-22（2026-10-03，210 轮）判「⏸ 记录不修」，理由是「改 Swagger 注解即改对外发布的
OpenAPI 契约内容，属对外契约变更，留待项目决策」。本轮**推翻了那个不修决定**，依据三条：

1. **本仓库已有的先例**：**P2-153**（383 轮）——「活体 OpenAPI 有 **51** 个 schema 宣告
   `type: string` 却带对象/数组 example —— 自相矛盾」—— **✅ 已修**，改的正是
   `@Schema` 注解里对外发布的 `type`。⇒ OpenAPI 注解里的**失实陈述**在本仓库是
   **按当轮修正**的，不是「留待决策」。
2. **改的不是契约，是契约的描述**：`@Schema(type = "object", example = …)` 的 `type`
   保持 `object` 不动；只把 example 里的 `installedAt` 从裸数字改成它**实际**是的那种值。
   路径、方法、字段名、字段类型、required-ness **一项未动**。契约本身没变，
   变的是契约**描述得对不对**。
3. **它已被实测证伪**：活体 25/25 条 `installedAt` 都是 ISO-8601 字符串（证据二）。
   继续发布 epoch 数字，是在对着一个**已被测量推翻的形状**发文档。

⇒ 对照本仓库既有的例外条款「**失实陈述的文档/注释修正**可修」，这两个示例属于可修范围。

⚠️ **同时记下 P2-22 当年的那半截修法本身是不完整的**：它只更正了文档层的
`GET /api/cursor/projects`，把 `GET /api/cursor/register/{projectName}` 写成
「需一并核对」就搁下了 —— 结果是文档里 Note 说「不是 epoch 数字」、示例却是 epoch 数字。
**「先行更正」如果没有另一侧的核对，就是把矛盾从一处搬到另一处。**

## 证据五：新增测试与两个方向的负控

新增 `backend/src/test/java/com/ablueforce/cortexce/controller/CursorApiContractTest.java`
（**8** 个用例），两类互相独立：

**运行时族（5 例，地面真相）** —— 对着真实 `CursorService`（反射注入 `dataDir` 到临时目录）
与真实 `CursorController`：

| 用例 | 钉住的事实 |
|------|-----------|
| `listedProjectCarriesIso8601InstalledAt` | `installedAt` 上线即 **`String`** 且能被 `Instant.parse` 解析；`count` 恒等于 `projects.length` |
| `checkRegisteredCarriesIso8601InstalledAt` | 注册检查返回同一个 ISO-8601 字符串 |
| `checkRegisteredForUnknownProjectHasTwoFields` | 未注册时**恰好**只有 `registered` 与 `projectName` 两个字段 |
| `registerWithoutProjectNameIsRefused` | 400 + `{"success":false,"error":"projectName is required"}` |
| `registerWithoutWorkspacePathIsRefused` | 400 + `{"success":false,"error":"workspacePath is required"}` |

**发布示例族（2 例，把两侧钉在一起）** —— 直接读 `@ApiResponse` 注解，
把 example 里每个字段的 **JSON 形态**与**运行时 record 组件类型**推出的形态逐一对比。
运行时形态**从 record 组件类型推导，不写死**：

```java
for (RecordComponent component : CursorService.CursorProjectEntry.class.getRecordComponents()) {
    JsonNode publishedValue = publishedEntry.get(component.getName());
    assertNotNull(publishedValue, "… omits the registry field " + component.getName());
    assertEquals(kindOfJavaType(component.getType()), jsonKind(publishedValue), "…");
}
```

**参考文档族（1 例）** —— 扫 `docs/API.md` 与 `docs/API-zh-CN.md` 里每一个
`"installedAt":` 的取值，要求**必须是被引号包起来的字符串**且**能被 `Instant.parse` 解析**；
并带**阳性对照**（每份文件至少扫到 **2** 处，否则判扫描器自己坏了）。

### 两个方向的负控（都做过，都被证明「这条断言有牙」）

| 负控 | 手法 | 读数 |
|------|------|------|
| **发布示例侧** | 把两处 Swagger example 临时改回 `installedAt\":1709000000000` | **恰好 2 红**，且红在 `publishedProjectsExampleMatchesRuntimeFieldTypes` 与 `publishedCheckRegisteredExampleMatchesRuntimeFieldTypes`；报错信息直指「types installedAt as number while the registry stores it as a String」。5 条运行时用例**保持绿**（它们本就不依赖注解） |
| **运行时侧** | 把 `CursorService.registerProject` 的 `Instant.now().toString()` 临时改成 `String.valueOf(System.currentTimeMillis())` | **恰好 2 红**，红在两条运行时用例，异常是 `DateTimeParseException: Text '1792623560348' could not be parsed at index 0`；两条发布示例用例**保持绿** |

⇒ 两族**互相独立**，且各自都被证明会在真出问题时变红。
两次负控后均**按字节还原**（`md5` 比对：还原后 `CursorController.java` = `6ccb270d4c89ac72bbfea127be037eb9`；
`CursorService.java` 与 `HEAD` 逐字相同，`git diff --exit-code` 通过）。

## 证据六：验收

| 项目 | 读数 |
|------|------|
| 后端单测 | **192 / 192** 通过（`mvn -o test`，改动前为 184 ⇒ 本轮 +8） |
| 新测试单独跑 | `mvn -o test -Dtest=CursorApiContractTest` ⇒ **8 / 8** |
| 负控（发布示例侧） | **2 红** / 5 绿 ⇒ 断言有牙 |
| 负控（运行时侧） | **2 红** / 5 绿 ⇒ 断言有牙 |
| `docs/API.md` 结构 | 围栏 **388**（偶数）、`U+FFFD` = **0**、新变更日志行 4 个分隔竖线且正文零裸竖线 |
| `docs/API-zh-CN.md` 结构 | 围栏 **316**（偶数）、`U+FFFD` = **0**、同上 |

回归与 Phase3 的最终读数见 `patrol-rotation.md` 与 `health-check-task.md`。

## 本轮的探针/判据自身的错（三处，全部在写成发现之前拦下）

1. **零覆盖扫描器第一版的 `%s` 没有格式化** ⇒ 阳性对照当场报错（该类名的计数是
   字面量 `%s` 而不是数字）⇒ 修好后重扫，才得到可信的「**51** 个类在测试中零提及」。
   **扫描器必须带阳性对照**，否则它报「零覆盖」时你分不清是真的还是它坏了。
2. **静态推断被受控实验推翻**：曾按 Spring 的属性绑定规则推断
   `CLAUDEMEM_RATE_LIMIT_MAX_REQUESTS` 这类环境变量**不会**生效（因为
   `application.yml` 里没有 `claudemem.rate-limit.*`）。实跑
   `CLAUDEMEM_RATE_LIMIT_MAX_REQUESTS=2` ⇒ 第 **3** 个 tool-use 请求即 **429**
   （`{"error":"Rate limit exceeded","retry_after":"59"}`），未设该变量的对照组 4 个请求全 **200**
   ⇒ **变量确实生效**，静态推断作废，该方向（限流器）**没有缺陷**。
   ⇒ **「按文档说的那样去测一遍」比「按源码推断」可靠。**
3. **新测试第一版用 `getDeclaredMethod("checkRegistered")` 取方法**，而该方法带
   `String projectName` 参数 ⇒ `NoSuchMethodException` ⇒ 首次运行即红，改成按名字扫描
   `getDeclaredMethods()` 后修正。

## 压缩记账

| 口径 | 数值 |
|------|------|
| 工作文件侧**迁出**量（P2-22 的 `- **Scope**` + `- **Problem**` 共 9 行） | **657** 字节 |
| 工作文件**写入前**余量 | **1350** 字节 |
| 工作文件**写入后**余量 | **1472** 字节 |
| 工作文件**净变化**（写入 P2-202 + 链接定义 + 改写 P2-22 的 Status − 迁出 657） | **1333 行 / 148650 字节 → 1331 行 / 148528 字节**（净 **−2 行 / −122 字节**） |

⇒ 本轮**无需动用 C8 / C10 的其余杠杆**：迁出 P2-22 的正文（C10，已结案条目正文整体迁入归档）
已经足够，且**阈值不必改** —— P2-115 仍是待用户决策的流程契约变更。

> ⇒ 与归档 `-93` 的记账纪律一致：**迁出量、写入量、净变化是三个数，不可混用**。
