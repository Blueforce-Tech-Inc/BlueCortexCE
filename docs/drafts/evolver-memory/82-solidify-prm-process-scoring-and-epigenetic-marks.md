# Doc 82 — Solidify PRM Process Scoring + Epigenetic Marks + Gene Learning Adaptation

**目标**：深入分析 `solidify.js` 中三个高度创新但未被独立文档化的机制：
1. **PRM-inspired Process Scoring**（8相多步评分）
2. **Epigenetic Marks**（表观遗传标记）
3. **Gene Learning Adaptation**（基因学习适应）+ FailedCapsule + Anti-pattern Publish

**源码**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/solidify.js`
**最后更新**：2026-05-04 | doc 82 v1 | 与 doc 34（Solidify管线端到端）的区别：doc 34 覆盖 PRM/ValidationReport/Content-addressable/Canary/Leak-check 全局视角；本文专注 `computeProcessScores` 内部8相评分数学、Epigenetic Marks 生物类比机制、`adaptGeneFromLearning` 适应循环，以及 FailedCapsule/Anti-pattern publish 失败资产化设计。

---

## §1 PRM-Inspired Process Scoring (`computeProcessScores`)

### 1.1 设计背景

传统二元结果（成功/失败）只能提供粗粒度反馈，无法区分：
- 约束检查通过但 validation 失败的"质量差"变异
- 约束检查失败但 validation 通过的"危险但正确"变异
- 基因选错但 mutation 恰好弥补的"意外成功"

`solidify.js` L451–L544 实现了 Process Reward Model（过程奖励模型）风格的 8 相独立评分，每相对应进化管线的一个关键环节。

### 1.2 八相评分定义

| Phase | 权重 | 评分逻辑 |
|-------|------|---------|
| **signal** (信号质量) | 5% | `0.4 + signals.length × 0.1`，封顶1.0。无信号=0.5 |
| **selection** (基因选择) | 10% | auto-gene=0.3，named-gene=0.7，curated-gene=0.9 |
| **mutation** (变异质量) | 5% | 有 rationale+category=0.8；low-risk=0.9；high-risk=0.6；无=0.3 |
| **blast** (爆炸半径控制) | 15% | 0文件=0.4；≤50% max_files=1.0；≤max_files=0.7；超限=0.2；估计偏差>3x 分数减半 |
| **constraint** (约束合规) | 25% | `1 - violations × 0.25`，最低0 |
| **validation** (验证通过率) | 25% | 通过数/总数；空 validation 数组=0.5（基因应至少定义1条验证命令）|
| **protocol** (协议合规) | 10% | `1 - violations × 0.3`，最低0 |
| **canary** (金丝雀健康) | 5% | ok/skipped=1.0；failed=0 |

**合成公式**（加权线性组合）：
```
composite = Σ(phase_score × weight)
           = signal×0.05 + selection×0.10 + mutation×0.05
           + blast×0.15 + constraint×0.25 + validation×0.25
           + protocol×0.10 + canary×0.05
```

### 1.3 关键设计洞察

**1. validation 权重最高（25%）**：solidify 的核心承诺是"基因定义的验证步骤必须通过"，因此 validation 失败是最严重的降权。

**2. 空 validation 数组 → 0.5 分**：这是一个强制机制——基因如果未定义任何验证命令，其 validation_score 自动扣一半分，驱动 Gene 作者必须提供验证步骤。

**3. blast radius 估计偏差惩罚**：如果实际变更文件数超过预先估计的3倍，`blastScore ×= 0.5`。这惩罚了过度乐观的预估，推动基因作者准确评估变更范围。

**4. 估计偏差检测**（L510-L515）：
```javascript
const ratio = blast.files / estFiles;
if (ratio > 3) blastScore *= 0.5;
else if (ratio > 2) blastScore *= 0.7;
```

### 1.4 CE 借鉴路径

**P0**：将 process scoring 思想引入 BlueCortexCE 的 Observation 质量评估。当前 CE 只有二元成功/失败，可以引入多维质量评分：
- signal_quality：观察的信号丰富度
- context_relevance：上下文相关性
- embedding_quality：向量嵌入质量

**P1**：在 `StructuredExtractionService` 中引入"提取质量评分"，约束检查权重参考 `constraint` 相设计。

---

## §2 Epigenetic Marks（表观遗传标记）

### 2.1 生物类比

表观遗传学研究生物如何在不改变 DNA 序列的情况下，通过 DNA 甲基化等机制响应环境变化——孙代可能继承祖代的环境适应记忆。

EvoMap 的 Epigenetic Marks 采用完全相同的思想：
- **不是修改 Gene 策略本身**（那是 Mutation 的工作）
- **而是在特定环境上下文中增强或抑制 Gene 的表达强度**

### 2.2 实现机制（L273-L350）

**环境上下文标识**（platform/arch/node_version 三元组）：
```javascript
const envContext = [platform, arch, nodeVersion].join('/') || 'unknown';
// 例如: "darwin/arm64/v22.15.0"
```

**结果驱动的标记更新**：

| Outcome | 已有标记 | 无标记 |
|---------|---------|--------|
| **success** | boost += 0.05（上限0.5）| 新增 +0.1 |
| **failed** | boost -= 0.1（下限-0.5）| 新增 -0.1 |

**衰减机制**（90天窗口，最多保留10个标记）：
```javascript
const cutoff = Date.now() - 90 * 24 * 60 * 60 * 1000;
gene.epigenetic_marks = gene.epigenetic_marks
  .filter(m => new Date(m.created_at).getTime() > cutoff)
  .slice(-10);
```

### 2.3 标记查询（表达增强）

```javascript
function getEpigeneticBoost(gene, envFingerprint) {
  const envContext = [platform, arch, nodeVersion].join('/');
  const mark = gene.epigenetic_marks.find(m => m.context === envContext);
  return mark ? Number(mark.boost) : 0;  // 范围 [-0.5, +0.5]
}
```

该 boost 值在 Selector 的基因评分中被叠加——同一基因在 macOS/arm64 表现好，在 Linux/x64 可能表现差。

### 2.4 CE 借鉴路径

**P1**：BlueCortexCE 的 Observation 可以在 session-level 引入类似的"环境上下文标记"：
- 操作系统平台（影响工具可用性）
- 项目语言栈（影响代码观察的相关性）
- 用户偏好（影响信息优先级）

例如：一个擅长 Python 的 session 产生的 Observation，在纯 Java 项目中的相关性应动态降权。

---

## §3 Gene Learning Adaptation（`adaptGeneFromLearning`）

### 3.1 失败模式到基因知识的转化

`solidify.js` L136-L183 的 `adaptGeneFromLearning` 实现了失败经验的结构化积累：

**成功时的适应**：
```javascript
if (outcomeStatus === 'success') {
  // 将新的 problem:/area: 信号添加到 signals_match
  for (const sig of learningSignals) {
    if (sig.startsWith('problem:') || sig.startsWith('area:')) {
      gene.signals_match.push(sig);  // 扩大基因信号覆盖范围
    }
  }
}
```

**失败时的适应**：
```javascript
if (outcomeStatus === 'failed') {
  gene.anti_patterns.push({
    at: nowIso(),
    mode: failureMode.mode,         // 'soft' | 'hard'
    reason_class: failureMode.reasonClass,
    learning_signals: learningSignals.slice(0, 8),
  });
  // 保留最近12条 anti-patterns
  gene.anti_patterns = gene.anti_patterns.slice(-12);
}
```

**learning_history 记录**（最近20条）：
```javascript
gene.learning_history.push({
  at: nowIso(),
  outcome: outcomeStatus,
  mode: failureMode.mode,
  reason_class: failureMode.reasonClass,
  retryable: !!failureMode.retryable,
  learning_signals: learningSignals.slice(0, 12),
});
```

### 3.2 Gene 知识积累的三大维度

| 维度 | 数据结构 | 容量 | 增长条件 |
|------|---------|------|---------|
| **信号覆盖** | `signals_match[]` | 无硬限制 | 成功时 problem:/area: 信号 |
| **失败经验** | `anti_patterns[]` | ≤12条 | 失败时 |
| **历史轨迹** | `learning_history[]` | ≤20条 | 每次固化 |

### 3.3 CE 借鉴路径

**P0**：BlueCortexCE 的 Gene 等价物是 `Observation` + `Summary`。失败适应模式可以直接迁移：
- Observation 命中某场景但生成质量差 → 在 Observation 上记录 anti_pattern
- Observation 成功泛化到新场景 → 扩展其 `signals_match`（CE 中是 tag/concept）

**P1**：`learning_history` 机制可以用于 `ModeService` 的能力边界探测——当某个 Mode 持续失败时，自动将其标记为"不稳定"并降低使用频率。

---

## §4 FailedCapsule：失败资产化

### 4.1 核心问题

进化失败后，`rollbackTracked()` 会回滚所有变更，导致失败现场被摧毁。"失败的变异"本身包含高价值信息（什么路径行不通、为什么），但传统设计将其丢弃。

### 4.2 解决方案：rollback 前捕获

```javascript
// L870-L893：在 rollback 之前捕获 failed diff
if (!dryRun && !success) {
  const diffSnapshot = captureDiffSnapshot(repoRoot);
  if (diffSnapshot) {
    const failedCapsule = {
      type: 'Capsule',
      id: 'failed_' + buildCapsuleId(ts),
      outcome: { status: 'failed', score },
      gene: geneUsed.id,
      trigger: signals.slice(0, 8),
      diff_snapshot: diffSnapshot,
      failure_reason: failureReason,
      learning_signals: softFailureLearningSignals,
      constraint_violations: constraintCheck.violations,
      env_fingerprint: envFp,
      blast_radius: { files, lines },
    };
    appendFailedCapsule(failedCapsule);
  }
}
```

**关键设计**：diff 在 `rollbackTracked()` 之前捕获，确保即使文件被还原，失败的可执行上下文也被保留。

### 4.3 FailedCapsule 的下游使用

`FailedCapsule` 不会触发 `isBlastRadiusSafe` 检查，也不会广播到 Hub，但会：
1. 被 `appendFailedCapsule()` 追加到 `failed_capsules.jsonl`
2. 在 `selectGeneAndCapsule` 管线中参与 Ban 机制（连续失败 → drift）

### 4.4 CE 借鉴路径

**P1**：BlueCortexCE 的 extraction 失败可以被类似地捕获：
- 提取失败时保留 prompt + LLM response + context snapshot
- 用于离线回放分析和 prompt 优化

---

## §5 Anti-pattern Auto-Publish（反模式自动发布）

### 5.1 设计动机

成功的 Capsule 广播到 Hub 帮助他人；**失败的 anti-pattern 广播帮助他人避免同样的错误**。两者信息价值对称。

### 5.2 资格条件（全部满足）

```javascript
const hasHighInfoFailure =
  (constraintViolations.length > 0)        // 约束违规（高信息量）
  || (canary && !canary.ok && !canary.skipped);  // 金丝雀失败（严重）
// 注意：routine validation 失败不发布（期望的调试信息）
```

### 5.3 发布bundle结构

```javascript
{
  type: 'publish',
  subtype: 'anti-pattern',        // 区别于普通 publish
  gene: {
    anti_pattern: true,
    failure_reason: buildFailureReason(...),
    signals_match: signals,
  },
  capsule: {
    type: 'Capsule',
    outcome: { status: 'failed', confidence: 0 },
    trigger: signals.slice(0, 8),
  },
}
```

### 5.4 CE 借鉴路径

**P2**：BlueCortexCE 的 extraction 误报/漏报可以类似机制发布到 Hub：
- 帮助其他用户识别常见提取失败模式
- 构建行业级"常见提取陷阱"知识库

---

## §6 Narrative Memory（有界叙事记忆）

### 6.1 设计目标

`narrativeMemory.js` 提供人类可读的进化历史摘要，用于：
- 快速了解系统当前的进化状态
- 无需解析 JSONL 即可理解上下文

### 6.2 容量约束

```javascript
const MAX_NARRATIVE_ENTRIES = 30;   // 最多30条
const MAX_NARRATIVE_SIZE = 12000;   // 最大12KB
```

**双重裁剪策略**：
1. 先按条目数裁剪（保留最新30条）
2. 若仍超12KB，再从最早端删除5条后重新拼接

### 6.3 Narrative 条目格式

```markdown
### [2026-05-04 14:30] REPAIR - success
- Gene: gene_tool_validation_v2 | Score: 0.85 | Scope: 3 files, 47 lines
- Signals: [log_error, protocol_drift, validation_failed]
- Why: Low confidence in signal detection
- Strategy:
  1. Extract structured signals from logs
  2. Select existing Gene by signals match
  3. Apply smallest reversible patch
- Result: Solidified: gene_tool_validation_v2 matched signals...
```

### 6.4 CE 借鉴路径

**P0**：BlueCortexCE 的 `SessionSummary` 机制已部分实现类似叙事。但可以引入"全局演化叙事"：
- 记录整个项目生命周期的重要决策和结果
- 12KB/30条约束保证不会无限膨胀

---

## §7 ValidationReport 标准化

### 7.1 架构设计

`validationReport.js`（55行）定义了标准的机器可读验证报告格式，独立于具体的验证命令：

```javascript
{
  type: 'ValidationReport',
  schema_version: SCHEMA_VERSION,
  id: 'vr_' + Date.now(),
  gene_id: 'gene_xxx',
  env_fingerprint: { platform, arch, node_version, ... },
  env_fingerprint_key: 'sha256_hex_16chars',
  commands: [
    { command: 'node scripts/validate.js', ok: true, stdout: '...', stderr: '' },
    { command: 'npm test', ok: false, stdout: '', stderr: 'FAIL: test x' },
  ],
  overall_ok: false,
  duration_ms: 2340,
  created_at: '2026-05-04T14:30:00.000Z',
  asset_id: 'sha256:...',  // 内容寻址
}
```

### 7.2 关键特性

**环境指纹嵌入**：每次验证报告包含当时的完整环境上下文，支持跨环境的验证结果对比（"在 macOS/arm64 通过但在 Linux/x64 失败"）。

**双重字段名兼容**：`stdout`/`out` 和 `stderr`/`err` 同时支持，兼容不同命令执行器的输出格式。

### 7.3 CE 借鉴路径

**P0**：`StructuredExtractionService` 的提取结果应附带 `ExtractionReport`，包含：
- 使用的 template 名称和版本
- 环境上下文（OS/模型版本）
- 提取耗时
- 置信度指标

---

## §8 Strategy Presets 自动切换

### 8.1 七种预设

`solidify.js` 引用的 `strategy.js` 提供了 7 种演化策略预设，通过 `EVOLVE_STRATEGY` 环境变量或自动检测切换：

| 策略 | repair | optimize | innovate | 使用场景 |
|------|--------|----------|----------|---------|
| **balanced** | 20% | 30% | 50% | 正常运营 |
| **innovate** | 5% | 15% | 80% | 系统稳定，最大化新能力 |
| **harden** | 40% | 40% | 20% | 大变更后，稳定优先 |
| **repair-only** | 80% | 20% | 0% | 紧急修复 |
| **early-stabilize** | 60% | 25% | 15% | 前5轮，稳定优先 |
| **steady-state** | 60% | 30% | 10% | 演化饱和，维持现有能力 |
| **auto** | — | — | — | 自适应（基于cycle数/saturation信号）|

### 8.2 自动检测规则

```javascript
// cycle ≤ 5 → early-stabilize（系统初期）
if (cycleCount > 0 && cycleCount <= 5) return 'early-stabilize';

// saturation 信号 → steady-state
if (signals.includes('force_steady_state')) return 'steady-state';
if (signals.includes('evolution_saturation')) return 'steady-state';

// 无信号且 cycle > 5 → balanced
return 'balanced';
```

### 8.3 CE 借鉴路径

**P1**：BlueCortexCE 的"模式"（Mode）可以借鉴策略预设思想：
- **探索模式**（explore）：高创新容忍，广泛 embedding 搜索
- **维护模式**（maintain）：低变更，只在明确失败时触发更新
- **修复模式**（repair）：优先使用已知可靠的 context

---

## §9 完整 Solidify 事件结构

最终写入 `events.jsonl` 的 EvolutionEvent 包含所有元数据：

```javascript
{
  type: 'EvolutionEvent',
  schema_version: '1.6.0',
  id: 'evt_1746353400000',
  parent: 'evt_1746353300000',        // 父事件链
  intent: 'repair',                   // 意图类别
  signals: ['log_error', 'protocol_drift'],
  genes_used: ['gene_tool_validation_v2'],
  mutation_id: 'mut_xxx',
  personality_state: { rigor: 0.7, creativity: 0.5, ... },
  blast_radius: { files: 3, lines: 47 },
  outcome: { status: 'success', score: 0.85 },
  capsule_id: 'capsule_1746353400000',
  gene_library_version: 'glib_a1b2c3d4e5f6...',  // genes.json SHA256 前16位
  env_fingerprint: { platform, arch, node_version, cwd_hash, ... },
  validation_report_id: 'vr_1746353400000',
  execution_trace: { /* 脱敏执行轨迹 */ },
  meta: {
    process_scores: {                 // §1 PRM 评分详情
      signal_quality: 0.70,
      gene_selection: 0.90,
      mutation_quality: 0.80,
      blast_control: 1.00,
      constraint_compliance: 1.00,
      validation_pass_rate: 0.50,     // 部分验证失败
      protocol_compliance: 1.00,
      canary_health: 1.00,
      composite: 0.85,
    },
    soft_failure: {                  // 失败时的学习信号
      learning_signals: [...],
      retryable: true,
      class: 'validation_partial',
      mode: 'soft',
    },
  },
  asset_id: 'sha256:...',            // 内容寻址
}
```

---

## §10 CE 行动项汇总

| 优先级 | 行动项 | 对应机制 | 预期收益 |
|--------|--------|---------|---------|
| **P0** | 引入多维 Observation 质量评分（signal/context/embedding） | §1 PRM | 更细粒度的记忆质量控制 |
| **P0** | Observation 附加 ExtractionReport（template版本/环境/耗时/置信度） | §7 ValidationReport | 可观测性 + 跨环境对比 |
| **P0** | 在 SessionSummary 中引入 12KB/30条容量约束 | §6 Narrative Memory | 防止 summary 无限膨胀 |
| **P1** | Extraction 失败时捕获 prompt+response+context snapshot（CE版FailedCapsule） | §4 FailedCapsule | 离线回放 + prompt 优化 |
| **P1** | 引入环境上下文标记（OS/语言栈），动态调整 Observation 相关性 | §2 Epigenetic Marks | 跨项目泛化能力提升 |
| **P1** | Mode 能力边界：连续失败时自动降级使用频率 | §3 Gene Learning | 自我诊断自我调整 |
| **P1** | 引入 Mode 预设（explore/maintain/repair） | §8 Strategy Presets | 自适应演化策略 |
| **P2** | extraction 误报/漏报 Hub 发布（anti-pattern publish） | §5 Anti-pattern Publish | 行业级失败模式知识库 |

---

## §11 文件级小结

| 文件 | 行数 | 核心职责 |
|------|------|---------|
| `solidify.js` | 1344 | 主固化管线：基因选择→约束检查→验证→评分→持久化→发布 |
| `validationReport.js` | 55 | ValidationReport 标准格式构建与验证 |
| `narrativeMemory.js` | 94 | 有界叙事记忆（30条/12KB）+ narrative 摘要加载 |
| `strategy.js` | 131 | 7种演化预设 + cycle-based 自动切换 |
| `memoryGraphAdapter.js` | 203 | local/remote memory graph 适配器 + graceful fallback |

---

_文档维护：doc 82 v1 | 2026-05-04 | 基于 v1.47.0 `solidify.js` L136–L1150 源码_
