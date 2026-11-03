golden set: real (36 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: after-v9
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 36 unique; runs scored: 1; key repeat: 3; real calls: 36 (cap none); duration: 601807 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 92% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 0/0/0/36 |
| format errors | 0 |
| validation rejections | 3 |
| call failures | 0 |
| title exact-hit rate (legacy) | 27% |
| title normalized hit rate (legacy) | 27% |
| title action-class hit rate (A6) | 42% (33 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 33% |
| title subject hit rate (legacy, diagnostic) | 82% |
| title trace-visible subject hit rate (A6b) | 82% (33 scored, 0 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 36% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 36% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 30% |
| title identifier hit rate | 82% |
| type accuracy | 52% |
| facts keyword precision (legacy) / recall | 100% / 100% |
| facts informative precision | 77% |
| facts trace-support rate | 100% |
| concepts precision / recall | 33% / 67% |
| skip consistency across repeats | 0% (0 repeated samples) |
| latency p50 / p95 / max (ms) | 16438 / 28975 / 34515 |
| tokens p50 / p95 / max / mean per call | 4622 / 5297 / 6093 / 4703 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 77% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 42% | **FAIL** |
| A6b | title trace-visible subject | >= 90% | 82% | **FAIL** |
| A6c | title identifiers | = 100% | 82% | **FAIL** |
| A6 | all three title dimensions together | all pass | 36% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 36 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 36 | 100% | 27% | 36% | 100% | 77% | 100% | 0 |

- average: skip 100%, title 27%, title contract 36%, facts P/R 100%/100%, facts actionable 77%
- worst run: skip 100%, title 27%, title contract 36%, facts P/R 100%/100%, facts actionable 77%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| refactor | 2 | 2/2 | 0/2 | 0/2 |
| feature | 7 | 6/7 | 3/7 | 1/7 |
| docs-update | 14 | 12/14 | 11/14 | 7/14 |
| test-add | 3 | 3/3 | 2/3 | 1/3 |
| security-fix | 10 | 10/10 | 1/10 | 0/10 |

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| real-77ba40df77 | refactor | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 0% | 50%/100% | 10488 |
| real-f0d346eab1 | feature | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 50%/100% | 19912 |
| real-3a2d426fdf | refactor | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 67% | 50%/100% | 17389 |
| real-864dadf51b | feature | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 20% | 50%/100% | 10683 |
| real-b064308244 | docs-update | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 67% | 50%/100% | 13091 |
| real-30a956e462 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 12127 |
| real-ba86bf2588 | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/100% | 8084 |
| real-a9c8452967 | feature | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 33% | 50%/100% | 12951 |
| real-db8ac0287e | docs-update | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 50%/100% | 16438 |
| real-7f5ff5974d | test-add | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 50%/100% | 8603 |
| real-9e6612fdf9 | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 67% | 0%/0% | 17452 |
| real-f43cfd6f44 | docs-update | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 15803 |
| real-e352ba3da3 | feature | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 22387 |
| real-112de33e23 | docs-update | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 50%/100% | 21250 |
| real-bc3512ae8d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/100% | 18188 |
| real-28edef5a35 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | MISS | 100%/100% | 100% | 50%/100% | 21044 |
| real-e02e436bdd | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 17531 |
| real-d44a83721d | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 16816 |
| real-6d6df2c321 | docs-update | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 67% | 50%/100% | 14716 |
| real-0fd4ffcc31 | test-add | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 19536 |
| real-d3431639a9 | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/100% | 22352 |
| real-7093cb5c56 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | match | 100%/100% | 67% | 50%/100% | 12357 |
| real-2025b33a95 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 0%/0% | 27000 |
| real-f3d12860ff | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | MISS | 100%/100% | 100% | 0%/0% | 28975 |
| real-6fd827abdc | feature | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 50%/100% | 34515 |
| real-eee7785ec8 | feature | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 67% | 50%/100% | 20998 |
| real-3409dda60c | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 12127 |
| real-f7b865efe5 | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 0%/0% | 21785 |
| real-a8cc1754bc | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 0%/0% | 6386 |
| real-9a0e90bed5 | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 50% | 0%/0% | 16940 |
| real-63b5502fb9 | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 0%/0% | 20281 |
| real-d33efbd8ae | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 67% | 0%/0% | 12843 |
| real-cd6b972cfd | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 67% | 0%/0% | 12368 |
| real-46e467bff6 | security-fix | observation | observation | pass | 0% | MISS (subject MISS, id MISS, action MISS) | miss | MISS | 100%/100% | 50% | 0%/0% | 14464 |
| real-325c817bbf | docs-update | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 9204 |
| real-e8c8b314d6 | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/100% | 13408 |

## Problems

- real-77ba40df77: title coverage 50% (expected keywords [change, extractionconsistency])
- real-77ba40df77: title action 'modify' missing: title leads 'create' (got 'Added opt-in majority vote for observation extraction in ExtractionConsistency')
- real-77ba40df77: title verb class 'change' missing (got 'Added opt-in majority vote for observation extraction in ExtractionConsistency')
- real-77ba40df77: type 'feature' != expected 'refactor'
- real-f0d346eab1: title coverage 50% (expected keywords [change, golden-tool-use-real.json])
- real-f0d346eab1: title action 'modify' missing: title leads 'create' (got 'Added golden-tool-use-real.json 36-sample golden set from real git history')
- real-f0d346eab1: title verb class 'change' missing (got 'Added golden-tool-use-real.json 36-sample golden set from real git history')
- real-f0d346eab1: type 'feature' != expected 'change'
- real-3a2d426fdf: title coverage 50% (expected keywords [change, goldensetscorer])
- real-3a2d426fdf: title action 'modify' missing: title leads 'create' (got 'Added promptTokens and run-to-run variance tracking to GoldenSetScorer')
- real-3a2d426fdf: title verb class 'change' missing (got 'Added promptTokens and run-to-run variance tracking to GoldenSetScorer')
- real-3a2d426fdf: type 'feature' != expected 'refactor'
- real-864dadf51b: title coverage 50% (expected keywords [change, goldensetscorer])
- real-864dadf51b: title verb class 'change' missing (got 'Extended GoldenSetScorer reproduction title requirements to name both identifiers')
- real-b064308244: title coverage 50% (expected keywords [document, extraction-quality-improvement-plan.md])
- real-b064308244: title action 'document' missing: title leads 'modify' (got 'Updated test layer attribution wording in docs/drafts/extraction-quality-improvement-plan.md')
- real-b064308244: title verb class 'document' missing (got 'Updated test layer attribution wording in docs/drafts/extraction-quality-improvement-plan.md')
- real-db8ac0287e: title coverage 50% (expected keywords [document, continuation.txt])
- real-db8ac0287e: title action 'document' missing: title leads 'remove' (got 'Removed non-security qualifier from Fixed verb definition in continuation.txt')
- real-db8ac0287e: title verb class 'document' missing (got 'Removed non-security qualifier from Fixed verb definition in continuation.txt')
- real-7f5ff5974d: title coverage 50% (expected keywords [change, golden-tool-use.json])
- real-7f5ff5974d: title action 'modify' missing: title leads 'create' (got 'Added five removal samples to golden-tool-use.json eval resource')
- real-7f5ff5974d: title verb class 'change' missing (got 'Added five removal samples to golden-tool-use.json eval resource')
- real-9e6612fdf9: title coverage 50% (expected keywords [harden, continuation.txt])
- real-9e6612fdf9: title action 'secure' missing: title leads 'modify' (got 'Updated Fixed verb guidance in continuation.txt to exclude security code')
- real-9e6612fdf9: title verb class 'harden' missing (got 'Updated Fixed verb guidance in continuation.txt to exclude security code')
- real-9e6612fdf9: type 'change' != expected 'bugfix'
- real-9e6612fdf9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-f43cfd6f44: type 'decision' != expected 'change'
- real-f43cfd6f44: concepts recall 0% (expected [what-changed], got [why-it-exists, pattern])
- real-e352ba3da3: validation rejected the observation: title too long: 106 chars > 100
- real-112de33e23: title coverage 50% (expected keywords [document, continuation.txt])
- real-112de33e23: title action 'document' missing: title leads 'modify' (got 'Extended title-naming rule for literal replacements in continuation.txt')
- real-112de33e23: title verb class 'document' missing (got 'Extended title-naming rule for literal replacements in continuation.txt')
- real-28edef5a35: title lost identifiers [longcat-after-v6.json] (got 'Archived round-six eval fixtures for longcat and minimax providers')
- real-28edef5a35: title action 'create' missing: title leads 'null' (got 'Archived round-six eval fixtures for longcat and minimax providers')
- real-28edef5a35: title verb class 'add' missing (got 'Archived round-six eval fixtures for longcat and minimax providers')
- real-28edef5a35: title subject [longcat-after-v6.json] missing (got 'Archived round-six eval fixtures for longcat and minimax providers')
- real-28edef5a35: title trace-visible subject [longcat-after-v6.json] missing (got 'Archived round-six eval fixtures for longcat and minimax providers')
- real-28edef5a35: type 'change' != expected 'feature'
- real-6d6df2c321: title coverage 50% (expected keywords [document, continuation.txt])
- real-6d6df2c321: title action 'document' missing: title leads 'modify' (got 'Updated skip criteria and title verb guidance in prompts/continuation.txt and init.txt')
- real-6d6df2c321: title verb class 'document' missing (got 'Updated skip criteria and title verb guidance in prompts/continuation.txt and init.txt')
- real-0fd4ffcc31: title coverage 50% (expected keywords [change, goldensetscorer])
- real-0fd4ffcc31: title verb class 'change' missing (got 'Fixed skip gate counters and folded plurals in GoldenSetScorer')
- real-0fd4ffcc31: type 'bugfix' != expected 'feature'
- real-d3431639a9: title lost identifiers [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] (got 'Documented round four quality results in model-output-quality-eval-minimax-after-v4-contract.md')
- real-d3431639a9: title subject [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] missing (got 'Documented round four quality results in model-output-quality-eval-minimax-after-v4-contract.md')
- real-d3431639a9: title trace-visible subject [2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md] missing (got 'Documented round four quality results in model-output-quality-eval-minimax-after-v4-contract.md')
- real-7093cb5c56: title lost identifiers [EmbeddingBackfillConcurrencyDatabaseTest] (got 'Fixed EmbeddingBackfillService to claim row before writing in EmbeddingBackfillService.java')
- real-7093cb5c56: title action 'secure' missing: title leads 'modify' (got 'Fixed EmbeddingBackfillService to claim row before writing in EmbeddingBackfillService.java')
- real-7093cb5c56: title verb class 'harden' missing (got 'Fixed EmbeddingBackfillService to claim row before writing in EmbeddingBackfillService.java')
- real-7093cb5c56: title subject [EmbeddingBackfillConcurrencyDatabaseTest] missing (got 'Fixed EmbeddingBackfillService to claim row before writing in EmbeddingBackfillService.java')
- real-7093cb5c56: title trace-visible subject [EmbeddingBackfillConcurrencyDatabaseTest] missing (got 'Fixed EmbeddingBackfillService to claim row before writing in EmbeddingBackfillService.java')
- real-2025b33a95: concepts recall 0% (expected [what-changed], got [why-it-exists, pattern])
- real-f3d12860ff: title lost identifiers [LogsControllerTest] (got 'Changed log API to optional in LogsController')
- real-f3d12860ff: title action 'secure' missing: title leads 'modify' (got 'Changed log API to optional in LogsController')
- real-f3d12860ff: title verb class 'harden' missing (got 'Changed log API to optional in LogsController')
- real-f3d12860ff: title subject [LogsControllerTest] missing (got 'Changed log API to optional in LogsController')
- real-f3d12860ff: title trace-visible subject [LogsControllerTest] missing (got 'Changed log API to optional in LogsController')
- real-f3d12860ff: type 'feature' != expected 'bugfix'
- real-f3d12860ff: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-6fd827abdc: title coverage 50% (expected keywords [change, extractionvalidationservicetest])
- real-6fd827abdc: type 'feature' != expected 'change'
- real-eee7785ec8: title lost identifiers [2026-11-02_model-output-quality-eval-minimax.md] (got 'Extended GoldenSetEvaluator with Anthropic provider support')
- real-eee7785ec8: title verb class 'change' missing (got 'Extended GoldenSetEvaluator with Anthropic provider support')
- real-eee7785ec8: title subject [2026-11-02_model-output-quality-eval-minimax.md] missing (got 'Extended GoldenSetEvaluator with Anthropic provider support')
- real-eee7785ec8: title trace-visible subject [2026-11-02_model-output-quality-eval-minimax.md] missing (got 'Extended GoldenSetEvaluator with Anthropic provider support')
- real-3409dda60c: validation rejected the observation: title too long: 101 chars > 100
- real-f7b865efe5: title coverage 50% (expected keywords [harden, index.html])
- real-f7b865efe5: title action 'secure' missing: title leads 'modify' (got 'Changed header background color to #f8f6f2 in index.html')
- real-f7b865efe5: title verb class 'harden' missing (got 'Changed header background color to #f8f6f2 in index.html')
- real-f7b865efe5: type 'change' != expected 'bugfix'
- real-f7b865efe5: concepts recall 0% (expected [problem-solution], got [what-changed, trade-off])
- real-a8cc1754bc: title coverage 50% (expected keywords [harden, pendingmessagerepository])
- real-a8cc1754bc: title action 'secure' missing: title leads 'remove' (got 'Removed dedup comment from PendingMessageRepository')
- real-a8cc1754bc: title verb class 'harden' missing (got 'Removed dedup comment from PendingMessageRepository')
- real-a8cc1754bc: type 'refactor' != expected 'bugfix'
- real-a8cc1754bc: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-9a0e90bed5: title coverage 50% (expected keywords [harden, observationrepository])
- real-9a0e90bed5: title action 'secure' missing: title leads 'null' (got 'Routed observation reads through memory scope predicates in ObservationRepository')
- real-9a0e90bed5: title verb class 'harden' missing (got 'Routed observation reads through memory scope predicates in ObservationRepository')
- real-9a0e90bed5: type 'refactor' != expected 'bugfix'
- real-9a0e90bed5: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-63b5502fb9: title coverage 50% (expected keywords [harden, v20__conversation_memory_scope_expand.sql])
- real-63b5502fb9: title action 'secure' missing: title leads 'document' (got 'Documented V20 migration header comment in V20__conversation_memory_scope_expand.sql')
- real-63b5502fb9: title verb class 'harden' missing (got 'Documented V20 migration header comment in V20__conversation_memory_scope_expand.sql')
- real-63b5502fb9: type 'change' != expected 'bugfix'
- real-63b5502fb9: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-d33efbd8ae: title coverage 50% (expected keywords [harden, conversationscopebackfillrunner])
- real-d33efbd8ae: title action 'secure' missing: title leads 'create' (got 'Added TechnicalBucketClassifier fail-closed classifier and ConversationScopeBackfillRunner')
- real-d33efbd8ae: title verb class 'harden' missing (got 'Added TechnicalBucketClassifier fail-closed classifier and ConversationScopeBackfillRunner')
- real-d33efbd8ae: type 'feature' != expected 'bugfix'
- real-d33efbd8ae: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-cd6b972cfd: title coverage 50% (expected keywords [harden, summaryjobservice])
- real-cd6b972cfd: title action 'secure' missing: title leads 'create' (got 'Added durable resumable SessionEnd summary job in SummaryJobService')
- real-cd6b972cfd: title verb class 'harden' missing (got 'Added durable resumable SessionEnd summary job in SummaryJobService')
- real-cd6b972cfd: type 'feature' != expected 'bugfix'
- real-cd6b972cfd: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-46e467bff6: title lost identifiers [conversation-scope-preflight.sql] (got 'Added V20 conversation_memory scope expansion with preflight and fixture SQL')
- real-46e467bff6: title action 'secure' missing: title leads 'create' (got 'Added V20 conversation_memory scope expansion with preflight and fixture SQL')
- real-46e467bff6: title verb class 'harden' missing (got 'Added V20 conversation_memory scope expansion with preflight and fixture SQL')
- real-46e467bff6: title subject [conversation-scope-preflight.sql] missing (got 'Added V20 conversation_memory scope expansion with preflight and fixture SQL')
- real-46e467bff6: title trace-visible subject [conversation-scope-preflight.sql] missing (got 'Added V20 conversation_memory scope expansion with preflight and fixture SQL')
- real-46e467bff6: type 'feature' != expected 'bugfix'
- real-46e467bff6: concepts recall 0% (expected [problem-solution], got [what-changed, pattern])
- real-325c817bbf: validation rejected the observation: title too long: 103 chars > 100

## Raw answers behind problems

### real-77ba40df77

```text
<observation>   <type>feature</type>   <title>Added opt-in majority vote for observation extraction in ExtractionConsistency</title>   <subtitle>New service implements majority vote logic for observation extraction, wired into AgentService.</subtitle>   <facts>     <fact>New file backend/src/main/java/com/ablueforce/cortexce/service/ExtractionConsistency.java implements majority vote for observati... [truncated]
```

### real-f0d346eab1

```text
<observation>   <type>feature</type>   <title>Added golden-tool-use-real.json 36-sample golden set from real git history</title>   <subtitle>Generated a real-data golden tool-use evaluation set from git history for the cortexce eval test suite.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use-real.json was created as a 36-sample golden set generated from real git hist... [truncated]
```

### real-3a2d426fdf

```text
<observation>   <type>feature</type>   <title>Added promptTokens and run-to-run variance tracking to GoldenSetScorer</title>   <subtitle>Extraction eval harness now records per-call token cost and run-to-run variance across the scorer, evaluator, and driver script.</subtitle>   <facts>     <fact>GoldenSetScorer.java declares a new `public final int promptTokens;` field for per-call token cost.</fa... [truncated]
```

### real-864dadf51b

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetScorer reproduction title requirements to name both identifiers</title>   <subtitle>GoldenSetScorer tests now require reproduction titles to name both test and production identifiers plus score trace-visible subjects.</subtitle>   <facts>     <fact>GoldenSetScorer.java was edited in backend/src/test/java/com/ablueforce/cortexce/eval/<... [truncated]
```

### real-b064308244

```text
<observation>   <type>change</type>   <title>Updated test layer attribution wording in docs/drafts/extraction-quality-improvement-plan.md</title>   <subtitle>Corrected the rehearsal test description to attribute it to the rehearsal database gate instead of the L4 layer.</subtitle>   <facts>     <fact>The phrase "runs in the L4 layer under the rehearsal" was replaced with "runs under the rehearsal ... [truncated]
```

### real-db8ac0287e

```text
<observation>   <type>change</type>   <title>Removed non-security qualifier from Fixed verb definition in continuation.txt</title>   <subtitle>Reverted prompt template wording that had narrowed the Fixed verb to non-security defects only.</subtitle>   <facts>     <fact>The phrase "in non-security code" was removed from the Fixed verb definition in backend/src/main/resources/prompts/continuation.tx... [truncated]
```

### real-7f5ff5974d

```text
<observation>   <type>feature</type>   <title>Added five removal samples to golden-tool-use.json eval resource</title>   <subtitle>Extended the golden tool-use eval fixture with five removal samples so the remove action branch is exercised.</subtitle>   <facts>     <fact>backend/src/test/resources/eval/golden-tool-use.json gained five removal samples covering the remove action branch.</fact>     <... [truncated]
```

### real-9e6612fdf9

```text
<observation>   <type>change</type>   <title>Updated Fixed verb guidance in continuation.txt to exclude security code</title>   <subtitle>Title-verb rules now restrict "Fixed" to non-security defects and add security-domain and insertion-scope criteria.</subtitle>   <facts>     <fact>The line "Fixed: a defect already produced wrong or failing behaviour." in backend/src/main/resources/prompts/conti... [truncated]
```

### real-f43cfd6f44

```text
```xml <observation>   <type>decision</type>   <title>Documented A6 action classes decision in extraction-quality-improvement-plan.md</title>   <subtitle>Round 7 section records the action-class decision, its results, and boundary evidence.</subtitle>   <facts>     <fact>A new section heading "## 7.13 Round 7 — A6 action classes derived from the trace" was added to docs/drafts/extraction-quality-i... [truncated]
```

### real-e352ba3da3

```text
<observation>   <type>feature</type>   <title>Added round-seven eval fixtures longcat-after-v7.json and minimax-after-v7.json with archive documentation</title>   <subtitle>Archived round-seven model output quality eval runs under the trace-derived action metric.</subtitle>   <facts>     <fact>New fixture `backend/src/test/resources/eval/fixtures/longcat-after-v7.json` added for round-seven eval r... [truncated]
```

### real-112de33e23

```text
<observation>   <type>change</type>   <title>Extended title-naming rule for literal replacements in continuation.txt</title>   <subtitle>Prompt now requires both the replaced literal and its file to be kept verbatim in a title.</subtitle>   <facts>     <fact>The line "- A replacement keeps BOTH names: the literal or key that was replaced and the file that now" was added to `backend/src/main/resour... [truncated]
```

### real-28edef5a35

```text
<observation>   <type>change</type>   <title>Archived round-six eval fixtures for longcat and minimax providers</title>   <subtitle>Committed round-six full run fixture data and archive documentation for both model providers.</subtitle>   <facts>     <fact>Commit "Archive the round-six full runs for both providers" changed 6 files with 2422 insertions and 0 deletions.</fact>     <fact>Fixture file... [truncated]
```

### real-6d6df2c321

```text
<observation>   <type>change</type>   <title>Updated skip criteria and title verb guidance in prompts/continuation.txt and init.txt</title>   <subtitle>Refined memory-extraction prompt instructions to make concept slots category-explicit and the title verb precise.</subtitle>   <facts>     <fact>backend/src/main/resources/prompts/continuation.txt skip guidance replaced the phrase "failed), not as ... [truncated]
```

### real-0fd4ffcc31

```text
<observation>   <type>bugfix</type>   <title>Fixed skip gate counters and folded plurals in GoldenSetScorer</title>   <subtitle>GoldenSetScorer keyword scoring now normalizes plural forms to canonical keywords and skip gate counters are corrected.</subtitle>   <facts>     <fact>GoldenSetScorer.java adds `Map.entry("documentation", "doc")` to the keyword synonym map, folding plural forms into canon... [truncated]
```

### real-d3431639a9

```text
<observation>   <type>change</type>   <title>Documented round four quality results in model-output-quality-eval-minimax-after-v4-contract.md</title>   <subtitle>Round four model output quality evaluation recorded in archive docs for MiniMax and LongCat plus a chain eval summary.</subtitle>   <facts>     <fact>Added heading "# Model output quality evaluation" to docs/archive/2026-11-03_model-output... [truncated]
```

### real-7093cb5c56

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed EmbeddingBackfillService to claim row before writing in EmbeddingBackfillService.java</title>   <subtitle>Embedding repair now claims the row before it writes, preventing concurrent backfill duplicates.</subtitle>   <facts>     <fact>EmbeddingBackfillService was changed so embedding repair claims the row before it writes instead of writing ... [truncated]
```

### real-2025b33a95

```text
<observation>   <type>change</type>   <title>Documented audit gap closure and rejected alternatives in extraction-quality-improvement-plan.md</title>   <subtitle>Added section 7.9 recording the audit gap closure and the alternatives that were rejected.</subtitle>   <facts>     <fact>A new section titled "### 7.9 Audit gap closure (2026-11-03)" was added to docs/drafts/extraction-quality-improvemen... [truncated]
```

### real-f3d12860ff

```text
<observation>   <type>feature</type>   <title>Changed log API to optional in LogsController</title>   <subtitle>LogsController log API is now optional while its default behavior is preserved; the test uses a setField helper for logDir.</subtitle>   <facts>     <fact>Commit message states "Make the log API optional without changing its default."</fact>     <fact>The commit changed 2 files with 91 i... [truncated]
```

### real-6fd827abdc

```text
<observation>   <type>feature</type>   <title>Updated ExtractionValidationServiceTest assertion to PaymentNotFoundException for missing orderId</title>   <subtitle>Replaced a process-noise test-name reference with a concrete behavioral assertion about a missing orderId.</subtitle>   <facts>     <fact>In backend/src/test/java/com/ablueforce/cortexce/service/ExtractionValidationServiceTest.java, the... [truncated]
```

### real-eee7785ec8

```text
<observation>   <type>feature</type>   <title>Extended GoldenSetEvaluator with Anthropic provider support</title>   <subtitle>Commit added Anthropic provider support to the model output quality evaluation test and script, plus archived eval documentation.</subtitle>   <facts>     <fact>Commit "test: support Anthropic provider in quality evaluation" changed 5 files with 183 insertions and 15 deleti... [truncated]
```

### real-3409dda60c

```text
<observation>   <type>change</type>   <title>Documented implementation commit range 325c817..f7b865e in conversation-memory-scope-refactor-plan.md</title>   <subtitle>Updated execution status table with actual commit range and count for the completed conversation/scope refactor.</subtitle>   <facts>     <fact>The "实施 commit 范围" table row in docs/drafts/conversation-memory-scope-refactor-plan.md no... [truncated]
```

### real-f7b865efe5

```text
<observation>   <type>change</type>   <title>Changed header background color to #f8f6f2 in index.html</title>   <subtitle>Updated the --color-bg-header CSS custom property as part of the v2 identity wire pairing.</subtitle>   <facts>     <fact>The `--color-bg-header` CSS custom property in `backend/src/main/resources/static/index.html` changed from `#f6f8fa` to `#f8f6f2`.</fact>     <fact>The edit... [truncated]
```

### real-a8cc1754bc

```text
<observation>   <type>refactor</type>   <title>Removed dedup comment from PendingMessageRepository</title>   <subtitle>Deleted a stale dedup comment from PendingMessageRepository as part of V20 schema refactor.</subtitle>   <facts>     <fact>Removed the comment line "// Dedup: check if a message with same session + tool already exists (not failed)" from PendingMessageRepository.java.</fact>     <f... [truncated]
```

### real-9a0e90bed5

```text
<observation>   <type>refactor</type>   <title>Routed observation reads through memory scope predicates in ObservationRepository</title>   <subtitle>ObservationRepository queries now filter by memoryScopeId instead of projectPath via memory scope subquery.</subtitle>   <facts>     <fact>ObservationRepository.java replaced the predicate `WHERE (:project IS NULL OR o.projectPath = :project)` with `W... [truncated]
```

### real-63b5502fb9

```text
<observation>   <type>change</type>   <title>Documented V20 migration header comment in V20__conversation_memory_scope_expand.sql</title>   <subtitle>Added a one-shot migration banner comment above the mem_memory_scopes table definition.</subtitle>   <facts>     <fact>Edit inserted the comment "-- V20: Conversation / Memory Scope identity — one-shot migration." immediately before "CREATE TABLE IF ... [truncated]
```

### real-d33efbd8ae

```text
<observation>   <type>feature</type>   <title>Added TechnicalBucketClassifier fail-closed classifier and ConversationScopeBackfillRunner</title>   <subtitle>New migration components provide fail-closed classification and checkpointed conversation scope backfill.</subtitle>   <facts>     <fact>Commit `feat(migration): add fail-closed classifier and checkpointed backfill runner` adds 1510 insertions... [truncated]
```

### real-cd6b972cfd

```text
```xml <observation>   <type>feature</type>   <title>Added durable resumable SessionEnd summary job in SummaryJobService</title>   <subtitle>New SummaryJobService in com.ablueforce.cortexce.service implements a resumable SessionEnd summary job with 326 lines.</subtitle>   <facts>     <fact>Created backend/src/main/java/com/ablueforce/cortexce/service/SummaryJobService.java in package com.ablueforc... [truncated]
```

### real-46e467bff6

```text
```xml <observation>   <type>feature</type>   <title>Added V20 conversation_memory scope expansion with preflight and fixture SQL</title>   <subtitle>Introduced additive DB migration plus read-only preflight and fixture scripts for the conversation-scope refactor.</subtitle>   <facts>     <fact>backend/src/main/resources/db/migration/V20__conversation_memory_scope_expand.sql is an additive migrati... [truncated]
```

### real-325c817bbf

```text
```xml <observation>   <type>change</type>   <title>Added conversation-memory-scope refactor plan in docs/drafts/conversation-memory-scope-refactor-plan.md</title>   <subtitle>New blueprint document defining the identity boundary refactor between Conversation and Memory Scope.</subtitle>   <facts>     <fact>Created docs/drafts/conversation-memory-scope-refactor-plan.md with 917 inserted lines.</fa... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
