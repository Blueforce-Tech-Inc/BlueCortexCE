golden set: real (60 samples)

# Model output quality evaluation

- Date: 2026-11-04
- Phase: r13-final
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 60 unique; runs scored: 1; key repeat: 1; real calls: 60 (cap none); duration: 1082188 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 95% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 10/0/0/50 |
| format errors | 0 |
| validation rejections | 3 |
| call failures | 0 |
| call success rate | 100% (0/60 failed) |
| title exact-hit rate (legacy) | 36% |
| title normalized hit rate (legacy) | 36% |
| title action-class hit rate (A6) | 87% (47 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 38% |
| title subject hit rate (legacy, diagnostic) | 100% |
| title trace-visible subject hit rate (A6b) | 100% (47 scored, 0 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 87% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 87% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 38% |
| title identifier hit rate | 100% |
| type accuracy (raw) | 72% |
| type accuracy (auditable labels only) | 81% (5 unverifiable label(s) excluded) |
| facts keyword precision (legacy) / recall | 100% / 100% |
| facts informative precision | 88% |
| facts trace-support rate | 100% |
| concepts precision / recall | 27% / 53% |
| skip consistency across repeats | 0% (0 repeated samples) |
| latency p50 / p95 / max (ms) | 15424 / 38925 / 81620 |
| tokens p50 / p95 / max / mean per call | 5682 / 7128 / 9209 / 5741 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 88% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 87% | PASS |
| A6b | title trace-visible subject | >= 90% | 100% | PASS |
| A6c | title identifiers | = 100% | 100% | PASS |
| A6 | all three title dimensions together | all pass | 87% | PASS |
| A13 | provider call success rate | >= 99% (at most 1 failed call(s) per run) | 100% (0/60 failed) | PASS |

## Status distribution

| status | calls |
|---|---|
| 200 | 60 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 60 | 100% | 36% | 87% | 100% | 88% | 100% | 0 |

- average: skip 100%, title 36%, title contract 87%, facts P/R 100%/100%, facts actionable 88%
- worst run: skip 100%, title 36%, title contract 87%, facts P/R 100%/100%, facts actionable 88%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| feature | 12 | 11/12 | 9/12 | 3/12 |
| config-change | 1 | 1/1 | 1/1 | 1/1 |
| refactor | 2 | 2/2 | 0/2 | 0/2 |
| docs-update | 20 | 19/20 | 19/20 | 10/20 |
| test-add | 2 | 2/2 | 2/2 | 1/2 |
| security-fix | 7 | 7/7 | 3/7 | 0/7 |
| bug-fix | 6 | 5/6 | 0/6 | 2/6 |
| diagnose | 10 | 10/10 | 0/10 | 0/10 |

## Unverifiable type labels (excluded from the auditable type accuracy)

5 sample(s) expect a type the recorded trace cannot evidence:

- real-e2c887775a: expected bugfix but the trace shows no defect or security evidence
- real-cf667b4d8b: expected bugfix but the trace shows no defect or security evidence
- real-c67e7a5ad3: expected bugfix but the trace shows no defect or security evidence
- real-bdf72f2509: expected bugfix but the trace shows no defect or security evidence
- real-95de35b2ea: expected bugfix but the trace shows no defect or security evidence

## Failure clusters (first run)

| cluster | misses | share of scored (47) |
|---|---|---|
| identifier missing | 0 | 0% |
| action class wrong | 6 | 13% |
| subject wrong | 0 | 0% |
| type wrong | 13 | 28% |

### action class wrong (6)

- real-f6a1b89e41 — expected [modify], got 'null' from title 'Expanded golden tool-use evaluation set to 60 samples in golden-tool-use-real.json'
- real-3a2d426fdf — expected [modify], got 'create' from title 'Added promptTokens tracking and EVAL_REPEAT_ALL to GoldenSetScorer'
- real-9e6612fdf9 — expected [document, secure, modify], got 'null' from title 'Clarified Fixed verb scope to non-security code in continuation.txt'
- real-85480a720e — expected [remove, document], got 'modify' from title 'Updated DB_HOST environment variable to DB_NAME in TESTING-zh-CN.md'
- real-0fd4ffcc31 — expected [modify], got 'create' from title 'Added must-record skip tracking with skipFp in GoldenSetScorer'
- real-63b5502fb9 — expected [secure, create, modify], got 'null' from title 'Expanded conversation memory scope migration in V20__conversation_memory_scope_expand.sql'

### type wrong (13)

- real-f6a1b89e41 — expected 'change', got 'feature' (title 'Expanded golden tool-use evaluation set to 60 samples in golden-tool-use-real.json')
- real-f0d346eab1 — expected 'change', got 'feature' (title 'Added real-history golden tool-use eval set in golden-tool-use-real.json')
- real-3a2d426fdf — expected 'change', got 'feature' (title 'Added promptTokens tracking and EVAL_REPEAT_ALL to GoldenSetScorer')
- real-9e6612fdf9 — expected 'feature', got 'change' (title 'Clarified Fixed verb scope to non-security code in continuation.txt')
- real-f3d12860ff — expected 'change', got 'feature' (title 'Refactored logDir injection using setField helper in LogsControllerTest')
- real-a8cc1754bc — expected 'bugfix', got 'refactor' (title 'Refactored dedup query out of PendingMessageRepository')
- real-e2c887775a — expected 'bugfix', got 'change' (title 'Updated /api/context/generate endpoint to /api/context/inject in AGENTS.md')
- real-cf667b4d8b — expected 'bugfix', got 'change' (title 'Updated feedback comments in ObservationFeedbackEntity')
- real-c67e7a5ad3 — expected 'bugfix', got 'change' (title 'Removed else clause in dto.py')
- real-bdf72f2509 — expected 'bugfix', got 'change' (title 'Removed SDK version User-Agent comment in CortexMemClientImpl')
- real-9a0e90bed5 — expected 'refactor', got 'feature' (title 'Changed project filter to memoryScopeId in ObservationRepository')
- real-63b5502fb9 — expected 'bugfix', got 'feature' (title 'Expanded conversation memory scope migration in V20__conversation_memory_scope_expand.sql')
- real-95de35b2ea — expected 'bugfix', got 'change' (title 'Updated byte-buddy pin rationale comment in pom.xml')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| real-f6a1b89e41 | feature | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 75% | 50%/100% | 23006 |
| real-1fe4ffb124 | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 15424 |
| real-d13e2c90cd | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 26537 |
| real-3b4f737562 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 27425 |
| real-77ba40df77 | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 15044 |
| real-f0d346eab1 | feature | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/100% | 24950 |
| real-3a2d426fdf | refactor | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 50%/100% | 20883 |
| real-864dadf51b | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 80% | 50%/100% | 38925 |
| real-b064308244 | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 7015 |
| real-30a956e462 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 26387 |
| real-ba86bf2588 | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 33% | 50%/100% | 14475 |
| real-a9c8452967 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 17084 |
| real-db8ac0287e | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 15669 |
| real-9e6612fdf9 | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 0%/0% | 16381 |
| real-f43cfd6f44 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 11271 |
| real-e352ba3da3 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 22775 |
| real-112de33e23 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/100% | 15316 |
| real-28edef5a35 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 19055 |
| real-6d6df2c321 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 67% | 0%/0% | 39346 |
| real-d3431639a9 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 28392 |
| real-7093cb5c56 | security-fix | observation | observation | pass | 0% | hit | miss | match | 100%/100% | 100% | 50%/100% | 81620 |
| real-f3d12860ff | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 35915 |
| real-3409dda60c | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 11132 |
| real-f7b865efe5 | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 11687 |
| real-a8cc1754bc | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 60% | 0%/0% | 18748 |
| real-e2c887775a | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 50% | 50%/100% | 36191 |
| real-cf667b4d8b | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 50% | 0%/0% | 43684 |
| real-a0e77ca4ee | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 75% | 0%/0% | 19789 |
| real-d958416fdc | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 80% | 0%/0% | 20405 |
| real-9b424d0d02 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 8157 |
| real-85480a720e | docs-update | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 0%/0% | 8138 |
| real-c67e7a5ad3 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 18663 |
| real-bdf72f2509 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 50% | 0%/0% | 29469 |
| real-8ac2a32980 | bug-fix | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 10246 |
| real-2025b33a95 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2920 |
| real-e8c8b314d6 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2572 |
| real-c42d7a9c5d | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2889 |
| real-b5ae725e2a | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3122 |
| real-9261b542ca | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3081 |
| real-7c557c8d79 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2817 |
| real-1dbccd058e | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3490 |
| real-1f9b5d09e7 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2500 |
| real-ed737be6bb | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3572 |
| real-b2d48b12a1 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2641 |
| real-bc3512ae8d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 16065 |
| real-e02e436bdd | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 10683 |
| real-d44a83721d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 13877 |
| real-0fd4ffcc31 | test-add | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 50%/100% | 33536 |
| real-eee7785ec8 | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 21092 |
| real-9a0e90bed5 | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 75% | 0%/0% | 24168 |
| real-63b5502fb9 | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 0%/0% | 25540 |
| real-d33efbd8ae | feature | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 7811 |
| real-cd6b972cfd | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 33% | 0%/0% | 14443 |
| real-46e467bff6 | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 27552 |
| real-325c817bbf | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 50% | 50%/100% | 9420 |
| real-7149578a88 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 10854 |
| real-95de35b2ea | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 19488 |
| real-53851b4a23 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 14517 |
| real-f4c6765fb9 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 9949 |
| real-67083a7bc1 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 13943 |

## Problems

- real-f6a1b89e41: title coverage 50% (expected keywords [change, golden-tool-use-real.json])
- real-f6a1b89e41: title action [modify] missing: title leads 'null' (got 'Expanded golden tool-use evaluation set to 60 samples in golden-tool-use-real.json')
- real-f6a1b89e41: title verb class 'change' missing (got 'Expanded golden tool-use evaluation set to 60 samples in golden-tool-use-real.json')
- real-f6a1b89e41: type 'feature' != expected 'change'
- real-d13e2c90cd: title coverage 50% (expected keywords [document, 2026-11-03_real-history-golden-eval-minimax.md])
- real-d13e2c90cd: title verb class 'document' missing (got 'Added golden eval replay test suite in 2026-11-03_real-history-golden-eval-minimax.md')
- real-3b4f737562: title coverage 50% (expected keywords [document, minimax-after-v9.json])
- real-3b4f737562: title verb class 'document' missing (got 'Added minimax-after-v9 eval fixture dataset in minimax-after-v9.json')
- real-f0d346eab1: type 'feature' != expected 'change'
- real-3a2d426fdf: title coverage 50% (expected keywords [change, goldensetscorer])
- real-3a2d426fdf: title action [modify] missing: title leads 'create' (got 'Added promptTokens tracking and EVAL_REPEAT_ALL to GoldenSetScorer')
- real-3a2d426fdf: title verb class 'change' missing (got 'Added promptTokens tracking and EVAL_REPEAT_ALL to GoldenSetScorer')
- real-3a2d426fdf: type 'feature' != expected 'change'
- real-864dadf51b: title coverage 50% (expected keywords [document, goldensetscorer])
- real-864dadf51b: title verb class 'document' missing (got 'Extended evidence scoring and marked hit rate legacy in GoldenSetScorer')
- real-b064308244: validation rejected the observation: title too long: 104 chars > 100
- real-30a956e462: concepts recall 0% (expected [problem-solution], got [pattern, what-changed])
- real-a9c8452967: title coverage 50% (expected keywords [document, searchsimilarityevaluator])
- real-a9c8452967: title verb class 'document' missing (got 'Added semantic relevance replay harness in SearchSimilarityEvaluator')
- real-db8ac0287e: title coverage 50% (expected keywords [document, continuation.txt])
- real-db8ac0287e: title verb class 'document' missing (got 'Updated Fixed definition to cover security defects in continuation.txt')
- real-db8ac0287e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9e6612fdf9: title coverage 50% (expected keywords [document, continuation.txt])
- real-9e6612fdf9: title action [document, secure, modify] missing: title leads 'null' (got 'Clarified Fixed verb scope to non-security code in continuation.txt')
- real-9e6612fdf9: title verb class 'document' missing (got 'Clarified Fixed verb scope to non-security code in continuation.txt')
- real-9e6612fdf9: type 'change' != expected 'feature'
- real-9e6612fdf9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e352ba3da3: title coverage 50% (expected keywords [document, longcat-after-v7.json])
- real-e352ba3da3: title verb class 'document' missing (got 'Added model output quality eval fixtures and docs in longcat-after-v7.json')
- real-28edef5a35: title coverage 50% (expected keywords [document, longcat-after-v6.json])
- real-28edef5a35: title verb class 'document' missing (got 'Added model output quality eval fixtures longcat-after-v6.json')
- real-6d6df2c321: title coverage 50% (expected keywords [document, continuation.txt])
- real-6d6df2c321: title verb class 'document' missing (got 'Updated skip-condition guidance in continuation.txt')
- real-6d6df2c321: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d3431639a9: title coverage 50% (expected keywords [document, 2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md])
- real-d3431639a9: title verb class 'document' missing (got 'Added model output quality eval 2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md')
- real-d3431639a9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-7093cb5c56: title coverage 0% (expected keywords [harden, embeddingbackfillconcurrencydatabasetest])
- real-7093cb5c56: title verb class 'harden' missing (got 'Fixed embedding backfill concurrency with EmbeddingBackfillConcurrencyDatabaseTest')
- real-f3d12860ff: title coverage 50% (expected keywords [harden, logscontrollertest])
- real-f3d12860ff: title verb class 'harden' missing (got 'Refactored logDir injection using setField helper in LogsControllerTest')
- real-f3d12860ff: type 'feature' != expected 'change'
- real-f3d12860ff: concepts recall 0% (expected [problem-solution], got [pattern, what-changed])
- real-3409dda60c: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-f7b865efe5: title coverage 50% (expected keywords [harden, index.html])
- real-f7b865efe5: title verb class 'harden' missing (got 'Changed header background color to #f8f6f2 in index.html')
- real-f7b865efe5: concepts recall 0% (expected [problem-solution], got [what-changed, what-changed])
- real-a8cc1754bc: title coverage 50% (expected keywords [remove, pendingmessagerepository])
- real-a8cc1754bc: title verb class 'remove' missing (got 'Refactored dedup query out of PendingMessageRepository')
- real-a8cc1754bc: type 'refactor' != expected 'bugfix'
- real-a8cc1754bc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e2c887775a: title coverage 50% (expected keywords [remove, agents.md])
- real-e2c887775a: title verb class 'remove' missing (got 'Updated /api/context/generate endpoint to /api/context/inject in AGENTS.md')
- real-e2c887775a: type 'change' != expected 'bugfix'
- real-cf667b4d8b: title coverage 50% (expected keywords [remove, observationfeedbackentity])
- real-cf667b4d8b: title verb class 'remove' missing (got 'Updated feedback comments in ObservationFeedbackEntity')
- real-cf667b4d8b: type 'change' != expected 'bugfix'
- real-cf667b4d8b: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a0e77ca4ee: concepts recall 0% (expected [problem-solution], got [pattern, what-changed])
- real-d958416fdc: title coverage 50% (expected keywords [remove, patrol-state.json])
- real-d958416fdc: title verb class 'remove' missing (got 'Updated patrol rotation state to backend in patrol-state.json')
- real-d958416fdc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9b424d0d02: title coverage 50% (expected keywords [remove, api-zh-cn.md])
- real-9b424d0d02: title verb class 'remove' missing (got 'Documented GET /api/settings default value corrections in API-zh-CN.md')
- real-9b424d0d02: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-85480a720e: title coverage 50% (expected keywords [remove, testing-zh-cn.md])
- real-85480a720e: title action [remove, document] missing: title leads 'modify' (got 'Updated DB_HOST environment variable to DB_NAME in TESTING-zh-CN.md')
- real-85480a720e: title verb class 'remove' missing (got 'Updated DB_HOST environment variable to DB_NAME in TESTING-zh-CN.md')
- real-85480a720e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-c67e7a5ad3: type 'change' != expected 'bugfix'
- real-c67e7a5ad3: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-bdf72f2509: type 'change' != expected 'bugfix'
- real-bdf72f2509: concepts recall 0% (expected [problem-solution], got [what-changed, what-changed])
- real-8ac2a32980: validation rejected the observation: title too long: 109 chars > 100
- real-e02e436bdd: title coverage 50% (expected keywords [document, continuation.txt])
- real-e02e436bdd: title verb class 'document' missing (got 'Updated prompt examples section in continuation.txt')
- real-0fd4ffcc31: title coverage 50% (expected keywords [change, goldensetscorer])
- real-0fd4ffcc31: title action [modify] missing: title leads 'create' (got 'Added must-record skip tracking with skipFp in GoldenSetScorer')
- real-0fd4ffcc31: title verb class 'change' missing (got 'Added must-record skip tracking with skipFp in GoldenSetScorer')
- real-9a0e90bed5: title coverage 50% (expected keywords [harden, observationrepository])
- real-9a0e90bed5: title verb class 'harden' missing (got 'Changed project filter to memoryScopeId in ObservationRepository')
- real-9a0e90bed5: type 'feature' != expected 'refactor'
- real-9a0e90bed5: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-63b5502fb9: title coverage 50% (expected keywords [harden, v20__conversation_memory_scope_expand.sql])
- real-63b5502fb9: title action [secure, create, modify] missing: title leads 'null' (got 'Expanded conversation memory scope migration in V20__conversation_memory_scope_expand.sql')
- real-63b5502fb9: title verb class 'harden' missing (got 'Expanded conversation memory scope migration in V20__conversation_memory_scope_expand.sql')
- real-63b5502fb9: type 'feature' != expected 'bugfix'
- real-63b5502fb9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d33efbd8ae: validation rejected the observation: title too long: 148 chars > 100
- real-cd6b972cfd: title coverage 50% (expected keywords [harden, summaryjobservice])
- real-cd6b972cfd: title verb class 'harden' missing (got 'Added SummaryJobService to the backend service layer')
- real-cd6b972cfd: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-46e467bff6: title coverage 50% (expected keywords [harden, conversation-scope-preflight.sql])
- real-46e467bff6: title verb class 'harden' missing (got 'Added conversation-scope preflight validation script conversation-scope-preflight.sql')
- real-46e467bff6: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-325c817bbf: title coverage 50% (expected keywords [document, conversation-memory-scope-refactor-plan.md])
- real-325c817bbf: title verb class 'document' missing (got 'Added Conversation 与 Memory Scope 身份边界重构实施蓝图 in conversation-memory-scope-refactor-plan.md')
- real-95de35b2ea: title coverage 50% (expected keywords [change, pom.xml])
- real-95de35b2ea: type 'change' != expected 'bugfix'
- real-95de35b2ea: concepts recall 0% (expected [problem-solution], got [what-changed, why-it-exists])
- real-53851b4a23: title coverage 50% (expected keywords [document, 2026-10-08_health-check-history-11.md])
- real-53851b4a23: title verb class 'document' missing (got 'Added health check history archive 11 in docs/archive/2026-10-08_health-check-history-11.md')
- real-53851b4a23: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])

## Raw answers behind problems

### real-f6a1b89e41

```text
<observation>   <type>feature</type>   <title>Expanded golden tool-use evaluation set to 60 samples in golden-tool-use-real.json</title>   <subtitle>Round-10 golden set grew from 30 to 60+ mechanically valid samples, with scorer now capturing model-produced titles.</subtitle>   <facts>     <fact>golden-tool-use-real.json version changed from real-git-2026-11-03 to real-git-2026-11-03-r10</fact>   ... [truncated]
```

### real-d13e2c90cd

```text
<observation>   <type>feature</type>   <title>Added golden eval replay test suite in 2026-11-03_real-history-golden-eval-minimax.md</title>   <subtitle>New MiniMax real-history golden eval suite adds a 36-sample replay test with fixtures and metrics.</subtitle>   <facts>     <fact>docs/archive/2026-11-03_real-history-golden-eval-minimax.md added with 439 lines documenting the real (36 samples) gol... [truncated]
```

### real-3b4f737562

```text
<observation>   <type>feature</type>   <title>Added minimax-after-v9 eval fixture dataset in minimax-after-v9.json</title>   <subtitle>New 2096-line model output quality evaluation fixture plus metrics and documentation</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/minimax-after-v9.json added 2096 lines of new fixture data</fact>     <fact>backend/src/test/resources/eval/... [truncated]
```

### real-f0d346eab1

```text
<observation>   <type>feature</type>   <title>Added real-history golden tool-use eval set in golden-tool-use-real.json</title>   <subtitle>A mechanically generated real-history golden tool-use eval set ships with its generator, evaluator selection, and test.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json was added with 1097 lines of mechanically generated ... [truncated]
```

### real-3a2d426fdf

```text
<observation>   <type>feature</type>   <title>Added promptTokens tracking and EVAL_REPEAT_ALL to GoldenSetScorer</title>   <subtitle>Evaluation scorer now tracks prompt tokens and supports repeating all samples for run-to-run variance measurement.</subtitle>   <facts>     <fact>GoldenSetScorer.java added 149 lines including a public final int promptTokens field for token accounting</fact>     <fac... [truncated]
```

### real-864dadf51b

```text
<observation>   <type>feature</type>   <title>Extended evidence scoring and marked hit rate legacy in GoldenSetScorer</title>   <subtitle>GoldenSetScorer now builds richer evidence from tool inputs and the title-subject hit rate is retained only as a diagnostic metric.</subtitle>   <facts>     <fact>GoldenSetScorer.java replaced the single line `String evidence = (sample.toolName == null ? "" : sa... [truncated]
```

### real-b064308244

```text
<observation>   <type>change</type>   <title>Documented rehearsal database gate for extraction quality test in extraction-quality-improvement-plan.md</title>   <subtitle>Updated the plan draft to describe the offline model stand-in test running under the rehearsal database gate.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md previously stated the test "runs in th... [truncated]
```

### real-30a956e462

```text
<observation>   <type>change</type>   <title>Documented Round 8 insertion-scope contract in extraction-quality-improvement-plan.md</title>   <subtitle>Added section 7.14 covering Round 8 changes: insertion-scope contract, coverage removal, and reverted security verb.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md gained 139 inserted lines and 0 deleted lines in t... [truncated]
```

### real-a9c8452967

```text
<observation>   <type>feature</type>   <title>Added semantic relevance replay harness in SearchSimilarityEvaluator</title>   <subtitle>New BGE-M3 semantic relevance evaluation harness with replay test, fixtures, corpus, and metrics</subtitle>   <facts>     <fact>Created backend/src/test/java/com/ablueforce/cortexce/eval/SearchSimilarityEvaluator.java with 411 lines of semantic relevance scoring lo... [truncated]
```

### real-db8ac0287e

```text
<observation>   <type>change</type>   <title>Updated Fixed definition to cover security defects in continuation.txt</title>   <subtitle>Removed the non-security qualifier from the Fixed classification rule in two prompt templates.</subtitle>   <facts>     <fact>continuation.txt Fixed bullet now reads "a defect already produced wrong or failing behaviour" without the "in non-security code" qualifie... [truncated]
```

### real-9e6612fdf9

```text
```xml <observation>   <type>change</type>   <title>Clarified Fixed verb scope to non-security code in continuation.txt</title>   <subtitle>Updated observation taxonomy prompts and golden set evaluators to include extend action and restrict Fixed to non-security defects.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt and init.txt now read "Fixed: a defect alread... [truncated]
```

### real-e352ba3da3

```text
<observation>   <type>feature</type>   <title>Added model output quality eval fixtures and docs in longcat-after-v7.json</title>   <subtitle>Created evaluation fixtures, metrics, and archived reports for longcat and minimax after-v7 model outputs.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v7.json was added with 656 lines of JSON eval fixture data</fact> ... [truncated]
```

### real-28edef5a35

```text
<observation>   <type>feature</type>   <title>Added model output quality eval fixtures longcat-after-v6.json</title>   <subtitle>Created longcat and minimax after-v6 fixture JSON, metrics JSON, and archived evaluation reports.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v6.json was created with 656 lines beginning with `{`.</fact>     <fact>backend/src/tes... [truncated]
```

### real-6d6df2c321

```text
<observation>   <type>change</type>   <title>Updated skip-condition guidance in continuation.txt</title>   <subtitle>Rewrote skip criteria in continuation.txt and init.txt so grep/glob-only searches are not recorded.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt replaced the text `failed), not as a skip.` with the bullet `- only locates where an identifier is u... [truncated]
```

### real-d3431639a9

```text
<observation>   <type>change</type>   <title>Added model output quality eval 2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md</title>   <subtitle>New evaluation archive documents model output quality and summary-chain results for minimax and longcat after the v4 contract.</subtitle>   <facts>     <fact>Created docs/archive/2026-11-03_model-output-quality-eval-minimax-after-v4-cont... [truncated]
```

### real-7093cb5c56

```text
<observation>   <type>bugfix</type>   <title>Fixed embedding backfill concurrency with EmbeddingBackfillConcurrencyDatabaseTest</title>   <subtitle>Added a database-backed concurrency test for the embedding backfill service and adjusted the service to use UUID-based row identifiers.</subtitle>   <facts>     <fact>EmbeddingBackfillService.java removed the comment "Each row is saved on its own; a fa... [truncated]
```

### real-f3d12860ff

```text
<observation>   <type>feature</type>   <title>Refactored logDir injection using setField helper in LogsControllerTest</title>   <subtitle>Replaced raw reflection field access in LogsControllerTest with a reusable setField helper while extending the logs controller.</subtitle>   <facts>     <fact>LogsControllerTest.java replaced the reflection line `LogsController.class.getDeclaredField("logDir")` ... [truncated]
```

### real-3409dda60c

```text
<observation>   <type>change</type>   <title>Documented commit range 325c817..f7b865e in conversation-memory-scope-refactor-plan.md</title>   <subtitle>Updated refactor plan's implementation status from pending to nine completed commits.</subtitle>   <facts>     <fact>The implementation commit range field changed from 待实施 to `325c817..f7b865e`（9 个提交，见文末清单）.</fact>     <fact>The document now refere... [truncated]
```

### real-f7b865efe5

```text
<observation>   <type>change</type>   <title>Changed header background color to #f8f6f2 in index.html</title>   <subtitle>Web UI header background recolored from cool gray to warm off-white in the static index page.</subtitle>   <facts>     <fact>CSS variable `--color-bg-header` in backend/src/main/resources/static/index.html changed from `#f6f8fa` to `#f8f6f2`</fact>     <fact>index.html was rebu... [truncated]
```

### real-a8cc1754bc

```text
<observation>   <type>refactor</type>   <title>Refactored dedup query out of PendingMessageRepository</title>   <subtitle>Moved pending-message deduplication from a repository query into AgentService and aligned session/conversation naming.</subtitle>   <facts>     <fact>PendingMessageRepository.java had 22 lines removed, deleting the dedup query method that checked for an existing message with th... [truncated]
```

### real-e2c887775a

```text
<observation>   <type>change</type>   <title>Updated /api/context/generate endpoint to /api/context/inject in AGENTS.md</title>   <subtitle>Corrected a hard-contract table entry that named the wrong context endpoint for the updateFiles field.</subtitle>   <facts>     <fact>AGENTS.md table row for `updateFiles` changed from `/api/context/generate` to `/api/context/inject` in `proxy/proxy.js`.</fact... [truncated]
```

### real-cf667b4d8b

```text
<observation>   <type>change</type>   <title>Updated feedback comments in ObservationFeedbackEntity</title>   <subtitle>Collapsed ObservationFeedbackEntity Javadoc blocks and reworded repository feedback ordering to most recent first.</subtitle>   <facts>     <fact>ObservationFeedbackEntity.java removed 10 lines and added 9 lines, replacing a `/**` Javadoc opener with `*`.</fact>     <fact>Observa... [truncated]
```

### real-a0e77ca4ee

```text
<observation>   <type>change</type>   <title>Removed drifted Lines column in docs/TESTING.md</title>   <subtitle>Removed the stale "Lines" column from the script table in TESTING.md and synced the Chinese translation and patrol state docs.</subtitle>   <facts>     <fact>docs/TESTING.md table header changed from "| Script | Description | Lines |" to "| Script | Description |", removing 35 line(s) a... [truncated]
```

### real-d958416fdc

```text
<observation>   <type>change</type>   <title>Updated patrol rotation state to backend in patrol-state.json</title>   <subtitle>Documentation-only update advanced patrol rotation from demo to backend and removed the obsolete /api/mode endpoint references.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json changed the `lastDirection` field from `demo` to `backend`.</fact>     <fact>docs/dr... [truncated]
```

### real-9b424d0d02

```text
```xml <observation>   <type>change</type>   <title>Documented GET /api/settings default value corrections in API-zh-CN.md</title>   <subtitle>Updated API changelog to record six corrected example defaults matching AppSettings.toMap().</subtitle>   <facts>     <fact>Added changelog entry for version 0.1.0-beta+42 dated 2026-05-06 in docs/API-zh-CN.md and docs/API.md.</fact>     <fact>Corrected 6 e... [truncated]
```

### real-85480a720e

```text
<observation>   <type>change</type>   <title>Updated DB_HOST environment variable to DB_NAME in TESTING-zh-CN.md</title>   <subtitle>Database configuration table now documents DB_NAME (claude_mem) instead of DB_HOST (127.0.0.1).</subtitle>   <facts>     <fact>docs/TESTING-zh-CN.md environment variable table row changed from `DB_HOST` / `127.0.0.1` to `DB_NAME` / `claude_mem`.</fact>     <fact>docs... [truncated]
```

### real-c67e7a5ad3

```text
<observation>   <type>change</type>   <title>Removed else clause in dto.py</title>   <subtitle>Simplified control flow in dto.py by removing an else block and ignored memory/.dreams in .gitignore.</subtitle>   <facts>     <fact>python-sdk/cortex-mem-python/cortex_mem/dto.py had an else: clause removed, deleting 2 lines with no replacement.</fact>     <fact>.gitignore now contains the entry memory/... [truncated]
```

### real-bdf72f2509

```text
<observation>   <type>change</type>   <title>Removed SDK version User-Agent comment in CortexMemClientImpl</title>   <subtitle>Deleted obsolete Javadoc comment about SDK version User-Agent header maintenance from the client implementation.</subtitle>   <facts>     <fact>Removed the Javadoc comment `/** SDK version for User-Agent header. Update when releasing new SDK versions. */` from `CortexMemCl... [truncated]
```

### real-8ac2a32980

```text
```xml <observation>   <type>change</type>   <title>Documented default port 37777 in backend/Dockerfile and removed stale build-arg sections from deployment docs</title>   <subtitle>Updated Dockerfile comment to name the concrete default port and removed obsolete build argument instructions from both deployment guides.</subtitle>   <facts>     <fact>backend/Dockerfile comment now reads "# Default ... [truncated]
```

### real-e02e436bdd

```text
<observation>   <type>change</type>   <title>Updated prompt examples section in continuation.txt</title>   <subtitle>Replaced a single "Good:" example line with a category-examples header in both continuation.txt and init.txt prompt resources.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt removed the line "- Good: `Fixed UserCache.computeIfAbsent null placehold... [truncated]
```

### real-0fd4ffcc31

```text
<observation>   <type>feature</type>   <title>Added must-record skip tracking with skipFp in GoldenSetScorer</title>   <subtitle>Test suite now separates record-worthy skips (skipFp) from other skips and extends the synonym map.</subtitle>   <facts>     <fact>GoldenSetScorer.java gained 29 lines and removed 2, extending its synonym map with entries such as Map.entry("docs", "doc") and Map.entry("d... [truncated]
```

### real-9a0e90bed5

```text
<observation>   <type>feature</type>   <title>Changed project filter to memoryScopeId in ObservationRepository</title>   <subtitle>Observation queries now filter by memory scope membership instead of projectPath, backed by a new MemoryScopeEntity and MemoryScopeService.</subtitle>   <facts>     <fact>ObservationRepository query changed from `o.projectPath = :project` to `o.memoryScopeId IN (` for ... [truncated]
```

### real-63b5502fb9

```text
<observation>   <type>feature</type>   <title>Expanded conversation memory scope migration in V20__conversation_memory_scope_expand.sql</title>   <subtitle>Migration V20 was expanded into a one-shot conversation/memory scope identity migration with rehearsal and conflict fixtures.</subtitle>   <facts>     <fact>V20__conversation_memory_scope_expand.sql was rewritten with a `-- V20: Conversation / ... [truncated]
```

### real-d33efbd8ae

```text
<observation>   <type>feature</type>   <title>Added ConversationScopeBackfillRunner migration tool in backend/src/main/java/com/ablueforce/cortexce/migration/ConversationScopeBackfillRunner.java</title>   <subtitle>New migration backfill tool with classifier, CLI entry point, and unit tests for conversation scope classification.</subtitle>   <facts>     <fact>Created ConversationScopeBackfillRunne... [truncated]
```

### real-cd6b972cfd

```text
<observation>   <type>feature</type>   <title>Added SummaryJobService to the backend service layer</title>   <subtitle>Created a new SummaryJobService class with 326 lines under com.ablueforce.cortexce.service.</subtitle>   <facts>     <fact>Created backend/src/main/java/com/ablueforce/cortexce/service/SummaryJobService.java with package declaration com.ablueforce.cortexce.service.</fact>     <fac... [truncated]
```

### real-46e467bff6

```text
```xml <observation>   <type>feature</type>   <title>Added conversation-scope preflight validation script conversation-scope-preflight.sql</title>   <subtitle>New SQL preflight validator, Flyway migration V20, and fixture dataset for conversation/memory scope identity expansion.</subtitle>   <facts>     <fact>scripts/db/conversation-scope-preflight.sql was added with 426 lines of SQL preflight che... [truncated]
```

### real-325c817bbf

```text
<observation>   <type>change</type>   <title>Added Conversation 与 Memory Scope 身份边界重构实施蓝图 in conversation-memory-scope-refactor-plan.md</title>   <subtitle>New 917-line implementation blueprint planning the identity boundary refactor between Conversation and Memory Scope</subtitle>   <facts>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md was created with 917 inserted lines and 0 ... [truncated]
```

### real-95de35b2ea

```text
<observation>   <type>change</type>   <title>Updated byte-buddy pin rationale comment in pom.xml</title>   <subtitle>Reworded the byte-buddy 1.15.x pin comment to cite Mockito 5.11 and the spring-boot-starter-test BOM instead of JDK 24.</subtitle>   <facts>     <fact>backend/pom.xml comment changed from "Pin test-classpath byte-buddy to 1.15.x for JDK 24 compatibility:" to "Pin byte-buddy to 1.15.... [truncated]
```

### real-53851b4a23

```text
<observation>   <type>change</type>   <title>Added health check history archive 11 in docs/archive/2026-10-08_health-check-history-11.md</title>   <subtitle>New archive document captures rounds 312~359+ health check history from 2026-10-06 to 2026-10-07 as the third compression migration.</subtitle>   <facts>     <fact>Created docs/archive/2026-10-08_health-check-history-11.md with 1206 lines cove... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
