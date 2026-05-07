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


---

*Split continuation → [`01b-overview-evolve-hermes-bluecortexce-solidify-selector-curriculum.md`](./01b-overview-evolve-hermes-bluecortexce-solidify-selector-curriculum.md)*
