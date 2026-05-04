# skillDistiller.js Full Pipeline Deep Dive

**目标**：完整分析 `skillDistiller.js`（1234 行）的双路径蒸馏管线，为 BlueCortexCE 提炼可落地的思想。

**源码**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/skillDistiller.js`

**最后更新**：2026-05-04

**前置文档**：
- [doc 64: Hub-Selector Feedback + validateSynthesizedGene](./64-hub-selector-feedback-and-skilldistiller-validate-v147.md) — 验证门 + Hub 反馈闭环
- [doc 36: Memory Architecture Synthesis](./36-memory-architecture-synthesis.md) — 8 大设计原则
- [doc 48: Gene as Compressed Memory](./48-gene-as-compressed-memory-closed-loop-architecture.md) — Gene 作为压缩记忆

---

## 1. 架构概览：双路径蒸馏

`skillDistiller.js` 实现两条独立的蒸馏路径，共享同一套验证和发布机制：

```
路径A: 成功模式蒸馏（Success Distillation）
  autoDistill()
    → collectDistillationData()     L78   收集成功胶囊
    → analyzePatterns()             L138  分析高频/漂移/缺口
    → prepareDistillation()         L525  幂等性检查 + 写 prompt
    → buildDistillationPrompt()      L222  构建 LLM 提示词
    → synthesizeGeneFromPatterns()  L602  基于模式的基因合成
    → validateSynthesizedGene()      L383  11 道验证门（见 doc 64）
    → finalizeDistilledGene()        L676  状态持久化 + 发布

路径B: 失败修复蒸馏（Failure Distillation）
  autoDistillFromFailures()
    → collectFailureDistillationData()  L862  收集 FailedCapsule
    → analyzeFailurePatterns()           L909  分析失败模式
    → buildFailureDistillationPrompt()   L961  失败专用 prompt
    → synthesizeRepairGeneFromFailures() L1041 基因修复合成
    → validateSynthesizedGene()           L383  同上验证门
    → finalizeDistilledGene()            L676  同上发布
```

**关键配置常量**：

| 常量 | 值 | 说明 |
|------|-----|------|
| `DISTILLER_MIN_CAPSULES` | 10 | 成功路径最低样本数 |
| `DISTILLER_MIN_SUCCESS_RATE` | 0.7 | 胶囊最低成功率阈值 |
| `DISTILLER_INTERVAL_HOURS` | 24 | 成功路径重试间隔 |
| `FAILURE_DISTILLER_MIN_CAPSULES` | 5 | 失败路径最低样本数（更低阈值） |
| `FAILURE_DISTILLER_INTERVAL_HOURS` | 12 | 失败路径重试间隔（更频繁） |
| `DISTILLED_MAX_FILES` | 12 | 蒸馏基因最大文件数 |
| `DISTILLED_ID_PREFIX` | `gene_distilled_` | 蒸馏基因 ID 前缀 |

**CE 启示**：双路径设计（成功 vs 失败）非常值得借鉴——BlueCortexCE 可以从"成功 Observation"和"失败 Observation"两条路径分别提炼结构化知识。

---

## 2. 数据收集：`collectDistillationData`（L78–L137）

### 2.1 胶囊合并逻辑

```javascript
// 从两个来源合并胶囊，去重
const capsulesJson  = readJsonIfExists(path.join(assetsDir, 'capsules.json'), { capsules: [] });
const capsulesJsonl = readJsonlIfExists(path.join(assetsDir, 'capsules.jsonl'));
let allCapsules = [].concat(capsulesJson.capsules || [], capsulesJsonl);
const unique = new Map();
allCapsules.forEach(c => { if (c && c.id) unique.set(String(c.id), c); });
allCapsules = Array.from(unique.values());
```

**设计要点**：
- 双重来源（JSON + JSONL）确保数据不丢失
- 去重 Map 保证同一 ID 只保留一份

### 2.2 成功胶囊筛选

```javascript
const successCapsules = allCapsules.filter(c => {
  if (!c || !c.outcome) return false;
  const status = typeof c.outcome === 'string' ? c.outcome : c.outcome.status;
  if (status !== 'success') return false;
  const score = Number.isFinite(Number(c.outcome.score)) ? Number(c.outcome.score) : 1;
  return score >= DISTILLER_MIN_SUCCESS_RATE; // ≥0.7
});
```

**设计要点**：
- outcome 可以是 string（`"success"`）或 object（`{status, score}`）
- 无 score 字段默认 1.0（完全成功）
- score < 0.7 的不纳入蒸馏

### 2.3 按 gene_id 分组

```javascript
const grouped = {};
// grouped[gene_id] = { triggers: [[s1,s2], [s3]], summaries: [...], total_count: N, avg_score: X }
```

每个分组聚合：触发信号数组列表、摘要列表、胶囊计数、平均分。

**CE 启示**：BlueCortexCE 可按 `observation_type` 或 `session_id` 分组，识别高频成功模式。

---

## 3. 模式分析：`analyzePatterns`（L138–L202）

### 3.1 三类模式报告

```javascript
const report = {
  high_frequency: [],  // 某基因 ≥5 次成功
  strategy_drift: [],   // 同一基因早期 vs 晚期摘要相似度 <0.6
  coverage_gaps: [],    // 出现 ≥3 次但无基因覆盖的信号
  total_success: N,
  total_capsules: M,
  success_rate: N/M,
};
```

### 3.2 高频模式检测

```javascript
if (g.total_count >= 5) {
  let flat = [];
  g.triggers.forEach(t => { if (Array.isArray(t)) flat = flat.concat(t); });
  const freq = {};
  flat.forEach(t => { freq[String(t).toLowerCase()] = (freq[t] || 0) + 1; });
  const top = Object.keys(freq).sort((a,b) => freq[b]-freq[a]).slice(0, 5);
  report.high_frequency.push({ gene_id, count, avg_score, top_triggers: top });
}
```

### 3.3 漂移检测（Jaccard 相似度）

```javascript
const first = g.summaries[0];
const last  = g.summaries[g.summaries.length - 1];
// Jaccard: |fw ∩ lw| / |fw ∪ lw|
if (sim < 0.6) {
  report.strategy_drift.push({ gene_id, similarity: sim, early_summary, recent_summary });
}
```

### 3.4 覆盖缺口检测

```javascript
// 统计所有事件的信号频率
const signalFreq = {};
(data.events || []).forEach(evt => {
  if (evt && Array.isArray(evt.signals)) {
    evt.signals.forEach(s => { signalFreq[String(s).toLowerCase()]++; });
  }
});
// 已有基因覆盖的信号
const covered = new Set();
Object.keys(grouped).forEach(geneId => {
  grouped[geneId].triggers.forEach(t => {
    if (Array.isArray(t)) t.forEach(s => covered.add(String(s).toLowerCase()));
  });
});
// 缺口 = 出现 ≥3 次但无基因覆盖
const gaps = Object.keys(signalFreq)
  .filter(s => signalFreq[s] >= 3 && !covered.has(s))
  .sort((a,b) => signalFreq[b] - signalFreq[a])
  .slice(0, 10);
```

**设计亮点**：
- 高频 + 漂移 + 缺口三维分析，覆盖不同类型知识提炼需求
- 漂移检测基于摘要文本，无需结构化标签
- 覆盖缺口主动识别"知识盲区"

**CE 启示**：BlueCortexCE 的 `StructuredExtractionService` 可借鉴"覆盖缺口检测"——识别高频观察类型但无对应结构化模板的场景。

---

## 4. Prompt 工程：`buildDistillationPrompt`（L222–L317）

这是整个模块最精华的部分之一。prompt 设计非常精妙：

### 4.1 严格的 ID 规则

```
- The id MUST start with "gene_distilled_" followed by a descriptive kebab-case name.
- NEVER include timestamps, numeric IDs, random numbers, tool names (cursor, vscode, etc.).
- Good: "gene_distilled_retry-with-exponential-backoff"
- Bad:  "gene_distilled_cursor-1773331925711"
```

### 4.2 Summary 作为 marketplace listing

```javascript
// Good
"Retry failed HTTP requests with exponential backoff, jitter, and circuit breaker to prevent cascade failures"
// Bad
"Distilled from capsules", "cursor automation", "1773331925711"
```

### 4.3 Signals_match 通用化规则

```javascript
// Good
["http_retry", "request_timeout", "exponential_backoff", "circuit_breaker", "resilience"]
// Bad
["cursor_auto_1773331925711", "cli_headless_1773331925711", "bypass_123"]
```

**核心原则**：Signal 必须描述**问题领域 + 解决方案方法**，而非工具或时间戳。

### 4.4 Strategy 步骤规范

```javascript
// Good: 具体 + 可执行 + 包含代码示例
"Wrap the HTTP call in a retry loop with `maxRetries=3` and initial delay of 500ms"
// Bad: 模糊描述
"Handle retries", "Fix the issue"
```

### 4.5 输入结构

Prompt 包含三个输入段：
1. `SUCCESSFUL CAPSULES` — 最多 8 个样本胶囊（gene/trigger/summary/outcome）
2. `EXISTING GENES` — 已有基因列表（id/category/signals_match），用于避免重复
3. `ANALYSIS` — 三维模式分析报告（高频/漂移/缺口）

**CE 启示**：BlueCortexCE 的 `StructuredExtractionService` prompt 可借鉴这种"分析报告 + 已有知识 + 样本"的三段式输入结构，让 LLM 在已有知识背景下进行结构化抽取。

---

## 5. 信号清洗 + ID 派生

### 5.1 `sanitizeSignalsMatch`（L355–L381）

```javascript
function sanitizeSignalsMatch(signals) {
  // 1. 去除时间戳后缀（10+ 位数字）
  sig = sig.replace(/[_-]\d{10,}$/g, '');
  // 2. 去除首尾下划线/连字符
  sig = sig.replace(/^[_-]+|[_-]+$/g, '');
  // 3. 纯数字信号 → 拒绝
  if (/^\d+$/.test(sig)) return;
  // 4. 纯工具名信号（cursor/vscode/vim...）→ 拒绝
  if (/^(cursor|vscode|...)[_-]?\d*$/i.test(sig)) return;
  // 5. 长度 < 3 → 拒绝
  if (sig.length < 3) return;
  // 6. 含 8+ 位数字序列（session ID）→ 拒绝
  if (/\d{8,}/.test(sig)) return;
}
```

### 5.2 `deriveDescriptiveId`（L325–L353）

当 LLM 给的 ID 不合格时，从信号匹配或摘要中派生描述性 ID：

```javascript
// 从 signals_match 前 3 个信号取词（≥3 字符，最多 6 词）
words = signals_match.slice(0, 3)
  .flatMap(s => s.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim().split(/\s+/))
  .filter(w => w.length >= 3 && words.length < 6);
// 备用：从 summary 取词
// 备用：从 strategy[0] 取词
// 最终：gene_distilled_{top-5-words-joined}
```

**设计亮点**：
- 信号清洗五步过滤，防止蒸馏结果被工具名/时间戳污染
- ID 派生确保即使 LLM 犯错也有可用结果

---

## 6. 基因合成：`synthesizeGeneFromPatterns`（L602–L675）

### 6.1 来源选择策略

```javascript
function chooseDistillationSource(data, analysis) {
  // 按 (成功次数×2 + 平均分) 排序，选择得分最高的基因分组
  const score = (g.total_count * 2) + (g.avg_score || 0);
}
```

### 6.2 信号匹配扩展

```javascript
// 1. 从触发信号频率统计取 top-6
signalsMatch = top_trigger_frequencies.slice(0, 6);
// 2. 用 learningSignals.expandSignals 扩展标签
//    (只取 problem: 和 area: 前缀标签)
const derivedTags = learningSignals.expandSignals(signalsMatch, summaryText)
  .filter(tag => tag.indexOf('problem:') === 0 || tag.indexOf('area:') === 0)
  .slice(0, 4);
// 3. 去重合并
signalsMatch = Array.from(new Set(signalsMatch.concat(derivedTags)));
```

### 6.3 类别推断

```javascript
function inferCategoryFromSignals(signals) {
  if (signals contains "error|fail|reliability") → "repair";
  if (signals contains "feature|capability|stagnation") → "innovate";
  return "optimize";
}
```

**CE 启示**：BlueCortexCE 可从 `ObservationEntity.observation_type` 和内容关键词推断"知识类别"（repair/innovate/optimize），用于分类存储和检索。

---

## 7. 失败蒸馏路径（L862–L1230）

### 7.1 失败数据收集

```javascript
function collectFailureDistillationData() {
  // 读取 events.jsonl 中 type='FailedCapsule' 的事件
  // 按 gene_id 分组
  // 过滤：avg_score < 0.5 的基因分组
}
```

### 7.2 失败蒸馏 Prompt

```javascript
function buildFailureDistillationPrompt(analysis, existingGenes, sampleCapsules) {
  // 类似成功蒸馏，但聚焦于：
  // 1. 失败根因分析
  // 2. 修复策略（rollback / preconditions / narrower validation）
  // 3. 预防性检查
}
```

### 7.3 修复基因合成

```javascript
function synthesizeRepairGeneFromFailures(data, analysis, existingGenes) {
  // 与成功路径类似，但：
  // 1. category 强制为 "repair"
  // 2. strategy 以 "Rollback" 或 "Add preconditions" 开头
  // 3. validation 命令更窄（如 "node --test" 而非 "npm test"）
}
```

**关键差异**：

| 维度 | 成功蒸馏 | 失败蒸馏 |
|------|---------|---------|
| 样本阈值 | 10 个 | 5 个 |
| 重试间隔 | 24h | 12h |
| 类别 | repair/innovate/optimize | 强制 repair |
| 策略起点 | Identify pattern + Apply change | Rollback + Add preconditions |

---

## 8. 幂等性保障机制

### 8.1 数据哈希

```javascript
function computeDataHash(capsules) {
  const ids = capsules.map(c => c.id || '').sort();
  return crypto.createHash('sha256').update(ids.join('|')).digest('hex').slice(0, 16);
}
```

### 8.2 双重保险

```javascript
function shouldDistill() {
  // 第一重：时间间隔检查
  const elapsed = Date.now() - new Date(state.last_distillation_at).getTime();
  if (elapsed < DISTILLER_INTERVAL_HOURS * 3600000) return false;
  // 第二重：数据哈希检查（数据无变化则跳过）
  if (state.last_data_hash === data.dataHash) return false;
}
```

### 8.3 蒸馏状态持久化

```javascript
// distill_state.json 结构
{
  last_distillation_at: "ISO timestamp",
  last_data_hash: "16-char sha256",
  last_gene_id: "gene_distilled_...",
  distillation_count: N,
}
```

**设计亮点**：
- 时间间隔 + 数据哈希双重保险，避免无意义的重复蒸馏
- 状态文件持久化，重启后仍然保持幂等性

**CE 启示**：BlueCortexCE 的 `StructuredExtractionService` 可用类似机制避免重复抽取——时间窗口 + 内容哈希。

---

## 9. `validateSynthesizedGene` 核心检查项（详见 doc 64）

以下检查在 doc 64 中已有详细分析，此处仅做索引：

| # | 检查项 | 关键阈值 |
|---|--------|---------|
| 1 | type === "Gene" | 必需 |
| 2 | id 存在且为 string | 必需 |
| 3 | category 存在 | 必需 |
| 4 | signals_match 非空 | ≥1 项 |
| 5 | signals_match 清洗后非空 | 过滤后 ≥1 |
| 6 | strategy ≥3 步 | 质量门槛 |
| 7 | constraints.forbidden_paths 包含 .git 或 node_modules | 安全 |
| 8 | validation 命令通过 policyCheck 白名单 | 安全 |
| 9 | schema_version 存在 | 默认 1.6.0 |
| 10 | ID 不与已有基因重复 | 重复则加 timestamp 后缀 |
| 11 | signals_match 不与已有基因完全重叠 | 防冗余 |

---

## 10. CE 翻译优先级

### P0（立即可落地）

1. **双路径知识提炼**：BlueCortexCE 应支持从成功 Observation 和失败 Observation 两条路径提炼结构化知识模板
2. **信号清洗五步法**：防止抽取结果被工具名、时间戳污染
3. **幂等性双保险**：时间窗口 + 内容哈希避免重复抽取

### P1（值得实现）

4. **模式分析三维度**：高频模式检测 + 策略漂移检测 + 覆盖缺口检测
5. **Marketplace-quality Summary**：抽取结果的 summary 应以"市场listing"标准编写
6. **类别推断**：从信号关键词自动推断知识类别（repair/innovate/optimize）

### P2（长期方向）

7. **蒸馏 Prompt 三段式**（样本 + 已有知识 + 分析报告）用于引导 LLM 抽取
8. **失败路径独立蒸馏**：对 FailedObservation 单独处理，生成修复性知识模板
9. **Gene ID 派生算法**：从信号组合自动生成描述性 ID

---

## 11. 关键常量速查

| 常量 | 值 | 影响 |
|------|-----|------|
| `DISTILLER_MIN_CAPSULES` | 10 | 成功蒸馏样本门槛 |
| `DISTILLER_MIN_SUCCESS_RATE` | 0.7 | 成功胶囊最低分数 |
| `DISTILLER_INTERVAL_HOURS` | 24 | 成功路径重复间隔 |
| `FAILURE_DISTILLER_MIN_CAPSULE` | 5 | 失败蒸馏样本门槛（更低） |
| `FAILURE_DISTILLER_INTERVAL_HOURS` | 12 | 失败路径重复间隔（更频繁） |
| `DISTILLED_MAX_FILES` | 12 | 蒸馏基因最大文件数 |
| `DISTILLED_ID_PREFIX` | `gene_distilled_` | 蒸馏基因 ID 前缀 |

---

## 12. 文件速查

| 文件 | 说明 |
|------|------|
| `distill_request.json` | 最近一次蒸馏请求元数据 |
| `distiller_log.jsonl` | 所有蒸馏操作的 append-only 日志 |
| `distiller_state.json` | 幂等性状态（last_distillation_at/data_hash/gene_id/count） |
| `distill_prompt_{timestamp}.txt` | 每次蒸馏的 prompt 快照（可审计） |

