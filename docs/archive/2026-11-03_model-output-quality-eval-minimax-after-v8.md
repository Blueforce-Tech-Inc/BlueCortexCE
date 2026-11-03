# Model output quality evaluation

- Date: 2026-11-03
- Phase: after
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 87 unique; runs scored: 3; key repeat: 3; real calls: 113 (cap none); duration: 300480 ms; temperature: 0.0
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
| title exact-hit rate (legacy) | 52% |
| title normalized hit rate (legacy) | 55% |
| title action-class hit rate (A6) | 90% (67 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 73% |
| title subject hit rate | 73% |
| title contract hit rate (action + subject + all identifiers) | 64% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 54% |
| title identifier hit rate | 99% |
| type accuracy | 90% |
| facts keyword precision (legacy) / recall | 78% / 92% |
| facts informative precision | 82% |
| facts trace-support rate | 100% |
| concepts precision / recall | 83% / 83% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 1793 / 5661 / 12800 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 82% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 90% | PASS |
| A6b | title subject | >= 90% | 73% | **FAIL** |
| A6c | title identifiers | = 100% | 99% | **FAIL** |
| A6 | all three title dimensions together | all pass | 64% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 87 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 87 | 100% | 55% | 64% | 78% | 82% | 92% | 0 |
| 1 | 13 | 100% | 50% | 60% | 90% | 100% | 100% | 0 |
| 2 | 13 | 100% | 60% | 80% | 83% | 78% | 100% | 0 |

- average: skip 100%, title 55%, title contract 68%, facts P/R 84%/97%, facts actionable 87%
- worst run: skip 100%, title 50%, title contract 60%, facts P/R 78%/92%, facts actionable 78%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 11/12 | 7/12 |
| refactor | 10 | 10/10 | 8/10 | 6/10 |
| test-add | 9 | 9/9 | 9/9 | 6/9 |
| security-fix | 9 | 9/9 | 8/9 | 4/9 |
| config-change | 12 | 12/12 | 11/12 | 8/12 |
| docs-update | 8 | 8/8 | 8/8 | 2/8 |
| deploy-change | 7 | 7/7 | 5/7 | 4/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 67% | MISS | miss | match | 67%/100% | 67% | 50%/50% | 1978 |
| bug-null-cache | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 1497 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 50%/50% | 1447 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 1793 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/67% | 100% | 100%/100% | 3029 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/67% | 100% | 100%/100% | 1617 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 50%/50% | 3391 |
| bug-race-counter | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 3943 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 25% | MISS | miss | MISS | 100%/100% | 100% | 50%/50% | 1626 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1821 |
| bug-regex-redos | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 1771 |
| bug-file-encoding | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1525 |
| refactor-invoice | refactor | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 1506 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 3463 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 2224 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 2392 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 0%/0% | 100% | 100%/100% | 1797 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | MISS | hit | match | 50%/100% | 50% | 100%/100% | 3893 |
| refactor-sql-repository | refactor | observation | observation | pass | 67% | hit | miss | MISS | 100%/100% | 100% | 50%/50% | 3546 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 0% | 100%/100% | 1687 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 50%/50% | 50% | 100%/100% | 2429 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 4712 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 1665 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 100% | 50%/50% | 1642 |
| test-integration-order | test-add | observation | observation | pass | 50% | MISS | miss | match | 67%/100% | 100% | 50%/50% | 2551 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 67% | 100%/100% | 1487 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 2881 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 100% | 50%/50% | 3469 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 1404 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | MISS | miss | match | 100%/67% | 100% | 100%/100% | 1383 |
| security-redact-log | security-fix | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 100% | 100%/100% | 10586 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | MISS | hit | match | 33%/50% | 33% | 100%/100% | 4209 |
| security-authz-admin | security-fix | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 33% | 100%/100% | 8103 |
| security-csrf | security-fix | observation | observation | pass | 67% | hit | miss | match | 50%/50% | 100% | 100%/100% | 3118 |
| security-secret-config | security-fix | observation | observation | pass | 50% | MISS | miss | MISS | 67%/100% | 100% | 50%/50% | 1928 |
| security-xxe | security-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 2887 |
| security-path-traversal | security-fix | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 2643 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS | miss | match | 33%/100% | 100% | 100%/100% | 5157 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS | miss | match | 50%/100% | 100% | 100%/100% | 1573 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 3153 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 2334 |
| config-log-level | config-change | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 33% | 100%/100% | 3233 |
| config-cors | config-change | observation | observation | pass | 75% | hit | miss | MISS | 67%/100% | 33% | 50%/50% | 5661 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/50% | 1340 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 2845 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 100% | 50%/50% | 1970 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 100% | 50%/50% | 1400 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 4044 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 33% | 100%/100% | 2656 |
| docs-runbook | docs-update | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 33% | 100%/100% | 2521 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 1759 |
| docs-architecture-adr | docs-update | observation | observation | pass | 75% | MISS | miss | match | 50%/100% | 100% | 100%/100% | 1505 |
| docs-migration-guide | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 2975 |
| docs-security-policy | docs-update | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 50% | 50%/50% | 3616 |
| docs-contributing | docs-update | observation | observation | pass | 33% | hit | miss | match | 100%/100% | 50% | 100%/100% | 1626 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 67% | 100%/100% | 1896 |
| deploy-github-actions | deploy-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 2100 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 50%/50% | 1924 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 25% | MISS | miss | MISS | 67%/100% | 67% | 50%/50% | 3415 |
| deploy-helm-values | deploy-change | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 100% | 100%/100% | 2301 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 33% | 100%/100% | 1696 |
| deploy-canary | deploy-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 1664 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1216 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 775 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1126 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1367 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 745 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 936 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1154 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1347 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 875 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 918 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1077 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2544 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 963 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 12800 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 10554 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 991 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 958 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1343 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1279 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1445 |
| remove-dependency | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1512 |
| remove-dead-code | refactor | observation | observation | pass | 50% | MISS | miss | MISS | 100%/100% | 100% | 100%/100% | 1530 |
| remove-stale-config | config-change | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 100% | 100%/100% | 1524 |
| remove-deprecated-endpoint | config-change | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 1679 |
| remove-feature-flag | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 1442 |

## Problems

- bug-offbyone-pagination: title coverage 67% (expected keywords [fix, pagination, orderquery])
- bug-offbyone-pagination: title subject [pagination] missing (got 'Fixed OrderQuery.pageOffset off-by-one on first page')
- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-connection-leak: facts recall 67% (expected [try-with-resources, activeConnections, 0], got [ConnectionPool.borrow now uses try (Connection connection = dataSource.getConnection()) to close the connection when the caller throws., PoolLeakTest reports activeConnections returns to 0 after 100 failing calls.])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-jwt-expiry: facts recall 67% (expected [expiration, isEqual, rejects], got [JwtVerifier checks claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now)., JwtVerifierTest#expiredAtExactBoundary passes after the expiration comparison change.])
- bug-csv-crlf: facts recall 50% (expected [CRLF, multilineFieldKeepsRowCount], got [CsvExporter.java now executes writer.write(line.replaceAll("\\r?\\n", "\\r\\n")) before quoting., CsvExporterTest#multilineFieldKeepsRowCount passes after the CsvExporter.java change.])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: title coverage 75% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: title subject [race] missing (got 'Fixed CounterService concurrent increment with AtomicLong.incrementAndGet')
- bug-n-plus-one: title coverage 25% (expected keywords [fix, n+1, orderrepository, entitygraph])
- bug-n-plus-one: title verb class 'fix' missing (got 'Extended OrderRepository customer orders with item fetch')
- bug-n-plus-one: title subject [n+1] missing (got 'Extended OrderRepository customer orders with item fetch')
- bug-n-plus-one: type 'feature' != expected 'bugfix'
- bug-n-plus-one: concepts recall 50% (expected [problem-solution, pattern], got [what-changed, pattern])
- bug-regex-redos: title coverage 75% (expected keywords [fix, redos, urlvalidator, regex])
- bug-regex-redos: title subject [redos] missing (got 'Fixed UrlValidator nested-quantifier regex performance in UrlValidator.java')
- refactor-invoice: title coverage 67% (expected keywords [refactor, invoiceservice, delegates])
- refactor-invoice: title subject [delegation] missing (got 'Refactored InvoiceService rendering through TaxCalculator and InvoiceRenderer')
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title subject [unmodifiable, map] missing (got 'Refactored ConfigLoader properties collection with typed accessors')
- refactor-retry-policy: facts recall 0% (expected [BackoffStrategy, 100/200/400], got [RetryPolicy.nextDelay(attempt) returns backoff.nextDelay(attempt)., RetryPolicyTest asserts the unchanged delay sequence 100, 200, and 400.])
- refactor-logging-filter: title subject [timing] missing (got 'Refactored LoggingFilter exchange logging into logExchange')
- refactor-sql-repository: title coverage 67% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title verb class 'refactor' missing (got 'Fixed UserRepository tenant query with parameterized JPQL in UserRepository.java')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- refactor-feature-flag: facts recall 50% (expected [Flags.isEnabled, on/off matrix], got [src/main/java/com/acme/flags/FeatureFlagService.java returns flags.isEnabled(flag, context) for flag evaluation., FeatureFlagServiceTest retains the same on/off evaluation matrix after the delegation change.])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles is a @ParameterizedTest using @CsvSource({"1,100","2,200","3,400"})., RetryPolicyTest#delayDoubles asserts policy.delay(attempt) equals 100 for attempt 1, 200 for attempt 2, and 400 for attempt 3., RetryPolicyTest#delayDoubles completed with 3 passing tests.])
- test-unit-retry: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-integration-order: title coverage 50% (expected keywords [add, integration, order, test])
- test-integration-order: title subject [integration, test] missing (got 'Added OrderApiIT#createOrderReturns201 for order creation contract')
- test-integration-order: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable uses @ParameterizedTest with @ValueSource(strings = {"a,b","a\"b","a\nb"})., CsvExporterTest#quotingIsStable covers CSV values containing commas, quotes, and newlines., All 3 CsvExporterTest#quotingIsStable cases pass.])
- test-security-headers: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-repro-null-cache: title lost identifiers [CacheConcurrencyTest, UserCache] (got 'Added CacheConcurrencyTest#concurrentReadersNeverSeeNull concurrent readers regression')
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-contract-openapi: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- test-flaky-clock: title action 'modify' missing: title leads 'create' (got 'Added fixed-clock scheduler test timing in SchedulerTest.java')
- test-flaky-clock: title verb class 'fix' missing (got 'Added fixed-clock scheduler test timing in SchedulerTest.java')
- test-flaky-clock: title subject [flaky, clock] missing (got 'Added fixed-clock scheduler test timing in SchedulerTest.java')
- test-flaky-clock: facts recall 67% (expected [Clock.fixed, Thread.sleep, 20 consecutive], got [SchedulerTest.java configures scheduler.clock with Clock.fixed at 2026-11-03T00:00:00Z and ZoneOffset.UTC., SchedulerTest.java replaces Thread.sleep-based timing with Clock.fixed.])
- security-redact-log: title verb class 'redact' missing (got 'Hardened credential redaction in LogSanitizer')
- security-sql-injection: title action 'secure' missing: title leads 'modify' (got 'Fixed UserDao.findByEmail SQL injection with parameter placeholder')
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail executes jdbcTemplate.query("select * from users where email = ?", mapper, email)., UserDao.findByEmail no longer interpolates email into the SQL query string., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the UserDao change.])
- security-authz-admin: facts recall 50% (expected [ROLE_ADMIN, 403], got [AdminController applies @PreAuthorize("hasRole('ADMIN')") to every endpoint., AdminAuthorizationTest#userRoleGets403 passes for requests from a user role., AdminController is located at src/main/java/com/acme/admin/AdminController.java.])
- security-csrf: title coverage 67% (expected keywords [fix, csrf, csrffilter])
- security-csrf: title verb class 'fix' missing (got 'Hardened CSRF token validation in CsrfFilter')
- security-csrf: facts recall 50% (expected [403, mismatched token], got [src/main/java/com/acme/web/CsrfFilter.java sends HTTP 403 when csrfToken does not match request., CsrfFilterTest#postWithoutTokenIs403 passes for POST requests without a valid token.])
- security-secret-config: title coverage 50% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: title action 'secure' missing: title leads 'modify' (got 'Changed ACME_API_KEY configuration in application.yml')
- security-secret-config: title verb class 'remove' missing (got 'Changed ACME_API_KEY configuration in application.yml')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Changed ACME_API_KEY configuration in application.yml')
- security-secret-config: type 'change' != expected 'bugfix'
- security-secret-config: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, gotcha])
- security-path-traversal: title coverage 75% (expected keywords [fix, path, traversal, filedownloadcontroller])
- security-path-traversal: title verb class 'fix' missing (got 'Hardened download path traversal validation in FileDownloadController')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title action 'secure' missing: title leads 'modify' (got 'Fixed RateLimiter empty bucket response with TooManyRequestsException')
- security-rate-limit: title verb class 'add' missing (got 'Fixed RateLimiter empty bucket response with TooManyRequestsException')
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- config-hikari-pool: title coverage 75% (expected keywords [change, hikari, pool, size])
- config-hikari-pool: title verb class 'change' missing (got 'Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml')
- config-log-level: title coverage 75% (expected keywords [change, logback, root, json])
- config-log-level: title verb class 'change' missing (got 'Configured root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: title subject [log, level] missing (got 'Configured root logger to INFO with JSON appender in logback-spring.xml')
- config-cors: title coverage 75% (expected keywords [restrict, cors, corsconfig, origin])
- config-cors: title verb class 'restrict' missing (got 'Hardened /api/** authorization origin and methods in CorsConfig')
- config-cors: type 'bugfix' != expected 'change'
- config-cors: concepts recall 50% (expected [what-changed, trade-off], got [problem-solution, trade-off])
- config-timeout: concepts recall 50% (expected [what-changed, trade-off], got [trade-off, pattern])
- config-docker-healthcheck: title action 'modify' missing: title leads 'create' (got 'Added container healthcheck for /actuator/health in Dockerfile')
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-maven-plugin: title action 'modify' missing: title leads 'create' (got 'Added jacoco-maven-plugin 0.8.12 to pom.xml')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Documented local setup steps in README.md')
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title verb class 'add' missing (got 'Documented queue recovery procedure in docs/runbook.md')
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title verb class 'change' missing (got 'Documented 2.4.0 release highlights in CHANGELOG.md')
- docs-architecture-adr: title coverage 75% (expected keywords [record, adr, extraction, validation])
- docs-architecture-adr: title subject [adr] missing (got 'Recorded extraction validation decision in 0007-extraction-validation.md')
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [what-changed, pattern])
- docs-security-policy: title coverage 50% (expected keywords [add, security, policy, disclosure])
- docs-security-policy: title verb class 'add' missing (got 'Documented vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: title subject [security, policy] missing (got 'Documented vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 33% (expected keywords [update, contributing, verify-local])
- docs-contributing: title verb class 'change' missing (got 'Documented local verification requirement in CONTRIBUTING.md')
- deploy-k8s-probe: title action 'modify' missing: title leads 'create' (got 'Added readiness probe for /api/health in deployment.yaml')
- deploy-github-actions: title coverage 75% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: title verb class 'add' missing (got 'Extended CI Java matrix to JDK 21 and 25 in ci.yml')
- deploy-docker-multistage: concepts recall 50% (expected [what-changed, trade-off], got [trade-off, pattern])
- deploy-terraform-iam: title coverage 25% (expected keywords [restrict, iam, terraform, s3])
- deploy-terraform-iam: title verb class 'restrict' missing (got 'Hardened deploy role S3 authorization in infra/main.tf')
- deploy-terraform-iam: title subject [iam, policy] missing (got 'Hardened deploy role S3 authorization in infra/main.tf')
- deploy-terraform-iam: type 'bugfix' != expected 'change'
- deploy-terraform-iam: concepts recall 50% (expected [what-changed, trade-off], got [problem-solution, trade-off])
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 75% (expected keywords [enable, canary, rollback, threshold])
- remove-dead-code: title coverage 50% (expected keywords [remove, dead, code, orderservice])
- remove-dead-code: title subject [dead, code] missing (got 'Removed legacySync synchronization method in OrderService')
- remove-dead-code: type 'change' != expected 'refactor'
- remove-stale-config: title subject [stale, setting] missing (got 'Removed legacy-search.enabled from application.yml')
- remove-deprecated-endpoint: title coverage 50% (expected keywords [remove, deprecated, endpoint, usercontroller])
- remove-deprecated-endpoint: title subject [deprecated, endpoint] missing (got 'Removed legacy /v1/users mapping in UserController')
- remove-feature-flag: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset off-by-one on first page</title>   <subtitle>OrderQuery now calculates the first-page offset as zero instead of negative one.</subtitle>   <facts>     <fact>OrderQuery.pageOffset now returns page * size without subtracting 1.</fact>     <fact>OrderQueryTest#firstPageStartsAtZero passes with the corrected offset calculation.</... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for off-by-one-day bug</title>   <subtitle>The regression test pins a UTC instant to its Asia/Shanghai business date.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins 2026-11-01T23:30Z and expects the Asia/Shanghai business date 2026-11-02.</fact>     <fact>DailyReportTest#r... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed missing order lookup result in PaymentService</title>   <subtitle>PaymentService now throws PaymentNotFoundException when orderId is absent.</subtitle>   <facts>     <fact>PaymentService returns repository.findByOrderId(orderId).orElseThrow(() -> new PaymentNotFoundException(orderId)) for missing orders.</fact>     <fact>PaymentService throws Paym... [truncated]
```

### bug-connection-leak

```text
<observation>   <type>bugfix</type>   <title>Fixed ConnectionPool.borrow connection leaks on caller exceptions</title>   <subtitle>try-with-resources returns pooled connections after failing calls, preventing active connection accumulation.</subtitle>   <facts>     <fact>ConnectionPool.borrow now uses try (Connection connection = dataSource.getConnection()) to close the connection when the caller ... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration boundary equality in JwtVerifier.java</title>   <subtitle>JwtVerifier now rejects tokens whose expiration is exactly equal to the current instant.</subtitle>   <facts>     <fact>JwtVerifier checks claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now).</fact>     <fact>JwtVerifierTest#expiredAtExactBound... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed embedded line endings in CsvExporter.java</title>   <subtitle>CsvExporter normalizes multiline field endings to CRLF before quoting, preserving CSV row counts.</subtitle>   <facts>     <fact>CsvExporter.java now executes writer.write(line.replaceAll("\\r?\\n", "\\r\\n")) before quoting.</fact>     <fact>CsvExporterTest#multilineFieldKeepsRowCount ... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService concurrent increment with AtomicLong.incrementAndGet</title>   <subtitle>Atomic increments eliminate lost updates under concurrent calls, with 1,000 increments verified.</subtitle>   <facts>     <fact>CounterService.increment returns counter.incrementAndGet() instead of counter.set(counter.get() + 1); return counter.get().</fact>   ... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>feature</type>   <title>Extended OrderRepository customer orders with item fetch</title>   <subtitle>Customer orders now load order items through a single database query.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId(String customerId) uses @EntityGraph(attributePaths = "items").</fact>     <fact>QueryCountTest observes 1 SQL statement for customer order ... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator nested-quantifier regex performance in UrlValidator.java</title>   <subtitle>UrlValidator now uses a linear URL pattern, and long-input completion remains below 100ms.</subtitle>   <facts>     <fact>UrlValidator.java defines URL as Pattern.compile("^https?://[^\\s]+$").</fact>     <fact>UrlValidator replaced the previous nested-quanti... [truncated]
```

### refactor-invoice

```text
<observation>   <type>refactor</type>   <title>Refactored InvoiceService rendering through TaxCalculator and InvoiceRenderer</title>   <subtitle>InvoiceService now delegates tax calculation and invoice rendering to dedicated collaborators without changing behavior.</subtitle>   <facts>     <fact>src/main/java/com/acme/billing/InvoiceService.java calls renderer.render(taxCalculator.calculate(invoic... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>refactor</type>   <title>Refactored ConfigLoader properties collection with typed accessors</title>   <subtitle>ConfigLoader now exposes an unmodifiable properties map and typed configuration accessors.</subtitle>   <facts>     <fact>ConfigLoader.java returns properties.entrySet() as an unmodifiable map.</fact>     <fact>ConfigLoader exposes typed accessors for configuration ... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Refactored RetryPolicy delay calculation through BackoffStrategy</title>   <subtitle>RetryPolicy now delegates exponential delay computation to BackoffStrategy while preserving the expected retry sequence.</subtitle>   <facts>     <fact>RetryPolicy.nextDelay(attempt) returns backoff.nextDelay(attempt).</fact>     <fact>RetryPolicyTest asserts the unch... [truncated]
```

### refactor-logging-filter

```text
<observation>   <type>refactor</type>   <title>Refactored LoggingFilter exchange logging into logExchange</title>   <subtitle>LoggingFilter now starts timing before exchange processing and captures headers through logExchange.</subtitle>   <facts>     <fact>LoggingFilter.java passes StopWatch.createStarted() to logExchange(exchange, StopWatch.createStarted()).</fact>     <fact>LoggingFilterTest ve... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Fixed UserRepository tenant query with parameterized JPQL in UserRepository.java</title>   <subtitle>UserRepository now filters users by tenantId using a JPQL @Query instead of concatenated SQL.</subtitle>   <facts>     <fact>UserRepository.java uses @Query("select u from User u where u.tenantId = :tenantId").</fact>     <fact>UserRepositoryTest returns... [truncated]
```

### refactor-feature-flag

```text
<observation>   <type>refactor</type>   <title>Refactored FeatureFlagService evaluation with Flags.isEnabled delegation</title>   <subtitle>FeatureFlagService now delegates flag evaluation to Flags.isEnabled while preserving the existing test matrix.</subtitle>   <facts>     <fact>src/main/java/com/acme/flags/FeatureFlagService.java returns flags.isEnabled(flag, context) for flag evaluation.</fact... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for UTC-vs-business-date bug</title>   <subtitle>Regression test expects the Asia/Shanghai business date to advance across the UTC midnight boundary.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone passes Instant.parse("2026-11-01T23:30:00Z") to report.dateAt with ZoneId.of("Asi... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added locked-account 423 regression test in LoginControllerTest</title>   <subtitle>LoginControllerTest#lockedAccountReturns423 reproduces locked accounts incorrectly receiving HTTP 401.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 mocks users.isLocked("bob") to return true.</fact>     <fact>LoginControllerTest#lockedAccou... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized exponential-backoff cases</title>   <subtitle>RetryPolicyTest now pins retry delays of 100 ms, 200 ms, and 400 ms for attempts 1 through 3.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles is a @ParameterizedTest using @CsvSource({"1,100","2,200","3,400"}).</fact>     <fact>RetryPolicyTest#del... [truncated]
```

### test-integration-order

```text
<observation>   <type>feature</type>   <title>Added OrderApiIT#createOrderReturns201 for order creation contract</title>   <subtitle>Integration test verifies successful order creation, HTTP 201, and the Location response header using PostgreSQL 16.</subtitle>   <facts>     <fact>src/test/java/com/acme/orders/OrderApiIT.java defines OrderApiIT#createOrderReturns201.</fact>     <fact>OrderApiIT#cre... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable parameterized quoting cases</title>   <subtitle>New parameterized cases verify stable CSV quoting for commas, quotes, and newlines.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable uses @ParameterizedTest with @ValueSource(strings = {"a,b","a\"b","a\nb"}).</fact>     <fact>CsvExporterTest#quotingIsSta... [truncated]
```

### test-security-headers

```text
<observation>   <type>feature</type>   <title>Added SecurityHeadersTest#responseHasCspAndNosniff security header assertions</title>   <subtitle>The new test verifies CSP and nosniff headers on the root response.</subtitle>   <facts>     <fact>SecurityHeadersTest#responseHasCspAndNosniff asserts that GET / returns Content-Security-Policy containing default-src 'self'.</fact>     <fact>SecurityHeade... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull concurrent readers regression</title>   <subtitle>Parallel reader regression test reproduces intermittent null values and passes after the UserCache fix.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull starts 1000 parallel readers and asserts cache.get("k") is no... [truncated]
```

### test-contract-openapi

```text
<observation>   <type>feature</type>   <title>Added OpenApiContractTest#generatedSpecMatchesCommittedFile for schema drift</title>   <subtitle>Contract test compares springdoc output with the committed OpenAPI specification.</subtitle>   <facts>     <fact>OpenApiContractTest#generatedSpecMatchesCommittedFile was added in src/test/java/com/acme/api/OpenApiContractTest.java.</fact>     <fact>OpenApi... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Added fixed-clock scheduler test timing in SchedulerTest.java</title>   <subtitle>SchedulerTest now uses a deterministic fixed clock instead of Thread.sleep to prevent flaky execution.</subtitle>   <facts>     <fact>SchedulerTest.java configures scheduler.clock with Clock.fixed at 2026-11-03T00:00:00Z and ZoneOffset.UTC.</fact>     <fact>SchedulerTest.... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Hardened credential redaction in LogSanitizer</title>   <subtitle>LogSanitizer now masks password, token, and secret values before log output.</subtitle>   <facts>     <fact>LogSanitizer uses the regex `(password|token|secret)=\\S+` and replaces each credential value with `$1=***`.</fact>     <fact>LogSanitizerTest#redactsCredentials passes for the `pas... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed UserDao.findByEmail SQL injection with parameter placeholder</title>   <subtitle>UserDao now binds email through JDBC while preserving empty results for quoted input.</subtitle>   <facts>     <fact>UserDao.findByEmail executes jdbcTemplate.query("select * from users where email = ?", mapper, email).</fact>     <fact>UserDao.findByEmail no longer i... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Hardened AdminController endpoint authorization with hasRole('ADMIN')</title>   <subtitle>AdminController now restricts every endpoint to users holding the ADMIN role.</subtitle>   <facts>     <fact>AdminController applies @PreAuthorize("hasRole('ADMIN')") to every endpoint.</fact>     <fact>AdminAuthorizationTest#userRoleGets403 passes for requests fro... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Hardened CSRF token validation in CsrfFilter</title>   <subtitle>CsrfFilter now rejects state-changing requests when the CSRF token does not match the request.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/CsrfFilter.java sends HTTP 403 when csrfToken does not match request.</fact>     <fact>CsrfFilterTest#postWithoutTokenIs403 passes for PO... [truncated]
```

### security-secret-config

```text
<observation>   <type>change</type>   <title>Changed ACME_API_KEY configuration in application.yml</title>   <subtitle>application.yml now resolves the API key from the environment instead of storing a literal secret.</subtitle>   <facts>     <fact>src/main/resources/application.yml replaces api-key: sk-live-123 with api-key: ${ACME_API_KEY}.</fact>     <fact>src/main/resources/application.yml no ... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Hardened download path traversal validation in FileDownloadController</title>   <subtitle>FileDownloadController rejects normalized download paths that escape the configured base directory.</subtitle>   <facts>     <fact>FileDownloadController resolves and normalizes base.resolve(name) into the resolved path.</fact>     <fact>FileDownloadController thro... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter empty bucket response with TooManyRequestsException</title>   <subtitle>RateLimiter now rejects requests with 429 after the token bucket exhausts.</subtitle>   <facts>     <fact>RateLimiter throws TooManyRequestsException when bucket.tryConsume(1) returns false.</fact>     <fact>RateLimiter returns HTTP 429 when the token bucket is emp... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>Session cookies now enforce HttpOnly, Secure, and SameSite=Strict protections.</subtitle>   <facts>     <fact>SessionCookieConfig sets cookie.setHttpOnly(true).</fact>     <fact>SessionCookieConfig sets cookie.setSecure(true).</fact>     <fact>SessionCookieConfig sets cookie.setSam... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml</title>   <subtitle>HikariCP connection pool sizing now reserves 20 maximum connections and maintains 5 idle connections.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20.</fact>     <fact>src/main/resources/... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Configured root logger to INFO with JSON appender in logback-spring.xml</title>   <subtitle>Root logging now uses INFO level and routes records through the JSON appender.</subtitle>   <facts>     <fact>src/main/resources/logback-spring.xml sets the root logger level to INFO.</fact>     <fact>src/main/resources/logback-spring.xml references the JSON appe... [truncated]
```

### config-cors

```text
<observation>   <type>bugfix</type>   <title>Hardened /api/** authorization origin and methods in CorsConfig</title>   <subtitle>CORS now permits only the production app origin and GET or POST API requests.</subtitle>   <facts>     <fact>CorsConfig maps /api/** to allowedOrigins("https://app.acme.com").</fact>     <fact>CorsConfig permits only GET and POST methods for /api/**.</fact>     <fact>Cor... [truncated]
```

### config-timeout

```text
<observation>   <type>change</type>   <title>Set WebClient responseTimeout to 5 seconds in WebClientConfig</title>   <subtitle>WebClient requests now terminate after a five-second response timeout.</subtitle>   <facts>     <fact>WebClientConfig configures HttpClient.create().responseTimeout(Duration.ofSeconds(5)).</fact>     <fact>WebClientConfigTest reads the response timeout from the WebClient b... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added container healthcheck for /actuator/health in Dockerfile</title>   <subtitle>Docker containers now run curl every 30 seconds against the application health endpoint.</subtitle>   <facts>     <fact>Dockerfile defines HEALTHCHECK with --interval=30s.</fact>     <fact>Dockerfile healthcheck runs curl -f http://localhost:8080/actuator/health and exits... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 to pom.xml</title>   <subtitle>Maven verification now generates JaCoCo coverage reports during the build lifecycle.</subtitle>   <facts>     <fact>pom.xml adds org.jacoco:jacoco-maven-plugin with version 0.8.12.</fact>     <fact>mvn verify writes the JaCoCo report to target/site/jacoco/jacoco.xml.</fact>   </facts>   <na... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Documented local setup steps in README.md</title>   <subtitle>README.md now provides a three-step setup using JDK 21, PostgreSQL Docker Compose, and Maven.</subtitle>   <facts>     <fact>README.md requires JDK 21 for local setup.</fact>     <fact>README.md starts PostgreSQL with docker compose up -d postgres.</fact>     <fact>README.md runs the applicat... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Documented queue recovery procedure in docs/runbook.md</title>   <subtitle>Runbook defines queue recovery through pending_messages inspection, worker restart, and health verification.</subtitle>   <facts>     <fact>docs/runbook.md lists checking pending_messages as the first queue recovery step.</fact>     <fact>docs/runbook.md instructs operators to re... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Documented 2.4.0 release highlights in CHANGELOG.md</title>   <subtitle>The changelog records offline replay evaluation and the V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md contains a `2.4.0 - 2026-11-03` release entry.</fact>     <fact>CHANGELOG.md lists `Added offline replay evaluation` under version 2.4.0.</fact>     <fact>... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>decision</type>   <title>Recorded extraction validation decision in 0007-extraction-validation.md</title>   <subtitle>ADR 0007 requires extracted facts to be validated against tool output before persistence.</subtitle>   <facts>     <fact>docs/adr/0007-extraction-validation.md records the decision to validate extracted facts against tool output before persistence.</fact>     ... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration sentinel prerequisite in docs/MIGRATION.md</title>   <subtitle>V20 migration now requires a mem_sessions backup and manual sentinel review configuration.</subtitle>   <facts>     <fact>docs/MIGRATION.md requires backing up mem_sessions before running the V20 migration.</fact>     <fact>docs/MIGRATION.md requires app.manual_senti... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Documented vulnerability disclosure channel in SECURITY.md</title>   <subtitle>SECURITY.md directs vulnerability reports to security@acme.com and prohibits public issues.</subtitle>   <facts>     <fact>SECURITY.md specifies security@acme.com as the vulnerability reporting channel.</fact>     <fact>SECURITY.md forbids opening public issues for vulnerabil... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Documented local verification requirement in CONTRIBUTING.md</title>   <subtitle>CONTRIBUTING.md requires running the local verification script before opening a pull request.</subtitle>   <facts>     <fact>CONTRIBUTING.md requires `./scripts/verify-local.sh` before opening a pull request.</fact>     <fact>CONTRIBUTING.md names four test layers in the lo... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added readiness probe for /api/health in deployment.yaml</title>   <subtitle>Kubernetes readiness checks now target the health endpoint after a 15-second startup delay.</subtitle>   <facts>     <fact>deployment.yaml defines readinessProbe.httpGet.path as /api/health and readinessProbe.httpGet.port as 37777.</fact>     <fact>deployment.yaml sets readines... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Extended CI Java matrix to JDK 21 and 25 in ci.yml</title>   <subtitle>The backend CI workflow now tests against Java 21 and Java 25, and its syntax check passes.</subtitle>   <facts>     <fact>.github/workflows/ci.yml strategy.matrix.java is configured as [21, 25].</fact>     <fact>The ci.yml workflow syntax check passes with the Java 21 and 25 matrix ... [truncated]
```

### deploy-docker-multistage

```text
<observation>   <type>change</type>   <title>Converted Dockerfile to multi-stage eclipse-temurin:21-jre runtime</title>   <subtitle>Dockerfile now builds with Maven and ships only app.jar in a Java 21 runtime image.</subtitle>   <facts>     <fact>Dockerfile defines a multi-stage build with a Maven build stage and an eclipse-temurin:21-jre runtime stage.</fact>     <fact>Dockerfile copies /app/targ... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>bugfix</type>   <title>Hardened deploy role S3 authorization in infra/main.tf</title>   <subtitle>aws_iam_role_policy.deploy now grants read-only S3 access instead of full S3 access.</subtitle>   <facts>     <fact>infra/main.tf defines aws_iam_role_policy.deploy with policy_arn = "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess".</fact>     <fact>aws_iam_role_policy.deploy pre... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added deployment rollback script scripts/rollback.sh</title>   <subtitle>Rollback script undoes the claude-mem deployment and waits for rollout completion.</subtitle>   <facts>     <fact>scripts/rollback.sh runs `kubectl rollout undo deployment/claude-mem`.</fact>     <fact>scripts/rollback.sh runs `kubectl rollout status deployment/claude-mem` after t... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Enabled 10 percent canary with 2 percent error threshold in infra/canary.yaml</title>   <subtitle>Canary traffic is enabled at 10 percent and rolls back when the error rate exceeds 2 percent.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true.</fact>     <fact>infra/canary.yaml sets canary.weight to 10 for a 10 percent canary r... [truncated]
```

### remove-dead-code

```text
<observation>   <type>change</type>   <title>Removed legacySync synchronization method in OrderService</title>   <subtitle>OrderService no longer exposes the legacy warehouse synchronization entry point.</subtitle>   <facts>     <fact>OrderService.legacySync was removed from src/main/java/com/acme/orders/OrderService.java.</fact>     <fact>No remaining references to OrderService.legacySync were fo... [truncated]
```

### remove-stale-config

```text
<observation>   <type>change</type>   <title>Removed legacy-search.enabled from application.yml</title>   <subtitle>Search configuration now relies exclusively on the search-v2 module.</subtitle>   <facts>     <fact>src/main/resources/application.yml no longer defines legacy-search.enabled.</fact>     <fact>The search module reads only search-v2 after removal of legacy-search.enabled.</fact>   </f... [truncated]
```

### remove-deprecated-endpoint

```text
<observation>   <type>change</type>   <title>Removed legacy /v1/users mapping in UserController</title>   <subtitle>UserController now exposes only the remaining /v2/users endpoint.</subtitle>   <facts>     <fact>UserController.listLegacy was removed from src/main/java/com/acme/users/UserController.java.</fact>     <fact>The @GetMapping("/v1/users") endpoint was removed from UserController.</fact>... [truncated]
```

### remove-feature-flag

```text
<observation>   <type>change</type>   <title>Removed FeatureFlags.LEGACY_SEARCH in FeatureFlags.java</title>   <subtitle>Legacy search flag was removed so callers now use the SEARCH_V2 feature flag.</subtitle>   <facts>     <fact>FeatureFlags.LEGACY_SEARCH constant was removed from src/main/java/com/acme/flags/FeatureFlags.java.</fact>     <fact>Feature flag callers now use SEARCH_V2 instead of th... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared verb class, (2) the declared subject tokens to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. The legacy keyword-coverage metric is reported alongside for before/after comparison.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
