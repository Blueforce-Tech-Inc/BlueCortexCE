# Content-addressable Asset System（内容寻址资产系统）

> **角色**：深入分析 Evolver `contentHash.js` + `assetStore.js` + `candidates.js` + `candidateEval.js` 的内容寻址资产层，提炼对 BlueCortexCE 的观察去重、完整性验证、跨节点一致性的可落地设计。
> **源码锚点**：`src/gep/contentHash.js`、`src/gep/assetStore.js`、`src/gep/candidates.js`、`src/gep/candidateEval.js`、`src/gep/assets.js`。
> **最后更新**：2026-04-24

---

## 1. 核心问题：资产系统的"身份"是什么？

传统资产管理系统用 UUID 或自增 ID 作为资产身份：

```javascript
// ❌ 传统方式：ID 与内容无关
{ id: "uuid-xxx", content: "..." }
{ id: "uuid-yyy", content: "..." }  // 相同内容可能产生不同 ID
```

**问题**：
- 相同内容在不同节点、不同时间可能被视为不同资产
- 无法检测"内容是否被篡改"
- 跨节点无法识别"这是同一个资产"

**Evolver 的解法**：内容寻址（Content-addressable）——用内容的哈希作为资产的唯一身份。

---

## 2. `contentHash.js`：Canonical JSON + SHA-256

### 2.1 规范化（Canonicalization）

```javascript
// contentHash.js
function canonicalize(obj) {
  if (obj === null || obj === undefined) return 'null';
  if (typeof obj === 'boolean') return obj ? 'true' : 'false';
  if (typeof obj === 'number') {
    if (!Number.isFinite(obj)) return 'null';
    return String(obj);
  }
  if (typeof obj === 'string') return JSON.stringify(obj);  // 字符串转义
  if (Array.isArray(obj)) {
    return '[' + obj.map(canonicalize).join(',') + ']';
  }
  if (typeof obj === 'object') {
    const keys = Object.keys(obj).sort();  // ← 对象键排序
    const pairs = [];
    for (const k of keys) {
      pairs.push(JSON.stringify(k) + ':' + canonicalize(obj[k]));
    }
    return '{' + pairs.join(',') + '}';
  }
  return 'null';
}
```

**关键保证**：
- 对象键**排序**（`Object.keys(obj).sort()`）→ 相同内容无论属性顺序如何，规范化结果相同
- 字符串**转义**（`JSON.stringify`）→ `"key"` 和 `key` 严格区分
- 非有限数 → `null`
- 数组按顺序递归

### 2.2 资产 ID 计算

```javascript
// contentHash.js
function computeAssetId(obj, excludeFields) {
  const exclude = new Set(Array.isArray(excludeFields) ? excludeFields : ['asset_id']);
  const clean = {};
  for (const k of Object.keys(obj)) {
    if (exclude.has(k)) continue;  // ← 排除 asset_id 自身（自引用）
    clean[k] = obj[k];
  }
  const canonical = canonicalize(clean);
  const hash = crypto.createHash('sha256').update(canonical, 'utf8').digest('hex');
  return 'sha256:' + hash;
}
```

**结果格式**：`sha256:<64-char-hex>`

### 2.3 完整性验证

```javascript
// contentHash.js
function verifyAssetId(obj) {
  const claimed = obj.asset_id;
  const computed = computeAssetId(obj);
  return claimed === computed;  // ← 篡改检测
}
```

### 2.4 `SCHEMA_VERSION`

```javascript
const SCHEMA_VERSION = '1.6.0';
```

版本号规则：
- **MAJOR**（破坏性变更）
- **MINOR**（新增字段）

资产 JSON 中记录 `schema_version`，便于跨版本兼容。

---

## 3. `assetStore.js`：资产持久化层

### 3.1 目录布局

```
getGepAssetsDir()/
├── genes/
│   ├── sha256_abc123...json
│   └── sha256_def456...json
├── capsules/
├── candidates/
├── lessons/
└── principles/
```

每个资产文件 = `{asset_id}.json`，内容是规范化的资产 JSON。

### 3.2 核心写入语义

```javascript
// assetStore.js
function writeJsonAtomic(filePath, obj) {
  const tmp = `${filePath}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(obj, null, 2) + '\n');
  fs.renameSync(tmp, filePath);  // ← 原子写入（rename 是原子的）
}
```

**原子写入**：`tmp` + `rename` 而不是直接 `writeFile`：
- 写入失败 → `.tmp` 残留，原文件无损
- 写入一半（断电）→ 原文件未被覆盖
- `rename` 在 POSIX 系统上是原子操作

### 3.3 基因资产结构

```javascript
// assetStore.js - getDefaultGenes() 节选
{
  type: 'Gene', id: 'gene_gep_repair_from_errors', category: 'repair',
  signals_match: ['error', 'exception', 'failed', 'unstable'],
  preconditions: ['signals contains error-related indicators'],
  strategy: [
    'Extract structured signals from logs and user instructions',
    'Select an existing Gene by signals match (no improvisation)',
    ...
  ],
  constraints: { max_files: 12, forbidden_paths: ['.git', 'node_modules'] },
  validation: [
    buildValidationCmd([...]),
    'node scripts/validate-suite.js',
  ],
  // asset_id 由 computeAssetId(clean) 生成（排除 asset_id 字段）
}
```

### 3.4 核心操作函数

| 函数 | 职责 |
|------|------|
| `computeAssetId()` | 内容哈希计算 |
| `verifyAssetId()` | 完整性验证 |
| `assetExists()` | 查询资产是否已存在（去重） |
| `appendCandidateJsonl()` | 追加候选人到 JSONL |
| `readRecentCandidates()` | 读取最近 N 条候选人 |
| `readRecentFailedCapsules()` | 读取最近失败胶囊 |
| `buildValidationCmd()` | 构建相对路径验证命令 |

---

## 4. `candidates.js` + `candidateEval.js`：候选评估管线

### 4.1 候选人来源

```
┌──────────────────────────────────────────────────────────────┐
│                   Candidate Sources                           │
│                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────┐ │
│  │ Transcript      │  │ Signals         │  │ Failed     │ │
│  │ (repeated tools)│  │ (as candidates)│  │ Capsules   │ │
│  └────────┬────────┘  └────────┬────────┘  └─────┬──────┘ │
│           │                     │                   │         │
│           ▼                     ▼                   ▼         │
│  extractToolCalls()      signalCandidates       Failed     │
│  countFreq()             expandSignals()         Capsule    │
│  if (count ≥ 3)                                  grouping  │
│           │                     │                   │         │
│           └──────────┬──────────┴───────────────────┘         │
│                      ▼                                        │
│             extractCapabilityCandidates()                    │
│                      │                                        │
│                      ▼                                        │
│          ┌─────────────────────────┐                         │
│          │ CapabilityCandidate[]  │                         │
│          │ (dedup by id)          │                         │
│          └─────────────────────────┘                         │
└──────────────────────────────────────────────────────────────┘
```

### 4.2 候选人形状（CapabilityCandidate）

```javascript
{
  type: 'CapabilityCandidate',
  id: 'cand_<8-char-stable-hash>',
  title: 'Repair recurring runtime errors',
  source: 'signals',           // 'transcript' | 'signals' | 'failed_capsules'
  created_at: '2026-04-24T...',
  signals: ['log_error', 'recurring_error', ...],
  tags: ['problem:reliability', 'area:error_handling', ...],
  shape: {
    title: 'Repair recurring runtime errors',
    input: 'Recent session transcript + memory snippets + user instructions',
    output: 'A safe, auditable evolution patch guided by GEP assets',
    invariants: 'Protocol order, small reversible patches, validation, append-only events',
    params: 'Signals: log_error, recurring_error, ...',
    failure_points: 'Missing signals, over-broad changes, skipped validation, ...',
    evidence: 'Signal present: log_error ...[TRUNCATED]'
  }
}
```

### 4.3 工具重复检测（Transcript）

```javascript
// candidates.js
function extractToolCalls(transcript) {
  // OpenClaw format: [TOOL: Shell]
  const m = line.match(/\[TOOL:\s*([^\]]+)\]/i);
  // Cursor format: [Tool call] Shell
  const m2 = line.match(/\[Tool call\]\s+(\S+)/i);
}
// countFreq(items) → Map<tool, count>
// if (count ≥ 3) → 候选
```

### 4.4 失败胶囊分组

```javascript
// candidates.js
const problemPriority = [
  'problem:performance',
  'problem:protocol',
  'problem:reliability',
  'problem:stagnation',
  'problem:capability',
];
// 失败胶囊按 problem tag 分组，count ≥ 2 → 候选
// 用于"从失败中学习"
```

### 4.5 外部资产预览（candidateEval.js）

```javascript
// candidateEval.js - buildCandidatePreviews()
const matchedExternalGenes = genesOnly
  .map(g => {
    const pats = g.signals_match || [];
    const hit = pats.reduce((acc, p) =>
      matchPatternToSignals(p, signals) ? acc + 1 : acc, 0);
    return { gene: g, hit };
  })
  .filter(x => x.hit > 0)
  .sort((a, b) => b.hit - a.hit)
  .slice(0, 3);

const matchedExternalCapsules = capsulesOnly
  .map(c => {
    const triggers = c.trigger || [];
    const score = triggers.reduce((acc, t) =>
      matchPatternToSignals(t, signals) ? acc + 1 : acc, 0);
    return { capsule: c, score };
  })
  .filter(x => x.score > 0)
  .sort((a, b) => b.score - a.score)
  .slice(0, 3);
```

外部资产按 **signal pattern 匹配度**排序，取 top-3。

---

## 5. 跨节点一致性机制

### 5.1 三重保证

| 保证 | 机制 |
|------|------|
| **内容一致** | Canonical JSON → SHA-256 → 相同内容产生相同 ID |
| **篡改检测** | `verifyAssetId()` 验算比对 |
| **版本追踪** | `schema_version` 字段 |

### 5.2 A2A 资产同步中的内容寻址

```javascript
// 资产发布时
{ asset_id: 'sha256:abc123...', content: {...} }

// 接收节点验算
const computed = computeAssetId(received);
if (computed !== received.asset_id) {
  throw new Error('Asset tampered during transfer');
}
```

---

## 6. BlueCortexCE 借鉴

### 6.1 观察去重（Observation Deduplication）

CE 当前 Observation 依赖 `(session_id, content)` 唯一约束，无法识别"不同 session 相同问题"的观察。

**可落地方案**：对 `mem_observations` 增加 `content_fingerprint` 字段：

```sql
ALTER TABLE mem_observations
  ADD COLUMN content_fingerprint VARCHAR(64);  -- sha256:<hex>

-- 写入时计算
content_fingerprint = sha256(canonicalize({
  observation_type, content, metadata->>'tool_name', ...
}));

-- 去重查询
SELECT * FROM mem_observations
WHERE content_fingerprint = :fingerprint
ORDER BY created_at DESC LIMIT 5;
```

### 6.2 完整性验证（Integrity Check）

CE 的 `EmbeddingService` 依赖向量相似度，但无法检测"原始观察是否被篡改"。

**可落地方案**：增加 `content_hash` 字段，每次读取时验算：

```java
// ObservationEntity.java
@Column(name = "content_hash")
private String contentHash;

// 读取时验证
String computed = computeHash(canonicalize(observation));
if (!computed.equals(observation.getContentHash())) {
  throw new DataIntegrityException("Observation content tampered");
}
```

### 6.3 内容规范化（用于更好的向量搜索）

CE 的 observation 内容可能存在微小差异（如同一个问题不同措辞），影响向量相似度匹配。

**可落地方案**：在 embedding 前做规范化：

```java
// EmbeddingService.java
public String normalizeForEmbedding(ObservationEntity obs) {
  // 1. 移除随机元素（时间戳、session_id）
  // 2. 规范化错误签名（同 evolver normalizeErrorSignature）
  // 3. 提取核心语义
  // 4. 返回规范化文本再 embedding
}
```

### 6.4 Schema Version 追踪

**借鉴**：`SCHEMA_VERSION = '1.6.0'` 的版本化机制。

CE 应为 API 响应 schema 增加版本字段：

```json
{
  "schema_version": "1.0.0",
  "data": { ... }
}
```

便于前端跨版本兼容处理。

---

## 7. 与其他文档的关联

| 相关主题 | 文档 |
|----------|------|
| Gene Pool + Selection | [24](./24-gene-strategy-layer.md) |
| Signal Taxonomy | [21](./21-signal-taxonomy-and-gene-selection-memory.md)、[37](./37-signal-taxonomy-gene-selection-end-to-end.md) |
| Asset Lifecycle + A2A | [35](./35-a2a-protocol-asset-lifecycle-feedback.md) |
| Memory Graph（资产读取来源） | [18](./18-evolver-local-source-memory-architecture-snapshot.md) |
| BlueCortexCE 对照 | [09](./09-aspect-bluecortex-bridge.md) |

---

## 8. 关键源码文件

| 文件 | 行数 | 职责 |
|------|------|------|
| `src/gep/contentHash.js` | ~80 | Canonical JSON + SHA-256 哈希 + 完整性验证 |
| `src/gep/assetStore.js` | ~200+ | 资产目录布局、原子写入、基因/胶囊/候选人持久化 |
| `src/gep/candidates.js` | ~180 | 候选人提取（Transcript + Signals + Failed Capsules） |
| `src/gep/candidateEval.js` | ~120 | 候选人预览构建 + 外部资产信号匹配 |
| `src/gep/assets.js` | - | 资产入口（`getGepAssetsDir()` 等路径管理） |
