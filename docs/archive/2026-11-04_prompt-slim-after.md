golden set: main (87 samples)

# Model output quality evaluation

- Date: 2026-11-04
- Phase: prompt-slim-after
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 87 unique; runs scored: 3; key repeat: 3; real calls: 113 (cap none); duration: 1693206 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 100% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 20/0/0/67 |
| format errors | 0 |
| validation rejections | 0 |
| call failures | 0 |
| call success rate | 100% (0/113 failed) |
| title exact-hit rate (legacy) | 36% |
| title normalized hit rate (legacy) | 40% |
| title action-class hit rate (A6) | 91% (67 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 75% |
| title subject hit rate (legacy, diagnostic) | 73% |
| title trace-visible subject hit rate (A6b) | 79% (57 scored, 10 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 60% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 67% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 48% |
| title identifier hit rate | 88% |
| type accuracy (raw) | 90% |
| type accuracy (auditable labels only) | 88% (11 unverifiable label(s) excluded) |
| facts keyword precision (legacy) / recall | 79% / 95% |
| facts informative precision | 79% |
| facts trace-support rate | 100% |
| concepts precision / recall | 85% / 81% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 13857 / 40439 / 51060 |
| tokens p50 / p95 / max / mean per call | 3289 / 4695 / 5841 / 3412 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 79% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 91% | PASS |
| A6b | title trace-visible subject | >= 90% | 79% | **FAIL** |
| A6c | title identifiers | = 100% | 88% | **FAIL** |
| A6 | all three title dimensions together | all pass | 67% | **FAIL** |
| A11 | p95 latency vs baseline | <= 1.5x baseline | 40439 ms (baseline 33716 ms) | PASS |
| A12 | p95 tokens vs baseline | <= 1.5x baseline | 4695 tokens (baseline 5763) | PASS |
| A13 | provider call success rate | >= 99% (at most 1 failed call(s) per run) | 100% (0/113 failed) | PASS |

## Status distribution

| status | calls |
|---|---|
| 200 | 87 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 87 | 100% | 40% | 60% | 79% | 79% | 95% | 0 |
| 1 | 13 | 100% | 40% | 60% | 88% | 88% | 100% | 0 |
| 2 | 13 | 100% | 30% | 30% | 77% | 78% | 100% | 0 |

- average: skip 100%, title 37%, title contract 50%, facts P/R 81%/98%, facts actionable 82%
- worst run: skip 100%, title 30%, title contract 30%, facts P/R 77%/95%, facts actionable 78%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 12/12 | 6/12 |
| refactor | 10 | 10/10 | 7/10 | 5/10 |
| test-add | 9 | 9/9 | 9/9 | 7/9 |
| security-fix | 9 | 9/9 | 8/9 | 2/9 |
| config-change | 12 | 12/12 | 11/12 | 6/12 |
| docs-update | 8 | 8/8 | 8/8 | 1/8 |
| deploy-change | 7 | 7/7 | 5/7 | 0/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Run-to-run variance (repeated samples)

| metric | min | max | mean | std dev |
|---|---|---|---|---|
| type accuracy | 90% | 100% | 97% | 5% |
| title action class | 91% | 100% | 97% | 4% |
| title trace-visible subject | 79% | 100% | 88% | 9% |
| title identifiers | 50% | 88% | 69% | 16% |
| title trace contract | 50% | 70% | 62% | 9% |
| facts informative | 78% | 88% | 82% | 5% |
| skip accuracy | 100% | 100% | 100% | 0% |

## Inconsistent samples (type or action changed between runs)

None: every repeated sample kept the same type and action class.

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

| cluster | misses | share of scored (67) |
|---|---|---|
| identifier missing | 8 | 12% |
| action class wrong | 6 | 9% |
| subject wrong | 12 | 18% |
| type wrong | 7 | 10% |

### identifier missing (8)

- bug-null-cache — expected identifiers [UserCache, computeIfAbsent], title 'Fixed concurrent null placeholder race in UserCache'
- bug-timezone-report — expected identifiers [DailyReportTest, reportUsesBusinessTimezone], title 'Added reproduction test for daily report off-by-one-day bug in DailyReportTest'
- test-repro-timezone — expected identifiers [DailyReportTest, reportUsesBusinessTimezone], title 'Added UTC vs business date reproduction test DailyReportTest'
- config-hikari-pool — expected identifiers [maximum-pool-size], title 'Extended Hikari connection pool sizing in application.yml'
- docs-api-observations — expected identifiers [API.md, /api/observations], title 'Documented pagination fields hasMore and items in API.md'
- deploy-k8s-probe — expected identifiers [deployment.yaml, /api/health], title 'Extended readinessProbe in deployment.yaml'
- deploy-docker-multistage — expected identifiers [Dockerfile, eclipse-temurin:21-jre], title 'Converted single-stage to multi-stage in Dockerfile'
- remove-stale-config — expected identifiers [legacy-search.enabled, application.yml], title 'Removed legacy-search toggle in application.yml'

### action class wrong (6)

- bug-jwt-expiry — expected [modify], got 'secure' from title 'Hardened JWT expiration boundary check in JwtVerifier'
- refactor-config-loader — expected [modify], got 'secure' from title 'Hardened config map immutability in ConfigLoader'
- refactor-sql-repository — expected [modify], got 'secure' from title 'Hardened tenant lookup SQL with JPQL @Query in UserRepository'
- security-rate-limit — expected [secure], got 'modify' from title 'Extended token bucket to return 429 in RateLimiter'
- config-maven-plugin — expected [modify], got 'create' from title 'Added jacoco-maven-plugin 0.8.12 to pom.xml'
- docs-security-policy — expected [document], got 'create' from title 'Added vulnerability disclosure policy in SECURITY.md'

### subject wrong (12)

- bug-timezone-report — expected subject tokens [timezone], title 'Added reproduction test for daily report off-by-one-day bug in DailyReportTest'
- refactor-config-loader — expected subject tokens [unmodifiable, map], title 'Hardened config map immutability in ConfigLoader'
- refactor-order-mapper — expected subject tokens [convertValue], title 'Refactored entity-to-DTO mapping in OrderMapper'
- test-repro-timezone — expected subject tokens [timezone], title 'Added UTC vs business date reproduction test DailyReportTest'
- test-unit-retry — expected subject tokens [parameterized], title 'Added RetryPolicy delay backoff parameterization test in RetryPolicyTest'
- security-redact-log — expected subject tokens [credentials], title 'Redacted password, token and secret values in LogSanitizer'
- security-cookie-httponly — expected subject tokens [httponly, cookie], title 'Hardened session cookie flags in SessionCookieConfig'
- config-log-level — expected subject tokens [level], title 'Updated root logger to INFO with JSON appender in logback-spring.xml'
- docs-api-observations — expected subject tokens [observations, pagination], title 'Documented pagination fields hasMore and items in API.md'
- docs-architecture-adr — expected subject tokens [adr], title 'Recorded extraction validation decision in 0007-extraction-validation.md'
- deploy-terraform-iam — expected subject tokens [iam, policy], title 'Restricted deploy role S3 permissions in infra/main.tf'
- deploy-helm-values — expected subject tokens [helm, values], title 'Changed replicaCount and memory limit in values.yaml'

### type wrong (7)

- refactor-config-loader — expected 'refactor', got 'change' (title 'Hardened config map immutability in ConfigLoader')
- refactor-sql-repository — expected 'refactor', got 'bugfix' (title 'Hardened tenant lookup SQL with JPQL @Query in UserRepository')
- security-rate-limit — expected 'bugfix', got 'feature' (title 'Extended token bucket to return 429 in RateLimiter')
- config-cors — expected 'change', got 'bugfix' (title 'Restricted CORS origins and methods in CorsConfig')
- deploy-terraform-iam — expected 'change', got 'bugfix' (title 'Restricted deploy role S3 permissions in infra/main.tf')
- deploy-rollback-script — expected 'change', got 'feature' (title 'Added claude-mem rollback script rollback.sh')
- remove-dead-code — expected 'refactor', got 'change' (title 'Removed legacySync from OrderService')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 21775 |
| bug-null-cache | bug-fix | observation | observation | pass | 75% | MISS (subject unreachable, id MISS, action hit) | miss | match | 100%/100% | 100% | 100%/100% | 10960 |
| bug-timezone-report | bug-fix | observation | observation | pass | 67% | MISS (subject MISS, id MISS, action hit) | miss | match | 50%/100% | 50% | 50%/50% | 7158 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 50%/50% | 14649 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 12528 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 50%/67% | 100% | 50%/50% | 11445 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 16993 |
| bug-race-counter | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 15091 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 15592 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 18636 |
| bug-regex-redos | bug-fix | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 100% | 100%/100% | 12114 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 10750 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 67% | 100%/100% | 10628 |
| refactor-auth-controller | refactor | observation | observation | pass | 67% | hit | miss | match | 50%/100% | 50% | 100%/100% | 16721 |
| refactor-config-loader | refactor | observation | observation | pass | 33% | MISS (subject MISS, id hit, action MISS) | miss | MISS | 67%/100% | 67% | 100%/50% | 44236 |
| refactor-order-mapper | refactor | observation | observation | pass | 67% | MISS (subject MISS, id hit, action hit) | miss | match | 50%/100% | 50% | 100%/100% | 16822 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 18260 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 0% | 100%/100% | 17582 |
| refactor-sql-repository | refactor | observation | observation | pass | 67% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 50%/50% | 14941 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 100%/100% | 31894 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 100% | 100%/50% | 7877 |
| test-repro-timezone | test-add | observation | observation | pass | 67% | MISS (subject MISS, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/50% | 17690 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 15611 |
| test-unit-retry | test-add | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 67%/50% | 100% | 100%/100% | 14003 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 13107 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 6627 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 21807 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/50% | 19359 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 17624 |
| test-flaky-clock | test-add | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 100% | 100%/100% | 21105 |
| security-redact-log | security-fix | observation | observation | pass | 67% | MISS (subject MISS, id hit, action hit) | miss | match | 100%/100% | 100% | 50%/50% | 14453 |
| security-sql-injection | security-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 18481 |
| security-authz-admin | security-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 0%/0% | 45957 |
| security-csrf | security-fix | observation | observation | pass | 67% | hit | miss | match | 50%/50% | 100% | 100%/100% | 19389 |
| security-secret-config | security-fix | observation | observation | pass | 50% | hit | miss | match | 67%/100% | 100% | 50%/50% | 40439 |
| security-xxe | security-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 37582 |
| security-path-traversal | security-fix | observation | observation | pass | 75% | hit | miss | match | 50%/100% | 100% | 100%/100% | 30558 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS (subject hit, id hit, action MISS) | miss | MISS | 100%/100% | 100% | 50%/50% | 42581 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 50%/100% | 50% | 0%/0% | 29364 |
| config-hikari-pool | config-change | observation | observation | pass | 50% | MISS (subject hit, id MISS, action hit) | miss | match | 50%/100% | 50% | 100%/100% | 22555 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 7545 |
| config-log-level | config-change | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 50%/50% | 0% | 100%/100% | 15582 |
| config-cors | config-change | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 67% | 100%/100% | 51060 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 11440 |
| config-feature-flags | config-change | observation | observation | pass | 50% | hit | miss | match | 33%/100% | 33% | 100%/100% | 18276 |
| config-docker-healthcheck | config-change | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 100%/100% | 10306 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 100%/100% | 50% | 50%/50% | 26493 |
| docs-api-observations | docs-update | observation | observation | pass | 75% | MISS (subject MISS, id MISS, action hit) | miss | match | 50%/100% | 50% | 100%/100% | 10726 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 100%/100% | 13857 |
| docs-runbook | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 0% | 100%/100% | 8822 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 9670 |
| docs-architecture-adr | docs-update | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 50%/100% | 100% | 100%/100% | 15942 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 12102 |
| docs-security-policy | docs-update | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 100%/100% | 50% | 50%/50% | 6754 |
| docs-contributing | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 50% | 100%/100% | 11341 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 75% | MISS (subject hit, id MISS, action hit) | miss | match | 100%/100% | 50% | 100%/100% | 26655 |
| deploy-github-actions | deploy-change | observation | observation | pass | 75% | hit | miss | match | 50%/100% | 100% | 100%/100% | 12624 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 75% | MISS (subject hit, id MISS, action hit) | miss | match | 33%/100% | 67% | 100%/100% | 16238 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 50% | MISS (subject MISS, id hit, action hit) | miss | MISS | 100%/100% | 50% | 100%/100% | 18678 |
| deploy-helm-values | deploy-change | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 50%/100% | 100% | 100%/100% | 7027 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 75% | hit | miss | MISS | 100%/100% | 0% | 100%/100% | 13732 |
| deploy-canary | deploy-change | observation | observation | pass | 75% | hit | miss | match | 0%/0% | 100% | 100%/100% | 15643 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4110 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 9864 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4013 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 6329 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5002 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3471 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2795 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5544 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2467 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2554 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4922 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5638 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 17914 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5195 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2925 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4071 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3555 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3834 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4278 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 6631 |
| remove-dependency | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/50% | 16686 |
| remove-dead-code | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 100% | 100%/50% | 7423 |
| remove-stale-config | config-change | observation | observation | pass | 100% | MISS (subject unreachable, id MISS, action hit) | hit | match | 100%/100% | 100% | 100%/100% | 8599 |
| remove-deprecated-endpoint | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/50% | 18047 |
| remove-feature-flag | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/50% | 36388 |

## Problems

- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed concurrent null placeholder race in UserCache')
- bug-timezone-report: title lost identifiers [DailyReportTest, reportUsesBusinessTimezone] (got 'Added reproduction test for daily report off-by-one-day bug in DailyReportTest')
- bug-timezone-report: title subject [timezone] missing (got 'Added reproduction test for daily report off-by-one-day bug in DailyReportTest')
- bug-timezone-report: title trace-visible subject [timezone] missing (got 'Added reproduction test for daily report off-by-one-day bug in DailyReportTest')
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-jwt-expiry: title coverage 50% (expected keywords [fix, jwt, expiry, boundary])
- bug-jwt-expiry: title action [modify] missing: title leads 'secure' (got 'Hardened JWT expiration boundary check in JwtVerifier')
- bug-jwt-expiry: title verb class 'fix' missing (got 'Hardened JWT expiration boundary check in JwtVerifier')
- bug-jwt-expiry: facts recall 67% (expected [expiration, isEqual, rejects], got [JwtVerifier's expiration check changed from `claims.getExpiration().before(now)` to `claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now)`., JwtVerifierTest#expiredAtExactBoundary passes after the change.])
- bug-jwt-expiry: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, gotcha])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: title coverage 75% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-n-plus-one: title coverage 75% (expected keywords [fix, n+1, orderrepository, entitygraph])
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed UTF-8 charset decoding in FileReaderFactory')
- refactor-auth-controller: title coverage 67% (expected keywords [refactor, authcontroller, tokenservice])
- refactor-auth-controller: title subject [delegation] missing (got 'Refactored inline JWT construction in AuthController')
- refactor-config-loader: title coverage 33% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title action [modify] missing: title leads 'secure' (got 'Hardened config map immutability in ConfigLoader')
- refactor-config-loader: title verb class 'refactor' missing (got 'Hardened config map immutability in ConfigLoader')
- refactor-config-loader: title subject [unmodifiable, map] missing (got 'Hardened config map immutability in ConfigLoader')
- refactor-config-loader: title trace-visible subject [unmodifiable, map] missing (got 'Hardened config map immutability in ConfigLoader')
- refactor-config-loader: type 'change' != expected 'refactor'
- refactor-config-loader: concepts recall 50% (expected [what-changed, pattern], got [what-changed, what-changed])
- refactor-order-mapper: title coverage 67% (expected keywords [refactor, ordermapper, convertvalue])
- refactor-order-mapper: title subject [convertValue] missing (got 'Refactored entity-to-DTO mapping in OrderMapper')
- refactor-order-mapper: title trace-visible subject [convertValue] missing (got 'Refactored entity-to-DTO mapping in OrderMapper')
- refactor-logging-filter: facts recall 50% (expected [logExchange, StopWatch], got [LoggingFilter moved timing and header capture into the logExchange method., LoggingFilterTest verifies the same access-log line after the refactor.])
- refactor-sql-repository: title coverage 67% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title action [modify] missing: title leads 'secure' (got 'Hardened tenant lookup SQL with JPQL @Query in UserRepository')
- refactor-sql-repository: title verb class 'refactor' missing (got 'Hardened tenant lookup SQL with JPQL @Query in UserRepository')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: concepts recall 50% (expected [what-changed, pattern], got [what-changed, gotcha])
- refactor-feature-flag: concepts recall 50% (expected [what-changed, pattern], got [what-changed, what-changed])
- test-repro-timezone: title lost identifiers [DailyReportTest, reportUsesBusinessTimezone] (got 'Added UTC vs business date reproduction test DailyReportTest')
- test-repro-timezone: title subject [timezone] missing (got 'Added UTC vs business date reproduction test DailyReportTest')
- test-repro-timezone: title trace-visible subject [timezone] missing (got 'Added UTC vs business date reproduction test DailyReportTest')
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: title coverage 75% (expected keywords [add, retrypolicy, parameterized, test])
- test-unit-retry: title subject [parameterized] missing (got 'Added RetryPolicy delay backoff parameterization test in RetryPolicyTest')
- test-unit-retry: title trace-visible subject [parameterized] missing (got 'Added RetryPolicy delay backoff parameterization test in RetryPolicyTest')
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles is a @ParameterizedTest driven by @CsvSource values "1,100", "2,200", "3,400"., The test asserts policy.delay(attempt) returns 100 ms for attempt 1, 200 ms for attempt 2, and 400 ms for attempt 3., All 3 parameterized cases pass, confirming the delay doubles per attempt.])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: title subject [credentials] missing (got 'Redacted password, token and secret values in LogSanitizer')
- security-redact-log: title trace-visible subject [credentials] missing (got 'Redacted password, token and secret values in LogSanitizer')
- security-redact-log: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, gotcha])
- security-sql-injection: title coverage 75% (expected keywords [fix, sql, injection, userdao])
- security-sql-injection: title verb class 'fix' missing (got 'Hardened email lookup against SQL injection in UserDao')
- security-authz-admin: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, pattern])
- security-csrf: title coverage 67% (expected keywords [fix, csrf, csrffilter])
- security-csrf: title verb class 'fix' missing (got 'Hardened CSRF token validation in CsrfFilter')
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter returns HTTP 403 via response.sendError(403) when csrfToken.matches(request) is false, CsrfFilterTest#postWithoutTokenIs403 passes])
- security-secret-config: title coverage 50% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: title verb class 'remove' missing (got 'Redacted API key sk-live-123 replaced with ACME_API_KEY in application.yml')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Redacted API key sk-live-123 replaced with ACME_API_KEY in application.yml')
- security-secret-config: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- security-path-traversal: title coverage 75% (expected keywords [fix, path, traversal, filedownloadcontroller])
- security-path-traversal: title verb class 'fix' missing (got 'Hardened path traversal check in FileDownloadController')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title action [secure] missing: title leads 'modify' (got 'Extended token bucket to return 429 in RateLimiter')
- security-rate-limit: title verb class 'add' missing (got 'Extended token bucket to return 429 in RateLimiter')
- security-rate-limit: type 'feature' != expected 'bugfix'
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [what-changed, pattern])
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- security-cookie-httponly: title trace-visible subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- security-cookie-httponly: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, what-changed])
- config-hikari-pool: title lost identifiers [maximum-pool-size] (got 'Extended Hikari connection pool sizing in application.yml')
- config-hikari-pool: title verb class 'change' missing (got 'Extended Hikari connection pool sizing in application.yml')
- config-log-level: title coverage 75% (expected keywords [change, logback, root, json])
- config-log-level: title subject [log, level] missing (got 'Updated root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: title trace-visible subject [level] missing (got 'Updated root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: facts recall 50% (expected [INFO, JSON appender], got [logback-spring.xml root element changed to level="INFO" with appender-ref ref="JSON"., LogbackConfigTest parses the logback-spring.xml XML configuration.])
- config-cors: type 'bugfix' != expected 'change'
- config-feature-flags: title coverage 50% (expected keywords [enable, checkout-v2, disable, legacy-search])
- config-feature-flags: title verb class 'enable' missing (got 'Changed checkout-v2 and legacy-search flags in feature-flags.yml')
- config-docker-healthcheck: title coverage 50% (expected keywords [add, docker, healthcheck, actuator])
- config-docker-healthcheck: title verb class 'add' missing (got 'Extended health check in Dockerfile')
- config-maven-plugin: title action [modify] missing: title leads 'create' (got 'Added jacoco-maven-plugin 0.8.12 to pom.xml')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-api-observations: title lost identifiers [API.md, /api/observations] (got 'Documented pagination fields hasMore and items in API.md')
- docs-api-observations: title subject [observations, pagination] missing (got 'Documented pagination fields hasMore and items in API.md')
- docs-api-observations: title trace-visible subject [observations, pagination] missing (got 'Documented pagination fields hasMore and items in API.md')
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Documented local setup steps in README.md')
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title verb class 'add' missing (got 'Documented queue recovery steps in runbook.md')
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title verb class 'change' missing (got 'Documented 2.4.0 release notes in CHANGELOG.md')
- docs-architecture-adr: title coverage 75% (expected keywords [record, adr, extraction, validation])
- docs-architecture-adr: title subject [adr] missing (got 'Recorded extraction validation decision in 0007-extraction-validation.md')
- docs-architecture-adr: title trace-visible subject [adr] missing (got 'Recorded extraction validation decision in 0007-extraction-validation.md')
- docs-migration-guide: title coverage 75% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [what-changed, pattern])
- docs-security-policy: title action [document] missing: title leads 'create' (got 'Added vulnerability disclosure policy in SECURITY.md')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 67% (expected keywords [update, contributing, verify-local])
- docs-contributing: title verb class 'change' missing (got 'Documented pre-PR verify-local.sh requirement in CONTRIBUTING.md')
- deploy-k8s-probe: title lost identifiers [deployment.yaml, /api/health] (got 'Extended readinessProbe in deployment.yaml')
- deploy-k8s-probe: title verb class 'add' missing (got 'Extended readinessProbe in deployment.yaml')
- deploy-github-actions: title coverage 75% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: title verb class 'add' missing (got 'Changed JDK matrix to 21 and 25 in ci.yml')
- deploy-docker-multistage: title lost identifiers [Dockerfile, eclipse-temurin:21-jre] (got 'Converted single-stage to multi-stage in Dockerfile')
- deploy-terraform-iam: title coverage 50% (expected keywords [restrict, iam, terraform, s3])
- deploy-terraform-iam: title subject [iam, policy] missing (got 'Restricted deploy role S3 permissions in infra/main.tf')
- deploy-terraform-iam: title trace-visible subject [iam, policy] missing (got 'Restricted deploy role S3 permissions in infra/main.tf')
- deploy-terraform-iam: type 'bugfix' != expected 'change'
- deploy-helm-values: title coverage 75% (expected keywords [change, helm, replicas, memory])
- deploy-helm-values: title subject [helm, values] missing (got 'Changed replicaCount and memory limit in values.yaml')
- deploy-helm-values: title trace-visible subject [helm, values] missing (got 'Changed replicaCount and memory limit in values.yaml')
- deploy-rollback-script: title coverage 75% (expected keywords [add, rollback, script, deployment])
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 75% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: facts recall 0% (expected [10 percent, 2 percent], got [infra/canary.yaml sets canary.enabled = true, canary.weight = 10, and canary.errorThreshold = 0.02., helm lint passes against the updated canary.yaml configuration.])
- remove-dependency: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])
- remove-dead-code: title coverage 50% (expected keywords [remove, dead, code, orderservice])
- remove-dead-code: title subject [dead, code] missing (got 'Removed legacySync from OrderService')
- remove-dead-code: type 'change' != expected 'refactor'
- remove-dead-code: concepts recall 50% (expected [what-changed, pattern], got [what-changed, what-changed])
- remove-stale-config: title lost identifiers [legacy-search.enabled, application.yml] (got 'Removed legacy-search toggle in application.yml')
- remove-stale-config: title subject [stale, setting] missing (got 'Removed legacy-search toggle in application.yml')
- remove-deprecated-endpoint: title coverage 75% (expected keywords [remove, deprecated, endpoint, usercontroller])
- remove-deprecated-endpoint: title subject [deprecated, endpoint] missing (got 'Removed legacy `/v1/users` endpoint in UserController')
- remove-deprecated-endpoint: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])
- remove-feature-flag: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed off-by-one pagination offset in OrderQuery</title>   <subtitle>OrderQuery.pageOffset no longer subtracts 1, so the first page starts at index 0.</subtitle>   <facts>     <fact>OrderQuery.pageOffset previously computed `page * size - 1`; it now returns `page * size`.</fact>     <fact>With page=1 and size=N, the old expression yielded N-1, so the fi... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed concurrent null placeholder race in UserCache</title>   <subtitle>Replaced cache.get with computeIfAbsent so concurrent callers cannot both write a null placeholder.</subtitle>   <facts>     <fact>UserCache.get previously returned cache.get(id) and now returns cache.computeIfAbsent(id, loader::load), preventing two concurrent callers from both wri... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added reproduction test for daily report off-by-one-day bug in DailyReportTest</title>   <subtitle>Test pins 2026-11-01T23:30Z and expects Asia/Shanghai business date 2026-11-02</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins 2026-11-01T23:30Z and expects the Asia/Shanghai business date 2026-11-02</fact>     <fact>The tes... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed null return for missing orderId in PaymentService</title>   <subtitle>PaymentService now throws PaymentNotFoundException instead of returning null when an orderId is not found.</subtitle>   <facts>     <fact>PaymentService now throws PaymentNotFoundException when repository.findByOrderId returns empty for a missing orderId instead of returning nul... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Hardened JWT expiration boundary check in JwtVerifier</title>   <subtitle>JwtVerifier now rejects a token whose expiration equals the current instant.</subtitle>   <facts>     <fact>JwtVerifier's expiration check changed from `claims.getExpiration().before(now)` to `claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now)`.</fact>    ... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed embedded line endings normalization in CsvExporter</title>   <subtitle>CsvExporter now normalizes embedded line endings to CRLF before quoting, ensuring multiline fields preserve row count.</subtitle>   <facts>     <fact>CsvExporter now writes `line.replaceAll("\\r?\\n", "\\r\\n")` to normalize embedded line endings to CRLF before quoting.</fact> ... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed non-atomic increment race condition in CounterService</title>   <subtitle>Replaced get/set pair with AtomicLong.incrementAndGet to eliminate lost updates under concurrent access</subtitle>   <facts>     <fact>CounterService.increment previously called counter.set(counter.get() + 1), which is not atomic and can lose updates when multiple threads re... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>bugfix</type>   <title>Fixed N+1 query in findAllByCustomerId in OrderRepository</title>   <subtitle>Added @EntityGraph to fetch order items eagerly, reducing SQL statements from 1+N to 1.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId now annotated with @EntityGraph(attributePaths = "items")</fact>     <fact>QueryCountTest shows 1 SQL statement instead of... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed UTF-8 charset decoding in FileReaderFactory</title>   <subtitle>FileReaderFactory now pins UTF-8 instead of relying on the platform default charset.</subtitle>   <facts>     <fact>FileReaderFactory now opens input with `new InputStreamReader(in, StandardCharsets.UTF_8)` rather than the JVM platform default charset.</fact>     <fact>FileReaderFacto... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored inline JWT construction in AuthController</title>   <subtitle>AuthController now delegates token issuance to TokenService.issue instead of building JWTs inline; existing tests stay green.</subtitle>   <facts>     <fact>AuthController replaced inline JWT construction with a call to tokenService.issue(authentication).</fact>     <fact>AuthCon... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>change</type>   <title>Hardened config map immutability in ConfigLoader</title>   <subtitle>ConfigLoader now returns an unmodifiable map and exposes typed accessors for configuration values.</subtitle>   <facts>     <fact>ConfigLoader returns an unmodifiable map via `properties.entrySet().stream().collect(toUnmodifiableMap(...))`</fact>     <fact>ConfigLoader exposes typed ac... [truncated]
```

### refactor-order-mapper

```text
<observation>   <type>refactor</type>   <title>Refactored entity-to-DTO mapping in OrderMapper</title>   <subtitle>Replaced 40 hand-written setters with a single Jackson convertValue call.</subtitle>   <facts>     <fact>OrderMapper replaces 40 hand-written setters with `mapper.convertValue(entity, OrderDto.class)`.</fact>     <fact>OrderMapperTest continues to compare the same DTO fields without m... [truncated]
```

### refactor-logging-filter

```text
<observation>   <type>refactor</type>   <title>Refactored timing and header capture into logExchange in LoggingFilter</title>   <subtitle>Timing and header capture consolidated into a single logExchange method while preserving the access-log line.</subtitle>   <facts>     <fact>LoggingFilter moved timing and header capture into the logExchange method.</fact>     <fact>LoggingFilterTest verifies th... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Hardened tenant lookup SQL with JPQL @Query in UserRepository</title>   <subtitle>UserRepository replaces string-concatenated SQL with a parameterized JPQL @Query for tenant lookup.</subtitle>   <facts>     <fact>UserRepository now uses the JPQL @Query "select u from User u where u.tenantId = :tenantId" with a bound :tenantId parameter instead of string... [truncated]
```

### refactor-feature-flag

```text
<observation>   <type>refactor</type>   <title>Refactored flag evaluation delegation in FeatureFlagService</title>   <subtitle>FeatureFlagService now delegates evaluation to Flags.isEnabled while keeping the same on/off matrix.</subtitle>   <facts>     <fact>FeatureFlagService returns flags.isEnabled(flag, context), delegating evaluation to Flags.isEnabled.</fact>     <fact>FeatureFlagServiceTest ... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added UTC vs business date reproduction test DailyReportTest</title>   <subtitle>New failing test pins the daily report's UTC-vs-business-date defect for Asia/Shanghai.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins `Instant.parse("2026-11-01T23:30:00Z")` with `ZoneId.of("Asia/Shanghai")` and expects `LocalDate.of(2026, ... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added locked account 423 status reproduction test LoginControllerTest</title>   <subtitle>New test pins the expected 423 LOCKED response for a locked user instead of the current 401.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to true and performs POST /login, expecting HTTP 423 (LOCKED).</fact... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicy delay backoff parameterization test in RetryPolicyTest</title>   <subtitle>Parameterized test pins exponential delay of 100/200/400 ms for retry attempts 1-3.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles is a @ParameterizedTest driven by @CsvSource values "1,100", "2,200", "3,400".</fact>     <fact>The test asserts poli... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added reproduction test for intermittent null from UserCache CacheConcurrencyTest</title>   <subtitle>New test reproduces intermittent null from UserCache with 1000 parallel readers; fails before fix, passes after.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull reproduces the intermittent null from UserCache with 1000... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Redacted password, token and secret values in LogSanitizer</title>   <subtitle>LogSanitizer now masks credential key/value pairs before logging instead of writing them verbatim.</subtitle>   <facts>     <fact>LogSanitizer applies the regex `(password|token|secret)=\S+` and replaces matches with `$1=***`, masking password, token and secret values.</fact>... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Hardened email lookup against SQL injection in UserDao</title>   <subtitle>Replaced string concatenation with a bound parameter placeholder in the email query.</subtitle>   <facts>     <fact>UserDao.findByEmail now calls jdbcTemplate.query("select * from users where email = ?", mapper, email) with a parameter placeholder instead of concatenating the ema... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Hardened ROLE_ADMIN authorization in AdminController</title>   <subtitle>Every AdminController endpoint now requires ROLE_ADMIN; AdminAuthorizationTest#userRoleGets403 confirms a 403 for users.</subtitle>   <facts>     <fact>`@PreAuthorize("hasRole('ADMIN')")` was added to AdminController, requiring ROLE_ADMIN on every endpoint.</fact>     <fact>AdminAu... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Hardened CSRF token validation in CsrfFilter</title>   <subtitle>CsrfFilter now rejects state-changing requests whose CSRF token does not match.</subtitle>   <facts>     <fact>CsrfFilter returns HTTP 403 via response.sendError(403) when csrfToken.matches(request) is false</fact>     <fact>CsrfFilterTest#postWithoutTokenIs403 passes</fact>   </facts>   <... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Redacted API key sk-live-123 replaced with ACME_API_KEY in application.yml</title>   <subtitle>Removed hardcoded secret from application.yml; now reads ACME_API_KEY from environment.</subtitle>   <facts>     <fact>Literal API key sk-live-123 was removed from src/main/resources/application.yml</fact>     <fact>Configuration now references environment var... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Hardened path traversal check in FileDownloadController</title>   <subtitle>File downloads now reject names that escape the base directory, satisfying PathTraversalTest#dotDotSlashIs400.</subtitle>   <facts>     <fact>FileDownloadController.java computes Path resolved = base.resolve(name).normalize() and throws InvalidPathException(name) when the normal... [truncated]
```

### security-rate-limit

```text
<observation>   <type>feature</type>   <title>Extended token bucket to return 429 in RateLimiter</title>   <subtitle>RateLimiter now throws TooManyRequestsException, producing HTTP 429, when the token bucket cannot serve a request.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/RateLimiter.java now contains `if (!bucket.tryConsume(1)) { throw new TooManyRequestsException(); }`, returnin... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>Session cookie now sets HttpOnly, Secure, and SameSite=Strict to block XSS, MITM, and CSRF.</subtitle>   <facts>     <fact>SessionCookieConfig sets cookie.setHttpOnly(true), cookie.setSecure(true), and cookie.setSameSite(Strict).</fact>     <fact>SessionCookieTest#flagsArePresent v... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Extended Hikari connection pool sizing in application.yml</title>   <subtitle>Raised HikariCP maximum-pool-size to 20 and minimum-idle to 5 in the Spring datasource configuration.</subtitle>   <facts>     <fact>application.yml now sets spring.datasource.hikari.maximum-pool-size: 20 and spring.datasource.hikari.minimum-idle: 5.</fact>     <fact>ConfigBin... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Updated root logger to INFO with JSON appender in logback-spring.xml</title>   <subtitle>Root logging level set to INFO and JSON appender attached in the Spring Boot logback configuration.</subtitle>   <facts>     <fact>logback-spring.xml root element changed to level="INFO" with appender-ref ref="JSON".</fact>     <fact>LogbackConfigTest parses the log... [truncated]
```

### config-cors

```text
<observation>   <type>bugfix</type>   <title>Restricted CORS origins and methods in CorsConfig</title>   <subtitle>CorsConfig allows only https://app.acme.com with GET and POST on /api/** and rejects other origins.</subtitle>   <facts>     <fact>CorsConfig restricts the /api/** mapping to allowed origin https://app.acme.com.</fact>     <fact>CorsConfig limits allowed methods to GET and POST for /a... [truncated]
```

### config-feature-flags

```text
<observation>   <type>change</type>   <title>Changed checkout-v2 and legacy-search flags in feature-flags.yml</title>   <subtitle>Configuration toggles checkout-v2 on and legacy-search off.</subtitle>   <facts>     <fact>src/main/resources/feature-flags.yml sets checkout-v2: true and legacy-search: false.</fact>     <fact>FlagBindingTest binds both booleans for the toggled flags.</fact>     <fact>... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Extended health check in Dockerfile</title>   <subtitle>Dockerfile now polls /actuator/health every 30 seconds via curl and fails the container when unhealthy.</subtitle>   <facts>     <fact>Dockerfile adds HEALTHCHECK --interval=30s CMD curl -f http://localhost:8080/actuator/health || exit 1.</fact>     <fact>docker build succeeds after the HEALTHCHECK... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 to pom.xml</title>   <subtitle>Build now runs JaCoCo code coverage and writes an XML report on verify.</subtitle>   <facts>     <fact>pom.xml adds jacoco-maven-plugin with groupId org.jacoco and version 0.8.12.</fact>     <fact>mvn verify produces the coverage report at target/site/jacoco/jacoco.xml.</fact>   </facts>   ... [truncated]
```

### docs-api-observations

```text
<observation>   <type>change</type>   <title>Documented pagination fields hasMore and items in API.md</title>   <subtitle>GET /api/observations response contract now records hasMore and items matching the controller.</subtitle>   <facts>     <fact>docs/API.md states GET /api/observations returns hasMore and items.</fact>     <fact>The documented pagination fields match the existing controller cont... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Documented local setup steps in README.md</title>   <subtitle>Added a three-step local setup guide to README.md covering JDK 21, Postgres via Docker, and Maven launch.</subtitle>   <facts>     <fact>README.md now contains a "## Setup" section with three numbered steps.</fact>     <fact>Step 1 instructs installing JDK 21.</fact>     <fact>Step 2 instruct... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Documented queue recovery steps in runbook.md</title>   <subtitle>Runbook documents queue recovery: check pending_messages, restart worker, verify /api/health.</subtitle>   <facts>     <fact>docs/runbook.md documents three queue recovery steps: inspect pending_messages, restart the worker, verify /api/health.</fact>   </facts>   <concepts>     <concept>... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Documented 2.4.0 release notes in CHANGELOG.md</title>   <subtitle>CHANGELOG.md gained a 2.4.0 entry dated 2026-11-03 listing offline replay evaluation and the V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md adds a 2.4.0 section dated 2026-11-03</fact>     <fact>The 2.4.0 entry lists "Added offline replay evaluation"</fact>     <... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>decision</type>   <title>Recorded extraction validation decision in 0007-extraction-validation.md</title>   <subtitle>ADR 0007 records the decision to validate extracted facts against tool output before persistence, with the rejection path described.</subtitle>   <facts>     <fact>ADR 0007 in docs/adr/0007-extraction-validation.md records the decision to validate extracted fa... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisite in MIGRATION.md</title>   <subtitle>MIGRATION.md now states that V20 requires backing up mem_sessions and setting app.manual_sentinel_reviewed=true before migration.</subtitle>   <facts>     <fact>docs/MIGRATION.md gained a V20 section instructing users to back up mem_sessions before running the migration.</fact>   ... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Added vulnerability disclosure policy in SECURITY.md</title>   <subtitle>New security policy directs vulnerability reports to security@acme.com and prohibits public issues.</subtitle>   <facts>     <fact>SECURITY.md defines the disclosure channel security@acme.com for reporting vulnerabilities.</fact>     <fact>SECURITY.md instructs that vulnerabilities... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Documented pre-PR verify-local.sh requirement in CONTRIBUTING.md</title>   <subtitle>CONTRIBUTING.md now requires running ./scripts/verify-local.sh before opening a pull request and names the four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md now states "Run ./scripts/verify-local.sh before opening a pull request."</fact>     <fact>CONTRIB... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Extended readinessProbe in deployment.yaml</title>   <subtitle>Added an HTTP readiness probe on /api/health:37777 with a 15-second initial delay to the deployment manifest.</subtitle>   <facts>     <fact>deploy/deployment.yaml adds a readinessProbe with httpGet path /api/health on port 37777 and initialDelaySeconds 15.</fact>     <fact>kubectl apply --d... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Changed JDK matrix to 21 and 25 in ci.yml</title>   <subtitle>CI now builds the backend against both JDK 21 and 25 in a matrix strategy.</subtitle>   <facts>     <fact>The `strategy.matrix.java` list in `.github/workflows/ci.yml` was set to `[21, 25]`, so the backend runs on both JDK 21 and JDK 25.</fact>     <fact>A workflow syntax check on the edited ... [truncated]
```

### deploy-docker-multistage

```text
<observation>   <type>change</type>   <title>Converted single-stage to multi-stage in Dockerfile</title>   <subtitle>Dockerfile split into maven build stage and eclipse-temurin:21-jre runtime stage, cutting image size from 780MB to 210MB.</subtitle>   <facts>     <fact>Dockerfile is now multi-stage: build stage runs maven, runtime stage uses eclipse-temurin:21-jre.</fact>     <fact>Runtime stage c... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>bugfix</type>   <title>Restricted deploy role S3 permissions in infra/main.tf</title>   <subtitle>aws_iam_role_policy.deploy now grants AmazonS3ReadOnlyAccess instead of full S3 access.</subtitle>   <facts>     <fact>infra/main.tf resource aws_iam_role_policy.deploy sets policy_arn to "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess", replacing full S3 access.</fact>     <fact... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Changed replicaCount and memory limit in values.yaml</title>   <subtitle>Deployment scaling raised to 3 replicas with a 2Gi memory cap per pod.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount: 3 and resources.limits.memory: 2Gi.</fact>     <fact>helm template renders 3 pods after the values.yaml edit.</fact>   </facts>   <concepts>   ... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added claude-mem rollback script rollback.sh</title>   <subtitle>New scripts/rollback.sh undoes the claude-mem deployment and waits for rollout status.</subtitle>   <facts>     <fact>scripts/rollback.sh runs `kubectl rollout undo deployment/claude-mem` followed by `kubectl rollout status deployment/claude-mem`.</fact>     <fact>The script is guarded by... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Enabled canary rollout weight 10 errorThreshold 0.02 in canary.yaml</title>   <subtitle>Canary deployment enabled at 10% traffic weight with automatic rollback above 2% error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled = true, canary.weight = 10, and canary.errorThreshold = 0.02.</fact>     <fact>helm lint passes against t... [truncated]
```

### remove-dependency

```text
<observation>   <type>change</type>   <title>Removed commons-lang3 dependency from pom.xml</title>   <subtitle>pom.xml no longer declares org.apache.commons:commons-lang3, and the build resolves without it.</subtitle>   <facts>     <fact>pom.xml no longer declares the dependency org.apache.commons:commons-lang3.</fact>     <fact>The build resolves without commons-lang3 present in pom.xml.</fact>  ... [truncated]
```

### remove-dead-code

```text
<observation>   <type>change</type>   <title>Removed legacySync from OrderService</title>   <subtitle>Deleted the unused legacySync method that pushed orders to the warehouse.</subtitle>   <facts>     <fact>OrderService.legacySync(Order order), which delegated to warehouse.push(order), was deleted from src/main/java/com/acme/orders/OrderService.java.</fact>     <fact>No other references to legacyS... [truncated]
```

### remove-stale-config

```text
<observation>   <type>change</type>   <title>Removed legacy-search toggle in application.yml</title>   <subtitle>Deleted the legacy-search.enabled setting so the search module reads only search-v2.</subtitle>   <facts>     <fact>src/main/resources/application.yml no longer defines the key legacy-search.enabled.</fact>     <fact>The search module now reads only the search-v2 configuration path.</fa... [truncated]
```

### remove-deprecated-endpoint

```text
<observation>   <type>change</type>   <title>Removed legacy `/v1/users` endpoint in UserController</title>   <subtitle>Deleted the listLegacy handler and its `/v1/users` mapping while retaining `/v2/users`.</subtitle>   <facts>     <fact>`UserController.listLegacy` and its `@GetMapping("/v1/users")` mapping were removed from `src/main/java/com/acme/users/UserController.java`.</fact>     <fact>The ... [truncated]
```

### remove-feature-flag

```text
<observation>   <type>change</type>   <title>Removed LEGACY_SEARCH constant from FeatureFlags</title>   <subtitle>Legacy feature flag constant removed; callers reference SEARCH_V2 instead.</subtitle>   <facts>     <fact>FeatureFlags.LEGACY_SEARCH constant was removed from src/main/java/com/acme/flags/FeatureFlags.java.</fact>     <fact>Callers are directed to use SEARCH_V2 instead of the removed L... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
