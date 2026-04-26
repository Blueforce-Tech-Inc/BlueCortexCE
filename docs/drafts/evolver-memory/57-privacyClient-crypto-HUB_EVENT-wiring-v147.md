# 57 — privacyClient.js + crypto.js + HUB_EVENT_SIGNALS 全链路接线分析

> **来源**：`EvoMap/evolver` v1.46–v1.47 (`src/gep/privacyClient.js`, `src/gep/crypto.js`, `src/evolve.js:1481–1555`)
> **最后更新**：2026-04-25

---

## 1. 架构总览：隐私计算管线

privacyClient.js + crypto.js 实现了 **TUI本地加密 → Hub密封执行 → 结果本地解密** 的隐私计算管线：

```
Client (Evolver)                              Hub (evomap.ai)
     │                                              │
     │  ① submitPrivacyTask(title, body, signals)   │
     │ ─────────────────────────────────────────────►│
     │◄──────────── { taskId, status }              │
     │                                              │
     │  ② key = generateKey()                        │
     │  ③ encrypted = encrypt(plaintext, key)        │
     │  ④ packed = pack(iv + authTag + ciphertext)  │
     │  ⑤ uploadEncryptedBlob(packed, taskId)       │
     │ ─────────────────────────────────────────────►│
     │◄──────────── { blobId }                      │
     │  (key is stored locally, never sent)          │
     │                                              │
     │  ⑥ executeSealedTool({ toolId, blobId })    │
     │ ─────────────────────────────────────────────►│
     │◄──────────── { resultKey, resultHash }       │
     │     (Hub executes tool on encrypted blob)    │
     │                                              │
     │  ⑦ result = getPrivacyResult(taskId)         │
     │ ◄───────────────────────────────────────────│
     │  ⑧ decrypted = decrypt(result, key)         │
     │     (locally, with integrity verification)   │
```

**设计原则**：
- **密钥不离本地**：AES-256-GCM key 在客户端生成，永远不发送到 Hub
- **传输层加密**：整个 blob 以 AES-256-GCM 加密后传输
- **完整性验证**：Hub 返回 `result_hash`，本地解密后验证

---

## 2. crypto.js：加密原语

**文件**：`src/gep/crypto.js` (67 lines)

### 2.1 算法参数

```javascript
const ALGORITHM = 'aes-256-gcm';  // 认证加密
const IV_BYTES  = 12;             // 96-bit IV (GCM 推荐)
const TAG_BYTES = 16;             // 128-bit auth tag
const KEY_BYTES = 32;             // 256-bit key
```

### 2.2 密钥生成

```javascript
function generateKey() {
  return crypto.randomBytes(KEY_BYTES);  // crypto.randomBytes, not Math.random
}
```

### 2.3 加密流程

```javascript
function encrypt(plaintext, key) {
  const iv = crypto.randomBytes(IV_BYTES);
  const cipher = crypto.createCipheriv(ALGORITHM, key, iv);
  const input = Buffer.isBuffer(plaintext) ? plaintext : Buffer.from(plaintext, 'utf8');
  const encrypted = Buffer.concat([cipher.update(input), cipher.final()]);
  const authTag = cipher.getAuthTag();
  return { ciphertext: encrypted, iv, authTag };
}
```

**关键点**：
- IV 每次加密随机生成（不能用固定 IV）
- `cipher.final()` 生成 authTag
- 输入可以是 `Buffer` 或 `string`

### 2.4 解密流程

```javascript
function decrypt(ciphertext, key, iv, authTag) {
  const decipher = crypto.createDecipheriv(ALGORITHM, key, iv);
  decipher.setAuthTag(authTag);  // 在解密前设置 authTag
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}
```

**关键点**：如果 authTag 验证失败（数据被篡改），`decipher.final()` 会 **throw**，不是静默失败。

### 2.5 打包格式（传输用）

```javascript
// 打包：Layout = [iv (12)] [authTag (16)] [ciphertext (...)]
function pack(parts) {
  return Buffer.concat([parts.iv, parts.authTag, parts.ciphertext]);
}

// 解包
function unpack(packed) {
  const iv = packed.subarray(0, 12);
  const authTag = packed.subarray(12, 28);
  const ciphertext = packed.subarray(28);
  return { ciphertext, iv, authTag };
}
```

---

## 3. privacyClient.js：Hub API 客户端

**文件**：`src/gep/privacyClient.js` (168 lines)

### 3.1 API 端点

```javascript
const HUB_URL = process.env.A2A_HUB_URL || process.env.EVOMAP_HUB_URL || 'https://evomap.ai';

function privacyUrl(path) {
  return `${HUB_URL}/a2a/privacy${path}`;
}
```

### 3.2 API 清单

| 函数 | HTTP | 端点 | 超时 | 用途 |
|------|------|------|------|------|
| `submitPrivacyTask(opts)` | POST | `/submit` | 15s | 提交隐私计算任务，获取 taskId |
| `uploadEncryptedBlob(plaintext, opts)` | POST | `/blob/upload` | 15s | 上传加密 blob，获取 blobId |
| `executeSealedTool(opts)` | POST | `/tool/execute` | 30s | 在 Hub 执行密封工具 |
| `getPrivacyStatus(taskId)` | GET | `/status/{taskId}` | 15s | 查询任务状态 |
| `getPrivacyResult(taskId, key)` | GET | `/result/{taskId}` | 15s | 获取加密结果并本地解密 |
| `getToolTemplates()` | GET | `/tool/templates` | 15s | 列出可用密封工具模板 |

### 3.3 关键设计：Blob 上传流程

```javascript
async function uploadEncryptedBlob(plaintext, opts) {
  const key = generateKey();           // 本地生成密钥
  const parts = encrypt(plaintext, key);  // AES-256-GCM 加密
  const packed = pack(parts);          // 打包 iv+tag+ciphertext

  const body = JSON.stringify({
    node_id: nodeId,
    privacy_task_id: opts.privacyTaskId,
    label: opts.label || 'blob',
    data_base64: packed.toString('base64'),  // base64 传输
    encryption: 'aes-256-gcm',
  });

  const res = await fetch(privacyUrl('/blob/upload'), { method: 'POST', body });
  const resp = await res.json();
  return {
    blobId: resp.blob_id || resp.blobId,  // 兼容两种字段名
    key,       // caller 必须自己存储！
    iv: parts.iv,
    authTag: parts.authTag,
  };
}
```

**⚠️ 关键警告**：返回的 `key`/`iv`/`authTag` **必须由调用方自己存储**，Hub 不存储密钥。如果丢失，结果将无法解密。

### 3.4 结果解密流程

```javascript
async function getPrivacyResult(taskId, key) {
  const res = await fetch(privacyUrl(`/result/${encodeURIComponent(taskId)}`));
  const data = await res.json();
  if (!data.encrypted_result_base64) return null;

  const packed = Buffer.from(data.encrypted_result_base64, 'base64');
  const parts = unpack(packed);
  const plaintext = decrypt(parts.ciphertext, key, parts.iv, parts.authTag);
  return { plaintext, resultHash: data.result_hash };
}
```

---

## 4. 与 taskReceiver.js 的接线

**文件**：`src/gep/taskReceiver.js:517–555`

### 4.1 PRIVACY_PARAMS 解析

taskReceiver.js 使用隐私计算管线的方式是：**从 task body 中解析 `[PRIVACY_PARAMS]` 块**，检测是否需要密封计算：

```javascript
// Task body 示例：
// [PRIVACY_PARAMS]
// tool_id: some_tool
// blob_ids: blob_abc, blob_def
// [/PRIVACY_PARAMS]
```

```javascript
function parsePrivacyParams(body) {
  const start = body.indexOf('[PRIVACY_PARAMS]');
  const end = body.indexOf('[/PRIVACY_PARAMS]');
  const block = body.substring(start + '[PRIVACY_PARAMS]'.length, end).trim();
  // 解析 key: value 行
  // 返回 { toolId, blobIds[] }
}
```

### 4.2 检测管线

```javascript
function detectPrivacyTask(task) {
  const body = task.body || task.description || '';
  return parsePrivacyParams(body);  // 来自 privacyClient.js
}

function taskToSignalsWithPrivacy(task) {
  const signals = taskToSignals(task);
  const pp = detectPrivacyTask(task);
  if (pp) {
    if (!signals.includes('privacy_computing')) signals.push('privacy_computing');
    if (!signals.includes('sealed_tool')) signals.push('sealed_tool');
  }
  return signals;
}
```

---

## 5. HUB_EVENT_SIGNALS 全表（evolve.js:1481–1543）

### 5.1 表结构

```javascript
const HUB_EVENT_SIGNALS = {
  [eventType]: [signal1, signal2, ...],
  ...
};
```

### 5.2 完整事件分类

#### 对话类
| 事件类型 | 信号 | 说明 |
|---------|------|------|
| `dialog_message` | `dialog`, `respond_required` | 需要响应 |

#### 议会 / 治理类
| 事件类型 | 信号 | 说明 |
|---------|------|------|
| `council_invite` | `council`, `governance`, `respond_required` | 议会邀请 |
| `council_second_request` | `council`, `governance`, `second_request`, `respond_required` | 附议请求 |
| `council_vote` | `council`, `vote`, `governance`, `respond_required` | 投票 |
| `council_community_vote` | `council`, `community_vote`, `governance`, `respond_required` | 社区投票 |
| `council_decision` | `council`, `decision`, `governance` | 议会决议 |
| `council_decision_notification` | `council`, `governance` | 决议通知 |

#### 审议 / 辩论类
| 事件类型 | 信号 | 说明 |
|---------|------|------|
| `deliberation_invite` | `deliberation`, `governance`, `respond_required` | 审议邀请 |
| `deliberation_challenge` | `deliberation`, `challenge`, `respond_required` | 辩论挑战 |
| `deliberation_next_round` | `deliberation`, `next_round`, `respond_required` | 下一轮 |
| `deliberation_completed` | `deliberation`, `governance` | 审议完成 |

#### 协作 / 会话类
| 事件类型 | 信号 | 说明 |
|---------|------|------|
| `collaboration_invite` | `collaboration`, `respond_required` | 协作邀请 |
| `session_message` | `collaboration`, `dialog`, `respond_required` | 会话消息 |
| `session_nudge` | `collaboration`, `idle_warning` | 空闲警告 |
| `task_board_update` | `collaboration`, `task_update` | 任务板更新 |

#### 任务 / 工作池类
| 事件类型 | 信号 | 说明 |
|---------|------|------|
| `task_available` | `task`, `work_available` | 有新任务 |
| `work_assigned` | `task`, `work_assigned` | 工作分配 |
| `swarm_subtask_available` | `swarm`, `task`, `work_available` | 蜂群子任务 |
| `swarm_aggregation_available` | `swarm`, `aggregation`, `work_available` | 蜂群聚合 |
| `diverge_task_assigned` | `swarm`, `task`, `work_assigned` | 分叉任务 |
| `pipeline_step_assigned` | `pipeline`, `task`, `work_assigned` | 管线步骤 |
| `organism_work` | `organism`, `task`, `work_assigned` | 有机体任务 |

#### 蜂群 PDRI 角色类
| 事件类型 | 信号 | 说明 |
|---------|------|------|
| `swarm_plan_available` | `swarm`, `planner`, `work_available` | 规划者可用 |
| `swarm_build_available` | `swarm`, `builder`, `work_available` | 构建者可用 |
| `swarm_review_available` | `swarm`, `reviewer`, `work_available`, `respond_required` | 评审者可用 |
| `swarm_aggregate_available` | `swarm`, `aggregator`, `work_available` | 聚合者可用 |
| `swarm_rework_required` | `swarm`, `rework`, `iterate` | 需要返工 |
| `subtask_failover` | `swarm`, `failover`, `urgent` | 子任务故障转移 |
| `team_formed` | `swarm`, `team`, `collaboration` | 团队形成 |
| `team_dissolved` | `swarm`, `team` | 团队解散 |

#### 隐私计算类
| 事件类型 | 信号 | 说明 |
|---------|------|------|
| `privacy_task_ready` | `privacy`, `sealed_tool`, `work_available` | 隐私任务就绪 |
| `privacy_result_available` | `privacy`, `result` | 隐私结果可用 |

#### 评审 / 赏金类
| 事件类型 | 信号 | 说明 |
|---------|------|------|
| `bounty_review_requested` | `review`, `bounty`, `respond_required` | 赏金评审请求 |
| `peer_review_request` | `review`, `swarm`, `respond_required` | 同伴评审请求 |
| `supplement_request` | `supplement`, `respond_required` | 补充请求 |

#### 成长 / 知识类
| 事件类型 | 信号 | 说明 |
|---------|------|------|
| `evolution_circle_formed` | `evolution_circle`, `collaboration` | 进化圈形成 |
| `knowledge_update` | `knowledge` | 知识更新 |
| `topic_notification` | `topic`, `knowledge` | 主题通知 |
| `reflection_prompt` | `reflection` | 反思提示 |

#### 系统类
| 事件类型 | 信号 | 说明 |
|---------|------|------|
| `task_overdue` | `overdue_task`, `urgent` | 任务逾期 |

**总计**：35+ 事件类型，全部映射到 1–4 个信号标签。

### 5.3 注入机制

```javascript
for (const ev of hubEvents) {
  const evSignals = HUB_EVENT_SIGNALS[ev.type] || ['hub_event'];
  for (const sig of evSignals) {
    if (!signals.includes(sig)) signals.unshift(sig);  // unshift = 高优先级
  }
  console.log('[HubEvents] Event: ' + ev.type + ' → signals: ' + evSignals.join(', '));
}

// 存储到全局，供下一轮 evolve 使用
if (!global._pendingHubEventContext) global._pendingHubEventContext = [];
global._pendingHubEventContext.push(...hubEvents);
```

**设计要点**：
- `unshift`（头部插入）而非 `push`（尾部追加）→ Hub 事件信号优先
- `if (!signals.includes(sig))` → 避免重复信号
- `global._pendingHubEventContext` → 跨轮次传递 Hub 事件上下文

---

## 6. CE 借鉴路径

### P0（立即可落地）
1. **AES-256-GCM 本地加密**：CE 的旁路写入模式天然支持本地加密 → 上传到 Hub 执行 → 结果解密返回
2. **HUB_EVENT_SIGNALS 映射**：CE 可将 Hub 事件映射为信号标签，直接注入 `ObservationEntity.source`
3. **PRIVACY_PARAMS 解析块**：CE 可设计类似 `[EXTRACTION_PARAMS]` 块，从用户提示中解析结构化指令

### P1（值得设计）
4. **密钥管理**：CE 需要设计本地密钥存储方案（文件/内存/HSM）
5. **隐私计算任务管线**：submit → upload → execute → poll → retrieve → decrypt 完整生命周期

### P2（架构探索）
6. **密封工具模板**：Hub `/tool/templates` API 列出可用密封工具，CE 可动态发现可用的隐私计算工具
7. **结果完整性验证**：利用 `result_hash` 做解密封完整性验证

---

## 附录：privacyClient.js 完整导出

```javascript
module.exports = {
  submitPrivacyTask,      // 提交任务
  uploadEncryptedBlob,   // 上传加密 blob（返回 key，调用方存储）
  executeSealedTool,     // 执行密封工具
  getPrivacyStatus,      // 查询状态
  getPrivacyResult,      // 获取并解密结果
  getToolTemplates,       // 列出工具模板
  parsePrivacyParams,    // 解析 task body 中的 [PRIVACY_PARAMS] 块
};
```
