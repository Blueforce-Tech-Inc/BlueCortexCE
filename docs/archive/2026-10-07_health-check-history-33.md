# 健康检查历史 33 — 第 338、339 轮（第 352 轮迁出）

> **归档规则**：承 `-19` ~ `-32` 的体例，迁出**较早轮次**的完整报告，
> 工作文件只保留 `## 第 N 轮 — ` 标题与一行自足指针；条目首尾的空行留在工作文件。
> 一次迁两轮承第 342 轮体例。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数取自**最终内容**的计算值并与 `doc-growth-check.sh` 实测对照，
> 不手写——本脚本首版用占位指针先算一遍，差了 1 行 / 1 字节（占位比最终多一个空行）。

> 本批为写入第 352 轮报告后 `docs/drafts/health-check-task.md` 达 **1618 行 /
> 102738 字节**（**行数越线** 1500，字节未越 150000）而迁出 **2 轮**：第 338、339 轮。
>
> 第 312~337 轮在工作文件里已是一行指针（更早各轮在 `-19` ~ `-30` 中），
> **第 338、339 轮是最早仍保留完整报告的两轮**。只迁第 338 轮**不够**：
> 1618 → 1563 行，仍高于 1500，故一次迁两轮。
> 轮次边界按**轮号**匹配 `## 第 N 轮 — ` 定位，**不按行号相邻**——第 342 轮教训。
>
> 迁出后实测 **1487 行 / 95524 字节**。

## 块 1 / 2：第 338 轮全文（第 352 轮逐字迁出，原 57 行）

## 第 338 轮 — 2026-10-07T09:30:00+08:00

代码方向：**Python SDK**（**零缺陷**，回归审计）；文档方向：**运维/用户指南**
（doc round 238，`docs/DEPLOYMENT.md` —— **零缺陷**，并复核 P2-89 的时效性）

### 健康预检

37777 在监听（pid 4029，保留），health 200。指纹 `5d960e9b…` / 2218 **与基线一致**，
工作区干净，HEAD = `f05c02d`。

### 代码方向（Python SDK）：回归审计

第 203 轮那处修复完好，且**写法正是当初刻意选的那种**：
`emoji` 与 `work_emoji` **各自独立取值**（`dto.py:927-928`：
`_str_field(data, "emoji")` 与 `_str_field(data, "work_emoji", "workEmoji")`），
而不是共用一次查找——共用会让 `work_emoji` 在缺 `emoji` 时**冒充 badge**，
这正是第 203 轮记录的那个坑。`to_dict()` 也两条都吐（`:940-941`）。

**顺带确认迁移集没有前进**：磁盘仍是 **V1–V19**（17 个文件，V9/V10 依旧不存在），
没有新增 V20。

### 文档方向（运维/用户指南，doc round 238）：`docs/DEPLOYMENT.md`

**先复核 P2-89 的时效性**（它是「记录不修」，需要确认没有变得更糟或已被自行修掉）：
迁移表 EN **16 行**（V1–V8、V11–V18）、ZH **16 行**、**双语彼此一致**，
磁盘 **17 个**（多 V19）——**与 P2-89 立项时完全一致，既未自行修复、也未进一步偏离**。

**环境变量覆盖度（此前没做过的一项）**：
把 `docker-compose.yml` 里 `environment:` 块下的 **24 个键** 与全部 **18 个 `${VAR}` 引用**
取并集得 **27 个变量**，逐一比对 DEPLOYMENT.md → **未记载者 0 个**。

**默认值抽查四个，逐字吻合**：

| 变量 | DEPLOYMENT.md | docker-compose.yml |
|---|---|---|
| `CLAUDE_MEM_MODE` | `code` | `${CLAUDE_MEM_MODE:-code}` |
| `SPRING_AI_OPENAI_CHAT_MODEL` | `gpt-4o` | `…:-gpt-4o` |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | `1536` | `…:-1536` |
| `SPRING_PROFILES_ACTIVE` | `prd` | `…:-prd` |

### 变更检测

本轮**未改任何代码或既有文档**，未新增 finding。指纹 `5d960e9b…` / 2218 **与基线一致**
→ **不跑完整验收、不推进基线**。37777 保留运行。

### 本轮踩到的仪器错误（一处）

解析 `docker-compose.yml` 的 `environment:` 块时，我把「遇到 `^[A-Za-z_]+:` 就结束块」
当作块结束判定 —— 而**块内的第一个键 `POSTGRES_DB:` 自身就匹配该模式**，
于是块在第一行就被判结束、环境变量提取数**恒为 0**，两版都「零遗漏」——
**一个恒零的比较看上去像完美结果，正是最危险的形态**。
改用**缩进**判定（键的缩进必须深于 `environment:` 那一行）后得真实的 27 个，
结论仍是零遗漏，但这次是**真结论**。

### 下一轮

代码方向：**JS/TS SDK**；文档方向：**API 文档**（doc round 239）。

## 块 2 / 2：第 339 轮全文（第 352 轮逐字迁出，原 78 行）

## 第 339 轮 — 2026-10-07T09:45:00+08:00

代码方向：**JS/TS SDK**（**零缺陷**，活体 wire 对拍）；文档方向：**API 文档**
（doc round 239 —— **零缺陷**，本轮把上一轮的纪律**操作化**了）

### 健康预检

37777 在监听（pid 4029，保留），health 200。指纹 `5d960e9b…` / 2218 **与基线一致**，
工作区干净，HEAD = `5fd8a06`。

### 本轮先把第 338 轮的教训变成流程

第 338 轮那次「恒零比较伪装成完美结果」之后，本轮**每个比较都先跑一条阳性对照**：
故意把字段改坏，**先证明探针报得出差异**，再采信「无差异」的结论。

### 代码方向（JS/TS SDK）：活体 wire 对拍

用 Node 原生类型剥离（v24.6.0）加载 **`dist/index.mjs`** —— 即 README Quick Start
实际导入的那份产物，也绕开了无扩展名内部导入在 Node ESM 下无法解析的问题。

**阳性对照**：把 `id` 与 `quality_score` 改坏 → 探针**报出差异 = true**，
故后续「无差异」是有效读数。

**对拍结果**（活体 `GET /api/observations?limit=2` 的真实响应）：

- 标量字段全部逐字一致：`id`/`type`/`title`/`user_comment`/`access_count`；
- 改名映射正确：`project`→`projectPath`、**`narrative`→`content`**；
- 四个 JSONB 列 wire 上是 **JSON 编码字符串**，**正确解析为数组**
  （`["fact1"]`→array(1)、`[]`→array(0)）；
- `extractedData`（wire 上是**真 dict**）完整存活；
- `null → undefined` 的映射是正常语义，非丢失。

**wire 34 键 → SDK 24 键**。逐键核对后，真正**没有对应**的是 10 个字段
（`content_hash`、`discovery_tokens`、`embedding_model_id`、`content_session_id`、
`embedding_768/1024/1536`、`step_number`、`relevance_count`、`platform_source`、
`generated_by_model`）—— 与第 173/216 轮记录的「24/34、四家丢同样 10 个」吻合，
且我实测另三家 SDK 对 `relevance_count`/`platform_source` **命中均为 0** → **四家一致**。
这个取舍**只**记在巡检归档里、SDK README 未提及，但那是**遗漏而非失实**
（README 并未声称建模全部 34 个），故不立条目。

**第 216 轮的结论仍成立**：`safeStringOrStringList` 与 `firstNonNullOr` **确未公开导出**
（属内部助手），公开的是 6 个 `safe*`，`src` 与 `dist` 两侧一致。

### 文档方向（API 文档，doc round 239）：SSE 事件与上下文端点

**SSE 事件类型对账**：后端实际发出的 `type` 值共 **8 个**
（`initial_load`、`processing_status`、`new_observation`、`new_prompt`、`new_summary`、
`item_deleted`、`session_deleted`、`text`）。API.md 的 SSE 事件表列了前 **5** 个；
`item_deleted` / `session_deleted` 由 `ViewerSessionService` 发出，
在**删除端点那一节**双语各有 1 处记载；`text` 经查是 `ContentBlock` 的块类型与
**MCP 工具响应**里的值，**不是 SSE 事件**。→ 无遗漏。

**一处看着像双语缺口、实则不是**：ZH 的 `/api/context` 有 **7** 个端点小节，EN 只有 **5** 个。
查下来 EN 对 `inject` 与 `generate` **都有完整小节**（`### Inject Context` /
`### Generate Context`，各带说明、代码块、参数表与响应示例）——
只是用的是 `### 描述名` 式版式而非 `#### METHOD \`path\`` 式，
与第 329 轮记录的「两者内容都完整，版式不同不等于有缺陷」一致。**不立条目。**

### 变更检测

本轮**未改任何代码或既有文档**，未新增 finding。指纹 `5d960e9b…` / 2218 **与基线一致**
→ **不跑完整验收、不推进基线**。37777 保留运行。

### 本轮踩到的仪器错误（四处）

①Node 原生 ESM 解析要求**显式扩展名**，而 SDK 内部用无扩展名导入 →
`ERR_MODULE_NOT_FOUND`；改从已构建的 `dist/index.mjs` 进入（也正是用户拿到的产物）。
②**我的「丢弃字段」对比把改名误算成丢弃**：`project`→`projectPath` 等 16 个字段
同时出现在「丢弃」与「新增」两侧，其实都是改名；必须先扣掉改名对才能得到真实的 10 个。
③`grep 'broadcast...|"type",\s*"[a-z_]+"'` 的**第二个分支**在全文件范围内匹配，
把 `ClaudeMemMcpTools` 里的 MCP 响应 `"type", "text"` 当成了 SSE 事件 ——
一个 alternation 分支的匹配范围没收紧，就足以造出一条不存在的「未记载事件」。
④一条 `grep` 把 `-B 6 -A 8` 写在了 `--` 之后，被当成文件名；
改用 Read 直接看原文结构。

### 下一轮

代码方向：**Demo**；文档方向：**SDK README**（doc round 240）。
