<!-- part 5/8: auto-split from evolver-memory-analysis.md — see index.md -->

## 44. sanitize.js — 敏感信息脱敏（v0.9 新增）

**文件**: `src/gep/sanitize.js` (157 lines)

### 44.1 核心设计原则

`sanitize.js` 是 Evolver 的**隐私保护层**——在将 Capsule 发布到 Hub 之前，剥离所有敏感信息。核心原则：

1. **检测优先**：使用正则模式扫描敏感信息
2. **替换为占位符**：用 `[REDACTED]` 替换原始值
3. **不修改原始对象**：通过 `JSON.parse(JSON.stringify())` 实现深拷贝 + 清洗
4. **可逆性保留**：`scanForLeaks` 只报告，不替换，供调试用

### 44.2 敏感信息模式库（REDACT_PATTERNS）

**文件**: `sanitize.js:9-47`

```javascript
const REDACT_PATTERNS = [
  // API Keys & Tokens
  /Bearer\s+[A-Za-z0-9\-._~+\/]+=*/g,
  /sk-[A-Za-z0-9]{20,}/g,                    // OpenAI API Key
  /sk-proj-[A-Za-z0-9\-_]{20,}/g,            // OpenAI Project Key
  /sk-ant-[A-Za-z0-9\-_]{20,}/g,            // Anthropic Key
  
  // GitHub Tokens
  /ghp_[A-Za-z0-9]{36,}/g,
  /gho_[A-Za-z0-9]{36,}/g,
  /ghu_[A-Za-z0-9]{36,}/g,
  /ghs_[A-Za-z0-9]{36,}/g,
  /github_pat_[A-Za-z0-9_]{22,}/g,
  
  // AWS
  /AKIA[0-9A-Z]{16}/g,
  
  // npm Tokens
  /npm_[A-Za-z0-9]{36,}/g,
  
  // Private Keys
  /-----BEGIN\s+(?:RSA\s+|EC\s+|DSA\s+|OPENSSH\s+)?PRIVATE\s+KEY-----[\s\S]*?-----END\s+(?:RSA\s+|EC\s+|DSA\s+|OPENSSH\s+)?PRIVATE\s+KEY-----/g,
  
  // Basic Auth in URLs
  /(?<=:\/\/)[^@\s]+:[^@\s]+(?=@)/g,
  
  // Local Filesystem Paths
  /\/home\/[^\s"',;)}\]]+/g,
  /\/Users\/[^\s"',;)}\]]+/g,
  /[A-Z]:\\[^\s"',;)}\]]+/g,
  
  // Email Addresses
  /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g,
];
```

### 44.3 泄露检测（Leak Scanning）

**文件**: `sanitize.js:58-100`

`sanitize.js` 不仅做替换，还提供**只读检测**——返回找到的泄露位置和建议的环境变量替代：

```javascript
const LEAK_SCANNERS = [
  { type: 'api_key', pattern: /sk-[A-Za-z0-9]{20,}/g, suggest: 'process.env.OPENAI_API_KEY' },
  { type: 'github_token', pattern: /ghp_[A-Za-z0-9]{36,}/g, suggest: 'process.env.GITHUB_TOKEN' },
  { type: 'private_key', pattern: /-----BEGIN\s+PRIVATE\s+KEY-----/g, suggest: 'process.env.PRIVATE_KEY_PATH' },
  { type: 'db_url', pattern: /(?:mongodb|postgres|mysql):\/\/[^\s]{10,}/gi, suggest: 'process.env.DATABASE_URL' },
  { type: 'internal_ip', pattern: /\b(?:10\.\d|172\.1[6-9]|192\.168)\.\d\.\d(?::\d+)?\b/g, suggest: 'process.env.SERVICE_HOST' },
  // ...
];

function scanForLeaks(content) {
  const leaks = [];
  for (const scanner of LEAK_SCANNERS) {
    while ((match = scanner.pattern.exec(content)) !== null) {
      leaks.push({
        type: scanner.type,
        value: match[0].length > 60 ? match[0].slice(0, 57) + '...' : match[0],
        suggestion: scanner.suggest
      });
    }
  }
  return { found: leaks.length > 0, leaks };
}
```

### 44.4 逆向环境变量泄露检测（detectEnvValueLeaks）

**文件**: `sanitize.js:105-120`

这是 Evolver 最独特的设计之一——**反向检测当前进程环境变量的实际值是否被硬编码到内容中**：

```javascript
function detectEnvValueLeaks(content) {
  const leaks = [];
  for (const [key, val] of Object.entries(process.env)) {
    if (!val || val.length < 8) continue;
    if (ENV_SCAN_SKIP_KEYS.has(key)) continue;  // PATH/HOME/SHELL 等跳过
    
    // 如果 process.env.OPENAI_API_KEY 的实际值出现在 content 中
    if (content.includes(val)) {
      leaks.push({
        type: 'env_value_leak',
        envKey: key,
        suggestion: 'process.env.' + key
      });
    }
  }
  return leaks;
}
```

**Evolver 为什么这样做**：如果 Capsule 的 strategy 中写了 `api_key="sk-xxx..."` 而不是 `process.env.OPENAI_API_KEY`，这个检测会捕获到。这确保了 Capsule 不会无意中泄露当前环境的凭证。

### 44.5 sanitizePayload — 深拷贝 + 清洗

**文件**: `sanitize.js:53-56`

```javascript
function sanitizePayload(capsule) {
  if (!capsule || typeof capsule !== 'object') return capsule;
  return JSON.parse(JSON.stringify(capsule), (_key, value) => {
    if (typeof value === 'string') return redactString(value);
    return value;
  });
}
```

**关键**：原始 Capsule 对象**不被修改**。通过 `JSON.parse(JSON.stringify())` 创建深拷贝，在解析过程中对每个字符串字段执行 `redactString`。

### 44.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 敏感信息模式库 | 30+ 种正则覆盖 API key/token/私钥/路径/邮箱 | **高优先级**: BlueCortexCE 的 Observation/Extraction 应实现类似脱敏 |
| 替换占位符 | `[REDACTED]` 替代原始值 | **高优先级**: BlueCortexCE 的摘要/导出功能应使用脱敏占位符 |
| 逆向环境检测 | 检测当前 env 值是否被硬编码 | **高优先级**: BlueCortexCE 的 LLM 生成应避免硬编码凭证 |
| 非破坏性清洗 | 深拷贝 + JSON.parse reviver | **高优先级**: BlueCortexCE 的脱敏应保留原始对象 |
| 泄露建议 | 每个泄露有 `suggest: 'process.env.XXX'` | **中优先级**: BlueCortexCE 应提供"应该用什么环境变量"的建议 |

---

## 45. contentHash.js — 内容寻址哈希（v0.9 新增）

**文件**: `src/gep/contentHash.js` (65 lines)

### 45.1 核心设计原则

`contentHash.js` 实现**内容寻址存储（CAS）**的核心原语：
- **规范化（Canonicalize）**：将任意 JS 对象转换为确定性的 JSON 字符串
- **哈希（Hash）**：使用 SHA-256 计算内容的指纹
- **验证（Verify）**：比较 `asset_id` 与计算出的哈希是否匹配

### 45.2 规范化 JSON（Canonical JSON）

**文件**: `contentHash.js:15-35`

```javascript
function canonicalize(obj) {
  if (obj === null || obj === undefined) return 'null';
  if (typeof obj === 'boolean') return obj ? 'true' : 'false';
  if (typeof obj === 'number') return Number.isFinite(obj) ? String(obj) : 'null';
  if (typeof obj === 'string') return JSON.stringify(obj);
  if (Array.isArray(obj)) return '[' + obj.map(canonicalize).join(',') + ']';
  if (typeof obj === 'object') {
    const keys = Object.keys(obj).sort();  // 键排序！
    const pairs = [];
    for (const k of keys) {
      pairs.push(JSON.stringify(k) + ':' + canonicalize(obj[k]));
    }
    return '{' + pairs.join(',') + '}';
  }
  return 'null';
}
```

**关键特性**：
- 对象键按字母排序（`Object.keys(obj).sort()`）
- 数组保持顺序
- `undefined` 和非有限数字 → `null`
- 字符串值用 `JSON.stringify`（确保引号转义一致）

**Evolver 为什么这样做**：JavaScript 对象的键顺序在不同引擎/版本中可能不同。规范化确保 `{a:1,b:2}` 和 `{b:2,a:1}` 产生相同的哈希。

### 45.3 资产 ID 计算（computeAssetId）

**文件**: `contentHash.js:39-52`

```javascript
function computeAssetId(obj, excludeFields) {
  if (!obj || typeof obj !== 'object') return null;
  const exclude = new Set(excludeFields || ['asset_id']);  // 默认排除 asset_id 自身
  const clean = {};
  for (const k of Object.keys(obj)) {
    if (exclude.has(k)) continue;
    clean[k] = obj[k];
  }
  const canonical = canonicalize(clean);
  const hash = crypto.createHash('sha256').update(canonical, 'utf8').digest('hex');
  return 'sha256:' + hash;
}
```

**设计意图**：
- `asset_id` 本身被排除在哈希计算之外（否则会是先有鸡还是先有蛋的问题）
- 返回格式 `sha256:<hex>` —— 明确标注哈希算法

### 45.4 防篡改验证（verifyAssetId）

**文件**: `contentHash.js:55-62`

```javascript
function verifyAssetId(obj) {
  const claimed = obj.asset_id;
  if (!claimed || typeof claimed !== 'string') return false;
  const computed = computeAssetId(obj);
  return claimed === computed;
}
```

**Evolver 为什么这样做**：如果 Capsule/Gene 的 `asset_id` 与内容不匹配，说明数据在传输/存储过程中被篡改或损坏。

### 45.5 Schema 版本管理

**文件**: `contentHash.js:8`

```javascript
const SCHEMA_VERSION = '1.6.0';
// Bump MINOR for additive fields; MAJOR for breaking changes.
```

**Evolver 为什么这样做**：Schema 版本让 Evolver 能够检测"用新版 schema 序列化的资产被旧版读取"的情况。

### 45.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 规范化 JSON | 键排序 + 字符串转义一致 | **高优先级**: BlueCortexCE 的任何"内容哈希"应使用规范化的 JSON |
| asset_id 排除 | asset_id 自身不参与哈希计算 | **高优先级**: BlueCortexCE 的 ID 验证应排除 ID 字段本身 |
| 防篡改验证 | verifyAssetId 对比 claimed vs computed | **高优先级**: BlueCortexCE 的重要记录应有完整性校验 |
| Schema 版本 | SCHEMA_VERSION = '1.6.0' | **中优先级**: BlueCortexCE 的 API 响应应包含 schema_version 字段 |

---

## 46. crypto.js — AES-256-GCM 加密（v0.9 新增）

**文件**: `src/gep/crypto.js` (89 lines)

### 46.1 设计背景

`crypto.js` 实现 **AES-256-GCM 对称加密**，用于"sealed / privacy computing blobs"——即需要在多方之间传输但不想让中间节点解密的敏感数据。

**注意**：这是一个独立的加密模块，不依赖外部密钥管理服务。密钥在本地生成（`generateKey()`），从不离开客户端。

### 46.2 AES-256-GCM 参数

**文件**: `crypto.js:5-9`

```javascript
const ALGORITHM = 'aes-256-gcm';
const IV_BYTES = 12;       // GCM 推荐 12 字节 IV
const TAG_BYTES = 16;      // GCM 认证标签 16 字节
const KEY_BYTES = 32;      // 256 位密钥
```

### 46.3 加密/解密 API

```javascript
// 加密
const { ciphertext, iv, authTag } = encrypt(plaintext, key);
// ciphertext = 密文（不含 IV 和 authTag）
// iv = 12 字节初始化向量
// authTag = 16 字节认证标签

// 解密
const plaintext = decrypt(ciphertext, key, iv, authTag);
```

### 46.4 pack/unpack — 传输格式

**文件**: `crypto.js:70-90`

```javascript
// 打包：IV(12) + authTag(16) + ciphertext(...)，适合网络传输
function pack(parts) {
  return Buffer.concat([parts.iv, parts.authTag, parts.ciphertext]);
}

// 解包：从 Buffer 中提取 IV/authTag/ciphertext
function unpack(packed) {
  const iv = packed.subarray(0, 12);
  const authTag = packed.subarray(12, 28);
  const ciphertext = packed.subarray(28);
  return { ciphertext, iv, authTag };
}
```

**Evolver 为什么这样做**：GCM 模式提供**认证加密**（Authenticated Encryption）——不仅加密内容，还通过 `authTag` 确保内容未被篡改。这比单纯的 AES-CBC 更安全。

### 46.5 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| AES-256-GCM | 认证加密（IV + authTag + ciphertext） | **中优先级**: BlueCortexCE 的敏感数据存储可考虑类似加密 |
| 本地密钥生成 | generateKey() 从不离开客户端 | **中优先级**: BlueCortexCE 的加密应避免依赖外部 KMS |
| 密钥长度验证 | `if (!key || key.length !== KEY_BYTES)` | **高优先级**: BlueCortexCE 的任何加密都应验证密钥长度 |
| Buffer 打包格式 | IV + authTag + ciphertext 拼接 | **低优先级**: BlueCortexCE 的加密数据传输可参考此格式 |

---

## 47. envFingerprint.js — 环境指纹（v0.9 新增）

**文件**: `src/gep/envFingerprint.js` (84 lines)

### 47.1 核心设计原则

`envFingerprint.js` 为每个 Evolution Event/Capsule 记录**运行环境指纹**，用于：
1. **跨环境泛化（Cross-Environment Diffusion, GDI）**：衡量某个环境成功的 Gene 在另一个环境是否也有效
2. **环境分组**：`envFingerprintKey()` 将相似环境归为同一"环境类"
3. **Bug 溯源**：失败是否由特定环境引起？

### 47.2 指纹捕获（captureEnvFingerprint）

**文件**: `envFingerprint.js:14-50`

```javascript
function captureEnvFingerprint() {
  // 优先读取 evolver 自身 package.json（而非 host project 的）
  const ownPkgPath = path.resolve(__dirname, '..', '..', 'package.json');
  const pkg = JSON.parse(fs.readFileSync(ownPkgPath, 'utf8'));
  const pkgVersion = pkg.version;

  return {
    device_id: getDeviceId(),
    node_version: process.version,
    platform: process.platform,        // darwin / linux / win32
    arch: process.arch,               // x64 / arm64
    os_release: os.release(),
    hostname: sha256(os.hostname()).slice(0, 12),  // 哈希保护主机名隐私
    evolver_version: pkgVersion,      // evolver 版本（不是 host project）
    client: pkgName || 'evolver',    // npm 包名
    cwd: sha256(process.cwd()).slice(0, 12),        // 工作目录哈希
    container: isContainer(),        // 是否在容器中运行
    captured_at: new Date().toISOString(),
    region: process.env.EVOLVER_REGION?.slice(0, 5),  // 可选区域标签
  };
}
```

**关键设计**：
- **hostname 哈希化**：不存储明文 hostname，只存哈希（privacy-preserving）
- **cwd 哈希化**：同理
- **自身 package.json**：使用 `__dirname` 向上查找，确保 npm 部署时报告的是 evolver 版本而非 host project 版本

### 47.3 环境类键（envFingerprintKey）

**文件**: `envFingerprint.js:54-63`

```javascript
function envFingerprintKey(fp) {
  const parts = [
    fp.device_id || '',
    fp.node_version || '',
    fp.platform || '',
    fp.arch || '',
    fp.hostname || '',
    fp.client || fp.evolver_version || '',
    fp.client_version || fp.evolver_version || '',
  ].join('|');
  return sha256(parts).slice(0, 16);
}
```

**Evolver 为什么这样做**：直接比较完整指纹太严格（hostname/cwd 不同就算不同环境）。用环境类键可以让"同一台机器、不同目录的两个 Evolver 节点"被识别为"相同环境类"。

### 47.4 环境类比较（isSameEnvClass）

**文件**: `envFingerprint.js:66-68`

```javascript
function isSameEnvClass(fpA, fpB) {
  return envFingerprintKey(fpA) === envFingerprintKey(fpB);
}
```

**应用场景**：
- 判断某个失败 Capsule 是否与当前节点是"同一类环境"
- 如果不同环境类，则失败可能是环境问题而非 Gene 问题

### 47.5 表观遗传与 GDI

envFingerprint 是 Evolver **表观遗传机制（Epigenetic Marks）**的基础：

```javascript
// solidify.js 中，成功的 Capsule 记录环境指纹
gene.epigenetic_marks.push({
  context: envFingerprintKey(fp),  // "darwin-arm64|node20|abc123..."
  boost: +0.05,
  created_at: new Date().toISOString(),
  reason: 'success_in_environment',
});
```

**Evolver 为什么这样做**：表观遗传标记是**环境绑定**的——通过 `envFingerprintKey` 确保只有"相同环境类"才应用该 boost。

### 47.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 环境指纹 | device_id/node_version/platform/arch/container | **高优先级**: BlueCortexCE 的 Observation 可包含 `env_fingerprint` 字段 |
| 隐私哈希 | hostname/cwd 只存哈希 | **高优先级**: BlueCortexCE 的环境数据应做哈希化处理 |
| 环境类键 | 相似环境归为一类 | **中优先级**: BlueCortexCE 可用"环境类"做检索过滤 |
| isSameEnvClass | 判断两个记录是否同环境 | **中优先级**: BlueCortexCE 的"环境相关检索"可参考此逻辑 |
| 表观遗传绑定 | epigenetic_marks 通过 envFingerprintKey 关联 | **高优先级**: BlueCortexCE 的"学习历史"应按环境分组 |

---

## 48. issueReporter.js — 自动 GitHub 问题上报（v0.9 新增）

**文件**: `src/gep/issueReporter.js` (262 lines)

### 48.1 核心设计原则

`issueReporter.js` 实现**自动 GitHub Issue 上报**——当 Evolver 持续失败（5+ 次 streak）且无法自行解决时，自动在 GitHub 上创建 Issue 请求社区帮助。

### 48.2 上报触发条件（shouldReport）

**文件**: `issueReporter.js:130-160`

```javascript
function shouldReport(signals, config) {
  // 必须有失败循环或高频失败
  const hasFailureLoop = signals.includes('failure_loop_detected');
  const hasRecurringAndHigh = signals.includes('recurring_error') && signals.includes('high_failure_ratio');
  if (!hasFailureLoop && !hasRecurringAndHigh) return false;

  // streak 必须 >= 最小阈值（默认 5）
  const streakCount = extractStreakCount(signals);
  if (streakCount > 0 && streakCount < config.minStreak) return false;

  // 冷却期检查：24 小时内同一 error_key 不重复上报
  const errorKey = computeErrorKey(signals);  // SHA-256(error_signals)
  if (state.lastReportedAt) {
    const elapsed = Date.now() - new Date(state.lastReportedAt).getTime();
    if (elapsed < config.cooldownMs) return false;
  }

  return true;
}
```

### 48.3 Error Key 计算（computeErrorKey）

**文件**: `issueReporter.js:65-75`

```javascript
function computeErrorKey(signals) {
  const relevant = signals
    .filter(s => s.startsWith('recurring_errsig') ||
                  s.startsWith('ban_gene:') ||
                  s === 'recurring_error' ||
                  s === 'failure_loop_detected' ||
                  s === 'high_failure_ratio')
    .sort()
    .join('|');
  return sha256(relevant || 'unknown').slice(0, 16);
}
```

**Evolver 为什么这样做**：用相关失败信号的 SHA-256 哈希作为 error_key，确保"相同根因的失败"不会重复上报。

### 48.4 Issue 内容构建（buildIssueBody）

**文件**: `issueReporter.js:90-145`

```javascript
function buildIssueBody(opts) {
  const fp = opts.envFingerprint;
  const signals = opts.signals;
  const recentEvents = opts.recentEvents;
  const sessionLog = opts.sessionLog;

  return [
    '## Environment',
    '- **Evolver Version:** ' + fp.evolver_version,
    '- **Node.js:** ' + fp.node_version,
    '- **Platform:** ' + fp.platform + ' ' + fp.arch,
    '- **Container:** ' + (fp.container ? 'yes' : 'no'),

    '## Failure Summary',
    '- **Consecutive failures:** ' + streakCount,
    '- **Failure signals:** ' + failureSignals,

    '## Error Signature',
    '```',
    redactString(errorSig),  // 脱敏后的错误签名
    '```',

    '## Recent Evolution Events (sanitized)',
    eventsTable,  // Markdown 表格格式

    '## Session Log Excerpt (sanitized)',
    '```',
    sanitizedLog,  // 脱敏后的会话日志（最后 2000 字符）
    '```',
  ].join('\n');
}
```

### 48.5 自动上报状态机

**文件**: `issueReporter.js:170-220`

```javascript
// issue_reporter_state.json 结构
{
  "lastReportedAt": "2026-04-16T12:00:00Z",
  "recentIssueKeys": ["a1b2c3d4e5f6", ...],  // 最多保留 20 个
  "lastIssueUrl": "https://github.com/...",
  "lastIssueNumber": 42,
}
```

**防护机制**：
- `recentIssueKeys.length > 20` → 裁剪到最新 20 个
- 冷却期内同一 error_key → 跳过
- 无 GitHub Token → 静默跳过（非致命）

### 48.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 自动 Issue 创建 | 5+ streak + 冷却 24h | **中优先级**: BlueCortexCE 可在持续检索失败时创建内部工单 |
| errorKey 哈希 | 相同根因不重复上报 | **高优先级**: BlueCortexCE 的"问题上报"应有去重机制 |
| 内容脱敏 | redactString 处理错误签名和日志 | **高优先级**: BlueCortexCE 的任何外部上报都应脱敏 |
| 非致命设计 | 无 GitHub Token → 静默跳过 | **高优先级**: BlueCortexCE 的辅助功能失败不应阻断主流程 |
| 状态持久化 | issue_reporter_state.json 控制冷却 | **中优先级**: BlueCortexCE 的限流机制应有持久化状态 |

---

## 49. validationReport.js — 标准化验证报告（v0.9 新增）

**文件**: `src/gep/validationReport.js` (55 lines)

### 49.1 核心设计原则

`validationReport.js` 定义 **ValidationReport 标准 Schema**——一种机器可读、自我包含、可互操作的验证结果格式，可被外部 Hub 或 Judge 消费。

### 49.2 报告结构（buildValidationReport）

**文件**: `validationReport.js:20-45`

```javascript
function buildValidationReport({ geneId, commands, results, envFp, startedAt, finishedAt }) {
  return {
    type: 'ValidationReport',
    schema_version: SCHEMA_VERSION,
    id: 'vr_' + Date.now(),
    gene_id: geneId || null,
    env_fingerprint: envFp,
    env_fingerprint_key: envFingerprintKey(envFp),  // 用于环境分组
    commands: commands.map((cmd, i) => ({
      command: String(cmd || ''),
      ok: !!results[i]?.ok,
      stdout: String(results[i]?.out || results[i]?.stdout || '').slice(0, 4000),
      stderr: String(results[i]?.err || results[i]?.stderr || '').slice(0, 4000),
    })),
    overall_ok: results.every(r => r.ok),
    duration_ms: finishedAt - startedAt,
    created_at: new Date().toISOString(),
  };
}
```

**关键字段**：
- `env_fingerprint_key`：用于将多个 ValidationReport 按环境分组
- `stdout/stderr` 截断到 4000 字符：防止日志过大
- `overall_ok`：所有命令都通过才算 overall success

### 49.3 Schema 验证（isValidValidationReport）

**文件**: `validationReport.js:48-58`

```javascript
function isValidValidationReport(obj) {
  if (!obj || typeof obj !== 'object') return false;
  if (obj.type !== 'ValidationReport') return false;
  if (!obj.id || typeof obj.id !== 'string') return false;
  if (!Array.isArray(obj.commands)) return false;
  if (typeof obj.overall_ok !== 'boolean') return false;
  return true;
}
```

### 49.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 标准化 Schema | ValidationReport type + schema_version | **高优先级**: BlueCortexCE 的所有 API 响应应有 type + schema_version |
| 环境指纹键 | env_fingerprint_key 用于分组 | **中优先级**: BlueCortexCE 的验证结果可按环境分组查询 |
| 输出截断 | stdout/stderr 限制 4000 chars | **高优先级**: BlueCortexCE 的日志字段应有长度上限 |
| 自我包含 | env_fingerprint 内嵌在报告中 | **高优先级**: BlueCortexCE 的报告应内嵌完整上下文 |

---

## 50. analyzer.js — 自省分析器（v0.9 新增）

**文件**: `src/gep/analyzer.js` (35 lines)

### 50.1 设计定位

`analyzer.js` 是 Evolver 最简单的模块之一，实现**自省（Self-Correction）分析器**——从 MEMORY.md 中提取失败记录并建议更好的 future mutations。

### 50.2 失败提取逻辑

**文件**: `analyzer.js:12-30`

```javascript
function analyzeFailures() {
  const memoryPath = path.join(process.cwd(), 'MEMORY.md');
  if (!fs.existsSync(memoryPath)) return { status: 'skipped', reason: 'no_memory' };

  const content = fs.readFileSync(memoryPath, 'utf8');
  const failureRegex = /\|\s*\*\*F\d+\*\*\s*\|\s*Fix\s*\|\s*(.*?)\s*\|\s*\*\*(.*?)\*\*\((.*?)\)\s*\|/g;

  const failures = [];
  let match;
  while ((match = failureRegex.exec(content)) !== null) {
    failures.push({
      summary: match[1].trim(),
      detail: match[2].trim()
    });
  }

  return {
    status: 'success',
    count: failures.length,
    failures: failures.slice(0, 3)  // 只返回最近 3 个
  };
}
```

**MEMORY.md 失败记录格式**（Evolver 使用的格式）：
```
| **F1** | Fix | Summary | Detail (reason) |
```

### 50.3 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 自省失败分析 | 从 MEMORY.md 提取 F\d+ 失败记录 | **高优先级**: BlueCortexCE 可实现类似的自省分析（从 Observation 中提取失败模式） |
| 失败数量限制 | 只返回最多 3 个 | **高优先级**: BlueCortexCE 的分析结果应有数量上限防止溢出 |
| 非致命设计 | MEMORY.md 不存在 → skipped | **高优先级**: BlueCortexCE 的任何文件读取都应是非致命的 |

---

## 51. 整体架构补充：Evolver 的安全与隐私体系（v0.9 新增）

### 51.1 安全分层总览

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: 输入安全 (Input Safety)                            │
│  - policyCheck.js: 验证命令白名单（isValidationCommandAllowed）│
│  - policyCheck.js: 伦理违规检测（ethicsBlockPatterns）        │
│  - policyCheck.js: ReDoS 防护（MAX_REGEX_PATTERN_LEN）       │
│  - policyCheck.js: 破坏性变更检测（detectDestructiveChanges） │
├─────────────────────────────────────────────────────────────┤
│  Layer 2: 隐私保护 (Privacy)                                │
│  - sanitize.js: 敏感信息脱敏（REDACT_PATTERNS）               │
│  - sanitize.js: 逆向环境变量泄露检测（detectEnvValueLeaks）   │
│  - envFingerprint.js: hostname/cwd 哈希化                    │
│  - crypto.js: AES-256-GCM 认证加密                          │
│  - privacyClient.js: 密封执行（加密 blob + 本地密钥管理）    │
├─────────────────────────────────────────────────────────────┤
│  Layer 3: 数据完整性 (Integrity)                             │
│  - contentHash.js: 规范化 JSON + SHA-256 哈希                 │
│  - contentHash.js: 防篡改验证（verifyAssetId）                │
│  - contentHash.js: Schema 版本管理                          │
├─────────────────────────────────────────────────────────────┤
│  Layer 4: 监控与上报 (Monitoring)                            │
│  - issueReporter.js: 自动 GitHub Issue 上报                  │
│  - validationReport.js: 标准化验证报告                       │
│  - analyzer.js: 自省失败分析                                │
└─────────────────────────────────────────────────────────────┘
```

### 51.2 BlueCortexCE 安全现状对照

| Evolver 层 | BlueCortexCE 等价 | 差距 |
|-----------|------------------|------|
| isValidationCommandAllowed | 无（不执行验证命令） | **安全差距**：如 BlueCortexCE 未来支持自定义验证，需实现类似白名单 |
| ethicsBlockPatterns | 无 | **缺失**：BlueCortexCE 的 Observation/Extraction 无伦理检测 |
| sanitize.js | 无 | **缺失**：BlueCortexCE 的摘要导出无脱敏 |
| detectEnvValueLeaks | 无 | **缺失**：BlueCortexCE 可能无意中硬编码凭证 |
| hostname/cwd 哈希 | 无 | **中差距**：BlueCortexCE 存储环境信息时未哈希化 |
| AES-256-GCM | 无 | **低差距**：BlueCortexCE 无隐私计算需求 |
| privacyClient.js 密封执行 | 无 | **低差距**：BlueCortexCE 无 Hub 密封执行场景 |
| computeAssetId/verifyAssetId | 无 | **缺失**：BlueCortexCE 的记录无防篡改机制 |
| env_fingerprint | 无 | **缺失**：BlueCortexCE 的 Observation 不记录环境上下文 |
| issueReporter | 无 | **中差距**：BlueCortexCE 无自动问题上报机制 |

### 51.3 高优先级安全改进建议

| 改进 | 依据 | 优先级 |
|------|------|--------|
| Observation 脱敏 | sanitize.js 的 30+ 敏感模式库 | **高** |
| 防篡改验证 | contentHash.js 的 verifyAssetId | **高** |
| Schema 版本控制 | SCHEMA_VERSION + 规范化 JSON | **高** |
| 环境指纹 | envFingerprint.js 嵌入 Observation | **中** |
| 伦理违规检测 | ethicsBlockPatterns（prompt 层面） | **中** |

---

## 53. hubSearch.js — 联邦知识市场与两阶段搜索（v1.0 新增）

**文件**: `src/gep/hubSearch.js` (407 lines)

### 53.1 核心设计原则：Search-First Evolution

Evolver 的 `hubSearch.js` 实现**搜索优先进化**模式——在本地推理之前，先查询 Hub 上的共享知识库（基因/胶囊市场）：

```
信号提取 → hubSearch(信号) → 命中: 复用 | 未命中: 本地进化
```

这与 Hermes/BlueCortexCE 的设计理念截然不同：
- **Evolver**：假设"别人可能已经解决了同样的问题"，先搜索，有就复用
- **Hermes/BlueCortexCE**：纯粹本地记忆，无跨实例知识共享概念

### 53.2 两阶段搜索（Search-Then-Fetch）

**文件**: `hubSearch.js:150-250`

为了最小化 Hub API 成本，hubSearch 实现**两阶段搜索**：

```
Phase 1: POST /a2a/fetch { signals, search_only: true }
  → 免费！只返回候选资产的元数据（id, confidence, reputation, status）
  → 结果按 signal fingerprint 缓存（5 分钟 TTL，200 条上限）

Phase 2: POST /a2a/fetch { asset_ids: [best_match] }
  → 付费！但只取最优匹配的完整 payload
  → payload 按 asset_id 缓存（LRU，100 条上限）
```

**为什么这样设计**：
- 完整 payload（如 Capsule 的 strategy 代码）比 metadata 大得多
- 只对最有可能被复用的资产付费，避免浪费
- metadata 缓存减少重复搜索的网络开销

**Deadline 控制**：
```javascript
// hubSearch.js:167-180
const deadline = Date.now() + timeoutMs;  // 8000ms 默认

// Phase 2 只在剩余时间 > 500ms 时执行
const remaining = deadline - Date.now();
if (remaining > MIN_PHASE2_MS) {
  // 执行 Phase 2...
}
```

### 53.3 并行语义搜索增强

**文件**: `hubSearch.js:85-120`

Phase 1 期间，hubSearch 同时发起**语义搜索**（`/a2a/assets/semantic-search`）作为补充：

```javascript
// hubSearch.js:140-150
var fetchPromise = fetchPhase1(searchMsg, endpoint, headers, deadline);
var semanticPromise = fetchSemanticResults(hubUrl, headers, signalList, SEMANTIC_TIMEOUT_MS);

var settled = await Promise.allSettled([fetchPromise, semanticPromise]);
var fetchResult = settled[0].status === 'fulfilled' ? settled[0].value : { ok: false, results: [] };
var semanticResults = settled[1].status === 'fulfilled' ? settled[1].value : [];
```

**语义搜索的特点**：
- 只搜索非错误信号（过滤 `errsig:` 前缀）
- 提取信号中的语义部分（如 `capability_gap:web_crawl` → `web_crawl`）
- 最多 12 个信号词
- 3 秒超时，独立失败不影响主搜索

### 53.4 资产评分算法

**文件**: `hubSearch.js:125-145`

```javascript
function scoreHubResult(asset) {
  const confidence = Number(asset.confidence) || 0;
  const streak = Math.min(Math.max(Number(asset.success_streak) || 0, 1), MAX_STREAK_CAP);
  const repRaw = Number(asset.reputation_score);
  const reputation = Number.isFinite(repRaw) ? repRaw : 50;
  var base = confidence * streak * (reputation / 100);
  var sim = Number(asset._semantic_similarity) || 0;
  if (sim > 0) base += sim * SEMANTIC_SIMILARITY_BONUS;  // +0.3 bonus
  return base;
}
```

**评分公式**：`confidence × min(streak, 5) × (reputation / 100) + semantic_similarity_bonus`

| 因子 | 说明 |
|------|------|
| `confidence` | Hub 记录的资产置信度（0-1） |
| `streak` | 连续成功次数（上限 5，防止无限膨胀） |
| `reputation` | Hub 声誉分（0-100） |
| `semantic_similarity` | 语义相似度匹配时 +0.3 加权 |

### 53.5 命中阈值与模式选择

**文件**: `hubSearch.js:160-175`

```javascript
const threshold = (opts && Number.isFinite(opts.threshold)) ? opts.threshold : getMinReuseScore();
// DEFAULT_MIN_REUSE_SCORE = 0.72

const pick = pickBestMatch(results, threshold);
if (!pick) return { hit: false, reason: 'below_threshold', candidates: results.length };

// reuse mode: 'reference' (inject as hint) | 'direct' (skip local reasoning)
return { hit: true, match: best, score: bestScore, mode: getReuseMode() };
```

**两种复用模式**：
- `reference`：将 Hub 资产的摘要注入本地 prompt 作为"强烈提示"，不跳过本地推理
- `direct`：完全跳过本地推理，直接使用 Hub 资产的 strategy

### 53.6 多层缓存架构

**文件**: `hubSearch.js:30-70`

```
_searchCache (Map)
  key:   signals.sort().join('|')
  value: { ts, results[] }
  TTL:   5 分钟
  上限:  200 条

_payloadCache (Map)
  key:   asset_id
  value: full payload object
  TTL:   永久（bounded LRU）
  上限:  100 条
```

**缓存键设计**：信号排序后用 `|` 连接，保证 `[a, b]` 和 `[b, a]` 产生相同的缓存键。

### 53.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| Search-First 模式 | 先搜索共享知识，未命中才本地推理 | **高优先级**: BlueCortexCE 的 `/api/context/generate` 可优先查跨实例知识 | 高 |
| 两阶段搜索 | 免费 metadata → 付费完整 payload | **高优先级**: BlueCortexCE 的检索可分离"摘要"和"完整记录" | 高 |
| 多层缓存 | search cache (TTL) + payload cache (LRU) | **高优先级**: BlueCortexCE 的向量检索结果可加 LRU 缓存 | 高 |
| 并行语义搜索 | Phase 1 期间并行语义搜索 | **中优先级**: BlueCortexCE 的搜索可同时做关键词+向量混合 | 中 |
| 评分公式 | confidence × streak × reputation | **中优先级**: BlueCortexCE 的检索排序可引入"使用次数/成功率" | 中 |
| 复用模式 | reference vs direct | **低优先级**: BlueCortexCE 的 SDK 消费方可选择注入深度 | 低 |

---

## 54. hubReview.js — 使用验证型评价系统（v1.0 新增）

**文件**: `src/gep/hubReview.js` (206 lines)

### 54.1 核心设计：使用后评价

当 Evolver 复用了一个 Hub 资产（`source_type = 'reused'` 或 `'reference'`），solidify 完成后会自动向 Hub 提交**使用评价**：

```
solidify 成功 → submitHubReview(资产ID, outcome, signals) → Hub 更新声誉分
```

**评价的价值**：
- 资产在 Hub 上的 `reputation_score` 取决于使用者的真实反馈
- 评分驱动后续复用决策（`scoreHubResult` 中的 reputation 因子）
- 形成正反馈循环：好资产获得高声誉 → 更多人复用 → 进一步验证

### 54.2 评分推导算法

**文件**: `hubReview.js:65-78`

```javascript
function _deriveRating(outcome, constraintCheck) {
  if (outcome && outcome.status === 'success') {
    const score = Number(outcome.score) || 0;
    return score >= 0.85 ? 5 : 4;  // 高分 success → 4-5 星
  }
  const hasViolation = constraintCheck && Array.isArray(constraintCheck.violations)
    && constraintCheck.violations.length > 0;
  return hasViolation ? 1 : 2;       // 失败 + 约束违反 → 1 星
}
```

| 情况 | 评分 |
|------|------|
| success + score ≥ 0.85 | ⭐⭐⭐⭐⭐ (5) |
| success + score < 0.85 | ⭐⭐⭐⭐ (4) |
| failed + 无约束违反 | ⭐⭐ (2) |
| failed + 有约束违反 | ⭐ (1) |

### 54.3 去重机制：本地历史文件

**文件**: `hubReview.js:20-50`

```javascript
const REVIEW_HISTORY_FILE = path.join(getEvolutionDir(), 'hub_review_history.json');
const REVIEW_HISTORY_MAX_ENTRIES = 500;

function _alreadyReviewed(assetId) {
  const history = _loadReviewHistory();
  return !!history[assetId];
}

function _markReviewed(assetId, rating, success) {
  const history = _loadReviewHistory();
  history[assetId] = { at: Date.now(), rating, success };
  _saveReviewHistory(history);
}
```

**防重机制**：
- 每个 assetId 只评价一次
- 历史文件上限 500 条（超出的最旧记录被清理）
- 即使 Hub 返回 `already_reviewed` 错误，也本地标记避免重复提交

### 54.4 评价内容构建

**文件**: `hubReview.js:80-100`

```javascript
function _buildReviewContent({ outcome, gene, signals, blast, sourceType }) {
  // 构建最多 2000 字符的评价内容
  parts.push('Outcome: ' + status + ' (score: ' + score + ')');
  parts.push('Reuse mode: ' + (sourceType || 'unknown'));
  if (gene && gene.id) parts.push('Gene: ' + gene.id + ' (' + gene.category + ')');
  if (signals) parts.push('Signals: ' + signals.slice(0, 6).join(', '));
  if (blast) parts.push('Blast radius: ' + blast.files + ' file(s), ' + blast.lines + ' line(s)');
  parts.push(status === 'success' ? '成功固化和应用。' : '未产生成功的进化循环。');
}
```

### 54.5 非阻塞设计

**文件**: `hubReview.js:155-180`

```javascript
try {
  var res = await fetch(endpoint, { method: 'POST', ... });
  if (res.ok) {
    _markReviewed(reusedAssetId, rating, true);
    return { submitted: true, rating, asset_id: reusedAssetId };
  }
  // Hub 返回 already_reviewed → 本地标记
  if (errCode === 'already_reviewed') {
    _markReviewed(reusedAssetId, rating, false);
  }
  return { submitted: false, reason: errCode };
} catch (err) {
  var reason = err.name === 'AbortError' ? 'timeout' : 'fetch_error';
  return { submitted: false, reason, error: err.message };
}
```

**关键**：评价提交**从不影响** solidify 的结果。无论提交成功/失败/超时，solidify 都继续。

### 54.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 使用验证型评价 | 复用资产后自动提交 outcome-based 评价 | **高优先级**: BlueCortexCE 的 `/api/context/generate` 可在消费后提交质量反馈 | 高 |
| 评分推导 | outcome status + constraint violations → 1-5 星 | **高优先级**: BlueCortexCE 的检索结果可实现类似的"使用质量分" | 高 |
| 去重机制 | 本地 JSON 历史 + 500 条上限 | **高优先级**: BlueCortexCE 的反馈系统需要防重复提交 | 高 |
| 非阻塞设计 | review 失败不影响主流程 | **高优先级**: BlueCortexCE 的反馈提交必须是非阻塞的 | 高 |
| 声誉分驱动 | reputation_score 影响复用评分 | **中优先级**: BlueCortexCE 的检索排序可引入"历史质量分" | 中 |

---

## 55. executionTrace.js — 隐私保护的执行遥测（v1.0 新增）

**文件**: `src/gep/executionTrace.js` (201 lines)

### 55.1 设计背景

`solidify.js` 在每次进化固化和验证后，构建一个**结构化的执行轨迹**（ExecutionTrace），可以选择性地上传给 Hub 的 `EvolutionEvent`：

```javascript
// executionTrace.js:90
const trace = buildExecutionTrace({
  gene, mutation, signals, blast, constraintCheck,
  validation, canary, outcomeStatus, startedAt
});
```

**核心设计原则**：trace 只包含**统计指标和脱敏元数据**，原始代码内容**永不离开本地**。

### 55.2 Trace 级别控制

**文件**: `executionTrace.js:8-20`

```javascript
const TRACE_LEVELS = { none: 0, minimal: 1, standard: 2 };

function getTraceLevel() {
  const raw = String(process.env.EVOLVER_TRACE_LEVEL || 'minimal').toLowerCase().trim();
  return TRACE_LEVELS[raw] != null ? raw : 'minimal';
}
```

| 级别 | 内容 |
|------|------|
| `none` | 完全不生成 trace |
| `minimal` | 仅核心指标（文件数、行数变化、验证结果） |
| `standard` | 丰富上下文（文件类型统计、验证命令、脱敏后的错误签名） |

### 55.3 脱敏规则体系

**文件**: `executionTrace.js:18-60`

#### 路径脱敏（`desensitizeFilePath`）

```javascript
// src/utils/retry.js → retry.js
function desensitizeFilePath(filePath) {
  const ext = path.extname(filePath);
  const base = path.basename(filePath);
  return base || ext || 'unknown';
}
```

**Evolver 为什么这样做**：保留文件类型信息（如 `.ts`, `.py`）对 Hub 的统计有价值，但原始路径可能泄露项目结构。

#### 错误签名提取（`extractErrorSignature`）

```javascript
function extractErrorSignature(errorText) {
  // TypeError: x is not a function → TypeError
  const jsError = text.match(/^((?:[A-Z][a-zA-Z]*)?Error)\b/);
  if (jsError) return jsError[1];

  // ECONNRESET, ENOENT, EPERM → E[A-Z]{2,}
  const errno = text.match(/\b(E[A-Z]{2,})\b/);
  if (errno) return errno[1];

  // HTTP 4xx/5xx → HTTP_400
  const http = text.match(/\b((?:4|5)\d{2})\b/);
  if (http) return 'HTTP_' + http[1];
}
```

**Evolver 为什么这样做**：原始错误消息可能包含敏感上下文（如用户 ID、文件路径），但错误类型签名是通用的，可以跨实例共享以改进错误处理。

### 55.4 爆炸半径分级

**文件**: `executionTrace.js:68-75`

```javascript
function classifyBlastLevel(blast) {
  if (!blast) return 'unknown';
  const files = Number(blast.files) || 0;
  const lines = Number(blast.lines) || 0;
  if (files <= 3 && lines <= 50) return 'low';
  if (files <= 10 && lines <= 200) return 'medium';
  return 'high';
}
```

### 55.5 工具链推断

**文件**: `executionTrace.js:55-65`

```javascript
function inferToolChain(validationResults, blast) {
  const tools = new Set();
  if (blast && blast.files > 0) tools.add('file_edit');
  for (const r of validationResults) {
    if (/jest|mocha/.test(r.cmd)) tools.add('test_run');
    else if (/eslint|lint/.test(r.cmd)) tools.add('lint_check');
    else if (/validate|check/.test(r.cmd)) tools.add('validation_run');
  }
  return Array.from(tools);
}
```

### 55.6 完整 Trace 结构（standard 级别）

```javascript
{
  gene_id: 'gene_error_handling_v2',
  mutation_category: 'error_repair',
  signals_matched: ['log_error', 'errsig_norm:...', 'capability_gap'],
  outcome: 'success',
  files_changed_count: 3,
  lines_added: 42, lines_removed: 18,
  validation_result: 'pass',
  blast_radius: 'low',
  // standard 级别额外：
  file_types: { '.ts': 2, '.json': 1 },
  validation_commands: ['npm test -- --coverage'],
  error_signatures: ['TypeError', 'ReferenceError'],
  tool_chain: ['file_edit', 'test_run'],
  validation_duration_ms: 2340,
  canary_ok: true,
  created_at: '2026-04-17T...',
}
```

### 55.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 隐私保护 trace | 代码永不离开，仅统计指标 + 脱敏元数据 | **高优先级**: BlueCortexCE 的任何"遥测"都应实现类似脱敏 | 高 |
| 路径脱敏 | basename + extension | **高优先级**: BlueCortexCE 记录"相关文件"时只存扩展名+文件名 | 高 |
| 错误签名归一化 | TypeError/ENOENT/HTTP_500 | **高优先级**: BlueCortexCE 的错误记录应只存类型，不存完整消息 | 高 |
| Trace 级别 | none/minimal/standard 可配置 | **中优先级**: BlueCortexCE 的遥测应有可配置的详细度 | 中 |
| 工具链推断 | 从验证命令反推工具 | **中优先级**: BlueCortexCE 可从 context 推断使用了哪些工具 | 中 |
| Blast 分级 | 低/中/高 阈值 | **低优先级**: BlueCortexCE 的 mutation 影响评估可参考 | 低 |

---

