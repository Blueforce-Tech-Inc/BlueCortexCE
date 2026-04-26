# EvoMap/evolver 核心设计模式与 CE 翻译路径

> **角色**：从 61 个子文档的细节中提炼**最高价值的可落地设计模式**，聚焦 BlueCortexCE（旁路型记忆系统）的借鉴路径。
> **数据来源**：`src/gep/memoryGraph.js`、`memoryGraphAdapter.js`、`signals.js`、`learningSignals.js`、`candidates.js`、`reflection.js`、`assetStore.js`、`assetCallLog.js`、`skillDistiller.js`、`paths.js`、`contentHash.js`。
> **最后更新**：2026-04-25

---

## 架构规范

- **单文件上限**：≤50KB（本文当前约 15KB）
- **范围**：提炼 5 个核心设计模式，每个模式覆盖"机制 → 源码证据 → CE 翻译"
- **不重复**：假设读者已读过 [`index.md`](./index.md) 的导航表；本文专注"模式 + 落地"

---

## 模式一：Append-only 事件溯源 + 状态投影

### 机制

Evolver 维护两个存储：

| 文件 | 性质 | 内容 |
|------|------|------|
| `memory_graph.jsonl` | Append-only、不可变 | **所有**事件的完整因果链（signal / hypothesis / attempt / outcome / confidence_edge / external_candidate） |
| `memory_graph_state.json` | 可变、仅存最新一条 | `last_action`（包含 `baseline_observed`）+ `outcome_recorded` 标志 |

**关键不变量**：`state.json` 是 `jsonl` 的**消费者视角物化视图**，而非独立状态机。

```javascript
// memoryGraph.js — 写入时双写
function recordAttempt({ signals, ... }) {
  // 1. Append to append-only graph (source of truth)
  appendJsonl(memoryGraphPath(), ev);

  // 2. Overwrite mutable state (projection for next outcome inference)
  const state = readJsonIfExists(statePath, { last_action: null });
  state.last_action = {
    action_id: actionId,
    baseline_observed: observations,   // captured AT attempt time
    had_error: hasErrorSignal(signals),
    outcome_recorded: false,          // not yet known
    // ...
  };
  writeJsonAtomic(statePath, state);  // overwrite, not append
}
```

outcome 推断：

```javascript
function recordOutcomeFromState({ signals, observations }) {
  const state = readJsonIfExists(statePath, { last_action: null });
  if (!state.last_action || state.last_action.outcome_recorded) return null; // idempotent

  // Infer outcome from baseline (attempt time) vs current (now)
  const inferred = inferOutcomeEnhanced({
    prevHadError: !!state.last_action.had_error,
    currentHasError: hasErrorSignal(signals),
    baselineObserved: state.last_action.baseline_observed,
    currentObserved: observations,
  });

  appendJsonl(memoryGraphPath(), { kind: 'outcome', outcome: inferred, ... });
  state.last_action.outcome_recorded = true;
  writeJsonAtomic(statePath, state); // mark recorded
}
```

**读取优化**：`tryReadMemoryGraphEvents` 只读文件尾部 512KB（`TAIL_BYTES`），无需全量扫描。

### 源码证据

- `memoryGraph.js`：`appendJsonl` / `writeJsonAtomic` / `tryReadMemoryGraphEvents`（尾部读取）
- `evolve.js`：`recordSignalSnapshot → recordHypothesis → recordAttempt → recordOutcomeFromState` 链

### CE 翻译路径

**现状**：CE 用 JPA 实体（ObservationEntity / SummaryEntity）做"实体存储"，没有事件溯源语义。

**P0 翻译**（直接可落地）：
- 在 `ObservationEntity`/`SummaryEntity` 中增加 `event_type`（ENUM：`session_start / user_prompt / observation / summary / context_generated`）字段
- 增加 `metadata` JSONB 字段存储"该事件特有的扩展数据"
- 增加 `baseline_snapshot` JSONB 字段（对标 `baseline_observed`）：在 session start 时快照当前上下文
- **不做 append-only 改造**（现有 JPA 实体有 Update/Delete 需求），但在新增的 `MemoryEventEntity` 上做 append-only 语义

**P1 翻译**（中期）：
- 引入 `MemoryEventEntity`（纯 append-only JSONB），存储**所有**原始事件
- JPA 实体从 `MemoryEventEntity` 投影生成（对标 `memory_graph_state.json` 的投影角色）
- 这样既有因果链（JSONL），又有结构化查询（Entity）

---

## 模式二：Signal Enrichment Pipeline（信号丰富化管线）

### 机制

信号从"原始文本匹配"到"可执行决策"经过**三个串行阶段**：

```
Stage 1: extractSignals (signals.js)
  ↓ corpus（transcript + log + memory + user）→ 原始 signal 列表
     - regex 匹配（log_error / errsig: / feature_request / improvement / perf / capability_gap）
     - 历史去重：analyzeRecentHistory（饱和检测 / 失败连击 / 工具绕行）
     - 多语言支持（EN / ZH-CN / ZH-TW / JA）

Stage 2: expandSignals (learningSignals.js)
  ↓ 原始 signal → 分类 tag
     problem:* (reliability / protocol / performance / capability / stagnation)
     action:* (repair / optimize / innovate)
     area:* (orchestration / memory / skills / prompt)
     risk:* (validation)

Stage 3: Gene Matching (learningSignals.js + memoryGraph.js)
  ↓ tag + signal-key Jaccard → Gene 评分
     scoreTagOverlap(gene, signals) → Jaccard(tag_set)
     getMemoryAdvice → edgeExpectedSuccess(signal-key × gene-id) → Laplace平滑 + 半衰衰减
```

**每个阶段都有边界约束**：
- Stage 1：结果去重（`analyzeRecentHistory.suppressedSignals`）
- Stage 2：tag 唯一化（`unique()`）
- Stage 3：ban 规则（`bannedGeneIds`）+ 效率门控（attempts≥2 且 best<0.18 则 ban）

```javascript
// Stage 2: expandSignals 源码片段
function expandSignals(signals, extraText) {
  const tags = [];
  // 1. 直接透传原始 signal
  for (const signal of raw) { add(tags, signal); }
  // 2. 分类标签扩展
  if (/(error|exception|failed)/.test(text)) {
    add(tags, 'problem:reliability');
    add(tags, 'action:repair');
  }
  if (/(perf|bottleneck|latency)/.test(text)) {
    add(tags, 'problem:performance');
    add(tags, 'action:optimize');
  }
  if (/(feature|capability_gap|stagnation)/.test(text)) {
    add(tags, 'problem:capability');
    add(tags, 'action:innovate');
  }
  return unique(tags); // 去重
}

// Stage 3: scoreTagOverlap 源码
function scoreTagOverlap(gene, signals) {
  const signalTags = expandSignals(signals, '');
  const geneTagList = geneTags(gene);
  const signalSet = new Set(signalTags);
  let hits = 0;
  for (const tag of geneTagList) {
    if (signalSet.has(tag)) hits++;
  }
  return hits; // Raw overlap count (not normalized Jaccard — Jaccard in getMemoryAdvice)
}
```

### 源码证据

- `signals.js`：`extractSignals`（Stage 1）—— 全文约 400 行
- `learningSignals.js`：`expandSignals` / `geneTags` / `scoreTagOverlap`（Stage 2）
- `memoryGraph.js`：`getMemoryAdvice`（Stage 3 Jaccard + edge 评分）

### CE 翻译路径

**现状**：CE ObservationEntity 有 `observation_type`（标签），但没有"标签扩展"逻辑；Observation → Strategy 评分依赖简单的向量相似度。

**P1 翻译**：
- 引入 `TagExpansionService`：接收原始 Observation，输出扩展标签集（problem:* / action:* / area:*）
- 标签分类表可配置（对标 `learningSignals.js` 的 regex 规则表）
- 在 `SearchService.vectorSearch()` 前加 tag-based pre-filter（减少向量搜索范围）

**P2 翻译**：
- 实现 Jaccard × 边权重的 Gene Selection（对标 `getMemoryAdvice` 的 signal-key × gene-edge 评分）
- 引入 Outcome 记录（signal-key × strategy-id → success/fail），实现**闭环反馈**

---

## 模式三：Outcome 推断三层策略

### 机制

outcome（成功 / 失败 / score）不是直接由外部报告的，而是通过**三层策略**从信号中推断：

```javascript
// memoryGraph.js
function inferOutcomeEnhanced({ prevHadError, currentHasError, baselineObserved, currentObserved }) {

  // Layer 1: 显式 EvolutionEvent 扫描（证据优先）
  const observed = tryParseLastEvolutionEventOutcome(combinedEvidence);
  if (observed) return observed;

  // Layer 2: 错误信号比较（快速降级）
  const base = inferOutcomeFromSignals({ prevHadError, currentHasError });
  // errsig: cleared → success (0.85)
  // errsig: persisted → failed (0.2)
  // new error appeared → failed (0.15)
  // stable → success (0.6)

  // Layer 3: 启发式增量调整（精细化）
  let score = base.score;
  // 错误计数 delta
  if (prevErrCount != null && curErrCount != null) {
    score += Math.max(-0.12, Math.min(0.12, delta / 50));
  }
  // 扫描速度 ratio
  if (prevScan > 0) {
    score += Math.max(-0.06, Math.min(0.06, (prevScan - curScan) / prevScan));
  }

  return { status: base.status, score: clamp01(score), note: `${base.note}|heuristic_delta` };
}
```

**为什么这样做**：Evolver 是旁路系统，不直接观察代码执行结果，只能通过日志/signal 推断。这套三层策略在"无直接证据"时提供了**合理默认值 + 可调启发式**。

### 源码证据

- `memoryGraph.js`：`inferOutcomeFromSignals` / `tryParseLastEvolutionEventOutcome` / `inferOutcomeEnhanced`

### CE 翻译路径

**现状**：CE 没有 outcome 推断逻辑——Observation 的 `outcome`（成功/失败）是手动填写的，或者根本没有。

**P1 翻译**：
- 引入 `OutcomeInferenceService`：接收 `baseline_context`（session start 时快照）+ `current_signals`（user_prompt + recent observations）
- 三层推断：ExplicitEvent（扫描最近的 Observation）→ SignalCompare（比较前后 error 信号）→ HeuristicDelta（error_count / scan_speed / token_usage）
- 将推断结果写入 `OutcomeEntity`（新表）或 `ObservationEntity.outcome_detail`

---

## 模式四：Content-Addressable Asset + Deduplicated Asset Lifecycle

### 机制

Evolver 的 Gene/Capsule/Skill 不是按 ID 存储的，而是按 **content hash** 存储——相同内容的资产永远只存一份：

```javascript
// contentHash.js
function computeAssetId(obj, excludeFields = ['asset_id']) {
  // 1. 排除 self-referential 字段
  const clean = {};
  for (const k of Object.keys(obj)) {
    if (!exclude.has(k)) clean[k] = obj[k];
  }
  // 2. Canonical JSON（key 排序 + 确定性格式）
  const canonical = canonicalize(clean);
  // 3. SHA-256 哈希
  const hash = crypto.createHash('sha256').update(canonical, 'utf8').digest('hex');
  return 'sha256:' + hash;
}

// assetStore.js: 写入时检查是否已存在
function ensureAssetFiles() {
  const assetFile = getAssetFile('genes');
  if (!fs.existsSync(assetFile)) {
    writeJsonAtomic(assetFile, { version: SCHEMA_VERSION, genes: [] });
  }
  // ...
}
```

**资产生命周期**：

```
candidates.js (extractCapabilityCandidates)
  ↓ (source: transcript / signals / failed_capsules)
assetStore.js (appendCandidateJsonl → deduplicated by content hash)
  ↓ (success streak ≥ 3 → candidateEval.js)
skillDistiller.js (LLM-driven skill creation from capsules)
  ↓ (skill validated → assetStore.js)
skillPublisher.js (publish to Hub)
  ↓ (used by other nodes → assetCallLog.js tracks usage)
```

**AssetCallLog** 是**使用追踪**的 append-only log，记录 `run_id / action / asset_id / score / signals`，为 skillDistiller 的选择提供数据。

### 源码证据

- `contentHash.js`：`canonicalize`（确定性序列化）/ `computeAssetId`（SHA-256）
- `assetStore.js`：`appendCandidateJsonl` / `readRecentCandidates`
- `assetCallLog.js`：`logAssetCall` / `readCallLog`
- `candidates.js`：`extractCapabilityCandidates`（三来源候选提取）
- `skillDistiller.js`：`collectDistillationData` / `buildDistillationPrompt` / `parseDistillationOutput`

### CE 翻译路径

**现状**：CE 没有 content-addressable 存储；ObservationEntity 有 `content_hash` 字段但只做展示，不用于去重。

**P1 翻译**：
- 实现 `ContentHashService`：canonical JSON → SHA-256，生成 `content_hash`
- 在 `ObservationEntity.content_hash` 上建立**唯一索引**
- `IngestionController` 写入前检查 `content_hash` 是否已存在——存在则**跳过写入**（对标 `appendCandidateJsonl` 的去重）
- `EmbeddingService` 只对**新内容**计算 embedding（避免重复 embedding 计算）

---

## 模式五：Provider Resolution + Local-First Adapter Pattern

### 机制

`memoryGraphAdapter.js` 实现了**稳定的接口边界**——`evolve.js` 只依赖 adapter 的方法签名，不关心底层是本地还是远程：

```javascript
// memoryGraphAdapter.js
function resolveAdapter() {
  const provider = (process.env.MEMORY_GRAPH_PROVIDER || 'local').toLowerCase().trim();
  if (provider === 'remote') {
    return buildRemoteAdapter();
  }
  return localAdapter; // default
}

// local adapter: 直接调用 memoryGraph.js
const localAdapter = {
  name: 'local',
  getAdvice(opts) { return localGraph.getMemoryAdvice(opts); },
  recordOutcome(opts) { return localGraph.recordOutcomeFromState(opts); },
  // ...
};

// remote adapter: local-first + graceful degradation
const remoteAdapter = {
  getAdvice: withFallback(
    (opts) => localGraph.getMemoryAdvice(opts),           // local primary
    async (opts) => {
      const result = await remoteCall('/kg/advice', opts); // remote enhancement
      return normalizeToLocalContract(result);
    }
  ),
  // 写操作：local 优先，async sync 到 remote
  recordOutcome(opts) {
    const ev = localGraph.recordOutcomeFromState(opts);   // local first
    remoteCall('/kg/ingest', { kind: 'outcome', event: ev }).catch(() => {}); // fire-and-forget
    return ev;
  },
};

function withFallback(localFn, remoteFn) {
  return async function (...args) {
    try {
      return await remoteFn(...args);  // try remote
    } catch (e) {
      return localFn(...args);          // degrade to local
    }
  };
}
```

**关键设计原则**：
1. **Local is always the default**：`MEMORY_GRAPH_PROVIDER=remote` 是 opt-in
2. **Write operations are local-first**：先写本地，异步同步远程（不阻塞主循环）
3. **Read operations try remote first**：如果远程失败，降级到本地
4. **Adapter contract is versioned**：`SCHEMA_VERSION` 在 `contentHash.js` 中定义，remote 需要做版本协商

### 源码证据

- `memoryGraphAdapter.js`：完整实现（~250 行）
- `index.js`：`resolveAdapter` 调用在模块加载时执行

### CE 翻译路径

**现状**：CE 没有远程 memory provider 的概念；所有操作都在本地 PostgreSQL。

**P2 翻译**（中期）：
- 定义 `MemoryProviderAdapter` 接口（Java）：`getAdvice` / `recordObservation` / `searchMemories`
- 实现 `LocalMemoryAdapter`（当前 PostgreSQL 实现）
- 实现 `RemoteMemoryAdapter`（调用远程 LLM Knowledge Graph 服务）
- 使用 `withFallback` 模式：远程优先，读失败降级本地；写操作本地优先，异步同步远程
- 通过 `app.properties` 的 `memory.graph.provider=local|remote` 控制

---

## 汇总：CE 翻译优先级矩阵

| 模式 | P0（直接可落地） | P1（短期 1-2 周） | P2（中期 1 个月） |
|------|----------------|-------------------|-----------------|
| Append-only 事件溯源 + 状态投影 | ObservationEntity 加 `event_type` + `baseline_snapshot` | 引入 `MemoryEventEntity`（append-only JSONB）做因果链 | JPA Entity 从 JSONB Projection 生成 |
| Signal Enrichment Pipeline | — | `TagExpansionService`（problem/action/area tag 分类）| Tag-based pre-filter + Jaccard × edge 评分 |
| Outcome 推断三层策略 | — | `OutcomeInferenceService`（baseline + signal compare + heuristic delta）| OutcomeEntity 闭环反馈到 Search |
| Content-Addressable Asset | `content_hash` 唯一索引 + 写入去重 | 只对**新内容**计算 embedding | Asset lifecycle log（对标 assetCallLog）|
| Provider Resolution Adapter | — | — | `MemoryProviderAdapter` + `LocalMemoryAdapter` + `RemoteMemoryAdapter` |

---

## 不在本文范围（见其他子文档）

| 主题 | 文档 |
|------|------|
| MemoryGraph 边权重 Laplace + 半衰衰减 | [`50`](./50-memory-graph-closed-loop-architecture.md) |
| Signal 提取（regex / 历史去重 / 饱和检测）| [`56`](./56-signals-reality-check-v147.md) |
| Reflection 自适应间隔（3/5/8 周期）| [`59`](./59-reflection-js-module-deep-dive.md) |
| Capability Candidate 生命周期管线 | [`51`](./51-capability-candidate-lifecycle-pipeline.md) |
| Skill Distiller（LLM 驱动技能提炼）| [`47`](./47-curriculum-executiontrace-skill-distillation.md) |
| Privacy + sanitize 脱敏管线 | [`61`](./61-sanitize-privacy-pipeline-deep-dive.md) |
| Ops 自我修复基础设施 | [`60`](./60-evolver-ops-self-healing-infrastructure.md) |
