# 81 — ATP Execute + AutoDeliver + MemoryGraph Adapter + SelfRepair

**v1.78 ATP 子系统新增模块深度分析**（doc 75 未覆盖部分）  
**数据来源**：`origin/main`（v1.78.1）`src/atp/` + `src/ops/self_repair.js` + `src/gep/memoryGraphAdapter.js` 源码  
**分析日期**：2026-05-04

---

## §1 atpExecute.js — ATP 端到端任务交付驱动

**文件**：`src/atp/atpExecute.js`（~10KB，CLI 入口 `atp-complete` 子命令）  
**定位**：ATP merchant 的"最后一公里"——Cursor 子 session 回答任务后，由本模块驱动完整结算路径。

### 1.1 调用链路

```
sessions_spawn (atp_pickup task)
  → Cursor sub-session 回答问题，写入 answer file
  → node index.js atp-complete \
        --task-id=<tid> --order-id=<oid> --answer-file=<path>
  → atpExecute.completeAtpTask()
```

### 1.2 四阶段结算管线

```
Stage 1: read_answer     — 读取 answer 文件（MAX_ANSWER_CHARS=32000，超截断）
     ↓
Stage 2: hello           — 确保 node_secret 已注册（sendHelloToHub）
     ↓
Stage 3: publish         — 发布 Gene+Capsule bundle 到 Hub（签名）
     ↓
Stage 4: complete        — 通知 Hub 任务已完成（bind resultAssetId）
     ↓
Stage 5: deliver         — 提交 DeliveryProof（submitDelivery）
```

### 1.3 Gene + Capsule 最小包

**Gene 构建**（`_buildGene`）：
- `id` = `gene_atp_answer_<capabilities>`（取前 40 字符）
- `signals_match` = 最多 8 个信号（默认 `['atp_task']`）
- `strategy` / `validation` = 内置固定模板
- `asset_id` = `computeAssetId(gene)` — 内容寻址，幂等

**Capsule 构建**（`_buildCapsule`）：
- `confidence` = 0.9（merchant 自我声明）
- `outcome.status` = `success`，`score` = confidence
- `blast_radius.files` = 0（ATP 任务是 side task，非代码演进）
- `blast_radius.lines` = `min(1000, answer.split('\n').length)`
- `source_type` = `atp_task_executor`
- `atp.order_id` / `atp.task_id` = 追踪字段

### 1.4 发布 + 签名机制

```javascript
// HMAC-SHA256 签名（nodeSecret = Hub hello 握手获得）
const signatureInput = [gene.asset_id, capsule.asset_id].sort().join('|');
const signature = crypto.createHmac('sha256', nodeSecret)
  .update(signatureInput).digest('hex');

const msg = {
  protocol: 'gep-a2a',
  protocol_version: '1.0.0',
  message_type: 'publish',
  payload: { assets: [gene, capsule], signature },
};
_postJson(_publishUrl(), msg);
```

**幂等性保证**：asset_id 是内容哈希，相同 answer → 相同 asset_id → Hub 拒绝重复发布（409）。

### 1.5 Hub 决策处理

```javascript
const decision = pub.data?.payload?.decision;
if (decision && decision !== 'accept') {
  return { ok: false, stage: 'publish', error: 'publish_rejected: ' + reason };
}
```

Hub 可能拒绝（decision ≠ accept），商户需将此错误传回上层以便重试。

### 1.6 DeliveryProof 构造

```javascript
const proofPayload = {
  asset_id: capsule.asset_id,
  result: capsule.summary,          // 摘要文本
  content_hash: capsule.asset_id,    // 内容完整性证明
  pass_rate: 1.0,                    // merchant 自评满分
  delivered_by: getNodeId(),
  task_id: taskId,
};
```

**verifyMode=auto** 时，Hub 将 `has_result=true + pass_rate=1.0` 视为自动通过，DeliveryProof 从 `pending → verified → settled`。

### 1.7 CE 借鉴（P1）

| 方面 | Evolver 实现 | CE 潜在应用 |
|------|------------|------------|
| 内容寻址幂等写入 | asset_id = SHA-256(content) | ObservationEntity 的 contentHash 字段已有类似设计 |
| 四阶段管线 + stage 错误返回 | `{ ok, stage, error }` 每阶段独立重试 | CE 的 async task 可参考 stage 化错误返回 |
| HMAC 签名发布 | nodeSecret hello 注册后使用 | CE MCP 工具调用暂无签名机制 |
| Capsule as delivery artifact | Gene+Capsule 双文件交付 | CE ObservationEntity 可考虑类似 minimal artifact 格式 |

---

## §2 autoDeliver.js — 商家自动结算守护进程

**文件**：`src/atp/autoDeliver.js`（~6.8KB）  
**定位**：解决 **"任务已完成但 DeliveryProof 从未提交"** 的生产事故（根因：Hub 路由了任务，但 Evolver 不知道要调用 `/a2a/atp/deliver`）。

### 2.1 问题背景

```
Hub routes task to merchant → marks task "claimed"
  → Evolver session answers → solidifies → result_asset_id written
  → ❌ submitDelivery never called → 7-day escrow timeout → buyer refunded
```

这正是 2026-04-27 观察到的 "0 settled in 13 days" 根因。

### 2.2 解决设计

```
autoDeliver.start({ pollMs })     — 启动时调用一次（幂等）
  └─→ setInterval(_tick, pollMs)  — 默认 60s，最小 15s
        └─→ hubClient.listMyTasks(20)
              └─→ filter: atp_order_id + result_asset_id + non-terminal status
                    └─→ hubClient.submitDelivery(orderId, proofPayload)
                          └─→ ledger[orderId] = Date.now()  (防重)
```

### 2.3 防重机制

Ledger 文件 `atp-autodeliver-ledger.json`：
```json
{
  "version": 1,
  "submitted": {
    "order_abc123": 1746364800000,
    "order_def456": -1746364800000   // 负数 = terminal error（不重试）
  }
}
```

**负数标记**：对于 `400/404/409` 等 terminal 错误，记录 `-timestamp`，下次 tick 跳过，避免无效重试。

**Ledger 上限**：`LEDGER_MAX_ENTRIES = 500`，超过裁剪旧记录。

### 2.4 触发条件三重过滤

```javascript
// 只有三个条件都满足才提交：
if (!orderId) continue;                    // ① 有 ATP order id
if (ledger.submitted[orderId]) continue;  // ② 未提交过
if (!task.result_asset_id) continue;        // ③ 有结果资产（solidify 完成）
if (task.status !== 'claimed' && task.status !== 'completed') continue;
// ④ 状态为 claimed 或 completed（其他 terminal 状态跳过）
```

### 2.5 交付证据构造

```javascript
proofPayload = {
  result: 'completed',
  asset_id: task.result_asset_id || null,
  completed_at: task.claimed_at || now,
  pass_rate: 1.0,
  signals: task.signals.slice(0, 10),
  submitter: 'evolver_auto_deliver',
};
```

注意：这里直接用 `result_asset_id`（solidify 产出的 artifact）作为 evidence，而非重新构建 Gene+Capsule——这意味着即使 `atpExecute` 没有运行（任务通过 solidify 而非 sessions_spawn 完成），autoDeliver 仍能结算。

### 2.6 CE 借鉴（P0）

| 方面 | Evolver | CE |
|------|---------|-----|
| 任务结算防漏 | polling + ledger 防重 | CE 的 async task 完成后需类似机制确保 callback/notification 不丢失 |
| terminal error 负数标记 | 400/404/409 → 不重试 | CE 可对明确失败状态（而非网络错误）做同样处理 |
| 三重过滤 | orderId + ledger + result_asset_id | CE task completion 可借鉴：状态机转换前需多重校验 |

---

## §3 memoryGraphAdapter.js — Memory Graph Provider 抽象

**文件**：`src/gep/memoryGraphAdapter.js`（~7KB）  
**定位**：为 memoryGraph 提供**本地 / 远程双 Provider 切换能力**，开源版本默认本地，云服务可插拔。

### 3.1 Adapter 接口契约

所有实现必须提供 9 个方法：

| 方法 | 签名 | 职责 |
|------|------|------|
| `getAdvice` | `({ signals, genes, driftEnabled }) → { preferredGeneId, bannedGeneIds, currentSignalKey, explanation }` | 核心建议生成 |
| `recordSignalSnapshot` | `({ signals, observations }) → event` | 信号快照写入 |
| `recordHypothesis` | `(opts) → { hypothesisId, signalKey }` | 假设记录 |
| `recordAttempt` | `(opts) → { actionId, signalKey }` | 尝试记录 |
| `recordOutcome` | `({ signals, observations }) → event\|null` | 结果记录 |
| `recordExternalCandidate` | `({ asset, source, signals }) → event\|null` | 外部候选记录 |
| `memoryGraphPath` | `() → string` | 图文件路径 |
| `computeSignalKey` | `(signals) → string` | 信号 key 计算 |
| `tryReadMemoryGraphEvents` | `(limit) → event[]` | 事件读取 |

### 3.2 Local Adapter（默认）

直接委托给 `memoryGraph.js`，无任何行为改变：

```javascript
const localAdapter = {
  name: 'local',
  getAdvice(opts) { return localGraph.getMemoryAdvice(opts); },
  recordHypothesis(opts) { return localGraph.recordHypothesis(opts); },
  // ... 其他 7 个方法同理
};
```

### 3.3 Remote Adapter（SaaS 云服务）

```javascript
function buildRemoteAdapter() {
  const remoteUrl  = process.env.MEMORY_GRAPH_REMOTE_URL || '';
  const remoteKey  = process.env.MEMORY_GRAPH_REMOTE_KEY || '';
  const timeoutMs  = Number(process.env.MEMORY_GRAPH_REMOTE_TIMEOUT_MS) || 5000;

  async function remoteCall(endpoint, body) {
    const res = await fetch(`${remoteUrl}${endpoint}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(remoteKey ? { Authorization: `Bearer ${remoteKey}` } : {}),
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    });
    if (!res.ok) throw new Error(`remote_kg_error: ${res.status}`);
    return res.json();
  }
```

**关键设计：本地优先 + 远程增强 + 自动降级**

```javascript
function withFallback(localFn, remoteFn) {
  return async function(...args) {
    try {
      return await remoteFn(...args);   // 优先远程
    } catch (e) {
      return localFn(...args);          // 任何失败 → 本地兜底
    }
  };
}
```

### 3.4 读写分离策略

| 操作类型 | 策略 | 说明 |
|---------|------|------|
| `getAdvice` | 远程优先，本地 fallback | 远程 KG 可能有更丰富的图推理 |
| 写操作（record*） | 本地先写，异步同步远程 | Append-only 本地图是 source of truth |
| 读操作（tryRead*） | 仅本地 | 读取不涉及远程同步 |

### 3.5 Provider 解析

```javascript
function resolveAdapter() {
  const provider = (process.env.MEMORY_GRAPH_PROVIDER || 'local').toLowerCase().trim();
  if (provider === 'remote') return buildRemoteAdapter();
  return localAdapter;
}
```

**默认 'local'**：开源版完全离线工作，Remote 是可选扩展。

### 3.6 CE 借鉴（P0）

| 方面 | Evolver | CE 现状 |
|------|---------|---------|
| Provider 抽象 | env 切换 local/remote | SearchService 直接依赖 EmbeddingService |
| 本地优先写 | 本地 append-only，异步 sync 远程 | ObservationRepository 直接写 DB |
| 远程读 fallback | 远程 KG 推理，本地降级 | `/api/memory/search` 仅走 DB |
| 接口契约 | 9 个方法统一定义 | MemoryService 方法分散，无统一 interface |

**CE P0 行动项**：
- 定义 `MemoryGraphProvider` interface（Java interface）
- SearchService 可注入不同 provider（本地 pgvector / 远程 KG）
- 写操作统一走 Repository，audit/log 可插拔

---

## §4 self_repair.js — Git 自愈模块

**文件**：`src/ops/self_repair.js`（72 行）  
**定位**：检测并修复 git 工作区异常状态，是 ops 自我修复基础设施的最基础一层。

### 4.1 四步修复流程

```
repair(gitRoot)
  ├─ ① git rebase --abort    （忽略错误 — 如果没有 rebase 则无操作）
  ├─ ② git merge --abort     （同上）
  ├─ ③ Remove stale index.lock  （age > LOCK_MAX_AGE_MS 才删除）
  └─ ④ git fetch origin        （always safe）
       └── optionally: git reset --hard origin/main（仅 EVOLVE_GIT_RESET=true）
```

### 4.2 锁文件年龄判断

```javascript
const stat = fs.statSync(lockFile);
const age = Date.now() - stat.mtimeMs;
if (age > LOCK_MAX_AGE_MS) {
  fs.unlinkSync(lockFile);  // 只删旧锁，不删新锁
}
```

`LOCK_MAX_AGE_MS` 来自 `config.js`（集中配置），避免误删正常的工作锁。

### 4.3 安全的 reset 门控

```javascript
if (process.env.EVOLVE_GIT_RESET === 'true') {
  execSync('git fetch origin main', ...);
  execSync('git reset --hard origin/main', ...);  // 破坏性操作，必须显式开启
} else {
  execSync('git fetch origin', ...);  // 安全操作，永远执行
}
```

**设计原则**：hard reset 是 last-resort，仅通过 env 显式开启，不自动触发。

### 4.4 CLI 接口

```bash
node index.js self-repair [--git-root=<path>]
```

### 4.5 CE 借鉴（P2）

| 方面 | Evolver | CE |
|------|---------|-----|
| 锁文件年龄判断 | mtimeMs > LOCK_MAX_AGE_MS 才删 | CE 暂无 git 操作，暂不适用 |
| 破坏性操作 gate | `EVOLVE_GIT_RESET=true` 显式开启 | CE 若引入 git 操作，参考此模式 |
| fetch always | 即使 reset 不执行，fetch 永远运行 | CE 的 external call 失败应 non-fatal |

---

## §5 ATP 模块结构全景（v1.78.1）

```
src/atp/
├── index.js              — 统一导出（9个子模块）
├── hubClient.js          — Hub API 客户端（275行）⭐ doc 75
├── atpExecute.js         — 端到端任务交付驱动  ⭐ 本 doc §1
├── atpTaskPickup.js      — 商家任务认领 → sessions_spawn  ⭐ doc 75 §6
├── autoBuyer.js          — 能力缺口自动下单  ⭐ doc 75 §5
├── autoDeliver.js        — 商家自动结算守护进程  ⭐ 本 doc §2
├── merchantAgent.js      — 商家模板（118行）  ⭐ doc 75 §3
├── consumerAgent.js      — 消费者模板（157行）  ⭐ doc 75 §4
├── serviceHelper.js      — 服务发布（99行）  ⭐ doc 75 §7
├── defaultHandler.js     — 默认订单处理器（72行）⭐ 本 doc §2.6
├── cli.js                — ATP CLI parsers（246行）  ⭐ 新分析
├── cliAutobuyPrompt.js   — auto buyer CLI prompt  ⭐ 新分析
├── heartbeatSignalsHandler.js — Hub 心跳信号处理  ⭐ 新分析
└── questionComposer.js   — ATP 问题构造  ⭐ 新分析
```

---

## §6 BlueCortexCE 优先级总结

| 优先级 | 借鉴项 | 来源模块 | 说明 |
|--------|--------|----------|------|
| **P0** | ObservationEntity + contentHash 内容寻址 | atpExecute §1.7 | CE 已有 contentHash 字段，完善幂等写入逻辑 |
| **P0** | async task stage 化错误返回 + 防重 ledger | autoDeliver §2.3 | CE task callback 防漏机制 |
| **P0** | MemoryGraphProvider interface + 本地优先写 | memoryGraphAdapter §3.6 | SearchService 可注入不同 provider |
| **P1** | HMAC 签名调用（Hub API） | atpExecute §1.4 | CE MCP 工具未来可考虑签名机制 |
| **P1** | terminal error 负数标记（400/404/409） | autoDeliver §2.3 | CE 的 failure state 分类可细化 |
| **P2** | Git hard reset env gate 模式 | self_repair §4.3 | CE 若引入 git 操作参考此设计 |
| **P2** | lock file age 判断防误删 | self_repair §4.2 | 通用资源保护模式 |

---

## 版本历史

- **81** 2026-05-04 — 初稿：atpExecute + autoDeliver + memoryGraphAdapter + self_repair
