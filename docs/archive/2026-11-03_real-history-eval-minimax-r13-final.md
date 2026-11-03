golden set: real (60 samples)

# Model output quality evaluation

- Date: 2026-11-04
- Phase: r13-final
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 60 unique; runs scored: 1; key repeat: 1; real calls: 60 (cap none); duration: 171219 ms; temperature: 0.0
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
| title exact-hit rate (legacy) | 43% |
| title normalized hit rate (legacy) | 43% |
| title action-class hit rate (A6) | 98% (47 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 43% |
| title subject hit rate (legacy, diagnostic) | 100% |
| title trace-visible subject hit rate (A6b) | 100% (47 scored, 0 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 98% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 98% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 43% |
| title identifier hit rate | 100% |
| type accuracy (raw) | 72% |
| type accuracy (auditable labels only) | 83% (6 unverifiable label(s) excluded) |
| facts keyword precision (legacy) / recall | 100% / 100% |
| facts informative precision | 90% |
| facts trace-support rate | 100% |
| concepts precision / recall | 26% / 51% |
| skip consistency across repeats | 0% (0 repeated samples) |
| latency p50 / p95 / max (ms) | 2600 / 6615 / 7578 |
| tokens p50 / p95 / max / mean per call | 4893 / 5544 / 5627 / 5013 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 90% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 98% | PASS |
| A6b | title trace-visible subject | >= 90% | 100% | PASS |
| A6c | title identifiers | = 100% | 100% | PASS |
| A6 | all three title dimensions together | all pass | 98% | PASS |
| A13 | provider call success rate | >= 99% (at most 1 failed call(s) per run) | 100% (0/60 failed) | PASS |

## Status distribution

| status | calls |
|---|---|
| 200 | 60 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 60 | 100% | 43% | 98% | 100% | 90% | 100% | 0 |

- average: skip 100%, title 43%, title contract 98%, facts P/R 100%/100%, facts actionable 90%
- worst run: skip 100%, title 43%, title contract 98%, facts P/R 100%/100%, facts actionable 90%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| feature | 12 | 12/12 | 9/12 | 2/12 |
| config-change | 1 | 1/1 | 1/1 | 1/1 |
| refactor | 2 | 2/2 | 0/2 | 0/2 |
| docs-update | 20 | 17/20 | 17/20 | 12/20 |
| test-add | 2 | 2/2 | 2/2 | 1/2 |
| security-fix | 7 | 7/7 | 5/7 | 1/7 |
| bug-fix | 6 | 6/6 | 0/6 | 3/6 |
| diagnose | 10 | 10/10 | 0/10 | 0/10 |

## Unverifiable type labels (excluded from the auditable type accuracy)

6 sample(s) expect a type the recorded trace cannot evidence:

- real-e2c887775a: expected bugfix but the trace shows no defect or security evidence
- real-cf667b4d8b: expected bugfix but the trace shows no defect or security evidence
- real-c67e7a5ad3: expected bugfix but the trace shows no defect or security evidence
- real-bdf72f2509: expected bugfix but the trace shows no defect or security evidence
- real-8ac2a32980: expected bugfix but the trace shows no defect or security evidence
- real-95de35b2ea: expected bugfix but the trace shows no defect or security evidence

## Failure clusters (first run)

| cluster | misses | share of scored (47) |
|---|---|---|
| identifier missing | 0 | 0% |
| action class wrong | 1 | 2% |
| subject wrong | 0 | 0% |
| type wrong | 13 | 28% |

### action class wrong (1)

- real-63b5502fb9 — expected [secure, create, modify], got 'null' from title 'Expanded conversation memory scope identity in V20__conversation_memory_scope_expand.sql'

### type wrong (13)

- real-f6a1b89e41 — expected 'change', got 'feature' (title 'Extended real-tool-use golden dataset to round 10 in golden-tool-use-real.json')
- real-f0d346eab1 — expected 'change', got 'feature' (title 'Added real-history golden tool-use evaluation set golden-tool-use-real.json')
- real-3a2d426fdf — expected 'change', got 'feature' (title 'Extended evaluation token metrics and repeat mode in GoldenSetScorer')
- real-864dadf51b — expected 'feature', got 'refactor' (title 'Refactored GoldenSetScorer evidence construction')
- real-f3d12860ff — expected 'change', got 'bugfix' (title 'Hardened log directory handling in LogsControllerTest')
- real-e2c887775a — expected 'bugfix', got 'change' (title 'Changed context endpoint mapping from generate to inject in AGENTS.md')
- real-cf667b4d8b — expected 'bugfix', got 'change' (title 'Updated ObservationFeedbackEntity documentation and repository ordering')
- real-c67e7a5ad3 — expected 'bugfix', got 'refactor' (title 'Removed redundant else branch in dto.py')
- real-bdf72f2509 — expected 'bugfix', got 'change' (title 'Removed SDK version header comment in CortexMemClientImpl')
- real-8ac2a32980 — expected 'bugfix', got 'change' (title 'Updated default port documentation and removed build argument sections in Dockerfile')
- real-9a0e90bed5 — expected 'refactor', got 'feature' (title 'Extended memory scope filtering in ObservationRepository')
- real-63b5502fb9 — expected 'bugfix', got 'feature' (title 'Expanded conversation memory scope identity in V20__conversation_memory_scope_expand.sql')
- real-95de35b2ea — expected 'bugfix', got 'change' (title 'Updated byte-buddy dependency alignment in backend/pom.xml')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| real-f6a1b89e41 | feature | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 3611 |
| real-1fe4ffb124 | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 2328 |
| real-d13e2c90cd | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 2957 |
| real-3b4f737562 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 2968 |
| real-77ba40df77 | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 2853 |
| real-f0d346eab1 | feature | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/100% | 3960 |
| real-3a2d426fdf | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 2250 |
| real-864dadf51b | feature | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 3191 |
| real-b064308244 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 1640 |
| real-30a956e462 | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 1754 |
| real-ba86bf2588 | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 6030 |
| real-a9c8452967 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 7028 |
| real-db8ac0287e | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 0% | 0%/0% | 6583 |
| real-9e6612fdf9 | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 67% | 0%/0% | 3537 |
| real-f43cfd6f44 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 2876 |
| real-e352ba3da3 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 4231 |
| real-112de33e23 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 1479 |
| real-28edef5a35 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 3078 |
| real-6d6df2c321 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 1851 |
| real-d3431639a9 | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 2667 |
| real-7093cb5c56 | security-fix | observation | observation | pass | 0% | hit | miss | match | 100%/100% | 100% | 50%/100% | 3011 |
| real-f3d12860ff | security-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 67% | 50%/100% | 6615 |
| real-3409dda60c | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 2165 |
| real-f7b865efe5 | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 3621 |
| real-a8cc1754bc | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 2812 |
| real-e2c887775a | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 2948 |
| real-cf667b4d8b | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 50% | 0%/0% | 2340 |
| real-a0e77ca4ee | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 80% | 0%/0% | 2760 |
| real-d958416fdc | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 80% | 0%/0% | 2030 |
| real-9b424d0d02 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 2952 |
| real-85480a720e | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 2288 |
| real-c67e7a5ad3 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 1699 |
| real-bdf72f2509 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 1562 |
| real-8ac2a32980 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 3300 |
| real-2025b33a95 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1005 |
| real-e8c8b314d6 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1172 |
| real-c42d7a9c5d | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1614 |
| real-b5ae725e2a | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 767 |
| real-9261b542ca | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 783 |
| real-7c557c8d79 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1012 |
| real-1dbccd058e | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 785 |
| real-1f9b5d09e7 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 892 |
| real-ed737be6bb | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 783 |
| real-b2d48b12a1 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 818 |
| real-bc3512ae8d | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 2423 |
| real-e02e436bdd | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 50% | 50%/100% | 2135 |
| real-d44a83721d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 1650 |
| real-0fd4ffcc31 | test-add | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 4734 |
| real-eee7785ec8 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 50% | 50%/100% | 7070 |
| real-9a0e90bed5 | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 67% | 0%/0% | 3776 |
| real-63b5502fb9 | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 0%/0% | 4165 |
| real-d33efbd8ae | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 80% | 0%/0% | 2600 |
| real-cd6b972cfd | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 1852 |
| real-46e467bff6 | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 2556 |
| real-325c817bbf | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/100% | 7578 |
| real-7149578a88 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3067 |
| real-95de35b2ea | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 2121 |
| real-53851b4a23 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 2334 |
| real-f4c6765fb9 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 2680 |
| real-67083a7bc1 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 2993 |

## Problems

- real-f6a1b89e41: title coverage 50% (expected keywords [change, golden-tool-use-real.json])
- real-f6a1b89e41: title verb class 'change' missing (got 'Extended real-tool-use golden dataset to round 10 in golden-tool-use-real.json')
- real-f6a1b89e41: type 'feature' != expected 'change'
- real-1fe4ffb124: concepts recall 0% (expected [what-changed], got [pattern, trade-off])
- real-d13e2c90cd: title coverage 50% (expected keywords [document, 2026-11-03_real-history-golden-eval-minimax.md])
- real-d13e2c90cd: title verb class 'document' missing (got 'Added real golden replay evaluation in 2026-11-03_real-history-golden-eval-minimax.md')
- real-3b4f737562: title coverage 50% (expected keywords [document, minimax-after-v9.json])
- real-3b4f737562: title verb class 'document' missing (got 'Added model output quality evaluation fixtures in minimax-after-v9.json')
- real-77ba40df77: concepts recall 0% (expected [what-changed], got [pattern, trade-off])
- real-f0d346eab1: type 'feature' != expected 'change'
- real-3a2d426fdf: title coverage 50% (expected keywords [change, goldensetscorer])
- real-3a2d426fdf: title verb class 'change' missing (got 'Extended evaluation token metrics and repeat mode in GoldenSetScorer')
- real-3a2d426fdf: type 'feature' != expected 'change'
- real-864dadf51b: title coverage 50% (expected keywords [document, goldensetscorer])
- real-864dadf51b: title verb class 'document' missing (got 'Refactored GoldenSetScorer evidence construction')
- real-864dadf51b: type 'refactor' != expected 'feature'
- real-30a956e462: validation rejected the observation: title too long: 103 chars > 100
- real-a9c8452967: title coverage 50% (expected keywords [document, searchsimilarityevaluator])
- real-a9c8452967: title verb class 'document' missing (got 'Added semantic relevance evaluation fixtures and replay suite in SearchSimilarityEvaluator')
- real-db8ac0287e: title coverage 50% (expected keywords [document, continuation.txt])
- real-db8ac0287e: title verb class 'document' missing (got 'Updated defect wording in continuation.txt')
- real-db8ac0287e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9e6612fdf9: title coverage 50% (expected keywords [document, continuation.txt])
- real-9e6612fdf9: title verb class 'document' missing (got 'Extended defect classification and extend synonyms in continuation.txt')
- real-9e6612fdf9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e352ba3da3: title coverage 50% (expected keywords [document, longcat-after-v7.json])
- real-e352ba3da3: title verb class 'document' missing (got 'Added longcat-after-v7.json model output evaluation fixtures')
- real-28edef5a35: title coverage 50% (expected keywords [document, longcat-after-v6.json])
- real-28edef5a35: title verb class 'document' missing (got 'Added model output evaluation fixtures in longcat-after-v6.json')
- real-6d6df2c321: concepts recall 0% (expected [problem-solution], got [pattern, what-changed])
- real-d3431639a9: validation rejected the observation: title too long: 112 chars > 100
- real-7093cb5c56: title coverage 0% (expected keywords [harden, embeddingbackfillconcurrencydatabasetest])
- real-7093cb5c56: title verb class 'harden' missing (got 'Fixed embedding backfill concurrency handling in EmbeddingBackfillConcurrencyDatabaseTest')
- real-f3d12860ff: type 'bugfix' != expected 'change'
- real-3409dda60c: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-f7b865efe5: title coverage 50% (expected keywords [harden, index.html])
- real-f7b865efe5: title verb class 'harden' missing (got 'Updated header color to #f8f6f2 in index.html')
- real-f7b865efe5: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a8cc1754bc: title coverage 50% (expected keywords [remove, pendingmessagerepository])
- real-a8cc1754bc: title verb class 'remove' missing (got 'Fixed pending message deduplication in PendingMessageRepository')
- real-e2c887775a: title coverage 50% (expected keywords [remove, agents.md])
- real-e2c887775a: title verb class 'remove' missing (got 'Changed context endpoint mapping from generate to inject in AGENTS.md')
- real-e2c887775a: type 'change' != expected 'bugfix'
- real-e2c887775a: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-cf667b4d8b: title coverage 50% (expected keywords [remove, observationfeedbackentity])
- real-cf667b4d8b: title verb class 'remove' missing (got 'Updated ObservationFeedbackEntity documentation and repository ordering')
- real-cf667b4d8b: type 'change' != expected 'bugfix'
- real-cf667b4d8b: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a0e77ca4ee: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d958416fdc: title coverage 50% (expected keywords [remove, patrol-state.json])
- real-d958416fdc: title verb class 'remove' missing (got 'Updated patrol rotation state in patrol-state.json')
- real-d958416fdc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9b424d0d02: title coverage 50% (expected keywords [remove, api-zh-cn.md])
- real-9b424d0d02: title verb class 'remove' missing (got 'Documented corrected GET /api/settings defaults in API-zh-CN.md')
- real-9b424d0d02: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-85480a720e: title coverage 50% (expected keywords [remove, testing-zh-cn.md])
- real-85480a720e: title verb class 'remove' missing (got 'Documented DB_NAME default in TESTING-zh-CN.md')
- real-85480a720e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-c67e7a5ad3: type 'refactor' != expected 'bugfix'
- real-c67e7a5ad3: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-bdf72f2509: type 'change' != expected 'bugfix'
- real-bdf72f2509: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-8ac2a32980: title verb class 'remove' missing (got 'Updated default port documentation and removed build argument sections in Dockerfile')
- real-8ac2a32980: type 'change' != expected 'bugfix'
- real-8ac2a32980: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-bc3512ae8d: validation rejected the observation: title too long: 106 chars > 100
- real-e02e436bdd: title coverage 50% (expected keywords [document, continuation.txt])
- real-e02e436bdd: title verb class 'document' missing (got 'Updated category examples in continuation.txt')
- real-0fd4ffcc31: title coverage 50% (expected keywords [change, goldensetscorer])
- real-0fd4ffcc31: title verb class 'change' missing (got 'Extended GoldenSetScorer with record-worthy sample skip accounting')
- real-eee7785ec8: title coverage 50% (expected keywords [document, 2026-11-02_model-output-quality-eval-minimax.md])
- real-eee7785ec8: title verb class 'document' missing (got 'Added model output quality evaluation harness in 2026-11-02_model-output-quality-eval-minimax.md')
- real-9a0e90bed5: title coverage 50% (expected keywords [harden, observationrepository])
- real-9a0e90bed5: title verb class 'harden' missing (got 'Extended memory scope filtering in ObservationRepository')
- real-9a0e90bed5: type 'feature' != expected 'refactor'
- real-9a0e90bed5: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-63b5502fb9: title coverage 50% (expected keywords [harden, v20__conversation_memory_scope_expand.sql])
- real-63b5502fb9: title action [secure, create, modify] missing: title leads 'null' (got 'Expanded conversation memory scope identity in V20__conversation_memory_scope_expand.sql')
- real-63b5502fb9: title verb class 'harden' missing (got 'Expanded conversation memory scope identity in V20__conversation_memory_scope_expand.sql')
- real-63b5502fb9: type 'feature' != expected 'bugfix'
- real-63b5502fb9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d33efbd8ae: title coverage 50% (expected keywords [harden, conversationscopebackfillrunner])
- real-d33efbd8ae: title verb class 'harden' missing (got 'Added conversation scope backfill runner in ConversationScopeBackfillRunner')
- real-d33efbd8ae: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-cd6b972cfd: title coverage 50% (expected keywords [harden, summaryjobservice])
- real-cd6b972cfd: title verb class 'harden' missing (got 'Added SummaryJobService')
- real-cd6b972cfd: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-46e467bff6: title coverage 50% (expected keywords [harden, conversation-scope-preflight.sql])
- real-46e467bff6: title verb class 'harden' missing (got 'Added conversation scope preflight SQL in conversation-scope-preflight.sql')
- real-46e467bff6: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-95de35b2ea: title coverage 50% (expected keywords [change, pom.xml])
- real-95de35b2ea: type 'change' != expected 'bugfix'
- real-95de35b2ea: concepts recall 0% (expected [problem-solution], got [what-changed, gotcha])
- real-53851b4a23: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])

## Raw answers behind problems

### real-f6a1b89e41

```text
<observation>   <type>feature</type>   <title>Extended real-tool-use golden dataset to round 10 in golden-tool-use-real.json</title>   <subtitle>The evaluation corpus and scoring pipeline now retain round-10 failure-cluster evidence across every file touched by real diffs.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json sets version to real-git-2026-11-03-r... [truncated]
```

### real-1fe4ffb124

```text
<observation>   <type>change</type>   <title>Documented Round 9 identifier miss attribution in extraction-quality-improvement-plan.md</title>   <subtitle>The extraction quality plan records full identifier attribution while test-suite floors increase from 423 to 472 across local and CI verification.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md added section “##... [truncated]
```

### real-d13e2c90cd

```text
<observation>   <type>feature</type>   <title>Added real golden replay evaluation in 2026-11-03_real-history-golden-eval-minimax.md</title>   <subtitle>Archived a 36-sample real-history golden evaluation with replay fixtures, metrics, and an executable Java test.</subtitle>   <facts>     <fact>docs/archive/2026-11-03_real-history-golden-eval-minimax.md documents a golden set containing 36 real sam... [truncated]
```

### real-3b4f737562

```text
<observation>   <type>feature</type>   <title>Added model output quality evaluation fixtures in minimax-after-v9.json</title>   <subtitle>Introduced Minimax after-v9 evaluation data, metrics, documentation, evaluator output fields, and corrected golden-set classification rules.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/minimax-after-v9.json contains 2096 lines of new ... [truncated]
```

### real-77ba40df77

```text
<observation>   <type>feature</type>   <title>Added extraction consistency voting to ExtractionConsistency</title>   <subtitle>Agent extraction now supports configurable per-trace voting, with consistency-focused unit coverage.</subtitle>   <facts>     <fact>ExtractionConsistency was added at backend/src/main/java/com/ablueforce/cortexce/service/ExtractionConsistency.java with 82 lines.</fact>    ... [truncated]
```

### real-f0d346eab1

```text
<observation>   <type>feature</type>   <title>Added real-history golden tool-use evaluation set golden-tool-use-real.json</title>   <subtitle>Mechanical golden-set generation and real-data evaluation now provide a larger tool-use quality benchmark.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json contains 1097 added lines of real-history golden data.</fact> ... [truncated]
```

### real-3a2d426fdf

```text
<observation>   <type>feature</type>   <title>Extended evaluation token metrics and repeat mode in GoldenSetScorer</title>   <subtitle>Golden-set evaluation now records prompt-token usage and can repeat every sample for run-to-run measurements.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java adds public final int promptTokens and expands scori... [truncated]
```

### real-864dadf51b

```text
<observation>   <type>refactor</type>   <title>Refactored GoldenSetScorer evidence construction</title>   <subtitle>Golden-set scoring now builds structured evidence with additional metadata while preserving evaluator behavior.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java replaced the tool-name-only evidence string with a 70-line structured... [truncated]
```

### real-30a956e462

```text
<observation>   <type>change</type>   <title>Documented Round 8 insertion-scope contract and security verb in extraction-quality-improvement-plan.md</title>   <subtitle>The draft records Round 8 guidance covering insertion scope, removal of coverage, and a reverted security verb.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md adds 139 lines for the section "7.14 ... [truncated]
```

### real-a9c8452967

```text
<observation>   <type>feature</type>   <title>Added semantic relevance evaluation fixtures and replay suite in SearchSimilarityEvaluator</title>   <subtitle>Added BGE-M3 similarity evaluation data, expected metrics, replay tests, evaluator code, and archived evaluation documentation.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/SearchSimilarityEvaluator.java de... [truncated]
```

### real-db8ac0287e

```text
<observation>   <type>change</type>   <title>Updated defect wording in continuation.txt</title>   <subtitle>Prompt guidance now permits Fixed for any reported wrong or failing behavior, including security defects.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt changes the Fixed definition to cover defects that produced wrong or failing behaviour without excludin... [truncated]
```

### real-9e6612fdf9

```text
<observation>   <type>feature</type>   <title>Extended defect classification and extend synonyms in continuation.txt</title>   <subtitle>Prompt guidance now reserves Fixed for non-security defects, while golden-set scoring recognizes extend-language variants.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt and backend/src/main/resources/prompts/init.txt now defin... [truncated]
```

### real-e352ba3da3

```text
<observation>   <type>feature</type>   <title>Added longcat-after-v7.json model output evaluation fixtures</title>   <subtitle>Added LongCat and MiniMax v7 evaluation fixtures, metrics, and archived evaluation reports.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v7.json contains 656 added lines of LongCat model-output evaluation data.</fact>     <fact>back... [truncated]
```

### real-28edef5a35

```text
<observation>   <type>feature</type>   <title>Added model output evaluation fixtures in longcat-after-v6.json</title>   <subtitle>Longcat and MiniMax after-v6 fixtures, metrics, and archived evaluation reports now provide reusable model-quality baselines.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v6.json contains 656 added lines of evaluation fixture dat... [truncated]
```

### real-6d6df2c321

```text
<observation>   <type>change</type>   <title>Documented continuation prompt skip rules in continuation.txt</title>   <subtitle>Continuation and initialization prompts gained expanded guidance for distinguishing actionable changes from read-only exploration.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt contains the rule to skip identifier usage or definition se... [truncated]
```

### real-d3431639a9

```text
<observation>   <type>change</type>   <title>Documented model output quality evaluations in 2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md</title>   <subtitle>Archived Minimax and LongCat evaluation contracts, session-summary results, and a Round 4 extraction-quality plan.</subtitle>   <facts>     <fact>docs/archive/2026-11-03_model-output-quality-eval-minimax-after-v4-contract.... [truncated]
```

### real-7093cb5c56

```text
<observation>   <type>bugfix</type>   <title>Fixed embedding backfill concurrency handling in EmbeddingBackfillConcurrencyDatabaseTest</title>   <subtitle>Database and service tests now cover concurrent backfill behavior, including isolation of individual row failures.</subtitle>   <facts>     <fact>EmbeddingBackfillConcurrencyDatabaseTest adds 333 lines covering concurrent embedding backfill exec... [truncated]
```

### real-f3d12860ff

```text
<observation>   <type>bugfix</type>   <title>Hardened log directory handling in LogsControllerTest</title>   <subtitle>LogsController now resolves a configured directory path while tests set the logDir field safely.</subtitle>   <facts>     <fact>LogsControllerTest sets the logDir field through setField("logDir", logDir.toString()) instead of direct reflective field assignment.</fact>     <fact>Lo... [truncated]
```

### real-3409dda60c

```text
<observation>   <type>change</type>   <title>Documented implementation commit range in conversation-memory-scope-refactor-plan.md</title>   <subtitle>The scope-refactor plan now identifies nine implementation commits and links the range to its closing checklist.</subtitle>   <facts>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md sets the implementation commit range to `325c817..f... [truncated]
```

### real-f7b865efe5

```text
<observation>   <type>change</type>   <title>Updated header color to #f8f6f2 in index.html</title>   <subtitle>The static viewer header uses a warm off-white theme and ships a rebuilt frontend bundle.</subtitle>   <facts>     <fact>backend/src/main/resources/static/index.html sets --color-bg-header to #f8f6f2.</fact>     <fact>backend/src/main/resources/static/index.html changed from 950 removed l... [truncated]
```

### real-a8cc1754bc

```text
<observation>   <type>bugfix</type>   <title>Fixed pending message deduplication in PendingMessageRepository</title>   <subtitle>Pending-message persistence now uses the corrected conversation-scoped deduplication behavior across repository and service layers.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/repository/PendingMessageRepository.java removed the 22-line s... [truncated]
```

### real-e2c887775a

```text
<observation>   <type>change</type>   <title>Changed context endpoint mapping from generate to inject in AGENTS.md</title>   <subtitle>Documentation now identifies the endpoint that returns the protected context field.</subtitle>   <facts>     <fact>AGENTS.md maps `updateFiles` to `/api/context/inject` and `proxy/proxy.js` instead of `/api/context/generate`.</fact>     <fact>docs/drafts/doc-review... [truncated]
```

### real-cf667b4d8b

```text
<observation>   <type>change</type>   <title>Updated ObservationFeedbackEntity documentation and repository ordering</title>   <subtitle>Observation feedback documentation and repository ordering semantics were clarified without changing public APIs.</subtitle>   <facts>     <fact>ObservationFeedbackEntity.java changed the documentation block delimiter from `/**` to `*` and removed 10 lines while ... [truncated]
```

### real-a0e77ca4ee

```text
<observation>   <type>change</type>   <title>Removed stale script line counts from TESTING.md</title>   <subtitle>English and Chinese testing guides now omit drifting line-count columns and duplicate headers.</subtitle>   <facts>     <fact>docs/TESTING.md removed the script table header "| Script | Description | Lines |" and replaced it with "| Script | Description |".</fact>     <fact>docs/TESTIN... [truncated]
```

### real-d958416fdc

```text
<observation>   <type>change</type>   <title>Updated patrol rotation state in patrol-state.json</title>   <subtitle>Documentation records the backend as the latest completed patrol and aligns related review artifacts.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json sets lastDirection to backend.</fact>     <fact>docs/drafts/patrol-rotation.md records the last completed patrol as Backe... [truncated]
```

### real-9b424d0d02

```text
<observation>   <type>change</type>   <title>Documented corrected GET /api/settings defaults in API-zh-CN.md</title>   <subtitle>Chinese and English API docs now record six example values corrected to match AppSettings.toMap() defaults.</subtitle>   <facts>     <fact>docs/API-zh-CN.md records GET /api/settings example corrections for PROVIDER, MODEL, and MAX_OBSERVATIONS in version 0.1.0-beta+42.<... [truncated]
```

### real-85480a720e

```text
<observation>   <type>change</type>   <title>Documented DB_NAME default in TESTING-zh-CN.md</title>   <subtitle>Chinese and English testing guides now list the database name instead of the database host.</subtitle>   <facts>     <fact>docs/TESTING-zh-CN.md documents `DB_NAME` with default value `claude_mem` as 数据库名称.</fact>     <fact>docs/TESTING.md documents `DB_NAME` with default value `claude_m... [truncated]
```

### real-c67e7a5ad3

```text
<observation>   <type>refactor</type>   <title>Removed redundant else branch in dto.py</title>   <subtitle>Simplified conditional flow in the Python SDK DTO module and ignored generated dream files.</subtitle>   <facts>     <fact>python-sdk/cortex-mem-python/cortex_mem/dto.py removed 2 lines, including the `else:` branch.</fact>     <fact>.gitignore added the `memory/.dreams` path.</fact>   </fact... [truncated]
```

### real-bdf72f2509

```text
<observation>   <type>change</type>   <title>Removed SDK version header comment in CortexMemClientImpl</title>   <subtitle>Removed an obsolete source comment without changing the client implementation.</subtitle>   <facts>     <fact>CortexMemClientImpl.java removed the comment documenting the SDK version used for the User-Agent header.</fact>     <fact>CortexMemClientImpl.java changed by 1 deletio... [truncated]
```

### real-8ac2a32980

```text
<observation>   <type>change</type>   <title>Updated default port documentation and removed build argument sections in Dockerfile</title>   <subtitle>Dockerfile now documents port 37777, while English and Chinese deployment guides remove obsolete build-argument sections.</subtitle>   <facts>     <fact>backend/Dockerfile changed the default port comment to “37777 — can be overridden by SERVER_PORT ... [truncated]
```

### real-bc3512ae8d

```text
<observation>   <type>change</type>   <title>Documented Round 6 category few-shots and measured title ceiling in extraction-quality-improvement-plan.md</title>   <subtitle>The quality-improvement plan now describes category-specific few-shot examples and the measured limit on title quality.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md added section "7.12 Round ... [truncated]
```

### real-e02e436bdd

```text
<observation>   <type>change</type>   <title>Updated category examples in continuation.txt</title>   <subtitle>Prompt guidance now provides category-specific title examples for bug, security, refactor, test, config, deploy, docs, and decision outputs.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt replaces the `Fixed UserCache.computeIfAbsent null placeholder ra... [truncated]
```

### real-0fd4ffcc31

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetScorer with record-worthy sample skip accounting</title>   <subtitle>Golden-set evaluation now distinguishes skipped record-worthy samples from other skipped samples and gates A3 on the correct metric.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java added 29 lines and removed 2 li... [truncated]
```

### real-eee7785ec8

```text
<observation>   <type>feature</type>   <title>Added model output quality evaluation harness in 2026-11-02_model-output-quality-eval-minimax.md</title>   <subtitle>Golden-set evaluation now supports Anthropic models alongside the existing evaluation workflow and records the harness in archived documentation.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSet... [truncated]
```

### real-9a0e90bed5

```text
<observation>   <type>feature</type>   <title>Extended memory scope filtering in ObservationRepository</title>   <subtitle>Observation queries now resolve project filters through memory scope membership.</subtitle>   <facts>     <fact>ObservationRepository project filtering changed from o.projectPath = :project to o.memoryScopeId IN (...).</fact>     <fact>MemoryScopeEntity was added as a new enti... [truncated]
```

### real-63b5502fb9

```text
<observation>   <type>feature</type>   <title>Expanded conversation memory scope identity in V20__conversation_memory_scope_expand.sql</title>   <subtitle>One-shot V20 migration and rehearsal fixtures establish conversation and memory scope identity with conflict handling.</subtitle>   <facts>     <fact>backend/src/main/resources/db/migration/V20__conversation_memory_scope_expand.sql grew from 240... [truncated]
```

### real-d33efbd8ae

```text
<observation>   <type>feature</type>   <title>Added conversation scope backfill runner in ConversationScopeBackfillRunner</title>   <subtitle>Introduced a Java migration runner with technical-bucket classification, CLI entrypoint, and classifier tests.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/migration/ConversationScopeBackfillRunner.java added 845 lines for the... [truncated]
```

### real-cd6b972cfd

```text
<observation>   <type>feature</type>   <title>Added SummaryJobService</title>   <subtitle>Introduced a 326-line summary job service implementation with authentication-related handling.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/service/SummaryJobService.java contains package com.ablueforce.cortexce.service.</fact>     <fact>SummaryJobService was added with 326 ins... [truncated]
```

### real-46e467bff6

```text
<observation>   <type>feature</type>   <title>Added conversation scope preflight SQL in conversation-scope-preflight.sql</title>   <subtitle>Database preflight, migration, and fixture scripts now support additive conversation and memory scope expansion.</subtitle>   <facts>     <fact>scripts/db/conversation-scope-preflight.sql contains 426 added lines for conversation-scope validation.</fact>     ... [truncated]
```

### real-95de35b2ea

```text
<observation>   <type>change</type>   <title>Updated byte-buddy dependency alignment in backend/pom.xml</title>   <subtitle>Aligned the test-classpath byte-buddy pin with Mockito 5.11 and clarified the Docker build-context requirement.</subtitle>   <facts>     <fact>backend/pom.xml pins byte-buddy to 1.15.x for Mockito 5.11 from the spring-boot-starter-test BOM.</fact>     <fact>backend/pom.xml re... [truncated]
```

### real-53851b4a23

```text
<observation>   <type>change</type>   <title>Documented health check history archive 11 in 2026-10-08_health-check-history-11.md</title>   <subtitle>Archived rounds 312–359+ and linked the third compressed migration in the archive index.</subtitle>   <facts>     <fact>docs/archive/2026-10-08_health-check-history-11.md contains 1206 added lines covering health check rounds 312–359+ from 2026-10-06 ... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
