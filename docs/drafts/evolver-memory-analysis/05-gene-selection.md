# 05 — 基因选择器

## 5.1 选择流程总览

```
signals[] + genes[] + memoryAdvice
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  selectGeneAndCapsule()                             │
│                                                     │
│  1. banGenesFromFailedCapsules()                   │
│     → 结合 memoryAdvice.bannedGeneIds               │
│                                                     │
│  2. selectGene()                                    │
│     → scoreGene() = exact + semantic + tag          │
│     → scoreGeneLearning() = history + anti-pattern  │
│     → driftIntensity 计算                           │
│     → diversity-directed drift                      │
│                                                     │
│  3. selectCapsule()                                │
│     → trigger 模式匹配                              │
│                                                     │
│  4. buildSelectorDecision()                        │
│     → reason[]（供 prompt 使用）                    │
└─────────────────────────────────────────────────────┘
         │
         ▼
{ selectedGene, capsuleCandidates, selector, driftIntensity }
```

## 5.2 模式匹配（Exact + Regex + Alias）

```javascript
function matchPatternToSignals(pattern, signals) {
  // 1. Regex 模式：/body/flags
  if (p.length >= 2 && p.startsWith('/') && p.lastIndexOf('/') > 0) {
    const re = new RegExp(body, flags || 'i');
    return sig.some(s => re.test(s));
  }

  // 2. 多语言别名：en_term|zh_term|ja_term
  if (p.includes('|') && !p.startsWith('/')) {
    const branches = p.split('|').map(b => b.trim().toLowerCase());
    return branches.some(needle => sig.some(s => s.includes(needle)));
  }

  // 3. 普通子串匹配
  const needle = p.toLowerCase();
  return sig.some(s => s.includes(needle));
}
```

**基因的 signals_match 示例**：
```json
{
  "signals_match": [
    "log_error",
    "recurring_error",
    "/TypeError|ReferenceError/i",
    "user_feature_request|功能请求"
  ]
}
```

## 5.3 语义相似度（Bag-of-Words Cosine）

```javascript
const SEMANTIC_WEIGHT = 0.4;  // 可通过 SEMANTIC_MATCH_WEIGHT 配置

function scoreGeneSemantic(gene, signals) {
  // 1. 信号 tokenize（去停用词）
  const signalTokens = signals.flatMap(tokenize);  // tokenize = 分词 + 去停用词
  const tfSignals = buildTermFrequency(signalTokens);

  // 2. 基因 tokenize（signals_match + summary + id）
  const geneTokens = [
    ...gene.signals_match.flatMap(tokenize),
    ...tokenize(gene.summary || ''),
    ...tokenize(gene.id || '')
  ];
  const tfGene = buildTermFrequency(geneTokens);

  // 3. Cosine 相似度
  return cosineSimilarity(tfSignals, tfGene);
}

function cosineSimilarity(tfA, tfB) {
  const keys = union(keysA, keysB);
  let dotProduct = 0, normA = 0, normB = 0;
  for (const k of keys) {
    dotProduct += (tfA[k] || 0) * (tfB[k] || 0);
    normA += (tfA[k] || 0) ** 2;
    normB += (tfB[k] || 0) ** 2;
  }
  return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
}
```

**停用词表**：the/and/for/with/from/that/this/into/when/are/was/...

## 5.4 标签重叠评分

```javascript
// expandSignals: 信号 → 结构化标签
function expandSignals(signals, extraText) {
  const tags = [];
  // 基础：每个信号 + 冒号前的基础名
  for (const s of signals) {
    tags.push(s);
    const base = s.split(':')[0];
    if (base && base !== s) tags.push(base);
  }

  const text = signals.join(' ') + extraText;

  // 问题类型标签
  if (/error|exception|failed/.test(text)) { add(tags, 'problem:reliability'); add(tags, 'action:repair'); }
  if (/protocol|prompt|drift/.test(text)) { add(tags, 'problem:protocol'); add(tags, 'action:optimize'); }
  if (/perf|bottleneck|latency/.test(text)) { add(tags, 'problem:performance'); add(tags, 'action:optimize'); }
  if (/feature|capability_gap/.test(text)) { add(tags, 'problem:capability'); add(tags, 'action:innovate'); }
  if (/stagnation|plateau|empty_cycle/.test(text)) { add(tags, 'problem:stagnation'); add(tags, 'action:innovate'); }

  // 区域标签
  if (/task|worker|heartbeat|orchestration/.test(text)) add(tags, 'area:orchestration');
  if (/memory|narrative|reflection/.test(text)) add(tags, 'area:memory');
  if (/skill|dashboard/.test(text)) add(tags, 'area:skills');
  if (/validation|canary|rollback/.test(text)) add(tags, 'risk:validation');

  return unique(tags);
}

// geneTags: 从基因提取标签
function geneTags(gene) {
  const inputs = [];
  if (gene.category) inputs.push('action:' + gene.category.toLowerCase());
  if (Array.isArray(gene.signals_match)) inputs = inputs.concat(gene.signals_match);
  if (gene.id) inputs.push(gene.id);
  if (gene.summary) inputs.push(gene.summary);
  return expandSignals(inputs, '');
}

// 得分 = 基因标签中与信号标签集合重叠的数量
function scoreTagOverlap(gene, signals) {
  const signalTags = expandSignals(signals, '');
  const geneTagList = geneTags(gene);
  return geneTagList.filter(t => signalTags.has(t)).length;
}
```

## 5.5 学习历史（Learning History）

```javascript
function scoreGeneLearning(gene, signals, envFingerprint) {
  let boost = 0;

  // 1. 基因自己的学习历史（最近 8 条）
  const history = gene.learning_history?.slice(-8) || [];
  for (const entry of history) {
    if (entry.outcome === 'success') boost += 0.12;
    else if (entry.mode === 'hard') boost -= 0.22;  // 硬失败惩罚
    else if (entry.mode === 'soft') boost -= 0.08;  // 软失败惩罚
  }

  // 2. 表观遗传标记（平台特定增强）
  boost += getEpigeneticBoostLocal(gene, envFingerprint);

  // 3. 抗性模式惩罚
  if (gene.anti_patterns?.length) {
    const signalTags = new Set(expandSignals(signals, ''));
    const recentAnti = gene.anti_patterns.slice(-6);
    for (const anti of recentAnti) {
      if (anti.learning_signals.some(t => signalTags.has(t))) {
        boost -= anti.mode === 'hard' ? 0.4 : 0.18;
      }
    }
  }

  return Math.max(-1.5, Math.min(1.5, boost));  // clamp
}
```

## 5.6 漂移强度（Drift Intensity）

```javascript
// 遗传学启发：有效种群越小 → 遗传漂变越强
// intensity = 1 / sqrt(Ne) where Ne = effective population size

function computeDriftIntensity(opts) {
  const { driftEnabled, effectivePopulationSize, genePoolSize } = opts;
  const ne = effectivePopulationSize || genePoolSize || null;

  if (driftEnabled) {
    // 显式 drift：中等至高强度
    return ne && ne > 1 ? Math.min(1, 1 / Math.sqrt(ne) + 0.3) : 0.7;
  }

  if (ne != null && ne > 0) {
    // 隐式 drift：依赖种群大小
    // Ne=1: 1.0（纯随机）
    // Ne=25: 0.2
    // Ne=100: 0.1
    return Math.min(1, 1 / Math.sqrt(ne));
  }

  return 0;  // 无 drift 信息，纯选择
}
```

**多样性导向漂移**（Diversity-Directed Drift）：
```javascript
if (driftIntensity > 0 && Math.random() < driftIntensity) {
  if (capabilityGaps.length > 0) {
    // 优先选择能覆盖能力缺口的基因
    const gapScores = filtered.map((entry, idx) => {
      let gapHits = 0;
      for (const gap of capabilityGaps.slice(0, 5)) {
        if (patterns.some(p => matchPatternToSignals(p, [gap]))) gapHits++;
      }
      return { idx, gapHits, baseScore: entry.score };
    });
    // 按 gap 覆盖率排序，再按基础分排序
    gapScores.sort((a, b) => b.gapHits - a.gapHits || b.baseScore - a.baseScore);
    selectedIdx = gapScores[0].idx;
    driftMode = 'diversity_directed';
  } else {
    // 无 gap 数据：纯随机漂移
    selectedIdx = Math.floor(Math.random() * topN);
    driftMode = 'random';
  }
}
```

## 5.7 最终选择

```javascript
// 得分 = scoreGene(...) + scoreGeneLearning(...)
scored.sort((a, b) => b.score - a.score);

// Memory 偏好覆盖（精确 key 优先）
if (preferredGeneId) {
  const preferred = scored.find(x => x.gene.id === preferredGeneId);
  if (preferred && (useDrift || !bannedGeneIds.has(preferredGeneId))) {
    return { selected: preferred.gene, driftMode: 'memory_preferred' };
  }
}

// 低效抑制
const filtered = useDrift ? scored : scored.filter(x => !bannedGeneIds.has(x.gene.id));
if (filtered.length === 0) return { selected: null, alternatives: scored.slice(0, 4) };

// Drift 模式：diversity_directed / random_weighted / random
return { selected: filtered[selectedIdx].gene, driftMode, driftIntensity };
```

---

_Next: [06-saturation.md](./06-saturation.md) — 饱和检测与降级策略_
