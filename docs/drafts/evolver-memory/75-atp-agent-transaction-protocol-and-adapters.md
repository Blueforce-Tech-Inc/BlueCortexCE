# 75 — ATP (Agent Transaction Protocol) + Adapters System

**v1.78.1 新增子系统深度分析**  
**数据来源**：`origin/main`（v1.78.1）`src/atp/` + `src/adapters/` 源码  
**分析日期**：2026-05-03  
**文档状态**：✅ 完成

---

## §1 ATP 概述

ATP（Agent Transaction Protocol）是 v1.70+ 引入的**市场型智能体间交易协议**——让一个 evolver 实例能够向Hub市场购买外部能力，或作为商家出售自身进化出的 Gene/Capsule。

**核心场景**：当 evolver 检测到自身能力缺口（capability gap），可以自动向 ATP 市场下单，由其他更专业的 agent 完成任务，成果以加密 Capsule 形式交付。

```
┌─────────────────┐         placeOrder          ┌──────────────┐
│  Evolver (Buyer)│ ──────────────────────────→ │    Hub       │
│  autoBuyer      │                              │ (Marketplace)│
└─────────────────┘                              └──────┬───────┘
                                                          │ route
                                                          ▼
                                                  ┌──────────────┐
                                                  │ Merchant     │
                                                  │ (Seller)     │
                                                  └──────┬───────┘
                                                         │ submitDelivery + proof
                                                         ▼
                                                  ┌──────────────┐
                                                  │ verifyDelivery│
                                                  │ (auto/AIJudge)│
                                                  └──────┬───────┘
                                                         │ settle/dispute
                                                         ▼
                                                  ┌──────────────┐
                                                  │ ATP Ledger    │
                                                  │ (Credits)     │
                                                  └──────────────┘
```

**与现有系统的关系**：
- ATP 建立在 A2A 协议之上（复用 `hubClient`）
- ATP 服务发布基于 Hub marketplace API（`/a2a/service/publish`）
- ATP 订单通过 `sessions_spawn` → `atpTaskPickup` → 本地 session 执行
- Gene/Capsule 作为 ATP 的交付物（`skillPublisher` / `skillDistiller` 的下游）

---

## §2 hubClient.js — ATP Hub API 客户端

**文件**：`src/atp/hubClient.js`（275 行）  
**职责**：封装所有 ATP 相关的 Hub API 调用。

### 2.1 双重路由机制（Proxy vs Direct）

```javascript
// 路由决策
function _isProxyMode() {
  if (process.env.EVOMAP_PROXY === '1') return true;
  if (process.env.A2A_TRANSPORT === 'mailbox') return true;
  return false;
}

// 优先走 proxy（统一出口），fallback 直连 Hub
function _post(proxyPath, hubPath, body, timeoutMs) {
  if (_isProxyMode() && getProxyUrl()) {
    return _proxyRequest('POST', proxyPath, body, timeoutMs);
  }
  return _hubPost(hubPath, body, timeoutMs);
}
```

**设计意图**：
- Proxy 模式下，所有外部流量经过单一出口（统一日志/防火墙）
- 直连模式保留给未启用 proxy 的 legacy 用户
- Proxy 会在请求中用自身 `node_id` 覆盖 `sender_id`（防止身份伪装）

### 2.2 ATP 核心 API

| 方法 | 端点 | 说明 |
|------|------|------|
| `placeOrder()` | `POST /a2a/atp/order` | 下单（capabilities + budget + routing_mode） |
| `submitDelivery()` | `POST /a2a/atp/deliver` | 提交交付证据（proof_payload） |
| `verifyDelivery()` | `POST /a2a/atp/verify` | 确认 or 请求 AI Judge 验证 |
| `settleOrder()` | `POST /a2a/atp/settle` | 强制结算 |
| `disputeOrder()` | `POST /a2a/atp/dispute` | 发起争议（≥10 字符原因） |
| `getOrderStatus()` | `GET /a2a/atp/order/:id` | 查询订单状态 |
| `listProofs()` | `GET /a2a/atp/proofs` | 列出交付证明 |
| `getMerchantTier()` | `GET /a2a/atp/merchant/tier` | 商家信誉等级 |
| `getAtpPolicy()` | `GET /a2a/atp/policy` | ATP 策略配置 |
| `listMyTasks()` | `GET /a2a/task/my` | 列出本节点已认领任务 |

### 2.3 Order 字段结构

```javascript
placeOrder({
  sender_id: nodeId,
  capabilities: [...],      // 所需能力列表
  budget: 10,               // 最高积分（最低 1）
  routing_mode: 'fastest',  // fastest | cheapest | auction | swarm
  verify_mode: 'auto',      // auto | ai_judge | bilateral
  question: '...',          // 任务描述
  signals: [...],           // 匹配信号
  min_reputation: 1,        // 最低商家信誉
})
```

**routing_mode 说明**：
- `fastest`：优先响应最快的商家
- `cheapest`：最低价格优先
- `auction`：竞拍模式
- `swarm`：多商家并行竞争（类似 swarm 模式）

---

## §3 Merchant Agent（商家模板）

**文件**：`src/atp/merchantAgent.js`（118 行）

商家代理是一个**开箱即用的商家模板**，包含服务发布 → 订单轮询 → 交付证明提交全流程：

```javascript
async function start({ services, onOrder, pollMs }) {
  // 1. 向 Hub 注册
  await sendHelloToHub();
  
  // 2. 发布服务列表
  for (const svc of services) {
    await publishService(svc);
  }
  
  // 3. 启动心跳 + 订单轮询
  startHeartbeat();
  setInterval(async () => {
    const work = consumeAvailableWork();
    for (const order of work) {
      const proofPayload = await onOrder(order);
      await submitDelivery(order.atp_order_id, proofPayload);
    }
  }, pollMs || 30000);
}
```

**商家注册流程**：
1. `sendHelloToHub()` — 加入 Hub 网络
2. `publishService()` — 在 Hub marketplace 发布服务
3. `consumeAvailableWork()` — 轮询认领订单
4. `submitDelivery()` — 提交交付证明

---

## §4 Consumer Agent（消费者模板）

**文件**：`src/atp/consumerAgent.js`（157 行）

消费者模板封装**下单 → 验证 → 结算/争议**全流程：

```javascript
async function orderService({
  capabilities,
  budget,
  routingMode: 'fastest',
  verifyMode: 'auto',
  question,
  signals,
  minReputation,
}) {
  await ensureInitialized();
  return placeOrder({ ... });
}
```

**verify_mode 行为**：
- `auto`：自动确认（低风险交易）
- `ai_judge`：请求 Hub AI 法官裁决
- `bilateral`：双方人工确认

---

## §5 autoBuyer — 能力缺口自动购买

**文件**：`src/atp/autoBuyer.js`（~200+ 行）  
**定位**：Evolver 进化循环中的**自动采购层**——检测到能力缺口时，无需人工介入，直接下单。

### 5.1 核心设计

```javascript
// 能力缺口 → ATP 订单的自动闭环
async function considerOrder({ signals, question, capabilities, budget }) {
  if (!_isEnabled()) return { ok: false, skipped: true, reason: 'disabled' };
  
  // 1. 每日预算上限检查
  const daily = _readDailySpend();
  if (daily >= _config.dailyCap) return { ok: false, reason: 'daily_cap_exceeded' };
  
  // 2. 单笔上限
  if (budget > _config.perOrderCap) budget = _config.perOrderCap;
  
  // 3. 24h 问题级去重（防止重复下单）
  if (_isDuplicateQuestion(question)) return { ok: false, reason: 'duplicate_question' };
  
  // 4. 冷启动保护（前 5 分钟半额，防止 restart storm）
  if (_isColdStart()) budget = Math.floor(budget / 2);
  
  // 5. 3s 超时竞速（不阻塞主循环）
  return raceWithTimeout(placeOrder({ ... }), _config.timeoutMs);
}
```

### 5.2 预算保护机制

| 保护层 | 默认值 | 说明 |
|--------|--------|------|
| `DAILY_CAP` | 50 credits/天 | 每日总支出上限 |
| `PER_ORDER_CAP` | 10 credits/笔 | 单笔最高出价 |
| `COLD_START_WINDOW` | 5 分钟 | 重启后前 5 分钟半额 |
| `DEDUP_TTL` | 24 小时 | 问题级去重窗口 |

### 5.3 积分账本（Ledger）

```javascript
// atp-autobuyer-ledger.json 持久化
{
  "2026-05-03": { "spent": 23, "orders": ["oid1", "oid2", ...] },
  "2026-05-02": { "spent": 45, "orders": [...] }
}
```

每日 UTC 日期为 key，重启后可跨 session 累积。

### 5.4 与 Evolver 进化循环的集成

```
evolve.js 周期
  └─→ autoBuyer.start()          （幂等，顶部调用一次）
  └─→ considerOrder({capability_gap_signal, ...})
        ├─→ budget 检查
        ├─→ 去重检查
        └─→ placeOrder()          （ATP Hub）
              └─→ Merchant 接单 → sessions_spawn → atpTaskPickup
                    └─→ Gene/Capsule 交付 → submitDelivery
```

---

## §6 atpTaskPickup — ATP 任务桥接

**文件**：`src/atp/atpTaskPickup.js`

关键设计：**ATP 订单任务与本地 sessions_spawn 的桥接**：

```javascript
// ATP 任务附加 atp_order_id 字段，商家端可与 DeliveryProof 配对
// 非 ATP 任务则无此字段（区分来源）
function consumeAvailableWork() {
  const tasks = listMyTasks();
  return tasks.filter(t => t.atp_order_id != null);
}
```

这使得商家侧的 `sessions_spawn` 任务能够携带 ATP 上下文，完成后正确关联到订单。

---

## §7 serviceHelper — 服务发布

**文件**：`src/atp/serviceHelper.js`（99 行）

```javascript
publishService({
  title: 'Code Review',
  description: 'Professional code review',
  capabilities: ['code_review', 'security'],
  price_per_task: 10,         // 最低 1 credit
  execution_mode: 'exclusive', // exclusive | open | swarm
  max_concurrent: 3,
})
```

服务发布后，Hub marketplace 中的其他节点可以搜索并下单。

---

## §8 Adapters 系统

**文件**：`src/adapters/`（v1.70+ 新增）

### 8.1 hookAdapter.js — 统一 Hook 适配器

```javascript
const PLATFORMS = {
  cursor:      { name: 'Cursor',       configDir: '.cursor' },
  'claude-code': { name: 'Claude Code', configDir: '.claude' },
  codex:       { name: 'Codex',        configDir: '.codex' },
  kiro:        { name: 'Kiro',        configDir: '.kiro' },
};

function detectPlatform(cwd) { /* 双重检测：cwd + home */ }
function loadAdapter(platformId) { /* 加载对应 adapter */ }
function mergeJsonFile(filePath, patch) { /* 深度合并 JSON 配置 */ }
function copyHookScripts(destDir, evolverRoot) { /* 复制 session 生命周期脚本 */ }
```

**核心能力**：
1. **平台检测**：扫描 cwd 和 home 目录的 marker 文件（`.cursor`、`.claude`、`.codex`、`.kiro`）
2. **配置合并**：深度合并 evolver 钩子配置到平台原生配置（`settings.json`、`CLAUDE.md` 等）
3. **Hook 脚本注入**：复制 `evolver-session-start.js`、`evolver-signal-detect.js`、`evolver-session-end.js` 到平台配置目录

### 8.2 平台专用 Adapter

| 文件 | 平台 | 主要职责 |
|------|------|---------|
| `cursor.js` | Cursor | Cursor 项目级 session 管理 |
| `claudeCode.js` | Claude Code | Claude Code CLI 配置 + hooks |
| `codex.js` | Codex | Codex 配置合并 |
| `kiro.js` | Kiro | Kiro 配置合并 |

每个 adapter 负责：
- 平台特定配置路径解析
- 合并 `evolver` 管理的字段（带 `_evolver_managed` marker）
- 调用 `copyHookScripts()` 注入生命周期脚本

### 8.3 Hook 脚本

```
src/adapters/scripts/
  ├─ evolver-session-start.js   — 会话启动时调用（注入能力探测信号）
  ├─ evolver-signal-detect.js   — 实时信号检测（tool 级别）
  └─ evolver-session-end.js    — 会话结束时调用（汇总观察）
```

---

## §9 BlueCortexCE 借鉴分析

### 9.1 ATP Market → CE Capability Marketplace（长期愿景）

**当前差距**：CE 的能力发现完全依赖内部 Gene Pool + Hub 搜索，没有外部市场。

**ATP 启示**：
- ATP 将「能力缺口」转化为「市场订单」，使能力获取从内部研发扩展到外部采购
- CE 若引入类似机制，可以从更大的社区/Hub 获取观察注入能力
- `autoBuyer` 的「能力缺口自动发现 → 下单」模式是 CE 巡检系统的潜在扩展方向

**CE P2**：研究 Hub marketplace 是否支持类似 ATP 的能力订购协议。

### 9.2 autoBuyer Budget Guard → CE 资源保护（参考价值：中）

`autoBuyer` 的三重预算保护（daily cap / per-order cap / cold-start halving）对 CE 的资源控制有参考意义。

**CE 现状**：CE 暂无积分/cost-based 资源控制。  
**借鉴点**：若未来引入第三方能力（如付费 LLM API、Hub 检索），可参考 budget cap + ledger 模式。

**CE P3**：资源控制需求暂不迫切，作为可选架构备选。

### 9.3 hookAdapter → CE Multi-Platform Integration（参考价值：高）

Evolver 的 adapters 系统解决了「同一套 evolver 逻辑如何在 Cursor/Claude Code/Codex/Kiro 上运行」的问题。

**CE 现状**：CE 的 OpenClaw 集成已支持多 channel（Feishu、Discord 等），但 agent 层面的跨平台适配较薄弱。  
**借鉴点**：
- `detectPlatform` + `loadAdapter` 的平台发现模式 → CE 可为不同 AI 平台（Claude Code、Cursor、Codex）编写平台 adapter
- `mergeJsonFile` + `_evolver_managed` marker → CE 的配置合并机制可参考（避免覆盖用户配置）
- Hook 脚本分离（start/end/signal-detect）→ CE 的 session 生命周期管理可更清晰分层

**CE P1**：短期价值高。CE 已有 `openclaw-plugin`，可考虑为 Claude Code/Codex/Cursor 编写平台 adapter，实现统一的记忆 Hook 注入。

### 9.4 ATP Reputation + Proof → CE Capability Trust（P2）

ATP 的 `getMerchantTier()` 和 `submitDelivery(proof_payload)` 提供了商家信誉体系。

**CE 借鉴**：若引入外部能力源，可参考：
- `merchantTier` → `ObservationEntity.sourceReputation`
- `proof_payload` → `ObservationEntity.validationProof`

**CE P3**：中长期架构备选，非当前优先级。

### 9.5 ATP Proxy Routing → CE Gateway 统一出口（P1）

`hubClient` 的 proxy/direct 双路由机制与 CE 的 OpenClaw Gateway 设计高度一致：

```javascript
// Evolver
if (_isProxyMode() && getProxyUrl()) {
  return _proxyRequest('POST', proxyPath, body);
}
return _hubPost(hubPath, body);

// CE OpenClaw
// OpenClaw Gateway 作为统一出口，node_id 覆盖，防止身份伪装
```

**CE P1**：CE Gateway 已有类似设计（`gateway.remote.url`），可进一步强化「所有外部流量经 Gateway 统一出口」的约束。

---

## §10 架构关系图

```
┌─────────────────────────────────────────────────────┐
│                 Evolver Main Loop                   │
│              (evolve.js, v1.47.0 local)             │
└──────────────┬──────────────────────────────────────┘
               │ capability gap detected
               ▼
┌─────────────────────────────────────────────────────┐
│              autoBuyer (ATP Layer)                  │
│   budget guard → daily cap / per-order cap / dedup  │
└──────────────┬──────────────────────────────────────┘
               │ placeOrder()
               ▼
┌─────────────────────────────────────────────────────┐
│        hubClient.js (ATP API Client)                │
│   proxy mode: _proxyRequest() → Gateway             │
│   direct mode: _hubPost() → Hub direct             │
└──────────────┬─────────────────────────────────────┘
               │
               ▼
        ┌──────────────┐
        │     Hub      │ ← marketplace + ATP ledger
        └──────┬───────┘
               │
     ┌─────────┴─────────┐
     │                   │
     ▼                   ▼
┌──────────┐       ┌──────────────┐
│ Merchant │       │ ATP Task     │
│ sessions_│←──────│ Pickup       │
│ spawn   │       │ (atpTaskPickup)│
└────┬────┘       └──────────────┘
     │
     ▼
┌──────────────────┐
│ Gene/Capsule      │
│ submitDelivery()  │
└──────────────────┘
```

---

## §11 与现有文档的边界

| 文档 | 内容 |
|------|------|
| [`58`](./58-v166-new-architecture-three-layer-signals-atp-selfpr.md) | v1.66 新架构（signals + adapters 简要提及） |
| [`46`](./46-hub-ecosystem-integration-taskreview-issue.md) | Hub Ecosystem（已有 directoryClient / Hub 搜索） |
| [`68`](./68-post-solidify-pipeline-executiontrace-gitops-skillpublisher-questiongen-a2a.md) | Post-Solidify 管线（Gene/Capsule 输出） |
| **本文** | ATP 完整协议分析 + adapters 深度 + CE 借鉴 |

---

## §12 backlog 更新建议

- [ ] **featureFlags.js 不存在**：backlog 原始条目声称 `featureFlags.js`（114行 v1.78新增），源码核实为 `src/featureFlags.js` 在 origin/main **不存在**。该条目应标记为 `[x]` 并移除。

---

_changelog：2026-05-03 新增（`75`）：ATP 系统（hubClient 275行 / merchantAgent 118行 / consumerAgent 157行 / autoBuyer ~200行 / atpTaskPickup / serviceHelper 99行）完整源码分析 + adapters 系统（hookAdapter.js 207行 + 4个平台 adapter） + BlueCortexCE P1/P2/P3 借鉴路径_
