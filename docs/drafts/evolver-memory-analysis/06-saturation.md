# 06 — 饱和检测与降级策略

## 6.1 饱和问题的根源

EvoMap/evolver 是一个自主进化的 AI Agent，理论上可以无限循环运行。但实践中会遇到几类饱和问题：

| 问题 | 表现 | 根因 |
|------|------|------|
| **空循环饱和** | 连续多次 cycle 产出 0 文件变更 | 进化空间耗尽（所有可改进点都已修复） |
| **修复循环** | 连续多次 repair intent 但持续失败 | 同一问题无法通过代码修复解决（如外部依赖） |
| **失败循环** | 连续多次 outcome.status = failed | 方法论错误，需要换基因 |
| **高失败率** | 最近 8 个 cycle 中 75%+ 失败 | 系统性错误，不应继续进化 |

## 6.2 饱和检测机制

### 6.2.1 信号层检测（signals.js）

在 `extractSignals()` 的 Phase 3 中，通过 `analyzeRecentHistory()` 注入饱和信号：

```javascript
// 连续空循环检测
if (history.consecutiveEmptyCycles >= 5) {
  signals.push('force_steady_state');   // 强制进入稳态
  signals.push('evolution_saturation');
} else if (history.consecutiveEmptyCycles >= 3) {
  signals.push('evolution_saturation');
}

// 空循环占比检测（≥50% in 8 events）
if (history.emptyCycleCount >= 4) {
  signals.push('stable_success_plateau');
  signals.push('empty_cycle_loop_detected');
}

// 连续修复循环（≥3）
if (history.consecutiveRepairCount >= 3) {
  signals.push('force_innovation_after_repair_loop');
  signals.push('repair_loop_detected');
  signals.push('stable_success_plateau');
}

// 连续失败（≥3）
if (history.consecutiveFailureCount >= 3) {
  signals.push('consecutive_failure_streak_' + count);
}
if (history.consecutiveFailureCount >= 5) {
  signals.push('failure_loop_detected');
  signals.push('ban_gene:' + topGene);  // 禁止表现最差的基因
}

// 高失败率（≥75%）
if (history.recentFailureRatio >= 0.75) {
  signals.push('high_failure_ratio');
  signals.push('force_innovation_after_repair_loop');
}
```

### 6.2.2 回路熔断器（Circuit Breaker）

`evolve.js` 中的 `checkRepairLoopCircuitBreaker()`：

```javascript
function checkRepairLoopCircuitBreaker() {
  const threshold = REPAIR_LOOP_THRESHOLD;  // 默认 10
  const recent = readAllEvents().slice(-threshold);

  if (recent.length >= threshold) {
    const allRepairFailed = recent.every(e =>
      e.intent === 'repair' && e.outcome?.status === 'failed'
    );
    if (allRepairFailed) {
      const sameGene = geneIds.every(id => id === geneIds[0]);
      console.warn(`[CircuitBreaker] ${threshold} consecutive failed repairs...`);
      process.env.FORCE_INNOVATION = 'true';
    }
  }
}
```

**10 次连续 repair + 全失败** → 强制创新，绕过当前基因策略。

## 6.3 降级策略

### 6.3.1 空闲周期门控（Idle Gating）

```javascript
function shouldSkipHubCalls(signals) {
  // 仅在饱和信号存在时触发
  const saturationIndicators = [
    'force_steady_state',
    'evolution_saturation',
    'empty_cycle_loop_detected'
  ];
  if (!signals.some(s => saturationIndicators.includes(s))) return false;

  // 检查是否有可执行的信号
  const actionablePatterns = [
    'log_error', 'recurring_error', 'capability_gap', 'perf_bottleneck',
    'external_task', 'bounty_task', 'overdue_task', 'urgent',
    'unsupported_input_type', 'errsig:', 'user_feature_request:'
  ];

  // 有可执行信号 → 不跳过 Hub 调用
  if (signals.some(s => actionablePatterns.some(p =>
    s === p || (p.endsWith(':') && s.startsWith(p))
  ))) return false;

  // 无可执行信号 + 上次 fetch 不足 30 分钟 → 跳过
  const _elapsed = Date.now() - _lastHubFetchMs;
  if (_elapsed < 600000) return true;  // 10 分钟（可配置）

  return false;
}
```

### 6.3.2 强制稳态（Force Steady State）

当 `force_steady_state` 信号存在时：
- `shouldSkipHubCalls()` 返回 `true`
- 跳过 Hub 任务获取
- `evolve()` 提前返回，**不生成 prompt**
- 系统进入低功耗模式，等待新的外部信号激活

### 6.3.3 策略降级

在 `computeAdaptiveStrategyPolicy()` 中：

```javascript
// 修复循环 → 强制创新
if (repairStreak >= 3 || failureStreak >= 3) {
  forceInnovate = stagnation && !signals.includes('log_error');
}

// 高风险基因 → 缩小爆炸半径
if (highRiskGene) {
  blastRadiusMaxFiles = Math.max(2, Math.min(blastRadiusMaxFiles, 6));
}

// 强制创新 → 适度扩大爆炸半径
if (forceInnovate) {
  blastRadiusMaxFiles = Math.max(3, Math.min(blastRadiusMaxFiles, 10));
}
```

### 6.3.4 漂移降级

连续失败 → ban 基因 + 启用漂移：

```javascript
// selector.js
if (!driftEnabled && info.attempts >= 2 && info.best < 0.18) {
  bannedGeneIds.add(geneId);  // 抑制低效基因
}

// 5+ 连续失败 → 强制漂移模式
if (history.consecutiveFailureCount >= 5) {
  useDrift = true;  // driftIntensity > 0
  driftMode = 'random_weighted';  // 随机探索
}
```

## 6.4 饱和检测的可观测性

每个 cycle 结束时，`observations` 对象记录系统状态：

```javascript
const observations = {
  agent: AGENT_NAME,
  scan_ms: scanTime,
  recent_error_count: recentErrorCount,
  evidence: {
    recent_session_tail: String(sessionLog).slice(-6000),
    today_log_tail: String(todayLog).slice(-2500),
  }
};
```

在 `recordOutcomeFromState()` 中：
- `baselineObserved` 记录执行前的错误数和扫描时间
- `currentObserved` 记录执行后的错误数和扫描时间
- `inferOutcomeEnhanced()` 根据错误数变化和扫描时间变化调整 score

```javascript
// 错误数减少 → score 加权提升（最多 +0.12）
const delta = prevErrCount - curErrCount;
score += Math.max(-0.12, Math.min(0.12, delta / 50));

// 扫描时间缩短 → score 加权提升（最多 +0.06）
if (prevScan > 0) {
  const ratio = (prevScan - curScan) / prevScan;
  score += Math.max(-0.06, Math.min(0.06, ratio));
}
```

---

_Next: [07-adapter.md](./07-adapter.md) — Adapter 模式与远程扩展_
