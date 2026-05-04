# v79 基础设施模块深度分析

**版本**：v79 | **时间**：2026-05-04 | **模块**：paths.js · assetCallLog.js · bridge.js · assets.js · validationReport.js · contentHash.js · envFingerprint.js · deviceId.js · analyzer.js · innovation.js

---

## 1. paths.js — 路径管理体系

**文件**：`src/gep/paths.js`（~130行）| **设计原则**：路径即配置 / Session Scope 隔离 / Parent Git 安全检查

### 1.1 核心路径函数

```js
getRepoRoot()       // evolver 自身目录（不混淆 host project）
getWorkspaceRoot()   // OpenClaw workspace 或 repo root
getLogsDir()        // logs/（可配置）
getMemoryDir()      // memory/（可配置）
getEvolutionDir()   // memory/evolution/（可配置 + scope 隔离）
getGepAssetsDir()   // assets/gep/（可配置 + scope 隔离）
getSkillsDir()      // skills/（可配置）
getNarrativePath()      // evolution_narrative.md
getReflectionLogPath()  // reflection_log.jsonl
```

### 1.2 Parent Git 安全检查

```js
// Safety: check evolver's own directory first to prevent operating on a
// parent repo that happens to contain .git (which could cause data loss
// when git reset --hard runs in the wrong scope).
if (fs.existsSync(path.join(ownDir, '.git'))) {
  return ownDir; // evolver 自己的 .git，直接使用
}
let dir = path.dirname(ownDir);
while (dir !== '/') {
  if (fs.existsSync(path.join(dir, '.git'))) {
    // ⚠️ 检测到 parent .git！默认拒绝，warn 用户
    return ownDir;
  }
  dir = path.dirname(dir);
}
```

**设计意图**：防止 evolver 作为 npm 依赖安装到某个 host project 时，错误地对 host project 的 git 仓库执行 `git reset --hard` 等危险操作。

### 1.3 Session Scope 隔离

```js
function getSessionScope() {
  const raw = String(process.env.EVOLVER_SESSION_SCOPE || '').trim();
  if (!raw) return null;
  // Sanitize: only allow alphanumeric, dash, underscore, dot
  const safe = raw.replace(/[^a-zA-Z0-9_\-\.]/g, '_').slice(0, 128);
  if (!safe || /^\.{1,2}$/.test(safe) || /\.\./.test(safe)) return null;
  return safe;
}
// 使用：
getEvolutionDir()   // → memory/evolution/scopes/{scope}/
getGepAssetsDir()   // → assets/gep/scopes/{scope}/
```

**场景**：Discord 多频道、同一机器多项目隔离，防止跨 scope 污染。

### 1.4 环境变量覆盖

所有路径均可通过环境变量覆盖：

| 默认路径 | 环境变量 |
|---------|---------|
| `{workspace}/memory` | `MEMORY_DIR` |
| `{workspace}/logs` | `EVOLVER_LOGS_DIR` |
| `{repo}/assets/gep` | `GEP_ASSETS_DIR` |
| `{workspace}/skills` | `SKILLS_DIR` |
| `{memory}/evolution` | `EVOLUTION_DIR` |
| workspace root | `OPENCLAW_WORKSPACE` |
| repo root | `EVOLVER_REPO_ROOT` |

### 1.5 CE 借鉴

**P1**：`getRepoRoot()` 的 parent git 安全检查模式 → BlueCortexCE 应在执行 `git reset --hard` 等危险操作前验证 repo root，防止误操作 host project。参考 `processPath` 或创建专用 `validateRepoRoot()`。

**P1**：`getSessionScope()` 的 sanitization 模式 → BlueCortexCE 应在执行写操作前对路径做类似的字符白名单 sanitization（`[^a-zA-Z0-9_\-\.]` → `_`）。

**P2**：`getWorkspaceRoot()` 的 OpenClaw awareness → CE 的 workspace 检测可优先检查 `OPENCLAW_WORKSPACE` 环境变量，与 OpenClaw 生态对齐。

---

## 2. assetCallLog.js — Append-only 审计日志

**文件**：`src/gep/assetCallLog.js`（~130行）| **设计原则**：Append-only / Non-blocking / 多维度过滤

### 2.1 核心接口

```js
logAssetCall(entry)    // 追加单条记录（JSONL，Non-fatal）
readCallLog(opts)      // 读取，支持 run_id/action/last/since 过滤
summarizeCallLog(opts) // CLI 展示用摘要
```

### 2.2 记录结构

```json
{
  "timestamp": "2026-05-04T...",
  "run_id": "run_xxx",
  "action": "hub_search_hit | hub_search_miss | asset_reuse | asset_publish | ...",
  "asset_id": "sha256:...",
  "asset_type": "...",
  "source_node_id": "...",
  "chain_id": "...",
  "score": 0.85,
  "mode": "direct | reference",
  "signals": ["capability_gap", "..."],
  "reason": "..."
}
```

### 2.3 Non-blocking 写入

```js
function logAssetCall(entry) {
  try {
    const logPath = getLogPath();
    ensureDir(logPath);
    const record = { timestamp: new Date().toISOString(), ...entry };
    fs.appendFileSync(logPath, JSON.stringify(record) + '\n', 'utf8');
  } catch (e) {
    // Non-fatal: never block evolution for logging failure
  }
}
```

**设计意图**：审计日志失败永远不能阻塞主循环。

### 2.4 多维度过滤读取

```js
readCallLog({ run_id, action, last: 10, since: '2026-05-01T...' })
// since: ISO date string，精确到毫秒
// last: 取最后 N 条
// action: hub_search_hit | hub_search_miss | asset_reuse | ...
```

### 2.5 摘要统计

```js
summarizeCallLog()
// → { total_entries, unique_assets, unique_runs, by_action: { ... }, entries }
```

### 2.6 CE 借鉴

**P2**：BlueCortexCE 可以引入类似的 Append-only 审计日志（`observation_call_log.jsonl`），记录所有 observation 操作的来源、信号、类型。用于分析观察覆盖率和检索有效性。

**P2**：`logAssetCall` 的 Non-fatal 模式 → CE 的非关键日志操作应使用 try/catch 包裹，永不阻塞主流程。

---

## 3. bridge.js — Prompt Artifact + sessions_spawn 渲染

**文件**：`src/gep/bridge.js`（~90行）| **设计原则**：Artifact 持久化 / Wrapper 协议 / Machine-parseable

### 3.1 writePromptArtifact

```js
writePromptArtifact({ memoryDir, cycleId, runId, prompt, meta })
// 输出：
// - gep_prompt_{cycle}_{run}.txt      (prompt 内容)
// - gep_prompt_{cycle}_{run}.json      (metadata: type/at/cycle_id/run_id/meta)
```

**安全**：cycleId 和 runId 均经过 `replace(/[^a-zA-Z0-9_\-#]/g, '_')` sanitization。

### 3.2 renderSessionsSpawnCall — Wrapper 协议

```js
renderSessionsSpawnCall({ task, agentId, label, cleanup })
// → `sessions_spawn({"task":"...","agentId":"...","cleanup":"...","label":"..."})`
```

**用途**：生成机器可解析的 `sessions_spawn()` 调用字符串，供 wrapper（如 Claude Code 的 CLI Hook）解析并转发给 OpenClaw。wrapper 使用 `lastIndexOf('sessions_spawn(') + JSON.parse` 提取任务。

**设计亮点**：输出纯 JSON 字符串，不是 shell 命令，因此：
- 无 shell 注入风险
- wrapper 可用 `JSON.parse` 安全解析

### 3.3 clip — 截断工具

```js
clip(text, maxChars)
// 截断文本，保留开头，末尾加 '\n...[TRUNCATED]...\n'
// 不同于普通截断，保留开头便于理解上下文
```

### 3.4 CE 借鉴

**P1**：`renderSessionsSpawnCall` 的 JSON parseable 输出模式 → BlueCortexCE 的 OpenClaw 集成（如 cortex-mem-spring-integration）如果需要与 OpenClaw agent 通信，应使用类似的机器可解析协议，而非字符串拼接。

**P2**：`writePromptArtifact` 的配对元数据模式 → CE 可以为每个 context 生成输出（`{prompt.txt, metadata.json}` 配对），便于后续分析和回放。

---

## 4. assets.js — 资产预览格式化 + 归一化

**文件**：`src/gep/assets.js`（~50行）| **设计原则**：防御性格式化 / Schema 标准化

### 4.1 formatAssetPreview

```js
formatAssetPreview(preview)
// 防御性处理 stringified JSON / array / null
// 优先解析 string → 如果是 array 则 pretty-print
// 解析失败则保留原字符串
```

### 4.2 normalizeAsset

```js
normalizeAsset(asset)
// 自动补充 schema_version（SCHEMA_VERSION = '1.6.0'）
// 自动补充 asset_id（computeAssetId）
```

### 4.3 CE 借鉴

**P2**：CE 的 `ObservationEntity` 在存储前可以通过类似的 `normalizeAsset()` 补充 `schema_version` 和 content-hash，既便于调试，又支持内容去重。

---

## 5. validationReport.js — 标准化验证报告

**文件**：`src/gep/validationReport.js`（~80行）| **设计原则**：Schema-versioned / Self-contained / Cross-node 可验证

### 5.1 报告结构

```json
{
  "type": "ValidationReport",
  "schema_version": "1.6.0",
  "id": "vr_1746307200000",
  "gene_id": "...",
  "env_fingerprint": { "device_id": "...", "node_version": "...", ... },
  "env_fingerprint_key": "a1b2c3d4e5f6...",
  "commands": [
    {
      "command": "npm test",
      "ok": true,
      "stdout": "...",
      "stderr": ""
    }
  ],
  "overall_ok": true,
  "duration_ms": 1523,
  "created_at": "2026-05-04T..."
}
```

### 5.2 env_fingerprint_key

来自 `envFingerprint.envFingerprintKey(fp)`，16字符 hex。用于判断两个 ValidationReport 是否来自"同类环境"。

### 5.3 stdout/stderr 截断

每条命令的 stdout 和 stderr 截断到 4000 字符，防止 ValidationReport 过大。

### 5.4 isValidValidationReport

```js
isValidValidationReport(obj)
// type === 'ValidationReport'
// id 非空字符串
// commands 是数组
// overall_ok 是 boolean
```

### 5.5 CE 借鉴

**P0**：ValidationReport 的 schema-versioned + env_fingerprint 模式 → BlueCortexCE 的 `ObservationEntity` 或 `SummaryEntity` 应包含 `schema_version` 字段，便于 API 版本演进时做数据迁移。

**P1**：ValidationReport 的 `env_fingerprint_key` 用于判断环境等价性 → CE 可以为每次 `/api/context/generate` 调用附加环境指纹，支持跨环境复用 context 策略。

---

## 6. contentHash.js — Canonical JSON + SHA-256 内容寻址

**文件**：`src/gep/contentHash.js`（~100行）| **SCHEMA_VERSION = '1.6.0'**

### 6.1 Canonical JSON

```js
canonicalize(obj)
// - boolean: 'true' | 'false'
// - number: NaN/Infinity → 'null'
// - string: JSON.stringify (含引号)
// - array: 递归，逗号分隔
// - object: keys.sort() 后递归，key:value 逗号分隔
```

关键：对象键按字母排序，保证相同内容的对象无论属性顺序如何，canonicalize 结果相同。

### 6.2 computeAssetId

```js
computeAssetId(obj, excludeFields = ['asset_id'])
// 排除 asset_id 自身（自引用字段）
// SHA-256 hex → 'sha256:<hex>'
```

### 6.3 verifyAssetId

```js
verifyAssetId(obj)
// 比较 obj.asset_id 与 computeAssetId(obj)
// 防篡改验证
```

### 6.4 CE 借鉴

**P1**：BlueCortexCE 的 observation 存储可以使用 content-hash 作为天然的 deduplication key。如果两次 observation 内容相同（canonicalize 后相同），则不必重复存储，实现 Append-only 的天然去重。

**P1**：验证方面参考 `verifyAssetId` → CE 可以在 observation 写入时计算 content-hash 并存储，读取时验证完整性。

**P2**：contentHash 的 canonicalize 算法（sorted keys + NaN/Infinity → null）可直接移植到 CE，用于 observation 的内容规范化。

---

## 7. envFingerprint.js — 环境指纹捕获

**文件**：`src/gep/envFingerprint.js`（~110行）| **设计原则**：Own package.json 优先 / Hashed hostname / 环境等价判断

### 7.1 捕获字段

```js
captureEnvFingerprint() → {
  device_id,           // 设备标识（getDeviceId()）
  node_version,        // process.version
  platform, arch,      // darwin/linux/win, arm64/x64
  os_release,          // os.release()
  hostname,            // SHA-256 哈希（不暴露明文主机名）
  evolver_version,     // 自身 package.json 的 version（不是 host project）
  client,              // package.json name
  client_version,      // 同 evolver_version
  region,              // EVOLVER_REGION env var（5字符）
  cwd,                 // SHA-256 哈希（不暴露工作目录）
  container,           // isContainer()
  captured_at,        // ISO timestamp
}
```

### 7.2 Own Package.json 优先解析

```js
// Read evolver's own package.json via __dirname so that npm-installed
// deployments report the correct evolver version. getRepoRoot() walks
// up to the nearest .git directory, which resolves to the HOST project
// when evolver is an npm dependency -- producing a wrong name/version.
const ownPkgPath = path.resolve(__dirname, '..', '..', 'package.json');
// 如果 ownPkgPath 的 package.json 无 version → fallback 到 repoRoot 的
```

**问题背景**：evolver 作为 npm 依赖安装到 host project 时，`getRepoRoot()` 会上溯到最近的 `.git` 目录，指向 host project 而非 evolver 自身。使用 `__dirname` 相对于 evolver 的实际位置读取 package.json，解决了版本号错误问题。

### 7.3 envFingerprintKey — 16字符环境等价键

```js
envFingerprintKey(fp) → SHA-256([device_id|node_version|platform|arch|hostname|client|client_version].join('|')).slice(0, 16)
// 16字符足够短，适合显示和比较
// 两个 fp key 相同 → 同一环境类
```

### 7.4 isSameEnvClass

```js
isSameEnvClass(fpA, fpB) → envFingerprintKey(fpA) === envFingerprintKey(fpB)
```

### 7.5 CE 借鉴

**P0**：BlueCortexCE 的 `ObservationEntity` 应包含 `runtime_env` 字段（JSON object），记录 node_version、platform、java_version、postgres 版本等环境信息。这对于跨环境观察的可靠性评估至关重要（类似 GDI - Genetic Diffusion Index）。

**P1**：`isSameEnvClass` 模式 → CE 的 context 策略可以按环境分类管理：在 A 环境有效的 context 策略，在 B 环境（不同 OS/版本）可能不适用。

**P1**：hostname 和 cwd 的 SHA-256 哈希 → CE 在记录环境指纹时，不应存储明文敏感路径或主机名，应使用哈希。

---

## 8. deviceId.js — 7层 Fallback 设备标识

**文件**：`src/gep/deviceId.js`（~200行）| **设计原则**：容器感知 / 双路径持久化 / 永不丢失身份

### 8.1 7层 Fallback 链

```
1. EVOMAP_DEVICE_ID env var        (explicit override, recommended for containers)
2. ~/.evomap/device_id            (persisted from previous run)
3. <project>/.evomap_device_id    (container fallback, volume-mounted project)
4. /etc/machine-id                 (Linux)
5. IOPlatformUUID                  (macOS via ioreg)
6. Docker/OCI container ID          (from /proc/self/cgroup or /proc/self/mountinfo)
7. hostname + MAC addresses        (network-based fallback)
8. crypto.randomBytes(16)          (last resort, persisted immediately)
```

### 8.2 容器检测 (isContainer)

```js
isContainer() // 3路检测：
// 1. fs.existsSync('/.dockerenv')
// 2. /proc/1/cgroup 含 docker|kubepods|containerd|cri-o|lxc|ecs
// 3. fs.existsSync('/run/.containerenv')
```

### 8.3 Container ID 提取

```js
readContainerId()
// Method 1: /proc/self/cgroup (cgroup v1)
// Method 2: /proc/self/mountinfo (cgroup v2 / containerd)
// Method 3: hostname（Docker 默认用短 container ID 作 hostname）
```

### 8.4 MAC 地址收集

```js
getMacAddresses()
// os.networkInterfaces() → 过滤 internal=false && mac!='00:00:00:00:00:00'
// sort → 顺序无关的 MAC 列表
```

### 8.5 双路径持久化

```js
// Primary: ~/.evomap/device_id (mode 0o600)
try { fs.writeFileSync(DEVICE_ID_FILE, id); return; } catch {}

// Fallback: <project>/.evomap_device_id (for ephemeral $HOME containers)
// 如果 primary 失败（$HOME 是 tmpfs），写 project-local 文件
```

**容器场景**：当 `$HOME` 是 tmpfs（重启丢失）但 project 目录是 volume mount 时，fallback 路径可以持久化设备 ID。

### 8.6 生成逻辑

```js
generateDeviceId()
→ machineId (SHA-256('evomap:' + machineId)).slice(0, 32)      // Linux/macOS
→ containerId (SHA-256('evomap:container:' + containerId)).slice(0, 32)  // 容器
→ macs (SHA-256('evomap:' + hostname + '|' + macs.join(','))).slice(0, 32)  // 网络
→ randomBytes(16).toString('hex')  // 最后手段
```

### 8.7 CE 借鉴

**P1**：BlueCortexCE 应实现类似的设备标识系统，支持容器化部署场景。当前 Java 代码中没有 `deviceId` 概念，如果需要多设备管理，应参考此 7 层 fallback 链。

**P2**：容器双路径持久化模式 → CE 的设备标识可以同时尝试 `~/.bluecortexce/device_id` 和 `{workdir}/.bluecortexce_device_id`。

**P2**：`isContainer()` 检测模式 → CE 在容器中运行时，应使用容器感知的配置路径（如 `/dev/shm` 替代 `/tmp`）。

---

## 9. analyzer.js — MEMORY.md 失败模式解析

**文件**：`src/gep/analyzer.js`（~50行）| **设计原则**：Meta-learning / 轻量解析 / 非侵入

### 9.1 解析逻辑

```js
// 从 MEMORY.md 提取 **Fix** 条目
const failureRegex = /\|\s*\*\*F\d+\*\*\s*\|\s*Fix\s*\|\s*(.*?)\s*\|\s*\*\*(.*?)\*\*\s*\((.*?)\)\s*\|/g;
// 匹配格式：
// | **F1** | Fix | <summary> | **<detail>** (<persona>) |
```

### 9.2 输出

```js
analyzeFailures() → {
  status: 'success' | 'skipped',
  count: failures.length,
  failures: failures.slice(0, 3)  // 仅返回 top 3 供 prompt context
}
```

### 9.3 CLI

```bash
node analyzer.js
# → {"status":"success","count":5,"failures":[{"summary":"...","detail":"..."}]}
```

### 9.4 CE 借鉴

**P1**：BlueCortexCE 可以从 `MEMORY.md` 的失败记录中学习，类似于 `analyzer.js` 从 MEMORY.md 提取 Fix 条目。CE 的 `SummaryEntity` 可以包含"从失败中学习"的元级分析。

**P2**：Top 3 限制 → CE 在生成 context 时，如果包含历史失败记录，应限制数量（避免 prompt 过长），优先展示最近和最重要的失败。

---

## 10. innovation.js — 技能缺口驱动创意生成

**文件**：`src/ops/innovation.js`（~100行）| **设计原则**：弱领域驱动 / Gap-based / Concrete suggestions

### 10.1 技能分类体系

```js
const categories = {
  'feishu':      s => s.startsWith('feishu-'),
  'dev':         s => s.startsWith('git-') || s.startsWith('code-') || s.includes('lint') || s.includes('test'),
  'media':       s => s.includes('image') || s.includes('video') || s.includes('music') || s.includes('voice'),
  'security':    s => s.includes('security') || s.includes('audit') || s.includes('guard'),
  'automation':  s => s.includes('auto-') || s.includes('scheduler') || s.includes('cron'),
  'data':        s => s.includes('db') || s.includes('store') || s.includes('cache') || s.includes('index'),
};
```

### 10.2 创意生成策略

```js
// 策略1: Fill the Gap（弱势领域填充）
if (weakAreas.includes('security')) {
  ideas.push("- Security: Implement a 'dependency-scanner' skill...");
  ideas.push("- Security: Create a 'permission-auditor'...");
}

// 策略2: Optimization（过度拥挤优化）
if (skills.length > 50) {
  ideas.push("- Optimization: Identify and deprecate unused skills...");
  ideas.push("- Optimization: Merge similar skills...");
}

// 策略3: Meta（自我增强）
ideas.push("- Meta: Enhance the Evolver's self-reflection...");
```

### 10.3 输出限制

```js
return ideas.slice(0, 3); // 最多3条，避免 prompt 过长
```

### 10.4 CE 借鉴

**P1**：BlueCortexCE 的 StructuredExtractionService（或未来的规划模块）可以参考"弱领域驱动"策略：分析当前 observation/summary 的覆盖盲区，生成有针对性的数据采集建议。例如：如果长期没有 security-related observations，系统可以建议下次对话中关注安全相关话题。

**P2**：`skills.length > 50` 的优化建议模式 → CE 可以检测"observation type 单一化"（如长期只有 coding 类型），建议引入多样化的观察类型。

---

## 11. 总体设计模式总结

| 模块 | 模式 | CE 优先级 |
|------|------|---------|
| paths.js | Parent git 安全检查 | P1 |
| paths.js | Session scope sanitization | P1 |
| paths.js | 环境变量覆盖所有路径 | P2 |
| assetCallLog.js | Append-only Non-blocking 审计 | P2 |
| bridge.js | JSON parseable sessions_spawn 协议 | P1 |
| bridge.js | 配对 artifact (prompt.txt + metadata.json) | P2 |
| contentHash.js | Canonical JSON (sorted keys) | P1 |
| contentHash.js | 内容去重 via SHA-256 | P1 |
| envFingerprint.js | Own package.json 优先解析 | P1 |
| envFingerprint.js | Hashed hostname/cwd（隐私保护） | P1 |
| envFingerprint.js | 16字符 envFingerprintKey | P2 |
| deviceId.js | 7层 fallback 链 | P1 |
| deviceId.js | 容器双路径持久化 | P2 |
| deviceId.js | isContainer() 三路检测 | P2 |
| analyzer.js | Top-N 限制（避免 prompt 过长） | P2 |
| innovation.js | 弱领域驱动创意生成 | P2 |

---

## 12. 下一步

- **接力建议**：深入分析 `solidify.js`（57KB，最大模块）源码结构
- **并行建议**：分析 `ops/self_repair.js`（未在 ops/index.js 中导出，可能是独立模块）
- **对比**：deviceId.js 的 7 层 fallback 与 envFingerprint.js 的环境捕获是否可合并为单一 Identity 模块
