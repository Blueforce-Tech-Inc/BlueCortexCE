<!-- part 1/8: auto-split from evolver-memory-analysis.md — see index.md -->

# Evolver 记忆系统深度分析

> **文档状态**: v1.5 (新增：llmReview.js LLM审查集成 + assetStore.js 资产存储层（双格式+原子写入+OOM防护）)
> **分析目标**: 为 BlueCortexCE（旁路型记忆系统）提供可落地的借鉴建议
> **数据来源**: `/Users/yangjiefeng/Documents/EvoMap/evolver/`
> **最后更新**: 2026-04-17 08:07

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
29. [skillDistiller.js — 深度补充](#29-skilldistillerjs--深度补充v06-新增)
30. [reflection.js — 战略反思机制](#30-reflectionjs--战略反思机制v06-新增)
31. [candidates.js + candidateEval.js — 能力候选提取](#31-candidatesjs--candidateevaljs--能力候选提取v06-新增)
32. [Evolver 的 Genes/Capsules 资产体系](#32-evolver-的-genescapsules-资产体系v06-补充)
33. [文档版本历史与 TODO](#33-文档版本历史与-todo)
34. [signals.js + learningSignals.js — 信号处理链路](#34-signalsjs--learningsignalsjs--信号处理链路-v07-新增)
35. [mutation.js — 基因突变算法](#35-mutationjs--基因突变算法-v07-新增)
36. [evolve.js — 核心进化循环](#36-evolvejs--核心进化循环-v07-新增)
37. [prompt.js — GEP 提示词构建](#37-promptjs--gep-提示词构建-v08-新增)
38. [strategy.js — 进化策略预设](#38-strategyjs--进化策略预设-v08-新增)
39. [questionGenerator.js — 主动问题生成](#39-questiongeneratorjs--主动问题生成-v08-新增)
40. [idleScheduler.js — OMLS 空闲调度](#40-idleschedulerjs--omls-空闲调度-v08-新增)
41. [gitOps.js — Git 操作与回滚](#41-gitopsjs--git-操作与回滚-v08-新增)
42. [localStateAwareness.js — 本地状态感知](#42-localstateawarenessjs--本地状态感知-v08-新增)
43. [policyCheck.js — 约束检查与验证命令安全（深度补充）](#43-policycheckjs--约束检查与验证命令安全v09-深度补充)
44. [sanitize.js — 敏感信息脱敏](#44-sanitizejs--敏感信息脱敏v09-新增)
45. [contentHash.js — 内容寻址哈希](#45-contenthashjs--内容寻址哈希v09-新增)
46. [crypto.js — AES-256-GCM 加密](#46-cryptojs--aes-256-gcm-加密v09-新增)
47. [envFingerprint.js — 环境指纹](#47-envfingerprintjs--环境指纹v09-新增)
48. [issueReporter.js — 自动 GitHub 问题上报](#48-issuereporterjs--自动-github-问题上报v09-新增)
49. [validationReport.js — 标准化验证报告](#49-validationreportjs--标准化验证报告v09-新增)
50. [analyzer.js — 自省分析器](#50-analyzerjs--自省分析器v09-新增)
51. [整体架构补充：Evolver 的安全与隐私体系](#51-整体架构补充evolver-的安全与隐私体系v09-新增)
52. [hubSearch.js — 联邦知识市场与两阶段搜索（v1.0 新增）](#53-hubsearchjs--联邦知识市场与两阶段搜索v10-新增)
53. [hubReview.js — 使用验证型评价系统（v1.0 新增）](#54-hubreviewjs--使用验证型评价系统v10-新增)
54. [executionTrace.js — 隐私保护的执行遥测（v1.0 新增）](#55-executiontracejs--隐私保护的执行遥测v10-新增)
55. [assetCallLog.js — 资产交互的 append-only 审计（v1.0 新增）](#56-assetcalllogjs--资产交互的-append-only-审计v10-新增)
56. [directoryClient.js — 节点目录与能力发现（v1.0 新增）](#57-directoryclientjs--节点目录与能力发现v10-新增)
57. [deviceId.js — 稳定节点身份与优先级指纹链（v1.0 新增）](#58-deviceidjs--稳定节点身份与优先级指纹链v10-新增)
58. [a2aProtocol.js — Agent-to-Agent 联邦通信协议（v1.1 新增）](#60-a2aprotocoljs--agent-to-agent-联邦通信协议v11-新增)
59. [prompt.js — GEP 提示词构建器（v1.2 新增）](#61-promptjs--gep-提示词构建器v12-新增)
60. [strategy.js — 进化策略预设系统（v1.2 新增）](#62-strategyjs--进化策略预设系统v12-新增)
61. [memoryGraphAdapter.js — 本地/远程双模适配器（v1.2 新增）](#63-memorygraphadapterjs--本地远程双模适配器v12-新增)
62. [innovation.js — 停滞检测与创新催化剂（v1.2 新增）](#64-innovationjs--停滞检测与创新催化剂v12-新增)
63. [questionGenerator.js — 主动问题生成机制（v1.2 新增）](#65-questiongeneratorjs--主动问题生成机制v12-新增)
64. [idleScheduler.js — OMLS 空闲调度器（v1.2 新增）](#66-idleschedulerjs--omls-空闲调度器v12-新增)
67. [localStateAwareness.js — 本地状态感知（v1.2 新增）](#67-localstateawarenessjs--本地状态感知v12-新增)
68. [gitOps.js — Git 操作与原子回滚（v1.3 新增）](#68-gitopsjs--git-操作与原子回滚v13-新增)
69. [bridge.js — 跨 Agent 协作桥接（v1.3 新增）](#69-bridgejs--跨-agent-协作桥接v13-新增)
70. [a2a.js — A2A 资产广播与置信度管理（v1.3 新增）](#70-a2ajs--a2a-资产广播与置信度管理v13-新增)
71. [privacyClient.js — 隐私计算与密封执行（v1.3 新增）](#71-privacyclientjs--隐私计算与密封执行v13-新增)
72. [assets.js — 资产格式统一抽象（v1.3 新增）](#72-assetsjs--资产格式统一抽象v13-新增)
73. [candidates.js — 能力候选提取算法（v1.4 新增）](#73-candidatesjs--能力候选提取算法-v14-新增)
74. [candidateEval.js — 候选预演构建与外部资产匹配（v1.4 新增）](#74-candidateevaljs--候选预演构建与外部资产匹配-v14-新增)
75. [skillPublisher.js — Gene 到 SKILL.md 格式转换与 Hub 发布（v1.4 新增）](#75-skillpublisherjs--gene-到-skillmd-格式转换与-hub-发布-v14-新增)
76. [下轮探索方向（v1.4 更新）](#76-下轮探索方向v14-更新)
77. [llmReview.js — LLM 代码审查集成（v1.5 新增）](#77-llmreviewjs--llm-代码审查集成v15-新增)
78. [assetStore.js — 资产存储层（v1.5 新增）](#78-assetstorejs--资产存储层v15-新增)

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

