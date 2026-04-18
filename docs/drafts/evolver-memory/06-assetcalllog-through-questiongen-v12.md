<!-- part 6/8: auto-split from evolver-memory-analysis.md — see index.md -->

## 56. assetCallLog.js — 资产交互的 append-only 审计（v1.0 新增）

**文件**: `src/gep/assetCallLog.js` (130 lines)

### 56.1 设计原则

`assetCallLog.js` 实现** append-only JSONL** 审计日志，记录每次 Hub 资产交互：

```
{signal → hubSearch} → logAssetCall({ action: 'hub_search_hit', asset_id, score, mode })
{signal → hubSearch miss} → logAssetCall({ action: 'hub_search_miss', reason })
{asset 复用成功} → logAssetCall({ action: 'hub_review_submitted', rating })
```

### 56.2 记录格式

**文件**: `assetCallLog.js:25-50`

```javascript
function logAssetCall(entry) {
  const record = {
    timestamp: new Date().toISOString(),
    ...entry,  // run_id, action, asset_id, asset_type, score, mode, signals, reason, extra
  };
  fs.appendFileSync(logPath, JSON.stringify(record) + '\n', 'utf8');
}
```

**action 类型**：
| action | 说明 |
|--------|------|
| `hub_search_hit` | Hub 搜索命中 |
| `hub_search_miss` | Hub 搜索未命中 |
| `asset_reuse` | 资产被复用 |
| `asset_reference` | 资产被引用（reference 模式） |
| `hub_review_submitted` | 评价已提交 |
| `hub_review_rejected` | 评价被 Hub 拒绝 |
| `hub_review_failed` | 评价提交失败 |

### 56.3 非阻塞设计

**文件**: `assetCallLog.js:28-35`

```javascript
function logAssetCall(entry) {
  try {
    const logPath = getLogPath();
    ensureDir(logPath);
    fs.appendFileSync(logPath, JSON.stringify(record) + '\n', 'utf8');
  } catch (e) {
    // Non-fatal: never block evolution for logging failure
  }
}
```

**Evolver 为什么这样做**：appendFileSync 是原子的（对于单条记录），且被 try-catch 包裹，确保即使磁盘满/权限问题也不会阻塞主流程。

### 56.4 读取与聚合

**文件**: `assetCallLog.js:60-130`

```javascript
function readCallLog({ run_id, action, last, since }) {
  // 支持多维度过滤
  if (o.run_id) entries = entries.filter(e => e.run_id === o.run_id);
  if (o.action) entries = entries.filter(e => e.action === o.action);
  if (o.last && Number.isFinite(o.last)) entries = entries.slice(-o.last);
}

function summarizeCallLog(opts) {
  return {
    total_entries: entries.length,
    unique_assets: assetsSeen.size,
    unique_runs: runsSeen.size,
    by_action: actionCounts,  // { hub_search_hit: 5, hub_search_miss: 12, ... }
    entries,
  };
}
```

### 56.5 与 narrativeMemory.js 的关系

`assetCallLog` 和 `narrativeMemory` 都是 append-only 日志，但服务不同目的：

| 维度 | assetCallLog | narrativeMemory |
|------|-------------|----------------|
| 格式 | JSONL（机器可读） | Markdown（人类可读） |
| 内容 | Hub 资产交互记录 | 进化决策叙事 |
| 用途 | 审计、分析、CLI 统计 | 决策上下文保留 |
| 大小 | 按行增长，无限 | 30 条 / 12000 chars 上限 |

### 56.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| Append-only JSONL | 资产交互日志，不修改只追加 | **高优先级**: BlueCortexCE 的 API 调用日志可采用 append-only JSONL | 高 |
| 多维度过滤 | 支持 run_id/action/since/last 过滤 | **高优先级**: BlueCortexCE 的审计日志应有灵活查询能力 | 高 |
| 非阻塞写入 | try-catch + 静默失败 | **高优先级**: BlueCortexCE 的日志写入必须非阻塞主流程 | 高 |
| 聚合摘要 | by_action 计数 + unique 资产/运行数 | **中优先级**: BlueCortexCE 的日志系统应支持聚合统计 | 中 |
| 双重日志系统 | JSONL (机器) + Markdown (人类) | **中优先级**: BlueCortexCE 既有机器可读日志，也有可读的 summary | 中 |

---

## 57. directoryClient.js — 节点目录与能力发现（v1.0 新增）

**文件**: `src/gep/directoryClient.js` (110 lines)

### 57.1 定位：Hub 上的节点目录

`directoryClient.js` 是 Hub **Agent Directory** API 的客户端，提供：
1. **语义搜索**：`searchByQuery("machine learning")` → 返回相关节点
2. **信号搜索**：`searchBySignals(["ml", "nlp"])` → 按技能标签搜索
3. **节点画像**：`getAgentProfile(nodeId)` → 获取特定节点的详细信息
4. **任务匹配**：`discoverForTask(task)` → 为任务发现合适的节点

### 57.2 三种搜索接口

**文件**: `directoryClient.js:15-75`

```javascript
// 语义搜索：自然语言查询
async function searchByQuery(query, { limit }) {
  const url = `${HUB_URL}/a2a/directory/search?q=${encodeURIComponent(query)}`;
  const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
  return data.results || data;
}

// 信号搜索：关键词数组
async function searchBySignals(signals, { limit }) {
  const params = new URLSearchParams({ signals: signals.join(',') });
  const url = `${HUB_URL}/a2a/directory/search?${params}`;
  // ...
}

// 任务驱动发现：组合信号 + 标题
async function discoverForTask(task, opts) {
  if (task.title) return searchByQuery(task.title, opts);
  if (task.signals) return searchBySignals(task.signals.split(','), opts);
}
```

### 57.3 节点画像结构

**文件**: `directoryClient.js:60-75`

```javascript
async function getAgentProfile(nodeId) {
  // 返回：
  {
    nodeId: string,
    domains: string[],           // 领域：["ml", "nlp", "code"]
    modelType: string,           // 模型类型
    reputation: number,          // 声誉分
    completedTasks: number,       // 完成的任务数
    currentLoad: number,         // 当前负载（0-1）
    online: boolean               // 是否在线
  }
}
```

### 57.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 节点目录服务 | Hub 上的 Agent 发现机制 | **高优先级**: BlueCortexCE 的多实例部署需要"节点发现"能力 | 高 |
| 能力标签搜索 | signals 驱动节点匹配 | **中优先级**: BlueCortexCE 的 SDK 可按能力标签路由请求 | 中 |
| 节点画像 | reputation + load + completedTasks | **中优先级**: BlueCortexCE 的负载均衡可参考节点负载 | 中 |
| 语义搜索 | 自然语言发现节点 | **低优先级**: BlueCortexCE 的管理面板可支持语义搜索节点 | 低 |

---

## 58. deviceId.js — 稳定节点身份与优先级指纹链（v1.0 新增）

**文件**: `src/gep/deviceId.js` (209 lines)

### 58.1 设计背景

每个 Evolver 节点需要一个**稳定、唯一、持久化**的设备 ID，用于：
1. **Hub 通信身份**：在 A2A 协议中标识发送者
2. **环境分组**：`envFingerprintKey()` 将相似环境归组
3. **跨环境泛化（GDI）**：判断某个 Gene 在环境 A 成功是否能在环境 B 复制

### 58.2 优先级指纹链（Priority Chain）

**文件**: `deviceId.js:100-145`

```javascript
function getDeviceId() {
  // 1. Env var override（容器化环境推荐）
  if (process.env.EVOMAP_DEVICE_ID && DEVICE_ID_RE.test(envId)) {
    return _cachedDeviceId = envId;
  }
  // 2. 本地文件（上次运行已生成）
  const persisted = loadPersistedDeviceId();
  if (persisted) return _cachedDeviceId = persisted;
  // 3. 从硬件/容器元数据生成
  const generated = generateDeviceId();
  persistDeviceId(generated);  // 立即持久化
  return _cachedDeviceId = generated;
}
```

**Evolver 为什么这样做**：没有单一来源能覆盖所有场景（裸机/VM/容器/Serverless），因此用优先级链让每种环境都能获得稳定 ID。

### 58.3 生成策略

**文件**: `deviceId.js:45-90`

```javascript
function generateDeviceId() {
  // 优先级：machine-id > container-id > MAC > random
  const machineId = readMachineId();       // Linux: /etc/machine-id, macOS: IOPlatformUUID
  if (machineId) return sha256('evomap:' + machineId).slice(0, 32);

  const containerId = readContainerId();  // Docker: /proc/self/cgroup
  if (containerId) return sha256('evomap:container:' + containerId).slice(0, 32);

  const macs = getMacAddresses();         // 网络接口 MAC 地址
  if (macs.length > 0) return sha256('evomap:' + hostname + '|' + macs.join(',')).slice(0, 32);

  return crypto.randomBytes(16).toString('hex');  // 兜底随机
}
```

### 58.4 容器检测

**文件**: `deviceId.js:18-42`

```javascript
function isContainer() {
  if (fs.existsSync('/.dockerenv')) return true;
  const cgroup = fs.readFileSync('/proc/1/cgroup', 'utf8');
  if (/docker|kubepods|containerd|cri-o|lxc|ecs/i.test(cgroup)) return true;
  if (fs.existsSync('/run/.containerenv')) return true;
  return false;
}
```

**容器环境下的特殊处理**：
- `~/.evomap/device_id` 可能挂载为临时文件系统，重启丢失
- 同时尝试项目本地文件 `<project>/.evomap_device_id`
- 如果 auto-generated 且运行在容器中，打印警告建议设置 `EVOMAP_DEVICE_ID`

### 58.5 持久化策略

**文件**: `deviceId.js:95-110`

```javascript
function persistDeviceId(id) {
  // 优先 ~/.evomap/device_id
  try {
    fs.mkdirSync(DEVICE_ID_DIR, { recursive: true, mode: 0o700 });  // 仅所有者可读写
    fs.writeFileSync(DEVICE_ID_FILE, id, { encoding: 'utf8', mode: 0o600 });  // 仅所有者可读写
    return;
  } catch {}

  // 容器 volume 挂载路径（非临时文件系统）
  try {
    fs.writeFileSync(LOCAL_DEVICE_ID_FILE, id, { encoding: 'utf8', mode: 0o600 });
    return;
  } catch {}

  console.error('[evolver] WARN: failed to persist device_id... Set EVOMAP_DEVICE_ID env var.');
}
```

**安全设计**：`0o700` 目录权限 + `0o600` 文件权限，确保 device_id 不会被其他用户读取。

### 58.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 优先级指纹链 | machine-id → container-id → MAC → random | **高优先级**: BlueCortexCE 的节点应实现类似的稳定 ID 生成 | 高 |
| Env var 覆盖 | EVOMAP_DEVICE_ID 显式覆盖 | **高优先级**: BlueCortexCE 应支持环境变量显式设置节点 ID | 高 |
| 容器自适应 | 检测 /.dockerenv, cgroup, .containerenv | **高优先级**: BlueCortexCE 的 Docker 部署需要容器感知 | 高 |
| 持久化路径双备 | ~/.evomap + 项目本地 | **中优先级**: BlueCortexCE 应在容器和非容器环境都有持久化方案 | 中 |
| 权限安全 | 0o700 / 0o600 限制 device_id 文件访问 | **高优先级**: BlueCortexCE 的敏感标识文件应有权限保护 | 高 |
| 跨环境 GDI | 环境分组判断 Gene 能否跨环境复制 | **低优先级**: BlueCortexCE 的检索可考虑"环境相似度" | 低 |

---

## 60. a2aProtocol.js — Agent-to-Agent 联邦通信协议（v1.1 新增）

**文件**: `src/gep/a2aProtocol.js` (1221 lines)

### 60.1 核心设计原则

`a2aProtocol.js` 是 Evolver 的 **联邦网络层**——它定义了节点（Evolver 实例）如何与 Hub（中央协调服务）通信，交换基因（Gene）和胶囊（Capsule）资产。

**与 BlueCortexCE 的本质差异**：
- Evolver 有 Hub 作为中央协调者，支持**跨实例知识共享**
- BlueCortexCE 是纯旁路型，**没有 Hub 生态**
- 但 a2aProtocol.js 的**基础设施设计**（身份、签名、传输抽象、心跳）在任何多节点系统中都有参考价值

### 60.2 协议消息类型体系

**文件**: `a2aProtocol.js:1-15`

```javascript
const VALID_MESSAGE_TYPES = ['hello', 'publish', 'fetch', 'report', 'decision', 'revoke'];
```

| 消息类型 | 方向 | 用途 |
|----------|------|------|
| `hello` | 节点 → Hub | 注册节点身份，获取 node_secret |
| `publish` | 节点 → Hub | 发布资产（Gene/Capsule）到 Hub 市场 |
| `fetch` | 节点 → Hub | 按 ID/信号/内容哈希请求资产 |
| `report` | 节点 → Hub | 发送 ValidationReport |
| `decision` | Hub → 节点 | 接受/拒绝/隔离某个资产 |
| `revoke` | 节点 → Hub | 撤回已发布的资产 |

### 60.3 节点身份与 HMAC 签名

**文件**: `a2aProtocol.js:65-85` (`getNodeId`)

```javascript
function getNodeId() {
  // 1. 环境变量（容器化环境推荐）
  if (process.env.A2A_NODE_ID) return _cachedNodeId = envId;

  // 2. 本地持久化文件（~/.evomap/node_id）
  const persisted = _loadPersistedNodeId();
  if (persisted) return _cachedNodeId = persisted;

  // 3. 从 deviceId + agentName + cwd 计算
  const raw = deviceId + '|' + agentName + '|' + process.cwd();
  const computed = 'node_' + sha256(raw).slice(0, 12);
  _persistNodeId(computed);
  return _cachedNodeId = computed;
}
```

**Evolver 为什么这样做**：设备指纹（machine-id/container-id/MAC）提供了稳定的身份基础，`cwd` 哈希让同一设备不同目录的 Agent 有不同 ID（多租户隔离）。

**HMAC 签名发布**（`buildPublish`）：

```javascript
function buildPublish(opts) {
  const assetIdVal = asset.asset_id || computeAssetId(asset);
  const nodeSecret = getHubNodeSecret();
  const signature = crypto.createHmac('sha256', nodeSecret)
    .update(assetIdVal)
    .digest('hex');
  // 签名内容 = asset_id（不是完整 payload）
  // 这样 Hub 可以验证"发布者确实拥有这个 asset_id"
}
```

**Evolver 为什么这样做**：使用 `asset_id`（内容哈希）而非完整 payload 做签名，确保：
1. 签名长度固定（64 字符 hex）
2. Hub 可以验证"发送者知道 asset 内容"
3. 完整 payload 在网络上传输，但签名证明来源

### 60.4 Bundle 发布（Gene + Capsule 组合）

**文件**: `a2aProtocol.js:150-200` (`buildPublishBundle`)

```javascript
function buildPublishBundle(opts) {
  // 将 Gene + Capsule（+ 可选的 EvolutionEvent）打包发布
  const geneAssetId = computeAssetId(gene);
  const capsuleAssetId = computeAssetId(capsule);
  const signatureInput = [geneAssetId, capsuleAssetId].sort().join('|');
  const signature = crypto.createHmac('sha256', nodeSecret)
    .update(signatureInput)
    .digest('hex');
  // 签名内容 = 按字母序排列的两个 asset_id
}
```

**Evolver 为什么这样做**：Gene 和 Capsule 必须**同时发布**——单独发布没有意义（没有 Capsule 的 Gene 是未经验证的空洞模板）。

### 60.5 双传输层抽象

**文件**: `a2aProtocol.js:290-330` (`fileTransportSend/Receive`)

Evolver 实现了两套传输层，可插拔：

| 传输层 | 用途 | 特点 |
|--------|------|------|
| **FileTransport** | 本地/离线环境 | JSONL 文件到 `a2a/inbox/outbox` 目录 |
| **HTTPTransport** | 与 Hub 通信 | REST API 调用 |

```javascript
const transports = {
  file: { send: fileTransportSend, receive: fileTransportReceive, list: fileTransportList },
  http: { send: httpTransportSend, receive: httpTransportReceive, list: httpTransportList },
};

function getTransport(name) {
  const n = String(name || process.env.A2A_TRANSPORT || 'file').toLowerCase();
  return transports[n];
}
```

**Evolver 为什么这样做**：FileTransport 让 Evolver 在没有网络的环境下也能运行（通过文件交换消息）。HTTPTransport 是生产环境的默认选择。

### 60.6 FileTransport 的安全设计

**文件**: `a2aProtocol.js:290-340`

```javascript
function fileTransportReceive(opts) {
  const MAX_FILE_BYTES = 256 * 1024;  // 256KB 每文件上限
  // 大文件只读末尾 chunk（从文件尾部向前读）
  if (stat.size > MAX_FILE_BYTES) {
    const buf = Buffer.alloc(MAX_FILE_BYTES);
    fs.readSync(fd, buf, 0, MAX_FILE_BYTES, stat.size - MAX_FILE_BYTES);
    // 跳过不完整的行
    const firstNl = raw.indexOf('\n');
    if (firstNl >= 0) raw = raw.slice(firstNl + 1);
  }
}
```

**Evolver 为什么这样做**：防止恶意/损坏的 inbox 文件撑爆内存。256KB 上限 + 从尾部读取确保即使文件很大也能处理。

### 60.7 心跳与节点注册机制

**文件**: `a2aProtocol.js:440-530` (`sendHelloToHub`, `sendHeartbeat`)

```javascript
function sendHelloToHub() {
  // 首次注册，获取 node_secret
  const msg = buildHello({ nodeId, capabilities: {} });
  return fetch(endpoint, { method: 'POST', ... })
    .then(res => res.json())
    .then(data => {
      const secret = data.payload?.node_secret;
      if (secret) {
        _persistNodeSecret(secret);  // 持久化到 ~/.evomap/node_secret
      }
    });
}
```

**心跳循环**：

```javascript
function sendHeartbeat() {
  const bodyObj = {
    node_id: nodeId,
    uptime_ms: Date.now() - _heartbeatStartedAt,
    meta: {
      worker_enabled: process.env.WORKER_ENABLED === '1',
      worker_domains: [...],
      max_load: Number(process.env.WORKER_MAX_LOAD) || 5,
    }
  };
  // Hub 返回：available_work, overdue_tasks, capability_gaps, novelty_hint
}
```

**心跳的作用**：
1. **保持连接活跃**：Hub 知道节点还在线
2. **拉取任务**：Hub 返回 `available_work` 数组（Hub 上的 bounty tasks）
3. **能力匹配**：Hub 返回 `capability_gaps` 供本地 curriculum 使用
4. **速率限制反馈**：`rate_limited` 时调整心跳间隔

### 60.8 Node Secret 的三级缓存

**文件**: `a2aProtocol.js:380-410` (`getHubNodeSecret`)

```javascript
function getHubNodeSecret() {
  // 1. 环境变量优先
  if (process.env.A2A_NODE_SECRET) return envSecret;
  // 2. 内存缓存（TTL 内）
  const now = Date.now();
  if (_cachedHubNodeSecret && (now - _cachedHubNodeSecretAt) < SECRET_CACHE_TTL_MS)
    return _cachedHubNodeSecret;
  // 3. 本地持久化文件（~/.evomap/node_secret）
  const persisted = _loadPersistedNodeSecret();
  // 4. 环境变量 fallback（A2A_HUB_TOKEN）
  if (process.env.A2A_HUB_TOKEN) return process.env.A2A_HUB_TOKEN;
  return null;
}
```

**Evolver 为什么这样做**：Node secret 是 Hub 认证的核心凭证，三级缓存确保：
- 环境变量覆盖用于容器化/生产部署
- 内存缓存避免频繁文件读取
- 持久化确保重启后仍有效

### 60.9 SSE 事件流（Server-Sent Events）

**文件**: `a2aProtocol.js:850-920` (`hubOpenEventStream`)

```javascript
function hubOpenEventStream(opts) {
  const EventSource = require('eventsource');
  const es = new EventSource(endpoint, {
    headers: { 'Authorization': 'Bearer ' + secret }
  });
  // Hub 通过 SSE 推送：task_assignment, skill_review, circle_invite 等
  return { ok: true, eventSource: es, close: () => es.close() };
}
```

**自动重连机制**：

```javascript
// 指数退避：5s → 10s → 20s → ... → max 120s
_sseReconnectMs = Math.min(_sseReconnectMs * 2, _sseMaxReconnectMs);
```

### 60.10 Hub 基础设施 API

**文件**: `a2aProtocol.js:1000-1221`

Evolver 实现了完整的 Hub 自助基础设施客户端：

| API | 用途 |
|-----|------|
| `hubSelfProvision()` | 机器账户自注册 |
| `hubCreditTopUp()` | 积分充值 |
| `hubCreditTransfer()` | 积分转账（给其他节点） |
| `hubTransferEstimate()` | 转账手续费估算 |
| `hubTransferHistory()` | 转账历史查询 |
| `hubGetIdentity(nodeId)` | 获取任意节点的公开身份 |
| `hubGetAttestation(nodeId)` | 获取声誉证明 |
| `hubVerifyAttestation()` | 验证声誉证明 |
| `hubSetDid(didDocument)` | 设置 DID 文档 |
| `hubGetAuditLogs()` | 审计日志查询 |
| `hubGetWorkReport()` | 工作报告生成 |
| `hubOpenEventStream()` | SSE 实时事件流 |

**关键洞察**：Hub 不仅是个基因市场，还是一个**自包含的 Agent 经济系统**——有身份（Node ID）、货币（Credits）、声誉（Attestation）、审计（Audit Logs）。

### 60.11 限速与退避机制

**文件**: `a2aProtocol.js:490-510`

```javascript
if (data.error === 'rate_limited') {
  const retryMs = Number(data.retry_after_ms) || 0;
  const backoff = retryMs > 0 ? retryMs + 5000 : _heartbeatIntervalMs;
  console.warn('[Heartbeat] Rate limited. Next attempt in ' + Math.round(backoff/1000) + 's.');
  _scheduleNextHeartbeat(backoff);
}
```

**连续失败告警**：
- 3 次连续失败 → 警告"网络问题？"
- 10 次连续失败 → 警告"Hub 不可达"
- 每 50 次连续失败 → 周期性告警

### 60.12 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 节点身份持久化 | 三级缓存（env > memory > file） | **高优先级**: BlueCortexCE 的 client_id 应有类似持久化机制 | 高 |
| HMAC 签名 | asset_id 做签名内容 | **中优先级**: BlueCortexCE 的写操作可用 HMAC 验证来源 | 中 |
| 双传输层抽象 | FileTransport + HTTPTransport 可插拔 | **中优先级**: BlueCortexCE 的传输层可抽象化 | 中 |
| 心跳机制 | 保持连接 + 拉取任务 + 能力匹配 | **高优先级**: BlueCortexCE 的 SDK 应实现轻量心跳 | 高 |
| SSE 事件流 | 实时推送 Hub 事件 | **低优先级**: BlueCortexCE 目前无 Hub 生态 | 低 |
| 积分/经济系统 | Credits + Transfer + Audit | **低优先级**: BlueCortexCE 无 Hub，无经济系统 | 低 |
| 限速退避 | 指数退避 + 连续失败告警 | **高优先级**: BlueCortexCE 的外部 API 调用应有退避机制 | 高 |
| Node Secret 三级缓存 | env > memory > file | **高优先级**: BlueCortexCE 的 API key 应有类似缓存 | 高 |

---

## 61. prompt.js — GEP 提示词构建器（v1.2 新增）

**文件**: `src/gep/prompt.js` (712 lines) + `src/ops/innovation.js` (92 lines)

### 61.1 核心设计原则

prompt.js 是 Evolver 的**核心提示词引擎**，负责构建驱动 LLM 执行的完整 GEP Prompt。它包含：
- **Schema 定义**：5 个强制 JSON 对象的严格格式
- **上下文注入**：信号、基因预览、胶囊历史、反模式区、叙事记忆等
- **指令与约束**：进化哲学、伦理约束、安全规则、宪法级 Ethics
- **多模版组合**：GEP 主提示词 + Hub 复用提示词 + Hub 匹配块

### 61.2 五大强制对象模型（Schema Definitions）

**文件**: `prompt.js:155-215` (`SCHEMA_DEFINITIONS`)

Evolver 要求 LLM 输出**严格分离的 5 个 JSON 对象**：

```javascript
// 0. Mutation（突变触发）— 必须第一个
{
  "type": "Mutation",
  "id": "mut_<timestamp>",
  "category": "repair|optimize|innovate",
  "trigger_signals": ["<signal_string>"],
  "target": "<module_or_gene_id>",
  "expected_effect": "<outcome_description>",
  "risk_level": "low|medium|high",
  "rationale": "<why_this_change_is_necessary>"
}

// 1. PersonalityState（人格状态）
{
  "type": "PersonalityState",
  "rigor": 0.0-1.0,
  "creativity": 0.0-1.0,
  "verbosity": 0.0-1.0,
  "risk_tolerance": 0.0-1.0,
  "obedience": 0.0-1.0
}

// 2. EvolutionEvent（进化事件记录）
{
  "type": "EvolutionEvent",
  "schema_version": "1.5.0",
  "id": "evt_<timestamp>",
  "parent": <parent_evt_id|null>,
  "intent": "repair|optimize|innovate",
  "signals": ["<signal_string>"],
  "genes_used": ["<gene_id>"],
  "mutation_id": "<mut_id>",
  "personality_state": { ... },
  "blast_radius": { "files": N, "lines": N },
  "outcome": { "status": "success|failed", "score": 0.0-1.0 }
}

// 3. Gene（知识单元）
{
  "type": "Gene",
  "schema_version": "1.5.0",
  "id": "gene_<descriptive_name>",
  "summary": "<clear description>",
  "category": "repair|optimize|innovate",
  "signals_match": ["<pattern>"],
  "preconditions": ["<condition>"],
  "strategy": ["<step_1>", "<step_2>"],
  "constraints": { "max_files": N, "forbidden_paths": [] },
  "validation": ["<node_command>"]
}

// 4. Capsule（结果胶囊）
{
  "type": "Capsule",
  "schema_version": "1.5.0",
  "id": "capsule_<timestamp>",
  "trigger": ["<signal_string>"],
  "gene": "<gene_id>",
  "summary": "<one sentence summary>",
  "confidence": 0.0-1.0,
  "blast_radius": { "files": N, "lines": N }
}
```

**Evolver 为什么这样做**：
- **强制顺序**：`Mutation` 必须第一个，确保 LLM 先思考"要做什么"再输出其他对象
- **结构化约束**：避免 LLM 输出无结构的自然语言，便于后续解析
- **Schema 版本化**：每个对象带 `schema_version`，便于协议演进

### 61.3 上下文块注入顺序

**文件**: `prompt.js:480-560` (`buildGepPrompt`)

```javascript
// 注入顺序（从上到下）：
// 1. SCHEMA_DEFINITIONS（Schema 定义）
// 2. Directives & Logic（Intent + Selection + Strategy）
// 3. PHILOSOPHY（进化哲学）
// 4. CONSTRAINTS（约束规则）
// 5. CONSTITUTIONAL ETHICS（宪法级伦理）
// 6. SKILL OVERLAP PREVENTION（技能重叠防护）
// 7. SKILL CREATION QUALITY GATES（技能创建质量门）
// 8. CRITICAL SAFETY（系统崩溃防护）
// 9. COMMON FAILURE PATTERNS（常见失败模式）
// 10. FAILURE STREAK AWARENESS（失败序列感知）
// 11. Context [Signals]
// 12. Context [Env Fingerprint]
// 13. Context [Injection Hint]
// 14. Context [Gene Preview]
// 15. Context [Capsule Preview]
// 16. Context [Capability Candidates]
// 17. Context [Hub Matched Solution]
// 18. Context [External Candidates]
// 19. Context [Anti-Pattern Zone]
// 20. Context [Lessons from Ecosystem]
// 21. Context [Execution]（包含完整的执行上下文）
```

### 61.4 宪法级 Ethics（Constitutional Ethics）

**文件**: `prompt.js:350-390`

这是 Evolver 的**最高层级约束**，任何进化周期都不能违背：

```javascript
CONSTITUTIONAL ETHICS (EvoMap Ethics Committee -- Mandatory):
1. HUMAN WELFARE PRIORITY: Never create tools that could harm humans...
2. CARBON-SILICON SYMBIOSIS: Evolution must serve both human and agent interests...
3. TRANSPARENCY: All actions must be auditable. Never hide or conceal mutations...
4. FAIRNESS: Never create monopolistic strategies that block other agents...
5. SAFETY: Never bypass safety mechanisms, guardrails, validation checks...
```

** Evolvement 为什么这样做**：内置宪法级伦理约束，确保 LLM 在任何情况下都不会生成有害代码。

### 61.5 技能创建质量门（Skill Creation Quality Gates）

**文件**: `prompt.js:395-450`

当 `intent=innovate` 时，创建新技能必须通过严格的质量门：

```javascript
SKILL CREATION QUALITY GATES (MANDATORY for innovate intent):
1. STRUCTURE: skills/<name>/ 必须有 index.js + SKILL.md + package.json
2. SKILL NAMING: 描述性 kebab-case，禁止时间戳/随机数/工具名
3. SKILL.MD FRONTMATTER: 必须有 YAML frontmatter（name + description）
4. CONCISENESS: SKILL.md < 500 lines，详细内容放 references/
5. EXPORT VERIFICATION: node -e "require('./skills/<name>')" 必须成功
6. SENSITIVE DATA PARAMETERIZATION: 所有密钥/路径/密码必须参数化
7. TEST BEFORE SOLIDIFY: 创建后必须实际运行验证
8. ATOMIC CREATION: 一个 cycle 内完成所有文件
```

**Evolver 为什么这样做**：创新的代价是风险——质量门确保新技能不会成为技术债务。

### 61.6 停滞检测与创新催化剂

**文件**: `src/ops/innovation.js` + `prompt.js:290-330`

当检测到停滞信号时，注入创新催化剂：

```javascript
// 停滞信号
const stagnationSignals = [
  'evolution_stagnation_detected',
  'stable_success_plateau',
  'repair_loop_detected',
  'empty_cycle_loop_detected',
  'evolution_saturation'
];

// 创新催化剂生成逻辑（innovation.js）
function generateInnovationIdeas() {
  const categories = {
    'security': skills.filter(s => s.includes('security|audit|guard')).length,
    'media': skills.filter(s => s.includes('image|video|music|voice')).length,
    'dev': skills.filter(s => s.includes('git-|code-|lint|test')).length,
    'automation': skills.filter(s => s.includes('auto-|scheduler|cron')).length,
    'data': skills.filter(s => s.includes('db|store|cache|index')).length
  };
  
  // 找出最弱的 2 个类别
  const weakAreas = Object.entries(categories).sort((a, b) => a[1] - b[1]).slice(0, 2);
  
  // 针对弱项生成创新建议
  // security → dependency-scanner, permission-auditor
  // media → meme-generator, video-summarizer
  // dev → code-stats, todo-manager
  // automation → meeting-prep, broken-link-checker
  // data → local-vector-store, log-analyzer
}
```

### 61.7 上下文截断策略（Truncation Strategy）

**文件**: `prompt.js:125-135`

```javascript
function truncateContext(text, maxLength = 20000) {
  if (!text || text.length <= maxLength) return text || '';
  return text.slice(0, maxLength) + '\n...[TRUNCATED_EXECUTION_CONTEXT]...';
}

// 截断策略：
// 1. 优先截断 Execution Context（最长的块）
// 2. Signals 最多 50 个，每个最长 200 字符
// 3. Capabilities 预览：选了基因时限制到 500 chars
// 4. 截断后添加明确的 [TRUNCATED] 标记
```

### 61.8 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 五大强制对象模型 | Mutation → Personality → Event → Gene → Capsule | **高优先级**: BlueCortexCE 的 API 响应应支持结构化 schema | 高 |
| 宪法级 Ethics | 内置最高层级伦理约束 | **高优先级**: BlueCortexCE 的 Observation 应拒绝有害内容 | 高 |
| 技能创建质量门 | 8 重质量门确保新技能质量 | **高优先级**: BlueCortexCE 的任何"自动生成"内容都应有多重验证 | 高 |
| 停滞检测 + 创新催化剂 | 信号 → 类别弱点 → 创新建议 | **高优先级**: BlueCortexCE 应在检索效果停滞时推荐新方向 | 高 |
| 上下文截断策略 | 按块类型优先级截断 | **高优先级**: BlueCortexCE 的 context generate 应有智能截断 | 高 |
| Schema 版本化 | 每个对象带 schema_version | **中优先级**: BlueCortexCE 的 API 应有版本字段 | 中 |
| 失败序列感知 | 3+ 次相同基因 → 强制换 intent | **高优先级**: BlueCortexCE 应检测"重复失败模式"并警告 | 高 |

---

## 62. strategy.js — 进化策略预设系统（v1.2 新增）

**文件**: `src/gep/strategy.js` (127 lines)

### 62.1 策略预设体系

**文件**: `strategy.js:10-60`

Evolver 实现了 6 种策略预设，每种定义三个 intent 的目标分配比例：

```javascript
const STRATEGIES = {
  'balanced': {
    repair: 0.20,      // 20% 资源用于修复
    optimize: 0.30,   // 30% 用于优化
    innovate: 0.50,    // 50% 用于创新
    repairLoopThreshold: 0.50,  // 50% repair 触发强制创新
    label: 'Balanced',
  },
  'innovate': {
    repair: 0.05,
    optimize: 0.15,
    innovate: 0.80,    // 80% 用于创新
    repairLoopThreshold: 0.30,
    label: 'Innovation Focus',
  },
  'harden': {
    repair: 0.40,      // 40% 用于修复
    optimize: 0.40,    // 40% 用于优化
    innovate: 0.20,
    repairLoopThreshold: 0.70,
    label: 'Hardening',
  },
  'repair-only': {
    repair: 0.80,
    optimize: 0.20,
    innovate: 0.00,    // 禁止创新
    repairLoopThreshold: 1.00,
    label: 'Repair Only',
  },
  'early-stabilize': {
    repair: 0.60,
    optimize: 0.25,
    innovate: 0.15,
    repairLoopThreshold: 0.80,
    label: 'Early Stabilization',
  },
  'steady-state': {
    repair: 0.60,
    optimize: 0.30,
    innovate: 0.10,    // 最小创新
    repairLoopThreshold: 0.90,
    label: 'Steady State',
  },
};
```

### 62.2 自适应策略检测

**文件**: `strategy.js:60-100`

```javascript
function resolveStrategy(opts) {
  const signals = opts?.signals || [];
  const name = process.env.EVOLVE_STRATEGY || 'balanced';
  
  // 自动检测：未设置显式策略时应用启发式
  if (name === 'balanced' || name === 'auto') {
    const cycleCount = _readCycleCount();
    
    // 前 5 个 cycle → early-stabilize
    if (cycleCount > 0 && cycleCount <= 5) {
      name = 'early-stabilize';
    }
    
    // 饱和信号 → steady-state
    if (signals.includes('evolution_saturation') || signals.includes('force_steady_state')) {
      name = 'steady-state';
    }
  }
  
  return STRATEGIES[name] || STRATEGIES['balanced'];
}
```

**Evolver 为什么这样做**：
- **前 5 cycle** 优先修复，避免早期引入不稳定因素
- **饱和时** 切换到守成，减少创新风险
- **环境变量覆盖** 支持手动强制指定策略

### 62.3 repairLoopThreshold — 强制创新触发器

**文件**: `strategy.js:20-30`

```javascript
// 当 repair intent 在最近 8 个 cycle 中占比超过 repairLoopThreshold 时
// → 强制切换到 innovate intent
// 例如：balanced 的 repairLoopThreshold=0.50
// 如果最近 8 个 cycle 有 5 个是 repair → 下个 cycle 必须 innovate
```

**Evolver 为什么这样做**：防止进化陷入"修复循环"——反复修复而不探索新方向。

### 62.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 多策略预设 | 6 种策略覆盖不同场景 | **高优先级**: BlueCortexCE 的检索策略可类似预设（精确/语义/混合） | 高 |
| repairLoopThreshold | 防止陷入修复循环 | **高优先级**: BlueCortexCE 应防止"反复检索相同失败模式" | 高 |
| 自适应策略检测 | cycle count + 饱和信号 | **高优先级**: BlueCortexCE 的总结触发可参考"使用频率"动态调整 | 高 |
| 策略分配比例 | repair/optimize/innovate 显式比例 | **中优先级**: BlueCortexCE 的 Observation 分类可参考此比例 | 中 |

---

## 63. memoryGraphAdapter.js — 本地/远程双模适配器（v1.2 新增）

**文件**: `src/gep/memoryGraphAdapter.js` (195 lines)

### 63.1 设计背景：适配器模式

**文件**: `memoryGraphAdapter.js:1-30`

memoryGraphAdapter 是 **memoryGraph.js 的接口抽象层**：

```
┌─────────────────────────────────────────┐
│           Adapter Interface Contract    │
│  getAdvice()                           │
│  recordSignalSnapshot()                 │
│  recordHypothesis()                    │
│  recordAttempt()                        │
│  recordOutcome()                        │
│  recordExternalCandidate()               │
│  memoryGraphPath()                      │
│  computeSignalKey()                     │
│  tryReadMemoryGraphEvents()             │
└─────────────────────────────────────────┘
           ↓                    ↓
┌─────────────────────┐  ┌─────────────────────┐
│   Local Adapter     │  │   Remote Adapter    │
│ (memoryGraph.js)   │  │ (MEMORY_GRAPH_PROVIDER=remote) │
│   默认实现          │  │   SaaS KG 服务       │
└─────────────────────┘  └─────────────────────┘
```

### 63.2 本地适配器（默认）

**文件**: `memoryGraphAdapter.js:40-70`

```javascript
const localAdapter = {
  name: 'local',
  
  getAdvice(opts) {
    return localGraph.getMemoryAdvice(opts);
  },
  recordSignalSnapshot(opts) {
    return localGraph.recordSignalSnapshot(opts);
  },
  recordHypothesis(opts) {
    return localGraph.recordHypothesis(opts);
  },
  recordAttempt(opts) {
    return localGraph.recordAttempt(opts);
  },
  recordOutcome(opts) {
    return localGraph.recordOutcomeFromState(opts);
  },
  recordExternalCandidate(opts) {
    return localGraph.recordExternalCandidate(opts);
  },
  memoryGraphPath() {
    return localGraph.memoryGraphPath();
  },
  computeSignalKey(signals) {
    return localGraph.computeSignalKey(signals);
  },
  tryReadMemoryGraphEvents(limit) {
    return localGraph.tryReadMemoryGraphEvents(limit);
  },
};
```

### 63.3 远程适配器（带本地降级）

**文件**: `memoryGraphAdapter.js:80-170`

```javascript
function buildRemoteAdapter() {
  const remoteUrl = process.env.MEMORY_GRAPH_REMOTE_URL || '';
  const remoteKey = process.env.MEMORY_GRAPH_REMOTE_KEY || '';
  const timeoutMs = Number(process.env.MEMORY_GRAPH_REMOTE_TIMEOUT_MS) || 5000;

  // 远程调用 + 本地降级包装
  function withFallback(localFn, remoteFn) {
    return async function (...args) {
      try {
        return await remoteFn(...args);
      } catch (e) {
        // 任何远程失败 → 回退到本地
        return localFn(...args);
      }
    };
  }

  return {
    name: 'remote',
    
    // getAdvice: 优先远程（主要增强点）
    getAdvice: withFallback(
      (opts) => localGraph.getMemoryAdvice(opts),
      async (opts) => {
        const result = await remoteCall('/kg/advice', { ... });
        // 规范化远程响应以匹配本地契约
        return {
          currentSignalKey: result.currentSignalKey || ...,
          preferredGeneId: result.preferredGeneId || null,
          bannedGeneIds: new Set(result.bannedGeneIds || []),
          explanation: Array.isArray(result.explanation) ? result.explanation : [],
        };
      }
    ),

    // 写操作：先写本地，再异步同步到远程
    recordSignalSnapshot(opts) {
      const ev = localGraph.recordSignalSnapshot(opts);
      remoteCall('/kg/ingest', { kind: 'signal', event: ev }).catch(() => {});
      return ev;
    },
    // ... 其他写操作类似
  };
}
```

### 63.4 双模适配器的关键设计

**Evolver 为什么这样做**：

1. **本地优先写**：append-only 本地图谱是事实来源，远程只是缓存
2. **远程降级**：`withFallback` 确保任何远程失败都不阻塞进化
3. **异步同步**：写操作立即返回，远程同步在后台，不阻塞主流程
4. **响应规范化**：远程 KG 可能用不同的字段名，需要适配器转换
5. **Provider 解析**：环境变量 `MEMORY_GRAPH_PROVIDER=remote` 切换模式

### 63.5 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 适配器接口契约 | 9 个方法定义完整接口 | **高优先级**: BlueCortexCE 的存储层应有类似抽象 | 高 |
| 本地优先写 | 本地是来源，远程是缓存 | **高优先级**: BlueCortexCE 的多实例部署应本地优先 | 高 |
| 远程降级 | withFallback 确保任何失败都能回退 | **高优先级**: BlueCortexCE 的外部 API 调用应有降级策略 | 高 |
| 异步同步 | 写操作立即返回，后台同步 | **高优先级**: BlueCortexCE 的搜索缓存更新可异步化 | 高 |
| Provider 切换 | 环境变量切换 local/remote | **中优先级**: BlueCortexCE 的存储后端可支持插件化 | 中 |

---

## 64. innovation.js — 停滞检测与创新催化剂（v1.2 新增）

**文件**: `src/ops/innovation.js` (92 lines)

### 64.1 设计背景

innovation.js 是 Evolver 的**创新催化剂**，当检测到进化停滞（stagnation）时，从已有技能库中发现能力缺口并提出创新方向。

### 64.2 技能分类扫描

**文件**: `innovation.js:10-40`

```javascript
function listSkills() {
  const dir = getSkillsDir();
  return fs.readdirSync(dir).filter(f => !f.startsWith('.'));
}

function generateInnovationIdeas() {
  const skills = listSkills();
  const categories = {
    'feishu': skills.filter(s => s.startsWith('feishu-')).length,
    'dev': skills.filter(s => s.startsWith('git-') || s.includes('lint') || s.includes('test')).length,
    'media': skills.filter(s => s.includes('image') || s.includes('video') || s.includes('music')).length,
    'security': skills.filter(s => s.includes('security') || s.includes('audit')).length,
    'automation': skills.filter(s => s.includes('auto-') || s.includes('scheduler')).length,
    'data': skills.filter(s => s.includes('db') || s.includes('cache') || s.includes('index')).length
  };
  
  // 找出最弱的 2 个类别
  const weakAreas = Object.entries(categories).sort((a, b) => a[1] - b[1]).slice(0, 2);
  
  // 针对弱项生成创新建议
  if (weakAreas.includes('security')) {
    ideas.push("- Security: Implement a 'dependency-scanner' skill...");
  }
  // ...
}
```

### 64.3 创新想法生成策略

**文件**: `innovation.js:40-80`

1. **填补缺口**：针对最弱的类别提出具体技能建议
2. **优化现有**：当技能数 > 50 时，提示去重/合并
3. **元创新**：建议增强 Evolver 自身（如性能监控仪表板）

```javascript
// 想法生成规则
if (skills.length > 50) {
  ideas.push("- Optimization: Identify and deprecate unused skills...");
  ideas.push("- Optimization: Merge similar skills...");
}

ideas.push("- Meta: Enhance Evolver's self-reflection...");
return ideas.slice(0, 3);  // 最多 3 个想法
```

### 64.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 技能分类扫描 | 统计各能力类别的技能数量 | **高优先级**: BlueCortexCE 应能统计"哪些类型观察被记录最多" | 高 |
| 能力缺口发现 | 最少类别 → 创新方向 | **高优先级**: BlueCortexCE 应能发现"哪些类型的查询效果最差" | 高 |
| 元创新 | 建议增强系统自身 | **中优先级**: BlueCortexCE 的巡检可建议"如何改进 BlueCortexCE" | 中 |
| 技能去重提示 | 50+ 技能时提示合并/废弃 | **低优先级**: BlueCortexCE 无技能库概念 | 低 |

---

## 65. questionGenerator.js — 主动问题生成机制（v1.2 新增）

**文件**: `src/gep/questionGenerator.js` (267 lines)

### 65.1 设计背景

questionGenerator 从进化上下文中生成**主动问题**，发送到 Hub bounty 系统，实现多 Agent 协作解决问题。

### 65.2 问题生成策略（6 种场景）

**文件**: `questionGenerator.js:70-180`

```javascript
function generateQuestions(opts) {
  // Strategy 1: 反复错误（无法自动修复）
  if (signalSet.has('recurring_error')) {
    candidates.push({
      question: 'Recurring error: ' + errDetail + ' -- What approaches have worked?',
      signals: ['recurring_error', 'auto_repair_failed'],
      priority: 3,
    });
  }
  
  // Strategy 2: 能力缺口
  if (signalSet.has('capability_gap')) {
    candidates.push({
      question: 'Capability gap: ' + gapContext + ' -- How can this be addressed?',
      signals: ['capability_gap'],
      priority: 2,
    });
  }
  
  // Strategy 3: 停滞/饱和
  if (signalSet.has('evolution_saturation')) {
    candidates.push({
      question: 'Evolution saturated. What new directions?',
      signals: ['evolution_saturation', 'innovation_needed'],
      priority: 1,
    });
  }
  
  // Strategy 4: 连续失败（≥4 次）
  if (streakCount >= 4) {
    candidates.push({
      question: 'Failed ' + streakCount + ' consecutive cycles. Alternative strategies?',
      signals: ['failure_streak', 'external_help_needed'],
      priority: 3,
    });
  }
  
  // Strategy 5: 用户功能请求
  if (signalSet.has('user_feature_request')) {
    candidates.push({
      question: 'User feature request: ' + featureContext,
      signals: ['user_feature_request', 'community_solution_sought'],
      priority: 1,
    });
  }
  
  // Strategy 6: 性能瓶颈
  if (signalSet.has('perf_bottleneck')) {
    candidates.push({
      question: 'Performance bottleneck: ' + perfContext + ' -- Optimization strategies?',
      signals: ['perf_bottleneck', 'optimization_sought'],
      priority: 2,
    });
  }
}
```

### 65.3 去重与限流

**文件**: `questionGenerator.js:20-65`

```javascript
const MIN_INTERVAL_MS = 3 * 60 * 60 * 1000;  // 3 小时最小间隔
const MAX_QUESTIONS_PER_CYCLE = 2;

// 去重：完全相同 OR 70% 词集合重叠
function isDuplicate(question, recentQuestions) {
  var qWords = new Set(qLower.split(/\s+/).filter(w => w.length > 2));
  var pWords = new Set(prev.split(/\s+/).filter(w => w.length > 2));
  var overlap = [...qWords].filter(w => pWords.has(w)).length;
  if (overlap / Math.max(qWords.size, pWords.size) > 0.7) return true;
  return false;
}
```

### 65.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 6 种问题场景 | recurring_error / capability_gap / saturation / failure_streak / user_request / perf | **高优先级**: BlueCortexCE 的错误处理可参考这 6 种场景 | 高 |
| 问题优先级 | priority 1-3 区分紧急程度 | **中优先级**: BlueCortexCE 的问题上报可按紧急度分级 | 中 |
| 3 小时限流 | 防止频繁向 Hub 发问题 | **高优先级**: BlueCortexCE 的任何外部 API 调用都应有频率限制 | 高 |
| 70% 词重叠去重 | 模糊去重而非精确匹配 | **中优先级**: BlueCortexCE 的去重可采用类似模糊策略 | 中 |
| 问题发送给 Hub | Hub bounty 系统协作解决 | **低优先级**: BlueCortexCE 无 Hub 生态 | 低 |

---

