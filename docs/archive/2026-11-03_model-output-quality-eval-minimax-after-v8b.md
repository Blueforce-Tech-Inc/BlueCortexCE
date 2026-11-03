# Model output quality evaluation

- Date: 2026-11-03
- Phase: after
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 87 unique; runs scored: 3; key repeat: 3; real calls: 113 (cap none); duration: 261698 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 92% |
| skip accuracy | 99% |
|   skip confusion (TP/FP/FN/TN) | 19/0/1/67 |
| format errors | 6 |
| validation rejections | 0 |
| call failures | 0 |
| title exact-hit rate (legacy) | 44% |
| title normalized hit rate (legacy) | 46% |
| title action-class hit rate (A6) | 85% (61 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 67% |
| title subject hit rate | 74% |
| title contract hit rate (action + subject + all identifiers) | 62% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 49% |
| title identifier hit rate | 98% |
| type accuracy | 90% |
| facts keyword precision (legacy) / recall | 81% / 92% |
| facts informative precision | 80% |
| facts trace-support rate | 100% |
| concepts precision / recall | 83% / 82% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 1724 / 6272 / 10036 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 6 | **FAIL** |
| A2 | skip accuracy | >= 95% | 99% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 1 | PASS |
| A5 | facts informative precision | >= 75% | 80% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 85% | PASS |
| A6b | title subject | >= 90% | 74% | **FAIL** |
| A6c | title identifiers | = 100% | 98% | **FAIL** |
| A6 | all three title dimensions together | all pass | 62% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 87 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 87 | 99% | 46% | 62% | 81% | 80% | 92% | 6 |
| 1 | 13 | 100% | 40% | 50% | 91% | 85% | 100% | 0 |
| 2 | 13 | 100% | 50% | 50% | 91% | 88% | 100% | 0 |

- average: skip 100%, title 45%, title contract 54%, facts P/R 88%/97%, facts actionable 85%
- worst run: skip 99%, title 40%, title contract 50%, facts P/R 81%/92%, facts actionable 80%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 11/12 | 10/12 | 5/12 |
| refactor | 10 | 9/10 | 7/10 | 7/10 |
| test-add | 9 | 7/9 | 7/9 | 5/9 |
| security-fix | 9 | 8/9 | 7/9 | 3/9 |
| config-change | 12 | 11/12 | 10/12 | 4/12 |
| docs-update | 8 | 8/8 | 8/8 | 2/8 |
| deploy-change | 7 | 7/7 | 6/7 | 2/7 |
| skip-low-value | 20 | 19/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 5627 |
| bug-null-cache | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 1824 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 1820 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 1597 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1523 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/67% | 100% | 100%/100% | 3921 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 50%/50% | 2062 |
| bug-race-counter | bug-fix | observation | malformed | FAIL | - | - | - | - | - | - | - | 9707 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 50% | MISS | miss | MISS | 100%/100% | 100% | 50%/50% | 1784 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1715 |
| bug-regex-redos | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 33%/50% | 67% | 100%/100% | 1494 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 1825 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 67% | 100%/100% | 3977 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 2868 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | hit | miss | MISS | 67%/100% | 33% | 100%/100% | 1390 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 1588 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 67% | 100%/100% | 1365 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | MISS | hit | match | 67%/100% | 33% | 100%/100% | 1543 |
| refactor-sql-repository | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 50% | 100%/100% | 2334 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 50% | 100%/100% | 3112 |
| refactor-feature-flag | refactor | observation | malformed | FAIL | - | - | - | - | - | - | - | 5271 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/50% | 2619 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 1963 |
| test-unit-retry | test-add | observation | malformed | FAIL | - | - | - | - | - | - | - | 5218 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 3405 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 67% | 100%/100% | 1504 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 1575 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | MISS | hit | match | 100%/67% | 100% | 50%/50% | 1865 |
| test-contract-openapi | test-add | observation | malformed | FAIL | - | - | - | - | - | - | - | 8285 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 67% | 100%/100% | 3481 |
| security-redact-log | security-fix | observation | observation | pass | 100% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 1929 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | MISS | hit | match | 33%/50% | 33% | 50%/50% | 3011 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | hit | miss | match | 100%/50% | 50% | 50%/50% | 2059 |
| security-csrf | security-fix | observation | malformed | FAIL | - | - | - | - | - | - | - | 6697 |
| security-secret-config | security-fix | observation | observation | pass | 50% | MISS | miss | MISS | 67%/100% | 67% | 50%/50% | 10036 |
| security-xxe | security-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 1445 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | MISS | hit | match | 67%/100% | 100% | 100%/100% | 1337 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS | miss | match | 33%/100% | 100% | 100%/100% | 1529 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 1662 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 2091 |
| config-flyway-baseline | config-change | observation | malformed | FAIL | - | - | - | - | - | - | - | 6272 |
| config-log-level | config-change | observation | observation | pass | 75% | MISS | miss | match | 33%/50% | 0% | 100%/100% | 1852 |
| config-cors | config-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 67% | 100%/100% | 2099 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/50% | 1555 |
| config-feature-flags | config-change | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 100%/100% | 1535 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 100% | 50%/50% | 1351 |
| config-maven-plugin | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 1579 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 2324 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 33% | 100%/100% | 1564 |
| docs-runbook | docs-update | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 1589 |
| docs-changelog | docs-update | observation | observation | pass | 33% | hit | miss | match | 100%/100% | 100% | 100%/100% | 1557 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 2184 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 3635 |
| docs-security-policy | docs-update | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 50% | 50%/50% | 1396 |
| docs-contributing | docs-update | observation | observation | pass | 33% | hit | miss | match | 100%/100% | 50% | 100%/100% | 1778 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 67% | 100%/100% | 1906 |
| deploy-github-actions | deploy-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 1661 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 100%/100% | 2045 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 50% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 3242 |
| deploy-helm-values | deploy-change | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 3028 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 33% | 50%/50% | 1871 |
| deploy-canary | deploy-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 1724 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 916 |
| skip-file-listing | skip-low-value | skip | malformed | FAIL | - | - | - | - | - | - | - | 2605 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1448 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 998 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2399 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 851 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 908 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 822 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 997 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2540 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1108 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1529 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 857 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 806 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 960 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1183 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 801 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1128 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1073 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1051 |
| remove-dependency | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1255 |
| remove-dead-code | refactor | observation | observation | pass | 50% | MISS | miss | MISS | 100%/100% | 100% | 100%/50% | 1273 |
| remove-stale-config | config-change | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 1706 |
| remove-deprecated-endpoint | config-change | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 3798 |
| remove-feature-flag | config-change | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/50% | 3520 |

## Problems

- bug-offbyone-pagination: title coverage 67% (expected keywords [fix, pagination, orderquery])
- bug-offbyone-pagination: title subject [pagination] missing (got 'Fixed OrderQuery.pageOffset first-page off-by-one in OrderQuery.java')
- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-null-cache: title coverage 75% (expected keywords [fix, race, usercache, computeifabsent])
- bug-null-cache: title subject [race] missing (got 'Fixed UserCache.get concurrent null writes with computeIfAbsent in UserCache.java')
- bug-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-jwt-expiry: facts recall 67% (expected [expiration, isEqual, rejects], got [JwtVerifier checks claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now)., JwtVerifierTest#expiredAtExactBoundary passes after the expiration-boundary fix.])
- bug-csv-crlf: facts recall 50% (expected [CRLF, multilineFieldKeepsRowCount], got [CsvExporter.java now applies line.replaceAll("\\r?\\n", "\\r\\n") before writing quoted CSV lines., CsvExporterTest#multilineFieldKeepsRowCount passes after embedded line-ending normalization.])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: format error: DataValidationException: No <observation> block found in XML
- bug-n-plus-one: title coverage 50% (expected keywords [fix, n+1, orderrepository, entitygraph])
- bug-n-plus-one: title verb class 'fix' missing (got 'Extended OrderRepository.findAllByCustomerId with item EntityGraph')
- bug-n-plus-one: title subject [n+1] missing (got 'Extended OrderRepository.findAllByCustomerId with item EntityGraph')
- bug-n-plus-one: type 'change' != expected 'bugfix'
- bug-n-plus-one: concepts recall 50% (expected [problem-solution, pattern], got [what-changed, pattern])
- bug-regex-redos: title coverage 75% (expected keywords [fix, redos, urlvalidator, regex])
- bug-regex-redos: title subject [redos] missing (got 'Fixed UrlValidator regex performance with linear URL pattern')
- bug-regex-redos: facts recall 50% (expected [linear pattern, longInputFinishesUnder100ms], got [src/main/java/com/acme/validation/UrlValidator.java defines URL as Pattern.compile("^https?://[^\\s]+$")., UrlValidator no longer uses a nested-quantifier regex for URL validation., UrlValidatorTest#longInputFinishesUnder100ms passes after the UrlValidator change.])
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed FileReaderFactory platform-default decoding with explicit UTF-8')
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title verb class 'refactor' missing (got 'Extended ConfigLoader with unmodifiable map and typed accessors')
- refactor-config-loader: type 'change' != expected 'refactor'
- refactor-retry-policy: facts recall 50% (expected [BackoffStrategy, 100/200/400], got [src/main/java/com/acme/resilience/RetryPolicy.java returns backoff.nextDelay(attempt) for retry delay calculation., RetryPolicyTest asserts delay values of 100, 200, and 400., RetryPolicy extracts delay calculation into BackoffStrategy.])
- refactor-logging-filter: title subject [timing] missing (got 'Refactored LoggingFilter access logging through logExchange')
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [src/main/java/com/acme/users/UserRepository.java defines @Query("select u from User u where u.tenantId = :tenantId")., UserRepositoryTest returns the same tenant rows after the query change.])
- refactor-event-bus: facts recall 50% (expected [listener map, event type], got [src/main/java/com/acme/events/EventBus.java selects listeners with listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event))., EventBusTest dispatches the same three events against the listener-map implementation.])
- refactor-feature-flag: format error: DataValidationException: No <observation> block found in XML
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-unit-retry: format error: DataValidationException: No <observation> block found in XML
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable uses @ParameterizedTest with @ValueSource(strings = {"a,b","a\"b","a\nb"})., CsvExporterTest#quotingIsStable covers comma, quote, and newline values., All 3 CsvExporterTest#quotingIsStable cases pass.])
- test-security-headers: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-repro-null-cache: title lost identifiers [CacheConcurrencyTest, UserCache] (got 'Added CacheConcurrencyTest#concurrentReadersNeverNull parallel reader test')
- test-repro-null-cache: facts recall 67% (expected [1000, null, parallel], got [src/test/java/com/acme/cache/CacheConcurrencyTest.java adds CacheConcurrencyTest#concurrentReadersNeverSeeNull., CacheConcurrencyTest#concurrentReadersNeverSeeNull runs 1,000 parallel cache.get("k") calls and requires every result to be non-null., CacheConcurrencyTest#concurrentReadersNeverSeeNull fails before the UserCache fix and passes after the fix.])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-contract-openapi: format error: DataValidationException: No <observation> block found in XML
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- test-flaky-clock: title action 'modify' missing: title leads 'create' (got 'Added fixed-clock scheduler test in SchedulerTest.java')
- test-flaky-clock: title verb class 'fix' missing (got 'Added fixed-clock scheduler test in SchedulerTest.java')
- test-flaky-clock: title subject [flaky, clock] missing (got 'Added fixed-clock scheduler test in SchedulerTest.java')
- security-redact-log: title action 'secure' missing: title leads 'modify' (got 'Fixed credential redaction for password, token and secret in LogSanitizer')
- security-redact-log: title verb class 'redact' missing (got 'Fixed credential redaction for password, token and secret in LogSanitizer')
- security-redact-log: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-sql-injection: title action 'secure' missing: title leads 'modify' (got 'Fixed SQL injection in UserDao.findByEmail parameterized query')
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail executes jdbcTemplate.query("select * from users where email = ?", mapper, email)., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the UserDao query change., UserDao.findByEmail no longer constructs the SQL predicate by concatenating email into the query string.])
- security-sql-injection: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Restricted AdminController endpoints authorization to ROLE_ADMIN')
- security-authz-admin: facts recall 50% (expected [ROLE_ADMIN, 403], got [AdminController applies @PreAuthorize("hasRole('ADMIN')") at the controller level., AdminAuthorizationTest#userRoleGets403 passes with the ROLE_ADMIN authorization requirement.])
- security-authz-admin: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-csrf: format error: DataValidationException: No <observation> block found in XML
- security-secret-config: title coverage 50% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: title action 'secure' missing: title leads 'modify' (got 'Replaced literal api-key with ACME_API_KEY environment lookup in application.yml')
- security-secret-config: title verb class 'remove' missing (got 'Replaced literal api-key with ACME_API_KEY environment lookup in application.yml')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Replaced literal api-key with ACME_API_KEY environment lookup in application.yml')
- security-secret-config: type 'change' != expected 'bugfix'
- security-secret-config: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, gotcha])
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-xxe: title action 'secure' missing: title leads 'modify' (got 'Fixed XXE rejection by disabling DOCTYPE declarations in XmlImportService')
- security-xxe: title verb class 'harden' missing (got 'Fixed XXE rejection by disabling DOCTYPE declarations in XmlImportService')
- security-path-traversal: title action 'secure' missing: title leads 'modify' (got 'Fixed path traversal in FileDownloadController')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title action 'secure' missing: title leads 'modify' (got 'Fixed RateLimiter empty token bucket response to 429')
- security-rate-limit: title verb class 'add' missing (got 'Fixed RateLimiter empty token bucket response to 429')
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- config-hikari-pool: title coverage 75% (expected keywords [change, hikari, pool, size])
- config-hikari-pool: title verb class 'change' missing (got 'Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml')
- config-flyway-baseline: format error: DataValidationException: No <observation> block found in XML
- config-log-level: title coverage 75% (expected keywords [change, logback, root, json])
- config-log-level: title verb class 'change' missing (got 'Set root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: title subject [log, level] missing (got 'Set root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: facts recall 50% (expected [INFO, JSON appender], got [src/main/resources/logback-spring.xml configures the root logger level to INFO., src/main/resources/logback-spring.xml routes root logger output through the appender named JSON., LogbackConfigTest parses src/main/resources/logback-spring.xml.])
- config-cors: title coverage 75% (expected keywords [restrict, cors, corsconfig, origin])
- config-timeout: concepts recall 50% (expected [what-changed, trade-off], got [trade-off, pattern])
- config-feature-flags: title coverage 50% (expected keywords [enable, checkout-v2, disable, legacy-search])
- config-feature-flags: title verb class 'enable' missing (got 'Changed checkout-v2 and legacy-search flags in feature-flags.yml')
- config-docker-healthcheck: title action 'modify' missing: title leads 'create' (got 'Added HEALTHCHECK for /actuator/health in Dockerfile')
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-maven-plugin: title coverage 75% (expected keywords [add, jacoco, plugin, pom])
- config-maven-plugin: title verb class 'add' missing (got 'Extended Maven build with jacoco-maven-plugin 0.8.12 in pom.xml')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Documented local setup in README.md')
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title verb class 'add' missing (got 'Documented queue recovery procedure in docs/runbook.md')
- docs-changelog: title coverage 33% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title verb class 'change' missing (got 'Documented offline replay evaluation and V20 migration fix in CHANGELOG.md')
- docs-migration-guide: title coverage 75% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [what-changed, pattern])
- docs-security-policy: title coverage 50% (expected keywords [add, security, policy, disclosure])
- docs-security-policy: title verb class 'add' missing (got 'Documented vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: title subject [security, policy] missing (got 'Documented vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 33% (expected keywords [update, contributing, verify-local])
- docs-contributing: title verb class 'change' missing (got 'Documented local verification requirement in CONTRIBUTING.md')
- deploy-k8s-probe: title coverage 75% (expected keywords [add, readiness, probe, deployment])
- deploy-k8s-probe: title verb class 'add' missing (got 'Extended readinessProbe /api/health in deploy/deployment.yaml')
- deploy-github-actions: title coverage 75% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: title verb class 'add' missing (got 'Extended CI backend matrix to JDK 21 and 25 in .github/workflows/ci.yml')
- deploy-terraform-iam: title coverage 50% (expected keywords [restrict, iam, terraform, s3])
- deploy-terraform-iam: title subject [iam, policy] missing (got 'Restricted deploy role to AmazonS3ReadOnlyAccess in infra/main.tf')
- deploy-helm-values: title coverage 75% (expected keywords [change, helm, replicas, memory])
- deploy-helm-values: title subject [helm, values] missing (got 'Changed replicaCount to 3 and memory limit to 2Gi in infra/values.yaml')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-rollback-script: concepts recall 50% (expected [what-changed, pattern], got [pattern, trade-off])
- deploy-canary: title coverage 75% (expected keywords [enable, canary, rollback, threshold])
- skip-file-listing: expected <skip> but produced malformed output: DataValidationException: No <observation> block found in XML
- remove-dead-code: title coverage 50% (expected keywords [remove, dead, code, orderservice])
- remove-dead-code: title subject [dead, code] missing (got 'Removed legacySync from OrderService')
- remove-dead-code: type 'change' != expected 'refactor'
- remove-dead-code: concepts recall 50% (expected [what-changed, pattern], got [what-changed, what-changed])
- remove-stale-config: title coverage 75% (expected keywords [remove, legacy, search, application])
- remove-stale-config: title action 'remove' missing: title leads 'modify' (got 'Disabled legacy-search.enabled in application.yml')
- remove-stale-config: title verb class 'remove' missing (got 'Disabled legacy-search.enabled in application.yml')
- remove-stale-config: title subject [stale, setting] missing (got 'Disabled legacy-search.enabled in application.yml')
- remove-deprecated-endpoint: title coverage 75% (expected keywords [remove, deprecated, endpoint, usercontroller])
- remove-deprecated-endpoint: title subject [deprecated, endpoint] missing (got 'Removed legacy /v1/users endpoint from UserController')
- remove-feature-flag: type 'refactor' != expected 'change'
- remove-feature-flag: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset first-page off-by-one in OrderQuery.java</title>   <subtitle>OrderQuery.pageOffset now starts first-page results at zero instead of subtracting one.</subtitle>   <facts>     <fact>OrderQuery.pageOffset changed from `page * size - 1` to `page * size` in src/main/java/com/acme/orders/OrderQuery.java.</fact>     <fact>OrderQuery... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache.get concurrent null writes with computeIfAbsent in UserCache.java</title>   <subtitle>UserCache.get now atomically loads absent users through the configured loader.</subtitle>   <facts>     <fact>UserCache.get in src/main/java/com/acme/cache/UserCache.java now calls cache.computeIfAbsent(id, loader::load).</fact>     <fact>Two concurrent... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for business date boundary</title>   <subtitle>The new test pins the Asia/Shanghai business date for a UTC boundary timestamp.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone uses ZoneId.of("Asia/Shanghai") for 2026-11-01T23:30Z.</fact>     <fact>DailyReportTest#reportUsesBusine... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed missing orderId lookup to throw PaymentNotFoundException in PaymentService</title>   <subtitle>PaymentService now returns a domain-specific exception instead of null when an order has no payment.</subtitle>   <facts>     <fact>PaymentService delegates repository.findByOrderId(orderId) lookup to Optional.orElseThrow.</fact>     <fact>PaymentService... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration equality boundary in JwtVerifier.java</title>   <subtitle>JwtVerifier now rejects tokens expiring exactly at the current instant.</subtitle>   <facts>     <fact>JwtVerifier checks claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now).</fact>     <fact>JwtVerifierTest#expiredAtExactBoundary passes after ... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed embedded line endings in CsvExporter.java</title>   <subtitle>CsvExporter normalizes embedded line endings to CRLF before quoting, preserving multiline CSV row counts.</subtitle>   <facts>     <fact>CsvExporter.java now applies line.replaceAll("\\r?\\n", "\\r\\n") before writing quoted CSV lines.</fact>     <fact>CsvExporterTest#multilineFieldKeep... [truncated]
```

### bug-race-counter

```text
We need output XML observation only. Need infer exact. Edit in CounterService increment replacing non-atomic get/set with incrementAndGet. Test passes 1000 threads 1000. Bug fix concurrency race. Title narrow: Fixed CounterService.increment atomicity with incrementAndGet in CounterService.java. Under 100 chars? "Fixed CounterService.increment atomicity with incrementAndGet in CounterService.java" ... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>change</type>   <title>Extended OrderRepository.findAllByCustomerId with item EntityGraph</title>   <subtitle>Order items are now fetched in one SQL statement, avoiding the previous N+1 query pattern.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId(String customerId) now uses @EntityGraph(attributePaths = "items") to fetch order items in the same query.</fa... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator regex performance with linear URL pattern</title>   <subtitle>UrlValidator now uses a non-nested-quantifier expression, and the long-input performance test completes under 100ms.</subtitle>   <facts>     <fact>src/main/java/com/acme/validation/UrlValidator.java defines URL as Pattern.compile("^https?://[^\\s]+$").</fact>     <fact>Url... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed FileReaderFactory platform-default decoding with explicit UTF-8</title>   <subtitle>FileReaderFactory now consistently reads files as UTF-8, preserving accented characters on GBK-default JVMs.</subtitle>   <facts>     <fact>src/main/java/com/acme/files/FileReaderFactory.java constructs InputStreamReader with StandardCharsets.UTF_8.</fact>     <fac... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>change</type>   <title>Extended ConfigLoader with unmodifiable map and typed accessors</title>   <subtitle>ConfigLoader now returns immutable properties and typed values while preserving tested behavior.</subtitle>   <facts>     <fact>src/main/java/com/acme/config/ConfigLoader.java collects properties.entrySet() into an unmodifiable map.</fact>     <fact>ConfigLoader exposes ... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Refactored RetryPolicy delay calculation with BackoffStrategy</title>   <subtitle>RetryPolicy now delegates delay calculation while preserving the tested exponential sequence.</subtitle>   <facts>     <fact>src/main/java/com/acme/resilience/RetryPolicy.java returns backoff.nextDelay(attempt) for retry delay calculation.</fact>     <fact>RetryPolicyTes... [truncated]
```

### refactor-logging-filter

```text
<observation>   <type>refactor</type>   <title>Refactored LoggingFilter access logging through logExchange</title>   <subtitle>LoggingFilter now starts timing and captures headers inside logExchange while preserving the access-log line.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/LoggingFilter.java calls logExchange(exchange, StopWatch.createStarted()).</fact>     <fact>LoggingFilter... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>refactor</type>   <title>Refactored UserRepository tenant filtering with JPQL @Query</title>   <subtitle>Parameterized tenantId lookup replaced concatenated SQL while preserving tenant row results.</subtitle>   <facts>     <fact>src/main/java/com/acme/users/UserRepository.java defines @Query("select u from User u where u.tenantId = :tenantId").</fact>     <fact>UserRepository... [truncated]
```

### refactor-event-bus

```text
<observation>   <type>refactor</type>   <title>Refactored EventBus dispatch with listener map by event type</title>   <subtitle>EventBus now dispatches events through listeners selected by event type instead of an if/else chain.</subtitle>   <facts>     <fact>src/main/java/com/acme/events/EventBus.java selects listeners with listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(e... [truncated]
```

### refactor-feature-flag

```text
We need produce observation. Need infer changed artifact exact identifier. Edit src/main/java/com/acme/flags/FeatureFlagService.java, new string return flags.isEnabled(flag, context). Outcome says delegates evaluation to Flags.isEnabled. Same on/off matrix. This is refactor behavior unchanged, likely title "Refactored FeatureFlagService evaluation with Flags.isEnabled delegation" path suffix becau... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for UTC-vs-business-date bug</title>   <subtitle>A regression test pins Asia/Shanghai business dates for UTC timestamps crossing midnight.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone asserts report.dateAt(2026-11-01T23:30:00Z, Asia/Shanghai) equals 2026-11-02.</fact>     <fa... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 for locked accounts</title>   <subtitle>Regression test reproduces the locked-account login response of 401 instead of the expected 423.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true.</fact>     <fact>LoginControllerTest#lockedAccou... [truncated]
```

### test-unit-retry

```text
We need output observation only XML. Need derive. New test file. Type feature. Title contract: Added RetryPolicyTest#delayDoubles parameterized cases for exponential backoff. Under 100 chars likely 83. Facts 1-5 exact identifiers/numbers. Files modified path. files_read likely empty? XML structure could empty tag? Need not claim read. facts: RetryPolicyTest#delayDoubles asserts policy.delay(attemp... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable parameterized cases</title>   <subtitle>Parameterized coverage verifies stable CSV quoting for commas, quotes, and newlines.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable uses @ParameterizedTest with @ValueSource(strings = {"a,b","a\"b","a\nb"}).</fact>     <fact>CsvExporterTest#quotingIsStable cov... [truncated]
```

### test-security-headers

```text
<observation>   <type>feature</type>   <title>Added SecurityHeadersTest#responseHasCspAndNosniff security header assertions</title>   <subtitle>New MockMvc tests verify Content-Security-Policy and X-Content-Type-Options headers on root responses.</subtitle>   <facts>     <fact>SecurityHeadersTest#responseHasCspAndNosniff asserts that GET / returns a Content-Security-Policy containing default-src '... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverNull parallel reader test</title>   <subtitle>A 1,000-reader regression test reproduces and guards against intermittent null cache reads.</subtitle>   <facts>     <fact>src/test/java/com/acme/cache/CacheConcurrencyTest.java adds CacheConcurrencyTest#concurrentReadersNeverSeeNull.</fact>     <fact>CacheCo... [truncated]
```

### test-contract-openapi

```text
We need output observation only. Need follow exact. We know new test file added, feature type because added test. Title 20-100 chars, verb Added new file. "Added OpenApiContractTest#generatedSpecMatchesCommittedFile schema drift check" perhaps 73. Need path suffix because test new file and identifier exact. Could title include path, but max 100. "Added OpenApiContractTest#generatedSpecMatchesCommi... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Added fixed-clock scheduler test in SchedulerTest.java</title>   <subtitle>SchedulerTest replaces Thread.sleep with a deterministic Clock.fixed setup to eliminate test flakiness.</subtitle>   <facts>     <fact>SchedulerTest.java sets scheduler.clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC).</fact>     <fact>SchedulerTest.jav... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed credential redaction for password, token and secret in LogSanitizer</title>   <subtitle>LogSanitizer now masks credential values before log output.</subtitle>   <facts>     <fact>src/main/java/com/acme/security/LogSanitizer.java replaces password, token, and secret values matching the credential pattern with masked values.</fact>     <fact>LogSani... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed SQL injection in UserDao.findByEmail parameterized query</title>   <subtitle>UserDao.findByEmail now binds email through a JDBC placeholder, and the quote-input regression test passes.</subtitle>   <facts>     <fact>UserDao.findByEmail executes jdbcTemplate.query("select * from users where email = ?", mapper, email).</fact>     <fact>SqlInjectionT... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Restricted AdminController endpoints authorization to ROLE_ADMIN</title>   <subtitle>Every AdminController endpoint now requires ROLE_ADMIN, and user-role access is rejected with HTTP 403.</subtitle>   <facts>     <fact>AdminController applies @PreAuthorize("hasRole('ADMIN')") at the controller level.</fact>     <fact>AdminAuthorizationTest#userRoleGets... [truncated]
```

### security-csrf

```text
We need produce observation. Need infer modification exact file. The edit says condition from likely always? New behavior mismatched token 403. CsrfFilterTest postWithoutToken passes. Is this bugfix or feature? "CsrfFilter rejects state-changing requests with a mismatched token." Existing perhaps filter was wrongly rejecting all? We only see edit one line and outcome. Need facts verifiable. Could ... [truncated]
```

### security-secret-config

```text
<observation>   <type>change</type>   <title>Replaced literal api-key with ACME_API_KEY environment lookup in application.yml</title>   <subtitle>Runtime configuration now resolves the API key from the environment instead of embedding a live secret.</subtitle>   <facts>     <fact>src/main/resources/application.yml defines api-key as ${ACME_API_KEY}.</fact>     <fact>src/main/resources/application.... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Fixed XXE rejection by disabling DOCTYPE declarations in XmlImportService</title>   <subtitle>XML parsing now rejects external entities, and XxeTest#externalEntityIsRejected passes.</subtitle>   <facts>     <fact>XmlImportService sets factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true).</fact>     <fact>XxeTest#externalEntit... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Fixed path traversal in FileDownloadController</title>   <subtitle>FileDownloadController now rejects names resolving outside the configured base directory.</subtitle>   <facts>     <fact>FileDownloadController resolves and normalizes requested names with base.resolve(name).normalize().</fact>     <fact>FileDownloadController throws InvalidPathException... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter empty token bucket response to 429</title>   <subtitle>RateLimiter now throws TooManyRequestsException when the token bucket cannot consume a request.</subtitle>   <facts>     <fact>RateLimiter.java throws TooManyRequestsException when bucket.tryConsume(1) returns false.</fact>     <fact>RateLimiter returns HTTP 429 when the token buck... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>Session cookies now enable HttpOnly, Secure, and SameSite=Strict to strengthen browser-side protections.</subtitle>   <facts>     <fact>SessionCookieConfig sets cookie.setHttpOnly(true).</fact>     <fact>SessionCookieConfig sets cookie.setSecure(true).</fact>     <fact>SessionCooki... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml</title>   <subtitle>Hikari connection-pool sizing now uses a maximum of 20 connections and maintains at least 5 idle connections.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20.</fact>     <fact>src/main/re... [truncated]
```

### config-flyway-baseline

```text
We need output observation only XML. Need carefully adhere. We have edit pom.xml adding Flyway baselineVersion 1 and baselineOnMigrate true. Type change (config). Title contract: Set? Two settings changed. "Set Flyway baselineVersion to 1 and baselineOnMigrate true in pom.xml" Allowed Set single numeric value only, but here two. "Changed Flyway baselineVersion to 1 and baselineOnMigrate true in po... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Set root logger to INFO with JSON appender in logback-spring.xml</title>   <subtitle>Logback now emits root-level INFO records through the JSON appender and is parsed by LogbackConfigTest.</subtitle>   <facts>     <fact>src/main/resources/logback-spring.xml configures the root logger level to INFO.</fact>     <fact>src/main/resources/logback-spring.xml ... [truncated]
```

### config-cors

```text
<observation>   <type>change</type>   <title>Restricted /api/** CORS to https://app.acme.com GET and POST in CorsConfig</title>   <subtitle>CorsConfig now permits only the production app origin and two HTTP methods.</subtitle>   <facts>     <fact>CorsConfig maps /api/** with allowedOrigins set to https://app.acme.com.</fact>     <fact>CorsConfig allows GET and POST for /api/** requests.</fact>    ... [truncated]
```

### config-timeout

```text
<observation>   <type>change</type>   <title>Set WebClient responseTimeout to 5 seconds in WebClientConfig</title>   <subtitle>WebClient requests now time out after five seconds, with the timeout verified from the configured bean.</subtitle>   <facts>     <fact>WebClientConfig configures HttpClient.create().responseTimeout(Duration.ofSeconds(5)).</fact>     <fact>WebClientConfigTest reads the resp... [truncated]
```

### config-feature-flags

```text
<observation>   <type>change</type>   <title>Changed checkout-v2 and legacy-search flags in feature-flags.yml</title>   <subtitle>Feature flag configuration enables checkout-v2 and disables legacy-search with both bindings verified.</subtitle>   <facts>     <fact>src/main/resources/feature-flags.yml sets checkout-v2 to true.</fact>     <fact>src/main/resources/feature-flags.yml sets legacy-search ... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added HEALTHCHECK for /actuator/health in Dockerfile</title>   <subtitle>Docker images now check actuator health every 30 seconds and exit on failure.</subtitle>   <facts>     <fact>Dockerfile defines HEALTHCHECK with --interval=30s.</fact>     <fact>Dockerfile runs curl -f http://localhost:8080/actuator/health || exit 1 as the health-check command.</fa... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Extended Maven build with jacoco-maven-plugin 0.8.12 in pom.xml</title>   <subtitle>Maven verification now generates JaCoCo XML coverage output at target/site/jacoco/jacoco.xml.</subtitle>   <facts>     <fact>pom.xml adds org.jacoco:jacoco-maven-plugin with version 0.8.12.</fact>     <fact>mvn verify writes coverage output to target/site/jacoco/jacoco.x... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Documented local setup in README.md</title>   <subtitle>README now provides three steps for running the application locally with JDK 21, PostgreSQL, and Maven.</subtitle>   <facts>     <fact>README.md requires JDK 21 for local setup.</fact>     <fact>README.md starts PostgreSQL with `docker compose up -d postgres`.</fact>     <fact>README.md launches th... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Documented queue recovery procedure in docs/runbook.md</title>   <subtitle>Queue recovery now uses pending_messages inspection, worker restart, and /api/health verification.</subtitle>   <facts>     <fact>docs/runbook.md defines step 1 as checking pending_messages.</fact>     <fact>docs/runbook.md defines step 2 as restarting the worker.</fact>     <fac... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Documented offline replay evaluation and V20 migration fix in CHANGELOG.md</title>   <subtitle>CHANGELOG.md now records release 2.4.0 capabilities and the resolved V20 migration conflict.</subtitle>   <facts>     <fact>CHANGELOG.md adds release entry `2.4.0 - 2026-11-03`.</fact>     <fact>The `2.4.0` entry lists `Added offline replay evaluation`.</fact>... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisites in docs/MIGRATION.md</title>   <subtitle>V20 requires backing up mem_sessions and enabling manual sentinel review before migration.</subtitle>   <facts>     <fact>docs/MIGRATION.md requires backing up mem_sessions before running the V20 migration.</fact>     <fact>docs/MIGRATION.md requires app.manual_sentinel_revi... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Documented vulnerability disclosure channel in SECURITY.md</title>   <subtitle>SECURITY.md directs vulnerability reports to security@acme.com and prohibits public issue disclosure.</subtitle>   <facts>     <fact>SECURITY.md defines security@acme.com as the vulnerability reporting channel.</fact>     <fact>SECURITY.md states that vulnerabilities must not... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Documented local verification requirement in CONTRIBUTING.md</title>   <subtitle>Contributors must run ./scripts/verify-local.sh before opening a pull request.</subtitle>   <facts>     <fact>CONTRIBUTING.md requires ./scripts/verify-local.sh before opening a pull request.</fact>     <fact>CONTRIBUTING.md names four test layers in its local verification ... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Extended readinessProbe /api/health in deploy/deployment.yaml</title>   <subtitle>Deployment readiness now checks the health endpoint on port 37777 after a 15-second initial delay.</subtitle>   <facts>     <fact>deploy/deployment.yaml defines a readinessProbe with httpGet path /api/health and port 37777.</fact>     <fact>deploy/deployment.yaml sets read... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Extended CI backend matrix to JDK 21 and 25 in .github/workflows/ci.yml</title>   <subtitle>Backend CI now validates both JDK 21 and JDK 25, with workflow syntax passing.</subtitle>   <facts>     <fact>.github/workflows/ci.yml sets strategy.matrix.java to [21, 25].</fact>     <fact>.github/workflows/ci.yml runs the backend on JDK 21 and JDK 25.</fact>  ... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted deploy role to AmazonS3ReadOnlyAccess in infra/main.tf</title>   <subtitle>The Terraform deploy role now grants read-only S3 access and passes terraform validate.</subtitle>   <facts>     <fact>aws_iam_role_policy.deploy uses policy_arn = "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess" in infra/main.tf.</fact>     <fact>The deploy role no lo... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Changed replicaCount to 3 and memory limit to 2Gi in infra/values.yaml</title>   <subtitle>Helm values now request three replicas with a 2Gi memory cap per pod.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount to 3.</fact>     <fact>infra/values.yaml sets resources.limits.memory to 2Gi.</fact>     <fact>helm template renders 3 pods wit... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added deployment rollback script for claude-mem in scripts/rollback.sh</title>   <subtitle>New rollback script undoes the claude-mem deployment and waits for rollout completion.</subtitle>   <facts>     <fact>scripts/rollback.sh runs kubectl rollout undo deployment/claude-mem.</fact>     <fact>scripts/rollback.sh runs kubectl rollout status deployment/... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Enabled 10 percent canary with 2 percent error rollback in infra/canary.yaml</title>   <subtitle>Canary rollout is enabled at 10 percent and rolls back when errors exceed 2 percent.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true.</fact>     <fact>infra/canary.yaml sets canary.weight to 10 for a 10 percent rollout.</fact>   ... [truncated]
```

### skip-file-listing

```text
We need output skip exact because only glob no edits.
```

### remove-dead-code

```text
<observation>   <type>change</type>   <title>Removed legacySync from OrderService</title>   <subtitle>OrderService.legacySync was deleted, leaving no remaining references to the legacy synchronization method.</subtitle>   <facts>     <fact>OrderService.legacySync was removed from src/main/java/com/acme/orders/OrderService.java.</fact>     <fact>No remaining code references OrderService.legacySync ... [truncated]
```

### remove-stale-config

```text
<observation>   <type>change</type>   <title>Disabled legacy-search.enabled in application.yml</title>   <subtitle>The search module now relies exclusively on the search-v2 configuration.</subtitle>   <facts>     <fact>src/main/resources/application.yml no longer defines legacy-search.enabled.</fact>     <fact>The search module reads only search-v2 after the legacy-search.enabled removal.</fact>  ... [truncated]
```

### remove-deprecated-endpoint

```text
<observation>   <type>change</type>   <title>Removed legacy /v1/users endpoint from UserController</title>   <subtitle>UserController now exposes only the /v2/users endpoint.</subtitle>   <facts>     <fact>UserController.listLegacy was removed from src/main/java/com/acme/users/UserController.java.</fact>     <fact>The @GetMapping("/v1/users") mapping was removed from src/main/java/com/acme/users/U... [truncated]
```

### remove-feature-flag

```text
<observation>   <type>refactor</type>   <title>Removed FeatureFlags.LEGACY_SEARCH constant in FeatureFlags.java</title>   <subtitle>Legacy search flag usage now resolves exclusively to FeatureFlags.SEARCH_V2.</subtitle>   <facts>     <fact>FeatureFlags.LEGACY_SEARCH was removed from src/main/java/com/acme/flags/FeatureFlags.java.</fact>     <fact>Callers of the removed legacy-search flag now use F... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared verb class, (2) the declared subject tokens to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. The legacy keyword-coverage metric is reported alongside for before/after comparison.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
