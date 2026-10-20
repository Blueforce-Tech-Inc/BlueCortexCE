# Backend Review Evidence 77 — 第 376 轮本轮新条目的整条正文（第 376 轮迁出）

> **归档规则**：承 `-62` ~ `-76` 的体例，迁出**本轮新写**的条目全文（写作时 findings 的
> 字节余量只剩 **119** 字节，本轮先做了一条**统一规则**的零信息损失压缩才腾出空间 ——
> C3d「唯一散文词组」去标记 **166** 处，**只删 `**` 标记、一个字未改**；
> 硬证明：去掉全文所有 `*` 后字符流与压缩前**逐字相同**），
> 工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：74 行 / 5847 字节。**
>
> 本批迁出 **P2-138** 一条新条目，并记录 **P2-136 的 Go 与 Java 侧结转**（**四家 demo 至此全部注明**）。
>
> **本轮代码方向为 Demo，文档方向为架构文档（doc round 276）。**
>
> **本轮的两条都在同一处收口**：demo 的 create 端点。Python（374 轮）、JS（375 轮）两侧
> 已注明「接受却不落库」；本轮补上 **Go** 与 **Java** 两家 ⇒ **四家 demo 全部注明**。
> 顺带查出**一句把读者引向错误心智模型的措辞**（P2-138）：Go demo README 写
> 「**Direct** observation creation」，而后端**确实**有一个真正叫「直接创建」的端点
> （`POST /api/ingest/observation`，同步落库、**落**那三个字段）—— 两个语义完全不同的端点
> 被同一个形容词盖住；**四家 demo 里只有 Go 这一家这么写** ⇒ **按断言清扫**范围只此一处。
---

### P2-138: Go demo README 把 tool-use 端点写成「**Direct** observation creation」—— 而后端**确实**有一个「直接创建」端点

- **Problem**: `go-sdk/cortex-mem-go/examples/http-server/README.md` 的端点表里有一行：
  `| POST | `/create-observation` | Direct observation creation |`
  —— **「Direct」（直接创建）** 这个词把读者引向错误的心智模型，**两层都不对**：
  1. **它不是后端的「直接创建」端点。** 后端**确实**有一个真正意义上「直接创建」的端点：
     `POST /api/ingest/observation`（`IngestionController.handleObservation`）——
     **同步落库**、`discoveryTokens = 0`、**落** `source` / `extractedData` / `prompt_number`。
     而 demo 的这个端点走的是 `POST /api/ingest/tool-use` —— **fire-and-forget 的异步入队**，
     经 LLM 抽取才产出 observation，且**不落**那三个字段。
     **两个端点的语义完全不同**，却被同一个形容词盖住。
  2. **它也不「直接」。** tool-use 路径要经 LLM 抽取才产出 observation，是**异步**的。
  ⇒ **四家 demo 里只有 Go 这一家这么写**：Python 与 JS 的 demo README 写的是
  「Record observation」/「Record observation (tool-use path)」，而 **Java demo 的 README
  根本没有端点表**（全篇零「observations」字样）⇒ **按断言清扫**范围只此一处。
- **修复**：那一行改成「**Record observation (tool-use path)**」（与 Python / JS 两家同一措辞），
  并在表下补一段说明：这个端点**不落** `source` / `extractedData`（见 P2-136），
  以及**它不是**后端的「直接创建」路径 —— `POST /api/ingest/observation` 才是那个，
  而**没有一家 SDK 暴露它**。
- **P2-136 的 Go 与 Java 侧结转（本轮）**：
  - Go demo `main.go` 的 `POST /create-observation`；
  - Java demo `ObservationsController.java` 的 `POST /demo/observations/create`。
  两处都补了与 Python / JS **同一句**的注释（零可执行行、demo 契约未动）。
  ⇒ **四家 demo 至此全部注明**「create 端点接受却不落库那三个字段」。

### 本轮文档方向：架构文档（doc round 276）—— 核实为真，未改一个字

- 审的是 `docs/ARCHITECTURE.md` 与 `docs/ARCHITECTURE-zh-CN.md` 的 **「API Layers → REST API」表**
  （逐路径与活体 OpenAPI 对拍）。
- **表里逐字列出的路径 token：EN 31 个、ZH 31 个，两个集合完全相同**。
- **零幻影端点**：两个表里的每一个路径 token 都在活体 OpenAPI 的 66 条路径里
  （含通配行的基路径本身）。**反向覆盖**：两版都覆盖活体 **65/66** 条，
  唯一未单列的 `/api/logs/clear` 在 Logs 行的**描述**里以「clear」写明 ⇒ **表是完整的**。
- **探针自身出错 1 处，先识别再采信**：第一版把「活体有、表未单列」的三条算成未覆盖 ——
  `/api/import` 与 `/api/mode` 是**通配行的基路径本身**（`/api/import/*` 的 base 就是
  `POST /api/import`），`/api/logs/clear` 在描述里；按**通配含基路径**的口径重算后即全覆盖。
  第二版在 ZH 侧用错了表头（ZH 的表头是 `| 层 | 路径模式 | 描述 |`）⇒ 抽出 0 个 token，
  改用各自表头后 EN/ZH 才是可比的 31 对 31。

### 本轮其余部分

- **Go demo 自身**：`gofmt -l` 零命中、`go vet ./...` 通过。
- **Java demo 自身**：`mvn compile` exit 0，产物 class 文件时间戳确认**真的重编过**
  （不是缓存命中）。
- **容量**：写入前 findings 只剩 **119 字节**，本轮做了一条**统一规则**的零信息损失压缩 ——
  C3d「唯一散文词组」（含汉字、无反引号、无 snake_case / camelCase 标识符、无路径、
  无代码符号、长度 ≤ 12，**全篇只出现一次**）去标记 **166** 处，**只删 `**` 标记、一个字未改**；
  硬证明：去掉全文所有 `*` 后字符流与压缩前**逐字相同**。共省 **664** 字节。
