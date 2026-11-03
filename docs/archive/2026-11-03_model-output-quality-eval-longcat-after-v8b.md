# Model output quality evaluation

- Date: 2026-11-03
- Phase: after
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 87 unique; runs scored: 3; key repeat: 3; real calls: 113 (cap none); duration: 1265658 ms; temperature: 0.0
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
| title normalized hit rate (legacy) | 64% |
| title action-class hit rate (A6) | 88% (66 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 77% |
| title subject hit rate | 88% |
| title contract hit rate (action + subject + all identifiers) | 76% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 67% |
| title identifier hit rate | 98% |
| type accuracy | 86% |
| facts keyword precision (legacy) / recall | 74% / 90% |
| facts informative precision | 80% |
| facts trace-support rate | 100% |
| concepts precision / recall | 86% / 83% |
| skip consistency across repeats | 92% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 10917 / 20464 / 29563 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 80% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 88% | PASS |
| A6b | title subject | >= 90% | 88% | **FAIL** |
| A6c | title identifiers | = 100% | 98% | **FAIL** |
| A6 | all three title dimensions together | all pass | 76% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 87 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 87 | 100% | 64% | 76% | 74% | 80% | 90% | 0 |
| 1 | 13 | 100% | 60% | 80% | 83% | 75% | 100% | 0 |
| 2 | 13 | 92% | 50% | 50% | 93% | 80% | 100% | 0 |

- average: skip 97%, title 58%, title contract 69%, facts P/R 84%/97%, facts actionable 78%
- worst run: skip 92%, title 50%, title contract 50%, facts P/R 74%/90%, facts actionable 75%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 12/12 | 9/12 |
| refactor | 10 | 10/10 | 7/10 | 7/10 |
| test-add | 9 | 8/9 | 7/9 | 7/9 |
| security-fix | 9 | 9/9 | 5/9 | 4/9 |
| config-change | 12 | 12/12 | 12/12 | 8/12 |
| docs-update | 8 | 8/8 | 8/8 | 3/8 |
| deploy-change | 7 | 7/7 | 6/7 | 4/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 17107 |
| bug-null-cache | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 5596 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 13764 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 50%/50% | 16675 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 7434 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 16643 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 100% | 50%/50% | 10868 |
| bug-race-counter | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/67% | 100% | 50%/50% | 14654 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 7766 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 6704 |
| bug-regex-redos | bug-fix | observation | observation | pass | 100% | hit | miss | match | 50%/50% | 100% | 100%/100% | 11097 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 11151 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 67% | 100%/100% | 11419 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 33% | 100%/100% | 9144 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | MISS | miss | MISS | 50%/50% | 50% | 50%/50% | 29563 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 10917 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 9951 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 100%/100% | 10922 |
| refactor-sql-repository | refactor | observation | observation | pass | 67% | hit | miss | MISS | 50%/50% | 100% | 50%/50% | 13939 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 50% | 100%/100% | 8223 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 100% | 100%/100% | 9807 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 28312 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 10257 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 10801 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 12816 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 25%/50% | 25% | 100%/100% | 11887 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 20464 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | MISS | hit | match | 75%/100% | 75% | 50%/50% | 19404 |
| test-contract-openapi | test-add | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 7827 |
| test-flaky-clock | test-add | observation | observation | pass | 100% | hit | miss | MISS | 100%/67% | 100% | 100%/100% | 14267 |
| security-redact-log | security-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 15161 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | MISS | hit | match | 50%/50% | 100% | 100%/100% | 13897 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 18660 |
| security-csrf | security-fix | observation | observation | pass | 67% | hit | miss | MISS | 33%/100% | 100% | 50%/50% | 20831 |
| security-secret-config | security-fix | observation | observation | pass | 50% | MISS | miss | match | 100%/50% | 100% | 100%/100% | 8272 |
| security-xxe | security-fix | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 100% | 50%/50% | 16463 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | MISS | hit | match | 67%/100% | 100% | 100%/100% | 5595 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS | miss | MISS | 50%/50% | 100% | 50%/50% | 15199 |
| security-cookie-httponly | security-fix | observation | observation | pass | 100% | hit | hit | MISS | 50%/100% | 100% | 50%/50% | 23747 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 67% | 100%/100% | 7660 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 13285 |
| config-log-level | config-change | observation | observation | pass | 100% | MISS | hit | match | 67%/100% | 33% | 100%/100% | 15693 |
| config-cors | config-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 16838 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 9575 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 6300 |
| config-docker-healthcheck | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 13358 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 50% | 50%/50% | 20057 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 7161 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 100%/100% | 6154 |
| docs-runbook | docs-update | observation | observation | pass | 75% | hit | miss | match | 0%/0% | 0% | 100%/100% | 5388 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 7735 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 100% | 100%/100% | 14514 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 11839 |
| docs-security-policy | docs-update | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 50% | 50%/50% | 6435 |
| docs-contributing | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 50% | 100%/100% | 16591 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | MISS | hit | match | 67%/100% | 67% | 50%/50% | 6103 |
| deploy-github-actions | deploy-change | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 50%/50% | 15516 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 50%/50% | 11774 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 17398 |
| deploy-helm-values | deploy-change | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 100% | 100%/100% | 13671 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | hit | MISS | 50%/100% | 25% | 100%/100% | 15065 |
| deploy-canary | deploy-change | observation | observation | pass | 75% | hit | miss | match | 33%/100% | 100% | 100%/100% | 11807 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 7239 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3992 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3831 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2757 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2143 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2436 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2991 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3750 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3111 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3703 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2976 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4374 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3782 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2956 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3252 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3444 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2184 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2507 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2173 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2120 |
| remove-dependency | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 11257 |
| remove-dead-code | refactor | observation | observation | pass | 50% | MISS | miss | MISS | 50%/100% | 0% | 100%/50% | 11415 |
| remove-stale-config | config-change | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/50% | 14808 |
| remove-deprecated-endpoint | config-change | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/50% | 12680 |
| remove-feature-flag | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/50% | 13258 |

## Problems

- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-race-counter: title coverage 75% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: facts recall 67% (expected [incrementAndGet, AtomicLong, 1000], got [CounterService.increment now returns counter.incrementAndGet() instead of calling counter.set(counter.get() + 1) followed by counter.get(), CounterServiceConcurrencyTest passes with 1000 threads producing exactly 1000 increments])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-regex-redos: facts recall 50% (expected [linear pattern, longInputFinishesUnder100ms], got [UrlValidator now uses `Pattern.compile("^https?://[^\\s]+$")` stored in the `URL` constant., UrlValidatorTest#longInputFinishesUnder100ms passes after the replacement.])
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed FileReaderFactory charset to UTF-8 in FileReaderFactory.java')
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [AuthController no longer builds JWTs inline in the login flow., AuthController now calls tokenService.issue(authentication) to produce the token., AuthControllerTest remains unchanged and green after the delegation change.])
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title action 'modify' missing: title leads 'secure' (got 'Hardened ConfigLoader map to unmodifiable in ConfigLoader.java')
- refactor-config-loader: title verb class 'refactor' missing (got 'Hardened ConfigLoader map to unmodifiable in ConfigLoader.java')
- refactor-config-loader: type 'change' != expected 'refactor'
- refactor-config-loader: facts recall 50% (expected [unmodifiable map, typed accessors], got [ConfigLoader exposes typed accessors for configuration values., ConfigLoaderTest passes with the same configuration values after the change.])
- refactor-config-loader: concepts recall 50% (expected [what-changed, pattern], got [what-changed, gotcha])
- refactor-sql-repository: title coverage 67% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title verb class 'refactor' missing (got 'Replaced string-concatenated SQL with JPQL @Query in UserRepository')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [UserRepository now declares @Query("select u from User u where u.tenantId = :tenantId") for the tenant-filtered lookup., UserRepositoryTest returns the same tenant rows after the change, confirming preserved behavior.])
- refactor-sql-repository: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- refactor-event-bus: facts recall 50% (expected [listener map, event type], got [EventBus replaces the if/else dispatch chain with the call `listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event))`., The listener registry in EventBus is keyed by event type, so each event type resolves to its own list of listeners.])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles uses @ParameterizedTest with @CsvSource values "1,100", "2,200", "3,400", All 3 parameterized test cases pass])
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable is a @ParameterizedTest using @ValueSource(strings = {"a,b","a\"b","a\nb"}), The test covers three CSV special characters: comma, double quote, and newline, All 3 parameterized cases pass, confirming stable quoting behavior, Test file located at src/test/java/com/acme/export/CsvExporterTest.java])
- test-repro-null-cache: title lost identifiers [CacheConcurrencyTest, UserCache] (got 'Added CacheConcurrencyTest#concurrentReadersNeverSeeNull concurrent-readers regression test')
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-contract-openapi: validation rejected the observation: title too long: 133 chars > 100
- test-flaky-clock: title verb class 'fix' missing (got 'Replaced Thread.sleep with Clock.fixed in SchedulerTest to eliminate flakiness')
- test-flaky-clock: type 'bugfix' != expected 'feature'
- test-flaky-clock: facts recall 67% (expected [Clock.fixed, Thread.sleep, 20 consecutive], got [SchedulerTest now injects Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC) for deterministic time control., 20 consecutive test runs pass after the change, confirming flakiness is eliminated.])
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: title subject [credentials] missing (got 'Redacted password token secret values in LogSanitizer.java')
- security-sql-injection: title action 'secure' missing: title leads 'modify' (got 'Fixed SQL injection in UserDao.findByEmail via parameterized query')
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail now executes `jdbcTemplate.query("select * from users where email = ?", mapper, email)` with a `?` placeholder instead of concatenating the email argument into the SQL string., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the change, confirming an email containing a quote returns empty results rather than executing injected SQL.])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Restricted AdminController endpoints authorization to ROLE_ADMIN')
- security-csrf: title coverage 67% (expected keywords [fix, csrf, csrffilter])
- security-csrf: title verb class 'fix' missing (got 'Hardened CSRF token validation in CsrfFilter')
- security-csrf: type 'feature' != expected 'bugfix'
- security-csrf: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-secret-config: title coverage 50% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: title verb class 'remove' missing (got 'Redacted api-key sk-live-123 to ACME_API_KEY in application.yml')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Redacted api-key sk-live-123 to ACME_API_KEY in application.yml')
- security-secret-config: facts recall 50% (expected [ACME_API_KEY, SecretScanTest], got [application.yml api-key field now references ${ACME_API_KEY} instead of the literal value sk-live-123.])
- security-xxe: type 'change' != expected 'bugfix'
- security-xxe: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, gotcha])
- security-path-traversal: title action 'secure' missing: title leads 'modify' (got 'Fixed path traversal vulnerability in FileDownloadController')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title action 'secure' missing: title leads 'modify' (got 'Extended RateLimiter to throw TooManyRequestsException on empty bucket')
- security-rate-limit: title verb class 'add' missing (got 'Extended RateLimiter to throw TooManyRequestsException on empty bucket')
- security-rate-limit: type 'feature' != expected 'bugfix'
- security-rate-limit: facts recall 50% (expected [429, token bucket], got [RateLimiter.java now throws TooManyRequestsException when bucket.tryConsume(1) returns false., RateLimiterTest#eleventhRequestIs429 passes, confirming the limiter returns 429 after the bucket is exhausted.])
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [what-changed, pattern])
- security-cookie-httponly: type 'feature' != expected 'bugfix'
- security-cookie-httponly: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, gotcha])
- config-hikari-pool: title coverage 75% (expected keywords [change, hikari, pool, size])
- config-hikari-pool: title verb class 'change' missing (got 'Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml')
- config-log-level: title subject [log, level] missing (got 'Changed root logger level to INFO and appender to JSON in logback-spring.xml')
- config-docker-healthcheck: title coverage 75% (expected keywords [add, docker, healthcheck, actuator])
- config-docker-healthcheck: title verb class 'add' missing (got 'Extended Dockerfile with HEALTHCHECK against /actuator/health')
- config-maven-plugin: title action 'modify' missing: title leads 'create' (got 'Added jacoco-maven-plugin 0.8.12 to pom.xml for code coverage reporting')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Documented local setup steps in README.md')
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title verb class 'add' missing (got 'Documented queue recovery procedure in docs/runbook.md')
- docs-runbook: facts recall 0% (expected [pending_messages, /api/health], got [docs/runbook.md created with queue recovery procedure for stuck message processing])
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title verb class 'change' missing (got 'Documented 2.4.0 release notes in CHANGELOG.md')
- docs-migration-guide: title coverage 75% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [pattern, what-changed])
- docs-security-policy: title action 'document' missing: title leads 'create' (got 'Added vulnerability disclosure policy security@acme.com in SECURITY.md')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 67% (expected keywords [update, contributing, verify-local])
- docs-contributing: title verb class 'change' missing (got 'Documented verify-local.sh pre-PR requirement in CONTRIBUTING.md')
- deploy-k8s-probe: title action 'modify' missing: title leads 'create' (got 'Added readinessProbe on /api/health:37777 in deployment.yaml')
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [pattern, what-changed])
- deploy-github-actions: title coverage 50% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: title verb class 'add' missing (got 'Extended Java version matrix in .github/workflows/ci.yml')
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-docker-multistage: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-terraform-iam: title coverage 75% (expected keywords [restrict, iam, terraform, s3])
- deploy-terraform-iam: title subject [iam, policy] missing (got 'Restricted deploy IAM role to AmazonS3ReadOnlyAccess in infra/main.tf')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 75% (expected keywords [enable, canary, rollback, threshold])
- remove-dead-code: title coverage 50% (expected keywords [remove, dead, code, orderservice])
- remove-dead-code: title subject [dead, code] missing (got 'Removed legacySync from OrderService.java')
- remove-dead-code: type 'change' != expected 'refactor'
- remove-dead-code: concepts recall 50% (expected [what-changed, pattern], got [what-changed, what-changed])
- remove-stale-config: title coverage 75% (expected keywords [remove, legacy, search, application])
- remove-stale-config: title action 'remove' missing: title leads 'modify' (got 'Disabled legacy-search.enabled in application.yml')
- remove-stale-config: title verb class 'remove' missing (got 'Disabled legacy-search.enabled in application.yml')
- remove-stale-config: title subject [stale, setting] missing (got 'Disabled legacy-search.enabled in application.yml')
- remove-stale-config: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])
- remove-deprecated-endpoint: title coverage 75% (expected keywords [remove, deprecated, endpoint, usercontroller])
- remove-deprecated-endpoint: title subject [deprecated, endpoint] missing (got 'Removed legacy /v1/users listLegacy endpoint in UserController.java')
- remove-deprecated-endpoint: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])
- remove-feature-flag: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])

## Raw answers behind problems

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai date boundary</title>   <subtitle>Test pins 2026-11-01T23:30Z and expects Asia/Shanghai business date 2026-11-02, reproducing the off-by-one-day bug.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins the instant 2026-11-01T23:30Z and expects business date 20... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed PaymentService missing orderId to throw PaymentNotFoundException in PaymentService.java</title>   <subtitle>PaymentService no longer returns null for a missing orderId; it now throws PaymentNotFoundException.</subtitle>   <facts>     <fact>PaymentService now returns repository.findByOrderId(orderId).orElseThrow(() -> new PaymentNotFoundException(o... [truncated]
```

### bug-jwt-expiry

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration boundary check in JwtVerifier.java</title>   <subtitle>JwtVerifier now rejects tokens whose expiration equals the current instant instead of accepting them.</subtitle>   <facts>     <fact>JwtVerifier.java previously used `claims.getExpiration().before(now)` which accepted tokens expiring exactly at the current instant... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed multiline CSV field line ending normalization in CsvExporter</title>   <subtitle>CsvExporter now converts embedded line endings to CRLF before quoting so multiline fields no longer break row counts.</subtitle>   <facts>     <fact>CsvExporter.write now applies line.replaceAll("\\r?\\n", "\\r\\n") to normalize embedded line endings to CRLF before qu... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment race condition in CounterService.java</title>   <subtitle>Replaced non-atomic get/set pair with AtomicLong.incrementAndGet to eliminate lost updates under concurrency.</subtitle>   <facts>     <fact>CounterService.increment now returns counter.incrementAndGet() instead of calling counter.set(counter.get() + 1) followed by ... [truncated]
```

### bug-regex-redos

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed UrlValidator nested-quantifier regex catastrophic backtracking</title>   <subtitle>Replaced the nested-quantifier URL pattern with a linear `^https?://[^\s]+$` regex to eliminate ReDoS.</subtitle>   <facts>     <fact>UrlValidator now uses `Pattern.compile("^https?://[^\\s]+$")` stored in the `URL` constant.</fact>     <fact>UrlValidatorTest... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed FileReaderFactory charset to UTF-8 in FileReaderFactory.java</title>   <subtitle>FileReaderFactory now always decodes streams as UTF-8, preventing accent corruption on JVMs with a non-UTF-8 platform default charset.</subtitle>   <facts>     <fact>FileReaderFactory.java now constructs its reader with `new InputStreamReader(in, StandardCharsets.UTF_... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored AuthController JWT issuance with TokenService delegation in AuthController.java</title>   <subtitle>AuthController now delegates token creation to TokenService.issue instead of building JWTs inline.</subtitle>   <facts>     <fact>AuthController no longer builds JWTs inline in the login flow.</fact>     <fact>AuthController now calls tokenSe... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>change</type>   <title>Hardened ConfigLoader map to unmodifiable in ConfigLoader.java</title>   <subtitle>ConfigLoader now returns an unmodifiable map and exposes typed accessors for configuration properties.</subtitle>   <facts>     <fact>ConfigLoader.java now uses toUnmodifiableMap when collecting properties.entrySet() into a map, preventing callers from mutating the return... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Replaced string-concatenated SQL with JPQL @Query in UserRepository</title>   <subtitle>UserRepository tenant lookup now uses a parameterized JPQL @Query instead of string-concatenated SQL.</subtitle>   <facts>     <fact>UserRepository now declares @Query("select u from User u where u.tenantId = :tenantId") for the tenant-filtered lookup.</fact>     <fa... [truncated]
```

### refactor-event-bus

```text
<observation>   <type>refactor</type>   <title>Refactored EventBus dispatch to listener map keyed by event type in EventBus.java</title>   <subtitle>EventBus now routes events through a listener map instead of an if/else dispatch chain.</subtitle>   <facts>     <fact>EventBus replaces the if/else dispatch chain with the call `listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone reproducing UTC-vs-business-date bug</title>   <subtitle>New JUnit test pins Asia/Shanghai business date 2026-11-02 for instant 2026-11-01T23:30Z, currently fails with 2026-11-01.</subtitle>   <facts>     <fact>src/test/java/com/acme/reporting/DailyReportTest.java defines test `reportUsesBusinessTimezone... [truncated]
```

### test-repro-login

```text
```xml <observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 test for locked account 423 status</title>   <subtitle>A new regression test reproduces locked accounts returning 401 instead of the expected 423 status.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true.</fact>     <fact>The tes... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized cases for exponential backoff</title>   <subtitle>Parameterized test verifies retry delay doubles across attempts 1-3 with 100/200/400 ms.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles uses @ParameterizedTest with @CsvSource values "1,100", "2,200", "3,400"</fact>     <fact>The test asserts... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable parameterized cases for CSV quoting</title>   <subtitle>New test verifies CSV exporter correctly quotes commas, double quotes, and newlines in values.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable is a @ParameterizedTest using @ValueSource(strings = {"a,b","a\"b","a\nb"})</fact>     <fact>The test ... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull concurrent-readers regression test</title>   <subtitle>New test reproduces the intermittent null returned by UserCache under concurrent access with 1000 parallel readers.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull uses IntStream.range(0, 1000).parallel() to ... [truncated]
```

### test-contract-openapi

```text
```xml <observation>   <type>feature</type>   <title>Added OpenApiContractTest#generatedSpecMatchesCommittedFile schema drift guard in src/test/java/com/acme/api/OpenApiContractTest.java</title>   <subtitle>New contract test diffs generated springdoc OpenAPI output against committed docs/openapi.yaml and fails on any schema drift.</subtitle>   <facts>     <fact>OpenApiContractTest.java added at sr... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>bugfix</type>   <title>Replaced Thread.sleep with Clock.fixed in SchedulerTest to eliminate flakiness</title>   <subtitle>SchedulerTest now uses a fixed clock instant instead of Thread.sleep, making 20 consecutive runs pass reliably.</subtitle>   <facts>     <fact>SchedulerTest previously used Thread.sleep, causing flaky test behavior due to real-time timing dependencies.</fa... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Redacted password token secret values in LogSanitizer.java</title>   <subtitle>LogSanitizer now masks credential values matching password, token, or secret keys before logging.</subtitle>   <facts>     <fact>LogSanitizer.java applies regex `(password|token|secret)=\S+` and replaces matches with `$1=***` to redact sensitive values.</fact>     <fact>LogSa... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed SQL injection in UserDao.findByEmail via parameterized query</title>   <subtitle>UserDao.findByEmail replaced string concatenation with a jdbcTemplate parameter placeholder to prevent SQL injection.</subtitle>   <facts>     <fact>UserDao.findByEmail now executes `jdbcTemplate.query("select * from users where email = ?", mapper, email)` with a `?` ... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Restricted AdminController endpoints authorization to ROLE_ADMIN</title>   <subtitle>Added class-level @PreAuthorize to enforce ROLE_ADMIN on all AdminController endpoints.</subtitle>   <facts>     <fact>AdminController.java now has @PreAuthorize("hasRole('ADMIN')") applied at the class level</fact>     <fact>AdminAuthorizationTest#userRoleGets403 passe... [truncated]
```

### security-csrf

```text
<observation>   <type>feature</type>   <title>Hardened CSRF token validation in CsrfFilter</title>   <subtitle>CsrfFilter now rejects state-changing requests with invalid or missing CSRF tokens by returning HTTP 403.</subtitle>   <facts>     <fact>CsrfFilter.java validates incoming CSRF tokens via csrfToken.matches(request) before processing</fact>     <fact>Mismatched token triggers response.send... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Redacted api-key sk-live-123 to ACME_API_KEY in application.yml</title>   <subtitle>Removed hardcoded API key from application.yml and replaced it with an environment variable reference.</subtitle>   <facts>     <fact>application.yml api-key field now references ${ACME_API_KEY} instead of the literal value sk-live-123.</fact>     <fact>SecretScanTest pa... [truncated]
```

### security-xxe

```text
```xml <observation>   <type>change</type>   <title>Hardened XmlImportService against XXE by disallowing DOCTYPE declarations</title>   <subtitle>XmlImportService now disables DOCTYPE declarations, blocking XML External Entity injection.</subtitle>   <facts>     <fact>XmlImportService.java sets the SAXParserFactory feature "http://apache.org/xml/features/disallow-doctype-decl" to true.</fact>     ... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Fixed path traversal vulnerability in FileDownloadController</title>   <subtitle>FileDownloadController now rejects names that escape the base directory via dot-dot sequences.</subtitle>   <facts>     <fact>FileDownloadController resolves user-supplied name against base and calls normalize() before checking resolved.startsWith(base).</fact>     <fact>Na... [truncated]
```

### security-rate-limit

```text
<observation>   <type>feature</type>   <title>Extended RateLimiter to throw TooManyRequestsException on empty bucket</title>   <subtitle>RateLimiter now rejects requests with 429 when the token bucket cannot consume a token.</subtitle>   <facts>     <fact>RateLimiter.java now throws TooManyRequestsException when bucket.tryConsume(1) returns false.</fact>     <fact>TooManyRequestsException maps to ... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>feature</type>   <title>Hardened session cookie HttpOnly, Secure, SameSite=Strict in SessionCookieConfig</title>   <subtitle>Session cookies now carry HttpOnly, Secure, and SameSite=Strict flags, verified by SessionCookieTest#flagsArePresent.</subtitle>   <facts>     <fact>SessionCookieConfig.java calls cookie.setHttpOnly(true), cookie.setSecure(true), and cookie.setSameSite(... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml</title>   <subtitle>Adjusted datasource connection pool sizing values in application.yml.</subtitle>   <facts>     <fact>application.yml sets spring.datasource.hikari.maximum-pool-size to 20</fact>     <fact>application.yml sets spring.datasource.hikari.minimum-idle to 5</fact> ... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Changed root logger level to INFO and appender to JSON in logback-spring.xml</title>   <subtitle>Root logger now logs at INFO level through the JSON appender in the Spring Boot logging configuration.</subtitle>   <facts>     <fact>logback-spring.xml root logger level is set to INFO</fact>     <fact>logback-spring.xml root logger references the JSON appe... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Extended Dockerfile with HEALTHCHECK against /actuator/health</title>   <subtitle>Dockerfile now probes the /actuator/health endpoint every 30 seconds using curl.</subtitle>   <facts>     <fact>Dockerfile adds HEALTHCHECK with --interval=30s.</fact>     <fact>HEALTHCHECK command runs curl -f against http://localhost:8080/actuator/health.</fact>     <fac... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 to pom.xml for code coverage reporting</title>   <subtitle>Maven build now includes JaCoCo code coverage plugin, generating reports during verify phase.</subtitle>   <facts>     <fact>pom.xml adds org.jacoco:jacoco-maven-plugin version 0.8.12</fact>     <fact>mvn verify generates JaCoCo coverage report at target/site/jac... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Documented local setup steps in README.md</title>   <subtitle>Added a three-step local setup guide covering JDK 21, Postgres via docker compose, and Spring Boot run command.</subtitle>   <facts>     <fact>README.md now contains a "## Setup" section with three numbered steps.</fact>     <fact>Step 1 instructs installing JDK 21.</fact>     <fact>Step 2 ru... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Documented queue recovery procedure in docs/runbook.md</title>   <subtitle>Created runbook with three-step recovery: inspect pending_messages, restart worker, verify /api/health</subtitle>   <facts>     <fact>docs/runbook.md created with queue recovery procedure for stuck message processing</fact>     <fact>Procedure step 1: check pending_messages table... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Documented 2.4.0 release notes in CHANGELOG.md</title>   <subtitle>Added the 2.4.0 changelog entry covering offline replay evaluation and the V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md gains a "## 2.4.0 - 2026-11-03" section.</fact>     <fact>The 2.4.0 entry lists "Added offline replay evaluation".</fact>     <fact>The 2.4.0... [truncated]
```

### docs-migration-guide

```text
```xml <observation>   <type>change</type>   <title>Documented V20 migration prerequisite in docs/MIGRATION.md</title>   <subtitle>Added V20 section requiring mem_sessions backup and app.manual_sentinel_reviewed=true before running the migration.</subtitle>   <facts>     <fact>docs/MIGRATION.md now contains a V20 section stating the prerequisite: back up mem_sessions before running the migration.<... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Added vulnerability disclosure policy security@acme.com in SECURITY.md</title>   <subtitle>New security policy document establishes a private disclosure channel and prohibits public issue reports.</subtitle>   <facts>     <fact>SECURITY.md defines the vulnerability disclosure channel as security@acme.com.</fact>     <fact>SECURITY.md instructs reporters... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Documented verify-local.sh pre-PR requirement in CONTRIBUTING.md</title>   <subtitle>CONTRIBUTING.md now mandates running ./scripts/verify-local.sh before opening a pull request, naming the four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md now states "Run ./scripts/verify-local.sh before opening a pull request."</fact>     <fact>The verif... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added readinessProbe on /api/health:37777 in deployment.yaml</title>   <subtitle>Deployment now gates Pod readiness on the HTTP health endpoint with a 15 second initial delay.</subtitle>   <facts>     <fact>deploy/deployment.yaml adds a readinessProbe using httpGet path /api/health on port 37777.</fact>     <fact>The readinessProbe sets initialDelaySeco... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Extended Java version matrix in .github/workflows/ci.yml</title>   <subtitle>CI workflow now tests the backend across JDK 21 and 25 using a build matrix strategy.</subtitle>   <facts>     <fact>The `strategy.matrix.java` block in `.github/workflows/ci.yml` sets the Java versions to `[21, 25]`.</fact>     <fact>The CI workflow now runs the backend on bot... [truncated]
```

### deploy-docker-multistage

```text
<observation>   <type>change</type>   <title>Converted Dockerfile to multi-stage build with eclipse-temurin:21-jre runtime</title>   <subtitle>Docker image now separates maven build stage from a slim JRE runtime stage, cutting size from 780MB to 210MB.</subtitle>   <facts>     <fact>Dockerfile is now multi-stage with a maven build stage and a runtime stage based on eclipse-temurin:21-jre.</fact>  ... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted deploy IAM role to AmazonS3ReadOnlyAccess in infra/main.tf</title>   <subtitle>Narrowed deploy role from full S3 access to read-only S3 access via managed policy attachment.</subtitle>   <facts>     <fact>aws_iam_role_policy "deploy" in infra/main.tf now references arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess</fact>     <fact>The deploy rol... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added rollback.sh kubectl rollout undo script for claude-mem deployment</title>   <subtitle>New shell script rolls back the claude-mem Kubernetes deployment and waits for rollout status.</subtitle>   <facts>     <fact>scripts/rollback.sh executes `kubectl rollout undo deployment/claude-mem` to revert the claude-mem deployment</fact>     <fact>scripts/r... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Enabled canary weight 10 and errorThreshold 0.02 in canary.yaml</title>   <subtitle>Canary deployment activated at 10 percent traffic with automatic rollback above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true with canary.weight 10 and errorThreshold 0.02</fact>     <fact>The 10 percent canary rollo... [truncated]
```

### remove-dead-code

```text
<observation>   <type>change</type>   <title>Removed legacySync from OrderService.java</title>   <subtitle>Deleted the unused public OrderService.legacySync method that forwarded orders to warehouse.push.</subtitle>   <facts>     <fact>The public method `legacySync(Order order)` was deleted from `src/main/java/com/acme/orders/OrderService.java`.</fact>     <fact>The removed method body consisted s... [truncated]
```

### remove-stale-config

```text
<observation>   <type>change</type>   <title>Disabled legacy-search.enabled in application.yml</title>   <subtitle>Removed legacy-search feature flag from configuration, leaving search-v2 as the only search implementation.</subtitle>   <facts>     <fact>application.yml no longer defines legacy-search.enabled</fact>     <fact>The search module now reads only search-v2</fact>   </facts>   <narrative... [truncated]
```

### remove-deprecated-endpoint

```text
<observation>   <type>change</type>   <title>Removed legacy /v1/users listLegacy endpoint in UserController.java</title>   <subtitle>Deleted the deprecated v1 user listing endpoint, leaving only /v2/users active.</subtitle>   <facts>     <fact>The `@GetMapping("/v1/users")` annotation and `listLegacy()` method were removed from `src/main/java/com/acme/users/UserController.java`</fact>     <fact>Th... [truncated]
```

### remove-feature-flag

```text
<observation>   <type>change</type>   <title>Removed LEGACY_SEARCH feature flag constant in FeatureFlags.java</title>   <subtitle>Deleted the legacy-search flag constant and migrated callers to SEARCH_V2.</subtitle>   <facts>     <fact>FeatureFlags.java no longer declares `public static final String LEGACY_SEARCH = "legacy-search"`.</fact>     <fact>Callers previously referencing LEGACY_SEARCH now... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared verb class, (2) the declared subject tokens to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. The legacy keyword-coverage metric is reported alongside for before/after comparison.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
