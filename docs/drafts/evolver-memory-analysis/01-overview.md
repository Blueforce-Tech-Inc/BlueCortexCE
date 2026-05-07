# 01 — 系统架构总览

## 1.1 三层记忆架构

EvoMap/evolver 的记忆系统由三个层次构成，每层解决不同问题：

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 3: Narrative Memory (narrativeMemory.js)              │
│  职责：人类可读的 Markdown 时间线                            │
│  格式：[YYYY-MM-DD HH:MM:SS] CATEGORY - status              │
│  用途：快速了解历史、调试、可视化                            │
└─────────────────────────────────────────────────────────────┘
                            ▲
                            │ 读取
                            │
┌─────────────────────────────────────────────────────────────┐
│  Layer 2: Memory Graph (memoryGraph.js + memoryGraphAdapter)│
│  职责：因果图推理（Signal→Gene→Outcome）                    │
│  存储：memory_graph.jsonl（追加）+ state.json（可变）       │
│  用途：指导基因选择、抑制低效路径、偏好高效路径              │
└─────────────────────────────────────────────────────────────┘
                            ▲
                            │ 追加事件
                            │
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: Signal Extraction (signals.js + learningSignals) │
│  职责：从会话日志/今日日志/MEMORY.md 中提取信号             │
│  输入：session transcript, today log, memory snippet, user  │
│  输出：14 类信号列表（含多语言支持）                        │
└─────────────────────────────────────────────────────────────┘
```

## 1.2 核心数据流

```
[Session Log] ──► [extractSignals()] ──► [Signal List]
                                              │
                         ┌────────────────────┼────────────────────┐
                         │                    │                    │
                         ▼                    ▼                    ▼
              [recordSignalSnapshot]  [getMemoryAdvice]    [selectGeneAndCapsule]
                   (写入图)              (读图推理)          (综合决策)
                         │                    │                    │
                         │                    ▼                    │
                         │            [preferredGeneId] ◄─────────┤
                         │            [bannedGeneIds]   ◄────────┤
                         │                    │                    │
                         └────────────────────┼────────────────────┘
                                              │
                                              ▼
                                    [Build Mutation Prompt]
                                              │
                                              ▼
                                    [recordHypothesis + recordAttempt]
                                              │
                                              ▼
                                    [Solidify → recordOutcomeFromState]
```

## 1.3 事件类型（Event Kinds）

memoryGraph.js 中定义的 6 类事件，全部以 JSONL 追加：

| Kind | 描述 | 关键字段 |
|------|------|---------|
| `signal` | 当前信号快照 | `signal.key`, `signal.signals`, `signal.error_signature` |
| `hypothesis` | Signal→Gene 因果假设 | `hypothesis.id`, `mutation`, `gene` |
| `attempt` | 实际执行的基因选择 | `action.id`, `selected_by` |
| `outcome` | 执行结果（成功/失败） | `outcome.status`, `outcome.score` |
| `confidence_edge` | Signal→Gene 置信度快照 | `stats.p`, `stats.decay_weight`, `stats.value` |
| `confidence_gene_outcome` | Gene→Outcome 置信度快照 | 同上（half_life=45d） |
| `external_candidate` | 外部候选资产（仅记录） | `asset.type`, `asset.id` |

## 1.4 核心设计原则

### 1.4.1 Append-Only 图存储

- **memory_graph.jsonl**：所有事件按时间顺序追加写入，永不修改或删除
- 读取时只加载最后 512KB（约 2000 条事件），零索引、零数据库依赖
- **state.json**：唯一可变状态文件，仅存储 `last_action`，记录当前 cycle 的执行上下文

### 1.4.2 因果闭包（Cause-Effect Closure）

每个进化 cycle 产生的事件链天然形成因果闭包：

```
Signal → Hypothesis → Attempt → Outcome
```

这使得：
- 给定当前信号，可以追溯"上次用哪个基因处理过类似信号，结果如何"
- 给定某个基因，可以查询"它在哪些信号下成功过/失败过"

### 1.4.3 图推理在读取时计算

边聚合（aggregateEdges）、半衰期衰减（decayWeight）、概率估计（edgeExpectedSuccess）**不在写入时计算**，而是在 `getMemoryAdvice()` 读取时实时聚合。

这样设计的好处：
- 写入极快（仅追加一行 JSON）
- 推理逻辑可以自由修改，无需迁移历史数据
- 每次读取都是基于全量历史的最新计算结果

### 1.4.4 离线优先

- 默认使用本地 JSONL 存储，无需任何外部服务
- Remote Adapter 通过 `MEMORY_GRAPH_PROVIDER=remote` 激活，写操作先本地后远程
- 远程失败时自动降级到本地，保证离线可运行

---

## 1.5 与 Claude-Mem 的本质区别

| 维度 | EvoMap/evolver | Claude-Mem |
|------|---------------|------------|
| 本质 | 因果图推理 | 向量记忆系统 |
| 记忆组织 | Signal-Gene-Outcome 事件链 | Session-Prompt-Observation |
| 推理方式 | Jaccard + 统计聚合 | 向量相似度 Top-K |
| 目标 | 指导"哪个基因/路径最有效" | 提供"上下文相关的历史记忆" |
| 衰减 | 指数半衰期（时间维度） | 无时间衰减（向量新鲜度依赖） |
| 可解释性 | 高（每条边有统计依据） | 中（向量相似度，黑盒） |

---

_Next: [02-storage.md](./02-storage.md) — JSONL 存储设计详解_
