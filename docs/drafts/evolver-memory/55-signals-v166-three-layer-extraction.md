# 55 — Signal Extraction v1.66: 三层信号提取架构

**版本**：v1.66（对应 evolver commit `fb20dde`）  
**与 v1.47 的区别**：v1.47 仅用 regex；v1.66 引入加权评分（Layer 2）和 LLM 提取（Layer 3）  
**分析日期**：2026-04-25

---

## 1. 三层提取架构总览

```
Corpus（语料）
    │
    ├─ Layer 1: _extractRegex()     → 二元匹配（hit/miss）
    ├─ Layer 2: _extractKeywordScore() → 加权评分（累积证据，超过阈值触发）
    └─ Layer 3: _extractLLM()       → LLM 语义提取（模糊/分布式模式）
    │
    ▼
_mergeSignals(regexSignals, scoreSignals, llmSignals)
    │
    ├─ scoreOnly = scoreSignals \ regexSignals   ← 新发现
    ├─ llmOnly   = llmSignals \ regexSignals \ scoreSignals ← 新发现
    └─ overlap    = regexSignals ∩ (scoreSignals ∪ llmSignals) ← 交叉验证
```

**核心设计原则**：三层提取是**互补而非竞争**的：
- Regex 提供确定性高速匹配
- Keyword Scoring 捕获"模糊/分散模式"（单条 regex 无法匹配）
- LLM 提取深层语义关联

---

## 2. Layer 2：加权关键词评分（Weighted Keyword Scoring）

### 2.1 设计动机

> "Unlike regex (binary hit/miss), keyword scoring accumulates weighted evidence from multiple keywords and fires only when confidence exceeds a threshold."

单条 regex 的问题是**二元判断**（命中/未命中）。现实中很多信号是"分布式"的：
- "slow"、"timeout"、"hung"、"unresponsive" 单独出现时可能是正常日志
- 组合出现（slow + timeout + hung）才是明确的性能瓶颈信号

### 2.2 信号画像（Signal Profiles）

每个信号类型定义为一个 `profile`：

```javascript
var SIGNAL_PROFILES = {
  perf_bottleneck: {
    keywords: {
      'slow': 3, 'timeout': 4, 'timed out': 4, 'latency': 3,
      'bottleneck': 5, 'lag': 2, 'delay': 2, 'hung': 3,
      'freeze': 3, 'unresponsive': 4, 'took too long': 4,
      'high cpu': 4, 'high memory': 4, 'oom': 5,
      'out of memory': 5, 'performance': 2, 'throttle': 3
    },
    threshold: 6,        // 累计权重 ≥ 6 才触发
  },
  capability_gap: {
    keywords: {
      'not supported': 5, 'cannot': 1, 'unsupported': 4,
      'not implemented': 5, 'no way to': 3, 'missing feature': 5,
      'not available': 3, 'no support for': 4, 'unavailable': 3,
      'incompatible': 3
    },
    threshold: 5,
  },
  user_feature_request: {
    keywords: {
      'add': 1, 'implement': 3, 'create': 2, 'build': 2,
      'feature': 3, 'i want': 3, 'i need': 3, 'we need': 3,
      'please add': 4, 'new function': 4, 'new module': 4,
      'endpoint': 2, 'capability': 2, 'support for': 2
    },
    threshold: 6,
  },
  recurring_error: {
    keywords: {
      'error': 1, 'exception': 2, 'failed': 1, 'crash': 4,
      'again': 1, 'still': 1, 'keeps': 2, 'repeatedly': 4,
      'same error': 5, 'still failing': 5, 'not fixed': 4
    },
    threshold: 7,
  },
  // ... 更多 profiles
};
```

### 2.3 评分算法

```javascript
function _extractKeywordScore(lower) {
  // 对每个 profile，累计 corpus 中所有 keyword 的权重
  // 当累计值 ≥ threshold 时，触发该信号
}
```

**示例**：
- Corpus: `"The process was slow and then hung, becoming unresponsive"`
- `perf_bottleneck` 评分：`'slow'(3) + 'hung'(3) + 'unresponsive'(4) = 10 ≥ 6 → **触发**

### 2.4 关键洞察

| 特性 | Regex | Keyword Scoring |
|------|-------|----------------|
| 匹配方式 | 二元 | 累积加权 |
| 模式 | 精确 | 模糊/分布式 |
| 速度 | 最快 | 快（O(keywords)） |
| 适用场景 | 确定性模式 | 分布式证据 |

---

## 3. Layer 3：LLM 提取

```javascript
function _extractLLM(corpus) {
  // 调用 LLM 从 corpus 中提取信号
  // 解析 LLM 返回的 structured output（JSON）
  return parsed.signals.filter(function (s) { /* 验证 */ });
}
```

**典型使用场景**：
- 跨多个会话的隐含模式
- 需要常识推理的信号
- 分布式证据的深层关联

---

## 4. 信号合并策略（_mergeSignals）

```javascript
function _mergeSignals(regexSignals, scoreSignals, llmSignals) {
  var scoreOnly = scoreSignals.filter(s => !regexSignals.includes(s)); // 新增
  var llmOnly = llmSignals.filter(s => !regexSignals.includes(s) && !scoreSignals.includes(s)); // 新增
  var overlap = regexSignals.filter(s => scoreSignals.includes(s) || llmSignals.includes(s)); // 交叉验证
  return [...overlap, ...scoreOnly, ...llmOnly]; // 去重合并
}
```

**合并逻辑**：
1. `overlap`：三层中至少两层都检测到 → 高置信度
2. `scoreOnly`：仅 Layer 2 发现（Layer 1 漏报）
3. `llmOnly`：仅 Layer 3 发现（Layer 1 和 Layer 2 都漏报）

---

## 5. 新增信号类型

v1.66 在 `OPPORTUNITY_SIGNALS` 中新增了 7 个信号：

```javascript
var OPPORTUNITY_SIGNALS = [
  // ... 原有信号 ...
  'issue_already_resolved',     // 新增：问题已被解决
  'openclaw_self_healed',      // 新增：OpenClaw 自愈
  'empty_cycle_loop_detected', // 新增：空循环检测
  'explore_opportunity',       // 新增：探索机会
  'hub_search_miss_with_problem', // 新增：Hub搜索未命中且有问题
  'plateau_pivot_required',    // 新增：需要转型
  'plateau_pivot_suggested',   // 新增：建议转型
];
```

---

## 6. 历史评分衰减（analyzeRecentHistory 增强）

```javascript
function analyzeRecentHistory(recentEvents) {
  // ...
  var recentScores = recentEvents.slice(-6).map(function (e) {
    // 计算最近6个事件的平均得分
  }).filter(function (s) { return s >= 0; });

  var avgScore = recentScores.reduce(function (a, b) { return a + b; }, 0)
                            / recentScores.length;
  // avgScore 用于判断是否需要降级策略
}
```

---

## 7. BlueCortexCE 借鉴路径

### P0（立即可用）
1. **三层提取思想**：将当前 Claude Code Hook 的信号提取改造为三层架构
2. **加权评分 for `user_feature_request`**：用户说"能不能加个..."时，多关键词组合比单条 regex 更可靠
3. **新增信号**：`issue_already_resolved`（问题已解决跳过）、`empty_cycle_loop_detected`（空循环检测）

### P1（值得借鉴）
1. **信号 Profile 抽象**：将信号类型定义为配置文件（YAML/JSON），而非硬编码
2. **LLM 辅助提取**：对于复杂隐含信号（跨会话模式），可考虑 LLM 辅助

### P2（架构参考）
1. **评分阈值动态调整**：根据 `avgScore` 动态调整策略
2. **三层互补设计**：任何一层都不完美，但组合后覆盖率大幅提升

---

## 附录：关键代码引用

```javascript
// signals.js 中的主入口
function extractSignals({ recentSessionTranscript, todayLog, memorySnippet, userSnippet, recentEvents }) {
  var regexSignals = _extractRegex(corpus, lower, errorHit);
  var scoreSignals = _extractKeywordScore(lower);  // 新增 Layer 2
  var llmSignals = _extractLLM(corpus);             // 新增 Layer 3
  return _mergeSignals(regexSignals, scoreSignals, llmSignals);
}
```
