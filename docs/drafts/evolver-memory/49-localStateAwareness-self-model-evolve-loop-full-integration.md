# `localStateAwareness.js` + `evolve.js` Full Integration Analysis

**文档版本**: v49-0.1-draft  
**数据来源**: `src/gep/localStateAwareness.js` (244行) + `src/evolve.js` 全文 (2275行)  
**目标**: 补充 doc 23（State+Event 双层 / 自省循环）未显式覆盖的 `localStateAwareness.js` 模块级实现细节，以及 `evolve.js` 主循环中记忆子系统的完整调用顺序

---

## §1 `localStateAwareness.js` 模块级分析

### 1.1 职责定位

`localStateAwareness.js` 是 evolver 的**自模型（Self-Model）快照采集器**。它在单次运行结束时（或按需）采集五类运行时状态，输出为一个结构化的文本摘要，供 LLM 在下一轮决策时"知道自己在哪里"。

这不是持久化记忆（那是 `memoryGraph.jsonl` + `narrativeMemory` 的职责），而是**瞬时运行时状态快照**。

### 1.2 五类快照

| 类别 | 采集内容 | 输出格式 |
|------|----------|----------|
| **Node Identity** | A2A node ID（进程内 / `~/.evomap/node_id`）、Node Secret 是否存在、是否已注册 | 多行文本，如 `- Node ID: xxx (REGISTERED -- do NOT re-register)` |
| **Environment Config** | 8个 A2A 相关环境变量是否配置（`A2A_NODE_ID`/`HUB_URL`/`NODE_SECRET`/`AGENT_NAME`/`EVOLVE_STRATEGY`/`WORKER_ENABLED`/`SESSION_SCOPE`/`GITHUB_TOKEN`）、`.env` 文件是否存在 | `- Env configured: ...` / `- Env not set: ...` / `- .env file: EXISTS/MISSING` |
| **Evolution State** | `evolution_state.json` cycleCount + lastRun 时间戳、`solidify_state.json` last_run gene + active_task_title、`personality_state.json` 当前人格参数（rigor/creativity/risk_tolerance） | 多行文本，如 `- Personality: rigor=0.7 creativity=0.5 ...` |
| **Memory & Knowledge** | MEMORY.md 文件大小（字节）、`memory_graph.jsonl` 文件大小、`evolution_narrative.md` 是否存在 | 多行文本，如 `- Memory graph: 204800 bytes` |
| **Skills** | `skills/` 目录下的 skill 子目录数量 | `- Installed skills: 7 (at /path/to/skills)` |

### 1.3 持久化路径

所有路径通过 `paths.js` 的 `getRepoRoot()` / `getMemoryDir()` / `getEvolutionDir()` / `getSkillsDir()` 计算，**优先读环境变量**（`PROCESS_ENV` override），确保测试可注入。

Node identity 密钥路径：
```
~/.evomap/node_id        ← 全局单节点 ID（优先级高于进程环境变量）
~/.evomap/node_secret   ← 认证密钥
<repoRoot>/.evomap_node_id  ← 本地 fallback（无全局 ID 时）
```

### 1.4 自模型在 `evolve.js` 中的使用

`evolve.js` 在构建 prompt 时调用 `captureLocalState()`，将输出作为 prompt 上下文的一部分注入：

```javascript
// evolve.js — prompt 构建阶段（buildGepPrompt 调用链）
const localStateSnapshot = captureLocalState();
// → 注入到 LLM 上下文中，让 LLM "知道自己处于什么状态"
```

这使得 LLM 的决策能考虑：
- 自己是否已注册到 Hub（影响是否可以发起 A2A 通信）
- 当前人格参数（影响风险接受度）
- 进化阶段（cycle count，early/mature）
- 当前记忆系统状态（graph 大小，narrative 是否存在）
- 已有技能数量（影响是蒸馏新技能还是复用现有技能）

### 1.5 与 doc 23（State+Event 双层）的关系

Doc 23 覆盖：
- `memory_graph_state.json`（可变 State）↔ `memory_graph.jsonl`（不可变事件）
- 幂等 outcome 写入
- 自省循环（Reflection Phase）

`localStateAwareness` 与 doc 23 的 State 文件是**不同层次**的快照：
- `memory_graph_state.json`：**进化决策层**的状态（last_action, hypothesis_id, baseline_observed）
- `localStateAwareness`：**运行时环境层**的状态（节点身份、人格参数、技能数量）

两者都在 prompt 中使用，但采集维度和目的不同。

### 1.6 BlueCortexCE 借鉴要点

| Evolver 实践 | CE 对应 | 差距 |
|---|---|---|
| Node Identity（A2A 注册状态） | 无对应（CE 是单体，无节点概念） | — |
| Environment Config 快照 | `ObservationEntity.runtime_env` 字段（仅平台/OS） | CE 可扩展：记录 SDK 版本、模型配置、数据库状态 |
| `personality_state.json` | 无对应 | CE 人格通过 `ModeService` 管理，非持久化 JSON |
| Skills 数量快照 | `SkillEntity` 在 DB 中 | CE 可在 prompt 中注入"当前已注册技能数量"辅助决策 |
| `localStateAwareness` 整体 | 无直接对应 | CE 可在 `/api/context/generate` 的 ICL 段中注入运行时快照 |

---

## §2 `evolve.js` 全文主循环 —— 记忆子系统调用顺序

### 2.1 主循环结构（简化版）

```
evolve.js main loop:
  1. Load assets (genes, capsules, failed_capsules, events)
  2. Extract signals from session transcript + logs + memory
  3. getMemoryAdvice(signals, genes)   ← read from memoryGraph
  4. selectGeneAndCapsule(...)        ← write to memoryGraph (signal snapshot)
  5. buildMutation(...)               ← pure computation
  6. [Dry-run if --dry-run]          ← skip execution
  7. executeGene()                   ← apply code changes
  8. policyCheck()                   ← constraint validation
  9. recordOutcomeFromState(...)     ← write outcome to memoryGraph
  10. maybeReportIssue(...)          ← issueReporter
  11. recordNarrative(...)           ← narrativeMemory
  12. if shouldReflect(...):         ← reflection
       buildReflectionContext(...)
       → recordReflection(...)
  13. Hub interaction (if not saturated):
       hubSearch → fetchTasks → selectBestTask → claimTask
  14. maybePublishToHub(...)         ← a2a capsule publish
```

### 2.2 记忆子系统完整调用链

#### 2.2.1 Signal Extraction（Read path）

```
extractSignals({
  recentSessionTranscript,
  todayLog,
  memorySnippet,       ← narrativeMemory 摘要（来自 loadNarrativeSummary）
  userSnippet,
  recentEvents,        ← readAllEvents() 从 assetStore
})
→ signals[]            ← 输出：字符串数组（错误信号、机会信号等）
```

关键：`memorySnippet` 来自 `loadNarrativeSummary(4000)`，将 narrative 裁剪到 4000 字符作为信号提取的上下文之一。

#### 2.2.2 Memory Advice（Read path）

```javascript
const memoryAdvice = getMemoryAdvice({
  signals,
  genes,
  driftEnabled: IS_RANDOM_DRIFT,
});
```

内部：
1. `tryReadMemoryGraphEvents(2000)` — 从 `memory_graph.jsonl` 尾部读取最多 2000 条事件
2. `aggregateEdges(events)` — 按 (signal_key, gene_id) 聚合 outcome
3. `aggregateGeneOutcomes(events)` — 按 gene_id 聚合 outcome
4. `computeSignalKey(signals)` — 计算当前信号的规范化 key（Jaccard 去噪后 stableHash）
5. 遍历历史事件，找 Jaccard ≥ 0.34 的相似信号 key
6. 对每个候选 gene 计算：`edgeExpectedSuccess(edge) * sim + genePrior * 0.12`
7. 输出 `{ preferredGeneId, bannedGeneIds, explanation }`

#### 2.2.3 Signal Snapshot（Write path）

在 `selectGeneAndCapsule` 之前，先记录信号快照：

```javascript
recordSignalSnapshot({ signals, observations });
→ appends to memory_graph.jsonl (kind='signal')
```

这确保每次决策都有一个信号基准线，后续 outcome 可以与之对比。

#### 2.2.4 Hypothesis + Attempt（Write path）

```javascript
recordHypothesis({ signals, mutation, personality_state, selectedGene, ... });
→ appends to memory_graph.jsonl (kind='hypothesis')

recordAttempt({ signals, mutation, personality_state, selectedGene,
                hypothesisId, capsulesUsed, observations });
→ appends to memory_graph.jsonl (kind='attempt')
→ writes to memory_graph_state.json (last_action 持久化)
```

`recordAttempt` 同时更新 `memory_graph_state.json`（可变 State），记录完整的决策上下文。

#### 2.2.5 Outcome Recording（Write path）

```javascript
recordOutcomeFromState({ signals, observations });
→ reads from memory_graph_state.json (last_action)
→ infers outcome from error signal delta + observed blast radius
→ appends to memory_graph.jsonl (kind='outcome')
→ appends confidence_edge + confidence_gene_outcome events
→ marks outcome_recorded=true in state
```

outcome 推断逻辑（`inferOutcomeEnhanced`）：
```
prevHadError=true, currentHasError=false → success (error_cleared, score=0.85)
prevHadError=true, currentHasError=true  → failed (error_persisted, score=0.2)
prevHadError=false, currentHasError=true → failed (new_error_appeared, score=0.15)
prevHadError=false, currentHasError=false → success (stable_no_error, score=0.6)
```

+ 错误计数 delta 调整（±0.12）和扫描时间 ratio 调整（±0.06）。

#### 2.2.6 Narrative（Write path）

```javascript
recordNarrative({
  gene, signals, mutation, outcome,
  blast,       ← blast_radius { files, lines }
  capsule,
});
→ appends to evolution_narrative.md (markdown, 倒序追加)
→ trimNarrative() 确保总大小 ≤12000 字符，最多 30 条
```

### 2.3 完整 JSONL 事件序列（一次进化 cycle）

一次完整 cycle 在 `memory_graph.jsonl` 中产生的序列：

```
1. MemoryGraphEvent(kind='signal')
2. MemoryGraphEvent(kind='hypothesis')
3. MemoryGraphEvent(kind='attempt')
4. MemoryGraphEvent(kind='outcome')
5. MemoryGraphEvent(kind='confidence_edge')     ← 从 outcome 派生
6. MemoryGraphEvent(kind='confidence_gene_outcome') ← 从 outcome 派生
```

注意：2 和 3 的顺序是 `hypothesis` 先于 `attempt`。这符合科学方法：先提出假设，再记录行动。

### 2.4 与 doc 19 的差异

Doc 19（`19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md`）覆盖了：
- `evolve.js` 中的记忆调用顺序
- `inferOutcomeEnhanced` 逻辑
- `last_action` State 持久化

本文档补充了 doc 19 未显式覆盖的内容：
- `localStateAwareness.js` 的五类快照内容（§1）
- `evolve.js` 中 Hub 交互与记忆系统的交互时机（saturation-based gating）
- Signal snapshot（`recordSignalSnapshot`）在 Gene 选择**之前**执行（doc 19 可能未强调）

### 2.5 Hub 饱和调度（Idle-cycle gating）

```javascript
// evolve.js — Hub 调用的条件判断
if (!shouldSkipHubCalls(signals)) {
  // 执行 hubSearch / fetchTasks / claimTask
}
```

`shouldSkipHubCalls` 定义了"饱和信号"（`evolution_saturation` / `empty_cycle_loop_detected` 等），当存在这些信号**且**没有可操作的信号时，跳过 Hub 调用。这避免了系统在空转时浪费网络资源。

CE 借鉴：对于 RAG 检索，当系统处于"稳定成功"状态时，可以降低检索频率（类似 OMLS 休眠调度）。

---

## §3 关键设计模式总结

### 3.1 记忆系统五层架构

| 层次 | 存储介质 | 读写模式 | 用途 |
|------|----------|----------|------|
| **Signal Snapshot** | `memory_graph.jsonl` | 写一次/cycle | 决策基准线 |
| **Hypothesis** | `memory_graph.jsonl` | 写一次/cycle | 科学假设记录 |
| **Attempt** | `memory_graph.jsonl` + `memory_graph_state.json` | 写一次/cycle | 行动记录（State 用于 outcome 推断） |
| **Outcome** | `memory_graph.jsonl` | 写一次/cycle | 结果反馈 |
| **Confidence** | `memory_graph.jsonl`（派生事件） | 从 outcome 派生 | 边权重快照（用于快速读取） |

### 3.2 State + Event 双写模式

```
attempt 写入时：
  1. appendJsonl(memory_graph.jsonl, attempt_event)     ← 不可变历史
  2. writeJsonAtomic(memory_graph_state.json, state)   ← 可变状态（last_action）

outcome 推断时：
  1. read memory_graph_state.json → 找到 last_action
  2. infer outcome from signals delta
  3. appendJsonl(memory_graph.jsonl, outcome_event)     ← 不可变历史
  4. appendJsonl → confidence_edge + confidence_gene_outcome
  5. update state.outcome_recorded=true, writeJsonAtomic
```

State 文件是**outcome 推断的锚点**：它记录了 attempt 的完整上下文，使 outcome 可以在任意后续时间点被推断（而不需要在 attempt 执行时立即知道结果）。

### 3.3 饱和降级与 Hub 节流

Evolver 的记忆饱和检测（信号频率抑制、空转循环检测）与 Hub 调用节流联动：

```
信号饱和检测 → 信号降级（strip error signals, inject innovation）
     ↓
shouldSkipHubCalls() = true → 跳过 Hub API 调用
     ↓
idleScheduler 调度强度降低（aggressive → normal）
```

CE 借鉴：RAG 系统的"检索饱和"可以类似地降低 embedding 查询频率。

---

## §4 BlueCortexCE 借鉴路径

### 4.1 可直接迁移的实践

| 实践 | CE 落点 | 说明 |
|------|---------|------|
| State + Event 双写 | `ObservationEntity`（State）+ `memory_graph.jsonl` 思路迁移到 DB 的 `events` 表 | outcome 锚定 last_action |
| Signal Snapshot 先于决策 | `/api/ingest/observation` 在 search 之前调用 | 决策基准线 |
| Outcome 从信号 delta 推断 | `inferOutcomeEnhanced` 逻辑迁移到 `OutcomeService` | error_sig_norm 变化 → score |
| 饱和降级 | RAG 查询频率根据"近期无新信息"信号降低 | `stable_success_plateau` 触发 |
| Narrative 摘要注入信号提取 | ICL prompt 中注入 narrative 摘要 | 跨 session 上下文 |

### 4.2 需要架构改造的实践

| 实践 | 改造难度 | 说明 |
|------|----------|------|
| 基因选择器的记忆图谱（Signal→Gene→Outcome） | 高 | 需要引入 Gene/Capsule 概念，CE 是纯记录型 |
| Hub 分布式交互 | 高 | CE 是单体架构，无 A2A 需求 |
| 人格状态机（personality_state.json） | 中 | CE 通过 `ModeService` 管理，但非持久化 JSON |
| Confidence 派生事件（ Laplace 平滑 + 半衰衰减） | 中 | 可在 CE 的 `SearchService` 中实现加权排序 |

---

## §5 待补充内容

- [ ] `evolve.js` 全文详细注释（>2000行，建议拆分为 `evolve-loop-phases.md`）
- [ ] `idleScheduler` 与 OMLS 启发式调度的 CE 映射（已在 doc 45 中覆盖，可勾选）
- [ ] `localStateAwareness` 在 prompt 中的实际注入示例（需抓取 LLM prompt 输出）
- [ ] `shouldSkipHubCalls` 的饱和判断逻辑是否可以迁移到 CE 的 RAG 节流

---

**版本历史**
- v49-0.1 (draft): 初稿，覆盖 `localStateAwareness.js` 五类快照、`evolve.js` 完整调用链、State+Event 双写模式、Hub 饱和节流、CE 借鉴路径
