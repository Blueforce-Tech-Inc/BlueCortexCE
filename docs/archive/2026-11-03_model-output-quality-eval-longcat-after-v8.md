# Model output quality evaluation

- Date: 2026-11-03
- Phase: after
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 87 unique; runs scored: 3; key repeat: 3; real calls: 113 (cap none); duration: 1343435 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 99% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 20/0/0/67 |
| format errors | 0 |
| validation rejections | 1 |
| call failures | 0 |
| title exact-hit rate (legacy) | 56% |
| title normalized hit rate (legacy) | 65% |
| title action-class hit rate (A6) | 91% (66 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 77% |
| title subject hit rate | 86% |
| title contract hit rate (action + subject + all identifiers) | 79% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 68% |
| title identifier hit rate | 98% |
| type accuracy | 94% |
| facts keyword precision (legacy) / recall | 78% / 92% |
| facts informative precision | 78% |
| facts trace-support rate | 100% |
| concepts precision / recall | 86% / 83% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 11105 / 22348 / 37796 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 78% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 91% | PASS |
| A6b | title subject | >= 90% | 86% | **FAIL** |
| A6c | title identifiers | = 100% | 98% | **FAIL** |
| A6 | all three title dimensions together | all pass | 79% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 87 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 87 | 100% | 65% | 79% | 78% | 78% | 92% | 0 |
| 1 | 13 | 100% | 60% | 80% | 80% | 80% | 97% | 0 |
| 2 | 13 | 100% | 80% | 90% | 85% | 80% | 100% | 0 |

- average: skip 100%, title 68%, title contract 83%, facts P/R 81%/96%, facts actionable 79%
- worst run: skip 100%, title 60%, title contract 79%, facts P/R 78%/92%, facts actionable 78%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 11/12 | 11/12 | 9/12 |
| refactor | 10 | 10/10 | 9/10 | 8/10 |
| test-add | 9 | 9/9 | 9/9 | 9/9 |
| security-fix | 9 | 9/9 | 8/9 | 2/9 |
| config-change | 12 | 12/12 | 12/12 | 9/12 |
| docs-update | 8 | 8/8 | 7/8 | 3/8 |
| deploy-change | 7 | 7/7 | 6/7 | 3/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 21812 |
| bug-null-cache | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/50% | 16122 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 11642 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 11994 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 9565 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 11105 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 100% | 50%/50% | 32032 |
| bug-race-counter | bug-fix | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 18763 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 9848 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 10036 |
| bug-regex-redos | bug-fix | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 50% | 100%/100% | 17346 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 10453 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 67% | 100%/100% | 13923 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 100% | 100%/100% | 8022 |
| refactor-config-loader | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 50% | 100%/100% | 13459 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 13909 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 8146 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 19958 |
| refactor-sql-repository | refactor | observation | observation | pass | 67% | MISS | miss | MISS | 67%/100% | 100% | 0%/0% | 18394 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 100%/100% | 17239 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 33% | 100%/100% | 14925 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 16086 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 10049 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 100% | 100%/100% | 9742 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 22312 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 33% | 100%/100% | 13326 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 75% | 100%/100% | 16736 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/50% | 11960 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 13995 |
| test-flaky-clock | test-add | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 100% | 100%/100% | 14470 |
| security-redact-log | security-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 13304 |
| security-sql-injection | security-fix | observation | observation | pass | 75% | hit | miss | match | 50%/50% | 100% | 100%/100% | 15066 |
| security-authz-admin | security-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 23188 |
| security-csrf | security-fix | observation | observation | pass | 67% | hit | miss | match | 67%/50% | 67% | 100%/100% | 10326 |
| security-secret-config | security-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 20377 |
| security-xxe | security-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 9580 |
| security-path-traversal | security-fix | observation | observation | pass | 75% | hit | miss | match | 50%/100% | 50% | 100%/100% | 37796 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS | miss | match | 67%/50% | 100% | 50%/50% | 34272 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS | miss | MISS | 50%/100% | 100% | 0%/0% | 13429 |
| config-hikari-pool | config-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 13450 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 22348 |
| config-log-level | config-change | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 0% | 100%/100% | 14359 |
| config-cors | config-change | observation | observation | pass | 50% | hit | miss | match | 67%/100% | 33% | 100%/100% | 15623 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 6807 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 9165 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 100% | 100%/100% | 15570 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 50% | 50%/50% | 9682 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 7475 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 100%/100% | 10350 |
| docs-runbook | docs-update | observation | observation | pass | 75% | hit | miss | match | 0%/0% | 0% | 100%/100% | 7737 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 6464 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 50%/50% | 10161 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 6224 |
| docs-security-policy | docs-update | observation | observation | pass | 100% | MISS | hit | MISS | 100%/50% | 100% | 50%/50% | 11169 |
| docs-contributing | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 6952 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 67% | 50%/50% | 17916 |
| deploy-github-actions | deploy-change | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 100%/100% | 12632 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 100%/100% | 13820 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 25% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 15911 |
| deploy-helm-values | deploy-change | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 100% | 100%/100% | 7581 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 33% | 100%/100% | 16339 |
| deploy-canary | deploy-change | observation | observation | pass | 75% | hit | miss | match | 50%/100% | 100% | 100%/100% | 14655 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2290 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2581 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5359 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2953 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2026 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3022 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5091 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2482 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2113 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4136 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4343 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3139 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4735 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2820 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4584 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2752 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3176 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2462 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3162 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5801 |
| remove-dependency | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 8040 |
| remove-dead-code | refactor | observation | observation | pass | 50% | MISS | miss | match | 50%/100% | 0% | 100%/50% | 17475 |
| remove-stale-config | config-change | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 100% | 100%/100% | 8801 |
| remove-deprecated-endpoint | config-change | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/50% | 12476 |
| remove-feature-flag | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/50% | 12633 |

## Problems

- bug-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: validation rejected the observation: title too long: 101 chars > 100
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed FileReaderFactory charset by pinning UTF-8 instead of platform default')
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [AuthController previously built JWTs inline and now calls TokenService.issue(authentication), The delegation target method is TokenService.issue(authentication)])
- refactor-config-loader: facts recall 50% (expected [unmodifiable map, typed accessors], got [ConfigLoader exposes typed accessors for retrieving configuration properties., ConfigLoaderTest passes with the same configuration values after the refactor.])
- refactor-sql-repository: title coverage 67% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title action 'modify' missing: title leads 'secure' (got 'Hardened UserRepository tenant filter with JPQL @Query')
- refactor-sql-repository: title verb class 'refactor' missing (got 'Hardened UserRepository tenant filter with JPQL @Query')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: concepts recall 0% (expected [what-changed, pattern], got [problem-solution, gotcha])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles uses @ParameterizedTest with @CsvSource({"1,100","2,200","3,400"}), The test asserts policy.delay(attempt) returns 100 ms, 200 ms, and 400 ms for attempts 1, 2, and 3 respectively, All 3 parameterized test cases pass])
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable is a @ParameterizedTest driven by @ValueSource(strings = {"a,b","a\"b","a\nb"}) in src/test/java/com/acme/export/CsvExporterTest.java, The 3 parameterized cases cover comma, double-quote and newline characters and all 3 pass, The test lives in package com.acme.export under src/test/java/com/acme/export/CsvExporterTest.java])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: title verb class 'redact' missing (got 'Hardened LogSanitizer redaction of password token secret values')
- security-redact-log: title subject [credentials] missing (got 'Hardened LogSanitizer redaction of password token secret values')
- security-redact-log: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-sql-injection: title coverage 75% (expected keywords [fix, sql, injection, userdao])
- security-sql-injection: title verb class 'fix' missing (got 'Hardened UserDao.findByEmail against SQL injection in UserDao.java')
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail now uses jdbcTemplate.query("select * from users where email = ?", mapper, email) instead of string concatenation, SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the change])
- security-authz-admin: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-csrf: title coverage 67% (expected keywords [fix, csrf, csrffilter])
- security-csrf: title verb class 'fix' missing (got 'Hardened CsrfFilter token validation in CsrfFilter.java')
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter calls csrfToken.matches(request) and invokes response.sendError(403) when the token does not match, returning early without processing the request., CsrfFilterTest#postWithoutTokenIs403 passes, confirming the 403 rejection behavior for state-changing requests without a valid token., The new validation lives in src/main/java/com/acme/web/CsrfFilter.java under the /repo/web working directory.])
- security-secret-config: title lost identifiers [application.yml, ACME_API_KEY] (got 'Removed literal API key sk-live-123 in application.yml')
- security-secret-config: title action 'secure' missing: title leads 'remove' (got 'Removed literal API key sk-live-123 in application.yml')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Removed literal API key sk-live-123 in application.yml')
- security-path-traversal: title coverage 75% (expected keywords [fix, path, traversal, filedownloadcontroller])
- security-path-traversal: title verb class 'fix' missing (got 'Hardened FileDownloadController path traversal check in FileDownloadController.java')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title action 'secure' missing: title leads 'modify' (got 'Fixed missing 429 rejection when RateLimiter token bucket empties in RateLimiter.java')
- security-rate-limit: title verb class 'add' missing (got 'Fixed missing 429 rejection when RateLimiter token bucket empties in RateLimiter.java')
- security-rate-limit: facts recall 50% (expected [429, token bucket], got [The guard `if (!bucket.tryConsume(1)) { throw new TooManyRequestsException(); }` was inserted into src/main/java/com/acme/web/RateLimiter.java., When `bucket.tryConsume(1)` returns false, RateLimiter throws `TooManyRequestsException`, producing HTTP 429 instead of allowing the request through., `RateLimiterTest#eleventhRequestIs429` passes after the edit, confirming the eleventh request receives 429.])
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig.java')
- security-cookie-httponly: type 'change' != expected 'bugfix'
- security-cookie-httponly: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, trade-off])
- config-log-level: title coverage 75% (expected keywords [change, logback, root, json])
- config-log-level: title verb class 'change' missing (got 'Set root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: title subject [log, level] missing (got 'Set root logger to INFO with JSON appender in logback-spring.xml')
- config-cors: title coverage 50% (expected keywords [restrict, cors, corsconfig, origin])
- config-cors: title verb class 'restrict' missing (got 'Hardened CORS /api/** to https://app.acme.com with GET and POST in CorsConfig.java')
- config-docker-healthcheck: title action 'modify' missing: title leads 'create' (got 'Added HEALTHCHECK against /actuator/health every 30s in Dockerfile')
- config-maven-plugin: title action 'modify' missing: title leads 'create' (got 'Added jacoco-maven-plugin 0.8.12 to pom.xml')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Documented local setup steps in README.md')
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title verb class 'add' missing (got 'Documented queue recovery procedure in docs/runbook.md')
- docs-runbook: facts recall 0% (expected [pending_messages, /api/health], got [docs/runbook.md documents a three-step queue recovery procedure])
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title verb class 'change' missing (got 'Documented 2.4.0 release entry in CHANGELOG.md')
- docs-architecture-adr: concepts recall 50% (expected [why-it-exists, trade-off], got [why-it-exists, pattern])
- docs-migration-guide: title coverage 75% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [what-changed, pattern])
- docs-security-policy: title action 'document' missing: title leads 'create' (got 'Added security disclosure policy in SECURITY.md')
- docs-security-policy: type 'feature' != expected 'change'
- docs-security-policy: facts recall 50% (expected [security@acme.com, public issues], got [SECURITY.md explicitly forbids opening public issues for vulnerabilities.])
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 67% (expected keywords [update, contributing, verify-local])
- docs-contributing: title verb class 'change' missing (got 'Documented ./scripts/verify-local.sh prerequisite in CONTRIBUTING.md')
- deploy-k8s-probe: title coverage 75% (expected keywords [add, readiness, probe, deployment])
- deploy-k8s-probe: title verb class 'add' missing (got 'Extended deploy/deployment.yaml readinessProbe with /api/health:37777')
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-github-actions: title coverage 50% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: title verb class 'add' missing (got 'Extended CI workflow with java matrix in .github/workflows/ci.yml')
- deploy-terraform-iam: title coverage 25% (expected keywords [restrict, iam, terraform, s3])
- deploy-terraform-iam: title verb class 'restrict' missing (got 'Hardened deploy role S3 permissions to AmazonS3ReadOnlyAccess in infra/main.tf')
- deploy-terraform-iam: title subject [iam, policy] missing (got 'Hardened deploy role S3 permissions to AmazonS3ReadOnlyAccess in infra/main.tf')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 75% (expected keywords [enable, canary, rollback, threshold])
- remove-dead-code: title coverage 50% (expected keywords [remove, dead, code, orderservice])
- remove-dead-code: title subject [dead, code] missing (got 'Removed unused legacySync from OrderService.java')
- remove-dead-code: concepts recall 50% (expected [what-changed, pattern], got [what-changed, what-changed])
- remove-stale-config: title subject [stale, setting] missing (got 'Removed legacy-search.enabled feature flag in application.yml')
- remove-deprecated-endpoint: title coverage 75% (expected keywords [remove, deprecated, endpoint, usercontroller])
- remove-deprecated-endpoint: title subject [deprecated, endpoint] missing (got 'Removed legacy /v1/users endpoint from UserController')
- remove-deprecated-endpoint: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])
- remove-feature-flag: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])

## Raw answers behind problems

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache.get concurrent null placeholder race with computeIfAbsent</title>   <subtitle>Replaced cache.get with cache.computeIfAbsent so concurrent callers cannot both write a null placeholder.</subtitle>   <facts>     <fact>UserCache.get changed from `return cache.get(id);` to `return cache.computeIfAbsent(id, loader::load);` in src/main/java/com... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai off-by-one-day bug</title>   <subtitle>New test pins 2026-11-01T23:30Z to expected Asia/Shanghai business date 2026-11-02.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins 2026-11-01T23:30Z and expects the Asia/Shanghai business date 2026-11-02.</fact>     ... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed PaymentService returning null for missing orderId in PaymentService.java</title>   <subtitle>PaymentService now throws PaymentNotFoundException instead of silently returning null when an orderId is not found.</subtitle>   <facts>     <fact>PaymentService now executes `repository.findByOrderId(orderId).orElseThrow(() -> new PaymentNotFoundException... [truncated]
```

### bug-jwt-expiry

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed JWT expiration boundary check in JwtVerifier</title>   <subtitle>JwtVerifier now rejects tokens whose expiration equals the current instant.</subtitle>   <facts>     <fact>JwtVerifier previously checked only `claims.getExpiration().before(now)`, accepting a token whose expiration equals the current instant.</fact>     <fact>JwtVerifier now ... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsvExporter multiline field line-ending normalisation in CsvExporter.java</title>   <subtitle>CsvExporter now converts embedded LF/CRLF line endings to CRLF before quoting, preserving row counts for multiline fields.</subtitle>   <facts>     <fact>CsvExporter.java writes each line via `writer.write(line.replaceAll("\\r?\\n", "\r\n"))`, normalising... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment race condition using AtomicLong.incrementAndGet in CounterService.java</title>   <subtitle>Replaced non-atomic get/set pair with AtomicLong.incrementAndGet to make counter increments thread-safe.</subtitle>   <facts>     <fact>CounterService.increment now calls AtomicLong.incrementAndGet() instead of counter.set(counter.ge... [truncated]
```

### bug-file-encoding

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed FileReaderFactory charset by pinning UTF-8 instead of platform default</title>   <subtitle>FileReaderFactory now explicitly opens UTF-8 streams, preventing garbled text on GBK-default JVMs.</subtitle>   <facts>     <fact>FileReaderFactory now uses new InputStreamReader(in, StandardCharsets.UTF_8) instead of relying on the platform default c... [truncated]
```

### refactor-auth-controller

```text
```xml <observation>   <type>refactor</type>   <title>Refactored AuthController JWT issuance with TokenService delegation</title>   <subtitle>AuthController no longer builds JWTs inline; it delegates to TokenService.issue instead.</subtitle>   <facts>     <fact>AuthController previously built JWTs inline and now calls TokenService.issue(authentication)</fact>     <fact>AuthControllerTest remains u... [truncated]
```

### refactor-config-loader

```text
```xml <observation>   <type>refactor</type>   <title>Refactored ConfigLoader to return unmodifiable map with typed accessors</title>   <subtitle>ConfigLoader now returns an unmodifiable property map and provides typed accessor methods while preserving existing values.</subtitle>   <facts>     <fact>ConfigLoader builds its returned map via `toUnmodifiableMap(...)`, making the result immutable to c... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Hardened UserRepository tenant filter with JPQL @Query</title>   <subtitle>Replaced string-concatenated SQL with a parameterized JPQL @Query for tenant-scoped user lookup.</subtitle>   <facts>     <fact>UserRepository now declares `@Query("select u from User u where u.tenantId = :tenantId")` for tenant filtering.</fact>     <fact>The JPQL query replaces... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone reproducing Asia/Shanghai date bug</title>   <subtitle>New test pins the UTC-vs-business-date defect for Asia/Shanghai and currently fails as expected.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone calls report.dateAt with Instant.parse("2026-11-01T23:30:00Z") and ZoneId.of("A... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 reproducing locked account 401 status</title>   <subtitle>New test pins the wrong HTTP status returned for locked accounts: expects 423 but observes 401.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true</fact>     <fact>The test perfor... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized cases for exponential backoff</title>   <subtitle>New parameterized test verifying retry delay doubles exponentially across three attempts.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles uses @ParameterizedTest with @CsvSource({"1,100","2,200","3,400"})</fact>     <fact>The test asserts poli... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable parameterized quoting cases</title>   <subtitle>New parameterized test pins CSV quoting stability for commas, quotes and newlines.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable is a @ParameterizedTest driven by @ValueSource(strings = {"a,b","a\"b","a\nb"}) in src/test/java/com/acme/export/CsvExport... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull reproducing UserCache intermittent null</title>   <subtitle>New parallel-read test pins the intermittent null returned by UserCache under concurrent access.</subtitle>   <facts>     <fact>src/test/java/com/acme/cache/CacheConcurrencyTest.java defines test method concurrentReadersNeverSeeNull.</fa... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Hardened LogSanitizer redaction of password token secret values</title>   <subtitle>LogSanitizer now masks password, token and secret credentials before they reach log output.</subtitle>   <facts>     <fact>LogSanitizer.java now applies the regex `(password|token|secret)=\S+` replaced with `$1=***` before logging a value.</fact>     <fact>LogSanitizerTe... [truncated]
```

### security-sql-injection

```text
```xml <observation>   <type>bugfix</type>   <title>Hardened UserDao.findByEmail against SQL injection in UserDao.java</title>   <subtitle>Replaced string concatenation with parameter placeholder in findByEmail query to prevent SQL injection.</subtitle>   <facts>     <fact>UserDao.findByEmail now uses jdbcTemplate.query("select * from users where email = ?", mapper, email) instead of string concat... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Hardened AdminController endpoint authorization to ROLE_ADMIN in AdminController.java</title>   <subtitle>Added class-level @PreAuthorize so non-admin users receive HTTP 403 on every AdminController endpoint.</subtitle>   <facts>     <fact>`@PreAuthorize("hasRole('ADMIN')")` was added to `AdminController`, requiring `ROLE_ADMIN` on every endpoint</fact>... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Hardened CsrfFilter token validation in CsrfFilter.java</title>   <subtitle>CsrfFilter now rejects state-changing requests with a mismatched CSRF token using HTTP 403.</subtitle>   <facts>     <fact>CsrfFilter calls csrfToken.matches(request) and invokes response.sendError(403) when the token does not match, returning early without processing the reques... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Removed literal API key sk-live-123 in application.yml</title>   <subtitle>Replaced a committed secret with environment variable substitution so SecretScanTest passes.</subtitle>   <facts>     <fact>`src/main/resources/application.yml` field `api-key` changed from literal value `sk-live-123` to `${ACME_API_KEY}` environment variable reference</fact>    ... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Hardened FileDownloadController path traversal check in FileDownloadController.java</title>   <subtitle>Path resolution now normalizes the target and rejects any name escaping the base directory with InvalidPathException.</subtitle>   <facts>     <fact>FileDownloadController resolves download names with `base.resolve(name).normalize()` and throws `Inval... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed missing 429 rejection when RateLimiter token bucket empties in RateLimiter.java</title>   <subtitle>RateLimiter now throws TooManyRequestsException when bucket.tryConsume(1) fails, satisfying RateLimiterTest#eleventhRequestIs429.</subtitle>   <facts>     <fact>The guard `if (!bucket.tryConsume(1)) { throw new TooManyRequestsException(); }` was ins... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>change</type>   <title>Hardened session cookie flags in SessionCookieConfig.java</title>   <subtitle>Session cookie now set with HttpOnly, Secure and SameSite=Strict attributes.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/SessionCookieConfig.java now calls cookie.setHttpOnly(true), cookie.setSecure(true) and cookie.setSameSite(Strict).</fact>     <fact>SessionC... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Set root logger to INFO with JSON appender in logback-spring.xml</title>   <subtitle>Root logger now emits JSON-formatted output at INFO level via the JSON appender.</subtitle>   <facts>     <fact>logback-spring.xml root logger level changed to INFO</fact>     <fact>Root logger appender-ref changed to JSON appender</fact>     <fact>LogbackConfigTest par... [truncated]
```

### config-cors

```text
<observation>   <type>change</type>   <title>Hardened CORS /api/** to https://app.acme.com with GET and POST in CorsConfig.java</title>   <subtitle>CORS configuration now restricts /api/** to a single allowed origin and limited HTTP methods.</subtitle>   <facts>     <fact>CorsConfig.java maps /api/** to allowed origin https://app.acme.com only</fact>     <fact>CorsConfig.java allows only GET and P... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added HEALTHCHECK against /actuator/health every 30s in Dockerfile</title>   <subtitle>Dockerfile now includes a container health check probing the Spring Boot actuator health endpoint.</subtitle>   <facts>     <fact>Dockerfile adds `HEALTHCHECK --interval=30s CMD curl -f http://localhost:8080/actuator/health || exit 1`.</fact>     <fact>The health chec... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 to pom.xml</title>   <subtitle>Maven build now runs JaCoCo code coverage during mvn verify and emits XML report.</subtitle>   <facts>     <fact>pom.xml declares jacoco-maven-plugin with groupId org.jacoco and version 0.8.12</fact>     <fact>mvn verify produces coverage report at target/site/jacoco/jacoco.xml</fact>   </f... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Documented local setup steps in README.md</title>   <subtitle>Added a three-step local development setup section covering JDK 21, PostgreSQL via Docker Compose, and Spring Boot startup.</subtitle>   <facts>     <fact>README.md now contains a "## Setup" section with three numbered steps</fact>     <fact>Step 1 instructs to install JDK 21</fact>     <fact... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Documented queue recovery procedure in docs/runbook.md</title>   <subtitle>Runbook documents three-step queue recovery: inspect pending_messages, restart worker, verify /api/health.</subtitle>   <facts>     <fact>docs/runbook.md documents a three-step queue recovery procedure</fact>     <fact>The procedure starts with inspecting pending_messages</fact> ... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Documented 2.4.0 release entry in CHANGELOG.md</title>   <subtitle>Added 2.4.0 release notes covering offline replay evaluation and V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md adds a new 2.4.0 entry dated 2026-11-03.</fact>     <fact>The 2.4.0 entry lists "Added offline replay evaluation" as a new feature.</fact>     <fact>Th... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>decision</type>   <title>Recorded ADR extraction validation decision in 0007-extraction-validation.md</title>   <subtitle>ADR 0007 records the decision to validate extracted facts against tool output before persistence, with the rejection path described.</subtitle>   <facts>     <fact>New file docs/adr/0007-extraction-validation.md records the decision to validate extracted f... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisite in docs/MIGRATION.md</title>   <subtitle>Added V20 section requiring a mem_sessions backup and app.manual_sentinel_reviewed=true before running the migration.</subtitle>   <facts>     <fact>docs/MIGRATION.md gained a V20 section instructing operators to back up the mem_sessions table before running the migration.</f... [truncated]
```

### docs-security-policy

```text
<observation>   <type>feature</type>   <title>Added security disclosure policy in SECURITY.md</title>   <subtitle>New SECURITY.md defines vulnerability reporting channel security@acme.com and prohibits public issues.</subtitle>   <facts>     <fact>SECURITY.md directs vulnerability reports to the email address security@acme.com.</fact>     <fact>SECURITY.md explicitly forbids opening public issues ... [truncated]
```

### docs-contributing

```text
```xml <observation>   <type>change</type>   <title>Documented ./scripts/verify-local.sh prerequisite in CONTRIBUTING.md</title>   <subtitle>CONTRIBUTING.md now requires running ./scripts/verify-local.sh before opening a pull request and names four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md now states to run ./scripts/verify-local.sh before opening a pull request.</fact>     <fact... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Extended deploy/deployment.yaml readinessProbe with /api/health:37777</title>   <subtitle>Added Kubernetes readiness probe hitting /api/health on port 37777 with a 15-second initial delay.</subtitle>   <facts>     <fact>deploy/deployment.yaml now includes a readinessProbe using httpGet on path /api/health port 37777</fact>     <fact>The readinessProbe s... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Extended CI workflow with java matrix in .github/workflows/ci.yml</title>   <subtitle>CI backend jobs now execute against JDK 21 and 25 via a strategy matrix.</subtitle>   <facts>     <fact>.github/workflows/ci.yml now defines a strategy.matrix.java array containing [21, 25]</fact>     <fact>Backend CI jobs run on both JDK 21 and JDK 25 as separate matr... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Hardened deploy role S3 permissions to AmazonS3ReadOnlyAccess in infra/main.tf</title>   <subtitle>Restricted the deploy IAM role from full S3 access to read-only, validated with terraform validate.</subtitle>   <facts>     <fact>infra/main.tf aws_iam_role_policy "deploy" now attaches policy_arn arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess</fact>     ... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added rollback.sh script for claude-mem deployment rollback</title>   <subtitle>New shell script undoes the claude-mem Kubernetes deployment and waits for rollout status.</subtitle>   <facts>     <fact>scripts/rollback.sh was created with content `set -euo pipefail; kubectl rollout undo deployment/claude-mem; kubectl rollout status deployment/claude-me... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Enabled canary rollout with weight 10 and errorThreshold 0.02 in canary.yaml</title>   <subtitle>Canary deployment now runs at 10 percent traffic with automatic rollback above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true, enabling the canary rollout</fact>     <fact>infra/canary.yaml sets canary.we... [truncated]
```

### remove-dead-code

```text
<observation>   <type>refactor</type>   <title>Removed unused legacySync from OrderService.java</title>   <subtitle>Deleted the unreferenced legacySync(Order) method that delegated to warehouse.push.</subtitle>   <facts>     <fact>OrderService.legacySync(Order) was deleted from src/main/java/com/acme/orders/OrderService.java.</fact>     <fact>The removed method body was a single delegation to ware... [truncated]
```

### remove-stale-config

```text
<observation>   <type>change</type>   <title>Removed legacy-search.enabled feature flag in application.yml</title>   <subtitle>Deleted the legacy-search.enabled=true entry so the search module reads only search-v2.</subtitle>   <facts>     <fact>application.yml no longer defines legacy-search.enabled; its previous value was true.</fact>     <fact>The search module now reads only search-v2 as a res... [truncated]
```

### remove-deprecated-endpoint

```text
<observation>   <type>change</type>   <title>Removed legacy /v1/users endpoint from UserController</title>   <subtitle>Deleted the deprecated listLegacy method and its /v1/users mapping, leaving /v2/users as the sole users listing route.</subtitle>   <facts>     <fact>UserController.listLegacy method was removed from src/main/java/com/acme/users/UserController.java</fact>     <fact>The @GetMapping... [truncated]
```

### remove-feature-flag

```text
<observation>   <type>change</type>   <title>Removed LEGACY_SEARCH constant in FeatureFlags.java</title>   <subtitle>Deleted the legacy-search feature flag constant; callers now reference SEARCH_V2.</subtitle>   <facts>     <fact>FeatureFlags.LEGACY_SEARCH with value "legacy-search" was deleted from src/main/java/com/acme/flags/FeatureFlags.java</fact>     <fact>Callers that referenced LEGACY_SEAR... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared verb class, (2) the declared subject tokens to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. The legacy keyword-coverage metric is reported alongside for before/after comparison.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
