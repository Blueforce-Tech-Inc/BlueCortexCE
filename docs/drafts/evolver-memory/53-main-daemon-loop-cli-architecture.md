# 53 — Main Entry Point (`index.js`) Daemon Loop & CLI Architecture

**分析目标**：深度解析 `EvoMap/evolver/index.js`（754行）作为主入口的架构设计，为 BlueCortexCE 提供进程生命周期管理、CLI 设计模式和自适应调度借鉴。

**数据来源**：`EvoMap/evolver/index.js`（v1.47.0，2026-04-16）

**最后更新**：2026-04-25

---

## §1 概览：主入口职责

`index.js` 是 evolver 的单文件 CLI 入口和守护进程实现，提供：

| 职责 | 说明 |
|------|------|
| **CLI 命令路由** | `run`/`solidify`/`review`/`distill`/`fetch`/`asset-log` 6 个子命令 |
| **守护进程循环** | `--loop` 模式下的自适应休眠 + 饱和感知的进化循环 |
| **进程生命周期管理** | 单例锁、自杀重启、信号处理、未捕获异常防护 |
| **Hub 心跳与事件流** | `startHeartbeat()` + `startEventStream()` 独立于进化循环运行 |
| **后置自动蒸馏** | `solidify` 成功后触发 auto-distillation |

---

## §2 CLI 命令结构

```
node index.js [run|solidify|review|distill|fetch|asset-log] [flags]
```

### §2.1 命令速览

| 命令 | 功能 | 核心模块 |
|------|------|----------|
| `run`（默认） | 执行单次或循环进化 | `evolve.run()` |
| `run --loop` | 守护进程模式 | 内部 while 循环 |
| `solidify` | 固化基因、提交变更 | `solidify()` |
| `review` | 查看待定变更 | git diff + state |
| `review --approve` | 批准并固化 | solidify + git |
| `review --reject` | 回滚并拒绝 | git checkout + state |
| `distill --response-file=<path>` | 完成 LLM 蒸馏 | `skillDistiller` |
| `fetch --skill <id>` | 从 Hub 下载技能 | `a2aProtocol` |
| `asset-log [--json]` | 查看资产生命周期日志 | `assetCallLog` |

### §2.2 单次运行（默认 `run`）

```
await evolve.run()
```

无 `--loop` 时，执行单次进化后立即退出（process.exit 0/1）。

### §2.3 守护进程模式（`--loop` / `--mad-dog`）

```javascript
const isLoop = args.includes('--loop') || args.includes('--mad-dog');
if (isLoop) { /* 守护进程逻辑 */ }
```

**关键环境变量**：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `EVOLVER_MIN_SLEEP_MS` | 2000 | 最小休眠（ms） |
| `EVOLVER_MAX_SLEEP_MS` | 300000 | 最大休眠（5min） |
| `EVOLVER_IDLE_THRESHOLD_MS` | 500 | 快循环判定阈值 |
| `EVOLVE_PENDING_SLEEP_MS` | 120000 | 待固化时的休眠 |
| `EVOLVER_MAX_CYCLES_PER_PROCESS` | 100 | 进程自杀前最大循环数 |
| `EVOLVER_MAX_RSS_MB` | 500 | RSS 内存上限（MB） |
| `EVOLVER_SUICIDE` | true | 是否启用自杀机制 |
| `EVOLVE_BRIDGE` | false | 是否启用 loop bridge |

---

## §3 守护进程循环架构

### §3.1 循环体结构

```javascript
while (true) {
  // 1. 待固化状态门控
  if (isPendingSolidify(st0)) { await sleep(pendingSleepMs); continue; }

  // 2. 执行进化
  ok = await evolve.run();

  // 3. 自适应休眠
  if (!ok || dt < idleThresholdMs) {
    currentSleepMs = Math.min(maxSleepMs, currentSleepMs * 2); // 快/失败 → 指数退避
  } else {
    currentSleepMs = minSleepMs; // 正常 → 重置
  }

  // 4. OMLS 空闲调度（失败时触发蒸馏）
  const schedule = getScheduleRecommendation();
  if (schedule.should_distill) { autoDistillFromFailures(); }

  // 5. 内存泄漏保护（自杀重启）
  if (cycleCount >= maxCyclesPerProcess || memMb > maxRssMb) { spawn_child && exit(0); }

  // 6. 饱和度节流
  if (lastSignals.includes('force_steady_state')) saturationMultiplier = 4;
  else if (lastSignals.includes('evolution_saturation')) saturationMultiplier = 2;

  // 7. 随机抖动 + 总休眠
  const jitter = Math.floor(Math.random() * 250);
  await sleep((currentSleepMs + jitter) * saturationMultiplier * omlsMultiplier);
}
```

### §3.2 自适应休眠策略

**核心原则**：快循环（< idleThresholdMs）视为"空转"，触发指数退避；正常循环重置到 minSleepMs。

```
OK + 慢 → sleep = minSleepMs（重置）
FAIL + 快 → sleep = min(sleep * 2, maxSleepMs)（退避）
```

**三层乘数叠加**：
- `currentSleepMs`（自适应退避基础值）
- `saturationMultiplier`（饱和信号节流：2x / 4x）
- `omlsMultiplier`（OMLS 空闲调度：0.5x~2x）
- `jitter`（随机 +0~250ms，避免锁步重启）

### §3.3 待固化状态门控（`isPendingSolidify`）

```javascript
function isPendingSolidify(state) {
  const lastRun = state.last_run;
  const lastSolid = state.last_solidify;
  if (!lastRun?.run_id) return false;
  if (!lastSolid?.run_id) return true;  // 有运行但从未固化 → pending
  return String(lastSolid.run_id) !== String(lastRun.run_id);
}
```

**设计意图**：防止在前一次运行尚未固化时开启新循环（避免并发冲突）。

**Bridge 禁用时的自净**：

```javascript
if (EVOLVE_BRIDGE === 'false' && isPendingSolidify(afterRun)) {
  rejectPendingRun(statePath); // 状态标记为 rejected，不回滚 git
}
```

---

## §4 进程生命周期管理

### §4.1 单例锁（Singleton Lock）

```javascript
function acquireLock() {
  const lockFile = path.join(__dirname, 'evolver.pid');
  // 1. 尝试以 wx flag 原子创建文件
  // 2. 若 EEXIST，读取已有 PID
  // 3. kill(pid, 0) 检测进程是否存在
  //    - 存在 → 退出（另一个实例运行中）
  //    - 不存在（stale）→ 接管锁
  // 4. 写入当前 PID，返回 true
}
```

**锁文件**：`evolver.pid`（与 `index.js` 同目录）

**健壮性**：
- `wx` flag 保证原子性
- `kill(pid, 0)` 检测进程存活（非仅文件存在）
- Stale lock 自动接管

### §4.2 自杀重启机制（Memory Leak 保护）

```javascript
const memMb = process.memoryUsage().rss / 1024 / 1024;
if (cycleCount >= maxCyclesPerProcess || memMb > maxRssMb) {
  // spawn child with detached:true, stdio:'ignore'
  const child = spawn(process.execPath, [__filename, ...args], {
    detached: true, stdio: 'ignore', env: process.env
  });
  child.unref();
  releaseLock();
  process.exit(0);  // 父进程退出，子进程成为孤儿
}
```

**触发条件**：
1. 单进程循环数达到上限（默认 100）
2. RSS 内存超过阈值（默认 500MB）

**设计意图**：防止 Node.js 长期运行导致的内存泄漏累积，通过进程级重启"重置"内存。

### §4.3 信号处理（Signal Handlers）

```javascript
process.on('SIGINT', () => { releaseLock(); stopEventStream(); process.exit(); });
process.on('SIGTERM', () => { releaseLock(); stopEventStream(); process.exit(); });
process.on('uncaughtException', (err) => {
  console.error('[FATAL] Uncaught exception:', err.stack);
  releaseLock(); process.exit(1);
});
process.on('unhandledRejection', (reason, promise) => {
  _unhandledRejectionCount++;
  if (_unhandledRejectionCount >= 5) {
    console.error('[FATAL] Too many unhandled rejections. Exiting.');
    releaseLock(); process.exit(1);
  }
});
```

**关键设计**：
- `SIGINT/SIGTERM`：优雅退出，释放锁、停止事件流
- `uncaughtException`：立即退出（不尝试恢复）
- `unhandledRejection`：累积计数，超过 5 次才退出（容忍瞬时错误）

### §4.4 Hub 心跳与事件流（独立于循环）

```javascript
// 在守护进程启动时立即执行，不阻塞循环
const { startHeartbeat, startEventStream } = require('./src/gep/a2aProtocol');
startHeartbeat();   // 保持 node 在 Hub 上的存活状态
startEventStream();  // SSE 事件流监听
```

**与进化循环解耦**：心跳和事件流在后台运行，确保 Hub 连接始终活跃，即使进化循环处于休眠状态。

---

## §5 核心设计模式

### §5.1 幂等状态机（Evolve + Solidify 双状态）

```
[evolve.run() 执行]
    ↓ 写入 last_run
[等待人工/solidify]
    ↓ 写入 last_solidify
[下次循环检测 isPendingSolidify]
```

- `last_run`：每次进化运行时更新
- `last_solidify`：每次固化成功/拒绝时更新
- `isPendingSolidify()`：比较 run_id 判断是否 pending

### §5.2 三层休眠乘数叠加

```
totalSleep = max(minSleepMs,
  (currentSleepMs + jitter)  // 指数退避基础
  * saturationMultiplier       // 饱和节流（1x/2x/4x）
  * omlsMultiplier             // OMLS 空闲调度（~0.5x~2x）
)
```

### §5.3 OMLS 空闲窗口 + 主动蒸馏

```javascript
const { getScheduleRecommendation } = require('./src/gep/idleScheduler');
const schedule = getScheduleRecommendation();
if (schedule.should_distill) {
  if (shouldDistillFromFailures()) {
    autoDistillFromFailures(); // 在空闲窗口主动提炼失败基因
  }
}
```

**创新点**：在"检测到空闲"时主动执行高成本操作（skill distillation），而不是等待下一次循环。

---

## §6 与其他模块的关系

```
index.js (main entry)
  ├── evolve.run()  → 进化主循环（signal → gene → outcome）
  ├── solidify()    → 基因固化（git commit + asset publish）
  ├── skillDistiller → 技能蒸馏（post-solidify 或 OMLS 空闲触发）
  ├── a2aProtocol  → Hub 心跳、事件流、fetch、publish
  ├── assetCallLog → 资产生命周期日志
  ├── idleScheduler → OMLS 空闲调度建议
  └── paths.js     → 目录路径解析
```

---

## §7 BlueCortexCE 借鉴路径

### P0（高优先级，可直接借鉴）

1. **单例锁机制**：防止多实例启动，BlueCortexCE Java 后端可用文件锁 + PID 检测实现
2. **未捕获异常防护**：累计 5 次 unhandledRejection 后安全退出，防止状态腐蚀
3. **进程级内存保护**：定期检测 RSS，超阈值时优雅重启（CE 可用 `ApplicationRunner` + 外部 watchdog）
4. **幂等状态机**：`last_run` / `last_solidify` 双状态比较，避免并发冲突

### P1（中优先级，需适配）

5. **自适应休眠退避**：快循环（< idleThresholdMs）指数退避，正常后重置；CE 可用于"快速连续请求"检测和节流
6. **三层休眠乘数叠加**：退避 × 饱和 × OMLS；CE 可用于"会话繁忙度"自适应调度
7. **Hub 心跳独立运行**：心跳/事件流与业务逻辑解耦；CE 的 OpenClaw 集成可借鉴（独立于主循环的连接保活）

### P2（低优先级，架构参考）

8. **detached child spawn 自杀重启**：进程级内存重置；CE 作为 JVM 进程，可用外部 systemd/supervisor 实现
9. **review --approve/--reject 双分支**：人工审核 + 状态标记；CE 的 extraction 结果审核可借鉴
10. **后置自动蒸馏触发**：solidify 成功后自动检查是否需要蒸馏；CE 的"观察积累阈值触发摘要生成"可类比

---

## §8 关键环境变量汇总

| 变量 | 默认值 | 用途 |
|------|--------|------|
| `EVOLVER_MIN_SLEEP_MS` | 2000 | 最小休眠 |
| `EVOLVER_MAX_SLEEP_MS` | 300000 | 最大休眠（5min） |
| `EVOLVER_IDLE_THRESHOLD_MS` | 500 | 快循环判定 |
| `EVOLVE_PENDING_SLEEP_MS` | 120000 | 待固化时休眠 |
| `EVOLVER_MAX_CYCLES_PER_PROCESS` | 100 | 单进程最大循环 |
| `EVOLVER_MAX_RSS_MB` | 500 | 内存上限（MB） |
| `EVOLVER_SUICIDE` | true | 自杀开关 |
| `EVOLVE_BRIDGE` | false | Bridge 模式 |
| `EVOLVER_VERBOSE` | false | 详细日志 |
| `EVOLVE_LOOP` | true | 循环模式标记 |

---

## §9 相关文档

| 文档 | 内容 |
|------|------|
| [19](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md) | `evolve.js` 进化主循环 |
| [27](./27-ops-suite-runtime-config-canary.md) | Ops 套件 + Canary |
| [45](./45-idleScheduler-OMLS-and-llmReview.md) | OMLS 空闲调度 |
| [34](./34-solidify-pipeline-end-to-end.md) | Solidify 固化管线 |
| [46](./46-hub-ecosystem-integration-taskreview-issue.md) | Hub 集成（taskReceiver + hubReview） |
| [49](./49-localStateAwareness-self-model-evolve-loop-full-integration.md) | localStateAwareness + evolve.js 集成 |
