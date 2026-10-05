# Backend Review Evidence 25 — P2-20 / P2-14 迁移块（第 275 轮）

> **归档规则**：工作文件 `docs/drafts/backend-review-findings.md` 保留各条目的
> **Problem**（问题是什么）与 **Status**（为什么这么定）；此处逐字保留**可复现的实测细节**。
> **归档文件创建后不得修改。**

---

## 块 1 / 2：P2-20 原始条目（逐字）

### P2-20: 全部 22 个数值查询参数都会静默接受十六进制字面量

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: Spring 的默认数字转换会**静默采纳 `0x`/`0X` 十六进制前缀**。
  规则是：先 trim，带十六进制前缀走 `Integer.decode`，否则走 `Integer.valueOf`。
  实测（活体）：`?limit=0x10` → **16 条**、`?lines=0x10` → `{"returnedLines":16}`、
  `?maxObservations=0x10` → 200、`?startEpoch=0x10` → 200、`?offset=0x2` → 200。
  状态码一律 `200`，**响应中没有任何字段表明读的是一个十六进制字面量**。
  顺带定住机制的一点：`?limit=010` 返回 **10 而非八进制 8**——若真是 `Integer.decode`
  一路到底，`010` 会被读成 8，因此是「仅对十六进制前缀走 decode」。
- **危害**：`startEpoch` / `endEpoch` 是**时间戳**，`0x` 前缀会把它们解释成一个
  1970 年附近的 epoch 毫秒值，**静默返回空时间窗而无任何报错**。`lines` 会被读成
  一个行数，`maxObservations` 会被读成一个条数。都没有校验、没有警告。
- **一个必须说明的排查陷阱**：`/api/context/timeline` 对 `?limit=abc`（**根本无法
  解析**）与 `?limit=0x3` 返回**完全相同**的 `400 {"error":"No anchor found"}`——
  那是**解析成功之后**的领域错误。若只看状态码，会误判为「该端点严格拒绝十六进制」
  而漏掉这条。真正的解析失败返回的是 Spring 默认的
  `{"status":400,"error":"Bad Request"}`（如 `/api/context/preview` 所返回的）。
  **状态码不等于原因，必须读响应体。**
- **对照**：布尔参数**不受影响**（`?includeObservations=0x1` → 400）；四家 SDK 把这些
  参数声明为数字类型，**根本无法**把十六进制字面量放到线上，故只影响直接调用
  HTTP API 的代码。
- **Status**: ⏸**已记录，不实现**。收紧会把一批当前的 `200` 变成 `400`，
  属**对外 API 契约变更**，且波及 11 个端点；需项目先决定是否值得。本轮代码方向为
  Backend，已做的是**如实记录**：`docs/API.md` + `-zh-CN` 新增「Query Parameter
  Conventions / 查询参数约定」一节，写明通用规则、完整参数清单与该排查陷阱，
  并把 `limit` 小节改为指向它而非重复叙述。


---

## 块 2 / 2：P2-14 原始条目（逐字，**含一处逐字重复句**）

> ⚠️ 块 2 的 Problem 段里，「一个从未接线的特性，只留下方法、注释和一条为它建的索引。」
> **逐字出现两次**（一次跨第 7-8 行、一次在第 9 行整行），系早前压缩残留。
> **工作文件侧已删除重复的那一行**（第 275 轮），归档侧按「创建后不得修改」原则**保留原样**。

### P2-14: `findNewObservations` 零调用方——增量抽取从未实现，却有索引为它而建

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 增量抽取**没有实现**。后端全文没有 `extraction_state`（0 命中），
  每次运行都取最新的 N 条、**没有「上次抽取之后」的过滤**。`findNewObservations`
  本身实现完好、SQL 正确，但 `backend/src/main` 中**零调用方**、连单测都没引用。
  与 P2-12（`deepRefineProjectMemories` 无调用方）同型：一个从未接线的特性，
  只留下方法、注释和一条为它建的索引。
  一个从未接线的特性，只留下方法、注释和一条为它建的索引。
- **实际行为（与文档描述不同）**：`findBySourceIn` 是 `ORDER BY created_at_epoch DESC LIMIT N`，
  所以新观测**会**进来，但超出上限的旧观测**永远不会被抽取**。既不是文档所称的
  「增量」，也不是「全量重扫」——是「每次重扫最新的 N 条」。
- **影响面**：纯成本与覆盖问题，不会返回错值；但 23.md 曾把它列为
  「primary cost reduction mechanism」，运维据此估算 token 预算会系统性偏低。
- **量化证据（第 202 轮补测）**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Status**: ⏸**已记录，不实现**。接上它需要持久化抽取状态（7.md §7.1 提议用
  `type="extraction_state"` 的观测行承载），属新增特性而非修 bug；且抽取状态的
  过期/重建语义应由项目决定。本轮已做的是**如实记录**：23.md §23.5 策略 3/4/5 全部补上
  「designed, not implemented」声明，并给出真实的候选选取路径与排序方向；
  8.md 第 5 条、0.2.md Gap 3、17.md §17.2 三处同一断言一并更正。
  **第 202 轮续做**：该次清扫**按文件逐个进行**，因此漏掉了同断言的另外两处
  （`00-quick-ref.md:14`、`15.md:211`）。本轮改为**按断言清扫**，并额外发现
  成本模型本身建立在这个不存在的机制上——23.md §23.2/§23.2b/§23.4/§23.5/§23.7
  已整体重写（月度抽取成本 $0.23 → $1.13，提炼占比 97%+ → ~89%），
  `0.3.md`、`structured-extraction.md`、`DEPLOYMENT.md` 三处同源说法一并更正。
  由此另立 **P2-17**（`EXTRACTION_MAX_BATCHES` 失效）与 **P2-18**
  （`reExtractForSession` 绕过全部上限）。

