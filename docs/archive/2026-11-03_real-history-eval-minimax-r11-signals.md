golden set: real (60 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: current
- Provider: anthropic, model `MiniMax-M3`, base URL `replay`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 60 unique; runs scored: 1; key repeat: 1; real calls: 0 (cap none); duration: 84 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 97% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 10/0/0/50 |
| format errors | 0 |
| validation rejections | 0 |
| call failures | 2 |
| title exact-hit rate (legacy) | 35% |
| title normalized hit rate (legacy) | 35% |
| title action-class hit rate (A6) | 98% (48 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 60% |
| title subject hit rate (legacy, diagnostic) | 56% |
| title trace-visible subject hit rate (A6b) | 56% (48 scored, 0 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 54% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 54% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 38% |
| title identifier hit rate | 56% |
| type accuracy (raw) | 79% |
| type accuracy (auditable labels only) | 84% (5 unverifiable label(s) excluded) |
| facts keyword precision (legacy) / recall | 100% / 100% |
| facts informative precision | 88% |
| facts trace-support rate | 100% |
| concepts precision / recall | 32% / 65% |
| skip consistency across repeats | 0% (0 repeated samples) |
| latency p50 / p95 / max (ms) | 4031 / 9888 / 25908 |
| tokens p50 / p95 / max / mean per call | 4603 / 5173 / 5235 / 4546 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 88% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 98% | PASS |
| A6b | title trace-visible subject | >= 90% | 56% | **FAIL** |
| A6c | title identifiers | = 100% | 56% | **FAIL** |
| A6 | all three title dimensions together | all pass | 54% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 58 |
| exception | 2 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 60 | 100% | 35% | 54% | 100% | 88% | 100% | 0 |

- average: skip 100%, title 35%, title contract 54%, facts P/R 100%/100%, facts actionable 88%
- worst run: skip 100%, title 35%, title contract 54%, facts P/R 100%/100%, facts actionable 88%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| feature | 12 | 11/12 | 10/12 | 1/12 |
| config-change | 1 | 1/1 | 1/1 | 1/1 |
| refactor | 2 | 2/2 | 0/2 | 0/2 |
| docs-update | 20 | 20/20 | 20/20 | 9/20 |
| test-add | 2 | 2/2 | 2/2 | 1/2 |
| security-fix | 7 | 7/7 | 3/7 | 4/7 |
| bug-fix | 6 | 5/6 | 2/6 | 1/6 |
| diagnose | 10 | 10/10 | 0/10 | 0/10 |

## Unverifiable type labels (excluded from the auditable type accuracy)

5 sample(s) expect a type the recorded trace cannot evidence:

- real-e2c887775a: expected bugfix but the trace shows no defect or security evidence
- real-c67e7a5ad3: expected bugfix but the trace shows no defect or security evidence
- real-bdf72f2509: expected bugfix but the trace shows no defect or security evidence
- real-8ac2a32980: expected bugfix but the trace shows no defect or security evidence
- real-95de35b2ea: expected bugfix but the trace shows no defect or security evidence

## Failure clusters (first run)

| cluster | misses | share of scored (48) |
|---|---|---|
| identifier missing | 21 | 44% |
| action class wrong | 1 | 2% |
| subject wrong | 21 | 44% |
| type wrong | 10 | 21% |

### identifier missing (21)

- real-f6a1b89e41 — expected identifiers [golden-tool-use-real.json], title 'Updated real tool-use golden set to real-git-2026-11-03-r10'
- real-d13e2c90cd — expected identifiers [2026-11-03_real-history-golden-eval-minimax.md], title 'Added real-history golden evaluation fixture and replay test'
- real-3b4f737562 — expected identifiers [minimax-after-v9.json], title 'Added MiniMax output-quality evaluation fixtures and metrics'
- real-f0d346eab1 — expected identifiers [golden-tool-use-real.json], title 'Added mechanically generated real-history evaluation suite'
- real-30a956e462 — expected identifiers [extraction-quality-improvement-plan.md], title 'Documented Round 8 insertion-scope contract in extraction quality plan'
- real-e352ba3da3 — expected identifiers [longcat-after-v7.json], title 'Added MiniMax and LongCat v7 model quality evaluation fixtures'
- real-112de33e23 — expected identifiers [continuation.txt], title 'Documented replacement identifier rule in prompt resources'
- real-28edef5a35 — expected identifiers [longcat-after-v6.json], title 'Added after-v6 model quality evaluation fixtures and reports'
- real-d3431639a9 — expected identifiers [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md], title 'Documented Round 4 model output quality evaluation in five evaluation docs'
- real-7093cb5c56 — expected identifiers [EmbeddingBackfillConcurrencyDatabaseTest], title 'Hardened embedding backfill concurrency and log sanitization'
- real-f3d12860ff — expected identifiers [LogsControllerTest], title 'Hardened LogsController log retrieval with sanitized file paths'
- real-d958416fdc — expected identifiers [patrol-state.json], title 'Updated patrol tracking and API documentation in docs/drafts and backend/README.md'
- real-9b424d0d02 — expected identifiers [API-zh-CN.md], title 'Documented GET /api/settings default corrections in docs/API.md'
- real-85480a720e — expected identifiers [TESTING-zh-CN.md], title 'Documented DB_NAME default claude_mem in testing guides'
- real-8ac2a32980 — expected identifiers [Dockerfile], title 'Updated Docker default port comment and removed deployment build-argument sections'
- real-bc3512ae8d — expected identifiers [extraction-quality-improvement-plan.md], title 'Documented Round 6 category few-shots and title ceiling in extraction plan'
- real-d44a83721d — expected identifiers [extraction-quality-improvement-plan.md], title 'Documented Round 5 metric correctness and title ceiling in extraction plan'
- real-eee7785ec8 — expected identifiers [2026-11-02_model-output-quality-eval-minimax.md], title 'Extended model output quality evaluator for Anthropic in GoldenSetEvaluator'
- real-d33efbd8ae — expected identifiers [ConversationScopeBackfillRunner], title 'Added conversation scope backfill and technical bucket classification'
- real-46e467bff6 — expected identifiers [conversation-scope-preflight.sql], title 'Hardened conversation and memory scope identity in V20 migration'
- real-325c817bbf — expected identifiers [conversation-memory-scope-refactor-plan.md], title 'Documented Conversation and Memory Scope identity boundary refactor plan'

### action class wrong (1)

- real-0fd4ffcc31 — expected [modify], got 'create' from title 'Added false-positive skip tracking to GoldenSetScorer'

### subject wrong (21)

- real-f6a1b89e41 — expected subject tokens [golden-tool-use-real.json], title 'Updated real tool-use golden set to real-git-2026-11-03-r10'
- real-d13e2c90cd — expected subject tokens [2026-11-03_real-history-golden-eval-minimax.md], title 'Added real-history golden evaluation fixture and replay test'
- real-3b4f737562 — expected subject tokens [minimax-after-v9.json], title 'Added MiniMax output-quality evaluation fixtures and metrics'
- real-f0d346eab1 — expected subject tokens [golden-tool-use-real.json], title 'Added mechanically generated real-history evaluation suite'
- real-30a956e462 — expected subject tokens [extraction-quality-improvement-plan.md], title 'Documented Round 8 insertion-scope contract in extraction quality plan'
- real-e352ba3da3 — expected subject tokens [longcat-after-v7.json], title 'Added MiniMax and LongCat v7 model quality evaluation fixtures'
- real-112de33e23 — expected subject tokens [continuation.txt], title 'Documented replacement identifier rule in prompt resources'
- real-28edef5a35 — expected subject tokens [longcat-after-v6.json], title 'Added after-v6 model quality evaluation fixtures and reports'
- real-d3431639a9 — expected subject tokens [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md], title 'Documented Round 4 model output quality evaluation in five evaluation docs'
- real-7093cb5c56 — expected subject tokens [EmbeddingBackfillConcurrencyDatabaseTest], title 'Hardened embedding backfill concurrency and log sanitization'
- real-f3d12860ff — expected subject tokens [LogsControllerTest], title 'Hardened LogsController log retrieval with sanitized file paths'
- real-d958416fdc — expected subject tokens [patrol-state.json], title 'Updated patrol tracking and API documentation in docs/drafts and backend/README.md'
- real-9b424d0d02 — expected subject tokens [API-zh-CN.md], title 'Documented GET /api/settings default corrections in docs/API.md'
- real-85480a720e — expected subject tokens [TESTING-zh-CN.md], title 'Documented DB_NAME default claude_mem in testing guides'
- real-8ac2a32980 — expected subject tokens [Dockerfile], title 'Updated Docker default port comment and removed deployment build-argument sections'
- real-bc3512ae8d — expected subject tokens [extraction-quality-improvement-plan.md], title 'Documented Round 6 category few-shots and title ceiling in extraction plan'
- real-d44a83721d — expected subject tokens [extraction-quality-improvement-plan.md], title 'Documented Round 5 metric correctness and title ceiling in extraction plan'
- real-eee7785ec8 — expected subject tokens [2026-11-02_model-output-quality-eval-minimax.md], title 'Extended model output quality evaluator for Anthropic in GoldenSetEvaluator'
- real-d33efbd8ae — expected subject tokens [ConversationScopeBackfillRunner], title 'Added conversation scope backfill and technical bucket classification'
- real-46e467bff6 — expected subject tokens [conversation-scope-preflight.sql], title 'Hardened conversation and memory scope identity in V20 migration'
- real-325c817bbf — expected subject tokens [conversation-memory-scope-refactor-plan.md], title 'Documented Conversation and Memory Scope identity boundary refactor plan'

### type wrong (10)

- real-f0d346eab1 — expected 'change', got 'feature' (title 'Added mechanically generated real-history evaluation suite')
- real-3a2d426fdf — expected 'change', got 'feature' (title 'Extended GoldenSetScorer with prompt token metrics in GoldenSetScorer.java')
- real-9e6612fdf9 — expected 'feature', got 'change' (title 'Documented non-security scope for Fixed in backend/src/main/resources/prompts/continuation.txt')
- real-f3d12860ff — expected 'change', got 'bugfix' (title 'Hardened LogsController log retrieval with sanitized file paths')
- real-f7b865efe5 — expected 'change', got 'bugfix' (title 'Hardened authentication in backend/src/main/resources/static/index.html')
- real-bdf72f2509 — expected 'bugfix', got 'refactor' (title 'Removed SDK version comment from CortexMemClientImpl')
- real-8ac2a32980 — expected 'bugfix', got 'change' (title 'Updated Docker default port comment and removed deployment build-argument sections')
- real-9a0e90bed5 — expected 'refactor', got 'bugfix' (title 'Fixed ObservationRepository project filtering with memory scopes')
- real-46e467bff6 — expected 'feature', got 'bugfix' (title 'Hardened conversation and memory scope identity in V20 migration')
- real-95de35b2ea — expected 'bugfix', got 'change' (title 'Updated byte-buddy dependency pin in backend/pom.xml')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| real-f6a1b89e41 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 4424 |
| real-1fe4ffb124 | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 6285 |
| real-d13e2c90cd | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 4507 |
| real-3b4f737562 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 4657 |
| real-77ba40df77 | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 5314 |
| real-f0d346eab1 | feature | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 50%/100% | 8849 |
| real-3a2d426fdf | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 2356 |
| real-864dadf51b | feature | observation | call-failed | FAIL | - | - | - | - | - | - | - | 24618 |
| real-b064308244 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 3720 |
| real-30a956e462 | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 9888 |
| real-ba86bf2588 | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/100% | 4295 |
| real-a9c8452967 | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 4748 |
| real-db8ac0287e | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 0%/0% | 2839 |
| real-9e6612fdf9 | security-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 25% | 0%/0% | 3142 |
| real-f43cfd6f44 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 4299 |
| real-e352ba3da3 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 5064 |
| real-112de33e23 | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 50% | 50%/100% | 17822 |
| real-28edef5a35 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 4031 |
| real-6d6df2c321 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 6324 |
| real-d3431639a9 | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 4438 |
| real-7093cb5c56 | security-fix | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 7154 |
| real-f3d12860ff | security-fix | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 50% | 50%/100% | 4190 |
| real-3409dda60c | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 0%/0% | 2343 |
| real-f7b865efe5 | security-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/100% | 2359 |
| real-a8cc1754bc | security-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 80% | 50%/100% | 3041 |
| real-e2c887775a | bug-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 50% | 50%/100% | 5084 |
| real-cf667b4d8b | bug-fix | observation | call-failed | FAIL | - | - | - | - | - | - | - | 25908 |
| real-a0e77ca4ee | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 33% | 0%/0% | 4317 |
| real-d958416fdc | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 80% | 0%/0% | 4021 |
| real-9b424d0d02 | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 67% | 0%/0% | 6566 |
| real-85480a720e | docs-update | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 6127 |
| real-c67e7a5ad3 | bug-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/100% | 3301 |
| real-bdf72f2509 | bug-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 8782 |
| real-8ac2a32980 | bug-fix | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 0%/0% | 3033 |
| real-2025b33a95 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1023 |
| real-e8c8b314d6 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1438 |
| real-c42d7a9c5d | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 855 |
| real-b5ae725e2a | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 872 |
| real-9261b542ca | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 813 |
| real-7c557c8d79 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1056 |
| real-1dbccd058e | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1325 |
| real-1f9b5d09e7 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 803 |
| real-ed737be6bb | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 2412 |
| real-b2d48b12a1 | diagnose | skip | skip | pass | - | - | - | - | - | - | - | 1355 |
| real-bc3512ae8d | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 4043 |
| real-e02e436bdd | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 2296 |
| real-d44a83721d | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 2910 |
| real-0fd4ffcc31 | test-add | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 67% | 0%/0% | 3981 |
| real-eee7785ec8 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 4502 |
| real-9a0e90bed5 | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 6257 |
| real-63b5502fb9 | security-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 8814 |
| real-d33efbd8ae | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 4157 |
| real-cd6b972cfd | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 67% | 0%/0% | 3285 |
| real-46e467bff6 | security-fix | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | MISS | 100%/100% | 100% | 50%/100% | 3370 |
| real-325c817bbf | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 67% | 50%/100% | 3577 |
| real-7149578a88 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 4390 |
| real-95de35b2ea | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 2883 |
| real-53851b4a23 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 4526 |
| real-f4c6765fb9 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/100% | 2842 |
| real-67083a7bc1 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 2312 |

## Problems

- real-f6a1b89e41: title lost identifiers [golden-tool-use-real.json] (got 'Updated real tool-use golden set to real-git-2026-11-03-r10')
- real-f6a1b89e41: title subject [golden-tool-use-real.json] missing (got 'Updated real tool-use golden set to real-git-2026-11-03-r10')
- real-f6a1b89e41: title trace-visible subject [golden-tool-use-real.json] missing (got 'Updated real tool-use golden set to real-git-2026-11-03-r10')
- real-d13e2c90cd: title lost identifiers [2026-11-03_real-history-golden-eval-minimax.md] (got 'Added real-history golden evaluation fixture and replay test')
- real-d13e2c90cd: title verb class 'document' missing (got 'Added real-history golden evaluation fixture and replay test')
- real-d13e2c90cd: title subject [2026-11-03_real-history-golden-eval-minimax.md] missing (got 'Added real-history golden evaluation fixture and replay test')
- real-d13e2c90cd: title trace-visible subject [2026-11-03_real-history-golden-eval-minimax.md] missing (got 'Added real-history golden evaluation fixture and replay test')
- real-3b4f737562: title lost identifiers [minimax-after-v9.json] (got 'Added MiniMax output-quality evaluation fixtures and metrics')
- real-3b4f737562: title verb class 'document' missing (got 'Added MiniMax output-quality evaluation fixtures and metrics')
- real-3b4f737562: title subject [minimax-after-v9.json] missing (got 'Added MiniMax output-quality evaluation fixtures and metrics')
- real-3b4f737562: title trace-visible subject [minimax-after-v9.json] missing (got 'Added MiniMax output-quality evaluation fixtures and metrics')
- real-f0d346eab1: title lost identifiers [golden-tool-use-real.json] (got 'Added mechanically generated real-history evaluation suite')
- real-f0d346eab1: title subject [golden-tool-use-real.json] missing (got 'Added mechanically generated real-history evaluation suite')
- real-f0d346eab1: title trace-visible subject [golden-tool-use-real.json] missing (got 'Added mechanically generated real-history evaluation suite')
- real-f0d346eab1: type 'feature' != expected 'change'
- real-3a2d426fdf: title coverage 50% (expected keywords [change, goldensetscorer])
- real-3a2d426fdf: title verb class 'change' missing (got 'Extended GoldenSetScorer with prompt token metrics in GoldenSetScorer.java')
- real-3a2d426fdf: type 'feature' != expected 'change'
- real-864dadf51b: call failed: IllegalStateException: all attempts returned no content
- real-30a956e462: title lost identifiers [extraction-quality-improvement-plan.md] (got 'Documented Round 8 insertion-scope contract in extraction quality plan')
- real-30a956e462: title subject [extraction-quality-improvement-plan.md] missing (got 'Documented Round 8 insertion-scope contract in extraction quality plan')
- real-30a956e462: title trace-visible subject [extraction-quality-improvement-plan.md] missing (got 'Documented Round 8 insertion-scope contract in extraction quality plan')
- real-30a956e462: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-a9c8452967: title coverage 50% (expected keywords [document, searchsimilarityevaluator])
- real-a9c8452967: title verb class 'document' missing (got 'Added semantic relevance floor evaluation in SearchSimilarityEvaluator')
- real-db8ac0287e: title coverage 50% (expected keywords [document, continuation.txt])
- real-db8ac0287e: title verb class 'document' missing (got 'Updated defect classification wording in continuation.txt and init.txt')
- real-db8ac0287e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9e6612fdf9: type 'change' != expected 'feature'
- real-9e6612fdf9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-e352ba3da3: title lost identifiers [longcat-after-v7.json] (got 'Added MiniMax and LongCat v7 model quality evaluation fixtures')
- real-e352ba3da3: title verb class 'document' missing (got 'Added MiniMax and LongCat v7 model quality evaluation fixtures')
- real-e352ba3da3: title subject [longcat-after-v7.json] missing (got 'Added MiniMax and LongCat v7 model quality evaluation fixtures')
- real-e352ba3da3: title trace-visible subject [longcat-after-v7.json] missing (got 'Added MiniMax and LongCat v7 model quality evaluation fixtures')
- real-112de33e23: title lost identifiers [continuation.txt] (got 'Documented replacement identifier rule in prompt resources')
- real-112de33e23: title subject [continuation.txt] missing (got 'Documented replacement identifier rule in prompt resources')
- real-112de33e23: title trace-visible subject [continuation.txt] missing (got 'Documented replacement identifier rule in prompt resources')
- real-28edef5a35: title lost identifiers [longcat-after-v6.json] (got 'Added after-v6 model quality evaluation fixtures and reports')
- real-28edef5a35: title verb class 'document' missing (got 'Added after-v6 model quality evaluation fixtures and reports')
- real-28edef5a35: title subject [longcat-after-v6.json] missing (got 'Added after-v6 model quality evaluation fixtures and reports')
- real-28edef5a35: title trace-visible subject [longcat-after-v6.json] missing (got 'Added after-v6 model quality evaluation fixtures and reports')
- real-6d6df2c321: concepts recall 0% (expected [problem-solution], got [pattern, what-changed])
- real-d3431639a9: title lost identifiers [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] (got 'Documented Round 4 model output quality evaluation in five evaluation docs')
- real-d3431639a9: title subject [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] missing (got 'Documented Round 4 model output quality evaluation in five evaluation docs')
- real-d3431639a9: title trace-visible subject [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] missing (got 'Documented Round 4 model output quality evaluation in five evaluation docs')
- real-d3431639a9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-7093cb5c56: title lost identifiers [EmbeddingBackfillConcurrencyDatabaseTest] (got 'Hardened embedding backfill concurrency and log sanitization')
- real-7093cb5c56: title subject [EmbeddingBackfillConcurrencyDatabaseTest] missing (got 'Hardened embedding backfill concurrency and log sanitization')
- real-7093cb5c56: title trace-visible subject [EmbeddingBackfillConcurrencyDatabaseTest] missing (got 'Hardened embedding backfill concurrency and log sanitization')
- real-f3d12860ff: title lost identifiers [LogsControllerTest] (got 'Hardened LogsController log retrieval with sanitized file paths')
- real-f3d12860ff: title subject [LogsControllerTest] missing (got 'Hardened LogsController log retrieval with sanitized file paths')
- real-f3d12860ff: title trace-visible subject [LogsControllerTest] missing (got 'Hardened LogsController log retrieval with sanitized file paths')
- real-f3d12860ff: type 'bugfix' != expected 'change'
- real-3409dda60c: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-f7b865efe5: type 'bugfix' != expected 'change'
- real-e2c887775a: title coverage 50% (expected keywords [remove, agents.md])
- real-e2c887775a: title verb class 'remove' missing (got 'Fixed endpoint mapping in AGENTS.md')
- real-cf667b4d8b: call failed: IllegalStateException: all attempts returned no content
- real-a0e77ca4ee: title coverage 50% (expected keywords [remove, testing.md])
- real-a0e77ca4ee: title verb class 'remove' missing (got 'Documented testing table cleanup in docs/TESTING.md and Chinese counterpart')
- real-a0e77ca4ee: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d958416fdc: title lost identifiers [patrol-state.json] (got 'Updated patrol tracking and API documentation in docs/drafts and backend/README.md')
- real-d958416fdc: title verb class 'remove' missing (got 'Updated patrol tracking and API documentation in docs/drafts and backend/README.md')
- real-d958416fdc: title subject [patrol-state.json] missing (got 'Updated patrol tracking and API documentation in docs/drafts and backend/README.md')
- real-d958416fdc: title trace-visible subject [patrol-state.json] missing (got 'Updated patrol tracking and API documentation in docs/drafts and backend/README.md')
- real-d958416fdc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9b424d0d02: title lost identifiers [API-zh-CN.md] (got 'Documented GET /api/settings default corrections in docs/API.md')
- real-9b424d0d02: title verb class 'remove' missing (got 'Documented GET /api/settings default corrections in docs/API.md')
- real-9b424d0d02: title subject [API-zh-CN.md] missing (got 'Documented GET /api/settings default corrections in docs/API.md')
- real-9b424d0d02: title trace-visible subject [API-zh-CN.md] missing (got 'Documented GET /api/settings default corrections in docs/API.md')
- real-9b424d0d02: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-85480a720e: title lost identifiers [TESTING-zh-CN.md] (got 'Documented DB_NAME default claude_mem in testing guides')
- real-85480a720e: title verb class 'remove' missing (got 'Documented DB_NAME default claude_mem in testing guides')
- real-85480a720e: title subject [TESTING-zh-CN.md] missing (got 'Documented DB_NAME default claude_mem in testing guides')
- real-85480a720e: title trace-visible subject [TESTING-zh-CN.md] missing (got 'Documented DB_NAME default claude_mem in testing guides')
- real-85480a720e: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-c67e7a5ad3: title coverage 50% (expected keywords [remove, dto.py])
- real-c67e7a5ad3: title verb class 'remove' missing (got 'Fixed DTO conditional branch in python-sdk/cortex-mem-python/cortex_mem/dto.py')
- real-bdf72f2509: type 'refactor' != expected 'bugfix'
- real-bdf72f2509: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-8ac2a32980: title lost identifiers [Dockerfile] (got 'Updated Docker default port comment and removed deployment build-argument sections')
- real-8ac2a32980: title verb class 'remove' missing (got 'Updated Docker default port comment and removed deployment build-argument sections')
- real-8ac2a32980: title subject [Dockerfile] missing (got 'Updated Docker default port comment and removed deployment build-argument sections')
- real-8ac2a32980: title trace-visible subject [Dockerfile] missing (got 'Updated Docker default port comment and removed deployment build-argument sections')
- real-8ac2a32980: type 'change' != expected 'bugfix'
- real-8ac2a32980: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-bc3512ae8d: title lost identifiers [extraction-quality-improvement-plan.md] (got 'Documented Round 6 category few-shots and title ceiling in extraction plan')
- real-bc3512ae8d: title subject [extraction-quality-improvement-plan.md] missing (got 'Documented Round 6 category few-shots and title ceiling in extraction plan')
- real-bc3512ae8d: title trace-visible subject [extraction-quality-improvement-plan.md] missing (got 'Documented Round 6 category few-shots and title ceiling in extraction plan')
- real-d44a83721d: title lost identifiers [extraction-quality-improvement-plan.md] (got 'Documented Round 5 metric correctness and title ceiling in extraction plan')
- real-d44a83721d: title subject [extraction-quality-improvement-plan.md] missing (got 'Documented Round 5 metric correctness and title ceiling in extraction plan')
- real-d44a83721d: title trace-visible subject [extraction-quality-improvement-plan.md] missing (got 'Documented Round 5 metric correctness and title ceiling in extraction plan')
- real-0fd4ffcc31: title coverage 50% (expected keywords [change, goldensetscorer])
- real-0fd4ffcc31: title action [modify] missing: title leads 'create' (got 'Added false-positive skip tracking to GoldenSetScorer')
- real-0fd4ffcc31: title verb class 'change' missing (got 'Added false-positive skip tracking to GoldenSetScorer')
- real-0fd4ffcc31: concepts recall 0% (expected [what-changed], got [problem-solution, pattern])
- real-eee7785ec8: title lost identifiers [2026-11-02_model-output-quality-eval-minimax.md] (got 'Extended model output quality evaluator for Anthropic in GoldenSetEvaluator')
- real-eee7785ec8: title verb class 'document' missing (got 'Extended model output quality evaluator for Anthropic in GoldenSetEvaluator')
- real-eee7785ec8: title subject [2026-11-02_model-output-quality-eval-minimax.md] missing (got 'Extended model output quality evaluator for Anthropic in GoldenSetEvaluator')
- real-eee7785ec8: title trace-visible subject [2026-11-02_model-output-quality-eval-minimax.md] missing (got 'Extended model output quality evaluator for Anthropic in GoldenSetEvaluator')
- real-9a0e90bed5: title coverage 50% (expected keywords [harden, observationrepository])
- real-9a0e90bed5: title verb class 'harden' missing (got 'Fixed ObservationRepository project filtering with memory scopes')
- real-9a0e90bed5: type 'bugfix' != expected 'refactor'
- real-d33efbd8ae: title lost identifiers [ConversationScopeBackfillRunner] (got 'Added conversation scope backfill and technical bucket classification')
- real-d33efbd8ae: title verb class 'harden' missing (got 'Added conversation scope backfill and technical bucket classification')
- real-d33efbd8ae: title subject [ConversationScopeBackfillRunner] missing (got 'Added conversation scope backfill and technical bucket classification')
- real-d33efbd8ae: title trace-visible subject [ConversationScopeBackfillRunner] missing (got 'Added conversation scope backfill and technical bucket classification')
- real-d33efbd8ae: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-cd6b972cfd: title coverage 50% (expected keywords [harden, summaryjobservice])
- real-cd6b972cfd: title verb class 'harden' missing (got 'Added SummaryJobService implementation in SummaryJobService.java')
- real-cd6b972cfd: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-46e467bff6: title lost identifiers [conversation-scope-preflight.sql] (got 'Hardened conversation and memory scope identity in V20 migration')
- real-46e467bff6: title subject [conversation-scope-preflight.sql] missing (got 'Hardened conversation and memory scope identity in V20 migration')
- real-46e467bff6: title trace-visible subject [conversation-scope-preflight.sql] missing (got 'Hardened conversation and memory scope identity in V20 migration')
- real-46e467bff6: type 'bugfix' != expected 'feature'
- real-325c817bbf: title lost identifiers [conversation-memory-scope-refactor-plan.md] (got 'Documented Conversation and Memory Scope identity boundary refactor plan')
- real-325c817bbf: title subject [conversation-memory-scope-refactor-plan.md] missing (got 'Documented Conversation and Memory Scope identity boundary refactor plan')
- real-325c817bbf: title trace-visible subject [conversation-memory-scope-refactor-plan.md] missing (got 'Documented Conversation and Memory Scope identity boundary refactor plan')
- real-95de35b2ea: title coverage 50% (expected keywords [change, pom.xml])
- real-95de35b2ea: type 'change' != expected 'bugfix'
- real-95de35b2ea: concepts recall 0% (expected [problem-solution], got [what-changed, gotcha])
- real-53851b4a23: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])

## Raw answers behind problems

### real-f6a1b89e41

```text
<observation>   <type>change</type>   <title>Updated real tool-use golden set to real-git-2026-11-03-r10</title>   <subtitle>Round-10 golden data now covers more diff history and records model-produced titles for failure clustering.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json version changed from real-git-2026-11-03 to real-git-2026-11-03-r10.</fact>   ... [truncated]
```

### real-d13e2c90cd

```text
<observation>   <type>feature</type>   <title>Added real-history golden evaluation fixture and replay test</title>   <subtitle>GoldenSetRealReplayTest now replays 36 real minimax samples with recorded metrics and an archived evaluation report.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/minimax-real-v9.json contains a 36-sample real golden set.</fact>     <fact>backend/... [truncated]
```

### real-3b4f737562

```text
<observation>   <type>feature</type>   <title>Added MiniMax output-quality evaluation fixtures and metrics</title>   <subtitle>New fixture, metrics, archive report, evaluator output, and golden-tool-use category updates support model-output evaluation.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/minimax-after-v9.json contains 2,096 added lines of model-output evaluation... [truncated]
```

### real-f0d346eab1

```text
<observation>   <type>feature</type>   <title>Added mechanically generated real-history evaluation suite</title>   <subtitle>Real-tool-use golden data and evaluation tooling now provide a reproducible model-quality benchmark.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json contains 1,097 added lines of real-history golden evaluation data.</fact>     <fact>s... [truncated]
```

### real-3a2d426fdf

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetScorer with prompt token metrics in GoldenSetScorer.java</title>   <subtitle>Scoring now records prompt tokens and supports repeatable model-quality evaluation runs.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java adds a public final int promptTokens field to scored results.</fact... [truncated]
```

### real-864dadf51b

```text
(no raw answer: the call failed before returning)
```

### real-30a956e462

```text
<observation>   <type>change</type>   <title>Documented Round 8 insertion-scope contract in extraction quality plan</title>   <subtitle>The extraction-quality plan now records Round 8 guidance covering insertion scope, removed coverage, and a reverted security verb.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md added section `### 7.14 Round 8 — insertion-scope c... [truncated]
```

### real-a9c8452967

```text
<observation>   <type>feature</type>   <title>Added semantic relevance floor evaluation in SearchSimilarityEvaluator</title>   <subtitle>Replay-based evaluation now compares BGE-M3 search similarity against corpus fixtures and recorded metrics.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/SearchSimilarityEvaluator.java defines a 411-line semantic similarity eva... [truncated]
```

### real-db8ac0287e

```text
<observation>   <type>change</type>   <title>Updated defect classification wording in continuation.txt and init.txt</title>   <subtitle>Removed the non-security qualifier from the prompt definition of Fixed defects.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt now defines Fixed as “a defect already produced wrong or failing behaviour.”</fact>     <fact>backend... [truncated]
```

### real-9e6612fdf9

```text
<observation>   <type>change</type>   <title>Documented non-security scope for Fixed in backend/src/main/resources/prompts/continuation.txt</title>   <subtitle>Prompt guidance now limits Fixed wording to non-security defects.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt defines Fixed as a defect in non-security code.</fact>     <fact>backend/src/main/resources... [truncated]
```

### real-e352ba3da3

```text
<observation>   <type>feature</type>   <title>Added MiniMax and LongCat v7 model quality evaluation fixtures</title>   <subtitle>Evaluation suites now include model outputs, metrics, and archived quality reports for both after-v7 models.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/minimax-after-v7.json contains 656 lines of MiniMax after-v7 evaluation output.</fact>    ... [truncated]
```

### real-112de33e23

```text
<observation>   <type>change</type>   <title>Documented replacement identifier rule in prompt resources</title>   <subtitle>Prompt templates now require retaining both replaced and current artifact identifiers.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt includes the rule that a replacement keeps BOTH names: the replaced literal or key and the file that now h... [truncated]
```

### real-28edef5a35

```text
<observation>   <type>feature</type>   <title>Added after-v6 model quality evaluation fixtures and reports</title>   <subtitle>LongCat and MiniMax now have v6 output fixtures, metrics, and archived evaluation reports.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/fixtures/longcat-after-v6.json contains 656 lines of v6 model-output evaluation data.</fact>     <fact>backend/src/test... [truncated]
```

### real-6d6df2c321

```text
<observation>   <type>change</type>   <title>Documented continuation skip rules in backend/src/main/resources/prompts/continuation.txt</title>   <subtitle>Prompt guidance now distinguishes identifier-location searches from recordable implementation work.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt gained 71 lines and removed 11 lines.</fact>     <fact>backend... [truncated]
```

### real-d3431639a9

```text
<observation>   <type>change</type>   <title>Documented Round 4 model output quality evaluation in five evaluation docs</title>   <subtitle>Archived evaluations and the extraction improvement plan now record contract titles, trace grounding, retrieval requirements, and repair-claim review.</subtitle>   <facts>     <fact>docs/archive/2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md... [truncated]
```

### real-7093cb5c56

```text
<observation>   <type>bugfix</type>   <title>Hardened embedding backfill concurrency and log sanitization</title>   <subtitle>Backfill persistence and logging changes added concurrency coverage, controlled embedding calls, and sanitization tests.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/repository/EmbeddingBackfillConcurrencyDatabaseTest.java added 333 lines beg... [truncated]
```

### real-f3d12860ff

```text
<observation>   <type>bugfix</type>   <title>Hardened LogsController log retrieval with sanitized file paths</title>   <subtitle>LogsControllerTest now sets logDir through a helper while the controller validates requested log access.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/controller/LogsControllerTest.java replaced reflective logDir assignment with setField("l... [truncated]
```

### real-3409dda60c

```text
<observation>   <type>change</type>   <title>Documented implementation commit range in conversation-memory-scope-refactor-plan.md</title>   <subtitle>The refactor plan now records nine implementation commits and links readers to the commit list.</subtitle>   <facts>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md sets the implementation commit range to `325c817..f7b865e`.</fact>  ... [truncated]
```

### real-f7b865efe5

```text
<observation>   <type>bugfix</type>   <title>Hardened authentication in backend/src/main/resources/static/index.html</title>   <subtitle>Static viewer assets were rebuilt with authentication-related security changes and an updated warm header color.</subtitle>   <facts>     <fact>backend/src/main/resources/static/index.html changed --color-bg-header from #f6f8fa to #f8f6f2.</fact>     <fact>backen... [truncated]
```

### real-e2c887775a

```text
<observation>   <type>bugfix</type>   <title>Fixed endpoint mapping in AGENTS.md</title>   <subtitle>AGENTS.md now identifies the endpoint that returns the updateFiles field.</subtitle>   <facts>     <fact>AGENTS.md changed the documented endpoint from /api/context/generate to /api/context/inject for updateFiles.</fact>     <fact>docs/drafts/doc-review-task.md records DOC-1 as fixed and identifies... [truncated]
```

### real-cf667b4d8b

```text
(no raw answer: the call failed before returning)
```

### real-a0e77ca4ee

```text
<observation>   <type>change</type>   <title>Documented testing table cleanup in docs/TESTING.md and Chinese counterpart</title>   <subtitle>Testing documentation tables now omit the obsolete Lines column and stay synchronized across English and Chinese versions.</subtitle>   <facts>     <fact>docs/TESTING.md changed the table header from `| Script | Description | Lines |` to `| Script | Descripti... [truncated]
```

### real-d958416fdc

```text
<observation>   <type>change</type>   <title>Updated patrol tracking and API documentation in docs/drafts and backend/README.md</title>   <subtitle>Patrol state now records Backend completion, and architecture endpoint tables align with the backend API.</subtitle>   <facts>     <fact>docs/drafts/patrol-state.json sets "lastDirection" to "backend" instead of "demo".</fact>     <fact>docs/drafts/pat... [truncated]
```

### real-9b424d0d02

```text
<observation>   <type>change</type>   <title>Documented GET /api/settings default corrections in docs/API.md</title>   <subtitle>English and Chinese API changelogs now align six settings examples with AppSettings.toMap() defaults.</subtitle>   <facts>     <fact>docs/API.md documents six corrected GET /api/settings example values matching AppSettings.toMap() defaults.</fact>     <fact>docs/API-zh-C... [truncated]
```

### real-85480a720e

```text
<observation>   <type>change</type>   <title>Documented DB_NAME default claude_mem in testing guides</title>   <subtitle>English and Chinese testing guides now describe the database name environment variable and its default.</subtitle>   <facts>     <fact>docs/TESTING-zh-CN.md replaces the `DB_HOST` row with `| `DB_NAME` | claude_mem | 数据库名称 |`.</fact>     <fact>docs/TESTING.md replaces the `DB_HO... [truncated]
```

### real-c67e7a5ad3

```text
<observation>   <type>bugfix</type>   <title>Fixed DTO conditional branch in python-sdk/cortex-mem-python/cortex_mem/dto.py</title>   <subtitle>Removed an empty else branch from the Python SDK DTO implementation.</subtitle>   <facts>     <fact>python-sdk/cortex-mem-python/cortex_mem/dto.py removed the empty `else:` branch and its associated lines.</fact>     <fact>python-sdk/cortex-mem-python/cort... [truncated]
```

### real-bdf72f2509

```text
<observation>   <type>refactor</type>   <title>Removed SDK version comment from CortexMemClientImpl</title>   <subtitle>Removed an obsolete release-maintenance comment without changing source behavior.</subtitle>   <facts>     <fact>CortexMemClientImpl.java removed the comment `/** SDK version for User-Agent header. Update when releasing new SDK versions. */`.</fact>     <fact>CortexMemClientImpl.... [truncated]
```

### real-8ac2a32980

```text
<observation>   <type>change</type>   <title>Updated Docker default port comment and removed deployment build-argument sections</title>   <subtitle>Dockerfile documentation now states port 37777, while both deployment guides drop the build-argument instructions.</subtitle>   <facts>     <fact>backend/Dockerfile now documents the default port as 37777 and retains SERVER_PORT as the override.</fact>... [truncated]
```

### real-bc3512ae8d

```text
<observation>   <type>change</type>   <title>Documented Round 6 category few-shots and title ceiling in extraction plan</title>   <subtitle>Round 6 records category few-shot findings and the measured title ceiling for extraction quality work.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md includes the section "## 7.12 Round 6 — category few-shots and the measured... [truncated]
```

### real-d44a83721d

```text
<observation>   <type>change</type>   <title>Documented Round 5 metric correctness and title ceiling in extraction plan</title>   <subtitle>The extraction quality plan gained a 99-line section addressing metric correctness and the remaining title ceiling.</subtitle>   <facts>     <fact>docs/drafts/extraction-quality-improvement-plan.md added 99 lines and deleted 0 lines.</fact>     <fact>docs/draf... [truncated]
```

### real-0fd4ffcc31

```text
<observation>   <type>feature</type>   <title>Added false-positive skip tracking to GoldenSetScorer</title>   <subtitle>Golden-set evaluation now distinguishes record-worthy skipped samples from other skipped samples.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetScorer.java defines separate skipFp and skipFn accounting for record-worthy skipped samples... [truncated]
```

### real-eee7785ec8

```text
<observation>   <type>feature</type>   <title>Extended model output quality evaluator for Anthropic in GoldenSetEvaluator</title>   <subtitle>Golden-set evaluation now supports Anthropic alongside the existing provider while remaining manually invoked.</subtitle>   <facts>     <fact>backend/src/test/java/com/ablueforce/cortexce/eval/GoldenSetEvaluator.java imports org.springframework.ai.anthropic.... [truncated]
```

### real-9a0e90bed5

```text
<observation>   <type>bugfix</type>   <title>Fixed ObservationRepository project filtering with memory scopes</title>   <subtitle>Observation queries now filter by memoryScopeId through MemoryScopeService while preserving optional project filtering.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/repository/ObservationRepository.java replaces the project-only condition... [truncated]
```

### real-d33efbd8ae

```text
<observation>   <type>feature</type>   <title>Added conversation scope backfill and technical bucket classification</title>   <subtitle>New migration components classify technical conversations and backfill conversation scope metadata through a CLI.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/migration/ConversationScopeBackfillRunner.java adds 845 lines for convers... [truncated]
```

### real-cd6b972cfd

```text
<observation>   <type>feature</type>   <title>Added SummaryJobService implementation in SummaryJobService.java</title>   <subtitle>SummaryJobService now provides a 326-line backend service implementation with security-related handling.</subtitle>   <facts>     <fact>backend/src/main/java/com/ablueforce/cortexce/service/SummaryJobService.java contains 326 added lines.</fact>     <fact>SummaryJobSer... [truncated]
```

### real-46e467bff6

```text
<observation>   <type>bugfix</type>   <title>Hardened conversation and memory scope identity in V20 migration</title>   <subtitle>Added additive schema expansion and SQL preflight/fixture support for conversation-scoped memory identities.</subtitle>   <facts>     <fact>backend/src/main/resources/db/migration/V20__conversation_memory_scope_expand.sql contains 311 added lines and identifies itself a... [truncated]
```

### real-325c817bbf

```text
<observation>   <type>change</type>   <title>Documented Conversation and Memory Scope identity boundary refactor plan</title>   <subtitle>Added a 917-line implementation blueprint for separating Conversation and Memory Scope identities.</subtitle>   <facts>     <fact>docs/drafts/conversation-memory-scope-refactor-plan.md gained 917 lines and the heading '# Conversation 与 Memory Scope 身份边界重构实施蓝图'.<... [truncated]
```

### real-95de35b2ea

```text
<observation>   <type>change</type>   <title>Updated byte-buddy dependency pin in backend/pom.xml</title>   <subtitle>Test dependency configuration now explicitly targets byte-buddy 1.15.x through Mockito 5.11.</subtitle>   <facts>     <fact>backend/pom.xml pins byte-buddy to 1.15.x using the Mockito 5.11 version inherited from the spring-boot-starter-test BOM.</fact>     <fact>scripts/docker-e2e-... [truncated]
```

### real-53851b4a23

```text
<observation>   <type>change</type>   <title>Documented health check rounds 312–359+ in docs/archive/2026-10-08_health-check-history-11.md</title>   <subtitle>Archived a compressed inspection history and registered the archive in the archive index.</subtitle>   <facts>     <fact>docs/archive/2026-10-08_health-check-history-11.md contains 1,206 lines covering health check rounds 312–359+ from 2026-... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
