# 125 ATP Task Pickup + Execute + Heartbeat Handler + Multi-Platform Adapters 深度分析

> **⚠️ 数据来源**：Evolver `src/atp/` + `src/adapters/` staged files（v1.79.1 main 分支，本地未合并）。
> **关联 doc**：#75（ATP/Adapters 已知）、#91（ATP Heartbeat 已知）、#78（hookAdapter 已知）。
> **本文新发现**：staged 文件完整源码级分析，Ledger 去重协议，sessions_spawn 隔离契约，内容哈希幂等性，heartbeat 旁路交付。

---

## 1. 概览：ATP 新增文件地图

| 文件 | 行数 | 核心职责 |
|------|------|---------|
| `src/atp/atpExecute.js` | 285 | 端到端 ATP task 完成驱动（读答案→合成 Gene+Capsule→发布→绑定→交付） |
| `src/atp/atpTaskPickup.js` | 233 | Hub 任务轮询→sessions_spawn 渲染，Ledger 防重 |
| `src/atp/heartbeatSignalsHandler.js` | 254 | Heartbeat 旁路交付（无需 run() 循环） |
| `src/atp/hubClient.js` | 275 | Hub API 客户端，proxy/direct 双路由 |
| `src/adapters/hookAdapter.js` | 207 | 跨平台 Hook 安装/卸载统一入口 |
| `src/adapters/kiro.js` | 203 | Kiro 平台适配器（新增） |
| `src/adapters/claudeCode.js` | 163 | Claude Code 适配器（更新：tool_input.* 修复） |
| `src/adapters/codex.js` | 172 | Codex 适配器（新增） |
| `src/adapters/cursor.js` | 89 | Cursor 适配器（新增） |
| `src/adapters/scripts/evolver-*.js` | 437 | Hook 脚本（session-start/end, signal-detect） |

**新增子系统特征**：
- ATP 是**市场协议**：商家节点接单→完成→交付，Hub 验证→结算
- Adapters 是**跨平台抽象层**：统一 Hook 安装协议，屏蔽 Cursor/Claude Code/Codex/Kiro 差异

---

## 2. `atpTaskPickup.js`（233L）：Ledger + sessions_spawn 隔离契约

### 2.1 问题背景

当买家在 Hub 下 ATP 订单时，Hub 创建一个 `status=claimed` 的 Task 行，绑定到目标商家节点。该 Task **不会出现在** `/a2a/fetch`（只返回 `status=open` 的任务）。没有本模块，商家节点的 Evolver runtime 永远不会知道有工作要做。

### 2.2 核心设计

```
Hub Task (status=claimed, atp_order_id set)
    ↓ poll /a2a/task/my
atpTaskPickup.pickOne()
    ↓ _isEligible() 过滤
    ↓ _recentlySpawned() Ledger 检查
    ↓ _buildSpawnTask() 构建 prompt
    ↓ renderSessionsSpawnCall() → spawnCall string
    ↓ 写入 atp-pickup-ledger.json
    ↓ 返回 { spawnCall, task }
evolve.js main loop 打印到 stdout
    ↓ wrapper 捕获 sessions_spawn
Cursor/Claude Code sub-session 执行
    ↓ 写入 answer .md
    ↓ 运行 node index.js atp-complete
```

### 2.3 Ledger 防重协议

```js
// 存储路径
path.join(getMemoryDir(), 'atp-pickup-ledger.json')

// Ledger 结构
{ version: 1, spawned: { "<taskId>": { at: timestamp, order_id: "..." } } }

// 写入原子性：.tmp + rename
const tmp = _ledgerPath() + '.tmp';
fs.writeFileSync(tmp, JSON.stringify(ledger, null, 2));
fs.renameSync(tmp, _ledgerPath());

// 有界大小：超过 500 条裁剪到最新
if (entries.length > LEDGER_MAX_ENTRIES) {
  ledger.spawned = Object.fromEntries(entries.slice(-LEDGER_MAX_ENTRIES));
}

// 冷却期：SPAWN_COOLDOWN_MS = 5min（同任务不再 spawn）
function _recentlySpawned(ledger, taskId) {
  const ts = Number(entry.at) || 0;
  return Date.now() - ts < SPAWN_COOLDOWN_MS;
}
```

### 2.4 sessions_spawn 隔离契约（关键设计）

**本模块绝不直接打印 sessions_spawn 到 stdout**。只返回 `spawnCall` 字符串给调用方（evolve.js main loop）。

```js
// 导出接口
async function pickOne(opts) {
  // ...
  return { spawnCall, task: picked }; // caller prints to stdout
}

// sessions_spawn 渲染
const spawnCall = renderSessionsSpawnCall({
  task: spawnTask,
  agentId: 'atp_pickup',
  cleanup: 'delete',
  label: 'atp_pickup_' + String(picked.id).slice(0, 32),
});
```

**为什么重要**：Evolver main loop 与 wrapper 之间有"每周期一个 sessions_spawn"的契约。本模块不打破该契约，只是提供 spawn 字符串让 caller 决定何时打印。

### 2.5 内容截断保护

```js
const MAX_ANSWER_PROMPT_CHARS = 12000; // 防止 Hub task question 过大

function _clipQuestion(q) {
  if (s.length <= MAX_ANSWER_PROMPT_CHARS) return s;
  return s.slice(0, MAX_ANSWER_PROMPT_CHARS - 40) + '\n...[TRUNCATED]...';
}
```

### 2.6 任务遗忘机制（用于 channel 不可用时）

```js
// 当 spawn channel 不可用时，调用 forget() 清除 ledger 条目
// 避免浪费 5min 冷却期
function forget(taskId) {
  const ledger = _readLedger();
  if (ledger.spawned && ledger.spawned[taskId]) {
    delete ledger.spawned[taskId];
    _writeLedger(ledger);
  }
}
```

### 2.7 BlueCortexCE 借鉴

| 借鉴点 | 描述 | CE 优先级 |
|--------|------|---------|
| **Ledger 防重** | Cron 任务 + Ledger 去重，避免重复执行 | P1 |
| **原子写入 .tmp+rename** | 进程崩溃安全的 JSON 状态持久化 | P1 |
| **Bounded ledger** | 有界内存，防止 ledger 无限增长 | P2 |
| **Spawn 隔离契约** | 子任务通过 caller 打印，不直接操作 stdout | P3 |
| **任务遗忘机制** | 当 spawn channel 不可用时，撤销 ledger 条目 | P3 |

---

## 3. `atpExecute.js`（285L）：端到端 ATP 完成驱动

### 3.1 执行管道（4阶段）

```
1. READ_ANSWER      → 读 answer .md，MAX_ANSWER_CHARS=32000 截断
2. PUBLISH          → 合成 Gene+Capsule，HMAC-SHA256 签名，POST /a2a/publish
3. COMPLETE         → POST /a2a/task/complete（绑定 asset_id 到 Hub task）
4. DELIVER          → submitDelivery（提交 DeliveryProof）
```

### 3.2 Gene + Capsule 合成

```js
// Gene：能力描述元数据，content hash 幂等
function _buildGene(capabilities, signals) {
  const gene = {
    type: 'Gene', schema_version: '1.0',
    id: 'gene_atp_answer_' + caps.sort().join('_').slice(0, 40),
    signals_match: sig,
    category: 'innovate',
    strategy: [...],  validation: [...],
  };
  gene.asset_id = computeAssetId(gene); // 确定性内容哈希
  return gene;
}

// Capsule：答案包装，HMAC 签名，Hub 验证
function _buildCapsule({ gene, answer, ... }) {
  const capsule = {
    type: 'Capsule', schema_version: '1.0',
    id: 'capsule_atp_' + orderId...,
    gene: gene.id,
    content: answer,        // MAX_ANSWER_CHARS=32000 保护
    source_type: 'atp_task_executor',
    atp: { order_id, task_id, capabilities },
  };
  capsule.asset_id = computeAssetId(capsule);
  return capsule;
}
```

### 3.3 内容哈希幂等性（关键安全属性）

**Gene 和 Capsule 的 `asset_id` 是确定性内容哈希**（`computeAssetId()`）。这意味着：
- 相同内容 → 相同 asset_id
- Hub 端对同一 bundle 的重复 publish 是**幂等的**（409 不会造成副作用）
- 即使本模块 retry 失败后重试，publish 端不会产生重复资产

### 3.4 签名机制

```js
const nodeSecret = getHubNodeSecret();
const signatureInput = [gene.asset_id, capsule.asset_id].sort().join('|');
const signature = crypto.createHmac('sha256', nodeSecret).update(signatureInput).digest('hex');
// 签名覆盖 gene + capsule 两个 asset_id，防止中间人替换
```

### 3.5 错误恢复设计

```js
// 每个阶段返回 { ok, stage, error }
//-caller 可以按阶段重试，不重复上游副作用
if (!pub.ok) return { ok: false, stage: 'publish', error: pub.error };
if (!complete.ok) return { ok: false, stage: 'complete', error: complete.error };
if (!delivery.ok) return {
  ok: false, stage: 'deliver', error: delivery.error,
  assetId: capsule.asset_id, // 仍返回 asset_id，caller 可单独 retry deliver
};
```

### 3.6 BlueCortexCE 借鉴

| 借鉴点 | 描述 | CE 优先级 |
|--------|------|---------|
| **确定性内容哈希** | ObservationEntity 内容哈希 → 幂等去重 / 重复检测 | P1 |
| **HMAC 签名** | MCP/Hook 调用签名，防止中间人篡改 | P2 |
| **分阶段幂等重试** | 每阶段独立错误返回，可按阶段恢复而不重复副作用 | P2 |
| **内容大小保护** | 答案写入前截断，防止 payload 过大 | P1 |

---

## 4. `heartbeatSignalsHandler.js`（254L）：Heartbeat 旁路交付

### 4.1 问题背景

商家节点可能从不进入 `run()` 循环（只做心跳），但仍然正常心跳。Hub 在心跳响应中附加 `pending_atp_tasks` 和 `pending_deliveries`。本模块在心跳回调中直接响应，无需 `run()` 循环。

### 4.2 设计约束（Safety Posture）

```js
// submitDelivery 是纯 HTTP POST，最小化自生成 proofPayload
// 无 LLM 调用，无 spawn，安全在任何 worker context 调用

// 但 phase=execute（需要 LLM 子会话）的任务无法在心跳中完成
// 仅记录 need_work 计数，由人工/调度器触发 run() 循环处理
```

### 4.3 双源收集 + 去重

```js
// 收集两个可能的信号源
function _collectDeliverable(pendingDeliveries, pendingAtpTasks) {
  // pending_deliveries: phase=deliver 的行
  // pending_atp_tasks: phase=execute 但已有 result_asset_id 的行（兜底）
  // 按 order_id 去重，first-wins
}
```

### 4.4 共享 Ledger（autoDeliver + heartbeat）

本模块复用 `autoDeliver` 的 ledger 路径（`atp-autodeliver-ledger.json`）。这确保运行 `run()` 循环的节点和 heartbeat-only 子进程不会双重提交。

### 4.5 三态 Ledger 条目

```js
// submitted[order_id] = Date.now()      → 成功提交
// submitted[order_id] = -Date.now()     → Hub 返回终结错误（400/404/409），不再重试
// submitted[order_id] = undefined       → 未处理
const terminal = status === 400 || status === 404 || 409;
if (terminal) ledger.submitted[row.order_id] = -Date.now();
```

### 4.6 BlueCortexCE 借鉴

| 借鉴点 | 描述 | CE 优先级 |
|--------|------|---------|
| **Heartbeat 旁路机制** | 后台任务（重试/交付）可通过心跳触发，无需主循环 | P2 |
| **共享 Ledger 双消费者** | 同一 ledger 被 run() 循环和 heartbeat 子进程同时读写，通过时间戳去重 | P2 |
| **终结错误码** | 某些 HTTP 错误（400/404/409）标记为"不再重试"，防止无效循环 | P2 |

---

## 5. `hubClient.js`（275L）：Proxy/Direct 双路由 Hub API 客户端

### 5.1 双路由规则（Bug #460 修复）

```js
function _isProxyMode() {
  if (process.env.EVOMAP_PROXY === '1') return true;
  if (process.env.A2A_TRANSPORT === 'mailbox') return true;
  return false;
}

// proxy 模式：所有请求走本地 proxy（单一出口点）
// direct 模式：直连 Hub（保留兼容性）
```

**为什么需要 proxy**：proxy 会用自己的 `node_id` 覆盖 `sender_id`，所以调用方必须与 proxy 在同一节点（proxy 绑定 127.0.0.1）。

### 5.2 Hub API 端点映射

| 操作 | Proxy 路径 | Direct 路径 |
|------|-----------|------------|
| ATP 下单 | `/atp/order` | `/a2a/atp/order` |
| 任务列表 | `/atp/task/my` | `/a2a/atp/task/my` |
| 提交交付 | `/atp/deliver` | `/a2a/atp/deliver` |
| 心跳 | `/atp/hb` | `/a2a/atp/hb` |

### 5.3 BlueCortexCE 借鉴

| 借鉴点 | 描述 | CE 优先级 |
|--------|------|---------|
| **双传输路由** | 本地 proxy vs 直连 Hub，可配置切换 | P3 |
| **Node secret 引导** | `_ensureNodeSecret()` 在必要时调用 `sendHelloToHub()` 注册节点 | P3 |

---

## 6. `hookAdapter.js`（207L）：跨平台 Hook 统一管理

### 6.1 平台检测

```js
const PLATFORMS = {
  cursor:       { name: 'Cursor',       configDir: '.cursor' },
  'claude-code':{ name: 'Claude Code',  configDir: '.claude' },
  codex:        { name: 'Codex',       configDir: '.codex' },
  kiro:         { name: 'Kiro',        configDir: '.kiro' },
};

function detectPlatform(cwd) {
  // 优先检查 cwd，再检查 $HOME
  // 两层检测防止误判
}
```

### 6.2 Hook 脚本复制

```js
function copyHookScripts(destDir, evolverRoot) {
  // 将 evolver-session-start/end/signal-detect 复制到 hooks 目录
  // 设置 0o755 权限（Windows 容错）
}
```

### 6.3 JSON 配置合并（安全）

```js
function mergeJsonFile(filePath, patch, { markerKey = '_evolver_managed' } = {}) {
  // 1. 读取现有配置
  // 2. deepMerge（Object 递归合并，Array 替换而非追加）
  // 3. 添加 _evolver_managed 标记（可识别哪些是 evolver 管理的）
  // 4. .tmp + rename 原子写入
}
```

### 6.4 Hook 卸载（干净移除）

```js
function removeEvolverHooks(filePath, { markerKey = '_evolver_managed' } = {}) {
  // 通过 markerKey 识别 evolver 管理的配置
  // 从 hooks 数组中过滤掉 evolver 脚本
  // 清理 markerKey
}
```

### 6.5 BlueCortexCE 借鉴

| 借鉴点 | 描述 | CE 优先级 |
|--------|------|---------|
| **平台检测双层降级**（cwd → $HOME） | 避免只在 cwd 检测导致漏判 | P2 |
| **JSON 原子合并** | 安装时安全修改配置文件 | P2 |
| **markerKey 追踪** | 哪些配置是本系统管理的，可干净卸载 | P2 |

---

## 7. Kiro / Claude Code / Codex / Cursor 适配器对比

| 适配器 | 行数 | 差异点 |
|--------|------|--------|
| `kiro.js` | 203 | 完整新适配器，Kiro 平台 |
| `claudeCode.js` | 163 | 已更新：tool_input.* 读取（signal-detect） |
| `codex.js` | 172 | 新适配器，Codex 平台 |
| `cursor.js` | 89 | 新适配器，Cursor 平台（最简） |

**Claude Code v1.78.5 关键更新**（已在 backlog #79c 记录）：
```js
// signal-detect 现在优先读 tool_input.* 下嵌套参数
// 兼容 Claude Code 的 PostToolUse payload 格式变化
const ti = raw.tool_input || {};
const content = ti.content || ti.new_string || ti.file_content || ti.output;
```

---

## 8. 综合：ATP 子系统的记忆工程模式

### 8.1 Ledger 模式总结（适用 CE）

| 场景 | Evolver 实现 | CE 潜在应用 |
|------|------------|-----------|
| 跨进程防重 | `atp-pickup-ledger.json` | Cron 任务防重执行 |
| 跨进程状态共享 | `atp-autodeliver-ledger.json` | run() 循环 + heartbeat 子进程共享状态 |
| 终结错误记忆 | `submitted[order_id] = -Date.now()` | 永久记录失败任务，避免重试 |
| 有界裁剪 | `slice(-LEDGER_MAX_ENTRIES)` | ObservationEntity 审计日志 |
| 冷却期 | `SPAWN_COOLDOWN_MS = 5min` | API 调用防抖 |

### 8.2 内容哈希幂等性模式（适用 CE）

```
ObservationEntity 内容哈希 → 幂等去重（同一 prompt 不重复记录）
Capsule asset_id = computeAssetId(capsule) → 发布幂等
Gene asset_id = computeAssetId(gene) → 发布幂等
```

### 8.3 BlueCortexCE 行动项

| 优先级 | 模块 | 行动项 | 对应 Evolver 机制 |
|--------|------|--------|-----------------|
| **P1** | ObservationEntity | 内容哈希字段（SHA-256(content)）用于幂等去重 | atpExecute asset_id |
| **P1** | ContextService | Cron 任务 Ledger（JSON，.tmp+rename，bounded）防重 | atpTaskPickup ledger |
| **P1** | SessionEntity | 原子写入（.tmp+rename）防止崩溃丢状态 | 所有 evolver ledger |
| **P2** | AsyncTask | 分阶段错误返回，每阶段独立重试 | atpExecute stage errors |
| **P2** | AsyncTask | 终结错误码（400/404/409）永久记录 | heartbeatSignalsHandler |
| **P2** | Hook 安装 | markerKey 追踪 + 干净卸载机制 | hookAdapter |

---

## 9. 上游版本状态

- **本地 checkout**：v1.47.0（`e72778e`）
- **main 分支**：v1.79.1（`93e44a3`），领先约 374 commits
- **Staged 文件**：ATP + Adapters 共 22 个文件，3788 行新增
- **分析建议**：尽快合并 ATP/adapters 到本地 checkout（v1.79.1 已稳定）

---

*文档状态：✅ 初稿完成（2026-05-07 01:00）*
*下次建议*：分析 v1.79.1 相比 v1.47.0 的 Gene/Capsule 市场机制演进，以及 Hook 脚本（evolver-session-*.js）的完整行为。
