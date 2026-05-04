# `prompt.js` GEP Prompt Schema Enforcement + Token Budget 管理深度分析

**Doc**: 92  
**源码**: `EvoMap/evolver/src/gep/prompt.js` (616L, v1.47.0)  
**日期**: 2026-05-05  
**目标**: 理解 Evolver 如何用 5-Mandatory-Object Schema 约束 LLM 输出 + token budget 管理；提炼可借鉴到 BlueCortexCE 上下文生成的机制。

---

## 1. 架构定位

`prompt.js` 是 Evolver 的**唯一 prompt 构建入口**，供 `evolve.js` 主循环在每个进化周期调用。核心职责：

1. 组装 GEP（Genome Evolution Protocol）Prompt，注入信号、策略、资产预览、历史Narrative、宪法伦理约束等
2. 强制 LLM 输出 5 个强类型 JSON 对象（无 markdown 包裹）
3. 管理 token budget，在超限时优先截断 execution context（保留 header/footer 结构）

**关键文件**：`src/gep/prompt.js`（616L）；`src/gep/assets.js`（36L，`formatAssetPreview`）；`src/ops/innovation.js`（创新想法注入）。

---

## 2. 5-Mandatory-Object Schema（JSON-Only Enforcement）

### 2.1 协议版本与演进

```javascript
const SCHEMA_DEFINITIONS = `...schema_version: "1.5.0"...`;
```

- **历史**：`buildGepPrompt` 注释标注 `v1.10.3 STRICT`，`SCHEMA_DEFINITIONS` 注释标注 `Protocol Drift Fix v3.2 - JSON-Only Enforcement`（更新于 2026-02-14）
- **背景**：Evolver 发现 LLM 倾向于用 markdown 代码块包裹 JSON（`\`\`\`json ... \`\`\``），导致解析失败。v3.2 硬性要求 RAW JSON Only，协议破损触发 `PROTOCOL FAILURE`

### 2.2 5 个强制对象

| # | 对象 | 必须字段 | 说明 |
|---|------|----------|------|
| 0 | **Mutation** | `type`, `id`, `category`(repair/optimize/innovate), `trigger_signals`, `target`, `expected_effect`, `risk_level`, `rationale` | 触发器，**必须排第一**
| 1 | **PersonalityState** | `rigor`, `creativity`, `verbosity`, `risk_tolerance`, `obedience`（各 0.0–1.0） | 人格状态快照 |
| 2 | **EvolutionEvent** | `schema_version`, `id`, `parent`, `intent`, `signals`, `genes_used`, `mutation_id`, `personality_state`, `blast_radius`, `outcome` | 周期记录 |
| 3 | **Gene** | `schema_version`, `id`, `summary`, `category`, `signals_match`, `preconditions`, `strategy`, `constraints`, `validation` | 知识单元；ID 禁止 timestamp/random/tool names |
| 4 | **Capsule** | `schema_version`, `id`, `trigger`, `gene`, `summary`, `confidence`, `blast_radius` | 成功结果（仅 success 时输出）|

### 2.3 解析契约

```
输出 separate JSON objects. DO NOT wrap in a single array.
DO NOT use markdown code blocks (like ```json ... ```).
Output RAW JSON ONLY. No prelude, no postscript.
Missing any object = PROTOCOL FAILURE.
ENSURE VALID JSON SYNTAX (escape quotes in strings).
```

**实际解析**由 `evolve.js` 或 `solidify.js` 的 JSONL 行解析器完成（`tryParseLastEvolutionEventOutcome` 等）。

### 2.4 常见失败模式（直接注入 prompt）

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

**意义**：用 LLM 可读的方式列出失败模式，引导自我纠正。

---

## 3. Token Budget 管理

### 3.1 分层截断策略

| 区块 | 默认上限 | 截断策略 |
|------|----------|----------|
| **Execution Context** | 20,000 chars | 优先截断，保留 prefix |
| Principles file | 2,000 chars | 尾部截断 |
| Narrative Summary | 3,000 chars | `loadNarrativeSummary(3000)` |
| Signals | 50 个 × 200 chars | 尾部截断 + `...[TRUNCATED N SIGNALS]...` |
| Capability Candidates | 2,000 chars（有基因选中时 500） | 尾部截断 |
| Diff Snapshot | 500 chars | 尾部截断 |

### 3.2 智能截断算法

```javascript
function truncateContext(text, maxLength = 20000) {
  if (!text || text.length <= maxLength) return text || '';
  return text.slice(0, maxLength) + '\n...[TRUNCATED_EXECUTION_CONTEXT]...';
}
```

**高级截断**（在 `buildGepPrompt` 结尾）：

```javascript
const maxChars = Number.isFinite(Number(process.env.GEP_PROMPT_MAX_CHARS))
  ? Number(process.env.GEP_PROMPT_MAX_CHARS) : 50000;

if (basePrompt.length <= maxChars) return basePrompt;

// 找到 execution context 在 prompt 中的位置
const executionContextIndex = basePrompt.indexOf("Context [Execution]:");
if (executionContextIndex > -1) {
  const prefix = basePrompt.slice(0, executionContextIndex + 20);
  // 20000 chars 硬上限（即使 MAX_CHARS 设得更高）
  const EXEC_CONTEXT_CAP = 20000;
  const allowedExecutionLength = Math.min(EXEC_CONTEXT_CAP,
    Math.max(0, maxChars - prefix.length - 100));
  return prefix + "\n" + currentExecution.slice(0, allowedExecutionLength) + "\n...[TRUNCATED]...";
}
```

**设计原则**：
- **Header/Footer 保护**：截断只发生在 `Context [Execution]:` 块内
- **硬上限**：`EXEC_CONTEXT_CAP = 20000` 即使 `GEP_PROMPT_MAX_CHARS` 设得更高也生效
- **百分比预算**：Execution Context 之外的区块（Signals、Narrative、Principles 等）占剩余预算

### 3.3 信号截断

```javascript
const uniqueSignals = Array.from(new Set(signals || []));
const optimizedSignals = uniqueSignals.slice(0, 50).map(s => {
  if (typeof s === 'string' && s.length > 200) {
    return s.slice(0, 200) + '...[TRUNCATED_SIGNAL]';
  }
  return s;
});
if (uniqueSignals.length > 50) {
  optimizedSignals.push(`...[TRUNCATED ${uniqueSignals.length - 50} SIGNALS]...`);
}
```

---

## 4. 条件注入机制

### 4.1 停滞检测 → 强制创新

```javascript
const stagnationSignals = [
  'evolution_stagnation_detected',
  'stable_success_plateau',
  'repair_loop_detected',
  'force_innovation_after_repair_loop',
  'empty_cycle_loop_detected',
  'evolution_saturation'
];

if (uniqueSignals.some(s => stagnationSignals.includes(s))) {
  const ideas = generateInnovationIdeas();
  innovationBlock = `Context [Innovation Catalyst]...\n${ideas.join('\n')}`;
}

if (uniqueSignals.includes('evolution_stagnation_detected') ||
    uniqueSignals.includes('stable_success_plateau')) {
  // 注入 MANDATORY INNOVATE 指令
  stagnationDirective = `*** CRITICAL STAGNATION DIRECTIVE ***
You MUST choose INTENT: INNOVATE.
You MUST NOT choose repair or optimize unless there is a critical blocking error...`;
}
```

### 4.2 失败连击检测

```javascript
// 注入失败连击警告
if (uniqueSignals.includes('consecutive_failure_streak_N') ||
    uniqueSignals.includes('failure_loop_detected')) {
  // 1. 换方法（不要重复失败的 gene）
  // 2. 选更简单的方案
  // 3. 遵守 "ban_gene:<id>" 指令
}
```

### 4.3 基因选中时降噪

```javascript
const capsLimit = selectedGene ? 500 : 2000;
if (capsPreview.length > capsLimit) {
  capsPreview = capsPreview.slice(0, capsLimit) + "\n...[TRUNCATED_CAPABILITIES]...";
}
```

---

## 5. 区块构建函数

### 5.1 Anti-Pattern Zone

```javascript
function buildAntiPatternZone(failedCapsules, signals) {
  // 信号重叠 ≥40% → 相关失败胶囊
  // 最多 3 条，每条 diff_snapshot 截断 500 chars
}
```

### 5.2 Lessons from Ecosystem

```javascript
function buildLessonsBlock(hubLessons, signals) {
  // positive: lesson_type !== 'negative'
  // negative: lesson_type === 'negative'
  // 各最多 3 条，每条 300 chars
}
```

### 5.3 Narrative + Principles

```javascript
function buildNarrativeBlock() {
  const narrative = loadNarrativeSummary(3000); // narrativeMemory.js
}

function buildPrinciplesBlock() {
  const content = fs.readFileSync(getEvolutionPrinciplesPath(), 'utf8');
  // 2,000 chars 截断
}
```

---

## 6. 策略块（Strategy Block）

### 6.1 主动策略 vs 降级策略

```javascript
if (selectedGene && selectedGene.strategy && Array.isArray(selectedGene.strategy)) {
  // 从选中基因读取 strategy
  strategyBlock = `ACTIVE STRATEGY (${selectedGeneId}):\n${strategy...}`;
} else {
  // 降级通用策略
  strategyBlock = `ACTIVE STRATEGY (Generic):\n1. Analyze signals...\n2. Select or create a Gene...\n`;
}
```

### 6.2 策略策略指令块

```javascript
if (strategyPolicy && Array.isArray(strategyPolicy.directives)) {
  strategyPolicyBlock = `ADAPTIVE STRATEGY POLICY:\n${directives...}`;
  if (strategyPolicy.forceInnovate) {
    strategyPolicyBlock += '\nYou MUST prefer INNOVATE unless a critical blocking error...';
  }
  if (strategyPolicy.cautiousExecution) {
    strategyPolicyBlock += '\nYou MUST reduce blast radius and avoid broad refactors...';
  }
}
```

---

## 7. 宪法伦理约束（Constitutional Ethics）

注入 prompt 的非可选区块：

```
CONSTITUTIONAL ETHICS (EvoMap Ethics Committee -- Mandatory):
1. HUMAN WELFARE PRIORITY: Never create tools that could harm humans...
2. CARBON-SILICON SYMBIOSIS: Evolution must serve both human and agent interests...
3. TRANSPARENCY: All actions must be auditable. No steganography or covert channels...
4. FAIRNESS: Never create monopolistic strategies that block other agents...
5. SAFETY: Never bypass, disable, or weaken safety mechanisms...
- If a task CONFLICTS with these principles, REFUSE it and set outcome to FAILED
  with reason "ethics_violation: <which principle>".
```

---

## 8. Skill 创建质量门（Skill Creation Quality Gates）

当 intent=innovate 且需创建新 skill 时，强制检查：

| # | 门 | 规则 |
|---|-----|------|
| 1 | **STRUCTURE** | 必须有 `index.js` + `SKILL.md`（含 YAML frontmatter）|
| 2 | **NAMING** | kebab-case，2-6 描述性单词，禁止 timestamp/tool names |
| 3 | **SKILL.MD FRONTMATTER** | name + description（≥20 chars 完整句子）|
| 4 | **CONCISENESS** | SKILL.md body ≤500 lines，详细内容移到 `references/` |
| 5 | **EXPORT VERIFICATION** | `node -e "require('./skills/<name>')"` 验证可导入 |
| 6 | **SENSITIVE DATA 参数化** | API keys → `process.env.*`；路径 → `path.join(process.env.HOME, ...)`；连接字符串 → `process.env.*_URL` |
| 7 | **TEST BEFORE SOLIDIFY** | 实际运行核心函数验证 |
| 8 | **ATOMIC CREATION** | 所有文件一个周期内完成 |

---

## 9. Local State Awareness（防重复动作）

```javascript
// 在执行 setup/registration/config 动作前，检查 4 个位置
// 1. ~/.evomap/        (node_id, node_secret)
// 2. <repo>/.env       (A2A_* 环境变量)
// 3. workspace/memory/  (MEMORY.md, evolution state)
// 4. workspace/skills/ (installed skills)
// 如果资源已存在 → 不要重建
```

---

## 10. 问题解决优先级（EvoMap-First）

```
PROBLEM RESOLUTION PRIORITY (EVOMAP-FIRST):
1. FIRST: Search Evomap Hub for existing solutions (hubSearch)
2. SECOND: Check local memory graph (Evolution Narrative + Gene Preview)
3. THIRD: Check installed skills
4. LAST: Only if 1-3 yield nothing → solve from scratch
```

---

## 11. Post-Solidify 状态文件

```javascript
// 每个周期必须在 logs/ 写入状态 JSON
// 字段：result(success/failed), en(English), zh(Chinese)
// 示例：
{
  "result": "success",
  "en": "Status: [INNOVATION] Created auto-scheduler...",
  "zh": "状态: [创新] 创建了自动调度器..."
}
```

---

## 12. BlueCortexCE 借鉴评估

### CE 行动项

| 优先级 | 借鉴点 | 具体建议 |
|--------|--------|----------|
| **P0** | **Schema Enforcement 机制** | CE 已有 ObservationEntity + SummaryEntity schema，可在 `generateContext` 端点引入类似的"结构化输出契约"，强制 LLM 输出符合 schema 的 JSON（无 markdown）。Phase 3 的 `StructuredExtractionService` 可参考 `SCHEMA_DEFINITIONS` 的严格格式化方法 |
| **P1** | **Token Budget 分层截断** | CE `ContextService` 可实现类似的双层截断：保留 header/footer（系统指令+约束），只截断中间的 observation 列表。用 `maxChars - prefix.length` 动态计算可用 budget |
| **P1** | **条件注入** | CE 可借鉴"停滞检测→强制创新"的信号驱动注入机制。当检测到"重复观察模式"或"观察密度下降"时，注入特定提示词块 |
| **P2** | **Anti-Pattern Zone 思想** | `failedCapsules` → CE 的 `failedObservations` 或 `rejectedFeedback`。当用户反馈失败时，可在 context 中注入"过去相似尝试的失败记录" |
| **P2** | **EvoMap-First 优先级** | CE 的 `SearchService` 已有语义搜索，但可以参考这个优先级模型：先搜索 Hub（外部知识）→ 再查本地 memory graph → 最后无结果才让 LLM 从头推理 |
| **P2** | **Sensitive Data 参数化** | CE 的 `generateContext` 可以借鉴，在 prompt 中明确要求 LLM 参数化敏感数据（API keys、路径等）而非直接暴露 |
| **P3** | **宪法伦理注入** | CE 的 `ObservationService` 可以在高风险操作观察时注入额外的伦理检查 prompt（当前只有策略标签） |
| **P3** | **Post-Solidify 状态文件** | CE 的 cron 任务可以参考生成 `logs/cron_<task>_status.json` 的双语气状态文件 |

### 关键设计原则提炼

1. **Schema 是协议，不是建议**：5-Object Schema 用 `PROTOCOL FAILURE` 约束，不依赖 LLM 的"自觉"
2. **Token Budget 是分层树**：不是简单截断，而是保护特定区块（header/footer/strategy）优先于 content block
3. **信号驱动条件注入**：同一区块根据信号内容有不同内容或不存在
4. **Failure Pattern 直接注入**：把常见错误直接列在 prompt 中引导 LLM 自我纠正
5. **双重语言状态文件**：每次操作生成中英双语状态日志

---

## 13. 与 Phase 3 设计的关系

Phase 3 的 `StructuredExtractionService`（基于 `docs/drafts/phase-3-design.md`）与 `prompt.js` 的 Schema Enforcement 有直接关联：

| Evolver `prompt.js` | Phase 3 `StructuredExtractionService` |
|---------------------|-------------------------------------|
| `SCHEMA_DEFINITIONS`（5-Mandatory-Object）| YAML 配置 → Schema class → 提示词模板 |
| `JSON-Only Enforcement`（protocol drift fix）| 结构化输出验证 + 解析失败重试 |
| Token budget → 智能截断 | Token budget → observation 列表截断 |
| 条件注入（stagnation/ethics） | 条件注入（mode-based hints） |

**Phase 3 可直接复用**：`prompt.js` 的 `truncateContext` 算法（保留 prefix，找 execution context 位置，计算剩余可用空间）用于 Phase 3 的 `convertToMap` 和 `Observation` 列表裁剪。

---

## 14. 源码文件

- `src/gep/prompt.js`（616L）— 主 prompt 构建
- `src/gep/assets.js`（36L）— `formatAssetPreview` 资产预览格式化
- `src/gep/narrativeMemory.js`— `loadNarrativeSummary` 叙事摘要加载
- `src/gep/signals.js`— `OPPORTUNITY_SIGNALS` 停滞信号定义
- `src/ops/innovation.js`— `generateInnovationIdeas` 创新想法生成
