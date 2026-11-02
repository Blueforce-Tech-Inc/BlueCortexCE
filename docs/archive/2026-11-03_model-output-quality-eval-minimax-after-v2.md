# Model output quality evaluation

- Date: 2026-11-03
- Phase: after-v2
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 249728 ms; temperature: 0.0
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
| title exact-hit rate (legacy) | 40% |
| title normalized hit rate | 66% |
| title identifier hit rate | 84% |
| type accuracy | 95% |
| facts precision / recall | 75% / 93% |
| facts trace-support rate | 100% |
| concepts precision / recall | 55% / 73% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 1867 / 4662 / 10095 |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | facts P | facts R | format errors |
|---|---|---|---|---|---|---|
| 0 | 82 | 100% | 66% | 75% | 93% | 0 |
| 1 | 13 | 100% | 70% | 88% | 100% | 0 |
| 2 | 13 | 100% | 70% | 87% | 93% | 0 |

- average: skip 100%, title 69%, facts P/R 83%/95%
- worst run: skip 100%, title 66%, facts P/R 75%/93%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 12/12 | 10/12 |
| refactor | 9 | 9/9 | 8/9 | 9/9 |
| test-add | 9 | 9/9 | 9/9 | 7/9 |
| security-fix | 9 | 9/9 | 8/9 | 8/9 |
| config-change | 8 | 8/8 | 8/8 | 4/8 |
| docs-update | 8 | 8/8 | 8/8 | 3/8 |
| deploy-change | 7 | 7/7 | 6/7 | 0/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title exact | type | facts P/R | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 67% | miss | match | 100%/100% | 67%/100% | 2142 |
| bug-null-cache | bug-fix | observation | observation | pass | 50% | miss | match | 100%/100% | 67%/100% | 4465 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | match | 33%/100% | 33%/50% | 4662 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 1781 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 2288 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | miss | match | 100%/67% | 67%/100% | 2815 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 50% | miss | match | 67%/100% | 67%/100% | 1987 |
| bug-race-counter | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 33%/50% | 2878 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 75% | miss | match | 67%/100% | 33%/50% | 6160 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 67% | miss | match | 100%/100% | 67%/100% | 1565 |
| bug-regex-redos | bug-fix | observation | observation | pass | 75% | miss | match | 67%/100% | 67%/100% | 1879 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | miss | match | 100%/100% | 67%/100% | 4533 |
| refactor-invoice | refactor | observation | observation | pass | 67% | miss | match | 100%/100% | 67%/100% | 1804 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | match | 33%/50% | 100%/100% | 1867 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | miss | MISS | 67%/100% | 33%/50% | 2004 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 2759 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | match | 50%/50% | 67%/100% | 1641 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 1867 |
| refactor-sql-repository | refactor | observation | observation | pass | 100% | hit | match | 100%/50% | 67%/100% | 3326 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | match | 33%/100% | 67%/100% | 2804 |
| refactor-feature-flag | refactor | observation | observation | pass | 67% | miss | match | 100%/100% | 67%/100% | 4858 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | match | 100%/100% | 33%/50% | 3884 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 2045 |
| test-unit-retry | test-add | observation | observation | pass | 75% | miss | match | 100%/50% | 50%/50% | 2030 |
| test-integration-order | test-add | observation | observation | pass | 50% | miss | match | 50%/100% | 50%/50% | 2578 |
| test-parameterized-csv | test-add | observation | observation | pass | 75% | miss | match | 100%/100% | 50%/50% | 2421 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 6130 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 3574 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | match | 33%/100% | 67%/100% | 3910 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | miss | match | 100%/100% | 67%/100% | 2315 |
| security-redact-log | security-fix | observation | observation | pass | 67% | miss | match | 100%/100% | 33%/50% | 3024 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | hit | match | 33%/50% | 67%/100% | 1870 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | miss | match | 67%/50% | 33%/50% | 10095 |
| security-csrf | security-fix | observation | observation | pass | 100% | hit | match | 50%/50% | 33%/50% | 2417 |
| security-secret-config | security-fix | observation | observation | pass | 50% | miss | match | 67%/100% | 67%/100% | 1843 |
| security-xxe | security-fix | observation | observation | pass | 67% | miss | match | 100%/100% | 67%/100% | 2767 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 1800 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | miss | match | 33%/100% | 33%/50% | 1822 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | miss | MISS | 50%/100% | 33%/50% | 1902 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | miss | match | 100%/100% | 50%/50% | 2435 |
| config-flyway-baseline | config-change | observation | observation | pass | 50% | miss | match | 100%/100% | 33%/50% | 1514 |
| config-log-level | config-change | observation | observation | pass | 75% | miss | match | 33%/50% | 50%/50% | 1504 |
| config-cors | config-change | observation | observation | pass | 100% | hit | match | 67%/100% | 50%/50% | 1825 |
| config-timeout | config-change | observation | observation | pass | 75% | miss | match | 50%/100% | 50%/50% | 1703 |
| config-feature-flags | config-change | observation | observation | pass | 50% | miss | match | 100%/100% | 50%/50% | 1608 |
| config-docker-healthcheck | config-change | observation | observation | pass | 75% | miss | match | 100%/100% | 33%/50% | 2637 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 1590 |
| docs-api-observations | docs-update | observation | observation | pass | 75% | hit | match | 50%/100% | 50%/50% | 1469 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | miss | match | 100%/100% | 50%/50% | 1564 |
| docs-runbook | docs-update | observation | observation | pass | 100% | hit | match | 67%/100% | 50%/50% | 1279 |
| docs-changelog | docs-update | observation | observation | pass | 67% | miss | match | 67%/100% | 50%/50% | 2460 |
| docs-architecture-adr | docs-update | observation | observation | pass | 75% | miss | match | 50%/100% | 67%/100% | 2407 |
| docs-migration-guide | docs-update | observation | observation | pass | 50% | miss | match | 67%/100% | 67%/100% | 2881 |
| docs-security-policy | docs-update | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 2817 |
| docs-contributing | docs-update | observation | observation | pass | 33% | miss | match | 100%/100% | 67%/100% | 1411 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | hit | match | 75%/100% | 50%/50% | 1623 |
| deploy-github-actions | deploy-change | observation | observation | pass | 50% | miss | match | 50%/100% | 50%/50% | 4577 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 50% | miss | match | 33%/100% | 67%/100% | 1873 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 50% | miss | match | 100%/100% | 33%/50% | 1586 |
| deploy-helm-values | deploy-change | observation | observation | pass | 50% | miss | match | 67%/100% | 50%/50% | 1445 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | MISS | 67%/100% | 67%/100% | 1606 |
| deploy-canary | deploy-change | observation | observation | pass | 25% | miss | match | 50%/100% | 33%/50% | 1927 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | 831 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | 3806 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | 1733 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | 726 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | 1002 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | 1111 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | 936 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | 908 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | 1219 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | 1912 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | 925 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | 1038 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | 787 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | 716 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | 1651 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | 1123 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | 1345 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | 1038 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | 779 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | 740 |

## Problems

- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed UserCache.get concurrent null-placeholder writes')
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha, pattern])
- bug-jwt-expiry: facts recall 67% (expected [expiration, isEqual, rejects], got [JwtVerifier checks claims.getExpiration() with isBefore(now) or isEqual(now)., JwtVerifierTest#expiredAtExactBoundary passes after the expiration check change.])
- bug-csv-crlf: title coverage 50% (expected keywords [fix, csv, line, endings])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, how-it-works, gotcha])
- bug-n-plus-one: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, how-it-works, what-changed])
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [src/main/java/com/acme/auth/AuthController.java now calls tokenService.issue(authentication)., AuthController no longer builds JWTs inline., AuthControllerTest remains unchanged and green.])
- refactor-config-loader: type 'change' != expected 'refactor'
- refactor-config-loader: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works, problem-solution])
- refactor-retry-policy: facts recall 50% (expected [BackoffStrategy, 100/200/400], got [RetryPolicy returns delay values through `backoff.nextDelay(attempt)`., RetryPolicyTest asserts the 100/200/400 backoff sequence.])
- refactor-logging-filter: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [UserRepository.java replaces string-concatenated SQL with `@Query("select u from User u where u.tenantId = :tenantId")`., UserRepository.java binds the tenant identifier through the `:tenantId` query parameter., UserRepositoryTest returns the same tenant rows after the UserRepository query change.])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha, pattern])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles covers attempts 1, 2, and 3 with expected delays 100, 200, and 400 ms., RetryPolicyTest#delayDoubles uses a parameterized test with @CsvSource., RetryPolicyTest#delayDoubles passes all 3 test cases.])
- test-unit-retry: concepts recall 50% (expected [what-changed, pattern], got [what-changed, problem-solution])
- test-integration-order: title coverage 50% (expected keywords [add, integration, order, test])
- test-integration-order: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- test-parameterized-csv: concepts recall 50% (expected [what-changed, pattern], got [what-changed, problem-solution])
- test-security-headers: concepts recall 50% (expected [what-changed, pattern], got [what-changed, problem-solution])
- test-repro-null-cache: title lost identifiers [CacheConcurrencyTest, UserCache] (got 'Added CacheConcurrencyTest#concurrentReadersNeverSeeNull for concurrent cache reads')
- security-redact-log: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, how-it-works])
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail executes `jdbcTemplate.query("select * from users where email = ?", mapper, email)`., UserDao.findByEmail no longer concatenates the `email` value into the SQL statement., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the UserDao.java change.])
- security-authz-admin: facts recall 50% (expected [ROLE_ADMIN, 403], got [src/main/java/com/acme/admin/AdminController.java uses @PreAuthorize("hasRole('ADMIN')")., AdminAuthorizationTest#userRoleGets403 passes after the AdminController authorization change., AdminController endpoints now require ROLE_ADMIN, preventing access by user-role accounts.])
- security-authz-admin: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, how-it-works])
- security-csrf: facts recall 50% (expected [403, mismatched token], got [src/main/java/com/acme/web/CsrfFilter.java sends HTTP 403 when csrfToken does not match request., CsrfFilterTest#postWithoutTokenIs403 passes after the CsrfFilter validation change.])
- security-csrf: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, how-it-works])
- security-secret-config: title lost identifiers [application.yml, ACME_API_KEY] (got 'Fixed exposed API key in application.yml')
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, what-changed, how-it-works])
- security-cookie-httponly: type 'change' != expected 'bugfix'
- security-cookie-httponly: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, problem-solution, pattern])
- config-hikari-pool: title lost identifiers [maximum-pool-size] (got 'Changed Hikari pool sizing in application.yml')
- config-hikari-pool: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-flyway-baseline: title coverage 50% (expected keywords [enable, flyway, baseline, migrate])
- config-flyway-baseline: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, gotcha])
- config-log-level: title lost identifiers [logback-spring.xml] (got 'Changed root logger to INFO with JSON appender')
- config-log-level: facts recall 50% (expected [INFO, JSON appender], got [src/main/resources/logback-spring.xml configures the root logger with level="INFO"., src/main/resources/logback-spring.xml configures the root logger with appender-ref ref="JSON"., LogbackConfigTest parses the updated logback-spring.xml.])
- config-log-level: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-cors: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, problem-solution])
- config-timeout: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-feature-flags: title coverage 50% (expected keywords [enable, checkout-v2, disable, legacy-search])
- config-feature-flags: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, problem-solution])
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- docs-api-observations: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-runbook: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-changelog: title lost identifiers [CHANGELOG.md] (got 'Added 2.4.0 changelog entry for offline replay and V20 migration')
- docs-changelog: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- docs-architecture-adr: title lost identifiers [0007-extraction-validation.md] (got 'Added ADR 0007 extraction validation decision')
- docs-migration-guide: title coverage 50% (expected keywords [document, v20, migration, sentinel])
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, why-it-exists])
- docs-contributing: title coverage 33% (expected keywords [update, contributing, verify-local])
- deploy-k8s-probe: title lost identifiers [deployment.yaml, /api/health] (got 'Added readinessProbe to deployment.yaml')
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- deploy-github-actions: title coverage 50% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- deploy-docker-multistage: title coverage 50% (expected keywords [convert, dockerfile, multi-stage, jre])
- deploy-terraform-iam: title lost identifiers [main.tf] (got 'Changed deploy IAM role to AmazonS3ReadOnlyAccess')
- deploy-terraform-iam: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, problem-solution, why-it-exists])
- deploy-helm-values: title coverage 50% (expected keywords [change, helm, replicas, memory])
- deploy-helm-values: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- deploy-rollback-script: title lost identifiers [rollback.sh] (got 'Added rollback script for deployment/claude-mem')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 25% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, problem-solution, gotcha])

## Raw answers behind problems

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache.get concurrent null-placeholder writes</title>   <subtitle>UserCache.get now uses computeIfAbsent so concurrent callers share one cache load.</subtitle>   <facts>     <fact>UserCache.get changed from return cache.get(id); to return cache.computeIfAbsent(id, loader::load);.</fact>     <fact>UserCache.get prevents two concurrent callers fr... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai</title>   <subtitle>Regression test pins UTC timestamp conversion to the expected Asia/Shanghai business date.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone was added to src/test/java/com/acme/reporting/DailyReportTest.java.</fact>     <fact>DailyReportTest#r... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration boundary check</title>   <subtitle>JwtVerifier now rejects tokens expiring exactly at the current instant.</subtitle>   <facts>     <fact>JwtVerifier checks claims.getExpiration() with isBefore(now) or isEqual(now).</fact>     <fact>JwtVerifierTest#expiredAtExactBoundary passes after the expiration check change.</fact>   </f... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed CSV multiline row counting in CsvExporter</title>   <subtitle>CsvExporter now normalises embedded line endings to CRLF before quoting to preserve CSV row structure.</subtitle>   <facts>     <fact>CsvExporter.java writes fields using writer.write(line.replaceAll("\\r?\\n", "\r\n")).</fact>     <fact>CsvExporterTest#multilineFieldKeepsRowCount passe... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment race with AtomicLong.incrementAndGet</title>   <subtitle>Concurrent increments now preserve every update, with 1,000 threads producing 1,000.</subtitle>   <facts>     <fact>CounterService.increment replaced counter.set(counter.get() + 1) with counter.incrementAndGet().</fact>     <fact>CounterServiceConcurrencyTest passes ... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>bugfix</type>   <title>Fixed N+1 item loading in OrderRepository.findAllByCustomerId</title>   <subtitle>OrderRepository now fetches associated items with one SQL statement instead of separate item queries.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId(String customerId) is annotated with @EntityGraph(attributePaths = "items").</fact>     <fact>QueryCount... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored AuthController JWT issuance through TokenService.issue</title>   <subtitle>AuthController delegates token creation while AuthControllerTest remains green.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/AuthController.java now calls tokenService.issue(authentication).</fact>     <fact>AuthController no longer builds JWTs inline.<... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>change</type>   <title>Changed ConfigLoader to return an unmodifiable map</title>   <subtitle>ConfigLoader now exposes typed accessors backed by an immutable properties map.</subtitle>   <facts>     <fact>ConfigLoader returns the result of properties.entrySet().stream().collect(toUnmodifiableMap(...)).</fact>     <fact>ConfigLoader exposes typed accessors for configuration va... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Refactored RetryPolicy delay calculation into BackoffStrategy</title>   <subtitle>RetryPolicy now delegates backoff delay computation while preserving the tested delay sequence.</subtitle>   <facts>     <fact>RetryPolicy returns delay values through `backoff.nextDelay(attempt)`.</fact>     <fact>RetryPolicyTest asserts the 100/200/400 backoff sequence... [truncated]
```

### refactor-logging-filter

```text
<observation>   <type>refactor</type>   <title>Refactored LoggingFilter access-log timing into logExchange</title>   <subtitle>LoggingFilter now delegates exchange timing and header capture to logExchange while preserving output.</subtitle>   <facts>     <fact>LoggingFilter.java calls logExchange(exchange, StopWatch.createStarted()).</fact>     <fact>LoggingFilterTest verifies the same access-log ... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>refactor</type>   <title>Refactored UserRepository tenant query to use JPQL @Query</title>   <subtitle>Tenant filtering now uses a parameterized JPQL query while preserving existing test results.</subtitle>   <facts>     <fact>UserRepository.java replaces string-concatenated SQL with `@Query("select u from User u where u.tenantId = :tenantId")`.</fact>     <fact>UserRepositor... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai date handling</title>   <subtitle>Regression test reproduces the UTC-versus-business-date bug with a 23:30 UTC instant.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone expects report.dateAt(2026-11-01T23:30:00Z, Asia/Shanghai) to equal 2026-11-02.</fact>     <f... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles for exponential retry delays</title>   <subtitle>Parameterized coverage verifies retry delays of 100, 200, and 400 milliseconds.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles covers attempts 1, 2, and 3 with expected delays 100, 200, and 400 ms.</fact>     <fact>RetryPolicyTest#delayDoubles uses a parame... [truncated]
```

### test-integration-order

```text
<observation>   <type>feature</type>   <title>Added OrderApiIT#createOrderReturns201 for order creation</title>   <subtitle>Integration test verifies successful order creation and its Location header using PostgreSQL 16 Testcontainers.</subtitle>   <facts>     <fact>src/test/java/com/acme/orders/OrderApiIT.java contains OrderApiIT#createOrderReturns201.</fact>     <fact>OrderApiIT#createOrderRetur... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable for CSV special characters</title>   <subtitle>Parameterized coverage verifies stable quoting for commas, embedded quotes, and newlines.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable uses @ParameterizedTest with @ValueSource(strings = {"a,b","a\"b","a\nb"}).</fact>     <fact>CsvExporterTest#quoting... [truncated]
```

### test-security-headers

```text
<observation>   <type>feature</type>   <title>Added SecurityHeadersTest#responseHasCspAndNosniff</title>   <subtitle>Security header tests verify CSP and nosniff behavior, with 2 tests passing.</subtitle>   <facts>     <fact>src/test/java/com/acme/web/SecurityHeadersTest.java defines SecurityHeadersTest#responseHasCspAndNosniff.</fact>     <fact>SecurityHeadersTest#responseHasCspAndNosniff asserts... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull for concurrent cache reads</title>   <subtitle>Regression test reproduces intermittent null reads with 1000 parallel UserCache readers.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull runs 1000 parallel cache.get("k") assertions.</fact>     <fact>CacheConcurrency... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed credential redaction in LogSanitizer</title>   <subtitle>LogSanitizer now masks password, token and secret values before logging.</subtitle>   <facts>     <fact>src/main/java/com/acme/security/LogSanitizer.java replaces password, token and secret values with ***.</fact>     <fact>LogSanitizerTest#redactsCredentials passes for password, token and s... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed SQL injection in UserDao.findByEmail</title>   <subtitle>UserDao.findByEmail now uses a JDBC parameter placeholder for email lookups.</subtitle>   <facts>     <fact>UserDao.findByEmail executes `jdbcTemplate.query("select * from users where email = ?", mapper, email)`.</fact>     <fact>UserDao.findByEmail no longer concatenates the `email` value i... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Fixed AdminController authorization with hasRole('ADMIN')</title>   <subtitle>AdminController now requires ROLE_ADMIN on every endpoint, and non-admin access returns 403.</subtitle>   <facts>     <fact>src/main/java/com/acme/admin/AdminController.java uses @PreAuthorize("hasRole('ADMIN')").</fact>     <fact>AdminAuthorizationTest#userRoleGets403 passes ... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Fixed CSRF token validation in CsrfFilter</title>   <subtitle>CsrfFilter now rejects state-changing requests when the supplied token does not match the expected token.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/CsrfFilter.java sends HTTP 403 when csrfToken does not match request.</fact>     <fact>CsrfFilterTest#postWithoutTokenIs403 passe... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Fixed exposed API key in application.yml</title>   <subtitle>application.yml now reads the API key from ACME_API_KEY, and SecretScanTest passes.</subtitle>   <facts>     <fact>src/main/resources/application.yml replaced the literal api-key value sk-live-123 with ${ACME_API_KEY}.</fact>     <fact>src/main/resources/application.yml no longer contains a li... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter empty-bucket response with TooManyRequestsException</title>   <subtitle>RateLimiter now returns HTTP 429 when the token bucket cannot consume another request.</subtitle>   <facts>     <fact>RateLimiter calls bucket.tryConsume(1) and throws TooManyRequestsException when consumption fails.</fact>     <fact>RateLimiterTest#eleventhRequest... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>change</type>   <title>Hardened SessionCookieConfig cookie flags</title>   <subtitle>Session cookies now use HttpOnly, Secure, and SameSite=Strict protections.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/SessionCookieConfig.java sets cookie.setHttpOnly(true), cookie.setSecure(true), and cookie.setSameSite(Strict).</fact>     <fact>SessionCookieTest#flagsArePres... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Changed Hikari pool sizing in application.yml</title>   <subtitle>Hikari now uses a maximum pool size of 20 and maintains five idle connections.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20.</fact>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.minimum... [truncated]
```

### config-flyway-baseline

```text
<observation>   <type>change</type>   <title>Changed Flyway baseline settings in pom.xml</title>   <subtitle>Flyway now baselines existing schemas at version 1 during migration.</subtitle>   <facts>     <fact>pom.xml sets flyway.baselineVersion to 1.</fact>     <fact>pom.xml sets flyway.baselineOnMigrate to true.</fact>     <fact>Maven build resolves the flyway.baselineVersion and flyway.baselineO... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Changed root logger to INFO with JSON appender</title>   <subtitle>logback-spring.xml now routes INFO-level root logging through the JSON appender.</subtitle>   <facts>     <fact>src/main/resources/logback-spring.xml configures the root logger with level="INFO".</fact>     <fact>src/main/resources/logback-spring.xml configures the root logger with appen... [truncated]
```

### config-cors

```text
<observation>   <type>change</type>   <title>Changed CorsConfig API origin and method restrictions</title>   <subtitle>CorsConfig now permits only the production origin and GET/POST requests for /api/**.</subtitle>   <facts>     <fact>CorsConfig registers allowedOrigins("https://app.acme.com") for /api/**.</fact>     <fact>CorsConfig allows only GET and POST methods for /api/**.</fact>     <fact>C... [truncated]
```

### config-timeout

```text
<observation>   <type>change</type>   <title>Changed WebClientConfig response timeout to 5 seconds</title>   <subtitle>WebClient requests now use a five-second response timeout configured through HttpClient.create().</subtitle>   <facts>     <fact>src/main/java/com/acme/web/WebClientConfig.java configures HttpClient.create().responseTimeout(Duration.ofSeconds(5)).</fact>     <fact>WebClientConfigT... [truncated]
```

### config-feature-flags

```text
<observation>   <type>change</type>   <title>Changed feature-flags.yml checkout-v2 and legacy-search values</title>   <subtitle>Enabled checkout-v2 and disabled legacy-search in the feature flag configuration.</subtitle>   <facts>     <fact>src/main/resources/feature-flags.yml sets checkout-v2 to true.</fact>     <fact>src/main/resources/feature-flags.yml sets legacy-search to false.</fact>     <f... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added HEALTHCHECK to Dockerfile</title>   <subtitle>Docker images now verify application health every 30 seconds through the actuator endpoint.</subtitle>   <facts>     <fact>Dockerfile defines HEALTHCHECK --interval=30s with curl -f http://localhost:8080/actuator/health.</fact>     <fact>Dockerfile HEALTHCHECK returns exit 1 when http://localhost:8080/... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 to pom.xml</title>   <subtitle>Maven verification now generates JaCoCo XML coverage output for the project.</subtitle>   <facts>     <fact>pom.xml configures org.jacoco:jacoco-maven-plugin with version 0.8.12.</fact>     <fact>mvn verify writes coverage data to target/site/jacoco/jacoco.xml.</fact>   </facts>   <narrativ... [truncated]
```

### docs-api-observations

```text
<observation>   <type>change</type>   <title>Changed /api/observations pagination documentation in docs/API.md</title>   <subtitle>API documentation now describes the hasMore and items response fields.</subtitle>   <facts>     <fact>docs/API.md documents GET /api/observations.</fact>     <fact>The GET /api/observations response includes hasMore and items fields.</fact>   </facts>   <narrative>The ... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Added local setup steps to README.md</title>   <subtitle>README.md now documents JDK 21, PostgreSQL startup, and the Spring Boot launch command.</subtitle>   <facts>     <fact>README.md requires JDK 21 for local setup.</fact>     <fact>README.md documents starting PostgreSQL with `docker compose up -d postgres`.</fact>     <fact>README.md documents laun... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Added queue recovery steps to docs/runbook.md</title>   <subtitle>The runbook documents inspecting pending_messages, restarting the worker, and verifying /api/health.</subtitle>   <facts>     <fact>docs/runbook.md contains the step “check pending_messages”.</fact>     <fact>docs/runbook.md contains the step “restart worker”.</fact>     <fact>docs/runboo... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Added 2.4.0 changelog entry for offline replay and V20 migration</title>   <subtitle>CHANGELOG.md documents the 2.4.0 release capabilities and migration fix.</subtitle>   <facts>     <fact>CHANGELOG.md now includes a "2.4.0 - 2026-11-03" release entry.</fact>     <fact>CHANGELOG.md documents "Added offline replay evaluation".</fact>     <fact>CHANGELOG.... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>decision</type>   <title>Added ADR 0007 extraction validation decision</title>   <subtitle>ADR 0007 requires validating extracted facts against tool output before persistence and defines rejection handling.</subtitle>   <facts>     <fact>docs/adr/0007-extraction-validation.md records the decision to validate extracted facts against tool output before persistence.</fact>     <... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Changed V20 migration steps in docs/MIGRATION.md</title>   <subtitle>V20 migration documentation now requires a mem_sessions backup and manual sentinel review flag.</subtitle>   <facts>     <fact>docs/MIGRATION.md documents a V20 migration section.</fact>     <fact>V20 migration requires backing up mem_sessions before migration.</fact>     <fact>V20 mig... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Added SECURITY.md vulnerability disclosure policy</title>   <subtitle>SECURITY.md directs vulnerability reports to security@acme.com and prohibits public issue creation.</subtitle>   <facts>     <fact>SECURITY.md reports vulnerabilities to security@acme.com.</fact>     <fact>SECURITY.md forbids opening public issues for vulnerabilities.</fact>   </facts... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Changed CONTRIBUTING.md pull request verification guidance</title>   <subtitle>Contributors are instructed to run the local verification script before opening a pull request.</subtitle>   <facts>     <fact>CONTRIBUTING.md requires running ./scripts/verify-local.sh before opening a pull request.</fact>     <fact>CONTRIBUTING.md names four test layers.</f... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added readinessProbe to deployment.yaml</title>   <subtitle>Kubernetes readiness checks now target the health endpoint on port 37777 after a 15-second startup delay.</subtitle>   <facts>     <fact>deploy/deployment.yaml defines readinessProbe.httpGet.path as /api/health.</fact>     <fact>deploy/deployment.yaml defines readinessProbe.httpGet.port as 3777... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Changed ci.yml backend matrix to Java 21 and 25</title>   <subtitle>GitHub Actions now validates the backend against both JDK 21 and JDK 25.</subtitle>   <facts>     <fact>.github/workflows/ci.yml defines strategy.matrix.java as [21, 25].</fact>     <fact>The workflow syntax check passes after updating .github/workflows/ci.yml.</fact>   </facts>   <narr... [truncated]
```

### deploy-docker-multistage

```text
<observation>   <type>change</type>   <title>Changed Dockerfile runtime to eclipse-temurin:21-jre</title>   <subtitle>Dockerfile now uses separate Maven build and Java 21 JRE runtime stages.</subtitle>   <facts>     <fact>Dockerfile defines a build stage using Maven and a runtime stage using eclipse-temurin:21-jre.</fact>     <fact>Dockerfile copies /app/target/app.jar from the build stage to /app... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Changed deploy IAM role to AmazonS3ReadOnlyAccess</title>   <subtitle>The deploy role now grants read-only S3 access instead of full S3 access.</subtitle>   <facts>     <fact>infra/main.tf defines aws_iam_role_policy.deploy with policy_arn set to arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess.</fact>     <fact>terraform validate succeeds after changing ... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Changed infra/values.yaml replica and memory limits</title>   <subtitle>Helm values now configure three replicas with a 2Gi memory cap.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount: 3.</fact>     <fact>infra/values.yaml sets resources.limits.memory: 2Gi.</fact>     <fact>helm template renders 3 pods with the updated values.</fact> ... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added rollback script for deployment/claude-mem</title>   <subtitle>scripts/rollback.sh undoes the claude-mem rollout and waits for deployment completion.</subtitle>   <facts>     <fact>scripts/rollback.sh runs `kubectl rollout undo deployment/claude-mem`.</fact>     <fact>scripts/rollback.sh runs `kubectl rollout status deployment/claude-mem` after th... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Changed canary rollout settings in canary.yaml</title>   <subtitle>Canary traffic is set to 10 percent with automatic rollback above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true.</fact>     <fact>infra/canary.yaml sets canary.weight to 10 for a 10 percent canary rollout.</fact>     <fact>infra/cana... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title hit requires every declared identifier verbatim and keyword coverage ≥ 0.67; keywords are normalized for case, punctuation, CamelCase and a small synonym table.
- Facts/concepts recall is the primary field signal; precision counts produced items that mention an expected keyword. Trace-support rate is measured independently against the recorded tool input/output.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
