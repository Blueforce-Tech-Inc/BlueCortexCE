# Archive Directory

> **Purpose**: Historical documents that captured a point-in-time snapshot.
> **Rule**: Files here should NEVER be modified after archival. They are read-only records.

## Naming Convention

```
YYYY-MM-DD_<descriptive-name>.md
```

The date prefix reflects when the document was last relevant (not when it was archived).

## What Belongs Here

- Completed task trackers and progress reports
- One-time test reports
- Code review snapshots
- Release drafts (after actual release)
- Design iteration snapshots (after implementation is finalized)

## What Does NOT Belong Here

- **Live documents** that get periodically updated (e.g., `docs/performance-test-results.md`)
- **Design documents** still under active iteration (use `docs/drafts/`)
- **Reference documentation** (use `docs/`)

## Current Archive

| File | Original Location | Date | Description |
|------|------------------|------|-------------|
| `2026-02-12_code-review-v1.md` | `reviews/` | 2026-02-12 | Initial code review |
| `2026-02-12_code-review-v2.md` | `reviews/` | 2026-02-12 | Code review v2 |
| `2026-02-12_code-review-fix-progress.md` | `reviews/` | 2026-02-12 | Fix progress tracking |
| `2026-02-12_code-review-v5-fix-progress.md` | `backend/` | 2026-02-12 | Code review v5 fix tracking |
| `2026-02-13_task-tracker-webui-complete.md` | `TASK_TRACKER.md` | 2026-02-13 | WebUI integration task tracker |
| `2026-02-13_comprehensive-test-report.md` | `backend/` | 2026-02-13 | Comprehensive test report |
| `2026-03-13_v0.1.0-beta-release-draft.md` | `RELEASE_DRAFT.md` | 2026-03-13 | Release draft (released as v0.1.0) |
| `2026-03-17_evo-memory-implementation-progress.md` | `docs/drafts/` | 2026-03-17 | Evo-Memory implementation progress |
| `2026-03-18_spring-ai-integration-progress.md` | `docs/drafts/` | 2026-03-18 | Spring AI integration progress |
| `2026-03-20_go-java-sdk-implementation-progress.md` | `docs/drafts/` | 2026-03-20 | Go/Java SDK implementation progress |
| `2026-03-24_performance-test-results-en.md` | `performance-test-results.md` | 2026-03-24 | Performance test results (duplicate) |
| `2026-09-30_backend-review-findings-history.md` | `docs/drafts/backend-review-findings.md` | 2026-09-30 | Complete Backend review history archived during activity-log compaction |
| `2026-09-30_doc-review-history.md` | `docs/drafts/doc-review-task.md` | 2026-09-30 | Complete document-review history archived during activity-log compaction |
| `2026-10-03_doc-review-history-69-81.md` | `docs/drafts/doc-review-task.md` | 2026-10-03 | 第六十九–八十一轮逐字迁出（第三批压缩），源文件条目未改写 |
| `2026-10-03_doc-review-history-82-93.md` | `docs/drafts/doc-review-task.md` | 2026-10-03 | 第八十二–九十三轮逐字迁出（第四批压缩） |
| `2026-10-03_doc-review-history-94-107.md` | `docs/drafts/doc-review-task.md` | 2026-10-03 | 第九十四–一百零七轮共 14 条逐字迁出，该轮前为 91263 字节、加条目将越过 `MAX_BYTES=102400`；未为没有时间戳的轮次编造时间 |
| `2026-10-03_doc-review-history-108-119.md` | `docs/drafts/doc-review-task.md` | 2026-10-03 | 第一百零八–一百一十九轮共 12 条逐字迁出（第五批压缩），该文件达 99568 字节、加本轮条目将越过阈值；一百二十轮及以后保留在工作文件 |
| `2026-10-01_health-check-history.md` | `docs/drafts/health-check-task.md` | 2026-10-01 | 58 轮健康检查/巡检历史（2026-04-08 ~ 2026-10-01 00:53），达 1000 行阈值时压缩迁移 |
| `2026-10-01_health-check-history-2.md` | `docs/drafts/health-check-task.md` | 2026-10-01 | 52 轮巡检历史续（2026-10-01 05:03 ~ 05:21），二次达阈值时压缩迁移 |
| `2026-10-02_health-check-history-3.md` | `docs/drafts/health-check-task.md` | 2026-10-02 | 47 轮巡检历史续（2026-10-01 05:26 ~ 07:35），三次达阈值时压缩迁移 |
| `2026-10-02_health-check-history-4.md` | `docs/drafts/health-check-task.md` | 2026-10-02 | 第 115~148 轮（2026-10-02 10:36 ~ 13:18，17 条），四次达阈值时压缩迁移 |
| `2026-10-02_health-check-history-5.md` | `docs/drafts/health-check-task.md` | 2026-10-02 | 第 149~157 轮（2026-10-02，9 条），五次达阈值时压缩迁移；归档时顺带纠正了第 157 轮曾被误插入 156 轮正文的顺序问题 |
| `2026-10-02_health-check-history-6.md` | `docs/drafts/health-check-task.md` | 2026-10-02 | 第 158~162 轮（2026-10-02 15:12 ~ 17:55，5 条），六次达阈值时压缩迁移；按轮号提取并逐块断言自报轮号（沿用第 5 次归档确立的模式） |
| `2026-10-02_health-check-history-7.md` | `docs/drafts/health-check-task.md` | 2026-10-02 | 第 163~170 轮（2026-10-02 18:15 ~ 21:05，8 条），七次达阈值时压缩迁移；写入前活动文件 904 行、追加 112 行将达 1016 行，故先迁出；同样按轮号提取并逐块断言自报轮号 |
| `2026-10-03_backend-review-history-resolved.md` | `docs/drafts/backend-review-findings.md` | 2026-10-03 | 10 条无条件已解决条目（P1-2、P2-1~P2-7、P2-9、P2-12，273 行）逐字迁出，达 `MAX_LINES=1000` 阈值时压缩；⏸ 决策记录与 Open 条目一律保留，归档文件内载明筛选规则 |
| `2026-10-03_backend-review-history-resolved-2.md` | `docs/drafts/backend-review-findings.md` | 2026-10-03 | P1-3、P1-4 两条无条件已解决条目（79 行）逐字迁出，加 P2-30 前已达 980 行、逼近 `MAX_LINES=1000`；P2-24 因仍带 ⏸ 残留**刻意保留**，筛选规则载于归档文件内 |
| `2026-10-03_backend-review-provenance.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | P2-22～P2-27 六条的**复核记录**（出处段）逐字迁出，工作文件 969 行已达结构性饱和；Scope / Problem / Status 等**决策推理**按规则全部保留，各留一行指针；同名逐轮全文另存于 patrol-rotation.md 与 doc-review-task.md，**可无损失还原** |
| `2026-10-04_backend-review-provenance-2.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 238 轮第二批：P2-28～P2-31 四条的**复核记录**逐字迁出（P2-36 加入后达 1035 行）；承第 237 轮同规则，**前一批已声明不可修改故另建文件**；迁出前以 `git show HEAD` 逐字校验 |
| `2026-10-04_backend-review-provenance-3.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 241 轮第三批（**第九次压缩**）：P2-24 与 P2-32～P2-37 共 7 条的**复核记录**逐字迁出（P2-37 加入后达 1033 行）；**Reproduction 段一并保留在工作文件**、只迁出处段；前两批均已声明不可修改故另建；迁出前以 `git show HEAD` 逐字校验 |
| `2026-10-04_backend-review-reproduction-4.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 241 轮第四批：**首次迁移 `复核记录` 以外的段**——P2-25 / P2-26 / P2-30 / P2-31 的 **Reproduction 实测记录**逐字迁出（前三批已用尽 `复核记录`，工作文件仍达 1029 行）。依据：实测记录是**可复现的证据**、⏸ 规则保护的是**决策推理**，故 Scope / Problem / Evidence / Status 全部留在工作文件。回落 1029 → **944**，为后续轮次留出余量 |
| `2026-10-04_health-check-history-8.md` | `docs/drafts/health-check-task.md` | 2026-10-04 | 第 171～172 轮巡检报告逐字迁出（第 242 轮后达 1027 行；173～230 轮早在第 4～7 批归档，故工作文件内最旧的两份即为本批）；沿用第 5～7 批确立的模式——**按轮号提取并逐块断言自报轮号与标题一致**后才删源行。回落 1027 → **816** |
| `2026-10-04_backend-review-reproduction-5.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 244 轮第五批（**第十一次压缩**）：P1-1 / P2-8 / P2-28 / P2-29（两段）/ P2-32 / P2-34 共 **7 段** `Evidence`·`Reproduction`·`规模` 实测记录逐字迁出（P2-40 加入后达 1031 行）；承第 241 轮第四批同规则，Scope / Problem / Status 全部保留；迁出前以 `git show HEAD` 逐字校验。回落 1031 → **894** |
| `2026-10-04_doc-review-history-120-132.md` | `docs/drafts/doc-review-task.md` | 2026-10-04 | 第 120～132 轮文档审查条目（共 14 条）逐字迁出（第 244 轮后达 **102,881 字节**、越过 `MAX_BYTES=102400`；119 轮及更早已在前三批归档）；沿用 `69-81`/`82-93`/`94-107` 的**按轮次区间命名**惯例，迁出前以 `git show HEAD` 逐字校验。回落 102,881 → **51,940 字节** |
| `2026-10-04_health-check-history-9.md` | `docs/drafts/health-check-task.md` | 2026-10-04 | 第 231~240 轮巡检报告逐字迁出（第 246 轮后达 1094 行、越过 1000 行阈值），承前七批同规则；源文件保留第 241~246 轮 |
| `2026-10-04_backend-review-reproduction-6.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 247 轮第六批（**第十三次压缩**）：P2-35~P2-40 六条的 **Evidence** 段（活体/比对证据，共 56 行）逐字迁出，Scope / Problem / Status 即决策推理留在原处；findings 997 → 948 行 |
| `2026-10-04_backend-review-resolved-3.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 250 轮第三批（**第十四次压缩**）：P2-11 与 P2-38 两条**无条件已解决**条目（86 行）整体逐字迁出，源文件留一行指针；findings 1019 → 935 行 |
| `2026-10-04_health-check-history-10.md` | `docs/drafts/health-check-task.md` | 2026-10-04 | 第 241~250 轮巡检报告逐字迁出（第 251 轮写入后达 **1074 行**、越过 `MAX_LINES=1000`，`doc-growth-check.sh` 返回 `COMPACTION_REQUIRED` 退出码 2），承前九批同规则：按**轮号**匹配 `## 第 N 轮 — ` 提取（不按物理位置，因第 157 轮曾被误插入第 156 轮正文），逐块断言自报轮号与标题一致（本次 **10/10**），迁出后回读确认结尾逐字相同。回落 1074 → **261 行** |
| `2026-10-04_backend-review-compression-log.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 252 轮：把工作文件 `## Archived History` 小节（第 1~4 批压缩的记录，17 行）**逐字迁出**，源文件留 5 行指针（写入 P2-48 后达 1034 行、越过 `MAX_LINES=1000`）。**这些段落记的是文件自身的压缩历史**，各批次的筛选规则与逐字校验方式在**本 README 中早已逐条登记**，属重复记录；**37 条 ⏸ 条目的决策推理一行未迁**。回读校验结尾逐字相同 |
| `2026-10-04_backend-review-evidence-5.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 253 轮第五批（**第十六次压缩**）：P2-41 / P2-46 / P2-48 / P2-49 / P2-50 五条的 **Evidence 实测记录**（共 36 行）逐字迁出，各留一行指针；承第 241/244/247 轮同规则，**Scope / Problem / Status 一行未动**。写入两条新条目后达 1051 行、越过 `MAX_LINES=1000` |
| `2026-10-04_backend-review-evidence-5.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 253 轮第五批（**第十六次压缩**）：P2-41 / P2-46 / P2-48 / P2-50 四条的 **Evidence 实测记录**（27 行）逐字迁出，各留一行指针；承第 241/244/247 轮同规则，**Scope / Problem / Status 一行未动**。写入两条新条目后达 1046 行。**本批首次因行号整体偏移而切错段并作废重做**——见 health-check-task.md 第 253 轮报告 |
| `2026-10-04_backend-review-evidence-6.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 254 轮第六批（**第十七次压缩**）：P2-51 的 **Evidence**（`projects` 单值被静默忽略的三组活体对照，各用不同 `project_path` 避开上下文缓存）逐字迁出；Scope / Problem / Status 未动 |
| `2026-10-04_backend-review-evidence-7.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 254 轮第七批（**第十八次压缩**）：P2-52 的 **Evidence**（`mvn clean test` 前后计数与退出码对比、10 个陈旧测试类清单）逐字迁出。**同一轮还首次执行了文件自己声明的规则**——「逐轮叙述不再保留在本区块」，把第 219~250 轮的摘要表整段移除改为指针（该区块曾在 10-03~10-04 间多次越线） |
| `2026-10-04_backend-review-scope-evidence-8.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 254 轮**第八批（经用户决策批准的规则放宽）**：全部 52 段的 `- **Scope**` 与 `- **Evidence**` 逐字迁出、各留一行指针，**Problem 与 Status（决策推理）一行未动**。此前 ⏸ 规则只允许迁出实测记录，本轮用户明确批准把**范围**也一并迁出——Scope 内多为文件行号与实测细节，同属可复现证据。**这是本文件自建立以来第一次由用户拍板放宽 ⏸ 规则**，其余待决项（是否取消 `AGENTS.md`/`CLAUDE.md` gitignore、⏸ 规则其余适用范围、`docs/drafts/` 下 3 个超 50KB 文件）仍未决 |
| `2026-10-04_backend-review-status-9.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 256 轮第九批：P2-54（✅ 已修、**无条件已解决**）的 `- **Status**` 段整体迁出。**Problem 段仍留在工作文件**——第 254 轮确立的「Problem + Status 必留」规则针对的是**带 ⏸ 的待决条目**，而本条没有任何待决推理可留，故按第 250 轮「无条件已解决条目整体迁出」的先例处理 |
| `2026-10-04_backend-review-status-9.md`（同批追加） | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 256 轮同批追加 **P2-50 / P2-52 / P2-53** 三条**无条件已解决**条目的 `- **Status**` 段逐字迁出（与 P2-54 同一例外）；三条的 Problem 段均保留在工作文件 |
| `2026-10-04_backend-review-evidence-11.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 258 轮第十一批（**第二十次压缩**）：14 段**实测/复现类**小节（共 84 行）逐字迁出、各留一行指针——含 `Evidence` 3 段、`实测记录` 8 段、P2-27 已被取代的 `更正` 与 `四家能力阶梯`、一处 `同区域另两处事实`、两段 `一处探针自身出错并先识别再采信`；**Problem 与 Status（决策推理）一行未动**。同轮按文件自身声明的规则**整体移除逐轮摘要表**（第 219~256 轮全部行），该表内容与 `patrol-rotation.md`、`doc-review-task.md` 逐字重复。**本批切错段并作废重做，且这次销毁了 P2-47 整块**：脚本在原始索引上算好全部区间后**按升序改写同一个列表**，每次替换都让后续区间整体前移，导致后面的切割全部错位——P2-47 的标题与正文既未留在工作文件、也未进入归档。处置为 `git checkout HEAD` 恢复 → 删掉已生成的归档文件（归档创建后不得修改，只能重建）→ **单遍重建**（顺序遍历输出、不复用已变更的索引）→ **逐条验证：43 条 HEAD 条目无一缺失、新增恰为 P2-55/P2-56、每条未迁出的正文逐字仍在（不匹配 0 处）、14 个指针对应归档中 14 个块、全部归档指针可解析**。findings 1081 → **978 行**、103814 → **91501 字节** |
| `2026-10-04_backend-review-evidence-12.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 259 轮第十二批（**第二十一次压缩**）：22 段**证据类**小节（共 53 行）逐字迁出、各留一行指针——`Scope` 5、`Evidence` 4、`实测记录` 6、`Reproduction` 4、`Verification` 1、`量化证据（第 202 轮补测）` 1、`精确边界` 1；**Problem 与 Status（决策推理）一行未动**。**本批是第 258 轮那两条硬约束的首次实测生效**：①**单遍顺序重建**，不在原始索引上算好区间后按升序改写同一列表；②边界断言同时覆盖 `- **`、`### ` **与 `## `** 三种行首（正是上一批漏掉 `## ` 才让最后一个块吞到文件末尾）。**压缩后逐条验证**：上一提交 45 条无一缺失、新增恰为 P2-57、**每条未迁出正文逐字仍在**、22 个指针对应归档中 22 个块、全部归档链接可解析。findings 1030 → **999 行**。**本轮我的验证脚本自己错了一次**：按 `^## ` 匹配后还 `[1:]`，误把一个真实块头当成文件标题丢掉，一度报「22 指针 vs 21 块」的假警报——**数据本身是完整的**，改正匹配后才确认一致 |
| `2026-10-04_backend-review-resolved-13.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 260 轮第十三批（**第二十二次压缩**，**首次结构性处理**）：P2-50 / P2-52 / P2-53 / P2-54 四条**无条件已解决**条目（Status 含 ✅、整条无 ⏸、无待决问题，共 61 行）正文**整体逐字迁出**，工作文件保留标题 + 一行指针。先例：第 250 轮第三批对 P2-11 / P2-38 做过同样处理。**动机**：第 259 轮压缩到 999 行、距 1000 只差 1 行，第 260 轮写入 P2-58 后立即再次越线——**继续只削证据已无法长期维持**，故改用结构性杠杆。**本批我连着犯两次边界/控制流错误，且都靠逐条验证而非肉眼发现**：①第一次把最后一条的结束点取成「下一个 `## ` 行」，**吞掉了其后 5 个条目**（P2-55~P2-58、P2-48），工作文件反而变长；处置为删除错误归档 → `git checkout HEAD` 恢复 → 重写尚未提交的 P2-58 → 改用**「下一个 `### ` 或 `## `，先到者」**的边界重做；②改用 `for` 循环时**只加了指针却没跳过正文**（此前用的是 `while i=e`），结果 diff 显示**0 删除、48 纯新增**——正文根本没被移走；据 diff 定位后补删 57 行。**终验**：HEAD 中已不在工作文件的 46 行**逐字全部存在于归档**、真正丢失 0 行、条目 47（46+P2-58）、归档含 4 条标题、全部归档链接可解析。findings 1043 → **990 行** |
| `2026-10-04_health-check-history-11.md` | `docs/drafts/health-check-task.md` | 2026-10-04 | 第 260 轮：第 1~254 轮巡检报告全文（393 行）逐字迁出，源文件保留第 255~260 轮；写入第 260 轮报告后达 **1044 行**、越过 `MAX_LINES=1000`，`doc-growth-check.sh` 返回 `COMPACTION_REQUIRED`。承前十批同规则：**按轮号**匹配 `## 第 N 轮 — ` 提取（不按物理位置）并**逐块断言自报轮号与标题一致，本次 10/10**。回读确认源文件保留轮次恰为 255~260。1045 → **652 行** |
| `2026-10-04_backend-review-evidence-14.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 263 轮第十四批（**第二十三次压缩**）：P2-58 的 **Scope**（6 行）与 **Evidence**（13 行）逐字迁出、各留一行指针，承第 254 轮经用户批准的规则（⏸ 条目的 Scope + Evidence 可迁入归档，Problem / Status 一行未动）。写入 P2-59 后 findings 达 1023 行、越过 `MAX_LINES=1000`，`doc-growth-check.sh` 返回 `COMPACTION_REQUIRED` 退出码 2 |
| `2026-10-04_backend-review-resolved-15.md` | `docs/drafts/backend-review-findings.md` | 2026-10-04 | 第 263 轮第十五批（**第二十四次压缩**）：①**P2-31**（✅ 无条件已解决、无 ⏸、无待决）整条 8 行整体迁出，工作文件留一行指针——先例为第 250 轮第三批（P2-11 / P2-38）与第 260 轮第十三批（P2-50/52/53/54）；该条正文原有一处失效交叉引用「本轮已修（见下）」，指向的第 256 轮已归档状态行已不在工作文件。②`## Current Status` 下 9 行**历史注记**（记述第 258 轮整体移除逐轮摘要表的经过与理由）整体迁出——与第 252 轮压缩日志同属「文件记录自身压缩历史」，而各批次的筛选规则与逐字校验方式在本 README 中早已逐条登记。**终验**：条目 47（HEAD 47 − P2-31 + P2-59）、**HEAD 中每一条非空行或仍在工作文件、或逐字存在于两个新归档，丢失 0 行**、指针数（2+2）与归档块数（2+2）相等、全部归档链接可解析。findings 1023 → **991 行** |
| `2026-10-04_doc-review-history-134-156.md` | `docs/drafts/doc-review-task.md` | 2026-10-04 | 第 263 轮（**doc round 164**）：**趁未越线**先归档。第一百三十四–一百五十六轮共 17 条「最近完成」条目（62 行 / 66,506 字节）逐字迁出，工作文件保留**第一百五十七–一百六十四轮 8 条**并留一行指针。**触发原因**：写入第 164 轮条目后文件达 **99,865 字节**、距 `MAX_BYTES=102400` 仅余约 2.5KB，**下一轮必触发 `COMPACTION_REQUIRED`**（退出码 2，且会打断 `&&` 链导致 commit 被跳过）。前序归档为 `2026-10-04_doc-review-history-120-132.md`。**沿用第 258 轮确立的两条硬约束**：单遍顺序重建（不在原始索引上算好区间后按升序改写同一列表）、边界断言覆盖 `- **最近完成**` 与 `- **下一方向**` 等多种行首而非只判 `## `。**先干跑后落盘**，干跑确认切口后紧接第一百五十七轮且为合法边界。**终验**：HEAD 的非空行除**被有意替换的 `- **下一方向**` 一行**（运维/用户指南 → API 文档）外**全部逐字保留**（丢失 0 行）、条目 8 + 17 = 25 与 HEAD 的 24 + 本轮新增的 164 一致、指针在位、归档链接全部可解析。99,865 → **33,585 字节** |
