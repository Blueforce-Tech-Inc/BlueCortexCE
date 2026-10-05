# Backend Review — P2-30 recovered

> **为什么有这个文件**：**P2-30 的条目主体在某次压缩中被销毁，且没有进任何归档。**
> 本文件把可恢复的部分集中到一处，并如实标注哪部分是**逐字原文**、哪部分是**重建**。
> 详见工作文件中的 P2-63。**本文件创建后不得修改。**

## 事故经过（已用 git 核实）

- `git log -S'### P2-30:' -- docs/drafts/backend-review-findings.md` 只有**两条**命中：
  `1491f5b`（条目建立）与 **`adf4366`**（条目消失）。
- `adf4366`（2026-10-04，Go SDK 轮，新增 P2-40）对工作文件的改动是 **41 insertions /
  143 deletions**，其中含 `### P2-30:` 头部的删除，而该提交的 diff 中**没有任何一行新增
  内容提到 P2-30**。
- 该提交新建的归档是 `2026-10-04_backend-review-reproduction-5.md`，其头部自述内容为
  **P1-1 / P2-8 / P2-28 / P2-29（两段）/ P2-32 / P2-34** —— **不含 P2-30**。
- 压缩日志 `2026-10-04_backend-review-compression-log.md` 明确记载：第 141 轮第四批
  迁出 P2-25/26/30/31 的 **Reproduction** 时「Scope / Problem / Evidence / Status
  stayed put」。**故 P2-30 的主体本应留在工作文件中，它不是有意归档的。**
- 这与工作文件 `Processing Rules` 里记录的 **P2-47 事故（第 258 轮，两条边界断言都漏、
  「整块被销毁且未进归档」）是同一种失效模式**，当时已立为常设规则，本次是复发。

## 幸存的部分

| 部分 | 位置 | 状态 |
|---|---|---|
| `Impact` | [`2026-10-04_backend-review-reproduction-4.md`](./2026-10-04_backend-review-reproduction-4.md) 第 70 行起 | **逐字幸存** |
| `Status` | 同上 | **逐字幸存**（尽管压缩日志称 Status 未迁出，实际随 Reproduction 一并移走了） |
| `Reproduction` | 同上（含 httptest 抓到的 rawQuery 对照表） | **逐字幸存** |
| `复核记录` | [`2026-10-04_backend-review-provenance-2.md`](./2026-10-04_backend-review-provenance-2.md) 第 47 行起 | **逐字幸存** |
| `Problem` | — | **已丢失**，仅存于轮次日志的散叙述（见下） |

## 重建的 `Problem`（**非逐字**，据第 133 轮日志与上表的 `Impact` 复原）

> 第 133 轮 doc log 的原文记述：
> 「**代码方向（Go SDK）新立 P2-30（记录不修）**：同一个非法 `limit = -5`，**四家三种行为**
> ——Java **抛 `IllegalArgumentException`**（且 >100 也抛）、Go 与 JS **静默丢弃**、
> Python **照发**（负数在 Python 是真值）而后端 `Math.max(1, limit)` 把它钳成 **1**。
> **Go 自身也不一致**：`Search` / `ListObservations` 静默丢弃，而同一 SDK 的
> `GetExtractionHistory` **抛 `ValidationError`**，代码与 README 都未给理由。」

据此复原：同一个非法 `limit = -5` 在**四家 SDK 中有三种不同行为**（Java 抛错、Go/JS 静默
丢弃、Python 照发后被后端钳成 1）；**Go 自身也不一致**——`Search` 与 `ListObservations`
静默丢弃参数，同一 SDK 的 `GetExtractionHistory` 却抛 `ValidationError`，代码与 README
均未给出理由。`Impact` 与逐字幸存部分见上表所引归档。

## 该条的处置

⏸ **记录不修**（Status 逐字幸存，见 `reproduction-4.md`）：让 Go 对负数抛错会让当前能正常
返回的调用方开始失败，属公开 API 行为变更；四家对齐更属跨 SDK 契约决策。**文档层已先行
更正**：Go SDK 两份 README 现明写三者的不同处理并附四家对拍表与后端裁定值。**Go SDK 代码
一字未改。**

**仍然有效的引用方**：`python-sdk/cortex-mem-python/cortex_mem/client.py:403` 的 docstring
写着邻近的 `limit` / `offset` 站点「is recorded as **P2-30** rather than fixed here」。
第 271 轮已把 P2-30 的条目骨架恢复到工作文件，该引用重新可解析。
