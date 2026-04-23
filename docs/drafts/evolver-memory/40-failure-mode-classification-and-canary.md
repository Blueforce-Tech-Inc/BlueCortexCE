# 40. Failure Mode Classification + Canary Safety Net（policyCheck.js 深度补充）

**角色**：深度分析 `policyCheck.js` 中 `classifyFailureMode`、`runCanaryCheck`、`buildSoftFailureLearningSignals` 的完整故障分类与自愈机制，提炼 BlueCortexCE 可落地的错误处理和健康检查设计。

**源码锚点**：`src/gep/policyCheck.js`（§420–§550），`src/canary.js`。

---

## 1. Failure Mode Classification：`classifyFailureMode`

### 1.1 设计目标

Evolver 的每次 Solidify 验证失败后，不是简单标记"失败"，而是**细粒度分类** failure mode，决定是否值得重试。这是"自我评估"的核心体现。

### 1.2 完整分类树

```
classifyFailureMode(opts)
├── mode: 'hard'          — 不可重试，立即终止
│   ├── reasonClass: 'constraint_destructive'
│   │   └── 触发条件：constraintViolations 包含
│   │       • HARD CAP BREACH（文件数 > 60 或行数 > 20000）
│   │       • CRITICAL_FILE_DELETED / CRITICAL_FILE_EMPTIED
│   │       • critical_path_modified（非 repair 且修改了关键文件）
│   │       • forbidden_path touched
│   │       • ethics:（策略尝试绕过安全机制）
│   │   └── 含义：破坏性变更，不可重试
│   ├── reasonClass: 'protocol'
│   │   └── 触发条件：protocolViolations 非空（A2A 协议违规）
│   ├── reasonClass: 'canary'
│   │   └── 触发条件：canary 检查失败（index.js 无法加载）
│   └── reasonClass: 'constraint'
│       └── 触发条件：constraintViolations 非空但非破坏性
│
└── mode: 'soft'           — 可重试，给基因一次修正机会
    ├── reasonClass: 'validation'
    │   └── 触发条件：validation 命令执行失败（非安全拦截）
    └── reasonClass: 'unknown'
        └── 兜底：无法归类时也允许重试
```

### 1.3 源码实现

**文件**：`policyCheck.js:475–500`（`classifyFailureMode`）

```javascript
function classifyFailureMode(opts) {
  const constraintViolations = opts && Array.isArray(opts.constraintViolations) ? opts.constraintViolations : [];
  const protocolViolations = opts && Array.isArray(opts.protocolViolations) ? opts.protocolViolations : [];
  const validation = opts && opts.validation ? opts.validation : null;
  const canary = opts && opts.canary ? opts.canary : null;

  // Hard #1: 破坏性约束违规
  if (constraintViolations.some(function (v) {
    const s = String(v || '');
    return /HARD CAP BREACH|CRITICAL_FILE_|critical_path_modified|forbidden_path touched|ethics:/i.test(s);
  })) {
    return { mode: 'hard', reasonClass: 'constraint_destructive', retryable: false };
  }
  // Hard #2: 协议违规
  if (protocolViolations.length > 0) {
    return { mode: 'hard', reasonClass: 'protocol', retryable: false };
  }
  // Hard #3: Canary 失败
  if (canary && !canary.ok && !canary.skipped) {
    return { mode: 'hard', reasonClass: 'canary', retryable: false };
  }
  // Hard #4: 一般约束违规
  if (constraintViolations.length > 0) {
    return { mode: 'hard', reasonClass: 'constraint', retryable: false };
  }
  // Soft #1: 验证命令失败（可重试）
  if (validation && validation.ok === false) {
    return { mode: 'soft', reasonClass: 'validation', retryable: true };
  }
  // Soft #2: 兜底
  return { mode: 'soft', reasonClass: 'unknown', retryable: true };
}
```

### 1.4 决策逻辑图

```
Solidify 验证失败
        │
        ▼
constraintViolations 包含破坏性关键词？
        │
   YES ─┤─→ hard / constraint_destructive / retryable=false
        │
       NO
        │
        ▼
protocolViolations 非空？
        │
   YES ─┤─→ hard / protocol / retryable=false
        │
       NO
        │
        ▼
canary.ok == false？
        │
   YES ─┤─→ hard / canary / retryable=false
        │
       NO
        │
        ▼
constraintViolations 非空？
        │
   YES ─┤─→ hard / constraint / retryable=false
        │
       NO
        │
        ▼
validation.ok == false？
        │
   YES ─┤─→ soft / validation / retryable=true
        │
       NO
        │
        ▼
   soft / unknown / retryable=true
```

### 1.5 破坏性关键词正则详解

```javascript
/HARD CAP BREACH|CRITICAL_FILE_|critical_path_modified|forbidden_path touched|ethics:/i
```

| 模式 | 含义 | 严重性 |
|------|------|--------|
| `HARD CAP BREACH` | 变更规模超系统上限（60 文件或 20000 行） | 系统级 |
| `CRITICAL_FILE_DELETED` | 关键文件被删除（SOUL.md、IDENTITY.md 等） | 致命 |
| `CRITICAL_FILE_EMPTIED` | 关键文件被清空 | 致命 |
| `critical_path_modified` | 关键路径被修改（skills/evolver/ 除外） | 严重 |
| `forbidden_path touched` | 触碰了 foridden_paths 中的路径 | 严重 |
| `ethics:` | 策略包含伦理违规内容（绕过安全机制等） | 最高 |

---

## 2. Canary Check：`runCanaryCheck`

### 2.1 设计目标

**在真正提交变更之前**，运行一个极简的健康检查脚本，验证 `index.js` 能否正常加载。如果 canary 失败，说明变更可能破坏了入口文件，此时**绝不提交**，直接标记为 hard failure。

### 2.2 canary.js 源码

**文件**：`src/canary.js`（18 行）

```javascript
// Canary script: run in a forked child process to verify index.js loads
// without crashing. Exit 0 = safe, non-zero = broken.
//
// This is the last safety net before solidify commits an evolution.
// If a patch broke index.js (syntax error, missing require, etc.),
// the canary catches it BEFORE the daemon restarts with broken code.
try {
  require('../index.js');
  process.exit(0);
} catch (e) {
  process.stderr.write(String(e.message || e).slice(0, 500));
  process.exit(1);
}
```

### 2.3 runCanaryCheck 实现

**文件**：`policyCheck.js:442–452`（`runCanaryCheck`）

```javascript
function runCanaryCheck(opts) {
  const repoRoot = (opts && opts.repoRoot) ? opts.repoRoot : getRepoRoot();
  const timeoutMs = (opts && Number.isFinite(Number(opts.timeoutMs))) ? Number(opts.timeoutMs) : _CFG_CANARY_TIMEOUT;
  const canaryScript = path.join(repoRoot, 'src', 'canary.js');
  if (!fs.existsSync(canaryScript)) {
    return { ok: true, skipped: true, reason: 'canary.js not found' };
  }
  const r = tryRunCmd(`node "${canaryScript}"`, { cwd: repoRoot, timeoutMs });
  return { ok: r.ok, skipped: false, out: String(r.out || ''), err: String(r.err || '') };
}
```

### 2.4 关键设计特点

1. **零依赖**：canary.js 本身不依赖任何 evolver 内部模块，只 require 顶层 `index.js`
2. **进程隔离**：通过 `tryRunCmd` 在子进程中运行，崩溃不影响主进程
3. **超时保护**：`_CFG_CANARY_TIMEOUT` 防止 index.js 的顶层同步代码死循环
4. **优雅降级**：canary.js 不存在时跳过（`skipped: true`），不阻塞流程
5. **最后关卡**：在 constraint check、validation、protocol check 之后，是提交前的最后一道门

### 2.5 Canary 在 Solidify 管线中的位置

```
Solidify Pipeline（简化版）
  1. restoreState()        ← 恢复变更前状态
  2. applyGene()          ← 应用基因变更
  3. checkConstraints()   ← Blast Radius / 关键文件检查
  4. runValidations()     ← 执行验证命令（npm test 等）
  5. runCanaryCheck()     ← ✅ 加载 index.js 做健康检查
  6. classifyFailureMode()← 根据上面结果分类 failure mode
  7. if hard → rollback   ← 硬失败 → 回滚
  8. if soft → retry      ← 软失败 → 重试基因
  9. if ok → commit/hub    ← 全部通过 → 提交或发布 Hub
```

---

## 3. Soft Failure → Learning Signals：`buildSoftFailureLearningSignals`

### 3.1 设计目标

对于 `mode: 'soft'` 的失败（validation 命令执行失败），不直接丢弃失败信息，而是从中提取 **learning signals**（可操作的反馈标签），供下一轮基因选择参考。

### 3.2 源码实现

**文件**：`policyCheck.js:454–478`（`buildSoftFailureLearningSignals`）

```javascript
function buildSoftFailureLearningSignals(opts) {
  const { expandSignals } = require('./learningSignals');
  const signals = opts && Array.isArray(opts.signals) ? opts.signals : [];
  const failureReason = opts && opts.failureReason ? String(opts.failureReason) : '';
  const violations = opts && Array.isArray(opts.violations) ? opts.violations : [];
  const validationResults = opts && Array.isArray(opts.validationResults) ? opts.validationResults : [];
  
  // 从验证失败结果中提取文本
  const validationText = validationResults
    .filter(function (r) { return r && r.ok === false; })
    .map(function (r) { return [r.cmd, r.stderr, r.stdout].filter(Boolean).join(' '); })
    .join(' ');
  
  // 调用 learningSignals.expandSignals 生成标签
  return expandSignals(signals.concat(violations), failureReason + ' ' + validationText)
    .filter(function (tag) {
      return tag.indexOf('problem:') === 0 ||
             tag.indexOf('risk:') === 0 ||
             tag.indexOf('area:') === 0 ||
             tag.indexOf('action:') === 0;
    });
}
```

### 3.3 四类输出标签

| 前缀 | 含义 | 示例 |
|------|------|------|
| `problem:` | 失败中识别出的具体问题 | `problem: undefined variable in src/handler.js` |
| `risk:` | 潜在风险 | `risk: same error in related module` |
| `area:` | 问题所属的代码区域 | `area: validation script timeout` |
| `action:` | 建议的修复动作 | `action: increase timeout or fix race condition` |

这些标签会注入到基因选择的 signal 输入中，形成 **失败驱动的自适应学习闭环**。

---

## 4. Validation 命令安全：`isValidationCommandAllowed`

### 4.1 设计目标

Solidify 在执行验证命令前，对命令进行**白名单级安全检查**，防止基因注入恶意验证脚本。

### 4.2 源码实现

**文件**：`policyCheck.js:408–419`（`isValidationCommandAllowed`）

```javascript
const VALIDATION_ALLOWED_PREFIXES = ['node ', 'npm ', 'npx '];

function isValidationCommandAllowed(cmd) {
  const c = String(cmd || '').trim();
  if (!c) return false;
  // 1. 必须以 node/npm/npx 开头
  if (!VALIDATION_ALLOWED_PREFIXES.some(p => c.startsWith(p))) return false;
  // 2. 禁止命令替换（反注入）
  if (/`|\$\(/.test(c)) return false;
  // 3. 去除引号后检查 shell 操作符
  const stripped = c.replace(/"[^"]*"/g, '').replace(/'[^']*'/g, '');
  if (/[;&|><]/.test(stripped)) return false;
  // 4. 禁止 node -e/--eval/--print/-p（防止直接代码执行）
  if (/^node\s+(-e|--eval|--print|-p)\b/.test(c)) return false;
  return true;
}
```

### 4.3 安全检查层级

| 检查层 | 机制 | 防御对象 |
|--------|------|----------|
| 前缀白名单 | `node`/`npm`/`npx` | 禁止任意二进制执行 |
| 反命令替换 | 禁止 `` ` `` 和 `$(...)` | 防止注入 |
| 反 shell 操作符 | 去除引号内容后检查 `;&\|><` | 防止管道/后台执行 |
| 禁止 eval | 禁止 `node -e` 等 | 防止直接代码执行 |

---

## 5. 构建失败原因：`buildFailureReason`

### 5.1 设计目标

将四层检查结果（constraint、validation、protocol、canary）聚合为一个**人类可读的错误摘要**，用于日志记录、issue body、和 signal 生成。

### 5.2 源码实现

**文件**：`policyCheck.js:454–466`（`buildFailureReason`）

```javascript
function buildFailureReason(constraintCheck, validation, protocolViolations, canary) {
  const reasons = [];
  if (constraintCheck && Array.isArray(constraintCheck.violations)) {
    for (let i = 0; i < constraintCheck.violations.length; i++) {
      reasons.push('constraint: ' + constraintCheck.violations[i]);
    }
  }
  if (Array.isArray(protocolViolations)) {
    for (let j = 0; j < protocolViolations.length; j++) {
      reasons.push('protocol: ' + protocolViolations[j]);
    }
  }
  if (validation && Array.isArray(validation.results)) {
    for (let k = 0; k < validation.results.length; k++) {
      const r = validation.results[k];
      if (r && !r.ok) {
        reasons.push('validation_failed: ' + String(r.cmd || '').slice(0, 120)
          + ' => ' + String(r.err || '').slice(0, 200));
      }
    }
  }
  if (canary && !canary.ok && !canary.skipped) {
    reasons.push('canary_failed: ' + String(canary.err || '').slice(0, 200));
  }
  return reasons.join('; ').slice(0, 2000) || 'unknown';
}
```

### 5.3 输出格式

```
constraint: max_files exceeded: 25 > 20; validation_failed: npm test => ERR! code ELIFECYCLE
```

---

## 6. Blast Radius 反馈：`compareBlastEstimate`

### 6.1 设计目标

Evolver 在基因选择时会**预估** blast radius（变更规模），solidify 后**实测** blast radius。对比两者，如果实际变更远超预估，说明 agent 规划能力不足，下次基因选择时应降低该基因的评分。

### 6.2 源码实现

**文件**：`policyCheck.js:220–238`（`compareBlastEstimate`）

```javascript
function compareBlastEstimate(estimate, actual) {
  if (!estimate || typeof estimate !== 'object') return null;
  const estFiles = Number(estimate.files);
  const actFiles = Number(actual.files);
  if (!Number.isFinite(estFiles) || estFiles <= 0) return null;
  const ratio = actFiles / estFiles;
  return {
    estimateFiles: estFiles,
    actualFiles: actFiles,
    ratio: Math.round(ratio * 100) / 100,
    drifted: ratio > 3 || ratio < 0.1,    // 实际是预估的 3 倍以上，或 1/10 以下
    message: ratio > 3
      ? `Estimate drift: actual ${actFiles} files is ${ratio.toFixed(1)}x the estimated ${estFiles}. Agent did not plan accurately.`
      : null,
  };
}
```

### 6.3 漂移判断阈值

| ratio | 判定 | 含义 |
|-------|------|------|
| `> 3.0` | `drifted: true` | 实际变更远超预估，agent 规划失败 |
| `< 0.1` | `drifted: true` | 实际变更远低于预估，空转 |
| `0.1–3.0` | `drifted: false` | 预估与实际基本吻合 |

---

## 7. BlueCortexCE 借鉴要点

### 7.1 Failure Mode Classification → CE 健康检查

CE 目前有 `/api/health` 端点，但仅返回 `{"status":"ok"}`。可以引入**多级健康状态**：

```java
// 建议：MultiLevelHealthResponse
public record HealthResponse(
    String status,           // "ok" | "degraded" | "critical"
    String reasonClass,      // "constraint_violation" | "validation_failed" | "unknown"
    boolean retryable,
    List<String> violations,
    long responseTimeMs
) {}
```

| Evolver reasonClass | CE 对应场景 | retryable |
|---------------------|-------------|-----------|
| `constraint_destructive` | 违规观察注入（XSS/注入攻击） | false |
| `protocol` | MCP 协议违规 / 参数校验失败 | false |
| `canary` | 关键端点超时 / 循环依赖检测失败 | false |
| `validation` | LLM API 调用失败 / embedding 生成失败 | true |
| `unknown` | 无法归类的错误 | true |

### 7.2 Canary Pattern → CE Startup Check

Evolver 的 canary 在**提交前**检查 `index.js` 能否加载。CE 可以引入类似机制：

```java
// 建议：ApplicationRunner 实现
@Component
public class CanaryCheckRunner implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        // 1. 验证数据库连接可用
        // 2. 验证 pgvector 扩展已加载
        // 3. 验证 API 端点可访问（/api/health）
        // 4. 验证 embedding service 可用
        if (anyCheck.fails()) {
            throw new IllegalStateException("Canary check failed: " + failures);
        }
    }
}
```

### 7.3 Soft Failure Learning Signals → CE 观察增强

Evolver 将 validation 失败信息注入 `expandSignals` 生成可操作标签。CE 可以在 `ObservationEntity.extractedData` 中增加结构化失败字段：

```json
{
  "failure_signature": "npm_test_timeout",
  "problem": "validation command exceeded timeout",
  "area": "regression_test_suite",
  "action": "increase timeout or split test suite"
}
```

这些标签可用于：
- **检索增强**：按 `area` 标签聚类相似失败
- **趋势分析**：同类 `problem` 出现频率
- **根因推断**：相同 `action` 的成功率对比

### 7.4 Blast Radius Feedback → CE 变更影响评估

CE 可以对观察进行**变更影响分级**：

```java
// 建议：ObservationEntity 新增字段
private String blastRadiusLevel;  // "low" | "medium" | "high" | "critical"

// blastRadiusLevel 计算逻辑：
// low:  单次错误，单文件影响
// medium: 重复错误，2-5 文件影响
// high: 大规模模式错误，>5 文件
// critical: 安全相关或核心模块
```

### 7.5 Validation Command Allowlist → CE 工具白名单

Evolver 对 validation 命令做白名单过滤。CE 的 MCP 工具也可以引入类似机制：

```java
// 建议：AllowedToolValidator
public class AllowedToolValidator {
    private static final Set<String> ALLOWED_TOOLS = Set.of(
        "search", "timeline", "observations", 
        "summaries", "sessions", "modes"
    );
    
    public boolean isAllowed(String toolName) {
        return ALLOWED_TOOLS.contains(toolName);
    }
}
```

### 7.6 架构对比总结

| 维度 | Evolver | BlueCortexCE |
|------|---------|-------------|
| 失败分类 | 5 级细粒度（hard/soft × reasonClass） | 目前只有 ok/fail |
| Canary 检查 | index.js 加载验证（提交前最后关卡） | 无对应机制 |
| 失败→信号 | soft failure 注入 expandSignals 生成标签 | 无对应机制 |
| 预估反馈 | blast radius 预估 vs 实测漂移检测 | 无对应机制 |
| 验证白名单 | node/npm/npx 前缀 + shell 操作符过滤 | MCP 工具有简单校验 |
| 伦理检测 | 正则检测绕过安全机制策略 | 无对应机制 |

---

## 附录：完整导出表（policyCheck.js）

| 函数 | 职责 |
|------|------|
| `readOpenclawConstraintPolicy` | 从 `openclaw.json` 读取 countedFilePolicy |
| `isConstraintCountedPath` | 判断文件是否计入变更规模 |
| `parseNumstatRows` | 解析 `git diff --numstat` 输出 |
| `computeBlastRadius` | 计算变更的文件数/行数 |
| `classifyBlastSeverity` | 分类 blast radius 严重性（warn/exceed/critical/hard_cap） |
| `analyzeBlastRadiusBreakdown` | 按目录拆分 top-N 贡献者 |
| `compareBlastEstimate` | 预估 vs 实测漂移检测 |
| `checkConstraints` | 综合约束检查（文件数/行数/关键文件/伦理） |
| `detectDestructiveChanges` | 检测关键文件删除/清空 |
| `isValidationCommandAllowed` | validation 命令白名单安全检查 |
| `runValidations` | 带重试的验证命令执行 |
| `runCanaryCheck` | Canary 健康检查 |
| `buildFailureReason` | 聚合多源失败原因为单一字符串 |
| `buildSoftFailureLearningSignals` | 将失败转化为 learning signals 标签 |
| `classifyFailureMode` | 失败模式分类（hard/soft × reasonClass） |
