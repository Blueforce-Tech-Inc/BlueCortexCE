# `68` Post-Solidify 完整管线：ExecutionTrace → GitOps → SkillPublisher → QuestionGenerator → A2A

**目标**：分析 Evolver solidify 之后的"下游处理"管线——将进化产物（Gene/Capsule）转化为可观测记录、安全回滚、Hub 资产发布与外部求援的全链路。

**数据来源**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/` 本地源码。

**最后更新**：2026-05-02（**`68` 新增**：`executionTrace.js` 三级脱敏执行轨迹构建 / `gitOps.js` Git 安全回滚与保护区 / `skillPublisher.js` Gene→SKILL.md 市场级输出 + Hub 发布/更新冲突处理 / `questionGenerator.js` 六策略主动求援问题生成 / `a2a.js` A2A 资产广播资格判断与置信降低 / CE P0/P1/P2 借鉴路径）

---

## 1. ExecutionTrace：脱敏执行轨迹构建

### 1.1 三级 Trace Level 控制

`executionTrace.js` 通过环境变量 `EVOLVER_TRACE_LEVEL` 控制脱敏粒度：

| Level | 值 | 输出内容 |
|-------|-----|---------|
| `none` | 0 | 全部跳过，返回 `null` |
| `minimal` | 1 | 仅核心指标（文件数、代码行数、验证结果、blast 级别） |
| `standard` | 2 | 上述 + 文件类型分布、验证命令列表、错误签名、工具链、canary 结果 |

```javascript
const TRACE_LEVELS = { none: 0, minimal: 1, standard: 2 };
function getTraceLevel() {
  const raw = String(process.env.EVOLVER_TRACE_LEVEL || 'minimal').toLowerCase().trim();
  return TRACE_LEVELS[raw] != null ? raw : 'minimal';
}
```

### 1.2 脱敏规则（核心设计原则）

**文件路径**：`src/utils/retry.js` → `retry.js`（仅保留 basename + extension）

```javascript
function desensitizeFilePath(filePath) {
  const ext = path.extname(filePath);
  const base = path.basename(filePath);
  return base || ext || 'unknown';
}
```

**错误信息**：仅保留错误类型签名，剥离具体值

```javascript
function extractErrorSignature(errorText) {
  // JS error: TypeError, ReferenceError → "TypeError"
  // errno: ECONNRESET, ENOENT → "ECONNRESET"
  // HTTP: 404, 500 → "HTTP_404"
  // Fallback: 首字母大写的单词
}
```

**代码内容**：从不发送，仅统计指标（行数、文件数）

**环境变量/密钥/用户数据**：完全剥离

### 1.3 `buildExecutionTrace` 完整输出结构

```javascript
buildExecutionTrace({
  gene, mutation, signals, blast, constraintCheck,
  validation, canary, outcomeStatus, startedAt
})
```

**所有级别共有字段**：

```json
{
  "gene_id": "gene_<name>",
  "mutation_category": "repair|optimize|innovate",
  "signals_matched": ["<signal>", ...],     // 最多 10 个
  "outcome": "success|failed|unknown",
  "files_changed_count": 3,
  "lines_added": 30,
  "lines_removed": 5,
  "validation_result": "pass|fail",
  "blast_radius": "low|medium|high",
  "created_at": "2026-05-02T..."
}
```

**`standard` 级别额外字段**：

```json
{
  "file_types": { ".js": 3, ".json": 1 },      // 文件扩展名统计
  "validation_commands": ["npm test", "npm run lint"],
  "error_signatures": ["TypeError", "ENOENT"],
  "tool_chain": ["file_edit", "test_run"],
  "validation_duration_ms": 3420,
  "canary_ok": true
}
```

### 1.4 Blast Radius 分类

```javascript
function classifyBlastLevel(blast) {
  const files = Number(blast.files) || 0;
  const lines = Number(blast.lines) || 0;
  if (files <= 3 && lines <= 50) return 'low';
  if (files <= 10 && lines <= 200) return 'medium';
  return 'high';
}
```

### 1.5 工具链推断

从 validation 命令列表反推使用的工具：

```javascript
function inferToolChain(validationResults, blast) {
  const tools = new Set();
  if (blast?.files > 0) tools.add('file_edit');
  for (const r of validationResults) {
    if (/jest|mocha/.test(r.cmd)) tools.add('test_run');
    if (/eslint|lint/.test(r.cmd)) tools.add('lint_check');
    if (/validate|check/.test(r.cmd)) tools.add('validation_run');
    if (r.cmd.startsWith('node ')) tools.add('node_exec');
  }
  return Array.from(tools);
}
```

### 1.6 CE 借鉴路径

| 优先级 | 借鉴点 | CE 落点 |
|--------|--------|---------|
| **P0** | Trace level 环境变量控制 | `ObservationEntity` 已有 `metadata` JSONB，可扩展 trace_level 字段 |
| **P0** | 错误签名规范化（`extractErrorSignature`） | `AgentService.saveObservation` 对 `type=error` 写入规范化签名（已有 [`22`](./22-error-sig-norm-implementation-proposal.md) 提案） |
| **P1** | 文件路径脱敏（basename only） | 写入 observation 前对文件路径脱敏 |
| **P1** | Blast radius 分类 | `ObservationEntity.metadata.blast_radius` 字段 |
| **P2** | 工具链推断 | 用于分析用户工作流模式，扩展 `ObservationType` 标签 |

---

## 2. GitOps：安全回滚与保护区

### 2.1 模块定位

`gitOps.js` 从 `solidify.js` 中抽取，专门负责所有 Git 操作。核心职责：

1. **diff 快照捕获**（写入 EvolutionEvent）
2. **保护区判定**（防止破坏性修改关键文件）
3. **回滚执行**（tracked 文件 + untracked 文件）

### 2.2 保护区（Critical Protected）

**目录级**：

```javascript
const CRITICAL_PROTECTED_PREFIXES = [
  'skills/feishu-evolver-wrapper/',
  'skills/feishu-common/',
  'skills/feishu-post/',
  'skills/feishu-card/',
  'skills/feishu-doc/',
  'skills/skill-tools/',
  'skills/clawhub/',
  'skills/git-sync/',
  'skills/evolver/',
];
```

**文件级**：

```javascript
const CRITICAL_PROTECTED_FILES = [
  'MEMORY.md', 'SOUL.md', 'IDENTITY.md', 'AGENTS.md',
  'USER.md', 'HEARTBEAT.md', 'RECENT_EVENTS.md',
  'TOOLS.md', 'TROUBLESHOOTING.md',
  'openclaw.json', '.env', 'package.json',
];
```

判定逻辑：

```javascript
function isCriticalProtectedPath(relPath) {
  const rel = normalizeRelPath(relPath);
  for (const prefix of CRITICAL_PROTECTED_PREFIXES) {
    const p = prefix.replace(/\/+$/, '');
    if (rel === p || rel.startsWith(p + '/')) return true;
  }
  for (const f of CRITICAL_PROTECTED_FILES) {
    if (rel === f) return true;
  }
  return false;
}
```

### 2.3 三种回滚模式（`EVOLVER_ROLLBACK_MODE`）

| Mode | 行为 |
|------|------|
| `none` | 完全跳过回滚 |
| `stash` | `git stash push -m "evolver-rollback-<timestamp>" --include-untracked`，失败则 fallback 到 hard |
| `hard`（默认） | `git restore --staged . && git restore --worktree . && git reset --hard` |

### 2.4 Untracked 文件回滚（`rollbackNewUntrackedFiles`）

**核心流程**：

1. **计算基准**：记录回滚开始前的 untracked 文件集合（`baselineUntracked`）
2. **差量检测**：对比当前 untracked 与基准，识别新增文件
3. **安全删除**：逐文件检查是否为保护路径，若是则跳过；否则删除
4. **空目录清理**：从最深路径向上，删除变空的目录

**安全护栏**：

```javascript
// 路径穿越防护：确保 abs 路径在 repoRoot 之下
const normRepo = path.resolve(repoRoot);
const normAbs = path.resolve(abs);
if (!normAbs.startsWith(normRepo + path.sep) && normAbs !== normRepo) continue;

// 关键保护区跳过
if (isCriticalProtectedPath(safeRel)) { skipped.push(safeRel); continue; }
```

### 2.5 Diff 快照捕获

```javascript
const DIFF_SNAPSHOT_MAX_CHARS = 8000;

function captureDiffSnapshot(repoRoot) {
  const parts = [];
  const unstaged = tryRunCmd('git diff', { cwd: repoRoot, timeoutMs: 30000 });
  if (unstaged.ok && unstaged.out) parts.push(unstaged.out);
  const staged = tryRunCmd('git diff --cached', { cwd: repoRoot, timeoutMs: 30000 });
  if (staged.ok && staged.out) parts.push(staged.out);
  let combined = parts.join('\n');
  if (combined.length > DIFF_SNAPSHOT_MAX_CHARS)
    combined = combined.slice(0, DIFF_SNAPSHOT_MAX_CHARS) + '\n... [TRUNCATED]';
  return combined || '';
}
```

### 2.6 CE 借鉴路径

| 优先级 | 借鉴点 | CE 落点 |
|--------|--------|---------|
| **P0** | 保护区判定逻辑 | `AgentService` / `ObservationService` 写入前检查关键文件路径（如 `SOUL.md`、`HEARTBEAT.md`）是否被修改 |
| **P0** | 路径穿越防护 | 所有文件路径操作使用 `path.resolve` + `startsWith` 检查 |
| **P1** | 三种回滚模式 | Java 实现中提供事务回滚（`RollbackMode.NONE` / `HARD` / `STASH` 对应 DB 回滚策略） |
| **P2** | Diff 快照捕获 | `ObservationEntity.metadata.diff_snapshot` 字段（用于失败分析） |

---

## 3. SkillPublisher：Gene → SKILL.md 市场级发布

### 3.1 Skill 名称规范化（`sanitizeSkillName`）

将 gene id 转换为合规的 kebab-case skill 名称：

```javascript
function sanitizeSkillName(rawName) {
  var name = rawName
    .replace(/^gene_distilled_/, '')
    .replace(/^gene_/, '')
    .replace(/_/g, '-')
    .replace(/-?\d{10,}-?/g, '-');   // 剥离所有 10+ 位数字（时间戳）

  // 禁止纯工具名
  if (/^(cursor|vscode|vim|emacs|windsurf|copilot|cline|codex)[-]?\d*$/i.test(name))
    return null;

  // 太短（去掉分隔符后 < 6 字符）也不行
  if (name.replace(/[-]/g, '').length < 6) return null;

  return name;
}
```

**示例**：
- `gene_retry_with_backoff_1746352000` → `retry-with-backoff`
- `gene_cursor-fix-1773331925711` → `null`（工具名 + 时间戳）
- `gene_test` → `null`（太短）

### 3.2 Gene → SKILL.md 转换（`geneToSkillMd`）

输出格式：

```markdown
---
name: Retry With Backoff
description: Automatically retry failed HTTP requests with exponential backoff.
---

# Retry With Backoff

Automatically retry failed HTTP requests with exponential backoff.

## When to Use
- When your project encounters: `http_timeout`, `connection_reset`

## Trigger Signals
- `http_timeout`
- `connection_reset`

## Preconditions
- Node.js environment
- HTTP client library installed

## Strategy
1. **Import** -- Import the retry library
2. **Configure** -- Set max retries and backoff base
3. **Wrap** -- Wrap HTTP calls with retry logic
4. **Validate** -- Run integration tests

## Validation
```bash
npm test
```

## Metadata
- Category: `repair`
- Schema version: `1.6.0`
- Distilled from: 3 successful capsules

---
*This Skill was generated by [Evolver](https://github.com/autogame-17/evolver) and is distributed under the [EvoMap Skill License (ESL-1.0)](https://evomap.ai/terms).*
```

**核心原则**：
- YAML frontmatter 是触发机制（name + description）
- 描述必须 ≥ 10 字符的完整句子
- SKILL.md body 控制在 500 行以内
- 不创建不必要的文件（README.md、CHANGELOG.md 等）

### 3.3 Hub 发布管线（`publishSkillToHub`）

**完整流程**：

```
Gene → sanitize signals_match → geneToSkillMd()
  → POST /a2a/skill/store/publish
    → 201/200: 成功
    → 409: updateSkillOnHub (PUT /a2a/skill/store/update)
    → 其他错误: { ok: false, error }
```

```javascript
async function publishSkillToHub(gene, opts) {
  const body = {
    sender_id: nodeId,
    skill_id: 'skill_' + derivedName,   // 时间戳已剥离
    content: geneToSkillMd(gene),       // SKILL.md 内容
    category: opts.category || gene.category || null,
    tags: sanitizeSignalsMatch(gene.signals_match),  // 信号作为 tag
  };

  const res = await fetch(hubUrl + '/a2a/skill/store/publish', {
    method: 'POST',
    headers: buildHubHeaders(),
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  });

  if (res.status === 409)
    return updateSkillOnHub(nodeId, skillId, content, opts, gene);

  return { ok: res.ok, ... };
}
```

### 3.4 CE 借鉴路径

| 优先级 | 借鉴点 | CE 落点 |
|--------|--------|---------|
| **P0** | Skill 名称规范化（时间戳剥离、工具名禁止） | `SkillService` 创建 skill 时对名称做规范化检查 |
| **P1** | YAML frontmatter 触发机制 | `SKILL.md` 的 `name` / `description` 字段作为 skill 匹配触发条件 |
| **P1** | Hub 冲突处理（409 → update） | 幂等发布：先查已存在，再决定 POST 还是 PUT |
| **P2** | Skill 发布时信号作 tag | 发布到 Hub 时 `tags` 字段传入 `observationTypes` |

---

## 4. QuestionGenerator：六策略主动求援

### 4.1 定位

`solidify` 后，若遇到本地无法解决的问题，通过 **Hub bounty 系统**向生态求援。`questionGenerator.js` 负责从当前上下文中生成高质量问题。

### 4.2 六种策略

| # | 策略名 | 触发信号 | 问题类型 |
|---|--------|----------|---------|
| 1 | 递归错误 | `recurring_error`, `high_failure_ratio` | "自动修复无法解决的重复错误" |
| 2 | 能力缺口 | `capability_gap`, `unsupported_input_type` | "不支持的输入类型/操作" |
| 3 | 进化饱和 | `evolution_saturation`, `force_steady_state` | "现有基因耗尽，寻求新方向" |
| 4 | 连续失败 | `consecutive_failure_streak_≥4` | "连续失败已无解，寻求替代策略" |
| 5 | 用户功能需求 | `user_feature_request` | "用户功能请求，可能有社区方案" |
| 6 | 性能瓶颈 | `perf_bottleneck` | "性能问题，寻求优化模式" |

### 4.3 模糊去重（`isDuplicate`）

```javascript
function isDuplicate(question, recentQuestions) {
  // 精确匹配
  if (prev === qLower) return true;

  // 模糊匹配：词汇集合重叠度 > 70%
  const qWords = new Set(qLower.split(/\s+/)
    .filter(w => w.length > 2));
  const pWords = new Set(prev.split(/\s+/)
    .filter(w => w.length > 2));
  const overlap = [...qWords].filter(w => pWords.has(w)).length;
  if (overlap / Math.max(qWords.size, pWords.size) > 0.7) return true;
}
```

### 4.4 速率限制与状态持久化

```javascript
const MIN_INTERVAL_MS = 3 * 60 * 60 * 1000;  // 至少 3 小时问一次
const MAX_QUESTIONS_PER_CYCLE = 2;
const QUESTION_STATE_FILE = 'evolution/question_generator_state.json';
```

状态文件记录：`lastAskedAt` + 最近 20 个问题（用于去重）

### 4.5 输出格式

```json
[{
  "question": "Recurring error in evolution cycle that auto-repair cannot resolve: ENOENT no such file",
  "amount": 0,
  "signals": ["recurring_error", "auto_repair_failed"],
  "priority": 3
}]
```

`amount: 0` 表示纯信息悬赏（无货币激励）。

### 4.6 CE 借鉴路径

| 优先级 | 借鉴点 | CE 落点 |
|--------|--------|---------|
| **P1** | 六策略问题生成 | `ObservationEntity` 中标记能力缺口类型（如 `capability_gap`、`unsupported_operation`） |
| **P1** | 模糊去重（词汇重叠度 > 70%） | `QuestionService` 去重：避免重复生成相似的"能力提升建议" |
| **P2** | 速率限制 + 状态持久化 | Hub bounty 频率控制，避免重复求援 |
| **P2** | 问题按 priority 排序 | CE 优先级队列：`P0`（连续失败） > `P1`（能力缺口） > `P2`（优化建议） |

---

## 5. A2A 协议客户端（`a2a.js`）

### 5.1 允许的 A2A 资产类型

```javascript
function isAllowedA2AAsset(obj) {
  return obj?.type === 'Gene'
      || obj?.type === 'Capsule'
      || obj?.type === 'EvolutionEvent';
}
```

仅这三类资产可通过 A2A 协议传输。

### 5.2 置信度降低（`lowerConfidence`）

外部接收的资产，置信度乘以系数（默认 0.6）：

```javascript
function lowerConfidence(asset, opts = {}) {
  const factor = opts.factor ?? 0.6;
  const cloned = JSON.parse(JSON.stringify(asset));

  if (cloned.type === 'Capsule')
    cloned.confidence = clamp01(cloned.confidence * factor);

  cloned.a2a = {
    status: 'external_candidate',
    source: opts.source || 'external',
    received_at: opts.received_at || nowIso(),
    confidence_factor: factor,
    schema_version: SCHEMA_VERSION,
  };

  return cloned;
}
```

**设计理由**：外部资产的置信度不应直接等于本地验证过的资产，需降权后再进入候选池。

### 5.3 Capsule 广播资格判断

```javascript
function isCapsuleBroadcastEligible(capsule) {
  // 1. 分数 ≥ 0.7
  if (capsule.outcome?.score < 0.7) return false;

  // 2. blast radius 在安全范围内
  if (!isBlastRadiusSafe(blast)) return false;

  // 3. 连续成功 streak ≥ 2
  const streak = computeCapsuleSuccessStreak({ capsuleId: capsule.id });
  if (streak < 2) return false;

  return true;
}
```

**成功 streak 计算**：

```javascript
function computeCapsuleSuccessStreak({ capsuleId, events }) {
  let streak = 0;
  for (let i = events.length - 1; i >= 0; i--) {
    const ev = events[i];
    if (ev.capsule_id !== capsuleId) continue;
    if (ev.outcome?.status === 'success') streak++;
    else break;
  }
  return streak;
}
```

### 5.4 Gene 广播资格判断

```javascript
function isGeneBroadcastEligible(gene) {
  return !!(
    gene?.id
    && Array.isArray(gene.strategy) && gene.strategy.length > 0
    && Array.isArray(gene.validation) && gene.validation.length > 0
  );
}
```

**注意**：与 Capsule 不同，Gene 广播不检查分数（Gene 是知识模板，非执行结果）。

### 5.5 A2A 输入解析（`parseA2AInput`）

支持三种格式：

1. **JSON 数组**：`[{...}, {...}]`
2. **单个 JSON 对象**：`{...}`
3. **JSONL**（逐行）：每行一个 JSON 对象

```javascript
function parseA2AInput(text) {
  try {
    const maybe = JSON.parse(text);
    if (Array.isArray(maybe))
      return maybe.map(unwrapAssetFromMessage).filter(Boolean);
    if (maybe?.type)
      return [unwrapAssetFromMessage(maybe)].filter(Boolean);
  } catch {}

  // JSONL fallback
  return text.split('\n')
    .map(l => JSON.parse(l.trim()))
    .map(unwrapAssetFromMessage)
    .filter(Boolean);
}
```

### 5.6 CE 借鉴路径

| 优先级 | 借鉴点 | CE 落点 |
|--------|--------|---------|
| **P0** | 外部资产置信度降权 | 外部 observation 摄入时 `confidence *= 0.6`，标记 `source: 'external'` |
| **P1** | Capsule 广播资格（分数 + blast radius + streak） | `GeneEntity` / `ObservationEntity` 设定发布门槛：`score >= 0.7` 且 `blast_radius <= medium` |
| **P1** | 成功 streak 计算 | 用于判断某个解决方案（capsule）的稳定性和可信赖度 |
| **P2** | JSONL 输入解析 | MCP 工具返回多行 JSON 时的解析容错处理 |

---

## 6. 端到端管线全图

```
solidify()
  │
  ├─► buildExecutionTrace()          [executionTrace.js]
  │     └─► EvolutionEvent.asset_id
  │
  ├─► captureDiffSnapshot()          [gitOps.js]
  │     └─► EvolutionEvent.diff_snapshot
  │
  ├─► isCriticalProtectedPath()      [gitOps.js]
  │     └─► 若违规 → rollbackTracked()
  │
  ├─► geneToSkillMd()                 [skillPublisher.js]
  │     └─► publishSkillToHub()
  │           ├─► 201/200 → 完成
  │           └─► 409 → updateSkillOnHub()
  │
  ├─► generateQuestions()             [questionGenerator.js]
  │     └─► Hub bounty system (A2A fetch payload.questions)
  │
  ├─► exportEligibleCapsules()        [a2a.js]
  │     └─► isCapsuleBroadcastEligible()
  │           ├─► score ≥ 0.7
  │           ├─► blast_radius safe
  │           └─► success_streak ≥ 2
  │
  └─► exportElibleGenes()             [a2a.js]
        └─► isGeneBroadcastEligible()
              ├─► has strategy[]
              └─► has validation[]
```

---

## 7. 与 CE 的对照总表

| Evolver 模块 | 功能 | CE 对应实体 | 借鉴优先级 |
|-------------|------|------------|-----------|
| `executionTrace.js` | 脱敏执行轨迹 | `ObservationEntity.metadata` | P0 |
| `gitOps.js` | Git 安全回滚 + 保护区 | `AgentService` 事务管理 | P0 |
| `skillPublisher.js` | Gene→SKILL.md + Hub 发布 | `SkillService` | P1 |
| `questionGenerator.js` | 六策略主动求援 | 能力缺口识别服务 | P1 |
| `a2a.js` | 资产广播资格 + 置信降权 | 外部 observation 摄入策略 | P1 |

---

## 8. 未覆盖模块清单

以下模块已在其它 doc 中覆盖：

| 模块 | 文件 | 覆盖 doc |
|------|------|---------|
| `solidify.js` | 1344 行 | [34](./34-solidify-pipeline-end-to-end.md) |
| `skillDistiller.js` | 1234 行 | [47](./47-curriculum-executiontrace-skill-distillation.md) |
| `curriculum.js` | 163 行 | [47](./47-curriculum-executiontrace-skill-distillation.md) |
| `executionTrace.js` | 201 行 | **本文 §1** |
| `gitOps.js` | 230 行 | **本文 §2** |
| `skillPublisher.js` | 307 行 | **本文 §3** |
| `questionGenerator.js` | 212 行 | **本文 §4** |
| `a2a.js` | 173 行 | **本文 §5** |
| `a2aProtocol.js` | 1221 行 | [35](./35-a2a-protocol-asset-lifecycle-feedback.md) |
| `prompt.js` | 616 行 | 待新增独立 doc（见 §9） |

---

## 9. `prompt.js`（616 行）待深度分析

`prompt.js` 是 GEP 主循环prompt构建系统，核心组件：

- **`SCHEMA_DEFINITIONS`**：5 类输出对象的严格 JSON Schema（Mutation / PersonalityState / EvolutionEvent / Gene / Capsule）
- **`buildGepPrompt`**：主构建函数，组装 20+ 个 context block
- **`buildAntiPatternZone`**：从失败 capsule 中过滤与当前 signal 重叠 ≥ 40% 的作为"避免"建议
- **`buildLessonsBlock`**：Hub lessons 正/负面分类
- **`buildNarrativeBlock`**：从 `narrativeMemory.js` 加载近期叙事（3KB 上限）
- **`buildPrinciplesBlock`**：从 `evolution_principles.md` 加载指导原则
- **Stagnation Directive**：检测 `evolution_stagnation_detected` 时强制 `INTENT: INNOVATE`
- **History Block**：最近 8 个 cycle 的 intent + signal + gene + outcome
- **Token 管理**：`GEP_PROMPT_MAX_CHARS`（默认 50KB），execution context 硬上限 20KB

**建议后续新增 `69-prompt-engineering-module-deep-dive.md`** 专项覆盖。
