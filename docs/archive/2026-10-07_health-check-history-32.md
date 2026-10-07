# 健康检查历史 27 — 第 337 轮（第 351 轮迁出）

> **归档规则**：承 `-19` ~ `-26` 的体例，迁出**较早轮次**的完整报告，
> 工作文件只保留 `## 第 N 轮 — ` 标题与一行自足指针；条目首尾的空行留在工作文件。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数取自 `doc-growth-check.sh` 的实测值，不手写。

> 本批为写入第 351 轮报告后 `docs/drafts/health-check-task.md` 达 **1555 行**
> （行数越线；字节尚未越线）而迁出 **1 轮**：第 337 轮。
>
> 工作文件里第 312~331 轮已是一行指针（更早各轮在 `-19` ~ `-26` 中），
> **第 337 轮是最早仍保留完整报告的一轮**，故只迁这一轮即可取回余量。
> 轮次边界按**轮号**匹配 `## 第 N 轮 — ` 定位，**不按行号相邻**——第 342 轮教训。

## 块 1 / 1：第 337 轮全文（第 351 轮逐字迁出）

## 第 337 轮 — 2026-10-07T09:20:00+08:00

代码方向：**Go SDK**（**零缺陷** —— P2-90 的数字**至今未漂移**，回归审计完好）；
文档方向：**架构文档**（doc round 237，`docs/ARCHITECTURE.md` —— **零缺陷**，三条计数断言全部精确）

### 健康预检

37777 在监听（pid 4029，保留），health 200。指纹 `5d960e9b…` / 2218 **与基线一致**，
工作区干净，HEAD = `63fdb0c`。

### 代码方向（Go SDK）：把第 336 轮的「以权威输出为准」纪律用到测试计数上

第 336 轮刚被 surefire 残留 XML 坑过一次，本轮对 Go 的计数**同样只用权威输出**，
不解析任何构建产物。

**逐模块实跑（每个 module 单独跑，因它们各有 go.mod）**：

| 模块 | 顶层测试 | 含子测试 |
|---|---|---|
| `.`（根包） | 207 | 236 |
| `dto` | 67 | 67 |
| `eino` | 8 | 8 |
| `genkit` | 13 | 13 |
| `langchaingo` | 12 | 12 |
| `examples/http-server` | 3 | 27 |
| **合计** | **310** | **363** |

**与 P2-90 当时记录的数字（顶层 310 / 含子测试 363）逐位一致** ——
即 README 的 **362 至今仍差 1，该 finding 未过时、也未进一步恶化**，无需改动处置。

**回归审计（第 186 轮那批）完好**：5 处路径参数化调用**全部**带 `url.PathEscape`
（`client_methods.go:37,220,229,282,300`）；查询参数经 `u.Query()` + `q.Set()` +
`q.Encode()`（即 `url.Values.Encode`）编码，非字符串拼接。

### 文档方向（架构文档，doc round 237）：三条计数断言逐条实证

第 108 轮曾结论「架构文档不含显式计数断言」。**该结论今天仍成立**，但文档里确实有三条，
逐条对着磁盘与活体核：

| 文档断言（EN / ZH） | 实测 | 结果 |
|---|---|---|
| `ContextController → /api/context/* (7 endpoints incl. /semantic)` | 活体 `/v3/api-docs` 中 `/api/context/*` **恰好 7 个**：`inject` / `preview` / `prior-messages` / `recent` / `timeline` / `generate` / `semantic` | ✅ |
| 「实际发布的 **32** 个 profile 是 `code`、`code--<语言>`（**30** 个变体，含 `code--chill`）与 `email-investigation`」 | `backend/src/main/resources/modes/*.json` **32** 个；`code--chill.json` **在** | ✅ |
| 回退的 `code` 副本有「同样的 **6** 种观测类型与 **7** 个概念」 | `code.json` 的 `observation_types` **6** 条、`observation_concepts` **7** 条 | ✅ |

**三条全部精确吻合**，无一含糊。`code.json` 里 6 种类型
（bugfix / feature / refactor / change / discovery / decision）与 7 个概念
（how-it-works / why-it-exists / what-changed / problem-solution / gotcha / pattern / trade-off）
与文档所述完全一致。

### 变更检测

本轮**未改任何代码或既有文档**，未新增 finding。指纹 `5d960e9b…` / 2218 **与基线一致**
→ **不跑完整验收、不推进基线**。37777 保留运行。

### 本轮踩到的仪器错误（三处）

①**包归属整体错位一格**：`go test -v ./...` 的 `ok <pkg>` 行出现在该包测试输出**之后**，
我按「读到包头就归属」的写法把包 A 的结果算到了包 B —— 改为**逐包单独跑**才消除该歧义；
②`eino`/`genkit`/`langchaingo`/`examples/http-server` 是**独立 Go module**，
从根目录 `go test ./eino` **静默返回 0 条**（不是失败，是根本没跑到），
一度让四个模块的计数显示为 0；必须 `cd` 进各自目录。
③**又一次 grep 跨行假象**：EN 的「The **32** profiles are…」把数字断在行尾，
我的行内正则没命中，一度看起来像「ZH 有这段注记而 EN 没有」的**双语不对称**——
直接读原文才发现 EN 同样有。**又一次差点把 grep 假象写成 finding。**

### 下一轮

代码方向：**Python SDK**；文档方向：**运维/用户指南**（doc round 238）。
