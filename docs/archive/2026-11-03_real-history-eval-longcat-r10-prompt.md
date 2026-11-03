golden set: real (60 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: r10-prompt
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 60 unique; runs scored: 1; key repeat: 3; real calls: 60 (cap none); duration: 1311028 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 95% |
| skip accuracy | 97% |
|   skip confusion (TP/FP/FN/TN) | 9/1/1/49 |
| format errors | 0 |
| validation rejections | 1 |
| call failures | 0 |
| title exact-hit rate (legacy) | 27% |
| title normalized hit rate (legacy) | 27% |
| title action-class hit rate (A6) | 92% (48 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 31% |
| title subject hit rate (legacy, diagnostic) | 77% |
| title trace-visible subject hit rate (A6b) | 77% (48 scored, 0 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 71% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 71% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 29% |
| title identifier hit rate | 77% |
| type accuracy | 58% |
| facts keyword precision (legacy) / recall | 100% / 100% |
| facts informative precision | 86% |
| facts trace-support rate | 100% |
| concepts precision / recall | 26% / 52% |
| skip consistency across repeats | 0% (0 repeated samples) |
| latency p50 / p95 / max (ms) | 20018 / 45155 / 108327 |
| tokens p50 / p95 / max / mean per call | 5091 / 6895 / 9836 / 5255 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 97% | PASS |
| A3 | must-record samples skipped | = 0 | 1 | **FAIL** |
| A4 | low-value samples recorded | <= max(1, TP/20) | 1 | PASS |
| A5 | facts informative precision | >= 75% | 86% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 92% | PASS |
| A6b | title trace-visible subject | >= 90% | 77% | **FAIL** |
| A6c | title identifiers | = 100% | 77% | **FAIL** |
| A6 | all three title dimensions together | all pass | 71% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 60 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 60 | 97% | 27% | 71% | 100% | 86% | 100% | 0 |

- average: skip 97%, title 27%, title contract 71%, facts P/R 100%/100%, facts actionable 86%
- worst run: skip 97%, title 27%, title contract 71%, facts P/R 100%/100%, facts actionable 86%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| feature | 9 | 9/9 | 6/9 | 0/9 |
| config-change | 1 | 1/1 | 1/1 | 0/1 |
| refactor | 3 | 3/3 | 0/3 | 1/3 |
| docs-update | 20 | 19/20 | 19/20 | 10/20 |
| test-add | 2 | 2/2 | 1/2 | 1/2 |
| security-fix | 10 | 10/10 | 1/10 | 1/10 |
| bug-fix | 5 | 4/5 | 0/5 | 0/5 |
| diagnose | 10 | 9/10 | 0/10 | 0/10 |

## Failure clusters (first run)

| cluster | misses | share of scored (48) |
|---|---|---|
| identifier missing | 11 | 23% |
| action class wrong | 4 | 8% |
| subject wrong | 11 | 23% |
| type wrong | 20 | 42% |

### identifier missing (11)

- real-1fe4ffb124 — expected identifiers [extraction-quality-improvement-plan.md], title 'Changed test floor min-tests from 423 to 472 in test-baseline.json'
- real-d13e2c90cd — expected identifiers [2026-11-03_real-history-golden-eval-minimax.md], title 'Added GoldenSetRealReplayTest golden eval replay for minimax-real-v9 fixtures'
- real-3b4f737562 — expected identifiers [minimax-after-v9.json], title 'Added minimax-after-v9 eval fixtures and metrics in backend/src/test/resources/eval/fixtures/'
- real-f0d346eab1 — expected identifiers [golden-tool-use-real.json], title 'Added GoldenSetRealDataTest with real-history tool-use golden set in GoldenSetRealDataTest.java'
- real-e352ba3da3 — expected identifiers [longcat-after-v7.json], title 'Added longcat-after-v7 and minimax-after-v7 eval fixture files and archive docs'
- real-28edef5a35 — expected identifiers [longcat-after-v6.json], title 'Added longcat-after-v6 and minimax-after-v6 eval fixtures and quality reports'
- real-cf667b4d8b — expected identifiers [ObservationFeedbackEntity], title 'Updated ObservationFeedbackRepository ordering comment to most recent first'
- real-9b424d0d02 — expected identifiers [API-zh-CN.md], title 'Documented /api/settings example value corrections in docs/API.md'
- real-85480a720e — expected identifiers [TESTING-zh-CN.md], title 'Updated database environment variable from DB_HOST to DB_NAME in TESTING docs'
- real-0fd4ffcc31 — expected identifiers [GoldenSetScorer], title 'Fixed skipFp counter check in GoldenSetEvaluator A3 gate'
- real-eee7785ec8 — expected identifiers [2026-11-02_model-output-quality-eval-minimax.md], title 'Extended GoldenSetEvaluator and eval-model-quality.sh with Anthropic provider support'

### action class wrong (4)

- real-3a2d426fdf — expected [modify], got 'create' from title 'Added prompt token tracking and EVAL_REPEAT_ALL repeat mode to GoldenSetScorer'
- real-a8cc1754bc — expected [remove, secure, modify], got 'null' from title 'Moved pending message deduplication from PendingMessageRepository to AgentService'
- real-85480a720e — expected [remove, document], got 'modify' from title 'Updated database environment variable from DB_HOST to DB_NAME in TESTING docs'
- real-95de35b2ea — expected [modify], got 'document' from title 'Documented byte-buddy pin rationale and Docker build context note in backend/pom.xml'

### subject wrong (11)

- real-1fe4ffb124 — expected subject tokens [extraction-quality-improvement-plan.md], title 'Changed test floor min-tests from 423 to 472 in test-baseline.json'
- real-d13e2c90cd — expected subject tokens [2026-11-03_real-history-golden-eval-minimax.md], title 'Added GoldenSetRealReplayTest golden eval replay for minimax-real-v9 fixtures'
- real-3b4f737562 — expected subject tokens [minimax-after-v9.json], title 'Added minimax-after-v9 eval fixtures and metrics in backend/src/test/resources/eval/fixtures/'
- real-f0d346eab1 — expected subject tokens [golden-tool-use-real.json], title 'Added GoldenSetRealDataTest with real-history tool-use golden set in GoldenSetRealDataTest.java'
- real-e352ba3da3 — expected subject tokens [longcat-after-v7.json], title 'Added longcat-after-v7 and minimax-after-v7 eval fixture files and archive docs'
- real-28edef5a35 — expected subject tokens [longcat-after-v6.json], title 'Added longcat-after-v6 and minimax-after-v6 eval fixtures and quality reports'
- real-cf667b4d8b — expected subject tokens [ObservationFeedbackEntity], title 'Updated ObservationFeedbackRepository ordering comment to most recent first'
- real-9b424d0d02 — expected subject tokens [API-zh-CN.md], title 'Documented /api/settings example value corrections in docs/API.md'
- real-85480a720e — expected subject tokens [TESTING-zh-CN.md], title 'Updated database environment variable from DB_HOST to DB_NAME in TESTING docs'
- real-0fd4ffcc31 — expected subject tokens [GoldenSetScorer], title 'Fixed skipFp counter check in GoldenSetEvaluator A3 gate'
- real-eee7785ec8 — expected subject tokens [2026-11-02_model-output-quality-eval-minimax.md], title 'Extended GoldenSetEvaluator and eval-model-quality.sh with Anthropic provider support'

### type wrong (20)

- real-f6a1b89e41 — expected 'change', got 'feature' (title 'Updated golden-tool-use-real.json to real-git-2026-11-03-r10 with expanded eval samples')
- real-77ba40df77 — expected 'refactor', got 'feature' (title 'Added ExtractionConsistency majority-vote service in ExtractionConsistency.java')
- real-f0d346eab1 — expected 'change', got 'feature' (title 'Added GoldenSetRealDataTest with real-history tool-use golden set in GoldenSetRealDataTest.java')
- real-3a2d426fdf — expected 'refactor', got 'feature' (title 'Added prompt token tracking and EVAL_REPEAT_ALL repeat mode to GoldenSetScorer')
- real-9e6612fdf9 — expected 'bugfix', got 'feature' (title 'Updated Fixed verb definition to exclude security defects in continuation.txt')
- real-e352ba3da3 — expected 'feature', got 'change' (title 'Added longcat-after-v7 and minimax-after-v7 eval fixture files and archive docs')
- real-7093cb5c56 — expected 'bugfix', got 'feature' (title 'Added EmbeddingBackfillConcurrencyDatabaseTest for concurrent backfill race coverage')
- real-f7b865efe5 — expected 'bugfix', got 'change' (title 'Changed --color-bg-header to #f8f6f2 in index.html')
- real-a8cc1754bc — expected 'bugfix', got 'refactor' (title 'Moved pending message deduplication from PendingMessageRepository to AgentService')
- real-e2c887775a — expected 'bugfix', got 'change' (title 'Fixed AGENTS.md hard-contract table endpoint from /api/context/generate to /api/context/inject')
- real-cf667b4d8b — expected 'refactor', got 'change' (title 'Updated ObservationFeedbackRepository ordering comment to most recent first')
- real-c67e7a5ad3 — expected 'bugfix', got 'refactor' (title 'Simplified conditional in dto.py and ignored memory/.dreams in .gitignore')
- real-8ac2a32980 — expected 'bugfix', got 'change' (title 'Documented default port 37777 in backend/Dockerfile')
- real-0fd4ffcc31 — expected 'feature', got 'bugfix' (title 'Fixed skipFp counter check in GoldenSetEvaluator A3 gate')
- real-9a0e90bed5 — expected 'bugfix', got 'feature' (title 'Changed ObservationRepository project filter from projectPath to memoryScopeId')
- real-63b5502fb9 — expected 'bugfix', got 'change' (title 'Extended V20 conversation memory scope migration in V20__conversation_memory_scope_expand.sql')
- real-d33efbd8ae — expected 'bugfix', got 'feature' (title 'Added migration runner ConversationScopeBackfillRunner')
- real-cd6b972cfd — expected 'bugfix', got 'feature' (title 'Added SummaryJobService in backend/src/main/java/com/ablueforce/cortexce/service')
- real-46e467bff6 — expected 'bugfix', got 'feature' (title 'Added conversation scope migration and preflight scripts in conversation-scope-preflight.sql')
- real-95de35b2ea — expected 'bugfix', got 'change' (title 'Documented byte-buddy pin rationale and Docker build context note in backend/pom.xml')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| real-f6a1b89e41 | feature | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 23400 |
| real-1fe4ffb124 | config-change | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 23745 |
| real-d13e2c90cd | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 10576 |
| real-3b4f737562 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 32913 |
| real-77ba40df77 | refactor | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/100% | 24610 |
| real-f0d346eab1 | feature | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 50%/100% | 21111 |
| real-3a2d426fdf | refactor | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 50%/100% | 20187 |
| real-864dadf51b | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 75% | 50%/100% | 75729 |
| real-b064308244 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 18908 |
| real-30a956e462 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 15988 |
| real-ba86bf2588 | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/100% | 20018 |
| real-a9c8452967 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 27017 |
| real-db8ac0287e | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 75% | 0%/0% | 12983 |
| real-9e6612fdf9 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 75% | 0%/0% | 29807 |
| real-f43cfd6f44 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 12583 |
| real-e352ba3da3 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 50%/100% | 10085 |
| real-112de33e23 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 22981 |
| real-28edef5a35 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 22120 |
| real-6d6df2c321 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 67% | 0%/0% | 44606 |
| real-d3431639a9 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 108327 |
| real-7093cb5c56 | security-fix | observation | observation | pass | 0% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 38654 |
| real-f3d12860ff | security-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 26125 |
| real-3409dda60c | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 45155 |
| real-f7b865efe5 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 13391 |
| real-a8cc1754bc | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 80% | 0%/0% | 21426 |
| real-e2c887775a | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 60% | 50%/100% | 24408 |
| real-cf667b4d8b | refactor | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 0% | 0%/0% | 28331 |
| real-a0e77ca4ee | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 22937 |
| real-d958416fdc | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 60% | 0%/0% | 19282 |
| real-9b424d0d02 | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 12877 |
| real-85480a720e | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | match | 100%/100% | 100% | 0%/0% | 6913 |
| real-c67e7a5ad3 | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 10833 |
| real-bdf72f2509 | bug-fix | observation | skip | FAIL | - | - | - | - | - | - | - | 5128 |
| real-8ac2a32980 | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 22647 |
| real-2025b33a95 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2738 |
| real-e8c8b314d6 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2466 |
| real-c42d7a9c5d | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2456 |
| real-b5ae725e2a | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2573 |
| real-9261b542ca | diagnose | skip | observation | FAIL | 0% | MISS (subject unreachable, id MISS, action MISS) | miss | MISS | 0%/0% | 0% | 0%/0% | 6412 |
| real-7c557c8d79 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2975 |
| real-1dbccd058e | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2563 |
| real-1f9b5d09e7 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2723 |
| real-ed737be6bb | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 4668 |
| real-b2d48b12a1 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 4007 |
| real-bc3512ae8d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 20532 |
| real-e02e436bdd | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 50% | 50%/100% | 19523 |
| real-d44a83721d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/100% | 22305 |
| real-0fd4ffcc31 | test-add | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 0%/0% | 34255 |
| real-eee7785ec8 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 19010 |
| real-9a0e90bed5 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 26727 |
| real-63b5502fb9 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 38461 |
| real-d33efbd8ae | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 50% | 0%/0% | 39608 |
| real-cd6b972cfd | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 50% | 0%/0% | 14123 |
| real-46e467bff6 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 64861 |
| real-325c817bbf | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 33% | 50%/100% | 28645 |
| real-7149578a88 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 10950 |
| real-95de35b2ea | bug-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 0%/0% | 27339 |
| real-53851b4a23 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 15923 |
| real-f4c6765fb9 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 50% | 50%/100% | 9387 |
| real-67083a7bc1 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 9358 |

## Problems

- real-f6a1b89e41: title coverage 50% (expected keywords [change, golden-tool-use-real.json])
- real-f6a1b89e41: type 'feature' != expected 'change'
- real-1fe4ffb124: title lost identifiers [extraction-quality-improvement-plan.md] (got 'Changed test floor min-tests from 423 to 472 in test-baseline.json')
- real-1fe4ffb124: title verb class 'document' missing (got 'Changed test floor min-tests from 423 to 472 in test-baseline.json')
- real-1fe4ffb124: title subject [extraction-quality-improvement-plan.md] missing (got 'Changed test floor min-tests from 423 to 472 in test-baseline.json')
- real-1fe4ffb124: title trace-visible subject [extraction-quality-improvement-plan.md] missing (got 'Changed test floor min-tests from 423 to 472 in test-baseline.json')
- real-d13e2c90cd: title lost identifiers [2026-11-03_real-history-golden-eval-minimax.md] (got 'Added GoldenSetRealReplayTest golden eval replay for minimax-real-v9 fixtures')
- real-d13e2c90cd: title verb class 'document' missing (got 'Added GoldenSetRealReplayTest golden eval replay for minimax-real-v9 fixtures')
- real-d13e2c90cd: title subject [2026-11-03_real-history-golden-eval-minimax.md] missing (got 'Added GoldenSetRealReplayTest golden eval replay for minimax-real-v9 fixtures')
- real-d13e2c90cd: title trace-visible subject [2026-11-03_real-history-golden-eval-minimax.md] missing (got 'Added GoldenSetRealReplayTest golden eval replay for minimax-real-v9 fixtures')
- real-3b4f737562: title lost identifiers [minimax-after-v9.json] (got 'Added minimax-after-v9 eval fixtures and metrics in backend/src/test/resources/eval/fixtures/')
- real-3b4f737562: title verb class 'document' missing (got 'Added minimax-after-v9 eval fixtures and metrics in backend/src/test/resources/eval/fixtures/')
- real-3b4f737562: title subject [minimax-after-v9.json] missing (got 'Added minimax-after-v9 eval fixtures and metrics in backend/src/test/resources/eval/fixtures/')
- real-3b4f737562: title trace-visible subject [minimax-after-v9.json] missing (got 'Added minimax-after-v9 eval fixtures and metrics in backend/src/test/resources/eval/fixtures/')
- real-77ba40df77: type 'feature' != expected 'refactor'
- real-f0d346eab1: title lost identifiers [golden-tool-use-real.json] (got 'Added GoldenSetRealDataTest with real-history tool-use golden set in GoldenSetRealDataTest.java')
- real-f0d346eab1: title subject [golden-tool-use-real.json] missing (got 'Added GoldenSetRealDataTest with real-history tool-use golden set in GoldenSetRealDataTest.java')
- real-f0d346eab1: title trace-visible subject [golden-tool-use-real.json] missing (got 'Added GoldenSetRealDataTest with real-history tool-use golden set in GoldenSetRealDataTest.java')
- real-f0d346eab1: type 'feature' != expected 'change'
- real-3a2d426fdf: title coverage 50% (expected keywords [change, goldensetscorer])
- real-3a2d426fdf: title action [modify] missing: title leads 'create' (got 'Added prompt token tracking and EVAL_REPEAT_ALL repeat mode to GoldenSetScorer')
- real-3a2d426fdf: title verb class 'change' missing (got 'Added prompt token tracking and EVAL_REPEAT_ALL repeat mode to GoldenSetScorer')
- real-3a2d426fdf: type 'feature' != expected 'refactor'
- real-864dadf51b: title coverage 50% (expected keywords [document, goldensetscorer])
- real-864dadf51b: title verb class 'document' missing (got 'Extended GoldenSetScorer evidence construction in GoldenSetScorer.java')
- real-b064308244: title coverage 50% (expected keywords [document, extraction-quality-improvement-plan.md])
- real-b064308244: title verb class 'document' missing (got 'Updated test layer description to rehearsal database gate in extraction-quality-improvement-plan.md')
- real-30a956e462: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a9c8452967: title coverage 50% (expected keywords [document, searchsimilarityevaluator])
- real-a9c8452967: title verb class 'document' missing (got 'Added SearchSimilarityEvaluator semantic relevance floor evaluation')
- real-db8ac0287e: title coverage 50% (expected keywords [document, continuation.txt])
- real-db8ac0287e: title verb class 'document' missing (got 'Updated Fixed verb definition in continuation.txt and init.txt prompts')
- real-db8ac0287e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9e6612fdf9: title coverage 50% (expected keywords [document, continuation.txt])
- real-9e6612fdf9: title verb class 'document' missing (got 'Updated Fixed verb definition to exclude security defects in continuation.txt')
- real-9e6612fdf9: type 'feature' != expected 'bugfix'
- real-9e6612fdf9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e352ba3da3: title lost identifiers [longcat-after-v7.json] (got 'Added longcat-after-v7 and minimax-after-v7 eval fixture files and archive docs')
- real-e352ba3da3: title verb class 'document' missing (got 'Added longcat-after-v7 and minimax-after-v7 eval fixture files and archive docs')
- real-e352ba3da3: title subject [longcat-after-v7.json] missing (got 'Added longcat-after-v7 and minimax-after-v7 eval fixture files and archive docs')
- real-e352ba3da3: title trace-visible subject [longcat-after-v7.json] missing (got 'Added longcat-after-v7 and minimax-after-v7 eval fixture files and archive docs')
- real-e352ba3da3: type 'change' != expected 'feature'
- real-112de33e23: title coverage 50% (expected keywords [document, continuation.txt])
- real-112de33e23: title verb class 'document' missing (got 'Extended continuation prompt with replacement naming rule in continuation.txt')
- real-28edef5a35: title lost identifiers [longcat-after-v6.json] (got 'Added longcat-after-v6 and minimax-after-v6 eval fixtures and quality reports')
- real-28edef5a35: title verb class 'document' missing (got 'Added longcat-after-v6 and minimax-after-v6 eval fixtures and quality reports')
- real-28edef5a35: title subject [longcat-after-v6.json] missing (got 'Added longcat-after-v6 and minimax-after-v6 eval fixtures and quality reports')
- real-28edef5a35: title trace-visible subject [longcat-after-v6.json] missing (got 'Added longcat-after-v6 and minimax-after-v6 eval fixtures and quality reports')
- real-6d6df2c321: title coverage 50% (expected keywords [document, continuation.txt])
- real-6d6df2c321: title verb class 'document' missing (got 'Extended skip criteria in prompts/continuation.txt and prompts/init.txt')
- real-6d6df2c321: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d3431639a9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-7093cb5c56: title coverage 0% (expected keywords [harden, embeddingbackfillconcurrencydatabasetest])
- real-7093cb5c56: title verb class 'harden' missing (got 'Added EmbeddingBackfillConcurrencyDatabaseTest for concurrent backfill race coverage')
- real-7093cb5c56: type 'feature' != expected 'bugfix'
- real-7093cb5c56: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-3409dda60c: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-f7b865efe5: title coverage 50% (expected keywords [harden, index.html])
- real-f7b865efe5: title verb class 'harden' missing (got 'Changed --color-bg-header to #f8f6f2 in index.html')
- real-f7b865efe5: type 'change' != expected 'bugfix'
- real-f7b865efe5: concepts recall 0% (expected [problem-solution], got [what-changed, what-changed])
- real-a8cc1754bc: title coverage 50% (expected keywords [remove, pendingmessagerepository])
- real-a8cc1754bc: title action [remove, secure, modify] missing: title leads 'null' (got 'Moved pending message deduplication from PendingMessageRepository to AgentService')
- real-a8cc1754bc: title verb class 'remove' missing (got 'Moved pending message deduplication from PendingMessageRepository to AgentService')
- real-a8cc1754bc: type 'refactor' != expected 'bugfix'
- real-a8cc1754bc: concepts recall 0% (expected [problem-solution], got [pattern, what-changed])
- real-e2c887775a: title coverage 50% (expected keywords [remove, agents.md])
- real-e2c887775a: title verb class 'remove' missing (got 'Fixed AGENTS.md hard-contract table endpoint from /api/context/generate to /api/context/inject')
- real-e2c887775a: type 'change' != expected 'bugfix'
- real-cf667b4d8b: title lost identifiers [ObservationFeedbackEntity] (got 'Updated ObservationFeedbackRepository ordering comment to most recent first')
- real-cf667b4d8b: title verb class 'remove' missing (got 'Updated ObservationFeedbackRepository ordering comment to most recent first')
- real-cf667b4d8b: title subject [ObservationFeedbackEntity] missing (got 'Updated ObservationFeedbackRepository ordering comment to most recent first')
- real-cf667b4d8b: title trace-visible subject [ObservationFeedbackEntity] missing (got 'Updated ObservationFeedbackRepository ordering comment to most recent first')
- real-cf667b4d8b: type 'change' != expected 'refactor'
- real-cf667b4d8b: concepts recall 0% (expected [problem-solution], got [what-changed, gotcha])
- real-a0e77ca4ee: validation rejected the observation: no verifiable facts after evidence check
- real-d958416fdc: title coverage 50% (expected keywords [remove, patrol-state.json])
- real-d958416fdc: title verb class 'remove' missing (got 'Changed patrol lastDirection to backend in docs/drafts/patrol-state.json')
- real-d958416fdc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9b424d0d02: title lost identifiers [API-zh-CN.md] (got 'Documented /api/settings example value corrections in docs/API.md')
- real-9b424d0d02: title verb class 'remove' missing (got 'Documented /api/settings example value corrections in docs/API.md')
- real-9b424d0d02: title subject [API-zh-CN.md] missing (got 'Documented /api/settings example value corrections in docs/API.md')
- real-9b424d0d02: title trace-visible subject [API-zh-CN.md] missing (got 'Documented /api/settings example value corrections in docs/API.md')
- real-9b424d0d02: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-85480a720e: title lost identifiers [TESTING-zh-CN.md] (got 'Updated database environment variable from DB_HOST to DB_NAME in TESTING docs')
- real-85480a720e: title action [remove, document] missing: title leads 'modify' (got 'Updated database environment variable from DB_HOST to DB_NAME in TESTING docs')
- real-85480a720e: title verb class 'remove' missing (got 'Updated database environment variable from DB_HOST to DB_NAME in TESTING docs')
- real-85480a720e: title subject [TESTING-zh-CN.md] missing (got 'Updated database environment variable from DB_HOST to DB_NAME in TESTING docs')
- real-85480a720e: title trace-visible subject [TESTING-zh-CN.md] missing (got 'Updated database environment variable from DB_HOST to DB_NAME in TESTING docs')
- real-85480a720e: concepts recall 0% (expected [problem-solution], got [what-changed, gotcha])
- real-c67e7a5ad3: title coverage 50% (expected keywords [remove, dto.py])
- real-c67e7a5ad3: title verb class 'remove' missing (got 'Simplified conditional in dto.py and ignored memory/.dreams in .gitignore')
- real-c67e7a5ad3: type 'refactor' != expected 'bugfix'
- real-c67e7a5ad3: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-bdf72f2509: expected an observation but model skipped
- real-8ac2a32980: title coverage 50% (expected keywords [remove, dockerfile])
- real-8ac2a32980: title verb class 'remove' missing (got 'Documented default port 37777 in backend/Dockerfile')
- real-8ac2a32980: type 'change' != expected 'bugfix'
- real-8ac2a32980: concepts recall 0% (expected [problem-solution], got [what-changed, what-changed])
- real-9261b542ca: expected <skip> but produced an observation
- real-e02e436bdd: title coverage 50% (expected keywords [document, continuation.txt])
- real-e02e436bdd: title verb class 'document' missing (got 'Updated prompt example heading in continuation.txt and init.txt')
- real-0fd4ffcc31: title lost identifiers [GoldenSetScorer] (got 'Fixed skipFp counter check in GoldenSetEvaluator A3 gate')
- real-0fd4ffcc31: title verb class 'change' missing (got 'Fixed skipFp counter check in GoldenSetEvaluator A3 gate')
- real-0fd4ffcc31: title subject [GoldenSetScorer] missing (got 'Fixed skipFp counter check in GoldenSetEvaluator A3 gate')
- real-0fd4ffcc31: title trace-visible subject [GoldenSetScorer] missing (got 'Fixed skipFp counter check in GoldenSetEvaluator A3 gate')
- real-0fd4ffcc31: type 'bugfix' != expected 'feature'
- real-0fd4ffcc31: concepts recall 0% (expected [what-changed], got [problem-solution, gotcha])
- real-eee7785ec8: title lost identifiers [2026-11-02_model-output-quality-eval-minimax.md] (got 'Extended GoldenSetEvaluator and eval-model-quality.sh with Anthropic provider support')
- real-eee7785ec8: title verb class 'document' missing (got 'Extended GoldenSetEvaluator and eval-model-quality.sh with Anthropic provider support')
- real-eee7785ec8: title subject [2026-11-02_model-output-quality-eval-minimax.md] missing (got 'Extended GoldenSetEvaluator and eval-model-quality.sh with Anthropic provider support')
- real-eee7785ec8: title trace-visible subject [2026-11-02_model-output-quality-eval-minimax.md] missing (got 'Extended GoldenSetEvaluator and eval-model-quality.sh with Anthropic provider support')
- real-9a0e90bed5: title coverage 50% (expected keywords [harden, observationrepository])
- real-9a0e90bed5: title verb class 'harden' missing (got 'Changed ObservationRepository project filter from projectPath to memoryScopeId')
- real-9a0e90bed5: type 'feature' != expected 'bugfix'
- real-9a0e90bed5: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-63b5502fb9: title coverage 50% (expected keywords [harden, v20__conversation_memory_scope_expand.sql])
- real-63b5502fb9: title verb class 'harden' missing (got 'Extended V20 conversation memory scope migration in V20__conversation_memory_scope_expand.sql')
- real-63b5502fb9: type 'change' != expected 'bugfix'
- real-63b5502fb9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d33efbd8ae: title coverage 50% (expected keywords [harden, conversationscopebackfillrunner])
- real-d33efbd8ae: title verb class 'harden' missing (got 'Added migration runner ConversationScopeBackfillRunner')
- real-d33efbd8ae: type 'feature' != expected 'bugfix'
- real-d33efbd8ae: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-cd6b972cfd: title coverage 50% (expected keywords [harden, summaryjobservice])
- real-cd6b972cfd: title verb class 'harden' missing (got 'Added SummaryJobService in backend/src/main/java/com/ablueforce/cortexce/service')
- real-cd6b972cfd: type 'feature' != expected 'bugfix'
- real-cd6b972cfd: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-46e467bff6: title coverage 50% (expected keywords [harden, conversation-scope-preflight.sql])
- real-46e467bff6: title verb class 'harden' missing (got 'Added conversation scope migration and preflight scripts in conversation-scope-preflight.sql')
- real-46e467bff6: type 'feature' != expected 'bugfix'
- real-46e467bff6: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-95de35b2ea: title coverage 50% (expected keywords [change, pom.xml])
- real-95de35b2ea: title action [modify] missing: title leads 'document' (got 'Documented byte-buddy pin rationale and Docker build context note in backend/pom.xml')
- real-95de35b2ea: title verb class 'change' missing (got 'Documented byte-buddy pin rationale and Docker build context note in backend/pom.xml')
- real-95de35b2ea: type 'change' != expected 'bugfix'
- real-95de35b2ea: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-53851b4a23: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-f4c6765fb9: title coverage 50% (expected keywords [document, performance-test-results.md])
- real-f4c6765fb9: title verb class 'document' missing (got 'Added performance test results draft in docs/drafts/performance-test-results.md')

## Raw answers behind problems

### real-f6a1b89e41

```text
<observation>   <type>feature</type>   <title>Updated golden-tool-use-real.json to real-git-2026-11-03-r10 with expanded eval samples</title>   <subtitle>Expanded the real-history golden eval set and added scorer fields to capture round-10 failure-cluster evidence.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json version changed from "real-git-2026-11-03" to... [truncated]
```

### real-1fe4ffb124

```text
<observation>   <type>change</type>   <title>Changed test floor min-tests from 423 to 472 in test-baseline.json</title>   <subtitle>Raised the minimum test count floor to 472 across baseline config, CI workflow, local verify script, and both testing docs.</subtitle>   <facts>     <fact>scripts/test-baseline.json sets "total" to 472, up from 423</fact>     <fact>.github/workflows/backend-tests.yml ... [truncated]
```

### real-d13e2c90cd

```text
<observation>   <type>feature</type>   <title>Added GoldenSetRealReplayTest golden eval replay for minimax-real-v9 fixtures</title>   <subtitle>New golden eval set replay test covering 36 real history samples with fixtures and metrics baseline.</subtitle>   <facts>     <fact>GoldenSetRealReplayTest.java added under backend/src/test/java/com/ablueforce/cortexce/eval with 87 lines</fact>     <fact>F... [truncated]
```

### real-3b4f737562

```text
<observation>   <type>feature</type>   <title>Added minimax-after-v9 eval fixtures and metrics in backend/src/test/resources/eval/fixtures/</title>   <subtitle>New evaluation fixture data, aggregate metrics, and documentation for minimax-after-v9 model output quality assessment.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/minimax-after-v9.json added with 2096 lines of m... [truncated]
```

### real-77ba40df77

```text
<observation>   <type>feature</type>   <title>Added ExtractionConsistency majority-vote service in ExtractionConsistency.java</title>   <subtitle>New service and test add repeat-call voting for tool traces, configurable via application.yml attempts setting.</subtitle>   <facts>     <fact>Created backend/src/main/java/com/ablueforce/cortexce/service/ExtractionConsistency.java with 82 lines.</fact> ... [truncated]
```

### real-f0d346eab1

```text
<observation>   <type>feature</type>   <title>Added GoldenSetRealDataTest with real-history tool-use golden set in GoldenSetRealDataTest.java</title>   <subtitle>New mechanically generated real-history golden set and generator script extend the model-quality evaluation harness.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json is a new 1097-line golden tool-u... [truncated]
```

### real-3a2d426fdf

```text
<observation>   <type>feature</type>   <title>Added prompt token tracking and EVAL_REPEAT_ALL repeat mode to GoldenSetScorer</title>   <subtitle>GoldenSetScorer now records prompt tokens and the evaluator can repeat every sample via EVAL_REPEAT_ALL.</subtitle>   <facts>     <fact>GoldenSetScorer.java added a `public final int promptTokens;` field and 149 new lines, replacing the constructor call `... [truncated]
```

### real-864dadf51b

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetScorer evidence construction in GoldenSetScorer.java</title>   <subtitle>Golden-set scoring gained expanded evidence construction, a legacy metric label, a toolInput test case, and updated prompt guidance.</subtitle>   <facts>     <fact>GoldenSetScorer.java replaced the toolName-only evidence line `String evidence = (sample.toolName =... [truncated]
```

### real-b064308244

```text
<observation>   <type>change</type>   <title>Updated test layer description to rehearsal database gate in extraction-quality-improvement-plan.md</title>   <subtitle>Draft plan documentation now describes the test as running under the rehearsal database gate rather than the L4 layer.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md replaced the phrase "The test runs... [truncated]
```

### real-30a956e462

```text
<observation>   <type>change</type>   <title>Documented Round 8 insertion-scope contract in extraction-quality-improvement-plan.md</title>   <subtitle>New section 7.14 records Round 8 extraction plan decisions on insertion scope, coverage removal, and a reverted security verb.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md gained section "### 7.14 Round 8 — inser... [truncated]
```

### real-a9c8452967

```text
<observation>   <type>feature</type>   <title>Added SearchSimilarityEvaluator semantic relevance floor evaluation</title>   <subtitle>New replayable eval harness scores search similarity against BGE-M3 fixtures and a corpus.</subtitle>   <facts>     <fact>SearchSimilarityEvaluator.java adds 411 lines under package com.ablueforce.cortexce.eval</fact>     <fact>SearchSimilarityReplayTest.java adds 1... [truncated]
```

### real-db8ac0287e

```text
<observation>   <type>change</type>   <title>Updated Fixed verb definition in continuation.txt and init.txt prompts</title>   <subtitle>Removed the "in non-security code" qualifier so the Fixed classification now covers security defects too.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt had the line "- Fixed: a defect already produced wrong or failing behaviour... [truncated]
```

### real-9e6612fdf9

```text
<observation>   <type>feature</type>   <title>Updated Fixed verb definition to exclude security defects in continuation.txt</title>   <subtitle>Observation verb taxonomy now scopes "Fixed" to non-security code and adds an "extend" verb to the golden set.</subtitle>   <facts>     <fact>continuation.txt now defines "Fixed" as "a defect already produced wrong or failing behaviour, in non-security cod... [truncated]
```

### real-e352ba3da3

```text
<observation>   <type>change</type>   <title>Added longcat-after-v7 and minimax-after-v7 eval fixture files and archive docs</title>   <subtitle>Created six new files capturing model output quality evaluation fixtures and archived reports for after-v7 runs.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v7.json added with 656 lines</fact>     <fact>backend/sr... [truncated]
```

### real-112de33e23

```text
```xml <observation>   <type>change</type>   <title>Extended continuation prompt with replacement naming rule in continuation.txt</title>   <subtitle>Added guidance that a replacement must keep both the old and new names in a title.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt gained 3 lines stating "A replacement keeps BOTH names: the literal or key that was ... [truncated]
```

### real-28edef5a35

```text
<observation>   <type>feature</type>   <title>Added longcat-after-v6 and minimax-after-v6 eval fixtures and quality reports</title>   <subtitle>New model output quality evaluation fixtures, metrics, and archived reports for longcat and minimax after-v6</subtitle>   <facts>     <fact>Added backend/src/test/resources/eval/fixtures/longcat-after-v6.json as a 656-line fixture starting with `{`</fact> ... [truncated]
```

### real-6d6df2c321

```text
<observation>   <type>change</type>   <title>Extended skip criteria in prompts/continuation.txt and prompts/init.txt</title>   <subtitle>The memory agent's WHEN TO SKIP rules now explicitly exclude read-only identifier searches and other non-deliverable turns in both prompt files.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt replaced the trailing fragment "fai... [truncated]
```

### real-d3431639a9

```text
<observation>   <type>change</type>   <title>Documented eval in docs/archive/2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md</title>   <subtitle>Authored five new evaluation and planning documents covering model-output and summary-chain quality after the v4 contract.</subtitle>   <facts>     <fact>docs/archive/2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md was ... [truncated]
```

### real-7093cb5c56

```text
<observation>   <type>feature</type>   <title>Added EmbeddingBackfillConcurrencyDatabaseTest for concurrent backfill race coverage</title>   <subtitle>New database and service tests verify concurrent embedding backfill behavior; EmbeddingBackfillService refactored with UUID-based tracking.</subtitle>   <facts>     <fact>New file backend/src/test/java/com/ablueforce/cortexce/repository/EmbeddingBac... [truncated]
```

### real-3409dda60c

```text
<observation>   <type>change</type>   <title>Documented commit range 325c817..f7b865e in conversation-memory-scope-refactor-plan.md</title>   <subtitle>Updated the refactor plan draft to record the actual implementation commit range instead of the placeholder "待实施".</subtitle>   <facts>     <fact>The table row `实施 commit 范围` in `docs/drafts/conversation-memory-scope-refactor-plan.md` was changed f... [truncated]
```

### real-f7b865efe5

```text
<observation>   <type>change</type>   <title>Changed --color-bg-header to #f8f6f2 in index.html</title>   <subtitle>Updated the header background color token from #f6f8fa to #f8f6f2 and rebuilt the static viewer bundle.</subtitle>   <facts>     <fact>CSS variable `--color-bg-header` changed from `#f6f8fa` to `#f8f6f2` in backend/src/main/resources/static/index.html.</fact>     <fact>backend/src/ma... [truncated]
```

### real-a8cc1754bc

```text
<observation>   <type>refactor</type>   <title>Moved pending message deduplication from PendingMessageRepository to AgentService</title>   <subtitle>Relocated dedup logic to service layer and aligned query naming across repositories.</subtitle>   <facts>     <fact>PendingMessageRepository removed 22 lines of dedup query methods for session + tool checks.</fact>     <fact>AgentService now owns dedu... [truncated]
```

### real-e2c887775a

```text
<observation>   <type>change</type>   <title>Fixed AGENTS.md hard-contract table endpoint from /api/context/generate to /api/context/inject</title>   <subtitle>Corrected a version-controlled contract table that pointed field-protection rules at an endpoint which never returned those fields.</subtitle>   <facts>     <fact>AGENTS.md hard-contract table row changed from `/api/context/generate` to `/a... [truncated]
```

### real-cf667b4d8b

```text
<observation>   <type>change</type>   <title>Updated ObservationFeedbackRepository ordering comment to most recent first</title>   <subtitle>Comment-only edits reworded feedback ordering semantics and trimmed entity Javadoc.</subtitle>   <facts>     <fact>ObservationFeedbackRepository.java comment changed from "ordered by creation time" to "most recent first".</fact>     <fact>ObservationFeedbackE... [truncated]
```

### real-a0e77ca4ee

```text
<observation>   <type>change</type>   <title>Removed drift-prone Lines column from TESTING.md script table</title>   <subtitle>Doc patrol review deleted the script line-count column that drifted from source and synchronized the Chinese edition.</subtitle>   <facts>     <docs/TESTING.md table header changed from `| Script | Description | Lines |` to `| Script | Description |`, removing the per-scri... [truncated]
```

### real-d958416fdc

```text
<observation>   <type>change</type>   <title>Changed patrol lastDirection to backend in docs/drafts/patrol-state.json</title>   <subtitle>Advanced the doc-review patrol rotation from demo to backend phase and synced rotation state across draft docs.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json now sets `"lastDirection": "backend"`, replacing `"demo"`.</fact>     <fact>docs/drafts/p... [truncated]
```

### real-9b424d0d02

```text
```xml <observation>   <type>change</type>   <title>Documented /api/settings example value corrections in docs/API.md</title>   <subtitle>Added changelog entry for version 0.1.0-beta+42 correcting six GET /api/settings example values to match AppSettings.toMap() defaults.</subtitle>   <facts>     <fact>Changelog row dated 2026-05-06 for version 0.1.0-beta+42 added to both docs/API.md and docs/API-... [truncated]
```

### real-85480a720e

```text
```xml <observation>   <type>change</type>   <title>Updated database environment variable from DB_HOST to DB_NAME in TESTING docs</title>   <subtitle>Replaced incorrect DB_HOST entry with DB_NAME claude_mem in both testing documentation files.</subtitle>   <facts>     <fact>docs/TESTING.md table row changed from `DB_HOST | 127.0.0.1 | Database host` to `DB_NAME | claude_mem | Database name`</fact>... [truncated]
```

### real-c67e7a5ad3

```text
```xml <observation>   <type>refactor</type>   <title>Simplified conditional in dto.py and ignored memory/.dreams in .gitignore</title>   <subtitle>Removed a redundant else clause from dto.py and added memory/.dreams to .gitignore.</subtitle>   <facts>     <fact>python-sdk/cortex-mem-python/cortex_mem/dto.py had 2 lines removed, specifically an `else:` clause</fact>     <fact>.gitignore now contai... [truncated]
```

### real-bdf72f2509

```text
<skip>removed a comment line with no functional impact</skip>
```

### real-8ac2a32980

```text
<observation>   <type>change</type>   <title>Documented default port 37777 in backend/Dockerfile</title>   <subtitle>Dockerfile comment now states default port 37777; outdated build-arguments sections removed from deployment docs.</subtitle>   <facts>     <fact>backend/Dockerfile comment changed to "# Default port (37777 — can be overridden by SERVER_PORT env var)"</fact>     <fact>docs/DEPLOYMENT... [truncated]
```

### real-9261b542ca

```text
<observation>   <type>discovery</type>   <title>Read patrol-state.json and patrol-rotation.md for last completed patrol round</title>   <subtitle>Investigation-only read of patrol state files; no change applied to the repository.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json records "lastDirection": "go-sdk" as the most recent patrol direction.</fact>     <fact>docs/drafts/patrol-ro... [truncated]
```

### real-e02e436bdd

```text
<observation>   <type>change</type>   <title>Updated prompt example heading in continuation.txt and init.txt</title>   <subtitle>Replaced a specific "Good" example line with a "Category examples" header in both prompt template files.</subtitle>   <facts>     <fact>`backend/src/main/resources/prompts/continuation.txt` replaced the line `- Good: \`Fixed UserCache.computeIfAbsent null placeholder rac... [truncated]
```

### real-0fd4ffcc31

```text
<observation>   <type>bugfix</type>   <title>Fixed skipFp counter check in GoldenSetEvaluator A3 gate</title>   <subtitle>Corrected the A3 must-record-samples gate and replay assertion to check skipFp instead of skipFn.</subtitle>   <facts>     <fact>GoldenSetEvaluator A3 gate now reads agg.skipFp instead of agg.skipFn, checking that record-worthy samples skipped equals 0.</fact>     <fact>GoldenS... [truncated]
```

### real-eee7785ec8

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetEvaluator and eval-model-quality.sh with Anthropic provider support</title>   <subtitle>Added multi-provider support so the golden-set output quality harness can run against Anthropic in addition to OpenAI.</subtitle>   <facts>     <fact>GoldenSetEvaluator.java now imports org.springframework.ai.anthropic.AnthropicChatModel, replacing... [truncated]
```

### real-9a0e90bed5

```text
<observation>   <type>feature</type>   <title>Changed ObservationRepository project filter from projectPath to memoryScopeId</title>   <subtitle>Observation filtering now resolves memoryScopeId instead of projectPath, with new MemoryScopeEntity and MemoryScopeService injected into AgentService.</subtitle>   <facts>     <fact>ObservationRepository.java replaced condition `WHERE (:project IS NULL OR... [truncated]
```

### real-63b5502fb9

```text
<observation>   <type>change</type>   <title>Extended V20 conversation memory scope migration in V20__conversation_memory_scope_expand.sql</title>   <subtitle>V20 conversation/memory scope identity migration was expanded and paired with new rehearsal and conflict fixture scripts.</subtitle>   <facts>     <fact>backend/src/main/resources/db/migration/V20__conversation_memory_scope_expand.sql was ed... [truncated]
```

### real-d33efbd8ae

```text
<observation>   <type>feature</type>   <title>Added migration runner ConversationScopeBackfillRunner</title>   <subtitle>New 845-line Java class in the migration package implements a conversation scope backfill runner.</subtitle>   <facts>     <fact>ConversationScopeBackfillRunner.java was created in backend/src/main/java/com/ablueforce/cortexce/migration/ with 845 inserted lines.</fact>     <fact... [truncated]
```

### real-cd6b972cfd

```text
<observation>   <type>feature</type>   <title>Added SummaryJobService in backend/src/main/java/com/ablueforce/cortexce/service</title>   <subtitle>Created new SummaryJobService class with 326 lines in the service package.</subtitle>   <facts>     <fact>New file backend/src/main/java/com/ablueforce/cortexce/service/SummaryJobService.java created with 326 lines</fact>     <fact>File declares package... [truncated]
```

### real-46e467bff6

```text
<observation>   <type>feature</type>   <title>Added conversation scope migration and preflight scripts in conversation-scope-preflight.sql</title>   <subtitle>New Flyway V20 migration plus preflight and fixture SQL scripts expand conversation/memory scope identity additively.</subtitle>   <facts>     <fact>backend/src/main/resources/db/migration/V20__conversation_memory_scope_expand.sql was added ... [truncated]
```

### real-95de35b2ea

```text
<observation>   <type>change</type>   <title>Documented byte-buddy pin rationale and Docker build context note in backend/pom.xml</title>   <subtitle>Comment-only updates clarify why byte-buddy 1.15.x is pinned and why the Docker build context is the repository root.</subtitle>   <facts>     <fact>backend/pom.xml comment now reads "Pin byte-buddy to 1.15.x: Mockito 5.11 (via spring-boot-starter-te... [truncated]
```

### real-53851b4a23

```text
<observation>   <type>change</type>   <title>Documented health check history archive 11 in docs/archive/2026-10-08_health-check-history-11.md</title>   <subtitle>Created archive of health check rounds 312–359+ covering 2026-10-06 to 2026-10-07, and indexed it in the archive README.</subtitle>   <facts>     <fact>docs/archive/2026-10-08_health-check-history-11.md was created with 1206 lines, titled... [truncated]
```

### real-f4c6765fb9

```text
<observation>   <type>change</type>   <title>Added performance test results draft in docs/drafts/performance-test-results.md</title>   <subtitle>Created a new performance test results document with 34 lines under docs/drafts.</subtitle>   <facts>     <fact>docs/drafts/performance-test-results.md was created with the heading "# Performance Test Results".</fact>     <fact>The edit added 34 lines and... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
