# 47 Curriculum + ExecutionTrace + SkillDistiller 深度分析

**目标**：深入分析 EvoMap 记忆系统的三大关键机制——**Curriculum（课程系统）**、**ExecutionTrace（执行追踪）** 和 **SkillDistiller（技能提炼器）**，提炼可借鉴的设计思想。

**数据来源**：`EvoMap/evolver/src/gep/curriculum.js`、`executionTrace.js`、`validationReport.js`、`gitOps.js`、`skillDistiller.js`、`skillPublisher.js`

**最后更新**：2026-04-24

---

## 目录

- [§1 Curriculum 课程学习系统](#s1-curriculum-课程学习系统)
  - [§1.1 三区分类机制](#s11-三区分类机制)
  - [§1.2 课程信号生成](#s12-课程信号生成)
  - [§1.3 难度等级晋升](#s13-难度等级晋升)
  - [§1.4 BlueCortexCE 借鉴路径](#s14-bluecortexce-借鉴路径)
- [§2 ExecutionTrace 执行追踪系统](#s2-executiontrace-执行追踪系统)
  - [§2.1 三级脱敏等级](#s21-三级脱敏等级)
  - [§2.2 错误签名提取](#s22-错误签名提取)
  - [§2.3 Tool Chain 推断](#s23-tool-chain-推断)
  - [§2.4 BlueCortexCE 借鉴路径](#s24-bluecortexce-借鉴路径)
- [§3 ValidationReport 标准化验证报告](#s3-validationreport-标准化验证报告)
  - [§3.1 Content-hash 资产 ID](#s31-content-hash-资产-id)
  - [§3.2 环境指纹可重现性](#s32-环境指纹可重现性)
- [§4 GitOps 关键文件保护与回滚](#s4-gitops-关键文件保护与回滚)
  - [§4.1 保护路径配置](#s41-保护路径配置)
  - [§4.2 三种回滚模式](#s42-三种回滚模式)
  - [§4.3 BlueCortexCE 借鉴路径](#s43-bluecortexce-借鉴路径)
- [§5 SkillDistiller 技能提炼管线](#s5-skilldistiller-技能提炼管线)
  - [§5.1 三步提炼流程](#s51-三步提炼流程)
  - [§5.2 模式分析引擎](#s52-模式分析引擎)
  - [§5.3 LLM 驱动的 Gene 合成](#s53-llm-驱动的-gene-合成)
  - [§5.4 重复避免机制](#s54-重复避免机制)
  - [§5.5 BlueCortexCE 借鉴路径](#s55-bluecortexce-借鉴路径)
- [§6 SkillPublisher SKILL.md 市场格式生成](#s6-skillpublisher-skillmd-市场格式生成)
  - [§6.1 技能名称净化](#s61-技能名称净化)
  - [§6.2 SKILL.md 结构](#s62-skillmd-结构)
- [§7 综合启示：面向 BlueCortexCE 的建议](#s7-综合启示面向-bluecortexce-的建议)

---

## §1 Curriculum 课程学习系统

`curriculum.js` 实现了一个基于**掌握度（Mastery）**的课程学习系统，灵感来自人类教育学中的"Sorted Cards"（间隔重复）和"Zone of Proximal Development"（最近发展区）理论。

### §1.1 三区分类机制

核心参数：

| 参数 | 值 | 含义 |
|------|-----|------|
| `MASTERY_THRESHOLD` | `0.8` | ≥80% 成功率 = 已掌握 |
| `FAILURE_THRESHOLD` | `0.3` | ≤30% 成功率 = 持续失败 |
| `MASTERY_MIN_ATTEMPTS` | `3` | 至少 3 次尝试才能判定掌握 |
| `MAX_CURRICULUM_SIGNALS` | `2` | 每次最多生成 2 个课程信号 |

数据来源：从 `memoryGraph.jsonl` 读取最近 **200 条 outcome 记录**（`kind === 'outcome'`）。

三区定义：

```
mastered  = success_rate ≥ 0.8 AND total ≥ 3
failing   = success_rate ≤ 0.3 AND total ≥ 2
frontier  = 所有其他（有统计意义但未掌握的）
```

**关键设计**：frontier 区按 `|rate - 0.5|` 升序排列，优先选择**最接近 50% 成功率**的技能——即"处于学习边缘"的技能。这正是 ZPD（最近发展区）的量化实现。

### §1.2 课程信号生成

生成逻辑（优先级递减）：

```
1. 如果有 capabilityGaps（外部输入的技能缺口），且未掌握 → 生成 curriculum_target:gap:<gap>
2. 如果信号不足 2 个，且 frontier 非空 → 取最边缘技能 → curriculum_target:frontier:<key>
3. 最多输出 2 个课程信号
```

信号格式：`curriculum_target:gap:<capability>` 或 `curriculum_target:frontier:<signal_key>`

课程状态持久化到 `evolution_dir/curriculum_state.json`：

```json
{
  "level": 3,
  "current_targets": ["curriculum_target:gap:error_handling", "curriculum_target:frontier:http_timeout"],
  "completed": [{ "signal": "...", "outcome": "success", "at": "2026-04-24T..." }],
  "updated_at": "..."
}
```

### §1.3 难度等级晋升

进度追踪（`markCurriculumProgress`）：
- 每次完成记录到 `completed`（最多保留 50 条）
- 每 **5 次成功**，`level` +1（上限 5）
- `level` 范围 [1, 5]，用于控制信号选择策略的激进程度

### §1.4 BlueCortexCE 借鉴路径

| CE 现状 | EvoMap 设计 | 借鉴建议 |
|---------|------------|---------|
| Observation/Summary 按时间衰减 | 三区分类（mastered/failing/frontier） | 可为 BlueCortexCE 设计**掌握度评分**——某类任务的平均 outcome 质量 |
| 无课程概念 | curriculum_target 信号驱动进化 | 可为 CE 设计"技能缺口驱动"的知识获取：检测到某类任务持续失败 → 触发知识补充 |
| Session 无难度等级 | Level 1-5 控制策略激进程度 | CE 的 Mode 可借鉴：困难场景自动切换到更深层的 Context 策略 |

---

## §2 ExecutionTrace 执行追踪系统

`executionTrace.js` 为每次进化执行生成**结构化、脱敏、可共享**的执行轨迹，用于 Hub 生态的评估和反馈。

### §2.1 三级脱敏等级

通过环境变量 `EVOLVER_TRACE_LEVEL` 控制（默认值 `minimal`）：

| 级别 | 触发条件 | 内容范围 |
|------|---------|---------|
| `none` | `TRACE_LEVELS.none` | 不生成 trace |
| `minimal`（默认） | `TRACE_LEVELS.minimal` | 仅核心指标：gene_id、mutation_category、signals_matched、outcome、files_changed_count、lines_added/removed、validation_result、blast_radius |
| `standard` | `TRACE_LEVELS.standard` | 完整指标：file_types 统计、validation_commands、error_signatures、tool_chain、duration_ms、canary_ok |

### §2.2 错误签名提取

错误消息脱敏的核心函数 `extractErrorSignature`：

```javascript
// JS 错误类型：TypeError, ReferenceError → "TypeError"
const jsError = text.match(/^((?:[A-Z][a-zA-Z]*)?Error)\b/);

// errno 风格：ECONNRESET, ENOENT → "ECONNRESET"
const errno = text.match(/\b(E[A-Z]{2,})\b/);

// HTTP 状态码：400, 500 → "HTTP_400"
const http = text.match(/\b((?:4|5)\d{2})\b/);
```

**设计原则**：只保留**类型签名**，丢弃具体消息内容。`"TypeError: x is not a function"` → `"TypeError"`

### §2.3 Tool Chain 推断

从 validation 命令推断使用的工具类型：

```javascript
// 从命令字符串反推工具
if (cmd.startsWith('npm test') || includes('jest') || includes('mocha'))
  → 'test_run'
if (includes('lint') || includes('eslint'))
  → 'lint_check'
if (includes('validate') || includes('check'))
  → 'validation_run'
if (cmd.startsWith('node '))
  → 'node_exec'
```

### §2.4 BlueCortexCE 借鉴路径

| CE 现状 | EvoMap 设计 | 借鉴建议 |
|---------|------------|---------|
| Observation/Summary 无执行上下文 | ExecutionTrace 含 blast_radius、tool_chain | 可为 CE 的 `processToolUseAsync` 关联一个**执行轨迹摘要**：记录工具调用模式、验证结果、变更范围 |
| Session 事件无结构化 trace | ExecutionTrace 是 self-contained JSON | 可为 Java 设计类似的 `ExecutionTrace` DTO，通过 SSE 广播给观察者 |
| 错误日志包含原始消息 | extractErrorSignature 只保留类型签名 | CE 的错误上报可借鉴：上报 Error 类型而非具体消息，防止隐私泄露 |

---

## §3 ValidationReport 标准化验证报告

`validationReport.js` 定义了跨系统互操作的**标准验证报告格式**。

### §3.1 Content-hash 资产 ID

```javascript
report.asset_id = computeAssetId(report);  // 来自 contentHash.js
```

`asset_id` 基于报告内容本身计算 hash（content-addressable），而非随机 UUID。这意味着：
- 相同内容的报告 → 相同 asset_id
- 可用于去重、缓存、比较

### §3.2 环境指纹可重现性

```javascript
report.env_fingerprint = captureEnvFingerprint();
report.env_fingerprint_key = envFingerprintKey(env);
```

每次验证记录执行时的**环境快照**，使得：
- "在环境 X 下，Gene Y 验证通过"的陈述可验证
- Hub 可以判断"同一 Gene 在不同环境下是否都通过"

---

## §4 GitOps 关键文件保护与回滚

`gitOps.js` 从 `solidify.js` 中提取，负责所有 git 操作和变更回滚。

### §4.1 保护路径配置

```javascript
// 保护的目录前缀（skills/ 下的关键包）
CRITICAL_PROTECTED_PREFIXES = [
  'skills/feishu-evolver-wrapper/',
  'skills/feishu-common/',
  // ... 共 10 个 skills 目录
];

// 保护的单文件
CRITICAL_PROTECTED_FILES = [
  'MEMORY.md', 'SOUL.md', 'IDENTITY.md', 'AGENTS.md',
  'USER.md', 'HEARTBEAT.md', 'RECENT_EVENTS.md',
  'TOOLS.md', 'TROUBLESHOOTING.md',
  'openclaw.json', '.env', 'package.json'
];
```

**关键特性**：
- 路径比较前做 `normalizeRelPath`（统一斜杠、去除 `./` 前缀）
- 绝对路径和相对路径都在 repo root 下做边界检查，防止路径穿越攻击
- `rollbacks` 模式删除新建文件时跳过所有受保护路径

### §4.2 三种回滚模式

通过 `EVOLVER_ROLLBACK_MODE` 环境变量控制：

| 模式 | 行为 | 适用场景 |
|------|------|---------|
| `none` | 不回滚，保留所有变更 | 调试模式 |
| `stash` | `git stash push -m "evolver-rollback-<timestamp>"` | 保守模式，可恢复 |
| `hard`（默认） | `git restore --staged . && git reset --hard` | 安全模式，确保干净 |

回滚范围：
1. **Tracked 文件**：`git restore --staged . && git reset --hard`
2. **新建 untracked 文件**：记录 baseline，对比当前，删除非 baseline 的文件（跳过受保护路径）
3. **空目录清理**：删除回滚后变空的目录（跳过受保护路径）

### §4.3 BlueCortexCE 借鉴路径

| CE 现状 | EvoMap 设计 | 借鉴建议 |
|---------|------------|---------|
| 无变更保护概念 | CRITICAL_PROTECTED_PREFIXES/FILES | CE 可为关键 Observation/Summary 设置保护标记，防止被意外覆盖 |
| Session rollback 无结构化设计 | 三种 rollback 模式 + baseline 对比 | CE 的 Session 可借鉴：支持"soft revert"（重放而非重建）|
| 无 git-aware 操作 | gitOps 独立模块 + 路径边界检查 | CE 的文件操作应始终在 repo root 下做边界验证 |

---

## §5 SkillDistiller 技能提炼管线

`skillDistiller.js` 是 EvoMap 的**知识提炼引擎**：将成功的进化成果（capsules）转换为可复用的 Genes（技能）。

### §5.1 三步提炼流程

```
collectDistillationData()   → 从 capsules.json/jsonl + events.jsonl + memoryGraph.jsonl 收集数据
analyzePatterns()           → 分析高频模式、策略漂移、覆盖缺口
distillToGene()             → LLM 合成 Gene
```

**输入要求**：
- `DISTILLER_MIN_CAPSULES = 10`（至少 10 个成功 capsule 才触发提炼）
- `DISTILLER_MIN_SUCCESS_RATE = 0.7`（成功率 ≥70% 才算成功 capsule）
- `DISTILLER_INTERVAL_HOURS = 24`（每 24 小时最多一次提炼）

### §5.2 模式分析引擎

`analyzePatterns()` 输出的报告结构：

```javascript
{
  high_frequency: [  // total_count ≥ 5 的 gene，按 count 降序
    { gene_id, count, avg_score, top_triggers: ['signal_a', 'signal_b'] }
  ],
  strategy_drift: [  // summaries 相似度 < 0.6 的 gene（策略发生了漂移）
    { gene_id, similarity, early_summary, recent_summary }
  ],
  coverage_gaps: [  // signals 出现 ≥ 3 次但无对应 gene 的缺口
    { signal: 'error_handling', frequency: 7 }
  ],
  total_success: N,
  total_capsules: M,
  success_rate: N/M
}
```

**设计亮点**：
- **strategy_drift** 检测：同 gene 的 summary 从早期到最近是否发生变化（用 Jaccard 相似度 < 0.6 判断）。发生变化说明该 gene 代表的策略在进化，需要重新提炼
- **coverage_gaps** 检测：信号出现频繁但无对应 gene，说明需要新建 gene 来填补知识空白

### §5.3 LLM 驱动的 Gene 合成

`buildDistillationPrompt()` 生成提示词，包含严格的输出规范：

**Gene ID 规则**（防止污染）：
- 必须以 `gene_distilled_` 开头
- 后缀为 3-6 个 kebab-case 单词描述核心能力
- 禁止：时间戳、随机数字、工具名（cursor/vscode）、UUID
- ✅ `gene_distilled_retry-with-exponential-backoff`
- ❌ `gene_distilled_cursor-1773331925711`

**Summary 规则**：
- 30-200 字符，人类可读 marketplace 风格
- ❌ `"Distilled from capsules"`, `"AI agent skill"`
- ✅ `"Retry failed HTTP requests with exponential backoff, jitter, and circuit breaker to prevent cascade failures"`

**Signals_match 规则**：
- 3-7 个 lowercase_snake_case 关键词
- 描述问题域和解决方案，而非实现细节
- ✅ `["http_retry", "request_timeout", "exponential_backoff", "circuit_breaker", "resilience"]`
- ❌ `["cursor_auto_1773331925711", "cli_headless"]`

**Strategy 规则**：
- 5-10 个可执行步骤
- 每个步骤是动词开头的祈使句
- ✅ `"Wrap the HTTP call in a retry loop with \`maxRetries=3\` and initial delay of 500ms"`
- ❌ `"Handle retries"`, `"Fix the issue"`

**Constraints 规则**：
- `max_files ≤ 12`
- `forbidden_paths` 必须包含 `[".git", "node_modules"]`

**Validation 规则**：
- 命令必须以 `node ` / `npm ` / `npx ` 开头（安全约束）
- 命令必须真正验证 Gene 有效性
- ❌ `node -v`（什么都不证明）

### §5.4 重复避免机制

**Data Hash 防重复提炼**：

```javascript
function computeDataHash(capsules) {
  const ids = capsules.map(c => c.id || '').sort();
  return crypto.createHash('sha256').update(ids.join('|')).digest('hex').slice(0, 16);
}
```

- Capsule 集合的 SHA-256 hash 作为 data hash
- 如果 capsule 集合未变化（相同 ID 集合），data hash 不变，跳过重复提炼

**existingGenes 校验**：
- 提炼前检查已有 genes（通过 `readGenesFromAssetsDir`）
- 如果已有相同 signals_match 的 gene，跳过生成

### §5.5 BlueCortexCE 借鉴路径

| CE 现状 | EvoMap 设计 | 借鉴建议 |
|---------|------------|---------|
| Observation/Summary 碎片化，无结构化沉淀 | Capsule → Gene 提炼管线 | CE 可以设计 **KnowledgeDistiller**：将高频成功交互提炼为"知识片段"（非 Gene，但类似的概念） |
| 无模式分析 | analyzePatterns() 检测 drift 和 gaps | CE 的 `MemoryRefineService` 可借鉴：分析 Observation 随时间的变化趋势，检测"记忆漂移" |
| 无 ID 命名规范 | Gene ID kebab-case + 前缀 + 禁止规则 | CE 的 Extracted Knowledge 可借鉴：建立命名规范，防止工具/会话 ID 污染知识表示 |
| 提炼依赖 LLM | 严格提示词模板 + JSON Schema | CE 的结构化提取（Phase 3）可借鉴：提供 Marketplace 质量标准的提示词模板 |

---

## §6 SkillPublisher SKILL.md 市场格式生成

`skillPublisher.js` 负责将 Gene 转换为**市场级别的 SKILL.md** 格式。

### §6.1 技能名称净化

`sanitizeSkillName()` 净化规则：

```javascript
// 1. 去除 gene_distilled_ / gene_ 前缀
// 2. _ → -（下划线转横线）
// 3. 去除所有嵌入的时间戳（10+ 位数字）
// 4. 去除工具名（cursor, vscode, vim, emacs, windsurf, copilot, cline, codex）+ 后面可能跟的数字
// 5. 去除全数字前缀（8+ 位）
// 6. 名称主体 < 6 字符 → 拒绝
```

**Fallback 名称派生**：
- 如果净化后名称不合格，从 `signals_match` 前 3 个和 `summary` 提取关键词生成 fallback 名称

### §6.2 SKILL.md 结构

生成的 SKILL.md 包含标准化章节：

```markdown
---
name: Retry With Backoff
description: Retry failed HTTP requests with exponential backoff...
---

# Retry With Backoff

## When to Use
- When your project encounters: `http_retry`, `request_timeout`...

## Trigger Signals
- `http_retry`
- `request_timeout`
- ...

## Preconditions
- Project uses Node.js >= 18
- ...

## Strategy
1. [步骤 1]
2. [步骤 2]
...

## Validation
- `npm test`
- `npx tsc --noEmit`
```

---

## §7 综合启示：面向 BlueCortexCE 的建议

### 高优先级借鉴（直接可落地）

1. **掌握度评分系统**
   - 在 CE 中为每类任务（Issue 解决、代码生成、文档写作）维护"成功率"
   - 基于 Outcome 记录（CE 的 Session 已有 outcome 字段）
   - 三区分类驱动知识优先级排序

2. **ExecutionTrace DTO**
   - 为 `processToolUseAsync` 关联结构化的执行轨迹
   - 含 blast_radius（变更范围）、tool_chain（工具类型）、validation_result
   - 通过 SSE 广播（符合 CE 的 SSE 契约）

3. **错误签名脱敏**
   - 上报 Error 类型而非具体消息
   - 避免用户代码内容/错误详情泄露

### 中优先级借鉴（需要设计）

4. **KnowledgeDistiller（知识提炼管线）**
   - 设计类似 Capsule → Gene 的机制：将高频成功交互提炼为"结构化知识片段"
   - 检测记忆漂移（strategy_drift）和覆盖缺口（coverage_gaps）
   - 基于 Content-hash 的去重

5. **Git-aware 变更保护**
   - 为 MEMORY.md、HEARTBEAT.md 等关键文件设置保护路径
   - Session 变更支持 stash/hard 模式回滚

### 低优先级（长期方向）

6. **Marketplace 格式导出**
   - CE 的结构化知识可导出为市场级别的文档格式
   - 供其他 Agent 发现和复用

---

## 附录：关键源码文件

| 文件 | 行数 | 核心职责 |
|------|------|---------|
| `curriculum.js` | ~130 | 课程学习状态机、三区分类、信号生成 |
| `executionTrace.js` | ~250 | 三级脱敏、执行轨迹构建、签名提取 |
| `validationReport.js` | ~60 | 标准化验证报告、content-hash ID |
| `gitOps.js` | ~250 | 关键文件保护、三种回滚模式 |
| `skillDistiller.js` | ~1100+ | 三步提炼流程、模式分析、LLM 合成 |
| `skillPublisher.js` | ~400 | 技能名称净化、SKILL.md 生成 |
