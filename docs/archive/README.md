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
