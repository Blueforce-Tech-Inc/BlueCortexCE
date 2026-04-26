# `58` v1.66 新架构分析：三层信号 + ATP + Adapters + Self-PR

**v1.47 → v1.66 架构跨越分析**

**目标**：记录 v1.66.0 相比当前分析的 v1.47.0 引入的重大新架构：三层信号提取、平台适配器系统、ATP（Agent Trading Protocol）、Self-PR 自提交机制。

**数据来源**：`/Users/yangjiefeng/Documents/EvoMap/evolver` @ v1.66.0

**最后更新**：2026-04-25

---

## 目录

- [§1 三层信号提取架构](#s1-三层信号提取架构)
- [§2 新增 Opportunity Signals](#s2-新增-opportunity-signals)
- [§3 Plateau 检测](#s3-plateau-检测)
- [§4 平台适配器系统（Adapters）](#s4-平台适配器系统adapters)
- [§5 ATP（Agent Trading Protocol）](#s5-atpagent-trading-protocol)
- [§6 Self-PR 自动提交机制](#s6-self-pr-自动提交机制)
- [§7 主要模块精简（evolve.js / memoryGraph.js / a2aProtocol.js）](#s7-主要模块精简evolvejs--memorygraphjs--a2aprotocoljs)
- [§8 CE 借鉴路径](#s8-ce-借鉴路径)

---

## §1 三层信号提取架构

> ⚠️ **Doc 55 与 Doc 56 的关系**：Doc 55 描述 v1.66 三层提取架构（准确）；Doc 56 是对 v1.47 单层的现实核查（纠正 Doc 55 早期版本的误判）。本节为 Doc 55 的补充细节。

v1.66 的 `signals.js`（660 行，相比 v1.47 的 ~360 行增加了 302 行）实现了**三层信号提取管线**：

### Layer 1 — `_extractRegex`（正则，二值匹配）

继承自 v1.47 的原始 `extractSignals` 函数：对关键词和工具调用进行快速二值正则匹配。

```javascript
function _extractRegex(corpus) {
  // Binary hit/miss: keyword present → signal fires
  var signals = [];
  var lower = corpus.toLowerCase();
  // ... high-frequency tool calls, error patterns
}
```

### Layer 2 — `_extractKeywordScore`（加权关键词评分）

**核心思想**：与 regex 的二值匹配不同，评分机制通过**多个关键词的加权证据累积**来判断信号，只有当总分超过阈值时才触发。这能捕捉到**单一正则无法匹配的模糊/分散模式**。

```javascript
var SIGNAL_PROFILES = {
  perf_bottleneck: {
    keywords: {
      'slow': 3, 'timeout': 4, 'timed out': 4, 'latency': 3, 'bottleneck': 5,
      'lag': 2, 'freeze': 3, 'unresponsive': 4, 'high cpu': 4, 'oom': 5,
      'out of memory': 5, 'took too long': 4, ...
    },
    threshold: 6,
  },
  recurring_error: {
    keywords: {
      'error': 1, 'exception': 2, 'failed': 1, 'crash': 4,
      'again': 1, 'still': 1, 'keeps': 2, 'repeatedly': 4,
      'same error': 5, 'still failing': 5, 'not fixed': 4,
    },
    threshold: 7,
  },
  // ... 8 个 signal profiles
};

function _extractKeywordScore(lower) {
  var scored = [];
  for (var signalName in SIGNAL_PROFILES) {
    var profile = SIGNAL_PROFILES[signalName];
    var totalScore = 0;
    for (var kw in profile.keywords) {
      var weight = profile.keywords[kw];
      var count = 0, idx = 0;
      while (idx < lower.length && count < 20) {
        var pos = lower.indexOf(kw, idx);
        if (pos === -1) break;
        count++; idx = pos + kw.length;
      }
      totalScore += count * weight;
    }
    if (totalScore >= profile.threshold) {
      scored.push(signalName);
    }
  }
  return scored;
}
```

**关键设计**：
- 每个关键词有独立权重（`slow: 3` vs `bottleneck: 5`）
- 每个 signal 有阈值（`recurring_error` 需要 threshold=7，比 `perf_bottleneck` 的 6 更高）
- 每个关键词最多计数 20 次，避免单个高频词主导分数
- 捕捉模糊/分散模式：比如 "same error + again + still failing" 三者叠加才触发 `recurring_error`

### Layer 3 — `_extractLLM`（LLM 语义分析）

**核心思想**：将语料摘要发送到 Hub，由 LLM 进行深层语义信号提取。**限速**：每 5 个进化周期才调用一次，避免 Hub 压力。

```javascript
var _llmSignalCycleCount = 0;
var LLM_SIGNAL_INTERVAL = 5;

function _extractLLM(corpus) {
  _llmSignalCycleCount++;
  if (_llmSignalCycleCount % LLM_SIGNAL_INTERVAL !== 1) return [];
  // → 只有每第 6 个周期才真正调用 LLM

  // 使用 execSync + curl 发起同步 HTTP 请求
  // （Node 的异步 http.request 无法在同步 spin-wait 循环内触发回调）
  var curlCmd = 'curl -s -m 10 -X POST'
    + ' -H "Content-Type: application/json"'
    + ' -H "Authorization: Bearer ' + nodeSecret + '"'
    + ' -d ' + JSON.stringify({ corpus_summary: summary, signal_types: OPPORTUNITY_SIGNALS, sender_id: getNodeId() })
    + ' ' + JSON.stringify(url);

  var stdout = execSync(curlCmd, { timeout: 12000, ... });
  var parsed = JSON.parse(stdout);
  return parsed.signals.filter(...).slice(0, 10);
}
```

### `_mergeSignals` — 三路归一化合并

```javascript
function _mergeSignals(regexSignals, scoreSignals, llmSignals) {
  var merged = new Set();
  for (var s of regexSignals) merged.add(s);
  for (var s of scoreSignals) merged.add(s);
  for (var s of llmSignals) merged.add(s);

  var scoreOnly = scoreSignals.filter(s => !regexSignals.includes(s));
  var llmOnly    = llmSignals.filter(s => !regexSignals.includes(s) && !scoreSignals.includes(s));
  var overlap    = regexSignals.filter(s => scoreSignals.includes(s) || llmSignals.includes(s));

  console.log('[Signals] Multi-strategy: regex=N, score=N, llm=N, '
    + 'score_only=N, llm_only=N, overlap=N');
  // → 可观测性：每层贡献透明

  return Array.from(merged);
}
```

**可观测性日志**示例：
```
[Signals] Multi-strategy: regex=3, score=2, llm=1, score_only=1, llm_only=1, overlap=1
```

### 对比总结

| 层级 | 方法 | 特点 | 适用场景 |
|------|------|------|----------|
| Layer 1 `_extractRegex` | 二值正则 | 快速、精确 | 已知错误模式、工具调用 |
| Layer 2 `_extractKeywordScore` | 加权评分 | 模糊感知、阈值门控 | 性能瓶颈、重复错误、功能请求 |
| Layer 3 `_extractLLM` | LLM 语义 | 深层理解、跨模式 | 复杂意图、隐含机会、战略信号 |

---

## §2 新增 Opportunity Signals

v1.66 的 `OPPORTUNITY_SIGNALS` 数组从 v1.47 的 12 个增加到 19 个：

```javascript
var OPPORTUNITY_SIGNALS = [
  // 原有 12 个
  'user_feature_request', 'user_improvement_suggestion', 'perf_bottleneck',
  'capability_gap', 'stable_success_plateau', 'external_opportunity',
  'recurring_error', 'unsupported_input_type', 'evolution_stagnation_detected',
  'repair_loop_detected', 'force_innovation_after_repair_loop', 'tool_bypass',
  'curriculum_target',
  // v1.66 新增 7 个
  'issue_already_resolved',        // 问题已被其他方式解决
  'openclaw_self_healed',          // OpenClaw 自愈信号
  'empty_cycle_loop_detected',     // 空循环检测
  'explore_opportunity',           // 探索机会（饱和时探索触发）
  'hub_search_miss_with_problem',  // Hub 搜索未命中且有实际问题
  'plateau_pivot_required',        // 需要平台转换（分数低+无改善）
  'plateau_pivot_suggested',       // 建议平台转换
];
```

**`explore_opportunity` 注入机制**：
```javascript
// 在饱和检测块中，当连续空循环 >= 3 且没有 explore_opportunity 时注入
if (history.consecutiveEmptyCycles >= 3 && !signals.includes('explore_opportunity')) {
  signals.push('explore_opportunity');
}
// → idle gating 路径可触发主动探索，而非睡眠
```

---

## §3 Plateau 检测

v1.66 新增**基于近期 outcome score 趋势的平台检测**：

```javascript
// Plateau detection: recent scores trending down or stagnant
if (Array.isArray(recentEvents) && recentEvents.length >= 4) {
  var recentScores = recentEvents.slice(-6).map(function (e) {
    return e.outcome && typeof e.outcome.score === 'number' ? e.outcome.score : -1;
  }).filter(function (s) { return s >= 0; });

  if (recentScores.length >= 3) {
    var avgScore = recentScores.reduce(function (a, b) { return a + b; }, 0)
                    / recentScores.length;
    var improving = recentScores.length >= 2
                     && recentScores[recentScores.length - 1] > recentScores[recentScores.length - 2] + 0.05;

    if (avgScore < 0.35 && !improving) {
      signals.push('plateau_pivot_required');
    } else if (avgScore < 0.55 && !improving && history.consecutiveRepairCount >= 2) {
      signals.push('plateau_pivot_suggested');
    }
  }
}
```

**触发条件**：
- `plateau_pivot_required`: 平均分数 < 0.35 且近期无改善（评分趋势向下或停滞）
- `plateau_pivot_suggested`: 平均分数 < 0.55 + 无改善 + 连续修复次数 >= 2

**意义**：相比 v1.47 的"空循环计数"饱和检测，v1.66 增加了**分数趋势感知**，能在平台到来之前早期预警，而非仅在连续空循环堆积后才触发。

---

## §4 平台适配器系统（Adapters）

v1.66 引入 `src/adapters/` 目录，实现统一的跨平台 Hook 安装机制：

### 目录结构

```
src/adapters/
├── hookAdapter.js       # 统一适配器主入口
├── cursor.js            # Cursor 平台适配
├── claudeCode.js        # Claude Code 平台适配
├── codex.js             # Codex 平台适配
└── scripts/
    ├── evolver-session-start.js
    ├── evolver-session-end.js
    └── evolver-signal-detect.js
```

### `hookAdapter.js` 核心 API

```javascript
// 平台检测
function detectPlatform(cwd) {
  // 检测 .cursor / .claude / .codex 目录存在性
  // 返回 'cursor' | 'claude-code' | 'codex' | null
}

// 加载对应平台适配器
function loadAdapter(platformId) {
  switch (platformId) {
    case 'cursor': return require('./cursor');
    case 'claude-code': return require('./claudeCode');
    case 'codex': return require('./codex');
    default: return null;
  }
}

// 统一安装入口
async function setupHooks({ platform, cwd, force, uninstall, evolverRoot }) {
  const platformId = platform || detectPlatform(cwd);
  const adapter = loadAdapter(platformId);
  return adapter.install({ configRoot, evolverRoot, force });
}
```

### 平台检测策略

```javascript
const PLATFORMS = {
  cursor: { name: 'Cursor', configDir: '.cursor', detector: '.cursor' },
  'claude-code': { name: 'Claude Code', configDir: '.claude', detector: '.claude' },
  codex: { name: 'Codex', configDir: '.codex', detector: '.codex' },
};
```

**优先级**：
1. 当前工作目录下的 `.cursor` / `.claude` / `.codex` 检测
2. 用户 home 目录下的检测
3. fallback 到当前工作目录

### Hook 脚本注入

每个平台适配器实现 `install()` 方法，将三个核心脚本注入到平台配置中：
- `evolver-session-start.js` — 会话初始化时调用
- `evolver-session-end.js` — 会话结束时调用
- `evolver-signal-detect.js` — 信号检测时调用

### `mergeJsonFile` 幂等合并

```javascript
function mergeJsonFile(filePath, patch, { markerKey = '_evolver_managed' } = {}) {
  // 读取现有 JSON
  // deepMerge 合并（不覆盖未管理的字段）
  // 写入时带 markerKey 标识（可识别 evolver 管理的字段）
  // 使用 .tmp + rename 原子写入
}
```

**安全设计**：
- `_evolver_managed` marker 标识 evolver 管理的字段
- 幂等合并：不会意外覆盖用户手动配置的字段
- 原子写入：避免 partial write 破坏配置文件

### 与 v1.47 的对比

v1.47 中，Hook 安装逻辑分散在 `evolve.js` 主循环和各平台特定代码中。v1.66 将其抽象为统一适配器层，每个平台只需实现 `install/uninstall` 两个方法。

---

## §5 ATP（Agent Trading Protocol）

v1.66 新增 `src/atp/` 目录，实现**ATP（Agent Trading Protocol）**——Hub 作为市场的交易协议：

### 目录结构

```
src/atp/
├── index.js              # 入口
├── hubClient.js          # Hub ATP 端点客户端
├── consumerAgent.js      # 消费者 Agent（购买解决方案）
├── merchantAgent.js      # 商户 Agent（出售创新方案）
├── serviceHelper.js      # 服务辅助函数
└── defaultHandler.js     # 默认处理器
```

### Hub ATP 端点

ATP 基于 `hubClient.js` 封装 Hub HTTP 端点：

```javascript
// POST /a2a/atp/order — 下单
function placeOrder(opts) {
  return _hubPost('/a2a/atp/order', {
    sender_id: nodeId,
    capabilities: opts.capabilities,    // 所需能力列表
    budget: Math.max(1, Math.round(opts.budget || 10)),  // 积分预算
    routing_mode: opts.routingMode || 'fastest',  // fastest | cheapest | auction | swarm
    verify_mode: opts.verifyMode || 'auto',        // auto | ai_judge | bilateral
    question: opts.question,
    signals: opts.signals,
    min_reputation: opts.minReputation,
  });
}

// POST /a2a/atp/deliver — 提交交付证明
function submitDelivery(orderId, proofPayload) {
  return _hubPost('/a2a/atp/deliver', {
    sender_id: nodeId,
    order_id: orderId,
    proof_payload: proofPayload || {},
  });
}
```

### ATP 路由模式

| 模式 | 含义 |
|------|------|
| `fastest` | 最快响应的 merchant 接单 |
| `cheapest` | 最低价格的 merchant 接单 |
| `auction` | 竞拍模式 |
| `swarm` | 蜂群模式，多 merchant 并行交付 |

### ATP 验证模式

| 模式 | 含义 |
|------|------|
| `auto` | 自动验证（基于交付证明） |
| `ai_judge` | AI Judge 评估 |
| `bilateral` | 双边确认 |

### CE 借鉴

ATP 代表了一种**市场化的 GDE 反馈机制**：
- Evolver 不仅从自己的 outcome 中学习，还可以通过 ATP 购买其他 Agent 的解决方案
- 这为 BlueCortexCE 提供了**外部知识获取**的新范式

---

## §6 Self-PR 自动提交机制

`src/gep/selfPR.js`（400+ 行）实现**将高置信度的自我优化代码变更自动提交为 GitHub PR**：

### 触发条件（所有条件同时满足）

```javascript
const SELF_PR_MIN_SCORE   = require('../config').SELF_PR_MIN_SCORE;   // 最低分数阈值
const SELF_PR_MIN_STREAK  = require('../config').SELF_PR_MIN_STREAK;   // 最低连胜次数
const SELF_PR_MAX_FILES   = require('../config').SELF_PR_MAX_FILES;   // 最多文件数
const SELF_PR_MAX_LINES   = require('../config').SELF_PR_MAX_LINES;   // 最多改动行数
const SELF_PR_COOLDOWN_MS = require('../config').SELF_PR_COOLDOWN_MS;  // 冷却时间（默认 24h）
```

### 安全门禁

```javascript
// 1. 只允许 optimize + low 风险 mutation
// 2. 必须通过 leak scan（fullLeakCheck）
// 3. diff 做过 SHA-256 去重（防止重复提交）
// 4. OBFSUCATED_FILES（public.manifest.json 中的混淆文件）不能提交
const OBFUSCATED_FILES = new Set([
  'src/evolve.js', 'src/gep/selector.js', 'src/gep/mutation.js',
  'src/gep/solidify.js', 'src/gep/prompt.js', ... // 26 个核心文件
]);

// 5. 只允许 public manifest 中的非混淆文件
function isPublicNonObfuscated(filePath) { ... }
```

### 环境门控

```javascript
// 必须设置 EVOLVER_SELF_PR=true 环境变量
// → 不会意外启用
```

### PR 创建流程

```javascript
// 1. 捕获 diff snapshot
// 2. 验证所有门禁（score/streak/leak/diff dedup）
// 3. gh pr create --title "[Self-PR] optimize: ..." --body "..."
// 4. 记录 self_pr_state.json（cooldown tracking）
// 5. 不自动 merge（never auto-merge）
```

### State 文件

```javascript
const STATE_FILE = 'self_pr_state.json';
// {
//   "last_pr_time": 1700000000000,
//   "last_pr_sha": "abc123...",
//   "cooldown_until": 1700086400000
// }
```

### CE 借鉴

BlueCortexCE 作为**记录型记忆**（而非 GEP 进化型），不需要 self-PR，但以下设计值得借鉴：
- **多层门禁**：分数 + 连胜 + blast radius + leak scan + diff dedup + cooldown
- **混淆文件隔离**：核心代码不能通过自优化修改
- **State 文件 + cooldown**：防止重复提交

---

## §7 主要模块精简（evolve.js / memoryGraph.js / a2aProtocol.js）

v1.66 的一个显著趋势是**核心模块大幅精简**，通过提取子模块降低复杂度：

### `evolve.js`（-2176 行！）

v1.47 的 `evolve.js` 包含大量会话管理、信号提取、选择逻辑。v1.66 将这些提取到独立模块：

| v1.47 内容 | v1.66 迁移到 |
|------------|-------------|
| 会话 transcript 读取 | `src/adapters/scripts/evolver-session-start.js` |
| 会话结束处理 | `src/adapters/scripts/evolver-session-end.js` |
| 信号检测逻辑 | `src/adapters/scripts/evolver-signal-detect.js` |
| 适配器加载 | `src/adapters/hookAdapter.js` |
| ATP 逻辑 | `src/atp/*.js` |

### `memoryGraph.js`（-788 行）

将 JSONL 事件存储和查询逻辑提取到子模块，保留核心的状态管理和反馈环路。

### `a2aProtocol.js`（-1222 行）

ATP 和 Hub 通信协议从 `a2aProtocol.js` 拆分到 `src/atp/hubClient.js`，`a2aProtocol.js` 保留 A2A 协议本身。

---

## §8 CE 借鉴路径

### 高优先级（P0）

| Evolver 特性 | CE 借鉴 | 落点 |
|-------------|---------|------|
| Layer 2 加权评分 | 为 BlueCortexCE 的**观察类型匹配**实现加权评分层 | `ObservationTypeMatcher` |
| Plateau 检测 | 在 Phase 3 提取引擎中增加**趋势感知**：检测长期无改善并发出信号 | `StructuredExtractionService` |
| `explore_opportunity` 信号 | CE 的 idle gating 可在饱和时触发**主动探索**（而非仅等待） | `IdleGatingService` |

### 中优先级（P1）

| Evolver 特性 | CE 借鉴 | 落点 |
|-------------|---------|------|
| Adapters 统一入口 | CE 的**多客户端会话读取**（Java/Hook/thin-proxy）统一适配接口 | 架构重构 |
| ATP market 机制 | CE 的外部知识获取可考虑类似 ATP 的**积分交换**模式 | 长期路线图 |
| Self-PR 多层门禁 | CE 的**变更质量门禁**（leak scan + diff dedup + cooldown） | Review/Gate 机制 |

### 低优先级（P2）

| Evolver 特性 | CE 借鉴 |
|-------------|---------|
| `_evolver_managed` marker | CE 配置合并时的幂等字段管理 |
| `deepMerge` 原子写入 | 配置文件更新的原子性保证 |
| Public manifest 非混淆文件列表 | CE 的**核心文件保护列表**（防止误删/破坏） |

---

## 附录：v1.66 新增文件清单

| 文件 | 行数 | 功能 |
|------|------|------|
| `src/adapters/hookAdapter.js` | 205 | 统一跨平台 Hook 安装/卸载 |
| `src/adapters/cursor.js` | ~89 | Cursor 平台适配器 |
| `src/adapters/claudeCode.js` | ~145 | Claude Code 平台适配器 |
| `src/adapters/codex.js` | ~172 | Codex 平台适配器 |
| `src/adapters/scripts/evolver-session-start.js` | ~93 | 会话开始 Hook 脚本 |
| `src/adapters/scripts/evolver-session-end.js` | ~194 | 会话结束 Hook 脚本 |
| `src/adapters/scripts/evolver-signal-detect.js` | ~69 | 信号检测 Hook 脚本 |
| `src/atp/index.js` | ~23 | ATP 入口 |
| `src/atp/hubClient.js` | ~171 | Hub ATP 端点客户端 |
| `src/atp/consumerAgent.js` | ~157 | 消费者 Agent |
| `src/atp/merchantAgent.js` | ~118 | 商户 Agent |
| `src/atp/serviceHelper.js` | ~99 | ATP 服务辅助 |
| `src/atp/defaultHandler.js` | ~69 | ATP 默认处理器 |
| `src/gep/selfPR.js` | ~400 | Self-PR 自动提交 |
| `src/gep/integrityCheck.js` | ~1 | 完整性检查（占位） |
| `src/gep/shield.js` | ~1 | Shield（占位） |
| `src/gep/explore.js` | ~1 | Explore（占位） |
