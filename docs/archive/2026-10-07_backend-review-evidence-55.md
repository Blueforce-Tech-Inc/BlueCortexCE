# Backend Review Evidence 55 — 第 353 轮已结案条目的整条正文（第 353 轮迁出）

> **归档规则**：承 `-50` ~ `-54` 的体例，迁出**已完全结案、不再有待决动作**的条目
> **全文**，工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）；
> 条目首尾的空行、以及夹在其后的 HTML 注释，一律原样留在工作文件。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**（第 340 轮 `-46` 归档曾因手写
> 字节数出错而整份返工，本批起数字一律取自计算值，不再手写）。

> 本批迁出 `P2-18` / `P2-19` 两条，**全部是 ⏸ 已记录不修**
> （对外契约变更或新增能力，按规则不单方面实施），对本循环**没有待办动作**，故可安全迁出。
> 选它们是因为 `backend-review-findings.md` 已到 **1413 行 / 151155 字节**（上限 1500 / 150000），
> 字节余量不足以为本轮两条新条目让路；未结案条目一律不动。
>
> 迁出前实测 **1413 行 / 151155 字节**，迁出后 **1385 行 / 148728 字节**。

## 块 1 / 2：P2-18 全文（第 353 轮逐字迁出，原 15 行）

### P2-18: `reExtractForSession` 绕过全部抽取上限，整会话一次性送入 LLM
- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 这是结构化抽取的**第二个活入口**，但它**完全不走批处理循环**。
  候选来自 `findByContentSessionIdOrderByCreatedAtEpochAsc(sessionId)`——
  一个无 `LIMIT` 的派生查询，返回该会话的**全部**观测；随后对每个启用模板直接
  `extractByTemplate(template, filtered, priorJson)` **一次调用**，
  整个 `filtered` 列表进同一个 prompt。`initialRunMaxCandidates`、
  `maxObservationsPerBatch`、`maxBatchesPerTemplate` **三个上限一个都不生效**。
- **影响**: 一个含 60 条匹配观测的会话会产生一次输入约为 20 条批量 **3 倍**的
  调用，而所有成本文档的「每次调用」价格都是从 20 条批量推出的。更严重的是
  **无上界**——会话越长，单次 prompt 越大，直到超出模型上下文窗口才失败。
  失败被 `catch (Exception)` 吞掉并记日志（`:161-162`），调用方看到的仍是成功。
- **Status**: ⏸**已记录，不实现**。接入上限会改变该端点的既有行为，属对外契约变更。
  本轮已在 `23.md` §23.4 记录该入口未被任何成本表计价，并在
  `StructuredExtractionService` 的既有注释中保持路径事实不变。

## 块 2 / 2：P2-19 全文（第 353 轮逐字迁出，原 19 行）

### P2-19: Java SDK 静默吞掉 refinement / extraction 触发失败，另三家都抛错
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）；
  **第 276 轮再迁出**另三家的**逐文件行号引用**与其原文注释 → [`2026-10-06_backend-review-evidence-26.md`](../archive/2026-10-06_backend-review-evidence-26.md)。
- **Problem**: `executeWithRetrySilent` 返回 `void`，**任何失败都被吞掉**，只在
  日志里留一条 WARN。调用方拿到的是一个正常返回的 `void`，**无从得知触发失败**——
  精炼没跑、抽取没跑，而调用方以为跑了。
  **另三家都抛错，且是刻意为之**（逐条行号见归档）——Go 写了注释说明理由
  「NOT fire-and-forget: this is an explicit user action, errors must propagate」；
  JS 与 Python 走各自的 no-content 请求路径，异常上抛。
- **Java 自己的注释是误导的**：`executeWithRetrySilent` 的 javadoc 写着
  「Matches the Go, Python and JS SDKs」。就**重试与退避策略**而言确实一致
  （±25% 抖动、不重试 4xx/500），但**错误传播**恰恰是 Java 唯一不同的那一点，
  而这正是调用方唯一能感知的部分。注释只对上了次要的一半。
- **同族的非静默差异**（不单独立项）：Java 还对 `submitFeedback` / `updateObservation` / `deleteObservation` /
  `getLatestExtraction` / `getExtractionHistory` 做了重试包装，Go/JS/Python 只在三个 fire-and-forget
  采集方法上重试。这些写操作本身**仍然抛错**，不是静默失败，只是重试面更宽——是否扩大属设计选择。
- **Status**: ⏸**已记录，不实现**。改这两处会**改变现有调用方的可观测行为**
  （原本被吞掉的异常会开始上抛），属对外行为契约变更，与 P2-13/P2-15/P2-16
  同一套判断；且需项目先决定这两条触发路径是否应纳入 fire-and-forget 语义。
