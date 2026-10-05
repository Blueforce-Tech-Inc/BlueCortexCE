# Backend Review Evidence 29 — P2-14 二次迁移块（第 278 轮）

> **归档规则**：工作文件保留 **Problem** 的问题陈述与 **Status**；此处逐字保留**可复现的实测细节**。
> **归档文件创建后不得修改。**

---

## 块 1 / 1：P2-14 第 275 轮压缩后的原文（逐字）

### P2-14: `findNewObservations` 零调用方——增量抽取从未实现，却有索引为它而建

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）；**第 275 轮**删除 Problem 段一处**逐字重复句**并迁出整条原文 → [`2026-10-06_backend-review-evidence-25.md`](../archive/2026-10-06_backend-review-evidence-25.md)。
- **Problem**: 增量抽取**没有实现**。后端全文没有 `extraction_state`（0 命中），
  每次运行都取最新的 N 条、**没有「上次抽取之后」的过滤**。`findNewObservations`
  本身实现完好、SQL 正确，但 `backend/src/main` 中**零调用方**、连单测都没引用。
  与 P2-12（`deepRefineProjectMemories` 无调用方）同型：一个从未接线的特性，
  只留下方法、注释和一条为它建的索引。
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

