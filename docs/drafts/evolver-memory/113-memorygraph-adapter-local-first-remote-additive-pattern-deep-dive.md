# `memoryGraphAdapter.js` 深度分析：Local-First / Remote-Addictive 适配器模式

**Doc**: 113
**Cron**: `22bff79e`
**分析目标**: `src/gep/memoryGraphAdapter.js`（203 行纯 JS）
**EvoMap 版本**: v1.47.0（`e72778e`）

---

## 1. 架构定位

`memoryGraphAdapter.js` 是 memoryGraph 操作与存储后端之间的**接口抽象层**。它将 memoryGraph 的所有操作通过统一 Adapter Interface 暴露，上层（`evolve.js` / `selector.js` 等）无需关心数据存储在哪里。

```
┌─────────────────────────────────────────────────┐
│  evolve.js / selector.js / solidify.js          │
│  (Consumer -- only calls adapter methods)       │
└──────────────────┬──────────────────────────────┘
                   │ Adapter Interface (10 methods)
┌──────────────────▼──────────────────────────────┐
│         memoryGraphAdapter.js                    │
│  ┌─────────────────┐  ┌─────────────────────┐    │
│  │  Local Adapter  │  │  Remote Adapter    │    │
│  │  (default)      │  │  (MEMORY_GRAPH_    │    │
│  │                 │  │   PROVIDER=remote)  │    │
│  └────────┬────────┘  └──────────┬──────────┘    │
└───────────┼───────────────────────┼──────────────┘
            │                       │
┌───────────▼───────────┐ ┌─────────▼────────────────┐
│  memoryGraph.js       │ │  Remote KG Service       │
│  (JSONL, Local-ONLY)  │ │  (SaaS, Optional)        │
└───────────────────────┘ └──────────────────────────┘
```

---

## 2. Adapter Interface Contract

所有适配器必须实现以下 10 个方法（文档注释中明确定义）：

| 方法 | 签名 | 说明 |
|------|------|------|
| `getAdvice` | `({ signals, genes, driftEnabled })` | 获取推荐 Gene ID 和禁用人列表 |
| `recordSignalSnapshot` | `({ signals, observations })` | 记录信号快照事件 |
| `recordHypothesis` | `({ signals, mutation, personality_state, selectedGene, ... })` | 记录假设事件 |
| `recordAttempt` | `({ signals, mutation, personality_state, selectedGene, hypothesisId, ... })` | 记录尝试事件 |
| `recordOutcome` | `({ signals, observations })` | 记录结果事件 |
| `recordExternalCandidate` | `({ asset, source, signals })` | 记录外部候选（如 Hub 资产） |
| `memoryGraphPath` | `()` | 返回存储路径字符串 |
| `computeSignalKey` | `(signals)` | 计算信号规范化 key |
| `tryReadMemoryGraphEvents` | `(limit)` | 读取最近 N 条事件 |

---

## 3. Local Adapter（默认）

```javascript
const localAdapter = {
  name: 'local',
  getAdvice(opts) { return localGraph.getMemoryAdvice(opts); },
  recordSignalSnapshot(opts) { return localGraph.recordSignalSnapshot(opts); },
  recordHypothesis(opts) { return localGraph.recordHypothesis(opts); },
  recordAttempt(opts) { return localGraph.recordAttempt(opts); },
  recordOutcome(opts) { return localGraph.recordOutcomeFromState(opts); },
  recordExternalCandidate(opts) { return localGraph.recordExternalCandidate(opts); },
  memoryGraphPath() { return localGraph.memoryGraphPath(); },
  computeSignalKey(signals) { return localGraph.computeSignalKey(signals); },
  tryReadMemoryGraphEvents(limit) { return localGraph.tryReadMemoryGraphEvents(limit); },
};
```

**关键特性**：
- 一对一委托，无任何中间逻辑
- 完全保留 `memoryGraph.js` 的所有语义
- 零额外开销

---

## 4. Remote Adapter（`MEMORY_GRAPH_PROVIDER=remote`）

### 4.1 Provider 解析

```javascript
function resolveAdapter() {
  const provider = (process.env.MEMORY_GRAPH_PROVIDER || 'local').toLowerCase().trim();
  if (provider === 'remote') return buildRemoteAdapter();
  return localAdapter;
}
```

### 4.2 核心机制：Local-First, Remote-Addictive

```javascript
recordSignalSnapshot(opts) {
  const ev = localGraph.recordSignalSnapshot(opts);  // ① 本地优先写
  remoteCall('/kg/ingest', { kind: 'signal', event: ev }).catch(() => {});  // ② 异步同步到远程
  return ev;
},
```

**写操作**：`本地先写 → 异步推送到远程 → 立即返回本地结果`

**核心保证**：
- **本地是唯一真实数据源**（Source of Truth）
- 远程同步失败 → 仅 `console.warn`，不影响主流程
- 远程故障 → 完全降级到 local adapter

### 4.3 Fallback 机制

```javascript
function withFallback(localFn, remoteFn) {
  return async function (...args) {
    try {
      return await remoteFn(...args);   // 尝试远程
    } catch (e) {
      return localFn(...args);          // 降级到本地
    }
  };
}
```

适用于：`getAdvice`（可选的远程智能增强），不适用于写操作（写操作用 fire-and-forget）。

### 4.4 `getAdvice` 的特殊处理

`getAdvice` 是**唯一**使用 `withFallback` 的方法：

```javascript
getAdvice: withFallback(
  (opts) => localGraph.getMemoryAdvice(opts),        // 本地兜底
  async (opts) => {
    const result = await remoteCall('/kg/advice', { // 远程优先
      signals: opts.signals,
      genes: (opts.genes || []).map(g => ({ id: g.id, category: g.category, type: g.type })),
      driftEnabled: opts.driftEnabled,
    });
    // 标准化响应格式
    return {
      currentSignalKey: result.currentSignalKey || localGraph.computeSignalKey(opts.signals),
      preferredGeneId: result.preferredGeneId || null,
      bannedGeneIds: new Set(result.bannedGeneIds || []),
      explanation: Array.isArray(result.explanation) ? result.explanation : [],
    };
  }
)
```

**设计意图**：Hub/云端 KG 服务拥有更丰富的全局知识图谱，可以提供更精准的 Gene 推荐。但若远程不可用，则优雅降级到本地推理。

### 4.5 远程调用基础设施

```javascript
async function remoteCall(endpoint, body) {
  const url = `${remoteUrl.replace(/\/+$/, '')}${endpoint}`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);  // 5s 超时
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(remoteKey ? { Authorization: `Bearer ${remoteKey}` } : {}),
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    if (!res.ok) throw new Error(`remote_kg_error: ${res.status}`);
    return await res.json();
  } finally {
    clearTimeout(timer);
  }
}
```

**关键特性**：
- 5s 默认超时（`MEMORY_GRAPH_REMOTE_TIMEOUT_MS` 可配置）
- Bearer Token 认证
- AbortController 主动取消
- 非 2xx 响应视为错误

---

## 5. 环境变量配置

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `MEMORY_GRAPH_PROVIDER` | `local` | `local` 或 `remote` |
| `MEMORY_GRAPH_REMOTE_URL` | 空 | 远程 KG 服务端点 |
| `MEMORY_GRAPH_REMOTE_KEY` | 空 | Bearer Token |
| `MEMORY_GRAPH_REMOTE_TIMEOUT_MS` | `5000` | 远程调用超时（毫秒） |

---

## 6. 架构设计原则

### 6.1 Local-First Write（写操作）

```
本地写入 → 立即返回 → 异步推送远程
```

- 保证离线可用性（开源版本完全离线工作）
- 保证数据不丢失（本地 JSONL 是持久化的真实数据源）
- 异步推送不阻塞主循环

### 6.2 Graceful Degradation（读操作）

```
远程尝试 → 失败 → 本地降级
```

- `getAdvice` 可以利用云端更丰富的知识图谱
- 网络/服务故障时自动降级，不影响本地进化循环

### 6.3 Open-Closed Principle

添加新的存储后端（如 Redis、S3+Lambda、D1 等）**无需修改 memoryGraph.js**：
- 新建 adapter 实现类
- 改 `MEMORY_GRAPH_PROVIDER` 环境变量
- 其他代码零改动

### 6.4 接口契约文档化

Adapter Interface 在文件顶部注释中完整定义（10 个方法签名+语义说明），任何 provider 必须实现全部 10 个方法。这确保了不同 adapter 之间的行为一致性。

---

## 7. BlueCortexCE 借鉴价值

### 7.1 当前 CE 架构 vs EvoMap Adapter

| 方面 | BlueCortexCE（现状） | EvoMap（adapter 模式） |
|------|---------------------|----------------------|
| 存储层 | PostgreSQL + pgvector（单一后端） | JSONL + 可插拔 Remote KG |
| 降级能力 | 无（DB 挂则服务不可用） | 网络/远程故障时自动降级 |
| 扩展性 | 需要改代码切换存储 | 改 env 变量即可 |
| 离线支持 | 需要 DB 连接 | 完全离线可用 |

### 7.2 CE 潜在改进方向（P2）

**场景**：用户希望 BlueCortexCE 在**无网络/无 DB** 情况下仍能记录 Observation。

**方案**：`LocalFirstObservationAdapter`

```java
public interface ObservationAdapter {
    ObservationEntity record(ObservationEntity obs);   // 同步写本地
    void syncToRemote(ObservationEntity obs);         // 异步推送到远程（fire-and-forget）
    List<ObservationEntity> search(SearchRequest req); // 优先远程，降级本地
}
```

- `LocalObservationAdapter`: 直接写 PostgreSQL（当前行为）
- `CachingObservationAdapter`: 写本地 SQLite，异步同步到 PostgreSQL
- `RemoteObservationAdapter`: 写远程 API，降级到本地 SQLite

**注**：这是 P2 改进方向，CE 当前稳定运行，无紧急行动项。

### 7.3 适配器模式在 CE 的适用场景

1. **多租户隔离**：不同用户使用不同的 embedding 模型（通过 adapter 选择）
2. **混合存储**：冷数据写对象存储，热数据写 PostgreSQL
3. **A2A 场景**：Hub 间共享 memory graph 片段（Remote adapter 用于 Hub 同步）

---

## 8. 与 Doc 86（双栈语义架构）的关联

Doc 86 分析了 Worker（Chroma）和 Java（pgvector）的**语义存储分离**问题。`memoryGraphAdapter.js` 提供了一种解决思路：

```
Adapter Interface
       ↓
┌──────────────┐   ┌────────────────┐
│ Local JSONL  │   │ Remote KG API  │  ← memoryGraphAdapter 的模式
└──────────────┘   └────────────────┘
       ↓                   ↓
┌──────────────┐   ┌────────────────┐
│ memoryGraph  │   │ Remote KG SaaS │  ← Remote adapter 可以桥接两栈
└──────────────┘   └────────────────┘
```

CE 若引入 Adapter 模式，可以将 `ObservationRepository`（pgvector）和 Chroma Worker 包装在同一个 adapter interface 下，通过配置选择使用哪条语义存储路径。

---

## 9. 关键源码证据

### 9.1 完整的 Remote Adapter 写操作（Local-First）

```javascript
recordHypothesis(opts) {
  const result = localGraph.recordHypothesis(opts);   // 本地写
  remoteCall('/kg/ingest', { kind: 'hypothesis', event: result }).catch(() => {}); // 异步推送
  return result;  // 立即返回本地结果
},
```

### 9.2 `getAdvice` 降级流程

```javascript
getAdvice: withFallback(
  (opts) => localGraph.getMemoryAdvice(opts),
  async (opts) => {
    // 远程 KG 可以访问全局 Gene 知识图谱
    const result = await remoteCall('/kg/advice', { signals, genes, driftEnabled });
    return {
      currentSignalKey: result.currentSignalKey || localGraph.computeSignalKey(opts.signals),
      preferredGeneId: result.preferredGeneId || null,
      bannedGeneIds: new Set(result.bannedGeneIds || []),
      explanation: Array.isArray(result.explanation) ? result.explanation : [],
    };
  }
)
```

---

## 10. 总结

`memoryGraphAdapter.js` 实现了一个**Local-First / Remote-Addictive** 的适配器模式：

- **Local-First 写**：所有写操作先落本地 JSONL，异步推送到远程，本地是 Source of Truth
- **Remote-Addictive 读**：`getAdvice` 优先调用远程 KG 服务（更丰富的图谱推理），失败后降级本地
- **零依赖离线**：默认 local adapter，EvoMap 开源版本完全离线工作
- **Graceful Degradation**：远程服务/网络故障不影响本地进化循环
- **Open-Closed**：新增存储后端无需修改消费方代码

**BlueCortexCE 行动项**：P2 — 考虑在 `ObservationRepository` 层引入适配器模式，用于多 embedding 模型选择和多租户隔离。当前架构稳定，无紧急行动项。
