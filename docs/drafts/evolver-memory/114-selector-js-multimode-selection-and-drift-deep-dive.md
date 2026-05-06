# `selector.js` 多模态选择与漂移策略深度分析

**Doc**: `114`  
**源码**: `src/gep/selector.js` (417 lines, pure JS)  
**分析时间**: 2026-05-06 05:59  
**前置阅读**: [`65`](./65-selector-gene-scoring-and-semantic-matching-deep-dive.md)（gene scoring 内部机制）；[`30`](./30-multifactor-gene-selection-continuous-drift.md)（连续漂移概念）

---

## 1. 架构定位

`selector.js` 是 Evolver GEP 的**决策中枢**：接收 `genes` + `capsules` + `signals`，输出 `{ selectedGene, capsuleCandidates, selector, driftIntensity }`。

```
signals ──┐
          ├──→ scoreGene() → scoreGeneLearning() → ranked list
genes ────┤
          │
capsules ─┤
          ├──→ selectCapsule() → trigger pattern match
          │
memoryAdvice ──→ bannedGeneIds / preferredGeneId → banGenesFromFailedCapsules()
          │
failedCapsules ─────────────────────────────────────────────────────────────→ bannedGeneIds
          │
capabilityGaps ─────────────────────────────────────────────────────────────→ diversity-directed drift
```

---

## 2. 多模态信号匹配（`matchPatternToSignals`）

三种模式，优先级：regex → multi-language alias → substring：

```javascript
// Mode 1: Regex /body/flags
const regexLike = p.length >= 2 && p.startsWith('/') && p.lastIndexOf('/') > 0;
if (regexLike) {
  const lastSlash = p.lastIndexOf('/');
  const body = p.slice(1, lastSlash);
  const flags = p.slice(lastSlash + 1);  // e.g. 'i' for case-insensitive
  const re = new RegExp(body, flags || 'i');
  return sig.some(s => re.test(s));
}

// Mode 2: Multi-language alias "en_term|zh_term|ja_term"
if (p.includes('|') && !p.startsWith('/')) {
  const branches = p.split('|').map(b => b.trim().toLowerCase()).filter(Boolean);
  return branches.some(needle => sig.some(s => s.toLowerCase().includes(needle)));
}

// Mode 3: Substring (default)
const needle = p.toLowerCase();
return sig.some(s => s.toLowerCase().includes(needle));
```

**关键约束**：`MAX_REGEX_PATTERN_LEN = 1024` — 防止正则 DoS。

### CE 借鉴

| 模式 | CE 实现 | 备注 |
|------|---------|------|
| Regex | `SearchService` 支持 `~` 前缀正则查询 | pgvector 无原生正则，需应用层过滤 |
| Multi-language | `ObservationEntity.tags` 支持多语言标签 | `action:optimize` / `area:prompt` 等 |
| Substring | `SearchService` 已有 `ILIKE` fallback | 低置信场景降级 |

---

## 3. 基因评分管线（`scoreGene`）

```javascript
function scoreGene(gene, signals) {
  const patterns = gene.signals_match || [];
  let score = 0;
  for (const pat of patterns) {
    if (matchPatternToSignals(pat, signals)) score += 1;
  }
  const semanticScore = scoreGeneSemantic(gene, signals) * SEMANTIC_WEIGHT;
  return score + (tagScore * 0.6) + semanticScore;
}
```

**权重分配**：

| 成分 | 权重 | 来源 |
|------|------|------|
| Exact pattern match | 1.0× per match | `signals_match` 任意一个匹配 |
| Tag overlap | 0.6× | `scoreTagOverlap(gene, signals)` — `learningSignals.expandSignals` |
| Semantic BoW | 0.4× (SEMANTIC_WEIGHT) | `scoreGeneSemantic` — cosine on tokenized text |

### `scoreGeneSemantic`（Bag-of-Words Cosine）

```javascript
function tokenize(text) {
  return String(text || '').toLowerCase()
    .replace(/[^a-z0-9_\-]+/g, ' ')
    .split(/\s+/)
    .filter(w => w.length >= 2 && !STOP_WORDS.has(w));
}

function cosineSimilarity(tfA, tfB) {
  const keys = new Set(Object.keys(tfA).concat(Object.keys(tfB)));
  let dotProduct = 0, normA = 0, normB = 0;
  keys.forEach(k => {
    const a = tfA[k] || 0, b = tfB[k] || 0;
    dotProduct += a * b; normA += a * a; normB += b * b;
  });
  return normA && normB ? dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)) : 0;
}
```

**特点**：纯 JS 无依赖，无需外部 embedding 服务。适用场景：`signals_match` 正则无法覆盖的语义相似性。

**token 来源**（gene 侧）：`signals_match[]` + `gene.summary` + `gene.id`

**token 来源**（signals 侧）：`signals[]` 全部拼接

### CE 翻译

CE `SearchService` 已有 pgvector 语义搜索（`embeddingService.embed()`）。selector 的 BoW Cosine 可作为**轻量备选**（无 LLM 调用开销），适用于：
- 快速过滤阶段（先 BoW 粗排，再 vector 精排）
- 资源受限环境（无 API key）

**P2 行动项**：评估 SearchService 是否需要 BoW 粗排层。

---

## 4. 基因学习评分（`scoreGeneLearning`）

### 4.1 历史 outcome 加权

```javascript
const history = gene.learning_history.slice(-8);
for (const entry of history) {
  if (entry.outcome === 'success') boost += 0.12;
  else if (entry.mode === 'hard') boost -= 0.22;  // hard failure: strong negative
  else if (entry.mode === 'soft') boost -= 0.08;   // soft failure: mild negative
}
```

| Outcome | Boost |
|---------|-------|
| `success` | +0.12 |
| `hard` failure | −0.22 |
| `soft` failure | −0.08 |

`hard` vs `soft` 由 `solidify.js` 决定（见 doc 82 §Epigenetic Marks）。

### 4.2 表观遗传加成（`getEpigeneticBoostLocal`）

```javascript
function getEpigeneticBoostLocal(gene, envFingerprint) {
  const platform = envFingerprint.platform || '';
  const arch = envFingerprint.arch || '';
  const nodeVersion = envFingerprint.node_version || '';
  const envContext = [platform, arch, nodeVersion].join('/') || 'unknown';
  const mark = gene.epigenetic_marks.find(m => m.context === envContext);
  return mark ? Number(mark.boost) || 0 : 0;
}
```

在**特定运行环境中**表现好的 gene，获得正向加成。`envFingerprint` 来源：`envFingerprint.js`（见 doc 38）。

### 4.3 反模式惩罚

```javascript
if (gene.anti_patterns?.length > 0) {
  const signalTags = new Set(expandSignals(signals, ''));
  const recentAntiPatterns = gene.anti_patterns.slice(-6);
  for (const anti of recentAntiPatterns) {
    const overlap = anti.learning_signals.some(tag => signalTags.has(String(tag)));
    if (overlap) overlapPenalty += anti.mode === 'hard' ? 0.4 : 0.18;
  }
  boost -= overlapPenalty;
}
```

### 4.4 综合 clamp

```javascript
return Math.max(-1.5, Math.min(1.5, boost));  // ±1.5 bound
```

### CE 翻译

| Evolver 机制 | CE 等价 | 备注 |
|-------------|---------|------|
| `learning_history.outcome` | `ObservationEntity.metadata.outcome` | 需在 solidify 时记录 |
| `epigenetic_marks` | `ObservationEntity.metadata.envContext` | Platform/arch 特定加成 |
| `anti_patterns` | `ObservationEntity.metadata.antiPatternTags` | 高频失败模式标记 |

**P2 行动项**：`ObservationEntity` 新增 `learningHistory` JSONB 字段，支持 success/hard/soft outcome 记录。

---

## 5. 连续漂移强度（`computeDriftIntensity`）

核心公式来自**群体遗传学**：

```
driftIntensity = 1 / √Ne   (Ne = effective population size)
```

| Ne (基因池大小) | driftIntensity | 行为 |
|---------------|---------------|------|
| 1 | 1.0 | 完全随机漂移 |
| 4 | 0.5 | 一半选择，一半漂移 |
| 25 | 0.2 | 强选择，弱漂移 |
| 100 | 0.1 | 几乎纯选择 |

```javascript
function computeDriftIntensity(opts) {
  const driftEnabled = !!(opts && opts.driftEnabled);
  const ne = effectivePopulationSize || genePoolSize || null;

  if (driftEnabled) {
    return ne && ne > 1 ? Math.min(1, 1 / Math.sqrt(ne) + 0.3) : 0.7;
  }
  if (ne != null && ne > 0) {
    return Math.min(1, 1 / Math.sqrt(ne));  // population-dependent passive drift
  }
  return 0;  // no drift info → pure selection
}
```

### 漂移决策矩阵（`selectGene`）

```javascript
if (driftIntensity > 0 && Math.random() < driftIntensity) {
  if (capabilityGaps.length > 0) {
    // === DIVERSITY-DIRECTED DRIFT ===
    // Score candidates by gap coverage
    const gapScores = filtered.map((entry, idx) => {
      const patterns = entry.gene.signals_match || [];
      let gapHits = 0;
      for (const gapSignal of capabilityGaps.slice(0, 5)) {
        if (patterns.some(p => matchPatternToSignals(p, [gapSignal]))) gapHits++;
      }
      return { idx, gapHits, baseScore: entry.score };
    });
    // Sort by gap coverage first, then by base score
    gapScores.sort((a, b) => b.gapHits - a.gapHits || b.baseScore - a.baseScore);
    selectedIdx = gapScores[0].idx;
    driftMode = 'diversity_directed';
  } else {
    // === RANDOM DRIFT ===
    const topN = Math.min(filtered.length, Math.max(2, Math.ceil(filtered.length * driftIntensity)));
    selectedIdx = Math.floor(Math.random() * topN);
    driftMode = Math.random() < driftIntensity ? 'random_weighted' : 'random';
  }
}
```

**三种漂移模式**：

| 模式 | 触发条件 | 选择策略 |
|------|---------|---------|
| `selection` | `driftIntensity = 0` 或 `Math.random() ≥ driftIntensity` | 纯得分排序 |
| `diversity_directed` | `driftIntensity > 0` + `capabilityGaps.length > 0` | gap 覆盖率优先 |
| `random_weighted` | `driftIntensity > 0` + 无 gap + novelty 低 | 扩展候选范围随机 |
| `random` | `driftIntensity > 0` + 无 gap | 原始随机漂移 |

**capabilityGaps 来源**：Hub 心跳驱动（`hubClient` → `heartbeatSignalsHandler` → `capabilityGaps`）。

### CE 翻译

CE 无基因池，**漂移概念**可映射为：
- **探索模式**（随机选择低置信 observation）
- **利用模式**（选择高相关 observation）

**P3 行动项**：SearchService 支持 `diversity_weight` 参数，在连续检索结果相似时引入随机性。

---

## 6. 失败胶囊封禁（`banGenesFromFailedCapsules`）

```javascript
const FAILED_CAPSULE_BAN_THRESHOLD = 2;      // 失败次数阈值
const FAILED_CAPSULE_OVERLAP_MIN = 0.6;      // 信号重叠率阈值

function banGenesFromFailedCapsules(failedCapsules, signals, existingBans) {
  const bans = new Set(existingBans);
  const geneFailCounts = {};
  for (const fc of failedCapsules) {
    const overlap = computeSignalOverlap(signals, fc.trigger || []);
    if (overlap < FAILED_CAPSULE_OVERLAP_MIN) continue;
    geneFailCounts[fc.gene] = (geneFailCounts[fc.gene] || 0) + 1;
  }
  for (const [gid, count] of Object.entries(geneFailCounts)) {
    if (count >= FAILED_CAPSULE_BAN_THRESHOLD) bans.add(gid);
  }
  return bans;
}
```

**双重条件**：`count ≥ 2` **且** `overlap ≥ 0.6`（信号重叠度高说明是同一个问题域的失败）。

### CE 翻译

| Evolver | CE 方案 |
|---------|---------|
| `failedCapsules` | `ObservationEntity.metadata.failedSkillIds` |
| 信号重叠 | `Observation.tags` 与 `failedSkill.tags` Jaccard |
| Ban 决策 | 同一 `signal_tag` 失败 ≥ 2 次 → 降低该类 observation 排序权重 |

**P2 行动项**：SearchService 增加 `excludeSignalPatterns` 参数，排除高频失败信号域。

---

## 7. 完整选择管线（`selectGeneAndCapsule`）

```javascript
function selectGeneAndCapsule({ genes, capsules, signals, memoryAdvice, driftEnabled, failedCapsules, capabilityGaps, noveltyScore }) {
  // Step 1: Compute effective bans (memory advice + failed capsules)
  const effectiveBans = banGenesFromFailedCapsules(failedCapsules, signals, bannedGeneIds);

  // Step 2: Select gene with drift
  const { selected, alternatives, driftIntensity } = selectGene(genes, signals, {
    bannedGeneIds: effectiveBans,
    preferredGeneId,
    driftEnabled: !!driftEnabled,
    capabilityGaps,
    noveltyScore,
  });

  // Step 3: Select capsule (independent signal match)
  const capsule = selectCapsule(capsules, signals);

  // Step 4: Build decision record
  const selector = buildSelectorDecision({ gene: selected, capsule, signals, alternatives, memoryAdvice, driftEnabled, driftIntensity });

  return { selectedGene: selected, capsuleCandidates: capsule ? [capsule] : [], selector, driftIntensity };
}
```

### `buildSelectorDecision` — 可观测性

```javascript
function buildSelectorDecision({ gene, capsule, signals, alternatives, memoryAdvice, driftEnabled, driftIntensity }) {
  const reason = [];
  if (gene) reason.push('signals match gene.signals_match');
  if (capsule) reason.push('capsule trigger matches signals');
  if (!gene) reason.push('no matching gene found; new gene may be required');
  if (signals?.length) reason.push(`signals: ${signals.join(', ')}`);
  if (memoryAdvice?.explanation?.length) reason.push(`memory_graph: ${memoryAdvice.explanation.join(' | ')}`);
  if (driftEnabled) reason.push('random_drift_override: true');
  if (driftIntensity > 0) reason.push(`drift_intensity: ${driftIntensity.toFixed(3)}`);

  return {
    selected: gene?.id || null,
    reason,
    alternatives: alternatives?.map(g => g.id) || [],
  };
}
```

**设计亮点**：`selector` 对象是纯**可观测性**产物——包含 `reason` 数组用于事后复盘和调试，不参与决策逻辑。

---

## 8. 与 doc 65 的差异化覆盖

| 维度 | doc 65 覆盖 | 本文（doc 114）新增 |
|------|------------|-------------------|
| BoW Cosine 实现 | 简述 | 完整 `tokenize`/`cosineSimilarity` 源码 |
| `matchPatternToSignals` 三模式 | 未覆盖 | 完整 regex/alias/substring 三模式分析 |
| `scoreGeneLearning` 细节 | 未覆盖 | history/outcome/epigenetic/anti-pattern 四层 |
| `computeDriftIntensity` 公式 | 概念描述 | 完整 `1/√Ne` 推导与阈值表 |
| `diversity_directed` 漂移 | 提及 | 完整 gap scoring 算法 |
| `banGenesFromFailedCapsules` | 未覆盖 | 完整双重条件封禁逻辑 |
| `buildSelectorDecision` | 未覆盖 | 完整 reason 构建逻辑 |
| CE 翻译行动项 | 部分覆盖 | 完整 P2/P3 提案 |

---

## 9. BlueCortexCE 行动项汇总

| 优先级 | 行动项 | 对应机制 |
|--------|--------|---------|
| **P2** | `ObservationEntity` 新增 `learningHistory` JSONB 字段 | `scoreGeneLearning` history |
| **P2** | SearchService 增加 `excludeSignalPatterns` 参数 | `banGenesFromFailedCapsules` |
| **P2** | 评估 SearchService BoW 粗排层（备选语义匹配） | `scoreGeneSemantic` |
| **P3** | SearchService 支持 `diversity_weight` 探索参数 | `computeDriftIntensity` |

---

## 10. 关键设计原则提炼

1. **纯 JS 无依赖**：BoW Cosine / tokenize / pattern match 全部手写，无 external embedding 依赖
2. **渐进式精确度**：regex > alias > substring，精确到模糊降级
3. **连续值漂移**：`0~1` 而非二元开关，公式来自群体遗传学 `1/√Ne`
4. **有信息引导的探索**：capability gaps 使漂移不是纯随机，而是"有方向的探索"
5. **可解释决策**：`buildSelectorDecision` 输出 human-readable `reason[]`，零额外成本可观测性
6. **双重门禁封禁**：失败次数 + 信号重叠率，防止误ban

---

*EOF*
