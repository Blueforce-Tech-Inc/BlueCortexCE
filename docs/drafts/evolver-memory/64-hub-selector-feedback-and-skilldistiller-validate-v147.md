# Doc 64: Hub-Selector 反馈闭环（v1.47 现实核查）+ Capability Candidate 生命周期 + Hub Events 全图

**目标**：
1. 记录 v1.47.0 `selector.js` + `evolve.js` Hub-Selector 反馈闭环的实际实现路径
2. 核查 doc 51（Capability Candidate 生命周期管线）的准确性
3. 记录 `skillDistiller.js` `validateSynthesizedGene` 5 大验证门的完整逻辑

**数据来源**：`/Users/yangjiefeng/Documents/EvoMap/evolver/` @ **v1.47.0**（git log: `e72778e Release v1.47.0`）

**最后更新**：2026-04-25

---

## 目录

- [§1 Hub-Selector 反馈闭环（v1.47 实际路径）](#s1-hub-selector-反馈闭环v147-实际路径)
- [§2 `selectGene()` 五模式漂移机制](#s2-selectgene-五模式漂移机制)
- [§3 `noveltyScore` 探索范围调节](#s3-noveltyscore-探索范围调节)
- [§4 `validateSynthesizedGene` 5 大验证门](#s4-validatesynthesizedgene-5-大验证门)
- [§5 `shouldDistill()` 蒸馏门禁条件](#s5-shoulddistill-蒸馏门禁条件)
- [§6 Capability Candidate 生命周期管线（doc 51 核查）](#s6-capability-candidate-生命周期管线doc-51-核查)
- [§7 Hub Events 全信号分类图](#s7-hub-events-全信号分类图)
- [§8 CE 借鉴要点](#s8-ce-借鉴要点)

---

## §1 Hub-Selector 反馈闭环（v1.47 实际路径）

### 1.1 完整数据流

```
Hub 心跳 (GET /a2a/events/poll)
    ↓
a2aProtocol.js: getNoveltyHint() → { score: 0.0–1.0 }
a2aProtocol.js: getCapabilityGaps() → string[]
    ↓
evolve.js run() → selectGeneAndCapsule({ noveltyScore, capabilityGaps })
    ↓
selector.js: selectGene() → driftMode ∈ { memory_preferred / selection / diversity_directed / random_weighted / random }
    ↓
selected Gene + driftMode → mutation → attempt
    ↓
outcome → memoryGraph (边权重更新)
```

### 1.2 evolve.js 中的解析代码

```javascript
// evolve.js run() ~line 1700
let heartbeatNovelty = null;
let heartbeatCapGaps = [];
try {
  const { getNoveltyHint, getCapabilityGaps: getCapGaps } = require('./gep/a2aProtocol');
  heartbeatNovelty = getNoveltyHint();
  heartbeatCapGaps = getCapGaps() || [];
} catch (e) {}

// 传入 selector
const { selectedGene, capsuleCandidates, selector } = selectGeneAndCapsule({
  genes,
  capsules,
  signals,
  memoryAdvice,
  driftEnabled: IS_RANDOM_DRIFT,
  failedCapsules: recentFailedCapsules,
  capabilityGaps: heartbeatCapGaps,
  noveltyScore: heartbeatNovelty && Number.isFinite(heartbeatNovelty.score)
    ? heartbeatNovelty.score
    : null,
});
```

### 1.3 关键实现差异（vs doc 63 描述）

doc 63 描述 `noveltyScore` 来自 Hub 心跳的 `_latestNoveltyHint`/`_latestCapabilityGaps` 字段解析链路（a2aProtocol.js 658 行）。这在 v1.47 中是准确的：
- `getNoveltyHint()` 从 `_latestNoveltyHint` 提取 `score` 字段
- `getCapabilityGaps()` 从 `_latestCapabilityGaps` 提取 gap 字符串数组
- 两者均在 Hub heartbeat 响应时缓存到模块级变量

但 doc 63 称 `validateSynthesizedGene` 在 **selector.js**，实际在 **`skillDistiller.js`（line 383）**。

---

## §2 `selectGene()` 五模式漂移机制

**文件**：`src/gep/selector.js` 函数 `selectGene()`（~line 192）

### 2.1 决策树

```
useDrift = driftEnabled || driftIntensity > 0.15
    ↓
preferredGeneId 存在且 useDrift?
  → YES: driftMode = 'memory_preferred'
  → NO: 继续
    ↓
filtered.length == 0 (全部被 ban)?
  → YES: 返回 scored 前4，不 drift
  → NO: 继续
    ↓
Math.random() < driftIntensity?
  → NO: driftMode = 'selection' (top-1)
  → YES: driftIntensity > 0
        ↓
        capabilityGaps.length > 0?
          → YES: diversity_directed drift (gap 覆盖排序)
          → NO: noveltyScore < 0.3?
                  → YES: random_weighted drift (topN+1)
                  → NO: random drift (topN)
```

### 2.2 五模式详解

| 模式 | 触发条件 | 选基因素 | 探索程度 |
|------|----------|----------|----------|
| `memory_preferred` | `preferredGeneId` 存在且 useDrift | 记忆图推荐 | 低（保守） |
| `selection` | drift 未激活或 `Math.random() >= driftIntensity` | 四因子评分（exact+semantic+learning+drift） | 最低（纯选择） |
| `diversity_directed` | drift 激活 + `capabilityGaps.length > 0` + 有 gap 命中 | gap 覆盖度排序 → base score tie-break | 中（定向探索） |
| `random_weighted` | drift 激活 + capabilityGaps 无命中 + `noveltyScore < 0.3` | topN+1 随机 | 高（扩展探索） |
| `random` | drift 激活 + capabilityGaps 为空 | topN 随机 | 高（盲探索） |

### 2.3 diversity_directed drift 算法

```javascript
// selector.js ~line 253
const gapScores = filtered.map((entry, idx) => {
  const g = entry.gene;
  const patterns = Array.isArray(g.signals_match) ? g.signals_match : [];
  let gapHits = 0;
  for (let gi = 0; gi < capabilityGaps.length && gi < 5; gi++) {
    const gapSignal = capabilityGaps[gi];
    if (typeof gapSignal === 'string' &&
        patterns.some(p => matchPatternToSignals(p, [gapSignal]))) {
      gapHits++;
    }
  }
  return { idx, gapHits, baseScore: entry.score };
});

gapScores.sort((a, b) => b.gapHits - a.gapHits || b.baseScore - a.baseScore);
selectedIdx = gapScores[0].idx;
driftMode = 'diversity_directed';
```

**注意**：只取 `capabilityGaps[0..4]`（最多 5 个 gap）参与评分。

---

## §3 `noveltyScore` 探索范围调节

**文件**：`selector.js` `selectGene()`（~line 278）

### 3.1 机制

```javascript
// 当 noveltyScore < 0.3 时，扩展 topN 范围 +1
if (noveltyScore != null && noveltyScore < 0.3 && topN < filtered.length) {
  topN = Math.min(filtered.length, topN + 1);  // ← 扩大探索池
}
selectedIdx = Math.floor(Math.random() * topN);
driftMode = 'random_weighted';
```

### 3.2 语义

- `noveltyScore` 来自 Hub，衡量该 Agent 与生态中其他 Agent 的差异程度
- `< 0.3` = 该 Agent 与其他 Agent 太相似（高度同质化）
- 应对措施：扩大随机候选池，增加探索多样性
- 这发生在 `diversity_directed` 无法匹配 gap 时（`hasGapHits == false`）

---

## §4 `validateSynthesizedGene` 5 大验证门

**文件**：`src/gep/skillDistiller.js` 函数 `validateSynthesizedGene()`（line 383）

### 4.1 完整验证链

```javascript
function validateSynthesizedGene(gene, existingGenes) {
  const errors = [];

  // Gate 1: 基础结构 + schema
  if (!gene || typeof gene !== 'object')
    return { valid: false, errors: ['gene is not an object'] };
  if (gene.type !== 'Gene')       errors.push('missing or wrong type (must be "Gene")');
  if (!gene.id || typeof gene.id !== 'string') errors.push('missing id');
  if (!gene.category)             errors.push('missing category');
  if (!Array.isArray(gene.signals_match) || gene.signals_match.length === 0)
    errors.push('missing or empty signals_match');
  if (!Array.isArray(gene.strategy) || gene.strategy.length === 0)
    errors.push('missing or empty strategy');

  // Gate 2: signals_match 脱敏（净化后再算 ID）
  if (Array.isArray(gene.signals_match)) {
    gene.signals_match = sanitizeSignalsMatch(gene.signals_match);
    if (gene.signals_match.length === 0)
      errors.push('signals_match is empty after sanitization');
  }

  // Gate 3: summary 净化（去除时间戳噪声）
  if (gene.summary)
    gene.summary = gene.summary
      .replace(/\s*\d{10,}\s*$/g, '')
      .replace(/\.\s*\d{10,}/g, '.')
      .trim();

  // Gate 4: ID 规范化
  if (gene.id && !gene.id.startsWith(DISTILLED_ID_PREFIX))
    gene.id = DISTILLED_ID_PREFIX + gene.id.replace(/^gene_/, '');
  // 重命名不合法 ID（纯数字/时间戳/厂商名）
  if (needsRename(gene.id)) gene.id = deriveDescriptiveId(gene);
  // 净化后 ID 过短则重命名
  if (cleanSuffix.length < 6) gene.id = deriveDescriptiveId(gene);

  // Gate 5: summary 降级（从 strategy[0] 或 signals_match 生成）
  if (!gene.summary || gene.summary.length < 10)
    gene.summary = /* 从 strategy/signals_match 构造 */;

  // Gate 6: strategy 质量门禁（≥3 步）
  if (gene.strategy.length < 3)
    errors.push('strategy must have at least 3 steps for a quality skill');

  // Gate 7: constraints 规范化
  if (!gene.constraints) gene.constraints = {};
  if (!Array.isArray(gene.constraints.forbidden_paths))
    gene.constraints.forbidden_paths = ['.git', 'node_modules'];
  if (!gene.constraints.forbidden_paths.some(p => p === '.git' || p === 'node_modules'))
    errors.push('constraints.forbidden_paths must include .git or node_modules');
  if (!gene.constraints.max_files || gene.constraints.max_files > DISTILLED_MAX_FILES)
    gene.constraints.max_files = DISTILLED_MAX_FILES;

  // Gate 8: validation 命令白名单（复用 policyCheck.isValidationCommandAllowed）
  if (Array.isArray(gene.validation)) {
    gene.validation = gene.validation.filter(cmd => isValidationCommandAllowed(cmd));
  }

  // Gate 9: schema_version
  if (!gene.schema_version) gene.schema_version = '1.6.0';

  // Gate 10: 重复 ID 检测
  const existingIds = new Set((existingGenes||[]).map(g => g.id));
  if (gene.id && existingIds.has(gene.id))
    gene.id = gene.id + '_' + Date.now().toString(36);

  // Gate 11: signals_match 完全重叠检测
  const newSet = new Set(gene.signals_match.map(s => String(s).toLowerCase()));
  for (const eg of (existingGenes||[])) {
    const egSet = new Set((eg.signals_match||[]).map(s => String(s).toLowerCase()));
    if (newSet.size > 0 && egSet.size > 0) {
      let overlap = 0;
      newSet.forEach(s => { if (egSet.has(s)) overlap++; });
      if (overlap === newSet.size && overlap === egSet.size)
        errors.push('signals_match fully overlaps with existing gene: ' + eg.id);
    }
  }

  return { valid: errors.length === 0, errors, gene };
}
```

**门禁摘要**：

| # | 门禁 | 检查内容 | 错误时处理 |
|---|------|----------|------------|
| 1 | 结构 | `type===Gene`/`id`/`category`/`signals_match`/`strategy` | 返回 valid=false |
| 2 | signals_match 脱敏 | `sanitizeSignalsMatch()` 后非空 | 错误累加 |
| 3 | summary 净化 | 去除时间戳噪声 | 静默修复 |
| 4 | ID 规范化 | `DISTILLED_ID_PREFIX` 前缀 + 合法字符 | 静默重命名 |
| 5 | summary 降级 | 长度 < 10 则从 strategy/signals_match 生成 | 静默生成 |
| 6 | strategy 步数 | ≥ 3 步 | 错误累加 |
| 7 | forbidden_paths | 包含 `.git` 或 `node_modules` | 错误累加 |
| 8 | validation 命令 | `isValidationCommandAllowed()` 白名单 | 静默过滤 |
| 9 | schema_version | 存在即保留，不存在设为 `1.6.0` | 静默设置 |
| 10 | 重复 ID | ID 不在 existingGenes 中 | 静默加时间戳后缀 |
| 11 | 信号重叠 | `newSet ⊆ existingSet` 时报错 | 错误累加 |

**doc 63 原述有误**：doc 63 称 5 大门禁为"结构/唯一性/重叠检测/安全约束/危险命令净化"。实际是 **11 道检查**（结构 1+2、ID 4+10、strategy 5+6、constraints 7、validation 8、signals 11）。

---

## §5 `shouldDistill()` 蒸馏门禁条件

**文件**：`skillDistiller.js` `shouldDistill()`（~line 492）

```javascript
function shouldDistill() {
  // 环境变量可禁用
  if (String(process.env.SKILL_DISTILLER || 'true').toLowerCase() === 'false') return false;

  // 冷却期检查（默认 24h）
  const state = readDistillerState();
  if (state.last_distillation_at) {
    const elapsed = Date.now() - new Date(state.last_distillation_at).getTime();
    if (elapsed < DISTILLER_INTERVAL_HOURS * 3600000) return false;
  }

  // 最近 10 个 capsule 中至少 7 个成功
  const recent = all.slice(-10);
  const recentSuccess = recent.filter(c => c.outcome?.status === 'success' || c.outcome === 'success').length;
  if (recentSuccess < 7) return false;

  // 全量至少 DISTILLER_MIN_CAPSULES 个成功（默认 20）
  const totalSuccess = all.filter(c => c.outcome?.status === 'success' || c.outcome === 'success').length;
  if (totalSuccess < DISTILLER_MIN_CAPSULES) return false;

  return true;
}
```

**CE 借鉴**：`shouldDistill` 模式等价于 CE 的"ObservationEntity 积累阈值"——只有当观测数量达到一定规模后才触发结构化提取。可类比：
- `recentSuccess >= 7` → 最近 N 次会话中成功推断比例
- `totalSuccess >= MIN_CAPSULES` → 全局观测总量门槛
- `elapsed >= INTERVAL_HOURS` → 冷却期防抖动

---

## §6 Capability Candidate 生命周期管线（doc 51 核查）

### 6.1 doc 51 描述 vs v1.47 实际

| 阶段 | doc 51 描述 | v1.47 实际 |
|------|-------------|------------|
| **来源 1** | 高频工具调用（`extractToolCalls`） | ✅ `extractToolCalls()` 来自 `candidates.js` |
| **来源 2** | 信号映射候选 | ✅ `signalsToCandidates()` |
| **来源 3** | 失败胶囊聚类 | ✅ `clusterFailedCapsules()` |
| **Hub 匹配** | 两阶段 + LRU | ✅ `hubSearch.js` 两阶段语义搜索 |
| **技能提炼** | LLM 驱动 `skillDistiller.js` | ✅ `distillCandidates()` → `distillCandidate()` |
| **问题生成** | 六策略 `questionGenerator.js` | ✅ `generateQuestions()` |

### 6.2 关键调用链（v1.47）

```
evolve.js run()
  → buildCandidatePreviews({ signals, recentSessionTranscript })
      → candidates.js: extractToolCalls()         // 工具调用提取
      → candidates.js: signalsToCandidates()      // 信号映射
      → candidates.js: clusterFailedCapsules()    // 失败聚类
      → candidateEval.js: scoreCandidates()       // 评分
      → candidateEval.js: buildExternalPreviews() // Hub 外部候选
      → hubSearch.js: hubSearch()                 // 两阶段 Hub 搜索

skillDistiller.js shouldDistill()
  → prepareDistillation()
      → distillCandidate()  × N
          → extractToolCallsFromCandidate()        // 从候选提取工具调用
          → synthesizeGene()                       // LLM 生成 Gene
          → validateSynthesizedGene(rawGene, existingGenes)  // 5+ 门禁
          → persistGene()
              → skillPublisher.publishSkill()      // 可选发布到 Hub
  → generateQuestions()                             // 悬赏提问

questionGenerator.js generateQuestions()
  → 六策略问题生成（SOLUTION/SKILL/CAPABILITY/EDGE_CASE/INVERSE/ALTERNATIVE）
  → hubQuestionClient.createBounty()               // 发布到 Hub
```

**doc 51 评估**：整体准确，描述了 v1.47 的五阶段管线。

---

## §7 Hub Events 全信号分类图

**文件**：`evolve.js` run() ~line 1520，`HUB_EVENT_SIGNALS` 对象（内联定义）

### 7.1 七分类 + 35+ 信号

| 分类 | 信号 | 语义 |
|------|------|------|
| **dialog（对话）** | `dialog_message` | 收到对话消息，需回复 |
| **governance/council（议会/治理）** | `council_invite` | 收到议会邀请 |
| | `council_second_request` | 被请求为提案背书 |
| | `council_vote` | 收到投票请求 |
| | `council_community_vote` | 社区投票请求 |
| | `council_decision` | 议会做出决定 |
| | `council_decision_notification` | 收到决定通知 |
| **governance/deliberation（审议/辩论）** | `deliberation_invite` | 收到辩论邀请 |
| | `deliberation_challenge` | 收到辩论挑战 |
| | `deliberation_next_round` | 进入下一轮辩论 |
| | `deliberation_completed` | 辩论结束 |
| **collaboration（协作）** | `collaboration_invite` | 收到协作邀请 |
| | `session_message` | 收到会话消息 |
| | `session_nudge` | 收到空闲警告 |
| | `task_board_update` | 任务板更新 |
| **task/workpool（任务/工作池）** | `task_available` | 新任务可用 |
| | `work_assigned` | 被分配任务 |
| | `swarm_subtask_available` | 蜂群子任务可用 |
| | `swarm_aggregation_available` | 蜂群聚合结果可用 |
| | `diverge_task_assigned` | 发散任务被分配 |
| | `pipeline_step_assigned` | 流水线步骤被分配 |
| | `organism_work` | 有机体任务分配 |
| **swarm/PDRI（蜂群角色）** | `swarm_plan_available` | 蜂群规划角色可用 |
| | `swarm_build_available` | 蜂群构建角色可用 |
| | `swarm_review_available` | 蜂群评审角色可用 |
| | `swarm_aggregate_available` | 蜂群聚合角色可用 |
| | `swarm_rework_required` | 需要返工 |
| **privacy（隐私计算）** | `privacy_task_submitted` | 隐私任务已提交 |
| | `privacy_result_ready` | 隐私计算结果就绪 |
| **knowledge（知识）** | `knowledge_shared` | 知识被共享 |
| | `lesson_learned` | 经验教训被记录 |
| **swarm/governance（蜂群治理）** | `swarm_governance_update` | 蜂群治理更新 |
| **review（评审）** | `hub_review_available` | Hub 有新评审 |

### 7.2 Hub Events 注入机制

```javascript
// evolve.js run()
const { consumeHubEvents } = require('./gep/a2aProtocol');
const hubEvents = consumeHubEvents();
if (hubEvents.length > 0) {
  const HUB_EVENT_SIGNALS = { /* 35+ 映射 */ };
  for (const evt of hubEvents) {
    const eventType = evt.type || evt.event_type;
    const mappedSignals = HUB_EVENT_SIGNALS[eventType] || [];
    for (const sig of mappedSignals) {
      if (!signals.includes(sig)) signals.unshift(sig);
    }
    // 也可存储 evt.context 供 LLM 感知
  }
}
```

**特点**：
- `signals.unshift()` 注入到最前（高优先级）
- 同类型事件不重复注入（`includes` 检查）
- 事件 context 可注入人格 prompt 增强 LLM 感知

---

## §8 CE 借鉴要点

| 主题 | Evolver v1.47 机制 | CE 借鉴方向 | 优先级 |
|------|---------------------|-------------|--------|
| Hub-Selector 闭环 | `noveltyScore` + `capabilityGaps` → `diversity_directed` drift | ObservationEntity 增加 `noveltyHint` / `capabilityGaps` 字段 | P1 |
| 五模式漂移 | `memory_preferred` → `selection` → `diversity_directed` → `random_weighted` → `random` | CE SessionScope 漂移策略分级（保守→探索） | P1 |
| `noveltyScore < 0.3` | 扩展 topN +1 增加多样性探索 | ObservationEntity 增加 `ecosystemNovelty` 指标 | P2 |
| `validateSynthesizedGene` | 11 道验证门（结构+脱敏+ID+strategy+constraints+validation+重叠） | `StructuredExtractionService` 增加 6+ 道 pre-write 验证 | P1 |
| `shouldDistill` 门禁 | 7/10 近期成功 + 总量门槛 + 冷却期 | ObservationEntity 积累阈值（会话数 + 推断成功率） | P1 |
| Hub Events 信号注入 | 35+ 信号 7 分类，`signals.unshift()` 高优先级注入 | SSE 事件 `type` 字段驱动 `observationTypes` 注入 | P2 |
| 能力缺口定向探索 | `capabilityGaps` 匹配 `signals_match` | 知识缺口驱动观测优先级排序 | P1 |

---

## 附录：关键文件行号索引（v1.47.0）

| 函数/对象 | 文件 | 行号 |
|-----------|------|------|
| `getNoveltyHint()` | `src/gep/a2aProtocol.js` | 模块级缓存 |
| `getCapabilityGaps()` | `src/gep/a2aProtocol.js` | 模块级缓存 |
| `selectGene()` | `src/gep/selector.js` | ~line 192 |
| `selectGeneAndCapsule()` | `src/gep/selector.js` | line 347 |
| `validateSynthesizedGene()` | `src/gep/skillDistiller.js` | line 383 |
| `shouldDistill()` | `src/gep/skillDistiller.js` | ~line 492 |
| `HUB_EVENT_SIGNALS`（内联对象） | `src/evolve.js` | ~line 1520 |
| `consumeHubEvents()` 调用 | `src/evolve.js` | ~line 1690 |
| `selectBestTask()` | `src/gep/taskReceiver.js` | 导出函数 |
| `buildCandidatePreviews()` | `src/gep/candidateEval.js` | 导出函数 |
| `hubSearch()` | `src/gep/hubSearch.js` | 导出函数 |
