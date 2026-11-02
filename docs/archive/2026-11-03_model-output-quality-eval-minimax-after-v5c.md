# Model output quality evaluation

- Date: 2026-11-03
- Phase: after
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 226310 ms; temperature: 0.0
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
| title exact-hit rate (legacy) | 42% |
| title normalized hit rate (legacy) | 47% |
| title verb-class hit rate | 73% |
| title subject hit rate | 76% |
| title contract hit rate (verb + subject + all identifiers) | 53% |
| title identifier hit rate | 95% |
| type accuracy | 92% |
| facts keyword precision (legacy) / recall | 76% / 91% |
| facts informative precision | 77% |
| facts trace-support rate | 100% |
| concepts precision / recall | 80% / 80% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 1583 / 4130 / 5848 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 77% | PASS |
| A6 | title contract hit (verb + subject + identifiers) | >= 80% | 53% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 82 | 100% | 47% | 53% | 76% | 77% | 91% | 0 |
| 1 | 13 | 100% | 50% | 50% | 87% | 90% | 97% | 0 |
| 2 | 13 | 100% | 60% | 60% | 90% | 97% | 100% | 0 |

- average: skip 100%, title 52%, title contract 54%, facts P/R 84%/96%, facts actionable 88%
- worst run: skip 100%, title 47%, title contract 50%, facts P/R 76%/91%, facts actionable 77%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 11/12 | 6/12 |
| refactor | 9 | 9/9 | 7/9 | 6/9 |
| test-add | 9 | 9/9 | 9/9 | 5/9 |
| security-fix | 9 | 9/9 | 8/9 | 4/9 |
| config-change | 8 | 8/8 | 8/8 | 3/8 |
| docs-update | 8 | 8/8 | 8/8 | 2/8 |
| deploy-change | 7 | 7/7 | 6/7 | 3/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 4130 |
| bug-null-cache | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/50% | 2400 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 100% | 50%/50% | 2773 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 50%/50% | 1387 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 1334 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 4013 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 50%/50% | 1511 |
| bug-race-counter | bug-fix | observation | observation | pass | 75% | hit | miss | match | 67%/67% | 100% | 100%/100% | 1676 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 25% | MISS | miss | MISS | 67%/100% | 100% | 50%/50% | 3065 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 2265 |
| bug-regex-redos | bug-fix | observation | observation | pass | 50% | MISS | miss | match | 50%/50% | 50% | 100%/100% | 3447 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 1583 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 100% | 100%/100% | 2809 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 100% | 100%/100% | 1540 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | MISS | miss | MISS | 67%/100% | 67% | 50%/50% | 1766 |
| refactor-order-mapper | refactor | observation | observation | pass | 67% | MISS | miss | match | 50%/100% | 50% | 100%/100% | 1467 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 1551 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 33% | 100%/100% | 1609 |
| refactor-sql-repository | refactor | observation | observation | pass | 67% | MISS | miss | MISS | 100%/100% | 67% | 50%/50% | 1529 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 33% | 100%/100% | 1536 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 50% | 100%/100% | 4739 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/67% | 100% | 50%/50% | 2926 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 1654 |
| test-unit-retry | test-add | observation | observation | pass | 75% | MISS | miss | match | 100%/50% | 100% | 100%/100% | 1553 |
| test-integration-order | test-add | observation | observation | pass | 50% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 1774 |
| test-parameterized-csv | test-add | observation | observation | pass | 75% | MISS | miss | match | 100%/50% | 33% | 50%/50% | 1793 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 75%/100% | 100% | 50%/50% | 1908 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 100% | 50%/50% | 3152 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 50%/50% | 2795 |
| test-flaky-clock | test-add | observation | observation | pass | 100% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 2538 |
| security-redact-log | security-fix | observation | observation | pass | 100% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 2422 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 33% | 100%/100% | 1686 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | MISS | miss | match | 50%/50% | 100% | 100%/100% | 1708 |
| security-csrf | security-fix | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 2736 |
| security-secret-config | security-fix | observation | observation | pass | 50% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 1532 |
| security-xxe | security-fix | observation | observation | pass | 33% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 1976 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 1543 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS | miss | match | 33%/100% | 100% | 100%/100% | 2544 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS | miss | MISS | 50%/100% | 100% | 0%/0% | 1284 |
| config-hikari-pool | config-change | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 67% | 100%/100% | 3377 |
| config-flyway-baseline | config-change | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 67% | 100%/100% | 2285 |
| config-log-level | config-change | observation | observation | pass | 75% | MISS | miss | match | 50%/50% | 0% | 100%/100% | 1862 |
| config-cors | config-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 1453 |
| config-timeout | config-change | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 50% | 100%/100% | 2285 |
| config-feature-flags | config-change | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 1429 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 1705 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 4786 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 0% | 100%/100% | 1248 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 33% | 100%/100% | 1381 |
| docs-runbook | docs-update | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 0% | 100%/100% | 1521 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 2923 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 50%/50% | 1452 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 0%/0% | 1537 |
| docs-security-policy | docs-update | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 4250 |
| docs-contributing | docs-update | observation | observation | pass | 33% | hit | miss | match | 100%/100% | 50% | 100%/100% | 1452 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 1467 |
| deploy-github-actions | deploy-change | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 1425 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 100% | 50%/50% | 1654 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 50% | 100%/100% | 1549 |
| deploy-helm-values | deploy-change | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 1283 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 33% | 50%/50% | 1644 |
| deploy-canary | deploy-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 3094 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 939 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 949 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1126 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 998 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 742 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 853 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 668 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5848 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2289 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1143 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1035 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 818 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 964 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 976 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 975 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 823 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 820 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2744 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 743 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3109 |

## Problems

- bug-offbyone-pagination: title coverage 67% (expected keywords [fix, pagination, orderquery])
- bug-offbyone-pagination: title subject [pagination] missing (got 'Fixed OrderQuery.pageOffset first-page offset calculation')
- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-csv-crlf: facts recall 50% (expected [CRLF, multilineFieldKeepsRowCount], got [CsvExporter now applies line.replaceAll("\\r?\\n", "\\r\\n") before writing each CSV line., CsvExporterTest#multilineFieldKeepsRowCount passes after embedded line endings are normalized.])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: title coverage 75% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: facts recall 67% (expected [incrementAndGet, AtomicLong, 1000], got [CounterService.increment now returns counter.incrementAndGet() instead of counter.set(counter.get() + 1) followed by counter.get()., CounterServiceConcurrencyTest passes with 1000 threads producing a count of 1000., src/main/java/com/acme/metrics/CounterService.java contains the atomic counter update.])
- bug-n-plus-one: title coverage 25% (expected keywords [fix, n+1, orderrepository, entitygraph])
- bug-n-plus-one: title verb class 'fix' missing (got 'Changed OrderRepository.findAllByCustomerId to fetch items eagerly')
- bug-n-plus-one: title subject [n+1] missing (got 'Changed OrderRepository.findAllByCustomerId to fetch items eagerly')
- bug-n-plus-one: type 'change' != expected 'bugfix'
- bug-n-plus-one: concepts recall 50% (expected [problem-solution, pattern], got [what-changed, pattern])
- bug-regex-redos: title coverage 50% (expected keywords [fix, redos, urlvalidator, regex])
- bug-regex-redos: title subject [redos] missing (got 'Fixed UrlValidator long input nested-quantifier performance')
- bug-regex-redos: facts recall 50% (expected [linear pattern, longInputFinishesUnder100ms], got [src/main/java/com/acme/validation/UrlValidator.java defines URL as Pattern.compile("^https?://[^\\s]+$")., UrlValidatorTest#longInputFinishesUnder100ms passes.])
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed FileReaderFactory default charset to UTF-8')
- refactor-auth-controller: title subject [delegation] missing (got 'Refactored JWT issuance to TokenService.issue in AuthController')
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title verb class 'refactor' missing (got 'Changed ConfigLoader configuration map to unmodifiable in ConfigLoader.java')
- refactor-config-loader: type 'change' != expected 'refactor'
- refactor-config-loader: concepts recall 50% (expected [what-changed, pattern], got [what-changed, trade-off])
- refactor-order-mapper: title coverage 67% (expected keywords [refactor, ordermapper, convertvalue])
- refactor-order-mapper: title subject [convertValue] missing (got 'Refactored OrderMapper entity conversion in OrderMapper.java')
- refactor-retry-policy: facts recall 50% (expected [BackoffStrategy, 100/200/400], got [RetryPolicy returns backoff.nextDelay(attempt) instead of calculating the delay inline., RetryPolicyTest asserts the unchanged 100/200/400 retry-delay sequence.])
- refactor-sql-repository: title coverage 67% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title verb class 'refactor' missing (got 'Fixed UserRepository tenant filtering with parameterized JPQL')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- refactor-event-bus: facts recall 50% (expected [listener map, event type], got [EventBus.java replaces the if/else dispatch chain with listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event))., EventBus.java stores listeners in a map keyed by event type., EventBusTest dispatches the same three events after the dispatch refactor.])
- test-repro-timezone: facts recall 67% (expected [Asia/Shanghai, 2026-11-02, 1 failure], got [DailyReportTest#reportUsesBusinessTimezone passes Instant.parse("2026-11-01T23:30:00Z") to report.dateAt with ZoneId.of("Asia/Shanghai")., DailyReportTest#reportUsesBusinessTimezone expects LocalDate.of(2026, 11, 2)., mvn -Dtest=DailyReportTest test reports 1 test and 1 failure because the actual date is 2026-11-01.])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: title coverage 75% (expected keywords [add, retrypolicy, parameterized, test])
- test-unit-retry: title subject [parameterized] missing (got 'Added RetryPolicyTest#delayDoubles exponential delay cases')
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles covers attempt values 1, 2, and 3 from @CsvSource., RetryPolicyTest#delayDoubles expects policy.delay(attempt) values of 100, 200, and 400 milliseconds., RetryPolicyTest#delayDoubles runs as 3 passing parameterized test cases.])
- test-integration-order: title coverage 50% (expected keywords [add, integration, order, test])
- test-integration-order: title subject [integration, test] missing (got 'Added OrderApiIT#createOrderReturns201 for order creation')
- test-parameterized-csv: title coverage 75% (expected keywords [add, csv, parameterized, test])
- test-parameterized-csv: title subject [parameterized] missing (got 'Added CsvExporterTest#quotingIsStable for CSV quoting cases')
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable is a parameterized test using @ValueSource(strings = {"a,b","a\"b","a\nb"})., CsvExporterTest#quotingIsStable covers CSV values containing commas, quotes, and newlines., All 3 CsvExporterTest#quotingIsStable cases pass.])
- test-parameterized-csv: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-security-headers: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-repro-null-cache: title lost identifiers [CacheConcurrencyTest, UserCache] (got 'Added CacheConcurrencyTest#concurrentReadersNeverSeeNull regression test')
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-contract-openapi: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-flaky-clock: title verb class 'fix' missing (got 'Added fixed Clock to SchedulerTest to remove Thread.sleep flakiness')
- security-redact-log: title verb class 'redact' missing (got 'Fixed credential redaction in LogSanitizer.java')
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail executes jdbcTemplate.query("select * from users where email = ?", mapper, email)., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the UserDao.findByEmail change., The edited file is src/main/java/com/acme/users/UserDao.java.])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Fixed AdminController authorization for every endpoint with ROLE_ADMIN')
- security-authz-admin: facts recall 50% (expected [ROLE_ADMIN, 403], got [AdminController uses @PreAuthorize("hasRole('ADMIN')") for every endpoint., AdminAuthorizationTest#userRoleGets403 passes after the authorization change.])
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter sends response.sendError(403) when csrfToken does not match request., CsrfFilterTest#postWithoutTokenIs403 passes after the CsrfFilter validation change.])
- security-secret-config: title lost identifiers [application.yml, ACME_API_KEY] (got 'Fixed literal API key exposure in application.yml')
- security-secret-config: title verb class 'remove' missing (got 'Fixed literal API key exposure in application.yml')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Fixed literal API key exposure in application.yml')
- security-xxe: title coverage 33% (expected keywords [harden, xmlimportservice, xxe])
- security-xxe: title verb class 'harden' missing (got 'Fixed XML external entity processing in XmlImportService')
- security-xxe: title subject [xxe] missing (got 'Fixed XML external entity processing in XmlImportService')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title verb class 'add' missing (got 'Fixed RateLimiter empty token bucket with 429 response')
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig.java')
- security-cookie-httponly: type 'change' != expected 'bugfix'
- security-cookie-httponly: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, trade-off])
- config-hikari-pool: title lost identifiers [maximum-pool-size] (got 'Set Hikari connection pool sizing in application.yml')
- config-hikari-pool: title verb class 'change' missing (got 'Set Hikari connection pool sizing in application.yml')
- config-flyway-baseline: title coverage 50% (expected keywords [enable, flyway, baseline, migrate])
- config-flyway-baseline: title verb class 'enable' missing (got 'Changed Flyway baseline configuration in pom.xml')
- config-log-level: title coverage 75% (expected keywords [change, logback, root, json])
- config-log-level: title verb class 'change' missing (got 'Set root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: title subject [log, level] missing (got 'Set root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: facts recall 50% (expected [INFO, JSON appender], got [src/main/resources/logback-spring.xml configures root level="INFO" and references appender ref="JSON"., LogbackConfigTest parses src/main/resources/logback-spring.xml.])
- config-timeout: title coverage 75% (expected keywords [set, webclient, timeout, 5])
- config-timeout: title verb class 'set' missing (got 'Changed WebClient response timeout to 5 seconds in WebClientConfig.java')
- config-feature-flags: title coverage 50% (expected keywords [enable, checkout-v2, disable, legacy-search])
- config-feature-flags: title verb class 'enable' missing (got 'Changed checkout-v2 and legacy-search flags in feature-flags.yml')
- config-feature-flags: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title verb class 'add' missing (got 'Documented queue recovery procedure in docs/runbook.md')
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-architecture-adr: concepts recall 50% (expected [why-it-exists, trade-off], got [why-it-exists, gotcha])
- docs-migration-guide: title coverage 75% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: concepts recall 0% (expected [what-changed, gotcha], got [pattern, trade-off])
- docs-security-policy: title coverage 75% (expected keywords [add, security, policy, disclosure])
- docs-security-policy: title verb class 'add' missing (got 'Changed vulnerability disclosure policy in SECURITY.md')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 33% (expected keywords [update, contributing, verify-local])
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [trade-off, pattern])
- deploy-github-actions: title coverage 75% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: title verb class 'add' missing (got 'Changed CI Java matrix to JDK 21 and 25 in .github/workflows/ci.yml')
- deploy-docker-multistage: concepts recall 50% (expected [what-changed, trade-off], got [trade-off, pattern])
- deploy-terraform-iam: title coverage 50% (expected keywords [restrict, iam, terraform, s3])
- deploy-terraform-iam: title subject [iam, policy] missing (got 'Restricted deploy role to AmazonS3ReadOnlyAccess in infra/main.tf')
- deploy-helm-values: title coverage 75% (expected keywords [change, helm, replicas, memory])
- deploy-helm-values: title subject [helm, values] missing (got 'Changed replicaCount and memory limit in infra/values.yaml')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-rollback-script: concepts recall 50% (expected [what-changed, pattern], got [pattern, trade-off])
- deploy-canary: title coverage 75% (expected keywords [enable, canary, rollback, threshold])

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset first-page offset calculation</title>   <subtitle>OrderQuery.pageOffset now starts at zero for the first page and matches the expected pagination boundary.</subtitle>   <facts>     <fact>OrderQuery.pageOffset now calculates page * size without subtracting 1.</fact>     <fact>OrderQueryTest#firstPageStartsAtZero passes after t... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache.get null placeholder race with computeIfAbsent</title>   <subtitle>UserCache.get now atomically loads missing entries through computeIfAbsent.</subtitle>   <facts>     <fact>UserCache.get replaces cache.get(id) with cache.computeIfAbsent(id, loader::load).</fact>     <fact>UserCache.get prevents two concurrent callers from both writing a... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for off-by-one-day bug</title>   <subtitle>Regression test pins UTC-to-Asia/Shanghai business-date conversion for a late-evening report timestamp.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone was added to src/test/java/com/acme/reporting/DailyReportTest.java.</fact>     <fact... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed missing orderId null result in PaymentService</title>   <subtitle>PaymentService now throws PaymentNotFoundException instead of returning null for an unknown order.</subtitle>   <facts>     <fact>PaymentService returns repository.findByOrderId(orderId).orElseThrow(() -&gt; new PaymentNotFoundException(orderId)).</fact>     <fact>PaymentServiceTest... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration boundary equality in JwtVerifier.java</title>   <subtitle>JwtVerifier now rejects tokens expiring exactly at the current instant.</subtitle>   <facts>     <fact>JwtVerifier.java changes the expiration check to reject claims.getExpiration().isEqual(now) as well as claims.getExpiration().isBefore(now).</fact>     <fact>JwtVeri... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsvExporter embedded line endings normalization</title>   <subtitle>CsvExporter now converts embedded newlines to CRLF before quoting multiline fields.</subtitle>   <facts>     <fact>CsvExporter now applies line.replaceAll("\\r?\\n", "\\r\\n") before writing each CSV line.</fact>     <fact>CsvExporterTest#multilineFieldKeepsRowCount passes after e... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment atomic counter update race</title>   <subtitle>AtomicLong.incrementAndGet prevents lost concurrent increments in CounterService.</subtitle>   <facts>     <fact>CounterService.increment now returns counter.incrementAndGet() instead of counter.set(counter.get() + 1) followed by counter.get().</fact>     <fact>CounterServiceC... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>change</type>   <title>Changed OrderRepository.findAllByCustomerId to fetch items eagerly</title>   <subtitle>OrderRepository now retrieves items with orders in one query, reducing N+1 SQL execution.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId(String customerId) uses @EntityGraph(attributePaths = "items").</fact>     <fact>OrderRepository.findAllByCusto... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator long input nested-quantifier performance</title>   <subtitle>UrlValidator now uses a linear URL regex, and the long-input performance regression test passes.</subtitle>   <facts>     <fact>src/main/java/com/acme/validation/UrlValidator.java defines URL as Pattern.compile("^https?://[^\\s]+$").</fact>     <fact>UrlValidatorTest#longInp... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed FileReaderFactory default charset to UTF-8</title>   <subtitle>FileReaderFactory now decodes files as UTF-8, preserving accented characters on GBK-default JVMs.</subtitle>   <facts>     <fact>FileReaderFactory constructs InputStreamReader with StandardCharsets.UTF_8 instead of the platform default charset.</fact>     <fact>FileReaderFactoryTest#ut... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored JWT issuance to TokenService.issue in AuthController</title>   <subtitle>AuthController delegates token creation while preserving green AuthControllerTest coverage.</subtitle>   <facts>     <fact>AuthController now returns tokenService.issue(authentication) instead of constructing a JWT inline.</fact>     <fact>AuthControllerTest remains un... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>change</type>   <title>Changed ConfigLoader configuration map to unmodifiable in ConfigLoader.java</title>   <subtitle>ConfigLoader now returns an unmodifiable property map and retains typed accessors.</subtitle>   <facts>     <fact>ConfigLoader.java uses properties.entrySet().stream().collect(toUnmodifiableMap(...)) for the returned configuration map.</fact>     <fact>Config... [truncated]
```

### refactor-order-mapper

```text
<observation>   <type>refactor</type>   <title>Refactored OrderMapper entity conversion in OrderMapper.java</title>   <subtitle>OrderMapper now uses one convertValue call while OrderMapperTest verifies equivalent DTO fields.</subtitle>   <facts>     <fact>src/main/java/com/acme/orders/OrderMapper.java replaces 40 hand-written setters with mapper.convertValue(entity, OrderDto.class).</fact>     <fa... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Refactored RetryPolicy delay calculation into BackoffStrategy</title>   <subtitle>RetryPolicy now delegates retry delays to BackoffStrategy while preserving the existing delay sequence.</subtitle>   <facts>     <fact>RetryPolicy returns backoff.nextDelay(attempt) instead of calculating the delay inline.</fact>     <fact>RetryPolicyTest asserts the unc... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Fixed UserRepository tenant filtering with parameterized JPQL</title>   <subtitle>UserRepository now uses a tenantId parameter for retrieving tenant-scoped User rows.</subtitle>   <facts>     <fact>UserRepository defines `@Query("select u from User u where u.tenantId = :tenantId")`.</fact>     <fact>UserRepositoryTest returns the same tenant rows after ... [truncated]
```

### refactor-event-bus

```text
<observation>   <type>refactor</type>   <title>Refactored EventBus dispatch to listener map in EventBus.java</title>   <subtitle>EventBus now dispatches each event through listeners registered for its event type.</subtitle>   <facts>     <fact>EventBus.java replaces the if/else dispatch chain with listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event)).</fact>     <fact>Eve... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai</title>   <subtitle>A regression test pins business-date conversion across the UTC midnight boundary.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone passes Instant.parse("2026-11-01T23:30:00Z") to report.dateAt with ZoneId.of("Asia/Shanghai").</fact>     <fact... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 for locked accounts</title>   <subtitle>Regression test pins the expected HTTP 423 response when the login account is locked.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true.</fact>     <fact>LoginControllerTest#lockedAccountReturns42... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles exponential delay cases</title>   <subtitle>Parameterized tests verify retry delays double from 100 ms through 400 ms.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles covers attempt values 1, 2, and 3 from @CsvSource.</fact>     <fact>RetryPolicyTest#delayDoubles expects policy.delay(attempt) values of 100... [truncated]
```

### test-integration-order

```text
<observation>   <type>feature</type>   <title>Added OrderApiIT#createOrderReturns201 for order creation</title>   <subtitle>Integration coverage verifies successful order creation and the Location response header.</subtitle>   <facts>     <fact>src/test/java/com/acme/orders/OrderApiIT.java defines OrderApiIT with @SpringBootTest(webEnvironment = RANDOM_PORT).</fact>     <fact>OrderApiIT#createOrde... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable for CSV quoting cases</title>   <subtitle>Parameterized test covers commas, embedded quotes, and newlines in CsvExporter values.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable is a parameterized test using @ValueSource(strings = {"a,b","a\"b","a\nb"}).</fact>     <fact>CsvExporterTest#quotingIsStabl... [truncated]
```

### test-security-headers

```text
<observation>   <type>feature</type>   <title>Added SecurityHeadersTest#responseHasCspAndNosniff for security headers</title>   <subtitle>Security header behavior is now pinned with a Spring MVC test, and the observed test suite reports 2 passing tests.</subtitle>   <facts>     <fact>src/test/java/com/acme/web/SecurityHeadersTest.java adds SecurityHeadersTest#responseHasCspAndNosniff.</fact>     <... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull regression test</title>   <subtitle>A 1000-reader parallel test reproduces UserCache intermittent null results and passes after the fix.</subtitle>   <facts>     <fact>src/test/java/com/acme/cache/CacheConcurrencyTest.java adds CacheConcurrencyTest#concurrentReadersNeverSeeNull.</fact>     <fact>... [truncated]
```

### test-contract-openapi

```text
<observation>   <type>feature</type>   <title>Added OpenApiContractTest#generatedSpecMatchesCommittedFile</title>   <subtitle>Contract test detects drift between springdoc output and the committed OpenAPI specification.</subtitle>   <facts>     <fact>src/test/java/com/acme/api/OpenApiContractTest.java defines OpenApiContractTest#generatedSpecMatchesCommittedFile.</fact>     <fact>OpenApiContractTe... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Added fixed Clock to SchedulerTest to remove Thread.sleep flakiness</title>   <subtitle>SchedulerTest now uses a deterministic UTC clock, and 20 consecutive test runs pass.</subtitle>   <facts>     <fact>src/test/java/com/acme/scheduling/SchedulerTest.java configures scheduler.clock with Clock.fixed at 2026-11-03T00:00:00Z and ZoneOffset.UTC.</fact>   ... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed credential redaction in LogSanitizer.java</title>   <subtitle>LogSanitizer now masks password, token, and secret values before log output.</subtitle>   <facts>     <fact>LogSanitizer replaces values matching password, token, or secret key-value pairs with a masked value.</fact>     <fact>LogSanitizerTest#redactsCredentials passes for password, tok... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed UserDao.findByEmail SQL injection via parameterized email query</title>   <subtitle>UserDao.findByEmail now binds email with a placeholder instead of concatenating untrusted input.</subtitle>   <facts>     <fact>UserDao.findByEmail executes jdbcTemplate.query("select * from users where email = ?", mapper, email).</fact>     <fact>SqlInjectionTest#... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Fixed AdminController authorization for every endpoint with ROLE_ADMIN</title>   <subtitle>AdminController now requires ROLE_ADMIN, and user-role access is verified to return HTTP 403.</subtitle>   <facts>     <fact>AdminController uses @PreAuthorize("hasRole('ADMIN')") for every endpoint.</fact>     <fact>AdminAuthorizationTest#userRoleGets403 passes a... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsrfFilter mismatched token rejection in CsrfFilter.java</title>   <subtitle>State-changing requests now receive HTTP 403 when the CSRF token does not match the request.</subtitle>   <facts>     <fact>CsrfFilter sends response.sendError(403) when csrfToken does not match request.</fact>     <fact>CsrfFilterTest#postWithoutTokenIs403 passes after t... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Fixed literal API key exposure in application.yml</title>   <subtitle>application.yml now sources ACME_API_KEY from the environment instead of embedding a secret.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets api-key to ${ACME_API_KEY}.</fact>     <fact>src/main/resources/application.yml no longer contains the literal value sk-l... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Fixed XML external entity processing in XmlImportService</title>   <subtitle>XmlImportService disables DOCTYPE declarations, and XxeTest#externalEntityIsRejected passes.</subtitle>   <facts>     <fact>XmlImportService sets http://apache.org/xml/features/disallow-doctype-decl to true.</fact>     <fact>XxeTest#externalEntityIsRejected passes after the XML... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter empty token bucket with 429 response</title>   <subtitle>RateLimiter throws TooManyRequestsException when the token bucket cannot consume another request.</subtitle>   <facts>     <fact>RateLimiter calls bucket.tryConsume(1) and throws TooManyRequestsException when consumption fails.</fact>     <fact>RateLimiter returns HTTP 429 when t... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>change</type>   <title>Hardened session cookie flags in SessionCookieConfig.java</title>   <subtitle>Session cookies now use HttpOnly, Secure, and SameSite=Strict protections.</subtitle>   <facts>     <fact>SessionCookieConfig.java sets cookie.setHttpOnly(true).</fact>     <fact>SessionCookieConfig.java sets cookie.setSecure(true).</fact>     <fact>SessionCookieConfig.java se... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Set Hikari connection pool sizing in application.yml</title>   <subtitle>Hikari now uses a maximum pool size of 20 and maintains five minimum idle connections.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20.</fact>     <fact>src/main/resources/application.yml sets spring.datasource... [truncated]
```

### config-flyway-baseline

```text
<observation>   <type>change</type>   <title>Changed Flyway baseline configuration in pom.xml</title>   <subtitle>Flyway now baselines existing schemas at version 1 when migration runs.</subtitle>   <facts>     <fact>pom.xml sets flyway.baselineVersion to 1.</fact>     <fact>pom.xml sets flyway.baselineOnMigrate to true.</fact>     <fact>Maven build resolves flyway.baselineVersion and flyway.basel... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Set root logger to INFO with JSON appender in logback-spring.xml</title>   <subtitle>Logback now emits root-level INFO logs through the JSON appender, covered by XML parsing.</subtitle>   <facts>     <fact>src/main/resources/logback-spring.xml configures root level="INFO" and references appender ref="JSON".</fact>     <fact>LogbackConfigTest parses src/... [truncated]
```

### config-timeout

```text
<observation>   <type>change</type>   <title>Changed WebClient response timeout to 5 seconds in WebClientConfig.java</title>   <subtitle>Reactor Netty HTTP clients now wait at most five seconds for response data.</subtitle>   <facts>     <fact>WebClientConfig.java configures HttpClient.create().responseTimeout(Duration.ofSeconds(5)).</fact>     <fact>WebClientConfigTest reads the configured respon... [truncated]
```

### config-feature-flags

```text
<observation>   <type>change</type>   <title>Changed checkout-v2 and legacy-search flags in feature-flags.yml</title>   <subtitle>Feature flag configuration enables checkout-v2 and disables legacy-search, with FlagBindingTest binding both values.</subtitle>   <facts>     <fact>src/main/resources/feature-flags.yml sets checkout-v2 to true and legacy-search to false.</fact>     <fact>FlagBindingTest... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added healthcheck for /actuator/health in Dockerfile</title>   <subtitle>Docker images now probe the application health endpoint every 30 seconds.</subtitle>   <facts>     <fact>Dockerfile defines HEALTHCHECK with --interval=30s.</fact>     <fact>Dockerfile HEALTHCHECK runs curl -f http://localhost:8080/actuator/health and exits 1 on failure.</fact>    ... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Changed local setup instructions in README.md</title>   <subtitle>README now documents a three-step local development setup.</subtitle>   <facts>     <fact>README.md requires JDK 21 for local setup.</fact>     <fact>README.md instructs developers to run `docker compose up -d postgres`.</fact>     <fact>README.md instructs developers to run `mvn spring-b... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Documented queue recovery procedure in docs/runbook.md</title>   <subtitle>Queue recovery now directs operators to inspect pending_messages, restart the worker, and verify /api/health.</subtitle>   <facts>     <fact>docs/runbook.md documents the queue recovery step "check pending_messages".</fact>     <fact>docs/runbook.md documents the queue recovery s... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Changed CHANGELOG.md release notes for 2.4.0</title>   <subtitle>Release notes now document offline replay evaluation and the V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md contains the release heading "## 2.4.0 - 2026-11-03".</fact>     <fact>CHANGELOG.md lists "Added offline replay evaluation" for version 2.4.0.</fact>     <fa... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>decision</type>   <title>Recorded extracted-fact validation decision in docs/adr/0007-extraction-validation.md</title>   <subtitle>ADR 0007 requires validating extracted facts against tool output before persistence and describes rejection handling.</subtitle>   <facts>     <fact>docs/adr/0007-extraction-validation.md records the decision to validate extracted facts against to... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisites in docs/MIGRATION.md</title>   <subtitle>V20 requires a mem_sessions backup and manual sentinel review before migration.</subtitle>   <facts>     <fact>docs/MIGRATION.md specifies backing up mem_sessions before the V20 migration.</fact>     <fact>docs/MIGRATION.md requires app.manual_sentinel_reviewed=true for the ... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Changed vulnerability disclosure policy in SECURITY.md</title>   <subtitle>SECURITY.md directs vulnerability reports to security@acme.com and prohibits public issues.</subtitle>   <facts>     <fact>SECURITY.md sets security@acme.com as the vulnerability reporting channel.</fact>     <fact>SECURITY.md forbids opening public issues for vulnerabilities.</f... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Changed CONTRIBUTING.md pull request verification requirements</title>   <subtitle>CONTRIBUTING.md now requires ./scripts/verify-local.sh and documents the four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md requires running ./scripts/verify-local.sh before opening a pull request.</fact>     <fact>CONTRIBUTING.md names four test layers.</fa... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added readinessProbe /api/health to deployment.yaml</title>   <subtitle>Kubernetes deployment now checks the application health endpoint before routing traffic.</subtitle>   <facts>     <fact>deploy/deployment.yaml defines a readinessProbe at /api/health on port 37777.</fact>     <fact>deploy/deployment.yaml sets readinessProbe.initialDelaySeconds to 15... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Changed CI Java matrix to JDK 21 and 25 in .github/workflows/ci.yml</title>   <subtitle>The backend CI workflow now tests against Java 21 and Java 25.</subtitle>   <facts>     <fact>.github/workflows/ci.yml sets strategy.matrix.java to [21, 25].</fact>     <fact>.github/workflows/ci.yml runs the backend on JDK 21 and JDK 25.</fact>     <fact>The workflo... [truncated]
```

### deploy-docker-multistage

```text
<observation>   <type>change</type>   <title>Converted Dockerfile to multi-stage with eclipse-temurin:21-jre</title>   <subtitle>Runtime images now contain only the packaged Java application, reducing image size from 780MB to 210MB.</subtitle>   <facts>     <fact>Dockerfile now copies /app/target/app.jar into /app/app.jar from the build stage.</fact>     <fact>Dockerfile uses eclipse-temurin:21-jr... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted deploy role to AmazonS3ReadOnlyAccess in infra/main.tf</title>   <subtitle>The deploy IAM role now grants read-only S3 access instead of full S3 permissions.</subtitle>   <facts>     <fact>infra/main.tf defines aws_iam_role_policy.deploy with policy_arn = "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess".</fact>     <fact>terraform validate su... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Changed replicaCount and memory limit in infra/values.yaml</title>   <subtitle>Helm deployment now renders three replicas with a 2Gi memory cap.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount to 3.</fact>     <fact>infra/values.yaml sets resources.limits.memory to 2Gi.</fact>     <fact>helm template renders 3 pods.</fact>   </facts> ... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added deployment rollback script scripts/rollback.sh</title>   <subtitle>Rollback script undoes the claude-mem deployment and waits for rollout completion.</subtitle>   <facts>     <fact>scripts/rollback.sh runs `kubectl rollout undo deployment/claude-mem`.</fact>     <fact>scripts/rollback.sh runs `kubectl rollout status deployment/claude-mem` after t... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Enabled canary rollback at 10 percent weight in infra/canary.yaml</title>   <subtitle>Canary traffic now uses a 10 percent weight with automatic rollback above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true.</fact>     <fact>infra/canary.yaml sets canary.weight to 10 for a 10 percent canary rollout.<... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared verb class, (2) the declared subject tokens to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. The legacy keyword-coverage metric is reported alongside for before/after comparison.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
