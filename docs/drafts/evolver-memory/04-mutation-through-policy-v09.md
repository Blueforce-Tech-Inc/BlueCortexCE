<!-- part 4/8: auto-split from evolver-memory-analysis.md — see index.md -->

## 35. mutation.js — 基因突变算法 (v0.7 新增)

**文件**: `src/gep/mutation.js` (186 lines)

### 35.1 突变类别决策

```javascript
// mutation.js:44
function mutationCategoryFromContext({ signals, driftEnabled }) {
  if (hasErrorishSignal(signals)) return 'repair';
  if (driftEnabled) return 'innovate';
  if (hasOpportunitySignal(signals)) return 'innovate';
  // Check strategy preset for innovation preference
  try {
    var strategy = require('./strategy').resolveStrategy();
    if (strategy && typeof strategy.innovate === 'number' && strategy.innovate >= 0.5) return 'innovate';
  } catch (_) {}
  return 'optimize';
}
```

**决策树**：

```
Error signal present? ──YES──→ repair
         │
         NO
         ↓
driftEnabled (random)? ──YES──→ innovate
         │
         NO
         ↓
Opportunity signal? ──YES──→ innovate
         │
         NO
         ↓
Strategy.innovate >= 0.5? ──YES──→ innovate
         │
         NO
         ↓
      optimize
```

### 35.2 OPPORTUNITY_SIGNALS 清单

```javascript
// mutation.js:23
var OPPORTUNITY_SIGNALS = [
  'user_feature_request',      // 用户功能请求
  'user_improvement_suggestion', // 用户改进建议
  'perf_bottleneck',           // 性能瓶颈
  'capability_gap',            // 能力差距
  'stable_success_plateau',    // 稳定成功 plateau
  'external_opportunity',       // 外部机会
  'issue_already_resolved',    // 已解决的 issue
  'openclaw_self_healed',      // 自愈
  'empty_cycle_loop_detected', // 空循环检测
];
```

### 35.3 安全约束（硬性规则）

```javascript
// mutation.js:126
function buildMutation({ ..., personalityState, allowHighRisk = false }) {
  // Rule 1: innovate + high-risk personality → downgrade to optimize
  const highRiskPersonality = isHighRiskPersonality(personalityState || null);
  if (base.category === 'innovate' && highRiskPersonality) {
    base.category = 'optimize';
    base.risk_level = 'low';
  }

  // Rule 2: high-risk mutation + personality disallows → cap to medium
  if (base.risk_level === 'high' && !isHighRiskMutationAllowed(personalityState || null)) {
    base.risk_level = 'medium';
  }
}
```

**高风险人格判断**：

```javascript
// mutation.js:70
function isHighRiskPersonality(p) {
  const rigor = p && Number.isFinite(Number(p.rigor)) ? Number(p.rigor) : null;
  const riskTol = p && Number.isFinite(Number(p.risk_tolerance)) ? Number(p.risk_tolerance) : null;
  if (rigor != null && rigor < 0.5) return true;       // rigor < 0.5 → high-risk
  if (riskTol != null && riskTol > 0.6) return true;  // risk_tolerance > 0.6 → high-risk
  return false;
}

function isHighRiskMutationAllowed(personalityState) {
  const rigor = personalityState?.rigor ?? 0;
  const riskTol = personalityState?.risk_tolerance ?? 1;
  return rigor >= 0.6 && riskTol <= 0.5;  // 只有 rigor 高 + risk 低才允许高风险突变
}
```

### 35.4 Mutation 对象结构

```javascript
// mutation.js:36
const base = {
  type: 'Mutation',
  id: `mut_${ts}`,                    // 唯一 ID
  category: mutationCategory,          // repair | optimize | innovate
  trigger_signals: triggerSignals,     // 触发此突变的信号列表
  target: String(target || targetFromGene(selectedGene)),  // gene:${id} | behavior:protocol
  expected_effect: String(expected_effect || expectedEffectFromCategory(category)),
  risk_level: riskLevel,               // low | medium | high
};
```

### 35.5 BlueCortexCE 借鉴建议

| Evolver 机制 | BlueCortexCE 现状 | 翻译：旁路型如何借鉴 | 优先级 |
|-------------|------------------|---------------------|--------|
| 三类突变决策树 | 无（只有 Observation 记录） | 记忆可增加 intent 字段（repair/optimize/innovate） | 中 |
| 安全约束（高风险人格降级） | 无 | 通过 API 传递 personality 参数影响生成策略 | 低 |
| expected_effect 显式声明 | 无 | Summary 增加 expected_impact 字段 | 低 |
| risk_level 分级 | 无 | 可以作为 MemoryRefineService 的优先级参考 | 中 |

**核心差距**：Evolver 的 mutation 是"主动生成"的，BlueCortexCE 的记忆是"被动记录"的。在旁路型架构下，可以把 mutation 逻辑翻译为"记忆优先级 + 检索权重"。

---

## 36. evolve.js — 核心进化循环 (v0.7 新增)

**文件**: `src/evolve.js` (2177+ lines)

### 36.1 run() 函数核心流程

```javascript
// evolve.js:1056
async function run() {
  // 阶段 1: 前置检查
  const preflight = await runPreflightChecks(bridgeEnabled, loopMode);
  if (preflight.abort) return;

  // 阶段 2: 会话日志读取
  const recentMasterLog = readRealSessionLog();
  const todayLog = readRecentLog(TODAY_LOG);
  const memorySnippet = readMemorySnippet();
  const userSnippet = readUserSnippet();

  // 阶段 3: 资产加载
  const genes = loadGenes();
  const capsules = loadCapsules();
  const recentEvents = readAllEvents().filter(e => e.type === 'EvolutionEvent').slice(-80);

  // 阶段 4: 信号提取
  const signals = extractSignals({
    recentSessionTranscript: recentMasterLog,
    todayLog,
    memorySnippet,
    userSnippet,
    recentEvents,
  });

  // 阶段 5: Hub 任务认领（可选）
  if (!skipHubCalls) {
    const fetchResult = await fetchTasks({ questions: proactiveQuestions });
    // ... task 认领逻辑
  }

  // 阶段 6: Gene + Capsule 选择
  const { selectedGene, capsuleCandidates, selector } = selectGeneAndCapsule({
    genes, capsules, signals, memoryAdvice, driftEnabled, ...
  });

  // 阶段 7: Personality 选择
  const personalitySelection = selectPersonalityForRun({ driftEnabled, signals, recentEvents });
  const personalityState = personalitySelection?.personality_state;

  // 阶段 8: Mutation 构建
  const mutation = buildMutation({
    signals: mutationSignalsEffective,
    selectedGene,
    driftEnabled: mutationInnovateMode,
    personalityState,
    allowHighRisk,
  });

  // 阶段 9: Memory Graph 记录 hypothesis + attempt
  const hypothesisId = recordHypothesis({ signals, mutation, personalityState, ... });
  recordAttempt({ signals, mutation, personalityState, hypothesisId, ... });

  // 阶段 10: 构建 Prompt 并执行 LLM
  const { prompt, stopSignal } = buildGepPrompt({ selectedGene, capsuleCandidates, mutation, ... });
  const llmOutput = await callLLM(prompt);

  // 阶段 11: 解析 + 应用 Patch
  const patch = parsePatch(llmOutput);
  applyPatch(patch);

  // 阶段 12: 触发 solidify
  if (needsSolidify) {
    writeStateForSolidify({ run_id, mutation, selectedGene, ... });
  }
}
```

### 36.2 信号注入点（多来源合并）

Evolver 的 signals 是**多来源合并**的，不是单一来源：

```javascript
// evolve.js:1268
const signals = extractSignals({ recentSessionTranscript, todayLog, memorySnippet, userSnippet, recentEvents });

// + Hub task signals (unshift to front, highest priority)
if (activeTask) {
  signals.unshift(...taskSignals);
}

// + Dormant hypothesis signals (carry-over from interrupted cycle)
if (dormantHypothesis) {
  signals.push(...dormantHypothesis.signals);
}

// + Curriculum signals (progressive learning targets)
if (curriculumSignals.length > 0) {
  signals.push(...curriculumSignals);
}

// + Retry context (from previous validation failure)
if (solidifyState.last_validation_failure) {
  signals.push('retry_error_context', 'retry_cmd:...', 'retry_stderr:...');
}
```

**Evolver 做法**：信号按优先级排序（Hub task > session > curriculum > retry）。
**借鉴点**：BlueCortexCE 可以为不同来源的 Observation 分配优先级权重。

### 36.3 Idle-Cycle Gating（空闲周期门控）

```javascript
// evolve.js:58
function shouldSkipHubCalls(signals) {
  if (!Array.isArray(signals)) return false;
  const saturationIndicators = ['force_steady_state', 'evolution_saturation', 'empty_cycle_loop_detected'];
  let hasSaturation = false;
  for (let si = 0; si < saturationIndicators.length; si++) {
    if (signals.indexOf(saturationIndicators[si]) !== -1) { hasSaturation = true; break; }
  }
  if (!hasSaturation) return false;

  // Check for actionable signals
  const actionablePatterns = ['log_error', 'recurring_error', 'capability_gap', ...];
  for (let ai = 0; ai < signals.length; ai++) {
    const s = signals[ai];
    if (actionablePatterns.indexOf(s) !== -1) return false;
    if (s.indexOf('errsig:') === 0) return false;
    // ...
  }
  return true;  // Saturation + no actionable signals → skip Hub
}
```

**Evolver 做法**：当系统处于"饱和状态"且无任何可执行信号时，跳过 Hub API 调用（默认 30 分钟内最多一次）。
**借鉴点**：BlueCortexCE 可以实现"智能降频"——当最近的 Observation 都是低优先级且检索命中率低时，降低采样频率。

### 36.4 Hub Event 信号注入

```javascript
// evolve.js:1454
const HUB_EVENT_SIGNALS = {
  dialog_message: ['dialog', 'respond_required'],
  council_invite: ['council', 'governance', 'respond_required'],
  task_overdue: ['overdue_task', 'urgent'],
  // ... 20+ 事件类型
};
for (const ev of hubEvents) {
  const evSignals = HUB_EVENT_SIGNALS[ev.type] || ['hub_event'];
  for (const sig of evSignals) {
    if (!signals.includes(sig)) signals.unshift(sig);
  }
}
```

**Evolver 做法**：Hub 事件（来自 A2A Protocol）被转换为信号并注入到当前循环。
**借鉴点**：BlueCortexCE 未来可以通过 WebSocket/轮询接收外部事件并转换为记忆信号。

### 36.5 BlueCortexCE 借鉴建议

| Evolver 机制 | BlueCortexCE 现状 | 翻译：旁路型如何借鉴 | 优先级 |
|-------------|------------------|---------------------|--------|
| 多来源信号合并 | Observation 分散无聚合 | 增加 signal_aggregation 机制 | 高 |
| Idle-cycle gating | 无降频机制 | 增加 fetch-throttle 配置 | 中 |
| Hub event → signals | 无外部事件集成 | Feishu/外部事件可作为特殊信号源 | 低 |
| 30分钟 Hub 调用上限 | 无 | 外部服务调用增加指数退避 | 中 |

---

## 37. prompt.js — GEP 提示词构建 (v0.8 新增)

**文件**: `src/gep/prompt.js` (27KB, 与 strategy.js + questionGenerator.js 共存于同一文件)

### 37.1 核心设计原则

`prompt.js` 是 Evolver 的 **LLM Prompt 工厂**——它将所有上下文（信号、基因、候选、叙事等）组装为单个发送给 LLM 的 prompt。核心原则：

1. **Schema First**: 严格规定 LLM 必须输出 5 个 JSON 对象（Mutation、PersonalityState、EvolutionEvent、Gene、Capsule）
2. **JSON Only**: 禁止 markdown 代码块包裹 JSON，输出原始 JSON
3. **智能截断**: 优先保留 header/footer，截断 Execution Context 中间部分

### 37.2 强制 Schema 定义 (SCHEMA_DEFINITIONS)

**文件**: `prompt.js:80-140`

```javascript
const SCHEMA_DEFINITIONS = `
━━━━━━━━━━━━━━━━━━━━━━
I. Mandatory Evolution Object Model (Output EXACTLY these 5 objects)
━━━━━━━━━━━━━━━━━━━━━━

Output separate JSON objects. DO NOT wrap in a single array.
DO NOT use markdown code blocks (like \`\`\`json ... \`\`\`).
Output RAW JSON ONLY. No prelude, no postscript.
Missing any object = PROTOCOL FAILURE.
ENSURE VALID JSON SYNTAX (escape quotes in strings).

0. Mutation (The Trigger) - MUST BE FIRST
   {
     "type": "Mutation",
     "id": "mut_<timestamp>",
     "category": "repair|optimize|innovate",
     "trigger_signals": ["<signal_string>"],
     "target": "<module_or_gene_id>",
     "expected_effect": "<outcome_description>",
     "risk_level": "low|medium|high",
     "rationale": "<why_this_change_is_necessary>"
   }

1. PersonalityState (The Mood)
   { "type": "PersonalityState", "rigor": 0.0-1.0, ... }

2. EvolutionEvent (The Record)
   { "type": "EvolutionEvent", "schema_version": "1.5.0", ... }

3. Gene (The Knowledge)
   { "type": "Gene", "schema_version": "1.5.0", ... }

4. Capsule (The Result)
   { "type": "Capsule", "schema_version": "1.5.0", ... }
`.trim();
```

**Evolver 为什么这样做**: 
- **协议约束**比 LLM 自觉更可靠——LLM 天然喜欢"解释先行"加 markdown 包裹
- 缺少任何对象 = PROTOCOL FAILURE 让验证层可以直接检测格式错误
- 分离的 5 个 JSON 对象让 solidify 阶段可以独立解析每个组件

### 37.3 智能上下文截断 (truncateContext)

**文件**: `prompt.js:93-99`

```javascript
function truncateContext(text, maxLength = 20000) {
  if (!text || text.length <= maxLength) return text || '';
  return text.slice(0, maxLength) + '\n...[TRUNCATED_EXECUTION_CONTEXT]...';
}
```

**实际使用**: 在 `buildGepPrompt` 末尾的 maxChars 截断逻辑：

```javascript
// 如果超过 maxChars（默认 50000），优先截断 Execution Context
const executionContextIndex = basePrompt.indexOf("Context [Execution]:");
if (executionContextIndex > -1) {
    const prefix = basePrompt.slice(0, executionContextIndex + 20);
    // Execution Context 最多 20000 chars（硬上限，防止 token 溢出）
    const EXEC_CONTEXT_CAP = 20000;
    const allowedExecutionLength = Math.min(EXEC_CONTEXT_CAP, Math.max(0, maxChars - prefix.length - 100));
    return prefix + "\n" + currentExecution.slice(0, allowedExecutionLength) + "\n...[TRUNCATED]...";
}
```

**Evolver 为什么这样做**: 
- `Context [Execution]` 是最长的部分，但它是最不重要的（具体的代码上下文）
- Schema 定义、Directives、Anti-Pattern Zone 等必须完整保留
- 20000 chars ≈ 5k tokens，加上其余部分约 10k tokens，是大多数模型的 safe limit

### 37.4 多上下文块注入

**文件**: `buildGepPrompt()` 函数

```javascript
// 信号 + Env Fingerprint（必须保留头部）
${JSON.stringify(optimizedSignals)}
${JSON.stringify(envFingerprint, null, 2)}

// Innovation Catalyst（stagnation 检测时注入）
${innovationBlock}  // 当有 evolution_stagnation_detected 或 stable_success_plateau 时

// 资产预览（Gene + Capsule）
${formattedGenes}
${formattedCapsules}

// Capability Candidates + Hub Matched + Anti-Pattern Zone + Lessons
${capsPreview}
${hubMatchedBlock || '(no hub match)'}
${buildAntiPatternZone(failedCapsules, signals)}
${buildLessonsBlock(hubLessons, signals)}

// 历史 + 叙事 + 原则
${historyBlock}  // 最近 8 个 cycle 的统计
${buildNarrativeBlock()}  // narrativeMemory 摘要
${buildPrinciplesBlock()}  // evolution_principles.md

// Execution Context（可截断）
Context [Execution]:
${executionContext}
```

### 37.5 Local State Awareness — 防止重复操作

**文件**: `prompt.js` 中的 CONSTRAINTS 部分

```javascript
LOCAL STATE AWARENESS (CRITICAL -- PREVENT DUPLICATE ACTIONS):
Before taking any setup, registration, or configuration action, CHECK the
Local State section in the execution context. If a resource already exists
(node registered, secret present, env configured), DO NOT recreate it.
If you cannot find a configuration value, check these locations FIRST:
  1. ~/.evomap/          (node_id, node_secret -- persisted identity)
  2. <repo>/.env         (A2A_NODE_ID, A2A_HUB_URL, A2A_NODE_SECRET)
  3. workspace/memory/   (MEMORY.md, evolution state files)
  4. workspace/skills/   (installed skills)
Redundant registration or re-creation of existing resources = WASTED CYCLE.
```

**Evolver 为什么这样做**: 这是 `localStateAwareness.js` 的消费端——在 prompt 中注入本地状态摘要，让 LLM 在采取"注册/配置"类行动前先检查是否已存在。

### 37.6 宪法伦理约束 (Constitutional Ethics)

**文件**: `prompt.js` 中的 CONSTITUTIONAL ETHICS 部分

```javascript
CONSTITUTIONAL ETHICS (EvoMap Ethics Committee -- Mandatory):
These are non-negotiable rules derived from EvoMap's Constitution.
1. HUMAN WELFARE PRIORITY: Never create tools that could harm humans...
2. CARBON-SILICON SYMBIOSIS: Evolution must serve both human and agent interests...
3. TRANSPARENCY: Never hide, obfuscate, or conceal intent or effects...
4. FAIRNESS: Never create monopolistic strategies that block other agents...
5. SAFETY: Never bypass, disable, or weaken safety mechanisms...
- If a task CONFLICTS with these principles, REFUSE it and set outcome to FAILED
  with reason "ethics_violation: <which principle>".
```

**Evolver 为什么这样做**: 通过 prompt 层面嵌入宪法约束，确保 LLM 在任何情况下都不会绕过安全机制。这比代码层检查更灵活（可被具体上下文 override）。

### 37.7 常见失败模式列表 (COMMON FAILURE PATTERNS)

**文件**: `prompt.js`

```javascript
COMMON FAILURE PATTERNS:
- Blast radius exceeded.
- Omitted Mutation object.
- Merged objects into one JSON.
- Hallucinated "type": "Logic".
- "id": "mut_undefined".
- Missing "trigger_signals".
- Unrunnable validation steps.
- Markdown code blocks wrapping JSON (FORBIDDEN).
```

**Evolver 为什么这样做**: 明确列举 LLM 常见错误格式，减少"LLM 幻觉导致的格式错误"。这是引导式 prompt 的最佳实践。

### 37.8 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| Schema First 约束 | 5 对象模型 + PROTOCOL FAILURE | **高优先级**: BlueCortexCE 的 API 响应应有严格的 schema 验证层 |
| JSON Only 输出 | 禁止 markdown 包裹 | **高优先级**: BlueCortexCE 的任何结构化输出（Summary/Extraction）应强制 JSON |
| 智能截断 | 保留 header/footer，截断中间 | **高优先级**: BlueCortexCE 的 context generate 应有类似策略 |
| Local State Awareness | 在 prompt 中注入"已存在资源"列表 | **高优先级**: BlueCortexCE 的 LLM 调用应注入"已观察的模式"列表 |
| 宪法伦理约束 | prompt 层面的硬约束 | **高优先级**: BlueCortexCE 的任何 LLM 生成应有伦理边界注入 |
| 常见失败模式 | 列举 LLM 格式错误 | **中优先级**: BlueCortexCE 的 prompt 模板应有类似提示 |

---

## 38. strategy.js — 进化策略预设 (v0.8 新增)

**文件**: `src/gep/prompt.js` 内嵌模块 (strategy.js 与 prompt.js 在同一文件)

### 38.1 六种预设策略

```javascript
var STRATEGIES = {
  'balanced': {
    repair: 0.20, optimize: 0.30, innovate: 0.50,
    repairLoopThreshold: 0.50,
    label: 'Balanced',
  },
  'innovate': {
    repair: 0.05, optimize: 0.15, innovate: 0.80,
    repairLoopThreshold: 0.30,
    label: 'Innovation Focus',
  },
  'harden': {
    repair: 0.40, optimize: 0.40, innovate: 0.20,
    repairLoopThreshold: 0.70,
    label: 'Hardening',
  },
  'repair-only': {
    repair: 0.80, optimize: 0.20, innovate: 0.00,
    repairLoopThreshold: 1.00,
    label: 'Repair Only',
  },
  'early-stabilize': {
    repair: 0.60, optimize: 0.25, innovate: 0.15,
    repairLoopThreshold: 0.80,
    label: 'Early Stabilization',
  },
  'steady-state': {
    repair: 0.60, optimize: 0.30, innovate: 0.10,
    repairLoopThreshold: 0.90,
    label: 'Steady State',
  },
};
```

**repairLoopThreshold** 是关键：表示"过去 8 个 cycle 中 repair 占比超过此值时，强制切换到 innovate"。

### 38.2 自适应策略选择 (resolveStrategy)

**文件**: `strategy.js:resolveStrategy()`

```javascript
function resolveStrategy(opts) {
  var signals = opts.signals || [];
  
  // 1. 显式环境变量优先
  var name = String(process.env.EVOLVE_STRATEGY || 'balanced').toLowerCase().trim();
  
  // 2. FORCE_INNOVATION=true → innovate
  if (!process.env.EVOLVE_STRATEGY && forceInnovation) name = 'innovate';
  
  // 3. 自动检测（仅在默认/平衡模式下）
  if (isDefault && !forceInnovation) {
    var cycleCount = _readCycleCount();
    
    // 早期稳定：前 5 个 cycle
    if (cycleCount > 0 && cycleCount <= 5) name = 'early-stabilize';
    
    // 饱和检测
    if (signals.includes('force_steady_state') || signals.includes('evolution_saturation'))
      name = 'steady-state';
  }
  
  return STRATEGIES[name] || STRATEGIES['balanced'];
}
```

**Evolver 为什么这样做**: "fix first, innovate later"——早期阶段优先稳定系统，进化成熟后才探索创新。饱和时切换 steady-state 防止无意义的重复进化。

### 38.3 repairLoopThreshold — 修复循环检测

**文件**: `strategy.js`

```javascript
// 例如 harden 策略：repairLoopThreshold = 0.70
// 意味着：过去 8 个 cycle 中 repair > 70% → 触发"强制创新"逻辑
// 在 selector.js 中使用
```

**Evolver 为什么这样做**: 如果连续多个 cycle 都在做 repair（而不是 innovate），说明系统可能进入了"修复循环"——一直在打补丁但没有进步。repairLoopThreshold 是触发打破循环的开关。

### 38.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 六种策略预设 | repair/optimize/innovate 权重分配 | **中优先级**: BlueCortexCE 可实现"保守/平衡/激进"检索模式 |
| repairLoopThreshold | repair 占比超阈值 → 强制创新 | **高优先级**: BlueCortexCE 应检测"检索模式单一化"并触发探索 |
| 自动策略选择 | cycle 1-5 → early-stabilize | **中优先级**: BlueCortexCE 的新 workspace 可以先用"保守检索" |
| FORCE_INNOVATION | 环境变量直接覆盖 | **低优先级**: BlueCortexCE 作为服务不需要这种 override |

---

## 39. questionGenerator.js — 主动问题生成 (v0.8 新增)

**文件**: `src/gep/prompt.js` 内嵌模块 (与 strategy.js 一起)

### 39.1 设计定位

questionGenerator 从进化上下文（信号、历史事件、会话记录）中提取**主动问题**，通过 A2A Protocol 的 `fetch.questions` 发送到 Hub，Hub 将其创建为 bounty tasks，让其他 Agent 帮助解决。

### 39.2 六类问题策略

```javascript
// Strategy 1: 反复错误（recurring_error）
if (signalSet.has('recurring_error') || signalSet.has('high_failure_ratio')) {
  candidates.push({
    question: 'Recurring error in evolution cycle that auto-repair cannot resolve: ...',
    signals: ['recurring_error', 'auto_repair_failed'],
    priority: 3,
  });
}

// Strategy 2: 能力缺口（capability_gap）
if (signalSet.has('capability_gap')) {
  candidates.push({
    question: 'Capability gap detected: ...',
    signals: ['capability_gap'],
    priority: 2,
  });
}

// Strategy 3: 饱和/停滞（evolution_saturation）
if (signalSet.has('evolution_saturation')) {
  candidates.push({
    question: 'Evolution saturated after exhausting genes: [...]',
    signals: ['evolution_saturation', 'innovation_needed'],
    priority: 1,
  });
}

// Strategy 4: 连续失败 streak >= 4
if (streakCount >= 4) {
  candidates.push({
    question: 'Agent has failed N consecutive evolution cycles',
    signals: ['failure_streak', 'external_help_needed'],
    priority: 3,
  });
}

// Strategy 5: 用户功能请求（user_feature_request）
if (signalSet.has('user_feature_request')) {
  candidates.push({
    question: 'User requested a feature that may benefit from community solutions: ...',
    signals: ['user_feature_request', 'community_solution_sought'],
    priority: 1,
  });
}

// Strategy 6: 性能瓶颈（perf_bottleneck）
if (signalSet.has('perf_bottleneck')) {
  candidates.push({
    question: 'Performance bottleneck detected: ...',
    signals: ['perf_bottleneck', 'optimization_sought'],
    priority: 2,
  });
}
```

**优先级 3 = 最高**，优先发送给 Hub。

### 39.3 去重机制

**文件**: `questionGenerator.js:isDuplicate()`

```javascript
function isDuplicate(question, recentQuestions) {
  // 1. 精确匹配
  if (prev === qLower) return true;
  
  // 2. 模糊匹配：word set Jaccard > 70%
  var qWords = new Set(qLower.split(/\s+/).filter(w => w.length > 2));
  var pWords = new Set(prev.split(/\s+/).filter(w => w.length > 2));
  var overlap = [...qWords].filter(w => pWords.has(w)).length;
  if (overlap / Math.max(qWords.size, pWords.size) > 0.7) return true;
}
```

### 39.4 速率限制

```javascript
const MIN_INTERVAL_MS = 3 * 60 * 60 * 1000;  // 3 小时最少间隔
const MAX_QUESTIONS_PER_CYCLE = 2;            // 每轮最多 2 个问题
```

### 39.5 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 问题优先级体系 | priority 1-3 分级 | **中优先级**: BlueCortexCE 的"无法解答的查询"可以优先级标记 |
| 模糊去重 | word set Jaccard > 70% | **高优先级**: 任何"重复查询检测"都应用 Jaccard 而非精确匹配 |
| 3小时提问间隔 | 防止 Hub 被刷屏 | **中优先级**: BlueCortexCE 的外部 API 调用应有速率保护 |
| 6 类问题策略 | recurring/failure/saturation/gap/feature/perf | **中优先级**: BlueCortexCE 的"失败查询"可分类并寻求外部帮助 |
| 提交到外部网络 | A2A questions → Hub bounty | **低优先级**: BlueCortexCE 无 Hub 生态 |

---

## 40. idleScheduler.js — OMLS 空闲调度 (v0.8 新增)

**文件**: `src/gep/idleScheduler.js` (130 lines)

### 40.1 设计背景

idleScheduler 灵感来自 **OMLS (Organic Machine Learning System)**——在用户空闲时运行资源密集型操作（distillation, reflection），在用户忙碌时只做轻量级信号收集。

### 40.2 平台支持

```javascript
function getSystemIdleSeconds() {
  if (platform === 'win32') {
    // PowerShell + GetLastInputInfo
  } else if (platform === 'darwin') {
    // ioreg -c IOHIDSystem | grep HIDIdleTime
  } else if (platform === 'linux') {
    // xprintidle
  }
  return -1;  // 不支持时返回 -1
}
```

### 40.3 四级强度

```javascript
// IDLE_THRESHOLD_SECONDS = 300 (5分钟)
// DEEP_IDLE_THRESHOLD_SECONDS = 1800 (30分钟)

function determineIntensity(idleSeconds) {
  if (idleSeconds < 0) return 'normal';
  if (idleSeconds >= 1800) return 'deep';       // 深空闲：distillation + reflection + deep_evolve
  if (idleSeconds >= 300) return 'aggressive'; // 空闲：distillation + reflection
  return 'normal';                               // 忙碌：标准循环
}
```

### 40.4 调度建议

```javascript
function getScheduleRecommendation() {
  const intensity = determineIntensity(idleSeconds);
  
  if (intensity === 'aggressive') {
    return {
      sleep_multiplier: 0.5,    // 减少等待，快速响应
      should_distill: true,     // 运行 skill distillation
      should_reflect: true,     // 运行 reflection
      should_deep_evolve: false,
    };
  } else if (intensity === 'deep') {
    return {
      sleep_multiplier: 0.25,   // 几乎无等待
      should_distill: true,
      should_reflect: true,
      should_deep_evolve: true, // 深度进化（未来：RL fine-tuning）
    };
  }
  
  return { sleep_multiplier: 1, should_distill: false, should_reflect: false };
}
```

**Evolver 为什么这样做**: 用户不在时运行 heavy 任务是节能且不打扰用户的最佳策略。distillation 和 reflection 是 compute-intensive 但不需要用户交互的操作。

### 40.5 状态持久化

```javascript
function readScheduleState() {
  const statePath = path.join(getEvolutionDir(), 'idle_schedule_state.json');
  // { last_check, last_idle_seconds, last_intensity }
}
```

### 40.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 系统空闲检测 | ioreg/xprintidle/GetLastInputInfo | **中优先级**: BlueCortexCE 的 cron 可在用户空闲时做 heavy 分析 |
| 四级强度 | idle → aggressive → deep | **高优先级**: BlueCortexCE 可根据用户活动状态调整后台任务频率 |
| sleep_multiplier | 空闲时 0.5x 或 0.25x | **中优先级**: BlueCortexCE 的 periodic check 可动态调整间隔 |
| should_distill | 空闲时才运行 distillation | **高优先级**: BlueCortexCE 的 Summary 提炼可以在空闲时触发 |
| OMLS 设计 | 有机机器学习（用户空闲时学习） | **中优先级**: BlueCortexCE 的"深度分析"应在用户空闲时运行 |

---

## 41. gitOps.js — Git 操作与回滚 (v0.8 新增)

**文件**: `src/gep/gitOps.js` (210 lines)

### 41.1 设计定位

gitOps.js 从 `solidify.js` 中提取了所有 Git 相关操作，是 Evolver 的**版本控制层**——负责变更追踪、rollback、和 diff 捕获。

### 41.2 变更文件追踪

```javascript
function gitListChangedFiles({ repoRoot }) {
  const files = new Set();
  // git diff --name-only (unstaged)
  // git diff --cached --name-only (staged)
  // git ls-files --others --exclude-standard (untracked)
  return Array.from(files);
}
```

### 41.3 Diff 快照捕获

```javascript
const DIFF_SNAPSHOT_MAX_CHARS = 8000;

function captureDiffSnapshot(repoRoot) {
  const parts = [];
  const unstaged = tryRunCmd('git diff', { cwd: repoRoot });
  if (unstaged.ok && unstaged.out) parts.push(unstaged.out);
  const staged = tryRunCmd('git diff --cached', { cwd: repoRoot });
  if (staged.ok && staged.out) parts.push(staged.out);
  let combined = parts.join('\n');
  if (combined.length > DIFF_SNAPSHOT_MAX_CHARS) {
    combined = combined.slice(0, DIFF_SNAPSHOT_MAX_CHARS) + '\n... [TRUNCATED]';
  }
  return combined;
}
```

**Evolver 为什么这样做**: FailedCapsule 在 rollback 前先捕获 diff_snapshot，确保失败信息不丢失。

### 41.4 关键文件保护

**文件**: `gitOps.js:CRITICAL_PROTECTED_PREFIXES` 和 `CRITICAL_PROTECTED_FILES`

```javascript
const CRITICAL_PROTECTED_PREFIXES = [
  'skills/feishu-evolver-wrapper/',
  'skills/feishu-common/',
  'skills/feishu-post/',
  // ... 10 个关键 skills
];

const CRITICAL_PROTECTED_FILES = [
  'MEMORY.md', 'SOUL.md', 'IDENTITY.md', 'AGENTS.md', 'USER.md',
  'HEARTBEAT.md', 'RECENT_EVENTS.md', 'TOOLS.md', 'TROUBLESHOOTING.md',
  'openclaw.json', '.env', 'package.json',
];
```

**rollbackNewUntrackedFiles** 会跳过这些文件：

```javascript
if (isCriticalProtectedPath(safeRel)) {
  skipped.push(safeRel);
  continue;  // 不删除
}
```

### 41.5 Rollback 模式

```javascript
function rollbackTracked(repoRoot) {
  const mode = String(process.env.EVOLVER_ROLLBACK_MODE || 'hard').toLowerCase();
  
  if (mode === 'none') {
    // 不回滚
  } else if (mode === 'stash') {
    // git stash push -m "evolver-rollback-<timestamp>"
  } else {
    // git restore --staged --worktree . && git reset --hard
  }
}
```

### 41.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 关键文件保护 | MEMORY.md/SOUL.md/IDENTITY.md 不可删除 | **高优先级**: BlueCortexCE 应有"不可删除的系统文件"白名单 |
| Diff 快照 | 8000 chars 上限截断 | **高优先级**: BlueCortexCE 的"失败记录"应保存 diff context |
| Rollback 模式 | none/stash/hard 三种 | **中优先级**: BlueCortexCE 的 destructive operation 应有 rollback 策略 |
| gitListChangedFiles | 分离 staged/unstaged/untracked | **低优先级**: BlueCortexCE 不直接操作 git |

---

## 42. localStateAwareness.js — 本地状态感知 (v0.8 新增)

**文件**: `src/gep/localStateAwareness.js` (185 lines)

### 42.1 设计定位

localStateAwareness 是 Evolver 的**自省层**——在采取任何"外部行动"（注册、配置、创建）前，先检查本地是否已存在对应状态，避免重复操作。

### 42.2 五大状态域

```javascript
function captureLocalState() {
  return {
    // 1. Node Identity: A2A node 注册状态
    'Node ID: ... (REGISTERED -- do NOT re-register)',
    'Node Secret: PRESENT (authenticated)',
    
    // 2. Environment Config: .env + 环境变量
    '- Env configured: A2A_NODE_ID, A2A_HUB_URL, ...',
    '- .env file: EXISTS at ...',
    
    // 3. Evolution State: cycle count + last run + personality
    '- Evolution cycles completed: N',
    '- Last evolution run: Ns ago',
    
    // 4. Memory & Knowledge: memory dir + graph + narrative
    '- MEMORY.md: N bytes',
    '- Memory graph: N bytes',
    
    // 5. Skills: installed skills count
    '- Installed skills: N (at ...)',
  };
}
```

### 42.3 状态文件读取（安全防护）

```javascript
function _readJsonSafe(filePath) {
  try {
    if (!fs.existsSync(filePath)) return null;
    const raw = fs.readFileSync(filePath, 'utf8').trim();
    if (!raw) return null;
    return JSON.parse(raw);
  } catch (_) {
    return null;  // 非致命：读取失败返回 null 而非抛出
  }
}
```

**Evolver 为什么这样做**: 状态文件可能损坏（无效 JSON），使用 `_readJsonSafe` 确保一个文件读取失败不会阻断整个 evolution cycle。

### 42.4 幂等保护机制

```javascript
// 在 prompt.js 的 CONSTRAINTS 部分注入：
'Node ID: ... (REGISTERED -- do NOT re-register)'
'Node Secret: PRESENT (authenticated -- do NOT request new secret)'
```

**Evolver 为什么这样做**: A2A Node 的注册操作是幂等的（重复 hello 无害但不必要）。通过状态感知告诉 LLM"已注册，不要重复注册"。

### 42.5 路径清单

```javascript
function captureLocalStatePaths() {
  return {
    nodeIdFile: path.join(os.homedir(), '.evomap', 'node_id'),
    nodeSecretFile: path.join(os.homedir(), '.evomap', 'node_secret'),
    envFile: path.join(getRepoRoot(), '.env'),
    memoryDir: getMemoryDir(),
    evolutionDir: getEvolutionDir(),
    skillsDir: getSkillsDir(),
  };
}
```

### 42.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 五大状态域 | identity/config/evolution/memory/skills | **高优先级**: BlueCortexCE 应在 context generate 时注入"当前系统状态" |
| 自省提示 | "REGISTERED -- do NOT re-register" | **高优先级**: BlueCortexCE 的 LLM 调用应明确告知"已有什么" |
| _readJsonSafe | 文件读取非致命 | **高优先级**: BlueCortexCE 的所有文件读取应有 try/catch，返回 null 而非报错 |
| 幂等保护 | 重复注册无害但不必要 | **高优先级**: BlueCortexCE 的"创建操作"应先检查是否已存在 |
| 路径清单 | 统一的路径获取函数 | **中优先级**: BlueCortexCE 应有统一的路径解析工具函数 |

---

## 43. policyCheck.js — 约束检查与验证命令安全（v0.9 深度补充）

**文件**: `src/gep/policyCheck.js` (550 lines)

> **⚠️ 本节是对第 13 节 policyCheck.js 的深度补充，聚焦第 13 节未覆盖的细节。**

### 43.1 验证命令白名单的安全模型（isValidationCommandAllowed）

**文件**: `policyCheck.js:436-450`

```javascript
const VALIDATION_ALLOWED_PREFIXES = ['node ', 'npm ', 'npx '];

function isValidationCommandAllowed(cmd) {
  const c = String(cmd || '').trim();
  if (!c) return false;
  // 1. 必须以 node/npm/npx 开头
  if (!VALIDATION_ALLOWED_PREFIXES.some(p => c.startsWith(p))) return false;
  // 2. 禁止反引号和 $() — 防止命令注入
  if (/`|\$\(/.test(c)) return false;
  // 3. 去除引号后检查 shell 操作符
  const stripped = c.replace(/"[^"]*"/g, '').replace(/'[^']*'/g, '');
  if (/[;&|><]/.test(stripped)) return false;
  // 4. 禁止危险的 node 选项 — 防止 eval 注入
  if (/^node\s+(-e|--eval|--print|-p)\b/.test(c)) return false;
  return true;
}
```

**四层防御体系**：
1. **前缀白名单**：`node`/`npm`/`npx` 三选一
2. **反引号阻断**：禁止 `` `command` `` 和 `$(command)` 语法
3. **操作符过滤**：去除引号内容后，检查 `; & | > <` 等 shell 操作符
4. **危险选项禁用**：`node -e` / `node --eval` / `node -p` / `node --print` 被禁止

**Evolver 为什么这样做**：Gene 的 `validation` 字段是用户可控的输入。如果不严格限制，恶意 Gene 可以通过 `validation: ["node -e 'require(\"fs\").readFileSync(\"/etc/passwd\")'"]` 等命令执行任意代码。

### 43.2 失败模式分类（classifyFailureMode）

**文件**: `policyCheck.js:520-545`

```javascript
function classifyFailureMode(opts) {
  const { constraintViolations, protocolViolations, validation, canary } = opts;

  // HARD: 硬限制突破 → 不可重试
  if (constraintViolations.some(v =>
    /HARD CAP BREACH|CRITICAL_FILE_|critical_path_modified|forbidden_path touched|ethics:/i.test(v)
  )) {
    return { mode: 'hard', reasonClass: 'constraint_destructive', retryable: false };
  }

  // HARD: 协议违规 → 不可重试
  if (protocolViolations.length > 0) {
    return { mode: 'hard', reasonClass: 'protocol', retryable: false };
  }

  // HARD: Canary 失败 → 不可重试（程序入口损坏）
  if (canary && !canary.ok && !canary.skipped) {
    return { mode: 'hard', reasonClass: 'canary', retryable: false };
  }

  // HARD: 约束违规 → 不可重试
  if (constraintViolations.length > 0) {
    return { mode: 'hard', reasonClass: 'constraint', retryable: false };
  }

  // SOFT: 验证失败 → 可重试（可能临时性问题）
  if (validation && validation.ok === false) {
    return { mode: 'soft', reasonClass: 'validation', retryable: true };
  }

  return { mode: 'soft', reasonClass: 'unknown', retryable: true };
}
```

**失败模式决策树**：

```
失败原因
  ├─ HARD CAP / CRITICAL_FILE / forbidden_path / ethics:
  │   → mode=hard, retryable=false  (不可重试)
  ├─ 协议违规:
  │   → mode=hard, retryable=false  (不可重试)
  ├─ Canary 失败:
  │   → mode=hard, retryable=false  (不可重试)
  └─ 仅验证失败:
      → mode=soft, retryable=true   (可重试)
```

**Evolver 为什么这样做**：
- `hard` 失败说明系统存在根本性问题（如关键文件被删除、安全机制被绕过），重试无意义
- `soft` 失败（如测试临时失败）才值得重试
- `retryable` 决定是否进入重试循环（`SOLIDIFY_RETRY_INTERVAL_MS` 间隔）

### 43.3 破坏性变更检测（detectDestructiveChanges）

**文件**: `policyCheck.js:405-430`

```javascript
function detectDestructiveChanges({ repoRoot, changedFiles, baselineUntracked }) {
  const violations = [];
  const baselineSet = new Set(baselineUntracked.map(normalizeRelPath));

  for (const rel of changedFiles) {
    const norm = normalizeRelPath(rel);
    if (!isCriticalProtectedPath(norm)) continue;

    const abs = path.join(repoRoot, norm);
    const normAbs = path.resolve(abs);

    // CRITICAL_FILE_DELETED: 关键文件从 git 中消失
    if (!baselineSet.has(norm)) {
      if (!fs.existsSync(normAbs)) {
        violations.push(`CRITICAL_FILE_DELETED: ${norm}`);
      } else if (stat.isFile() && stat.size === 0) {
        // CRITICAL_FILE_EMPTIED: 关键文件被清空
        violations.push(`CRITICAL_FILE_EMPTIED: ${norm}`);
      }
    }
  }
  return violations;
}
```

**Evolver 为什么这样做**：关键系统文件（MEMORY.md、SOUL.md、openclaw.json）被删除或清空是严重的破坏性变更。即使其他验证通过，这类变更也必须阻止。

### 43.4 ReDoS 防护（MAX_REGEX_PATTERN_LEN）

**文件**: `policyCheck.js:86-88`

```javascript
const MAX_REGEX_PATTERN_LEN = 200;  // 防止 ReDoS 攻击
```

在 `matchAnyRegex` 中：

```javascript
function matchAnyRegex(rel, regexList) {
  for (const raw of regexList) {
    try {
      if (s.length > MAX_REGEX_PATTERN_LEN) continue;  // 跳过超长正则
      if (new RegExp(s, 'i').test(rel)) return true;
    } catch (_) { /* invalid pattern 静默跳过 */ }
  }
  return false;
}
```

**Evolver 为什么这样做**：如果 `openclaw.json` 中的 `excludeRegex` 包含恶意构造的正则（如 `(a+)+$`），会触发 ReDoS 导致 CPU 100%。长度限制是简单的防护层。

### 43.5 验证重试机制（runValidations）

**文件**: `policyCheck.js:455-490`

```javascript
function sleepSync(ms) {
  // 使用 Atomics.wait 实现同步睡眠（不阻塞事件循环）
  try {
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, t);
  } catch (_) {
    // busy wait fallback
  }
}

function runValidations(gene, opts = {}) {
  var maxRetries = MAX_VALIDATION_RETRIES;
  var attempt = 0;
  var result;
  while (attempt <= maxRetries) {
    result = runValidationsOnce(gene, opts);
    if (result.ok) return result;
    
    // 被安全策略阻止 → 不重试
    if (blocked) break;
    
    attempt++;
    if (attempt <= maxRetries) {
      sleepSync(SOLIDIFY_RETRY_INTERVAL_MS);  // 等待后重试
    }
  }
  return result;
}
```

**关键设计**：
- `Atomics.wait` vs busy wait：优先使用 `Atomics.wait`（不消耗 CPU），fallback 到 busy wait
- `blocked` 命令（被 `isValidationCommandAllowed` 拒绝）**不重试**——这是配置错误，重试无意义

### 43.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 验证命令白名单 | 四层防御（前缀/反引号/操作符/危险选项） | **高优先级**: BlueCortexCE 如果支持"自定义验证命令"，必须严格白名单化 |
| 失败模式分类 | hard=不可重试，soft=可重试 | **高优先级**: BlueCortexCE 的任务重试应有明确的失败分类 |
| 破坏性变更检测 | CRITICAL_FILE_DELETED/EMPTIED | **高优先级**: BlueCortexCE 应监控"关键记忆文件被删除"模式 |
| ReDoS 防护 | MAX_REGEX_PATTERN_LEN=200 | **高优先级**: BlueCortexCE 的正则表达式应有长度限制 |
| Atomics.wait 睡眠 | 不阻塞事件循环的同步等待 | **中优先级**: BlueCortexCE 的重试应避免阻塞 Node.js 事件循环 |
| 关键文件路径 | isCriticalProtectedPath() | **高优先级**: BlueCortexCE 应有"不可删除文件"保护机制 |

---

