golden set: real (60 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: current
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 60 unique; runs scored: 1; key repeat: 1; real calls: 60 (cap none); duration: 300267 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 88% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 10/0/0/50 |
| format errors | 0 |
| validation rejections | 1 |
| call failures | 6 |
| title exact-hit rate (legacy) | 47% |
| title normalized hit rate (legacy) | 47% |
| title action-class hit rate (A6) | 98% (43 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 49% |
| title subject hit rate (legacy, diagnostic) | 98% |
| title trace-visible subject hit rate (A6b) | 98% (43 scored, 0 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 95% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 95% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 49% |
| title identifier hit rate | 98% |
| type accuracy (raw) | 79% |
| type accuracy (auditable labels only) | 89% (5 unverifiable label(s) excluded) |
| facts keyword precision (legacy) / recall | 100% / 100% |
| facts informative precision | 85% |
| facts trace-support rate | 100% |
| concepts precision / recall | 30% / 60% |
| skip consistency across repeats | 0% (0 repeated samples) |
| latency p50 / p95 / max (ms) | 3184 / 14357 / 27292 |
| tokens p50 / p95 / max / mean per call | 5328 / 6093 / 6215 / 4937 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 85% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 98% | PASS |
| A6b | title trace-visible subject | >= 90% | 98% | PASS |
| A6c | title identifiers | = 100% | 98% | **FAIL** |
| A6 | all three title dimensions together | all pass | 95% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 54 |
| exception | 6 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 60 | 100% | 47% | 95% | 100% | 85% | 100% | 0 |

- average: skip 100%, title 47%, title contract 95%, facts P/R 100%/100%, facts actionable 85%
- worst run: skip 100%, title 47%, title contract 95%, facts P/R 100%/100%, facts actionable 85%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| feature | 12 | 11/12 | 9/12 | 4/12 |
| config-change | 1 | 1/1 | 1/1 | 1/1 |
| refactor | 2 | 1/2 | 0/2 | 0/2 |
| docs-update | 20 | 18/20 | 18/20 | 14/20 |
| test-add | 2 | 2/2 | 2/2 | 1/2 |
| security-fix | 7 | 5/7 | 4/7 | 0/7 |
| bug-fix | 6 | 5/6 | 0/6 | 0/6 |
| diagnose | 10 | 10/10 | 0/10 | 0/10 |

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
| identifier missing | 1 | 2% |
| action class wrong | 1 | 2% |
| subject wrong | 1 | 2% |
| type wrong | 9 | 21% |

### identifier missing (1)

- real-28edef5a35 — expected identifiers [longcat-after-v6.json], title 'Added model output quality evaluation fixtures and reports'

### action class wrong (1)

- real-0fd4ffcc31 — expected [modify], got 'create' from title 'Added false-positive skip tracking to GoldenSetScorer'

### subject wrong (1)

- real-28edef5a35 — expected subject tokens [longcat-after-v6.json], title 'Added model output quality evaluation fixtures and reports'

### type wrong (9)

- real-f6a1b89e41 — expected 'change', got 'feature' (title 'Extended round-10 real-history golden set in golden-tool-use-real.json')
- real-f0d346eab1 — expected 'change', got 'feature' (title 'Added real-history golden set in golden-tool-use-real.json')
- real-3a2d426fdf — expected 'change', got 'feature' (title 'Extended token accounting and repeated evaluation in GoldenSetScorer')
- real-f3d12860ff — expected 'change', got 'bugfix' (title 'Fixed log directory access and credential handling in LogsControllerTest')
- real-e2c887775a — expected 'bugfix', got 'change' (title 'Corrected context endpoint mapping in AGENTS.md')
- real-cf667b4d8b — expected 'bugfix', got 'change' (title 'Updated feedback comment formatting in ObservationFeedbackEntity')
- real-c67e7a5ad3 — expected 'bugfix', got 'refactor' (title 'Refactored empty else branch in dto.py')
- real-8ac2a32980 — expected 'bugfix', got 'change' (title 'Updated default port documentation in Dockerfile')
- real-95de35b2ea — expected 'bugfix', got 'change' (title 'Updated test dependency pinning and Docker build context in pom.xml')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| real-f6a1b89e41 | feature | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 3152 |
| real-1fe4ffb124 | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3184 |
| real-d13e2c90cd | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 8698 |
| real-3b4f737562 | feature | observation | call-failed | FAIL | - | - | - | - | - | - | - | 27292 |
| real-77ba40df77 | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3501 |
| real-f0d346eab1 | feature | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/100% | 4837 |
| real-3a2d426fdf | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 75% | 50%/100% | 4039 |
| real-864dadf51b | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 4921 |
| real-b064308244 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 7935 |
| real-30a956e462 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 4787 |
| real-ba86bf2588 | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/100% | 9537 |
| real-a9c8452967 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 8157 |
| real-db8ac0287e | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 67% | 0%/0% | 1956 |
| real-9e6612fdf9 | security-fix | observation | call-failed | FAIL | - | - | - | - | - | - | - | 14402 |
| real-f43cfd6f44 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3099 |
| real-e352ba3da3 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 5129 |
| real-112de33e23 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3022 |
| real-28edef5a35 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 3934 |
| real-6d6df2c321 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 4838 |
| real-d3431639a9 | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 6592 |
| real-7093cb5c56 | security-fix | observation | observation | pass | 0% | hit | miss | match | 100%/100% | 100% | 50%/100% | 3618 |
| real-f3d12860ff | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 67% | 50%/100% | 2802 |
| real-3409dda60c | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 0%/0% | 2786 |
| real-f7b865efe5 | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 67% | 0%/0% | 4757 |
| real-a8cc1754bc | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 80% | 50%/100% | 5356 |
| real-e2c887775a | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 50% | 0%/0% | 4551 |
| real-cf667b4d8b | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 50% | 0%/0% | 2227 |
| real-a0e77ca4ee | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 75% | 0%/0% | 2951 |
| real-d958416fdc | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 80% | 0%/0% | 3174 |
| real-9b424d0d02 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 67% | 0%/0% | 4752 |
| real-85480a720e | docs-update | observation | call-failed | FAIL | - | - | - | - | - | - | - | 9166 |
| real-c67e7a5ad3 | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 1752 |
| real-bdf72f2509 | bug-fix | observation | call-failed | FAIL | - | - | - | - | - | - | - | 9879 |
| real-8ac2a32980 | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 3102 |
| real-2025b33a95 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2582 |
| real-e8c8b314d6 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2617 |
| real-c42d7a9c5d | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1813 |
| real-b5ae725e2a | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 858 |
| real-9261b542ca | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 8482 |
| real-7c557c8d79 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 943 |
| real-1dbccd058e | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2160 |
| real-1f9b5d09e7 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 912 |
| real-ed737be6bb | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1274 |
| real-b2d48b12a1 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 978 |
| real-bc3512ae8d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/100% | 2362 |
| real-e02e436bdd | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3165 |
| real-d44a83721d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/100% | 2666 |
| real-0fd4ffcc31 | test-add | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 50%/100% | 2269 |
| real-eee7785ec8 | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/100% | 4271 |
| real-9a0e90bed5 | refactor | observation | call-failed | FAIL | - | - | - | - | - | - | - | 14357 |
| real-63b5502fb9 | security-fix | observation | call-failed | FAIL | - | - | - | - | - | - | - | 21698 |
| real-d33efbd8ae | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 4908 |
| real-cd6b972cfd | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 67% | 0%/0% | 2789 |
| real-46e467bff6 | security-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 5016 |
| real-325c817bbf | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/100% | 3052 |
| real-7149578a88 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 4854 |
| real-95de35b2ea | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 2755 |
| real-53851b4a23 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 3138 |
| real-f4c6765fb9 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/100% | 2581 |
| real-67083a7bc1 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3423 |

## Problems

- real-f6a1b89e41: title coverage 50% (expected keywords [change, golden-tool-use-real.json])
- real-f6a1b89e41: title verb class 'change' missing (got 'Extended round-10 real-history golden set in golden-tool-use-real.json')
- real-f6a1b89e41: type 'feature' != expected 'change'
- real-3b4f737562: call failed: IllegalStateException: all attempts returned no content
- real-f0d346eab1: type 'feature' != expected 'change'
- real-3a2d426fdf: title coverage 50% (expected keywords [change, goldensetscorer])
- real-3a2d426fdf: title verb class 'change' missing (got 'Extended token accounting and repeated evaluation in GoldenSetScorer')
- real-3a2d426fdf: type 'feature' != expected 'change'
- real-864dadf51b: title coverage 50% (expected keywords [document, goldensetscorer])
- real-864dadf51b: title verb class 'document' missing (got 'Extended GoldenSetScorer with evidence extraction')
- real-30a956e462: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a9c8452967: title coverage 50% (expected keywords [document, searchsimilarityevaluator])
- real-a9c8452967: title verb class 'document' missing (got 'Added semantic search evaluation suite in SearchSimilarityEvaluator')
- real-db8ac0287e: title coverage 50% (expected keywords [document, continuation.txt])
- real-db8ac0287e: title verb class 'document' missing (got 'Updated bugfix guidance in continuation.txt')
- real-db8ac0287e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9e6612fdf9: call failed: IllegalStateException: all attempts returned no content
- real-e352ba3da3: title coverage 50% (expected keywords [document, longcat-after-v7.json])
- real-e352ba3da3: title verb class 'document' missing (got 'Added model evaluation fixtures longcat-after-v7.json')
- real-28edef5a35: title lost identifiers [longcat-after-v6.json] (got 'Added model output quality evaluation fixtures and reports')
- real-28edef5a35: title verb class 'document' missing (got 'Added model output quality evaluation fixtures and reports')
- real-28edef5a35: title subject [longcat-after-v6.json] missing (got 'Added model output quality evaluation fixtures and reports')
- real-28edef5a35: title trace-visible subject [longcat-after-v6.json] missing (got 'Added model output quality evaluation fixtures and reports')
- real-6d6df2c321: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d3431639a9: validation rejected the observation: title too long: 111 chars > 100
- real-7093cb5c56: title coverage 0% (expected keywords [harden, embeddingbackfillconcurrencydatabasetest])
- real-7093cb5c56: title verb class 'harden' missing (got 'Fixed embedding backfill concurrency and sanitization in EmbeddingBackfillConcurrencyDatabaseTest')
- real-f3d12860ff: title coverage 50% (expected keywords [harden, logscontrollertest])
- real-f3d12860ff: title verb class 'harden' missing (got 'Fixed log directory access and credential handling in LogsControllerTest')
- real-f3d12860ff: type 'bugfix' != expected 'change'
- real-3409dda60c: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-f7b865efe5: title coverage 50% (expected keywords [harden, index.html])
- real-f7b865efe5: title verb class 'harden' missing (got 'Updated authentication interface styling in index.html')
- real-f7b865efe5: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a8cc1754bc: title coverage 50% (expected keywords [remove, pendingmessagerepository])
- real-a8cc1754bc: title verb class 'remove' missing (got 'Hardened pending message dedup scoping in PendingMessageRepository')
- real-e2c887775a: title coverage 50% (expected keywords [remove, agents.md])
- real-e2c887775a: title verb class 'remove' missing (got 'Corrected context endpoint mapping in AGENTS.md')
- real-e2c887775a: type 'change' != expected 'bugfix'
- real-e2c887775a: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-cf667b4d8b: title coverage 50% (expected keywords [remove, observationfeedbackentity])
- real-cf667b4d8b: title verb class 'remove' missing (got 'Updated feedback comment formatting in ObservationFeedbackEntity')
- real-cf667b4d8b: type 'change' != expected 'bugfix'
- real-cf667b4d8b: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a0e77ca4ee: title coverage 50% (expected keywords [remove, testing.md])
- real-a0e77ca4ee: title verb class 'remove' missing (got 'Documented synchronized testing table cleanup in TESTING.md')
- real-a0e77ca4ee: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d958416fdc: title coverage 50% (expected keywords [remove, patrol-state.json])
- real-d958416fdc: title verb class 'remove' missing (got 'Updated patrol direction to backend in patrol-state.json')
- real-d958416fdc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9b424d0d02: title coverage 50% (expected keywords [remove, api-zh-cn.md])
- real-9b424d0d02: title verb class 'remove' missing (got 'Documented corrected GET /api/settings defaults in API-zh-CN.md')
- real-9b424d0d02: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-85480a720e: call failed: IllegalStateException: all attempts returned no content
- real-c67e7a5ad3: title coverage 50% (expected keywords [remove, dto.py])
- real-c67e7a5ad3: title verb class 'remove' missing (got 'Refactored empty else branch in dto.py')
- real-c67e7a5ad3: type 'refactor' != expected 'bugfix'
- real-c67e7a5ad3: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-bdf72f2509: call failed: IllegalStateException: all attempts returned no content
- real-8ac2a32980: title coverage 50% (expected keywords [remove, dockerfile])
- real-8ac2a32980: title verb class 'remove' missing (got 'Updated default port documentation in Dockerfile')
- real-8ac2a32980: type 'change' != expected 'bugfix'
- real-8ac2a32980: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-0fd4ffcc31: title coverage 50% (expected keywords [change, goldensetscorer])
- real-0fd4ffcc31: title action [modify] missing: title leads 'create' (got 'Added false-positive skip tracking to GoldenSetScorer')
- real-0fd4ffcc31: title verb class 'change' missing (got 'Added false-positive skip tracking to GoldenSetScorer')
- real-9a0e90bed5: call failed: IllegalStateException: all attempts returned no content
- real-63b5502fb9: call failed: IllegalStateException: all attempts returned no content
- real-d33efbd8ae: title coverage 50% (expected keywords [harden, conversationscopebackfillrunner])
- real-d33efbd8ae: title verb class 'harden' missing (got 'Added conversation scope backfill runner in ConversationScopeBackfillRunner')
- real-d33efbd8ae: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-cd6b972cfd: title coverage 50% (expected keywords [harden, summaryjobservice])
- real-cd6b972cfd: title verb class 'harden' missing (got 'Added summary job service implementation in SummaryJobService')
- real-cd6b972cfd: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-46e467bff6: title coverage 50% (expected keywords [harden, conversation-scope-preflight.sql])
- real-46e467bff6: title verb class 'harden' missing (got 'Added conversation scope preflight in conversation-scope-preflight.sql')
- real-46e467bff6: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-95de35b2ea: title coverage 50% (expected keywords [change, pom.xml])
- real-95de35b2ea: type 'change' != expected 'bugfix'
- real-95de35b2ea: concepts recall 0% (expected [problem-solution], got [what-changed, gotcha])
- real-53851b4a23: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])

## Raw answers behind problems

### real-f6a1b89e41

```text
<observation>   <type>feature</type>   <title>Extended round-10 real-history golden set in golden-tool-use-real.json</title>   <subtitle>The golden dataset now captures broader real-history traces, model titles, and at least 60 mechanically valid samples.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json version changed from real-git-2026-11-03 to real-git-20... [truncated]
```

### real-3b4f737562

```text
(no raw answer: the call failed before returning)
```

### real-f0d346eab1

```text
<observation>   <type>feature</type>   <title>Added real-history golden set in golden-tool-use-real.json</title>   <subtitle>Introduced a mechanically generated real-history evaluation dataset with a reusable generator, test, evaluator selection, and shell-script workflow.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json contains 1097 added lines of real-his... [truncated]
```

### real-3a2d426fdf

```text
<observation>   <type>feature</type>   <title>Extended token accounting and repeated evaluation in GoldenSetScorer</title>   <subtitle>Golden-set evaluation now records prompt tokens and can repeat every sample for run-to-run quality measurement.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java adds public final int promptTokens.</fact>     <fa... [truncated]
```

### real-864dadf51b

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetScorer with evidence extraction</title>   <subtitle>Golden-set scoring now derives richer evidence while preserving legacy title-subject diagnostics.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java added 70 lines and removed 7 lines to implement evidence extraction around sample.t... [truncated]
```

### real-30a956e462

```text
<observation>   <type>change</type>   <title>Documented Round 8 extraction scope contract in extraction-quality-improvement-plan.md</title>   <subtitle>The draft plan gained a Round 8 section covering insertion scope, removed coverage, and a reverted security verb.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md added the section heading "### 7.14 Round 8 — insert... [truncated]
```

### real-a9c8452967

```text
<observation>   <type>feature</type>   <title>Added semantic search evaluation suite in SearchSimilarityEvaluator</title>   <subtitle>Added a replay evaluator, fixtures, corpus, metrics, and archived evaluation documentation.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/SearchSimilarityEvaluator.java contains 411 added lines for semantic search similarity evalu... [truncated]
```

### real-db8ac0287e

```text
<observation>   <type>change</type>   <title>Updated bugfix guidance in continuation.txt</title>   <subtitle>Prompt templates now define Fixed without limiting the category to non-security code.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt changed the Fixed guidance to “- Fixed: a defect already produced wrong or failing behaviour.”</fact>     <fact>backend/sr... [truncated]
```

### real-9e6612fdf9

```text
(no raw answer: the call failed before returning)
```

### real-e352ba3da3

```text
<observation>   <type>feature</type>   <title>Added model evaluation fixtures longcat-after-v7.json</title>   <subtitle>Recorded LongCat and MiniMax after-v7 evaluation outputs, metrics, and archived quality reports.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v7.json contains 656 added lines of evaluation fixture data.</fact>     <fact>backend/src/test/re... [truncated]
```

### real-28edef5a35

```text
<observation>   <type>feature</type>   <title>Added model output quality evaluation fixtures and reports</title>   <subtitle>New Longcat and MiniMax v6 evaluation data, metrics, and archived quality reports were added.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v6.json contains 656 lines of model output evaluation fixture data.</fact>     <fact>backend/sr... [truncated]
```

### real-6d6df2c321

```text
<observation>   <type>change</type>   <title>Documented skip conditions for identifier searches in continuation.txt</title>   <subtitle>Prompt guidance now distinguishes identifier-only searches from substantive work and expands continuation behavior.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt contains the rule “only locates where an identifier is used or de... [truncated]
```

### real-d3431639a9

```text
<observation>   <type>change</type>   <title>Documented model output quality evaluation in 2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md</title>   <subtitle>Archived model and session-summary evaluations now compare post-v4 contract behavior across MiniMax and LongCat.</subtitle>   <facts>     <fact>docs/archive/2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md ... [truncated]
```

### real-7093cb5c56

```text
<observation>   <type>bugfix</type>   <title>Fixed embedding backfill concurrency and sanitization in EmbeddingBackfillConcurrencyDatabaseTest</title>   <subtitle>Embedding backfill now isolates row failures, prevents concurrent retries, and sanitizes sensitive logged content.</subtitle>   <facts>     <fact>EmbeddingBackfillConcurrencyDatabaseTest.java added 333 lines covering database-level embed... [truncated]
```

### real-f3d12860ff

```text
<observation>   <type>bugfix</type>   <title>Fixed log directory access and credential handling in LogsControllerTest</title>   <subtitle>LogsController now validates log paths and applies safer credential-aware handling.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/controller/LogsControllerTest.java replaced reflective logDir field access with setField("logDir", lo... [truncated]
```

### real-3409dda60c

```text
<observation>   <type>change</type>   <title>Documented commit range in conversation-memory-scope-refactor-plan.md</title>   <subtitle>The refactor plan now records nine implementation commits and links to a supporting checklist.</subtitle>   <facts>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md defines the implementation commit range as `325c817..f7b865e`.</fact>     <fact>docs... [truncated]
```

### real-f7b865efe5

```text
<observation>   <type>change</type>   <title>Updated authentication interface styling in index.html</title>   <subtitle>The static web interface and bundled viewer assets were refreshed with a warmer header color and a newer web UI revision.</subtitle>   <facts>     <fact>backend/src/main/resources/static/index.html sets --color-bg-header to #f8f6f2 instead of #f6f8fa.</fact>     <fact>backend/src... [truncated]
```

### real-a8cc1754bc

```text
<observation>   <type>bugfix</type>   <title>Hardened pending message dedup scoping in PendingMessageRepository</title>   <subtitle>Pending-message deduplication now scopes queued work by conversation, tool, and input while removing obsolete repository logic.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/repository/PendingMessageRepository.java removed 22 lines and a... [truncated]
```

### real-e2c887775a

```text
<observation>   <type>change</type>   <title>Corrected context endpoint mapping in AGENTS.md</title>   <subtitle>AGENTS.md now associates the updateFiles field with /api/context/inject and documents the resolved endpoint contract.</subtitle>   <facts>     <fact>AGENTS.md maps `/api/context/inject` to `updateFiles` in `proxy/proxy.js`, replacing the `/api/context/generate` mapping.</fact>     <fact... [truncated]
```

### real-cf667b4d8b

```text
<observation>   <type>change</type>   <title>Updated feedback comment formatting in ObservationFeedbackEntity</title>   <subtitle>Entity documentation formatting changed while repository feedback ordering documentation was clarified to newest first.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/entity/ObservationFeedbackEntity.java changed 10 removed lines and 9 adde... [truncated]
```

### real-a0e77ca4ee

```text
<observation>   <type>change</type>   <title>Documented synchronized testing table cleanup in TESTING.md</title>   <subtitle>English and Chinese testing guides removed drifting line-count columns and duplicate table headers.</subtitle>   <facts>     <fact>docs/TESTING.md removed the "Lines" column from the script table and removed 35 lines while adding 33 lines.</fact>     <fact>docs/TESTING-zh-CN... [truncated]
```

### real-d958416fdc

```text
<observation>   <type>change</type>   <title>Updated patrol direction to backend in patrol-state.json</title>   <subtitle>Patrol records now reflect backend architecture review, while API documentation aligns with the current viewer routes.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json sets "lastDirection" to "backend" instead of "demo".</fact>     <fact>docs/drafts/patrol-rotation.... [truncated]
```

### real-9b424d0d02

```text
<observation>   <type>change</type>   <title>Documented corrected GET /api/settings defaults in API-zh-CN.md</title>   <subtitle>Chinese API documentation now records corrected setting examples matching AppSettings.toMap() defaults.</subtitle>   <facts>     <fact>docs/API-zh-CN.md documents GET /api/settings examples corrected to match AppSettings.toMap() actual defaults for 6 values.</fact>     <... [truncated]
```

### real-85480a720e

```text
(no raw answer: the call failed before returning)
```

### real-c67e7a5ad3

```text
<observation>   <type>refactor</type>   <title>Refactored empty else branch in dto.py</title>   <subtitle>Removed an unnecessary `else:` branch from the Python SDK DTO implementation.</subtitle>   <facts>     <fact>python-sdk/cortex-mem-python/cortex_mem/dto.py removed the `else:` branch and deleted 2 lines.</fact>     <fact>.gitignore added the `memory/.dreams` path.</fact>   </facts>   <narrativ... [truncated]
```

### real-bdf72f2509

```text
(no raw answer: the call failed before returning)
```

### real-8ac2a32980

```text
<observation>   <type>change</type>   <title>Updated default port documentation in Dockerfile</title>   <subtitle>Dockerfile comments now identify port 37777 as the default, while deployment build-argument examples were removed.</subtitle>   <facts>     <fact>backend/Dockerfile now documents the default port as 37777, overridable with the SERVER_PORT environment variable.</fact>     <fact>docs/DEP... [truncated]
```

### real-0fd4ffcc31

```text
<observation>   <type>feature</type>   <title>Added false-positive skip tracking to GoldenSetScorer</title>   <subtitle>Golden-set evaluation now distinguishes record-worthy false positives from false negatives when enforcing skip gates.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java adds skip tracking that distinguishes skipFp from skipFn.</... [truncated]
```

### real-9a0e90bed5

```text
(no raw answer: the call failed before returning)
```

### real-63b5502fb9

```text
(no raw answer: the call failed before returning)
```

### real-d33efbd8ae

```text
<observation>   <type>feature</type>   <title>Added conversation scope backfill runner in ConversationScopeBackfillRunner</title>   <subtitle>New migration tooling classifies technical conversations and backfills conversation scope through a dedicated CLI.</subtitle>   <facts>     <fact>ConversationScopeBackfillRunner adds 845 lines in backend/src/main/java/com/ablueforce/cortexce/migration/Conver... [truncated]
```

### real-cd6b972cfd

```text
<observation>   <type>feature</type>   <title>Added summary job service implementation in SummaryJobService</title>   <subtitle>SummaryJobService introduces a new Java service for handling summary-related job operations.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/service/SummaryJobService.java now contains the package declaration com.ablueforce.cortexce.service an... [truncated]
```

### real-46e467bff6

```text
<observation>   <type>feature</type>   <title>Added conversation scope preflight in conversation-scope-preflight.sql</title>   <subtitle>Database tooling now validates conversation and memory scope expansion with dedicated preflight and fixture scripts.</subtitle>   <facts>     <fact>scripts/db/conversation-scope-preflight.sql contains 426 added lines for conversation-scope preflight.</fact>     <... [truncated]
```

### real-95de35b2ea

```text
<observation>   <type>change</type>   <title>Updated test dependency pinning and Docker build context in pom.xml</title>   <subtitle>Revised Maven test dependency configuration and clarified the repository-root Docker build context.</subtitle>   <facts>     <fact>backend/pom.xml pins byte-buddy to 1.15.x for Mockito 5.11 from the spring-boot-starter-test BOM.</fact>     <fact>scripts/docker-e2e-te... [truncated]
```

### real-53851b4a23

```text
<observation>   <type>change</type>   <title>Documented health check archive 11 in 2026-10-08_health-check-history-11.md</title>   <subtitle>Archived 1,206 lines of inspection history and indexed the third compressed migration.</subtitle>   <facts>     <fact>docs/archive/2026-10-08_health-check-history-11.md contains 1,206 inserted lines covering rounds 312 through 359+ from 2026-10-06 to 2026-10-... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
