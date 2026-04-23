# 多因子 Gene 选择与连续漂移机制

> **来源**：`EvoMap/evolver/src/gep/selector.js`（~280 行）  
> **补充**：[`21`](./21-signal-taxonomy-and-gene-selection-memory.md)（标签重叠评分）、[`24`](./24-gene-strategy-layer.md)（Gene/Strategy 层）、[`25`](./25-advanced-patterns-prm-epigenetic-antipattern.md)（Epigenetic / Anti-Pattern）  
> **最后更新**：2026-04-23

---

## 1. 选择管线总览

`selectGeneAndCapsule` 是入口函数，编排完整的 Gene 选择流程：

```
signals + genes + capsules + memoryAdvice + failedCapsules + capabilityGaps
    │
    ├─ banGenesFromFailedCapsules() → effectiveBans
    │
    ├─ selectGene(genes, signals, {bannedGeneIds, preferredGeneId, driftEnabled, capabilityGaps, noveltyScore})
    │   ├─ scoreGene() × N genes → scored[]
    │   ├─ scoreGeneLearning() 加分/减分
    │   ├─ memoryAdvice preferredGeneId 覆盖
    │   ├─ bannedGeneIds 过滤
    │   └─ driftIntensity 随机/定向选择
    │
    ├─ selectCapsule(capsules, signals)
    │
    └─ buildSelectorDecision() → 可解释决策记录
```

---

## 2. Gene 评分：四因子叠加

### 2.1 `scoreGene(gene, signals)` — 基础匹配分

```
score = signalMatchScore + tagOverlapScore × 0.6 + semanticScore × SEMANTIC_WEIGHT
```

| 因子 | 实现 | 权重 |
|------|------|------|
| **精确模式匹配** | `gene.signals_match` 中每个 pattern 与 signals 做 `matchPatternToSignals` | 1.0/命中 |
| **标签重叠** | `scoreTagOverlap`（`learningSignals.expandSignals` 分类后计数） | ×0.6 |
| **语义相似度** | `scoreGeneSemantic`（bag-of-words cosine） | ×SEMANTIC_WEIGHT (默认 0.4) |

### 2.2 `matchPatternToSignals` — 三种匹配模式

```javascript
// 1. 正则模式: /body/flags
/^repair|fix/i  →  正则匹配 signals

// 2. 多语言别名: "en_term|zh_term|ja_term"
"add feature|加个功能|機能追加"  →  任一分支命中即匹配

// 3. 子串匹配（默认）
"log_error"  →  signals 中任一包含即命中
```

### 2.3 `scoreGeneLearning(gene, signals, envFingerprint)` — 学习历史

```javascript
// 最近 8 条 learning_history
for entry in history:
  if outcome === 'success':  boost += 0.12
  if mode === 'hard':        boost -= 0.22
  if mode === 'soft':        boost -= 0.08

// Epigenetic marks（环境上下文相关）
boost += epigeneticMark.boost  // "darwin/arm64/v22.x" 等

// Anti-patterns（失败模式惩罚）
for antiPattern in gene.anti_patterns.slice(-6):
  if antiPattern.learning_signals 与当前 signals 有重叠:
    boost -= (mode === 'hard' ? 0.4 : 0.18)

clamp(boost, -1.5, 1.5)
```

**关键设计**：anti-pattern 惩罚比成功奖励更大（0.4 vs 0.12），体现"避免重复错误"优先于"重复成功"的原则。

---

## 3. 连续漂移强度（`computeDriftIntensity`）

### 3.1 核心公式：群体遗传学 `1/√Ne`

```javascript
// Ne = effective population size = 活跃 gene 数量
driftIntensity = 1 / sqrt(Ne)
```

| Ne | driftIntensity | 含义 |
|----|---------------|------|
| 1 | 1.0 | 纯漂移（无选择压力） |
| 4 | 0.5 | 中等漂移 |
| 25 | 0.2 | 弱漂移 |
| 100 | 0.1 | 极弱漂移 |

**显式 drift 模式**下：`intensity = min(1, 1/√Ne + 0.3)`（额外 +0.3 偏移）

### 3.2 漂移模式判定

```javascript
useDrift = driftEnabled || driftIntensity > 0.15
// 即使未显式开启 drift，如果 gene 池很小（Ne ≤ 44），也会自动进入漂移模式
```

### 3.3 三种漂移策略

当 `driftIntensity > 0` 且 `Math.random() < driftIntensity` 时触发：

| 策略 | 触发条件 | 行为 |
|------|----------|------|
| **`diversity_directed`** | 有 `capabilityGaps` 且有 gene 能覆盖 gap | 按 gap 覆盖度排序选择 |
| **`random_weighted`** | 有 `capabilityGaps` 但无 gene 覆盖 | 在 top-N 候选中随机（N 受 `noveltyScore` 影响） |
| **`random`** | 无 capabilityGaps | 在 top-N 候选中随机 |

**noveltyScore 调节**：当 `noveltyScore < 0.3`（agent 与他人过于相似）时，扩大随机选择范围以增加探索。

### 3.4 Failed Capsules Ban

```javascript
// 如果 capsule 失败且信号重叠 ≥60%，累计失败计数
// 同一 gene 失败 ≥2 次 → ban
for failedCapsule in failedCapsules:
  overlap = computeSignalOverlap(currentSignals, failedCapsule.trigger)
  if overlap >= 0.6:
    geneFailCounts[failedCapsule.gene]++
    if geneFailCounts[gene] >= 2:
      bannedGeneIds.add(gene)
```

---

## 4. `selectGeneAndCapsule` 集成点

### 4.1 MemoryAdvice 集成

```javascript
const preferredGeneId = memoryAdvice?.preferredGeneId;  // from getMemoryAdvice
const bannedGeneIds = memoryAdvice?.bannedGeneIds;      // from getMemoryAdvice

// preferredGeneId 只在 gene 已经是匹配候选时才覆盖
// bannedGeneIds 与 failed capsule bans 合并为 effectiveBans
```

### 4.2 可解释决策（`buildSelectorDecision`）

每次选择都生成一个 `selector` 对象，包含：
- `selected`: 选中的 gene id
- `reason`: 匹配原因列表（signals match、memory_graph advice、drift 等）
- `alternatives`: 备选 gene id 列表
- `driftIntensity`: 当前漂移强度

---

## 5. 设计思想

### 5.1 连续 vs 二元

传统 drift 是二元开关（on/off）。Evolver 将其改为**连续光谱**，受群体大小自然调节。小 gene 池自动增加探索，大 gene 池自动偏向利用。

### 5.2 定向 vs 随机

纯随机漂移浪费计算资源。`diversity_directed` drift 用 Hub 报告的 capability gaps 指引探索方向，在"需要什么"和"有什么"之间建立桥梁。

### 5.3 惩罚 > 奖励

Anti-pattern 惩罚（0.4）> 成功奖励（0.12），体现进化生物学中**避免致命错误比追求最优更重要**的原则。

---

## 6. BlueCortexCE 借鉴

| Evolver 模式 | CE 翻译方案 | 优先级 |
|-------------|-----------|--------|
| **多因子评分** | `ContextService` 为每种观察类型计算 relevance score，叠加时间衰减、失败惩罚、类型权重 | P0 |
| **连续漂移** | 当近期观察类型分布过于集中时，自动增加"探索性"上下文（非最近类型的历史观察） | P1 |
| **定向漂移** | 当用户长时间未涉及某领域时，适当注入该领域的早期记忆作为提醒 | P2 |
| **Anti-pattern 惩罚 > 奖励** | `fail_penalty` 权重应大于 `time_decay_score` 的正面贡献 | P0（已有） |
| **Failed capsule ban** | 连续失败的提取模板自动降权（不完全禁用，允许恢复） | P1 |

### 6.1 CE 多因子评分伪代码

```java
public double scoreObservation(ObservationEntity obs, SearchContext ctx) {
    double score = 0;

    // 1. 类型匹配（类似 exact match）
    score += typeMatchScore(obs.getType(), ctx.getQueryTypes());

    // 2. 语义相似度（已有 cosine similarity）
    score += semanticScore(obs, ctx) * SEMANTIC_WEIGHT;

    // 3. 时间衰减（已有 time_decay_score）
    score += decayScore(obs.getCreatedAt(), ctx.getHalfLifeDays());

    // 4. 失败惩罚（已有 fail_penalty）
    if ("failed".equals(obs.getOutcome())) {
        score -= FAIL_PENALTY;
    }

    // 5. 探索奖励（新）
    if (isUnderrepresented(obs.getType(), ctx.getRecentTypes())) {
        score += EXPLORATION_BOOST;
    }

    return score;
}
```
