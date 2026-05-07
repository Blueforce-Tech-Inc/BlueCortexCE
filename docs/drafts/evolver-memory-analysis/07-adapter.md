# 07 — Adapter 模式与远程扩展

## 7.1 为什么需要 Adapter 模式？

EvoMap/evolver 是一个**离线优先**的自进化框架。核心设计原则：

> **本地实现是默认的、完整的、可独立运行的。远程服务是可选的、降级友好的。**

这与 Claude-Mem 的架构不同——Claude-Mem 强依赖 PostgreSQL + pgvector，而 EvoMap 的本地实现仅依赖文件系统。

## 7.2 接口契约

memoryGraphAdapter.js 定义了 9 个方法，任意 Provider 必须实现：

```javascript
// 必需方法
getAdvice({ signals, genes, driftEnabled })
  => { currentSignalKey, preferredGeneId, bannedGeneIds, explanation }

recordSignalSnapshot({ signals, observations }) => event
recordHypothesis({ signals, mutation, personality_state, selectedGene, ... }) => { hypothesisId, signalKey }
recordAttempt({ signals, mutation, personality_state, selectedGene, ... }) => { actionId, signalKey }
recordOutcome({ signals, observations }) => event | null
recordExternalCandidate({ asset, source, signals }) => event | null

// 只读方法
memoryGraphPath() => string
computeSignalKey(signals) => string
tryReadMemoryGraphEvents(limit) => event[]
```

## 7.3 本地 Adapter（默认）

```javascript
const localAdapter = {
  name: 'local',

  getAdvice(opts) {
    return localGraph.getMemoryAdvice(opts);  // 直接调用 memoryGraph.js
  },

  recordSignalSnapshot(opts) {
    return localGraph.recordSignalSnapshot(opts);
  },

  // ... 其他方法类似

  recordOutcome(opts) {
    return localGraph.recordOutcomeFromState(opts);  // 注意别名映射
  },

  memoryGraphPath() {
    return localGraph.memoryGraphPath();
  },

  computeSignalKey(signals) {
    return localGraph.computeSignalKey(signals);
  },

  tryReadMemoryGraphEvents(limit) {
    return localGraph.tryReadMemoryGraphEvents(limit);
  },
};
```

## 7.4 远程 Adapter（可选）

```javascript
function buildRemoteAdapter() {
  const remoteUrl = process.env.MEMORY_GRAPH_REMOTE_URL || '';
  const remoteKey = process.env.MEMORY_GRAPH_REMOTE_KEY || '';
  const timeoutMs = Number(process.env.MEMORY_GRAPH_REMOTE_TIMEOUT_MS) || 5000;

  async function remoteCall(endpoint, body) {
    const url = `${remoteUrl.replace(/\/+$/, '')}${endpoint}`;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(remoteKey ? { Authorization: `Bearer ${remoteKey}` } : {}),
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    clearTimeout(timer);
    if (!res.ok) throw new Error(`remote_kg_error: ${res.status}`);
    return res.json();
  }

  // 本地降级包装器
  function withFallback(localFn, remoteFn) {
    return async function (...args) {
      try {
        return await remoteFn(...args);  // 先尝试远程
      } catch (e) {
        return localFn(...args);          // 失败则降级到本地
      }
    };
  }

  return {
    name: 'remote',

    // getAdvice：最可能受益于远程 KG 的丰富推理
    getAdvice: withFallback(
      localGraph.getMemoryAdvice,
      async (opts) => {
        const result = await remoteCall('/kg/advice', {
          signals: opts.signals,
          genes: opts.genes.map(g => ({ id: g.id, category: g.category, type: g.type })),
          driftEnabled: opts.driftEnabled,
        });
        // 规范化远程响应
        return {
          currentSignalKey: result.currentSignalKey || localGraph.computeSignalKey(opts.signals),
          preferredGeneId: result.preferredGeneId || null,
          bannedGeneIds: new Set(result.bannedGeneIds || []),
          explanation: Array.isArray(result.explanation) ? result.explanation : [],
        };
      }
    ),

    // 写操作：本地优先 + 异步远程同步
    recordSignalSnapshot(opts) {
      const ev = localGraph.recordSignalSnapshot(opts);  // 同步，写入本地
      remoteCall('/kg/ingest', { kind: 'signal', event: ev }).catch(() => {});  // 异步，忽略失败
      return ev;
    },

    // ... 其他写方法类似
  };
}
```

## 7.5 Provider 解析

```javascript
function resolveAdapter() {
  const provider = (process.env.MEMORY_GRAPH_PROVIDER || 'local').toLowerCase().trim();
  if (provider === 'remote') return buildRemoteAdapter();
  return localAdapter;
}

const adapter = resolveAdapter();
module.exports = adapter;
```

**激活远程模式**：
```bash
export MEMORY_GRAPH_PROVIDER=remote
export MEMORY_GRAPH_REMOTE_URL=https://your-kg-service.com
export MEMORY_GRAPH_REMOTE_KEY=your-api-key
export MEMORY_GRAPH_REMOTE_TIMEOUT_MS=5000
```

## 7.6 关键设计决策

### 7.6.1 本地优先写，远程异步同步

```javascript
// 写操作：先本地，再远程
recordSignalSnapshot(opts) {
  const ev = localGraph.recordSignalSnapshot(opts);  // 同步，写入本地
  remoteCall('/kg/ingest', { kind: 'signal', event: ev }).catch(() => {});  // 异步，忽略失败
  return ev;
}
```

**目的**：
- 写入延迟最小化（本地磁盘 O(1)）
- 网络故障不影响本地进化
- 远程 KG 作为备份和分析引擎，不影响核心流程

### 7.6.2 远程降级不降级 getAdvice

注意：`getAdvice` 的 `withFallback` 顺序是 `remoteFn → localFn`（先远程，失败则本地），而写操作是 `localFn → remoteFn`（先本地，异步远程）。

**原因**：
- `getAdvice` 是**读取**操作，远程 KG 可能提供更丰富的图推理
- 如果远程不可用，本地 JSONL 仍能提供完整的图推理能力
- 这是唯一"先远程后本地"的方法

### 7.6.3 远程响应规范化

远程 KG 可能返回不同的字段名或结构，Adapter 负责规范化：

```javascript
// 远程可能返回 snake_case 或不同字段
const result = await remoteCall('/kg/advice', ...);

// 规范化为 localAdapter 的契约
return {
  currentSignalKey: result.currentSignalKey || localGraph.computeSignalKey(opts.signals),
  bannedGeneIds: new Set(result.bannedGeneIds || []),  // Set 化
  explanation: Array.isArray(result.explanation) ? result.explanation : [],
};
```

## 7.7 对 Claude-Mem 的借鉴价值

Claude-Mem 目前没有 Adapter 模式，直接绑定 PostgreSQL + pgvector。如果要实现类似 EvoMap 的离线优先：

```
当前：PostgreSQL (必须) → 远程服务 (不可插拔)
改进：Adapter 接口 → Local PG 实现 / Remote 降级实现
```

**参考设计**：
1. 定义 `MemoryProvider` 接口（Java Interface 或 TypeScript Interface）
2. 本地实现：`PostgresMemoryProvider`（现有实现）
3. 远程降级：`RemoteMemoryProvider`（可选，使用 REST/GRPC）
4. 写入：本地优先，异步远程同步
5. 读取：可配置本地优先或远程优先

---

_Next: [08-reflection.md](./08-reflection.md) — 反思机制与叙事记忆_
