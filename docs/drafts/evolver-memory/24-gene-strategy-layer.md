# Gene/Strategy 层架构分析

> **角色**：分析 EvoMap/evolver 的 Gene Pool 层（策略基因选择 + 变异 + 候选池），补充现有 [`18`](./18-evolver-local-source-memory-architecture-snapshot.md) 对 `memoryGraph.js` 的覆盖。  
> **数据来源**：`src/gep/strategy.js`、`src/gep/selector.js`、`src/gep/mutation.js`、`src/gep/candidates.js`。  
> **前置**：先读 [`18`](./18-evolver-local-source-memory-architecture-snapshot.md)（Memory Graph 架构）。  
> **状态**：v1 初稿（待定稿迁入 `24-gene-strategy-layer.md`）

---

## 1. 整体架构：Memory Graph 之上的 Gene Pool

Evolver 的记忆系统分为**两层**：

| 层 | 职责 | 核心模块 |
|----|------|----------|
| **Memory Graph** | 存储观察、信号、叙事；维护 signal×gene 边 | `memoryGraph.js` |
| **Gene Pool** | 可复用的行为策略模板库；根据信号选择、变异、评估 | `selector.js` + `strategy.js` + `mutation.js` + `candidates.js` |

**Gene**（策略基因）= 一个行为策略模板，包含：
- `id`：唯一标识
- `signals_match`：匹配模式数组（字符串前缀 / regex / 多语言别名）
- `summary`：策略摘要描述
- `epigenetic_marks`：环境相关适应标记（context → score）
- `learning_signals`：关联的学习信号标签

**关键类比**：
- Evolver Gene Pool ≈ **BlueCortexCE 的 `SearchService` observation pool**（都是"根据信号检索匹配的记忆单元"）
- 区别：Gene Pool 存**行为策略**（可执行），Observation pool 存**历史观察**（可检索上下文）

---

## 2. Strategy Presets（strategy.js, 131 行）

### 2.1 七种预设

| 预设 | repair | optimize | innovate | 适用场景 |
|------|--------|----------|----------|----------|
| `balanced` | 0.20 | 0.30 | 0.50 | 正常运营 |
| `innovate` | 0.05 | 0.15 | 0.80 | 系统稳定，最大化新能力 |
| `harden` | 0.40 | 0.40 | 0.20 | 大变更后，稳定性和健壮性 |
| `repair-only` | 0.80 | 0.20 | 0.00 | 紧急修复 |
| `early-stabilize` | 0.60 | 0.25 | 0.15 | 初始阶段，先修后创 |
| `steady-state` | 0.60 | 0.30 | 0.10 | 演化饱和，维持现状 |
| `auto` | — | — | — | 根据 cycle count + 饱和信号自适应 |

每种预设还定义了 `repairLoopThreshold`（repair 占比阈值，超过则触发强制 innovation）。

### 2.2 读取机制

```javascript
// 读取 evolution_state.json 获取当前 cycle count
var localPath = path.resolve(__dirname, '..', '..', 'memory', 'evolution_state.json');
var workspacePath = path.resolve(__dirname, '..', '..', '..', '..', 'memory', 'evolution', 'evolution_state.json');
```

**双路径查找**：先找 evolver 内部的 `memory/evolution_state.json`，再找 workspace 的 `memory/evolution/evolution_state.json`。

### 2.3 对 BlueCortexCE 的借鉴

**可控的"上下文注入强度"策略**：类似 Evolver 的 repair/optimize/innovate 比例分配，BlueCortexCE 可以引入"观察类型注入策略"：
- 高错误率时 → 优先注入 error 相关 observations（repair 类比）
- 稳定期 → 优先注入 success/innovation observations（innovate 类比）

---

## 3. Gene Selector（selector.js, 417 行）

### 3.1 多因子评分：`scoreGene`

```javascript
function scoreGene(gene, signals) {
  // 1. Exact match: signals_match 精确/前缀/regex/多语言别名匹配
  const exact = scoreGeneExact(gene, signals);
  
  // 2. Semantic: bag-of-words cosine similarity（tokenize + TF + cosine）
  const semantic = scoreGeneSemantic(gene, signals) * SEMANTIC_WEIGHT;
  
  // 3. Epigenetic: 环境指纹匹配
  const epigenetic = getEpigeneticBoostLocal(gene, envContext);
  
  // 4. Learning signal overlap
  const learning = scoreGeneLearning(gene, signals, envFingerprint);
  
  return (exact + semantic + epigenetic + learning).clamp(0, 1);
}
```

**关键设计**：
- `SEMANTIC_MATCH_WEIGHT = 0.4`（默认），可通过环境变量配置
- Semantic 用 bag-of-words 而非 embedding（轻量，可离线工作）
- `clamp01` 保证分数在 [0, 1]

### 3.2 模式匹配：`matchPatternToSignals`

```javascript
// 三种匹配模式
// 1. Regex: /body/flags（如 /err.*file/i）
if (p.startsWith('/') && p.lastIndexOf('/') > 0) {
  const re = new RegExp(body, flags || 'i');
  return sig.some(s => re.test(s));
}

// 2. Multi-language alias: "en_term|zh_term|ja_term"（任意分支命中即命中）
if (p.includes('|') && !p.startsWith('/')) {
  const branches = p.split('|').map(b => b.trim().toLowerCase());
  return branches.some(needle => sig.some(s => s.toLowerCase().includes(needle)));
}

// 3. Substring: 普通字符串包含匹配
const needle = p.toLowerCase();
return sig.some(s => s.toLowerCase().includes(needle));
```

### 3.3 Capability Gap 感知选择

```javascript
function selectGene(genes, signals, opts) {
  // ...
  // 如果有 capability gap 信号，增强对应 gene 的分数
  if (gapSignal && patterns.some(p => matchPatternToSignals(p, [gapSignal]))) {
    entry.gapBonus = 0.15; // +15% bonus for gap-filling genes
  }
  // ...
}
```

### 3.4 对 BlueCortexCE 的借鉴

**语义+精确混合检索**：BlueCortexCE `SearchService` 目前主要是向量检索（语义），可以借鉴 Evolver 增加：
1. **Exact signal filter**：根据 `requiredSignals` 做精确匹配过滤
2. **Bag-of-words cosine fallback**：当 embedding 服务不可用时的轻量备选
3. **Capability gap boost**：当检测到用户提问类型时，boost 相关 observations

---

## 4. Mutation 模块（mutation.js, 186 行）

### 4.1 信号→变异类别映射

```javascript
function mutationCategoryFromContext({ signals, driftEnabled }) {
  if (hasErrorishSignal(signals)) return 'repair';        // log_error / errsig:*
  if (hasOpportunitySignal(signals)) return 'innovate';  // user_feature_request / perf_bottleneck 等
  if (driftEnabled) return 'innovate';
  // Consult strategy preset
  if (strategy.innovate >= 0.5) return 'innovate';
  return 'optimize';
}
```

**机会信号列表**：
```javascript
var OPPORTUNITY_SIGNALS = [
  'user_feature_request',
  'user_improvement_suggestion',
  'perf_bottleneck',
  'capability_gap',
  'stable_success_plateau',
  'external_opportunity',
  'issue_already_resolved',
  'openclaw_self_healed',
  'empty_cycle_loop_detected',
];
```

### 4.2 安全约束

```javascript
// 高风险人格（低 rigor 或高 risk_tolerance）禁止 innovate 变异
if (category === 'innovate' && isHighRiskPersonality(personalityState)) {
  base.category = 'optimize';
  base.trigger_signals.push('safety:avoid_innovate_with_high_risk_personality');
}

// 未授权的高风险变异降级为 medium
if (base.risk_level === 'high' && !isHighRiskMutationAllowed(personalityState)) {
  base.risk_level = 'medium';
}
```

**变异风险等级**：
- `repair` / `optimize` → `low`
- `innovate` → `medium`（默认）
- `innovate` + 授权 → `high`

### 4.3 对 BlueCortexCE 的借鉴

**观察类型的风险分类**：BlueCortexCE 暂无"观察风险分级"机制。借鉴 mutation.js：
- error 类型 observations → 低风险（已知问题）
- capability_gap / perf_bottleneck → 中风险（需人工确认）
- user_feature_request → 高风险（可能改变系统行为）

---

## 5. Candidates Pool（candidates.js, 208 行）

### 5.1 核心操作

| 操作 | 说明 |
|------|------|
| `extractToolCalls(transcript)` | 从对话记录中提取工具调用（用于生成候选） |
| `buildFiveQuestionsShape()` | 格式化候选问题结构 |
| `extractCapabilityCandidates()` | 从失败 caps + 信号中提取能力缺口候选 |
| `renderCandidatesPreview()` | 预览候选列表（maxChars 1400） |

### 5.2 失败胶囊提取

```javascript
function extractCapabilityCandidates({ recentSessionTranscript, signals, recentFailedCapsules }) {
  // 从失败胶囊中提取 failureTags
  var failureTags = expandSignals(fc.trigger.concat(signalList), reason)
    .filter(tag => tag.includes('area:') || tag.includes('risk:'));
  
  // 按 area 分组
  Object.keys(groups).forEach(key => { ... });
}
```

---

## 6. 关键设计思想总结

### 6.1 双层记忆架构（Memory Graph + Gene Pool）

```
Signals → Gene Pool → Selected Gene → Mutation → Capsule → Outcome
              ↑
      Memory Graph (signal×gene edges with decay)
```

- Memory Graph 负责**叙事和观察存储**
- Gene Pool 负责**行为策略选择**
- 两者通过 signal×gene 边连接（`getMemoryAdvice`）

### 6.2 轻量级语义（Bag-of-Words）

不使用 embedding 服务时，用 bag-of-words cosine 替代：
- Tokenize（去除 stop words）→ TF → cosine similarity
- 可配置 `SEMANTIC_MATCH_WEIGHT = 0.4`

### 6.3 安全约束内嵌

- Personality state 决定最大风险等级
- 高风险人格 + innovate = 自动降级
- 变异风险分三级：low / medium / high

---

## 7. 与 BlueCortexCE 的对照

| Evolver 概念 | BlueCortexCE 类比 | 差距 |
|--------------|-------------------|------|
| Gene Pool (strategy templates) | `ObservationEntity` pool | Gene 存策略模板；Observation 存历史事件 |
| Signal → Gene selection | `SearchService` 检索 + `requiredSignals` 过滤 | 无风险分级、无 capability gap boost |
| Strategy presets (repair/optimize/innovate) | 无对应 | 可引入"观察注入策略"控制注入比例 |
| Mutation safety constraints | 无对应 | 可引入"高风险观察人工确认"机制 |
| Bag-of-words semantic fallback | 完全依赖 pgvector embedding | 可增加轻量备选检索 |
| Candidates pool | 无对应 | 候选基因池是 Evolver 独有 |

---

*状态：v1 初稿。后续可补充 selector §5 `selectGeneAndCapsule` 决策树 + candidates `extractCapabilityCandidates` 完整流程图。*
