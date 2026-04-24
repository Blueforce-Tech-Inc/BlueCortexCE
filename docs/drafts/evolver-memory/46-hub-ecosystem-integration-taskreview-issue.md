# 46 · Hub Ecosystem Integration：Task Receiver + Hub Review + Issue Reporter

**文档目标**：分析 EvoMap/evolver 的 Hub 双向集成层——任务拉取（taskReceiver）、使用后 review 提交（hubReview）、自动 GitHub issue 报告（issueReporter），提炼对 BlueCortexCE 的借鉴思路。

**源码文件**：`/path/to/EvoMap/evolver/src/gep/taskReceiver.js`、`src/gep/hubReview.js`、`src/gep/issueReporter.js`、`src/gep/a2a.js`、`src/gep/directoryClient.js`（Hub 目录 API 客户端）。

**前置阅读**：
- [`34`](./34-solidify-pipeline-end-to-end.md) — solidify 管线端到端（了解完成后触发点）
- [`35`](./35-a2a-protocol-asset-lifecycle-feedback.md) — A2A 协议 / 资产生命周期（Hub ↔ Evolver 循环）
- [`36`](./36-memory-architecture-synthesis.md) — 记忆系统架构综合（8 大设计原则）
- [`44`](./44-personality-state-machine-and-hub-search-caching.md) — Hub Search 两相搜索

**最后更新**：2026-04-24（初始分析 v8.0）

---

## 1 · 概览：Hub 生态四模块角色

```
┌─────────────────────────────────────────────────────────────────────┐
│                         EvoMap Hub (central registry)                │
│  tasks · assets · reviews · bounties · questions                    │
└──────────┬──────────────────────────────────────┬──────────────────┘
           │                                      │
    ┌──────▼──────┐                       ┌───────▼───────┐
    │ taskReceiver │ ◄──── task fetch ──► │   hubReview   │
    │  (pull side)│                       │ (review side) │
    └──────┬──────┘                       └───────┬───────┘
           │ inject signals                      │ submit review
           ▼                                     │
    ┌──────────────────────┐                    │
    │  evolve.js 循环       │                    │
    │  (signals → gene →   │                    │
    │   mutation → solidify)│                    │
    └──────────┬───────────┘                    │
               │ post-solidify                  │
        ┌──────▼──────┐                         │
        │ issueReporter│                         │
        │ (auto-issue) │                         │
        └─────────────┘                          │
                                                    │
                                              submit review
                                                    │
                                            ┌───────▼───────┐
                                            │  Hub reviews  │
                                            │  /assets/:id  │
                                            └───────────────┘
```

| 模块 | 行数 | 职责 |
|------|------|------|
| `taskReceiver.js` | 566 | 从 Hub 拉取外部任务 → ROI 评分 → 注入为进化信号 |
| `hubReview.js` | 206 | solidify 完成后向 Hub 提交 usage-verified review |
| `issueReporter.js` | 262 | 持续失败时自动向 GitHub 提交 issue |
| `a2a.js` | 173 | A2A 资产广播资格判断、confidence 下调、JSONL 解析 |
| `directoryClient.js` | 110 | Hub 外部目录 API 客户端：语义搜索 / 信号搜索 / Agent Profile 查询 / 任务发现 |

---

## 2 · taskReceiver.js：Hub 任务拉取与评分

### 2.1 核心函数链路

```
fetchTasks()
  └─► POST /a2a/fetch  (tasks_only: true)
      └─► selectBestTask() → estimateCapabilityMatch() → scoreTask()
          └─► claimTask() / completeTask()
              └─► taskToSignals() → inject into evolve cycle
```

### 2.2 三策略 ROI 评分（scoreTask）

```javascript
const STRATEGY_WEIGHTS = {
  greedy:       { roi: 0.10, capability: 0.05, completion: 0.05, bounty: 0.80 },
  balanced:     { roi: 0.35, capability: 0.30, completion: 0.20, bounty: 0.15 },
  conservative: { roi: 0.25, capability: 0.45, completion: 0.25, bounty: 0.05 },
};
```

**评分公式**：
```
composite = w.roi   * (bounty / difficulty / 200)
          + w.capability * capabilityMatch
          + w.completion * historical_completion_rate
          + w.bounty  * (bounty / 100)
```

- `difficulty` 来自 Hub 的 `complexity_score`，或本地 `localDifficultyEstimate()` 估算
- `capabilityMatch` 由 `estimateCapabilityMatch()` 计算（见下节）
- `bountyNorm`：bounty / 100（封顶 1.0）

### 2.3 Capability Match 估算

```javascript
function estimateCapabilityMatch(task, memoryEvents) {
  // 1. Jaccard overlap：task signals vs all signals this agent has ever seen
  var overlapScore = jaccard(taskSignals, allSigArr);  // 40% weight

  // 2. Laplace-smoothed success rate across matching signal keys
  //    从 memoryGraph events 提取 outcome，聚合 signal key 维度的成功率
  var rate = (succ + 1) / (total + 2);  // +1/+2 = Laplace smoothing
  // 60% weight

  return overlapScore * 0.4 + successScore * 0.6;
}
```

**CE 借鉴**：BlueCortexCE 的 `SearchService` 可以参考此思想，在"历史检索成功率"维度增加信号匹配分：

```
CE_score = semantic_similarity * 0.6 + historical_success_rate * 0.4
```

其中 `historical_success_rate` 从 `ObservationEntity` 的 `outcome` 相关字段聚合（需要扩展 schema）。

### 2.4 承诺截止时间估算

```javascript
const DIFFICULTY_DURATION_MAP = [
  { threshold: 0.3, durationMs: 15 * 60 * 1000 },   // low    → 15 min
  { threshold: 0.5, durationMs: 30 * 60 * 1000 },   // medium → 30 min
  { threshold: 0.7, durationMs: 60 * 60 * 1000 },    // high   → 60 min
  { threshold: 1.0, durationMs: 120 * 60 * 1000 },  // v.high → 120 min
];
// 约束：MIN = 5min，MAX = 24h
// 还会与 Hub 提供的 task.expires_at 对齐
```

### 2.5 selectBestTask 决策逻辑

```javascript
// 优先级 1：已认领任务（断点恢复）
var myClaimedTask = tasks.find(t => t.status === 'claimed' && t.claimed_by === nodeId);
if (myClaimedTask) return myClaimedTask;

// 优先级 2：greedy 模式降级（无 history 时退化为旧行为）
if (TASK_STRATEGY === 'greedy' && !memoryEvents.length) {
  return bountyTasks[0] || open[0];  // 纯 bounty 排序
}

// 优先级 3：全量评分 + 最低 capability 过滤
var scored = open.map(t => ({
  task: t,
  composite: scoreTask(t, estimateCapabilityMatch(t, history)).composite,
  ...
}));
// TASK_MIN_CAPABILITY_MATCH 环境变量过滤（默认 0.1）
scored.sort((a, b) => b.composite - a.composite);
```

**CE 借鉴**：CE 的任务队列（如 MCP 工具调用、ingest 优先级）可以参考：
1. 已进行中的任务优先恢复
2. 低 capability 匹配的任务降级处理
3. 多维度加权评分（不只是时间戳）

---

### 2.6 directoryClient.js：Hub 目录 API 客户端

`directoryClient.js`（110 行）是 Hub **外部目录服务**的 API 客户端，与 `hubSearch.js`（本地候选搜索）**互补**——前者查询远端 Hub 注册的 Agent 能力目录，后者查询本地 skill/capsule 资产。

**与 hubSearch 的区别**：

| 维度 | `hubSearch.js` | `directoryClient.js` |
|------|----------------|----------------------|
| 数据源 | 本地 `candidates/` 文件系统 | Hub 远端 `/a2a/directory/` REST API |
| 搜索方式 | 两相（search_only / payload + LRU） | 语义查询 / 信号关键词查询 |
| 返回内容 | 本地 skill/capsule 评分与适用性 | 远端 Agent 的 domains / reputation / load |
| 用途 | 选择本地 mutation 策略候选 | 发现外部可用 Agent / 评估竞争格局 |

#### 四个核心函数

**① searchByQuery（语义搜索）**
```javascript
async function searchByQuery(query, opts) {
  const url = `${HUB_URL}/a2a/directory/search?q=${encodeURIComponent(query)}`;
  const res = await fetch(url, {
    headers: buildHubHeaders(),
    signal: AbortSignal.timeout(DIRECTORY_TIMEOUT_MS),  // 8000ms
  });
  return data.results || data;  // [{ nodeId, score, domains, reputation }, ...]
}
```
- 用途：给定自然语言任务描述（如 `"machine learning inference optimization"`），发现具备相关领域的远端 Agent
- 失败：静默返回 `null`（不阻塞主流程）

**② searchBySignals（信号搜索）**
```javascript
async function searchBySignals(signals, opts) {
  // signals = ["ml", "nlp", "inference"]
  const url = `${HUB_URL}/a2a/directory/search?signals=${signals.join(',')}`;
  // ...同上，URL 参数格式不同
}
```
- 用途：用 `learningSignals` 数组直接查匹配的 Agent
- 失败：静默返回 `null`

**③ getAgentProfile（Agent Profile 获取）**
```javascript
async function getAgentProfile(nodeId) {
  const url = `${HUB_URL}/a2a/directory/profile/${encodeURIComponent(nodeId)}`;
  // 返回：{ nodeId, domains, modelType, reputation, completedTasks, currentLoad, online }
}
```
- 用途：在考虑委托任务给某 Agent 前，查询其 reputation、当前负载、已完成任务数
- 失败：静默返回 `null`

**④ discoverForTask（任务驱动发现）**
```javascript
async function discoverForTask(task, opts) {
  // task = { title: "...", signals: "ml,nlp" }
  if (query)  return searchByQuery(query, opts);   // 优先语义
  if (signals) return searchBySignals(signals, opts);  // 次选信号
  return null;
}
```
- 用途：给定任务对象，自动选择最佳搜索策略（语义 > 信号）

#### 设计特点

1. **全异步 + fetch**：基于原生 `fetch` API，`AbortSignal.timeout` 控制 8s 上限
2. **静默失败**：任何网络错误 → `null`，不抛异常，不阻塞主流程
3. **HUB_URL 环境变量**：`A2A_HUB_URL` 或 `EVOMAP_HUB_URL`，默认 `https://evomap.ai`
4. **Hub 原点头**：`buildHubHeaders()` 复用 `a2aProtocol.js` 的 Hub 认证头，保持会话一致性
5. **未被内部调用**：这是一个**导出给外部使用**的客户端库；Evolver 内部用 `hubSearch` 搜索本地资产

#### CE 借鉴路径

| 借鉴点 | CE 落点 |
|--------|---------|
| **Agent 目录发现** | BlueCortexCE 未来可通过类似 API 发现「相似工作流的外部 CE 实例」，实现跨实例记忆共享 |
| **reputation 机制** | CE 可引入「观察有效性评分」——基于 `outcome` 聚合历史质量，对高频用户提供 reputation |
| **静默失败设计** | 所有外部 HTTP 调用（Hook/MCP）均应静默降级，不抛异常 |

---

## 3 · hubReview.js：使用后 Review 提交

### 3.1 触发条件

```javascript
// 在 solidify 完成时调用，只对 Hub 来源的资产提交 review
if (sourceType !== 'reused' && sourceType !== 'reference') {
  return { submitted: false, reason: 'not_hub_sourced' };
}
```

### 3.2 Rating 推导

```javascript
function _deriveRating(outcome, constraintCheck) {
  if (outcome?.status === 'success') {
    return outcome.score >= 0.85 ? 5 : 4;  // 高分 → 5 星
  }
  var hasViolation = constraintCheck?.violations?.length > 0;
  return hasViolation ? 1 : 2;  // 有约束违反 → 1 星，否则 2 星
}
```

### 3.3 本地去重机制

```javascript
// 文件：evolutionDir/hub_review_history.json
// 最多 500 条，FIFO 淘汰最旧条目
// 双重去重：本地文件 + Hub API 的 already_reviewed 错误码
```

**CE 借鉴**：BlueCortexCE 向外部服务提交反馈时（如向某个 Hub 提交信号），可以参考此去重机制，避免重复提交。

### 3.4 Review 内容构建

```javascript
function _buildReviewContent({ outcome, gene, signals, blast, sourceType }) {
  // 构建 markdown table 格式的 review 内容
  // 字段：Outcome、Reuse mode、Gene、Signals、Blast radius
  // 截断到 2000 字符
}
```

---

## 4 · issueReporter.js：自动 GitHub Issue

### 4.1 触发条件

```javascript
function shouldReport(signals, config) {
  var hasFailureLoop = signals.includes('failure_loop_detected');
  var hasRecurringAndHigh = signals.includes('recurring_error') && signals.includes('high_failure_ratio');

  if (!hasFailureLoop && !hasRecurringAndHigh) return false;

  var streakCount = extractStreakCount(signals);
  if (streakCount > 0 && streakCount < config.minStreak) return false;  // 默认 minStreak=5

  // Cooldown 检查（默认 24h）
  // SHA-256(error_key) 防止同一错误重复 issue
}
```

### 4.2 Issue 内容结构

```markdown
## Environment
- Evolver Version, Node.js, Platform, Container

## Failure Summary
- Consecutive failures: N
- Failure signals: recurring_errsig(3x):...

## Error Signature
[规范化错误签名，redactString 脱敏]

## Recent Evolution Events
| # | Intent | Gene | Outcome | Reason |
|---|--------|------|---------|--------|
[最多 5 条失败事件]

## Session Log Excerpt (sanitized)
[最后 2000 字符，redactString 脱敏]
```

### 4.3 去重与冷却

```javascript
// SHA-256(error_key) = signal 中 recurring_errsig + ban_gene 的组合哈希
// cooldown: 24h（可配置 EVOLVER_ISSUE_COOLDOWN_MS）
// 最大追踪 20 个 error_key（超过则淘汰最旧的）
```

### 4.4 CE 借鉴

CE 的"自报告系统"可以参考：
- **失败观测积累**：达到 N 次同类失败后自动创建 issue（GitHub/飞书）
- **脱敏**：所有日志经过 `sanitize.redactString()` 处理
- **错误签名**：同 `normalizeErrorSignature` 归一化后 SHA-256 取前 16 位
- **Cooldown**：同一错误不重复报告（防止刷屏）

---

## 5 · a2a.js：A2A 广播资格判断

### 5.1 Capsule 广播资格

```javascript
function isCapsuleBroadcastEligible(capsule) {
  // 1. score >= 0.7
  if (score < 0.7) return false;

  // 2. blast_radius 在安全范围内（A2A_MAX_FILES/LINES 环境变量）
  if (!isBlastRadiusSafe(blast)) return false;

  // 3. 至少 2 次连续成功（防止单次偶然高分）
  var streak = computeCapsuleSuccessStreak({ capsuleId: capsule.id });
  if (streak < 2) return false;

  return true;
}
```

### 5.2 外部资产 Confidence 下调

```javascript
function lowerConfidence(asset, opts) {
  // 收到 Hub 资产时，confidence *= 0.6（factor 可配置）
  // 标记 a2a.status = 'external_candidate'
  // 重新计算 asset_id（content hash）
}
```

**CE 借鉴**：CE 从外部 API/工具获取的建议，默认降低 confidence：
```java
// 外部建议 confidence = 原始建议.confidence * 0.6
// 并标记来源（a2a.status = 'external_candidate'）
```

---

## 6 · BlueCortexCE 借鉴路径

### 6.1 高优先级（可直接落地）

| 借鉴点 | CE 落点 | 实现难度 |
|--------|---------|---------|
| **失败自动报告**（issueReporter 模式） | 连续 N 次同类观察失败 → 自动在飞书创建卡片记录 | 低 |
| **Hub 来源 review 提交** | CE 向外部 Hub 提交使用反馈时参考 `hubReview` 去重机制 | 中 |
| **Capability-based task scoring** | `SearchService` 增加"历史检索成功率"维度 | 中 |
| **外部资产 confidence 下调** | 从 MCP 工具来的建议，默认 `confidence *= 0.6` | 低 |

### 6.2 中优先级（需要 schema 扩展）

| 借鉴点 | CE 落点 | 需要 |
|--------|---------|------|
| **多策略 ROI 评分** | MCP 工具调用队列评分（bounty → capability → completion）| 扩展 `ObservationEntity.outcome` |
| **承诺截止时间估算** | ingest 任务的 SLA 估算 | 新字段 |
| **连续成功 streak 保护** | 高质量观察需要 streak 验证才能广播 | 新字段 |

### 6.3 设计原则提炼

1. **Hub 双向循环**：Pull（taskReceiver）+ Push（hubReview）+ 自报告（issueReporter）构成完整反馈环
2. **非阻塞报告**：review 和 issue 提交均为非阻塞，失败不影响主流程
3. **本地去重 + 远程去重**：双重保护避免重复提交
4. **Capability 感知**：任务选择时用 Jaccard + Laplace-smoothed success rate 估算匹配度
5. **Confidence 递进下调**：外部资产逐步验证，不一次信任

---

## 附：源码速查

| 文件 | 关键导出 |
|------|---------|
| `taskReceiver.js` | `fetchTasks()`, `selectBestTask()`, `claimTask()`, `completeTask()`, `taskToSignals()`, `estimateCapabilityMatch()`, `estimateCommitmentDeadline()` |
| `hubReview.js` | `submitHubReview()` |
| `issueReporter.js` | `maybeReportIssue()`, `buildIssueBody()`, `shouldReport()` |
| `directoryClient.js` | `searchByQuery()`, `searchBySignals()`, `getAgentProfile()`, `discoverForTask()` |
| `a2a.js` | `isCapsuleBroadcastEligible()`, `isGeneBroadcastEligible()`, `lowerConfidence()`, `exportEligibleCapsules()`, `parseA2AInput()` |
