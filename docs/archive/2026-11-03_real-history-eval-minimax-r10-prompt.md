golden set: real (60 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: r10-prompt
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 60 unique; runs scored: 1; key repeat: 3; real calls: 60 (cap none); duration: 325785 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 90% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 10/0/0/50 |
| format errors | 0 |
| validation rejections | 1 |
| call failures | 5 |
| title exact-hit rate (legacy) | 27% |
| title normalized hit rate (legacy) | 27% |
| title action-class hit rate (A6) | 95% (44 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 41% |
| title subject hit rate (legacy, diagnostic) | 55% |
| title trace-visible subject hit rate (A6b) | 55% (44 scored, 0 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 52% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 52% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 30% |
| title identifier hit rate | 55% |
| type accuracy | 61% |
| facts keyword precision (legacy) / recall | 100% / 100% |
| facts informative precision | 89% |
| facts trace-support rate | 100% |
| concepts precision / recall | 23% / 45% |
| skip consistency across repeats | 0% (0 repeated samples) |
| latency p50 / p95 / max (ms) | 4169 / 12861 / 24863 |
| tokens p50 / p95 / max / mean per call | 4149 / 4802 / 4993 / 3205 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 89% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 95% | PASS |
| A6b | title trace-visible subject | >= 90% | 55% | **FAIL** |
| A6c | title identifiers | = 100% | 55% | **FAIL** |
| A6 | all three title dimensions together | all pass | 52% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 55 |
| exception | 5 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 60 | 100% | 27% | 52% | 100% | 89% | 100% | 0 |

- average: skip 100%, title 27%, title contract 52%, facts P/R 100%/100%, facts actionable 89%
- worst run: skip 100%, title 27%, title contract 52%, facts P/R 100%/100%, facts actionable 89%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| feature | 9 | 9/9 | 7/9 | 0/9 |
| config-change | 1 | 1/1 | 1/1 | 1/1 |
| refactor | 3 | 2/3 | 0/3 | 1/3 |
| docs-update | 20 | 17/20 | 17/20 | 8/20 |
| test-add | 2 | 1/2 | 0/2 | 0/2 |
| security-fix | 10 | 9/10 | 2/10 | 0/10 |
| bug-fix | 5 | 5/5 | 0/5 | 2/5 |
| diagnose | 10 | 10/10 | 0/10 | 0/10 |

## Failure clusters (first run)

| cluster | misses | share of scored (44) |
|---|---|---|
| identifier missing | 20 | 45% |
| action class wrong | 2 | 5% |
| subject wrong | 20 | 45% |
| type wrong | 17 | 39% |

### identifier missing (20)

- real-d13e2c90cd — expected identifiers [2026-11-03_real-history-golden-eval-minimax.md], title 'Added real-history golden evaluation replay for 36 samples'
- real-3b4f737562 — expected identifiers [minimax-after-v9.json], title 'Added MiniMax v9 evaluation fixture and metrics'
- real-f0d346eab1 — expected identifiers [golden-tool-use-real.json], title 'Added real-history golden evaluation set and generator'
- real-db8ac0287e — expected identifiers [continuation.txt], title 'Updated defect classification guidance in prompt templates'
- real-9e6612fdf9 — expected identifiers [continuation.txt], title 'Clarified bugfix classification and added extend golden-set cases'
- real-e352ba3da3 — expected identifiers [longcat-after-v7.json], title 'Added v7 model output evaluation fixtures and reports'
- real-112de33e23 — expected identifiers [continuation.txt], title 'Documented replacement naming rule in prompt resources'
- real-28edef5a35 — expected identifiers [longcat-after-v6.json], title 'Added v6 model output evaluation fixtures and reports'
- real-6d6df2c321 — expected identifiers [continuation.txt], title 'Updated prompt skip and observation guidance in backend/src/main/resources/prompts'
- real-d3431639a9 — expected identifiers [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md], title 'Documented model and summary quality evaluations in five evaluation files'
- real-f3d12860ff — expected identifiers [LogsControllerTest], title 'Fixed log endpoint path traversal and credential exposure in LogsController'
- real-a8cc1754bc — expected identifiers [PendingMessageRepository], title 'Hardened message deduplication and conversation summary queries'
- real-cf667b4d8b — expected identifiers [ObservationFeedbackEntity], title 'Updated ObservationFeedbackRepository ordering comment and entity documentation'
- real-d958416fdc — expected identifiers [patrol-state.json], title 'Updated patrol state and architecture endpoint documentation'
- real-9b424d0d02 — expected identifiers [API-zh-CN.md], title 'Documented GET /api/settings default corrections in docs/API.md'
- real-bc3512ae8d — expected identifiers [extraction-quality-improvement-plan.md], title 'Documented Round 6 category few-shots and measured title ceiling in extraction plan'
- real-eee7785ec8 — expected identifiers [2026-11-02_model-output-quality-eval-minimax.md], title 'Added provider-selectable model output quality evaluation harness'
- real-63b5502fb9 — expected identifiers [V20__conversation_memory_scope_expand.sql], title 'Added conversation memory scope identity migration and rehearsal fixtures'
- real-46e467bff6 — expected identifiers [conversation-scope-preflight.sql], title 'Added conversation scope preflight and fixture SQL scripts'
- real-95de35b2ea — expected identifiers [pom.xml], title 'Updated byte-buddy pin and Docker build context documentation'

### action class wrong (2)

- real-9e6612fdf9 — expected [document, secure, modify], got 'null' from title 'Clarified bugfix classification and added extend golden-set cases'
- real-85480a720e — expected [remove, document], got 'modify' from title 'Updated database environment variables in docs/TESTING.md and docs/TESTING-zh-CN.md'

### subject wrong (20)

- real-d13e2c90cd — expected subject tokens [2026-11-03_real-history-golden-eval-minimax.md], title 'Added real-history golden evaluation replay for 36 samples'
- real-3b4f737562 — expected subject tokens [minimax-after-v9.json], title 'Added MiniMax v9 evaluation fixture and metrics'
- real-f0d346eab1 — expected subject tokens [golden-tool-use-real.json], title 'Added real-history golden evaluation set and generator'
- real-db8ac0287e — expected subject tokens [continuation.txt], title 'Updated defect classification guidance in prompt templates'
- real-9e6612fdf9 — expected subject tokens [continuation.txt], title 'Clarified bugfix classification and added extend golden-set cases'
- real-e352ba3da3 — expected subject tokens [longcat-after-v7.json], title 'Added v7 model output evaluation fixtures and reports'
- real-112de33e23 — expected subject tokens [continuation.txt], title 'Documented replacement naming rule in prompt resources'
- real-28edef5a35 — expected subject tokens [longcat-after-v6.json], title 'Added v6 model output evaluation fixtures and reports'
- real-6d6df2c321 — expected subject tokens [continuation.txt], title 'Updated prompt skip and observation guidance in backend/src/main/resources/prompts'
- real-d3431639a9 — expected subject tokens [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md], title 'Documented model and summary quality evaluations in five evaluation files'
- real-f3d12860ff — expected subject tokens [LogsControllerTest], title 'Fixed log endpoint path traversal and credential exposure in LogsController'
- real-a8cc1754bc — expected subject tokens [PendingMessageRepository], title 'Hardened message deduplication and conversation summary queries'
- real-cf667b4d8b — expected subject tokens [ObservationFeedbackEntity], title 'Updated ObservationFeedbackRepository ordering comment and entity documentation'
- real-d958416fdc — expected subject tokens [patrol-state.json], title 'Updated patrol state and architecture endpoint documentation'
- real-9b424d0d02 — expected subject tokens [API-zh-CN.md], title 'Documented GET /api/settings default corrections in docs/API.md'
- real-bc3512ae8d — expected subject tokens [extraction-quality-improvement-plan.md], title 'Documented Round 6 category few-shots and measured title ceiling in extraction plan'
- real-eee7785ec8 — expected subject tokens [2026-11-02_model-output-quality-eval-minimax.md], title 'Added provider-selectable model output quality evaluation harness'
- real-63b5502fb9 — expected subject tokens [V20__conversation_memory_scope_expand.sql], title 'Added conversation memory scope identity migration and rehearsal fixtures'
- real-46e467bff6 — expected subject tokens [conversation-scope-preflight.sql], title 'Added conversation scope preflight and fixture SQL scripts'
- real-95de35b2ea — expected subject tokens [pom.xml], title 'Updated byte-buddy pin and Docker build context documentation'

### type wrong (17)

- real-77ba40df77 — expected 'refactor', got 'feature' (title 'Added extraction consistency voting in ExtractionConsistency.java')
- real-f0d346eab1 — expected 'change', got 'feature' (title 'Added real-history golden evaluation set and generator')
- real-864dadf51b — expected 'feature', got 'change' (title 'Extended golden-set scoring diagnostics in GoldenSetScorer.java')
- real-9e6612fdf9 — expected 'bugfix', got 'change' (title 'Clarified bugfix classification and added extend golden-set cases')
- real-7093cb5c56 — expected 'bugfix', got 'feature' (title 'Added EmbeddingBackfillConcurrencyDatabaseTest and EmbeddingBackfillServiceTest')
- real-f7b865efe5 — expected 'bugfix', got 'change' (title 'Updated header background color and rebuilt viewer assets in index.html')
- real-e2c887775a — expected 'bugfix', got 'change' (title 'Updated context endpoint updateFiles contract in AGENTS.md')
- real-cf667b4d8b — expected 'refactor', got 'change' (title 'Updated ObservationFeedbackRepository ordering comment and entity documentation')
- real-c67e7a5ad3 — expected 'bugfix', got 'change' (title 'Removed empty else branch in python-sdk/cortex-mem-python/cortex_mem/dto.py')
- real-bdf72f2509 — expected 'bugfix', got 'change' (title 'Removed SDK version User-Agent comment in CortexMemClientImpl')
- real-8ac2a32980 — expected 'bugfix', got 'change' (title 'Updated default port comment to 37777 in backend/Dockerfile')
- real-0fd4ffcc31 — expected 'feature', got 'bugfix' (title 'Fixed must-record sample skip accounting in GoldenSetScorer')
- real-9a0e90bed5 — expected 'bugfix', got 'feature' (title 'Extended ObservationRepository queries with memoryScopeId')
- real-63b5502fb9 — expected 'bugfix', got 'feature' (title 'Added conversation memory scope identity migration and rehearsal fixtures')
- real-cd6b972cfd — expected 'bugfix', got 'feature' (title 'Added SummaryJobService service implementation')
- real-46e467bff6 — expected 'bugfix', got 'feature' (title 'Added conversation scope preflight and fixture SQL scripts')
- real-95de35b2ea — expected 'bugfix', got 'change' (title 'Updated byte-buddy pin and Docker build context documentation')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| real-f6a1b89e41 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 4972 |
| real-1fe4ffb124 | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3217 |
| real-d13e2c90cd | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 4828 |
| real-3b4f737562 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 9875 |
| real-77ba40df77 | refactor | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 8948 |
| real-f0d346eab1 | feature | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 50%/100% | 6294 |
| real-3a2d426fdf | refactor | observation | call-failed | FAIL | - | - | - | - | - | - | - | 11640 |
| real-864dadf51b | feature | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 3276 |
| real-b064308244 | docs-update | observation | call-failed | FAIL | - | - | - | - | - | - | - | 6886 |
| real-30a956e462 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 4747 |
| real-ba86bf2588 | test-add | observation | call-failed | FAIL | - | - | - | - | - | - | - | 13343 |
| real-a9c8452967 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 4642 |
| real-db8ac0287e | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 2182 |
| real-9e6612fdf9 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | MISS | 100%/100% | 25% | 0%/0% | 9631 |
| real-f43cfd6f44 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3138 |
| real-e352ba3da3 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 24863 |
| real-112de33e23 | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 2920 |
| real-28edef5a35 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 7906 |
| real-6d6df2c321 | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 4132 |
| real-d3431639a9 | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 6125 |
| real-7093cb5c56 | security-fix | observation | observation | pass | 0% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 3330 |
| real-f3d12860ff | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 7546 |
| real-3409dda60c | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 4684 |
| real-f7b865efe5 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 12861 |
| real-a8cc1754bc | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 40% | 50%/100% | 3574 |
| real-e2c887775a | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 50% | 50%/100% | 4237 |
| real-cf667b4d8b | refactor | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 50% | 0%/0% | 4169 |
| real-a0e77ca4ee | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 33% | 0%/0% | 3922 |
| real-d958416fdc | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 80% | 0%/0% | 4743 |
| real-9b424d0d02 | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 2683 |
| real-85480a720e | docs-update | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 0%/0% | 7650 |
| real-c67e7a5ad3 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 2603 |
| real-bdf72f2509 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 3312 |
| real-8ac2a32980 | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 3896 |
| real-2025b33a95 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 4190 |
| real-e8c8b314d6 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1053 |
| real-c42d7a9c5d | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1918 |
| real-b5ae725e2a | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 5311 |
| real-9261b542ca | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2930 |
| real-7c557c8d79 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3306 |
| real-1dbccd058e | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 946 |
| real-1f9b5d09e7 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 818 |
| real-ed737be6bb | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2115 |
| real-b2d48b12a1 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 967 |
| real-bc3512ae8d | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 4655 |
| real-e02e436bdd | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 50% | 50%/100% | 3337 |
| real-d44a83721d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3544 |
| real-0fd4ffcc31 | test-add | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 3839 |
| real-eee7785ec8 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 75% | 50%/100% | 5650 |
| real-9a0e90bed5 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 67% | 0%/0% | 6380 |
| real-63b5502fb9 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 0%/0% | 4132 |
| real-d33efbd8ae | security-fix | observation | call-failed | FAIL | - | - | - | - | - | - | - | 22701 |
| real-cd6b972cfd | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 67% | 0%/0% | 3802 |
| real-46e467bff6 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 0%/0% | 5023 |
| real-325c817bbf | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 3986 |
| real-7149578a88 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3361 |
| real-95de35b2ea | bug-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 0%/0% | 2165 |
| real-53851b4a23 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 4352 |
| real-f4c6765fb9 | docs-update | observation | call-failed | FAIL | - | - | - | - | - | - | - | 6701 |
| real-67083a7bc1 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 5462 |

## Problems

- real-f6a1b89e41: title coverage 50% (expected keywords [change, golden-tool-use-real.json])
- real-d13e2c90cd: title lost identifiers [2026-11-03_real-history-golden-eval-minimax.md] (got 'Added real-history golden evaluation replay for 36 samples')
- real-d13e2c90cd: title verb class 'document' missing (got 'Added real-history golden evaluation replay for 36 samples')
- real-d13e2c90cd: title subject [2026-11-03_real-history-golden-eval-minimax.md] missing (got 'Added real-history golden evaluation replay for 36 samples')
- real-d13e2c90cd: title trace-visible subject [2026-11-03_real-history-golden-eval-minimax.md] missing (got 'Added real-history golden evaluation replay for 36 samples')
- real-3b4f737562: title lost identifiers [minimax-after-v9.json] (got 'Added MiniMax v9 evaluation fixture and metrics')
- real-3b4f737562: title verb class 'document' missing (got 'Added MiniMax v9 evaluation fixture and metrics')
- real-3b4f737562: title subject [minimax-after-v9.json] missing (got 'Added MiniMax v9 evaluation fixture and metrics')
- real-3b4f737562: title trace-visible subject [minimax-after-v9.json] missing (got 'Added MiniMax v9 evaluation fixture and metrics')
- real-77ba40df77: type 'feature' != expected 'refactor'
- real-77ba40df77: concepts recall 0% (expected [what-changed], got [pattern, trade-off])
- real-f0d346eab1: title lost identifiers [golden-tool-use-real.json] (got 'Added real-history golden evaluation set and generator')
- real-f0d346eab1: title subject [golden-tool-use-real.json] missing (got 'Added real-history golden evaluation set and generator')
- real-f0d346eab1: title trace-visible subject [golden-tool-use-real.json] missing (got 'Added real-history golden evaluation set and generator')
- real-f0d346eab1: type 'feature' != expected 'change'
- real-3a2d426fdf: call failed: IllegalStateException: all attempts returned no content
- real-864dadf51b: title coverage 50% (expected keywords [document, goldensetscorer])
- real-864dadf51b: title verb class 'document' missing (got 'Extended golden-set scoring diagnostics in GoldenSetScorer.java')
- real-864dadf51b: type 'change' != expected 'feature'
- real-b064308244: call failed: IllegalStateException: all attempts returned no content
- real-30a956e462: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-ba86bf2588: call failed: IllegalStateException: all attempts returned no content
- real-a9c8452967: title coverage 50% (expected keywords [document, searchsimilarityevaluator])
- real-a9c8452967: title verb class 'document' missing (got 'Added semantic relevance floor evaluation in SearchSimilarityEvaluator')
- real-db8ac0287e: title lost identifiers [continuation.txt] (got 'Updated defect classification guidance in prompt templates')
- real-db8ac0287e: title verb class 'document' missing (got 'Updated defect classification guidance in prompt templates')
- real-db8ac0287e: title subject [continuation.txt] missing (got 'Updated defect classification guidance in prompt templates')
- real-db8ac0287e: title trace-visible subject [continuation.txt] missing (got 'Updated defect classification guidance in prompt templates')
- real-db8ac0287e: concepts recall 0% (expected [problem-solution], got [what-changed, gotcha])
- real-9e6612fdf9: title lost identifiers [continuation.txt] (got 'Clarified bugfix classification and added extend golden-set cases')
- real-9e6612fdf9: title action [document, secure, modify] missing: title leads 'null' (got 'Clarified bugfix classification and added extend golden-set cases')
- real-9e6612fdf9: title verb class 'document' missing (got 'Clarified bugfix classification and added extend golden-set cases')
- real-9e6612fdf9: title subject [continuation.txt] missing (got 'Clarified bugfix classification and added extend golden-set cases')
- real-9e6612fdf9: title trace-visible subject [continuation.txt] missing (got 'Clarified bugfix classification and added extend golden-set cases')
- real-9e6612fdf9: type 'change' != expected 'bugfix'
- real-9e6612fdf9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e352ba3da3: title lost identifiers [longcat-after-v7.json] (got 'Added v7 model output evaluation fixtures and reports')
- real-e352ba3da3: title verb class 'document' missing (got 'Added v7 model output evaluation fixtures and reports')
- real-e352ba3da3: title subject [longcat-after-v7.json] missing (got 'Added v7 model output evaluation fixtures and reports')
- real-e352ba3da3: title trace-visible subject [longcat-after-v7.json] missing (got 'Added v7 model output evaluation fixtures and reports')
- real-112de33e23: title lost identifiers [continuation.txt] (got 'Documented replacement naming rule in prompt resources')
- real-112de33e23: title subject [continuation.txt] missing (got 'Documented replacement naming rule in prompt resources')
- real-112de33e23: title trace-visible subject [continuation.txt] missing (got 'Documented replacement naming rule in prompt resources')
- real-28edef5a35: title lost identifiers [longcat-after-v6.json] (got 'Added v6 model output evaluation fixtures and reports')
- real-28edef5a35: title verb class 'document' missing (got 'Added v6 model output evaluation fixtures and reports')
- real-28edef5a35: title subject [longcat-after-v6.json] missing (got 'Added v6 model output evaluation fixtures and reports')
- real-28edef5a35: title trace-visible subject [longcat-after-v6.json] missing (got 'Added v6 model output evaluation fixtures and reports')
- real-6d6df2c321: title lost identifiers [continuation.txt] (got 'Updated prompt skip and observation guidance in backend/src/main/resources/prompts')
- real-6d6df2c321: title verb class 'document' missing (got 'Updated prompt skip and observation guidance in backend/src/main/resources/prompts')
- real-6d6df2c321: title subject [continuation.txt] missing (got 'Updated prompt skip and observation guidance in backend/src/main/resources/prompts')
- real-6d6df2c321: title trace-visible subject [continuation.txt] missing (got 'Updated prompt skip and observation guidance in backend/src/main/resources/prompts')
- real-6d6df2c321: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d3431639a9: title lost identifiers [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] (got 'Documented model and summary quality evaluations in five evaluation files')
- real-d3431639a9: title subject [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] missing (got 'Documented model and summary quality evaluations in five evaluation files')
- real-d3431639a9: title trace-visible subject [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] missing (got 'Documented model and summary quality evaluations in five evaluation files')
- real-d3431639a9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-7093cb5c56: title coverage 0% (expected keywords [harden, embeddingbackfillconcurrencydatabasetest])
- real-7093cb5c56: title verb class 'harden' missing (got 'Added EmbeddingBackfillConcurrencyDatabaseTest and EmbeddingBackfillServiceTest')
- real-7093cb5c56: type 'feature' != expected 'bugfix'
- real-7093cb5c56: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-f3d12860ff: title lost identifiers [LogsControllerTest] (got 'Fixed log endpoint path traversal and credential exposure in LogsController')
- real-f3d12860ff: title verb class 'harden' missing (got 'Fixed log endpoint path traversal and credential exposure in LogsController')
- real-f3d12860ff: title subject [LogsControllerTest] missing (got 'Fixed log endpoint path traversal and credential exposure in LogsController')
- real-f3d12860ff: title trace-visible subject [LogsControllerTest] missing (got 'Fixed log endpoint path traversal and credential exposure in LogsController')
- real-3409dda60c: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-f7b865efe5: title coverage 50% (expected keywords [harden, index.html])
- real-f7b865efe5: title verb class 'harden' missing (got 'Updated header background color and rebuilt viewer assets in index.html')
- real-f7b865efe5: type 'change' != expected 'bugfix'
- real-f7b865efe5: concepts recall 0% (expected [problem-solution], got [what-changed, trade-off])
- real-a8cc1754bc: title lost identifiers [PendingMessageRepository] (got 'Hardened message deduplication and conversation summary queries')
- real-a8cc1754bc: title verb class 'remove' missing (got 'Hardened message deduplication and conversation summary queries')
- real-a8cc1754bc: title subject [PendingMessageRepository] missing (got 'Hardened message deduplication and conversation summary queries')
- real-a8cc1754bc: title trace-visible subject [PendingMessageRepository] missing (got 'Hardened message deduplication and conversation summary queries')
- real-e2c887775a: title coverage 50% (expected keywords [remove, agents.md])
- real-e2c887775a: title verb class 'remove' missing (got 'Updated context endpoint updateFiles contract in AGENTS.md')
- real-e2c887775a: type 'change' != expected 'bugfix'
- real-cf667b4d8b: title lost identifiers [ObservationFeedbackEntity] (got 'Updated ObservationFeedbackRepository ordering comment and entity documentation')
- real-cf667b4d8b: title verb class 'remove' missing (got 'Updated ObservationFeedbackRepository ordering comment and entity documentation')
- real-cf667b4d8b: title subject [ObservationFeedbackEntity] missing (got 'Updated ObservationFeedbackRepository ordering comment and entity documentation')
- real-cf667b4d8b: title trace-visible subject [ObservationFeedbackEntity] missing (got 'Updated ObservationFeedbackRepository ordering comment and entity documentation')
- real-cf667b4d8b: type 'change' != expected 'refactor'
- real-cf667b4d8b: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a0e77ca4ee: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d958416fdc: title lost identifiers [patrol-state.json] (got 'Updated patrol state and architecture endpoint documentation')
- real-d958416fdc: title verb class 'remove' missing (got 'Updated patrol state and architecture endpoint documentation')
- real-d958416fdc: title subject [patrol-state.json] missing (got 'Updated patrol state and architecture endpoint documentation')
- real-d958416fdc: title trace-visible subject [patrol-state.json] missing (got 'Updated patrol state and architecture endpoint documentation')
- real-d958416fdc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9b424d0d02: title lost identifiers [API-zh-CN.md] (got 'Documented GET /api/settings default corrections in docs/API.md')
- real-9b424d0d02: title verb class 'remove' missing (got 'Documented GET /api/settings default corrections in docs/API.md')
- real-9b424d0d02: title subject [API-zh-CN.md] missing (got 'Documented GET /api/settings default corrections in docs/API.md')
- real-9b424d0d02: title trace-visible subject [API-zh-CN.md] missing (got 'Documented GET /api/settings default corrections in docs/API.md')
- real-9b424d0d02: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-85480a720e: title coverage 50% (expected keywords [remove, testing-zh-cn.md])
- real-85480a720e: title action [remove, document] missing: title leads 'modify' (got 'Updated database environment variables in docs/TESTING.md and docs/TESTING-zh-CN.md')
- real-85480a720e: title verb class 'remove' missing (got 'Updated database environment variables in docs/TESTING.md and docs/TESTING-zh-CN.md')
- real-85480a720e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-c67e7a5ad3: type 'change' != expected 'bugfix'
- real-c67e7a5ad3: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-bdf72f2509: type 'change' != expected 'bugfix'
- real-bdf72f2509: concepts recall 0% (expected [problem-solution], got [what-changed, what-changed])
- real-8ac2a32980: title coverage 50% (expected keywords [remove, dockerfile])
- real-8ac2a32980: title verb class 'remove' missing (got 'Updated default port comment to 37777 in backend/Dockerfile')
- real-8ac2a32980: type 'change' != expected 'bugfix'
- real-8ac2a32980: concepts recall 0% (expected [problem-solution], got [what-changed, trade-off])
- real-bc3512ae8d: title lost identifiers [extraction-quality-improvement-plan.md] (got 'Documented Round 6 category few-shots and measured title ceiling in extraction plan')
- real-bc3512ae8d: title subject [extraction-quality-improvement-plan.md] missing (got 'Documented Round 6 category few-shots and measured title ceiling in extraction plan')
- real-bc3512ae8d: title trace-visible subject [extraction-quality-improvement-plan.md] missing (got 'Documented Round 6 category few-shots and measured title ceiling in extraction plan')
- real-e02e436bdd: title coverage 50% (expected keywords [document, continuation.txt])
- real-e02e436bdd: title verb class 'document' missing (got 'Updated prompt category examples in backend/src/main/resources/prompts/continuation.txt')
- real-0fd4ffcc31: title coverage 50% (expected keywords [change, goldensetscorer])
- real-0fd4ffcc31: title verb class 'change' missing (got 'Fixed must-record sample skip accounting in GoldenSetScorer')
- real-0fd4ffcc31: type 'bugfix' != expected 'feature'
- real-0fd4ffcc31: concepts recall 0% (expected [what-changed], got [problem-solution, gotcha])
- real-eee7785ec8: title lost identifiers [2026-11-02_model-output-quality-eval-minimax.md] (got 'Added provider-selectable model output quality evaluation harness')
- real-eee7785ec8: title verb class 'document' missing (got 'Added provider-selectable model output quality evaluation harness')
- real-eee7785ec8: title subject [2026-11-02_model-output-quality-eval-minimax.md] missing (got 'Added provider-selectable model output quality evaluation harness')
- real-eee7785ec8: title trace-visible subject [2026-11-02_model-output-quality-eval-minimax.md] missing (got 'Added provider-selectable model output quality evaluation harness')
- real-9a0e90bed5: title coverage 50% (expected keywords [harden, observationrepository])
- real-9a0e90bed5: title verb class 'harden' missing (got 'Extended ObservationRepository queries with memoryScopeId')
- real-9a0e90bed5: type 'feature' != expected 'bugfix'
- real-9a0e90bed5: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-63b5502fb9: title lost identifiers [V20__conversation_memory_scope_expand.sql] (got 'Added conversation memory scope identity migration and rehearsal fixtures')
- real-63b5502fb9: title verb class 'harden' missing (got 'Added conversation memory scope identity migration and rehearsal fixtures')
- real-63b5502fb9: title subject [V20__conversation_memory_scope_expand.sql] missing (got 'Added conversation memory scope identity migration and rehearsal fixtures')
- real-63b5502fb9: title trace-visible subject [V20__conversation_memory_scope_expand.sql] missing (got 'Added conversation memory scope identity migration and rehearsal fixtures')
- real-63b5502fb9: type 'feature' != expected 'bugfix'
- real-63b5502fb9: concepts recall 0% (expected [problem-solution], got [pattern, gotcha])
- real-d33efbd8ae: call failed: IllegalStateException: all attempts returned no content
- real-cd6b972cfd: title coverage 50% (expected keywords [harden, summaryjobservice])
- real-cd6b972cfd: title verb class 'harden' missing (got 'Added SummaryJobService service implementation')
- real-cd6b972cfd: type 'feature' != expected 'bugfix'
- real-cd6b972cfd: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-46e467bff6: title lost identifiers [conversation-scope-preflight.sql] (got 'Added conversation scope preflight and fixture SQL scripts')
- real-46e467bff6: title verb class 'harden' missing (got 'Added conversation scope preflight and fixture SQL scripts')
- real-46e467bff6: title subject [conversation-scope-preflight.sql] missing (got 'Added conversation scope preflight and fixture SQL scripts')
- real-46e467bff6: title trace-visible subject [conversation-scope-preflight.sql] missing (got 'Added conversation scope preflight and fixture SQL scripts')
- real-46e467bff6: type 'feature' != expected 'bugfix'
- real-46e467bff6: concepts recall 0% (expected [problem-solution], got [pattern, what-changed])
- real-325c817bbf: validation rejected the observation: title too long: 130 chars > 100
- real-95de35b2ea: title lost identifiers [pom.xml] (got 'Updated byte-buddy pin and Docker build context documentation')
- real-95de35b2ea: title subject [pom.xml] missing (got 'Updated byte-buddy pin and Docker build context documentation')
- real-95de35b2ea: title trace-visible subject [pom.xml] missing (got 'Updated byte-buddy pin and Docker build context documentation')
- real-95de35b2ea: type 'change' != expected 'bugfix'
- real-95de35b2ea: concepts recall 0% (expected [problem-solution], got [what-changed, gotcha])
- real-53851b4a23: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-f4c6765fb9: call failed: IllegalStateException: all attempts returned no content

## Raw answers behind problems

### real-f6a1b89e41

```text
<observation>   <type>change</type>   <title>Updated real-git golden dataset to round 10 in golden-tool-use-real.json</title>   <subtitle>Golden-set evaluation data, scoring metadata, and minimum-sample expectations were expanded for round-10 failure analysis.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json now uses version "real-git-2026-11-03-r10" and con... [truncated]
```

### real-d13e2c90cd

```text
<observation>   <type>feature</type>   <title>Added real-history golden evaluation replay for 36 samples</title>   <subtitle>GoldenSetRealReplayTest now replays a real 36-sample minimax fixture against expected outputs and metrics.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetRealReplayTest.java adds an 87-line Java replay test.</fact>     <fact>backen... [truncated]
```

### real-3b4f737562

```text
<observation>   <type>feature</type>   <title>Added MiniMax v9 evaluation fixture and metrics</title>   <subtitle>New model-output quality fixture, archived report, and evaluator support establish reusable evaluation records.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/minimax-after-v9.json contains 2096 lines of model evaluation cases.</fact>     <fact>backend/src/test... [truncated]
```

### real-77ba40df77

```text
<observation>   <type>feature</type>   <title>Added extraction consistency voting in ExtractionConsistency.java</title>   <subtitle>ExtractionConsistency centralizes multi-call extraction comparison and AgentService now applies consistency handling.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/service/ExtractionConsistency.java defines a new 82-line service in packa... [truncated]
```

### real-f0d346eab1

```text
<observation>   <type>feature</type>   <title>Added real-history golden evaluation set and generator</title>   <subtitle>Added mechanically generated real-tool-use fixtures, evaluator integration, and a dedicated evaluation test.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json contains 1097 lines of real-history golden evaluation data.</fact>     <fact>scri... [truncated]
```

### real-3a2d426fdf

```text
(no raw answer: the call failed before returning)
```

### real-864dadf51b

```text
<observation>   <type>change</type>   <title>Extended golden-set scoring diagnostics in GoldenSetScorer.java</title>   <subtitle>Scorer evidence now captures richer tool input while evaluator output labels legacy hit-rate metrics.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java now builds evidence with Javadoc and expanded scoring logic.</fact... [truncated]
```

### real-b064308244

```text
(no raw answer: the call failed before returning)
```

### real-30a956e462

```text
<observation>   <type>change</type>   <title>Documented Round 8 insertion-scope contract in docs/drafts/extraction-quality-improvement-plan.md</title>   <subtitle>Round 8 documentation covers scope handling, removal coverage, and a reverted security-related verb.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md gained 139 lines under the heading "### 7.14 Round 8 —... [truncated]
```

### real-ba86bf2588

```text
(no raw answer: the call failed before returning)
```

### real-a9c8452967

```text
<observation>   <type>feature</type>   <title>Added semantic relevance floor evaluation in SearchSimilarityEvaluator</title>   <subtitle>New replay evaluation scores BGE-M3 search similarity against a labeled corpus and pinned metrics.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/SearchSimilarityEvaluator.java implements the semantic relevance evaluation with 4... [truncated]
```

### real-db8ac0287e

```text
<observation>   <type>change</type>   <title>Updated defect classification guidance in prompt templates</title>   <subtitle>Prompt guidance now applies the Fixed classification to all defects, including security issues.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt now states “Fixed: a defect already produced wrong or failing behaviour.”</fact>     <fact>backen... [truncated]
```

### real-9e6612fdf9

```text
<observation>   <type>change</type>   <title>Clarified bugfix classification and added extend golden-set cases</title>   <subtitle>Prompts now distinguish non-security fixes, while golden-set rules recognize extend actions and their accepted verb variants.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt defines “Fixed” as a defect already producing wrong or faili... [truncated]
```

### real-e352ba3da3

```text
<observation>   <type>feature</type>   <title>Added v7 model output evaluation fixtures and reports</title>   <subtitle>New JSON fixtures, metrics, and archived evaluation reports capture post-v7 output for Longcat and Minimax.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v7.json contains 656 lines of Longcat post-v7 evaluation fixture data.</fact>     <fac... [truncated]
```

### real-112de33e23

```text
<observation>   <type>change</type>   <title>Documented replacement naming rule in prompt resources</title>   <subtitle>Prompt guidance now requires preserving both replaced and destination names.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt includes the rule that a replacement keeps both names.</fact>     <fact>backend/src/main/resources/prompts/init.txt incl... [truncated]
```

### real-28edef5a35

```text
<observation>   <type>feature</type>   <title>Added v6 model output evaluation fixtures and reports</title>   <subtitle>Longcat and MiniMax v6 outputs, metrics, and archived evaluation reports were added for model-output-quality testing.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v6.json contains 656 lines of LongCat v6 evaluation fixture output.</fact>  ... [truncated]
```

### real-6d6df2c321

```text
<observation>   <type>change</type>   <title>Updated prompt skip and observation guidance in backend/src/main/resources/prompts</title>   <subtitle>Prompt templates now define structured skip handling, recordable changes, title contracts, facts, concepts, and XML output requirements.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt removed 11 lines and added 71 li... [truncated]
```

### real-d3431639a9

```text
<observation>   <type>change</type>   <title>Documented model and summary quality evaluations in five evaluation files</title>   <subtitle>Added archived MiniMax and LongCat evaluation records plus a Round 4 extraction-quality improvement plan.</subtitle>   <facts>     <fact>docs/archive/2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md now contains 598 lines beginning with "# Mode... [truncated]
```

### real-7093cb5c56

```text
<observation>   <type>feature</type>   <title>Added EmbeddingBackfillConcurrencyDatabaseTest and EmbeddingBackfillServiceTest</title>   <subtitle>New tests cover concurrent embedding backfill, row-level persistence, service behavior, log sanitization, and controllable model calls.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/repository/EmbeddingBackfillConcurrencyDa... [truncated]
```

### real-f3d12860ff

```text
<observation>   <type>bugfix</type>   <title>Fixed log endpoint path traversal and credential exposure in LogsController</title>   <subtitle>LogsController now validates requested log paths and prevents sensitive file content from being returned.</subtitle>   <facts>     <fact>LogsController changed the `lines` request parameter handling and added validation around the requested log path.</fact>  ... [truncated]
```

### real-3409dda60c

```text
<observation>   <type>change</type>   <title>Documented implementation commit range in conversation-memory-scope-refactor-plan.md</title>   <subtitle>The refactor plan now identifies nine implementation commits and links to the commit list.</subtitle>   <facts>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md sets the implementation commit range to `325c817..f7b865e`.</fact>     <f... [truncated]
```

### real-f7b865efe5

```text
<observation>   <type>change</type>   <title>Updated header background color and rebuilt viewer assets in index.html</title>   <subtitle>Static UI styling changed to a warmer header tone, with the bundled viewer and webui submodule updated accordingly.</subtitle>   <facts>     <fact>backend/src/main/resources/static/index.html sets --color-bg-header to #f8f6f2, replacing #f6f8fa.</fact>     <fact>... [truncated]
```

### real-a8cc1754bc

```text
<observation>   <type>bugfix</type>   <title>Hardened message deduplication and conversation summary queries</title>   <subtitle>Repository queries now scope pending work correctly and summaries use conversation identifiers consistently.</subtitle>   <facts>     <fact>PendingMessageRepository.java removes the session-and-tool deduplication query logic.</fact>     <fact>AgentService.java documents ... [truncated]
```

### real-e2c887775a

```text
<observation>   <type>change</type>   <title>Updated context endpoint updateFiles contract in AGENTS.md</title>   <subtitle>AGENTS.md now maps updateFiles to the endpoint that actually returns the field.</subtitle>   <facts>     <fact>AGENTS.md changed the updateFiles row endpoint from /api/context/generate to /api/context/inject.</fact>     <fact>js-sdk/cortex-mem-js/src/client.ts removed four co... [truncated]
```

### real-cf667b4d8b

```text
<observation>   <type>change</type>   <title>Updated ObservationFeedbackRepository ordering comment and entity documentation</title>   <subtitle>Feedback lookup documentation now specifies newest-first ordering, with corresponding entity comment cleanup.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/repository/ObservationFeedbackRepository.java documents finding obse... [truncated]
```

### real-a0e77ca4ee

```text
<observation>   <type>change</type>   <title>Removed stale Lines columns from testing guides in docs/TESTING.md</title>   <subtitle>English and Chinese testing tables were synchronized while patrol and review state advanced to the demo cycle.</subtitle>   <facts>     <fact>docs/TESTING.md changed the table header from "| Script | Description | Lines |" to "| Script | Description |".</fact>     <fa... [truncated]
```

### real-d958416fdc

```text
<observation>   <type>change</type>   <title>Updated patrol state and architecture endpoint documentation</title>   <subtitle>Patrol records now reflect backend completion, and API documentation aligns the Viewer endpoint list.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json sets lastDirection to backend.</fact>     <fact>docs/drafts/patrol-rotation.md records Backend as the last comp... [truncated]
```

### real-9b424d0d02

```text
<observation>   <type>change</type>   <title>Documented GET /api/settings default corrections in docs/API.md</title>   <subtitle>English and Chinese API changelogs now document six corrected settings examples for version 0.1.0-beta+42.</subtitle>   <facts>     <fact>docs/API.md records that GET /api/settings corrected 6 example values to match AppSettings.toMap() actual defaults.</fact>     <fact>... [truncated]
```

### real-85480a720e

```text
<observation>   <type>change</type>   <title>Updated database environment variables in docs/TESTING.md and docs/TESTING-zh-CN.md</title>   <subtitle>Testing configuration tables now list DB_NAME instead of the previous DB_HOST entry.</subtitle>   <facts>     <fact>docs/TESTING.md replaces DB_HOST with DB_NAME and documents the default value claude_mem.</fact>     <fact>docs/TESTING-zh-CN.md replac... [truncated]
```

### real-c67e7a5ad3

```text
<observation>   <type>change</type>   <title>Removed empty else branch in python-sdk/cortex-mem-python/cortex_mem/dto.py</title>   <subtitle>Simplified DTO conditional flow by deleting an empty else branch and ignoring local dream memory state.</subtitle>   <facts>     <fact>python-sdk/cortex-mem-python/cortex_mem/dto.py removed the empty else: branch with 2 deleted lines and 0 added lines.</fact>... [truncated]
```

### real-bdf72f2509

```text
<observation>   <type>change</type>   <title>Removed SDK version User-Agent comment in CortexMemClientImpl</title>   <subtitle>Removed an obsolete release-maintenance comment from the client implementation.</subtitle>   <facts>     <fact>cortex-mem-spring-integration/cortex-mem-client/src/main/java/com/ablueforce/cortexce/client/CortexMemClientImpl.java removed the comment “SDK version for User-Ag... [truncated]
```

### real-8ac2a32980

```text
<observation>   <type>change</type>   <title>Updated default port comment to 37777 in backend/Dockerfile</title>   <subtitle>Dockerfile documentation now identifies 37777 as the default SERVER_PORT override, while deployment examples remove optional build arguments.</subtitle>   <facts>     <fact>backend/Dockerfile documents default port 37777 and states that SERVER_PORT can override it.</fact>   ... [truncated]
```

### real-bc3512ae8d

```text
<observation>   <type>change</type>   <title>Documented Round 6 category few-shots and measured title ceiling in extraction plan</title>   <subtitle>The extraction quality plan now records category-level few-shot guidance and measured title-length limits.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md contains the new section "7.12 Round 6 — category few-shots an... [truncated]
```

### real-e02e436bdd

```text
<observation>   <type>change</type>   <title>Updated prompt category examples in backend/src/main/resources/prompts/continuation.txt</title>   <subtitle>Prompt templates now define category examples with a consistent copyable format.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt replaced the example “Fixed UserCache.computeIfAbsent null placeholder race” with “... [truncated]
```

### real-0fd4ffcc31

```text
<observation>   <type>bugfix</type>   <title>Fixed must-record sample skip accounting in GoldenSetScorer</title>   <subtitle>Golden-set scoring now distinguishes skipped record-worthy samples from skipped non-record samples.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java adds the canonical mapping for the remaining documentation alias.</fact>... [truncated]
```

### real-eee7785ec8

```text
<observation>   <type>feature</type>   <title>Added provider-selectable model output quality evaluation harness</title>   <subtitle>GoldenSetEvaluator and eval-model-quality.sh support Anthropic alongside OpenAI for manual quality evaluation.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetEvaluator.java now imports org.springframework.ai.anthropic.Anthro... [truncated]
```

### real-9a0e90bed5

```text
<observation>   <type>feature</type>   <title>Extended ObservationRepository queries with memoryScopeId</title>   <subtitle>Observation retrieval now filters by memory scope while retaining optional project filtering.</subtitle>   <facts>     <fact>ObservationRepository.java changed the filter from `o.projectPath = :project` to an `o.memoryScopeId IN (...)` predicate.</fact>     <fact>MemoryScopeE... [truncated]
```

### real-63b5502fb9

```text
<observation>   <type>feature</type>   <title>Added conversation memory scope identity migration and rehearsal fixtures</title>   <subtitle>V20 now establishes canonical conversation and memory scope identities with dedicated database rehearsal scenarios.</subtitle>   <facts>     <fact>backend/src/main/resources/db/migration/V20__conversation_memory_scope_expand.sql now contains a one-shot migrati... [truncated]
```

### real-d33efbd8ae

```text
(no raw answer: the call failed before returning)
```

### real-cd6b972cfd

```text
<observation>   <type>feature</type>   <title>Added SummaryJobService service implementation</title>   <subtitle>Introduced a 326-line Java service class for summary job operations.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/service/SummaryJobService.java defines package com.ablueforce.cortexce.service.</fact>     <fact>SummaryJobService.java contains 326 added li... [truncated]
```

### real-46e467bff6

```text
<observation>   <type>feature</type>   <title>Added conversation scope preflight and fixture SQL scripts</title>   <subtitle>New database scripts and an additive migration support conversation and memory scope identity expansion.</subtitle>   <facts>     <fact>scripts/db/conversation-scope-preflight.sql was added with 426 lines for conversation-scope preflight validation.</fact>     <fact>scripts/... [truncated]
```

### real-325c817bbf

```text
<observation>   <type>change</type>   <title>Documented Conversation and Memory Scope identity-boundary refactor plan in docs/drafts/conversation-memory-scope-refactor-plan.md</title>   <subtitle>Added a 917-line implementation blueprint for separating Conversation and Memory Scope identity boundaries.</subtitle>   <facts>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md contains t... [truncated]
```

### real-95de35b2ea

```text
<observation>   <type>change</type>   <title>Updated byte-buddy pin and Docker build context documentation</title>   <subtitle>backend/pom.xml clarifies Mockito 5.11 byte-buddy compatibility, while the E2E script documents repository-root build context.</subtitle>   <facts>     <fact>backend/pom.xml comment now identifies byte-buddy 1.15.x as supplied by Mockito 5.11 through the spring-boot-starte... [truncated]
```

### real-53851b4a23

```text
<observation>   <type>change</type>   <title>Documented health check rounds 312–359+ in 2026-10-08_health-check-history-11.md</title>   <subtitle>Archived 1,206 lines of October 6–7 inspection history and indexed the archive in the README.</subtitle>   <facts>     <fact>docs/archive/2026-10-08_health-check-history-11.md contains the heading “Health Check History Archive 11（第 312~359+ 轮，2026-10-06 ... [truncated]
```

### real-f4c6765fb9

```text
(no raw answer: the call failed before returning)
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
