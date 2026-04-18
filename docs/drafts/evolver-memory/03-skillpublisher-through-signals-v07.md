<!-- part 3/8: auto-split from evolver-memory-analysis.md — see index.md -->

## 24. skillPublisher.js — 技能发布机制（v0.5 新增）

**文件**: `src/gep/skillPublisher.js` (307 lines)

### 24.1 核心设计：Gene → SKILL.md 转换

skillPublisher 负责将 Evolver 的 Gene 资产转换为标准化的 Skill 文档，发布到 Hub 的技能市场。

**SKILL.md 格式结构**（市场级质量）：

```markdown
---
name: Retry With Backoff
description: AI agent skill for implementing retry logic with exponential backoff.
---

# Retry With Backoff

[自动生成的技能描述]

## When to Use
- When your project encounters: `log_error`, `errsig:...`

## Trigger Signals
- `log_error`
- `errsig:...`

## Preconditions
- signals_key == xxx

## Strategy
1. **Verify** -- [step description]
2. **Run** -- `npm test`

## Constraints
- Max files per invocation: 12
- Forbidden paths: `.git`, `node_modules`

## Validation
```bash
node scripts/validate-modules.js
```

## Metadata
- Category: `repair`
- Schema version: `1.6.0`
- Distilled from: 5 successful capsules
```

### 24.2 技能名称归一化

**文件**: `skillPublisher.js:15-30`

```javascript
function sanitizeSkillName(rawName) {
  // gene_distilled_xxx → xxx
  // gene_repair_distilled_xxx → xxx
  // 去除所有 10+ 位数字的时间戳
  name = name.replace(/-?\d{10,}-?/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
  
  // 过滤纯数字、工具名、IDE 名
  if (/^\d{8,}/.test(name)) return null;
  if (/^(cursor|vscode|vim|emacs|windsurf|copilot|cline|codex)[-]?\d*$/i.test(name)) return null;
}
```

**Evolver 为什么这样做**: Hub 技能市场需要人类可读的技能名，且需要过滤掉自动生成的垃圾名称。

### 24.3 发布流程

**文件**: `skillPublisher.js:231-307`

```javascript
async function publishSkillToHub(gene, opts) {
  const content = geneToSkillMd(gene);  // 转 SKILL.md
  const skillId = 'skill_' + derivedName;
  
  const body = {
    protocol: 'gep-a2a',
    message_type: 'publish_skill',
    payload: {
      skill_id: skillId,
      name: displayName,
      content: content,  // SKILL.md 全文
      tags: gene.signals_match,
    }
  };
  
  const res = await fetch(hubUrl + '/a2a/skills', {
    method: 'POST',
    headers: buildHubHeaders(),
    body: JSON.stringify(msg),
  });
}
```

### 24.4 表观遗传信号的导出

**文件**: `skillPublisher.js:110-160`

Gene 的表观遗传标记（`epigenetic_marks`）不会被发布到 Hub——因为它们是环境相关的本地知识。但 `signals_match` 会被保留并消毒处理。

### 24.5 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| SKILL.md 标准化格式 | Gene → 标准化 Markdown | **高优先级**: BlueCortexCE 的 Extraction 结果应支持导出为标准 Skill 格式 |
| 技能名归一化 | 去除时间戳 + 过滤工具名 | **中优先级**: BlueCortexCE 的"能力沉淀"应有标准化命名规则 |
| 发布元数据 | signals_match + category + distillation_count | **中优先级**: BlueCortexCE 的 Summary 应包含可发布的元数据 |
| 策略步骤格式化 | 动词提取 + 标题化展示 | **低优先级**: BlueCortexCE 的 Summary 策略链可采用类似格式化 |

---

## 25. executionTrace.js — 执行轨迹脱敏（v0.5 新增）

**文件**: `src/gep/executionTrace.js` (202 lines)

### 25.1 设计原则

executionTrace 在 `solidify` 阶段构建，用于跨 Agent 经验共享。**核心原则是脱敏**：

- 文件路径 → 仅保留 basename + extension（`src/utils/retry.js` → `retry.js`）
- 代码内容 → 从不发送，仅发送统计指标（行数、文件数）
- 错误信息 → 仅保留错误类型签名（`TypeError: x is not a function` → `TypeError`）
- 环境变量、密钥、用户数据 → 彻底剥离

### 25.2 Trace 级别

**文件**: `executionTrace.js:12-20`

```javascript
const TRACE_LEVELS = { none: 0, minimal: 1, standard: 2 };

function getTraceLevel() {
  return String(process.env.EVOLVER_TRACE_LEVEL || 'minimal').toLowerCase().trim();
}
```

| 级别 | 内容 |
|------|------|
| `none` | 不记录 |
| `minimal` | 核心指标：文件数、行数、验证结果 |
| `standard` | 丰富上下文：文件类型分布、验证命令、错误签名 |

### 25.3 脱敏函数

**文件**: `executionTrace.js:22-55`

```javascript
function desensitizeFilePath(filePath) {
  // src/utils/retry.js → retry.js
  return path.basename(filePath) || path.extname(filePath) || 'unknown';
}

function extractErrorSignature(errorText) {
  // "TypeError: x is not a function" → "TypeError"
  const jsError = text.match(/^((?:[A-Z][a-zA-Z]*)?Error)\b/);
  if (jsError) return jsError[1];
  
  // "ECONNRESET" → "ECONNRESET"
  const errno = text.match(/\b(E[A-Z]{2,})\b/);
  if (errno) return errno[1];
  
  // HTTP 4xx/5xx → "HTTP_404"
  const http = text.match(/\b((?:4|5)\d{2})\b/);
  if (http) return 'HTTP_' + http[1];
}
```

### 25.4 工具链推断

**文件**: `executionTrace.js:58-80`

```javascript
function inferToolChain(validationResults, blast) {
  const tools = new Set();
  
  if (blast.files > 0) tools.add('file_edit');
  
  for (const r of validationResults) {
    if (cmd.includes('jest') || cmd.includes('mocha')) tools.add('test_run');
    else if (cmd.includes('eslint')) tools.add('lint_check');
    else if (cmd.includes('validate')) tools.add('validation_run');
    else if (cmd.startsWith('node ')) tools.add('node_exec');
  }
  
  return Array.from(tools);
}
```

### 25.5 Blast Radius 分级

**文件**: `executionTrace.js:83-95`

```javascript
function classifyBlastLevel(blast) {
  if (files <= 3 && lines <= 50) return 'low';
  if (files <= 10 && lines <= 200) return 'medium';
  return 'high';
}
```

### 25.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 脱敏设计 | 路径→basename、错误→类型签名 | **高优先级**: BlueCortexCE 的 Observation 在跨 Agent 共享前必须脱敏 |
| Trace 级别控制 | `EVOLVER_TRACE_LEVEL` 环境变量 | **高优先级**: BlueCortexCE 应支持 Observation 的敏感度分级 |
| 工具链推断 | 从验证命令推断工具类型 | **中优先级**: BlueCortexCE 可从 API 调用日志推断工具链 |
| 变更范围分级 | low/medium/high 三级 | **中优先级**: BlueCortexCE 的 Observation 可包含变更范围标签 |

---

## 26. taskReceiver.js — 主动任务认领（v0.5 新增）

**文件**: `src/gep/taskReceiver.js` (567 lines)

### 26.1 外部任务获取

**文件**: `taskReceiver.js:50-130`

Evolver 支持从 Hub 获取外部任务（bounty tasks）并注入为高优先级信号：

```javascript
async function fetchTasks(opts) {
  const msg = {
    protocol: 'gep-a2a',
    message_type: 'fetch',
    payload: {
      tasks_only: true,
      include_tasks: true,
    }
  };
  
  const res = await fetch(HUB_URL + '/a2a/fetch', {
    method: 'POST',
    headers: buildHubHeaders(),
    body: JSON.stringify(msg),
  });
}
```

### 26.2 能力匹配算法

**文件**: `taskReceiver.js:105-175`

```javascript
function estimateCapabilityMatch(task, memoryEvents) {
  // 1. 计算任务信号与历史信号的 Jaccard 重叠度
  const taskSignals = parseSignals(task.signals || task.title);
  const overlapScore = jaccard(taskSignals, allAgentSignals);
  
  // 2. 加权成功率先验
  // 对每个匹配的历史信号键，计算 Laplace 平滑成功率
  for (const sk in totalBySignalKey) {
    const skParts = sk.split('|').map(s => s.trim().toLowerCase());
    const sim = jaccard(taskSignals, skParts);
    if (sim < 0.15) continue;
    
    const rate = (succ + 1) / (total + 2);  // Laplace
    weightedSuccess += rate * sim;
    weightSum += sim;
  }
  
  // 3. 综合评分：40% 信号重叠 + 60% 历史成功率
  return Math.min(1, overlapScore * 0.4 + successScore * 0.6);
}
```

**Evolver 为什么这样做**: 在认领外部任务前，先评估本 Agent 的能力是否匹配，避免无效的任务认领导致失败。

### 26.3 任务选择策略

**文件**: `taskReceiver.js:20-35`

```javascript
const STRATEGY_WEIGHTS = {
  greedy:       { roi: 0.10, capability: 0.05, completion: 0.05, bounty: 0.80 },
  balanced:     { roi: 0.35, capability: 0.30, completion: 0.20, bounty: 0.15 },
  conservative: { roi: 0.25, capability: 0.45, completion: 0.25, bounty: 0.05 },
};
```

### 26.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 外部任务获取 | Hub 任务 → 信号注入 | **中优先级**: BlueCortexCE 可支持"外部问题 → 记忆查询"的映射 |
| 能力匹配 | Jaccard + Laplace 加权成功率 | **高优先级**: BlueCortexCE 的 Search 应返回"匹配度"评分 |
| 策略选择 | greedy/balanced/conservative | **低优先级**: BlueCortexCE 的 API 可支持不同检索策略 |
| 任务 ROI 评估 | 赏金 + 能力匹配 + 完成度 | **低优先级**: BlueCortexCE 可实现"问题复杂度"评分 |

---

## 27. hubReview.js — Hub 审查提交（v0.5 新增）

**文件**: `src/gep/hubReview.js` (208 lines)

### 27.1 审查提交时机

**文件**: `hubReview.js:1-10`

当 Evolver 使用了 Hub 资产（`source_type = 'reused'` 或 `'reference'`）且 `solidify` 完成时，**自动提交审查**到 Hub：

```javascript
// 在 solidify() 的最后阶段
if (reusedAssetId && (sourceType === 'reused' || sourceType === 'reference')) {
  submitHubReview({
    reusedAssetId,
    outcome: event.outcome,
    gene: geneUsed,
    signals,
  });
}
```

### 27.2 评分推导

**文件**: `hubReview.js:35-50`

```javascript
function _deriveRating(outcome, constraintCheck) {
  if (outcome.status === 'success') {
    return score >= 0.85 ? 5 : 4;  // 高成功 + 高分 → 5星
  }
  // 失败 + 有约束违反 → 1星（资产质量差）
  // 失败 + 无约束违反 → 2星（可能环境问题）
  return hasConstraintViolation ? 1 : 2;
}
```

### 27.3 重复提交防护

**文件**: `hubReview.js:25-45`

本地文件 `hub_review_history.json` 记录已提交的 assetId，避免重复审查：

```javascript
function _alreadyReviewed(assetId) {
  const history = _loadReviewHistory();
  return !!history[assetId];
}

function _markReviewed(assetId, rating, success) {
  const history = _loadReviewHistory();
  history[assetId] = { at: Date.now(), rating, success };
  _saveReviewHistory(history);
}
```

### 27.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 使用后审查 | 每次使用 Hub 资产后自动提交评分 | **中优先级**: BlueCortexCE 的 API 可支持"使用反馈"提交 |
| 评分体系 | 1-5 星，成功率 + 约束违反双重判定 | **高优先级**: BlueCortexCE 的 Search 结果应支持评分反馈 |
| 重复防护 | 本地历史文件防重复提交 | **中优先级**: BlueCortexCE 应有防重复提交机制 |
| 非阻塞 | 审查失败不影响 solidify 结果 | **高优先级**: BlueCortexCE 的反馈机制应完全异步 |

---

## 28. 整体架构总结：Evolver 的记忆分层（v0.5 补充）

### 28.1 四层记忆架构

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: 即时记忆 (Signals)                                 │
│  - signals.js: 从日志/对话/环境提取"信号"                    │
│  - 生命周期: 单次进化周期                                     │
├─────────────────────────────────────────────────────────────┤
│  Layer 2: 事件记忆 (Events)                                  │
│  - memoryGraph.jsonl: Signal→Hypothesis→Attempt→Outcome     │
│  - 生命周期: 永久（append-only）                              │
├─────────────────────────────────────────────────────────────┤
│  Layer 3: 资产记忆 (Assets)                                  │
│  - genes.json / capsules.json: 成功的 Gene + Capsule         │
│  - failed_capsules.jsonl: 失败的 Capsule（反模式）            │
│  - 生命周期: 持久化资产库                                     │
├─────────────────────────────────────────────────────────────┤
│  Layer 4: 聚合知识 (Aggregated Knowledge)                   │
│  - narrativeMemory.md: 叙事性历史（人类可读）                 │
│  - hubSearch: Hub 共享知识                                    │
│  - executionTrace: 脱敏执行轨迹                               │
│  - 生命周期: 跨 Agent 共享                                   │
└─────────────────────────────────────────────────────────────┘
```

### 28.2 表观遗传机制（特别设计）

Evolver 的表观遗传（`epigenetic_marks`）是一个**独特设计**：

- **环境绑定**: 基因在不同环境（Linux/macOS/Node版本）下表现不同
- **非遗传**: 不会改变基因的核心策略，只是调整表达强度
- **衰减**: 90 天无强化则消失，最多保留 10 个标记
- **Boost 值范围**: [-0.5, +0.5]，成功时 +0.05，失败时 -0.1

这相当于为每个 Gene 维护了一个**环境相关的成功率缓存**。

### 28.3 BlueCortexCE 对照

| Evolver 层 | BlueCortexCE 等价 |
|-----------|------------------|
| Signals | Observations（用户提示 + 工具结果） |
| memoryGraph.jsonl | PostgreSQL 表（SessionEntity, ObservationEntity） |
| Genes/Capsules | SummaryEntity（固化经验） |
| narrativeMemory | Summary.content（人类可读摘要） |
| executionTrace | （无直接对应——但可作为 Observation 的 metadata） |
| epigenetic_marks | （无直接对应——BlueCortexCE 是旁路型，无"环境感知进化"） |

---

## 29. skillDistiller.js — 深度补充（v0.6 新增）

### 29.1 完整 Distillation Pipeline

skillDistiller 实际上有**两套并行的提炼流程**：

#### 成功路径提炼 (Success Distillation)

**Gate 条件**:
- `DISTILLER_MIN_CAPSULES = 10` (最近 10 个 capsule 中至少需要 ≥7 个成功)
- `DISTILLER_MIN_SUCCESS_RATE = 0.7`
- `DISTILLER_INTERVAL_HOURS = 24` (间隔至少 24 小时)
- 数据哈希变化 (idempotent skip)

**流程** (`skillDistiller.js:551-570`):

```javascript
function prepareDistillation() {
  // Step 1: collectDistillationData — 收集成功 capsule，分组统计
  const data = collectDistillationData();
  
  // Step 2: analyzePatterns — 发现高频、漂移、覆盖缺口
  const analysis = analyzePatterns(data);
  
  // Step 3: buildDistillationPrompt — 构建 LLM 提示词
  const prompt = buildDistillationPrompt(analysis, existingGenes, samples);
  
  // 写入 distill_request.json 和 prompt 文件
  fs.writeFileSync(reqPath, requestData);
  fs.writeFileSync(promptPath, prompt);
}
```

```javascript
function completeDistillation(responseText) {
  // Step 4: extractJsonFromLlmResponse — 从 LLM 响应解析 Gene JSON
  const rawGene = extractJsonFromLlmResponse(responseText);
  
  // Step 5: validateSynthesizedGene — 多重验证
  const validation = validateSynthesizedGene(rawGene, existingGenes);
  
  // 验证通过后写入 genes.json
  assetStore.upsertGene(gene);
  
  // 自动发布到 Hub
  if (process.env.SKILL_AUTO_PUBLISH !== '0') {
    skillPublisher.publishSkillToHub(gene);
  }
}
```

#### 失败路径提炼 (Failure Distillation)

**Gate 条件**:
- `FAILURE_DISTILLER_MIN_CAPSULES = 5` (至少 5 个失败 capsule)
- `FAILURE_DISTILLER_INTERVAL_HOURS = 12`

专门从**失败胶囊**中提取反模式，生成 `gene_repair_distilled_*` 前缀的修复型 Gene。

### 29.2 sanitizeSignalsMatch — 信号清洗

**文件**: `skillDistiller.js:357-390`

这是 skillDistiller 的**核心防御机制**——确保 LLM 生成的信号不会泄露工具名称、时间戳或会话 ID：

```javascript
function sanitizeSignalsMatch(signals) {
  return signals
    .map(s => String(s).trim().toLowerCase())
    .filter(s => s.length >= 3)                          // 太短则过滤
    .filter(s => !/^\d+$/.test(s))                       // 纯数字过滤
    .filter(s => !/^(cursor|vscode|vim|emacs|windsurf|copilot|cline|codex|bypass|distill)[_-]?\d*$/i.test(s))  // 工具名过滤
    .filter(s => !/\d{8,}/.test(s))                     // 长数字序列（会话 ID）过滤
    .map(s => s.replace(/[_-]\d{10,}$/g, ''))           // 去除尾部时间戳
    .map(s => s.replace(/^[_-]+|[_-]+$/g, ''))          // 去除首尾分隔符
    .filter(Boolean)
    .deduplicate();
}
```

**Evolver 为什么这样做**: LLM 生成 `signals_match` 时容易带上原始会话的上下文（工具名、时间戳），这些必须被清洗掉，否则同一个技能的多个 distillation 会产生不同的信号键，导致基因无法被正确匹配。

### 29.3 validateSynthesizedGene — 多重验证门

**文件**: `skillDistiller.js:392-430`

```javascript
function validateSynthesizedGene(gene, existingGenes) {
  const errors = [];
  
  // 1. 必须有 type=Gene
  if (gene.type !== 'Gene') errors.push('missing or wrong type');
  
  // 2. ID 必须以 gene_distilled_ 开头
  if (!gene.id?.startsWith(DISTILLED_ID_PREFIX)) 
    gene.id = DISTILLED_ID_PREFIX + gene.id;
  
  // 3. 工具名/纯数字 ID → deriveDescriptiveId 自动重命名
  if (needsRename) gene.id = deriveDescriptiveId(gene);
  
  // 4. signals_match 清洗后不能为空
  gene.signals_match = sanitizeSignalsMatch(gene.signals_match);
  if (gene.signals_match.length === 0) 
    errors.push('signals_match empty after sanitization');
  
  // 5. strategy 至少 3 步
  if (gene.strategy?.length < 3) 
    errors.push('strategy must have at least 3 steps');
  
  // 6. constraints.forbidden_paths 必须包含 .git 或 node_modules
  if (!gene.constraints?.forbidden_paths?.some(p => p === '.git' || p === 'node_modules'))
    errors.push('must forbid .git or node_modules');
  
  // 7. max_files ≤ 12
  if (gene.constraints?.max_files > 12) 
    gene.constraints.max_files = 12;
  
  // 8. validation 命令必须通过 policyCheck.isValidationCommandAllowed
  gene.validation = gene.validation.filter(cmd => isValidationCommandAllowed(cmd));
  
  // 9. signals_match 不能与已有基因完全重复
  if (overlapsWithExisting(gene.signals_match, existingGenes))
    errors.push('signals_match fully overlaps with existing gene');
  
  // 10. ID 不能与已有基因冲突
  if (existingIds.has(gene.id))
    gene.id = gene.id + '_' + Date.now().toString(36);
}
```

### 29.4 deriveDescriptiveId — 无意义 ID 的自动修复

**文件**: `skillDistiller.js:321-355`

当 LLM 生成的 ID 包含工具名/时间戳时，使用**描述性 fallback**：

```javascript
function deriveDescriptiveId(gene) {
  // 优先从 signals_match 提取关键词
  const words = gene.signals_match?.slice(0, 3)
    .flatMap(s => s.toLowerCase().replace(/[^a-z0-9]+/g, ' ').split(' '))
    .filter(w => w.length >= 3)
    .slice(0, 6) || [];
  
  // 次选从 summary 提取
  if (words.length < 3 && gene.summary) {
    const STOP = new Set(['the','and','for','with','from','that','this','into','when','are','was','has','had']);
    words.push(...gene.summary.split(' ').filter(w => w.length >= 3 && !STOP.has(w)).slice(0, 6));
  }
  
  // 兜底：从 strategy 第一步提取
  if (words.length < 2) words.push('auto', 'distilled', 'strategy');
  
  return DISTILLED_ID_PREFIX + unique(words).slice(0, 5).join('-');
}
```

### 29.5 buildDistillationPrompt — 完整的 LLM 提示词模板

**文件**: `skillDistiller.js:225-300`

```javascript
// 核心指令片段
'- Output ONLY a single valid JSON object (no markdown fences, no explanation).'
'- The id MUST start with "gene_distilled_" followed by a descriptive kebab-case name.'
'- Good: "gene_distilled_retry-with-exponential-backoff"'
'- Bad: "gene_distilled_cursor-1773331925711", "gene_distilled_1234567890"'
'- Summary must be 30-200 chars, marketplace-quality description.'
'- signals_match: 3-7 generic reusable keywords, lowercase_snake_case.'
'- NEVER include timestamps, build numbers, tool names (cursor, vscode, etc.)'
'- Strategy: 5-10 actionable imperative steps with inline code examples.'
'- Validation: commands must start with "node ", "npm ", or "npx "'
'- constraints.max_files MUST be <= 12'
'- constraints.forbidden_paths MUST include at least [".git", "node_modules"]'
'- Imagine this Gene will be published on a marketplace for thousands of AI agents.'
```

**Evolver 为什么这样做**: 提示词层面的强约束比验证规则更高效——从源头阻止无效信号比事后过滤更可靠。

### 29.6 distillation state.json — 幂等状态机

**文件**: `skillDistiller.js:51-70`

```javascript
// distiller_state.json 内容
{
  "last_distillation_at": "2026-04-16T12:00:00Z",
  "last_data_hash": "a1b2c3d4e5f6",
  "last_gene_id": "gene_distilled_retry-with-exponential-backoff",
  "distillation_count": 3
}

// 两个幂等保证:
// 1. 时间间隔: elapsed < DISTILLER_INTERVAL_HOURS → skip
// 2. 数据不变: last_data_hash === current_data_hash → skip
```

### 29.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 信号清洗 (sanitizeSignalsMatch) | LLM 生成后 strip 工具名/时间戳 | **高优先级**: BlueCortexCE 的 Summary 生成后应清洗无效信号 |
| 多重验证门 | 10 重检查覆盖类型/ID/信号/策略/约束/验证命令 | **高优先级**: BlueCortexCE 的任何 LLM 生成内容都应有多重验证 |
| deriveDescriptiveId fallback | 无意义 ID 自动从 signals_match 重建 | **高优先级**: BlueCortexCE 的 extraction 结果如果信号太具体，应自动抽象化 |
| 幂等状态机 | state.json + data_hash 防重复提炼 | **高优先级**: BlueCortexCE 的任何周期性任务应有 idempotent skip |
| 失败路径提炼 | 从 failed_capsules 提取反模式 | **中优先级**: BlueCortexCE 可记录"检索无效"的模式，避免重复 |
| 自动 Hub 发布 | SKILL_AUTO_PUBLISH → skillPublisher.publishSkillToHub | **低优先级**: BlueCortexCE 的 extraction 结果可发布到共享市场 |

---

## 30. reflection.js — 战略反思机制（v0.6 新增）

**文件**: `src/gep/reflection.js` (145 lines)

### 30.1 设计定位

reflection.js 是 Evolver 的**元认知层**——在多个进化周期后，停下来反思：
- 当前策略是否最优？
- 是否有被忽略的信号？
- 是否陷入了局部最优？

### 30.2 shouldReflect — 自适应反思周期

**文件**: `reflection.js:35-50`

```javascript
function computeReflectionInterval(recentEvents) {
  if (recentEvents.length < 3) return REFLECTION_INTERVAL_DEFAULT; // 5
  
  const tail = recentEvents.slice(-3);
  const allSuccess = tail.every(e => e.outcome?.status === 'success');
  const allFailed = tail.every(e => e.outcome?.status === 'failed');
  
  if (allSuccess) return REFLECTION_INTERVAL_SUCCESS;    // 8 cycles
  if (allFailed)  return REFLECTION_INTERVAL_FAILURE;    // 3 cycles
  return REFLECTION_INTERVAL_DEFAULT;                     // 5 cycles
}
```

**Evolver 为什么这样做**: 
- 连续成功时延长反思间隔（8 cycles），因为系统运转良好
- 连续失败时缩短反思间隔（3 cycles），尽快发现问题
- 反思冷却时间 30 分钟，防止在短时间内重复反思

### 30.3 buildSuggestedMutations — 反思驱动的参数调整

**文件**: `reflection.js:55-80`

```javascript
function buildSuggestedMutations(signals) {
  const muts = [];
  
  // 停滞 → 提高创造力
  if (has('stable_success_plateau', 'evolution_stagnation_detected', 'empty_cycle_loop_detected'))
    muts.push({ param: 'creativity', delta: +0.05 });
  
  // 错误 → 提高严谨度
  if (has('log_error', 'errsig:', 'errsig_norm:'))
    muts.push({ param: 'rigor', delta: +0.05 });
  
  // 能力缺口 → 提高风险容忍
  if (has('capability_gap', 'external_opportunity'))
    muts.push({ param: 'risk_tolerance', delta: +0.05 });
  
  return muts.slice(0, 2);  // 每次最多 2 个建议
}
```

**Evolver 为什么这样做**: 反思阶段不是生成新的 Gene，而是建议**调整人格参数**。这是最小干预原则——如果当前策略本身没问题，只是执行时的冒险程度需要调整。

### 30.4 buildReflectionContext — 反思上下文构建

**文件**: `reflection.js:82-120`

```javascript
function buildReflectionContext({ recentEvents, signals, memoryAdvice, narrative }) {
  // 输出结构化报告:
  // ## Recent Cycle Statistics (last 10)
  // - Success: N, Failed: N
  // - Intent distribution: {...}
  // - Gene usage: {...}
  
  // ## Current Signals
  // [signals...]
  
  // ## Memory Graph Advice
  // - Preferred gene: ...
  // - Banned genes: ...
  
  // ## Recent Evolution Narrative
  // [narrative snippet]
  
  // ## Questions to Answer
  // 1. Are there persistent signals being ignored?
  // 2. Is the gene selection strategy optimal?
  // 3. Should the balance between repair/optimize/innovate shift?
  // 4. Are there capability gaps that no current gene addresses?
  // 5. What single strategic adjustment would have the highest impact?
  
  return prompt;
}
```

**Evolver 为什么这样做**: 反思不是空想，而是基于数据：最近的统计（成功率、基因使用频率）、当前信号、历史叙事记忆。

### 30.5 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 自适应反思周期 | 连续成功→长间隔，连续失败→短间隔 | **高优先级**: BlueCortexCE 的 Summary 触发可参考"检索成功率"动态调整 |
| 反思冷却 (30min) | 防止短时间重复反思 | **高优先级**: BlueCortexCE 的任何 LLM 调用应有冷却机制 |
| 参数微调建议 | 反思 → 建议人格参数 delta | **低优先级**: BlueCortexCE 是旁路型，无人格参数 |
| 5 个战略问题 | 引导 LLM 聚焦关键决策 | **中优先级**: BlueCortexCE 的 periodic review 可参考这些问题模板 |
| 叙事记忆注入 | 反思时加载 narrative 摘要 | **高优先级**: BlueCortexCE 的检索结果可附带"历史使用情况" |

---

## 31. candidates.js + candidateEval.js — 能力候选提取（v0.6 新增）

**文件**: `src/gep/candidates.js` (210 lines) + `src/gep/candidateEval.js` (80 lines)

### 31.1 设计定位

candidates.js 从**当前会话**中提取"能力缺口候选"，在 solidify 之前预填充 Gene 候选池：

```
Session Transcript
    ↓
extractCapabilityCandidates (candidates.js)
    ↓
[Cap1: 重复工具调用, Cap2: 失败路径模式, Cap3: 信号缺口]
    ↓
appendCandidateJsonl (持久化到 candidates.jsonl)
    ↓
solidify 时从 candidates.jsonl 加载，作为 Gene 选择参考
```

### 31.2 候选来源 (extractCapabilityCandidates)

**文件**: `candidates.js:60-200`

**来源 1: 重复工具调用** (工具使用 ≥3 次)
```javascript
for (const [tool, count] of freq.entries()) {
  if (count < 3) continue;
  // 从 transcript 中提取的重复工具 → CapabilityCandidate
  candidates.push({
    type: 'CapabilityCandidate',
    title: `Repeated tool usage: ${tool}`,
    source: 'transcript',
    tags: expandSignals(signals, transcript),  // 语义扩展
  });
}
```

**来源 2: 信号缺口** (当前信号列表中有特定信号)
```javascript
const signalCandidates = [
  { signal: 'log_error', title: 'Repair recurring runtime errors' },
  { signal: 'protocol_drift', title: 'Prevent protocol drift' },
  { signal: 'user_feature_request', title: 'Implement user-requested feature' },
  { signal: 'capability_gap', title: 'Fill capability gap' },
  { signal: 'stable_success_plateau', title: 'Explore new strategies during stability plateau' },
  // ...
];
```

**来源 3: 失败胶囊反模式** (失败 ≥2 次，按问题类型分组)
```javascript
// 按 problem:xxx 标签分组
const groups = {};
failedCapsules.forEach(fc => {
  const failureTags = expandSignals(triggers, reason)
    .filter(t => t.startsWith('problem:') || t.startsWith('risk:') || t.startsWith('area:'));
  // 同一 dominantProblem 的失败聚合成一条候选
  groups[key] = { count, tags, reasons, gene };
});

// count >= 2 时才生成候选（避免噪声）
if (group.count >= 2) {
  candidates.push({
    type: 'CapabilityCandidate',
    title: getTitleFromProblemType(dominantProblem),
    source: 'failed_capsules',
  });
}
```

### 31.3 CapabilityCandidate 的 Shape 结构

**文件**: `candidates.js:35-50`

```javascript
function buildFiveQuestionsShape({ title, signals, evidence }) {
  return {
    title: String(title).slice(0, 120),
    input: 'Recent session transcript + memory snippets + user instructions',
    output: 'A safe, auditable evolution patch guided by GEP assets',
    invariants: 'Protocol order, small reversible patches, validation, append-only events',
    params: `Signals: ${signals.join(', ')}`,
    failure_points: 'Missing signals, over-broad changes, skipped validation, missing knowledge solidification',
    evidence: clip(evidence, 240),
  };
}
```

**Evolver 为什么这样做**: 用 Five Questions 模板结构化候选表达，确保每个候选都有清晰的输入/输出/失败点描述。

### 31.4 buildCandidatePreviews — 候选预览构建

**文件**: `candidateEval.js:15-80`

**内部候选**:
```javascript
const newCandidates = extractCapabilityCandidates({ transcript, signals, failedCapsules });
const recentCandidates = readRecentCandidates(20);
const capabilityCandidatesPreview = renderCandidatesPreview(recentCandidates.slice(-8), 1600);
```

**外部候选** (从 Hub 获取的基因/胶囊):
```javascript
const external = readRecentExternalCandidates(50);
const capsulesOnly = external.filter(x => x.type === 'Capsule');
const genesOnly = external.filter(x => x.type === 'Gene');

// 按 signals_match 与当前信号的匹配度排序
const matchedExternalGenes = genesOnly
  .map(g => ({
    gene: g,
    hit: g.signals_match.reduce((acc, p) => matchPatternToSignals(p, signals) ? acc + 1 : acc, 0)
  }))
  .filter(x => x.hit > 0)
  .sort((a, b) => b.hit - a.hit)
  .slice(0, 3)
  .map(x => x.gene);
```

**Evolver 为什么这样做**: 外部候选来自 Hub，按信号匹配度过滤，只推荐与当前信号相关的外部资产。

### 31.5 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 重复工具调用候选 | transcript 中工具 ≥3 次 → CapabilityCandidate | **高优先级**: BlueCortexCE 可从 Session 中检测"重复模式"作为 Summary 候选 |
| 失败胶囊反模式 | 失败 ≥2 次 + 同问题类型 → 候选 | **高优先级**: BlueCortexCE 应有"失败经验"记录（Observation +1 标记） |
| 外部候选匹配 | Hub 资产按 signals_match 匹配度过滤 | **中优先级**: BlueCortexCE 的 Search 结果可标注"匹配度评分" |
| Five Questions Shape | 标准化输入/输出/失败点 | **中优先级**: BlueCortexCE 的 Structured Extraction 可参考此格式 |
| 候选池持久化 | candidates.jsonl append-only | **低优先级**: BlueCortexCE 当前用 Summary 作为"候选" |

---

## 32. Evolver 的 Genes/Capsules 资产体系（v0.6 补充）

### 32.1 Gene Schema 完整字段

**文件**: `src/gep/assetStore.js:80-150`

```javascript
// Gene 的完整结构
{
  "id": "gene_distilled_retry-with-exponential-backoff",  // 必须前缀
  "type": "Gene",
  "category": "repair|optimize|innovate",
  "summary": "Retry failed HTTP requests with exponential backoff...",
  
  "signals_match": [                    // 触发信号（归一化后）
    "http_retry",
    "request_timeout",
    "circuit_breaker",
    "resilience"
  ],
  
  "preconditions": [                    // 前置条件
    "Project uses Node.js >= 18",
    "HTTP client library available"
  ],
  
  "strategy": [                         // 策略步骤
    "Step 1: ...",
    "Step 2: ..."
  ],
  
  "constraints": {
    "max_files": 12,
    "forbidden_paths": [".git", "node_modules"]
  },
  
  "validation": [                       // 验证命令
    "npm test",
    "npx tsc --noEmit"
  ],
  
  "_distilled_meta": {                  // 仅 distillation 生成时有
    "distilled_at": "2026-04-16T12:00:00Z",
    "source_capsule_count": 10,
    "data_hash": "a1b2c3"
  },
  
  "epigenetic_marks": [                 // 环境标记（Evolver 特有）
    { "env": "darwin-arm64", "boost": 0.15 },
    { "env": "linux-x64", "boost": -0.05 }
  ]
}
```

### 32.2 Capsule Schema 完整字段

```javascript
// Capsule = 一次进化尝试的完整记录
{
  "id": "cap_xxx",
  "type": "Capsule",
  "gene": "gene_distilled_retry-with-exponential-backoff",
  "trigger": ["http_retry", "request_timeout"],
  
  "outcome": {
    "status": "success|failed",
    "score": 0.85,
    "blast_radius": { "files": 2, "lines": 80 },
    "duration_ms": 45000
  },
  
  "summary": "Added retry logic to HTTP client module...",
  
  // 失败特有字段
  "failure_reason": "...",
  "failure_tags": ["problem:reliability", "risk:regression"],
  
  // 固化和发布标记
  "solidified": true,
  "source_type": "new|reused|reference",
  "published": false
}
```

### 32.3 BlueCortexCE 对照

| Evolver 资产 | BlueCortexCE 等价 | 差距 |
|-------------|------------------|------|
| Gene.signals_match | ObservationEntity.tags | 差距：BC 的 tags 是原始信号，无归一化 |
| Gene.strategy | SummaryEntity.content | 差距：BC 的 content 是自然语言，非结构化步骤 |
| Gene.constraints | 无 | **缺失**: BlueCortexCE 没有 constraints 概念 |
| Gene.validation | 无 | **缺失**: BlueCortexCE 没有验证命令概念 |
| Capsule.blast_radius | 无 | **缺失**: BlueCortexCE 没有"影响范围"记录 |
| Capsule.failure_reason | ObservationEntity 内容 | BC 将失败记录为普通 Observation |
| epigenetic_marks | 无 | **缺失**: BlueCortexCE 无环境感知 |

### 32.4 最关键的差距分析

**Gap 1: signals_match 归一化**
Evolver 的 `signals_match` 是经过 `sanitizeSignalsMatch` 清洗的归一化信号（无工具名/时间戳）。BlueCortexCE 的 observation.tags 直接来自信号提取，没有经过归一化清洗。

**Gap 2: 策略的结构化表达**
Evolver 的 Gene.strategy 是明确的步骤列表。BlueCortexCE 的 Summary.content 是自然语言，AI 可读但无法直接用于自动化执行。

**Gap 3: constraints + validation**
Evolver 的 Gene 有 constraints（max_files, forbidden_paths）和 validation（npm test）用于安全执行。BlueCortexCE 的任何"自动执行"都缺乏这类安全约束。

---

## 33. 文档版本历史与 TODO

### 33.1 版本记录

| 版本 | 日期 | 新增内容 |
|------|------|----------|
| v0.1 | 2026-04-16 | 初始框架 |
| v0.2 | 2026-04-16 | skillDistiller.js 初步分析 |
| v0.3 | 2026-04-16 | solidify.js, selector.js, curriculum.js |
| v0.4 | 2026-04-16 | memoryGraph 深度分析 + 整体架构总结 |
| v0.5 | 2026-04-16 | skillPublisher, executionTrace, taskReceiver, hubReview |
| v0.6 | 2026-04-16 20:24 | skillDistiller 深度补充 + reflection.js + candidates.js + Gene/Capsule 资产体系 |
| v0.7 | 2026-04-16 21:25 | signals.js + learningSignals.js + mutation.js + evolve.js 核心循环 |
| v0.8 | 2026-04-16 22:54 | prompt.js + strategy.js + questionGenerator.js + idleScheduler.js + gitOps.js + localStateAwareness.js |
| v0.9 | 2026-04-17 01:34 | policyCheck.js 深度补充 + sanitize.js + contentHash.js + crypto.js + envFingerprint.js + issueReporter.js + validationReport.js + analyzer.js + 安全隐私体系总结 |
| v1.0 | 2026-04-17 02:22 | hubSearch.js + hubReview.js + executionTrace.js + assetCallLog.js + directoryClient.js + deviceId.js |
| v1.1 | 2026-04-17 03:35 | a2aProtocol.js 深度分析（联邦通信协议、HMAC 签名、双传输层、心跳机制、SSE 事件流） |
| v1.2 | 2026-04-17 04:36 | prompt.js + strategy.js + memoryGraphAdapter.js + innovation.js + questionGenerator.js + idleScheduler.js + localStateAwareness.js 深度补充 |
| v1.3 | 2026-04-17 05:41 | gitOps.js + bridge.js + a2a.js + privacyClient.js + assets.js 深度补充 |

---

## 34. signals.js + learningSignals.js — 信号处理链路 (v0.7 新增)

### 34.1 整体信号处理架构

Evolver 的信号系统分为两层：

```
原始信号来源 → signals.js (提取+去重) → expandedTags → gene selection
                           ↓
              learningSignals.js (信号扩展+标签评分)
```

**关键认知**：BlueCortexCE 的 Observation.tags 是"原始信号"，Evolver 的 signals 是经过多步处理的"精炼信号"。

### 34.2 signals.js — 信号提取与去重

**文件**: `src/gep/signals.js` (446 lines)

#### 34.2.1 信号来源（4 个语料库）

```javascript
// evolve.js:1268
var corpus = [
  String(recentSessionTranscript || ''),
  String(todayLog || ''),
  String(memorySnippet || ''),
  String(userSnippet || ''),
].join('\n');
```

| 来源 | 说明 | BlueCortexCE 等价 |
|------|------|------------------|
| recentSessionTranscript | Agent 执行日志 | SessionEntity + UserPromptEntity |
| todayLog | 当日记忆摘要 | 当日 Observation 汇总 |
| memorySnippet | narrativeMemory 摘要 | SummaryEntity |
| userSnippet | 用户显式输入 | 最新 UserPromptEntity |

#### 34.2.2 防御性信号（Defensive Signals）

```javascript
// signals.js:144
var errorHit = /\[error\]|error:|exception:|iserror":true|"status":\s*"error"|.../.test(lower);
if (errorHit) signals.push('log_error');
```

**Evolver 做法**：多语言正则匹配 + 结构化 JSON 错误检测，支持 EN/ZH/JA。
**BlueCortexCE 现状**：依赖 LLM 的自然语言理解，没有结构化错误模式检测。

#### 34.2.3 重复错误检测

```javascript
// signals.js:183
var recurringErrors = Object.entries(errorCounts).filter(function (e) { return e[1] >= 3; });
if (recurringErrors.length > 0) {
  signals.push('recurring_error');
  signals.push('recurring_errsig(' + topErr[1] + 'x):' + topErr[0].slice(0, 150));
}
```

**Evolver 做法**：统计 3 次以上的重复错误，生成可读的 errsig 标签。
**借鉴点**：BlueCortexCE 可以在 Observation 层面增加"重复计数"字段，当同一模式出现 3+ 次时触发升级信号。

#### 34.2.4 历史信号压制（去重机制）

```javascript
// signals.js:32
function analyzeRecentHistory(recentEvents) {
  // 抑制最近 8 个事件中出现 3+ 次的信号
  var suppressedSignals = new Set();
  // ...
}
```

**Evolver 做法**：如果某个信号在过去 8 个事件中出现 ≥3 次，则压制它避免重复处理。
**BlueCortexCE 现状**：无去重机制，所有 Observation 平等对待。

#### 34.2.5 连续失败/空循环检测

```javascript
// signals.js:106
consecutiveRepairCount: consecutiveRepairCount,  // 连续 repair 次数
consecutiveEmptyCycles: consecutiveEmptyCycles,  // 连续空循环次数
consecutiveFailureCount: consecutiveFailureCount,  // 连续失败次数
recentFailureRatio: recentFailureCount / tail.length,  // 失败率
```

**Evolver 做法**：检测连续失败/空循环，用于判断是否需要降级（repair loop circuit breaker）。
**BlueCortexCE 现状**：无此机制。

### 34.3 learningSignals.js — 信号扩展与标签评分

**文件**: `src/gep/learningSignals.js` (89 lines)

#### 34.3.1 expandSignals — 信号扩展

```javascript
// learningSignals.js:16
function expandSignals(signals, extraText) {
  const raw = Array.isArray(signals) ? signals.map(function (s) { return String(s); }) : [];
  const tags = [];

  // 1. 基础扩展：添加带参数前缀的原始信号
  for (let i = 0; i < raw.length; i++) {
    const signal = raw[i];
    add(tags, signal);
    const base = signal.split(':')[0];
    if (base && base !== signal) add(tags, base);
  }

  // 2. 问题-行动映射
  const text = (raw.join(' ') + ' ' + String(extraText || '')).toLowerCase();

  if (/(error|exception|failed|unstable|log_error|runtime|429)/.test(text)) {
    add(tags, 'problem:reliability');
    add(tags, 'action:repair');
  }
  if (/(protocol|prompt|audit|gep|schema|drift)/.test(text)) {
    add(tags, 'problem:protocol');
    add(tags, 'action:optimize');
    add(tags, 'area:prompt');
  }
  if (/(perf|performance|bottleneck|latency|slow|throughput)/.test(text)) {
    add(tags, 'problem:performance');
    add(tags, 'action:optimize');
  }
  // ...
}
```

**Evolver 做法**：将原始信号映射到 (problem, action, area) 三元组标签。
**借鉴点**：BlueCortexCE 的 Observation.tags 可以经过类似的语义扩展，增加 (domain, action, severity) 标签。

#### 34.3.2 scoreTagOverlap — Gene 匹配评分

```javascript
// learningSignals.js:67
function scoreTagOverlap(gene, signals) {
  const signalTags = expandSignals(signals, '');
  const geneTagList = geneTags(gene);
  if (signalTags.length === 0 || geneTagList.length === 0) return 0;
  const signalSet = new Set(signalTags);
  let hits = 0;
  for (let i = 0; i < geneTagList.length; i++) {
    if (signalSet.has(geneTagList[i])) hits++;
  }
  return hits / geneTagList.length;  // Jaccard-like 相似度
}
```

**Evolver 做法**：使用 Jaccard 相似度计算 Gene 与当前信号的匹配度。
**借鉴点**：BlueCortexCE 可以用类似算法做"Summary 推荐"——给定当前 session signals，推荐最相关的历史 Summary。

### 34.4 BlueCortexCE 借鉴建议

| Evolver 机制 | BlueCortexCE 现状 | 翻译：旁路型如何借鉴 | 优先级 |
|-------------|------------------|---------------------|--------|
| 信号去重（压制 3+ 次重复） | 无 | Observation 增加 repeatCount，出现 3+ 次时标记 elevated | 高 |
| 连续失败/空循环检测 | 无 | Summary 增加 failureStreak 字段 | 高 |
| expandSignals 语义扩展 | 无 | Tags 增加 (domain, action) 扩展层 | 中 |
| scoreTagOverlap 推荐 | 无 | SearchService 增加 gene-like 推荐算法 | 中 |
| recurring_error 聚合 | 无 | 错误模式聚类（相似 errors 归为同一 pattern） | 中 |

---

