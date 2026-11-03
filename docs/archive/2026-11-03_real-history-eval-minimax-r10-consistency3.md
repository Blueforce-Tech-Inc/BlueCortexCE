golden set: real (60 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: r10-consistency3
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 30 unique; runs scored: 1; key repeat: 3; real calls: 90 (cap none); duration: 213625 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 87% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 0/0/0/30 |
| format errors | 0 |
| validation rejections | 1 |
| call failures | 3 |
| title exact-hit rate (legacy) | 8% |
| title normalized hit rate (legacy) | 8% |
| title action-class hit rate (A6) | 96% (26 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 27% |
| title subject hit rate (legacy, diagnostic) | 31% |
| title trace-visible subject hit rate (A6b) | 31% (26 scored, 0 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 23% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 23% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 8% |
| title identifier hit rate | 27% |
| type accuracy | 69% |
| facts keyword precision (legacy) / recall | 100% / 100% |
| facts informative precision | 83% |
| facts trace-support rate | 100% |
| concepts precision / recall | 27% / 54% |
| skip consistency across repeats | 0% (0 repeated samples) |
| latency p50 / p95 / max (ms) | 5939 / 17904 / 28479 |
| tokens p50 / p95 / max / mean per call | 1503 / 3091 / 4726 / 1588 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 83% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 96% | PASS |
| A6b | title trace-visible subject | >= 90% | 31% | **FAIL** |
| A6c | title identifiers | = 100% | 27% | **FAIL** |
| A6 | all three title dimensions together | all pass | 23% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 27 |
| exception | 3 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 30 | 100% | 8% | 23% | 100% | 83% | 100% | 0 |

- average: skip 100%, title 8%, title contract 23%, facts P/R 100%/100%, facts actionable 83%
- worst run: skip 100%, title 8%, title contract 23%, facts P/R 100%/100%, facts actionable 83%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| feature | 8 | 8/8 | 6/8 | 0/8 |
| config-change | 1 | 1/1 | 1/1 | 0/1 |
| refactor | 3 | 2/3 | 0/3 | 0/3 |
| docs-update | 11 | 9/11 | 9/11 | 2/11 |
| test-add | 1 | 0/1 | 0/1 | 0/1 |
| security-fix | 5 | 5/5 | 1/5 | 0/5 |
| bug-fix | 1 | 1/1 | 1/1 | 0/1 |

## Failure clusters (first run)

| cluster | misses | share of scored (26) |
|---|---|---|
| identifier missing | 19 | 73% |
| action class wrong | 1 | 4% |
| subject wrong | 18 | 69% |
| type wrong | 8 | 31% |

### identifier missing (19)

- real-1fe4ffb124 — expected identifiers [extraction-quality-improvement-plan.md], title 'Updated extraction test floor to 472 and documented Round 9 attribution'
- real-d13e2c90cd — expected identifiers [2026-11-03_real-history-golden-eval-minimax.md], title 'Added real-history golden evaluation replay in GoldenSetRealReplayTest'
- real-3b4f737562 — expected identifiers [minimax-after-v9.json], title 'Added MiniMax after-v9 evaluation fixtures, metrics, and documentation'
- real-77ba40df77 — expected identifiers [ExtractionConsistency], title 'Added extraction consistency voting and bounded retry configuration'
- real-f0d346eab1 — expected identifiers [golden-tool-use-real.json], title 'Added real-history golden set evaluation with generator and evaluator selection'
- real-a9c8452967 — expected identifiers [SearchSimilarityEvaluator], title 'Added semantic relevance floor evaluation with SearchSimilarityReplayTest'
- real-db8ac0287e — expected identifiers [continuation.txt], title 'Updated Fixed verb guidance in backend prompt resources'
- real-9e6612fdf9 — expected identifiers [continuation.txt], title 'Restricted Fixed label to non-security code in prompt files'
- real-e352ba3da3 — expected identifiers [longcat-after-v7.json], title 'Added v7 model evaluation fixtures and archived reports'
- real-112de33e23 — expected identifiers [continuation.txt], title 'Documented replacement naming rule in prompt resources'
- real-28edef5a35 — expected identifiers [longcat-after-v6.json], title 'Added v6 model output quality fixtures and evaluations'
- real-6d6df2c321 — expected identifiers [continuation.txt], title 'Hardened prompt output and identifier-search handling in prompt templates'
- real-d3431639a9 — expected identifiers [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md], title 'Documented Round 4 quality evaluation results in extraction-quality-improvement-plan.md'
- real-7093cb5c56 — expected identifiers [EmbeddingBackfillConcurrencyDatabaseTest], title 'Added concurrency and service tests for embedding backfill'
- real-f3d12860ff — expected identifiers [LogsControllerTest], title 'Fixed log directory reflection and endpoint parameter handling in LogsController'
- real-3409dda60c — expected identifiers [conversation-memory-scope-refactor-plan.md], title 'Documented conversation memory scope refactor commit range in plan'
- real-a8cc1754bc — expected identifiers [PendingMessageRepository], title 'Refactored pending message deduplication across conversation scope'
- real-d958416fdc — expected identifiers [patrol-state.json], title 'Updated patrol rotation and architecture API documentation'
- real-9b424d0d02 — expected identifiers [API-zh-CN.md], title 'Documented GET /api/settings default corrections in docs/API.md'

### action class wrong (1)

- real-f6a1b89e41 — expected [modify], got 'create' from title 'Added round-10 real-history golden set evidence in golden-tool-use-real.json'

### subject wrong (18)

- real-1fe4ffb124 — expected subject tokens [extraction-quality-improvement-plan.md], title 'Updated extraction test floor to 472 and documented Round 9 attribution'
- real-d13e2c90cd — expected subject tokens [2026-11-03_real-history-golden-eval-minimax.md], title 'Added real-history golden evaluation replay in GoldenSetRealReplayTest'
- real-3b4f737562 — expected subject tokens [minimax-after-v9.json], title 'Added MiniMax after-v9 evaluation fixtures, metrics, and documentation'
- real-f0d346eab1 — expected subject tokens [golden-tool-use-real.json], title 'Added real-history golden set evaluation with generator and evaluator selection'
- real-a9c8452967 — expected subject tokens [SearchSimilarityEvaluator], title 'Added semantic relevance floor evaluation with SearchSimilarityReplayTest'
- real-db8ac0287e — expected subject tokens [continuation.txt], title 'Updated Fixed verb guidance in backend prompt resources'
- real-9e6612fdf9 — expected subject tokens [continuation.txt], title 'Restricted Fixed label to non-security code in prompt files'
- real-e352ba3da3 — expected subject tokens [longcat-after-v7.json], title 'Added v7 model evaluation fixtures and archived reports'
- real-112de33e23 — expected subject tokens [continuation.txt], title 'Documented replacement naming rule in prompt resources'
- real-28edef5a35 — expected subject tokens [longcat-after-v6.json], title 'Added v6 model output quality fixtures and evaluations'
- real-6d6df2c321 — expected subject tokens [continuation.txt], title 'Hardened prompt output and identifier-search handling in prompt templates'
- real-d3431639a9 — expected subject tokens [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md], title 'Documented Round 4 quality evaluation results in extraction-quality-improvement-plan.md'
- real-7093cb5c56 — expected subject tokens [EmbeddingBackfillConcurrencyDatabaseTest], title 'Added concurrency and service tests for embedding backfill'
- real-f3d12860ff — expected subject tokens [LogsControllerTest], title 'Fixed log directory reflection and endpoint parameter handling in LogsController'
- real-3409dda60c — expected subject tokens [conversation-memory-scope-refactor-plan.md], title 'Documented conversation memory scope refactor commit range in plan'
- real-a8cc1754bc — expected subject tokens [PendingMessageRepository], title 'Refactored pending message deduplication across conversation scope'
- real-d958416fdc — expected subject tokens [patrol-state.json], title 'Updated patrol rotation and architecture API documentation'
- real-9b424d0d02 — expected subject tokens [API-zh-CN.md], title 'Documented GET /api/settings default corrections in docs/API.md'

### type wrong (8)

- real-f6a1b89e41 — expected 'change', got 'feature' (title 'Added round-10 real-history golden set evidence in golden-tool-use-real.json')
- real-77ba40df77 — expected 'refactor', got 'feature' (title 'Added extraction consistency voting and bounded retry configuration')
- real-f0d346eab1 — expected 'change', got 'feature' (title 'Added real-history golden set evaluation with generator and evaluator selection')
- real-9e6612fdf9 — expected 'bugfix', got 'change' (title 'Restricted Fixed label to non-security code in prompt files')
- real-7093cb5c56 — expected 'bugfix', got 'feature' (title 'Added concurrency and service tests for embedding backfill')
- real-f7b865efe5 — expected 'bugfix', got 'change' (title 'Changed --color-bg-header to #f8f6f2 in backend/src/main/resources/static/index.html')
- real-a8cc1754bc — expected 'bugfix', got 'refactor' (title 'Refactored pending message deduplication across conversation scope')
- real-cf667b4d8b — expected 'refactor', got 'change' (title 'Updated observation feedback Javadocs in ObservationFeedbackEntity.java')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| real-f6a1b89e41 | feature | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 50%/100% | 6845 |
| real-1fe4ffb124 | config-change | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 5258 |
| real-d13e2c90cd | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 75% | 50%/100% | 4285 |
| real-3b4f737562 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 12089 |
| real-77ba40df77 | refactor | observation | observation | pass | 100% | MISS (subject hit, id MISS, action hit) | miss | MISS | 100%/100% | 75% | 0%/0% | 7321 |
| real-f0d346eab1 | feature | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 50%/100% | 7364 |
| real-3a2d426fdf | refactor | observation | call-failed | FAIL | - | - | - | - | - | - | - | 28479 |
| real-864dadf51b | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 67% | 50%/100% | 3842 |
| real-b064308244 | docs-update | observation | call-failed | FAIL | - | - | - | - | - | - | - | 9251 |
| real-30a956e462 | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 3705 |
| real-ba86bf2588 | test-add | observation | call-failed | FAIL | - | - | - | - | - | - | - | 9909 |
| real-a9c8452967 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 17904 |
| real-db8ac0287e | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 2489 |
| real-9e6612fdf9 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 75% | 0%/0% | 6166 |
| real-f43cfd6f44 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 6067 |
| real-e352ba3da3 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 7670 |
| real-112de33e23 | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 3379 |
| real-28edef5a35 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 6948 |
| real-6d6df2c321 | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 4105 |
| real-d3431639a9 | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 5359 |
| real-7093cb5c56 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 50%/100% | 3864 |
| real-f3d12860ff | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 4962 |
| real-3409dda60c | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 67% | 0%/0% | 6203 |
| real-f7b865efe5 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 5939 |
| real-a8cc1754bc | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 75% | 0%/0% | 3482 |
| real-e2c887775a | bug-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 0% | 50%/100% | 4433 |
| real-cf667b4d8b | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 0% | 0%/0% | 6749 |
| real-a0e77ca4ee | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 40% | 0%/0% | 3079 |
| real-d958416fdc | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 80% | 0%/0% | 11244 |
| real-9b424d0d02 | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 4839 |

## Problems

- real-f6a1b89e41: title coverage 50% (expected keywords [change, golden-tool-use-real.json])
- real-f6a1b89e41: title action [modify] missing: title leads 'create' (got 'Added round-10 real-history golden set evidence in golden-tool-use-real.json')
- real-f6a1b89e41: title verb class 'change' missing (got 'Added round-10 real-history golden set evidence in golden-tool-use-real.json')
- real-f6a1b89e41: type 'feature' != expected 'change'
- real-1fe4ffb124: title lost identifiers [extraction-quality-improvement-plan.md] (got 'Updated extraction test floor to 472 and documented Round 9 attribution')
- real-1fe4ffb124: title verb class 'document' missing (got 'Updated extraction test floor to 472 and documented Round 9 attribution')
- real-1fe4ffb124: title subject [extraction-quality-improvement-plan.md] missing (got 'Updated extraction test floor to 472 and documented Round 9 attribution')
- real-1fe4ffb124: title trace-visible subject [extraction-quality-improvement-plan.md] missing (got 'Updated extraction test floor to 472 and documented Round 9 attribution')
- real-d13e2c90cd: title lost identifiers [2026-11-03_real-history-golden-eval-minimax.md] (got 'Added real-history golden evaluation replay in GoldenSetRealReplayTest')
- real-d13e2c90cd: title verb class 'document' missing (got 'Added real-history golden evaluation replay in GoldenSetRealReplayTest')
- real-d13e2c90cd: title subject [2026-11-03_real-history-golden-eval-minimax.md] missing (got 'Added real-history golden evaluation replay in GoldenSetRealReplayTest')
- real-d13e2c90cd: title trace-visible subject [2026-11-03_real-history-golden-eval-minimax.md] missing (got 'Added real-history golden evaluation replay in GoldenSetRealReplayTest')
- real-3b4f737562: title lost identifiers [minimax-after-v9.json] (got 'Added MiniMax after-v9 evaluation fixtures, metrics, and documentation')
- real-3b4f737562: title verb class 'document' missing (got 'Added MiniMax after-v9 evaluation fixtures, metrics, and documentation')
- real-3b4f737562: title subject [minimax-after-v9.json] missing (got 'Added MiniMax after-v9 evaluation fixtures, metrics, and documentation')
- real-3b4f737562: title trace-visible subject [minimax-after-v9.json] missing (got 'Added MiniMax after-v9 evaluation fixtures, metrics, and documentation')
- real-77ba40df77: title lost identifiers [ExtractionConsistency] (got 'Added extraction consistency voting and bounded retry configuration')
- real-77ba40df77: type 'feature' != expected 'refactor'
- real-77ba40df77: concepts recall 0% (expected [what-changed], got [pattern, trade-off])
- real-f0d346eab1: title lost identifiers [golden-tool-use-real.json] (got 'Added real-history golden set evaluation with generator and evaluator selection')
- real-f0d346eab1: title subject [golden-tool-use-real.json] missing (got 'Added real-history golden set evaluation with generator and evaluator selection')
- real-f0d346eab1: title trace-visible subject [golden-tool-use-real.json] missing (got 'Added real-history golden set evaluation with generator and evaluator selection')
- real-f0d346eab1: type 'feature' != expected 'change'
- real-3a2d426fdf: call failed: IllegalStateException: all attempts returned no content
- real-864dadf51b: title coverage 50% (expected keywords [document, goldensetscorer])
- real-864dadf51b: title verb class 'document' missing (got 'Extended GoldenSetScorer tool evidence extraction in GoldenSetScorer.java')
- real-b064308244: call failed: IllegalStateException: all attempts returned no content
- real-30a956e462: validation rejected the observation: title too long: 103 chars > 100
- real-ba86bf2588: call failed: IllegalStateException: all attempts returned no content
- real-a9c8452967: title lost identifiers [SearchSimilarityEvaluator] (got 'Added semantic relevance floor evaluation with SearchSimilarityReplayTest')
- real-a9c8452967: title verb class 'document' missing (got 'Added semantic relevance floor evaluation with SearchSimilarityReplayTest')
- real-a9c8452967: title subject [SearchSimilarityEvaluator] missing (got 'Added semantic relevance floor evaluation with SearchSimilarityReplayTest')
- real-a9c8452967: title trace-visible subject [SearchSimilarityEvaluator] missing (got 'Added semantic relevance floor evaluation with SearchSimilarityReplayTest')
- real-db8ac0287e: title lost identifiers [continuation.txt] (got 'Updated Fixed verb guidance in backend prompt resources')
- real-db8ac0287e: title verb class 'document' missing (got 'Updated Fixed verb guidance in backend prompt resources')
- real-db8ac0287e: title subject [continuation.txt] missing (got 'Updated Fixed verb guidance in backend prompt resources')
- real-db8ac0287e: title trace-visible subject [continuation.txt] missing (got 'Updated Fixed verb guidance in backend prompt resources')
- real-db8ac0287e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9e6612fdf9: title lost identifiers [continuation.txt] (got 'Restricted Fixed label to non-security code in prompt files')
- real-9e6612fdf9: title verb class 'document' missing (got 'Restricted Fixed label to non-security code in prompt files')
- real-9e6612fdf9: title subject [continuation.txt] missing (got 'Restricted Fixed label to non-security code in prompt files')
- real-9e6612fdf9: title trace-visible subject [continuation.txt] missing (got 'Restricted Fixed label to non-security code in prompt files')
- real-9e6612fdf9: type 'change' != expected 'bugfix'
- real-9e6612fdf9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e352ba3da3: title lost identifiers [longcat-after-v7.json] (got 'Added v7 model evaluation fixtures and archived reports')
- real-e352ba3da3: title verb class 'document' missing (got 'Added v7 model evaluation fixtures and archived reports')
- real-e352ba3da3: title subject [longcat-after-v7.json] missing (got 'Added v7 model evaluation fixtures and archived reports')
- real-e352ba3da3: title trace-visible subject [longcat-after-v7.json] missing (got 'Added v7 model evaluation fixtures and archived reports')
- real-112de33e23: title lost identifiers [continuation.txt] (got 'Documented replacement naming rule in prompt resources')
- real-112de33e23: title subject [continuation.txt] missing (got 'Documented replacement naming rule in prompt resources')
- real-112de33e23: title trace-visible subject [continuation.txt] missing (got 'Documented replacement naming rule in prompt resources')
- real-28edef5a35: title lost identifiers [longcat-after-v6.json] (got 'Added v6 model output quality fixtures and evaluations')
- real-28edef5a35: title verb class 'document' missing (got 'Added v6 model output quality fixtures and evaluations')
- real-28edef5a35: title subject [longcat-after-v6.json] missing (got 'Added v6 model output quality fixtures and evaluations')
- real-28edef5a35: title trace-visible subject [longcat-after-v6.json] missing (got 'Added v6 model output quality fixtures and evaluations')
- real-6d6df2c321: title lost identifiers [continuation.txt] (got 'Hardened prompt output and identifier-search handling in prompt templates')
- real-6d6df2c321: title verb class 'document' missing (got 'Hardened prompt output and identifier-search handling in prompt templates')
- real-6d6df2c321: title subject [continuation.txt] missing (got 'Hardened prompt output and identifier-search handling in prompt templates')
- real-6d6df2c321: title trace-visible subject [continuation.txt] missing (got 'Hardened prompt output and identifier-search handling in prompt templates')
- real-6d6df2c321: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d3431639a9: title lost identifiers [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] (got 'Documented Round 4 quality evaluation results in extraction-quality-improvement-plan.md')
- real-d3431639a9: title subject [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] missing (got 'Documented Round 4 quality evaluation results in extraction-quality-improvement-plan.md')
- real-d3431639a9: title trace-visible subject [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] missing (got 'Documented Round 4 quality evaluation results in extraction-quality-improvement-plan.md')
- real-d3431639a9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-7093cb5c56: title lost identifiers [EmbeddingBackfillConcurrencyDatabaseTest] (got 'Added concurrency and service tests for embedding backfill')
- real-7093cb5c56: title verb class 'harden' missing (got 'Added concurrency and service tests for embedding backfill')
- real-7093cb5c56: title subject [EmbeddingBackfillConcurrencyDatabaseTest] missing (got 'Added concurrency and service tests for embedding backfill')
- real-7093cb5c56: title trace-visible subject [EmbeddingBackfillConcurrencyDatabaseTest] missing (got 'Added concurrency and service tests for embedding backfill')
- real-7093cb5c56: type 'feature' != expected 'bugfix'
- real-f3d12860ff: title lost identifiers [LogsControllerTest] (got 'Fixed log directory reflection and endpoint parameter handling in LogsController')
- real-f3d12860ff: title verb class 'harden' missing (got 'Fixed log directory reflection and endpoint parameter handling in LogsController')
- real-f3d12860ff: title subject [LogsControllerTest] missing (got 'Fixed log directory reflection and endpoint parameter handling in LogsController')
- real-f3d12860ff: title trace-visible subject [LogsControllerTest] missing (got 'Fixed log directory reflection and endpoint parameter handling in LogsController')
- real-3409dda60c: title lost identifiers [conversation-memory-scope-refactor-plan.md] (got 'Documented conversation memory scope refactor commit range in plan')
- real-3409dda60c: title subject [conversation-memory-scope-refactor-plan.md] missing (got 'Documented conversation memory scope refactor commit range in plan')
- real-3409dda60c: title trace-visible subject [conversation-memory-scope-refactor-plan.md] missing (got 'Documented conversation memory scope refactor commit range in plan')
- real-3409dda60c: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-f7b865efe5: title coverage 50% (expected keywords [harden, index.html])
- real-f7b865efe5: title verb class 'harden' missing (got 'Changed --color-bg-header to #f8f6f2 in backend/src/main/resources/static/index.html')
- real-f7b865efe5: type 'change' != expected 'bugfix'
- real-f7b865efe5: concepts recall 0% (expected [problem-solution], got [what-changed, trade-off])
- real-a8cc1754bc: title lost identifiers [PendingMessageRepository] (got 'Refactored pending message deduplication across conversation scope')
- real-a8cc1754bc: title verb class 'remove' missing (got 'Refactored pending message deduplication across conversation scope')
- real-a8cc1754bc: title subject [PendingMessageRepository] missing (got 'Refactored pending message deduplication across conversation scope')
- real-a8cc1754bc: title trace-visible subject [PendingMessageRepository] missing (got 'Refactored pending message deduplication across conversation scope')
- real-a8cc1754bc: type 'refactor' != expected 'bugfix'
- real-a8cc1754bc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e2c887775a: title coverage 50% (expected keywords [remove, agents.md])
- real-e2c887775a: title verb class 'remove' missing (got 'Fixed context endpoint contract to /api/context/inject in AGENTS.md')
- real-cf667b4d8b: title coverage 50% (expected keywords [remove, observationfeedbackentity])
- real-cf667b4d8b: title verb class 'remove' missing (got 'Updated observation feedback Javadocs in ObservationFeedbackEntity.java')
- real-cf667b4d8b: type 'change' != expected 'refactor'
- real-cf667b4d8b: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a0e77ca4ee: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d958416fdc: title lost identifiers [patrol-state.json] (got 'Updated patrol rotation and architecture API documentation')
- real-d958416fdc: title verb class 'remove' missing (got 'Updated patrol rotation and architecture API documentation')
- real-d958416fdc: title subject [patrol-state.json] missing (got 'Updated patrol rotation and architecture API documentation')
- real-d958416fdc: title trace-visible subject [patrol-state.json] missing (got 'Updated patrol rotation and architecture API documentation')
- real-d958416fdc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9b424d0d02: title lost identifiers [API-zh-CN.md] (got 'Documented GET /api/settings default corrections in docs/API.md')
- real-9b424d0d02: title verb class 'remove' missing (got 'Documented GET /api/settings default corrections in docs/API.md')
- real-9b424d0d02: title subject [API-zh-CN.md] missing (got 'Documented GET /api/settings default corrections in docs/API.md')
- real-9b424d0d02: title trace-visible subject [API-zh-CN.md] missing (got 'Documented GET /api/settings default corrections in docs/API.md')
- real-9b424d0d02: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])

## Raw answers behind problems

### real-f6a1b89e41

```text
<observation>   <type>feature</type>   <title>Added round-10 real-history golden set evidence in golden-tool-use-real.json</title>   <subtitle>Expanded the real Git golden dataset to 60 mechanically valid samples and preserved model-title failure evidence.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json changed version from "real-git-2026-11-03" to "real-gi... [truncated]
```

### real-1fe4ffb124

```text
<observation>   <type>change</type>   <title>Updated extraction test floor to 472 and documented Round 9 attribution</title>   <subtitle>Verification thresholds now require 472 tests, with Round 9 documenting complete identifier-miss attribution.</subtitle>   <facts>     <fact>scripts/test-baseline.json sets "total" to 472.</fact>     <fact>scripts/verify-local.sh documents VERIFY_MIN_TESTS=472 wi... [truncated]
```

### real-d13e2c90cd

```text
<observation>   <type>feature</type>   <title>Added real-history golden evaluation replay in GoldenSetRealReplayTest</title>   <subtitle>Added a 36-sample real golden set, replay test, expected metrics, and archived evaluation documentation.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetRealReplayTest.java defines a real-history golden replay test.</fac... [truncated]
```

### real-3b4f737562

```text
<observation>   <type>feature</type>   <title>Added MiniMax after-v9 evaluation fixtures, metrics, and documentation</title>   <subtitle>Evaluation now captures a full model-output quality fixture, derived metrics, and supporting evaluation documentation.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/minimax-after-v9.json contains 2096 added lines of evaluation fixture da... [truncated]
```

### real-77ba40df77

```text
<observation>   <type>feature</type>   <title>Added extraction consistency voting and bounded retry configuration</title>   <subtitle>ExtractionConsistency now supports repeated tool traces, while AgentService integrates consensus results and application.yml configures attempts.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/service/ExtractionConsistency.java defines ... [truncated]
```

### real-f0d346eab1

```text
<observation>   <type>feature</type>   <title>Added real-history golden set evaluation with generator and evaluator selection</title>   <subtitle>Real tool-use history can now be mechanically generated, selected, and scored alongside existing evaluation data.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json contains 1097 lines of real-history golden evaluati... [truncated]
```

### real-3a2d426fdf

```text
(no raw answer: the call failed before returning)
```

### real-864dadf51b

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetScorer tool evidence extraction in GoldenSetScorer.java</title>   <subtitle>Golden-set scoring now captures tool input alongside tool names for richer evidence.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java now extracts both sample.toolName and sample.toolInput into evidence.</f... [truncated]
```

### real-b064308244

```text
(no raw answer: the call failed before returning)
```

### real-30a956e462

```text
<observation>   <type>change</type>   <title>Documented Round 8 insertion-scope contract and security verb in extraction-quality-improvement-plan.md</title>   <subtitle>Round 8 documents insertion-scope rules, removes coverage, and records a reverted security verb.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md now contains the heading "### 7.14 Round 8 — inserti... [truncated]
```

### real-ba86bf2588

```text
(no raw answer: the call failed before returning)
```

### real-a9c8452967

```text
<observation>   <type>feature</type>   <title>Added semantic relevance floor evaluation with SearchSimilarityReplayTest</title>   <subtitle>New evaluator, replay test, fixtures, corpus, metrics, and archived evaluation documentation establish semantic-search quality baselines.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/SearchSimilarityEvaluator.java adds a 41... [truncated]
```

### real-db8ac0287e

```text
<observation>   <type>change</type>   <title>Updated Fixed verb guidance in backend prompt resources</title>   <subtitle>Prompt guidance now applies the Fixed verb to security defects without the non-security restriction.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt now defines Fixed as a defect that already produced wrong or failing behaviour.</fact>     <fac... [truncated]
```

### real-9e6612fdf9

```text
<observation>   <type>change</type>   <title>Restricted Fixed label to non-security code in prompt files</title>   <subtitle>Prompt guidance and golden-set scoring now distinguish non-security fixes from security hardening.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt now defines “Fixed” as a defect fix in non-security code.</fact>     <fact>backend/src/main/r... [truncated]
```

### real-e352ba3da3

```text
<observation>   <type>feature</type>   <title>Added v7 model evaluation fixtures and archived reports</title>   <subtitle>Longcat and MiniMax evaluation outputs, metrics, and reports now support reproducible after-v7 quality checks.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v7.json contains 656 lines of LongCat evaluation output.</fact>     <fact>backend... [truncated]
```

### real-112de33e23

```text
<observation>   <type>change</type>   <title>Documented replacement naming rule in prompt resources</title>   <subtitle>Both initialization and continuation prompts require replacements to retain the replaced identifier and destination file.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt contains the rule “A replacement keeps BOTH names: the literal or key that ... [truncated]
```

### real-28edef5a35

```text
<observation>   <type>feature</type>   <title>Added v6 model output quality fixtures and evaluations</title>   <subtitle>Longcat and MiniMax v6 outputs now have reusable JSON fixtures, metrics, and archived evaluation reports.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v6.json contains 656 lines of LongCat v6 model output fixture data.</fact>     <fact>ba... [truncated]
```

### real-6d6df2c321

```text
<observation>   <type>change</type>   <title>Hardened prompt output and identifier-search handling in prompt templates</title>   <subtitle>Prompt templates now enforce XML-only observations and define precise skip behavior for identifier searches.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt removed 11 lines and added 71 lines, replacing the failed-skip instru... [truncated]
```

### real-d3431639a9

```text
<observation>   <type>change</type>   <title>Documented Round 4 quality evaluation results in extraction-quality-improvement-plan.md</title>   <subtitle>Archived model-output and session-summary evaluations now capture contract titles, grounded summaries, retrieval floors, and repair claims.</subtitle>   <facts>     <fact>docs/archive/2026-11-03_model-output-quality-eval-minimax-after-v4-contract.... [truncated]
```

### real-7093cb5c56

```text
<observation>   <type>feature</type>   <title>Added concurrency and service tests for embedding backfill</title>   <subtitle>New database concurrency, service behavior, log sanitization, and controllable AI model tests were added.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/repository/EmbeddingBackfillConcurrencyDatabaseTest.java adds 333 lines covering embedding b... [truncated]
```

### real-f3d12860ff

```text
<observation>   <type>bugfix</type>   <title>Fixed log directory reflection and endpoint parameter handling in LogsController</title>   <subtitle>LogsController tests now set logDir through a helper while the controller validates and documents log-reading parameters.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/controller/LogsControllerTest.java replaces direct logD... [truncated]
```

### real-3409dda60c

```text
<observation>   <type>change</type>   <title>Documented conversation memory scope refactor commit range in plan</title>   <subtitle>The implementation range now identifies nine commits and links to the document’s commit list.</subtitle>   <facts>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md defines the implementation commit range as `325c817..f7b865e`.</fact>     <fact>docs/dra... [truncated]
```

### real-f7b865efe5

```text
<observation>   <type>change</type>   <title>Changed --color-bg-header to #f8f6f2 in backend/src/main/resources/static/index.html</title>   <subtitle>The static viewer UI received a warmer header background and an updated bundled frontend.</subtitle>   <facts>     <fact>backend/src/main/resources/static/index.html changed --color-bg-header from #f6f8fa to #f8f6f2.</fact>     <fact>backend/src/main... [truncated]
```

### real-a8cc1754bc

```text
<observation>   <type>refactor</type>   <title>Refactored pending message deduplication across conversation scope</title>   <subtitle>Repository and service code now uses conversation-scoped deduplication and updated query identifiers.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/service/AgentService.java now describes deduplication as scoped to the same conversatio... [truncated]
```

### real-e2c887775a

```text
<observation>   <type>bugfix</type>   <title>Fixed context endpoint contract to /api/context/inject in AGENTS.md</title>   <subtitle>AGENTS.md now points updateFiles to the endpoint that actually returns the documented field.</subtitle>   <facts>     <fact>AGENTS.md maps `/api/context/inject` with `updateFiles` to `proxy/proxy.js`.</fact>     <fact>AGENTS.md previously mapped `/api/context/generat... [truncated]
```

### real-cf667b4d8b

```text
<observation>   <type>change</type>   <title>Updated observation feedback Javadocs in ObservationFeedbackEntity.java</title>   <subtitle>Adjusted entity and repository documentation without changing observable application behavior.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/entity/ObservationFeedbackEntity.java changed the Javadoc opening marker from '/**' to '*'.... [truncated]
```

### real-a0e77ca4ee

```text
<observation>   <type>change</type>   <title>Removed drifting Lines column from TESTING.md and synchronized patrol metadata</title>   <subtitle>English and Chinese testing tables now share the same two-column format, with patrol state updated.</subtitle>   <facts>     <fact>docs/TESTING.md changed the script table header from `| Script | Description | Lines |` to `| Script | Description |`.</fact>... [truncated]
```

### real-d958416fdc

```text
<observation>   <type>change</type>   <title>Updated patrol rotation and architecture API documentation</title>   <subtitle>Patrol state now tracks Backend as the latest completed review, and API documentation reflects current backend behavior.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json sets "lastDirection" to "backend" instead of "demo".</fact>     <fact>docs/drafts/patrol-rotat... [truncated]
```

### real-9b424d0d02

```text
<observation>   <type>change</type>   <title>Documented GET /api/settings default corrections in docs/API.md</title>   <subtitle>English and Chinese API references now align six settings examples with AppSettings.toMap() defaults.</subtitle>   <facts>     <fact>docs/API.md records six GET /api/settings example corrections in version 0.1.0-beta+42.</fact>     <fact>docs/API-zh-CN.md records six GET... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
