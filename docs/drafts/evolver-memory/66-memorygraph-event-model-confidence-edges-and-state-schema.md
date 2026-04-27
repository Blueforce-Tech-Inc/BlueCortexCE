# 66. MemoryGraph 事件模型完整解析：置信边、状态Schema、外部候选

**来源**：`EvoMap/evolver/src/gep/memoryGraph.js`（全量 788 行源码）。  
**定位**：补充 [`50`](./50-memory-graph-closed-loop-architecture.md) — 专注 **置信边事件构建**、**状态 Schema**、**外部候选入图**三个此前未显式覆盖的机制。  
**最后更新**：2026-04-26。

---

## 1. 事件类型全谱

`memoryGraph.js` 定义了 7 种 `MemoryGraphEvent`：

| kind | 触发时机 | 包含内容 |
|------|----------|---------|
| `signal` | 每轮开始前 | signal_key + signals[] + error_signature |
| `hypothesis` | 选择基因/胶囊后 | mutation + personality + gene + capsules |
| `attempt` | 执行操作前 | hypothesis_id + action.id |
| `outcome` | 执行完成后 | status + score + observed |
| `confidence_edge` | outcome 写入后（自动） | signal×gene 边统计（success/fail/p/decay/value） |
| `confidence_gene_outcome` | outcome 写入后（自动） | gene 全局统计（独立于 signal） |
| `external_candidate` | Hub 外部候选到达时 | asset type/id + trigger + gene + confidence |

---

## 2. 置信边事件详解

### 2.1 `confidence_edge` — Signal×Gene 边

每轮 outcome 写入后，**自动**构建并追加（不对用户暴露）：

```javascript
function buildConfidenceEdgeEvent({ signalKey, signals, geneId, geneCategory, outcomeEventId, halfLifeDays }) {
  // 从最近 2000 条事件中实时聚合 signal×gene 边统计
  const events = tryReadMemoryGraphEvents(2000);
  const edges = aggregateEdges(events);
  const edge = edges.get(`${signalKey}::${geneId}`) || { success: 0, fail: 0, last_ts: null };

  // edgeExpectedSuccess = Laplace 平滑 + 半衰衰减
  const ex = edgeExpectedSuccess(edge, { half_life_days: halfLifeDays });

  return {
    type: 'MemoryGraphEvent',
    kind: 'confidence_edge',
    stats: {
      success: edge.success,
      fail: edge.fail,
      attempts: ex.total,
      p: ex.p,                        // Laplace 平滑后成功概率
      decay_weight: ex.w,             // 半衰衰减权重
      value: ex.value,                // p × w
      half_life_days: halfLifeDays,   // signal×gene 边：30天
      updated_at: nowIso(),
    },
    derived_from: { outcome_event_id: outcomeEventId },
  };
}
```

**`edgeExpectedSuccess` 公式**：
```
p = (success + 1) / (total + 2)   // Laplace 平滑，避免 0/1 极端
w = 0.5 ^ (age_days / half_life)   // 指数半衰衰减
value = p × w
```

**关键**：读端（`getMemoryAdvice`）使用 `aggregateEdges` **实时重新聚合**，而非在写端维护计数器。这保证了一致性（无竞态），且允许灵活调整 `half_life_days`。

### 2.2 `confidence_gene_outcome` — Gene 先验（独立于 Signal）

```javascript
function buildGeneOutcomeConfidenceEvent({ geneId, geneCategory, outcomeEventId, halfLifeDays }) {
  // 从所有 outcome 事件聚合，不区分 signal
  const events = tryReadMemoryGraphEvents(2000);
  const geneOutcomes = aggregateGeneOutcomes(events);
  const edge = geneOutcomes.get(String(geneId)) || { success: 0, fail: 0, last_ts: null };
  const ex = edgeExpectedSuccess(edge, { half_life_days: halfLifeDays }); // 45天半衰
  // ...
}
```

**与 `confidence_edge` 的区别**：

| 维度 | `confidence_edge` | `confidence_gene_outcome` |
|------|-------------------|--------------------------|
| 键 | `signal_key::gene_id` | `gene_id` |
| 半衰期 | 30 天 | 45 天 |
| 用途 | 信号→基因的特化路径 | 基因全局成功率先验 |
| 组合 | `getMemoryAdvice` 中 `best + prior*0.12` | `prior * 0.4`（无特化边时） |

---

## 3. Outcome 解析：从 EvolutionEvent JSONL 提取

```javascript
function tryParseLastEvolutionEventOutcome(evidenceText) {
  // 扫描末尾 400 行，查找最后一个 EvolutionEvent JSON 行
  const lines = evidenceText.split('\n').slice(-400);
  for (let i = lines.length - 1; i >= 0; i--) {
    const line = lines[i].trim();
    if (!line.includes('"type"') || !line.includes('EvolutionEvent')) continue;
    try {
      const obj = JSON.parse(line);
      if (obj?.type !== 'EvolutionEvent') continue;
      const o = obj.outcome;
      if (!o) continue;
      const status = o.status === 'success' || o.status === 'failed' ? o.status : null;
      const score = Number.isFinite(Number(o.score)) ? clamp01(Number(o.score)) : null;
      if (!status && score == null) continue;
      return {
        status: status || (score != null && score >= 0.5 ? 'success' : 'failed'),
        score: score ?? (status === 'success' ? 0.75 : 0.25),
        note: 'evolutionevent_observed',
      };
    } catch (_) { continue; }
  }
  return null;
}
```

**设计意图**：从上一次 solidify 流程写入的 `EvolutionEvent` JSONL 行中直接提取 outcome，**无需**额外的状态传递通道。`evidenceText` 来源：
```javascript
const combinedEvidence =
  currentObserved?.evidence?.recent_session_tail +
  '\n' +
  currentObserved?.evidence?.today_log_tail;
```

---

## 4. `memory_graph_state.json` — 状态 Schema

这是**可变状态**文件（与不可变 JSONL 对应），定义在 `recordAttempt` 中写入：

```javascript
// 写入结构
state.last_action = {
  action_id: actionId,
  signal_key: signalKey,
  signals: Array.isArray(signals) ? signals : [],
  mutation_id: mutNorm?.id,
  mutation_category: mutNorm?.category,
  mutation_risk_level: mutNorm?.risk_level,
  personality_key: psNorm ? personalityKey(psNorm) : null,
  personality_state: psNorm || null,
  gene_id: geneId,
  gene_category: geneCategory,
  hypothesis_id: hypothesisId,
  capsules_used: capsulesUsed,
  had_error: hasErrorSignal(signals),    // 关键：记录执行前是否有错误
  created_at: ts,
  outcome_recorded: false,               // 幂等保护
  baseline_observed: observations,         // 执行前快照
};
```

**幂等保护**：`outcome_recorded` 标志确保每次 `recordOutcomeFromState` 只写入一次。

**用途链**：`recordOutcomeFromState` 读取此状态 → 推断 outcome → 写入 JSONL event → 清除 `outcome_recorded = true`。

---

## 5. 外部候选入图（`recordExternalCandidate`）

```javascript
function recordExternalCandidate({ asset, source, signals }) {
  const ev = {
    type: 'MemoryGraphEvent',
    kind: 'external_candidate',
    external: {
      source: source || 'external',
      received_at: ts,
    },
    asset: { type, id },
    candidate: {
      trigger: type === 'Capsule' && Array.isArray(a.trigger) ? a.trigger : [],
      gene: type === 'Capsule' && a.gene ? String(a.gene) : null,
      confidence: type === 'Capsule' && Number.isFinite(Number(a.confidence)) ? Number(a.confidence) : null,
    },
  };
  appendJsonl(memoryGraphPath(), ev);
}
```

**关键约束**：外部候选**只记录，不参与 outcome 聚合**（`aggregateEdges` 只看 `kind === 'outcome'`）。这防止外部引入的低质量资产污染本地学习图谱。

---

## 6. 完整 Cycle 时序图

```
[Cycle Start]
    │
    ▼
recordSignalSnapshot(signal)        ──→ MemoryGraphEvent(kind=signal)
    │
    ▼
recordHypothesis(...)                ──→ MemoryGraphEvent(kind=hypothesis)
    │
    ▼
recordAttempt(...)                   ──→ MemoryGraphEvent(kind=attempt)
    │                                + memory_graph_state.json (last_action)
    ▼
[执行 Patch]
    │
    ▼
recordOutcomeFromState(...)         ──→ MemoryGraphEvent(kind=outcome)
    │                                + MemoryGraphEvent(kind=confidence_edge)  ← 自动
    │                                + MemoryGraphEvent(kind=confidence_gene_outcome) ← 自动
    │
    ▼
[Cycle End]
```

**置信边在写 outcome 时同步生成**，不依赖额外的异步流程。

---

## 7. CE 借鉴路径

| 机制 | CE 借鉴 |
|------|---------|
| **置信边事件** | `SearchService` 写入 outcome 后，对 `(signal_type, observation_type)` 边写入独立置信记录；读端实时聚合 |
| **`confidence_gene_outcome` 先验** | `ObservationEntity` 按**观察类型**聚合全局成功率（如 `type=error` 的成功率），作为检索排序的先验因子 |
| **双半衰期（30天/45天）** | CE 对高频观察（每天多条）用短半衰（7天），低频观察用长半衰（30天） |
| **outcome 幂等保护** | `SearchService` outcome 写入使用 `WHERE outcome_recorded = false` 条件，避免重复写入 |
| **外部候选隔离** | Hub 外部检索结果**只影响排序**，不直接写入本地观察库；需经过本地验证后才纳入 |
| **EvolutionEvent 解析** | CE solidify 流程写入 `SolidifyEvent` JSONL，`ContextService` 从中提取验证结果注入上下文 |

**P0**：双半衰期机制 → CE `SearchService` 按观察频率自适应衰减。  
**P1**：outcome 幂等保护 → CE `ObservationEntity` 加 `outcome_recorded` 标志。  
**P2**：外部候选隔离 → CE Hub 检索结果经过本地相关性验证后才影响长期记忆。
