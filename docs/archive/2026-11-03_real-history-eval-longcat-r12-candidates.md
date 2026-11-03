golden set: real (60 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: current
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 60 unique; runs scored: 1; key repeat: 1; real calls: 60 (cap none); duration: 1319351 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 95% |
| skip accuracy | 98% |
|   skip confusion (TP/FP/FN/TN) | 9/0/1/50 |
| format errors | 0 |
| validation rejections | 1 |
| call failures | 1 |
| title exact-hit rate (legacy) | 42% |
| title normalized hit rate (legacy) | 42% |
| title action-class hit rate (A6) | 90% (48 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 42% |
| title subject hit rate (legacy, diagnostic) | 100% |
| title trace-visible subject hit rate (A6b) | 100% (48 scored, 0 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 90% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 90% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 42% |
| title identifier hit rate | 100% |
| type accuracy (raw) | 73% |
| type accuracy (auditable labels only) | 83% (6 unverifiable label(s) excluded) |
| facts keyword precision (legacy) / recall | 100% / 100% |
| facts informative precision | 85% |
| facts trace-support rate | 100% |
| concepts precision / recall | 24% / 48% |
| skip consistency across repeats | 0% (0 repeated samples) |
| latency p50 / p95 / max (ms) | 18245 / 45635 / 81066 |
| tokens p50 / p95 / max / mean per call | 5915 / 7857 / 8963 / 6090 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 98% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 1 | PASS |
| A5 | facts informative precision | >= 75% | 85% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 90% | PASS |
| A6b | title trace-visible subject | >= 90% | 100% | PASS |
| A6c | title identifiers | = 100% | 100% | PASS |
| A6 | all three title dimensions together | all pass | 90% | PASS |

## Status distribution

| status | calls |
|---|---|
| 200 | 59 |
| exception | 1 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 60 | 98% | 42% | 90% | 100% | 85% | 100% | 0 |

- average: skip 98%, title 42%, title contract 90%, facts P/R 100%/100%, facts actionable 85%
- worst run: skip 98%, title 42%, title contract 90%, facts P/R 100%/100%, facts actionable 85%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| feature | 12 | 12/12 | 10/12 | 3/12 |
| config-change | 1 | 1/1 | 1/1 | 1/1 |
| refactor | 2 | 2/2 | 0/2 | 0/2 |
| docs-update | 20 | 19/20 | 19/20 | 12/20 |
| test-add | 2 | 2/2 | 2/2 | 1/2 |
| security-fix | 7 | 6/7 | 3/7 | 0/7 |
| bug-fix | 6 | 6/6 | 0/6 | 3/6 |
| diagnose | 10 | 9/10 | 0/10 | 0/10 |

## Unverifiable type labels (excluded from the auditable type accuracy)

6 sample(s) expect a type the recorded trace cannot evidence:

- real-e2c887775a: expected bugfix but the trace shows no defect or security evidence
- real-cf667b4d8b: expected bugfix but the trace shows no defect or security evidence
- real-c67e7a5ad3: expected bugfix but the trace shows no defect or security evidence
- real-bdf72f2509: expected bugfix but the trace shows no defect or security evidence
- real-8ac2a32980: expected bugfix but the trace shows no defect or security evidence
- real-95de35b2ea: expected bugfix but the trace shows no defect or security evidence

## Failure clusters (first run)

| cluster | misses | share of scored (48) |
|---|---|---|
| identifier missing | 0 | 0% |
| action class wrong | 5 | 10% |
| subject wrong | 0 | 0% |
| type wrong | 13 | 27% |

### action class wrong (5)

- real-db8ac0287e — expected [document, secure, modify], got 'null' from title 'Shortened Fixed verb definition in continuation.txt'
- real-6d6df2c321 — expected [document, secure, modify], got 'null' from title 'Expanded skip guidance in continuation.txt'
- real-9b424d0d02 — expected [remove, document], got 'modify' from title 'Corrected GET /api/settings example defaults in API-zh-CN.md'
- real-85480a720e — expected [remove, document], got 'modify' from title 'Updated database env var from DB_HOST to DB_NAME in TESTING-zh-CN.md'
- real-8ac2a32980 — expected [remove, document, modify], got 'null' from title 'Clarified default port in backend/Dockerfile and removed build-args sections from deployment docs'

### type wrong (13)

- real-f6a1b89e41 — expected 'change', got 'feature' (title 'Extended golden tool-use eval set to r10 in golden-tool-use-real.json')
- real-f0d346eab1 — expected 'change', got 'feature' (title 'Added real-history tool-use golden evaluation dataset in golden-tool-use-real.json')
- real-3a2d426fdf — expected 'change', got 'feature' (title 'Extended promptTokens accounting and run-to-run sampling in GoldenSetScorer')
- real-f3d12860ff — expected 'change', got 'refactor' (title 'Refactored logDir injection with setField helper in LogsControllerTest')
- real-a8cc1754bc — expected 'bugfix', got 'refactor' (title 'Refactored PendingMessageRepository to use conversation-based scoping')
- real-e2c887775a — expected 'bugfix', got 'change' (title 'Corrected /api/context/generate to /api/context/inject in AGENTS.md')
- real-cf667b4d8b — expected 'bugfix', got 'change' (title 'Updated Javadoc and ordering comment in ObservationFeedbackEntity')
- real-c67e7a5ad3 — expected 'bugfix', got 'change' (title 'Removed else clause in dto.py serialization logic')
- real-bdf72f2509 — expected 'bugfix', got 'change' (title 'Removed SDK version comment from CortexMemClientImpl')
- real-8ac2a32980 — expected 'bugfix', got 'change' (title 'Clarified default port in backend/Dockerfile and removed build-args sections from deployment docs')
- real-9a0e90bed5 — expected 'refactor', got 'feature' (title 'Replaced projectPath filter with memoryScopeId subquery in ObservationRepository')
- real-63b5502fb9 — expected 'bugfix', got 'change' (title 'Extended conversation scope migration in V20__conversation_memory_scope_expand.sql')
- real-95de35b2ea — expected 'bugfix', got 'change' (title 'Updated byte-buddy pin comment in pom.xml')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| real-f6a1b89e41 | feature | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 22959 |
| real-1fe4ffb124 | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 63551 |
| real-d13e2c90cd | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 30256 |
| real-3b4f737562 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 29868 |
| real-77ba40df77 | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 24622 |
| real-f0d346eab1 | feature | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/100% | 15267 |
| real-3a2d426fdf | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 45635 |
| real-864dadf51b | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 75% | 50%/100% | 61310 |
| real-b064308244 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 17317 |
| real-30a956e462 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 0%/0% | 15544 |
| real-ba86bf2588 | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/100% | 18036 |
| real-a9c8452967 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 26776 |
| real-db8ac0287e | docs-update | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 33% | 0%/0% | 16211 |
| real-9e6612fdf9 | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 26400 |
| real-f43cfd6f44 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 9520 |
| real-e352ba3da3 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 24809 |
| real-112de33e23 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 18256 |
| real-28edef5a35 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 26787 |
| real-6d6df2c321 | docs-update | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 0%/0% | 17265 |
| real-d3431639a9 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 35741 |
| real-7093cb5c56 | security-fix | observation | call-failed | FAIL | - | - | - | - | - | - | - | 81066 |
| real-f3d12860ff | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 31410 |
| real-3409dda60c | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 11539 |
| real-f7b865efe5 | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 19497 |
| real-a8cc1754bc | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 60% | 0%/0% | 37862 |
| real-e2c887775a | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 80% | 0%/0% | 35046 |
| real-cf667b4d8b | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 0% | 0%/0% | 21021 |
| real-a0e77ca4ee | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 33% | 0%/0% | 15318 |
| real-d958416fdc | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 80% | 0%/0% | 19140 |
| real-9b424d0d02 | docs-update | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 0%/0% | 15986 |
| real-85480a720e | docs-update | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 0%/0% | 36816 |
| real-c67e7a5ad3 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 50% | 0%/0% | 5342 |
| real-bdf72f2509 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 16262 |
| real-8ac2a32980 | bug-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | MISS | 100%/100% | 100% | 0%/0% | 10911 |
| real-2025b33a95 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2986 |
| real-e8c8b314d6 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 4051 |
| real-c42d7a9c5d | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3146 |
| real-b5ae725e2a | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3700 |
| real-9261b542ca | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 19952 |
| real-7c557c8d79 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2903 |
| real-1dbccd058e | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3843 |
| real-1f9b5d09e7 | diagnose | skip | observation | FAIL | 0% | MISS (subject unreachable, id MISS, action MISS) | miss | MISS | 0%/0% | 0% | 0%/0% | 22537 |
| real-ed737be6bb | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3993 |
| real-b2d48b12a1 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2425 |
| real-bc3512ae8d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 21166 |
| real-e02e436bdd | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 33% | 50%/100% | 16864 |
| real-d44a83721d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 18245 |
| real-0fd4ffcc31 | test-add | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 40187 |
| real-eee7785ec8 | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 31534 |
| real-9a0e90bed5 | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 24189 |
| real-63b5502fb9 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 32737 |
| real-d33efbd8ae | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 17531 |
| real-cd6b972cfd | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 0% | 0%/0% | 16537 |
| real-46e467bff6 | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 30427 |
| real-325c817bbf | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 33% | 50%/100% | 15520 |
| real-7149578a88 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 14206 |
| real-95de35b2ea | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 18316 |
| real-53851b4a23 | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 17810 |
| real-f4c6765fb9 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/100% | 18060 |
| real-67083a7bc1 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 12648 |

## Problems

- real-f6a1b89e41: title coverage 50% (expected keywords [change, golden-tool-use-real.json])
- real-f6a1b89e41: title verb class 'change' missing (got 'Extended golden tool-use eval set to r10 in golden-tool-use-real.json')
- real-f6a1b89e41: type 'feature' != expected 'change'
- real-d13e2c90cd: title coverage 50% (expected keywords [document, 2026-11-03_real-history-golden-eval-minimax.md])
- real-d13e2c90cd: title verb class 'document' missing (got 'Added 36-sample real-history golden eval in 2026-11-03_real-history-golden-eval-minimax.md')
- real-3b4f737562: title coverage 50% (expected keywords [document, minimax-after-v9.json])
- real-3b4f737562: title verb class 'document' missing (got 'Added minimax-after-v9 model output eval fixture in minimax-after-v9.json')
- real-f0d346eab1: type 'feature' != expected 'change'
- real-3a2d426fdf: title coverage 50% (expected keywords [change, goldensetscorer])
- real-3a2d426fdf: title verb class 'change' missing (got 'Extended promptTokens accounting and run-to-run sampling in GoldenSetScorer')
- real-3a2d426fdf: type 'feature' != expected 'change'
- real-864dadf51b: title coverage 50% (expected keywords [document, goldensetscorer])
- real-864dadf51b: title verb class 'document' missing (got 'Extended GoldenSetScorer evidence matching to include toolInput in GoldenSetScorer')
- real-b064308244: title coverage 50% (expected keywords [document, extraction-quality-improvement-plan.md])
- real-b064308244: title verb class 'document' missing (got 'Updated test gating description in extraction-quality-improvement-plan.md')
- real-30a956e462: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a9c8452967: title coverage 50% (expected keywords [document, searchsimilarityevaluator])
- real-a9c8452967: title verb class 'document' missing (got 'Added semantic relevance floor evaluation SearchSimilarityEvaluator')
- real-db8ac0287e: title coverage 50% (expected keywords [document, continuation.txt])
- real-db8ac0287e: title action [document, secure, modify] missing: title leads 'null' (got 'Shortened Fixed verb definition in continuation.txt')
- real-db8ac0287e: title verb class 'document' missing (got 'Shortened Fixed verb definition in continuation.txt')
- real-db8ac0287e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9e6612fdf9: title coverage 50% (expected keywords [document, continuation.txt])
- real-9e6612fdf9: title verb class 'document' missing (got 'Updated Fixed verb definition in continuation.txt')
- real-9e6612fdf9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e352ba3da3: title coverage 50% (expected keywords [document, longcat-after-v7.json])
- real-e352ba3da3: title verb class 'document' missing (got 'Added longcat-after-v7 eval fixtures and archived reports in longcat-after-v7.json')
- real-112de33e23: title coverage 50% (expected keywords [document, continuation.txt])
- real-112de33e23: title verb class 'document' missing (got 'Updated continuation.txt prompt resource with stray title-naming guidance text')
- real-28edef5a35: title coverage 50% (expected keywords [document, longcat-after-v6.json])
- real-28edef5a35: title verb class 'document' missing (got 'Added model output quality eval fixtures in longcat-after-v6.json')
- real-6d6df2c321: title coverage 50% (expected keywords [document, continuation.txt])
- real-6d6df2c321: title action [document, secure, modify] missing: title leads 'null' (got 'Expanded skip guidance in continuation.txt')
- real-6d6df2c321: title verb class 'document' missing (got 'Expanded skip guidance in continuation.txt')
- real-6d6df2c321: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d3431639a9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-7093cb5c56: call failed: IllegalStateException: all attempts returned no content
- real-f3d12860ff: title coverage 50% (expected keywords [harden, logscontrollertest])
- real-f3d12860ff: title verb class 'harden' missing (got 'Refactored logDir injection with setField helper in LogsControllerTest')
- real-f3d12860ff: type 'refactor' != expected 'change'
- real-f3d12860ff: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-3409dda60c: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-f7b865efe5: title coverage 50% (expected keywords [harden, index.html])
- real-f7b865efe5: title verb class 'harden' missing (got 'Changed header background color to #f8f6f2 in index.html')
- real-f7b865efe5: concepts recall 0% (expected [problem-solution], got [what-changed, what-changed])
- real-a8cc1754bc: title coverage 50% (expected keywords [remove, pendingmessagerepository])
- real-a8cc1754bc: title verb class 'remove' missing (got 'Refactored PendingMessageRepository to use conversation-based scoping')
- real-a8cc1754bc: type 'refactor' != expected 'bugfix'
- real-a8cc1754bc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e2c887775a: title coverage 50% (expected keywords [remove, agents.md])
- real-e2c887775a: title verb class 'remove' missing (got 'Corrected /api/context/generate to /api/context/inject in AGENTS.md')
- real-e2c887775a: type 'change' != expected 'bugfix'
- real-e2c887775a: concepts recall 0% (expected [problem-solution], got [what-changed, gotcha])
- real-cf667b4d8b: title coverage 50% (expected keywords [remove, observationfeedbackentity])
- real-cf667b4d8b: title verb class 'remove' missing (got 'Updated Javadoc and ordering comment in ObservationFeedbackEntity')
- real-cf667b4d8b: type 'change' != expected 'bugfix'
- real-cf667b4d8b: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a0e77ca4ee: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d958416fdc: title coverage 50% (expected keywords [remove, patrol-state.json])
- real-d958416fdc: title verb class 'remove' missing (got 'Updated patrol rotation direction to backend in patrol-state.json')
- real-d958416fdc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9b424d0d02: title coverage 50% (expected keywords [remove, api-zh-cn.md])
- real-9b424d0d02: title action [remove, document] missing: title leads 'modify' (got 'Corrected GET /api/settings example defaults in API-zh-CN.md')
- real-9b424d0d02: title verb class 'remove' missing (got 'Corrected GET /api/settings example defaults in API-zh-CN.md')
- real-9b424d0d02: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-85480a720e: title coverage 50% (expected keywords [remove, testing-zh-cn.md])
- real-85480a720e: title action [remove, document] missing: title leads 'modify' (got 'Updated database env var from DB_HOST to DB_NAME in TESTING-zh-CN.md')
- real-85480a720e: title verb class 'remove' missing (got 'Updated database env var from DB_HOST to DB_NAME in TESTING-zh-CN.md')
- real-85480a720e: concepts recall 0% (expected [problem-solution], got [what-changed, what-changed])
- real-c67e7a5ad3: type 'change' != expected 'bugfix'
- real-c67e7a5ad3: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-bdf72f2509: type 'change' != expected 'bugfix'
- real-bdf72f2509: concepts recall 0% (expected [problem-solution], got [what-changed, gotcha])
- real-8ac2a32980: title action [remove, document, modify] missing: title leads 'null' (got 'Clarified default port in backend/Dockerfile and removed build-args sections from deployment docs')
- real-8ac2a32980: title verb class 'remove' missing (got 'Clarified default port in backend/Dockerfile and removed build-args sections from deployment docs')
- real-8ac2a32980: type 'change' != expected 'bugfix'
- real-8ac2a32980: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-1f9b5d09e7: expected <skip> but produced an observation
- real-d44a83721d: concepts recall 0% (expected [what-changed], got [why-it-exists, pattern])
- real-0fd4ffcc31: title coverage 50% (expected keywords [change, goldensetscorer])
- real-0fd4ffcc31: title verb class 'change' missing (got 'Extended skip-failure tracking in GoldenSetScorer')
- real-9a0e90bed5: title coverage 50% (expected keywords [harden, observationrepository])
- real-9a0e90bed5: title verb class 'harden' missing (got 'Replaced projectPath filter with memoryScopeId subquery in ObservationRepository')
- real-9a0e90bed5: type 'feature' != expected 'refactor'
- real-9a0e90bed5: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-63b5502fb9: title coverage 50% (expected keywords [harden, v20__conversation_memory_scope_expand.sql])
- real-63b5502fb9: title verb class 'harden' missing (got 'Extended conversation scope migration in V20__conversation_memory_scope_expand.sql')
- real-63b5502fb9: type 'change' != expected 'bugfix'
- real-63b5502fb9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d33efbd8ae: title coverage 50% (expected keywords [harden, conversationscopebackfillrunner])
- real-d33efbd8ae: title verb class 'harden' missing (got 'Added ConversationScopeBackfillRunner migration batch with TechnicalBucketClassifierTest')
- real-d33efbd8ae: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-cd6b972cfd: title coverage 50% (expected keywords [harden, summaryjobservice])
- real-cd6b972cfd: title verb class 'harden' missing (got 'Added summary job orchestration in SummaryJobService')
- real-cd6b972cfd: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-46e467bff6: title coverage 50% (expected keywords [harden, conversation-scope-preflight.sql])
- real-46e467bff6: title verb class 'harden' missing (got 'Added conversation scope preflight checks in conversation-scope-preflight.sql')
- real-46e467bff6: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-95de35b2ea: title coverage 50% (expected keywords [change, pom.xml])
- real-95de35b2ea: type 'change' != expected 'bugfix'
- real-95de35b2ea: concepts recall 0% (expected [problem-solution], got [what-changed, what-changed])
- real-53851b4a23: validation rejected the observation: title too long: 102 chars > 100

## Raw answers behind problems

### real-f6a1b89e41

```text
<observation>   <type>feature</type>   <title>Extended golden tool-use eval set to r10 in golden-tool-use-real.json</title>   <subtitle>Regenerated the real-history golden set for round 10, doubling the mechanically valid sample floor and retaining model titles for failure-cluster evidence.</subtitle>   <facts>     <fact>golden-tool-use-real.json version changed from "real-git-2026-11-03" to "real... [truncated]
```

### real-d13e2c90cd

```text
<observation>   <type>feature</type>   <title>Added 36-sample real-history golden eval in 2026-11-03_real-history-golden-eval-minimax.md</title>   <subtitle>New GoldenSetRealReplayTest replays 36 real minimax history samples against a golden fixture with metrics.</subtitle>   <facts>     <fact>GoldenSetRealReplayTest added at backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetRealReplayTe... [truncated]
```

### real-3b4f737562

```text
<observation>   <type>feature</type>   <title>Added minimax-after-v9 model output eval fixture in minimax-after-v9.json</title>   <subtitle>New golden-set eval fixture and metrics for minimax-after-v9, plus evaluator support and archive documentation.</subtitle>   <facts>     <fact>Added backend/src/test/resources/eval/fixtures/minimax-after-v9.json with 2096 lines of eval fixture data.</fact>    ... [truncated]
```

### real-f0d346eab1

```text
<observation>   <type>feature</type>   <title>Added real-history tool-use golden evaluation dataset in golden-tool-use-real.json</title>   <subtitle>New mechanically generated golden set and test harness score tool-use evaluation against real history.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json was added with 1097 lines as the real-history golden datase... [truncated]
```

### real-3a2d426fdf

```text
<observation>   <type>feature</type>   <title>Extended promptTokens accounting and run-to-run sampling in GoldenSetScorer</title>   <subtitle>Eval harness now tracks prompt token usage and can repeat every sample via EVAL_REPEAT_ALL to measure run-to-run variability.</subtitle>   <facts>     <fact>GoldenSetScorer.java replaced the delegating constructor call this(sample, raw, skipped, observation,... [truncated]
```

### real-864dadf51b

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetScorer evidence matching to include toolInput in GoldenSetScorer</title>   <subtitle>GoldenSetScorer test evidence now uses full toolInput instead of only toolName, with the old title-subject hit rate marked legacy.</subtitle>   <facts>     <fact>GoldenSetScorer.java replaced `String evidence = (sample.toolName == null ? "" : sample.t... [truncated]
```

### real-b064308244

```text
<observation>   <type>change</type>   <title>Updated test gating description in extraction-quality-improvement-plan.md</title>   <subtitle>Draft plan text now describes the offline-model test as running under the rehearsal database gate instead of the L4 layer.</subtitle>   <facts>     <fact>The sentence "The test runs in the L4 layer under the rehearsal" was replaced with "The test runs under the... [truncated]
```

### real-30a956e462

```text
<observation>   <type>change</type>   <title>Documented Round 8 insertion-scope contract in extraction-quality-improvement-plan.md</title>   <subtitle>Added section 7.14 covering insertion-scope contract, coverage removal, and a reverted security verb.</subtitle>   <facts>     <fact>Added section 7.14 titled "Round 8 — insertion-scope contract, remove coverage, and a reverted security verb" to doc... [truncated]
```

### real-a9c8452967

```text
<observation>   <type>feature</type>   <title>Added semantic relevance floor evaluation SearchSimilarityEvaluator</title>   <subtitle>New evaluator harness replays BGE-M3 search similarity fixtures and asserts semantic relevance floor metrics.</subtitle>   <facts>     <fact>SearchSimilarityEvaluator.java is a new 411-line evaluator harness under backend/src/test/java/com/ablueforce/cortexce/eval/<... [truncated]
```

### real-db8ac0287e

```text
<observation>   <type>change</type>   <title>Shortened Fixed verb definition in continuation.txt</title>   <subtitle>Removed the "in non-security code" qualifier from the Fixed definition in both prompt templates.</subtitle>   <facts>     <fact>The line "- Fixed: a defect already produced wrong or failing behaviour, in non-security code." became "- Fixed: a defect already produced wrong or failing... [truncated]
```

### real-9e6612fdf9

```text
```xml <observation>   <type>feature</type>   <title>Updated Fixed verb definition in continuation.txt</title>   <subtitle>Observer prompt now restricts the "Fixed" verb to non-security code, and golden set tests add "extend" vocabulary.</subtitle>   <facts>     <fact>continuation.txt and init.txt now define "Fixed" as "a defect already produced wrong or failing behaviour, in non-security code".</... [truncated]
```

### real-e352ba3da3

```text
```xml <observation>   <type>feature</type>   <title>Added longcat-after-v7 eval fixtures and archived reports in longcat-after-v7.json</title>   <subtitle>Newly created eval fixture JSON, metrics, and archived model output quality reports for longcat and minimax after v7.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v7.json was created with 656 lines of mo... [truncated]
```

### real-112de33e23

```text
<observation>   <type>change</type>   <title>Updated continuation.txt prompt resource with stray title-naming guidance text</title>   <subtitle>Both prompt template files received the same three-line block of title-naming instruction text instead of application prompt content.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt gained 3 inserted lines beginning with ... [truncated]
```

### real-28edef5a35

```text
<observation>   <type>feature</type>   <title>Added model output quality eval fixtures in longcat-after-v6.json</title>   <subtitle>New after-v6 eval fixtures and archived reports capture model output quality for longcat and minimax.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v6.json was created with 656 lines.</fact>     <fact>backend/src/test/resources/... [truncated]
```

### real-6d6df2c321

```text
<observation>   <type>change</type>   <title>Expanded skip guidance in continuation.txt</title>   <subtitle>Prompt instruction files now enumerate skip conditions and recording rules for observation turns.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt changed by 11 deletions and 71 insertions, adding skip-condition guidance such as "only locates where an identi... [truncated]
```

### real-d3431639a9

```text
<observation>   <type>change</type>   <title>Documented quality evaluation in 2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md</title>   <subtitle>Five archival and planning documents added for minimax/longcat output and summary-chain quality evaluation</subtitle>   <facts>     <fact>Created docs/archive/2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md with 598 li... [truncated]
```

### real-7093cb5c56

```text
(no raw answer: the call failed before returning)
```

### real-f3d12860ff

```text
<observation>   <type>refactor</type>   <title>Refactored logDir injection with setField helper in LogsControllerTest</title>   <subtitle>Replaced direct reflection lookup of the logDir field with a reusable setField helper call.</subtitle>   <facts>     <fact>LogsControllerTest.java replaced the reflection statement `LogsController.class.getDeclaredField("logDir")` with the helper call `setField(... [truncated]
```

### real-3409dda60c

```text
<observation>   <type>change</type>   <title>Documented implementation commit range in conversation-memory-scope-refactor-plan.md</title>   <subtitle>Filled the implementation commit range placeholder in the memory scope refactor plan with the actual commit range and a pointer to the commit list.</subtitle>   <facts>     <fact>Replaced the placeholder row `| 实施 commit 范围 | 待实施 |` with `| 实施 commit... [truncated]
```

### real-f7b865efe5

```text
<observation>   <type>change</type>   <title>Changed header background color to #f8f6f2 in index.html</title>   <subtitle>Header background recolored from cool gray to warm off-white; viewer bundle and webui submodule updated accordingly.</subtitle>   <facts>     <fact>`--color-bg-header` changed from `#f6f8fa` to `#f8f6f2` in `backend/src/main/resources/static/index.html`.</fact>     <fact>`backe... [truncated]
```

### real-a8cc1754bc

```text
<observation>   <type>refactor</type>   <title>Refactored PendingMessageRepository to use conversation-based scoping</title>   <subtitle>Removed session-based dedup logic and aligned repository queries on conversation identifiers.</subtitle>   <facts>     <fact>PendingMessageRepository.java removed 22 lines including the dedup comment "// Dedup: check if a message with same session + tool already ... [truncated]
```

### real-e2c887775a

```text
<observation>   <type>change</type>   <title>Corrected /api/context/generate to /api/context/inject in AGENTS.md</title>   <subtitle>Fixed a hard-contract table entry that pointed the updateFiles proxy route at the wrong endpoint.</subtitle>   <facts>     <fact>AGENTS.md hard-contract table row for `updateFiles` now names `/api/context/inject` instead of `/api/context/generate`.</fact>     <fact>T... [truncated]
```

### real-cf667b4d8b

```text
<observation>   <type>change</type>   <title>Updated Javadoc and ordering comment in ObservationFeedbackEntity</title>   <subtitle>Condensed the entity Javadoc block and corrected the repository comment to say feedback is returned most recent first.</subtitle>   <facts>     <fact>ObservationFeedbackEntity.java had a Javadoc block starting with "/**" replaced with a single "*" line, removing 10 lin... [truncated]
```

### real-a0e77ca4ee

```text
```xml <observation>   <type>change</type>   <title>Removed drifted Lines column from TESTING.md tables</title>   <subtitle>Dropped the line-count column and fixed duplicate table headers in testing docs, synced across Chinese translation and patrol draft notes.</subtitle>   <facts>     <fact>docs/TESTING.md table header changed from '| Script | Description | Lines |' to '| Script | Description |'... [truncated]
```

### real-d958416fdc

```text
<observation>   <type>change</type>   <title>Updated patrol rotation direction to backend in patrol-state.json</title>   <subtitle>Patrol state and rotation docs now record the backend review as the last completed direction instead of demo.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json sets lastDirection to "backend", replacing "demo".</fact>     <fact>docs/drafts/patrol-rotation.md... [truncated]
```

### real-9b424d0d02

```text
```xml <observation>   <type>change</type>   <title>Corrected GET /api/settings example defaults in API-zh-CN.md</title>   <subtitle>Updated 6 example values in Chinese API docs to match actual AppSettings.toMap() defaults.</subtitle>   <facts>     <fact>GET /api/settings documentation in docs/API-zh-CN.md now reflects actual AppSettings.toMap() defaults: PROVIDER changed from openai to claude, MO... [truncated]
```

### real-85480a720e

```text
<observation>   <type>change</type>   <title>Updated database env var from DB_HOST to DB_NAME in TESTING-zh-CN.md</title>   <subtitle>Corrected environment variable documentation from DB_HOST to DB_NAME in both English and Chinese testing guides.</subtitle>   <facts>     <fact>docs/TESTING-zh-CN.md now lists environment variable DB_NAME with value claude_mem and description 数据库名称, replacing DB_HOS... [truncated]
```

### real-c67e7a5ad3

```text
<observation>   <type>change</type>   <title>Removed else clause in dto.py serialization logic</title>   <subtitle>Deleted a redundant else branch from the DTO serialization method in dto.py.</subtitle>   <facts>     <fact>Removed 2 lines (else: branch) from python-sdk/cortex-mem-python/cortex_mem/dto.py</fact>     <fact>Added memory/.dreams to .gitignore to exclude dream state files from version ... [truncated]
```

### real-bdf72f2509

```text
<observation>   <type>change</type>   <title>Removed SDK version comment from CortexMemClientImpl</title>   <subtitle>Deleted the Javadoc comment instructing developers to update the SDK version for the User-Agent header on release.</subtitle>   <facts>     <fact>The comment `/** SDK version for User-Agent header. Update when releasing new SDK versions. */` was removed from CortexMemClientImpl.jav... [truncated]
```

### real-8ac2a32980

```text
<observation>   <type>change</type>   <title>Clarified default port in backend/Dockerfile and removed build-args sections from deployment docs</title>   <subtitle>Dockerfile port comment now names 37777; obsolete build-arguments sections removed from both deployment guides.</subtitle>   <facts>     <fact>backend/Dockerfile comment changed to "# Default port (37777 — can be overridden by SERVER_POR... [truncated]
```

### real-1f9b5d09e7

```text
<observation>   <type>discovery</type>   <title>Reviewed health-check history 2026-10-07_health-check-history-32.md and patrol rotation notes</title>   <subtitle>Read-only review of archived health-check history and current patrol rotation state with no changes applied.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json contains "lastDirection": "python-sdk", indicating the most recent p... [truncated]
```

### real-d44a83721d

```text
<observation>   <type>change</type>   <title>Documented Round 5 metric correctness and title ceiling in extraction-quality-improvement-plan.md</title>   <subtitle>Added section 7.11 outlining the fifth round of extraction quality improvements targeting metric correctness and the remaining title ceiling.</subtitle>   <facts>     <fact>Section heading "## 7.11 Round 5 — metric correctness and the re... [truncated]
```

### real-0fd4ffcc31

```text
<observation>   <type>feature</type>   <title>Extended skip-failure tracking in GoldenSetScorer</title>   <subtitle>Golden-set scoring tests now distinguish record-worthy skipped samples (skipFp) from other skips (skipFn).</subtitle>   <facts>     <fact>GoldenSetScorer.java gained 29 lines and lost 2, adding skip-failure counter logic.</fact>     <fact>GoldenSetEvaluator.java now documents skipFp ... [truncated]
```

### real-9a0e90bed5

```text
<observation>   <type>feature</type>   <title>Replaced projectPath filter with memoryScopeId subquery in ObservationRepository</title>   <subtitle>Observation queries now resolve project scope through a new MemoryScopeEntity instead of matching projectPath directly.</subtitle>   <facts>     <fact>ObservationRepository WHERE clause changed from `o.projectPath = :project` to `o.memoryScopeId IN (` s... [truncated]
```

### real-63b5502fb9

```text
<observation>   <type>change</type>   <title>Extended conversation scope migration in V20__conversation_memory_scope_expand.sql</title>   <subtitle>Rewrote the V20 migration into a one-shot conversation/memory scope identity migration and added rehearsal and fixture scripts.</subtitle>   <facts>     <fact>V20__conversation_memory_scope_expand.sql now opens with the comment `-- V20: Conversation / ... [truncated]
```

### real-d33efbd8ae

```text
<observation>   <type>feature</type>   <title>Added ConversationScopeBackfillRunner migration batch with TechnicalBucketClassifierTest</title>   <subtitle>New conversation-scope backfill runner ships with technical bucket classifier, CLI entry point, and unit tests.</subtitle>   <facts>     <fact>ConversationScopeBackfillRunner.java was created with 845 lines in com.ablueforce.cortexce.migration.<... [truncated]
```

### real-cd6b972cfd

```text
<observation>   <type>feature</type>   <title>Added summary job orchestration in SummaryJobService</title>   <subtitle>A new Java service class was created to handle summary job processing.</subtitle>   <facts>     <fact>Created new service class SummaryJobService at backend/src/main/java/com/ablueforce/cortexce/service/SummaryJobService.java under package com.ablueforce.cortexce.service.</fact>  ... [truncated]
```

### real-46e467bff6

```text
<observation>   <type>feature</type>   <title>Added conversation scope preflight checks in conversation-scope-preflight.sql</title>   <subtitle>Created a preflight SQL script, an additive V20 migration, and a fixture script for conversation/memory scope identity.</subtitle>   <facts>     <fact>scripts/db/conversation-scope-preflight.sql was created as a new file with 426 lines of preflight SQL.</f... [truncated]
```

### real-95de35b2ea

```text
<observation>   <type>change</type>   <title>Updated byte-buddy pin comment in pom.xml</title>   <subtitle>Rewrote the byte-buddy pin rationale in the Maven POM and the Docker build-context comment in the e2e script.</subtitle>   <facts>     <fact>backend/pom.xml comment now reads "Pin byte-buddy to 1.15.x: Mockito 5.11 (via spring-boot-starter-test BOM)" instead of citing JDK 24 compatibility.</f... [truncated]
```

### real-53851b4a23

```text
<observation>   <type>change</type>   <title>Added Health Check History Archive 11 covering rounds 312-359 in 2026-10-08_health-check-history-11.md</title>   <subtitle>Created a 1206-line archive document consolidating health check history rounds 312 through 359+ from October 2026.</subtitle>   <facts>     <fact>docs/archive/2026-10-08_health-check-history-11.md was created with 1206 lines coverin... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
