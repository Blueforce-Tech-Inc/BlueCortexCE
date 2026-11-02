# Extraction quality improvement plan

> **Purpose**: Track the prompt-contract, deterministic-validation and evaluation work that raises the
> quality of observations extracted from tool-use traces.
> **Updated by**: manual (agent-assisted implementation), appended after every completed change.
> **Update rule**: append to “Implementation record”; revise only pre-committed thresholds when a
> measured result proves the original threshold was impossible, and say so explicitly.

## 1. Current baseline (2026-11-02, 12 hand-written samples)

| Metric | LongCat | MiniMax |
|---|---|---|
| Contract pass rate | 92% | 83% |
| Skip accuracy (TP/FP/FN/TN) | 92% (2/1/0/9) | 83% (0/0/2/10) |
| Type accuracy | 100% | 90% |
| Title full-hit rate | 44% | 20% |
| Facts precision / recall | 54% / 94% | 65% / 90% |
| Concepts precision / recall | 57% / 83% | 52% / 80% |
| Format errors | 0 | 0 |

Known wrong answers:

- `test-timezone-regression` was skipped by LongCat although it is a legitimate regression test.
- MiniMax produced observations for both low-value traces (`skip-read-for-question`, `skip-file-listing`).
- MiniMax labelled a new regression test as `bugfix` instead of `feature`.
- Titles were paraphrased: identifiers such as `PaymentService`, `UserCache` and `computeIfAbsent`
  frequently disappeared, which is why the full-hit rate is low.
- Only 12 samples: no read-only/multi-step/no-conclusion/long-trace forms, so skip accuracy is
  measured on an unrepresentative slice.

## 2. Root-cause analysis

1. **The skip rule is a prose paragraph, not a checklist.** The prompt lists “simple file listings”
   and “repetitive operations” but never states that a pure-read turn with no code/config change must
   skip. Both providers therefore guessed.
2. **No “must record” list.** A regression test added to reproduce a bug looks like a “test-only”
   change; without an explicit rule, models label it `bugfix` (MiniMax) or skip it (LongCat).
3. **No title contract.** “Short title capturing the core action” invites paraphrase; identifiers
   are lost and retrieval degrades.
4. **No facts contract.** Models emit opinions and inferences (“should improve performance”) that
   cannot be verified from the trace.
5. **No deterministic guard.** Everything the model says is persisted, so one hallucinated fact
   survives into long-term memory.
6. **Evaluation is too small and keywords are brittle.** Twelve samples, exact-substring title
   matching, one run per sample, no per-type breakdown, no status/latency accounting.

## 3. Planned changes

### 3.1 Prompt contract (`init.txt`, `continuation.txt`)

- Explicit **skip checklist**: pure read/search to answer a question, exploratory directory listing,
  repeated confirmation, viewing command output only, any turn with no code/config/docs change.
- Explicit **must-record checklist**: bug fix, test add/change, refactor, config change, security
  fix, deployment change, and tests added to reproduce a reported issue.
- **Title contract**: verb + object + key identifier; class/method/file names copied verbatim;
  maximum length; one positive example.
- **Facts contract**: directly verifiable from the trace only; no speculation or feelings; maximum
  count; definitions and examples for bugfix vs feature/test-add.
- `<skip>` stays the only skip signal; `XmlParser` is not relaxed.
- Offline prompt-assertion tests with a scripted model: pure-read must skip, reproduction test must
  produce an observation, title identifiers stay verbatim.

### 3.2 Deterministic validation layer (no model call)

Applied after `XmlParser.parseObservation`, before persistence:

- A fact is kept only when its significant tokens are supported by the tool input/output text;
  unsupported facts are dropped.
- A duplicate memory in the same project scope (normalized title + type) is skipped; the existing
  row is never mutated (chosen over “merge” so a bad new extraction cannot rewrite good memory).
- Empty title, overlong title and “all facts dropped” are rejected with a recorded reason.
- Toggle: `claudemem.extraction.validation.enabled` (default `true`).
- No public API shape change.

### 3.3 Evaluation upgrade

- Title scoring: normalize case/punctuation/CamelCase, split identifiers, allow a small synonym
  table; per-sample normalized hit, aggregate rate.
- Facts/concepts: recall stays the primary signal, precision secondary; add trace-support rate.
- Golden set expanded to 60–100 samples covering bugfix / refactor / test-add / security /
  config / docs / deploy / skip variants plus short, long, multi-step and no-conclusion traces.
- Key samples repeated 3× to measure answer consistency.
- Per-type accuracy, skip confusion matrix, format errors, call count, latency distribution,
  status-code distribution, single-run and worst-of-3 views.
- Offline replay: recorded answers stored as fixtures; CI only replays and scores, never calls a
  provider. The manual runner keeps a configurable cap (default unlimited) and never retries a
  failed call.

## 4. Pre-committed acceptance thresholds

These are fixed before implementation and may only be raised, or re-based with written evidence.

| # | Criterion | Threshold |
|---|---|---|
| A1 | Format errors (non-skip, non-observation answer) | 0 |
| A2 | Skip accuracy on the expanded set | ≥ 95% |
| A3 | Missed must-record samples (expected observation, got skip) | 0 |
| A4 | Low-value samples falsely recorded | ≤ 1 per 20 skip samples |
| A5 | Facts accuracy (keyword-grounded precision) | ≥ 75% |
| A6 | Normalized title hit rate | ≥ 80% |
| A7 | Four-layer regression (`./scripts/verify-local.sh`) | ALL CHECKS PASSED, 0 skipped, no new failures |
| A8 | Deterministic validation removes unsupported facts and never rejects a fully supported observation | unit tests pass |
| A9 | Replaying recorded fixtures in CI reproduces the reported metrics | exact match |

Metric definitions:

- **Skip accuracy** = (skip TP + skip TN) / total samples.
- **Facts accuracy** = mean per-sample precision (share of produced facts that mention an expected
  keyword); **facts recall** = share of expected keywords present.
- **Normalized title hit** = all declared identifiers appear verbatim AND normalized keyword
  coverage ≥ 0.8 (synonyms allowed).
- **Format error** = neither `<skip>` nor a parseable `<observation>`.

## 5. Risks and rollback

| Risk | Mitigation | Rollback |
|---|---|---|
| Stricter prompt makes the model skip real work | Skip rules are paired with an explicit must-record list; skip accuracy measured per type | Revert the prompt commit; parser untouched |
| Validation rejects legitimate observations | Rejection is logged with the reason; toggle defaults on but can be disabled by one env var; unit tests cover the all-dropped boundary | `CLAUDEMEM_EXTRACTION_VALIDATION_ENABLED=false` |
| Duplicate check hides a genuinely new memory | Duplicate requires same scope + same type + normalized title equality; the existing row is not touched | Disable the validation layer |
| Larger golden set slows evaluation | 72 samples × 2 providers × key repeats; recorded fixtures make CI replay free | Drop repeats with `EVAL_REPEAT=1` |
| Prompt edits break parser assumptions | Prompts stay English; `<skip>` and the `<observation>` schema are unchanged; prompt tests assert both | Revert prompt commit |

## 6. Implementation order

1. Planning document (this file).
2. Prompt contract + offline prompt tests.
3. Deterministic validation layer + unit tests.
4. Scorer upgrade + expanded golden set + replay fixtures.
5. Before/after runs (LongCat, MiniMax), same samples.
6. Four-layer regression, threshold sync, commits.

## 7. Implementation record

Appended per completed item; see sections below as work lands.

### 7.1 Expanded baseline (2026-11-03, 82 samples, 108 runs per provider)

The hand-written 12-sample set in section 1 was replaced before implementation with a
mechanically generated 82-sample set (`backend/src/test/resources/eval/golden-tool-use.json`):
bug-fix 12, refactor 9, test-add 9, security-fix 9, config-change 8, docs-update 8,
deploy-change 7, skip-low-value 20; 13 key samples repeated 3× (108 runs per provider).

Measured with the **pre-change prompt** (`src/test/resources/eval/prompts-baseline/`) and
`EVAL_VALIDATE=0`, so the numbers below contain neither the new prompt contract nor the
deterministic guard:

| Metric | LongCat | MiniMax |
|---|---|---|
| Skip accuracy (TP/FP/FN/TN) | 96.34% (17/0/3/62) | 84.15% (7/0/13/62) |
| Contract pass rate | 96.34% | 84.15% |
| Title exact / normalized / identifiers | 24.19% / 12.90% / 43.55% | 11.29% / 0.00% / 9.68% |
| Type accuracy | 82.26% | 85.48% |
| Facts precision / recall / trace support | 53.36% / 89.52% / 83.92% | 66.26% / 88.17% / 91.80% |
| Concepts precision / recall | 52.15% / 75.81% | 49.60% / 72.58% |
| Format errors / call failures | 0 / 0 | 0 / 0 |
| Repeat consistency | 92.3% | 92.3% |
| Latency p50 / p95 / max (ms) | 7519 / 11117 / 29421 | 2163 / 3936 / 11920 |
| Wall time (108 calls) | 854 s | 277 s |

Numbers are re-scored with the final scorer so the before/after comparison uses one code path.
The expansion confirms the root causes in section 2: the pre-change prompt loses identifiers
(title normalized 0–12.9%) and MiniMax skips 13 of 20 must-record samples.

### 7.2 Prompt contract + deterministic guard (2026-11-03)

Files:

- `backend/src/main/resources/prompts/init.txt`, `continuation.txt` — skip checklist, must-record
  checklist, type boundary (`feature` for test-only and reproduction-test changes), title contract,
  facts contract, concepts contract.
- `backend/src/main/java/com/ablueforce/cortexce/service/ExtractionValidationService.java` +
  `ObservationRepository.findRecentByMemoryScopeId` + `AgentService` wiring.
- `backend/src/main/resources/application.yml` — `claudemem.extraction.validation.*` (enabled,
  max-title-length, max-facts, duplicate-lookback).
- Tests: `PromptContractTest` (5), `ExtractionValidationServiceTest` (10).

Verification command: `cd backend && mvn -q test -Dtest='PromptContractTest,ExtractionValidationServiceTest'`.

### 7.3 Evaluation tooling (2026-11-03)

Files:

- `GoldenSetScorer` — normalized title matching (CamelCase split, synonym folding, percentage
  normalization), skip confusion matrix, per-type, per-run, repeat consistency, facts
  precision/recall/support.
- `GoldenSetEvaluator` — real calls (manual, never retried, configurable cap) and offline fixture
  replay; records status/elapsed/raw per entry.
- `GoldenSetDataTest` (4) and `GoldenSetScorerTest` (9).
- `scripts/eval-model-quality.sh` — `EVAL_MAX_CALLS` defaults to 0 (unlimited), records
  fixture + metrics, supports replay.
- Fixtures: `src/test/resources/eval/fixtures/{longcat,minimax}-{before,after}.json` and
  `*-metrics.json`.

### 7.4 After measurement and gate status (2026-11-03)

Three prompt revisions were measured on the same 82 samples / 108 runs per provider
(`docs/archive/2026-11-03_model-output-quality-eval-*-after-v3.md` holds the v3 run):

| Metric | LongCat before | LongCat after | MiniMax before | MiniMax after |
|---|---|---|---|---|
| Real calls / wall time | 108 / 854 s | 108 / 945 s | 108 / 277 s | 108 / 226 s* |
| Skip accuracy (TP/FP/FN/TN) | 96.34% (17/0/3/62) | **100%** (20/0/0/62) | 84.15% (7/0/13/62) | **98.78%** (19/1/0/62) |
| Title exact | 24.19% | 56.45% | 11.29% | 38.71% |
| Title normalized (≥0.8 coverage) | 12.90% | 59.68% | 0.00% | 37.10% |
| Title identifiers verbatim | 43.55% | **100%** | 9.68% | 91.94% |
| Type accuracy | 82.26% | 90.32% | 85.48% | 91.94% |
| Facts precision / recall / support | 53.36% / 89.52% / 83.92% | 73.25% / 91.67% / **100%** | 66.26% / 88.17% / 91.80% | 75.00% / 90.32% / **100%** |
| Concepts precision / recall | 52.15% / 75.81% | 65.05% / 70.97% | 49.60% / 72.58% | 65.59% / 70.97% |
| Format errors / validation rejections | 0 / — | 0 / 0 | 0 / — | 0 / 0 |
| Repeat consistency | 92.3% | **100%** | 92.3% | **100%** |
| Latency p50 / p95 / max (ms) | 7519 / 11117 / 29421 | 8739 / 13110 / 18197 | 2163 / 3936 / 11920 | 1746 / 4173 / 7146 |

\* MiniMax wall time is the sum of per-call latencies: the wall-clock line was overwritten when
the fixture was replayed, and the replay always reports `calls=0`.

Gate (pre-committed in section 4):

| id | criterion | threshold | LongCat | MiniMax | verdict |
|---|---|---|---|---|---|
| A1 | format errors | 0 | 0 | 0 | PASS |
| A2 | skip accuracy | ≥ 95% | 100% | 98.78% | PASS |
| A3 | must-record samples skipped | 0 | 0 | 0 | PASS |
| A4 | low-value samples recorded | ≤ max(1, TP/20) | 0 | 1 | PASS |
| A5 | facts precision | ≥ 75% | 73.25% | 75.00% | **FAIL (LongCat)** |
| A6 | normalized title hit | ≥ 80% | 59.68% | 37.10% | **FAIL (both)** |
| A7 | four-layer regression, 0 skipped | green | 386 tests, 0 failures, 0 skipped | — | PASS |
| A8 | guard unit tests | pass | 10 tests | — | PASS |
| A9 | fixture replay reproduces metrics | exact | replay == recorded for both fixtures | — | PASS |

**A6 root cause (measured, not corrected by weakening the threshold).** With the pre-committed
definition (all declared identifiers verbatim *and* keyword coverage ≥ 0.8), 3–4 declared keywords
per sample mean every keyword must appear. Human-acceptable titles land at 0.75 (one incidental
noun missing), so the ceiling is structural. Three alternatives were measured offline against the
same fixtures and all were rejected:

1. Lower the floor to 0.66/0.75 — rejected as a threshold reduction.
2. Verb-class equivalence (`Fixed`/`Changed`/`Updated` counted as one class) plus one object noun:
   69–73%, still under 80% and it weakens the verb signal.
3. Post-hoc pruning of the `titleKeywords` lists — rejected as grading to the test.

Remaining wrong answers (v3, LongCat): `deploy-rollback-script` typed `feature` instead of
`change`; `deploy-canary` and `bug-timezone-report` produce traceable facts that do not quote the
declared keywords (facts precision 73.25%); titles such as `Changed Hikari pool sizing in
application.yml` still drop a config key. MiniMax: `skip-view-logs` records a low-value trace
(1 FP); 5 titles lose `Asia/Shanghai`, `main.tf`, `logback-spring.xml`, `ACME_API_KEY` or
`maximum-pool-size`.

### 7.5 Decisions recorded during implementation

| decision | alternatives | why chosen | blast radius | rollback |
|---|---|---|---|---|
| Duplicate memory → reject (skip) instead of merge | merge into the existing row | a merge lets a new extraction rewrite an established record, and the merge policy is not specified anywhere | one observation is dropped, the pending row is marked skipped | delete the duplicate check in `ExtractionValidationService` |
| Guard on by default, switchable by property | always on; env-only | operators need an escape hatch when a provider legitimately returns ungrounded facts | one config key | set `claudemem.extraction.validation.enabled=false` |
| Layer L3/L4 run with the guard disabled | make the offline stand-in emit trace-grounded facts | L3/L4 prove hook → queue → observation wiring; the guard has dedicated unit tests | none; comment names the reason | flip the property back and ground the stand-in |
| Replay assertions check “after ≥ before” | assert absolute A1–A6 gates in the unit test | a frozen model answer must not turn the build red for reasons unrelated to code; the gate lives in the eval report | none | add absolute assertions back if the dataset is re-baselined |
| Percentage and inflection folding in the scorer | leave `10%` ≠ `10 percent` | both forms describe the same change; symmetric folding applies to expected and actual | metric recomputation only | revert the synonym entries |

### 7.6 Verification commands (2026-11-03)

```bash
cd backend && mvn test                                  # 386 tests, 0 failures, 45 skipped (DB-gated)
./scripts/verify-local.sh                               # ALL CHECKS PASSED, 0 skipped, V20, L1–L4
cd backend && mvn -q test -Dtest=GoldenSetReplayTest    # replay == recorded metrics
```

`VERIFY_MIN_TESTS` was raised from 360 to 380 to lock in the new tests (386 executed).

### 7.7 Next-round candidates

1. **Title metric re-baseline.** Rewrite the golden `titleKeywords` as `verb-class + subject +
   identifiers`, matching section 3.1 exactly, and re-measure A6 without touching the 80% target.
2. **Facts precision.** Deterministically drop process-noise facts (e.g. “helm lint passes”) in
   `ExtractionValidationService`; the contract already forbids them, but only the prompt enforces it.
3. **Concepts recall** fell from 75.8% to 71.0% for both providers after the concepts contract was
   added; re-check whether the “choose exactly 2” rule is too tight.
4. **Summary chain.** `prompts/summary.txt` has no grounding contract; add a trace-anchored rule and
   a golden-set for summaries, then wire it into the same replay harness.
5. **Search relevance.** `SearchService` vector/hybrid branches have no score floor; add a
   configurable minimum similarity and a DB-gated test that an unrelated query returns no rows.

### 7.8 Round 2 — process-noise facts (2026-11-03)

Candidate 2 from 7.7 is implemented: `ExtractionValidationService.isProcessNoise` drops facts whose
only content is “a check passed”, unless the fact states a number/status code or names the exact
artifact (`PaymentServiceTest passes`). Three variants were measured offline against the recorded
fixtures (no extra model calls):

| variant | LongCat facts P / R | MiniMax facts P / R | A5 |
|---|---|---|---|
| no filtering (7.4) | 72.45% / 91.67% | 75.00% / 90.32% | FAIL / PASS |
| drop every pass-statement without a digit | 75.67% / 87.63% | 76.08% / 85.48% | PASS / PASS |
| **shipped: also keep statements that name the artifact** | **73.25% / 91.67%** | **75.00% / 90.32%** | **FAIL / PASS** |

The middle variant buys 2.4 points of precision for 4 points of recall and contradicts our own
facts contract, which allows a statement that names the exact test; it was rejected even though it
would have turned A5 green for LongCat. The shipped rule keeps recall (the stated priority) and
leaves A5 unmet for LongCat by 1.75 points. Decision record:

| decision | alternatives | why chosen | blast radius | rollback |
|---|---|---|---|---|
| keep identifier-bearing pass statements | drop all pass statements | recall is the stated priority; the contract allows named artifacts | one predicate | delete the `isProcessNoise` call and its tests |
| keep noise filtering in the guard only | filter in the scorer too | one place owns fact quality; the scorer must observe what production persists | a fact list shrinks | set `validation.enabled=false` |

New tests: `processNoiseIsDropped`, `noiseOnlyFactsReject` (12 tests in the class).

### 7.9 Audit gap closure (2026-11-03)

Input: `/tmp/cortexce-audit/quality-gap-audit.md` (read-only audit, no build run). Every claim that
could be expressed as a test was re-checked by a test; the table below records what survived.

| audit claim | result | evidence |
|---|---|---|
| read-then-lock lets a second worker claim the same job | confirmed | `ObservationClaimDatabaseTest`: 8 concurrent claimers, exactly one winner, a claimed row is invisible afterwards |
| embedding failure degrades silently | confirmed | `LlmProviderFailureTest.embeddingFailureIsRecordedAndRepairedByBackfill` |
| the listener's exception branch bypasses the retry budget | confirmed | `PendingMessageEventListenerTest` retryable / terminal / unrecordable branches |
| `/api/health` carries `checks` | **wrong** | liveness is `{service,status,timestamp}`; checks live on `/api/readiness` |
| latest migration is V20 | outdated | the suite now pins “≥ 20 and no pending”; latest is V23 |
| local verify can reuse any `postgresql` container | **unsafe in practice** | the shared container held 94/100 clients; the suite's cached contexts exhausted it |

#### Decisions

| # | decision | rejected alternative | why | blast radius | rollback |
|---|---|---|---|---|---|
| 1 | conditional `UPDATE … WHERE status='pending'` is the only ownership arbiter; the Conversation row lock stays for serialization with SessionEnd | `SELECT … FOR UPDATE SKIP LOCKED` | one atomic statement is enough; SKIP LOCKED would add a second ordering rule and still need the Conversation lock; the conditional update also makes every completion path self-checking | `PendingMessageRepository` + `ConversationWriteService` | revert the three call sites; rows remain in the old states |
| 2 | dedicated `embedding_state` / `embedding_error` columns plus a scheduled backfill sweep | reuse `embedding_model_id IS NULL`; queue `failed` | the queue row is gone once the observation is stored, so it cannot describe an embedding that failed after admission; the dedicated column stays queryable forever and the sweep is idempotent | one migration + one scheduled bean | `claudemem.embedding.backfill.enabled=false`; columns are nullable |
| 3 | listener failures go through `markObservationFailed` with the same `retry_count` budget; terminal failures only get a `failure_kind` | listener writes `failed` directly | a direct write cannot see how many attempts already happened and can overwrite a completed row; the job and the listener must share one classification (`RetryPolicy`) | listener + service | revert the listener call; stale recovery still closes the row |
| 4 | `queueDepth` = pending + processing; add `queueFailed`, `queueFailedLast24h`, `embeddingBacklog` and `recent_failure_kinds` | rename/re-shape existing fields | WebUI reads the existing names; additive fields cannot break it | readiness payload + health indicator + V22 partial indexes | ignore the new fields; nothing consumes them |
| 5 | `--min-tests 423` + `--require-baseline` with `scripts/test-baseline.json` | a single total floor only | a total floor cannot see a layer being replaced by cheap tests elsewhere; the per-layer ratchet can | CI + `verify-local.sh` + TESTING docs | lower the floor and delete the baseline file (both visible in review) |
| 6 | the contract probe pins **error-body shape** (a message key) and key success fields; it does not unify error shapes | make every error use one envelope | unifying would break callers that already match on `path`/`status`; the gate only needs the shape not to drift silently | one checker, no runtime change | drop the probes from `contract_probes` |
| 7 | `/api/logs` stays enabled and unauthenticated by default; opt-in `claudemem.logs.api-enabled=false` or `api-token` | protect by default | the WebUI LogsModal has no token flow; changing the default would break an existing integration | `LogsController` only | flip the two properties back |
| 8 | loopback host mapping in compose; `USER app` in the image, `SERVER_ADDRESS=0.0.0.0` inside the container namespace | keep 0.0.0.0 host mapping; run as root with a `read_only` rootfs | the host mapping is the actual exposure; the container namespace needs a non-local bind for the health probe; non-root is the portable fix | compose + Dockerfile | revert both files |
| 9 | one batched `IN` lookup for `/api/sdk-sessions/batch`; composite indexes added, none dropped | per-id lookups; dropping the superseded single-column indexes | the batch fix removes N+1 without changing match semantics; dropping indexes is a separate measurable decision | repository + service + controller + V23 | revert the two controller branches; indexes can be dropped |
| 10 | V20 conflicts are rehearsed in a **sibling disposable database** created by the test | reusing the shared rehearsal DB | the shared DB is already at V23 and must stay consistent for every other DB test | one gated test, no production change | delete the test class |

#### What the audit's “must run” list became

Converted to tests: the claim race (1), the index set and batch equivalence (2, 9), the listener
budget (3), the V20 conflict/cleanup/re-run loop (5), the queue/failure observability fields (4).
Not convertible: real-provider latency and cost, CI secret availability, production WebUI runtime
behaviour, and planner choices that only appear at production data volume. Those stay as
run-time checks; the index existence test is the part that can be asserted without volume.

#### Verification

`./scripts/verify-local.sh` — **ALL CHECKS PASSED**: 423 tests / 0 failures / 0 errors / 0 skipped
(L1 409, L2 7, L3 6, L4 1); Flyway V23; the EN/ZH reference surface (80 operations) matched the
live server; response shapes printed by the probe
(`/api/health` `{service,status,timestamp}`, `/api/readiness` `{checks,status,timestamp}`,
`/api/observations` `{hasMore,items}`, 404/400 bodies carry a message key); proxy E2E executed
with 0 skipped. The Docker image was built and started once: `ConfigUser=app`, runtime uid 100,
container health `healthy`, `/api/health` reachable through the loopback-only mapping.



