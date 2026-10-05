# Backend Review Evidence 26 — P2-19 迁移块（第 276 轮）

> **归档规则**：工作文件保留各条目的 **Problem** 与 **Status**；此处逐字保留**可复现的实测细节**。
> **归档文件创建后不得修改。**

---

## 块 1 / 1：P2-19 原始条目（逐字）

### P2-19: Java SDK 静默吞掉 refinement / extraction 触发失败，另三家都抛错

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `executeWithRetrySilent` 返回 `void`，**任何失败都被吞掉**，只在
  日志里留一条 WARN。调用方拿到的是一个正常返回的 `void`，**无从得知触发失败**——
  精炼没跑、抽取没跑，而调用方以为跑了。
- **另三家都抛错，且是刻意为之**：
  - Go `client_methods.go:189` 甚至写了注释说明理由——
    「NOT fire-and-forget: this is an explicit user action, errors must propagate」；
  - JS `client.ts:300-306` / `:395-399` 走 `requestNoContent`，异常上抛；
  - Python `client.py:543-550` / `:670-677` 走 `_request_no_content`，异常上抛。
- **Java 自己的注释是误导的**：`executeWithRetrySilent` 的 javadoc 写着
  「Matches the Go, Python and JS SDKs」。就**重试与退避策略**而言确实一致
  （±25% 抖动、不重试 4xx/500），但**错误传播**恰恰是三家里 Java 唯一不同的那一点，
  而这正是调用方唯一能感知的部分。注释只对上了次要的一半。
- **同族的非静默差异**（不单独立项）：Java 还对 `submitFeedback` /
  `updateObservation` / `deleteObservation` / `getLatestExtraction` /
  `getExtractionHistory` 做了重试包装（`executeWithRetry` / `...Return`），
  而 Go/JS/Python 只在三个 fire-and-forget 采集方法上重试。这三个写操作
  本身**仍然抛错**，所以不是静默失败，只是重试面更宽——是否扩大属设计选择，
  与上面那条性质不同。
- **Status**: ⏸**已记录，不实现**。改这两处会**改变现有调用方的可观测行为**
  （原本被吞掉的异常会开始上抛），属对外行为契约变更，与 P2-13/P2-15/P2-16
  同一套判断；且需项目先决定这两条触发路径是否应纳入 fire-and-forget 语义。
  本轮代码方向为 Python SDK，已核实 Python 侧行为正确，故只记录。

