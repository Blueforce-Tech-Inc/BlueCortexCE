# `solidify.js` 核心深度分析（1344行）

**Doc #117** | cron 2026-05-06 07:53 | 来源：`src/gep/solidify.js` v1.47.0

---

## 1. 架构定位

`solidify()` 是 Evolver 基因进化管线的**最终阶段**——在 mutation 之后执行验证、持久化、回滚和发布。

核心职责：
1. **验证**：policyCheck + canary + LLM review 三层门禁
2. **评分**：PRM 启发的 8 维过程评分
3. **持久化**：EvolutionEvent + Capsule + FailedCapsule + Gene 更新
4. **回滚**：失败时恢复 git 状态
5. **发布**：自动发布成功 capsule 到 Hub + 失败 anti-pattern 到 Hub

---

## 2. 8维 PRM 启发式过程评分

`computeProcessScores()` 是 Evolver 的**过程质量评分系统**——不是二元 success/fail，而是 8 个独立维度的加权复合：

```javascript
const weights = {
  signal:        0.05,   // 信号质量
  selection:     0.10,   // 基因选择质量
  mutation:      0.05,   // 变异格式质量
  blast:         0.15,   // 爆炸半径控制
  constraint:    0.25,   // 约束合规（最高权重）
  validation:    0.25,   // 验证通过率（最高权重）
  protocol:      0.10,   // 协议合规
  canary:        0.05,   // canary 健康
};
```

### 各维度评分逻辑

| 维度 | 满分条件 | 失分条件 |
|------|---------|---------|
| **signal** | 信号数量越多（n×0.1，cap 1.0） | 无信号→0.5 |
| **selection** | 知名 gene（非 auto_）→0.9 | 无 gene→0.3 |
| **mutation** | 有 rationale+category+low risk→0.9 | 无 mutation→0.3 |
| **blast** | 文件数≤maxFiles×0.5→1.0 | 超限→0.2；估计偏差>3x→×0.5 |
| **constraint** | 0 violations→1.0 | 每次 violation -0.25 |
| **validation** | 全部通过→1.0 | 空验证命令→0.5（应至少有一条）|
| **protocol** | 0 violations→1.0 | 每次 violation -0.3 |
| **canary** | 通过→1.0 | 失败→0 |

### BlueCortexCE 借鉴价值：P1

**当前状态**：BlueCortexCE 的 Outcome 只有二元 status（success/failed），缺少过程质量分解。

**提案**：
```java
// ObservationEntity 新增字段
processScores: {
  signal_quality: 0.8,        // 信号丰富度
  selection_quality: 0.7,      // 选择质量（对应 search 服务质量）
  scope_control: 0.6,         // 范围控制（对应 token budget 管理）
  constraint_compliance: 1.0, // 约束合规
  semantic_quality: 0.9,      // 语义一致性
  composite: 0.82
}
```

**使用场景**：
- 搜索结果质量评估（而非只看"有没有结果"）
- 上下文注入效果后验
- 跨 session 质量趋势追踪

---

## 3. 三层验证门禁

### Layer 1: PolicyCheck（blast radius + constraints）

```javascript
const constraintCheck = checkConstraints({ gene, blast, blastRadiusEstimate, repoRoot });
```

来自 `policyCheck.js`（已分析 doc 42）：
- `isConstraintCountedPath`：路径匹配决策树
- `computeBlastRadius`：git numstat + untracked 行数统计
- `classifyBlastSeverity`：5级分类
- `detectDestructiveChanges`：关键依赖破坏检测

### Layer 2: Canary（进程级健康检查）

```javascript
const canary = runCanaryCheck({ repoRoot, timeoutMs: 30000 });
if (!canary.ok && !canary.skipped) {
  constraintCheck.violations.push(`canary_failed: index.js cannot load...`);
}
```

**核心逻辑**：在独立子进程中加载 `index.js`，验证入口文件可执行。这捕捉基因验证可能遗漏的**运行时崩溃**。

### Layer 3: LLM Review（语义级评审）

```javascript
if (constraintCheck.ok && validation.ok && isLlmReviewEnabled()) {
  const reviewDiff = captureDiffSnapshot(repoRoot);
  llmReviewResult = runLlmReview({ diff, gene, signals, mutation });
  if (llmReviewResult.approved === false) {
    constraintCheck.violations.push('llm_review_rejected...');
  }
}
```

来自 `llmReview.js`（已分析 doc 45）：规则门禁+语义评审双层。

### BlueCortexCE 借鉴价值：P1

**当前状态**：BlueCortexCE 的 ContextService.generateContext 没有对"生成的上下文"本身做质量验证。

**提案**：为 `/api/context/generate` 增加三层验证：
1. **结构验证**：token 上限、字段完整性
2. **运行时验证**：通过测试 prompt 验证上下文有效性
3. **语义验证**：LLM 评审上下文连贯性

---

## 4. FailedCapsule：失败信息零丢失设计

**关键设计原则**：回滚（rollback）会清除 git 工作区变更，因此在回滚**之前**必须捕获失败信息。

```javascript
// 在 rollback 之前执行
if (!dryRun && !success) {
  const diffSnapshot = captureDiffSnapshot(repoRoot);
  const failedCapsule = {
    type: 'Capsule',
    outcome: { status: 'failed', score },
    diff_snapshot: diffSnapshot,          // 保存变更内容
    failure_reason: failureReason,
    learning_signals: softFailureLearningSignals,
    constraint_violations: constraintCheck.violations,
  };
  appendFailedCapsule(failedCapsule);
}
```

### BlueCortexCE 借鉴价值：P1

**当前状态**：BlueCortexCE 失败时 ObservationEntity 没有保存"失败的上下文内容"。

**提案**：
```java
// ObservationEntity 新增
failedContext: {        // JSONB
  prompt: "...",
  attemptedContext: "...",
  failureReason: "token_limit_exceeded",
  partialResult: "..."
}
```

---

## 5. Rollback 双策略

```javascript
if (!success && rollbackOnFailure) {
  rollbackTracked(repoRoot);  // 恢复 tracked 文件
  // 仅在有 baseline 时清理 untracked
  if (lastRun && Array.isArray(lastRun.baseline_untracked)) {
    rollbackNewUntrackedFiles({ repoRoot, baselineUntracked });
  }
}
```

**安全机制**：`baseline_untracked` 记录进化前的 untracked 文件列表，确保只删除 AI 生成的 untracked 文件，不误删预先存在的文件。

### BlueCortexCE 借鉴价值：P2

类似的机制可用于"当上下文注入失败时，恢复到上一个健康状态"。

---

## 6. 基因表观遗传（Epigenetic Marks）

```javascript
adaptGeneFromLearning({
  gene: geneUsed,
  outcomeStatus,        // 'success' | 'failed'
  learningSignals,      // 成功→原始信号；失败→softFailureLearningSignals
  failureMode,
});
applyEpigeneticMarks(geneUsed, envFp, outcomeStatus);
upsertGene(geneUsed);
```

**`adaptGeneFromLearning`**：
- 成功时：追加 `signals_match`（problem:/area:标签）和 `learning_history`
- 失败时：追加 `anti_patterns`（失败模式 + timestamp）

**`applyEpigeneticMarks`**：基于环境指纹（envFingerprint）为基因添加环境特定标记。

### BlueCortexCE 借鉴价值：P2

ObservationEntity 可以记录类似的环境感知元数据：
```java
epigeneticMarks: {
  environment: "java-21-macos",
  confidence_boost: 0.15,
  reason: "same_platform_success"
}
```

---

## 7. 多通道自动发布

### 成功发布（Search-First Evolution）

```javascript
if (capsule && capsule.a2a.eligible_to_broadcast) {
  const bundle = buildPublishBundle({ gene, capsule, event, chainId });
  httpTransportSend(bundle, { hubUrl });  // 异步，不阻塞主流程
}
```

**Eligibility 条件**：
- blast radius 安全
- score ≥ `BROADCAST_SCORE_THRESHOLD`
- success_streak ≥ `BROADCAST_SUCCESS_STREAK`

**发布前 leak check**：扫描 payload 敏感数据（`fullLeakCheck`）。

### 失败 Anti-pattern 发布（opt-in）

```javascript
if (publishAntiPatterns && hasHighInfoFailure) {
  // constraint violations 或 canary failures 才发布
  apGene.anti_pattern = true;
  apGene.failure_reason = buildFailureReason(...);
  buildPublishBundle({ gene: apGene, capsule: apCapsule });
}
```

### BlueCortexCE 借鉴价值：P3

CE 的"观察发布"（未来可能的 Hub 集成）可参考：
- 成功观察：满足质量阈值后自动发布
- 失败观察：标记为 anti-pattern 供他人避免
- 零信息丢失：failed context 完整保留

---

## 8. LessonL 机制

**轻量级失败知识化**：不是发布完整 capsule，而是将失败原因嵌入 `EvolutionEvent.failure_reason` 字段。Hub 的 `solicitLesson()` 钩子从 event 中提取 lesson。

```javascript
// 即使不发布 event，也记录 failure_reason
event.failure_reason = failureContent;
event.summary = 'Failed: ' + geneId + ' on signals [...] - ' + failureReason;
```

### BlueCortexCE 借鉴价值：P2

**StructuredExtractionService** 的结果（无论成功/失败）都应记录 `failure_reason` 字段，供后续 session 复用：
```java
extractionResult: {
  success: false,
  failure_reason: "template_missing_field: user_preference.color",
  partial_result: { ... },
  lesson: "下次提取 color 时，检查模板 schema 字段是否存在"
}
```

---

## 9. Hub Task 自动完成

```javascript
if (lastRun && lastRun.active_task_id) {
  // 原子 claim + complete（deferred 模式）
  claimAndCompleteWorkerTask(taskId, resultAssetId);
  // 或 legacy 已 claim 模式
  completeWorkerTask(assignmentId, resultAssetId);
}
```

### BlueCortexCE 借鉴价值：P3

如果 CE 未来支持 Hub 集成，此机制可直接迁移。

---

## 10. ValidationReport 标准化

```javascript
const validationReport = buildValidationReport({
  geneId, commands, results, envFp,
  startedAt: validation.startedAt,
  finishedAt: validation.finishedAt,
});
// 写入 events.jsonl
appendEventJsonl(validationReport);
```

**来自 `validationReport.js`**（doc 97）：标准化的 `ValidationReport` 类型，含 env_fingerprint + stdout/stderr。

### BlueCortexCE 现状：P0 已完成

CE 的 `ValidationReport` 已实现（doc 97），与 Evolver 对齐。

---

## 总结：solidify.js 的 5 大可借鉴设计

| 优先级 | 设计 | CE 当前状态 | 提案 |
|--------|------|-----------|------|
| **P1** | 8维 PRM 过程评分 | 无，只有二元 outcome | ObservationEntity.processScores |
| **P1** | 三层验证门禁 | 无上下文质量验证 | contextGenerate 三层验证 |
| **P1** | FailedCapsule 零丢失 | 无失败上下文保存 | ObservationEntity.failedContext |
| **P2** | 表观遗传 Marks | 无环境感知调整 | epigeneticMarks 字段 |
| **P2** | LessonL 轻量知识化 | 无结构化 failure_reason | extractionResult.failure_reason |

---

## 相关已分析 Doc

- Doc 42: `policyCheck.js`（blast radius + constraints）
- Doc 45: `llmReview.js`（LLM 语义评审）
- Doc 97: `validationReport.js`（ValidationReport 标准化）
- Doc 99: `evolve.js` 安全系统（solidify 调用的 gitOps 来自此）
- Doc 102: `learningSignals.js`（softFailureLearningSignals 来源）
