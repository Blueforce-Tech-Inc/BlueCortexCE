# EvoMap Signal Taxonomy 与 Gene Selection 端到端链路

> **角色**：梳理从原始日志文本到 Gene/Capsule 选中的完整数据流，聚焦 Signal Taxonomy 的分类体系、标签扩展机制、以及 Gene 选择器的四因子评分。
> **数据来源**：`src/gep/signals.js`、`src/gep/learningSignals.js`、`src/gep/selector.js`、`src/gep/memoryGraph.js`、`src/gep/mutation.js`。
> **定位**：对 `21`（Signal Taxonomy）、`24`（Gene/Strategy）、`29`（Signal 提取）、`30`（多因子选择）的综合提炼，补充被各模块文档拆散的关键链路。

---

## 1. Signal 的生命周期

```
原始文本 (session transcript / log)
    │
    ▼
┌──────────────────────────────────────┐
│  extractSignals()  (signals.js)       │
│  - 错误检测（正则 / JSON markers）      │
│  - Opportunity 检测（4语言）             │
│  - 工具使用分析（频率、绕行）            │
│  - 历史去重（analyzeRecentHistory）     │
│  - 饱和降级（空转 / 失败连击）           │
└──────────────┬───────────────────────┘
               ▼
         [signal₁, signal₂, ...]
               │
               ├──────────────────┬──────────────────┐
               ▼                  ▼                  ▼
        getMemoryAdvice()   buildMutation()    expandSignals()
        (memoryGraph.js)    (mutation.js)      (learningSignals.js)
               │                  │                  │
               ▼                  ▼                  ▼
        preferredGeneId     mutation.category    [tag₁, tag₂, ...]
        bannedGeneIds        risk_level          (problem: / action: / area:)
```

---

## 2. Signal Taxonomy（信号分类体系）

### 2.1 三大类别

| 类别 | 触发关键词（部分） | 对应 Action |
|------|------------------|-------------|
| **problem:reliability** | error, exception, failed, unstable, runtime, 429 | `action:repair` |
| **problem:protocol** | protocol, prompt, audit, gep, schema, drift | `action:optimize` + `area:prompt` |
| **problem:performance** | perf, bottleneck, latency, slow, throughput | `action:optimize` |
| **problem:capability** | feature, capability_gap, user_feature_request, external_opportunity | `action:innovate` |
| **problem:stagnation** | stagnation, plateau, steady_state, saturation, empty_cycle_loop | `action:innovate` |

### 2.2 Area 标签（覆盖模块）

| Area 标签 | 含义 |
|-----------|------|
| `area:orchestration` | Task/Worker/Heartbeat/Hub/Commitments |
| `area:memory` | Memory/Narrative/Reflection |
| `area:skills` | Skill/Dashboard |
| `risk:validation` | Validation/Canary/Rollback/Constraint/Blast radius |

### 2.3 标签扩展函数 `expandSignals(signals, extraText)`

输入：原始 signal 列表 + 补充文本  
输出：扩展后的 tag 集合（含 base 截断、多维度映射）

```javascript
// 核心逻辑
raw.forEach(s => { add(tags, s); add(tags, s.split(':')[0]); });
// 例如 "errsig:TypeError" → ["errsig:TypeError", "errsig"]

// 基于 extraText 内容关键词追加标签
if (text.match(/(error|exception|...)/)) add(tags, 'problem:reliability'), add(tags, 'action:repair');
if (text.match(/(protocol|prompt|...)/)) add(tags, 'problem:protocol'), add(tags, 'action:optimize'), add(tags, 'area:prompt');
// ...
```

### 2.4 规范化错误签名 `normalizeErrorSignature(text)`

将每次运行都不同的错误文本归一化为可跨运行匹配的稳定 key：

```javascript
.replace(/[a-z]:\\[^ \n\r\t]+/gi, '<path>')   // Windows 路径
.replace(/\/[^ \n\r\t]+/g, '<path>')            // Unix 路径
.replace(/\b0x[0-9a-f]+\b/gi, '<hex>')          // 十六进制数
.replace(/\b\d+\b/g, '<n>')                     // 所有数字
.replace(/\s+/g, ' ')
```

结果 → `stableHash()` → 短字符串用于 `computeSignalKey()` 中的 Jaccard 匹配。

### 2.5 `computeSignalKey(signals)` 稳定键

```javascript
function computeSignalKey(signals) {
  const list = normalizeSignalsForMatching(signals); // errsig → errsig_norm:hash
  const uniq = [...new Set(list.filter(Boolean))].sort();
  return uniq.join('|') || '(none)';
}
```

等价 signal key → Jaccard 相似度计算 → MemoryGraph outcome 关联。

---

## 3. 基因选择器：四因子叠加评分

`selectGene()` 输入：genes pool + signals + options  
输出：selected gene + alternatives + driftMode

### 因子 1：Exact + Semantic 匹配

```javascript
score = exactMatchCount                         // 精确匹配 signals_match 模式
      + scoreTagOverlap(gene, signals) * 0.6    // 标签重叠 × 0.6
      + scoreGeneSemantic(gene, signals) * 0.4 // 余弦相似度 × 0.4
```

- `scoreTagOverlap()`：将 signals 扩展为 tags，再与 gene 的 tags 计算交集
- `scoreGeneSemantic()`：bag-of-words 余弦相似度（tokenize + TF + cosine）

### 因子 2：Learning 历史（Epigenetic + Anti-pattern）

```javascript
boost = Σ(success: +0.12, hard_fail: -0.22, soft_fail: -0.08)
      + epigenetic_mark_boost
      - anti_pattern_overlap_penalty(hard: 0.4, soft: 0.18)
```

- `epigenetic_marks`：平台/架构/Node 版本特定的成功加成
- `anti_patterns`：失败模式标签，若当前 signals 扩展后命中则扣分

### 因子 3：Memory Graph Advice（历史 outcome）

在 `evolve.js` 的主循环中，`getMemoryAdvice()` 从 JSONL 读取最近 2000 条事件：

```javascript
// signal_key → gene_id outcome edge
// Laplace 平滑：p = (succ + 1) / (total + 2)
// 半衰衰减：w = 0.5^(age_days / half_life_days)
// combined = edge_value + prior * 0.12
```

preferredGeneId 来自该评分，bannedGeneIds 来自低效路径抑制（≥2 次尝试且 score < 0.18）。

### 因子 4：Population Drift（连续漂移强度）

```javascript
driftIntensity = driftEnabled
  ? min(1, 1/sqrt(Ne) + 0.3)     // 显式漂移
  : min(1, 1/sqrt(Ne))             // 隐式漂移（种群小 → 漂移强）
```

- `Ne`：有效种群规模（= 基因池大小）
- `driftEnabled=true`：强制启用探索模式
- `diversity_directed drift`：当 Hub 提供 capability_gaps 时，按 gap 覆盖度选择而非纯随机

### 叠加流程

```
因子1(exact+semantic) ─┐
因子2(learning)      ─┼→ score = Σ → 排序 → filtered(ban 过滤)
因子3(memory advice) ─┤        │
因子4(drift)         ─┘        ▼
               driftIntensity > 0.15
               ? diversity_directed / random
               : pure selection
```

---

## 4. Capsule 选择与 Ban 机制

### 4.1 Capsule 匹配

```javascript
capsule.score = trigger.reduce((acc, t) =>
  matchPatternToSignals(t, signals) ? acc + 1 : acc, 0);
// trigger 模式命中越多，优先级越高
```

### 4.2 Failed Capsule Ban

```javascript
// 如果某个 capsule 在相同 signal overlap(≥0.6) 下失败 ≥2 次
// → ban 该 capsule 引用的 gene
banGenesFromFailedCapsules(failedCapsules, signals, existingBans)
```

防止重复选择已知失败的路径。

---

## 5. Mutation Category 决策链

`buildMutation()` 调用 `mutationCategoryFromContext()`：

```
hasErrorSignal()          → 'repair'
driftEnabled              → 'innovate'
hasOpportunitySignal()    → 'innovate'
strategy.innovate ≥ 0.5   → 'innovate'
otherwise                  → 'optimize'
```

安全约束：
- `innovate + highRiskPersonality` → downgrade to `optimize`
- `high risk_level` without personality permission → downgrade to `medium`

---

## 6. BlueCortexCE 借鉴要点

### 6.1 已具备

| EvoMap 概念 | BlueCortexCE 实现 |
|------------|------------------|
| Signal extraction | Observation Ingestion（原始文本） |
| Semantic matching | `/api/memory/search` + pgvector |
| Outcome tracking | Session summary on close |
| Narrative memory | HEARTBEAT.md（手动） |

### 6.2 可借鉴（可行）

| EvoMap 概念 | BlueCortexCE 实现建议 |
|------------|----------------------|
| Error signature normalization | 新增 `Observation.extracted_data.error_sig_norm` 字段，写入规范化 hash |
| Signal taxonomy tags | 新增 `ObservationType.label` 字段（`expandSignals` 风格） |
| Memory graph outcome edge | SessionEntity → 新增 `last_outcome_signal_key`、`outcome_score` |
| Adaptive reflection interval | 成功后 8 次循环反射，失败后 3 次 |
| Failed capsule ban | 新增 `GeneRecommendation.ban_count` + `ban_threshold` |

### 6.3 高成本（暂缓）

| EvoMap 概念 | 原因 |
|------------|------|
| Gene Pool + Selector | 需要引入 GEP 资产管理系统，超出当前 scope |
| Personality State | 5 维参数自演化系统，实现复杂 |
| Drift 机制 | 需要种群规模估计 + 随机探索，适合长期演进型 Agent |
| Narrative Memory（自动裁剪） | 当前 HEARTBEAT.md 由 cron 维护，功能对等 |

---

## 7. 关键代码引用

| 功能 | 文件 | 关键函数 |
|------|------|---------|
| Signal 提取 | `signals.js` | `extractSignals()` |
| 规范化 | `signals.js` | `normalizeErrorSignature()` |
| 稳定 Key | `memoryGraph.js` | `computeSignalKey()` |
| 标签扩展 | `learningSignals.js` | `expandSignals()` |
| Gene 评分 | `selector.js` | `scoreGene()` + `scoreGeneLearning()` |
| Gene 选择 | `selector.js` | `selectGene()` |
| Capsule 选择 | `selector.js` | `selectCapsule()` |
| Outcome 推断 | `memoryGraph.js` | `inferOutcomeEnhanced()` |
| Mutation 构建 | `mutation.js` | `buildMutation()` |
| Drift 强度 | `selector.js` | `computeDriftIntensity()` |
