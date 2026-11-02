# Model output quality evaluation

- Date: 2026-11-03
- Phase: after-v2
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 932294 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 99% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 20/0/0/62 |
| format errors | 0 |
| validation rejections | 1 |
| call failures | 0 |
| title exact-hit rate (legacy) | 49% |
| title normalized hit rate | 48% |
| title identifier hit rate | 93% |
| type accuracy | 89% |
| facts precision / recall | 67% / 89% |
| facts trace-support rate | 100% |
| concepts precision / recall | 56% / 79% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 8243 / 15226 / 31106 |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | facts P | facts R | format errors |
|---|---|---|---|---|---|---|
| 0 | 82 | 100% | 48% | 67% | 89% | 0 |
| 1 | 13 | 100% | 60% | 83% | 100% | 0 |
| 2 | 13 | 100% | 70% | 89% | 100% | 0 |

- average: skip 100%, title 59%, facts P/R 80%/96%
- worst run: skip 100%, title 48%, facts P/R 67%/89%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 11/12 | 11/12 | 7/12 |
| refactor | 9 | 9/9 | 7/9 | 4/9 |
| test-add | 9 | 9/9 | 9/9 | 7/9 |
| security-fix | 9 | 9/9 | 6/9 | 3/9 |
| config-change | 8 | 8/8 | 8/8 | 3/8 |
| docs-update | 8 | 8/8 | 7/8 | 2/8 |
| deploy-change | 7 | 7/7 | 6/7 | 3/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title exact | type | facts P/R | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 6338 |
| bug-null-cache | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 7040 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | match | 33%/100% | 33%/50% | 31106 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 6680 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | match | 50%/67% | 67%/100% | 9332 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | miss | match | 67%/67% | 67%/100% | 12520 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 75% | miss | match | 50%/50% | 67%/100% | 10750 |
| bug-race-counter | bug-fix | observation | observation | pass | 100% | hit | match | 100%/67% | 33%/50% | 10920 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 75% | miss | match | 67%/100% | 33%/50% | 14184 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 8391 |
| bug-regex-redos | bug-fix | observation | observation | pass | 75% | miss | match | 50%/50% | 67%/100% | 10909 |
| bug-file-encoding | bug-fix | observation | validation-rejected | FAIL | - | - | - | - | - | 11935 |
| refactor-invoice | refactor | observation | observation | pass | 67% | miss | match | 67%/100% | 67%/100% | 5257 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 7295 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | miss | MISS | 75%/100% | 67%/100% | 16322 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 6499 |
| refactor-retry-policy | refactor | observation | observation | pass | 67% | miss | match | 67%/100% | 67%/100% | 7041 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | match | 67%/100% | 100%/100% | 9462 |
| refactor-sql-repository | refactor | observation | observation | pass | 33% | miss | MISS | 100%/50% | 25%/50% | 12913 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | match | 33%/100% | 67%/100% | 11417 |
| refactor-feature-flag | refactor | observation | observation | pass | 67% | miss | match | 67%/100% | 100%/100% | 7464 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 11798 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | match | 50%/100% | 67%/100% | 7368 |
| test-unit-retry | test-add | observation | observation | pass | 75% | miss | match | 25%/50% | 67%/100% | 7152 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | match | 50%/100% | 67%/100% | 15701 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | match | 25%/50% | 67%/100% | 15851 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | match | 50%/100% | 67%/100% | 8822 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | match | 60%/100% | 33%/50% | 12715 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | match | 33%/100% | 67%/100% | 10682 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | miss | match | 100%/100% | 67%/100% | 10646 |
| security-redact-log | security-fix | observation | observation | pass | 33% | miss | match | 67%/100% | 67%/100% | 13537 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | hit | match | 25%/50% | 67%/100% | 11774 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | miss | MISS | 67%/100% | 0%/0% | 8407 |
| security-csrf | security-fix | observation | observation | pass | 67% | miss | MISS | 100%/50% | 33%/50% | 14223 |
| security-secret-config | security-fix | observation | observation | pass | 75% | miss | match | 50%/50% | 67%/100% | 5550 |
| security-xxe | security-fix | observation | observation | pass | 67% | miss | match | 100%/100% | 50%/100% | 7014 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 9955 |
| security-rate-limit | security-fix | observation | observation | pass | 100% | hit | MISS | 67%/100% | 33%/50% | 10501 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | miss | match | 33%/100% | 67%/100% | 14886 |
| config-hikari-pool | config-change | observation | observation | pass | 50% | miss | match | 67%/100% | 50%/50% | 11459 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | match | 67%/100% | 33%/50% | 9939 |
| config-log-level | config-change | observation | observation | pass | 75% | miss | match | 67%/100% | 50%/50% | 8525 |
| config-cors | config-change | observation | observation | pass | 50% | miss | match | 50%/100% | 33%/50% | 8067 |
| config-timeout | config-change | observation | observation | pass | 75% | miss | match | 33%/100% | 33%/50% | 13388 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 6030 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 8485 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 8243 |
| docs-api-observations | docs-update | observation | observation | pass | 75% | miss | match | 50%/100% | 50%/50% | 5436 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | miss | match | 100%/100% | 50%/50% | 9290 |
| docs-runbook | docs-update | observation | observation | pass | 100% | hit | match | 50%/100% | 67%/100% | 8297 |
| docs-changelog | docs-update | observation | observation | pass | 67% | miss | match | 100%/100% | 50%/50% | 7162 |
| docs-architecture-adr | docs-update | observation | observation | pass | 75% | miss | MISS | 33%/50% | 33%/50% | 9930 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | miss | match | 50%/50% | 67%/100% | 6827 |
| docs-security-policy | docs-update | observation | observation | pass | 100% | hit | match | 100%/50% | 67%/100% | 8911 |
| docs-contributing | docs-update | observation | observation | pass | 33% | miss | match | 100%/100% | 67%/100% | 6647 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | hit | match | 100%/100% | 33%/50% | 9122 |
| deploy-github-actions | deploy-change | observation | observation | pass | 75% | miss | match | 50%/100% | 50%/50% | 10569 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | match | 33%/100% | 67%/100% | 9664 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 50% | miss | match | 67%/100% | 33%/50% | 7393 |
| deploy-helm-values | deploy-change | observation | observation | pass | 25% | miss | match | 67%/100% | 50%/50% | 11066 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | MISS | 67%/100% | 100%/100% | 6775 |
| deploy-canary | deploy-change | observation | observation | pass | 50% | miss | match | 33%/50% | 33%/50% | 8120 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | 3255 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | 2833 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | 5453 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | 15226 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | 4162 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | 2452 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | 4417 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | 6895 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | 4890 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | 2355 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | 3486 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | 3975 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | 4304 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | 2382 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | 3061 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | 3700 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | 4087 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | 3099 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | 2639 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | 6700 |

## Problems

- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [what-changed, gotcha, pattern])
- bug-connection-leak: facts recall 67% (expected [try-with-resources, activeConnections, 0], got [ConnectionPool.borrow in src/main/java/com/acme/db/ConnectionPool.java now uses `try (Connection connection = dataSource.getConnection())` so the connection is closed when the caller throws., PoolLeakTest reports activeConnections back to 0 after 100 failing calls, confirming the leak is resolved.])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-jwt-expiry: facts recall 67% (expected [expiration, isEqual, rejects], got [JwtVerifier expiration check changed from `claims.getExpiration().before(now)` to `claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now)`., Previously a token whose expiration instant exactly matched the current time was accepted., `JwtVerifierTest#expiredAtExactBoundary` passes after the change.])
- bug-csv-crlf: title coverage 75% (expected keywords [fix, csv, line, endings])
- bug-csv-crlf: facts recall 50% (expected [CRLF, multilineFieldKeepsRowCount], got [CsvExporter.java writes each line via `writer.write(line.replaceAll("\\r?\\n", "\\r\\n"))` before quoting., CsvExporterTest#multilineFieldKeepsRowCount passes after this change.])
- bug-race-counter: facts recall 67% (expected [incrementAndGet, AtomicLong, 1000], got [CounterService.increment changed from `counter.set(counter.get() + 1); return counter.get();` to `return counter.incrementAndGet();` in src/main/java/com/acme/metrics/CounterService.java, CounterServiceConcurrencyTest passes with 1000 threads producing a final counter value of 1000])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha, how-it-works])
- bug-n-plus-one: title coverage 75% (expected keywords [fix, n+1, orderrepository, entitygraph])
- bug-n-plus-one: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, how-it-works, what-changed])
- bug-regex-redos: title coverage 75% (expected keywords [fix, redos, urlvalidator, regex])
- bug-regex-redos: facts recall 50% (expected [linear pattern, longInputFinishesUnder100ms], got [UrlValidator.java now uses `Pattern.compile("^https?://[^\\s]+$")` instead of a nested-quantifier regex., UrlValidatorTest#longInputFinishesUnder100ms passes after the change.])
- bug-file-encoding: validation rejected the observation: no verifiable facts after evidence check
- refactor-invoice: title coverage 67% (expected keywords [refactor, invoiceservice, delegates])
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: type 'feature' != expected 'refactor'
- refactor-retry-policy: title coverage 67% (expected keywords [refactor, retrypolicy, backoffstrategy])
- refactor-sql-repository: title coverage 33% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [UserRepository.java now declares `@Query("select u from User u where u.tenantId = :tenantId")` instead of string-concatenated SQL., UserRepositoryTest returns the same tenant rows after the change, confirming query behavior is preserved.])
- refactor-sql-repository: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, what-changed, security, how-it-works])
- refactor-feature-flag: title coverage 67% (expected keywords [refactor, featureflagservice, delegates])
- test-unit-retry: title coverage 75% (expected keywords [add, retrypolicy, parameterized, test])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest.java created at src/test/java/com/acme/resilience/RetryPolicyTest.java, RetryPolicyTest#delayDoubles is annotated with @ParameterizedTest and @CsvSource({"1,100","2,200","3,400"}), The test asserts policy.delay(attempt) returns 100, 200, and 400 ms for attempts 1, 2, and 3 respectively, All 3 parameterized test cases pass after the file was written])
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable is a @ParameterizedTest using @ValueSource with three string values: "a,b", "a\"b", "a\nb"., The test covers the three characters requiring CSV escaping: comma, double-quote, and newline., All 3 parameterized cases pass, confirming stable quoting behavior for special characters., Test file located at src/test/java/com/acme/export/CsvExporterTest.java in the export module.])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, pattern])
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- security-redact-log: title coverage 33% (expected keywords [redact, credentials, logsanitizer])
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail previously constructed SQL using string concatenation with the email parameter, UserDao.findByEmail now uses jdbcTemplate.query with a parameterized query "select * from users where email = ?", SqlInjectionTest#emailWithQuoteReturnsEmpty test now passes after the fix, The email parameter is passed as a separate argument to jdbcTemplate.query instead of being interpolated into the SQL string])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: type 'feature' != expected 'bugfix'
- security-authz-admin: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, why-it-exists, security])
- security-csrf: title coverage 67% (expected keywords [fix, csrf, csrffilter])
- security-csrf: type 'feature' != expected 'bugfix'
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter.java now contains `if (!csrfToken.matches(request)) { response.sendError(403); return; }` to reject requests with a mismatched CSRF token., CsrfFilterTest#postWithoutTokenIs403 passes, confirming a POST without a CSRF token returns HTTP 403.])
- security-csrf: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, problem-solution, how-it-works])
- security-secret-config: title coverage 75% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: facts recall 50% (expected [ACME_API_KEY, SecretScanTest], got [src/main/resources/application.yml previously contained the literal value `api-key: sk-live-123`., The api-key property now references `${ACME_API_KEY}` resolved from the environment.])
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-rate-limit: type 'feature' != expected 'bugfix'
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [what-changed, pattern, how-it-works])
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- config-hikari-pool: title lost identifiers [maximum-pool-size] (got 'Configured HikariCP connection pool in application.yml')
- config-hikari-pool: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-flyway-baseline: title lost identifiers [pom.xml] (got 'Enabled Flyway baselineOnMigrate with baselineVersion 1')
- config-flyway-baseline: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, why-it-exists, gotcha])
- config-log-level: title coverage 75% (expected keywords [change, logback, root, json])
- config-log-level: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-cors: title coverage 50% (expected keywords [restrict, cors, corsconfig, origin])
- config-cors: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, why-it-exists, gotcha])
- config-timeout: title coverage 75% (expected keywords [set, webclient, timeout, 5])
- config-timeout: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern, gotcha])
- config-feature-flags: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- docs-api-observations: title lost identifiers [API.md, /api/observations] (got 'Documented pagination fields in docs/API.md')
- docs-api-observations: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- docs-architecture-adr: title lost identifiers [0007-extraction-validation.md] (got 'Added ADR 0007 extraction validation decision')
- docs-architecture-adr: type 'change' != expected 'decision'
- docs-architecture-adr: facts recall 50% (expected [validate, before persistence], got [ADR 0007 was created at docs/adr/0007-extraction-validation.md, Decision records validation of extracted facts against tool output before persistence, ADR includes description of the rejection path for invalid facts])
- docs-architecture-adr: concepts recall 50% (expected [why-it-exists, trade-off], got [why-it-exists, pattern, gotcha])
- docs-migration-guide: title coverage 75% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: facts recall 50% (expected [mem_sessions, app.manual_sentinel_reviewed], got [docs/MIGRATION.md now includes a V20 section., V20 migration must be run with app.manual_sentinel_reviewed=true set.])
- docs-security-policy: facts recall 50% (expected [security@acme.com, public issues], got [SECURITY.md explicitly forbids opening public issues for vulnerabilities.])
- docs-contributing: title coverage 33% (expected keywords [update, contributing, verify-local])
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern, gotcha])
- deploy-github-actions: title coverage 75% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-terraform-iam: title coverage 50% (expected keywords [restrict, iam, terraform, s3])
- deploy-terraform-iam: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, why-it-exists, pattern])
- deploy-helm-values: title coverage 25% (expected keywords [change, helm, replicas, memory])
- deploy-helm-values: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 50% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: facts recall 50% (expected [10 percent, 2 percent], got [infra/canary.yaml now contains `canary: { enabled: true, weight: 10, errorThreshold: 0.02 }`, Canary weight is set to 10 percent, helm lint passes on the updated canary.yaml])
- deploy-canary: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, pattern])

## Raw answers behind problems

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone reproducing off-by-one-day bug</title>   <subtitle>Regression test pins 2026-11-01T23:30Z and expects Asia/Shanghai business date 2026-11-02.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone was added to src/test/java/com/acme/reporting/DailyReportTest.java.</fact>     <fact>The ... [truncated]
```

### bug-connection-leak

```text
<observation>   <type>bugfix</type>   <title>Fixed connection leak in ConnectionPool.borrow with try-with-resources</title>   <subtitle>ConnectionPool.borrow now closes the borrowed connection when the caller throws an exception.</subtitle>   <facts>     <fact>ConnectionPool.borrow in src/main/java/com/acme/db/ConnectionPool.java now uses `try (Connection connection = dataSource.getConnection())` ... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JWT expiration boundary in JwtVerifier</title>   <subtitle>JwtVerifier now rejects tokens whose expiration equals the current instant instead of accepting them.</subtitle>   <facts>     <fact>JwtVerifier expiration check changed from `claims.getExpiration().before(now)` to `claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed multiline field line ending normalization in CsvExporter</title>   <subtitle>CsvExporter now normalizes embedded LF/CRLF to CRLF before quoting so multiline fields keep row count.</subtitle>   <facts>     <fact>CsvExporter.java writes each line via `writer.write(line.replaceAll("\\r?\\n", "\\r\\n"))` before quoting.</fact>     <fact>The regex `\r?... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed race condition in CounterService.increment with AtomicLong.incrementAndGet</title>   <subtitle>Replaced non-atomic get/set read-modify-write on counter with single atomic incrementAndGet call.</subtitle>   <facts>     <fact>CounterService.increment changed from `counter.set(counter.get() + 1); return counter.get();` to `return counter.incrementAnd... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>bugfix</type>   <title>Fixed N+1 query in OrderRepository.findAllByCustomerId</title>   <subtitle>EntityGraph now eagerly fetches order items in a single query instead of triggering N additional queries.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId now annotated with `@EntityGraph(attributePaths = "items")` to fetch order items in the same query</fact>  ... [truncated]
```

### bug-regex-redos

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed catastrophic backtracking in UrlValidator regex</title>   <subtitle>Replaced nested-quantifier regex with linear pattern to prevent ReDoS on long input.</subtitle>   <facts>     <fact>UrlValidator.java now uses `Pattern.compile("^https?://[^\\s]+$")` instead of a nested-quantifier regex.</fact>     <fact>UrlValidatorTest#longInputFinishesUn... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed charset handling in FileReaderFactory to UTF-8</title>   <subtitle>FileReaderFactory now always decodes streams as UTF-8 instead of the platform default charset.</subtitle>   <facts>     <fact>FileReaderFactory opens InputStreams with `new InputStreamReader(in, StandardCharsets.UTF_8)`.     <fact>Before the change, FileReaderFactory used the platf... [truncated]
```

### refactor-invoice

```text
<observation>   <type>refactor</type>   <title>Refactored InvoiceService to delegate to TaxCalculator, InvoiceRenderer and MailGateway</title>   <subtitle>InvoiceService now delegates tax calculation, rendering and mailing to three collaborators with no behaviour change.</subtitle>   <facts>     <fact>InvoiceService.java was edited so it calls `renderer.render(taxCalculator.calculate(invoice), inv... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>feature</type>   <title>Added unmodifiable map and typed accessors to ConfigLoader</title>   <subtitle>ConfigLoader now returns an unmodifiable map of properties and exposes typed accessors, with ConfigLoaderTest still passing.</subtitle>   <facts>     <fact>ConfigLoader.java was changed so its return path is properties.entrySet().stream().collect(toUnmodifiableMap(...)).</fa... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Extracted delay calculation from RetryPolicy into BackoffStrategy</title>   <subtitle>RetryPolicy now delegates delay computation to BackoffStrategy, preserving the 100/200/400 retry delay sequence.</subtitle>   <facts>     <fact>RetryPolicy.java in src/main/java/com/acme/resilience was edited to return `backoff.nextDelay(attempt)`. </fact>     <fact>... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Hardened UserRepository tenant query against SQL injection</title>   <subtitle>Replaced string-concatenated SQL in UserRepository with a parameterized JPQL @Query bound to tenantId.</subtitle>   <facts>     <fact>UserRepository.java now declares `@Query("select u from User u where u.tenantId = :tenantId")` instead of string-concatenated SQL.</fact>     ... [truncated]
```

### refactor-feature-flag

```text
```xml <observation>   <type>refactor</type>   <title>Refactored FeatureFlagService evaluation to delegate to Flags.isEnabled</title>   <subtitle>FeatureFlagService now delegates flag evaluation to Flags.isEnabled while preserving existing test matrix.</subtitle>   <facts>     <fact>src/main/java/com/acme/flags/FeatureFlagService.java now contains `return flags.isEnabled(flag, context)` in the eva... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles for exponential backoff verification</title>   <subtitle>New parameterized test pins RetryPolicy.delay to expected doubling values across attempts 1-3.</subtitle>   <facts>     <fact>RetryPolicyTest.java created at src/test/java/com/acme/resilience/RetryPolicyTest.java</fact>     <fact>RetryPolicyTest#delayDoubles is ... [truncated]
```

### test-parameterized-csv

```text
```xml <observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable parameterized CSV quoting test</title>   <subtitle>New parameterized test verifies CSV quoting handles commas, quotes, and newlines correctly.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable is a @ParameterizedTest using @ValueSource with three string values: "a,b", "a\"b", "a\nb".</fact>     ... [truncated]
```

### test-repro-null-cache

```text
```xml <observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull for UserCache null</title>   <subtitle>Test reproduces intermittent null from UserCache with 1000 parallel readers.</subtitle>   <facts>     <fact>Test file `src/test/java/com/acme/cache/CacheConcurrencyTest.java` was created.</fact>     <fact>Test method `CacheConcurrencyTest#concurrentRe... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Deflaked SchedulerTest with Clock.fixed instead of Thread.sleep</title>   <subtitle>Replaced Thread.sleep in SchedulerTest with a fixed Clock to eliminate timing flakiness; 20 consecutive runs pass.</subtitle>   <facts>     <fact>SchedulerTest.java in com.acme.scheduling previously relied on Thread.sleep for time-dependent assertions.</fact>     <fact>... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed credential leakage in LogSanitizer</title>   <subtitle>LogSanitizer now redacts password, token, and secret values before they reach application logs.</subtitle>   <facts>     <fact>src/main/java/com/acme/security/LogSanitizer.java now calls value.replaceAll("(password|token|secret)=\\S+", "$1=***")</fact>     <fact>LogSanitizerTest#redactsCredent... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed SQL injection in UserDao.findByEmail</title>   <subtitle>Replaced string concatenation with parameterized query placeholder in findByEmail method.</subtitle>   <facts>     <fact>UserDao.findByEmail previously constructed SQL using string concatenation with the email parameter</fact>     <fact>UserDao.findByEmail now uses jdbcTemplate.query with a ... [truncated]
```

### security-authz-admin

```text
<observation>   <type>feature</type>   <title>Added ROLE_ADMIN authorization to AdminController</title>   <subtitle>Every endpoint in AdminController now requires ROLE_ADMIN via @PreAuthorize.</subtitle>   <facts>     <fact>AdminController.java now has @PreAuthorize("hasRole('ADMIN')") applied at class level</fact>     <fact>AdminAuthorizationTest#userRoleGets403 passes, confirming users without A... [truncated]
```

### security-csrf

```text
<observation>   <type>feature</type>   <title>Added CSRF token validation in CsrfFilter</title>   <subtitle>CsrfFilter now rejects state-changing requests with a mismatched or missing CSRF token via HTTP 403.</subtitle>   <facts>     <fact>CsrfFilter.java now contains `if (!csrfToken.matches(request)) { response.sendError(403); return; }` to reject requests with a mismatched CSRF token.</fact>    ... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Fixed hardcoded API key in application.yml to use ACME_API_KEY env var</title>   <subtitle>Removed literal secret sk-live-123 from config; now reads from environment variable.</subtitle>   <facts>     <fact>src/main/resources/application.yml previously contained the literal value `api-key: sk-live-123`.</fact>     <fact>The api-key property now referenc... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Fixed XXE vulnerability in XmlImportService by disallowing DOCTYPE declarations</title>   <subtitle>XmlImportService now rejects DOCTYPE declarations on the XML parser factory, blocking external entity expansion.</subtitle>   <facts>     <fact>XmlImportService calls factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) on its ... [truncated]
```

### security-rate-limit

```text
<observation>   <type>feature</type>   <title>Added token bucket enforcement in RateLimiter</title>   <subtitle>RateLimiter now throws TooManyRequestsException (HTTP 429) when the token bucket is exhausted.</subtitle>   <facts>     <fact>RateLimiter.java in src/main/java/com/acme/web/RateLimiter.java now contains the check `if (!bucket.tryConsume(1)) { throw new TooManyRequestsException(); }`.</fa... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>Session cookies now set HttpOnly, Secure, and SameSite=Strict to mitigate XSS, MITM, and CSRF risks.</subtitle>   <facts>     <fact>SessionCookieConfig.java was edited to call cookie.setHttpOnly(true), cookie.setSecure(true), and cookie.setSameSite(Strict) on the session cookie.</f... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Configured HikariCP connection pool in application.yml</title>   <subtitle>Added maximum-pool-size and minimum-idle settings to Spring datasource Hikari configuration.</subtitle>   <facts>     <fact>application.yml sets spring.datasource.hikari.maximum-pool-size to 20</fact>     <fact>application.yml sets spring.datasource.hikari.minimum-idle to 5</fact... [truncated]
```

### config-flyway-baseline

```text
```xml <observation>   <type>change</type>   <title>Enabled Flyway baselineOnMigrate with baselineVersion 1</title>   <subtitle>Configured Flyway to baseline existing databases at version 1 on first migration run.</subtitle>   <facts>     <fact>pom.xml now sets `flyway.baselineOnMigrate` to `true`</fact>     <fact>pom.xml now sets `flyway.baselineVersion` to `1`</fact>     <fact>Maven build resolv... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Switched logback-spring.xml root logger to INFO with JSON appender</title>   <subtitle>Root logger level changed to INFO and bound to JSON appender in logback-spring.xml.</subtitle>   <facts>     <fact>src/main/resources/logback-spring.xml root element now has level="INFO".</fact>     <fact>Root logger in logback-spring.xml references the JSON appender ... [truncated]
```

### config-cors

```text
<observation>   <type>change</type>   <title>Hardened CORS policy in CorsConfig for /api/**</title>   <subtitle>CORS registry restricted to https://app.acme.com with GET and POST methods only.</subtitle>   <facts>     <fact>CorsConfig.java registry.addMapping("/api/**") now sets allowedOrigins("https://app.acme.com")</fact>     <fact>CorsConfig.java allowedMethods restricted to "GET" and "POST"</f... [truncated]
```

### config-timeout

```text
<observation>   <type>change</type>   <title>Added 5 second response timeout to WebClientConfig</title>   <subtitle>WebClientConfig now configures the HTTP client with a 5 second response timeout.</subtitle>   <facts>     <fact>WebClientConfig sets HttpClient responseTimeout to 5 seconds via HttpClient.create().responseTimeout(Duration.ofSeconds(5)).</fact>     <fact>WebClientConfigTest reads the ... [truncated]
```

### config-feature-flags

```text
```xml <observation>   <type>change</type>   <title>Enabled checkout-v2 and disabled legacy-search in feature-flags.yml</title>   <subtitle>Updated feature flag configuration and verified bindings via FlagBindingTest.</subtitle>   <facts>     <fact>src/main/resources/feature-flags.yml now sets checkout-v2: true</fact>     <fact>src/main/resources/feature-flags.yml now sets legacy-search: false</fa... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added HEALTHCHECK to Dockerfile for /actuator/health</title>   <subtitle>Dockerfile now includes a 30-second health check against the Spring Boot actuator endpoint.</subtitle>   <facts>     <fact>Dockerfile contains HEALTHCHECK directive with --interval=30s</fact>     <fact>Health check uses curl -f against http://localhost:8080/actuator/health</fact>  ... [truncated]
```

### config-maven-plugin

```text
```xml <observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 to pom.xml</title>   <subtitle>Build now generates JaCoCo code coverage report at target/site/jacoco/jacoco.xml during mvn verify.</subtitle>   <facts>     <fact>pom.xml now declares org.jacoco:jacoco-maven-plugin version 0.8.12</fact>     <fact>mvn verify produces coverage output at target/site/jacoco/jacoco.xml<... [truncated]
```

### docs-api-observations

```text
```xml <observation>   <type>change</type>   <title>Documented pagination fields in docs/API.md</title>   <subtitle>Added API documentation for /api/observations pagination fields hasMore and items.</subtitle>   <facts>     <fact>docs/API.md now documents that GET /api/observations returns hasMore and items fields.</fact>     <fact>The documented pagination contract matches the controller implemen... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Added Setup section to README.md</title>   <subtitle>README.md now documents a three-step local setup using JDK 21, Docker Compose Postgres, and Maven Spring Boot run.</subtitle>   <facts>     <fact>README.md now contains a "## Setup" section with three numbered steps.</fact>     <fact>Setup step 1 instructs installing JDK 21.</fact>     <fact>Setup ste... [truncated]
```

### docs-changelog

```text
```xml <observation>   <type>change</type>   <title>Documented 2.4.0 release notes in CHANGELOG.md</title>   <subtitle>Changelog entry for 2.4.0 records offline replay evaluation and V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md gained a "2.4.0 - 2026-11-03" entry dated 2026-11-03.</fact>     <fact>The 2.4.0 entry lists "Added offline replay evaluation" as a new feature.<... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>change</type>   <title>Added ADR 0007 extraction validation decision</title>   <subtitle>Documented decision to validate extracted facts against tool output before persistence with rejection path.</subtitle>   <facts>     <fact>ADR 0007 was created at docs/adr/0007-extraction-validation.md</fact>     <fact>Decision records validation of extracted facts against tool output bef... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisite in docs/MIGRATION.md</title>   <subtitle>Added V20 section requiring mem_sessions backup and manual sentinel review flag before migration.</subtitle>   <facts>     <fact>docs/MIGRATION.md now includes a V20 section.</fact>     <fact>V20 migration requires backing up the mem_sessions table before running.</fact>     ... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Added SECURITY.md vulnerability disclosure policy</title>   <subtitle>SECURITY.md defines the security disclosure channel and prohibits public vulnerability reports.</subtitle>   <facts>     <fact>SECURITY.md was created in the repository root directory.</fact>     <fact>SECURITY.md directs vulnerability reports to the email address security@acme.com.</... [truncated]
```

### docs-contributing

```text
```xml <observation>   <type>change</type>   <title>Documented pre-PR verification requirement in CONTRIBUTING.md</title>   <subtitle>CONTRIBUTING.md now mandates running ./scripts/verify-local.sh before opening a pull request and names the four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md requires running ./scripts/verify-local.sh before opening a pull request.</fact>     <fact>The... [truncated]
```

### deploy-k8s-probe

```text
```xml <observation>   <type>change</type>   <title>Added readinessProbe to deployment.yaml on /api/health:37777</title>   <subtitle>Deployment now defines a Kubernetes readiness probe to gate traffic on application health.</subtitle>   <facts>     <fact>deploy/deployment.yaml includes a readinessProbe using httpGet on path /api/health and port 37777.</fact>     <fact>The readinessProbe sets initi... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Changed JDK matrix to 21 and 25 in ci.yml</title>   <subtitle>CI workflow now runs the backend test suite against JDK 21 and JDK 25.</subtitle>   <facts>     <fact>.github/workflows/ci.yml sets strategy.matrix.java to [21, 25].</fact>     <fact>Workflow syntax check passed after the matrix edit.</fact>     <fact>The matrix change targets the backend bui... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted deploy role to AmazonS3ReadOnlyAccess in main.tf</title>   <subtitle>Replaced full S3 access with read-only S3 policy on the deploy IAM role to follow least privilege.</subtitle>   <facts>     <fact>aws_iam_role_policy "deploy" in infra/main.tf now attaches arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess.</fact>     <fact>The deploy role previ... [truncated]
```

### deploy-helm-values

```text
```xml <observation>   <type>change</type>   <title>Raised replicaCount to 3 and capped memory at 2Gi in values.yaml</title>   <subtitle>Deployment values increased from previous replica count to 3 with a 2Gi memory limit per pod.</subtitle>   <facts>     <fact>`infra/values.yaml` sets `replicaCount: 3` to scale the deployment to three pods.</fact>     <fact>`infra/values.yaml` adds `resources.lim... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added rollback.sh script for claude-mem deployment</title>   <subtitle>Created scripts/rollback.sh to undo the claude-mem deployment and wait for rollout status.</subtitle>   <facts>     <fact>scripts/rollback.sh executes `kubectl rollout undo deployment/claude-mem` followed by `kubectl rollout status deployment/claude-mem`.</fact>     <fact>scripts/ro... [truncated]
```

### deploy-canary

```text
```xml <observation>   <type>change</type>   <title>Enabled canary deployment in infra/canary.yaml</title>   <subtitle>Canary deployment configured at 10% weight with 2% error rate rollback threshold.</subtitle>   <facts>     <fact>infra/canary.yaml now contains `canary: { enabled: true, weight: 10, errorThreshold: 0.02 }`</fact>     <fact>Canary weight is set to 10 percent</fact>     <fact>Error ... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title hit requires every declared identifier verbatim and keyword coverage ≥ 0.67; keywords are normalized for case, punctuation, CamelCase and a small synonym table.
- Facts/concepts recall is the primary field signal; precision counts produced items that mention an expected keyword. Trace-support rate is measured independently against the recorded tool input/output.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
