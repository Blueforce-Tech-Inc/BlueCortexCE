# MemoryGraph 闭环反馈架构：Signal → Outcome 完整时序分析

**文档版本**: v50-0.1-draft  
**数据来源**: `src/gep/memoryGraph.js`（`memoryGraphAdapter.js` 导出部分，`~580行有效实现`）+ `src/evolve.js` 全文  
**目标**: 补充 doc 36（架构综合）与 doc 37（信号→选择链路）未显式覆盖的 MemoryGraph **事件时序闭环**、**outcome 推断算法**、**中断恢复**与**多租户隔离**机制，为 BlueCortexCE 的 OutcomeEntity + ObservationEntity 反馈环路设计提供可直接翻译的实现级参考。  

**最后更新**: 2026-04-25

---

## §1 核心命题：MemoryGraph 是因果闭环日志

Evolver 的 MemoryGraph 不是"存储过去的记录"，而是**维护一条因果链**：

```
Signal(what changed?) → Hypothesis(why this action?) → Attempt(concrete change) → Outcome(did it work?) → [next Signal]
```

每个 cycle 在 JSONL 中追加 2–4 条事件。下一 cycle 读取历史事件，聚合为 **边权重**（Signal×Gene → Outcome），用于指导基因选择。这是**时间序列 + 因果图谱**的混合结构。

---

## §2 六类事件的职责与时序

### 2.1 事件类型一览

| 事件 `kind` | 写入时机 | 主要字段 | 是否影响边权重 |
|------------|---------|---------|--------------|
| `signal` | cycle 开始（`recordSignalSnapshot`） | `signal.key`（规范化信号键）、`signal.signals[]`、`signal.error_signature` | ❌（仅记录输入） |
| `hypothesis` | cycle 开始（`recordHypothesis`） | `hypothesis.id`、`mutation`（归一化）、`gene`、`action.selected_by` | ❌（预测，未验证） |
| `attempt` | cycle 开始（`recordAttempt`） | `action.id`、`hypothesis.id`、`mutation`、`gene` | ❌（记录动作） |
| `outcome` | **下一 cycle 开始**（`recordOutcomeFromState`） | `outcome.status`、`outcome.score`、`outcome.note` | ✅（更新边权重） |
| `confidence_edge` | `outcome` 写入时（副作用） | `stats{success,fail,p,decay_weight,value}`、`half_life_days: 30` | 由 `getMemoryAdvice` 读取 |
| `confidence_gene_outcome` | `outcome` 写入时（副作用） | `stats` 同上，`half_life_days: 45` | 由 `getMemoryAdvice` 读取 |
| `external_candidate` | 外部资产入账时（`recordExternalCandidate`） | `asset{type,id}`、`candidate{trigger,confidence}` | ❌（标注来源） |

### 2.2 典型 Cycle 的事件时序

```
T=0: Cycle #N 开始
  1. recordSignalSnapshot()     → kind:signal
  2. getMemoryAdvice()        ← 读取历史 outcome 事件（读端）
  3. selectGeneAndCapsule()    ← 基于 advice 选择基因
  4. buildMutation()          ← 生成变更计划
  5. recordHypothesis()        → kind:hypothesis（Signal → Gene 的预测）
  6. recordAttempt()          → kind:attempt（记录选中的 action_id）
  7. [solidify 状态写入]      → 持久化 run context（但不写入 JSONL）
  8. [spawn 执行器 agent]
  9. [solidify 验证]
  10. Cycle #N 结束

T=1: Cycle #N+1 开始
  11. recordOutcomeFromState()→ kind:outcome（推断 #N 的结果）
  12. [副作用] buildConfidenceEdgeEvent() → kind:confidence_edge
  13. [副作用] buildGeneOutcomeConfidenceEvent() → kind:confidence_gene_outcome
  14. recordSignalSnapshot()   → kind:signal（#N+1 的新信号）
  → 循环
```

**关键设计**：outcome 事件在**下一个 cycle**写入，而非当前 cycle 结束时。这是合理的，因为需要收集"执行后的状态"（下一轮 scan 时才能看到错误是否消除）。

### 2.3 可变 State vs 不可变 Event

```
memory_graph.jsonl           ← Append-only（事件日志）
memory_graph_state.json     ← 可变（仅存 last_action）
```

`memory_graph_state.json` 是为了在 cycle 之间传递 `last_action`——它需要被覆写（每次新的 attempt 替换上一次的）。但 JSONL 永远只追加，保证：
- 审计轨迹完整
- 任意时刻可重放
- 多进程/多 cycle 并发安全（append 不需要锁）

---

## §3 Outcome 推断算法：核心实现解析

### 3.1 `recordOutcomeFromState` 的完整流程

```javascript
function recordOutcomeFromState({ signals, observations }) {
  // 1. 读取上一次 attempt 的上下文
  const state = readJsonIfExists(statePath, { last_action: null });
  const last = state.last_action;
  if (!last || !last.action_id) return null;       // 无历史 action，跳过
  if (last.outcome_recorded) return null;          // 已记录过，幂等保护

  // 2. 提取当前 cycle 的 error signal
  const currentHasError = hasErrorSignal(signals);

  // 3. 推断结果（多策略）
  const inferred = inferOutcomeEnhanced({
    prevHadError: !!last.had_error,
    currentHasError,
    baselineObserved: last.baseline_observed || null,
    currentObserved: observations || null,
  });

  // 4. 写入 outcome 事件
  appendJsonl(memoryGraphPath(), outcomeEvent);

  // 5. 副作用：写入 confidence_edge + confidence_gene_outcome
  if (last.gene_id) {
    appendJsonl(memoryGraphPath(), buildConfidenceEdgeEvent(...));
    appendJsonl(memoryGraphPath(), buildGeneOutcomeConfidenceEvent(...));
  }

  // 6. 标记 outcome 已记录（可重入保护）
  last.outcome_recorded = true;
  writeJsonAtomic(statePath, state);
}
```

### 3.2 Outcome 推断的三层策略（`inferOutcomeEnhanced`）

```javascript
function inferOutcomeEnhanced({ prevHadError, currentHasError,
                                baselineObserved, currentObserved }) {
  // 策略 1：扫描今日日志中最近的 EvolutionEvent，提取显式 outcome
  const observed = tryParseLastEvolutionEventOutcome(combinedEvidence);
  if (observed) return observed;                            // ← 最优先

  // 策略 2：基于 error signal 的启发式推断
  const base = inferOutcomeFromSignals({ prevHadError, currentHasError });
  // error_cleared   → status:success, score:0.85
  // error_persisted → status:failed,  score:0.20
  // new_error       → status:failed,  score:0.15
  // stable_no_error → status:success, score:0.60

  // 策略 3：基于量化 delta 的微调
  // - error_count delta / 50      → ±0.12
  // - scan_ms ratio               → ±0.06
  score = clamp01(base.score + delta_adjustments);

  return { status: base.status, score, note: '...' };
}
```

### 3.3 推断算法的 BlueCortexCE 翻译

| Evolver | BlueCortexCE (对应物) |
|---------|----------------------|
| `prevHadError` / `currentHasError` | Baseline Observation vs Current Observation 中的 `is_error` 标记 |
| `tryParseLastEvolutionEventOutcome` | 读取上一次 `SummaryEntity.outcome_status`（如果存在） |
| `baselineObserved.recent_error_count` | Baseline Observation 的 `error_count` 字段 |
| `clamp01(score + delta)` | `Math.max(0, Math.min(1, score))` + delta |
| `outcome_recorded` 幂等标记 | SummaryEntity 中 `outcome_recorded: boolean` 字段 |
| `half_life_days: 30/45` | SummaryEntity 权重计算中的时间衰减系数 |

---

## §4 边权重计算：Signal×Gene → Success Probability

### 4.1 聚合逻辑（`aggregateEdges` + `edgeExpectedSuccess`）

```javascript
// 每个 outcome 事件贡献一次计数
const k = `${signalKey}::${geneId}`;   // 边 key
// 累加 success / fail
if (status === 'success') edge.success += 1;
else if (status === 'failed') edge.fail += 1;

// 读取时的概率计算（带 Laplace 平滑 + 半衰衰减）
function edgeExpectedSuccess(edge, { half_life_days }) {
  const p = (edge.success + 1) / (edge.fail + edge.success + 2);  // Laplace: +1,+2
  const w = decayWeight(edge.last_ts, half_life_days);             // 指数衰减
  return { p, w, total: edge.success + edge.fail, value: p * w };
}
```

**公式解读**：
- `p`：Signal×Gene 条件成功率（加 1 平滑避免 0/1 极端）
- `w`：时间衰减权重，`0.5^(age_days / half_life_days)`
- `value = p × w`：综合得分（近期高分 > 远期满分）

### 4.2 Jaccard 信号匹配（`jaccard` + `normalizeSignalsForMatching`）

```javascript
function jaccard(aList, bList) {
  const a = new Set(normalizeSignalsForMatching(aList));   // 去噪后集合
  const b = new Set(normalizeSignalsForMatching(bList));
  return |a ∩ b| / |a ∪ b|;
}

// 规范化：去除路径/数字/十六进制（路径每次不同，但 pattern 相同）
function normalizeSignalsForMatching(signals) {
  return signals.map(s => {
    if (s.startsWith('errsig:')) {
      return `errsig_norm:${stableHash(normalizeErrorSignature(s))}`;
    }
    return s;
  });
}
```

**关键发现**：信号中的错误签名（路径/行号/数值）会被规范化（替换为 `<path>`/`<hex>`/`<n>`），然后做 SHA-1 哈希作为匹配键。这意味着**相同类型的错误**会被归一化到一起，而不是按具体堆栈跟踪分开。

### 4.3 Gene 抑制规则（`getMemoryAdvice` 中的 Ban 逻辑）

```javascript
// 低效路径抑制：>=2 次尝试且得分 < 0.18 → ban
if (!driftEnabled && info.attempts >= 2 && info.best < 0.18) {
  bannedGeneIds.add(geneId);
}

// 全局低质路径抑制：信号边稀疏但基因全局得分低 → ban
if (!driftEnabled && info.attempts < 2 && info.prior_attempts >= 3 && info.prior < 0.12) {
  bannedGeneIds.add(geneId);
}
```

**含义**：避免在**已知低效路径**上浪费尝试。`driftEnabled` 时关闭此限制（强制探索）。

---

## §5 中断恢复：Dormant Hypothesis 机制

### 5.1 问题场景

当 evolver 在 cycle 中途被打断（例如：进程被 kill、资源耗尽、machine sleep），会导致：
- `recordAttempt` 已写入（`last_action` 已更新）
- 但 `solidify` 未执行 → `outcome` 永远不会被写入
- 该 hypothesis 被"悬空"

### 5.2 解决：写入 + TTL 过期

```javascript
// 中断时写入 dormant hypothesis
writeDormantHypothesis({
  backoff_reason: 'active_sessions_exceeded',
  signals: [...],
  selected_gene_id: ...,
  mutation: ...,
  personality_state: ...,
  run_id: ...,
});

// 下一 cycle 恢复时
const dormant = readDormantHypothesis(); // TTL 1小时，过期自动清除
if (dormant) {
  signals.push(...dormant.signals);  // 重新注入未处理的信号
  console.log('[DormantHypothesis] Recovered partial state...');
  clearDormantHypothesis();
}
```

### 5.3 BlueCortexCE 翻译建议

BlueCortexCE 面临类似场景：`processToolUseAsync` 写入成功但后续步骤失败。

**方案 A**：为每个 in-flight Observation 添加 `pending_outcome: true` 标记，完成后清除。

**方案 B**（更轻量）：在 `SummaryEntity` 中记录 `last_unconfirmed_action_id`，定期清理超时的未确认记录。

---

## §6 Idle Gating 与饱和节流

### 6.1 `shouldSkipHubCalls` 的完整条件

```javascript
function shouldSkipHubCalls(signals) {
  // 第一层：检查是否处于饱和状态
  const saturationIndicators = [
    'force_steady_state',
    'evolution_saturation',
    'empty_cycle_loop_detected',
  ];
  const isSaturated = signals.some(s => saturationIndicators.includes(s));
  if (!isSaturated) return false;

  // 第二层：即使饱和，如果有可操作信号，仍然调用 Hub
  const actionablePatterns = [
    'log_error', 'recurring_error', 'capability_gap', 'perf_bottleneck',
    'external_task', 'bounty_task', 'overdue_task', 'urgent',
    'unsupported_input_type',
    /^errsig:/, /^user_feature_request:/, /^user_improvement_suggestion:/,
  ];
  const hasActionable = signals.some(s =>
    actionablePatterns.some(p => typeof p === 'string' ? s === p : p.test(s))
  );
  if (hasActionable) return false;

  return true;  // 饱和且无可操作信号 → 跳过 Hub 调用
}
```

### 6.2 时间间隔控制

```javascript
const IDLE_FETCH_INTERVAL_MS = 600000;  // 10 分钟
// 如果跳过 Hub 调用，检查距上次调用是否已超间隔
if (shouldSkipHubCalls(signals)) {
  const elapsed = Date.now() - _lastHubFetchMs;
  if (elapsed < IDLE_FETCH_INTERVAL_MS) {
    skipHubCalls = true;  // 仍在冷却期内
  }
  // 否则：强制做一次 Hub 调用（防止永远卡在饱和状态）
}
```

### 6.3 BlueCortexCE 翻译建议

类似机制可用于 Claude-Mem 的 **Hub 集成**（如果有）：当系统处于空闲状态时，减少不必要的外部 API 调用，节省费用。

---

## §7 Session Scope 多租户隔离

### 7.1 Scope 感知路径

```javascript
// evolver.js — 读取 MEMORY.md 时
const scope = getSessionScope();
let memFile = MEMORY_FILE;
if (scope) {
  const scopedMemory = path.join(MEMORY_DIR, 'scopes', scope, 'MEMORY.md');
  if (fs.existsSync(scopedMemory)) {
    memFile = scopedMemory;  // 使用 scoped memory
  }
}
```

### 7.2 完整 Scope 隔离维度

| 维度 | 隔离方式 |
|------|---------|
| `MEMORY.md` | `memory/scopes/<scope>/MEMORY.md` |
| `evolution_state.json` | 同一文件，但 `cycleCount` 按 scope 独立累计 |
| `memory_graph.jsonl` | 同一文件，通过 `observed.session_scope` 字段标注 |
| Gene/Capsule Asset | 共享（asset 是生态级别共享资产，不按 scope 隔离） |
| Narrative Memory | `evolution_narrative.md` 同一文件 |

### 7.3 BlueCortexCE 翻译

BlueCortexCE 的 SessionEntity 已有 `scope` 字段。对应关系：
- Evolver `scopedMemory` → BlueCortexCE 的 **多 Session 隔离**（每个 session 的 Observation/Prompt 独立）
- Evolver `sessionScope` → BlueCortexCE 的 `session.scope` 字段（用于语义搜索过滤）

---

## §8 完整 Cycle 调用链（记忆子系统视角）

以下是 `evolve.js` 中记忆子系统的完整调用顺序：

```
run()
  │
  ├─ readRealSessionLog()                    ← 读取原始会话日志
  ├─ readMemorySnippet()                     ← 读取 MEMORY.md（含 scope 感知）
  ├─ readUserSnippet()                      ← 读取 USER.md
  ├─ captureLocalState()                    ← 自模型快照（5类状态）
  │
  ├─ extractSignals({                        ← Signal 提取
  │    recentSessionTranscript,
  │    todayLog,
  │    memorySnippet,
  │    userSnippet,
  │    recentEvents,                         ← 读 history 用于去重
  │  })
  │
  ├─ getMemoryAdvice({ signals, genes })    ← MemoryGraph 读（边权重聚合）
  │    └─ tryReadMemoryGraphEvents(2000)    ← tail 读取 JSONL
  │
  ├─ recordSignalSnapshot({ signals })      ← MemoryGraph 写：kind:signal
  │
  ├─ recordHypothesis({ signals, mutation })← MemoryGraph 写：kind:hypothesis
  │
  ├─ recordAttempt({ signals, mutation })    ← MemoryGraph 写：kind:attempt
  │    └─ 更新 memory_graph_state.json
  │
  ├─ [solidify 状态写入]                      ← 持久化到 solidify_state.json
  │
  ├─ [spawn 执行器 agent]
  │
  ├─ [solidify 验证 + asset 写入]
  │
  └─ 下一 cycle 开始时：
       recordOutcomeFromState({ signals })  ← MemoryGraph 写：kind:outcome
           └─ buildConfidenceEdgeEvent()      ← 副作用：kind:confidence_edge
           └─ buildGeneOutcomeConfidenceEvent() ← 副作用：kind:confidence_gene_outcome
```

---

## §9 BlueCortexCE 关键启示（按优先级）

### P0（立即可落地）

1. **Outcome 反馈环路**：BlueCortexCE 的 SummaryEntity 应该记录 `baseline_observation_id`，并在下一轮 Summary 生成时比对 `baseline.is_error` vs `current.is_error`，推算 outcome score。参考 `inferOutcomeEnhanced` 的三层策略。

2. **时间衰减权重**：在 `SearchService` 的向量检索结果上叠加时间衰减（`Math.pow(0.5, ageDays / halfLife)`），让近期 Observation 权重更高。

3. **Signal 规范化**：ObservationEntity 的 `semantic_tags` 字段应规范化错误签名（路径→`<PATH>`），提升跨 session 的匹配准确率。

### P1（需要设计）

4. **Laplace 平滑**：当 (success + fail) 很小时，使用 `(success + 1) / (total + 2)` 代替原始比率，避免 0/1 极端值。

5. **幂等 outcome 写入**：SummaryEntity 增加 `outcome_recorded: boolean`，防止重复写入导致的计数偏移。

6. **Dormant Hypothesis**（轻量版）：为 in-flight Summary 添加 TTL 超时，清理悬空记录。

### P2（架构级改进）

7. **Session Scope 隔离**：BlueCortexCE 已支持 session scope 字段，建议在 `semantic search` 时默认按 `session.scope` 过滤，确保多租户隔离。

8. **JSONL 审计日志**（可选）：为每个 API 写入操作追加一条审计事件（`entity_type`、`entity_id`、`action`），便于事后重放和问题排查。

---

## §10 关键实现函数索引

| 函数 | 文件 | 用途 |
|------|------|------|
| `appendJsonl` | `memoryGraph.js` | 追加事件到 JSONL |
| `tryReadMemoryGraphEvents` | `memoryGraph.js` | tail 读取 JSONL（内存优化） |
| `computeSignalKey` | `memoryGraph.js` | 规范化信号键 |
| `normalizeSignalsForMatching` | `memoryGraph.js` | 错误签名去噪 |
| `jaccard` | `memoryGraph.js` | 信号集合相似度 |
| `decayWeight` | `memoryGraph.js` | 半衰期指数衰减 |
| `aggregateEdges` | `memoryGraph.js` | outcome 事件聚合为边 |
| `edgeExpectedSuccess` | `memoryGraph.js` | 边成功率计算（ Laplace + 衰减） |
| `getMemoryAdvice` | `memoryGraph.js` | 核心：读取 → 聚合 → 推荐 |
| `recordSignalSnapshot` | `memoryGraph.js` | 写入：kind:signal |
| `recordHypothesis` | `memoryGraph.js` | 写入：kind:hypothesis |
| `recordAttempt` | `memoryGraph.js` | 写入：kind:attempt |
| `recordOutcomeFromState` | `memoryGraph.js` | 写入：kind:outcome（核心闭环） |
| `inferOutcomeEnhanced` | `memoryGraph.js` | outcome 推断算法 |
| `writeDormantHypothesis` | `evolve.js` | 中断状态保存 |
| `readDormantHypothesis` | `evolve.js` | 中断状态恢复 |
| `shouldSkipHubCalls` | `evolve.js` | 空闲节流判断 |
