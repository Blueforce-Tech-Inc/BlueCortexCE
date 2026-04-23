# Solidify Pipeline: End-to-End Deep Dive

**Source**: `src/gep/solidify.js` (v1.47.0, ~900 lines), `src/gep/contentHash.js`, `src/gep/validationReport.js`, `src/gep/executionTrace.js`, `src/canary.js`
**Date**: 2026-04-23
**Purpose**: Document the complete solidify (固化) pipeline — the evolution commit mechanism — as a cohesive unit. While individual components are covered in docs 25 (PRM/epigenetic), 26 (runtime orchestration), and 27 (ops/canary), this doc provides the **end-to-end flow** that ties them together.

## 1. Pipeline Overview

The solidify pipeline is the **single bottleneck** through which all evolution outcomes pass. It transforms a prepared mutation into either a committed success (Capsule) or a rolled-back failure (FailedCapsule + learning signals). Every step is a gate: failure at any point triggers rollback.

```
readState → loadGene → ensureGene → computeBlastRadius
  → checkConstraints → detectDestructiveChanges
  → runValidations → runCanaryCheck → [optional: LLM Review]
  → computeProcessScores (PRM)
  → if success: create Capsule + epigenetic marks + auto-publish
  → if failure: create FailedCapsule + rollback + learning signals
  → recordNarrative + updatePersonalityStats
  → auto-complete Hub task + Hub review
```

## 2. Phase-by-Phase Walkthrough

### 2.1 State Recovery & Gene Resolution

```javascript
// solidify.js: readStateForSolidify()
const state = readStateForSolidify();  // reads evolution_solidify_state.json
const lastRun = state.last_run;        // previous cycle's prepared state
const genes = loadGenes();             // from genes.json
const selectedGene = genes.find(g => g.id === geneId);
```

**Key design**: Solidify doesn't select a gene — that happens in `evolve.js` during the prepare phase. Solidify **reads** the pre-selected gene from state. This separation of concerns means solidify is a pure "commit" step.

**`ensureGene` fallback**: If no gene was pre-selected (edge case), `ensureGene()` either re-selects from existing genes or creates an auto-generated `gene_auto_*` as last resort.

### 2.2 Blast Radius Computation

```javascript
const blast = computeBlastRadius({
  repoRoot,
  baselineUntracked: lastRun.baseline_untracked || [],
});
```

Computes `files` (count of changed files) and `lines` (total churn). Uses `git diff` for tracked changes and compares against a pre-mutation baseline for untracked files.

**Diagnostics on elevated severity**: When blast severity exceeds "approaching_limit", logs a breakdown of which file categories are affected and compares actual vs estimated blast radius.

### 2.3 Constraint Checking

Multi-layered constraint verification:

| Layer | What It Checks | Failure Mode |
|-------|---------------|--------------|
| `max_files` | Changed files ≤ gene constraint | Soft violation |
| `forbidden_paths` | No changes to `.git`, `node_modules`, etc. | Hard violation |
| `BLAST_RADIUS_HARD_CAP` | Files ≤ 50, Lines ≤ 2000 | Hard cap breach |
| `CRITICAL_PROTECTED_*` | Core files (package.json, index.js) | Critical modification |
| `detectDestructiveChanges` | Deletion of non-generated files | Destructive change |

**Design principle**: Constraints are defined per-Gene, not globally. A "repair" gene might have `max_files: 12` while an "innovate" gene allows `max_files: 20`. This gene-level constraint design means safety is **inherited** from the gene, not bolted on.

### 2.4 Validation Execution

```javascript
validation = runValidations(geneUsed, { repoRoot, timeoutMs: 180000 });
```

Runs each command in `gene.validation[]` with:
- `cwd` set to repo root
- 180-second timeout per command
- Retry logic (up to `SOLIDIFY_MAX_RETRIES` with `SOLIDIFY_RETRY_INTERVAL_MS` delay)

**Validation commands are gene-defined**: Each Gene carries its own validation array. Default genes use `node scripts/validate-modules.js ./src/gep/...` and `node scripts/validate-suite.js`.

**Empty validation array penalty**: PRM gives a 0.5 score (not 0) for empty validation — genes *should* define at least one validation command.

### 2.5 Canary Safety Net

```javascript
// canary.js — runs in forked child process
try {
  require('../index.js');
  process.exit(0);  // safe
} catch (e) {
  process.exit(1);  // broken
}
```

**Purpose**: Last-resort safety net that catches broken entry points that gene validations might miss. If `index.js` can't load (syntax error, missing require), canary catches it BEFORE the daemon restarts with broken code.

**30-second timeout** (`CANARY_TIMEOUT_MS`). Canary failure adds `canary_failed:` to constraint violations, making it a hard failure.

### 2.6 Optional LLM Review

When `EVOLVER_LLM_REVIEW=true` and all prior checks pass:

```javascript
llmReviewResult = runLlmReview({ diff, gene, signals, mutation });
if (llmReviewResult.approved === false) {
  constraintCheck.violations.push('llm_review_rejected: ...');
}
```

**Key insight**: LLM review is the **last** gate, not the first. It only runs when constraints, validation, protocol, and canary all pass. This minimizes LLM cost by only calling it on changes that are already structurally sound.

### 2.7 PRM Multi-Step Scoring (Process Reward Model)

The most important innovation in the solidify pipeline. Instead of binary pass/fail, each phase is scored independently:

| Phase | Weight | Scoring Logic |
|-------|--------|---------------|
| `signal_quality` | 0.05 | 0.4 + count * 0.1 (capped at 1.0) |
| `gene_selection` | 0.10 | 0.3 (none) → 0.7 (auto) → 0.9 (existing) |
| `mutation_quality` | 0.05 | 0.3 (none) → 0.8 (has rationale) → 0.9 (low risk) |
| `blast_control` | 0.15 | 1.0 (≤50% of max) → 0.7 (≤max) → 0.2 (exceeded) |
| `constraint_compliance` | 0.25 | 1.0 - violations * 0.25 |
| `validation_pass_rate` | 0.25 | passed / total (0.5 penalty for empty) |
| `protocol_compliance` | 0.10 | 1.0 - violations * 0.3 |
| `canary_health` | 0.05 | 1.0 or 0 |

**Composite formula**: `Σ(phase_score × weight)` → clamped to [0, 1]

**Design insight**: `constraint_compliance` and `validation_pass_rate` together carry 50% of the weight. This means structural correctness matters more than signal quality or gene selection — a sound change with poor signals scores better than a risky change with perfect signals.

**Blast estimate comparison**: If an estimate was provided before mutation, the actual blast is compared. If actual > 3× estimate, `blast_control` is halved. This penalizes mutations that wildly exceed their predicted scope.

### 2.8 Outcome Determination

```javascript
const success = constraintCheck.ok && validation.ok && protocolViolations.length === 0;
```

Three-way AND: all must pass. Canary failure injects into `constraintCheck.violations`, so it's covered. LLM rejection also injects into violations.

### 2.9 Success Path: Capsule Creation

On success, a **Capsule** is created — the reusable knowledge artifact:

```javascript
capsule = {
  type: 'Capsule',
  id: capsuleId,
  trigger: signals,                    // what triggered this
  gene: geneUsed.id,                   // which gene was used
  summary: summary || autoSummary,     // human-readable description
  confidence: score,                   // PRM composite score
  blast_radius: { files, lines },
  outcome: { status: 'success', score },
  success_streak: 1,                   // incremented on reuse
  success_reason: buildSuccessReason(...),
  gene_library_version: geneLibVersion,// hash of genes.json
  env_fingerprint: envFp,              // platform/node/arch
  source_type: 'generated' | 'reused' | 'reference',
  a2a: { eligible_to_broadcast: false },
  content: capsuleContent,             // truncated summary
  diff: diffSnapshot,                  // actual changes
  strategy: gene.strategy,             // applied strategy steps
};
```

**Success streak**: Computed by counting prior successful events with the same capsule ID. Streak ≥ 2 + score ≥ 0.7 + safe blast radius = `eligible_to_broadcast` to Hub.

**Capsule content**: Built from intent + gene + signals + strategy + blast + rationale + score. Truncated at 8000 chars (`CAPSULE_CONTENT_MAX_CHARS`).

### 2.10 Epigenetic Marks

Applied to the Gene (not the Capsule) based on outcome + environment:

```javascript
// Success: reinforce mark for this environment
gene.epigenetic_marks[idx].boost = min(0.5, boost + 0.05);

// Failure: suppress mark
gene.epigenetic_marks[idx].boost = max(-0.5, boost - 0.1);
```

**Environment context**: `platform/arch/nodeVersion` string. A gene that succeeds on `darwin/arm64/v22` gets a positive mark for that environment.

**Decay**: Marks older than 90 days are pruned. Max 10 marks per gene. This prevents unbounded growth while allowing long-term environmental learning.

**`getEpigeneticBoost(gene, envFingerprint)`**: Returns the boost for the current environment. Used by the selector to adjust gene scores.

### 2.11 Gene Learning History

```javascript
adaptGeneFromLearning({ gene, outcomeStatus, learningSignals, failureMode });
```

- On **success**: Adds `problem:*` and `area:*` signals to `gene.signals_match` (expanding the gene's reach)
- On **failure**: Appends to `gene.anti_patterns[]` (max 12 entries)
- Always: Appends to `gene.learning_history[]` (max 20 entries) with outcome, mode, reason_class, retryability

### 2.12 Failure Path: FailedCapsule + Rollback

On failure:

1. **FailedCapsule** is created with diff_snapshot, failure_reason, learning_signals, constraint_violations
2. **Rollback**: `rollbackTracked(repoRoot)` reverts git changes; `rollbackNewUntrackedFiles()` deletes AI-generated files (only if baseline exists — safety guard)
3. **Soft failure signals**: Generated from constraint violations + validation failures, fed back to learning

### 2.13 Auto-Publish to Hub

If capsule is `eligible_to_broadcast` and `sourceType !== 'reused'` and score ≥ `MIN_PUBLISH_SCORE` (0.78):

1. **Leak check**: `fullLeakCheck()` scans capsule + gene content for sensitive data
   - `strict` mode: blocks publish entirely
   - `warn` mode: logs but proceeds (sanitizePayload handles redaction)
2. **Bundle format**: Gene + Capsule + optional EvolutionEvent, sent via `httpTransportSend`
3. **Anti-pattern publish** (opt-in via `EVOLVER_PUBLISH_ANTI_PATTERNS=true`): Publishes high-information-value failures as anti-pattern assets

### 2.14 Narrative Recording

```javascript
recordNarrative({ gene, signals, mutation, outcome, blast, capsule });
```

Appends to `narrativeMemory.md` — a human-readable evolution journal. Truncated at `NARRATIVE_SUMMARY_MAX_CHARS` (3000).

### 2.15 Hub Task Completion

If the evolution was driven by a Hub task (`lastRun.active_task_id`):

- **Deferred claim mode**: `claimAndCompleteWorkerTask()` atomically claims + completes
- **Legacy path**: `completeWorkerTask()` for already-claimed assignments
- **Bounty path**: `completeTask()` via `/a2a/task/complete`

### 2.16 Hub Review

If the cycle reused a Hub asset (`reusedAssetId` + `sourceType=reused|reference`):

```javascript
submitHubReview({ reusedAssetId, sourceType, outcome, gene, signals, blast, constraintCheck });
```

Submits a usage-verified review back to the Hub, enabling the asset's effectiveness tracking.

## 3. Content-Addressable Asset System

### 3.1 Canonical JSON (`contentHash.js`)

```javascript
function canonicalize(obj) {
  // Sorted keys at all levels
  // Arrays preserve order
  // Non-finite numbers → null
  // undefined → null
}
```

**Purpose**: Deterministic serialization. Two nodes computing `canonicalize(obj)` get identical strings, enabling:
- **Deduplication**: Same content = same asset_id
- **Tamper detection**: Any modification changes the hash
- **Cross-node consistency**: Hub can verify asset integrity

### 3.2 Asset ID Computation

```javascript
function computeAssetId(obj) {
  const clean = { ...obj };
  delete clean.asset_id;  // exclude self-referential field
  const canonical = canonicalize(clean);
  return 'sha256:' + crypto.createHash('sha256').update(canonical).digest('hex');
}
```

**Schema version**: `SCHEMA_VERSION = '1.6.0'` — bump MINOR for additive fields, MAJOR for breaking changes.

### 3.3 Atomic Writes (`assetStore.js`)

```javascript
function writeJsonAtomic(filePath, obj) {
  const tmp = `${filePath}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(obj, null, 2) + '\n', 'utf8');
  fs.renameSync(tmp, filePath);  // atomic on POSIX
}
```

Write-to-temp-then-rename ensures readers never see partial writes.

### 3.4 Default Gene Library

Three built-in genes ship with evolver:

| Gene ID | Category | Signals | Purpose |
|---------|----------|---------|---------|
| `gene_gep_repair_from_errors` | repair | error, exception, failed, unstable | Fix runtime errors |
| `gene_gep_optimize_prompt_and_assets` | optimize | protocol, gep, prompt, audit, reusable | Improve evolution protocol |
| `gene_tool_integrity` | repair | tool_bypass | Prevent tool circumvention |

Each carries its own `constraints`, `validation[]`, and `strategy[]`.

## 4. ValidationReport Standardization

```javascript
{
  type: 'ValidationReport',
  id: 'vr_' + timestamp,
  gene_id: geneId,
  env_fingerprint: env,
  env_fingerprint_key: envFingerprintKey(env),
  commands: [{ command, ok, stdout (max 4000), stderr (max 4000) }],
  overall_ok: boolean,
  duration_ms: number,
  created_at: ISO,
  asset_id: 'sha256:...'
}
```

**Machine-readable**: External Hubs or Judges can consume this for automated assessment without parsing logs.

## 5. Execution Trace Desensitization

```javascript
// EVOLVER_TRACE_LEVEL: none | minimal | standard
{
  gene_id, mutation_category, signals_matched,
  files_changed_count, lines_added, lines_removed,
  validation_result, blast_radius,  // minimal level
  file_types, error_signatures, tool_chain,  // standard level
}
```

**Desensitization rules**:
- File paths → basename only (`src/utils/retry.js` → `retry.js`)
- Code content → never sent, only metrics
- Error messages → type signature only (`TypeError: x is not a function` → `TypeError`)
- Env vars, secrets, user data → stripped entirely

**Purpose**: Cross-agent experience sharing without leaking implementation details.

## 6. CE 借鉴要点

### 6.1 可借鉴的设计模式

| Evolver 模式 | CE 对应 | 优先级 |
|-------------|---------|--------|
| PRM 多步骤评分 | `process_scores` 字段加权到 `outcome.score` | P1 |
| Content-addressable ID | `sha256:` 前缀的 observation/summary ID | P1 |
| Atomic write (tmp+rename) | JSONL append 天然原子，但 JSON 文件需要 | P2 |
| FailedCapsule + learning signals | 失败观察 + `extractedData.error_sig_norm` | P1 |
| Epigenetic marks (环境感知) | 按 agent/platform 差异化检索权重 | P2 |
| Canary safety net | 后端启动健康检查 | P0 |
| Leak check before publish | 观察数据脱敏 | P0 |
| Execution trace (desensitized) | 可共享的抽象经验摘要 | P2 |
| Gene learning_history | 模板/策略的使用统计 | P2 |
| ValidationReport standardization | 标准化的健康/质量报告格式 | P2 |

### 6.2 反模式（不要借鉴）

- **Gene 作为可变状态**：CE 的观察/摘要应该是不可变的，学习通过新记录而非修改旧记录
- **Rollback on failure**：CE 是旁路记忆系统，不应修改用户代码，无需 rollback
- **Blast radius**：CE 不执行代码修改，此概念不适用

### 6.3 关键架构洞察

1. **Solidify 是唯一的 commit 路径**：所有进化结果都通过这一个函数。这保证了一致性和可审计性。CE 应该有类似的 "ingest 是唯一的写入路径"。

2. **PRM > Binary**：多步骤评分比二进制成功/失败提供更丰富的反馈信号。CE 的 `outcome.score` 应该类似地聚合多个维度。

3. **Content-addressable = 天然去重**：`sha256:hash` ID 使得同一内容不会被重复存储，无论它从哪个路径进来。

4. **Desensitized traces = 可共享经验**：Execution trace 的脱敏设计使得跨 agent 经验分享成为可能，而不会泄露敏感信息。

5. **Gene-level constraints > Global constraints**：每个 Gene 定义自己的安全边界，比全局约束更灵活。CE 可以让每个 extraction template 定义自己的验证规则。
