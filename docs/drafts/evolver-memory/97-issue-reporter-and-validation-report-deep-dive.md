# `97` `issueReporter.js` + `validationReport.js` 深度分析

**模块**：`src/gep/issueReporter.js` (262行) + `src/gep/validationReport.js` (55行)  
**源码版本**：v1.47.0 (`e72778e`)  
**分析日期**：2026-05-05  
**前置阅读**：[`42`](./42-policycheck-constraint-system-deep-dive.md)（policyCheck 约束系统）/ [`46`](./46-hub-ecosystem-integration-taskreview-issue.md)（Hub Ecosystem / issueReporter 已在 Hub 侧提及）/ [`82`](./82-solidify-prm-process-scoring-and-epigenetic-marks.md)（solidify PRM + epigenetic）

---

## 一、`validationReport.js`（55行）— 标准验证报告类型

### 1.1 设计目标

`ValidationReport` 是一个**标准化、自包含、机器可读**的验证结果格式。Evolver 的 Gene 验证命令（lint/test/build）输出被统一封装为此类型，写入 JSONL 事件日志，供外部 Hub 或 Judge 自动评估。

### 1.2 Schema 核心字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 固定 `"ValidationReport"` |
| `id` | string | `vr_${Date.now()}` 时间戳 ID |
| `gene_id` | string? | 关联的 Gene ID |
| `env_fingerprint` | object | 完整环境指纹（platform/arch/node_version/...） |
| `env_fingerprint_key` | string | 环境指纹的 16 字符摘要键（用于「同类环境」判断） |
| `commands[]` | array | 各验证命令结果 `{command, ok, stdout, stderr}` |
| `overall_ok` | boolean | 全通过 = true |
| `duration_ms` | number? | 验证耗时毫秒 |
| `asset_id` | string | SHA-256 内容寻址（通过 `contentHash.computeAssetId`） |

### 1.3 关键设计点

**stdout/stderr 双字段兼容**：代码同时支持 `r.out` 和 `r.stdout`（`r.err` 和 `r.stderr`），说明验证命令执行层有两套输出字段命名惯例。stdout/stderr 均截断至 4000 字符防日志膨胀。

**环境指纹双重记录**：`env_fingerprint` 完整对象 + `env_fingerprint_key` 短键。前者用于人工核查，后者用于数据库索引和跨节点「同一类环境」JOIN 查询（参见 doc 38）。

**Content-addressable ID**：`asset_id = computeAssetId(report)` 对整个报告内容求 SHA-256，确保相同验证结果产生相同 ID，支持去重和缓存。

**Schema 验证函数**：`isValidValidationReport()` 提供了运行时 Schema 校验（type/id/commands/overall_ok 四项检查）。

### 1.4 CE 借鉴（P0）

CE 的 `ObservationEntity` 可以借鉴 ValidationReport 的**环境指纹双重记录**设计：在 `ObservationEntity` 中同时存储 `envFingerprint` 完整对象和 `envFingerprintKey` 短键，支持跨会话/节点检索「同一环境下的失败观察」。

---

## 二、`issueReporter.js`（262行）— 自动 GitHub Issue 上报

### 2.1 问题背景

Evolver 在经历**持续失败**（而非单次失败）时，自动向 GitHub 提交 Issue，包含脱敏的日志、环境信息和失败信号，供开发者或 AI 调查根本原因。这是**可观测性 + 自动化维护**的结合。

### 2.2 触发条件（AND 逻辑）

```
shouldReport = (
  failure_loop_detected
  OR (recurring_error AND high_failure_ratio)
)
AND consecutive_failure_streak >= minStreak (默认 5)
AND cooldown 未到 (默认 24h)
AND 同一 errorKey 未在 cooldown 内报告过
```

**设计意图**：不能单次失败就开 Issue，必须是**结构性/重复性问题**。且同一 errorKey 在 24h 内只报一次。

### 2.3 错误去重机制（核心）

```javascript
function computeErrorKey(signals) {
  const relevant = signals
    .filter(s =>
      s.startsWith('recurring_errsig:') ||
      s.startsWith('ban_gene:') ||
      s === 'recurring_error' ||
      s === 'failure_loop_detected' ||
      s === 'high_failure_ratio'
    )
    .sort()
    .join('|');
  return SHA-256(relevant || 'unknown').slice(0, 16);
}
```

从信号中提取**相关失败信号**（错误签名、被禁基因、循环检测），排序后 SHA-256 得到 16 字符 errorKey。同类错误产生相同 errorKey，实现精确去重。

### 2.4 Issue 内容构建（`buildIssueBody`）

Issue 包含以下 Markdown 分区：

| 分区 | 内容 | 脱敏 |
|------|------|------|
| Environment | evolver_version / Node.js / platform+arch / container | ❌ 不含敏感信息 |
| Failure Summary | 连续失败次数 / 失败信号列表 | ✅ redactString |
| Error Signature | 错误签名块（规范化后的错误模式） | ✅ redactString |
| Recent Events | 最近失败事件的 Markdown 表格（intent/gene/outcome/reason） | ✅ redactString + 80字符截断 |
| Session Log | 最近 2000 字符的 session 日志 | ✅ redactString |

### 2.5 状态持久化（`issue_reporter_state.json`）

```json
{
  "lastReportedAt": "2026-05-04T12:00:00.000Z",
  "recentIssueKeys": ["a3f5c8d2e1b0f...", "..."],
  "lastIssueUrl": "https://github.com/autogame-17/...",
  "lastIssueNumber": 42
}
```

- `lastReportedAt`：全局冷却时钟，防止短时间内多个不同 errorKey 触发重复 Issue
- `recentIssueKeys`：最多 20 个 errorKey，与 cooldown 共同构成**双保险去重**
- `lastIssueUrl/Number`：便于事后查询已上报的 Issue

### 2.6 GitHub API 调用

```javascript
POST https://api.github.com/repos/{repo}/issues
Headers: Authorization: Bearer {token}
         X-GitHub-Api-Version: 2022-11-28
Body: { title, body }
Timeout: 15000ms
```

- 支持 `GITHUB_TOKEN` / `GH_TOKEN` / `GITHUB_PAT` 三种 token 环境变量
- 15s 超时保护，防止 GitHub API 阻塞主循环
- 失败时**静默吞掉**（非 fatal），不阻止主循环继续

### 2.7 完整调用链

```
maybeReportIssue({ signals, envFingerprint, recentEvents, sessionLog })
  → shouldReport()        ← 冷却 + 频率检查
  → getGithubToken()      ← 多 env var fallback
  → buildIssueBody()      ← 脱敏 + 格式化
  → createGithubIssue()   ← API 调用
  → writeState()          ← 持久化 cooldown
```

### 2.8 CE 借鉴路径

| 优先级 | 借鉴点 | CE 落地方式 |
|--------|--------|------------|
| **P1** | 错误去重 + 冷却机制 | 在 `ObservationService` 或 `AlertService` 中实现 `errorKey`（SHA-256 of normalized error sig + signal key） + 24h cooldown，防止同一错误重复告警 |
| **P1** | 多分区 Issue/Markdown 报告体 | `ObservationEntity` 或独立 `AlertReport` 实体，包含环境指纹 + 错误签名 + 事件时间线 + 日志片段 |
| **P1** | 状态文件双保险去重 | `alert_state.json` 持久化 `lastAlertedAt` + `recentAlertKeys[]`（上限 20） |
| **P2** | 脱敏 + redactString | 复用 `sanitize.js` 的 `redactString` 或实现 CE 自己的脱敏管线 |
| **P2** | GitHub Issue vs 飞书消息 | CE 将 GitHub 替换为飞书机器人消息（WebHook），保持同样的触发/冷却/去重逻辑 |
| **P3** | 自动化维护闭环 | Issue 创建后，AI 自动分析并提交修复 PR（对应 Evolver 的 `selfPR.js`） |

---

## 三、`solidify.js` 补遗：新发现

> ⚠️ 以下是 `solidify.js`（1344行）中**之前文档未覆盖**的新发现。Doc 34 和 Doc 82 已覆盖大部分流程，此处补充增量。

### 3.1 FailedCapsule Diff 保留（在 Rollback 前）

```javascript
// Capture failed mutation as a FailedCapsule before rollback destroys the diff.
if (!dryRun && !success) {
  const diffSnapshot = captureDiffSnapshot(repoRoot);
  if (diffSnapshot) {
    const failedCapsule = {
      type: 'Capsule',
      id: 'failed_' + buildCapsuleId(ts),
      diff_snapshot: diffSnapshot,   // ← Rollback 前捕获
      failure_reason: failureReason,
      constraint_violations: constraintCheck.violations,
      env_fingerprint: envFp,
      ...
    };
    appendFailedCapsule(failedCapsule);
  }
}
```

**设计意图**：在 `rollbackTracked()` 执行**之前**捕获 diff snapshot，确保失败变更仍可用于事后分析（anti-pattern 学习）。Rollback 恢复了文件，但 diff 被永久保留在 FailedCapsule 中。

### 3.2 三路 Hub Task 自动完成

`solidify()` 根据任务来源类型自动选择完成路径：

**路径 A — Deferred Atomic Claim+Complete**（Worker Pool 延迟认领）:
```javascript
if (workerPending && !workerAssignmentId) {
  const { claimAndCompleteWorkerTask } = require('./taskReceiver');
  // 原子操作：认领 + 完成合并为一个 API 调用
  result = claimAndCompleteWorkerTask(taskId, resultAssetId);
}
```

**路径 B — Legacy Assignment Complete**（已认领的任务）:
```javascript
if (workerAssignmentId) {
  const { completeWorkerTask } = require('./taskReceiver');
  result = completeWorkerTask(workerAssignmentId, resultAssetId);
}
```

**路径 C — Bounty Task Complete**（悬赏任务）:
```javascript
// 通过 /a2a/task/complete 端点
const { completeTask } = require('./taskReceiver');
result = completeTask(taskId, resultAssetId);
```

### 3.3 Anti-pattern 自动发布（Opt-in）

```javascript
const publishAntiPatterns =
  String(process.env.EVOLVER_PUBLISH_ANTI_PATTERNS || '').toLowerCase() === 'true';
```

- 仅当 `EVOLVER_PUBLISH_ANTI_PATTERNS=true` 时启用（opt-in，非默认）
- 仅发布**高信息量**失败：constraint violations 或 canary failure
- 发布为 `anti_pattern: true` 的 Gene + Capsule bundle
- **普通验证失败**不触发（信息量低）

### 3.4 Pre-publish Leak Check

```javascript
const leakResult = fullLeakCheck(contentToScan);
if (leakResult.found) {
  if (leakCheckMode === 'strict') {
    // 阻止发布
    publishResult = { blocked: true, reason: 'leak_detected' };
  } else {
    // 仅警告，sanitizePayload 会自动脱敏
    console.warn('[LeakCheck] WARNING: sensitive data detected');
  }
}
```

`strict` 模式阻止发布；`warn` 模式允许脱敏后发布。CE 借鉴此双模式，在向外部系统发送数据前做 leak scan。

### 3.5 Gene Library Versioning

```javascript
function computeGeneLibraryVersion() {
  const genesPath = path.join(getGepAssetsDir(), 'genes.json');
  const raw = fs.readFileSync(genesPath, 'utf8');
  const hash = SHA-256(raw).slice(0, 16);
  return 'glib_' + hash;
}
```

每次 solidification 对 `genes.json` 求 SHA-256，版本标记到 Event/Capsule 中。用于追踪「哪些 Gene 库版本产生的哪些结果」，支持跨时间点回溯。

---

## 四、CE 行动项总结

| 优先级 | 行动项 | 关联模块 |
|--------|--------|---------|
| **P0** | 结构化 ValidationReport 类型：CE 应在 `ObservationEntity` 中增加 `validationReport` JSONB 字段，记录验证命令结果序列 | `validationReport.js` |
| **P1** | 错误告警去重 + 冷却机制：在 `AlertService` 实现 errorKey（SHA-256 of error sig + signal）+ 24h cooldown + `alert_state.json` 双保险 | `issueReporter.js` |
| **P1** | 失败 Diff 保留：CE 的 `ObservationEntity` 在 `rollbackOnFailure` 时应保留变更前后 diff（或至少 error sig），而非完全丢弃 | `solidify.js` §3.1 |
| **P2** | 飞书告警体格式化：参考 `buildIssueBody` 的多分区 Markdown 格式，输出飞书富文本消息（环境 / 错误签名 / 最近事件 / 日志片段） | `issueReporter.js` |
| **P2** | 环境指纹双重记录：在 `ObservationEntity` 同时存储 `envFingerprint` 完整对象 + `envFingerprintKey` 16字符键 | `validationReport.js` §1.3 |
| **P3** | Anti-pattern 发布机制：CE 的 Structured Extraction 失败案例（Schema 解析失败 / 提取质量问题）可发布到内部知识库作为 anti-pattern | `solidify.js` §3.3 |

---

## 五、源码位置速查

| 文件 | 行数 | 入口函数 |
|------|------|---------|
| `src/gep/validationReport.js` | 55 | `buildValidationReport()` |
| `src/gep/issueReporter.js` | 262 | `maybeReportIssue()` |
| `src/gep/solidify.js`（相关段落） | 1344 | `solidify()` |

---

_Last updated: 2026-05-05 (本篇为 doc 97)_
