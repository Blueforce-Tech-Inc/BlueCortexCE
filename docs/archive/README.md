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
