# Go SDK & Java SDK 实施进度

## Phase A: Java SDK 补充（当前阶段）

### A1: P0 功能

| 任务 | 状态 | 产出 |
|------|------|------|
| Search API | 🔄 开始实施 | SearchRequest.java + Client方法 |
| ListObservations API | ⏳ 待实施 | ObservationsRequest.java + Client方法 |
| BatchObservations API | ⏳ 待实施 | Client方法 |

### A2: P1 功能

| 任务 | 状态 | 产出 |
|------|------|------|
| GetProjects | ⏳ 待实施 | Client方法 |
| ListSummaries | ⏳ 待实施 | SummariesRequest.java |
| ListPrompts | ⏳ 待实施 | PromptsRequest.java |
| GetStats | ⏳ 待实施 | Client方法 |
| GetVersion | ⏳ 待实施 | Client方法 |

### A3: Demo 更新

| 任务 | 状态 | 产出 |
|------|------|------|
| 更新 Demo 展示新 API | ⏳ 待实施 | SearchController等 |

## Phase B: Go SDK 实施（待Phase A完成后开始）

| 任务 | 状态 | 产出 |
|------|------|------|
| 核心包 | ⏳ 待实施 | cortex-mem-go/ |
| 集成层 | ⏳ 待实施 | eino/, langchaingo/, genkit/ |
| Demo 项目 | ⏳ 待实施 | examples/ |

## 进度日志

- 2026-03-24 18:12: 开始 Phase A 实施，首先实现 Search API
