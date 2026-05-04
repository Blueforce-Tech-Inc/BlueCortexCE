# Doc 87 — `assetStore.js` + `contentHash.js` 资产持久化与内容寻址深度分析

**模块**：`assetStore.js`（369行） · `contentHash.js`（67行）
**路径**：`src/gep/`
**源码版本**：v1.47.0（`e72778e`）
**最后更新**：2026-05-05

---

## §1 概览：资产存储层在架构中的位置

```
memoryGraph.js          ←→  events.jsonl（追加写入）
    │                           ↑
    │                    appendEventJsonl()
    ↓
selector.js             ←→  genes.json + genes.jsonl（基因模板库）
    │                           ↑
    │                    upsertGene()
    ↓
skillDistiller.js       ←→  capsules.json + capsules.jsonl（成功经验胶囊）
    │                           ↑
    │                    appendCapsule() / upsertCapsule()
    ↓
candidates.js           ←→  candidates.jsonl（能力候选）
    ↑                           ↑
    │                    appendCandidateJsonl()
candidateEval.js
```

**核心职责**：`assetStore.js` 是 Evolver 的**统一持久化接口**，管理所有 GEP 资产的生命周期（Gene、Capsule、EvolutionEvent、Candidate、FailedCapsule）。它通过 `contentHash.js` 提供内容寻址能力，实现去重和防篡改。

---

## §2 `assetStore.js` 文件布局与存储策略

### 2.1 双文件策略：JSON + JSONL

每个资产类型同时以两种格式存储：

| 资产类型 | JSON（主仓库） | JSONL（追加日志） | 用途 |
|----------|---------------|------------------|------|
| `Gene` | `genes.json`（数组） | `genes.jsonl`（每行一个） | JSON=全局快照；JSONL=历史追溯 |
| `Capsule` | `capsules.json`（数组） | `capsules.jsonl`（每行一个） | 同上 |
| `EvolutionEvent` | — | `events.jsonl`（唯一） | 纯追加，不可变 |
| `CapabilityCandidate` | — | `candidates.jsonl`（唯一） | 纯追加 |
| `FailedCapsule` | `failed_capsules.json`（数组，有上限） | — | 有界存储，超量裁剪 |
| `ExternalCandidate` | — | `external_candidates.jsonl`（唯一） | 外部来源 |

**关键设计**：
- **Append-only 事件**（events、candidates）只写 JSONL，不用 JSON——天然支持历史重放
- **有状态资产**（genes、capsules）用 JSON 做主存储 + JSONL 做审计日志
- **有界缓存**（failed_capsules）：最多200条，超出裁剪至100条——防止磁盘无限增长

### 2.2 原子写入（Atomic Write）

所有 JSON 写入使用 `writeJsonAtomic()`：

```javascript
function writeJsonAtomic(filePath, obj) {
  const tmp = `${filePath}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(obj, null, 2) + '\n', 'utf8');
  fs.renameSync(tmp, filePath);  // rename 是原子操作
}
```

**安全点**：
- 先写 `.tmp` 文件
- 再 `rename`（原子替换）
- 崩溃恢复：下次启动读 JSON 文件，如有 `.tmp` 残留则丢弃

### 2.3 大文件尾读（Large File Tail Read）

`readRecentCandidates()` 对 >1MB 的文件使用**尾读**而非全量加载：

```javascript
if (stat.size < 1024 * 1024) {
  // 全量读（< 1MB）
  const raw = fs.readFileSync(p, 'utf8');
} else {
  // 尾读：只读最后 N KB，避免 OOM
  const chunkSize = Math.min(stat.size, limit * 4096);
  const buf = Buffer.alloc(chunkSize);
  fs.readSync(fd, buf, 0, chunkSize, stat.size - chunkSize);
}
```

**思想**：append-only JSONL 文件只追加不修改，尾部就是最新数据，无需全量扫描。

---

## §3 `assetStore.js` 核心操作

### 3.1 Gene 加载：`loadGenes()`（L96–L119）

```javascript
function loadGenes() {
  // 1. 从 genes.json 读取（默认基因）
  const jsonGenes = readJsonIfExists(genesPath(), getDefaultGenes()).genes || [];
  // 2. 从 genes.jsonl 读取（蒸馏出的基因）
  const jsonlGenes = readJsonIfExists(genesPath()).split('\n')...
  // 3. 合并去重（ID 相同则后者覆盖）
  const combined = [...jsonGenes, ...jsonlGenes];
  const unique = new Map();
  combined.forEach(g => { if (g && g.id) unique.set(g.id, g); });
  return Array.from(unique.values());
}
```

**设计意图**：
- `genes.json`：内置默认基因（3个：repair_from_errors、optimize_prompt、tool_integrity）
- `genes.jsonl`：SkillDistiller 动态蒸馏出的基因
- **同 ID 覆盖**：JSONL 中的基因优先（更新的版本覆盖旧版本）

### 3.2 Capsule 管理（L281–L293）

```javascript
function appendCapsule(capsuleObj) {
  ensureSchemaFields(capsuleObj);
  // 追加到 capsules.json（数组）
  const capsules = readJsonIfExists(capsulesPath()).capsules || [];
  capsules.push(capsuleObj);
  writeJsonAtomic(capsulesPath(), { version, capsules });
}

function upsertCapsule(capsuleObj) {
  // 按 ID 查找并更新（不存在则追加）
  const idx = capsules.findIndex(c => c.id === capsuleObj.id);
  if (idx >= 0) capsules[idx] = capsuleObj;
  else capsules.push(capsuleObj);
  writeJsonAtomic(capsulesPath(), { version, capsules });
}
```

**关键区别**：
- `appendCapsule()`：新增，不检查 ID——用于成功完成的 capsule
- `upsertCapsule()`：按 ID 更新——用于更新已存在的 capsule（如 outcome 写入时）

### 3.3 FailedCapsule 有界缓存（L295–L324）

```javascript
const FAILED_CAPSULES_MAX = 200;
const FAILED_CAPSULES_TRIM_TO = 100;

function appendFailedCapsule(capsuleObj) {
  const current = readJsonIfExists(failedCapsulesPath(), getDefaultFailedCapsules());
  let list = current.failed_capsules || [];
  list.push(capsuleObj);
  if (list.length > FAILED_CAPSULES_MAX) {
    list = list.slice(list.length - FAILED_CAPSULES_TRIM_TO);  // 裁剪到100条
  }
  writeJsonAtomic(failedCapsulesPath(), { version, failed_capsules: list });
}
```

**设计意图**：失败胶囊需要保留用于 anti-pattern 学习，但超过200条时裁剪到100条——平衡历史信息和存储增长。

---

## §4 `contentHash.js` 内容寻址系统

### 4.1 规范化 JSON（Canonical JSON）

```javascript
function canonicalize(obj) {
  if (Array.isArray(obj)) {
    return '[' + obj.map(canonicalize).join(',') + ']';
  }
  if (typeof obj === 'object' && obj !== null) {
    const keys = Object.keys(obj).sort();  // 键排序保证确定性
    const pairs = [];
    for (const k of keys) {
      pairs.push(JSON.stringify(k) + ':' + canonicalize(obj[k]));
    }
    return '{' + pairs.join(',') + '}';
  }
  return JSON.stringify(obj);  // string/number/boolean/null
}
```

**关键特性**：
- **键排序**：无论源对象键的顺序如何，序列化结果一致
- **NaN → null**：非有限数统一为 null
- **递归处理嵌套对象和数组**

### 4.2 内容寻址 Asset ID

```javascript
function computeAssetId(obj, excludeFields = ['asset_id']) {
  const clean = {};
  for (const k of Object.keys(obj)) {
    if (!excludeFields.has(k)) clean[k] = obj[k];  // 排除自身
  }
  const canonical = canonicalize(clean);
  const hash = crypto.createHash('sha256').update(canonical, 'utf8').digest('hex');
  return 'sha256:' + hash;  // 前缀标识算法
}
```

**与 Git commit hash 的类比**：
- 相同内容 → 相同 hash（幂等性）
- 内容变更 → hash 变化（防篡改检测）
- `asset_id` 字段自身排除在计算之外（避免循环）

### 4.3 Schema 版本控制（L8–L9）

```javascript
const SCHEMA_VERSION = '1.6.0';
```

所有资产写入前强制注入 `schema_version` 和 `asset_id`：

```javascript
function ensureSchemaFields(obj) {
  if (!obj.schema_version) obj.schema_version = SCHEMA_VERSION;
  if (!obj.asset_id) obj.asset_id = computeAssetId(obj);
  return obj;
}
```

**版本演化策略**：
- **MINOR bump**（1.5→1.6）：新增字段，向后兼容
- **MAJOR bump**：破坏性变更

---

## §5 `assetStore.js` 启动保障

### 5.1 `ensureAssetFiles()` 惰性初始化（L350–L374）

```javascript
function ensureAssetFiles() {
  // 创建空文件（如不存在）
  // 确保外部 grep/read 命令不会因 "No such file" 失败
  const files = [
    { path: genesPath(), defaultContent: JSON.stringify(getDefaultGenes()) + '\n' },
    { path: capsulesPath(), defaultContent: JSON.stringify(getDefaultCapsules()) + '\n' },
    { path: eventsPath(), defaultContent: '' },          // 空文件 OK
    { path: candidatesPath(), defaultContent: '' },     // 空文件 OK
    { path: failedCapsulesPath(), defaultContent: JSON.stringify(getDefaultFailedCapsules()) + '\n' },
  ];
}
```

**设计意图**：append-only 日志文件如果不存在，外部的 `grep`/`tail` 命令会报错。惰性初始化确保这些文件总是存在。

---

## §6 BlueCortexCE 借鉴路径

### P0：内容寻址防重
```javascript
// CE 可借鉴：ObservationEntity 在 saveObservation 时计算 content_hash
// 写入前：用 canonicalize() 计算 SHA-256 hash
// 存储：extracted_data.content_hash 字段
// 效果：精确去重（相同内容→相同 hash），防记录篡改
```

### P0：Append-only 事件日志
```javascript
// CE 可借鉴：events.jsonl 追加模式
// 对比：CE 当前 ObservationEntity 是关系数据库 UPDATE
// 迁移路径：新增 event_log.jsonl（或数据库 append-only 表）
// 用途：审计重放、历史追溯、无需事务的增量写入
```

### P1：Schema 版本控制
```javascript
// CE 可借鉴：schema_version + ensureSchemaFields() 模式
// 在 Entity 类上添加 @Version 注解或 JSONB schema_version 字段
// 迁移时做字段兼容检查（旧数据 + 新字段 = 合法）
```

### P1：有界缓存（FailedCapsule 模式）
```javascript
// CE 可借鉴：高频错误记录的有界缓存
// ObservationEntity 表：超过 N 条时自动裁剪最旧记录
// 例如：每个 session 最多保留 200 条 error 类型 observation
// 防止单一 session 产生数百万条错误记录导致表膨胀
```

### P2：原子写入（tmp + rename）
```javascript
// CE 可借鉴：对 JSON 配置文件使用原子写入
// 对比：Java 关系数据库本身有 ACID，不需要此模式
// 适用场景：本地文件配置（如 settings.json）的原子更新
// 实现：BufferedWriter + atomic rename
```

---

## §7 与现有文档的关系

| 已覆盖 | 本文档补充 |
|--------|-----------|
| Doc 39 `contentHash.js` 概念介绍 | **源码级** canonicalize / computeAssetId 实现细节 |
| Doc 48 Gene as Compressed Memory | Gene/Capsule 的**持久化机制**（存储格式、原子写、大文件处理） |
| Doc 51 Capability Candidate 生命周期 | `candidates.jsonl` 的**大文件尾读**优化 |
| Doc 82 Solidify + Epigenetic | `ensureSchemaFields()` 的 **schema_version 注入** |

---

## §8 源码文件清单

| 文件 | 行数 | 核心职责 |
|------|------|----------|
| `src/gep/assetStore.js` | 369 | 统一持久化接口（Gene/Capsule/Event/Candidate） |
| `src/gep/contentHash.js` | 67 | 规范化 JSON + SHA-256 内容寻址 |
| `src/gep/genes.json` | — | 默认基因仓库（3个内置基因） |
| `src/gep/capsules.json` | — | Capsule 主仓库（追加写入） |
| `src/gep/events.jsonl` | — | EvolutionEvent 追加日志 |
| `src/gep/candidates.jsonl` | — | CapabilityCandidate 追加日志 |
| `src/gep/failed_capsules.json` | — | 失败 Capsule 有界缓存（≤200条） |
