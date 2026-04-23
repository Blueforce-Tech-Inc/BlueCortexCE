# 42. policyCheck.js 约束系统深度分析

**角色**：`policyCheck.js`（23KB，501行）是 Evolver Solidify 管线的安全保障核心——负责约束检查、blast radius 计算、验证命令白名单、伦理模式检测、破坏性变更检测。本文档深入分析 `classifyFailureMode` 之外的关键机制，提炼 BlueCortexCE 可落地的安全工程设计。

**源码锚点**：`src/gep/policyCheck.js`（501行）。

---

## 1. 文件定位与职责概览

```
policyCheck.js 导出（15个函数 + 4个常量）
│
├── 约束策略读取
│   └── readOpenclawConstraintPolicy()      — 从 openclaw.json 读取 countedFilePolicy
│
├── 路径匹配（核心算法）
│   ├── isConstraintCountedPath()           — 判断文件是否计入 blast radius
│   ├── matchAnyPrefix()                    — 路径前缀匹配
│   ├── matchAnyExact()                     — 精确路径匹配
│   └── matchAnyRegex()                     — 正则匹配（含 ReDoS 防护）
│
├── Blast Radius 计算
│   ├── parseNumstatRows()                  — 解析 git diff --numstat
│   ├── computeBlastRadius()                — 计算变更的文件数/行数
│   ├── classifyBlastSeverity()             — 分类 severity 等级
│   ├── analyzeBlastRadiusBreakdown()       — 按目录分解 top-N
│   └── compareBlastEstimate()              — 预估 vs 实际对比
│
├── 约束检查（Solidify 调用）
│   ├── checkConstraints()                 — 主检查入口（gene.constraints）
│   ├── isForbiddenPath()                   — 路径黑名单
│   └── detectDestructiveChanges()         — 关键文件删除/清空检测
│
├── 验证安全
│   ├── isValidationCommandAllowed()        — 命令白名单（安全检查）
│   ├── runValidationsOnce()                — 单次验证执行
│   └── runValidations()                    — 重试逻辑包装
│
├── Canary
│   └── runCanaryCheck()                    — 执行 canary.js 健康检查
│
└── 故障分析与信号构建
    ├── buildFailureReason()               — 聚合失败原因字符串
    ├── buildSoftFailureLearningSignals()   — 失败 → learning signals
    └── classifyFailureMode()               — 五级分类（hard/soft × reasonClass）
```

---

## 2. 约束策略：从 openclaw.json 读取

### 2.1 默认策略

```javascript
// readOpenclawConstraintPolicy() 默认值
const defaults = {
  excludePrefixes: ['logs/', 'memory/', 'assets/gep/', 'out/', 'temp/', 'node_modules/'],
  excludeExact: ['event.json', 'temp_gep_output.json', 'temp_evolution_output.json', 'evolution_error.log'],
  excludeRegex: ['capsule', 'events?\\.jsonl$'],
  includePrefixes: ['src/', 'scripts/', 'config/'],
  includeExact: ['index.js', 'package.json'],
  includeExtensions: ['.js', '.cjs', '.mjs', '.ts', '.tsx', '.json', '.yaml', '.yml', '.toml', '.ini', '.sh'],
};
```

### 2.2 配置路径

```javascript
// 从 workspace 上一级目录读取 openclaw.json
const root = path.resolve(getWorkspaceRoot(), '..');
const cfgPath = path.join(root, 'openclaw.json');
// evolver.constraints.countedFilePolicy.* 覆盖默认值
```

### 2.3 BlueCortexCE 借鉴

**场景**：BlueCortexCE 的"观察过滤"和"检索范围"可能也需要类似的路径/扩展名策略：
- 哪些 observation_types 应该计入趋势分析
- 哪些文件路径的变更应该触发额外的上下文注入
- 可以通过配置文件声明，而硬编码

---

## 3. 路径匹配算法：`isConstraintCountedPath`

### 3.1 决策树

```
isConstraintCountedPath(relPath, policy)
│
├── 1. exact exclude 命中？ → false
│
├── 2. prefix exclude 命中？ → false
│   （'logs/' 匹配 'logs/file.txt' 和 'logs/subdir/file.txt'）
│
├── 3. regex exclude 命中？ → false
│   （MAX_REGEX_PATTERN_LEN 防护 ReDoS）
│
├── 4. exact include 命中？ → true
│   （'package.json' 即使在 excludePrefix 目录下也计入）
│
├── 5. prefix include 命中？ → true
│   （'src/' 优先于 'node_modules/'）
│
├── 6. extension include 命中？ → true
│   （'src/a.txt' 计入，但 'node_modules/a.js' 不计入，因为 prefix exclude 优先）
│
└── 7. 其他 → false
```

### 3.2 关键设计决策

**优先级**：exclude 规则优先于 include 规则。这防止了"node_modules/.js"被意外计入。

**路径归一化**：
```javascript
function normalizeRelPath(p) {
  return p.replace(/\\/g, '/').replace(/^\.\/+/, '').replace(/\/+$/, '');
}
```
Windows (`\`) 和 Unix (`/`) 统一处理。

**ReDoS 防护**：
```javascript
const MAX_REGEX_PATTERN_LEN = 200;
if (s.length > MAX_REGEX_PATTERN_LEN) continue;
```
如果正则超长，跳过而非拒绝。

**BlueCortexCE 借鉴**：路径匹配逻辑可以迁移为"观察类型过滤"或"会话范围过滤"的决策引擎。

---

## 4. Blast Radius 计算：`computeBlastRadius`

### 4.1 输入

```javascript
computeBlastRadius({ repoRoot, baselineUntracked })
```

- `repoRoot`：Git 仓库根目录
- `baselineUntracked`：基准时刻的 untracked 文件列表（用于排除"本来就有"的文件）

### 4.2 计算过程

```javascript
// 1. 获取所有变更文件（含 untracked）
changedFiles = gitListChangedFiles(repoRoot)
  .map(normalizeRelPath)
  .filter(f => !baselineUntracked.has(f));

// 2. 分类：计入 vs 不计入
countedFiles = changedFiles.filter(f => isConstraintCountedPath(f, policy));
ignoredFiles  = changedFiles.filter(f => !isConstraintCountedPath(f, policy));

// 3. 解析 numstat（additions + deletions）
unstagedRows = parseNumstatRows(git diff --numstat)
stagedRows   = parseNumstatRows(git diff --cached --numstat)
stagedUnstagedChurn = Σ(row.added + row.deleted)  // 仅 counted 文件

// 4. 解析 untracked 文件行数
untrackedLines = Σ(countFileLines(f))  // 仅 counted untracked 文件

// 5. 总 churn
churn = stagedUnstagedChurn + untrackedLines
```

### 4.3 输出

```javascript
{
  files: 5,                          // 计入 blast radius 的文件数
  lines: 142,                        // 总变更行数（adds+dels）
  changed_files: ['src/a.js', ...],   // 计入的文件列表
  ignored_files: ['node_modules/...'], // 不计入的文件列表
  all_changed_files: ['src/a.js', 'node_modules/...'], // 所有变更文件
}
```

### 4.4 关键洞察

**Untracked 文件处理**：不仅计算已追踪文件的 diff，还统计 untracked 新建文件的行数。这防止了"大量新建文件"绕过 blast radius 检测。

**Baseline 对比**：传入 `baselineUntracked` 可以排除"本来就有"的文件，只计算本次运行时新增的 untracked 文件。

**BlueCortexCE 借鉴**：
- 如果 BlueCortexCE 实现"会话影响力评估"，可以用类似方法：
  - 新增 observation count vs 历史平均值
  - 变更的 observation types 列表
  - 新增 session 数量

---

## 5. Severity 分类：`classifyBlastSeverity`

### 5.1 常量

```javascript
const BLAST_RADIUS_HARD_CAP_FILES = 60;    // 系统硬上限（环境变量可覆盖）
const BLAST_RADIUS_HARD_CAP_LINES = 20000;  // 系统硬上限
const BLAST_WARN_RATIO = 0.8;              // 警告阈值
const BLAST_CRITICAL_RATIO = 2.0;          // 严重超标倍数
```

### 5.2 分类树

```
classifyBlastSeverity({ blast, maxFiles })
│
├── blast.files > 60 OR blast.lines > 20000
│   └── severity: 'hard_cap_breach'
│       → 不可重试，立即终止
│
├── !Number.isFinite(maxFiles)
│   └── severity: 'within_limit'（无约束）
│
├── blast.files > maxFiles * 2.0
│   └── severity: 'critical_overrun'
│       → 很可能发生了批量/意外操作
│       → 触发目录分解报告
│
├── blast.files > maxFiles
│   └── severity: 'exceeded'
│
├── blast.files > maxFiles * 0.8
│   └── severity: 'approaching_limit'
│
└── blast.files <= maxFiles * 0.8
    └── severity: 'within_limit'
```

### 5.3 基因约束 `maxFiles`

```javascript
// checkConstraints() 读取 gene.constraints.max_files
const maxFiles = gene.constraints.max_files > 0
  ? gene.constraints.max_files
  : DEFAULT_MAX_FILES;  // 20
```

不同类型的基因（repair/optimize/innovate）可以有不同的 `max_files` 约束。

### 5.4 目录分解：`analyzeBlastRadiusBreakdown`

```javascript
analyzeBlastRadiusBreakdown(changedFiles, topN = 5)
// 输出: [{ dir: 'src/utils', files: 3 }, { dir: 'src/api', files: 2 }, ...]
```

帮助判断"变更集中在哪个目录"，用于 `critical_overrun` 时的诊断输出。

### 5.5 预估对比：`compareBlastEstimate`

```javascript
compareBlastEstimate(estimate, actual)
// 若 actual.files / estimate.files > 3 或 < 0.1
// → drifted: true → 警告：Agent 预估不准确
```

防止 Agent 低估或高估操作规模。

---

## 6. 约束检查主入口：`checkConstraints`

### 6.1 检查项目

```javascript
checkConstraints({ gene, blast, blastRadiusEstimate, repoRoot })
```

| 检查项 | 失败条件 | severity |
|--------|---------|---------|
| **HARD CAP** | files > 60 OR lines > 20000 | hard_cap_breach → 终止 |
| **CRITICAL OVERRUN** | files > maxFiles * 2.0 | critical_overrun → 终止 |
| **MAX FILES EXCEEDED** | files > maxFiles | exceeded → 终止 |
| **APPROACHING LIMIT** | files > maxFiles * 0.8 | approaching_limit → 警告 |
| **预估漂移** | actual/estimate > 3x 或 < 0.1x | drifted → 警告 |
| **FORBIDDEN PATH** | 触碰黑名单路径 | 终止 |
| **CRITICAL PATH** | 触碰关键文件（非 allowSelfModify） | 终止 |
| **不完整 SKILL** | skills/X/ 少于 2 个文件 | 警告 |
| **伦理违规** | 策略文本匹配伦理模式 | 终止 |

### 6.2 关键代码片段

```javascript
// SELF_MODIFY 例外：允许 evolver/ 技能在 repair category 下自我修改
const allowSelfModify = process.env.EVOLVE_ALLOW_SELF_MODIFY === 'true';
if (isCriticalProtectedPath(f)) {
  if (allowSelfModify && norm.startsWith('skills/evolver/') && gene.category === 'repair') {
    warnings.push('self_modify_evolver_repair: ' + norm);
  } else {
    violations.push('critical_path_modified: ' + norm);
  }
}
```

### 6.3 伦理模式检测

```javascript
const ethicsBlockPatterns = [
  { re: /(?:bypass|disable|circumvent|remove)\s+(?:safety|guardrail|security|ethic|constraint|protection)/i,
    rule: 'safety', msg: 'ethics: strategy attempts to bypass safety mechanisms' },
  { re: /(?:keylogger|screen\s*capture|webcam\s*hijack|mic(?:rophone)?\s*record)/i,
    rule: 'human_welfare', msg: 'ethics: covert monitoring tool in strategy' },
  { re: /(?:social\s+engineering|phishing)\s+(?:attack|template|script)/i,
    rule: 'human_welfare', msg: 'ethics: social engineering content in strategy' },
  { re: /(?:exploit|hack)\s+(?:user|human|people|victim)/i,
    rule: 'human_welfare', msg: 'ethics: human exploitation in strategy' },
  { re: /(?:hide|conceal|obfuscat)\w*\s+(?:action|behavior|intent|log)/i,
    rule: 'transparency', msg: 'ethics: strategy conceals actions from audit trail' },
];
```

检查基因的 `strategy` + `description` + `summary` 字段。

### 6.4 BlueCortexCE 借鉴

**观察安全**：BlueCortexCE 的 observation 写入路径可以做类似检查：
- 观察内容是否包含敏感数据模式（API key、密码等）→ 警告或过滤
- 观察频率是否异常（单会话 > X 次）→ 警告
- 特定类型的观察是否来自可信的 hook

---

## 7. 验证命令白名单：`isValidationCommandAllowed`

### 7.1 设计目标

Solidify 的 `gene.validation` 命令列表在执行前必须通过安全检查，防止通过验证脚本执行任意危险操作。

### 7.2 允许规则

```javascript
const VALIDATION_ALLOWED_PREFIXES = ['node ', 'npm ', 'npx '];

function isValidationCommandAllowed(cmd) {
  // 1. 必须以允许前缀开头
  if (!VALIDATION_ALLOWED_PREFIXES.some(p => c.startsWith(p))) return false;

  // 2. 禁止命令替换（反注入）
  if (/`|\$\(/.test(c)) return false;

  // 3. 禁止 shell 操作符
  const stripped = c.replace(/"[^"]*"/g, '').replace(/'[^']*'/g, '');
  if (/[;&|><]/.test(stripped)) return false;

  // 4. 禁止 node -e/--eval（可以执行任意代码）
  if (/^node\s+(-e|--eval|--print|-p)\b/.test(c)) return false;

  return true;
}
```

### 7.3 关键约束

- **只允许** `node` / `npm` / `npx` 开头的命令
- 引号内内容不参与 shell 操作符检查（允许路径含空格）
- `node -e "..."` 被明确禁止（可以执行任意代码）

### 7.4 BlueCortexCE 借鉴

如果 BlueCortexCE 实现"用户自定义提取模板"或"用户定义的 hook 脚本"：
- 模板中的变量展开需要防注入检查
- Hook 脚本路径需要白名单验证
- 避免 `eval()` 或动态 `exec()` 调用不受信任的字符串

---

## 8. 破坏性变更检测：`detectDestructiveChanges`

### 8.1 检测类型

| 违规类型 | 检测条件 |
|---------|---------|
| `CRITICAL_FILE_DELETED` | 关键文件在 baseline 中存在，但当前不存在 |
| `CRITICAL_FILE_EMPTIED` | 关键文件大小为 0（被清空） |

### 8.2 关键文件定义

```javascript
// gitOps.js: isCriticalProtectedPath()
// 关键系统文件：MEMORY.md, SOUL.md, IDENTITY.md, USER.md,
// openclaw.json, .env, package.json, skills/ 目录等
```

### 8.3 BlueCortexCE 借鉴

BlueCortexCE 如果实现"数据库迁移"或"schema 变更"功能：
- 类似的关键表/字段删除检测
- Session 表被清空时的警告
- observation_types 被删除时的级联影响评估

---

## 9. 验证执行与重试：`runValidations`

### 9.1 重试策略

```javascript
function runValidations(gene, opts = {}) {
  const maxRetries = MAX_VALIDATIONS_RETRIES;  // 从 config.js 读取
  let attempt = 0;
  let result;
  while (attempt <= maxRetries) {
    result = runValidationsOnce(gene, opts);
    if (result.ok) return result;  // 通过 → 返回
    if (blocked) break;             // 被安全拦截 → 不重试
    attempt++;
    sleepSync(SOLIDIFY_RETRY_INTERVAL_MS);  // 等待后重试
  }
  return result;
}
```

### 9.2 安全拦截不重试

如果验证命令被 `isValidationCommandAllowed` 拦截（BLOCKED），立即终止，不重试。

### 9.3 BlueCortexCE 借鉴

如果 BlueCortexCE 实现"观察提取重试"：
- 速率限制错误（429）→ 等待后重试
- 认证错误（401/403）→ 不重试，立即终止
- 超时 → 可配置重试次数

---

## 10. 导出函数速查

| 函数 | 职责 | BlueCortexCE 借鉴 |
|------|------|------------------|
| `readOpenclawConstraintPolicy` | 从 openclaw.json 读取策略 | 配置驱动的观察过滤 |
| `isConstraintCountedPath` | 路径匹配决策树 | 观察类型过滤引擎 |
| `parseNumstatRows` | 解析 git numstat | diff 分析基础 |
| `computeBlastRadius` | 计算变更规模 | 会话影响力评估 |
| `classifyBlastSeverity` | 5 级 severity | 观察严重性分类 |
| `analyzeBlastRadiusBreakdown` | 目录级 top-N | 热点 observation 类型 |
| `compareBlastEstimate` | 预估准确性 | 检索质量自评 |
| `checkConstraints` | 主检查入口 | 观察写入前安全检查 |
| `isForbiddenPath` | 路径黑名单 | API 路径过滤 |
| `detectDestructiveChanges` | 关键文件保护 | schema 变更保护 |
| `isValidationCommandAllowed` | 命令白名单 | Hook 脚本安全 |
| `runValidations` | 重试包装 | API 重试策略 |
| `runCanaryCheck` | 健康检查 | BlueCortexCE 健康检查 |
| `buildFailureReason` | 聚合失败原因 | 错误归因分析 |
| `buildSoftFailureLearningSignals` | 失败→信号 | 观察失败→标签 |
| `classifyFailureMode` | 5 级故障分类 | 错误分类处理 |

---

## 11. 关键设计原则总结

1. **配置优先于硬编码**：策略来自 `openclaw.json`，而不是代码中硬编码
2. **拒绝优先于警告**：安全相关判断优先返回 false/违规
3. **分层防御**：约束检查 → 验证命令 → Canary → 故障分类，层层过滤
4. **可调试输出**：违规时输出目录分解、变更文件列表，方便定位问题
5. **环境变量覆盖**：关键常量（CAP、timeout）可通过环境变量调整
6. **无外部依赖**：纯 Node.js fs/Path，无第三方库依赖
