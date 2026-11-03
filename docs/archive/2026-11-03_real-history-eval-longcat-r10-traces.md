golden set: real (60 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: r10-traces
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 60 unique; runs scored: 1; key repeat: 3; real calls: 60 (cap none); duration: 1314864 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 97% |
| skip accuracy | 98% |
|   skip confusion (TP/FP/FN/TN) | 9/0/1/50 |
| format errors | 0 |
| validation rejections | 1 |
| call failures | 0 |
| title exact-hit rate (legacy) | 31% |
| title normalized hit rate (legacy) | 31% |
| title action-class hit rate (A6) | 90% (49 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 35% |
| title subject hit rate (legacy, diagnostic) | 67% |
| title trace-visible subject hit rate (A6b) | 67% (49 scored, 0 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 61% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 61% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 33% |
| title identifier hit rate | 67% |
| type accuracy | 61% |
| facts keyword precision (legacy) / recall | 100% / 100% |
| facts informative precision | 88% |
| facts trace-support rate | 100% |
| concepts precision / recall | 24% / 49% |
| skip consistency across repeats | 0% (0 repeated samples) |
| latency p50 / p95 / max (ms) | 18916 / 59495 / 112680 |
| tokens p50 / p95 / max / mean per call | 4993 / 7082 / 10516 / 5156 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 98% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 1 | PASS |
| A5 | facts informative precision | >= 75% | 88% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 90% | PASS |
| A6b | title trace-visible subject | >= 90% | 67% | **FAIL** |
| A6c | title identifiers | = 100% | 67% | **FAIL** |
| A6 | all three title dimensions together | all pass | 61% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 60 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 60 | 98% | 31% | 61% | 100% | 88% | 100% | 0 |

- average: skip 98%, title 31%, title contract 61%, facts P/R 100%/100%, facts actionable 88%
- worst run: skip 98%, title 31%, title contract 61%, facts P/R 100%/100%, facts actionable 88%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| feature | 9 | 9/9 | 7/9 | 1/9 |
| config-change | 1 | 1/1 | 1/1 | 0/1 |
| refactor | 3 | 3/3 | 0/3 | 1/3 |
| docs-update | 20 | 19/20 | 19/20 | 10/20 |
| test-add | 2 | 2/2 | 2/2 | 1/2 |
| security-fix | 10 | 10/10 | 1/10 | 0/10 |
| bug-fix | 5 | 5/5 | 0/5 | 2/5 |
| diagnose | 10 | 9/10 | 0/10 | 0/10 |

## Failure clusters (first run)

| cluster | misses | share of scored (49) |
|---|---|---|
| identifier missing | 16 | 33% |
| action class wrong | 5 | 10% |
| subject wrong | 16 | 33% |
| type wrong | 19 | 39% |

### identifier missing (16)

- real-1fe4ffb124 — expected identifiers [extraction-quality-improvement-plan.md], title 'Changed test floor min-tests to 472 in scripts/test-baseline.json and verify-local.sh'
- real-d13e2c90cd — expected identifiers [2026-11-03_real-history-golden-eval-minimax.md], title 'Added GoldenSetRealReplayTest with minimax-real-v9 golden set fixtures'
- real-3b4f737562 — expected identifiers [minimax-after-v9.json], title 'Added minimax-after-v9 eval fixture set and quality report'
- real-9e6612fdf9 — expected identifiers [continuation.txt], title 'Extended golden set with extend verb in GoldenSetScorer and GoldenSetActionRules'
- real-e352ba3da3 — expected identifiers [longcat-after-v7.json], title 'Added longcat-after-v7 and minimax-after-v7 eval fixtures and reports'
- real-28edef5a35 — expected identifiers [longcat-after-v6.json], title 'Added longcat-after-v6 and minimax-after-v6 eval fixtures and archive docs'
- real-d3431639a9 — expected identifiers [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md], title 'Added model output quality evaluation reports for minimax and longcat in docs/archive'
- real-7093cb5c56 — expected identifiers [EmbeddingBackfillConcurrencyDatabaseTest], title 'Fixed EmbeddingBackfillService partial batch write failure in EmbeddingBackfillService'
- real-a8cc1754bc — expected identifiers [PendingMessageRepository], title 'Renamed SummaryRepository contentSessionId to conversationId in @Query'
- real-cf667b4d8b — expected identifiers [ObservationFeedbackEntity], title 'Updated feedback ordering Javadoc in ObservationFeedbackRepository'
- real-d958416fdc — expected identifiers [patrol-state.json], title 'Updated patrol rotation to backend and removed /api/mode endpoint in backend/README.md'
- real-85480a720e — expected identifiers [TESTING-zh-CN.md], title 'Updated DB_HOST to DB_NAME in docs/TESTING.md'
- real-8ac2a32980 — expected identifiers [Dockerfile], title 'Removed build arguments sections from DEPLOYMENT.md and DEPLOYMENT-zh-CN.md'
- real-0fd4ffcc31 — expected identifiers [GoldenSetScorer], title 'Fixed GoldenSetReplayTest must-record assertion to check skipFp instead of skipFn'
- real-eee7785ec8 — expected identifiers [2026-11-02_model-output-quality-eval-minimax.md], title 'Extended eval-model-quality.sh with EVAL_PROVIDER=anthropic and AnthropicChatModel'
- real-46e467bff6 — expected identifiers [conversation-scope-preflight.sql], title 'Added conversation memory scope identity expansion in V20__conversation_memory_scope_expand.sql'

### action class wrong (5)

- real-3a2d426fdf — expected [modify], got 'create' from title 'Added promptTokens tracking and EVAL_REPEAT_ALL to GoldenSetScorer'
- real-a8cc1754bc — expected [remove, secure, modify], got 'null' from title 'Renamed SummaryRepository contentSessionId to conversationId in @Query'
- real-9b424d0d02 — expected [remove, document], got 'modify' from title 'Updated GET /api/settings example values in docs/API.md and docs/API-zh-CN.md'
- real-85480a720e — expected [remove, document], got 'modify' from title 'Updated DB_HOST to DB_NAME in docs/TESTING.md'
- real-63b5502fb9 — expected [secure, create, modify], got 'null' from title 'Expanded conversation memory scope identity migration in V20__conversation_memory_scope_expand.sql'

### subject wrong (16)

- real-1fe4ffb124 — expected subject tokens [extraction-quality-improvement-plan.md], title 'Changed test floor min-tests to 472 in scripts/test-baseline.json and verify-local.sh'
- real-d13e2c90cd — expected subject tokens [2026-11-03_real-history-golden-eval-minimax.md], title 'Added GoldenSetRealReplayTest with minimax-real-v9 golden set fixtures'
- real-3b4f737562 — expected subject tokens [minimax-after-v9.json], title 'Added minimax-after-v9 eval fixture set and quality report'
- real-9e6612fdf9 — expected subject tokens [continuation.txt], title 'Extended golden set with extend verb in GoldenSetScorer and GoldenSetActionRules'
- real-e352ba3da3 — expected subject tokens [longcat-after-v7.json], title 'Added longcat-after-v7 and minimax-after-v7 eval fixtures and reports'
- real-28edef5a35 — expected subject tokens [longcat-after-v6.json], title 'Added longcat-after-v6 and minimax-after-v6 eval fixtures and archive docs'
- real-d3431639a9 — expected subject tokens [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md], title 'Added model output quality evaluation reports for minimax and longcat in docs/archive'
- real-7093cb5c56 — expected subject tokens [EmbeddingBackfillConcurrencyDatabaseTest], title 'Fixed EmbeddingBackfillService partial batch write failure in EmbeddingBackfillService'
- real-a8cc1754bc — expected subject tokens [PendingMessageRepository], title 'Renamed SummaryRepository contentSessionId to conversationId in @Query'
- real-cf667b4d8b — expected subject tokens [ObservationFeedbackEntity], title 'Updated feedback ordering Javadoc in ObservationFeedbackRepository'
- real-d958416fdc — expected subject tokens [patrol-state.json], title 'Updated patrol rotation to backend and removed /api/mode endpoint in backend/README.md'
- real-85480a720e — expected subject tokens [TESTING-zh-CN.md], title 'Updated DB_HOST to DB_NAME in docs/TESTING.md'
- real-8ac2a32980 — expected subject tokens [Dockerfile], title 'Removed build arguments sections from DEPLOYMENT.md and DEPLOYMENT-zh-CN.md'
- real-0fd4ffcc31 — expected subject tokens [GoldenSetScorer], title 'Fixed GoldenSetReplayTest must-record assertion to check skipFp instead of skipFn'
- real-eee7785ec8 — expected subject tokens [2026-11-02_model-output-quality-eval-minimax.md], title 'Extended eval-model-quality.sh with EVAL_PROVIDER=anthropic and AnthropicChatModel'
- real-46e467bff6 — expected subject tokens [conversation-scope-preflight.sql], title 'Added conversation memory scope identity expansion in V20__conversation_memory_scope_expand.sql'

### type wrong (19)

- real-f6a1b89e41 — expected 'change', got 'feature' (title 'Updated golden-tool-use-real.json to real-git-2026-11-03-r10 and extended GoldenSetScorer evidence')
- real-77ba40df77 — expected 'refactor', got 'feature' (title 'Added ExtractionConsistency voting service in ExtractionConsistency.java')
- real-f0d346eab1 — expected 'change', got 'feature' (title 'Added golden-tool-use-real.json dataset and GoldenSetRealDataTest for real-history evaluation')
- real-3a2d426fdf — expected 'refactor', got 'feature' (title 'Added promptTokens tracking and EVAL_REPEAT_ALL to GoldenSetScorer')
- real-9e6612fdf9 — expected 'bugfix', got 'feature' (title 'Extended golden set with extend verb in GoldenSetScorer and GoldenSetActionRules')
- real-f3d12860ff — expected 'bugfix', got 'change' (title 'Refactored LogsControllerTest to use setField helper for logDir injection')
- real-f7b865efe5 — expected 'bugfix', got 'change' (title 'Changed header background color --color-bg-header to #f8f6f2 in index.html')
- real-a8cc1754bc — expected 'bugfix', got 'refactor' (title 'Renamed SummaryRepository contentSessionId to conversationId in @Query')
- real-e2c887775a — expected 'bugfix', got 'change' (title 'Corrected `/api/context/generate` endpoint reference to `/api/context/inject` in AGENTS.md')
- real-cf667b4d8b — expected 'refactor', got 'change' (title 'Updated feedback ordering Javadoc in ObservationFeedbackRepository')
- real-c67e7a5ad3 — expected 'bugfix', got 'change' (title 'Removed redundant else clause in dto.py and added memory/.dreams to .gitignore')
- real-bdf72f2509 — expected 'bugfix', got 'change' (title 'Removed SDK version reminder comment in CortexMemClientImpl.java')
- real-8ac2a32980 — expected 'bugfix', got 'change' (title 'Removed build arguments sections from DEPLOYMENT.md and DEPLOYMENT-zh-CN.md')
- real-9a0e90bed5 — expected 'bugfix', got 'feature' (title 'Added MemoryScopeEntity and refactored ObservationRepository to filter by memoryScopeId')
- real-63b5502fb9 — expected 'bugfix', got 'feature' (title 'Expanded conversation memory scope identity migration in V20__conversation_memory_scope_expand.sql')
- real-d33efbd8ae — expected 'bugfix', got 'feature' (title 'Added ConversationScopeBackfillRunner and TechnicalBucketClassifier migration tooling')
- real-cd6b972cfd — expected 'bugfix', got 'feature' (title 'Added SummaryJobService in backend service package')
- real-46e467bff6 — expected 'bugfix', got 'feature' (title 'Added conversation memory scope identity expansion in V20__conversation_memory_scope_expand.sql')
- real-95de35b2ea — expected 'bugfix', got 'change' (title 'Updated byte-buddy 1.15.x pin rationale in backend/pom.xml')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| real-f6a1b89e41 | feature | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 37007 |
| real-1fe4ffb124 | config-change | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 22224 |
| real-d13e2c90cd | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 32370 |
| real-3b4f737562 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 23956 |
| real-77ba40df77 | refactor | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 18916 |
| real-f0d346eab1 | feature | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/100% | 31233 |
| real-3a2d426fdf | refactor | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 67% | 50%/100% | 14057 |
| real-864dadf51b | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 59495 |
| real-b064308244 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 27571 |
| real-30a956e462 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 12906 |
| real-ba86bf2588 | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/100% | 14079 |
| real-a9c8452967 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 17775 |
| real-db8ac0287e | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 33% | 0%/0% | 15132 |
| real-9e6612fdf9 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 67% | 0%/0% | 58140 |
| real-f43cfd6f44 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 19975 |
| real-e352ba3da3 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 27610 |
| real-112de33e23 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 20660 |
| real-28edef5a35 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 22049 |
| real-6d6df2c321 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 80% | 0%/0% | 18655 |
| real-d3431639a9 | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 19862 |
| real-7093cb5c56 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 28870 |
| real-f3d12860ff | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 28821 |
| real-3409dda60c | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 11211 |
| real-f7b865efe5 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 19325 |
| real-a8cc1754bc | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | MISS | 100%/100% | 0% | 0%/0% | 112680 |
| real-e2c887775a | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 21883 |
| real-cf667b4d8b | refactor | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 0% | 0%/0% | 19701 |
| real-a0e77ca4ee | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 80% | 0%/0% | 23620 |
| real-d958416fdc | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 21215 |
| real-9b424d0d02 | docs-update | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 0%/0% | 27081 |
| real-85480a720e | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | match | 100%/100% | 100% | 0%/0% | 15866 |
| real-c67e7a5ad3 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 10571 |
| real-bdf72f2509 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 20622 |
| real-8ac2a32980 | bug-fix | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 0%/0% | 31460 |
| real-2025b33a95 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3298 |
| real-e8c8b314d6 | diagnose | skip | observation | FAIL | 0% | MISS (subject unreachable, id MISS, action MISS) | miss | MISS | 0%/0% | 0% | 0%/0% | 7740 |
| real-c42d7a9c5d | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2688 |
| real-b5ae725e2a | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3438 |
| real-9261b542ca | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3835 |
| real-7c557c8d79 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2393 |
| real-1dbccd058e | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2395 |
| real-1f9b5d09e7 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3514 |
| real-ed737be6bb | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3549 |
| real-b2d48b12a1 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2805 |
| real-bc3512ae8d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 14833 |
| real-e02e436bdd | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 8194 |
| real-d44a83721d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 16634 |
| real-0fd4ffcc31 | test-add | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 26343 |
| real-eee7785ec8 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 27575 |
| real-9a0e90bed5 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 14985 |
| real-63b5502fb9 | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 0%/0% | 19544 |
| real-d33efbd8ae | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 20626 |
| real-cd6b972cfd | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 33% | 0%/0% | 10403 |
| real-46e467bff6 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 0%/0% | 15145 |
| real-325c817bbf | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/100% | 64895 |
| real-7149578a88 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 12933 |
| real-95de35b2ea | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 80062 |
| real-53851b4a23 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 18013 |
| real-f4c6765fb9 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/100% | 8714 |
| real-67083a7bc1 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 12980 |

## Problems

- real-f6a1b89e41: title coverage 50% (expected keywords [change, golden-tool-use-real.json])
- real-f6a1b89e41: type 'feature' != expected 'change'
- real-1fe4ffb124: title lost identifiers [extraction-quality-improvement-plan.md] (got 'Changed test floor min-tests to 472 in scripts/test-baseline.json and verify-local.sh')
- real-1fe4ffb124: title verb class 'document' missing (got 'Changed test floor min-tests to 472 in scripts/test-baseline.json and verify-local.sh')
- real-1fe4ffb124: title subject [extraction-quality-improvement-plan.md] missing (got 'Changed test floor min-tests to 472 in scripts/test-baseline.json and verify-local.sh')
- real-1fe4ffb124: title trace-visible subject [extraction-quality-improvement-plan.md] missing (got 'Changed test floor min-tests to 472 in scripts/test-baseline.json and verify-local.sh')
- real-d13e2c90cd: title lost identifiers [2026-11-03_real-history-golden-eval-minimax.md] (got 'Added GoldenSetRealReplayTest with minimax-real-v9 golden set fixtures')
- real-d13e2c90cd: title verb class 'document' missing (got 'Added GoldenSetRealReplayTest with minimax-real-v9 golden set fixtures')
- real-d13e2c90cd: title subject [2026-11-03_real-history-golden-eval-minimax.md] missing (got 'Added GoldenSetRealReplayTest with minimax-real-v9 golden set fixtures')
- real-d13e2c90cd: title trace-visible subject [2026-11-03_real-history-golden-eval-minimax.md] missing (got 'Added GoldenSetRealReplayTest with minimax-real-v9 golden set fixtures')
- real-3b4f737562: title lost identifiers [minimax-after-v9.json] (got 'Added minimax-after-v9 eval fixture set and quality report')
- real-3b4f737562: title verb class 'document' missing (got 'Added minimax-after-v9 eval fixture set and quality report')
- real-3b4f737562: title subject [minimax-after-v9.json] missing (got 'Added minimax-after-v9 eval fixture set and quality report')
- real-3b4f737562: title trace-visible subject [minimax-after-v9.json] missing (got 'Added minimax-after-v9 eval fixture set and quality report')
- real-77ba40df77: type 'feature' != expected 'refactor'
- real-77ba40df77: concepts recall 0% (expected [what-changed], got [pattern, trade-off])
- real-f0d346eab1: type 'feature' != expected 'change'
- real-3a2d426fdf: title coverage 50% (expected keywords [change, goldensetscorer])
- real-3a2d426fdf: title action [modify] missing: title leads 'create' (got 'Added promptTokens tracking and EVAL_REPEAT_ALL to GoldenSetScorer')
- real-3a2d426fdf: title verb class 'change' missing (got 'Added promptTokens tracking and EVAL_REPEAT_ALL to GoldenSetScorer')
- real-3a2d426fdf: type 'feature' != expected 'refactor'
- real-864dadf51b: title coverage 50% (expected keywords [document, goldensetscorer])
- real-864dadf51b: title verb class 'document' missing (got 'Extended GoldenSetScorer evidence extraction beyond toolName')
- real-b064308244: title coverage 50% (expected keywords [document, extraction-quality-improvement-plan.md])
- real-b064308244: title verb class 'document' missing (got 'Updated rehearsal database gate wording in docs/drafts/extraction-quality-improvement-plan.md')
- real-30a956e462: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a9c8452967: title coverage 50% (expected keywords [document, searchsimilarityevaluator])
- real-a9c8452967: title verb class 'document' missing (got 'Added SearchSimilarityEvaluator BGE-M3 replay harness and corpus')
- real-db8ac0287e: title coverage 50% (expected keywords [document, continuation.txt])
- real-db8ac0287e: title verb class 'document' missing (got 'Updated Fixed verb definition in continuation.txt and init.txt')
- real-db8ac0287e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9e6612fdf9: title lost identifiers [continuation.txt] (got 'Extended golden set with extend verb in GoldenSetScorer and GoldenSetActionRules')
- real-9e6612fdf9: title verb class 'document' missing (got 'Extended golden set with extend verb in GoldenSetScorer and GoldenSetActionRules')
- real-9e6612fdf9: title subject [continuation.txt] missing (got 'Extended golden set with extend verb in GoldenSetScorer and GoldenSetActionRules')
- real-9e6612fdf9: title trace-visible subject [continuation.txt] missing (got 'Extended golden set with extend verb in GoldenSetScorer and GoldenSetActionRules')
- real-9e6612fdf9: type 'feature' != expected 'bugfix'
- real-9e6612fdf9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e352ba3da3: title lost identifiers [longcat-after-v7.json] (got 'Added longcat-after-v7 and minimax-after-v7 eval fixtures and reports')
- real-e352ba3da3: title verb class 'document' missing (got 'Added longcat-after-v7 and minimax-after-v7 eval fixtures and reports')
- real-e352ba3da3: title subject [longcat-after-v7.json] missing (got 'Added longcat-after-v7 and minimax-after-v7 eval fixtures and reports')
- real-e352ba3da3: title trace-visible subject [longcat-after-v7.json] missing (got 'Added longcat-after-v7 and minimax-after-v7 eval fixtures and reports')
- real-112de33e23: title coverage 50% (expected keywords [document, continuation.txt])
- real-112de33e23: title verb class 'document' missing (got 'Updated continuation.txt and init.txt prompt templates with replacement naming rule')
- real-28edef5a35: title lost identifiers [longcat-after-v6.json] (got 'Added longcat-after-v6 and minimax-after-v6 eval fixtures and archive docs')
- real-28edef5a35: title verb class 'document' missing (got 'Added longcat-after-v6 and minimax-after-v6 eval fixtures and archive docs')
- real-28edef5a35: title subject [longcat-after-v6.json] missing (got 'Added longcat-after-v6 and minimax-after-v6 eval fixtures and archive docs')
- real-28edef5a35: title trace-visible subject [longcat-after-v6.json] missing (got 'Added longcat-after-v6 and minimax-after-v6 eval fixtures and archive docs')
- real-6d6df2c321: title coverage 50% (expected keywords [document, continuation.txt])
- real-6d6df2c321: title verb class 'document' missing (got 'Extended skip guidance rules in prompts/continuation.txt and prompts/init.txt')
- real-6d6df2c321: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d3431639a9: title lost identifiers [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] (got 'Added model output quality evaluation reports for minimax and longcat in docs/archive')
- real-d3431639a9: title verb class 'document' missing (got 'Added model output quality evaluation reports for minimax and longcat in docs/archive')
- real-d3431639a9: title subject [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] missing (got 'Added model output quality evaluation reports for minimax and longcat in docs/archive')
- real-d3431639a9: title trace-visible subject [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] missing (got 'Added model output quality evaluation reports for minimax and longcat in docs/archive')
- real-d3431639a9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-7093cb5c56: title lost identifiers [EmbeddingBackfillConcurrencyDatabaseTest] (got 'Fixed EmbeddingBackfillService partial batch write failure in EmbeddingBackfillService')
- real-7093cb5c56: title verb class 'harden' missing (got 'Fixed EmbeddingBackfillService partial batch write failure in EmbeddingBackfillService')
- real-7093cb5c56: title subject [EmbeddingBackfillConcurrencyDatabaseTest] missing (got 'Fixed EmbeddingBackfillService partial batch write failure in EmbeddingBackfillService')
- real-7093cb5c56: title trace-visible subject [EmbeddingBackfillConcurrencyDatabaseTest] missing (got 'Fixed EmbeddingBackfillService partial batch write failure in EmbeddingBackfillService')
- real-f3d12860ff: title coverage 50% (expected keywords [harden, logscontrollertest])
- real-f3d12860ff: title verb class 'harden' missing (got 'Refactored LogsControllerTest to use setField helper for logDir injection')
- real-f3d12860ff: type 'change' != expected 'bugfix'
- real-f3d12860ff: concepts recall 0% (expected [problem-solution], got [pattern, what-changed])
- real-3409dda60c: validation rejected the observation: title too long: 103 chars > 100
- real-f7b865efe5: title coverage 50% (expected keywords [harden, index.html])
- real-f7b865efe5: title verb class 'harden' missing (got 'Changed header background color --color-bg-header to #f8f6f2 in index.html')
- real-f7b865efe5: type 'change' != expected 'bugfix'
- real-f7b865efe5: concepts recall 0% (expected [problem-solution], got [what-changed, what-changed])
- real-a8cc1754bc: title lost identifiers [PendingMessageRepository] (got 'Renamed SummaryRepository contentSessionId to conversationId in @Query')
- real-a8cc1754bc: title action [remove, secure, modify] missing: title leads 'null' (got 'Renamed SummaryRepository contentSessionId to conversationId in @Query')
- real-a8cc1754bc: title verb class 'remove' missing (got 'Renamed SummaryRepository contentSessionId to conversationId in @Query')
- real-a8cc1754bc: title subject [PendingMessageRepository] missing (got 'Renamed SummaryRepository contentSessionId to conversationId in @Query')
- real-a8cc1754bc: title trace-visible subject [PendingMessageRepository] missing (got 'Renamed SummaryRepository contentSessionId to conversationId in @Query')
- real-a8cc1754bc: type 'refactor' != expected 'bugfix'
- real-a8cc1754bc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e2c887775a: title coverage 50% (expected keywords [remove, agents.md])
- real-e2c887775a: title verb class 'remove' missing (got 'Corrected `/api/context/generate` endpoint reference to `/api/context/inject` in AGENTS.md')
- real-e2c887775a: type 'change' != expected 'bugfix'
- real-cf667b4d8b: title lost identifiers [ObservationFeedbackEntity] (got 'Updated feedback ordering Javadoc in ObservationFeedbackRepository')
- real-cf667b4d8b: title verb class 'remove' missing (got 'Updated feedback ordering Javadoc in ObservationFeedbackRepository')
- real-cf667b4d8b: title subject [ObservationFeedbackEntity] missing (got 'Updated feedback ordering Javadoc in ObservationFeedbackRepository')
- real-cf667b4d8b: title trace-visible subject [ObservationFeedbackEntity] missing (got 'Updated feedback ordering Javadoc in ObservationFeedbackRepository')
- real-cf667b4d8b: type 'change' != expected 'refactor'
- real-cf667b4d8b: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a0e77ca4ee: title verb class 'remove' missing (got 'Updated TESTING.md and TESTING-zh-CN.md to remove drifted line-count column')
- real-a0e77ca4ee: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d958416fdc: title lost identifiers [patrol-state.json] (got 'Updated patrol rotation to backend and removed /api/mode endpoint in backend/README.md')
- real-d958416fdc: title verb class 'remove' missing (got 'Updated patrol rotation to backend and removed /api/mode endpoint in backend/README.md')
- real-d958416fdc: title subject [patrol-state.json] missing (got 'Updated patrol rotation to backend and removed /api/mode endpoint in backend/README.md')
- real-d958416fdc: title trace-visible subject [patrol-state.json] missing (got 'Updated patrol rotation to backend and removed /api/mode endpoint in backend/README.md')
- real-d958416fdc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9b424d0d02: title coverage 50% (expected keywords [remove, api-zh-cn.md])
- real-9b424d0d02: title action [remove, document] missing: title leads 'modify' (got 'Updated GET /api/settings example values in docs/API.md and docs/API-zh-CN.md')
- real-9b424d0d02: title verb class 'remove' missing (got 'Updated GET /api/settings example values in docs/API.md and docs/API-zh-CN.md')
- real-9b424d0d02: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-85480a720e: title lost identifiers [TESTING-zh-CN.md] (got 'Updated DB_HOST to DB_NAME in docs/TESTING.md')
- real-85480a720e: title action [remove, document] missing: title leads 'modify' (got 'Updated DB_HOST to DB_NAME in docs/TESTING.md')
- real-85480a720e: title verb class 'remove' missing (got 'Updated DB_HOST to DB_NAME in docs/TESTING.md')
- real-85480a720e: title subject [TESTING-zh-CN.md] missing (got 'Updated DB_HOST to DB_NAME in docs/TESTING.md')
- real-85480a720e: title trace-visible subject [TESTING-zh-CN.md] missing (got 'Updated DB_HOST to DB_NAME in docs/TESTING.md')
- real-85480a720e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-c67e7a5ad3: type 'change' != expected 'bugfix'
- real-c67e7a5ad3: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-bdf72f2509: type 'change' != expected 'bugfix'
- real-bdf72f2509: concepts recall 0% (expected [problem-solution], got [what-changed, what-changed])
- real-8ac2a32980: title lost identifiers [Dockerfile] (got 'Removed build arguments sections from DEPLOYMENT.md and DEPLOYMENT-zh-CN.md')
- real-8ac2a32980: title subject [Dockerfile] missing (got 'Removed build arguments sections from DEPLOYMENT.md and DEPLOYMENT-zh-CN.md')
- real-8ac2a32980: title trace-visible subject [Dockerfile] missing (got 'Removed build arguments sections from DEPLOYMENT.md and DEPLOYMENT-zh-CN.md')
- real-8ac2a32980: type 'change' != expected 'bugfix'
- real-8ac2a32980: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e8c8b314d6: expected <skip> but produced an observation
- real-e02e436bdd: title coverage 50% (expected keywords [document, continuation.txt])
- real-e02e436bdd: title verb class 'document' missing (got 'Updated example formatting in prompt templates continuation.txt and init.txt')
- real-0fd4ffcc31: title lost identifiers [GoldenSetScorer] (got 'Fixed GoldenSetReplayTest must-record assertion to check skipFp instead of skipFn')
- real-0fd4ffcc31: title verb class 'change' missing (got 'Fixed GoldenSetReplayTest must-record assertion to check skipFp instead of skipFn')
- real-0fd4ffcc31: title subject [GoldenSetScorer] missing (got 'Fixed GoldenSetReplayTest must-record assertion to check skipFp instead of skipFn')
- real-0fd4ffcc31: title trace-visible subject [GoldenSetScorer] missing (got 'Fixed GoldenSetReplayTest must-record assertion to check skipFp instead of skipFn')
- real-0fd4ffcc31: concepts recall 0% (expected [what-changed], got [problem-solution, gotcha])
- real-eee7785ec8: title lost identifiers [2026-11-02_model-output-quality-eval-minimax.md] (got 'Extended eval-model-quality.sh with EVAL_PROVIDER=anthropic and AnthropicChatModel')
- real-eee7785ec8: title verb class 'document' missing (got 'Extended eval-model-quality.sh with EVAL_PROVIDER=anthropic and AnthropicChatModel')
- real-eee7785ec8: title subject [2026-11-02_model-output-quality-eval-minimax.md] missing (got 'Extended eval-model-quality.sh with EVAL_PROVIDER=anthropic and AnthropicChatModel')
- real-eee7785ec8: title trace-visible subject [2026-11-02_model-output-quality-eval-minimax.md] missing (got 'Extended eval-model-quality.sh with EVAL_PROVIDER=anthropic and AnthropicChatModel')
- real-9a0e90bed5: title coverage 50% (expected keywords [harden, observationrepository])
- real-9a0e90bed5: title verb class 'harden' missing (got 'Added MemoryScopeEntity and refactored ObservationRepository to filter by memoryScopeId')
- real-9a0e90bed5: type 'feature' != expected 'bugfix'
- real-9a0e90bed5: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-63b5502fb9: title coverage 50% (expected keywords [harden, v20__conversation_memory_scope_expand.sql])
- real-63b5502fb9: title action [secure, create, modify] missing: title leads 'null' (got 'Expanded conversation memory scope identity migration in V20__conversation_memory_scope_expand.sql')
- real-63b5502fb9: title verb class 'harden' missing (got 'Expanded conversation memory scope identity migration in V20__conversation_memory_scope_expand.sql')
- real-63b5502fb9: type 'feature' != expected 'bugfix'
- real-63b5502fb9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d33efbd8ae: title coverage 50% (expected keywords [harden, conversationscopebackfillrunner])
- real-d33efbd8ae: title verb class 'harden' missing (got 'Added ConversationScopeBackfillRunner and TechnicalBucketClassifier migration tooling')
- real-d33efbd8ae: type 'feature' != expected 'bugfix'
- real-d33efbd8ae: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-cd6b972cfd: title coverage 50% (expected keywords [harden, summaryjobservice])
- real-cd6b972cfd: title verb class 'harden' missing (got 'Added SummaryJobService in backend service package')
- real-cd6b972cfd: type 'feature' != expected 'bugfix'
- real-cd6b972cfd: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-46e467bff6: title lost identifiers [conversation-scope-preflight.sql] (got 'Added conversation memory scope identity expansion in V20__conversation_memory_scope_expand.sql')
- real-46e467bff6: title verb class 'harden' missing (got 'Added conversation memory scope identity expansion in V20__conversation_memory_scope_expand.sql')
- real-46e467bff6: title subject [conversation-scope-preflight.sql] missing (got 'Added conversation memory scope identity expansion in V20__conversation_memory_scope_expand.sql')
- real-46e467bff6: title trace-visible subject [conversation-scope-preflight.sql] missing (got 'Added conversation memory scope identity expansion in V20__conversation_memory_scope_expand.sql')
- real-46e467bff6: type 'feature' != expected 'bugfix'
- real-46e467bff6: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-95de35b2ea: title coverage 50% (expected keywords [change, pom.xml])
- real-95de35b2ea: type 'change' != expected 'bugfix'
- real-95de35b2ea: concepts recall 0% (expected [problem-solution], got [what-changed, trade-off])
- real-53851b4a23: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])

## Raw answers behind problems

### real-f6a1b89e41

```text
<observation>   <type>feature</type>   <title>Updated golden-tool-use-real.json to real-git-2026-11-03-r10 and extended GoldenSetScorer evidence</title>   <subtitle>Round-10 golden tool-use eval set regenerated with 60-sample minimum and expanded failure-cluster evidence in the scorer.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json version changed from "re... [truncated]
```

### real-1fe4ffb124

```text
<observation>   <type>change</type>   <title>Changed test floor min-tests to 472 in scripts/test-baseline.json and verify-local.sh</title>   <subtitle>Raised the hard minimum test-count floor from 423 to 472 across baseline, verify script, CI, and docs, and added a Round 9 attribution section to the improvement plan.</subtitle>   <facts>     <fact>scripts/test-baseline.json sets "total" to 472, ra... [truncated]
```

### real-d13e2c90cd

```text
<observation>   <type>feature</type>   <title>Added GoldenSetRealReplayTest with minimax-real-v9 golden set fixtures</title>   <subtitle>New real-history golden set replay evaluation for minimax with fixtures, metrics, test, and archive docs.</subtitle>   <facts>     <fact>GoldenSetRealReplayTest.java was added at backend/src/test/java/com/ablueforce/cortexce/eval/ with 87 lines.</fact>     <fact>... [truncated]
```

### real-3b4f737562

```text
<observation>   <type>feature</type>   <title>Added minimax-after-v9 eval fixture set and quality report</title>   <subtitle>New golden-set fixture, metrics file, and archived quality evaluation report added for the minimax-after-v9 model run.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/minimax-after-v9.json was added with 2096 lines.</fact>     <fact>backend/src/test/r... [truncated]
```

### real-77ba40df77

```text
<observation>   <type>feature</type>   <title>Added ExtractionConsistency voting service in ExtractionConsistency.java</title>   <subtitle>Introduced a new extraction consistency layer that runs multiple LLM calls per tool trace and applies majority voting.</subtitle>   <facts>     <fact>New file backend/src/main/java/com/ablueforce/cortexce/service/ExtractionConsistency.java adds 82 lines impleme... [truncated]
```

### real-f0d346eab1

```text
<observation>   <type>feature</type>   <title>Added golden-tool-use-real.json dataset and GoldenSetRealDataTest for real-history evaluation</title>   <subtitle>New real-history golden tool-use dataset, generator, and test class wired into GoldenSetEvaluator via EVAL_GOLDEN.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json adds 1097 lines of real-history gold... [truncated]
```

### real-3a2d426fdf

```text
<observation>   <type>feature</type>   <title>Added promptTokens tracking and EVAL_REPEAT_ALL to GoldenSetScorer</title>   <subtitle>Golden set evaluator now records prompt token counts and supports repeating every sample for run-to-run variance measurement.</subtitle>   <facts>     <fact>GoldenSetScorer.java gained a `public final int promptTokens;` field to capture per-sample prompt token usage.... [truncated]
```

### real-864dadf51b

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetScorer evidence extraction beyond toolName</title>   <subtitle>GoldenSetScorer evidence assembly replaced a toolName-only string with a larger structured block.</subtitle>   <facts>     <fact>GoldenSetScorer.java replaced `String evidence = (sample.toolName == null ? "" : sample.toolName) + "\n"` with a 70-line block beginning `/**`.<... [truncated]
```

### real-b064308244

```text
<observation>   <type>change</type>   <title>Updated rehearsal database gate wording in docs/drafts/extraction-quality-improvement-plan.md</title>   <subtitle>Replaced the "L4 layer" description of the offline-model hook test with "rehearsal database gate" in the draft plan.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md now says the offline-model stand-in hook t... [truncated]
```

### real-30a956e462

```text
<observation>   <type>change</type>   <title>Documented Round 8 insertion-scope contract in docs/drafts/extraction-quality-improvement-plan.md</title>   <subtitle>Added section 7.14 covering insertion-scope contract, coverage removal, and a reverted security verb.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md gained 139 inserted lines with 0 deletions.</fact>   ... [truncated]
```

### real-a9c8452967

```text
<observation>   <type>feature</type>   <title>Added SearchSimilarityEvaluator BGE-M3 replay harness and corpus</title>   <subtitle>New semantic relevance evaluation harness with replay test, BGE-M3 fixtures, metrics, and archive documentation.</subtitle>   <facts>     <fact>Created SearchSimilarityEvaluator.java (411 lines) in backend/src/test/java/com/ablueforce/cortexce/eval/.</fact>     <fact>C... [truncated]
```

### real-db8ac0287e

```text
```xml <observation>   <type>change</type>   <title>Updated Fixed verb definition in continuation.txt and init.txt</title>   <subtitle>Removed the non-security qualifier from the Fixed classification rule so security defect fixes also use that verb.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt changed the rule line "- Fixed: a defect already produced wrong or ... [truncated]
```

### real-9e6612fdf9

```text
<observation>   <type>feature</type>   <title>Extended golden set with extend verb in GoldenSetScorer and GoldenSetActionRules</title>   <subtitle>Added extend as a recognized verb variant and narrowed the Fixed verb definition to exclude security code.</subtitle>   <facts>     <fact>The "Fixed" verb definition in `continuation.txt` and `init.txt` was changed from "a defect already produced wrong ... [truncated]
```

### real-e352ba3da3

```text
<observation>   <type>feature</type>   <title>Added longcat-after-v7 and minimax-after-v7 eval fixtures and reports</title>   <subtitle>New model output quality evaluation fixtures and archived reports added for longcat and minimax after v7.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v7.json and backend/src/test/resources/eval/fixtures/minimax-after-v7.js... [truncated]
```

### real-112de33e23

```text
<observation>   <type>change</type>   <title>Updated continuation.txt and init.txt prompt templates with replacement naming rule</title>   <subtitle>Both prompt resource files gained the same three-line instruction requiring replacements to keep both names.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt was edited with 3 line(s) inserted and 0 deleted.</fact>   ... [truncated]
```

### real-28edef5a35

```text
```xml <observation>   <type>feature</type>   <title>Added longcat-after-v6 and minimax-after-v6 eval fixtures and archive docs</title>   <subtitle>Six new files add model output quality evaluation fixtures, metrics, and archived reports for longcat and minimax after v6.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v6.json was created with 656 lines.</fact>... [truncated]
```

### real-6d6df2c321

```text
<observation>   <type>change</type>   <title>Extended skip guidance rules in prompts/continuation.txt and prompts/init.txt</title>   <subtitle>Expanded memory-agent skip criteria across both prompt files, replacing a single line with detailed guidance.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt removed 11 line(s) and added 71 line(s).</fact>     <fact>backen... [truncated]
```

### real-d3431639a9

```text
<observation>   <type>change</type>   <title>Added model output quality evaluation reports for minimax and longcat in docs/archive</title>   <subtitle>Five documentation files created covering model output quality evals, summary chain evals, and an extraction quality improvement plan.</subtitle>   <facts>     <fact>docs/archive/2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md was ... [truncated]
```

### real-7093cb5c56

```text
<observation>   <type>bugfix</type>   <title>Fixed EmbeddingBackfillService partial batch write failure in EmbeddingBackfillService</title>   <subtitle>Embedding backfill now saves batches atomically and is covered by new concurrency and service tests.</subtitle>   <facts>     <fact>EmbeddingBackfillService.java removed the comment stating each row is saved on its own and a failure on one row cann... [truncated]
```

### real-f3d12860ff

```text
<observation>   <type>change</type>   <title>Refactored LogsControllerTest to use setField helper for logDir injection</title>   <subtitle>Test reflection boilerplate replaced with setField helper; LogsController gained Javadoc for the lines parameter.</subtitle>   <facts>     <fact>LogsControllerTest replaced `Field field = LogsController.class.getDeclaredField("logDir")` with `setField("logDir",... [truncated]
```

### real-3409dda60c

```text
<observation>   <type>change</type>   <title>Documented implementation commit range `325c817..f7b865e` in conversation-memory-scope-refactor-plan.md</title>   <subtitle>Updated the refactor plan's commit range field from "待实施" to the 9-commit range with an appended list.</subtitle>   <facts>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md field `实施 commit 范围` changed from `待实施` to... [truncated]
```

### real-f7b865efe5

```text
<observation>   <type>change</type>   <title>Changed header background color --color-bg-header to #f8f6f2 in index.html</title>   <subtitle>Updated the header background CSS variable from a cool gray to a warm off-white tone.</subtitle>   <facts>     <fact>In backend/src/main/resources/static/index.html, the CSS variable --color-bg-header was changed from #f6f8fa to #f8f6f2.</fact>     <fact>The e... [truncated]
```

### real-a8cc1754bc

```text
<observation>   <type>refactor</type>   <title>Renamed SummaryRepository contentSessionId to conversationId in @Query</title>   <subtitle>SummaryRepository query now references conversationId instead of contentSessionId.</subtitle>   <facts>     <fact>SummaryRepository @Query changed from `s.contentSessionId = :contentSessionId` to `s.conversationId = :conversationId`</fact>     <fact>The ORDER BY... [truncated]
```

### real-e2c887775a

```text
```xml <observation>   <type>change</type>   <title>Corrected `/api/context/generate` endpoint reference to `/api/context/inject` in AGENTS.md</title>   <subtitle>AGENTS.md hard-contract table no longer ties the `updateFiles` field to an endpoint that never returns it.</subtitle>   <facts>     <fact>AGENTS.md now pairs the endpoint `/api/context/inject` with `updateFiles` and `proxy/proxy.js` in t... [truncated]
```

### real-cf667b4d8b

```text
<observation>   <type>change</type>   <title>Updated feedback ordering Javadoc in ObservationFeedbackRepository</title>   <subtitle>Repository method comment now states feedback is returned most recent first rather than ordered by creation time.</subtitle>   <facts>     <fact>ObservationFeedbackRepository.java Javadoc changed from "ordered by creation time" to "most recent first".</fact>     <fact... [truncated]
```

### real-a0e77ca4ee

```text
<observation>   <type>change</type>   <title>Updated TESTING.md and TESTING-zh-CN.md to remove drifted line-count column</title>   <subtitle>Doc-review patrol stripped the drifting Lines column and a duplicated table header from testing docs, syncing English and Chinese.</subtitle>   <facts>     <fact>docs/TESTING.md table header changed from "| Script | Description | Lines |" to "| Script | Descr... [truncated]
```

### real-d958416fdc

```text
<observation>   <type>change</type>   <title>Updated patrol rotation to backend and removed /api/mode endpoint in backend/README.md</title>   <subtitle>Documentation refresh marks backend review complete, drops the /api/mode endpoint row, and updates architecture endpoint tables.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json sets `lastDirection` to `backend`, replacing the previous ... [truncated]
```

### real-9b424d0d02

```text
<observation>   <type>change</type>   <title>Updated GET /api/settings example values in docs/API.md and docs/API-zh-CN.md</title>   <subtitle>Corrected 6 example values in API documentation to match actual AppSettings.toMap() defaults.</subtitle>   <facts>     <fact>GET /api/settings example PROVIDER default corrected from openai to claude in both docs/API.md and docs/API-zh-CN.md.</fact>     <fa... [truncated]
```

### real-85480a720e

```text
<observation>   <type>change</type>   <title>Updated DB_HOST to DB_NAME in docs/TESTING.md</title>   <subtitle>Environment variable documentation now references DB_NAME (claude_mem) instead of DB_HOST (127.0.0.1) in both language versions.</subtitle>   <facts>     <fact>docs/TESTING.md replaces `DB_HOST` (value `127.0.0.1`) with `DB_NAME` (value `claude_mem`) in the environment variable table.</fa... [truncated]
```

### real-c67e7a5ad3

```text
<observation>   <type>change</type>   <title>Removed redundant else clause in dto.py and added memory/.dreams to .gitignore</title>   <subtitle>Cleaned up conditional structure in DTO module and ignored dream memory directory.</subtitle>   <facts>     <fact>python-sdk/cortex-mem-python/cortex_mem/dto.py removed 2 lines containing an else clause</fact>     <fact>.gitignore added 'memory/.dreams' en... [truncated]
```

### real-bdf72f2509

```text
<observation>   <type>change</type>   <title>Removed SDK version reminder comment in CortexMemClientImpl.java</title>   <subtitle>Javadoc comment about updating SDK version for User-Agent header was deleted from the client implementation file.</subtitle>   <facts>     <fact>The comment `/** SDK version for User-Agent header. Update when releasing new SDK versions. */` was removed from CortexMemCli... [truncated]
```

### real-8ac2a32980

```text
<observation>   <type>change</type>   <title>Removed build arguments sections from DEPLOYMENT.md and DEPLOYMENT-zh-CN.md</title>   <subtitle>Removed obsolete build argument documentation and updated Dockerfile default port comment to 37777.</subtitle>   <facts>     <fact>backend/Dockerfile comment changed from "# Default port (can be overridden by SERVER_PORT env var)" to "# Default port (37777 — ... [truncated]
```

### real-e8c8b314d6

```text
<observation>   <type>discovery</type>   <title>Read backend review findings and patrol rotation docs</title>   <subtitle>Investigation of draft review findings, evidence archive, and patrol state with no changes applied.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json has lastDirection set to "backend".</fact>     <fact>docs/drafts/backend-review-findings.md and docs/archive/2026-10-... [truncated]
```

### real-e02e436bdd

```text
<observation>   <type>change</type>   <title>Updated example formatting in prompt templates continuation.txt and init.txt</title>   <subtitle>Replaced a single Good example line with category examples block in two prompt resource files.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt had 9 lines removed and 11 lines added.</fact>     <fact>backend/src/main/resour... [truncated]
```

### real-0fd4ffcc31

```text
<observation>   <type>feature</type>   <title>Fixed GoldenSetReplayTest must-record assertion to check skipFp instead of skipFn</title>   <subtitle>Test assertion corrected to use skipFp for must-record samples skipped, with clarifying comments added to GoldenSetEvaluator.</subtitle>   <facts>     <fact>GoldenSetReplayTest assertion for "must-record samples skipped" changed from `a.skipFn` to `a.s... [truncated]
```

### real-eee7785ec8

```text
<observation>   <type>feature</type>   <title>Extended eval-model-quality.sh with EVAL_PROVIDER=anthropic and AnthropicChatModel</title>   <subtitle>Added Anthropic as a selectable evaluation provider to the manual golden-set harness and documented the run.</subtitle>   <facts>     <fact>scripts/eval-model-quality.sh now shows usage `#   EVAL_PROVIDER=anthropic ./scripts/eval-model-quality.sh`, re... [truncated]
```

### real-9a0e90bed5

```text
```xml <observation>   <type>feature</type>   <title>Added MemoryScopeEntity and refactored ObservationRepository to filter by memoryScopeId</title>   <subtitle>Introduced MemoryScope entity and service, switching observation queries from projectPath to memory scope ID filtering.</subtitle>   <facts>     <fact>MemoryScopeEntity.java was created in com.ablueforce.cortexce.entity with 65 lines of ne... [truncated]
```

### real-63b5502fb9

```text
<observation>   <type>feature</type>   <title>Expanded conversation memory scope identity migration in V20__conversation_memory_scope_expand.sql</title>   <subtitle>Rewrote V20 migration and added rehearsal and fixture scripts for conversation/memory scope identity expansion.</subtitle>   <facts>     <fact>backend/src/main/resources/db/migration/V20__conversation_memory_scope_expand.sql grew from ... [truncated]
```

### real-d33efbd8ae

```text
<observation>   <type>feature</type>   <title>Added ConversationScopeBackfillRunner and TechnicalBucketClassifier migration tooling</title>   <subtitle>New migration package adds conversation scope backfill runner, technical bucket classifier, CLI entry point, and tests.</subtitle>   <facts>     <fact>ConversationScopeBackfillRunner.java added to backend/src/main/java/com/ablueforce/cortexce/migra... [truncated]
```

### real-cd6b972cfd

```text
<observation>   <type>feature</type>   <title>Added SummaryJobService in backend service package</title>   <subtitle>Created new SummaryJobService Java class with 326 lines in cortexce backend service layer.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/service/SummaryJobService.java was created with 326 inserted lines and 0 deletions.</fact>     <fact>The file decla... [truncated]
```

### real-46e467bff6

```text
<observation>   <type>feature</type>   <title>Added conversation memory scope identity expansion in V20__conversation_memory_scope_expand.sql</title>   <subtitle>New additive Flyway migration V20 expands conversation and memory scope identity, accompanied by preflight checks and fixture data.</subtitle>   <facts>     <fact>backend/src/main/resources/db/migration/V20__conversation_memory_scope_expa... [truncated]
```

### real-95de35b2ea

```text
<observation>   <type>change</type>   <title>Updated byte-buddy 1.15.x pin rationale in backend/pom.xml</title>   <subtitle>Comment now attributes the byte-buddy pin to Mockito 5.11 via the spring-boot-starter-test BOM rather than JDK 24 test-classpath compatibility.</subtitle>   <facts>     <fact>backend/pom.xml byte-buddy pin comment changed from "Pin test-classpath byte-buddy to 1.15.x for JDK ... [truncated]
```

### real-53851b4a23

```text
<observation>   <type>change</type>   <title>Documented health-check rounds 312-359 in docs/archive/2026-10-08_health-check-history-11.md</title>   <subtitle>Created archive 11 of health check history covering rounds 312 through 359+ from 2026-10-06 to 2026-10-07.</subtitle>   <facts>     <fact>docs/archive/2026-10-08_health-check-history-11.md was created with 1206 lines covering rounds 312~359+ ... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
