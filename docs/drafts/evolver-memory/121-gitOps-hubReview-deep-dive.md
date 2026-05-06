# `gitOps.js` + `hubReview.js` 深度分析

**Doc**: 121  
**源码**: `EvoMap/evolver/src/gep/gitOps.js` (230L) + `src/gep/hubReview.js` (206L, v1.47.0)  
**日期**: 2026-05-06  
**目标**: 分析 Evolver 的 Git 操作安全系统和 Hub 资产评审提交机制，这两类模块均未被专项深度分析。

---

## 1. `gitOps.js`：Git 操作安全与回滚引擎

### 1.1 命令执行安全

```javascript
function runCmd(cmd, opts = {}) {
  const cwd = opts.cwd || getRepoRoot();
  const timeoutMs = Number.isFinite(Number(opts.timeoutMs)) ? Number(opts.timeoutMs) : 120000;
  return execSync(cmd, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: timeoutMs, windowsHide: true });
}
```

**安全设计**：
- `stdio: ['ignore', 'pipe', 'pipe']` —— 防止 stdin 交互式输入注入
- `windowsHide: true` —— Windows 隐藏控制台窗口
- 超时保护（默认 120s，可覆盖）
- `tryRunCmd` 封装：所有 git 操作都通过 tryRunCmd 捕获异常，不直接 throw

### 1.2 路径规范化和遍历保护

```javascript
function normalizeRelPath(relPath) {
  return String(relPath || '').replace(/\\/g, '/').replace(/^\.\/+/, '').trim();
}
```

**关键保护链**：

```javascript
// rollbackNewUntrackedFiles 中的双重保护
if (!normAbs.startsWith(normRepo + path.sep) && normAbs !== normRepo) continue;  // 路径遍历检测
```

- Windows 反斜杠 → 正斜杠统一
- 去除 `./` 前缀防止 `../../../etc/passwd` 攻击
- 回滚删除时检查 `normAbs.startsWith(normRepo + path.sep)` 防止跨仓库删除

### 1.3 关键文件保护名单

```javascript
const CRITICAL_PROTECTED_PREFIXES = [
  'skills/feishu-evolver-wrapper/',
  'skills/feishu-common/',
  // ... 10 个 skills 目录
];

const CRITICAL_PROTECTED_FILES = [
  'MEMORY.md', 'SOUL.md', 'IDENTITY.md', 'AGENTS.md', 'USER.md',
  'HEARTBEAT.md', 'RECENT_EVENTS.md', 'TOOLS.md',
  'openclaw.json', '.env', 'package.json',
];
```

**设计决策**：即使在 rollback 场景下，这些文件也不会被删除。这是 AI 自我保护的基础。

### 1.4 回滚策略（三种模式）

| 模式 | 环境变量 | 行为 |
|------|---------|------|
| `hard`（默认） | `EVOLVER_ROLLBACK_MODE=hard` | `git restore --staged --worktree .` + `git reset --hard` |
| `stash` | `EVOLVER_ROLLBACK_MODE=stash` | `git stash push -m "evolver-rollback-{timestamp}"` |
| `none` | `EVOLVER_ROLLBACK_MODE=none` | 完全跳过 |

**`stash` 模式容错**：如果 stash 失败（无变更），自动 fallback 到 hard reset。

### 1.5 变更快照捕获

```javascript
const DIFF_SNAPSHOT_MAX_CHARS = 8000;

function captureDiffSnapshot(repoRoot) {
  // 合并 unstaged + staged diff，截断到 8000 字符
}
```

用于在回滚前记录变更快照（可用于事后审计或重放）。

### 1.6 BlueCortexCE 借鉴价值

| 优先级 | 借鉴点 | 具体实现 |
|--------|--------|---------|
| **P1** | 关键文件保护 | CE 的 SOUL.md、HEARTBEAT.md、MEMORY.md 等应加入保护名单 |
| **P1** | 路径遍历保护 | `path.resolve(normAbs).startsWith(normRepo)` 检查 |
| **P2** | 回滚策略 | 重大变更前 `git stash`，失败后自动恢复 |
| **P3** | 变更快照 | 写入前记录 diff 快照，用于异常恢复或人工审查 |

---

## 2. `hubReview.js`：Hub 资产评审提交机制

### 2.1 核心设计

`hubReview.js` 在 `solidify` 完成后，**异步提交** Hub 资产使用评审。评审仅针对**复用型资产**（`source_type = 'reused'` 或 `'reference'`），全新基因不提交评审。

### 2.2 评分推导

```javascript
function _deriveRating(outcome, constraintCheck) {
  if (outcome && outcome.status === 'success') {
    const score = Number(outcome.score) || 0;
    return score >= 0.85 ? 5 : 4;        // 成功：4-5 分
  }
  const hasViolation = constraintCheck?.violations?.length > 0;
  return hasViolation ? 1 : 2;           // 失败：1-2 分（有无违规）
}
```

**双因素评分**：outcome status × constraint violation 组合成 4 档评分。

### 2.3 防重复提交

```javascript
// hub_review_history.json 本地记录
history[assetId] = { at: Date.now(), rating, success };
```

- 本地 JSON 文件记录已提交评审的 assetId（最多 500 条，超出则按时间淘汰最老记录）
- Hub 返回 `already_reviewed` 错误码时也更新本地记录
- **非阻塞**：Hub 评审失败不影响 solidify 结果

### 2.4 评审内容构建

```javascript
function _buildReviewContent({ outcome, gene, signals, blast, sourceType }) {
  // 拼接：Outcome + Reuse mode + Gene + Signals(≤6) + Blast radius + 简短描述
  // 总长度截断到 2000 字符
}
```

### 2.5 异步非阻塞设计

```javascript
// 10s 超时
const timer = setTimeout(() => controller.abort('hub_review_timeout'), 10000);
const res = await fetch(endpoint, { method: 'POST', signal: controller.signal });
clearTimeout(timer);

// 错误处理：不 throw，不影响主流程
catch (err) {
  console.log('[HubReview] Failed (non-fatal, ' + reason + '): ' + err.message);
  return { submitted: false, reason, error: err.message };
}
```

### 2.6 BlueCortexCE 借鉴价值

| 优先级 | 借鉴点 | 具体实现 |
|--------|--------|---------|
| **P2** | 复用资产评审 | CE 复用 Hub 能力资产时，可提交匿名使用评审（成功/失败 + 简短理由） |
| **P3** | 防重复机制 | 本地 JSON 文件记录已处理 ID，防止重复处理 |
| **P3** | 评分推导 | outcome status × constraint violation → 4 档评分 |

---

## 3. 两模块的关系

```
solidify.js (Phase 7: solidify complete)
    ↓
gitOps.rollbackTracked()       ← 失败时触发，保护工作树
gitOps.rollbackNewUntrackedFiles()  ← 同上
    ↓
hubReview.submitHubReview()    ← 复用资产时异步提交评审（非阻塞）
```

**职责分离**：`gitOps` 负责**自我修复**（失败后恢复工作树），`hubReview` 负责**生态贡献**（向 Hub 反馈资产质量）。

---

## 4. 与 doc 99（evolve.js 安全系统）的关系

doc 99 的 `evolve.js` 安全系统处理**进化前**的自我保护（竞态检测、CWD 验证等），而 `gitOps.js` 处理**失败后**的自我修复。两者互补：

- **进化前**：evolve.js 安全检查
- **进化后**：gitOps.js 回滚修复
- **进化外**：hubReview.js 生态贡献

---

## 总结

| 模块 | 行数 | 核心功能 | CE 借鉴优先级 |
|------|------|---------|------------|
| `gitOps.js` | 230 | Git 安全操作 + 回滚策略 + 关键文件保护 | **P1** |
| `hubReview.js` | 206 | Hub 资产评审提交（非阻塞 + 防重复） | P3 |

**P0/P1 行动项**：
1. 🔴 **P1**：`gitOps.js` 的关键文件保护名单模式 → CE 应为 `SOUL.md/HEARTBEAT.md/MEMORY.md` 等添加白名单保护
2. 🔴 **P1**：`gitOps.js` 的路径遍历保护 `startsWith(repoRoot)` 检查 → CE 写入文件时应验证目标路径在允许范围内
