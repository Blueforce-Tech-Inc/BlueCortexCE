# Model output quality evaluation

- Date: 2026-11-03
- Phase: after
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 207814 ms; temperature: 0.0
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
| title exact-hit rate (legacy) | 55% |
| title normalized hit rate (legacy) | 55% |
| title action-class hit rate (A6) | 77% (62 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 73% |
| title subject hit rate | 79% |
| title contract hit rate (action + subject + all identifiers) | 60% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 53% |
| title identifier hit rate | 95% |
| type accuracy | 95% |
| facts keyword precision (legacy) / recall | 79% / 93% |
| facts informative precision | 81% |
| facts trace-support rate | 100% |
| concepts precision / recall | 81% / 81% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 1638 / 3446 / 5236 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 81% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 77% | **FAIL** |
| A6b | title subject | >= 90% | 79% | **FAIL** |
| A6c | title identifiers | = 100% | 95% | **FAIL** |
| A6 | all three title dimensions together | all pass | 60% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 82 | 100% | 55% | 60% | 79% | 81% | 93% | 0 |
| 1 | 13 | 100% | 70% | 70% | 93% | 90% | 100% | 0 |
| 2 | 13 | 100% | 60% | 60% | 90% | 87% | 100% | 0 |

- average: skip 100%, title 62%, title contract 63%, facts P/R 88%/98%, facts actionable 86%
- worst run: skip 100%, title 55%, title contract 60%, facts P/R 79%/93%, facts actionable 81%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 12/12 | 6/12 |
| refactor | 9 | 9/9 | 8/9 | 7/9 |
| test-add | 9 | 9/9 | 9/9 | 7/9 |
| security-fix | 9 | 9/9 | 8/9 | 3/9 |
| config-change | 8 | 8/8 | 8/8 | 6/8 |
| docs-update | 8 | 8/8 | 8/8 | 3/8 |
| deploy-change | 7 | 7/7 | 6/7 | 2/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 1566 |
| bug-null-cache | bug-fix | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 1712 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 1769 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 2986 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1750 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/67% | 100% | 100%/100% | 1676 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 50% | MISS | miss | match | 67%/100% | 100% | 50%/50% | 4970 |
| bug-race-counter | bug-fix | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 3232 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1518 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1669 |
| bug-regex-redos | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 33%/50% | 67% | 100%/100% | 1576 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 1478 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 67% | 100%/100% | 2123 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | MISS | hit | match | 100%/50% | 100% | 100%/100% | 2443 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 2730 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 1521 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 1349 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 100%/100% | 1216 |
| refactor-sql-repository | refactor | observation | observation | pass | 67% | hit | miss | MISS | 50%/50% | 100% | 50%/50% | 3331 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 100%/100% | 1741 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 50%/50% | 50% | 100%/100% | 1338 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 1740 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 1630 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 100% | 100%/100% | 1620 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 1355 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1433 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 4974 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 67% | 50%/50% | 3046 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 50%/50% | 2056 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 1494 |
| security-redact-log | security-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 2559 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | MISS | hit | match | 33%/50% | 100% | 100%/100% | 5236 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 50% | 50%/50% | 1698 |
| security-csrf | security-fix | observation | observation | pass | 100% | MISS | hit | match | 33%/50% | 100% | 100%/100% | 3446 |
| security-secret-config | security-fix | observation | observation | pass | 50% | MISS | miss | MISS | 100%/100% | 100% | 0%/0% | 2818 |
| security-xxe | security-fix | observation | observation | pass | 67% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 3407 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | MISS | hit | match | 67%/100% | 100% | 100%/100% | 2390 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 100% | 50%/50% | 1859 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS | miss | match | 50%/100% | 100% | 50%/50% | 2550 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 67% | 50%/50% | 2223 |
| config-flyway-baseline | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 67% | 100%/100% | 2976 |
| config-log-level | config-change | observation | observation | pass | 100% | MISS | hit | match | 33%/50% | 33% | 100%/100% | 1553 |
| config-cors | config-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 1933 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 2001 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 1381 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 100% | 50%/50% | 1735 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 100% | 50%/50% | 1323 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 1884 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 33% | 100%/100% | 1615 |
| docs-runbook | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 33% | 100%/100% | 2407 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 1450 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 50%/50% | 1437 |
| docs-migration-guide | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 1477 |
| docs-security-policy | docs-update | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 50% | 50%/50% | 2182 |
| docs-contributing | docs-update | observation | observation | pass | 33% | hit | miss | match | 100%/100% | 50% | 100%/100% | 2088 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | MISS | hit | match | 67%/100% | 67% | 100%/100% | 1461 |
| deploy-github-actions | deploy-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 3452 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 100%/100% | 2513 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 50% | MISS | miss | match | 67%/100% | 67% | 50%/50% | 3144 |
| deploy-helm-values | deploy-change | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 2274 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 33% | 50%/50% | 2519 |
| deploy-canary | deploy-change | observation | observation | pass | 50% | hit | miss | match | 67%/100% | 100% | 100%/100% | 1453 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 845 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1298 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 984 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 821 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 844 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 808 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1638 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 787 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 757 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2360 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1540 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 847 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 808 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1391 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1097 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1147 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 829 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 816 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 918 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 795 |

## Problems

- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed concurrent null placeholder writes in UserCache.get')
- bug-null-cache: title subject [race] missing (got 'Fixed concurrent null placeholder writes in UserCache.get')
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-jwt-expiry: facts recall 67% (expected [expiration, isEqual, rejects], got [JwtVerifier evaluates claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now) before accepting a token., JwtVerifierTest#expiredAtExactBoundary passes after the expiration-boundary change.])
- bug-csv-crlf: title coverage 50% (expected keywords [fix, csv, line, endings])
- bug-csv-crlf: title subject [line, endings] missing (got 'Fixed multiline CSV row count with CRLF normalization in CsvExporter')
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: title coverage 50% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: title subject [race] missing (got 'Fixed CounterService.increment atomic updates in CounterService.java')
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-regex-redos: title coverage 75% (expected keywords [fix, redos, urlvalidator, regex])
- bug-regex-redos: title subject [redos] missing (got 'Fixed UrlValidator long input regex performance in UrlValidator.java')
- bug-regex-redos: facts recall 50% (expected [linear pattern, longInputFinishesUnder100ms], got [UrlValidator.java defines URL as Pattern.compile("^https?://[^\\s]+$")., UrlValidatorTest#longInputFinishesUnder100ms passes with the linear URL pattern., UrlValidator.java no longer uses the nested-quantifier regex for URL validation.])
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed UTF-8 decoding in FileReaderFactory')
- refactor-auth-controller: title subject [delegation] missing (got 'Refactored AuthController JWT issuance with TokenService.issue')
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [AuthController now returns tokenService.issue(authentication) instead of constructing JWTs inline., AuthControllerTest remained unchanged and passed after the TokenService.issue delegation.])
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title subject [unmodifiable, map] missing (got 'Refactored ConfigLoader properties map with typed accessors')
- refactor-sql-repository: title coverage 67% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title verb class 'refactor' missing (got 'Fixed UserRepository tenant filtering with parameterized JPQL query')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [UserRepository uses @Query("select u from User u where u.tenantId = :tenantId")., UserRepositoryTest returns the same tenant rows after replacing string-concatenated SQL.])
- refactor-sql-repository: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- refactor-feature-flag: facts recall 50% (expected [Flags.isEnabled, on/off matrix], got [src/main/java/com/acme/flags/FeatureFlagService.java returns flags.isEnabled(flag, context)., FeatureFlagServiceTest retains the same on/off evaluation matrix.])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles uses @ParameterizedTest and @CsvSource with cases 1,100; 2,200; and 3,400., RetryPolicyTest#delayDoubles asserts that policy.delay(attempt) equals expected for attempts 1-3., RetryPolicyTest#delayDoubles ran 3 tests successfully.])
- test-repro-null-cache: title lost identifiers [CacheConcurrencyTest, UserCache] (got 'Added CacheConcurrencyTest#concurrentReadersNeverSeeNull for concurrent cache reads')
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-contract-openapi: concepts recall 50% (expected [what-changed, pattern], got [pattern, problem-solution])
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- test-flaky-clock: title action 'modify' missing: title leads 'create' (got 'Added deterministic Clock.fixed scheduler test in SchedulerTest.java')
- test-flaky-clock: title verb class 'fix' missing (got 'Added deterministic Clock.fixed scheduler test in SchedulerTest.java')
- test-flaky-clock: title subject [flaky, clock] missing (got 'Added deterministic Clock.fixed scheduler test in SchedulerTest.java')
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: title action 'secure' missing: title leads 'modify' (got 'Fixed credential leakage in LogSanitizer')
- security-redact-log: title verb class 'redact' missing (got 'Fixed credential leakage in LogSanitizer')
- security-sql-injection: title action 'secure' missing: title leads 'modify' (got 'Fixed UserDao.findByEmail SQL injection with parameter placeholder')
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail uses the SQL placeholder `email = ?` instead of concatenating the email value., UserDao.findByEmail passes the email value through the parameterized `jdbcTemplate.query` call., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the query change.])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title action 'secure' missing: title leads 'modify' (got 'Fixed AdminController authorization to require ADMIN on every endpoint')
- security-authz-admin: title verb class 'harden' missing (got 'Fixed AdminController authorization to require ADMIN on every endpoint')
- security-authz-admin: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- security-csrf: title action 'secure' missing: title leads 'modify' (got 'Fixed CsrfFilter mismatched-token rejection for state-changing requests')
- security-csrf: facts recall 50% (expected [403, mismatched token], got [src/main/java/com/acme/web/CsrfFilter.java calls response.sendError(403) when csrfToken.matches(request) returns false., CsrfFilterTest#postWithoutTokenIs403 passes for a POST request without a CSRF token., CsrfFilter rejects state-changing requests carrying a mismatched CSRF token.])
- security-secret-config: title coverage 50% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: title action 'secure' missing: title leads 'modify' (got 'Configured ACME_API_KEY environment lookup in application.yml')
- security-secret-config: title verb class 'remove' missing (got 'Configured ACME_API_KEY environment lookup in application.yml')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Configured ACME_API_KEY environment lookup in application.yml')
- security-secret-config: type 'change' != expected 'bugfix'
- security-secret-config: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, trade-off])
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-xxe: title action 'secure' missing: title leads 'modify' (got 'Fixed XXE vulnerability by disabling DOCTYPE declarations in XmlImportService')
- security-xxe: title verb class 'harden' missing (got 'Fixed XXE vulnerability by disabling DOCTYPE declarations in XmlImportService')
- security-path-traversal: title action 'secure' missing: title leads 'modify' (got 'Fixed path traversal in FileDownloadController.java')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title action 'secure' missing: title leads 'modify' (got 'Fixed RateLimiter empty token bucket response in RateLimiter.java')
- security-rate-limit: title verb class 'add' missing (got 'Fixed RateLimiter empty token bucket response in RateLimiter.java')
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title action 'secure' missing: title leads 'modify' (got 'Fixed session cookie flags with HttpOnly Secure SameSite=Strict in SessionCookieConfig')
- security-cookie-httponly: title verb class 'harden' missing (got 'Fixed session cookie flags with HttpOnly Secure SameSite=Strict in SessionCookieConfig')
- security-cookie-httponly: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- config-hikari-pool: title coverage 75% (expected keywords [change, hikari, pool, size])
- config-hikari-pool: title verb class 'change' missing (got 'Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml')
- config-hikari-pool: concepts recall 50% (expected [what-changed, trade-off], got [trade-off, pattern])
- config-flyway-baseline: title coverage 75% (expected keywords [enable, flyway, baseline, migrate])
- config-flyway-baseline: title verb class 'enable' missing (got 'Set Flyway baselineOnMigrate and baselineVersion in pom.xml')
- config-log-level: title subject [log, level] missing (got 'Changed root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: facts recall 50% (expected [INFO, JSON appender], got [src/main/resources/logback-spring.xml sets the root logger level to INFO., src/main/resources/logback-spring.xml routes the root logger through the appender named JSON., LogbackConfigTest parses src/main/resources/logback-spring.xml.])
- config-docker-healthcheck: title action 'modify' missing: title leads 'create' (got 'Added container HEALTHCHECK for /actuator/health in Dockerfile')
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-maven-plugin: title action 'modify' missing: title leads 'create' (got 'Added jacoco-maven-plugin 0.8.12 to pom.xml')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Documented local setup with JDK 21 and PostgreSQL in README.md')
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title verb class 'add' missing (got 'Documented queue recovery procedure in docs/runbook.md')
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title verb class 'change' missing (got 'Documented 2.4.0 offline replay evaluation and V20 migration fix in CHANGELOG.md')
- docs-architecture-adr: concepts recall 50% (expected [why-it-exists, trade-off], got [why-it-exists, pattern])
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [what-changed, pattern])
- docs-security-policy: title coverage 50% (expected keywords [add, security, policy, disclosure])
- docs-security-policy: title action 'document' missing: title leads 'modify' (got 'Changed vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: title verb class 'add' missing (got 'Changed vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: title subject [security, policy] missing (got 'Changed vulnerability disclosure channel in SECURITY.md')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 33% (expected keywords [update, contributing, verify-local])
- docs-contributing: title verb class 'change' missing (got 'Documented pull request verification and test layers in CONTRIBUTING.md')
- deploy-k8s-probe: title lost identifiers [deployment.yaml, /api/health] (got 'Added readinessProbe to deployment.yaml')
- deploy-k8s-probe: title action 'modify' missing: title leads 'create' (got 'Added readinessProbe to deployment.yaml')
- deploy-github-actions: title coverage 75% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: title verb class 'add' missing (got 'Changed CI Java matrix to JDK 21 and 25 in .github/workflows/ci.yml')
- deploy-terraform-iam: title coverage 50% (expected keywords [restrict, iam, terraform, s3])
- deploy-terraform-iam: title subject [iam, policy] missing (got 'Restricted deploy role to AmazonS3ReadOnlyAccess in infra/main.tf')
- deploy-terraform-iam: concepts recall 50% (expected [what-changed, trade-off], got [trade-off, pattern])
- deploy-helm-values: title coverage 75% (expected keywords [change, helm, replicas, memory])
- deploy-helm-values: title subject [helm, values] missing (got 'Changed replicaCount to 3 and memory limit to 2Gi in infra/values.yaml')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-rollback-script: concepts recall 50% (expected [what-changed, pattern], got [pattern, trade-off])
- deploy-canary: title coverage 50% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: title verb class 'enable' missing (got 'Set canary rollout weight and error threshold in infra/canary.yaml')

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset off-by-one in pagination</title>   <subtitle>OrderQuery.pageOffset now starts the first page at zero.</subtitle>   <facts>     <fact>OrderQuery.pageOffset now returns page * size without subtracting 1.</fact>     <fact>OrderQueryTest#firstPageStartsAtZero passes after the pagination fix.</fact>   </facts>   <narrative>OrderQu... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed concurrent null placeholder writes in UserCache.get</title>   <subtitle>UserCache.get now uses computeIfAbsent so concurrent callers share the loader result.</subtitle>   <facts>     <fact>UserCache.get in src/main/java/com/acme/cache/UserCache.java now calls cache.computeIfAbsent(id, loader::load).</fact>     <fact>CacheConcurrencyTest passes wit... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai date boundary</title>   <subtitle>Regression test pins the Asia/Shanghai business date across the UTC date boundary.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone uses ZoneId.of("Asia/Shanghai") and the timestamp 2026-11-01T23:30Z.</fact>     <fact>DailyRepor... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed missing order lookup to throw PaymentNotFoundException in PaymentService</title>   <subtitle>PaymentService now reports an unknown orderId explicitly, and PaymentServiceTest#missingOrderThrows passes.</subtitle>   <facts>     <fact>PaymentService uses repository.findByOrderId(orderId).orElseThrow(() -&gt; new PaymentNotFoundException(orderId)).</f... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration equality at exact boundary</title>   <subtitle>JwtVerifier now rejects tokens expiring exactly at the verification instant.</subtitle>   <facts>     <fact>JwtVerifier evaluates claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now) before accepting a token.</fact>     <fact>JwtVerifierTest#expiredAtExact... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed multiline CSV row count with CRLF normalization in CsvExporter</title>   <subtitle>CsvExporter now normalizes embedded line endings to CRLF before CSV quoting.</subtitle>   <facts>     <fact>CsvExporter.java writes each line using line.replaceAll("\\r?\\n", "\r\n") before quoting.</fact>     <fact>CsvExporterTest#multilineFieldKeepsRowCount passes... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment atomic updates in CounterService.java</title>   <subtitle>AtomicLong.incrementAndGet prevents lost increments during concurrent counter updates.</subtitle>   <facts>     <fact>CounterService.increment now returns counter.incrementAndGet() instead of counter.set(counter.get() + 1).</fact>     <fact>CounterServiceConcurrency... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator long input regex performance in UrlValidator.java</title>   <subtitle>Replaced a nested-quantifier expression with a linear URL pattern and verified long-input completion timing.</subtitle>   <facts>     <fact>UrlValidator.java defines URL as Pattern.compile("^https?://[^\\s]+$").</fact>     <fact>UrlValidatorTest#longInputFinishesUnd... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed UTF-8 decoding in FileReaderFactory</title>   <subtitle>FileReaderFactory now opens files as UTF-8, preventing accent corruption on GBK-default JVMs.</subtitle>   <facts>     <fact>FileReaderFactory constructs InputStreamReader with StandardCharsets.UTF_8 instead of the platform default charset.</fact>     <fact>FileReaderFactoryTest#utf8FileKeeps... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored AuthController JWT issuance with TokenService.issue</title>   <subtitle>AuthController delegates token creation to TokenService while preserving green AuthControllerTest coverage.</subtitle>   <facts>     <fact>AuthController now returns tokenService.issue(authentication) instead of constructing JWTs inline.</fact>     <fact>AuthControllerT... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>refactor</type>   <title>Refactored ConfigLoader properties map with typed accessors</title>   <subtitle>ConfigLoader now returns an unmodifiable properties map while preserving tested configuration values.</subtitle>   <facts>     <fact>src/main/java/com/acme/config/ConfigLoader.java uses properties.entrySet().stream().collect(toUnmodifiableMap(...)).</fact>     <fact>Config... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Fixed UserRepository tenant filtering with parameterized JPQL query</title>   <subtitle>UserRepository now filters users by tenant through a JPQL parameter instead of concatenated SQL.</subtitle>   <facts>     <fact>UserRepository uses @Query("select u from User u where u.tenantId = :tenantId").</fact>     <fact>UserRepositoryTest returns the same tenan... [truncated]
```

### refactor-feature-flag

```text
<observation>   <type>refactor</type>   <title>Refactored FeatureFlagService evaluation to Flags.isEnabled delegation</title>   <subtitle>FeatureFlagService now delegates flag evaluation to Flags.isEnabled while preserving the existing on/off test matrix.</subtitle>   <facts>     <fact>src/main/java/com/acme/flags/FeatureFlagService.java returns flags.isEnabled(flag, context).</fact>     <fact>Fea... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for UTC-vs-business-date bug</title>   <subtitle>Regression test expects an Asia/Shanghai report date one day after the UTC date.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone checks report.dateAt(2026-11-01T23:30:00Z, Asia/Shanghai) equals LocalDate.of(2026, 11, 2).</fact>   ... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 for locked-account status</title>   <subtitle>A regression test reproduces the wrong HTTP 401 response for locked accounts, which currently returns 423.</subtitle>   <facts>     <fact>src/test/java/com/acme/auth/LoginControllerTest.java adds LoginControllerTest#lockedAccountReturns423.</fact>     <fact>... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized cases for exponential backoff</title>   <subtitle>Parameterized tests verify retry delays of 100, 200, and 400 milliseconds for attempts 1-3.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles uses @ParameterizedTest and @CsvSource with cases 1,100; 2,200; and 3,400.</fact>     <fact>RetryPolicy... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull for concurrent cache reads</title>   <subtitle>Regression test reproduces UserCache intermittent null results using 1000 parallel readers.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull runs 1000 parallel cache.get("k") assertions and requires every result to be... [truncated]
```

### test-contract-openapi

```text
<observation>   <type>feature</type>   <title>Added OpenApiContractTest#generatedSpecMatchesCommittedFile schema drift check</title>   <subtitle>Contract test compares generated springdoc output with docs/openapi.yaml and fails on schema drift.</subtitle>   <facts>     <fact>OpenApiContractTest#generatedSpecMatchesCommittedFile compares springdoc output against docs/openapi.yaml.</fact>     <fact>... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Added deterministic Clock.fixed scheduler test in SchedulerTest.java</title>   <subtitle>SchedulerTest replaces Thread.sleep with a fixed UTC clock, eliminating timing-related flakiness.</subtitle>   <facts>     <fact>src/test/java/com/acme/scheduling/SchedulerTest.java sets scheduler.clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffse... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed credential leakage in LogSanitizer</title>   <subtitle>LogSanitizer now masks password, token, and secret values before logs are written.</subtitle>   <facts>     <fact>LogSanitizer replaces password, token, and secret values matching the credential key pattern with masked values.</fact>     <fact>LogSanitizerTest#redactsCredentials passes for pas... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed UserDao.findByEmail SQL injection with parameter placeholder</title>   <subtitle>UserDao.findByEmail now queries with a bound parameter and safely handles quoted email input.</subtitle>   <facts>     <fact>UserDao.findByEmail uses the SQL placeholder `email = ?` instead of concatenating the email value.</fact>     <fact>UserDao.findByEmail passes ... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Fixed AdminController authorization to require ADMIN on every endpoint</title>   <subtitle>AdminController now denies non-admin users with HTTP 403 before endpoint execution.</subtitle>   <facts>     <fact>AdminController applies @PreAuthorize("hasRole('ADMIN')") at class scope.</fact>     <fact>AdminAuthorizationTest#userRoleGets403 verifies that a use... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsrfFilter mismatched-token rejection for state-changing requests</title>   <subtitle>CsrfFilter now returns HTTP 403 when a request token does not match the CSRF token.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/CsrfFilter.java calls response.sendError(403) when csrfToken.matches(request) returns false.</fact>     <fact>CsrfFilterT... [truncated]
```

### security-secret-config

```text
<observation>   <type>change</type>   <title>Configured ACME_API_KEY environment lookup in application.yml</title>   <subtitle>application.yml now resolves the API key from the environment instead of storing a literal secret.</subtitle>   <facts>     <fact>src/main/resources/application.yml replaces literal api-key: sk-live-123 with api-key: ${ACME_API_KEY}.</fact>     <fact>SecretScanTest passes ... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Fixed XXE vulnerability by disabling DOCTYPE declarations in XmlImportService</title>   <subtitle>XmlImportService now rejects external XML entities, with XxeTest#externalEntityIsRejected passing.</subtitle>   <facts>     <fact>XmlImportService sets http://apache.org/xml/features/disallow-doctype-decl to true.</fact>     <fact>XxeTest#externalEntityIsRe... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Fixed path traversal in FileDownloadController.java</title>   <subtitle>FileDownloadController now rejects normalized names escaping the base directory with InvalidPathException.</subtitle>   <facts>     <fact>FileDownloadController resolves names with base.resolve(name).normalize() and verifies resolved.startsWith(base).</fact>     <fact>FileDownloadCo... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter empty token bucket response in RateLimiter.java</title>   <subtitle>RateLimiter now rejects exhausted token buckets with HTTP 429 and passes the eleventh-request regression test.</subtitle>   <facts>     <fact>RateLimiter throws TooManyRequestsException when bucket.tryConsume(1) returns false.</fact>     <fact>RateLimiterTest#eleventhR... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Fixed session cookie flags with HttpOnly Secure SameSite=Strict in SessionCookieConfig</title>   <subtitle>Session cookies now carry restrictive browser flags, and SessionCookieTest#flagsArePresent verifies the configuration.</subtitle>   <facts>     <fact>SessionCookieConfig sets cookie.setHttpOnly(true).</fact>     <fact>SessionCookieConfig sets cooki... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml</title>   <subtitle>Hikari connection pool sizing is now explicitly configured and verified by configuration binding.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20.</fact>     <fact>src/main/resources/appl... [truncated]
```

### config-flyway-baseline

```text
<observation>   <type>change</type>   <title>Set Flyway baselineOnMigrate and baselineVersion in pom.xml</title>   <subtitle>Flyway baselines version 1 during migration for non-empty existing databases.</subtitle>   <facts>     <fact>pom.xml sets flyway.baselineOnMigrate to true.</fact>     <fact>pom.xml sets flyway.baselineVersion to 1.</fact>     <fact>Maven build resolves the flyway.baselineOnM... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Changed root logger to INFO with JSON appender in logback-spring.xml</title>   <subtitle>The application root logger now emits INFO-level events through the JSON appender.</subtitle>   <facts>     <fact>src/main/resources/logback-spring.xml sets the root logger level to INFO.</fact>     <fact>src/main/resources/logback-spring.xml routes the root logger ... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added container HEALTHCHECK for /actuator/health in Dockerfile</title>   <subtitle>Docker images now report health every 30 seconds using the application health endpoint.</subtitle>   <facts>     <fact>Dockerfile defines HEALTHCHECK --interval=30s for http://localhost:8080/actuator/health.</fact>     <fact>The HEALTHCHECK command runs curl -f and exits ... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 to pom.xml</title>   <subtitle>Maven verification now generates JaCoCo coverage reports during the build.</subtitle>   <facts>     <fact>pom.xml adds org.jacoco:jacoco-maven-plugin version 0.8.12.</fact>     <fact>mvn verify writes the JaCoCo XML report to target/site/jacoco/jacoco.xml.</fact>   </facts>   <narrative>The... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Documented local setup with JDK 21 and PostgreSQL in README.md</title>   <subtitle>README.md now provides a three-step local development startup procedure.</subtitle>   <facts>     <fact>README.md requires JDK 21 for local setup.</fact>     <fact>README.md starts PostgreSQL with docker compose up -d postgres.</fact>     <fact>README.md starts the Spring... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Documented queue recovery procedure in docs/runbook.md</title>   <subtitle>The runbook specifies inspecting pending_messages, restarting the worker, and verifying /api/health.</subtitle>   <facts>     <fact>docs/runbook.md defines a queue recovery procedure beginning with checking pending_messages.</fact>     <fact>docs/runbook.md directs operators to r... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Documented 2.4.0 offline replay evaluation and V20 migration fix in CHANGELOG.md</title>   <subtitle>CHANGELOG.md now records the 2.4.0 release features and migration correction.</subtitle>   <facts>     <fact>CHANGELOG.md adds a 2.4.0 entry dated 2026-11-03.</fact>     <fact>CHANGELOG.md lists offline replay evaluation as an addition in version 2.4.0.<... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>decision</type>   <title>Recorded ADR extraction validation decision in 0007-extraction-validation.md</title>   <subtitle>ADR 0007 requires extracted facts to pass tool-output validation before persistence and defines rejection handling.</subtitle>   <facts>     <fact>docs/adr/0007-extraction-validation.md records the decision to validate extracted facts against tool output b... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 mem_sessions backup and sentinel prerequisite in docs/MIGRATION.md</title>   <subtitle>Migration guidance requires backing up mem_sessions and enabling manual sentinel review.</subtitle>   <facts>     <fact>docs/MIGRATION.md specifies backing up mem_sessions before the V20 migration.</fact>     <fact>docs/MIGRATION.md requires app.manual_... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Changed vulnerability disclosure channel in SECURITY.md</title>   <subtitle>SECURITY.md directs vulnerability reports to security@acme.com and prohibits public issues.</subtitle>   <facts>     <fact>SECURITY.md specifies security@acme.com as the vulnerability reporting channel.</fact>     <fact>SECURITY.md forbids opening public issues for vulnerabiliti... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Documented pull request verification and test layers in CONTRIBUTING.md</title>   <subtitle>CONTRIBUTING.md now requires local verification before opening pull requests.</subtitle>   <facts>     <fact>CONTRIBUTING.md requires running ./scripts/verify-local.sh before opening a pull request.</fact>     <fact>CONTRIBUTING.md names four test layers for the ... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added readinessProbe to deployment.yaml</title>   <subtitle>Deployment readiness now checks /api/health on port 37777 after a 15-second startup delay.</subtitle>   <facts>     <fact>deploy/deployment.yaml defines readinessProbe.httpGet.path as /api/health and readinessProbe.httpGet.port as 37777.</fact>     <fact>deploy/deployment.yaml sets readinessPro... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Changed CI Java matrix to JDK 21 and 25 in .github/workflows/ci.yml</title>   <subtitle>Backend CI now validates builds against Java 21 and Java 25.</subtitle>   <facts>     <fact>.github/workflows/ci.yml sets strategy.matrix.java to [21, 25].</fact>     <fact>.github/workflows/ci.yml runs the backend on JDK 21 and JDK 25.</fact>     <fact>Workflow synt... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted deploy role to AmazonS3ReadOnlyAccess in infra/main.tf</title>   <subtitle>The deploy IAM role now grants read-only S3 access and passes terraform validate.</subtitle>   <facts>     <fact>infra/main.tf defines aws_iam_role_policy.deploy with policy_arn = "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess".</fact>     <fact>The deploy role no lon... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Changed replicaCount to 3 and memory limit to 2Gi in infra/values.yaml</title>   <subtitle>Helm values now render three pods with each pod capped at 2Gi memory.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount to 3.</fact>     <fact>infra/values.yaml sets resources.limits.memory to 2Gi.</fact>     <fact>helm template renders 3 pods wit... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added deployment rollback script scripts/rollback.sh</title>   <subtitle>Rollback script undoes the claude-mem deployment and waits for rollout completion.</subtitle>   <facts>     <fact>scripts/rollback.sh runs `kubectl rollout undo deployment/claude-mem`.</fact>     <fact>scripts/rollback.sh runs `kubectl rollout status deployment/claude-mem` after u... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Set canary rollout weight and error threshold in infra/canary.yaml</title>   <subtitle>Canary releases are enabled at 10 percent with automatic rollback above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true.</fact>     <fact>infra/canary.yaml sets canary.weight to 10 for a 10 percent canary rollout.</... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared verb class, (2) the declared subject tokens to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. The legacy keyword-coverage metric is reported alongside for before/after comparison.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
