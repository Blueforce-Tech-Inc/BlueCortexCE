# 5. 结果追踪与衰减

## 5.1 结果推断（Outcome Inference）

`recordOutcomeFromState()` 从 `last_action` state 和当前信号推断结果：

### 基础推断规则

```js
function inferOutcomeFromSignals({ prevHadError, currentHasError }) {
  if (prevHadError && !currentHasError)
    return { status: 'success', score: 0.85, note: 'error_cleared' };
  if (prevHadError && currentHasError)
    return { status: 'failed', score: 0.2, note: 'error_persisted' };
  if (!prevHadError && currentHasError)
    return { status: 'failed', score: 0.15, note: 'new_error_appeared' };
  return { status: 'success', score: 0.6, note: 'stable_no_error' };
}
```

### 增强推断（Enhanced Inference）

`inferOutcomeEnhanced()` 在基础规则上叠加启发式 delta：

```js
// 错误数量变化
if (prevErrCount != null && curErrCount != null) {
  const delta = prevErrCount - curErrCount;
  score += Math.max(-0.12, Math.min(0.12, delta / 50));
}

// 扫描性能变化
if (prevScan > 0) {
  const ratio = (prevScan - curScan) / prevScan;
  score += Math.max(-0.06, Math.min(0.06, ratio));
}
```

### EvolutionEvent 溯源

```js
// 从日志末尾 400 行中解析最近的 EvolutionEvent JSON
// 提取其中的 outcome.status 和 outcome.score
function tryParseLastEvolutionEventOutcome(evidenceText) {
  // 扫描末尾 400 行，查找包含 "type":"EvolutionEvent" 的行
  // 解析 outcome.status 和 outcome.score
}
```

## 5.2 置信边（Confidence Edge）

每次记录 outcome 时，派生两类置信边事件：

### Signal→Gene 边（半衰期 30 天）

```js
buildConfidenceEdgeEvent({
  signalKey, geneId, outcomeEventId, halfLifeDays: 30
})
// 输出 kind: 'confidence_edge'
```

### Gene→Outcome 边（半衰期 45 天）

```js
buildGeneOutcomeConfidenceEvent({
  geneId, outcomeEventId, halfLifeDays: 45
})
// 输出 kind: 'confidence_gene_outcome'
```

两类边都是 append-only，供 `getMemoryAdvice()` 在读取时重新聚合。

## 5.3 衰减模型

**指数半衰期衰减**：

```js
function decayWeight(updatedAtIso, halfLifeDays) {
  const ageDays = (Date.now() - Date.parse(updatedAtIso)) / (1000*60*60*24);
  return Math.pow(0.5, ageDays / halfLifeDays);
}
```

| 边类型 | 半衰期 | 用途 |
|--------|--------|------|
| Signal→Gene | 30 天 | 近期信号-基因关联置信度 |
| Gene→Outcome | 45 天 | 基因全局成功率的长期先验 |

**衰减曲线示例**（30 天半衰期）：

| 时间 | 衰减权重 |
|------|----------|
| 即时 | 1.0 |
| 30 天 | 0.5 |
| 60 天 | 0.25 |
| 90 天 | 0.125 |

## 5.4 Laplace 平滑

```js
const p = (success + 1) / (total + 2);
```

- `total = 0`（无数据）→ `p = 0.5`（最大不确定性）
- `total = 1` 且 `success = 0` → `p = 1/4 = 0.25`
- `total = 10` 且 `success = 10` → `p = 11/12 ≈ 0.917`

**组合得分**：`value = p × decay_weight`

## 5.5 边聚合在读取时计算

关键设计：**边聚合不在写入时计算，而是每次 `getMemoryAdvice()` 读取时从 JSONL 重新聚合**。

```js
function getMemoryAdvice({ signals, genes, driftEnabled }) {
  const events = tryReadMemoryGraphEvents(2000);  // 每次重新读取
  const edges = aggregateEdges(events);            // 每次重新聚合
  const geneOutcomes = aggregateGeneOutcomes(events);
  // ...
}
```

这确保：
1. 旧事件可以独立过期（通过 decay weight）
2. 不需要维护增量更新的边状态
3. append-only 的 JSONL 是唯一的真相来源
