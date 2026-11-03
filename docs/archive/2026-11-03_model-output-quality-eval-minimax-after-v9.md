# Model output quality evaluation

- Date: 2026-11-03
- Phase: after-v9
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 87 unique; runs scored: 3; key repeat: 3; real calls: 261 (cap none); duration: 724640 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 95% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 20/0/0/67 |
| format errors | 4 |
| validation rejections | 0 |
| call failures | 0 |
| title exact-hit rate (legacy) | 52% |
| title normalized hit rate (legacy) | 56% |
| title action-class hit rate (A6) | 87% (63 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 73% |
| title subject hit rate (legacy, diagnostic) | 79% |
| title trace-visible subject hit rate (A6b) | 89% (53 scored, 10 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 68% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 76% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 57% |
| title identifier hit rate | 97% |
| type accuracy | 95% |
| facts keyword precision (legacy) / recall | 80% / 90% |
| facts informative precision | 82% |
| facts trace-support rate | 100% |
| concepts precision / recall | 83% / 83% |
| skip consistency across repeats | 99% (87 repeated samples) |
| latency p50 / p95 / max (ms) | 2585 / 5648 / 12541 |
| tokens p50 / p95 / max / mean per call | 3940 / 4048 / 4137 / 2934 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 4 | **FAIL** |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 82% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 87% | PASS |
| A6b | title trace-visible subject | >= 90% | 89% | **FAIL** |
| A6c | title identifiers | = 100% | 97% | **FAIL** |
| A6 | all three title dimensions together | all pass | 76% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 87 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 87 | 100% | 56% | 68% | 80% | 82% | 90% | 4 |
| 1 | 87 | 100% | 54% | 60% | 80% | 77% | 94% | 4 |
| 2 | 87 | 99% | 51% | 65% | 77% | 80% | 93% | 2 |

- average: skip 100%, title 53%, title contract 64%, facts P/R 79%/92%, facts actionable 80%
- worst run: skip 99%, title 51%, title contract 60%, facts P/R 77%/90%, facts actionable 77%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 12/12 | 6/12 |
| refactor | 10 | 9/10 | 8/10 | 7/10 |
| test-add | 9 | 8/9 | 8/9 | 7/9 |
| security-fix | 9 | 9/9 | 9/9 | 4/9 |
| config-change | 12 | 11/12 | 10/12 | 7/12 |
| docs-update | 8 | 7/8 | 7/8 | 2/8 |
| deploy-change | 7 | 7/7 | 6/7 | 2/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Run-to-run variance (repeated samples)

| metric | min | max | mean | std dev |
|---|---|---|---|---|
| type accuracy | 89% | 97% | 94% | 3% |
| title action class | 84% | 91% | 87% | 3% |
| title trace-visible subject | 85% | 89% | 87% | 1% |
| title identifiers | 95% | 97% | 96% | 1% |
| title trace contract | 71% | 77% | 75% | 2% |
| facts informative | 77% | 82% | 80% | 2% |
| skip accuracy | 99% | 100% | 100% | 1% |

## Inconsistent samples (type or action changed between runs)

13 of 87 repeated samples disagreed:

- bug-n-plus-one: type {bugfix, change}
- test-integration-order: type {unparsed, feature}; action {unparsed, create}
- test-flaky-clock: action {create, modify}
- security-redact-log: action {modify, secure}
- security-secret-config: type {bugfix, change}
- security-cookie-httponly: type {bugfix, security}
- config-maven-plugin: action {modify, create}
- docs-readme-setup: type {unparsed, change}; action {unparsed, document}
- deploy-k8s-probe: action {modify, create}
- deploy-rollback-script: type {feature, change}
- skip-file-listing: type {skip, unparsed}; action {skip, unparsed}
- remove-dead-code: type {refactor, change}
- remove-feature-flag: type {refactor, change}

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 2412 |
| bug-null-cache | bug-fix | observation | observation | pass | 50% | MISS (subject unreachable, id MISS, action hit) | miss | match | 100%/100% | 67% | 50%/50% | 2316 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 50%/50% | 3017 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 3036 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 3503 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 3141 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 50% | MISS (subject MISS, id hit, action hit) | miss | match | 67%/100% | 100% | 50%/50% | 3636 |
| bug-race-counter | bug-fix | observation | observation | pass | 50% | hit | miss | match | 100%/67% | 100% | 100%/100% | 2740 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 1922 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 3943 |
| bug-regex-redos | bug-fix | observation | observation | pass | 75% | hit | miss | match | 50%/50% | 100% | 100%/100% | 1980 |
| bug-file-encoding | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 4484 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 100% | 100%/100% | 4182 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/50% | 67% | 100%/100% | 2837 |
| refactor-config-loader | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 1990 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 3652 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 3087 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 3014 |
| refactor-sql-repository | refactor | observation | observation | pass | 67% | hit | miss | MISS | 100%/50% | 100% | 0%/0% | 1927 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 0%/0% | 50% | 100%/100% | 2668 |
| refactor-feature-flag | refactor | observation | malformed | FAIL | - | - | - | - | - | - | - | 12541 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 2085 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 1795 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 100% | 100%/100% | 2659 |
| test-integration-order | test-add | observation | malformed | FAIL | - | - | - | - | - | - | - | 7162 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 67% | 100%/100% | 2795 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 3183 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 2470 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 1789 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | MISS (subject MISS, id hit, action MISS) | miss | match | 100%/100% | 100% | 100%/100% | 2359 |
| security-redact-log | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 50%/50% | 2744 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 33%/50% | 33% | 50%/50% | 3135 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | hit | miss | match | 100%/50% | 50% | 100%/100% | 3451 |
| security-csrf | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 50%/50% | 100% | 100%/100% | 1860 |
| security-secret-config | security-fix | observation | observation | pass | 50% | MISS (subject hit, id hit, action MISS) | miss | match | 67%/100% | 100% | 100%/100% | 3114 |
| security-xxe | security-fix | observation | observation | pass | 67% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 100% | 100%/100% | 3401 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 67%/100% | 100% | 100%/100% | 2080 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS (subject hit, id hit, action MISS) | miss | match | 33%/100% | 100% | 50%/50% | 2591 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 50%/100% | 100% | 100%/100% | 2700 |
| config-hikari-pool | config-change | observation | observation | pass | 50% | MISS (subject hit, id MISS, action hit) | miss | match | 67%/100% | 67% | 100%/100% | 3765 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 2256 |
| config-log-level | config-change | observation | observation | pass | 100% | MISS (subject MISS, id hit, action hit) | hit | match | 33%/50% | 33% | 100%/100% | 2984 |
| config-cors | config-change | observation | malformed | FAIL | - | - | - | - | - | - | - | 9916 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 7993 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 2321 |
| config-docker-healthcheck | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 1753 |
| config-maven-plugin | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 2847 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 0% | 100%/100% | 4193 |
| docs-readme-setup | docs-update | observation | malformed | FAIL | - | - | - | - | - | - | - | 5085 |
| docs-runbook | docs-update | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 33% | 100%/100% | 1916 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 1878 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 3816 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 1849 |
| docs-security-policy | docs-update | observation | observation | pass | 50% | MISS (subject MISS, id hit, action hit) | miss | match | 100%/100% | 50% | 50%/50% | 1556 |
| docs-contributing | docs-update | observation | observation | pass | 33% | hit | miss | match | 100%/100% | 50% | 100%/100% | 4252 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 75% | hit | miss | match | 75%/100% | 50% | 100%/100% | 3516 |
| deploy-github-actions | deploy-change | observation | observation | pass | 50% | hit | miss | match | 67%/100% | 100% | 100%/100% | 2681 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 100%/100% | 2570 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 67% | 100%/100% | 3017 |
| deploy-helm-values | deploy-change | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 67%/100% | 100% | 100%/100% | 5648 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 0% | 50%/50% | 2087 |
| deploy-canary | deploy-change | observation | observation | pass | 50% | hit | miss | match | 67%/100% | 100% | 100%/100% | 1931 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1238 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2704 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1261 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 898 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1691 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 692 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 852 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 970 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1505 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1779 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1483 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2095 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2044 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2491 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1089 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1291 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2585 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1154 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 793 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3148 |
| remove-dependency | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1823 |
| remove-dead-code | refactor | observation | observation | pass | 50% | hit | miss | match | 50%/100% | 50% | 100%/100% | 3310 |
| remove-stale-config | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1711 |
| remove-deprecated-endpoint | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 2307 |
| remove-feature-flag | config-change | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/50% | 4342 |

## Problems

- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed concurrent null placeholder writes in UserCache.get')
- bug-null-cache: title subject [race] missing (got 'Fixed concurrent null placeholder writes in UserCache.get')
- bug-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-csv-crlf: title coverage 50% (expected keywords [fix, csv, line, endings])
- bug-csv-crlf: title subject [line, endings] missing (got 'Fixed CsvExporter multiline field row count with CRLF normalization')
- bug-csv-crlf: title trace-visible subject [line, endings] missing (got 'Fixed CsvExporter multiline field row count with CRLF normalization')
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: title coverage 50% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: title subject [race] missing (got 'Fixed lost counter increments in CounterService.increment')
- bug-race-counter: facts recall 67% (expected [incrementAndGet, AtomicLong, 1000], got [CounterService.increment uses counter.incrementAndGet() instead of counter.set(counter.get() + 1)., CounterServiceConcurrencyTest passes with 1000 threads producing a final count of 1000.])
- bug-n-plus-one: title coverage 75% (expected keywords [fix, n+1, orderrepository, entitygraph])
- bug-regex-redos: title coverage 75% (expected keywords [fix, redos, urlvalidator, regex])
- bug-regex-redos: title subject [redos] missing (got 'Fixed UrlValidator nested-quantifier regex performance')
- bug-regex-redos: facts recall 50% (expected [linear pattern, longInputFinishesUnder100ms], got [UrlValidator uses the pattern ^https?://[^\\s]+$ instead of a nested-quantifier regular expression., UrlValidatorTest#longInputFinishesUnder100ms passes after the UrlValidator regex replacement.])
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [src/main/java/com/acme/auth/AuthController.java returns tokenService.issue(authentication)., AuthController no longer builds JWTs inline and delegates token issuance to TokenService.issue., AuthControllerTest remained unchanged and green after the AuthController refactor.])
- refactor-retry-policy: facts recall 50% (expected [BackoffStrategy, 100/200/400], got [RetryPolicy.java returns backoff.nextDelay(attempt) instead of calculating the delay directly., RetryPolicyTest asserts the same 100/200/400 delay sequence after the delegation change.])
- refactor-sql-repository: title coverage 67% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title verb class 'refactor' missing (got 'Fixed UserRepository tenant filtering with parameterized JPQL')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [UserRepository replaces string-concatenated SQL with @Query("select u from User u where u.tenantId = :tenantId")., UserRepositoryTest returns the same tenant rows after the UserRepository query change.])
- refactor-sql-repository: concepts recall 0% (expected [what-changed, pattern], got [problem-solution, gotcha])
- refactor-event-bus: facts recall 0% (expected [listener map, event type], got [EventBus resolves listeners with listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event))., EventBus dispatch behavior remains covered for the same three events in EventBusTest.])
- refactor-feature-flag: format error: DataValidationException: No <observation> block found in XML
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles is a parameterized test using @CsvSource with attempts 1, 2, and 3., RetryPolicyTest#delayDoubles expects policy.delay(attempt) values of 100, 200, and 400 milliseconds., RetryPolicyTest#delayDoubles passes all 3 test cases.])
- test-integration-order: format error: DataValidationException: No <observation> block found in XML
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable uses @ParameterizedTest with values "a,b", "a\"b", and "a\nb"., CsvExporterTest#quotingIsStable covers CSV values containing commas, quotes, and newlines., All 3 CsvExporterTest#quotingIsStable parameterized cases pass.])
- test-security-headers: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-contract-openapi: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- test-flaky-clock: title action 'modify' missing: title leads 'create' (got 'Added fixed-clock SchedulerTest configuration for deterministic timing')
- test-flaky-clock: title verb class 'fix' missing (got 'Added fixed-clock SchedulerTest configuration for deterministic timing')
- test-flaky-clock: title subject [flaky, clock] missing (got 'Added fixed-clock SchedulerTest configuration for deterministic timing')
- test-flaky-clock: title trace-visible subject [flaky, clock] missing (got 'Added fixed-clock SchedulerTest configuration for deterministic timing')
- security-redact-log: title action 'secure' missing: title leads 'modify' (got 'Fixed LogSanitizer credential redaction for password, token and secret')
- security-redact-log: title verb class 'redact' missing (got 'Fixed LogSanitizer credential redaction for password, token and secret')
- security-redact-log: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-sql-injection: title action 'secure' missing: title leads 'modify' (got 'Fixed UserDao.findByEmail SQL injection in UserDao.java')
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail executes `select * from users where email = ?` with the email supplied through the mapper parameter., UserDao.findByEmail no longer concatenates the email value into the SQL statement., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the UserDao.java change.])
- security-sql-injection: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Restricted AdminController endpoints authorization to ROLE_ADMIN')
- security-authz-admin: facts recall 50% (expected [ROLE_ADMIN, 403], got [AdminController applies @PreAuthorize("hasRole('ADMIN')") to every endpoint., AdminAuthorizationTest#userRoleGets403 passes with ROLE_ADMIN authorization enforced.])
- security-csrf: title action 'secure' missing: title leads 'modify' (got 'Fixed CsrfFilter mismatched-token rejection in CsrfFilter.java')
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter.java sends response status 403 when csrfToken does not match request., CsrfFilterTest#postWithoutTokenIs403 passes after the CsrfFilter.java change.])
- security-secret-config: title coverage 50% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: title action 'secure' missing: title leads 'modify' (got 'Replaced literal api-key with ACME_API_KEY in application.yml')
- security-secret-config: title verb class 'remove' missing (got 'Replaced literal api-key with ACME_API_KEY in application.yml')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Replaced literal api-key with ACME_API_KEY in application.yml')
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-xxe: title action 'secure' missing: title leads 'modify' (got 'Fixed XXE by disabling DOCTYPE declarations in XmlImportService')
- security-xxe: title verb class 'harden' missing (got 'Fixed XXE by disabling DOCTYPE declarations in XmlImportService')
- security-path-traversal: title action 'secure' missing: title leads 'modify' (got 'Fixed FileDownloadController path traversal with normalized base validation')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title action 'secure' missing: title leads 'modify' (got 'Fixed RateLimiter empty bucket response with TooManyRequestsException')
- security-rate-limit: title verb class 'add' missing (got 'Fixed RateLimiter empty bucket response with TooManyRequestsException')
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- security-cookie-httponly: title trace-visible subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- config-hikari-pool: title lost identifiers [maximum-pool-size] (got 'Set Hikari pool bounds to 20 and 5 in application.yml')
- config-hikari-pool: title verb class 'change' missing (got 'Set Hikari pool bounds to 20 and 5 in application.yml')
- config-log-level: title subject [log, level] missing (got 'Changed root logging to INFO with JSON in logback-spring.xml')
- config-log-level: title trace-visible subject [level] missing (got 'Changed root logging to INFO with JSON in logback-spring.xml')
- config-log-level: facts recall 50% (expected [INFO, JSON appender], got [src/main/resources/logback-spring.xml sets the root logger level to INFO., src/main/resources/logback-spring.xml routes root logging to the appender named JSON., LogbackConfigTest parses src/main/resources/logback-spring.xml.])
- config-cors: format error: DataValidationException: No <observation> block found in XML
- config-docker-healthcheck: title coverage 75% (expected keywords [add, docker, healthcheck, actuator])
- config-docker-healthcheck: title verb class 'add' missing (got 'Extended Dockerfile health check for /actuator/health')
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-maven-plugin: title coverage 75% (expected keywords [add, jacoco, plugin, pom])
- config-maven-plugin: title verb class 'add' missing (got 'Configured jacoco-maven-plugin 0.8.12 in pom.xml')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-readme-setup: format error: DataValidationException: No <observation> block found in XML
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title verb class 'add' missing (got 'Documented queue recovery procedure in docs/runbook.md')
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title verb class 'change' missing (got 'Documented 2.4.0 release notes in CHANGELOG.md')
- docs-migration-guide: title coverage 75% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [what-changed, pattern])
- docs-security-policy: title coverage 50% (expected keywords [add, security, policy, disclosure])
- docs-security-policy: title verb class 'add' missing (got 'Documented vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: title subject [security, policy] missing (got 'Documented vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: title trace-visible subject [security, policy] missing (got 'Documented vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 33% (expected keywords [update, contributing, verify-local])
- docs-contributing: title verb class 'change' missing (got 'Documented local verification prerequisite in CONTRIBUTING.md')
- deploy-k8s-probe: title coverage 75% (expected keywords [add, readiness, probe, deployment])
- deploy-k8s-probe: title verb class 'add' missing (got 'Extended readinessProbe /api/health on port 37777 in deployment.yaml')
- deploy-github-actions: title coverage 50% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: title verb class 'add' missing (got 'Extended backend CI matrix to Java 21 and 25 in .github/workflows/ci.yml')
- deploy-terraform-iam: title coverage 75% (expected keywords [restrict, iam, terraform, s3])
- deploy-helm-values: title coverage 75% (expected keywords [change, helm, replicas, memory])
- deploy-helm-values: title subject [helm, values] missing (got 'Changed replicaCount to 3 and memory limit to 2Gi in infra/values.yaml')
- deploy-helm-values: title trace-visible subject [helm, values] missing (got 'Changed replicaCount to 3 and memory limit to 2Gi in infra/values.yaml')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-rollback-script: concepts recall 50% (expected [what-changed, pattern], got [pattern, trade-off])
- deploy-canary: title coverage 50% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: title verb class 'enable' missing (got 'Changed canary.weight to 10 and errorThreshold to 0.02 in infra/canary.yaml')
- remove-dead-code: title coverage 50% (expected keywords [remove, dead, code, orderservice])
- remove-dead-code: title subject [dead, code] missing (got 'Removed OrderService.legacySync from OrderService.java')
- remove-stale-config: title subject [stale, setting] missing (got 'Removed legacy-search.enabled from application.yml')
- remove-deprecated-endpoint: title coverage 75% (expected keywords [remove, deprecated, endpoint, usercontroller])
- remove-deprecated-endpoint: title subject [deprecated, endpoint] missing (got 'Removed legacy /v1/users endpoint from UserController')
- remove-feature-flag: type 'refactor' != expected 'change'
- remove-feature-flag: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset off-by-one in pagination</title>   <subtitle>OrderQuery now starts the first page at offset 0 instead of -1.</subtitle>   <facts>     <fact>OrderQuery.pageOffset changed from `page * size - 1` to `page * size` in `src/main/java/com/acme/orders/OrderQuery.java`.</fact>     <fact>OrderQuery.pageOffset returns 0 for the first pa... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed concurrent null placeholder writes in UserCache.get</title>   <subtitle>UserCache.get now uses computeIfAbsent so concurrent callers load and populate the cache atomically.</subtitle>   <facts>     <fact>UserCache.get changed from `return cache.get(id);` to `return cache.computeIfAbsent(id, loader::load);`.</fact>     <fact>UserCache.get prevents ... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai date</title>   <subtitle>Regression test pins the reported off-by-one-day bug around the Asia/Shanghai business date.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins 2026-11-01T23:30Z and expects the Asia/Shanghai business date 2026-11-02.</fact>     <fact... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed PaymentService missing orderId handling in PaymentService.java</title>   <subtitle>Missing orders now throw PaymentNotFoundException instead of returning null.</subtitle>   <facts>     <fact>PaymentService calls repository.findByOrderId(orderId).orElseThrow(() -&gt; new PaymentNotFoundException(orderId)).</fact>     <fact>PaymentServiceTest#missin... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration equality boundary in JwtVerifier.java</title>   <subtitle>JwtVerifier now rejects tokens expiring exactly at the current instant, and JwtVerifierTest#expiredAtExactBoundary passes.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/JwtVerifier.java now rejects expiration when claims.getExpiration().isBefore(now) or i... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsvExporter multiline field row count with CRLF normalization</title>   <subtitle>CsvExporter now normalizes embedded line endings before CSV quoting, preserving multiline row counts.</subtitle>   <facts>     <fact>src/main/java/com/acme/export/CsvExporter.java now executes writer.write(line.replaceAll("\\r?\\n", "\\r\\n")).</fact>     <fact>CsvEx... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed lost counter increments in CounterService.increment</title>   <subtitle>CounterService.increment now atomically increments its counter under concurrent access.</subtitle>   <facts>     <fact>CounterService.increment uses counter.incrementAndGet() instead of counter.set(counter.get() + 1).</fact>     <fact>CounterServiceConcurrencyTest passes with ... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>bugfix</type>   <title>Fixed N+1 order item loading in OrderRepository</title>   <subtitle>@EntityGraph fetches items with orders, reducing customer-order queries from 1 + N to 1.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId(String customerId) uses @EntityGraph(attributePaths = "items").</fact>     <fact>QueryCountTest reports 1 SQL statement instead of ... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator nested-quantifier regex performance</title>   <subtitle>UrlValidator now validates HTTP URLs with a linear pattern and completes long inputs under 100ms.</subtitle>   <facts>     <fact>UrlValidator uses the pattern ^https?://[^\\s]+$ instead of a nested-quantifier regular expression.</fact>     <fact>UrlValidatorTest#longInputFinishes... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored AuthController JWT issuance with TokenService.issue delegation</title>   <subtitle>AuthController now delegates JWT creation to TokenService instead of constructing tokens inline.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/AuthController.java returns tokenService.issue(authentication).</fact>     <fact>AuthController no long... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Refactored RetryPolicy delay calculation through BackoffStrategy</title>   <subtitle>RetryPolicy now delegates delay computation while preserving the tested 100/200/400 sequence.</subtitle>   <facts>     <fact>RetryPolicy.java returns backoff.nextDelay(attempt) instead of calculating the delay directly.</fact>     <fact>RetryPolicyTest asserts the sam... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Fixed UserRepository tenant filtering with parameterized JPQL</title>   <subtitle>Tenant lookup now binds tenantId through JPQL while preserving the same filtered rows.</subtitle>   <facts>     <fact>UserRepository replaces string-concatenated SQL with @Query("select u from User u where u.tenantId = :tenantId").</fact>     <fact>UserRepositoryTest retur... [truncated]
```

### refactor-event-bus

```text
<observation>   <type>refactor</type>   <title>Refactored EventBus dispatch with listener map by event type</title>   <subtitle>EventBus now dispatches events through listeners keyed by event type instead of an if/else chain.</subtitle>   <facts>     <fact>EventBus resolves listeners with listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event)).</fact>     <fact>EventBus dis... [truncated]
```

### refactor-feature-flag

```text
We need output observation only. Need assess change. Edit existing production file delegates evaluation to Flags.isEnabled. Outcome says test same matrix. Refactor likely. Need facts verifiable exact identifiers path and method? parameters only file path, new string. We know FeatureFlagService delegates evaluation to Flags.isEnabled. Test same on/off matrix. Could make one observation. Need title ... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai business date</title>   <subtitle>Regression test reproduces the UTC-versus-business-date bug at the Asia/Shanghai day boundary.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone checks Instant.parse("2026-11-01T23:30:00Z") with ZoneId.of("Asia/Shanghai").</fact>... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 reproducing locked-account status</title>   <subtitle>The new test confirms locked accounts incorrectly return 401 instead of the expected 423 status.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 mocks users.isLocked("bob") to return true.</fact>     <fact>LoginControllerTe... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized exponential backoff cases</title>   <subtitle>RetryPolicyTest verifies delay doubles from 100 ms to 400 ms across attempts 1 through 3.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles is a parameterized test using @CsvSource with attempts 1, 2, and 3.</fact>     <fact>RetryPolicyTest#delayDou... [truncated]
```

### test-integration-order

```text
We need produce observation exactly XML. Need infer new test file added. Need title contract 20-100. Type feature (new test). Title "Added OrderApiIT#createOrderReturns201 contract test in OrderApiIT.java" count okay 74. Include exact identifiers perhaps PostgreSQL 16, 201, Location. Facts 1-5. Narrative. Concepts exactly two: problem-solution? It adds test pinning perhaps create order returns 201... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable parameterized cases for CSV escaping</title>   <subtitle>CsvExporterTest now verifies stable quoting for commas, quotes, and newlines.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable uses @ParameterizedTest with values "a,b", "a\"b", and "a\nb".</fact>     <fact>CsvExporterTest#quotingIsStable covers... [truncated]
```

### test-security-headers

```text
<observation>   <type>feature</type>   <title>Added SecurityHeadersTest#responseHasCspAndNosniff security header assertions</title>   <subtitle>SecurityHeadersTest verifies CSP and nosniff headers on the root response.</subtitle>   <facts>     <fact>src/test/java/com/acme/web/SecurityHeadersTest.java contains SecurityHeadersTest#responseHasCspAndNosniff.</fact>     <fact>SecurityHeadersTest#respon... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull reproducing UserCache null</title>   <subtitle>Parallel-reader regression test reproduces intermittent null values from UserCache.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull launches 1000 parallel cache.get("k") calls.</fact>     <fact>CacheConcurrencyTest#c... [truncated]
```

### test-contract-openapi

```text
<observation>   <type>feature</type>   <title>Added OpenApiContractTest#generatedSpecMatchesCommittedFile schema drift check</title>   <subtitle>Contract test compares generated springdoc output with the committed OpenAPI specification.</subtitle>   <facts>     <fact>OpenApiContractTest#generatedSpecMatchesCommittedFile was added in src/test/java/com/acme/api/OpenApiContractTest.java.</fact>     <... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Added fixed-clock SchedulerTest configuration for deterministic timing</title>   <subtitle>SchedulerTest now replaces Thread.sleep with a fixed UTC clock and passes 20 consecutive runs.</subtitle>   <facts>     <fact>src/test/java/com/acme/scheduling/SchedulerTest.java sets scheduler.clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed LogSanitizer credential redaction for password, token and secret</title>   <subtitle>LogSanitizer now masks credential values before logs expose sensitive data.</subtitle>   <facts>     <fact>src/main/java/com/acme/security/LogSanitizer.java replaces password, token and secret values matching password, token or secret with masked values.</fact>   ... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed UserDao.findByEmail SQL injection in UserDao.java</title>   <subtitle>User lookup now uses a JDBC parameter placeholder instead of concatenating the email value.</subtitle>   <facts>     <fact>UserDao.findByEmail executes `select * from users where email = ?` with the email supplied through the mapper parameter.</fact>     <fact>UserDao.findByEmai... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Restricted AdminController endpoints authorization to ROLE_ADMIN</title>   <subtitle>AdminController now requires ROLE_ADMIN on every endpoint, and user access returns HTTP 403.</subtitle>   <facts>     <fact>AdminController applies @PreAuthorize("hasRole('ADMIN')") to every endpoint.</fact>     <fact>AdminAuthorizationTest#userRoleGets403 passes with R... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsrfFilter mismatched-token rejection in CsrfFilter.java</title>   <subtitle>State-changing requests now receive HTTP 403 when the CSRF token does not match the request.</subtitle>   <facts>     <fact>CsrfFilter.java sends response status 403 when csrfToken does not match request.</fact>     <fact>CsrfFilterTest#postWithoutTokenIs403 passes after ... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Replaced literal api-key with ACME_API_KEY in application.yml</title>   <subtitle>Application configuration now loads the API key from the environment instead of embedding a live secret.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets api-key to ${ACME_API_KEY} instead of sk-live-123.</fact>     <fact>src/main/resources/applicatio... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Fixed XXE by disabling DOCTYPE declarations in XmlImportService</title>   <subtitle>XmlImportService now rejects external entity payloads through its XML factory configuration.</subtitle>   <facts>     <fact>XmlImportService sets http://apache.org/xml/features/disallow-doctype-decl to true.</fact>     <fact>XxeTest#externalEntityIsRejected passes after ... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Fixed FileDownloadController path traversal with normalized base validation</title>   <subtitle>FileDownloadController rejects download names resolving outside the configured base directory.</subtitle>   <facts>     <fact>FileDownloadController resolves each download name against base, calls normalize(), and verifies resolved.startsWith(base).</fact>   ... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter empty bucket response with TooManyRequestsException</title>   <subtitle>RateLimiter now returns HTTP 429 when the token bucket cannot consume another request.</subtitle>   <facts>     <fact>RateLimiter throws TooManyRequestsException when bucket.tryConsume(1) returns false.</fact>     <fact>RateLimiterTest#eleventhRequestIs429 passes a... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>Session cookies now enforce HttpOnly, Secure, and SameSite=Strict protections.</subtitle>   <facts>     <fact>SessionCookieConfig sets cookie.setHttpOnly(true).</fact>     <fact>SessionCookieConfig sets cookie.setSecure(true).</fact>     <fact>SessionCookieConfig sets cookie.setSam... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Set Hikari pool bounds to 20 and 5 in application.yml</title>   <subtitle>Hikari maximum-pool-size is 20 and minimum-idle is 5, with configuration binding coverage.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20.</fact>     <fact>src/main/resources/application.yml sets spring.datas... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Changed root logging to INFO with JSON in logback-spring.xml</title>   <subtitle>Root logs now use INFO level and route through the JSON appender.</subtitle>   <facts>     <fact>src/main/resources/logback-spring.xml sets the root logger level to INFO.</fact>     <fact>src/main/resources/logback-spring.xml routes root logging to the appender named JSON.<... [truncated]
```

### config-cors

```text
We need produce observation XML only. Need infer modified files. Edit CorsConfig.java, and outcome says CorsConfigTest asserts rejected origin perhaps test was added/updated same turn though parameters only edit. Must fact verifiable from observed. Could mention CorsConfig.java restricts mapping. Type change likely security-ish CORS restriction. Title 20-100: "Restricted /api/** CORS origins and m... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Extended Dockerfile health check for /actuator/health</title>   <subtitle>Docker images now probe the actuator health endpoint every 30 seconds.</subtitle>   <facts>     <fact>Dockerfile defines HEALTHCHECK with --interval=30s for the container image.</fact>     <fact>Dockerfile health check runs curl -f http://localhost:8080/actuator/health and exits 1... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Configured jacoco-maven-plugin 0.8.12 in pom.xml</title>   <subtitle>Maven verification now generates the JaCoCo XML coverage report.</subtitle>   <facts>     <fact>pom.xml configures jacoco-maven-plugin with version 0.8.12.</fact>     <fact>mvn verify writes the JaCoCo XML report to target/site/jacoco/jacoco.xml.</fact>   </facts>   <narrative>The Mave... [truncated]
```

### docs-readme-setup

```text
We need produce observation. Need obey title. Docs edit: "Documented local setup procedure in README.md" length okay, concept pattern + what-changed because doc-only. Facts exact identifiers. files read? None explicitly? The edit file modified. files_read perhaps README.md? Since only edit output, no read. But template likely include empty tags? Need include files_read maybe file README.md? We kno... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Documented queue recovery procedure in docs/runbook.md</title>   <subtitle>Queue recovery instructions cover pending messages, worker restart, and health verification.</subtitle>   <facts>     <fact>docs/runbook.md lists pending_messages as the first queue recovery inspection step.</fact>     <fact>docs/runbook.md instructs operators to restart the work... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Documented 2.4.0 release notes in CHANGELOG.md</title>   <subtitle>Release notes cover offline replay evaluation and the V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md adds the release heading "## 2.4.0 - 2026-11-03".</fact>     <fact>CHANGELOG.md records "Added offline replay evaluation" for version 2.4.0.</fact>     <fact>CHAN... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisites in docs/MIGRATION.md</title>   <subtitle>Migration guidance now requires a mem_sessions backup and manual sentinel review before execution.</subtitle>   <facts>     <fact>docs/MIGRATION.md requires backing up mem_sessions before running the V20 migration.</fact>     <fact>docs/MIGRATION.md requires app.manual_senti... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Documented vulnerability disclosure channel in SECURITY.md</title>   <subtitle>SECURITY.md directs vulnerability reports to security@acme.com and prohibits public issues.</subtitle>   <facts>     <fact>SECURITY.md defines security@acme.com as the vulnerability reporting channel.</fact>     <fact>SECURITY.md forbids reporting vulnerabilities through publ... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Documented local verification prerequisite in CONTRIBUTING.md</title>   <subtitle>Contributors must run the local verification script before opening pull requests.</subtitle>   <facts>     <fact>CONTRIBUTING.md requires running ./scripts/verify-local.sh before opening a pull request.</fact>     <fact>CONTRIBUTING.md names four test layers.</fact>   </fa... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Extended readinessProbe /api/health on port 37777 in deployment.yaml</title>   <subtitle>Deployment readiness now checks the application health endpoint after a 15-second initial delay.</subtitle>   <facts>     <fact>deploy/deployment.yaml configures readinessProbe.httpGet.path as /api/health.</fact>     <fact>deploy/deployment.yaml configures readiness... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Extended backend CI matrix to Java 21 and 25 in .github/workflows/ci.yml</title>   <subtitle>Backend workflow jobs now run against JDK 21 and JDK 25.</subtitle>   <facts>     <fact>.github/workflows/ci.yml defines strategy.matrix.java with values 21 and 25.</fact>     <fact>.github/workflows/ci.yml runs the backend on JDK 21 and 25.</fact>     <fact>Wor... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted IAM policy deploy role to AmazonS3ReadOnlyAccess in infra/main.tf</title>   <subtitle>The deploy role now receives read-only S3 access instead of full S3 permissions.</subtitle>   <facts>     <fact>aws_iam_role_policy.deploy in infra/main.tf sets policy_arn to arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess.</fact>     <fact>The deploy role no... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Changed replicaCount to 3 and memory limit to 2Gi in infra/values.yaml</title>   <subtitle>Helm values now request three replicas with a 2Gi memory cap.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount to 3.</fact>     <fact>infra/values.yaml sets resources.limits.memory to 2Gi.</fact>     <fact>helm template renders 3 pods with the up... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added scripts/rollback.sh deployment rollback command</title>   <subtitle>Rollback script undoes the claude-mem deployment and waits for rollout completion.</subtitle>   <facts>     <fact>scripts/rollback.sh runs kubectl rollout undo deployment/claude-mem.</fact>     <fact>scripts/rollback.sh runs kubectl rollout status deployment/claude-mem.</fact>   ... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Changed canary.weight to 10 and errorThreshold to 0.02 in infra/canary.yaml</title>   <subtitle>Canary rollout enables a 10 percent release and rolls back when errors exceed 2 percent.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true.</fact>     <fact>infra/canary.yaml sets canary.weight to 10 for a 10 percent canary rollout.... [truncated]
```

### remove-dead-code

```text
<observation>   <type>refactor</type>   <title>Removed OrderService.legacySync from OrderService.java</title>   <subtitle>Deprecated warehouse synchronization behavior was removed from the order service.</subtitle>   <facts>     <fact>OrderService.legacySync was removed from src/main/java/com/acme/orders/OrderService.java.</fact>     <fact>No remaining references to legacySync exist after the remo... [truncated]
```

### remove-stale-config

```text
<observation>   <type>change</type>   <title>Removed legacy-search.enabled from application.yml</title>   <subtitle>The search module now reads only from search-v2.</subtitle>   <facts>     <fact>src/main/resources/application.yml no longer defines legacy-search.enabled.</fact>     <fact>The search module reads only search-v2 after removal of legacy-search.enabled.</fact>   </facts>   <narrative>T... [truncated]
```

### remove-deprecated-endpoint

```text
<observation>   <type>change</type>   <title>Removed legacy /v1/users endpoint from UserController</title>   <subtitle>UserController now exposes only the retained /v2/users route.</subtitle>   <facts>     <fact>UserController.listLegacy was removed from src/main/java/com/acme/users/UserController.java.</fact>     <fact>The @GetMapping("/v1/users") mapping was removed from UserController.</fact>  ... [truncated]
```

### remove-feature-flag

```text
<observation>   <type>refactor</type>   <title>Removed FeatureFlags.LEGACY_SEARCH constant in FeatureFlags.java</title>   <subtitle>Callers now use the SEARCH_V2 feature-flag identifier instead of the removed legacy-search constant.</subtitle>   <facts>     <fact>FeatureFlags.java removed the constant FeatureFlags.LEGACY_SEARCH with value "legacy-search".</fact>     <fact>Feature flag callers now ... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
