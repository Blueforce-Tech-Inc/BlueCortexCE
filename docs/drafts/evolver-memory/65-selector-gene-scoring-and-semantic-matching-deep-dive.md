# 65. Selector 基因评分与语义匹配深度分析

**来源**：`EvoMap/evolver/src/gep/selector.js`（419行）；参照 `learningSignals.js`、`envFingerprint.js`。  
**定位**：补充 [`30`](./30-multifactor-gene-selection-continuous-drift.md) — 专注 **评分机制内部实现**，而非选择流程。  
**最后更新**：2026-04-26。

---

## 1. 评分体系总览

`selector.js` 的基因选择分两层：

```
scored = genes.map(gene => scoreGene(gene, signals) + scoreGeneLearning(gene, signals, envFingerprint))
           .filter(s => s > 0)
           .sort((a,b) => b.score - a.score)
```

| 评分函数 | 贡献 | 环境依赖 |
|----------|------|----------|
| `scoreGene` | exact match + tag overlap + semantic | 无 |
| `scoreGeneLearning` | epigenetic boost + history + anti-pattern penalty | envFingerprint |
| `getEpigeneticBoostLocal` | 平台/架构/Node版本匹配加成 | envFingerprint |

---

## 2. `scoreGene` — 三通道叠加

```javascript
function scoreGene(gene, signals) {
  // Channel 1: exact pattern match (1 point per matched pattern)
  let score = 0;
  for (const pat of patterns) {
    if (matchPatternToSignals(pat, signals)) score += 1;
  }

  // Channel 2: tag overlap (×0.6)
  score += scoreTagOverlap(gene, signals) * 0.6;

  // Channel 3: semantic cosine similarity (×SEMANTIC_WEIGHT=0.4)
  score += scoreGeneSemantic(gene, signals) * SEMANTIC_WEIGHT;

  return score;
}
```

**叠加公式**：`exact_count + tagScore×0.6 + semanticScore×0.4`

### 2.1 Pattern 匹配引擎（`matchPatternToSignals`）

三种匹配策略，按优先级：

| 策略 | 条件 | 示例 |
|------|------|------|
| **Regex** | `pat` 首尾为 `/` | `/error\|fail/i` → 大小写不敏感 |
| **多语言别名** | `pat` 包含 `\|` 且不以 `/` 开头 | `error\|错误\|エラー` |
| **子串包含** | 以上均不满足 | `capability_gap` |

```javascript
// Multi-language alias: "en_term|zh_term|ja_term"
if (p.includes('|') && !p.startsWith('/')) {
  const branches = p.split('|').map(b => b.trim().toLowerCase()).filter(Boolean);
  return branches.some(needle => sig.some(s => s.toLowerCase().includes(needle)));
}
```

**安全限制**：`MAX_REGEX_PATTERN_LEN = 1024`，超长 regex 回退为子串匹配（不抛错）。

### 2.2 Tag 重叠评分（`scoreTagOverlap`）

来自 `learningSignals.js`：`expandSignals` 扩展后计算 Jaccard 重叠。详见 [`21`](./21-signal-taxonomy-and-gene-selection-memory.md)。

### 2.3 语义评分（`scoreGeneSemantic`）— **Bag-of-Words Cosine**

```javascript
const SEMANTIC_WEIGHT = 0.4; // 环境变量 SEMANTIC_MATCH_WEIGHT 可覆盖
const STOP_WORDS = new Set(['the','and','for','with','from','that','this','into','when',...]);

function tokenize(text) {
  return String(text||'').toLowerCase()
    .replace(/[^a-z0-9_\-]+/g, ' ')
    .split(/\s+/)
    .filter(w => w.length >= 2 && !STOP_WORDS.has(w));
}

function scoreGeneSemantic(gene, signals) {
  // 从 signals 收集 token
  var signalTokens = [];
  signals.forEach(s => { signalTokens = signalTokens.concat(tokenize(s)); });

  // 从 gene 收集 token（signals_match + summary + id）
  var geneTokens = [];
  gene.signals_match?.forEach(s => { geneTokens = geneTokens.concat(tokenize(s)); });
  if (gene.summary) geneTokens = geneTokens.concat(tokenize(gene.summary));
  if (gene.id) geneTokens = geneTokens.concat(tokenize(gene.id));

  // TF（词频）→ Cosine
  return cosineSimilarity(buildTermFrequency(signalTokens), buildTermFrequency(geneTokens));
}
```

**关键设计**：
- **非 embedding**：纯词袋 + TF + Cosine，不依赖外部模型
- `signals_match` 中的每个 pattern 都被 tokenize 加入基因词表
- `SEMANTIC_WEIGHT=0.4` 通过环境变量可调（平衡 exact 和 semantic 的权重）
- 当 `EMBEDDING_PROVIDER` 配置后可用真实 embedding 替换（模块注释注明）

---

## 3. `scoreGeneLearning` — 学习增强层

```javascript
function scoreGeneLearning(gene, signals, envFingerprint) {
  let boost = 0;

  // 3.1 历史成功/失败
  const history = gene.learning_history.slice(-8);
  for (const entry of history) {
    if (entry.outcome === 'success') boost += 0.12;
    else if (entry.mode === 'hard') boost -= 0.22;
    else if (entry.mode === 'soft') boost -= 0.08;
  }

  // 3.2 表观遗传加成
  boost += getEpigeneticBoostLocal(gene, envFingerprint);

  // 3.3 反模式惩罚
  const signalTags = new Set(expandSignals(signals, ''));
  const recentAntiPatterns = gene.anti_patterns.slice(-6);
  for (const anti of recentAntiPatterns) {
    const overlap = anti.learning_signals.some(tag => signalTags.has(String(tag)));
    if (overlap) {
      boost -= anti.mode === 'hard' ? 0.4 : 0.18;
    }
  }

  return Math.max(-1.5, Math.min(1.5, boost));
}
```

### 3.1 表观遗传加成（`getEpigeneticBoostLocal`）

```javascript
function getEpigeneticBoostLocal(gene, envFingerprint) {
  const platform = envFingerprint?.platform || '';
  const arch = envFingerprint?.arch || '';
  const nodeVersion = envFingerprint?.node_version || '';
  const envContext = [platform, arch, nodeVersion].join('/') || 'unknown';

  const mark = gene.epigenetic_marks.find(m => m && m.context === envContext);
  return mark ? Number(mark.boost) || 0 : 0;
}
```

**设计思想**：`epigenetic_marks` = 环境特定的加成标签（如 `{context: "darwin/arm64/v22", boost: 0.3}`）。同一基因在不同平台有不同表现，marks 记录历史最优环境加成。

### 3.2 反模式惩罚机制

```javascript
// anti_pattern 结构（来自 gene 定义）
{
  learning_signals: ['repair_loop_detected', 'evolution_stagnation'],
  mode: 'hard',    // 'hard' | 'soft'
}
```

- `mode === 'hard'`：惩罚 0.4（该信号下此基因**禁止**使用）
- `mode !== 'hard'`：惩罚 0.18（该信号下此基因**降低优先级**）

---

## 4. 漂移强度公式：`1/√Ne`

```javascript
// Population-size-dependent drift intensity.
// Formula from population genetics: genetic drift is stronger in small populations.
// driftIntensity: 0 = pure selection, 1 = pure drift (random).
function computeDriftIntensity(opts) {
  const driftEnabled = !!(opts && opts.driftEnabled);
  const ne = effectivePopulationSize || genePoolSize || null;

  if (driftEnabled) {
    // 显式漂移：中等~高强度
    return ne && ne > 1 ? Math.min(1, 1 / Math.sqrt(ne) + 0.3) : 0.7;
  }

  if (ne != null && ne > 0) {
    // 自然漂移：种群越小，漂移越强
    // Ne=1:  intensity=1.0 (pure drift)
    // Ne=25: intensity=0.2
    // Ne=100: intensity=0.1
    return Math.min(1, 1 / Math.sqrt(ne));
  }

  return 0; // 无漂移信息，纯粹选择
}
```

**漂移模式（`driftMode`）**：

| driftMode | 条件 | 行为 |
|-----------|------|------|
| `memory_preferred` | `preferredGeneId` 命中且是候选 | 优先选择记忆推荐基因 |
| `diversity_directed` | `driftIntensity > 0.15` + `capabilityGaps` 非空 | 从覆盖 gap 的基因中随机 |
| `random` | `driftIntensity > 0.15` + `Math.random() < driftIntensity` | 完全随机 |
| `selection` | 以上均不满足 | 纯粹得分排序 |
| `none` | 无候选 | 返回 null |

---

## 5. 选择流程

```javascript
function selectGene(genes, signals, opts) {
  // 1. 计算所有基因得分
  const scored = genes
    .map(g => ({
      gene: g,
      score: scoreGene(g, signals) + scoreGeneLearning(g, signals, envFingerprint)
    }))
    .filter(x => x.score > 0)
    .sort((a, b) => b.score - a.score);

  // 2. Memory 推荐覆盖（memory_preferred）
  if (preferredGeneId) {
    const preferred = scored.find(x => x.gene.id === preferredGeneId);
    if (preferred && (useDrift || !bannedGeneIds.has(preferredGeneId))) {
      return { selected: preferred.gene, driftMode: 'memory_preferred', ... };
    }
  }

  // 3. 多样性导向漂移（capability gaps → 覆盖度排序）
  if (driftIntensity > 0 && capabilityGaps.length > 0) {
    // 从覆盖 gap 的基因中按 driftIntensity 概率随机
    const gapCovering = scored.filter(x => genesCoverCapabilityGap(x.gene, capabilityGaps));
    // 从 gapCovering 中选择
  }

  // 4. 随机漂移（无 gap 信息）
  if (driftIntensity > 0 && Math.random() < driftIntensity) {
    // 从 filtered 中随机
  }

  // 5. 纯选择（得分最高）
  return { selected: filtered[0].gene, driftMode: 'selection', ... };
}
```

---

## 6. CE 借鉴路径

| 机制 | CE 借鉴 |
|------|---------|
| **Bag-of-Words Cosine** | `SearchService` 的 semantic fallback：当 embedding 不存在时，用 tokenize + cosine 作为后备（非依赖外部模型） |
| **多语言别名** | 信号 pattern 支持多语言：`error\|错误` 同时匹配英文和中文信号 |
| **表观遗传加成** | `ObservationEntity` 中存储 `environment_context` 字段，对特定环境（macOS/Docker）的高频观察加权 |
| **Anti-pattern 惩罚** | `SearchService` 对已知的"不好用"观察类型施加负权重 |
| **`1/√Ne` 漂移公式** | `SearchService` 候选少时增加探索性（类似冷启动的随机性） |
| **多模式 drift** | `ContextService` 注入策略可选：`memory_preferred`（记忆优先）vs `exploration`（探索）|

**P0**：`SEMANTIC_WEIGHT` 环境变量可调机制 → CE `SearchService` 支持配置 `semantic_weight` 参数。  
**P1**：反模式惩罚 → CE `SearchService` 对已知低效观察类型降权。  
**P2**：表观遗传加成（需长期数据积累）。
