# Evolver 记忆系统深度分析

> **文档状态**: v1.4 (新增：candidates.js能力候选提取 + candidateEval.js候选预演构建 + skillPublisher.js Gene到SKILL.md发布)
> **分析目标**: 为 BlueCortexCE（旁路型记忆系统）提供可落地的借鉴建议
> **数据来源**: `/Users/yangjiefeng/Documents/EvoMap/evolver/`
> **最后更新**: 2026-04-17 04:36

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

---

## 29. skillDistiller.js — 深度补充（v0.6 新增）

### 29.1 完整 Distillation Pipeline

skillDistiller 实际上有**两套并行的提炼流程**：

#### 成功路径提炼 (Success Distillation)

**Gate 条件**:
- `DISTILLER_MIN_CAPSULES = 10` (最近 10 个 capsule 中至少需要 ≥7 个成功)
- `DISTILLER_MIN_SUCCESS_RATE = 0.7`
- `DISTILLER_INTERVAL_HOURS = 24` (间隔至少 24 小时)
- 数据哈希变化 (idempotent skip)

**流程** (`skillDistiller.js:551-570`):

```javascript
function prepareDistillation() {
  // Step 1: collectDistillationData — 收集成功 capsule，分组统计
  const data = collectDistillationData();
  
  // Step 2: analyzePatterns — 发现高频、漂移、覆盖缺口
  const analysis = analyzePatterns(data);
  
  // Step 3: buildDistillationPrompt — 构建 LLM 提示词
  const prompt = buildDistillationPrompt(analysis, existingGenes, samples);
  
  // 写入 distill_request.json 和 prompt 文件
  fs.writeFileSync(reqPath, requestData);
  fs.writeFileSync(promptPath, prompt);
}
```

```javascript
function completeDistillation(responseText) {
  // Step 4: extractJsonFromLlmResponse — 从 LLM 响应解析 Gene JSON
  const rawGene = extractJsonFromLlmResponse(responseText);
  
  // Step 5: validateSynthesizedGene — 多重验证
  const validation = validateSynthesizedGene(rawGene, existingGenes);
  
  // 验证通过后写入 genes.json
  assetStore.upsertGene(gene);
  
  // 自动发布到 Hub
  if (process.env.SKILL_AUTO_PUBLISH !== '0') {
    skillPublisher.publishSkillToHub(gene);
  }
}
```

#### 失败路径提炼 (Failure Distillation)

**Gate 条件**:
- `FAILURE_DISTILLER_MIN_CAPSULES = 5` (至少 5 个失败 capsule)
- `FAILURE_DISTILLER_INTERVAL_HOURS = 12`

专门从**失败胶囊**中提取反模式，生成 `gene_repair_distilled_*` 前缀的修复型 Gene。

### 29.2 sanitizeSignalsMatch — 信号清洗

**文件**: `skillDistiller.js:357-390`

这是 skillDistiller 的**核心防御机制**——确保 LLM 生成的信号不会泄露工具名称、时间戳或会话 ID：

```javascript
function sanitizeSignalsMatch(signals) {
  return signals
    .map(s => String(s).trim().toLowerCase())
    .filter(s => s.length >= 3)                          // 太短则过滤
    .filter(s => !/^\d+$/.test(s))                       // 纯数字过滤
    .filter(s => !/^(cursor|vscode|vim|emacs|windsurf|copilot|cline|codex|bypass|distill)[_-]?\d*$/i.test(s))  // 工具名过滤
    .filter(s => !/\d{8,}/.test(s))                     // 长数字序列（会话 ID）过滤
    .map(s => s.replace(/[_-]\d{10,}$/g, ''))           // 去除尾部时间戳
    .map(s => s.replace(/^[_-]+|[_-]+$/g, ''))          // 去除首尾分隔符
    .filter(Boolean)
    .deduplicate();
}
```

**Evolver 为什么这样做**: LLM 生成 `signals_match` 时容易带上原始会话的上下文（工具名、时间戳），这些必须被清洗掉，否则同一个技能的多个 distillation 会产生不同的信号键，导致基因无法被正确匹配。

### 29.3 validateSynthesizedGene — 多重验证门

**文件**: `skillDistiller.js:392-430`

```javascript
function validateSynthesizedGene(gene, existingGenes) {
  const errors = [];
  
  // 1. 必须有 type=Gene
  if (gene.type !== 'Gene') errors.push('missing or wrong type');
  
  // 2. ID 必须以 gene_distilled_ 开头
  if (!gene.id?.startsWith(DISTILLED_ID_PREFIX)) 
    gene.id = DISTILLED_ID_PREFIX + gene.id;
  
  // 3. 工具名/纯数字 ID → deriveDescriptiveId 自动重命名
  if (needsRename) gene.id = deriveDescriptiveId(gene);
  
  // 4. signals_match 清洗后不能为空
  gene.signals_match = sanitizeSignalsMatch(gene.signals_match);
  if (gene.signals_match.length === 0) 
    errors.push('signals_match empty after sanitization');
  
  // 5. strategy 至少 3 步
  if (gene.strategy?.length < 3) 
    errors.push('strategy must have at least 3 steps');
  
  // 6. constraints.forbidden_paths 必须包含 .git 或 node_modules
  if (!gene.constraints?.forbidden_paths?.some(p => p === '.git' || p === 'node_modules'))
    errors.push('must forbid .git or node_modules');
  
  // 7. max_files ≤ 12
  if (gene.constraints?.max_files > 12) 
    gene.constraints.max_files = 12;
  
  // 8. validation 命令必须通过 policyCheck.isValidationCommandAllowed
  gene.validation = gene.validation.filter(cmd => isValidationCommandAllowed(cmd));
  
  // 9. signals_match 不能与已有基因完全重复
  if (overlapsWithExisting(gene.signals_match, existingGenes))
    errors.push('signals_match fully overlaps with existing gene');
  
  // 10. ID 不能与已有基因冲突
  if (existingIds.has(gene.id))
    gene.id = gene.id + '_' + Date.now().toString(36);
}
```

### 29.4 deriveDescriptiveId — 无意义 ID 的自动修复

**文件**: `skillDistiller.js:321-355`

当 LLM 生成的 ID 包含工具名/时间戳时，使用**描述性 fallback**：

```javascript
function deriveDescriptiveId(gene) {
  // 优先从 signals_match 提取关键词
  const words = gene.signals_match?.slice(0, 3)
    .flatMap(s => s.toLowerCase().replace(/[^a-z0-9]+/g, ' ').split(' '))
    .filter(w => w.length >= 3)
    .slice(0, 6) || [];
  
  // 次选从 summary 提取
  if (words.length < 3 && gene.summary) {
    const STOP = new Set(['the','and','for','with','from','that','this','into','when','are','was','has','had']);
    words.push(...gene.summary.split(' ').filter(w => w.length >= 3 && !STOP.has(w)).slice(0, 6));
  }
  
  // 兜底：从 strategy 第一步提取
  if (words.length < 2) words.push('auto', 'distilled', 'strategy');
  
  return DISTILLED_ID_PREFIX + unique(words).slice(0, 5).join('-');
}
```

### 29.5 buildDistillationPrompt — 完整的 LLM 提示词模板

**文件**: `skillDistiller.js:225-300`

```javascript
// 核心指令片段
'- Output ONLY a single valid JSON object (no markdown fences, no explanation).'
'- The id MUST start with "gene_distilled_" followed by a descriptive kebab-case name.'
'- Good: "gene_distilled_retry-with-exponential-backoff"'
'- Bad: "gene_distilled_cursor-1773331925711", "gene_distilled_1234567890"'
'- Summary must be 30-200 chars, marketplace-quality description.'
'- signals_match: 3-7 generic reusable keywords, lowercase_snake_case.'
'- NEVER include timestamps, build numbers, tool names (cursor, vscode, etc.)'
'- Strategy: 5-10 actionable imperative steps with inline code examples.'
'- Validation: commands must start with "node ", "npm ", or "npx "'
'- constraints.max_files MUST be <= 12'
'- constraints.forbidden_paths MUST include at least [".git", "node_modules"]'
'- Imagine this Gene will be published on a marketplace for thousands of AI agents.'
```

**Evolver 为什么这样做**: 提示词层面的强约束比验证规则更高效——从源头阻止无效信号比事后过滤更可靠。

### 29.6 distillation state.json — 幂等状态机

**文件**: `skillDistiller.js:51-70`

```javascript
// distiller_state.json 内容
{
  "last_distillation_at": "2026-04-16T12:00:00Z",
  "last_data_hash": "a1b2c3d4e5f6",
  "last_gene_id": "gene_distilled_retry-with-exponential-backoff",
  "distillation_count": 3
}

// 两个幂等保证:
// 1. 时间间隔: elapsed < DISTILLER_INTERVAL_HOURS → skip
// 2. 数据不变: last_data_hash === current_data_hash → skip
```

### 29.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 信号清洗 (sanitizeSignalsMatch) | LLM 生成后 strip 工具名/时间戳 | **高优先级**: BlueCortexCE 的 Summary 生成后应清洗无效信号 |
| 多重验证门 | 10 重检查覆盖类型/ID/信号/策略/约束/验证命令 | **高优先级**: BlueCortexCE 的任何 LLM 生成内容都应有多重验证 |
| deriveDescriptiveId fallback | 无意义 ID 自动从 signals_match 重建 | **高优先级**: BlueCortexCE 的 extraction 结果如果信号太具体，应自动抽象化 |
| 幂等状态机 | state.json + data_hash 防重复提炼 | **高优先级**: BlueCortexCE 的任何周期性任务应有 idempotent skip |
| 失败路径提炼 | 从 failed_capsules 提取反模式 | **中优先级**: BlueCortexCE 可记录"检索无效"的模式，避免重复 |
| 自动 Hub 发布 | SKILL_AUTO_PUBLISH → skillPublisher.publishSkillToHub | **低优先级**: BlueCortexCE 的 extraction 结果可发布到共享市场 |

---

## 30. reflection.js — 战略反思机制（v0.6 新增）

**文件**: `src/gep/reflection.js` (145 lines)

### 30.1 设计定位

reflection.js 是 Evolver 的**元认知层**——在多个进化周期后，停下来反思：
- 当前策略是否最优？
- 是否有被忽略的信号？
- 是否陷入了局部最优？

### 30.2 shouldReflect — 自适应反思周期

**文件**: `reflection.js:35-50`

```javascript
function computeReflectionInterval(recentEvents) {
  if (recentEvents.length < 3) return REFLECTION_INTERVAL_DEFAULT; // 5
  
  const tail = recentEvents.slice(-3);
  const allSuccess = tail.every(e => e.outcome?.status === 'success');
  const allFailed = tail.every(e => e.outcome?.status === 'failed');
  
  if (allSuccess) return REFLECTION_INTERVAL_SUCCESS;    // 8 cycles
  if (allFailed)  return REFLECTION_INTERVAL_FAILURE;    // 3 cycles
  return REFLECTION_INTERVAL_DEFAULT;                     // 5 cycles
}
```

**Evolver 为什么这样做**: 
- 连续成功时延长反思间隔（8 cycles），因为系统运转良好
- 连续失败时缩短反思间隔（3 cycles），尽快发现问题
- 反思冷却时间 30 分钟，防止在短时间内重复反思

### 30.3 buildSuggestedMutations — 反思驱动的参数调整

**文件**: `reflection.js:55-80`

```javascript
function buildSuggestedMutations(signals) {
  const muts = [];
  
  // 停滞 → 提高创造力
  if (has('stable_success_plateau', 'evolution_stagnation_detected', 'empty_cycle_loop_detected'))
    muts.push({ param: 'creativity', delta: +0.05 });
  
  // 错误 → 提高严谨度
  if (has('log_error', 'errsig:', 'errsig_norm:'))
    muts.push({ param: 'rigor', delta: +0.05 });
  
  // 能力缺口 → 提高风险容忍
  if (has('capability_gap', 'external_opportunity'))
    muts.push({ param: 'risk_tolerance', delta: +0.05 });
  
  return muts.slice(0, 2);  // 每次最多 2 个建议
}
```

**Evolver 为什么这样做**: 反思阶段不是生成新的 Gene，而是建议**调整人格参数**。这是最小干预原则——如果当前策略本身没问题，只是执行时的冒险程度需要调整。

### 30.4 buildReflectionContext — 反思上下文构建

**文件**: `reflection.js:82-120`

```javascript
function buildReflectionContext({ recentEvents, signals, memoryAdvice, narrative }) {
  // 输出结构化报告:
  // ## Recent Cycle Statistics (last 10)
  // - Success: N, Failed: N
  // - Intent distribution: {...}
  // - Gene usage: {...}
  
  // ## Current Signals
  // [signals...]
  
  // ## Memory Graph Advice
  // - Preferred gene: ...
  // - Banned genes: ...
  
  // ## Recent Evolution Narrative
  // [narrative snippet]
  
  // ## Questions to Answer
  // 1. Are there persistent signals being ignored?
  // 2. Is the gene selection strategy optimal?
  // 3. Should the balance between repair/optimize/innovate shift?
  // 4. Are there capability gaps that no current gene addresses?
  // 5. What single strategic adjustment would have the highest impact?
  
  return prompt;
}
```

**Evolver 为什么这样做**: 反思不是空想，而是基于数据：最近的统计（成功率、基因使用频率）、当前信号、历史叙事记忆。

### 30.5 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 自适应反思周期 | 连续成功→长间隔，连续失败→短间隔 | **高优先级**: BlueCortexCE 的 Summary 触发可参考"检索成功率"动态调整 |
| 反思冷却 (30min) | 防止短时间重复反思 | **高优先级**: BlueCortexCE 的任何 LLM 调用应有冷却机制 |
| 参数微调建议 | 反思 → 建议人格参数 delta | **低优先级**: BlueCortexCE 是旁路型，无人格参数 |
| 5 个战略问题 | 引导 LLM 聚焦关键决策 | **中优先级**: BlueCortexCE 的 periodic review 可参考这些问题模板 |
| 叙事记忆注入 | 反思时加载 narrative 摘要 | **高优先级**: BlueCortexCE 的检索结果可附带"历史使用情况" |

---

## 31. candidates.js + candidateEval.js — 能力候选提取（v0.6 新增）

**文件**: `src/gep/candidates.js` (210 lines) + `src/gep/candidateEval.js` (80 lines)

### 31.1 设计定位

candidates.js 从**当前会话**中提取"能力缺口候选"，在 solidify 之前预填充 Gene 候选池：

```
Session Transcript
    ↓
extractCapabilityCandidates (candidates.js)
    ↓
[Cap1: 重复工具调用, Cap2: 失败路径模式, Cap3: 信号缺口]
    ↓
appendCandidateJsonl (持久化到 candidates.jsonl)
    ↓
solidify 时从 candidates.jsonl 加载，作为 Gene 选择参考
```

### 31.2 候选来源 (extractCapabilityCandidates)

**文件**: `candidates.js:60-200`

**来源 1: 重复工具调用** (工具使用 ≥3 次)
```javascript
for (const [tool, count] of freq.entries()) {
  if (count < 3) continue;
  // 从 transcript 中提取的重复工具 → CapabilityCandidate
  candidates.push({
    type: 'CapabilityCandidate',
    title: `Repeated tool usage: ${tool}`,
    source: 'transcript',
    tags: expandSignals(signals, transcript),  // 语义扩展
  });
}
```

**来源 2: 信号缺口** (当前信号列表中有特定信号)
```javascript
const signalCandidates = [
  { signal: 'log_error', title: 'Repair recurring runtime errors' },
  { signal: 'protocol_drift', title: 'Prevent protocol drift' },
  { signal: 'user_feature_request', title: 'Implement user-requested feature' },
  { signal: 'capability_gap', title: 'Fill capability gap' },
  { signal: 'stable_success_plateau', title: 'Explore new strategies during stability plateau' },
  // ...
];
```

**来源 3: 失败胶囊反模式** (失败 ≥2 次，按问题类型分组)
```javascript
// 按 problem:xxx 标签分组
const groups = {};
failedCapsules.forEach(fc => {
  const failureTags = expandSignals(triggers, reason)
    .filter(t => t.startsWith('problem:') || t.startsWith('risk:') || t.startsWith('area:'));
  // 同一 dominantProblem 的失败聚合成一条候选
  groups[key] = { count, tags, reasons, gene };
});

// count >= 2 时才生成候选（避免噪声）
if (group.count >= 2) {
  candidates.push({
    type: 'CapabilityCandidate',
    title: getTitleFromProblemType(dominantProblem),
    source: 'failed_capsules',
  });
}
```

### 31.3 CapabilityCandidate 的 Shape 结构

**文件**: `candidates.js:35-50`

```javascript
function buildFiveQuestionsShape({ title, signals, evidence }) {
  return {
    title: String(title).slice(0, 120),
    input: 'Recent session transcript + memory snippets + user instructions',
    output: 'A safe, auditable evolution patch guided by GEP assets',
    invariants: 'Protocol order, small reversible patches, validation, append-only events',
    params: `Signals: ${signals.join(', ')}`,
    failure_points: 'Missing signals, over-broad changes, skipped validation, missing knowledge solidification',
    evidence: clip(evidence, 240),
  };
}
```

**Evolver 为什么这样做**: 用 Five Questions 模板结构化候选表达，确保每个候选都有清晰的输入/输出/失败点描述。

### 31.4 buildCandidatePreviews — 候选预览构建

**文件**: `candidateEval.js:15-80`

**内部候选**:
```javascript
const newCandidates = extractCapabilityCandidates({ transcript, signals, failedCapsules });
const recentCandidates = readRecentCandidates(20);
const capabilityCandidatesPreview = renderCandidatesPreview(recentCandidates.slice(-8), 1600);
```

**外部候选** (从 Hub 获取的基因/胶囊):
```javascript
const external = readRecentExternalCandidates(50);
const capsulesOnly = external.filter(x => x.type === 'Capsule');
const genesOnly = external.filter(x => x.type === 'Gene');

// 按 signals_match 与当前信号的匹配度排序
const matchedExternalGenes = genesOnly
  .map(g => ({
    gene: g,
    hit: g.signals_match.reduce((acc, p) => matchPatternToSignals(p, signals) ? acc + 1 : acc, 0)
  }))
  .filter(x => x.hit > 0)
  .sort((a, b) => b.hit - a.hit)
  .slice(0, 3)
  .map(x => x.gene);
```

**Evolver 为什么这样做**: 外部候选来自 Hub，按信号匹配度过滤，只推荐与当前信号相关的外部资产。

### 31.5 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 重复工具调用候选 | transcript 中工具 ≥3 次 → CapabilityCandidate | **高优先级**: BlueCortexCE 可从 Session 中检测"重复模式"作为 Summary 候选 |
| 失败胶囊反模式 | 失败 ≥2 次 + 同问题类型 → 候选 | **高优先级**: BlueCortexCE 应有"失败经验"记录（Observation +1 标记） |
| 外部候选匹配 | Hub 资产按 signals_match 匹配度过滤 | **中优先级**: BlueCortexCE 的 Search 结果可标注"匹配度评分" |
| Five Questions Shape | 标准化输入/输出/失败点 | **中优先级**: BlueCortexCE 的 Structured Extraction 可参考此格式 |
| 候选池持久化 | candidates.jsonl append-only | **低优先级**: BlueCortexCE 当前用 Summary 作为"候选" |

---

## 32. Evolver 的 Genes/Capsules 资产体系（v0.6 补充）

### 32.1 Gene Schema 完整字段

**文件**: `src/gep/assetStore.js:80-150`

```javascript
// Gene 的完整结构
{
  "id": "gene_distilled_retry-with-exponential-backoff",  // 必须前缀
  "type": "Gene",
  "category": "repair|optimize|innovate",
  "summary": "Retry failed HTTP requests with exponential backoff...",
  
  "signals_match": [                    // 触发信号（归一化后）
    "http_retry",
    "request_timeout",
    "circuit_breaker",
    "resilience"
  ],
  
  "preconditions": [                    // 前置条件
    "Project uses Node.js >= 18",
    "HTTP client library available"
  ],
  
  "strategy": [                         // 策略步骤
    "Step 1: ...",
    "Step 2: ..."
  ],
  
  "constraints": {
    "max_files": 12,
    "forbidden_paths": [".git", "node_modules"]
  },
  
  "validation": [                       // 验证命令
    "npm test",
    "npx tsc --noEmit"
  ],
  
  "_distilled_meta": {                  // 仅 distillation 生成时有
    "distilled_at": "2026-04-16T12:00:00Z",
    "source_capsule_count": 10,
    "data_hash": "a1b2c3"
  },
  
  "epigenetic_marks": [                 // 环境标记（Evolver 特有）
    { "env": "darwin-arm64", "boost": 0.15 },
    { "env": "linux-x64", "boost": -0.05 }
  ]
}
```

### 32.2 Capsule Schema 完整字段

```javascript
// Capsule = 一次进化尝试的完整记录
{
  "id": "cap_xxx",
  "type": "Capsule",
  "gene": "gene_distilled_retry-with-exponential-backoff",
  "trigger": ["http_retry", "request_timeout"],
  
  "outcome": {
    "status": "success|failed",
    "score": 0.85,
    "blast_radius": { "files": 2, "lines": 80 },
    "duration_ms": 45000
  },
  
  "summary": "Added retry logic to HTTP client module...",
  
  // 失败特有字段
  "failure_reason": "...",
  "failure_tags": ["problem:reliability", "risk:regression"],
  
  // 固化和发布标记
  "solidified": true,
  "source_type": "new|reused|reference",
  "published": false
}
```

### 32.3 BlueCortexCE 对照

| Evolver 资产 | BlueCortexCE 等价 | 差距 |
|-------------|------------------|------|
| Gene.signals_match | ObservationEntity.tags | 差距：BC 的 tags 是原始信号，无归一化 |
| Gene.strategy | SummaryEntity.content | 差距：BC 的 content 是自然语言，非结构化步骤 |
| Gene.constraints | 无 | **缺失**: BlueCortexCE 没有 constraints 概念 |
| Gene.validation | 无 | **缺失**: BlueCortexCE 没有验证命令概念 |
| Capsule.blast_radius | 无 | **缺失**: BlueCortexCE 没有"影响范围"记录 |
| Capsule.failure_reason | ObservationEntity 内容 | BC 将失败记录为普通 Observation |
| epigenetic_marks | 无 | **缺失**: BlueCortexCE 无环境感知 |

### 32.4 最关键的差距分析

**Gap 1: signals_match 归一化**
Evolver 的 `signals_match` 是经过 `sanitizeSignalsMatch` 清洗的归一化信号（无工具名/时间戳）。BlueCortexCE 的 observation.tags 直接来自信号提取，没有经过归一化清洗。

**Gap 2: 策略的结构化表达**
Evolver 的 Gene.strategy 是明确的步骤列表。BlueCortexCE 的 Summary.content 是自然语言，AI 可读但无法直接用于自动化执行。

**Gap 3: constraints + validation**
Evolver 的 Gene 有 constraints（max_files, forbidden_paths）和 validation（npm test）用于安全执行。BlueCortexCE 的任何"自动执行"都缺乏这类安全约束。

---

## 33. 文档版本历史与 TODO

### 33.1 版本记录

| 版本 | 日期 | 新增内容 |
|------|------|----------|
| v0.1 | 2026-04-16 | 初始框架 |
| v0.2 | 2026-04-16 | skillDistiller.js 初步分析 |
| v0.3 | 2026-04-16 | solidify.js, selector.js, curriculum.js |
| v0.4 | 2026-04-16 | memoryGraph 深度分析 + 整体架构总结 |
| v0.5 | 2026-04-16 | skillPublisher, executionTrace, taskReceiver, hubReview |
| v0.6 | 2026-04-16 20:24 | skillDistiller 深度补充 + reflection.js + candidates.js + Gene/Capsule 资产体系 |
| v0.7 | 2026-04-16 21:25 | signals.js + learningSignals.js + mutation.js + evolve.js 核心循环 |
| v0.8 | 2026-04-16 22:54 | prompt.js + strategy.js + questionGenerator.js + idleScheduler.js + gitOps.js + localStateAwareness.js |
| v0.9 | 2026-04-17 01:34 | policyCheck.js 深度补充 + sanitize.js + contentHash.js + crypto.js + envFingerprint.js + issueReporter.js + validationReport.js + analyzer.js + 安全隐私体系总结 |
| v1.0 | 2026-04-17 02:22 | hubSearch.js + hubReview.js + executionTrace.js + assetCallLog.js + directoryClient.js + deviceId.js |
| v1.1 | 2026-04-17 03:35 | a2aProtocol.js 深度分析（联邦通信协议、HMAC 签名、双传输层、心跳机制、SSE 事件流） |
| v1.2 | 2026-04-17 04:36 | prompt.js + strategy.js + memoryGraphAdapter.js + innovation.js + questionGenerator.js + idleScheduler.js + localStateAwareness.js 深度补充 |
| v1.3 | 2026-04-17 05:41 | gitOps.js + bridge.js + a2a.js + privacyClient.js + assets.js 深度补充 |

---

## 34. signals.js + learningSignals.js — 信号处理链路 (v0.7 新增)

### 34.1 整体信号处理架构

Evolver 的信号系统分为两层：

```
原始信号来源 → signals.js (提取+去重) → expandedTags → gene selection
                           ↓
              learningSignals.js (信号扩展+标签评分)
```

**关键认知**：BlueCortexCE 的 Observation.tags 是"原始信号"，Evolver 的 signals 是经过多步处理的"精炼信号"。

### 34.2 signals.js — 信号提取与去重

**文件**: `src/gep/signals.js` (446 lines)

#### 34.2.1 信号来源（4 个语料库）

```javascript
// evolve.js:1268
var corpus = [
  String(recentSessionTranscript || ''),
  String(todayLog || ''),
  String(memorySnippet || ''),
  String(userSnippet || ''),
].join('\n');
```

| 来源 | 说明 | BlueCortexCE 等价 |
|------|------|------------------|
| recentSessionTranscript | Agent 执行日志 | SessionEntity + UserPromptEntity |
| todayLog | 当日记忆摘要 | 当日 Observation 汇总 |
| memorySnippet | narrativeMemory 摘要 | SummaryEntity |
| userSnippet | 用户显式输入 | 最新 UserPromptEntity |

#### 34.2.2 防御性信号（Defensive Signals）

```javascript
// signals.js:144
var errorHit = /\[error\]|error:|exception:|iserror":true|"status":\s*"error"|.../.test(lower);
if (errorHit) signals.push('log_error');
```

**Evolver 做法**：多语言正则匹配 + 结构化 JSON 错误检测，支持 EN/ZH/JA。
**BlueCortexCE 现状**：依赖 LLM 的自然语言理解，没有结构化错误模式检测。

#### 34.2.3 重复错误检测

```javascript
// signals.js:183
var recurringErrors = Object.entries(errorCounts).filter(function (e) { return e[1] >= 3; });
if (recurringErrors.length > 0) {
  signals.push('recurring_error');
  signals.push('recurring_errsig(' + topErr[1] + 'x):' + topErr[0].slice(0, 150));
}
```

**Evolver 做法**：统计 3 次以上的重复错误，生成可读的 errsig 标签。
**借鉴点**：BlueCortexCE 可以在 Observation 层面增加"重复计数"字段，当同一模式出现 3+ 次时触发升级信号。

#### 34.2.4 历史信号压制（去重机制）

```javascript
// signals.js:32
function analyzeRecentHistory(recentEvents) {
  // 抑制最近 8 个事件中出现 3+ 次的信号
  var suppressedSignals = new Set();
  // ...
}
```

**Evolver 做法**：如果某个信号在过去 8 个事件中出现 ≥3 次，则压制它避免重复处理。
**BlueCortexCE 现状**：无去重机制，所有 Observation 平等对待。

#### 34.2.5 连续失败/空循环检测

```javascript
// signals.js:106
consecutiveRepairCount: consecutiveRepairCount,  // 连续 repair 次数
consecutiveEmptyCycles: consecutiveEmptyCycles,  // 连续空循环次数
consecutiveFailureCount: consecutiveFailureCount,  // 连续失败次数
recentFailureRatio: recentFailureCount / tail.length,  // 失败率
```

**Evolver 做法**：检测连续失败/空循环，用于判断是否需要降级（repair loop circuit breaker）。
**BlueCortexCE 现状**：无此机制。

### 34.3 learningSignals.js — 信号扩展与标签评分

**文件**: `src/gep/learningSignals.js` (89 lines)

#### 34.3.1 expandSignals — 信号扩展

```javascript
// learningSignals.js:16
function expandSignals(signals, extraText) {
  const raw = Array.isArray(signals) ? signals.map(function (s) { return String(s); }) : [];
  const tags = [];

  // 1. 基础扩展：添加带参数前缀的原始信号
  for (let i = 0; i < raw.length; i++) {
    const signal = raw[i];
    add(tags, signal);
    const base = signal.split(':')[0];
    if (base && base !== signal) add(tags, base);
  }

  // 2. 问题-行动映射
  const text = (raw.join(' ') + ' ' + String(extraText || '')).toLowerCase();

  if (/(error|exception|failed|unstable|log_error|runtime|429)/.test(text)) {
    add(tags, 'problem:reliability');
    add(tags, 'action:repair');
  }
  if (/(protocol|prompt|audit|gep|schema|drift)/.test(text)) {
    add(tags, 'problem:protocol');
    add(tags, 'action:optimize');
    add(tags, 'area:prompt');
  }
  if (/(perf|performance|bottleneck|latency|slow|throughput)/.test(text)) {
    add(tags, 'problem:performance');
    add(tags, 'action:optimize');
  }
  // ...
}
```

**Evolver 做法**：将原始信号映射到 (problem, action, area) 三元组标签。
**借鉴点**：BlueCortexCE 的 Observation.tags 可以经过类似的语义扩展，增加 (domain, action, severity) 标签。

#### 34.3.2 scoreTagOverlap — Gene 匹配评分

```javascript
// learningSignals.js:67
function scoreTagOverlap(gene, signals) {
  const signalTags = expandSignals(signals, '');
  const geneTagList = geneTags(gene);
  if (signalTags.length === 0 || geneTagList.length === 0) return 0;
  const signalSet = new Set(signalTags);
  let hits = 0;
  for (let i = 0; i < geneTagList.length; i++) {
    if (signalSet.has(geneTagList[i])) hits++;
  }
  return hits / geneTagList.length;  // Jaccard-like 相似度
}
```

**Evolver 做法**：使用 Jaccard 相似度计算 Gene 与当前信号的匹配度。
**借鉴点**：BlueCortexCE 可以用类似算法做"Summary 推荐"——给定当前 session signals，推荐最相关的历史 Summary。

### 34.4 BlueCortexCE 借鉴建议

| Evolver 机制 | BlueCortexCE 现状 | 翻译：旁路型如何借鉴 | 优先级 |
|-------------|------------------|---------------------|--------|
| 信号去重（压制 3+ 次重复） | 无 | Observation 增加 repeatCount，出现 3+ 次时标记 elevated | 高 |
| 连续失败/空循环检测 | 无 | Summary 增加 failureStreak 字段 | 高 |
| expandSignals 语义扩展 | 无 | Tags 增加 (domain, action) 扩展层 | 中 |
| scoreTagOverlap 推荐 | 无 | SearchService 增加 gene-like 推荐算法 | 中 |
| recurring_error 聚合 | 无 | 错误模式聚类（相似 errors 归为同一 pattern） | 中 |

---

## 35. mutation.js — 基因突变算法 (v0.7 新增)

**文件**: `src/gep/mutation.js` (186 lines)

### 35.1 突变类别决策

```javascript
// mutation.js:44
function mutationCategoryFromContext({ signals, driftEnabled }) {
  if (hasErrorishSignal(signals)) return 'repair';
  if (driftEnabled) return 'innovate';
  if (hasOpportunitySignal(signals)) return 'innovate';
  // Check strategy preset for innovation preference
  try {
    var strategy = require('./strategy').resolveStrategy();
    if (strategy && typeof strategy.innovate === 'number' && strategy.innovate >= 0.5) return 'innovate';
  } catch (_) {}
  return 'optimize';
}
```

**决策树**：

```
Error signal present? ──YES──→ repair
         │
         NO
         ↓
driftEnabled (random)? ──YES──→ innovate
         │
         NO
         ↓
Opportunity signal? ──YES──→ innovate
         │
         NO
         ↓
Strategy.innovate >= 0.5? ──YES──→ innovate
         │
         NO
         ↓
      optimize
```

### 35.2 OPPORTUNITY_SIGNALS 清单

```javascript
// mutation.js:23
var OPPORTUNITY_SIGNALS = [
  'user_feature_request',      // 用户功能请求
  'user_improvement_suggestion', // 用户改进建议
  'perf_bottleneck',           // 性能瓶颈
  'capability_gap',            // 能力差距
  'stable_success_plateau',    // 稳定成功 plateau
  'external_opportunity',       // 外部机会
  'issue_already_resolved',    // 已解决的 issue
  'openclaw_self_healed',      // 自愈
  'empty_cycle_loop_detected', // 空循环检测
];
```

### 35.3 安全约束（硬性规则）

```javascript
// mutation.js:126
function buildMutation({ ..., personalityState, allowHighRisk = false }) {
  // Rule 1: innovate + high-risk personality → downgrade to optimize
  const highRiskPersonality = isHighRiskPersonality(personalityState || null);
  if (base.category === 'innovate' && highRiskPersonality) {
    base.category = 'optimize';
    base.risk_level = 'low';
  }

  // Rule 2: high-risk mutation + personality disallows → cap to medium
  if (base.risk_level === 'high' && !isHighRiskMutationAllowed(personalityState || null)) {
    base.risk_level = 'medium';
  }
}
```

**高风险人格判断**：

```javascript
// mutation.js:70
function isHighRiskPersonality(p) {
  const rigor = p && Number.isFinite(Number(p.rigor)) ? Number(p.rigor) : null;
  const riskTol = p && Number.isFinite(Number(p.risk_tolerance)) ? Number(p.risk_tolerance) : null;
  if (rigor != null && rigor < 0.5) return true;       // rigor < 0.5 → high-risk
  if (riskTol != null && riskTol > 0.6) return true;  // risk_tolerance > 0.6 → high-risk
  return false;
}

function isHighRiskMutationAllowed(personalityState) {
  const rigor = personalityState?.rigor ?? 0;
  const riskTol = personalityState?.risk_tolerance ?? 1;
  return rigor >= 0.6 && riskTol <= 0.5;  // 只有 rigor 高 + risk 低才允许高风险突变
}
```

### 35.4 Mutation 对象结构

```javascript
// mutation.js:36
const base = {
  type: 'Mutation',
  id: `mut_${ts}`,                    // 唯一 ID
  category: mutationCategory,          // repair | optimize | innovate
  trigger_signals: triggerSignals,     // 触发此突变的信号列表
  target: String(target || targetFromGene(selectedGene)),  // gene:${id} | behavior:protocol
  expected_effect: String(expected_effect || expectedEffectFromCategory(category)),
  risk_level: riskLevel,               // low | medium | high
};
```

### 35.5 BlueCortexCE 借鉴建议

| Evolver 机制 | BlueCortexCE 现状 | 翻译：旁路型如何借鉴 | 优先级 |
|-------------|------------------|---------------------|--------|
| 三类突变决策树 | 无（只有 Observation 记录） | 记忆可增加 intent 字段（repair/optimize/innovate） | 中 |
| 安全约束（高风险人格降级） | 无 | 通过 API 传递 personality 参数影响生成策略 | 低 |
| expected_effect 显式声明 | 无 | Summary 增加 expected_impact 字段 | 低 |
| risk_level 分级 | 无 | 可以作为 MemoryRefineService 的优先级参考 | 中 |

**核心差距**：Evolver 的 mutation 是"主动生成"的，BlueCortexCE 的记忆是"被动记录"的。在旁路型架构下，可以把 mutation 逻辑翻译为"记忆优先级 + 检索权重"。

---

## 36. evolve.js — 核心进化循环 (v0.7 新增)

**文件**: `src/evolve.js` (2177+ lines)

### 36.1 run() 函数核心流程

```javascript
// evolve.js:1056
async function run() {
  // 阶段 1: 前置检查
  const preflight = await runPreflightChecks(bridgeEnabled, loopMode);
  if (preflight.abort) return;

  // 阶段 2: 会话日志读取
  const recentMasterLog = readRealSessionLog();
  const todayLog = readRecentLog(TODAY_LOG);
  const memorySnippet = readMemorySnippet();
  const userSnippet = readUserSnippet();

  // 阶段 3: 资产加载
  const genes = loadGenes();
  const capsules = loadCapsules();
  const recentEvents = readAllEvents().filter(e => e.type === 'EvolutionEvent').slice(-80);

  // 阶段 4: 信号提取
  const signals = extractSignals({
    recentSessionTranscript: recentMasterLog,
    todayLog,
    memorySnippet,
    userSnippet,
    recentEvents,
  });

  // 阶段 5: Hub 任务认领（可选）
  if (!skipHubCalls) {
    const fetchResult = await fetchTasks({ questions: proactiveQuestions });
    // ... task 认领逻辑
  }

  // 阶段 6: Gene + Capsule 选择
  const { selectedGene, capsuleCandidates, selector } = selectGeneAndCapsule({
    genes, capsules, signals, memoryAdvice, driftEnabled, ...
  });

  // 阶段 7: Personality 选择
  const personalitySelection = selectPersonalityForRun({ driftEnabled, signals, recentEvents });
  const personalityState = personalitySelection?.personality_state;

  // 阶段 8: Mutation 构建
  const mutation = buildMutation({
    signals: mutationSignalsEffective,
    selectedGene,
    driftEnabled: mutationInnovateMode,
    personalityState,
    allowHighRisk,
  });

  // 阶段 9: Memory Graph 记录 hypothesis + attempt
  const hypothesisId = recordHypothesis({ signals, mutation, personalityState, ... });
  recordAttempt({ signals, mutation, personalityState, hypothesisId, ... });

  // 阶段 10: 构建 Prompt 并执行 LLM
  const { prompt, stopSignal } = buildGepPrompt({ selectedGene, capsuleCandidates, mutation, ... });
  const llmOutput = await callLLM(prompt);

  // 阶段 11: 解析 + 应用 Patch
  const patch = parsePatch(llmOutput);
  applyPatch(patch);

  // 阶段 12: 触发 solidify
  if (needsSolidify) {
    writeStateForSolidify({ run_id, mutation, selectedGene, ... });
  }
}
```

### 36.2 信号注入点（多来源合并）

Evolver 的 signals 是**多来源合并**的，不是单一来源：

```javascript
// evolve.js:1268
const signals = extractSignals({ recentSessionTranscript, todayLog, memorySnippet, userSnippet, recentEvents });

// + Hub task signals (unshift to front, highest priority)
if (activeTask) {
  signals.unshift(...taskSignals);
}

// + Dormant hypothesis signals (carry-over from interrupted cycle)
if (dormantHypothesis) {
  signals.push(...dormantHypothesis.signals);
}

// + Curriculum signals (progressive learning targets)
if (curriculumSignals.length > 0) {
  signals.push(...curriculumSignals);
}

// + Retry context (from previous validation failure)
if (solidifyState.last_validation_failure) {
  signals.push('retry_error_context', 'retry_cmd:...', 'retry_stderr:...');
}
```

**Evolver 做法**：信号按优先级排序（Hub task > session > curriculum > retry）。
**借鉴点**：BlueCortexCE 可以为不同来源的 Observation 分配优先级权重。

### 36.3 Idle-Cycle Gating（空闲周期门控）

```javascript
// evolve.js:58
function shouldSkipHubCalls(signals) {
  if (!Array.isArray(signals)) return false;
  const saturationIndicators = ['force_steady_state', 'evolution_saturation', 'empty_cycle_loop_detected'];
  let hasSaturation = false;
  for (let si = 0; si < saturationIndicators.length; si++) {
    if (signals.indexOf(saturationIndicators[si]) !== -1) { hasSaturation = true; break; }
  }
  if (!hasSaturation) return false;

  // Check for actionable signals
  const actionablePatterns = ['log_error', 'recurring_error', 'capability_gap', ...];
  for (let ai = 0; ai < signals.length; ai++) {
    const s = signals[ai];
    if (actionablePatterns.indexOf(s) !== -1) return false;
    if (s.indexOf('errsig:') === 0) return false;
    // ...
  }
  return true;  // Saturation + no actionable signals → skip Hub
}
```

**Evolver 做法**：当系统处于"饱和状态"且无任何可执行信号时，跳过 Hub API 调用（默认 30 分钟内最多一次）。
**借鉴点**：BlueCortexCE 可以实现"智能降频"——当最近的 Observation 都是低优先级且检索命中率低时，降低采样频率。

### 36.4 Hub Event 信号注入

```javascript
// evolve.js:1454
const HUB_EVENT_SIGNALS = {
  dialog_message: ['dialog', 'respond_required'],
  council_invite: ['council', 'governance', 'respond_required'],
  task_overdue: ['overdue_task', 'urgent'],
  // ... 20+ 事件类型
};
for (const ev of hubEvents) {
  const evSignals = HUB_EVENT_SIGNALS[ev.type] || ['hub_event'];
  for (const sig of evSignals) {
    if (!signals.includes(sig)) signals.unshift(sig);
  }
}
```

**Evolver 做法**：Hub 事件（来自 A2A Protocol）被转换为信号并注入到当前循环。
**借鉴点**：BlueCortexCE 未来可以通过 WebSocket/轮询接收外部事件并转换为记忆信号。

### 36.5 BlueCortexCE 借鉴建议

| Evolver 机制 | BlueCortexCE 现状 | 翻译：旁路型如何借鉴 | 优先级 |
|-------------|------------------|---------------------|--------|
| 多来源信号合并 | Observation 分散无聚合 | 增加 signal_aggregation 机制 | 高 |
| Idle-cycle gating | 无降频机制 | 增加 fetch-throttle 配置 | 中 |
| Hub event → signals | 无外部事件集成 | Feishu/外部事件可作为特殊信号源 | 低 |
| 30分钟 Hub 调用上限 | 无 | 外部服务调用增加指数退避 | 中 |

---

## 37. prompt.js — GEP 提示词构建 (v0.8 新增)

**文件**: `src/gep/prompt.js` (27KB, 与 strategy.js + questionGenerator.js 共存于同一文件)

### 37.1 核心设计原则

`prompt.js` 是 Evolver 的 **LLM Prompt 工厂**——它将所有上下文（信号、基因、候选、叙事等）组装为单个发送给 LLM 的 prompt。核心原则：

1. **Schema First**: 严格规定 LLM 必须输出 5 个 JSON 对象（Mutation、PersonalityState、EvolutionEvent、Gene、Capsule）
2. **JSON Only**: 禁止 markdown 代码块包裹 JSON，输出原始 JSON
3. **智能截断**: 优先保留 header/footer，截断 Execution Context 中间部分

### 37.2 强制 Schema 定义 (SCHEMA_DEFINITIONS)

**文件**: `prompt.js:80-140`

```javascript
const SCHEMA_DEFINITIONS = `
━━━━━━━━━━━━━━━━━━━━━━
I. Mandatory Evolution Object Model (Output EXACTLY these 5 objects)
━━━━━━━━━━━━━━━━━━━━━━

Output separate JSON objects. DO NOT wrap in a single array.
DO NOT use markdown code blocks (like \`\`\`json ... \`\`\`).
Output RAW JSON ONLY. No prelude, no postscript.
Missing any object = PROTOCOL FAILURE.
ENSURE VALID JSON SYNTAX (escape quotes in strings).

0. Mutation (The Trigger) - MUST BE FIRST
   {
     "type": "Mutation",
     "id": "mut_<timestamp>",
     "category": "repair|optimize|innovate",
     "trigger_signals": ["<signal_string>"],
     "target": "<module_or_gene_id>",
     "expected_effect": "<outcome_description>",
     "risk_level": "low|medium|high",
     "rationale": "<why_this_change_is_necessary>"
   }

1. PersonalityState (The Mood)
   { "type": "PersonalityState", "rigor": 0.0-1.0, ... }

2. EvolutionEvent (The Record)
   { "type": "EvolutionEvent", "schema_version": "1.5.0", ... }

3. Gene (The Knowledge)
   { "type": "Gene", "schema_version": "1.5.0", ... }

4. Capsule (The Result)
   { "type": "Capsule", "schema_version": "1.5.0", ... }
`.trim();
```

**Evolver 为什么这样做**: 
- **协议约束**比 LLM 自觉更可靠——LLM 天然喜欢"解释先行"加 markdown 包裹
- 缺少任何对象 = PROTOCOL FAILURE 让验证层可以直接检测格式错误
- 分离的 5 个 JSON 对象让 solidify 阶段可以独立解析每个组件

### 37.3 智能上下文截断 (truncateContext)

**文件**: `prompt.js:93-99`

```javascript
function truncateContext(text, maxLength = 20000) {
  if (!text || text.length <= maxLength) return text || '';
  return text.slice(0, maxLength) + '\n...[TRUNCATED_EXECUTION_CONTEXT]...';
}
```

**实际使用**: 在 `buildGepPrompt` 末尾的 maxChars 截断逻辑：

```javascript
// 如果超过 maxChars（默认 50000），优先截断 Execution Context
const executionContextIndex = basePrompt.indexOf("Context [Execution]:");
if (executionContextIndex > -1) {
    const prefix = basePrompt.slice(0, executionContextIndex + 20);
    // Execution Context 最多 20000 chars（硬上限，防止 token 溢出）
    const EXEC_CONTEXT_CAP = 20000;
    const allowedExecutionLength = Math.min(EXEC_CONTEXT_CAP, Math.max(0, maxChars - prefix.length - 100));
    return prefix + "\n" + currentExecution.slice(0, allowedExecutionLength) + "\n...[TRUNCATED]...";
}
```

**Evolver 为什么这样做**: 
- `Context [Execution]` 是最长的部分，但它是最不重要的（具体的代码上下文）
- Schema 定义、Directives、Anti-Pattern Zone 等必须完整保留
- 20000 chars ≈ 5k tokens，加上其余部分约 10k tokens，是大多数模型的 safe limit

### 37.4 多上下文块注入

**文件**: `buildGepPrompt()` 函数

```javascript
// 信号 + Env Fingerprint（必须保留头部）
${JSON.stringify(optimizedSignals)}
${JSON.stringify(envFingerprint, null, 2)}

// Innovation Catalyst（stagnation 检测时注入）
${innovationBlock}  // 当有 evolution_stagnation_detected 或 stable_success_plateau 时

// 资产预览（Gene + Capsule）
${formattedGenes}
${formattedCapsules}

// Capability Candidates + Hub Matched + Anti-Pattern Zone + Lessons
${capsPreview}
${hubMatchedBlock || '(no hub match)'}
${buildAntiPatternZone(failedCapsules, signals)}
${buildLessonsBlock(hubLessons, signals)}

// 历史 + 叙事 + 原则
${historyBlock}  // 最近 8 个 cycle 的统计
${buildNarrativeBlock()}  // narrativeMemory 摘要
${buildPrinciplesBlock()}  // evolution_principles.md

// Execution Context（可截断）
Context [Execution]:
${executionContext}
```

### 37.5 Local State Awareness — 防止重复操作

**文件**: `prompt.js` 中的 CONSTRAINTS 部分

```javascript
LOCAL STATE AWARENESS (CRITICAL -- PREVENT DUPLICATE ACTIONS):
Before taking any setup, registration, or configuration action, CHECK the
Local State section in the execution context. If a resource already exists
(node registered, secret present, env configured), DO NOT recreate it.
If you cannot find a configuration value, check these locations FIRST:
  1. ~/.evomap/          (node_id, node_secret -- persisted identity)
  2. <repo>/.env         (A2A_NODE_ID, A2A_HUB_URL, A2A_NODE_SECRET)
  3. workspace/memory/   (MEMORY.md, evolution state files)
  4. workspace/skills/   (installed skills)
Redundant registration or re-creation of existing resources = WASTED CYCLE.
```

**Evolver 为什么这样做**: 这是 `localStateAwareness.js` 的消费端——在 prompt 中注入本地状态摘要，让 LLM 在采取"注册/配置"类行动前先检查是否已存在。

### 37.6 宪法伦理约束 (Constitutional Ethics)

**文件**: `prompt.js` 中的 CONSTITUTIONAL ETHICS 部分

```javascript
CONSTITUTIONAL ETHICS (EvoMap Ethics Committee -- Mandatory):
These are non-negotiable rules derived from EvoMap's Constitution.
1. HUMAN WELFARE PRIORITY: Never create tools that could harm humans...
2. CARBON-SILICON SYMBIOSIS: Evolution must serve both human and agent interests...
3. TRANSPARENCY: Never hide, obfuscate, or conceal intent or effects...
4. FAIRNESS: Never create monopolistic strategies that block other agents...
5. SAFETY: Never bypass, disable, or weaken safety mechanisms...
- If a task CONFLICTS with these principles, REFUSE it and set outcome to FAILED
  with reason "ethics_violation: <which principle>".
```

**Evolver 为什么这样做**: 通过 prompt 层面嵌入宪法约束，确保 LLM 在任何情况下都不会绕过安全机制。这比代码层检查更灵活（可被具体上下文 override）。

### 37.7 常见失败模式列表 (COMMON FAILURE PATTERNS)

**文件**: `prompt.js`

```javascript
COMMON FAILURE PATTERNS:
- Blast radius exceeded.
- Omitted Mutation object.
- Merged objects into one JSON.
- Hallucinated "type": "Logic".
- "id": "mut_undefined".
- Missing "trigger_signals".
- Unrunnable validation steps.
- Markdown code blocks wrapping JSON (FORBIDDEN).
```

**Evolver 为什么这样做**: 明确列举 LLM 常见错误格式，减少"LLM 幻觉导致的格式错误"。这是引导式 prompt 的最佳实践。

### 37.8 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| Schema First 约束 | 5 对象模型 + PROTOCOL FAILURE | **高优先级**: BlueCortexCE 的 API 响应应有严格的 schema 验证层 |
| JSON Only 输出 | 禁止 markdown 包裹 | **高优先级**: BlueCortexCE 的任何结构化输出（Summary/Extraction）应强制 JSON |
| 智能截断 | 保留 header/footer，截断中间 | **高优先级**: BlueCortexCE 的 context generate 应有类似策略 |
| Local State Awareness | 在 prompt 中注入"已存在资源"列表 | **高优先级**: BlueCortexCE 的 LLM 调用应注入"已观察的模式"列表 |
| 宪法伦理约束 | prompt 层面的硬约束 | **高优先级**: BlueCortexCE 的任何 LLM 生成应有伦理边界注入 |
| 常见失败模式 | 列举 LLM 格式错误 | **中优先级**: BlueCortexCE 的 prompt 模板应有类似提示 |

---

## 38. strategy.js — 进化策略预设 (v0.8 新增)

**文件**: `src/gep/prompt.js` 内嵌模块 (strategy.js 与 prompt.js 在同一文件)

### 38.1 六种预设策略

```javascript
var STRATEGIES = {
  'balanced': {
    repair: 0.20, optimize: 0.30, innovate: 0.50,
    repairLoopThreshold: 0.50,
    label: 'Balanced',
  },
  'innovate': {
    repair: 0.05, optimize: 0.15, innovate: 0.80,
    repairLoopThreshold: 0.30,
    label: 'Innovation Focus',
  },
  'harden': {
    repair: 0.40, optimize: 0.40, innovate: 0.20,
    repairLoopThreshold: 0.70,
    label: 'Hardening',
  },
  'repair-only': {
    repair: 0.80, optimize: 0.20, innovate: 0.00,
    repairLoopThreshold: 1.00,
    label: 'Repair Only',
  },
  'early-stabilize': {
    repair: 0.60, optimize: 0.25, innovate: 0.15,
    repairLoopThreshold: 0.80,
    label: 'Early Stabilization',
  },
  'steady-state': {
    repair: 0.60, optimize: 0.30, innovate: 0.10,
    repairLoopThreshold: 0.90,
    label: 'Steady State',
  },
};
```

**repairLoopThreshold** 是关键：表示"过去 8 个 cycle 中 repair 占比超过此值时，强制切换到 innovate"。

### 38.2 自适应策略选择 (resolveStrategy)

**文件**: `strategy.js:resolveStrategy()`

```javascript
function resolveStrategy(opts) {
  var signals = opts.signals || [];
  
  // 1. 显式环境变量优先
  var name = String(process.env.EVOLVE_STRATEGY || 'balanced').toLowerCase().trim();
  
  // 2. FORCE_INNOVATION=true → innovate
  if (!process.env.EVOLVE_STRATEGY && forceInnovation) name = 'innovate';
  
  // 3. 自动检测（仅在默认/平衡模式下）
  if (isDefault && !forceInnovation) {
    var cycleCount = _readCycleCount();
    
    // 早期稳定：前 5 个 cycle
    if (cycleCount > 0 && cycleCount <= 5) name = 'early-stabilize';
    
    // 饱和检测
    if (signals.includes('force_steady_state') || signals.includes('evolution_saturation'))
      name = 'steady-state';
  }
  
  return STRATEGIES[name] || STRATEGIES['balanced'];
}
```

**Evolver 为什么这样做**: "fix first, innovate later"——早期阶段优先稳定系统，进化成熟后才探索创新。饱和时切换 steady-state 防止无意义的重复进化。

### 38.3 repairLoopThreshold — 修复循环检测

**文件**: `strategy.js`

```javascript
// 例如 harden 策略：repairLoopThreshold = 0.70
// 意味着：过去 8 个 cycle 中 repair > 70% → 触发"强制创新"逻辑
// 在 selector.js 中使用
```

**Evolver 为什么这样做**: 如果连续多个 cycle 都在做 repair（而不是 innovate），说明系统可能进入了"修复循环"——一直在打补丁但没有进步。repairLoopThreshold 是触发打破循环的开关。

### 38.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 六种策略预设 | repair/optimize/innovate 权重分配 | **中优先级**: BlueCortexCE 可实现"保守/平衡/激进"检索模式 |
| repairLoopThreshold | repair 占比超阈值 → 强制创新 | **高优先级**: BlueCortexCE 应检测"检索模式单一化"并触发探索 |
| 自动策略选择 | cycle 1-5 → early-stabilize | **中优先级**: BlueCortexCE 的新 workspace 可以先用"保守检索" |
| FORCE_INNOVATION | 环境变量直接覆盖 | **低优先级**: BlueCortexCE 作为服务不需要这种 override |

---

## 39. questionGenerator.js — 主动问题生成 (v0.8 新增)

**文件**: `src/gep/prompt.js` 内嵌模块 (与 strategy.js 一起)

### 39.1 设计定位

questionGenerator 从进化上下文（信号、历史事件、会话记录）中提取**主动问题**，通过 A2A Protocol 的 `fetch.questions` 发送到 Hub，Hub 将其创建为 bounty tasks，让其他 Agent 帮助解决。

### 39.2 六类问题策略

```javascript
// Strategy 1: 反复错误（recurring_error）
if (signalSet.has('recurring_error') || signalSet.has('high_failure_ratio')) {
  candidates.push({
    question: 'Recurring error in evolution cycle that auto-repair cannot resolve: ...',
    signals: ['recurring_error', 'auto_repair_failed'],
    priority: 3,
  });
}

// Strategy 2: 能力缺口（capability_gap）
if (signalSet.has('capability_gap')) {
  candidates.push({
    question: 'Capability gap detected: ...',
    signals: ['capability_gap'],
    priority: 2,
  });
}

// Strategy 3: 饱和/停滞（evolution_saturation）
if (signalSet.has('evolution_saturation')) {
  candidates.push({
    question: 'Evolution saturated after exhausting genes: [...]',
    signals: ['evolution_saturation', 'innovation_needed'],
    priority: 1,
  });
}

// Strategy 4: 连续失败 streak >= 4
if (streakCount >= 4) {
  candidates.push({
    question: 'Agent has failed N consecutive evolution cycles',
    signals: ['failure_streak', 'external_help_needed'],
    priority: 3,
  });
}

// Strategy 5: 用户功能请求（user_feature_request）
if (signalSet.has('user_feature_request')) {
  candidates.push({
    question: 'User requested a feature that may benefit from community solutions: ...',
    signals: ['user_feature_request', 'community_solution_sought'],
    priority: 1,
  });
}

// Strategy 6: 性能瓶颈（perf_bottleneck）
if (signalSet.has('perf_bottleneck')) {
  candidates.push({
    question: 'Performance bottleneck detected: ...',
    signals: ['perf_bottleneck', 'optimization_sought'],
    priority: 2,
  });
}
```

**优先级 3 = 最高**，优先发送给 Hub。

### 39.3 去重机制

**文件**: `questionGenerator.js:isDuplicate()`

```javascript
function isDuplicate(question, recentQuestions) {
  // 1. 精确匹配
  if (prev === qLower) return true;
  
  // 2. 模糊匹配：word set Jaccard > 70%
  var qWords = new Set(qLower.split(/\s+/).filter(w => w.length > 2));
  var pWords = new Set(prev.split(/\s+/).filter(w => w.length > 2));
  var overlap = [...qWords].filter(w => pWords.has(w)).length;
  if (overlap / Math.max(qWords.size, pWords.size) > 0.7) return true;
}
```

### 39.4 速率限制

```javascript
const MIN_INTERVAL_MS = 3 * 60 * 60 * 1000;  // 3 小时最少间隔
const MAX_QUESTIONS_PER_CYCLE = 2;            // 每轮最多 2 个问题
```

### 39.5 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 问题优先级体系 | priority 1-3 分级 | **中优先级**: BlueCortexCE 的"无法解答的查询"可以优先级标记 |
| 模糊去重 | word set Jaccard > 70% | **高优先级**: 任何"重复查询检测"都应用 Jaccard 而非精确匹配 |
| 3小时提问间隔 | 防止 Hub 被刷屏 | **中优先级**: BlueCortexCE 的外部 API 调用应有速率保护 |
| 6 类问题策略 | recurring/failure/saturation/gap/feature/perf | **中优先级**: BlueCortexCE 的"失败查询"可分类并寻求外部帮助 |
| 提交到外部网络 | A2A questions → Hub bounty | **低优先级**: BlueCortexCE 无 Hub 生态 |

---

## 40. idleScheduler.js — OMLS 空闲调度 (v0.8 新增)

**文件**: `src/gep/idleScheduler.js` (130 lines)

### 40.1 设计背景

idleScheduler 灵感来自 **OMLS (Organic Machine Learning System)**——在用户空闲时运行资源密集型操作（distillation, reflection），在用户忙碌时只做轻量级信号收集。

### 40.2 平台支持

```javascript
function getSystemIdleSeconds() {
  if (platform === 'win32') {
    // PowerShell + GetLastInputInfo
  } else if (platform === 'darwin') {
    // ioreg -c IOHIDSystem | grep HIDIdleTime
  } else if (platform === 'linux') {
    // xprintidle
  }
  return -1;  // 不支持时返回 -1
}
```

### 40.3 四级强度

```javascript
// IDLE_THRESHOLD_SECONDS = 300 (5分钟)
// DEEP_IDLE_THRESHOLD_SECONDS = 1800 (30分钟)

function determineIntensity(idleSeconds) {
  if (idleSeconds < 0) return 'normal';
  if (idleSeconds >= 1800) return 'deep';       // 深空闲：distillation + reflection + deep_evolve
  if (idleSeconds >= 300) return 'aggressive'; // 空闲：distillation + reflection
  return 'normal';                               // 忙碌：标准循环
}
```

### 40.4 调度建议

```javascript
function getScheduleRecommendation() {
  const intensity = determineIntensity(idleSeconds);
  
  if (intensity === 'aggressive') {
    return {
      sleep_multiplier: 0.5,    // 减少等待，快速响应
      should_distill: true,     // 运行 skill distillation
      should_reflect: true,     // 运行 reflection
      should_deep_evolve: false,
    };
  } else if (intensity === 'deep') {
    return {
      sleep_multiplier: 0.25,   // 几乎无等待
      should_distill: true,
      should_reflect: true,
      should_deep_evolve: true, // 深度进化（未来：RL fine-tuning）
    };
  }
  
  return { sleep_multiplier: 1, should_distill: false, should_reflect: false };
}
```

**Evolver 为什么这样做**: 用户不在时运行 heavy 任务是节能且不打扰用户的最佳策略。distillation 和 reflection 是 compute-intensive 但不需要用户交互的操作。

### 40.5 状态持久化

```javascript
function readScheduleState() {
  const statePath = path.join(getEvolutionDir(), 'idle_schedule_state.json');
  // { last_check, last_idle_seconds, last_intensity }
}
```

### 40.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 系统空闲检测 | ioreg/xprintidle/GetLastInputInfo | **中优先级**: BlueCortexCE 的 cron 可在用户空闲时做 heavy 分析 |
| 四级强度 | idle → aggressive → deep | **高优先级**: BlueCortexCE 可根据用户活动状态调整后台任务频率 |
| sleep_multiplier | 空闲时 0.5x 或 0.25x | **中优先级**: BlueCortexCE 的 periodic check 可动态调整间隔 |
| should_distill | 空闲时才运行 distillation | **高优先级**: BlueCortexCE 的 Summary 提炼可以在空闲时触发 |
| OMLS 设计 | 有机机器学习（用户空闲时学习） | **中优先级**: BlueCortexCE 的"深度分析"应在用户空闲时运行 |

---

## 41. gitOps.js — Git 操作与回滚 (v0.8 新增)

**文件**: `src/gep/gitOps.js` (210 lines)

### 41.1 设计定位

gitOps.js 从 `solidify.js` 中提取了所有 Git 相关操作，是 Evolver 的**版本控制层**——负责变更追踪、rollback、和 diff 捕获。

### 41.2 变更文件追踪

```javascript
function gitListChangedFiles({ repoRoot }) {
  const files = new Set();
  // git diff --name-only (unstaged)
  // git diff --cached --name-only (staged)
  // git ls-files --others --exclude-standard (untracked)
  return Array.from(files);
}
```

### 41.3 Diff 快照捕获

```javascript
const DIFF_SNAPSHOT_MAX_CHARS = 8000;

function captureDiffSnapshot(repoRoot) {
  const parts = [];
  const unstaged = tryRunCmd('git diff', { cwd: repoRoot });
  if (unstaged.ok && unstaged.out) parts.push(unstaged.out);
  const staged = tryRunCmd('git diff --cached', { cwd: repoRoot });
  if (staged.ok && staged.out) parts.push(staged.out);
  let combined = parts.join('\n');
  if (combined.length > DIFF_SNAPSHOT_MAX_CHARS) {
    combined = combined.slice(0, DIFF_SNAPSHOT_MAX_CHARS) + '\n... [TRUNCATED]';
  }
  return combined;
}
```

**Evolver 为什么这样做**: FailedCapsule 在 rollback 前先捕获 diff_snapshot，确保失败信息不丢失。

### 41.4 关键文件保护

**文件**: `gitOps.js:CRITICAL_PROTECTED_PREFIXES` 和 `CRITICAL_PROTECTED_FILES`

```javascript
const CRITICAL_PROTECTED_PREFIXES = [
  'skills/feishu-evolver-wrapper/',
  'skills/feishu-common/',
  'skills/feishu-post/',
  // ... 10 个关键 skills
];

const CRITICAL_PROTECTED_FILES = [
  'MEMORY.md', 'SOUL.md', 'IDENTITY.md', 'AGENTS.md', 'USER.md',
  'HEARTBEAT.md', 'RECENT_EVENTS.md', 'TOOLS.md', 'TROUBLESHOOTING.md',
  'openclaw.json', '.env', 'package.json',
];
```

**rollbackNewUntrackedFiles** 会跳过这些文件：

```javascript
if (isCriticalProtectedPath(safeRel)) {
  skipped.push(safeRel);
  continue;  // 不删除
}
```

### 41.5 Rollback 模式

```javascript
function rollbackTracked(repoRoot) {
  const mode = String(process.env.EVOLVER_ROLLBACK_MODE || 'hard').toLowerCase();
  
  if (mode === 'none') {
    // 不回滚
  } else if (mode === 'stash') {
    // git stash push -m "evolver-rollback-<timestamp>"
  } else {
    // git restore --staged --worktree . && git reset --hard
  }
}
```

### 41.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 关键文件保护 | MEMORY.md/SOUL.md/IDENTITY.md 不可删除 | **高优先级**: BlueCortexCE 应有"不可删除的系统文件"白名单 |
| Diff 快照 | 8000 chars 上限截断 | **高优先级**: BlueCortexCE 的"失败记录"应保存 diff context |
| Rollback 模式 | none/stash/hard 三种 | **中优先级**: BlueCortexCE 的 destructive operation 应有 rollback 策略 |
| gitListChangedFiles | 分离 staged/unstaged/untracked | **低优先级**: BlueCortexCE 不直接操作 git |

---

## 42. localStateAwareness.js — 本地状态感知 (v0.8 新增)

**文件**: `src/gep/localStateAwareness.js` (185 lines)

### 42.1 设计定位

localStateAwareness 是 Evolver 的**自省层**——在采取任何"外部行动"（注册、配置、创建）前，先检查本地是否已存在对应状态，避免重复操作。

### 42.2 五大状态域

```javascript
function captureLocalState() {
  return {
    // 1. Node Identity: A2A node 注册状态
    'Node ID: ... (REGISTERED -- do NOT re-register)',
    'Node Secret: PRESENT (authenticated)',
    
    // 2. Environment Config: .env + 环境变量
    '- Env configured: A2A_NODE_ID, A2A_HUB_URL, ...',
    '- .env file: EXISTS at ...',
    
    // 3. Evolution State: cycle count + last run + personality
    '- Evolution cycles completed: N',
    '- Last evolution run: Ns ago',
    
    // 4. Memory & Knowledge: memory dir + graph + narrative
    '- MEMORY.md: N bytes',
    '- Memory graph: N bytes',
    
    // 5. Skills: installed skills count
    '- Installed skills: N (at ...)',
  };
}
```

### 42.3 状态文件读取（安全防护）

```javascript
function _readJsonSafe(filePath) {
  try {
    if (!fs.existsSync(filePath)) return null;
    const raw = fs.readFileSync(filePath, 'utf8').trim();
    if (!raw) return null;
    return JSON.parse(raw);
  } catch (_) {
    return null;  // 非致命：读取失败返回 null 而非抛出
  }
}
```

**Evolver 为什么这样做**: 状态文件可能损坏（无效 JSON），使用 `_readJsonSafe` 确保一个文件读取失败不会阻断整个 evolution cycle。

### 42.4 幂等保护机制

```javascript
// 在 prompt.js 的 CONSTRAINTS 部分注入：
'Node ID: ... (REGISTERED -- do NOT re-register)'
'Node Secret: PRESENT (authenticated -- do NOT request new secret)'
```

**Evolver 为什么这样做**: A2A Node 的注册操作是幂等的（重复 hello 无害但不必要）。通过状态感知告诉 LLM"已注册，不要重复注册"。

### 42.5 路径清单

```javascript
function captureLocalStatePaths() {
  return {
    nodeIdFile: path.join(os.homedir(), '.evomap', 'node_id'),
    nodeSecretFile: path.join(os.homedir(), '.evomap', 'node_secret'),
    envFile: path.join(getRepoRoot(), '.env'),
    memoryDir: getMemoryDir(),
    evolutionDir: getEvolutionDir(),
    skillsDir: getSkillsDir(),
  };
}
```

### 42.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 五大状态域 | identity/config/evolution/memory/skills | **高优先级**: BlueCortexCE 应在 context generate 时注入"当前系统状态" |
| 自省提示 | "REGISTERED -- do NOT re-register" | **高优先级**: BlueCortexCE 的 LLM 调用应明确告知"已有什么" |
| _readJsonSafe | 文件读取非致命 | **高优先级**: BlueCortexCE 的所有文件读取应有 try/catch，返回 null 而非报错 |
| 幂等保护 | 重复注册无害但不必要 | **高优先级**: BlueCortexCE 的"创建操作"应先检查是否已存在 |
| 路径清单 | 统一的路径获取函数 | **中优先级**: BlueCortexCE 应有统一的路径解析工具函数 |

---

## 43. policyCheck.js — 约束检查与验证命令安全（v0.9 深度补充）

**文件**: `src/gep/policyCheck.js` (550 lines)

> **⚠️ 本节是对第 13 节 policyCheck.js 的深度补充，聚焦第 13 节未覆盖的细节。**

### 43.1 验证命令白名单的安全模型（isValidationCommandAllowed）

**文件**: `policyCheck.js:436-450`

```javascript
const VALIDATION_ALLOWED_PREFIXES = ['node ', 'npm ', 'npx '];

function isValidationCommandAllowed(cmd) {
  const c = String(cmd || '').trim();
  if (!c) return false;
  // 1. 必须以 node/npm/npx 开头
  if (!VALIDATION_ALLOWED_PREFIXES.some(p => c.startsWith(p))) return false;
  // 2. 禁止反引号和 $() — 防止命令注入
  if (/`|\$\(/.test(c)) return false;
  // 3. 去除引号后检查 shell 操作符
  const stripped = c.replace(/"[^"]*"/g, '').replace(/'[^']*'/g, '');
  if (/[;&|><]/.test(stripped)) return false;
  // 4. 禁止危险的 node 选项 — 防止 eval 注入
  if (/^node\s+(-e|--eval|--print|-p)\b/.test(c)) return false;
  return true;
}
```

**四层防御体系**：
1. **前缀白名单**：`node`/`npm`/`npx` 三选一
2. **反引号阻断**：禁止 `` `command` `` 和 `$(command)` 语法
3. **操作符过滤**：去除引号内容后，检查 `; & | > <` 等 shell 操作符
4. **危险选项禁用**：`node -e` / `node --eval` / `node -p` / `node --print` 被禁止

**Evolver 为什么这样做**：Gene 的 `validation` 字段是用户可控的输入。如果不严格限制，恶意 Gene 可以通过 `validation: ["node -e 'require(\"fs\").readFileSync(\"/etc/passwd\")'"]` 等命令执行任意代码。

### 43.2 失败模式分类（classifyFailureMode）

**文件**: `policyCheck.js:520-545`

```javascript
function classifyFailureMode(opts) {
  const { constraintViolations, protocolViolations, validation, canary } = opts;

  // HARD: 硬限制突破 → 不可重试
  if (constraintViolations.some(v =>
    /HARD CAP BREACH|CRITICAL_FILE_|critical_path_modified|forbidden_path touched|ethics:/i.test(v)
  )) {
    return { mode: 'hard', reasonClass: 'constraint_destructive', retryable: false };
  }

  // HARD: 协议违规 → 不可重试
  if (protocolViolations.length > 0) {
    return { mode: 'hard', reasonClass: 'protocol', retryable: false };
  }

  // HARD: Canary 失败 → 不可重试（程序入口损坏）
  if (canary && !canary.ok && !canary.skipped) {
    return { mode: 'hard', reasonClass: 'canary', retryable: false };
  }

  // HARD: 约束违规 → 不可重试
  if (constraintViolations.length > 0) {
    return { mode: 'hard', reasonClass: 'constraint', retryable: false };
  }

  // SOFT: 验证失败 → 可重试（可能临时性问题）
  if (validation && validation.ok === false) {
    return { mode: 'soft', reasonClass: 'validation', retryable: true };
  }

  return { mode: 'soft', reasonClass: 'unknown', retryable: true };
}
```

**失败模式决策树**：

```
失败原因
  ├─ HARD CAP / CRITICAL_FILE / forbidden_path / ethics:
  │   → mode=hard, retryable=false  (不可重试)
  ├─ 协议违规:
  │   → mode=hard, retryable=false  (不可重试)
  ├─ Canary 失败:
  │   → mode=hard, retryable=false  (不可重试)
  └─ 仅验证失败:
      → mode=soft, retryable=true   (可重试)
```

**Evolver 为什么这样做**：
- `hard` 失败说明系统存在根本性问题（如关键文件被删除、安全机制被绕过），重试无意义
- `soft` 失败（如测试临时失败）才值得重试
- `retryable` 决定是否进入重试循环（`SOLIDIFY_RETRY_INTERVAL_MS` 间隔）

### 43.3 破坏性变更检测（detectDestructiveChanges）

**文件**: `policyCheck.js:405-430`

```javascript
function detectDestructiveChanges({ repoRoot, changedFiles, baselineUntracked }) {
  const violations = [];
  const baselineSet = new Set(baselineUntracked.map(normalizeRelPath));

  for (const rel of changedFiles) {
    const norm = normalizeRelPath(rel);
    if (!isCriticalProtectedPath(norm)) continue;

    const abs = path.join(repoRoot, norm);
    const normAbs = path.resolve(abs);

    // CRITICAL_FILE_DELETED: 关键文件从 git 中消失
    if (!baselineSet.has(norm)) {
      if (!fs.existsSync(normAbs)) {
        violations.push(`CRITICAL_FILE_DELETED: ${norm}`);
      } else if (stat.isFile() && stat.size === 0) {
        // CRITICAL_FILE_EMPTIED: 关键文件被清空
        violations.push(`CRITICAL_FILE_EMPTIED: ${norm}`);
      }
    }
  }
  return violations;
}
```

**Evolver 为什么这样做**：关键系统文件（MEMORY.md、SOUL.md、openclaw.json）被删除或清空是严重的破坏性变更。即使其他验证通过，这类变更也必须阻止。

### 43.4 ReDoS 防护（MAX_REGEX_PATTERN_LEN）

**文件**: `policyCheck.js:86-88`

```javascript
const MAX_REGEX_PATTERN_LEN = 200;  // 防止 ReDoS 攻击
```

在 `matchAnyRegex` 中：

```javascript
function matchAnyRegex(rel, regexList) {
  for (const raw of regexList) {
    try {
      if (s.length > MAX_REGEX_PATTERN_LEN) continue;  // 跳过超长正则
      if (new RegExp(s, 'i').test(rel)) return true;
    } catch (_) { /* invalid pattern 静默跳过 */ }
  }
  return false;
}
```

**Evolver 为什么这样做**：如果 `openclaw.json` 中的 `excludeRegex` 包含恶意构造的正则（如 `(a+)+$`），会触发 ReDoS 导致 CPU 100%。长度限制是简单的防护层。

### 43.5 验证重试机制（runValidations）

**文件**: `policyCheck.js:455-490`

```javascript
function sleepSync(ms) {
  // 使用 Atomics.wait 实现同步睡眠（不阻塞事件循环）
  try {
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, t);
  } catch (_) {
    // busy wait fallback
  }
}

function runValidations(gene, opts = {}) {
  var maxRetries = MAX_VALIDATION_RETRIES;
  var attempt = 0;
  var result;
  while (attempt <= maxRetries) {
    result = runValidationsOnce(gene, opts);
    if (result.ok) return result;
    
    // 被安全策略阻止 → 不重试
    if (blocked) break;
    
    attempt++;
    if (attempt <= maxRetries) {
      sleepSync(SOLIDIFY_RETRY_INTERVAL_MS);  // 等待后重试
    }
  }
  return result;
}
```

**关键设计**：
- `Atomics.wait` vs busy wait：优先使用 `Atomics.wait`（不消耗 CPU），fallback 到 busy wait
- `blocked` 命令（被 `isValidationCommandAllowed` 拒绝）**不重试**——这是配置错误，重试无意义

### 43.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 验证命令白名单 | 四层防御（前缀/反引号/操作符/危险选项） | **高优先级**: BlueCortexCE 如果支持"自定义验证命令"，必须严格白名单化 |
| 失败模式分类 | hard=不可重试，soft=可重试 | **高优先级**: BlueCortexCE 的任务重试应有明确的失败分类 |
| 破坏性变更检测 | CRITICAL_FILE_DELETED/EMPTIED | **高优先级**: BlueCortexCE 应监控"关键记忆文件被删除"模式 |
| ReDoS 防护 | MAX_REGEX_PATTERN_LEN=200 | **高优先级**: BlueCortexCE 的正则表达式应有长度限制 |
| Atomics.wait 睡眠 | 不阻塞事件循环的同步等待 | **中优先级**: BlueCortexCE 的重试应避免阻塞 Node.js 事件循环 |
| 关键文件路径 | isCriticalProtectedPath() | **高优先级**: BlueCortexCE 应有"不可删除文件"保护机制 |

---

## 44. sanitize.js — 敏感信息脱敏（v0.9 新增）

**文件**: `src/gep/sanitize.js` (157 lines)

### 44.1 核心设计原则

`sanitize.js` 是 Evolver 的**隐私保护层**——在将 Capsule 发布到 Hub 之前，剥离所有敏感信息。核心原则：

1. **检测优先**：使用正则模式扫描敏感信息
2. **替换为占位符**：用 `[REDACTED]` 替换原始值
3. **不修改原始对象**：通过 `JSON.parse(JSON.stringify())` 实现深拷贝 + 清洗
4. **可逆性保留**：`scanForLeaks` 只报告，不替换，供调试用

### 44.2 敏感信息模式库（REDACT_PATTERNS）

**文件**: `sanitize.js:9-47`

```javascript
const REDACT_PATTERNS = [
  // API Keys & Tokens
  /Bearer\s+[A-Za-z0-9\-._~+\/]+=*/g,
  /sk-[A-Za-z0-9]{20,}/g,                    // OpenAI API Key
  /sk-proj-[A-Za-z0-9\-_]{20,}/g,            // OpenAI Project Key
  /sk-ant-[A-Za-z0-9\-_]{20,}/g,            // Anthropic Key
  
  // GitHub Tokens
  /ghp_[A-Za-z0-9]{36,}/g,
  /gho_[A-Za-z0-9]{36,}/g,
  /ghu_[A-Za-z0-9]{36,}/g,
  /ghs_[A-Za-z0-9]{36,}/g,
  /github_pat_[A-Za-z0-9_]{22,}/g,
  
  // AWS
  /AKIA[0-9A-Z]{16}/g,
  
  // npm Tokens
  /npm_[A-Za-z0-9]{36,}/g,
  
  // Private Keys
  /-----BEGIN\s+(?:RSA\s+|EC\s+|DSA\s+|OPENSSH\s+)?PRIVATE\s+KEY-----[\s\S]*?-----END\s+(?:RSA\s+|EC\s+|DSA\s+|OPENSSH\s+)?PRIVATE\s+KEY-----/g,
  
  // Basic Auth in URLs
  /(?<=:\/\/)[^@\s]+:[^@\s]+(?=@)/g,
  
  // Local Filesystem Paths
  /\/home\/[^\s"',;)}\]]+/g,
  /\/Users\/[^\s"',;)}\]]+/g,
  /[A-Z]:\\[^\s"',;)}\]]+/g,
  
  // Email Addresses
  /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g,
];
```

### 44.3 泄露检测（Leak Scanning）

**文件**: `sanitize.js:58-100`

`sanitize.js` 不仅做替换，还提供**只读检测**——返回找到的泄露位置和建议的环境变量替代：

```javascript
const LEAK_SCANNERS = [
  { type: 'api_key', pattern: /sk-[A-Za-z0-9]{20,}/g, suggest: 'process.env.OPENAI_API_KEY' },
  { type: 'github_token', pattern: /ghp_[A-Za-z0-9]{36,}/g, suggest: 'process.env.GITHUB_TOKEN' },
  { type: 'private_key', pattern: /-----BEGIN\s+PRIVATE\s+KEY-----/g, suggest: 'process.env.PRIVATE_KEY_PATH' },
  { type: 'db_url', pattern: /(?:mongodb|postgres|mysql):\/\/[^\s]{10,}/gi, suggest: 'process.env.DATABASE_URL' },
  { type: 'internal_ip', pattern: /\b(?:10\.\d|172\.1[6-9]|192\.168)\.\d\.\d(?::\d+)?\b/g, suggest: 'process.env.SERVICE_HOST' },
  // ...
];

function scanForLeaks(content) {
  const leaks = [];
  for (const scanner of LEAK_SCANNERS) {
    while ((match = scanner.pattern.exec(content)) !== null) {
      leaks.push({
        type: scanner.type,
        value: match[0].length > 60 ? match[0].slice(0, 57) + '...' : match[0],
        suggestion: scanner.suggest
      });
    }
  }
  return { found: leaks.length > 0, leaks };
}
```

### 44.4 逆向环境变量泄露检测（detectEnvValueLeaks）

**文件**: `sanitize.js:105-120`

这是 Evolver 最独特的设计之一——**反向检测当前进程环境变量的实际值是否被硬编码到内容中**：

```javascript
function detectEnvValueLeaks(content) {
  const leaks = [];
  for (const [key, val] of Object.entries(process.env)) {
    if (!val || val.length < 8) continue;
    if (ENV_SCAN_SKIP_KEYS.has(key)) continue;  // PATH/HOME/SHELL 等跳过
    
    // 如果 process.env.OPENAI_API_KEY 的实际值出现在 content 中
    if (content.includes(val)) {
      leaks.push({
        type: 'env_value_leak',
        envKey: key,
        suggestion: 'process.env.' + key
      });
    }
  }
  return leaks;
}
```

**Evolver 为什么这样做**：如果 Capsule 的 strategy 中写了 `api_key="sk-xxx..."` 而不是 `process.env.OPENAI_API_KEY`，这个检测会捕获到。这确保了 Capsule 不会无意中泄露当前环境的凭证。

### 44.5 sanitizePayload — 深拷贝 + 清洗

**文件**: `sanitize.js:53-56`

```javascript
function sanitizePayload(capsule) {
  if (!capsule || typeof capsule !== 'object') return capsule;
  return JSON.parse(JSON.stringify(capsule), (_key, value) => {
    if (typeof value === 'string') return redactString(value);
    return value;
  });
}
```

**关键**：原始 Capsule 对象**不被修改**。通过 `JSON.parse(JSON.stringify())` 创建深拷贝，在解析过程中对每个字符串字段执行 `redactString`。

### 44.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 敏感信息模式库 | 30+ 种正则覆盖 API key/token/私钥/路径/邮箱 | **高优先级**: BlueCortexCE 的 Observation/Extraction 应实现类似脱敏 |
| 替换占位符 | `[REDACTED]` 替代原始值 | **高优先级**: BlueCortexCE 的摘要/导出功能应使用脱敏占位符 |
| 逆向环境检测 | 检测当前 env 值是否被硬编码 | **高优先级**: BlueCortexCE 的 LLM 生成应避免硬编码凭证 |
| 非破坏性清洗 | 深拷贝 + JSON.parse reviver | **高优先级**: BlueCortexCE 的脱敏应保留原始对象 |
| 泄露建议 | 每个泄露有 `suggest: 'process.env.XXX'` | **中优先级**: BlueCortexCE 应提供"应该用什么环境变量"的建议 |

---

## 45. contentHash.js — 内容寻址哈希（v0.9 新增）

**文件**: `src/gep/contentHash.js` (65 lines)

### 45.1 核心设计原则

`contentHash.js` 实现**内容寻址存储（CAS）**的核心原语：
- **规范化（Canonicalize）**：将任意 JS 对象转换为确定性的 JSON 字符串
- **哈希（Hash）**：使用 SHA-256 计算内容的指纹
- **验证（Verify）**：比较 `asset_id` 与计算出的哈希是否匹配

### 45.2 规范化 JSON（Canonical JSON）

**文件**: `contentHash.js:15-35`

```javascript
function canonicalize(obj) {
  if (obj === null || obj === undefined) return 'null';
  if (typeof obj === 'boolean') return obj ? 'true' : 'false';
  if (typeof obj === 'number') return Number.isFinite(obj) ? String(obj) : 'null';
  if (typeof obj === 'string') return JSON.stringify(obj);
  if (Array.isArray(obj)) return '[' + obj.map(canonicalize).join(',') + ']';
  if (typeof obj === 'object') {
    const keys = Object.keys(obj).sort();  // 键排序！
    const pairs = [];
    for (const k of keys) {
      pairs.push(JSON.stringify(k) + ':' + canonicalize(obj[k]));
    }
    return '{' + pairs.join(',') + '}';
  }
  return 'null';
}
```

**关键特性**：
- 对象键按字母排序（`Object.keys(obj).sort()`）
- 数组保持顺序
- `undefined` 和非有限数字 → `null`
- 字符串值用 `JSON.stringify`（确保引号转义一致）

**Evolver 为什么这样做**：JavaScript 对象的键顺序在不同引擎/版本中可能不同。规范化确保 `{a:1,b:2}` 和 `{b:2,a:1}` 产生相同的哈希。

### 45.3 资产 ID 计算（computeAssetId）

**文件**: `contentHash.js:39-52`

```javascript
function computeAssetId(obj, excludeFields) {
  if (!obj || typeof obj !== 'object') return null;
  const exclude = new Set(excludeFields || ['asset_id']);  // 默认排除 asset_id 自身
  const clean = {};
  for (const k of Object.keys(obj)) {
    if (exclude.has(k)) continue;
    clean[k] = obj[k];
  }
  const canonical = canonicalize(clean);
  const hash = crypto.createHash('sha256').update(canonical, 'utf8').digest('hex');
  return 'sha256:' + hash;
}
```

**设计意图**：
- `asset_id` 本身被排除在哈希计算之外（否则会是先有鸡还是先有蛋的问题）
- 返回格式 `sha256:<hex>` —— 明确标注哈希算法

### 45.4 防篡改验证（verifyAssetId）

**文件**: `contentHash.js:55-62`

```javascript
function verifyAssetId(obj) {
  const claimed = obj.asset_id;
  if (!claimed || typeof claimed !== 'string') return false;
  const computed = computeAssetId(obj);
  return claimed === computed;
}
```

**Evolver 为什么这样做**：如果 Capsule/Gene 的 `asset_id` 与内容不匹配，说明数据在传输/存储过程中被篡改或损坏。

### 45.5 Schema 版本管理

**文件**: `contentHash.js:8`

```javascript
const SCHEMA_VERSION = '1.6.0';
// Bump MINOR for additive fields; MAJOR for breaking changes.
```

**Evolver 为什么这样做**：Schema 版本让 Evolver 能够检测"用新版 schema 序列化的资产被旧版读取"的情况。

### 45.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 规范化 JSON | 键排序 + 字符串转义一致 | **高优先级**: BlueCortexCE 的任何"内容哈希"应使用规范化的 JSON |
| asset_id 排除 | asset_id 自身不参与哈希计算 | **高优先级**: BlueCortexCE 的 ID 验证应排除 ID 字段本身 |
| 防篡改验证 | verifyAssetId 对比 claimed vs computed | **高优先级**: BlueCortexCE 的重要记录应有完整性校验 |
| Schema 版本 | SCHEMA_VERSION = '1.6.0' | **中优先级**: BlueCortexCE 的 API 响应应包含 schema_version 字段 |

---

## 46. crypto.js — AES-256-GCM 加密（v0.9 新增）

**文件**: `src/gep/crypto.js` (89 lines)

### 46.1 设计背景

`crypto.js` 实现 **AES-256-GCM 对称加密**，用于"sealed / privacy computing blobs"——即需要在多方之间传输但不想让中间节点解密的敏感数据。

**注意**：这是一个独立的加密模块，不依赖外部密钥管理服务。密钥在本地生成（`generateKey()`），从不离开客户端。

### 46.2 AES-256-GCM 参数

**文件**: `crypto.js:5-9`

```javascript
const ALGORITHM = 'aes-256-gcm';
const IV_BYTES = 12;       // GCM 推荐 12 字节 IV
const TAG_BYTES = 16;      // GCM 认证标签 16 字节
const KEY_BYTES = 32;      // 256 位密钥
```

### 46.3 加密/解密 API

```javascript
// 加密
const { ciphertext, iv, authTag } = encrypt(plaintext, key);
// ciphertext = 密文（不含 IV 和 authTag）
// iv = 12 字节初始化向量
// authTag = 16 字节认证标签

// 解密
const plaintext = decrypt(ciphertext, key, iv, authTag);
```

### 46.4 pack/unpack — 传输格式

**文件**: `crypto.js:70-90`

```javascript
// 打包：IV(12) + authTag(16) + ciphertext(...)，适合网络传输
function pack(parts) {
  return Buffer.concat([parts.iv, parts.authTag, parts.ciphertext]);
}

// 解包：从 Buffer 中提取 IV/authTag/ciphertext
function unpack(packed) {
  const iv = packed.subarray(0, 12);
  const authTag = packed.subarray(12, 28);
  const ciphertext = packed.subarray(28);
  return { ciphertext, iv, authTag };
}
```

**Evolver 为什么这样做**：GCM 模式提供**认证加密**（Authenticated Encryption）——不仅加密内容，还通过 `authTag` 确保内容未被篡改。这比单纯的 AES-CBC 更安全。

### 46.5 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| AES-256-GCM | 认证加密（IV + authTag + ciphertext） | **中优先级**: BlueCortexCE 的敏感数据存储可考虑类似加密 |
| 本地密钥生成 | generateKey() 从不离开客户端 | **中优先级**: BlueCortexCE 的加密应避免依赖外部 KMS |
| 密钥长度验证 | `if (!key || key.length !== KEY_BYTES)` | **高优先级**: BlueCortexCE 的任何加密都应验证密钥长度 |
| Buffer 打包格式 | IV + authTag + ciphertext 拼接 | **低优先级**: BlueCortexCE 的加密数据传输可参考此格式 |

---

## 47. envFingerprint.js — 环境指纹（v0.9 新增）

**文件**: `src/gep/envFingerprint.js` (84 lines)

### 47.1 核心设计原则

`envFingerprint.js` 为每个 Evolution Event/Capsule 记录**运行环境指纹**，用于：
1. **跨环境泛化（Cross-Environment Diffusion, GDI）**：衡量某个环境成功的 Gene 在另一个环境是否也有效
2. **环境分组**：`envFingerprintKey()` 将相似环境归为同一"环境类"
3. **Bug 溯源**：失败是否由特定环境引起？

### 47.2 指纹捕获（captureEnvFingerprint）

**文件**: `envFingerprint.js:14-50`

```javascript
function captureEnvFingerprint() {
  // 优先读取 evolver 自身 package.json（而非 host project 的）
  const ownPkgPath = path.resolve(__dirname, '..', '..', 'package.json');
  const pkg = JSON.parse(fs.readFileSync(ownPkgPath, 'utf8'));
  const pkgVersion = pkg.version;

  return {
    device_id: getDeviceId(),
    node_version: process.version,
    platform: process.platform,        // darwin / linux / win32
    arch: process.arch,               // x64 / arm64
    os_release: os.release(),
    hostname: sha256(os.hostname()).slice(0, 12),  // 哈希保护主机名隐私
    evolver_version: pkgVersion,      // evolver 版本（不是 host project）
    client: pkgName || 'evolver',    // npm 包名
    cwd: sha256(process.cwd()).slice(0, 12),        // 工作目录哈希
    container: isContainer(),        // 是否在容器中运行
    captured_at: new Date().toISOString(),
    region: process.env.EVOLVER_REGION?.slice(0, 5),  // 可选区域标签
  };
}
```

**关键设计**：
- **hostname 哈希化**：不存储明文 hostname，只存哈希（privacy-preserving）
- **cwd 哈希化**：同理
- **自身 package.json**：使用 `__dirname` 向上查找，确保 npm 部署时报告的是 evolver 版本而非 host project 版本

### 47.3 环境类键（envFingerprintKey）

**文件**: `envFingerprint.js:54-63`

```javascript
function envFingerprintKey(fp) {
  const parts = [
    fp.device_id || '',
    fp.node_version || '',
    fp.platform || '',
    fp.arch || '',
    fp.hostname || '',
    fp.client || fp.evolver_version || '',
    fp.client_version || fp.evolver_version || '',
  ].join('|');
  return sha256(parts).slice(0, 16);
}
```

**Evolver 为什么这样做**：直接比较完整指纹太严格（hostname/cwd 不同就算不同环境）。用环境类键可以让"同一台机器、不同目录的两个 Evolver 节点"被识别为"相同环境类"。

### 47.4 环境类比较（isSameEnvClass）

**文件**: `envFingerprint.js:66-68`

```javascript
function isSameEnvClass(fpA, fpB) {
  return envFingerprintKey(fpA) === envFingerprintKey(fpB);
}
```

**应用场景**：
- 判断某个失败 Capsule 是否与当前节点是"同一类环境"
- 如果不同环境类，则失败可能是环境问题而非 Gene 问题

### 47.5 表观遗传与 GDI

envFingerprint 是 Evolver **表观遗传机制（Epigenetic Marks）**的基础：

```javascript
// solidify.js 中，成功的 Capsule 记录环境指纹
gene.epigenetic_marks.push({
  context: envFingerprintKey(fp),  // "darwin-arm64|node20|abc123..."
  boost: +0.05,
  created_at: new Date().toISOString(),
  reason: 'success_in_environment',
});
```

**Evolver 为什么这样做**：表观遗传标记是**环境绑定**的——通过 `envFingerprintKey` 确保只有"相同环境类"才应用该 boost。

### 47.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 环境指纹 | device_id/node_version/platform/arch/container | **高优先级**: BlueCortexCE 的 Observation 可包含 `env_fingerprint` 字段 |
| 隐私哈希 | hostname/cwd 只存哈希 | **高优先级**: BlueCortexCE 的环境数据应做哈希化处理 |
| 环境类键 | 相似环境归为一类 | **中优先级**: BlueCortexCE 可用"环境类"做检索过滤 |
| isSameEnvClass | 判断两个记录是否同环境 | **中优先级**: BlueCortexCE 的"环境相关检索"可参考此逻辑 |
| 表观遗传绑定 | epigenetic_marks 通过 envFingerprintKey 关联 | **高优先级**: BlueCortexCE 的"学习历史"应按环境分组 |

---

## 48. issueReporter.js — 自动 GitHub 问题上报（v0.9 新增）

**文件**: `src/gep/issueReporter.js` (262 lines)

### 48.1 核心设计原则

`issueReporter.js` 实现**自动 GitHub Issue 上报**——当 Evolver 持续失败（5+ 次 streak）且无法自行解决时，自动在 GitHub 上创建 Issue 请求社区帮助。

### 48.2 上报触发条件（shouldReport）

**文件**: `issueReporter.js:130-160`

```javascript
function shouldReport(signals, config) {
  // 必须有失败循环或高频失败
  const hasFailureLoop = signals.includes('failure_loop_detected');
  const hasRecurringAndHigh = signals.includes('recurring_error') && signals.includes('high_failure_ratio');
  if (!hasFailureLoop && !hasRecurringAndHigh) return false;

  // streak 必须 >= 最小阈值（默认 5）
  const streakCount = extractStreakCount(signals);
  if (streakCount > 0 && streakCount < config.minStreak) return false;

  // 冷却期检查：24 小时内同一 error_key 不重复上报
  const errorKey = computeErrorKey(signals);  // SHA-256(error_signals)
  if (state.lastReportedAt) {
    const elapsed = Date.now() - new Date(state.lastReportedAt).getTime();
    if (elapsed < config.cooldownMs) return false;
  }

  return true;
}
```

### 48.3 Error Key 计算（computeErrorKey）

**文件**: `issueReporter.js:65-75`

```javascript
function computeErrorKey(signals) {
  const relevant = signals
    .filter(s => s.startsWith('recurring_errsig') ||
                  s.startsWith('ban_gene:') ||
                  s === 'recurring_error' ||
                  s === 'failure_loop_detected' ||
                  s === 'high_failure_ratio')
    .sort()
    .join('|');
  return sha256(relevant || 'unknown').slice(0, 16);
}
```

**Evolver 为什么这样做**：用相关失败信号的 SHA-256 哈希作为 error_key，确保"相同根因的失败"不会重复上报。

### 48.4 Issue 内容构建（buildIssueBody）

**文件**: `issueReporter.js:90-145`

```javascript
function buildIssueBody(opts) {
  const fp = opts.envFingerprint;
  const signals = opts.signals;
  const recentEvents = opts.recentEvents;
  const sessionLog = opts.sessionLog;

  return [
    '## Environment',
    '- **Evolver Version:** ' + fp.evolver_version,
    '- **Node.js:** ' + fp.node_version,
    '- **Platform:** ' + fp.platform + ' ' + fp.arch,
    '- **Container:** ' + (fp.container ? 'yes' : 'no'),

    '## Failure Summary',
    '- **Consecutive failures:** ' + streakCount,
    '- **Failure signals:** ' + failureSignals,

    '## Error Signature',
    '```',
    redactString(errorSig),  // 脱敏后的错误签名
    '```',

    '## Recent Evolution Events (sanitized)',
    eventsTable,  // Markdown 表格格式

    '## Session Log Excerpt (sanitized)',
    '```',
    sanitizedLog,  // 脱敏后的会话日志（最后 2000 字符）
    '```',
  ].join('\n');
}
```

### 48.5 自动上报状态机

**文件**: `issueReporter.js:170-220`

```javascript
// issue_reporter_state.json 结构
{
  "lastReportedAt": "2026-04-16T12:00:00Z",
  "recentIssueKeys": ["a1b2c3d4e5f6", ...],  // 最多保留 20 个
  "lastIssueUrl": "https://github.com/...",
  "lastIssueNumber": 42,
}
```

**防护机制**：
- `recentIssueKeys.length > 20` → 裁剪到最新 20 个
- 冷却期内同一 error_key → 跳过
- 无 GitHub Token → 静默跳过（非致命）

### 48.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 自动 Issue 创建 | 5+ streak + 冷却 24h | **中优先级**: BlueCortexCE 可在持续检索失败时创建内部工单 |
| errorKey 哈希 | 相同根因不重复上报 | **高优先级**: BlueCortexCE 的"问题上报"应有去重机制 |
| 内容脱敏 | redactString 处理错误签名和日志 | **高优先级**: BlueCortexCE 的任何外部上报都应脱敏 |
| 非致命设计 | 无 GitHub Token → 静默跳过 | **高优先级**: BlueCortexCE 的辅助功能失败不应阻断主流程 |
| 状态持久化 | issue_reporter_state.json 控制冷却 | **中优先级**: BlueCortexCE 的限流机制应有持久化状态 |

---

## 49. validationReport.js — 标准化验证报告（v0.9 新增）

**文件**: `src/gep/validationReport.js` (55 lines)

### 49.1 核心设计原则

`validationReport.js` 定义 **ValidationReport 标准 Schema**——一种机器可读、自我包含、可互操作的验证结果格式，可被外部 Hub 或 Judge 消费。

### 49.2 报告结构（buildValidationReport）

**文件**: `validationReport.js:20-45`

```javascript
function buildValidationReport({ geneId, commands, results, envFp, startedAt, finishedAt }) {
  return {
    type: 'ValidationReport',
    schema_version: SCHEMA_VERSION,
    id: 'vr_' + Date.now(),
    gene_id: geneId || null,
    env_fingerprint: envFp,
    env_fingerprint_key: envFingerprintKey(envFp),  // 用于环境分组
    commands: commands.map((cmd, i) => ({
      command: String(cmd || ''),
      ok: !!results[i]?.ok,
      stdout: String(results[i]?.out || results[i]?.stdout || '').slice(0, 4000),
      stderr: String(results[i]?.err || results[i]?.stderr || '').slice(0, 4000),
    })),
    overall_ok: results.every(r => r.ok),
    duration_ms: finishedAt - startedAt,
    created_at: new Date().toISOString(),
  };
}
```

**关键字段**：
- `env_fingerprint_key`：用于将多个 ValidationReport 按环境分组
- `stdout/stderr` 截断到 4000 字符：防止日志过大
- `overall_ok`：所有命令都通过才算 overall success

### 49.3 Schema 验证（isValidValidationReport）

**文件**: `validationReport.js:48-58`

```javascript
function isValidValidationReport(obj) {
  if (!obj || typeof obj !== 'object') return false;
  if (obj.type !== 'ValidationReport') return false;
  if (!obj.id || typeof obj.id !== 'string') return false;
  if (!Array.isArray(obj.commands)) return false;
  if (typeof obj.overall_ok !== 'boolean') return false;
  return true;
}
```

### 49.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 标准化 Schema | ValidationReport type + schema_version | **高优先级**: BlueCortexCE 的所有 API 响应应有 type + schema_version |
| 环境指纹键 | env_fingerprint_key 用于分组 | **中优先级**: BlueCortexCE 的验证结果可按环境分组查询 |
| 输出截断 | stdout/stderr 限制 4000 chars | **高优先级**: BlueCortexCE 的日志字段应有长度上限 |
| 自我包含 | env_fingerprint 内嵌在报告中 | **高优先级**: BlueCortexCE 的报告应内嵌完整上下文 |

---

## 50. analyzer.js — 自省分析器（v0.9 新增）

**文件**: `src/gep/analyzer.js` (35 lines)

### 50.1 设计定位

`analyzer.js` 是 Evolver 最简单的模块之一，实现**自省（Self-Correction）分析器**——从 MEMORY.md 中提取失败记录并建议更好的 future mutations。

### 50.2 失败提取逻辑

**文件**: `analyzer.js:12-30`

```javascript
function analyzeFailures() {
  const memoryPath = path.join(process.cwd(), 'MEMORY.md');
  if (!fs.existsSync(memoryPath)) return { status: 'skipped', reason: 'no_memory' };

  const content = fs.readFileSync(memoryPath, 'utf8');
  const failureRegex = /\|\s*\*\*F\d+\*\*\s*\|\s*Fix\s*\|\s*(.*?)\s*\|\s*\*\*(.*?)\*\*\((.*?)\)\s*\|/g;

  const failures = [];
  let match;
  while ((match = failureRegex.exec(content)) !== null) {
    failures.push({
      summary: match[1].trim(),
      detail: match[2].trim()
    });
  }

  return {
    status: 'success',
    count: failures.length,
    failures: failures.slice(0, 3)  // 只返回最近 3 个
  };
}
```

**MEMORY.md 失败记录格式**（Evolver 使用的格式）：
```
| **F1** | Fix | Summary | Detail (reason) |
```

### 50.3 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 自省失败分析 | 从 MEMORY.md 提取 F\d+ 失败记录 | **高优先级**: BlueCortexCE 可实现类似的自省分析（从 Observation 中提取失败模式） |
| 失败数量限制 | 只返回最多 3 个 | **高优先级**: BlueCortexCE 的分析结果应有数量上限防止溢出 |
| 非致命设计 | MEMORY.md 不存在 → skipped | **高优先级**: BlueCortexCE 的任何文件读取都应是非致命的 |

---

## 51. 整体架构补充：Evolver 的安全与隐私体系（v0.9 新增）

### 51.1 安全分层总览

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: 输入安全 (Input Safety)                            │
│  - policyCheck.js: 验证命令白名单（isValidationCommandAllowed）│
│  - policyCheck.js: 伦理违规检测（ethicsBlockPatterns）        │
│  - policyCheck.js: ReDoS 防护（MAX_REGEX_PATTERN_LEN）       │
│  - policyCheck.js: 破坏性变更检测（detectDestructiveChanges） │
├─────────────────────────────────────────────────────────────┤
│  Layer 2: 隐私保护 (Privacy)                                │
│  - sanitize.js: 敏感信息脱敏（REDACT_PATTERNS）               │
│  - sanitize.js: 逆向环境变量泄露检测（detectEnvValueLeaks）   │
│  - envFingerprint.js: hostname/cwd 哈希化                    │
│  - crypto.js: AES-256-GCM 认证加密                          │
│  - privacyClient.js: 密封执行（加密 blob + 本地密钥管理）    │
├─────────────────────────────────────────────────────────────┤
│  Layer 3: 数据完整性 (Integrity)                             │
│  - contentHash.js: 规范化 JSON + SHA-256 哈希                 │
│  - contentHash.js: 防篡改验证（verifyAssetId）                │
│  - contentHash.js: Schema 版本管理                          │
├─────────────────────────────────────────────────────────────┤
│  Layer 4: 监控与上报 (Monitoring)                            │
│  - issueReporter.js: 自动 GitHub Issue 上报                  │
│  - validationReport.js: 标准化验证报告                       │
│  - analyzer.js: 自省失败分析                                │
└─────────────────────────────────────────────────────────────┘
```

### 51.2 BlueCortexCE 安全现状对照

| Evolver 层 | BlueCortexCE 等价 | 差距 |
|-----------|------------------|------|
| isValidationCommandAllowed | 无（不执行验证命令） | **安全差距**：如 BlueCortexCE 未来支持自定义验证，需实现类似白名单 |
| ethicsBlockPatterns | 无 | **缺失**：BlueCortexCE 的 Observation/Extraction 无伦理检测 |
| sanitize.js | 无 | **缺失**：BlueCortexCE 的摘要导出无脱敏 |
| detectEnvValueLeaks | 无 | **缺失**：BlueCortexCE 可能无意中硬编码凭证 |
| hostname/cwd 哈希 | 无 | **中差距**：BlueCortexCE 存储环境信息时未哈希化 |
| AES-256-GCM | 无 | **低差距**：BlueCortexCE 无隐私计算需求 |
| privacyClient.js 密封执行 | 无 | **低差距**：BlueCortexCE 无 Hub 密封执行场景 |
| computeAssetId/verifyAssetId | 无 | **缺失**：BlueCortexCE 的记录无防篡改机制 |
| env_fingerprint | 无 | **缺失**：BlueCortexCE 的 Observation 不记录环境上下文 |
| issueReporter | 无 | **中差距**：BlueCortexCE 无自动问题上报机制 |

### 51.3 高优先级安全改进建议

| 改进 | 依据 | 优先级 |
|------|------|--------|
| Observation 脱敏 | sanitize.js 的 30+ 敏感模式库 | **高** |
| 防篡改验证 | contentHash.js 的 verifyAssetId | **高** |
| Schema 版本控制 | SCHEMA_VERSION + 规范化 JSON | **高** |
| 环境指纹 | envFingerprint.js 嵌入 Observation | **中** |
| 伦理违规检测 | ethicsBlockPatterns（prompt 层面） | **中** |

---

## 53. hubSearch.js — 联邦知识市场与两阶段搜索（v1.0 新增）

**文件**: `src/gep/hubSearch.js` (407 lines)

### 53.1 核心设计原则：Search-First Evolution

Evolver 的 `hubSearch.js` 实现**搜索优先进化**模式——在本地推理之前，先查询 Hub 上的共享知识库（基因/胶囊市场）：

```
信号提取 → hubSearch(信号) → 命中: 复用 | 未命中: 本地进化
```

这与 Hermes/BlueCortexCE 的设计理念截然不同：
- **Evolver**：假设"别人可能已经解决了同样的问题"，先搜索，有就复用
- **Hermes/BlueCortexCE**：纯粹本地记忆，无跨实例知识共享概念

### 53.2 两阶段搜索（Search-Then-Fetch）

**文件**: `hubSearch.js:150-250`

为了最小化 Hub API 成本，hubSearch 实现**两阶段搜索**：

```
Phase 1: POST /a2a/fetch { signals, search_only: true }
  → 免费！只返回候选资产的元数据（id, confidence, reputation, status）
  → 结果按 signal fingerprint 缓存（5 分钟 TTL，200 条上限）

Phase 2: POST /a2a/fetch { asset_ids: [best_match] }
  → 付费！但只取最优匹配的完整 payload
  → payload 按 asset_id 缓存（LRU，100 条上限）
```

**为什么这样设计**：
- 完整 payload（如 Capsule 的 strategy 代码）比 metadata 大得多
- 只对最有可能被复用的资产付费，避免浪费
- metadata 缓存减少重复搜索的网络开销

**Deadline 控制**：
```javascript
// hubSearch.js:167-180
const deadline = Date.now() + timeoutMs;  // 8000ms 默认

// Phase 2 只在剩余时间 > 500ms 时执行
const remaining = deadline - Date.now();
if (remaining > MIN_PHASE2_MS) {
  // 执行 Phase 2...
}
```

### 53.3 并行语义搜索增强

**文件**: `hubSearch.js:85-120`

Phase 1 期间，hubSearch 同时发起**语义搜索**（`/a2a/assets/semantic-search`）作为补充：

```javascript
// hubSearch.js:140-150
var fetchPromise = fetchPhase1(searchMsg, endpoint, headers, deadline);
var semanticPromise = fetchSemanticResults(hubUrl, headers, signalList, SEMANTIC_TIMEOUT_MS);

var settled = await Promise.allSettled([fetchPromise, semanticPromise]);
var fetchResult = settled[0].status === 'fulfilled' ? settled[0].value : { ok: false, results: [] };
var semanticResults = settled[1].status === 'fulfilled' ? settled[1].value : [];
```

**语义搜索的特点**：
- 只搜索非错误信号（过滤 `errsig:` 前缀）
- 提取信号中的语义部分（如 `capability_gap:web_crawl` → `web_crawl`）
- 最多 12 个信号词
- 3 秒超时，独立失败不影响主搜索

### 53.4 资产评分算法

**文件**: `hubSearch.js:125-145`

```javascript
function scoreHubResult(asset) {
  const confidence = Number(asset.confidence) || 0;
  const streak = Math.min(Math.max(Number(asset.success_streak) || 0, 1), MAX_STREAK_CAP);
  const repRaw = Number(asset.reputation_score);
  const reputation = Number.isFinite(repRaw) ? repRaw : 50;
  var base = confidence * streak * (reputation / 100);
  var sim = Number(asset._semantic_similarity) || 0;
  if (sim > 0) base += sim * SEMANTIC_SIMILARITY_BONUS;  // +0.3 bonus
  return base;
}
```

**评分公式**：`confidence × min(streak, 5) × (reputation / 100) + semantic_similarity_bonus`

| 因子 | 说明 |
|------|------|
| `confidence` | Hub 记录的资产置信度（0-1） |
| `streak` | 连续成功次数（上限 5，防止无限膨胀） |
| `reputation` | Hub 声誉分（0-100） |
| `semantic_similarity` | 语义相似度匹配时 +0.3 加权 |

### 53.5 命中阈值与模式选择

**文件**: `hubSearch.js:160-175`

```javascript
const threshold = (opts && Number.isFinite(opts.threshold)) ? opts.threshold : getMinReuseScore();
// DEFAULT_MIN_REUSE_SCORE = 0.72

const pick = pickBestMatch(results, threshold);
if (!pick) return { hit: false, reason: 'below_threshold', candidates: results.length };

// reuse mode: 'reference' (inject as hint) | 'direct' (skip local reasoning)
return { hit: true, match: best, score: bestScore, mode: getReuseMode() };
```

**两种复用模式**：
- `reference`：将 Hub 资产的摘要注入本地 prompt 作为"强烈提示"，不跳过本地推理
- `direct`：完全跳过本地推理，直接使用 Hub 资产的 strategy

### 53.6 多层缓存架构

**文件**: `hubSearch.js:30-70`

```
_searchCache (Map)
  key:   signals.sort().join('|')
  value: { ts, results[] }
  TTL:   5 分钟
  上限:  200 条

_payloadCache (Map)
  key:   asset_id
  value: full payload object
  TTL:   永久（bounded LRU）
  上限:  100 条
```

**缓存键设计**：信号排序后用 `|` 连接，保证 `[a, b]` 和 `[b, a]` 产生相同的缓存键。

### 53.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| Search-First 模式 | 先搜索共享知识，未命中才本地推理 | **高优先级**: BlueCortexCE 的 `/api/context/generate` 可优先查跨实例知识 | 高 |
| 两阶段搜索 | 免费 metadata → 付费完整 payload | **高优先级**: BlueCortexCE 的检索可分离"摘要"和"完整记录" | 高 |
| 多层缓存 | search cache (TTL) + payload cache (LRU) | **高优先级**: BlueCortexCE 的向量检索结果可加 LRU 缓存 | 高 |
| 并行语义搜索 | Phase 1 期间并行语义搜索 | **中优先级**: BlueCortexCE 的搜索可同时做关键词+向量混合 | 中 |
| 评分公式 | confidence × streak × reputation | **中优先级**: BlueCortexCE 的检索排序可引入"使用次数/成功率" | 中 |
| 复用模式 | reference vs direct | **低优先级**: BlueCortexCE 的 SDK 消费方可选择注入深度 | 低 |

---

## 54. hubReview.js — 使用验证型评价系统（v1.0 新增）

**文件**: `src/gep/hubReview.js` (206 lines)

### 54.1 核心设计：使用后评价

当 Evolver 复用了一个 Hub 资产（`source_type = 'reused'` 或 `'reference'`），solidify 完成后会自动向 Hub 提交**使用评价**：

```
solidify 成功 → submitHubReview(资产ID, outcome, signals) → Hub 更新声誉分
```

**评价的价值**：
- 资产在 Hub 上的 `reputation_score` 取决于使用者的真实反馈
- 评分驱动后续复用决策（`scoreHubResult` 中的 reputation 因子）
- 形成正反馈循环：好资产获得高声誉 → 更多人复用 → 进一步验证

### 54.2 评分推导算法

**文件**: `hubReview.js:65-78`

```javascript
function _deriveRating(outcome, constraintCheck) {
  if (outcome && outcome.status === 'success') {
    const score = Number(outcome.score) || 0;
    return score >= 0.85 ? 5 : 4;  // 高分 success → 4-5 星
  }
  const hasViolation = constraintCheck && Array.isArray(constraintCheck.violations)
    && constraintCheck.violations.length > 0;
  return hasViolation ? 1 : 2;       // 失败 + 约束违反 → 1 星
}
```

| 情况 | 评分 |
|------|------|
| success + score ≥ 0.85 | ⭐⭐⭐⭐⭐ (5) |
| success + score < 0.85 | ⭐⭐⭐⭐ (4) |
| failed + 无约束违反 | ⭐⭐ (2) |
| failed + 有约束违反 | ⭐ (1) |

### 54.3 去重机制：本地历史文件

**文件**: `hubReview.js:20-50`

```javascript
const REVIEW_HISTORY_FILE = path.join(getEvolutionDir(), 'hub_review_history.json');
const REVIEW_HISTORY_MAX_ENTRIES = 500;

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

**防重机制**：
- 每个 assetId 只评价一次
- 历史文件上限 500 条（超出的最旧记录被清理）
- 即使 Hub 返回 `already_reviewed` 错误，也本地标记避免重复提交

### 54.4 评价内容构建

**文件**: `hubReview.js:80-100`

```javascript
function _buildReviewContent({ outcome, gene, signals, blast, sourceType }) {
  // 构建最多 2000 字符的评价内容
  parts.push('Outcome: ' + status + ' (score: ' + score + ')');
  parts.push('Reuse mode: ' + (sourceType || 'unknown'));
  if (gene && gene.id) parts.push('Gene: ' + gene.id + ' (' + gene.category + ')');
  if (signals) parts.push('Signals: ' + signals.slice(0, 6).join(', '));
  if (blast) parts.push('Blast radius: ' + blast.files + ' file(s), ' + blast.lines + ' line(s)');
  parts.push(status === 'success' ? '成功固化和应用。' : '未产生成功的进化循环。');
}
```

### 54.5 非阻塞设计

**文件**: `hubReview.js:155-180`

```javascript
try {
  var res = await fetch(endpoint, { method: 'POST', ... });
  if (res.ok) {
    _markReviewed(reusedAssetId, rating, true);
    return { submitted: true, rating, asset_id: reusedAssetId };
  }
  // Hub 返回 already_reviewed → 本地标记
  if (errCode === 'already_reviewed') {
    _markReviewed(reusedAssetId, rating, false);
  }
  return { submitted: false, reason: errCode };
} catch (err) {
  var reason = err.name === 'AbortError' ? 'timeout' : 'fetch_error';
  return { submitted: false, reason, error: err.message };
}
```

**关键**：评价提交**从不影响** solidify 的结果。无论提交成功/失败/超时，solidify 都继续。

### 54.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 使用验证型评价 | 复用资产后自动提交 outcome-based 评价 | **高优先级**: BlueCortexCE 的 `/api/context/generate` 可在消费后提交质量反馈 | 高 |
| 评分推导 | outcome status + constraint violations → 1-5 星 | **高优先级**: BlueCortexCE 的检索结果可实现类似的"使用质量分" | 高 |
| 去重机制 | 本地 JSON 历史 + 500 条上限 | **高优先级**: BlueCortexCE 的反馈系统需要防重复提交 | 高 |
| 非阻塞设计 | review 失败不影响主流程 | **高优先级**: BlueCortexCE 的反馈提交必须是非阻塞的 | 高 |
| 声誉分驱动 | reputation_score 影响复用评分 | **中优先级**: BlueCortexCE 的检索排序可引入"历史质量分" | 中 |

---

## 55. executionTrace.js — 隐私保护的执行遥测（v1.0 新增）

**文件**: `src/gep/executionTrace.js` (201 lines)

### 55.1 设计背景

`solidify.js` 在每次进化固化和验证后，构建一个**结构化的执行轨迹**（ExecutionTrace），可以选择性地上传给 Hub 的 `EvolutionEvent`：

```javascript
// executionTrace.js:90
const trace = buildExecutionTrace({
  gene, mutation, signals, blast, constraintCheck,
  validation, canary, outcomeStatus, startedAt
});
```

**核心设计原则**：trace 只包含**统计指标和脱敏元数据**，原始代码内容**永不离开本地**。

### 55.2 Trace 级别控制

**文件**: `executionTrace.js:8-20`

```javascript
const TRACE_LEVELS = { none: 0, minimal: 1, standard: 2 };

function getTraceLevel() {
  const raw = String(process.env.EVOLVER_TRACE_LEVEL || 'minimal').toLowerCase().trim();
  return TRACE_LEVELS[raw] != null ? raw : 'minimal';
}
```

| 级别 | 内容 |
|------|------|
| `none` | 完全不生成 trace |
| `minimal` | 仅核心指标（文件数、行数变化、验证结果） |
| `standard` | 丰富上下文（文件类型统计、验证命令、脱敏后的错误签名） |

### 55.3 脱敏规则体系

**文件**: `executionTrace.js:18-60`

#### 路径脱敏（`desensitizeFilePath`）

```javascript
// src/utils/retry.js → retry.js
function desensitizeFilePath(filePath) {
  const ext = path.extname(filePath);
  const base = path.basename(filePath);
  return base || ext || 'unknown';
}
```

**Evolver 为什么这样做**：保留文件类型信息（如 `.ts`, `.py`）对 Hub 的统计有价值，但原始路径可能泄露项目结构。

#### 错误签名提取（`extractErrorSignature`）

```javascript
function extractErrorSignature(errorText) {
  // TypeError: x is not a function → TypeError
  const jsError = text.match(/^((?:[A-Z][a-zA-Z]*)?Error)\b/);
  if (jsError) return jsError[1];

  // ECONNRESET, ENOENT, EPERM → E[A-Z]{2,}
  const errno = text.match(/\b(E[A-Z]{2,})\b/);
  if (errno) return errno[1];

  // HTTP 4xx/5xx → HTTP_400
  const http = text.match(/\b((?:4|5)\d{2})\b/);
  if (http) return 'HTTP_' + http[1];
}
```

**Evolver 为什么这样做**：原始错误消息可能包含敏感上下文（如用户 ID、文件路径），但错误类型签名是通用的，可以跨实例共享以改进错误处理。

### 55.4 爆炸半径分级

**文件**: `executionTrace.js:68-75`

```javascript
function classifyBlastLevel(blast) {
  if (!blast) return 'unknown';
  const files = Number(blast.files) || 0;
  const lines = Number(blast.lines) || 0;
  if (files <= 3 && lines <= 50) return 'low';
  if (files <= 10 && lines <= 200) return 'medium';
  return 'high';
}
```

### 55.5 工具链推断

**文件**: `executionTrace.js:55-65`

```javascript
function inferToolChain(validationResults, blast) {
  const tools = new Set();
  if (blast && blast.files > 0) tools.add('file_edit');
  for (const r of validationResults) {
    if (/jest|mocha/.test(r.cmd)) tools.add('test_run');
    else if (/eslint|lint/.test(r.cmd)) tools.add('lint_check');
    else if (/validate|check/.test(r.cmd)) tools.add('validation_run');
  }
  return Array.from(tools);
}
```

### 55.6 完整 Trace 结构（standard 级别）

```javascript
{
  gene_id: 'gene_error_handling_v2',
  mutation_category: 'error_repair',
  signals_matched: ['log_error', 'errsig_norm:...', 'capability_gap'],
  outcome: 'success',
  files_changed_count: 3,
  lines_added: 42, lines_removed: 18,
  validation_result: 'pass',
  blast_radius: 'low',
  // standard 级别额外：
  file_types: { '.ts': 2, '.json': 1 },
  validation_commands: ['npm test -- --coverage'],
  error_signatures: ['TypeError', 'ReferenceError'],
  tool_chain: ['file_edit', 'test_run'],
  validation_duration_ms: 2340,
  canary_ok: true,
  created_at: '2026-04-17T...',
}
```

### 55.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 隐私保护 trace | 代码永不离开，仅统计指标 + 脱敏元数据 | **高优先级**: BlueCortexCE 的任何"遥测"都应实现类似脱敏 | 高 |
| 路径脱敏 | basename + extension | **高优先级**: BlueCortexCE 记录"相关文件"时只存扩展名+文件名 | 高 |
| 错误签名归一化 | TypeError/ENOENT/HTTP_500 | **高优先级**: BlueCortexCE 的错误记录应只存类型，不存完整消息 | 高 |
| Trace 级别 | none/minimal/standard 可配置 | **中优先级**: BlueCortexCE 的遥测应有可配置的详细度 | 中 |
| 工具链推断 | 从验证命令反推工具 | **中优先级**: BlueCortexCE 可从 context 推断使用了哪些工具 | 中 |
| Blast 分级 | 低/中/高 阈值 | **低优先级**: BlueCortexCE 的 mutation 影响评估可参考 | 低 |

---

## 56. assetCallLog.js — 资产交互的 append-only 审计（v1.0 新增）

**文件**: `src/gep/assetCallLog.js` (130 lines)

### 56.1 设计原则

`assetCallLog.js` 实现** append-only JSONL** 审计日志，记录每次 Hub 资产交互：

```
{signal → hubSearch} → logAssetCall({ action: 'hub_search_hit', asset_id, score, mode })
{signal → hubSearch miss} → logAssetCall({ action: 'hub_search_miss', reason })
{asset 复用成功} → logAssetCall({ action: 'hub_review_submitted', rating })
```

### 56.2 记录格式

**文件**: `assetCallLog.js:25-50`

```javascript
function logAssetCall(entry) {
  const record = {
    timestamp: new Date().toISOString(),
    ...entry,  // run_id, action, asset_id, asset_type, score, mode, signals, reason, extra
  };
  fs.appendFileSync(logPath, JSON.stringify(record) + '\n', 'utf8');
}
```

**action 类型**：
| action | 说明 |
|--------|------|
| `hub_search_hit` | Hub 搜索命中 |
| `hub_search_miss` | Hub 搜索未命中 |
| `asset_reuse` | 资产被复用 |
| `asset_reference` | 资产被引用（reference 模式） |
| `hub_review_submitted` | 评价已提交 |
| `hub_review_rejected` | 评价被 Hub 拒绝 |
| `hub_review_failed` | 评价提交失败 |

### 56.3 非阻塞设计

**文件**: `assetCallLog.js:28-35`

```javascript
function logAssetCall(entry) {
  try {
    const logPath = getLogPath();
    ensureDir(logPath);
    fs.appendFileSync(logPath, JSON.stringify(record) + '\n', 'utf8');
  } catch (e) {
    // Non-fatal: never block evolution for logging failure
  }
}
```

**Evolver 为什么这样做**：appendFileSync 是原子的（对于单条记录），且被 try-catch 包裹，确保即使磁盘满/权限问题也不会阻塞主流程。

### 56.4 读取与聚合

**文件**: `assetCallLog.js:60-130`

```javascript
function readCallLog({ run_id, action, last, since }) {
  // 支持多维度过滤
  if (o.run_id) entries = entries.filter(e => e.run_id === o.run_id);
  if (o.action) entries = entries.filter(e => e.action === o.action);
  if (o.last && Number.isFinite(o.last)) entries = entries.slice(-o.last);
}

function summarizeCallLog(opts) {
  return {
    total_entries: entries.length,
    unique_assets: assetsSeen.size,
    unique_runs: runsSeen.size,
    by_action: actionCounts,  // { hub_search_hit: 5, hub_search_miss: 12, ... }
    entries,
  };
}
```

### 56.5 与 narrativeMemory.js 的关系

`assetCallLog` 和 `narrativeMemory` 都是 append-only 日志，但服务不同目的：

| 维度 | assetCallLog | narrativeMemory |
|------|-------------|----------------|
| 格式 | JSONL（机器可读） | Markdown（人类可读） |
| 内容 | Hub 资产交互记录 | 进化决策叙事 |
| 用途 | 审计、分析、CLI 统计 | 决策上下文保留 |
| 大小 | 按行增长，无限 | 30 条 / 12000 chars 上限 |

### 56.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| Append-only JSONL | 资产交互日志，不修改只追加 | **高优先级**: BlueCortexCE 的 API 调用日志可采用 append-only JSONL | 高 |
| 多维度过滤 | 支持 run_id/action/since/last 过滤 | **高优先级**: BlueCortexCE 的审计日志应有灵活查询能力 | 高 |
| 非阻塞写入 | try-catch + 静默失败 | **高优先级**: BlueCortexCE 的日志写入必须非阻塞主流程 | 高 |
| 聚合摘要 | by_action 计数 + unique 资产/运行数 | **中优先级**: BlueCortexCE 的日志系统应支持聚合统计 | 中 |
| 双重日志系统 | JSONL (机器) + Markdown (人类) | **中优先级**: BlueCortexCE 既有机器可读日志，也有可读的 summary | 中 |

---

## 57. directoryClient.js — 节点目录与能力发现（v1.0 新增）

**文件**: `src/gep/directoryClient.js` (110 lines)

### 57.1 定位：Hub 上的节点目录

`directoryClient.js` 是 Hub **Agent Directory** API 的客户端，提供：
1. **语义搜索**：`searchByQuery("machine learning")` → 返回相关节点
2. **信号搜索**：`searchBySignals(["ml", "nlp"])` → 按技能标签搜索
3. **节点画像**：`getAgentProfile(nodeId)` → 获取特定节点的详细信息
4. **任务匹配**：`discoverForTask(task)` → 为任务发现合适的节点

### 57.2 三种搜索接口

**文件**: `directoryClient.js:15-75`

```javascript
// 语义搜索：自然语言查询
async function searchByQuery(query, { limit }) {
  const url = `${HUB_URL}/a2a/directory/search?q=${encodeURIComponent(query)}`;
  const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
  return data.results || data;
}

// 信号搜索：关键词数组
async function searchBySignals(signals, { limit }) {
  const params = new URLSearchParams({ signals: signals.join(',') });
  const url = `${HUB_URL}/a2a/directory/search?${params}`;
  // ...
}

// 任务驱动发现：组合信号 + 标题
async function discoverForTask(task, opts) {
  if (task.title) return searchByQuery(task.title, opts);
  if (task.signals) return searchBySignals(task.signals.split(','), opts);
}
```

### 57.3 节点画像结构

**文件**: `directoryClient.js:60-75`

```javascript
async function getAgentProfile(nodeId) {
  // 返回：
  {
    nodeId: string,
    domains: string[],           // 领域：["ml", "nlp", "code"]
    modelType: string,           // 模型类型
    reputation: number,          // 声誉分
    completedTasks: number,       // 完成的任务数
    currentLoad: number,         // 当前负载（0-1）
    online: boolean               // 是否在线
  }
}
```

### 57.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 节点目录服务 | Hub 上的 Agent 发现机制 | **高优先级**: BlueCortexCE 的多实例部署需要"节点发现"能力 | 高 |
| 能力标签搜索 | signals 驱动节点匹配 | **中优先级**: BlueCortexCE 的 SDK 可按能力标签路由请求 | 中 |
| 节点画像 | reputation + load + completedTasks | **中优先级**: BlueCortexCE 的负载均衡可参考节点负载 | 中 |
| 语义搜索 | 自然语言发现节点 | **低优先级**: BlueCortexCE 的管理面板可支持语义搜索节点 | 低 |

---

## 58. deviceId.js — 稳定节点身份与优先级指纹链（v1.0 新增）

**文件**: `src/gep/deviceId.js` (209 lines)

### 58.1 设计背景

每个 Evolver 节点需要一个**稳定、唯一、持久化**的设备 ID，用于：
1. **Hub 通信身份**：在 A2A 协议中标识发送者
2. **环境分组**：`envFingerprintKey()` 将相似环境归组
3. **跨环境泛化（GDI）**：判断某个 Gene 在环境 A 成功是否能在环境 B 复制

### 58.2 优先级指纹链（Priority Chain）

**文件**: `deviceId.js:100-145`

```javascript
function getDeviceId() {
  // 1. Env var override（容器化环境推荐）
  if (process.env.EVOMAP_DEVICE_ID && DEVICE_ID_RE.test(envId)) {
    return _cachedDeviceId = envId;
  }
  // 2. 本地文件（上次运行已生成）
  const persisted = loadPersistedDeviceId();
  if (persisted) return _cachedDeviceId = persisted;
  // 3. 从硬件/容器元数据生成
  const generated = generateDeviceId();
  persistDeviceId(generated);  // 立即持久化
  return _cachedDeviceId = generated;
}
```

**Evolver 为什么这样做**：没有单一来源能覆盖所有场景（裸机/VM/容器/Serverless），因此用优先级链让每种环境都能获得稳定 ID。

### 58.3 生成策略

**文件**: `deviceId.js:45-90`

```javascript
function generateDeviceId() {
  // 优先级：machine-id > container-id > MAC > random
  const machineId = readMachineId();       // Linux: /etc/machine-id, macOS: IOPlatformUUID
  if (machineId) return sha256('evomap:' + machineId).slice(0, 32);

  const containerId = readContainerId();  // Docker: /proc/self/cgroup
  if (containerId) return sha256('evomap:container:' + containerId).slice(0, 32);

  const macs = getMacAddresses();         // 网络接口 MAC 地址
  if (macs.length > 0) return sha256('evomap:' + hostname + '|' + macs.join(',')).slice(0, 32);

  return crypto.randomBytes(16).toString('hex');  // 兜底随机
}
```

### 58.4 容器检测

**文件**: `deviceId.js:18-42`

```javascript
function isContainer() {
  if (fs.existsSync('/.dockerenv')) return true;
  const cgroup = fs.readFileSync('/proc/1/cgroup', 'utf8');
  if (/docker|kubepods|containerd|cri-o|lxc|ecs/i.test(cgroup)) return true;
  if (fs.existsSync('/run/.containerenv')) return true;
  return false;
}
```

**容器环境下的特殊处理**：
- `~/.evomap/device_id` 可能挂载为临时文件系统，重启丢失
- 同时尝试项目本地文件 `<project>/.evomap_device_id`
- 如果 auto-generated 且运行在容器中，打印警告建议设置 `EVOMAP_DEVICE_ID`

### 58.5 持久化策略

**文件**: `deviceId.js:95-110`

```javascript
function persistDeviceId(id) {
  // 优先 ~/.evomap/device_id
  try {
    fs.mkdirSync(DEVICE_ID_DIR, { recursive: true, mode: 0o700 });  // 仅所有者可读写
    fs.writeFileSync(DEVICE_ID_FILE, id, { encoding: 'utf8', mode: 0o600 });  // 仅所有者可读写
    return;
  } catch {}

  // 容器 volume 挂载路径（非临时文件系统）
  try {
    fs.writeFileSync(LOCAL_DEVICE_ID_FILE, id, { encoding: 'utf8', mode: 0o600 });
    return;
  } catch {}

  console.error('[evolver] WARN: failed to persist device_id... Set EVOMAP_DEVICE_ID env var.');
}
```

**安全设计**：`0o700` 目录权限 + `0o600` 文件权限，确保 device_id 不会被其他用户读取。

### 58.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 优先级指纹链 | machine-id → container-id → MAC → random | **高优先级**: BlueCortexCE 的节点应实现类似的稳定 ID 生成 | 高 |
| Env var 覆盖 | EVOMAP_DEVICE_ID 显式覆盖 | **高优先级**: BlueCortexCE 应支持环境变量显式设置节点 ID | 高 |
| 容器自适应 | 检测 /.dockerenv, cgroup, .containerenv | **高优先级**: BlueCortexCE 的 Docker 部署需要容器感知 | 高 |
| 持久化路径双备 | ~/.evomap + 项目本地 | **中优先级**: BlueCortexCE 应在容器和非容器环境都有持久化方案 | 中 |
| 权限安全 | 0o700 / 0o600 限制 device_id 文件访问 | **高优先级**: BlueCortexCE 的敏感标识文件应有权限保护 | 高 |
| 跨环境 GDI | 环境分组判断 Gene 能否跨环境复制 | **低优先级**: BlueCortexCE 的检索可考虑"环境相似度" | 低 |

---

## 60. a2aProtocol.js — Agent-to-Agent 联邦通信协议（v1.1 新增）

**文件**: `src/gep/a2aProtocol.js` (1221 lines)

### 60.1 核心设计原则

`a2aProtocol.js` 是 Evolver 的 **联邦网络层**——它定义了节点（Evolver 实例）如何与 Hub（中央协调服务）通信，交换基因（Gene）和胶囊（Capsule）资产。

**与 BlueCortexCE 的本质差异**：
- Evolver 有 Hub 作为中央协调者，支持**跨实例知识共享**
- BlueCortexCE 是纯旁路型，**没有 Hub 生态**
- 但 a2aProtocol.js 的**基础设施设计**（身份、签名、传输抽象、心跳）在任何多节点系统中都有参考价值

### 60.2 协议消息类型体系

**文件**: `a2aProtocol.js:1-15`

```javascript
const VALID_MESSAGE_TYPES = ['hello', 'publish', 'fetch', 'report', 'decision', 'revoke'];
```

| 消息类型 | 方向 | 用途 |
|----------|------|------|
| `hello` | 节点 → Hub | 注册节点身份，获取 node_secret |
| `publish` | 节点 → Hub | 发布资产（Gene/Capsule）到 Hub 市场 |
| `fetch` | 节点 → Hub | 按 ID/信号/内容哈希请求资产 |
| `report` | 节点 → Hub | 发送 ValidationReport |
| `decision` | Hub → 节点 | 接受/拒绝/隔离某个资产 |
| `revoke` | 节点 → Hub | 撤回已发布的资产 |

### 60.3 节点身份与 HMAC 签名

**文件**: `a2aProtocol.js:65-85` (`getNodeId`)

```javascript
function getNodeId() {
  // 1. 环境变量（容器化环境推荐）
  if (process.env.A2A_NODE_ID) return _cachedNodeId = envId;

  // 2. 本地持久化文件（~/.evomap/node_id）
  const persisted = _loadPersistedNodeId();
  if (persisted) return _cachedNodeId = persisted;

  // 3. 从 deviceId + agentName + cwd 计算
  const raw = deviceId + '|' + agentName + '|' + process.cwd();
  const computed = 'node_' + sha256(raw).slice(0, 12);
  _persistNodeId(computed);
  return _cachedNodeId = computed;
}
```

**Evolver 为什么这样做**：设备指纹（machine-id/container-id/MAC）提供了稳定的身份基础，`cwd` 哈希让同一设备不同目录的 Agent 有不同 ID（多租户隔离）。

**HMAC 签名发布**（`buildPublish`）：

```javascript
function buildPublish(opts) {
  const assetIdVal = asset.asset_id || computeAssetId(asset);
  const nodeSecret = getHubNodeSecret();
  const signature = crypto.createHmac('sha256', nodeSecret)
    .update(assetIdVal)
    .digest('hex');
  // 签名内容 = asset_id（不是完整 payload）
  // 这样 Hub 可以验证"发布者确实拥有这个 asset_id"
}
```

**Evolver 为什么这样做**：使用 `asset_id`（内容哈希）而非完整 payload 做签名，确保：
1. 签名长度固定（64 字符 hex）
2. Hub 可以验证"发送者知道 asset 内容"
3. 完整 payload 在网络上传输，但签名证明来源

### 60.4 Bundle 发布（Gene + Capsule 组合）

**文件**: `a2aProtocol.js:150-200` (`buildPublishBundle`)

```javascript
function buildPublishBundle(opts) {
  // 将 Gene + Capsule（+ 可选的 EvolutionEvent）打包发布
  const geneAssetId = computeAssetId(gene);
  const capsuleAssetId = computeAssetId(capsule);
  const signatureInput = [geneAssetId, capsuleAssetId].sort().join('|');
  const signature = crypto.createHmac('sha256', nodeSecret)
    .update(signatureInput)
    .digest('hex');
  // 签名内容 = 按字母序排列的两个 asset_id
}
```

**Evolver 为什么这样做**：Gene 和 Capsule 必须**同时发布**——单独发布没有意义（没有 Capsule 的 Gene 是未经验证的空洞模板）。

### 60.5 双传输层抽象

**文件**: `a2aProtocol.js:290-330` (`fileTransportSend/Receive`)

Evolver 实现了两套传输层，可插拔：

| 传输层 | 用途 | 特点 |
|--------|------|------|
| **FileTransport** | 本地/离线环境 | JSONL 文件到 `a2a/inbox/outbox` 目录 |
| **HTTPTransport** | 与 Hub 通信 | REST API 调用 |

```javascript
const transports = {
  file: { send: fileTransportSend, receive: fileTransportReceive, list: fileTransportList },
  http: { send: httpTransportSend, receive: httpTransportReceive, list: httpTransportList },
};

function getTransport(name) {
  const n = String(name || process.env.A2A_TRANSPORT || 'file').toLowerCase();
  return transports[n];
}
```

**Evolver 为什么这样做**：FileTransport 让 Evolver 在没有网络的环境下也能运行（通过文件交换消息）。HTTPTransport 是生产环境的默认选择。

### 60.6 FileTransport 的安全设计

**文件**: `a2aProtocol.js:290-340`

```javascript
function fileTransportReceive(opts) {
  const MAX_FILE_BYTES = 256 * 1024;  // 256KB 每文件上限
  // 大文件只读末尾 chunk（从文件尾部向前读）
  if (stat.size > MAX_FILE_BYTES) {
    const buf = Buffer.alloc(MAX_FILE_BYTES);
    fs.readSync(fd, buf, 0, MAX_FILE_BYTES, stat.size - MAX_FILE_BYTES);
    // 跳过不完整的行
    const firstNl = raw.indexOf('\n');
    if (firstNl >= 0) raw = raw.slice(firstNl + 1);
  }
}
```

**Evolver 为什么这样做**：防止恶意/损坏的 inbox 文件撑爆内存。256KB 上限 + 从尾部读取确保即使文件很大也能处理。

### 60.7 心跳与节点注册机制

**文件**: `a2aProtocol.js:440-530` (`sendHelloToHub`, `sendHeartbeat`)

```javascript
function sendHelloToHub() {
  // 首次注册，获取 node_secret
  const msg = buildHello({ nodeId, capabilities: {} });
  return fetch(endpoint, { method: 'POST', ... })
    .then(res => res.json())
    .then(data => {
      const secret = data.payload?.node_secret;
      if (secret) {
        _persistNodeSecret(secret);  // 持久化到 ~/.evomap/node_secret
      }
    });
}
```

**心跳循环**：

```javascript
function sendHeartbeat() {
  const bodyObj = {
    node_id: nodeId,
    uptime_ms: Date.now() - _heartbeatStartedAt,
    meta: {
      worker_enabled: process.env.WORKER_ENABLED === '1',
      worker_domains: [...],
      max_load: Number(process.env.WORKER_MAX_LOAD) || 5,
    }
  };
  // Hub 返回：available_work, overdue_tasks, capability_gaps, novelty_hint
}
```

**心跳的作用**：
1. **保持连接活跃**：Hub 知道节点还在线
2. **拉取任务**：Hub 返回 `available_work` 数组（Hub 上的 bounty tasks）
3. **能力匹配**：Hub 返回 `capability_gaps` 供本地 curriculum 使用
4. **速率限制反馈**：`rate_limited` 时调整心跳间隔

### 60.8 Node Secret 的三级缓存

**文件**: `a2aProtocol.js:380-410` (`getHubNodeSecret`)

```javascript
function getHubNodeSecret() {
  // 1. 环境变量优先
  if (process.env.A2A_NODE_SECRET) return envSecret;
  // 2. 内存缓存（TTL 内）
  const now = Date.now();
  if (_cachedHubNodeSecret && (now - _cachedHubNodeSecretAt) < SECRET_CACHE_TTL_MS)
    return _cachedHubNodeSecret;
  // 3. 本地持久化文件（~/.evomap/node_secret）
  const persisted = _loadPersistedNodeSecret();
  // 4. 环境变量 fallback（A2A_HUB_TOKEN）
  if (process.env.A2A_HUB_TOKEN) return process.env.A2A_HUB_TOKEN;
  return null;
}
```

**Evolver 为什么这样做**：Node secret 是 Hub 认证的核心凭证，三级缓存确保：
- 环境变量覆盖用于容器化/生产部署
- 内存缓存避免频繁文件读取
- 持久化确保重启后仍有效

### 60.9 SSE 事件流（Server-Sent Events）

**文件**: `a2aProtocol.js:850-920` (`hubOpenEventStream`)

```javascript
function hubOpenEventStream(opts) {
  const EventSource = require('eventsource');
  const es = new EventSource(endpoint, {
    headers: { 'Authorization': 'Bearer ' + secret }
  });
  // Hub 通过 SSE 推送：task_assignment, skill_review, circle_invite 等
  return { ok: true, eventSource: es, close: () => es.close() };
}
```

**自动重连机制**：

```javascript
// 指数退避：5s → 10s → 20s → ... → max 120s
_sseReconnectMs = Math.min(_sseReconnectMs * 2, _sseMaxReconnectMs);
```

### 60.10 Hub 基础设施 API

**文件**: `a2aProtocol.js:1000-1221`

Evolver 实现了完整的 Hub 自助基础设施客户端：

| API | 用途 |
|-----|------|
| `hubSelfProvision()` | 机器账户自注册 |
| `hubCreditTopUp()` | 积分充值 |
| `hubCreditTransfer()` | 积分转账（给其他节点） |
| `hubTransferEstimate()` | 转账手续费估算 |
| `hubTransferHistory()` | 转账历史查询 |
| `hubGetIdentity(nodeId)` | 获取任意节点的公开身份 |
| `hubGetAttestation(nodeId)` | 获取声誉证明 |
| `hubVerifyAttestation()` | 验证声誉证明 |
| `hubSetDid(didDocument)` | 设置 DID 文档 |
| `hubGetAuditLogs()` | 审计日志查询 |
| `hubGetWorkReport()` | 工作报告生成 |
| `hubOpenEventStream()` | SSE 实时事件流 |

**关键洞察**：Hub 不仅是个基因市场，还是一个**自包含的 Agent 经济系统**——有身份（Node ID）、货币（Credits）、声誉（Attestation）、审计（Audit Logs）。

### 60.11 限速与退避机制

**文件**: `a2aProtocol.js:490-510`

```javascript
if (data.error === 'rate_limited') {
  const retryMs = Number(data.retry_after_ms) || 0;
  const backoff = retryMs > 0 ? retryMs + 5000 : _heartbeatIntervalMs;
  console.warn('[Heartbeat] Rate limited. Next attempt in ' + Math.round(backoff/1000) + 's.');
  _scheduleNextHeartbeat(backoff);
}
```

**连续失败告警**：
- 3 次连续失败 → 警告"网络问题？"
- 10 次连续失败 → 警告"Hub 不可达"
- 每 50 次连续失败 → 周期性告警

### 60.12 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 节点身份持久化 | 三级缓存（env > memory > file） | **高优先级**: BlueCortexCE 的 client_id 应有类似持久化机制 | 高 |
| HMAC 签名 | asset_id 做签名内容 | **中优先级**: BlueCortexCE 的写操作可用 HMAC 验证来源 | 中 |
| 双传输层抽象 | FileTransport + HTTPTransport 可插拔 | **中优先级**: BlueCortexCE 的传输层可抽象化 | 中 |
| 心跳机制 | 保持连接 + 拉取任务 + 能力匹配 | **高优先级**: BlueCortexCE 的 SDK 应实现轻量心跳 | 高 |
| SSE 事件流 | 实时推送 Hub 事件 | **低优先级**: BlueCortexCE 目前无 Hub 生态 | 低 |
| 积分/经济系统 | Credits + Transfer + Audit | **低优先级**: BlueCortexCE 无 Hub，无经济系统 | 低 |
| 限速退避 | 指数退避 + 连续失败告警 | **高优先级**: BlueCortexCE 的外部 API 调用应有退避机制 | 高 |
| Node Secret 三级缓存 | env > memory > file | **高优先级**: BlueCortexCE 的 API key 应有类似缓存 | 高 |

---

## 61. prompt.js — GEP 提示词构建器（v1.2 新增）

**文件**: `src/gep/prompt.js` (712 lines) + `src/ops/innovation.js` (92 lines)

### 61.1 核心设计原则

prompt.js 是 Evolver 的**核心提示词引擎**，负责构建驱动 LLM 执行的完整 GEP Prompt。它包含：
- **Schema 定义**：5 个强制 JSON 对象的严格格式
- **上下文注入**：信号、基因预览、胶囊历史、反模式区、叙事记忆等
- **指令与约束**：进化哲学、伦理约束、安全规则、宪法级 Ethics
- **多模版组合**：GEP 主提示词 + Hub 复用提示词 + Hub 匹配块

### 61.2 五大强制对象模型（Schema Definitions）

**文件**: `prompt.js:155-215` (`SCHEMA_DEFINITIONS`)

Evolver 要求 LLM 输出**严格分离的 5 个 JSON 对象**：

```javascript
// 0. Mutation（突变触发）— 必须第一个
{
  "type": "Mutation",
  "id": "mut_<timestamp>",
  "category": "repair|optimize|innovate",
  "trigger_signals": ["<signal_string>"],
  "target": "<module_or_gene_id>",
  "expected_effect": "<outcome_description>",
  "risk_level": "low|medium|high",
  "rationale": "<why_this_change_is_necessary>"
}

// 1. PersonalityState（人格状态）
{
  "type": "PersonalityState",
  "rigor": 0.0-1.0,
  "creativity": 0.0-1.0,
  "verbosity": 0.0-1.0,
  "risk_tolerance": 0.0-1.0,
  "obedience": 0.0-1.0
}

// 2. EvolutionEvent（进化事件记录）
{
  "type": "EvolutionEvent",
  "schema_version": "1.5.0",
  "id": "evt_<timestamp>",
  "parent": <parent_evt_id|null>,
  "intent": "repair|optimize|innovate",
  "signals": ["<signal_string>"],
  "genes_used": ["<gene_id>"],
  "mutation_id": "<mut_id>",
  "personality_state": { ... },
  "blast_radius": { "files": N, "lines": N },
  "outcome": { "status": "success|failed", "score": 0.0-1.0 }
}

// 3. Gene（知识单元）
{
  "type": "Gene",
  "schema_version": "1.5.0",
  "id": "gene_<descriptive_name>",
  "summary": "<clear description>",
  "category": "repair|optimize|innovate",
  "signals_match": ["<pattern>"],
  "preconditions": ["<condition>"],
  "strategy": ["<step_1>", "<step_2>"],
  "constraints": { "max_files": N, "forbidden_paths": [] },
  "validation": ["<node_command>"]
}

// 4. Capsule（结果胶囊）
{
  "type": "Capsule",
  "schema_version": "1.5.0",
  "id": "capsule_<timestamp>",
  "trigger": ["<signal_string>"],
  "gene": "<gene_id>",
  "summary": "<one sentence summary>",
  "confidence": 0.0-1.0,
  "blast_radius": { "files": N, "lines": N }
}
```

**Evolver 为什么这样做**：
- **强制顺序**：`Mutation` 必须第一个，确保 LLM 先思考"要做什么"再输出其他对象
- **结构化约束**：避免 LLM 输出无结构的自然语言，便于后续解析
- **Schema 版本化**：每个对象带 `schema_version`，便于协议演进

### 61.3 上下文块注入顺序

**文件**: `prompt.js:480-560` (`buildGepPrompt`)

```javascript
// 注入顺序（从上到下）：
// 1. SCHEMA_DEFINITIONS（Schema 定义）
// 2. Directives & Logic（Intent + Selection + Strategy）
// 3. PHILOSOPHY（进化哲学）
// 4. CONSTRAINTS（约束规则）
// 5. CONSTITUTIONAL ETHICS（宪法级伦理）
// 6. SKILL OVERLAP PREVENTION（技能重叠防护）
// 7. SKILL CREATION QUALITY GATES（技能创建质量门）
// 8. CRITICAL SAFETY（系统崩溃防护）
// 9. COMMON FAILURE PATTERNS（常见失败模式）
// 10. FAILURE STREAK AWARENESS（失败序列感知）
// 11. Context [Signals]
// 12. Context [Env Fingerprint]
// 13. Context [Injection Hint]
// 14. Context [Gene Preview]
// 15. Context [Capsule Preview]
// 16. Context [Capability Candidates]
// 17. Context [Hub Matched Solution]
// 18. Context [External Candidates]
// 19. Context [Anti-Pattern Zone]
// 20. Context [Lessons from Ecosystem]
// 21. Context [Execution]（包含完整的执行上下文）
```

### 61.4 宪法级 Ethics（Constitutional Ethics）

**文件**: `prompt.js:350-390`

这是 Evolver 的**最高层级约束**，任何进化周期都不能违背：

```javascript
CONSTITUTIONAL ETHICS (EvoMap Ethics Committee -- Mandatory):
1. HUMAN WELFARE PRIORITY: Never create tools that could harm humans...
2. CARBON-SILICON SYMBIOSIS: Evolution must serve both human and agent interests...
3. TRANSPARENCY: All actions must be auditable. Never hide or conceal mutations...
4. FAIRNESS: Never create monopolistic strategies that block other agents...
5. SAFETY: Never bypass safety mechanisms, guardrails, validation checks...
```

** Evolvement 为什么这样做**：内置宪法级伦理约束，确保 LLM 在任何情况下都不会生成有害代码。

### 61.5 技能创建质量门（Skill Creation Quality Gates）

**文件**: `prompt.js:395-450`

当 `intent=innovate` 时，创建新技能必须通过严格的质量门：

```javascript
SKILL CREATION QUALITY GATES (MANDATORY for innovate intent):
1. STRUCTURE: skills/<name>/ 必须有 index.js + SKILL.md + package.json
2. SKILL NAMING: 描述性 kebab-case，禁止时间戳/随机数/工具名
3. SKILL.MD FRONTMATTER: 必须有 YAML frontmatter（name + description）
4. CONCISENESS: SKILL.md < 500 lines，详细内容放 references/
5. EXPORT VERIFICATION: node -e "require('./skills/<name>')" 必须成功
6. SENSITIVE DATA PARAMETERIZATION: 所有密钥/路径/密码必须参数化
7. TEST BEFORE SOLIDIFY: 创建后必须实际运行验证
8. ATOMIC CREATION: 一个 cycle 内完成所有文件
```

**Evolver 为什么这样做**：创新的代价是风险——质量门确保新技能不会成为技术债务。

### 61.6 停滞检测与创新催化剂

**文件**: `src/ops/innovation.js` + `prompt.js:290-330`

当检测到停滞信号时，注入创新催化剂：

```javascript
// 停滞信号
const stagnationSignals = [
  'evolution_stagnation_detected',
  'stable_success_plateau',
  'repair_loop_detected',
  'empty_cycle_loop_detected',
  'evolution_saturation'
];

// 创新催化剂生成逻辑（innovation.js）
function generateInnovationIdeas() {
  const categories = {
    'security': skills.filter(s => s.includes('security|audit|guard')).length,
    'media': skills.filter(s => s.includes('image|video|music|voice')).length,
    'dev': skills.filter(s => s.includes('git-|code-|lint|test')).length,
    'automation': skills.filter(s => s.includes('auto-|scheduler|cron')).length,
    'data': skills.filter(s => s.includes('db|store|cache|index')).length
  };
  
  // 找出最弱的 2 个类别
  const weakAreas = Object.entries(categories).sort((a, b) => a[1] - b[1]).slice(0, 2);
  
  // 针对弱项生成创新建议
  // security → dependency-scanner, permission-auditor
  // media → meme-generator, video-summarizer
  // dev → code-stats, todo-manager
  // automation → meeting-prep, broken-link-checker
  // data → local-vector-store, log-analyzer
}
```

### 61.7 上下文截断策略（Truncation Strategy）

**文件**: `prompt.js:125-135`

```javascript
function truncateContext(text, maxLength = 20000) {
  if (!text || text.length <= maxLength) return text || '';
  return text.slice(0, maxLength) + '\n...[TRUNCATED_EXECUTION_CONTEXT]...';
}

// 截断策略：
// 1. 优先截断 Execution Context（最长的块）
// 2. Signals 最多 50 个，每个最长 200 字符
// 3. Capabilities 预览：选了基因时限制到 500 chars
// 4. 截断后添加明确的 [TRUNCATED] 标记
```

### 61.8 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 五大强制对象模型 | Mutation → Personality → Event → Gene → Capsule | **高优先级**: BlueCortexCE 的 API 响应应支持结构化 schema | 高 |
| 宪法级 Ethics | 内置最高层级伦理约束 | **高优先级**: BlueCortexCE 的 Observation 应拒绝有害内容 | 高 |
| 技能创建质量门 | 8 重质量门确保新技能质量 | **高优先级**: BlueCortexCE 的任何"自动生成"内容都应有多重验证 | 高 |
| 停滞检测 + 创新催化剂 | 信号 → 类别弱点 → 创新建议 | **高优先级**: BlueCortexCE 应在检索效果停滞时推荐新方向 | 高 |
| 上下文截断策略 | 按块类型优先级截断 | **高优先级**: BlueCortexCE 的 context generate 应有智能截断 | 高 |
| Schema 版本化 | 每个对象带 schema_version | **中优先级**: BlueCortexCE 的 API 应有版本字段 | 中 |
| 失败序列感知 | 3+ 次相同基因 → 强制换 intent | **高优先级**: BlueCortexCE 应检测"重复失败模式"并警告 | 高 |

---

## 62. strategy.js — 进化策略预设系统（v1.2 新增）

**文件**: `src/gep/strategy.js` (127 lines)

### 62.1 策略预设体系

**文件**: `strategy.js:10-60`

Evolver 实现了 6 种策略预设，每种定义三个 intent 的目标分配比例：

```javascript
const STRATEGIES = {
  'balanced': {
    repair: 0.20,      // 20% 资源用于修复
    optimize: 0.30,   // 30% 用于优化
    innovate: 0.50,    // 50% 用于创新
    repairLoopThreshold: 0.50,  // 50% repair 触发强制创新
    label: 'Balanced',
  },
  'innovate': {
    repair: 0.05,
    optimize: 0.15,
    innovate: 0.80,    // 80% 用于创新
    repairLoopThreshold: 0.30,
    label: 'Innovation Focus',
  },
  'harden': {
    repair: 0.40,      // 40% 用于修复
    optimize: 0.40,    // 40% 用于优化
    innovate: 0.20,
    repairLoopThreshold: 0.70,
    label: 'Hardening',
  },
  'repair-only': {
    repair: 0.80,
    optimize: 0.20,
    innovate: 0.00,    // 禁止创新
    repairLoopThreshold: 1.00,
    label: 'Repair Only',
  },
  'early-stabilize': {
    repair: 0.60,
    optimize: 0.25,
    innovate: 0.15,
    repairLoopThreshold: 0.80,
    label: 'Early Stabilization',
  },
  'steady-state': {
    repair: 0.60,
    optimize: 0.30,
    innovate: 0.10,    // 最小创新
    repairLoopThreshold: 0.90,
    label: 'Steady State',
  },
};
```

### 62.2 自适应策略检测

**文件**: `strategy.js:60-100`

```javascript
function resolveStrategy(opts) {
  const signals = opts?.signals || [];
  const name = process.env.EVOLVE_STRATEGY || 'balanced';
  
  // 自动检测：未设置显式策略时应用启发式
  if (name === 'balanced' || name === 'auto') {
    const cycleCount = _readCycleCount();
    
    // 前 5 个 cycle → early-stabilize
    if (cycleCount > 0 && cycleCount <= 5) {
      name = 'early-stabilize';
    }
    
    // 饱和信号 → steady-state
    if (signals.includes('evolution_saturation') || signals.includes('force_steady_state')) {
      name = 'steady-state';
    }
  }
  
  return STRATEGIES[name] || STRATEGIES['balanced'];
}
```

**Evolver 为什么这样做**：
- **前 5 cycle** 优先修复，避免早期引入不稳定因素
- **饱和时** 切换到守成，减少创新风险
- **环境变量覆盖** 支持手动强制指定策略

### 62.3 repairLoopThreshold — 强制创新触发器

**文件**: `strategy.js:20-30`

```javascript
// 当 repair intent 在最近 8 个 cycle 中占比超过 repairLoopThreshold 时
// → 强制切换到 innovate intent
// 例如：balanced 的 repairLoopThreshold=0.50
// 如果最近 8 个 cycle 有 5 个是 repair → 下个 cycle 必须 innovate
```

**Evolver 为什么这样做**：防止进化陷入"修复循环"——反复修复而不探索新方向。

### 62.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 多策略预设 | 6 种策略覆盖不同场景 | **高优先级**: BlueCortexCE 的检索策略可类似预设（精确/语义/混合） | 高 |
| repairLoopThreshold | 防止陷入修复循环 | **高优先级**: BlueCortexCE 应防止"反复检索相同失败模式" | 高 |
| 自适应策略检测 | cycle count + 饱和信号 | **高优先级**: BlueCortexCE 的总结触发可参考"使用频率"动态调整 | 高 |
| 策略分配比例 | repair/optimize/innovate 显式比例 | **中优先级**: BlueCortexCE 的 Observation 分类可参考此比例 | 中 |

---

## 63. memoryGraphAdapter.js — 本地/远程双模适配器（v1.2 新增）

**文件**: `src/gep/memoryGraphAdapter.js` (195 lines)

### 63.1 设计背景：适配器模式

**文件**: `memoryGraphAdapter.js:1-30`

memoryGraphAdapter 是 **memoryGraph.js 的接口抽象层**：

```
┌─────────────────────────────────────────┐
│           Adapter Interface Contract    │
│  getAdvice()                           │
│  recordSignalSnapshot()                 │
│  recordHypothesis()                    │
│  recordAttempt()                        │
│  recordOutcome()                        │
│  recordExternalCandidate()               │
│  memoryGraphPath()                      │
│  computeSignalKey()                     │
│  tryReadMemoryGraphEvents()             │
└─────────────────────────────────────────┘
           ↓                    ↓
┌─────────────────────┐  ┌─────────────────────┐
│   Local Adapter     │  │   Remote Adapter    │
│ (memoryGraph.js)   │  │ (MEMORY_GRAPH_PROVIDER=remote) │
│   默认实现          │  │   SaaS KG 服务       │
└─────────────────────┘  └─────────────────────┘
```

### 63.2 本地适配器（默认）

**文件**: `memoryGraphAdapter.js:40-70`

```javascript
const localAdapter = {
  name: 'local',
  
  getAdvice(opts) {
    return localGraph.getMemoryAdvice(opts);
  },
  recordSignalSnapshot(opts) {
    return localGraph.recordSignalSnapshot(opts);
  },
  recordHypothesis(opts) {
    return localGraph.recordHypothesis(opts);
  },
  recordAttempt(opts) {
    return localGraph.recordAttempt(opts);
  },
  recordOutcome(opts) {
    return localGraph.recordOutcomeFromState(opts);
  },
  recordExternalCandidate(opts) {
    return localGraph.recordExternalCandidate(opts);
  },
  memoryGraphPath() {
    return localGraph.memoryGraphPath();
  },
  computeSignalKey(signals) {
    return localGraph.computeSignalKey(signals);
  },
  tryReadMemoryGraphEvents(limit) {
    return localGraph.tryReadMemoryGraphEvents(limit);
  },
};
```

### 63.3 远程适配器（带本地降级）

**文件**: `memoryGraphAdapter.js:80-170`

```javascript
function buildRemoteAdapter() {
  const remoteUrl = process.env.MEMORY_GRAPH_REMOTE_URL || '';
  const remoteKey = process.env.MEMORY_GRAPH_REMOTE_KEY || '';
  const timeoutMs = Number(process.env.MEMORY_GRAPH_REMOTE_TIMEOUT_MS) || 5000;

  // 远程调用 + 本地降级包装
  function withFallback(localFn, remoteFn) {
    return async function (...args) {
      try {
        return await remoteFn(...args);
      } catch (e) {
        // 任何远程失败 → 回退到本地
        return localFn(...args);
      }
    };
  }

  return {
    name: 'remote',
    
    // getAdvice: 优先远程（主要增强点）
    getAdvice: withFallback(
      (opts) => localGraph.getMemoryAdvice(opts),
      async (opts) => {
        const result = await remoteCall('/kg/advice', { ... });
        // 规范化远程响应以匹配本地契约
        return {
          currentSignalKey: result.currentSignalKey || ...,
          preferredGeneId: result.preferredGeneId || null,
          bannedGeneIds: new Set(result.bannedGeneIds || []),
          explanation: Array.isArray(result.explanation) ? result.explanation : [],
        };
      }
    ),

    // 写操作：先写本地，再异步同步到远程
    recordSignalSnapshot(opts) {
      const ev = localGraph.recordSignalSnapshot(opts);
      remoteCall('/kg/ingest', { kind: 'signal', event: ev }).catch(() => {});
      return ev;
    },
    // ... 其他写操作类似
  };
}
```

### 63.4 双模适配器的关键设计

**Evolver 为什么这样做**：

1. **本地优先写**：append-only 本地图谱是事实来源，远程只是缓存
2. **远程降级**：`withFallback` 确保任何远程失败都不阻塞进化
3. **异步同步**：写操作立即返回，远程同步在后台，不阻塞主流程
4. **响应规范化**：远程 KG 可能用不同的字段名，需要适配器转换
5. **Provider 解析**：环境变量 `MEMORY_GRAPH_PROVIDER=remote` 切换模式

### 63.5 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 适配器接口契约 | 9 个方法定义完整接口 | **高优先级**: BlueCortexCE 的存储层应有类似抽象 | 高 |
| 本地优先写 | 本地是来源，远程是缓存 | **高优先级**: BlueCortexCE 的多实例部署应本地优先 | 高 |
| 远程降级 | withFallback 确保任何失败都能回退 | **高优先级**: BlueCortexCE 的外部 API 调用应有降级策略 | 高 |
| 异步同步 | 写操作立即返回，后台同步 | **高优先级**: BlueCortexCE 的搜索缓存更新可异步化 | 高 |
| Provider 切换 | 环境变量切换 local/remote | **中优先级**: BlueCortexCE 的存储后端可支持插件化 | 中 |

---

## 64. innovation.js — 停滞检测与创新催化剂（v1.2 新增）

**文件**: `src/ops/innovation.js` (92 lines)

### 64.1 设计背景

innovation.js 是 Evolver 的**创新催化剂**，当检测到进化停滞（stagnation）时，从已有技能库中发现能力缺口并提出创新方向。

### 64.2 技能分类扫描

**文件**: `innovation.js:10-40`

```javascript
function listSkills() {
  const dir = getSkillsDir();
  return fs.readdirSync(dir).filter(f => !f.startsWith('.'));
}

function generateInnovationIdeas() {
  const skills = listSkills();
  const categories = {
    'feishu': skills.filter(s => s.startsWith('feishu-')).length,
    'dev': skills.filter(s => s.startsWith('git-') || s.includes('lint') || s.includes('test')).length,
    'media': skills.filter(s => s.includes('image') || s.includes('video') || s.includes('music')).length,
    'security': skills.filter(s => s.includes('security') || s.includes('audit')).length,
    'automation': skills.filter(s => s.includes('auto-') || s.includes('scheduler')).length,
    'data': skills.filter(s => s.includes('db') || s.includes('cache') || s.includes('index')).length
  };
  
  // 找出最弱的 2 个类别
  const weakAreas = Object.entries(categories).sort((a, b) => a[1] - b[1]).slice(0, 2);
  
  // 针对弱项生成创新建议
  if (weakAreas.includes('security')) {
    ideas.push("- Security: Implement a 'dependency-scanner' skill...");
  }
  // ...
}
```

### 64.3 创新想法生成策略

**文件**: `innovation.js:40-80`

1. **填补缺口**：针对最弱的类别提出具体技能建议
2. **优化现有**：当技能数 > 50 时，提示去重/合并
3. **元创新**：建议增强 Evolver 自身（如性能监控仪表板）

```javascript
// 想法生成规则
if (skills.length > 50) {
  ideas.push("- Optimization: Identify and deprecate unused skills...");
  ideas.push("- Optimization: Merge similar skills...");
}

ideas.push("- Meta: Enhance Evolver's self-reflection...");
return ideas.slice(0, 3);  // 最多 3 个想法
```

### 64.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 技能分类扫描 | 统计各能力类别的技能数量 | **高优先级**: BlueCortexCE 应能统计"哪些类型观察被记录最多" | 高 |
| 能力缺口发现 | 最少类别 → 创新方向 | **高优先级**: BlueCortexCE 应能发现"哪些类型的查询效果最差" | 高 |
| 元创新 | 建议增强系统自身 | **中优先级**: BlueCortexCE 的巡检可建议"如何改进 BlueCortexCE" | 中 |
| 技能去重提示 | 50+ 技能时提示合并/废弃 | **低优先级**: BlueCortexCE 无技能库概念 | 低 |

---

## 65. questionGenerator.js — 主动问题生成机制（v1.2 新增）

**文件**: `src/gep/questionGenerator.js` (267 lines)

### 65.1 设计背景

questionGenerator 从进化上下文中生成**主动问题**，发送到 Hub bounty 系统，实现多 Agent 协作解决问题。

### 65.2 问题生成策略（6 种场景）

**文件**: `questionGenerator.js:70-180`

```javascript
function generateQuestions(opts) {
  // Strategy 1: 反复错误（无法自动修复）
  if (signalSet.has('recurring_error')) {
    candidates.push({
      question: 'Recurring error: ' + errDetail + ' -- What approaches have worked?',
      signals: ['recurring_error', 'auto_repair_failed'],
      priority: 3,
    });
  }
  
  // Strategy 2: 能力缺口
  if (signalSet.has('capability_gap')) {
    candidates.push({
      question: 'Capability gap: ' + gapContext + ' -- How can this be addressed?',
      signals: ['capability_gap'],
      priority: 2,
    });
  }
  
  // Strategy 3: 停滞/饱和
  if (signalSet.has('evolution_saturation')) {
    candidates.push({
      question: 'Evolution saturated. What new directions?',
      signals: ['evolution_saturation', 'innovation_needed'],
      priority: 1,
    });
  }
  
  // Strategy 4: 连续失败（≥4 次）
  if (streakCount >= 4) {
    candidates.push({
      question: 'Failed ' + streakCount + ' consecutive cycles. Alternative strategies?',
      signals: ['failure_streak', 'external_help_needed'],
      priority: 3,
    });
  }
  
  // Strategy 5: 用户功能请求
  if (signalSet.has('user_feature_request')) {
    candidates.push({
      question: 'User feature request: ' + featureContext,
      signals: ['user_feature_request', 'community_solution_sought'],
      priority: 1,
    });
  }
  
  // Strategy 6: 性能瓶颈
  if (signalSet.has('perf_bottleneck')) {
    candidates.push({
      question: 'Performance bottleneck: ' + perfContext + ' -- Optimization strategies?',
      signals: ['perf_bottleneck', 'optimization_sought'],
      priority: 2,
    });
  }
}
```

### 65.3 去重与限流

**文件**: `questionGenerator.js:20-65`

```javascript
const MIN_INTERVAL_MS = 3 * 60 * 60 * 1000;  // 3 小时最小间隔
const MAX_QUESTIONS_PER_CYCLE = 2;

// 去重：完全相同 OR 70% 词集合重叠
function isDuplicate(question, recentQuestions) {
  var qWords = new Set(qLower.split(/\s+/).filter(w => w.length > 2));
  var pWords = new Set(prev.split(/\s+/).filter(w => w.length > 2));
  var overlap = [...qWords].filter(w => pWords.has(w)).length;
  if (overlap / Math.max(qWords.size, pWords.size) > 0.7) return true;
  return false;
}
```

### 65.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 6 种问题场景 | recurring_error / capability_gap / saturation / failure_streak / user_request / perf | **高优先级**: BlueCortexCE 的错误处理可参考这 6 种场景 | 高 |
| 问题优先级 | priority 1-3 区分紧急程度 | **中优先级**: BlueCortexCE 的问题上报可按紧急度分级 | 中 |
| 3 小时限流 | 防止频繁向 Hub 发问题 | **高优先级**: BlueCortexCE 的任何外部 API 调用都应有频率限制 | 高 |
| 70% 词重叠去重 | 模糊去重而非精确匹配 | **中优先级**: BlueCortexCE 的去重可采用类似模糊策略 | 中 |
| 问题发送给 Hub | Hub bounty 系统协作解决 | **低优先级**: BlueCortexCE 无 Hub 生态 | 低 |

---

## 66. idleScheduler.js — OMLS 空闲调度器（v1.2 新增）

**文件**: `src/gep/idleScheduler.js` (171 lines)

### 66.1 设计背景：OMLS 概念

idleScheduler 实现了 **OMLS（Observer Model with Limited States）空闲调度**——当检测到用户不活跃时，Evolver 可以运行更重的操作（distillation、reflection）；用户活跃时只做轻量信号收集。

### 66.2 系统空闲时间检测

**文件**: `idleScheduler.js:30-90`

```javascript
function getSystemIdleSeconds() {
  const platform = process.platform;
  
  if (platform === 'win32') {
    // Windows: PowerShell 调用 GetLastInputInfo
    const psCode = [...].join('\n');
    return parseInt(execSync('powershell ...', { timeout: 10000 }), 10);
  } else if (platform === 'darwin') {
    // macOS: ioreg 查询 HIDIdleTime
    const result = execSync('ioreg -c IOHIDSystem | grep HIDIdleTime', { timeout: 5000 });
    return Math.floor(parseInt(match[1], 10) / 1000000000);
  } else if (platform === 'linux') {
    // Linux: xprintidle 或 /proc/interrupts
    const result = execSync('xprintidle 2>/dev/null || echo -1', { timeout: 5000 });
    return Math.floor(parseInt(result, 10) / 1000);
  }
  return -1;  // 不支持
}
```

### 66.3 强度级别（Intensity Levels）

**文件**: `idleScheduler.js:95-115`

```javascript
const IDLE_THRESHOLD_SECONDS = 300;      // 5 分钟
const DEEP_IDLE_THRESHOLD_SECONDS = 1800; // 30 分钟

function determineIntensity(idleSeconds) {
  if (idleSeconds < 0) return 'normal';
  if (idleSeconds >= DEEP_IDLE_THRESHOLD_SECONDS) return 'deep';
  if (idleSeconds >= IDLE_THRESHOLD_SECONDS) return 'aggressive';
  return 'normal';
}
```

| 强度 | 空闲时间 | Sleep Multiplier | 应该蒸馏 | 应该反思 | 应该深度进化 |
|------|---------|-----------------|---------|---------|------------|
| `normal` | < 5 分钟 | 1x | ❌ | ❌ | ❌ |
| `aggressive` | 5-30 分钟 | 0.5x | ✅ | ✅ | ❌ |
| `deep` | > 30 分钟 | 0.25x | ✅ | ✅ | ✅ |

### 66.4 调度推荐结构

**文件**: `idleScheduler.js:120-160`

```javascript
function getScheduleRecommendation() {
  return {
    enabled: true,
    idle_seconds: 450,
    intensity: 'aggressive',
    sleep_multiplier: 0.5,        // 减少等待间隔
    should_distill: true,         // 可以运行 distillation
    should_reflect: true,         // 可以运行 reflection
    should_deep_evolve: false,
  };
}
```

**Evolver 为什么这样做**：
- **用户空闲时**运行重量级操作，不影响用户体验
- **用户活跃时**只做信号收集，避免资源竞争
- **自适应强度**：空闲越久，可以运行的操作越重

### 66.5 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 跨平台空闲检测 | Windows/macOS/Linux 各自实现 | **高优先级**: BlueCortexCE 的服务可检测用户活跃状态 | 高 |
| 强度级别 | normal / aggressive / deep | **高优先级**: BlueCortexCE 的巡检任务可根据系统负载动态调整 | 高 |
| Sleep Multiplier | 空闲时减少等待 | **中优先级**: BlueCortexCE 的 cron 调度可参考空闲状态 | 中 |
| 重操作门控 | distillation / reflection 门控 | **中优先级**: BlueCortexCE 的 LLM 调用可按系统状态分级 | 中 |
| OMLS 概念 | 有限状态机的空闲调度 | **中优先级**: BlueCortexCE 的任务队列可实现类似状态机 | 中 |

---

## 67. localStateAwareness.js — 本地状态感知（v1.2 新增）

**文件**: `src/gep/localStateAwareness.js` (200 lines)

### 67.1 设计背景

localStateAwareness 是 Evolver 的**去重防护层**——在执行任何注册/配置/创建操作前，检查本地状态，避免重复创建已有资源。

### 67.2 五大状态捕获

**文件**: `localStateAwareness.js:90-195`

```javascript
function captureLocalState() {
  var sections = [];
  
  sections.push('[Node Identity]');
  sections = sections.concat(captureNodeIdentity());
  // 输出：
  // - Node ID: node_abc123 (REGISTERED -- do NOT re-register)
  // - Node Secret: PRESENT (authenticated -- do NOT request new secret)
  
  sections.push('[Environment Config]');
  sections = sections.concat(captureEnvConfig());
  // 输出：
  // - Env configured: A2A_NODE_ID, A2A_HUB_URL
  // - Env not set: GITHUB_TOKEN
  // - .env file: EXISTS at /path/.env
  
  sections.push('[Evolution State]');
  sections = sections.concat(captureEvolutionState());
  // 输出：
  // - Evolution cycles completed: 47
  // - Last evolution run: 3600s ago
  // - Personality: rigor=0.75 creativity=0.35 risk_tolerance=0.4
  
  sections.push('[Memory & Knowledge]');
  sections = sections.concat(captureMemoryState());
  // 输出：
  // - Memory directory: EXISTS at memory/
  // - MEMORY.md: 2048 bytes
  // - Memory graph: 8192 bytes
  
  sections.push('[Skills]');
  sections = sections.concat(captureSkillsState());
  // 输出：
  // - Installed skills: 23 (at skills/)
  
  return sections.join('\n');
}
```

### 67.3 节点身份感知

**文件**: `localStateAwareness.js:35-55`

```javascript
function captureNodeIdentity() {
  const nodeId = process.env.A2A_NODE_ID || _readFileSafe(NODE_ID_FILE);
  if (nodeId) {
    lines.push('- Node ID: ' + nodeId + ' (REGISTERED -- do NOT re-register)');
  } else {
    lines.push('- Node ID: NOT SET (registration may be needed)');
  }
  
  const hasSecret = !!process.env.A2A_NODE_SECRET || _fileExists(NODE_SECRET_FILE);
  if (hasSecret) {
    lines.push('- Node Secret: PRESENT (authenticated -- do NOT request new secret)');
  } else {
    lines.push('- Node Secret: MISSING (hello handshake may be needed)');
  }
}
```

### 67.4 配置文件检测

**文件**: `localStateAwareness.js:55-75`

```javascript
function captureEnvConfig() {
  const A2A_ENV_KEYS = [
    'A2A_NODE_ID', 'A2A_HUB_URL', 'A2A_NODE_SECRET',
    'AGENT_NAME', 'EVOLVE_STRATEGY', 'WORKER_ENABLED',
    'EVOLVER_SESSION_SCOPE', 'GITHUB_TOKEN',
  ];
  
  // 检测哪些环境变量已配置
  for (const key of A2A_ENV_KEYS) {
    if (process.env[key]) configured.push(key);
    else missing.push(key);
  }
  
  // 检测 .env 文件是否存在
  const envFile = path.join(repoRoot, '.env');
  if (fs.existsSync(envFile)) {
    lines.push('- .env file: EXISTS at ' + envFile);
  } else {
    lines.push('- .env file: MISSING at ' + envFile);
  }
}
```

### 67.5 进化状态感知

**文件**: `localStateAwareness.js:75-90`

```javascript
function captureEvolutionState() {
  const statePath = path.join(evoDir, 'evolution_state.json');
  const state = JSON.parse(fs.readFileSync(statePath, 'utf8'));
  
  lines.push('- Evolution cycles completed: ' + state.cycleCount);
  lines.push('- Last evolution run: ' + ago + 's ago');
  
  // personality 状态
  const personality = JSON.parse(fs.readFileSync(personalityPath, 'utf8'));
  lines.push('- Personality: rigor=' + p.rigor + ' creativity=' + p.creativity ...);
}
```

### 67.6 本地状态注入 GEP Prompt

**文件**: `prompt.js:320-340`

Evolver 在 GEP Prompt 中注入本地状态：

```javascript
LOCAL STATE AWARENESS (CRITICAL -- PREVENT DUPLICATE ACTIONS):
Before taking any setup, registration, or configuration action,
CHECK the Local State section in the execution context.
If a resource already exists (node registered, secret present, env configured),
DO NOT recreate it.

If you cannot find a configuration value, check these locations FIRST:
  1. ~/.evomap/          (node_id, node_secret)
  2. <repo>/.env         (A2A_NODE_ID, A2A_HUB_URL, A2A_NODE_SECRET)
  3. workspace/memory/   (MEMORY.md, evolution state files)
  4. workspace/skills/   (installed skills)
Redundant registration = WASTED CYCLE.
```

**Evolver 为什么这样做**：
- **防止重复注册**：Hub handshake 幂等，重复调用浪费 credits
- **防止重复配置**：.env 存在就不重新创建
- **防止重复创建技能**：技能已安装就不重新创建

### 67.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 五大状态捕获 | Node Identity / Env Config / Evolution / Memory / Skills | **高优先级**: BlueCortexCE 的巡检应能捕获完整系统状态 | 高 |
| 已注册标记 | "(REGISTERED -- do NOT re-register)" | **高优先级**: BlueCortexCE 的 SDK 应跟踪"已完成操作"避免重复 | 高 |
| 配置存在性检测 | .env / node_secret / memory/ 文件夹 | **高优先级**: BlueCortexCE 的初始化应检测而非盲目创建 | 高 |
| 浪费 cycle 警告 | "Redundant registration = WASTED CYCLE" | **高优先级**: BlueCortexCE 的日志应明确标识重复操作 | 高 |
| 多位置查询 | ~/.evomap → .env → workspace/memory → skills | **中优先级**: BlueCortexCE 的配置解析应有优先级链 | 中 |

---

## 68. gitOps.js — Git 操作与原子回滚（v1.3 新增）

**文件**: `src/gep/gitOps.js` (258 lines)

### 68.1 设计定位

gitOps.js 是 Evolver 的 **Git 操作工具层**，从 solidify.js 中分离出来，专门负责：
1. Git 命令执行（`runCmd` / `tryRunCmd`）
2. 变更文件列表捕获（`gitListChangedFiles`）
3. Diff 快照（`captureDiffSnapshot`）
4. 关键文件保护（`isCriticalProtectedPath`）
5. 原子回滚（`rollbackTracked` + `rollbackNewUntrackedFiles`）

### 68.2 变更文件捕获（gitListChangedFiles）

**文件**: `gitOps.js:55-72`

```javascript
function gitListChangedFiles({ repoRoot }) {
  const files = new Set();
  // 1. unstaged changes
  const s1 = tryRunCmd('git diff --name-only', { cwd: repoRoot });
  // 2. staged changes
  const s2 = tryRunCmd('git diff --cached --name-only', { cwd: repoRoot });
  // 3. untracked files (new files)
  const s3 = tryRunCmd('git ls-files --others --exclude-standard', { cwd: repoRoot });
  return Array.from(files);
}
```

**Evolver 为什么这样做**：变更文件列表是 blast radius 计算的基础。需要同时捕获 staged、unstaged 和 untracked 三类变更。

### 68.3 Diff 快照（captureDiffSnapshot）

**文件**: `gitOps.js:75-90`

```javascript
const DIFF_SNAPSHOT_MAX_CHARS = 8000;

function captureDiffSnapshot(repoRoot) {
  const parts = [];
  const unstaged = tryRunCmd('git diff', { cwd: repoRoot, timeoutMs: 30000 });
  if (unstaged.ok && unstaged.out) parts.push(unstaged.out);
  const staged = tryRunCmd('git diff --cached', { cwd: repoRoot, timeoutMs: 30000 });
  if (staged.ok && staged.out) parts.push(staged.out);
  let combined = parts.join('\n');
  if (combined.length > DIFF_SNAPSHOT_MAX_CHARS) {
    combined = combined.slice(0, DIFF_SNAPSHOT_MAX_CHARS) + '\n... [TRUNCATED]';
  }
  return combined || '';
}
```

**Evolver 为什么这样做**：`captureDiffSnapshot` 在 `rollback` **之前**调用，用于保存失败时的完整变更内容（FailedCapsule 的一部分）。

### 68.4 关键文件保护（CRITICAL_PROTECTED）

**文件**: `gitOps.js:95-118`

```javascript
const CRITICAL_PROTECTED_PREFIXES = [
  'skills/feishu-evolver-wrapper/',
  'skills/feishu-common/',
  'skills/feishu-post/',
  // ... 10 个 skill 目录
];

const CRITICAL_PROTECTED_FILES = [
  'MEMORY.md', 'SOUL.md', 'IDENTITY.md', 'AGENTS.md', 'USER.md',
  'HEARTBEAT.md', 'RECENT_EVENTS.md', 'TOOLS.md',
  'TROUBLESHOOTING.md', 'openclaw.json', '.env', 'package.json',
];

function isCriticalProtectedPath(relPath) {
  const rel = normalizeRelPath(relPath);
  for (const prefix of CRITICAL_PROTECTED_PREFIXES) {
    if (rel === prefix || rel.startsWith(prefix)) return true;
  }
  for (const f of CRITICAL_PROTECTED_FILES) {
    if (rel === f) return true;
  }
  return false;
}
```

**Evolver 为什么这样做**：即使在回滚模式下，**关键 agent 文件**（MEMORY.md、openclaw.json、skills 目录等）也**不能被删除或覆盖**。这是安全防护的最后一道防线。

### 68.5 三模式回滚（rollbackTracked）

**文件**: `gitOps.js:120-155`

```javascript
const mode = String(process.env.EVOLVER_ROLLBACK_MODE || 'hard').toLowerCase();

if (mode === 'none') {
  // 不回滚，仅记录
  return;
}

if (mode === 'stash') {
  const stashRef = 'evolver-rollback-' + Date.now();
  const result = tryRunCmd('git stash push -m "' + stashRef + '" --include-untracked');
  // 可通过 git stash list 恢复
  return;
}

// 默认 hard 模式
tryRunCmd('git restore --staged --worktree .');
tryRunCmd('git reset --hard');
```

**Evolver 为什么这样做**：
- `none`：诊断模式，保留所有变更用于调试
- `stash`：安全模式，变更存入 stash 可恢复
- `hard`：破坏性模式，彻底清除所有变更

### 68.6 新文件回滚（rollbackNewUntrackedFiles）

**文件**: `gitOps.js:157-215`

```javascript
function rollbackNewUntrackedFiles({ repoRoot, baselineUntracked }) {
  const baseline = new Set(baselineUntracked);
  const current = gitListUntrackedFiles(repoRoot);
  // 仅删除本次运行新增的文件（不在 baseline 中的）
  const toDelete = current.filter(f => !baseline.has(f));
  
  for (const rel of toDelete) {
    // 跳过关键保护文件
    if (isCriticalProtectedPath(safeRel)) { skipped.push(safeRel); continue; }
    // 路径穿越防护
    if (!normAbs.startsWith(normRepo + path.sep)) continue;
    // 删除文件
    fs.unlinkSync(normAbs);
  }
  
  // 清理空目录（从深到浅排序，避免误删父目录）
  for (const dir of sortedDirs) {
    if (fs.readdirSync(dirAbs).length === 0) fs.rmdirSync(dirAbs);
  }
}
```

**关键安全措施**：
1. **Baseline 比较**：只删除"本次运行新增"的文件，baseline 中已有的文件不碰
2. **关键文件跳过**：`isCriticalProtectedPath` 保护所有 skills 目录和 agent 文件
3. **路径穿越防护**：确保 `normAbs` 在 `repoRoot` 子树下
4. **空目录清理**：从深到浅删除（避免删了子目录后又删父目录的误判）

### 68.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 关键文件保护 | skills/ + MEMORY.md 等列表保护 | **高优先级**: BlueCortexCE 的回滚操作应保护用户的关键文件 | 高 |
| 三模式回滚 | none / stash / hard | **高优先级**: BlueCortexCE 的危险操作应有回滚模式开关 | 高 |
| Baseline 比较 | 只删除新增文件 | **高优先级**: BlueCortexCE 的清理操作应比较"操作前后"状态 | 高 |
| 路径穿越防护 | `normAbs.startsWith(normRepo + path.sep)` | **高优先级**: BlueCortexCE 的文件操作应防路径穿越 | 高 |
| Diff 快照保存 | rollback 前保存 diff_snapshot | **高优先级**: BlueCortexCE 的失败记录应保存完整的变更上下文 | 高 |
| 空目录清理 | 从深到浅排序删除 | **中优先级**: BlueCortexCE 的文件清理应有目录树清理逻辑 | 中 |

---

## 69. bridge.js — 跨 Agent 协作桥接（v1.3 新增）

**文件**: `src/gep/bridge.js` (71 lines)

### 69.1 设计定位

bridge.js 实现 **Bridge 模式**——当 Evolver 需要调用外部 Agent（Claude Code、OpenClaw subagent 等）执行具体任务时，通过 `sessions_spawn` 桥接，并在本地记录 Prompt Artifact。

### 69.2 Prompt Artifact 持久化

**文件**: `bridge.js:25-60`

```javascript
function writePromptArtifact({ memoryDir, cycleId, runId, prompt, meta }) {
  const safeCycle = String(cycleId || 'cycle').replace(/[^a-zA-Z0-9_\-#]/g, '_');
  const safeRun = String(runId || Date.now()).replace(/[^a-zA-Z0-9_\-]/g, '_');
  const base = `gep_prompt_${safeCycle}_${safeRun}`;
  
  const promptPath = path.join(dir, base + '.txt');
  const metaPath = path.join(dir, base + '.json');
  
  fs.writeFileSync(promptPath, String(prompt || ''), 'utf8');
  fs.writeFileSync(metaPath, JSON.stringify({
    type: 'GepPromptArtifact',
    at: nowIso(),
    cycle_id: cycleId,
    run_id: runId,
    prompt_path: promptPath,
    meta: meta && typeof meta === 'object' ? meta : null,
  }, null, 2) + '\n', 'utf8');
  
  return { promptPath, metaPath };
}
```

**Evolver 为什么这样做**：
- Prompt 是发送给 LLM 的"决策依据"，需要持久化用于审计
- Meta JSON 记录元数据（时间戳、cycle ID、run ID）
- 两者分离：纯文本 prompt + 结构化 meta

### 69.3 sessions_spawn 调用渲染

**文件**: `bridge.js:62-80`

```javascript
function renderSessionsSpawnCall({ task, agentId, label, cleanup }) {
  // 输出 JSON 格式的调用，wrapper 可用 JSON.parse 解析
  const payload = JSON.stringify({ task: t, agentId: a, cleanup: c, label: l });
  return `sessions_spawn(${payload})`;
}
```

**Evolver 为什么这样做**：
- 输出 `sessions_spawn(JSON.stringify({...}))` 格式，wrapper 可用 `JSON.parse` 提取参数
- 比正则提取更可靠——避免特殊字符导致的解析错误
- `cleanup: 'delete'` 确保 subagent 会话执行后自动清理

### 69.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| Prompt Artifact 持久化 | GEP Prompt → gep_prompt_${cycle}_${run}.txt | **高优先级**: BlueCortexCE 的 context generate 应能持久化输入 Prompt | 高 |
| Meta JSON 分离 | 纯文本 + 结构化 JSON 分离 | **中优先级**: BlueCortexCE 的日志应分离"内容"和"元数据" | 中 |
| JSON 格式的 spawn call | `sessions_spawn(JSON.stringify({...}))` | **高优先级**: BlueCortexCE 的 subagent 调用应使用 JSON 解析而非正则 | 高 |
| 清理策略 | cleanup: 'delete' 自动清理会话 | **中优先级**: BlueCortexCE 的 subagent 应有会话生命周期管理 | 中 |

---

## 70. a2a.js — A2A 资产广播与置信度管理（v1.3 新增）

**文件**: `src/gep/a2a.js` (193 lines)

### 70.1 设计定位

a2a.js 是 **A2A 资产层**的轻量工具模块，负责：
1. 外部资产置信度降级（`lowerConfidence`）
2. 广播资格判定（`isCapsuleBroadcastEligible` / `isGeneBroadcastEligible`）
3. A2A 消息解析（`parseA2AInput`）

### 70.2 外部资产置信度降级（lowerConfidence）

**文件**: `a2a.js:35-70`

```javascript
function lowerConfidence(asset, opts) {
  var factor = Number.isFinite(Number(opts.factor)) ? Number(opts.factor) : 0.6;
  var receivedFrom = opts.source || 'external';
  var receivedAt = opts.received_at || nowIso();
  
  var cloned = JSON.parse(JSON.stringify(asset || {}));
  if (!isAllowedA2AAsset(cloned)) return null;
  
  if (cloned.type === 'Capsule') {
    if (typeof cloned.confidence === 'number')
      cloned.confidence = clamp01(cloned.confidence * factor);
  }
  
  cloned.a2a = {
    status: 'external_candidate',
    source: receivedFrom,
    received_at: receivedAt,
    confidence_factor: factor,
  };
  
  return cloned;
}
```

**Evolver 为什么这样做**：从 Hub 获取的外部资产，其 `confidence` 需要降权（factor = 0.6）。这是"信任递减"原则——外部资产不如本地验证过的资产可信。

### 70.3 Capsule 广播资格判定

**文件**: `a2a.js:90-115`

```javascript
function isCapsuleBroadcastEligible(capsule, opts) {
  if (!capsule || capsule.type !== 'Capsule') return false;
  
  // 1. 评分门槛：score >= 0.7
  var score = capsule.outcome?.score;
  if (score == null || score < 0.7) return false;
  
  // 2. Blast radius 安全检查
  var blast = capsule.blast_radius || capsule.outcome?.blast_radius;
  if (!isBlastRadiusSafe(blast)) return false;
  
  // 3. 连续成功 streak >= 2
  var streak = computeCapsuleSuccessStreak({ capsuleId: capsule.id, events });
  if (streak < 2) return false;
  
  return true;
}
```

**三门控设计**：
- **评分门槛**：防止低质量 Capsule 污染 Hub
- **Blast radius 检查**：影响范围过大的 Capsule 不适合共享（可能包含敏感项目代码）
- **连续成功 streak**：确保不是偶然成功，而是稳定可复现

### 70.4 Gene 广播资格判定

**文件**: `a2a.js:118-130`

```javascript
function isGeneBroadcastEligible(gene) {
  if (!gene || gene.type !== 'Gene') return false;
  if (!gene.id || typeof gene.id !== 'string') return false;
  if (!Array.isArray(gene.strategy) || gene.strategy.length === 0) return false;
  if (!Array.isArray(gene.validation) || gene.validation.length === 0) return false;
  return true;
}
```

**Evolver 为什么这样做**：Gene 必须有 `strategy`（策略步骤）和 `validation`（验证命令）才能被 Hub 评审。没有验证步骤的 Gene 质量不可靠。

### 70.5 A2A 消息解析（parseA2AInput）

**文件**: `a2a.js:135-175`

```javascript
function parseA2AInput(text) {
  // 支持多种格式：
  // 1. JSON array: [...]
  // 2. JSON object: {...}
  // 3. JSONL 格式: 每行一个 JSON 对象
  
  var raw = String(text || '').trim();
  if (!raw) return [];
  
  try {
    var maybe = JSON.parse(raw);
    if (Array.isArray(maybe)) {
      return maybe.map(item => unwrapAssetFromMessage(item) || item).filter(Boolean);
    }
    if (maybe && typeof maybe === 'object') {
      var unwrapped = unwrapAssetFromMessage(maybe);
      return unwrapped ? [unwrapped] : [maybe];
    }
  } catch {}
  
  // JSONL fallback
  var lines = raw.split('\n').map(l => l.trim()).filter(Boolean);
  var items = [];
  for (const line of lines) {
    try {
      var obj = JSON.parse(line);
      items.push(unwrapAssetFromMessage(obj) || obj);
    } catch { continue; }
  }
  return items;
}
```

**Evolver 为什么这样做**：Hub 返回的资产数据可能有多种格式（JSON array、JSON object、JSONL），统一解析层让下游代码不需要处理格式差异。

### 70.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 外部资产降权 | confidence × 0.6 | **高优先级**: BlueCortexCE 从外部（Hub/其他节点）获取的检索结果应降权 | 高 |
| 广播三门控 | score ≥ 0.7 + blast radius + streak ≥ 2 | **高优先级**: BlueCortexCE 的"发布/共享"功能应有质量门槛 | 高 |
| Gene 必填字段 | strategy + validation 必须存在 | **高优先级**: BlueCortexCE 的 Summary 如果要共享，应有最低字段要求 | 高 |
| 多格式解析 | JSON array / object / JSONL 统一 | **中优先级**: BlueCortexCE 的 API 应能处理多种输入格式 | 中 |
| confidence_factor 记录 | 记录降权因子 | **中优先级**: BlueCortexCE 的外部数据应有"来源可信度"元数据 | 中 |

---

## 71. privacyClient.js — 隐私计算与密封执行（v1.3 新增）

**文件**: `src/gep/privacyClient.js` (216 lines)

### 71.1 设计背景

privacyClient.js 实现 **隐私计算协议**——当某个任务需要处理敏感数据（如代码审计、日志分析）但又希望借助 Hub 的知识时，可以使用"密封执行"（Sealed Execution）模式：
1. 数据在本地加密后上传到 Hub
2. Hub 在加密 blob 上执行密封工具（不知道明文内容）
3. 结果返回本地后解密
4. Hub 始终不知道原始数据内容

### 71.2 核心流程

```
本地                    Hub                     本地
 |                       |                       |
 |-- submitPrivacyTask --&gt;|                       |
 |                       |                       |
 |&lt;-- taskId ----------|                       |
 |                       |                       |
 |-- uploadEncryptedBlob -| (blobId)             |
 |                       |                       |
 |            (Hub 对 blob 执行 sealed tool)      |
 |                       |                       |
 |-- executeSealedTool -|&gt;|                       |
 |                       |&lt;-- result -----------|
 |                       |                       |
 |-- getPrivacyResult --&gt;| (encrypted)          |
 |&lt;-- encrypted_result --|                       |
 |                       |                       |
(decrypt locally)        |                       |
 V                       |                       |
 plaintext result        |                       |
```

### 71.3 加密 blob 上传（uploadEncryptedBlob）

**文件**: `privacyClient.js:50-85`

```javascript
async function uploadEncryptedBlob(plaintext, opts) {
  const key = generateKey();          // 本地生成 AES-256-GCM 密钥
  const parts = encrypt(plaintext, key);  // 加密
  const packed = pack(parts);         // IV + authTag + ciphertext 打包
  
  const res = await fetch(privacyUrl('/blob/upload'), {
    body: JSON.stringify({
      data_base64: packed.toString('base64'),
      encryption: 'aes-256-gcm',
    }),
  });
  
  return {
    blobId: resp.blob_id,  // Hub 返回 blob ID
    key,                    // 本地保留密钥（关键！）
    iv: parts.iv,
    authTag: parts.authTag,
  };
}
```

**关键设计**：密钥 `key` **永远不发送**给 Hub。只有 Hub 收到加密数据，但无法解密。

### 71.4 密封工具执行（executeSealedTool）

**文件**: `privacyClient.js:90-115`

```javascript
async function executeSealedTool(opts) {
  // Hub 对加密 blob 执行 tool，不解密内容
  const res = await fetch(privacyUrl('/tool/execute'), {
    body: JSON.stringify({
      toolId: opts.toolId,   // 工具 ID（明文，Hub 可见）
      blobId: opts.blobId,  // 加密 blob ID（Hub 可见）
    }),
  });
  
  return { resultKey, resultHash, error };
}
```

**Evolver 为什么这样做**：`toolId` 是明文的（Hub 需要知道运行什么工具），`blobId` 指向加密数据（Hub 看不到内容）。这相当于"盲计算"——Hub 知道你在做什么工具，但不知道处理的是什么数据。

### 71.5 结果解密（getPrivacyResult）

**文件**: `privacyClient.js:130-155`

```javascript
async function getPrivacyResult(taskId, key) {
  const res = await fetch(privacyUrl(`/result/${taskId}`));
  const data = await res.json();
  
  // encrypted_result_base64 是 Hub 返回的加密结果
  const packed = Buffer.from(data.encrypted_result_base64, 'base64');
  const parts = unpack(packed);
  
  // 本地用本地密钥解密——Hub 不知道结果
  const plaintext = decrypt(parts.ciphertext, key, parts.iv, parts.authTag);
  return { plaintext, resultHash: data.result_hash };
}
```

### 71.6 隐私参数解析（parsePrivacyParams）

**文件**: `privacyClient.js:185-215`

```javascript
function parsePrivacyParams(body) {
  // 从 task body 中提取 [PRIVACY_PARAMS] 块
  // [PRIVACY_PARAMS]
  // tool_id: my_tool
  // blob_ids: blob1,blob2
  // [/PRIVACY_PARAMS]
  
  const block = body.substring(start + 16, end).trim();
  // 解析 key: value 行
  // 返回 { toolId, blobIds[] }
}
```

**Evolver 为什么这样做**：Hub 下发的任务 body 中可以包含隐私计算指令，Evolver 解析后执行对应的密封工具。

### 71.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 本地密钥生成 | key 从不离开客户端 | **高优先级**: BlueCortexCE 的隐私计算必须确保密钥不泄露 | 高 |
| 密封执行 | Hub 看到 toolId 但看不到 blob 内容 | **高优先级**: BlueCortexCE 处理敏感数据时可用类似模式 | 中 |
| 加密 blob 上传 | AES-256-GCM + pack(IV+authTag+ciphertext) | **中优先级**: BlueCortexCE 的隐私数据存储可参考此格式 | 中 |
| 结果本地解密 | Hub 返回加密结果，客户端解密 | **高优先级**: BlueCortexCE 的"云端处理"结果应在本地解密 | 高 |
| 隐私参数块 | [PRIVACY_PARAMS] 标签格式 | **低优先级**: BlueCortexCE 的任务描述格式可支持隐私标记 | 低 |

---

## 72. assets.js — 资产格式统一抽象（v1.3 新增）

**文件**: `src/gep/assets.js` (36 lines)

### 72.1 资产预览格式化（formatAssetPreview）

**文件**: `assets.js:8-35`

```javascript
function formatAssetPreview(preview) {
  if (!preview) return '(none)';
  if (typeof preview === 'string') {
    try {
      const parsed = JSON.parse(preview);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return JSON.stringify(parsed, null, 2);
      }
      return preview;
    } catch { return preview; }
  }
  return JSON.stringify(preview, null, 2);
}
```

**Evolver 为什么这样做**：资产预览可能是字符串（JSON 字符串）、数组或对象。统一格式化逻辑让 prompt 注入时的输出保持一致。

### 72.2 资产规范化（normalizeAsset）

**文件**: `assets.js:37-45`

```javascript
function normalizeAsset(asset) {
  if (!asset || typeof asset !== 'object') return asset;
  if (!asset.schema_version) asset.schema_version = SCHEMA_VERSION;
  if (!asset.asset_id) {
    try { asset.asset_id = computeAssetId(asset); } catch {}
  }
  return asset;
}
```

**Evolver 为什么这样做**：发布到 Hub 之前，自动补全 `schema_version` 和 `asset_id`——避免因缺失字段被 Hub 拒绝。

### 72.3 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 资产规范化 | 写入前补全 schema_version + asset_id | **高优先级**: BlueCortexCE 的任何写入前应自动补全元数据 | 高 |
| 预览格式化 | 字符串/数组/对象统一 JSON 格式化 | **中优先级**: BlueCortexCE 的 API 响应格式化应统一处理不同类型 | 中 |

---

## 73. candidates.js — 能力候选提取算法（v1.4 新增）

**文件**: `src/gep/candidates.js` (225 lines)

### 73.1 核心设计思想

Evolver 的 `candidates.js` 实现了**从失败和成功经验中自动发现可复用的能力模式**的算法。它从三个来源提取能力候选：

| 来源 | 触发条件 | 候选类型 |
|------|----------|---------|
| **Transcript 工具调用** | 同一工具调用 ≥3 次 | `CapabilityCandidate` |
| **Signal 模式** | 特定 signal 出现时 | `CapabilityCandidate` |
| **Failed Capsules** | 同类失败 ≥2 次 | `CapabilityCandidate` |

### 73.2 工具调用频率提取（extractToolCalls）

```javascript
// candidates.js:28-40
function extractToolCalls(transcript) {
  const lines = toLines(transcript);
  const calls = [];
  for (const line of lines) {
    // OpenClaw format: [TOOL: Shell]
    const m = line.match(/\[TOOL:\s*([^\]]+)\]/i);
    if (m && m[1]) { calls.push(m[1].trim()); continue; }
    // Cursor transcript format: [Tool call] Shell
    const m2 = line.match(/\[Tool call\]\s+(\S+)/i);
    if (m2 && m2[1]) calls.push(m2[1].trim());
  }
  return calls;
}
```

**Evolver 为什么这样做**：从 session transcript 中提取工具调用模式，识别"重复使用的工具"作为能力候选。频率 ≥3 才触发（避免噪声）。

### 73.3 Five Questions Shape 模板

每个候选都转换为**五问模板**，用于指导后续的 Gene 生成：

```javascript
// candidates.js:48-60
function buildFiveQuestionsShape({ title, signals, evidence }) {
  return {
    title: String(title || '').slice(0, 120),
    input: 'Recent session transcript + memory snippets + user instructions',
    output: 'A safe, auditable evolution patch guided by GEP assets',
    invariants: 'Protocol order, small reversible patches, validation, append-only events',
    params: `Signals: ${Array.isArray(signals) ? signals.join(', ') : ''}`.trim(),
    failure_points: 'Missing signals, over-broad changes, skipped validation, missing knowledge solidification',
    evidence: clip(evidence, 240),
  };
}
```

**五问**：
1. **Input** — 什么输入触发了这个能力？
2. **Output** — 期望的输出是什么？
3. **Invariants** — 必须保持不变的条件是什么？
4. **Params** — 与哪些 signals 相关？
5. **Failure Points** — 常见的失败点是什么？

### 73.4 Signal 驱动的候选生成

```javascript
// candidates.js:75-98
const signalCandidates = [
  // Defensive signals
  { signal: 'log_error', title: 'Repair recurring runtime errors' },
  { signal: 'protocol_drift', title: 'Prevent protocol drift and enforce auditable outputs' },
  { signal: 'windows_shell_incompatible', title: 'Avoid platform-specific shell assumptions (Windows compatibility)' },
  { signal: 'session_logs_missing', title: 'Harden session log detection and fallback behavior' },
  // Opportunity signals (innovation)
  { signal: 'user_feature_request', title: 'Implement user-requested feature' },
  { signal: 'user_improvement_suggestion', title: 'Apply user improvement suggestion' },
  { signal: 'perf_bottleneck', title: 'Resolve performance bottleneck' },
  { signal: 'capability_gap', title: 'Fill capability gap' },
  { signal: 'stable_success_plateau', title: 'Explore new strategies during stability plateau' },
  { signal: 'external_opportunity', title: 'Evaluate external A2A asset for local adoption' },
];
```

**Evolver 为什么这样做**：将 signal 模式直接映射为候选能力——当检测到特定 signal 时，自动生成对应的能力候选，驱动进化循环。

### 73.5 Failed Capsules 分组聚合

```javascript
// candidates.js:103-145
var groups = {};
var problemPriority = [
  'problem:performance',
  'problem:protocol',
  'problem:reliability',
  'problem:stagnation',
  'problem:capability',
];
for (var i = 0; i < failedCapsules.length; i++) {
  var fc = failedCapsules[i];
  if (!fc || fc.outcome && fc.outcome.status === 'success') continue;
  var reason = String(fc.failure_reason || '').trim();
  var failureTags = expandSignals((fc.trigger || []).concat(signalList), reason)
    .filter(function (t) {
      return t.indexOf('problem:') === 0 || t.indexOf('risk:') === 0 ||
             t.indexOf('area:') === 0 || t.indexOf('action:') === 0;
    });
  if (failureTags.length === 0) continue;
  var dominantProblem = null;
  for (var p = 0; p < problemPriority.length; p++) {
    if (failureTags.indexOf(problemPriority[p]) !== -1) {
      dominantProblem = problemPriority[p];
      break;
    }
  }
  // ...
}
```

**Evolver 为什么这样做**：将相似失败模式的 Capsule 聚合分组，识别"反复失败的进化路径"作为学习机会。同一问题类型出现 ≥2 次才生成候选。

### 73.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 工具调用频率提取 | transcript 中提取 `[TOOL: xxx]` 模式，≥3 次触发 | **高优先级**: BlueCortexCE 可从 session transcript 中提取重复行为模式 | 高 |
| 五问模板 | 候选转换为 input/output/invariants/params/failure_points | **高优先级**: BlueCortexCE Observation 可增加 structured template | 中 |
| Signal 驱动候选 | signal → capability candidate 自动映射 | **中优先级**: BlueCortexCE 可基于 signal 类型生成 structured extraction | 中 |
| Failed 聚合 | 失败 Capsule 按 problem type 分组，≥2 次触发 | **中优先级**: BlueCortexCE 的 `/api/sessions/{id}/failed` 可做类似聚合 | 低 |
| 确定性哈希 | `stableHash()` 用于去重 ID 生成 | **高优先级**: BlueCortexCE 的 entity ID 生成应使用确定性哈希 | 高 |

---

## 74. candidateEval.js — 候选预演构建与外部资产匹配（v1.4 新增）

**文件**: `src/gep/candidateEval.js` (107 lines)

### 74.1 buildCandidatePreviews 函数

`candidateEval.js` 的核心是 `buildCandidatePreviews` 函数，它：

1. 从当前 session 的 transcript 和 signals 生成新候选
2. 持久化候选到 `assetStore`
3. 读取最近的本地和外部候选
4. 构建供 GEP prompt 使用的预览文本

```javascript
// candidateEval.js:13-25
function buildCandidatePreviews({ signals, recentSessionTranscript }) {
  // Step 1: 提取新候选
  const newCandidates = extractCapabilityCandidates({
    recentSessionTranscript: recentSessionTranscript || '',
    signals,
    recentFailedCapsules: readRecentFailedCapsules(50),
  });
  // Step 2: 持久化
  for (const c of newCandidates) {
    try { appendCandidateJsonl(c); } catch (e) { ... }
  }
  // Step 3: 读取本地候选
  const recentCandidates = readRecentCandidates(20);
  const capabilityCandidatesPreview = renderCandidatesPreview(recentCandidates.slice(-8), 1600);
  // Step 4: 读取外部候选 + 信号匹配
  let externalCandidatesPreview = '(none)';
  // ...
}
```

### 74.2 外部 Gene 与 Capsule 的信号匹配

```javascript
// candidateEval.js:35-55
const matchedExternalGenes = genesOnly
  .map(g => {
    const pats = Array.isArray(g.signals_match) ? g.signals_match : [];
    const hit = pats.reduce((acc, p) => (matchPatternToSignals(p, signals) ? acc + 1 : acc), 0);
    return { gene: g, hit };
  })
  .filter(x => x.hit > 0)
  .sort((a, b) => b.hit - a.hit)
  .slice(0, 3)
  .map(x => x.gene);

const matchedExternalCapsules = capsulesOnly
  .map(c => {
    const triggers = Array.isArray(c.trigger) ? c.trigger : [];
    const score = triggers.reduce((acc, t) => (matchPatternToSignals(t, signals) ? acc + 1 : acc), 0);
    return { capsule: c, score };
  })
  .filter(x => x.score > 0)
  .sort((a, b) => b.score - a.score)
  .slice(0, 3)
  .map(x => x.capsule);
```

**Evolver 为什么这样做**：
- 从 Hub 同步的外部 Gene/Capsule，按当前 signals 匹配度排序
- 取 top-3 作为预演内容，让 GEP prompt 知道外部有什么可用资产
- 这是**联邦知识发现**的关键环节

### 74.3 预览格式化输出

```javascript
// candidateEval.js:60-90
externalCandidatesPreview = `\`\`\`json\n${JSON.stringify(
  [
    ...matchedExternalGenes.map(g => ({
      type: g.type,
      id: g.id,
      category: g.category || null,
      signals_match: g.signals_match || [],
      a2a: g.a2a || null,
    })),
    ...matchedExternalCapsules.map(c => ({
      type: c.type,
      id: c.id,
      trigger: c.trigger,
      gene: c.gene,
      summary: c.summary,
      confidence: c.confidence,
      blast_radius: c.blast_radius || null,
      outcome: c.outcome || null,
      success_streak: c.success_streak || null,
      a2a: c.a2a || null,
    })),
  ],
  null, 2
)}\n\`\`\``;
```

**Evolver 为什么这样做**：输出 JSON 格式便于 LLM 解析，包含 type/id/trigger/summary/confidence 等关键字段。

### 74.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 外部资产信号匹配 | genes/capsules 按 signals 匹配度排序，取 top-3 | **高优先级**: BlueCortexCE 的 `/api/search` 可增加"信号匹配度"排序 | 高 |
| 联邦知识发现 | Hub 同步 + 本地匹配 | **中优先级**: BlueCortexCE 可实现多实例联邦搜索 | 中 |
| 预览格式化 JSON | JSON 输出便于 LLM 解析 | **中优先级**: BlueCortexCE 的 context 输出可增加结构化 JSON 块 | 中 |
| 候选持久化 | appendCandidateJsonl 异步写入 | **高优先级**: BlueCortexCE 的候选observation应有异步写入机制 | 高 |

---

## 75. skillPublisher.js — Gene 到 SKILL.md 格式转换与 Hub 发布（v1.4 新增）

**文件**: `src/gep/skillPublisher.js` (307 lines)

### 75.1 核心设计思想

`skillPublisher.js` 实现将 **Gene 资产转换为可发布的 SKILL.md 格式**并发布到 Hub 的完整流程。这是 Evolver 知识变现的核心环节：

```
Gene (内部资产) → SKILL.md (Hub 发布格式) → Hub (联邦知识市场)
```

### 75.2 Gene → SKILL.md 格式转换（geneToSkillMd）

```javascript
// skillPublisher.js:67-135
function geneToSkillMd(gene) {
  var name = sanitizeSkillName(gene.id) || deriveFallbackName(gene);
  var displayName = toTitleCase(name);
  var lines = [
    '---',
    'name: ' + displayName,
    'description: ' + desc,
    '---',
    '',
    '# ' + displayName,
    '',
    '## When to Use',
    '- When your project encounters: ' + gene.signals_match.slice(0, 4).map(...).join(', '),
    '',
    '## Trigger Signals',
    gene.signals_match.forEach(s => lines.push('- `' + s + '`')),
    '',
    '## Preconditions',
    gene.preconditions.forEach(p => lines.push('- ' + p)),
    '',
    '## Strategy',
    gene.strategy.map((step, i) => (i+1) + '. **' + extractStepVerb(step) + '** -- ' + stripLeadingVerb(step)),
    '',
    '## Constraints',
    // constraints.max_files, constraints.forbidden_paths
    '',
    '## Validation',
    gene.validation.map(cmd => '```bash\n' + cmd + '\n```'),
    '',
    '## Metadata',
    '- Category: `' + gene.category + '`',
    '- Schema version: `' + gene.schema_version + '`',
    '- Distilled from: ' + gene._distilled_meta.source_capsule_count + ' successful capsules',
  ];
  return lines.join('\n');
}
```

**SKILL.md 结构**：

| Section | 内容 |
|---------|------|
| Frontmatter | name, description (YAML) |
| When to Use | 触发条件（signals） |
| Trigger Signals | 信号列表 |
| Preconditions | 前置条件 |
| Strategy | 步骤列表（动词 bold 化） |
| Constraints | 约束（文件数限制、禁止路径） |
| Validation | 验证命令 |
| Metadata | 类别、版本、来源 |

### 75.3 技能名称清洗（sanitizeSkillName）

```javascript
// skillPublisher.js:13-28
function sanitizeSkillName(rawName) {
  var name = rawName.replace(/[\r\n]+/g, '-')
                     .replace(/^gene_distilled_/, '')
                     .replace(/^gene_/, '')
                     .replace(/_/g, '-');
  // Strip ALL embedded timestamps (10+ digit sequences)
  name = name.replace(/-?\d{10,}-?/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
  // 过滤工具名和纯数字
  if (/^\d{8,}/.test(name) || /^(cursor|vscode|vim|emacs|windsurf|copilot|cline|codex)[-]?\d*$/i.test(name)) {
    return null;
  }
  if (name.replace(/[-]/g, '').length < 6) return null;
  return name;
}
```

**Evolver 为什么这样做**：
- 去除 `gene_distilled_` 和 `gene_` 前缀
- 将下划线转为连字符（kebab-case）
- 去除嵌入的时间戳
- 过滤工具名和纯数字

### 75.4 动词提取（extractStepVerb）

```javascript
// skillPublisher.js:157-168
function extractStepVerb(step) {
  // Only match a capitalized verb at the very start
  var match = step.match(/^([A-Z][a-z]+)/);
  return match ? match[1] : '';
}

function stripLeadingVerb(step) {
  var verb = extractStepVerb(step);
  if (verb && step.startsWith(verb)) {
    var rest = step.slice(verb.length).replace(/^[\s:.\-]+/, '');
    return rest || step;
  }
  return step;
}
```

**Evolver 为什么这样做**：策略步骤格式为 "Verb -- rest"，展示时动词 bold 化，让格式更易读。

### 75.5 Hub 发布流程（publishSkillToHub）

```javascript
// skillPublisher.js:180-230
function publishSkillToHub(gene, opts) {
  var hubUrl = getHubUrl();
  if (!hubUrl) return Promise.resolve({ ok: false, error: 'no_hub_url' });

  var content = geneToSkillMd(geneCopy);
  var skillId = 'skill_' + derivedName.replace(/_?\d{10,}_?/g, '_').replace(/_+/g, '_');
  var body = {
    sender_id: nodeId,
    skill_id: skillId,
    content: content,
    category: opts.category || geneCopy.category || null,
    tags: tags,
  };

  var endpoint = hubUrl + '/a2a/skill/store/publish';
  return fetch(endpoint, {
    method: 'POST',
    headers: buildHubHeaders(),
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  })
    .then(function (res) {
      if (res.status === 201 || res.status === 200) {
        return { ok: true, result: result.data };
      }
      if (res.status === 409) {
        return updateSkillOnHub(nodeId, skillId, content, opts, gene); // 已存在则更新
      }
      return { ok: false, error: result.data?.error || 'publish_failed' };
    });
}
```

**Evolver 为什么这样做**：
- `409 Conflict` 时自动触发 `updateSkillOnHub`（版本迭代）
- 15s 超时防止 Hub 无响应阻塞
- `AbortSignal.timeout()` 现代 API

### 75.6 标签清洗（sanitizeSignalsMatch）

```javascript
// skillPublisher.js:196-203
var tags = opts.tags || geneCopy.signals_match || [];
tags = tags.filter(function (t) {
  var s = String(t || '').trim();
  return s.length >= 3 && !/^\d+$/.test(s) && !/\d{10,}/.test(s);
});
```

**Evolver 为什么这样做**：过滤纯数字和时间戳标签，防止 Hub 拒绝或排序异常。

### 75.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 技能格式化 | Gene → SKILL.md (frontmatter + sections) | **高优先级**: BlueCortexCE 可将 Observation 导出为 SKILL.md 格式 | 高 |
| 技能名称清洗 | kebab-case + 去除时间戳 | **高优先级**: BlueCortexCE 的 asset 名称应有规范化逻辑 | 高 |
| Hub 发布 | POST → 409 → PUT 自动版本更新 | **中优先级**: BlueCortexCE 的资产发布可参考此幂等模式 | 中 |
| Verb bold 化 | 策略步骤 "Verify -- installation" → "**Verify** -- installation" | **中优先级**: BlueCortexCE 的 structured output 可类似格式化 | 中 |
| 来源追溯 | `_distilled_meta.source_capsule_count` 标注成功 Capsule 数量 | **高优先级**: BlueCortexCE 的 Observation 应记录来源 session | 高 |

---

## 76. 下轮探索方向（v1.4 更新）

### 高优先级
1. ~~**a2aProtocol.js**~~ ✅ v1.1 已新增（联邦通信协议、HMAC 签名、双传输层、心跳机制、SSE 事件流）
2. ~~**skillPublisher.js**~~ ✅ v1.4 已新增（Gene→SKILL.md、Hub发布、名称清洗）
3. **taskReceiver.js** — Hub 任务接收与处理（未找到文件，可能是旧版本已移除）

### 中优先级
4. ~~**privacyClient.js**~~ ✅ v1.3 已新增（隐私计算协议、密封执行、加密 blob）
5. ~~**gitOps.js**~~ ✅ v1.3 已新增（Git 操作、关键文件保护、原子回滚）
6. ~~**bridge.js**~~ ✅ v1.3 已新增（Prompt Artifact、sessions_spawn 渲染）
7. ~~**a2a.js**~~ ✅ v1.3 已新增（A2A 资产广播资格、置信度降权）
8. ~~**assets.js**~~ ✅ v1.3 已新增（资产格式化规范化）
9. **llmReview.js** (92 lines) — LLM 代码审查集成

### 待深入分析（v1.4 更新）

**已分析文件**：
1. ~~**hubSearch.js**~~ ✅ v1.0（两阶段搜索、多层缓存、联邦知识市场）
2. ~~**hubReview.js**~~ ✅ v1.0（使用验证型评价、去重机制、非阻塞设计）
3. ~~**executionTrace.js**~~ ✅ v1.0（隐私保护遥测、脱敏规则、blast 分级）
4. ~~**assetCallLog.js**~~ ✅ v1.0（append-only JSONL 审计、多维过滤）
5. ~~**directoryClient.js**~~ ✅ v1.0（节点目录、语义发现、能力标签）
6. ~~**deviceId.js**~~ ✅ v1.0（优先级指纹链、容器感知、权限安全）
7. ~~**a2aProtocol.js**~~ ✅ v1.1（联邦通信协议、HMAC 签名、双传输层抽象、心跳注册、SSE 事件流）
8. ~~**gitOps.js**~~ ✅ v1.3（Git 操作与回滚、关键文件保护、三模式回滚）
9. ~~**bridge.js**~~ ✅ v1.3（Prompt Artifact 持久化、sessions_spawn JSON 渲染）
10. ~~**a2a.js**~~ ✅ v1.3（A2A 资产广播资格、三门控设计、置信度降权）
11. ~~**privacyClient.js**~~ ✅ v1.3（隐私计算协议、密封执行、本地密钥管理）
12. ~~**assets.js**~~ ✅ v1.3（资产格式统一抽象、规范化写入）
13. ~~**candidates.js**~~ ✅ v1.4（能力候选提取、工具频率、Signal驱动、Failed聚合）
14. ~~**candidateEval.js**~~ ✅ v1.4（候选预演构建、外部资产信号匹配）
15. ~~**skillPublisher.js**~~ ✅ v1.4（Gene→SKILL.md、Hub发布幂等模式）

**待深入分析文件**：
16. **llmReview.js** (92 lines) — LLM 代码审查集成
17. **assetStore.js** (14,600 bytes) — 资产存储与读取（candidateEval 依赖）
18. ~~**sanitize.js**~~ ✅ v0.9 已新增
19. ~~**contentHash.js**~~ ✅ v0.9 已新增
20. ~~**crypto.js**~~ ✅ v0.9 已新增
21. ~~**envFingerprint.js**~~ ✅ v0.9 已新增
22. ~~**issueReporter.js**~~ ✅ v0.9 已新增
23. ~~**validationReport.js**~~ ✅ v0.9 已新增
24. ~~**analyzer.js**~~ ✅ v0.9 已新增

