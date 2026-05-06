# `paths.js` 路径架构 + Session Scope 隔离深度分析

**Doc**: 122  
**源码**: `EvoMap/evolver/src/gep/paths.js` (133L, v1.47.0)  
**日期**: 2026-05-06  
**目标**: 分析 Evolver 的集中路径管理和多租户 Session Scope 隔离机制——这是理解整个 evolver 目录结构的钥匙。

---

## 1. 架构定位

`paths.js` 是 Evolver 的**路径中枢**，所有其他模块通过 `require('./paths')` 获取路径，确保路径计算逻辑集中一处、修改可追溯。

```
memoryGraph.js ──require──→ paths.getEvolutionDir()
solidify.js    ──require──→ paths.getRepoRoot()
hubReview.js   ──require──→ paths.getEvolutionDir()
gitOps.js     ──require──→ paths.getRepoRoot()
```

---

## 2. Repo Root 安全检测

```javascript
function getRepoRoot() {
  // 1. 显式环境变量优先
  if (process.env.EVOLVER_REPO_ROOT) return process.env.EVOLVER_REPO_ROOT;

  // 2. 检查自身目录（防止误操作父仓库）
  const ownDir = path.resolve(__dirname, '..', '..');
  if (fs.existsSync(path.join(ownDir, '.git'))) return ownDir;

  // 3. 向上搜索 .git，检测到时警告
  let dir = path.dirname(ownDir);
  while (dir !== '/' && dir !== '.') {
    if (fs.existsSync(path.join(dir, '.git'))) {
      if (process.env.EVOLVER_USE_PARENT_GIT === 'true') return dir;
      // 静默返回 ownDir（安全默认）
      return ownDir;
    }
    dir = path.dirname(dir);
  }
  return ownDir;
}
```

**关键设计**：
- **自身目录优先**：先检查 `evolver/` 自身是否有 `.git`，防止误用父仓库（可能导致 `git reset --hard` 在错误范围执行）
- **静默安全默认值**：检测到父仓库 `.git` 但未设置 `EVOLVER_USE_PARENT_GIT=true` 时，静默返回 evolver 自身目录（不抛出异常）

---

## 3. Session Scope 隔离（多租户路径）

### 3.1 核心机制

```javascript
function getSessionScope() {
  const raw = String(process.env.EVOLVER_SESSION_SCOPE || '').trim();
  if (!raw) return null;

  // 路径遍历防护：只允许 alphanumeric, dash, underscore, dot
  const safe = raw.replace(/[^a-zA-Z0-9_\-\.]/g, '_').slice(0, 128);

  // 防护：不允许纯点、点点等路径攻击
  if (!safe || /^\.{1,2}$/.test(safe) || /\.\./.test(safe)) return null;
  return safe;
}

function getEvolutionDir() {
  const baseDir = process.env.EVOLUTION_DIR || path.join(getMemoryDir(), 'evolution');
  const scope = getSessionScope();
  if (scope) return path.join(baseDir, 'scopes', scope);
  return baseDir;
}
```

### 3.2 目录结构

```
# 无 scope（全局，默认兼容）
evolution/
  memory_graph.jsonl
  evolution_narrative.md

# 有 scope（多租户）
evolution/
  scopes/
    discord_12345/
      memory_graph.jsonl
      evolution_narrative.md
    project_alpha/
      memory_graph.jsonl
      evolution_narrative.md
```

### 3.3 路径遍历防护

**三层防护**：
1. `replace(/[^a-zA-Z0-9_\-\.]/g, '_')` —— 白名单字符集
2. `.slice(0, 128)` —— 长度限制
3. `/^\.{1,2}$/.test(safe) || /\.\./.test(safe)` —— 禁止纯点或点点

### 3.4 BlueCortexCE 借鉴价值

| 优先级 | 借鉴点 | 具体实现 |
|--------|--------|---------|
| **P1** | Session Scope 隔离 | CE 可用 `EVOLVER_SESSION_SCOPE` 模式实现多租户记忆隔离（如不同项目、不同会话链） |
| **P1** | 路径遍历防护 | CE 所有路径拼接都应经过 `normalizePath` 检查白名单字符 |
| **P2** | 集中路径管理 | CE 应创建 `PathConfig.java` 集中管理所有存储路径（避免散落在各处） |

---

## 4. Workspace Root 的演进

```javascript
// 旧版（错误假设 OpenClaw 布局）
let dir = path.dirname(path.dirname(path.dirname(path.dirname(__dirname))));

// 新版（v1.47.0）：更清晰的逻辑
function getWorkspaceRoot() {
  if (process.env.OPENCLAW_WORKSPACE) return process.env.OPENCLAW_WORKSPACE;

  const repoRoot = getRepoRoot();
  const workspaceDir = path.join(repoRoot, 'workspace');
  if (fs.existsSync(workspaceDir)) return workspaceDir;

  // Standalone / Cursor / non-OpenClaw：使用 repo root 自身
  return repoRoot;
}
```

**演进背景**：旧版 4 层 `__dirname` 上溯假设 OpenClaw 的 `skills/evolver/` 布局，在 Cursor/Codex 等非 OpenClaw 环境下解析错误。新版显式优先 `OPENCLAW_WORKSPACE` 环境变量，fallback 到 repoRoot，行为可预测。

---

## 5. 所有路径函数一览

| 函数 | 用途 | 环境变量覆盖 |
|------|------|------------|
| `getRepoRoot()` | git 仓库根目录 | `EVOLVER_REPO_ROOT` |
| `getWorkspaceRoot()` | 工作空间目录 | `OPENCLAW_WORKSPACE` |
| `getLogsDir()` | 日志目录 | `EVOLVER_LOGS_DIR` |
| `getEvolverLogPath()` | 主日志路径 | `EVOLVER_LOGS_DIR` |
| `getMemoryDir()` | 记忆目录 | `MEMORY_DIR` |
| `getEvolutionDir()` | 进化状态目录 | `EVOLUTION_DIR` |
| `getGepAssetsDir()` | GEP 资产目录 | `GEP_ASSETS_DIR` |
| `getSkillsDir()` | 技能目录 | `SKILLS_DIR` |
| `getNarrativePath()` | 进化叙事文件 | `getEvolutionDir()` |
| `getReflectionLogPath()` | 反思日志 | `getEvolutionDir()` |

---

## 6. 与 doc 80（config 集中化）的关系

doc 80 分析了 `src/config.js` 集中配置 magic number；`paths.js` 是路径配置的集中化。两者共同构成 evolver 的「配置中枢」设计：

- **config.js**：运行时参数（阈值、超时、比例）
- **paths.js**：文件系统路径（目录、文件位置）

---

## 总结

| 模块 | 行数 | 核心功能 | CE 借鉴优先级 |
|------|------|---------|------------|
| `paths.js` | 133 | 集中路径管理 + 多租户 Scope 隔离 + 路径遍历防护 | **P1** |

**P0/P1 行动项**：
1. 🔴 **P1**：`paths.js` 的 Session Scope 隔离模式 → CE 应实现多租户路径隔离（`evolution/scopes/{scope}/`）
2. 🔴 **P1**：所有路径拼接白名单字符验证 → CE `PathConfig.java` 应拒绝包含 `..` 或非法字符的路径
3. 🟡 **P2**：集中路径管理 → CE 创建 `PathConfig.java` 统一管理所有数据目录
