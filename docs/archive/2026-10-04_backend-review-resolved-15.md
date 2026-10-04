# Backend Review — 已解决条目归档（第 263 轮第十五批）

> 源文件：`docs/drafts/backend-review-findings.md`　归档日期：2026-10-04
> 规则：**无条件已解决**条目（无 ⏸、无待决）可整体迁出，工作文件留一行指针。
> 先例：第 250 轮第三批（P2-11 / P2-38）、第 260 轮第十三批（P2-50/52/53/54）。

## current-status-note

> **逐轮叙述不再保留在本区块**：每条发现在 `## Open Findings` 里有完整条目；
> 逐轮上下文另存于 `patrol-rotation.md` 与 `doc-review-task.md`，历次压缩批次记在文末 `## Archived History`。
> **逐轮摘要表已整体移除**（第 258 轮，第 219–256 轮的全部行）。本文件顶部的说明
> 从一开始就写着「逐轮叙述不再保留在本区块」，而这张表恰恰违反它自己写下的规则；
> 且每一行都在 `patrol-rotation.md` 与 `doc-review-task.md` 里有**同轮、同等或更完整**的叙述，
> 属纯重复。第 254 轮已按同一理由移除第 219 轮以前的部分，本轮把剩余部分一并清掉。

> 历次压缩的批次与理由统一记在文末 `## Archived History`，
> **此处不再重复**——两处原本记着同一批压缩事件，每次压缩都要改两遍。

## entry

### P2-31: Go SDK 仍把负数 `maxChars` 发上 wire，注入被钳到 100 字符

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: `omitempty` 只省略 **0**，**负数照发**（`omitempty` 判定的是 Go 零值，
  而 `-5` 不是零值）。后端解析式是 `maxChars != null ? Math.max(100, maxChars) : 4000`
  （`MemoryController.java:154`），**判 null 不判 0**，于是负数落进 `Math.max(100, -5)`
  → **100**。**Python 曾是同一形态**（`if max_chars:` 只跳过 0），本轮已修（见下）。
- **Reproduction**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。

