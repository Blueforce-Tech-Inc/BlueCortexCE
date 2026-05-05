# `103` v1.78.9 Delta + `defaultHandler.js` + Token Budget 对比分析

**分析目标**：v1.78.7→v1.78.9 版本增量分析 + 新增 `atp/defaultHandler.js` 模块 + CE vs Evolver Token Budget 架构对比。  
**数据来源**：`git diff 643630f 5304511`（v1.78.5→v1.78.9）、`src/atp/defaultHandler.js`（69行）、`src/gep/prompt.js`、`ContextService.java`。  
**最后更新**：2026-05-05

---

## 1. v1.78.9 版本增量（v1.78.5 → v1.78.9）

### 1.1 核心变更：`index.js` dotenv 加载顺序修复（#526）

**Root Cause**：`.env` 加载发生在 `getRepoRoot()` 首次调用**之后**，`EVOLVER_REPO_ROOT` 被缓存的 `.git` 查找结果覆盖，导致用户配置静默失效。

**Fix**（+32 行 `index.js`）：

```javascript
// Step 1: load .env from process.cwd() BEFORE any internal require
require('dotenv').config({ path: _path.join(process.cwd(), '.env') });

// Step 2: suppress "Using host git repository" banner during bootstrap
// because the initial banner would show wrong path while debugging #526
const _prevQuiet = process.env.EVOLVER_QUIET_PARENT_GIT;
process.env.EVOLVER_QUIET_PARENT_GIT = '1';

// Step 3: only now call getRepoRoot() which will honor EVOLVER_REPO_ROOT
const { getRepoRoot: _getRepoRoot } = require('./src/gep/paths');
const _root = _getRepoRoot();
if (_root && _root !== process.cwd()) {
  require('dotenv').config({ path: _path.join(_root, '.env') });
}

// Step 4: restore quiet flag
if (_prevQuiet === undefined) delete process.env.EVOLVER_QUIET_PARENT_GIT;
else process.env.EVOLVER_QUIET_PARENT_GIT = _prevQuiet;
```

**Load Order**（修复后）：
1. `.env` from `process.cwd()`（用户项目根目录）
2. `EVOLVER_REPO_ROOT` env var 检查（如有，优先）
3. `getRepoRoot()` 调用（不再缓存预-dotenv 结果）
4. `.env` from repo root（如与 cwd 不同）

**对应 `paths.js` Fix**：确保 `EVOLVER_REPO_ROOT` 在任意时刻都优先于缓存值：

```javascript
function getRepoRoot() {
  // Always check EVOLVER_REPO_ROOT first, even when a cached value exists.
  // .env is loaded during index.js bootstrap AFTER this function has
  // already been called at least once (for locating the .env file itself).
  if (process.env.EVOLVER_REPO_ROOT) {
    _cachedRepoRoot = process.env.EVOLVER_REPO_ROOT;
    return _cachedRepoRoot;
  }
  if (_cachedRepoRoot) return _cachedRepoRoot;
  // ... .git walk ...
}
```

### 1.2 数据更新：`genes.json` +201 / `capsules.json` +4

```
assets/gep/genes.json    | +201 行（新增基因）
assets/gep/capsules.json | +4 行（新增胶囊）
```

**新增基因类型**：

| Category | Gene ID | Signals Match | 用途 |
|----------|---------|---------------|------|
| `repair` | `gene_gep_repair_from_errors` | error/exception/failed/unstable | 从错误日志提取信号→选择基因→最小可逆补丁 |
| `optimize` | `gene_gep_optimize_prompt_and_assets` | protocol/gep/prompt/audit/reusable | GEP Prompt 优化与资产生命周期 |
| `innovate` | `gene_gep_innovate_from_opportunity` | (待确认) | 机会驱动创新 |

每条基因包含完整结构：`type` / `id` / `category` / `signals_match[]` / `preconditions[]` / `strategy[]` / `constraints` / `validation[]`。

### 1.3 其余变更

- `src/gep/paths.js`：新增 `getEvolutionPrinciplesPath()` 和 `getReflectionLogPath()` 两个路径访问函数。
- 所有 `.js` 模块：`integrityCheck.js` 版本标签更新（`±2` 行变更，无功能变化）。
- `src/gep/hubVerify.js`、`src/gep/shield.js`、`src/gep/explore.js`：版本标签更新，功能无变化。

**结论**：v1.78.9 是**纯数据 + dotenv 修复**版本，无架构变更。CE 无需跟进（dotenv 加载在 Java 侧不存在）。

---

## 2. 新增模块：`atp/defaultHandler.js`（69行）

**引入版本**：v1.78.9 新增（不在 v1.78.5）。

### 2.1 模块职责

ATP（Agent Transaction Protocol）订单的默认处理器。Evolver 在 loop 模式下接收 ATP 订单时的通用响应逻辑。

### 2.2 三大导出

#### `defaultOrderHandler(order)` — 通用订单处理器

```javascript
function defaultOrderHandler(order) {
  const title = (order.title || '').toLowerCase();
  const signals = (order.signals || '').toLowerCase();
  let result;
  if (title.includes('review') || signals.includes('code_review') || signals.includes('bug')) {
    result = 'Code review processed by evolver. Analysis complete.';
  } else if (title.includes('translat') || signals.includes('translation') || signals.includes('localization')) {
    result = 'Translation processed by evolver. Output ready.';
  } else if (title.includes('summar') || signals.includes('summarization') || signals.includes('digest')) {
    result = 'Summarization processed by evolver. Digest generated.';
  } else {
    result = 'Task processed by evolver agent.';
  }
  return { result, output: result, pass_rate: 1.0, processed_at: ..., processor: 'evolver-default' };
}
```

**设计观察**：
- 基于 `title` 和 `signals` 关键词匹配路由，无 LLM 调用
- 返回固定的 `pass_rate: 1.0`（乐观假设）
- 属于 "dumb fallback" 处理器，实际业务逻辑由自定义 `onOrder` callback 接管

#### `resolveAtpServices()` — 服务注册

```javascript
function resolveAtpServices() {
  // 优先使用环境变量 EVOLVER_ATP_SERVICES（JSON 数组）
  // 回退到默认单个 evolver 服务描述
  return [{
    title: agentName + ' - Code Evolution',
    description: 'Automated code evolution, bug fixes, and code review powered by GEP.',
    capabilities: ['code_evolution', 'bug_fix', 'code_review', 'refactoring'],
    useCases: ['Automated repair', 'Code quality', 'Evolution cycle'],
    pricePerTask: 5,
    maxConcurrent: 3,
  }];
}
```

**与 `hubClient.js` 的关系**：`hubClient` 是 ATP Hub 通信客户端（发送订单/接收任务）；`defaultHandler` 是 ATP 订单的消费处理器。前者对接 Hub 市场，后者处理订单执行。

#### `getAtpMode()` — 三态开关

```javascript
function getAtpMode() {
  // off: 完全关闭 ATP
  // on: 强制开启
  // auto: 默认行为（根据环境自动判断）
}
```

**CE 借鉴**：BlueCortexCE 目前没有 ATP-like 机制。`getAtpMode` 的三态（`off/on/auto`）模式值得在 `SearchService` 或 `EmbeddingService` 等模块中参考，作为功能开关的标准范式。

---

## 3. Token Budget 对比：CE vs Evolver

### 3.1 BlueCortexCE：Count-Based Limit

```java
// ContextService.java
private static final int TOTAL_OBSERVATION_COUNT = 50;
```

**特点**：
- 固定observation数量上限（50条）
- 不考虑单条observation的长度差异
- `findByTypeAndConcepts(projectPath, types, concepts, conceptsEmpty, 50)` 直接传数量
- 无 token 估算（`TokenService` 仅用于**统计报告**，不参与上下文注入决策）
- 无动态预算分配（timeline / semantic / summary 各部分无独立预算）

**优点**：实现简单，无估算误差
**缺点**：短observation 50条可能很少，长observation 50条可能超 token limit

### 3.2 Evolver：Character-Based Budget

```javascript
// prompt.js
const EXEC_CONTEXT_CAP = 20000; // chars ≈ 5k tokens

// 分层保护
// 1. Header + Footer 优先保留
// 2. Strategy Block 次优先
// 3. Execution Context 动态计算剩余空间
// 4. 按优先级截断：Anti-Pattern Zone / Lessons / Observations / Narrative
```

**特点**：
- 基于字符数硬上限（可配置 `GEP_PROMPT_MAX_CHARS`）
- 分层截断策略（优先级保护）
- `TokenService.calculateObservationTokens()` 用于统计报告，但截断基于**字符数**
- `EXEC_CONTEXT_CAP = 20000` 约等于 5k tokens（4 chars/token）

### 3.3 关键差距

| 维度 | BlueCortexCE | Evolver |
|------|-------------|---------|
| **预算单位** | 固定 observation 数量（50） | 字符数硬上限（20000 chars） |
| **动态适应** | ❌ 无（等长假设） | ✅ 按实际内容长度分配 |
| **分层保护** | ❌ 无 | ✅ Header/Footer/Strategy 优先保留 |
| **语义注入预算** | ❌ 无独立预算 | ✅ `GEP_PROMPT_MAX_CHARS` 控制总长 |
| **Token 估算** | ✅ 仅用于统计报告 | ✅ 用于截断决策 |
| **配置方式** | 硬编码常量 | 环境变量 `GEP_PROMPT_MAX_CHARS` |

### 3.4 Research Backlog 状态更新

> **语义注入与时间线并存的 token 预算**：`additionalContext` 与主上下文拼接策略、关闭开关与延迟预算。

**现状**：
- CE `generateContext` 使用**时间线注入**（50条 observation 数量限制），**不**调用 `SearchService`
- CE `generateContextSemantic` 使用**语义检索**（pgvector 向量相似度），但**无**独立 token budget
- Evolver 的 `EXEC_CONTEXT_CAP` 为**两层注入**（timeline narrative + semantic search results）提供了统一的字符预算框架

**CE P2 行动项**（来自 Research Backlog）：

| 优先级 | 行动项 | 说明 |
|--------|--------|------|
| **P1** | 引入字符/Token 预算上限 | 替代固定数量限制，按实际 observation 长度动态计算 |
| **P2** | 分层注入保护 | Timeline observations 优先，semantic results 次优先 |
| **P2** | 语义注入独立预算 | `generateContextSemantic` 应有独立的 token 上限（建议 2000-3000 chars） |
| **P3** | `TokenService` 整合到截断决策 | 目前仅用于统计，应参与实际上下文生成 |

---

## 4. 小结

1. **v1.78.9**：dotenv 加载顺序修复（#526）+ 201 条新基因 + 4 个新胶囊；CE 无需跟进
2. **`defaultHandler.js`**：ATP 订单的 dumb fallback 处理器；`getAtpMode()` 三态开关模式值得 CE 借鉴
3. **Token Budget**：CE 使用固定数量（50），Evolver 使用字符上限（20k chars）+ 分层保护；差距明显，Research Backlog 项「语义注入 + timeline token 预算」仍是 open 状态
