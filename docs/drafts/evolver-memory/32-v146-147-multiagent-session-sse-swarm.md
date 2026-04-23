# v1.46–v1.47 深度分析：多 Agent 会话兼容、SSE 事件流与蜂群 PDRI

> **来源**：`EvoMap/evolver` commit `748320f` (v1.46.0, 2026-04-06) → `e72778e` (v1.47.0, 2026-04-07)
> **变更规模**：9 files, +570 lines (v1.46) + 12 files, +1035/-1369 lines (v1.47)
> **最后更新**：2026-04-23

---

## 1. 多 Agent 会话日志兼容（v1.47 核心变更）

### 1.1 问题背景

Evolver 从单一 OpenClaw 会话读取 transcript，但实际部署中 agent 可能是 Cursor、Codex CLI、Manus 等不同运行时。各运行时的 JSONL 格式差异极大：

| 运行时 | 顶层 type | message 结构 | 特征字段 |
|--------|-----------|-------------|---------|
| OpenClaw | `message` | `message.role` + `message.content[]` | `role !== 'toolResult'` |
| Claude Code | `user` / `assistant` | `message.content[]` (input_text/output_text) | 顶层 type 直接是角色 |
| Cursor | (无 type) | `role` + `message` | 顶层 `role` 字段，无 `type` |
| Codex CLI | `item.added` / `item.completed` | `item.type` + `item.content[]` | Rollout JSONL 格式 |
| Manus | `user_message` / `assistant_message` | `data.user_message.content` | type 以 `_message` 结尾 |

### 1.2 统一检测链

**文件**：`src/evolve.js:120-220`（`formatSessionLog` 函数重写）

```javascript
// 检测优先级：Claude Code → OpenClaw → Cursor → Codex → Manus → ToolResult
const isClaudeCode = data.type === 'user' || data.type === 'assistant';
const isOpenClaw = data.type === 'message' && data.message
  && data.message.role !== 'toolResult';
const isCursor = !data.type && data.role && data.message;
const isCodexItem = (data.type === 'item.added' || data.type === 'item.completed') && data.item;
const isManus = data.type === 'user_message' || data.type === 'assistant_message';
const isToolResult = data.type === 'tool_result'
  || (data.message && data.message.role === 'toolResult');
```

**设计要点**：
- 检测顺序按**常见度**排列，Claude Code 和 OpenClaw 最常出现
- 每种格式的 content 提取统一到 `extractContent()` / `extractContentArray()` 函数
- `extractContentArray` 处理 `text` / `input_text` / `output_text` / `tool_use` / `toolCall` / `function_call` / `thinking` 等多种 content type
- **LLM 错误捕获**保留：`data.message.errorMessage` → `[LLM ERROR] ...`

### 1.3 SESSION_SOURCE 环境变量

**文件**：`src/evolve.js:98`

```javascript
const SESSION_SOURCE = (process.env.EVOLVER_SESSION_SOURCE || 'auto').toLowerCase();
```

| 值 | 行为 |
|----|------|
| `auto`（默认）| OpenClaw 优先，Cursor/Codex/Manus 回退 |
| `cursor` | 仅 Cursor/Codex/Manus transcripts |
| `openclaw` | 仅 OpenClaw sessions |
| `merge` | 合并两个来源，最新 section 在前 |

### 1.4 collectTranscriptFiles — 递归目录遍历

**文件**：`src/evolve.js:295-315`

```javascript
function collectTranscriptFiles(dir, maxDepth) {
  // 递归遍历，跳过 subagents/ 和 node_modules/
  // 返回 { path, name, time, size } 数组
  // maxDepth 控制深度（默认 3）
}
```

**用途**：Cursor/Codex/Manus 的 transcript 文件可能分散在多级子目录中，需要递归发现。OpenClaw 的 session 文件在扁平目录中，无需递归。

### 1.5 getRecentActiveSessionCount 跨源计数

**文件**：`src/evolve.js:935-965`

```javascript
function getRecentActiveSessionCount(windowMs) {
  let count = 0;
  // 1. 统计 OpenClaw sessions
  count += fs.readdirSync(AGENT_SESSIONS_DIR)...
  // 2. 统计 Cursor/Codex/Manus transcripts
  const transcriptFiles = collectTranscriptFiles(CURSOR_TRANSCRIPTS_DIR, 3);
  count += transcriptFiles.filter(f => (now - f.time) < w).length;
  return count;
}
```

**Evolver 为什么这样做**：Evolver 需要在 agent 忙碌时退避。如果只检查 OpenClaw sessions 而忽略 Cursor transcripts，会在混合部署时误判 agent 空闲，导致资源争抢。

---

## 2. SSE 事件流与自动重连（v1.46）

### 2.1 设计定位

**文件**：`src/gep/a2aProtocol.js:1087-1145`

Evolver 之前通过**心跳轮询**（POST `/a2a/heartbeat`）获取 Hub 事件。v1.46 引入 **SSE（Server-Sent Events）流**作为事件推送通道，心跳退化为保活/状态上报用途。

### 2.2 实现机制

```javascript
// 连接建立
var EventSource = require('eventsource');
var endpoint = `${HUB_URL}/a2a/events/stream?node_id=${nodeId}`;
var es = new EventSource(endpoint, esOpts);

// 事件处理
es.onmessage = function(e) {
  var event = JSON.parse(e.data);
  // 存入 _pendingHubEvents 数组，下次 evolve 循环消费
  if (!global._pendingHubEvents) global._pendingHubEvents = [];
  global._pendingHubEvents.push(event);
};
```

### 2.3 指数退避重连

```javascript
// 初始 5s，最大 120s，每次翻倍
_sseReconnectMs = 5000;
// 连接失败时：
_sseReconnectMs = Math.min(_sseReconnectMs * 2, 120000);
// 连接成功时重置为 5000
```

### 2.4 禁用开关

```javascript
if (process.env.EVOLVER_SSE_DISABLED === '1') return;  // 回退到纯轮询
```

### 2.5 BlueCortexCE 借鉴

| 发现 | Evolver 做法 | CE 翻译 | 优先级 |
|------|-------------|---------|--------|
| 长连接事件推送 | SSE 替代轮询 | CE WebUI 的 SSE (`useSSE.ts`) 已实现；**Worker→Java 可参考** | 中 |
| 指数退避重连 | 5s→120s 指数退避 | CE SSE 客户端应加入退避，避免重连风暴 | 高 |
| 优雅降级 | `EVOLVER_SSE_DISABLED=1` → 纯轮询 | CE 的 SSE 断开时应自动回退 HTTP polling | 中 |

---

## 3. HUB_EVENT_SIGNALS 扩展（35+ 事件类型）

### 3.1 事件分类

**文件**：`src/evolve.js:1481-1540`

```javascript
const HUB_EVENT_SIGNALS = {
  // 对话
  dialog_message:                ['dialog', 'respond_required'],

  // 议会/治理（6 种）
  council_invite:                ['council', 'governance', 'respond_required'],
  council_vote:                  ['council', 'vote', 'governance', 'respond_required'],
  // ...

  // 审议/辩论（4 种）
  deliberation_invite:           ['deliberation', 'governance', 'respond_required'],
  deliberation_challenge:        ['deliberation', 'challenge', 'respond_required'],

  // 协作/会话（4 种）
  collaboration_invite:          ['collaboration', 'respond_required'],
  session_nudge:                 ['collaboration', 'idle_warning'],

  // 任务/工作池（7 种）
  task_available:                ['task', 'work_available'],
  swarm_subtask_available:       ['swarm', 'task', 'work_available'],
  pipeline_step_assigned:        ['pipeline', 'task', 'work_assigned'],
  organism_work:                 ['organism', 'task', 'work_assigned'],

  // ★ 蜂群 PDRI 角色（6 种 — v1.46 新增）
  swarm_plan_available:          ['swarm', 'planner', 'work_available'],
  swarm_build_available:         ['swarm', 'builder', 'work_available'],
  swarm_review_available:        ['swarm', 'reviewer', 'work_available', 'respond_required'],
  swarm_aggregate_available:     ['swarm', 'aggregator', 'work_available'],
  swarm_rework_required:         ['swarm', 'rework', 'iterate'],
  subtask_failover:              ['swarm', 'failover', 'urgent'],

  // ★ 隐私计算（2 种 — v1.46 新增）
  privacy_task_ready:            ['privacy', 'sealed_tool', 'work_available'],
  privacy_result_available:      ['privacy', 'result'],

  // 评审/赏金（3 种）
  bounty_review_requested:       ['review', 'bounty', 'respond_required'],

  // 成长/知识（4 种）
  evolution_circle_formed:       ['evolution_circle', 'collaboration'],
  reflection_prompt:             ['reflection'],

  // 系统
  task_overdue:                  ['overdue_task', 'urgent'],
};
```

### 3.2 PDRI 蜂群模式

**PDRI = Plan → Build → Review → Iterate**

Evolver 的蜂群协作模式将任务分解为四个角色：

| 角色 | 事件类型 | 信号标签 | 职责 |
|------|---------|---------|------|
| Planner | `swarm_plan_available` | `swarm, planner, work_available` | 任务分解、方案设计 |
| Builder | `swarm_build_available` | `swarm, builder, work_available` | 代码实现、执行 |
| Reviewer | `swarm_review_available` | `swarm, reviewer, work_available, respond_required` | 代码审查、质量把关 |
| Aggregator | `swarm_aggregate_available` | `swarm, aggregator, work_available` | 结果合并、发布 |

**附加事件**：
- `swarm_rework_required` → `['swarm', 'rework', 'iterate']`（审查不通过，返回修改）
- `subtask_failover` → `['swarm', 'failover', 'urgent']`（节点故障，紧急转移）
- `team_formed` / `team_dissolved` → 蜂群生命周期管理

### 3.3 信号注入机制

```javascript
for (const ev of hubEvents) {
  const evSignals = HUB_EVENT_SIGNALS[ev.type] || ['hub_event'];
  for (const sig of evSignals) {
    if (!signals.includes(sig)) signals.unshift(sig);  // unshift = 高优先级
  }
}
// 存储事件到 evidence 供 LLM 上下文使用
global._pendingHubEventContext.push(...hubEvents);
```

**设计要点**：
- 未知事件类型 fallback 到 `['hub_event']`
- 信号 `unshift` 到数组头部，确保优先级高于普通信号
- 原始事件数据存入 `global._pendingHubEventContext`，下一次 evolve 循环时注入 LLM prompt

---

## 4. EvoMap-First Hub 搜索（问题信号自适应）

### 4.1 机制

**文件**：`src/evolve.js:1649-1670`

```javascript
const problemSignals = ['log_error', 'recurring_error', 'capability_gap',
  'perf_bottleneck', 'test_failure', 'deployment_issue'];
const hasProblemSignal = signals.some(s =>
  problemSignals.includes(s) || s.startsWith('errsig:'));
const hubSearchOpts = hasProblemSignal
  ? { timeoutMs: 12000, threshold: 0.55 }   // 更宽松
  : { timeoutMs: 8000 };                      // 默认
```

**设计思想**：当 agent 遇到问题类信号时，优先在 EvoMap Hub 中寻找已有解决方案（类似"在 Stack Overflow 搜索再自己写"）。降低阈值（0.55）和延长超时（12s）最大化复用概率。

### 4.2 BlueCortexCE 借鉴

| 发现 | CE 翻译 | 优先级 |
|------|---------|--------|
| 问题信号触发外部搜索 | CE 的 `/api/memory/search` 可在检测到 `error` 类 observation 时自动扩大搜索范围 | 中 |
| 自适应阈值 | CE 的语义搜索可按信号类型调整 `minScore` | 低 |

---

## 5. model_tier 心跳上报

**文件**：`src/gep/a2aProtocol.js:585`

```javascript
meta.model_tier = modelTier;  // 来自 EVOLVER_MODEL_TIER 环境变量
```

Hub 可根据节点的模型能力进行任务分配。例如 GPT-4 级节点接收高难度任务，GPT-3.5 级节点接收简单任务。

---

## 6. BlueCortexCE 综合借鉴

### 6.1 P0 — 立即可做

| 改进项 | 对应 Evolver 机制 | CE 实现路径 |
|--------|------------------|------------|
| SSE 重连退避 | 指数退避 5s→120s | `useSSE.ts` 添加 `reconnectInterval` 指数退避 |
| 会话日志格式检测 | Claude Code/Cursor/Codex/Manus 统一检测链 | CE proxy 的 session parser 可参考多格式检测 |

### 6.2 P1 — 近期规划

| 改进项 | 对应 Evolver 机制 | CE 实现路径 |
|--------|------------------|------------|
| 跨源活跃计数 | OpenClaw + Cursor 双源计数 | CE Worker 的 busy detection 应检查所有 session 来源 |
| 问题信号 → 扩大搜索 | EvoMap-First 自适应阈值 | CE `/api/memory/search` 的 `minScore` 可按 observation type 动态调整 |

### 6.3 P2 — 远期参考

| 改进项 | 对应 Evolver 机制 | CE 实现路径 |
|--------|------------------|------------|
| PDRI 蜂群协作 | Planner/Builder/Reviewer/Aggregator 角色信号 | CE 多 agent 协作可参考角色分工模式 |
| 隐私计算 | AES-256-GCM + sealed tool | CE 目前无 Hub 场景，暂不需要 |
| model_tier 上报 | 模型能力标签 | CE 可在 health check 中暴露 JVM/SDK 版本信息 |
