# EvoMap/evolver 记忆系统架构分析

> 分析对象：`/Users/yangjiefeng/Documents/EvoMap/evolver`
> 分析时间：2026-05-07
> 代码版本：src/gep/ 目录核心文件

---

## 📋 执行摘要

EvoMap/evolver 是一个**自主进化的 AI Agent 框架**，其记忆系统是整个框架的"因果记忆中枢"。与 Claude-Mem 的向量检索不同，EvoMap 通过**事件溯源（Event Sourcing）+ JSONL 追加日志**实现了一个轻量级、可审计的因果图（Cause-Effect Graph），指导基因选择和进化决策。

**核心设计哲学**：记忆不是"存储检索"，而是"因果推理"——每次进化 cycle 的输入（Signal）、决策（Gene）、输出（Outcome）都被完整记录，并通过图推理指导下一轮基因选择。

---

## 🏗️ 1. 系统架构总览

### 1.1 三层记忆架构

EvoMap 的记忆系统由三个层次构成，每层解决不同问题：

```
┌──────────────────────────────────────────────────────────────┐
│  Layer 3: Narrative Memory (narrativeMemory.js)             │
│  职责：人类可读的 Markdown 时间线                            │
│  格式：[YYYY-MM-DD HH:MM:SS] CATEGORY - status              │
│  用途：快速了解历史、调试、可视化                             │
└──────────────────────────────────────────────────────────────┘
                            ▲
                            │ 读取
                            │
┌──────────────────────────────────────────────────────────────┐
│  Layer 2: Memory Graph (memoryGraph.js + memoryGraphAdapter) │
│  职责：因果图推理（Signal→Gene→Outcome）                    │
│  存储：memory_graph.jsonl（追加）+ state.json（可变）        │
│  用途：指导基因选择、抑制低效路径、偏好高效路径               │
└──────────────────────────────────────────────────────────────┘
                            ▲
                            │ 追加事件
                            │
┌──────────────────────────────────────────────────────────────┐
│  Layer 1: Signal Extraction (signals.js + learningSignals)   │
│  职责：从会话日志/今日日志/MEMORY.md 中提取信号              │
│  输入：session transcript, today log, memory snippet, user   │
│  输出：14+ 类信号列表（含多语言支持）                        │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 核心数据流

```
[Session Log] ──► [extractSignals()] ──► [Signal List]
                                              │
                         ┌────────────────────┼────────────────────┐
                         │                    │                    │
                         ▼                    ▼                    ▼
              [recordSignalSnapshot]  [getMemoryAdvice]    [selectGeneAndCapsule]
                   (写入图)              (读图推理)          (综合决策)
                         │                    │                    │
                         │                    ▼                    │
                         │            [preferredGeneId] ◄─────────┤
                         │            [bannedGeneIds]   ◄────────┤
                         │                    │                    │
                         └────────────────────┼────────────────────┘
                                              │
                                              ▼
                                    [Build Mutation Prompt]
                                              │
                                              ▼
                                    [recordHypothesis + recordAttempt]
                                              │
                                              ▼
                                    [Solidify → recordOutcomeFromState]
```

### 1.3 事件类型（Event Kinds）

memoryGraph.js 中定义的 6 类事件，全部以 JSONL 追加：

| Kind | 描述 | 关键字段 |
|------|------|---------|
| `signal` | 当前信号快照 | `signal.key`, `signal.signals`, `signal.error_signature` |
| `hypothesis` | Signal→Gene 因果假设 | `hypothesis.id`, `mutation`, `gene` |
| `attempt` | 实际执行的基因选择 | `action.id`, `selected_by` |
| `outcome` | 执行结果（成功/失败） | `outcome.status`, `outcome.score` |
| `confidence_edge` | Signal→Gene 置信度快照 | `stats.p`, `stats.decay_weight`, `stats.value` |
| `confidence_gene_outcome` | Gene→Outcome 置信度快照 | 同上（half_life=45d） |
| `external_candidate` | 外部候选资产（仅记录） | `asset.type`, `asset.id` |

### 1.4 核心设计原则

#### 1.4.1 Append-Only 图存储

- **memory_graph.jsonl**：所有事件按时间顺序追加写入，永不修改或删除
- 读取时只加载最后 512KB（约 2000 条事件），零索引、零数据库依赖
- **memory_graph_state.json**：唯一可变状态文件，仅存储 `last_action`

#### 1.4.2 因果闭包（Cause-Effect Closure）

每个进化 cycle 产生的事件链天然形成因果闭包：

```
Signal → Hypothesis → Attempt → Outcome
```

这使得：
- 给定当前信号，可以追溯"上次用哪个基因处理过类似信号，结果如何"
- 给定某个基因，可以查询"它在哪些信号下成功过/失败过"

#### 1.4.3 图推理在读取时计算

边聚合（aggregateEdges）、半衰期衰减（decayWeight）、概率估计（edgeExpectedSuccess）**不在写入时计算**，而是在 `getMemoryAdvice()` 读取时实时聚合。

**设计动机**：
- 写入极快（仅追加一行 JSON）
- 推理逻辑可以自由修改，无需迁移历史数据
- 每次读取都是基于全量历史的最新计算结果

#### 1.4.4 离线优先

- 默认使用本地 JSONL 存储，无需任何外部服务
- Remote Adapter 通过 `MEMORY_GRAPH_PROVIDER=remote` 激活，写操作先本地后远程
- 远程失败时自动降级到本地，保证离线可运行

---

## 💾 2. 存储设计

### 2.1 文件布局

```
${EVOLUTION_DIR}/
├── memory_graph.jsonl          # 追加日志（只追加，从不删除）
└── memory_graph_state.json     # 可变状态（last_action）

${MEMORY_DIR}/
├── YYYY-MM-DD.md               # 每日日志（当天事件摘要）
└── narrative.md                # 人类可读叙事时间线（Markdown）

${EVOLUTION_DIR}/
├── evolution_state.json        # Cycle 计数器 + 最后运行时间
├── solidify_state.json         # Solidify 状态（run_id、baseline 等）
└── dormant_hypothesis.json     # 中断假设（TTL=1h）
```

### 2.2 JSONL 追加日志设计

#### 写入机制

```javascript
function appendJsonl(filePath, obj) {
  const dir = path.dirname(filePath);
  ensureDir(dir);
  fs.appendFileSync(filePath, JSON.stringify(obj) + '\n', 'utf8');
}
```

每次事件写入一条 JSON 行，`\n` 分隔符。**O(1) 追加，无锁竞争**。

#### 读取机制（只读尾部）

```javascript
function tryReadMemoryGraphEvents(limitLines = 2000) {
  // 如果文件 ≤ 512KB：全量读取
  // 如果文件 > 512KB：从尾部读取 512KB，跳过不完整的行
  const TAIL_BYTES = 512 * 1024;
  const stat = fs.statSync(p);
  if (stat.size <= TAIL_BYTES) {
    raw = fs.readFileSync(p, 'utf8');
  } else {
    // seek 到 size - 512KB，读取
    fs.readSync(fd, buf, 0, TAIL_BYTES, stat.size - TAIL_BYTES);
    raw = buf.toString('utf8');
    // 跳过第一个不完整的行（可能从中间截断）
    const firstNewline = raw.indexOf('\n');
    if (firstNewline >= 0) raw = raw.slice(firstNewline + 1);
  }
  // 解析、过滤无效行、返回最近 limitLines 条
}
```

**设计动机**：
- 避免全量读取（GB 级文件）
- 2000 条事件已足够覆盖最近数百个 cycle 的图推理
- 超过 2000 条时，旧事件对当前决策影响已通过衰减降低

### 2.3 原子写入（状态文件）

```javascript
function writeJsonAtomic(filePath, obj) {
  const tmp = `${filePath}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(obj, null, 2) + '\n');
  fs.renameSync(tmp, filePath);  // POSIX 原子替换
}
```

### 2.4 Signal Key 规范化

```javascript
function computeSignalKey(signals) {
  const list = normalizeSignalsForMatching(signals);
  const uniq = Array.from(new Set(list.filter(Boolean))).sort();
  return uniq.join('|') || '(none)';
}

function normalizeErrorSignature(text) {
  return s
    .toLowerCase()
    .replace(/[a-z]:\\[^ \n\r\t]+/gi, '<path>')  // Windows 路径
    .replace(/\/[^ \n\r\t]+/g, '<path>')           // Unix 路径
    .replace(/\b0x[0-9a-f]+\b/gi, '<hex>')
    .replace(/\b\d+\b/g, '<n>')
    .replace(/\s+/g, ' ')
    .slice(0, 220);
}
```

**目的**：让相似错误归一化到同一个 key，经验可以被累积。

---

## 🔍 3. 信号提取机制

### 3.1 信号分类体系

signals.js 定义了 **14 类机会信号**（OPPORTUNITY_SIGNALS）：

```javascript
var OPPORTUNITY_SIGNALS = [
  'user_feature_request',        // 用户功能请求
  'user_improvement_suggestion', // 用户改进建议
  'perf_bottleneck',             // 性能瓶颈
  'capability_gap',             // 能力缺口
  'stable_success_plateau',      // 稳定成功 plateau（创新信号）
  'external_opportunity',        // 外部机会
  'recurring_error',            // 反复错误
  'unsupported_input_type',      // 不支持的输入类型
  'evolution_stagnation_detected', // 进化停滞
  'repair_loop_detected',       // 修复循环
  'force_innovation_after_repair_loop', // 强制创新
  'tool_bypass',               // 工具绕过
  'curriculum_target',          // 课程目标
];
```

**额外 6 类防御信号**（在 extractSignals 中动态添加）：
- `log_error` — 日志中有错误
- `errsig:<normalized>` — 错误签名
- `memory_missing` / `user_missing` — 文件缺失
- `session_logs_missing` — 会话日志缺失
- `windows_shell_incompatible` — Windows Shell 不兼容
- `path_outside_workspace` — 路径越界

### 3.2 提取流程

```
输入文本拼接
  corpus = session + todayLog + memorySnippet + userSnippet
          │
          ▼
┌──────────────────────────────────────────────────┐
│  Phase 1: 防御信号检测                            │
│  - log_error（正则匹配 [error]|error:|exception:） │
│  - errsig（提取错误行并归一化）                    │
│  - memory/user/session_missing                    │
│  - recurring_error（3+ 次重复错误）                 │
│  - tool_bypass（检测裸 node/python/curl 执行）    │
└────────────────┬─────────────────────────────────┘
                 │ 合并到 signals[]
                 ▼
┌──────────────────────────────────────────────────┐
│  Phase 2: 机会信号检测                            │
│  - user_feature_request（EN/ZH-CN/ZH-TW/JA）      │
│  - user_improvement_suggestion（4 语种）           │
│  - perf_bottleneck（慢/超时/高 CPU）               │
│  - capability_gap（not supported/cannot）          │
│  - unsupported_input_type（MIME 类型）             │
│  - tool_usage（高频工具检测，阈值 10 次）          │
│  - repeated_tool_usage:exec（5 次 exec）           │
└────────────────┬─────────────────────────────────┘
                 │ 合并到 signals[]
                 ▼
┌──────────────────────────────────────────────────┐
│  Phase 3: 去重与饱和                              │
│  - analyzeRecentHistory() 分析最近 8 个事件        │
│  - 抑制 3+ 次出现的信号                           │
│  - 连续 3+ 次 repair → 注入 force_innovation     │
│  - 连续 4+ 次空循环 → 注入 stable_success_plateau│
│  - 连续 5+ 次空循环 → 注入 force_steady_state    │
│  - 连续 3+ 次失败 → 注入 consecutive_failure_*   │
│  - 5+ 次失败 → 注入 failure_loop_detected        │
└──────────────────────────────────────────────────┘
```

### 3.3 多语言支持（4 语种）

signals.js 是 EvoMap 中多语言支持最完善的模块：

| 语言 | 模式 | 示例 |
|------|------|------|
| EN | `\b(add\|implement\|create\|build)\b...feature\b` | "add feature X" |
| ZH-CN | `加个\|实现一下\|做个\|想要\s*一个` | "加个功能" |
| ZH-TW | `加個\|實現一下\|做個\|請加` | "加個功能" |
| JA | `追加\|実装\|作って\|機能を` | "機能を追加" |

### 3.4 饱和检测阈值

| 条件 | 阈值 | 注入信号 |
|------|------|---------|
| 连续 repair | ≥ 3 | `force_innovation_after_repair_loop` |
| 连续空循环 | ≥ 3 | `evolution_saturation` |
| 连续空循环 | ≥ 5 | `force_steady_state` |
| 空循环占比 | ≥ 50% in 8 events | `stable_success_plateau` |
| 连续失败 | ≥ 3 | `consecutive_failure_streak_N` |
| 连续失败 | ≥ 5 | `failure_loop_detected` + ban top gene |
| 失败率 | ≥ 75% in 8 events | `high_failure_ratio` + `force_innovation` |

---

## 📊 4. 图检索与推理机制

### 4.1 核心函数：getMemoryAdvice()

这是记忆图的读取入口，在每次进化 cycle 开始时调用：

```javascript
function getMemoryAdvice({ signals, genes, driftEnabled }) {
  const events = tryReadMemoryGraphEvents(2000);  // 尾部 2000 条
  const edges = aggregateEdges(events);           // Signal→Gene 边聚合
  const geneOutcomes = aggregateGeneOutcomes(events); // Gene→Outcome 边聚合

  const curKey = computeSignalKey(signals);       // 当前信号 key
  const candidateKeys = [];                        // 候选信号 key（精确+相似）

  // 精确匹配：当前 key
  candidateKeys.push({ key: curKey, sim: 1 });
  seenKeys.add(curKey);

  // 相似匹配：Jaccard ≥ 0.34 的历史 key
  for (const ev of events) {
    const k = ev.signal?.key;
    if (seenKeys.has(k)) continue;
    const sim = jaccard(curSignals, ev.signal?.signals || []);
    if (sim >= 0.34) {
      candidateKeys.push({ key: k, sim });
    }
  }

  // 对每个基因，聚合所有候选 key 的边得分
  for (const ck of candidateKeys) {
    for (const g of genes) {
      const edge = edges.get(`${ck.key}::${g.id}`);
      const gEdge = geneOutcomes.get(g.id);

      // Signal→Gene 边得分
      if (edge) {
        const ex = edgeExpectedSuccess(edge, { half_life_days: 30 });
        const weighted = ex.value * ck.sim;  // 相似度加权
        cur.best = Math.max(cur.best, weighted);
        cur.attempts = Math.max(cur.attempts, ex.total);
      }

      // Gene→Outcome 全局先验（独立于信号）
      if (gEdge) {
        const gx = edgeExpectedSuccess(gEdge, { half_life_days: 45 });
        cur.prior = Math.max(cur.prior, gx.value);
      }

      byGene.set(g.id, cur);
    }
  }

  // 综合得分 = best_signal_edge + 0.12 * prior_global
  // 低效路径抑制：attempts ≥ 2 且 best < 0.18 → ban（除非 drift）
  // 稀疏抑制：attempts < 2 且 prior < 0.12 且 prior_attempts ≥ 3 → ban
  // ...

  return { preferredGeneId, bannedGeneIds, explanation };
}
```

### 4.2 Jaccard 相似度

```javascript
function jaccard(aList, bList) {
  const aNorm = normalizeSignalsForMatching(aList);
  const bNorm = normalizeSignalsForMatching(bList);
  const a = new Set(aNorm.map(String));
  const b = new Set(bNorm.map(String));

  let inter = 0;
  for (const x of a) if (b.has(x)) inter++;
  const union = a.size + b.size - inter;
  return union === 0 ? 0 : inter / union;
}
```

**阈值 0.34**：经验值，平衡精确性和召回率。

### 4.3 边聚合（Edge Aggregation）

```javascript
function aggregateEdges(events) {
  const map = new Map();
  for (const ev of events) {
    if (ev.kind !== 'outcome') continue;
    const signalKey = ev.signal?.key || '(none)';
    const geneId = ev.gene?.id;
    if (!geneId) continue;

    const k = `${signalKey}::${geneId}`;
    const cur = map.get(k) || { signalKey, geneId, success: 0, fail: 0, last_ts: null };

    if (ev.outcome?.status === 'success') cur.success++;
    else if (ev.outcome?.status === 'failed') cur.fail++;

    const ts = ev.ts || ev.created_at;
    if (ts && Date.parse(ts) > Date.parse(cur.last_ts || 0)) {
      cur.last_ts = ts;
      cur.last_score = ev.outcome?.score;
    }
    map.set(k, cur);
  }
  return map;
}
```

### 4.4 置信度计算：Laplace + 指数半衰期

```javascript
function edgeExpectedSuccess(edge, opts) {
  const { success = 0, fail = 0, last_ts = null } = edge;
  const total = success + fail;

  // Laplace 平滑：避免 0/1 极端概率
  const p = (success + 1) / (total + 2);

  // 指数半衰期衰减
  const halfLifeDays = opts?.half_life_days ?? 30;
  const w = decayWeight(last_ts, halfLifeDays);

  return {
    p,           // Laplace 平滑后的成功概率
    w,           // 衰减权重 (0, 1]
    total,       // 总尝试次数
    value: p * w // 综合得分
  };
}

function decayWeight(updatedAtIso, halfLifeDays) {
  if (!Number.isFinite(halfLifeDays) || halfLifeDays <= 0) return 1;
  const ageDays = (Date.now() - Date.parse(updatedAtIso)) / (1000 * 60 * 60 * 24);
  return Math.pow(0.5, ageDays / halfLifeDays);
}
```

**双半衰期设计**：
- Signal→Gene 边：`half_life_days = 30`（较快衰减，信号上下文敏感性高）
- Gene→Outcome 全局边：`half_life_days = 45`（较慢衰减，基因整体表现更稳定）

### 4.5 得分组合公式

```javascript
for (const [geneId, info] of byGene.entries()) {
  // 综合得分 = 信号边得分 + 12% * 全局先验
  const combined = info.best > 0
    ? info.best + info.prior * 0.12
    : info.prior * 0.4;

  // 抑制规则：
  // 1. 低效路径：attempts ≥ 2 且 best < 0.18 → ban（无 drift）
  // 2. 稀疏抑制：attempts < 2 且 prior_attempts ≥ 3 且 prior < 0.12 → ban（无 drift）
  if (!driftEnabled && info.attempts >= 2 && info.best < 0.18) {
    bannedGeneIds.add(geneId);
  }
}
```

---

## 🎯 5. 基因选择器

### 5.1 选择流程总览

```
signals[] + genes[] + memoryAdvice
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  selectGeneAndCapsule()                             │
│                                                     │
│  1. banGenesFromFailedCapsules()                   │
│  2. selectGene()                                   │
│     → scoreGene() = exact + semantic + tag          │
│     → scoreGeneLearning() = history + anti-pattern  │
│     → driftIntensity 计算                           │
│     → diversity-directed drift                      │
│  3. selectCapsule()                                │
│  4. buildSelectorDecision()                        │
└─────────────────────────────────────────────────────┘
```

### 5.2 三维评分体系

| 维度 | 权重 | 说明 |
|------|------|------|
| **Exact Match** | 高 | 信号 key 完全匹配 |
| **Semantic** | 0.4（可配置） | Bag-of-Words Cosine 相似度 |
| **Tag Overlap** | - | problem/action/area 标签重叠数 |
| **Learning History** | ±0.12~0.22 | 基因自己的历史成功/失败记录 |
| **Anti-pattern** | -0.18~-0.4 | 近期失败模式的惩罚 |
| **Epigenetic** | ±0.1 | 环境特定印记 |

### 5.3 漂移强度（Drift Intensity）

遗传学启发：有效种群越小 → 遗传漂变越强

```javascript
function computeDriftIntensity(opts) {
  const ne = effectivePopulationSize || genePoolSize || null;

  if (driftEnabled) {
    return ne && ne > 1 ? Math.min(1, 1 / Math.sqrt(ne) + 0.3) : 0.7;
  }

  if (ne != null && ne > 0) {
    // Ne=1: 1.0（纯随机）
    // Ne=25: 0.2
    // Ne=100: 0.1
    return Math.min(1, 1 / Math.sqrt(ne));
  }

  return 0;
}
```

---

## 🔄 6. 饱和检测与降级策略

### 6.1 饱和问题分类

| 问题 | 表现 | 根因 |
|------|------|------|
| **空循环饱和** | 连续多次 cycle 产出 0 文件变更 | 进化空间耗尽 |
| **修复循环** | 连续多次 repair intent 但持续失败 | 同一问题无法通过代码修复解决 |
| **失败循环** | 连续多次 outcome.status = failed | 方法论错误，需要换基因 |
| **高失败率** | 最近 8 个 cycle 中 75%+ 失败 | 系统性错误 |

### 6.2 回路熔断器（Circuit Breaker）

```javascript
function checkRepairLoopCircuitBreaker() {
  const threshold = REPAIR_LOOP_THRESHOLD;  // 默认 10
  const recent = readAllEvents().slice(-threshold);

  if (recent.length >= threshold) {
    const allRepairFailed = recent.every(e =>
      e.intent === 'repair' && e.outcome?.status === 'failed'
    );
    if (allRepairFailed) {
      process.env.FORCE_INNOVATION = 'true';
    }
  }
}
```

**10 次连续 repair + 全失败** → 强制创新，绕过当前基因策略。

### 6.3 降级策略

- **Idle Gating**：饱和信号存在时，停止向 Hub 发送 API 请求
- **Force Steady State**：`force_steady_state` 信号存在时跳过 Hub 任务获取
- **Drift 降级**：连续失败 → ban 基因 + 启用漂移模式

---

## 🔌 7. Adapter 模式与远程扩展

### 7.1 为什么需要 Adapter 模式？

EvoMap 是一个**离线优先**的自进化框架。核心设计原则：

> **本地实现是默认的、完整的、可独立运行的。远程服务是可选的、降级友好的。**

### 7.2 接口契约

memoryGraphAdapter.js 定义了 9 个方法，任意 Provider 必须实现：

```javascript
// 必需方法
getAdvice({ signals, genes, driftEnabled })
  => { currentSignalKey, preferredGeneId, bannedGeneIds, explanation }

recordSignalSnapshot({ signals, observations }) => event
recordHypothesis({ signals, mutation, personality_state, selectedGene, ... }) => { hypothesisId, signalKey }
recordAttempt({ signals, mutation, personality_state, selectedGene, ... }) => { actionId, signalKey }
recordOutcome({ signals, observations }) => event | null
recordExternalCandidate({ asset, source, signals }) => event | null

// 只读方法
memoryGraphPath() => string
computeSignalKey(signals) => string
tryReadMemoryGraphEvents(limit) => event[]
```

### 7.3 关键设计决策

**本地优先写，远程异步同步**：
```javascript
recordSignalSnapshot(opts) {
  const ev = localGraph.recordSignalSnapshot(opts);  // 同步，写入本地
  remoteCall('/kg/ingest', { kind: 'signal', event: ev }).catch(() => {});  // 异步，忽略失败
  return ev;
}
```

**getAdvice 先远程后本地**：
```javascript
getAdvice: withFallback(
  localGraph.getMemoryAdvice,  // fallback
  async (opts) => remoteCall('/kg/advice', opts)  // 先尝试远程
)
```

---

## 📖 8. 反思机制与叙事记忆

### 8.1 周期性反思（Reflection）

触发条件：
- 每 20 个 cycle 触发一次
- 连续 5 个相同意图
- 连续 3 个失败

反思内容：
- 近期事件统计（成功率、平均分、基因频率）
- 信号分布
- Memory Advice 建议的基因
- 叙事摘要

### 8.2 叙事记忆（Narrative Memory）

```markdown
### [2026-05-03 01:23:45] REPAIR - success
- Gene: gene_self_repair_v3 | Score: 0.85 | Scope: 3 files, 127 lines
- Signals: [log_error, errsig:TypeError]
- Why: Fix TypeError in session handler caused by null reference
```

**修剪策略**：
- 最多保留 30 条
- 最多 12000 字符
- 两者都超 → 优先保留最新条目

### 8.3 叙事 vs 图记忆：职责分离

| 维度 | Narrative Memory | Memory Graph |
|------|-----------------|--------------|
| 格式 | Markdown（人类可读） | JSONL（机器可读） |
| 粒度 | 粗（每个 cycle 一条） | 细（每个事件一条） |
| 主要读者 | 人类（调试、审计） | 代码（selector、advice） |
| 容量 | 30 条 / 12000 字符 | 无限（追加） |
| 推理依赖 | 否 | 是（边聚合、图推理） |

---

## 🧬 9. Solidify 机制与基因学习

### 9.1 整体定位

`solidify.js` 是整个进化循环的**终点**——当一个 cycle 执行完毕，`solidify()` 负责：
1. 验证变更质量（约束检查 + 验证命令 + 金丝雀测试）
2. 计算 outcome score（多阶段 PRM 评分）
3. 将学习反馈写回 gene（adaptGeneFromLearning）
4. 记录 EvolutionEvent 到 JSONL 图
5. 必要时创建新 Capsule（成功经验封装）

### 9.2 Gene 自适应学习

```javascript
function adaptGeneFromLearning({ gene, outcomeStatus, learningSignals, failureMode }) {
  // 成功时：扩展 signals_match（问题类型标签 + 区域标签）
  if (outcomeStatus === 'success') {
    for (const sig of learningSignals) {
      if (!seenSignal.has(sig) && (sig.startsWith('problem:') || sig.startsWith('area:'))) {
        gene.signals_match.push(sig);
      }
    }
  }

  // 追加学习历史（最多保留 20 条）
  gene.learning_history.push({
    at: nowIso(),
    outcome: outcomeStatus,
    mode: failureMode.mode,
    reason_class: failureMode.reasonClass,
    retryable: !!failureMode.retryable,
    learning_signals: learningSignals.slice(0, 12),
  });

  // 失败时：记录 anti_pattern（最多保留 12 条）
  if (outcomeStatus === 'failed') {
    gene.anti_patterns.push({
      at: nowIso(),
      mode: failureMode.mode,
      reason_class: failureMode.reasonClass,
      learning_signals: learningSignals.slice(0, 8),
    });
  }

  return gene;
}
```

### 9.3 表观遗传标记（Epigenetic Marks）

生物启发的环境适应机制。基因在不同环境中表现不同：

```javascript
function applyEpigeneticMarks(gene, envFingerprint, outcomeStatus) {
  const envContext = [platform, arch, nodeVersion].join('/') || 'unknown';

  if (outcomeStatus === 'success') {
    gene.epigenetic_marks.push({
      context: envContext,
      boost: Math.min(0.5, cur.boost + 0.05),
      reason: 'success_in_environment'
    });
  } else if (outcomeStatus === 'failed') {
    gene.epigenetic_marks.push({
      context: envContext,
      boost: Math.max(-0.5, cur.boost - 0.1),
      reason: 'failure_in_environment'
    });
  }
}
```

### 9.4 PRM 启发式多阶段评分

`computeProcessScores()` 将 outcome 分解为 8 个维度：

| 维度 | 权重 | 评分逻辑 |
|------|------|---------|
| `signal_quality` | 5% | 信号数量：0→0.4, n→min(1, 0.4+n*0.1) |
| `gene_selection` | 10% | 无基因→0.3, auto→0.7, 已有基因→0.9 |
| `mutation_quality` | 5% | 无mutation→0.3, 完整→0.8, 低风险→0.9 |
| `blast_control` | 15% | 0文件→0.4, ≤50%上限→1.0, 正常→0.7 |
| `constraint_compliance` | 25% | 每违规-0.25，最低0 |
| `validation_pass_rate` | 25% | 通过数/总数（无验证命令→0.5 penalty） |
| `protocol_compliance` | 10% | 每违规-0.3，最低0 |
| `canary_health` | 5% | 金丝雀失败→0，否则1 |

---

## ⚙️ 10. Skill Distiller：从经验 Capsule 到可复用 Gene

### 10.1 整体定位

`skillDistiller.js` 的核心职责是：

> **将多次成功的进化经验（Capsule）蒸馏成一个通用的、可被其他 Agent 发现和复用的 Gene。**

```
Evolution Cycles (N 次成功)
     │
     ▼
Capsule Store (capsules.json / capsules.jsonl)
     │
     ▼
skillDistiller.js  ──►  Distillation Pipeline
     │                   1. collectDistillationData()
     │                   2. analyzePatterns()
     │                   3. buildDistillationPrompt()
     │                   4. LLM synthesis
     │                   5. validateSynthesizedGene()
     │                   6. upsertGene()
     ▼
Gene Store (genes.json) ──► Hub Marketplace
```

### 10.2 触发条件

```javascript
function shouldDistill() {
  // 1. 环境变量关闭检查
  if (SKILL_DISTILLER === 'false') return false;

  // 2. 间隔检查（默认 24 小时）
  const elapsed = Date.now() - last_distillation_at;
  if (elapsed < DISTILLER_INTERVAL_HOURS * 3600000) return false;

  // 3. 最近 10 个 capsule 中至少 7 个成功
  const recentSuccess = recent.slice(-10).filter(isSuccess).length;
  if (recentSuccess < 7) return false;

  // 4. 总成功 capsule ≥ 10
  if (totalSuccess < DISTILLER_MIN_CAPSULES) return false;

  return true;
}
```

### 10.3 LLM 蒸馏提示词约束

| 约束 | 规则 |
|------|------|
| Gene ID | 必须以 `gene_distilled_` 开头 + 描述性 kebab-case |
| 禁止 | 时间戳、UUID、工具名（cursor/vscode）、随机数 |
| 信号 | 3-7 个通用领域词（`lowercase_snake_case`） |
| 策略 | 5-10 个可执行步骤（动词开头的祈使句） |
| 约束 | `max_files ≤ 12`，必须包含 `.git` 和 `node_modules` |
| 验证命令 | 必须以 `node / npm / npx` 开头 |

---

## 🔒 11. 安全、并发与运维保障

### 11.1 并发控制：文件锁

```javascript
function withFileLock(targetPath, fn) {
  const lockPath = targetPath + '.lock';
  const lockPath = _acquireLock(targetPath);  // O_EXCL 原子创建
  try {
    return fn();  // 临界区：读-改-写
  } finally {
    _releaseLock(lockPath);
  }
}
```

**Stale lock 检测**：读取锁文件中的 PID，用 `process.kill(pid, 0)` 验证进程是否存活。

### 11.2 GitOps 回滚

```javascript
// 1. 还原已跟踪文件的变更
rollbackTracked(repoRoot, mode);
// mode: 'hard'（默认）→ git reset --hard

// 2. 删除新增的未跟踪文件
rollbackNewUntrackedFiles({ repoRoot, baselineUntracked });
```

**关键文件保护**：20+ 个文件和路径永不删除（skills/、MEMORY.md、SOUL.md 等）。

### 11.3 隐私脱敏（Sanitization）

`sanitize.js` 定义了 26 种脱敏模式：

```javascript
const REDACT_PATTERNS = [
  /sk-[A-Za-z0-9]{20,}/g,          // OpenAI sk-
  /ghp_[A-Za-z0-9]{36,}/g,         // GitHub personal token
  /\/home\/[^\s"']+/g,             // Unix home dir
  /\/Users\/[^\s"']+/g,            // macOS home dir
  /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g,  // email
  // ...
];
```

### 11.4 Self-PR

当 evolver 在自身代码上成功优化（高 score + 连续成功 + 低 blast radius），将变更作为 PR 贡献回公开仓库。

**入参要求（全部满足才触发）**：
- score ≥ 0.82
- 成功 streak ≥ 3
- 变更文件 ≤ 8
- 变更行数 ≤ 400
- category = optimize + risk = low
- 24 小时内不重复

---

## 📊 12. 与 Claude-Mem 的架构对比

| 维度 | EvoMap/evolver | Claude-Mem (BlueCortexCE) |
|------|---------------|---------------------------|
| 存储介质 | JSONL 文件 | PostgreSQL + pgvector |
| 检索方式 | Jaccard 相似度（启发式） | 向量语义检索 |
| 记忆类型 | 因果图（Signal→Gene→Outcome） | 对话上下文 + Observation |
| 图推理 | 有（内存图边聚合） | 无（扁平向量检索） |
| 衰减模型 | 指数半衰期（可配置） | 无衰减（向量相似度） |
| 饱和检测 | 有（连续空循环/失败熔断） | 无 |
| Adapter 模式 | 有（Local + Remote） | 无 |
| 叙事记忆 | 有（Markdown 时间线） | 有（Summary + Observation） |
| 多语言支持 | 有（EN/ZH-TW/JA） | 无 |
| Gene 自学习 | 有（learning_history + anti_patterns） | 无直接对应 |
| 表观遗传 | 有（环境印记） | 无 |
| PRM 多维评分 | 有（8 维度） | 无（向量相似度） |
| Skill Distiller | 有（LLM 蒸馏 Gene） | 无 |
| ATP 市场 | 有（完整市场经济层） | 无 |
| 并发控制 | 有（文件锁） | 无（单进程设计） |
| GitOps 回滚 | 有 | 无 |
| 隐私脱敏 | 有（26 种模式） | 无 |

---

## 💡 13. 对 Claude-Mem 的借鉴价值

### 13.1 高优先级借鉴

| 设计 | EvoMap 做法 | Claude-Mem 改进建议 |
|------|------------|---------------------|
| **时间衰减** | 指数半衰期（Signal→Gene 30d，Gene→Outcome 45d） | 引入基于时间的向量权重衰减 |
| **饱和检测** | 连续空循环/失败熔断 | 引入会话活性检测 |
| **基因自学习** | learning_history + anti_patterns | 可在 Session 级别积累偏好 |
| **多维评分** | PRM 8 维度评分 | 可引入多维度 context quality score |
| **Adapter 模式** | Local/Remote 可插拔 | 引入多存储后端支持 |

### 13.2 中优先级借鉴

| 设计 | EvoMap 做法 | Claude-Mem 改进建议 |
|------|------------|---------------------|
| **信号系统** | 14 类信号 + 去重 | 可引入任务类型信号 |
| **叙事记忆** | Markdown 时间线 | 可引入决策日志 |
| **多语言支持** | EN/ZH-TW/JA | 可引入中文信号检测 |
| **并发控制** | 文件锁（O_EXCL） | 多实例部署时的协调 |

### 13.3 低优先级借鉴

| 设计 | EvoMap 做法 | Claude-Mem 改进建议 |
|------|------------|---------------------|
| **表观遗传** | 环境印记 | 跨环境部署场景较少 |
| **Skill Distiller** | LLM 蒸馏 Gene | 架构差异较大 |
| **ATP 市场** | 去中心化市场 | 需求不明确 |
| **Self-PR** | 自动贡献 | 需求不明确 |

---

## 📁 14. 关键文件索引

| 文件 | 行数 | 职责 |
|------|------|------|
| `src/gep/memoryGraph.js` | ~1000 | 核心图存储与推理引擎 |
| `src/gep/memoryGraphAdapter.js` | ~300 | Local/Remote 适配器 |
| `src/gep/narrativeMemory.js` | ~200 | Markdown 叙事日志 |
| `src/gep/signals.js` | ~500 | 信号提取与去重 |
| `src/gep/learningSignals.js` | ~300 | 结构化信号展开、标签匹配 |
| `src/gep/selector.js` | ~800 | 基因选择器（含漂移强度） |
| `src/gep/solidify.js` | ~1344 | Solidify 机制、PRM 评分、基因学习 |
| `src/gep/skillDistiller.js` | ~1344 | LLM 驱动的 Gene 蒸馏 |
| `src/gep/paths.js` | ~100 | 存储路径解析 |
| `src/gep/sanitize.js` | ~500 | 隐私脱敏 |
| `src/gep/assetStore.js` | ~500 | 文件锁、原子写入 |

---

_由 Claude Code 分析生成 | 2026-05-07 | BlueCortexCE 项目文档_
