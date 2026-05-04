# `78` v1.78 新增 Proxy 子系统架构深度分析

**数据来源**：`/Users/yangjiefeng/Documents/EvoMap/evolver` origin/main (v1.78.5) vs 本地 v1.47.0  
**最后更新**：2026-05-04（初稿）

---

## 目录

- [§1 概述](#s1-概述)
- [§2 架构总览](#s2-架构总览)
- [§3 MailboxStore](#s3-mailboxstore)
- [§4 SyncEngine](#s4-syncengine)
- [§5 LifecycleManager](#s5-lifecyclemanager)
- [§6 HTTP Server & Routes](#s6-http-server--routes)
- [§7 TaskMonitor](#s7-taskmonitor)
- [§8 Extensions](#s8-extensions)
- [§9 Platform Adapters](#s9-platform-adapters)
- [§10 安全设计](#s10-安全设计)
- [§11 整体架构定位](#s11-整体架构定位)
- [§12 BlueCortexCE 借鉴路径](#s12-bluecortexce-借鉴路径)

---

## §1 概述

v1.78 引入全新 **`src/proxy/`** 子系统（~1800 行 JS），作为 Evolver Agent 与 A2A Hub 之间的**持久化中间代理层**。

**解决的问题**：
- Agent 离线时消息无法到达（Hub 无法推送）
- 需要持久化邮箱队列保证 at-least-once 投递
- 支持多平台（Cursor/Claude Code/Codex/Kiro）统一的 Hook 适配
- P2P Session 协作（多 Agent 协同 subtask）

**与旧架构的区别**：
- 旧（v1.47）：Evolver 直接通过 HTTP 调用 Hub API，无本地持久化
- 新（v1.78）：Evolver → Proxy → Hub，Proxy 负责消息持久化、双向同步、生命周期管理

---

## §2 架构总览

```
┌─────────────────────────────────────────────────────────┐
│                      Evolver Agent                       │
│  (evolve.js / a2aProtocol / signals / memoryGraph)     │
└─────────────────────┬───────────────────────────────────┘
                      │  (internal)
┌─────────────────────▼───────────────────────────────────┐
│                    EvoMapProxy (index.js, 250L)          │
│  ┌─────────────┐  ┌────────────┐  ┌──────────────────┐ │
│  │ MailboxStore│  │SyncEngine  │  │LifecycleManager  │ │
│  │ (JSONL+Mem) │  │(in+out)    │  │(hello/hb/reauth) │ │
│  └──────┬──────┘  └─────┬──────┘  └──────────────────┘ │
│         │                │                               │
│  ┌──────▼────────────────▼──────────────────────────┐   │
│  │          ProxyHttpServer (181L)                   │   │
│  │  /mailbox/*  /asset/*  /task/*  /session/*       │   │
│  └───────────────────────────────────────────────────┘  │
│         ↕ HTTP (outbound)     ↕ HTTP (inbound polling)   │
│  ┌──────▼──────────────────────────────┐                 │
│  │  Extensions: SessionHandler(141L)  │                 │
│  │            DmHandler(45L)          │                 │
│  │            SkillUpdater(64L)       │                 │
│  └────────────────────────────────────┘                 │
└─────────────────────┬───────────────────────────────────┘
                      │  POST /a2a/mailbox/outbound
                      ▼
               ┌──────────────┐
               │   A2A Hub    │
               └──────────────┘
```

**主类 `EvoMapProxy`** (`src/proxy/index.js`, 250L)：
- 持有所有子组件引用
- `start()` 初始化所有组件
- `stop()` 优雅关闭

---

## §3 MailboxStore

**文件**：`src/proxy/mailbox/store.js` (415L)  
**核心职责**：JSONL 持久化 + 内存索引的消息队列

### 3.1 数据模型

```
messages.jsonl  (append-only)
state.json      (node_id, cursors, etc.)
```

每条消息（JSONL 行）：
```json
{
  "id": "uuid-v7",
  "type": "asset_submit | session_create | task_subscribe | ...",
  "payload": {...},
  "direction": "inbound | outbound",
  "status": "pending | acknowledged | sent | failed",
  "channel": "evomap-hub",
  "priority": "high | normal | low",
  "refId": "ref-to-other-msg-id",
  "expiresAt": "ISO8601 | null",
  "created_at": "ISO8601",
  "acknowledgedAt": "ISO8601 | null"
}
```

### 3.2 UUID v7 生成器

```javascript
// RFC 9562 compliant: 48-bit unix_ts_ms + 4-bit version + 12-bit rand_a + 2-bit variant + 62-bit rand_b
function generateUUIDv7() {
  const now = Date.now();
  const msHex = now.toString(16).padStart(12, '0');
  const bytes = crypto.randomBytes(10);
  bytes[0] = (bytes[0] & 0x0f) | 0x70; // version 7
  bytes[2] = (bytes[2] & 0x3f) | 0x80; // variant 10
  // ...
}
```

时间戳在 UUID 前 48 位，支持按时间排序。

### 3.3 安全设计：Prototype Pollution 防护

```javascript
function safeAssign(target, fields) {
  const keys = Object.keys(fields);
  for (const k of keys) {
    if (k === '__proto__' || k === 'constructor' || k === 'prototype')
      continue;  // ← 防止 JSONL 行污染 Object.prototype
    target[k] = fields[k];
  }
}
```

### 3.4 核心 API

| 方法 | 说明 |
|------|------|
| `send(...)` | 写一条 outbound 消息到 JSONL + 内存索引 |
| `poll({ channel, type, limit })` | 拉取待处理 inbound 消息 |
| `pollOutbound({ channel, limit })` | 拉取待发送 outbound 消息（用于 flush） |
| `ack(messageIds)` | 确认消息 |
| `writeInboundBatch(msgs)` | 批量写入 inbound |
| `getCursor(key) / setCursor(key, val)` | 分页游标 |
| `getState(key) / setState(key, val)` | 持久化状态（node_id, node_secret 等） |
| `list(...)` | 查询消息列表（支持 type/direction/status 过滤） |

---

## §4 SyncEngine

**文件**：`src/proxy/sync/engine.js` (120L) + `inbound.js` (99L) + `outbound.js` (97L)  
**核心职责**：Hub 与 Proxy 之间的双向同步

### 4.1 Outbound Sync

```javascript
class OutboundSync {
  async flush(channel = 'evomap-hub') {
    // 1. pollOutbound({ channel, limit: 50 })
    // 2. POST /a2a/mailbox/outbound { sender_id, messages[] }
    // 3. 根据 results 批量 ack
    // 4. 若有 inbound 消息（结果附带），写入 MailboxStore
  }
}
```

**特点**：
- 批量发送（MAX_BATCH = 50）
- 重试最多 10 次
- 30s 超时
- 403/401 → 触发 reauth

### 4.2 Inbound Sync

```javascript
class InboundSync {
  DEFAULT_POLL_INTERVAL_ACTIVE = 10_000ms;
  DEFAULT_POLL_INTERVAL_IDLE = 60_000ms;

  async pull(channel, limit = 50) {
    // 1. getCursor(channel:inbound_cursor) 获取游标
    // 2. POST /a2a/mailbox/inbound { sender_id, cursor, limit }
    // 3. writeInboundBatch(messages)
    // 4. 更新 cursor
  }
}
```

**特点**：
- 游标式分页（不会重复拉取）
- 活跃时 10s 轮询，空闲时 60s 轮询
- 35s 超时

### 4.3 SyncEngine 编排

```javascript
class SyncEngine {
  constructor({ store, hubUrl, getHeaders, logger, onAuthError, onInboundReceived }) {
    this.outbound = new OutboundSync(...);
    this.inbound = new InboundSync(...);
  }

  async start() {
    // active poll loop (10s) + idle fallback (60s)
    // onAuthError → re-authenticate
    // onInboundReceived → skillUpdater.pollAndApply()
  }
}
```

---

## §5 LifecycleManager

**文件**：`src/proxy/lifecycle/manager.js` (322L)  
**核心职责**：Hub 注册、心跳、环境指纹

### 5.1 Hello 握手

```javascript
async hello({ rotateSecret = false } = {}) {
  // POST /a2a/hello
  // payload: {
  //   node_id, node_secret (or null for new),
  //   capabilities: {},
  //   env_fingerprint: captureEnvFingerprint(),
  //   agent_version, proxy_protocol_version: '0.1.0',
  //   rotate_secret, platform, arch, node_version
  // }
}
```

### 5.2 心跳

```javascript
DEFAULT_HEARTBEAT_INTERVAL = 360_000ms (6min);
HELLO_TIMEOUT = 15s;
HEARTBEAT_TIMEOUT = 10s;
MAX_REAUTH_ATTEMPTS = 2;

// POST /a2a/hello 带上：
// { type: 'heartbeat', node_id, proxy_protocol_version,
//   task_meta: getTaskMeta(), uptime, last_hb_succeeded }
```

### 5.3 重认证

```javascript
// hello_rate_limit: 1 per minute
// reauth_backoff: exponential
// AuthError 分类: 403/401 → re-auth flow
```

### 5.4 环境指纹

```javascript
function captureEnvFingerprint() {
  // 来自 src/gep/envFingerprint.js
  // platform, arch, node_version, ...
}
```

---

## §6 HTTP Server & Routes

**文件**：`src/proxy/server/http.js` (181L) + `routes.js` (463L) + `settings.js` (64L)

### 6.1 路由总表

#### Mailbox 路由

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/mailbox/send` | 发送消息 |
| `POST` | `/mailbox/poll` | 拉取消息 |
| `POST` | `/mailbox/ack` | 确认消息 |
| `GET` | `/mailbox/list` | 列表查询 |
| `GET` | `/mailbox/status/:id` | 消息状态 |

#### Asset 路由

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/asset/validate` | 验证资产 |
| `POST` | `/asset/fetch` | 获取资产 |
| `POST` | `/asset/search` | 搜索资产 |
| `POST` | `/asset/submit` | 提交资产（→ Hub） |
| `GET` | `/asset/submissions` | 查询提交状态 |

#### Task 路由

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/task/subscribe` | 订阅任务 |
| `GET` | `/task/list` | 任务列表 |
| `GET` | `/task/status/:id` | 任务状态 |

#### Session 路由（Extension）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/session/create` | 创建会话 |
| `POST` | `/session/join` | 加入会话 |
| `POST` | `/session/leave` | 离开会话 |
| `POST` | `/session/message` | 发送消息 |
| `POST` | `/session/delegate` | 委托子任务 |

#### DM 路由（Extension）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/dm/send` | 发送 DM |
| `POST` | `/dm/poll` | 拉取 DM |
| `POST` | `/dm/ack` | 确认 DM |

### 6.2 请求验证

```javascript
// 每个 handler 都有 input validation
'POST /mailbox/send': async ({ body }) => {
  if (!body.type) throw Object.assign(new Error('type is required'), { statusCode: 400 });
  if (!body.payload) throw Object.assign(new Error('payload is required'), { statusCode: 400 });
  // ...
}
```

### 6.3 Asset Submissions 特殊处理

`/asset/submissions` 需要将 `asset_submit` 消息与 `asset_submit_result` inbound 消息 join：
```javascript
const submissionIds = new Set(submissions.map(s => s.id));
const resultMap = {};
for (const msg of store._messages) {
  if (msg.type === 'asset_submit_result' && msg.direction === 'inbound') {
    const refId = msg.payload?.ref_id;
    if (refId && submissionIds.has(refId)) resultMap[refId] = msg;
  }
}
const enriched = submissions.map(s => ({ ...s, result: resultMap[s.id] || null }));
```

---

## §7 TaskMonitor

**文件**：`src/proxy/task/monitor.js` (131L)

```javascript
class TaskMonitor {
  constructor({ store, logger }) {
    this.store = store;
    this.logger = logger;
  }

  subscribe(capability_filter) {
    // 写入 store.send({ type: 'task_subscribe', payload: { capability_filter } })
  }

  getHeartbeatMeta() {
    // 返回心跳元数据
    return { subscribed_tasks: [...], active_count: N };
  }
}
```

---

## §8 Extensions

### 8.1 SessionHandler (141L) - P2P Swarm Collaboration

**新增最高层特性**：使 Agent 能主动创建/加入/管理协作 Session，从"被动 Hub 编排"转向"Agent 主动发起的 Mesh 协作"。

```javascript
class SessionHandler {
  createSession({ title, description, inviteNodeIds, maxParticipants }) {
    // → store.send({ type: 'session_create', priority: 'high', ... })
  }

  joinSession({ sessionId }) { /* ... */ }
  leaveSession({ sessionId }) { /* ... */ }

  sendMessage({ sessionId, toNodeId, msgType, payload }) {
    // payload ≤ 16KB 校验
  }

  delegateSubtask({ sessionId, toNodeId, title, description, role }) {
    // subtask delegation
  }
}
```

**关键约束**：
- `max_participants`: 2-20，默认 5
- `invite_node_ids`: 最多 10 个
- payload ≤ 16KB

### 8.2 DmHandler (45L) - Direct Messages

```javascript
class DmHandler {
  send({ toNodeId, msgType, payload }) { /* ... */ }
  poll() { /* 拉取 DM */ }
  ack(messageIds) { /* 确认 DM */ }
}
```

### 8.3 SkillUpdater (64L) - Skill 自动更新

```javascript
class SkillUpdater {
  pollAndApply() {
    // 检查 Hub 上的 skill 更新，推送到本地 skills/
  }
}
```

---

## §9 Platform Adapters

**文件**：`src/adapters/hookAdapter.js` + `cursor.js` (89L) + `claudeCode.js` (163L) + `codex.js` (172L) + `kiro.js` (203L)

### 9.1 hookAdapter.js - 统一跨平台 Hook 注入

```javascript
const PLATFORMS = {
  cursor: { name: 'Claude Code', configDir: '.cursor', detector: '.cursor' },
  'claude-code': { name: 'Claude Code', configDir: '.claude', detector: '.claude' },
  codex: { name: 'Codex', configDir: '.codex', detector: '.codex' },
  kiro: { name: 'Kiro', configDir: '.kiro', detector: '.kiro' },
};

function detectPlatform(cwd) {
  // 检测优先级: cwd > home dir
  for (const [id, meta] of Object.entries(PLATFORMS)) {
    if (fs.existsSync(path.join(root, meta.detector))) return id;
  }
}

function mergeJsonFile(filePath, patch, { markerKey = '_evolver_managed' }) {
  // deepMerge(existing, patch) 并写入 .tmp 再 rename（原子写）
  // 保留 _evolver_managed 标记
}

function copyHookScripts(destDir, evolverRoot) {
  // 复制 evolver-session-start.js / evolver-signal-detect.js / evolver-session-end.js
}
```

### 9.2 平台特定适配器

每个平台适配器负责：
1. 读取平台 session 日志格式
2. 写入 Hook 脚本到平台配置目录
3. 处理平台特有的信号检测

```javascript
// claudeCode.js (163L) - Claude Code session log 格式
// codex.js (172L) - Codex 特有格式
// kiro.js (203L) - Kiro 平台
// cursor.js (89L) - Cursor 平台
```

---

## §10 安全设计

### 10.1 Prototype Pollution 防护

`MailboxStore.safeAssign()` 显式排除 `__proto__`、`constructor`、`prototype` 关键字，防止恶意 JSONL 行污染全局原型链。

### 10.2 原子文件写入

所有 JSON/JSONL 写入使用 `.tmp` + `rename()` 的原子模式：
```javascript
fs.writeFileSync(tmp, data);
fs.renameSync(tmp, filePath);
```

### 10.3 输入校验

每个路由 handler 对 body 字段做 required 检查并返回 400 + 明确错误信息。

### 10.4 Auth 分离

```javascript
_buildHeaders() {
  const headers = { 'Content-Type': 'application/json' };
  const secret = this.nodeSecret;
  if (secret) headers['Authorization'] = 'Bearer ' + secret;
  headers['x-correlation-id'] = crypto.randomUUID();
  return headers;
}
```

### 10.5 Payload Size 限制

`sessionHandler.sendMessage()` 限制 payload ≤ 16KB。

---

## §11 整体架构定位

### 11.1 v1.78 架构图

```
Evolver Agent
    │
    ├─ evolve.js (主循环)
    ├─ signals.js (三层提取)
    ├─ memoryGraph.js (记忆图)
    ├─ a2aProtocol.js (旧版 Hub 通信)
    │
    └─ EvoMapProxy (新增 v1.78)
         ├─ MailboxStore (持久化 JSONL)
         ├─ SyncEngine (Hub 双向同步)
         ├─ LifecycleManager (注册/心跳)
         ├─ ProxyHttpServer (REST API)
         ├─ SessionHandler (P2P 协作)
         └─ Adapters (多平台 Hook)

A2A Hub
    ├─ /a2a/mailbox/outbound  ← Proxy push
    ├─ /a2a/mailbox/inbound   ← Proxy poll
    ├─ /a2a/hello             ← Proxy 注册
    └─ Task Market / Asset Market
```

### 11.2 与旧模块的关系

| 旧模块 | 关系 |
|--------|------|
| `a2aProtocol.js` | 仍在 evolve.js 中使用，Proxy 提供备用通信路径 |
| `signals.js` | 不变 |
| `memoryGraph.js` | 不变 |
| `hubClient.js` (ATP) | Proxy 接管持久化和离线场景 |

---

## §12 BlueCortexCE 借鉴路径

### P0（立即可借鉴）

**1. JSONL + 内存索引的 Append-only 存储模式**

`MailboxStore` 的设计（append-only JSONL + Map 索引 + safeAssign）非常适合 BlueCortexCE 的旁路记忆系统：
- **借鉴点**：用 JSONL 作为 WAL（Write-Ahead Log），内存 Map 作为索引，备份/重放
- **场景**：`ObservationEntity` / `SummaryEntity` 的审计日志
- **安全防护**：prototype pollution 检查直接可迁移

**2. 分层 API 路由设计**

BlueCortexCE 可以参考 `/mailbox/*`、`/asset/*`、`/task/*` 的分层：
- 当前 Java API 全平铺在 Controller 层
- 引入统一前缀路由分组（如 `/api/v2/memory/*`）可提高可扩展性

### P1（值得参考）

**3. Cursor-based 分页**

`getCursor` / `setCursor` 游标分页模式比 offset 更适合高并发场景。BlueCortexCE 的 `SearchService` 当前使用 offset 分页，在大结果集上有性能问题。

**4. 批量确认 + 重试机制**

`OutboundSync.flush()` 的批量发送 + ACK + 重试（最多 10 次）模式可迁移到 BlueCortexCE 的 Webhook/Callback 场景。

**5. 原子文件写入（.tmp + rename）**

所有 store 写入使用 `.tmp` + `rename()`，防止文件损坏。这个模式 BlueCortexCE 目前缺失（直接 `Files.write`），可作为 P1 改进。

### P2（架构参考）

**6. Proxy 中间层思想**

BlueCortexCE 的 OpenClaw Plugin 扮演类似角色（Agent ↔ BlueCortexCE Memory 的中间层），可以参考 Proxy 的：
- 消息持久化（不丢失）
- 双向同步（Hub ↔ Agent）
- 生命周期管理（注册/心跳）

但 BlueCortexCE 目前是 Pull 模型（Agent 主动查询），Proxy 是 Push+Pull 混合模型。

**7. 多平台 Adapter 模式**

`hookAdapter.js` 的 `detectPlatform` + `loadAdapter` 动态加载模式适合 BlueCortexCE 支持多种 Agent（Claude Code/Codex/Cursor/OpenClaw）。

---

## 附录：关键文件行数统计

| 文件 | 行数 | 职责 |
|------|------|------|
| `src/proxy/index.js` | 250 | 主入口 |
| `src/proxy/mailbox/store.js` | 415 | 持久化邮箱 |
| `src/proxy/lifecycle/manager.js` | 322 | 生命周期 |
| `src/proxy/server/routes.js` | 463 | REST 路由 |
| `src/proxy/server/http.js` | 181 | HTTP 服务器 |
| `src/proxy/sync/engine.js` | 120 | 同步编排 |
| `src/proxy/sync/inbound.js` | 99 | 入站同步 |
| `src/proxy/sync/outbound.js` | 97 | 出站同步 |
| `src/proxy/task/monitor.js` | 131 | 任务监控 |
| `src/proxy/extensions/sessionHandler.js` | 141 | 会话协作 |
| `src/proxy/extensions/dmHandler.js` | 45 | 私信 |
| `src/proxy/extensions/skillUpdater.js` | 64 | Skill 更新 |
| `src/proxy/server/settings.js` | 64 | 配置管理 |
| **proxy 子系统合计** | **~2392** | |
| `src/adapters/hookAdapter.js` | ~250 | Hook 适配 |
| `src/adapters/claudeCode.js` | 163 | Claude Code 适配 |
| `src/adapters/codex.js` | 172 | Codex 适配 |
| `src/adapters/kiro.js` | 203 | Kiro 适配 |
| `src/adapters/cursor.js` | 89 | Cursor 适配 |
| **adapters 合计** | **~877** | |

**v1.47 → v1.78 Proxy + Adapters 新增：~3269 行**
