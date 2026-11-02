# Model output quality evaluation

- Date: 2026-11-03
- Phase: after
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 263576 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 100% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 20/0/0/62 |
| format errors | 0 |
| validation rejections | 0 |
| call failures | 0 |
| title exact-hit rate (legacy) | 45% |
| title normalized hit rate (legacy) | 47% |
| title verb-class hit rate | 69% |
| title subject hit rate | 74% |
| title contract hit rate (verb + subject + all identifiers) | 47% |
| title identifier hit rate | 92% |
| type accuracy | 92% |
| facts keyword precision (legacy) / recall | 79% / 92% |
| facts informative precision | 79% |
| facts trace-support rate | 100% |
| concepts precision / recall | 71% / 71% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 1636 / 3977 / 14384 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 79% | PASS |
| A6 | title contract hit (verb + subject + identifiers) | >= 80% | 47% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 82 | 100% | 47% | 47% | 79% | 79% | 92% | 0 |
| 1 | 13 | 100% | 50% | 50% | 91% | 85% | 97% | 0 |
| 2 | 13 | 100% | 50% | 50% | 87% | 87% | 100% | 0 |

- average: skip 100%, title 49%, title contract 49%, facts P/R 86%/96%, facts actionable 83%
- worst run: skip 100%, title 47%, title contract 47%, facts P/R 79%/92%, facts actionable 79%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 11/12 | 6/12 |
| refactor | 9 | 9/9 | 9/9 | 8/9 |
| test-add | 9 | 9/9 | 9/9 | 5/9 |
| security-fix | 9 | 9/9 | 7/9 | 3/9 |
| config-change | 8 | 8/8 | 7/8 | 3/8 |
| docs-update | 8 | 8/8 | 8/8 | 2/8 |
| deploy-change | 7 | 7/7 | 6/7 | 2/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 1678 |
| bug-null-cache | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 67% | 100%/100% | 1473 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 100% | 50%/50% | 7340 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 1461 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1816 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 1492 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 50%/50% | 1519 |
| bug-race-counter | bug-fix | observation | observation | pass | 50% | MISS | miss | match | 100%/67% | 100% | 50%/50% | 1385 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 50% | MISS | miss | MISS | 67%/100% | 67% | 50%/50% | 1748 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 3946 |
| bug-regex-redos | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 1383 |
| bug-file-encoding | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1322 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 67% | 100%/100% | 2231 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | MISS | hit | match | 33%/50% | 33% | 100%/100% | 1404 |
| refactor-config-loader | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 2417 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 2039 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 6335 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 0% | 50%/50% | 1429 |
| refactor-sql-repository | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 2444 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 33% | 100%/100% | 1691 |
| refactor-feature-flag | refactor | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 50% | 100%/100% | 1226 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 1659 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 1520 |
| test-unit-retry | test-add | observation | observation | pass | 75% | MISS | miss | match | 100%/50% | 100% | 100%/100% | 1922 |
| test-integration-order | test-add | observation | observation | pass | 50% | MISS | miss | match | 67%/100% | 100% | 50%/50% | 2947 |
| test-parameterized-csv | test-add | observation | observation | pass | 75% | MISS | miss | match | 100%/50% | 67% | 50%/50% | 2818 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 1763 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 2573 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 50%/50% | 1585 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 1695 |
| security-redact-log | security-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 67% | 100%/100% | 2804 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 2430 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | MISS | miss | match | 67%/50% | 100% | 100%/100% | 1736 |
| security-csrf | security-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 1720 |
| security-secret-config | security-fix | observation | observation | pass | 50% | MISS | miss | MISS | 50%/50% | 50% | 50%/50% | 1433 |
| security-xxe | security-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 1414 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 2073 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS | miss | match | 33%/100% | 100% | 50%/50% | 2545 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS | miss | MISS | 67%/100% | 100% | 50%/50% | 2282 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 67% | 100%/100% | 1788 |
| config-flyway-baseline | config-change | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 1710 |
| config-log-level | config-change | observation | observation | pass | 100% | MISS | hit | match | 67%/100% | 33% | 50%/50% | 1934 |
| config-cors | config-change | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 33% | 50%/50% | 1859 |
| config-timeout | config-change | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 50% | 50%/50% | 1401 |
| config-feature-flags | config-change | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 1478 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/50% | 2538 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 13387 |
| docs-api-observations | docs-update | observation | observation | pass | 75% | MISS | miss | match | 50%/100% | 100% | 50%/50% | 2525 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 33% | 50%/50% | 1617 |
| docs-runbook | docs-update | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 0% | 50%/50% | 1245 |
| docs-changelog | docs-update | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 2546 |
| docs-architecture-adr | docs-update | observation | observation | pass | 75% | MISS | miss | match | 50%/50% | 100% | 50%/50% | 2836 |
| docs-migration-guide | docs-update | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 2613 |
| docs-security-policy | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 0%/0% | 1412 |
| docs-contributing | docs-update | observation | observation | pass | 33% | hit | miss | match | 100%/100% | 50% | 50%/50% | 1982 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/50% | 1568 |
| deploy-github-actions | deploy-change | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 14384 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 50% | MISS | miss | match | 33%/100% | 67% | 100%/100% | 3977 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 67% | 100%/100% | 1636 |
| deploy-helm-values | deploy-change | observation | observation | pass | 50% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 2291 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | hit | MISS | 33%/50% | 33% | 50%/50% | 1545 |
| deploy-canary | deploy-change | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 1500 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 819 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 999 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1237 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1009 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 921 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 812 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 812 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1552 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1498 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 836 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1332 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 834 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 889 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 752 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3490 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2329 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1329 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 703 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 866 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 932 |

## Problems

- bug-offbyone-pagination: title coverage 67% (expected keywords [fix, pagination, orderquery])
- bug-offbyone-pagination: title subject [pagination] missing (got 'Fixed OrderQuery.pageOffset first-page zero offset')
- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed UserCache.get concurrent null placeholder race')
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-csv-crlf: facts recall 50% (expected [CRLF, multilineFieldKeepsRowCount], got [src/main/java/com/acme/export/CsvExporter.java normalizes embedded line endings with line.replaceAll("\\r?\\n", "\\r\\n") before quoting., CsvExporterTest#multilineFieldKeepsRowCount passes after the line-ending normalization change.])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: title coverage 50% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: title subject [race] missing (got 'Fixed CounterService.increment lost updates with incrementAndGet')
- bug-race-counter: facts recall 67% (expected [incrementAndGet, AtomicLong, 1000], got [CounterService.increment returns counter.incrementAndGet() instead of counter.set(counter.get() + 1)., CounterServiceConcurrencyTest passes with 1000 threads producing a counter value of 1000.])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-n-plus-one: title coverage 50% (expected keywords [fix, n+1, orderrepository, entitygraph])
- bug-n-plus-one: title verb class 'fix' missing (got 'Optimized OrderRepository.findAllByCustomerId with @EntityGraph items fetching')
- bug-n-plus-one: title subject [n+1] missing (got 'Optimized OrderRepository.findAllByCustomerId with @EntityGraph items fetching')
- bug-n-plus-one: type 'optimization' != expected 'bugfix'
- bug-n-plus-one: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-regex-redos: title coverage 75% (expected keywords [fix, redos, urlvalidator, regex])
- bug-regex-redos: title subject [redos] missing (got 'Fixed UrlValidator long input regex performance')
- refactor-auth-controller: title subject [delegation] missing (got 'Refactored AuthController JWT issuance into TokenService.issue')
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [src/main/java/com/acme/auth/AuthController.java now returns tokenService.issue(authentication)., AuthController no longer constructs JWTs inline., AuthControllerTest remained unchanged and was green.])
- refactor-retry-policy: concepts recall 50% (expected [what-changed, pattern], got [pattern, how-it-works])
- refactor-logging-filter: concepts recall 50% (expected [what-changed, pattern], got [pattern, how-it-works])
- refactor-feature-flag: title coverage 67% (expected keywords [refactor, featureflagservice, delegates])
- refactor-feature-flag: title subject [delegation] missing (got 'Refactored FeatureFlagService evaluation to Flags.isEnabled')
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-unit-retry: title coverage 75% (expected keywords [add, retrypolicy, parameterized, test])
- test-unit-retry: title subject [parameterized] missing (got 'Added RetryPolicyTest#delayDoubles for exponential retry delays')
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles covers attempts 1, 2, and 3 with expected delays of 100, 200, and 400 milliseconds., RetryPolicyTest#delayDoubles uses @ParameterizedTest with @CsvSource values "1,100", "2,200", and "3,400"., RetryPolicyTest#delayDoubles passed 3 test cases.])
- test-integration-order: title coverage 50% (expected keywords [add, integration, order, test])
- test-integration-order: title subject [integration, test] missing (got 'Added OrderApiIT#createOrderReturns201 for order creation')
- test-integration-order: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- test-parameterized-csv: title coverage 75% (expected keywords [add, csv, parameterized, test])
- test-parameterized-csv: title subject [parameterized] missing (got 'Added CsvExporterTest#quotingIsStable for CSV escaping edge cases')
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable uses @ParameterizedTest with the values "a,b", "a\"b", and "a\nb"., CsvExporterTest#quotingIsStable covers CSV quoting behavior for commas, quotes, and newlines., All 3 CsvExporterTest#quotingIsStable cases pass.])
- test-parameterized-csv: concepts recall 50% (expected [what-changed, pattern], got [what-changed, gotcha])
- test-security-headers: concepts recall 50% (expected [what-changed, pattern], got [what-changed, problem-solution])
- test-contract-openapi: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- test-flaky-clock: title verb class 'fix' missing (got 'Added fixed Clock to SchedulerTest to eliminate Thread.sleep flakiness')
- test-flaky-clock: title subject [flaky, clock] missing (got 'Added fixed Clock to SchedulerTest to eliminate Thread.sleep flakiness')
- test-flaky-clock: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: title verb class 'redact' missing (got 'Fixed LogSanitizer credential redaction for password, token and secret')
- security-redact-log: title subject [credentials] missing (got 'Fixed LogSanitizer credential redaction for password, token and secret')
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail executes `select * from users where email = ?` with the email value passed as a query parameter., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the UserDao.findByEmail change.])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Fixed AdminController authorization for ROLE_ADMIN access')
- security-authz-admin: facts recall 50% (expected [ROLE_ADMIN, 403], got [src/main/java/com/acme/admin/AdminController.java uses @PreAuthorize("hasRole('ADMIN')") to require ROLE_ADMIN., AdminController enforces ROLE_ADMIN authorization on every endpoint., AdminAuthorizationTest#userRoleGets403 passes for requests with the user role.])
- security-secret-config: title lost identifiers [application.yml, ACME_API_KEY] (got 'Changed application.yml api-key to environment variable')
- security-secret-config: title verb class 'remove' missing (got 'Changed application.yml api-key to environment variable')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Changed application.yml api-key to environment variable')
- security-secret-config: type 'change' != expected 'bugfix'
- security-secret-config: facts recall 50% (expected [ACME_API_KEY, SecretScanTest], got [src/main/resources/application.yml configures api-key as ${ACME_API_KEY}., src/main/resources/application.yml no longer contains the literal value sk-live-123.])
- security-secret-config: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, problem-solution])
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-xxe: title verb class 'harden' missing (got 'Fixed XmlImportService XXE via disallow-doctype-decl')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title verb class 'add' missing (got 'Fixed RateLimiter empty token bucket response')
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened SessionCookieConfig session cookie flags')
- security-cookie-httponly: type 'change' != expected 'bugfix'
- security-cookie-httponly: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, gotcha])
- config-hikari-pool: title lost identifiers [maximum-pool-size] (got 'Changed Hikari pool sizing in application.yml')
- config-flyway-baseline: title coverage 50% (expected keywords [enable, flyway, baseline, migrate])
- config-flyway-baseline: title verb class 'enable' missing (got 'Changed Flyway baseline configuration in pom.xml')
- config-flyway-baseline: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, gotcha])
- config-log-level: title subject [log, level] missing (got 'Changed logback-spring.xml root logger to INFO with JSON appender')
- config-log-level: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-cors: title coverage 50% (expected keywords [restrict, cors, corsconfig, origin])
- config-cors: title verb class 'restrict' missing (got 'Changed CorsConfig /api/** CORS allowlist and methods')
- config-cors: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, problem-solution])
- config-timeout: title coverage 50% (expected keywords [set, webclient, timeout, 5])
- config-timeout: title verb class 'set' missing (got 'Changed WebClientConfig response timeout to five seconds')
- config-timeout: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, gotcha])
- config-feature-flags: title coverage 50% (expected keywords [enable, checkout-v2, disable, legacy-search])
- config-feature-flags: title verb class 'enable' missing (got 'Changed feature-flags.yml checkout-v2 and legacy-search values')
- config-feature-flags: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-docker-healthcheck: type 'feature' != expected 'change'
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- docs-api-observations: title coverage 75% (expected keywords [document, observations, pagination, api])
- docs-api-observations: title verb class 'document' missing (got 'Changed /api/observations pagination contract in docs/API.md')
- docs-api-observations: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Added local setup instructions to README.md')
- docs-readme-setup: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-runbook: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title verb class 'change' missing (got 'Added CHANGELOG.md 2.4.0 release entry')
- docs-changelog: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- docs-architecture-adr: title lost identifiers [0007-extraction-validation.md] (got 'Recorded ADR 0007 extracted-fact persistence validation')
- docs-architecture-adr: facts recall 50% (expected [validate, before persistence], got [docs/adr/0007-extraction-validation.md records validation of extracted facts against tool output before persistence., docs/adr/0007-extraction-validation.md describes the rejection path for facts that fail validation.])
- docs-architecture-adr: concepts recall 50% (expected [why-it-exists, trade-off], got [why-it-exists, how-it-works])
- docs-migration-guide: title coverage 50% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: title verb class 'document' missing (got 'Changed V20 migration prerequisites in docs/MIGRATION.md')
- docs-security-policy: concepts recall 0% (expected [why-it-exists, pattern], got [what-changed, how-it-works])
- docs-contributing: title coverage 33% (expected keywords [update, contributing, verify-local])
- docs-contributing: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- deploy-github-actions: title coverage 75% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: title verb class 'add' missing (got 'Changed .github/workflows/ci.yml backend matrix to JDK 21 and 25')
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-docker-multistage: title coverage 50% (expected keywords [convert, dockerfile, multi-stage, jre])
- deploy-docker-multistage: title verb class 'convert' missing (got 'Changed Dockerfile runtime stage to eclipse-temurin:21-jre')
- deploy-docker-multistage: title subject [multi-stage] missing (got 'Changed Dockerfile runtime stage to eclipse-temurin:21-jre')
- deploy-terraform-iam: title lost identifiers [main.tf] (got 'Changed aws_iam_role_policy deploy S3 permissions to read-only')
- deploy-terraform-iam: title verb class 'restrict' missing (got 'Changed aws_iam_role_policy deploy S3 permissions to read-only')
- deploy-helm-values: title coverage 50% (expected keywords [change, helm, replicas, memory])
- deploy-helm-values: title subject [helm, values] missing (got 'Changed infra/values.yaml replicaCount and memory limit')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-rollback-script: facts recall 50% (expected [kubectl rollout undo, shellcheck], got [scripts/rollback.sh runs kubectl rollout undo deployment/claude-mem., scripts/rollback.sh runs kubectl rollout status deployment/claude-mem after rollback initiation., scripts/rollback.sh enables strict error handling with set -euo pipefail.])
- deploy-rollback-script: concepts recall 50% (expected [what-changed, pattern], got [how-it-works, pattern])
- deploy-canary: title coverage 75% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: title verb class 'enable' missing (got 'Changed infra/canary.yaml rollout weight and rollback threshold')

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset first-page zero offset</title>   <subtitle>OrderQuery.pageOffset now calculates page * size, and the first-page regression test passes.</subtitle>   <facts>     <fact>OrderQuery.pageOffset returns page * size instead of page * size - 1.</fact>     <fact>OrderQueryTest#firstPageStartsAtZero passes after the offset correction.<... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache.get concurrent null placeholder race</title>   <subtitle>UserCache.get now atomically loads missing entries through computeIfAbsent.</subtitle>   <facts>     <fact>UserCache.get changed from `cache.get(id)` to `cache.computeIfAbsent(id, loader::load)` in src/main/java/com/acme/cache/UserCache.java.</fact>     <fact>UserCache.get prevents... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai date</title>   <subtitle>Regression test pins the UTC-to-business-date boundary that previously produced an off-by-one-day result.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone was added to src/test/java/com/acme/reporting/DailyReportTest.java.</fact>     <fa... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed PaymentService missing orderId null return</title>   <subtitle>PaymentService now throws PaymentNotFoundException instead of returning null for an unknown orderId.</subtitle>   <facts>     <fact>PaymentService returns repository.findByOrderId(orderId).orElseThrow(() -> new PaymentNotFoundException(orderId)).</fact>     <fact>PaymentService now thr... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration equality boundary</title>   <subtitle>JwtVerifier now rejects tokens expiring exactly at the current instant.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/JwtVerifier.java rejects expiration when claims.getExpiration().isBefore(now) or claims.getExpiration().isEqual(now).</fact>     <fact>JwtVerifierTest#expire... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsvExporter embedded line endings in CsvExporterTest#multilineFieldKeepsRowCount</title>   <subtitle>Embedded line endings are normalized to CRLF before CSV quoting, preserving row counts for multiline fields.</subtitle>   <facts>     <fact>src/main/java/com/acme/export/CsvExporter.java normalizes embedded line endings with line.replaceAll("\\r?\\... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment lost updates with incrementAndGet</title>   <subtitle>AtomicLong.incrementAndGet prevents concurrent increments from overwriting each other.</subtitle>   <facts>     <fact>CounterService.increment returns counter.incrementAndGet() instead of counter.set(counter.get() + 1).</fact>     <fact>CounterServiceConcurrencyTest pas... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>optimization</type>   <title>Optimized OrderRepository.findAllByCustomerId with @EntityGraph items fetching</title>   <subtitle>Order items are fetched in the same query, reducing customer orders retrieval from 1 + N statements to 1.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId(String customerId) now uses @EntityGraph(attributePaths = "items").</fact>   ... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator long input regex performance</title>   <subtitle>UrlValidator now uses a linear URL pattern, and UrlValidatorTest#longInputFinishesUnder100ms passes.</subtitle>   <facts>     <fact>src/main/java/com/acme/validation/UrlValidator.java defines URL as Pattern.compile("^https?://[^\\s]+$").</fact>     <fact>UrlValidator replaces a nested-q... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored AuthController JWT issuance into TokenService.issue</title>   <subtitle>AuthController delegates token creation to TokenService without changing observed test behavior.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/AuthController.java now returns tokenService.issue(authentication).</fact>     <fact>AuthController no longer cons... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Refactored RetryPolicy delay calculation into BackoffStrategy</title>   <subtitle>RetryPolicy now delegates retry timing while preserving the expected 100/200/400 delay sequence.</subtitle>   <facts>     <fact>RetryPolicy.java calls backoff.nextDelay(attempt) for delay calculation.</fact>     <fact>RetryPolicyTest asserts the retry delay sequence 100/... [truncated]
```

### refactor-logging-filter

```text
<observation>   <type>refactor</type>   <title>Refactored LoggingFilter timing into logExchange</title>   <subtitle>LoggingFilter now delegates exchange timing and header capture to logExchange.</subtitle>   <facts>     <fact>LoggingFilter invokes logExchange(exchange, StopWatch.createStarted()) in src/main/java/com/acme/web/LoggingFilter.java.</fact>     <fact>LoggingFilter moves timing and heade... [truncated]
```

### refactor-feature-flag

```text
<observation>   <type>refactor</type>   <title>Refactored FeatureFlagService evaluation to Flags.isEnabled</title>   <subtitle>FeatureFlagService now delegates feature evaluation to Flags.isEnabled without changing its on/off behavior.</subtitle>   <facts>     <fact>FeatureFlagService returns flags.isEnabled(flag, context).</fact>     <fact>FeatureFlagServiceTest retains the same on/off matrix.</f... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai date boundary</title>   <subtitle>Regression test captures UTC-to-business-timezone date conversion failing across the Asia/Shanghai midnight boundary.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone calls report.dateAt with Instant.parse("2026-11-01T23:30:00Z"... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 for locked accounts</title>   <subtitle>Regression test reproduces the incorrect HTTP status returned for a locked account.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true.</fact>     <fact>LoginControllerTest#lockedAccountReturns423 ... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles for exponential retry delays</title>   <subtitle>Parameterized coverage verifies retry delays double from 100 ms through 400 ms.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles covers attempts 1, 2, and 3 with expected delays of 100, 200, and 400 milliseconds.</fact>     <fact>RetryPolicyTest#delayDoubles ... [truncated]
```

### test-integration-order

```text
<observation>   <type>feature</type>   <title>Added OrderApiIT#createOrderReturns201 for order creation</title>   <subtitle>Integration test verifies successful order creation, including the 201 status and Location header.</subtitle>   <facts>     <fact>src/test/java/com/acme/orders/OrderApiIT.java adds OrderApiIT#createOrderReturns201.</fact>     <fact>OrderApiIT#createOrderReturns201 posts a val... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable for CSV escaping edge cases</title>   <subtitle>Parameterized coverage verifies stable CSV quoting for commas, quotes, and newlines.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable uses @ParameterizedTest with the values "a,b", "a\"b", and "a\nb".</fact>     <fact>CsvExporterTest#quotingIsStable cove... [truncated]
```

### test-security-headers

```text
<observation>   <type>feature</type>   <title>Added SecurityHeadersTest#responseHasCspAndNosniff</title>   <subtitle>Security header tests verify CSP default-src and nosniff protections.</subtitle>   <facts>     <fact>SecurityHeadersTest#responseHasCspAndNosniff asserts the Content-Security-Policy response header contains default-src 'self'.</fact>     <fact>SecurityHeadersTest#responseHasCspAndNo... [truncated]
```

### test-contract-openapi

```text
<observation>   <type>feature</type>   <title>Added OpenApiContractTest#generatedSpecMatchesCommittedFile for schema drift</title>   <subtitle>The API contract test compares springdoc output with the committed OpenAPI document.</subtitle>   <facts>     <fact>OpenApiContractTest#generatedSpecMatchesCommittedFile diffs springdoc output against docs/openapi.yaml.</fact>     <fact>OpenApiContractTest#... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Added fixed Clock to SchedulerTest to eliminate Thread.sleep flakiness</title>   <subtitle>SchedulerTest now uses a deterministic UTC clock and passed 20 consecutive runs.</subtitle>   <facts>     <fact>src/test/java/com/acme/scheduling/SchedulerTest.java configures scheduler.clock with Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed LogSanitizer credential redaction for password, token and secret</title>   <subtitle>LogSanitizer now masks credential values before sensitive parameters reach logs.</subtitle>   <facts>     <fact>LogSanitizer replaces values matching password, token, or secret with *** using the key preserved before the equals sign.</fact>     <fact>LogSanitizerT... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed UserDao.findByEmail SQL injection via parameter placeholder</title>   <subtitle>UserDao.findByEmail now binds email values safely and returns no rows for quoted input.</subtitle>   <facts>     <fact>UserDao.findByEmail executes `select * from users where email = ?` with the email value passed as a query parameter.</fact>     <fact>SqlInjectionTest... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Fixed AdminController authorization for ROLE_ADMIN access</title>   <subtitle>AdminController now rejects requests lacking ROLE_ADMIN on every endpoint.</subtitle>   <facts>     <fact>src/main/java/com/acme/admin/AdminController.java uses @PreAuthorize("hasRole('ADMIN')") to require ROLE_ADMIN.</fact>     <fact>AdminController enforces ROLE_ADMIN author... [truncated]
```

### security-secret-config

```text
<observation>   <type>change</type>   <title>Changed application.yml api-key to environment variable</title>   <subtitle>application.yml now reads ACME_API_KEY from the environment instead of storing a literal API key.</subtitle>   <facts>     <fact>src/main/resources/application.yml configures api-key as ${ACME_API_KEY}.</fact>     <fact>src/main/resources/application.yml no longer contains the l... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Fixed XmlImportService XXE via disallow-doctype-decl</title>   <subtitle>XmlImportService now disables DOCTYPE declarations, and XxeTest#externalEntityIsRejected passes.</subtitle>   <facts>     <fact>src/main/java/com/acme/import/XmlImportService.java sets http://apache.org/xml/features/disallow-doctype-decl to true.</fact>     <fact>XxeTest#externalEn... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter empty token bucket response</title>   <subtitle>RateLimiter now throws TooManyRequestsException when the token bucket cannot consume another request.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/RateLimiter.java calls bucket.tryConsume(1) and throws TooManyRequestsException when consumption fails.</fact>     <fact>RateLimi... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>change</type>   <title>Hardened SessionCookieConfig session cookie flags</title>   <subtitle>Session cookies now use HttpOnly, Secure, and SameSite=Strict protections.</subtitle>   <facts>     <fact>SessionCookieConfig sets cookie.setHttpOnly(true).</fact>     <fact>SessionCookieConfig sets cookie.setSecure(true).</fact>     <fact>SessionCookieConfig sets cookie.setSameSite(S... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Changed Hikari pool sizing in application.yml</title>   <subtitle>Configured a maximum of 20 connections and five minimum idle connections for the datasource pool.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20.</fact>     <fact>src/main/resources/application.yml sets spring.dataso... [truncated]
```

### config-flyway-baseline

```text
<observation>   <type>change</type>   <title>Changed Flyway baseline configuration in pom.xml</title>   <subtitle>Flyway now baselines existing schemas at version 1 when migration is enabled.</subtitle>   <facts>     <fact>pom.xml sets flyway.baselineVersion to 1.</fact>     <fact>pom.xml sets flyway.baselineOnMigrate to true.</fact>   </facts>   <narrative>The Maven configuration enables Flyway b... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Changed logback-spring.xml root logger to INFO with JSON appender</title>   <subtitle>Production logging now emits root-level INFO events through the JSON appender.</subtitle>   <facts>     <fact>src/main/resources/logback-spring.xml sets the root logger level to INFO.</fact>     <fact>src/main/resources/logback-spring.xml routes root logger output to t... [truncated]
```

### config-cors

```text
<observation>   <type>change</type>   <title>Changed CorsConfig /api/** CORS allowlist and methods</title>   <subtitle>CorsConfig now permits only https://app.acme.com using GET and POST for /api/** endpoints.</subtitle>   <facts>     <fact>CorsConfig maps /api/** with allowedOrigins("https://app.acme.com").</fact>     <fact>CorsConfig permits only the GET and POST methods for /api/** requests.</f... [truncated]
```

### config-timeout

```text
<observation>   <type>change</type>   <title>Changed WebClientConfig response timeout to five seconds</title>   <subtitle>WebClientConfig now limits response waits, with WebClientConfigTest reading the configured timeout.</subtitle>   <facts>     <fact>WebClientConfig.java configures HttpClient.create().responseTimeout(Duration.ofSeconds(5)).</fact>     <fact>WebClientConfigTest reads the response... [truncated]
```

### config-feature-flags

```text
<observation>   <type>change</type>   <title>Changed feature-flags.yml checkout-v2 and legacy-search values</title>   <subtitle>Feature flag configuration now enables checkout-v2 and disables legacy-search for FlagBindingTest binding.</subtitle>   <facts>     <fact>src/main/resources/feature-flags.yml sets checkout-v2 to true.</fact>     <fact>src/main/resources/feature-flags.yml sets legacy-searc... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>feature</type>   <title>Added Dockerfile HEALTHCHECK for /actuator/health</title>   <subtitle>Docker images now check the actuator health endpoint every 30 seconds.</subtitle>   <facts>     <fact>Dockerfile defines HEALTHCHECK --interval=30s with curl -f http://localhost:8080/actuator/health.</fact>     <fact>The Dockerfile HEALTHCHECK returns exit code 1 when curl fails agai... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 coverage reporting to pom.xml</title>   <subtitle>Maven verification now generates JaCoCo coverage output at target/site/jacoco/jacoco.xml.</subtitle>   <facts>     <fact>pom.xml configures org.jacoco:jacoco-maven-plugin with version 0.8.12.</fact>     <fact>mvn verify writes coverage output to target/site/jacoco/jacoco.... [truncated]
```

### docs-api-observations

```text
<observation>   <type>change</type>   <title>Changed /api/observations pagination contract in docs/API.md</title>   <subtitle>API documentation now identifies the hasMore and items fields returned by GET /api/observations.</subtitle>   <facts>     <fact>docs/API.md documents that GET /api/observations returns hasMore and items.</fact>     <fact>docs/API.md states that the pagination contract match... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Added local setup instructions to README.md</title>   <subtitle>README.md now documents the three-step local development startup process.</subtitle>   <facts>     <fact>README.md requires JDK 21 for local setup.</fact>     <fact>README.md instructs developers to run docker compose up -d postgres for the PostgreSQL service.</fact>     <fact>README.md ins... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Added queue recovery steps to docs/runbook.md</title>   <subtitle>Queue recovery now documents inspecting pending_messages, restarting the worker, and verifying /api/health.</subtitle>   <facts>     <fact>docs/runbook.md documents checking pending_messages during queue recovery.</fact>     <fact>docs/runbook.md documents restarting the worker during que... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Added CHANGELOG.md 2.4.0 release entry</title>   <subtitle>Documented offline replay evaluation and the V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md adds a 2.4.0 release section dated 2026-11-03.</fact>     <fact>CHANGELOG.md records "Added offline replay evaluation" under version 2.4.0.</fact>     <fact>CHANGELOG.md records "... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>decision</type>   <title>Recorded ADR 0007 extracted-fact persistence validation</title>   <subtitle>ADR 0007 requires validating extracted facts against tool output before persistence and describes rejection handling.</subtitle>   <facts>     <fact>docs/adr/0007-extraction-validation.md records validation of extracted facts against tool output before persistence.</fact>     ... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Changed V20 migration prerequisites in docs/MIGRATION.md</title>   <subtitle>Migration documentation now requires a mem_sessions backup and manual sentinel review configuration.</subtitle>   <facts>     <fact>docs/MIGRATION.md V20 instructions require backing up mem_sessions before migration.</fact>     <fact>docs/MIGRATION.md V20 instructions require a... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Added SECURITY.md vulnerability disclosure policy</title>   <subtitle>SECURITY.md directs vulnerability reports to security@acme.com instead of public issues.</subtitle>   <facts>     <fact>SECURITY.md specifies security@acme.com as the vulnerability reporting channel.</fact>     <fact>SECURITY.md forbids opening public issues for vulnerabilities.</fact... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Changed CONTRIBUTING.md pull request verification requirement</title>   <subtitle>Contributor guidance now requires the local verification script and identifies four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md requires running ./scripts/verify-local.sh before opening a pull request.</fact>     <fact>CONTRIBUTING.md names four test layers... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added readinessProbe /api/health to deployment.yaml</title>   <subtitle>Deployment readiness now checks the health endpoint on port 37777 after a 15-second delay.</subtitle>   <facts>     <fact>deploy/deployment.yaml configures readinessProbe.httpGet.path as /api/health.</fact>     <fact>deploy/deployment.yaml configures readinessProbe.httpGet.port as 3... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Changed .github/workflows/ci.yml backend matrix to JDK 21 and 25</title>   <subtitle>The CI workflow now tests the backend against Java 21 and Java 25.</subtitle>   <facts>     <fact>.github/workflows/ci.yml sets strategy.matrix.java to [21, 25].</fact>     <fact>.github/workflows/ci.yml backend jobs run on JDK 21 and JDK 25.</fact>     <fact>The workfl... [truncated]
```

### deploy-docker-multistage

```text
<observation>   <type>change</type>   <title>Changed Dockerfile runtime stage to eclipse-temurin:21-jre</title>   <subtitle>Dockerfile now builds with Maven and runs the packaged application in a smaller Java 21 JRE image.</subtitle>   <facts>     <fact>Dockerfile defines a runtime stage using FROM eclipse-temurin:21-jre AS runtime.</fact>     <fact>Dockerfile copies /app/target/app.jar into the r... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Changed aws_iam_role_policy deploy S3 permissions to read-only</title>   <subtitle>The deploy IAM role now uses AmazonS3ReadOnlyAccess instead of full S3 access.</subtitle>   <facts>     <fact>infra/main.tf defines aws_iam_role_policy.deploy with policy_arn = "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess".</fact>     <fact>aws_iam_role_policy.deploy g... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Changed infra/values.yaml replicaCount and memory limit</title>   <subtitle>Helm deployment configuration now requests three pods with a 2Gi memory cap.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount to 3.</fact>     <fact>infra/values.yaml sets resources.limits.memory to 2Gi.</fact>     <fact>helm template renders 3 pods with the up... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added scripts/rollback.sh for deployment/claude-mem rollbacks</title>   <subtitle>Rollback script undoes the claude-mem deployment and waits for rollout completion.</subtitle>   <facts>     <fact>scripts/rollback.sh runs kubectl rollout undo deployment/claude-mem.</fact>     <fact>scripts/rollback.sh runs kubectl rollout status deployment/claude-mem af... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Changed infra/canary.yaml rollout weight and rollback threshold</title>   <subtitle>Canary traffic now reaches 10 percent with rollback above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true.</fact>     <fact>infra/canary.yaml sets canary.weight to 10 for a 10 percent canary rollout.</fact>     <fact>i... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared verb class, (2) the declared subject tokens to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. The legacy keyword-coverage metric is reported alongside for before/after comparison.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
