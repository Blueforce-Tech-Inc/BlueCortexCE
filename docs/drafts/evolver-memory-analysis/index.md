# EvoMap/evolver 记忆系统架构分析

> 分析对象：`/Users/yangjiefeng/Documents/EvoMap/evolver`
> 分析时间：2026-05-03
> 最后更新：2026-05-07
> 代码版本：基于 src/gep/ 目录核心文件

---

## 总览

EvoMap/evolver 的记忆系统是整个自进化框架的"因果记忆中枢"。它不依赖向量数据库，而是通过**事件溯源（Event Sourcing）+ JSONL 追加日志**实现了一个轻量级、可审计的因果图（Cause-Effect Graph）。

**核心设计哲学**：记忆不是"存储检索"，而是"因果推理"——每次进化（evolution cycle）的输入（Signal）、决策（Gene）、输出（Outcome）都被完整记录，并通过图推理指导下一轮基因选择。

---

## 文档结构

| 文档 | 内容概要 |
|------|---------|
| **[01-overview.md](./01-overview.md)** | 系统架构总览、三层设计、核心数据流 |
| **[02-storage.md](./02-storage.md)** | JSONL 存储设计、状态管理、文件布局 |
| **[03-signals.md](./03-signals.md)** | 信号提取机制、14 类信号、去重与饱和检测 |
| **[04-retrieval.md](./04-retrieval.md)** | Jaccard 匹配、半衰期衰减、Laplace 平滑、图聚合 |
| **[05-gene-selection.md](./05-gene-selection.md)** | 基因评分（模式/语义/标签）、抗性模式、学习历史 |
| **[06-saturation.md](./06-saturation.md)** | 饱和检测、回路熔断、降级策略 |
| **[07-adapter.md](./07-adapter.md)** | Adapter 模式、本地/远程双模式、前向兼容 |
| **[08-reflection.md](./08-reflection.md)** | 周期性反思机制、叙事记忆 |
| **[09-solidify-learning.md](./09-solidify-learning.md)** | Solidify 机制、PRM 评分、表观遗传标记、基因自学习 |
| **[10-skill-distillation.md](./10-skill-distillation.md)** | Skill Distiller：LLM 驱动的 Gene 综合、Capsule → Gene 蒸馏管道 |
| **[11-hub-integration.md](./11-hub-integration.md)** | Hub 市场集成：两阶段查询、Task Receiver、能力匹配 |
| **[12-curriculum.md](./12-curriculum.md)** | 自适应课程学习：三区划分、课程信号生成、进度追踪 |
| **[13-safety-ops.md](./13-safety-ops.md)** | 安全、并发与运维：文件锁、GitOps 回滚、Seed 机制、隐私脱敏、Self-PR、ATP |
| **[14-hub-review-asset-log.md](./14-hub-review-asset-log.md)** | Hub 使用反馈闭环：submitHubReview、Asset Call Log 可观测性、Skill 发布管道 |

---

## 核心架构图

```
Session Transcript / Today Log / Memory.md
         │
         ▼
┌─────────────────────────┐
│  signals.js             │  ← 文本模式匹配 → 信号列表
│  extractSignals()       │    14 类信号 + 多语言支持
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  memoryGraph.js         │  ← 追加事件到 JSONL
│  recordSignalSnapshot() │    recordHypothesis()
│  recordAttempt()       │    recordOutcomeFromState()
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  getMemoryAdvice()      │  ← 图推理
│  tryReadMemoryGraph...()│    Signal → Gene 边聚合
│  aggregateEdges()       │    Gene → Outcome 边聚合
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  selector.js            │  ← 综合决策
│  selectGeneAndCapsule()│    Memory Advice + Tag Score
│                         │    + Semantic + Anti-pattern
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  narrativeMemory.js     │  ← 人类可读叙事
│  recordNarrative()      │    Markdown 时间线
└─────────────────────────┘
```

---

## 关键设计亮点

1. **Append-Only 因果图**：所有事件（signal/hypothesis/attempt/outcome）以 JSONL 追加写入，只读取最后 512KB，零删除
2. **Laplace 平滑 + 指数半衰期衰减**：概率估计稳健，避免极端值
3. **饱和检测**：连续空循环 ≥5 → `force_steady_state`；连续失败 ≥5 → `failure_loop_detected`
4. **Adapter 模式**：本地 JSONL 为默认，远程 KG 服务可插拔，离线降级
5. **信号多语言支持**：EN/ZH-CN/ZH-TW/JA 四语种特征检测
6. **Population-Dependent Drift**：小基因池 → 高随机漂移，大池 → 精准选择

---

## 关键文件索引

| 文件 | 职责 |
|------|------|
| `src/gep/memoryGraph.js` | 核心图存储与推理引擎 |
| `src/gep/memoryGraphAdapter.js` | Local/Remote 适配器（Adapter 模式） |
| `src/gep/narrativeMemory.js` | Markdown 叙事日志 |
| `src/gep/signals.js` | 信号提取与去重 |
| `src/gep/learningSignals.js` | 结构化信号展开、标签匹配 |
| `src/gep/selector.js` | 基因选择器（含漂移强度计算） |
| `src/gep/paths.js` | 存储路径解析 |

---

## 与 Claude-Mem 的架构对比

| 维度 | EvoMap/evolver | Claude-Mem (BlueCortexCE) |
|------|---------------|--------------------------|
| 存储介质 | JSONL 文件 | PostgreSQL + pgvector |
| 检索方式 | Jaccard 相似度（启发式） | 向量语义检索 |
| 记忆类型 | 因果图（Signal→Gene→Outcome） | 对话上下文 + Observation |
| 图推理 | 有（内存图边聚合） | 无（扁平向量检索） |
| 衰减模型 | 指数半衰期（可配置） | 无衰减（向量相似度） |
| 饱和检测 | 有（连续空循环/失败熔断） | 无 |
| Adapter 模式 | 有（Local + Remote） | 无 |
| 叙事记忆 | 有（Markdown 时间线） | 有（Summary + Observation） |
| 多语言支持 | 有（EN/ZH-TW/JA） | 无 |

---

_由 Claude Code 分析生成 | 2026-05-03 | Doc 14（Hub Review + Asset Log，2026-05-07）_
