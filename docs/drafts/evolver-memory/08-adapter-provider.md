# 8. 适配器与 Provider 模式

## 8.1 适配器接口契约

`memoryGraphAdapter.js` 导出统一的适配器实例，提供以下方法：

| 方法 | 签名 | 返回 |
|------|------|------|
| `getAdvice` | `({ signals, genes, driftEnabled })` | `{ preferredGeneId, bannedGeneIds, currentSignalKey, explanation }` |
| `recordSignalSnapshot` | `({ signals, observations })` | event |
| `recordHypothesis` | `({ signals, mutation, personality_state, selectedGene, ... })` | `{ hypothesisId, signalKey }` |
| `recordAttempt` | `({ signals, mutation, selectedGene, hypothesisId, ... })` | `{ actionId, signalKey }` |
| `recordOutcome` | `({ signals, observations })` | event \| null |
| `recordExternalCandidate` | `({ asset, source, signals })` | event \| null |
| `memoryGraphPath` | `()` | string |
| `computeSignalKey` | `(signals)` | string |
| `tryReadMemoryGraphEvents` | `(limit)` | event[] |

## 8.2 Local Adapter（默认）

直接委托给 `memoryGraph.js` 的本地实现，所有操作在本地文件系统完成。

```js
const localAdapter = {
  name: 'local',
  getAdvice(opts) { return localGraph.getMemoryAdvice(opts); },
  recordSignalSnapshot(opts) { return localGraph.recordSignalSnapshot(opts); },
  // ...
};
```

## 8.3 Remote Adapter（可选 SaaS）

通过 `MEMORY_GRAPH_PROVIDER=remote` 启用：

```js
// 环境变量
MEMORY_GRAPH_REMOTE_URL=https://api.evomap.ai
MEMORY_GRAPH_REMOTE_KEY=sk_xxx
MEMORY_GRAPH_REMOTE_TIMEOUT_MS=5000
```

### 读写分离策略

```js
// getAdvice：优先远程，降级本地
getAdvice: withFallback(
  localGraph.getMemoryAdvice,
  async (opts) => {
    const result = await remoteCall('/kg/advice', opts);
    // 规范化远程响应格式
    return { currentSignalKey, preferredGeneId, bannedGeneIds, explanation };
  }
)

// 写入：先本地，后异步远程同步
recordSignalSnapshot(opts) {
  const ev = localGraph.recordSignalSnapshot(opts);
  remoteCall('/kg/ingest', { kind: 'signal', event: ev }).catch(() => {});
  return ev;
}
```

**关键**：写入操作**永远先写本地**，远程同步是异步的且失败静默。这保证了：
1. 离线时本地记录不丢失
2. 网络恢复后自动同步
3. 本地 JSONL 是唯一真相来源

## 8.4 Provider 解析

```js
function resolveAdapter() {
  const provider = (process.env.MEMORY_GRAPH_PROVIDER || 'local').toLowerCase().trim();
  if (provider === 'remote') return buildRemoteAdapter();
  return localAdapter;
}
```

## 8.5 离线降级

Remote adapter 的 `withFallback` 包装确保任何远程失败都降级到本地：

```js
function withFallback(localFn, remoteFn) {
  return async function (...args) {
    try {
      return await remoteFn(...args);
    } catch (e) {
      // 静默降级：网络错误 / 超时 / 配置缺失 / 400/500 响应
      return localFn(...args);
    }
  };
}
```

这使得 evolver 在 SaaS 不可用时仍能完整运行核心功能。
