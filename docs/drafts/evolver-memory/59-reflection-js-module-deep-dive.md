# `59` `reflection.js` 模块深度分析

> **来源**：`EvoMap/evolver/src/gep/reflection.js`（178 行）
> **前置**：[`23`](./23-evolver-state-event-dual-layer-and-self-awareness-loop.md)（State+Event 双层）、[`31`](./31-reflection-remote-adapter-local-state.md)（自适应自省概述）— 本文档补充源码级细节，不重复已覆盖的高层设计
> **状态**：v1（2026-04-25）

---

## 1. 模块定位与架构位置

`reflection.js` 是 Evolver GEP 层唯一的**战略级自省模块**，职责是在进化循环之上提供**周期性离线复盘**能力，与 `evolve.js` 的在线周期循环互补。

```
evolve.js 主循环（在线，每周期）
  └─ 每周期 recordOutcome / extractSignals

reflection.js 战略自省（离线，按间隔触发）
  └─ 触发条件：cycleCount % interval === 0 + 冷却期过期
  └─ 产出：insights + strategy_adjustment + mutation_suggestions → 写入 JSONL
```

### 1.1 核心导出

| 导出 | 职责 |
|------|------|
| `shouldReflect({ cycleCount, recentEvents })` | 判断当前周期是否应触发自省 |
| `computeReflectionInterval(recentEvents)` | 计算自适应间隔（3/5/8） |
| `buildSuggestedMutations(signals)` | 从信号生成人格参数微调建议 |
| `buildReflectionContext({ recentEvents, signals, memoryAdvice, narrative })` | 组装 LLM 战略复盘提示词 |
| `recordReflection(reflection)` | 将复盘结果写入 JSONL 日志 |
| `loadRecentReflections(count)` | 从 JSONL 读取最近 N 条复盘记录 |
| `REFLECTION_INTERVAL_CYCLES` | 常量导出（向后兼容，默认 5） |

---

## 2. 自适应间隔机制（`computeReflectionInterval`）

### 2.1 三态间隔算法

```javascript
function computeReflectionInterval(recentEvents) {
  if (events.length < 3) return 5;               // 数据不足 → 默认
  const tail = events.slice(-3);                  // 只看最近 3 个周期

  if (tail.every(e => e.outcome.status === 'success')) return 8;  // 连续成功
  if (tail.every(e => e.outcome.status === 'failed'))   return 3;  // 连续失败
  return 5;                                         // 混合 → 默认
}
```

**设计原则**：少数服从多数 + 极端情况加速反应。连续失败时 3 周期（约几分钟内）即触发复盘；连续成功时 8 周期（约数十分钟）才复盘一次，减少无谓开销。

### 2.2 `shouldReflect` 的双重条件

即使 `computeReflectionInterval` 返回 3/5/8，`shouldReflect` 仍需满足两个额外条件：

```javascript
function shouldReflect({ cycleCount, recentEvents }) {
  const interval = computeReflectionInterval(recentEvents);

  // 条件 1：周期计数达标
  if (cycleCount < interval) return false;
  if (cycleCount % interval !== 0) return false;  // 每 interval 周期才触发一次

  // 条件 2：冷却期未过
  const logPath = getReflectionLogPath();
  if (fs.existsSync(logPath)) {
    const age = Date.now() - fs.statSync(logPath).mtimeMs;
    if (age < 30 * 60 * 1000) return false;       // 距上次 < 30 分钟则跳过
  }

  return true;
}
```

**两层过滤**：周期对齐防止重复触发，冷却期防止日志文件频繁写入。

---

## 3. 战略复盘提示词构建（`buildReflectionContext`）

### 3.1 四段式上下文组装

`buildReflectionContext` 按顺序拼接以下四个区块，最终附加 5 个战略问题和一个 JSON 输出格式说明：

```
[固定开场]
↓ 拼接
[近期周期统计]（最近 10 条）
↓ 拼接
[当前信号]（最多 20 条）
↓ 拼接
[Memory Graph 建议]（preferredGeneId / bannedGeneIds / explanation）
↓ 拼接
[进化叙事摘要]（最后 3000 字符）
↓ 附加
[5 个战略问题]
↓ 附加
[JSON 输出格式要求]
```

### 3.2 近期周期统计的精确聚合逻辑

这是源码级的关键细节——`buildReflectionContext` 不只给 LLM 原始事件列表，而是预计算了**有意义的统计摘要**：

```javascript
const last10 = recentEvents.slice(-10);

// 统计 1：成功率
const successCount = last10.filter(e => e.outcome.status === 'success').length;
const failCount = last10.filter(e => e.outcome.status === 'failed').length;

// 统计 2：意图分布（按 intent 字段聚合频率）
const intents = {};
last10.forEach(e => { intents[e.intent] = (intents[e.intent] || 0) + 1; });

// 统计 3：Gene 使用频率（只看每周期第一个 gene）
const genes = {};
last10.forEach(e => {
  const g = e.genes_used?.[0] || 'unknown';
  genes[g] = (genes[g] || 0) + 1;
});
```

渲染后的 Markdown 片段：
```markdown
## Recent Cycle Statistics (last 10)
- Success: 7, Failed: 3
- Intent distribution: {"code_repair":4,"feature_request":3,"test_boost":3}
- Gene usage: {"Gene_repair_01":5,"Gene_test_boost":3,"unknown":2}
```

**预聚合而非原始列表**：减少 token 消耗，让 LLM 快速抓住模式。

### 3.3 五个战略问题（精确文本）

```markdown
## Questions to Answer
1. Are there persistent signals being ignored?
2. Is the gene selection strategy optimal, or are we stuck in a local maximum?
3. Should the balance between repair/optimize/innovate shift?
4. Are there capability gaps that no current gene addresses?
5. What single strategic adjustment would have the highest impact?
```

这 5 个问题覆盖了**信号、选择、策略、能力、优先级**五个维度，是自省的系统化框架。

### 3.4 LLM JSON 输出格式

```json
{
  "insights": ["..."],
  "strategy_adjustment": "...",
  "priority_signals": ["..."]
}
```

- `insights`：最多个发现（数组），每条是对近期模式的解读
- `strategy_adjustment`：一句话描述推荐的整体策略方向
- `priority_signals`：需要优先处理的信号列表（将出现在下一周期的 signal 输入中）

---

## 4. 突变建议生成（`buildSuggestedMutations`）

### 4.1 信号→人格参数映射

```javascript
function buildSuggestedMutations(signals) {
  const muts = [];

  // 稳定成功 plateau → 提高创造性
  if (hasStagnation) {
    muts.push({ param: 'creativity', delta: +0.05, reason: 'stagnation detected' });
  }

  // 错误信号 → 提高严谨性
  if (hasError) {
    muts.push({ param: 'rigor', delta: +0.05, reason: 'errors detected in reflection' });
  }

  // 能力缺口 / 外部机会 → 提高风险容忍度
  if (hasGap) {
    muts.push({ param: 'risk_tolerance', delta: +0.05, reason: 'capability gap in reflection' });
  }

  return muts.slice(0, 2);  // 最多 2 个，避免剧烈波动
}
```

### 4.2 信号识别规则（精确）

```javascript
const hasStagnation = sigs.some(s =>
  s === 'stable_success_plateau' ||
  s === 'evolution_stagnation_detected' ||
  s === 'empty_cycle_loop_detected'
);

const hasError = sigs.some(s =>
  s === 'log_error' ||
  String(s).startsWith('errsig:') ||
  String(s).startsWith('errsig_norm:')   // 规范化错误签名
);

const hasGap = sigs.some(s =>
  s === 'capability_gap' ||
  s === 'external_opportunity'
);
```

**注意**：`errsig_norm:` 前缀对应 `memoryGraph.js` 的 `normalizeErrorSignature` 输出（路径归一化 → `<path>`，十六进制 → `<hex>`，数字 → `<n>`）。

---

## 5. JSONL 日志机制

### 5.1 写入格式（`recordReflection`）

```javascript
function recordReflection(reflection) {
  const entry = JSON.stringify({
    ts: new Date().toISOString(),
    type: 'reflection',
    ...reflection,         // 展开 LLM 返回的 insights / strategy_adjustment / priority_signals
  }) + '\n';

  fs.appendFileSync(logPath, entry, 'utf8');
}
```

每条记录为一行 JSON，展开后包含：
```json
{"ts":"2026-04-25T12:00:00.000Z","type":"reflection",
 "insights":["gene selection appears stuck in repair mode"],"strategy_adjustment":"shift toward innovate","priority_signals":["capability_gap"]}
```

### 5.2 读取格式（`loadRecentReflections`）

```javascript
function loadRecentReflections(count) {
  const lines = fs.readFileSync(logPath, 'utf8')
    .trim().split('\n').filter(Boolean);

  return lines.slice(-n)                    // 最近 n 条
    .map(line => JSON.parse(line))          // 解析 JSON
    .filter(Boolean);
}
```

纯文本 JSONL + `slice(-n)` 实现无数据库的固定窗口读取。

---

## 6. 与 `evolve.js` 主循环的集成

### 6.1 集成点

根据 `evolve.js` 的调用链（见 [`19`](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md)），`reflection.js` 的集成点位于**每个进化周期结束时**：

```
evolve 周期循环
  └─ 1. extractSignals()        ← 信号提取
  └─ 2. getMemoryAdvice()       ← 从 Memory Graph 获取建议
  └─ 3. shouldReflect() ?       ← ← ← reflection.js 决策点
       └─ true → buildReflectionContext() + LLM调用 → recordReflection()
  └─ 4. buildSuggestedMutations()   ← 生成参数调整建议
  └─ 5. recordOutcome()         ← 写 JSONL
```

`buildSuggestedMutations` 的输出会被传递给**人格状态机**（`personality.js`）以微调参数。

### 6.2 与 `innovation.js` 的关系

`innovation.js` 的 `generateInnovationIdeas()` 分析**当前技能目录结构**，找出弱势领域（技能数量最少的两个分类），生成 3 条改进思路。

`reflection.js` 的 `buildSuggestedMutations` 则分析**近期进化历史**，生成 1–2 条参数调整建议。

两者**互补**：
- `innovation.js` → **功能级**（做什么新能力）
- `reflection.js` → **参数级**（如何调整策略方向）

---

## 7. BlueCortexCE 借鉴路径

### 7.1 自适应复盘间隔 → CE 上下文注入策略

| Evolver 设计 | CE 可借鉴机制 |
|-------------|--------------|
| 连续成功 → 间隔 8 | 连续 session 无高优先级观察 → 降低注入频率 |
| 连续失败 → 间隔 3 | 连续 session 有 error 类观察 → 提高注入优先级 |
| 冷却 30 分钟 | `ContextService` 结果缓存 TTL 控制 |
| 统计预聚合（intent/gene 分布） | `ObservationEntity` 聚合视图统计 |

### 7.2 战略复盘提示词框架 → CE 自我诊断

Evolver 的 5 问框架可以直接映射到 CE 的**会话级自我诊断**：

```markdown
## Session Self-Reflection
1. Were there important observations that were ignored or underweighted?
2. Is the search/retrieval strategy optimal for this session type?
3. Should the injection ratio of prompts/observations/summaries shift?
4. Are there capability gaps (no relevant memory for this query type)?
5. What single context adjustment would have the highest impact?
```

这一框架可用于**在 session-end 生成内省摘要**，写入 `SummaryEntity`，供后续 session 参考。

### 7.3 突变建议 → CE 观察类型权重调参

| Evolver 参数 | CE 对应 | 建议触发条件 |
|-------------|---------|------------|
| `creativity` +0.05 | 降低 `system` 类观察权重 | 连续成功但无新发现 |
| `rigor` +0.05 | 提高 `error` 类观察权重 | 连续 error 类观察 |
| `risk_tolerance` +0.05 | 提高 `capability_gap` 类观察注入比例 | 外部机会信号出现 |

### 7.4 JSONL 复盘日志 → CE `SummaryEntity` 增强

Evolver 的 JSONL 复盘记录可被 `loadRecentReflections(count)` 读取，形成**长期战略记忆**。

CE 已有 `SummaryEntity`，但缺少"战略级摘要"（跨 session 的模式发现）：
- **当前**：每个 session 生成一条 summary（会话摘要）
- **可增补**：定期（如每 10 个 session）生成一条**元级摘要**，包含：
  ```json
  {
    "type": "meta_summary",
    "ts": "...",
    "insights": ["observation_type distribution across sessions"],
    "strategy_adjustment": "increase error observation weight",
    "priority_signals": ["capability_gap"]
  }
  ```

---

## 8. 源码文件对照

| 源码文件 | 行数 | 本文档覆盖 |
|---------|------|---------|
| `src/gep/reflection.js` | 178 | 全文 |
| `src/gep/memoryGraph.js`（normalizeErrorSignature） | — | §4.2 信号识别 |
| `src/gep/personality.js` | — | §6.1 集成点 |
| `src/ops/innovation.js` | — | §6.2 与 innovation.js 的互补关系 |
