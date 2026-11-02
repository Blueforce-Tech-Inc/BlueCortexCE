# Model output quality evaluation

- Date: 2026-11-03
- Phase: current
- Provider: anthropic, model `claude-sonnet-4-5`, base URL `replay`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 277251 ms (wall clock; replay calls are 0); temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 84% |
| skip accuracy | 84% |
|   skip confusion (TP/FP/FN/TN) | 7/0/13/62 |
| format errors | 0 |
| validation rejections | 0 |
| call failures | 0 |
| title exact-hit rate (legacy) | 11% |
| title normalized hit rate | 0% |
| title identifier hit rate | 10% |
| type accuracy | 85% |
| facts precision / recall | 66% / 88% |
| facts trace-support rate | 92% |
| concepts precision / recall | 50% / 73% |
| skip consistency across repeats | 92% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 2163 / 3936 / 11920 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 84% | **FAIL** |
| A3 | must-record samples skipped | = 0 | 13 | **FAIL** |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts precision | >= 75% | 66% | **FAIL** |
| A6 | normalized title hit | >= 80% | 0% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | facts P | facts R | format errors |
|---|---|---|---|---|---|---|
| 0 | 82 | 84% | 0% | 66% | 88% | 0 |
| 1 | 13 | 77% | 0% | 79% | 93% | 0 |
| 2 | 13 | 85% | 0% | 73% | 90% | 0 |

- average: skip 82%, title 0%, facts P/R 73%/91%
- worst run: skip 77%, title 0%, facts P/R 66%/88%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 11/12 | 0/12 |
| refactor | 9 | 9/9 | 8/9 | 0/9 |
| test-add | 9 | 9/9 | 5/9 | 0/9 |
| security-fix | 9 | 9/9 | 9/9 | 0/9 |
| config-change | 8 | 8/8 | 6/8 | 0/8 |
| docs-update | 8 | 8/8 | 8/8 | 0/8 |
| deploy-change | 7 | 7/7 | 6/7 | 0/7 |
| skip-low-value | 20 | 7/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title exact | type | facts P/R | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 67% | miss | match | 100%/100% | 67%/100% | 3233 |
| bug-null-cache | bug-fix | observation | observation | pass | 25% | miss | match | 100%/100% | 33%/50% | 1915 |
| bug-timezone-report | bug-fix | observation | observation | pass | 67% | miss | MISS | 50%/100% | 67%/100% | 2492 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 50% | miss | match | 67%/100% | 67%/100% | 2215 |
| bug-connection-leak | bug-fix | observation | observation | pass | 50% | miss | match | 67%/100% | 33%/50% | 3936 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | miss | match | 100%/67% | 67%/100% | 2344 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 25% | miss | match | 67%/100% | 67%/100% | 2808 |
| bug-race-counter | bug-fix | observation | observation | pass | 75% | miss | match | 67%/67% | 25%/50% | 2082 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 25% | miss | match | 67%/100% | 50%/100% | 2309 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 33% | miss | match | 100%/100% | 50%/100% | 7990 |
| bug-regex-redos | bug-fix | observation | observation | pass | 25% | miss | match | 67%/100% | 67%/100% | 2072 |
| bug-file-encoding | bug-fix | observation | observation | pass | 50% | miss | match | 67%/100% | 67%/100% | 1996 |
| refactor-invoice | refactor | observation | observation | pass | 67% | miss | match | 50%/67% | 67%/100% | 2466 |
| refactor-auth-controller | refactor | observation | observation | pass | 33% | miss | match | 67%/100% | 67%/100% | 1655 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | miss | match | 33%/50% | 67%/100% | 1893 |
| refactor-order-mapper | refactor | observation | observation | pass | 0% | miss | match | 67%/100% | 67%/100% | 3205 |
| refactor-retry-policy | refactor | observation | observation | pass | 33% | miss | match | 33%/50% | 67%/100% | 2169 |
| refactor-logging-filter | refactor | observation | observation | pass | 0% | miss | match | 67%/100% | 33%/50% | 2683 |
| refactor-sql-repository | refactor | observation | observation | pass | 33% | miss | MISS | 67%/50% | 67%/100% | 1857 |
| refactor-event-bus | refactor | observation | observation | pass | 75% | miss | match | 67%/100% | 67%/100% | 2463 |
| refactor-feature-flag | refactor | observation | observation | pass | 33% | miss | match | 33%/50% | 67%/100% | 1964 |
| test-repro-timezone | test-add | observation | observation | pass | 67% | miss | MISS | 67%/67% | 33%/50% | 4292 |
| test-repro-login | test-add | observation | observation | pass | 75% | miss | MISS | 100%/100% | 33%/50% | 2607 |
| test-unit-retry | test-add | observation | observation | pass | 75% | miss | match | 67%/50% | 67%/100% | 2612 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | match | 50%/100% | 67%/100% | 2835 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | match | 67%/50% | 67%/100% | 1884 |
| test-security-headers | test-add | observation | observation | pass | 75% | miss | match | 75%/100% | 33%/50% | 2788 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | MISS | 100%/67% | 33%/50% | 2306 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | match | 33%/100% | 67%/100% | 1886 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | miss | MISS | 67%/67% | 67%/100% | 2067 |
| security-redact-log | security-fix | observation | observation | pass | 67% | miss | match | 100%/100% | 33%/50% | 2591 |
| security-sql-injection | security-fix | observation | observation | pass | 50% | miss | match | 67%/100% | 50%/100% | 3488 |
| security-authz-admin | security-fix | observation | observation | pass | 0% | miss | match | 67%/100% | 33%/50% | 2135 |
| security-csrf | security-fix | observation | observation | pass | 67% | miss | match | 33%/50% | 33%/50% | 3074 |
| security-secret-config | security-fix | observation | observation | pass | 100% | miss | match | 100%/100% | 33%/50% | 2161 |
| security-xxe | security-fix | observation | observation | pass | 33% | miss | match | 67%/100% | 33%/50% | 3503 |
| security-path-traversal | security-fix | observation | observation | pass | 25% | miss | match | 67%/100% | 67%/100% | 3918 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | miss | match | 0%/0% | 33%/50% | 11920 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | miss | match | 50%/100% | 33%/50% | 1994 |
| config-hikari-pool | config-change | observation | observation | pass | 50% | miss | match | 67%/100% | 33%/50% | 2926 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | match | 67%/100% | 33%/50% | 2001 |
| config-log-level | config-change | observation | observation | pass | 50% | miss | match | 67%/100% | 50%/50% | 2015 |
| config-cors | config-change | observation | observation | pass | 50% | miss | MISS | 50%/100% | 33%/50% | 6513 |
| config-timeout | config-change | observation | observation | pass | 50% | miss | match | 67%/100% | 33%/50% | 3374 |
| config-feature-flags | config-change | observation | observation | pass | 0% | miss | match | 67%/100% | 50%/50% | 2512 |
| config-docker-healthcheck | config-change | observation | observation | pass | 75% | miss | MISS | 100%/67% | 33%/50% | 2623 |
| config-maven-plugin | config-change | observation | observation | pass | 75% | miss | match | 100%/100% | 50%/50% | 2031 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | match | 33%/100% | 50%/50% | 3039 |
| docs-readme-setup | docs-update | observation | observation | pass | 25% | miss | match | 100%/100% | 50%/50% | 1835 |
| docs-runbook | docs-update | observation | observation | pass | 75% | miss | match | 67%/100% | 50%/50% | 2720 |
| docs-changelog | docs-update | observation | observation | pass | 33% | miss | match | 100%/100% | 50%/50% | 1675 |
| docs-architecture-adr | docs-update | observation | observation | pass | 0% | miss | match | 33%/100% | 33%/50% | 1767 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | miss | match | 67%/100% | 67%/100% | 1827 |
| docs-security-policy | docs-update | observation | observation | pass | 75% | miss | match | 67%/100% | 67%/100% | 3317 |
| docs-contributing | docs-update | observation | observation | pass | 0% | miss | match | 100%/100% | 33%/50% | 2163 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 75% | miss | match | 67%/100% | 33%/50% | 1954 |
| deploy-github-actions | deploy-change | observation | observation | pass | 75% | miss | match | 67%/100% | 33%/50% | 1744 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 25% | miss | match | 33%/100% | 67%/100% | 1904 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 75% | miss | match | 33%/50% | 67%/100% | 2629 |
| deploy-helm-values | deploy-change | observation | observation | pass | 50% | miss | match | 67%/100% | 50%/50% | 1840 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | MISS | 67%/100% | 33%/50% | 2344 |
| deploy-canary | deploy-change | observation | observation | pass | 25% | miss | match | 50%/100% | 33%/50% | 2051 |
| skip-read-for-question | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 1717 |
| skip-file-listing | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 1432 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | 774 |
| skip-view-logs | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 1420 |
| skip-multi-step-explore | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 2120 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | 815 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | 971 |
| skip-format-only | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 2227 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | 1020 |
| skip-read-docs | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 2395 |
| skip-grep-search | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 1794 |
| skip-open-file-to-explain | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 3206 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | 939 |
| skip-docker-ps | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 1732 |
| skip-empty-diff | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 3297 |
| skip-mvn-version | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 2584 |
| skip-cat-file | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 2037 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | 1324 |
| skip-read-config-to-answer | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 2283 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | 1561 |

## Problems

- bug-offbyone-pagination: title lost identifiers [OrderQuery] (got 'Fixed first-page pagination offset')
- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Prevented duplicate cache loads in UserCache')
- bug-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, how-it-works])
- bug-timezone-report: title lost identifiers [DailyReportTest, reportUsesBusinessTimezone] (got 'Daily report uses the configured business timezone')
- bug-timezone-report: type 'bugfix' != expected 'feature'
- bug-payment-missing-order: title lost identifiers [PaymentService] (got 'Missing payments now raise PaymentNotFoundException')
- bug-connection-leak: title coverage 50% (expected keywords [fix, connection, leak, connectionpool])
- bug-connection-leak: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, pattern])
- bug-jwt-expiry: title lost identifiers [JwtVerifier] (got 'JWT expiration boundary check now rejects exact-time expiry')
- bug-jwt-expiry: facts recall 67% (expected [expiration, isEqual, rejects], got [src/main/java/com/acme/auth/JwtVerifier.java now checks expiration with isBefore(now) or isEqual(now)., JWT tokens whose expiration equals the current instant are rejected., JwtVerifierTest#expiredAtExactBoundary passes after the expiration-boundary fix.])
- bug-csv-crlf: title lost identifiers [CsvExporter] (got 'CSV multiline fields now preserve row boundaries')
- bug-race-counter: title lost identifiers [CounterService] (got 'Fixed atomic counter increment race condition')
- bug-race-counter: facts recall 67% (expected [incrementAndGet, AtomicLong, 1000], got [src/main/java/com/acme/metrics/CounterService.java replaces counter.get()/counter.set() with counter.incrementAndGet()., AtomicLong.incrementAndGet performs each increment as a single atomic operation., CounterServiceConcurrencyTest passes with 1,000 threads producing a final count of 1,000.])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, what-changed, how-it-works, gotcha])
- bug-n-plus-one: title lost identifiers [OrderRepository] (got 'Eliminated N+1 query when loading customer orders')
- bug-dateformat-locale: title lost identifiers [DateFormatter] (got 'Locale-independent date formatting')
- bug-regex-redos: title coverage 25% (expected keywords [fix, redos, urlvalidator, regex])
- bug-file-encoding: title coverage 50% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- refactor-invoice: title coverage 67% (expected keywords [refactor, invoiceservice, delegates])
- refactor-invoice: facts recall 67% (expected [TaxCalculator, InvoiceRenderer, MailGateway], got [src/main/java/com/acme/billing/InvoiceService.java delegates tax calculation to TaxCalculator.calculate(invoice)., src/main/java/com/acme/billing/InvoiceService.java delegates invoice rendering to renderer.render(...)., InvoiceService retains responsibility for delegating mail delivery to MailGateway., InvoiceServiceTest remains green after the refactor.])
- refactor-auth-controller: title lost identifiers [AuthController] (got 'Centralized JWT issuance in TokenService')
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: facts recall 50% (expected [unmodifiable map, typed accessors], got [ConfigLoader.java now collects property entries into an unmodifiable map., ConfigLoader continues to expose typed configuration accessors., ConfigLoaderTest passes with unchanged expected values.])
- refactor-order-mapper: title lost identifiers [OrderMapper] (got 'Simplified order entity-to-DTO mapping')
- refactor-retry-policy: title lost identifiers [RetryPolicy] (got 'Delegated retry delay calculation to BackoffStrategy')
- refactor-retry-policy: facts recall 50% (expected [BackoffStrategy, 100/200/400], got [src/main/java/com/acme/resilience/RetryPolicy.java delegates delay computation using backoff.nextDelay(attempt)., RetryPolicyTest continues to assert retry delays of 100 milliseconds, 200 milliseconds, and 400 milliseconds., BackoffStrategy now owns retry delay calculation within the resilience project.])
- refactor-logging-filter: title lost identifiers [LoggingFilter] (got 'Centralized exchange logging and timing')
- refactor-logging-filter: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works, problem-solution])
- refactor-sql-repository: title lost identifiers [UserRepository] (got 'Replaced concatenated tenant SQL with JPQL')
- refactor-sql-repository: type 'change' != expected 'refactor'
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [src/main/java/com/acme/users/UserRepository.java defines a @Query statement selecting users by tenantId., The query binds tenantId through the named parameter :tenantId., UserRepositoryTest returns the same tenant-filtered rows after the repository change.])
- refactor-event-bus: title coverage 75% (expected keywords [refactor, eventbus, listener, map])
- refactor-feature-flag: title lost identifiers [FeatureFlagService] (got 'Feature flag evaluation delegated to Flags API')
- refactor-feature-flag: facts recall 50% (expected [Flags.isEnabled, on/off matrix], got [src/main/java/com/acme/flags/FeatureFlagService.java delegates evaluation through `flags.isEnabled(flag, context)`., FeatureFlagServiceTest retains the existing feature flag on/off behavior matrix., The refactor preserves externally observable evaluation behavior while centralizing logic in the Flags abstraction.])
- test-repro-timezone: title lost identifiers [DailyReportTest, reportUsesBusinessTimezone] (got 'Daily report ignores business timezone')
- test-repro-timezone: type 'discovery' != expected 'feature'
- test-repro-timezone: facts recall 67% (expected [Asia/Shanghai, 2026-11-02, 1 failure], got [src/test/java/com/acme/reporting/DailyReportTest.java was added with DailyReportTest#reportUsesBusinessTimezone., The regression test uses 2026-11-01T23:30:00Z and Asia/Shanghai to expect business date 2026-11-02., mvn -Dtest=DailyReportTest test fails because report.dateAt returns 2026-11-01 instead of 2026-11-02.])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha, how-it-works])
- test-repro-login: title lost identifiers [LoginControllerTest] (got 'Locked-account login test reproduces incorrect 401 response')
- test-repro-login: type 'discovery' != expected 'feature'
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha, pattern])
- test-unit-retry: title lost identifiers [RetryPolicyTest] (got 'Added parameterized retry delay tests')
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles uses @ParameterizedTest and @CsvSource for retry attempts one through three., RetryPolicyTest#delayDoubles expects policy.delay(attempt) to return 100, 200, and 400 milliseconds., RetryPolicyTest suite passes with three test cases.])
- test-integration-order: title lost identifiers [OrderApiIT] (got 'Added order creation API integration test')
- test-parameterized-csv: title lost identifiers [CsvExporterTest] (got 'Added parameterized CSV quoting stability test')
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [src/test/java/com/acme/export/CsvExporterTest.java contains a parameterized quotingIsStable test., quotingIsStable uses @ValueSource strings representing comma, quote, and newline content., All three CSV quoting stability cases pass.])
- test-security-headers: title lost identifiers [SecurityHeadersTest] (got 'Added security response header tests')
- test-security-headers: concepts recall 50% (expected [what-changed, pattern], got [what-changed, problem-solution, how-it-works])
- test-repro-null-cache: title lost identifiers [CacheConcurrencyTest, UserCache] (got 'Added concurrency regression test for non-null cache reads')
- test-repro-null-cache: type 'bugfix' != expected 'feature'
- test-repro-null-cache: facts recall 67% (expected [1000, null, parallel], got [src/test/java/com/acme/cache/CacheConcurrencyTest.java defines concurrentReadersNeverSeeNull., concurrentReadersNeverSeeNull starts 1,000 parallel readers that assert cache.get("k") is non-null., CacheConcurrencyTest#concurrentReadersNeverSeeNull fails before the UserCache fix and passes afterward.])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern, how-it-works])
- test-contract-openapi: title lost identifiers [OpenApiContractTest] (got 'Added OpenAPI contract drift test')
- test-flaky-clock: title lost identifiers [SchedulerTest] (got 'Scheduler tests use deterministic fixed clock')
- test-flaky-clock: type 'bugfix' != expected 'feature'
- test-flaky-clock: facts recall 67% (expected [Clock.fixed, Thread.sleep, 20 consecutive], got [SchedulerTest.java configures scheduler.clock with Clock.fixed using Instant.parse("2026-11-03T00:00:00Z")., SchedulerTest.java specifies ZoneOffset.UTC for the fixed test clock., Twenty consecutive SchedulerTest runs pass after replacing Thread.sleep.])
- security-redact-log: title lost identifiers [LogSanitizer] (got 'Redact credentials from sanitized log output')
- security-redact-log: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, pattern])
- security-sql-injection: title lost identifiers [UserDao] (got 'Parameterized user lookup prevents SQL injection')
- security-authz-admin: title lost identifiers [AdminController] (got 'Admin endpoints now require ROLE_ADMIN')
- security-authz-admin: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, how-it-works])
- security-csrf: title lost identifiers [CsrfFilter] (got 'CSRF filter now rejects mismatched request tokens')
- security-csrf: facts recall 50% (expected [403, mismatched token], got [src/main/java/com/acme/web/CsrfFilter.java compares the request CSRF token using csrfToken.matches(request)., CsrfFilter sends HTTP 403 and stops processing when the CSRF token does not match., CsrfFilterTest#postWithoutTokenIs403 passes after the validation change.])
- security-csrf: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, how-it-works, what-changed])
- security-secret-config: title lost identifiers [application.yml, ACME_API_KEY] (got 'Removed hard-coded API key from application configuration')
- security-secret-config: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, problem-solution, pattern])
- security-xxe: title lost identifiers [XmlImportService] (got 'Blocked XXE attacks by disabling XML DOCTYPE declarations')
- security-xxe: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, how-it-works])
- security-path-traversal: title lost identifiers [FileDownloadController] (got 'Blocked directory traversal in file downloads')
- security-rate-limit: title lost identifiers [RateLimiter] (got 'Rate limiter rejects requests after token-bucket exhaustion')
- security-rate-limit: facts recall 0% (expected [429, token bucket], got [src/main/java/com/acme/web/RateLimiter.java calls bucket.tryConsume(1) before admitting each request., RateLimiter throws TooManyRequestsException when bucket.tryConsume(1) returns false., RateLimiterTest#eleventhRequestIs429 passes after the change.])
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, how-it-works, what-changed])
- security-cookie-httponly: title lost identifiers [SessionCookieConfig] (got 'Hardened session cookie security flags')
- security-cookie-httponly: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, how-it-works])
- config-hikari-pool: title lost identifiers [maximum-pool-size] (got 'Configured Hikari connection pool sizing')
- config-hikari-pool: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, why-it-exists])
- config-flyway-baseline: title lost identifiers [pom.xml] (got 'Enabled Flyway baseline-on-migrate behavior')
- config-flyway-baseline: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, problem-solution])
- config-log-level: title lost identifiers [logback-spring.xml] (got 'Root logging configured for JSON output')
- config-log-level: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-cors: title lost identifiers [CorsConfig] (got 'Restricted API CORS policy')
- config-cors: type 'feature' != expected 'change'
- config-cors: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, problem-solution, gotcha])
- config-timeout: title lost identifiers [WebClientConfig] (got 'Configured WebClient response timeout')
- config-timeout: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, problem-solution, how-it-works])
- config-feature-flags: title lost identifiers [feature-flags.yml] (got 'Updated feature flag defaults')
- config-feature-flags: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-docker-healthcheck: title lost identifiers [Dockerfile] (got 'Added Docker container health check')
- config-docker-healthcheck: type 'feature' != expected 'change'
- config-docker-healthcheck: facts recall 67% (expected [HEALTHCHECK, 30s, /actuator/health], got [Dockerfile defines HEALTHCHECK with a 30-second interval., Health check sends an HTTP request to http://localhost:8080/actuator/health., Docker build succeeds after adding the health check.])
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, why-it-exists])
- config-maven-plugin: title lost identifiers [jacoco-maven-plugin, pom.xml] (got 'Added JaCoCo Maven plugin for coverage reports')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- docs-api-observations: title lost identifiers [API.md, /api/observations] (got 'Documented observations API pagination')
- docs-api-observations: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-readme-setup: title lost identifiers [README.md] (got 'Added local setup instructions')
- docs-readme-setup: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-runbook: title lost identifiers [runbook.md] (got 'Documented queue recovery runbook')
- docs-runbook: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-changelog: title lost identifiers [CHANGELOG.md] (got 'Documented version 2.4.0 release')
- docs-changelog: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- docs-architecture-adr: title lost identifiers [0007-extraction-validation.md] (got 'Validate extracted facts before persistence')
- docs-architecture-adr: concepts recall 50% (expected [why-it-exists, trade-off], got [why-it-exists, how-it-works, problem-solution])
- docs-migration-guide: title lost identifiers [MIGRATION.md] (got 'Documented V20 migration prerequisites')
- docs-security-policy: title lost identifiers [SECURITY.md] (got 'Added vulnerability disclosure policy')
- docs-contributing: title lost identifiers [CONTRIBUTING.md] (got 'Documented local verification requirement for pull requests')
- docs-contributing: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists, how-it-works])
- deploy-k8s-probe: title lost identifiers [deployment.yaml, /api/health] (got 'Added Kubernetes readiness probe')
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, why-it-exists])
- deploy-github-actions: title lost identifiers [ci.yml] (got 'Expanded CI backend matrix to JDK 21 and 25')
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern, why-it-exists])
- deploy-docker-multistage: title lost identifiers [Dockerfile, eclipse-temurin:21-jre] (got 'Docker runtime image reduced to Eclipse Temurin JRE')
- deploy-terraform-iam: title lost identifiers [main.tf] (got 'Restricted deploy IAM role to read-only S3 access')
- deploy-terraform-iam: facts recall 50% (expected [AmazonS3ReadOnlyAccess, terraform validate], got [infra/main.tf defines resource aws_iam_role_policy.deploy., The deploy role policy uses arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess., Terraform validation succeeds after the IAM policy change.])
- deploy-helm-values: title lost identifiers [values.yaml] (got 'Helm deployment scaled to three replicas')
- deploy-helm-values: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- deploy-rollback-script: title lost identifiers [rollback.sh] (got 'Added deployment rollback script')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-rollback-script: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works, problem-solution])
- deploy-canary: title lost identifiers [canary.yaml] (got 'Configured Helm canary rollout')
- deploy-canary: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, problem-solution])
- skip-read-for-question: expected <skip> but produced an observation
- skip-file-listing: expected <skip> but produced an observation
- skip-view-logs: expected <skip> but produced an observation
- skip-multi-step-explore: expected <skip> but produced an observation
- skip-format-only: expected <skip> but produced an observation
- skip-read-docs: expected <skip> but produced an observation
- skip-grep-search: expected <skip> but produced an observation
- skip-open-file-to-explain: expected <skip> but produced an observation
- skip-docker-ps: expected <skip> but produced an observation
- skip-empty-diff: expected <skip> but produced an observation
- skip-mvn-version: expected <skip> but produced an observation
- skip-cat-file: expected <skip> but produced an observation
- skip-read-config-to-answer: expected <skip> but produced an observation

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed first-page pagination offset</title>   <subtitle>OrderQuery.pageOffset now starts the first page at offset zero instead of minus one.</subtitle>   <facts>     <fact>src/main/java/com/acme/orders/OrderQuery.java changed pageOffset calculation from page * size - 1 to page * size.</fact>     <fact>OrderQuery.pageOffset now returns zero for the first ... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Prevented duplicate cache loads in UserCache</title>   <subtitle>UserCache.get now uses computeIfAbsent, eliminating concurrent null-placeholder writes.</subtitle>   <facts>     <fact>src/main/java/com/acme/cache/UserCache.java changed cache.get(id) to cache.computeIfAbsent(id, loader::load).</fact>     <fact>Concurrent callers can no longer both write ... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>bugfix</type>   <title>Daily report uses the configured business timezone</title>   <subtitle>Regression coverage verifies UTC timestamps roll into the correct Asia/Shanghai business date.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone was added in src/test/java/com/acme/reporting/DailyReportTest.java.</fact>     <fact>The regression test uses the t... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Missing payments now raise PaymentNotFoundException</title>   <subtitle>PaymentService returns an explicit exception instead of null when an order has no payment record.</subtitle>   <facts>     <fact>PaymentService calls repository.findByOrderId(orderId).</fact>     <fact>PaymentService throws PaymentNotFoundException when repository.findByOrderId(orde... [truncated]
```

### bug-connection-leak

```text
<observation>   <type>bugfix</type>   <title>ConnectionPool releases borrowed connections after caller failures</title>   <subtitle>try-with-resources now closes connections even when borrowing operations throw exceptions.</subtitle>   <facts>     <fact>src/main/java/com/acme/db/ConnectionPool.java uses try-with-resources in ConnectionPool.borrow.</fact>     <fact>Borrowed JDBC connections are clo... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>JWT expiration boundary check now rejects exact-time expiry</title>   <subtitle>JwtVerifier treats expiration equal to the current instant as expired, fixing the exact-boundary case.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/JwtVerifier.java now checks expiration with isBefore(now) or isEqual(now).</fact>     <fact>JWT tokens whose expi... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>CSV multiline fields now preserve row boundaries</title>   <subtitle>Embedded line endings are normalized to CRLF before CSV quoting, keeping multiline fields from corrupting row counts.</subtitle>   <facts>     <fact>CsvExporter.java now rewrites embedded line endings with replaceAll("\\r?\\n", "\r\n") before quoting.</fact>     <fact>CsvExporterTest#m... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed atomic counter increment race condition</title>   <subtitle>CounterService now increments atomically, preventing lost updates under concurrent access.</subtitle>   <facts>     <fact>src/main/java/com/acme/metrics/CounterService.java replaces counter.get()/counter.set() with counter.incrementAndGet().</fact>     <fact>AtomicLong.incrementAndGet per... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>bugfix</type>   <title>Eliminated N+1 query when loading customer orders</title>   <subtitle>OrderRepository now fetches order items eagerly, reducing customer-order loading from 1+N SQL statements to one.</subtitle>   <facts>     <fact>src/main/java/com/acme/orders/OrderRepository.java adds @EntityGraph(attributePaths = "items") to findAllByCustomerId(String customerId).</fa... [truncated]
```

### bug-dateformat-locale

```text
<observation>   <type>bugfix</type>   <title>Locale-independent date formatting</title>   <subtitle>DateFormatter now uses Locale.ROOT to prevent Turkish default locales from producing incorrect month names.</subtitle>   <facts>     <fact>src/main/java/com/acme/i18n/DateFormatter.java creates DateTimeFormatter.ofPattern(pattern, Locale.ROOT).</fact>     <fact>Locale.ROOT prevents Turkish locale-sp... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>UrlValidator regression removed with linear URL pattern</title>   <subtitle>UrlValidator now avoids nested-quantifier regex behavior, and long-input validation completes under 100ms.</subtitle>   <facts>     <fact>src/main/java/com/acme/validation/UrlValidator.java defines URL as Pattern.compile("^https?://[^\\s]+$").</fact>     <fact>UrlValidator now u... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>FileReaderFactory now reads files consistently as UTF-8</title>   <subtitle>Explicit UTF-8 decoding prevents accents from corrupting on JVMs using a GBK platform default.</subtitle>   <facts>     <fact>src/main/java/com/acme/files/FileReaderFactory.java wraps input streams with new InputStreamReader(in, StandardCharsets.UTF_8).</fact>     <fact>FileRead... [truncated]
```

### refactor-invoice

```text
<observation>   <type>refactor</type>   <title>InvoiceService delegates tax calculation and rendering</title>   <subtitle>InvoiceService now composes TaxCalculator and InvoiceRenderer without changing invoice behavior.</subtitle>   <facts>     <fact>src/main/java/com/acme/billing/InvoiceService.java delegates tax calculation to TaxCalculator.calculate(invoice).</fact>     <fact>src/main/java/com/a... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Centralized JWT issuance in TokenService</title>   <subtitle>AuthController now delegates token creation to TokenService instead of constructing JWTs inline.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/AuthController.java now returns tokenService.issue(authentication).</fact>     <fact>AuthController no longer builds JWT payloads inline... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>refactor</type>   <title>ConfigLoader now returns an unmodifiable property map</title>   <subtitle>ConfigLoader converts loaded properties into an unmodifiable map while retaining typed accessors.</subtitle>   <facts>     <fact>ConfigLoader.java now collects property entries into an unmodifiable map.</fact>     <fact>ConfigLoader continues to expose typed configuration access... [truncated]
```

### refactor-order-mapper

```text
<observation>   <type>refactor</type>   <title>Simplified order entity-to-DTO mapping</title>   <subtitle>OrderMapper now uses a single MapStruct convertValue call instead of 40 manually assigned DTO fields.</subtitle>   <facts>     <fact>src/main/java/com/acme/orders/OrderMapper.java maps entities to OrderDto through mapper.convertValue(entity, OrderDto.class).</fact>     <fact>The OrderMapper co... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Delegated retry delay calculation to BackoffStrategy</title>   <subtitle>RetryPolicy now obtains retry delays through BackoffStrategy while preserving the existing backoff sequence.</subtitle>   <facts>     <fact>src/main/java/com/acme/resilience/RetryPolicy.java delegates delay computation using backoff.nextDelay(attempt).</fact>     <fact>RetryPolic... [truncated]
```

### refactor-logging-filter

```text
<observation>   <type>refactor</type>   <title>Centralized exchange logging and timing</title>   <subtitle>LoggingFilter now delegates timing and header capture to logExchange while preserving the expected access-log output.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/LoggingFilter.java now starts the exchange stopwatch inside logExchange.</fact>     <fact>LoggingFilterTest verifies ... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>change</type>   <title>Replaced concatenated tenant SQL with JPQL</title>   <subtitle>UserRepository now filters users by tenantId through a named JPQL query.</subtitle>   <facts>     <fact>src/main/java/com/acme/users/UserRepository.java defines a @Query statement selecting users by tenantId.</fact>     <fact>The query binds tenantId through the named parameter :tenantId.</f... [truncated]
```

### refactor-event-bus

```text
<observation>   <type>refactor</type>   <title>EventBus dispatch switched to typed listener map</title>   <subtitle>EventBus now resolves listeners by event type and invokes each registered listener without an explicit branching chain.</subtitle>   <facts>     <fact>src/main/java/com/acme/events/EventBus.java stores listeners in a map keyed by event type.</fact>     <fact>EventBus dispatch uses li... [truncated]
```

### refactor-feature-flag

```text
<observation>   <type>refactor</type>   <title>Feature flag evaluation delegated to Flags API</title>   <subtitle>FeatureFlagService now delegates on/off evaluation to Flags.isEnabled while preserving existing test behavior.</subtitle>   <facts>     <fact>src/main/java/com/acme/flags/FeatureFlagService.java delegates evaluation through `flags.isEnabled(flag, context)`.</fact>     <fact>FeatureFlag... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>discovery</type>   <title>Daily report ignores business timezone</title>   <subtitle>A regression test confirms business dates are derived from UTC rather than the configured business timezone.</subtitle>   <facts>     <fact>src/test/java/com/acme/reporting/DailyReportTest.java was added with DailyReportTest#reportUsesBusinessTimezone.</fact>     <fact>The regression test use... [truncated]
```

### test-repro-login

```text
<observation>   <type>discovery</type>   <title>Locked-account login test reproduces incorrect 401 response</title>   <subtitle>A regression test confirms locked accounts incorrectly receive HTTP 401 instead of HTTP 423.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 mocks users.isLocked("bob") to return true.</fact>     <fact>The POST /login request for the locked acco... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added parameterized retry delay tests</title>   <subtitle>RetryPolicyTest verifies exponential delays of 100, 200, and 400 milliseconds across attempts one through three.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles uses @ParameterizedTest and @CsvSource for retry attempts one through three.</fact>     <fact>RetryPolicyTest#delayDoubles ... [truncated]
```

### test-integration-order

```text
<observation>   <type>feature</type>   <title>Added order creation API integration test</title>   <subtitle>Integration coverage verifies successful order creation, HTTP 201, Location header, and PostgreSQL 16 through Testcontainers.</subtitle>   <facts>     <fact>src/test/java/com/acme/orders/OrderApiIT.java uses @SpringBootTest with RANDOM_PORT.</fact>     <fact>OrderApiIT#createOrderReturns201 ... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added parameterized CSV quoting stability test</title>   <subtitle>CsvExporterTest verifies stable quoting for commas, embedded quotes, and newlines.</subtitle>   <facts>     <fact>src/test/java/com/acme/export/CsvExporterTest.java contains a parameterized quotingIsStable test.</fact>     <fact>quotingIsStable uses @ValueSource strings representing com... [truncated]
```

### test-security-headers

```text
<observation>   <type>feature</type>   <title>Added security response header tests</title>   <subtitle>MockMvc tests verify Content Security Policy and nosniff protection headers on the root response.</subtitle>   <facts>     <fact>src/test/java/com/acme/web/SecurityHeadersTest.java contains responseHasCspAndNosniff.</fact>     <fact>responseHasCspAndNosniff verifies Content-Security-Policy contai... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>bugfix</type>   <title>Added concurrency regression test for non-null cache reads</title>   <subtitle>New parallel-reader test reproduces intermittent null values from UserCache before the fix.</subtitle>   <facts>     <fact>src/test/java/com/acme/cache/CacheConcurrencyTest.java defines concurrentReadersNeverSeeNull.</fact>     <fact>concurrentReadersNeverSeeNull starts 1,000... [truncated]
```

### test-contract-openapi

```text
<observation>   <type>feature</type>   <title>Added OpenAPI contract drift test</title>   <subtitle>A Java test now verifies generated Springdoc output matches the committed OpenAPI document.</subtitle>   <facts>     <fact>OpenApiContractTest.java was added under src/test/java/com/acme/api/.</fact>     <fact>OpenApiContractTest#generatedSpecMatchesCommittedFile compares generated Springdoc output ... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>bugfix</type>   <title>Scheduler tests use deterministic fixed clock</title>   <subtitle>SchedulerTest replaces thread sleeping with a fixed UTC clock, eliminating timing-related test flakiness.</subtitle>   <facts>     <fact>SchedulerTest.java configures scheduler.clock with Clock.fixed using Instant.parse("2026-11-03T00:00:00Z").</fact>     <fact>SchedulerTest.java specifie... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Redact credentials from sanitized log output</title>   <subtitle>LogSanitizer now masks password, token, and secret values while preserving their field names.</subtitle>   <facts>     <fact>src/main/java/com/acme/security/LogSanitizer.java replaces credential values matching password, token, or secret keys with ***.</fact>     <fact>LogSanitizerTest#red... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Parameterized user lookup prevents SQL injection</title>   <subtitle>UserDao.findByEmail now uses a bound placeholder, and quoted email input safely returns no result.</subtitle>   <facts>     <fact>src/main/java/com/acme/users/UserDao.java uses jdbcTemplate.query with a ? parameter placeholder for email filtering.</fact>     <fact>UserDao.findByEmail n... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Admin endpoints now require ROLE_ADMIN</title>   <subtitle>AdminController enforces ADMIN authorization across every endpoint, restoring the expected 403 response for non-admin users.</subtitle>   <facts>     <fact>src/main/java/com/acme/admin/AdminController.java applies @PreAuthorize("hasRole('ADMIN')") to the controller.</fact>     <fact>AdminAuthori... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>CSRF filter now rejects mismatched request tokens</title>   <subtitle>State-changing requests receive HTTP 403 when their CSRF token does not match the expected token.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/CsrfFilter.java compares the request CSRF token using csrfToken.matches(request).</fact>     <fact>CsrfFilter sends HTTP 403 and ... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Removed hard-coded API key from application configuration</title>   <subtitle>Application configuration now reads the ACME API key from the environment, and SecretScanTest passes.</subtitle>   <facts>     <fact>src/main/resources/application.yml replaces the literal api-key value with ${ACME_API_KEY}.</fact>     <fact>ACME_API_KEY is now supplied throug... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Blocked XXE attacks by disabling XML DOCTYPE declarations</title>   <subtitle>XmlImportService now rejects external entity payloads during XML import.</subtitle>   <facts>     <fact>src/main/java/com/acme/import/XmlImportService.java configures the XML factory with the disallow-doctype-decl feature enabled.</fact>     <fact>XML documents containing DOCT... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Blocked directory traversal in file downloads</title>   <subtitle>FileDownloadController now rejects resolved paths outside the configured base directory.</subtitle>   <facts>     <fact>FileDownloadController resolves the requested filename against the base directory and normalizes the result.</fact>     <fact>FileDownloadController throws InvalidPathEx... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Rate limiter rejects requests after token-bucket exhaustion</title>   <subtitle>RateLimiter now throws TooManyRequestsException when the token bucket cannot consume another request.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/RateLimiter.java calls bucket.tryConsume(1) before admitting each request.</fact>     <fact>RateLimiter throws TooM... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened session cookie security flags</title>   <subtitle>Session cookies now use HttpOnly, Secure, and SameSite=Strict protections.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/SessionCookieConfig.java sets cookie.setHttpOnly(true).</fact>     <fact>src/main/java/com/acme/auth/SessionCookieConfig.java sets cookie.setSecure(true).</fact> ... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Configured Hikari connection pool sizing</title>   <subtitle>Application configuration now limits the maximum pool size and maintains five idle connections.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20.</fact>     <fact>src/main/resources/application.yml sets spring.datasource.hi... [truncated]
```

### config-flyway-baseline

```text
<observation>   <type>change</type>   <title>Enabled Flyway baseline-on-migrate behavior</title>   <subtitle>Maven configuration baselines Flyway migrations at version 1 when migrating an existing database.</subtitle>   <facts>     <fact>pom.xml defines the property flyway.baselineVersion with value 1.</fact>     <fact>pom.xml defines the property flyway.baselineOnMigrate with value true.</fact>  ... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Root logging configured for JSON output</title>   <subtitle>Logback now sends root-level INFO logs through the JSON appender and validates the XML configuration.</subtitle>   <facts>     <fact>src/main/resources/logback-spring.xml configures the root logger at INFO level.</fact>     <fact>src/main/resources/logback-spring.xml references the JSON appende... [truncated]
```

### config-cors

```text
<observation>   <type>feature</type>   <title>Restricted API CORS policy</title>   <subtitle>CORS now permits only the production web origin for limited HTTP methods.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/CorsConfig.java registers a CORS mapping for /api/**.</fact>     <fact>The /api/** CORS policy allows only the https://app.acme.com origin.</fact>     <fact>The /api/** CORS p... [truncated]
```

### config-timeout

```text
<observation>   <type>change</type>   <title>Configured WebClient response timeout</title>   <subtitle>WebClient now terminates requests lacking a response within five seconds.</subtitle>   <facts>     <fact>WebClientConfig.java configures HttpClient.create().responseTimeout(Duration.ofSeconds(5)).</fact>     <fact>WebClientConfigTest.java reads the response timeout from the configured WebClient b... [truncated]
```

### config-feature-flags

```text
<observation>   <type>change</type>   <title>Updated feature flag defaults</title>   <subtitle>Feature flag configuration enables checkout-v2 and disables legacy-search, with binding coverage for both values.</subtitle>   <facts>     <fact>src/main/resources/feature-flags.yml sets checkout-v2 to true.</fact>     <fact>src/main/resources/feature-flags.yml sets legacy-search to false.</fact>     <fa... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>feature</type>   <title>Added Docker container health check</title>   <subtitle>Docker images now verify the Spring Boot actuator health endpoint every 30 seconds.</subtitle>   <facts>     <fact>Dockerfile defines HEALTHCHECK with a 30-second interval.</fact>     <fact>Health check sends an HTTP request to http://localhost:8080/actuator/health.</fact>     <fact>Docker build s... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Added JaCoCo Maven plugin for coverage reports</title>   <subtitle>JaCoCo 0.8.12 now generates an XML coverage report during Maven verification.</subtitle>   <facts>     <fact>pom.xml adds org.jacoco:jacoco-maven-plugin version 0.8.12.</fact>     <fact>mvn verify generates target/site/jacoco/jacoco.xml.</fact>   </facts>   <narrative>The Maven build was... [truncated]
```

### docs-api-observations

```text
<observation>   <type>change</type>   <title>Documented observations API pagination</title>   <subtitle>API documentation now describes the hasMore and items fields returned by GET /api/observations.</subtitle>   <facts>     <fact>docs/API.md documents GET /api/observations.</fact>     <fact>The GET /api/observations response includes hasMore and items fields.</fact>     <fact>The documented pagin... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Added local setup instructions</title>   <subtitle>README now documents the three steps required to run the project locally.</subtitle>   <facts>     <fact>README.md requires JDK 21 for local development.</fact>     <fact>PostgreSQL starts with the command `docker compose up -d postgres`.</fact>     <fact>The Spring Boot application starts with `mvn spr... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Documented queue recovery runbook</title>   <subtitle>Queue recovery procedures now cover pending-message inspection, worker restart, and health verification.</subtitle>   <facts>     <fact>docs/runbook.md contains a three-step queue recovery procedure.</fact>     <fact>The queue recovery procedure starts by checking pending_messages.</fact>     <fact>T... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Documented version 2.4.0 release</title>   <subtitle>CHANGELOG.md now records offline replay evaluation and the V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md contains a new 2.4.0 release section dated 2026-11-03.</fact>     <fact>Version 2.4.0 adds offline replay evaluation.</fact>     <fact>Version 2.4.0 fixes a V20 migration ... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>decision</type>   <title>Validate extracted facts before persistence</title>   <subtitle>ADR 0007 requires comparing extracted facts with tool output and rejecting invalid facts before storage.</subtitle>   <facts>     <fact>ADR 0007 documents the requirement to validate extracted facts against tool output before persistence.</fact>     <fact>ADR 0007 defines a rejection path... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisites</title>   <subtitle>MIGRATION.md now requires a mem_sessions backup and manual sentinel review configuration.</subtitle>   <facts>     <fact>docs/MIGRATION.md includes a V20 migration section.</fact>     <fact>The V20 migration requires backing up the mem_sessions data.</fact>     <fact>The V20 migration requires s... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Added vulnerability disclosure policy</title>   <subtitle>SECURITY.md directs vulnerability reports to a private email channel and discourages public issues.</subtitle>   <facts>     <fact>SECURITY.md was created at the repository root.</fact>     <fact>Security vulnerabilities must be reported to security@acme.com.</fact>     <fact>Public issues are ex... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Documented local verification requirement for pull requests</title>   <subtitle>CONTRIBUTING.md now requires the local verification script before opening pull requests.</subtitle>   <facts>     <fact>CONTRIBUTING.md requires contributors to run ./scripts/verify-local.sh before opening a pull request.</fact>     <fact>CONTRIBUTING.md identifies four test... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added Kubernetes readiness probe</title>   <subtitle>Deployment now probes the API health endpoint before becoming ready.</subtitle>   <facts>     <fact>deploy/deployment.yaml defines a readinessProbe using HTTP GET /api/health on port 37777.</fact>     <fact>The readinessProbe uses initialDelaySeconds: 15.</fact>     <fact>kubectl apply --dry-run=clien... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Expanded CI backend matrix to JDK 21 and 25</title>   <subtitle>GitHub Actions now validates the backend against both Java 21 and Java 25.</subtitle>   <facts>     <fact>.github/workflows/ci.yml defines a Java strategy matrix with versions 21 and 25.</fact>     <fact>The backend CI workflow runs against JDK 21 and JDK 25.</fact>     <fact>The updated ci... [truncated]
```

### deploy-docker-multistage

```text
<observation>   <type>change</type>   <title>Docker runtime image reduced to Eclipse Temurin JRE</title>   <subtitle>Multi-stage Dockerfile now builds with Maven and runs the application on a smaller JRE-based image.</subtitle>   <facts>     <fact>Dockerfile runtime stage uses eclipse-temurin:21-jre.</fact>     <fact>Dockerfile copies /app/target/app.jar from the Maven build stage into /app/app.ja... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted deploy IAM role to read-only S3 access</title>   <subtitle>Terraform now attaches AmazonS3ReadOnlyAccess to the deploy role instead of granting full S3 permissions.</subtitle>   <facts>     <fact>infra/main.tf defines resource aws_iam_role_policy.deploy.</fact>     <fact>The deploy role policy uses arn:aws:iam::aws:policy/AmazonS3ReadOnlyAcce... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Helm deployment scaled to three replicas</title>   <subtitle>Infrastructure Helm values now request three pods with a 2Gi memory limit.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount to 3.</fact>     <fact>infra/values.yaml caps workload memory usage at 2Gi.</fact>     <fact>helm template renders three workload pods with the updated ... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added deployment rollback script</title>   <subtitle>Rollback script undoes the claude-mem Kubernetes deployment and waits for rollout completion.</subtitle>   <facts>     <fact>scripts/rollback.sh runs kubectl rollout undo deployment/claude-mem.</fact>     <fact>scripts/rollback.sh waits for kubectl rollout status deployment/claude-mem.</fact>     <fa... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Configured Helm canary rollout</title>   <subtitle>Canary deployments now use 10 percent traffic and roll back when errors exceed 2 percent.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true.</fact>     <fact>infra/canary.yaml sets canary.weight to 10 for a 10 percent canary rollout.</fact>     <fact>infra/canary.yaml sets can... [truncated]
```

### skip-read-for-question

```text
<observation>   <type>discovery</type>   <title>OrderQuery builds paginated JPA Criteria queries</title>   <subtitle>OrderQuery translates page and size inputs into a JPA Criteria query.</subtitle>   <facts>     <fact>src/main/java/com/acme/orders/OrderQuery.java contains the order query builder.</fact>     <fact>OrderQuery uses JPA Criteria API constructs.</fact>     <fact>OrderQuery accepts page... [truncated]
```

### skip-file-listing

```text
<observation>   <type>discovery</type>   <title>Java source tree located</title>   <subtitle>The project contains 214 Java files under the main source directory.</subtitle>   <facts>     <fact>Repository root is /repo.</fact>     <fact>Java sources are located under src/main/java/**/*.java.</fact>     <fact>214 Java files were found and no edits were made.</fact>   </facts>   <narrative>The primar... [truncated]
```

### skip-view-logs

```text
<observation>   <type>discovery</type>   <title>Application starts successfully without recent errors</title>   <subtitle>ClaudeMemApplication initialized in 4.2 seconds, and the latest 100 log lines contain no errors.</subtitle>   <facts>     <fact>ClaudeMemApplication started successfully in logs/app.log.</fact>     <fact>Application startup completed in 4.2 seconds.</fact>     <fact>The last 10... [truncated]
```

### skip-multi-step-explore

```text
<observation>   <type>discovery</type>   <title>fetchSymbol implementation and tests located</title>   <subtitle>SymbolService.java contains the fetchSymbol method, with coverage in two test files.</subtitle>   <facts>     <fact>SymbolService.java contains the fetchSymbol method.</fact>     <fact>Two test files reference fetchSymbol.</fact>     <fact>The repository search examined 34 files.</fact>... [truncated]
```

### skip-format-only

```text
<observation>   <type>discovery</type>   <title>Spotless formatting check passes</title>   <subtitle>Maven Spotless validation found no formatting violations requiring changes.</subtitle>   <facts>     <fact>The `mvn spotless:check` command completed successfully.</fact>     <fact>Spotless reported no formatting violations.</fact>     <fact>No files were reformatted during the check.</fact>   </fa... [truncated]
```

### skip-read-docs

```text
<observation>   <type>discovery</type>   <title>Architecture documentation explains queue operation</title>   <subtitle>docs/ARCHITECTURE.md contains the project architecture overview used to understand queue behavior.</subtitle>   <facts>     <fact>Project architecture documentation is located at docs/ARCHITECTURE.md.</fact>     <fact>The repository root is /repo.</fact>     <fact>No documentatio... [truncated]
```

### skip-grep-search

```text
<observation>   <type>discovery</type>   <title>Pagination state spans hook and controllers</title>   <subtitle>Search identified hasMore handling in the pagination hook and three controller implementations.</subtitle>   <facts>     <fact>usePagination.ts contains hasMore pagination state or logic.</fact>     <fact>Three controllers reference hasMore for pagination handling.</fact>     <fact>The h... [truncated]
```

### skip-open-file-to-explain

```text
<observation>   <type>discovery</type>   <title>UserCache computeIfAbsent behavior identified</title>   <subtitle>UserCache was reviewed to explain the semantics of computeIfAbsent without modifying the implementation.</subtitle>   <facts>     <fact>src/main/java/com/acme/cache/UserCache.java contains the UserCache class reviewed for computeIfAbsent semantics.</fact>     <fact>The review focused o... [truncated]
```

### skip-docker-ps

```text
<observation>   <type>discovery</type>   <title>PostgreSQL and Redis containers are running</title>   <subtitle>Docker status confirms both supporting services have remained up for two hours.</subtitle>   <facts>     <fact>PostgreSQL container status is Up with a runtime of two hours.</fact>     <fact>Redis container status is Up with a runtime of two hours.</fact>     <fact>Docker container statu... [truncated]
```

### skip-empty-diff

```text
<observation>   <type>discovery</type>   <title>Repository working tree remains unchanged</title>   <subtitle>Git diff statistics show no modified, inserted, or deleted files in the repository.</subtitle>   <facts>     <fact>Repository path is `/repo`.</fact>     <fact>`git diff --stat` reports 0 files changed.</fact>     <fact>Git diff reports 0 insertions and 0 deletions.</fact>   </facts>   <na... [truncated]
```

### skip-mvn-version

```text
<observation>   <type>discovery</type>   <title>Build toolchain versions confirmed</title>   <subtitle>The project environment uses Apache Maven 3.9.9 with Java 21.0.5.</subtitle>   <facts>     <fact>Apache Maven version is 3.9.9.</fact>     <fact>Java runtime version is 21.0.5.</fact>     <fact>The working directory is /repo.</fact>   </facts>   <narrative>The repository build environment was con... [truncated]
```

### skip-cat-file

```text
<observation>   <type>discovery</type>   <title>Application configuration reviewed</title>   <subtitle>Spring application configuration was inspected without modification to answer a configuration question.</subtitle>   <facts>     <fact>src/main/resources/application.yml contains 120 lines of application configuration.</fact>     <fact>src/main/resources/application.yml was read without edits.</f... [truncated]
```

### skip-read-config-to-answer

```text
<observation>   <type>discovery</type>   <title>Java version identified from Maven configuration</title>   <subtitle>The project’s root pom.xml was read to determine the configured Java version.</subtitle>   <facts>     <fact>Root project configuration is located at /repo/pom.xml.</fact>     <fact>The Maven configuration was inspected to identify the Java version.</fact>     <fact>No project files... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title hit requires every declared identifier verbatim and keyword coverage ≥ 0.8; keywords are normalized for case, punctuation, CamelCase and a small synonym table.
- Facts/concepts recall is the primary field signal; precision counts produced items that mention an expected keyword. Trace-support rate is measured independently against the recorded tool input/output.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
