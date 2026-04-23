# EvoMap/evolver 记忆系统架构综合分析

> **角色**：将散落在 `01`–`35` 分片中的 EvoMap/evolver 记忆系统设计**合成为一张连贯架构图**，提取对 BlueCortexCE 有价值的核心设计思想。
> **数据来源**：`EvoMap/evolver/src/gep/memoryGraph.js`、`memoryGraphAdapter.js`、`narrativeMemory.js`、`signals.js`、`selector.js`、`reflection.js`、`learningSignals.js`、`solidify.js`。
> **最后更新**：2026-04-23

---

## 1. 架构总览：三层记忆 + 一个反馈环路

```
┌─────────────────────────────────────────────────────────────────┐
│                    EvoMap/evolver 记忆架构                       │
│                                                                 │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐      │
│  │  Signal      │   │  Gene Pool   │   │  Narrative   │      │
│  │  Extraction  │   │  Selection   │   │  Memory      │      │
│  │  (signals.js)│   │  (selector.js)│   │  (narrative) │      │
│  └──────┬───────┘   └──────▲───────┘   └──────────────┘      │
│         │                  │                                  │
│         ▼                  │                                  │
│  ┌──────────────────────────────────────┐                     │
│  │        MemoryGraphAdapter            │                     │
│  │  (local JSONL 优先 / remote fallback) │                     │
│  └──────┬───────────────────────┬───────┘                     │
│         │                       │                              │
│         ▼                       ▼                              │
│  ┌─────────────┐   ┌──────────────────────┐                   │
│  │ memory_graph│   │ memory_graph_state   │                   │
│  │ .jsonl      │   │ .json (mutable)      │                   │
│  │ (append-    │   │ (last_action,        │                   │
│  │  only)      │   │  outcome_recorded)   │                   │
│  └─────────────┘   └──────────────────────┘                   │
│         ▲                       │                              │
│         │                       │                              │
│  ┌──────┴──────────────────────┴──────┐                      │
│  │        Evolution Cycle Loop          │                      │
│  │  signal→hypothesis→attempt→outcome   │                      │
│  └─────────────────────────────────────┘                      │
└─────────────────────────────────────────────────────────────────┘
```

### 三层记忆

| 层 | 存储介质 | 特性 | 用途 |
|----|---------|------|------|
| **事件图谱** | `memory_graph.jsonl`（JSONL，按行追加） | Append-only、不可变、因果链完整 | 长期历史、信号→结果关联、记忆advice |
| **状态快照** | `memory_graph_state.json`（JSON） | 可变、仅保留最近一次 `last_action` | outcome 推断的基准、快速读取 |
| **叙事记忆** | `narrativeMemory.md`（Markdown） | 按条裁剪（≤30条/≤12KB）、人类可读 | 人类审查、历史摘要 |

### 关键洞察：状态文件是图的"缓存"

`memory_graph_state.json` 不是独立的状态机——它是 `memory_graph.jsonl` 的**投影**：
- 每次 `recordAttempt` 时写入 `last_action`（包含基准 `baseline_observed`）
- `recordOutcomeFromState` 读取该 `last_action` + 当前 signals → 推断 outcome
- 推断完成后，**outcome 写入 JSONL**，状态文件更新 `outcome_recorded=true`
- JSONL 是 source of truth；状态文件是**消费者视角的物化视图**

---

## 2. 核心设计原则

### 2.1 Append-only 事件溯源（Event Sourcing）

所有记忆事件以 JSONL 行追加写入，**不修改已写入内容**：

```javascript
// memoryGraph.js
function appendJsonl(filePath, obj) {
  fs.appendFileSync(filePath, JSON.stringify(obj) + '\n', 'utf8');
}
```

**优点**：
- 天然支持完整因果链追踪（从头读到尾 = 完整历史）
- 并发安全（只追加，不读写竞争）
- 天然支持 replay 和重放
- 大文件只读尾部 512KB（`tryReadMemoryGraphEvents`）

**BlueCortexCE 借鉴**：BlueCortexCE 的 Observation/Summary/Prompt 表是"实体存储"，不是事件日志。如果要实现完整的因果追踪，可以考虑：
- 增加一张 `memory_events` 事件日志表（append-only）
- 在适当的生命周期节点写入事件
- 或者增强现有表添加 `event_type` + `causal_chain_id`

### 2.2 错误签名规范化（Error Signature Normalization）

原始错误文本每次不同，但归一化后可以跨运行匹配同一类错误：

```javascript
function normalizeErrorSignature(text) {
  return s
    .toLowerCase()
    .replace(/[a-z]:\\[^ \n\r\t]+/gi, '<path>')   // Windows path
    .replace(/\/[^ \n\r\t]+/g, '<path>')            // Unix path
    .replace(/\b0x[0-9a-f]+\b/gi, '<hex>')         // hex
    .replace(/\b\d+\b/g, '<n>')                     // numbers
    .replace(/\s+/g, ' ')
    .slice(0, 220);
}
```

- 路径 → `<path>`，数字/十六进制 → `<n>`
- 规范化后做 `stableHash` → 稳定信号键
- `computeSignalKey` 将 `errsig:<raw>` → `errsig_norm:<hash>`，使同类错误可聚合

**BlueCortexCE 借鉴**：BlueCortexCE 的 `ObservationEntity` 目前存储 `content` 原文。如果要支持跨会话的错误模式聚合：
- 添加 `error_signature_normalized` 字段
- 在 `ObservationService` 提取时做规范化
- 通过 `error_signature_normalized` 做向量相似搜索

### 2.3 Signal Taxonomy：信号分类 → 标签扩展 → 评分

`learningSignals.js` 的 `expandSignals` 建立了信号→标签的映射：

```javascript
// 原始信号 → 多维标签
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
```

`scoreTagOverlap` 在基因选择时，对 gene 的信号匹配做标签重叠评分。

**BlueCortexCE 借鉴**：BlueCortexCE 有"观察类型"（observation_types），但目前是静态标签。可以通过 `ObservationTypeMapper` 扩展为多标签体系，在嵌入时使用扩展后的标签提升检索精度。

---

## 3. MemoryGraphAdapter：适配器模式 + 本地优先写入

`memoryGraphAdapter.js` 是 EvoMap/evolver 记忆系统的核心接口抽象：

```javascript
// 适配器接口合约
const adapterInterface = {
  getAdvice({ signals, genes, driftEnabled }),
  recordSignalSnapshot({ signals, observations }),
  recordHypothesis({ signals, mutation, personality_state, selectedGene, ... }),
  recordAttempt({ signals, mutation, selectedGene, hypothesisId, ... }),
  recordOutcome({ signals, observations }),
  recordExternalCandidate({ asset, source, signals }),
  memoryGraphPath(),
  computeSignalKey(signals),
  tryReadMemoryGraphEvents(limit),
};
```

### 本地适配器（默认）

```javascript
const localAdapter = {
  name: 'local',
  getAdvice(opts) { return localGraph.getMemoryAdvice(opts); },
  recordSignalSnapshot(opts) { return localGraph.recordSignalSnapshot(opts); },
  // ... 直接委托 memoryGraph.js
};
```

### 远程适配器（SaaS）

```javascript
const remoteAdapter = {
  name: 'remote',
  // 写操作：先写本地，再异步同步到远程
  recordAttempt(opts) {
    const ev = localGraph.recordAttempt(opts);
    remoteCall('/kg/ingest', { kind: 'attempt', event: ev }).catch(() => {});
    return ev;
  },
  // 读操作：优先远程，失败则回落本地
  getAdvice: withFallback(
    (opts) => localGraph.getMemoryAdvice(opts),
    async (opts) => await remoteCall('/kg/advice', opts)
  ),
};
```

**关键设计**：
1. **环境变量驱动**：`MEMORY_GRAPH_PROVIDER=local|remote` 切换实现
2. **本地是 source of truth**：即使配置了远程，写操作也先落本地
3. **本地优先读取**：读取始终先尝试本地（`tryReadMemoryGraphEvents`）
4. **优雅降级**：远程失败 → 静默忽略 → 继续本地操作

**BlueCortexCE 借鉴**：
- BlueCortexCE 目前强依赖 PostgreSQL。如果实现 MemoryGraphAdapter 风格的接口：
  - `LocalMemoryAdapter` → 当前 PostgreSQL 实现
  - `RemoteMemoryAdapter` → 未来对接 Claude-Mem 云服务
  - 写入：先本地再异步同步
  - 读取：优先本地，远程作为增强
- 这也是"无 PostgreSQL 也能跑本地模式"的基础

---

## 4. 反馈环路：Signal → Gene → Outcome → Memory

```
  ┌─────────────┐
  │ Signal      │ ←── extractSignals() 从工作区/日志/历史中提取
  │ Extraction  │
  └──────┬──────┘
         │
         ▼
  ┌─────────────────────────┐
  │ getMemoryAdvice()       │ ← 读 JSONL 聚合边 (signal_key, gene_id)
  │ (记忆图谱查询)           │   Jaccard≥0.34 找相似信号键
  └──────┬──────────────────┘   Laplace 平滑 + 指数半衰
         │
         ▼
  ┌─────────────────────────┐
  │ selectGene()            │ ← 四因子评分：
  │ (基因选择)              │   1. exact signals_match 命中
  │                         │   2. semantic (BoW cosine) 相似度
  │                         │   3. epigenetic marks (平台/架构特定)
  │                         │   4. learning history (成功/失败)
  │                         │   + driftIntensity = 1/√Ne (种群遗传学)
  └──────┬──────────────────┘
         │
         ▼
  ┌─────────────────────────┐
  │ recordAttempt()         │ ← 写入 JSONL (kind='attempt')
  │                         │   同时更新 memory_graph_state.json
  │                         │   last_action = { baseline_observed }
  └──────┬──────────────────┘
         │
         ▼
  ┌─────────────────────────┐
  │ recordOutcomeFromState()│ ← 读 last_action + 当前 signals
  │ (Outcome 推断)          │   inferOutcomeEnhanced():
  │                         │   - error 是否消除？
  │                         │   - error count 是否下降？
  │                         │   - scan_ms 是否改善？
  │                         │   → outcome { status, score }
  └──────┬──────────────────┘
         │
         ▼
  ┌─────────────────────────┐
  │ buildConfidenceEdgeEvent │ ← 写入 JSONL (kind='confidence_edge')
  │ (置信快照)              │   半衰 30 天 / 45 天
  └──────┬──────────────────┘   → 影响下次 getMemoryAdvice
         │
         ▼
  ┌─────────────────────────┐
  │ recordNarrative()        │ ← narrativeMemory.md (Markdown)
  │ (叙事记忆)              │   trimNarrative: ≤30 条, ≤12KB
  └─────────────────────────┘
```

### 关键机制详解

#### 4.1 Jaccard 相似信号发现

```javascript
// memoryGraph.js getMemoryAdvice
const sim = jaccard(curSignals, sigs);
if (sim >= 0.34) {
  candidateKeys.push({ key: k, sim });
}
```

- 阈值 0.34 是经验值（Jaccard ≥ 1/3）
- 从最近 2000 行事件中扫描所有历史信号键
- 相似信号键对应的成功基因被优先选择

#### 4.2 半衰期衰减

```javascript
function decayWeight(updatedAtIso, halfLifeDays = 30) {
  const ageDays = (Date.now() - Date.parse(updatedAtIso)) / (1000*60*60*24);
  return Math.pow(0.5, ageDays / halfLifeDays);
}

function edgeExpectedSuccess(edge, opts) {
  const p = (succ + 1) / (total + 2); // Laplace 平滑
  const w = decayWeight(e.last_ts, halfLifeDays);
  return { p, w, value: p * w };
}
```

- 成功概率 × 衰减权重 = 综合分数
- 新数据权重高，旧数据权重低但不完全丢弃

#### 4.3 连续漂移（Drift）强度

```javascript
// selector.js computeDriftIntensity
// 源自种群遗传学：有效种群越小，遗传漂变越强
// intensity = 1 / sqrt(Ne)
// Ne=1: intensity=1.0 (纯漂移), Ne=25: intensity=0.2, Ne=100: intensity=0.1
return Math.min(1, 1 / Math.sqrt(ne));
```

- 当基因池较小时，漂移强度高（探索更多样化）
- 当基因池较大时，漂移强度低（选择为主）
- `diversity-directed drift`：在漂移模式下，仍然优先选择能覆盖 capability gaps 的基因

#### 4.4 Failed Capsule Ban

```javascript
// selector.js banGenesFromFailedCapsules
// 同一 Capsule 失败 2 次 + 信号重叠 ≥ 60% → ban 对应 gene
const FAILED_CAPSULE_BAN_THRESHOLD = 2;
const FAILED_CAPSULE_OVERLAP_MIN = 0.6;
```

防止重复失败的基因被反复选择。

---

## 5. 信号去重与饱和降级（防止系统空转）

`signals.js` 的 `analyzeRecentHistory` + `extractSignals` 实现了复杂的信号调控：

| 机制 | 触发条件 | 行为 |
|------|---------|------|
| **频率抑制** | 信号在过去 8 事件中出现 ≥3 次 | 抑制该信号 |
| **空转饱和** | 连续 5 次空 cycle（blast_radius=0） | 注入 `force_steady_state` + `evolution_saturation` |
| **空转降解** | 过去 8 事件中 ≥50% 为空 cycle | 注入 `empty_cycle_loop_detected` |
| **失败连击** | 连续 3+ 次失败 | 注入 `consecutive_failure_streak_N` |
| **失败降级** | 连续 5+ 次失败 | 注入 `failure_loop_detected` + `ban_gene:<top>` |
| **高失败率** | 过去 8 次中 ≥75% 失败 | 注入 `high_failure_ratio` |
| **修复循环** | 连续 3+ 次 repair | 注入 `force_innovation_after_repair_loop` |

**关键洞察**：信号系统不是被动收集，而是**主动调控**。防止系统：
- 反复处理同一问题（频率抑制）
- 在没有改进空间时继续空转（饱和降级）
- 重复失败同一路径（ban 机制）

**BlueCortexCE 借鉴**：
- BlueCortexCE 的 `ModeService` 目前是静态模式切换
- 可以增加动态调控机制：
  - 在 Observation 中追踪"相似信号最近出现频率"
  - 当频率过高时，自动切换到低频/被动观察模式
  - 实现"系统稳定后减少观察频率"的自适应逻辑

---

## 6. 自适应反思间隔

`reflection.js` 根据最近 outcomes 动态调整反思频率：

```javascript
function computeReflectionInterval(recentEvents) {
  if (allSuccess(last 3)) return 8;   // 稳定期：少反思
  if (allFailed(last 3))  return 3;   // 失败期：勤反思
  return 5;                            // 默认
}
```

- **成功期**：更长间隔 → 减少计算浪费
- **失败期**：更短间隔 → 快速调整策略
- 配合 `REFLECTION_COOLDOWN_MS = 30min` 防止过度反思

---

## 7. 对 BlueCortexCE 的关键启示

### 7.1 高价值借鉴（P0）

| EvoMap 设计 | BlueCortexCE 现状 | 建议 |
|------------|------------------|------|
| Append-only 事件日志 | 实体表（有更新/删除） | 增加 `memory_events` 事件日志，支持因果追踪 |
| 错误签名规范化 + stableHash | `error_sig_norm` 字段存在但未充分使用 | 规范化逻辑前移至 Observation 提取层 |
| Signal taxonomy + 标签扩展 | 静态 observation_types | 增加 `expandSignals()` 风格的多标签扩展 |
| 频率抑制 / 空转饱和 | 无动态调控 | 增加相似观察频率追踪，超阈值时切换模式 |

### 7.2 中等价值借鉴（P1）

| EvoMap 设计 | BlueCortexCE 现状 | 建议 |
|------------|------------------|------|
| 半衰期衰减（`decayWeight`） | 无时间衰减 | 在搜索结果排序中引入时间衰减因子 |
| Jaccard ≥ 0.34 相似信号发现 | 向量相似搜索（pgvector） | 用 Jaccard 对规范化信号键做辅助过滤 |
| Failed Capsule Ban | 无 gene 对应机制 | 在 `StructuredExtractionService` 中增加"连续失败模板 ban" |
| Narrative Memory | Session summary（LLM 生成） | 增加轻量 Markdown 叙事层（自动 trim） |

### 7.3 长期演进（P2）

| EvoMap 设计 | BlueCortexCE 现状 | 建议 |
|------------|------------------|------|
| MemoryGraphAdapter 接口抽象 | 直接调用 Repository | 引入 Adapter 接口，支持多存储后端 |
| 远程适配器 + 本地优先写入 | 强依赖 PostgreSQL | 实现可选本地模式（SQLite/文件） |
| Population-genetics drift intensity | 无 | 未来探索：多会话协同时的多样性控制 |

---

## 8. 架构特性速查

| 特性 | 实现 | 位置 |
|------|------|------|
| 存储格式 | Append-only JSONL + 可变 JSON state | `memoryGraph.js` |
| 事件类型 | signal / hypothesis / attempt / outcome / confidence_edge | `memoryGraph.js` |
| 错误归一化 | 路径/数字/十六进制 → `<path>`/`<n>`/`<hex>` | `memoryGraph.js` `normalizeErrorSignature` |
| 信号键 | 规范化后 stableHash，排序拼接 | `memoryGraph.js` `computeSignalKey` |
| 相似发现 | Jaccard ≥ 0.34 | `memoryGraph.js` `getMemoryAdvice` |
| 衰减模型 | Laplace 平滑 + 指数半衰 | `memoryGraph.js` `edgeExpectedSuccess` |
| 适配器 | local-first / remote fallback | `memoryGraphAdapter.js` |
| 叙事记忆 | Markdown，≤30 条，≤12KB | `narrativeMemory.js` `trimNarrative` |
| 信号提取 | 4 语言、频率抑制、饱和降级 | `signals.js` `extractSignals` |
| 基因选择 | exact + semantic + epigenetic + learning 四因子 | `selector.js` `selectGene` |
| Drift 强度 | `1/√Ne`（种群遗传学模型） | `selector.js` `computeDriftIntensity` |
| Failed Ban | 2 次失败 + 60% 信号重叠 | `selector.js` `banGenesFromFailedCapsules` |
| 反思间隔 | 成功 8 / 失败 3 / 默认 5 | `reflection.js` `computeReflectionInterval` |
| 固化管线 | 约束检查 → PRM 评分 → Canary → 发布 | `solidify.js` |
| A2A 协议 | hello / publish / fetch / review / task | `gep/a2aProtocol.js` |

---

## 9. 与 BlueCortexCE 的本质差异

理解这些差异有助于判断哪些可以借鉴：

| 维度 | EvoMap/evolver | BlueCortexCE |
|------|----------------|--------------|
| **架构类型** | 内置型（和 Agent 共进程） | 旁路型（独立服务） |
| **目标** | 进化优化（让 Agent 越做越好） | 记忆持久化（跨会话上下文） |
| **写入频率** | 每个进化 cycle 一次 | 每个 session 多次 |
| **记忆粒度** | 粗粒度事件（signal→outcome） | 细粒度 observation |
| **反馈回路** | Signal → Gene → Outcome → Signal（闭环） | Observation → 检索 → 注入（开环） |
| **存储依赖** | JSONL 文件（零依赖） | PostgreSQL（强依赖） |
| **适配器** | 有（local/remote） | 无（直接调 Repository） |

**核心差异本质**：Evolver 是**优化型记忆**（learns from outcomes），BlueCortexCE 是**记录型记忆**（persists for context）。两者的设计哲学不同，不应简单移植。

借鉴的正确姿势：提取**机制**（信号归一化、频率抑制、半衰衰减）而非**架构**（内置型事件循环）。
