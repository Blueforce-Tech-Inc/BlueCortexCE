golden set: real (60 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: current
- Provider: openai, model `LongCat-2.5`, base URL `replay`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 60 unique; runs scored: 1; key repeat: 1; real calls: 0 (cap none); duration: 72 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 87% |
| skip accuracy | 97% |
|   skip confusion (TP/FP/FN/TN) | 9/1/1/49 |
| format errors | 0 |
| validation rejections | 6 |
| call failures | 0 |
| title exact-hit rate (legacy) | 28% |
| title normalized hit rate (legacy) | 28% |
| title action-class hit rate (A6) | 88% (43 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 28% |
| title subject hit rate (legacy, diagnostic) | 74% |
| title trace-visible subject hit rate (A6b) | 74% (43 scored, 0 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 70% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 70% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 28% |
| title identifier hit rate | 74% |
| type accuracy (raw) | 72% |
| type accuracy (auditable labels only) | 82% (5 unverifiable label(s) excluded) |
| facts keyword precision (legacy) / recall | 100% / 100% |
| facts informative precision | 90% |
| facts trace-support rate | 100% |
| concepts precision / recall | 23% / 47% |
| skip consistency across repeats | 0% (0 repeated samples) |
| latency p50 / p95 / max (ms) | 19577 / 58468 / 64591 |
| tokens p50 / p95 / max / mean per call | 5366 / 7094 / 8653 / 5480 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 97% | PASS |
| A3 | must-record samples skipped | = 0 | 1 | **FAIL** |
| A4 | low-value samples recorded | <= max(1, TP/20) | 1 | PASS |
| A5 | facts informative precision | >= 75% | 90% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 88% | PASS |
| A6b | title trace-visible subject | >= 90% | 74% | **FAIL** |
| A6c | title identifiers | = 100% | 74% | **FAIL** |
| A6 | all three title dimensions together | all pass | 70% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 60 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 60 | 97% | 28% | 70% | 100% | 90% | 100% | 0 |

- average: skip 97%, title 28%, title contract 70%, facts P/R 100%/100%, facts actionable 90%
- worst run: skip 97%, title 28%, title contract 70%, facts P/R 100%/100%, facts actionable 90%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| feature | 12 | 12/12 | 10/12 | 3/12 |
| config-change | 1 | 1/1 | 1/1 | 0/1 |
| refactor | 2 | 2/2 | 0/2 | 0/2 |
| docs-update | 20 | 14/20 | 14/20 | 6/20 |
| test-add | 2 | 2/2 | 2/2 | 1/2 |
| security-fix | 7 | 7/7 | 4/7 | 0/7 |
| bug-fix | 6 | 5/6 | 0/6 | 2/6 |
| diagnose | 10 | 9/10 | 0/10 | 0/10 |

## Unverifiable type labels (excluded from the auditable type accuracy)

5 sample(s) expect a type the recorded trace cannot evidence:

- real-e2c887775a: expected bugfix but the trace shows no defect or security evidence
- real-cf667b4d8b: expected bugfix but the trace shows no defect or security evidence
- real-c67e7a5ad3: expected bugfix but the trace shows no defect or security evidence
- real-8ac2a32980: expected bugfix but the trace shows no defect or security evidence
- real-95de35b2ea: expected bugfix but the trace shows no defect or security evidence

## Failure clusters (first run)

| cluster | misses | share of scored (43) |
|---|---|---|
| identifier missing | 11 | 26% |
| action class wrong | 5 | 12% |
| subject wrong | 11 | 26% |
| type wrong | 12 | 28% |

### identifier missing (11)

- real-1fe4ffb124 — expected identifiers [extraction-quality-improvement-plan.md], title 'Changed minimum test count to 472 in scripts/test-baseline.json'
- real-d13e2c90cd — expected identifiers [2026-11-03_real-history-golden-eval-minimax.md], title 'Added GoldenSetRealReplayTest golden set replay for minimax-real-v9'
- real-3a2d426fdf — expected identifiers [GoldenSetScorer], title 'Added EVAL_REPEAT_ALL mode to GoldenSetEvaluator for run-to-run variance measurement'
- real-9e6612fdf9 — expected identifiers [continuation.txt], title 'Added extend as modify synonym in GoldenSetScorer and GoldenSetActionRules'
- real-e352ba3da3 — expected identifiers [longcat-after-v7.json], title 'Added longcat-after-v7 and minimax-after-v7 eval fixtures and archive docs'
- real-28edef5a35 — expected identifiers [longcat-after-v6.json], title 'Added longcat-after-v6 and minimax-after-v6 eval fixtures in eval/fixtures/'
- real-7093cb5c56 — expected identifiers [EmbeddingBackfillConcurrencyDatabaseTest], title 'Fixed EmbeddingBackfillService concurrent batch race condition'
- real-cf667b4d8b — expected identifiers [ObservationFeedbackEntity], title 'Updated ObservationFeedbackRepository javadoc to describe most-recent-first ordering'
- real-9b424d0d02 — expected identifiers [API-zh-CN.md], title 'Documented GET /api/settings example value corrections in docs/API.md changelog'
- real-85480a720e — expected identifiers [TESTING-zh-CN.md], title 'Updated TESTING docs env var table to use DB_NAME instead of DB_HOST'
- real-46e467bff6 — expected identifiers [conversation-scope-preflight.sql], title 'Added conversation memory scope expansion migration V20__conversation_memory_scope_expand.sql'

### action class wrong (5)

- real-f6a1b89e41 — expected [modify], got 'null' from title 'Expanded golden-tool-use-real.json dataset to version real-git-2026-11-03-r10'
- real-3a2d426fdf — expected [modify], got 'create' from title 'Added EVAL_REPEAT_ALL mode to GoldenSetEvaluator for run-to-run variance measurement'
- real-db8ac0287e — expected [document, secure, modify], got 'remove' from title 'Removed non-security qualifier from Fixed definition in prompts/continuation.txt and init.txt'
- real-9e6612fdf9 — expected [document, secure, modify], got 'create' from title 'Added extend as modify synonym in GoldenSetScorer and GoldenSetActionRules'
- real-85480a720e — expected [remove, document], got 'modify' from title 'Updated TESTING docs env var table to use DB_NAME instead of DB_HOST'

### subject wrong (11)

- real-1fe4ffb124 — expected subject tokens [extraction-quality-improvement-plan.md], title 'Changed minimum test count to 472 in scripts/test-baseline.json'
- real-d13e2c90cd — expected subject tokens [2026-11-03_real-history-golden-eval-minimax.md], title 'Added GoldenSetRealReplayTest golden set replay for minimax-real-v9'
- real-3a2d426fdf — expected subject tokens [GoldenSetScorer], title 'Added EVAL_REPEAT_ALL mode to GoldenSetEvaluator for run-to-run variance measurement'
- real-9e6612fdf9 — expected subject tokens [continuation.txt], title 'Added extend as modify synonym in GoldenSetScorer and GoldenSetActionRules'
- real-e352ba3da3 — expected subject tokens [longcat-after-v7.json], title 'Added longcat-after-v7 and minimax-after-v7 eval fixtures and archive docs'
- real-28edef5a35 — expected subject tokens [longcat-after-v6.json], title 'Added longcat-after-v6 and minimax-after-v6 eval fixtures in eval/fixtures/'
- real-7093cb5c56 — expected subject tokens [EmbeddingBackfillConcurrencyDatabaseTest], title 'Fixed EmbeddingBackfillService concurrent batch race condition'
- real-cf667b4d8b — expected subject tokens [ObservationFeedbackEntity], title 'Updated ObservationFeedbackRepository javadoc to describe most-recent-first ordering'
- real-9b424d0d02 — expected subject tokens [API-zh-CN.md], title 'Documented GET /api/settings example value corrections in docs/API.md changelog'
- real-85480a720e — expected subject tokens [TESTING-zh-CN.md], title 'Updated TESTING docs env var table to use DB_NAME instead of DB_HOST'
- real-46e467bff6 — expected subject tokens [conversation-scope-preflight.sql], title 'Added conversation memory scope expansion migration V20__conversation_memory_scope_expand.sql'

### type wrong (12)

- real-f6a1b89e41 — expected 'change', got 'feature' (title 'Expanded golden-tool-use-real.json dataset to version real-git-2026-11-03-r10')
- real-f0d346eab1 — expected 'change', got 'feature' (title 'Added real-history golden set evaluation in golden-tool-use-real.json')
- real-3a2d426fdf — expected 'change', got 'feature' (title 'Added EVAL_REPEAT_ALL mode to GoldenSetEvaluator for run-to-run variance measurement')
- real-f3d12860ff — expected 'change', got 'refactor' (title 'Refactored LogsControllerTest logDir injection to use setField helper')
- real-a8cc1754bc — expected 'bugfix', got 'refactor' (title 'Refactored pending message dedup and summary queries in PendingMessageRepository')
- real-e2c887775a — expected 'bugfix', got 'change' (title 'Documented /api/context/inject in AGENTS.md hard-contract table')
- real-cf667b4d8b — expected 'bugfix', got 'change' (title 'Updated ObservationFeedbackRepository javadoc to describe most-recent-first ordering')
- real-c67e7a5ad3 — expected 'bugfix', got 'change' (title 'Removed else clause in dto.py and ignored memory/.dreams in .gitignore')
- real-8ac2a32980 — expected 'bugfix', got 'change' (title 'Updated backend/Dockerfile port comment to 37777 and removed build args from deployment docs')
- real-9a0e90bed5 — expected 'refactor', got 'feature' (title 'Changed ObservationRepository project filter to memoryScopeId IN subquery')
- real-63b5502fb9 — expected 'bugfix', got 'feature' (title 'Updated V20 memory scope migration to one-shot in V20__conversation_memory_scope_expand.sql')
- real-95de35b2ea — expected 'bugfix', got 'change' (title 'Updated byte-buddy pin rationale comment in backend/pom.xml')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| real-f6a1b89e41 | feature | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 50%/100% | 29426 |
| real-1fe4ffb124 | config-change | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 24195 |
| real-d13e2c90cd | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 19717 |
| real-3b4f737562 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 47464 |
| real-77ba40df77 | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 17855 |
| real-f0d346eab1 | feature | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/100% | 32128 |
| real-3a2d426fdf | refactor | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | MISS | 100%/100% | 100% | 50%/100% | 35732 |
| real-864dadf51b | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 75% | 50%/100% | 25419 |
| real-b064308244 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 7680 |
| real-30a956e462 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 21123 |
| real-ba86bf2588 | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 33% | 50%/100% | 15175 |
| real-a9c8452967 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 21635 |
| real-db8ac0287e | docs-update | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 33% | 0%/0% | 21940 |
| real-9e6612fdf9 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | match | 100%/100% | 100% | 0%/0% | 48226 |
| real-f43cfd6f44 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 15386 |
| real-e352ba3da3 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 19577 |
| real-112de33e23 | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 19358 |
| real-28edef5a35 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 31510 |
| real-6d6df2c321 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 64591 |
| real-d3431639a9 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 43728 |
| real-7093cb5c56 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 47123 |
| real-f3d12860ff | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 30892 |
| real-3409dda60c | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 9855 |
| real-f7b865efe5 | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 17117 |
| real-a8cc1754bc | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 60% | 0%/0% | 62719 |
| real-e2c887775a | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 80% | 50%/100% | 60691 |
| real-cf667b4d8b | bug-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 0% | 0%/0% | 30608 |
| real-a0e77ca4ee | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 33646 |
| real-d958416fdc | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 20193 |
| real-9b424d0d02 | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 9648 |
| real-85480a720e | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | match | 100%/100% | 100% | 0%/0% | 15459 |
| real-c67e7a5ad3 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 15236 |
| real-bdf72f2509 | bug-fix | observation | skip | FAIL | - | - | - | - | - | - | - | 4161 |
| real-8ac2a32980 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 35221 |
| real-2025b33a95 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 4915 |
| real-e8c8b314d6 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2671 |
| real-c42d7a9c5d | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 15932 |
| real-b5ae725e2a | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3641 |
| real-9261b542ca | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3828 |
| real-7c557c8d79 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2870 |
| real-1dbccd058e | diagnose | skip | observation | FAIL | 0% | MISS (subject unreachable, id MISS, action MISS) | miss | MISS | 0%/0% | 0% | 0%/0% | 22890 |
| real-1f9b5d09e7 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2402 |
| real-ed737be6bb | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3212 |
| real-b2d48b12a1 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2442 |
| real-bc3512ae8d | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 16848 |
| real-e02e436bdd | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 36194 |
| real-d44a83721d | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 6888 |
| real-0fd4ffcc31 | test-add | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 25223 |
| real-eee7785ec8 | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 75% | 50%/100% | 58468 |
| real-9a0e90bed5 | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 25194 |
| real-63b5502fb9 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 35819 |
| real-d33efbd8ae | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 36793 |
| real-cd6b972cfd | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 50% | 0%/0% | 34039 |
| real-46e467bff6 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 17235 |
| real-325c817bbf | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 15157 |
| real-7149578a88 | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 11702 |
| real-95de35b2ea | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 15604 |
| real-53851b4a23 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 16763 |
| real-f4c6765fb9 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/100% | 16453 |
| real-67083a7bc1 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 15351 |

## Problems

- real-f6a1b89e41: title coverage 50% (expected keywords [change, golden-tool-use-real.json])
- real-f6a1b89e41: title action [modify] missing: title leads 'null' (got 'Expanded golden-tool-use-real.json dataset to version real-git-2026-11-03-r10')
- real-f6a1b89e41: title verb class 'change' missing (got 'Expanded golden-tool-use-real.json dataset to version real-git-2026-11-03-r10')
- real-f6a1b89e41: type 'feature' != expected 'change'
- real-1fe4ffb124: title lost identifiers [extraction-quality-improvement-plan.md] (got 'Changed minimum test count to 472 in scripts/test-baseline.json')
- real-1fe4ffb124: title verb class 'document' missing (got 'Changed minimum test count to 472 in scripts/test-baseline.json')
- real-1fe4ffb124: title subject [extraction-quality-improvement-plan.md] missing (got 'Changed minimum test count to 472 in scripts/test-baseline.json')
- real-1fe4ffb124: title trace-visible subject [extraction-quality-improvement-plan.md] missing (got 'Changed minimum test count to 472 in scripts/test-baseline.json')
- real-d13e2c90cd: title lost identifiers [2026-11-03_real-history-golden-eval-minimax.md] (got 'Added GoldenSetRealReplayTest golden set replay for minimax-real-v9')
- real-d13e2c90cd: title verb class 'document' missing (got 'Added GoldenSetRealReplayTest golden set replay for minimax-real-v9')
- real-d13e2c90cd: title subject [2026-11-03_real-history-golden-eval-minimax.md] missing (got 'Added GoldenSetRealReplayTest golden set replay for minimax-real-v9')
- real-d13e2c90cd: title trace-visible subject [2026-11-03_real-history-golden-eval-minimax.md] missing (got 'Added GoldenSetRealReplayTest golden set replay for minimax-real-v9')
- real-3b4f737562: title coverage 50% (expected keywords [document, minimax-after-v9.json])
- real-3b4f737562: title verb class 'document' missing (got 'Added minimax-after-v9 eval fixtures and metrics in minimax-after-v9.json')
- real-77ba40df77: concepts recall 0% (expected [what-changed], got [pattern, trade-off])
- real-f0d346eab1: type 'feature' != expected 'change'
- real-3a2d426fdf: title lost identifiers [GoldenSetScorer] (got 'Added EVAL_REPEAT_ALL mode to GoldenSetEvaluator for run-to-run variance measurement')
- real-3a2d426fdf: title action [modify] missing: title leads 'create' (got 'Added EVAL_REPEAT_ALL mode to GoldenSetEvaluator for run-to-run variance measurement')
- real-3a2d426fdf: title verb class 'change' missing (got 'Added EVAL_REPEAT_ALL mode to GoldenSetEvaluator for run-to-run variance measurement')
- real-3a2d426fdf: title subject [GoldenSetScorer] missing (got 'Added EVAL_REPEAT_ALL mode to GoldenSetEvaluator for run-to-run variance measurement')
- real-3a2d426fdf: title trace-visible subject [GoldenSetScorer] missing (got 'Added EVAL_REPEAT_ALL mode to GoldenSetEvaluator for run-to-run variance measurement')
- real-3a2d426fdf: type 'feature' != expected 'change'
- real-864dadf51b: title coverage 50% (expected keywords [document, goldensetscorer])
- real-864dadf51b: title verb class 'document' missing (got 'Extended GoldenSetScorer evidence matching with toolInput in GoldenSetScorer.java')
- real-b064308244: title coverage 50% (expected keywords [document, extraction-quality-improvement-plan.md])
- real-b064308244: title verb class 'document' missing (got 'Updated rehearsal test execution reference in extraction-quality-improvement-plan.md')
- real-30a956e462: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a9c8452967: title coverage 50% (expected keywords [document, searchsimilarityevaluator])
- real-a9c8452967: title verb class 'document' missing (got 'Added SearchSimilarityEvaluator replay harness with BGE-M3 fixtures')
- real-db8ac0287e: title coverage 50% (expected keywords [document, continuation.txt])
- real-db8ac0287e: title action [document, secure, modify] missing: title leads 'remove' (got 'Removed non-security qualifier from Fixed definition in prompts/continuation.txt and init.txt')
- real-db8ac0287e: title verb class 'document' missing (got 'Removed non-security qualifier from Fixed definition in prompts/continuation.txt and init.txt')
- real-db8ac0287e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9e6612fdf9: title lost identifiers [continuation.txt] (got 'Added extend as modify synonym in GoldenSetScorer and GoldenSetActionRules')
- real-9e6612fdf9: title action [document, secure, modify] missing: title leads 'create' (got 'Added extend as modify synonym in GoldenSetScorer and GoldenSetActionRules')
- real-9e6612fdf9: title verb class 'document' missing (got 'Added extend as modify synonym in GoldenSetScorer and GoldenSetActionRules')
- real-9e6612fdf9: title subject [continuation.txt] missing (got 'Added extend as modify synonym in GoldenSetScorer and GoldenSetActionRules')
- real-9e6612fdf9: title trace-visible subject [continuation.txt] missing (got 'Added extend as modify synonym in GoldenSetScorer and GoldenSetActionRules')
- real-9e6612fdf9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e352ba3da3: title lost identifiers [longcat-after-v7.json] (got 'Added longcat-after-v7 and minimax-after-v7 eval fixtures and archive docs')
- real-e352ba3da3: title verb class 'document' missing (got 'Added longcat-after-v7 and minimax-after-v7 eval fixtures and archive docs')
- real-e352ba3da3: title subject [longcat-after-v7.json] missing (got 'Added longcat-after-v7 and minimax-after-v7 eval fixtures and archive docs')
- real-e352ba3da3: title trace-visible subject [longcat-after-v7.json] missing (got 'Added longcat-after-v7 and minimax-after-v7 eval fixtures and archive docs')
- real-112de33e23: validation rejected the observation: no verifiable facts after evidence check
- real-28edef5a35: title lost identifiers [longcat-after-v6.json] (got 'Added longcat-after-v6 and minimax-after-v6 eval fixtures in eval/fixtures/')
- real-28edef5a35: title verb class 'document' missing (got 'Added longcat-after-v6 and minimax-after-v6 eval fixtures in eval/fixtures/')
- real-28edef5a35: title subject [longcat-after-v6.json] missing (got 'Added longcat-after-v6 and minimax-after-v6 eval fixtures in eval/fixtures/')
- real-28edef5a35: title trace-visible subject [longcat-after-v6.json] missing (got 'Added longcat-after-v6 and minimax-after-v6 eval fixtures in eval/fixtures/')
- real-6d6df2c321: title coverage 50% (expected keywords [document, continuation.txt])
- real-6d6df2c321: title verb class 'document' missing (got 'Updated identifier-search skip rule in continuation.txt')
- real-6d6df2c321: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d3431639a9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-7093cb5c56: title lost identifiers [EmbeddingBackfillConcurrencyDatabaseTest] (got 'Fixed EmbeddingBackfillService concurrent batch race condition')
- real-7093cb5c56: title verb class 'harden' missing (got 'Fixed EmbeddingBackfillService concurrent batch race condition')
- real-7093cb5c56: title subject [EmbeddingBackfillConcurrencyDatabaseTest] missing (got 'Fixed EmbeddingBackfillService concurrent batch race condition')
- real-7093cb5c56: title trace-visible subject [EmbeddingBackfillConcurrencyDatabaseTest] missing (got 'Fixed EmbeddingBackfillService concurrent batch race condition')
- real-f3d12860ff: title coverage 50% (expected keywords [harden, logscontrollertest])
- real-f3d12860ff: title verb class 'harden' missing (got 'Refactored LogsControllerTest logDir injection to use setField helper')
- real-f3d12860ff: type 'refactor' != expected 'change'
- real-f3d12860ff: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-3409dda60c: validation rejected the observation: title too long: 101 chars > 100
- real-f7b865efe5: title coverage 50% (expected keywords [harden, index.html])
- real-f7b865efe5: title verb class 'harden' missing (got 'Changed header background color --color-bg-header in index.html')
- real-f7b865efe5: concepts recall 0% (expected [problem-solution], got [what-changed, what-changed])
- real-a8cc1754bc: title coverage 50% (expected keywords [remove, pendingmessagerepository])
- real-a8cc1754bc: title verb class 'remove' missing (got 'Refactored pending message dedup and summary queries in PendingMessageRepository')
- real-a8cc1754bc: type 'refactor' != expected 'bugfix'
- real-a8cc1754bc: concepts recall 0% (expected [problem-solution], got [what-changed, what-changed])
- real-e2c887775a: title coverage 50% (expected keywords [remove, agents.md])
- real-e2c887775a: title verb class 'remove' missing (got 'Documented /api/context/inject in AGENTS.md hard-contract table')
- real-e2c887775a: type 'change' != expected 'bugfix'
- real-cf667b4d8b: title lost identifiers [ObservationFeedbackEntity] (got 'Updated ObservationFeedbackRepository javadoc to describe most-recent-first ordering')
- real-cf667b4d8b: title verb class 'remove' missing (got 'Updated ObservationFeedbackRepository javadoc to describe most-recent-first ordering')
- real-cf667b4d8b: title subject [ObservationFeedbackEntity] missing (got 'Updated ObservationFeedbackRepository javadoc to describe most-recent-first ordering')
- real-cf667b4d8b: title trace-visible subject [ObservationFeedbackEntity] missing (got 'Updated ObservationFeedbackRepository javadoc to describe most-recent-first ordering')
- real-cf667b4d8b: type 'change' != expected 'bugfix'
- real-cf667b4d8b: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a0e77ca4ee: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d958416fdc: title coverage 50% (expected keywords [remove, patrol-state.json])
- real-d958416fdc: title verb class 'remove' missing (got 'Updated patrol rotation lastDirection to backend in docs/drafts/patrol-state.json')
- real-d958416fdc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9b424d0d02: title lost identifiers [API-zh-CN.md] (got 'Documented GET /api/settings example value corrections in docs/API.md changelog')
- real-9b424d0d02: title verb class 'remove' missing (got 'Documented GET /api/settings example value corrections in docs/API.md changelog')
- real-9b424d0d02: title subject [API-zh-CN.md] missing (got 'Documented GET /api/settings example value corrections in docs/API.md changelog')
- real-9b424d0d02: title trace-visible subject [API-zh-CN.md] missing (got 'Documented GET /api/settings example value corrections in docs/API.md changelog')
- real-9b424d0d02: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-85480a720e: title lost identifiers [TESTING-zh-CN.md] (got 'Updated TESTING docs env var table to use DB_NAME instead of DB_HOST')
- real-85480a720e: title action [remove, document] missing: title leads 'modify' (got 'Updated TESTING docs env var table to use DB_NAME instead of DB_HOST')
- real-85480a720e: title verb class 'remove' missing (got 'Updated TESTING docs env var table to use DB_NAME instead of DB_HOST')
- real-85480a720e: title subject [TESTING-zh-CN.md] missing (got 'Updated TESTING docs env var table to use DB_NAME instead of DB_HOST')
- real-85480a720e: title trace-visible subject [TESTING-zh-CN.md] missing (got 'Updated TESTING docs env var table to use DB_NAME instead of DB_HOST')
- real-85480a720e: concepts recall 0% (expected [problem-solution], got [what-changed, gotcha])
- real-c67e7a5ad3: type 'change' != expected 'bugfix'
- real-c67e7a5ad3: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-bdf72f2509: expected an observation but model skipped
- real-8ac2a32980: title verb class 'remove' missing (got 'Updated backend/Dockerfile port comment to 37777 and removed build args from deployment docs')
- real-8ac2a32980: type 'change' != expected 'bugfix'
- real-8ac2a32980: concepts recall 0% (expected [problem-solution], got [what-changed, gotcha])
- real-1dbccd058e: expected <skip> but produced an observation
- real-bc3512ae8d: validation rejected the observation: title too long: 106 chars > 100
- real-e02e436bdd: title coverage 50% (expected keywords [document, continuation.txt])
- real-e02e436bdd: title verb class 'document' missing (got 'Updated title example guidance in continuation.txt and init.txt prompt resources')
- real-d44a83721d: validation rejected the observation: title too long: 102 chars > 100
- real-0fd4ffcc31: title coverage 50% (expected keywords [change, goldensetscorer])
- real-0fd4ffcc31: title verb class 'change' missing (got 'Extended GoldenSetScorer with skipFp metric and updated A3 gate in GoldenSetReplayTest')
- real-9a0e90bed5: title coverage 50% (expected keywords [harden, observationrepository])
- real-9a0e90bed5: title verb class 'harden' missing (got 'Changed ObservationRepository project filter to memoryScopeId IN subquery')
- real-9a0e90bed5: type 'feature' != expected 'refactor'
- real-9a0e90bed5: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-63b5502fb9: title coverage 50% (expected keywords [harden, v20__conversation_memory_scope_expand.sql])
- real-63b5502fb9: title verb class 'harden' missing (got 'Updated V20 memory scope migration to one-shot in V20__conversation_memory_scope_expand.sql')
- real-63b5502fb9: type 'feature' != expected 'bugfix'
- real-63b5502fb9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d33efbd8ae: title coverage 50% (expected keywords [harden, conversationscopebackfillrunner])
- real-d33efbd8ae: title verb class 'harden' missing (got 'Added ConversationScopeBackfillRunner and TechnicalBucketClassifier migration tooling')
- real-d33efbd8ae: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-cd6b972cfd: title coverage 50% (expected keywords [harden, summaryjobservice])
- real-cd6b972cfd: title verb class 'harden' missing (got 'Added SummaryJobService to service layer in SummaryJobService.java')
- real-cd6b972cfd: concepts recall 0% (expected [problem-solution], got [pattern, what-changed])
- real-46e467bff6: title lost identifiers [conversation-scope-preflight.sql] (got 'Added conversation memory scope expansion migration V20__conversation_memory_scope_expand.sql')
- real-46e467bff6: title verb class 'harden' missing (got 'Added conversation memory scope expansion migration V20__conversation_memory_scope_expand.sql')
- real-46e467bff6: title subject [conversation-scope-preflight.sql] missing (got 'Added conversation memory scope expansion migration V20__conversation_memory_scope_expand.sql')
- real-46e467bff6: title trace-visible subject [conversation-scope-preflight.sql] missing (got 'Added conversation memory scope expansion migration V20__conversation_memory_scope_expand.sql')
- real-46e467bff6: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-325c817bbf: validation rejected the observation: title too long: 131 chars > 100
- real-7149578a88: validation rejected the observation: title too long: 108 chars > 100
- real-95de35b2ea: title coverage 50% (expected keywords [change, pom.xml])
- real-95de35b2ea: type 'change' != expected 'bugfix'
- real-95de35b2ea: concepts recall 0% (expected [problem-solution], got [what-changed, what-changed])
- real-53851b4a23: title coverage 50% (expected keywords [document, 2026-10-08_health-check-history-11.md])
- real-53851b4a23: title verb class 'document' missing (got 'Added health check history archive 11 in docs/archive/2026-10-08_health-check-history-11.md')
- real-53851b4a23: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])

## Raw answers behind problems

### real-f6a1b89e41

```text
<observation>   <type>feature</type>   <title>Expanded golden-tool-use-real.json dataset to version real-git-2026-11-03-r10</title>   <subtitle>Real-history golden eval set regenerated with 1245 added lines, sample threshold doubled to 60, and round-10 failure evidence field added.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json version changed from "real-g... [truncated]
```

### real-1fe4ffb124

```text
<observation>   <type>change</type>   <title>Changed minimum test count to 472 in scripts/test-baseline.json</title>   <subtitle>Raised the hard test floor from 423 to 472 across baseline, CI workflow, verify script, and testing docs.</subtitle>   <facts>     <fact>scripts/test-baseline.json changed "total" from 423 to 472.</fact>     <fact>.github/workflows/backend-tests.yml now runs check-test-r... [truncated]
```

### real-d13e2c90cd

```text
<observation>   <type>feature</type>   <title>Added GoldenSetRealReplayTest golden set replay for minimax-real-v9</title>   <subtitle>New regression harness replays 36 real-history eval samples against the minimax strategy with recorded metrics.</subtitle>   <facts>     <fact>Added GoldenSetRealReplayTest.java at backend/src/test/java/com/ablueforce/cortexce/eval/ with 87 lines.</fact>     <fact>A... [truncated]
```

### real-3b4f737562

```text
<observation>   <type>feature</type>   <title>Added minimax-after-v9 eval fixtures and metrics in minimax-after-v9.json</title>   <subtitle>New model output quality eval fixture set, metrics file, and archive doc, with small edits to existing eval tooling.</subtitle>   <facts>     <fact>Added backend/src/test/resources/eval/fixtures/minimax-after-v9.json with 2096 lines of eval fixture data.</fact... [truncated]
```

### real-77ba40df77

```text
<observation>   <type>feature</type>   <title>Added ExtractionConsistency service with majority vote for tool traces</title>   <subtitle>New ExtractionConsistency service and test added, wired into AgentService, with configurable attempts per tool trace in application.yml.</subtitle>   <facts>     <fact>New file backend/src/main/java/com/ablueforce/cortexce/service/ExtractionConsistency.java added... [truncated]
```

### real-f0d346eab1

```text
<observation>   <type>feature</type>   <title>Added real-history golden set evaluation in golden-tool-use-real.json</title>   <subtitle>New mechanically generated real-history golden dataset plus generator script, test class, and EVAL_GOLDEN selection wiring.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json added with 1097 lines of real-history golden tool-u... [truncated]
```

### real-3a2d426fdf

```text
<observation>   <type>feature</type>   <title>Added EVAL_REPEAT_ALL mode to GoldenSetEvaluator for run-to-run variance measurement</title>   <subtitle>Golden-set evaluation can now repeat every sample EVAL_KEY_REPEAT times and records promptTokens per scored sample.</subtitle>   <facts>     <fact>GoldenSetEvaluator.java replaces the keySample-only repeat branch (`int runs = sample.keySample ? keyR... [truncated]
```

### real-864dadf51b

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetScorer evidence matching with toolInput in GoldenSetScorer.java</title>   <subtitle>Golden-set evaluation now derives evidence from toolInput in addition to toolName, and the legacy title-subject metric is marked diagnostic.</subtitle>   <facts>     <fact>GoldenSetScorer.java replaced the line `String evidence = (sample.toolName == nu... [truncated]
```

### real-b064308244

```text
```xml <observation>   <type>change</type>   <title>Updated rehearsal test execution reference in extraction-quality-improvement-plan.md</title>   <subtitle>Draft plan now says the rehearsal test runs under the rehearsal database gate instead of the L4 layer.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md replaced "The test runs in the L4 layer under the rehearsa... [truncated]
```

### real-30a956e462

```text
<observation>   <type>change</type>   <title>Documented Round 8 extraction changes in docs/drafts/extraction-quality-improvement-plan.md</title>   <subtitle>Added 139 lines recording Round 8 work: insertion-scope contract, coverage removal, and a reverted security verb.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md gained 139 inserted lines with 0 deletions.</fa... [truncated]
```

### real-a9c8452967

```text
<observation>   <type>feature</type>   <title>Added SearchSimilarityEvaluator replay harness with BGE-M3 fixtures</title>   <subtitle>New replay-style search similarity evaluation harness with corpus, BGE-M3 metric fixtures, and archived design note.</subtitle>   <facts>     <fact>SearchSimilarityEvaluator.java was created with 411 lines in package com.ablueforce.cortexce.eval.</fact>     <fact>Se... [truncated]
```

### real-db8ac0287e

```text
<observation>   <type>change</type>   <title>Removed non-security qualifier from Fixed definition in prompts/continuation.txt and init.txt</title>   <subtitle>Prompt verb contract for Fixed no longer limits the definition to non-security code.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt line changed from “- Fixed: a defect already produced wrong or failing be... [truncated]
```

### real-9e6612fdf9

```text
<observation>   <type>feature</type>   <title>Added extend as modify synonym in GoldenSetScorer and GoldenSetActionRules</title>   <subtitle>Golden set evaluation now recognizes "extend" as a valid synonym for the "modify" action verb.</subtitle>   <facts>     <fact>GoldenSetActionRules.java now includes Map.entry("extend", "modify") alongside the existing enable mapping</fact>     <fact>GoldenSet... [truncated]
```

### real-e352ba3da3

```text
<observation>   <type>feature</type>   <title>Added longcat-after-v7 and minimax-after-v7 eval fixtures and archive docs</title>   <subtitle>New model output quality evaluation fixtures and metrics for longcat and minimax after-v7, plus two archived evaluation documents.</subtitle>   <facts>     <fact>Created backend/src/test/resources/eval/fixtures/longcat-after-v7.json with 656 lines of fixture ... [truncated]
```

### real-112de33e23

```text
<observation>   <type>change</type>   <title>Extended prompt templates continuation.txt and init.txt with stray guidance text</title>   <subtitle>Unrelated memory-observer guidance text was inserted into two backend prompt resource templates.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt received 3 inserted lines beginning with "- A replacement keeps BOTH names... [truncated]
```

### real-28edef5a35

```text
<observation>   <type>feature</type>   <title>Added longcat-after-v6 and minimax-after-v6 eval fixtures in eval/fixtures/</title>   <subtitle>Created model output quality evaluation fixtures, metrics, and archived reports for two model variants.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v6.json and minimax-after-v6.json each received 656 lines of fixture... [truncated]
```

### real-6d6df2c321

```text
<observation>   <type>change</type>   <title>Updated identifier-search skip rule in continuation.txt</title>   <subtitle>Memory observer prompt templates now skip grep/glob searches that only locate an identifier, even when matches are found.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt removed 11 lines and added 71 lines in the observation skip guidance.</fac... [truncated]
```

### real-d3431639a9

```text
<observation>   <type>change</type>   <title>Documented eval in docs/archive/2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md</title>   <subtitle>Archived model output quality evaluations for minimax and longcat plus an extraction quality improvement plan round.</subtitle>   <facts>     <fact>Added 598 lines to docs/archive/2026-11-03_model-output-quality-eval-minimax-after-v4-con... [truncated]
```

### real-7093cb5c56

```text
<observation>   <type>bugfix</type>   <title>Fixed EmbeddingBackfillService concurrent batch race condition</title>   <subtitle>Added concurrency isolation to EmbeddingBackfillService and verified with new EmbeddingBackfillConcurrencyDatabaseTest.</subtitle>   <facts>     <fact>EmbeddingBackfillConcurrencyDatabaseTest.java added 333 lines testing concurrent database access for embedding backfill</... [truncated]
```

### real-f3d12860ff

```text
<observation>   <type>refactor</type>   <title>Refactored LogsControllerTest logDir injection to use setField helper</title>   <subtitle>Test reflection logic replaced with reusable setField method; LogsController gained javadoc documentation.</subtitle>   <facts>     <fact>LogsControllerTest replaced direct Field reflection (getDeclaredField("logDir")) with setField("logDir", logDir.toString()) h... [truncated]
```

### real-3409dda60c

```text
<observation>   <type>change</type>   <title>Documented implementation commit range 325c817..f7b865e in conversation-memory-scope-refactor-plan.md</title>   <subtitle>Updated the refactor plan's commit range field from pending to the concrete 9-commit range with a full list appended.</subtitle>   <facts>     <fact>The table field "实施 commit 范围" in docs/drafts/conversation-memory-scope-refactor-pla... [truncated]
```

### real-f7b865efe5

```text
```xml <observation>   <type>change</type>   <title>Changed header background color --color-bg-header in index.html</title>   <subtitle>Updated the static viewer header background CSS variable to a warmer off-white tone.</subtitle>   <facts>     <fact>--color-bg-header in backend/src/main/resources/static/index.html changed from #f6f8fa to #f8f6f2.</fact>     <fact>backend/src/main/resources/stati... [truncated]
```

### real-a8cc1754bc

```text
<observation>   <type>refactor</type>   <title>Refactored pending message dedup and summary queries in PendingMessageRepository</title>   <subtitle>Removed unused dedup repository code, renamed summary query field to conversationId, and dropped COLLATE C from scope filter.</subtitle>   <facts>     <fact>PendingMessageRepository.java removed 22 lines including the comment "Dedup: check if a message... [truncated]
```

### real-e2c887775a

```text
<observation>   <type>change</type>   <title>Documented /api/context/inject in AGENTS.md hard-contract table</title>   <subtitle>Corrected AGENTS.md's contract table endpoint from /api/context/generate to /api/context/inject and removed a stale comment from client.ts.</subtitle>   <facts>     <fact>AGENTS.md now maps `/api/context/inject` to `updateFiles` in `proxy/proxy.js`, replacing the prior `... [truncated]
```

### real-cf667b4d8b

```text
<observation>   <type>change</type>   <title>Updated ObservationFeedbackRepository javadoc to describe most-recent-first ordering</title>   <subtitle>Corrected repository javadoc to state feedback is returned most recent first instead of ordered by creation time.</subtitle>   <facts>     <fact>ObservationFeedbackRepository.java javadoc changed from "ordered by creation time" to "most recent first"... [truncated]
```

### real-a0e77ca4ee

```text
<observation>   <type>change</type>   <title>Removed drifted line count column and duplicate header in TESTING.md</title>   <subtitle>Testing guide documentation refreshed: line-count column dropped, duplicate table header fixed, Chinese and English versions synced.</subtitle>   <facts>     <fact>docs/TESTING.md script table now uses two columns "| Script | Description |", removing the drifted "Li... [truncated]
```

### real-d958416fdc

```text
<observation>   <type>change</type>   <title>Updated patrol rotation lastDirection to backend in docs/drafts/patrol-state.json</title>   <subtitle>Patrol state and rotation docs now record the backend pass as the most recent completed review direction.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json sets `lastDirection` to `backend` (previously `demo`).</fact>     <fact>docs/drafts/pa... [truncated]
```

### real-9b424d0d02

```text
<observation>   <type>change</type>   <title>Documented GET /api/settings example value corrections in docs/API.md changelog</title>   <subtitle>Changelog entries added to API docs recording that 6 example values were corrected to match AppSettings.toMap() defaults.</subtitle>   <facts>     <fact>docs/API.md and docs/API-zh-CN.md each gained a changelog row dated 2026-05-06 for version 0.1.0-beta+... [truncated]
```

### real-85480a720e

```text
<observation>   <type>change</type>   <title>Updated TESTING docs env var table to use DB_NAME instead of DB_HOST</title>   <subtitle>Replaced the DB_HOST environment variable entry with DB_NAME (claude_mem) in both English and Chinese testing documentation.</subtitle>   <facts>     <fact>docs/TESTING.md and docs/TESTING-zh-CN.md env var tables had `DB_HOST | 127.0.0.1` replaced with `DB_NAME | cl... [truncated]
```

### real-c67e7a5ad3

```text
<observation>   <type>change</type>   <title>Removed else clause in dto.py and ignored memory/.dreams in .gitignore</title>   <subtitle>Cleaned up redundant else branch in dto.py and excluded memory/.dreams from version control.</subtitle>   <facts>     <fact>python-sdk/cortex-mem-python/cortex_mem/dto.py had 2 lines removed (an `else:` branch) with 0 lines added.</fact>     <fact>.gitignore now i... [truncated]
```

### real-bdf72f2509

```text
<skip>Removed a comment line only; no functional change or deliverable</skip>
```

### real-8ac2a32980

```text
<observation>   <type>change</type>   <title>Updated backend/Dockerfile port comment to 37777 and removed build args from deployment docs</title>   <subtitle>Clarified default port in Dockerfile and removed obsolete build arguments sections from both deployment guides.</subtitle>   <facts>     <fact>backend/Dockerfile comment changed from "# Default port (can be overridden by SERVER_PORT env var)"... [truncated]
```

### real-1dbccd058e

```text
<observation>   <type>discovery</type>   <title>Read patrol-state.json showing lastDirection backend</title>   <subtitle>Patrol rotation state records backend as last completed direction on 2026-10-07, round 353, with no change applied.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json contains "lastDirection": "backend".</fact>     <fact>docs/drafts/patrol-rotation.md records last comp... [truncated]
```

### real-bc3512ae8d

```text
<observation>   <type>change</type>   <title>Documented Round 6 category few-shots and measured title ceiling in extraction-quality-improvement-plan.md</title>   <subtitle>Added section 7.12 recording Round 6 work on category few-shots and the measured title ceiling to the extraction quality draft plan.</subtitle>   <facts>     <fact>Section 7.12 "Round 6 — category few-shots and the measured titl... [truncated]
```

### real-e02e436bdd

```text
```xml <observation>   <type>change</type>   <title>Updated title example guidance in continuation.txt and init.txt prompt resources</title>   <subtitle>Prompt resource files now present title format guidance as category-shaped examples rather than a single illustrative line.</subtitle>   <facts>     <fact>In backend/src/main/resources/prompts/continuation.txt, the line `- Good: \`Fixed UserCache.... [truncated]
```

### real-d44a83721d

```text
<observation>   <type>change</type>   <title>Documented Round 5 metric correctness and title ceiling plan in extraction-quality-improvement-plan.md</title>   <subtitle>Added section 7.11 covering metric correctness fixes and remaining title extraction improvements.</subtitle>   <facts>     <fact>Section 7.11 titled "Round 5 — metric correctness and the remaining title ceiling" was added to docs/dr... [truncated]
```

### real-0fd4ffcc31

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetScorer with skipFp metric and updated A3 gate in GoldenSetReplayTest</title>   <subtitle>Added skipFp tracking for record-worthy samples skipped, distinct from skipFn, and switched the A3 gate assertion to use it.</subtitle>   <facts>     <fact>GoldenSetScorer.java was extended with 29 new lines adding a skipFp field to track record-w... [truncated]
```

### real-9a0e90bed5

```text
<observation>   <type>feature</type>   <title>Changed ObservationRepository project filter to memoryScopeId IN subquery</title>   <subtitle>Introduced memory scope abstraction: observations now filtered by memoryScopeId instead of projectPath.</subtitle>   <facts>     <fact>ObservationRepository.java replaced `WHERE (:project IS NULL OR o.projectPath = :project)` with `WHERE (:project IS NULL OR o... [truncated]
```

### real-63b5502fb9

```text
<observation>   <type>feature</type>   <title>Updated V20 memory scope migration to one-shot in V20__conversation_memory_scope_expand.sql</title>   <subtitle>Rewrote the V20 conversation/memory scope migration as a one-shot schema change and added rehearsal plus fixture scripts.</subtitle>   <facts>     <fact>backend/src/main/resources/db/migration/V20__conversation_memory_scope_expand.sql was rew... [truncated]
```

### real-d33efbd8ae

```text
<observation>   <type>feature</type>   <title>Added ConversationScopeBackfillRunner and TechnicalBucketClassifier migration tooling</title>   <subtitle>New backend migration package introduces conversation scope backfill, technical bucket classification, a CLI entry point, and unit tests.</subtitle>   <facts>     <fact>Created backend/src/main/java/com/ablueforce/cortexce/migration/ConversationSco... [truncated]
```

### real-cd6b972cfd

```text
<observation>   <type>feature</type>   <title>Added SummaryJobService to service layer in SummaryJobService.java</title>   <subtitle>New backend service class implementing summary job functionality with authentication considerations.</subtitle>   <facts>     <fact>SummaryJobService.java now contains 326 lines of service implementation in package com.ablueforce.cortexce.service.</fact>     <fact>Th... [truncated]
```

### real-46e467bff6

```text
```xml <observation>   <type>feature</type>   <title>Added conversation memory scope expansion migration V20__conversation_memory_scope_expand.sql</title>   <subtitle>New additive V20 migration expands conversation/memory scope identity schema with preflight and fixture scripts.</subtitle>   <facts>     <fact>New migration backend/src/main/resources/db/migration/V20__conversation_memory_scope_expa... [truncated]
```

### real-325c817bbf

```text
<observation>   <type>change</type>   <title>Documented Conversation/Memory Scope identity-boundary refactor blueprint in docs/drafts/conversation-memory-scope-refactor-plan.md</title>   <subtitle>Draft implementation plan added covering the refactor of identity boundaries between Conversation and Memory Scope.</subtitle>   <facts>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md w... [truncated]
```

### real-7149578a88

```text
<observation>   <type>change</type>   <title>Documented docker-e2e build context and byte-buddy correction section in docs/drafts/backend-fix-progress.md</title>   <subtitle>Added a dated progress section heading covering the second round of build fixes.</subtitle>   <facts>     <fact>docs/drafts/backend-fix-progress.md gained a new section heading "## 2026-10-02 01:57 | 构建修复第二轮 — docker-e2e 构建上下文... [truncated]
```

### real-95de35b2ea

```text
<observation>   <type>change</type>   <title>Updated byte-buddy pin rationale comment in backend/pom.xml</title>   <subtitle>Reworded the byte-buddy version pin comment in pom.xml and corrected the build context comment in docker-e2e-test.sh.</subtitle>   <facts>     <fact>backend/pom.xml comment now reads "Pin byte-buddy to 1.15.x: Mockito 5.11 (via spring-boot-starter-test BOM)" instead of citin... [truncated]
```

### real-53851b4a23

```text
<observation>   <type>change</type>   <title>Added health check history archive 11 in docs/archive/2026-10-08_health-check-history-11.md</title>   <subtitle>Created a 1206-line archive of health-check rounds 312~359+ covering 2026-10-06 to 2026-10-07 and indexed it in docs/archive/README.md.</subtitle>   <facts>     <fact>New file docs/archive/2026-10-08_health-check-history-11.md added 1206 lines... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
