# Backend Review Evidence 63 — 第 363 轮本轮新条目的整条正文（第 363 轮迁出）

> **归档规则**：承 `-62` 的体例，迁出**本轮新写**的条目全文（写作时 findings 的字节余量已近于零），
> 工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来
> 改变自身长度，本批**迭代至不动点**后再写盘。
>
> 本批迁出 `P2-117` / `P2-118` 两条——**本轮新写**的条目，写完即整体迁出，
> 工作文件各留标题与 `- **Status**` 一行（含归档指针）。**正文逐字保留在本归档**。
>
> 另：本批同时给 **P2-43** 与 **P2-116** 补上各自的标注——**P2-116 是 P2-43 的重复立条**，
> 第 362 轮未能先查到 P2-43；标注已就地写入工作文件，**不改写任何历史正文**。
>
> 迁出前实测 **1429 行 / 144507 字节**；本归档实测 **96 行 / 8249 字节**。

### P2-117: JS SDK 的源码注释与双语 README 仍断言「四家里只有 JS 能把字符串字段清空为 NULL，Python 跳过 `None`」——而该断言早在第 247 轮就被本仓库自己证伪

- **Scope / Evidence**:
  - `js-sdk/cortex-mem-js/src/dto/observation.ts:47-49`（修前）——「This makes JS **the only SDK**
    that can clear a string field to NULL: Java omits nulls via `@JsonInclude(NON_NULL)`, Go via
    `omitempty`, and **Python skips `None`**」
  - `js-sdk/cortex-mem-js/README.md:233-234`（修前）——「This SDK is **the only one of the four** that
    can clear a string field to NULL: … and **Python skips `None`**」
  - `js-sdk/cortex-mem-js/README-zh-CN.md:222-223`（修前）——「四家 SDK 中**只有本家能把字符串字段
    清空为 NULL**：Java 用 `@JsonInclude(NON_NULL)` 省略 null，Go 用 `omitempty`，**Python 直接跳过
    `None`**」
  - **证伪来源就在本仓库**：`docs/drafts/backend-review-findings.md` 的 **P2-43**（第 247 轮立）已实测
    「Python SDK 两种调用风格对 `None` 语义相反」，其 Problem 里明写
    「**故 P2-27 能力表把 Python 判「✗」只对 dataclass 路径成立**」
  - 第 362 轮补上的活体端到端证据：**kwargs 路径 `content=None` 之后 `mem_observations.content` 列
    实为 NULL**（`psql` 直查），见 **P2-116**
- **Problem**: 三处复述点仍在断言一件**已被本仓库自己证伪过**的事。证伪发生在第 247 轮
  （2026-10-04），**16 天后这三处一字未改**——因为当时的处置只落在 Python 一侧
  （且那次的「已加注」直到第 362 轮才发现**从未落盘**）。
  **「修掉一个说法 ≠ 修掉复述它的每个说法」在跨 SDK 边界上的又一次复发**：证伪写在 findings 里，
  而复述在**另一个 SDK 的源码注释与用户文档里**，两边从未被对拍。
  **本轮独立核实了断言里另外两半**（不沿用他人结论）：Java 的
  `ObservationUpdate` 是带 `@JsonInclude(JsonInclude.Include.NON_NULL)` 的 record
  （`cortex-mem-client/.../dto/ObservationUpdate.java:19`）⇒ **null 被省略、发不出 null** ✓；
  Go 的 `ObservationUpdate` 是 `Title *string` + `json:"title,omitempty"`
  （`go-sdk/cortex-mem-go/dto/observation.go:100-109`）⇒ **指针没有 null 的表达** ✓。
  **故错的只有 Python 那一半**，Java/Go 两半为真、保留。
- **已修（注释与文档，零行为变更）**: 三处断言改为如实描述——Java 用 `@JsonInclude(NON_NULL)`、
  Go 用 `omitempty`，**这两家都发不出 null**；Python 的**数据类形式**跳过 `None`，但其
  **关键字参数形式**把值原样拷上 wire，故 **Python 也能把字符串字段清空为 NULL**，
  **本家并非四家中唯一能做到的一家**。改后按断言清扫：全仓（排除 `dist/` 与 `node_modules/`）
  对该措辞**零命中**；`dist/` 是 gitignore 的构建产物，已 `npm run build` 重建，
  **dist 产物里已含更正文本**（否则发布到 npm 的包里仍带着那句错话）。
  `npm run lint`（`tsc --noEmit`）**退出 0**、`vitest` **259 passed**（3 个测试文件）。
- **Status**: ✅ **已修（第 363 轮，零行为变更）** —— 属**失实陈述的文档修正**，
  不涉任何行为、wire 格式或公开 API 变更。

### P2-118: API 文档 2026-10-07 的 changelog 行把请求体表归在 `POST /api/memory/observations` 名下——而该端点根本不存在

- **Scope / Evidence**:
  - `docs/API.md:2948`（修前）与 `docs/API-zh-CN.md:2946`（修前）——2026-10-07 那条写
    「The `POST /api/memory/observations` request table is deliberately untouched: it carries its own
    Required column…」／「`POST /api/memory/observations` 的请求体表**刻意不动**：它自带 Required 列…」
  - 活体 `/v3/api-docs`：`/api/memory/observations/{id}` **只有 `PATCH` 与 `DELETE`**，
    **没有任何 `POST /api/memory/observations`**；创建观测的端点是 `POST /api/ingest/observation`
  - 那张「自带 Required 列」的请求体表实际在 `docs/API.md:509`
    （`| Field | Type | Required | Description |`），属于 **`POST /api/ingest/observation`** 段（`:501`）
  - 全库检索：该错名**只出现在这两行**，别处没有
- **Problem**: 一条 changelog 记录把请求体表**归错了端点**，而那个端点**根本不存在**。
  该行的**实质内容仍然成立**（请求体表确实自带 Required 列、确实被刻意保留原样），
  **错的只是端点名**。但它出现在**两个语言版本的更新日志**里，且指向一个**活体没有的端点**——
  与本项目一直在清理的「幻影端点」同类。
- **处置**: 按**更新日志的既有体例**（第 120 轮「两版 API changelog 各补一条**带日期的记录**」），
  **不改写 2026-10-07 那行**（它是带日期的历史记录），而在双语 changelog **各补一条 2026-10-20 的
  更正行**，写明正确的端点是 `POST /api/ingest/observation`，并写明原行实质内容不变、
  只是端点名写错。
- **Status**: ✅ **已修（第 363 轮，纯文档零行为变更）** —— 失实陈述的文档修正；双语同步。

### 文档方向（API 文档，doc round 263）的其余核验结果——零缺陷

- **端点清单双向对拍**（活体 `/v3/api-docs` **66 条路径**）：**双语各 66 条全部有记载，遗漏 0**。
- **正文零幻影端点**：文档内 91 个路径 token 中，25 个「活体没有」的候选**逐条查原文后全部证伪** ——
  7 个是**文件路径示例**（`/Users/dev/myproject`、`/path/to/project/CLAUDE.md`、`/src/auth.ts` 等），
  1 个是**错误响应示例**（`/api/memory/observations/not-a-uuid`），
  1 个是 **OpenAPI 自身的路径**（`/v3/api-docs`），
  1 个是 **changelog 里的历史散文**（`POST /api/memory/observations`，即 P2-118），
  其余 15 个是**正则截断出的片段**（如 `…/{templateName}/latest` 的后半截 `/latest`）。
  与第 160 轮同型：**「逐条查原文后全部证伪」**，故不是 25 个缺陷。
- **迁移与端点相关的历史结论仍然成立**：`docs/DEVELOPMENT.md:405` 与中文版的
  「Flyway migrations (V1–V8, V11–V19)」与 `db/migration/` 实测的 17 个文件**双向吻合**、
  V9/V10 确认缺席（与第 345 轮一致）。
- **本轮探针自身三处出错，全部在写入前拦下**：
  ①**路径正则只认 `/api/` 开头** ⇒ 活体的 `/stream` 被误报成「文档遗漏」，
  而中文版 `API-zh-CN.md:2388` 明明写着 `#### GET \`/stream\``——**改用「任意首斜杠路径」后遗漏为 0**；
  ②**遗漏检测的阳性对照写错**：`.replace(..., 1)` 只替换了 `/api/modes` 的 **12 处出现中的 1 处**，
  于是对照「失败」——**失败的是对照本身**，重写为全部替换后 **3/3 触发**；
  ③**路径正则的 lookbehind 未排除 `}`**，把 `…/{templateName}/latest` 的后半截
  `/latest` 当成独立路径，制造出 15 个假「幻影」。
