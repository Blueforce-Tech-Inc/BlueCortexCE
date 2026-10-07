# Backend Review Evidence 53 — 第 352 轮已结案条目的整条正文（第 352 轮迁出）

> **归档规则**：承 `-50` ~ `-52` 的体例，迁出**已完全结案、不再有待决动作**的条目
> **全文**，工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）；
> 条目首尾的空行、以及夹在其后的 HTML 注释，一律原样留在工作文件。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**（第 340 轮 `-46` 归档曾因手写
> 字节数出错而整份返工，本批起数字一律取自计算值，不再手写）。

> 本批迁出 `P2-10` / `P2-13` / `P2-14` / `P2-15` 四条，**全部是 ⏸ 已记录不修**
> （对外契约变更或新增能力，按规则不单方面实施），对本循环**没有待办动作**，故可安全迁出。
> 选它们是因为 `backend-review-findings.md` 已到 **1466 行 / 148444 字节**（上限 1500 / 150000），
> 字节余量不足以为本轮两条新条目让路；未结案条目一律不动。
>
> 迁出前实测 **1466 行 / 148444 字节**，迁出后 **1415 行 / 143770 字节**。

## 块 1 / 4：P2-10 全文（第 352 轮逐字迁出，原 20 行）

### P2-10: 四个 ingest 端点对项目路径的必填性不一致
- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 同一族端点对同一个语义字段给出两种契约。实测（对运行中的后端）： | 端点 | 缺失/空白 `project_path`（或 `cwd`） |
  |------|------------------------------------| | `POST /api/ingest/observation` | **400** `Missing required field:
  project_path` | | `POST /api/ingest/tool-use` | **200** `{"status":"accepted"}` | | `POST /api/ingest/user-prompt` |
  **200** `{"status":"ok"}` | | `POST /api/ingest/session-end` | **200** `{"status":"ok"}` |
  `cwd` 省略与发送 `"cwd": ""` 行为相同。
- **实际影响**：仅影响直接使用 HTTP API 的调用方——四家 SDK 均已在客户端拒绝空
  `project_path`（本轮刚为 Python 补齐），因此 SDK 路径不会触发。直接调 API 的调用方
  会得到一条项目路径为空的记录：写入成功、返回 200，但该记录不会出现在任何按项目
  过滤的查询里，**且不会有任何错误提示**。`user-prompt` 的 `prompt_text` 同样缺失即
  接受（仅 `session_id` 被强制）。
- **未修的原因**：收紧另三个端点属于**对外 API 契约变更**，会影响既有直接调用方与
  薄代理 `wrapper.js` 的边界输入，属产品决策；本轮代码方向为 Python SDK，按轮换
  纪律不在本轮动手。
- **Status**: ⏸ 已记录不修（2026-10-02，第 175 轮 API 文档轮发现）。文档方向已在同轮
  于 `docs/API.md` / `docs/API-zh-CN.md` 三个端点各加一段说明，逐条写明「缺失与空串
  都会被接受」「`/api/ingest/observation` 是唯一严格的那个」「SDK 会在客户端拦截」，
  使读者不必自行推断这层差异。API 文档原本对两者的「必填」标注**是正确的**
  （observation 标 ✅、另三个标 ❌），本轮只是补上未言明的后果。

## 块 2 / 4：P2-13 全文（第 352 轮逐字迁出，原 20 行）

### P2-13: Spring AI 集成无法按用户隔离记忆——会话上下文里没有 userId
- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 后端**支持**按用户隔离 ICL 记忆（第 194 轮实测：同一项目下 alice 返 1 条
  经验、bob 返 0 条），`ICLPromptRequest` / `ExperienceRequest` 也都带 `userId`，
  `DefaultMemoryRetrievalService` 更是**已经实现并透传** `userId`。
  但真正把记忆注入 Agent 的两个组件——`CortexMemoryAdvisor` 与
  `CortexMemoryTools` 的两个读方法——**结构上做不到**：
  它们唯一的会话级状态是 `CortexSessionContext`，而那个类里根本没有 `userId` 字段。
  因此在多用户部署中，**自动注入给每个 Agent 的 ICL 上下文是项目级的、所有人相同**。
  手工调用 `client.buildICLPrompt(...)` 并自行设置 `userId` 是可行的——
  受影响的是自动路径。
- **影响面**：与 P2-11（错模板名不被拒绝）不同，这不是静默错值，而是**缺少一个能力**；
  后果是不同用户之间**记忆串味**（用户 A 的偏好会出现在用户 B 的提示里），
  在「每用户独立档案」类应用中属于数据可见性问题。
- **Status**: ⏸**已记录，本轮不实现**。修它需要给 `SessionInfo` 加字段、给 `begin()`
  加重载、把 userId 从调用方一路串到 advisor 与工具，属于**新增能力**而非修 bug；
  且 `CortexSessionContextBridgeAdvisor` 需要知道从何处取 userId（会话 id？应用配置？，
  还是新的 `begin()` 入参），这个选择应由项目决定而不是由巡检轮次决定。
  本轮已做的是**如实记录**：`cortex-mem-spring-integration/README.md` 与 `README-zh-CN.md`
  新增多用户段落，写明自动路径不做用户隔离、哪些端点其实认 `userId`、以及可用的手工做法。

## 块 3 / 4：P2-14 全文（第 352 轮逐字迁出，原 16 行）

### P2-14: `findNewObservations` 零调用方——增量抽取从未实现，却有索引为它而建
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）；**第 275 轮**删除 Problem 段一处**逐字重复句**并迁出整条原文 → [`2026-10-06_backend-review-evidence-25.md`](../archive/2026-10-06_backend-review-evidence-25.md)；**第 278 轮**再迁出其 Status 的压缩过程原文 → [`2026-10-06_backend-review-evidence-29.md`](../archive/2026-10-06_backend-review-evidence-29.md)。
- **Problem**: 增量抽取**没有实现**。后端全文没有 `extraction_state`（0 命中），
  每次运行都取最新的 N 条、**没有「上次抽取之后」的过滤**。`findNewObservations`
  本身实现完好、SQL 正确，但 `backend/src/main` 中**零调用方**、连单测都没引用。
  与 P2-12（`deepRefineProjectMemories` 无调用方）同型：一个从未接线的特性，只留下方法、注释和一条为它建的索引。
- **实际行为（与文档描述不同）**：`findBySourceIn` 是 `ORDER BY created_at_epoch DESC LIMIT N`，
  所以新观测**会**进来，但超出上限的旧观测**永远不会被抽取**——既不是文档所称的
  「增量」，也不是「全量重扫」，是「每次重扫最新的 N 条」。**纯成本与覆盖问题，不会返回错值**；
  但 23.md 曾把它列为「primary cost reduction mechanism」，运维据此估算 token 预算会系统性偏低。
- **量化证据（第 202 轮补测）**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Status**: ⏸**已记录，不实现**。接上它需要持久化抽取状态（7.md §7.1 提议用
  `type="extraction_state"` 的观测行承载），属新增特性而非修 bug，且过期/重建语义应由项目决定。
  **已做的是如实记录**：23.md §23.5 策略 3/4/5 补上「designed, not implemented」声明并给出真实的
  候选选取路径与排序方向；8.md 第 5 条、0.2.md Gap 3、17.md §17.2 三处同一断言一并更正。
  **按断言清扫的逐处经过**：逐字迁入 [`…-32.md`](../archive/2026-10-06_backend-review-evidence-32.md)（第 282 轮）。

## 块 4 / 4：P2-15 全文（第 352 轮逐字迁出，原 15 行）

### P2-15: `save_memory` 的共享会话是 check-then-act，并发下必然丢失一次保存
- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `mem_sessions.content_session_id` 上有**活体确认**的唯一约束
  （`pg_constraint`: `mem_sessions_content_session_id_key UNIQUE (content_session_id)`），
  而这里是典型的 check-then-act：两个并发的 `save_memory` 调用都会查不到、都会走
  `orElseGet` 去插入，第二次必然撞唯一约束。异常被外层捕获，返回
  `{"success": false, "error": "Failed to save memory: ..."}`。
- **影响面**：**不会写脏数据、也不会假报成功**（安全方向），但一次本该成功的
  记忆保存被报成失败，且信息误导——调用方看到的是「保存失败」而不是「并发冲突，请重试」。
  重试即可成功（此时会话已存在），所以属于瞬时可恢复的伪失败。
  MCP 工具调用可由 agent 并行发起，多客户端同理，因此并发是现实场景而非理论场景。
- **Status**: ⏸**已记录，不实现**。常规修法是捕获 `DataIntegrityViolationException`
  后重新查询会话再继续，但那要在 `orElseGet` 的懒执行路径里插入一次重试，
  改变的是该工具的错误语义与重试行为，属应由项目拍板的契约问题而非巡检轮次的修 bug。
  与 P2-13/P2-14 同一套判断。
