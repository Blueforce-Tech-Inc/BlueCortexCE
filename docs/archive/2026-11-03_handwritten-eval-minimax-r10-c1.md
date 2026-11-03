golden set: main (87 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: r10-c1-main
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 87 unique; runs scored: 3; key repeat: 3; real calls: 113 (cap none); duration: 1017881 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 93% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 20/0/0/67 |
| format errors | 0 |
| validation rejections | 1 |
| call failures | 5 |
| title exact-hit rate (legacy) | 51% |
| title normalized hit rate (legacy) | 54% |
| title action-class hit rate (A6) | 90% (61 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 79% |
| title subject hit rate (legacy, diagnostic) | 72% |
| title trace-visible subject hit rate (A6b) | 85% (53 scored, 8 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 61% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 72% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 56% |
| title identifier hit rate | 95% |
| type accuracy | 93% |
| facts keyword precision (legacy) / recall | 79% / 92% |
| facts informative precision | 80% |
| facts trace-support rate | 100% |
| concepts precision / recall | 84% / 84% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 2476 / 6912 / 664921 |
| tokens p50 / p95 / max / mean per call | 270 / 4038 / 4083 / 1082 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 80% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 90% | PASS |
| A6b | title trace-visible subject | >= 90% | 85% | **FAIL** |
| A6c | title identifiers | = 100% | 95% | **FAIL** |
| A6 | all three title dimensions together | all pass | 72% | **FAIL** |
| A11 | p95 latency vs baseline | <= 1.5x baseline | 6912 ms (baseline 5648 ms) | PASS |
| A12 | p95 tokens vs baseline | <= 1.5x baseline | 4038 tokens (baseline 4048) | PASS |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |
| exception | 5 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 87 | 100% | 54% | 61% | 79% | 80% | 92% | 0 |
| 1 | 13 | 100% | 56% | 56% | 93% | 93% | 100% | 0 |
| 2 | 13 | 100% | 56% | 56% | 89% | 93% | 100% | 0 |

- average: skip 100%, title 55%, title contract 57%, facts P/R 87%/97%, facts actionable 88%
- worst run: skip 100%, title 54%, title contract 56%, facts P/R 79%/92%, facts actionable 80%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 9/12 | 8/12 | 4/12 |
| refactor | 10 | 10/10 | 8/10 | 4/10 |
| test-add | 9 | 9/9 | 9/9 | 8/9 |
| security-fix | 9 | 9/9 | 9/9 | 5/9 |
| config-change | 12 | 12/12 | 12/12 | 9/12 |
| docs-update | 8 | 7/8 | 7/8 | 2/8 |
| deploy-change | 7 | 5/7 | 4/7 | 1/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Run-to-run variance (repeated samples)

| metric | min | max | mean | std dev |
|---|---|---|---|---|
| type accuracy | 93% | 100% | 98% | 3% |
| title action class | 89% | 100% | 93% | 5% |
| title trace-visible subject | 85% | 100% | 95% | 7% |
| title identifiers | 78% | 95% | 87% | 7% |
| title trace contract | 72% | 78% | 76% | 3% |
| facts informative | 80% | 93% | 88% | 6% |
| skip accuracy | 100% | 100% | 100% | 0% |

## Inconsistent samples (type or action changed between runs)

2 of 13 repeated samples disagreed:

- bug-file-encoding: type {unparsed, bugfix}; action {unparsed, modify}
- security-redact-log: action {modify, secure}

## Unreachable subject labels (excluded from A6b)

8 sample(s) declare subject tokens the recorded trace never contains:

- bug-offbyone-pagination: [pagination]
- bug-null-cache: [race]
- bug-race-counter: [race]
- bug-n-plus-one: [n+1]
- refactor-auth-controller: [delegation]
- remove-dead-code: [dead, code]
- remove-stale-config: [stale, setting]
- remove-deprecated-endpoint: [deprecated, endpoint]

## Failure clusters (first run)

| cluster | misses | share of scored (61) |
|---|---|---|
| identifier missing | 3 | 5% |
| action class wrong | 6 | 10% |
| subject wrong | 8 | 13% |
| type wrong | 4 | 7% |

### identifier missing (3)

- bug-null-cache — expected identifiers [UserCache, computeIfAbsent], title 'Fixed concurrent null placeholder writes in UserCache.get'
- security-secret-config — expected identifiers [application.yml, ACME_API_KEY], title 'Hardened api-key secret handling in src/main/resources/application.yml'
- deploy-docker-multistage — expected identifiers [Dockerfile, eclipse-temurin:21-jre], title 'Converted Dockerfile to multi-stage runtime image'

### action class wrong (6)

- security-redact-log — expected [secure], got 'modify' from title 'Fixed credential redaction for password, token and secret values in LogSanitizer.java'
- security-sql-injection — expected [secure], got 'modify' from title 'Fixed UserDao.findByEmail SQL injection in UserDao.java'
- security-csrf — expected [secure], got 'modify' from title 'Fixed CSRF token validation for state-changing requests in CsrfFilter.java'
- security-path-traversal — expected [secure], got 'modify' from title 'Fixed path traversal escape rejection in FileDownloadController'
- security-rate-limit — expected [secure], got 'modify' from title 'Fixed RateLimiter empty token bucket handling in RateLimiter.java'
- config-maven-plugin — expected [modify], got 'create' from title 'Added jacoco-maven-plugin 0.8.12 to pom.xml'

### subject wrong (8)

- refactor-invoice — expected subject tokens [delegation], title 'Refactored InvoiceService invoice rendering through TaxCalculator and InvoiceRenderer'
- refactor-order-mapper — expected subject tokens [convertValue], title 'Refactored OrderMapper DTO conversion in OrderMapper.java'
- refactor-feature-flag — expected subject tokens [delegation], title 'Refactored FeatureFlagService evaluation through Flags.isEnabled'
- test-flaky-clock — expected subject tokens [flaky, clock], title 'Updated SchedulerTest clock to fixed time in SchedulerTest.java'
- security-cookie-httponly — expected subject tokens [httponly, cookie], title 'Hardened session cookie flags in SessionCookieConfig'
- config-log-level — expected subject tokens [level], title 'Changed root logger to INFO with JSON appender in logback-spring.xml'
- docs-security-policy — expected subject tokens [security, policy], title 'Documented vulnerability disclosure channel in SECURITY.md'
- deploy-rollback-script — expected subject tokens [rollback, script], title 'Added rollback.sh for deployment/claude-mem rollback'

### type wrong (4)

- bug-n-plus-one — expected 'bugfix', got 'refactor' (title 'Refactored OrderRepository.findAllByCustomerId to fetch items in one query')
- refactor-sql-repository — expected 'refactor', got 'bugfix' (title 'Fixed tenant filtering with JPQL in UserRepository')
- deploy-rollback-script — expected 'change', got 'feature' (title 'Added rollback.sh for deployment/claude-mem rollback')
- remove-dead-code — expected 'refactor', got 'change' (title 'Removed OrderService.legacySync in OrderService.java')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 50%/50% | 4771 |
| bug-null-cache | bug-fix | observation | observation | pass | 50% | MISS (subject unreachable, id MISS, action hit) | miss | match | 100%/100% | 100% | 50%/50% | 2344 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 2138 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 3850 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 2486 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 3922 |
| bug-csv-crlf | bug-fix | observation | call-failed | FAIL | - | - | - | - | - | - | - | 6645 |
| bug-race-counter | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/67% | 100% | 50%/50% | 5454 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 25% | hit | miss | MISS | 100%/100% | 100% | 100%/100% | 3429 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 3310 |
| bug-regex-redos | bug-fix | observation | call-failed | FAIL | - | - | - | - | - | - | - | 6554 |
| bug-file-encoding | bug-fix | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 2443 |
| refactor-invoice | refactor | observation | observation | pass | 67% | MISS (subject MISS, id hit, action hit) | miss | match | 67%/100% | 67% | 100%/100% | 2278 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 33% | 100%/100% | 3434 |
| refactor-config-loader | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 2857 |
| refactor-order-mapper | refactor | observation | observation | pass | 67% | MISS (subject MISS, id hit, action hit) | miss | match | 50%/100% | 50% | 100%/100% | 2216 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 2368 |
| refactor-logging-filter | refactor | observation | observation | pass | 67% | hit | miss | match | 50%/100% | 50% | 100%/100% | 2641 |
| refactor-sql-repository | refactor | observation | observation | pass | 67% | hit | miss | MISS | 50%/50% | 100% | 50%/50% | 3004 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 0%/0% | 50% | 100%/100% | 5707 |
| refactor-feature-flag | refactor | observation | observation | pass | 67% | MISS (subject MISS, id hit, action hit) | miss | match | 100%/50% | 100% | 100%/100% | 2774 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 2688 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 2677 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/50% | 100% | 100%/100% | 8195 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 3644 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 3874 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 6912 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 3966 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 50%/50% | 4756 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 100%/100% | 100% | 100%/100% | 4460 |
| security-redact-log | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 100%/100% | 2538 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 33%/50% | 67% | 100%/100% | 2405 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | hit | miss | match | 100%/50% | 100% | 100%/100% | 3042 |
| security-csrf | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 50%/50% | 100% | 100%/100% | 2545 |
| security-secret-config | security-fix | observation | observation | pass | 50% | MISS (subject hit, id MISS, action hit) | miss | match | 100%/100% | 100% | 100%/100% | 2076 |
| security-xxe | security-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 1719 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 67%/100% | 100% | 100%/100% | 2408 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS (subject hit, id hit, action MISS) | miss | match | 33%/100% | 100% | 50%/50% | 1551 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 67%/100% | 100% | 100%/100% | 5162 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 67% | 100%/100% | 5367 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/50% | 1844 |
| config-log-level | config-change | observation | observation | pass | 100% | MISS (subject MISS, id hit, action hit) | hit | match | 67%/100% | 33% | 50%/50% | 664921 |
| config-cors | config-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 2729 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 4202 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 2322 |
| config-docker-healthcheck | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 5899 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 100%/100% | 100% | 50%/50% | 1882 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 0% | 100%/100% | 3436 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 33% | 100%/100% | 2129 |
| docs-runbook | docs-update | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 0% | 100%/100% | 2125 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 2004 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 1693 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 1914 |
| docs-security-policy | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id hit, action hit) | miss | match | 100%/100% | 50% | 50%/50% | 1375 |
| docs-contributing | docs-update | observation | call-failed | FAIL | - | - | - | - | - | - | - | 5629 |
| deploy-k8s-probe | deploy-change | observation | call-failed | FAIL | - | - | - | - | - | - | - | 6884 |
| deploy-github-actions | deploy-change | observation | call-failed | FAIL | - | - | - | - | - | - | - | 8441 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 75% | MISS (subject hit, id MISS, action hit) | miss | match | 33%/100% | 67% | 100%/100% | 1943 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 50% | 100%/100% | 1777 |
| deploy-helm-values | deploy-change | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 100% | 100%/100% | 2013 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | MISS | 67%/100% | 33% | 100%/100% | 2592 |
| deploy-canary | deploy-change | observation | observation | pass | 50% | hit | miss | match | 67%/100% | 100% | 100%/100% | 3006 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 907 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1014 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 25953 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1010 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1023 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1384 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1070 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2157 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1269 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1142 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 945 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1332 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1492 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1048 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 770 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1016 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4902 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1933 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1149 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3788 |
| remove-dependency | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1619 |
| remove-dead-code | refactor | observation | observation | pass | 50% | hit | miss | MISS | 100%/100% | 0% | 100%/100% | 2216 |
| remove-stale-config | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1532 |
| remove-deprecated-endpoint | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 2851 |
| remove-feature-flag | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 2476 |

## Problems

- bug-offbyone-pagination: title coverage 67% (expected keywords [fix, pagination, orderquery])
- bug-offbyone-pagination: title subject [pagination] missing (got 'Fixed OrderQuery.pageOffset off-by-one in OrderQuery.java')
- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed concurrent null placeholder writes in UserCache.get')
- bug-null-cache: title subject [race] missing (got 'Fixed concurrent null placeholder writes in UserCache.get')
- bug-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-csv-crlf: call failed: IllegalStateException: all attempts returned no content
- bug-race-counter: title coverage 75% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: title subject [race] missing (got 'Fixed CounterService.increment lost updates with AtomicLong.incrementAndGet')
- bug-race-counter: facts recall 67% (expected [incrementAndGet, AtomicLong, 1000], got [CounterService.increment uses counter.incrementAndGet() instead of counter.set(counter.get() + 1); return counter.get()., CounterServiceConcurrencyTest passes with 1000 threads producing a count of 1000.])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-n-plus-one: title coverage 25% (expected keywords [fix, n+1, orderrepository, entitygraph])
- bug-n-plus-one: title verb class 'fix' missing (got 'Refactored OrderRepository.findAllByCustomerId to fetch items in one query')
- bug-n-plus-one: title subject [n+1] missing (got 'Refactored OrderRepository.findAllByCustomerId to fetch items in one query')
- bug-n-plus-one: type 'refactor' != expected 'bugfix'
- bug-regex-redos: call failed: IllegalStateException: all attempts returned no content
- bug-file-encoding: validation rejected the observation: title too long: 108 chars > 100
- refactor-invoice: title coverage 67% (expected keywords [refactor, invoiceservice, delegates])
- refactor-invoice: title subject [delegation] missing (got 'Refactored InvoiceService invoice rendering through TaxCalculator and InvoiceRenderer')
- refactor-invoice: title trace-visible subject [delegation] missing (got 'Refactored InvoiceService invoice rendering through TaxCalculator and InvoiceRenderer')
- refactor-auth-controller: title subject [delegation] missing (got 'Refactored JWT issuance to TokenService.issue in AuthController')
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [src/main/java/com/acme/auth/AuthController.java now calls tokenService.issue(authentication)., AuthController no longer builds JWTs inline., AuthControllerTest remained unchanged and green.])
- refactor-order-mapper: title coverage 67% (expected keywords [refactor, ordermapper, convertvalue])
- refactor-order-mapper: title subject [convertValue] missing (got 'Refactored OrderMapper DTO conversion in OrderMapper.java')
- refactor-order-mapper: title trace-visible subject [convertValue] missing (got 'Refactored OrderMapper DTO conversion in OrderMapper.java')
- refactor-logging-filter: title coverage 67% (expected keywords [refactor, loggingfilter, logexchange])
- refactor-sql-repository: title coverage 67% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title verb class 'refactor' missing (got 'Fixed tenant filtering with JPQL in UserRepository')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [UserRepository.java uses @Query("select u from User u where u.tenantId = :tenantId")., UserRepositoryTest returns the same tenant rows after replacing string-concatenated SQL.])
- refactor-sql-repository: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- refactor-event-bus: facts recall 0% (expected [listener map, event type], got [EventBus.java uses listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event)) for dispatch., EventBus dispatches the same three events covered by EventBusTest after replacing the if/else dispatch chain.])
- refactor-feature-flag: title coverage 67% (expected keywords [refactor, featureflagservice, delegates])
- refactor-feature-flag: title subject [delegation] missing (got 'Refactored FeatureFlagService evaluation through Flags.isEnabled')
- refactor-feature-flag: title trace-visible subject [delegation] missing (got 'Refactored FeatureFlagService evaluation through Flags.isEnabled')
- refactor-feature-flag: facts recall 50% (expected [Flags.isEnabled, on/off matrix], got [FeatureFlagService now returns flags.isEnabled(flag, context) for feature flag evaluation.])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles uses @ParameterizedTest and @CsvSource with inputs 1,100; 2,200; and 3,400., RetryPolicyTest#delayDoubles asserts policy.delay(attempt) equals 100, 200, and 400 milliseconds for attempts 1 through 3., src/test/java/com/acme/resilience/RetryPolicyTest.java passes 3 tests.])
- test-security-headers: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-contract-openapi: concepts recall 50% (expected [what-changed, pattern], got [pattern, problem-solution])
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- test-flaky-clock: title verb class 'fix' missing (got 'Updated SchedulerTest clock to fixed time in SchedulerTest.java')
- test-flaky-clock: title subject [flaky, clock] missing (got 'Updated SchedulerTest clock to fixed time in SchedulerTest.java')
- test-flaky-clock: title trace-visible subject [flaky, clock] missing (got 'Updated SchedulerTest clock to fixed time in SchedulerTest.java')
- security-redact-log: title action [secure] missing: title leads 'modify' (got 'Fixed credential redaction for password, token and secret values in LogSanitizer.java')
- security-redact-log: title verb class 'redact' missing (got 'Fixed credential redaction for password, token and secret values in LogSanitizer.java')
- security-sql-injection: title action [secure] missing: title leads 'modify' (got 'Fixed UserDao.findByEmail SQL injection in UserDao.java')
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail uses jdbcTemplate.query with the SQL placeholder ? for the email parameter., UserDao.findByEmail no longer concatenates the email value into the users query string., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the UserDao.java change.])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Restricted AdminController endpoints authorization to ROLE_ADMIN in AdminController')
- security-authz-admin: facts recall 50% (expected [ROLE_ADMIN, 403], got [AdminController uses @PreAuthorize("hasRole('ADMIN')") for every endpoint., AdminAuthorizationTest#userRoleGets403 passes with the ROLE_ADMIN authorization requirement.])
- security-csrf: title action [secure] missing: title leads 'modify' (got 'Fixed CSRF token validation for state-changing requests in CsrfFilter.java')
- security-csrf: facts recall 50% (expected [403, mismatched token], got [src/main/java/com/acme/web/CsrfFilter.java returns HTTP 403 when csrfToken does not match the request token., CsrfFilterTest#postWithoutTokenIs403 passes for POST requests without a CSRF token.])
- security-secret-config: title lost identifiers [application.yml, ACME_API_KEY] (got 'Hardened api-key secret handling in src/main/resources/application.yml')
- security-secret-config: title verb class 'remove' missing (got 'Hardened api-key secret handling in src/main/resources/application.yml')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Hardened api-key secret handling in src/main/resources/application.yml')
- security-path-traversal: title action [secure] missing: title leads 'modify' (got 'Fixed path traversal escape rejection in FileDownloadController')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title action [secure] missing: title leads 'modify' (got 'Fixed RateLimiter empty token bucket handling in RateLimiter.java')
- security-rate-limit: title verb class 'add' missing (got 'Fixed RateLimiter empty token bucket handling in RateLimiter.java')
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- security-cookie-httponly: title trace-visible subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- config-hikari-pool: title coverage 75% (expected keywords [change, hikari, pool, size])
- config-hikari-pool: title verb class 'change' missing (got 'Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml')
- config-flyway-baseline: concepts recall 50% (expected [what-changed, trade-off], got [trade-off, gotcha])
- config-log-level: title subject [log, level] missing (got 'Changed root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: title trace-visible subject [level] missing (got 'Changed root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-docker-healthcheck: title coverage 75% (expected keywords [add, docker, healthcheck, actuator])
- config-docker-healthcheck: title verb class 'add' missing (got 'Extended container healthcheck against /actuator/health in Dockerfile')
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-maven-plugin: title action [modify] missing: title leads 'create' (got 'Added jacoco-maven-plugin 0.8.12 to pom.xml')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Documented local setup steps in README.md')
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title verb class 'add' missing (got 'Documented queue recovery steps in docs/runbook.md')
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title verb class 'change' missing (got 'Documented 2.4.0 offline replay evaluation and V20 migration conflict fix in CHANGELOG.md')
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
- deploy-docker-multistage: title lost identifiers [Dockerfile, eclipse-temurin:21-jre] (got 'Converted Dockerfile to multi-stage runtime image')
- deploy-terraform-iam: title coverage 75% (expected keywords [restrict, iam, terraform, s3])
- deploy-rollback-script: title coverage 75% (expected keywords [add, rollback, script, deployment])
- deploy-rollback-script: title subject [rollback, script] missing (got 'Added rollback.sh for deployment/claude-mem rollback')
- deploy-rollback-script: title trace-visible subject [rollback, script] missing (got 'Added rollback.sh for deployment/claude-mem rollback')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 50% (expected keywords [enable, canary, rollback, threshold])
- remove-dead-code: title coverage 50% (expected keywords [remove, dead, code, orderservice])
- remove-dead-code: title subject [dead, code] missing (got 'Removed OrderService.legacySync in OrderService.java')
- remove-dead-code: type 'change' != expected 'refactor'
- remove-stale-config: title subject [stale, setting] missing (got 'Removed legacy-search.enabled setting in application.yml')
- remove-deprecated-endpoint: title coverage 75% (expected keywords [remove, deprecated, endpoint, usercontroller])
- remove-deprecated-endpoint: title subject [deprecated, endpoint] missing (got 'Removed legacy /v1/users endpoint in UserController.java')
- remove-deprecated-endpoint: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- remove-feature-flag: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset off-by-one in OrderQuery.java</title>   <subtitle>OrderQuery.pageOffset now starts the first page at zero instead of subtracting one.</subtitle>   <facts>     <fact>OrderQuery.pageOffset changed from page * size - 1 to page * size.</fact>     <fact>OrderQueryTest#firstPageStartsAtZero passes with the corrected pagination offs... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed concurrent null placeholder writes in UserCache.get</title>   <subtitle>UserCache.get now atomically loads missing entries through cache.computeIfAbsent.</subtitle>   <facts>     <fact>src/main/java/com/acme/cache/UserCache.java changed return cache.get(id) to return cache.computeIfAbsent(id, loader::load).</fact>     <fact>UserCache.get no longer... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for off-by-one-day bug</title>   <subtitle>Regression test pins UTC-to-Asia/Shanghai business-date conversion at the date boundary.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone uses ZoneId.of("Asia/Shanghai").</fact>     <fact>DailyReportTest#reportUsesBusinessTimezone pins 2... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed PaymentService missing orderId null result in PaymentService.java</title>   <subtitle>Missing payments now raise PaymentNotFoundException instead of returning null.</subtitle>   <facts>     <fact>PaymentService uses repository.findByOrderId(orderId).orElseThrow(() -&gt; new PaymentNotFoundException(orderId)) in src/main/java/com/acme/payment/Payme... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration boundary handling in JwtVerifier.java</title>   <subtitle>Tokens expiring exactly at the current instant are now rejected by JwtVerifier.</subtitle>   <facts>     <fact>JwtVerifier.java checks claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now).</fact>     <fact>JwtVerifier rejects a token whose expir... [truncated]
```

### bug-csv-crlf

```text
(no raw answer: the call failed before returning)
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment lost updates with AtomicLong.incrementAndGet</title>   <subtitle>CounterService.increment now performs one atomic increment, and the concurrency test passes with 1000 threads.</subtitle>   <facts>     <fact>CounterService.increment uses counter.incrementAndGet() instead of counter.set(counter.get() + 1); return counter.get... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>refactor</type>   <title>Refactored OrderRepository.findAllByCustomerId to fetch items in one query</title>   <subtitle>Order items are eagerly loaded, eliminating the separate item query for customer orders.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId(String customerId) uses @EntityGraph(attributePaths = "items").</fact>     <fact>QueryCountTest observ... [truncated]
```

### bug-regex-redos

```text
(no raw answer: the call failed before returning)
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed platform-default encoding for FileReaderFactory in src/main/java/com/acme/files/FileReaderFactory.java</title>   <subtitle>FileReaderFactory now reads UTF-8 files consistently, including on a GBK-default JVM.</subtitle>   <facts>     <fact>src/main/java/com/acme/files/FileReaderFactory.java constructs InputStreamReader with StandardCharsets.UTF_8.... [truncated]
```

### refactor-invoice

```text
<observation>   <type>refactor</type>   <title>Refactored InvoiceService invoice rendering through TaxCalculator and InvoiceRenderer</title>   <subtitle>InvoiceService delegates tax calculation and rendering while preserving existing invoice output behaviour.</subtitle>   <facts>     <fact>InvoiceService.java now calls renderer.render(taxCalculator.calculate(invoice), invoice).</fact>     <fact>In... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored JWT issuance to TokenService.issue in AuthController</title>   <subtitle>AuthController delegates token creation to TokenService instead of constructing JWTs inline.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/AuthController.java now calls tokenService.issue(authentication).</fact>     <fact>AuthController no longer builds JW... [truncated]
```

### refactor-order-mapper

```text
<observation>   <type>refactor</type>   <title>Refactored OrderMapper DTO conversion in OrderMapper.java</title>   <subtitle>OrderMapper now converts entities to DTOs through one mapper call while preserving field comparisons.</subtitle>   <facts>     <fact>OrderMapper.java replaces 40 hand-written setters with mapper.convertValue(entity, OrderDto.class).</fact>     <fact>OrderMapperTest compares ... [truncated]
```

### refactor-logging-filter

```text
<observation>   <type>refactor</type>   <title>Refactored LoggingFilter timing and header capture in LoggingFilter.java</title>   <subtitle>LoggingFilter now delegates timing and header capture to logExchange while preserving the access-log line.</subtitle>   <facts>     <fact>LoggingFilter.java now calls logExchange(exchange, StopWatch.createStarted()).</fact>     <fact>LoggingFilterTest verifies... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Fixed tenant filtering with JPQL in UserRepository</title>   <subtitle>UserRepository now uses a parameterized tenantId query instead of string-concatenated SQL.</subtitle>   <facts>     <fact>UserRepository.java uses @Query("select u from User u where u.tenantId = :tenantId").</fact>     <fact>UserRepositoryTest returns the same tenant rows after repla... [truncated]
```

### refactor-event-bus

```text
<observation>   <type>refactor</type>   <title>Refactored EventBus dispatch to listener map in EventBus.java</title>   <subtitle>EventBus now dispatches events through listeners registered by event type instead of an if/else chain.</subtitle>   <facts>     <fact>EventBus.java uses listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event)) for dispatch.</fact>     <fact>EventBu... [truncated]
```

### refactor-feature-flag

```text
<observation>   <type>refactor</type>   <title>Refactored FeatureFlagService evaluation through Flags.isEnabled</title>   <subtitle>FeatureFlagService now delegates flag evaluation while preserving the existing on/off test matrix.</subtitle>   <facts>     <fact>FeatureFlagService now returns flags.isEnabled(flag, context) for feature flag evaluation.</fact>     <fact>FeatureFlagServiceTest retains... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone reproducing UTC-vs-business-date bug</title>   <subtitle>The regression test pins business-date conversion to Asia/Shanghai and currently exposes the UTC date mismatch.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone converts 2026-11-01T23:30:00Z with ZoneId.of("Asia/Shanghai") ... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 reproducing locked account status</title>   <subtitle>LoginControllerTest now pins locked-account login responses to HTTP 423.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true.</fact>     <fact>LoginControllerTest#lockedAccountReturns4... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized cases for exponential backoff</title>   <subtitle>Parameterized retry-delay tests verify attempts 1 through 3 produce 100, 200, and 400 millisecond delays.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles uses @ParameterizedTest and @CsvSource with inputs 1,100; 2,200; and 3,400.</fact>     <f... [truncated]
```

### test-security-headers

```text
<observation>   <type>feature</type>   <title>Added SecurityHeadersTest#responseHasCspAndNosniff security headers contract</title>   <subtitle>Regression coverage verifies the root response includes a restrictive CSP and nosniff protection.</subtitle>   <facts>     <fact>SecurityHeadersTest#responseHasCspAndNosniff asserts GET / returns Content-Security-Policy containing default-src 'self'.</fact>... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull reproducing UserCache null</title>   <subtitle>A parallel regression test verifies that 1,000 readers never observe a null cache value.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull runs IntStream.range(0, 1000).parallel() against cache.get("k").</fact>     <fa... [truncated]
```

### test-contract-openapi

```text
<observation>   <type>feature</type>   <title>Added OpenApiContractTest#generatedSpecMatchesCommittedFile schema drift guard</title>   <subtitle>Contract test compares springdoc output with the committed OpenAPI specification.</subtitle>   <facts>     <fact>OpenApiContractTest#generatedSpecMatchesCommittedFile compares generated springdoc output against docs/openapi.yaml.</fact>     <fact>OpenApiC... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Updated SchedulerTest clock to fixed time in SchedulerTest.java</title>   <subtitle>SchedulerTest replaces Thread.sleep with a deterministic fixed clock to eliminate timing-related test flakiness.</subtitle>   <facts>     <fact>SchedulerTest.java configures scheduler.clock with Clock.fixed at Instant.parse("2026-11-03T00:00:00Z") and ZoneOffset.UTC.</f... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed credential redaction for password, token and secret values in LogSanitizer.java</title>   <subtitle>LogSanitizer now masks sensitive query-style credential values before log output.</subtitle>   <facts>     <fact>src/main/java/com/acme/security/LogSanitizer.java replaces password, token and secret values using `value.replaceAll("(password|token|se... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed UserDao.findByEmail SQL injection in UserDao.java</title>   <subtitle>Parameterized queries now safely handle email values containing SQL quote characters.</subtitle>   <facts>     <fact>UserDao.findByEmail uses jdbcTemplate.query with the SQL placeholder ? for the email parameter.</fact>     <fact>UserDao.findByEmail no longer concatenates the em... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Restricted AdminController endpoints authorization to ROLE_ADMIN in AdminController</title>   <subtitle>Every AdminController endpoint now rejects non-admin users with HTTP 403.</subtitle>   <facts>     <fact>AdminController uses @PreAuthorize("hasRole('ADMIN')") for every endpoint.</fact>     <fact>AdminAuthorizationTest#userRoleGets403 passes with the... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Fixed CSRF token validation for state-changing requests in CsrfFilter.java</title>   <subtitle>CsrfFilter now rejects requests whose token does not match before forwarding them.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/CsrfFilter.java returns HTTP 403 when csrfToken does not match the request token.</fact>     <fact>CsrfFilterTest#postW... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Hardened api-key secret handling in src/main/resources/application.yml</title>   <subtitle>Application configuration now loads the API key from ACME_API_KEY instead of embedding a literal secret.</subtitle>   <facts>     <fact>src/main/resources/application.yml replaces the literal api-key value sk-live-123 with the environment expression ${ACME_API_KEY... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Fixed path traversal escape rejection in FileDownloadController</title>   <subtitle>FileDownloadController now rejects resolved names outside the base directory with InvalidPathException.</subtitle>   <facts>     <fact>FileDownloadController resolves and normalizes base.resolve(name) before checking resolved.startsWith(base).</fact>     <fact>FileDownlo... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter empty token bucket handling in RateLimiter.java</title>   <subtitle>RateLimiter now throws TooManyRequestsException when the token bucket cannot consume one request.</subtitle>   <facts>     <fact>RateLimiter.java calls bucket.tryConsume(1) and throws TooManyRequestsException when the result is false.</fact>     <fact>RateLimiter retur... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>Session cookies now disable script access, require HTTPS, and restrict cross-site requests.</subtitle>   <facts>     <fact>SessionCookieConfig sets cookie.setHttpOnly(true).</fact>     <fact>SessionCookieConfig sets cookie.setSecure(true) and cookie.setSameSite(Strict).</fact>     ... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml</title>   <subtitle>Hikari connection-pool sizing now reserves a minimum of five idle connections with a maximum of twenty.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20.</fact>     <fact>src/main/resource... [truncated]
```

### config-flyway-baseline

```text
<observation>   <type>change</type>   <title>Enabled Flyway baselineOnMigrate with baselineVersion 1 in pom.xml</title>   <subtitle>Flyway now baselines version 1 when migrating databases without recorded migration history.</subtitle>   <facts>     <fact>pom.xml sets flyway.baselineVersion to 1.</fact>     <fact>pom.xml sets flyway.baselineOnMigrate to true.</fact>     <fact>Maven build resolves t... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Changed root logger to INFO with JSON appender in logback-spring.xml</title>   <subtitle>Logback now routes root-level INFO messages through the JSON appender.</subtitle>   <facts>     <fact>src/main/resources/logback-spring.xml sets the root logger level to INFO.</fact>     <fact>src/main/resources/logback-spring.xml references the JSON appender from t... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Extended container healthcheck against /actuator/health in Dockerfile</title>   <subtitle>Docker image health now checks the Spring Boot actuator endpoint every 30 seconds.</subtitle>   <facts>     <fact>Dockerfile defines HEALTHCHECK --interval=30s.</fact>     <fact>Dockerfile healthcheck runs curl -f http://localhost:8080/actuator/health.</fact>     <... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 to pom.xml</title>   <subtitle>Maven verification now generates JaCoCo XML coverage output at target/site/jacoco/jacoco.xml.</subtitle>   <facts>     <fact>pom.xml adds org.jacoco:jacoco-maven-plugin with version 0.8.12.</fact>     <fact>mvn verify writes coverage output to target/site/jacoco/jacoco.xml.</fact>   </facts... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Documented local setup steps in README.md</title>   <subtitle>README now provides a three-step workflow for running the project locally.</subtitle>   <facts>     <fact>README.md setup requires JDK 21.</fact>     <fact>README.md starts PostgreSQL with docker compose up -d postgres.</fact>     <fact>README.md starts the application with mvn spring-boot:ru... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Documented queue recovery steps in docs/runbook.md</title>   <subtitle>Queue recovery now has a three-step runbook covering inspection, restart, and health verification.</subtitle>   <facts>     <fact>docs/runbook.md instructs operators to check pending_messages.</fact>     <fact>docs/runbook.md instructs operators to restart the worker.</fact>     <fac... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Documented 2.4.0 offline replay evaluation and V20 migration conflict fix in CHANGELOG.md</title>   <subtitle>CHANGELOG.md now records the new evaluation capability and migration fix.</subtitle>   <facts>     <fact>CHANGELOG.md adds version 2.4.0 dated 2026-11-03.</fact>     <fact>CHANGELOG.md lists "Added offline replay evaluation" under version 2.4.0.... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisites in docs/MIGRATION.md</title>   <subtitle>Migration guidance now requires backing up mem_sessions and enabling manual sentinel review.</subtitle>   <facts>     <fact>docs/MIGRATION.md requires backing up mem_sessions before running the V20 migration.</fact>     <fact>docs/MIGRATION.md requires app.manual_sentinel_re... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Documented vulnerability disclosure channel in SECURITY.md</title>   <subtitle>SECURITY.md directs vulnerability reports to security@acme.com and prohibits public issues.</subtitle>   <facts>     <fact>SECURITY.md names security@acme.com as the vulnerability reporting channel.</fact>     <fact>SECURITY.md forbids opening public issues for vulnerabilitie... [truncated]
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
<observation>   <type>change</type>   <title>Converted Dockerfile to multi-stage runtime image</title>   <subtitle>Dockerfile now builds with Maven and runs the jar on eclipse-temurin:21-jre, reducing image size from 780MB to 210MB.</subtitle>   <facts>     <fact>Dockerfile defines a runtime stage using FROM eclipse-temurin:21-jre.</fact>     <fact>Dockerfile copies /app/target/app.jar from the bu... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted IAM policy deploy role to AmazonS3ReadOnlyAccess in infra/main.tf</title>   <subtitle>The deploy role now receives read-only S3 permissions, and terraform validate succeeds.</subtitle>   <facts>     <fact>infra/main.tf defines aws_iam_role_policy.deploy with policy_arn = "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess".</fact>     <fact>terra... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added rollback.sh for deployment/claude-mem rollback</title>   <subtitle>Rollback script undoes the claude-mem deployment and waits for rollout completion.</subtitle>   <facts>     <fact>scripts/rollback.sh runs `kubectl rollout undo deployment/claude-mem`.</fact>     <fact>scripts/rollback.sh runs `kubectl rollout status deployment/claude-mem` after t... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Enabled canary rollout with 10 percent weight in infra/canary.yaml</title>   <subtitle>Canary traffic is set to 10 percent with rollback triggered above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true.</fact>     <fact>infra/canary.yaml sets canary.weight to 10 for a 10 percent canary rollout.</fact> ... [truncated]
```

### remove-dead-code

```text
<observation>   <type>change</type>   <title>Removed OrderService.legacySync in OrderService.java</title>   <subtitle>The legacy warehouse synchronization method was removed from order processing.</subtitle>   <facts>     <fact>OrderService.java no longer defines the legacySync(Order order) method.</fact>     <fact>OrderService.legacySync previously invoked warehouse.push(order).</fact>     <fact>... [truncated]
```

### remove-stale-config

```text
<observation>   <type>change</type>   <title>Removed legacy-search.enabled setting in application.yml</title>   <subtitle>The search module now reads only from the search-v2 configuration.</subtitle>   <facts>     <fact>src/main/resources/application.yml no longer defines legacy-search.enabled.</fact>     <fact>The search module reads only search-v2 after removal of legacy-search.enabled.</fact>  ... [truncated]
```

### remove-deprecated-endpoint

```text
<observation>   <type>change</type>   <title>Removed legacy /v1/users endpoint in UserController.java</title>   <subtitle>UserController now exposes only the /v2/users route after legacy user listing was removed.</subtitle>   <facts>     <fact>UserController.listLegacy was removed from src/main/java/com/acme/users/UserController.java.</fact>     <fact>The @GetMapping("/v1/users") mapping was remov... [truncated]
```

### remove-feature-flag

```text
<observation>   <type>change</type>   <title>Removed FeatureFlags.LEGACY_SEARCH constant in FeatureFlags.java</title>   <subtitle>Feature flag callers now use SEARCH_V2 after removal of the legacy-search constant.</subtitle>   <facts>     <fact>FeatureFlags.LEGACY_SEARCH was removed from src/main/java/com/acme/flags/FeatureFlags.java.</fact>     <fact>Feature flag callers use SEARCH_V2 instead of ... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
