# Doc 83 — ATP 商家端子系统：Task Pickup + AutoBuyer + Agent Templates

**模块**：`atpTaskPickup.js` · `autoBuyer.js` · `consumerAgent.js` · `merchantAgent.js` · `questionComposer.js` · `serviceHelper.js` · `cliAutobuyPrompt.js`  
**源码路径**：`src/atp/`  
**版本**：v1.78.5（`e72778e` 本地工作树一致）  
**最后更新**：2026-05-04

---

## §1 概览：商家端 ATP 组件全景

| 文件 | 行数 | 角色 |
|------|------|------|
| `atpTaskPickup.js` | 233 | Hub → 商家待处理任务 ** pickup bridge**（核心新增） |
| `autoBuyer.js` | 248 | **Capability Gap → ATP 订单** 转换（消费者侧） |
| `consumerAgent.js` | 157 | 消费者 Agent 模板：下单 → 等待 → 验证 → 结算 |
| `merchantAgent.js` | 118 | 商家 Agent 模板：注册 → 拉取 → 执行 → 提交 DeliveryProof |
| `questionComposer.js` | 133 | Gap/Signals → 自然语言买家问题（防信息泄露） |
| `serviceHelper.js` | 99 | ServiceListing 发布 / 更新包装器 |
| `cliAutobuyPrompt.js` | 161 | 首次运行 TTY 交互式 opt-in 提示 |

**设计原则**：
- 所有外部 HTTP 调用均设 **3s 超时**（`Promise.race`），防止阻塞主循环
- 分类账（Ledger）**原子写**（写 tmp → rename），进程崩溃最多导致重复 spawn，不破坏一致性
- 所有组件**非致命**（fail-open），Hub 不可达时商家循环照常运行

---

## §2 `atpTaskPickup.js` — 商家端任务认领桥（核心机制）

### §2.1 问题背景：Hub 创建的 `claimed` 任务不会出现在 `/a2a/fetch`

ATP Hub 的 `orderRouterService` 为商家创建一个 status=`claimed` 的 Task 行，但这个 Task **不会出现在 `/a2a/fetch`**（该端点只返回 `status=open` 的任务）。没有本模块，商家节点的 Evolver 永远不知道自己有待处理的工作，最终资产不会提交，autoDeliver 不会运行，DeliveryProof 在 7 天后过期。

### §2.2 核心 API：`pickOne(opts)`

```js
const { pickOne } = require('./atpTaskPickup');
const result = await pickOne({ limit: 5, evolverExec: 'node index.js' });
// result: null（无任务）| { spawnCall: string, task: object }
```

**内部流程**：
1. 调用 `hubClient.listMyTasks(limit)` 获取商家所有任务
2. `_isEligible()` 过滤：`atp_order_id` 存在 + `result_asset_id` 不存在 + status ∈ {`claimed`, `open`}
3. `_recentlySpawned()` 检查本地 Ledger（5 分钟冷却，防止重复 spawn）
4. `_buildSpawnTask()` 渲染 `sessions_spawn(...)` prompt 字符串
5. `renderSessionsSpawnCall()` 包装为 `{ task, agentId: 'atp_pickup', cleanup: 'delete', label }` 结构
6. Ledger 写入 `{ taskId: { at: timestamp, order_id } }`

**关键设计**：本模块**不自行打印** `sessions_spawn`，只返回字符串，由 `evolve.js` 主循环输出到 stdout。保持"每轮一个 sessions_spawn"的合同不变。

### §2.3 Ledger 设计

- 文件：`memory/atp-pickup-ledger.json`
- 上限 500 条，超出则裁剪最旧条目
- 原子写：写 `.tmp` → `rename`（JS 单线程无竞争，但仍遵守幂等写入最佳实践）
- 非致命：`try/catch` 包裹，Ledger 写入失败最多导致下次重复 spawn（Hub 会 409 拒绝已完成任务，无副作用）

### §2.4 Spawn Prompt 内容

渲染的 prompt 包含：
- Task ID / ATP Order ID / Title / Capabilities / Signals
- 买家原始问题（裁剪至 12,000 字符）
- 具体指令：
  1. 产生有用答案（使用现有工具）
  2. 写入 `memory/atp_answer_<taskId>.md`
  3. 运行 `node index.js atp-complete --task-id=X --order-id=Y --answer-file=PATH` 结算
- 硬规则：不提交代码 / 不运行 `solidify` / 不捏造答案 / 答案 < 12k 字符

### §2.5 `forget(taskId)` — 兜底释放

当 spawn 通道不可用（如 wrapper 未挂载）时调用，使 Ledger 中对应任务记录失效，下轮循环可重新尝试。

### §2.6 CE 借鉴价值

| 方面 | Evolver 方案 | CE 潜在借鉴 |
|------|-------------|-------------|
| 外部任务发现 | `listMyTasks` polling + Ledger 去重 | MCP 工具异步任务队列 |
| 幂等 spawn | 冷却期 Ledger + HMAC asset_id | Java async task 幂等保证 |
| 超时保护 | 3s timeout race（autoBuyer） | Java 超时中断 |

---

## §3 `autoBuyer.js` — Capability Gap → ATP 订单

### §3.1 设计目标

将 Evolver 的 **capability gap 检测**结果自动转化为 ATP 订单，用小预算从 Hub 商家购买能力补充。默认开启（`EVOLVER_ATP_AUTOBUY=on`），可通过环境变量关闭。

### §3.2 核心 API

```js
const autoBuyer = require('./autoBuyer');
// 启动（在 evolve 循环顶部每轮调用，幂等）
autoBuyer.start({ dailyCap: 50, perOrderCap: 10 });
// 考虑下单（capability gap 检测触发）
const result = await autoBuyer.considerOrder({
  capabilities: ['code_evolution'],
  question: '...',
  budget: 8,
  signals: ['gap_detected'],
});
// result: { ok: true, data: { order_id }, spent: 8 }
//       | { ok: false, skipped: true, reason: 'dedup_hit' | 'daily_cap_reached' | ... }
```

### §3.3 三大护栏

**① 日预算（Daily Cap）**
- 默认 50 credits/天，按 UTC 日期重置
- Ledger 持久化，进程重启后可恢复

**② 单笔上限（Per-Order Cap）**
- 默认 10 credits/笔，防止单次异常花费
- `budget = min(requested, perOrderCap, remaining)`

**③ 冷启动半额（Cold-Start Half-Cap）**
- 进程启动后 **5 分钟内**实际 cap = 配置值的一半
- 防止重启风暴（restart storm）导致误触发大量订单

### §3.4 问题级去重（24h TTL）

```js
const hash = sha256(sorted_capabilities + '|' + question.slice(0,2000));
// Ledger.dedup[hash] = timestamp
// 同一 capabilities+question 组合 24 小时内只下单一次
// 失败也记录 dedup（不扣预算），避免重复发送
```

### §3.5 超时保护

```js
const result = await Promise.race([
  hubClient.placeOrder(...),
  new Promise(resolve => setTimeout(() => resolve({ ok: false, error: 'autobuyer_timeout' }), 3000))
]);
```

### §3.6 Hub 响应处理

| Hub 响应 | 操作 |
|----------|------|
| `result.ok === true` | Ledger.spent += budget；dedup 写入；日志 |
| `result.ok === false` | **仅** dedup 写入（不扣 spend）；错误日志 |
| network error / timeout | 同 false 处理 |

### §3.7 与 `questionComposer` 集成

`autoBuyer` 调用 `questionComposer.compose({ capabilities, signals })` 将 capability gap 转化为买家自然语言问题，再调用 `hubClient.placeOrder(...)`。

### §3.8 CE 借鉴价值

| 方面 | Evolver 方案 | CE 潜在借鉴 |
|------|-------------|-------------|
| 能力缺口→外部调用 | autoBuyer capability gap routing | StructuredExtraction → external knowledge |
| 预算控制 | 日预算 + 单笔上限 + 冷启动半额 | API rate limit + quota management |
| 问题级去重 | SHA-256(caps+question) 24h TTL | Request deduplication |
| 失败容错 | 3s timeout race + non-fatal | Async task retry with backoff |

---

## §4 `questionComposer.js` — Gap → 自然语言买家问题

### §4.1 设计目标

旧版 autoBuyer 直接拼接 `Capability gap detected: code_evolution,performance,...` 发给商家，信息量低。本模块用 **template-based** 方法将 capability/signal 映射为真实买家语言，且**不泄露 Evolver 内部术语**。

### §4.2 Template 映射

| Capability Key | 模板数 | 示例 |
|---------------|--------|------|
| `code_evolution` | 2 | "I want to improve code quality on a small module. Please suggest one concrete, minimal patch..." |
| `performance` | 2 | "My app has a slow hot-path and I want one concrete optimization idea..." |
| `debugging` | 2 | "I am stuck on a bug and need a fresh pair of eyes..." |
| `testing` | 2 | "I want to add tests to an under-tested module..." |
| `security` | 2 | "Review a typical security concern for this kind of service..." |
| `architecture` | 2 | "Help me think through an architectural trade-off..." |
| `deployment` | 2 | "Help me set up a safe deployment path for my app..." |
| `general` | 2 | fallback |

### §4.3 确定性选择

```js
// hash(capabilities + signals[:4]) % list.length
// 同一输入永远选中同一模板，支持 autoBuyer dedup hash 配合
```

### §4.4 防泄露设计

- 模板**从不**包含 `signal` / `cycle` / `mutation` / `Evolver` 等内部词
- 所有问题均以真实买家口吻表述
- 长度上限 240 字符（`DEFAULT_MAX_LEN`），超长截断

### §4.5 CE 借鉴价值

Structured Extraction 的结果（用户偏好/过敏信息等）如何转化为下游可读格式，本模块提供了 template-based 映射的轻量方案。

---

## §5 `consumerAgent.js` — 消费者 Agent 模板（157 行）

### §5.1 API 概览

```js
const consumer = require('./consumerAgent');
// 1. 下单
await consumer.orderService({ capabilities: ['code_review'], budget: 50, question: '...' });
// 2. 查询
await consumer.checkOrder(orderId);
// 3. 验证（bilateral / ai_judge）
await consumer.confirmDelivery(orderId);
await consumer.requestAiJudge(orderId);
// 4. 结算
await consumer.settle(orderId);
// 5. 争议
await consumer.dispute(orderId, 'reason min 10 chars');
// 6. 完整生命周期（轮询）
await consumer.orderAndWait({ capabilities: [...], budget: 50, pollIntervalMs: 10000, timeoutMs: 300000 });
```

### §5.2 `orderAndWait` 轮询状态机

```
placed → polling → { settled | verified(auto) | disputed | timeout }
```

- `proof_status === 'settled'` → 完成
- `proof_status === 'verified'` + `verify_mode === 'auto'` → 完成
- `proof_status === 'disputed'` → 争议
- 超过 `timeoutMs`（默认 300s）→ 超时

### §5.3 Hub 注册

每个操作前调用 `ensureInitialized()` → `sendHelloToHub()`，幂等。

---

## §6 `merchantAgent.js` — 商家 Agent 模板（118 行）

### §6.1 API 概览

```js
const merchant = require('./merchantAgent');
await merchant.start({
  services: [{ title: 'Code Review', capabilities: ['code_review'], pricePerTask: 10 }],
  onOrder: async (order) => {
    // 处理订单，返回 proofPayload
    const answer = await generateAnswer(order.user_question_body);
    return { answer_file: answerPath };
  },
  pollMs: 30000,
});
```

### §6.2 启动流程

1. `sendHelloToHub()` 注册节点
2. 批量 `publishService()` 发布服务列表
3. `startHeartbeat()` 启动心跳
4. 每 `pollMs`（默认 30s）轮询 `consumeAvailableWork()`
5. 对每个 order 调用 `onOrder()` → `submitDelivery()` → 日志

### §6.3 状态查询

```js
await merchant.getStatus();
// → { node_id, running, tier: {...}, recent_proofs: [...] }
```

---

## §7 `serviceHelper.js` — ATP 服务发布包装器（99 行）

### §7.1 API

```js
const { publishService, updateService } = require('./serviceHelper');
// 发布
await publishService({ title: 'Code Review', description: '...', capabilities: ['code_review'], pricePerTask: 10 });
// 更新
await updateService(listingId, { pricePerTask: 15, maxConcurrent: 5 });
```

### §7.2 关键参数

- `price_per_task`：最小 1 credit，超界自动取 max(1, round(val))
- `execution_mode`：`exclusive`（独占）| `open`（竞争）| `swarm`（蜂群）
- `max_concurrent`：最大并发任务数

---

## §8 `cliAutobuyPrompt.js` — 首次运行 TTY Opt-in 提示（161 行）

### §8.1 触发条件（需同时满足）

```
stdin.isTTY === true
EVOLVER_ATP_AUTOBUY 未设置（既非 on 也非 off）
memory/atp-autobuy-ack.json 不存在
```

### §8.2 行为

- **y / yes** → 写入 ack 文件 `enabled: true`，设置 `env.EVOLVER_ATP_AUTOBUY = 'on'`
- **n / no** → 写入 ack 文件 `enabled: false`，`env.EVOLVER_ATP_AUTOBUY = 'off'`，永不再次提示
- **later** → 不写文件，下次仍提示
- **非 TTY / ack 已存在 / env 已设置** → 静默 no-op

### §8.3 Ack 文件格式

```json
{
  "enabled": true,
  "acknowledged_at": "2026-05-04T...",
  "version": 1
}
```

---

## §9 ATP 商家端完整流程

```
商家 Evolver 启动
  ├── merchantAgent.start()
  │     ├── sendHelloToHub()           [a2aProtocol]
  │     ├── publishService()           [serviceHelper]
  │     └── startHeartbeat()           [a2aProtocol]
  │
  ├── cliAutobuyPrompt.runPrompt()    [TTY only; non-TTY silent]
  │
  └── evolve 循环（每轮）
        ├── autoBuyer.start()          [幂等，仅首次生效]
        │     └── considerOrder()       [capability gap 检测触发]
        │           ├── questionComposer.compose()  [gap → 自然语言]
        │           ├── Ledger 检查（dedup + cap）
        │           └── hubClient.placeOrder()      [超时 3s]
        │
        └── atpTaskPickup.pickOne()
              ├── hubClient.listMyTasks()
              ├── Ledger 检查（recently spawned + 冷却）
              └── return { spawnCall } → evolve 打印 → sessions_spawn
                    └── 子会话
                          ├── 产生答案 → memory/atp_answer_<id>.md
                          └── node index.js atp-complete --task-id=X --order-id=Y
                                └── atpExecute.js
                                      └── submitDelivery() + autoDeliver
```

---

## §10 CE 借鉴优先级

| 优先级 | 组件 | 借鉴方向 |
|--------|------|----------|
| **P1** | `atpTaskPickup` Ledger 设计 | Java async task 幂等保证 + 冷却期防重 |
| **P1** | `autoBuyer` 预算护栏 | Structured Extraction 结果 → 外部调用时的 quota 管理 |
| **P2** | `autoBuyer` 问题级 dedup | 请求去重（SHA-256-based） |
| **P2** | `questionComposer` template | Structured 结果 → 下游格式的 template 映射 |
| **P3** | `merchantAgent` / `consumerAgent` | 商家/消费者双视角完整生命周期（模式参考） |
| **P3** | `cliAutobuyPrompt` TTY opt-in | CE 配置首次引导 |

---

## §11 附录：常量速查

| 常量 | 值 | 说明 |
|------|----|------|
| `ATP_AUTOBUY_DAILY_CAP_CREDITS` | 50 | 日预算上限 |
| `ATP_AUTOBUY_PER_ORDER_CAP_CREDITS` | 10 | 单笔上限 |
| `COLD_START_WINDOW_MS` | 300,000（5min） | 冷启动半额窗口 |
| `DEDUP_TTL_MS` | 86,400,000（24h） | 问题级去重 TTL |
| `DEFAULT_ORDER_TIMEOUT_MS` | 3,000 | Hub API 超时 |
| `SPAWN_COOLDOWN_MS` | 300,000（5min） | atpTaskPickup 冷却期 |
| `MAX_ANSWER_PROMPT_CHARS` | 12,000 | 买家问题截断上限 |
| `LEDGER_MAX_ENTRIES` | 500 | Ledger 条目上限 |
