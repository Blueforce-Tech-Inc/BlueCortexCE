# `94` v1.78.7–v1.78.9 版本差分与回归测试护栏

**模块**: `index.js` / `memoryGraph.js` / `evolve.js` / `test/`
**定位**: 版本增量同步 + 新增回归测试护栏分析
**版本**: v1.78.7 (`d2a6620`) / v1.78.8 (`2b3c046`) / v1.78.9 (`5304511`)

---

## 1. v1.78.7 (`d2a6620`) — dotenv 加载顺序修复 #526

### 问题背景

**Issue #526**: `getRepoRoot()` 在首次调用时缓存结果，导致 `.env` 中设置的 `EVOLVER_REPO_ROOT` 被静默忽略。

```
旧加载顺序:
1. require('./src/gep/paths')         ← getRepoRoot() 首次执行并缓存
2. require('dotenv').config()           ← 此时 EVOLVER_REPO_ROOT 已缓存，无效
3. getRepoRoot() 返回错误的根路径
```

### 修复方案（`index.js` +32 行）

```javascript
// Step 1: load .env from process.cwd() BEFORE any internal require
require('dotenv').config({ path: _path.join(process.cwd(), '.env') });

// Step 2: suppress banner during bootstrap
const _prevQuiet = process.env.EVOLVER_QUIET_PARENT_GIT;
process.env.EVOLVER_QUIET_PARENT_GIT = '1';

// Step 3: now call getRepoRoot() — EVOLVER_REPO_ROOT 已从 dotenv 加载
const { getRepoRoot: _getRepoRoot } = require('./src/gep/paths');
const _root = _getRepoRoot();

// Step 4: 如果 repo root 与 cwd 不同，也加载 repo root 下的 .env
if (_root && _root !== process.cwd()) {
  require('dotenv').config({ path: _path.join(_root, '.env') });
}
```

**关键设计**：
- `dotenv` 不会覆盖已设置的 `process.env` 变量 → Step 1 的 `cwd/.env` 优先
- `EVOLVER_QUIET_PARENT_GIT` 防止在 dotenv 未加载完成时打印误导性 banner
- 修复后 `getRepoRoot()` 能正确读取 `EVOLVER_REPO_ROOT`

### 其他 v1.78.7 变更

| 变更 | 说明 |
|------|------|
| `genes.json` | +201 条新 Gene |
| `capsules.json` | +4 条新 Capsule |
| 所有 `.js` 模块 | 版本号 +1（`.integrity` hash 更新） |

**CE 行动项**：无。`index.js` 是 Node.js 入口，CE 是 Java Spring Boot，无直接借鉴。

---

## 2. v1.78.8 (`2b3c046`) — MemoryGraph Rotation 回归测试

### Issue #519: MemoryGraph JSONL 无限膨胀

`memory_graph.jsonl` 是 append-only 文件，长时间运行后会积累到数 GB。v1.78.8 新增 `test/memoryGraphRotation.test.js`（167 行）对此进行回归测试。

### 测试覆盖

```javascript
describe('memoryGraph rotation (#519)', () => {
  // 环境变量隔离
  EVOLVER_MEMORY_GRAPH_AUTO_ROTATE
  EVOLVER_MEMORY_GRAPH_MAX_SIZE_MB      // 设置为 0.01 (10KB) 进行测试
  EVOLVER_MEMORY_GRAPH_RETENTION_COUNT

  it('rotates when active file exceeds max size')
  it('archives rather than deletes')
  it('respects retention count')
  it('gzips rotated files')
  it('only rotates once per call (idempotent)')
  it('no-op when rotation is disabled')
  it('honors EVOLVER_MEMORY_GRAPH_PATH env var')
})
```

### 关键实现

- `maybeRotateMemoryGraph()`: 检查文件大小，超过阈值则压缩归档
- **gzip 压缩**：旋转后的文件压缩节省空间
- **保留计数**：`EVOLVER_MEMORY_GRAPH_RETENTION_COUNT` 控制保留历史归档数量
- **幂等性**：同一文件只旋转一次，防止重复调用
- **环境隔离**：每个测试用例独立设置 `process.env`，`afterEach` 恢复

### CE 借鉴价值

| 优先级 | 借鉴点 | 说明 |
|--------|--------|------|
| **P1** | Append-only 日志轮转 | CE 的 `observation_events.jsonl` / `audit_log.jsonl` 同样需要轮转机制 |
| **P1** | gzip 压缩归档 | 历史事件压缩存储节省空间 |
| **P2** | 幂等 Rotation | 防止重复轮转 |
| **P2** | 保留计数策略 | 有界存储，防止无限膨胀 |
| **P2** | 环境变量驱动测试 | 每个测试隔离环境变量 |

**CE 落点建议**：
- `audit_log.jsonl` 轮转：当文件超过 10MB 时压缩归档
- `ObservationRepository` 事件日志（如果用 JSONL）：同上
- 参考 `EVOLVER_MEMORY_GRAPH_RETENTION_COUNT = 5` 设置默认保留 5 个归档

---

## 3. v1.78.9 (`5304511`) — AGENT_SESSIONS_DIR 回归测试 #527

### Issue #527: 硬编码会话目录导致 Windows/非标准布局静默失败

**问题**：之前 `evolve.js` 硬编码会话目录为：
```
os.homedir() + '/.openclaw/agents/<AGENT_NAME>/sessions'
```
这导致：
1. 设置了 `process.env.AGENT_SESSIONS_DIR` 的用户配置被忽略
2. Windows 系统和非标准 OpenClaw 布局静默返回 `[NO SESSION LOGS FOUND]`

### 修复方案

`getAgentSessionsDir()` 现在通过 `evolve.js` 内部路径解析模块获取目录，尊重 `AGENT_SESSIONS_DIR` 环境变量覆盖。

### 测试覆盖

```javascript
describe('evolve.js sessions-dir resolution (#527)', () => {
  // 覆盖的环境变量
  AGENT_SESSIONS_DIR
  AGENT_NAME
  EVOLVER_SESSION_SCOPE
  EVOLVER_SESSION_SOURCE
  EVOLVER_REPO_ROOT
  MEMORY_DIR
  EVOLUTION_DIR
  EVOLVER_QUIET_PARENT_GIT
  HUB_OFFLINE
  A2A_NODE_SECRET

  it('resolves AGENT_SESSIONS_DIR from env')
  it('falls back to default when not set')
  it('isolates between test cases')
})
```

### 模块缓存清除策略

```javascript
function purgeModuleCache() {
  // 清除所有包含 /src/ 的缓存条目
  // 确保每个测试用例读取当前的 process.env
  for (const k of Object.keys(require.cache)) {
    if (k.includes(path.sep + 'src' + path.sep)) {
      delete require.cache[k];
    }
  }
}
```

### CE 借鉴价值

| 优先级 | 借鉴点 | 说明 |
|--------|--------|------|
| **P0** | 环境变量优先于硬编码路径 | CE 应避免硬编码路径（如 `~/.claude-mem`），应优先读 env |
| **P1** | 模块缓存清除的测试隔离 | Java 可通过 `@DirtiesContext` 达到类似效果 |
| **P1** | 静默失败 → 明确错误 | `[NO SESSION LOGS FOUND]` 应在配置被忽略时给出警告 |

---

## 4. 三版本总体增量摘要

| 版本 | 核心变更 | 代码行变化 |
|------|----------|-----------|
| v1.78.7 | dotenv 加载顺序修复 + 基因库扩充 | `index.js` +32L / `genes.json` +201 |
| v1.78.8 | MemoryGraph JSONL 轮转回归测试 | `test/` +167L |
| v1.78.9 | AGENT_SESSIONS_DIR 覆盖回归测试 | `test/` +170L |

**净代码增量**：无功能代码变更（均为测试 + 配置数据 + 版本号更新）

**测试基础设施成熟度**：
- v1.78.8 前：几乎无自动化测试
- v1.78.8 后：3 个新测试文件（`memoryGraphRotation.test.js`, `evolveSessionsDir.test.js`, `test/evolveSessionsDir.test.js`）
- 表明 EvoMap 项目进入**测试驱动维护**阶段

---

## 5. BlueCortexCE 行动项

| 优先级 | 行动项 | 关联 |
|--------|--------|------|
| **P1** | 检查 CE 的 `backend/.env` 加载顺序，确保路径配置优先级正确 | 类比 #526 |
| **P1** | 为 `audit_log.jsonl` 实现轮转机制（>10MB → gzip 归档，保留 5 个） | 类比 #519 |
| **P2** | 避免硬编码路径，优先使用 `process.env` 配置 | 类比 #527 |
| **P2** | 为关键配置项添加环境变量覆盖测试 | 测试隔离最佳实践 |

---

**本地工作树仍在 v1.47.0**（`e72778e`），以上版本差异来自 `git fetch origin && git log origin/main` 分析。CE 无需立即跟进。
