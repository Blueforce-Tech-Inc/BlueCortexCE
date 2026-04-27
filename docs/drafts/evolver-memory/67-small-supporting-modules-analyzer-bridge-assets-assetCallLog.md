# Doc 67 — 四个小型支撑模块源码级分析

**模块**：`analyzer.js` · `bridge.js` · `assets.js` · `assetCallLog.js`  
**路径**：`src/gep/`  
**源码版本**：与 doc 66 同 commit（`3a53c2e` 最新）  
**最后更新**：2026-04-26

---

## §1 概览：四个模块在架构中的位置

| 模块 | 职责定位 | 核心 API | 规模 |
|------|----------|----------|------|
| `analyzer.js` | 元学习 / 自我纠错分析器 | `analyzeFailures()` | 988 B |
| `bridge.js` | Prompt 工件持久化 + 跨进程调用渲染 | `writePromptArtifact()` · `renderSessionsSpawnCall()` | 2096 B |
| `assets.js` | Asset 预览格式化 + Schema 规范化 | `formatAssetPreview()` · `normalizeAsset()` | 1110 B |
| `assetCallLog.js` | Hub 资产生命周期 Append-only JSONL 日志 | `logAssetCall()` · `readCallLog()` · `summarizeCallLog()` | 3478 B |

**关键设计观察**：四个模块均属于**支撑性基础设施**，不参与核心进化循环（Signal→Gene→Outcome），但在关键节点提供元级反馈（analyzer）、跨系统桥接（bridge）、资产预览（assets）和可审计性（assetCallLog）。

---

## §2 `analyzer.js` — 自我纠错分析器

### §2.1 设计意图

从 `MEMORY.md` 中解析 "F1/F2/..." 失败表格，提取 "Fix" 栏内容作为结构性反馈，供后续 Mutation 或 Reflection Prompt 使用。本质是**元学习**模块——从历史故障中提取修复知识。

### §2.2 实现解析

```javascript
// 核心：MEMORY.md 失败表格 regex
/\|\s*\*\*F\d+\*\*\s*\|\s*Fix\s*\|\s*(.*?)\s*\|\s*\*\*(.*?)\*\*\s*\((.*?)\)\s*\|/g
// 捕获组：match[1]=Fix摘要 | match[2]=详细信息 | match[3]=时间戳/来源
```

**输出格式**：
```json
{
  "status": "success",
  "count": N,
  "failures": [
    { "summary": "Fix 摘要", "detail": "详细信息" }
  ]  // 最多返回 top 3
}
```

**边界处理**：
- `MEMORY.md` 不存在 → `{ status: 'skipped', reason: 'no_memory' }`
- 无匹配行 → `{ status: 'success', count: 0, failures: [] }`
- 超过 3 条仅截取前 3

### §2.3 局限性

- 强依赖 `MEMORY.md` 中的固定 F-table 格式；格式变化则 regex 失效
- 无版本控制概念，所有历史 F 条目混在一起
- 返回 top 3 是硬编码，缺少加权逻辑（如按 severity 或 recency 排序）

### §2.4 CE 借鉴路径（P0/P1/P2）

| 优先级 | 借鉴点 | 方案 |
|--------|--------|------|
| **P2** | 失败知识提取 | 复用此 regex pattern，从 BlueCortexCE 的 `ObservationEntity`（type=failure）中提取 Fix |
| **P2** | 元级反馈注入 | `analyzer.js` 的 top-3 failures 可作为 `ReflectionService` 的 prompt 上下文 |
| **P1** | 持久化失败表格 | 当前 CE 没有 F-table 格式，可定义一个 `FailedAttemptEntity` 替代，analyzer 从 DB 查询而非 regex |

---

## §3 `bridge.js` — Prompt 工件持久化 + 跨进程调用渲染

### §3.1 `clip()` — 文本截断工具

```javascript
function clip(text, maxChars) {
  if (s.length <= n) return s;
  return s.slice(0, Math.max(0, n - 40)) + '\n...[TRUNCATED]...\n';
}
```
- 在截断位置前预留 40 字符余量，保证 `...[TRUNCATED]...` 标签完整可见
- 安全处理：`maxChars` 非正数时直接返回原文本

### §3.2 `writePromptArtifact()` — GEP Prompt 工件写入

**文件布局**：
```
{memoryDir}/
  gep_prompt_{safeCycle}_{safeRun}.txt   ← 原始 prompt 内容
  gep_prompt_{safeCycle}_{safeRun}.json  ← metadata sidecar
```

**Metadata Schema**：
```json
{
  "type": "GepPromptArtifact",
  "at": "2026-04-26T08:21:00.000Z",
  "cycle_id": "cycle-42",
  "run_id": "run-1700",
  "prompt_path": "/path/to/gep_prompt_cycle-42_1700.txt",
  "meta": { ... }  // 任意额外元数据
}
```

**关键设计**：
- **原子写入**：先写 `.txt`，再写 `.json`，无 transactional guarantee（但 sidecar 独立，不影响 prompt 本身）
- **文件名安全**：`safeCycle`/`safeRun` 清理了 `[^a-zA-Z0-9_\-#]` 和 `[^a-zA-Z0-9_]`
- **幂等性**：同名 cycle+run 覆盖旧文件

### §3.3 `renderSessionsSpawnCall()` — 跨进程调用渲染

```javascript
function renderSessionsSpawnCall({ task, agentId, label, cleanup }) {
  const payload = JSON.stringify({ task, agentId, cleanup, label });
  return `sessions_spawn(${payload})`;
}
```

**用途**：渲染一个 `sessions_spawn(JSON)` 字符串，OpenClaw wrapper 通过 `lastIndexOf('sessions_spawn(') + JSON.parse` 提取任务参数。这是一种**进程间命令协议**，而非 REST/HTTP。

**设计观察**：
- 所有参数通过 JSON 序列化传递，避免 shell 注入
- `agentId` 默认 `'main'`，`cleanup` 默认 `'delete'`，`label` 默认 `'gep_bridge'`

### §3.4 CE 借鉴路径

| 优先级 | 借鉴点 | 方案 |
|--------|--------|------|
| **P0** | Prompt 工件持久化 | BlueCortexCE 当前没有 GEP-style prompt artifact 存储；如需调试/审计 prompt，可复用此 sidecar 模式 |
| **P1** | `sessions_spawn` 协议 | 如果 CE 需与 OpenClaw 子 agent 通信，可参考此 JSON-over-string 协议，避免 HTTP 开销 |
| **P2** | `clip()` 截断 | CE 的 context window 有限，`clip()` 的 `...[TRUNCATED]...` 标记模式值得复用 |

---

## §4 `assets.js` — Asset 预览格式化 + Schema 规范化

### §4.1 `formatAssetPreview()` — Preview 渲染

```javascript
function formatAssetPreview(preview) {
  // 1. null/undefined → '(none)'
  // 2. string → try JSON.parse:
  //    - 成功且为非空数组 → JSON.stringify(pretty)
  //    - 其他 string → 直接返回
  // 3. 其他类型 → JSON.stringify(pretty)
}
```

**设计意图**：Asset preview 可能是字符串（已序列化 JSON）或原生对象，统一格式化后供 LLM prompt 阅读。

### §4.2 `normalizeAsset()` — Schema 规范化

```javascript
function normalizeAsset(asset) {
  if (!asset.schema_version) asset.schema_version = SCHEMA_VERSION;  // 从 contentHash.js 导入
  if (!asset.asset_id) asset.asset_id = computeAssetId(asset);        // SHA-256 content hash
  return asset;
}
```

**关键机制**：
- `SCHEMA_VERSION` 来自 `contentHash.js` 的 `SCHEMA_VERSION` 常量
- `computeAssetId()` 是 `contentHash.js` 导出的 SHA-256 asset ID 计算函数
- 如果 `asset_id` 已存在则不覆盖（幂等性）

### §4.3 CE 借鉴路径

| 优先级 | 借鉴点 | 方案 |
|--------|--------|------|
| **P1** | Asset preview 格式化 | CE 的 structured extraction 结果可通过 `formatAssetPreview()` 格式化后注入 prompt context |
| **P2** | Schema 规范化 | CE 的 `StructuredExtractionResult` 可复用 `schema_version` + content-hash `result_id` 模式 |

---

## §5 `assetCallLog.js` — Hub 资产生命周期 Append-only 日志

### §5.1 文件布局

- **日志路径**：`{evolution_dir}/asset_call_log.jsonl`
- **格式**：每行一个 JSON 对象（Append-only，不压缩，不轮转）
- **获取路径**：`getLogPath()` → `path.join(getEvolutionDir(), 'asset_call_log.jsonl')`

### §5.2 `logAssetCall()` — 记录资产调用

**Entry Schema**：
```typescript
{
  timestamp: string;          // ISO 8601
  run_id: string;
  action: AssetCallAction;     // 见下表
  asset_id?: string;
  asset_type?: string;
  source_node_id?: string;
  chain_id?: string;
  score?: number;
  mode?: 'direct' | 'reference';
  signals?: string[];
  reason?: string;
  extra?: object;
}
```

**6 种 Action 类型**：

| Action | 含义 |
|--------|------|
| `hub_search_hit` | Hub 语义搜索命中资产 |
| `hub_search_miss` | Hub 语义搜索未命中 |
| `asset_reuse` | 直接复用已有资产 |
| `asset_reference` | 引用外部资产（非本地） |
| `asset_publish` | 向 Hub 发布新资产 |
| `asset_publish_skip` | 发布被跳过（duplicate/cooldown 等） |

**错误处理**：完全 non-fatal——`try/catch` 包裹，失败不阻塞进化循环。

### §5.3 `readCallLog()` — 读取日志

**支持 4 种过滤**：
```javascript
readCallLog({
  run_id: 'run-42',      // 按 run 过滤
  action: 'hub_search_hit', // 按 action 类型过滤
  last: 10,              // 仅返回最后 N 条
  since: '2026-04-01T00:00:00Z'  // 时间窗口
})
```

**去重机制**：`.jsonl` 每行独立，无自动去重；调用方需自行处理重复。

### §5.4 `summarizeCallLog()` — CLI 摘要

```javascript
{
  total_entries: number,
  unique_assets: number,    // Set(asset_id).size
  unique_runs: number,       // Set(run_id).size
  by_action: {               // action → count
    'hub_search_hit': 42,
    'asset_publish': 3,
    ...
  },
  entries: [...]            // 完整 entries 数组
}
```

### §5.5 局限性

- **无轮转**：长期运行 `.jsonl` 可能无限增长（需要外部 logrotate 或定时清理）
- **无压缩**：纯文本 JSONL，存储效率低
- **无聚合视图**：仅支持时序查询，无预聚合 analytics

### §5.6 CE 借鉴路径

| 优先级 | 借鉴点 | 方案 |
|--------|--------|------|
| **P0** | Append-only asset log | CE 可用类似 JSONL 格式记录每个 structured extraction 结果（append-only，便于审计回放） |
| **P0** | Action 类型枚举 | CE 可定义 6 种 asset action 类似的 extraction action 类型（extracted/validated/rejected/deduplicated） |
| **P1** | Non-fatal logging | `assetCallLog.js` 的完全 non-fatal 设计（try/catch 包裹但静默失败）值得 CE 学习：日志失败不能影响主流程 |
| **P2** | JSONL + read filter | CE 的事件溯源可复用此 JSONL + filter 查询模式（避免引入完整数据库） |

---

## §6 综合：四个模块的协作关系

```
analyzer.js
  ↓ 读取 MEMORY.md F-table → top-3 failures
  → 作为 Reflection/innovation.js 的 prompt 上下文

bridge.js
  → writePromptArtifact(): 持久化 GEP prompt 到 .txt+.json sidecar
  → renderSessionsSpawnCall(): 渲染 sessions_spawn() 协议字符串

assets.js
  ← 被 assetCallLog.js 使用（computeAssetId from contentHash.js）
  → normalizeAsset(): 添加 schema_version + asset_id
  → formatAssetPreview(): 格式化预览供 LLM 阅读

assetCallLog.js
  → 记录 Hub 资产交互（搜索/发布/复用/引用）
  → readCallLog() / summarizeCallLog() 提供查询接口
```

**整体架构观察**：
- 这四个模块构建了一个 **Hub 资产可审计层** + **GEP Prompt 持久化层** + **元学习反馈层**
- `assets.js` 是 `assetCallLog.js` 的依赖（提供 `computeAssetId`）
- `bridge.js` 独立于资产体系，提供跨进程调用协议

---

## §7 与核心记忆架构的关系

| 核心模块 | 支撑模块 | 关系 |
|----------|----------|------|
| `memoryGraph.js` | `assetCallLog.js` | Asset Call Log 记录 memoryGraph 的 Hub 资产交互 |
| `evolve.js` | `bridge.js` | bridge 负责写入 GEP prompt artifact（evolve 的输入） |
| `reflection.js` | `analyzer.js` | analyzer 从 MEMORY.md 提取 failures → reflection 的反馈源 |
| `solidify.js` | `assets.js` | assets 规范化 solidification 输出的 asset preview |
| `curriculum.js` | `assetCallLog.js` | curriculum 的 asset 发布/引用通过 call log 审计 |

---

## §8 CE P0/P1/P2 综合建议

| 优先级 | 模块 | 具体行动 |
|--------|------|----------|
| **P0** | `assetCallLog.js` | CE 立即引入 JSONL append-only log 记录每个 extraction 结果；定义 4 种 action：extracted/rejected/validated/deduplicated |
| **P0** | `bridge.js` | CE 如果需要子 agent 协作，参考 `sessions_spawn(JSON)` 协议；否则 `clip()` 截断模式可立即复用 |
| **P1** | `analyzer.js` | CE 定义 `FailedAttemptEntity` 替代 F-table regex；从 DB 查询替代文件解析 |
| **P1** | `assets.js` | CE structured extraction 结果用 `formatAssetPreview()` 格式化后注入 prompt；`normalizeAsset()` 添加 schema_version |
| **P2** | 全模块 | 考虑将这四个模块的代码模式抽取为 CE 的 `commons/` 工具库 |

---

## §9 源码索引

| 文件 | 行数 | 最后更新 commit |
|------|------|----------------|
| `src/gep/analyzer.js` | ~30 | `d1b08fc` v1.4.1 |
| `src/gep/bridge.js` | ~65 | `3101347` fix(security): remove hardcoded Feishu token |
| `src/gep/assets.js` | ~35 | `d1b08fc` v1.4.1 |
| `src/gep/assetCallLog.js` | ~130 | `3a53c2e` feat: add asset call log for tracking Hub asset interactions |
