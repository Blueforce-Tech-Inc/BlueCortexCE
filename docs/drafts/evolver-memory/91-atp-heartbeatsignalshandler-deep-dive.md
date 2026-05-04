# `91` ATP `heartbeatSignalsHandler.js` 深度分析

**文件**: `src/atp/heartbeatSignalsHandler.js` (254 行)
**数据来源**: 本地 `EvoMap/evolver/src/atp/heartbeatSignalsHandler.js` 源码
**分析时间**: 2026-05-05
**前身 doc**: doc 81 文件树提及但未分析

---

## 1. 背景：为什么需要这个模块？

### 1.1 问题：Merchant 节点的两类运行模式

Evolver ATP 商家端（Merchant）节点可以运行在两种模式：

| 模式 | 说明 | 能执行 ATP Task |
|------|------|----------------|
| `node index.js run` | 完整 `run()` 主循环 | ✅ 可以（LLM sub-session） |
| 后台 / 纯心跳模式 | 仅维持心跳连接 | ❌ 无法执行需 LLM 的任务 |

问题在于：Hub 在心跳响应中可能附加 `pending_deliveries`（待提交交付）或 `pending_atp_tasks`（待处理任务）。运行在纯心跳模式的节点**无法执行需 LLM 的任务**，但**可以自动提交已有结果的交付**（`submitDelivery` 是纯 HTTP POST，无需 LLM）。

`heartbeatSignalsHandler.js` 就是这个问题的解决方案——它从心跳回调直接触发 `submitDelivery`，无需 `run()` 循环。

### 1.2 安全前提

```javascript
// Safety posture:
//   - submitDelivery (phase=deliver or pending_deliveries with result_asset_id)
//     is a pure HTTP POST with a minimal auto-generated proofPayload. No LLM,
//     no spawn, safe to call from any worker context.
//   - Tasks that still need execution (phase=claim or phase=execute without a
//     result_asset_id) cannot be completed in heartbeat-only mode because they
//     require an LLM sub-session.
```

关键设计洞察：**`submitDelivery` 是纯函数式 HTTP 调用**（幂等 + 无状态），可以在任何 worker 上下文中安全调用。只需 `result_asset_id`（商家已有产出物）即可。

---

## 2. 核心架构

### 2.1 模块导出

```javascript
module.exports = {
  handleHeartbeatSignals,   // 主入口
  _internals: { ... },      // 测试用
};
```

### 2.2 状态变量（模块级）

```javascript
let _inflight = false;      // 并发保护：防止并发执行
let _lastRunAt = 0;          // 冷却追踪：上次运行时间戳
```

注意：使用简单 `let` 变量而非闭包类实例，因为这是**单例模块**（无多实例需求）。

---

## 3. 三大保护机制

### 3.1 `_isEnabled()` — 功能开关

```javascript
function _isEnabled() {
  const raw = (process.env.EVOLVER_ATP_AUTODELIVER || 'on').toLowerCase().trim();
  return raw !== 'off' && raw !== '0' && raw !== 'false';
}
```

- 环境变量 `EVOLVER_ATP_AUTODELIVER` 控制（默认 `'on'`）
- 支持多种 falsy 值检测（`'off'`, `'0'`, `'false'`）

### 3.2 `_inflight` — 并发锁

```javascript
if (_inflight) return summary;
_inflight = true;
// ... 处理逻辑 ...
_inflight = false;  // finally 块中保证重置
```

防止心跳并发触发多次处理。注意这是**粗粒度锁**——若 `handleHeartbeatSignals` 被并发调用，第二次调用直接返回空结果。

### 3.3 `HANDLER_COOLDOWN_MS = 30s` — 速率限制

```javascript
var now = Date.now();
if (_lastRunAt && (now - _lastRunAt) < HANDLER_COOLDOWN_MS) return summary;
```

两次心跳处理之间至少间隔 30 秒。即使心跳高频到达（如每 5s 一次），实际处理最多每 30s 一次。**这是节点级别的速率保护**，防止向 Hub 疯狂提交。

---

## 4. Delivery 收集与去重

### 4.1 `_collectDeliverable()` — 从两个信号源收集

```javascript
function _collectDeliverable(pendingDeliveries, pendingAtpTasks) {
  var out = [];

  // 来源 1: pending_deliveries（直接是待交付清单）
  if (Array.isArray(pendingDeliveries)) {
    for (var i = 0; i < pendingDeliveries.length; i++) {
      var r = pendingDeliveries[i];
      if (r && r.order_id && r.result_asset_id) {
        out.push({ order_id, proof_id, task_id, result_asset_id, source: 'pending_deliveries' });
      }
    }
  }

  // 来源 2: pending_atp_tasks（可能包含已执行但未提交的任务）
  if (Array.isArray(pendingAtpTasks)) {
    for (var j = 0; j < pendingAtpTasks.length; j++) {
      var t = pendingAtpTasks[j];
      if (t && t.order_id && t.result_asset_id) {
        out.push({ order_id, proof_id, task_id, result_asset_id, source: 'pending_atp_tasks' });
      }
    }
  }

  // 去重：order_id first-wins
  var seen = {};
  var dedup = [];
  for (var k = 0; k < out.length; k++) {
    if (seen[out[k].order_id]) continue;
    seen[out[k].order_id] = 1;
    dedup.push(out[k]);
  }
  return dedup;
}
```

**关键洞察**：
- `pending_atp_tasks` 中可能有已完成（有 `result_asset_id`）但未提交的任务——从心跳线程补救提交
- 两个来源都需同时满足 `order_id` AND `result_asset_id`
- 去重 key 是 `order_id`（同一订单只提交一次）

### 4.2 `need_work` 计数 — 不可完成任务的可见性

```javascript
if (Array.isArray(signals.pending_atp_tasks)) {
  for (var i = 0; i < signals.pending_atp_tasks.length; i++) {
    var t = signals.pending_atp_tasks[i];
    if (t && !t.result_asset_id) summary.need_work++;
  }
}

if (summary.need_work > 0) {
  console.log('[ATP-HB] ' + summary.need_work + ' ATP task(s) need work on this node but no run() loop is active. '
    + 'Start Evolver with `node index.js run` to pick them up. Skipping from heartbeat-only mode.');
}
```

**重要 UX 设计**：对无法完成的任务，不是静默忽略，而是**打印人类可见的警告**到 stdout。这在 `supervised runs`（supervisor 监控进程）中可直接看到问题。

---

## 5. 交付提交管线

### 5.1 Ledger 去重（防双重提交）

```javascript
var ledger = _readLedger();
// ...
if (ledger.submitted && ledger.submitted[row.order_id]) {
  summary.skipped++;
  continue;
}
```

Ledger 文件：`atp-autodeliver-ledger.json`（与 `autoDeliver.js` 共用同一文件）。

**Ledger 格式**：
```json
{
  "version": 1,
  "submitted": {
    "order_abc123": 1714896000000,   // 正数：成功提交时间戳
    "order_def456": -1714896100000   // 负数：终态失败标记
  }
}
```

- **正数**：成功提交，Hub 已接收
- **负数**（`-Date.now()`）：终态失败（400/404/409），不再重试

### 5.2 `_buildProofPayload()` — 最小化交付凭证

```javascript
function _buildProofPayload(row) {
  return {
    result: 'completed',
    asset_id: row.result_asset_id || null,
    completed_at: new Date().toISOString(),
    pass_rate: 1.0,
    submitter: 'evolver_heartbeat_deliver',
  };
}
```

心跳线程无法做 LLM 评估，所以：
- `pass_rate: 1.0`（固定满分，Hub 自会验证）
- `submitter: 'evolver_heartbeat_deliver'`（标识来源）

### 5.3 错误分类：可重试 vs 终态

```javascript
var status = resp && resp.status;
var terminal = status === 400 || status === 404 || status === 409;

if (terminal) {
  // 终态错误：不再重试（负数标记）
  ledger.submitted[row.order_id] = -Date.now();
  wrote = true;
}
```

| HTTP 状态码 | 含义 | 处理 |
|-------------|------|------|
| 400 | Bad Request（参数错误） | 终态，不重试 |
| 404 | Not Found（订单不存在） | 终态，不重试 |
| 409 | Conflict（已提交/完成） | 终态，不重试 |
| 5xx | Server Error | 可重试（下次心跳再试） |
| timeout | 网络超时 | 可重试（下次心跳再试） |

### 5.4 `_withTimeout()` — 10s 超时保护

```javascript
function _withTimeout(promise, ms) {
  return new Promise(function (resolve) {
    var done = false;
    var timer = setTimeout(function () {
      if (done) return;
      done = true;
      resolve({ ok: false, error: 'timeout', status: 0 });
    }, ms);
    Promise.resolve(promise).then(function (v) {
      if (done) return;
      done = true;
      clearTimeout(timer);
      resolve(v);
    }, function (err) { ... });
  });
}
```

标准 Promise 超时模式：**先执行，后 timeout**（timer 启动后等待 Promise 先完成）。`done` flag 防止 race condition。

---

## 6. Ledger 持久化

### 6.1 `_writeLedger()` — 有界缓存 + 原子写入

```javascript
function _writeLedger(ledger) {
  var entries = Object.entries(ledger.submitted || {});
  if (entries.length > LEDGER_MAX_ENTRIES) {
    // 裁剪为最近 500 条
    ledger.submitted = Object.fromEntries(entries.slice(-LEDGER_MAX_ENTRIES));
  }
  var tmp = _ledgerPath() + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(ledger, null, 2));
  fs.renameSync(tmp, _ledgerPath());  // 原子化
}
```

**设计要点**：
- `LEDGER_MAX_ENTRIES = 500`：有界缓存，防止磁盘无限增长
- `tmp + rename`：原子写入，防止写入中途 crash 导致 ledger 损坏
- 无 `try-catch` 对 `renameSync` 的错误处理（可能的 race，但 ledger 损坏不影响核心功能）

---

## 7. 完整调用链

```
Hub 心跳响应
  ↓
handleHeartbeatSignals(signals)
  ├─ _isEnabled() → false? return
  ├─ _inflight 锁 → true? return
  ├─ 30s cooldown → 冷却中? return
  ├─ _collectDeliverable() → deliverables[]
  │   ├─ pending_deliveries（直接）
  │   └─ pending_atp_tasks（有 result_asset_id 的）
  ├─ need_work 计数（无 result_asset_id 的任务）
  ├─ 打印警告（need_work > 0 && deliverables.length === 0）
  └─ 对每个 deliverable:
      ├─ Ledger 已提交? skip
      ├─ _buildProofPayload(row)
      ├─ hubClient.submitDelivery() + 10s timeout
      │   ├─ ok → ledger[order_id] = +Date.now()
      │   └─ terminal error(400/404/409) → ledger[order_id] = -Date.now()
      │   └─ 可重试错误 → 不写 ledger，下次心跳再试
      └─ _writeLedger(ledger)
```

---

## 8. BlueCortexCE 借鉴价值

### P1: 旁路自动提交模式

CE 目前没有 ATP 类似物，但类似场景：
- **Task Queue 处理结果自动回写**：当 LLM 生成结果后，即使 LLM session 结束，结果也应自动持久化
- **防漏设计**：心跳线程发现"已有结果但未提交"时自动补救

CE 可参考的代码模式：
```java
// CE 类似场景：Context 生成完成后自动持久化
// 即使 session 异常退出，结果也不应丢失
if (context != null && !context.isPersisted()) {
    persistAsync(context);  // 旁路补救
}
```

### P1: 多重保护机制

`heartbeatSignalsHandler` 的三重保护（`isEnabled` → `inflight` 锁 → `cooldown` 计时）组合非常实用：

```java
// CE 类似的并发/速率保护
if (rateLimiter.isCoolingDown()) return;      // 速率限制
if (inflightRef.get()) return;                  // 并发锁
inflightRef.set(true);
try {
    // 处理逻辑
} finally {
    inflightRef.set(false);
}
```

### P2: Ledger 有界缓存 + 原子写入

`LEDGER_MAX_ENTRIES = 500` + `tmp+rename` 原子写入是防数据损坏的经典模式。CE 的 Observation 写入可以用类似方式做幂等保护。

### P2: 终态错误 vs 可重试错误分离

错误分类对系统可靠性至关重要：
```java
boolean isTerminal = (status == 400 || status == 404 || status == 409);
if (isTerminal) {
    ledger.markPermanentFailure(orderId);
} else {
    // 可重试，下次再试
}
```

---

## 9. 总结评分

| 维度 | 评分 | 说明 |
|------|------|------|
| **线程安全** | ⭐⭐⭐⭐ | `_inflight` 锁 + 冷却计时 + 单线程 JS 事件循环 |
| **内存管理** | ⭐⭐⭐⭐⭐ | 无动态分配，LEDGER 有界 500 条 |
| **输入验证** | ⭐⭐⭐⭐ | Array + null 检查，`typeof signals !== 'object'` |
| **错误处理** | ⭐⭐⭐⭐ | 终态/可重试分离，超时保护，Ledger 容错 |
| **可观测性** | ⭐⭐⭐⭐ | `[ATP-HB]` 日志标识，`need_work` 计数，summary 对象 |
| **安全设计** | ⭐⭐⭐⭐⭐ | 纯 HTTP POST 无 LLM 调用，安全上下文调用 |
| **CE 借鉴价值** | P1 | 防漏机制、多重保护、有界缓存模式 |
