# `forceUpdate.js` — Hub 心跳驱动的三通道强制版本迁移

**模块**：`src/forceUpdate.js`
**路径**：`EvoMap/evolver/src/forceUpdate.js`
**源码版本**：origin/main v1.78.9（`5304511`）
**本地工作树**：v1.47.0（`e72778e`）不包含此文件；来源为 origin/main
**行数**：100 行
**最后更新**：2026-05-05

---

## §1 背景与设计意图

### 1.1 为什么需要 ForceUpdate

Evolver 的进化循环（`evolve.js`）运行于主循环和心跳线程两种上下文。心跳专用 worker 从不进入 `run()` 主循环，因此无法消费 `pending force_update` 指令。将 `forceUpdate` 提取为独立模块，使两种上下文均可独立触发版本迁移。

**关键设计原则**：版本迁移与进化循环**解耦**——心跳触发也能执行强制更新，不依赖主循环进度。

### 1.2 Hub 驱动的版本契约

```javascript
// Hub 下发的 forceUpdate 信号格式
{
  required_version: ">=1.78.9",   // semver 前缀清理
  release_url: "https://github.com/..." // Channel 3 兜底
}
```

Hub 通过心跳信号（`heartbeatSignalsHandler`）检测版本差距，下发 `forceUpdate` 指令。Evolver 收到后通过 `executeForceUpdate()` 执行。

---

## §2 三通道更新机制

### Channel 1：GitHub Release（degit）

```javascript
execSync('npx -y degit EvoMap/evolver ' + tmpTarget, {...})
// 下载到临时目录 → 校验版本 → 原子替换
```

**关键细节**：
- 使用 `degit` 拉取最新 tarball（不走完整 git clone）
- 版本校验：`isAtLeast(current, required)` 三段 semver 比较
- **白名单保护**：跳过 `node_modules`、`memory`、`MEMORY.md`、`.git`（保留本地记忆和 git 历史）
- 替换策略：`fs.rmSync` → `fs.cpSync`（非 git merge，直接文件替换）
- 临时目录用 `.evolver-update-tmp` + `fs.rmSync` 清理

**安全观察**：`node_modules` 被保留（不重新 `npm install`），意味着 Channel 1 不更新依赖——仅更新业务代码。

### Channel 2：npm

```javascript
execSync('npm install -g @evomap/evolver@latest', {...})
```

**关键细节**：
- 全局安装 `@evomap/evolver` 包
- 超时 120s（比 degit 60s 更宽松）
- Channel 1 失败时降级到 Channel 2

### Channel 3：手动下载（兜底）

```javascript
if (releaseUrl) {
  console.log('[ForceUpdate] Visit: ' + releaseUrl);
}
```

仅打印 URL，要求用户手动下载。

---

## §3 关键设计观察

### 3.1 幂等性与原子性

| 特性 | 实现 |
|------|------|
| 版本校验 | `isAtLeast()` 三段比较，精确到 patch |
| 临时目录清理 | `try { fs.rmSync } catch (_) {}` 静默清理 |
| 文件替换原子性 | 无事务——`rmSync` + `cpSync` 分离，crash 会留下不完整状态 |
| 保护路径白名单 | `node_modules`、`memory`、`MEMORY.md`、`.git` 不删除 |

### 3.2 与心跳线程的集成

`forceUpdate.js` 从 `evolve.js` 提取的背景：心跳线程（`heartbeatSignalsHandler`）接收 Hub 信号，但心跳线程不执行 `run()` 循环。通过独立模块，心跳线程可直接调用 `executeForceUpdate()`。

```javascript
// evolve.js 中：
// Extracted from src/evolve.js so both the evolve main loop
// and heartbeat thread can trigger it independently
```

### 3.3 更新后不重新安装依赖

Channel 1（degit）跳过 `node_modules`，只更新业务代码。这意味着：
- 依赖包版本**不**随版本迁移更新
- 如果新版本需要新依赖，Channel 1 更新后会面临模块缺失
- Channel 2（npm）会安装完整依赖，但全局安装路径与本地开发路径可能不同

---

## §4 BlueCortexCE 借鉴评估

### P3（可选借鉴）：Agent 版本自迁移

CE 当前无 Agent 端版本迁移机制。若未来引入「Evolvable Agent」概念，可参考三通道策略：
- Channel 1：Git release → 原子文件替换
- Channel 2：Maven/npm 包更新
- Channel 3：手动指导

**CE 不应照搬**：CE 是旁路记忆系统，不是自主进化的 Agent。版本迁移属于运维层，不属于记忆系统架构核心。

### 更重要的借鉴：心跳与版本契约

Hub → Evolver 心跳信号中的 `forceUpdate` 契约，是**声明式版本约束**思想。CE 若有多实例协作，可参考：Hub 下发最低版本要求，Agent 自我检查并触发更新。

---

## §5 在 doc 体系中的位置

本文档覆盖 v1.78.9 新增模块，不属于核心记忆架构（Signal/Gene/MemoryGraph/Hub），属于 **ops/infrastructure** 补充分析。

**相关 doc**：
- [78](./78-v178-proxy-subsystem-architecture.md) — v1.78 Proxy 子系统架构（LifecycleManager hello/hb/reauth）
- [91](./91-atp-heartbeatsignalshandler-deep-dive.md) — ATP 心跳旁路交付
- [60](./60-evolver-ops-self-healing-infrastructure.md) — Ops 自我修复基础设施

**未覆盖模块**（v1.78.9 新增，已在其他 doc 分析）：
- `src/adapters/` — [79c](./79c-evolver-hook-adapter-system-deep-dive.md)
- `src/atp/` — [75](./75-atp-agent-transaction-protocol-and-adapters.md)、[81](./81-atp-execute-autodeliver-memorygraph-adapter-selfrepair.md)、[83](./83-atp-merchant-side-task-pickup-autobuyer-and-agent-templates.md)、[91](./91-atp-heartbeatsignalshandler-deep-dive.md)
- `src/proxy/` — [78](./78-v178-proxy-subsystem-architecture.md)
