# Doc 73: 三层信号提取架构现实核查 + 新机会信号

**目标**：核查 `src/gep/signals.js` 在 v1.78 中的实际架构，纠正 doc 56 的错误结论，并记录新发现的 7 个机会信号。

**数据来源**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/signals.js`（v1.78.1，444 行）  
**本地仓库版本**：v1.47.0 → `origin/main` v1.78.1  
**最后更新**：2026-05-03

---

## 1. Doc 56 结论核查

### Doc 56 原结论

> signals.js 实际为 v1.39/v1.47 单层 regex；doc 55 Layer 2/3/SIGNAL_PROFILES 不存在；git log fbca5ab 确认。

### 现实核查结果：⚠️ Doc 56 结论需要修正

**v1.78.1 源码确认**：`src/gep/signals.js`（444 行）**确实包含**三层提取架构。

| 层级 | 函数 | 机制 |
|------|------|------|
| Layer 1 | `_extractRegex`（`analyzeRecentHistory` 中的内联逻辑） | 正则匹配（历史抑制 + 失败连击 + 多语言） |
| Layer 2 | `_extractKeywordScore` | `SIGNAL_PROFILES` 加权关键词评分（累积 evidence → 阈值触发） |
| Layer 3 | `_extractLLM` | Hub `/a2a/signal/analyze` LLM 语义分析（每 5 个 cycle 调用一次） |
| Merge | `_mergeSignals` | 三路去重合并 + observability 日志 |

**`SIGNAL_PROFILES`**（v1.78 新增）：

```javascript
var SIGNAL_PROFILES = {
  perf_bottleneck:    { keywords: { 'slow':3, 'timeout':4, ... }, threshold: 6 },
  capability_gap:     { keywords: { 'not supported':5, 'cannot':1, ... }, threshold: 5 },
  user_feature_request:{ keywords: { 'add':1, 'implement':3, ... }, threshold: 6 },
  user_improvement_suggestion: { keywords: {...}, threshold: 5 },
  recurring_error:    { keywords: { 'error':1, 'again':1, 'same error':5, ... }, threshold: 7 },
  tool_bypass:        { keywords: { 'exec':2, 'subprocess':3, ... }, threshold: 6 },
  evolution_stagnation_detected: { keywords: { 'no change':4, 'plateau':4, ... }, threshold: 6 },
};
```

### Doc 56 错误原因分析

Doc 56 使用 `git log fbca5ab` 来判断 `signals.js` 的最后更新时间。**但三层提取架构是 v1.48 之后新增的**（在 v1.47.0 → v1.78.1 的 46 个 commit 中引入），与 v1.39 的 `fbca5ab` 无关。

Doc 56 的验证方法存在逻辑缺陷：Git commit 时间 ≠ 代码引入时间，特别是经过大规模重构（大量模块被移动/重写）的版本。

---

## 2. 三层提取架构详解

### Layer 1: Regex（已有，存在时间最长）

```javascript
function analyzeRecentHistory(recentEvents) {
  // 1. 分析最近 evolution 历史，去重 / 抑制重复信号
  // 2. 失败连击检测（consecutive failures）
  // 3. 多语言支持
  // 4. 基因 ban 规则
}
```

- 特点：**二元命中**（hit/miss），适合明确的关键词模式
- 局限：无法处理模糊/分布式证据（如"这个问题讨论了很久但没有明确 error 关键词"）

### Layer 2: Weighted Keyword Scoring（v1.48+ 新增）

```javascript
function _extractKeywordScore(lower) {
  // 遍历 corpus，统计每个 keyword 在文本中出现的次数 × 权重
  // 只有当 totalScore >= profile.threshold 时才触发信号
  var scored = [];
  for (var pi = 0; pi < profileKeys.length; pi++) {
    var signalName = profileKeys[pi];
    var profile = SIGNAL_PROFILES[signalName];
    var totalScore = 0;
    for (var kw in profile.keywords) {
      var weight = profile.keywords[kw];
      var count = countOccurrences(lower, kw); // 最大 20 次/hit
      totalScore += count * weight;
    }
    if (totalScore >= profile.threshold) {
      scored.push(signalName);
    }
  }
  return scored;
}
```

**关键设计思想**：
- **累积 evidence**：不是单次匹配触发，而是多次低权重证据累积
- **阈值触发**：防止噪声误触发（`recurring_error` 需要 7 分才触发）
- **模糊匹配**：捕捉"讨论了很多次但没有明确关键词"的分布式证据

**安全设计**：使用 `execFileSync`（无 shell）+ argv 数组传递参数，防止命令注入。

### Layer 3: LLM Semantic（v1.48+ 新增）

```javascript
function _extractLLM(corpus) {
  _llmSignalCycleCount++;
  if (_llmSignalCycleCount % LLM_SIGNAL_INTERVAL !== 1) return [];
  // LLM_SIGNAL_INTERVAL = 5（每 5 个 cycle 调用一次）

  // 使用 curl argv 数组，postData 作为离散参数传入
  var stdout = execFileSync('curl', [
    '-s', '-m', '10', '-X', 'POST',
    '-H', 'Content-Type: application/json',
    '-H', 'Authorization: Bearer ' + nodeSecret,
    '-d', postData,
    url,
  ], { timeout: 12000, encoding: 'utf8' });

  // 解析 Hub 返回的 signals 数组
  return parsed.signals.filter(s => typeof s === 'string').slice(0, 10);
}
```

**关键特点**：
- **节流**：每 5 个 cycle 才调用一次，避免 LLM 调用爆炸
- **静默降级**：Hub 不可用时返回空数组，不阻塞流程
- **命令注入防护**：`execFileSync` + argv 数组 vs `exec` / 模板字符串

### Merge：`_mergeSignals`

```javascript
function _mergeSignals(regexSignals, scoreSignals, llmSignals) {
  var merged = new Set();
  for (var ri = 0; ri < regexSignals.length; ri++) merged.add(regexSignals[ri]);
  for (var si = 0; si < scoreSignals.length; si++) merged.add(scoreSignals[si]);
  for (var li = 0; li < llmSignals.length; li++) merged.add(llmSignals[li]);

  // Observability
  var scoreOnly = scoreSignals.filter(s => !regexSignals.includes(s));
  var llmOnly = llmSignals.filter(s => !regexSignals.includes(s) && !scoreSignals.includes(s));
  var overlap = regexSignals.filter(s => scoreSignals.includes(s) || llmSignals.includes(s));

  console.log('[Signals] Multi-strategy: regex=' + regexSignals.length + ' score=' + scoreSignals.length + ' llm=' + llmSignals.length + ' merged=' + merged.size);
  if (scoreOnly.length > 0) console.log('[Signals] Score-only: ' + scoreOnly.join(', '));
  if (llmOnly.length > 0) console.log('[Signals] LLM-only: ' + llmOnly.join(', '));

  return Array.from(merged);
}
```

---

## 3. 新增 7 个机会信号

v1.78 在 `OPPORTUNITY_SIGNALS` 数组中新增：

| 信号名 | 触发场景 | 优先级 |
|--------|----------|--------|
| `issue_already_resolved` | 问题已被其他 Agent/用户解决 | 高（避免重复劳动） |
| `openclaw_self_healed` | OpenClaw 自愈（错误自动恢复） | 高（记录自愈能力） |
| `empty_cycle_loop_detected` | 空循环检测（无进展循环） | 高（触发创新转移） |
| `explore_opportunity` | 探索机会（非问题驱动，主动探索） | 中 |
| `hub_search_miss_with_problem` | Hub 搜索未命中但有明确问题 | 中（触发本地进化） |
| `plateau_pivot_required` | 平台期强制转移 | 高（强制创新） |
| `plateau_pivot_suggested` | 平台期建议转移（弱信号） | 中 |

**与现有信号的协同关系**：
- `empty_cycle_loop_detected` + `plateau_pivot_required/suggested` → 共同构成**平台期检测体系**
- `issue_already_resolved` → 阻止无效进化（节省资源）
- `openclaw_self_healed` → 记录自愈能力用于未来自愈参考

---

## 4. BlueCortexCE 借鉴

### P0（高优先级）

1. **三层信号提取架构**：BlueCortexCE 的 `StructuredExtractionService` 可参考：
   - Layer 1 = regex/FTS（快速、准确）
   - Layer 2 = 语义评分（模糊/分布式）
   - Layer 3 = LLM 语义（深度理解，但需节流）
   
2. **`OPPORTUNITY_SIGNALS` 枚举**：`ObservationEntity.type` 扩展时参考这 7 个新信号的设计思路

3. **命令注入防护**：`execFileSync` + argv 数组模式，BlueCortexCE 如果有子进程调用应采用同样模式

### P1（中优先级）

4. **`SIGNAL_PROFILES` 阈值设计**：累积 evidence → 阈值触发模式，可用于 `SearchService` 的相关性评分优化

5. **静默降级**：`try/catch` 全包裹 + 空数组返回，不因单层失败阻塞主流程

---

## 5. 待核查项

- [ ] **Doc 56 需要修正**：在 doc 56 文件开头添加"⚠️ 2026-05-03 核查：doc 56 结论错误，Layer 2/3 实际存在于 v1.78"的说明，并链接到本文档
- [ ] **Layer 2/3 实际引入版本**：需要通过 `git log --all --oneline -- src/gep/signals.js` 找到引入三层架构的具体 commit 版本
- [ ] **`hubVerify.js` / `explore.js` / `shield.js` / `integrityCheck.js`**：均为 1 行 stub 文件，待后续版本完整实现后分析
- [ ] **`featureFlags.js`**（114 行）：功能开关系统，对 BlueCortexCE 配置管理有借鉴价值
- [ ] **`mailboxTransport.js`**（82 行）：邮箱传输机制
- [ ] **`claimNudge.js`**（121 行）：认领催促机制

---

## 附录：三层 vs 单层对比

| 维度 | 单层 Regex | 三层架构 |
|------|-----------|---------|
| 速度 | 最快 | Layer 1 快速，Layer 2 中等，Layer 3 最慢（节流） |
| 准确性 | 高（精确匹配） | Layer 1 高，Layer 2 中等，Layer 3 高（LLM） |
| 覆盖度 | 低（漏掉模糊证据） | 高（分布式证据被 Layer 2 捕捉） |
| 计算成本 | 最低 | Layer 1 低，Layer 2 中，Layer 3 高（但节流） |
| 适用场景 | 明确关键词（error, crash） | 模糊证据（"讨论很久但没解决"） |
