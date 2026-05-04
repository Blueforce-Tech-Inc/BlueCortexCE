# 89 · hubSearch.js 两阶段搜索 + 语义增强 + 双层缓存 Deep Dive

**文档目标**：深度分析 `hubSearch.js`（407行）的两阶段搜索架构（Phase 1 search_only 免费元数据 → Phase 2 按需获取完整 payload）、语义增强搜索（`/a2a/assets/semantic-search`）、双层 LRU 缓存（search cache + payload cache）、以及 `scoreHubResult` 评分公式。为 BlueCortexCE 的检索层提供借鉴。

**源码**：`/path/to/EvoMap/evolver/src/gep/hubSearch.js`

**前置**：
- [`44`](./44-personality-state-machine-and-hub-search-caching.md) — Hub Search 缓存概览（搜索入口、LRU 配置）
- [`75`](./75-atp-agent-transaction-protocol-and-adapters.md) — Hub Adapter / hookAdapter
- [`86`](./86-dual-stack-semantic-architecture.md) — Worker Chroma vs Java pgvector（向量存储对照）

**最后更新**：2026-05-05

---

## 1 · 定位：Hub Search-First Evolution 范式

```
Evolve 循环（无 Hub Search-First）：
  signals → local Gene Pool → select → mutate → solidify → publish

Evolve 循环（Hub Search-First）：
  signals → hubSearch(signals) ─┬─► hit  → reuse Gene (skip local evolve)
                               │
                               └─► miss → normal local evolve
```

**Search-First 的核心价值**：如果 Hub 已有可复用的 Gene，优先复用而非本地重新生成——节省 LLM 调用成本、加速解决速度。

**两种复用模式**（`EVOLVER_REUSE_MODE`）：
- `reference`（默认）：将 Hub Gene 作为"强提示"注入 prompt，不完全依赖
- `direct`：跳过本地推理，直接使用 Hub 资产

---

## 2 · 两阶段搜索架构（Two-Phase Search-Then-Fetch）

### 2.1 为什么需要两阶段？

```
Naive approach: fetch all matching assets immediately
  POST /a2a/fetch { assetIds: [all_candidates] }
  → Pays for ALL candidate assets (expensive)

Two-phase approach:
  Phase 1: POST /a2a/fetch { signals, search_only: true }
  → Free metadata only (no credit cost)
  → Returns: [{ asset_id, confidence, reputation, success_streak, ... }]

  Phase 2: Only for selected asset
  → POST /a2a/fetch { asset_ids: [best_match] }
  → Pays for exactly 1 asset
```

**关键洞察**：`search_only=true` 是 Hub API 的免费查询模式，返回元数据但不返回完整 payload。这使 agent 可以先评估是否值得获取，而非盲目付费。

### 2.2 Phase 1 并行语义搜索

```javascript
// Phase 1: 两路并行
var fetchPromise = (async function () {
  const searchMsg = buildFetch({ signals: signalList, searchOnly: true });
  // ... POST to /a2a/fetch
})();

var semanticPromise = isSemanticEnabled()
  ? fetchSemanticResults(hubUrl, headers, signalList, SEMANTIC_TIMEOUT_MS)
  : Promise.resolve([]);

var settled = await Promise.allSettled([fetchPromise, semanticPromise]);
```

**语义搜索 URL**：`GET /a2a/assets/semantic-search?q={query}&type=Gene&limit=10`

语义搜索与关键词搜索**并行执行**，互为补充。语义搜索不受信号字段格式限制，适合跨语言/跨表述场景。

### 2.3 `buildSemanticQuery` 信号→自然语言查询

```javascript
function buildSemanticQuery(signals) {
  return signals
    .filter(s => !s.startsWith('errsig:') && !s.startsWith('errsig_norm:'))  // 排除错误签名
    .map(s => {
      var colonIdx = s.indexOf(':');
      // "ts:typescript:file-write" → "typescript file-write" (取冒号后内容)
      return colonIdx > 0 && colonIdx < 30 ? s.slice(colonIdx + 1).trim() : s;
    })
    .filter(Boolean)
    .slice(0, 12)   // 最多 12 个信号
    .join(' ');     // 空格拼接为自然语言查询
}
```

**设计意图**：
- `errsig*` 信号不参与语义搜索（错误签名过于具体，不适合语义泛化）
- 冒号前缀通常是类别标签（如 `ts:typescript`），取冒号后内容保留核心语义
- 截断到 12 个信号防止查询过长
- 最终拼接为 `space-separated` 自然语言查询

### 2.4 结果合并（去重 + 语义分数增强）

```javascript
function mergeResults(fetchResults, semanticResults) {
  var seen = {};
  var merged = [];

  // Keyword results first (higher precision)
  for (asset of fetchResults) {
    var id = asset.asset_id || asset.assetId || '';
    if (id) seen[id] = true;
    merged.push(asset);
  }

  // Semantic results: dedup against keyword results, add similarity score
  for (asset of semanticResults) {
    var id = asset.asset_id || asset.assetId || '';
    if (seen[id]) {
      // Already in keyword results → enhance with semantic similarity
      var existing = merged.find(m => (m.asset_id || m.assetId) === id);
      if (existing) existing._semantic_similarity = asset._semantic_similarity || 0;
      continue;
    }
    if (id) seen[id] = true;
    merged.push(asset);  // New from semantic search
  }
  return merged;
}
```

**合并策略**：关键词结果优先（高精确率），语义结果补充（高召回率）。如果语义结果与关键词结果重叠，只增强 `_semantic_similarity` 分数，不重复添加。

### 2.5 Phase 2 条件触发

```javascript
const MIN_PHASE2_MS = 500;

const remaining = deadline - Date.now();
if (remaining > MIN_PHASE2_MS) {
  // Execute Phase 2 fetch
  const fetchMsg = buildFetch({ assetIds: [selectedAssetId] });
  const res2 = await fetch(endpoint, { method: 'POST', headers, body: JSON.stringify(fetchMsg) });
  // ... merge full payload into pick.match
} else {
  // Skip Phase 2: not enough time → return metadata only
  console.log(`[HubSearch] Phase 2 skipped: ${remaining}ms remaining < ${MIN_PHASE2_MS}ms`);
}
```

**时间保护**：全局 `deadline` 约束两阶段总时长（默认 8000ms）。如果剩余时间不足 500ms，跳过 Phase 2，只返回元数据。这确保搜索永远不会阻塞进化循环。

---

## 3 · 双层 LRU 缓存

### 3.1 Search Cache（第一层）

```javascript
const SEARCH_CACHE_TTL_MS = 5 * 60 * 1000;  // 5 分钟 TTL
const SEARCH_CACHE_MAX = 200;                // 最大 200 条

function _cacheKey(signals) {
  return signals.slice().sort().join('|');   // 信号排序后拼接为 key
}

function _getSearchCache(key) {
  const entry = _searchCache.get(key);
  if (!entry) return null;
  if (Date.now() - entry.ts > SEARCH_CACHE_TTL_MS) {
    _searchCache.delete(key);
    return null;
  }
  return entry.value;
}

function _setSearchCache(key, value) {
  if (_searchSearchCache.size >= SEARCH_CACHE_MAX) {
    const oldest = _searchCache.keys().next().value;
    _searchCache.delete(oldest);  // LRU eviction
  }
  _searchCache.set(key, { ts: Date.now(), value });
}
```

**Search Cache 命中场景**：
- 同一组信号在 5 分钟内被多次搜索 → 直接返回缓存结果（零网络开销）
- 典型场景：`idleScheduler` OMLS 期间重复调度，或多个进化周期使用相同信号

### 3.2 Payload Cache（第二层）

```javascript
const PAYLOAD_CACHE_MAX = 100;  // 无 TTL（永久缓存直到 LRU 淘汰）

function _getPayloadCache(assetId) {
  return _payloadCache.get(assetId) || null;
}

function _setPayloadCache(assetId, payload) {
  if (_payloadCache.size >= PAYLOAD_CACHE_MAX) {
    const oldest = _payloadCache.keys().next().value;
    _payloadCache.delete(oldest);  // LRU eviction
  }
  _payloadCache.set(assetId, payload);
}
```

**Payload Cache 命中场景**：
- 同一 `asset_id` 在 Phase 2 fetch 后被缓存
- 即使 Phase 1 结果被缓存，Phase 2 仍可能需要 fetch（如果之前没缓存过这个 asset 的 payload）

**双层缓存的协同**：
```
Scenario: Same signals searched twice within 5 min
───────────────────────────────────────────────────
Search 1:
  Phase 1 → search cache MISS → network call → cache result
  Phase 2 → payload cache MISS → network call → cache payload
  Return { hit: true, match: {...} }

Search 2 (same signals, within 5 min):
  Phase 1 → search cache HIT → return cached results (instant)
  Phase 2 → payload cache HIT → return cached payload (instant)
  Return { hit: true, match: {...} }  (zero network calls)
```

### 3.3 两层缓存的生命周期差异

| 维度 | Search Cache | Payload Cache |
|------|-------------|---------------|
| TTL | 5 分钟 | 无（永久） |
| Key | 信号指纹（排序拼接） | asset_id |
| 淘汰策略 | LRU（max 200）+ TTL | LRU（max 100） |
| 目的 | 减少免费查询的网络开销 | 避免重复付费 fetch |

---

## 4 · `scoreHubResult` 评分公式

### 4.1 完整实现

```javascript
const MAX_STREAK_CAP = 5;
const SEMANTIC_SIMILARITY_BONUS = 0.3;

function scoreHubResult(asset) {
  // Base: confidence × streak × reputation
  const confidence = Number(asset.confidence) || 0;
  const streak = Math.min(Math.max(Number(asset.success_streak) || 0, 1), MAX_STREAK_CAP);
  const repRaw = Number(asset.reputation_score);
  const reputation = Number.isFinite(repRaw) ? repRaw : 50;

  var base = confidence * streak * (reputation / 100);

  // Semantic similarity bonus
  var sim = Number(asset._semantic_similarity) || 0;
  if (sim > 0) base += sim * SEMANTIC_SIMILARITY_BONUS;

  return base;
}
```

### 4.2 公式拆解

```
score = confidence × min(success_streak, 5) × (reputation / 100)
      + semantic_similarity × 0.3   (if available)

其中：
- confidence: Hub 评估的资产质量（0–1）
- success_streak: 连续成功次数（上限 5，防止刷分）
- reputation: 发布者声誉（默认 50）
- semantic_similarity: 语义搜索返回的相似度分数（可选）
```

### 4.3 `pickBestMatch` 阈值门禁

```javascript
function pickBestMatch(results, threshold) {
  var best = null;
  var bestScore = 0;

  for (asset of results) {
    if (asset.status && asset.status !== 'promoted') continue;  // 只选 promoted 资产
    const s = scoreHubResult(asset);
    if (s > bestScore) { bestScore = s; best = asset; }
  }

  if (!best || bestScore < threshold) return null;  // 低于阈值则放弃复用

  return { match: best, score: bestScore, mode: getReuseMode() };
}
```

**默认阈值**：`DEFAULT_MIN_REUSE_SCORE = 0.72`（`EVOLVER_MIN_REUSE_SCORE` 环境变量可覆盖）

**`status !== 'promoted'` 过滤**：Hub 只有 `promoted` 状态的资产才会被考虑。`claimed`、`pending` 等中间状态资产不参与评分。

---

## 5 · `hubSearch` 完整调用流程

```
hubSearch(signals, opts)
  │
  ├─► No hub URL? → return { hit: false, reason: 'no_hub_url' }
  ├─► Empty signals? → return { hit: false, reason: 'no_signals' }
  │
  ├─► Check search cache (signal fingerprint key)
  │     └─ HIT? → skip Phase 1, use cached results
  │
  ├─► Phase 1a: POST /a2a/fetch { signals, search_only: true }  (network)
  ├─► Phase 1b: GET /a2a/assets/semantic-search (parallel, optional)
  │     └─ Parallel Promise.allSettled
  │
  ├─► mergeResults(keywordResults, semanticResults)
  ├─► Cache merged results in search cache
  │
  ├─► pickBestMatch(results, threshold)
  │     └─ No match above threshold? → { hit: false }
  │
  ├─► Check payload cache (asset_id key)
  │     └─ HIT? → merge cached payload into match
  │
  ├─► Phase 2: POST /a2a/fetch { asset_ids: [best] }  (if time permits)
  │     └─ Cache full payload in payload cache
  │
  └─► { hit: true, match, score, mode, asset_id, source_node_id, chain_id }
```

---

## 6 · CE 借鉴：BlueCortexCE 检索层增强

### 6.1 两阶段检索模式

Evolver 的两阶段搜索思想可以映射到 BlueCortexCE：

```
Phase 1 (free/cheap): SearchService.semantic()
  → 返回 top-N 候选的元数据（embedding 相似度 + 历史成功率）
  → 不获取完整 payload

Phase 2 (on-demand): 根据 Phase 1 结果决定是否 fetch 完整 observation
  → 如果 rank-1 足够好（score > threshold）→ 直接注入
  → 如果 rank-1 不够好 → 获取更多上下文或回退到本地生成
```

### 6.2 双层缓存

BlueCortexCE 的 `SearchService` 已经有 pgvector 存储，但可以增强应用层缓存：

```java
// Proposal: SearchResultCache
// Layer 1: Embedding query result cache (signals → top-K ids)
// Layer 2: Observation payload cache (id → full entity)

// Key design:
// - Layer 1 TTL: 5 minutes (avoid stale context)
// - Layer 2: Permanent until LRU eviction (100 items)
// - Cache invalidation: on new observation ingestion matching cache key
```

### 6.3 成功率加权排序

```java
// Evolver's scoreHubResult → BlueCortexCE enhanced scoring:
// score = embedding_similarity * w1
//       + historical_success_rate * w2      // Laplace-smoothed
//       + streak_bonus * w3                   // Recent successes
//       + reputation * w4                     // Source quality

// Default weights (balanced mode):
// w1=0.4, w2=0.3, w3=0.2, w4=0.1
```

---

## 附录：完整导出函数表

| 函数 | 行号 | 职责 |
|------|------|------|
| `_cacheKey` | L26 | 信号数组 → 排序拼接 cache key |
| `_getSearchCache` | L29–L35 | Search cache 读取 + TTL 检查 |
| `_setSearchCache` | L38–L43 | Search cache 写入 + LRU 淘汰 |
| `_getPayloadCache` | L49–L51 | Payload cache 读取 |
| `_setPayloadCache` | L54–L59 | Payload cache 写入 + LRU 淘汰 |
| `clearCaches` | L62 | 手动清除所有缓存（测试用） |
| `getHubUrl` | L69–L71 | 读取 `A2A_HUB_URL` env |
| `getReuseMode` | L74–L79 | 读取 `EVOLVER_REUSE_MODE` env |
| `getMinReuseScore` | L82–L85 | 读取 `EVOLVER_MIN_REUSE_SCORE` env |
| `_buildHeaders` | L88–L97 | 构建带 auth 的 HTTP headers |
| `isSemanticEnabled` | L100–L104 | 检查 `HUBSEARCH_SEMANTIC` env |
| `buildSemanticQuery` | L107–L116 | 信号列表 → 自然语言查询字符串 |
| `fetchSemanticResults` | L119–L144 | GET `/a2a/assets/semantic-search` |
| `mergeResults` | L147–L169 | 关键词 + 语义结果合并去重 |
| `scoreHubResult` | L175–L189 | Hub 资产评分公式 |
| `pickBestMatch` | L195–L215 | 选最优 + 阈值门禁 |
| `hubSearch` | L221–L341 | 主入口：两阶段搜索 + 双缓存 + 原子操作 |
