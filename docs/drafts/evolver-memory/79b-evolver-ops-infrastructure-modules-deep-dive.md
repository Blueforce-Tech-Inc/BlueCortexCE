# v79b 运维基础设施模块深度分析

**版本**：v79b | **时间**：2026-05-04 | **模块**：health_check.js · trigger.js · commentary.js · skills_monitor.js · cleanup.js · lifecycle.js

---

## 1. health_check.js — 健康检查

**文件**：`src/ops/health_check.js`（~120行）| **设计原则**：分层严重度 / 缓存优化 / Graceful degradation

### 1.1 检查项目

| 检查项 | 方式 | 严重度 |
|--------|------|--------|
| `env:FEISHU_APP_ID/APP_SECRET` | `process.env[key]` | warning（非 critical，防止重启循环） |
| `env:CLAWHUB_TOKEN/OPENAI_API_KEY` | `process.env[key]` | info |
| `disk_space` | `statfs`（Node 18+）或 `df -P` fallback | critical (>90%) / warning (>80%) |
| `memory` | `os.freemem/totalmem` | critical (>95%) |
| `process_count` (Linux only) | `readdirSync('/proc')` 缓存60s | warning (>2000) |

### 1.2 关键设计：Secret 检查降级

```js
// ⚠️ Downgraded to warning to prevent restart loops
// （之前是 critical，导致 evolver 持续重启）
criticalSecrets.forEach(key => {
  checks.push({ name: `env:${key}`, ok: false, status: 'missing', severity: 'warning' });
  warnings++;
});
```

**教训**：将 missing secret 设为 critical 级别会导致 ops 在 secret 未配置时就绪（development 场景）时持续报错/重启，改为 warning 让 evolver 可以优雅降级运行。

### 1.3 磁盘空间检查

```js
// Node 18+ API
const stats = fs.statfsSync(mount || '/');
const total = stats.blocks * stats.bsize;
const free = stats.bavail * stats.bsize; // 优先用 unprivileged 可用空间
// df fallback
execSync(`df -P "${safeMount}" | tail -1 | awk '{print $5, $4}'`)
```

**关键**：使用 `bavail` 而非 `bfree`，优先考虑 unprivileged 用户可用空间（容器场景中 root 和普通用户可用空间不同）。

### 1.4 进程数缓存

```js
// 60秒缓存，避免频繁 readdirSync('/proc')（开销大）
if (!runHealthCheck._procCache || now - runHealthCheck._procCacheAt > 60000) {
  runHealthCheck._procCache = fs.readdirSync('/proc').filter(f => /^\d+$/.test(f)).length;
  runHealthCheck._procCacheAt = now;
}
```

### 1.5 CE 借鉴

**P0**：`bavail` 而非 `bfree` → BlueCortexCE 在检查磁盘空间时，应优先使用 unprivileged 可用空间（Java: `FileStore.getUsableSpace()` 而非 `getFreeSpace()`）。

**P1**：Secret missing 降级为 warning → CE 的健康检查中，optional 配置缺失（如可选的 API key）不应设为 critical，应设为 info 或 warning，防止 development 场景阻塞。

**P1**：进程数检查缓存 60s → CE 如果需要类似检查，应实现缓存机制，避免高频系统调用。

---

## 2. trigger.js — 信号文件 IPC

**文件**：`src/ops/trigger.js`（~30行）| **设计原则**：文件即信号 / Wrapper 轮询 / 零依赖

### 2.1 接口

```js
send()       // 写 'WAKE' 到 evolver_wake.signal
clear()      // 删除信号文件
isPending()  // 检查信号是否挂起
```

### 2.2 机制

```js
var WAKE_FILE = path.join(getWorkspaceRoot(), 'memory', 'evolver_wake.signal');

function send() {
  fs.writeFileSync(WAKE_FILE, 'WAKE');
}
```

Wrapper 进程轮询此文件，一旦发现就立即唤醒 evolver 主循环。这是 evolver 与 wrapper 之间的最简 IPC 机制。

### 2.3 CE 借鉴

**P2**：BlueCortexCE 的 Java 后端与 OpenClaw 之间的即时唤醒可以参考类似机制：使用文件系统信号（如 `.wake` 文件）而非轮询 HTTP 端点，减少轮询开销。当前 CE 的 cron 调度使用 HTTP 健康检查，如果改为文件信号可以降低开销。

**P2**：trigger 的 send/clear/isPending 三接口模式 → 任何需要即时响应的子系统都可以用类似模式（信号文件 + 轮询/通知）。

---

## 3. commentary.js — 人格化评语生成

**文件**：`src/ops/commentary.js`（~70行）| **设计原则**：人格化 / 轻量 / Zero-dependency

### 3.1 三种人格

```js
PERSONAS = {
  standard: {
    success: ['Evolution complete. System improved.', 'Another successful cycle.', ...],
    failure: ['Cycle failed. Will retry.', 'Encountered issues. Investigating.', ...],
  },
  greentea: {
    success: ['Did I do good? Praise me~', 'So efficient... unlike someone else~', ...],
    failure: ['Oops... it is not my fault though~', 'This is harder than it looks, okay?', ...],
  },
  maddog: {
    success: ['TARGET ELIMINATED.', 'Mission complete. Next.', ...],
    failure: ['FAILED. RETRYING.', 'Obstacle encountered. Adapting.', ...],
  },
};
```

### 3.2 获取评语

```js
getComment({ persona: 'greentea', success: true, duration: 5000 })
// → 从对应人格的 success/failure pool 中随机选一条
```

### 3.3 CE 借鉴

**P2**：BlueCortexCE 的 Session Summary 或周期性报告可以引入人格化评语。当前 HEARTBEAT.md 报告是纯技术性的，引入人格化风格可以让报告更生动（但需考虑 Feishu 群聊场景的适用性）。

**P3**：人格化评语对开发/调试很有帮助，但生产环境（用户可见）可能需要更正式的语气。建议作为 development/debug 功能保留。

---

## 4. skills_monitor.js — 技能监控 + 自愈

**文件**：`src/ops/skills_monitor.js`（~160行）| **设计原则**：Zero Feishu / 自愈优先 / 性能优化

### 4.1 检查项

| 问题 | 检测方式 | 自愈方式 |
|------|---------|---------|
| Missing node_modules | 检查 `node_modules/` 目录存在性 | `npm install --production --no-audit --no-fund` |
| Empty node_modules | `readdirSync.length === 0` | 删除后 `npm install` |
| Invalid package.json | `JSON.parse` 异常 | — |
| Missing SKILL.md | 检查文件存在性 | 创建 stub SKILL.md |

### 4.2 性能优化：避免同步 spawn

```js
// ⚠️ 之前版本对每个 skill 都 spawn node 做语法检查
// 问题：spawn 是同步的，每个 skill 都调用，开销巨大
// 解决：去掉同步 spawn，信任运行时捕获语法错误

// 优化：只检查 node_modules 存在性，不执行 node -c
// "We can trust the runtime to catch syntax errors when loading."
```

**设计意图**：skills_monitor 必须在每次 evolver 循环中运行（用于健康检查），因此必须极快。移除同步 spawn 后，扫描所有 skill 的时间从几十秒降到几百毫秒。

### 4.3 用户忽略列表

```js
// .skill_monitor_ignore 文件格式：
// skill-name-to-ignore
// another-skill
// # 注释
IGNORE_LIST.add(t);
```

内置 ignore list：
```js
const IGNORE_LIST = new Set(['common', 'clawhub', 'input-validator', 'proactive-agent', 'security-audit']);
```

### 4.4 SKILL.md Stub 生成

```js
// 自愈创建最小化 SKILL.md
var name = skillName.replace(/-/g, ' ');
fs.writeFileSync(path.join(skillPath, 'SKILL.md'), '# ' + skillName + '\n\n' + name + ' skill.\n');
```

### 4.5 CE 借鉴

**P0**：`npm install` 自愈时的 `package-lock.json` 删除 → CE 在执行 npm 操作前，应先删除 `package-lock.json` 避免版本冲突（参考 `self_repair.js` 模式）。

**P1**：性能优化理念（不 spawn 则不做语法检查，信任运行时）→ BlueCortexCE 的技能监控如果需要扫描大量文件，应优先使用文件系统检查而非执行进程。

**P2**：用户忽略列表（`.skill_monitor_ignore`）→ CE 的配置系统可以支持类似的用户级 ignore 文件。

---

## 5. cleanup.js — 两阶段 GEP Artifact 清理

**文件**：`src/ops/cleanup.js`（~100行）| **设计原则**：双保险 / 最小化留存 / 原子批删

### 5.1 两阶段清理

```js
// Phase 1: Age-based（age > MAX_AGE_MS，保留至少 MIN_KEEP）
for (var i = MIN_KEEP; i < files.length; i++) {
  if (now - files[i].mtime > MAX_AGE_MS) {
    filesToDelete.push(files[i].path);
  }
}

// Phase 2: Size-based safety cap（超过 MAX_FILES 则删最旧的）
if (remainingFiles.length > MAX_FILES) {
  var toDelete = remainingFiles.slice(MAX_FILES).map(f => f.path);
  deleted += safeBatchDelete(toDelete);
}
```

### 5.2 配置参数

| 参数 | 来源 | 说明 |
|------|------|------|
| `MAX_AGE_MS` | `config.js` | 超过此时间的文件可删除 |
| `MIN_KEEP` | `config.js` | 无论多旧，至少保留 N 个 |
| `MAX_FILES` | `config.js` | 文件总数上限 |

### 5.3 原子批删

```js
function safeBatchDelete(batch) {
  var deleted = 0;
  for (var i = 0; i < batch.length; i++) {
    try { fs.unlinkSync(batch[i]); deleted++; } catch (_) {}
  }
  return deleted;
}
```

**设计亮点**：两阶段清理保证：
- 无论时间多近，至少保留 N 个文件（防止误删最新文件）
- 即使 N 个文件都很新，总数超限后仍然删最旧的（防止磁盘耗尽）

### 5.4 Session Scope 感知

```js
var evoDir = getEvolutionDir();
// 如果 EVOLVER_SESSION_SCOPE 设置，getEvolutionDir() → memory/evolution/scopes/{scope}/
// 清理只在当前 session scope 内进行，不影响其他 scope
```

### 5.5 CE 借鉴

**P1**：两阶段清理模式（age-based + size-based）→ BlueCortexCE 的日志/临时文件清理可以参考：即使文件未过期，超过数量上限时仍删除最旧的。

**P2**：`safeBatchDelete` 的 continue-on-error 模式 → CE 批量删除文件时，不应因为单个文件删除失败而中止整个批量操作。

---

## 6. lifecycle.js — 进程生命周期管理

**文件**：`src/ops/lifecycle.js`（~230行）| **设计原则**：Detached Daemon / PID 文件 / Graceful Shutdown / Wrapper 优先

### 6.1 进程发现

```js
function getRunningPids() {
  // ps -e -o pid,args → 过滤包含 'node' && 'index.js' && '--loop'
  // 且包含 'feishu-evolver-wrapper' 或 'skills/evolver'
  // 排除当前进程
}
```

**为什么用 `ps` 而非 PID 文件**：PID 文件可能被 stale（进程崩溃后未清理），`ps` 扫描保证找到真正运行的进程。

### 6.2 Start — Detached Daemon

```js
var child = spawn('node', [script, '--loop'], {
  detached: true,       // 分离：父进程退出后子进程继续运行
  stdio: ['ignore', out, err],  // stdin 忽略，stdout/stderr → log file
  cwd: WORKSPACE_ROOT,
  env: env,
});
child.unref();  // 父进程不等待子进程
fs.writeFileSync(PID_FILE, String(child.pid));  // 记录 PID
```

### 6.3 Stop — Graceful Shutdown

```js
// SIGTERM → 等待最多 5s → SIGKILL
for (var i = 0; i < pids.length; i++) {
  process.kill(pids[i], 'SIGTERM');
}
var attempts = 0;
while (getRunningPids().length > 0 && attempts < 10) {
  execSync('sleep 0.5');  // 每 0.5s 检查一次
  attempts++;
}
var remaining = getRunningPids();
for (var j = 0; j < remaining.length; j++) {
  process.kill(remaining[j], 'SIGKILL');
}
```

### 6.4 Health Check — Stagnation 检测

```js
function checkHealth() {
  if (pids.length === 0) return { healthy: false, reason: 'not_running' };
  var silenceMs = Date.now() - fs.statSync(LOG_FILE).mtimeMs;
  if (silenceMs > MAX_SILENCE_MS) {
    return { healthy: false, reason: 'stagnation', silenceMinutes: ... };
  }
  return { healthy: true, pids: pids };
}
```

**Stagnation**：如果日志文件在 `MAX_SILENCE_MS`（默认可能很长，如几小时）内未更新，说明 evolver 可能卡住了。这是比"进程是否存在"更精确的健康指标。

### 6.5 Wrapper 优先

```js
function getLoopScript() {
  // 优先使用 wrapper（feishu-evolver-wrapper）
  var wrapper = path.join(WORKSPACE_ROOT, 'skills/feishu-evolver-wrapper/index.js');
  if (fs.existsSync(wrapper)) return wrapper;
  // fallback 到 core
  return path.join(getRepoRoot(), 'index.js');
}
```

### 6.6 PATH 修复

```js
var npmGlobal = path.join(process.env.HOME || '', '.npm-global/bin');
if (env.PATH && !env.PATH.includes(npmGlobal)) {
  env.PATH = npmGlobal + ':' + env.PATH;
}
```

解决 detached 子进程找不到全局 npm 命令的问题。

### 6.7 CLI 接口

```bash
node lifecycle.js start
node lifecycle.js stop
node lifecycle.js restart
node lifecycle.js status     # → { running: true, pids: [{pid, cmd}], log: '...' }
node lifecycle.js log         # tail -n 20
node lifecycle.js check       # health check → auto restart if unhealthy
```

### 6.8 CE 借鉴

**P0**：Graceful shutdown（SIGTERM → 等待 → SIGKILL）→ BlueCortexCE 的服务管理器（如 systemd 或 Docker restart policy）应实现类似的优雅关闭策略，给进程时间清理资源。

**P1**：Stagnation 检测（`LOG_FILE.mtime` 而非仅检查进程）→ CE 的健康检查可以不仅检查进程存在，还可以检查日志/活动状态，防止"僵尸进程"被误认为健康。

**P1**：Wrapper 优先模式 → CE 如果有多个实现版本（如 OpenClaw plugin vs standalone），可以类似地优先使用 plugin 版本。

**P2**：PATH 修复 → Java 进程在 fork/exec 时，应注意 PATH 环境变量可能与交互式 shell 不同。

---

## 7. 总体设计模式总结

| 模块 | 模式 | CE 优先级 |
|------|------|---------|
| health_check.js | `bavail` unprivileged 空间 | P0 |
| health_check.js | Secret missing → warning（非 critical） | P1 |
| health_check.js | 进程数缓存 60s | P1 |
| trigger.js | 信号文件 IPC（文件即信号） | P2 |
| commentary.js | 人格化评语（Zero-dependency） | P2 |
| skills_monitor.js | npm install 自愈 + package-lock 删除 | P0 |
| skills_monitor.js | 避免同步 spawn（性能优先） | P1 |
| skills_monitor.js | 用户 ignore 文件 | P2 |
| cleanup.js | age + size 双保险 | P1 |
| cleanup.js | Continue-on-error 批删 | P2 |
| cleanup.js | Session scope 感知 | P2 |
| lifecycle.js | Detached daemon + unref | P1 |
| lifecycle.js | Graceful shutdown（SIGTERM → 等待 → SIGKILL） | P0 |
| lifecycle.js | Stagnation 检测（日志 mtime） | P1 |
| lifecycle.js | Wrapper 优先 + core 兜底 | P1 |
| lifecycle.js | PATH 修复（npm global） | P2 |

---

## 8. ops/index.js 导出清单

```js
// src/ops/index.js
module.exports = {
  lifecycle:      require('./lifecycle'),
  skillsMonitor:  require('./skills_monitor'),
  cleanup:        require('./cleanup'),
  trigger:        require('./trigger'),
  commentary:      require('./commentary'),
  selfRepair:     require('./self_repair'),
  // 注意：health_check 和 innovation 不在 index 中
  // health_check 可能是独立模块，通过 cron 调用
  // innovation 可能是按需调用，非周期性
};
```

---

## 9. 下一步

- **接力建议**：`self_repair.js` 详细分析（`ops/index.js` 中导出但尚未深度覆盖）
- **并行建议**：`health_check.js` 与 `lifecycle.js` 的 `checkHealth()` 是否可以合并
- **待确认**：`innovation.js` 为何不在 `ops/index.js` 中导出（是独立按需调用？）
