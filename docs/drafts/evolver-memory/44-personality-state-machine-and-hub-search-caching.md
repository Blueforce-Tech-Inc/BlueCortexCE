# 44. Personality State Machine + Hub Search 管线

**分析目标**：深度剖析 `personality.js`（人格状态机）和 `hubSearch.js`（Hub 搜索两相管线），为 BlueCortexCE 提炼多维度人格自适应和外部知识库查询的借鉴思路。

**数据来源**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/personality.js`、`/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/hubSearch.js`

**最后更新**：2026-04-24（v44 新增：**personality.js** 人格状态机完整分析（五维状态 + 自然选择 + 信号驱动突变 + 反思驱动突变）/ **hubSearch.js** 两相搜索管线（search_only免费 + payload缓存 + LRU + 并行语义搜索））

---

## §1 Personality State Machine

### 1.1 五维人格状态

```javascript
function defaultPersonalityState() {
  return {
    type: 'PersonalityState',
    rigor: 0.7,         // 协议遵守 / 严谨性
    creativity: 0.35,    // 创新 / 探索意愿
    verbosity: 0.25,    // 输出详细程度
    risk_tolerance: 0.4, // 风险承受度
    obedience: 0.85,     // 指令服从度
  };
}
```

**设计特点**：
- **五维正交**：每个维度独立变化，允许细粒度控制
- **离散化 key**：状态按 0.1 步长离散化（`roundToStep(x, 0.1)`），避免连续空间爆炸
  ```
  key = "rigor=0.7|creativity=0.3|verbosity=0.2|risk_tolerance=0.4|obedience=0.9"
  ```
- **clamp01**：所有值被限制在 [0, 1]，避免边界溢出

### 1.2 人格评分与选择（自然选择）

```javascript
function personalityScore(statsEntry) {
  const succ = Number(e.success) || 0;
  const fail = Number(e.fail) || 0;
  const total = succ + fail;
  const p = (succ + 1) / (total + 2);        // Laplace 平滑成功率
  const sampleWeight = Math.min(1, total / 8); // 样本不足时降低置信度
  const avg = Number.isFinite(e.avg_score) ? e.avg_score : 0.5;
  const q = clamp01(avg);
  return p * 0.75 + q * 0.25 * sampleWeight;  // 加权：成功率75% + 质量25%
}
```

**选择最佳已知配置**：
```javascript
function chooseBestKnownPersonality(statsByKey) {
  // 遍历所有有 ≥3 次历史记录的 key，
  // 取 personalityScore 最高者
  return best { key, score, entry };
}
```

### 1.3 三层突变机制

人格状态更新不是简单的"用新状态覆盖"，而是经过三层叠加：

#### Layer 1: 自然选择（Natural Selection）
```javascript
// 从历史最优配置中取方向性微调
const diffs = getParamDeltas(base, bestState).filter(d => Math.abs(d.delta) >= 0.05);
const muts = diffs.slice(0, 2).map(d => ({
  type: 'PersonalityMutation',
  param: d.param,
  delta: Math.max(-0.1, Math.min(0.1, d.delta)),  // 每周期最多 ±0.1
  reason: 'natural_selection',
}));
```
- 不是直接跳到最优，而是取方向
- 最多改2个参数，防止剧变

#### Layer 2: 信号驱动突变（Rule-based triggered mutation）
```javascript
// 当 driftEnabled=true：creativity ↑0.1, risk_tolerance ↓0.05
// 当 protocol_drift 信号：obedience ↑0.1, rigor ↑0.05
// 当 log_error 或 errsig 信号：rigor ↑0.1, risk_tolerance ↓0.1
// 当 opportunity 信号：creativity ↑0.1, risk_tolerance ↑0.05
// 否则：creativity ↑0.05, verbosity ↓0.05（突破 plateau）
```
**自适应逻辑**：根据当前信号决定往哪个方向调整

#### Layer 3: 反思驱动突变（Reflection-driven）
```javascript
// 从最近一次 reflection 中读取 suggested_mutations
// 最多再追加 4 - (Layer1_count + Layer2_count) 个突变
const refMuts = recent[0].suggested_mutations.slice(0, 4 - totalApplied);
```

**突变上限**：三层合计每周期最多 4 个参数变更（cap 保护，防止突变堆积）

### 1.4 触发条件

```javascript
function shouldTriggerPersonalityMutation({ driftEnabled, recentEvents }) {
  if (driftEnabled) return { ok: true, reason: 'drift enabled' };
  // 方式A：最近6个事件中 ≥4 个失败
  // 方式B：最近3个有 mutation_id 的事件全部失败
  return { ok: false };
}
```

### 1.5 人格状态持久化

```javascript
// 状态文件：personality_state.json
{
  version: 1,
  current: { rigor, creativity, verbosity, risk_tolerance, obedience },
  stats: {
    "rigor=0.7|creativity=0.3|...": { success: 5, fail: 2, avg_score: 0.82, n: 7 },
    ...
  },
  history: [  // 最多120条
    { at: "2026-04-24T...", key: "rigor=0.7|...", outcome: "success", score: 0.85 },
    ...
  ],
  updated_at: "2026-04-24T...",
}
```

### 1.6 BlueCortexCE 借鉴方案

CE 的观察注入策略目前是**静态配置**（`observation_types` 白名单、权重）。借鉴 personality 思想：

```java
// InjectionPersonalityState.java
public record InjectionPersonalityState(
    double rigor,           // 严格遵循注入格式
    double creativity,      // 超出白名单的探索注入
    double verbosity,       // 摘要详细程度
    double riskTolerance,   // 高风险观察（错误/能力缺口）是否优先注入
    double obedience        // 遵循用户/系统指令的倾向
) {
    public static InjectionPersonalityState DEFAULT = new InjectionPersonalityState(
        0.7, 0.35, 0.25, 0.4, 0.85
    );
}
```

**CE 可实现的人格适应规则**：

| 信号 | 人格调整 | 理由 |
|------|---------|------|
| 用户连续提问同一问题 | `rigor ↑0.1` | 需要更严格遵循协议 |
| 高频 `error` 观察 | `rigor ↑0.1, riskTolerance ↓0.1` | 错误场景降低风险 |
| `user_feature_request` | `creativity ↑0.1, riskTolerance ↑0.05` | 功能请求需要探索 |
| `evolution_saturation` | `creativity ↑0.05, verbosity ↓0.05` | 突破 plateau |
| drift mode | `creativity ↑0.1, riskTolerance ↓0.05` | 探索但控制风险 |

---

## §2 Hub Search 两相管线（`hubSearch.js`）

### 2.1 设计动机：Credit 成本最小化

Hub 是付费服务（基于 credit）。每次 `fetch` 需要消耗 credit。Evolver 的设计原则是：**Phase 1（搜索）免费或低成本，Phase 2（获取完整 payload）才真正付费**。

### 2.2 两相搜索管线

```javascript
async function hubSearch(signals, opts) {
  // Phase 1: search_only=true → 只返回 metadata（免费）
  const searchMsg = buildFetch({ signals, searchOnly: true });
  const res = await fetch(endpoint, { method: 'POST', body: JSON.stringify(searchMsg) });
  const results = data.payload.results;  // [{ asset_id, confidence, success_streak, reputation_score, ... }]

  // Phase 2: 只对最优候选 fetch 完整 payload（paid）
  const best = pickBestMatch(results, threshold);
  const fetchMsg = buildFetch({ assetIds: [best.asset_id] });
  const res2 = await fetch(endpoint, { method: 'POST', body: JSON.stringify(fetchMsg) });
  const fullAsset = res2.payload.results[0];  // 完整内容
}
```

### 2.3 并行语义搜索

```javascript
// Phase 1 内并行执行 keyword search + semantic search
const [fetchResult, semanticResults] = await Promise.allSettled([
  fetch(endpoint, { body: JSON.stringify(searchMsg) }),       // keyword
  fetchSemanticResults(hubUrl, headers, signalList, 3000),    // semantic (3s timeout)
]);

// 合并去重（seen by asset_id）
results = mergeResults(fetchResult.results || [], semanticResults);
```

### 2.4 缓存层设计

```javascript
// 两层 LRU 缓存（内存中，进程生命周期）

// Layer 1: Phase 1 结果缓存（5分钟 TTL）
const _searchCache = new Map();  // signalKey → { ts, results[] }
const SEARCH_CACHE_TTL_MS = 5 * 60 * 1000;
const SEARCH_CACHE_MAX = 200;

// Layer 2: Phase 2 payload 缓存（无 TTL，LRU 淘汰）
const _payloadCache = new Map();  // asset_id → full payload
const PAYLOAD_CACHE_MAX = 100;

// LRU 淘汰
if (_searchCache.size >= SEARCH_CACHE_MAX) {
  const oldest = _searchCache.keys().next().value;
  _searchCache.delete(oldest);
}
```

### 2.5 评分函数与选优

```javascript
function scoreHubResult(asset) {
  const confidence = asset.confidence || 0;
  const streak = Math.min(Math.max(asset.success_streak || 0, 1), MAX_STREAK_CAP);
  const reputation = asset.reputation_score / 100;
  var base = confidence * streak * reputation;

  // 语义相似度加分（如果有）
  if (asset._semantic_similarity > 0) {
    base += asset._semantic_similarity * 0.3;
  }
  return base;
}
```

**评分设计要点**：
- `streak` 被 cap 到 `[1, MAX_STREAK_CAP=5]`，防止连续成功过多导致分数爆炸
- 语义相似度是额外加分，不影响基础排名

### 2.6 Deadline 控制

```javascript
const deadline = Date.now() + timeoutMs;  // 整个搜索的截止时间

// Phase 1 内并行 + 超时控制
const timer = setTimeout(() => controller.abort(), deadline - Date.now());

// Phase 2 跳过条件
const remaining = deadline - Date.now();
if (remaining < MIN_PHASE2_MS) {
  // 跳过 Phase 2，直接用 metadata（cache hit 时用 cached payload）
}
```

### 2.7 BlueCortexCE 借鉴方案

CE 目前**没有外部知识库查询机制**。Hub search 的两相管线是 CE 可以借鉴的架构：

```java
// CeHubSearchService
// Phase 1: 搜索（免费）→ Phase 2: 获取完整内容（按需）

public record HubSearchResult(
    boolean hit,
    String assetId,
    Double score,
    String mode,          // "reference" or "direct"
    String content,      // Phase 2 fetched content
    String sourceNodeId,
    String chainId
) {}

// 缓存策略（CE 可用 Redis 或内存）
// Layer 1: search results → 5min TTL
// Layer 2: full content → indefinite (LRU, max 100)
```

**CE 的 Hub 对应物**：
- Evolver Hub = 外部 Gene/Capsule 知识库
- CE 对应物可以是：**GitHub Issues 搜索**、**Stack Overflow API**、**内部最佳实践库**

**CE 可借鉴的缓存设计**：
```java
// ObservationCacheService
// Layer 1: 语义搜索结果缓存（5分钟 TTL，信号 → 结果集）
// Layer 2: 观察详情缓存（asset_id → 完整观察，LRU 100条）
// - 避免重复查询向量数据库
// - 减少 LLM embedding 调用次数
```

---

## §3 人格状态机 + Hub 搜索的协同

```
signals[]
    │
    ▼
┌─────────────────────────────┐
│  HubSearch (Phase 1)        │ ← signals → metadata results (free)
│  fetchSemanticResults()     │ ← 并行语义搜索
└──────────────┬──────────────┘
               │ cache hit?
    ┌──────────▼──────────┐
    │  Score & Pick Best   │ → asset_id
    └──────────┬──────────┘
               │ Phase 2 fetch (paid, cacheable)
    ┌──────────▼──────────┐
    │  Full payload cached│
    └──────────┬──────────┘
               │ used as context in next step
               ▼
    ┌───────────────────────────────────────┐
    │  Personality State Machine             │
    │  Layer1: natural selection from stats │
    │  Layer2: signal-driven mutations      │
    │  Layer3: reflection-driven mutations  │
    └────────────────┬──────────────────────┘
                     │ selects personality-influenced prompt
                     ▼
               LLM → output
```

**协同点**：
- Hub 搜索结果提供外部知识
- 人格状态决定如何解读和使用这些外部知识
- 两者的输出共同构成 LLM 的输入上下文

---

## §4 综合启示

### 4.1 Personality State Machine 的核心价值

| 方面 | 设计 | CE 可借鉴 |
|------|------|---------|
| **状态离散化** | 连续值按 0.1 步长离散化为 string key | CE 的注入策略可离散化为 preset 组合 |
| **Laplace 平滑** | `(succ+1)/(total+2)` 避免零样本问题 | CE 的检索结果评分可用类似平滑 |
| **三层叠加** | natural + triggered + reflection，不互相覆盖 | CE 的策略组合可分层叠加 |
| **cap 保护** | 每周期最多 4 个参数变更 | CE 的配置更新应有变更速率限制 |
| **历史统计** | 每个 key 独立记录 success/fail/avg_score | CE 的注入策略效果追踪 |

### 4.2 Hub Search 缓存设计的核心价值

| 方面 | 设计 | CE 可借鉴 |
|------|------|---------|
| **两相搜索** | 免费 metadata → 按需 paid content | CE 的外部 API 调用应区分"查询"和"获取详情" |
| **并行执行** | keyword + semantic 同时发请求 | CE 可并行查询多个数据源 |
| **deadline 控制** | 总时间 budget 内分配给各 phase | CE 的 context 生成应有 token/time budget |
| **LRU 缓存** | 两层 LRU，进程内存存储 | CE 的观察缓存可用 Redis LRU |
| **缓存 key** | `signals.sort().join('\|')` → 不受顺序影响 | CE 的缓存 key 设计应规范化 |

---

## 附录：相关文件索引

| 文件 | 内容 |
|------|------|
| [index.md](./index.md) | 总导航 |
| [43](./43-privacy-computing-and-hub-ecosystem.md) | 隐私计算、问答生成、issue报告、人格commentary |
| [31](./31-reflection-remote-adapter-local-state.md) | 自省 / 远程适配器 / 三层自调节 |
| [26](./26-runtime-orchestration-adaptive-policy-candidates.md) | 自适应策略、候选评估 |
| [35](./35-a2a-protocol-asset-lifecycle-feedback.md) | A2A 协议（hello/publish/fetch/review） |
| [37](./37-signal-taxonomy-gene-selection-end-to-end.md) | Signal Taxonomy + Gene Selection |
| [09](./09-aspect-bluecortex-bridge.md) | Evolver ↔ CE 方面级对照 |
| [10](./10-aspect-bluecortex-implementation-map.md) | CE 实现锚点与缺口 |
