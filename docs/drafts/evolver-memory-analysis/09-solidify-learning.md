# 09 — Solidify 机制与基因学习

## 9.1 整体定位

`solidify.js`（1344 行）是整个进化循环的**终点**——当一个 cycle 执行完毕（基因已选、变更已应用），`solidify()` 负责：
1. 验证变更质量（约束检查 + 验证命令 + 金丝雀测试）
2. 计算 outcome score（多阶段 PRM 评分）
3. 将学习反馈写回 gene（adaptGeneFromLearning）
4. 记录 EvolutionEvent 到 JSONL 图
5. 必要时创建新 Capsule（成功经验封装）

```
基因选择 → Mutation → Git Apply → Solidify → Outcome Event
                                                           │
                                              ┌────────────┴────────────┐
                                              │  adaptGeneFromLearning   │
                                              │  → learning_history     │
                                              │  → signals_match 扩展    │
                                              │  → anti_patterns 记录   │
                                              │  → epigenetic_marks     │
                                              └─────────────────────────┘
```

## 9.2 主要函数：solidify()

`solidify()` 是入口，从 `evolution_solidify_state.json` 读取上次 cycle 的上下文：

```javascript
function solidify({ intent, summary, dryRun = false, rollbackOnFailure = true } = {}) {
  // 1. 读取上次 cycle 状态
  const state = readStateForSolidify();
  const lastRun = state.last_run;  // selected_gene_id, signals, mutation, personality_state...

  // 2. 加载基因库
  const genes = loadGenes();
  const selectedGene = genes.find(g => g.id === lastRun.selected_gene_id);

  // 3. 提取信号（兜底：state 无则重新提取）
  const signals = lastRun.signals || extractSignals(readRecentSessionInputs());
  const signalKey = computeSignalKey(signals);

  // 4. 计算 blast radius（变更范围）
  const blast = computeBlastRadius({ repoRoot, baselineUntracked: lastRun.baseline_untracked });
  const constraintCheck = checkConstraints({ gene: geneUsed, blast, blastRadiusEstimate, repoRoot });

  // 5. 金丝雀检查：子进程加载 index.js
  const canary = runCanaryCheck({ repoRoot, timeoutMs: 30000 });

  // 6. 运行基因定义的验证命令
  const validation = runValidations(geneUsed, { repoRoot, timeoutMs: 180000 });

  // 7. 可选 LLM Review
  if (isLlmReviewEnabled()) {
    llmReviewResult = runLlmReview({ diff: captureDiffSnapshot(repoRoot), gene: geneUsed });
    if (!llmReviewResult.approved) constraintCheck.ok = false;
  }

  // 8. 多阶段 PRM 评分
  const processScores = computeProcessScores({ constraintCheck, validation, canary, blast, ... });
  const score = clamp01(processScores.composite);

  // 9. 失败分类
  const failureMode = !success ? classifyFailureMode({ constraintViolations, protocolViolations, validation, canary }) : { mode: 'none' };

  // 10. 构建 EvolutionEvent
  const event = {
    type: 'EvolutionEvent',
    id: buildEventId(ts),
    // 完整记录信号/基因/mutation/outcome/score
    ...
  };

  // 11. 追加到 JSONL 图
  appendEventJsonl(event);

  // 12. 适配基因学习
  const adaptedGene = adaptGeneFromLearning({
    gene: geneUsed,
    outcomeStatus,
    learningSignals: outcomeStatus === 'failed' ? softFailureLearningSignals : successSignals,
    failureMode,
  });

  // 13. 表观遗传标记（环境印记）
  const envFp = captureEnvFingerprint();
  applyEpigeneticMarks(adaptedGene, envFp, outcomeStatus);

  // 14. 写入基因库
  upsertGene(adaptedGene);

  // 15. 封装成功经验为 Capsule
  if (success && capsuleId) {
    const capsule = buildCapsule({ id: capsuleId, gene: adaptedGene, signals, blast, score, intent });
    upsertCapsule(capsule);
  }

  // 16. 回滚失败变更
  if (!success && rollbackOnFailure) {
    rollbackTracked(repoRoot);
    rollbackNewUntrackedFiles(repoRoot, lastRun.baseline_untracked);
  }

  // 17. 记录叙事
  recordNarrative({ gene: adaptedGene, signals, mutation, outcome, blast, capsule });

  return { ok: success, status: outcomeStatus, score, event, processScores };
}
```

## 9.3 Gene 自适应学习

### 9.3.1 adaptGeneFromLearning()

每次 outcome 后，基因会**积累经验**：

```javascript
function adaptGeneFromLearning({ gene, outcomeStatus, learningSignals, failureMode }) {
  if (!gene) return gene;

  // 成功时：扩展 signals_match（问题类型标签 + 区域标签）
  if (outcomeStatus === 'success') {
    for (const sig of learningSignals) {
      if (!seenSignal.has(sig) && (sig.startsWith('problem:') || sig.startsWith('area:'))) {
        gene.signals_match.push(sig);
        seenSignal.add(sig);
      }
    }
  }

  // 追加学习历史（最多保留 20 条）
  gene.learning_history.push({
    at: nowIso(),
    outcome: outcomeStatus,
    mode: failureMode.mode,         // 'hard' | 'soft' | 'none'
    reason_class: failureMode.reasonClass,
    retryable: !!failureMode.retryable,
    learning_signals: learningSignals.slice(0, 12),
  });

  // 失败时：记录 anti_pattern（最多保留 12 条）
  if (outcomeStatus === 'failed') {
    gene.anti_patterns.push({
      at: nowIso(),
      mode: failureMode.mode,
      reason_class: failureMode.reasonClass,
      learning_signals: learningSignals.slice(0, 8),
    });
  }

  return gene;
}
```

**学习历史的作用**（selector.js 中的 `scoreGeneLearning()`）：
```javascript
// 成功 → +0.12 奖励
// 硬失败 → -0.22 惩罚（mode === 'hard'）
// 软失败 → -0.08 惩罚（mode === 'soft'）
boost += entry.outcome === 'success' ? 0.12
  : entry.mode === 'hard' ? -0.22
  : -0.08;
```

### 9.3.2 表观遗传标记（Epigenetic Marks）

生物启发的环境适应机制。基因在不同环境中表现不同，epigenetic marks 记录这些环境印记：

```javascript
function applyEpigeneticMarks(gene, envFingerprint, outcomeStatus) {
  const envContext = [platform, arch, nodeVersion].join('/') || 'unknown';

  if (outcomeStatus === 'success') {
    if (mark exists) {
      cur.boost = Math.min(0.5, cur.boost + 0.05);  // 强化
    } else {
      gene.epigenetic_marks.push({ context: envContext, boost: 0.1, reason: 'success_in_environment' });
    }
  } else if (outcomeStatus === 'failed') {
    if (mark exists) {
      cur.boost = Math.max(-0.5, cur.boost - 0.1);  // 抑制
    } else {
      gene.epigenetic_marks.push({ context: envContext, boost: -0.1, reason: 'failure_in_environment' });
    }
  }

  // 衰减：保留最近 90 天内、最多 10 条标记
  gene.epigenetic_marks = gene.epigenetic_marks
    .filter(m => new Date(m.created_at).getTime() > cutoff)
    .slice(-10);
}
```

**用途**：selector.js 在评分时会应用 epigenetic boost：
```javascript
boost += getEpigeneticBoostLocal(gene, envFingerprint);
```

## 9.4 PRM 启发式多阶段评分

`computeProcessScores()` 将 outcome 分解为 8 个维度，替代简单的成功/失败二元判断：

| 维度 | 权重 | 评分逻辑 |
|------|------|---------|
| `signal_quality` | 5% | 信号数量：0→0.4, n→min(1, 0.4+n*0.1) |
| `gene_selection` | 10% | 无基因→0.3, auto→0.7, 已有基因→0.9 |
| `mutation_quality` | 5% | 无mutation→0.3, 完整→0.8, 低风险→0.9, 高风险→0.6 |
| `blast_control` | 15% | 0文件→0.4, ≤50%上限→1.0, 正常→0.7, 超过→0.2 |
| `constraint_compliance` | 25% | 每违规-0.25，最低0 |
| `validation_pass_rate` | 25% | 通过数/总数（无验证命令→0.5 penalty） |
| `protocol_compliance` | 10% | 每违规-0.3，最低0 |
| `canary_health` | 5% | 金丝雀失败→0，否则1 |

**最终得分**：`composite = Σ(score_i × weight_i)`

### 9.4.1 金丝雀检查（Canary Check）

在验证命令之外，额外用子进程加载 index.js：
```javascript
function runCanaryCheck({ repoRoot, timeoutMs = 30000 }) {
  // spawn child: node -e "require(repoRoot + '/index.js')"
  // 超时或报错 → canary.ok = false
}
```

### 9.4.2 LLM Review（可选）

当 `EVOLVER_LLM_REVIEW=true` 时，变更 diff 会被提交给 LLM 评审：
```javascript
if (llmReviewResult.approved === false) {
  constraintCheck.ok = false;
  constraintCheck.violations.push('llm_review_rejected: ' + summary);
}
```

## 9.5 自动基因创建（Auto-Gene）

当没有基因匹配当前信号时，`ensureGene()` 会创建 auto-gene：

```javascript
function buildAutoGene({ signals, intent }) {
  const signalKey = computeSignalKey(signals);
  return {
    type: 'Gene',
    id: `gene_auto_${stableHash(signalKey)}`,
    category: intent || inferCategoryFromSignals(signals),
    signals_match: signals.slice(0, 8),
    preconditions: [`signals_key == ${signalKey}`],
    strategy: [
      'Extract structured signals from logs and user instructions',
      'Select an existing Gene by signals match (no improvisation)',
      'Estimate blast radius before editing',
      'Apply smallest reversible patch',
      'Validate; rollback on failure',
      'Solidify knowledge: append EvolutionEvent, update Gene/Capsule store',
    ],
    constraints: {
      max_files: 12,
      forbidden_paths: ['.git', 'node_modules', ...],
    },
    validation: [
      'node scripts/validate-modules.js ...',
      'node scripts/validate-suite.js',
    ],
    epigenetic_marks: [],
  };
}
```

## 9.6 与 Claude-Mem 的类比

| EvoMap solidify | Claude-Mem 对应机制 |
|----------------|-------------------|
| `adaptGeneFromLearning()` | SessionEnd hook → Observation/Summary |
| `learning_history` | Session summary + observation records |
| `anti_patterns` | 无直接对应 |
| `epigenetic_marks` | 无直接对应（环境感知的评分调整） |
| `auto_gene` | 无直接对应（Claude-Mem 不自动创建新的 context 模板） |
| `computeProcessScores()` | 无直接对应（Claude-Mem 依赖向量相似度） |
| `Capsule` | 无直接对应 |
| `canary check` | 健康检查端点 |
| `llm_review` | 无直接对应 |

---

_Next: [10-skill-distillation.md](./10-skill-distillation.md) — Skill Distiller 管道_
