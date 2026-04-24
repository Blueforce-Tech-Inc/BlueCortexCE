# 43. Privacy Computing + Hub Ecosystem：隐私计算、问答生成、自动报告

**分析目标**：理解 Evolver Hub 生态的四大周边模块——隐私计算（`privacyClient`）、人格对话（`commentary`）、问题生成（`questionGenerator`）、自动 GitHub Issue 报告（`issueReporter`）——为 BlueCortexCE 提炼可借鉴的设计。

**数据来源**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/privacyClient.js`、`src/gep/paths.js`（crypto段）、`src/ops/commentary.js`、`src/gep/questionGenerator.js`、`src/gep/issueReporter.js`

**最后更新**：2026-04-24（v43 新增：**隐私计算完整管线**（AES-256-GCM + 密封工具 + 本地密钥管理）/ **commentary 三人格生成器** / **questionGenerator 六策略 + 模糊去重 + Hub bounty** / **issueReporter 自动报告 + 冷却去重 + 脱敏**）

---

## §1 隐私计算架构（`privacyClient` + `crypto`）

### 1.1 设计背景与威胁模型

Evolver 的隐私计算模型解决的是**"数据不能以明文形式离开客户端"**的问题。Hub 作为远程服务器，可能不可信或半可信——即使 Hub 诚实，也应假设其不拥有查看用户数据的能力。

**核心保证**：
- 加密密钥在客户端**生成**，**永久本地存储**，**永不传输**
- Hub 仅接收和处理**密文**
- 即使 Hub 被攻破，攻击者获得的也只是无意义的密文 blob

**应用场景**：
- 用户对话内容需要 Hub 帮助处理，但对话可能包含 API key、密码、业务敏感信息
- 多方协作时，各方数据互相保密

### 1.2 AES-256-GCM 加密层（`crypto` from `paths.js`）

```javascript
// paths.js 底部导出（与 paths 函数共存）
const ALGORITHM = 'aes-256-gcm';
const IV_BYTES = 12;
const TAG_BYTES = 16;
const KEY_BYTES = 32;  // 256-bit

function generateKey() {
  return crypto.randomBytes(KEY_BYTES);  // 本地随机生成
}

function encrypt(plaintext, key) {
  const iv = crypto.randomBytes(IV_BYTES);
  const cipher = crypto.createCipheriv(ALGORITHM, key, iv);
  const encrypted = Buffer.concat([cipher.update(input), cipher.final()]);
  const authTag = cipher.getAuthTag();  // GCM 认证标签
  return { ciphertext, iv, authTag };
}

// pack: [iv(12)][authTag(16)][ciphertext(...)] 固定头部长度便于解析
function pack(parts) {
  return Buffer.concat([parts.iv, parts.authTag, parts.ciphertext]);
}
```

**设计要点**：
- **GCM 模式**：同时提供机密性（加密）和完整性（authTag 验证）。密文被篡改时解密会抛出异常，而非返回错误数据。
- **12-byte IV**：足够随机，不重复即可（每次加密随机生成），无需计数器。
- **固定头部 layout**：`pack()` 的 `[iv][authTag][ciphertext]` layout 使得接收方无需 JSON 解析就能知道各字段边界（二进制协议设计）。
- **keys never leave client**：这是整个模型的根基。

### 1.3 隐私计算管线端到端

```
Client (Evolver)                           Hub Server
    │                                           │
    │  ① POST /a2a/privacy/submit              │
    │     { title, body, signals, node_id }     │
    │ ─────────────────────────────────────────►│
    │     ← { taskId, status }                 │
    │                                           │
    │  ② 本地: generateKey() → key (never sent) │
    │     encrypt(body, key) → {iv, authTag, ct}│
    │     pack() → packed blob                 │
    │  ③ POST /a2a/privacy/blob/upload         │
    │     { data_base64(packed), node_id,      │
    │       privacy_task_id, encryption }       │
    │ ─────────────────────────────────────────►│ (Hub stores encrypted blob)
    │     ← { blob_id }                        │
    │                                           │
    │  ④ POST /a2a/privacy/tool/execute        │
    │     { toolId, blob_id, node_id }          │
    │ ─────────────────────────────────────────►│ (Hub runs sealed tool on ciphertext)
    │     ← { resultKey, resultHash }          │
    │                                           │
    │  ⑤ GET /a2a/privacy/result/{taskId}     │
    │     ← { encrypted_result_base64 }         │
    │                                           │
    │  ⑥ 本地: unpack() → parts               │
    │     decrypt(parts, key) → plaintext       │
    │     (key never left client)               │
```

**关键观察**：
- **步骤 ②③④ 在 Hub 不可信时仍然安全**：Hub 只看到密文blob 和 toolId，不知道具体数据内容
- **步骤 ⑤ 返回的仍是加密结果**：`encrypted_result_base64` 是 Hub 在密文上执行的结果，再次加密后返回；本地用同一把 key 解密
- **Hub 不知道 key**：Hub 执行 `executeSealedTool` 时，只拿到 blob_id 和 tool_id，工具在 Hub 的 TEE 或密封环境中运行，无法获取 client key

### 1.4 密封工具执行模型

```javascript
async function executeSealedTool({ toolId, blobId }) {
  // Hub 在密封环境中运行 toolId，
  // 输入：blobId（密文 blob）
  // 输出：加密的结果 blob
  // Hub 本身不解密
}

async function getPrivacyResult(taskId, key) {
  // 本地解密 Hub 返回的加密结果
  const packed = Buffer.from(data.encrypted_result_base64, 'base64');
  const parts = unpack(packed);
  return decrypt(parts.ciphertext, key, parts.iv, parts.authTag);
}
```

**实现细节**：
- `parsePrivacyParams(body)`：解析 task body 中的 `[PRIVACY_PARAMS]...[/PRIVACY_PARAMS]` 块，提取 `tool_id` 和 `blob_ids[]`
- 这允许普通 task 中嵌入隐私计算需求，灵活组合
- 工具模板：`getToolTemplates()` 从 Hub 获取可用密封工具列表

### 1.5 BlueCortexCE 借鉴方案

| 方面 | Evolver 方案 | CE 可借鉴 |
|------|-------------|---------|
| **密钥管理** | 本地生成 `crypto.generateKey()`，存文件系统 | 未来可引入用户端密钥（如 OpenPGP 或 AES-256-GCM），观察数据加密后存储 |
| **传输安全** | blob 永远 base64 编码，TLS + 密文双重保护 | CE HTTP API 可支持 `X-Encrypted: true` 头部，body 为加密 blob |
| **隐私计算** | 密封工具在 Hub TEE 执行，数据不泄露 | CE 未来可引入"隐私观察"类型——观察内容加密存储，只有授权的 LLM 才能解密读取 |
| **二进制协议** | `pack()` 固定 layout，避免 JSON 解析开销 | 高频内部通信可用二进制格式 |
| **隐私块嵌入** | `[PRIVACY_PARAMS]` 可嵌入任意 task body | CE 未来的 context 模板可支持 `{{#privacy}}...{{/privacy}}` 块 |

**CE 短期可落地**：
1. `ObservationEntity` 增加 `encrypted_content` 字段（JSONB），内容为 AES-256-GCM 加密后的观察数据，key 由用户管理
2. 搜索时在数据库执行加密搜索（需要 homomorphic encryption 或可信执行环境，CE 短期不实现）
3. API 层增加 `X-Encryption-Key-ID` 头部，标识用户密钥版本

---

## §2 Commentary人格对话生成器（`ops/commentary.js`）

### 2.1 三种人格设计

```javascript
var PERSONAS = {
    standard: {
        success: ['Evolution complete. System improved.', 'Another successful cycle.'],
        failure: ['Cycle failed. Will retry.', 'Encountered issues. Investigating.'],
    },
    greentea: {
        success: ['So efficient... unlike someone else~', 'I finished before you even noticed~'],
        failure: ['Oops... it is not my fault though~', 'This is harder than it looks, okay?'],
    },
    maddog: {
        success: ['TARGET ELIMINATED.', 'Mission complete. Next.'],
        failure: ['FAILED. RETRYING.', 'Error. Will overcome.'],
    },
};

function getComment({ persona, success, duration }) {
    var p = PERSONAS[persona] || PERSONAS.standard;
    var pool = success ? p.success : p.failure;
    return pool[Math.floor(Math.random() * pool.length)];
}
```

**设计特点**：
- **极简**：没有 LLM 调用，直接查表 + random pick
- **人格正交**：三种人格在语气、emoji、措辞上截然不同
- **上下文感知**：传入 `success` 布尔值决定用哪个 pool
- **CLI 可执行**：`require.main === module` 时接受 persona 参数直接输出

### 2.2 BlueCortexCE 借鉴方案

CE 的观察/总结产出目前没有人格化。借鉴 commentary 的思路：

```java
// CE 可实现 PersonalityCommentService
enum Persona { STANDARD, CONCISE, VERBOSE }
String generateCycleComment(Persona persona, boolean success, long durationMs)

// 例：Concise 人格
success: ["Done.", "Improved.", "Fixed."]
failure: ["Failed.", "Error.", "Retry."]

// 例：Verbose 人格
success: ["Successfully completed the evolution cycle with positive outcome."]
failure: ["Cycle encountered issues. Investigation in progress."]
```

**用途**：Session summary 的人风格的简短注释，或 WebUI 展示 cycle 结果时的人格化描述。

---

## §3 问题生成器（`questionGenerator.js`）

### 3.1 Hub Bounty 系统集成

questionGenerator 的输出格式是标准的 A2A fetch `payload.questions`：

```javascript
return filtered.map(q => ({
  question: q.question,
  amount: q.amount,   // bounty 金额（0 = 无偿提问）
  signals: q.signals, // 关联信号
}));
```

Hub 接收到问题后创建 bounty，其他 agent 可认领并提供解答。这是"**社区协作问题解决**"的机制。

### 3.2 六种生成策略

| # | 触发信号 | 策略 | Priority |
|---|---------|------|---------|
| 1 | `recurring_error` / `high_failure_ratio` |  recurring_errsig → "哪些方法解决过类似错误？" | 3 |
| 2 | `capability_gap` / `unsupported_input_type` | 从会话转录找 "not supported" 行 → "如何解决能力缺口？" | 2 |
| 3 | `evolution_saturation` / `force_steady_state` | 已用基因列表 → "哪些新方向有价值？" | 1 |
| 4 | `consecutive_failure_streak_≥4` | 失败次数 → "应尝试什么替代策略？" | 3 |
| 5 | `user_feature_request` | 转录中找 "add/implement/create" → "有无社区最佳实践？" | 1 |
| 6 | `perf_bottleneck` | 转录中找 "slow/timeout/latency" → "哪些优化架构？" | 2 |

**设计亮点**：
- **上下文提取**：不仅用信号，还从 session transcript 中提取相关的上下文行（限制150字符）
- **优先级分层**：Priority 1=低优先级（探索性），Priority 3=高优先级（必须解决）
- **每周期上限**：`MAX_QUESTIONS_PER_CYCLE = 2`，防止污染 Hub

### 3.3 模糊去重机制

```javascript
function isDuplicate(question, recentQuestions) {
  // 精确匹配
  if (prev === qLower) return true;
  // 模糊匹配：word set 重叠 > 70%
  var qWords = new Set(qLower.split(/\s+/).filter(w => w.length > 2));
  var pWords = new Set(prev.split(/\s+/).filter(w => w.length > 2));
  var overlap = [...qWords].filter(w => pWords.has(w)).length;
  if (overlap / Math.max(qWords.size, pWords.size) > 0.7) return true;
  return false;
}
```

**去重状态管理**：
- 状态文件 `question_generator_state.json` 记录 `lastAskedAt`（3小时节流）和 `recentQuestions`（最近20条）
- 状态文件路径：`path.join(getEvolutionDir(), 'question_generator_state.json')`（session-scoped）

### 3.4 BlueCortexCE 借鉴方案

CE 的"观察 → 信号"链路已建立，但缺乏**主动提问/社区协作**机制。借鉴思路：

```java
// CE ObservationQuestionService
// 从高频失败观察、高频错误模式生成"待研究问题"
// 输出给 WebUI 的 "建议研究" 面板，或提交到外部协作系统

public record ResearchQuestion(
    String question,
    String contextExcerpt,    // 从失败观察中提取的上下文
    List<String> relatedSignals,
    int priority,             // 1-3
    String category            // "error_resolution" | "capability_gap" | "optimization"
) {}
```

**CE 可借鉴的实现细节**：
- 模糊去重的 word-set-overlap 算法：可用于 CE 的"重复观察检测"（观察去重不仅靠 content_hash，还可用语义模糊匹配）
- 问题优先级映射信号类型：CE 可建立 `ObservationType → ResearchQuestionPriority` 的对应表

---

## §4 自动 Issue 报告（`issueReporter.js`）

### 4.1 触发条件与冷却机制

```javascript
const DEFAULT_COOLDOWN_MS = 24 * 60 * 60 * 1000;  // 24小时
const DEFAULT_MIN_STREAK = 5;                       // 至少5次连续失败

function shouldReport(signals, config) {
  // 必须满足：
  // (failure_loop_detected) OR (recurring_error + high_failure_ratio)
  const hasFailureLoop = signals.includes('failure_loop_detected');
  const hasRecurringAndHigh = signals.includes('recurring_error')
                            && signals.includes('high_failure_ratio');
  if (!hasFailureLoop && !hasRecurringAndHigh) return false;

  // 连续失败次数必须 ≥ minStreak
  const streakCount = extractStreakCount(signals);
  if (streakCount > 0 && streakCount < config.minStreak) return false;

  // 冷却期内相同 errorKey 不重复报告
  const errorKey = computeErrorKey(signals);
  if (state.recentIssueKeys.includes(errorKey)) return false;
}
```

**errorKey 去重**：
```javascript
function computeErrorKey(signals) {
  const relevant = signals
    .filter(s => s.startsWith('recurring_errsig') || s.startsWith('ban_gene:')
              || s === 'recurring_error' || s === 'failure_loop_detected'
              || s === 'high_failure_ratio')
    .sort().join('|');
  return crypto.createHash('sha256').update(relevant).digest('hex').slice(0, 16);
}
```

### 4.2 Issue 内容构建

```javascript
function buildIssueBody(opts) {
  // 环境指纹（设备ID、版本、平台、容器）
  // 失败摘要（连续次数、失败信号）
  // 错误签名（规范化后的 recurring_errsig）
  // 最近事件表（intent / gene / outcome / reason）
  // 会话日志摘录（脱敏后，限制2000字符）
  // 报告ID（SHA256 of nodeId|timestamp|errorSig）
}
```

**脱敏**：`redactString()` 对日志和错误签名进行敏感信息脱敏（API key、token、路径等）

### 4.3 GitHub API 集成

```javascript
async function createGithubIssue(repo, title, body, token) {
  const url = 'https://api.github.com/repos/' + repo + '/issues';
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Authorization': 'Bearer ' + token, ... },
    body: JSON.stringify({ title, body }),
    signal: AbortSignal.timeout(15000),  // 15s 超时
  });
  return { number: data.number, url: data.html_url };
}
```

**报告后状态更新**：
```javascript
writeState({
  lastReportedAt: new Date().toISOString(),
  recentIssueKeys: [...recentKeys, errorKey].slice(-20),
  lastIssueUrl: result.url,
  lastIssueNumber: result.number,
});
```

### 4.4 BlueCortexCE 借鉴方案

CE 已有 `AlertService` 和飞书通知，但缺乏**自动外部 Issue 创建**机制。

| 方面 | Evolver | CE 可借鉴 |
|------|---------|---------|
| **触发条件** | 失败连击 + 信号组合 | CE 可定义"连续 N 次同类错误"触发 GitHub issue 创建 |
| **冷却机制** | 24h + errorKey 去重 | CE alert 已有类似机制，可增强 |
| **错误指纹** | `computeErrorKey` 哈希 | CE 可用 `extractedData.error_sig_norm`（规范化错误签名）作为 issue key |
| **内容模板** | 固定 Markdown 格式 | CE 可定义 issue 模板：环境 + 错误签名 + 最近观察 + 建议 |
| **脱敏** | `redactString()` | CE 在创建外部 issue 前必须脱敏观察内容 |
| **环境指纹** | `captureEnvFingerprint()` | CE 的 `runtime_env` 字段可作为 issue 的环境信息 |

---

## §5 综合：Hub 生态系统全景

```
                    ┌──────────────────────────────────────┐
                    │           Evolver Client              │
                    │  (加密 / 问题生成 / 自动报告 / 人格)  │
                    └──────────────┬───────────────────────┘
                                   │ HTTPS + TLS
                    ┌──────────────▼───────────────────────┐
                    │            Hub Server                │
                    │  ┌──────────────────────────────┐   │
                    │  │   Privacy Computing Engine   │   │
                    │  │   (Sealed Tool Execution)    │   │
                    │  │   ← AES-256-GCM blobs        │   │
                    │  └──────────────────────────────┘   │
                    │  ┌──────────────────────────────┐   │
                    │  │   Agent Directory             │   │
                    │  │   (searchByQuery/Signals)     │   │
                    │  └──────────────────────────────┘   │
                    │  ┌──────────────────────────────┐   │
                    │  │   Hub Search (Gene Reuse)    │   │
                    │  │   (Two-phase + LRU cache)    │   │
                    │  └──────────────────────────────┘   │
                    │  ┌──────────────────────────────┐   │
                    │  │   Bounty System               │   │
                    │  │   (Questions → Solutions)    │   │
                    │  └──────────────────────────────┘   │
                    └──────────────────────────────────────┘
```

**BlueCortexCE 在这个生态中的定位思考**：
- CE 是**旁路记忆**，天然适合作为 Hub 的数据来源之一（CE 的观察可贡献给 Hub 作为信号）
- CE 的隐私模型：目前是"明文存储于 PostgreSQL"，未来可演进为"用户端加密存储"
- CE 的 Hub 集成：`hubSearch.js` 的两相搜索管线是 CE 缺乏的——CE 目前没有"先搜 Hub 再本地"的机制

---

## §6 CE 落地优先级

| 优先级 | 任务 | 对应模块 |
|--------|------|---------|
| **P2** | `ObservationEntity` 增加 `encrypted_content` 字段（AES-256-GCM） | `privacyClient` 思想 |
| **P2** | 问题生成服务：从高频错误观察生成"建议研究" | `questionGenerator` 思想 |
| **P3** | Alert 增强：连续失败触发 GitHub issue（需脱敏） | `issueReporter` 思想 |
| **P3** | 模糊去重算法：word-set-overlap 用于观察去重 | `questionGenerator` §3.3 |
| **P3** | Session summary 增加人格化注释 | `commentary` 思想 |
| **P4** | Hub 集成：先搜 Hub Gene 再本地 solve | `hubSearch` 两相管线 |

---

## 附录：相关文件索引

| 文件 | 内容 |
|------|------|
| [index.md](./index.md) | 总导航 |
| [36](./36-memory-architecture-synthesis.md) | 记忆系统综合（反馈环路、适配器、三层记忆） |
| [37](./37-signal-taxonomy-gene-selection-end-to-end.md) | Signal Taxonomy + Gene Selection |
| [35](./35-a2a-protocol-asset-lifecycle-feedback.md) | A2A 协议（hello/publish/fetch/review） |
| [34](./34-solidify-pipeline-end-to-end.md) | Solidify 管线端到端 |
| [33](./33-v148-v166-architecture-evolution.md) | v1.48–v1.66 架构演变 |
| [27](./27-ops-suite-runtime-config-canary.md) | Ops 套件（health_check / lifecycle / cleanup） |
| [25](./25-advanced-patterns-prm-epigenetic-antipattern.md) | PRM / Epigenetic / Innovation Catalyst |
