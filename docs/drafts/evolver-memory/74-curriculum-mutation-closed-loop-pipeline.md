# Doc 74 — Curriculum + Mutation 闭环管线深度分析

**模块**：`curriculum.js` · `mutation.js`  
**路径**：`src/gep/`  
**源码版本**：v1.47.0（`e72778e`）  
**最后更新**：2026-05-03

---

## §1 概览：两个模块在架构中的位置

`curriculum.js`（163行）和 `mutation.js`（186行）构成 Evolver 的**意图决策层**：

```
Signal Pipeline
     ↓
curriculum.js  ──generateCurriculumSignals()──→  frontier signals
     ↓
mutation.js   ──mutationCategoryFromContext()──→  category (repair/optimize/innovate)
     ↓
solidify.js   ──执行代码变更──→
     ↓
outcome → memoryGraph ──aggregateOutcomes()──→  回流到 curriculum
```

**核心职责**：
- `curriculum.js`：**能力边界探测**——从最近 200 条 outcome 中识别已精通/失败/边界信号，生成 curriculum_target 信号
- `mutation.js`：**意图决策**——根据 error/opportunity 信号 + 人格状态，决定 mutation 类别（repair/optimize/innovate）+ 风险等级

---

## §2 curriculum.js — 能力边界探测

### §2.1 核心函数

| 函数 | 职责 |
|------|------|
| `aggregateOutcomes(memoryGraphPath)` | 从最近 200 条 outcome JSONL 提取 signal_key → {success/fail/total} 计数 |
| `identifyFrontier(outcomes)` | 按 MASTERY_THRESHOLD=0.8 / FAILURE_THRESHOLD=0.3 将信号分为 mastered/failing/frontier |
| `generateCurriculumSignals(opts)` | 输出最多 2 个 curriculum_target 信号 |
| `markCurriculumProgress(signal, outcome)` | 记录进度，每 5 次成功 +1 level（上限 5） |

### §2.2 `aggregateOutcomes()` — Outcome 聚合

```javascript
// 从 memoryGraph.jsonl 最近 200 行提取 outcome 统计
var recent = lines.slice(-200);
for (var i = 0; i < recent.length; i++) {
  var ev = JSON.parse(recent[i]);
  if (ev.kind !== 'outcome' || !ev.outcome) continue;
  var key = ev.signal_key || ev.key || '';
  // 累加 success/fail/total
}
// 输出: { [signal_key]: { success: N, fail: N, total: N } }
```

**设计观察**：
- 仅聚合最近 200 条（滑动窗口），避免历史数据污染当前判断
- 使用 `signal_key` 而非完整 signal 字符串做聚类键（规范化）
- 完全 non-fatal（所有 try/catch 静默）

### §2.3 `identifyFrontier()` — 三区分类

```javascript
var MASTERY_THRESHOLD = 0.8;
var MASTERY_MIN_ATTEMPTS = 3;
var FAILURE_THRESHOLD = 0.3;

// master: rate >= 0.8 AND total >= 3
// failing: rate <= 0.3 AND total >= 2
// frontier: 0.3 < rate < 0.8 → 最接近 0.5 的优先
frontier.sort(function (a, b) {
  return Math.abs(a.rate - 0.5) - Math.abs(b.rate - 0.5);
});
```

**关键洞察**：
- frontier 按「最不确定」排序（|rate - 0.5| 最小），即**最有学习价值**的信号优先
- mastery 需要至少 3 次尝试，防止单次成功误判
- failing rate ≤ 0.3 表示系统**持续失败**，需要修复

### §2.4 `generateCurriculumSignals()` — 信号生成

```javascript
var MAX_CURRICULUM_SIGNALS = 2;

function generateCurriculumSignals({ capabilityGaps, memoryGraphPath, personality }) {
  var outcomes = aggregateOutcomes(memoryGraphPath);
  var analysis = identifyFrontier(outcomes);

  // 第一优先：capabilityGaps（来自 Hub 的外部反馈）
  if (capabilityGaps.length > 0) {
    var gapTarget = capabilityGaps[0];
    if (!analysis.mastered.some(m => m.key.includes(gapTarget))) {
      signals.push('curriculum_target:gap:' + gapTarget.slice(0, 60));
    }
  }

  // 第二优先：frontier 信号（系统内生不确定性）
  if (signals.length < MAX_CURRICULUM_SIGNALS && analysis.frontier.length > 0) {
    var best = analysis.frontier[0];
    if (!signals.some(s => s.includes(best.key))) {
      signals.push('curriculum_target:frontier:' + best.key.slice(0, 60));
    }
  }

  // 持久化 current_targets 到 curriculum_state.json
  saveCurriculumState({ current_targets: signals, level: state.level });
  return signals.slice(0, MAX_CURRICULUM_SIGNALS);
}
```

**三层信号来源优先级**：
1. **Hub capability gaps**（外部反馈）—— 来自 Hub 的 `_latestCapabilityGaps`
2. **内生 frontier**（系统自测）—— 来自 memoryGraph 的滑动窗口分析
3. **空 → 无 curriculum 信号**

### §2.5 `markCurriculumProgress()` — 进度追踪

```javascript
// 每完成一个 curriculum signal，记录 outcome
// 成功累计每 5 次 → level++（上限 5）
// completed 数组最多保留 50 条（滑动窗口）
state.completed.slice(-50);
if (successCount > 0 && successCount % 5 === 0 && state.level < 5) {
  state.level++;
}
```

**设计观察**：
- level 是 1-5 的整数，代表课程难度等级
- 进度在本地 `curriculum_state.json` 持久化，与 memoryGraph 分离

### §2.6 CE 借鉴路径

| 优先级 | 借鉴点 | 方案 |
|--------|--------|------|
| **P0** | Outcome 聚合 | CE 在 `OutcomeEntity` 中添加 `signal_key` 规范化字段，按 sliding window 聚合 success/fail/total |
| **P0** | Frontier 探测 | CE 实现 `identifyFrontier(signals)`，将 0.3-0.8 success rate 的观察标记为 frontier，提供给 LLM 作为「值得探索」的提示 |
| **P1** | Capability Gap 信号 | CE 可从 Phase 3 structured extraction 结果中提取 capability gaps，注入 curriculum 信号 |
| **P1** | Level progression | CE 维护 `curriculum_level`（1-5），每 N 次成功升级，用于控制探索激进程度 |
| **P2** | curriculum_target signal format | CE 可定义 `ce_frontier:{observation_type}:{signal_key}` 信号格式，保持与 Evolver 兼容 |

---

## §3 mutation.js — 意图决策引擎

### §3.1 核心函数

| 函数 | 职责 |
|------|------|
| `mutationCategoryFromContext({signals, driftEnabled})` | 5 层决策树 → repair / optimize / innovate |
| `buildMutation(...)` | 构造完整 Mutation 对象（含安全降级） |
| `isValidMutation(obj)` | 验证 Mutation schema 合法性 |
| `normalizeMutation(obj)` | 规范化任意对象为合规 Mutation |
| `hasOpportunitySignal(signals)` | 检测 9 种 opportunity 信号 |
| `isHighRiskMutationAllowed(personalityState)` | 风险等级守卫 |

### §3.2 9 种 Opportunity Signals

```javascript
var OPPORTUNITY_SIGNALS = [
  'user_feature_request',         // 用户功能请求
  'user_improvement_suggestion',  // 用户改进建议
  'perf_bottleneck',              // 性能瓶颈
  'capability_gap',               // 能力缺口
  'stable_success_plateau',        // 稳定成功 plateau
  'external_opportunity',          // 外部机会
  'issue_already_resolved',        // ⚡ 问题已被外部修复（CE 可借鉴：检测 self-healed）
  'openclaw_self_healed',         // ⚡ OpenClaw 自我修复（CE 可借鉴：检测 autonomous recovery）
  'empty_cycle_loop_detected',     // ⚡ 检测到空转循环（CE 可借鉴：idle cycle 检测）
];
```

**关键洞察**：3 个「自我感知」机会信号（`issue_already_resolved`/`openclaw_self_healed`/`empty_cycle_loop_detected`）使系统能够识别**不需干预**的情况——这是 Evolver 的「无为」检测机制。

### §3.3 `mutationCategoryFromContext()` — 5 层决策树

```javascript
function mutationCategoryFromContext({ signals, driftEnabled }) {
  // 第 1 层：error 信号 → repair（最高优先）
  if (hasErrorishSignal(signals)) return 'repair';

  // 第 2 层：显式 drift 开启 → innovate
  if (driftEnabled) return 'innovate';

  // 第 3 层：opportunity 信号存在 → innovate
  if (hasOpportunitySignal(signals)) return 'innovate';

  // 第 4 层：strategy preset 的 innovate 权重 ≥ 0.5 → innovate
  try {
    var strategy = require('./strategy').resolveStrategy();
    if (strategy && strategy.innovate >= 0.5) return 'innovate';
  } catch (_) {}

  // 第 5 层：默认 → optimize
  return 'optimize';
}
```

**决策优先级**：repair > drift > opportunity > strategy > optimize

### §3.4 `hasErrorishSignal()` — 错误信号识别

```javascript
function hasErrorishSignal(signals) {
  var list = Array.isArray(signals) ? signals.map(s => String(s || '')) : [];
  // 注意：以下两个被排除（不是错误，是已修复）
  if (list.includes('issue_already_resolved')) return false;
  if (list.includes('openclaw_self_healed')) return false;
  // 以下是错误信号
  if (list.includes('log_error')) return true;
  if (list.some(s => s.startsWith('errsig:') || s.startsWith('errsig_norm:'))) return true;
  return false;
}
```

**设计精妙之处**：`issue_already_resolved` 和 `openclaw_self_healed` 同时出现在 OPPORTUNITY_SIGNALS 和 hasErrorishSignal 的排除列表中——它们既是机会信号（可用于 analytics），又不是错误信号（不触发 repair）。

### §3.5 `buildMutation()` — Mutation 对象构造 + 安全降级

```javascript
function buildMutation({
  signals, selectedGene, driftEnabled,
  personalityState, allowHighRisk = false,
  target, expected_effect,
} = {}) {
  // 1. 确定 category（5层决策树）
  var category = mutationCategoryFromContext({ signals, driftEnabled });

  // 2. 基础 Mutation 对象
  var base = {
    type: 'Mutation',
    id: `mut_${ts}`,
    category: category,
    trigger_signals: uniqStrings(signals),
    target: target || targetFromGene(selectedGene),
    expected_effect: expected_effect || expectedEffectFromCategory(category),
    risk_level: 'low',
  };

  // 3. innovate → medium risk
  if (category === 'innovate') base.risk_level = 'medium';

  // 4. 高风险升级（有条件）
  if (allowHighRisk && category === 'innovate') base.risk_level = 'high';

  // 5. 安全降级：high-risk personality + innovate → downgrade to optimize
  if (base.category === 'innovate' && isHighRiskPersonality(personalityState)) {
    base.category = 'optimize';
    base.expected_effect = 'safety downgrade: optimize under high-risk personality';
    base.risk_level = 'low';
    base.trigger_signals.push('safety:avoid_innovate_with_high_risk_personality');
  }

  // 6. 双重守卫：high risk + 不足 rigor → downgrade to medium
  if (base.risk_level === 'high' && !isHighRiskMutationAllowed(personalityState)) {
    base.risk_level = 'medium';
    base.trigger_signals.push('safety:downgrade_high_risk');
  }

  return base;
}
```

**风险等级降级链**：
```
high → [isHighRiskMutationAllowed fails] → medium
high + [isHighRiskPersonality] → optimize (category change)
```

### §3.6 `isHighRiskMutationAllowed()` — 人格安全守卫

```javascript
function isHighRiskMutationAllowed(personalityState) {
  var rigor = personalityState?.rigor ?? 0;    // 需要 ≥ 0.6
  var riskTol = personalityState?.risk_tolerance ?? 1;  // 需要 ≤ 0.5
  return rigor >= 0.6 && riskTol <= 0.5;
}
```

**含义**：高风险操作需要**高严谨度 + 低风险偏好**。这是一个典型的「能力-意愿」分离的安全模型。

### §3.7 `isHighRiskPersonality()` — 粗筛

```javascript
function isHighRiskPersonality(p) {
  var rigor = p?.rigor ?? null;
  var riskTol = p?.risk_tolerance ?? null;
  if (rigor != null && rigor < 0.5) return true;    // 低严谨度
  if (riskTol != null && riskTol > 0.6) return true; // 高风险偏好
  return false;
}
```

### §3.8 CE 借鉴路径

| 优先级 | 借鉴点 | 方案 |
|--------|--------|------|
| **P0** | 5 层决策树 | CE 构造 `MutationContext.decideCategory()`，优先 repair > opportunity > frontier > optimize |
| **P0** | Opportunity signals | CE 添加 `self_healed`/`idle_cycle_detected` 检测，识别「不需干预」情况 |
| **P1** | 人格安全模型 | CE 的 `ObservationEntity` 添加 `rigor`/`risk_tolerance` 字段，高风险操作前做能力-意愿检查 |
| **P1** | `isHighRiskMutationAllowed` | CE structured extraction 高风险操作（涉及文件系统/网络）需 rigor ≥ 0.6 |
| **P2** | Safety downgrade 信号 | CE 操作降级时记录 `safety:*` 信号，便于审计 |
| **P2** | Normalize mutation | CE 的 structured extraction 结果可复用 `normalizeMutation` 模式做 schema 规范化 |

---

## §4 闭环管线端到端

```
┌─────────────────────────────────────────────────────────┐
│                    EVOLUTION CYCLE                       │
│                                                          │
│  1. memoryGraph.jsonl (recent 200 outcomes)              │
│     ↓ aggregateOutcomes()                                │
│                                                          │
│  2. { mastered / failing / frontier }                   │
│     ↓ identifyFrontier()                                 │
│                                                          │
│  3. curriculum_signals (≤2)                            │
│     + Hub.capabilityGaps                                │
│     ↓ generateCurriculumSignals()                        │
│                                                          │
│  4. signals[] (curriculum + error + opportunity)        │
│     ↓ mutationCategoryFromContext()                      │
│                                                          │
│  5. category ∈ { repair / optimize / innovate }         │
│     + personality safety check                          │
│     ↓ buildMutation()                                    │
│                                                          │
│  6. Mutation { category, risk_level, trigger_signals }  │
│     ↓ solidify()                                         │
│                                                          │
│  7. outcome → memoryGraph.jsonl                         │
│     → loop back to step 1                               │
└─────────────────────────────────────────────────────────┘
```

### §4.1 关键数值常量

| 常量 | 值 | 含义 |
|------|-----|------|
| `MASTERY_THRESHOLD` | 0.8 | success rate ≥ 80% → mastered |
| `MASTERY_MIN_ATTEMPTS` | 3 | 至少 3 次尝试才判断 mastery |
| `FAILURE_THRESHOLD` | 0.3 | success rate ≤ 30% → failing |
| `MAX_CURRICULUM_SIGNALS` | 2 | 每次最多输出 2 个 curriculum 信号 |
| curriculum level max | 5 | 课程难度等级上限 |
| completed 滑动窗口 | 50 | progress 记录最多保留 50 条 |

### §4.2 与 strategy.js 的联动

`mutationCategoryFromContext()` 第 4 层调用 `strategy.resolveStrategy()`：

```javascript
// strategy.js resolveStrategy() 决策顺序：
// 1. FORCE_INNOVATION=true → 'innovate'
// 2. cycle ≤ 5 → 'early-stabilize'（前 5 轮优先稳定）
// 3. 'force_steady_state' signal → 'steady-state'
// 4. 'evolution_saturation' signal → 'steady-state'
// 5. 默认 → 'balanced'
```

**关键洞察**：strategy 层控制**宏观方向**（前 5 轮 stabilization / 饱和期 steady-state），mutation 层控制**微观决策**（每轮 repair/optimize/innovate）。两层分工明确。

### §4.3 「无为」检测机制

最值得 CE 借鉴的设计——`empty_cycle_loop_detected` + `openclaw_self_healed` + `issue_already_resolved`：

```javascript
// mutation.js 中：
if (hasOpportunitySignal(signals)) return 'innovate';
// 但 hasErrorishSignal 排除了 issue_already_resolved 和 openclaw_self_healed

// signals.js 中的处理：
// 这类信号 → opportunity，但不触发 repair
// → 记录到 narrative，但不强制执行操作
```

**CE 等价设计**：BlueCortexCE 的 Phase 3 extraction 中，检测到「上次已成功解决」或「系统自动恢复」的情况，应该记录但不重复干预。

---

## §5 与其他模块的关系

| 模块 | 关系 |
|------|------|
| `signals.js` | 提供 error/opportunity 信号输入 |
| `memoryGraph.js` | 提供 outcome JSONL 数据源 |
| `solidify.js` | 消费 Mutation，执行代码变更 |
| `strategy.js` | 第 4 层 fallback 决策源 |
| `personality.js` | 提供人格状态（rigor/risk_tolerance） |
| `hubSearch.js` | Hub capability gaps 作为外部信号源 |
| `narrativeMemory.js` | mutation 结果写入 evolution_narrative.md |

---

## §6 CE P0/P1/P2 综合建议

| 优先级 | 具体行动 |
|--------|----------|
| **P0** | 在 `ObservationEntity` 添加 `signal_key` 规范化字段（对应 Evolver 的 `signal_key`） |
| **P0** | 实现 `identifyFrontier()`：从最近 N 条 observation 聚合 success rate，标记 frontier 观察 |
| **P0** | 实现 5 层决策树 `MutationContext.decideCategory()`，优先处理 error > opportunity > frontier |
| **P1** | 添加「自我恢复」检测（`self_healed` / `idle_cycle_detected`），避免重复干预 |
| **P1** | 添加人格安全模型（`rigor`/`risk_tolerance`），高风险 structured extraction 操作前做检查 |
| **P2** | 实现 curriculum level（1-5），根据 level 控制探索激进程度 |
| **P2** | 与 Phase 3 structured extraction 联动：从 extraction 结果生成 curriculum signals |

---

## §7 源码索引

| 文件 | 行数 | 关键导出 |
|------|------|----------|
| `src/gep/curriculum.js` | 163 | `generateCurriculumSignals` · `markCurriculumProgress` · `loadCurriculumState` |
| `src/gep/mutation.js` | 186 | `buildMutation` · `mutationCategoryFromContext` · `hasOpportunitySignal` · `isHighRiskMutationAllowed` |
| `src/gep/strategy.js` | 131 | `resolveStrategy` · `getStrategyNames` · `STRATEGIES` |
