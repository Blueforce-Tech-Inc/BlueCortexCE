# `128` Binary Build Pipeline + Bootstrap Fix — v1.79.0 深度分析

**文件**: `docs/drafts/evolver-memory/128-v1790-v180-binary-build-and-bootstrap-deep-dive.md`
**目标**: 分析 v1.79.0 新增的 binary build pipeline 和 bootstrap 修复
**数据来源**: `scripts/build_binaries.js` (388L) + `index.js` diff (v1.79.0)
**版本**: v1.79.0 (991b39b)

---

## 1. Binary Build Pipeline (`scripts/build_binaries.js`)

### 1.1 目标

将 evolver Node.js 应用打包为独立的平台二进制文件（无需安装 Node.js 即可运行）。

### 1.2 三阶段管线

```
Stage 1: bun build ./index.js --target=node --outfile=stage/bundled.js
  → 将所有 require() 解析为单一自包含文件

Stage 2: javascript-obfuscator stage/bundled.js → stage/bundled.obf.js
  → 高强度混淆配置：stringArray (rc4) + controlFlowFlattening +
    deadCodeInjection + identifier hex + splitStrings + numbers-to-expr

Stage 3: bun build stage/bundled.obf.js --compile --minify --target=<TARGET>
  → 嵌入 bun runtime + 混淆后 JS → 独立可执行文件
```

### 1.3 混淆器关键配置决策

```javascript
selfDefending: MUST be off
  // selfDefending 在 bun 独立容器中会触发无限循环自防御

renameGlobals: MUST be off
  // 避免 bun bundle 步骤无法解析动态 require 字符串

transformObjectKeys: MUST be off
  // 同上原因
```

### 1.4 支持平台

| 目标 | 输出文件名 |
|------|----------|
| `bun-darwin-arm64` | evolver-darwin-arm64 |
| `bun-darwin-x64` | evolver-darwin-x64 |
| `bun-linux-x64` | evolver-linux-x64 |
| `bun-linux-arm64` | evolver-linux-arm64 |
| `bun-windows-x64` | evolver-windows-x64.exe |

### 1.5 输出产物

```
<outDir>/
  evolver-<platform>          二进制文件
  evolver-<platform>.sha256  单文件 hash
  SHA256SUMS.txt             组合 hash 清单（用于批量验证）
```

### 1.6 退出码

| 退出码 | 含义 |
|--------|------|
| 0 | 成功 |
| 1 | 前置条件失败（工具缺失或版本不匹配） |
| 2 | 构建步骤失败 |
| 3 | 产出 binary smoke test 失败 |

### 1.7 使用方式

```bash
node scripts/build_binaries.js                    # 构建所有 4 个目标
node scripts/build_binaries.js --target=darwin-arm64  # 指定目标
node scripts/build_binaries.js --skip-obfuscate   # 快速路径（仅 bun bundle）
node scripts/build_binaries.js --out-dir=dist-binaries
node scripts/build_binaries.js --dry-run          # 模拟运行
```

### 1.8 CE 借鉴意义 (P3)

**P3 - 长期参考**:
- BlueCortexCE 目前无 binary 分发需求，但未来如需分发 Java binary，可参考类似管线思路（Maven shade/jar + 混淆）
- SHA256SUMS 组合 hash 清单是标准二进制分发验证模式

---

## 2. Bootstrap 修复 (index.js — v1.79.0)

### 2.1 问题背景

v1.79.0 修复了两个 bootstrap 时序问题：

**Issue #460**: ATP 模块（a2aProtocol 等）在初始化时无法看到 `A2A_NODE_SECRET` / `A2A_NODE_ID` / `A2A_HUB_URL`，因为这些环境变量在 .env 文件中，而 dotenv 加载时机太晚。

**Issue #526**: `getRepoRoot()` 在首次调用时会缓存 .git 查找结果，如果在此之前 .env 未加载，`EVOLVER_REPO_ROOT` 会被静默忽略。这是典型的"鸡生蛋"问题。

### 2.2 修复策略

```javascript
// Step 1: 从 process.cwd() 加载 .env（在任何内部 require 之前）
require('dotenv').config({ path: _path.join(process.cwd(), '.env') });

// Step 2: 静默获取 repo root（抑制 "Using host git repository" 横幅）
const _prevQuiet = process.env.EVOLVER_QUIET_PARENT_GIT;
process.env.EVOLVER_QUIET_PARENT_GIT = '1';
const { getRepoRoot: _getRepoRoot } = require('./src/gep/paths');
const _root = _getRepoRoot();

// Step 3: 如果 repo root 与 cwd 不同，从 repo root 再次加载 .env
if (_root && _root !== process.cwd()) {
  require('dotenv').config({ path: _path.join(_root, '.env') });
}

// dotenv 不会覆盖已存在的 key，所以 Step 1 优先
```

### 2.3 关键设计原则

- **cwd .env 优先**: 用户在项目目录运行 `evolver`，期望读取项目目录的 .env
- **repo root .env 次之**: 如果 repo root 与 cwd 不同，从 repo root 补充加载
- **防缓存污染**: `getRepoRoot()` 调用前设置 `EVOLVER_QUIET_PARENT_GIT=1`，避免横幅误导
- **可恢复**: 临时覆盖 `EVOLVER_QUIET_PARENT_GIT`，finally 块恢复原值

### 2.4 CE 借鉴意义 (P2)

**P2 - 值得借鉴**:
- BlueCortexCE Java 后端使用 Spring Boot 的 `.env` 支持（如有），应验证环境变量加载时序
- Claude Code CLI Hook (`proxy/wrapper.js`) 如果有类似环境变量注入需求，应参考此模式确保 .env 在模块加载前可用
- `dotenv 不覆盖已存在 key` 的特性是设计关键：cwd .env 优先级 > repo root .env

---

## 3. Windows Spawn Fix (v1.79.1)

### 3.1 Issue #528

**问题**: 在 Windows 上，`child_process.spawn(detached: true, windowsHide: true)` 会在每次 daemon 自杀-重启时打开新的 conhost 窗口。当 daemon 达到 `EVOLVER_MAX_CYCLES` (100) 或 `EVOLVER_MAX_RSS_MB` (500) 时，cmd 弹窗不断出现。

### 3.2 修复方案

v1.79.1 使 Windows 上的进程替换变为**可选项**，回退到"退出非零 + 让 supervisor 负责重启"模式：

```javascript
// Windows: 不在进程内 respawn，而是优雅退出让 supervisor 处理
// Unix: 继续使用 in-process respawn
```

相关测试: `test/spawnReplacementProcess.test.js` (167L)，通过 `withPlatform()` helper 临时覆盖 `process.platform` 进行跨平台测试。

### 3.3 CE 借鉴意义 (P3)

**P3 - 长期参考**:
- BlueCortexCE 后端运行在 JVM 上，不存在此问题
- 但如果未来有 spawn 子进程场景（如 OpenClaw CLI Hook wrapper），应参考此模式处理 Windows 兼容
