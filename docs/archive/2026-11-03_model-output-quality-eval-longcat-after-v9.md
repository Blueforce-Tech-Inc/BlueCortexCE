# Model output quality evaluation

- Date: 2026-11-03
- Phase: after-v9
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 87 unique; runs scored: 3; key repeat: 3; real calls: 261 (cap none); duration: 3879811 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 98% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 20/0/0/67 |
| format errors | 0 |
| validation rejections | 2 |
| call failures | 0 |
| title exact-hit rate (legacy) | 52% |
| title normalized hit rate (legacy) | 58% |
| title action-class hit rate (A6) | 89% (65 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 77% |
| title subject hit rate (legacy, diagnostic) | 83% |
| title trace-visible subject hit rate (A6b) | 89% (55 scored, 10 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 74% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 80% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 65% |
| title identifier hit rate | 98% |
| type accuracy | 95% |
| facts keyword precision (legacy) / recall | 80% / 93% |
| facts informative precision | 86% |
| facts trace-support rate | 100% |
| concepts precision / recall | 87% / 85% |
| skip consistency across repeats | 100% (87 repeated samples) |
| latency p50 / p95 / max (ms) | 15726 / 33716 / 45624 |
| tokens p50 / p95 / max / mean per call | 4352 / 5763 / 6366 / 4420 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 86% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 89% | PASS |
| A6b | title trace-visible subject | >= 90% | 89% | **FAIL** |
| A6c | title identifiers | = 100% | 98% | **FAIL** |
| A6 | all three title dimensions together | all pass | 80% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 87 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 87 | 100% | 58% | 74% | 80% | 86% | 93% | 0 |
| 1 | 87 | 100% | 61% | 78% | 75% | 82% | 91% | 0 |
| 2 | 87 | 100% | 69% | 78% | 75% | 85% | 95% | 0 |

- average: skip 100%, title 63%, title contract 76%, facts P/R 77%/93%, facts actionable 84%
- worst run: skip 100%, title 58%, title contract 74%, facts P/R 75%/91%, facts actionable 82%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 12/12 | 10/12 |
| refactor | 10 | 10/10 | 9/10 | 8/10 |
| test-add | 9 | 8/9 | 8/9 | 6/9 |
| security-fix | 9 | 9/9 | 8/9 | 4/9 |
| config-change | 12 | 12/12 | 12/12 | 6/12 |
| docs-update | 8 | 8/8 | 8/8 | 2/8 |
| deploy-change | 7 | 6/7 | 5/7 | 2/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Run-to-run variance (repeated samples)

| metric | min | max | mean | std dev |
|---|---|---|---|---|
| type accuracy | 90% | 95% | 91% | 3% |
| title action class | 88% | 91% | 89% | 1% |
| title trace-visible subject | 89% | 98% | 93% | 4% |
| title identifiers | 98% | 100% | 99% | 1% |
| title trace contract | 80% | 87% | 84% | 3% |
| facts informative | 82% | 86% | 84% | 2% |
| skip accuracy | 100% | 100% | 100% | 0% |

## Inconsistent samples (type or action changed between runs)

18 of 87 repeated samples disagreed:

- refactor-config-loader: type {change, refactor, feature}; action {secure, modify}
- refactor-sql-repository: type {refactor, bugfix}
- test-contract-openapi: type {unparsed, feature}; action {unparsed, create}
- security-authz-admin: type {change, bugfix}
- security-csrf: action {secure, modify}
- security-secret-config: action {secure, remove}
- security-rate-limit: type {bugfix, feature}
- security-cookie-httponly: type {bugfix, change}
- config-cors: type {change, bugfix}
- config-docker-healthcheck: action {create, modify}
- config-maven-plugin: type {change, feature}; action {modify, create}
- docs-runbook: action {document, create}
- docs-security-policy: action {create, document}
- deploy-k8s-probe: action {modify, create}
- deploy-github-actions: type {unparsed, change}; action {unparsed, modify}
- remove-dead-code: type {refactor, change}
- remove-stale-config: action {modify, remove}
- remove-feature-flag: type {change, refactor}

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 10539 |
| bug-null-cache | bug-fix | observation | observation | pass | 75% | MISS (subject unreachable, id MISS, action hit) | miss | match | 100%/100% | 67% | 100%/100% | 7870 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 27995 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 16758 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 15489 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/67% | 100% | 100%/100% | 17252 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 16572 |
| bug-race-counter | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/67% | 100% | 50%/50% | 12463 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 16382 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 19289 |
| bug-regex-redos | bug-fix | observation | observation | pass | 100% | hit | miss | match | 50%/50% | 100% | 100%/100% | 10941 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 16298 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 67% | 100%/100% | 16413 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 100% | 100%/100% | 9125 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | MISS (subject hit, id hit, action MISS) | miss | MISS | 67%/100% | 100% | 50%/50% | 31072 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 23042 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 8622 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 17285 |
| refactor-sql-repository | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 15385 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 100%/100% | 17306 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 100% | 100%/100% | 15469 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/67% | 100% | 50%/50% | 24495 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 35590 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 100% | 100%/100% | 13361 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 18866 |
| test-parameterized-csv | test-add | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 33%/50% | 33% | 100%/100% | 9169 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 75%/100% | 75% | 100%/100% | 16609 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 17508 |
| test-contract-openapi | test-add | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 37844 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 100%/67% | 100% | 100%/100% | 45624 |
| security-redact-log | security-fix | observation | observation | pass | 67% | MISS (subject MISS, id hit, action hit) | miss | match | 100%/100% | 100% | 100%/100% | 15726 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 67%/100% | 67% | 100%/100% | 16059 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | hit | miss | MISS | 100%/100% | 100% | 0%/0% | 16003 |
| security-csrf | security-fix | observation | observation | pass | 67% | hit | miss | match | 67%/50% | 100% | 50%/50% | 22453 |
| security-secret-config | security-fix | observation | observation | pass | 50% | hit | miss | match | 67%/100% | 100% | 50%/50% | 7653 |
| security-xxe | security-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 17676 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 50%/100% | 100% | 100%/100% | 8537 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS (subject hit, id hit, action MISS) | miss | match | 33%/100% | 100% | 50%/50% | 15903 |
| security-cookie-httponly | security-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 33% | 50%/50% | 33716 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 67% | 100%/100% | 8521 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 18896 |
| config-log-level | config-change | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 67%/100% | 33% | 100%/100% | 16953 |
| config-cors | config-change | observation | observation | pass | 75% | hit | miss | match | 50%/100% | 100% | 100%/100% | 41337 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 10929 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 22535 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 100%/100% | 100% | 100%/100% | 15342 |
| config-maven-plugin | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 12598 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 15129 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 100%/100% | 18568 |
| docs-runbook | docs-update | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 14575 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 17385 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 12053 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 50%/50% | 9109 |
| docs-security-policy | docs-update | observation | observation | pass | 75% | MISS (subject MISS, id hit, action MISS) | miss | match | 100%/100% | 50% | 50%/50% | 13661 |
| docs-contributing | docs-update | observation | observation | pass | 33% | hit | miss | match | 100%/100% | 50% | 100%/100% | 16302 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 67% | 50%/50% | 22398 |
| deploy-github-actions | deploy-change | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 15284 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 100%/100% | 32417 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 50% | 100%/100% | 15377 |
| deploy-helm-values | deploy-change | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 100% | 100%/100% | 12807 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | MISS | 67%/100% | 33% | 100%/100% | 17850 |
| deploy-canary | deploy-change | observation | observation | pass | 50% | hit | miss | match | 67%/100% | 100% | 100%/100% | 17722 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3372 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3791 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2614 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5750 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4425 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2968 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3735 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4124 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2215 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 18070 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 19926 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5665 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 7288 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 18458 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 22619 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 20368 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2674 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3205 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3472 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5236 |
| remove-dependency | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/50% | 15257 |
| remove-dead-code | refactor | observation | observation | pass | 50% | hit | miss | match | 67%/100% | 33% | 100%/50% | 29299 |
| remove-stale-config | config-change | observation | observation | pass | 75% | MISS (subject unreachable, id hit, action MISS) | miss | match | 100%/100% | 100% | 100%/100% | 16610 |
| remove-deprecated-endpoint | config-change | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 100%/100% | 11415 |
| remove-feature-flag | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 15509 |

## Problems

- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed UserCache.get concurrent null placeholder race in UserCache')
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-jwt-expiry: facts recall 67% (expected [expiration, isEqual, rejects], got [JwtVerifier previously used `claims.getExpiration().before(now)`, which allowed a token to pass when its expiration timestamp was exactly equal to `now`., The condition was changed to `claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now)` so equality is also treated as expired., JwtVerifierTest#expiredAtExactBoundary passes after the fix, pinning the boundary behavior.])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: facts recall 67% (expected [incrementAndGet, AtomicLong, 1000], got [CounterService.increment now calls counter.incrementAndGet() instead of counter.set(counter.get() + 1); return counter.get()., CounterServiceConcurrencyTest passes with 1000 threads producing 1000.])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-regex-redos: facts recall 50% (expected [linear pattern, longInputFinishesUnder100ms], got [UrlValidator.java URL pattern replaced with `^https?://[^\\s]+$` eliminating nested quantifiers, UrlValidatorTest#longInputFinishesUnder100ms passes after the change])
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed FileReaderFactory to use UTF-8 instead of platform default charset')
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [AuthController.java no longer builds JWTs inline and instead calls tokenService.issue(authentication)., The return statement in AuthController was changed to `return tokenService.issue(authentication)`.])
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title action 'modify' missing: title leads 'secure' (got 'Hardened ConfigLoader to return unmodifiable map with typed accessors')
- refactor-config-loader: title verb class 'refactor' missing (got 'Hardened ConfigLoader to return unmodifiable map with typed accessors')
- refactor-config-loader: type 'change' != expected 'refactor'
- refactor-config-loader: concepts recall 50% (expected [what-changed, pattern], got [what-changed, gotcha])
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [src/main/java/com/acme/users/UserRepository.java now declares `@Query("select u from User u where u.tenantId = :tenantId")` on the tenant lookup method., UserRepositoryTest returns the same tenant rows after the change, confirming behaviour is preserved.])
- test-repro-timezone: facts recall 67% (expected [Asia/Shanghai, 2026-11-02, 1 failure], got [DailyReportTest#reportUsesBusinessTimezone asserts report.dateAt(Instant.parse("2026-11-01T23:30:00Z"), ZoneId.of("Asia/Shanghai")) equals LocalDate.of(2026, 11, 2), The test fails with expected 2026-11-02 but got 2026-11-01, The bug is that dateAt computes the date in UTC instead of the specified business timezone])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles is annotated with @ParameterizedTest and @CsvSource({"1,100","2,200","3,400"})., The test asserts policy.delay(attempt) equals the expected value for attempts 1, 2, and 3, covering 100 ms, 200 ms, and 400 ms., All 3 parameterized cases pass against the RetryPolicy implementation.])
- test-parameterized-csv: title coverage 75% (expected keywords [add, csv, parameterized, test])
- test-parameterized-csv: title subject [parameterized] missing (got 'Added CsvExporterTest#quotingIsStable covering CSV quoting for commas, quotes and newlines')
- test-parameterized-csv: title trace-visible subject [parameterized] missing (got 'Added CsvExporterTest#quotingIsStable covering CSV quoting for commas, quotes and newlines')
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest.java was created at src/test/java/com/acme/export/CsvExporterTest.java, The test method quotingIsStable is annotated with @ParameterizedTest and @ValueSource(strings = {"a,b","a\"b","a\nb"}), The three parameterized cases cover comma, double-quote, and newline characters and all 3 pass])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-contract-openapi: validation rejected the observation: title too long: 101 chars > 100
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- test-flaky-clock: title verb class 'fix' missing (got 'Refactored SchedulerTest time control from Thread.sleep to Clock.fixed')
- test-flaky-clock: title subject [flaky, clock] missing (got 'Refactored SchedulerTest time control from Thread.sleep to Clock.fixed')
- test-flaky-clock: title trace-visible subject [flaky, clock] missing (got 'Refactored SchedulerTest time control from Thread.sleep to Clock.fixed')
- test-flaky-clock: facts recall 67% (expected [Clock.fixed, Thread.sleep, 20 consecutive], got [SchedulerTest sets scheduler.clock to Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)., After the change, 20 consecutive runs of SchedulerTest pass.])
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: title subject [credentials] missing (got 'Redacted password token secret values in LogSanitizer')
- security-redact-log: title trace-visible subject [credentials] missing (got 'Redacted password token secret values in LogSanitizer')
- security-sql-injection: title action 'secure' missing: title leads 'modify' (got 'Fixed UserDao.findByEmail SQL injection via parameter placeholder in UserDao.java')
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Restricted AdminController endpoints authorization to ROLE_ADMIN')
- security-authz-admin: type 'change' != expected 'bugfix'
- security-authz-admin: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, trade-off])
- security-csrf: title coverage 67% (expected keywords [fix, csrf, csrffilter])
- security-csrf: title verb class 'fix' missing (got 'Hardened CsrfFilter CSRF token validation returning 403 in CsrfFilter.java')
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter now calls response.sendError(403) and returns when the CSRF token does not match the request., CsrfFilterTest#postWithoutTokenIs403 passes, confirming a POST without a valid token yields HTTP 403., The rejection guard is `if (!csrfToken.matches(request))` placed in src/main/java/com/acme/web/CsrfFilter.java.])
- security-csrf: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, problem-solution])
- security-secret-config: title coverage 50% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: title verb class 'remove' missing (got 'Redacted API key in application.yml using ACME_API_KEY env var')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Redacted API key in application.yml using ACME_API_KEY env var')
- security-secret-config: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- security-path-traversal: title action 'secure' missing: title leads 'modify' (got 'Fixed path traversal vulnerability in FileDownloadController path resolution')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title action 'secure' missing: title leads 'modify' (got 'Fixed RateLimiter empty token bucket 429 in RateLimiter.java')
- security-rate-limit: title verb class 'add' missing (got 'Fixed RateLimiter empty token bucket 429 in RateLimiter.java')
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, what-changed])
- security-cookie-httponly: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- config-hikari-pool: title coverage 75% (expected keywords [change, hikari, pool, size])
- config-hikari-pool: title verb class 'change' missing (got 'Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml')
- config-log-level: title coverage 75% (expected keywords [change, logback, root, json])
- config-log-level: title verb class 'change' missing (got 'Set root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: title subject [log, level] missing (got 'Set root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: title trace-visible subject [level] missing (got 'Set root logger to INFO with JSON appender in logback-spring.xml')
- config-cors: title coverage 75% (expected keywords [restrict, cors, corsconfig, origin])
- config-docker-healthcheck: title action 'modify' missing: title leads 'create' (got 'Added HEALTHCHECK against /actuator/health in Dockerfile')
- config-maven-plugin: title coverage 75% (expected keywords [add, jacoco, plugin, pom])
- config-maven-plugin: title verb class 'add' missing (got 'Extended pom.xml with jacoco-maven-plugin 0.8.12')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [pattern, what-changed])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Documented local setup steps in README.md')
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title verb class 'add' missing (got 'Documented queue recovery procedure in docs/runbook.md')
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title verb class 'change' missing (got 'Documented 2.4.0 offline replay evaluation and V20 migration conflict in CHANGELOG.md')
- docs-migration-guide: title coverage 75% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [what-changed, pattern])
- docs-security-policy: title coverage 75% (expected keywords [add, security, policy, disclosure])
- docs-security-policy: title action 'document' missing: title leads 'create' (got 'Added SECURITY.md defining security@acme.com disclosure channel')
- docs-security-policy: title subject [security, policy] missing (got 'Added SECURITY.md defining security@acme.com disclosure channel')
- docs-security-policy: title trace-visible subject [security, policy] missing (got 'Added SECURITY.md defining security@acme.com disclosure channel')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 33% (expected keywords [update, contributing, verify-local])
- docs-contributing: title verb class 'change' missing (got 'Documented pull request verification requirement in CONTRIBUTING.md')
- deploy-k8s-probe: title coverage 75% (expected keywords [add, readiness, probe, deployment])
- deploy-k8s-probe: title verb class 'add' missing (got 'Extended deployment.yaml with readinessProbe on /api/health:37777')
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-github-actions: validation rejected the observation: no verifiable facts after evidence check
- deploy-terraform-iam: title coverage 75% (expected keywords [restrict, iam, terraform, s3])
- deploy-rollback-script: title coverage 75% (expected keywords [add, rollback, script, deployment])
- deploy-rollback-script: title subject [rollback, script] missing (got 'Added rollback.sh for claude-mem deployment rollback')
- deploy-rollback-script: title trace-visible subject [rollback, script] missing (got 'Added rollback.sh for claude-mem deployment rollback')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 50% (expected keywords [enable, canary, rollback, threshold])
- remove-dependency: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])
- remove-dead-code: title coverage 50% (expected keywords [remove, dead, code, orderservice])
- remove-dead-code: title subject [dead, code] missing (got 'Removed legacySync method from OrderService')
- remove-dead-code: concepts recall 50% (expected [what-changed, pattern], got [what-changed, what-changed])
- remove-stale-config: title coverage 75% (expected keywords [remove, legacy, search, application])
- remove-stale-config: title action 'remove' missing: title leads 'modify' (got 'Disabled legacy-search.enabled in application.yml')
- remove-stale-config: title verb class 'remove' missing (got 'Disabled legacy-search.enabled in application.yml')
- remove-stale-config: title subject [stale, setting] missing (got 'Disabled legacy-search.enabled in application.yml')
- remove-deprecated-endpoint: title coverage 50% (expected keywords [remove, deprecated, endpoint, usercontroller])
- remove-deprecated-endpoint: title subject [deprecated, endpoint] missing (got 'Removed UserController.listLegacy and /v1/users mapping in UserController')

## Raw answers behind problems

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache.get concurrent null placeholder race in UserCache</title>   <subtitle>Replaced direct cache.get with computeIfAbsent so concurrent callers cannot both write a null placeholder.</subtitle>   <facts>     <fact>UserCache.get now calls cache.computeIfAbsent(id, loader::load) instead of cache.get(id).</fact>     <fact>The previous cache.get(i... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone reproducing DailyReport off-by-one-day</title>   <subtitle>Test pins 2026-11-01T23:30Z and expects Asia/Shanghai business date 2026-11-02, reproducing the off-by-one-day bug.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins input 2026-11-01T23:30Z and expects the Asia/Shangh... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration boundary accepting tokens at exact expiry instant</title>   <subtitle>JwtVerifier now rejects tokens whose expiration equals the current instant, closing an off-by-one boundary bug.</subtitle>   <facts>     <fact>JwtVerifier previously used `claims.getExpiration().before(now)`, which allowed a token to pass when its expirati... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsvExporter embedded line endings in multiline CSV export</title>   <subtitle>CsvExporter now normalises embedded line endings to CRLF before quoting so multiline fields keep their row count.</subtitle>   <facts>     <fact>CsvExporter now calls writer.write(line.replaceAll("\\r?\\n", "\\r\\n")) to normalise embedded line endings to CRLF before quo... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment race condition with AtomicLong.incrementAndGet</title>   <subtitle>Replaced a non-atomic get/set pair in CounterService.increment with a single atomic incrementAndGet call.</subtitle>   <facts>     <fact>CounterService.increment now calls counter.incrementAndGet() instead of counter.set(counter.get() + 1); return counter.g... [truncated]
```

### bug-regex-redos

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed UrlValidator nested-quantifier regex catastrophic backtracking</title>   <subtitle>Replaced exponential-backtracking URL regex with linear pattern to prevent ReDoS on long inputs.</subtitle>   <facts>     <fact>UrlValidator.java URL pattern replaced with `^https?://[^\\s]+$` eliminating nested quantifiers</fact>     <fact>UrlValidatorTest#l... [truncated]
```

### bug-file-encoding

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed FileReaderFactory to use UTF-8 instead of platform default charset</title>   <subtitle>FileReaderFactory now explicitly specifies StandardCharsets.UTF_8 for InputStreamReader, preventing character corruption on non-UTF-8 platforms.</subtitle>   <facts>     <fact>FileReaderFactory.java now constructs InputStreamReader with StandardCharsets.U... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored AuthController JWT issuance to delegate to TokenService.issue in AuthController.java</title>   <subtitle>AuthController now delegates JWT creation to TokenService.issue instead of building tokens inline.</subtitle>   <facts>     <fact>AuthController.java no longer builds JWTs inline and instead calls tokenService.issue(authentication).</fac... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>change</type>   <title>Hardened ConfigLoader to return unmodifiable map with typed accessors</title>   <subtitle>ConfigLoader now returns an immutable configuration map and provides typed accessor methods.</subtitle>   <facts>     <fact>ConfigLoader returns an unmodifiable map via `toUnmodifiableMap(...)` instead of a mutable map.</fact>     <fact>ConfigLoader now exposes typ... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>refactor</type>   <title>Refactored UserRepository tenant lookup to parameterized JPQL @Query</title>   <subtitle>UserRepository now binds tenantId via a named JPQL parameter instead of string-concatenating SQL.</subtitle>   <facts>     <fact>src/main/java/com/acme/users/UserRepository.java now declares `@Query("select u from User u where u.tenantId = :tenantId")` on the tena... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone reproducing dateAt UTC-vs-business-date bug</title>   <subtitle>Test pins 2026-11-01T23:30Z expecting 2026-11-02 in Asia/Shanghai; dateAt currently returns 2026-11-01.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone asserts report.dateAt(Instant.parse("2026-11-01T23:30:00Z"), Zo... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 reproducing locked account 401 instead of 423</title>   <subtitle>New test pins the bug where locked accounts return HTTP 401 instead of expected 423 Locked status.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true.</fact>     <fact>The... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized cases for exponential backoff</title>   <subtitle>Parameterized test pins RetryPolicy delay values at 100, 200, and 400 ms across attempts 1-3.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles is annotated with @ParameterizedTest and @CsvSource({"1,100","2,200","3,400"}).</fact>     <fact>The ... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable covering CSV quoting for commas, quotes and newlines</title>   <subtitle>New parameterized test pins stable CSV quoting across three delimiter-edge character cases.</subtitle>   <facts>     <fact>CsvExporterTest.java was created at src/test/java/com/acme/export/CsvExporterTest.java</fact>     <fact>The test method ... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest reproducing UserCache null</title>   <subtitle>New parallel test reproduces intermittent null returns from UserCache under concurrent reads.</subtitle>   <facts>     <fact>Test CacheConcurrencyTest#concurrentReadersNeverSeeNull uses IntStream.range(0, 1000).parallel() to call cache.get("k") concurrently</fact>     <fact>The t... [truncated]
```

### test-contract-openapi

```text
<observation>   <type>feature</type>   <title>Added OpenApiContractTest#generatedSpecMatchesCommittedFile diffs springdoc against docs/openapi.yaml</title>   <subtitle>New contract test fails when springdoc-generated OpenAPI spec diverges from the committed docs/openapi.yaml.</subtitle>   <facts>     <fact>OpenApiContractTest#generatedSpecMatchesCommittedFile compares springdoc output against docs... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Refactored SchedulerTest time control from Thread.sleep to Clock.fixed</title>   <subtitle>SchedulerTest now injects a fixed clock, making time-dependent assertions deterministic and removing flakiness.</subtitle>   <facts>     <fact>SchedulerTest sets scheduler.clock to Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC).</fact>     <fa... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Redacted password token secret values in LogSanitizer</title>   <subtitle>LogSanitizer now masks credential query parameters before writing to logs, preventing secret leakage.</subtitle>   <facts>     <fact>LogSanitizer applies `value.replaceAll("(password|token|secret)=\\S+", "$1=***")` to redact password, token, and secret values.</fact>     <fact>Log... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed UserDao.findByEmail SQL injection via parameter placeholder in UserDao.java</title>   <subtitle>Replaced string concatenation with a parameterized query placeholder in findByEmail, closing a SQL injection vector.</subtitle>   <facts>     <fact>UserDao.findByEmail previously built its query by string concatenation, allowing an email value containin... [truncated]
```

### security-authz-admin

```text
<observation>   <type>change</type>   <title>Restricted AdminController endpoints authorization to ROLE_ADMIN</title>   <subtitle>Added @PreAuthorize hasRole('ADMIN') to AdminController, blocking non-admin access to all endpoints.</subtitle>   <facts>     <fact>AdminController.java now applies @PreAuthorize("hasRole('ADMIN')") restricting every endpoint to ROLE_ADMIN</fact>     <fact>AdminAuthoriz... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Hardened CsrfFilter CSRF token validation returning 403 in CsrfFilter.java</title>   <subtitle>State-changing requests with a mismatched CSRF token are now rejected with HTTP 403.</subtitle>   <facts>     <fact>CsrfFilter now calls response.sendError(403) and returns when the CSRF token does not match the request.</fact>     <fact>CsrfFilterTest#postWit... [truncated]
```

### security-secret-config

```text
```xml <observation>   <type>bugfix</type>   <title>Redacted API key in application.yml using ACME_API_KEY env var</title>   <subtitle>Replaced hardcoded API key in application.yml with environment variable reference to prevent secret leakage.</subtitle>   <facts>     <fact>application.yml previously contained literal API key value sk-live-123</fact>     <fact>api-key property now references ${ACM... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Fixed path traversal vulnerability in FileDownloadController path resolution</title>   <subtitle>FileDownloadController now rejects names that escape the base directory using normalize and startsWith validation.</subtitle>   <facts>     <fact>FileDownloadController.java now calls base.resolve(name).normalize() and throws InvalidPathException(name) when ... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter empty token bucket 429 in RateLimiter.java</title>   <subtitle>RateLimiter now throws TooManyRequestsException when bucket.tryConsume(1) fails, returning HTTP 429.</subtitle>   <facts>     <fact>RateLimiter.java now throws TooManyRequestsException when bucket.tryConsume(1) returns false.</fact>     <fact>RateLimiterTest#eleventhRequest... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened session cookie with HttpOnly, Secure and SameSite=Strict in SessionCookieConfig</title>   <subtitle>Session cookie now protected against XSS, MITM and CSRF attacks via security flags</subtitle>   <facts>     <fact>SessionCookieConfig.java sets cookie.setHttpOnly(true), cookie.setSecure(true), and cookie.setSameSite(Strict)</fact>     <fact>Sess... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml</title>   <subtitle>Database connection pool configured with maximum 20 connections and minimum 5 idle connections.</subtitle>   <facts>     <fact>application.yml now sets spring.datasource.hikari.maximum-pool-size to 20</fact>     <fact>application.yml now sets spring.datasourc... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Set root logger to INFO with JSON appender in logback-spring.xml</title>   <subtitle>Logback configuration now emits structured JSON logs at INFO level via the JSON appender.</subtitle>   <facts>     <fact>logback-spring.xml sets the root logger level to INFO</fact>     <fact>logback-spring.xml root logger references the JSON appender via appender-ref</... [truncated]
```

### config-cors

```text
<observation>   <type>change</type>   <title>Restricted CORS for /api/** to https://app.acme.com with GET/POST in CorsConfig.java</title>   <subtitle>CorsConfig now permits only https://app.acme.com for GET and POST on /api/**; CorsConfigTest verifies a rejected origin.</subtitle>   <facts>     <fact>CorsConfig.registry.addMapping("/api/**") uses allowedOrigins("https://app.acme.com") and allowedM... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added HEALTHCHECK against /actuator/health in Dockerfile</title>   <subtitle>Docker image now self-reports container health via Spring Boot actuator endpoint every 30 seconds.</subtitle>   <facts>     <fact>Dockerfile adds HEALTHCHECK instruction with --interval=30s</fact>     <fact>Healthcheck command is `curl -f http://localhost:8080/actuator/health |... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Extended pom.xml with jacoco-maven-plugin 0.8.12</title>   <subtitle>Maven build now runs JaCoCo coverage reporting during verify, emitting an XML report.</subtitle>   <facts>     <fact>pom.xml declares org.jacoco:jacoco-maven-plugin with version 0.8.12.</fact>     <fact>Running mvn verify writes the coverage report to target/site/jacoco/jacoco.xml.</fa... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Documented local setup steps in README.md</title>   <subtitle>Added a three-step local setup section covering JDK 21, Docker Compose Postgres, and Maven Spring Boot run.</subtitle>   <facts>     <fact>README.md now contains a "## Setup" section with three numbered steps.</fact>     <fact>Step 1 instructs installing JDK 21.</fact>     <fact>Step 2 instru... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Documented queue recovery procedure in docs/runbook.md</title>   <subtitle>New runbook documents three-step queue recovery: check pending_messages, restart worker, verify /api/health.</subtitle>   <facts>     <fact>docs/runbook.md contains a numbered three-step queue recovery procedure</fact>     <fact>Step 1 of docs/runbook.md instructs to check pendin... [truncated]
```

### docs-changelog

```text
```xml <observation>   <type>change</type>   <title>Documented 2.4.0 offline replay evaluation and V20 migration conflict in CHANGELOG.md</title>   <subtitle>Added version 2.4.0 release notes listing offline replay evaluation and a V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md now contains a "## 2.4.0 - 2026-11-03" section header.</fact>     <fact>The 2.4.0 entry records ... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisite in docs/MIGRATION.md</title>   <subtitle>Added a V20 section requiring a mem_sessions backup and app.manual_sentinel_reviewed=true before running the migration.</subtitle>   <facts>     <fact>docs/MIGRATION.md gained a V20 section describing its prerequisite steps.</fact>     <fact>The V20 prerequisite requires back... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Added SECURITY.md defining security@acme.com disclosure channel</title>   <subtitle>New security policy document establishes a private vulnerability disclosure channel and forbids public issues.</subtitle>   <facts>     <fact>SECURITY.md defines security@acme.com as the channel for reporting vulnerabilities</fact>     <fact>SECURITY.md instructs that vu... [truncated]
```

### docs-contributing

```text
```xml <observation>   <type>change</type>   <title>Documented pull request verification requirement in CONTRIBUTING.md</title>   <subtitle>CONTRIBUTING.md now requires ./scripts/verify-local.sh before opening a pull request and names four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md requires ./scripts/verify-local.sh to be run before opening a pull request.</fact>     <fact>CONTRIB... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Extended deployment.yaml with readinessProbe on /api/health:37777</title>   <subtitle>Kubernetes deployment now gates pod traffic on an HTTP readiness probe against /api/health port 37777.</subtitle>   <facts>     <fact>deploy/deployment.yaml adds a readinessProbe using httpGet with path /api/health and port 37777</fact>     <fact>The readinessProbe set... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Extended CI workflow matrix to JDK 21 and 25 in ci.yml</title>   <subtitle>Added a Java matrix strategy so the backend CI pipeline runs on both JDK 21 and 25.</subtitle>   <facts>     `.github/workflows/ci.yml` now defines a matrix strategy with `java: [21, 25]` for the backend job.     The CI workflow runs the backend on both JDK 21 and JDK 25.     Wor... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted deploy role IAM policy to AmazonS3ReadOnlyAccess in infra/main.tf</title>   <subtitle>Narrowed deploy role S3 permissions from full access to read-only via AWS managed policy.</subtitle>   <facts>     <fact>infra/main.tf aws_iam_role_policy deploy now attaches arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess instead of full S3 access</fact>    ... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added rollback.sh for claude-mem deployment rollback</title>   <subtitle>New shell script automates undoing and verifying the claude-mem Kubernetes deployment.</subtitle>   <facts>     <fact>scripts/rollback.sh runs `kubectl rollout undo deployment/claude-mem` followed by `kubectl rollout status deployment/claude-mem`</fact>     <fact>scripts/rollback.... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Enabled canary rollout at 10 percent in infra/canary.yaml</title>   <subtitle>Canary deployment is enabled with a 10 percent traffic weight and a 2 percent error threshold for automatic rollback.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true to activate the canary rollout.</fact>     <fact>canary.weight is set to 10, routi... [truncated]
```

### remove-dependency

```text
<observation>   <type>change</type>   <title>Removed commons-lang3 dependency from pom.xml</title>   <subtitle>Dropped the unused org.apache.commons:commons-lang3 dependency; the Maven build still resolves.</subtitle>   <facts>     <fact>pom.xml no longer declares the org.apache.commons:commons-lang3 dependency.</fact>     <fact>The Maven build resolves successfully without commons-lang3, confirmi... [truncated]
```

### remove-dead-code

```text
```xml <observation>   <type>refactor</type>   <title>Removed legacySync method from OrderService</title>   <subtitle>Deleted the unused legacySync(Order) method that called warehouse.push(order); no references remain.</subtitle>   <facts>     <fact>OrderService.legacySync(Order order) was removed from src/main/java/com/acme/orders/OrderService.java.</fact>     <fact>The removed method body consis... [truncated]
```

### remove-stale-config

```text
<observation>   <type>change</type>   <title>Disabled legacy-search.enabled in application.yml</title>   <subtitle>Removed the legacy-search.enabled flag so the search module reads only search-v2.</subtitle>   <facts>     <fact>application.yml no longer defines legacy-search.enabled (previously set to true).</fact>     <fact>The search module now reads only search-v2, with legacy-search fully remo... [truncated]
```

### remove-deprecated-endpoint

```text
```xml <observation>   <type>change</type>   <title>Removed UserController.listLegacy and /v1/users mapping in UserController</title>   <subtitle>Deleted the legacy list endpoint so only /v2/users remains exposed.</subtitle>   <facts>     <fact>UserController.listLegacy method and its @GetMapping("/v1/users") mapping were removed in src/main/java/com/acme/users/UserController.java.</fact>     <fac... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
