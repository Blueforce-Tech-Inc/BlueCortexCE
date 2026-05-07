# 11 — Hub 市场集成：知识共享与任务获取

## 11.1 整体定位

EvoMap 的 Hub 集成由两个模块组成，分别解决"复用他人经验"和"贡献自身经验"两个方向的对等需求：

| 模块 | 文件 | 职责 |
|------|------|------|
| Hub Search | `hubSearch.js` | 向 Hub 市场查询可复用的 Gene/Capsule |
| Task Receiver | `taskReceiver.js` | 从 Hub 拉取外部任务、注入为高优先级信号 |

**Hub 的本质**：一个去中心化的 Gene/Capsule 市场。节点可以发布自己蒸馏的 Gene，也可以从 Hub 发现他人发布的 Gene。

**与 Claude-Mem 的类比**：Hub 类似一个"向量记忆的主动推送网络"——不是被动检索，而是节点主动订阅/拉取与自己信号匹配的知识资产。

---

## 11.2 Hub Search：Search-First Evolution

### 11.2.1 两阶段查询（最小化计费）

```
extractSignals()
       │
       ▼
hubSearch(signals)  ──────────────────────────────────┐
       │                                               │
       ▼                                               │
Phase 1: POST /a2a/fetch { signals, search_only: true }│
       │ ← Hub 返回候选元数据（免费）                   │
       ▼                                               │
并行: fetchSemanticResults()                           │
       │ ← 语义搜索补充（/a2a/assets/semantic-search）  │
       ▼                                               │
mergeResults()  ───────────────────────────────────────┤
       │                                               │
       ▼                                               │
pickBestMatch() → 选取得分最高的资产                   │
       │                                               │
       ├── [得分 < threshold] → miss                  │
       └── [得分 ≥ threshold]                         │
                 │                                     │
                 ▼                                     │
Phase 2: POST /a2a/fetch { asset_ids: [best_id] }  ←─┘
       │ ← 获取完整 payload（付费/消耗积分）
       │
       ▼
reuseAsset() → 注入为基因候选
```

**为什么要分两阶段**：
- Phase 1 只获取元数据（信号、评分、reputation），免费
- Phase 2 获取完整 Gene 内容，按次计费
- 只有当 Phase 1 找到了足够好的候选时才触发 Phase 2

### 11.2.2 结果评分公式

```javascript
function scoreHubResult(asset) {
  // confidence × 成功率_streak × (reputation / 100)
  // streak 封顶为 MAX_STREAK_CAP = 5，防止无限膨胀
  const streak = Math.min(Math.max(asset.success_streak || 0, 1), MAX_STREAK_CAP);
  const base = asset.confidence * streak * (asset.reputation_score / 100);

  // 语义相似度加分（额外奖励）
  if (asset._semantic_similarity > 0) {
    return base + asset._semantic_similarity * 0.3;
  }
  return base;
}
```

### 11.2.3 两层缓存

| 缓存层 | Key | TTL | 目的 |
|--------|-----|-----|------|
| Search 缓存 | `信号指纹（排序后 join）` | 5 分钟 | 避免重复 Phase 1 查询 |
| Payload 缓存 | `asset_id` | 永久（LRU-100） | 避免重复 Phase 2 获取 |

### 11.2.4 两种复用模式

| 模式 | 环境变量 | 行为 |
|------|----------|------|
| `reference`（默认） | `EVOLVER_REUSE_MODE=reference` | 将找到的 Gene 注入 prompt 作为参考，不直接执行 |
| `direct` | `EVOLVER_REUSE_MODE=direct` | 将找到的 Gene 直接作为候选执行 |

---

## 11.3 Task Receiver：外部任务注入

### 11.3.1 任务获取流程

```
Hub (/a2a/fetch 返回 tasks[])
       │
       ▼
fetchTasks() → 任务列表
       │
       ▼
for each task:
  capability_match = estimateCapabilityMatch(task, memoryGraphEvents)
       │
       ▼
ROI_score = 策略加权计算
  ├── greedy:      bounty × 0.80 + ...
  ├── balanced:    roi × 0.35 + capability × 0.30 + ...
  └── conservative: capability × 0.45 + completion × 0.25 + ...
       │
       ▼
按策略选任务 → 承诺截止时间 → claimTask() → 注入信号
```

### 11.3.2 能力匹配（Capability Match）

```javascript
function estimateCapabilityMatch(task, memoryEvents) {
  // 1. 信号 Jaccard 重叠度（当前节点处理过哪些信号）
  const allSignals = new Set();
  const successByKey = {};
  const totalByKey = {};
  for (const ev of memoryEvents) {
    if (ev.kind !== 'outcome') continue;
    const key = ev.signal.key;
    const status = ev.outcome.status;
    allSignals.add(...ev.signal.signals.map(s => s.toLowerCase()));
    totalByKey[key]++;
    if (status === 'success') successByKey[key]++;
  }

  // 2. 任务信号与节点历史信号的 Jaccard（40% 权重）
  const taskSignals = parseSignals(task.signals);
  const overlapScore = jaccard(taskSignals, Array.from(allSignals));

  // 3. 按信号 key 的成功率（60% 权重，Laplace 平滑）
  let weightedSuccess = 0, weightSum = 0;
  for (const key in totalByKey) {
    const sim = jaccard(taskSignals, key.split('|'));
    if (sim < 0.15) continue;
    const rate = (successByKey[key] + 1) / (totalByKey[key] + 2);
    weightedSuccess += rate * sim;
    weightSum += sim;
  }
  const successScore = weightSum > 0 ? weightedSuccess / weightSum : 0.5;

  return overlapScore * 0.4 + successScore * 0.6;
}
```

### 11.3.3 策略加权（ROI 任务选择）

```javascript
const STRATEGY_WEIGHTS = {
  greedy:       { roi: 0.10, capability: 0.05, completion: 0.05, bounty: 0.80 },
  balanced:     { roi: 0.35, capability: 0.30, completion: 0.20, bounty: 0.15 },
  conservative: { roi: 0.25, capability: 0.45, completion: 0.25, bounty: 0.05 },
};
```

---

## 11.4 Execution Trace：脱敏执行轨迹

`executionTrace.js` 在每次 `solidify()` 时构建一个**脱敏的执行轨迹**，可选地与 Hub 共享：

### 脱敏规则

| 信息 | 处理方式 | 示例 |
|------|----------|------|
| 文件路径 | 只保留 basename + ext | `src/utils/retry.js` → `retry.js` |
| 代码内容 | 不发送，只发统计指标 | 无 |
| 错误消息 | 只保留类型签名 | `TypeError: x is not a function` → `TypeError` |
| 环境变量/密钥 | 完全剥离 | 无 |
| errno | 提取名称 | `ECONNRESET` |
| HTTP 状态码 | 提取码值 | `404` → `HTTP_404` |

### Trace 级别

```javascript
const TRACE_LEVELS = { none: 0, minimal: 1, standard: 2 };

// minimal（默认）: 核心指标
{
  gene_id, outcome, files_changed_count, lines_added/removed,
  validation_result, blast_radius
}

// standard: 丰富上下文
{
  file_types, validation_commands, error_signatures[],
  tool_chain[], validation_duration_ms, canary_ok
}
```

---

## 11.5 与 Claude-Mem 的类比

| EvoMap Hub | Claude-Mem 对应机制 |
|-----------|-------------------|
| Hub Search（marketplace lookup） | 无直接对应（Claude-Mem 无主动推送/发现机制） |
| Task Receiver（pull external tasks） | 无对应 |
| Execution Trace sharing | WebUI 会话共享 |
| 两阶段查询（降低计费） | 无计费机制 |
| 语义搜索（semantic-search） | 向量语义检索（但无 marketplace 层） |
| 节点能力画像（capability match） | 无对应（Claude-Mem 无节点能力建模） |
| ROI 任务选择策略 | 无对应 |

---

_Next: [12-curriculum.md](./12-curriculum.md) — 自适应课程学习系统_
