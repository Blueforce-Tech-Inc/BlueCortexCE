# 2. MemoryGraph 核心

## 2.1 设计哲学

**Append-only JSONL 事件图谱** —— 所有记忆操作都是追加写入，不修改历史。状态由轻量级 JSON 文件单独管理（`memory_graph_state.json`）。

```
memory_graph.jsonl    ← append-only，所有事件永久保留
memory_graph_state.json  ← atomic 读写，当前状态快照
```

## 2.2 事件类型

| kind | 触发时机 | 关键字段 |
|------|----------|----------|
| `signal` | 信号快照记录 | `signal.key`, `signal.signals[]`, `signal.error_signature` |
| `hypothesis` | 基因选择时记录假设 | `hypothesis.id`, `mutation`, `gene`, `action` |
| `attempt` | 执行动作时记录 | `action.id`, `hypothesis.id`, `capsules.used` |
| `outcome` | 结果评估时记录 | `outcome.status`, `outcome.score`, `baseline` |
| `confidence_edge` | 结果推导置信边 | `stats.{p,decay_weight,value}`, `half_life_days` |
| `confidence_gene_outcome` | 基因全局结果置信 | 同上 |
| `external_candidate` | 外部 Capsule 接入 | `asset.type/id`, `candidate.trigger/gene` |

## 2.3 核心数据结构

### Signal Key（信号键）

信号键是 Jaccard 相似度匹配的锚点：

```js
function computeSignalKey(signals) {
  const list = normalizeSignalsForMatching(signals);
  // 规范化：errsig 提取 + 路径/数字匿名化
  // 按字母排序去重，用 "|" 连接
  const uniq = Array.from(new Set(list.filter(Boolean))).sort();
  return uniq.join('|') || '(none)';
}
```

**规范化规则**：
- `errsig:<raw>` → 提取 error signature → `errsig_norm:<stableHash>`
- 路径替换为 `<path>`（Windows/Unix 分别处理）
- 十六进制/数字替换为 `<hex>` / `<n>`

### State 文件（memory_graph_state.json）

```json
{
  "last_action": {
    "action_id": "act_xxx",
    "signal_key": "log_error|errsig_norm:abc123",
    "signals": ["log_error", "errsig:TypeError: ..."],
    "mutation_id": "fix_null_pointer",
    "gene_id": "repair_stability_v2",
    "hypothesis_id": "hyp_xxx",
    "capsules_used": ["capsule_safe_rollback_v1"],
    "had_error": true,
    "outcome_recorded": false,
    "baseline_observed": { "recent_error_count": 5, "scan_ms": 1200 }
  }
}
```

State 是**可变的**，在 `recordAttempt` 时写入当前动作，在 `recordOutcomeFromState` 时更新结果标记。事件本身永远追加。

## 2.4 边聚合（Edge Aggregation）

### Signal → Gene 边

```js
function aggregateEdges(events) {
  // 按 (signal_key, gene_id) 分组统计 success/fail 次数
  // Laplace 平滑：p = (success + 1) / (total + 2)
}
```

### Gene → Outcome 边（基因全局成功率）

```js
function aggregateGeneOutcomes(events) {
  // 按 gene_id 分组，独立于信号
  // 作为 stabilizer：当信号边稀疏时提供先验概率
}
```

### 指数半衰期衰减

```js
function decayWeight(updatedAtIso, halfLifeDays) {
  // weight = 0.5^(ageDays / halfLifeDays)
  const ageDays = (Date.now() - Date.parse(updatedAtIso)) / (1000*60*60*24);
  return Math.pow(0.5, ageDays / halfLifeDays);
}
```

**默认半衰期**：Signal→Gene 边 30天，Gene→Outcome 边 45天。

### 期望成功概率

```js
function edgeExpectedSuccess(edge, opts) {
  const p = (success + 1) / (total + 2); // Laplace
  const w = decayWeight(last_ts, half_life_days);
  return { p, w, total, value: p * w };
}
```

## 2.5 记忆建议（getMemoryAdvice）

```js
function getMemoryAdvice({ signals, genes, driftEnabled }) {
  // 1. 聚合最近 2000 条事件得到边
  // 2. 计算当前信号键 + Jaccard 相似候选键（sim ≥ 0.34）
  // 3. 对每个基因：
  //    - 信号边得分 = edge_value × signal_sim
  //    - 基因全局先验 = gene_outcome_value × 0.12
  //    - combined = signal_best + prior * 0.12
  // 4. 抑制规则：
  //    - attempts ≥ 2 且 best < 0.18 → ban（除非 drift）
  //    - attempts < 2 但 prior_attempts ≥ 3 且 prior < 0.12 → ban
  // 5. 返回 preferredGeneId + bannedGeneIds + explanation
}
```

## 2.6 读取优化：Tail-Only 读取

```js
function tryReadMemoryGraphEvents(limitLines = 2000) {
  // 文件 < 512KB：全量读取
  // 文件 ≥ 512KB：从尾部读取 512KB，然后取最后 limitLines 行
  // 这避免了加载 GB 级日志文件的内存爆炸
}
```
