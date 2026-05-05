# Doc 101 — EvoMap/evolver 核心记忆架构模式深度分析

> **角色**：从 `src/gep/memoryGraph.js`（788行）和相关模块源码，提炼对 BlueCortexCE 有直接借鉴价值的**核心架构模式**。不是重复已有分片的细节，而是**跨分片综合 + 源码级验证**。
>
> **源码**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/memoryGraph.js`（v1.47.0）
> **依赖模块**：`memoryGraphAdapter.js`、`signals.js`、`narrativeMemory.js`、`contentHash.js`、`executionTrace.js`
> **前置阅读**：doc 18（架构快照）、doc 19（outcome 推断链）、doc 36（8 大设计原则）
> **最后更新**：2026-05-05

---

## §1 核心架构：事件日志 + 可变状态双层分离

### 1.1 模式描述

Evolver 的记忆系统采用**两个存储层次**：

| 文件 | 性质 | 写操作 | 读操作 |
|------|------|--------|--------|
| `memory_graph.jsonl` | **Append-only 事件日志** | `appendJsonl()` 只追加，永不修改或删除 | `tryReadMemoryGraphEvents()` 尾部读取 |
| `memory_graph_state.json` | **Mutable 可变状态** | `writeJsonAtomic()` 全量覆盖 | `readJsonIfExists()` 读取 |

**关键设计原则**：图是只增的（immudb-style），状态是临时的。

### 1.2 源码证据

```javascript
// memoryGraph.js L86–L88: appendJsonl
function appendJsonl(filePath, obj) {
  const dir = path.dirname(filePath);
  ensureDir(dir);
  fs.appendFileSync(filePath, JSON.stringify(obj) + '\n', 'utf8');  // 只追加
}

// memoryGraph.js L92–L102: readJsonIfExists
function readJsonIfExists(filePath, fallback) {
  try {
    if (!fs.existsSync(filePath)) return fallback;
    const raw = fs.readFileSync(filePath, 'utf8');
    if (!raw.trim()) return fallback;
    return JSON.parse(raw);
  } catch (e) { return fallback; }
}

// memoryGraph.js L104–L111: writeJsonAtomic（先写.tmp再rename，防撕裂）
function writeJsonAtomic(filePath, obj) {
  const dir = path.dirname(filePath);
  ensureDir(dir);
  const tmp = `${filePath}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(obj, null, 2) + '\n', 'utf8');
  fs.renameSync(tmp, filePath);  // POSIX rename 是原子的
}
```

### 1.3 状态机的 causal memory 循环

`memory_graph_state.json` 只存储 `last_action`，这是实现**跨周期因果推理**的关键：

```javascript
// memoryGraph.js L484–L506: recordAttempt 写入 mutable state
state.last_action = {
  action_id: actionId,
  signal_key: signalKey,
  signals: signals,
  gene_id: geneId,
  had_error: hasErrorSignal(signals),
  created_at: ts,
  outcome_recorded: false,        // 标记：outcome 尚未闭合
  baseline_observed: observations, // 本次 attempt 的观测作为 baseline
};
writeJsonAtomic(statePath, state);
```

下一轮 start 时，`recordOutcomeFromState()` 读取这个 `last_action`，对比 `baseline_observed` 和当前 `observations`，推断 action N 的 outcome：

```javascript
// memoryGraph.js L653–L659: outcome 推断
const inferred = inferOutcomeEnhanced({
  prevHadError: !!last.had_error,           // attempt 时的错误状态
  currentHasError: hasErrorSignal(signals),  // 当前（下一轮）错误状态
  baselineObserved: last.baseline_observed,
  currentObserved: observations,
});
```

### 1.4 BlueCortexCE 借鉴价值

| 方面 | Evolver | BlueCortexCE 当前 | 借鉴思路 |
|------|---------|-------------------|----------|
| 事件持久化 | JSONL append-only | Observations 写入 DB（可覆盖） | 关键学习事件（extraction results / feedback）可走 append-only 路径 |
| 状态传递 | `memory_graph_state.json` + `dormant_hypothesis.jsonl` | SessionEntity 存储当前 session 状态 | 跨 session 的"上一轮outcome"传递；休眠假设恢复 |
| 原子写 | `.tmp + rename` | JPA transaction | 轻量替代方案：对于非 DB 存储（文件），使用 `.tmp + rename` |

---

## §2 五大事件类型与生命周期

### 2.1 事件类型一览

```javascript
// memoryGraph.js 各 record* 函数产生的事件类型
recordSignalSnapshot()   → kind: 'signal'        // 周期开始：信号快照
recordHypothesis()       → kind: 'hypothesis'    // 基因选定后：因果假设
recordAttempt()          → kind: 'attempt'        // 变异执行后：行动记录
recordOutcomeFromState() → kind: 'outcome'        // 下一周期：推断结果（含 confidence edge）
recordExternalCandidate()→ kind: 'external_candidate' // Hub 发现的新基因
```

### 2.2 因果链设计

```
signal (周期N开始)
  ↓ signals + observations → computeSignalKey()
hypothesis (基因选择后)
  ↓ gene + mutation + personality → causal reasoning
attempt (solidify 执行后)
  ↓ last_action 存入 state.json（outcome_recorded=false）
--- 周期结束 ---
(周期N+1开始)
  ↓ 读取 last_action，对比 baseline vs current
outcome (推断得出)
  ↓ confidence edge 写回 graph（供后续查询）
```

**核心思想**：outcome 的闭合发生在**下一个周期**，而非当前周期立即打分。这是"旁路记忆"模式的精髓——**观察滞后于行动，结果需要回溯**。

### 2.3 BlueCortexCE 借鉴价值

BlueCortexCE 的 `ObservationEntity` 可以借鉴这套生命周期：
- **signal 对应**：用户 prompt / tool result / session context
- **hypothesis 对应**：extraction template 匹配 / strategy selection
- **attempt 对应**：structured extraction 执行
- **outcome 对应**：feedback record / extraction 结果验证

关键差距：BlueCortexCE 目前没有显式的"outcome 推断"机制（feedback 是主动上报的，而非从 observations 推断）。

---

## §3 Stable Signal Key：跨周期匹配的基础

### 3.1 问题

同一个错误在不同运行中可能产生不同的文本描述（路径不同、参数不同、行号不同），导致历史匹配失效。

### 3.2 解决方案：normalizeErrorSignature + computeSignalKey

```javascript
// memoryGraph.js L28–L47: normalizeErrorSignature
function normalizeErrorSignature(text) {
  const s = String(text || '').trim();
  return s
    .toLowerCase()
    .replace(/[a-z]:\\[^ \n\r\t]+/gi, '<path>')   // Windows 路径
    .replace(/\/[^ \n\r\t]+/g, '<path>')           // Unix 路径
    .replace(/\b0x[0-9a-f]+\b/gi, '<hex>')           // 十六进制
    .replace(/\b\d+\b/g, '<n>')                     // 数字
    .replace(/\s+/g, ' ')
    .slice(0, 220);
}

// memoryGraph.js L56–L66: computeSignalKey（信号→稳定键）
function computeSignalKey(signals) {
  const list = normalizeSignalsForMatching(signals);
  // errsig:xxx → errsig_norm:<stableHash>，其余原始保留
  const uniq = Array.from(new Set(list.filter(Boolean))).sort();
  return uniq.join('|') || '(none)';
}
```

### 3.3 效果

| 原始信号 | 归一化后 key |
|----------|-------------|
| `errsig:Error: /Users/foo/src/bar.js:42 Cannot read property` | `errsig_norm:a1b2c3d4` |
| `errsig:Error: /home/baz/src/baz.js:99 Cannot read property` | `errsig_norm:a1b2c3d4`（相同！） |

### 3.4 BlueCortexCE 借鉴

`ObservationEntity.extractedData` 中 `error_signature` 字段可应用同样归一化算法。参考 doc 22 的提案：`normalizeErrorSignature(errMsg)` → `stableHash` → 存入 `extractedData.error_sig_norm`。

---

## §4 时间衰减 + Laplace 平滑：稀疏数据的概率估计

### 4.1 双重聚合

```javascript
// memoryGraph.js L181–L216: aggregateEdges - 按 (signal_key, gene_id) 聚合
function aggregateEdges(events) {
  const map = new Map();
  for (const ev of events) {
    if (ev.kind !== 'outcome') continue;
    const k = `${signalKey}::${geneId}`;
    const cur = map.get(k) || { signalKey, geneId, success: 0, fail: 0, last_ts: null };
    if (status === 'success') cur.success += 1;
    else if (status === 'failed') cur.fail += 1;
    // ...
    map.set(k, cur);
  }
  return map;
}

// memoryGraph.js L218–L246: aggregateGeneOutcomes - 按 gene_id 独立聚合（prior）
function aggregateGeneOutcomes(events) { /* 同上，但 key = geneId */ }
```

### 4.2 时间衰减

```javascript
// memoryGraph.js L163–L173: 半衰期指数衰减
function decayWeight(updatedAtIso, halfLifeDays) {
  const ageDays = (Date.now() - Date.parse(updatedAtIso)) / (1000 * 60 * 60 * 24);
  return Math.pow(0.5, ageDays / halfLifeDays);  // 30天半衰: 0.5^(age/30)
}
```

### 4.3 Laplace 平滑 + 混合评分

```javascript
// memoryGraph.js L247–L254: edgeExpectedSuccess
const p = (succ + 1) / (total + 2);   // Laplace 平滑：避免 0/1
const w = decayWeight(e.last_ts, 30);
return { p, w, value: p * w };       // 概率 × 时间权重

// memoryGraph.js L293–L296: getMemoryAdvice 双链混合
const combined = info.best + info.prior * 0.12;  // signal-gene 边 + gene prior
// prior_attempts < 2 且 prior < 0.12 → ban（全局差基因）
```

**核心公式**：`score = edge_p(signal,gene) × decay(signal,gene,30d) + gene_prior(gene) × decay(gene,45d) × 0.12`

### 4.4 BlueCortexCE 借鉴

`SearchService` 可新增"成功率排序"：在 observation 写入时额外记录 `outcome_status`（success/failure），查询时按时间衰减的 success ratio 排序。参考 doc 20 的时间衰减提案。

---

## §5 Dormant Hypothesis：中断恢复与记忆永不丢失

### 5.1 机制

Preflight 阶段检测到以下条件时，写入 `dormant_hypothesis.jsonl` 并中断本轮：
- 另一 evolver 进程正在运行（竞态检测）
- 活跃会话数超过 QUEUE_MAX
- 系统 CPU 负载过高

下一轮 start 时，读取 dormant 文件恢复信号、基因、变异状态：

```javascript
// evolve.js L1098–L1111: 休眠恢复
const dormantHypothesis = readDormantHypothesis();
if (dormantHypothesis) {
  signals = dormantHypothesis.signals || [];
  // 清除休眠文件 ...
}
```

### 5.2 BlueCortexCE 借鉴

BlueCortexCE 的任务（如 structured extraction pipeline）可以借鉴：
- 任务开始时写入 `dormant_<taskId>.json`
- 完成时删除
- 启动时检查并恢复

这比 JPA `pending_task` 表更轻量，且天然支持进程崩溃恢复。

---

## §6 Narrative Memory：可读历史摘要

### 6.1 滚动裁剪策略

```javascript
// narrativeMemory.js L55–L75: 双重裁剪
function trimNarrative(content) {
  // 1. 条目数量上限
  while (entries.length > MAX_NARRATIVE_ENTRIES) { entries.shift(); }
  // 2. 总大小上限
  if (result.length > MAX_NARRATIVE_SIZE) {
    const keep = Math.max(1, entries.length - 5);
    result = header + entries.slice(-keep).join('');
  }
  return result;
}
```

- `MAX_NARRATIVE_ENTRIES = 30`
- `MAX_NARRATIVE_SIZE = 12000` bytes

### 6.2 Narrative vs Graph 互补

| 维度 | Memory Graph | Narrative Memory |
|------|-------------|-----------------|
| 格式 | JSONL（机器解析） | Markdown（人类阅读） |
| 用途 | 算法决策（基因选择） | 人工复盘（上下文理解） |
| 大小 | 无上限（jsonl 追加） | 有上限（自动裁剪） |
| 内容 | 结构化事件（signal/hypothesis/attempt/outcome） | 自然语言摘要（决策理由） |

### 6.3 BlueCortexCE 借鉴

`SummaryEntity` 可以扮演类似的"机器可读 + 人类可读"双重角色：
- 机器端：structured JSON 供检索排序
- 人类端：narrative text 供快速复盘

---

## §7 Content-Addressable Asset ID：去重与防篡改

### 7.1 规范 JSON + SHA-256

```javascript
// contentHash.js canonicalize(): 递归排序键 + 规范序列化
// → SHA-256 摘要 → "sha256:<hex>"

// contentHash.js computeAssetId(): 排除 asset_id 自身后计算
function computeAssetId(obj) {
  const clean = {};
  for (const k of Object.keys(obj)) {
    if (exclude.has(k)) continue;  // 排除 asset_id
    clean[k] = obj[k];
  }
  const canonical = canonicalize(clean);
  return 'sha256:' + crypto.createHash('sha256').update(canonical, 'utf8').digest('hex');
}
```

### 7.2 BlueCortexCE 借鉴

`ObservationEntity.content_hash` 已存在但目前可能未充分利用。可扩展：
- `extractedData` JSONB 内容做 SHA-256 作为 deduplication key
- `AssetEntity.asset_id` 改用 content hash（而非 UUID），实现跨节点内容去重

---

## §8 Execution Trace：跨节点安全共享

### 8.1 脱敏规则

```javascript
// executionTrace.js desensitizeFilePath(): 只保留 basename + ext
// Error: 提取 type signature，不保留具体消息
// Code content: 永远不发送，只发送统计指标
// Environment: 全部剥离
```

### 8.2 三级 Trace Level

| Level | 内容 | Hub 共享 |
|-------|------|---------|
| `none` | 无 | ❌ |
| `minimal` | 统计指标（行数/文件数/工具链） | ✅ |
| `standard` | 详细指标 | ✅ |

### 8.3 BlueCortexCE 借鉴

如果 BlueCortexCE 要做跨节点经验共享（如 skill publishing），可参考这套脱敏框架：
- 观察内容（不含 prompt 原文）→ 统计脱敏 → 可共享摘要

---

## §9 关键配置参数参考

```javascript
// memoryGraph.js
const HALF_LIFE_DAYS_SIGNAL_GENE = 30;    // signal→gene 边半衰期
const HALF_LIFE_DAYS_GENE = 45;           // gene 独立 prior 半衰期
const BAN_THRESHOLD_ATTEMPTS = 2;          // 低效路径ban：≥2次尝试
const BAN_THRESHOLD_EFFICIENCY = 0.18;    // 效率阈值：<0.18 → ban
const JACCARD_SIM_THRESHOLD = 0.34;       // 信号相似度阈值

// narrativeMemory.js
const MAX_NARRATIVE_ENTRIES = 30;          // Markdown 条目上限
const MAX_NARRATIVE_SIZE = 12000;          // 总大小上限（bytes）

// contentHash.js
const SCHEMA_VERSION = '1.6.0';            // 规范版本（bump on breaking change）
```

---

## §10 模式总结：7大可借鉴架构

| # | 模式 | BlueCortexCE 落地建议 | 优先级 |
|---|------|----------------------|--------|
| 1 | Append-only 事件日志 + 可变状态分离 | 关键学习事件走 JSONL 追加路径（extraction results / feedback） | P1 |
| 2 | Stable Signal Key（错误归一化） | `extractedData.error_sig_norm` = `stableHash(normalizeErrorSignature())` | P1 |
| 3 | 时间衰减 + Laplace 平滑概率 | `SearchService` 按 success ratio × decay 排序 | P2 |
| 4 | Dormant Hypothesis 中断恢复 | 任务持久化（dormant JSON）用于崩溃恢复 | P2 |
| 5 | Narrative + Graph 双重历史 | `SummaryEntity` 兼作"机器可读 + 人类可读" | P2 |
| 6 | Content-Addressable Asset ID | `ObservationEntity.content_hash` 充分利用；跨节点去重 | P2 |
| 7 | Execution Trace 脱敏 | 跨节点经验共享时的隐私保护框架 | P3 |

---

## 附：源码文件清单

| 文件 | 行数 | 核心职责 |
|------|------|---------|
| `src/gep/memoryGraph.js` | 788 | 核心记忆图：事件存储、信号key、聚合、评分、outcome推断 |
| `src/gep/memoryGraphAdapter.js` | ~200 | Local/Remote 双适配器接口 |
| `src/gep/signals.js` | ~445 | 信号提取、分类、归一化、去重 |
| `src/gep/narrativeMemory.js` | ~110 | Markdown 可读历史摘要 |
| `src/gep/contentHash.js` | ~70 | 规范JSON + SHA-256 资产ID |
| `src/gep/executionTrace.js` | ~200 | 脱敏执行跟踪，跨节点共享 |
| `src/gep/localStateAwareness.js` | ~250 | 节点身份、session scope、持久化状态 |

---

**下一步**：更新 [`11-research-backlog.md`](./11-research-backlog.md) 标记 doc 101 完成；建议优先推进 P1 的"Stable Signal Key"和"Append-only 事件日志"两个模式的 BlueCortexCE 落地。
