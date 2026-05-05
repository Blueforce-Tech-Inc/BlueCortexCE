# Evolver v1.47.0 `evolve.js` 安全系统与基础设施模式

> **角色**：记录 `src/evolve.js`（v1.47.0，2175行）中**安全保卫系统（Safety & Preflight）**与**基础设施模式**——这些在现有 doc 中覆盖不足，但对 BlueCortexCE 的**任务调度稳定性**有直接借鉴价值。
> **源码**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/evolve.js`
> **版本**：v1.47.0（本地 git `e72778e`）
> **最后更新**：2026-05-05

---

## 1. 概览：为什么关注安全系统

Evolver 作为**后台进化进程**（daemon loop），在多会话环境中运行，面临三类风险：

| 风险类别 | 具体问题 | v1.47.0 解决方案 |
|----------|----------|-----------------|
| **资源竞争** | 占用用户会话资源 | 活跃会话数上限（QUEUE_MAX） |
| **系统过载** | CPU/内存耗尽影响主机 | 系统负载感知（load1m vs CPU cores） |
| **并发冲突** | 多 evolver 实例同时运行 | 进程级竞速检测（`ps aux`） |
| **循环陷阱** | repair→fail→repair 死循环 | 修复循环断路器（5次强制创新） |
| **状态丢失** | CWD 被删除 | CWD 恢复机制 |
| **会话堆积** | session log 无限增长 | 定期归档（>100个时archive/） |

---

## 2. Preflight 检查链（`runPreflightChecks`，约 L960–L1030）

### 2.1 竞速检测（Race Detection）

```javascript
// SAFEGUARD: If another evolver Hand Agent is already running, back off.
if (process.platform !== 'win32') {
  const _psRace = execSync(
    'ps aux | grep "evolver_hand_" | grep -v grep',
    { encoding: 'utf8', timeout: 5000 }
  ).trim();
  if (_psRace && _psRace.length > 0) {
    console.log('[Evolver] Another evolver Hand Agent is already running. Yielding this cycle.');
    return { abort: true };
  }
}
```

**设计意图**：防止多个 evolver 实例同时修改基因库/图谱导致状态冲突。Windows 平台跳过（`grep` 不可用）。

**CE 借鉴**：BlueCortexCE 的 cron 调度若可能并发执行，应使用类似机制（文件锁或进程检测）。

### 2.2 活跃会话数上限（Queue Limit）

```javascript
const QUEUE_MAX = parseInt(process.env.EVOLVE_AGENT_QUEUE_MAX || '10', 10);
const QUEUE_BACKOFF_MS = parseInt(process.env.EVOLVE_AGENT_QUEUE_BACKOFF_MS || '60000', 10);
const activeUserSessions = getRecentActiveSessionCount(10 * 60 * 1000);
if (activeUserSessions > QUEUE_MAX) {
  console.log(`[Evolver] Agent has ${activeUserSessions} active sessions (max ${QUEUE_MAX}). Backing off...`);
  writeDormantHypothesis({ backoff_reason: 'active_sessions_exceeded', ... });
  await sleepMs(QUEUE_BACKOFF_MS);
  return { abort: true };
}
```

**会话计数算法**（`getRecentActiveSessionCount`）：
- OpenClaw sessions：扫描 `~/.openclaw/agents/<agent>/sessions/*.jsonl`，按 mtime 在 10min 内过滤
- Cursor transcripts：扫描 `CURSOR_TRANSCRIPTS_DIR`，同样按 mtime 过滤
- 排除 `evolver_hand_*` 文件（evolver 自己的执行会话）

**CE 借鉴**：`SearchService` 写入路径若有后台 cron，应检测活跃 API 调用数，超过阈值时退让（backoff）。

### 2.3 系统负载感知（System Load Awareness）

```javascript
function getSystemLoad() {
  const loadavg = os.loadavg();
  return { load1m: loadavg[0], load5m: loadavg[1], load15m: loadavg[2] };
}

function getDefaultLoadMax() {
  const cpuCount = os.cpus().length;
  return cpuCount === 1 ? 0.9 : cpuCount * 0.9; // 保留 10% 头舱
}

const LOAD_MAX = parseFloat(process.env.EVOLVE_LOAD_MAX || String(getDefaultLoadMax()));
if (sysLoad.load1m > LOAD_MAX) {
  console.log(`[Evolver] System load ${sysLoad.load1m.toFixed(2)} exceeds max ${LOAD_MAX.toFixed(1)}...`);
  await sleepMs(QUEUE_BACKOFF_MS);
  return { abort: true };
}
```

**关键设计**：默认阈值 = CPU cores × 0.9（保留 10% 头舱），可通过 `EVOLVE_LOAD_MAX` 环境变量覆盖。

**CE 借鉴**：cron 调度应感知系统负载，在高负载时减少工作频率。

### 2.4 循环门控（Loop Gating）

```javascript
if (bridgeEnabled && loopMode) {
  const st = readStateForSolidify();
  const pending = !lastSolid || !lastSolid.run_id ||
    String(lastSolid.run_id) !== String(lastRun.run_id);
  if (pending) {
    writeDormantHypothesis({ backoff_reason: 'loop_gating_pending_solidify', ... });
    await sleepMs(parseInt(process.env.EVOLVE_PENDING_SLEEP_MS || '120000'));
    return { abort: true };
  }
}
```

**设计意图**：在 `--loop` 模式下，必须等上一轮 solidify 完成才开启新 cycle，防止基因库被并发写入。

**CE 借鉴**：BlueCortexCE 的后台任务调度若有"写入-验证"两阶段，应在验证完成前阻止下一轮触发。

### 2.5 CWD 恢复

```javascript
try {
  process.cwd();
} catch (e) {
  if (e.code === 'ENOENT') {
    console.warn('[Evolver] CWD lost (ENOENT). Recovering to REPO_ROOT...');
    process.chdir(REPO_ROOT);
  }
}
```

**触发场景**：`git reset --hard` 或 `git clean -fd` 可能删除工作目录，导致 `process.cwd()` 抛出 ENOENT。

**CE 借鉴**：任何文件操作前应验证 CWD 有效，或使用绝对路径。

---

## 3. 修复循环断路器（`checkRepairLoopCircuitBreaker`）

```javascript
function checkRepairLoopCircuitBreaker() {
  const threshold = require('./config').REPAIR_LOOP_THRESHOLD; // 默认 5
  const allEvents = readAllEvents();
  const recent = allEvents.slice(-threshold);
  const allRepairFailed = recent.every(e =>
    e && e.intent === 'repair' &&
    e.outcome && e.outcome.status === 'failed'
  );
  if (allRepairFailed) {
    const geneIds = recent.map(e => e.genes_used && e.genes_used[0]);
    const sameGene = geneIds.every(id => id === geneIds[0]);
    console.warn(`[CircuitBreaker] Detected ${threshold} consecutive failed repairs...`);
    process.env.FORCE_INNOVATION = 'true'; // 强制切换到创新模式
  }
}
```

**断路逻辑**：
1. 读取最近 N 个事件（默认 5）
2. 若全部是 `intent=repair` + `outcome.status=failed`
3. 设置 `FORCE_INNOVATION=true`，强制下一轮使用创新策略，打破 repair 循环

**CE 借鉴**：观察连续 N 次相同类型失败时，自动切换注入策略（如从 repair 模式切换到 reflect 模式）。

---

## 4. 维护子系统（`performMaintenance`）

```javascript
function performMaintenance() {
  // 1. 删除 evolver_hand_* 会话文件（立即删除，不累积）
  const evolverFiles = files.filter(f => f.startsWith('evolver_hand_'));
  for (const f of evolverFiles) fs.unlinkSync(f);

  // 2. 当 session log 总数 > 100 时，归档最旧的（保留最近 50 个）
  if (remaining >= 100) {
    const toArchive = fileStats.slice(0, fileStats.length - 50);
    for (const file of toArchive) {
      fs.renameSync(oldPath, path.join(ARCHIVE_DIR, file.name));
    }
  }
}
```

**设计意图**：
- `evolver_hand_*`：evolver 的执行代理会话是单次用的，不能与用户会话混淆
- 归档阈值 100，保留 50——确保会话目录不会无限膨胀

**CE 借鉴**：BlueCortexCE 的 session log 管理可参考此归档策略。

---

## 5. Skills 缓存（6小时 TTL + mtime 验证）

```javascript
const CACHE_TTL = 1000 * 60 * 60 * 6; // 6 Hours

// Use cache if it's fresh AND newer than the directory
if (isFresh && cacheStats.mtimeMs > dirStats.mtimeMs) {
  fileList = cached.list; // 使用缓存
} else {
  fileList = buildSkillsList(); // 重新扫描
  fs.writeFileSync(SKILLS_CACHE_FILE, JSON.stringify({ list: fileList }));
}
```

**关键设计**：
1. 缓存新鲜度：6小时 TTL
2. 目录结构变化检测：缓存 mtime > 目录 mtime 时才认为有效
3. 缓存文件本身会被写入（下次可复用）

**CE 借鉴**：在 Java 侧缓存 skill 列表时，应以 skill 目录 mtime 作为失效条件，而非仅依赖 TTL。

---

## 6. Mood Awareness（`mood.json`）

```javascript
let moodStatus = 'Mood: Unknown';
try {
  const moodFile = path.join(MEMORY_DIR, 'mood.json');
  if (fs.existsSync(moodFile)) {
    const moodData = JSON.parse(fs.readFileSync(moodFile, 'utf8'));
    moodStatus = `Mood: ${moodData.current_mood || 'Neutral'} (Intensity: ${moodData.intensity || 0})`;
  }
} catch (e) {}
```

**用途**：从 `mood.json` 读取当前"情绪"状态（`current_mood` + `intensity`），注入 LLM prompt 影响生成风格（是 personality state machine 的外部输入源）。

**CE 借鉴**：`personality.json` 或 `agent_state.json` 可类似设计，为 BlueCortexCE 提供人格/状态外部注入通道。

---

## 7. Git-Sync 检测

```javascript
const hasGitSync = fs.existsSync(path.join(skillsDir, 'git-sync'));
if (hasGitSync) {
  syncDirective = 'Workspace sync: run skills/git-sync/sync.sh "Evolution: Workspace Sync"';
}
```

**设计意图**：如果项目安装了 `git-sync` skill，则在 prompt 中注入同步指令，使 evolver 能够自动同步工作区变更。

**CE 借鉴**：Hook 脚本可类似检测本地安装的 skill，决定是否注入特定上下文。

---

## 8. Auto-Update（ClawHub CLI）

```javascript
function checkAndAutoUpdate() {
  // 1. 读取 openclaw.json 配置（autoUpdate + intervalHours）
  // 2. 检查距上次更新是否超过 intervalHours
  // 3. 查找 clawhub CLI（which / 标准路径）
  // 4. 对 ['evolver', 'feishu-evolver-wrapper'] 执行 clawhub update --force
  // 5. 写入 evolver_update_check.json 记录时间戳
}
```

**关键设计**：
- 速率限制：默认 6 小时检查一次，可配置
- 非阻塞：更新失败不影响本次进化循环
- 多包更新：同时更新 evolver 核心包和飞书包装器

**CE 借鉴**：BlueCortexCE 的 cron 任务可类似实现自我更新能力（检查新版 + 非阻塞热更新）。

---

## 9. 休眠假设恢复（Dormant Hypothesis）

```javascript
// 恢复中断 cycle 的状态
const dormantHypothesis = readDormantHypothesis();
if (dormantHypothesis) {
  console.log('[DormantHypothesis] Recovered partial state...');
  // 重新注入 signals
  for (const sig of dormantHypothesis.signals) {
    if (!signals.includes(sig)) signals.push(sig);
  }
}

// 保存休眠假设（preflight abort 时）
function writeDormantHypothesis(data) {
  fs.writeFileSync(DORMANT_PATH, JSON.stringify(data, null, 2));
}
```

**设计意图**：当 preflight check 失败（如 QUEUE_BACKOFF_MS 休眠）时，将当前 cycle 的状态保存到文件，下次运行时恢复，避免 signals 丢失。

**CE 借鉴**：BlueCortexCE 的后台任务若被中断，应将中间状态持久化，以便下次恢复。

---

## 10. 与现有 doc 的对照

| 模式 | 本文深度 | 已有 doc 覆盖 |
|------|---------|-------------|
| 竞速检测（`ps aux`） | **完整** | ❌ 未专门分析 |
| 队列上限（QUEUE_MAX） | **完整** |  doc 26 提及，未深入 |
| 系统负载感知 | **完整** | doc 26 提及 |
| 循环门控（loop gating） | **完整** | doc 19 提及outcome推断，未深入门控 |
| CWD 恢复 | **完整** | doc 26 提及 |
| 修复循环断路器 | **完整** | doc 25 提及（advanced patterns） |
| 维护（归档） | **完整** | ❌ 未专门分析 |
| Skills 缓存 6h TTL | **完整** | ❌ 未专门分析 |
| Mood awareness | **完整** | doc 44 提及 personality，未深入 mood |
| Git-sync 检测 | **完整** | ❌ 未专门分析 |
| Auto-update clawhub | **完整** | ❌ 未专门分析 |
| Dormant hypothesis | **完整** | doc 19 提及 outcome，未深入恢复 |

---

## 11. BlueCortexCE 可借鉴的行动项

| 优先级 | 模式 | 具体行动 |
|--------|------|---------|
| **P1** | 队列上限 | 在 `SearchService` 或 cron 调度层实现活跃请求计数，超过阈值时退让 |
| **P1** | 系统负载感知 | cron 任务感知 `os.loadavg()`，高负载时减少频率 |
| **P1** | 修复循环断路器 | `ObservationService` 连续 N 次相同 type 失败时，切换注入策略 |
| **P2** | Skills 缓存 mtime | skill 列表缓存以 skill 目录 mtime 为失效条件 |
| **P2** | 维护归档 | 实现 session log 归档（>100 个时移入 archive/） |
| **P3** | Dormant 状态恢复 | 后台任务被中断时，将中间状态写入文件以便恢复 |
| **P3** | CWD 验证 | 文件操作前验证 CWD 有效，使用绝对路径 |
| **P3** | Auto-update | cron 任务定期检查新版 + 非阻塞热更新 |

---

## 12. v1.47 vs v1.78 演进说明

v1.47.0 `evolve.js`（2175L）包含了大量内联逻辑。v1.78 对其进行了模块化拆分（-2176行到~300L），新增 `src/gep/atp/`、`src/adapters/` 等子系统。从安全系统角度，v1.47 的 preflight 检查链是**最完整的内联版本**，v1.78 将部分逻辑迁移到了 `src/ops/` 模块。

---

_Last verified against `src/evolve.js` L1–L2175 (v1.47.0 `e72778e`)_
