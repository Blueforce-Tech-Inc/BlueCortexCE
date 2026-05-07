# 6. 反思系统

## 6.1 反思触发条件

`shouldReflect()` 判断是否需要执行反思：

```js
function shouldReflect({ cycleCount, recentEvents }) {
  // 1. 自适应间隔检查
  const interval = computeReflectionInterval(recentEvents);
  if (cycleCount % interval !== 0) return false;

  // 2. 冷启动保护：距上次反思 < 30 分钟则跳过
  const stat = fs.statSync(logPath);
  if (Date.now() - stat.mtimeMs < REFLECTION_COOLDOWN_MS) return false;

  return true;
}
```

### 自适应间隔

```js
function computeReflectionInterval(recentEvents) {
  const tail = events.slice(-3);
  if (allSuccess)  return 8;   // 连续成功 → 慢节奏
  if (allFailed)   return 3;   // 连续失败 → 快节奏
  return 5;                     // 默认
}
```

| 最近 3 次结果 | 反思间隔 |
|--------------|----------|
| 全部成功 | 8 个周期 |
| 全部失败 | 3 个周期 |
| 混合 | 5 个周期 |

## 6.2 反思上下文构建

`buildReflectionContext()` 生成 LLM 反思 prompt：

```
You are performing a strategic reflection on recent evolution cycles.
Analyze the patterns below and provide concise strategic guidance.

## Recent Cycle Statistics (last 10)
- Success: 7, Failed: 3
- Intent distribution: {"repair":4,"optimize":5,"innovate":1}
- Gene usage: {"repair_stability_v2":3,"optimize_perf_v1":2,...}

## Current Signals
log_error, errsig:TypeError: x is not a function, recurring_error

## Memory Graph Advice
- Preferred gene: repair_null_check_v3
- Banned genes: risky_mutation_x

## Recent Evolution Narrative
[最近 8 条叙事记录，裁剪到 3000 字符]

## Questions to Answer
1. Are there persistent signals being ignored?
2. Is the gene selection strategy optimal...?
3. Should the balance between repair/optimize/innovate shift?
4. Are there capability gaps that no current gene addresses?
5. What single strategic adjustment would have the highest impact?

Respond with JSON: { insights: [...], strategy_adjustment: "...", priority_signals: [...] }
```

## 6.3 建议的变异参数

```js
function buildSuggestedMutations(signals) {
  if (hasStagnation)
    muts.push({ param: 'creativity', delta: +0.05 });
  if (hasError)
    muts.push({ param: 'rigor', delta: +0.05 });
  if (hasGap)
    muts.push({ param: 'risk_tolerance', delta: +0.05 });
}
```

## 6.4 反思日志

反思结果以 JSONL 追加到 `reflection_log.jsonl`：

```json
{"ts":"2026-05-07T03:00:00.000Z","type":"reflection","insights":[...],"strategy_adjustment":"...","priority_signals":[...]}
```

`loadRecentReflections()` 支持读取最近 N 条反思记录供后续决策使用。
