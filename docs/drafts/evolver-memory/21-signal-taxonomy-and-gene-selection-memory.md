# Signal Taxonomy 与基因选择中的记忆匹配机制

> **角色**：深入分析 Evolver `learningSignals.js` + `memoryGraph.js`（`getMemoryAdvice`、`scoreTagOverlap`）的信号分类与基因匹配机制，为 BlueCortexCE 的**观察类型排序**提供可落地借鉴。  
> **数据来源**：`EvoMap/evolver/src/gep/learningSignals.js`、`src/gep/memoryGraph.js`（§ `getMemoryAdvice` / `scoreTagOverlap` / `expandSignals`）、`src/gep/signals.js`。  
> **前置**：先读 [`09-aspect-bluecortex-bridge.md`](./09-aspect-bluecortex-bridge.md) §3.2 与 §4 P0/P1。  
> **最后更新**：2026-04-19

---

## 1. 核心概念：为什么需要 Signal Taxonomy？

Evolver 的记忆系统面对的是一个**持续运行的进化循环**：每次循环从工作区提取信号（errors、bottlenecks、capability gaps 等），根据信号选择基因（Gene = 行为策略模板），执行后评估结果。

**问题**：信号是**原始字符串列表**（如 `["log_error", "perf_bottleneck", "user_feature_request:..."]`），直接用于匹配是粗糙的：
- `"errsig:..."` 的原始错误文本每次不同，但本质是同一类错误
- `"user_feature_request:..."` 带有详细描述，前缀相同应该归为同一类
- 不同信号之间有语义关联（如 `error` + `repair` 关联）

**解决方案**：Signal Taxonomy = **规范化 → 扩展 → 打标签 → 评分** 四步。

---

## 2. 规范化层：`computeSignalKey` 与 `normalizeSignalsForMatching`

### 2.1 `computeSignalKey`（memoryGraph.js）

```javascript
function computeSignalKey(signals) {
  const list = normalizeSignalsForMatching(signals);
  const uniq = Array.from(new Set(list.filter(Boolean))).sort();
  return uniq.join('|') || '(none)';
}
```

- 对信号列表去重、排序、用 `|` 连接，得到**稳定键**
- 相同信号集合无论顺序如何，得到的 key 相同
- 用于：事件关联、记忆图边聚合

### 2.2 `normalizeSignalsForMatching`（memoryGraph.js）

```javascript
function normalizeSignalsForMatching(signals) {
  const list = Array.isArray(signals) ? signals : [];
  const out = [];
  for (const s of list) {
    const str = String(s || '').trim();
    if (!str) continue;
    if (str.startsWith('errsig:')) {
      const norm = normalizeErrorSignature(str.slice('errsig:'.length));
      if (norm) out.push(`errsig_norm:${stableHash(norm)}`);
      continue;
    }
    out.push(str);
  }
  return out;
}
```

**关键**：`errsig:` 前缀的错误原始文本经 `normalizeErrorSignature` 归一化：
- Windows 路径 `C:\foo\bar` → `<path>`
- Unix 路径 `/foo/bar` → `<path>`
- 十六进制数 `0x1A2B` → `<hex>`
- 普通数字 `42` → `<n>`
- 结果用 `stableHash` 摘要，只保留 hash 前缀

→ 同一类错误（路径不同、数字不同）产生相同的归一化 key

### 2.3 `normalizeErrorSignature`（memoryGraph.js）

```javascript
function normalizeErrorSignature(text) {
  const s = String(text || '').trim();
  if (!s) return null;
  return (
    s
      .toLowerCase()
      .replace(/[a-z]:\\[^ \n\r\t]+/gi, '<path>')
      .replace(/\/[^ \n\r\t]+/g, '<path>')
      .replace(/\b0x[0-9a-f]+\b/gi, '<hex>')
      .replace(/\b\d+\b/g, '<n>')
      .replace(/\s+/g, ' ')
      .slice(0, 220)
  );
}
```

- 保留错误文本结构（异常类型、关键短语），去掉具体值
- 截断到 220 字符防止超长

---

## 3. 扩展层：`expandSignals`（learningSignals.js）

### 3.1 函数签名

```javascript
function expandSignals(signals, extraText) {
  // signals: 原始信号数组
  // extraText: 额外文本（基因 summary 等），用于扩大关键词匹配范围
}
```

### 3.2 核心逻辑

**Step 1：基础展开**
```javascript
for (const signal of raw) {
  add(tags, signal);           // 保留原始信号
  const base = signal.split(':')[0];  // 取前缀
  if (base && base !== signal) add(tags, base);  // 添加前缀
}
```
- `"user_feature_request:add dark mode"` → 添加 `"user_feature_request"` 和 `"user_feature_request:add dark mode"` 两个 tag

**Step 2：关键词模式匹配**

| 模式（正则） | 添加的标签 |
|-------------|-----------|
| `error\|exception\|failed\|unstable\|log_error\|runtime\|429` | `problem:reliability`, `action:repair` |
| `protocol\|prompt\|audit\|gep\|schema\|drift` | `problem:protocol`, `action:optimize`, `area:prompt` |
| `perf\|performance\|bottleneck\|latency\|slow\|throughput` | `problem:performance`, `action:optimize` |
| `feature\|capability_gap\|user_feature_request\|external_opportunity\|stagnation` | `problem:capability`, `action:innovate` |
| `stagnation\|plateau\|steady_state\|saturation\|empty_cycle_loop\|loop_detected\|recurring` | `problem:stagnation`, `action:innovate` |
| `task\|worker\|heartbeat\|hub\|commitment\|assignment\|orchestration` | `area:orchestration` |
| `memory\|narrative\|reflection` | `area:memory` |
| `skill\|dashboard` | `area:skills` |
| `validation\|canary\|rollback\|constraint\|blast radius\|destructive` | `risk:validation` |

**Step 3：去重**
```javascript
return unique(tags);  // Set 去重
```

### 3.3 设计思想

**核心洞察**：原始信号是**扁平的字符串**，但它们背后有**隐含的语义维度**：
- 问题类型维度（reliability / performance / capability / stagnation）
- 行动导向维度（repair / optimize / innovate）
- 领域维度（orchestration / memory / skills / prompt）
- 风险维度（validation）

`expandSignals` 通过**启发式正则**把这些隐含维度显式化为可计算的标签。

---

## 4. 基因标签：`geneTags` 与 `scoreTagOverlap`

### 4.1 `geneTags(gene)`

```javascript
function geneTags(gene) {
  if (!gene || typeof gene !== 'object') return [];
  let inputs = [];
  if (gene.category) inputs.push('action:' + String(gene.category).toLowerCase());
  if (Array.isArray(gene.signals_match)) inputs = inputs.concat(gene.signals_match);
  if (typeof gene.id === 'string') inputs.push(gene.id);
  if (typeof gene.summary === 'string') inputs.push(gene.summary);
  return expandSignals(inputs, '');
}
```

- 从基因的 `category`、`signals_match`（显式声明匹配的信号）、`id`、`summary` 构建输入
- 同样经过 `expandSignals` 扩展，生成基因的语义标签

### 4.2 `scoreTagOverlap(gene, signals)`

```javascript
function scoreTagOverlap(gene, signals) {
  const signalTags = expandSignals(signals, '');
  const geneTagList = geneTags(gene);
  if (signalTags.length === 0 || geneTagList.length === 0) return 0;
  const signalSet = new Set(signalTags);
  let hits = 0;
  for (const tag of geneTagList) {
    if (signalSet.has(tag)) hits++;
  }
  return hits;
}
```

- 计算当前信号集合的扩展标签与基因标签的**重叠数**
- 这是一个简单的集合交集计数，返回匹配标签数量

---

## 5. 记忆驱动的基因选择：`getMemoryAdvice`（memoryGraph.js）

这是整个信号-基因匹配机制的核心：**基于历史记忆选择最优基因**。

### 5.1 概览

```
输入：当前 signals + 可用 genes
输出：preferredGeneId + bannedGeneIds + explanation
```

### 5.2 信号相似性发现（Jaccard）

```javascript
const curSignals = Array.isArray(signals) ? signals : [];
const curKey = computeSignalKey(curSignals);

// 首先尝试精确匹配当前 signal key
candidateKeys.push({ key: curKey, sim: 1 });
seenKeys.add(curKey);

// 然后从历史事件中找 Jaccard ≥ 0.34 的相似信号
for (const ev of events) {
  const sigs = ev.signal?.signals || [];
  const sim = jaccard(curSignals, sigs);
  if (sim >= 0.34) {
    candidateKeys.push({ key: k, sim });
  }
}
```

**Jaccard 相似度**：
```javascript
function jaccard(aList, bList) {
  const aNorm = normalizeSignalsForMatching(aList);
  const bNorm = normalizeSignalsForMatching(bList);
  const a = new Set(aNorm.map(String));
  const b = new Set(bNorm.map(String));
  if (a.size === 0 && b.size === 0) return 1;
  if (a.size === 0 || b.size === 0) return 0;
  let inter = 0;
  for (const x of a) if (b.has(x)) inter++;
  const union = a.size + b.size - inter;
  return union === 0 ? 0 : inter / union;
}
```

- 阈值 0.34（约 1/3 交集）比较宽松，允许泛化匹配
- 归一化后的信号比较，过滤了具体值差异

### 5.3 边置信度计算

对于每个 `(signalKey, geneId)` 边，计算历史成功率：

```javascript
function edgeExpectedSuccess(edge, opts) {
  const succ = edge.success || 0;
  const fail = edge.fail || 0;
  const total = succ + fail;
  const p = (succ + 1) / (total + 2);        // Laplace 平滑
  const halfLifeDays = opts?.half_life_days ?? 30;
  const w = decayWeight(edge.last_ts, halfLifeDays);
  return { p, w, total, value: p * w };        // value = p × w
}
```

- **Laplace 平滑**：`(+1)/(+2)` 避免 0 次尝试时的 0 或 1 极端概率
- **指数半衰衰减**：边的历史越老，权重越低；**信号×基因边用 30 天**半衰，**基因全局先验用 45 天**半衰（更长记忆）
- **最终分数**：`value = p × w`

### 5.4 加权组合

```javascript
const combined = info.best > 0
  ? info.best + info.prior * 0.12    // 有信号-基因边时，基因先验辅助
  : info.prior * 0.4;                 // 无边时，纯基因先验
```

- `best`：当前信号相似匹配 × 边置信度（最高值）
- `prior`：基因全局成功率的衰减加权
- 系数 0.12 和 0.4 是经验参数，控制信号驱动 vs 经验驱动的比重

### 5.5 低效基因抑制（Banning）

```javascript
// 多次失败 + 低置信度 → ban
if (!driftEnabled && info.attempts >= 2 && info.best < 0.18) {
  bannedGeneIds.add(geneId);
}

// 基因全局差且信号边稀疏 → ban
if (!driftEnabled && info.attempts < 2 && info.prior_attempts >= 3 && info.prior < 0.12) {
  bannedGeneIds.add(geneId);
}
```

- `driftEnabled`（随机探索模式）时跳过抑制，允许探索
- 0.18 和 0.12 是硬阈值，防止反复选择已知差的基因

---

## 6. 与 BlueCortexCE 的对应关系

| Evolver 概念 | BlueCortexCE 对应 | 借鉴要点 |
|-------------|------------------|---------|
| `computeSignalKey` | **观察去重 / 规范化** | 对重复错误做规范化签名，避免同一类问题重复建索引 |
| `expandSignals` 标签体系 | **观察类型（observation_type）+ 质量分维度** | 定义显式维度（reliability、performance 等）驱动排序 |
| `geneTags` | **基因/策略的语义标签**（CE 中暂无直接对应） | 未来当 CE 引入"策略偏好"时可用类似标签 |
| `scoreTagOverlap` | **观察 relevance scoring** | 命中时考虑语义标签重叠数 |
| `getMemoryAdvice` | **历史成功率的检索增强** | 参考边置信度 + 时间衰减公式，对高频失败的模式降权 |
| Jaccard ≥ 0.34 | **语义相似度阈值** | 设置宽松阈值允许泛化匹配 |
| Banning 机制 | **结果反馈驱动排序抑制** | 对连续失败的模式在排序中降权（已在 `20-time-decay` 中讨论） |
| Laplace 平滑 | **冷启动平滑** | 新观察/新策略的默认置信度不过低 |

---

## 7. 实施建议（可执行）

### P0：立即可落地

1. **规范化错误签名**：在 `ObservationService` 写入路径，对同类错误的堆栈做规范化（路径→`<path>`，数字→`<n>`），存入 `extracted_data.error_signature_normalized` 字段
2. **观察类型显式标签**：为 `observation_type` 字段定义标准化标签集（参考 `expandSignals` 的 problem/action/area 维度），支持精确过滤

### P1：下次迭代

3. **历史成功率衰减**：在 `SearchService` 排序中引入"该类观察的过去检索成功率"因子，参考 `edgeExpectedSuccess` 的 Laplace + 半衰公式
4. **相似观察聚合**：用 Jaccard 归一化信号做 pre-filter，减少向量检索的噪音

### P2：设计时考虑

5. **策略标签（未来）**：如果 BlueCortexCE 引入"用户偏好策略"或"项目类型策略"，参考 `geneTags` + `scoreTagOverlap` 为策略打标签
6. **Ban 机制（可选）**：对持续低质量匹配的观察模式做抑制

---

## 8. 相关文档

- 总索引：[`index.md`](./index.md)
- 方面对照（Evolver ↔ CE）：[`09-aspect-bluecortex-bridge.md`](./09-aspect-bluecortex-bridge.md) §3.2、§4
- CE 实现映射：[`10-aspect-bluecortex-implementation-map.md`](./10-aspect-bluecortex-implementation-map.md)
- 时间衰减专题：[`20-time-decay-and-fail-degradation.md`](./20-time-decay-and-fail-degradation.md)
- evolve 循环中的记忆顺序：[`19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md`](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md)
- 研究 backlog：[`11-research-backlog.md`](./11-research-backlog.md)
