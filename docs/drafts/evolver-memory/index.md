# EvoMap/evolver 记忆系统架构分析

> 本文档是对 [EvoMap/evolver](https://github.com/EvoMap/evolver) 记忆系统架构的深入分析，用于提炼设计思想和实现机制，为 Cortex CE 项目提供参考。
>
> **当前版本**: v1.80.0 (ab9d68e) | 本地 checkout v1.47.0 (e72778e) | 文档总数 **139** 个 .md | 最大 44,877B

## 📚 文档索引

| 章节 | 文档 | 内容概要 |
|------|------|----------|
| 1 | [架构概览](./01-architecture-overview.md) | 系统定位、核心组件、文件布局 |
| 2 | [MemoryGraph 核心](./02-memory-graph.md) | JSONL append-only 事件图谱、信号键、边聚合 |
| 3 | [信号提取系统](./03-signal-extraction.md) | 多语言信号检测、分类、信号去重与优先级 |
| 4 | [基因选择机制](./04-gene-selection.md) | 模式匹配、语义相似度、标签评分、结果建议 |
| 5 | [结果追踪与衰减](./05-outcome-tracking.md) | 推断结果、置信边、指数半衰期衰减 |
| 6 | [反思系统](./06-reflection-system.md) | 周期反思、自适应间隔、冷启动保护 |
| 7 | [叙事记忆](./07-narrative-memory.md) | 人类可读历史记录、滚动裁剪 |
| 8 | [适配器与Provider](./08-adapter-provider.md) | Local/Remote 双模式、离线优先、降级策略 |
| 9 | [存储布局](./09-storage-layout.md) | 目录结构、作用域隔离、环境变量 |
| 10 | [设计原则与借鉴](./10-design-principles.md) | 核心设计原则、对 Cortex CE 的启示 |
| 127 | [opencode 适配器 v1.80.0](./127-opencode-adapter-v180-deep-dive.md) | 第5平台适配器、session.idle 触发模式 |
| 128 | [Binary Build + Bootstrap Fix v1.79](./128-v1790-v180-binary-build-and-bootstrap-deep-dive.md) | 三阶段二进制管线、环境变量时序修复、Windows spawn fix |

## 🔑 核心架构图

```
┌──────────────────────────────────────────────────────────────────┐
│                        Signal Extraction                          │
│  (signals.js) logs/user input → raw signal list → dedup/prioritize │
└──────────────┬───────────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────────────────┐
│                      Gene / Capsule Selection                     │
│  (selector.js + memoryGraphAdapter) pattern match + semantic score │
└──────────────┬───────────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────────────────┐
│                      MemoryGraph (Append-Only)                    │
│  (memoryGraph.js)  signal + gene + outcome → JSONL events        │
│   - signal: key + signals[] + error_signature                     │
│   - hypothesis: predicted outcome                                 │
│   - attempt: action taken                                         │
│   - outcome: status + score (inferred from signals)               │
│   - confidence_edge: decay-weighted success probability           │
└──────────────┬───────────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────────────────┐
│                     Advice / Outcome Feedback                     │
│  (memoryGraphAdapter) getMemoryAdvice → preferredGeneId + banned │
└──────────────────────────────────────────────────────────────────┘
```

## 📊 关键指标

- **存储格式**: Append-only JSONL + atomic JSON state
- **事件类型**: signal / hypothesis / attempt / outcome / confidence_edge / external_candidate
- **衰减模型**: 指数半衰期（默认 signal→gene 边 30天，gene→outcome 边 45天）
- **信号去重**: Jaccard 相似度 ≥ 0.34 触发边聚合；3/8 频率抑制
- **最大回溯**: 最近 2000 条事件 / 512KB tail

## 下一步

从 [01-架构概览](./01-architecture-overview.md) 开始阅读。
