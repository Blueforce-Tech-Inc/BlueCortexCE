# 10 — Skill Distiller：从经验 Capsule 到可复用 Gene

## 10.1 整体定位

`skillDistiller.js`（1344 行）是整个进化框架的**知识提炼中枢**。它的核心职责是：

> **将多次成功的进化经验（Capsule）蒸馏成一个通用的、可被其他 Agent 发现和复用的 Gene。**

```
Evolution Cycles (N 次成功)
     │
     ▼
Capsule Store (capsules.json / capsules.jsonl)
     │
     ▼
skillDistiller.js  ──►  Distillation Pipeline
     │                      1. collectDistillationData()
     │                      2. analyzePatterns()
     │                      3. buildDistillationPrompt()
     │                      4. LLM synthesis (外部调用)
     │                      5. validateSynthesizedGene()
     │                      6. upsertGene()
     ▼
Gene Store (genes.json) ──► Hub Marketplace
```

**与 Claude-Mem 的类比**：这类似于 Claude-Mem 中"把高频 Observation 提炼成结构化文档"的过程，但 EvoMap 走得更远——它用 LLM 直接生成可执行的 Gene 策略代码。

---

## 10.2 触发条件

```javascript
function shouldDistill() {
  // 1. 环境变量关闭检查
  if (SKILL_DISTILLER === 'false') return false;

  // 2. 间隔检查（默认 24 小时）
  const elapsed = Date.now() - last_distillation_at;
  if (elapsed < DISTILLER_INTERVAL_HOURS * 3600000) return false;

  // 3. 最近 10 个 capsule 中至少 7 个成功
  const recentSuccess = recent.slice(-10).filter(isSuccess).length;
  if (recentSuccess < 7) return false;

  // 4. 总成功 capsule ≥ 10
  if (totalSuccess < DISTILLER_MIN_CAPSULES) return false;

  return true;
}
```

**失败专用蒸馏**：`FAILURE_DISTILLER_MIN_CAPSULES=5`，`FAILURE_DISTILLER_INTERVAL_HOURS=12`，专门提炼失败模式生成 repair 基因。

---

## 10.3 数据收集（Step 1）

```javascript
function collectDistillationData() {
  // 1. 加载所有 capsule（兼容 JSON 和 JSONL 格式）
  const capsulesJson = readJsonIfExists('capsules.json', { capsules: [] });
  const capsulesJsonl = readJsonlIfExists('capsules.jsonl');
  let allCapsules = concat(capsulesJson.capsules, capsulesJsonl);

  // 2. 过滤成功 capsule（outcome === 'success' 且 score ≥ 0.7）
  const successCapsules = allCapsules.filter(c =>
    c.outcome.status === 'success' && c.outcome.score >= 0.7
  );

  // 3. 按 gene_id 分组，聚合 trigger/summary/score
  const grouped = {};
  successCapsules.forEach(c => {
    const geneId = c.gene || c.gene_id;
    grouped[geneId] = grouped[geneId] || { gene_id, capsules: [], ... };
    grouped[geneId].capsules.push(c);
    grouped[geneId].total_score += c.outcome.score;
    grouped[geneId].triggers.push(...c.trigger);
    grouped[geneId].summaries.push(c.summary);
  });

  // 4. 计算 data hash（同组 capsule → 同 hash，用于幂等跳过）
  const dataHash = sha256(ids.sort().join('|'));

  return { successCapsules, allCapsules, grouped, dataHash };
}
```

---

## 10.4 模式分析（Step 2）

```javascript
function analyzePatterns(data) {
  const report = {
    high_frequency: [],    // 基因出现 ≥5 次的高频模式
    strategy_drift: [],     // 同一基因早期 vs 最近的 summary 相似度 <0.6
    coverage_gaps: [],      // 信号出现过 ≥3 次但无基因覆盖
    success_rate: total_success / total_capsules,
  };

  // High frequency: 统计每个触发信号的频率
  // Strategy drift: 比较首个和末个 summary 的 Jaccard 相似度
  // Coverage gaps: 对比 events 中的信号频率 vs grouped 中的覆盖情况
}
```

---

## 10.5 LLM 蒸馏提示词工程

`buildDistillationPrompt()` 生成一个高度结构化的 prompt。关键约束：

| 约束 | 规则 |
|------|------|
| Gene ID | 必须以 `gene_distilled_` 开头 + 描述性 kebab-case |
| 禁止 | 时间戳、UUID、工具名（cursor/vscode）、随机数 |
| 信号 | 3-7 个通用领域词（`lowercase_snake_case`） |
| 策略 | 5-10 个可执行步骤（动词开头的祈使句） |
| 约束 | `max_files ≤ 12`，必须包含 `.git` 和 `node_modules` |
| 验证命令 | 必须以 `node / npm / npx` 开头 |

---

## 10.6 合成与验证管道

```
LLM Response (raw text)
        │
        ▼
extractJsonFromLlmResponse()  ← 提取 { ... } JSON 对象（深度扫描）
        │
        ▼
validateSynthesizedGene()      ← 多重校验
        │
        ├── type === 'Gene' ✓
        ├── id 以 gene_distilled_ 开头 ✓
        ├── signals_match 非空 ✓
        ├── strategy ≥ 3 步 ✓
        ├── constraints.forbidden_paths 包含 .git 或 node_modules ✓
        ├── validation 命令以 node/npm/npx 开头 ✓
        ├── 无时间戳/UUID/工具名 ✓
        └── 不与现有基因完全重复 ✓
        │
        ▼
deriveDescriptiveId()  ← LLM 给的名称不合规时，从内容自动推导
        │
        ▼
upsertGene() → genes.json
```

### deriveDescriptiveId 自动命名

当 LLM 输出不合规的 ID（如包含 timestamp）时，从信号、summary、strategy 中提取词干：

```javascript
function deriveDescriptiveId(gene) {
  let words = [];
  // 从 signals_match 取前 3 个信号（取词干，最多 6 个词）
  gene.signals_match.slice(0, 3).forEach(s => {
    String(s).replace(/[^a-z0-9]+/g, ' ').split(/\s+/)
      .forEach(w => { if (w.length >= 3 && words.length < 6) words.push(w); });
  });
  return DISTILLED_ID_PREFIX + uniqueWords.slice(0, 5).join('-');
  // → gene_distilled_retry-backoff-circuit-breaker
}
```

---

## 10.7 失败修复专用蒸馏

```javascript
const FAILURE_DISTILLER_MIN_CAPSULES = 5;   // 只需要 5 个失败 capsule
const FAILURE_DISTILLER_INTERVAL_HOURS = 12; // 每 12 小时一次

const REPAIR_DISTILLED_ID_PREFIX = 'gene_repair_distilled_';
```

**修复基因的特点**：
- ID 前缀 `gene_repair_distilled_`
- 关注"什么情况下失败"而非"如何成功"
- 策略偏向验证 + 回滚（而非直接应用变更）
- signals_match 包含 `problem:*` 类型的信号

---

## 10.8 幂等性保证

```javascript
// 数据没变化（hash 相同）→ 跳过
if (state.last_data_hash === data.dataHash) {
  return { ok: false, reason: 'idempotent_skip' };
}

// 信号完全重复的基因 → 拒绝
if (overlap === newSet.size && overlap === egSet.size) {
  errors.push('signals_match fully overlaps with existing gene');
}
```

---

## 10.9 与 Claude-Mem 的类比

| EvoMap skillDistiller | Claude-Mem 对应机制 |
|----------------------|-------------------|
| Capsule → Gene 蒸馏 | Observation → Summary 提炼 |
| LLM 生成 Gene 策略 | LLM 生成 Summary 叙事 |
| `synthesizeGeneFromPatterns()` fallback | 无 fallback（直接用 Observation） |
| 信号去噪（sanitizeSignalsMatch） | 无专门去噪（向量检索天然抗噪） |
| 幂等跳过（data hash） | 无等幂机制 |
| 失败修复蒸馏（repair_distilled） | 无失败专门化 |
| Hub marketplace 发布 | 无直接发布机制 |
| validation 命令安全校验 | 无等效校验（SDK 层面无约束） |

---

_Next: [11-hub-integration.md](./11-hub-integration.md) — Hub 市场集成_
