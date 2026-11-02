# Model output quality evaluation

- Date: 2026-11-03
- Phase: current
- Provider: openai, model `deepseek-chat`, base URL `replay`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 854098 ms (wall clock; replay calls are 0); temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 96% |
| skip accuracy | 96% |
|   skip confusion (TP/FP/FN/TN) | 17/0/3/62 |
| format errors | 0 |
| validation rejections | 0 |
| call failures | 0 |
| title exact-hit rate (legacy) | 24% |
| title normalized hit rate | 13% |
| title identifier hit rate | 44% |
| type accuracy | 82% |
| facts precision / recall | 53% / 90% |
| facts trace-support rate | 84% |
| concepts precision / recall | 52% / 76% |
| skip consistency across repeats | 92% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 7519 / 11117 / 29421 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 96% | PASS |
| A3 | must-record samples skipped | = 0 | 3 | **FAIL** |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts precision | >= 75% | 53% | **FAIL** |
| A6 | normalized title hit | >= 80% | 13% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | facts P | facts R | format errors |
|---|---|---|---|---|---|---|
| 0 | 82 | 96% | 13% | 53% | 90% | 0 |
| 1 | 13 | 100% | 30% | 72% | 100% | 0 |
| 2 | 13 | 92% | 10% | 70% | 93% | 0 |

- average: skip 96%, title 18%, facts P/R 65%/94%
- worst run: skip 92%, title 10%, facts P/R 53%/90%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 11/12 | 3/12 |
| refactor | 9 | 9/9 | 9/9 | 1/9 |
| test-add | 9 | 9/9 | 5/9 | 1/9 |
| security-fix | 9 | 9/9 | 5/9 | 1/9 |
| config-change | 8 | 8/8 | 8/8 | 1/8 |
| docs-update | 8 | 8/8 | 8/8 | 1/8 |
| deploy-change | 7 | 7/7 | 5/7 | 0/7 |
| skip-low-value | 20 | 17/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title exact | type | facts P/R | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | match | 75%/100% | 67%/100% | 6084 |
| bug-null-cache | bug-fix | observation | observation | pass | 100% | hit | match | 75%/100% | 50%/100% | 11117 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | miss | MISS | 33%/100% | 67%/100% | 8018 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 75% | miss | match | 50%/100% | 67%/100% | 6798 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | miss | match | 67%/100% | 67%/100% | 6983 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 50% | miss | match | 67%/67% | 67%/100% | 9144 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 75% | miss | match | 25%/50% | 67%/100% | 6993 |
| bug-race-counter | bug-fix | observation | observation | pass | 75% | miss | match | 50%/67% | 33%/50% | 9969 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 75% | miss | match | 50%/100% | 50%/100% | 6719 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 67% | miss | match | 100%/100% | 67%/100% | 10205 |
| bug-regex-redos | bug-fix | observation | observation | pass | 100% | hit | match | 25%/50% | 67%/100% | 9104 |
| bug-file-encoding | bug-fix | observation | observation | pass | 50% | miss | match | 100%/100% | 67%/100% | 9365 |
| refactor-invoice | refactor | observation | observation | pass | 67% | miss | match | 80%/100% | 67%/100% | 8436 |
| refactor-auth-controller | refactor | observation | observation | pass | 67% | miss | match | 67%/100% | 67%/100% | 6492 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | miss | match | 33%/50% | 67%/100% | 9809 |
| refactor-order-mapper | refactor | observation | observation | pass | 67% | miss | match | 33%/100% | 67%/100% | 8709 |
| refactor-retry-policy | refactor | observation | observation | pass | 67% | miss | match | 50%/100% | 67%/100% | 29421 |
| refactor-logging-filter | refactor | observation | observation | pass | 67% | miss | match | 50%/100% | 50%/50% | 8993 |
| refactor-sql-repository | refactor | observation | observation | pass | 67% | miss | match | 50%/50% | 67%/100% | 9586 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | match | 25%/100% | 67%/100% | 9984 |
| refactor-feature-flag | refactor | observation | observation | pass | 67% | miss | match | 25%/50% | 67%/100% | 8797 |
| test-repro-timezone | test-add | observation | observation | pass | 33% | miss | MISS | 50%/100% | 67%/100% | 10239 |
| test-repro-login | test-add | observation | observation | pass | 75% | miss | MISS | 50%/100% | 33%/50% | 9925 |
| test-unit-retry | test-add | observation | observation | pass | 75% | miss | match | 20%/50% | 67%/100% | 9355 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | match | 50%/100% | 67%/100% | 9990 |
| test-parameterized-csv | test-add | observation | observation | pass | 75% | miss | match | 25%/50% | 100%/100% | 7265 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | match | 50%/100% | 67%/100% | 7519 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | MISS | 80%/100% | 67%/100% | 10727 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | match | 25%/100% | 33%/50% | 8981 |
| test-flaky-clock | test-add | observation | observation | pass | 50% | miss | MISS | 100%/100% | 67%/100% | 9935 |
| security-redact-log | security-fix | observation | observation | pass | 100% | miss | match | 67%/100% | 33%/50% | 9138 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | hit | match | 33%/50% | 33%/50% | 8427 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | miss | MISS | 50%/100% | 0%/0% | 13016 |
| security-csrf | security-fix | observation | observation | pass | 33% | miss | MISS | 25%/50% | 33%/50% | 8380 |
| security-secret-config | security-fix | observation | observation | pass | 75% | miss | match | 67%/100% | 67%/100% | 5221 |
| security-xxe | security-fix | observation | observation | pass | 67% | miss | match | 75%/100% | 67%/100% | 8373 |
| security-path-traversal | security-fix | observation | observation | pass | 75% | miss | match | 67%/100% | 50%/100% | 5862 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | miss | MISS | 75%/100% | 33%/50% | 5753 |
| security-cookie-httponly | security-fix | observation | observation | pass | 100% | hit | MISS | 40%/100% | 0%/0% | 9058 |
| config-hikari-pool | config-change | observation | observation | pass | 50% | miss | match | 50%/100% | 33%/50% | 9240 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | match | 50%/100% | 33%/50% | 8507 |
| config-log-level | config-change | observation | observation | pass | 75% | miss | match | 50%/100% | 50%/50% | 7773 |
| config-cors | config-change | observation | observation | pass | 100% | miss | match | 67%/100% | 33%/50% | 7658 |
| config-timeout | config-change | observation | observation | pass | 50% | miss | match | 67%/100% | 33%/50% | 7973 |
| config-feature-flags | config-change | observation | observation | pass | 25% | miss | match | 67%/100% | 50%/50% | 6952 |
| config-docker-healthcheck | config-change | observation | observation | pass | 75% | miss | match | 100%/67% | 33%/50% | 5420 |
| config-maven-plugin | config-change | observation | observation | pass | 75% | miss | match | 50%/100% | 33%/50% | 6701 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | match | 33%/100% | 50%/50% | 6256 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | miss | match | 75%/100% | 50%/50% | 10139 |
| docs-runbook | docs-update | observation | observation | pass | 75% | miss | match | 33%/100% | 67%/100% | 6798 |
| docs-changelog | docs-update | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 7239 |
| docs-architecture-adr | docs-update | observation | observation | pass | 50% | miss | match | 25%/50% | 33%/50% | 9535 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | miss | match | 67%/100% | 67%/100% | 6749 |
| docs-security-policy | docs-update | observation | observation | pass | 75% | miss | match | 67%/100% | 50%/50% | 5667 |
| docs-contributing | docs-update | observation | observation | pass | 67% | miss | match | 33%/50% | 67%/100% | 6667 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | hit | match | 50%/100% | 33%/50% | 5546 |
| deploy-github-actions | deploy-change | observation | observation | pass | 50% | miss | match | 25%/100% | 33%/50% | 6965 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 75% | miss | MISS | 25%/100% | 50%/100% | 15447 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 75% | miss | match | 40%/100% | 33%/50% | 22715 |
| deploy-helm-values | deploy-change | observation | observation | pass | 75% | miss | match | 67%/100% | 50%/50% | 7775 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | MISS | 40%/100% | 67%/100% | 7316 |
| deploy-canary | deploy-change | observation | observation | pass | 50% | miss | match | 50%/100% | 33%/50% | 6993 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | 2848 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | 3995 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | 3501 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | 4099 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | 7994 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | 5045 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | 4130 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | 2557 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | 3279 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | 4847 |
| skip-grep-search | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 8863 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | 3266 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | 4194 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | 2277 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | 3471 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | 2267 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | 8952 |
| skip-ls-tree | skip-low-value | skip | malformed | FAIL | - | - | - | - | - | 3412 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | 3484 |
| skip-no-conclusion | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 6558 |

## Problems

- bug-timezone-report: title lost identifiers [DailyReportTest, reportUsesBusinessTimezone] (got 'Added timezone test to verify daily report bugfix')
- bug-timezone-report: type 'bugfix' != expected 'feature'
- bug-payment-missing-order: title coverage 75% (expected keywords [fix, payment, missing, order])
- bug-connection-leak: title lost identifiers [ConnectionPool] (got 'Connection pool leak fixed with try-with-resources')
- bug-jwt-expiry: title lost identifiers [JwtVerifier] (got 'JWT verifier rejects tokens at exact expiration boundary')
- bug-jwt-expiry: facts recall 67% (expected [expiration, isEqual, rejects], got [File src/main/java/com/acme/auth/JwtVerifier.java had expiration check changed from `claims.getExpiration().before(now)` to `claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now)`., Previously, a token with expiration exactly equal to the current instant was incorrectly accepted., JwtVerifierTest#expiredAtExactBoundary now passes, confirming the boundary case is handled correctly.])
- bug-csv-crlf: title coverage 75% (expected keywords [fix, csv, line, endings])
- bug-csv-crlf: facts recall 50% (expected [CRLF, multilineFieldKeepsRowCount], got [CsvExporter.java write path now applies line.replaceAll("\r?\n", "\r\n") before quoting, Fix targets CSV rows whose fields contain embedded line breaks, CsvExporterTest#multilineFieldKeepsRowCount now passes, File located at src/main/java/com/acme/export/CsvExporter.java in /repo/export])
- bug-race-counter: title coverage 75% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: facts recall 67% (expected [incrementAndGet, AtomicLong, 1000], got [src/main/java/com/acme/metrics/CounterService.java increment() previously used counter.set(counter.get() + 1) followed by counter.get(), a non-atomic read-modify-write., increment() now calls counter.incrementAndGet(), making the increment a single atomic operation., CounterServiceConcurrencyTest passes with 1000 concurrent threads producing an exact count of 1000., The previous get/set sequence could lose increments when multiple threads interleaved between the read and the write.])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, what-changed, gotcha])
- bug-n-plus-one: title coverage 75% (expected keywords [fix, n+1, orderrepository, entitygraph])
- bug-dateformat-locale: title coverage 67% (expected keywords [fix, locale, dateformatter])
- bug-regex-redos: facts recall 50% (expected [linear pattern, longInputFinishesUnder100ms], got [UrlValidator.java used a regex with nested quantifiers causing catastrophic backtracking, New pattern is `^https?://[^\s]+$` which avoids nested quantifiers, UrlValidatorTest#longInputFinishesUnder100ms now passes after the fix, File located at src/main/java/com/acme/validation/UrlValidator.java in /repo/validation])
- bug-file-encoding: title coverage 50% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- refactor-invoice: title coverage 67% (expected keywords [refactor, invoiceservice, delegates])
- refactor-auth-controller: title coverage 67% (expected keywords [refactor, authcontroller, tokenservice])
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: facts recall 50% (expected [unmodifiable map, typed accessors], got [ConfigLoader.java modified to return an unmodifiable map built via stream collect with toUnmodifiableMap, ConfigLoader now exposes typed accessor methods for configuration values, ConfigLoaderTest passes with the same configuration values as before the change])
- refactor-order-mapper: title coverage 67% (expected keywords [refactor, ordermapper, convertvalue])
- refactor-retry-policy: title coverage 67% (expected keywords [refactor, retrypolicy, backoffstrategy])
- refactor-logging-filter: title coverage 67% (expected keywords [refactor, loggingfilter, logexchange])
- refactor-logging-filter: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- refactor-sql-repository: title coverage 67% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [src/main/java/com/acme/users/UserRepository.java now declares the tenant query via @Query("select u from User u where u.tenantId = :tenantId")., The previous implementation built SQL by string concatenation, which is vulnerable to injection and quoting issues., The new query binds tenantId as a named parameter (:tenantId) instead of interpolating it into the SQL text., UserRepositoryTest confirms the repository still returns the same tenant rows after the change.])
- refactor-feature-flag: title coverage 67% (expected keywords [refactor, featureflagservice, delegates])
- refactor-feature-flag: facts recall 50% (expected [Flags.isEnabled, on/off matrix], got [FeatureFlagService.java modified to return flags.isEnabled(flag, context), Evaluation logic now lives in the Flags class rather than FeatureFlagService, FeatureFlagServiceTest retains the same on/off behavior matrix, Public API behavior of FeatureFlagService remains unchanged])
- test-repro-timezone: title lost identifiers [DailyReportTest, reportUsesBusinessTimezone] (got 'Failing test reproduces UTC-vs-business-date bug in DailyReport')
- test-repro-timezone: type 'discovery' != expected 'feature'
- test-repro-login: title lost identifiers [LoginControllerTest] (got 'Test added for locked account status code bug')
- test-repro-login: type 'bugfix' != expected 'feature'
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, how-it-works, pattern])
- test-unit-retry: title lost identifiers [RetryPolicyTest] (got 'Retry policy delay doubling test added')
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [New test class RetryPolicyTest created at src/test/java/com/acme/resilience/RetryPolicyTest.java, Test method delayDoubles uses JUnit 5 @ParameterizedTest with @CsvSource inputs (1,100), (2,200), (3,400), Test asserts policy.delay(attempt) returns expected exponential backoff delay values, All 3 parameterized test cases pass successfully, RetryPolicy.delay() implements doubling delay behavior: 100ms base with 2x growth per attempt])
- test-parameterized-csv: title lost identifiers [CsvExporterTest] (got 'Added CSV exporter quoting stability tests')
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable added as a @ParameterizedTest using @ValueSource with three inputs: "a,b", "a\"b", and "a\nb", Test validates that CSV quoting logic correctly handles commas, embedded quotes, and newlines, All three parameterized test cases pass, Test file located at src/test/java/com/acme/export/CsvExporterTest.java in the export module])
- test-security-headers: title lost identifiers [SecurityHeadersTest] (got 'Security headers test added for CSP and nosniff')
- test-repro-null-cache: title lost identifiers [CacheConcurrencyTest, UserCache] (got 'Concurrency test added to reproduce intermittent null from UserCache')
- test-repro-null-cache: type 'bugfix' != expected 'feature'
- test-contract-openapi: title lost identifiers [OpenApiContractTest] (got 'Added OpenAPI contract test to prevent schema drift')
- test-contract-openapi: concepts recall 50% (expected [what-changed, pattern], got [pattern, how-it-works, problem-solution])
- test-flaky-clock: title lost identifiers [SchedulerTest] (got 'Eliminate test flakiness with deterministic Clock.fixed')
- test-flaky-clock: type 'bugfix' != expected 'feature'
- security-redact-log: title lost identifiers [LogSanitizer] (got 'Log sanitizer now redacts credentials before logging')
- security-redact-log: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, problem-solution, pattern])
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.java used string concatenation for SQL query in findByEmail method, Changed to parameterized query: jdbcTemplate.query("select * from users where email = ?", mapper, email), SqlInjectionTest#emailWithQuoteReturnsEmpty now passes, confirming injection is prevented])
- security-sql-injection: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, security, what-changed])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: type 'feature' != expected 'bugfix'
- security-authz-admin: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, how-it-works, pattern])
- security-csrf: title lost identifiers [CsrfFilter] (got 'CSRF protection implemented in web filter')
- security-csrf: type 'feature' != expected 'bugfix'
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter.java added token validation logic to reject mismatched CSRF tokens, Requests with invalid CSRF tokens now return HTTP 403 Forbidden response, CsrfFilterTest#postWithoutTokenIs403 test passes verifying the new behavior, Filter validates token before processing state-changing HTTP requests])
- security-csrf: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, how-it-works, problem-solution])
- security-secret-config: title lost identifiers [application.yml, ACME_API_KEY] (got 'API key moved from hardcoded value to environment variable')
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-path-traversal: title lost identifiers [FileDownloadController] (got 'Path Traversal Protection Added to File Download Controller')
- security-rate-limit: title lost identifiers [RateLimiter] (got 'Rate limiter implements token bucket algorithm')
- security-rate-limit: type 'feature' != expected 'bugfix'
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [how-it-works, pattern, what-changed])
- security-cookie-httponly: title lost identifiers [SessionCookieConfig] (got 'Session cookie hardened with HttpOnly, Secure, and SameSite=Strict flags')
- security-cookie-httponly: type 'feature' != expected 'bugfix'
- security-cookie-httponly: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, why-it-exists, pattern])
- config-hikari-pool: title lost identifiers [maximum-pool-size] (got 'Configure Hikari connection pool sizing in application.yml')
- config-hikari-pool: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, pattern])
- config-flyway-baseline: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, why-it-exists, gotcha])
- config-log-level: title lost identifiers [logback-spring.xml] (got 'Logback root logger switched to INFO with JSON appender')
- config-log-level: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-cors: title lost identifiers [CorsConfig] (got 'CORS configuration restricted to specific origin')
- config-cors: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, pattern])
- config-timeout: title lost identifiers [WebClientConfig] (got 'WebClient HTTP timeout configured')
- config-timeout: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, pattern])
- config-feature-flags: title lost identifiers [feature-flags.yml] (got 'Feature flags configured for checkout-v2 rollout')
- config-feature-flags: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-docker-healthcheck: title coverage 75% (expected keywords [add, docker, healthcheck, actuator])
- config-docker-healthcheck: facts recall 67% (expected [HEALTHCHECK, 30s, /actuator/health], got [HEALTHCHECK instruction added to Dockerfile with 30-second interval, Health check hits http://localhost:8080/actuator/health, Docker build succeeds with new healthcheck configuration])
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, pattern])
- config-maven-plugin: title lost identifiers [jacoco-maven-plugin, pom.xml] (got 'Added JaCoCo Maven plugin for code coverage')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, pattern])
- docs-api-observations: title lost identifiers [API.md, /api/observations] (got 'Documented /api/observations pagination contract')
- docs-api-observations: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-readme-setup: title lost identifiers [README.md] (got 'README setup instructions added')
- docs-readme-setup: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-runbook: title lost identifiers [runbook.md] (got 'Queue recovery runbook documented')
- docs-changelog: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- docs-architecture-adr: title lost identifiers [0007-extraction-validation.md] (got 'ADR 0007: Extract fact validation before persistence')
- docs-architecture-adr: facts recall 50% (expected [validate, before persistence], got [ADR 0007 created at docs/adr/0007-extraction-validation.md, Decision mandates validation of extracted facts against original tool output before persistence, Rejection path documented for cases where extracted facts fail validation, Validation step acts as quality gate preventing incorrect facts from entering system])
- docs-architecture-adr: concepts recall 50% (expected [why-it-exists, trade-off], got [why-it-exists, pattern, problem-solution])
- docs-migration-guide: title lost identifiers [MIGRATION.md] (got 'Documented V20 migration prerequisite')
- docs-security-policy: title lost identifiers [SECURITY.md] (got 'Security policy documentation added')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, why-it-exists])
- docs-contributing: title coverage 67% (expected keywords [update, contributing, verify-local])
- docs-contributing: facts recall 50% (expected [verify-local.sh, four test layers], got [CONTRIBUTING.md now requires running ./scripts/verify-local.sh before opening a pull request., The verify script covers four distinct test layers in the repository's verification process., The requirement is documented in the repository's contribution guidelines at CONTRIBUTING.md.])
- deploy-k8s-probe: title lost identifiers [deployment.yaml, /api/health] (got 'Added Kubernetes readiness probe to deployment.yaml')
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern, how-it-works])
- deploy-github-actions: title lost identifiers [ci.yml] (got 'CI workflow now tests backend on JDK 21 and 25')
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern, why-it-exists])
- deploy-docker-multistage: title lost identifiers [Dockerfile, eclipse-temurin:21-jre] (got 'Dockerfile converted to multi-stage build for image size reduction')
- deploy-docker-multistage: type 'refactor' != expected 'change'
- deploy-terraform-iam: title lost identifiers [main.tf] (got 'Deploy IAM role restricted to read-only S3 access')
- deploy-terraform-iam: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, why-it-exists, pattern])
- deploy-helm-values: title lost identifiers [values.yaml] (got 'Helm values updated to increase replicas and cap memory')
- deploy-helm-values: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- deploy-rollback-script: title lost identifiers [rollback.sh] (got 'Added Kubernetes rollback script for claude-mem deployment')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 50% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern, how-it-works])
- skip-grep-search: expected <skip> but produced an observation
- skip-ls-tree: expected <skip> but produced malformed output: DataValidationException: No <observation> block found in XML
- skip-no-conclusion: expected <skip> but produced an observation

## Raw answers behind problems

### bug-timezone-report

```text
```xml <observation>   <type>bugfix</type>   <title>Added timezone test to verify daily report bugfix</title>   <subtitle>Test pins UTC timestamp to verify Asia/Shanghai business date calculation in DailyReport</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone added to verify business timezone handling</fact>     <fact>Test uses 2026-11-01T23:30Z timestamp expecting Asia/Sh... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>PaymentService throws PaymentNotFoundException for missing orderId</title>   <subtitle>Replaced null return with explicit exception when repository lookup finds no payment for the given orderId.</subtitle>   <facts>     <fact>PaymentService.java modified in src/main/java/com/acme/payment/PaymentService.java</fact>     <fact>Method now calls repository.f... [truncated]
```

### bug-connection-leak

```text
<observation>   <type>bugfix</type>   <title>Connection pool leak fixed with try-with-resources</title>   <subtitle>ConnectionPool.borrow now guarantees connections close even when caller throws an exception.</subtitle>   <facts>     <fact>ConnectionPool.borrow in src/main/java/com/acme/db/ConnectionPool.java was leaking connections on caller exceptions</fact>     <fact>borrow method updated to wr... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>JWT verifier rejects tokens at exact expiration boundary</title>   <subtitle>JwtVerifier now rejects tokens whose expiration equals the current instant, closing a boundary-condition acceptance gap.</subtitle>   <facts>     <fact>File src/main/java/com/acme/auth/JwtVerifier.java had expiration check changed from `claims.getExpiration().before(now)` to `c... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>CsvExporter normalises embedded line endings to CRLF before quoting</title>   <subtitle>Multiline CSV fields now keep row count intact by normalising \r\n line endings prior to quoting.</subtitle>   <facts>     <fact>CsvExporter.java write path now applies line.replaceAll("\r?\n", "\r\n") before quoting</fact>     <fact>Fix targets CSV rows whose fields... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed race condition in CounterService.increment</title>   <subtitle>Replaced non-atomic get/set counter update with AtomicLong.incrementAndGet to prevent lost updates under concurrency.</subtitle>   <facts>     <fact>src/main/java/com/acme/metrics/CounterService.java increment() previously used counter.set(counter.get() + 1) followed by counter.get(), ... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>bugfix</type>   <title>Fixed N+1 query problem in OrderRepository.findAllByCustomerId</title>   <subtitle>Order items now fetched in a single SQL statement using JPA EntityGraph instead of 1 + N queries.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId(String customerId) was causing an N+1 query problem when accessing order items.</fact>     <fact>Added @Ent... [truncated]
```

### bug-dateformat-locale

```text
<observation>   <type>bugfix</type>   <title>DateFormatter pinned to Locale.ROOT to avoid Turkish locale month-name corruption</title>   <subtitle>DateFormatter now uses Locale.ROOT in DateTimeFormatter.ofPattern, preventing Turkish default locales from producing dotless-i month names.</subtitle>   <facts>     <fact>File src/main/java/com/acme/i18n/DateFormatter.java was edited to use DateTimeForm... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed ReDoS vulnerability in UrlValidator regex</title>   <subtitle>Replaced nested-quantifier regex with linear pattern to prevent catastrophic backtracking.</subtitle>   <facts>     <fact>UrlValidator.java used a regex with nested quantifiers causing catastrophic backtracking</fact>     <fact>New pattern is `^https?://[^\s]+$` which avoids nested quan... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>FileReaderFactory forced to UTF-8 charset</title>   <subtitle>FileReaderFactory now explicitly uses UTF-8 instead of platform default, fixing encoding issues on non-UTF-8 JVMs.</subtitle>   <facts>     <fact>FileReaderFactory.java now uses `new InputStreamReader(in, StandardCharsets.UTF_8)` instead of relying on platform default charset</fact>     <fact... [truncated]
```

### refactor-invoice

```text
<observation>   <type>refactor</type>   <title>InvoiceService delegates to TaxCalculator, InvoiceRenderer, MailGateway</title>   <subtitle>InvoiceService refactored to delegate tax calculation, rendering, and mail delivery to dedicated components.</subtitle>   <facts>     <fact>InvoiceService.java updated so rendering call now uses renderer.render(taxCalculator.calculate(invoice), invoice)</fact> ... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Extract JWT Issuance from AuthController to TokenService</title>   <subtitle>AuthController now delegates JWT creation to TokenService.issue instead of building tokens inline.</subtitle>   <facts>     <fact>AuthController no longer constructs JWTs directly; it calls TokenService.issue(authentication)</fact>     <fact>AuthControllerTest remains unchang... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>refactor</type>   <title>ConfigLoader returns unmodifiable map with typed accessors</title>   <subtitle>Configuration loading hardened by returning immutable properties and exposing typed accessor methods.</subtitle>   <facts>     <fact>ConfigLoader.java modified to return an unmodifiable map built via stream collect with toUnmodifiableMap</fact>     <fact>ConfigLoader now ex... [truncated]
```

### refactor-order-mapper

```text
<observation>   <type>refactor</type>   <title>OrderMapper simplified using Jackson convertValue</title>   <subtitle>Replaced 40 hand-written setters in OrderMapper with a single Jackson convertValue call.</subtitle>   <facts>     <fact>OrderMapper.java now uses mapper.convertValue(entity, OrderDto.class) instead of 40 manual setter calls</fact>     <fact>OrderMapperTest compares the same DTO fiel... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>RetryPolicy delegates delay calculation to BackoffStrategy</title>   <subtitle>RetryPolicy extracted delay calculation logic into BackoffStrategy, preserving existing 100/200/400 delay sequence.</subtitle>   <facts>     <fact>RetryPolicy.java now calls backoff.nextDelay(attempt) instead of computing delay internally</fact>     <fact>BackoffStrategy ga... [truncated]
```

### refactor-logging-filter

```text
```xml <observation>   <type>refactor</type>   <title>LoggingFilter extracts logExchange method</title>   <subtitle>Timing and header capture logic moved into a dedicated logExchange method within LoggingFilter.</subtitle>   <facts>     <fact>File modified: src/main/java/com/acme/web/LoggingFilter.java</fact>     <fact>Timing and header capture now encapsulated in a logExchange method</fact>     <... [truncated]
```

### refactor-sql-repository

```text
```xml <observation>   <type>refactor</type>   <title>UserRepository switched from string-concatenated SQL to JPQL @Query</title>   <subtitle>Tenant-filtered user lookup now uses a typed JPQL query with a named parameter.</subtitle>   <facts>     <fact>src/main/java/com/acme/users/UserRepository.java now declares the tenant query via @Query("select u from User u where u.tenantId = :tenantId").</fa... [truncated]
```

### refactor-feature-flag

```text
<observation>   <type>refactor</type>   <title>FeatureFlagService now delegates evaluation to Flags.isEnabled</title>   <subtitle>FeatureFlagService refactored to delegate flag evaluation to Flags.isEnabled(flag, context).</subtitle>   <facts>     <fact>FeatureFlagService.java modified to return flags.isEnabled(flag, context)</fact>     <fact>Evaluation logic now lives in the Flags class rather th... [truncated]
```

### test-repro-timezone

```text
```xml <observation>   <type>discovery</type>   <title>Failing test reproduces UTC-vs-business-date bug in DailyReport</title>   <subtitle>DailyReportTest#reportUsesBusinessTimezone confirms dateAt() returns wrong date for Asia/Shanghai business timezone</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone written in src/test/java/com/acme/reporting/DailyReportTest.java</fact>... [truncated]
```

### test-repro-login

```text
```xml <observation>   <type>bugfix</type>   <title>Test added for locked account status code bug</title>   <subtitle>LoginControllerTest reproduces wrong HTTP status for locked accounts</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 verifies locked accounts return HTTP 423</fact>     <fact>Test uses Mockito to stub users.isLocked("bob") returning true</fact>     <fact>... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Retry policy delay doubling test added</title>   <subtitle>Parameterized JUnit test verifies exponential backoff delays of 100/200/400ms across retry attempts.</subtitle>   <facts>     <fact>New test class RetryPolicyTest created at src/test/java/com/acme/resilience/RetryPolicyTest.java</fact>     <fact>Test method delayDoubles uses JUnit 5 @Parameteri... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CSV exporter quoting stability tests</title>   <subtitle>Parameterized tests verify CSV escaping for commas, quotes, and newlines all pass.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable added as a @ParameterizedTest using @ValueSource with three inputs: "a,b", "a\"b", and "a\nb"</fact>     <fact>Test validates that CSV quoting lo... [truncated]
```

### test-security-headers

```text
<observation>   <type>feature</type>   <title>Security headers test added for CSP and nosniff</title>   <subtitle>MockMvc test verifies Content-Security-Policy and X-Content-Type-Options headers on responses.</subtitle>   <facts>     <fact>New test file src/test/java/com/acme/web/SecurityHeadersTest.java was created in the web project.</fact>     <fact>Test method responseHasCspAndNosniff uses moc... [truncated]
```

### test-repro-null-cache

```text
```xml <observation>   <type>bugfix</type>   <title>Concurrency test added to reproduce intermittent null from UserCache</title>   <subtitle>New test concurrentReadersNeverSeeNull fails on 1000 parallel cache.get calls before the race condition fix.</subtitle>   <facts>     <fact>Test file src/test/java/com/acme/cache/CacheConcurrencyTest.java created with test concurrentReadersNeverSeeNull</fact>... [truncated]
```

### test-contract-openapi

```text
```xml <observation>   <type>feature</type>   <title>Added OpenAPI contract test to prevent schema drift</title>   <subtitle>Test validates that generated springdoc spec matches committed docs/openapi.yaml</subtitle>   <facts>     <fact>Created OpenApiContractTest in src/test/java/com/acme/api</fact>     <fact>Test method generatedSpecMatchesCommittedFile diffs springdoc output against docs/openap... [truncated]
```

### test-flaky-clock

```text
```xml <observation>   <type>bugfix</type>   <title>Eliminate test flakiness with deterministic Clock.fixed</title>   <subtitle>SchedulerTest replaced Thread.sleep-based timing with a fixed clock for reliable, repeatable test runs.</subtitle>   <facts>     <fact>src/test/java/com/acme/scheduling/SchedulerTest.java now sets scheduler.clock to Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneO... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Log sanitizer now redacts credentials before logging</title>   <subtitle>LogSanitizer redacts password, token, and secret values to prevent credential leakage into logs.</subtitle>   <facts>     <fact>LogSanitizer.java applies a regex replacement of "(password|token|secret)=\S+" to "$1=***" on log values.</fact>     <fact>The redaction covers three sens... [truncated]
```

### security-sql-injection

```text
```xml <observation>   <type>bugfix</type>   <title>SQL injection vulnerability fixed in UserDao.findByEmail</title>   <subtitle>Replaced string concatenation with parameterized query to prevent SQL injection in email lookup.</subtitle>   <facts>     <fact>UserDao.java used string concatenation for SQL query in findByEmail method</fact>     <fact>Changed to parameterized query: jdbcTemplate.query(... [truncated]
```

### security-authz-admin

```text
<observation>   <type>feature</type>   <title>AdminController secured with ROLE_ADMIN authorization</title>   <subtitle>Admin endpoints now require ADMIN role via @PreAuthorize, verified by 403 test.</subtitle>   <facts>     <fact>AdminController.java was edited to add @PreAuthorize("hasRole('ADMIN')") restricting every endpoint to ADMIN role.</fact>     <fact>Authorization enforced at /repo/admin... [truncated]
```

### security-csrf

```text
<observation>   <type>feature</type>   <title>CSRF protection implemented in web filter</title>   <subtitle>State-changing requests with mismatched CSRF tokens now rejected with HTTP 403</subtitle>   <facts>     <fact>CsrfFilter.java added token validation logic to reject mismatched CSRF tokens</fact>     <fact>Requests with invalid CSRF tokens now return HTTP 403 Forbidden response</fact>     <fa... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>API key moved from hardcoded value to environment variable</title>   <subtitle>Hardcoded API key in application.yml replaced with ACME_API_KEY environment variable reference.</subtitle>   <facts>     <fact>src/main/resources/application.yml previously contained literal API key "sk-live-123"</fact>     <fact>application.yml now references ${ACME_API_KEY}... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>XML External Entity (XXE) vulnerability blocked in XmlImportService</title>   <subtitle>DOCTYPE declarations disabled in XML parser to prevent XXE injection attacks.</subtitle>   <facts>     <fact>XmlImportService.java in /repo/import/src/main/java/com/acme/import/ modified</fact>     <fact>Added factory.setFeature("http://apache.org/xml/features/disall... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Path Traversal Protection Added to File Download Controller</title>   <subtitle>FileDownloadController now rejects path traversal attempts using normalize and startsWith checks.</subtitle>   <facts>     <fact>FileDownloadController.java updated with path normalization guard using base.resolve(name).normalize()</fact>     <fact>Names escaping base direct... [truncated]
```

### security-rate-limit

```text
<observation>   <type>feature</type>   <title>Rate limiter implements token bucket algorithm</title>   <subtitle>RateLimiter now rejects requests with 429 when token bucket is exhausted.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/RateLimiter.java added check: if (!bucket.tryConsume(1)) throw new TooManyRequestsException()</fact>     <fact>TooManyRequestsException maps to HTTP 429 st... [truncated]
```

### security-cookie-httponly

```text
```xml <observation>   <type>feature</type>   <title>Session cookie hardened with HttpOnly, Secure, and SameSite=Strict flags</title>   <subtitle>SessionCookieConfig now applies all three security flags to the session cookie, closing XSS, MITM, and CSRF attack vectors.</subtitle>   <facts>     <fact>File modified: src/main/java/com/acme/auth/SessionCookieConfig.java</fact>     <fact>Session cookie... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Configure Hikari connection pool sizing in application.yml</title>   <subtitle>DataSource pool limits now set to maximum 20 connections with 5 minimum idle connections.</subtitle>   <facts>     <fact>application.yml now sets spring.datasource.hikari.maximum-pool-size to 20</fact>     <fact>application.yml now sets spring.datasource.hikari.minimum-idle t... [truncated]
```

### config-flyway-baseline

```text
<observation>   <type>change</type>   <title>Flyway baselineOnMigrate enabled in pom.xml</title>   <subtitle>Maven build now applies Flyway baseline with version 1 on migration startup.</subtitle>   <facts>     <fact>pom.xml defines flyway.baselineVersion property with value 1</fact>     <fact>pom.xml defines flyway.baselineOnMigrate property set to true</fact>     <fact>Edit applied successfully ... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Logback root logger switched to INFO with JSON appender</title>   <subtitle>logback-spring.xml root level changed to INFO and wired to the JSON appender.</subtitle>   <facts>     <fact>File modified: src/main/resources/logback-spring.xml</fact>     <fact>Root logger level set to INFO in logback-spring.xml</fact>     <fact>Root logger now references the ... [truncated]
```

### config-cors

```text
```xml <observation>   <type>change</type>   <title>CORS configuration restricted to specific origin</title>   <subtitle>CorsConfig now limits /api/** endpoints to https://app.acme.com with GET and POST methods only.</subtitle>   <facts>     <fact>CorsConfig.java restricts /api/** routes to origin https://app.acme.com</fact>     <fact>Only GET and POST HTTP methods are permitted for API endpoints<... [truncated]
```

### config-timeout

```text
<observation>   <type>change</type>   <title>WebClient HTTP timeout configured</title>   <subtitle>Added 5-second response timeout to HttpClient in WebClientConfig</subtitle>   <facts>     <fact>WebClientConfig.java now configures HttpClient with 5-second response timeout</fact>     <fact>Timeout implemented via HttpClient.create().responseTimeout(Duration.ofSeconds(5))</fact>     <fact>WebClientC... [truncated]
```

### config-feature-flags

```text
<observation>   <type>change</type>   <title>Feature flags configured for checkout-v2 rollout</title>   <subtitle>Feature flags updated to enable checkout-v2 and disable legacy-search in application configuration.</subtitle>   <facts>     <fact>checkout-v2 feature flag set to true in feature-flags.yml</fact>     <fact>legacy-search feature flag set to false in feature-flags.yml</fact>     <fact>Fl... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Dockerfile HEALTHCHECK Added</title>   <subtitle>Container now monitors application health via Spring Boot actuator endpoint every 30 seconds.</subtitle>   <facts>     <fact>HEALTHCHECK instruction added to Dockerfile with 30-second interval</fact>     <fact>Health check hits http://localhost:8080/actuator/health</fact>     <fact>Docker build succeeds w... [truncated]
```

### config-maven-plugin

```text
```xml <observation>   <type>change</type>   <title>Added JaCoCo Maven plugin for code coverage</title>   <subtitle>Configured jacoco-maven-plugin 0.8.12 in pom.xml to generate coverage reports during Maven verify phase.</subtitle>   <facts>     <fact>jacoco-maven-plugin version 0.8.12 added to pom.xml</fact>     <fact>Plugin configured to generate coverage reports during Maven verify phase</fact>... [truncated]
```

### docs-api-observations

```text
<observation>   <type>change</type>   <title>Documented /api/observations pagination contract</title>   <subtitle>docs/API.md now specifies that GET /api/observations returns hasMore and items fields.</subtitle>   <facts>     <fact>docs/API.md updated to document the /api/observations endpoint pagination response structure</fact>     <fact>GET /api/observations returns hasMore (boolean) and items ... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>README setup instructions added</title>   <subtitle>README.md now documents three-step local setup process for the project.</subtitle>   <facts>     <fact>README.md gained a "Setup" section with three numbered steps</fact>     <fact>Step 1 requires JDK 21 installation</fact>     <fact>Step 2 runs PostgreSQL via docker compose: `docker compose up -d post... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Queue recovery runbook documented</title>   <subtitle>New docs/runbook.md captures the three-step queue recovery procedure.</subtitle>   <facts>     <fact>docs/runbook.md created in /repo documenting queue recovery steps.</fact>     <fact>Procedure: check pending_messages, restart the worker, verify /api/health.</fact>     <fact>Runbook is intended as o... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>CHANGELOG.md updated with 2.4.0 release entry</title>   <subtitle>Added 2.4.0 release notes documenting offline replay evaluation and V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md gained a 2.4.0 entry dated 2026-11-03</fact>     <fact>2.4.0 features offline replay evaluation</fact>     <fact>2.4.0 includes a fix for a V20 migra... [truncated]
```

### docs-architecture-adr

```text
```xml <observation>   <type>decision</type>   <title>ADR 0007: Extract fact validation before persistence</title>   <subtitle>Decision to validate extracted facts against tool output before persisting them, with defined rejection path.</subtitle>   <facts>     <fact>ADR 0007 created at docs/adr/0007-extraction-validation.md</fact>     <fact>Decision mandates validation of extracted facts against ... [truncated]
```

### docs-migration-guide

```text
```xml <observation>   <type>change</type>   <title>Documented V20 migration prerequisite</title>   <subtitle>Added V20 migration guidance requiring mem_sessions backup and enabling manual_sentinel_reviewed.</subtitle>   <facts>     <fact>docs/MIGRATION.md now documents a V20 migration prerequisite section.</fact>     <fact>V20 migration requires backing up the mem_sessions table before running.</... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Security policy documentation added</title>   <subtitle>SECURITY.md created defining vulnerability disclosure channel and public issue restriction.</subtitle>   <facts>     <fact>SECURITY.md file created at repository root</fact>     <fact>Vulnerability reports directed to security@acme.com</fact>     <fact>Public issues explicitly forbidden for securit... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>CONTRIBUTING.md updated with pre-PR verification requirement</title>   <subtitle>Contributors now must run ./scripts/verify-local.sh before opening a pull request, covering four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md now requires running ./scripts/verify-local.sh before opening a pull request.</fact>     <fact>The verify script cove... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added Kubernetes readiness probe to deployment.yaml</title>   <subtitle>Deployment now includes HTTP readiness probe on /api/health at port 37777 with 15s initial delay.</subtitle>   <facts>     <fact>File modified: deploy/deployment.yaml</fact>     <fact>Readiness probe configured as httpGet against path /api/health on port 37777</fact>     <fact>initi... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>CI workflow now tests backend on JDK 21 and 25</title>   <subtitle>GitHub Actions matrix strategy added to run backend tests across Java 21 and 25.</subtitle>   <facts>     <fact>File modified: .github/workflows/ci.yml</fact>     <fact>Added matrix strategy with java versions [21, 25]</fact>     <fact>Backend tests now execute in parallel across two JDK... [truncated]
```

### deploy-docker-multistage

```text
<observation>   <type>refactor</type>   <title>Dockerfile converted to multi-stage build for image size reduction</title>   <subtitle>Docker image rebuilt in two stages cuts size from 780MB to 210MB while keeping runtime behavior unchanged.</subtitle>   <facts>     <fact>Dockerfile in /repo was changed to a multi-stage build.</fact>     <fact>Build stage uses Maven; runtime stage uses eclipse-temu... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Deploy IAM role restricted to read-only S3 access</title>   <subtitle>AWS deploy role policy changed from full S3 access to AmazonS3ReadOnlyAccess to enforce least privilege.</subtitle>   <facts>     <fact>File infra/main.tf contains resource aws_iam_role_policy named "deploy".</fact>     <fact>The deploy role policy ARN was changed to arn:aws:iam::aws:... [truncated]
```

### deploy-helm-values

```text
```xml <observation>   <type>change</type>   <title>Helm values updated to increase replicas and cap memory</title>   <subtitle>infra/values.yaml raises replicaCount to 3 and sets memory limit to 2Gi</subtitle>   <facts>     <fact>infra/values.yaml replicaCount changed to 3</fact>     <fact>Memory resource limit set to 2Gi</fact>     <fact>helm template renders 3 pods with updated configuration</f... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added Kubernetes rollback script for claude-mem deployment</title>   <subtitle>New rollback.sh script enables quick rollback of claude-mem deployment with status verification</subtitle>   <facts>     <fact>scripts/rollback.sh created with kubectl rollout undo and rollout status commands</fact>     <fact>Script uses set -euo pipefail for strict error ha... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Canary deployment configuration enabled in infra/canary.yaml</title>   <subtitle>Canary strategy configured for 10% traffic with automatic rollback at 2% error threshold.</subtitle>   <facts>     <fact>canary.yaml enables canary deployment with weight: 10 (10% traffic)</fact>     <fact>Error threshold set to 0.02 (2%) triggering automatic rollback</fact... [truncated]
```

### skip-grep-search

```text
```xml <observation>   <type>discovery</type>   <title>hasMore pattern usage identified</title>   <subtitle>Found hasMore implementation in usePagination.ts and three controllers across codebase.</subtitle>   <facts>     <fact>hasMore pattern exists in usePagination.ts file</fact>     <fact>hasMore referenced in three controllers</fact>     <fact>Pattern appears to be related to pagination functio... [truncated]
```

### skip-ls-tree

```text
<skip>Routine directory listing with no changes or deliverables
```

### skip-no-conclusion

```text
```xml <observation>   <type>discovery</type>   <title>PaymentGatewayV3 references found in deprecated module</title>   <subtitle>Grep search located two PaymentGatewayV3 matches within a deprecated module in the repository.</subtitle>   <facts>     <fact>PaymentGatewayV3 identifier has two matches in the repository</fact>     <fact>Both matches are located in a deprecated module</fact>     <fact>... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title hit requires every declared identifier verbatim and keyword coverage ≥ 0.8; keywords are normalized for case, punctuation, CamelCase and a small synonym table.
- Facts/concepts recall is the primary field signal; precision counts produced items that mention an expected keyword. Trace-support rate is measured independently against the recorded tool input/output.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
