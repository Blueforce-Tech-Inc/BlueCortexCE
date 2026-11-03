golden set: real (60 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: current
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 60 unique; runs scored: 1; key repeat: 1; real calls: 60 (cap none); duration: 285398 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 98% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 10/0/0/50 |
| format errors | 0 |
| validation rejections | 1 |
| call failures | 0 |
| call success rate | 100% (0/60 failed) |
| title exact-hit rate (legacy) | 45% |
| title normalized hit rate (legacy) | 45% |
| title action-class hit rate (A6) | 100% (49 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 47% |
| title subject hit rate (legacy, diagnostic) | 100% |
| title trace-visible subject hit rate (A6b) | 100% (49 scored, 0 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 100% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 100% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 47% |
| title identifier hit rate | 100% |
| type accuracy (raw) | 76% |
| type accuracy (auditable labels only) | 84% (6 unverifiable label(s) excluded) |
| facts keyword precision (legacy) / recall | 100% / 100% |
| facts informative precision | 93% |
| facts trace-support rate | 100% |
| concepts precision / recall | 29% / 57% |
| skip consistency across repeats | 0% (0 repeated samples) |
| latency p50 / p95 / max (ms) | 3330 / 14227 / 20097 |
| tokens p50 / p95 / max / mean per call | 5453 / 6583 / 7039 / 5576 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 93% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 100% | PASS |
| A6b | title trace-visible subject | >= 90% | 100% | PASS |
| A6c | title identifiers | = 100% | 100% | PASS |
| A6 | all three title dimensions together | all pass | 100% | PASS |
| A13 | provider call success rate | >= 99% | 100% (0/60 failed) | PASS |

## Status distribution

| status | calls |
|---|---|
| 200 | 60 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 60 | 100% | 45% | 100% | 100% | 93% | 100% | 0 |

- average: skip 100%, title 45%, title contract 100%, facts P/R 100%/100%, facts actionable 93%
- worst run: skip 100%, title 45%, title contract 100%, facts P/R 100%/100%, facts actionable 93%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| feature | 12 | 12/12 | 10/12 | 3/12 |
| config-change | 1 | 1/1 | 1/1 | 1/1 |
| refactor | 2 | 2/2 | 0/2 | 0/2 |
| docs-update | 20 | 19/20 | 19/20 | 14/20 |
| test-add | 2 | 2/2 | 2/2 | 1/2 |
| security-fix | 7 | 7/7 | 4/7 | 1/7 |
| bug-fix | 6 | 6/6 | 1/6 | 2/6 |
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

| cluster | misses | share of scored (49) |
|---|---|---|
| identifier missing | 0 | 0% |
| action class wrong | 0 | 0% |
| subject wrong | 0 | 0% |
| type wrong | 12 | 24% |

### type wrong (12)

- real-f6a1b89e41 — expected 'change', got 'feature' (title 'Extended real-history golden set to round 10 in golden-tool-use-real.json')
- real-f0d346eab1 — expected 'change', got 'feature' (title 'Added real-history golden tool-use evaluation dataset in golden-tool-use-real.json')
- real-3a2d426fdf — expected 'change', got 'feature' (title 'Extended golden-set token metrics in GoldenSetScorer')
- real-f3d12860ff — expected 'change', got 'bugfix' (title 'Hardened credential handling in LogsControllerTest')
- real-cf667b4d8b — expected 'bugfix', got 'change' (title 'Updated observation feedback ordering in ObservationFeedbackEntity')
- real-c67e7a5ad3 — expected 'bugfix', got 'change' (title 'Removed empty else branch in dto.py')
- real-bdf72f2509 — expected 'bugfix', got 'refactor' (title 'Removed SDK version comment in CortexMemClientImpl')
- real-8ac2a32980 — expected 'bugfix', got 'change' (title 'Updated default SERVER_PORT comment in Dockerfile')
- real-9a0e90bed5 — expected 'refactor', got 'bugfix' (title 'Fixed observation scope filtering by memoryScopeId in ObservationRepository')
- real-63b5502fb9 — expected 'bugfix', got 'change' (title 'Migrated conversation memory scope identity in V20__conversation_memory_scope_expand.sql')
- real-46e467bff6 — expected 'feature', got 'change' (title 'Added conversation scope preflight and fixture in conversation-scope-preflight.sql')
- real-95de35b2ea — expected 'bugfix', got 'change' (title 'Updated byte-buddy pinning and Docker build context in pom.xml')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| real-f6a1b89e41 | feature | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 3634 |
| real-1fe4ffb124 | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 6728 |
| real-d13e2c90cd | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 6042 |
| real-3b4f737562 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 14275 |
| real-77ba40df77 | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 3010 |
| real-f0d346eab1 | feature | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/100% | 5148 |
| real-3a2d426fdf | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 3351 |
| real-864dadf51b | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 75% | 50%/100% | 3958 |
| real-b064308244 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 7209 |
| real-30a956e462 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 2118 |
| real-ba86bf2588 | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/100% | 7737 |
| real-a9c8452967 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 3330 |
| real-db8ac0287e | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 50% | 0%/0% | 2096 |
| real-9e6612fdf9 | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 20097 |
| real-f43cfd6f44 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 4235 |
| real-e352ba3da3 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 6537 |
| real-112de33e23 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3114 |
| real-28edef5a35 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 8291 |
| real-6d6df2c321 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 5503 |
| real-d3431639a9 | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 9612 |
| real-7093cb5c56 | security-fix | observation | observation | pass | 0% | hit | miss | match | 100%/100% | 100% | 50%/100% | 4998 |
| real-f3d12860ff | security-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 67% | 50%/100% | 2728 |
| real-3409dda60c | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 10929 |
| real-f7b865efe5 | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 4131 |
| real-a8cc1754bc | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 75% | 50%/100% | 7224 |
| real-e2c887775a | bug-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 75% | 50%/100% | 3771 |
| real-cf667b4d8b | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 2216 |
| real-a0e77ca4ee | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 2770 |
| real-d958416fdc | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 80% | 0%/0% | 3435 |
| real-9b424d0d02 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 3577 |
| real-85480a720e | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 5859 |
| real-c67e7a5ad3 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 1576 |
| real-bdf72f2509 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 12302 |
| real-8ac2a32980 | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 4469 |
| real-2025b33a95 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2682 |
| real-e8c8b314d6 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2363 |
| real-c42d7a9c5d | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1734 |
| real-b5ae725e2a | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 955 |
| real-9261b542ca | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 829 |
| real-7c557c8d79 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1148 |
| real-1dbccd058e | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1460 |
| real-1f9b5d09e7 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2354 |
| real-ed737be6bb | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 991 |
| real-b2d48b12a1 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1010 |
| real-bc3512ae8d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 2840 |
| real-e02e436bdd | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 50% | 50%/100% | 3014 |
| real-d44a83721d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 2162 |
| real-0fd4ffcc31 | test-add | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 2296 |
| real-eee7785ec8 | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/100% | 3378 |
| real-9a0e90bed5 | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 19008 |
| real-63b5502fb9 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 14227 |
| real-d33efbd8ae | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 3288 |
| real-cd6b972cfd | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 2391 |
| real-46e467bff6 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 3477 |
| real-325c817bbf | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 2609 |
| real-7149578a88 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3152 |
| real-95de35b2ea | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 2437 |
| real-53851b4a23 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 3691 |
| real-f4c6765fb9 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/100% | 2407 |
| real-67083a7bc1 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3112 |

## Problems

- real-f6a1b89e41: title coverage 50% (expected keywords [change, golden-tool-use-real.json])
- real-f6a1b89e41: title verb class 'change' missing (got 'Extended real-history golden set to round 10 in golden-tool-use-real.json')
- real-f6a1b89e41: type 'feature' != expected 'change'
- real-d13e2c90cd: title coverage 50% (expected keywords [document, 2026-11-03_real-history-golden-eval-minimax.md])
- real-d13e2c90cd: title verb class 'document' missing (got 'Added real-history golden replay suite in 2026-11-03_real-history-golden-eval-minimax.md')
- real-3b4f737562: title coverage 50% (expected keywords [document, minimax-after-v9.json])
- real-3b4f737562: title verb class 'document' missing (got 'Added MiniMax model-output evaluation fixture minimax-after-v9.json')
- real-77ba40df77: concepts recall 0% (expected [what-changed], got [pattern, trade-off])
- real-f0d346eab1: type 'feature' != expected 'change'
- real-3a2d426fdf: title coverage 50% (expected keywords [change, goldensetscorer])
- real-3a2d426fdf: title verb class 'change' missing (got 'Extended golden-set token metrics in GoldenSetScorer')
- real-3a2d426fdf: type 'feature' != expected 'change'
- real-864dadf51b: title coverage 50% (expected keywords [document, goldensetscorer])
- real-864dadf51b: title verb class 'document' missing (got 'Extended GoldenSetScorer with evidence-based title matching')
- real-30a956e462: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a9c8452967: title coverage 50% (expected keywords [document, searchsimilarityevaluator])
- real-a9c8452967: title verb class 'document' missing (got 'Added semantic relevance replay evaluation to SearchSimilarityEvaluator')
- real-db8ac0287e: title coverage 50% (expected keywords [document, continuation.txt])
- real-db8ac0287e: title verb class 'document' missing (got 'Updated bugfix classification in continuation.txt')
- real-db8ac0287e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9e6612fdf9: title coverage 50% (expected keywords [document, continuation.txt])
- real-9e6612fdf9: title verb class 'document' missing (got 'Updated non-security defect classification guidance in continuation.txt')
- real-9e6612fdf9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e352ba3da3: title coverage 50% (expected keywords [document, longcat-after-v7.json])
- real-e352ba3da3: title verb class 'document' missing (got 'Added model output evaluation fixtures and reports for longcat-after-v7.json')
- real-28edef5a35: title coverage 50% (expected keywords [document, longcat-after-v6.json])
- real-28edef5a35: title verb class 'document' missing (got 'Added model output evaluation fixtures longcat-after-v6.json')
- real-6d6df2c321: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d3431639a9: validation rejected the observation: title too long: 111 chars > 100
- real-7093cb5c56: title coverage 0% (expected keywords [harden, embeddingbackfillconcurrencydatabasetest])
- real-7093cb5c56: title verb class 'harden' missing (got 'Fixed embedding backfill concurrency in EmbeddingBackfillConcurrencyDatabaseTest')
- real-f3d12860ff: type 'bugfix' != expected 'change'
- real-3409dda60c: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-f7b865efe5: title coverage 50% (expected keywords [harden, index.html])
- real-f7b865efe5: title verb class 'harden' missing (got 'Updated viewer interface and header color in index.html')
- real-f7b865efe5: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a8cc1754bc: title coverage 50% (expected keywords [remove, pendingmessagerepository])
- real-a8cc1754bc: title verb class 'remove' missing (got 'Hardened message deduplication and scope filtering in PendingMessageRepository')
- real-e2c887775a: title coverage 50% (expected keywords [remove, agents.md])
- real-e2c887775a: title verb class 'remove' missing (got 'Fixed /api/context/inject endpoint mapping in AGENTS.md')
- real-cf667b4d8b: title coverage 50% (expected keywords [remove, observationfeedbackentity])
- real-cf667b4d8b: title verb class 'remove' missing (got 'Updated observation feedback ordering in ObservationFeedbackEntity')
- real-cf667b4d8b: type 'change' != expected 'bugfix'
- real-cf667b4d8b: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a0e77ca4ee: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d958416fdc: title coverage 50% (expected keywords [remove, patrol-state.json])
- real-d958416fdc: title verb class 'remove' missing (got 'Updated patrol direction to backend in patrol-state.json')
- real-d958416fdc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9b424d0d02: title coverage 50% (expected keywords [remove, api-zh-cn.md])
- real-9b424d0d02: title verb class 'remove' missing (got 'Documented GET /api/settings default value corrections in API-zh-CN.md')
- real-9b424d0d02: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-85480a720e: title coverage 50% (expected keywords [remove, testing-zh-cn.md])
- real-85480a720e: title verb class 'remove' missing (got 'Documented DB_NAME environment variable in TESTING-zh-CN.md')
- real-85480a720e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-c67e7a5ad3: type 'change' != expected 'bugfix'
- real-c67e7a5ad3: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-bdf72f2509: type 'refactor' != expected 'bugfix'
- real-bdf72f2509: concepts recall 0% (expected [problem-solution], got [what-changed, what-changed])
- real-8ac2a32980: title coverage 50% (expected keywords [remove, dockerfile])
- real-8ac2a32980: title verb class 'remove' missing (got 'Updated default SERVER_PORT comment in Dockerfile')
- real-8ac2a32980: type 'change' != expected 'bugfix'
- real-8ac2a32980: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e02e436bdd: title coverage 50% (expected keywords [document, continuation.txt])
- real-e02e436bdd: title verb class 'document' missing (got 'Updated prompt category examples in continuation.txt')
- real-0fd4ffcc31: title coverage 50% (expected keywords [change, goldensetscorer])
- real-0fd4ffcc31: title verb class 'change' missing (got 'Extended skipped-sample scoring in GoldenSetScorer')
- real-9a0e90bed5: title coverage 50% (expected keywords [harden, observationrepository])
- real-9a0e90bed5: title verb class 'harden' missing (got 'Fixed observation scope filtering by memoryScopeId in ObservationRepository')
- real-9a0e90bed5: type 'bugfix' != expected 'refactor'
- real-63b5502fb9: title coverage 50% (expected keywords [harden, v20__conversation_memory_scope_expand.sql])
- real-63b5502fb9: title verb class 'harden' missing (got 'Migrated conversation memory scope identity in V20__conversation_memory_scope_expand.sql')
- real-63b5502fb9: type 'change' != expected 'bugfix'
- real-63b5502fb9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d33efbd8ae: title coverage 50% (expected keywords [harden, conversationscopebackfillrunner])
- real-d33efbd8ae: title verb class 'harden' missing (got 'Added conversation scope backfill runner in ConversationScopeBackfillRunner')
- real-d33efbd8ae: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-cd6b972cfd: title coverage 50% (expected keywords [harden, summaryjobservice])
- real-cd6b972cfd: title verb class 'harden' missing (got 'Added SummaryJobService implementation in SummaryJobService')
- real-cd6b972cfd: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-46e467bff6: title coverage 50% (expected keywords [harden, conversation-scope-preflight.sql])
- real-46e467bff6: title verb class 'harden' missing (got 'Added conversation scope preflight and fixture in conversation-scope-preflight.sql')
- real-46e467bff6: type 'change' != expected 'feature'
- real-46e467bff6: concepts recall 0% (expected [problem-solution], got [pattern, trade-off])
- real-95de35b2ea: title coverage 50% (expected keywords [change, pom.xml])
- real-95de35b2ea: type 'change' != expected 'bugfix'
- real-95de35b2ea: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-53851b4a23: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])

## Raw answers behind problems

### real-f6a1b89e41

```text
<observation>   <type>feature</type>   <title>Extended real-history golden set to round 10 in golden-tool-use-real.json</title>   <subtitle>The evaluation fixture and supporting scorer, generator, and validation types now capture expanded round-10 failure-cluster evidence.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json changed version from "real-git-2026-1... [truncated]
```

### real-d13e2c90cd

```text
<observation>   <type>feature</type>   <title>Added real-history golden replay suite in 2026-11-03_real-history-golden-eval-minimax.md</title>   <subtitle>A documented 36-sample golden evaluation was added with replay tests and metric fixtures.</subtitle>   <facts>     <fact>docs/archive/2026-11-03_real-history-golden-eval-minimax.md documents a real golden set containing 36 samples.</fact>     <f... [truncated]
```

### real-3b4f737562

```text
<observation>   <type>feature</type>   <title>Added MiniMax model-output evaluation fixture minimax-after-v9.json</title>   <subtitle>New MiniMax v9 fixtures, metrics, archived results, and evaluator reporting support model-output quality evaluation.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/minimax-after-v9.json now contains 2,096 lines of JSON evaluation fixture dat... [truncated]
```

### real-77ba40df77

```text
<observation>   <type>feature</type>   <title>Added extraction consistency voting in ExtractionConsistency</title>   <subtitle>Agent extraction now supports configurable multi-call voting with consistency checks.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/service/ExtractionConsistency.java adds the ExtractionConsistency service with 82 lines.</fact>     <fact>back... [truncated]
```

### real-f0d346eab1

```text
<observation>   <type>feature</type>   <title>Added real-history golden tool-use evaluation dataset in golden-tool-use-real.json</title>   <subtitle>Mechanically generated golden data, validation tests, and evaluator selection now support scoring against real tool-use histories.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json adds 1097 lines of real-history... [truncated]
```

### real-3a2d426fdf

```text
<observation>   <type>feature</type>   <title>Extended golden-set token metrics in GoldenSetScorer</title>   <subtitle>Golden-set evaluation now captures prompt-token metrics and supports repeating every sample for run-to-run measurements.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java adds public final int promptTokens to golden-set scoring ... [truncated]
```

### real-864dadf51b

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetScorer with evidence-based title matching</title>   <subtitle>Golden-set scoring now evaluates tool-input evidence and reports the legacy title-subject metric separately.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java added evidence handling based on sample.toolName and sample.to... [truncated]
```

### real-30a956e462

```text
<observation>   <type>change</type>   <title>Documented Round 8 extraction scope and security contract in extraction-quality-improvement-plan.md</title>   <subtitle>The extraction quality plan now defines Round 8 insertion scope, coverage removal, and a reverted security verb.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md added 139 lines with no deletions.</fact... [truncated]
```

### real-a9c8452967

```text
<observation>   <type>feature</type>   <title>Added semantic relevance replay evaluation to SearchSimilarityEvaluator</title>   <subtitle>Replay fixtures now evaluate BGE-M3 search similarity against a labeled corpus and compare recorded metrics.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/SearchSimilarityEvaluator.java adds 411 lines for semantic relevance ev... [truncated]
```

### real-db8ac0287e

```text
<observation>   <type>change</type>   <title>Updated bugfix classification in continuation.txt</title>   <subtitle>Prompt guidance now classifies all fixed defects as bugfixes without limiting the category to non-security code.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt now defines Fixed as a defect that produced wrong or failing behaviour without the non-se... [truncated]
```

### real-9e6612fdf9

```text
<observation>   <type>feature</type>   <title>Updated non-security defect classification guidance in continuation.txt</title>   <subtitle>Prompt guidance now distinguishes non-security defects, while the evaluator accepts extend as a modify action.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt and backend/src/main/resources/prompts/init.txt now define Fixed as ... [truncated]
```

### real-e352ba3da3

```text
<observation>   <type>feature</type>   <title>Added model output evaluation fixtures and reports for longcat-after-v7.json</title>   <subtitle>Evaluation fixtures, metrics, and archived quality reports now cover after-v7 outputs for longcat and minimax models.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v7.json contains 656 added lines of evaluation fixtur... [truncated]
```

### real-28edef5a35

```text
<observation>   <type>feature</type>   <title>Added model output evaluation fixtures longcat-after-v6.json</title>   <subtitle>LongCat and MiniMax v6 evaluation outputs, metrics, and archived reports now provide reproducible model-quality comparison data.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v6.json contains 656 added lines of model evaluation fixtu... [truncated]
```

### real-6d6df2c321

```text
<observation>   <type>change</type>   <title>Documented prompt continuation guidance in continuation.txt</title>   <subtitle>Expanded prompt instructions to clarify search-only, read-only, and verification behavior for continuation sessions.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt expanded from the replaced skip guidance to 71 added lines.</fact>     <fac... [truncated]
```

### real-d3431639a9

```text
<observation>   <type>change</type>   <title>Documented model output quality evaluation in 2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md</title>   <subtitle>Archived Round 4 evaluation reports and the extraction improvement plan now document contract, trace grounding, retrieval, and repair-review results.</subtitle>   <facts>     <fact>docs/archive/2026-11-03_model-output-quali... [truncated]
```

### real-7093cb5c56

```text
<observation>   <type>bugfix</type>   <title>Fixed embedding backfill concurrency in EmbeddingBackfillConcurrencyDatabaseTest</title>   <subtitle>Database concurrency coverage and service changes isolate backfill failures and add sanitization coverage.</subtitle>   <facts>     <fact>EmbeddingBackfillConcurrencyDatabaseTest.java added 333 lines covering concurrent database backfill behavior.</fact>... [truncated]
```

### real-f3d12860ff

```text
<observation>   <type>bugfix</type>   <title>Hardened credential handling in LogsControllerTest</title>   <subtitle>Log access now uses a safer field setup while controller behavior received expanded security-focused handling.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/controller/LogsControllerTest.java replaced direct logDir reflection with setField("logDir", log... [truncated]
```

### real-3409dda60c

```text
<observation>   <type>change</type>   <title>Documented implementation commit range in conversation-memory-scope-refactor-plan.md</title>   <subtitle>The refactor plan now identifies nine completed implementation commits and provides a supporting commit list.</subtitle>   <facts>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md sets the implementation commit range to `325c817..f7b8... [truncated]
```

### real-f7b865efe5

```text
<observation>   <type>change</type>   <title>Updated viewer interface and header color in index.html</title>   <subtitle>The embedded viewer was rebuilt with a warmer header color and refreshed static assets.</subtitle>   <facts>     <fact>backend/src/main/resources/static/index.html changes --color-bg-header from #f6f8fa to #f8f6f2.</fact>     <fact>backend/src/main/resources/static/index.html ch... [truncated]
```

### real-a8cc1754bc

```text
<observation>   <type>bugfix</type>   <title>Hardened message deduplication and scope filtering in PendingMessageRepository</title>   <subtitle>Repository queries and service wiring now enforce conversation-aware deduplication and scope isolation.</subtitle>   <facts>     <fact>PendingMessageRepository removed 22 lines containing the message deduplication implementation.</fact>     <fact>SessionRe... [truncated]
```

### real-e2c887775a

```text
<observation>   <type>bugfix</type>   <title>Fixed /api/context/inject endpoint mapping in AGENTS.md</title>   <subtitle>The immutable-contract table now references the endpoint that actually returns the updateFiles field.</subtitle>   <facts>     <fact>AGENTS.md maps `/api/context/inject` to `updateFiles` and `proxy/proxy.js` instead of `/api/context/generate`.</fact>     <fact>docs/drafts/doc-re... [truncated]
```

### real-cf667b4d8b

```text
<observation>   <type>change</type>   <title>Updated observation feedback ordering in ObservationFeedbackEntity</title>   <subtitle>Observation feedback Javadocs and repository ordering now describe returning the most recent feedback first.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/entity/ObservationFeedbackEntity.java changed its Javadoc delimiter from '/**' to ... [truncated]
```

### real-a0e77ca4ee

```text
<observation>   <type>change</type>   <title>Removed drifting line counts and duplicate table headers in TESTING.md</title>   <subtitle>English and Chinese testing guides now use synchronized two-column script tables.</subtitle>   <facts>     <fact>docs/TESTING.md removes the obsolete "| Script | Description | Lines |" column and 35 lines while adding 33 lines.</fact>     <fact>docs/TESTING-zh-CN.... [truncated]
```

### real-d958416fdc

```text
<observation>   <type>change</type>   <title>Updated patrol direction to backend in patrol-state.json</title>   <subtitle>Patrol records and architecture documentation now reflect a backend-focused review.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json sets "lastDirection" to "backend".</fact>     <fact>docs/drafts/patrol-rotation.md records "Last completed" as "Backend (2026-09-30 0... [truncated]
```

### real-9b424d0d02

```text
<observation>   <type>change</type>   <title>Documented GET /api/settings default value corrections in API-zh-CN.md</title>   <subtitle>Chinese and English API references now match six AppSettings.toMap() defaults in the GET /api/settings example.</subtitle>   <facts>     <fact>docs/API-zh-CN.md records a 2026-05-06 revision for version 0.1.0-beta+42 correcting six GET /api/settings example values... [truncated]
```

### real-85480a720e

```text
<observation>   <type>change</type>   <title>Documented DB_NAME environment variable in TESTING-zh-CN.md</title>   <subtitle>Chinese and English testing guides now identify DB_NAME with the default database name claude_mem.</subtitle>   <facts>     <fact>docs/TESTING-zh-CN.md documents DB_NAME with the default value claude_mem and the label 数据库名称.</fact>     <fact>docs/TESTING.md documents DB_NAME... [truncated]
```

### real-c67e7a5ad3

```text
<observation>   <type>change</type>   <title>Removed empty else branch in dto.py</title>   <subtitle>Simplified DTO control flow and ignored generated memory dream artifacts.</subtitle>   <facts>     <fact>python-sdk/cortex-mem-python/cortex_mem/dto.py removed an empty else branch with 2 deleted lines.</fact>     <fact>.gitignore added the memory/.dreams path.</fact>   </facts>   <narrative>The DT... [truncated]
```

### real-bdf72f2509

```text
<observation>   <type>refactor</type>   <title>Removed SDK version comment in CortexMemClientImpl</title>   <subtitle>Removed obsolete release guidance without changing executable client behavior.</subtitle>   <facts>     <fact>CortexMemClientImpl no longer contains the comment `/** SDK version for User-Agent header. Update when releasing new SDK versions. */`.</fact>     <fact>The edit to cortex-... [truncated]
```

### real-8ac2a32980

```text
<observation>   <type>change</type>   <title>Updated default SERVER_PORT comment in Dockerfile</title>   <subtitle>Docker deployment documentation now identifies the default server port as 37777.</subtitle>   <facts>     <fact>backend/Dockerfile changes the default port comment to 37777 while retaining SERVER_PORT override behavior.</fact>     <fact>docs/DEPLOYMENT-zh-CN.md removes the six-line bu... [truncated]
```

### real-e02e436bdd

```text
<observation>   <type>change</type>   <title>Updated prompt category examples in continuation.txt</title>   <subtitle>Prompt templates now use category-specific examples for consistent observation titles and concepts.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt replaces the UserCache.computeIfAbsent example with a Category examples section.</fact>     <fact>b... [truncated]
```

### real-0fd4ffcc31

```text
<observation>   <type>feature</type>   <title>Extended skipped-sample scoring in GoldenSetScorer</title>   <subtitle>Golden-set tests distinguish false-positive skips from record-worthy false-negative skips.</subtitle>   <facts>     <fact>GoldenSetEvaluator now tracks skipFp separately from skipFn for record-worthy sample skips.</fact>     <fact>GoldenSetReplayTest now asserts a.skipFp for the "mu... [truncated]
```

### real-9a0e90bed5

```text
<observation>   <type>bugfix</type>   <title>Fixed observation scope filtering by memoryScopeId in ObservationRepository</title>   <subtitle>Observation queries now filter project access through memory scope membership instead of direct project path equality.</subtitle>   <facts>     <fact>ObservationRepository filters `:project` using `o.memoryScopeId IN (...)` instead of `o.projectPath = :projec... [truncated]
```

### real-63b5502fb9

```text
<observation>   <type>change</type>   <title>Migrated conversation memory scope identity in V20__conversation_memory_scope_expand.sql</title>   <subtitle>The one-shot scope migration now has dedicated rehearsal and SQL fixtures for clean and conflicting data states.</subtitle>   <facts>     <fact>V20__conversation_memory_scope_expand.sql changed from 240 to 562 lines and starts with a one-shot con... [truncated]
```

### real-d33efbd8ae

```text
<observation>   <type>feature</type>   <title>Added conversation scope backfill runner in ConversationScopeBackfillRunner</title>   <subtitle>Added a Java migration runner with technical bucket classification, CLI orchestration, and classifier tests.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/migration/ConversationScopeBackfillRunner.java added 845 lines in packag... [truncated]
```

### real-cd6b972cfd

```text
<observation>   <type>feature</type>   <title>Added SummaryJobService implementation in SummaryJobService</title>   <subtitle>Created a 326-line summary job service with security-related authentication handling.</subtitle>   <facts>     <fact>SummaryJobService was added at backend/src/main/java/com/ablueforce/cortexce/service/SummaryJobService.java.</fact>     <fact>SummaryJobService contains 326 ... [truncated]
```

### real-46e467bff6

```text
<observation>   <type>change</type>   <title>Added conversation scope preflight and fixture in conversation-scope-preflight.sql</title>   <subtitle>Added database preflight, fixture, and additive migration scripts for conversation and memory scope identity expansion.</subtitle>   <facts>     <fact>scripts/db/conversation-scope-preflight.sql was created with 426 inserted lines.</fact>     <fact>bac... [truncated]
```

### real-95de35b2ea

```text
<observation>   <type>change</type>   <title>Updated byte-buddy pinning and Docker build context in pom.xml</title>   <subtitle>Build configuration now aligns byte-buddy with Mockito and uses the repository root as Docker context.</subtitle>   <facts>     <fact>backend/pom.xml pins byte-buddy to the 1.15.x line for Mockito 5.11 from the spring-boot-starter-test BOM.</fact>     <fact>scripts/docker... [truncated]
```

### real-53851b4a23

```text
<observation>   <type>change</type>   <title>Documented health check archive 11 history in 2026-10-08_health-check-history-11.md</title>   <subtitle>Archived health check rounds 312–359+ for October 6–7, 2026, including parallel process rounds.</subtitle>   <facts>     <fact>docs/archive/2026-10-08_health-check-history-11.md contains 1206 added lines under the heading “Health Check History Archive... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
