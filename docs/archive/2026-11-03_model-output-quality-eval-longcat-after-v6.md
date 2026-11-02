# Model output quality evaluation

- Date: 2026-11-03
- Phase: after
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 1007733 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 99% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 20/0/0/62 |
| format errors | 0 |
| validation rejections | 1 |
| call failures | 0 |
| title exact-hit rate (legacy) | 57% |
| title normalized hit rate (legacy) | 62% |
| title verb-class hit rate | 80% |
| title subject hit rate | 90% |
| title contract hit rate (verb + subject + all identifiers) | 72% |
| title identifier hit rate | 98% |
| type accuracy | 93% |
| facts keyword precision (legacy) / recall | 73% / 90% |
| facts informative precision | 79% |
| facts trace-support rate | 100% |
| concepts precision / recall | 82% / 82% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 8567 / 17169 / 35257 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 79% | PASS |
| A6 | title contract hit (verb + subject + identifiers) | >= 80% | 72% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 82 | 100% | 62% | 72% | 73% | 79% | 90% | 0 |
| 1 | 13 | 100% | 67% | 67% | 89% | 82% | 100% | 0 |
| 2 | 13 | 100% | 50% | 50% | 88% | 85% | 100% | 0 |

- average: skip 100%, title 60%, title contract 63%, facts P/R 83%/97%, facts actionable 82%
- worst run: skip 100%, title 50%, title contract 50%, facts P/R 73%/90%, facts actionable 79%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 11/12 | 11/12 | 9/12 |
| refactor | 9 | 9/9 | 7/9 | 7/9 |
| test-add | 9 | 9/9 | 9/9 | 8/9 |
| security-fix | 9 | 9/9 | 8/9 | 3/9 |
| config-change | 8 | 8/8 | 8/8 | 6/8 |
| docs-update | 8 | 8/8 | 8/8 | 1/8 |
| deploy-change | 7 | 7/7 | 6/7 | 4/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 10466 |
| bug-null-cache | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/50% | 6422 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 8291 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 19137 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 8421 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 16255 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 9084 |
| bug-race-counter | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 8036 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 50% | 100%/100% | 12812 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 11069 |
| bug-regex-redos | bug-fix | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 7852 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 9953 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 67% | 100%/100% | 9571 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 50% | 100%/100% | 12265 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | MISS | miss | MISS | 67%/100% | 67% | 50%/50% | 35257 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 10148 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 7383 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 8370 |
| refactor-sql-repository | refactor | observation | observation | pass | 67% | MISS | miss | MISS | 50%/50% | 50% | 0%/0% | 10346 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 100%/100% | 11520 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 50% | 100%/100% | 10200 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 14567 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 7802 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 100% | 100%/100% | 9100 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 75% | 100%/100% | 5822 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 25%/50% | 25% | 100%/100% | 12222 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 13451 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/50% | 13148 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 17109 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | MISS | miss | match | 100%/67% | 100% | 100%/100% | 15228 |
| security-redact-log | security-fix | observation | observation | pass | 67% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 15315 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 10273 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 15349 |
| security-csrf | security-fix | observation | observation | pass | 100% | hit | hit | match | 67%/50% | 100% | 50%/50% | 12485 |
| security-secret-config | security-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 11621 |
| security-xxe | security-fix | observation | observation | pass | 67% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 9557 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 100%/100% | 9963 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS | miss | match | 50%/50% | 100% | 50%/50% | 12379 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS | miss | MISS | 50%/100% | 50% | 0%/0% | 15316 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 8037 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 12895 |
| config-log-level | config-change | observation | observation | pass | 100% | MISS | hit | match | 67%/100% | 33% | 100%/100% | 17361 |
| config-cors | config-change | observation | observation | pass | 75% | hit | miss | match | 50%/100% | 100% | 100%/100% | 20024 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 6894 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 8017 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 9987 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 17169 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 8465 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 7230 |
| docs-runbook | docs-update | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 67% | 100%/100% | 6212 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 11392 |
| docs-architecture-adr | docs-update | observation | observation | pass | 75% | MISS | miss | match | 33%/100% | 100% | 100%/100% | 7311 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/50% | 100% | 50%/50% | 11096 |
| docs-security-policy | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/50% | 100% | 50%/50% | 8567 |
| docs-contributing | docs-update | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 50% | 100%/100% | 7103 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 10823 |
| deploy-github-actions | deploy-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 8388 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | hit | match | 25%/100% | 75% | 50%/50% | 12456 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 50% | 100%/100% | 12002 |
| deploy-helm-values | deploy-change | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 100% | 100%/100% | 7500 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 75% | hit | miss | MISS | 67%/100% | 33% | 100%/100% | 6630 |
| deploy-canary | deploy-change | observation | observation | pass | 50% | MISS | miss | match | 33%/50% | 100% | 100%/100% | 12840 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3790 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2969 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2962 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4165 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4200 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3126 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3462 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3448 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3679 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2848 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2199 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4322 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2855 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2792 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4084 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4285 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2448 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2499 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3512 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4114 |

## Problems

- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-n-plus-one: facts recall 50% (expected [EntityGraph, 1 SQL statement], got [src/main/java/com/acme/orders/OrderRepository.java now declares `@EntityGraph(attributePaths = "items") List<Order> findAllByCustomerId(String customerId);`., The eager fetch is scoped to the `items` association of the returned `List<Order>`.])
- bug-regex-redos: validation rejected the observation: title too long: 101 chars > 100
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed FileReaderFactory platform-default charset corruption with StandardCharsets.UTF_8')
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [AuthController.java now returns tokenService.issue(authentication) instead of building JWTs inline., AuthControllerTest remained unchanged and green after the refactor.])
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title verb class 'refactor' missing (got 'Hardened ConfigLoader map with toUnmodifiableMap and typed accessors in ConfigLoader.java')
- refactor-config-loader: type 'change' != expected 'refactor'
- refactor-config-loader: concepts recall 50% (expected [what-changed, pattern], got [what-changed, gotcha])
- refactor-retry-policy: facts recall 50% (expected [BackoffStrategy, 100/200/400], got [RetryPolicy.java now returns `backoff.nextDelay(attempt)` instead of computing the delay inline., RetryPolicyTest asserts the unchanged 100/200/400 millisecond delay sequence, confirming behavior is preserved.])
- refactor-sql-repository: title coverage 67% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title verb class 'refactor' missing (got 'Replaced string-concatenated SQL with JPQL @Query in UserRepository')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [UserRepository.java defines `@Query("select u from User u where u.tenantId = :tenantId")` to fetch users filtered by tenantId., UserRepositoryTest returns the same tenant rows after the change, confirming identical behavior.])
- refactor-sql-repository: concepts recall 0% (expected [what-changed, pattern], got [problem-solution, gotcha])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles uses @ParameterizedTest with @CsvSource("1,100","2,200","3,400"), The test asserts policy.delay(attempt) equals expected values 100, 200, and 400 milliseconds, All 3 parameterized test cases pass])
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable is a @ParameterizedTest driven by @ValueSource(strings = {"a,b","a\"b","a\nb"})., The three cases cover a value containing a comma, a value containing a double quote, and a value containing a newline., All 3 parameterized cases pass., Test is located at src/test/java/com/acme/export/CsvExporterTest.java.])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- test-flaky-clock: title verb class 'fix' missing (got 'Added deterministic Clock.fixed scheduler clock in SchedulerTest')
- test-flaky-clock: title subject [flaky, clock] missing (got 'Added deterministic Clock.fixed scheduler clock in SchedulerTest')
- test-flaky-clock: facts recall 67% (expected [Clock.fixed, Thread.sleep, 20 consecutive], got [SchedulerTest now assigns scheduler.clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC), SchedulerTest passes 20 consecutive runs after the Clock.fixed change])
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: title subject [credentials] missing (got 'Redacted password, token and secret values in LogSanitizer.java')
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail now calls jdbcTemplate.query with "select * from users where email = ?" and passes email as a bound parameter., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the change.])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Restricted AdminController endpoints authorization to ROLE_ADMIN')
- security-authz-admin: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter now invokes response.sendError(403) and returns early when csrfToken.matches(request) evaluates false., State-changing requests carrying a mismatched CSRF token receive HTTP status 403 instead of being processed., CsrfFilterTest#postWithoutTokenIs403 passes after the edit to src/main/java/com/acme/web/CsrfFilter.java.])
- security-csrf: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-secret-config: title lost identifiers [application.yml, ACME_API_KEY] (got 'Redacted hardcoded API key in application.yml')
- security-secret-config: title verb class 'remove' missing (got 'Redacted hardcoded API key in application.yml')
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-xxe: title verb class 'harden' missing (got 'Fixed XXE vulnerability in XmlImportService via disallow-doctype-decl')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title verb class 'add' missing (got 'Fixed RateLimiter 429 enforcement on empty token bucket in RateLimiter')
- security-rate-limit: facts recall 50% (expected [429, token bucket], got [RateLimiter now throws TooManyRequestsException when bucket.tryConsume(1) returns false., RateLimiterTest#eleventhRequestIs429 passes, confirming the 11th request is rejected with 429.])
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- security-cookie-httponly: type 'change' != expected 'bugfix'
- security-cookie-httponly: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, trade-off])
- config-hikari-pool: title coverage 75% (expected keywords [change, hikari, pool, size])
- config-hikari-pool: title verb class 'change' missing (got 'Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml')
- config-log-level: title subject [log, level] missing (got 'Changed root logger level to INFO and appender to JSON in logback-spring.xml')
- config-cors: title coverage 75% (expected keywords [restrict, cors, corsconfig, origin])
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [pattern, what-changed])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Added three-step local setup instructions in README.md')
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title verb class 'add' missing (got 'Documented queue recovery procedure in docs/runbook.md')
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-architecture-adr: title coverage 75% (expected keywords [record, adr, extraction, validation])
- docs-architecture-adr: title subject [adr] missing (got 'Recorded extraction validation decision in 0007-extraction-validation.md')
- docs-migration-guide: title coverage 75% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: facts recall 50% (expected [mem_sessions, app.manual_sentinel_reviewed], got [docs/MIGRATION.md states the V20 migration must run with app.manual_sentinel_reviewed=true.])
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [what-changed, pattern])
- docs-security-policy: title coverage 75% (expected keywords [add, security, policy, disclosure])
- docs-security-policy: facts recall 50% (expected [security@acme.com, public issues], got [SECURITY.md explicitly forbids opening public issues for vulnerabilities])
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 67% (expected keywords [update, contributing, verify-local])
- docs-contributing: title verb class 'change' missing (got 'Documented ./scripts/verify-local.sh pre-PR requirement in CONTRIBUTING.md')
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-docker-multistage: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-terraform-iam: title coverage 75% (expected keywords [restrict, iam, terraform, s3])
- deploy-rollback-script: title coverage 75% (expected keywords [add, rollback, script, deployment])
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 50% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: title verb class 'enable' missing (got 'Changed canary weight to 10 and errorThreshold to 0.02 in infra/canary.yaml')
- deploy-canary: facts recall 50% (expected [10 percent, 2 percent], got [infra/canary.yaml sets canary.enabled to true, weight to 10, and errorThreshold to 0.02, canary.errorThreshold = 0.02 means rollback triggers once the canary error rate exceeds 2 percent, helm lint passes against the updated canary.yaml configuration])

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset off-by-one in pagination</title>   <subtitle>Removed erroneous -1 subtraction from pagination offset calculation in OrderQuery.pageOffset.</subtitle>   <facts>     <fact>OrderQuery.pageOffset now returns (page * size) instead of (page * size - 1)</fact>     <fact>OrderQueryTest#firstPageStartsAtZero passes with the corrected ... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache null placeholder race in UserCache.get with computeIfAbsent</title>   <subtitle>Replaced plain cache lookup with atomic computeIfAbsent so concurrent misses cannot insert duplicate placeholders.</subtitle>   <facts>     <fact>UserCache.get now returns cache.computeIfAbsent(id, loader::load) instead of cache.get(id).</fact>     <fact>The ... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for off-by-one-day timezone bug</title>   <subtitle>Regression test pins UTC timestamp and asserts correct Asia/Shanghai business date</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins input 2026-11-01T23:30Z</fact>     <fact>Test expects Asia/Shanghai business date 2026-11-0... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration boundary to reject tokens at the exact instant</title>   <subtitle>JwtVerifier now rejects tokens whose expiration equals the current instant, closing an off-by-one at the boundary.</subtitle>   <facts>     <fact>JwtVerifier previously used `claims.getExpiration().before(now)`, which returned false when expiration exactly eq... [truncated]
```

### bug-csv-crlf

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed CsvExporter embedded line endings normalization</title>   <subtitle>CsvExporter now converts all newline variants to CRLF before writing quoted CSV fields, preventing row count mismatches.</subtitle>   <facts>     <fact>CsvExporter.java line now uses replaceAll("\\r?\\n", "\\r\\n") to normalize embedded line endings to CRLF before quoting</... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment race condition with AtomicLong.incrementAndGet in CounterService.java</title>   <subtitle>Replaced non-atomic get/set increment with single atomic operation to prevent lost updates under concurrency.</subtitle>   <facts>     <fact>CounterService.increment now calls AtomicLong.incrementAndGet instead of counter.set(counter.... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderRepository.findAllByCustomerId N+1 queries with @EntityGraph in OrderRepository.java</title>   <subtitle>Added a fetch graph so order items load in the same query, eliminating 1 + N SQL statements.</subtitle>   <facts>     <fact>src/main/java/com/acme/orders/OrderRepository.java now declares `@EntityGraph(attributePaths = "items") List<Order>... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator catastrophic backtracking by replacing nested-quantifier regex with linear pattern</title>   <subtitle>Replaced the URL validation regex with a linear pattern to eliminate catastrophic backtracking on long inputs.</subtitle>   <facts>     <fact>UrlValidator now uses `Pattern.compile("^https?://[^\\s]+$")` as its URL validation pattern... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed FileReaderFactory platform-default charset corruption with StandardCharsets.UTF_8</title>   <subtitle>FileReaderFactory now forces UTF-8 decoding so accented text survives on GBK-default JVMs.</subtitle>   <facts>     <fact>FileReaderFactory.java constructs InputStreamReader with StandardCharsets.UTF_8 instead of relying on the platform default ch... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored AuthController JWT issuance with TokenService delegation</title>   <subtitle>AuthController now delegates token creation to TokenService.issue instead of building JWTs inline.</subtitle>   <facts>     <fact>AuthController.java now returns tokenService.issue(authentication) instead of building JWTs inline.</fact>     <fact>AuthControllerTest... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>change</type>   <title>Hardened ConfigLoader map with toUnmodifiableMap and typed accessors in ConfigLoader.java</title>   <subtitle>ConfigLoader now returns an unmodifiable properties map and exposes typed accessors; ConfigLoaderTest passes with unchanged values.</subtitle>   <facts>     <fact>src/main/java/com/acme/config/ConfigLoader.java returns its properties via toUnmod... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Refactored RetryPolicy delay calculation with BackoffStrategy delegation</title>   <subtitle>RetryPolicy now delegates next-delay computation to BackoffStrategy, preserving the existing 100/200/400 sequence.</subtitle>   <facts>     <fact>RetryPolicy.java now returns `backoff.nextDelay(attempt)` instead of computing the delay inline.</fact>     <fact>... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Replaced string-concatenated SQL with JPQL @Query in UserRepository</title>   <subtitle>UserRepository now uses a parameterized JPQL query to filter users by tenantId instead of concatenating SQL strings.</subtitle>   <facts>     <fact>UserRepository.java defines `@Query("select u from User u where u.tenantId = :tenantId")` to fetch users filtered by te... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone reproduction for UTC-vs-business-date bug</title>   <subtitle>A failing test pins the reported defect where 2026-11-01T23:30:00Z yields 2026-11-01 instead of 2026-11-02 in Asia/Shanghai.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone calls report.dateAt(at, ZoneId.of("Asia/Shan... [truncated]
```

### test-repro-login

```text
```xml <observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 reproducing wrong 401 status for locked accounts</title>   <subtitle>Test expects HTTP 423 for locked account login but controller currently returns 401, confirming reported defect.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 calls POST /login with users.isLocked("b... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized cases for exponential backoff</title>   <subtitle>Parameterized test verifies RetryPolicy delay doubles from 100ms to 400ms across attempts 1-3.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles uses @ParameterizedTest with @CsvSource("1,100","2,200","3,400")</fact>     <fact>The test asserts p... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable parameterized cases for CSV quoting</title>   <subtitle>Parameterized test verifies CSV quoting remains stable for values containing commas, quotes, and newlines.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable is a @ParameterizedTest driven by @ValueSource(strings = {"a,b","a\"b","a\nb"}).</fact>   ... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull reproducing UserCache null</title>   <subtitle>Stress test reproduces intermittent null from UserCache with 1000 parallel readers; fails before fix and passes after.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull calls cache.get("k") under IntStream.range(0, 100... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Added deterministic Clock.fixed scheduler clock in SchedulerTest</title>   <subtitle>Replaced Thread.sleep-based timing in SchedulerTest with an injected fixed Clock to eliminate flaky test runs.</subtitle>   <facts>     <fact>SchedulerTest now assigns scheduler.clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)</fact>     <fact... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Redacted password, token and secret values in LogSanitizer.java</title>   <subtitle>Log output now masks credential values for password, token and secret keys before writing.</subtitle>   <facts>     <fact>LogSanitizer.java applies value.replaceAll("(password|token|secret)=\\S+", "$1=***") to mask credential values.</fact>     <fact>The regex covers the... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed SQL injection in UserDao.findByEmail with parameter placeholder</title>   <subtitle>User-supplied email is now bound as a SQL parameter instead of concatenated into the query string.</subtitle>   <facts>     <fact>UserDao.findByEmail now calls jdbcTemplate.query with "select * from users where email = ?" and passes email as a bound parameter.</fac... [truncated]
```

### security-authz-admin

```text
```xml <observation>   <type>bugfix</type>   <title>Restricted AdminController endpoints authorization to ROLE_ADMIN</title>   <subtitle>Added class-level @PreAuthorize so every AdminController endpoint requires ROLE_ADMIN.</subtitle>   <facts>     <fact>`@PreAuthorize("hasRole('ADMIN')")` was added to `src/main/java/com/acme/admin/AdminController.java` at class level.</fact>     <fact>Every endpo... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsrfFilter missing CSRF token rejection in CsrfFilter.java</title>   <subtitle>Added enforcement so state-changing requests with a mismatched CSRF token are rejected with HTTP 403.</subtitle>   <facts>     <fact>CsrfFilter now invokes response.sendError(403) and returns early when csrfToken.matches(request) evaluates false.</fact>     <fact>State-... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Redacted hardcoded API key in application.yml</title>   <subtitle>Replaced literal secret sk-live-123 with environment variable reference ${ACME_API_KEY}.</subtitle>   <facts>     <fact>application.yml no longer contains literal sk-live-123; api-key now reads ${ACME_API_KEY} from environment</fact>     <fact>SecretScanTest passes after the change</fact>... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Fixed XXE vulnerability in XmlImportService via disallow-doctype-decl</title>   <subtitle>Added XML parser feature to reject DOCTYPE declarations and prevent external entity attacks.</subtitle>   <facts>     <fact>XmlImportService.java sets factory feature `http://apache.org/xml/features/disallow-doctype-decl` to `true`</fact>     <fact>Disabling DOCTYP... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter 429 enforcement on empty token bucket in RateLimiter</title>   <subtitle>RateLimiter now throws TooManyRequestsException when tryConsume returns false, making the eleventh request return 429.</subtitle>   <facts>     <fact>RateLimiter now throws TooManyRequestsException when bucket.tryConsume(1) returns false.</fact>     <fact>RateLimi... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>change</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>Session cookie now sets HttpOnly, Secure, and SameSite=Strict attributes.</subtitle>   <facts>     <fact>SessionCookieConfig marks the session cookie with setHttpOnly(true), setSecure(true), and setSameSite(Strict).</fact>     <fact>SessionCookieTest#flagsArePresent passes and veri... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml</title>   <subtitle>Database connection pool sizing configured via Spring datasource Hikari properties</subtitle>   <facts>     <fact>application.yml sets spring.datasource.hikari.maximum-pool-size to 20</fact>     <fact>application.yml sets spring.datasource.hikari.minimum-idle... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Changed root logger level to INFO and appender to JSON in logback-spring.xml</title>   <subtitle>Updated root logging configuration to use INFO level and JSON appender output.</subtitle>   <facts>     <fact>logback-spring.xml sets the root logger level attribute to "INFO".</fact>     <fact>logback-spring.xml root logger references the "JSON" appender vi... [truncated]
```

### config-cors

```text
<observation>   <type>change</type>   <title>Restricted CORS /api/** to https://app.acme.com in CorsConfig.java</title>   <subtitle>CORS policy for /api/** narrowed to a single origin with GET and POST methods only.</subtitle>   <facts>     <fact>CorsConfig restricts `/api/**` to origin `https://app.acme.com` with methods `GET` and `POST` only</fact>     <fact>CorsConfigTest asserts that a rejecte... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added HEALTHCHECK to Dockerfile polling /actuator/health every 30s</title>   <subtitle>Docker image now declares a container health check that curls the Spring Boot actuator health endpoint.</subtitle>   <facts>     <fact>Dockerfile adds HEALTHCHECK with --interval=30s invoking curl -f http://localhost:8080/actuator/health, exiting 1 on failure.</fact> ... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Added three-step local setup instructions in README.md</title>   <subtitle>README now documents JDK 21 install, docker compose postgres startup, and mvn spring-boot:run launch.</subtitle>   <facts>     <fact>README.md now contains a "## Setup" section with three numbered steps.</fact>     <fact>Step 1 instructs installing JDK 21.</fact>     <fact>Step 2... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Documented queue recovery procedure in docs/runbook.md</title>   <subtitle>New runbook captures the three-step manual queue recovery workflow for operators.</subtitle>   <facts>     <fact>docs/runbook.md now documents a queue runbook with three ordered steps: check pending_messages, restart worker, verify /api/health.</fact>     <fact>The runbook step "... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Changed CHANGELOG.md 2.4.0 entry with offline replay evaluation and V20 fix</title>   <subtitle>Release notes now record version 2.4.0 dated 2026-11-03 covering new offline replay evaluation and the V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md gains a new section headed "## 2.4.0 - 2026-11-03".</fact>     <fact>The 2.4.0 entry... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>decision</type>   <title>Recorded extraction validation decision in 0007-extraction-validation.md</title>   <subtitle>ADR 0007 documents validating extracted facts against tool output before persistence, including rejection path.</subtitle>   <facts>     <fact>New file docs/adr/0007-extraction-validation.md written containing ADR 0007</fact>     <fact>ADR 0007 decision: valid... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisites in docs/MIGRATION.md</title>   <subtitle>Migration guide now records the V20 prerequisites: back up mem_sessions and set app.manual_sentinel_reviewed=true.</subtitle>   <facts>     <fact>docs/MIGRATION.md adds a V20 section stating that mem_sessions must be backed up before running the migration.</fact>     <fact>d... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Added security policy in SECURITY.md</title>   <subtitle>New SECURITY.md documents vulnerability disclosure channel and prohibits public issue filing.</subtitle>   <facts>     <fact>SECURITY.md specifies vulnerability reports must be sent to security@acme.com</fact>     <fact>SECURITY.md explicitly forbids opening public issues for vulnerabilities</fact... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Documented ./scripts/verify-local.sh pre-PR requirement in CONTRIBUTING.md</title>   <subtitle>CONTRIBUTING.md now mandates running verify-local.sh before a pull request and enumerates the four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md now instructs contributors to run ./scripts/verify-local.sh before opening a pull request.</fact>    ... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added readinessProbe on /api/health:37777 in deployment.yaml</title>   <subtitle>Deployment manifest now declares an HTTP readiness probe with 15 second initial delay.</subtitle>   <facts>     <fact>deploy/deployment.yaml adds a readinessProbe using httpGet with path /api/health and port 37777.</fact>     <fact>readinessProbe initialDelaySeconds is set ... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Added JDK 21 and 25 matrix strategy in ci.yml</title>   <subtitle>CI workflow now executes the backend against both JDK 21 and JDK 25.</subtitle>   <facts>     <fact>.github/workflows/ci.yml defines strategy.matrix.java with values [21, 25]</fact>     <fact>The backend test job now runs once per JDK version in the matrix</fact>     <fact>Workflow syntax... [truncated]
```

### deploy-docker-multistage

```text
<observation>   <type>change</type>   <title>Converted Dockerfile to multi-stage build with eclipse-temurin:21-jre</title>   <subtitle>Split Dockerfile into a maven build stage and a slim JRE runtime stage, cutting image size from 780MB to 210MB.</subtitle>   <facts>     <fact>Dockerfile now defines a multi-stage build with a `build` stage running maven and a `runtime` stage.</fact>     <fact>The ... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted deploy role IAM policy to AmazonS3ReadOnlyAccess in infra/main.tf</title>   <subtitle>The deploy role no longer has full S3 access; it now attaches only the read-only managed policy.</subtitle>   <facts>     <fact>aws_iam_role_policy.deploy now attaches arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess instead of full S3 access.</fact>     <fact... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added rollback script scripts/rollback.sh</title>   <subtitle>New shell script undoes the claude-mem Kubernetes deployment and waits for rollout completion.</subtitle>   <facts>     <fact>scripts/rollback.sh executes `kubectl rollout undo deployment/claude-mem` followed by `kubectl rollout status deployment/claude-mem`.</fact>     <fact>The script star... [truncated]
```

### deploy-canary

```text
```xml <observation>   <type>change</type>   <title>Changed canary weight to 10 and errorThreshold to 0.02 in infra/canary.yaml</title>   <subtitle>Enabled a 10 percent canary rollout that automatically rolls back above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true, weight to 10, and errorThreshold to 0.02</fact>     <fact>canary.weight = 10 c... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared verb class, (2) the declared subject tokens to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. The legacy keyword-coverage metric is reported alongside for before/after comparison.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
