# 3. 信号提取系统

## 3.1 核心职责

`signals.js` 的 `extractSignals()` 函数从原始文本语料中提取结构化信号列表。

**输入**：recentSessionTranscript / todayLog / memorySnippet / userSnippet
**输出**：去重排序后的信号数组，如 `['log_error', 'errsig:TypeError: ...', 'user_feature_request:添加 OAuth 支持']`

## 3.2 信号分类

### 防御性信号（Defensive）

| 信号 | 触发条件 | 说明 |
|------|----------|------|
| `log_error` | 文本含 `[error]`, `error:`, `exception:`, `"status":"error"`, 中文错误关键词 | 粗粒度错误标记 |
| `errsig:<text>` | 从错误行提取，裁剪到 260 字符，含路径/数字规范化 | 可复现的错误指纹 |
| `memory_missing` | 包含 "memory.md missing" | 文件缺失 |
| `user_missing` | 包含 "user.md missing" | 文件缺失 |
| `windows_shell_incompatible` | Windows 平台 + `pgrep`/`ps aux`/`cat >`/`heredoc` | Shell 兼容性 |
| `path_outside_workspace` | `path.resolve(__dirname, '../../../` 模式 | 路径越界 |
| `protocol_drift` | 含 "prompt" 但不含 "EvolutionEvent" | 协议漂移 |

### 反复性错误（Robustness）

| 信号 | 触发条件 |
|------|----------|
| `recurring_error` | 相同错误模式出现 ≥ 3 次 |
| `recurring_errsig(Nx):<key>` | 反复错误签名（附频率） |

### 机会信号（Opportunity / Innovation）

| 信号 | 触发条件 | 语言支持 |
|------|----------|----------|
| `user_feature_request` | EN: "add feature" / ZH: "加个功能" / TW: "加個功能" / JA: "機能を追加" | 4种 |
| `user_improvement_suggestion` | EN: "should be" / ZH: "改进一下" / TW: "改進一下" / JA: "改善" | 4种 |
| `perf_bottleneck` | 含 "slow", "timeout", "latency", "bottleneck" 等 | EN |
| `capability_gap` | 含 "not supported", "cannot", "missing feature" 等（排除文件缺失） | EN |
| `unsupported_input_type` | 含 "unsupported mime", "invalid mime" | EN |

### 工具使用分析

| 信号 | 触发条件 |
|------|----------|
| `high_tool_usage:<name>` | 某工具使用 ≥ 10 次 |
| `repeated_tool_usage:exec` | exec 调用 ≥ 5 次 |
| `tool_bypass` | 检测到 `node *.js` / `npx` / `curl api` / `python *.py` 在 exec 内容中 |

## 3.3 信号去重与优先级

### 高频抑制（Over-processing Suppression）

来自 `analyzeRecentHistory()` 的 3/8 规则：

```js
// 在最近 8 个事件中出现 ≥ 3 次的信号被抑制
if (history.suppressedSignals.size > 0) {
  signals = signals.filter(s => !history.suppressedSignals.has(normalizeKey(s)));
}
// 如果全部被抑制 → 系统稳定但陷入循环 → 强制注入创新信号
if (signals.length === 0) {
  signals.push('evolution_stagnation_detected');
  signals.push('stable_success_plateau');
}
```

### 优先级规则

**有可操作信号时，丢弃装饰性信号**：

```js
var actionable = signals.filter(s =>
  s !== 'user_missing' && s !== 'memory_missing' &&
  s !== 'session_logs_missing' && s !== 'windows_shell_incompatible'
);
if (actionable.length > 0) signals = actionable;
```

## 3.4 强制创新机制

当系统陷入特定模式时，自动注入创新信号打破僵局：

| 条件 | 注入信号 |
|------|----------|
| 连续 3+ 次 repair | `force_innovation_after_repair_loop` + 过滤掉 `log_error` |
| 8 个事件中 ≥ 4 个空循环 | `empty_cycle_loop_detected` + `stable_success_plateau` |
| 连续 5+ 空循环 | `force_steady_state` + `evolution_saturation`（优雅降级） |
| 连续 3+ 次失败 | `consecutive_failure_streak_N` |
| 连续 5+ 次失败 | `failure_loop_detected` + `ban_gene:<topGene>` |
| 失败率 ≥ 75% | `high_failure_ratio` + `force_innovation_after_repair_loop` |

## 3.5 多语言支持

信号提取支持 **EN / ZH-CN / ZH-TW / JA** 四种语言的特征请求和改进建议检测。正则表达式模式：

```js
// 中文功能请求
/加个|实现一下|做个|想要\s*一个|需要\s*一个|帮我加|帮我开发|加一下|新增一个/

// 日语功能请求
/追加|実装|作って|機能を|追加して|が欲しい|を追加|してほしい/
```

每个信号可附加原始语言片段（裁剪到 200 字符），供后续 gene selector 使用。
