# Capability Candidate 生命周期管线：从会话信号到可执行基因

**文档版本**: v51-0.1-draft
**数据来源**: `src/gep/candidates.js` + `src/gep/candidateEval.js` + `src/gep/skillDistiller.js` + `src/gep/questionGenerator.js` + `src/gep/hubSearch.js`
**目标**: 将 EvoMap/evolver 中散落于多个文件的 capability candidate 生命周期，作为**端到端管线**统一分析。提炼"信号→候选提取→Hub匹配→本地评估→技能提炼→悬赏提问"五阶段机制，为 BlueCortexCE 的**observation→可行动知识单元**转化路径提供借鉴。
**最后更新**: 2026-04-25

---

## §1 核心命题：为什么需要 Capability Candidate 管线

Evolver 的记忆系统面临一个根本问题：**observation（观察）≠ 可执行能力（actionable capability）**。

MemoryGraph 记录了"发生了什么"，Gene Pool 提供了"怎么做"的行为模板。但两者之间存在一个缺口：**如何从真实失败的模式中，自动提炼出新的行为策略**？

Capability Candidate 管线就是这座桥：

```
Session Transcript + Signals
        ↓
extractCapabilityCandidates()     ← candidates.js
        ↓
buildCandidatePreviews()          ← candidateEval.js
        ↓
┌───────┴───────┐
↓               ↓
hubSearch()    skillDistiller()
(外部匹配)      (本地提炼)
        ↓               ↓
  复用外部Gene    生成新Gene/Capsule
        ↓               ↓
questionGenerator() ← 悬赏提问（外部求解）
```

---

## §2 Stage 1：候选提取（`candidates.js`）

### 2.1 三类来源

`extractCapabilityCandidates()` 从三个来源提取候选：

#### 来源 A：高频工具调用

```javascript
function extractToolCalls(transcript) {
  // OpenClaw format: [TOOL: Shell]
  const m = line.match(/\[TOOL:\s*([^\]]+)\]/i);
  // Cursor format: [Tool call] Shell
  const m2 = line.match(/\[Tool call\]\s+(\S+)/i);
}
```

统计工具调用频率，**≥3 次**的工具调用被标记为候选（说明 agent 习惯性依赖某个工具）。

#### 来源 B：信号模式

```javascript
const signalCandidates = [
  { signal: 'log_error', title: 'Repair recurring runtime errors' },
  { signal: 'protocol_drift', title: 'Prevent protocol drift and enforce auditable outputs' },
  { signal: 'windows_shell_incompatible', title: 'Avoid platform-specific shell assumptions' },
  { signal: 'session_logs_missing', title: 'Harden session log detection and fallback behavior' },
  { signal: 'user_feature_request', title: 'Implement user-requested feature' },
  { signal: 'perf_bottleneck', title: 'Resolve performance bottleneck' },
  { signal: 'capability_gap', title: 'Fill capability gap' },
  { signal: 'stable_success_plateau', title: 'Explore new strategies during stability plateau' },
  { signal: 'external_opportunity', title: 'Evaluate external A2A asset for local adoption' },
];
```

每个 signal 对应一个 capability shape。

#### 来源 C：失败胶囊模式聚类

```javascript
// Group failed capsules by dominant problem category
const problemPriority = [
  'problem:performance',
  'problem:protocol',
  'problem:reliability',
  'problem:stagnation',
  'problem:capability',
];

// Count failed capsules per problem type
if (group.count >= 2) {
  candidates.push({
    type: 'CapabilityCandidate',
    title: 'Learn from recurring failed evolution paths',
    source: 'failed_capsules',
    tags: group.tags,  // problem:*, risk:*, area:*
  });
}
```

**关键**：不是从单个失败胶囊提炼，而是对**多个相似失败**做聚类，识别系统性弱点。

### 2.2 Capability Shape 结构

每个候选携带一个 `shape`（五问结构）：

```javascript
function buildFiveQuestionsShape({ title, signals, evidence }) {
  return {
    title: String(title || '').slice(0, 120),
    input: 'Recent session transcript + memory snippets + user instructions',
    output: 'A safe, auditable evolution patch guided by GEP assets',
    invariants: 'Protocol order, small reversible patches, validation, append-only events',
    params: `Signals: ${signals.join(', ')}`,
    failure_points: 'Missing signals, over-broad changes, skipped validation, missing knowledge solidification',
    evidence: clip(evidence, 240),
  };
}
```

这个 shape 是**脱敏后的能力描述**，可以发给 Hub 或 LLM 而不泄露敏感信息。

---

## §3 Stage 2：候选预览与持久化（`candidateEval.js`）

### 3.1 双候选池

```
本地候选池: candidates.jsonl      ← 从本次会话提取的候选
外部候选池: external_candidates.jsonl ← Hub 推送的候选（通过 a2aProtocol）
```

`buildCandidatePreviews()` 同时处理两个池：

```javascript
const { buildCandidatePreviews } = require('./candidateEval');

// 输出：
// - capabilityCandidatesPreview: 本地候选的渲染文本（用于 prompt injection）
// - externalCandidatesPreview: 外部候选的匹配列表（按 signal match 评分排序）
// - newCandidates: 本次新增的候选数组
```

### 3.2 外部候选匹配算法

```javascript
const matchedExternalGenes = genesOnly
  .map(g => {
    const pats = Array.isArray(g.signals_match) ? g.signals_match : [];
    const hit = pats.reduce((acc, p) => (matchPatternToSignals(p, signals) ? acc + 1 : acc), 0);
    return { gene: g, hit };
  })
  .filter(x => x.hit > 0)
  .sort((a, b) => b.hit - a.hit)
  .slice(0, 3);

const matchedExternalCapsules = capsulesOnly
  .map(c => {
    const triggers = Array.isArray(c.trigger) ? c.trigger : [];
    const score = triggers.reduce((acc, t) => (matchPatternToSignals(t, signals) ? acc + 1 : acc), 0);
    return { capsule: c, score };
  })
  .filter(x => x.score > 0)
  .sort((a, b) => b.score - a.score)
  .slice(0, 3);
```

外部候选按 **signal match count** 排序，取 top 3。

### 3.3 持久化策略

```javascript
for (const c of newCandidates) {
  appendCandidateJsonl(c);  // 幂等追加，不覆盖
}
```

每个候选只追加一次（`id` 去重），不修改历史。

---

## §4 Stage 3：Hub 搜索匹配（`hubSearch.js`）

### 4.1 Search-First 策略

**两种模式**：

| 模式 | 行为 | 适用场景 |
|------|------|---------|
| `direct` | 跳过本地推理，直接使用 Hub 资产 | 高置信度外部匹配 |
| `reference` | 将 Hub 资产作为 hint 注入 prompt | 低置信度，需要本地验证 |

### 4.2 两阶段搜索（成本最小化）

**Phase 1: Search-only（免费）**
```javascript
const searchMsg = buildFetch({ signals: signalList, searchOnly: true });
// 返回：{ results: [{ asset_id, confidence, success_streak, reputation_score, status }] }
```

**Phase 2: Payload fetch（按需付费）**
```javascript
// 仅对 best match 执行 Phase 2
const fetchMsg = buildFetch({ assetIds: [selectedAssetId] });
// 返回：完整 asset payload（strategy, validation, constraints 等）
```

**Phase 1 结果缓存**：5 分钟 TTL，LRU 驱逐（上限 200 条）。

**Phase 2 Payload 缓存**：永久（LRU 上限 100 条），因为同一个 asset 不会变化。

### 4.3 评分公式

```javascript
function scoreHubResult(asset) {
  const confidence = Number(asset.confidence) || 0;
  const streak = Math.min(Math.max(Number(asset.success_streak) || 0, 1), MAX_STREAK_CAP); // cap=5
  const reputation = Number.isFinite(repRaw) ? repRaw : 50;
  var base = confidence * streak * (reputation / 100);
  var sim = Number(asset._semantic_similarity) || 0;
  if (sim > 0) base += sim * SEMANTIC_SIMILARITY_BONUS; // +0.3 bonus
  return base;
}
```

**设计意图**：
- `success_streak` 被 cap 防止单次极端成功造成分数膨胀
- `reputation / 100` 作为归一化因子
- 语义相似度作为额外加分项（最多 +0.3）

### 4.4 并行语义搜索

```javascript
var semanticPromise = isSemanticEnabled()
  ? fetchSemanticResults(hubUrl, headers, signalList, SEMANTIC_TIMEOUT_MS)
  : Promise.resolve([]);

// 两路并行，结果合并（Dedupe by asset_id）
var settled = await Promise.allSettled([fetchPromise, semanticPromise]);
```

语义搜索走独立端点（`/a2a/assets/semantic-search`），与 Phase 1 的 keyword 搜索互补。

---

## §5 Stage 4：技能提炼（`skillDistiller.js`）

### 5.1 触发条件

```
DISTILLER_MIN_CAPSULES = 10      （至少 10 个成功胶囊才触发）
DISTILLER_INTERVAL_HOURS = 24     （每 24 小时最多一次）
DISTILLER_MIN_SUCCESS_RATE = 0.7 （平均分数 ≥ 0.7）
```

### 5.2 提炼管线

```javascript
function collectDistillationData() {
  // Step 1: 收集所有成功胶囊（outcome.status === 'success' 且 score >= 0.7）
  const successCapsules = allCapsules.filter(c =>
    c.outcome.status === 'success' && score >= DISTILLER_MIN_SUCCESS_RATE
  );

  // Step 2: 按 gene_id 分组
  const grouped = {};
  successCapsules.forEach(c => {
    const geneId = c.gene || c.gene_id;
    grouped[geneId].capsules.push(c);
    grouped[geneId].total_score += score;
  });

  // Step 3: 计算 data_hash（防止重复提炼）
  const dataHash = computeDataHash(successCapsules); // SHA-256(ids.join('|'))
}
```

### 5.3 模式分析

```javascript
function analyzePatterns(data) {
  // high_frequency: 基因被使用 ≥5 次，提取高频触发词
  // strategy_drift: 策略分布随时间变化检测
  // coverage_gaps: 从未触发过的成功胶囊（潜在未探索方向）
}
```

### 5.4 LLM 驱动的技能生成

```javascript
// 从多个胶囊提炼为单个 Gene
const distillationPrompt = [
  `Given ${capsules.length} successful evolution capsules for gene ${geneId}:`,
  capsules.map((c, i) => `${i + 1}. trigger=${c.trigger} summary=${c.summary}`).join('\n'),
  `Distill into a single Gene JSON with id=gene_distilled_${dataHash}`,
  'Include: signals_match[], preconditions[], strategy[], constraints{}, validation[]',
].join('\n\n');
```

生成结果通过 `ensureSchemaFields()` 注入 `schema_version` 和 `asset_id`（SHA-256 content hash），写入 genes.jsonl。

### 5.5 失败驱动的提炼

```javascript
const FAILURE_DISTILLER_MIN_CAPSULES = 5;
const FAILURE_DISTILLER_INTERVAL_HOURS = 12;

function distilleFromFailures(data) {
  // 收集失败胶囊，按 dominant_problem 分组
  // 生成 repair-specific Gene（gene_repair_distilled_*）
}
```

失败提炼的触发间隔（12h）比成功提炼（24h）更短——说明失败模式更需要快速响应。

---

## §6 Stage 5：悬赏提问（`questionGenerator.js`）

### 6.1 六策略问题生成

| 策略 | 触发信号 | 问题模板 |
|------|---------|---------|
| S1: 反复错误 | `recurring_error`, `high_failure_ratio` | "Recurring error: {errDetail} -- What approaches have worked?" |
| S2: 能力缺口 | `capability_gap`, `unsupported_input_type` | "Capability gap: {context} -- How can this be addressed?" |
| S3: 演化饱和 | `evolution_saturation`, `force_steady_state` | "Saturation after genes: [{genes}]. What new directions?" |
| S4: 连续失败 | `consecutive_failure_streak_≥4` | "{streak} consecutive failures. What alternatives?" |
| S5: 用户功能请求 | `user_feature_request` | "User requested: {context} -- Existing implementations?" |
| S6: 性能瓶颈 | `perf_bottleneck` | "Performance bottleneck: {context} -- Optimization patterns?" |

### 6.2 去重机制

```javascript
function isDuplicate(question, recentQuestions) {
  // 精确匹配
  if (prev === qLower) return true;
  // 模糊匹配：词汇重叠 > 70%
  var qWords = new Set(qLower.split(/\s+/).filter(w => w.length > 2));
  var pWords = new Set(prev.split(/\s+/).filter(w => w.length > 2));
  var overlap = [...qWords].filter(w => pWords.has(w)).length;
  if (overlap / Math.max(qWords.size, pWords.size) > 0.7) return true;
}
```

### 6.3 速率限制

```javascript
const MIN_INTERVAL_MS = 3 * 60 * 60 * 1000; // 两次提问至少间隔 3 小时
const MAX_QUESTIONS_PER_CYCLE = 2;            // 每次最多提 2 个问题
const recentQuestions 窗口 = 20;               // 保留最近 20 个问题用于去重
```

---

## §7 管线总览：五阶段数据流

```
Session Transcript + Signals
         │
         ▼
  ┌──────────────────────────┐
  │ candidates.js           │  Stage 1: 提取（三来源）
  │ - tool call frequency   │
  │ - signal patterns       │
  │ - failed capsule cluster│
  └──────────┬──────────────┘
             │ CapabilityCandidate[]
             ▼
  ┌──────────────────────────┐
  │ candidateEval.js        │  Stage 2: 评估 + 预览
  │ - 本地持久化 (jsonl)     │
  │ - 外部候选匹配 (top 3)  │
  │ - preview 渲染           │
  └──────┬───────────────┬───┘
         │               │
    本地候选            外部候选
         │               │
         ▼               ▼
  ┌────────────┐   ┌────────────────┐
  │skillDistil │   │ hubSearch.js   │  Stage 3: Hub 搜索
  │ -ler.js   │   │ - 两阶段搜索    │
  │ (本地提炼) │   │ - 评分 + 缓存   │
  └─────┬──────┘   └──────┬────────┘
        │                  │
   Gene/Capsule      matched Asset
        │                  │
        ▼                  ▼
  ┌─────────────────────────────────┐
  │ questionGenerator.js            │  Stage 5: 悬赏提问
  │ (未命中时 → 外部求解)           │
  └─────────────────────────────────┘
```

---

## §8 BlueCortexCE 借鉴路径

### 8.1 P0（可直接翻译）

| 发现 | Evolver 做法 | BlueCortexCE 如何借鉴 |
|------|------------|---------------------|
| Capability Shape 五问结构 | 脱敏能力描述，shape.input/output/invariants/failure_points | **高优先级**: BlueCortexCE 的 observation → 可行动建议转换，可采用类似 shape 结构 |
| 失败胶囊聚类 | 按 problem:* 标签对多个失败分组，≥2 次触发 | **高优先级**: BlueCortexCE 的 `SummaryEntity` 按 `observationType` 分组，识别反复失败模式 |
| 两阶段 Hub 搜索 | search_only 免费 → 按需付费 payload | BlueCortexCE 对外部知识库的查询可借鉴：先用低成本元数据查询，再用详细获取 |

### 8.2 P1（需要适配）

| 发现 | Evolver 做法 | 翻译方案 |
|------|------------|---------|
| 工具调用频率候选提取 | 从 transcript 提取 `[TOOL:` 标记 | BlueCortexCE 的 `ToolUseEntity` 可类似统计工具使用频率 |
| skillDistiller LLM 提炼 | 从多个胶囊生成新 Gene JSON | BlueCortexCE 的 `StructuredExtractionService`（Phase 3）可作为类似"从多个 Observation 提炼结构化 Schema"的机制 |
| 悬赏提问六策略 | 信号 → Hub bounty → 外部求解 | BlueCortexCE 可对"反复无法解决的 context"向外部知识库发起类似悬赏查询 |

### 8.3 P2（架构启发）

| 发现 | Evolver 做法 | 启发 |
|------|------------|-----|
| 候选持久化（jsonl） | 本地候选与外部候选双池分离 | BlueCortexCE 的 `pending_messages` 机制可作为候选暂存 |
| data_hash 防重复提炼 | SHA-256(ids.join('\|')) 防止相同胶囊集合重复提炼 | BlueCortexCE 的 contentHash 机制（doc 39）已实现类似能力 |
| 外部候选按 signal match 排序 | `matchPatternToSignals(p, signals)` 计数排序 | BlueCortexCE 的 `SearchService` 可借鉴信号匹配评分机制 |

---

## §9 与其他文档的边界

| 本文档聚焦 | 相关文档 |
|-----------|---------|
| 候选生命周期管线（端到端） | Doc 24（Gene/Strategy 层，候选池在 Gene 选择中的角色） |
| candidates.js 提取逻辑 | Doc 29（Signal 提取，工具绕行检测） |
| hubSearch.js 两阶段搜索 | Doc 44（Hub Search + Caching，已覆盖两阶段 + LRU） |
| skillDistiller.js 提炼 | Doc 47（Curriculum + ExecutionTrace + Skill Distillation） |
| questionGenerator.js 悬赏 | Doc 43（Privacy + Hub Ecosystem，六策略已覆盖） |
| candidateEval.js 预览构建 | Doc 46（Hub Ecosystem，external candidates 匹配已覆盖） |

**本文档价值**：将散落于 5 个文件的管线串联为统一视图，补充其他文档的"模块内"分析。
