# 03 — 信号提取机制

## 3.1 信号分类体系

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

## 3.2 提取流程

```
输入文本拼接
  corpus = session + todayLog + memorySnippet + userSnippet
          │
          ▼
┌──────────────────────────────────────────────────┐
│  Phase 1: 防御信号检测                             │
│  - log_error（正则匹配 [error]|error:|exception:） │
│  - errsig（提取错误行并归一化）                     │
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
│  - repeated_tool_usage:exec（5 次 exec）          │
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
└────────────────┬─────────────────────────────────┘
                 │ 最终 signals[]
                 ▼
          返回 String[] 信号列表
```

## 3.3 多语言支持（4 语种）

signals.js 是 EvoMap 中多语言支持最完善的模块。特征请求检测：

| 语言 | 模式 | 示例 |
|------|------|------|
| EN | `\b(add|implement|create|build)\b...feature\b` | "add feature X" |
| ZH-CN | `加个|实现一下|做个|想要\s*一个` | "加个功能" |
| ZH-TW | `加個|實現一下|做個|請加` | "加個功能" |
| JA | `追加|実装|作って|機能を` | "機能を追加" |

**Snippet 提取**：每个信号可附带最多 200 字符的上下文片段，供基因选择和 prompt 使用。

## 3.4 错误签名归一化

```javascript
// 原始错误行
// "Error: Cannot find module '/workspace/src/utils.js' at line 15"
// "ReferenceError: x is not defined at eval (app.js:22:10)"

// 归一化后（用于 Signal Key）
// "<path>:15 TypeError"
// "<path>:22 ReferenceError"
```

归一化策略：
1. 所有绝对路径 → `<path>`
2. 所有十六进制数 → `<hex>`
3. 所有十进制数 → `<n>`
4. 连续空白 → 单空格
5. 截断到 220 字符

**注意**：`errsig:<normalized>` 信号会被进一步 hash 成 `errsig_norm:<hash>`，保证不同来源的相似错误映射到同一 key。

## 3.5 历史感知的去重与饱和

```javascript
function analyzeRecentHistory(recentEvents) {
  // 最近 10 个事件
  const tail = recent.slice(-10);

  // 1. 连续 repair 计数
  let consecutiveRepairCount = 0;
  for (let i = tail.length - 1; i >= 0; i--) {
    if (tail[i].intent === 'repair') consecutiveRepairCount++;
    else break;
  }

  // 2. 信号频率（在最近 8 个事件中）
  let signalFreq = {};
  for (const evt of tail) {
    for (const s of evt.signals) {
      signalFreq[stripSuffix(s)]++;  // 归一化 key
    }
  }
  // 频率 ≥ 3 → 抑制
  let suppressedSignals = new Set();
  for (const [k, v] of Object.entries(signalFreq)) {
    if (v >= 3) suppressedSignals.add(k);
  }

  // 3. 连续空循环计数
  let consecutiveEmptyCycles = 0;
  for (let i = tail.length - 1; i >= 0; i--) {
    if (tail[i].meta?.empty_cycle || tail[i].blast_radius?.files === 0)
      consecutiveEmptyCycles++;
    else break;
  }

  // 4. 连续失败计数
  let consecutiveFailureCount = 0;
  for (let i = tail.length - 1; i >= 0; i--) {
    if (tail[i].outcome?.status === 'failed') consecutiveFailureCount++;
    else break;
  }

  // 5. 失败率
  let recentFailureRatio = recentFailureCount / tail.length;

  return {
    suppressedSignals,
    consecutiveRepairCount,
    consecutiveEmptyCycles,
    consecutiveFailureCount,
    recentFailureRatio,
    signalFreq,
    geneFreq,
  };
}
```

**饱和检测阈值**：

| 条件 | 阈值 | 注入信号 |
|------|------|---------|
| 连续 repair | ≥ 3 | `force_innovation_after_repair_loop` |
| 连续空循环 | ≥ 3 | `evolution_saturation` |
| 连续空循环 | ≥ 5 | `force_steady_state` |
| 空循环占比 | ≥ 50% in 8 events | `stable_success_plateau` |
| 连续失败 | ≥ 3 | `consecutive_failure_streak_N` |
| 连续失败 | ≥ 5 | `failure_loop_detected` + ban top gene |
| 失败率 | ≥ 75% in 8 events | `high_failure_ratio` + `force_innovation` |

## 3.6 工具使用分析

```javascript
// 统计工具调用频率
var toolMatches = corpus.match(/\[TOOL:\s*([\w-]+)\]/g) || [];
var toolUsage = {};
for (const m of toolMatches) {
  toolName = m.match(/\[TOOL:\s*([\w-]+)\]/)[1];
  toolUsage[toolName] = (toolUsage[toolName] || 0) + 1;
}

// 阈值：exec ≥ 10 次 → high_tool_usage:exec
//       任意工具 ≥ 10 次 → high_tool_usage:<tool>
//       exec 重复使用 ≥ 5 次 → repeated_tool_usage:exec
```

**良性 exec 检测**：过滤 `node xxx.js ensure` 类型的看门狗检查，避免误报。

---

_Next: [04-retrieval.md](./04-retrieval.md) — 图检索与推理机制_
