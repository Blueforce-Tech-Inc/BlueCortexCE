# 08 — 反思机制与叙事记忆

## 8.1 周期性反思（Reflection）

### 8.1.1 触发条件

```javascript
function shouldReflect({ cycleCount, recentEvents }) {
  // 每 20 个 cycle 触发一次
  if (cycleCount % 20 === 0) return true;

  // 或者连续 5 个相同意图
  const tail = recentEvents.slice(-5);
  const intents = tail.map(e => e.intent);
  if (intents.every(i => i === intents[0])) return true;

  // 或者连续 3 个失败
  const failures = tail.filter(e => e.outcome?.status === 'failed');
  if (failures.length >= 3) return true;

  return false;
}
```

### 8.1.2 反思内容

```javascript
function buildReflectionContext({ recentEvents, signals, memoryAdvice, narrative }) {
  // 1. 近期事件统计
  const stats = {
    totalCycles: recentEvents.length,
    successRate: successCount / recentEvents.length,
    avgScore: recentEvents.reduce((a, e) => a + (e.outcome?.score || 0), 0) / recentEvents.length,
    geneFrequency: {},
  };

  // 2. 基因使用频率
  for (const e of recentEvents) {
    for (const g of e.genes_used || []) {
      stats.geneFrequency[g] = (stats.geneFrequency[g] || 0) + 1;
    }
  }

  // 3. 信号分布
  const signalCounts = {};
  for (const e of recentEvents) {
    for (const s of e.signals || []) {
      const base = s.split(':')[0];
      signalCounts[base] = (signalCounts[base] || 0) + 1;
    }
  }

  // 4. Memory Advice 建议的基因
  const memorySuggested = memoryAdvice?.preferredGeneId;

  // 5. 叙事摘要
  const recentNarrative = loadNarrativeSummary(3000);

  return formatReflectionReport(stats, signalCounts, memorySuggested, recentNarrative);
}
```

### 8.1.3 建议变异生成

```javascript
function buildSuggestedMutations(signals) {
  const suggestions = [];

  // 基于信号类型建议突变方向
  if (signals.includes('repair_loop_detected')) {
    suggestions.push({
      type: 'intent_shift',
      rationale: 'repair loop detected — switch to innovate intent',
      target_genes: ['innovation', 'curriculum'],
    });
  }

  if (signals.includes('stable_success_plateau')) {
    suggestions.push({
      type: 'expansion',
      rationale: 'plateau — explore new capability areas',
      target_genes: ['capability_gap_coverage'],
    });
  }

  if (signals.includes('perf_bottleneck')) {
    suggestions.push({
      type: 'optimization',
      rationale: 'performance issue — prioritize optimize intent',
      target_genes: ['performance_refactor'],
    });
  }

  return suggestions;
}
```

### 8.1.4 反思记录

```javascript
recordReflection({
  cycle_count: cycleCount,
  signals_snapshot: signals.slice(0, 20),
  preferred_gene: memoryAdvice?.preferredGeneId,
  banned_genes: Array.from(memoryAdvice?.bannedGeneIds || []),
  context_preview: reflectionCtx.slice(0, 1000),
  suggested_mutations: buildSuggestedMutations(signals),
});
```

反思记录被写入单独文件或作为 EvolutionEvent 的 meta 字段保存，供后续 audit。

## 8.2 叙事记忆（Narrative Memory）

### 8.2.1 记录格式

```javascript
function recordNarrative({ gene, signals, mutation, outcome, blast, capsule }) {
  const entry = [
    `### [${ts}] ${category.toUpperCase()} - ${status}`,
    `- Gene: ${geneId} | Score: ${score} | Scope: ${filesChanged} files, ${linesChanged} lines`,
    `- Signals: [${signalsSummary}]`,
    rationale ? `- Why: ${rationale}` : null,
    strategy ? `- Strategy:\n${strategy}` : null,
    capsuleSummary ? `- Result: ${capsuleSummary}` : null,
    '',
  ].filter(Boolean).join('\n');
  // 追加到 narrative.md
}
```

**输出示例**：
```markdown
# Evolution Narrative

A chronological record of evolution decisions and outcomes.

### [2026-05-03 01:23:45] REPAIR - success
- Gene: gene_self_repair_v3 | Score: 0.85 | Scope: 3 files, 127 lines
- Signals: [log_error, errsig:TypeError]
- Why: Fix TypeError in session handler caused by null reference

### [2026-05-03 00:45:12] INNOVATE - failed
- Gene: gene_capability_expansion | Score: 0.22 | Scope: 0 files, 0 lines
- Signals: [stable_success_plateau, evolution_saturation]
- Why: No actionable improvements found; system at plateau
```

### 8.2.2 修剪策略

```javascript
const MAX_NARRATIVE_ENTRIES = 30;    // 最多保留 30 条
const MAX_NARRATIVE_SIZE = 12000;     // 最多 12000 字符

function trimNarrative(content) {
  if (content.length <= MAX_NARRATIVE_SIZE) return content;

  const entries = content.split(/(?=^### \[)/m);
  while (entries.length > MAX_NARRATIVE_ENTRIES) entries.shift();

  let result = header + entries.join('');
  if (result.length > MAX_NARRATIVE_SIZE) {
    // 如果还是超长，保留最近 25 条
    result = header + entries.slice(-25).join('');
  }
  return result;
}
```

**两种修剪维度**：
1. 条目数量限制（30 条）
2. 字符总量限制（12000 字符）
3. 两者都超 → 优先保留最新条目

### 8.2.3 叙事加载

```javascript
function loadNarrativeSummary(maxChars = 4000) {
  const entries = content.split(/(?=^### \[)/m);
  const recent = entries.slice(-8);  // 最近 8 条
  let summary = recent.join('');

  if (summary.length > maxChars) {
    summary = summary.slice(-maxChars);
    const firstEntry = summary.indexOf('### [');
    if (firstEntry > 0) summary = summary.slice(firstEntry);
  }
  return summary.trim();
}
```

## 8.3 叙事 vs 图记忆：职责分离

| 维度 | Narrative Memory | Memory Graph |
|------|-----------------|--------------|
| 格式 | Markdown（人类可读） | JSONL（机器可读） |
| 粒度 | 粗（每个 cycle 一条） | 细（每个事件一条） |
| 主要读者 | 人类（调试、审计） | 代码（selector、advice） |
| 容量 | 30 条 / 12000 字符 | 无限（追加） |
| 推理依赖 | 否 | 是（边聚合、图推理） |

**设计意图**：Narrative 是人类的高层摘要，Graph 是机器的底层推理引擎。两者互补，不重复。

## 8.4 与 Claude-Mem Summary 的对比

| 维度 | EvoMap Narrative | Claude-Mem Summary |
|------|-----------------|-------------------|
| 格式 | Markdown | Text + structured fields |
| 粒度 | per-cycle | per-session |
| 保留策略 | 固定窗口（30/12000） | 无限制（向量存储） |
| 生成时机 | 每次 cycle 结束 | SessionEnd hook |
| 内容 | 决策+结果+原因 | 对话摘要+关键观察 |
| 追加 | 否（重写） | 是（向量追加） |
| 用于推理 | 否 | 是（context injection） |

**关键差异**：EvoMap 的 Narrative 是"历史记录"，Claude-Mem 的 Summary 是"上下文摘要"——前者回顾过去，后者服务当下推理。

---

_Next: [09-solidify-learning.md](./09-solidify-learning.md) — Solidify 机制与基因学习_
