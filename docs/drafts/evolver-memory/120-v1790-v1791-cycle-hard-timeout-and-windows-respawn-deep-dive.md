# v1.79.0 / v1.79.1 Delta 分析：Cycle Hard-Timeout + Windows Respawn + dotenv 修复 + build_binaries.js

**Doc #120** | cron 2026-05-06 13:56（build_binaries.js 部分追加于 2026-05-06 21:43）| 上游: `93e44a3` (v1.79.1) / `991b39b` (v1.79.0)

---

## 变更概览

| 文件 | 变更量 | 核心内容 |
|------|--------|---------|
| `index.js` | +89 −57 (≈+89L) | daemon 主循环重写，+3 个新工具函数 |
| `assets/gep/genes.json` | +93 −2 | +91 行新基因 |
| `assets/gep/capsules.json` | +4 | +4 capsule |
| `test/cycleHardTimeout.test.js` | 新增 127L | 回归测试：hard-timeout 结构验证 |
| `test/spawnReplacementProcess.test.js` | 新增 167L | 单元测试：Windows 跳过逻辑 |
| `src/evolve.js` | 2L bump | 版本同步 |
| 全模块 `.integrity` / `integrityCheck.js` 等 | 31 文件版本同步 | — |

---

## 1. Cycle Hard-Timeout（Issue #19）

**目标**：防止 `evolve.run()` 内部调用挂死（未关闭的 socket、LLM 卡死等）导致进程冻结数天。

### 实现机制

```javascript
// index.js — daemon 主循环
const cycleTimeoutEnabled = parseBoolEnv(process.env.EVOLVER_CYCLE_TIMEOUT_ENABLED, true);
const cycleTimeoutMs = parseMs(process.env.EVOLVER_CYCLE_TIMEOUT_MS, 2700000); // 45 min

// 1. 启动时写 progress 文件
writeCycleProgressAtomic(cycleProgressPath, { phase: 'evolve.run', cycleNum, startedAt });

// 2. progress ticker：每 30s 刷新进度
progressTicker = setInterval(() => {
  writeCycleProgressAtomic(cycleProgressPath, { phase: 'evolve.run', cycleNum, updatedAt });
}, progressUpdateMs);

// 3. 硬超时 timer
cycleTimeoutHandle = setTimeout(() => {
  reject(new CycleTimeoutError(cycleTimeoutMs, 'evolve.run', cycleCount));
}, cycleTimeoutMs);

// 4. Promise.race 竞速
await Promise.race([evolvePromise, timeoutPromise]);

// finally: 清理 timer + ticker
finally {
  clearInterval(progressTicker);
  clearTimeout(cycleTimeoutHandle);
}
```

### CycleTimeoutError 类

```javascript
class CycleTimeoutError extends Error {
  constructor(timeoutMs, phase, cycleNum) {
    super('Cycle hard-timeout exceeded after ' + timeoutMs + 'ms (cycle=' + cycleNum + ', phase=' + phase + ')');
    this.name = 'CycleTimeoutError';
    this.code = 'CYCLE_TIMEOUT';
    this.timeoutMs = timeoutMs;
    this.phase = phase;
    this.cycleNum = cycleNum;
  }
}
```

### 超时后处理

```javascript
// CYCLE_TIMEOUT 分支：force-respawn + exit(1)
if (error.code === 'CYCLE_TIMEOUT') {
  spawnReplacementProcess({ reason: 'cycle_hard_timeout', args, logPath });
  process.exit(1);
}
```

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `EVOLVER_CYCLE_TIMEOUT_ENABLED` | `true` | 是否启用硬超时 |
| `EVOLVER_CYCLE_TIMEOUT_MS` | `2700000` (45 min) | 超时阈值 |
| `progressUpdateMs` | ~30000ms | progress 文件刷新间隔 |

### 回归测试覆盖（`cycleHardTimeout.test.js`）

- `CycleTimeoutError` 是真正的 Error 子类，字段正确
- `parseBoolEnv` 布尔解析（`true/false/1/0/on/off/yes/no`）
- `Promise.race([evolvePromise, timeoutPromise])` 结构存在
- `parseMs(EVOLVER_CYCLE_TIMEOUT_MS, 2700000)` 默认 45 min
- `writeCycleProgressAtomic` 写入 `{phase: 'evolve.run'}` 
- `progressTicker = setInterval(...)` 周期性刷新
- `finally { clearInterval + clearTimeout }` 不泄漏句柄
- `CYCLE_TIMEOUT → spawnReplacementProcess + exit(1)`

---

## 2. Windows Respawn 修复（Issue #528）

### 问题

在 Windows 上，`child_process.spawn(detached: true, windowsHide: true)` 每次重启会分配新的 `conhost.exe` 窗口，导致 cmd 弹窗不断累积。

### 解决方案

`spawnReplacementProcess` 增加了 Windows 检测逻辑：

```javascript
function spawnReplacementProcess({ reason, args, logPath }) {
  const isWindows = process.platform === 'win32';
  const allowOnWindows = parseBoolEnv(process.env.EVOLVER_SUICIDE_WINDOWS, false);

  // Windows 默认跳过，依赖外部 supervisor 重启
  if (isWindows && !allowOnWindows) {
    console.log('[Daemon] Skipping in-process respawn on Windows. ' +
      'Set EVOLVER_SUICIDE_WINDOWS=true to opt back in. ' +
      'Recommended: run evolver under feishu-evolver-wrapper >= 1.10.0, NSSM, or pm2-windows.');
    return { spawned: false, reason: 'windows_default_skip' };
  }

  // 非 Windows 或显式 opt-in：正常执行
  const logFd = fs.openSync(logPath, 'a');
  // ... spawn logic
}
```

### 测试覆盖（`spawnReplacementProcess.test.js`）

- Windows 默认跳过 → `{spawned: false, reason: 'windows_default_skip'}`
- Windows + `EVOLVER_SUICIDE_WINDOWS=false` → 同上
- Windows + `EVOLVER_SUICIDE_WINDOWS=true` → 进入 spawn 尝试块
- 非 Windows 平台 → 跳过 Windows gate，正常执行

---

## 3. dotenv 加载顺序修复（Issue #460, #526）

### 问题

原代码在 `index.js` 底部才加载 `.env`：

```javascript
// 旧代码（buggy）
const { getRepoRoot } = require('./src/gep/paths');  // getRepoRoot() 在这里才被调用，但…
try { require('dotenv').config({ path: path.join(getRepoRoot(), '.env') }); }
```

实际上，`getRepoRoot()` 在 `require('./src/gep/paths')` 时就可能被其他模块间接调用（在 dotenv 加载之前），导致 `EVOLVER_REPO_ROOT` 环境变量在 `.env` 加载前就被缓存。

### 新代码（index.js 顶部）

```javascript
// Step 1: 从 process.cwd() 加载 .env（优先于 getRepoRoot 缓存）
require('dotenv').config({ path: _path.join(process.cwd(), '.env') });

// Step 2: 静默 parent git 发现 banner（避免误导）
process.env.EVOLVER_QUIET_PARENT_GIT = '1';
const { getRepoRoot: _getRepoRoot } = require('./src/gep/paths');
const _root = _getRepoRoot();

// Step 3: 如果 repo root 与 cwd 不同，从 root 路径二次加载 .env
if (_root && _root !== process.cwd()) {
  require('dotenv').config({ path: _path.join(_root, '.env') });
}
```

**关键点**：`cwd` 的 `.env` 优先级最高（`dotenv` 不会覆盖已存在的环境变量），确保用户在项目根目录运行的配置生效。

---

## 4. 基因/胶囊更新

- `genes.json`: 110L → 201L（+91 行，约 +201 个基因条目）
- `capsules.json`: +4 行

---

## 5. `scripts/build_binaries.js`（新增 388 行）

### 背景

v1.79.0 新增独立 CLI 二进制构建脚本，将 evolver 打包为**零依赖单文件可执行文件**（类似 Go `go build`）。

### 构建管线

```
1. bun build ./index.js --target=node --outfile=stage/bundled.js
   → 解析所有 require() 为单一自包含文件

2. javascript-obfuscator stage/bundled.js → stage/bundled.obf.js
   → 高强度混淆：stringArray(rc4) + controlFlowFlattening + deadCodeInjection
     + identifier hex + splitStrings + numbers-to-expr
   → selfDefending 必须关闭（bun standalone wrapper 会触发无限自保护循环）
   → renameGlobals 必须关闭（bun bundle 依赖动态 require 字符串解析）

3. bun build stage/bundled.obf.js --compile --minify --target=<TARGET>
   → 嵌入 bun runtime + 混淆后 JS → 单可执行文件
   → --minify 第二轮压缩
```

### 支持平台

```
bun-darwin-arm64  → evolver-darwin-arm64
bun-darwin-x64    → evolver-darwin-x64
bun-linux-x64     → evolver-linux-x64
bun-linux-arm64   → evolver-linux-arm64
bun-windows-x64   → evolver-windows-x64.exe
```

### 用法

```bash
node scripts/build_binaries.js                      # 构建所有目标
node scripts/build_binaries.js --target=darwin-arm64
node scripts/build_binaries.js --skip-obfuscate     # dev 快速路径
node scripts/build_binaries.js --dry-run            # 预览
```

### 输出产物

```
<outDir>/evolver-<platform>           可执行文件
<outDir>/evolver-<platform>.sha256    单文件 SHA256
<outDir>/SHA256SUMS.txt              汇总清单
```

### 退出码

| 码 | 含义 |
|----|------|
| 0 | 成功 |
| 1 | 前置条件失败 |
| 2 | 构建失败 |
| 3 | smoke test 失败 |

### 与 BlueCortexCE 的关联

**P3（低优先级）**：build_binaries.js 属于发布基础设施，非记忆系统核心。但间接价值：
- CE 未来若需 JS/TS 前端混淆分发，javascript-obfuscator 的配置经验（`selfDefending` 必须关闭）有参考价值
- 多平台二进制构建管线（bun + obfuscator + compile）是成熟方案

---

## BlueCortexCE 行动项

### P1：超时保护机制

Evolver 的 `Promise.race` + `CycleTimeoutError` 设计值得借鉴。BlueCortexCE 后端可以考虑：

- **API 层超时**：对每个 LLM 调用设置硬超时，防止无限等待
- **会话级别超时**：对长时间运行的会话注入进度心跳
- **参考实现**：`progressTicker` + `writeCycleProgressAtomic` 的原子写入模式

### P1：环境变量加载顺序

Evolver 的 dotenv 加载顺序 bug（Issue #526）是一个典型的前车之鉴。BlueCortexCE 后端应检查：

- Spring Boot 的 `application.properties` / `.env` 加载时机
- 是否存在类似的"配置在依赖模块初始化后才加载"的问题

### P2：跨平台进程管理

Evolver 的 Windows supervisor 模式建议（`feishu-evolver-wrapper >= 1.10.0`）适用于守护进程场景。BlueCortexCE 如有 daemon 组件，应考虑：

- 平台检测后选择合适的进程管理策略
- 外部 supervisor 的优雅降级

### P3：build_binaries.js 发布管线（参考）

如 CE 未来需 JS/TS 前端混淆分发，javascript-obfuscator 配置经验（`selfDefending` 必须关闭等）有参考价值。

---

## 相关文档

- 分析总入口：[`index.md`](./index.md)
- Changelog 条目：[`changelog-entries.md`](./changelog-entries.md)
