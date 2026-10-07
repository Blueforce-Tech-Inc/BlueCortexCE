# Backend Review Evidence 46 — 第 340 轮已结案条目的整条正文（第 340 轮迁出）

> **归档规则**：承 `-40` ~ `-45` 的体例，迁出**已完全结案、不再有待决动作**的条目
> **全文**，工作文件只保留标题与一行自足指针；条目首尾的空行留在工作文件。
>
> **归档文件创建后不得修改。**

> 本批为写入 P2-98 / P2-99 后文件**行数越线**（实测 **1524 行 / 148569 字节**）而迁出
> **2 条**：`P1-2`（第 323 轮结案，正文残余）与 `P2-97`（本轮第 340 轮结案）。
>
> **`P1-2` 此前一直留在 `## Open Findings` 段内**，尽管它的 Status 早已是 ✅；
> 同样已结案却仍列在那一段里的还有 `P2-24`（3 行）。**段落标题与实际内容不符**这一条
> 本轮不处理——它牵动的是 Open 计数口径，而那个口径**至今没有成文定义**，单方面改会
> 引入新的未定义行为，留待作者裁定。

## 块 1 / 2：P1-2 全文（第 340 轮逐字迁出）

### P1-2: Java demo 的 `?path=` **无任何路径校验**，且服务绑 `*:37778` —— 同网段可读走本机任意文件
- **Scope / Evidence**: `examples/cortex-mem-demo/.../FileReadTool.java:23-25`；三个 HTTP 入口
  `ToolsController.java:36-48`、`SessionLifecycleController.java:104-111`、`:186-217`；
  `src/main/resources/application.yml:2`（**只设 `server.port`，无 `server.address`**）。
- **Problem**: `readFile` 直接 `Files.readString(Path.of(path))`，**无根目录约束、无 `..` 检查、无白名单**；
  `?project=` 只约束**记忆捕获**的项目，**与文件读取无关**。同时 `application.yml` 未设
  `server.address`，Spring Boot 默认绑 `*:37778` —— 而后端显式设了
  `address: ${SERVER_ADDRESS:127.0.0.1}`，两者姿态相反。
- **实测记录**: 逐字迁入 [`2026-10-05_backend-review-evidence-18.md`](../archive/2026-10-05_backend-review-evidence-18.md)（第 269 轮）。
- **Severity 说明**：demo 全局无鉴权是**已知设计**（架构文档写明 "Currently no authentication
  (local development)"），但**「无鉴权」与「可读任意文件」是两件事** —— 前者只暴露记忆 API，
  后者可取走 `~/.ssh/id_rsa`、`~/.aws/credentials`、含密钥的 `.env`，同网段即可触发。
- **Status**: ✅ **已修** —— 逐字迁入 [`…-40.md`](../archive/2026-10-07_backend-review-evidence-40.md)（第 323 轮）。

## 块 2 / 2：P2-97 全文（第 340 轮逐字迁出）

### P2-97: JS SDK README 的方法表里 **3 行参数名写成 `project`**，而源码、同表另外 3 行、以及 Python SDK 全都是 `projectPath` —— **同一个 bug 在 2026-04-01 修过一次，只改了 2 行**
- **Scope / Evidence**: `js-sdk/cortex-mem-js/README.md:132,138,149` 与
  `README-zh-CN.md:130,136,147`（各 3 行，双语同型）；权威为源码 `src/client.ts:397` / `:430` / `:517`
  —— `getQualityDistribution(projectPath: string)` / `triggerExtraction(projectPath: string)` /
  `getStats(projectPath?: string)`。
- **Problem**: 那张表的写法约定就是**列出 SDK 的真实参数名**
  （`startSession(req)`、`updateSessionUserId(sessionId, userId)`、`triggerRefinement(projectPath)`、
  `getLatestExtraction(projectPath, templateName, userId?)`），而这 3 行写的是 `project`。
  **三重反证**：①**同一张表另外 3 行**用的就是 `projectPath`；②**同一份文档的必填参数表**
  也是 `projectPath`（`getQualityDistribution` / `triggerExtraction` 两行）；
  ③**Python SDK README 对同样三个方法用的是 `project_path`**
  （`README.md:92,121,126`，`-zh-CN.md` 同号）。
  **而这是上一次的漏网之鱼**：`docs/archive/2026-09-30_doc-review-history.md:150` 记载
  2026-04-01 那一轮**已经修过同一类错** ——「JS SDK README (EN+ZH) `getLatestExtraction` 和
  `getExtractionHistory` 参数名与源码不一致——`project`→`projectPath`、`template`→`templateName`」——
  **只改了那 2 行，同表另外 3 行原样留下**，直到本轮才被逮到。
  **排除「表里写的是后端 query 参数」这个解释**：`triggerExtraction` 的 query 参数恰恰
  **就叫** `projectPath`（`client.ts:433-435`），所以这三行连这一条路也圆不上。
- **Severity**: 低（TypeScript 是位置参数，调用方行为不受影响，也没有任何代码依赖这段文字）。
  但它是**已发布文档对 API 面的失实陈述**，且属同一缺陷的**复发**，不是首次笔误。
- **Status**: ✅ **已修（第 340 轮，纯文档零行为变更）** —— 6 行 `project`→`projectPath`、
  `project?`→`projectPath?`（双语各 3 行）。`git diff` = **6 增 6 删**、无其他改动；
  阴性 grep（错模式）EXIT=**1**、阳性 grep（新模式）EXIT=**0** 且 6 行齐全。
  判据为「**失实陈述 + 正确值被权威确定**」：正确值就在源码签名里，并被同表另外 3 行、
  必填参数表、Python SDK README 三方独立佐证，无需任何推断。

