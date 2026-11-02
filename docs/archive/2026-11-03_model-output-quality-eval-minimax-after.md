# Model output quality evaluation

- Date: 2026-11-03
- Phase: after
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 295389 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 99% |
| skip accuracy | 99% |
|   skip confusion (TP/FP/FN/TN) | 19/0/1/62 |
| format errors | 0 |
| validation rejections | 0 |
| call failures | 0 |
| title exact-hit rate (legacy) | 40% |
| title normalized hit rate | 50% |
| title identifier hit rate | 79% |
| type accuracy | 92% |
| facts precision / recall | 72% / 87% |
| facts trace-support rate | 100% |
| concepts precision / recall | 57% / 79% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 1957 / 5495 / 37698 |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | facts P | facts R | format errors |
|---|---|---|---|---|---|---|
| 0 | 82 | 99% | 50% | 72% | 87% | 0 |
| 1 | 13 | 100% | 30% | 88% | 100% | 0 |
| 2 | 13 | 100% | 30% | 87% | 97% | 0 |

- average: skip 100%, title 37%, facts P/R 82%/94%
- worst run: skip 99%, title 30%, facts P/R 72%/87%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 11/12 | 7/12 |
| refactor | 9 | 9/9 | 8/9 | 6/9 |
| test-add | 9 | 9/9 | 8/9 | 8/9 |
| security-fix | 9 | 9/9 | 8/9 | 5/9 |
| config-change | 8 | 8/8 | 8/8 | 1/8 |
| docs-update | 8 | 8/8 | 8/8 | 2/8 |
| deploy-change | 7 | 7/7 | 6/7 | 2/7 |
| skip-low-value | 20 | 19/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title exact | type | facts P/R | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 67% | miss | match | 100%/100% | 67%/100% | 2662 |
| bug-null-cache | bug-fix | observation | observation | pass | 50% | miss | match | 100%/100% | 67%/100% | 2057 |
| bug-timezone-report | bug-fix | observation | observation | pass | 67% | miss | MISS | 33%/100% | 67%/100% | 2225 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 1816 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 5008 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | miss | match | 67%/100% | 67%/100% | 1860 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 50% | miss | match | 67%/100% | 67%/100% | 2199 |
| bug-race-counter | bug-fix | observation | observation | pass | 50% | miss | match | 100%/67% | 33%/50% | 1966 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 75% | miss | match | 100%/100% | 33%/50% | 2111 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 2591 |
| bug-regex-redos | bug-fix | observation | observation | pass | 75% | miss | match | 33%/50% | 67%/100% | 2855 |
| bug-file-encoding | bug-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 2870 |
| refactor-invoice | refactor | observation | observation | pass | 67% | miss | match | 100%/67% | 67%/100% | 1858 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | match | 100%/100% | 100%/100% | 37698 |
| refactor-config-loader | refactor | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 1860 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 2463 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | match | 50%/50% | 67%/100% | 1535 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 4359 |
| refactor-sql-repository | refactor | observation | observation | pass | 67% | miss | MISS | 67%/50% | 67%/100% | 7769 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | match | 33%/100% | 67%/100% | 1836 |
| refactor-feature-flag | refactor | observation | observation | pass | 67% | miss | match | 50%/50% | 67%/100% | 2219 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | match | 67%/67% | 67%/100% | 2889 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 1992 |
| test-unit-retry | test-add | observation | observation | pass | 75% | miss | match | 100%/50% | 67%/100% | 2109 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 2001 |
| test-parameterized-csv | test-add | observation | observation | pass | 75% | miss | match | 100%/50% | 67%/100% | 2791 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 3236 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | match | 100%/100% | 33%/50% | 5040 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 1764 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | miss | MISS | 100%/67% | 67%/100% | 1901 |
| security-redact-log | security-fix | observation | observation | pass | 33% | miss | match | 100%/100% | 67%/100% | 5861 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | hit | match | 50%/50% | 33%/50% | 2160 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | miss | match | 33%/50% | 67%/100% | 1920 |
| security-csrf | security-fix | observation | observation | pass | 100% | hit | match | 33%/50% | 33%/50% | 1834 |
| security-secret-config | security-fix | observation | observation | pass | 50% | miss | MISS | 50%/50% | 67%/100% | 2277 |
| security-xxe | security-fix | observation | observation | pass | 67% | miss | match | 67%/100% | 67%/100% | 1724 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 1747 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | miss | match | 33%/100% | 33%/50% | 1980 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | miss | match | 50%/100% | 67%/100% | 3228 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | miss | match | 67%/100% | 50%/50% | 2350 |
| config-flyway-baseline | config-change | observation | observation | pass | 75% | miss | match | 100%/100% | 50%/50% | 2458 |
| config-log-level | config-change | observation | observation | pass | 75% | miss | match | 33%/50% | 50%/50% | 1599 |
| config-cors | config-change | observation | observation | pass | 50% | miss | match | 67%/100% | 33%/50% | 1947 |
| config-timeout | config-change | observation | observation | pass | 50% | miss | match | 100%/100% | 33%/50% | 2159 |
| config-feature-flags | config-change | observation | observation | pass | 50% | miss | match | 100%/100% | 50%/50% | 2728 |
| config-docker-healthcheck | config-change | observation | observation | pass | 75% | miss | match | 100%/100% | 50%/50% | 1416 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 2380 |
| docs-api-observations | docs-update | observation | observation | pass | 75% | hit | match | 50%/100% | 50%/50% | 2269 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | miss | match | 100%/67% | 50%/50% | 2956 |
| docs-runbook | docs-update | observation | observation | pass | 100% | hit | match | 67%/100% | 50%/50% | 2368 |
| docs-changelog | docs-update | observation | observation | pass | 67% | miss | match | 100%/100% | 50%/50% | 1561 |
| docs-architecture-adr | docs-update | observation | observation | pass | 50% | miss | match | 50%/100% | 33%/50% | 1932 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | miss | match | 67%/100% | 67%/100% | 1916 |
| docs-security-policy | docs-update | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 1674 |
| docs-contributing | docs-update | observation | observation | pass | 33% | miss | match | 100%/100% | 50%/50% | 1329 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | hit | match | 75%/100% | 50%/50% | 4359 |
| deploy-github-actions | deploy-change | observation | observation | pass | 75% | miss | match | 67%/100% | 33%/50% | 1837 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 50% | miss | match | 33%/100% | 67%/100% | 1952 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 25% | miss | match | 100%/100% | 33%/50% | 8705 |
| deploy-helm-values | deploy-change | observation | observation | pass | 75% | miss | match | 67%/100% | 50%/50% | 1697 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | MISS | 67%/100% | 67%/100% | 1650 |
| deploy-canary | deploy-change | observation | observation | pass | 25% | miss | match | 0%/0% | 33%/50% | 5197 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | 2105 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | 942 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | 966 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | 842 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | 1103 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | 1050 |
| skip-package-install | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 5495 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | 1785 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | 1047 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | 862 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | 842 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | 1197 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | 742 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | 801 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | 1020 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | 1957 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | 835 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | 833 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | 940 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | 2729 |

## Problems

- bug-offbyone-pagination: title coverage 67% (expected keywords [fix, pagination, orderquery])
- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed concurrent cache initialization in UserCache.get')
- bug-timezone-report: title coverage 67% (expected keywords [fix, timezone, dailyreport])
- bug-timezone-report: type 'feature' != expected 'bugfix'
- bug-csv-crlf: title coverage 50% (expected keywords [fix, csv, line, endings])
- bug-race-counter: title coverage 50% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: facts recall 67% (expected [incrementAndGet, AtomicLong, 1000], got [CounterService.increment replaced counter.set(counter.get() + 1) with counter.incrementAndGet()., CounterServiceConcurrencyTest passes with 1000 threads producing a final count of 1000.])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, what-changed, how-it-works])
- bug-n-plus-one: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, what-changed, how-it-works])
- bug-regex-redos: facts recall 50% (expected [linear pattern, longInputFinishesUnder100ms], got [UrlValidator defines URL as Pattern.compile("^https?://[^\\s]+$")., UrlValidatorTest#longInputFinishesUnder100ms passes after the UrlValidator regex change., The previous nested-quantifier regex was replaced by a linear matching pattern.])
- refactor-invoice: title coverage 67% (expected keywords [refactor, invoiceservice, delegates])
- refactor-invoice: facts recall 67% (expected [TaxCalculator, InvoiceRenderer, MailGateway], got [InvoiceService calls renderer.render(taxCalculator.calculate(invoice), invoice)., InvoiceService delegates tax calculation to TaxCalculator., InvoiceService delegates invoice rendering to InvoiceRenderer.])
- refactor-retry-policy: facts recall 50% (expected [BackoffStrategy, 100/200/400], got [RetryPolicy returns the delay produced by BackoffStrategy.nextDelay(attempt)., RetryPolicyTest asserts delays of 100, 200, and 400 milliseconds.])
- refactor-sql-repository: title coverage 67% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [UserRepository uses @Query("select u from User u where u.tenantId = :tenantId")., UserRepositoryTest returns the same tenant rows after the query change., The UserRepository edit was applied in /repo/users/src/main/java/com/acme/users/UserRepository.java.])
- refactor-feature-flag: title coverage 67% (expected keywords [refactor, featureflagservice, delegates])
- refactor-feature-flag: facts recall 50% (expected [Flags.isEnabled, on/off matrix], got [FeatureFlagService returns flags.isEnabled(flag, context)., FeatureFlagServiceTest retains the existing on/off evaluation matrix.])
- test-repro-timezone: facts recall 67% (expected [Asia/Shanghai, 2026-11-02, 1 failure], got [DailyReportTest#reportUsesBusinessTimezone tests Instant.parse("2026-11-01T23:30:00Z") against ZoneId.of("Asia/Shanghai")., DailyReportTest#reportUsesBusinessTimezone expects LocalDate.of(2026, 11, 2)., mvn -Dtest=DailyReportTest test reported 1 test and 1 failure because the actual date was 2026-11-01.])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles uses @ParameterizedTest with @CsvSource values "1,100", "2,200", and "3,400"., RetryPolicyTest#delayDoubles asserts that policy.delay(attempt) equals 100, 200, and 400 milliseconds for attempts 1, 2, and 3., RetryPolicyTest#delayDoubles passed all 3 test cases.])
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable is a parameterized test using @ValueSource with "a,b", "a\"b", and "a\nb"., CsvExporterTest#quotingIsStable passes all 3 CSV quoting cases.])
- test-repro-null-cache: title lost identifiers [CacheConcurrencyTest, UserCache] (got 'Added CacheConcurrencyTest#concurrentReadersNeverSeeNull for concurrent cache reads')
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern, what-changed])
- test-flaky-clock: type 'bugfix' != expected 'feature'
- test-flaky-clock: facts recall 67% (expected [Clock.fixed, Thread.sleep, 20 consecutive], got [SchedulerTest.java configures scheduler.clock with Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)., SchedulerTest passed 20 consecutive runs after the Clock.fixed change.])
- security-redact-log: title coverage 33% (expected keywords [redact, credentials, logsanitizer])
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail executes jdbcTemplate.query("select * from users where email = ?", mapper, email)., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the UserDao.findByEmail change.])
- security-sql-injection: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, how-it-works])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: facts recall 50% (expected [ROLE_ADMIN, 403], got [AdminController applies @PreAuthorize("hasRole('ADMIN')") to every endpoint., AdminAuthorizationTest#userRoleGets403 passes after the authorization change., AdminController.java is located at src/main/java/com/acme/admin/AdminController.java.])
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter.java compares csrfToken against request using csrfToken.matches(request)., CsrfFilter.java sends HTTP 403 and returns when csrfToken.matches(request) is false., CsrfFilterTest#postWithoutTokenIs403 passes.])
- security-csrf: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, how-it-works])
- security-secret-config: title coverage 50% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: type 'change' != expected 'bugfix'
- security-secret-config: facts recall 50% (expected [ACME_API_KEY, SecretScanTest], got [src/main/resources/application.yml api-key is configured as ${ACME_API_KEY}., src/main/resources/application.yml no longer contains the literal value sk-live-123.])
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, what-changed, how-it-works])
- config-hikari-pool: title lost identifiers [maximum-pool-size] (got 'Changed HikariCP pool sizing in application.yml')
- config-hikari-pool: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-flyway-baseline: title lost identifiers [pom.xml] (got 'Configured Flyway baseline on migrate')
- config-flyway-baseline: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-log-level: title lost identifiers [logback-spring.xml] (got 'Changed root logger output to JSON appender')
- config-log-level: facts recall 50% (expected [INFO, JSON appender], got [src/main/resources/logback-spring.xml configures the root logger with level="INFO"., src/main/resources/logback-spring.xml attaches appender-ref ref="JSON" to the root logger., LogbackConfigTest parses the updated logback-spring.xml.])
- config-log-level: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-cors: title coverage 50% (expected keywords [restrict, cors, corsconfig, origin])
- config-cors: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, problem-solution, how-it-works])
- config-timeout: title coverage 50% (expected keywords [set, webclient, timeout, 5])
- config-timeout: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, gotcha])
- config-feature-flags: title lost identifiers [feature-flags.yml] (got 'Changed feature flags checkout-v2 and legacy-search')
- config-feature-flags: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-docker-healthcheck: title lost identifiers [Dockerfile] (got 'Added HEALTHCHECK for /actuator/health')
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- docs-api-observations: title lost identifiers [API.md, /api/observations] (got 'Changed /api/observations pagination documentation')
- docs-api-observations: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: facts recall 67% (expected [JDK 21, docker compose, spring-boot:run], got [README.md instructs developers to run `docker compose up -d postgres`., README.md instructs developers to start the application with `mvn spring-boot:run`.])
- docs-readme-setup: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-runbook: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- docs-architecture-adr: title lost identifiers [0007-extraction-validation.md] (got 'Decided validation before persistence in ADR 0007')
- docs-architecture-adr: concepts recall 50% (expected [why-it-exists, trade-off], got [why-it-exists, how-it-works, pattern])
- docs-migration-guide: title lost identifiers [MIGRATION.md] (got 'Documented V20 migration prerequisite')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, why-it-exists])
- docs-contributing: title coverage 33% (expected keywords [update, contributing, verify-local])
- docs-contributing: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- deploy-k8s-probe: title lost identifiers [deployment.yaml, /api/health] (got 'Added readinessProbe to deployment.yaml')
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- deploy-github-actions: title lost identifiers [ci.yml] (got 'Changed CI backend matrix to JDK 21 and 25')
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, pattern])
- deploy-docker-multistage: title coverage 50% (expected keywords [convert, dockerfile, multi-stage, jre])
- deploy-terraform-iam: title lost identifiers [main.tf] (got 'Changed deploy role to AmazonS3ReadOnlyAccess')
- deploy-terraform-iam: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, why-it-exists, problem-solution])
- deploy-helm-values: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 25% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: facts recall 0% (expected [10 percent, 2 percent], got [infra/canary.yaml sets canary.enabled to true., infra/canary.yaml sets canary.weight to 10., infra/canary.yaml sets canary.errorThreshold to 0.02, and helm lint passes.])
- deploy-canary: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, gotcha])
- skip-package-install: expected <skip> but produced an observation

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset off-by-one error</title>   <subtitle>OrderQuery.pageOffset now starts the first page at offset zero.</subtitle>   <facts>     <fact>OrderQuery.pageOffset changed from `page * size - 1` to `page * size`.</fact>     <fact>OrderQueryTest#firstPageStartsAtZero passes after the change.</fact>   </facts>   <narrative>OrderQuery pag... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed concurrent cache initialization in UserCache.get</title>   <subtitle>UserCache.get now atomically loads missing entries through computeIfAbsent.</subtitle>   <facts>     <fact>UserCache.get changed from return cache.get(id) to return cache.computeIfAbsent(id, loader::load).</fact>     <fact>CacheConcurrencyTest passes after the UserCache.get chang... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai</title>   <subtitle>Regression test pins a UTC timestamp and expects the Asia/Shanghai business date.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone was added to src/test/java/com/acme/reporting/DailyReportTest.java.</fact>     <fact>DailyReportTest#reportUses... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed multiline CSV row counting in CsvExporter</title>   <subtitle>CsvExporter normalizes embedded line endings to CRLF before quoting, preserving multiline fields as one logical row.</subtitle>   <facts>     <fact>CsvExporter.java writes lines with `line.replaceAll("\r?\n", "\r\n")` before quoting.</fact>     <fact>CsvExporterTest#multilineFieldKeepsR... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed atomic counter increment in CounterService.increment</title>   <subtitle>CounterService.increment now uses AtomicLong.incrementAndGet, preventing lost concurrent increments.</subtitle>   <facts>     <fact>CounterService.increment replaced counter.set(counter.get() + 1) with counter.incrementAndGet().</fact>     <fact>CounterServiceConcurrencyTest ... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>bugfix</type>   <title>Fixed N+1 order-item queries in OrderRepository</title>   <subtitle>OrderRepository now fetches associated items using one SQL statement.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId(String customerId) uses @EntityGraph(attributePaths = "items").</fact>     <fact>QueryCountTest observes 1 SQL statement instead of 1 + N for customer... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator catastrophic backtracking with linear URL regex</title>   <subtitle>UrlValidator now rejects whitespace-containing input using a regex without nested quantifiers.</subtitle>   <facts>     <fact>UrlValidator defines URL as Pattern.compile("^https?://[^\\s]+$").</fact>     <fact>UrlValidatorTest#longInputFinishesUnder100ms passes after ... [truncated]
```

### refactor-invoice

```text
<observation>   <type>refactor</type>   <title>Refactored InvoiceService tax and rendering delegation</title>   <subtitle>InvoiceService now delegates tax calculation and rendering to dedicated collaborators.</subtitle>   <facts>     <fact>InvoiceService calls renderer.render(taxCalculator.calculate(invoice), invoice).</fact>     <fact>InvoiceService delegates tax calculation to TaxCalculator.</fa... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Refactored RetryPolicy delay calculation into BackoffStrategy</title>   <subtitle>RetryPolicy delegates delay computation while preserving the tested retry sequence.</subtitle>   <facts>     <fact>RetryPolicy returns the delay produced by BackoffStrategy.nextDelay(attempt).</fact>     <fact>RetryPolicyTest asserts delays of 100, 200, and 400 milliseco... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Fixed UserRepository tenant filtering with JPQL @Query</title>   <subtitle>UserRepository now filters tenant rows using a parameterized JPQL query.</subtitle>   <facts>     <fact>UserRepository uses @Query("select u from User u where u.tenantId = :tenantId").</fact>     <fact>UserRepositoryTest returns the same tenant rows after the query change.</fact>... [truncated]
```

### refactor-feature-flag

```text
<observation>   <type>refactor</type>   <title>Refactored FeatureFlagService evaluation to Flags.isEnabled</title>   <subtitle>FeatureFlagService now delegates flag evaluation to the shared Flags API without changing test behavior.</subtitle>   <facts>     <fact>FeatureFlagService returns flags.isEnabled(flag, context).</fact>     <fact>FeatureFlagServiceTest retains the existing on/off evaluation... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai</title>   <subtitle>Regression test reproduces the UTC-versus-business-date bug in daily report date calculation.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone tests Instant.parse("2026-11-01T23:30:00Z") against ZoneId.of("Asia/Shanghai").</fact>     <fact>Da... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles for exponential retry delays</title>   <subtitle>Parameterized tests verify retry delays of 100, 200, and 400 milliseconds for attempts 1 through 3.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles uses @ParameterizedTest with @CsvSource values "1,100", "2,200", and "3,400".</fact>     <fact>RetryPolicyTest... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable for CSV quoting</title>   <subtitle>Parameterized coverage verifies stable quoting for commas, quotes, and newlines.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable is a parameterized test using @ValueSource with "a,b", "a\"b", and "a\nb".</fact>     <fact>CsvExporterTest#quotingIsStable passes all 3... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull for concurrent cache reads</title>   <subtitle>A parallel-reader regression test reproduces intermittent null values from UserCache.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull runs 1000 parallel readers against cache.get("k").</fact>     <fact>CacheConcurren... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>bugfix</type>   <title>Fixed SchedulerTest clock flakiness with Clock.fixed</title>   <subtitle>SchedulerTest now uses a fixed UTC clock instead of Thread.sleep, with 20 consecutive passing runs.</subtitle>   <facts>     <fact>SchedulerTest.java configures scheduler.clock with Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC).</fact>     <fact>SchedulerTest.j... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed credential leakage in LogSanitizer</title>   <subtitle>LogSanitizer now redacts password, token, and secret key-value pairs before logging.</subtitle>   <facts>     <fact>src/main/java/com/acme/security/LogSanitizer.java replaces password, token, and secret values matching the configured key pattern with ***.</fact>     <fact>LogSanitizerTest#reda... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed SQL injection in UserDao.findByEmail</title>   <subtitle>UserDao.findByEmail now binds email with a JDBC parameter placeholder instead of concatenating SQL.</subtitle>   <facts>     <fact>UserDao.findByEmail executes jdbcTemplate.query("select * from users where email = ?", mapper, email).</fact>     <fact>SqlInjectionTest#emailWithQuoteReturnsEmp... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Fixed AdminController authorization for ROLE_ADMIN</title>   <subtitle>AdminController now requires ROLE_ADMIN on every endpoint, and user-role access is rejected.</subtitle>   <facts>     <fact>AdminController applies @PreAuthorize("hasRole('ADMIN')") to every endpoint.</fact>     <fact>AdminAuthorizationTest#userRoleGets403 passes after the authorizat... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Fixed CSRF token validation in CsrfFilter</title>   <subtitle>CsrfFilter now rejects state-changing requests when the submitted CSRF token does not match.</subtitle>   <facts>     <fact>CsrfFilter.java compares csrfToken against request using csrfToken.matches(request).</fact>     <fact>CsrfFilter.java sends HTTP 403 and returns when csrfToken.matches(r... [truncated]
```

### security-secret-config

```text
<observation>   <type>change</type>   <title>Changed application.yml api-key to use ACME_API_KEY</title>   <subtitle>application.yml now reads the API key from the environment instead of storing a literal secret.</subtitle>   <facts>     <fact>src/main/resources/application.yml api-key is configured as ${ACME_API_KEY}.</fact>     <fact>src/main/resources/application.yml no longer contains the lite... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Fixed XXE vulnerability in XmlImportService</title>   <subtitle>XmlImportService now rejects XML DOCTYPE declarations and external entities.</subtitle>   <facts>     <fact>XmlImportService configures disallow-doctype-decl to true.</fact>     <fact>XxeTest#externalEntityIsRejected passes after the configuration change.</fact>     <fact>XML import request... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter empty-bucket response</title>   <subtitle>RateLimiter now throws TooManyRequestsException after the token bucket is exhausted.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/RateLimiter.java calls bucket.tryConsume(1) and throws TooManyRequestsException when consumption fails.</fact>     <fact>RateLimiter returns HTTP 429 wh... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Changed HikariCP pool sizing in application.yml</title>   <subtitle>Configured a maximum pool size of 20 and minimum idle size of 5 for the Spring datasource.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20.</fact>     <fact>src/main/resources/application.yml sets spring.datasource.... [truncated]
```

### config-flyway-baseline

```text
<observation>   <type>change</type>   <title>Configured Flyway baseline on migrate</title>   <subtitle>Flyway now baselines existing schemas at version 1 during migration.</subtitle>   <facts>     <fact>pom.xml sets flyway.baselineVersion to 1.</fact>     <fact>pom.xml sets flyway.baselineOnMigrate to true.</fact>     <fact>Maven build resolved the new flyway.baselineVersion property.</fact>   </f... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Changed root logger output to JSON appender</title>   <subtitle>logback-spring.xml now routes INFO-level root logs through the JSON appender.</subtitle>   <facts>     <fact>src/main/resources/logback-spring.xml configures the root logger with level="INFO".</fact>     <fact>src/main/resources/logback-spring.xml attaches appender-ref ref="JSON" to the roo... [truncated]
```

### config-cors

```text
<observation>   <type>change</type>   <title>Changed CorsConfig API CORS policy</title>   <subtitle>CorsConfig now restricts /api/** requests to https://app.acme.com and GET or POST methods.</subtitle>   <facts>     <fact>CorsConfig maps /api/** with allowedOrigins("https://app.acme.com").</fact>     <fact>CorsConfig permits only GET and POST methods for /api/** requests.</fact>     <fact>CorsConf... [truncated]
```

### config-timeout

```text
<observation>   <type>change</type>   <title>Changed WebClientConfig response timeout to five seconds</title>   <subtitle>WebClient now terminates slow HTTP responses after a five-second response timeout.</subtitle>   <facts>     <fact>WebClientConfig configures HttpClient.create() with responseTimeout(Duration.ofSeconds(5)).</fact>     <fact>WebClientConfigTest reads the configured response timeo... [truncated]
```

### config-feature-flags

```text
<observation>   <type>change</type>   <title>Changed feature flags checkout-v2 and legacy-search</title>   <subtitle>feature-flags.yml enables checkout-v2 and disables legacy-search for boolean binding coverage.</subtitle>   <facts>     <fact>src/main/resources/feature-flags.yml sets checkout-v2: true.</fact>     <fact>src/main/resources/feature-flags.yml sets legacy-search: false.</fact>     <fac... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added HEALTHCHECK for /actuator/health</title>   <subtitle>Dockerfile now probes the actuator health endpoint every 30 seconds.</subtitle>   <facts>     <fact>Dockerfile adds HEALTHCHECK --interval=30s CMD curl -f http://localhost:8080/actuator/health || exit 1.</fact>     <fact>docker build succeeds after the Dockerfile HEALTHCHECK addition.</fact>   <... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 to pom.xml</title>   <subtitle>Maven verification now generates JaCoCo coverage reports during the build lifecycle.</subtitle>   <facts>     <fact>pom.xml declares org.jacoco:jacoco-maven-plugin with version 0.8.12.</fact>     <fact>mvn verify generates target/site/jacoco/jacoco.xml.</fact>   </facts>   <narrative>The Ma... [truncated]
```

### docs-api-observations

```text
<observation>   <type>change</type>   <title>Changed /api/observations pagination documentation</title>   <subtitle>API.md now documents the hasMore and items pagination fields returned by the observations endpoint.</subtitle>   <facts>     <fact>docs/API.md documents GET /api/observations as returning hasMore and items.</fact>     <fact>docs/API.md points readers to the pagination contract.</fact... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Changed README.md local setup steps</title>   <subtitle>README.md now documents a three-step local development setup using JDK 21 and PostgreSQL.</subtitle>   <facts>     <fact>README.md specifies JDK 21 as the required Java version.</fact>     <fact>README.md instructs developers to run `docker compose up -d postgres`.</fact>     <fact>README.md instru... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Added queue recovery steps to docs/runbook.md</title>   <subtitle>The runbook documents inspection, worker restart, and health verification for queue recovery.</subtitle>   <facts>     <fact>docs/runbook.md contains the step "check pending_messages".</fact>     <fact>docs/runbook.md contains the step "restart worker".</fact>     <fact>docs/runbook.md co... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Added CHANGELOG.md 2.4.0 release entry</title>   <subtitle>Documented offline replay evaluation and the V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md now contains a `2.4.0 - 2026-11-03` release entry.</fact>     <fact>CHANGELOG.md lists `Added offline replay evaluation` for version 2.4.0.</fact>     <fact>CHANGELOG.md lists `Fi... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>decision</type>   <title>Decided validation before persistence in ADR 0007</title>   <subtitle>Extracted facts must be validated against tool output before persistence, with rejected facts excluded.</subtitle>   <facts>     <fact>docs/adr/0007-extraction-validation.md records the decision to validate extracted facts against tool output before persistence.</fact>     <fact>ADR... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisite</title>   <subtitle>Migration documentation now specifies the mem_sessions backup and manual sentinel configuration.</subtitle>   <facts>     <fact>docs/MIGRATION.md documents the V20 migration procedure.</fact>     <fact>V20 migration requires backing up mem_sessions before migration.</fact>     <fact>V20 migration... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Added SECURITY.md vulnerability disclosure policy</title>   <subtitle>Security policy identifies the private reporting channel and prohibits public vulnerability issues.</subtitle>   <facts>     <fact>SECURITY.md directs vulnerability reports to security@acme.com.</fact>     <fact>SECURITY.md forbids opening public issues for vulnerabilities.</fact>   <... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Changed CONTRIBUTING.md pull request verification</title>   <subtitle>CONTRIBUTING.md now requires the local verification script before opening a pull request.</subtitle>   <facts>     <fact>CONTRIBUTING.md instructs contributors to run ./scripts/verify-local.sh before opening a pull request.</fact>     <fact>CONTRIBUTING.md names four test layers.</fac... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added readinessProbe to deployment.yaml</title>   <subtitle>Deployment readiness now checks the API health endpoint on port 37777 after a 15-second startup delay.</subtitle>   <facts>     <fact>deploy/deployment.yaml defines readinessProbe.httpGet.path as /api/health.</fact>     <fact>deploy/deployment.yaml defines readinessProbe.httpGet.port as 37777.<... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Changed CI backend matrix to JDK 21 and 25</title>   <subtitle>The backend CI workflow now tests both supported Java versions.</subtitle>   <facts>     <fact>.github/workflows/ci.yml defines strategy.matrix.java as [21, 25].</fact>     <fact>.github/workflows/ci.yml runs the backend on JDK 21 and 25.</fact>     <fact>Workflow syntax validation for .gith... [truncated]
```

### deploy-docker-multistage

```text
<observation>   <type>change</type>   <title>Changed Dockerfile runtime image to eclipse-temurin:21-jre</title>   <subtitle>Dockerfile now copies app.jar into a smaller JRE-only runtime stage.</subtitle>   <facts>     <fact>Dockerfile uses `FROM eclipse-temurin:21-jre AS runtime`.</fact>     <fact>Dockerfile copies `/app/target/app.jar` from the build stage to `/app/app.jar`.</fact>     <fact>Cont... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Changed deploy role to AmazonS3ReadOnlyAccess</title>   <subtitle>The deploy IAM role now receives read-only S3 permissions, and terraform validate succeeds.</subtitle>   <facts>     <fact>infra/main.tf defines aws_iam_role_policy.deploy with policy_arn set to arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess.</fact>     <fact>The aws_iam_role_policy.deplo... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Changed Helm replicaCount and memory limit in values.yaml</title>   <subtitle>Helm now renders three pods with a 2Gi memory cap.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount to 3.</fact>     <fact>infra/values.yaml sets resources.limits.memory to 2Gi.</fact>     <fact>helm template renders 3 pods with the updated values.</fact>   <... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added deployment rollback script rollback.sh</title>   <subtitle>Rollback script reverts the claude-mem deployment and waits for rollout completion.</subtitle>   <facts>     <fact>scripts/rollback.sh runs `kubectl rollout undo deployment/claude-mem`.</fact>     <fact>scripts/rollback.sh runs `kubectl rollout status deployment/claude-mem` after the roll... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Changed canary rollout configuration in infra/canary.yaml</title>   <subtitle>Canary traffic is enabled at 10 percent with automatic rollback above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true.</fact>     <fact>infra/canary.yaml sets canary.weight to 10.</fact>     <fact>infra/canary.yaml sets cana... [truncated]
```

### skip-package-install

```text
<observation>   <type>change</type>   <title>Installed npm dependencies without changing package-lock.json</title>   <subtitle>npm install added 412 packages with no audit vulnerabilities and preserved the existing lockfile.</subtitle>   <facts>     <fact>Command `npm install --no-audit` completed in `/repo` with 412 packages added in 18 seconds.</fact>     <fact>`npm install --no-audit` reported ... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title hit requires every declared identifier verbatim and keyword coverage ≥ 0.67; keywords are normalized for case, punctuation, CamelCase and a small synonym table.
- Facts/concepts recall is the primary field signal; precision counts produced items that mention an expected keyword. Trace-support rate is measured independently against the recorded tool input/output.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
