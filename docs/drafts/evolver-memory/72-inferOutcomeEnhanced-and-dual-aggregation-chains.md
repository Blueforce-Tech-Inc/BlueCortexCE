# `72` `inferOutcomeEnhanced` Baseline/Current Delta 机制 + 双聚合链

**文件**: `docs/drafts/evolver-memory/72-inferOutcomeEnhanced-and-dual-aggregation-chains.md`  
**目标**: 分析 Evolver `inferOutcomeEnhanced` baseline vs current delta 机制 + `getMemoryAdvice` 双聚合链  
**数据来源**: `memoryGraph.js` 本地 v1.47.0（`e72778e`）非混淆源码  
**最后更新**: 2026-05-03

---

## 1. `inferOutcomeEnhanced` Baseline/Current Delta

### 1.1 定位

`memoryGraph.js` L551–L592，`inferOutcomeEnhanced` 是 `outcome` 推断的增强层，位于 `inferOutcomeFromSignals`（信号推断）之后，作为启发式微调。

### 1.2 完整逻辑

```javascript
function inferOutcomeEnhanced({ prevHadError, currentHasError, baselineObserved, currentObserved }) {
  // Step 1: 尝试从实际证据（evidence log）解析 observed outcome
  const combinedEvidence = `${evidence.recent_session_tail}\n${evidence.today_log_tail}`;
  const observed = tryParseLastEvolutionEventOutcome(combinedEvidence);
  if (observed) return observed;  // 直接命中 → 返回真实 outcome

  // Step 2: 从信号推断 base outcome
  const base = inferOutcomeFromSignals({ prevHadError, currentHasError });

  // Step 3: error_count delta 微调（±0.12）
  const prevErrCount = baselineObserved?.recent_error_count;  // 基因创建时的错误数
  const curErrCount  = currentObserved?.recent_error_count;   // 当前错误数
  if (prevErrCount != null && curErrCount != null) {
    const delta = prevErrCount - curErrCount;                 // 减少错误 → 正 delta
    score += Math.max(-0.12, Math.min(0.12, delta / 50)); // clamp 到 ±0.12
  }

  // Step 4: scan_ms delta 微调（±0.06）
  const prevScan = baselineObserved?.scan_ms;                // 基因创建时的扫描时间
  const curScan  = currentObserved?.scan_ms;                // 当前扫描时间
  if (prevScan != null && curScan != null && prevScan > 0) {
    const ratio = (prevScan - curScan) / prevScan;         // 减少扫描时间 → 正 ratio
    score += Math.max(-0.06, Math.min(0.06, ratio));       // clamp 到 ±0.06
  }

  return { status: base.status, score: clamp01(score), note: `${base.note}|heuristic_delta` };
}
```

### 1.3 关键参数

| 维度 | 指标 | Delta 来源 | Score 影响 | 上限 |
|------|------|-----------|-----------|------|
| **错误数** | `recent_error_count` | baseline − current | ±0.12 | `\|delta\|/50 ≤ 0.12` |
| **扫描时间** | `scan_ms` | (baseline − current) / baseline | ±0.06 | `\|ratio\| ≤ 0.06` |

**解读**：
- `delta = baseline − current`：减少错误 → 正向奖励（gene 有效）
- `ratio`：扫描时间减少比例 → 正向奖励（gene 有效）
- 两个 delta 叠加到 base score 上，`clamp01` 保证最终在 `[0, 1]`

### 1.4 何时使用

该函数在 `evolve.js` 的 gene 评估阶段被调用（`L657`），当需要对一个 gene 的 outcome 进行评分时：

- 优先使用真实证据（`tryParseLastEvolutionEventOutcome`）直接推断
- 真实证据不足时，用信号推断 + delta 启发式微调

### 1.5 CE 借鉴路径

| 优先级 | 借鉴点 | CE 映射 |
|--------|--------|---------|
| **P1** | baseline/current 双时点对比启发式 | ObservationEntity 中存储 `baseline_metrics`（首次观察到问题时的指标），后续同类型观察与之对比，动态调整"问题严重程度"评分 |
| **P2** | `clamp01` 边界保护 | 任何动态 score 计算后 clamp 到 [0, 1] |
| **P2** | 双指标叠加（错误数 + 扫描时间） | 复合质量评分：多个维度分别计算 delta 后加权求和 |

---

## 2. 双聚合链：`getMemoryAdvice` 的两条独立衰减链

### 2.1 背景

`getMemoryAdvice`（在 `memoryGraph.js` 中实现）维护两条独立的记忆链：

```
链 1: (signal_key, gene_id) 边 → confidence_edge
       半衰期: 30 天
       覆盖: 该信号在该基因上的具体表现

链 2: gene_id 先验 → confidence_gene_outcome
       半衰期: 45 天
       覆盖: 该基因的全局表现（跨所有信号）
```

### 2.2 组合策略

```javascript
// 策略 1: 混合最佳 + 先验加权
bestEdgeScore + priorScore * 0.12

// 策略 2: 纯先验（当边数据不足时）
priorScore * 0.4
```

### 2.3 设计原理

| 链 | 半衰期 | 粒度 | 用途 |
|----|--------|------|------|
| **(signal, gene) 边** | 30 天 | 细粒度（信号×基因） | 短期、特定信号的基因效果 |
| **gene 先验** | 45 天 | 粗粒度（基因级别） | 长期、基因整体质量 |

- 30 天 < 45 天：信号×基因的记忆更短期（容易随任务变化）；基因整体表现更长期（基因能力相对稳定）
- `×0.12` 权重：先验影响小，主要依赖细粒度边数据

### 2.4 CE 借鉴路径

| 优先级 | 借鉴点 | CE 映射 |
|--------|--------|---------|
| **P1** | 双链衰减设计 | `SearchService` 中：对同一 query，历史检索成功率（信号×观察类型）和整体观察质量（类型级别）分别计算，加权组合 |
| **P1** | 细粗粒度分离 | Observation 的 `type` 级别衰减（粗）和 `content_hash` 级别衰减（细）分别计算 |
| **P2** | `×0.12` 小权重先验 | 防止冷启动时没有历史数据可用；提供平滑的 fallback |

---

## 3. 总结：CE 可落地的设计

### 3.1 近期可实现（Phase 3+）

1. **Observation 质量评分增强**：在 `ObservationEntity` 中新增 `baselineMetrics` JSONB 字段（如 `{"error_count": 5, "scan_ms": 1200}`），存储首次观察到该类型问题时的指标。后续同类型观察与之对比，动态调整严重程度评分。

2. **双链搜索排序**：
   ```sql
   -- 链 1: content_hash 级别的历史成功率（30天半衰）
   -- 链 2: observation type 级别的整体成功率（45天半衰）
   SELECT 
     o.*,
     o.score * 0.7 + 
     COALESCE(type_success_rate(o.type, now() - interval '45 days') * 0.12, 0) +
     COALESCE(hash_success_rate(o.content_hash, now() - interval '30 days') * 0.18, 0) as adjusted_score
   FROM observations o
   ORDER BY adjusted_score DESC;
   ```

3. **Outcome score clamp**：所有动态计算的 score 统一 clamp 到 `[0, 1]`。

### 3.2 架构备注

- `inferOutcomeEnhanced` 的 baseline 数据（`baselineObserved`）来自 gene 创建时快照，存储在 `MemoryGraphEvent` 中
- CE 的等价物：首次 `type=error` 观察时的 metrics 作为该类型的 baseline
- `clamp01` 确保任何启发式调整不会导致极端值
