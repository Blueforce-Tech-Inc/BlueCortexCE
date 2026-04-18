<!-- part 2/8: auto-split from evolver-memory-analysis.md — see index.md -->

## 12. skillDistiller.js — 技能提炼与迁移（v0.2 新增）

**文件**: `src/gep/skillDistiller.js` (1234 lines)

### 12.1 Distillation Pipeline 概览

skillDistiller 将多个成功的 Capsule 提炼为**可复用的 distilled Gene**：

```
collectDistillationData
    ↓
analyzePatterns (high_frequency, strategy_drift, coverage_gaps)
    ↓
buildDistilledGene (从 patterns 创建新 Gene)
    ↓
upsertGene (写入 gene store)
```

### 12.2 Pattern Analysis

**文件**: `skillDistiller.js:195-280`

```javascript
function analyzePatterns(data) {
  const report = {
    high_frequency: [],    // 基因被使用 ≥5 次，信号集中
    strategy_drift: [],    // 同一基因在不同时期策略发生变化
    coverage_gaps: [],     // 有信号（≥3次）但无基因覆盖
    success_rate: ...,
  };
  
  // High frequency: 某基因被频繁使用
  if (g.total_count >= 5) {
    const flat = g.triggers.flat();
    const freq = count(flat);
    const top = Object.keys(freq).sort((a,b) => freq[b]-freq[a]).slice(0, 5);
    report.high_frequency.push({ gene_id, count, avg_score, top_triggers: top });
  }
  
  // Strategy drift: 同一基因早期 vs 晚期 summary 相似度 < 60%
  if (g.summaries.length >= 3) {
    const sim = jaccard(first.toLowerCase(), last.toLowerCase());
    if (sim < 0.6) {
      report.strategy_drift.push({ gene_id, similarity: sim, early, recent });
    }
  }
  
  // Coverage gaps: 信号出现 ≥3 次但无基因覆盖
  const gaps = Object.keys(signalFreq)
    .filter(s => signalFreq[s] >= 3 && !covered.has(s))
    .sort((a,b) => signalFreq[b] - signalFreq[a])
    .slice(0, 10);
  report.coverage_gaps = gaps.map(s => ({ signal: s, frequency: signalFreq[s] }));
}
```

### 12.3 Distilled Gene 创建

**文件**: `skillDistiller.js:400-500`

```javascript
function buildDistilledGene(grouped, coverageGaps, avgScoreThreshold) {
  // 从高成功率 capsule 组创建 distilled gene
  const bestGroups = Object.values(grouped)
    .filter(g => g.total_count >= DISTILLER_MIN_CAPSULES)
    .filter(g => g.avg_score >= avgScoreThreshold);
  
  // 合并触发信号，去重
  const allTriggers = new Set();
  bestGroups.forEach(g => g.triggers.forEach(t => t.forEach(s => allTriggers.add(s))));
  
  const distilledGene = {
    type: 'Gene',
    id: DISTILLED_ID_PREFIX + hash(allTriggers),
    category: inferCategoryFromTriggers(allTriggers),
    signals_match: Array.from(allTriggers).slice(0, 12),
    strategy: extractCommonStrategy(bestGroups),
    constraints: { max_files: DISTILLED_MAX_FILES },
    distilled: true,
    source_count: bestGroups.reduce((sum, g) => sum + g.total_count, 0),
  };
}
```

### 12.4 Coverage Gaps → New Gene

**文件**: `skillDistiller.js:500-600`

```javascript
// 无基因覆盖的信号 → 创建新基因
if (coverageGaps.length > 0) {
  const gapSignals = coverageGaps.map(g => g.signal).slice(0, 8);
  const newGene = {
    type: 'Gene',
    id: 'gene_distilled_gap_' + hash(gapSignals),
    category: inferCategoryFromSignals(gapSignals),
    signals_match: gapSignals,
    strategy: [
      '信号来自 coverage gap 分析，尚未有成功先例',
      '采用保守策略：最小变更范围',
      '优先复用现有基因模式',
    ],
    constraints: { max_files: 6 },  // 更保守
    distilled: true,
    source: 'coverage_gap',
  };
}
```

### 12.5 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| Pattern Analysis | high_frequency/strategy_drift/coverage_gaps | **高优先级**: BlueCortexCE 应分析观察的覆盖率和模式 |
| Coverage Gaps | 无基因覆盖的信号 | **高优先级**: BlueCortexCE 应识别"无记忆覆盖的查询类型" |
| Distilled Gene | 合并多个成功 capsule 为可复用基因 | **中优先级**: BlueCortexCE 可从多个成功检索中提炼"检索模式" |
| Strategy Drift | 检测基因策略随时间的漂移 | **低优先级**: BlueCortexCE 无基因概念 |

---

## 13. policyCheck.js — 约束检查与 Blast Radius 量化（v0.3 新增）

**文件**: `src/gep/policyCheck.js` (551 lines)

### 13.1 核心设计原则

policyCheck.js 是 Evolver 的**安全防护层**，负责：
1. 从 `openclaw.json` 读取约束策略（哪些文件计入 blast radius）
2. 计算 blast radius（变更范围 = 文件数 + 代码行数）
3. 对 blast radius 做多级严重性分类
4. 伦理违规检测（绕过安全机制、隐蔽行为等）
5. 破坏性变更检测（删除关键文件、清空文件）
6. 验证命令白名单 + 带重试的验证执行

### 13.2 约束策略（Constraint Policy）

**文件**: `policyCheck.js:28-65` (`readOpenclawConstraintPolicy`)

Evolver 从 `openclaw.json` 读取文件级别的约束策略：

```javascript
const defaults = {
  excludePrefixes: ['logs/', 'memory/', 'assets/gep/', 'out/', 'temp/', 'node_modules/'],
  excludeExact: ['event.json', 'temp_gep_output.json', ...],
  excludeRegex: ['capsule', 'events?\\.jsonl$'],
  includePrefixes: ['src/', 'scripts/', 'config/'],
  includeExact: ['index.js', 'package.json'],
  includeExtensions: ['.js', '.cjs', '.mjs', '.ts', '.tsx', '.json', '.yaml', ...],
};
```

**三层过滤逻辑**：
1. **excludeExact** → 精确匹配的文件不计入
2. **excludePrefix** → 目录前缀匹配的不计入
3. **excludeRegex** → 正则匹配的不计入
4. **includeExact** → 精确匹配的**计入**
5. **includePrefix** → 目录前缀匹配的**计入**
6. **includeExtensions** → 匹配扩展名的**计入**
7. **默认** → 不匹配任何规则的文件**不计入**

**Evolver 为什么这样做**：变更范围的计算必须是"有意义的工作量"——修改 node_modules/ 下的文件不计入 blast radius，因为那是依赖而非代码本身。

### 13.3 Blast Radius 计算

**文件**: `policyCheck.js:125-165` (`computeBlastRadius`)

```javascript
function computeBlastRadius({ repoRoot, baselineUntracked }) {
  const policy = readOpenclawConstraintPolicy();
  let changedFiles = gitListChangedFiles({ repoRoot }).map(normalizeRelPath).filter(Boolean);

  // 排除 baseline 中的 untracked 文件
  if (Array.isArray(baselineUntracked)) {
    const baselineSet = new Set(baselineUntracked.map(normalizeRelPath));
    changedFiles = changedFiles.filter(f => !baselineSet.has(f));
  }

  const countedFiles = changedFiles.filter(f => isConstraintCountedPath(f, policy));
  const ignoredFiles = changedFiles.filter(f => !isConstraintCountedPath(f, policy));

  // 计算 lines churn（added + deleted）
  const unstagedRows = parseNumstatRows(tryRunCmd('git diff --numstat').out);
  const stagedRows = parseNumstatRows(tryRunCmd('git diff --cached --numstat').out);
  let stagedUnstagedChurn = 0;
  for (const row of [...unstagedRows, ...stagedRows]) {
    if (!isConstraintCountedPath(row.file, policy)) continue;
    stagedUnstagedChurn += row.added + row.deleted;
  }

  // untracked 文件的行数（首次添加的文件也计入 churn）
  // ...遍历 untracked 文件 + countFileLines

  return {
    files: countedFiles.length,      // 有意义的文件数
    lines: stagedUnstagedChurn + untrackedLines,  // 总代码变动行数
    changed_files: countedFiles,      // 详细列表
    ignored_files: ignoredFiles,      // 被排除的文件
    all_changed_files: changedFiles, // 全部变更
  };
}
```

### 13.4 Blast Radius 严重性分级

**文件**: `policyCheck.js:185-220` (`classifyBlastSeverity`)

```javascript
const BLAST_RADIUS_HARD_CAP_FILES = 60;
const BLAST_RADIUS_HARD_CAP_LINES = 20000;
const BLAST_WARN_RATIO = 0.8;       // 80% → warning
const BLAST_CRITICAL_RATIO = 2.0;   // 200% → critical overrun

function classifyBlastSeverity({ blast, maxFiles }) {
  if (files > HARD_CAP_FILES || lines > HARD_CAP_LINES) {
    return { severity: 'hard_cap_breach' };   // 直接拒绝
  }
  if (files > maxFiles * 2.0) {
    return { severity: 'critical_overrun' };  // 超过基因约束2倍
  }
  if (files > maxFiles) {
    return { severity: 'exceeded' };           // 超过基因约束
  }
  if (files > maxFiles * 0.8) {
    return { severity: 'approaching_limit' };  // 接近上限
  }
  return { severity: 'within_limit' };
}
```

**Evolver 为什么这样做**：
- `hard_cap_breach` 是系统级硬限制（60 files / 20000 lines），防止失控
- `critical_overrun` 是基因级限制的 2 倍，说明 Agent 做了超出预期的批量操作
- 多级 severity 让后续的 PRM 评分和固化决策有细粒度依据

### 13.5 伦理违规检测

**文件**: `policyCheck.js:290-330`

```javascript
const ethicsBlockPatterns = [
  { re: /(?:bypass|disable|remove)\s+(?:safety|guardrail|security|ethic|constraint)/i,
    rule: 'safety', msg: 'ethics: strategy attempts to bypass safety mechanisms' },
  { re: /(?:keylogger|screen\s*capture|webcam\s*hijack|mic(?:rophone)?\s*record)/i,
    rule: 'human_welfare', msg: 'ethics: covert monitoring tool in strategy' },
  { re: /(?:social\s+engineering|phishing)\s+(?:attack|template|script)/i,
    rule: 'human_welfare', msg: 'ethics: social engineering content in strategy' },
  { re: /(?:exploit|hack)\s+(?:user|human|people|victim)/i,
    rule: 'human_welfare', msg: 'ethics: human exploitation in strategy' },
  { re: /(?:hide|conceal|obfuscat)\w*\s+(?:action|behavior|intent|log)/i,
    rule: 'transparency', msg: 'ethics: strategy conceals actions from audit trail' },
];
```

**Evolver 为什么这样做**：即使 Agent 运行在隔离环境中，也必须防止任何形式的隐蔽操作或伤害性工具生成。伦理违规是 `hard` 失败，不可重试。

### 13.6 验证命令白名单

**文件**: `policyCheck.js:360-400`

```javascript
const VALIDATION_ALLOWED_PREFIXES = ['node ', 'npm ', 'npx '];

function isValidationCommandAllowed(cmd) {
  // 1. 必须以 node/npm/npx 开头
  if (!VALIDATION_ALLOWED_PREFIXES.some(p => c.startsWith(p))) return false;
  // 2. 禁止 shell 操作符
  if (/`|\$\(/.test(c)) return false;  // 反引号和 $() 禁止
  const stripped = c.replace(/"[^"]*"/g, '').replace(/'[^']*'/g, '');
  if (/[;&|><]/.test(stripped)) return false;
  // 3. 禁止危险的 node 选项
  if (/^node\s+(-e|--eval|--print|-p)\b/.test(c)) return false;
  return true;
}
```

**Evolver 为什么这样做**：基因的 `validation` 字段是用户可控的输入，必须严格白名单化，防止通过 `validation` 执行恶意命令。

### 13.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 约束策略配置 | openclaw.json 定义 include/exclude | **高优先级**: BlueCortexCE 应支持管理员配置"哪些 API 调用计入用量" |
| Blast radius 多级 | hard_cap → critical → exceeded → warning → within | **高优先级**: BlueCortexCE 的用量限制应有类似分级响应 |
| 代码 churn 计算 | added + deleted lines | **中优先级**: 用量不仅算次数，还应算影响范围 |
| 伦理违规检测 | 5 类 regex pattern 匹配 | **高优先级**: BlueCortexCE 应拒绝有害内容的 Observation 写入 |
| 验证命令白名单 | node/npm/npx 前缀 + shell 操作符排除 | **高优先级**: 如果 BlueCortexCE 支持"验证命令"执行，必须严格白名单化 |
| 破坏性变更检测 | 关键文件删除/清空检测 | **高优先级**: BlueCortexCE 应监控异常的大量删除操作模式 |

---

## 14. hubSearch.js — Hub 知识共享网络（v0.3 新增）

**文件**: `src/gep/hubSearch.js` (407 lines)

### 14.1 核心设计原则：Search-First Evolution

Evolver 的 Hub 搜索代表了一种 **Search-First 策略**：

```
信号提取 → Hub 搜索 → 命中？→ [是] 复用 Hub 方案
                            → [否] 本地正常进化
```

**两种复用模式**：
- **`reference`**（默认）：Hub 方案作为提示注入 prompt，Agent 自主决策
- **`direct`**：直接使用 Hub 方案，跳过本地推理

### 14.2 两阶段搜索（Two-Phase Search）

**文件**: `hubSearch.js:190-280`

这是 Evolver 最精妙的设计之一——**通过两阶段设计最小化 Hub 成本**：

```javascript
// Phase 1: search_only=true → 只获取候选元数据（免费，无 credit 消耗）
const searchMsg = buildFetch({ signals: signalList, searchOnly: true });
const res = await fetch(endpoint, { method: 'POST', body: JSON.stringify(searchMsg), ... });
const results = res.payload.results;  // 包含 confidence/streak/reputation 等

// Phase 2: asset_ids=[best_match] → 获取完整 payload（付费，但只获取 1 个）
const fetchMsg = buildFetch({ assetIds: [selectedAssetId] });
const res2 = await fetch(endpoint, { method: 'POST', body: JSON.stringify(fetchMsg), ... });
```

**为什么这样做**：
1. Hub 的 `/a2a/fetch` 是按次收费的
2. Phase 1 只返回 metadata（免费），让 Evolver 可以在**有信息依据**的情况下选择最有希望的 1 个
3. Phase 2 才付费获取完整内容

### 14.3 缓存策略

**文件**: `hubSearch.js:30-75`

```javascript
const SEARCH_CACHE_TTL_MS = 5 * 60 * 1000;    // 5 分钟 TTL
const SEARCH_CACHE_MAX = 200;                   // LRU 上限
const PAYLOAD_CACHE_MAX = 100;                  // 无 TTL，但 LRU 上限

function _cacheKey(signals) {
  return signals.slice().sort().join('|');  // 信号排序后作为 key
}
```

**两层缓存**：
1. **搜索缓存**：信号指纹 → Phase 1 结果（5 分钟 TTL，防止重复搜索）
2. **载荷缓存**：asset_id → 完整 payload（无 TTL，直到 LRU 上限被驱逐）

### 14.4 Hub 资产评分

**文件**: `hubSearch.js:140-170`

```javascript
function scoreHubResult(asset) {
  const confidence = Number(asset.confidence) || 0;
  const streak = Math.min(Math.max(Number(asset.success_streak) || 0, 1), MAX_STREAK_CAP);
  // streak 上限 = 5（防止无限累积）
  const reputation = Number.isFinite(repRaw) ? repRaw : 50;
  var base = confidence * streak * (reputation / 100);
  var sim = Number(asset._semantic_similarity) || 0;
  if (sim > 0) base += sim * SEMANTIC_SIMILARITY_BONUS;
  return base;
}
```

**评分公式**：`score = confidence × min(streak, 5) × (reputation / 100) + semantic_bonus`

**Evolver 为什么这样做**：
- **streak 封顶 = 5**：防止"一个方案被重复使用100次导致 streak=100 评分爆炸"
- **reputation 归一化**：外部置信度归一化到 0-1 范围

### 14.5 阈值与命中判定

**文件**: `hubSearch.js:170-190`

```javascript
const DEFAULT_MIN_REUSE_SCORE = 0.72;  // 默认阈值 0.72

function pickBestMatch(results, threshold) {
  for (const asset of results) {
    if (asset.status !== 'promoted') continue;  // 只考虑已发布的
    const s = scoreHubResult(asset);
    if (s > bestScore && s >= threshold) { ... }
  }
  if (!best || bestScore < threshold) return null;
  return { match: best, score, mode: reuseMode };
}
```

**`status=promoted` 门控**：只有经过 Hub 评审并被 **promoted** 的资产才能被复用。

### 14.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| Search-First 策略 | Hub 有则复用，无则本地 | **高优先级**: BlueCortexCE 的 context generate 可先查"是否有现成答案"，无则 LLM 生成 |
| 两阶段搜索 | metadata 免费 → 选择 → fetch | **高优先级**: BlueCortexCE 可以先做轻量搜索，再按需调用昂贵 LLM |
| 搜索缓存 | 5min TTL + LRU | **高优先级**: BlueCortexCE 的搜索结果应缓存 |
| Streak 封顶 | min(streak, 5) | **高优先级**: 任何"计数"类评分都应有上限 |
| status=promoted | Hub 评审通过的资产才可用 | **中优先级**: BlueCortexCE 可以对高频检索结果做"预热缓存" |

---

## 15. a2aProtocol.js — Agent-to-Agent 通信协议（v0.3 新增）

**文件**: `src/gep/a2aProtocol.js` (1221 lines)

### 15.1 协议概览

Evolver 的 A2A 协议定义了 Agent 之间交换 Assets（Gene/Capsule）的标准方式：

| 消息类型 | 用途 | 方向 |
|----------|------|------|
| `hello` | 节点发现和能力广告 | 节点 → Hub |
| `publish` | 发布资产到 Hub | 节点 → Hub |
| `fetch` | 请求指定资产 | 节点 → Hub |
| `report` | 发送验证报告 | 节点 → Hub |
| `decision` | Hub 接受/拒绝/隔离决定 | Hub → 节点 |
| `revoke` | 撤回已发布资产 | 节点 → Hub |

### 15.2 Node ID 生成

**文件**: `a2aProtocol.js:20-65`

```javascript
function getNodeId() {
  // 1. 环境变量优先
  if (process.env.A2A_NODE_ID) return process.env.A2A_NODE_ID;
  // 2. 本地持久化文件（~/.evomap/node_id）
  const persisted = _loadPersistedNodeId();
  if (persisted) return persisted;
  // 3. 从设备指纹 + agent name + cwd 计算
  const raw = deviceId + '|' + agentName + '|' + process.cwd();
  const computed = 'node_' + sha256(raw).slice(0, 12);
  _persistNodeId(computed);  // 持久化
  return computed;
}
```

**Evolver 为什么这样做**：
- `cwd` 让同一机器不同目录的 Agent 有不同 ID（多租户隔离）
- 本地持久化避免每次运行重新计算

### 15.3 HMAC 签名发布

**文件**: `a2aProtocol.js:200-240`

```javascript
function buildPublish(opts) {
  const assetIdVal = asset.asset_id || computeAssetId(asset);
  const nodeSecret = getHubNodeSecret();
  const signature = crypto.createHmac('sha256', nodeSecret).update(assetIdVal).digest('hex');
  return { protocol: 'gep-a2a', message_type: 'publish', ..., payload: { asset, signature } };
}
```

**Evolver 为什么这样做**：Hub 需要验证"谁在发布"——HMAC 签名让 Hub 可以验证消息确实来自该节点。

### 15.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| Node ID 持久化 | 本地文件 + 环境变量优先 | **高优先级**: BlueCortexCE 的 client SDK 应支持 node_id 持久化 |
| HMAC 签名 | 发布消息的来源验证 | **中优先级**: BlueCortexCE 的 API 可以验证请求来源 |
| 多租户隔离 | cwd 哈希区分同一机器不同目录 | **中优先级**: BlueCortexCE 的 workspace 隔离可用 cwd 作为维度之一 |

---

## 16. llmReview.js — LLM 辅助代码审查（v0.3 新增）

**文件**: `src/gep/llmReview.js` (93 lines)

### 16.1 环境门控

```javascript
function isLlmReviewEnabled() {
  return String(process.env.EVOLVER_LLM_REVIEW || '').toLowerCase() === 'true';
}
```

默认禁用。只有设置 `EVOLVER_LLM_REVIEW=true` 时才启用。

### 16.2 审查 Prompt 模板

**文件**: `llmReview.js:15-45`

```javascript
function buildReviewPrompt({ diff, gene, signals, mutation }) {
  return `You are reviewing a code change produced by an autonomous evolution engine.

## Context
- Gene: ${geneId} (${category})
- Signals: [${signalsList}]
- Rationale: ${rationale}

## Diff
${diffPreview}

## Review Criteria
1. Does this change address the stated signals?
2. Are there any obvious regressions or bugs introduced?
3. Is the blast radius proportionate to the problem?
4. Are there any security or safety concerns?

## Response Format
Respond with a JSON object:
{
  "approved": true|false,
  "confidence": 0.0-1.0,
  "concerns": ["..."],
  "summary": "one-line review summary"
}`;
}
```

### 16.3 当前实现状态

**重要发现**：当前的 `runLlmReview` 实现**实际上是空操作**——auto-approve：

```javascript
const reviewScript = `
  console.log(JSON.stringify({ approved: true, confidence: 0.7,
    concerns: [], summary: 'auto-approved (no external LLM configured)' }));
`;
const result = execFileSync(process.execPath, ['-e', reviewScript, tmpFile], ...);
return JSON.parse(result.trim());
```

**Evolver 为什么这样做**：llmReview.js 是**框架预留**的接口，当前没有连接真正的 LLM provider。auto-approve 确保禁用 LLM review 时不会阻断进化流程。

### 16.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 环境门控 | EVOLVER_LLM_REVIEW=true 才启用 | **高优先级**: BlueCortexCE 的任何 LLM 增强功能应有环境开关 |
| 结构化 JSON 审查 | approved/confidence/concerns/summary | **高优先级**: BlueCortexCE 的任何 LLM 审查应有标准 JSON 格式 |
| 空操作的优雅降级 | auto-approve + non-fatal | **高优先级**: LLM 不可用时，BlueCortexCE 应 fallback，而非报错 |
| Prompt 模板化 | Context/Diff/Review Criteria/Response Format | **中优先级**: BlueCortexCE 的 LLM 审查 prompt 应结构化且可配置 |

---

## 17. reflection.js — 战略反思机制（v0.3 新增）

**文件**: `src/gep/reflection.js` (179 lines)

### 17.1 核心设计原则

reflection.js 是 Evolver 的**元认知层**——不是处理具体的代码变更，而是**审视进化过程本身**：
- 最近 10 个 cycle 的成功/失败统计
- 基因选择策略是否陷入局部最优
- 是否存在被忽视的信号
- 是否需要调整进化参数（creativity/rigor/risk_tolerance）

### 17.2 自适应反思间隔

**文件**: `reflection.js:10-40`

```javascript
const REFLECTION_INTERVAL_DEFAULT = 5;   // 默认每 5 cycle 反思一次
const REFLECTION_INTERVAL_SUCCESS = 8;   // 连续成功 → 降低频率
const REFLECTION_INTERVAL_FAILURE = 3;   // 连续失败 → 提高频率
const REFLECTION_COOLDOWN_MS = 30 * 60 * 1000;  // 两次反思至少间隔 30 分钟

function computeReflectionInterval(recentEvents) {
  const tail = events.slice(-3);
  if (tail.allSuccess) return REFLECTION_INTERVAL_SUCCESS;   // 更少反思
  if (tail.allFailed) return REFLECTION_INTERVAL_FAILURE;     // 更多反思
  return REFLECTION_INTERVAL_DEFAULT;
}
```

**Evolver 为什么这样做**：连续成功说明进化状态良好，不需要频繁干预；连续失败说明环境可能发生了变化，需要更频繁地审视策略。

### 17.3 反思触发条件

**文件**: `reflection.js:40-60`

```javascript
function shouldReflect({ cycleCount, recentEvents }) {
  const interval = computeReflectionInterval(recentEvents);

  // 1. cycleCount % interval === 0（周期性触发）
  if (cycleCount % interval !== 0) return false;

  // 2. 距离上次反思至少 30 分钟（防止过于频繁）
  if (Date.now() - stat.mtimeMs < REFLECTION_COOLDOWN_MS) return false;

  return true;
}
```

### 17.4 反思上下文构建

**文件**: `reflection.js:70-130`

```javascript
function buildReflectionContext({ recentEvents, signals, memoryAdvice, narrative }) {
  // 输出：
  // ## Recent Cycle Statistics (last 10)
  // - Success: 7, Failed: 3
  // - Intent distribution: {"repair":5,"optimize":3,"innovate":2}
  // - Gene usage: {"error-handling-v2":4,"perf-v1":3,...}
  //
  // ## Questions to Answer
  // 1. Are there persistent signals being ignored?
  // 2. Is the gene selection strategy optimal?
  // 3. Should the balance between repair/optimize/innovate shift?
  // 4. Are there capability gaps that no current gene addresses?
  // 5. What single strategic adjustment would have the highest impact?
}
```

### 17.5 战略调整建议生成

**文件**: `reflection.js:55-70`

```javascript
function buildSuggestedMutations(signals) {
  const muts = [];
  if (hasStagnation)  muts.push({ param: 'creativity',    delta: +0.05 });
  if (hasError)       muts.push({ param: 'rigor',          delta: +0.05 });
  if (hasGap)         muts.push({ param: 'risk_tolerance', delta: +0.05 });
  return muts.slice(0, 2);  // 每次最多返回 2 个调整建议
}
```

**Evolver 为什么这样做**：反思不是要大幅改变策略，而是做**微调**——+0.05 的小步调整避免从一个极端跳到另一个极端。

### 17.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 自适应反思间隔 | 连续成功→慢反思，连续失败→快反思 | **高优先级**: BlueCortexCE 的巡检任务应根据健康状态动态调整频率 |
| 元认知层 | 审视进化过程本身，而非具体变更 | **高优先级**: BlueCortexCE 应有"系统审视自己工作"的能力 |
| 冷却期保护 | 两次反思至少 30 分钟 | **中优先级**: BlueCortexCE 的任何定期任务应有最小间隔保护 |
| 策略微调 | 每次最多 2 个 +0.05 调整 | **高优先级**: BlueCortexCE 的自适应参数调整应"小步慢走" |
| 聚合统计 | Intent distribution + Gene usage | **高优先级**: BlueCortexCE 应统计 API 使用模式 |

---

## 待进一步确认

1. ✅ ~~memoryGraph 查询性能~~ — JSONL 全量扫描，200条聚合，够用但大型化需优化
2. ✅ ~~solidify.js 验证机制~~ — **已详细分析**（PRM + Canary + FailedCapsule + Epigenetic）
3. ✅ ~~selector.js 选择算法~~ — **已详细分析**（三层评分 + 漂移强度 + Learning boost）
4. ✅ ~~curriculum.js 学习课程~~ — **已详细分析**（frontier/mastered/failing 三层）
5. ✅ ~~skillDistiller.js 提炼机制~~ — **已详细分析**（pattern analysis + coverage gaps）
6. ✅ ~~hubSearch.js~~ — **已详细分析**（两阶段搜索 + 缓存 + 评分）
7. ✅ ~~a2aProtocol.js~~ — **已详细分析**（A2A 消息类型 + HMAC 签名 + Node ID）
8. ✅ ~~policyCheck.js~~ — **已详细分析**（blast radius + 约束策略 + 伦理检测）
9. ✅ ~~llmReview.js~~ — **已详细分析**（环境门控 + JSON 审查格式）
10. ✅ ~~reflection.js~~ — **已详细分析**（自适应间隔 + 战略反思）
11. ✅ ~~mutation.js~~ — **已详细分析**（categoryFromContext + Opportunity signals + repair/innovate/optimize 三分类）
12. ✅ ~~solidify.js 表观遗传~~ — **已补充分析**（adaptGeneFromLearning + 成功时扩展 signals_match + 失败时记录 anti_patterns）
13. ✅ ~~evolve.js 主循环~~ — **已详细分析**（完整 6 阶段：预检→信号提取→Curriculum→Hub搜索→Memory推理→反思）
14. ✅ ~~assetStore.js~~ — **已详细分析**（JSON+JSONL 双存储 + 大文件尾读 + FailedCapsule 环缓冲）
15. ✅ ~~assetCallLog.js~~ — **已详细分析**（Hub 调用日志 + 6 种 action 类型 + 非致命设计）
16. **skillsDir 扫描** — skills 列表缓存更新机制（skills_list_cache.json）？
17. **taskReceiver.js** — Hub 任务认领和主动问题生成机制？
18. **skillPublisher.js** — 技能发布到 Hub 的完整流程？

---

## 下轮探索方向

1. **policyCheck.js** — 约束检查 + 验证命令安全（深度分析）
2. **hubSearch.js** — Hub 共享知识搜索（深度分析）
3. **a2aProtocol.js** — A2A 通信协议（深度分析）
4. **issueReporter.js** — Hub 问题上报机制
5. **envFingerprint.js** — 环境指纹与节点识别

---

## 18. evolve.js — 核心进化主循环（v0.4 新增）

**文件**: `src/evolve.js` (2076 lines)

### 18.1 完整执行阶段

Evolver 的 `run()` 函数是整个 GEP 协议的主入口编排器，按顺序执行以下阶段：

```
阶段 0: 前置检查 (runPreflightChecks)
    ↓
阶段 1: 日志扫描 + 信号提取 (extractSignals)
    ↓
阶段 2: Curriculum 课程信号注入
    ↓
阶段 3: Hub 搜索 (Search-First Evolution)
    ↓
阶段 4: Memory Graph 推理 (getMemoryAdvice)
    ↓
阶段 5: 战略反思 (shouldReflect)
    ↓
阶段 6: 基因选择 (selectGeneAndCapsule)
    ↓
阶段 7: 突变生成 + LLM 执行
    ↓
阶段 8: 固化验证 (solidify)
```

### 18.2 阶段 1：信号提取（核心代码）

**文件**: `evolve.js:1275-1310`

```javascript
const signals = extractSignals({
  recentSessionTranscript: recentMasterLog,  // 完整会话历史
  todayLog,                                  // 今日日志
  memorySnippet,                             // MEMORY.md 摘要
  userSnippet,                               // USER.md 摘要
  recentEvents,                              // 最近 80 条 EvolutionEvent
});
```

**关键输入**：
- `recentMasterLog` — 从 OpenClaw/Claude Code/Cursor 等格式的会话日志中提取内容
- `recentEvents` — 来自 `assetStore.readAllEvents()`，过滤 `type==='EvolutionEvent'` 的最近 80 条

**Dormant Hypothesis 注入**：如果上次 cycle 因 backoff 中断，会将中断时的 signals 重新注入当前 cycle。

### 18.3 阶段 2：Curriculum 信号注入

**文件**: `evolve.js:1335-1355`

```javascript
var curriculumSignals = generateCurriculumSignals({
  capabilityGaps: earlyCapGaps,
  memoryGraphPath: memGraphPath,
  personality: {},
});
for (var ci = 0; ci < curriculumSignals.length; ci++) {
  if (!signals.includes(curriculumSignals[ci])) {
    signals.push(curriculumSignals[ci]);
  }
}
```

**Evolver 为什么这样做**：Curriculum 层主动注入"能力缺口"信号，引导进化向未掌握领域探索。

### 18.4 阶段 3：Hub 搜索（Search-First Evolution）

**文件**: `evolve.js:1658-1685`

```javascript
const hasProblemSignal = signals.some(s =>
  ['log_error','recurring_error','capability_gap','perf_bottleneck',
   'test_failure','deployment_issue'].includes(s) || s.startsWith('errsig:')
);

// 问题信号存在时，降低阈值 + 延长超时
const hubSearchOpts = hasProblemSignal
  ? { timeoutMs: 12000, threshold: 0.55 }
  : { timeoutMs: 8000 };

hubHit = await hubSearch(signals, hubSearchOpts);
```

**Evolver 为什么这样做**：问题信号存在时优先搜索 Hub，避免重复发明轮子。降低阈值扩大匹配范围，延长超时给 Hub 充足响应时间。

### 18.5 阶段 4：Memory Graph 推理

**文件**: `evolve.js:1686-1692`

```javascript
memoryAdvice = getMemoryAdvice({ signals, genes, driftEnabled: IS_RANDOM_DRIFT });
```

**返回结构**：
```javascript
{
  preferredGeneId: 'error-handling-v2',  // 推荐基因
  bannedGeneIds: ['unstable-gene-v1'],   // 禁用基因
  explanation: '...'                      // 推理说明
}
```

### 18.6 阶段 5：战略反思

**文件**: `evolve.js:1699-1715`

```javascript
if (shouldReflect({ cycleCount, recentEvents })) {
  const reflectionCtx = buildReflectionContext({ recentEvents, signals, memoryAdvice, narrative });
  recordReflection({ ... });
}
```

**反思触发条件**（在 `reflection.js` 中定义）：
- 周期性：`cycleCount % interval === 0`（间隔由 `computeReflectionInterval` 动态计算）
- 冷却期：距上次反思至少 30 分钟

### 18.7 阶段 6：基因选择

**文件**: `evolve.js:1737-1750`

```javascript
const { selectedGene, capsuleCandidates, selector } = selectGeneAndCapsule({
  genes, capsules, signals, memoryAdvice,
  driftEnabled: IS_RANDOM_DRIFT,
  failedCapsules: recentFailedCapsules,
  capabilityGaps: heartbeatCapGaps,
  noveltyScore: heartbeatNovelty,
});
```

### 18.8 空闲门控（Idle Gating）

**文件**: `evolve.js:60-90`

当检测到饱和信号（`evolution_saturation`、`empty_cycle_loop_detected` 等）时，Hub 搜索被节流：

```javascript
function shouldSkipHubCalls(signals) {
  // 有饱和信号但没有可操作信号 → 跳过
  // 下次 Hub 调用至少间隔 EVOLVER_IDLE_FETCH_INTERVAL_MS（默认 10 分钟）
}
```

**Evolver 为什么这样做**：进化饱和时，继续调用 Hub 是浪费 credits，需要等待环境变化。

### 18.9 多 Session 来源支持

**文件**: `evolve.js:140-200`

Evolver 支持多种 Agent 会话格式的自动检测：

| 格式 | 检测方式 | 提取逻辑 |
|------|---------|---------|
| OpenClaw | `type==='message'` | `message.role` + `message.content` |
| Claude Code | `type==='user'\|'assistant'` | `message.content` |
| Cursor | `role && message` (无 type) | `role` + `message.content` |
| Codex CLI | `type==='item.added'\|'item.completed'` | `item.type` 区分 message/function_call |
| Manus | `type==='user_message'\|'assistant_message'` | 直接 content |

**工具调用提取**：
```javascript
// OpenClaw格式: [TOOL: Shell]
const m = line.match(/\[TOOL:\s*([^\]]+)\]/i);
// Cursor格式: [Tool call] Shell
const m2 = line.match(/\[Tool call\]\s+(\S+)/i);
```

### 18.10 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| Search-First Evolution | 问题信号存在时优先搜 Hub | **高优先级**: BlueCortexCE 的 API 应支持"搜索历史最优解" |
| 多格式会话解析 | 自动检测 5 种 Agent 格式 | **高优先级**: BlueCortexCE 的 API 应接受多种会话格式 |
| 空闲门控 | 饱和时降低 Hub 调用频率 | **中优先级**: BlueCortexCE 可实现"冷却期"机制避免重复调用 |
| Dormant Hypothesis | 中断恢复时重新注入 signals | **高优先级**: BlueCortexCE 应支持会话中断恢复 |
| Curriculum 信号注入 | 主动探索能力缺口 | **中优先级**: BlueCortexCE 的巡检可主动发现知识盲区 |
| skills 目录缓存 | 6 小时 TTL + mtime 比对 | **中优先级**: BlueCortexCE 可缓存 skills 列表 |

---

## 19. assetStore.js — 基因/胶囊/事件持久化（v0.4 新增）

**文件**: `src/gep/assetStore.js` (369 lines)

### 19.1 双重存储架构

Evolver 对 Genes 和 Capsules 采用 **JSON + JSONL 双重存储**：

| 资产 | JSON (主存储) | JSONL (追加记录) | 去重策略 |
|------|-------------|-----------------|---------|
| Genes | `genes.json` (数组) | `genes.jsonl` (每条一条) | ID 唯一，Map 去重 |
| Capsules | `capsules.json` (数组) | `capsules.jsonl` (每条一条) | ID 唯一，Map 去重 |
| Events | — | `events.jsonl` (append-only) | 无去重，纯追加 |
| Candidates | — | `candidates.jsonl` (append-only) | 无去重，纯追加 |

**文件路径**：
```javascript
function genesPath()        { return path.join(getGepAssetsDir(), 'genes.json'); }
function capsulesJsonlPath(){ return path.join(getGepAssetsDir(), 'capsules.jsonl'); }
function eventsPath()      { return path.join(getGepAssetsDir(), 'events.jsonl'); }
```

### 19.2 大文件尾读优化

**文件**: `assetStore.js:205-235`

```javascript
// 文件 < 1MB: 全量读
if (stat.size < 1024 * 1024) {
  const raw = fs.readFileSync(p, 'utf8');
  const lines = raw.split('\n').map(l => l.trim()).filter(Boolean);
  return lines.slice(-limit).map(l => JSON.parse(l));
}

// 文件 >= 1MB: 只读末尾 chunk
const chunkSize = Math.min(stat.size, limit * 4096);
const buf = Buffer.alloc(chunkSize);
fs.readSync(fd, buf, 0, chunkSize, stat.size - chunkSize);
```

**Evolver 为什么这样做**：Candidates.jsonl 可能快速膨胀（每次 cycle 追加）。超过 1MB 时只读末尾，避免 OOM。

### 19.3 FailedCapsule 环缓冲

**文件**: `assetStore.js:310-330`

```javascript
const FAILED_CAPSULES_MAX = 200;     // 上限
const FAILED_CAPSULES_TRIM_TO = 100; // 触发裁剪时保留量

function appendFailedCapsule(capsuleObj) {
  list.push(capsuleObj);
  if (list.length > FAILED_CAPSULES_MAX) {
    list = list.slice(list.length - FAILED_CAPSULES_TRIM_TO);  // 保留最新的 100 条
  }
  writeJsonAtomic(failedCapsulesPath(), ...);
}
```

### 19.4 Asset 文件初始化保障

**文件**: `assetStore.js:340-365`

```javascript
function ensureAssetFiles() {
  const files = [
    { path: genesPath(), defaultContent: JSON.stringify(getDefaultGenes()) + '\n' },
    { path: capsulesPath(), defaultContent: JSON.stringify(getDefaultCapsules()) + '\n' },
    { path: eventsPath(), defaultContent: '' },  // 空文件
    { path: candidatesPath(), defaultContent: '' },
    { path: failedCapsulesPath(), defaultContent: JSON.stringify(getDefaultFailedCapsules()) + '\n' },
  ];
  // 不存在则创建，防止外部 grep/cat 命令失败
}
```

### 19.5 Schema 字段强制

**文件**: `assetStore.js:244-260`

```javascript
function ensureSchemaFields(obj) {
  if (!obj.schema_version) obj.schema_version = SCHEMA_VERSION;
  if (!obj.asset_id) obj.asset_id = computeAssetId(obj);
  return obj;
}
```

### 19.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 大文件尾读 | >1MB 只读末尾 chunk | **高优先级**: BlueCortexCE 的 JSONL 日志表应实现类似优化 |
| FailedCapsule 环缓冲 | 200 上限，触发时裁剪到 100 | **中优先级**: BlueCortexCE 的"失败记录"应有上限保护 |
| ensureAssetFiles | 启动时确保所有文件存在 | **低优先级**: BlueCortexCE 不需要这种防护 |
| Schema 字段强制 | 写入前自动填充 schema_version + asset_id | **高优先级**: BlueCortexCE 的 API 应有 Schema 校验 |

---

## 20. assetCallLog.js — Hub 调用审计（v0.4 新增）

**文件**: `src/gep/assetCallLog.js` (131 lines)

### 20.1 记录类型体系

**文件**: `assetCallLog.js:30-50`

```javascript
const actionTypes = [
  'hub_search_hit',     // Hub 搜索命中
  'hub_search_miss',    // Hub 搜索未命中
  'asset_reuse',        // 复用外部资产
  'asset_reference',    // 引用外部资产
  'asset_publish',      // 发布资产到 Hub
  'asset_publish_skip', // 跳过发布
];
```

### 20.2 非致命设计

**文件**: `assetCallLog.js:40-50`

```javascript
function logAssetCall(entry) {
  try {
    fs.appendFileSync(logPath, JSON.stringify(record) + '\n', 'utf8');
  } catch (e) {
    // Non-fatal: never block evolution for logging failure
  }
}
```

**Evolver 为什么这样做**：Hub 调用日志是审计用途，不应阻塞主流程。即使磁盘满或权限问题，也应该继续进化。

### 20.3 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 审计日志 | 记录 6 种 Hub 交互类型 | **高优先级**: BlueCortexCE 应记录 API 调用的审计日志 |
| 非致命 | 日志失败不阻塞主流程 | **高优先级**: BlueCortexCE 的所有辅助功能（监控/日志）都应非致命 |

---

## 21. mutation.js — 突变分类与风险（v0.4 新增）

**文件**: `src/gep/mutation.js` (188 lines)

### 21.1 突变类别推断

**文件**: `mutation.js:45-70`

```javascript
function mutationCategoryFromContext({ signals, driftEnabled }) {
  if (hasErrorishSignal(signals))      return 'repair';    // 有错误 → 修复
  if (driftEnabled)                   return 'innovate';  // 显式漂移 → 创新
  if (hasOpportunitySignal(signals))   return 'innovate';  // 机会信号 → 创新
  if (strategy.innovate >= 0.5)       return 'innovate';  // 策略倾向创新
  return 'optimize';                               // 默认优化
}
```

### 21.2 Opportunity Signals 白名单

**文件**: `mutation.js:20-35`

```javascript
const OPPORTUNITY_SIGNALS = [
  'user_feature_request',
  'user_improvement_suggestion',
  'perf_bottleneck',
  'capability_gap',
  'stable_success_plateau',     // 成功 plateau 也是机会（需突破）
  'external_opportunity',
  'issue_already_resolved',     // 已解决也是机会（可泛化）
  'openclaw_self_healed',       // 自愈也是机会
  'empty_cycle_loop_detected',
];
```

**Evolver 为什么这样做**：机会信号不是只有"用户请求"，还包括"成功 plateau"、"自愈"等看似正面的情况——这些都可能是创新的契机。

### 21.3 预期效果声明

**文件**: `mutation.js:75-85`

```javascript
function expectedEffectFromCategory(category) {
  if (category === 'repair')    return 'reduce runtime errors, increase stability';
  if (category === 'optimize')  return 'improve success rate and reduce repeated operational cost';
  if (category === 'innovate')  return 'explore new strategy combinations to escape local optimum';
}
```

### 21.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 三分类决策树 | repair/innovate/optimize 明确区分 | **高优先级**: BlueCortexCE 的 Observation 应支持分类（修复/优化/创新） |
| 机会信号扩展 | plateau/自愈也是机会 | **高优先级**: BlueCortexCE 的信号体系应包含"正面机会"类型 |
| 预期效果声明 | 每个 category 都有明确的效果描述 | **低优先级**: BlueCortexCE 作为服务不需要这个 |

---

## 22. solidify.js — 表观遗传机制补充（v0.4 新增）

### 22.1 Gene 的学习历史（Epigenetic Learning）

**文件**: `solidify.js:130-200`

```javascript
function adaptGeneFromLearning({ gene, outcomeStatus, learningSignals, failureMode }) {
  // 成功时：扩展 signals_match（学习新的触发条件）
  if (outcomeStatus === 'success') {
    for (const sig of learningSignals) {
      if (sig.startsWith('problem:') || sig.startsWith('area:')) {
        gene.signals_match.push(sig);  // 自动扩展匹配范围
      }
    }
    gene.learning_history.push({
      outcome: 'success',
      mode: failureMode.mode,
      learning_signals: learningSignals.slice(0, 12),
    });
  }

  // 失败时：记录 anti_pattern
  if (outcomeStatus === 'failed') {
    gene.anti_patterns.push({
      at: nowIso(),
      mode: failureMode.mode,
      reason_class: failureMode.reasonClass,
      learning_signals: learningSignals.slice(0, 8),
    });
    gene.learning_history.push({ outcome: 'failed', ... });
  }

  // 限制历史长度（最多 20 条学习记录，12 条反模式）
  if (gene.learning_history.length > 20) gene.learning_history.slice(-20);
  if (gene.anti_patterns.length > 12) gene.anti_patterns.slice(-12);

  return gene;
}
```

**Evolver 为什么这样做**：Gene 不是静态的——每次成功/失败都会微调其触发条件和反模式库。这是"表观遗传"的核心：环境直接修改 Gene 的表达能力。

### 22.2 表观遗传 vs 基因突变

| 维度 | 基因突变 (Mutation) | 表观遗传 (Epigenetic) |
|------|-------------------|---------------------|
| 触发 | 显式策略决策 | 进化结果反馈 |
| 范围 | Gene 核心策略变更 | signals_match 扩展 + anti_patterns 记录 |
| 可逆性 | 不可逆 | 可通过后续成功覆盖 |
| 速度 | 慢（需要 LLM 生成） | 快（自动追加） |

### 22.3 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 成功时扩展 signals_match | 自动学习新触发条件 | **高优先级**: BlueCortexCE 可对"有效检索模式"进行自动扩展 |
| 失败时记录 anti_patterns | 记录"什么情况下失败" | **高优先级**: BlueCortexCE 应记录"无效检索"的上下文 |
| 历史上限保护 | 20 条学习 + 12 条反模式 | **高优先级**: BlueCortexCE 的学习历史应有上限 |
| Learning Signals 格式 | `problem:` / `area:` 前缀 | **中优先级**: BlueCortexCE 的元数据可用前缀区分类型 |

---

## 23. candidates.js — 能力候选提取（v0.4 新增）

**文件**: `src/gep/candidates.js` (208 lines)

### 23.1 候选提取流程

**文件**: `candidates.js:50-120`

```javascript
function extractCapabilityCandidates({ recentSessionTranscript, signals, recentFailedCapsules }) {
  // 1. 从 Session Transcript 中提取工具调用
  const toolCalls = extractToolCalls(transcript);
  const toolFreq = countFreq(toolCalls);

  // 2. 从 Failed Capsules 中提取失败模式
  const failurePatterns = recentFailedCapsules.map(c => c.failure_mode).filter(Boolean);

  // 3. 生成候选对象
  return newCandidates;
}
```

### 23.2 工具调用提取（多格式支持）

**文件**: `candidates.js:20-45`

```javascript
function extractToolCalls(transcript) {
  // OpenClaw格式: [TOOL: Shell]
  const m = line.match(/\[TOOL:\s*([^\]]+)\]/i);
  // Cursor格式: [Tool call] Shell
  const m2 = line.match(/\[Tool call\]\s+(\S+)/i);
}
```

### 23.3 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 工具调用频率统计 | 从 Session 中提取工具调用模式 | **高优先级**: BlueCortexCE 可统计"有效 API 调用模式" |
| 失败模式提取 | 从 FailedCapsule 中提取 failure_mode | **高优先级**: BlueCortexCE 的 Observation 应包含 failure_mode 字段 |
| 候选预览渲染 | `renderCandidatesPreview(recent, 1600)` 限制 1600 chars | **中优先级**: BlueCortexCE 的摘要应有长度上限 |

---

## 下轮探索方向

1. **idleScheduler.js** — 空闲调度机制
2. **reflection.js** — 战略反思机制（待补充深度分析）
3. **localStateAwareness.js** — 本地状态感知


---

