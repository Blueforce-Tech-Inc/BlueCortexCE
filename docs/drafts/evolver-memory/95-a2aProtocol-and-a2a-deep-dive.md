# `95` a2aProtocol.js + a2a.js 深度分析

**模块**: `src/gep/a2aProtocol.js` (1221L) + `src/gep/a2a.js` (173L)
**定位**: Agent-to-Agent 通信协议核心层 + A2A 资产业务逻辑层
**版本**: v1.78.1 (Evolver 本地工作树 v1.47.0 `e72778e` 源码)

---

## 1. 架构分层总览

```
a2a.js (173L)                     a2aProtocol.js (1221L)
─────────────────────────────────────────────────────────────────
A2A 业务逻辑层                    A2A 协议传输层
• 广播资格判断                     • 消息构造（7 种类型）
• 置信度降权                      • 节点身份管理
• 爆炸半径安全检查                 • 双传输引擎（File / HTTP）
• 输入解析（JSON/JSONL/text）      • 心跳注册 + Hub 反馈
• Gene/Capsule 导出过滤            • Hub 基础设施（身份/信用/审计）
                                    • SSE 事件流 + 重连
                                    • 可插拔传输注册表
```

**职责边界清晰**：
- `a2a.js` 是**业务规则层**——判断"谁可以广播什么"、降权、外部资产解析
- `a2aProtocol.js` 是**协议传输层**——负责"消息怎么构造、怎么发送、谁来接收"

两层通过 `unwrapAssetFromMessage`（a2aProtocol）→ `parseA2AInput`（a2a）串联。

---

## 2. 节点身份系统

### 2.1 NodeId 生成（7 层 fallback）

```javascript
// 优先级：env > 持久化文件 > 设备指纹计算
// L23-63: 7 层 fallback
1. process.env.A2A_NODE_ID          // 显式设置
2. ~/.evomap/node_id                 // 全局持久化
3. ./.evomap_node_id                 // 本地持久化（项目级）
4. deviceId + agentName + cwd → SHA-256 → "node_" + 12hex  // 计算
```

```javascript
// L34-46: 设备指纹计算
const raw = deviceId + '|' + agentName + '|' + process.cwd();
const computed = 'node_' + crypto.createHash('sha256')
  .update(raw).digest('hex').slice(0, 12);
```

**存储**：`~/.evomap/node_id`（mode 0o600）+ `./.evomap_node_id`（备选）

### 2.2 Hub Node Secret（HMAC 签名密钥）

```javascript
// L443-462: Hub Node Secret 获取
// 优先级：env > 内存缓存(TTL) > 持久化文件 > env fallback token
1. process.env.A2A_NODE_SECRET
2. _cachedHubNodeSecret（TTL: SECRET_CACHE_TTL_MS）
3. ~/.evomap/node_secret（持久化，mode 0o600）
4. process.env.A2A_HUB_TOKEN
```

**用途**：所有发往 Hub 的 HTTP 请求，通过 `Authorization: Bearer <secret>` header 认证。

### 2.3 CE 借鉴：去中心化节点身份

BlueCortexCE 目前缺少节点身份层。Evolver 的方案值得借鉴：

| 方面 | Evolver | CE 现状 | CE 提案 |
|------|---------|---------|---------|
| 节点标识 | `node_<12hex>` | 无 | `deviceId` 已有，可派生 |
| 身份持久化 | 双文件 fallback | 无 | 复用 `deviceId.js` 机制 |
| Hub 认证 | HMAC Bearer token | 无 | MCP 工具认证机制缺失 |
| 身份绑定 | deviceId+agentName+cwd | 游离 | `runtime_env` 字段已有，可扩展 |

---

## 3. 消息类型系统

### 3.1 7 种消息类型

```javascript
// L19: VALID_MESSAGE_TYPES
['hello', 'publish', 'fetch', 'report', 'decision', 'revoke']
// 额外：'events_poll'（Hub 拉取待处理事件）
```

### 3.2 消息Envelope 结构

```javascript
// L93-101: buildMessage()
{
  protocol: 'gep-a2a',           // 协议名
  protocol_version: '1.0.0',      // 协议版本
  message_type: messageType,       // 7 种之一
  message_id: 'msg_<timestamp>_<4hex>',  // 幂等消息ID
  sender_id: senderId || getNodeId(),     // 发送方节点ID
  timestamp: new Date().toISOString(),    // ISO-8601
  payload: payload || {},         // 类型相关负载
}
```

### 3.3 核心消息构造器

#### `buildHello`（L119-132）
节点启动时向 Hub 自举：
```javascript
payload: {
  capabilities: {},              // Agent 能力声明
  gene_count: number,            // 本地 Gene 数量
  capsule_count: number,         // 本地 Capsule 数量
  env_fingerprint: captureEnvFingerprint(),  // 环境指纹
}
```
Hub 返回 `node_secret`（后续所有请求的认证令牌）。

#### `buildPublish`（L140-156）
发布单个资产（已逐步被 `buildPublishBundle` 替代）：
```javascript
// L146-149: HMAC 签名
const assetIdVal = asset.asset_id || computeAssetId(asset);
const signature = crypto.createHmac('sha256', nodeSecret)
  .update(assetIdVal).digest('hex');
```
**签名内容**：资产的 `asset_id`（内容寻址哈希），确保传输途中不可篡改。

#### `buildPublishBundle`（L158-203）⭐
**当前推荐方式**，Gene + Capsule 捆绑发布：
```javascript
// L178-180: 双资产联合签名
const signatureInput = [geneAssetId, capsuleAssetId].sort().join('|');
const signature = crypto.createHmac('sha256', nodeSecret)
  .update(signatureInput).digest('hex');
```
**关键设计**：
- Gene + Capsule 排序后联合签名，防止重排攻击
- 可选附加 `EvolutionEvent`（事件也会计算 `asset_id`）
- `chain_id` 支持跨链溯源

#### `buildFetch`（L205-226）
从 Hub 拉取资产，支持多种查询方式：
```javascript
payload: {
  asset_type: 'Gene' | 'Capsule' | 'EvolutionEvent' | null,  // 过滤类型
  local_id: string | null,    // 本地ID（精确匹配）
  content_hash: string | null,  // 内容哈希（内容寻址）
  signals: string[] | null,    // 信号匹配（语义搜索）
  search_only: boolean,        // 仅返回元数据（不下载payload）
  asset_ids: string[] | null,   // 批量指定 asset_id
}
```

#### `buildReport`（L228-236）
向 Hub 发送 ValidationReport：
```javascript
payload: {
  target_asset_id: string | null,
  target_local_id: string | null,
  validation_report: ValidationReport | null,  // 验证报告
}
```

#### `buildDecision`（L238-250）
Hub 对收到的资产做出决策：
```javascript
payload: {
  target_asset_id: string | null,
  target_local_id: string | null,
  decision: 'accept' | 'reject' | 'quarantine',
  reason: string | null,
}
```

#### `buildRevoke`（L252-262）
撤销已发布的资产：
```javascript
payload: {
  target_asset_id: string | null,
  target_local_id: string | null,
  reason: string | null,
}
```

### 3.4 CE 借鉴：消息契约设计

Evolver 的消息Envelope设计非常规范：

```javascript
{
  protocol,           // 版本化协议名 → CE: "claude-mem-a2a"
  protocol_version,   // 版本化协议版本 → CE: "1.0.0"
  message_type,       // 枚举化消息类型 → CE: "observation" / "context" / "feedback"
  message_id,         // 幂等ID → CE: "obs_<uuid>"
  sender_id,          // 发送方标识 → CE: deviceId
  timestamp,          // ISO-8601 时间戳
  payload,            // 类型安全负载
}
```

**CE 可借鉴**：在 MCP 工具层引入类似Envelope，MCP 工具名=`a2a_message`，payload 直接传递序列化消息。

---

## 4. 双传输引擎架构

### 4.1 传输注册表

```javascript
// L872-888: 可插拔传输
const transports = {
  file: { send, receive, list },
  http: { send, receive, list },
};

function getTransport(name) {
  const n = String(name || process.env.A2A_TRANSPORT || 'file').toLowerCase();
  const t = transports[n];
  if (!t) throw new Error('Unknown A2A transport: ' + n);
  return t;
}

function registerTransport(name, impl) {
  // 运行时注册新传输（MQTT/WebSocket/GRPC...）
  transports[name] = impl;
}
```

### 4.2 FileTransport（本地 JSONL）

```javascript
// L311-370: FileTransport 实现
fileTransportSend(message, opts):
  → dir/outbox/<message_type>.jsonl  // 按类型分文件
  → fs.appendFileSync（追加写）

fileTransportReceive(opts):
  → 扫描 dir/inbox/*.jsonl（最多50文件）
  → 小文件（≤256KB）：整读
  → 大文件（>256KB）：尾部读，避免 OOM
  → 逐行 JSON.parse，过滤 protocol === 'gep-a2a'
  → 返回消息数组

fileTransportList(opts):
  → 列出 dir/outbox/*.jsonl
```

**设计亮点**：
- 按消息类型分文件（`hello.jsonl`/`publish.jsonl`/`fetch.jsonl`...），避免单一文件膨胀
- 256KB 分界：尾部读取大文件，避免内存溢出
- JSONL 追加写天然 append-only，兼容原子性需求

### 4.3 HTTPTransport（Hub 通信）

```javascript
// L373-420: HTTPTransport 实现
httpTransportSend(message, opts):
  → POST <hubUrl>/a2a/<message_type>
  → Authorization: Bearer <node_secret>
  → Content-Type: application/json
  → AbortSignal.timeout(HTTP_TRANSPORT_TIMEOUT_MS)

httpTransportReceive(opts):
  → POST <hubUrl>/a2a/fetch
  → 构造 buildFetch({ assetType, signals })
  → 解析 response.payload.results[]

httpTransportList():
  → ['http']  // 占位符
```

### 4.4 CE 借鉴：传输抽象

BlueCortexCE 目前 MCP 工具直接调用内部服务，缺乏传输抽象：

| 方面 | Evolver | CE 现状 |
|------|---------|---------|
| 传输抽象 | File/HTTP 可切换 | 直接 HTTP 调用 |
| 消息持久化 | FileTransport (JSONL) | 无（内存为主） |
| 传输扩展性 | `registerTransport` 注册新协议 | 硬编码 |
| 离线支持 | FileTransport 本地缓存 | 无 |

**CE 提案**：引入 `Transport` 接口，MCP 工具通过传输层而非直接调用内部服务：
```java
public interface MessageTransport {
    SendResult send(A2AMessage message);
    List<A2AMessage> receive(ReceiveOptions opts);
}
```
实现：LocalJsonTransport（开发）、HttpTransport（生产）、GrpcTransport（未来）。

---

## 5. 心跳注册与反馈闭环

### 5.1 心跳启动序列

```javascript
// L813-827: startHeartbeat()
startHeartbeat(intervalMs):
  1. sendHelloToHub()          // 获取 node_secret
     → POST /a2a/hello
     → 解析 response.node_secret
     → 持久化到 ~/.evomap/node_secret
  2. 延迟 _scheduleNextHeartbeat(HEARTBEAT_FIRST_DELAY_MS)
     // 避免 hello 和第一个 heartbeat 太近触发 rate limit
```

### 5.2 心跳 Payload

```javascript
// L564-606: sendHeartbeat() 构建 body
bodyObj: {
  node_id: getNodeId(),
  sender_id: getNodeId(),
  version: '1.0.0',
  uptime_ms: Date.now() - _heartbeatStartedAt,
  timestamp: ISO-8601,
  meta: {
    // Worker 模式（空闲时接 Hub 任务）
    worker_enabled: true,
    worker_domains: [...],
    max_load: 5,
    // 模型层
    model_tier: '...',
    // 待更新承诺
    commitment_updates: [{ task_id, deadline, assignment }],
    // 环境指纹（仅首次发送）
    env_fingerprint: captureEnvFingerprint(),
  }
}
```

### 5.3 心跳响应：Hub → Agent 反馈

```javascript
// L627-684: 心跳响应解析
data 返回字段：
├── available_work: [...]         // Hub 分配的新任务
├── overdue_tasks: [...]           // Agent 承诺过期的任务（需报告）
├── skill_store: {...}            // Skill 市场提示
│   ├── eligible: boolean
│   ├── hint: string
│   └── published_skills: number
├── novelty: {...}                // 新颖性提示（探索方向）
├── capability_gaps: [...]         // Hub 识别的能力缺口
├── circle_experience: {...}      // Evolution Circle 参与邀请
└── has_pending_events: boolean   // 是否有待拉取的 Hub 事件
```

### 5.4 Rate Limit 处理

```javascript
// L630-645: rate_limited 响应处理
if (data.error === 'rate_limited') {
  const retryMs = data.retry_after_ms;
  const backoff = retryMs > 0 ? retryMs + 5000
                : (windowMs > 0 ? windowMs + 5000
                : _heartbeatIntervalMs);
  _scheduleNextHeartbeat(backoff);  // 退避后重试
}
```

### 5.5 CE 借鉴：心跳反馈机制

Evolver 通过心跳从 Hub 获取**工作提示**（`available_work`）、**能力缺口**（`capability_gaps`）、**新颖性提示**（`novelty_hint`）。这是一个 Pull + Push 混合的反馈机制。

BlueCortexCE 目前没有类似的 Hub 机制，但可以借鉴其**反馈信号传递模式**：

| Evolver 心跳反馈 | CE 对应机制 | 现状 |
|-----------------|-----------|------|
| `available_work` | 外部任务注入 | 无 |
| `capability_gaps` | `ModeService` 能力缺口检测 | 部分（启发式） |
| `novelty_hint` | `SearchService` 多样性排序 | 无 |
| `overdue_tasks` | 任务承诺超期警告 | 无 |

**CE 提案**：在 `ContextService` 中引入 `FeedbackSignal` 类型：
```java
public record FeedbackSignal(
    String type,           // "capability_gap" / "novelty" / "overdue_task"
    Map<String, Object> data,
    Instant timestamp
);
```

---

## 6. Hub 基础设施层

### 6.1 身份与证明

```javascript
// L1050-1070: DID 身份管理
hubSetDid(didDocument, didMethod='did:evomap')
  → POST /a2a/identity/did

hubGetIdentity(nodeId)
  → GET /a2a/identity/<nodeId>

hubGetAttestation(nodeId)
  → GET /a2a/identity/<nodeId>/attestation
  // 可验证的名誉证明

hubVerifyAttestation(attestation)
  → POST /a2a/identity/verify
```

### 6.2 信用系统

```javascript
// L973-1000: 信用充值
hubCreditTopUp(amount, opts)
  → POST /a2a/credit/topup
  → idempotency_key 防重复

// L1002-1022: 信用转账（Agent 间）
hubCreditTransfer(toNodeId, amount, opts)
  → POST /a2a/credit/transfer
  → from_node_id / to_node_id / amount / reason / reference_id / meta
```

### 6.3 审计与报告

```javascript
// L1040-1048: 审计日志
hubGetAuditLogs(nodeId, { action, since, until, limit, offset })
  → GET /a2a/audit/<nodeId>?action=...&since=...&until=...

// L1050: 工作报告
hubGetWorkReport(nodeId, { days=7 })
  → GET /a2a/audit/<nodeId>/report?days=N
```

### 6.4 CE 借鉴：信用与问责

Evolver 的信用系统为 Agent 经济模型奠定了基础。BlueCortexCE 目前没有信用概念：

| 方面 | Evolver | CE 借鉴可行性 |
|------|---------|-------------|
| 信用充值 | `hubCreditTopUp` | 低（CE 无 Hub） |
| Agent 间转账 | `hubCreditTransfer` | 低（无多 Agent 经济） |
| 审计日志 | `hubGetAuditLogs` | **高**（操作审计） |
| 身份证明 | `hubGetAttestation` | **高**（设备身份） |

**CE 高优先级行动项**：
- 引入 `ObservationEntity.auditTrail`（操作溯源）
- 复用 `deviceId` 机制，扩展为可验证身份（类似 Evolver 的 DID）

---

## 7. SSE 事件流

### 7.1 Hub → Agent 实时推送

```javascript
// L1110-1155: startEventStream()
hubOpenEventStream(opts):
  → GET /a2a/events/stream?node_id=...&duration_ms=600000
  → Authorization: Bearer <node_secret>
  → EventSource (Server-Sent Events)
  → 返回 { ok, eventSource, close() }

// 事件处理
eventSource.onmessage = (ev) → {
  parsed = JSON.parse(ev.data);
  _hubEvents.push(parsed.type);
}

eventSource.onerror = () → {
  stopEventStream();
  // 指数退避重连：5000ms → 120000ms 上限
  _sseReconnectTimer = setTimeout(startEventStream, _sseReconnectMs);
  _sseReconnectMs = Math.min(_sseReconnectMs * 2, 120000);
}
```

### 7.2 轮询降级

```javascript
// L729-767: _fetchHubEvents()
if (data.has_pending_events) {
  _fetchHubEvents()
    → POST /a2a/events/poll
    → 解析 response.events[]
    → 缓冲到 _latestHubEvents（最多200条，超出裁剪）
}
```

### 7.3 CE 借鉴：SSE 推送

BlueCortexCE 目前 WebUI 使用 SSE（`SSEBroadcaster`），但只有推送没有拉取。Evolver 的 SSE 方案值得参考：

| 方面 | Evolver | CE |
|------|---------|-----|
| 推送方向 | Hub → Agent | SSEBroadcaster → WebUI |
| 重连策略 | 指数退避（5s→120s） | 无（依赖前端） |
| 轮询降级 | events_poll fallback | 无 |
| 缓冲管理 | 200条有界裁剪 | 无 |

---

## 8. a2a.js 业务逻辑层

### 8.1 广播资格判断

```javascript
// L81-97: Capsule 广播资格（3 门禁）
isCapsuleBroadcastEligible(capsule, opts):
  1. score ≥ 0.7           // outcome.score
  2. blast_radius 安全      // A2A_MAX_FILES ≤ 5, A2A_MAX_LINES ≤ 200
  3. 连续成功 streak ≥ 2    // 从 EvolutionEvent 计算

// L99-110: Gene 广播资格（2 门禁）
isGeneBroadcastEligible(gene):
  1. gene.id 存在
  2. strategy.length > 0    // 有策略标签
  3. validation.length > 0 // 有验证记录
```

### 8.2 置信度降权（外部资产）

```javascript
// L52-72: lowerConfidence()
lowerConfidence(asset, { factor=0.6, source='external', received_at }):
  → confidence *= 0.6    // 外部资产降权 40%
  → a2a.status = 'external_candidate'
  → a2a.source = source    // 'external' / Hub / 其他节点
  → a2a.received_at = ISO
  → asset_id 未设置则计算
```

**设计思想**：外部资产（来自其他节点）天然不如本地验证过的资产可信，需要降权。这是防止声誉攻击（reputation attack）的关键机制。

### 8.3 输入解析（容错）

```javascript
// L138-165: parseA2AInput()
// 支持三种输入格式：
// 1. JSON 对象 → parse → unwrapAssetFromMessage
// 2. JSON 数组 → 逐项 unwrap
// 3. JSONL（多行）→ split → parse → unwrap → filter

// unwrapAssetFromMessage: 统一新旧格式
// 新格式：{ protocol: 'gep-a2a', message_type: 'publish', payload: { asset: {...} } }
// 旧格式：{ type: 'Gene', ... } → 直接返回
```

### 8.4 CE 借鉴：外部观察降权

BlueCortexCE 暂无 Agent 间观察共享机制，但未来多 Agent 协作场景下，外部来源的观察需要降权处理：

```java
// CE 提案：ExternalObservation降权
public record ExternalObservation(
    ObservationEntity original,
    double confidenceFactor,  // 外部来源默认 0.6
    String sourceAgent,
    Instant receivedAt
) {
    public double adjustedConfidence() {
        return original.getConfidence() * confidenceFactor;
    }
}
```

---

## 9. 完整调用链

```
Evolver 主循环 (evolve.js)
  │
  ├─ solidify.js 成功 → skillPublisher.js
  │    ├─ a2a.exportEligibleCapsules()  → 过滤资格
  │    ├─ a2a.exportEligibleGenes()      → 过滤资格
  │    └─ a2aProtocol.buildPublishBundle() → 构造消息
  │          └─ httpTransportSend()     → POST /a2a/publish
  │
  ├─ evolve.js 主循环心跳
  │    ├─ a2aProtocol.startHeartbeat()
  │    │    ├─ sendHelloToHub()         → 注册获取 node_secret
  │    │    └─ 定时 sendHeartbeat()
  │    │         ├─ POST /a2a/heartbeat
  │    │         ├─ 解析 available_work / capability_gaps / novelty
  │    │         └─ has_pending_events → _fetchHubEvents()
  │    └─ a2aProtocol.startEventStream()
  │         └─ SSE /a2a/events/stream  → 实时推送
  │
  └─ taskReceiver.js 拉取 Hub 任务
       └─ httpTransportReceive()       → POST /a2a/fetch
```

---

## 10. 关键设计原则

| 原则 | 体现 | CE 借鉴 |
|------|------|---------|
| **内容寻址** | `computeAssetId(asset)` → HMAC签名 | `contentHash` 已有，可扩展 |
| **传输解耦** | File/HTTP 可插拔 | MCP 工具可抽象传输 |
| **幂等消息ID** | `msg_<timestamp>_<4hex>` | 每条 MCP 消息需幂等ID |
| **身份分层** | NodeId（身份）+ Secret（认证）分离 | deviceId + HMAC 机制 |
| **降权信任** | 外部资产 0.6× | 多Agent场景外部观察降权 |
| **反馈闭环** | 心跳返回 capability_gaps | SearchService 多样性排序 |
| **SSE+轮询** | 实时推送+降级轮询 | WebUI SSE已有，需加轮询降级 |
| **Append-only** | FileTransport JSONL 追加写 | AuditLog 已有，可规范化 |

---

## 11. 与现有 Doc 的关系

- Doc 35 (§A2A Protocol)：A2A 协议在资产生命周期中的角色（broadcast eligibility 逻辑略）
- Doc 46（Hub Ecosystem）：Hub taskReceiver/hubReview/issueReporter 协作（不涉及 a2aProtocol.js 源码）
- Doc 63（Hub-Selector Feedback Loop）：`_latestCapabilityGaps` / `_latestNoveltyHint` 的消费者（不涉及 a2aProtocol.js 源码）
- Doc 88（taskReceiver）：`hubSearch.js` 两阶段搜索（不涉及 a2aProtocol.js 源码）

**本文与 Doc 35/46/63/88 的区别**：那些文档从**生态协作**角度描述 A2A；本文从**协议层源码**角度描述 a2aProtocol.js 的完整实现机制。

---

**CE 行动项优先级**：
- **P1**：SSE 重连 + 轮询降级（WebUI 已有 SSE，缺降级）
- **P1**：外部观察降权（`confidence × 0.6`）
- **P2**：传输抽象层（MCP 工具通过 Transport 而非直调）
- **P2**：设备身份可验证化（复用 deviceId 机制）
- **P3**：审计日志 API（`hubGetAuditLogs` 模式）

_Document Maintainer: PM Agent | 2026-05-05 | 基于 v1.47.0 `a2aProtocol.js` (L1–1221) + `a2a.js` (L1–173) 源码_
