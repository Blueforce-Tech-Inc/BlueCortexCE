# 76. `selectGeneAndCapsule` 端到端决策管线与 Failed Capsule Ban 机制

**来源**：`EvoMap/evolver/src/gep/selector.js`（419行）；配合 [`65`](./65-selector-gene-scoring-and-semantic-matching-deep-dive.md) 评分机制和 [`24`](./24-gene-strategy-layer.md) Gene/Strategy 层概览。  
**定位**：聚焦 `selectGeneAndCapsule` 决策管线的**完整流程**和 **Failed Capsule Ban** 机制——两者均未在现有文档中深度覆盖。  
**最后更新**：2026-05-03（v1 初稿）。

---

## 1. 入口函数：`selectGeneAndCapsule` 完整管线

```javascript
function selectGeneAndCapsule({ genes, capsules, signals, memoryAdvice,
                                driftEnabled, failedCapsules, capabilityGaps, noveltyScore }) {
  // Step 1: 构建 ban 集合（memoryAdvice + failedCapsules）
  const bannedGeneIds = memoryAdvice?.bannedGeneIds instanceof Set
    ? memoryAdvice.bannedGeneIds : new Set();
  const effectiveBans = banGenesFromFailedCapsules(
    failedCapsules, signals, bannedGeneIds
  );

  // Step 2: 选择基因（评分 + drift + capability gap bonus）
  const { selected, alternatives, driftIntensity } = selectGene(genes, signals, {
    bannedGeneIds: effectiveBans,
    preferredGeneId: memoryAdvice?.preferredGeneId || null,
    driftEnabled: !!driftEnabled,
    capabilityGaps: Array.isArray(capabilityGaps) ? capabilityGaps : [],
    noveltyScore: Number.isFinite(noveltyScore) ? noveltyScore : null,
  });

  // Step 3: 选择胶囊（触发器模式匹配）
  const capsule = selectCapsule(capsules, signals);

  // Step 4: 构造决策解释
  const selector = buildSelectorDecision({ gene: selected, capsule, signals,
    alternatives, memoryAdvice, driftEnabled, driftIntensity });

  return {
    selectedGene: selected,
    capsuleCandidates: capsule ? [capsule] : [],
    selector,
    driftIntensity,
  };
}
```

**四步管线**：ban 构建 → 基因选择 → 胶囊选择 → 决策解释。

**关键输入**：
- `failedCapsules`：近期失败的 capsule 列表（触发 ban 计算）
- `capabilityGaps`：Hub 返回的能力缺口信号（驱动 diversity_directed 漂移）
- `noveltyScore`：探索广度指标（<0.3 时扩展 topN）
- `memoryAdvice`：来自 Memory Graph 的 `getMemoryAdvice`（preferredGeneId / bannedGeneIds）

---

## 2. Failed Capsule Ban 机制（`banGenesFromFailedCapsules`）

```javascript
const FAILED_CAPSULE_BAN_THRESHOLD = 2;  // 失败次数阈值
const FAILED_CAPSULE_OVERLAP_MIN = 0.6;  // 信号重叠率阈值

function banGenesFromFailedCapsules(failedCapsules, signals, existingBans) {
  const bans = new Set(existingBans);
  if (!Array.isArray(failedCapsules) || failedCapsules.length === 0) return bans;

  const geneFailCounts = {};
  for (const fc of failedCapsules) {
    if (!fc?.gene) continue;

    // 计算当前信号与 capsule 触发信号的 overlap
    const overlap = computeSignalOverlap(signals, fc.trigger || []);
    if (overlap < FAILED_CAPSULE_OVERLAP_MIN) continue;  // 重叠不足，跳过

    const gid = String(fc.gene);
    geneFailCounts[gid] = (geneFailCounts[gid] || 0) + 1;
  }

  // 失败次数达到阈值 → ban
  for (const [gid, count] of Object.entries(geneFailCounts)) {
    if (count >= FAILED_CAPSULE_BAN_THRESHOLD) {
      bans.add(gid);
    }
  }
  return bans;
}
```

### 2.1 `computeSignalOverlap`：连续重叠率

```javascript
function computeSignalOverlap(signalsA, signalsB) {
  if (!signalsA?.length || !signalsB?.length) return 0;
  const setB = new Set(signalsB.map(s => String(s).toLowerCase()));
  let hits = 0;
  for (const s of signalsA) {
    if (setB.has(String(s).toLowerCase())) hits++;
  }
  return hits / Math.max(signalsA.length, 1);  // overlap ∈ [0, 1]
}
```

**重叠率 = 命中数 / max(len(A), 1)**：不是简单的"是否命中"，而是**连续比例**。

**双重门禁**：
1. `overlap ≥ 0.6`（信号重叠够多，不是边缘情况才计入）
2. `failCount ≥ 2`（单次失败不 ban，需要**重复失败**才触发）

### 2.2 设计思想

| 特性 | 含义 |
|------|------|
| **连续 overlap 而非布尔命中** | `0.6` 阈值意味着：10 个信号中至少 6 个重叠才算有效——避免因单个噪声信号就 ban |
| **两次失败才 ban** | 单次失败可能是偶然；两次以上才反映"该基因对此类信号无效" |
| **overlap 门控** | 即使同一个 capsule 失败 3 次，如果信号类型完全不同（overlap < 0.6），也不 ban |
| **叠加 existingBans** | memoryAdvice 的 ban + failedCapsule 的 ban 合并，防止双重积累 |
| **`geneFailCounts` 重置** | 每次调用重新计算（基于当前 `failedCapsules` 窗口），不会无限累积 |

### 2.3 对 BlueCortexCE 的借鉴

**重复失败观察类型降权（Pending）**：

BlueCortexCE `SearchService` 可以类似地实现"同类观察多次失败后降权"：

```sql
-- 示例：observation_type 失败计数窗口查询
SELECT observation_type,
       COUNT(*) FILTER (WHERE outcome = 'failure') as fail_count,
       COUNT(*) as total_count
FROM observations
WHERE session_id IN (
  SELECT id FROM sessions WHERE user_id = :userId ORDER BY created_at DESC LIMIT :windowSize
)
GROUP BY observation_type
HAVING COUNT(*) >= 2
   AND COUNT(*) FILTER (WHERE outcome = 'failure')::float / COUNT(*) > 0.6;
```

**实现路径**：
1. 在 `ObservationEntity` 增加 `outcome` 字段（success/failure/unknown）
2. 在 `SearchService` 查询时，过滤掉 fail_count ≥ 2 且 fail_ratio > 0.6 的 observation_type
3. 使用 session 时间窗口（最近 N 个 session）而非全局计数
4. 该机制与 `time_decay_score` 和 `fail_penalty` 互补（doc 20）：后者是衰减排序，前者是直接过滤

**P1**：同 observation_type 在最近 3 个 session 内失败 2 次以上 → 降权至底部  
**P2**：overlap 相似性（信号类型重叠率）用于更细粒度的观察类型关联降权

---

## 3. 连续 Drift 强度（`computeDriftIntensity`）

### 3.1 从二元开关到连续谱

```javascript
// Old: binary driftEnabled (true/false)
// New: continuous driftIntensity ∈ [0, 1]
//   0 = pure selection (no drift)
//   1 = pure random drift

function computeDriftIntensity(opts) {
  const driftEnabled = !!(opts?.driftEnabled);
  const effectivePopulationSize = Number(opts?.effectivePopulationSize) || null;
  const genePoolSize = Number(opts?.genePoolSize) || null;
  const ne = effectivePopulationSize || genePoolSize || null;

  if (driftEnabled) {
    // 显式启用漂移：1/√Ne + 0.3（最小 0.7）
    return ne && ne > 1 ? Math.min(1, 1 / Math.sqrt(ne) + 0.3) : 0.7;
  }

  if (ne != null && ne > 0) {
    // 自然漂移：种群越小，漂移越强（遗传学原理）
    // Ne=1:  1.0 (pure drift)
    // Ne=25: 0.2
    // Ne=100: 0.1
    return Math.min(1, 1 / Math.sqrt(ne));
  }

  return 0; // 无漂移信息，纯粹选择
}
```

### 3.2 遗传学背景：`1/√Ne`

来自种群遗传学：**有效种群规模（Ne）越小，遗传漂变越强**。
- 大种群：基因频率变化小，自然选择主导
- 小种群：随机性主导，漂变强烈

Evolver 用 `1/√Ne` 公式量化这个效应，并加上 0.3 偏置避免小种群时 drift 过低。

### 3.3 五种 Drift Mode

| Mode | 触发条件 | 行为 |
|------|----------|------|
| `memory_preferred` | `preferredGeneId` 命中且在候选中 | 强制选择推荐基因（drift 覆盖） |
| `diversity_directed` | `driftIntensity > 0.15` + `capabilityGaps` 非空 | 从覆盖 gap 的基因中按 drift 概率随机 |
| `random_weighted` | `driftIntensity > 0.15` + 无 gap | 从过滤后基因中按 drift 概率随机 |
| `random` | `driftIntensity > 0.15` + 纯随机 | 完全随机（无视分数） |
| `selection` | 以上均不满足 | 按得分排序选择 |
| `none` | 无候选基因 | 返回 null |

### 3.4 `noveltyScore` 对 `topN` 的动态扩展

```javascript
// 如果 noveltyScore < 0.3，扩大候选池 topN
const topN = Math.min(filtered.length,
  Math.max(2, Math.ceil(filtered.length * driftIntensity)));
```

当系统处于"低探索"状态（noveltyScore < 0.3）时，扩展候选数量以增加探索多样性。

### 3.5 对 BlueCortexCE 的借鉴

**SearchService 的探索性配置**：

| Drift 概念 | CE 翻译 |
|-----------|---------|
| `driftIntensity` | 候选 observation 数量少时（冷启动），增加探索性 |
| `noveltyScore < 0.3` → 扩大 topN | 用户查询新颖度高时（罕见概念），扩展候选集 |
| `diversity_directed` | 当检测到 `capabilityGaps`（用户问题类型缺失）时，从不同类型的 observations 中采样 |
| `memory_preferred` | 用户有明确偏好（`preferredGeneId`）时，优先返回同类 observation |

**可配置参数**：
```yaml
# SearchService 配置
search:
  drift_intensity: 0.0    # 0=纯得分排序, 0.3=适度探索, 1.0=纯随机
  novelty_threshold: 0.3   # 低于此阈值扩展候选池
  min_candidates: 3        # 最小候选数（避免冷启动时候选过少）
```

**P2**：实现基于候选数量的动态 drift intensity（候选少 → drift 高 → 更多探索）

---

## 4. Capability Gap Directed Drift

```javascript
// selectGene 内部（简化）
if (driftIntensity > 0 && capabilityGaps.length > 0) {
  // 从覆盖 gap 的基因中按 driftIntensity 概率随机
  const gapCovering = scored.filter(x => genesCoverCapabilityGap(x.gene, capabilityGaps));
  // 如果 gapCovering 非空，优先从 gapCovering 中选择
}
```

### 4.1 `genesCoverCapabilityGap` 判定

```javascript
function genesCoverCapabilityGap(gene, capabilityGaps) {
  // 检查基因的 signals_match patterns 是否与 capability gap 信号匹配
  // 逻辑同 matchPatternToSignals（regex / 多语言别名 / 子串）
  for (const gap of capabilityGaps) {
    for (const pat of gene.signals_match || []) {
      if (matchPatternToSignals(pat, [gap])) return true;
    }
  }
  return false;
}
```

### 4.2 Capability Gap 的来源

从 `curriculum.js` 的 `generateCurriculumSignals` 注入：

```javascript
// curriculum.js §2
if (capabilityGaps?.length > 0) {
  signals.push(...generateCurriculumSignals(capabilityGaps));
}
```

`capabilityGaps` 来自 Hub 返回的"能力缺口"信号，经过 `curriculum.js` 格式化为 `CURRICULUM_CAPABILITY_GAP_*` 信号后，注入到 `selectGene` 的候选集中。

### 4.3 对 BlueCortexCE 的借鉴

**基于问题类型（Problem Type）的自适应候选扩展**：

CE `SearchService` 目前没有 problem type 概念，可以借鉴：

```typescript
// 伪代码：problem type → required observation types
const PROBLEM_TYPE_OBSERVATION_MAP = {
  'error_investigation': ['error', 'stack_trace', 'failure_mode'],
  'performance_optimization': ['performance', 'latency', 'bottleneck'],
  'feature_request': ['user_preference', 'feature_request', 'capability_gap'],
  'general': []  // 无限制
};

function searchWithProblemContext(query, problemType) {
  const requiredTypes = PROBLEM_TYPE_OBSERVATION_MAP[problemType] || [];
  if (requiredTypes.length > 0) {
    // Boost 特定类型的 observations
    return searchService.search(query, {
      requiredObservationTypes: requiredTypes,
      boostFactor: 1.5
    });
  }
  return searchService.search(query);
}
```

**P2**：当用户问题落入特定 problem type 时，自动 boost 相关 observation types（类似 `capabilityGap bonus`）

---

## 5. 决策解释（`buildSelectorDecision`）

```javascript
function buildSelectorDecision({ gene, capsule, signals, alternatives,
                                 memoryAdvice, driftEnabled, driftIntensity }) {
  const reason = [];
  if (gene) reason.push('signals match gene.signals_match');
  if (capsule) reason.push('capsule trigger matches signals');
  if (!gene) reason.push('no matching gene found; new gene may be required');
  if (signals?.length) reason.push(`signals: ${signals.join(', ')}`);
  if (memoryAdvice?.explanation?.length) {
    reason.push(`memory_graph: ${memoryAdvice.explanation.join(' | ')}`);
  }
  if (driftEnabled) reason.push('random_drift_override: true');
  if (driftIntensity > 0) {
    reason.push(`drift_intensity: ${driftIntensity.toFixed(3)}`);
  }

  return {
    selected: gene?.id || null,
    reason,                                           // 人类可读原因列表
    alternatives: alternatives?.map(g => g.id) || [],  // 备选基因
  };
}
```

**可观测性设计**：每次选择都附带：
- `reason[]`：为什么选择这个基因（信号匹配 / memory_graph / drift）
- `alternatives[]`：排名靠前的备选项（用于调试和人类审查）
- `driftIntensity`：当前漂移强度（可追踪何时发生探索性选择）

### 5.1 对 BlueCortexCE 的借鉴

**SearchService 结果可解释性**：

CE `SearchService` 目前返回结果时缺少"为什么返回这个"的解释。可以借鉴：

```json
{
  "observations": [...],
  "searchExplanation": {
    "query": "...",
    "matchedSignals": ["error", "database"],
    "driftIntensity": 0.0,
    "topAlternatives": ["obs_id_2", "obs_id_5"],
    "filterReason": null
  }
}
```

**P2**：在 `SearchResult` 中增加 `explanation` 字段，记录匹配的信号类型、排序理由、过滤掉的候选项

---

## 6. 完整管线数据流总结

```
evolve.js cycle
    │
    ├── signals[] ──────────→ selectGeneAndCapsule()
    │                                  │
    ├── failedCapsules[] ───→ banGenesFromFailedCapsules()
    │                                  │
    ├── memoryAdvice ────────→ bannedGeneIds + preferredGeneId
    │                         (getMemoryAdvice from MemoryGraph)
    │                                  │
    ├── capabilityGaps[] ─────→ selectGene() [diversity_directed drift]
    │                         (from Hub / curriculum.js)
    │                                  │
    ├── noveltyScore ────────→ selectGene() [expand topN]
    │                         (from localStateAwareness)
    │                                  │
    ├── genes[] ──────────────→ scoreGene() + scoreGeneLearning()
    │                         │   exact × 1.0 + tag × 0.6 + semantic × 0.4
    │                         │   + epigenetic_boost + anti_pattern_penalty
    │                         ├── computeDriftIntensity()
    │                         │   (1/√Ne continuous spectrum)
    │                         ├── selectGene()
    │                         │   (memory_preferred → diversity_directed →
    │                         │    random_weighted → selection)
    │                         └── return: selectedGene + alternatives
    │
    ├── capsules[] ─────────→ selectCapsule()
    │                         (trigger pattern match)
    │
    └── buildSelectorDecision()
                              (reason[] + alternatives[])

Result:
  { selectedGene, capsuleCandidates, selector: { selected, reason, alternatives }, driftIntensity }
```

---

## 7. 与 BlueCortexCE 的对照

| Evolver 机制 | CE 类比 | 现状 | 借鉴优先级 |
|-------------|--------|------|-----------|
| Failed capsule ban（overlap ≥ 0.6 + fail ≥ 2） | 同 observation_type 重复失败后降权 | 无 | **P1** |
| 连续 drift intensity（`1/√Ne`） | 候选少时增加探索性 | 无 | P2 |
| `noveltyScore < 0.3` → 扩展 topN | 用户问题新颖时扩展候选集 | 无 | P2 |
| Capability gap directed drift | Problem type → observation type boost | 无 | P2 |
| `buildSelectorDecision` 可解释性 | SearchResult.explanation | 无 | P2 |
| `gene.anti_patterns` 惩罚 | Observation type anti-pattern 去排名 | 部分（`time_decay_score`） | P2 |

---

## 8. 未决问题

- [ ] **Failed capsule ban 的时间窗口**：当前 `failedCapsules` 传入多少条记录？是全量还是滑动窗口？需要在 `evolve.js` 中确认。
- [ ] **noveltyScore 的计算来源**：`localStateAwareness` 如何计算 `noveltyScore`？与 CE 的"用户问题新颖度"如何对应？
- [ ] **`effectivePopulationSize` vs `genePoolSize`**：两者在什么场景下不同？是否反映"活跃基因数"vs"总基因数"的区别？

---

*状态：v1 初稿。后续可补充 doc 74 `curriculum.js` 如何生成 `capabilityGaps` 并传入 `selectGeneAndCapsule` 的完整链路图。*
