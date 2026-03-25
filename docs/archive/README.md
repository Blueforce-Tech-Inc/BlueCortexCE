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
