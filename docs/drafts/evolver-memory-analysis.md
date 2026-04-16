# Evolver 记忆系统深度分析

> **文档状态**: v0.1 (初稿)
> **分析目标**: 为 BlueCortexCE（旁路型记忆系统）提供可落地的借鉴建议
> **数据来源**: `/Users/yangjiefeng/Documents/EvoMap/evolver/`
> **最后更新**: 2026-04-16 14:00

---

## ⚠️ 架构定位认知（阅读前必读）

**Evolver 与 BlueCortexCE 是两种截然不同的架构：**

| 维度 | Evolver | BlueCortexCE |
|------|---------|--------------|
| 架构 | **GEP 协议驱动的自进化引擎** | **旁路型外部记忆增强系统** |
| 核心 | 通过基因（Gene）和胶囊（Capsule）实现可审计的进化 | 提供记忆存储与检索 API |
| 记忆 | memoryGraph (JSONL) + narrativeMemory (MD) + signals | PostgreSQL + pgvector |
| 进化 | 协议约束的突变+验证循环 | 通过 API 提供记忆能力 |

**分析原则**：每个发现必须经过"翻译"——不是直接搬套 Evolver 的做法，而是思考：**在旁路型架构下，这个设计思想如何落地？**

---

## 目录

1. [memoryGraph.js — 核心因果记忆图谱](#1-memorygraphjs--核心因果记忆图谱)
2. [narrativeMemory.js — 叙事记忆](#2-narrativememoryjs--叙事记忆)
3. [signals.js — 信号提取机制](#3-signalsjs--信号提取机制)
4. [personality.js — 个性化状态管理](#4-personalityjs--个性化状态管理)
5. [learningSignals.js — 学习信号扩展](#5-learningsignalsjs--学习信号扩展)
6. [Evolve.js — 核心进化循环](#6-evolvejs--核心进化循环)
7. [与 Hermes 的架构对比](#7-与-hermes-的架构对比)
8. [BlueCortexCE 借鉴建议汇总](#8-bluecortexce-借鉴建议汇总)

---

## 1. memoryGraph.js — 核心因果记忆图谱

**文件**: `src/gep/memoryGraph.js` (787 lines)

### 1.1 核心设计原则

Evolver 的 memoryGraph 采用 **append-only JSONL** 存储，这与 Hermes 的 SQLite/FTS5 方案形成鲜明对比：

| 特性 | Evolver | Hermes |
|------|---------|--------|
| 存储格式 | JSONL (append-only) | SQLite + FTS5 |
| 数据结构 | 事件流 (Events) | Relational tables |
| 查询方式 | 全量扫描 + 内存聚合 | SQL/FTS 查询 |
| 事务支持 | 无 (天然不可变) | ACID |

### 1.2 事件类型体系

memoryGraph 定义了 4 种核心事件类型，通过 `kind` 字段区分：

```javascript
// src/gep/memoryGraph.js:310-330 (伪代码)
const eventKinds = {
  'signal':     '信号快照',
  'hypothesis': '假设记录',
  'attempt':    '行动记录',
  'outcome':    '结果记录',
};
```

**Signal Event** — 记录当前周期检测到的信号：
```javascript
// src/gep/memoryGraph.js:340-360
{
  type: 'MemoryGraphEvent',
  kind: 'signal',
  id: `mge_${Date.now()}_${hash}`,
  ts: '2026-04-16T...',
  signal: {
    key: 'log_error|errsig_norm:abc123|capability_gap',  // 归一化信号键
    signals: ['log_error', 'errsig:...', 'capability_gap'],
    error_signature: 'TypeError: Cannot read property...',  // 原始错误签名
  },
  observed: { /* observations 对象 */ },
}
```

**Hypothesis Event** — 记录 Signal → Gene 的因果假设：
```javascript
// src/gep/memoryGraph.js:370-410
{
  type: 'MemoryGraphEvent',
  kind: 'hypothesis',
  signal: { key, signals, error_signature },
  hypothesis: {
    id: 'hyp_xxx',
    text: 'Given signal_key=..., selecting gene=xxx under mode=directed...',
    predicted_outcome: { status: null, score: null },  // 预测待验证
  },
  mutation: { id, category, trigger_signals, target, risk_level },
  personality: { key: 'rigor=0.7|...', state: {...} },
  gene: { id, category },
  action: { drift, selected_by, selector },
  capsules: { used: [...] },
}
```

**Attempt Event** — 记录实际执行的行动：
```javascript
// src/gep/memoryGraph.js:420-460
{
  type: 'MemoryGraphEvent',
  kind: 'attempt',
  action: {
    id: 'act_xxx',
    drift: false,
    selected_by: 'memory_graph+selector',
  },
  hypothesis: { id: 'hyp_xxx' },  // 关联假设
}
```

**Outcome Event** — 记录执行结果：
```javascript
// src/gep/memoryGraph.js:490-530
{
  type: 'MemoryGraphEvent',
  kind: 'outcome',
  signal: { key, signals },
  outcome: {
    status: 'success' | 'failed',
    score: 0.85,
    blast_radius: { files: 3, lines: 150 },
  },
}
```

### 1.3 核心算法

#### 1.3.1 信号键计算 (computeSignalKey)

**文件**: `src/gep/memoryGraph.js:50-80`

```javascript
function computeSignalKey(signals) {
  // 1. 提取 errsig 并归一化
  const normalized = signals.map(s => {
    if (s.startsWith('errsig:')) {
      // 归一化: 路径→<path>, 数字→<n>, hex→<hex>
      return `errsig_norm:${stableHash(normalizeErrorSignature(s))}`;
    }
    return s;
  });
  // 2. 去重 + 排序
  const uniq = Array.from(new Set(normalized)).sort();
  return uniq.join('|') || '(none)';
}
```

**Evolver 为什么这样做**: 错误签名的路径/数字变体会导致相同的根因被识别为不同的信号。归一化让相同类型错误的多次出现能合并统计。

#### 1.3.2 Jaccard 相似度 (jaccard)

**文件**: `src/gep/memoryGraph.js:155-175`

```javascript
function jaccard(aList, bList) {
  // 先归一化信号
  const aNorm = normalizeSignalsForMatching(aList);
  const bNorm = normalizeSignalsForMatching(bList);
  const a = new Set(aNorm);
  const b = new Set(bNorm);
  
  if (a.size === 0 && b.size === 0) return 1;
  if (a.size === 0 || b.size === 0) return 0;
  
  const inter = [...a].filter(x => b.has(x)).length;
  const union = a.size + b.size - inter;
  return union === 0 ? 0 : inter / union;
}
```

**阈值**: 当 Jaccard ≥ 0.34 时，认为两个信号集"足够相似"。

#### 1.3.3 指数衰减 (decayWeight)

**文件**: `src/gep/memoryGraph.js:177-188`

```javascript
function decayWeight(updatedAtIso, halfLifeDays = 30) {
  const ageDays = (Date.now() - Date.parse(updatedAtIso)) / (1000*60*60*24);
  // 指数半衰期衰减: weight = 0.5^(age/hl)
  return Math.pow(0.5, ageDays / halfLifeDays);
}
```

**Evolver 为什么这样做**: 旧的进化记录应该权重更低。30 天的半衰期意味着 60 天前的记录权重只有 25%。

#### 1.3.4 边期望成功 (edgeExpectedSuccess)

**文件**: `src/gep/memoryGraph.js:195-210`

```javascript
function edgeExpectedSuccess(edge, opts) {
  const succ = edge.success || 0;
  const fail = edge.fail || 0;
  const total = succ + fail;
  // Laplace smoothing: (succ + 1) / (total + 2)
  // 避免 0/1 的极端概率
  const p = (succ + 1) / (total + 2);
  const w = decayWeight(edge.last_ts || '', opts.half_life_days || 30);
  return { p, w, total, value: p * w };
}
```

#### 1.3.5 基因推荐算法 (getMemoryAdvice)

**文件**: `src/gep/memoryGraph.js:220-295`

核心逻辑：
1. 计算当前信号键
2. 扫描历史事件，查找相似信号键 (Jaccard ≥ 0.34)
3. 聚合 Signal→Gene 边的成功/失败次数
4. 计算 Laplace 平滑后的成功概率
5. 乘以衰减权重 + 相似度
6. **低效路径抑制**: 尝试≥2次但期望值<0.18 的基因被 ban
7. **先验抑制**: 全局成功率<12% 的基因被 ban

```javascript
// 最终评分公式
const combined = info.best > 0 
  ? info.best + info.prior * 0.12  // 信号边 + 12% 基因先验
  : info.prior * 0.4;               // 只有先验时权重更低
```

### 1.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 错误签名归一化 | 路径/数字/hex → 占位符，稳定 hash | **高优先级**: 在 BlueCortexCE 的错误观察记录中，实现类似的错误签名归一化，避免相同错误因路径不同被识别为不同模式 |
| 半衰期衰减 | 30天半衰期，旧记录权重指数降低 | **中优先级**: BlueCortexCE 可对历史观察实现时间衰减权重 |
| Laplace 平滑 | 避免 0/1 极端概率 | **高优先级**: 任何成功/失败统计都应使用 Laplace 平滑 |
| 低效路径抑制 | 连续失败 + 低成功率 → ban | **中优先级**: BlueCortexCE 可记录"已验证无效的检索模式" |
| 归因链 | signal → hypothesis → attempt → outcome | **高优先级**: BlueCortexCE 的 observation 应支持归因链追踪 |

---

## 2. narrativeMemory.js — 叙事记忆

**文件**: `src/gep/narrativeMemory.js` (108 lines)

### 2.1 设计原则

narrativeMemory 是 **append-only Markdown** 文件，记录进化决策的叙事性历史：

```
# Evolution Narrative

A chronological record of evolution decisions and outcomes.

### [2026-04-16 02:00] REPAIR - success
- Gene: error-handling-v2 | Score: 0.85 | Scope: 2 files, 80 lines
- Signals: [log_error, errsig:TypeError..., capability_gap]
- Why: Detected recurring TypeError in API calls
- Strategy:
  1. Add try-catch wrapper
  2. Implement fallback
- Result: Error handling improved, API resilience enhanced.
```

### 2.2 关键特性

#### 2.2.1 自动裁剪 (trimNarrative)

**文件**: `src/gep/narrativeMemory.js:55-80`

```javascript
const MAX_NARRATIVE_ENTRIES = 30;
const MAX_NARRATIVE_SIZE = 12000;

function trimNarrative(content) {
  if (content.length <= MAX_NARRATIVE_SIZE) return content;
  
  // 保留最新 30 条记录
  const entries = content.split(/(?=^### \[)/m);
  while (entries.length > MAX_NARRATIVE_ENTRIES) {
    entries.shift();  // 删除最旧的
  }
  
  // 如果还是太大，保留最后 N 条
  while (result.length > MAX_NARRATIVE_SIZE && entries.length > 1) {
    entries.shift();
  }
}
```

#### 2.2.2 摘要加载 (loadNarrativeSummary)

**文件**: `src/gep/narrativeMemory.js:82-108`

支持加载最近 N 条记录的可配置大小摘要，用于 reflection 阶段的上下文注入。

### 2.3 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| Markdown 叙事格式 | 人类可读 + Markdown 结构化 | **高优先级**: BlueCortexCE 的 summary 可以采用类似格式，保留决策上下文 |
| 固定上限裁剪 | 30 条 / 12000 chars 双限制 | **中优先级**: BlueCortexCE 的 summary 可配置类似上限 |
| 策略链记录 | 记录 Strategy + Result | **高优先级**: BlueCortexCE 的 summary 应记录"当时为什么这样做" |

---

## 3. signals.js — 信号提取机制

**文件**: `src/gep/signals.js` (444 lines)

### 3.1 信号分类体系

Evolver 的信号分为三大类：

#### 3.1.1 防御性信号 (Defensive Signals)
- `log_error` — 检测到错误标记
- `errsig:...` — 错误签名（带归一化）
- `memory_missing` — MEMORY.md 缺失
- `session_logs_missing` — 会话日志缺失
- `windows_shell_incompatible` — Windows 兼容性问题

#### 3.1.2 机会信号 (Opportunity Signals)
- `user_feature_request` — 用户功能请求（4语言支持）
- `user_improvement_suggestion` — 改进建议
- `perf_bottleneck` — 性能瓶颈
- `capability_gap` — 能力缺失

#### 3.1.3 稳定性信号 (Robustness Signals)
- `recurring_error` — 重复出现 3+ 次的错误
- `empty_cycle_loop_detected` — 空循环检测
- `evolution_saturation` — 进化饱和

### 3.2 多语言支持

**文件**: `src/gep/signals.js:180-280`

Evolver 支持 4 种语言的 feature/improvement 检测：

```javascript
// 英语
/I want|I need|please add|can you add/

// 简体中文
/加个|实现一下|做个|想要\s*一个|需要\s*一个|帮我加|帮我开发/

// 繁体中文
/加個|實現一下|做個|想要一個|請加/

// 日语
/追加|実装|作って|機能を|追加して/
```

**Evolver 为什么这样做**: Evolver 是开源项目，用户遍布全球。多语言支持确保非英语用户的需求也能被识别。

### 3.3 历史感知抑制

**文件**: `src/gep/signals.js:50-100`

```javascript
function analyzeRecentHistory(recentEvents) {
  // 统计最后 8 个事件的信号频率
  const signalFreq = {};
  for (const evt of tail) {
    for (const s of evt.signals) {
      signalFreq[normalizeKey(s)] = (signalFreq[normalizeKey(s)] || 0) + 1;
    }
  }
  
  // 抑制 3+ 次出现的信号（正在被反复处理）
  const suppressedSignals = new Set();
  for (const [key, count] of Object.entries(signalFreq)) {
    if (count >= 3) suppressedSignals.add(key);
  }
  
  // 连续空循环检测
  let consecutiveEmptyCycles = 0;
  for (let i = recent.length - 1; i >= 0; i--) {
    if (isEmptyCycle(recent[i])) consecutiveEmptyCycles++;
    else break;
  }
}
```

### 3.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 多语言信号提取 | 支持 EN/ZH-CN/ZH-TW/JA | **高优先级**: BlueCortexCE 的信号/观察提取应支持多语言 |
| 错误签名归一化 | errsig + hash | **高优先级**: 与 memoryGraph 的归一化联动 |
| 重复信号抑制 | 3+ 次 → suppress | **中优先级**: BlueCortexCE 可实现"已处理信号"的冷却期 |
| 空循环检测 | 连续 N 次 blast_radius=0 | **高优先级**: BlueCortexCE 可检测"无效检索循环" |

---

## 4. personality.js — 个性化状态管理

**文件**: `src/gep/personality.js` (379 lines)

### 4.1 人格状态结构

**文件**: `src/gep/personality.js:35-50`

```javascript
function defaultPersonalityState() {
  return {
    rigor: 0.7,           // 严谨性: 验证严格度
    creativity: 0.35,     // 创造性: 突变幅度
    verbosity: 0.25,      // 冗长性: 输出详细度
    risk_tolerance: 0.4,  // 风险承受: 高风险操作意愿
    obedience: 0.85,      // 服从性: 协议遵循度
  };
}
```

### 4.2 人格键 (personalityKey)

**文件**: `src/gep/personality.js:80-90`

```javascript
function personalityKey(state) {
  const s = normalizePersonalityState(state);
  const step = 0.1;  // 离散化精度
  return [
    `rigor=${round(s.rigor, step)}`,
    `creativity=${round(s.creativity, step)}`,
    `verbosity=${round(s.verbosity, step)}`,
    `risk_tolerance=${round(s.risk_tolerance, step)}`,
    `obedience=${round(s.obedience, step)}`,
  ].join('|');
}
// 例: 'rigor=0.7|creativity=0.4|verbosity=0.2|risk_tolerance=0.4|obedience=0.9'
```

### 4.3 自然选择机制

**文件**: `src/gep/personality.js:150-280`

Evolver 实现了人格的"自然选择"：
1. **探索**: 每次运行随机小幅突变人格参数
2. **适应度评估**: 基于进化结果调整参数权重
3. **精英保留**: 表现好的人格状态被保留并微调

### 4.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 多维人格参数 | rigor/creativity/risk_tolerance 等 | **低优先级**: BlueCortexCE 作为服务不需要人格系统 |
| 离散化 personalityKey | 0.1 步长，避免微小波动 | **低优先级** |
| 自然选择 | 人格突变 + 结果反馈 | **低优先级** |

---

## 5. learningSignals.js — 学习信号扩展

**文件**: `src/gep/learningSignals.js` (89 lines)

### 5.1 信号扩展 (expandSignals)

**文件**: `src/gep/learningSignals.js:15-60`

```javascript
function expandSignals(signals, extraText) {
  const tags = [];
  
  // 1. 原始信号 + 前缀提取
  for (const signal of signals) {
    add(tags, signal);
    const base = signal.split(':')[0];  // errsig:xxx → errsig
    if (base !== signal) add(tags, base);
  }
  
  // 2. 基于关键词的语义扩展
  const text = (raw.join(' ') + ' ' + extraText).toLowerCase();
  
  if (/(error|exception|failed)/.test(text)) {
    add(tags, 'problem:reliability');
    add(tags, 'action:repair');
  }
  if (/(protocol|prompt|audit|gep)/.test(text)) {
    add(tags, 'problem:protocol');
    add(tags, 'action:optimize');
  }
  if (/(perf|performance|bottleneck)/.test(text)) {
    add(tags, 'problem:performance');
    add(tags, 'action:optimize');
  }
  if (/(feature|capability_gap)/.test(text)) {
    add(tags, 'problem:capability');
    add(tags, 'action:innovate');
  }
  // ... 更多规则
}
```

### 5.2 标签评分 (scoreTagOverlap)

**文件**: `src/gep/learningSignals.js:65-85`

```javascript
function scoreTagOverlap(gene, signals) {
  const signalTags = expandSignals(signals, '');
  const geneTagList = geneTags(gene);
  
  const signalSet = new Set(signalTags);
  let hits = 0;
  for (const tag of geneTagList) {
    if (signalSet.has(tag)) hits++;
  }
  return hits;  // 返回匹配的标签数
}
```

### 5.3 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 语义标签扩展 | 关键词 → problem:xxx/action:xxx/area:xxx | **高优先级**: BlueCortexCE 的检索系统应实现语义标签扩展 |
| 多层标签 | base signal + semantic category | **高优先级**: observation 可携带多层标签 |
| 标签重叠评分 | gene-tags ∩ signal-tags | **中优先级**: 可用于检索结果排序 |

---

## 6. evolve.js — 核心进化循环

**文件**: `src/evolve.js` (完整源码)

### 6.1 主循环概览

Evolver 的核心进化循环：

```
1. 预检 (Preflight Checks)
   ├── 进程互斥 (防止重复运行)
   ├── 队列限流 (避免饥饿用户会话)
   ├── 系统负载检测
   └── 循环门控 (等待上一次 solidification 完成)

2. 信号提取 (Signal Extraction)
   ├── extractSignals() — 从会话日志/MEMORY.md 提取
   ├── 分析历史抑制信号
   └── Hub 任务自动认领

3. Hub 交互 (Optional)
   ├── Hub 搜索 (复用已有方案)
   ├── 任务获取与认领
   └── Hub 事件处理

4. 记忆图谱更新
   ├── recordOutcomeFromState() — 推断上轮结果
   ├── recordSignalSnapshot() — 记录当前信号
   ├── recordHypothesis() — 记录假设
   └── recordAttempt() — 记录行动

5. 基因选择 (Gene Selection)
   ├── getMemoryAdvice() — 从记忆图谱获取建议
   ├── selectGeneAndCapsule() — 选择基因和胶囊
   └── computeAdaptiveStrategyPolicy() — 计算策略

6. 突变构建
   ├── buildMutation() — 生成突变
   └── allowHighRisk 判定

7. 桥接执行 (Bridge Mode)
   └── sessions_spawn → Hand Agent 执行
```

### 6.2 预检机制

**文件**: `src/evolve.js:350-420`

```javascript
async function runPreflightChecks(bridgeEnabled, loopMode) {
  // 1. 进程互斥
  const psRace = execSync('ps aux | grep "evolver_hand_" | grep -v grep');
  if (_psRace.length > 0) return { abort: true };
  
  // 2. 队列限流
  const activeUserSessions = getRecentActiveSessionCount(10 * 60 * 1000);
  if (activeUserSessions > QUEUE_MAX) {
    await sleepMs(QUEUE_BACKOFF_MS);
    return { abort: true };
  }
  
  // 3. 系统负载
  if (sysLoad.load1m > LOAD_MAX) {
    await sleepMs(QUEUE_BACKOFF_MS);
    return { abort: true };
  }
  
  // 4. 循环门控
  if (bridgeEnabled && loopMode) {
    const st = readStateForSolidify();
    if (lastRun exists && !lastSolidify) {
      // 上次运行还没完成 solidification，等待
      return { abort: true };
    }
  }
}
```

### 6.3 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 进程互斥 | 防止重复运行 | **高优先级**: BlueCortexCE 应防止并发写入冲突 |
| 队列限流 | 避免饥饿用户会话 | **高优先级**: BlueCortexCE 的 cron 任务应尊重用户活跃期 |
| 系统负载检测 | load1m vs 核心数*0.9 | **中优先级**: 资源密集型操作应检测负载 |
| 循环门控 | 等待上一次完成 | **高优先级**: BlueCortexCE 的观察写入应有幂等保证 |

---

## 7. 与 Hermes 的架构对比

### 7.1 记忆存储对比

| 维度 | Evolver | Hermes |
|------|---------|--------|
| 核心存储 | JSONL append-only | SQLite + FTS5 |
| curated memory | MEMORY.md 平面文件 | MEMORY.md 平面文件 |
| 记忆图谱 | signal→hypothesis→attempt→outcome | Holographic HRR vectors |
| 叙事记忆 | narrativeMemory.md | 无 |
| 信号系统 | signals.js 多语言提取 | honcho recall modes |

### 7.2 关键差异

1. **Evolver 有显式因果链**: signal → hypothesis → attempt → outcome，追踪"为什么这样做"
2. **Hermes 有向量检索**: HRR + cosine similarity，支持语义搜索
3. **Evolver 有叙事记忆**: 人类可读的决策历史
4. **Evolver 有 Gene 概念**: 结构化的突变模板库
5. **Hermes 有多 Provider**: honcho/hindsight/mem0 可插拔

### 7.3 Hermes 对 Evolver 的"借鉴"分析

根据 hermes-memory-analysis.md，Hermes 的 Holographic 机制与 Evolver 的 memoryGraph 有以下相似点：

| Evolver | Hermes (推测抄袭) |
|---------|------------------|
| signal → hypothesis → attempt → outcome | Observation → Memory → Holographic |
| computeSignalKey (归一化) | Entity extraction + normalization |
| getMemoryAdvice (基因推荐) | reason() (代数检索) |
| edgeExpectedSuccess (Laplace + 衰减) | Trust scoring (指数衰减) |

**⚠️ 注意**: Hermes 的实现可能已偏离 Evolver 的原始设计思想。本分析仅基于 evolver 代码库的实际内容。

---

## 8. BlueCortexCE 借鉴建议汇总

### 8.1 高优先级借鉴

| 借鉴点 | Evolver 做法 | BlueCortexCE 现状 | 具体建议 |
|--------|-------------|------------------|----------|
| 错误签名归一化 | errsig + stableHash 归一化 | 无 | 在 Observation 记录中实现类似的错误签名归一化 |
| 归因链追踪 | signal→hypothesis→attempt→outcome | observations 表只有单条记录 | 增加 `parent_id` 和 `cause_type` 字段支持归因 |
| 时间衰减权重 | 30天半衰期指数衰减 | 无时间衰减 | 在检索结果排序中加入时间衰减因子 |
| Laplace 平滑 | (succ+1)/(total+2) | 无 | 所有成功率统计使用 Laplace 平滑 |
| 叙事摘要格式 | Markdown + 策略链 + 结果 | 纯文本 summary | 改进 summary 格式，支持策略/结果结构化 |
| 多语言支持 | EN/ZH-CN/ZH-TW/JA 信号检测 | 无 | 实现多语言观察提取 |
| 历史抑制 | 3+ 次信号 suppress | 无 | 实现"已处理信号"的冷却机制 |

### 8.2 中优先级借鉴

| 借鉴点 | Evolver 做法 | 具体建议 |
|--------|-------------|----------|
| 低效路径抑制 | 连续失败+低成功率 → ban | 记录"无效检索模式"并降权 |
| 语义标签扩展 | expandSignals → problem/action/area | 实现观察的语义标签分类 |
| 固定上限裁剪 | 30 条 / 12000 chars | summaries 表实现自动裁剪 |
| 进程互斥 | 防止重复运行 | cron 任务使用文件锁 |

### 8.3 低优先级（架构差异大）

| Evolver 特性 | BlueCortexCE 评估 |
|-------------|-------------------|
| Gene/Capsule 进化系统 | 旁路型系统不需要突变模板 |
| Personality 自然选择 | 无需人格系统 |
| Hub 生态系统 | 暂无多 Agent 协作需求 |

---

## 待进一步确认

1. **memoryGraph 的查询性能**: JSONL 全量扫描在大型记忆图谱上的性能如何？
2. **gene/capsule 的具体语义**: 这两个概念在 evolver 中如何定义和分类？
3. **solidify.js 的验证机制**: 突变固化和验证的完整流程是什么？
4. **Hub 生态系统**: Hub 的任务池和知识共享机制细节？

---

## 下轮探索方向

1. **solidify.js** — 突变固化和验证流程
2. **selector.js** — 基因和胶囊选择算法
3. **curriculum.js** — 渐进式学习课程
4. **skillDistiller.js** — 技能提炼和迁移
