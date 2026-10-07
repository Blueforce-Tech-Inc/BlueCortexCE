# 健康检查历史 27 — 第 335 轮（第 348 轮迁出）

> **归档规则**：承 `-19` ~ `-26` 的体例，迁出**较早轮次**的完整报告，
> 工作文件只保留 `## 第 N 轮 — ` 标题与一行自足指针；条目首尾的空行留在工作文件。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数取自 `doc-growth-check.sh` 的实测值，不手写。

> 本批为写入第 348 轮报告后 `docs/drafts/health-check-task.md` 达 **1515 行**
> （行数越线；字节尚未越线）而迁出 **1 轮**：第 335 轮。
>
> 工作文件里第 312~331 轮已是一行指针（更早各轮在 `-19` ~ `-26` 中），
> **第 335 轮是最早仍保留完整报告的一轮**，故只迁这一轮即可取回余量。
> 轮次边界按**轮号**匹配 `## 第 N 轮 — ` 定位，**不按行号相邻**——第 342 轮教训。

## 块 1 / 1：第 335 轮全文（第 348 轮逐字迁出）

## 第 335 轮 — 2026-10-07T08:56:00+08:00

代码方向：**Backend**（**回归审计：已修条目零回退**，本轮换一种有牙齿的角度）；
文档方向：**SDK README**（doc round 235，`python-sdk/cortex-mem-python/README.md` —— **零缺陷**）

### 健康预检

37777 在监听（pid 4029，保留），health 200。指纹 `5d960e9b…` / 2218 **与基线一致**，
工作区干净，HEAD = `131c231`。

### 代码方向（Backend）：改做「已修条目是否还在」的系统复查

Backend 的**零覆盖文件**已在第 329 轮审尽（`SummaryEntity`、`QueueHealthIndicator`）。
本轮换一个此前**没有系统做过**的角度：**回归审计** —— 第 216 轮记录过
「修了又错回来」的实例（JS README 的 `session.response` 注释修好后，
下一次改 `session_id` 时被原样带回来）。既然本循环产出了大量「已修」条目，
**逐条确认这些修复在今天的 HEAD 上仍然成立**，比再找新覆盖面更有价值。

| 条目 | 修复内容 | 复查结果 |
|---|---|---|
| P2-64（第 273 轮） | Java SDK 两个 `isX()` 访问器加 `@JsonIgnore` | ✅ 仍在（`ObservationUpdate.java:48`、`ExtractionResponse.java` 均在） |
| P2-65（第 274 轮） | Python `is_retryable` 加宽为 `int \| BaseException` | ✅ 仍在（`error.py:135` 签名逐字一致） |
| P2-83（第 317 轮） | 11 处路径变量改为 `{sessionId}` | ✅ 仍在（`/api/session/{sessionId}` 双语各 4 处，与活体规格一致） |
| P2-84（第 319 轮） | `TrimSuffix` → `TrimRight` | ✅ 仍在（`client_impl.go:131`，且 `:122` 保留了解释性注释） |
| P2-86（第 321 轮） | 4 处改为 `{id}`，中英双语 | ✅ 仍在（`API.md` / `API-zh-CN.md` 各 6 处 `{id}`，双语一致） |

**外加一条行为级验证**（比 grep 硬）：P1-3 记的 `created_at_epoch` 排序修复 ——
活体 `GET /api/observations?limit=8` 返回的 `created_at_epoch` **严格非递增**，
换算成时间是今天 07:23:40 → 07:24:17，与第 330 轮回归测试的实际运行时间吻合。

**结论：5 条代码修复 + 1 条行为修复，全部完好，无一回归。**

### 文档方向（SDK README，doc round 235）：Python SDK README

选它是因为四份 README 里它的审计引用**最少**（1 次）。

**四项断言逐条实证**：

| 断言 | 核验方式 | 结果 |
|---|---|---|
| **Zero forced dependencies** — 仅 `requests` | `pyproject.toml` `dependencies = ["requests>=2.28"]` | ✅ |
| **453 unit tests**（226 client + 140 DTO + 87 demo） | `python3.11 -m pytest` → **453 passed**；`--collect-only` 逐文件 **226 / 140 / 87** | ✅ **分毫不差** |
| **25 methods** | 见下 | ⚠️ 见下，**不记为缺陷** |
| **Quick Start 五个调用** | `inspect.signature` 逐个比对 | ✅ 全部吻合 |

Quick Start 的五个签名逐个对上真实方法：`start_session('my-session', '/path/to/project')`
对应 `session_id, project_path, user_id=None`（位置参数可用）；
`record_observation` / `retrieve_experiences` / `build_icl_prompt` / `record_session_end`
所用的关键字参数**全部存在**。

**「25 methods」这条：查清了，但不记为 finding。**
README 的 API Reference 方法表按小节抽取出 **26 个**方法，运行时 `CortexMemClient`
的公开方法也**恰好 26 个**，**1:1 无遗漏、无多余**。
而 Features 写 25 —— 差别**恰好是一个 `close()`**，它归在 `Lifecycle` 小节，
而 Features 那句自己列举的是「Session, Capture, Retrieval, Management, Extraction」
**五类**，本就不含 Lifecycle。**两个数字各自内部自洽**，不是失实陈述。
它与 P2-90 是**同一形态**（README 里写死的总数会随代码漂移），
而 P2-90 的建议「去掉硬编码总数、只留可运行命令」已经涵盖这一条，故不另立条目。

### 变更检测

本轮**未改任何代码或既有文档**，未新增 finding。指纹 `5d960e9b…` / 2218 **与基线一致**
→ **不跑完整验收、不推进基线**。37777 保留运行。

### 本轮踩到的仪器错误（三处，全部在采信前拦下）

①解析活体观测响应时找 `observations`/`data` 键，实际是 `items`（第 334 轮已犯过同类，
本轮在**别的**脚本里再次出现，说明这个坑要写进固定检查项）；
②第一版方法表抽取把**构造参数行**（`api_key`/`timeout`/`max_retries`…）当成了方法，
虚报「6 个文档方法不在类上」；
③第二版行正则写成 `` `name` | ``，而本 README 的表格是
`` `name(args)` | `` —— **参数列表也在反引号里**，故一版都没匹配上。
另：第二版一度用了 `python3`（本机 pyenv 解析到 **3.10.6**），
与本循环「Python 必须用 python3.11」的约束不符，第三版已改回 `python3.11`。

### 下一轮

代码方向：**Java SDK**；文档方向：**设计文档**（doc round 236）。
