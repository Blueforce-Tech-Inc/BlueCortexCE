# `90` executionTrace.js + gitOps.js 深度分析

**模块**: `src/gep/executionTrace.js` (201行) + `src/gep/gitOps.js` (230行)
**定位**: Post-Solidify 管线（doc #68）的两个关键子模块，前者负责结构化结果记录，后者负责 Git 安全与回滚
**版本**: v1.47 / 本地工作树
**最后更新**: 2026-05-05

---

## 1. executionTrace.js：结构化脱敏结果记录

### 1.1 三级脱敏架构（核心设计）

```javascript
const TRACE_LEVELS = { none: 0, minimal: 1, standard: 2 };
// EVOLVER_TRACE_LEVEL=none|minimal|standard (default: minimal)
```

| 级别 | 输出内容 | 典型用途 |
|------|----------|----------|
| `none` | null（完全跳过） | 最高隐私敏感环境 |
| `minimal` | outcome + files_changed_count + lines_added/removed + blast_radius | **默认**，上报 Hub 足够 |
| `standard` | 上述 + file_types + validation_commands + error_signatures + tool_chain + validation_duration_ms + canary_ok | 深度调试/离线分析 |

**设计原则**：脱敏在本地应用，不信任 Hub。文件路径只保留 basename+extension（`src/utils/retry.js` → `retry.js`）；代码内容永不传输，只传统计指标。

### 1.2 Error Signature 提取算法

```javascript
extractErrorSignature(errorText)  // 优先级：
// 1. JS 错误类型: /^((?:[A-Z][a-zA-Z]*)?Error)\b/  → TypeError/ReferenceError
// 2. errno 码: /\b(E[A-Z]{2,})\b/                   → ECONNRESET/ENOENT
// 3. HTTP 状态码: /\b((?:4|5)\d{2})\b/              → HTTP_500
// 4. 首单词 fallback: [A-Z]开头 → UnknownError
```

**示例**：`TypeError: x is not a function` → `TypeError`（而非完整消息）

### 1.3 Blast Radius 三级分类

```javascript
classifyBlastLevel(blast) {
  // low:  ≤3 files,  ≤50 lines
  // medium: ≤10 files, ≤200 lines
  // high:  >10 files 或 >200 lines
}
```

### 1.4 Outcome 感知行数拆分

```javascript
if (outcomeStatus === 'success') {
  // 成功时倾向于增行：60% added / 40% removed
  trace.lines_added = Math.round(total * 0.6);
} else {
  // 失败时趋于平衡：50/50
  trace.lines_added = Math.round(total * 0.5);
}
```

### 1.5 Tool Chain 推理

```javascript
inferToolChain(validationResults, blast)
// 从 validation 命令字符串推断使用的工具集
// npm test/jest/mocha → test_run
// eslint → lint_check
// validate/check → validation_run
// node → node_exec
// blast.files > 0 → file_edit
```

### 1.6 Standard 级别完整 trace 结构

```javascript
{
  gene_id,           // 基因 ID
  mutation_category,  // 突变类别
  signals_matched,    // 前10个信号（裁剪）
  outcome,            // success/fail/unknown
  files_changed_count,
  lines_added,
  lines_removed,
  validation_result,  // pass/fail
  blast_radius,       // low/medium/high/unknown
  // --- standard only ---
  file_types,         // { ".js": 3, ".json": 1 }
  validation_commands,
  error_signatures,   // ["TypeError", "ENOENT"] 最多10条
  tool_chain,         // ["file_edit", "test_run"]
  validation_duration_ms,
  canary_ok,
  created_at,
}
```

### 1.7 与 Solidify 管线的集成

`buildExecutionTrace` 由 `solidify.js` 在循环结束时调用，输入包括：
- `gene` / `mutation`：来源基因与突变类别
- `signals`：本次匹配到的信号（前10条）
- `blast`：变更范围（文件数、行数、文件列表）
- `constraintCheck`：约束违规列表
- `validation`：验证结果
- `canary`：金丝雀健康检查结果
- `outcomeStatus`：最终状态

产物通过 `EvolutionEvent` payload 可选上报 Hub，实现**跨节点学习**——Hub 聚合多个节点的 execution trace，识别普遍性失败模式。

---

## 2. gitOps.js：Git 安全操作与回滚

### 2.1 模块职责

从 `solidify.js` 提取的 Git 操作子模块，所有直接调用 git CLI 或管理回滚的逻辑集中于此。

### 2.2 命令执行封装

```javascript
runCmd(cmd, opts)           // execSync，超时默认120s
tryRunCmd(cmd, opts)        // 返回 { ok, out, err }，不抛异常
```

关键保障：
- `windowsHide: true`：Windows 兼容
- 默认超时 120s（大仓库 diff 可接受）
- `tryRunCmd` 是所有 Git 操作的安全外层

### 2.3 文件变更快照

```javascript
gitListChangedFiles(repoRoot)   // 三路并集：unstaged + staged + untracked
gitListUntrackedFiles(repoRoot) // 纯 untracked 文件
captureDiffSnapshot(repoRoot)   // 合并非 staged diff，8000字符截断
```

### 2.4 关键路径保护（Critical Path Protection）

```javascript
CRITICAL_PROTECTED_PREFIXES = [
  'skills/feishu-evolver-wrapper/',
  'skills/feishu-common/',
  // ... 8个 skill 目录
  'skills/evolver/',
];

CRITICAL_PROTECTED_FILES = [
  'MEMORY.md', 'SOUL.md', 'IDENTITY.md', 'AGENTS.md',
  'USER.md', 'HEARTBEAT.md', 'RECENT_EVENTS.md', 'TOOLS.md',
  'TROUBLESHOOTING.md', 'openclaw.json', '.env', 'package.json',
];
```

`isCriticalProtectedPath()` 对**回滚操作**中的 untracked 文件进行路径白名单检查，确保即使 evolver 进入错误目录也不会删除关键文件。

### 2.5 三种回滚模式

通过 `EVOLVER_ROLLBACK_MODE` 控制（默认 `hard`）：

| 模式 | 行为 | 可恢复性 |
|------|------|----------|
| `none` | 跳过回滚 | — |
| `stash` | `git stash push -m "evolver-rollback-{timestamp}" --include-untracked` | 可通过 `git stash pop` 恢复 |
| `hard` | `git restore --staged . && git restore --worktree . && git reset --hard` | **不可恢复** |

**Stash 模式降级**：若 stash 失败（无变更时），自动降级为 hard reset。

### 2.6 Untracked 文件回滚算法

```javascript
rollbackNewUntrackedFiles({ repoRoot, baselineUntracked })
// 1. 对比 baseline（solidify 开始前的 untracked 集合）
// 2. 找出新增的 untracked 文件
// 3. 安全删除（非 critical path + 是文件 + 路径在 repo 内）
// 4. 收集被清空的目录（从深到浅排序，避免误删父目录）
// 5. 删除空目录
// 返回 { deleted, skipped, removedDirs }
```

**路径穿越防护**：
```javascript
const normAbs = path.resolve(abs);
if (!normAbs.startsWith(normRepo + path.sep) ...) continue;
// 确保 abs 始终在 repoRoot 子树下
```

**目录清理策略**：按路径长度降序排序（先删深目录），避免删了子目录后父目录变空无法删。

### 2.7 回滚在 Solidify 中的调用时机

典型使用模式（对应 doc #68 Post-Solidify 管线）：

```javascript
// solidify.js 伪代码
const baselineUntracked = gitListUntrackedFiles(repoRoot);
// ... 执行基因突变 ...
const success = runValidation();
if (!success) {
  rollbackTracked(repoRoot);              // 恢复 tracked 文件
  rollbackNewUntrackedFiles({             // 删除新生成的文件
    repoRoot, baselineUntracked
  });
}
```

---

## 3. CE 翻译启示

### 3.1 executionTrace → BlueCortexCE

| EvoMap 概念 | CE 对应 | 优先级 |
|-------------|---------|--------|
| 三级脱敏 trace | OutcomeEntity 结构化摘要字段 | **P1** |
| Error signature 提取 | 错误类型标准化（而非完整堆栈） | **P1** |
| Blast radius 分类 | SessionEntity 的变更范围元数据 | P2 |
| Tool chain 推理 | 工具使用频率统计 | P3 |
| Outcome 感知 heuristic | ObservationEntity 分类（success/failure） | P1 |

**核心建议**：CE 应引入类似的结构化 outcome 摘要机制。当前 `ObservationEntity` 包含完整文本，但缺少**脱敏后的统计摘要**字段。建议新增：

```java
// OutcomeSummary embedding-ready 结构
public record OutcomeSummary(
    String outcome,           // success/failure/unknown
    int filesChangedCount,
    int linesAdded,
    int linesRemoved,
    String blastRadius,       // low/medium/high
    List<String> errorSignatures,  // 脱敏后的错误类型
    List<String> toolChain,   // inferred tools
    boolean validationPassed,
    Long validationDurationMs
) {}
```

### 3.2 gitOps → BlueCortexCE

| EvoMap 概念 | CE 对应 | 优先级 |
|-------------|---------|--------|
| Critical path protection | 防止误删 `.env`/`HEARTBEAT.md` 等关键文件 | **P0** |
| Baseline untracked 比较 | 增量文件追踪（区分 old vs new） | P1 |
| 三种回滚模式 | Session 级别的变更回滚能力 | P2 |
| 路径穿越防护 | Path traversal 安全（已有但可加强） | P0 |

**CE 当前状态**：CE 是**读优先**系统（Observation/Summary 提取），不做代码修改，因此 gitOps 的直接适用性较低。但 `rollbackNewUntrackedFiles` 的**基线对比模式**可用于：
- Session 结束时检测临时文件泄漏
- 确保 HEARTBEAT.md 等关键文件不会被意外删除

### 3.3 两者协同：Post-Solidify 完整性闭环

```
solidify.start()
  ├─ baselineUntracked = gitListUntrackedFiles()
  ├─ [执行基因突变]
  ├─ blast = gitListChangedFiles()
  ├─ validation = runValidation()
  └─ if (!success):
       rollbackTracked()          ← gitOps
       rollbackNewUntrackedFiles() ← gitOps
  trace = buildExecutionTrace()   ← executionTrace
  submitHubReview(trace)          ← Hub 上报
```

两者共同保证：即使 evolver 失败，也不会留下脏状态（tracked 文件恢复 + untracked 文件清理），且变更结果以脱敏形式可审计。

---

## 4. 与 Doc #68 的关系

Doc #68（Post-Solidify 管线）概述了两者的存在，此文档提供源码级深度：

| 本文档 | Doc #68 |
|--------|---------|
| `executionTrace.js` 201行全量分析 | 提及 `executionTrace` 作为脱敏轨迹构建器 |
| `gitOps.js` 230行全量分析 | 提及 `gitOps` 三种回滚模式 |
| 三级脱敏架构细节 | 未区分三个级别 |
| Critical path 完整列表 | 未展开保护文件列表 |
| Untracked 回滚算法 | 未展开目录清理策略 |
