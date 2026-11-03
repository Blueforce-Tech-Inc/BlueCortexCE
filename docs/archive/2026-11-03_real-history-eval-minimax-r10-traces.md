golden set: real (60 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: r10-traces
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 60 unique; runs scored: 1; key repeat: 3; real calls: 60 (cap none); duration: 302312 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 95% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 10/0/0/50 |
| format errors | 0 |
| validation rejections | 1 |
| call failures | 2 |
| title exact-hit rate (legacy) | 26% |
| title normalized hit rate (legacy) | 26% |
| title action-class hit rate (A6) | 94% (47 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 40% |
| title subject hit rate (legacy, diagnostic) | 45% |
| title trace-visible subject hit rate (A6b) | 45% (47 scored, 0 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 43% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 43% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 26% |
| title identifier hit rate | 43% |
| type accuracy | 62% |
| facts keyword precision (legacy) / recall | 100% / 100% |
| facts informative precision | 87% |
| facts trace-support rate | 100% |
| concepts precision / recall | 28% / 55% |
| skip consistency across repeats | 0% (0 repeated samples) |
| latency p50 / p95 / max (ms) | 4079 / 12663 / 18753 |
| tokens p50 / p95 / max / mean per call | 4072 / 4703 / 4907 / 3238 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 87% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 94% | PASS |
| A6b | title trace-visible subject | >= 90% | 45% | **FAIL** |
| A6c | title identifiers | = 100% | 43% | **FAIL** |
| A6 | all three title dimensions together | all pass | 43% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 58 |
| exception | 2 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 60 | 100% | 26% | 43% | 100% | 87% | 100% | 0 |

- average: skip 100%, title 26%, title contract 43%, facts P/R 100%/100%, facts actionable 87%
- worst run: skip 100%, title 26%, title contract 43%, facts P/R 100%/100%, facts actionable 87%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| feature | 9 | 9/9 | 6/9 | 1/9 |
| config-change | 1 | 1/1 | 1/1 | 0/1 |
| refactor | 3 | 3/3 | 1/3 | 0/3 |
| docs-update | 20 | 19/20 | 19/20 | 8/20 |
| test-add | 2 | 2/2 | 1/2 | 1/2 |
| security-fix | 10 | 8/10 | 1/10 | 1/10 |
| bug-fix | 5 | 5/5 | 0/5 | 1/5 |
| diagnose | 10 | 10/10 | 0/10 | 0/10 |

## Failure clusters (first run)

| cluster | misses | share of scored (47) |
|---|---|---|
| identifier missing | 27 | 57% |
| action class wrong | 3 | 6% |
| subject wrong | 26 | 55% |
| type wrong | 18 | 38% |

### identifier missing (27)

- real-f6a1b89e41 — expected identifiers [golden-tool-use-real.json], title 'Expanded real-history golden set to 60 samples with round-10 evidence'
- real-1fe4ffb124 — expected identifiers [extraction-quality-improvement-plan.md], title 'Raised minimum test count to 472 across test configuration'
- real-d13e2c90cd — expected identifiers [2026-11-03_real-history-golden-eval-minimax.md], title 'Added real-history golden evaluation for MiniMax'
- real-3b4f737562 — expected identifiers [minimax-after-v9.json], title 'Updated Minimax v9 evaluation fixtures and scoring'
- real-77ba40df77 — expected identifiers [ExtractionConsistency], title 'Added extraction consistency voting and configured tool trace attempts'
- real-a9c8452967 — expected identifiers [SearchSimilarityEvaluator], title 'Added semantic relevance evaluation harness in backend/src/test'
- real-db8ac0287e — expected identifiers [continuation.txt], title 'Updated defect classification guidance in prompt resources'
- real-9e6612fdf9 — expected identifiers [continuation.txt], title 'Updated defect taxonomy and golden-set action rules'
- real-e352ba3da3 — expected identifiers [longcat-after-v7.json], title 'Added model output quality evaluation fixtures for longcat and minimax'
- real-112de33e23 — expected identifiers [continuation.txt], title 'Extended replacement naming guidance in prompt templates'
- real-28edef5a35 — expected identifiers [longcat-after-v6.json], title 'Added MiniMax and LongCat v6 model-output evaluation fixtures'
- real-6d6df2c321 — expected identifiers [continuation.txt], title 'Hardened continuation and init prompt skip guidance'
- real-d3431639a9 — expected identifiers [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md], title 'Documented Round 4 model output quality evaluation in five files'
- real-7093cb5c56 — expected identifiers [EmbeddingBackfillConcurrencyDatabaseTest], title 'Added embedding backfill concurrency and sanitization tests'
- real-a8cc1754bc — expected identifiers [PendingMessageRepository], title 'Refactored pending-message deduplication and repository queries'
- real-d958416fdc — expected identifiers [patrol-state.json], title 'Updated patrol state and API documentation across six files'
- real-85480a720e — expected identifiers [TESTING-zh-CN.md], title 'Updated database environment variable documentation in docs/TESTING.md'
- real-c67e7a5ad3 — expected identifiers [dto.py], title 'Removed empty else branch and ignored memory/.dreams in .gitignore'
- real-8ac2a32980 — expected identifiers [Dockerfile], title 'Updated default port documentation and removed build-argument guidance'
- real-d44a83721d — expected identifiers [extraction-quality-improvement-plan.md], title 'Documented Round 5 metric correctness and title ceiling in extraction plan'
- real-0fd4ffcc31 — expected identifiers [GoldenSetScorer], title 'Fixed must-record sample skip gate in GoldenSetReplayTest'
- real-eee7785ec8 — expected identifiers [2026-11-02_model-output-quality-eval-minimax.md], title 'Extended GoldenSetEvaluator for Anthropic model evaluation'
- real-63b5502fb9 — expected identifiers [V20__conversation_memory_scope_expand.sql], title 'Added one-shot conversation memory scope migration and rehearsal fixtures'
- real-46e467bff6 — expected identifiers [conversation-scope-preflight.sql], title 'Added conversation scope preflight and fixture SQL scripts'
- real-325c817bbf — expected identifiers [conversation-memory-scope-refactor-plan.md], title 'Documented Conversation 与 Memory Scope 身份边界重构实施蓝图'
- real-95de35b2ea — expected identifiers [pom.xml], title 'Updated byte-buddy pin comment and Docker build context guidance'
- real-53851b4a23 — expected identifiers [2026-10-08_health-check-history-11.md], title 'Documented health-check history rounds 312~359+ in archive index'

### action class wrong (3)

- real-f6a1b89e41 — expected [modify], got 'null' from title 'Expanded real-history golden set to 60 samples with round-10 evidence'
- real-1fe4ffb124 — expected [document, modify], got 'null' from title 'Raised minimum test count to 472 across test configuration'
- real-85480a720e — expected [remove, document], got 'modify' from title 'Updated database environment variable documentation in docs/TESTING.md'

### subject wrong (26)

- real-f6a1b89e41 — expected subject tokens [golden-tool-use-real.json], title 'Expanded real-history golden set to 60 samples with round-10 evidence'
- real-1fe4ffb124 — expected subject tokens [extraction-quality-improvement-plan.md], title 'Raised minimum test count to 472 across test configuration'
- real-d13e2c90cd — expected subject tokens [2026-11-03_real-history-golden-eval-minimax.md], title 'Added real-history golden evaluation for MiniMax'
- real-3b4f737562 — expected subject tokens [minimax-after-v9.json], title 'Updated Minimax v9 evaluation fixtures and scoring'
- real-a9c8452967 — expected subject tokens [SearchSimilarityEvaluator], title 'Added semantic relevance evaluation harness in backend/src/test'
- real-db8ac0287e — expected subject tokens [continuation.txt], title 'Updated defect classification guidance in prompt resources'
- real-9e6612fdf9 — expected subject tokens [continuation.txt], title 'Updated defect taxonomy and golden-set action rules'
- real-e352ba3da3 — expected subject tokens [longcat-after-v7.json], title 'Added model output quality evaluation fixtures for longcat and minimax'
- real-112de33e23 — expected subject tokens [continuation.txt], title 'Extended replacement naming guidance in prompt templates'
- real-28edef5a35 — expected subject tokens [longcat-after-v6.json], title 'Added MiniMax and LongCat v6 model-output evaluation fixtures'
- real-6d6df2c321 — expected subject tokens [continuation.txt], title 'Hardened continuation and init prompt skip guidance'
- real-d3431639a9 — expected subject tokens [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md], title 'Documented Round 4 model output quality evaluation in five files'
- real-7093cb5c56 — expected subject tokens [EmbeddingBackfillConcurrencyDatabaseTest], title 'Added embedding backfill concurrency and sanitization tests'
- real-a8cc1754bc — expected subject tokens [PendingMessageRepository], title 'Refactored pending-message deduplication and repository queries'
- real-d958416fdc — expected subject tokens [patrol-state.json], title 'Updated patrol state and API documentation across six files'
- real-85480a720e — expected subject tokens [TESTING-zh-CN.md], title 'Updated database environment variable documentation in docs/TESTING.md'
- real-c67e7a5ad3 — expected subject tokens [dto.py], title 'Removed empty else branch and ignored memory/.dreams in .gitignore'
- real-8ac2a32980 — expected subject tokens [Dockerfile], title 'Updated default port documentation and removed build-argument guidance'
- real-d44a83721d — expected subject tokens [extraction-quality-improvement-plan.md], title 'Documented Round 5 metric correctness and title ceiling in extraction plan'
- real-0fd4ffcc31 — expected subject tokens [GoldenSetScorer], title 'Fixed must-record sample skip gate in GoldenSetReplayTest'
- real-eee7785ec8 — expected subject tokens [2026-11-02_model-output-quality-eval-minimax.md], title 'Extended GoldenSetEvaluator for Anthropic model evaluation'
- real-63b5502fb9 — expected subject tokens [V20__conversation_memory_scope_expand.sql], title 'Added one-shot conversation memory scope migration and rehearsal fixtures'
- real-46e467bff6 — expected subject tokens [conversation-scope-preflight.sql], title 'Added conversation scope preflight and fixture SQL scripts'
- real-325c817bbf — expected subject tokens [conversation-memory-scope-refactor-plan.md], title 'Documented Conversation 与 Memory Scope 身份边界重构实施蓝图'
- real-95de35b2ea — expected subject tokens [pom.xml], title 'Updated byte-buddy pin comment and Docker build context guidance'
- real-53851b4a23 — expected subject tokens [2026-10-08_health-check-history-11.md], title 'Documented health-check history rounds 312~359+ in archive index'

### type wrong (18)

- real-f6a1b89e41 — expected 'change', got 'feature' (title 'Expanded real-history golden set to 60 samples with round-10 evidence')
- real-3b4f737562 — expected 'feature', got 'change' (title 'Updated Minimax v9 evaluation fixtures and scoring')
- real-77ba40df77 — expected 'refactor', got 'feature' (title 'Added extraction consistency voting and configured tool trace attempts')
- real-f0d346eab1 — expected 'change', got 'feature' (title 'Added real-history golden tool-use evaluation dataset in golden-tool-use-real.json')
- real-3a2d426fdf — expected 'refactor', got 'feature' (title 'Extended GoldenSetScorer with promptTokens and repeat-all evaluation')
- real-9e6612fdf9 — expected 'bugfix', got 'change' (title 'Updated defect taxonomy and golden-set action rules')
- real-7093cb5c56 — expected 'bugfix', got 'feature' (title 'Added embedding backfill concurrency and sanitization tests')
- real-a8cc1754bc — expected 'bugfix', got 'change' (title 'Refactored pending-message deduplication and repository queries')
- real-e2c887775a — expected 'bugfix', got 'change' (title 'Corrected /api/context/inject updateFiles contract in AGENTS.md')
- real-c67e7a5ad3 — expected 'bugfix', got 'change' (title 'Removed empty else branch and ignored memory/.dreams in .gitignore')
- real-bdf72f2509 — expected 'bugfix', got 'change' (title 'Removed SDK version comment in CortexMemClientImpl.java')
- real-8ac2a32980 — expected 'bugfix', got 'change' (title 'Updated default port documentation and removed build-argument guidance')
- real-0fd4ffcc31 — expected 'feature', got 'bugfix' (title 'Fixed must-record sample skip gate in GoldenSetReplayTest')
- real-9a0e90bed5 — expected 'bugfix', got 'feature' (title 'Extended ObservationRepository filtering by memoryScopeId')
- real-63b5502fb9 — expected 'bugfix', got 'feature' (title 'Added one-shot conversation memory scope migration and rehearsal fixtures')
- real-cd6b972cfd — expected 'bugfix', got 'feature' (title 'Added SummaryJobService service implementation in SummaryJobService.java')
- real-46e467bff6 — expected 'bugfix', got 'feature' (title 'Added conversation scope preflight and fixture SQL scripts')
- real-95de35b2ea — expected 'bugfix', got 'change' (title 'Updated byte-buddy pin comment and Docker build context guidance')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| real-f6a1b89e41 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | MISS | 100%/100% | 100% | 50%/100% | 4125 |
| real-1fe4ffb124 | config-change | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | match | 100%/100% | 100% | 50%/100% | 4408 |
| real-d13e2c90cd | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 2713 |
| real-3b4f737562 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 50%/100% | 12661 |
| real-77ba40df77 | refactor | observation | observation | pass | 100% | MISS (subject hit, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 50%/100% | 4587 |
| real-f0d346eab1 | feature | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/100% | 11319 |
| real-3a2d426fdf | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 7209 |
| real-864dadf51b | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 75% | 50%/100% | 5011 |
| real-b064308244 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 18753 |
| real-30a956e462 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 0%/0% | 4380 |
| real-ba86bf2588 | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 33% | 50%/100% | 5254 |
| real-a9c8452967 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 3880 |
| real-db8ac0287e | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 2088 |
| real-9e6612fdf9 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 25% | 0%/0% | 2951 |
| real-f43cfd6f44 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 2995 |
| real-e352ba3da3 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 5947 |
| real-112de33e23 | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 2349 |
| real-28edef5a35 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 4630 |
| real-6d6df2c321 | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 3866 |
| real-d3431639a9 | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 5310 |
| real-7093cb5c56 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 50%/100% | 4879 |
| real-f3d12860ff | security-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 8501 |
| real-3409dda60c | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 3487 |
| real-f7b865efe5 | security-fix | observation | call-failed | FAIL | - | - | - | - | - | - | - | 12665 |
| real-a8cc1754bc | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 40% | 0%/0% | 6315 |
| real-e2c887775a | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 50% | 50%/100% | 10863 |
| real-cf667b4d8b | refactor | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 67% | 0%/0% | 4994 |
| real-a0e77ca4ee | docs-update | observation | call-failed | FAIL | - | - | - | - | - | - | - | 12663 |
| real-d958416fdc | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 80% | 0%/0% | 4833 |
| real-9b424d0d02 | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 4079 |
| real-85480a720e | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | match | 100%/100% | 100% | 0%/0% | 5177 |
| real-c67e7a5ad3 | bug-fix | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 0%/0% | 2612 |
| real-bdf72f2509 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 3371 |
| real-8ac2a32980 | bug-fix | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 0%/0% | 3457 |
| real-2025b33a95 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 763 |
| real-e8c8b314d6 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1001 |
| real-c42d7a9c5d | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1210 |
| real-b5ae725e2a | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1021 |
| real-9261b542ca | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1029 |
| real-7c557c8d79 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1448 |
| real-1dbccd058e | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3320 |
| real-1f9b5d09e7 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 3220 |
| real-ed737be6bb | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 18068 |
| real-b2d48b12a1 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 7251 |
| real-bc3512ae8d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 2754 |
| real-e02e436bdd | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 2929 |
| real-d44a83721d | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 2728 |
| real-0fd4ffcc31 | test-add | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 0%/0% | 5253 |
| real-eee7785ec8 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 50% | 50%/100% | 3863 |
| real-9a0e90bed5 | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 75% | 0%/0% | 5408 |
| real-63b5502fb9 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 0%/0% | 6765 |
| real-d33efbd8ae | security-fix | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 4491 |
| real-cd6b972cfd | security-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 0% | 0%/0% | 3545 |
| real-46e467bff6 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 0%/0% | 3801 |
| real-325c817bbf | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 50% | 50%/100% | 2341 |
| real-7149578a88 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 1941 |
| real-95de35b2ea | bug-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 0%/0% | 3355 |
| real-53851b4a23 | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 5185 |
| real-f4c6765fb9 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 2813 |
| real-67083a7bc1 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 4086 |

## Problems

- real-f6a1b89e41: title lost identifiers [golden-tool-use-real.json] (got 'Expanded real-history golden set to 60 samples with round-10 evidence')
- real-f6a1b89e41: title action [modify] missing: title leads 'null' (got 'Expanded real-history golden set to 60 samples with round-10 evidence')
- real-f6a1b89e41: title verb class 'change' missing (got 'Expanded real-history golden set to 60 samples with round-10 evidence')
- real-f6a1b89e41: title subject [golden-tool-use-real.json] missing (got 'Expanded real-history golden set to 60 samples with round-10 evidence')
- real-f6a1b89e41: title trace-visible subject [golden-tool-use-real.json] missing (got 'Expanded real-history golden set to 60 samples with round-10 evidence')
- real-f6a1b89e41: type 'feature' != expected 'change'
- real-1fe4ffb124: title lost identifiers [extraction-quality-improvement-plan.md] (got 'Raised minimum test count to 472 across test configuration')
- real-1fe4ffb124: title action [document, modify] missing: title leads 'null' (got 'Raised minimum test count to 472 across test configuration')
- real-1fe4ffb124: title verb class 'document' missing (got 'Raised minimum test count to 472 across test configuration')
- real-1fe4ffb124: title subject [extraction-quality-improvement-plan.md] missing (got 'Raised minimum test count to 472 across test configuration')
- real-1fe4ffb124: title trace-visible subject [extraction-quality-improvement-plan.md] missing (got 'Raised minimum test count to 472 across test configuration')
- real-d13e2c90cd: title lost identifiers [2026-11-03_real-history-golden-eval-minimax.md] (got 'Added real-history golden evaluation for MiniMax')
- real-d13e2c90cd: title verb class 'document' missing (got 'Added real-history golden evaluation for MiniMax')
- real-d13e2c90cd: title subject [2026-11-03_real-history-golden-eval-minimax.md] missing (got 'Added real-history golden evaluation for MiniMax')
- real-d13e2c90cd: title trace-visible subject [2026-11-03_real-history-golden-eval-minimax.md] missing (got 'Added real-history golden evaluation for MiniMax')
- real-3b4f737562: title lost identifiers [minimax-after-v9.json] (got 'Updated Minimax v9 evaluation fixtures and scoring')
- real-3b4f737562: title verb class 'document' missing (got 'Updated Minimax v9 evaluation fixtures and scoring')
- real-3b4f737562: title subject [minimax-after-v9.json] missing (got 'Updated Minimax v9 evaluation fixtures and scoring')
- real-3b4f737562: title trace-visible subject [minimax-after-v9.json] missing (got 'Updated Minimax v9 evaluation fixtures and scoring')
- real-3b4f737562: type 'change' != expected 'feature'
- real-77ba40df77: title lost identifiers [ExtractionConsistency] (got 'Added extraction consistency voting and configured tool trace attempts')
- real-77ba40df77: type 'feature' != expected 'refactor'
- real-f0d346eab1: type 'feature' != expected 'change'
- real-3a2d426fdf: title coverage 50% (expected keywords [change, goldensetscorer])
- real-3a2d426fdf: title verb class 'change' missing (got 'Extended GoldenSetScorer with promptTokens and repeat-all evaluation')
- real-3a2d426fdf: type 'feature' != expected 'refactor'
- real-864dadf51b: title coverage 50% (expected keywords [document, goldensetscorer])
- real-864dadf51b: title verb class 'document' missing (got 'Extended GoldenSetScorer evidence handling and legacy metrics')
- real-30a956e462: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a9c8452967: title lost identifiers [SearchSimilarityEvaluator] (got 'Added semantic relevance evaluation harness in backend/src/test')
- real-a9c8452967: title verb class 'document' missing (got 'Added semantic relevance evaluation harness in backend/src/test')
- real-a9c8452967: title subject [SearchSimilarityEvaluator] missing (got 'Added semantic relevance evaluation harness in backend/src/test')
- real-a9c8452967: title trace-visible subject [SearchSimilarityEvaluator] missing (got 'Added semantic relevance evaluation harness in backend/src/test')
- real-db8ac0287e: title lost identifiers [continuation.txt] (got 'Updated defect classification guidance in prompt resources')
- real-db8ac0287e: title verb class 'document' missing (got 'Updated defect classification guidance in prompt resources')
- real-db8ac0287e: title subject [continuation.txt] missing (got 'Updated defect classification guidance in prompt resources')
- real-db8ac0287e: title trace-visible subject [continuation.txt] missing (got 'Updated defect classification guidance in prompt resources')
- real-db8ac0287e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9e6612fdf9: title lost identifiers [continuation.txt] (got 'Updated defect taxonomy and golden-set action rules')
- real-9e6612fdf9: title verb class 'document' missing (got 'Updated defect taxonomy and golden-set action rules')
- real-9e6612fdf9: title subject [continuation.txt] missing (got 'Updated defect taxonomy and golden-set action rules')
- real-9e6612fdf9: title trace-visible subject [continuation.txt] missing (got 'Updated defect taxonomy and golden-set action rules')
- real-9e6612fdf9: type 'change' != expected 'bugfix'
- real-9e6612fdf9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e352ba3da3: title lost identifiers [longcat-after-v7.json] (got 'Added model output quality evaluation fixtures for longcat and minimax')
- real-e352ba3da3: title verb class 'document' missing (got 'Added model output quality evaluation fixtures for longcat and minimax')
- real-e352ba3da3: title subject [longcat-after-v7.json] missing (got 'Added model output quality evaluation fixtures for longcat and minimax')
- real-e352ba3da3: title trace-visible subject [longcat-after-v7.json] missing (got 'Added model output quality evaluation fixtures for longcat and minimax')
- real-112de33e23: title lost identifiers [continuation.txt] (got 'Extended replacement naming guidance in prompt templates')
- real-112de33e23: title verb class 'document' missing (got 'Extended replacement naming guidance in prompt templates')
- real-112de33e23: title subject [continuation.txt] missing (got 'Extended replacement naming guidance in prompt templates')
- real-112de33e23: title trace-visible subject [continuation.txt] missing (got 'Extended replacement naming guidance in prompt templates')
- real-28edef5a35: title lost identifiers [longcat-after-v6.json] (got 'Added MiniMax and LongCat v6 model-output evaluation fixtures')
- real-28edef5a35: title verb class 'document' missing (got 'Added MiniMax and LongCat v6 model-output evaluation fixtures')
- real-28edef5a35: title subject [longcat-after-v6.json] missing (got 'Added MiniMax and LongCat v6 model-output evaluation fixtures')
- real-28edef5a35: title trace-visible subject [longcat-after-v6.json] missing (got 'Added MiniMax and LongCat v6 model-output evaluation fixtures')
- real-6d6df2c321: title lost identifiers [continuation.txt] (got 'Hardened continuation and init prompt skip guidance')
- real-6d6df2c321: title verb class 'document' missing (got 'Hardened continuation and init prompt skip guidance')
- real-6d6df2c321: title subject [continuation.txt] missing (got 'Hardened continuation and init prompt skip guidance')
- real-6d6df2c321: title trace-visible subject [continuation.txt] missing (got 'Hardened continuation and init prompt skip guidance')
- real-6d6df2c321: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d3431639a9: title lost identifiers [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] (got 'Documented Round 4 model output quality evaluation in five files')
- real-d3431639a9: title subject [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] missing (got 'Documented Round 4 model output quality evaluation in five files')
- real-d3431639a9: title trace-visible subject [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] missing (got 'Documented Round 4 model output quality evaluation in five files')
- real-d3431639a9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-7093cb5c56: title lost identifiers [EmbeddingBackfillConcurrencyDatabaseTest] (got 'Added embedding backfill concurrency and sanitization tests')
- real-7093cb5c56: title verb class 'harden' missing (got 'Added embedding backfill concurrency and sanitization tests')
- real-7093cb5c56: title subject [EmbeddingBackfillConcurrencyDatabaseTest] missing (got 'Added embedding backfill concurrency and sanitization tests')
- real-7093cb5c56: title trace-visible subject [EmbeddingBackfillConcurrencyDatabaseTest] missing (got 'Added embedding backfill concurrency and sanitization tests')
- real-7093cb5c56: type 'feature' != expected 'bugfix'
- real-3409dda60c: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-f7b865efe5: call failed: IllegalStateException: all attempts returned no content
- real-a8cc1754bc: title lost identifiers [PendingMessageRepository] (got 'Refactored pending-message deduplication and repository queries')
- real-a8cc1754bc: title verb class 'remove' missing (got 'Refactored pending-message deduplication and repository queries')
- real-a8cc1754bc: title subject [PendingMessageRepository] missing (got 'Refactored pending-message deduplication and repository queries')
- real-a8cc1754bc: title trace-visible subject [PendingMessageRepository] missing (got 'Refactored pending-message deduplication and repository queries')
- real-a8cc1754bc: type 'change' != expected 'bugfix'
- real-a8cc1754bc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e2c887775a: title coverage 50% (expected keywords [remove, agents.md])
- real-e2c887775a: title verb class 'remove' missing (got 'Corrected /api/context/inject updateFiles contract in AGENTS.md')
- real-e2c887775a: type 'change' != expected 'bugfix'
- real-cf667b4d8b: title coverage 50% (expected keywords [remove, observationfeedbackentity])
- real-cf667b4d8b: title verb class 'remove' missing (got 'Updated ObservationFeedbackEntity and repository query documentation')
- real-cf667b4d8b: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a0e77ca4ee: call failed: IllegalStateException: all attempts returned no content
- real-d958416fdc: title lost identifiers [patrol-state.json] (got 'Updated patrol state and API documentation across six files')
- real-d958416fdc: title verb class 'remove' missing (got 'Updated patrol state and API documentation across six files')
- real-d958416fdc: title subject [patrol-state.json] missing (got 'Updated patrol state and API documentation across six files')
- real-d958416fdc: title trace-visible subject [patrol-state.json] missing (got 'Updated patrol state and API documentation across six files')
- real-d958416fdc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9b424d0d02: title coverage 50% (expected keywords [remove, api-zh-cn.md])
- real-9b424d0d02: title verb class 'remove' missing (got 'Documented corrected GET /api/settings defaults in docs/API.md and docs/API-zh-CN.md')
- real-9b424d0d02: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-85480a720e: title lost identifiers [TESTING-zh-CN.md] (got 'Updated database environment variable documentation in docs/TESTING.md')
- real-85480a720e: title action [remove, document] missing: title leads 'modify' (got 'Updated database environment variable documentation in docs/TESTING.md')
- real-85480a720e: title verb class 'remove' missing (got 'Updated database environment variable documentation in docs/TESTING.md')
- real-85480a720e: title subject [TESTING-zh-CN.md] missing (got 'Updated database environment variable documentation in docs/TESTING.md')
- real-85480a720e: title trace-visible subject [TESTING-zh-CN.md] missing (got 'Updated database environment variable documentation in docs/TESTING.md')
- real-85480a720e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-c67e7a5ad3: title lost identifiers [dto.py] (got 'Removed empty else branch and ignored memory/.dreams in .gitignore')
- real-c67e7a5ad3: title subject [dto.py] missing (got 'Removed empty else branch and ignored memory/.dreams in .gitignore')
- real-c67e7a5ad3: title trace-visible subject [dto.py] missing (got 'Removed empty else branch and ignored memory/.dreams in .gitignore')
- real-c67e7a5ad3: type 'change' != expected 'bugfix'
- real-c67e7a5ad3: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-bdf72f2509: type 'change' != expected 'bugfix'
- real-bdf72f2509: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-8ac2a32980: title lost identifiers [Dockerfile] (got 'Updated default port documentation and removed build-argument guidance')
- real-8ac2a32980: title verb class 'remove' missing (got 'Updated default port documentation and removed build-argument guidance')
- real-8ac2a32980: title subject [Dockerfile] missing (got 'Updated default port documentation and removed build-argument guidance')
- real-8ac2a32980: title trace-visible subject [Dockerfile] missing (got 'Updated default port documentation and removed build-argument guidance')
- real-8ac2a32980: type 'change' != expected 'bugfix'
- real-8ac2a32980: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e02e436bdd: title coverage 50% (expected keywords [document, continuation.txt])
- real-e02e436bdd: title verb class 'document' missing (got 'Updated prompt title guidance in continuation.txt and init.txt')
- real-d44a83721d: title lost identifiers [extraction-quality-improvement-plan.md] (got 'Documented Round 5 metric correctness and title ceiling in extraction plan')
- real-d44a83721d: title subject [extraction-quality-improvement-plan.md] missing (got 'Documented Round 5 metric correctness and title ceiling in extraction plan')
- real-d44a83721d: title trace-visible subject [extraction-quality-improvement-plan.md] missing (got 'Documented Round 5 metric correctness and title ceiling in extraction plan')
- real-0fd4ffcc31: title lost identifiers [GoldenSetScorer] (got 'Fixed must-record sample skip gate in GoldenSetReplayTest')
- real-0fd4ffcc31: title verb class 'change' missing (got 'Fixed must-record sample skip gate in GoldenSetReplayTest')
- real-0fd4ffcc31: title subject [GoldenSetScorer] missing (got 'Fixed must-record sample skip gate in GoldenSetReplayTest')
- real-0fd4ffcc31: title trace-visible subject [GoldenSetScorer] missing (got 'Fixed must-record sample skip gate in GoldenSetReplayTest')
- real-0fd4ffcc31: type 'bugfix' != expected 'feature'
- real-0fd4ffcc31: concepts recall 0% (expected [what-changed], got [problem-solution, gotcha])
- real-eee7785ec8: title lost identifiers [2026-11-02_model-output-quality-eval-minimax.md] (got 'Extended GoldenSetEvaluator for Anthropic model evaluation')
- real-eee7785ec8: title verb class 'document' missing (got 'Extended GoldenSetEvaluator for Anthropic model evaluation')
- real-eee7785ec8: title subject [2026-11-02_model-output-quality-eval-minimax.md] missing (got 'Extended GoldenSetEvaluator for Anthropic model evaluation')
- real-eee7785ec8: title trace-visible subject [2026-11-02_model-output-quality-eval-minimax.md] missing (got 'Extended GoldenSetEvaluator for Anthropic model evaluation')
- real-9a0e90bed5: title coverage 50% (expected keywords [harden, observationrepository])
- real-9a0e90bed5: title verb class 'harden' missing (got 'Extended ObservationRepository filtering by memoryScopeId')
- real-9a0e90bed5: type 'feature' != expected 'bugfix'
- real-9a0e90bed5: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-63b5502fb9: title lost identifiers [V20__conversation_memory_scope_expand.sql] (got 'Added one-shot conversation memory scope migration and rehearsal fixtures')
- real-63b5502fb9: title verb class 'harden' missing (got 'Added one-shot conversation memory scope migration and rehearsal fixtures')
- real-63b5502fb9: title subject [V20__conversation_memory_scope_expand.sql] missing (got 'Added one-shot conversation memory scope migration and rehearsal fixtures')
- real-63b5502fb9: title trace-visible subject [V20__conversation_memory_scope_expand.sql] missing (got 'Added one-shot conversation memory scope migration and rehearsal fixtures')
- real-63b5502fb9: type 'feature' != expected 'bugfix'
- real-63b5502fb9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d33efbd8ae: validation rejected the observation: title too long: 110 chars > 100
- real-cd6b972cfd: title coverage 50% (expected keywords [harden, summaryjobservice])
- real-cd6b972cfd: title verb class 'harden' missing (got 'Added SummaryJobService service implementation in SummaryJobService.java')
- real-cd6b972cfd: type 'feature' != expected 'bugfix'
- real-cd6b972cfd: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-46e467bff6: title lost identifiers [conversation-scope-preflight.sql] (got 'Added conversation scope preflight and fixture SQL scripts')
- real-46e467bff6: title verb class 'harden' missing (got 'Added conversation scope preflight and fixture SQL scripts')
- real-46e467bff6: title subject [conversation-scope-preflight.sql] missing (got 'Added conversation scope preflight and fixture SQL scripts')
- real-46e467bff6: title trace-visible subject [conversation-scope-preflight.sql] missing (got 'Added conversation scope preflight and fixture SQL scripts')
- real-46e467bff6: type 'feature' != expected 'bugfix'
- real-46e467bff6: concepts recall 0% (expected [problem-solution], got [pattern, what-changed])
- real-325c817bbf: title lost identifiers [conversation-memory-scope-refactor-plan.md] (got 'Documented Conversation 与 Memory Scope 身份边界重构实施蓝图')
- real-325c817bbf: title subject [conversation-memory-scope-refactor-plan.md] missing (got 'Documented Conversation 与 Memory Scope 身份边界重构实施蓝图')
- real-325c817bbf: title trace-visible subject [conversation-memory-scope-refactor-plan.md] missing (got 'Documented Conversation 与 Memory Scope 身份边界重构实施蓝图')
- real-95de35b2ea: title lost identifiers [pom.xml] (got 'Updated byte-buddy pin comment and Docker build context guidance')
- real-95de35b2ea: title subject [pom.xml] missing (got 'Updated byte-buddy pin comment and Docker build context guidance')
- real-95de35b2ea: title trace-visible subject [pom.xml] missing (got 'Updated byte-buddy pin comment and Docker build context guidance')
- real-95de35b2ea: type 'change' != expected 'bugfix'
- real-95de35b2ea: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-53851b4a23: title lost identifiers [2026-10-08_health-check-history-11.md] (got 'Documented health-check history rounds 312~359+ in archive index')
- real-53851b4a23: title subject [2026-10-08_health-check-history-11.md] missing (got 'Documented health-check history rounds 312~359+ in archive index')
- real-53851b4a23: title trace-visible subject [2026-10-08_health-check-history-11.md] missing (got 'Documented health-check history rounds 312~359+ in archive index')
- real-53851b4a23: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])

## Raw answers behind problems

### real-f6a1b89e41

```text
<observation>   <type>feature</type>   <title>Expanded real-history golden set to 60 samples with round-10 evidence</title>   <subtitle>Golden data, generator, scorer, and tests now capture broader diff coverage and generated-title failures.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json version changed to real-git-2026-11-03-r10.</fact>     <fact>GoldenSe... [truncated]
```

### real-1fe4ffb124

```text
<observation>   <type>change</type>   <title>Raised minimum test count to 472 across test configuration</title>   <subtitle>Local, CI, baseline, and testing documentation now enforce the expanded extraction-quality test suite.</subtitle>   <facts>     <fact>scripts/test-baseline.json changed the total test count from 423 to 472.</fact>     <fact>scripts/verify-local.sh changed VERIFY_MIN_TESTS fro... [truncated]
```

### real-d13e2c90cd

```text
<observation>   <type>feature</type>   <title>Added real-history golden evaluation for MiniMax</title>   <subtitle>Real replay fixtures, metrics, a JUnit test, and archived evaluation documentation were added.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/minimax-real-v9.json contains 36 real golden-set samples.</fact>     <fact>backend/src/test/java/com/ablueforce/cortex... [truncated]
```

### real-3b4f737562

```text
<observation>   <type>change</type>   <title>Updated Minimax v9 evaluation fixtures and scoring</title>   <subtitle>Minimax v9 now includes full output fixtures, metrics, archived evaluation results, and revised golden-set categorization.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/minimax-after-v9.json contains 2096 lines of model output evaluation fixture data.</fact>... [truncated]
```

### real-77ba40df77

```text
<observation>   <type>feature</type>   <title>Added extraction consistency voting and configured tool trace attempts</title>   <subtitle>ExtractionConsistency adds majority voting for tool traces, while application.yml controls voting attempts.</subtitle>   <facts>     <fact>ExtractionConsistency.java was added with 82 lines in backend/src/main/java/com/ablueforce/cortexce/service/ExtractionConsis... [truncated]
```

### real-f0d346eab1

```text
<observation>   <type>feature</type>   <title>Added real-history golden tool-use evaluation dataset in golden-tool-use-real.json</title>   <subtitle>Mechanically generated real-history cases now support dedicated model-quality evaluation runs.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json contains 1097 lines of real-history golden tool-use evaluation data... [truncated]
```

### real-3a2d426fdf

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetScorer with promptTokens and repeat-all evaluation</title>   <subtitle>Golden-set evaluation now records prompt token counts and can repeat every sample for run-to-run measurements.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java adds a public final int promptTokens field and expa... [truncated]
```

### real-864dadf51b

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetScorer evidence handling and legacy metrics</title>   <subtitle>Golden-set scoring now handles structured tool evidence and labels the legacy title-subject metric.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java was expanded by 70 lines and replaced tool-name-only evidence constru... [truncated]
```

### real-30a956e462

```text
<observation>   <type>change</type>   <title>Documented Round 8 extraction quality improvements in extraction-quality-improvement-plan.md</title>   <subtitle>Added a Round 8 plan covering insertion scope, coverage removal, and a reverted security verb.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md contains the heading "### 7.14 Round 8 — insertion-scope contract... [truncated]
```

### real-a9c8452967

```text
<observation>   <type>feature</type>   <title>Added semantic relevance evaluation harness in backend/src/test</title>   <subtitle>Added a replayable search similarity evaluator, fixtures, corpus, metrics, and archived evaluation documentation.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/SearchSimilarityEvaluator.java defines a 411-line semantic similarity eval... [truncated]
```

### real-db8ac0287e

```text
<observation>   <type>change</type>   <title>Updated defect classification guidance in prompt resources</title>   <subtitle>Prompt templates now classify all reported defects as Fixed without limiting the definition to non-security code.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt now defines Fixed as “a defect already produced wrong or failing behaviour.”</f... [truncated]
```

### real-9e6612fdf9

```text
<observation>   <type>change</type>   <title>Updated defect taxonomy and golden-set action rules</title>   <subtitle>Prompts now distinguish non-security defects, while golden-set scoring recognizes extend actions.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt defines “Fixed” as a defect producing wrong or failing behaviour “in non-security code”.</fact>     <f... [truncated]
```

### real-e352ba3da3

```text
<observation>   <type>feature</type>   <title>Added model output quality evaluation fixtures for longcat and minimax</title>   <subtitle>Version 7 JSON fixtures, metrics, and archived evaluation reports now cover both model output sets.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v7.json contains 656 lines of model output evaluation fixture data.</fact>   ... [truncated]
```

### real-112de33e23

```text
<observation>   <type>change</type>   <title>Extended replacement naming guidance in prompt templates</title>   <subtitle>Both prompt templates now require retaining the replaced key and its new file.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt includes the rule “A replacement keeps BOTH names”.</fact>     <fact>backend/src/main/resources/prompts/init.txt inc... [truncated]
```

### real-28edef5a35

```text
<observation>   <type>feature</type>   <title>Added MiniMax and LongCat v6 model-output evaluation fixtures</title>   <subtitle>Archived v6 evaluations now include transcript fixtures, metrics, and Markdown reports for both models.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/minimax-after-v6.json contains 656 lines of MiniMax v6 model-output evaluation data.</fact>     ... [truncated]
```

### real-6d6df2c321

```text
<observation>   <type>change</type>   <title>Hardened continuation and init prompt skip guidance</title>   <subtitle>Prompt templates now explicitly classify identifier searches as non-recordable exploration.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt adds guidance excluding identifier usage or definition searches from recorded observations.</fact>     <fact... [truncated]
```

### real-d3431639a9

```text
<observation>   <type>change</type>   <title>Documented Round 4 model output quality evaluation in five files</title>   <subtitle>Archived evaluations and the extraction plan now capture contract-title, summary-grounding, retrieval, and repair-claim results.</subtitle>   <facts>     <fact>docs/archive/2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md contains 598 added lines beginn... [truncated]
```

### real-7093cb5c56

```text
<observation>   <type>feature</type>   <title>Added embedding backfill concurrency and sanitization tests</title>   <subtitle>Database and service tests now cover concurrent backfills, isolated row failures, and sanitized sensitive output.</subtitle>   <facts>     <fact>EmbeddingBackfillConcurrencyDatabaseTest.java adds 333 lines covering repository-level embedding backfill concurrency.</fact>    ... [truncated]
```

### real-3409dda60c

```text
<observation>   <type>change</type>   <title>Documented implementation commit range in conversation-memory-scope-refactor-plan.md</title>   <subtitle>The refactor plan now identifies nine implemented commits and adds supporting detail.</subtitle>   <facts>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md sets the implementation commit range to `325c817..f7b865e`.</fact>     <fact>d... [truncated]
```

### real-f7b865efe5

```text
(no raw answer: the call failed before returning)
```

### real-a8cc1754bc

```text
<observation>   <type>change</type>   <title>Refactored pending-message deduplication and repository queries</title>   <subtitle>Pending-message deduplication now uses conversation scope, while repository lookups were aligned with current entity fields.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/repository/PendingMessageRepository.java removed the comment describi... [truncated]
```

### real-e2c887775a

```text
<observation>   <type>change</type>   <title>Corrected /api/context/inject updateFiles contract in AGENTS.md</title>   <subtitle>The endpoint contract now names the route that actually returns updateFiles.</subtitle>   <facts>     <fact>AGENTS.md maps `/api/context/inject` to `updateFiles` in `proxy/proxy.js`.</fact>     <fact>AGENTS.md no longer maps `/api/context/generate` to `updateFiles`.</fac... [truncated]
```

### real-cf667b4d8b

```text
<observation>   <type>refactor</type>   <title>Updated ObservationFeedbackEntity and repository query documentation</title>   <subtitle>Observation feedback repository now documents newest-first ordering, with entity documentation formatting adjusted.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/entity/ObservationFeedbackEntity.java removed 10 lines and added 9 line... [truncated]
```

### real-a0e77ca4ee

```text
(no raw answer: the call failed before returning)
```

### real-d958416fdc

```text
<observation>   <type>change</type>   <title>Updated patrol state and API documentation across six files</title>   <subtitle>Patrol records now mark Backend as completed, and API docs align with backend endpoints.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json sets "lastDirection" to "backend" instead of "demo".</fact>     <fact>docs/drafts/patrol-rotation.md records "Last completed:... [truncated]
```

### real-9b424d0d02

```text
<observation>   <type>change</type>   <title>Documented corrected GET /api/settings defaults in docs/API.md and docs/API-zh-CN.md</title>   <subtitle>API changelogs now align six settings examples with AppSettings.toMap() actual defaults.</subtitle>   <facts>     <fact>docs/API.md records version 0.1.0-beta+42 dated 2026-05-06 for corrected GET /api/settings example values.</fact>     <fact>docs/A... [truncated]
```

### real-85480a720e

```text
<observation>   <type>change</type>   <title>Updated database environment variable documentation in docs/TESTING.md</title>   <subtitle>Testing guides now list DB_NAME with the default claude_mem instead of DB_HOST.</subtitle>   <facts>     <fact>docs/TESTING.md documents DB_NAME with default value claude_mem and description Database name.</fact>     <fact>docs/TESTING-zh-CN.md documents DB_NAME w... [truncated]
```

### real-c67e7a5ad3

```text
<observation>   <type>change</type>   <title>Removed empty else branch and ignored memory/.dreams in .gitignore</title>   <subtitle>Cleaned up DTO control flow and excluded generated dream artifacts from version control.</subtitle>   <facts>     <fact>python-sdk/cortex-mem-python/cortex_mem/dto.py removed an empty else branch containing no statements.</fact>     <fact>.gitignore now includes memor... [truncated]
```

### real-bdf72f2509

```text
<observation>   <type>change</type>   <title>Removed SDK version comment in CortexMemClientImpl.java</title>   <subtitle>Deleted a stale User-Agent SDK version maintenance comment.</subtitle>   <facts>     <fact>cortex-mem-spring-integration/cortex-mem-client/src/main/java/com/ablueforce/cortexce/client/CortexMemClientImpl.java removed the comment `/** SDK version for User-Agent header. Update whe... [truncated]
```

### real-8ac2a32980

```text
<observation>   <type>change</type>   <title>Updated default port documentation and removed build-argument guidance</title>   <subtitle>Deployment docs now omit build arguments, while the Dockerfile identifies port 37777 as the default.</subtitle>   <facts>     <fact>backend/Dockerfile documents 37777 as the default port overridable by the SERVER_PORT environment variable.</fact>     <fact>docs/DE... [truncated]
```

### real-e02e436bdd

```text
<observation>   <type>change</type>   <title>Updated prompt title guidance in continuation.txt and init.txt</title>   <subtitle>Prompt templates now require category-specific verb, object, identifier, and symptom formatting.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt replaced the `Fixed UserCache.computeIfAbsent null placeholder race` example with category-s... [truncated]
```

### real-d44a83721d

```text
<observation>   <type>change</type>   <title>Documented Round 5 metric correctness and title ceiling in extraction plan</title>   <subtitle>Added a 99-line Round 5 section to the extraction quality improvement plan.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md added 99 lines.</fact>     <fact>docs/drafts/extraction-quality-improvement-plan.md contains the headi... [truncated]
```

### real-0fd4ffcc31

```text
<observation>   <type>bugfix</type>   <title>Fixed must-record sample skip gate in GoldenSetReplayTest</title>   <subtitle>Golden-set evaluation now checks false-positive skips instead of false-negative skips for record-worthy samples.</subtitle>   <facts>     <fact>GoldenSetEvaluator now defines skipFp as a record-worthy sample being skipped and skipFn as a non-record-worthy sample being skipped.... [truncated]
```

### real-eee7785ec8

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetEvaluator for Anthropic model evaluation</title>   <subtitle>Golden-set evaluation now supports Anthropic alongside the existing provider configuration.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetEvaluator.java imports org.springframework.ai.anthropic.AnthropicChatModel.</fact>     <fact... [truncated]
```

### real-9a0e90bed5

```text
<observation>   <type>feature</type>   <title>Extended ObservationRepository filtering by memoryScopeId</title>   <subtitle>Observation queries now support memory-scope membership while preserving project filtering.</subtitle>   <facts>     <fact>ObservationRepository.java replaces the project-only predicate with a memoryScopeId IN filter for :project-scoped queries.</fact>     <fact>MemoryScopeEn... [truncated]
```

### real-63b5502fb9

```text
<observation>   <type>feature</type>   <title>Added one-shot conversation memory scope migration and rehearsal fixtures</title>   <subtitle>V20 establishes conversation and memory scope identity, with database rehearsal and conflict fixtures.</subtitle>   <facts>     <fact>backend/src/main/resources/db/migration/V20__conversation_memory_scope_expand.sql now defines a one-shot Conversation / Memory... [truncated]
```

### real-d33efbd8ae

```text
<observation>   <type>feature</type>   <title>Added conversation scope backfill migration toolkit in backend/src/main/java/com/ablueforce/cortexce/migration</title>   <subtitle>Introduced classifier, runner, CLI entry point, and tests for conversation-scope backfill processing.</subtitle>   <facts>     <fact>ConversationScopeBackfillRunner.java contains 845 lines under package com.ablueforce.corte... [truncated]
```

### real-cd6b972cfd

```text
<observation>   <type>feature</type>   <title>Added SummaryJobService service implementation in SummaryJobService.java</title>   <subtitle>Summary job processing now has a dedicated service implementation under the backend service package.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/service/SummaryJobService.java declares package com.ablueforce.cortexce.service.</f... [truncated]
```

### real-46e467bff6

```text
<observation>   <type>feature</type>   <title>Added conversation scope preflight and fixture SQL scripts</title>   <subtitle>Added additive database scripts for conversation and memory scope identity expansion.</subtitle>   <facts>     <fact>scripts/db/conversation-scope-preflight.sql contains 426 lines beginning with `-- conversation-scope-preflight.sql`.</fact>     <fact>backend/src/main/resourc... [truncated]
```

### real-325c817bbf

```text
<observation>   <type>change</type>   <title>Documented Conversation 与 Memory Scope 身份边界重构实施蓝图</title>   <subtitle>新增 Conversation 与 Memory Scope 身份边界重构的详细实施蓝图，共 917 行。</subtitle>   <facts>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md 新增标题“# Conversation 与 Memory Scope 身份边界重构实施蓝图”。</fact>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md 本次编辑新增 917 行，未删除既有行。</fac... [truncated]
```

### real-95de35b2ea

```text
<observation>   <type>change</type>   <title>Updated byte-buddy pin comment and Docker build context guidance</title>   <subtitle>Dependency documentation now identifies Mockito 5.11 as the byte-buddy source, and E2E guidance names repository-root build context.</subtitle>   <facts>     <fact>backend/pom.xml pins byte-buddy to 1.15.x for Mockito 5.11 via the spring-boot-starter-test BOM.</fact>   ... [truncated]
```

### real-53851b4a23

```text
<observation>   <type>change</type>   <title>Documented health-check history rounds 312~359+ in archive index</title>   <subtitle>Archived 1,206 lines of October 6–7 health-check history and indexed the new archive.</subtitle>   <facts>     <fact>docs/archive/2026-10-08_health-check-history-11.md contains 1,206 lines covering rounds 312~359+ from 2026-10-06 to 2026-10-07.</fact>     <fact>docs/arc... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
