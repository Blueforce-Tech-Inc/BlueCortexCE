# 13 — 安全、并发与运维保障系统

## 13.1 整体定位

前 12 个文档聚焦于 EvoMap 的**认知与学习核心**：信号提取、基因选择、图推理、课程学习。但一个生产级自进化系统还需要完善的**安全护栏**——防止自我破坏、保护敏感信息、保证并发安全、支持故障恢复。

本章涵盖 6 大保障系统：

| 保障层 | 文件 | 职责 |
|--------|------|------|
| **并发控制** | `assetStore.js` (内嵌) | JSON 文件的读-改-写事务锁 |
| **GitOps 回滚** | `assetStore.js` (内嵌) | 变更失败后恢复到 baseline |
| **首次引导** | `assetStore.js` (内嵌) | npm upgrade 不覆盖用户积累的基因库 |
| **隐私脱敏** | `sanitize.js` | 发布前清除 API key、路径、邮箱等敏感信息 |
| **泄漏扫描** | `sanitize.js` | 检测 diff 中是否意外包含 secrets |
| **自我 PR** | `selfPR.js` | 高置信度变更自动贡献回公开仓库 |

---

## 13.2 并发控制：文件锁

### 背景问题

多个进程可能同时操作 `genes.json`：
- 后台 daemon（持续运行）
- CLI 脚本（按需触发）
- Cron 定时任务

`writeJsonAtomic()` 保证单次写入的原子性，但不保护**读-改-写**整个事务。

### 解决方案：O_EXCL 锁文件

```javascript
function withFileLock(targetPath, fn) {
  const lockPath = targetPath + '.lock';
  const lockPath = _acquireLock(targetPath);  // O_EXCL 原子创建
  try {
    return fn();  // 临界区：读-改-写
  } finally {
    _releaseLock(lockPath);
  }
}

function _acquireLock(targetPath) {
  const lockPath = targetPath + '.lock';
  const deadline = Date.now() + LOCK_TIMEOUT_MS;  // 5 秒超时
  while (Date.now() < deadline) {
    try {
      fs.writeFileSync(lockPath, String(process.pid), { flag: 'wx' }); // O_EXCL
      return lockPath;
    } catch (e) {
      if (e.code !== 'EEXIST') throw e;
      // 锁存在：检查是否 stale（owner PID 不再存活）
      const pid = parseInt(fs.readFileSync(lockPath, 'utf8'), 10);
      try { process.kill(pid, 0); }  // OS 会报 ESRCH
      catch { fs.unlinkSync(lockPath); continue; }  // 进程已死，回收锁
      _busyWait(LOCK_RETRY_INTERVAL_MS);  // 20ms 轮询
    }
  }
  throw new Error('[AssetStore] Lock timeout for: ' + targetPath);
}
```

**设计要点**：
- `flag: 'wx'` = O_EXCL，原子性保证只创建一个进程能获得锁
- Stale lock 检测：读取锁文件中的 PID，用 `process.kill(pid, 0)` 验证进程是否存活
- Busy-wait 20ms 轮询，5 秒总超时

---

## 13.3 GitOps 回滚

### 双重回滚策略

`solidify()` 在验证失败时执行回滚，分两个维度：

```javascript
// 1. 还原已跟踪文件的变更（staged + unstaged）
rollbackTracked(repoRoot, mode);
// mode: 'hard'（默认）→ git reset --hard
// mode: 'stash'         → git stash push（保留证据）
// mode: 'none'         → 跳过

// 2. 删除新增的未跟踪文件（排除关键文件）
rollbackNewUntrackedFiles({ repoRoot, baselineUntracked });
```

### 关键文件保护

以下文件和目录**永不删除**（`isCriticalProtectedPath`）：

```javascript
const CRITICAL_PROTECTED_PREFIXES = [
  'skills/feishu-evolver-wrapper/',
  'skills/feishu-common/',
  'skills/feishu-post/',
  'skills/feishu-doc/',
  'skills/skill-tools/',
  'skills/git-sync/',
  'skills/evolver/',
];

const CRITICAL_PROTECTED_FILES = [
  'MEMORY.md', 'SOUL.md', 'IDENTITY.md', 'AGENTS.md', 'USER.md',
  'HEARTBEAT.md', 'RECENT_EVENTS.md', 'TOOLS.md', 'TROUBLESHOOTING.md',
  'openclaw.json', 'evolver.json', '.env', 'package.json',
];
```

---

## 13.4 首次引导：升级不丢数据

### 问题

npm 全局安装的 evolver 会在每次 `npm i -g @evomap/evolver` 时更新。如果基因库存储在 `node_modules` 内，用户的积累会被覆盖。

### 解决方案：Seed 文件隔离

```
genes.json          ← 用户积累（存在 gepAssetsDir，永远不覆盖）
genes.seed.json     ← npm 包内（只读，初始基因模板）
genes.jsonl         ← 增量追加（append-only）
```

```javascript
function ensureGenesSeeded() {
  const target = genesPath();         // 用户基因库路径
  if (fs.existsSync(target)) return; // 已有用户基因库，跳过

  const seed = genesSeedPath();       // npm 包内的 seed 文件
  if (!fs.existsSync(seed)) return;
  try {
    fs.copyFileSync(seed, target);   // 首次使用：从 seed 初始化
  } catch (e) { /* 非致命：继续运行 */ }
}
```

---

## 13.5 隐私脱敏（Sanitization）

### sanitize.js 概览

`sanitize.js` 是 Hub 发布前的**最后一道防线**，确保共享的 Capsule 内容不包含敏感信息。

### 脱敏模式（26 种）

```javascript
const REDACT_PATTERNS = [
  // 通用凭证
  /sk-[A-Za-z0-9]{20,}/g,          // OpenAI sk-
  /sk-proj-[A-Za-z0-9\-_]{20,}/g,  // OpenAI project key
  /sk-ant-[A-Za-z0-9\-_]{20,}/g,   // Anthropic key
  /ghp_[A-Za-z0-9]{36,}/g,         // GitHub personal token
  /github_pat_[A-Za-z0-9_]{22,}/g, // GitHub PAT
  /AKIA[0-9A-Z]{16}/g,              // AWS access key
  /xox[baprsv]-[A-Za-z0-9-]{10,}/g, // Slack token
  /eyJ[A-Za-z0-9_\-]+\.eyJ[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]{20,}/g, // JWT
  /-----BEGIN\s+.*PRIVATE\s+KEY-----[\s\S]*?-----END.*PRIVATE\s+KEY-----/g,

  // 路径
  /\/home\/[^\s"']+/g,             // Unix home dir
  /\/Users\/[^\s"']+/g,            // macOS home dir
  /[A-Z]:\\[^\s"']+/g,             // Windows path

  // 邮件
  /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g,

  // .env 引用
  /\.env(?:\.[a-zA-Z]+)?/g,
];
```

### 泄漏扫描（Detection-Only）

与脱敏不同，扫描模式**只检测不修改**，用于安全审计：

```javascript
// scanForLeaks: 返回结构化结果，含建议的 env 变量名
{ found: true, leaks: [
  { type: 'github_token', value: 'ghp_xxxx...', suggestion: 'process.env.GITHUB_TOKEN' },
  { type: 'local_path', value: '/Users/john/...', suggestion: 'process.env.HOME' },
]}
```

---

## 13.6 Self-PR：自动贡献改进

### 入参要求（全部满足才触发）

```javascript
const SELF_PR_MIN_SCORE = 0.82;    // PRM 得分 ≥ 0.82
const SELF_PR_MIN_STREAK = 3;       // 成功 streak ≥ 3
const SELF_PR_MAX_FILES = 8;        // 变更文件 ≤ 8
const SELF_PR_MAX_LINES = 400;      // 变更行数 ≤ 400
const SELF_PR_COOLDOWN_MS = 24h;    // 24 小时内不重复
```

**基因类别限制**：只有 `category=optimize` + `risk=low` 的变更才可 PR。

### Diff 幂等性

```javascript
// 同一份 diff 内容不会产生两个 PR
computeDiffHash(changedFiles, repoRoot):
  for each file: content = fs.readFileSync(abs)
  combined = file:content 拼接
  return SHA256(combined).slice(0, 16)
```

---

## 13.7 ATP：市场驱动的任务处理

ATP（Autonomous Task Processing）是 EvoMap 的**市场经济层**：Hub 作为 marketplace，买家发布任务（附 bounty），节点认领并执行：

```
Buyer → Hub (marketplace) → EvoMap Node (merchant agent)
         ↑                              ↓
         └────── task/complete ← Answer ─┘
```

### 能力匹配（ROI 驱动的任务选择）

```javascript
// 节点对每个任务计算 ROI
ROI = bounty × 0.80 + capability × 0.05 + completion × 0.05  // greedy
ROI = bounty × 0.35 + capability × 0.30 + completion × 0.20  // balanced
ROI = bounty × 0.05 + capability × 0.45 + completion × 0.25  // conservative

// capability = 基于节点历史信号的 Jaccard 成功率
// completion = 已完成任务数 / 已认领任务数
```

---

## 13.8 关键设计哲学总结

### 纵深防御（Defense in Depth）

```
变更申请
    │
    ▼
┌─────────────────────────────────────────┐
│ 1. 约束检查（constraints.max_files）    │ ← 变更范围限制
├─────────────────────────────────────────┤
│ 2. 验证命令（validation scripts）       │ ← 功能正确性
├─────────────────────────────────────────┤
│ 3. 金丝雀检查（runCanaryCheck）          │ ← 运行时加载测试
├─────────────────────────────────────────┤
│ 4. LLM Review（可选）                   │ ← 语义审查
├─────────────────────────────────────────┤
│ 5. PRM 评分（computeProcessScores）       │ ← 多维度质量评估
├─────────────────────────────────────────┤
│ 6. 失败回滚（rollbackTracked）           │ ← 故障恢复
├─────────────────────────────────────────┤
│ 7. PR 门控（Self-PR 6 项条件）          │ ← 发布安全
└─────────────────────────────────────────┘
```

### 隐私优先（Privacy First）

- **脱敏**（发布前清理）胜于**禁止**（不许包含）
- **扫描**（检测但不修改）提供可审计性
- **反转检测**（env 值泄漏）防止隐蔽的信息外泄

### Claude-Mem 差距分析

| 保障机制 | EvoMap | Claude-Mem |
|-----------|--------|------------|
| 并发写保护 | ✅ 文件锁 | ❌ 无（单进程设计） |
| 变更回滚 | ✅ GitOps | ❌ 无 |
| 隐私脱敏 | ✅ 26 种模式 | ❌ 无 |
| 升级不丢数据 | ✅ Seed 机制 | ⚠️ 无（依赖外部 DB） |
| Self-PR 贡献 | ✅ 6 重门控 | ❌ 无 |
| ATP 市场层 | ✅ 完整 | ❌ 无（仅有 Hub search） |
| 关键文件保护 | ✅ 20+ 路径 | ❌ 无 |

---

_共 13 个子文档 | EvoMap/evolver 记忆系统分析完毕_
