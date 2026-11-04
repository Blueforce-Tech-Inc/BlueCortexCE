golden set: main (87 samples)

# Model output quality evaluation

- Date: 2026-11-04
- Phase: prompt-slim-before
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: backend/src/test/resources/eval/prompts-prompt-slim-baseline
- Samples: 87 unique; runs scored: 3; key repeat: 3; real calls: 113 (cap none); duration: 1691794 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 98% |
| skip accuracy | 99% |
|   skip confusion (TP/FP/FN/TN) | 19/0/1/67 |
| format errors | 0 |
| validation rejections | 0 |
| call failures | 1 |
| call success rate | 99% (1/113 failed) |
| title exact-hit rate (legacy) | 52% |
| title normalized hit rate (legacy) | 55% |
| title action-class hit rate (A6) | 92% (66 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 74% |
| title subject hit rate (legacy, diagnostic) | 83% |
| title trace-visible subject hit rate (A6b) | 93% (56 scored, 10 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 73% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 83% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 59% |
| title identifier hit rate | 95% |
| type accuracy (raw) | 88% |
| type accuracy (auditable labels only) | 85% (11 unverifiable label(s) excluded) |
| facts keyword precision (legacy) / recall | 77% / 93% |
| facts informative precision | 77% |
| facts trace-support rate | 100% |
| concepts precision / recall | 84% / 83% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 11367 / 38218 / 144781 |
| tokens p50 / p95 / max / mean per call | 5013 / 6094 / 8522 / 5010 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 99% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 1 | PASS |
| A5 | facts informative precision | >= 75% | 77% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 92% | PASS |
| A6b | title trace-visible subject | >= 90% | 93% | PASS |
| A6c | title identifiers | = 100% | 95% | **FAIL** |
| A6 | all three title dimensions together | all pass | 83% | **FAIL** |
| A11 | p95 latency vs baseline | <= 1.5x baseline | 38218 ms (baseline 33716 ms) | PASS |
| A12 | p95 tokens vs baseline | <= 1.5x baseline | 6094 tokens (baseline 5763) | PASS |
| A13 | provider call success rate | >= 99% (at most 1 failed call(s) per run) | 99% (1/113 failed) | PASS |

## Status distribution

| status | calls |
|---|---|
| 200 | 86 |
| exception | 1 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 87 | 99% | 55% | 73% | 77% | 77% | 93% | 0 |
| 1 | 13 | 100% | 50% | 70% | 88% | 77% | 100% | 0 |
| 2 | 13 | 100% | 40% | 60% | 79% | 78% | 100% | 0 |

- average: skip 100%, title 48%, title contract 68%, facts P/R 82%/98%, facts actionable 77%
- worst run: skip 99%, title 40%, title contract 60%, facts P/R 77%/93%, facts actionable 77%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 12/12 | 8/12 |
| refactor | 10 | 9/10 | 6/10 | 5/10 |
| test-add | 9 | 9/9 | 9/9 | 9/9 |
| security-fix | 9 | 9/9 | 8/9 | 3/9 |
| config-change | 12 | 12/12 | 10/12 | 7/12 |
| docs-update | 8 | 8/8 | 8/8 | 2/8 |
| deploy-change | 7 | 7/7 | 5/7 | 2/7 |
| skip-low-value | 20 | 19/20 | 0/20 | 0/20 |

## Run-to-run variance (repeated samples)

| metric | min | max | mean | std dev |
|---|---|---|---|---|
| type accuracy | 88% | 100% | 96% | 6% |
| title action class | 92% | 100% | 97% | 4% |
| title trace-visible subject | 86% | 100% | 93% | 6% |
| title identifiers | 80% | 95% | 88% | 6% |
| title trace contract | 70% | 90% | 81% | 8% |
| facts informative | 77% | 78% | 77% | 1% |
| skip accuracy | 99% | 100% | 100% | 1% |

## Inconsistent samples (type or action changed between runs)

1 of 13 repeated samples disagreed:

- refactor-invoice: type {unparsed, refactor}; action {unparsed, modify}

## Unreachable subject labels (excluded from A6b)

10 sample(s) declare subject tokens the recorded trace never contains:

- bug-offbyone-pagination: [pagination]
- bug-null-cache: [race]
- bug-race-counter: [race]
- bug-n-plus-one: [n+1]
- bug-regex-redos: [redos]
- bug-file-encoding: [encoding]
- refactor-auth-controller: [delegation]
- remove-dead-code: [dead, code]
- remove-stale-config: [stale, setting]
- remove-deprecated-endpoint: [deprecated, endpoint]

## Unverifiable type labels (excluded from the auditable type accuracy)

11 sample(s) expect a type the recorded trace cannot evidence:

- bug-offbyone-pagination: expected bugfix but the trace shows no defect or security evidence
- bug-null-cache: expected bugfix but the trace shows no defect or security evidence
- bug-payment-missing-order: expected bugfix but the trace shows no defect or security evidence
- bug-connection-leak: expected bugfix but the trace shows no defect or security evidence
- bug-csv-crlf: expected bugfix but the trace shows no defect or security evidence
- bug-race-counter: expected bugfix but the trace shows no defect or security evidence
- bug-n-plus-one: expected bugfix but the trace shows no defect or security evidence
- bug-dateformat-locale: expected bugfix but the trace shows no defect or security evidence
- bug-regex-redos: expected bugfix but the trace shows no defect or security evidence
- bug-file-encoding: expected bugfix but the trace shows no defect or security evidence
- security-xxe: expected bugfix but the trace shows no defect or security evidence

## Failure clusters (first run)

| cluster | misses | share of scored (66) |
|---|---|---|
| identifier missing | 3 | 5% |
| action class wrong | 5 | 8% |
| subject wrong | 4 | 6% |
| type wrong | 8 | 12% |

### identifier missing (3)

- bug-null-cache — expected identifiers [UserCache, computeIfAbsent], title 'Fixed UserCache concurrent null placeholder race in UserCache'
- deploy-k8s-probe — expected identifiers [deployment.yaml, /api/health], title 'Extended readiness probe in deployment.yaml'
- remove-feature-flag — expected identifiers [FeatureFlags, LEGACY_SEARCH], title 'Removed legacy-search constant in FeatureFlags'

### action class wrong (5)

- refactor-sql-repository — expected [modify], got 'secure' from title 'Hardened tenant lookup in UserRepository against SQL injection'
- security-sql-injection — expected [secure], got 'modify' from title 'Fixed SQL injection in UserDao.findByEmail using parameter placeholder'
- security-path-traversal — expected [secure], got 'modify' from title 'Fixed path traversal in FileDownloadController'
- security-rate-limit — expected [secure], got 'create' from title 'Added token-bucket 429 rejection in RateLimiter'
- config-docker-healthcheck — expected [modify], got 'create' from title 'Added HEALTHCHECK probe against /actuator/health in Dockerfile'

### subject wrong (4)

- refactor-sql-repository — expected subject tokens [jpql], title 'Hardened tenant lookup in UserRepository against SQL injection'
- security-redact-log — expected subject tokens [credentials], title 'Redacted password token secret values in LogSanitizer'
- security-cookie-httponly — expected subject tokens [httponly, cookie], title 'Hardened session cookie flags in SessionCookieConfig'
- deploy-helm-values — expected subject tokens [helm, values], title 'Changed replicaCount to 3 and memory limit to 2Gi in values.yaml'

### type wrong (8)

- refactor-config-loader — expected 'refactor', got 'feature' (title 'Extended ConfigLoader with typed accessors and unmodifiable map')
- refactor-sql-repository — expected 'refactor', got 'bugfix' (title 'Hardened tenant lookup in UserRepository against SQL injection')
- security-rate-limit — expected 'bugfix', got 'feature' (title 'Added token-bucket 429 rejection in RateLimiter')
- config-cors — expected 'change', got 'bugfix' (title 'Restricted /api/** CORS origins to https://app.acme.com in CorsConfig')
- deploy-terraform-iam — expected 'change', got 'bugfix' (title 'Restricted deploy IAM policy to AmazonS3ReadOnlyAccess in infra/main.tf')
- deploy-rollback-script — expected 'change', got 'feature' (title 'Added claude-mem deployment rollback script rollback.sh')
- remove-dead-code — expected 'refactor', got 'change' (title 'Removed legacySync method from OrderService')
- remove-feature-flag — expected 'change', got 'refactor' (title 'Removed legacy-search constant in FeatureFlags')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 8709 |
| bug-null-cache | bug-fix | observation | observation | pass | 75% | MISS (subject unreachable, id MISS, action hit) | miss | match | 100%/100% | 100% | 100%/100% | 5982 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/50% | 31370 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 11150 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 12677 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 67% | 100%/100% | 12886 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 8571 |
| bug-race-counter | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/67% | 100% | 50%/50% | 21998 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 11367 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 14032 |
| bug-regex-redos | bug-fix | observation | observation | pass | 100% | hit | miss | match | 50%/50% | 50% | 100%/100% | 13183 |
| bug-file-encoding | bug-fix | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 100%/100% | 9785 |
| refactor-invoice | refactor | observation | call-failed | FAIL | - | - | - | - | - | - | - | 144781 |
| refactor-auth-controller | refactor | observation | observation | pass | 67% | hit | miss | match | 50%/50% | 50% | 100%/100% | 11334 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | hit | miss | MISS | 67%/100% | 100% | 100%/100% | 16781 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 9080 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 67% | 100%/100% | 25071 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 0% | 100%/100% | 17528 |
| refactor-sql-repository | refactor | observation | observation | pass | 33% | MISS (subject MISS, id hit, action MISS) | miss | MISS | 50%/100% | 100% | 0%/0% | 20531 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 33% | 100%/100% | 15743 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 50% | 100%/100% | 15241 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 38218 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 10249 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/50% | 100% | 100%/100% | 5927 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 13485 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 50% | 100%/100% | 9171 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 18795 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/50% | 25900 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 50%/50% | 20344 |
| test-flaky-clock | test-add | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 100% | 100%/100% | 46069 |
| security-redact-log | security-fix | observation | observation | pass | 67% | MISS (subject MISS, id hit, action hit) | miss | match | 67%/100% | 67% | 50%/50% | 31271 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 33%/50% | 67% | 100%/100% | 12394 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 50% | 100%/100% | 62141 |
| security-csrf | security-fix | observation | observation | pass | 67% | hit | miss | match | 50%/50% | 100% | 50%/50% | 19596 |
| security-secret-config | security-fix | observation | observation | pass | 50% | hit | miss | match | 67%/100% | 100% | 100%/100% | 7936 |
| security-xxe | security-fix | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 9174 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 67%/100% | 100% | 100%/100% | 9592 |
| security-rate-limit | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | MISS | 33%/100% | 100% | 50%/50% | 14393 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 50%/100% | 100% | 0%/0% | 34492 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 67% | 100%/100% | 5259 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 5996 |
| config-log-level | config-change | observation | observation | pass | 75% | hit | miss | match | 50%/100% | 50% | 100%/100% | 18118 |
| config-cors | config-change | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 100% | 100%/100% | 13408 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 22210 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 10587 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 100%/100% | 100% | 100%/100% | 11105 |
| config-maven-plugin | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 48069 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 5818 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 100%/100% | 13227 |
| docs-runbook | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 0% | 100%/100% | 8132 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 15161 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 100% | 100%/100% | 13570 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 50%/50% | 10071 |
| docs-security-policy | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 50% | 50%/50% | 12368 |
| docs-contributing | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/50% | 50% | 100%/100% | 13301 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 75% | MISS (subject hit, id MISS, action hit) | miss | match | 67%/100% | 67% | 100%/100% | 16881 |
| deploy-github-actions | deploy-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 11795 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 100%/100% | 13154 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 75% | hit | miss | MISS | 67%/100% | 67% | 50%/50% | 19722 |
| deploy-helm-values | deploy-change | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 67%/100% | 100% | 100%/100% | 5678 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 33% | 100%/100% | 13096 |
| deploy-canary | deploy-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 11180 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2797 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2777 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4728 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4731 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5323 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4432 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5387 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5891 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3772 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2975 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2514 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2195 |
| skip-test-output-view | skip-low-value | skip | observation | FAIL | 0% | MISS (subject unreachable, id MISS, action MISS) | miss | MISS | 0%/0% | 0% | 0%/0% | 4284 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3190 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2581 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3415 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3075 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1997 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4118 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2862 |
| remove-dependency | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 14651 |
| remove-dead-code | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 67% | 100%/50% | 11371 |
| remove-stale-config | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 9978 |
| remove-deprecated-endpoint | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/67% | 100% | 100%/100% | 18828 |
| remove-feature-flag | config-change | observation | observation | pass | 100% | MISS (subject hit, id MISS, action hit) | hit | MISS | 100%/100% | 50% | 50%/50% | 34120 |

## Problems

- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed UserCache concurrent null placeholder race in UserCache')
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: title coverage 75% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: facts recall 67% (expected [incrementAndGet, AtomicLong, 1000], got [CounterService.increment replaced `counter.set(counter.get() + 1); return counter.get();` with `return counter.incrementAndGet();` in src/main/java/com/acme/metrics/CounterService.java., CounterServiceConcurrencyTest passes with 1000 threads producing exactly 1000, confirming no increments are lost.])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-regex-redos: facts recall 50% (expected [linear pattern, longInputFinishesUnder100ms], got [UrlValidator's URL pattern changed to linear `^https?://[^\s]+$`, removing the nested-quantifier regex., UrlValidatorTest#longInputFinishesUnder100ms passes after the regex replacement.])
- bug-file-encoding: title coverage 50% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed platform-default charset decoding in FileReaderFactory')
- refactor-invoice: call failed: NoContentException: all attempts returned no content (finish=LENGTH)
- refactor-auth-controller: title coverage 67% (expected keywords [refactor, authcontroller, tokenservice])
- refactor-auth-controller: title subject [delegation] missing (got 'Refactored JWT issuance in AuthController')
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [AuthController no longer builds JWTs inline and instead calls tokenService.issue(authentication)., AuthControllerTest remained unchanged and green after the delegation change.])
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title verb class 'refactor' missing (got 'Extended ConfigLoader with typed accessors and unmodifiable map')
- refactor-config-loader: type 'feature' != expected 'refactor'
- refactor-retry-policy: facts recall 50% (expected [BackoffStrategy, 100/200/400], got [RetryPolicy delay calculation now calls `backoff.nextDelay(attempt)` instead of computing the delay inline., RetryPolicyTest asserts the same 100/200/400 delay sequence, confirming behaviour is unchanged after extraction., The change touched one source file, src/main/java/com/acme/resilience/RetryPolicy.java, with no test or configuration edits.])
- refactor-sql-repository: title coverage 33% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title action [modify] missing: title leads 'secure' (got 'Hardened tenant lookup in UserRepository against SQL injection')
- refactor-sql-repository: title verb class 'refactor' missing (got 'Hardened tenant lookup in UserRepository against SQL injection')
- refactor-sql-repository: title subject [jpql] missing (got 'Hardened tenant lookup in UserRepository against SQL injection')
- refactor-sql-repository: title trace-visible subject [jpql] missing (got 'Hardened tenant lookup in UserRepository against SQL injection')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: concepts recall 0% (expected [what-changed, pattern], got [problem-solution, gotcha])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles uses @ParameterizedTest with @CsvSource values "1,100", "2,200", and "3,400" for attempts 1-3., RetryPolicyTest#delayDoubles asserts policy.delay(attempt) equals expected, expecting 100, 200, and 400 ms., RetryPolicyTest.java is located at src/test/java/com/acme/resilience/RetryPolicyTest.java and all 3 parameterized tests pass.])
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable is a @ParameterizedTest using @ValueSource with inputs "a,b", "a\"b", and "a\nb"., All 3 parameterized cases pass, confirming quoting is stable for commas, quotes, and newlines.])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-contract-openapi: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-flaky-clock: title verb class 'fix' missing (got 'Updated timing to Clock.fixed removing Thread.sleep flakiness in SchedulerTest')
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: title subject [credentials] missing (got 'Redacted password token secret values in LogSanitizer')
- security-redact-log: title trace-visible subject [credentials] missing (got 'Redacted password token secret values in LogSanitizer')
- security-redact-log: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, gotcha])
- security-sql-injection: title action [secure] missing: title leads 'modify' (got 'Fixed SQL injection in UserDao.findByEmail using parameter placeholder')
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail now executes jdbcTemplate.query with "select * from users where email = ?" and passes email as a bound parameter, The previous implementation built the query by string concatenation, allowing quote characters in email to alter the SQL, SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the change])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Restricted endpoint authorization to ROLE_ADMIN in AdminController')
- security-csrf: title coverage 67% (expected keywords [fix, csrf, csrffilter])
- security-csrf: title verb class 'fix' missing (got 'Hardened CSRF token rejection in CsrfFilter')
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter.java now calls response.sendError(403) when csrfToken.matches(request) returns false., CsrfFilterTest#postWithoutTokenIs403 passes after the change.])
- security-csrf: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-secret-config: title coverage 50% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: title verb class 'remove' missing (got 'Redacted ACME_API_KEY secret in application.yml')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Redacted ACME_API_KEY secret in application.yml')
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-xxe: title verb class 'harden' missing (got 'Restricted DOCTYPE declarations in XmlImportService to block XXE')
- security-path-traversal: title action [secure] missing: title leads 'modify' (got 'Fixed path traversal in FileDownloadController')
- security-rate-limit: title action [secure] missing: title leads 'create' (got 'Added token-bucket 429 rejection in RateLimiter')
- security-rate-limit: type 'feature' != expected 'bugfix'
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [what-changed, pattern])
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- security-cookie-httponly: title trace-visible subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- security-cookie-httponly: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, what-changed])
- config-hikari-pool: title coverage 75% (expected keywords [change, hikari, pool, size])
- config-hikari-pool: title verb class 'change' missing (got 'Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml')
- config-log-level: title coverage 75% (expected keywords [change, logback, root, json])
- config-log-level: title verb class 'change' missing (got 'Set root logger level to INFO and JSON appender in logback-spring.xml')
- config-log-level: title subject [log, level] missing (got 'Set root logger level to INFO and JSON appender in logback-spring.xml')
- config-cors: type 'bugfix' != expected 'change'
- config-docker-healthcheck: title action [modify] missing: title leads 'create' (got 'Added HEALTHCHECK probe against /actuator/health in Dockerfile')
- config-maven-plugin: title coverage 75% (expected keywords [add, jacoco, plugin, pom])
- config-maven-plugin: title verb class 'add' missing (got 'Extended JaCoCo coverage with jacoco-maven-plugin 0.8.12 in pom.xml')
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Documented local setup instructions in README.md')
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title verb class 'add' missing (got 'Documented queue recovery procedure in docs/runbook.md')
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title verb class 'change' missing (got 'Documented 2.4.0 release with offline replay evaluation and V20 migration fix in CHANGELOG.md')
- docs-migration-guide: title coverage 75% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [what-changed, pattern])
- docs-security-policy: title coverage 75% (expected keywords [add, security, policy, disclosure])
- docs-security-policy: title verb class 'add' missing (got 'Documented vulnerability disclosure policy in SECURITY.md')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 67% (expected keywords [update, contributing, verify-local])
- docs-contributing: title verb class 'change' missing (got 'Documented verify-local.sh pre-PR requirement in CONTRIBUTING.md')
- docs-contributing: facts recall 50% (expected [verify-local.sh, four test layers], got [CONTRIBUTING.md now states "Run ./scripts/verify-local.sh before opening a pull request.", ./scripts/verify-local.sh is the script contributors must run before submitting a pull request.])
- deploy-k8s-probe: title lost identifiers [deployment.yaml, /api/health] (got 'Extended readiness probe in deployment.yaml')
- deploy-k8s-probe: title verb class 'add' missing (got 'Extended readiness probe in deployment.yaml')
- deploy-github-actions: title coverage 75% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: title verb class 'add' missing (got 'Extended ci.yml Java matrix to JDK 21 and 25')
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-terraform-iam: title coverage 75% (expected keywords [restrict, iam, terraform, s3])
- deploy-terraform-iam: type 'bugfix' != expected 'change'
- deploy-terraform-iam: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, gotcha])
- deploy-helm-values: title coverage 75% (expected keywords [change, helm, replicas, memory])
- deploy-helm-values: title subject [helm, values] missing (got 'Changed replicaCount to 3 and memory limit to 2Gi in values.yaml')
- deploy-helm-values: title trace-visible subject [helm, values] missing (got 'Changed replicaCount to 3 and memory limit to 2Gi in values.yaml')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 75% (expected keywords [enable, canary, rollback, threshold])
- skip-test-output-view: expected <skip> but produced an observation
- remove-dead-code: title coverage 50% (expected keywords [remove, dead, code, orderservice])
- remove-dead-code: title subject [dead, code] missing (got 'Removed legacySync method from OrderService')
- remove-dead-code: type 'change' != expected 'refactor'
- remove-dead-code: concepts recall 50% (expected [what-changed, pattern], got [what-changed, what-changed])
- remove-stale-config: title subject [stale, setting] missing (got 'Removed legacy-search.enabled flag from application.yml')
- remove-deprecated-endpoint: title coverage 75% (expected keywords [remove, deprecated, endpoint, usercontroller])
- remove-deprecated-endpoint: title subject [deprecated, endpoint] missing (got 'Removed listLegacy /v1/users endpoint in UserController')
- remove-deprecated-endpoint: facts recall 67% (expected [listLegacy, /v1/users, /v2/users], got [UserController.listLegacy method and its @GetMapping("/v1/users") mapping were removed from src/main/java/com/acme/users/UserController.java.])
- remove-feature-flag: title lost identifiers [FeatureFlags, LEGACY_SEARCH] (got 'Removed legacy-search constant in FeatureFlags')
- remove-feature-flag: type 'refactor' != expected 'change'
- remove-feature-flag: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, gotcha])

## Raw answers behind problems

### bug-offbyone-pagination

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed pageOffset off-by-one pagination in OrderQuery</title>   <subtitle>OrderQuery.pageOffset returned (page * size - 1), causing the first page to start at offset -1.</subtitle>   <facts>     <fact>OrderQuery.pageOffset previously computed offset as page * size - 1, producing -1 for the first page (page=0, size>0).</fact>     <fact>The calculat... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache concurrent null placeholder race in UserCache</title>   <subtitle>Replaced direct cache.get with computeIfAbsent so concurrent callers cannot both write a null placeholder.</subtitle>   <facts>     <fact>UserCache.get previously called cache.get(id) and could allow two concurrent callers to both write a null placeholder.</fact>     <fact... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added reportUsesBusinessTimezone off-by-one-day timezone test in DailyReportTest</title>   <subtitle>DailyReportTest#reportUsesBusinessTimezone reproduces the off-by-one-day bug for Asia/Shanghai business date conversion</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins input time 2026-11-01T23:30Z and expects business date... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed null return for missing orderId in PaymentService</title>   <subtitle>PaymentService now throws PaymentNotFoundException when an orderId lookup yields no result.</subtitle>   <facts>     <fact>PaymentService no longer returns null for a missing orderId and instead throws PaymentNotFoundException.</fact>     <fact>Implementation uses repository.fin... [truncated]
```

### bug-jwt-expiry

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed JWT expiration boundary check in JwtVerifier</title>   <subtitle>JwtVerifier now rejects tokens whose expiration equals the current instant, closing a security edge case.</subtitle>   <facts>     <fact>JwtVerifier's expiration check changed from `claims.getExpiration().before(now)` to `claims.getExpiration().isBefore(now) || claims.getExpir... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed embedded line endings normalisation to CRLF in CsvExporter</title>   <subtitle>CsvExporter now converts embedded \r?\n to \r\n before quoting so multiline fields keep the CSV row count.</subtitle>   <facts>     <fact>CsvExporter.java now writes each line via writer.write(line.replaceAll("\\r?\\n", "\\r\n")) before quoting.</fact>     <fact>The rep... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed counter increment race condition in CounterService</title>   <subtitle>CounterService.increment now uses AtomicLong.incrementAndGet instead of a non-atomic get/set pair, preventing lost updates under concurrency.</subtitle>   <facts>     <fact>CounterService.increment replaced `counter.set(counter.get() + 1); return counter.get();` with `return co... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator regex catastrophic backtracking in UrlValidator</title>   <subtitle>Replaced nested-quantifier URL regex with a linear pattern to eliminate catastrophic backtracking on long inputs.</subtitle>   <facts>     <fact>UrlValidator's URL pattern changed to linear `^https?://[^\s]+$`, removing the nested-quantifier regex.</fact>     <fact>Ur... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed platform-default charset decoding in FileReaderFactory</title>   <subtitle>FileReaderFactory now always opens UTF-8 instead of relying on the JVM platform default charset.</subtitle>   <facts>     <fact>FileReaderFactory constructs InputStreamReader with StandardCharsets.UTF_8 instead of the platform default charset.</fact>     <fact>FileReaderFac... [truncated]
```

### refactor-invoice

```text
(no raw answer: the call failed before returning)
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored JWT issuance in AuthController</title>   <subtitle>AuthController now delegates token creation to TokenService.issue instead of building JWTs inline.</subtitle>   <facts>     <fact>AuthController no longer builds JWTs inline and instead calls tokenService.issue(authentication).</fact>     <fact>AuthControllerTest remained unchanged and gree... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>feature</type>   <title>Extended ConfigLoader with typed accessors and unmodifiable map</title>   <subtitle>ConfigLoader now returns an unmodifiable map and exposes typed accessors for configuration values.</subtitle>   <facts>     <fact>ConfigLoader returns an unmodifiable map collected via `toUnmodifiableMap(...)` stream</fact>     <fact>ConfigLoader now exposes typed acces... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Refactored delay calculation into BackoffStrategy in RetryPolicy</title>   <subtitle>RetryPolicy now delegates delay computation to BackoffStrategy while preserving the existing exponential sequence.</subtitle>   <facts>     <fact>RetryPolicy delay calculation now calls `backoff.nextDelay(attempt)` instead of computing the delay inline.</fact>     <fa... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Hardened tenant lookup in UserRepository against SQL injection</title>   <subtitle>Replaced string-concatenated SQL with a parameterized JPQL @Query binding tenantId.</subtitle>   <facts>     <fact>UserRepository now declares the JPQL @Query "select u from User u where u.tenantId = :tenantId" instead of concatenating the tenant value into SQL.</fact>   ... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added reportUsesBusinessTimezone reproduction test for UTC-vs-business-date bug in DailyReportTest</title>   <subtitle>Test pins Asia/Shanghai timezone boundary where UTC date differs from business date.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone parses Instant 2026-11-01T23:30:00Z and asserts dateAt with Asia/Shanghai re... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added locked-account 401 reproduction test LoginControllerTest</title>   <subtitle>New test pins the reported defect where locked accounts return 401 instead of 423.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true.</fact>     <fact>LoginControllerTest#lockedAccountReturns423 performs... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized cases for exponential backoff</title>   <subtitle>Parameterized test verifies retry delay doubles to 100, 200, and 400 ms across attempts 1-3.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles uses @ParameterizedTest with @CsvSource values "1,100", "2,200", and "3,400" for attempts 1-3.</fact> ... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added quotingIsStable parameterized cases to CsvExporterTest</title>   <subtitle>New parameterized test verifies CSV quoting stability across commas, quotes, and newlines.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable is a @ParameterizedTest using @ValueSource with inputs "a,b", "a\"b", and "a\nb".</fact>     <fact>All 3 parameterized ... [truncated]
```

### test-repro-null-cache

```text
```xml <observation>   <type>feature</type>   <title>Added concurrentReadersNeverSeeNull reproducing UserCache null in CacheConcurrencyTest</title>   <subtitle>Reproduction test pins an intermittent null from UserCache under 1000 concurrent readers.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull asserts cache.get("k") is not null across 1000 parallel readers.</fa... [truncated]
```

### test-contract-openapi

```text
<observation>   <type>feature</type>   <title>Added OpenApiContractTest diffing springdoc output against docs/openapi.yaml</title>   <subtitle>New contract test fails the build when generated OpenAPI schema drifts from the committed spec file.</subtitle>   <facts>     <fact>OpenApiContractTest#generatedSpecMatchesCommittedFile compares springdoc-generated output against docs/openapi.yaml and fails... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Updated timing to Clock.fixed removing Thread.sleep flakiness in SchedulerTest</title>   <subtitle>SchedulerTest now injects a deterministic fixed Clock instead of relying on Thread.sleep, eliminating scheduling flakiness.</subtitle>   <facts>     <fact>SchedulerTest sets `scheduler.clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Redacted password token secret values in LogSanitizer</title>   <subtitle>Log output now masks credential values for password, token and secret keys before logging.</subtitle>   <facts>     <fact>LogSanitizer.java replaces matches of the regex (password|token|secret)=\S+ with $1=*** before logging.</fact>     <fact>Redaction covers exactly three keys: p... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed SQL injection in UserDao.findByEmail using parameter placeholder</title>   <subtitle>UserDao.findByEmail now binds the email value via a prepared-statement placeholder instead of concatenating it into the SQL string.</subtitle>   <facts>     <fact>UserDao.findByEmail now executes jdbcTemplate.query with "select * from users where email = ?" and pa... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Restricted endpoint authorization to ROLE_ADMIN in AdminController</title>   <subtitle>Added @PreAuthorize("hasRole('ADMIN')") to AdminController so every endpoint denies non-admin callers with 403.</subtitle>   <facts>     <fact>AdminController is annotated with @PreAuthorize("hasRole('ADMIN')"), requiring ROLE_ADMIN on every endpoint.</fact>     <fact... [truncated]
```

### security-csrf

```text
```xml <observation>   <type>bugfix</type>   <title>Hardened CSRF token rejection in CsrfFilter</title>   <subtitle>CsrfFilter now returns HTTP 403 for state-changing requests with a mismatched CSRF token.</subtitle>   <facts>     <fact>CsrfFilter.java now calls response.sendError(403) when csrfToken.matches(request) returns false.</fact>     <fact>CsrfFilterTest#postWithoutTokenIs403 passes after... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Redacted ACME_API_KEY secret in application.yml</title>   <subtitle>Replaced hardcoded API key literal with environment variable reference to prevent secret leakage</subtitle>   <facts>     <fact>application.yml previously contained literal value `sk-live-123` at the `api-key` property</fact>     <fact>application.yml now sets `api-key: ${ACME_API_KEY}`... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Restricted DOCTYPE declarations in XmlImportService to block XXE</title>   <subtitle>Disabled XML DOCTYPE declarations in the import parser, closing an XML external entity injection hole.</subtitle>   <facts>     <fact>XmlImportService now sets feature "http://apache.org/xml/features/disallow-doctype-decl" to true on the XML factory.</fact>     <fact>Xx... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Fixed path traversal in FileDownloadController</title>   <subtitle>FileDownloadController now rejects names that resolve outside the base directory, preventing directory escape.</subtitle>   <facts>     <fact>FileDownloadController resolves the requested name via base.resolve(name).normalize() before serving files</fact>     <fact>FileDownloadController... [truncated]
```

### security-rate-limit

```text
<observation>   <type>feature</type>   <title>Added token-bucket 429 rejection in RateLimiter</title>   <subtitle>RateLimiter now throws TooManyRequestsException with HTTP 429 when the token bucket is empty.</subtitle>   <facts>     <fact>RateLimiter.java calls bucket.tryConsume(1) and throws TooManyRequestsException when it returns false</fact>     <fact>RateLimiter now returns HTTP 429 when the ... [truncated]
```

### security-cookie-httponly

```text
```xml <observation>   <type>bugfix</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>Session cookie now sets HttpOnly, Secure, and SameSite=Strict to mitigate XSS, MITM, and CSRF risks.</subtitle>   <facts>     <fact>SessionCookieConfig sets cookie.setHttpOnly(true), cookie.setSecure(true), and cookie.setSameSite(Strict) on the session cookie.</fact>     <fa... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml</title>   <subtitle>Hikari connection pool sizing was configured with a max of 20 and minimum idle of 5.</subtitle>   <facts>     <fact>application.yml sets spring.datasource.hikari.maximum-pool-size to 20</fact>     <fact>application.yml sets spring.datasource.hikari.minimum-id... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Set root logger level to INFO and JSON appender in logback-spring.xml</title>   <subtitle>Root logging switched to INFO level with structured JSON appender output.</subtitle>   <facts>     <fact>logback-spring.xml sets the root logger to level="INFO" and references the JSON appender via &lt;appender-ref ref="JSON"/&gt;.</fact>     <fact>LogbackConfigTes... [truncated]
```

### config-cors

```text
<observation>   <type>bugfix</type>   <title>Restricted /api/** CORS origins to https://app.acme.com in CorsConfig</title>   <subtitle>CORS for /api/** is narrowed to a single trusted origin with only GET and POST methods.</subtitle>   <facts>     <fact>CorsConfig registry.addMapping("/api/**") now sets allowedOrigins to https://app.acme.com only.</fact>     <fact>allowedMethods for the /api/** ma... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added HEALTHCHECK probe against /actuator/health in Dockerfile</title>   <subtitle>Dockerfile now monitors container health with a 30 second curl probe to the Actuator health endpoint.</subtitle>   <facts>     <fact>Dockerfile defines HEALTHCHECK with `--interval=30s` running `curl -f http://localhost:8080/actuator/health || exit 1`</fact>     <fact>The... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Extended JaCoCo coverage with jacoco-maven-plugin 0.8.12 in pom.xml</title>   <subtitle>Added the JaCoCo code coverage plugin to the Maven build so verify produces a coverage report.</subtitle>   <facts>     <fact>pom.xml now declares the jacoco-maven-plugin with version 0.8.12.</fact>     <fact>Running mvn verify writes the coverage report to target/si... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Documented local setup instructions in README.md</title>   <subtitle>Added a three-step Setup section covering JDK 21, Docker Compose Postgres, and Maven Spring Boot run.</subtitle>   <facts>     <fact>README.md now contains a "## Setup" section with three numbered steps for local development</fact>     <fact>Setup step 1 specifies "Install JDK 21"</fac... [truncated]
```

### docs-runbook

```text
```xml <observation>   <type>change</type>   <title>Documented queue recovery procedure in docs/runbook.md</title>   <subtitle>New runbook covers inspecting pending_messages, restarting the worker, and verifying /api/health.</subtitle>   <facts>     <fact>docs/runbook.md defines a three-step queue recovery procedure: check pending_messages, restart the worker, verify /api/health.</fact>     <fact>... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Documented 2.4.0 release with offline replay evaluation and V20 migration fix in CHANGELOG.md</title>   <subtitle>CHANGELOG.md gains a 2.4.0 entry dated 2026-11-03 noting the new offline replay evaluation and the V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md adds a "## 2.4.0 - 2026-11-03" release entry.</fact>     <fact>The 2.4... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisite in MIGRATION.md</title>   <subtitle>Added V20 section requiring mem_sessions backup and app.manual_sentinel_reviewed=true before migration.</subtitle>   <facts>     <fact>docs/MIGRATION.md now includes a V20 section with prerequisite instructions.</fact>     <fact>V20 requires backing up mem_sessions before running ... [truncated]
```

### docs-security-policy

```text
```xml <observation>   <type>change</type>   <title>Documented vulnerability disclosure policy in SECURITY.md</title>   <subtitle>Added SECURITY.md defining security@acme.com as the disclosure channel and forbidding public issues for vulnerabilities.</subtitle>   <facts>     <fact>SECURITY.md specifies security@acme.com as the vulnerability reporting channel</fact>     <fact>SECURITY.md forbids op... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Documented verify-local.sh pre-PR requirement in CONTRIBUTING.md</title>   <subtitle>CONTRIBUTING.md now requires running ./scripts/verify-local.sh before opening a pull request and names the four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md now states "Run ./scripts/verify-local.sh before opening a pull request."</fact>     <fact>The doc... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Extended readiness probe in deployment.yaml</title>   <subtitle>Added an HTTP readiness probe on /api/health:37777 with a 15 second initial delay to the deployment manifest.</subtitle>   <facts>     <fact>deployment.yaml now defines a readinessProbe using httpGet path /api/health on port 37777.</fact>     <fact>The readinessProbe sets initialDelaySecond... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Extended ci.yml Java matrix to JDK 21 and 25</title>   <subtitle>CI workflow now builds the backend against JDK 21 and 25 via a strategy matrix.</subtitle>   <facts>     <fact>.github/workflows/ci.yml now defines strategy.matrix.java with values 21 and 25.</fact>     <fact>The backend build in ci.yml runs on both JDK 21 and JDK 25.</fact>     <fact>Work... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>bugfix</type>   <title>Restricted deploy IAM policy to AmazonS3ReadOnlyAccess in infra/main.tf</title>   <subtitle>Deploy role now has read-only S3 access instead of full S3 permissions.</subtitle>   <facts>     <fact>infra/main.tf attaches policy ARN arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess to the aws_iam_role_policy deploy resource.</fact>     <fact>The deploy role pr... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Changed replicaCount to 3 and memory limit to 2Gi in values.yaml</title>   <subtitle>Deployment scaling increased to 3 replicas with a 2Gi memory cap per pod.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount to 3</fact>     <fact>infra/values.yaml caps resources.limits.memory at 2Gi</fact>     <fact>helm template renders 3 pods from th... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added claude-mem deployment rollback script rollback.sh</title>   <subtitle>New shell script automates undoing the claude-mem Kubernetes deployment and waiting for rollout status.</subtitle>   <facts>     <fact>scripts/rollback.sh executes `kubectl rollout undo deployment/claude-mem` followed by `kubectl rollout status deployment/claude-mem`</fact>    ... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Enabled 10 percent canary weight with 2 percent error threshold in canary.yaml</title>   <subtitle>Canary deployment configuration now rolls out 10 percent of traffic with automatic rollback above 2 percent error rate.</subtitle>   <facts>     <fact>canary.yaml sets canary.enabled to true to activate canary deployment</fact>     <fact>canary.yaml sets c... [truncated]
```

### skip-test-output-view

```text
<observation>   <type>discovery</type>   <title>Verified full test suite passes with mvn -q test</title>   <subtitle>All 360 tests pass with zero failures, errors, or skips on a clean codebase.</subtitle>   <facts>     <fact>mvn -q test reports Tests run: 360, Failures: 0, Errors: 0, Skipped: 0</fact>     <fact>No source files were changed during the test run</fact>   </facts>   <narrative>The com... [truncated]
```

### remove-dead-code

```text
<observation>   <type>change</type>   <title>Removed legacySync method from OrderService</title>   <subtitle>Deleted the unused legacySync method and its warehouse.push call from OrderService.</subtitle>   <facts>     <fact>OrderService.legacySync was removed from src/main/java/com/acme/orders/OrderService.java.</fact>     <fact>Nothing else references legacySync in the codebase.</fact>     <fact>... [truncated]
```

### remove-stale-config

```text
<observation>   <type>change</type>   <title>Removed legacy-search.enabled flag from application.yml</title>   <subtitle>Deleted the legacy-search.enabled toggle so the search module consumes only search-v2 configuration.</subtitle>   <facts>     <fact>src/main/resources/application.yml no longer defines the `legacy-search.enabled` key.</fact>     <fact>The search module now reads only `search-v2`... [truncated]
```

### remove-deprecated-endpoint

```text
<observation>   <type>change</type>   <title>Removed listLegacy /v1/users endpoint in UserController</title>   <subtitle>Deleted the legacy v1 user listing endpoint, leaving only the /v2/users mapping in UserController.</subtitle>   <facts>     <fact>UserController.listLegacy method and its @GetMapping("/v1/users") mapping were removed from src/main/java/com/acme/users/UserController.java.</fact> ... [truncated]
```

### remove-feature-flag

```text
<observation>   <type>refactor</type>   <title>Removed legacy-search constant in FeatureFlags</title>   <subtitle>Deleted the unused LEGACY_SEARCH constant; remaining callers reference SEARCH_V2.</subtitle>   <facts>     <fact>FeatureFlags.java no longer declares the public constant LEGACY_SEARCH with value "legacy-search".</fact>     <fact>Callers previously using LEGACY_SEARCH now use SEARCH_V2.... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
