# 自省机制、远程适配器与本地状态感知

> **来源**：`EvoMap/evolver/src/gep/reflection.js`、`memoryGraphAdapter.js`、`localStateAwareness.js`  
> **补充**：[`23`](./23-evolver-state-event-dual-layer-and-self-awareness-loop.md)（State+Event 双层）、[`25`](./25-advanced-patterns-prm-epigenetic-antipattern.md) §7（Adaptive Reflection）  
> **最后更新**：2026-04-23

---

## 1. 自适应自省间隔（`reflection.js`）

### 1.1 核心机制：动态间隔

自省不是固定每 N 个周期执行一次，而是根据近期表现**动态调整**：

```javascript
function computeReflectionInterval(recentEvents) {
  const tail = recentEvents.slice(-3);

  if (tail.every(e => e.outcome?.status === 'success'))  return 8;   // 连续成功 → 放宽
  if (tail.every(e => e.outcome?.status === 'failed'))    return 3;   // 连续失败 → 收紧
  return 5;  // 默认
}
```

| 近期状态 | 间隔 | 设计意图 |
|----------|------|----------|
| 连续成功 | 8 周期 | 系统运转良好，减少自省开销 |
| 连续失败 | 3 周期 | 需要频繁反思调整策略 |
| 混合 | 5 周期 | 默认频率 |

### 1.2 冷却机制

```javascript
const REFLECTION_COOLDOWN_MS = 30 * 60 * 1000;  // 30 分钟

// 即使满足周期条件，如果上次自省在 30 分钟内，跳过
if (Date.now() - lastReflectionTime < REFLECTION_COOLDOWN_MS) return false;
```

这防止高频率进化周期（如自动化 cron）导致自省过于频繁。

### 1.3 人格微调建议

自省阶段会生成**人格参数调整建议**：

```javascript
function buildSuggestedMutations(signals) {
  if (hasStagnation) → { param: 'creativity', delta: +0.05 }
  if (hasError)      → { param: 'rigor', delta: +0.05 }
  if (hasCapabilityGap) → { param: 'risk_tolerance', delta: +0.05 }
}
```

每次最多调整 2 个参数，增量很小（0.05），体现**渐进式调整**原则。

### 1.4 自省上下文构建

`buildReflectionContext` 组装 LLM 提示词，包含：

1. **最近 10 个周期统计**：成功率、意图分布、gene 使用频率
2. **当前信号**：最多 20 个
3. **Memory Graph 建议**：偏好 gene、ban 列表、解释
4. **叙事记忆摘要**：最近的进化记录
5. **5 个反思问题**：
   - 是否有被忽视的持久信号？
   - gene 选择是否困在局部最优？
   - repair/optimize/innovate 平衡是否需要调整？
   - 是否有当前 gene 无法覆盖的能力缺口？
   - 最高影响的单点调整是什么？

LLM 返回结构化 JSON：`{ insights, strategy_adjustment, priority_signals }`

---

## 2. 远程适配器模式（`memoryGraphAdapter.js`）

### 2.1 架构

```
memoryGraphAdapter (interface)
    │
    ├─ localAdapter (default)  ──→ memoryGraph.js (JSONL)
    │
    └─ remoteAdapter (SaaS)    ──→ remote KG service
         │                          + localGraph (source of truth)
         └─ fallback → localAdapter on failure
```

### 2.2 本地优先写入策略

```javascript
// 写操作：先写本地，再异步同步远程
recordOutcome(opts) {
  const ev = localGraph.recordOutcomeFromState(opts);     // 同步本地写
  if (ev) {
    remoteCall('/kg/ingest', { kind: 'outcome', event: ev }).catch(() => {});
    // ↑ 异步、fire-and-forget、失败不重试
  }
  return ev;
}
```

**关键约束**：本地 JSONL 始终是 source of truth。远程是可选增强。

### 2.3 远程读取增强

```javascript
// getAdvice 是唯一有远程增强的读操作
getAdvice: withFallback(
  localGraph.getMemoryAdvice,                    // fallback
  async (opts) => remoteCall('/kg/advice', {...}) // primary
)
```

远程 KG 可以提供"更丰富的图谱推理"，但失败时无缝降级到本地。

### 2.4 配置

```javascript
MEMORY_GRAPH_PROVIDER = 'local' | 'remote'
MEMORY_GRAPH_REMOTE_URL    // KG 服务地址
MEMORY_GRAPH_REMOTE_KEY    // Bearer token
MEMORY_GRAPH_REMOTE_TIMEOUT_MS  // 默认 5000ms
```

---

## 3. 本地状态感知（`localStateAwareness.js`）

### 3.1 自模型捕获

`captureLocalState()` 在每次进化前生成 agent 的**自我状态快照**，注入到 prompt 中：

```markdown
[Node Identity]
- Node ID: xxx (REGISTERED -- do NOT re-register)
- Node Secret: PRESENT

[Environment Config]
- Env configured: A2A_NODE_ID, A2A_HUB_URL, ...
- Env not set: WORKER_ENABLED, ...

[Evolution State]
- Evolution cycles completed: 42
- Last evolution run: 120s ago
- Personality: rigor=0.6 creativity=0.4 risk_tolerance=0.3

[Memory & Knowledge]
- Memory directory: EXISTS
- MEMORY.md: 3200 bytes
- Memory graph: 128000 bytes
- Evolution narrative: EXISTS

[Skills]
- Installed skills: 5
```

### 3.2 设计意图

- **防重复注册**：明确标注 "REGISTERED -- do NOT re-register"
- **防重复配置**：列出已配置和缺失的环境变量
- **状态可见性**：agent 能看到自己的进化次数、人格状态、记忆大小
- **路径感知**：暴露文件路径，供 agent 诊断问题

---

## 4. BlueCortexCE 借鉴

### 4.1 自适应自省 → CE 上下文生成频率

| Evolver | CE 方案 |
|---------|---------|
| 连续成功 → 放宽间隔 | 连续 session 无新重要观察 → 降低 `generateContext` 频率 |
| 连续失败 → 收紧间隔 | 连续 session 有 error 观察 → 增加上下文注入量 |
| 冷却机制 | `ContextService` 的缓存 TTL 已有类似机制 |
| 人格微调 | 不适用（CE 无人格模型） |

### 4.2 远程适配器 → CE 分层存储

| Evolver | CE 方案 |
|---------|---------|
| 本地 JSONL = source of truth | PostgreSQL = source of truth |
| 远程 KG = 可选增强 | 向量搜索（pgvector）= 集成增强 |
| fire-and-forget 同步 | 已有 embedding 生成 |
| 5s 超时 + fallback | 可添加搜索超时 + 降级到关键词搜索 |

### 4.3 本地状态感知 → CE 会话元数据注入

CE 可在 context 生成时注入类似的自模型信息：

```java
// 在 generateContext 中添加：
StringBuilder selfModel = new StringBuilder();
selfModel.append("Session: ").append(sessionId).append("\n");
selfModel.append("Observations: ").append(observationCount).append("\n");
selfModel.append("Recent types: ").append(recentTypeDistribution).append("\n");
selfModel.append("Last summary: ").append(lastSummaryAge).append(" ago\n");
```

这帮助 LLM 理解当前记忆系统的状态，做出更合理的决策。

---

## 5. 综合：Evolver 记忆系统的三层自调节

```
Layer 1: Signal-level（signals.js）
  ├── 频率抑制
  ├── 连续修复检测
  ├── 空转饱和降级
  └── 失败连击干预

Layer 2: Selection-level（selector.js + memoryGraph.js）
  ├── 多因子评分
  ├── 连续漂移强度
  ├── Memory graph 偏好/ban
  └── Failed capsule ban

Layer 3: Reflection-level（reflection.js）
  ├── 自适应间隔
  ├── 人格参数微调
  └── 战略反思（LLM 驱动）
```

三层互相配合：
- Layer 1 决定"关注什么"（信号过滤）
- Layer 2 决定"怎么做"（gene 选择）
- Layer 3 决定"为什么这样做"（战略调整）

CE 可对应：
- Layer 1 → 观察类型频率分析、异常检测
- Layer 2 → 上下文检索排序、类型平衡
- Layer 3 → 定期摘要质量评估、策略调整
