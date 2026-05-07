# 4. 基因选择机制

## 4.1 评分函数（Gene Scoring）

`selector.js` 的 `scoreGene()` 是基因评分的核心：

```js
function scoreGene(gene, signals) {
  // 1. 模式匹配得分
  let score = 0;
  for (const pat of gene.signals_match) {
    if (matchPatternToSignals(pat, signals)) score += 1;
  }

  // 2. 标签重叠得分（权重 0.6）
  const tagScore = scoreTagOverlap(gene, signals) * 0.6;

  // 3. 语义相似度得分（权重 SEMANTIC_WEIGHT=0.4）
  const semanticScore = scoreGeneSemantic(gene, signals) * SEMANTIC_WEIGHT;

  return score + tagScore + semanticScore;
}
```

### 模式匹配（Pattern Matching）

```js
function matchPatternToSignals(pattern, signals) {
  // 1. 正则表达式：/body/flags
  if (p.startsWith('/') && !p.startsWith('//')) {
    const re = new RegExp(body, flags || 'i');
    return sig.some(s => re.test(s));
  }
  // 2. 多语言别名："en_term|zh_term|ja_term"
  if (p.includes('|') && !p.startsWith('/')) {
    return branches.some(needle => sig.some(s => s.includes(needle)));
  }
  // 3. 子串包含（默认）
  return sig.some(s => s.toLowerCase().includes(p.toLowerCase()));
}
```

### 语义相似度（Bag-of-Words Cosine）

```js
// 不依赖外部 embedding provider，用 TF-IDF 余弦相似度
function cosineSimilarity(tfA, tfB) {
  // dotProduct / (normA * normB)
}

function scoreGeneSemantic(gene, signals) {
  // 信号文本 → tokenize → TF
  // 基因 signals_match + summary + id → tokenize → TF
  // cosine similarity
}
```

**语义权重通过 `SEMANTIC_MATCH_WEIGHT` 环境变量配置（默认 0.4）。**

## 4.2 表观遗传 Boost（Epigenetic Marks）

基因可携带环境特异性标记，在特定环境下获得 boost：

```js
function getEpigeneticBoostLocal(gene, envFingerprint) {
  // 检查基因的 epigenetic_marks 是否匹配当前环境
  // platform, arch, node_version 三元组
  // 返回 boost 分数（如 0.3）
}
```

这使得基因可以在特定操作系统/架构下自动获得优先级。

## 4.3 MemoryGraph 记忆建议

来自 `memoryGraph.getMemoryAdvice()` 的反馈：

| 建议 | 来源 | 效果 |
|------|------|------|
| `preferredGeneId` | 信号边 × Jaccard 相似度 | 推荐优先使用 |
| `bannedGeneIds` | 低效抑制规则 | 明确禁止（可被 drift 覆盖） |
| `gene_prior` | Gene→Outcome 全局先验 | 作为 stabilizer 提供背景知识 |

### 抑制规则详解

```js
// 规则 1：已知低效路径
if (attempts >= 2 && best < 0.18) bannedGeneIds.add(geneId);

// 规则 2：全局结果差且信号边稀疏
if (attempts < 2 && prior_attempts >= 3 && prior < 0.12) {
  bannedGeneIds.add(geneId);
}
```

## 4.4 变异类别决策

`mutation.js` 的 `mutationCategoryFromContext()` 决定进化方向：

```js
function mutationCategoryFromContext({ signals, driftEnabled }) {
  if (hasErrorishSignal(signals)) return 'repair';      // 优先修复
  if (driftEnabled) return 'innovate';                    // 漂移模式
  if (hasOpportunitySignal(signals)) return 'innovate';   // 机会信号
  // 检查 strategy preset 是否偏向创新
  if (strategy.innovate >= 0.5) return 'innovate';
  return 'optimize';
}
```

三种变异类别：

| 类别 | 目标 | 基因策略 |
|------|------|----------|
| `repair` | 减少运行时错误，提高稳定性 | 修复型基因 |
| `optimize` | 提高成功率，降低重复运营成本 | 优化型基因 |
| `innovate` | 探索新策略组合，逃离局部最优 | 创新型基因 |

## 4.5 完整选择流程

```
signals[]
  │
  ├─→ extractSignals() ──────────────────────────┐
  │                                            │
  ├─→ MemoryGraph.getMemoryAdvice() ───────────→ Selector 聚合
  │    (preferredGeneId, bannedGeneIds)          │
  │                                            ▼
  │                                    scoreGene(gene, signals)
  │                                      ├─ pattern match score
  │                                      ├─ tag overlap × 0.6
  │                                      ├─ semantic cosine × 0.4
  │                                      └─ epigenetic boost
  │
  └─→ mutationCategoryFromContext() ────→ 变异类别 + expected_effect
```
