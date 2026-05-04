# 88 · taskReceiver.js Worker Pool + Privacy Detection + Capability Match Deep Dive

**文档目标**：深度分析 `taskReceiver.js`（566行）中被 doc 46 简略带过或未覆盖的核心实现——Worker Pool 原子 claim+complete 模式、隐私任务检测、Capability Match 全算法、以及 fetchTasks 返回的 relevant lessons 注入管线。为 BlueCortexCE 的 async task 生命周期管理和能力匹配提供借鉴。

**源码**：`/path/to/EvoMap/evolver/src/gep/taskReceiver.js`

**前置**：
- [`46`](./46-hub-ecosystem-integration-taskreview-issue.md) — taskReceiver 概览（ROI 评分、三策略权重、selectBestTask 决策树）
- [`81`](./81-atp-execute-autodeliver-memorygraph-adapter-selfrepair.md) — atpExecute 四阶段 + HMAC 幂等（同类模式对照）
- [`12`](./12-bluecortex-api-memory-surface.md) — BlueCortexCE HTTP 写入面

**最后更新**：2026-05-05

---

## 1 · Worker Pool 任务操作（原子 claim+complete）

### 1.1 三种任务 API 的区分

`taskReceiver.js` 对 Hub 暴露了**两套**任务协议：

| 协议 | 用途 | API 端点 | 生命周期 |
|------|------|----------|----------|
| **Bounty Task** | 悬赏型外部任务 | `/a2a/task/claim` + `/a2a/task/complete` | claim → solve → complete |
| **Worker Pool Task** | 批量委托型任务 | `/a2a/work/claim` + `/a2a/work/complete` | claim → execute → complete |
| **Atomic Worker** | 原子 claim+complete（推荐） | `/a2a/work/claim` + auto-complete | claim+complete in one shot |

### 1.2 独立操作（非原子）

```javascript
// claimWorkerTask — 认领一个工作池任务
async function claimWorkerTask(taskId) {
  const url = `${HUB_URL}/a2a/work/claim`;
  const body = { task_id: taskId, node_id: nodeId };
  const res = await fetch(url, { method: 'POST', headers: buildAuthHeaders(), body: JSON.stringify(body) });
  // Returns: { id: assignment_id, ... } or null
  return res.ok ? await res.json() : null;
}

// completeWorkerTask — 完成已认领的工作
async function completeWorkerTask(assignmentId, resultAssetId) {
  const url = `${HUB_URL}/a2a/work/complete`;
  const body = { assignment_id: assignmentId, node_id: nodeId, result_asset_id: resultAssetId };
  const res = await fetch(url, { method: 'POST', headers: buildAuthHeaders(), body: JSON.stringify(body) });
  return res.ok;
}
```

### 1.3 `claimAndCompleteWorkerTask` 原子操作（核心创新）

```javascript
/**
 * Atomic claim+complete for deferred worker tasks.
 * Called from solidify after a successful evolution cycle so we never hold
 * an assignment that might expire before completion.
 *
 * @param {string} taskId
 * @param {string} resultAssetId - sha256:... of the published capsule
 * @returns {{ ok: boolean, assignment_id?: string, error?: string }}
 */
async function claimAndCompleteWorkerTask(taskId, resultAssetId) {
  const nodeId = getNodeId();
  if (!nodeId || !taskId || !resultAssetId) {
    return { ok: false, error: 'missing_params' };
  }

  // Step 1: Claim the task
  const assignment = await claimWorkerTask(taskId);
  if (!assignment) {
    return { ok: false, error: 'claim_failed' };
  }

  // Step 2: Extract assignment ID
  const assignmentId = assignment.id || assignment.assignment_id;
  if (!assignmentId) {
    return { ok: false, error: 'no_assignment_id' };
  }

  // Step 3: Complete immediately with result asset ID
  const completed = await completeWorkerTask(assignmentId, resultAssetId);
  if (!completed) {
    console.warn(`[WorkerPool] Claimed assignment ${assignmentId} but complete failed -- will expire on Hub`);
    return { ok: false, error: 'complete_failed', assignment_id: assignmentId };
  }

  return { ok: true, assignment_id: assignmentId };
}
```

**为什么需要原子操作？**

```
Problem: Non-atomic claim leaves a "zombie assignment" window
────────────────────────────────────────────────────────────────
t=0   claimWorkerTask(taskA) → success, assignment_id="xyz"
t=1   Agent starts solving taskA...
t=2   ⏰ Hub assignment EXPIRES (deadline passed)
t=3   Agent completes → completeWorkerTask("xyz") → REJECTED (already expired)
Result: Agent wasted effort; Hub thinks task still open

Solution: claimAndCompleteWorkerTask
────────────────────────────────────
t=0   claimWorkerTask(taskA) → { id: "xyz" }
t=1   completeWorkerTask("xyz", assetId) → immediately submitted
t=2   Even if Hub processes complete before expiry check → OK
Result: Assignment is fulfilled before it can expire
```

**调用时机**：从 `solidify.js` 成功后调用，确保 Gene/Capsule 发布后才 complete。如果 solidification 失败（代码验证失败、policy 违规等），整个流程中止，不执行 claimAndComplete（因为没有可发布的 asset）。

**CE 借鉴路径**：
- BlueCortexCE 的 async task（`atpExecute` 类任务）同样面临"任务认领后执行失败"的僵尸任务问题
- 可以设计 `claimAndDeliverAsyncTask(taskId, assetId)` 原子操作
- 在写入路径成功 commit 后立即 deliver，避免 task 在中间状态过期

### 1.4 Worker Pool vs Bounty Task 决策

```javascript
// selectBestTask 中的优先级：
// 1. 已认领的 Bounty 任务（断点恢复）
if (t.status === 'claimed' && t.claimed_by === nodeId) return t;

// 2. Bounty 任务（按 composite score 选）
// → 通过 fetchTasks → selectBestTask → claimTask

// 3. Worker Pool 任务（通过 claimAndCompleteWorkerTask）
// → 由 solidify 成功后直接触发，不经过 selectBestTask
```

**两种任务的生命周期**：
- **Bounty**：主动拉取 → selectBestTask 选最优 → claimTask → 内部 solve → completeTask → 注入 signals
- **Worker Pool**：solidify 成功后原子 claim+complete → 直接发布结果 asset

---

## 2 · Privacy Task 检测（PRIVACY_PARAMS）

### 2.1 `detectPrivacyTask` 实现

```javascript
function detectPrivacyTask(task) {
  if (!task) return null;
  const body = task.body || task.description || '';
  try {
    const { parsePrivacyParams } = require('./privacyClient');
    return parsePrivacyParams(body);
  } catch {
    return null;
  }
}
```

delegates 到 `privacyClient.js` 的 `parsePrivacyParams`（见 doc 57 §3）。这是一个轻量 delegation pattern：taskReceiver 不直接引入加密逻辑，保持模块边界清晰。

### 2.2 `taskToSignalsWithPrivacy` 信号增强

```javascript
function taskToSignalsWithPrivacy(task) {
  const signals = taskToSignals(task);  // base signals
  const pp = detectPrivacyTask(task);
  if (pp) {
    if (!signals.includes('privacy_computing')) signals.push('privacy_computing');
    if (!signals.includes('sealed_tool')) signals.push('sealed_tool');
  }
  return signals;
}
```

**效果**：当任务包含 PRIVACY_PARAMS 密封计算块时，自动注入 `privacy_computing` 和 `sealed_tool` 信号，使进化循环知道这个任务需要特殊处理（不暴露明文数据）。

### 2.3 调用点

`taskToSignalsWithPrivacy` vs `taskToSignals`：
- `taskToSignalsWithPrivacy` 用于**实际注入**到进化循环的场景（带隐私检测增强）
- `taskToSignals` 是基础版本（仅 task signals + title keywords）

---

## 3 · Capability Match 全算法

doc 46 §2.3 只给了概要，这里补充完整实现。

### 3.1 `estimateCapabilityMatch` 三步算法

```javascript
function estimateCapabilityMatch(task, memoryEvents) {
  // Step 1: Extract task signals (from .signals field or .title)
  var taskSignals = parseSignals(task.signals || task.title);

  // Step 2: Build signal success history from memory graph events
  var successBySignalKey = {};
  var totalBySignalKey = {};
  var allSignals = {};  // union of all signals ever seen

  for (ev of memoryEvents) {
    if (ev.kind !== 'outcome') continue;
    var sigs = ev.signal.signals;
    var key = ev.signal.key;        // "signal1|signal2|..."
    var status = ev.outcome.status; // "success" | "failure" | ...

    // Track all signals ever seen
    for (s of sigs) allSignals[s.toLowerCase()] = true;

    // Aggregate by signal key
    totalBySignalKey[key]++;
    if (status === 'success') successBySignalKey[key]++;
  }

  // Step 3: Jaccard overlap (40% weight)
  var allSigArr = Object.keys(allSignals);
  var overlapScore = jaccard(taskSignals, allSigArr);

  // Step 4: Weighted success rate (60% weight)
  var weightedSuccess = 0;
  var weightSum = 0;
  for (sk in totalBySignalKey) {
    // Reconstruct signal array from key
    var skParts = sk.split('|').map(s => s.trim().toLowerCase());
    var sim = jaccard(taskSignals, skParts);
    if (sim < 0.15) continue;  // similarity threshold filter

    var total = totalBySignalKey[sk];
    var succ = successBySignalKey[sk] || 0;
    var rate = (succ + 1) / (total + 2);  // Laplace smoothing
    weightedSuccess += rate * sim;
    weightSum += sim;
  }
  var successScore = weightSum > 0 ? weightedSuccess / weightSum : 0.5;

  // Step 5: Combine
  return Math.min(1, overlapScore * 0.4 + successScore * 0.6);
}
```

### 3.2 Jaccard 实现

```javascript
function jaccard(a, b) {
  if (!a.length || !b.length) return 0;
  var setA = new Set(a);
  var setB = new Set(b);
  var inter = 0;
  for (var v of setB) { if (setA.has(v)) inter++; }
  return inter / (setA.size + setB.size - inter);
}
```

### 3.3 `TASK_MIN_CAPABILITY_MATCH` 过滤器

```javascript
if (TASK_MIN_CAPABILITY_MATCH > 0) {
  var filtered = scored.filter(s => s.capability >= TASK_MIN_CAPABILITY_MATCH);
  if (filtered.length > 0) scored = filtered;
}
```

默认值 `0.1`，意味着即使 capability 极低的任务也不会完全被过滤掉（保守策略）。

### 3.4 CE 借鉴：Observation Type Capability Score

```
BlueCortexCE 可以实现类似的 "observation type 匹配"：

score(observation_type) = Jaccard(task_requirements, agent_capability_vector) * 0.4
                         + success_rate(observation_type) * 0.6

其中：
- agent_capability_vector = 从 AgentService 历史聚合的 observation type 成功率
- success_rate = (successes + 1) / (total + 2)  // Laplace 平滑

应用场景：
1. AsyncTask 优先级排序时考虑 capability match
2. 检索结果排序时结合历史成功率
3. 观察注入策略选择（高 capability → 优先注入）
```

---

## 4 · `fetchTasks` 返回的 relevant lessons

### 4.1 lessonL 注入管线

```javascript
// fetchTasks 返回值扩展
const result = { tasks };

// LessonL: extract relevant lessons from Hub response
if (Array.isArray(respPayload.relevant_lessons) && respPayload.relevant_lessons.length > 0) {
  result.relevant_lessons = respPayload.relevant_lessons;
}

return result;
```

**lessonL 是 Hub 基于 task signals 返回的"相关经验资产"**——与 fetchTasks 的主要任务并行返回，供 Agent 在执行前参考。

### 4.2 lessonL 与信号注入的协作

```
fetchTasks()
  │
  ├──► tasks[] → selectBestTask() → claimTask() → taskToSignalsWithPrivacy() → signals → evolve loop
  │
  └──► relevant_lessons[] → 预注入到 session context
                        → Agent 在执行前看到 "相关经验"
```

这是一个**主动学习**（proactive learning）模式：不是在失败后被动发现问题，而是在接受任务前就获取相关经验参考。

---

## 5 · 三策略权重与 composite score 全公式

### 5.1 完整 scoreTask

```javascript
function scoreTask(task, capabilityMatch) {
  var w = STRATEGY_WEIGHTS[TASK_STRATEGY] || STRATEGY_WEIGHTS.balanced;

  var difficulty = (task.complexity_score != null) ? task.complexity_score : localDifficultyEstimate(task);
  var bountyAmount = task.bounty_amount || 0;
  var completionRate = (task.historical_completion_rate != null) ? task.historical_completion_rate : 0.5;

  // ROI: bounty per unit difficulty
  var roiNorm = Math.min(bountyAmount / (difficulty + 0.1) / 200, 1);

  // Bounty absolute
  var bountyNorm = Math.min(bountyAmount / 100, 1);

  var composite =
    w.roi * roiNorm +
    w.capability * capabilityMatch +
    w.completion * completionRate +
    w.bounty * bountyNorm;

  return { composite, factors: { roiNorm, capabilityMatch, completionRate, bountyNorm, difficulty } };
}
```

### 5.2 三策略适用场景

| 策略 | 权重分配 | 适用场景 |
|------|----------|----------|
| `greedy` | bounty 80% | 新手期，无 capability history，快速积累资产 |
| `balanced` | ROI 35% + cap 30% | 成长期，质量和收益兼顾 |
| `conservative` | capability 45% | 成熟期，只做有把握的高质量任务 |

通过环境变量 `TASK_STRATEGY` 切换，不需要改代码。

### 5.3 CE 优先级矩阵借鉴

BlueCortexCE 可以设计类似的 observation injection 策略：

```javascript
const OBSERVATION_STRATEGY_WEIGHTS = {
  // 保守策略：优先历史成功率高的 observation types
  conservative: { semantic_sim: 0.3, success_rate: 0.5, novelty: 0.2 },
  // 平衡策略
  balanced: { semantic_sim: 0.4, success_rate: 0.3, novelty: 0.3 },
  // 探索策略：优先新颖 observation types
  exploratory: { semantic_sim: 0.3, success_rate: 0.2, novelty: 0.5 },
};
```

---

## 6 · `selectBestTask` 完整决策树

```
START: tasks[]
  │
  ├─► any task.status === 'claimed' && claimed_by === nodeId?
  │     └─ YES → return that task (断点恢复，最高优先级)
  │
  ├─► TASK_STRATEGY === 'greedy' && no memoryEvents?
  │     └─ YES → return highest bounty task (legacy mode)
  │
  └─► Score all open tasks:
        │
        ├─► estimateCapabilityMatch(task, memoryEvents)
        ├─► scoreTask(task, capabilityMatch) → composite
        └─► Optional: filter by TASK_MIN_CAPABILITY_MATCH (default 0.1)

      Sort by composite descending
        │
        └─► return top 1
```

**关键特性**：断点恢复（claimed 任务优先）确保 agent 不会丢失进度；greedy legacy 模式保持向后兼容；capability 过滤防止浪费资源在完全无把握的任务上。

---

## 7 · CE 行动项汇总

| 优先级 | 行动 | 对应 Evolver 模式 |
|--------|------|------------------|
| **P0** | 在 `AsyncTaskService` 实现 `claimAndDeliverAsyncTask` 原子操作，防止任务在执行中途过期 | `claimAndCompleteWorkerTask` |
| **P1** | 在 `SearchService` 引入 capability match 评分：`success_rate × 0.6 + semantic_sim × 0.4` | `estimateCapabilityMatch` |
| **P1** | 新增 `TASK_STRATEGY` 环境变量控制观察注入策略（conservative/balanced/exploratory） | `TASK_STRATEGY` |
| **P2** | 在 async task 生命周期引入 `relevant_lessons` 预注入机制（接受任务前获取相关经验） | `relevant_lessons` |
| **P2** | 实现 observation type 级别的 Laplace 平滑成功率（`(successes + 1) / (total + 2)`） | `estimateCapabilityMatch` Laplace |

---

## 附录：完整导出函数表

| 函数 | 行号范围 | 职责 |
|------|----------|------|
| `fetchTasks` | L179–L225 | 从 Hub 拉取 bounty tasks + relevant lessons |
| `parseSignals` | L236–L239 | signal 字符串解析（逗号分隔 + lowercase） |
| `jaccard` | L242–L249 | Jaccard 相似度（集合交集/并集） |
| `estimateCapabilityMatch` | L256–L302 | 从 memory graph 历史估算任务匹配度（0–1） |
| `localDifficultyEstimate` | L308–L315 | 无 Hub complexity_score 时的本地估算 |
| `estimateCommitmentDeadline` | L326–L366 | 难度 → 时间约束映射（5min–24h） |
| `scoreTask` | L378–L400 | composite score 四因子加权计算 |
| `selectBestTask` | L409–L454 | 完整决策树（断点恢复/legacy/评分） |
| `claimTask` | L463–L489 | 认领 bounty 任务 |
| `completeTask` | L498–L515 | 完成 bounty 任务 |
| `taskToSignals` | L524–L540 | task → 进化信号数组 |
| `claimWorkerTask` | L548–L570 | 认领 worker pool 任务 |
| `completeWorkerTask` | L579–L596 | 完成 worker pool 任务 |
| `claimAndCompleteWorkerTask` | L606–L633 | 原子 claim+complete（核心创新） |
| `detectPrivacyTask` | L644–L655 | 检测 PRIVACY_PARAMS 密封计算任务 |
| `taskToSignalsWithPrivacy` | L665–L675 | 带隐私检测增强的信号生成 |
