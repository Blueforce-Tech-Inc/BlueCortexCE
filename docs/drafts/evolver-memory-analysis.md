# Evolver 记忆系统深度分析

> **文档状态**: v0.5 (新增：skillPublisher.js + executionTrace.js + taskReceiver.js + hubReview.js)
> **分析目标**: 为 BlueCortexCE（旁路型记忆系统）提供可落地的借鉴建议
> **数据来源**: `/Users/yangjiefeng/Documents/EvoMap/evolver/`
> **最后更新**: 2026-04-16 23:17

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
9. [solidify.js — 突变固化与验证流程](#9-solidifyjs--突变固化与验证流程)
10. [selector.js — 基因和胶囊选择算法](#10-selectorjs--基因和胶囊选择算法)
11. [curriculum.js — 渐进式学习课程](#11-curriculumjs--渐进式学习课程)
12. [skillDistiller.js — 技能提炼与迁移](#12-skilldistillerjs--技能提炼与迁移)

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

## 9. solidify.js — 突变固化与验证流程（v0.2 新增）

**文件**: `src/gep/solidify.js` (1344 lines)

### 9.1 核心设计原则

solidify.js 是 Evolver 的**验证与固化层**，负责：
1. 对上轮突变进行多维度验证
2. 计算 PRM (Process Reward Model) 评分
3. 成功时创建 Capsule，失败时保留 FailedCapsule
4. 应用表观遗传标记（Epigenetic Marks）
5. 可选地自动发布到 Hub

### 9.2 Process Reward Model (PRM) — 多步评分

**文件**: `solidify.js:430-560` (`computeProcessScores`)

Evolver 不使用简单的成功/失败二元判断，而是评估**8个独立维度**：

```javascript
// Phase weights
const weights = {
  signal:           0.05,   // 信号质量
  selection:        0.10,  // 基因选择质量
  mutation:         0.05,  // 突变格式质量
  blast:            0.15,  // 变更范围控制
  constraint:       0.25,  // 约束合规性
  validation:       0.25,  // 验证通过率
  protocol:         0.10,  // 协议合规性
  canary:           0.05,  // Canary 健康检查
};
```

**各维度评分逻辑**：

| Phase | 评分规则 |
|-------|---------|
| **signal_quality** | 0.4 + signals.length × 0.1，上限 1.0 |
| **gene_selection** | gene_auto_ = 0.7，named gene = 0.9，无 = 0.3 |
| **mutation_quality** | 有 rationale+category = 0.8，低风险=0.9，高风险=0.6 |
| **blast_control** | 文件数 ≤ 50%maxFiles = 1.0，≤ 100% = 0.7，>100% = 0.2 |
| **constraint_compliance** | 1 - violationCount × 0.25，下限 0 |
| **validation_pass_rate** | 通过数/总数，无验证命令 = 0.5 penalty |
| **protocol_compliance** | 1 - protocolViolations.length × 0.3 |
| **canary_health** | canary 失败 = 0，否则 1.0 |

**Evolver 为什么这样做**：单一的成功/失败指标无法区分"好的一般"和"差的一般"。PRM 让进化系统能够理解**部分成功**——例如验证通过但 blast radius 超标的改进仍然有价值。

### 9.3 Canary Safety Check — 隔离进程验证

**文件**: `solidify.js:610-625`

```javascript
// 在 validation 之后、提交之前，运行 canary 检查
const canary = runCanaryCheck({ repoRoot, timeoutMs: 30000 });
if (!canary.ok && !canary.skipped) {
  constraintCheck.violations.push(
    `canary_failed: index.js cannot load in child process: ${canary.err}`
  );
  constraintCheck.ok = false;
}
```

**设计意图**：`gene.validation` 命令可能通过（只验证了特定模块），但 index.js 入口点可能已损坏。Canary 在**隔离子进程**中加载 index.js，确保整体可运行性。

**Evolver 为什么这样做**：基因验证是局部检查，canary 是全局冒烟测试。两者结合确保"验证通过"≠"系统可用"。

### 9.4 FailedCapsule — 失败信息的保全

**文件**: `solidify.js:840-880`

```javascript
// 在 rollback 之前，捕获失败突变的信息
if (!dryRun && !success) {
  const diffSnapshot = captureDiffSnapshot(repoRoot);
  if (diffSnapshot) {
    const failedCapsule = {
      type: 'Capsule',
      id: 'failed_' + buildCapsuleId(ts),
      outcome: { status: 'failed', score: score },
      gene: geneUsed && geneUsed.id ? geneUsed.id : null,
      trigger: signals.slice(0, 8),
      diff_snapshot: diffSnapshot,
      failure_reason: failureReason,
      learning_signals: softFailureLearningSignals,
      constraint_violations: constraintCheck.violations || [],
      env_fingerprint: envFp,
      blast_radius: { files: blast.files, lines: blast.lines },
    };
    appendFailedCapsule(failedCapsule);
  }
}
```

**关键点**：rollback 会清除工作区变更，但在 rollback **之前**先捕获 diff_snapshot。这样即使失败，失败的原因和上下文也被永久记录。

### 9.5 Epigenetic Marks — 环境感知的基因表达调控

**文件**: `solidify.js:245-340` (`applyEpigeneticMarks`, `buildEpigeneticMark`)

```javascript
// 环境上下文 = platform + arch + node_version
const envContext = [platform, arch, nodeVersion].join('/') || 'unknown';

// 成功的环境 → 正向标记
if (outcomeStatus === 'success') {
  if (existingIdx >= 0) {
    cur.boost = Math.min(0.5, cur.boost + 0.05);  // 强化
  } else {
    gene.epigenetic_marks.push(buildEpigeneticMark(envContext, 0.1, 'success_in_environment'));
  }
}

// 失败的环境 → 负向标记
if (outcomeStatus === 'failed') {
  if (existingIdx >= 0) {
    cur.boost = Math.max(-0.5, cur.boost - 0.1);  // 抑制
  } else {
    gene.epigenetic_marks.push(buildEpigeneticMark(envContext, -0.1, 'failure_in_environment'));
  }
}

// 衰减：90天前的标记过期，最多保留10个
const cutoff = Date.now() - 90 * 24 * 60 * 60 * 1000;
gene.epigenetic_marks = gene.epigenetic_marks
  .filter(m => new Date(m.created_at).getTime() > cutoff)
  .slice(-10);
```

**选择算法中的表观遗传 boost**：

```javascript
// selector.js:getEpigeneticBoostLocal()
function getEpigeneticBoostLocal(gene, envFingerprint) {
  const envContext = [platform, arch, nodeVersion].join('/') || 'unknown';
  const mark = gene.epigenetic_marks.find(m => m.context === envContext);
  return mark ? Number(mark.boost) || 0 : 0;
}
```

**Evolver 为什么这样做**：
1. **跨环境泛化**：Linux 上成功的基因在 macOS 上可能失败
2. **无损调整**：表观遗传标记不改变基因本身，只调整表达强度
3. **生物类比**：DNA 甲基化——相同基因在不同环境下有不同表达

### 9.6 Auto-publish to Hub — 成功的自动共享

**文件**: `solidify.js:950-1080`

```javascript
// 成功 + eligible_to_broadcast + 公开可见 + 最低分数 → 自动发布
if (!dryRun && capsule && capsule.a2a && capsule.a2a.eligible_to_broadcast) {
  const autoPublish = String(process.env.EVOLVER_AUTO_PUBLISH || 'true').toLowerCase() !== 'false';
  // ...
  if (autoPublish && visibility === 'public' && sourceType !== 'reused' && score >= minPublishScore) {
    // 发布 Gene + Capsule bundle 到 Hub
    const msg = buildPublishBundle({ gene, capsule, event, chainId, modelName });
    httpTransportSend(msg, { hubUrl });
  }
}
```

**eligible_to_broadcast 的判定**：
```javascript
capsule.a2a = {
  eligible_to_broadcast:
    isBlastRadiusSafe(capsule.blast_radius) &&
    (capsule.outcome.score || 0) >= BROADCAST_SCORE_THRESHOLD &&
    (capsule.success_streak || 0) >= BROADCAST_SUCCESS_STREAK,
};
```

### 9.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| PRM 多步评分 | 8维度独立评分，权重加权 | **高优先级**: BlueCortexCE 的 observation 可以记录多维度质量指标，不只是"记录成功/失败" |
| Canary 冒烟测试 | 隔离子进程加载 index.js | **高优先级**: BlueCortexCE 的 API 可以在写入后做基本的"冒烟测试"（如 health check） |
| FailedCapsule 保存 | rollback 前捕获 diff_snapshot | **高优先级**: BlueCortexCE 的失败记录应保存完整的上下文信息 |
| Epigenetic Marks | 环境感知的基因表达调控 | **中优先级**: BlueCortexCE 的检索可以记录"特定环境下检索效果好/差" |
| Auto-publish | 成功胶囊自动共享 Hub | **低优先级**: BlueCortexCE 无 Hub 生态 |

---

## 10. selector.js — 基因和胶囊选择算法（v0.2 新增）

**文件**: `src/gep/selector.js` (417 lines)

### 10.1 多层评分体系

Evolver 的基因选择使用**三层评分叠加**：

```javascript
function scoreGene(gene, signals) {
  const patterns = gene.signals_match || [];
  const tagScore = scoreTagOverlap(gene, signals);  // 语义标签重叠
  let score = 0;
  for (const pat of patterns) {
    if (matchPatternToSignals(pat, signals)) score += 1;  // 精确匹配
  }
  const semanticScore = scoreGeneSemantic(gene, signals) * SEMANTIC_WEIGHT;  // 语义相似度
  return score + (tagScore * 0.6) + semanticScore;
}
```

**评分构成**：
- 精确信号匹配：每个匹配 +1 分
- 语义标签重叠：scoreTagOverlap × 0.6
- Bag-of-words 语义相似度：cosine similarity × 0.4

### 10.2 Bag-of-Words 语义相似度

**文件**: `selector.js:25-70`

```javascript
// 停用词过滤
const STOP_WORDS = new Set([
  'the', 'and', 'for', 'with', 'from', 'that', 'this', 'into', 'when',
  'are', 'was', 'has', 'had', 'not', 'but', 'its', 'can', 'will', ...
]);

function tokenize(text) {
  return String(text || '').toLowerCase()
    .replace(/[^a-z0-9_\-]+/g, ' ')
    .split(/\s+/)
    .filter(w => w.length >= 2 && !STOP_WORDS.has(w));
}

function cosineSimilarity(tfA, tfB) {
  // TF-IDF style cosine similarity
  var dotProduct = 0, normA = 0, normB = 0;
  keys.forEach(function(k) {
    dotProduct += (tfA[k]||0) * (tfB[k]||0);
    normA += (tfA[k]||0) ** 2;
    normB += (tfB[k]||0) ** 2;
  });
  return normA && normB ? dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)) : 0;
}
```

**Evolver 为什么这样做**：在无外部 embedding provider 时，用 bag-of-words 实现轻量语义匹配。无需 LLM 或向量数据库。

### 10.3 多语言别名匹配

**文件**: `selector.js:100-115`

```javascript
// 多语言别名: "en_term|zh_term|ja_term" — 任意分支匹配即命中
if (p.includes('|') && !p.startsWith('/')) {
  const branches = p.split('|').map(b => b.trim().toLowerCase()).filter(Boolean);
  return branches.some(needle => sig.some(s => s.toLowerCase().includes(needle)));
}
```

**Evolver 为什么这样做**：Evolver 是国际化项目，用户使用多种语言。多语言别名让单一基因模式匹配多个语言的信号。

### 10.4 连续漂移强度

**文件**: `selector.js:450-490` (`computeDriftIntensity`)

```javascript
// 遗传漂移强度公式：1/sqrt(Ne)
// Ne = effective population size（活跃基因数量）
function computeDriftIntensity(opts) {
  const ne = effectivePopulationSize || genePoolSize || null;
  if (driftEnabled) {
    return ne && ne > 1 ? Math.min(1, 1 / Math.sqrt(ne) + 0.3) : 0.7;
  }
  if (ne != null && ne > 0) {
    // 小种群 = 强漂移，Ne=1 → intensity=1.0, Ne=25 → 0.2
    return Math.min(1, 1 / Math.sqrt(ne));
  }
  return 0;
}
```

**进化生物学背景**：在自然界，小种群更容易发生遗传漂移（随机因素主导），大种群由自然选择主导。Evolver 将这个原理形式化为连续函数。

### 10.5 多样性导向漂移

**文件**: `selector.js:510-560`

```javascript
if (driftIntensity > 0 && Math.random() < driftIntensity) {
  if (capabilityGaps.length > 0) {
    // 有 gap 数据：选择覆盖最多 gap 的基因
    const gapScores = filtered.map((entry, idx) => {
      const patterns = g.signals_match || [];
      let gapHits = 0;
      for (const gapSignal of capabilityGaps.slice(0, 5)) {
        if (patterns.some(p => matchPatternToSignals(p, [gapSignal]))) gapHits++;
      }
      return { idx, gapHits, baseScore: entry.score };
    });
    // 优先 gap 覆盖，次优先 base score
    gapScores.sort((a, b) => b.gapHits - a.gapHits || b.baseScore - a.baseScore);
    selectedIdx = gapScores[0].idx;
    driftMode = 'diversity_directed';
  }
}
```

**Evolver 为什么这样做**：普通的随机漂移（pure drift）探索效率低。多样性导向漂移优先探索**能力空白区**，提高探索效率。

### 10.6 Learning History Boost

**文件**: `selector.js:155-195`

```javascript
function scoreGeneLearning(gene, signals, envFingerprint) {
  let boost = 0;
  const history = gene.learning_history || [];
  
  // 近期历史（最后8条）
  for (const entry of history.slice(-8)) {
    if (entry.outcome === 'success') boost += 0.12;
    else if (entry.mode === 'hard') boost -= 0.22;
    else if (entry.mode === 'soft') boost -= 0.08;
  }
  
  // 表观遗传标记
  boost += getEpigeneticBoostLocal(gene, envFingerprint);
  
  // Anti-pattern 惩罚
  for (const anti of gene.anti_patterns.slice(-6)) {
    const overlap = anti.learning_signals.some(tag => signalTags.has(String(tag)));
    if (overlap) boost -= anti.mode === 'hard' ? 0.4 : 0.18;
  }
  
  return Math.max(-1.5, Math.min(1.5, boost));  // clamp
}
```

**Evolver 为什么这样做**：
- 近期成功 = 正向反馈信号
- 硬失败（hard）= 严重惩罚（不可恢复的错误）
- 软失败（soft）= 轻度惩罚（可能重试成功）
- Anti-pattern 重叠 = 避免重复已知失败路径

### 10.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 多层评分 | 精确+标签+语义三层叠加 | **高优先级**: BlueCortexCE 的检索可使用多维度评分（信号匹配+语义+时效性） |
| Bag-of-words 语义 | 无外部依赖的轻量语义匹配 | **中优先级**: 简单场景可不用 embedding provider |
| 多语言别名 | en\|zh\|ja 分支匹配 | **高优先级**: BlueCortexCE 的检索应支持多语言查询 |
| 连续漂移强度 | 1/sqrt(Ne) 公式 | **低优先级**: BlueCortexCE 无基因池概念 |
| Learning boost | 历史成功+失败+anti-pattern | **高优先级**: BlueCortexCE 的检索结果排序应考虑"历史使用效果" |
| 多样性导向 | 能力 gap 覆盖优先 | **中优先级**: BlueCortexCE 的推荐可以覆盖"低频但重要"的观察 |

---

## 11. curriculum.js — 渐进式学习课程（v0.2 新增）

**文件**: `src/gep/curriculum.js` (163 lines)

### 11.1 核心概念：Frontier 信号

Evolver 的 curriculum 系统基于信号空间的**三层划分**：

| 类别 | 定义 | 阈值 |
|------|------|------|
| **mastered** | 已掌握，成功率 ≥ 80%，至少3次尝试 | rate ≥ 0.8, total ≥ 3 |
| **failing** | 持续失败，成功率 ≤ 30%，至少2次尝试 | rate ≤ 0.3, total ≥ 2 |
| **frontier** | 中间地带，正在学习 | rate ∈ (0.3, 0.8) 或 total < 2 |

```javascript
function identifyFrontier(outcomes) {
  const mastered = [], failing = [], frontier = [];
  
  for (const [k, o] of Object.entries(outcomes)) {
    if (o.total < 2) continue;
    const rate = o.success / o.total;
    if (rate >= MASTERY_THRESHOLD && o.total >= MASTERY_MIN_ATTEMPTS) {
      mastered.push({ key: k, rate, total: o.total });
    } else if (rate <= FAILURE_THRESHOLD && o.total >= 2) {
      failing.push({ key: k, rate, total: o.total });
    } else {
      // 按与 0.5 的距离排序（越接近 0.5 越需要学习）
      frontier.push({ key: k, rate, total: o.total });
    }
  }
  
  frontier.sort((a, b) => Math.abs(a.rate - 0.5) - Math.abs(b.rate - 0.5));
  return { mastered, failing, frontier };
}
```

### 11.2 课程信号生成

**文件**: `curriculum.js:80-110`

```javascript
function generateCurriculumSignals(opts) {
  const { capabilityGaps, memoryGraphPath, personality } = opts;
  const signals = [];
  
  // 1. 优先处理 Hub capability gaps
  if (capabilityGaps.length > 0) {
    const gapTarget = capabilityGaps[0];
    const alreadyMastered = analysis.mastered.some(m => m.key.indexOf(gapTarget) >= 0);
    if (!alreadyMastered) {
      signals.push('curriculum_target:gap:' + gapTarget.slice(0, 60));
    }
  }
  
  // 2. 其次处理 frontier 信号（最需要学习的）
  if (signals.length < MAX_CURRICULUM_SIGNALS && analysis.frontier.length > 0) {
    const best = analysis.frontier[0];  // 最接近 0.5 的
    signals.push('curriculum_target:frontier:' + best.key.slice(0, 60));
  }
  
  return signals.slice(0, MAX_CURRICULUM_SIGNALS);  // 最多2个
}
```

### 11.3 结果聚合

**文件**: `curriculum.js:30-60`

```javascript
function aggregateOutcomes(memoryGraphPath) {
  const outcomes = {};
  const lines = fs.readFileSync(memoryGraphPath, 'utf8').trim().split('\n').slice(-200);
  
  for (const line of lines) {
    const ev = JSON.parse(line);
    if (ev.kind !== 'outcome') continue;
    const key = ev.signal_key || ev.key || '';
    if (!outcomes[key]) outcomes[key] = { success: 0, fail: 0, total: 0 };
    if (ev.outcome.status === 'success') outcomes[key].success++;
    else if (ev.outcome.status === 'failed') outcomes[key].fail++;
    outcomes[key].total++;
  }
  return outcomes;
}
```

**Evolver 为什么这样做**：从最近 200 个 outcome 事件聚合信号级别的成功率，这是 curriculum 学习的数据基础。

### 11.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 三层信号分类 | mastered/failing/frontier | **高优先级**: BlueCortexCE 可实现检索效果的类似分类 |
| 课程信号 | gap + frontier 优先 | **中优先级**: BlueCortexCE 可优先推荐"低曝光但潜在高价值"的观察 |
| 结果聚合 | signal → success/fail/total | **高优先级**: BlueCortexCE 应聚合检索历史的成功率 |
| 渐进式难度 | frontier 最需要学习 | **中优先级**: BlueCortexCE 的推荐可按"难度/覆盖率"排序 |

---

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

1. **skillPublisher.js** — 技能发布机制
2. **taskReceiver.js** — 主动任务认领 + 问题生成
3. **executionTrace.js** — 执行轨迹捕获机制
4. **hubReview.js** — Hub 审查机制
5. **idleScheduler.js** — 空闲调度机制

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

