# Model output quality evaluation

- Date: 2026-11-03
- Phase: after
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 218819 ms; temperature: 0.0
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
| title exact-hit rate (legacy) | 52% |
| title normalized hit rate (legacy) | 55% |
| title verb-class hit rate | 76% |
| title subject hit rate | 81% |
| title contract hit rate (verb + subject + all identifiers) | 61% |
| title identifier hit rate | 97% |
| type accuracy | 95% |
| facts keyword precision (legacy) / recall | 75% / 90% |
| facts informative precision | 75% |
| facts trace-support rate | 100% |
| concepts precision / recall | 85% / 85% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 1559 / 3364 / 24461 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 75% | PASS |
| A6 | title contract hit (verb + subject + identifiers) | >= 80% | 61% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 82 | 100% | 55% | 61% | 75% | 75% | 90% | 0 |
| 1 | 13 | 100% | 40% | 40% | 97% | 97% | 100% | 0 |
| 2 | 13 | 100% | 70% | 60% | 93% | 93% | 97% | 0 |

- average: skip 100%, title 55%, title contract 54%, facts P/R 88%/96%, facts actionable 88%
- worst run: skip 100%, title 40%, title contract 40%, facts P/R 75%/90%, facts actionable 75%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 12/12 | 8/12 |
| refactor | 9 | 9/9 | 8/9 | 6/9 |
| test-add | 9 | 9/9 | 9/9 | 7/9 |
| security-fix | 9 | 9/9 | 8/9 | 4/9 |
| config-change | 8 | 8/8 | 8/8 | 4/8 |
| docs-update | 8 | 8/8 | 8/8 | 2/8 |
| deploy-change | 7 | 7/7 | 6/7 | 3/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 2934 |
| bug-null-cache | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 1459 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 2111 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 1604 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1484 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 1556 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 1632 |
| bug-race-counter | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/67% | 100% | 100%/100% | 2774 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1455 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1827 |
| bug-regex-redos | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 1761 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 1932 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 100% | 100%/100% | 1559 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 1639 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | MISS | miss | match | 67%/100% | 33% | 100%/100% | 1490 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 1463 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 0%/0% | 100% | 100%/100% | 1444 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 100%/100% | 24461 |
| refactor-sql-repository | refactor | observation | observation | pass | 33% | MISS | miss | MISS | 100%/50% | 50% | 50%/50% | 1357 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 0%/0% | 50% | 100%/100% | 3057 |
| refactor-feature-flag | refactor | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 50% | 100%/100% | 4782 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/67% | 100% | 50%/50% | 2491 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 2073 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/50% | 67% | 100%/100% | 2557 |
| test-integration-order | test-add | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 1653 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 33% | 100%/100% | 2735 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/50% | 3036 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 100% | 50%/50% | 1568 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 50%/50% | 1287 |
| test-flaky-clock | test-add | observation | observation | pass | 100% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 1647 |
| security-redact-log | security-fix | observation | observation | pass | 100% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 2696 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 3449 |
| security-authz-admin | security-fix | observation | observation | pass | 33% | MISS | miss | match | 50%/50% | 50% | 100%/100% | 2671 |
| security-csrf | security-fix | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 1670 |
| security-secret-config | security-fix | observation | observation | pass | 50% | MISS | miss | MISS | 67%/100% | 100% | 50%/50% | 2357 |
| security-xxe | security-fix | observation | observation | pass | 67% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 3364 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 1473 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS | miss | match | 33%/100% | 100% | 100%/100% | 1523 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 100% | 50%/50% | 2739 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 1534 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 1345 |
| config-log-level | config-change | observation | observation | pass | 75% | MISS | miss | match | 33%/50% | 0% | 100%/100% | 1628 |
| config-cors | config-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 100%/100% | 2267 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 2535 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1399 |
| config-docker-healthcheck | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 1567 |
| config-maven-plugin | config-change | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 1999 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 1461 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 33% | 100%/100% | 2334 |
| docs-runbook | docs-update | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 0% | 100%/100% | 2221 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 1347 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 2355 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 1300 |
| docs-security-policy | docs-update | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 50% | 50%/50% | 1413 |
| docs-contributing | docs-update | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 50% | 100%/100% | 1428 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | hit | hit | match | 75%/100% | 50% | 100%/100% | 2853 |
| deploy-github-actions | deploy-change | observation | observation | pass | 75% | MISS | miss | match | 50%/100% | 100% | 50%/50% | 1402 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 100%/100% | 1644 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 67% | 100%/100% | 2525 |
| deploy-helm-values | deploy-change | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 100% | 100%/100% | 1428 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | MISS | hit | MISS | 67%/100% | 33% | 50%/50% | 1455 |
| deploy-canary | deploy-change | observation | observation | pass | 50% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 2403 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 747 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 838 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1604 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 791 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1525 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 756 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 855 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1114 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 842 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 884 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 792 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 741 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4567 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 926 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 871 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1052 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1145 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1125 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 822 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 803 |

## Problems

- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: title coverage 75% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: title subject [race] missing (got 'Fixed CounterService concurrent increment with AtomicLong.incrementAndGet')
- bug-race-counter: facts recall 67% (expected [incrementAndGet, AtomicLong, 1000], got [CounterService.increment uses counter.incrementAndGet() instead of counter.set(counter.get() + 1)., CounterServiceConcurrencyTest passes with 1000 threads producing a counter value of 1000.])
- bug-regex-redos: title coverage 75% (expected keywords [fix, redos, urlvalidator, regex])
- bug-regex-redos: title subject [redos] missing (got 'Fixed UrlValidator nested-quantifier regex with linear URL pattern')
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed FileReaderFactory locale-dependent decoding with explicit UTF-8')
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title subject [unmodifiable, map] missing (got 'Refactored ConfigLoader configuration map with typed accessors')
- refactor-retry-policy: facts recall 0% (expected [BackoffStrategy, 100/200/400], got [RetryPolicy returns backoff.nextDelay(attempt) instead of calculating the delay inline., RetryPolicyTest asserts the unchanged delay sequence 100, 200, and 400.])
- refactor-sql-repository: title coverage 33% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title verb class 'refactor' missing (got 'Fixed tenant query SQL injection risk in UserRepository')
- refactor-sql-repository: title subject [jpql] missing (got 'Fixed tenant query SQL injection risk in UserRepository')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [UserRepository defines `@Query("select u from User u where u.tenantId = :tenantId")`., UserRepositoryTest returns the same tenant rows after the query change.])
- refactor-sql-repository: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- refactor-event-bus: facts recall 0% (expected [listener map, event type], got [src/main/java/com/acme/events/EventBus.java uses listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event))., EventBus dispatch behavior remains covered by EventBusTest, which dispatches the same three events.])
- refactor-feature-flag: title coverage 67% (expected keywords [refactor, featureflagservice, delegates])
- refactor-feature-flag: title subject [delegation] missing (got 'Refactored FeatureFlagService evaluation to Flags.isEnabled')
- test-repro-timezone: facts recall 67% (expected [Asia/Shanghai, 2026-11-02, 1 failure], got [DailyReportTest#reportUsesBusinessTimezone calls report.dateAt with 2026-11-01T23:30:00Z and ZoneId.of("Asia/Shanghai")., DailyReportTest#reportUsesBusinessTimezone expects LocalDate.of(2026, 11, 2)., mvn -Dtest=DailyReportTest test reports 1 test and 1 failure because the actual date is 2026-11-01.])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles uses @ParameterizedTest and @CsvSource with cases 1/100, 2/200, and 3/400., RetryPolicyTest#delayDoubles asserts that policy.delay(attempt) equals 100 ms, 200 ms, and 400 ms for attempts 1, 2, and 3., Three RetryPolicyTest tests pass.])
- test-integration-order: title coverage 75% (expected keywords [add, integration, order, test])
- test-integration-order: title subject [integration, test] missing (got 'Added OrderApiIT#createOrderReturns201 integration coverage')
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable is a parameterized test using @ValueSource with "a,b", "a\"b", and "a\nb"., CsvExporterTest#quotingIsStable covers CSV values containing commas, quotes, and newlines., All 3 CsvExporterTest#quotingIsStable cases pass.])
- test-security-headers: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-repro-null-cache: title lost identifiers [CacheConcurrencyTest, UserCache] (got 'Added CacheConcurrencyTest#concurrentReadersNeverSeeNull for concurrent null reads')
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-contract-openapi: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-flaky-clock: title verb class 'fix' missing (got 'Added fixed Clock to SchedulerTest to remove Thread.sleep flakiness')
- security-redact-log: title verb class 'redact' missing (got 'Fixed credential redaction in LogSanitizer')
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [src/main/java/com/acme/users/UserDao.java uses jdbcTemplate.query("select * from users where email = ?", mapper, email)., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the UserDao.findByEmail change.])
- security-authz-admin: title coverage 33% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Restricted AdminController endpoints to ROLE_ADMIN in AdminController.java')
- security-authz-admin: title subject [authorization] missing (got 'Restricted AdminController endpoints to ROLE_ADMIN in AdminController.java')
- security-authz-admin: facts recall 50% (expected [ROLE_ADMIN, 403], got [AdminController applies @PreAuthorize("hasRole('ADMIN')") in src/main/java/com/acme/admin/AdminController.java., AdminAuthorizationTest#userRoleGets403 passes after AdminController authorization was added.])
- security-csrf: facts recall 50% (expected [403, mismatched token], got [src/main/java/com/acme/web/CsrfFilter.java calls response.sendError(403) when csrfToken.matches(request) returns false., CsrfFilterTest#postWithoutTokenIs403 passes after the CsrfFilter mismatch check.])
- security-secret-config: title coverage 50% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: title verb class 'remove' missing (got 'Changed API key to ACME_API_KEY environment variable in application.yml')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Changed API key to ACME_API_KEY environment variable in application.yml')
- security-secret-config: type 'change' != expected 'bugfix'
- security-secret-config: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, gotcha])
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-xxe: title verb class 'harden' missing (got 'Fixed XXE by disabling DOCTYPE declarations in XmlImportService')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title verb class 'add' missing (got 'Fixed RateLimiter empty bucket response with TooManyRequestsException')
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- security-cookie-httponly: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- config-hikari-pool: title coverage 75% (expected keywords [change, hikari, pool, size])
- config-hikari-pool: title verb class 'change' missing (got 'Set Hikari pool limits maximum-pool-size 20 and minimum-idle 5')
- config-log-level: title coverage 75% (expected keywords [change, logback, root, json])
- config-log-level: title verb class 'change' missing (got 'Set root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: title subject [log, level] missing (got 'Set root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: facts recall 50% (expected [INFO, JSON appender], got [src/main/resources/logback-spring.xml defines the root logger with level="INFO"., src/main/resources/logback-spring.xml routes the root logger through appender-ref ref="JSON"., LogbackConfigTest parses src/main/resources/logback-spring.xml.])
- config-docker-healthcheck: title coverage 75% (expected keywords [add, docker, healthcheck, actuator])
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-maven-plugin: title coverage 75% (expected keywords [add, jacoco, plugin, pom])
- config-maven-plugin: title verb class 'add' missing (got 'Configured jacoco-maven-plugin 0.8.12 in pom.xml')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title verb class 'add' missing (got 'Documented queue recovery procedure in docs/runbook.md')
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-migration-guide: title coverage 75% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [pattern, gotcha])
- docs-security-policy: title coverage 50% (expected keywords [add, security, policy, disclosure])
- docs-security-policy: title verb class 'add' missing (got 'Documented vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: title subject [security, policy] missing (got 'Documented vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 67% (expected keywords [update, contributing, verify-local])
- docs-contributing: title verb class 'change' missing (got 'Documented verify-local.sh pull request requirement in CONTRIBUTING.md')
- deploy-github-actions: title coverage 75% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: title verb class 'add' missing (got 'Changed CI backend matrix to JDK 21 and 25 in ci.yml')
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [trade-off, pattern])
- deploy-terraform-iam: title coverage 75% (expected keywords [restrict, iam, terraform, s3])
- deploy-rollback-script: title lost identifiers [rollback.sh] (got 'Added claude-mem deployment rollback script')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-rollback-script: concepts recall 50% (expected [what-changed, pattern], got [pattern, trade-off])
- deploy-canary: title coverage 50% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: title verb class 'enable' missing (got 'Changed canary rollout weight and error threshold in infra/canary.yaml')

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset off-by-one in pagination</title>   <subtitle>OrderQuery.pageOffset now calculates the page offset without subtracting one.</subtitle>   <facts>     <fact>OrderQuery.pageOffset returns page * size instead of page * size - 1.</fact>     <fact>OrderQueryTest#firstPageStartsAtZero passes after the pagination offset correction.</f... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for off-by-one-day bug</title>   <subtitle>Regression test verifies Asia/Shanghai business-date conversion across the UTC date boundary.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins 2026-11-01T23:30Z and expects the Asia/Shanghai business date 2026-11-02.</fact>     <fac... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed missing order lookup to throw PaymentNotFoundException in PaymentService</title>   <subtitle>PaymentService now rejects unknown orderId values instead of returning null.</subtitle>   <facts>     <fact>PaymentService resolves repository.findByOrderId(orderId) with orElseThrow(() -&gt; new PaymentNotFoundException(orderId)).</fact>     <fact>Payment... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration equality boundary at current instant</title>   <subtitle>JwtVerifier now rejects tokens expiring exactly when validation occurs.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/JwtVerifier.java checks claims.getExpiration().isBefore(now) or claims.getExpiration().isEqual(now).</fact>     <fact>JwtVerifierTest#expi... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsvExporter embedded line endings to CRLF</title>   <subtitle>CsvExporter normalizes embedded line endings before quoting, preserving multiline field row counts.</subtitle>   <facts>     <fact>CsvExporter.java now writes `writer.write(line.replaceAll("\\r?\\n", "\\r\\n"));`.</fact>     <fact>CsvExporter normalizes embedded line endings to CRLF bef... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService concurrent increment with AtomicLong.incrementAndGet</title>   <subtitle>CounterService now performs atomic increments, preventing lost updates under concurrent access.</subtitle>   <facts>     <fact>CounterService.increment uses counter.incrementAndGet() instead of counter.set(counter.get() + 1).</fact>     <fact>CounterServiceConc... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator nested-quantifier regex with linear URL pattern</title>   <subtitle>UrlValidator now validates HTTP(S) URLs without catastrophic backtracking under long input.</subtitle>   <facts>     <fact>src/main/java/com/acme/validation/UrlValidator.java defines URL as Pattern.compile("^https?://[^\\s]+$").</fact>     <fact>UrlValidator replaced ... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed FileReaderFactory locale-dependent decoding with explicit UTF-8</title>   <subtitle>FileReaderFactory now decodes files consistently as UTF-8 regardless of the JVM platform default.</subtitle>   <facts>     <fact>src/main/java/com/acme/files/FileReaderFactory.java constructs InputStreamReader with StandardCharsets.UTF_8.</fact>     <fact>FileReade... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>refactor</type>   <title>Refactored ConfigLoader configuration map with typed accessors</title>   <subtitle>ConfigLoader now returns an unmodifiable properties map while preserving configured values.</subtitle>   <facts>     <fact>src/main/java/com/acme/config/ConfigLoader.java collects properties.entrySet() into an unmodifiable map.</fact>     <fact>ConfigLoader exposes type... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Refactored RetryPolicy delay calculation with BackoffStrategy delegation</title>   <subtitle>RetryPolicy now delegates backoff timing to BackoffStrategy while preserving exponential delays.</subtitle>   <facts>     <fact>RetryPolicy returns backoff.nextDelay(attempt) instead of calculating the delay inline.</fact>     <fact>RetryPolicyTest asserts the... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Fixed tenant query SQL injection risk in UserRepository</title>   <subtitle>UserRepository now filters tenant rows with a parameterized JPQL query.</subtitle>   <facts>     <fact>UserRepository defines `@Query("select u from User u where u.tenantId = :tenantId")`.</fact>     <fact>UserRepository no longer constructs SQL through string concatenation.</fa... [truncated]
```

### refactor-event-bus

```text
<observation>   <type>refactor</type>   <title>Refactored EventBus dispatch with listener map</title>   <subtitle>EventBus now dispatches events through listeners keyed by event type instead of an if/else chain.</subtitle>   <facts>     <fact>src/main/java/com/acme/events/EventBus.java uses listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event)).</fact>     <fact>EventBus d... [truncated]
```

### refactor-feature-flag

```text
<observation>   <type>refactor</type>   <title>Refactored FeatureFlagService evaluation to Flags.isEnabled</title>   <subtitle>FeatureFlagService now delegates flag evaluation while retaining the existing on/off test matrix.</subtitle>   <facts>     <fact>src/main/java/com/acme/flags/FeatureFlagService.java evaluates flags with return flags.isEnabled(flag, context).</fact>     <fact>FeatureFlagSer... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for business-date bug</title>   <subtitle>A regression test reproduces the UTC-versus-Asia/Shanghai date mismatch.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone calls report.dateAt with 2026-11-01T23:30:00Z and ZoneId.of("Asia/Shanghai").</fact>     <fact>DailyReportTest#repor... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 for locked account status</title>   <subtitle>Regression test reproduces the locked-account login response returning 401 instead of 423.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 mocks users.isLocked("bob") to return true.</fact>     <fact>LoginControllerTest#lockedAccou... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized exponential backoff cases</title>   <subtitle>RetryPolicyTest now verifies doubling delays for attempts 1 through 3.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles uses @ParameterizedTest and @CsvSource with cases 1/100, 2/200, and 3/400.</fact>     <fact>RetryPolicyTest#delayDoubles asserts... [truncated]
```

### test-integration-order

```text
<observation>   <type>feature</type>   <title>Added OrderApiIT#createOrderReturns201 integration coverage</title>   <subtitle>Integration test verifies valid order creation returns 201 and a Location header using PostgreSQL 16.</subtitle>   <facts>     <fact>src/test/java/com/acme/orders/OrderApiIT.java uses @SpringBootTest(webEnvironment = RANDOM_PORT).</fact>     <fact>OrderApiIT#createOrderRetu... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable parameterized cases</title>   <subtitle>CSV quoting behavior is pinned for commas, embedded quotes, and newlines across three passing cases.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable is a parameterized test using @ValueSource with "a,b", "a\"b", and "a\nb".</fact>     <fact>CsvExporterTest#quot... [truncated]
```

### test-security-headers

```text
<observation>   <type>feature</type>   <title>Added SecurityHeadersTest#responseHasCspAndNosniff CSP and nosniff assertions</title>   <subtitle>MockMvc now verifies the root response includes Content-Security-Policy and X-Content-Type-Options: nosniff.</subtitle>   <facts>     <fact>SecurityHeadersTest#responseHasCspAndNosniff requests GET / through mockMvc.</fact>     <fact>SecurityHeadersTest#re... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull for concurrent null reads</title>   <subtitle>Regression test reproduces UserCache intermittent null results under 1000 parallel readers.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull executes 1000 parallel readers with IntStream.range(0, 1000).parallel().forEa... [truncated]
```

### test-contract-openapi

```text
<observation>   <type>feature</type>   <title>Added OpenAPI schema drift contract test in OpenApiContractTest</title>   <subtitle>generatedSpecMatchesCommittedFile compares springdoc output with docs/openapi.yaml and fails on schema drift.</subtitle>   <facts>     <fact>OpenApiContractTest#generatedSpecMatchesCommittedFile was added in src/test/java/com/acme/api/OpenApiContractTest.java.</fact>   ... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Added fixed Clock to SchedulerTest to remove Thread.sleep flakiness</title>   <subtitle>SchedulerTest now uses a deterministic UTC clock, and 20 consecutive runs pass.</subtitle>   <facts>     <fact>SchedulerTest sets scheduler.clock to Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC).</fact>     <fact>SchedulerTest no longer uses Thr... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed credential redaction in LogSanitizer</title>   <subtitle>LogSanitizer now masks password, token, and secret values before logging.</subtitle>   <facts>     <fact>src/main/java/com/acme/security/LogSanitizer.java replaces `(password|token|secret)=\S+` values with `$1=***`.</fact>     <fact>LogSanitizerTest#redactsCredentials passes for the password... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed UserDao email SQL injection with parameter placeholder</title>   <subtitle>UserDao.findByEmail now binds the email parameter instead of concatenating untrusted input into SQL.</subtitle>   <facts>     <fact>src/main/java/com/acme/users/UserDao.java uses jdbcTemplate.query("select * from users where email = ?", mapper, email).</fact>     <fact>SqlI... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Restricted AdminController endpoints to ROLE_ADMIN in AdminController.java</title>   <subtitle>AdminController now requires ROLE_ADMIN on every endpoint, preventing unauthorized user-role access.</subtitle>   <facts>     <fact>AdminController applies @PreAuthorize("hasRole('ADMIN')") in src/main/java/com/acme/admin/AdminController.java.</fact>     <fact... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Fixed mismatched CSRF token rejection in CsrfFilter.java</title>   <subtitle>CsrfFilter now returns HTTP 403 before processing requests whose CSRF token does not match.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/CsrfFilter.java calls response.sendError(403) when csrfToken.matches(request) returns false.</fact>     <fact>CsrfFilterTest#pos... [truncated]
```

### security-secret-config

```text
<observation>   <type>change</type>   <title>Changed API key to ACME_API_KEY environment variable in application.yml</title>   <subtitle>Application configuration now loads the API key from the environment instead of embedding a live credential.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets api-key to ${ACME_API_KEY}.</fact>     <fact>src/main/resources/application.yml no ... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Fixed XXE by disabling DOCTYPE declarations in XmlImportService</title>   <subtitle>XmlImportService now rejects DOCTYPE declarations, preventing external XML entity processing.</subtitle>   <facts>     <fact>XmlImportService sets disallow-doctype-decl to true on the XML parser factory.</fact>     <fact>XmlImportService blocks DOCTYPE declarations and e... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter empty bucket response with TooManyRequestsException</title>   <subtitle>RateLimiter now throws TooManyRequestsException when the token bucket cannot consume a request.</subtitle>   <facts>     <fact>RateLimiter calls bucket.tryConsume(1) and throws TooManyRequestsException when consumption fails.</fact>     <fact>RateLimiter returns HT... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>Session cookies now use HttpOnly, Secure, and SameSite=Strict protections.</subtitle>   <facts>     <fact>SessionCookieConfig sets cookie.setHttpOnly(true).</fact>     <fact>SessionCookieConfig sets cookie.setSecure(true).</fact>     <fact>SessionCookieConfig sets cookie.setSameSit... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Set Hikari pool limits maximum-pool-size 20 and minimum-idle 5</title>   <subtitle>application.yml now configures the datasource connection pool with explicit maximum and idle capacity.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20.</fact>     <fact>src/main/resources/application.... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Set root logger to INFO with JSON appender in logback-spring.xml</title>   <subtitle>Production logging now emits JSON records at INFO level through the JSON appender.</subtitle>   <facts>     <fact>src/main/resources/logback-spring.xml defines the root logger with level="INFO".</fact>     <fact>src/main/resources/logback-spring.xml routes the root logg... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added HEALTHCHECK endpoint check in Dockerfile</title>   <subtitle>Docker images now probe /actuator/health every 30 seconds using curl.</subtitle>   <facts>     <fact>Dockerfile defines HEALTHCHECK --interval=30s for the container image.</fact>     <fact>HEALTHCHECK requests http://localhost:8080/actuator/health and exits 1 when curl fails.</fact>     ... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Configured jacoco-maven-plugin 0.8.12 in pom.xml</title>   <subtitle>Maven verification now generates JaCoCo XML coverage reports for the project.</subtitle>   <facts>     <fact>pom.xml configures org.jacoco:jacoco-maven-plugin with version 0.8.12.</fact>     <fact>mvn verify writes the JaCoCo report to target/site/jacoco/jacoco.xml.</fact>   </facts>  ... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Changed local setup steps in README.md</title>   <subtitle>README.md now documents three steps for starting the project locally with JDK 21 and PostgreSQL.</subtitle>   <facts>     <fact>README.md requires JDK 21 for local setup.</fact>     <fact>README.md starts PostgreSQL with docker compose up -d postgres.</fact>     <fact>README.md starts the applic... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Documented queue recovery procedure in docs/runbook.md</title>   <subtitle>Queue recovery now has a three-step runbook for inspecting messages, restarting the worker, and checking health.</subtitle>   <facts>     <fact>docs/runbook.md instructs operators to check pending_messages.</fact>     <fact>docs/runbook.md instructs operators to restart the worke... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Changed CHANGELOG.md with 2.4.0 offline replay and V20 migration entries</title>   <subtitle>Release notes now document offline replay evaluation and the fixed V20 migration conflict.</subtitle>   <facts>     <fact>CHANGELOG.md adds the release heading "## 2.4.0 - 2026-11-03".</fact>     <fact>CHANGELOG.md lists "Added offline replay evaluation" under v... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisites in docs/MIGRATION.md</title>   <subtitle>Migration guidance requires a mem_sessions backup and manual sentinel review configuration.</subtitle>   <facts>     <fact>docs/MIGRATION.md requires backing up mem_sessions before running the V20 migration.</fact>     <fact>docs/MIGRATION.md requires app.manual_sentinel_rev... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Documented vulnerability disclosure channel in SECURITY.md</title>   <subtitle>SECURITY.md directs private vulnerability reports to security@acme.com and prohibits public issues.</subtitle>   <facts>     <fact>SECURITY.md specifies security@acme.com as the vulnerability reporting address.</fact>     <fact>SECURITY.md states that vulnerabilities must not... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Documented verify-local.sh pull request requirement in CONTRIBUTING.md</title>   <subtitle>CONTRIBUTING.md now requires ./scripts/verify-local.sh before opening a pull request and identifies four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md requires running ./scripts/verify-local.sh before opening a pull request.</fact>     <fact>CONTRIBU... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Changed CI backend matrix to JDK 21 and 25 in ci.yml</title>   <subtitle>Backend CI jobs now validate the project against both Java 21 and Java 25.</subtitle>   <facts>     <fact>.github/workflows/ci.yml sets strategy.matrix.java to [21, 25].</fact>     <fact>.github/workflows/ci.yml backend workflow syntax check passes.</fact>   </facts>   <narrative>T... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted deploy IAM policy to AmazonS3ReadOnlyAccess in infra/main.tf</title>   <subtitle>The deploy role now receives read-only Amazon S3 access, and terraform validate succeeds.</subtitle>   <facts>     <fact>resource "aws_iam_role_policy" "deploy" sets policy_arn to "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess" in infra/main.tf.</fact>     <fact... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added claude-mem deployment rollback script</title>   <subtitle>Rollback script undoes the claude-mem deployment and waits for rollout completion.</subtitle>   <facts>     <fact>scripts/rollback.sh runs `kubectl rollout undo deployment/claude-mem`.</fact>     <fact>scripts/rollback.sh runs `kubectl rollout status deployment/claude-mem` after the undo.<... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Changed canary rollout weight and error threshold in infra/canary.yaml</title>   <subtitle>Canary rollout now targets 10 percent traffic and rolls back above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true.</fact>     <fact>infra/canary.yaml sets canary.weight to 10 for a 10 percent canary rollout.</f... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared verb class, (2) the declared subject tokens to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. The legacy keyword-coverage metric is reported alongside for before/after comparison.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
