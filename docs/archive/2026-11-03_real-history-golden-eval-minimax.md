golden set: real (36 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: after-v9
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 36 unique; runs scored: 1; key repeat: 3; real calls: 36 (cap none); duration: 139655 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 100% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 0/0/0/36 |
| format errors | 0 |
| validation rejections | 0 |
| call failures | 0 |
| title exact-hit rate (legacy) | 17% |
| title normalized hit rate (legacy) | 17% |
| title action-class hit rate (A6) | 42% (36 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 33% |
| title subject hit rate (legacy, diagnostic) | 61% |
| title trace-visible subject hit rate (A6b) | 61% (36 scored, 0 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 22% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 22% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 19% |
| title identifier hit rate | 61% |
| type accuracy | 61% |
| facts keyword precision (legacy) / recall | 100% / 100% |
| facts informative precision | 74% |
| facts trace-support rate | 100% |
| concepts precision / recall | 38% / 75% |
| skip consistency across repeats | 0% (0 repeated samples) |
| latency p50 / p95 / max (ms) | 3350 / 5707 / 10355 |
| tokens p50 / p95 / max / mean per call | 324 / 4137 / 4169 / 770 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 74% | **FAIL** |
| A6a | title action class (trace-derived) | >= 80% | 42% | **FAIL** |
| A6b | title trace-visible subject | >= 90% | 61% | **FAIL** |
| A6c | title identifiers | = 100% | 61% | **FAIL** |
| A6 | all three title dimensions together | all pass | 22% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 36 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 36 | 100% | 17% | 22% | 100% | 74% | 100% | 0 |

- average: skip 100%, title 17%, title contract 22%, facts P/R 100%/100%, facts actionable 74%
- worst run: skip 100%, title 17%, title contract 22%, facts P/R 100%/100%, facts actionable 74%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| refactor | 2 | 2/2 | 0/2 | 0/2 |
| feature | 7 | 7/7 | 4/7 | 1/7 |
| docs-update | 14 | 14/14 | 14/14 | 4/14 |
| test-add | 3 | 3/3 | 2/3 | 1/3 |
| security-fix | 10 | 10/10 | 2/10 | 0/10 |

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| real-77ba40df77 | refactor | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 33% | 50%/100% | 2812 |
| real-f0d346eab1 | feature | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 50%/100% | 10355 |
| real-3a2d426fdf | refactor | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 67% | 50%/100% | 4461 |
| real-864dadf51b | feature | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 50% | 50%/100% | 2695 |
| real-b064308244 | docs-update | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 50%/100% | 5028 |
| real-30a956e462 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 2529 |
| real-ba86bf2588 | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/100% | 5632 |
| real-a9c8452967 | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 5543 |
| real-db8ac0287e | docs-update | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 67% | 50%/100% | 4592 |
| real-7f5ff5974d | test-add | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 50%/100% | 2119 |
| real-9e6612fdf9 | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 0% | 0%/0% | 3191 |
| real-f43cfd6f44 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/100% | 4301 |
| real-e352ba3da3 | feature | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 50%/100% | 5707 |
| real-112de33e23 | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | match | 100%/100% | 50% | 50%/100% | 3694 |
| real-bc3512ae8d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3912 |
| real-28edef5a35 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | MISS | 100%/100% | 100% | 50%/100% | 3074 |
| real-e02e436bdd | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | match | 100%/100% | 100% | 50%/100% | 2895 |
| real-d44a83721d | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 3256 |
| real-6d6df2c321 | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | match | 100%/100% | 67% | 50%/100% | 4239 |
| real-0fd4ffcc31 | test-add | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 25% | 0%/0% | 2967 |
| real-d3431639a9 | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 3316 |
| real-7093cb5c56 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | match | 100%/100% | 67% | 50%/100% | 3271 |
| real-2025b33a95 | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 4321 |
| real-f3d12860ff | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 67% | 0%/0% | 3002 |
| real-6fd827abdc | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 67% | 50%/100% | 4521 |
| real-eee7785ec8 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 33% | 50%/100% | 3067 |
| real-3409dda60c | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3611 |
| real-f7b865efe5 | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 67% | 0%/0% | 2597 |
| real-a8cc1754bc | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 50%/100% | 3183 |
| real-9a0e90bed5 | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 50% | 0%/0% | 3659 |
| real-63b5502fb9 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | MISS | 100%/100% | 100% | 0%/0% | 4825 |
| real-d33efbd8ae | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | MISS | 100%/100% | 67% | 0%/0% | 3025 |
| real-cd6b972cfd | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 67% | 0%/0% | 3350 |
| real-46e467bff6 | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 0%/0% | 3111 |
| real-325c817bbf | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 33% | 50%/100% | 3612 |
| real-e8c8b314d6 | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 50% | 50%/100% | 3857 |

## Problems

- real-77ba40df77: title coverage 50% (expected keywords [change, extractionconsistency])
- real-77ba40df77: title action 'modify' missing: title leads 'create' (got 'Added opt-in majority vote for observation extraction in ExtractionConsistency')
- real-77ba40df77: title verb class 'change' missing (got 'Added opt-in majority vote for observation extraction in ExtractionConsistency')
- real-77ba40df77: type 'feature' != expected 'refactor'
- real-f0d346eab1: title coverage 50% (expected keywords [change, golden-tool-use-real.json])
- real-f0d346eab1: title action 'modify' missing: title leads 'create' (got 'Added 36-sample real-history golden set in golden-tool-use-real.json')
- real-f0d346eab1: title verb class 'change' missing (got 'Added 36-sample real-history golden set in golden-tool-use-real.json')
- real-f0d346eab1: type 'feature' != expected 'change'
- real-3a2d426fdf: title lost identifiers [GoldenSetScorer] (got 'Extended extraction eval with promptTokens and run-to-run variance')
- real-3a2d426fdf: title verb class 'change' missing (got 'Extended extraction eval with promptTokens and run-to-run variance')
- real-3a2d426fdf: title subject [GoldenSetScorer] missing (got 'Extended extraction eval with promptTokens and run-to-run variance')
- real-3a2d426fdf: title trace-visible subject [GoldenSetScorer] missing (got 'Extended extraction eval with promptTokens and run-to-run variance')
- real-3a2d426fdf: type 'feature' != expected 'refactor'
- real-864dadf51b: title coverage 50% (expected keywords [change, goldensetscorer])
- real-864dadf51b: title action 'modify' missing: title leads 'create' (got 'Added trace-visible subject scoring to GoldenSetScorer')
- real-864dadf51b: title verb class 'change' missing (got 'Added trace-visible subject scoring to GoldenSetScorer')
- real-b064308244: title coverage 50% (expected keywords [document, extraction-quality-improvement-plan.md])
- real-b064308244: title action 'document' missing: title leads 'modify' (got 'Updated rehearsal test attribution in extraction-quality-improvement-plan.md')
- real-b064308244: title verb class 'document' missing (got 'Updated rehearsal test attribution in extraction-quality-improvement-plan.md')
- real-db8ac0287e: title coverage 50% (expected keywords [document, continuation.txt])
- real-db8ac0287e: title action 'document' missing: title leads 'modify' (got 'Updated security defect definition in backend/src/main/resources/prompts/continuation.txt')
- real-db8ac0287e: title verb class 'document' missing (got 'Updated security defect definition in backend/src/main/resources/prompts/continuation.txt')
- real-7f5ff5974d: title coverage 50% (expected keywords [change, golden-tool-use.json])
- real-7f5ff5974d: title action 'modify' missing: title leads 'create' (got 'Added five removal samples for remove action in golden-tool-use.json')
- real-7f5ff5974d: title verb class 'change' missing (got 'Added five removal samples for remove action in golden-tool-use.json')
- real-9e6612fdf9: title coverage 50% (expected keywords [harden, continuation.txt])
- real-9e6612fdf9: title action 'secure' missing: title leads 'modify' (got 'Updated Fixed verb scope in continuation.txt')
- real-9e6612fdf9: title verb class 'harden' missing (got 'Updated Fixed verb scope in continuation.txt')
- real-9e6612fdf9: type 'change' != expected 'bugfix'
- real-9e6612fdf9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e352ba3da3: title coverage 50% (expected keywords [add, longcat-after-v7.json])
- real-e352ba3da3: title action 'create' missing: title leads 'null' (got 'Archived round-seven evaluation fixtures in longcat-after-v7.json and minimax-after-v7.json')
- real-e352ba3da3: title verb class 'add' missing (got 'Archived round-seven evaluation fixtures in longcat-after-v7.json and minimax-after-v7.json')
- real-e352ba3da3: type 'change' != expected 'feature'
- real-112de33e23: title lost identifiers [continuation.txt] (got 'Updated replacement naming guidance in backend prompt files')
- real-112de33e23: title action 'document' missing: title leads 'modify' (got 'Updated replacement naming guidance in backend prompt files')
- real-112de33e23: title verb class 'document' missing (got 'Updated replacement naming guidance in backend prompt files')
- real-112de33e23: title subject [continuation.txt] missing (got 'Updated replacement naming guidance in backend prompt files')
- real-112de33e23: title trace-visible subject [continuation.txt] missing (got 'Updated replacement naming guidance in backend prompt files')
- real-28edef5a35: title lost identifiers [longcat-after-v6.json] (got 'Archived round-six longcat and minimax evaluation fixtures')
- real-28edef5a35: title action 'create' missing: title leads 'null' (got 'Archived round-six longcat and minimax evaluation fixtures')
- real-28edef5a35: title verb class 'add' missing (got 'Archived round-six longcat and minimax evaluation fixtures')
- real-28edef5a35: title subject [longcat-after-v6.json] missing (got 'Archived round-six longcat and minimax evaluation fixtures')
- real-28edef5a35: title trace-visible subject [longcat-after-v6.json] missing (got 'Archived round-six longcat and minimax evaluation fixtures')
- real-28edef5a35: type 'change' != expected 'feature'
- real-e02e436bdd: title lost identifiers [continuation.txt] (got 'Updated prompt title guidance with category-shaped examples')
- real-e02e436bdd: title action 'document' missing: title leads 'modify' (got 'Updated prompt title guidance with category-shaped examples')
- real-e02e436bdd: title verb class 'document' missing (got 'Updated prompt title guidance with category-shaped examples')
- real-e02e436bdd: title subject [continuation.txt] missing (got 'Updated prompt title guidance with category-shaped examples')
- real-e02e436bdd: title trace-visible subject [continuation.txt] missing (got 'Updated prompt title guidance with category-shaped examples')
- real-d44a83721d: title lost identifiers [extraction-quality-improvement-plan.md] (got 'Documented Round 5 metric correctness and A6 title ceiling in extraction plan')
- real-d44a83721d: title subject [extraction-quality-improvement-plan.md] missing (got 'Documented Round 5 metric correctness and A6 title ceiling in extraction plan')
- real-d44a83721d: title trace-visible subject [extraction-quality-improvement-plan.md] missing (got 'Documented Round 5 metric correctness and A6 title ceiling in extraction plan')
- real-6d6df2c321: title lost identifiers [continuation.txt] (got 'Updated prompt category slots and title verb guidance')
- real-6d6df2c321: title action 'document' missing: title leads 'modify' (got 'Updated prompt category slots and title verb guidance')
- real-6d6df2c321: title verb class 'document' missing (got 'Updated prompt category slots and title verb guidance')
- real-6d6df2c321: title subject [continuation.txt] missing (got 'Updated prompt category slots and title verb guidance')
- real-6d6df2c321: title trace-visible subject [continuation.txt] missing (got 'Updated prompt category slots and title verb guidance')
- real-0fd4ffcc31: title coverage 50% (expected keywords [change, goldensetscorer])
- real-0fd4ffcc31: title verb class 'change' missing (got 'Fixed skip gate counters and plural keyword scoring in GoldenSetScorer')
- real-0fd4ffcc31: type 'bugfix' != expected 'feature'
- real-0fd4ffcc31: concepts recall 0% (expected [what-changed], got [problem-solution, pattern])
- real-d3431639a9: title lost identifiers [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] (got 'Documented round four model evaluation results in archived evaluation notes')
- real-d3431639a9: title subject [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] missing (got 'Documented round four model evaluation results in archived evaluation notes')
- real-d3431639a9: title trace-visible subject [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] missing (got 'Documented round four model evaluation results in archived evaluation notes')
- real-7093cb5c56: title lost identifiers [EmbeddingBackfillConcurrencyDatabaseTest] (got 'Fixed embedding repair row claiming before write in EmbeddingBackfillService')
- real-7093cb5c56: title action 'secure' missing: title leads 'modify' (got 'Fixed embedding repair row claiming before write in EmbeddingBackfillService')
- real-7093cb5c56: title verb class 'harden' missing (got 'Fixed embedding repair row claiming before write in EmbeddingBackfillService')
- real-7093cb5c56: title subject [EmbeddingBackfillConcurrencyDatabaseTest] missing (got 'Fixed embedding repair row claiming before write in EmbeddingBackfillService')
- real-7093cb5c56: title trace-visible subject [EmbeddingBackfillConcurrencyDatabaseTest] missing (got 'Fixed embedding repair row claiming before write in EmbeddingBackfillService')
- real-2025b33a95: title lost identifiers [extraction-quality-improvement-plan.md] (got 'Documented audit gap closure and rejected alternatives')
- real-2025b33a95: title subject [extraction-quality-improvement-plan.md] missing (got 'Documented audit gap closure and rejected alternatives')
- real-2025b33a95: title trace-visible subject [extraction-quality-improvement-plan.md] missing (got 'Documented audit gap closure and rejected alternatives')
- real-f3d12860ff: title coverage 50% (expected keywords [harden, logscontrollertest])
- real-f3d12860ff: title action 'secure' missing: title leads 'modify' (got 'Changed LogsController log directory setup in LogsControllerTest.java')
- real-f3d12860ff: title verb class 'harden' missing (got 'Changed LogsController log directory setup in LogsControllerTest.java')
- real-f3d12860ff: type 'change' != expected 'bugfix'
- real-f3d12860ff: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-6fd827abdc: title coverage 50% (expected keywords [change, extractionvalidationservicetest])
- real-eee7785ec8: title lost identifiers [2026-11-02_model-output-quality-eval-minimax.md] (got 'Extended quality evaluation to support Anthropic provider')
- real-eee7785ec8: title verb class 'change' missing (got 'Extended quality evaluation to support Anthropic provider')
- real-eee7785ec8: title subject [2026-11-02_model-output-quality-eval-minimax.md] missing (got 'Extended quality evaluation to support Anthropic provider')
- real-eee7785ec8: title trace-visible subject [2026-11-02_model-output-quality-eval-minimax.md] missing (got 'Extended quality evaluation to support Anthropic provider')
- real-f7b865efe5: title coverage 50% (expected keywords [harden, index.html])
- real-f7b865efe5: title action 'secure' missing: title leads 'modify' (got 'Changed header background color to #f8f6f2 in index.html')
- real-f7b865efe5: title verb class 'harden' missing (got 'Changed header background color to #f8f6f2 in index.html')
- real-f7b865efe5: type 'change' != expected 'bugfix'
- real-f7b865efe5: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a8cc1754bc: title coverage 50% (expected keywords [harden, pendingmessagerepository])
- real-a8cc1754bc: title action 'secure' missing: title leads 'modify' (got 'Fixed PendingMessageRepository deduplication for real V20 schema')
- real-a8cc1754bc: title verb class 'harden' missing (got 'Fixed PendingMessageRepository deduplication for real V20 schema')
- real-9a0e90bed5: title coverage 50% (expected keywords [harden, observationrepository])
- real-9a0e90bed5: title action 'secure' missing: title leads 'modify' (got 'Refactored observation reads to memory scope predicates in ObservationRepository')
- real-9a0e90bed5: title verb class 'harden' missing (got 'Refactored observation reads to memory scope predicates in ObservationRepository')
- real-9a0e90bed5: type 'refactor' != expected 'bugfix'
- real-9a0e90bed5: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-63b5502fb9: title lost identifiers [V20__conversation_memory_scope_expand.sql] (got 'Converted V20 conversation memory scope migration to atomic fail-closed form')
- real-63b5502fb9: title action 'secure' missing: title leads 'modify' (got 'Converted V20 conversation memory scope migration to atomic fail-closed form')
- real-63b5502fb9: title verb class 'harden' missing (got 'Converted V20 conversation memory scope migration to atomic fail-closed form')
- real-63b5502fb9: title subject [V20__conversation_memory_scope_expand.sql] missing (got 'Converted V20 conversation memory scope migration to atomic fail-closed form')
- real-63b5502fb9: title trace-visible subject [V20__conversation_memory_scope_expand.sql] missing (got 'Converted V20 conversation memory scope migration to atomic fail-closed form')
- real-63b5502fb9: type 'change' != expected 'bugfix'
- real-63b5502fb9: concepts recall 0% (expected [problem-solution], got [what-changed, gotcha])
- real-d33efbd8ae: title lost identifiers [ConversationScopeBackfillRunner] (got 'Added fail-closed classifier and checkpointed backfill runner')
- real-d33efbd8ae: title action 'secure' missing: title leads 'create' (got 'Added fail-closed classifier and checkpointed backfill runner')
- real-d33efbd8ae: title verb class 'harden' missing (got 'Added fail-closed classifier and checkpointed backfill runner')
- real-d33efbd8ae: title subject [ConversationScopeBackfillRunner] missing (got 'Added fail-closed classifier and checkpointed backfill runner')
- real-d33efbd8ae: title trace-visible subject [ConversationScopeBackfillRunner] missing (got 'Added fail-closed classifier and checkpointed backfill runner')
- real-d33efbd8ae: type 'feature' != expected 'bugfix'
- real-d33efbd8ae: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-cd6b972cfd: title coverage 50% (expected keywords [harden, summaryjobservice])
- real-cd6b972cfd: title action 'secure' missing: title leads 'create' (got 'Added durable resumable SessionEnd summary job in SummaryJobService')
- real-cd6b972cfd: title verb class 'harden' missing (got 'Added durable resumable SessionEnd summary job in SummaryJobService')
- real-cd6b972cfd: type 'feature' != expected 'bugfix'
- real-cd6b972cfd: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-46e467bff6: title coverage 50% (expected keywords [harden, conversation-scope-preflight.sql])
- real-46e467bff6: title action 'secure' missing: title leads 'create' (got 'Added read-only conversation scope preflight in conversation-scope-preflight.sql')
- real-46e467bff6: title verb class 'harden' missing (got 'Added read-only conversation scope preflight in conversation-scope-preflight.sql')
- real-46e467bff6: type 'feature' != expected 'bugfix'
- real-46e467bff6: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-325c817bbf: title lost identifiers [conversation-memory-scope-refactor-plan.md] (got 'Documented Conversation and Memory Scope refactor plan')
- real-325c817bbf: title subject [conversation-memory-scope-refactor-plan.md] missing (got 'Documented Conversation and Memory Scope refactor plan')
- real-325c817bbf: title trace-visible subject [conversation-memory-scope-refactor-plan.md] missing (got 'Documented Conversation and Memory Scope refactor plan')
- real-e8c8b314d6: title lost identifiers [backend-review-findings.md] (got 'Documented round 360 blank base-url application abort in backend review findings')
- real-e8c8b314d6: title subject [backend-review-findings.md] missing (got 'Documented round 360 blank base-url application abort in backend review findings')
- real-e8c8b314d6: title trace-visible subject [backend-review-findings.md] missing (got 'Documented round 360 blank base-url application abort in backend review findings')

## Raw answers behind problems

### real-77ba40df77

```text
<observation>   <type>feature</type>   <title>Added opt-in majority vote for observation extraction in ExtractionConsistency</title>   <subtitle>ExtractionConsistency now supports consistency voting, with AgentService integration and dedicated tests.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/service/ExtractionConsistency.java defines the opt-in majority-vote extr... [truncated]
```

### real-f0d346eab1

```text
<observation>   <type>feature</type>   <title>Added 36-sample real-history golden set in golden-tool-use-real.json</title>   <subtitle>Generated evaluation fixtures and test coverage from real git history.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json contains a generated 36-sample golden set from real git history.</fact>     <fact>GoldenSetRealDataTest.j... [truncated]
```

### real-3a2d426fdf

```text
<observation>   <type>feature</type>   <title>Extended extraction eval with promptTokens and run-to-run variance</title>   <subtitle>Golden-set scoring now tracks per-call token cost and compares repeated evaluation runs.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java adds public final int promptTokens.</fact>     <fact>backend/src/test/java/... [truncated]
```

### real-864dadf51b

```text
<observation>   <type>feature</type>   <title>Added trace-visible subject scoring to GoldenSetScorer</title>   <subtitle>Golden-set scoring now scores subjects visible in reproduction traces and requires identifier-specific titles.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java now derives evidence text from a sample toolName value.</fact>   ... [truncated]
```

### real-b064308244

```text
<observation>   <type>change</type>   <title>Updated rehearsal test attribution in extraction-quality-improvement-plan.md</title>   <subtitle>Replaced the L4 layer description with the rehearsal database gate attribution.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md now states that the offline model stand-in test runs under the rehearsal database gate.</fact>  ... [truncated]
```

### real-db8ac0287e

```text
<observation>   <type>change</type>   <title>Updated security defect definition in backend/src/main/resources/prompts/continuation.txt</title>   <subtitle>Prompt guidance now classifies all security defects as Fixed without a non-security qualification.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt defines Fixed as “a defect already produced wrong or failing be... [truncated]
```

### real-7f5ff5974d

```text
<observation>   <type>feature</type>   <title>Added five removal samples for remove action in golden-tool-use.json</title>   <subtitle>Expanded the golden tool-use evaluation dataset with samples that exercise the remove action branch.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use.json received 180 inserted lines and 0 deleted lines.</fact>     <fact>backend/src/te... [truncated]
```

### real-9e6612fdf9

```text
<observation>   <type>change</type>   <title>Updated Fixed verb scope in continuation.txt</title>   <subtitle>Prompt guidance now reserves “Fixed” for defects in non-security code.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt defines “Fixed” as a defect already producing wrong or failing behaviour in non-security code.</fact>     <fact>The commit changed 4 fil... [truncated]
```

### real-e352ba3da3

```text
<observation>   <type>change</type>   <title>Archived round-seven evaluation fixtures in longcat-after-v7.json and minimax-after-v7.json</title>   <subtitle>Round-seven model-output-quality evaluation artifacts were added to the backend fixtures and docs archive.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v7.json contains the round-seven longcat evaluatio... [truncated]
```

### real-112de33e23

```text
<observation>   <type>change</type>   <title>Updated replacement naming guidance in backend prompt files</title>   <subtitle>Prompt instructions now require both replaced literals and new file names, plus exact script and test identifiers.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt now contains the instruction “A replacement keeps BOTH names”.</fact>     <fa... [truncated]
```

### real-28edef5a35

```text
<observation>   <type>change</type>   <title>Archived round-six longcat and minimax evaluation fixtures</title>   <subtitle>Round-six full-run outputs were added for both providers and archived with a minimax quality report.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v6.json contains the archived round-six longcat full-run output.</fact>     <fact>backend... [truncated]
```

### real-e02e436bdd

```text
<observation>   <type>change</type>   <title>Updated prompt title guidance with category-shaped examples</title>   <subtitle>Prompt templates now model titles by change category instead of repeating the generic title contract.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt replaces a UserCache.computeIfAbsent example with the heading "- Category examples (copy t... [truncated]
```

### real-d44a83721d

```text
<observation>   <type>change</type>   <title>Documented Round 5 metric correctness and A6 title ceiling in extraction plan</title>   <subtitle>The extraction quality plan records round five decisions and the remaining A6 title ceiling.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md includes the section "## 7.11 Round 5 — metric correctness and the remaining title... [truncated]
```

### real-6d6df2c321

```text
<observation>   <type>change</type>   <title>Updated prompt category slots and title verb guidance</title>   <subtitle>Prompt instructions now explicitly constrain concept categories and title verbs.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt now includes guidance for locating identifiers with grep/glob searches.</fact>     <fact>backend/src/main/resources/p... [truncated]
```

### real-0fd4ffcc31

```text
<observation>   <type>bugfix</type>   <title>Fixed skip gate counters and plural keyword scoring in GoldenSetScorer</title>   <subtitle>Golden-set evaluation now folds plural keywords and corrects skip-gate counter behavior across scorer and replay tests.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java keyword mappings include Map.entry("docs"... [truncated]
```

### real-d3431639a9

```text
<observation>   <type>change</type>   <title>Documented round four model evaluation results in archived evaluation notes</title>   <subtitle>Archived evaluation notes record what improved, what failed, and the rationale across MiniMax and LongCat runs.</subtitle>   <facts>     <fact>docs/archive/2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md begins with the heading "# Model outp... [truncated]
```

### real-7093cb5c56

```text
<observation>   <type>bugfix</type>   <title>Fixed embedding repair row claiming before write in EmbeddingBackfillService</title>   <subtitle>Embedding backfill now claims each row before writing, with concurrency and service coverage.</subtitle>   <facts>     <fact>EmbeddingBackfillService now claims the row before it writes embedding repair data.</fact>     <fact>EmbeddingBackfillConcurrencyData... [truncated]
```

### real-2025b33a95

```text
<observation>   <type>change</type>   <title>Documented audit gap closure and rejected alternatives</title>   <subtitle>The extraction quality plan records audit-gap closure and alternatives considered on 2026-11-03.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md contains the section "7.9 Audit gap closure (2026-11-03)".</fact>     <fact>docs/drafts/extraction-qu... [truncated]
```

### real-f3d12860ff

```text
<observation>   <type>change</type>   <title>Changed LogsController log directory setup in LogsControllerTest.java</title>   <subtitle>LogsControllerTest now sets the logDir field directly while preserving the existing log API default.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/controller/LogsControllerTest.java replaced reflective logDir field lookup with setFiel... [truncated]
```

### real-6fd827abdc

```text
<observation>   <type>change</type>   <title>Updated ExtractionValidationServiceTest#missingOrderThrows fact wording</title>   <subtitle>Replaced a process-noise test reference with the concrete missing-orderId exception behavior.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/service/ExtractionValidationServiceTest.java now records “PaymentNotFoundException is thrown... [truncated]
```

### real-eee7785ec8

```text
<observation>   <type>feature</type>   <title>Extended quality evaluation to support Anthropic provider</title>   <subtitle>Golden set evaluation now handles Anthropic alongside the existing model-provider workflow.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetEvaluator.java supports the Anthropic provider.</fact>     <fact>scripts/eval-model-quality.s... [truncated]
```

### real-f7b865efe5

```text
<observation>   <type>change</type>   <title>Changed header background color to #f8f6f2 in index.html</title>   <subtitle>The WebUI static viewer header color was updated as part of the v2 identity wire commit.</subtitle>   <facts>     <fact>backend/src/main/resources/static/index.html sets --color-bg-header to #f8f6f2.</fact>     <fact>The commit “build: pair WebUI gitlink and static viewer with ... [truncated]
```

### real-a8cc1754bc

```text
<observation>   <type>bugfix</type>   <title>Fixed PendingMessageRepository deduplication for real V20 schema</title>   <subtitle>Refactored repository and agent-session persistence to align startup and serving with the real V20 schema.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/repository/PendingMessageRepository.java removed the comment describing same-session a... [truncated]
```

### real-9a0e90bed5

```text
<observation>   <type>refactor</type>   <title>Refactored observation reads to memory scope predicates in ObservationRepository</title>   <subtitle>Observation queries now resolve projects through memory scope membership instead of direct project-path equality.</subtitle>   <facts>     <fact>ObservationRepository.java replaced the predicate `o.projectPath = :project` with `o.memoryScopeId IN (...)... [truncated]
```

### real-63b5502fb9

```text
<observation>   <type>change</type>   <title>Converted V20 conversation memory scope migration to atomic fail-closed form</title>   <subtitle>V20 now provides one-shot conversation and memory scope identity migration with conflict rehearsal tooling.</subtitle>   <facts>     <fact>backend/src/main/resources/db/migration/V20__conversation_memory_scope_expand.sql now starts with the V20 one-shot migr... [truncated]
```

### real-d33efbd8ae

```text
<observation>   <type>feature</type>   <title>Added fail-closed classifier and checkpointed backfill runner</title>   <subtitle>Migration code now classifies technical conversations and supports resumable conversation scope backfill.</subtitle>   <facts>     <fact>ConversationScopeBackfillRunner.java introduces a checkpointed migration runner under com.ablueforce.cortexce.migration.</fact>     <fa... [truncated]
```

### real-cd6b972cfd

```text
<observation>   <type>feature</type>   <title>Added durable resumable SessionEnd summary job in SummaryJobService</title>   <subtitle>SummaryJobService introduces durable and resumable processing for SessionEnd summary jobs.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/service/SummaryJobService.java defines package com.ablueforce.cortexce.service.</fact>     <fact>S... [truncated]
```

### real-46e467bff6

```text
<observation>   <type>feature</type>   <title>Added read-only conversation scope preflight in conversation-scope-preflight.sql</title>   <subtitle>New SQL preflight supports validation before applying the V20 conversation memory scope expansion.</subtitle>   <facts>     <fact>scripts/db/conversation-scope-preflight.sql was added with 986 inserted lines across 3 changed files.</fact>     <fact>back... [truncated]
```

### real-325c817bbf

```text
<observation>   <type>change</type>   <title>Documented Conversation and Memory Scope refactor plan</title>   <subtitle>Added a comprehensive implementation blueprint for clarifying Conversation and Memory Scope identity boundaries.</subtitle>   <facts>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md was added with 917 inserted lines and 0 deleted lines.</fact>     <fact>The new d... [truncated]
```

### real-e8c8b314d6

```text
<observation>   <type>change</type>   <title>Documented round 360 blank base-url application abort in backend review findings</title>   <subtitle>Backend review notes now identify blank base-url as a Spring startup abort affecting non-README design coverage.</subtitle>   <facts>     <fact>docs/drafts/backend-review-findings.md now contains the heading “第 360 轮补充：受影响的不止 README，零覆盖设计稿同样未记载”.</fact> ... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
