# A2A Protocol, Asset Lifecycle & Feedback Loops

**Source**: `src/gep/a2aProtocol.js` (1221 lines), `src/gep/hubReview.js`, `src/gep/sanitize.js`, `src/gep/taskReceiver.js`
**Date**: 2026-04-23
**Purpose**: Document the agent-to-agent communication protocol, asset publishing lifecycle, and the feedback loops that close the evolution circuit.

## 1. A2A Protocol Overview

The GEP A2A (Agent-to-Agent) protocol enables evolver nodes to discover each other, share evolution assets (Genes + Capsules), and collaborate on tasks through a central Hub.

### 1.1 Message Types

| Type | Direction | Purpose |
|------|-----------|---------|
| `hello` | Node → Hub | Capability advertisement, node registration |
| `publish` | Node → Hub | Broadcast an eligible asset (Gene/Capsule bundle) |
| `fetch` | Node → Hub → Node | Request assets by ID, signal match, or task |
| `report` | Node → Hub | Submit ValidationReport for a received asset |
| `decision` | Node → Hub | Accept/reject/quarantine decision on received asset |
| `revoke` | Node → Hub | Withdraw a previously published asset |

### 1.2 Message Envelope

```javascript
{
  protocol: 'gep-a2a',
  protocol_version: '1.0.0',
  message_type: 'hello' | 'publish' | 'fetch' | 'report' | 'decision' | 'revoke',
  message_id: 'msg_<timestamp>_<random_hex>',
  sender_id: 'node_<sha256_prefix_12>',
  timestamp: ISO,
  signature: '<hmac_sha256>',  // HMAC of payload using node_secret
  payload: { ... }
}
```

### 1.3 Node Identity

```javascript
// Priority order:
// 1. A2A_NODE_ID env var (explicit, stable)
// 2. ~/.evomap/node_id file (persisted from hello response)
// 3. .evomap_node_id in project root (local fallback)
// 4. Computed from device fingerprint (changes across machines)
```

**Design insight**: Four-layer identity resolution ensures the evolver always has an identity, even without Hub registration. The computed identity (`deviceFingerprint|agentName|cwd`) is unstable across machines but sufficient for local operation.

## 2. Asset Publishing Lifecycle

### 2.1 Eligibility Gate

A Capsule becomes eligible for publishing when ALL conditions are met:

```javascript
eligible_to_broadcast =
  isBlastRadiusSafe(capsule.blast_radius) &&      // ≤ safe threshold
  capsule.outcome.score >= BROADCAST_SCORE_THRESHOLD &&  // ≥ 0.7
  capsule.success_streak >= BROADCAST_SUCCESS_STREAK;    // ≥ 2
```

**Three-bar gate**: Safety (blast) × Quality (score) × Reliability (streak). A capsule must prove itself across multiple cycles before broadcasting.

### 2.2 Publish Bundle Format

```javascript
{
  gene: {           // The Gene that produced this Capsule
    type: 'Gene',
    id, category, signals_match, strategy, constraints,
    parent: 'sha256:...'  // if derived from Hub asset
  },
  capsule: {        // The reusable knowledge artifact
    type: 'Capsule',
    id, trigger, gene, summary, confidence,
    blast_radius, outcome, success_streak,
    parent: 'sha256:...'  // provenance chain
  },
  event: {          // Optional: the EvolutionEvent
    type: 'EvolutionEvent',
    id, signals, genes_used, mutation, outcome, ...
  },
  chainId: '...',   // links related capsules across versions
  modelName: '...'  // LLM that produced this (metadata)
}
```

### 2.3 Pre-Publish Leak Check

Before publishing, `fullLeakCheck()` scans the payload:

| Scanner | Pattern | Suggestion |
|---------|---------|------------|
| `api_key` | `sk-...`, `sk-proj-...`, `sk-ant-...` | `process.env.OPENAI_API_KEY` |
| `github_token` | `ghp_...`, `github_pat_...` | `process.env.GITHUB_TOKEN` |
| `npm_token` | `npm_...` | `process.env.NPM_TOKEN` |
| `bearer_token` | `Bearer ...` | `process.env.AUTH_TOKEN` |
| `private_key` | `-----BEGIN PRIVATE KEY-----` | `process.env.PRIVATE_KEY_PATH` |
| `db_connection` | `postgres://user:pass@...` | `process.env.DATABASE_URL` |
| `local_path` | `/Users/...`, `/home/...`, `C:\...` | relative path |
| `email` | `user@domain.com` | `<email>` |

**Two modes**:
- `strict`: Block publish entirely on any leak
- `warn`: Log leaks, proceed with `sanitizePayload()` redaction

### 2.4 Sanitization (`sanitize.js`)

`sanitizePayload()` deep-clones and redacts using 15+ regex patterns. Applies to strings at any depth in the JSON tree. Key patterns:

- Bearer tokens, API keys (OpenAI, Anthropic, AWS, GitHub, npm)
- Private keys (RSA, EC, DSA, OpenSSH)
- Basic auth in URLs (`user:pass@`)
- Local filesystem paths
- Email addresses
- `.env` file references

### 2.5 Anti-Pattern Publishing

Opt-in via `EVOLVER_PUBLISH_ANTI_PATTERNS=true`. Publishes **high-information-value failures** to Hub:

```javascript
gene.anti_pattern = true;
gene.failure_reason = buildFailureReason(...);
// Sent as Gene+Capsule bundle with confidence: 0
```

**Design insight**: Anti-patterns are just as valuable as successes. Knowing "what doesn't work" prevents other nodes from repeating the same mistake.

## 3. Asset Fetching & Reuse

### 3.1 Fetch Modes

```javascript
// Signal-based: find assets matching current signals
POST /a2a/fetch { signals: ['error', 'timeout'], limit: 10 }

// Task-based: fetch available tasks
POST /a2a/fetch { tasks_only: true, include_tasks: true }

// Direct: fetch specific asset
POST /a2a/fetch { asset_id: 'sha256:...' }
```

### 3.2 Source Types

| `source_type` | Meaning | Publish Eligible? |
|---------------|---------|-------------------|
| `generated` | Created by this node | Yes |
| `reused` | Direct copy from Hub | No (already exists) |
| `reference` | Inspired by Hub asset, modified | Yes (new derivative) |

### 3.3 Provenance Chain

When reusing an asset, `parent` field links back:

```javascript
// Original asset (node A)
{ id: 'sha256:abc...', parent: null }

// Derivative (node B, inspired by node A)
{ id: 'sha256:def...', parent: 'sha256:abc...' }

// Further derivative (node C)
{ id: 'sha256:ghi...', parent: 'sha256:def...' }
```

This creates a **lineage graph** of evolution assets across the network.

## 4. Feedback Loops

### 4.1 Hub Review (`hubReview.js`)

When a cycle reuses a Hub asset:

1. Compute rating from outcome: success → 4-5, failure → 1-2
2. Submit to `POST /a2a/assets/:assetId/reviews`
3. Track reviewed asset IDs in `hub_review_history.json` (max 500 entries)
4. Duplicate prevention: skip if already reviewed

**Non-blocking**: Review submission errors never affect solidify result.

### 4.2 Task Receiver (`taskReceiver.js`)

Pulls tasks from Hub and injects them as high-priority signals:

```javascript
// Task scoring with strategy-aware weights
const STRATEGY_WEIGHTS = {
  greedy:       { roi: 0.10, capability: 0.05, completion: 0.05, bounty: 0.80 },
  balanced:     { roi: 0.35, capability: 0.30, completion: 0.20, bounty: 0.15 },
  conservative: { roi: 0.25, capability: 0.45, completion: 0.25, bounty: 0.05 },
};
```

**Capability matching**: Uses Jaccard similarity between task signals and agent's memory graph history. Minimum threshold: `TASK_MIN_CAPABILITY_MATCH` (default 0.1).

**Task completion**: Three paths:
- **Deferred claim**: `claimAndCompleteWorkerTask()` — atomic claim + complete
- **Legacy**: `completeWorkerTask()` for already-claimed assignments
- **Bounty**: `completeTask()` via `/a2a/task/complete`

### 4.3 Lessons from Hub

Fetch responses may include `relevant_lessons` — structured negative lessons from other nodes' failures. These are injected as learning signals into the current evolution cycle.

## 5. Content-Addressable Deduplication

### 5.1 Canonical JSON

```javascript
function canonicalize(obj) {
  if (Array.isArray(obj)) return '[' + obj.map(canonicalize).join(',') + ']';
  if (typeof obj === 'object') {
    const keys = Object.keys(obj).sort();  // deterministic key order
    return '{' + keys.map(k => JSON.stringify(k) + ':' + canonicalize(obj[k])).join(',') + '}';
  }
  // primitives: non-finite numbers → null, undefined → null
}
```

### 5.2 Asset ID

```javascript
function computeAssetId(obj) {
  const clean = { ...obj };
  delete clean.asset_id;  // exclude self-referential
  return 'sha256:' + sha256(canonicalize(clean));
}
```

### 5.3 Benefits

| Property | Mechanism | CE Analog |
|----------|-----------|-----------|
| Deduplication | Same content → same ID | Observation content hash |
| Tamper detection | Any change → different ID | Integrity verification |
| Cross-node consistency | Canonical serialization | Reproducible IDs across instances |
| Provenance tracking | `parent: 'sha256:...'` | Observation source chain |

## 6. CE 借鉴要点

### 6.1 可借鉴的模式

| Evolver 模式 | CE 对应方案 | 优先级 |
|-------------|-------------|--------|
| Pre-publish leak check | 观察数据脱敏管道 | **P0** |
| Content-addressable IDs | `sha256:` 观察/摘要 ID | P1 |
| Provenance chain (`parent`) | 观察来源追溯 | P2 |
| Three-bar eligibility gate | 质量门禁 (score × streak × safety) | P1 |
| Hub review feedback loop | 观察/模板使用效果反馈 | P2 |
| Anti-pattern publishing | 失败经验共享 | P2 |
| Capability matching (Jaccard) | 信号匹配检索增强 | P2 |

### 6.2 反模式

- **不要**实现完整的 A2A 协议：CE 是旁路记忆系统，不是进化引擎
- **不要**实现 rollback：CE 不修改用户代码
- **不要**实现 blast radius：CE 没有代码变更概念

### 6.3 关键洞察

1. **Leak check 是发布前的硬门禁**：任何包含敏感数据的内容都不应离开本地。CE 应该在写入数据库前就进行脱敏，而不是在查询时。

2. **Content-addressable ID 的力量**：同一份数据从任何路径进来都得到相同 ID，天然去重。CE 的观察/摘要应该用同样的方案。

3. **Provenance chain > flat storage**：知道一个观察是从哪个上游推导出来的，比仅仅存储它更有价值。

4. **Feedback loops close the circuit**：Evolution without feedback is random walk. Hub review + task completion + lessons create a closed loop. CE 需要类似的"使用→评估→改进"闭环。
