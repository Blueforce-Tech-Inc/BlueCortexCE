# 04 — 图检索与推理机制

## 4.1 核心函数：getMemoryAdvice()

这是记忆图的读取入口，在每次进化 cycle 开始时调用：

```javascript
function getMemoryAdvice({ signals, genes, driftEnabled }) {
  const events = tryReadMemoryGraphEvents(2000);  // 尾部 2000 条
  const edges = aggregateEdges(events);           // Signal→Gene 边聚合
  const geneOutcomes = aggregateGeneOutcomes(events); // Gene→Outcome 边聚合

  const curKey = computeSignalKey(signals);       // 当前信号 key
  const candidateKeys = [];                        // 候选信号 key（精确+相似）

  // 精确匹配：当前 key
  candidateKeys.push({ key: curKey, sim: 1 });
  seenKeys.add(curKey);

  // 相似匹配：Jaccard ≥ 0.34 的历史 key
  for (const ev of events) {
    const k = ev.signal?.key;
    if (seenKeys.has(k)) continue;
    const sim = jaccard(curSignals, ev.signal?.signals || []);
    if (sim >= 0.34) {
      candidateKeys.push({ key: k, sim });
    }
  }

  // 对每个基因，聚合所有候选 key 的边得分
  for (const ck of candidateKeys) {
    for (const g of genes) {
      const edge = edges.get(`${ck.key}::${g.id}`);
      const gEdge = geneOutcomes.get(g.id);

      // Signal→Gene 边得分
      if (edge) {
        const ex = edgeExpectedSuccess(edge, { half_life_days: 30 });
        const weighted = ex.value * ck.sim;  // 相似度加权
        cur.best = Math.max(cur.best, weighted);
        cur.attempts = Math.max(cur.attempts, ex.total);
      }

      // Gene→Outcome 全局先验（独立于信号）
      if (gEdge) {
        const gx = edgeExpectedSuccess(gEdge, { half_life_days: 45 });
        cur.prior = Math.max(cur.prior, gx.value);
      }

      byGene.set(g.id, cur);
    }
  }

  // 综合得分 = best_signal_edge + 0.12 * prior_global
  // 低效路径抑制：attempts ≥ 2 且 best < 0.18 → ban（除非 drift）
  // 稀疏抑制：attempts < 2 且 prior < 0.12 且 prior_attempts ≥ 3 → ban
  // ...

  return { preferredGeneId, bannedGeneIds, explanation };
}
```

## 4.2 Jaccard 相似度

```javascript
function jaccard(aList, bList) {
  // 1. 规范化信号（归一化 errsig）
  const aNorm = normalizeSignalsForMatching(aList);
  const bNorm = normalizeSignalsForMatching(bList);

  // 2. 转 Set
  const a = new Set(aNorm.map(String));
  const b = new Set(bNorm.map(String));

  // 3. Jaccard = |A ∩ B| / |A ∪ B|
  let inter = 0;
  for (const x of a) if (b.has(x)) inter++;
  const union = a.size + b.size - inter;
  return union === 0 ? 0 : inter / union;
}
```

**阈值 0.34**：经验值，平衡精确性和召回率。低于 0.34 的信号重叠被认为不够相似。

## 4.3 边聚合（Edge Aggregation）

```javascript
function aggregateEdges(events) {
  const map = new Map();
  for (const ev of events) {
    if (ev.kind !== 'outcome') continue;
    const signalKey = ev.signal?.key || '(none)';
    const geneId = ev.gene?.id;
    if (!geneId) continue;

    const k = `${signalKey}::${geneId}`;
    const cur = map.get(k) || { signalKey, geneId, success: 0, fail: 0, last_ts: null };

    if (ev.outcome?.status === 'success') cur.success++;
    else if (ev.outcome?.status === 'failed') cur.fail++;

    // 更新时间戳
    const ts = ev.ts || ev.created_at;
    if (ts && Date.parse(ts) > Date.parse(cur.last_ts || 0)) {
      cur.last_ts = ts;
      cur.last_score = ev.outcome?.score;
    }
    map.set(k, cur);
  }
  return map;  // Map<"signalKey::geneId", { success, fail, last_ts, last_score }>
}
```

**仅聚合 `kind === 'outcome'` 事件**：其他类型（signal/hypothesis/attempt）不参与图推理。

## 4.4 置信度计算：Laplace + 指数半衰期

```javascript
function edgeExpectedSuccess(edge, opts) {
  const { success = 0, fail = 0, last_ts = null } = edge;
  const total = success + fail;

  // Laplace 平滑：避免 0/1 极端概率
  const p = (success + 1) / (total + 2);

  // 指数半衰期衰减
  const halfLifeDays = opts?.half_life_days ?? 30;
  const w = decayWeight(last_ts, halfLifeDays);

  return {
    p,           // Laplace 平滑后的成功概率
    w,           // 衰减权重 (0, 1]
    total,       // 总尝试次数
    value: p * w // 综合得分
  };
}

function decayWeight(updatedAtIso, halfLifeDays) {
  if (!Number.isFinite(halfLifeDays) || halfLifeDays <= 0) return 1;
  const ageDays = (Date.now() - Date.parse(updatedAtIso)) / (1000 * 60 * 60 * 24);
  // weight = 0.5^(age / half_life)
  return Math.pow(0.5, ageDays / halfLifeDays);
}
```

**双半衰期设计**：
- Signal→Gene 边：`half_life_days = 30`（较快衰减，信号上下文敏感性高）
- Gene→Outcome 全局边：`half_life_days = 45`（较慢衰减，基因整体表现更稳定）

## 4.5 得分组合公式

```javascript
for (const [geneId, info] of byGene.entries()) {
  // 综合得分 = 信号边得分 + 12% * 全局先验
  const combined = info.best > 0
    ? info.best + info.prior * 0.12
    : info.prior * 0.4;

  scoredGeneIds.push({ geneId, score: combined, attempts: info.attempts, prior: info.prior });

  // 抑制规则：
  // 1. 低效路径：attempts ≥ 2 且 best < 0.18 → ban（无 drift）
  // 2. 稀疏抑制：attempts < 2 且 prior_attempts ≥ 3 且 prior < 0.12 → ban（无 drift）
  if (!driftEnabled && info.attempts >= 2 && info.best < 0.18) {
    bannedGeneIds.add(geneId);
  }
}
```

**12% 加权因子**：经验值，表示"即使没有具体信号经验，基因的全局成功表现也有一定参考价值"。

## 4.6 结果解释（explanation）

```javascript
const explanation = [];
if (preferredGeneId) explanation.push(`memory_prefer:${preferredGeneId}`);
if (bannedGeneIds.size) explanation.push(`memory_ban:${Array.from(bannedGeneIds).slice(0, 6).join(',')}`);
if (preferredGeneId) explanation.push(`gene_prior:${top.prior.toFixed(3)}`);
if (driftEnabled) explanation.push('random_drift:enabled');
return { currentSignalKey, preferredGeneId, bannedGeneIds, explanation };
```

这些 explanation 会被注入到进化 prompt 中，供 LLM 决策参考。

---

_Next: [05-gene-selection.md](./05-gene-selection.md) — 基因选择器详解_
