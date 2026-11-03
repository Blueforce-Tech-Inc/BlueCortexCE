golden set: main (87 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: r10-c3-main
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 87 unique; runs scored: 3; key repeat: 3; real calls: 339 (cap none); duration: 422666 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 94% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 20/0/0/67 |
| format errors | 0 |
| validation rejections | 0 |
| call failures | 5 |
| title exact-hit rate (legacy) | 53% |
| title normalized hit rate (legacy) | 60% |
| title action-class hit rate (A6) | 92% (62 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 76% |
| title subject hit rate (legacy, diagnostic) | 73% |
| title trace-visible subject hit rate (A6b) | 85% (53 scored, 9 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 63% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 74% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 55% |
| title identifier hit rate | 95% |
| type accuracy | 92% |
| facts keyword precision (legacy) / recall | 76% / 90% |
| facts informative precision | 79% |
| facts trace-support rate | 100% |
| concepts precision / recall | 81% / 81% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 2802 / 8227 / 29039 |
| tokens p50 / p95 / max / mean per call | 4583 / 12160 / 12570 / 5822 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 79% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 92% | PASS |
| A6b | title trace-visible subject | >= 90% | 85% | **FAIL** |
| A6c | title identifiers | = 100% | 95% | **FAIL** |
| A6 | all three title dimensions together | all pass | 74% | **FAIL** |
| A11 | p95 latency vs baseline | <= 1.5x baseline | 8227 ms (baseline 5648 ms) | PASS |
| A12 | p95 tokens vs baseline | <= 1.5x baseline | 12160 tokens (baseline 4048) | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |
| exception | 5 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 87 | 100% | 60% | 63% | 76% | 79% | 90% | 0 |
| 1 | 13 | 100% | 67% | 56% | 96% | 96% | 100% | 0 |
| 2 | 13 | 100% | 56% | 56% | 93% | 93% | 100% | 0 |

- average: skip 100%, title 61%, title contract 58%, facts P/R 88%/97%, facts actionable 89%
- worst run: skip 100%, title 56%, title contract 56%, facts P/R 76%/90%, facts actionable 79%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 10/12 | 9/12 | 5/12 |
| refactor | 10 | 10/10 | 9/10 | 7/10 |
| test-add | 9 | 9/9 | 9/9 | 8/9 |
| security-fix | 9 | 9/9 | 9/9 | 5/9 |
| config-change | 12 | 12/12 | 10/12 | 8/12 |
| docs-update | 8 | 7/8 | 7/8 | 2/8 |
| deploy-change | 7 | 5/7 | 4/7 | 2/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Run-to-run variance (repeated samples)

| metric | min | max | mean | std dev |
|---|---|---|---|---|
| type accuracy | 92% | 100% | 97% | 4% |
| title action class | 89% | 92% | 90% | 1% |
| title trace-visible subject | 83% | 100% | 89% | 8% |
| title identifiers | 78% | 95% | 87% | 7% |
| title trace contract | 67% | 74% | 69% | 4% |
| facts informative | 79% | 96% | 89% | 7% |
| skip accuracy | 100% | 100% | 100% | 0% |

## Inconsistent samples (type or action changed between runs)

None: every repeated sample kept the same type and action class.

## Unreachable subject labels (excluded from A6b)

9 sample(s) declare subject tokens the recorded trace never contains:

- bug-offbyone-pagination: [pagination]
- bug-null-cache: [race]
- bug-race-counter: [race]
- bug-n-plus-one: [n+1]
- bug-file-encoding: [encoding]
- refactor-auth-controller: [delegation]
- remove-dead-code: [dead, code]
- remove-stale-config: [stale, setting]
- remove-deprecated-endpoint: [deprecated, endpoint]

## Failure clusters (first run)

| cluster | misses | share of scored (62) |
|---|---|---|
| identifier missing | 3 | 5% |
| action class wrong | 5 | 8% |
| subject wrong | 8 | 13% |
| type wrong | 5 | 8% |

### identifier missing (3)

- bug-null-cache — expected identifiers [UserCache, computeIfAbsent], title 'Fixed UserCache.get concurrent null placeholder writes in UserCache'
- security-secret-config — expected identifiers [application.yml, ACME_API_KEY], title 'Redacted literal API key in application.yml'
- deploy-docker-multistage — expected identifiers [Dockerfile, eclipse-temurin:21-jre], title 'Converted Dockerfile to multi-stage build in Dockerfile'

### action class wrong (5)

- security-redact-log — expected [secure], got 'modify' from title 'Fixed credential redaction for password token and secret in LogSanitizer.java'
- security-sql-injection — expected [secure], got 'modify' from title 'Fixed UserDao.findByEmail SQL injection with parameter placeholder'
- security-csrf — expected [secure], got 'modify' from title 'Fixed CSRF rejection for mismatched tokens in CsrfFilter.java'
- security-path-traversal — expected [secure], got 'modify' from title 'Fixed path traversal in FileDownloadController'
- security-rate-limit — expected [secure], got 'modify' from title 'Fixed RateLimiter empty token bucket response in RateLimiter.java'

### subject wrong (8)

- refactor-order-mapper — expected subject tokens [convertValue], title 'Refactored OrderMapper DTO conversion in OrderMapper.java'
- refactor-logging-filter — expected subject tokens [timing], title 'Refactored LoggingFilter access logging through logExchange'
- refactor-sql-repository — expected subject tokens [jpql], title 'Fixed tenant filtering query injection in UserRepository.java'
- test-flaky-clock — expected subject tokens [flaky, clock], title 'Extended SchedulerTest clock control with Clock.fixed'
- security-cookie-httponly — expected subject tokens [httponly, cookie], title 'Hardened session cookie flags in SessionCookieConfig'
- config-log-level — expected subject tokens [level], title 'Changed root logger to INFO with JSON appender in logback-spring.xml'
- docs-security-policy — expected subject tokens [security, policy], title 'Documented vulnerability disclosure channel in SECURITY.md'
- deploy-terraform-iam — expected subject tokens [iam, policy], title 'Restricted deploy role to AmazonS3ReadOnlyAccess in infra/main.tf'

### type wrong (5)

- bug-n-plus-one — expected 'bugfix', got 'refactor' (title 'Refactored OrderRepository.findAllByCustomerId to fetch items with EntityGraph')
- refactor-sql-repository — expected 'refactor', got 'bugfix' (title 'Fixed tenant filtering query injection in UserRepository.java')
- config-cors — expected 'change', got 'bugfix' (title 'Restricted /api/** CORS origin and methods in CorsConfig')
- deploy-rollback-script — expected 'change', got 'feature' (title 'Added deployment rollback script in scripts/rollback.sh')
- remove-feature-flag — expected 'change', got 'refactor' (title 'Removed FeatureFlags.LEGACY_SEARCH constant in FeatureFlags.java')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 50%/50% | 8306 |
| bug-null-cache | bug-fix | observation | observation | pass | 50% | MISS (subject unreachable, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/50% | 2716 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 50%/50% | 1928 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 8227 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 3159 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/67% | 100% | 100%/100% | 3603 |
| bug-csv-crlf | bug-fix | observation | call-failed | FAIL | - | - | - | - | - | - | - | 6662 |
| bug-race-counter | bug-fix | observation | observation | pass | 75% | hit | miss | match | 67%/67% | 100% | 100%/100% | 3240 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 50% | hit | miss | MISS | 67%/100% | 67% | 100%/100% | 3934 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 3279 |
| bug-regex-redos | bug-fix | observation | call-failed | FAIL | - | - | - | - | - | - | - | 8844 |
| bug-file-encoding | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 2409 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 67%/67% | 67% | 100%/100% | 2802 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 67% | 100%/100% | 3293 |
| refactor-config-loader | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 100%/100% | 3534 |
| refactor-order-mapper | refactor | observation | observation | pass | 67% | MISS (subject MISS, id hit, action hit) | miss | match | 100%/100% | 50% | 100%/100% | 3149 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 1614 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | MISS (subject MISS, id hit, action hit) | hit | match | 100%/100% | 33% | 100%/100% | 5426 |
| refactor-sql-repository | refactor | observation | observation | pass | 33% | MISS (subject MISS, id hit, action hit) | miss | MISS | 100%/50% | 100% | 50%/50% | 4544 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 33% | 100%/100% | 3312 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 50%/50% | 50% | 100%/100% | 3184 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 5438 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 100% | 50%/50% | 4249 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/50% | 100% | 100%/100% | 3566 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 2820 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 67% | 50%/50% | 5432 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 2470 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/67% | 100% | 50%/50% | 3329 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 50%/50% | 3438 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 100%/100% | 100% | 100%/100% | 2979 |
| security-redact-log | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 100%/100% | 3096 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 33%/50% | 33% | 100%/100% | 2463 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | hit | miss | match | 67%/100% | 67% | 100%/100% | 2729 |
| security-csrf | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 50%/50% | 100% | 100%/100% | 8712 |
| security-secret-config | security-fix | observation | observation | pass | 50% | MISS (subject hit, id MISS, action hit) | miss | match | 67%/100% | 100% | 100%/100% | 2696 |
| security-xxe | security-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 1870 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 67%/100% | 100% | 100%/100% | 2256 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS (subject hit, id hit, action MISS) | miss | match | 33%/100% | 100% | 100%/100% | 2085 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 50%/100% | 100% | 100%/100% | 4746 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 67% | 100%/100% | 4390 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 1972 |
| config-log-level | config-change | observation | observation | pass | 100% | MISS (subject MISS, id hit, action hit) | hit | match | 33%/50% | 33% | 100%/100% | 3154 |
| config-cors | config-change | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 33% | 0%/0% | 3903 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/50% | 4737 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 4637 |
| config-docker-healthcheck | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 2244 |
| config-maven-plugin | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 29039 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 0% | 100%/100% | 2925 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 33% | 100%/100% | 2743 |
| docs-runbook | docs-update | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 33% | 100%/100% | 2597 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 2648 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 2119 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 2614 |
| docs-security-policy | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id hit, action hit) | miss | match | 100%/100% | 50% | 50%/50% | 2340 |
| docs-contributing | docs-update | observation | call-failed | FAIL | - | - | - | - | - | - | - | 5429 |
| deploy-k8s-probe | deploy-change | observation | call-failed | FAIL | - | - | - | - | - | - | - | 6283 |
| deploy-github-actions | deploy-change | observation | call-failed | FAIL | - | - | - | - | - | - | - | 7452 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 75% | MISS (subject hit, id MISS, action hit) | miss | match | 33%/100% | 100% | 50%/50% | 2903 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 50% | MISS (subject MISS, id hit, action hit) | miss | match | 100%/100% | 50% | 50%/50% | 2712 |
| deploy-helm-values | deploy-change | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 100% | 50%/50% | 3072 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 33% | 100%/100% | 3199 |
| deploy-canary | deploy-change | observation | observation | pass | 50% | hit | miss | match | 50%/100% | 100% | 50%/50% | 2607 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1061 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1331 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1005 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1021 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1281 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2153 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 6133 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1432 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 897 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1040 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2529 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2259 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1672 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1366 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1064 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1404 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2002 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 968 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1085 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3156 |
| remove-dependency | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1994 |
| remove-dead-code | refactor | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 67% | 100%/100% | 2493 |
| remove-stale-config | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 2077 |
| remove-deprecated-endpoint | config-change | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 100%/100% | 2347 |
| remove-feature-flag | config-change | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/50% | 4499 |

## Problems

- bug-offbyone-pagination: title coverage 67% (expected keywords [fix, pagination, orderquery])
- bug-offbyone-pagination: title subject [pagination] missing (got 'Fixed OrderQuery.pageOffset off-by-one in OrderQuery.java')
- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed UserCache.get concurrent null placeholder writes in UserCache')
- bug-null-cache: title subject [race] missing (got 'Fixed UserCache.get concurrent null placeholder writes in UserCache')
- bug-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-jwt-expiry: facts recall 67% (expected [expiration, isEqual, rejects], got [JwtVerifier.java changed expiration validation to reject when claims.getExpiration() is before or equal to now., JwtVerifierTest#expiredAtExactBoundary passes with the expiration-boundary fix., Tokens whose expiration equals the current instant are rejected by JwtVerifier.])
- bug-csv-crlf: call failed: IllegalStateException: all attempts returned no content
- bug-race-counter: title coverage 75% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: title subject [race] missing (got 'Fixed CounterService.increment lost updates with AtomicLong.incrementAndGet')
- bug-race-counter: facts recall 67% (expected [incrementAndGet, AtomicLong, 1000], got [CounterService.increment in src/main/java/com/acme/metrics/CounterService.java now returns counter.incrementAndGet()., CounterService.increment previously used counter.set(counter.get() + 1) followed by counter.get()., CounterServiceConcurrencyTest passes with 1000 threads producing a final count of 1000.])
- bug-n-plus-one: title coverage 50% (expected keywords [fix, n+1, orderrepository, entitygraph])
- bug-n-plus-one: title verb class 'fix' missing (got 'Refactored OrderRepository.findAllByCustomerId to fetch items with EntityGraph')
- bug-n-plus-one: title subject [n+1] missing (got 'Refactored OrderRepository.findAllByCustomerId to fetch items with EntityGraph')
- bug-n-plus-one: type 'refactor' != expected 'bugfix'
- bug-regex-redos: call failed: IllegalStateException: all attempts returned no content
- refactor-invoice: facts recall 67% (expected [TaxCalculator, InvoiceRenderer, MailGateway], got [InvoiceService.java now calls renderer.render(taxCalculator.calculate(invoice), invoice)., InvoiceService delegates tax calculation to TaxCalculator and rendering to InvoiceRenderer., InvoiceServiceTest stays green after the InvoiceService.java refactor.])
- refactor-auth-controller: title subject [delegation] missing (got 'Refactored AuthController JWT issuance to TokenService.issue')
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [src/main/java/com/acme/auth/AuthController.java now returns tokenService.issue(authentication)., AuthController no longer builds JWTs inline., AuthControllerTest remained unchanged and passed.])
- refactor-order-mapper: title coverage 67% (expected keywords [refactor, ordermapper, convertvalue])
- refactor-order-mapper: title subject [convertValue] missing (got 'Refactored OrderMapper DTO conversion in OrderMapper.java')
- refactor-order-mapper: title trace-visible subject [convertValue] missing (got 'Refactored OrderMapper DTO conversion in OrderMapper.java')
- refactor-retry-policy: facts recall 50% (expected [BackoffStrategy, 100/200/400], got [src/main/java/com/acme/resilience/RetryPolicy.java now returns backoff.nextDelay(attempt) for retry delay calculation., RetryPolicyTest asserts the unchanged 100/200/400 retry delay sequence.])
- refactor-logging-filter: title subject [timing] missing (got 'Refactored LoggingFilter access logging through logExchange')
- refactor-logging-filter: title trace-visible subject [timing] missing (got 'Refactored LoggingFilter access logging through logExchange')
- refactor-sql-repository: title coverage 33% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title verb class 'refactor' missing (got 'Fixed tenant filtering query injection in UserRepository.java')
- refactor-sql-repository: title subject [jpql] missing (got 'Fixed tenant filtering query injection in UserRepository.java')
- refactor-sql-repository: title trace-visible subject [jpql] missing (got 'Fixed tenant filtering query injection in UserRepository.java')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [UserRepository.java replaces string-concatenated SQL with @Query("select u from User u where u.tenantId = :tenantId")., UserRepositoryTest returns the same tenant rows after the UserRepository query change.])
- refactor-sql-repository: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- refactor-feature-flag: facts recall 50% (expected [Flags.isEnabled, on/off matrix], got [FeatureFlagService now returns `flags.isEnabled(flag, context)`., FeatureFlagServiceTest retains the same on/off evaluation matrix.])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-repro-login: facts recall 50% (expected [lockedAccountReturns423, 401], got [LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true., LoginControllerTest#lockedAccountReturns423 performs POST /login and expects status().isLocked().])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles is a parameterized test with CSV cases "1,100","2,200","3,400"., RetryPolicyTest#delayDoubles asserts policy.delay(attempt) equals 100, 200, and 400 milliseconds for attempts 1 through 3., RetryPolicyTest produced 3 passing tests.])
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable uses @ParameterizedTest with @ValueSource(strings = {"a,b","a\"b","a\nb"})., CsvExporterTest#quotingIsStable covers CSV quoting for commas, quotes, and newlines., All 3 CsvExporterTest#quotingIsStable cases pass.])
- test-parameterized-csv: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-security-headers: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-repro-null-cache: facts recall 67% (expected [1000, null, parallel], got [CacheConcurrencyTest#concurrentReadersNeverSeeNull runs 1,000 parallel cache.get("k") assertions expecting non-null results., CacheConcurrencyTest#concurrentReadersNeverSeeNull fails before the UserCache fix and passes after the fix.])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-contract-openapi: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- test-flaky-clock: title verb class 'fix' missing (got 'Extended SchedulerTest clock control with Clock.fixed')
- test-flaky-clock: title subject [flaky, clock] missing (got 'Extended SchedulerTest clock control with Clock.fixed')
- test-flaky-clock: title trace-visible subject [flaky, clock] missing (got 'Extended SchedulerTest clock control with Clock.fixed')
- security-redact-log: title action [secure] missing: title leads 'modify' (got 'Fixed credential redaction for password token and secret in LogSanitizer.java')
- security-redact-log: title verb class 'redact' missing (got 'Fixed credential redaction for password token and secret in LogSanitizer.java')
- security-sql-injection: title action [secure] missing: title leads 'modify' (got 'Fixed UserDao.findByEmail SQL injection with parameter placeholder')
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail executes jdbcTemplate.query("select * from users where email = ?", mapper, email)., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the UserDao.findByEmail query change., UserDao.findByEmail no longer interpolates the email value directly into the SQL query string.])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Restricted AdminController endpoints authorization to ROLE_ADMIN')
- security-csrf: title action [secure] missing: title leads 'modify' (got 'Fixed CSRF rejection for mismatched tokens in CsrfFilter.java')
- security-csrf: facts recall 50% (expected [403, mismatched token], got [src/main/java/com/acme/web/CsrfFilter.java rejects a request with response.sendError(403) when csrfToken.matches(request) returns false., CsrfFilterTest#postWithoutTokenIs403 passes after the CsrfFilter rejection change.])
- security-secret-config: title lost identifiers [application.yml, ACME_API_KEY] (got 'Redacted literal API key in application.yml')
- security-secret-config: title verb class 'remove' missing (got 'Redacted literal API key in application.yml')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Redacted literal API key in application.yml')
- security-path-traversal: title action [secure] missing: title leads 'modify' (got 'Fixed path traversal in FileDownloadController')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title action [secure] missing: title leads 'modify' (got 'Fixed RateLimiter empty token bucket response in RateLimiter.java')
- security-rate-limit: title verb class 'add' missing (got 'Fixed RateLimiter empty token bucket response in RateLimiter.java')
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- security-cookie-httponly: title trace-visible subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- config-hikari-pool: title coverage 75% (expected keywords [change, hikari, pool, size])
- config-hikari-pool: title verb class 'change' missing (got 'Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml')
- config-log-level: title subject [log, level] missing (got 'Changed root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: title trace-visible subject [level] missing (got 'Changed root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: facts recall 50% (expected [INFO, JSON appender], got [src/main/resources/logback-spring.xml sets the root logger level to INFO., src/main/resources/logback-spring.xml routes the root logger to the appender named JSON., LogbackConfigTest parses src/main/resources/logback-spring.xml.])
- config-cors: type 'bugfix' != expected 'change'
- config-cors: concepts recall 0% (expected [what-changed, trade-off], got [problem-solution, gotcha])
- config-timeout: concepts recall 50% (expected [what-changed, trade-off], got [trade-off, pattern])
- config-docker-healthcheck: title coverage 75% (expected keywords [add, docker, healthcheck, actuator])
- config-docker-healthcheck: title verb class 'add' missing (got 'Extended Dockerfile HEALTHCHECK for /actuator/health')
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-maven-plugin: title coverage 75% (expected keywords [add, jacoco, plugin, pom])
- config-maven-plugin: title verb class 'add' missing (got 'Extended Maven coverage reporting with jacoco-maven-plugin in pom.xml')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Documented local setup with JDK 21 and PostgreSQL in README.md')
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title verb class 'add' missing (got 'Documented queue recovery procedure in docs/runbook.md')
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title verb class 'change' missing (got 'Documented 2.4.0 offline replay and V20 migration fix in CHANGELOG.md')
- docs-migration-guide: title coverage 75% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [pattern, what-changed])
- docs-security-policy: title coverage 50% (expected keywords [add, security, policy, disclosure])
- docs-security-policy: title verb class 'add' missing (got 'Documented vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: title subject [security, policy] missing (got 'Documented vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: title trace-visible subject [security, policy] missing (got 'Documented vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: call failed: IllegalStateException: all attempts returned no content
- deploy-k8s-probe: call failed: IllegalStateException: all attempts returned no content
- deploy-github-actions: call failed: IllegalStateException: all attempts returned no content
- deploy-docker-multistage: title lost identifiers [Dockerfile, eclipse-temurin:21-jre] (got 'Converted Dockerfile to multi-stage build in Dockerfile')
- deploy-docker-multistage: concepts recall 50% (expected [what-changed, trade-off], got [trade-off, pattern])
- deploy-terraform-iam: title coverage 50% (expected keywords [restrict, iam, terraform, s3])
- deploy-terraform-iam: title subject [iam, policy] missing (got 'Restricted deploy role to AmazonS3ReadOnlyAccess in infra/main.tf')
- deploy-terraform-iam: title trace-visible subject [iam, policy] missing (got 'Restricted deploy role to AmazonS3ReadOnlyAccess in infra/main.tf')
- deploy-terraform-iam: concepts recall 50% (expected [what-changed, trade-off], got [trade-off, pattern])
- deploy-helm-values: concepts recall 50% (expected [what-changed, trade-off], got [trade-off, pattern])
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 50% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: title verb class 'enable' missing (got 'Changed canary rollout weight to 10 and errorThreshold to 0.02 in infra/canary.yaml')
- deploy-canary: concepts recall 50% (expected [what-changed, trade-off], got [trade-off, pattern])
- remove-dead-code: title coverage 50% (expected keywords [remove, dead, code, orderservice])
- remove-dead-code: title subject [dead, code] missing (got 'Removed legacySync from OrderService')
- remove-stale-config: title subject [stale, setting] missing (got 'Removed legacy-search.enabled from application.yml')
- remove-deprecated-endpoint: title coverage 50% (expected keywords [remove, deprecated, endpoint, usercontroller])
- remove-deprecated-endpoint: title subject [deprecated, endpoint] missing (got 'Removed legacy GET /v1/users from UserController')
- remove-feature-flag: type 'refactor' != expected 'change'
- remove-feature-flag: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset off-by-one in OrderQuery.java</title>   <subtitle>First-page pagination now starts at offset 0 instead of -1.</subtitle>   <facts>     <fact>OrderQuery.pageOffset now calculates int offset as page * size without subtracting 1.</fact>     <fact>OrderQueryTest#firstPageStartsAtZero passes with the corrected first-page offset.</... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache.get concurrent null placeholder writes in UserCache</title>   <subtitle>UserCache.get now atomically loads missing entries through computeIfAbsent.</subtitle>   <facts>     <fact>src/main/java/com/acme/cache/UserCache.java changed return cache.get(id) to return cache.computeIfAbsent(id, loader::load).</fact>     <fact>UserCache.get uses ... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for off-by-one-day bug</title>   <subtitle>A regression test pins UTC input to the Asia/Shanghai business date across midnight.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins 2026-11-01T23:30Z and expects the Asia/Shanghai business date 2026-11-02.</fact>     <fact>DailyRe... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed missing-order null result in PaymentService</title>   <subtitle>PaymentService now throws PaymentNotFoundException when repository lookup finds no matching orderId.</subtitle>   <facts>     <fact>PaymentService uses repository.findByOrderId(orderId).orElseThrow(() -> new PaymentNotFoundException(orderId)) for missing orders.</fact>     <fact>Payme... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration boundary for tokens expiring at now</title>   <subtitle>JwtVerifier now rejects tokens whose expiration is exactly equal to the current instant.</subtitle>   <facts>     <fact>JwtVerifier.java changed expiration validation to reject when claims.getExpiration() is before or equal to now.</fact>     <fact>JwtVerifierTest#expir... [truncated]
```

### bug-csv-crlf

```text
(no raw answer: the call failed before returning)
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment lost updates with AtomicLong.incrementAndGet</title>   <subtitle>Atomic increment eliminates race-prone get/set updates under concurrent calls.</subtitle>   <facts>     <fact>CounterService.increment in src/main/java/com/acme/metrics/CounterService.java now returns counter.incrementAndGet().</fact>     <fact>CounterService... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>refactor</type>   <title>Refactored OrderRepository.findAllByCustomerId to fetch items with EntityGraph</title>   <subtitle>Order item loading now uses one SQL statement instead of separate item queries.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId(String customerId) now declares @EntityGraph(attributePaths = "items").</fact>     <fact>QueryCountTest obs... [truncated]
```

### bug-regex-redos

```text
(no raw answer: the call failed before returning)
```

### refactor-invoice

```text
<observation>   <type>refactor</type>   <title>Refactored InvoiceService tax rendering delegation to InvoiceRenderer</title>   <subtitle>InvoiceService now delegates tax calculation and invoice rendering to dedicated collaborators without changing behavior.</subtitle>   <facts>     <fact>InvoiceService.java now calls renderer.render(taxCalculator.calculate(invoice), invoice).</fact>     <fact>Invo... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored AuthController JWT issuance to TokenService.issue</title>   <subtitle>AuthController delegates token creation instead of building JWTs inline.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/AuthController.java now returns tokenService.issue(authentication).</fact>     <fact>AuthController no longer builds JWTs inline.</fact>    ... [truncated]
```

### refactor-order-mapper

```text
<observation>   <type>refactor</type>   <title>Refactored OrderMapper DTO conversion in OrderMapper.java</title>   <subtitle>OrderMapper now converts entities to OrderDto through one mapper.convertValue call.</subtitle>   <facts>     <fact>OrderMapper.convertValue(entity, OrderDto.class) replaces 40 hand-written setters.</fact>     <fact>OrderMapperTest compares the same OrderDto fields after the ... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Refactored RetryPolicy delay calculation through BackoffStrategy</title>   <subtitle>RetryPolicy delegates delay computation to BackoffStrategy while preserving the expected retry sequence.</subtitle>   <facts>     <fact>src/main/java/com/acme/resilience/RetryPolicy.java now returns backoff.nextDelay(attempt) for retry delay calculation.</fact>     <f... [truncated]
```

### refactor-logging-filter

```text
<observation>   <type>refactor</type>   <title>Refactored LoggingFilter access logging through logExchange</title>   <subtitle>LoggingFilter now owns timing and header capture while preserving the verified access-log format.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/LoggingFilter.java calls logExchange(exchange, StopWatch.createStarted()).</fact>     <fact>LoggingFilterTest verifie... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Fixed tenant filtering query injection in UserRepository.java</title>   <subtitle>UserRepository now uses a parameterized JPQL query for tenant row retrieval.</subtitle>   <facts>     <fact>UserRepository.java replaces string-concatenated SQL with @Query("select u from User u where u.tenantId = :tenantId").</fact>     <fact>UserRepositoryTest returns th... [truncated]
```

### refactor-feature-flag

```text
<observation>   <type>refactor</type>   <title>Refactored FeatureFlagService evaluation to delegate to Flags.isEnabled</title>   <subtitle>FeatureFlagService now delegates flag evaluation while preserving the existing on/off test matrix.</subtitle>   <facts>     <fact>FeatureFlagService now returns `flags.isEnabled(flag, context)`.</fact>     <fact>FeatureFlagServiceTest retains the same on/off ev... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai date</title>   <subtitle>Regression test pins the reported UTC-versus-business-date bug using an Asia/Shanghai timestamp.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone asserts report.dateAt(2026-11-01T23:30:00Z, Asia/Shanghai) equals 2026-11-02.</fact>     <f... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 reproducing locked-account status</title>   <subtitle>A regression test expects HTTP 423 when login targets the locked account "bob".</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true.</fact>     <fact>LoginControllerTest#lockedAccountR... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized cases for exponential backoff</title>   <subtitle>Retry delay assertions cover attempts 1 through 3 at 100, 200, and 400 milliseconds.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles is a parameterized test with CSV cases "1,100","2,200","3,400".</fact>     <fact>RetryPolicyTest#delayDoubles ... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable parameterized cases for CSV quoting</title>   <subtitle>Parameterized tests cover commas, embedded quotes, and newlines in exported CSV values.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable uses @ParameterizedTest with @ValueSource(strings = {"a,b","a\"b","a\nb"}).</fact>     <fact>CsvExporterTest#... [truncated]
```

### test-security-headers

```text
<observation>   <type>feature</type>   <title>Added SecurityHeadersTest#responseHasCspAndNosniff security header coverage</title>   <subtitle>Regression coverage verifies CSP and nosniff response headers for the root endpoint.</subtitle>   <facts>     <fact>SecurityHeadersTest#responseHasCspAndNosniff asserts that GET / returns Content-Security-Policy containing "default-src 'self'".</fact>     <f... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull reproducing UserCache null</title>   <subtitle>Parallel regression test checks that 1,000 concurrent readers never observe a null cached value.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull runs 1,000 parallel cache.get("k") assertions expecting non-null result... [truncated]
```

### test-contract-openapi

```text
<observation>   <type>feature</type>   <title>Added OpenApiContractTest#generatedSpecMatchesCommittedFile schema drift contract</title>   <subtitle>OpenAPI contract test compares springdoc output with docs/openapi.yaml and fails when schemas differ.</subtitle>   <facts>     <fact>OpenApiContractTest#generatedSpecMatchesCommittedFile was added in src/test/java/com/acme/api/OpenApiContractTest.java.... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Extended SchedulerTest clock control with Clock.fixed</title>   <subtitle>SchedulerTest replaces Thread.sleep with a fixed UTC clock, eliminating timing-related flakiness.</subtitle>   <facts>     <fact>src/test/java/com/acme/scheduling/SchedulerTest.java sets scheduler.clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC).</fact> ... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed credential redaction for password token and secret in LogSanitizer.java</title>   <subtitle>LogSanitizer now masks credential values before log output, with coverage for all three supported keys.</subtitle>   <facts>     <fact>src/main/java/com/acme/security/LogSanitizer.java replaces values matching "(password|token|secret)=\S+" with "$1=***".</f... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed UserDao.findByEmail SQL injection with parameter placeholder</title>   <subtitle>UserDao.findByEmail now queries with a bound email parameter instead of concatenated SQL.</subtitle>   <facts>     <fact>UserDao.findByEmail executes jdbcTemplate.query("select * from users where email = ?", mapper, email).</fact>     <fact>SqlInjectionTest#emailWithQ... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Restricted AdminController endpoints authorization to ROLE_ADMIN</title>   <subtitle>Every AdminController endpoint now requires the ADMIN role, and the user-role request receives HTTP 403.</subtitle>   <facts>     <fact>AdminController applies @PreAuthorize("hasRole('ADMIN')") in src/main/java/com/acme/admin/AdminController.java.</fact>     <fact>Admin... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Fixed CSRF rejection for mismatched tokens in CsrfFilter.java</title>   <subtitle>CsrfFilter now returns HTTP 403 when a state-changing request presents a mismatched token.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/CsrfFilter.java rejects a request with response.sendError(403) when csrfToken.matches(request) returns false.</fact>     <fa... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Redacted literal API key in application.yml</title>   <subtitle>application.yml now resolves the API key from the ACME_API_KEY environment variable.</subtitle>   <facts>     <fact>src/main/resources/application.yml replaces api-key: sk-live-123 with api-key: ${ACME_API_KEY}.</fact>     <fact>SecretScanTest passes after the api-key change in src/main/res... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Fixed path traversal in FileDownloadController</title>   <subtitle>File downloads now reject names resolving outside the configured base directory.</subtitle>   <facts>     <fact>FileDownloadController resolves the requested name with base.resolve(name).normalize().</fact>     <fact>FileDownloadController throws InvalidPathException when the resolved pa... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter empty token bucket response in RateLimiter.java</title>   <subtitle>RateLimiter now throws TooManyRequestsException when the eleventh request exceeds the token bucket capacity.</subtitle>   <facts>     <fact>RateLimiter.java calls bucket.tryConsume(1) and throws TooManyRequestsException when bucket.tryConsume(1) returns false.</fact>  ... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>Session cookies now enforce HttpOnly, Secure, and SameSite=Strict protections.</subtitle>   <facts>     <fact>SessionCookieConfig sets cookie.setHttpOnly(true).</fact>     <fact>SessionCookieConfig sets cookie.setSecure(true).</fact>     <fact>SessionCookieConfig sets cookie.setSam... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml</title>   <subtitle>HikariCP pool sizing now uses 20 maximum connections and 5 minimum idle connections.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20.</fact>     <fact>src/main/resources/application.yml s... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Changed root logger to INFO with JSON appender in logback-spring.xml</title>   <subtitle>Application logging now routes INFO-level root events through the JSON appender.</subtitle>   <facts>     <fact>src/main/resources/logback-spring.xml sets the root logger level to INFO.</fact>     <fact>src/main/resources/logback-spring.xml routes the root logger to... [truncated]
```

### config-cors

```text
<observation>   <type>bugfix</type>   <title>Restricted /api/** CORS origin and methods in CorsConfig</title>   <subtitle>CorsConfig now permits only the production app origin for GET and POST API requests.</subtitle>   <facts>     <fact>CorsConfig maps "/api/**" with allowedOrigins("https://app.acme.com").</fact>     <fact>CorsConfig permits only allowedMethods("GET","POST") for "/api/**".</fact>... [truncated]
```

### config-timeout

```text
<observation>   <type>change</type>   <title>Set WebClient responseTimeout to 5 seconds in WebClientConfig</title>   <subtitle>WebClient requests now terminate after five seconds without a response.</subtitle>   <facts>     <fact>WebClientConfig configures HttpClient.create().responseTimeout(Duration.ofSeconds(5)).</fact>     <fact>WebClientConfigTest reads the configured response timeout from the... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Extended Dockerfile HEALTHCHECK for /actuator/health</title>   <subtitle>Container health now checks the actuator endpoint every 30 seconds.</subtitle>   <facts>     <fact>Dockerfile defines HEALTHCHECK --interval=30s with curl -f http://localhost:8080/actuator/health.</fact>     <fact>Dockerfile HEALTHCHECK exits with status 1 when curl fails.</fact>  ... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Extended Maven coverage reporting with jacoco-maven-plugin in pom.xml</title>   <subtitle>Maven verification now generates JaCoCo XML coverage reports at target/site/jacoco/jacoco.xml.</subtitle>   <facts>     <fact>pom.xml configures org.jacoco:jacoco-maven-plugin with version 0.8.12.</fact>     <fact>mvn verify writes target/site/jacoco/jacoco.xml.</f... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Documented local setup with JDK 21 and PostgreSQL in README.md</title>   <subtitle>README.md now provides three steps to start the project locally.</subtitle>   <facts>     <fact>README.md requires JDK 21 for local setup.</fact>     <fact>README.md starts PostgreSQL with docker compose up -d postgres.</fact>     <fact>README.md starts the application wi... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Documented queue recovery procedure in docs/runbook.md</title>   <subtitle>Queue recovery now instructs operators to inspect pending messages, restart the worker, and verify the health endpoint.</subtitle>   <facts>     <fact>docs/runbook.md documents the queue recovery step "check pending_messages".</fact>     <fact>docs/runbook.md instructs operators ... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Documented 2.4.0 offline replay and V20 migration fix in CHANGELOG.md</title>   <subtitle>Release notes record offline replay evaluation and the corrected V20 migration conflict.</subtitle>   <facts>     <fact>CHANGELOG.md adds a 2.4.0 entry dated 2026-11-03.</fact>     <fact>CHANGELOG.md identifies offline replay evaluation as added in 2.4.0.</fact>   ... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisite in docs/MIGRATION.md</title>   <subtitle>V20 requires backing up mem_sessions and enabling manual sentinel review.</subtitle>   <facts>     <fact>docs/MIGRATION.md requires backing up mem_sessions before the V20 migration.</fact>     <fact>docs/MIGRATION.md requires app.manual_sentinel_reviewed=true when running the... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Documented vulnerability disclosure channel in SECURITY.md</title>   <subtitle>SECURITY.md directs vulnerability reports to security@acme.com and prohibits public issues.</subtitle>   <facts>     <fact>SECURITY.md defines security@acme.com as the vulnerability reporting channel.</fact>     <fact>SECURITY.md forbids reporting vulnerabilities through publ... [truncated]
```

### docs-contributing

```text
(no raw answer: the call failed before returning)
```

### deploy-k8s-probe

```text
(no raw answer: the call failed before returning)
```

### deploy-github-actions

```text
(no raw answer: the call failed before returning)
```

### deploy-docker-multistage

```text
<observation>   <type>change</type>   <title>Converted Dockerfile to multi-stage build in Dockerfile</title>   <subtitle>Runtime images now contain only the built JAR on eclipse-temurin:21-jre, reducing image size.</subtitle>   <facts>     <fact>Dockerfile now copies /app/target/app.jar from the build stage into /app/app.jar in the runtime stage.</fact>     <fact>Dockerfile uses eclipse-temurin:21... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted deploy role to AmazonS3ReadOnlyAccess in infra/main.tf</title>   <subtitle>Terraform now grants the deploy role read-only Amazon S3 access instead of full S3 permissions.</subtitle>   <facts>     <fact>infra/main.tf resource aws_iam_role_policy.deploy sets policy_arn to arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess.</fact>     <fact>terrafor... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Changed Helm replica count to 3 and memory limit to 2Gi in infra/values.yaml</title>   <subtitle>Helm deployment now requests three replicas with a 2Gi memory ceiling per pod.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount to 3.</fact>     <fact>infra/values.yaml sets resources.limits.memory to 2Gi.</fact>     <fact>helm template ren... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added deployment rollback script in scripts/rollback.sh</title>   <subtitle>Rollback script undoes the claude-mem deployment and waits until rollout completion is confirmed.</subtitle>   <facts>     <fact>scripts/rollback.sh runs `kubectl rollout undo deployment/claude-mem`.</fact>     <fact>scripts/rollback.sh runs `kubectl rollout status deployment/c... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Changed canary rollout weight to 10 and errorThreshold to 0.02 in infra/canary.yaml</title>   <subtitle>Canary releases now serve 10 percent of traffic and roll back when errors exceed 2 percent.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true.</fact>     <fact>infra/canary.yaml sets canary.weight to 10 for a 10 percent roll... [truncated]
```

### remove-dead-code

```text
<observation>   <type>refactor</type>   <title>Removed legacySync from OrderService</title>   <subtitle>OrderService no longer exposes the obsolete warehouse push synchronization method.</subtitle>   <facts>     <fact>OrderService.legacySync was removed from src/main/java/com/acme/orders/OrderService.java</fact>     <fact>OrderService.legacySync previously called warehouse.push(order)</fact>     <... [truncated]
```

### remove-stale-config

```text
<observation>   <type>change</type>   <title>Removed legacy-search.enabled from application.yml</title>   <subtitle>The search module now reads only from the search-v2 configuration.</subtitle>   <facts>     <fact>src/main/resources/application.yml no longer defines legacy-search.enabled.</fact>     <fact>The search module reads only search-v2.</fact>   </facts>   <narrative>The legacy search togg... [truncated]
```

### remove-deprecated-endpoint

```text
<observation>   <type>change</type>   <title>Removed legacy GET /v1/users from UserController</title>   <subtitle>UserController now exposes only the retained /v2/users endpoint.</subtitle>   <facts>     <fact>UserController.listLegacy was removed from src/main/java/com/acme/users/UserController.java.</fact>     <fact>The @GetMapping("/v1/users") mapping was removed from UserController.</fact>    ... [truncated]
```

### remove-feature-flag

```text
<observation>   <type>refactor</type>   <title>Removed FeatureFlags.LEGACY_SEARCH constant in FeatureFlags.java</title>   <subtitle>Legacy search callers now use the SEARCH_V2 feature flag.</subtitle>   <facts>     <fact>FeatureFlags.LEGACY_SEARCH was removed from src/main/java/com/acme/flags/FeatureFlags.java.</fact>     <fact>FeatureFlags callers now use SEARCH_V2 instead of the removed legacy-s... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
