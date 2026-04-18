<!-- part 7/8: auto-split from evolver-memory-analysis.md — see index.md -->

## 66. idleScheduler.js — OMLS 空闲调度器（v1.2 新增）

**文件**: `src/gep/idleScheduler.js` (171 lines)

### 66.1 设计背景：OMLS 概念

idleScheduler 实现了 **OMLS（Observer Model with Limited States）空闲调度**——当检测到用户不活跃时，Evolver 可以运行更重的操作（distillation、reflection）；用户活跃时只做轻量信号收集。

### 66.2 系统空闲时间检测

**文件**: `idleScheduler.js:30-90`

```javascript
function getSystemIdleSeconds() {
  const platform = process.platform;
  
  if (platform === 'win32') {
    // Windows: PowerShell 调用 GetLastInputInfo
    const psCode = [...].join('\n');
    return parseInt(execSync('powershell ...', { timeout: 10000 }), 10);
  } else if (platform === 'darwin') {
    // macOS: ioreg 查询 HIDIdleTime
    const result = execSync('ioreg -c IOHIDSystem | grep HIDIdleTime', { timeout: 5000 });
    return Math.floor(parseInt(match[1], 10) / 1000000000);
  } else if (platform === 'linux') {
    // Linux: xprintidle 或 /proc/interrupts
    const result = execSync('xprintidle 2>/dev/null || echo -1', { timeout: 5000 });
    return Math.floor(parseInt(result, 10) / 1000);
  }
  return -1;  // 不支持
}
```

### 66.3 强度级别（Intensity Levels）

**文件**: `idleScheduler.js:95-115`

```javascript
const IDLE_THRESHOLD_SECONDS = 300;      // 5 分钟
const DEEP_IDLE_THRESHOLD_SECONDS = 1800; // 30 分钟

function determineIntensity(idleSeconds) {
  if (idleSeconds < 0) return 'normal';
  if (idleSeconds >= DEEP_IDLE_THRESHOLD_SECONDS) return 'deep';
  if (idleSeconds >= IDLE_THRESHOLD_SECONDS) return 'aggressive';
  return 'normal';
}
```

| 强度 | 空闲时间 | Sleep Multiplier | 应该蒸馏 | 应该反思 | 应该深度进化 |
|------|---------|-----------------|---------|---------|------------|
| `normal` | < 5 分钟 | 1x | ❌ | ❌ | ❌ |
| `aggressive` | 5-30 分钟 | 0.5x | ✅ | ✅ | ❌ |
| `deep` | > 30 分钟 | 0.25x | ✅ | ✅ | ✅ |

### 66.4 调度推荐结构

**文件**: `idleScheduler.js:120-160`

```javascript
function getScheduleRecommendation() {
  return {
    enabled: true,
    idle_seconds: 450,
    intensity: 'aggressive',
    sleep_multiplier: 0.5,        // 减少等待间隔
    should_distill: true,         // 可以运行 distillation
    should_reflect: true,         // 可以运行 reflection
    should_deep_evolve: false,
  };
}
```

**Evolver 为什么这样做**：
- **用户空闲时**运行重量级操作，不影响用户体验
- **用户活跃时**只做信号收集，避免资源竞争
- **自适应强度**：空闲越久，可以运行的操作越重

### 66.5 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 跨平台空闲检测 | Windows/macOS/Linux 各自实现 | **高优先级**: BlueCortexCE 的服务可检测用户活跃状态 | 高 |
| 强度级别 | normal / aggressive / deep | **高优先级**: BlueCortexCE 的巡检任务可根据系统负载动态调整 | 高 |
| Sleep Multiplier | 空闲时减少等待 | **中优先级**: BlueCortexCE 的 cron 调度可参考空闲状态 | 中 |
| 重操作门控 | distillation / reflection 门控 | **中优先级**: BlueCortexCE 的 LLM 调用可按系统状态分级 | 中 |
| OMLS 概念 | 有限状态机的空闲调度 | **中优先级**: BlueCortexCE 的任务队列可实现类似状态机 | 中 |

---

## 67. localStateAwareness.js — 本地状态感知（v1.2 新增）

**文件**: `src/gep/localStateAwareness.js` (200 lines)

### 67.1 设计背景

localStateAwareness 是 Evolver 的**去重防护层**——在执行任何注册/配置/创建操作前，检查本地状态，避免重复创建已有资源。

### 67.2 五大状态捕获

**文件**: `localStateAwareness.js:90-195`

```javascript
function captureLocalState() {
  var sections = [];
  
  sections.push('[Node Identity]');
  sections = sections.concat(captureNodeIdentity());
  // 输出：
  // - Node ID: node_abc123 (REGISTERED -- do NOT re-register)
  // - Node Secret: PRESENT (authenticated -- do NOT request new secret)
  
  sections.push('[Environment Config]');
  sections = sections.concat(captureEnvConfig());
  // 输出：
  // - Env configured: A2A_NODE_ID, A2A_HUB_URL
  // - Env not set: GITHUB_TOKEN
  // - .env file: EXISTS at /path/.env
  
  sections.push('[Evolution State]');
  sections = sections.concat(captureEvolutionState());
  // 输出：
  // - Evolution cycles completed: 47
  // - Last evolution run: 3600s ago
  // - Personality: rigor=0.75 creativity=0.35 risk_tolerance=0.4
  
  sections.push('[Memory & Knowledge]');
  sections = sections.concat(captureMemoryState());
  // 输出：
  // - Memory directory: EXISTS at memory/
  // - MEMORY.md: 2048 bytes
  // - Memory graph: 8192 bytes
  
  sections.push('[Skills]');
  sections = sections.concat(captureSkillsState());
  // 输出：
  // - Installed skills: 23 (at skills/)
  
  return sections.join('\n');
}
```

### 67.3 节点身份感知

**文件**: `localStateAwareness.js:35-55`

```javascript
function captureNodeIdentity() {
  const nodeId = process.env.A2A_NODE_ID || _readFileSafe(NODE_ID_FILE);
  if (nodeId) {
    lines.push('- Node ID: ' + nodeId + ' (REGISTERED -- do NOT re-register)');
  } else {
    lines.push('- Node ID: NOT SET (registration may be needed)');
  }
  
  const hasSecret = !!process.env.A2A_NODE_SECRET || _fileExists(NODE_SECRET_FILE);
  if (hasSecret) {
    lines.push('- Node Secret: PRESENT (authenticated -- do NOT request new secret)');
  } else {
    lines.push('- Node Secret: MISSING (hello handshake may be needed)');
  }
}
```

### 67.4 配置文件检测

**文件**: `localStateAwareness.js:55-75`

```javascript
function captureEnvConfig() {
  const A2A_ENV_KEYS = [
    'A2A_NODE_ID', 'A2A_HUB_URL', 'A2A_NODE_SECRET',
    'AGENT_NAME', 'EVOLVE_STRATEGY', 'WORKER_ENABLED',
    'EVOLVER_SESSION_SCOPE', 'GITHUB_TOKEN',
  ];
  
  // 检测哪些环境变量已配置
  for (const key of A2A_ENV_KEYS) {
    if (process.env[key]) configured.push(key);
    else missing.push(key);
  }
  
  // 检测 .env 文件是否存在
  const envFile = path.join(repoRoot, '.env');
  if (fs.existsSync(envFile)) {
    lines.push('- .env file: EXISTS at ' + envFile);
  } else {
    lines.push('- .env file: MISSING at ' + envFile);
  }
}
```

### 67.5 进化状态感知

**文件**: `localStateAwareness.js:75-90`

```javascript
function captureEvolutionState() {
  const statePath = path.join(evoDir, 'evolution_state.json');
  const state = JSON.parse(fs.readFileSync(statePath, 'utf8'));
  
  lines.push('- Evolution cycles completed: ' + state.cycleCount);
  lines.push('- Last evolution run: ' + ago + 's ago');
  
  // personality 状态
  const personality = JSON.parse(fs.readFileSync(personalityPath, 'utf8'));
  lines.push('- Personality: rigor=' + p.rigor + ' creativity=' + p.creativity ...);
}
```

### 67.6 本地状态注入 GEP Prompt

**文件**: `prompt.js:320-340`

Evolver 在 GEP Prompt 中注入本地状态：

```javascript
LOCAL STATE AWARENESS (CRITICAL -- PREVENT DUPLICATE ACTIONS):
Before taking any setup, registration, or configuration action,
CHECK the Local State section in the execution context.
If a resource already exists (node registered, secret present, env configured),
DO NOT recreate it.

If you cannot find a configuration value, check these locations FIRST:
  1. ~/.evomap/          (node_id, node_secret)
  2. <repo>/.env         (A2A_NODE_ID, A2A_HUB_URL, A2A_NODE_SECRET)
  3. workspace/memory/   (MEMORY.md, evolution state files)
  4. workspace/skills/   (installed skills)
Redundant registration = WASTED CYCLE.
```

**Evolver 为什么这样做**：
- **防止重复注册**：Hub handshake 幂等，重复调用浪费 credits
- **防止重复配置**：.env 存在就不重新创建
- **防止重复创建技能**：技能已安装就不重新创建

### 67.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 五大状态捕获 | Node Identity / Env Config / Evolution / Memory / Skills | **高优先级**: BlueCortexCE 的巡检应能捕获完整系统状态 | 高 |
| 已注册标记 | "(REGISTERED -- do NOT re-register)" | **高优先级**: BlueCortexCE 的 SDK 应跟踪"已完成操作"避免重复 | 高 |
| 配置存在性检测 | .env / node_secret / memory/ 文件夹 | **高优先级**: BlueCortexCE 的初始化应检测而非盲目创建 | 高 |
| 浪费 cycle 警告 | "Redundant registration = WASTED CYCLE" | **高优先级**: BlueCortexCE 的日志应明确标识重复操作 | 高 |
| 多位置查询 | ~/.evomap → .env → workspace/memory → skills | **中优先级**: BlueCortexCE 的配置解析应有优先级链 | 中 |

---

## 68. gitOps.js — Git 操作与原子回滚（v1.3 新增）

**文件**: `src/gep/gitOps.js` (258 lines)

### 68.1 设计定位

gitOps.js 是 Evolver 的 **Git 操作工具层**，从 solidify.js 中分离出来，专门负责：
1. Git 命令执行（`runCmd` / `tryRunCmd`）
2. 变更文件列表捕获（`gitListChangedFiles`）
3. Diff 快照（`captureDiffSnapshot`）
4. 关键文件保护（`isCriticalProtectedPath`）
5. 原子回滚（`rollbackTracked` + `rollbackNewUntrackedFiles`）

### 68.2 变更文件捕获（gitListChangedFiles）

**文件**: `gitOps.js:55-72`

```javascript
function gitListChangedFiles({ repoRoot }) {
  const files = new Set();
  // 1. unstaged changes
  const s1 = tryRunCmd('git diff --name-only', { cwd: repoRoot });
  // 2. staged changes
  const s2 = tryRunCmd('git diff --cached --name-only', { cwd: repoRoot });
  // 3. untracked files (new files)
  const s3 = tryRunCmd('git ls-files --others --exclude-standard', { cwd: repoRoot });
  return Array.from(files);
}
```

**Evolver 为什么这样做**：变更文件列表是 blast radius 计算的基础。需要同时捕获 staged、unstaged 和 untracked 三类变更。

### 68.3 Diff 快照（captureDiffSnapshot）

**文件**: `gitOps.js:75-90`

```javascript
const DIFF_SNAPSHOT_MAX_CHARS = 8000;

function captureDiffSnapshot(repoRoot) {
  const parts = [];
  const unstaged = tryRunCmd('git diff', { cwd: repoRoot, timeoutMs: 30000 });
  if (unstaged.ok && unstaged.out) parts.push(unstaged.out);
  const staged = tryRunCmd('git diff --cached', { cwd: repoRoot, timeoutMs: 30000 });
  if (staged.ok && staged.out) parts.push(staged.out);
  let combined = parts.join('\n');
  if (combined.length > DIFF_SNAPSHOT_MAX_CHARS) {
    combined = combined.slice(0, DIFF_SNAPSHOT_MAX_CHARS) + '\n... [TRUNCATED]';
  }
  return combined || '';
}
```

**Evolver 为什么这样做**：`captureDiffSnapshot` 在 `rollback` **之前**调用，用于保存失败时的完整变更内容（FailedCapsule 的一部分）。

### 68.4 关键文件保护（CRITICAL_PROTECTED）

**文件**: `gitOps.js:95-118`

```javascript
const CRITICAL_PROTECTED_PREFIXES = [
  'skills/feishu-evolver-wrapper/',
  'skills/feishu-common/',
  'skills/feishu-post/',
  // ... 10 个 skill 目录
];

const CRITICAL_PROTECTED_FILES = [
  'MEMORY.md', 'SOUL.md', 'IDENTITY.md', 'AGENTS.md', 'USER.md',
  'HEARTBEAT.md', 'RECENT_EVENTS.md', 'TOOLS.md',
  'TROUBLESHOOTING.md', 'openclaw.json', '.env', 'package.json',
];

function isCriticalProtectedPath(relPath) {
  const rel = normalizeRelPath(relPath);
  for (const prefix of CRITICAL_PROTECTED_PREFIXES) {
    if (rel === prefix || rel.startsWith(prefix)) return true;
  }
  for (const f of CRITICAL_PROTECTED_FILES) {
    if (rel === f) return true;
  }
  return false;
}
```

**Evolver 为什么这样做**：即使在回滚模式下，**关键 agent 文件**（MEMORY.md、openclaw.json、skills 目录等）也**不能被删除或覆盖**。这是安全防护的最后一道防线。

### 68.5 三模式回滚（rollbackTracked）

**文件**: `gitOps.js:120-155`

```javascript
const mode = String(process.env.EVOLVER_ROLLBACK_MODE || 'hard').toLowerCase();

if (mode === 'none') {
  // 不回滚，仅记录
  return;
}

if (mode === 'stash') {
  const stashRef = 'evolver-rollback-' + Date.now();
  const result = tryRunCmd('git stash push -m "' + stashRef + '" --include-untracked');
  // 可通过 git stash list 恢复
  return;
}

// 默认 hard 模式
tryRunCmd('git restore --staged --worktree .');
tryRunCmd('git reset --hard');
```

**Evolver 为什么这样做**：
- `none`：诊断模式，保留所有变更用于调试
- `stash`：安全模式，变更存入 stash 可恢复
- `hard`：破坏性模式，彻底清除所有变更

### 68.6 新文件回滚（rollbackNewUntrackedFiles）

**文件**: `gitOps.js:157-215`

```javascript
function rollbackNewUntrackedFiles({ repoRoot, baselineUntracked }) {
  const baseline = new Set(baselineUntracked);
  const current = gitListUntrackedFiles(repoRoot);
  // 仅删除本次运行新增的文件（不在 baseline 中的）
  const toDelete = current.filter(f => !baseline.has(f));
  
  for (const rel of toDelete) {
    // 跳过关键保护文件
    if (isCriticalProtectedPath(safeRel)) { skipped.push(safeRel); continue; }
    // 路径穿越防护
    if (!normAbs.startsWith(normRepo + path.sep)) continue;
    // 删除文件
    fs.unlinkSync(normAbs);
  }
  
  // 清理空目录（从深到浅排序，避免误删父目录）
  for (const dir of sortedDirs) {
    if (fs.readdirSync(dirAbs).length === 0) fs.rmdirSync(dirAbs);
  }
}
```

**关键安全措施**：
1. **Baseline 比较**：只删除"本次运行新增"的文件，baseline 中已有的文件不碰
2. **关键文件跳过**：`isCriticalProtectedPath` 保护所有 skills 目录和 agent 文件
3. **路径穿越防护**：确保 `normAbs` 在 `repoRoot` 子树下
4. **空目录清理**：从深到浅删除（避免删了子目录后又删父目录的误判）

### 68.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 关键文件保护 | skills/ + MEMORY.md 等列表保护 | **高优先级**: BlueCortexCE 的回滚操作应保护用户的关键文件 | 高 |
| 三模式回滚 | none / stash / hard | **高优先级**: BlueCortexCE 的危险操作应有回滚模式开关 | 高 |
| Baseline 比较 | 只删除新增文件 | **高优先级**: BlueCortexCE 的清理操作应比较"操作前后"状态 | 高 |
| 路径穿越防护 | `normAbs.startsWith(normRepo + path.sep)` | **高优先级**: BlueCortexCE 的文件操作应防路径穿越 | 高 |
| Diff 快照保存 | rollback 前保存 diff_snapshot | **高优先级**: BlueCortexCE 的失败记录应保存完整的变更上下文 | 高 |
| 空目录清理 | 从深到浅排序删除 | **中优先级**: BlueCortexCE 的文件清理应有目录树清理逻辑 | 中 |

---

## 69. bridge.js — 跨 Agent 协作桥接（v1.3 新增）

**文件**: `src/gep/bridge.js` (71 lines)

### 69.1 设计定位

bridge.js 实现 **Bridge 模式**——当 Evolver 需要调用外部 Agent（Claude Code、OpenClaw subagent 等）执行具体任务时，通过 `sessions_spawn` 桥接，并在本地记录 Prompt Artifact。

### 69.2 Prompt Artifact 持久化

**文件**: `bridge.js:25-60`

```javascript
function writePromptArtifact({ memoryDir, cycleId, runId, prompt, meta }) {
  const safeCycle = String(cycleId || 'cycle').replace(/[^a-zA-Z0-9_\-#]/g, '_');
  const safeRun = String(runId || Date.now()).replace(/[^a-zA-Z0-9_\-]/g, '_');
  const base = `gep_prompt_${safeCycle}_${safeRun}`;
  
  const promptPath = path.join(dir, base + '.txt');
  const metaPath = path.join(dir, base + '.json');
  
  fs.writeFileSync(promptPath, String(prompt || ''), 'utf8');
  fs.writeFileSync(metaPath, JSON.stringify({
    type: 'GepPromptArtifact',
    at: nowIso(),
    cycle_id: cycleId,
    run_id: runId,
    prompt_path: promptPath,
    meta: meta && typeof meta === 'object' ? meta : null,
  }, null, 2) + '\n', 'utf8');
  
  return { promptPath, metaPath };
}
```

**Evolver 为什么这样做**：
- Prompt 是发送给 LLM 的"决策依据"，需要持久化用于审计
- Meta JSON 记录元数据（时间戳、cycle ID、run ID）
- 两者分离：纯文本 prompt + 结构化 meta

### 69.3 sessions_spawn 调用渲染

**文件**: `bridge.js:62-80`

```javascript
function renderSessionsSpawnCall({ task, agentId, label, cleanup }) {
  // 输出 JSON 格式的调用，wrapper 可用 JSON.parse 解析
  const payload = JSON.stringify({ task: t, agentId: a, cleanup: c, label: l });
  return `sessions_spawn(${payload})`;
}
```

**Evolver 为什么这样做**：
- 输出 `sessions_spawn(JSON.stringify({...}))` 格式，wrapper 可用 `JSON.parse` 提取参数
- 比正则提取更可靠——避免特殊字符导致的解析错误
- `cleanup: 'delete'` 确保 subagent 会话执行后自动清理

### 69.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| Prompt Artifact 持久化 | GEP Prompt → gep_prompt_${cycle}_${run}.txt | **高优先级**: BlueCortexCE 的 context generate 应能持久化输入 Prompt | 高 |
| Meta JSON 分离 | 纯文本 + 结构化 JSON 分离 | **中优先级**: BlueCortexCE 的日志应分离"内容"和"元数据" | 中 |
| JSON 格式的 spawn call | `sessions_spawn(JSON.stringify({...}))` | **高优先级**: BlueCortexCE 的 subagent 调用应使用 JSON 解析而非正则 | 高 |
| 清理策略 | cleanup: 'delete' 自动清理会话 | **中优先级**: BlueCortexCE 的 subagent 应有会话生命周期管理 | 中 |

---

## 70. a2a.js — A2A 资产广播与置信度管理（v1.3 新增）

**文件**: `src/gep/a2a.js` (193 lines)

### 70.1 设计定位

a2a.js 是 **A2A 资产层**的轻量工具模块，负责：
1. 外部资产置信度降级（`lowerConfidence`）
2. 广播资格判定（`isCapsuleBroadcastEligible` / `isGeneBroadcastEligible`）
3. A2A 消息解析（`parseA2AInput`）

### 70.2 外部资产置信度降级（lowerConfidence）

**文件**: `a2a.js:35-70`

```javascript
function lowerConfidence(asset, opts) {
  var factor = Number.isFinite(Number(opts.factor)) ? Number(opts.factor) : 0.6;
  var receivedFrom = opts.source || 'external';
  var receivedAt = opts.received_at || nowIso();
  
  var cloned = JSON.parse(JSON.stringify(asset || {}));
  if (!isAllowedA2AAsset(cloned)) return null;
  
  if (cloned.type === 'Capsule') {
    if (typeof cloned.confidence === 'number')
      cloned.confidence = clamp01(cloned.confidence * factor);
  }
  
  cloned.a2a = {
    status: 'external_candidate',
    source: receivedFrom,
    received_at: receivedAt,
    confidence_factor: factor,
  };
  
  return cloned;
}
```

**Evolver 为什么这样做**：从 Hub 获取的外部资产，其 `confidence` 需要降权（factor = 0.6）。这是"信任递减"原则——外部资产不如本地验证过的资产可信。

### 70.3 Capsule 广播资格判定

**文件**: `a2a.js:90-115`

```javascript
function isCapsuleBroadcastEligible(capsule, opts) {
  if (!capsule || capsule.type !== 'Capsule') return false;
  
  // 1. 评分门槛：score >= 0.7
  var score = capsule.outcome?.score;
  if (score == null || score < 0.7) return false;
  
  // 2. Blast radius 安全检查
  var blast = capsule.blast_radius || capsule.outcome?.blast_radius;
  if (!isBlastRadiusSafe(blast)) return false;
  
  // 3. 连续成功 streak >= 2
  var streak = computeCapsuleSuccessStreak({ capsuleId: capsule.id, events });
  if (streak < 2) return false;
  
  return true;
}
```

**三门控设计**：
- **评分门槛**：防止低质量 Capsule 污染 Hub
- **Blast radius 检查**：影响范围过大的 Capsule 不适合共享（可能包含敏感项目代码）
- **连续成功 streak**：确保不是偶然成功，而是稳定可复现

### 70.4 Gene 广播资格判定

**文件**: `a2a.js:118-130`

```javascript
function isGeneBroadcastEligible(gene) {
  if (!gene || gene.type !== 'Gene') return false;
  if (!gene.id || typeof gene.id !== 'string') return false;
  if (!Array.isArray(gene.strategy) || gene.strategy.length === 0) return false;
  if (!Array.isArray(gene.validation) || gene.validation.length === 0) return false;
  return true;
}
```

**Evolver 为什么这样做**：Gene 必须有 `strategy`（策略步骤）和 `validation`（验证命令）才能被 Hub 评审。没有验证步骤的 Gene 质量不可靠。

### 70.5 A2A 消息解析（parseA2AInput）

**文件**: `a2a.js:135-175`

```javascript
function parseA2AInput(text) {
  // 支持多种格式：
  // 1. JSON array: [...]
  // 2. JSON object: {...}
  // 3. JSONL 格式: 每行一个 JSON 对象
  
  var raw = String(text || '').trim();
  if (!raw) return [];
  
  try {
    var maybe = JSON.parse(raw);
    if (Array.isArray(maybe)) {
      return maybe.map(item => unwrapAssetFromMessage(item) || item).filter(Boolean);
    }
    if (maybe && typeof maybe === 'object') {
      var unwrapped = unwrapAssetFromMessage(maybe);
      return unwrapped ? [unwrapped] : [maybe];
    }
  } catch {}
  
  // JSONL fallback
  var lines = raw.split('\n').map(l => l.trim()).filter(Boolean);
  var items = [];
  for (const line of lines) {
    try {
      var obj = JSON.parse(line);
      items.push(unwrapAssetFromMessage(obj) || obj);
    } catch { continue; }
  }
  return items;
}
```

**Evolver 为什么这样做**：Hub 返回的资产数据可能有多种格式（JSON array、JSON object、JSONL），统一解析层让下游代码不需要处理格式差异。

### 70.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 外部资产降权 | confidence × 0.6 | **高优先级**: BlueCortexCE 从外部（Hub/其他节点）获取的检索结果应降权 | 高 |
| 广播三门控 | score ≥ 0.7 + blast radius + streak ≥ 2 | **高优先级**: BlueCortexCE 的"发布/共享"功能应有质量门槛 | 高 |
| Gene 必填字段 | strategy + validation 必须存在 | **高优先级**: BlueCortexCE 的 Summary 如果要共享，应有最低字段要求 | 高 |
| 多格式解析 | JSON array / object / JSONL 统一 | **中优先级**: BlueCortexCE 的 API 应能处理多种输入格式 | 中 |
| confidence_factor 记录 | 记录降权因子 | **中优先级**: BlueCortexCE 的外部数据应有"来源可信度"元数据 | 中 |

---

## 71. privacyClient.js — 隐私计算与密封执行（v1.3 新增）

**文件**: `src/gep/privacyClient.js` (216 lines)

### 71.1 设计背景

privacyClient.js 实现 **隐私计算协议**——当某个任务需要处理敏感数据（如代码审计、日志分析）但又希望借助 Hub 的知识时，可以使用"密封执行"（Sealed Execution）模式：
1. 数据在本地加密后上传到 Hub
2. Hub 在加密 blob 上执行密封工具（不知道明文内容）
3. 结果返回本地后解密
4. Hub 始终不知道原始数据内容

### 71.2 核心流程

```
本地                    Hub                     本地
 |                       |                       |
 |-- submitPrivacyTask --&gt;|                       |
 |                       |                       |
 |&lt;-- taskId ----------|                       |
 |                       |                       |
 |-- uploadEncryptedBlob -| (blobId)             |
 |                       |                       |
 |            (Hub 对 blob 执行 sealed tool)      |
 |                       |                       |
 |-- executeSealedTool -|&gt;|                       |
 |                       |&lt;-- result -----------|
 |                       |                       |
 |-- getPrivacyResult --&gt;| (encrypted)          |
 |&lt;-- encrypted_result --|                       |
 |                       |                       |
(decrypt locally)        |                       |
 V                       |                       |
 plaintext result        |                       |
```

### 71.3 加密 blob 上传（uploadEncryptedBlob）

**文件**: `privacyClient.js:50-85`

```javascript
async function uploadEncryptedBlob(plaintext, opts) {
  const key = generateKey();          // 本地生成 AES-256-GCM 密钥
  const parts = encrypt(plaintext, key);  // 加密
  const packed = pack(parts);         // IV + authTag + ciphertext 打包
  
  const res = await fetch(privacyUrl('/blob/upload'), {
    body: JSON.stringify({
      data_base64: packed.toString('base64'),
      encryption: 'aes-256-gcm',
    }),
  });
  
  return {
    blobId: resp.blob_id,  // Hub 返回 blob ID
    key,                    // 本地保留密钥（关键！）
    iv: parts.iv,
    authTag: parts.authTag,
  };
}
```

**关键设计**：密钥 `key` **永远不发送**给 Hub。只有 Hub 收到加密数据，但无法解密。

### 71.4 密封工具执行（executeSealedTool）

**文件**: `privacyClient.js:90-115`

```javascript
async function executeSealedTool(opts) {
  // Hub 对加密 blob 执行 tool，不解密内容
  const res = await fetch(privacyUrl('/tool/execute'), {
    body: JSON.stringify({
      toolId: opts.toolId,   // 工具 ID（明文，Hub 可见）
      blobId: opts.blobId,  // 加密 blob ID（Hub 可见）
    }),
  });
  
  return { resultKey, resultHash, error };
}
```

**Evolver 为什么这样做**：`toolId` 是明文的（Hub 需要知道运行什么工具），`blobId` 指向加密数据（Hub 看不到内容）。这相当于"盲计算"——Hub 知道你在做什么工具，但不知道处理的是什么数据。

### 71.5 结果解密（getPrivacyResult）

**文件**: `privacyClient.js:130-155`

```javascript
async function getPrivacyResult(taskId, key) {
  const res = await fetch(privacyUrl(`/result/${taskId}`));
  const data = await res.json();
  
  // encrypted_result_base64 是 Hub 返回的加密结果
  const packed = Buffer.from(data.encrypted_result_base64, 'base64');
  const parts = unpack(packed);
  
  // 本地用本地密钥解密——Hub 不知道结果
  const plaintext = decrypt(parts.ciphertext, key, parts.iv, parts.authTag);
  return { plaintext, resultHash: data.result_hash };
}
```

### 71.6 隐私参数解析（parsePrivacyParams）

**文件**: `privacyClient.js:185-215`

```javascript
function parsePrivacyParams(body) {
  // 从 task body 中提取 [PRIVACY_PARAMS] 块
  // [PRIVACY_PARAMS]
  // tool_id: my_tool
  // blob_ids: blob1,blob2
  // [/PRIVACY_PARAMS]
  
  const block = body.substring(start + 16, end).trim();
  // 解析 key: value 行
  // 返回 { toolId, blobIds[] }
}
```

**Evolver 为什么这样做**：Hub 下发的任务 body 中可以包含隐私计算指令，Evolver 解析后执行对应的密封工具。

### 71.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 本地密钥生成 | key 从不离开客户端 | **高优先级**: BlueCortexCE 的隐私计算必须确保密钥不泄露 | 高 |
| 密封执行 | Hub 看到 toolId 但看不到 blob 内容 | **高优先级**: BlueCortexCE 处理敏感数据时可用类似模式 | 中 |
| 加密 blob 上传 | AES-256-GCM + pack(IV+authTag+ciphertext) | **中优先级**: BlueCortexCE 的隐私数据存储可参考此格式 | 中 |
| 结果本地解密 | Hub 返回加密结果，客户端解密 | **高优先级**: BlueCortexCE 的"云端处理"结果应在本地解密 | 高 |
| 隐私参数块 | [PRIVACY_PARAMS] 标签格式 | **低优先级**: BlueCortexCE 的任务描述格式可支持隐私标记 | 低 |

---

## 72. assets.js — 资产格式统一抽象（v1.3 新增）

**文件**: `src/gep/assets.js` (36 lines)

### 72.1 资产预览格式化（formatAssetPreview）

**文件**: `assets.js:8-35`

```javascript
function formatAssetPreview(preview) {
  if (!preview) return '(none)';
  if (typeof preview === 'string') {
    try {
      const parsed = JSON.parse(preview);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return JSON.stringify(parsed, null, 2);
      }
      return preview;
    } catch { return preview; }
  }
  return JSON.stringify(preview, null, 2);
}
```

**Evolver 为什么这样做**：资产预览可能是字符串（JSON 字符串）、数组或对象。统一格式化逻辑让 prompt 注入时的输出保持一致。

### 72.2 资产规范化（normalizeAsset）

**文件**: `assets.js:37-45`

```javascript
function normalizeAsset(asset) {
  if (!asset || typeof asset !== 'object') return asset;
  if (!asset.schema_version) asset.schema_version = SCHEMA_VERSION;
  if (!asset.asset_id) {
    try { asset.asset_id = computeAssetId(asset); } catch {}
  }
  return asset;
}
```

**Evolver 为什么这样做**：发布到 Hub 之前，自动补全 `schema_version` 和 `asset_id`——避免因缺失字段被 Hub 拒绝。

### 72.3 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 资产规范化 | 写入前补全 schema_version + asset_id | **高优先级**: BlueCortexCE 的任何写入前应自动补全元数据 | 高 |
| 预览格式化 | 字符串/数组/对象统一 JSON 格式化 | **中优先级**: BlueCortexCE 的 API 响应格式化应统一处理不同类型 | 中 |

---

## 73. candidates.js — 能力候选提取算法（v1.4 新增）

**文件**: `src/gep/candidates.js` (225 lines)

### 73.1 核心设计思想

Evolver 的 `candidates.js` 实现了**从失败和成功经验中自动发现可复用的能力模式**的算法。它从三个来源提取能力候选：

| 来源 | 触发条件 | 候选类型 |
|------|----------|---------|
| **Transcript 工具调用** | 同一工具调用 ≥3 次 | `CapabilityCandidate` |
| **Signal 模式** | 特定 signal 出现时 | `CapabilityCandidate` |
| **Failed Capsules** | 同类失败 ≥2 次 | `CapabilityCandidate` |

### 73.2 工具调用频率提取（extractToolCalls）

```javascript
// candidates.js:28-40
function extractToolCalls(transcript) {
  const lines = toLines(transcript);
  const calls = [];
  for (const line of lines) {
    // OpenClaw format: [TOOL: Shell]
    const m = line.match(/\[TOOL:\s*([^\]]+)\]/i);
    if (m && m[1]) { calls.push(m[1].trim()); continue; }
    // Cursor transcript format: [Tool call] Shell
    const m2 = line.match(/\[Tool call\]\s+(\S+)/i);
    if (m2 && m2[1]) calls.push(m2[1].trim());
  }
  return calls;
}
```

**Evolver 为什么这样做**：从 session transcript 中提取工具调用模式，识别"重复使用的工具"作为能力候选。频率 ≥3 才触发（避免噪声）。

### 73.3 Five Questions Shape 模板

每个候选都转换为**五问模板**，用于指导后续的 Gene 生成：

```javascript
// candidates.js:48-60
function buildFiveQuestionsShape({ title, signals, evidence }) {
  return {
    title: String(title || '').slice(0, 120),
    input: 'Recent session transcript + memory snippets + user instructions',
    output: 'A safe, auditable evolution patch guided by GEP assets',
    invariants: 'Protocol order, small reversible patches, validation, append-only events',
    params: `Signals: ${Array.isArray(signals) ? signals.join(', ') : ''}`.trim(),
    failure_points: 'Missing signals, over-broad changes, skipped validation, missing knowledge solidification',
    evidence: clip(evidence, 240),
  };
}
```

**五问**：
1. **Input** — 什么输入触发了这个能力？
2. **Output** — 期望的输出是什么？
3. **Invariants** — 必须保持不变的条件是什么？
4. **Params** — 与哪些 signals 相关？
5. **Failure Points** — 常见的失败点是什么？

### 73.4 Signal 驱动的候选生成

```javascript
// candidates.js:75-98
const signalCandidates = [
  // Defensive signals
  { signal: 'log_error', title: 'Repair recurring runtime errors' },
  { signal: 'protocol_drift', title: 'Prevent protocol drift and enforce auditable outputs' },
  { signal: 'windows_shell_incompatible', title: 'Avoid platform-specific shell assumptions (Windows compatibility)' },
  { signal: 'session_logs_missing', title: 'Harden session log detection and fallback behavior' },
  // Opportunity signals (innovation)
  { signal: 'user_feature_request', title: 'Implement user-requested feature' },
  { signal: 'user_improvement_suggestion', title: 'Apply user improvement suggestion' },
  { signal: 'perf_bottleneck', title: 'Resolve performance bottleneck' },
  { signal: 'capability_gap', title: 'Fill capability gap' },
  { signal: 'stable_success_plateau', title: 'Explore new strategies during stability plateau' },
  { signal: 'external_opportunity', title: 'Evaluate external A2A asset for local adoption' },
];
```

**Evolver 为什么这样做**：将 signal 模式直接映射为候选能力——当检测到特定 signal 时，自动生成对应的能力候选，驱动进化循环。

### 73.5 Failed Capsules 分组聚合

```javascript
// candidates.js:103-145
var groups = {};
var problemPriority = [
  'problem:performance',
  'problem:protocol',
  'problem:reliability',
  'problem:stagnation',
  'problem:capability',
];
for (var i = 0; i < failedCapsules.length; i++) {
  var fc = failedCapsules[i];
  if (!fc || fc.outcome && fc.outcome.status === 'success') continue;
  var reason = String(fc.failure_reason || '').trim();
  var failureTags = expandSignals((fc.trigger || []).concat(signalList), reason)
    .filter(function (t) {
      return t.indexOf('problem:') === 0 || t.indexOf('risk:') === 0 ||
             t.indexOf('area:') === 0 || t.indexOf('action:') === 0;
    });
  if (failureTags.length === 0) continue;
  var dominantProblem = null;
  for (var p = 0; p < problemPriority.length; p++) {
    if (failureTags.indexOf(problemPriority[p]) !== -1) {
      dominantProblem = problemPriority[p];
      break;
    }
  }
  // ...
}
```

**Evolver 为什么这样做**：将相似失败模式的 Capsule 聚合分组，识别"反复失败的进化路径"作为学习机会。同一问题类型出现 ≥2 次才生成候选。

### 73.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 工具调用频率提取 | transcript 中提取 `[TOOL: xxx]` 模式，≥3 次触发 | **高优先级**: BlueCortexCE 可从 session transcript 中提取重复行为模式 | 高 |
| 五问模板 | 候选转换为 input/output/invariants/params/failure_points | **高优先级**: BlueCortexCE Observation 可增加 structured template | 中 |
| Signal 驱动候选 | signal → capability candidate 自动映射 | **中优先级**: BlueCortexCE 可基于 signal 类型生成 structured extraction | 中 |
| Failed 聚合 | 失败 Capsule 按 problem type 分组，≥2 次触发 | **中优先级**: BlueCortexCE 的 `/api/sessions/{id}/failed` 可做类似聚合 | 低 |
| 确定性哈希 | `stableHash()` 用于去重 ID 生成 | **高优先级**: BlueCortexCE 的 entity ID 生成应使用确定性哈希 | 高 |

---

## 74. candidateEval.js — 候选预演构建与外部资产匹配（v1.4 新增）

**文件**: `src/gep/candidateEval.js` (107 lines)

### 74.1 buildCandidatePreviews 函数

`candidateEval.js` 的核心是 `buildCandidatePreviews` 函数，它：

1. 从当前 session 的 transcript 和 signals 生成新候选
2. 持久化候选到 `assetStore`
3. 读取最近的本地和外部候选
4. 构建供 GEP prompt 使用的预览文本

```javascript
// candidateEval.js:13-25
function buildCandidatePreviews({ signals, recentSessionTranscript }) {
  // Step 1: 提取新候选
  const newCandidates = extractCapabilityCandidates({
    recentSessionTranscript: recentSessionTranscript || '',
    signals,
    recentFailedCapsules: readRecentFailedCapsules(50),
  });
  // Step 2: 持久化
  for (const c of newCandidates) {
    try { appendCandidateJsonl(c); } catch (e) { ... }
  }
  // Step 3: 读取本地候选
  const recentCandidates = readRecentCandidates(20);
  const capabilityCandidatesPreview = renderCandidatesPreview(recentCandidates.slice(-8), 1600);
  // Step 4: 读取外部候选 + 信号匹配
  let externalCandidatesPreview = '(none)';
  // ...
}
```

### 74.2 外部 Gene 与 Capsule 的信号匹配

```javascript
// candidateEval.js:35-55
const matchedExternalGenes = genesOnly
  .map(g => {
    const pats = Array.isArray(g.signals_match) ? g.signals_match : [];
    const hit = pats.reduce((acc, p) => (matchPatternToSignals(p, signals) ? acc + 1 : acc), 0);
    return { gene: g, hit };
  })
  .filter(x => x.hit > 0)
  .sort((a, b) => b.hit - a.hit)
  .slice(0, 3)
  .map(x => x.gene);

const matchedExternalCapsules = capsulesOnly
  .map(c => {
    const triggers = Array.isArray(c.trigger) ? c.trigger : [];
    const score = triggers.reduce((acc, t) => (matchPatternToSignals(t, signals) ? acc + 1 : acc), 0);
    return { capsule: c, score };
  })
  .filter(x => x.score > 0)
  .sort((a, b) => b.score - a.score)
  .slice(0, 3)
  .map(x => x.capsule);
```

**Evolver 为什么这样做**：
- 从 Hub 同步的外部 Gene/Capsule，按当前 signals 匹配度排序
- 取 top-3 作为预演内容，让 GEP prompt 知道外部有什么可用资产
- 这是**联邦知识发现**的关键环节

### 74.3 预览格式化输出

```javascript
// candidateEval.js:60-90
externalCandidatesPreview = `\`\`\`json\n${JSON.stringify(
  [
    ...matchedExternalGenes.map(g => ({
      type: g.type,
      id: g.id,
      category: g.category || null,
      signals_match: g.signals_match || [],
      a2a: g.a2a || null,
    })),
    ...matchedExternalCapsules.map(c => ({
      type: c.type,
      id: c.id,
      trigger: c.trigger,
      gene: c.gene,
      summary: c.summary,
      confidence: c.confidence,
      blast_radius: c.blast_radius || null,
      outcome: c.outcome || null,
      success_streak: c.success_streak || null,
      a2a: c.a2a || null,
    })),
  ],
  null, 2
)}\n\`\`\``;
```

**Evolver 为什么这样做**：输出 JSON 格式便于 LLM 解析，包含 type/id/trigger/summary/confidence 等关键字段。

### 74.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 外部资产信号匹配 | genes/capsules 按 signals 匹配度排序，取 top-3 | **高优先级**: BlueCortexCE 的 `/api/search` 可增加"信号匹配度"排序 | 高 |
| 联邦知识发现 | Hub 同步 + 本地匹配 | **中优先级**: BlueCortexCE 可实现多实例联邦搜索 | 中 |
| 预览格式化 JSON | JSON 输出便于 LLM 解析 | **中优先级**: BlueCortexCE 的 context 输出可增加结构化 JSON 块 | 中 |
| 候选持久化 | appendCandidateJsonl 异步写入 | **高优先级**: BlueCortexCE 的候选observation应有异步写入机制 | 高 |

---

## 75. skillPublisher.js — Gene 到 SKILL.md 格式转换与 Hub 发布（v1.4 新增）

**文件**: `src/gep/skillPublisher.js` (307 lines)

### 75.1 核心设计思想

`skillPublisher.js` 实现将 **Gene 资产转换为可发布的 SKILL.md 格式**并发布到 Hub 的完整流程。这是 Evolver 知识变现的核心环节：

```
Gene (内部资产) → SKILL.md (Hub 发布格式) → Hub (联邦知识市场)
```

### 75.2 Gene → SKILL.md 格式转换（geneToSkillMd）

```javascript
// skillPublisher.js:67-135
function geneToSkillMd(gene) {
  var name = sanitizeSkillName(gene.id) || deriveFallbackName(gene);
  var displayName = toTitleCase(name);
  var lines = [
    '---',
    'name: ' + displayName,
    'description: ' + desc,
    '---',
    '',
    '# ' + displayName,
    '',
    '## When to Use',
    '- When your project encounters: ' + gene.signals_match.slice(0, 4).map(...).join(', '),
    '',
    '## Trigger Signals',
    gene.signals_match.forEach(s => lines.push('- `' + s + '`')),
    '',
    '## Preconditions',
    gene.preconditions.forEach(p => lines.push('- ' + p)),
    '',
    '## Strategy',
    gene.strategy.map((step, i) => (i+1) + '. **' + extractStepVerb(step) + '** -- ' + stripLeadingVerb(step)),
    '',
    '## Constraints',
    // constraints.max_files, constraints.forbidden_paths
    '',
    '## Validation',
    gene.validation.map(cmd => '```bash\n' + cmd + '\n```'),
    '',
    '## Metadata',
    '- Category: `' + gene.category + '`',
    '- Schema version: `' + gene.schema_version + '`',
    '- Distilled from: ' + gene._distilled_meta.source_capsule_count + ' successful capsules',
  ];
  return lines.join('\n');
}
```

**SKILL.md 结构**：

| Section | 内容 |
|---------|------|
| Frontmatter | name, description (YAML) |
| When to Use | 触发条件（signals） |
| Trigger Signals | 信号列表 |
| Preconditions | 前置条件 |
| Strategy | 步骤列表（动词 bold 化） |
| Constraints | 约束（文件数限制、禁止路径） |
| Validation | 验证命令 |
| Metadata | 类别、版本、来源 |

### 75.3 技能名称清洗（sanitizeSkillName）

```javascript
// skillPublisher.js:13-28
function sanitizeSkillName(rawName) {
  var name = rawName.replace(/[\r\n]+/g, '-')
                     .replace(/^gene_distilled_/, '')
                     .replace(/^gene_/, '')
                     .replace(/_/g, '-');
  // Strip ALL embedded timestamps (10+ digit sequences)
  name = name.replace(/-?\d{10,}-?/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
  // 过滤工具名和纯数字
  if (/^\d{8,}/.test(name) || /^(cursor|vscode|vim|emacs|windsurf|copilot|cline|codex)[-]?\d*$/i.test(name)) {
    return null;
  }
  if (name.replace(/[-]/g, '').length < 6) return null;
  return name;
}
```

**Evolver 为什么这样做**：
- 去除 `gene_distilled_` 和 `gene_` 前缀
- 将下划线转为连字符（kebab-case）
- 去除嵌入的时间戳
- 过滤工具名和纯数字

### 75.4 动词提取（extractStepVerb）

```javascript
// skillPublisher.js:157-168
function extractStepVerb(step) {
  // Only match a capitalized verb at the very start
  var match = step.match(/^([A-Z][a-z]+)/);
  return match ? match[1] : '';
}

function stripLeadingVerb(step) {
  var verb = extractStepVerb(step);
  if (verb && step.startsWith(verb)) {
    var rest = step.slice(verb.length).replace(/^[\s:.\-]+/, '');
    return rest || step;
  }
  return step;
}
```

**Evolver 为什么这样做**：策略步骤格式为 "Verb -- rest"，展示时动词 bold 化，让格式更易读。

### 75.5 Hub 发布流程（publishSkillToHub）

```javascript
// skillPublisher.js:180-230
function publishSkillToHub(gene, opts) {
  var hubUrl = getHubUrl();
  if (!hubUrl) return Promise.resolve({ ok: false, error: 'no_hub_url' });

  var content = geneToSkillMd(geneCopy);
  var skillId = 'skill_' + derivedName.replace(/_?\d{10,}_?/g, '_').replace(/_+/g, '_');
  var body = {
    sender_id: nodeId,
    skill_id: skillId,
    content: content,
    category: opts.category || geneCopy.category || null,
    tags: tags,
  };

  var endpoint = hubUrl + '/a2a/skill/store/publish';
  return fetch(endpoint, {
    method: 'POST',
    headers: buildHubHeaders(),
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  })
    .then(function (res) {
      if (res.status === 201 || res.status === 200) {
        return { ok: true, result: result.data };
      }
      if (res.status === 409) {
        return updateSkillOnHub(nodeId, skillId, content, opts, gene); // 已存在则更新
      }
      return { ok: false, error: result.data?.error || 'publish_failed' };
    });
}
```

**Evolver 为什么这样做**：
- `409 Conflict` 时自动触发 `updateSkillOnHub`（版本迭代）
- 15s 超时防止 Hub 无响应阻塞
- `AbortSignal.timeout()` 现代 API

### 75.6 标签清洗（sanitizeSignalsMatch）

```javascript
// skillPublisher.js:196-203
var tags = opts.tags || geneCopy.signals_match || [];
tags = tags.filter(function (t) {
  var s = String(t || '').trim();
  return s.length >= 3 && !/^\d+$/.test(s) && !/\d{10,}/.test(s);
});
```

**Evolver 为什么这样做**：过滤纯数字和时间戳标签，防止 Hub 拒绝或排序异常。

### 75.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 技能格式化 | Gene → SKILL.md (frontmatter + sections) | **高优先级**: BlueCortexCE 可将 Observation 导出为 SKILL.md 格式 | 高 |
| 技能名称清洗 | kebab-case + 去除时间戳 | **高优先级**: BlueCortexCE 的 asset 名称应有规范化逻辑 | 高 |
| Hub 发布 | POST → 409 → PUT 自动版本更新 | **中优先级**: BlueCortexCE 的资产发布可参考此幂等模式 | 中 |
| Verb bold 化 | 策略步骤 "Verify -- installation" → "**Verify** -- installation" | **中优先级**: BlueCortexCE 的 structured output 可类似格式化 | 中 |
| 来源追溯 | `_distilled_meta.source_capsule_count` 标注成功 Capsule 数量 | **高优先级**: BlueCortexCE 的 Observation 应记录来源 session | 高 |

---

