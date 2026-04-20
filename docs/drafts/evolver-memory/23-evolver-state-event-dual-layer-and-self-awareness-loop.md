# EvoMap/evolver 架构专题：State + Event 双层与自省循环

> **文件**: `23-evolver-state-event-dual-layer-and-self-awareness-loop.md`  
> **目标**: 提炼源码中新发现的两大架构模式——**State+Event 双层**与**自省循环**（`reflection`），对照 BlueCortexCE 可落地点。  
> **源码锚点**: `src/gep/memoryGraph.js`、`src/gep/reflection.js`、`src/gep/localStateAwareness.js`、`src/evolve.js`  
> **最后更新**: 2026-04-19

---

## 1. State + Event 双层架构（核心发现）

### 1.1 设计动机

纯 append-only JSONL 的**固有问题**：
- 无法高效追踪"当前未关闭的 action"（每次都要扫尾部判断 outcome 是否已记）
- 无法原子更新"当前状态"（如 `last_action.outcome_recorded`）

Evolver 的解法：**一个可变状态文件 + 一组不可变事件行**，并行写互补。

### 1.2 两条边的职责划分

| 层次 | 文件 | 语义 | 可变性 |
|------|------|------|--------|
| **可变 State** | `memory_graph_state.json` | 当前"正在飞行中"的 action、元数据 | 每次 outcome 推断后原地更新 |
| **不可变 Events** | `memory_graph.jsonl` | 完整的 signal→hypothesis→attempt→outcome 因果链 | 只追加，永不修改 |

### 1.3 State 文件的核心结构（memoryGraphStatePath）

```json
{
  "last_action": {
    "action_id": "act_xxx",
    "signal_key": "log_error|recurring_error",
    "had_error": true,
    "outcome_recorded": false,   // ← 防重复写入 outcome 的关键标志
    "baseline_observed": { ... },
    "created_at": "..."
  }
}
```

### 1.4 State+Event 的协作流程

```
recordAttempt()
  ├─ appendJsonl(memory_graph.jsonl, kind='attempt')     ← 不可变事件
  └─ writeJsonAtomic(memory_graph_state.json, state)     ← 更新 last_action

recordOutcomeFromState()
  ├─ read memory_graph_state.json
  ├─ if (last.outcome_recorded) return null;            ← 防重复
  ├─ inferOutcomeEnhanced({ prevHadError, currentHasError, ... })
  ├─ appendJsonl(memory_graph.jsonl, kind='outcome')    ← 追加 outcome 事件
  ├─ append confidence_edge / confidence_gene_outcome 事件
  └─ writeJsonAtomic(state, last.outcome_recorded=true)  ← 原子关闭
```

**关键保证**：
1. 同一 `action_id` 不会产生两个 `outcome` 事件（幂等写入）
2. State 文件是"飞行中"状态的一次性快照，outcome 写入后永久关闭
3. JSONL 是审计日志，State 是快速查找的索引缓存

### 1.5 与 BlueCortexCE 的对照

| EvoMap State+Event | CE 可对应实现 |
|---------------------|---------------|
| `memory_graph_state.json` 的 `last_action` | `mem_sessions.current_action_id` + `outcome_recorded` flag |
| State 文件的"关闭"语义 | `mem_observations.outcome_status` 从 `pending` → `success/failed` |
| 防重复写入 `outcome_recorded` | CE 中可以给 `mem_observations` 加 `outcome_finalized boolean` 或用 `UNIQUE(action_id)` 约束 |
| 原子写 (`writeJsonAtomic` = tmp+rename) | PostgreSQL transaction = 天然原子；不需要 tmp+rename |

**可落地行动**：给 `mem_observations` 表增加字段：
```sql
ALTER TABLE mem_observations
  ADD COLUMN IF NOT EXISTS outcome_finalized BOOLEAN DEFAULT FALSE;
```

在 outcome 写入逻辑中先 `SELECT ... WHERE session_id=? AND outcome_finalized=FALSE FOR UPDATE` 做幂等保护。

---

## 2. 自省循环（Reflection Phase）

### 2.1 什么是自省循环

`reflection.js` 实现了 Evolover 的**元认知层**——每 N 轮（自适应：成功时 8 轮、失败时 3 轮、默认 5 轮），暂停正常进化流程，对过去策略做一次战略性复盘。

### 2.2 触发条件

```
shouldReflect({ cycleCount, recentEvents })
  ├─ interval = computeReflectionInterval(recentEvents)
  │    ├─ 最近3个全 success → 8
  │    ├─ 最近3个全 failed → 3
  │    └─ 其他            → 5
  ├─ cycleCount % interval !== 0 → false
  └─ reflection log mtime < 30min ago → false (cooldown)
```

### 2.3 自省时的输入上下文

```js
buildReflectionContext({
  recentEvents,       // ← 过去10个 cycle 的 intent分布、gene使用、success/fail统计
  signals,            // ← 当前信号快照（前20个）
  memoryAdvice,       // ← memory graph 的 preferredGene / bannedGenes
  narrative,          // ← 最近叙事摘要（前3000字符）
})
```

LLM 被要求输出：
```json
{
  "insights": [...],
  "strategy_adjustment": "...",
  "priority_signals": [...]
}
```

### 2.4 自省会产生的副作用

```js
recordReflection({
  cycle_count, signals_snapshot, preferred_gene, banned_genes,
  context_preview, suggested_mutations
})
// → 写入 getReflectionLogPath() (reflection_log.jsonl)
```

同时 `buildSuggestedMutations()` 根据信号模式建议 personality 参数微调：
- 遇到 plateau → `creativity += 0.05`
- 遇到 errors → `rigor += 0.05`
- 遇到 capability gap → `risk_tolerance += 0.05`

### 2.5 与 BlueCortexCE 的对照

CE 没有等价的"自省循环"，但这个模式可以翻译为：

| EvoMap Reflection | CE 可对应实现 |
|-------------------|---------------|
| 每 N 轮触发战略复盘 | **cron 巡检**（已存在）时，额外读取最近 N 个 observation 的 intent 分布 + outcome 分布 |
| personality 参数微调 | 通过 `mem_modes` 的 `observation_types` 权重做**自适应调参** |
| `loadRecentReflections()` | 从 `mem_reflection_log` 表读取历史自省记录（可建） |
| 冷却期 30min | cron 任务本身已有间隔，不需要额外冷却 |

**可落地行动**：
1. 在 cron 巡检逻辑中增加"自省"步骤：读取最近 10 个 observations，做 intent 分布统计，若 failure streak ≥ 3 则主动推送预警
2. 在 `mem_modes` 的 `observation_types` 中增加 `stagnation` 类型，当连续 N 个 observations 都是同一 intent 时触发

---

## 3. localStateAwareness：自模型（Self-Model）

### 3.1 设计定位

`localStateAwareness.js` 是 Evolver 的**自省感知层**——在执行任何"创建/注册/配置"类操作前，先检查本地状态，避免重复行动。

### 3.2 捕获的五个维度

| 维度 | 关键指标 | 用途 |
|------|----------|------|
| **Node Identity** | A2A node ID、node secret 是否存在 | 防止重复注册 |
| **Env Config** | 关键 ENV 是否配置、.env 文件是否存在 | 指导"配置类"操作 |
| **Evolution State** | cycleCount、lastRun、lastGene、personality 参数 | 注入 prompt 上下文 |
| **Memory State** | MEMORY.md 大小、memory_graph.jsonl 大小、narrative 是否存在 | 感知知识库饱和度 |
| **Skills State** | skills/ 目录下的 skill 数量 | 感知能力边界 |

### 3.3 与 Reflection 的关系

```
evolve.js 中:
  localStateSummary = captureLocalState()   ← 每次 cycle 都捕获
  ↓ 注入 prompt context
  LLM 做 mutation/基因选择时先检查本地状态

每 N 轮触发 reflection 时:
  memoryAdvice + narrative + recentEvents + localState
  → buildReflectionContext → LLM 战略复盘
```

### 3.4 与 BlueCortexCE 的对照

| EvoMap localStateAwareness | CE 可对应实现 |
|----------------------------|---------------|
| Node ID / secret 检查 | CE 有 `device_id`、`node_id`（但不是 A2A 上下文） |
| ENV 配置状态注入 | CE 有 `runtime-config` 的 env 检查 |
| MEMORY.md 大小感知 | CE 的 `contextWindowTokens` / 截断策略 |
| Skills 数量感知 | CE 通过 `skills/` 目录扫描，但目前无结构化摘要 |
| evolution_state.json | **CE 无对应**——建议：cron 巡检时写入 `mem_health_check` 表记录 cycle count |

**可落地行动**：
- 在 CE cron 巡检的 `/api/health` 响应中增加结构化字段：
  ```json
  {
    "memory_graph_bytes": 38400,
    "cycle_count": 42,
    "skills_count": 12,
    "last_reflection_at": "..."
  }
  ```

---

## 4. 三大模式的关联全景

```
┌─────────────────────────────────────────────────────────┐
│  evolve.js run()                                         │
│                                                          │
│  captureLocalState()  ──────────────────────────────┐    │
│         ↓                                            │    │
│  extractSignals()  ─────────────────────────────►  │    │
│         ↓                                         记忆  │
│  recordOutcomeFromState()  ←──┐                     │    │
│         ↓                   │  State+Event        │    │
│  recordSignalSnapshot()  ────┤  双层写入            │    │
│         ↓                   │                     │    │
│  getMemoryAdvice()  ←───────┘                     │    │
│         ↓                                           │    │
│  selectGeneAndCapsule()                            │    │
│         ↓                                           │    │
│  recordHypothesis() / recordAttempt()  ────────────┤────┘
│         ↓                                           │
│  computeAdaptiveStrategyPolicy()  ←────────────────┤    │
│         ↓                                           │    │
│  if (shouldReflect) ─────────────────────────────►│──────► Reflection
│         ↓                                           │    │
│  renderSessionsSpawnCall()                          │    │
│         ↓                                           │    │
│  solidify (post-execution)                           │    │
│         ↓                                           │    │
│  recordOutcomeFromState()  ──────────────────────► │──────► State+Event 关闭
└─────────────────────────────────────────────────────────┘
```

---

## 5. 关键源码索引

| 文件 | 关键函数 | 行号 |
|------|---------|------|
| `memoryGraph.js` | `recordAttempt()`（State写入） | ~lines 300-380 |
| `memoryGraph.js` | `recordOutcomeFromState()`（State+Event双写） | ~lines 420-520 |
| `memoryGraph.js` | `writeJsonAtomic()`（原子写） | ~lines 50-60 |
| `reflection.js` | `shouldReflect()` | ~lines 35-55 |
| `reflection.js` | `buildReflectionContext()` | ~lines 65-120 |
| `localStateAwareness.js` | `captureLocalState()` | ~lines 140-175 |

---

## 6. 一句话总结

| 模式 | 核心价值 | CE 翻译难度 |
|------|---------|------------|
| State + Event 双层 | 幂等 outcome 写入 + 高效"飞行中"状态查找 | ⭐ 低（PostgreSQL transaction 已天然实现） |
| Reflection 自省循环 | 防止进化策略陷入局部最优 | ⭐⭐ 中（需要新增 cron 自省步骤） |
| localStateAwareness | 防止重复配置/注册，感知自身状态边界 | ⭐⭐ 中（CE 有部分 env 检查，需结构化） |

---

## 附录：相关已有文档

| 已有文档 | 与本文关系 |
|---------|-----------|
| [`18-evolver-local-source-memory-architecture-snapshot.md`](./18-evolver-local-source-memory-architecture-snapshot.md) | 包含 State 文件说明；本文§1 为浓缩提炼 |
| [`09-aspect-bluecortex-bridge.md`](./09-aspect-bluecortex-bridge.md) | 包含 BlueCortexCE 对照；本文§1.5/§2.5 为增量 |
| [`19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md`](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md) | 包含 outcome 推断链；本文§4 为全局关联图 |
| [`index.md`](./index.md) | 总入口；本文加入附录索引 |
