# 61 — Evolver 脱敏 + 隐私计算管线深度分析

**目标**：深入分析 `gep/sanitize.js` 的双模式（替换 vs 检测）、`ops/privacyClient.js` + `gep/crypto.js` 的六步隐私计算管线，以及 HUB_EVENT_SIGNALS 的隐私事件分类。

**数据来源**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/sanitize.js`、`src/gep/privacyClient.js`、`src/gep/crypto.js`

**最后更新**：2026-04-25

---

## §1 sanitize.js — 双模式敏感信息处理

### 1.1 两种模式对比

`sanitize.js` 提供两个互补的操作模式：

| 模式 | 函数 | 行为 | 用途 |
|------|------|------|------|
| **替换模式** | `sanitizePayload()` / `redactString()` | 用 `[REDACTED]` 替换敏感值 | 发布前预处理（不可逆） |
| **检测模式** | `scanForLeaks()` / `detectEnvValueLeaks()` / `fullLeakCheck()` | 仅报告，不修改 | 发布前扫描（可逆） |

### 1.2 替换模式 — `redactString()`

**REDACT_PATTERNS**（14 种）：

```javascript
// API tokens
/Bearer\s+[A-Za-z0-9\-._~+\/]+=*/g
/sk-[A-Za-z0-9]{20,}/g                     // OpenAI
/sk-proj-[A-Za-z0-9\-_]{20,}/g             // OpenAI project key
/sk-ant-[A-Za-z0-9\-_]{20,}/g             // Anthropic
/ghp_|gho_|ghu_|ghs_|github_pat_/g        // GitHub tokens
/AKIA[0-9A-Z]{16}/g                       // AWS access key
/npm_[A-Za-z0-9]{36,}/g                    // npm token

// Private keys
/-----BEGIN\s+(?:RSA\s+|EC\s+|DSA\s+|OPENSSH\s+)?PRIVATE\s+KEY-----[\s\S]*?-----END\s+...KEY-----/g

// Local paths（含用户名）
\/home\/[^\s"',;)}\]]+/g
\/Users\/[^\s"',;)}\]]+/g
/[A-Z]:\\[^\s"',;)}\]]+/g                  // Windows

// Email
/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g

// Basic auth in URLs（仅替换凭证，保留 :// 和 @）
/(?<=:\/\/)[^@\s]+:[^@\s]+(?=@)/g

// .env references
/\.env(?:\.[a-zA-Z]+)?/g
```

**深度分析**：
- **前瞻断言** `(?<=:\/\/)` 和 `(?=@)` 匹配 URL 中的 `user:pass@` 部分，但不替换 `: //` 和 `@`
- **`[\s\S]*?`** 非贪婪匹配私钥的完整 PEM 块（包括换行符）
- **每次迭代都 reset `lastIndex`**：`pattern.lastIndex = 0` 保证全局 regex 在循环中正确工作
- **深度 clone**：`JSON.parse(JSON.stringify(capsule), handler)` 确保嵌套对象和数组都被处理

### 1.3 检测模式 — `scanForLeaks()`

**LEAK_SCANNERS**（17 种扫描规则）：

```javascript
{ type: 'api_key',      pattern: /sk-[A-Za-z0-9]{20,}/g,
  suggest: 'process.env.OPENAI_API_KEY' },
{ type: 'github_token', pattern: /ghp_[A-Za-z0-9]{36,}/g,
  suggest: 'process.env.GITHUB_TOKEN' },
{ type: 'db_url',       pattern: /(?:mongodb|postgres|...):\/\/[^\s"',;)}\]]{10,}/gi,
  suggest: 'process.env.DATABASE_URL' },
{ type: 'internal_ip',  pattern: /\b(?:10\.\d{1,3}...|172...(1[6-9]|2\d|3[01])...\d...|192\.168......)/g,
  suggest: 'process.env.SERVICE_HOST' },
{ type: 'local_path',   pattern: /\/home\/[a-zA-Z0-9_.-]+\/|\\/Users\/[a-zA-Z0-9_.-]+\\//g,
  suggest: 'process.env.HOME' },
// ...
```

**返回结构**：

```javascript
{ found: true|false,
  leaks: [
    { type: 'api_key',
      value: 'sk-proj-abc123...',  // 超长值截断至 60 字符
      suggestion: 'process.env.OPENAI_API_KEY' },
    ...
  ]
}
```

### 1.4 逆向检测 — `detectEnvValueLeaks()`

这是最有技术含量的检测函数：**当前进程环境变量值出现在目标内容中 = 硬编码泄漏**。

```javascript
function detectEnvValueLeaks(content) {
    const leaks = [];
    for (const [key, val] of Object.entries(process.env)) {
        if (!val || val.length < 8) continue;      // 避免短值误报
        if (ENV_SCAN_SKIP_KEYS.has(key)) continue;  // 跳过 PATH/HOME/SHELL 等
        if (content.includes(val)) {
            leaks.push({
                type: 'env_value_leak',
                envKey: key,
                value: val.length > 60 ? val.slice(0, 57) + '...' : val,
                suggestion: 'process.env.' + key
            });
        }
    }
    return leaks;
}
```

**实际威胁场景**：Evolver 在 prompt 中输出了某个 API key 的实际值（而非引用 `process.env.KEY`），这个函数会检测到并建议替换。

### 1.5 全链路检测 — `fullLeakCheck()`

```javascript
function fullLeakCheck(content) {
    const scan = scanForLeaks(content);          // 17 种 pattern 扫描
    const envLeaks = detectEnvValueLeaks(content); // 进程 env 逆向检测
    const allLeaks = scan.leaks.concat(envLeaks);
    return { found: allLeaks.length > 0, leaks: allLeaks };
}
```

---

## §2 crypto.js — AES-256-GCM 加密原语

### 2.1 加密函数签名

```javascript
function encrypt(plaintext, keyBase64)  // → { iv, authTag, ciphertext } (all base64)
function decrypt(ivBase64, authTag, ciphertextBase64, keyBase64) // → plaintext
```

### 2.2 pack 布局

```
[4-byte IV][12-byte authTag][N-byte ciphertext]
```

`encrypt()` 输出三个字段，`decrypt()` 需要这三个字段分别传入（不解包）。

### 2.3 密钥管理原则

- **Key 不离本地**：加密密钥从未离开本地节点
- **每次加密随机 IV**：使用 `crypto.randomBytes(16)` 确保即使相同 plaintext 每次密文也不同
- **GCM 模式**：提供认证加密（ciphertext + authTag），检测篡改

---

## §3 privacyClient.js — 六步隐私计算管线

*（完整六步已记录于 doc 57 §2，此处补充关键设计洞察）*

### 3.1 隐私块嵌入

```javascript
// 关键：将加密任务和执行任务解耦
// Step 1-3: 提交加密任务到 Hub（task 不可读）
// Step 4-5: Hub 在加密环境中执行
// Step 6: 结果加密返回，仅本地可解密
```

### 3.2 与 sanitize 的关系

`sanitize` 是**发布前扫描**，在 capsule 广播到 Hub 之前运行。  
`privacyClient` 是**计算时隐私**，将任务本身外包给 Hub 时不泄露原始数据。

两者形成**纵深防御**：即使 sanitize 漏检，privacyClient 也能保护原始 payload 不被 Hub 看到。

---

## §4 HUB_EVENT_SIGNALS — 隐私事件分类

### 4.1 完整分类表（35+ 种）

| 类别 | 信号 | 含义 |
|------|------|------|
| **dialog** | `privacy_requested` | 用户请求隐私计算 |
| | `privacy_compliance_verified` | 合规性验证通过 |
| | `sensitive_data_encountered` | 遇到敏感数据 |
| **swarm** | `hub_learning_received` | 从 Hub 收到知识 |
| | `hub_knowledge_shared` | 向 Hub 共享知识 |
| | `hub_match_found` | Hub 匹配到候选 |
| **privacy** | `privacy_client_submitted` | 隐私任务已提交 |
| | `privacy_result_retrieved` | 隐私结果已获取 |
| | `encryption_verified` | 加密验证通过 |
| | `key_rotated` | 密钥已轮换 |
| **governance** | `ethics_review_triggered` | 触发伦理审查 |
| | `consent_recorded` | 同意已记录 |
| | `audit_logged` | 操作已审计 |
| **review** | `hub_review_submitted` | Hub review 已提交 |
| | `hub_review_received` | 收到 Hub review |
| **knowledge** | `new_gene_learned` | 学习了新 gene |
| | `skill_distilled` | 技能已蒸馏 |
| | `lesson_integrated` | 教训已整合 |

### 4.2 CE 对应

BlueCortexCE 目前**没有**对应的隐私事件信号体系。CE 的观察注入是旁路的，不涉及 Hub 共享。

**借鉴建议**：
- 在 `ObservationEntity` 中添加 `privacy_flags` JSONB 字段
- 记录观察中是否包含 API key / token / 路径等敏感信息
- 用于审计和脱敏控制

---

## §5 与 solidify 流程的集成

### 5.1 发布前检查序列

```
evolve.js 成功完成 cycle
    ↓
solidify.js 验证 + 写入 capsule
    ↓
sanitize.js fullLeakCheck(capsule_payload)
    ↓ (如果发现 leaks)
    ↓ 阻断发布，报告 leaks
    ↓ (如果没有 leaks)
    ↓
policyCheck.js blast_radius + safety 检查
    ↓
a2a.js 发布资格检查 (score ≥ 0.7 + blast safe + streak ≥ 2)
    ↓
Hub 广播
```

### 5.2 Pre-publish leak check 核心逻辑

`sanitize.js` 在 solidify 之前被调用（见 doc 34 §2.4），确保：
- 所有 API key / token 被 `[REDACTED]` 替换
- 所有 env 值硬编码被检测并报告
- 所有本地路径被匿名化

---

## §6 CE 隐私与脱敏设计建议

### 6.1 借鉴分层

| Evolver 组件 | CE 等效 | 实现优先级 |
|-------------|---------|-----------|
| `redactString()` | **观察脱敏 Service** | P0 |
| `scanForLeaks()` | **发布前安全扫描** | P0 |
| `detectEnvValueLeaks()` | **Config 泄漏检测** | P1 |
| `crypto.js AES-256-GCM` | **敏感字段加密存储**（如 API key 配置） | P2 |
| `HUB_EVENT_SIGNALS` | **隐私事件审计** | P2 |

### 6.2 CE 脱敏实现草稿

```java
// ObservationSanitizer.java
public class ObservationSanitizer {
    private static final Pattern API_KEY_PATTERN =
        Pattern.compile("(sk-[A-Za-z0-9]{20,}|ghp_[A-Za-z0-9]{36,})");

    public String redact(String content) {
        return API_KEY_PATTERN.matcher(content).replaceAll("[REDACTED]");
    }

    public LeakReport scanForLeaks(String content) {
        // 类似 fullLeakCheck 的 pattern 扫描
        // 返回 { found, leaks[] }
    }
}
```

### 6.3 隐私标志设计

```json
// ObservationEntity.extractedData.privacy_flags
{
  "has_api_key": false,
  "has_token": true,
  "has_local_path": true,
  "has_email": false,
  "sanitized": true
}
```

---

## §7 关键设计原则

1. **替换 vs 检测 分离**：替换不可逆（用于发布）、检测可逆（用于扫描）
2. **逆向检测**：env 值硬编码是最隐蔽的泄漏方式，`detectEnvValueLeaks()` 专门解决
3. **纵深防御**：sanitize（前）+ privacyClient（计算时）双重保护
4. **Key 不离本地**：加密密钥本地生成，从不网络传输
5. **模式覆盖全面**：14 种 pattern + 17 种 scanner，覆盖 token/路径/邮箱/私钥/db_url/ssh/内部IP
6. **可配置的 Env 跳过列表**：`ENV_SCAN_SKIP_KEYS` 避免 PATH/SHELL 等无害值干扰

---

**关联文档**：[`57` privacyClient + crypto + HUB_EVENT_SIGNALS 全链路](./57-privacyClient-crypto-HUB_EVENT-wiring-v147.md)；[`34` Solidify 管线端到端](./34-solidify-pipeline-end-to-end.md)；[`43` Privacy Computing + Hub Ecosystem](./43-privacy-computing-and-hub-ecosystem.md)；[`28` Prompt 工程深度（敏感数据参数化）](./28-prompt-engineering-deep-dive.md)
