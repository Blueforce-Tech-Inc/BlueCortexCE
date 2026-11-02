# Model output quality evaluation

- Date: 2026-11-03
- Phase: current
- Provider: anthropic, model `claude-sonnet-4-5`, base URL `replay`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 226368 ms (sum of per-call latencies; wall clock line lost when the replay overwrote this file); temperature: 0.0
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
| title exact-hit rate (legacy) | 39% |
| title normalized hit rate | 37% |
| title identifier hit rate | 92% |
| type accuracy | 92% |
| facts precision / recall | 75% / 90% |
| facts trace-support rate | 100% |
| concepts precision / recall | 66% / 71% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 1746 / 4173 / 7146 |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | facts P | facts R | format errors |
|---|---|---|---|---|---|---|
| 0 | 82 | 99% | 37% | 75% | 90% | 0 |
| 1 | 13 | 100% | 40% | 83% | 100% | 0 |
| 2 | 13 | 100% | 60% | 91% | 100% | 0 |

- average: skip 100%, title 46%, facts P/R 83%/97%
- worst run: skip 99%, title 37%, facts P/R 75%/90%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 11/12 | 4/12 |
| refactor | 9 | 9/9 | 7/9 | 5/9 |
| test-add | 9 | 9/9 | 9/9 | 4/9 |
| security-fix | 9 | 9/9 | 8/9 | 3/9 |
| config-change | 8 | 8/8 | 8/8 | 3/8 |
| docs-update | 8 | 8/8 | 8/8 | 2/8 |
| deploy-change | 7 | 7/7 | 6/7 | 2/7 |
| skip-low-value | 20 | 19/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title exact | type | facts P/R | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 67% | miss | match | 100%/100% | 100%/100% | 1868 |
| bug-null-cache | bug-fix | observation | observation | pass | 75% | miss | match | 100%/100% | 33%/50% | 2226 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 100%/100% | 3778 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 100%/100% | 1681 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 1883 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | miss | match | 100%/100% | 67%/100% | 1767 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 50% | miss | match | 67%/100% | 67%/100% | 2176 |
| bug-race-counter | bug-fix | observation | observation | pass | 75% | miss | match | 100%/100% | 50%/50% | 6767 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 50% | miss | MISS | 100%/100% | 67%/100% | 1727 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 100%/100% | 1783 |
| bug-regex-redos | bug-fix | observation | observation | pass | 75% | miss | match | 67%/100% | 50%/50% | 1707 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | miss | match | 100%/100% | 67%/100% | 2360 |
| refactor-invoice | refactor | observation | observation | pass | 67% | miss | match | 67%/100% | 100%/100% | 1779 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | match | 50%/50% | 100%/100% | 1623 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | miss | MISS | 67%/100% | 50%/50% | 4173 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | match | 67%/100% | 100%/100% | 2239 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | match | 50%/50% | 100%/100% | 1694 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | match | 67%/100% | 100%/100% | 2182 |
| refactor-sql-repository | refactor | observation | observation | pass | 67% | miss | MISS | 100%/50% | 100%/100% | 1575 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | match | 0%/0% | 100%/100% | 1917 |
| refactor-feature-flag | refactor | observation | observation | pass | 67% | miss | match | 50%/50% | 100%/100% | 2482 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | match | 67%/100% | 100%/100% | 2070 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | match | 100%/100% | 100%/100% | 1711 |
| test-unit-retry | test-add | observation | observation | pass | 75% | miss | match | 67%/50% | 100%/100% | 5029 |
| test-integration-order | test-add | observation | observation | pass | 50% | miss | match | 50%/100% | 50%/50% | 3941 |
| test-parameterized-csv | test-add | observation | observation | pass | 75% | miss | match | 100%/50% | 50%/50% | 1710 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 2998 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 2575 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 1851 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | miss | match | 100%/100% | 50%/50% | 1546 |
| security-redact-log | security-fix | observation | observation | pass | 67% | miss | match | 67%/100% | 50%/50% | 2005 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | hit | match | 33%/50% | 67%/100% | 3240 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | miss | match | 50%/50% | 50%/50% | 1608 |
| security-csrf | security-fix | observation | observation | pass | 100% | hit | match | 33%/50% | 50%/50% | 1743 |
| security-secret-config | security-fix | observation | observation | pass | 50% | miss | MISS | 67%/100% | 50%/50% | 1760 |
| security-xxe | security-fix | observation | observation | pass | 67% | miss | match | 67%/100% | 67%/100% | 1906 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 50%/50% | 2035 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | miss | match | 33%/100% | 50%/50% | 1727 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | miss | match | 67%/100% | 67%/100% | 2388 |
| config-hikari-pool | config-change | observation | observation | pass | 50% | miss | match | 100%/100% | 50%/50% | 1837 |
| config-flyway-baseline | config-change | observation | observation | pass | 50% | miss | match | 100%/100% | 50%/50% | 1788 |
| config-log-level | config-change | observation | observation | pass | 100% | hit | match | 33%/50% | 50%/50% | 1590 |
| config-cors | config-change | observation | observation | pass | 50% | miss | match | 67%/100% | 33%/50% | 1880 |
| config-timeout | config-change | observation | observation | pass | 75% | miss | match | 100%/100% | 50%/50% | 2097 |
| config-feature-flags | config-change | observation | observation | pass | 50% | miss | match | 100%/100% | 50%/50% | 3883 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 1569 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 2511 |
| docs-api-observations | docs-update | observation | observation | pass | 75% | miss | match | 50%/100% | 50%/50% | 1566 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | miss | match | 100%/100% | 50%/50% | 1746 |
| docs-runbook | docs-update | observation | observation | pass | 100% | hit | match | 67%/100% | 50%/50% | 1468 |
| docs-changelog | docs-update | observation | observation | pass | 67% | miss | match | 100%/100% | 50%/50% | 1489 |
| docs-architecture-adr | docs-update | observation | observation | pass | 50% | miss | match | 50%/100% | 50%/50% | 1717 |
| docs-migration-guide | docs-update | observation | observation | pass | 50% | miss | match | 100%/100% | 100%/100% | 1366 |
| docs-security-policy | docs-update | observation | observation | pass | 100% | hit | match | 100%/100% | 0%/0% | 1356 |
| docs-contributing | docs-update | observation | observation | pass | 33% | miss | match | 100%/100% | 50%/50% | 1458 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | hit | match | 67%/100% | 50%/50% | 1944 |
| deploy-github-actions | deploy-change | observation | observation | pass | 50% | miss | match | 100%/100% | 50%/50% | 1607 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 50% | miss | match | 33%/100% | 100%/100% | 1820 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 25% | miss | match | 67%/100% | 50%/50% | 2953 |
| deploy-helm-values | deploy-change | observation | observation | pass | 50% | miss | match | 67%/100% | 100%/100% | 2358 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | MISS | 67%/100% | 67%/100% | 1625 |
| deploy-canary | deploy-change | observation | observation | pass | 50% | miss | match | 67%/100% | 50%/50% | 1463 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | 741 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | 761 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | 1060 |
| skip-view-logs | skip-low-value | skip | observation | FAIL | 0% | miss | MISS | 0%/0% | 0%/0% | 2338 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | 1197 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | 5019 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | 968 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | 870 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | 738 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | 846 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | 874 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | 7146 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | 821 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | 717 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | 751 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | 1121 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | 2021 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | 938 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | 780 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | 1045 |

## Problems

- bug-offbyone-pagination: title coverage 67% (expected keywords [fix, pagination, orderquery])
- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed UserCache.get concurrent null placeholder race')
- bug-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, how-it-works])
- bug-connection-leak: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, how-it-works])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-csv-crlf: title coverage 50% (expected keywords [fix, csv, line, endings])
- bug-race-counter: title coverage 75% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, how-it-works])
- bug-n-plus-one: title coverage 50% (expected keywords [fix, n+1, orderrepository, entitygraph])
- bug-n-plus-one: type 'optimization' != expected 'bugfix'
- bug-regex-redos: title coverage 75% (expected keywords [fix, redos, urlvalidator, regex])
- bug-regex-redos: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- refactor-invoice: title coverage 67% (expected keywords [refactor, invoiceservice, delegates])
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [AuthController now calls TokenService.issue(authentication) instead of constructing JWTs inline., AuthControllerTest remains unchanged and passes after the AuthController delegation change.])
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: type 'change' != expected 'refactor'
- refactor-config-loader: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- refactor-retry-policy: facts recall 50% (expected [BackoffStrategy, 100/200/400], got [RetryPolicy returns backoff.nextDelay(attempt) for retry delay calculation., RetryPolicyTest asserts the unchanged 100/200/400 retry delay sequence.])
- refactor-sql-repository: title coverage 67% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: type 'change' != expected 'refactor'
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [src/main/java/com/acme/users/UserRepository.java uses @Query("select u from User u where u.tenantId = :tenantId")., UserRepositoryTest returns the same tenant rows after the query change.])
- refactor-event-bus: facts recall 0% (expected [listener map, event type], got [src/main/java/com/acme/events/EventBus.java uses listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event)) for dispatch., EventBus dispatch behavior is covered by EventBusTest for the same three events.])
- refactor-feature-flag: title coverage 67% (expected keywords [refactor, featureflagservice, delegates])
- refactor-feature-flag: facts recall 50% (expected [Flags.isEnabled, on/off matrix], got [FeatureFlagService returns flags.isEnabled(flag, context) for feature flag evaluation., FeatureFlagServiceTest retains the same on/off evaluation matrix.])
- test-unit-retry: title coverage 75% (expected keywords [add, retrypolicy, parameterized, test])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles covers attempts 1, 2, and 3 with expected delays of 100, 200, and 400 milliseconds., RetryPolicyTest uses @ParameterizedTest and @CsvSource with inputs "1,100", "2,200", and "3,400"., RetryPolicyTest#delayDoubles passed all 3 tests.])
- test-integration-order: title coverage 50% (expected keywords [add, integration, order, test])
- test-integration-order: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- test-parameterized-csv: title coverage 75% (expected keywords [add, csv, parameterized, test])
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable uses @ParameterizedTest with values "a,b", "a\"b", and "a\nb"., CsvExporterTest#quotingIsStable passes all 3 CSV quoting cases.])
- test-parameterized-csv: concepts recall 50% (expected [what-changed, pattern], got [what-changed, problem-solution])
- test-security-headers: concepts recall 50% (expected [what-changed, pattern], got [what-changed, problem-solution])
- test-repro-null-cache: title lost identifiers [CacheConcurrencyTest, UserCache] (got 'Added CacheConcurrencyTest#concurrentReadersNeverSeeNull for parallel cache reads')
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, problem-solution])
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- test-flaky-clock: concepts recall 50% (expected [problem-solution, pattern], got [what-changed, problem-solution])
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail uses jdbcTemplate.query("select * from users where email = ?", mapper, email)., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the UserDao.findByEmail change., The changed file is src/main/java/com/acme/users/UserDao.java.])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: facts recall 50% (expected [ROLE_ADMIN, 403], got [AdminController applies @PreAuthorize("hasRole('ADMIN')") to every endpoint., AdminAuthorizationTest#userRoleGets403 passes after the authorization change.])
- security-authz-admin: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, problem-solution])
- security-csrf: facts recall 50% (expected [403, mismatched token], got [src/main/java/com/acme/web/CsrfFilter.java calls response.sendError(403) when !csrfToken.matches(request)., CsrfFilter rejects state-changing requests with a mismatched CSRF token before request processing continues., CsrfFilterTest#postWithoutTokenIs403 passes after the CsrfFilter validation change.])
- security-csrf: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- security-secret-config: title coverage 50% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: type 'change' != expected 'bugfix'
- security-secret-config: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, problem-solution])
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-path-traversal: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, how-it-works])
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [what-changed, problem-solution])
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- config-hikari-pool: title lost identifiers [maximum-pool-size] (got 'Configured Hikari pool bounds in application.yml')
- config-hikari-pool: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-flyway-baseline: title coverage 50% (expected keywords [enable, flyway, baseline, migrate])
- config-flyway-baseline: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-log-level: facts recall 50% (expected [INFO, JSON appender], got [src/main/resources/logback-spring.xml defines the root logger with level="INFO"., src/main/resources/logback-spring.xml routes root logger events through appender-ref ref="JSON"., LogbackConfigTest parses the XML in src/main/resources/logback-spring.xml.])
- config-log-level: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-cors: title coverage 50% (expected keywords [restrict, cors, corsconfig, origin])
- config-cors: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, problem-solution, how-it-works])
- config-timeout: title coverage 75% (expected keywords [set, webclient, timeout, 5])
- config-timeout: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, problem-solution])
- config-feature-flags: title coverage 50% (expected keywords [enable, checkout-v2, disable, legacy-search])
- config-feature-flags: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- docs-api-observations: title coverage 75% (expected keywords [document, observations, pagination, api])
- docs-api-observations: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-runbook: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- docs-architecture-adr: title lost identifiers [0007-extraction-validation.md] (got 'Added ADR 0007 extracted fact validation')
- docs-architecture-adr: concepts recall 50% (expected [why-it-exists, trade-off], got [why-it-exists, how-it-works])
- docs-migration-guide: title coverage 50% (expected keywords [document, v20, migration, sentinel])
- docs-security-policy: concepts recall 0% (expected [why-it-exists, pattern], got [what-changed, how-it-works])
- docs-contributing: title coverage 33% (expected keywords [update, contributing, verify-local])
- docs-contributing: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- deploy-github-actions: title coverage 50% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- deploy-docker-multistage: title coverage 50% (expected keywords [convert, dockerfile, multi-stage, jre])
- deploy-terraform-iam: title lost identifiers [main.tf] (got 'Hardened deploy role with AmazonS3ReadOnlyAccess')
- deploy-terraform-iam: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, problem-solution])
- deploy-helm-values: title coverage 50% (expected keywords [change, helm, replicas, memory])
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 50% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- skip-view-logs: expected <skip> but produced an observation

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset first-page offset calculation</title>   <subtitle>First-page pagination now starts at offset zero instead of minus one.</subtitle>   <facts>     <fact>OrderQuery.pageOffset now returns page * size without subtracting 1.</fact>     <fact>OrderQueryTest#firstPageStartsAtZero passes after the offset correction.</fact>   </facts>... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache.get concurrent null placeholder race</title>   <subtitle>UserCache.get atomically loads missing entries through computeIfAbsent, and CacheConcurrencyTest passes.</subtitle>   <facts>     <fact>UserCache.get changed from returning cache.get(id) to returning cache.computeIfAbsent(id, loader::load).</fact>     <fact>UserCache.get prevents t... [truncated]
```

### bug-connection-leak

```text
<observation>   <type>bugfix</type>   <title>Fixed ConnectionPool.borrow connection leak on caller exceptions</title>   <subtitle>Borrowed connections now close with try-with-resources when callers throw.</subtitle>   <facts>     <fact>ConnectionPool.borrow uses try-with-resources for the connection returned by dataSource.getConnection().</fact>     <fact>PoolLeakTest reports activeConnections = 0... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration equality boundary</title>   <subtitle>JwtVerifier now rejects tokens expiring at the exact current instant, including the equality boundary.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/JwtVerifier.java checks claims.getExpiration().isBefore(now) or claims.getExpiration().isEqual(now).</fact>     <fact>JwtVerif... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsvExporter embedded newline normalization to CRLF</title>   <subtitle>CsvExporter now normalizes embedded line endings before quoting, preserving CSV row counts for multiline fields.</subtitle>   <facts>     <fact>src/main/java/com/acme/export/CsvExporter.java writes `line.replaceAll("\\r?\\n", "\\r\\n")` before CSV quoting.</fact>     <fact>CsvE... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment lost updates with AtomicLong.incrementAndGet</title>   <subtitle>Counter increments now use one atomic operation, preventing concurrent updates from overwriting each other.</subtitle>   <facts>     <fact>CounterService.increment returns counter.incrementAndGet() instead of counter.set(counter.get() + 1) followed by counter... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>optimization</type>   <title>Optimized OrderRepository item fetching to eliminate N+1 queries</title>   <subtitle>OrderRepository now fetches order items in the same query, reducing QueryCountTest from 1 + N SQL statements to 1.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId(String customerId) uses @EntityGraph(attributePaths = "items") to fetch items in t... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator long input regex denial-of-service</title>   <subtitle>Replaced a nested-quantifier regex with a linear URL pattern that completes long inputs under 100ms.</subtitle>   <facts>     <fact>UrlValidator declares URL as Pattern.compile("^https?://[^\\s]+$").</fact>     <fact>UrlValidatorTest#longInputFinishesUnder100ms passes after the re... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed FileReaderFactory UTF-8 decoding on non-UTF-8 JVMs</title>   <subtitle>FileReaderFactory now explicitly decodes files as UTF-8 instead of using the platform default charset.</subtitle>   <facts>     <fact>FileReaderFactory.java constructs InputStreamReader with StandardCharsets.UTF_8.</fact>     <fact>FileReaderFactoryTest#utf8FileKeepsAccents pas... [truncated]
```

### refactor-invoice

```text
<observation>   <type>refactor</type>   <title>Refactored InvoiceService tax and rendering delegation</title>   <subtitle>InvoiceService now delegates tax calculation and invoice rendering to dedicated collaborators without changing behavior.</subtitle>   <facts>     <fact>src/main/java/com/acme/billing/InvoiceService.java calls renderer.render(taxCalculator.calculate(invoice), invoice).</fact>   ... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored AuthController JWT issuance into TokenService.issue</title>   <subtitle>AuthController delegates token creation to TokenService while existing controller tests remain green.</subtitle>   <facts>     <fact>AuthController now calls TokenService.issue(authentication) instead of constructing JWTs inline.</fact>     <fact>AuthControllerTest rema... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>change</type>   <title>Changed ConfigLoader to return an unmodifiable map</title>   <subtitle>ConfigLoader now exposes typed accessors backed by an unmodifiable properties map.</subtitle>   <facts>     <fact>ConfigLoader returns properties.entrySet().stream().collect(toUnmodifiableMap(...)).</fact>     <fact>ConfigLoader exposes typed accessors for configuration values.</fact... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Refactored RetryPolicy delay calculation into BackoffStrategy</title>   <subtitle>RetryPolicy now delegates backoff delay computation while preserving the established retry sequence.</subtitle>   <facts>     <fact>RetryPolicy returns backoff.nextDelay(attempt) for retry delay calculation.</fact>     <fact>RetryPolicyTest asserts the unchanged 100/200/... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>change</type>   <title>Changed UserRepository tenant lookup to JPQL @Query</title>   <subtitle>UserRepository now binds tenantId through JPQL instead of concatenating SQL.</subtitle>   <facts>     <fact>src/main/java/com/acme/users/UserRepository.java uses @Query("select u from User u where u.tenantId = :tenantId").</fact>     <fact>UserRepositoryTest returns the same tenant ... [truncated]
```

### refactor-event-bus

```text
<observation>   <type>refactor</type>   <title>Refactored EventBus dispatch to listener map by event type</title>   <subtitle>EventBus now dispatches events through listeners keyed by event type instead of an if/else chain.</subtitle>   <facts>     <fact>src/main/java/com/acme/events/EventBus.java uses listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event)) for dispatch.</f... [truncated]
```

### refactor-feature-flag

```text
<observation>   <type>refactor</type>   <title>Refactored FeatureFlagService evaluation to Flags.isEnabled</title>   <subtitle>FeatureFlagService now delegates flag evaluation while preserving its existing on/off behavior matrix.</subtitle>   <facts>     <fact>FeatureFlagService returns flags.isEnabled(flag, context) for feature flag evaluation.</fact>     <fact>FeatureFlagServiceTest retains the ... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles for exponential retry delays</title>   <subtitle>Parameterized tests verify attempt delays of 100, 200, and 400 milliseconds.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles covers attempts 1, 2, and 3 with expected delays of 100, 200, and 400 milliseconds.</fact>     <fact>RetryPolicyTest uses @Parameteri... [truncated]
```

### test-integration-order

```text
<observation>   <type>feature</type>   <title>Added OrderApiIT#createOrderReturns201 for order creation</title>   <subtitle>Integration test verifies successful order creation with HTTP 201 and a Location header.</subtitle>   <facts>     <fact>src/test/java/com/acme/orders/OrderApiIT.java uses @SpringBootTest(webEnvironment = RANDOM_PORT).</fact>     <fact>OrderApiIT#createOrderReturns201 posts a ... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable for CSV quoting edge cases</title>   <subtitle>Parameterized coverage verifies stable quoting for commas, quotes, and newlines.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable uses @ParameterizedTest with values "a,b", "a\"b", and "a\nb".</fact>     <fact>CsvExporterTest#quotingIsStable passes all 3 ... [truncated]
```

### test-security-headers

```text
<observation>   <type>feature</type>   <title>Added SecurityHeadersTest#responseHasCspAndNosniff for response security headers</title>   <subtitle>MockMvc tests verify CSP self-origin policy and MIME sniffing prevention.</subtitle>   <facts>     <fact>SecurityHeadersTest#responseHasCspAndNosniff requests GET / through MockMvc.</fact>     <fact>SecurityHeadersTest#responseHasCspAndNosniff expects C... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull for parallel cache reads</title>   <subtitle>A regression test reproduces intermittent null values returned by UserCache under concurrent access.</subtitle>   <facts>     <fact>src/test/java/com/acme/cache/CacheConcurrencyTest.java defines CacheConcurrencyTest#concurrentReadersNeverSeeNull.</fact... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Added fixed-clock SchedulerTest stability for 2026-11-03</title>   <subtitle>SchedulerTest replaced Thread.sleep with a deterministic fixed UTC clock and passed 20 consecutive runs.</subtitle>   <facts>     <fact>src/test/java/com/acme/scheduling/SchedulerTest.java sets scheduler.clock to Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UT... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed LogSanitizer credential redaction for password, token and secret</title>   <subtitle>LogSanitizer now masks credential values before log output, with regression coverage for all three keys.</subtitle>   <facts>     <fact>LogSanitizer redacts values matching password, token and secret key-value pairs as ***.</fact>     <fact>LogSanitizerTest#redact... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed UserDao.findByEmail SQL injection via parameter placeholder</title>   <subtitle>UserDao.findByEmail now queries with a JDBC placeholder, and the quote regression test passes.</subtitle>   <facts>     <fact>UserDao.findByEmail uses jdbcTemplate.query("select * from users where email = ?", mapper, email).</fact>     <fact>SqlInjectionTest#emailWithQ... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Fixed AdminController authorization for non-admin users</title>   <subtitle>Every AdminController endpoint now requires the ADMIN role, and unauthorized users receive HTTP 403.</subtitle>   <facts>     <fact>AdminController applies @PreAuthorize("hasRole('ADMIN')") to every endpoint.</fact>     <fact>AdminAuthorizationTest#userRoleGets403 passes after t... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsrfFilter mismatched-token rejection for state-changing requests</title>   <subtitle>CsrfFilter now returns HTTP 403 when the request token does not match the expected CSRF token.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/CsrfFilter.java calls response.sendError(403) when !csrfToken.matches(request).</fact>     <fact>CsrfFilter re... [truncated]
```

### security-secret-config

```text
<observation>   <type>change</type>   <title>Changed application.yml api-key to ACME_API_KEY environment variable</title>   <subtitle>Removed the literal API key from configuration and made SecretScanTest pass.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets api-key to ${ACME_API_KEY}.</fact>     <fact>src/main/resources/application.yml no longer contains the literal value s... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Fixed XmlImportService XXE via DOCTYPE declaration blocking</title>   <subtitle>XmlImportService now disables DOCTYPE declarations, and XxeTest#externalEntityIsRejected passes.</subtitle>   <facts>     <fact>XmlImportService sets http://apache.org/xml/features/disallow-doctype-decl to true.</fact>     <fact>XxeTest#externalEntityIsRejected passes with D... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Fixed FileDownloadController path traversal escape</title>   <subtitle>Download names are normalized and rejected when resolving outside the base directory.</subtitle>   <facts>     <fact>FileDownloadController resolves and normalizes the requested file name against base with Path.resolve(name).normalize().</fact>     <fact>FileDownloadController throws... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter empty token bucket response with 429</title>   <subtitle>RateLimiter now throws TooManyRequestsException when the token bucket cannot consume one token.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/RateLimiter.java throws TooManyRequestsException when bucket.tryConsume(1) returns false.</fact>     <fact>RateLimiter returns... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened SessionCookieConfig session cookie security flags</title>   <subtitle>Session cookies now use HttpOnly, Secure, and SameSite=Strict protections.</subtitle>   <facts>     <fact>SessionCookieConfig sets cookie.setHttpOnly(true).</fact>     <fact>SessionCookieConfig sets cookie.setSecure(true).</fact>     <fact>SessionCookieConfig sets cookie.setS... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Configured Hikari pool bounds in application.yml</title>   <subtitle>Hikari now permits 20 maximum connections and maintains 5 idle connections.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20.</fact>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.minimum... [truncated]
```

### config-flyway-baseline

```text
<observation>   <type>change</type>   <title>Changed Flyway baseline configuration in pom.xml</title>   <subtitle>Maven now baselines existing Flyway migrations at version 1 when migrating.</subtitle>   <facts>     <fact>pom.xml sets flyway.baselineVersion to 1.</fact>     <fact>pom.xml sets flyway.baselineOnMigrate to true.</fact>     <fact>Maven build resolves the new flyway.baselineVersion and ... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Changed root logger to INFO with JSON appender in logback-spring.xml</title>   <subtitle>Application logging now routes INFO-level root events through the JSON appender.</subtitle>   <facts>     <fact>src/main/resources/logback-spring.xml defines the root logger with level="INFO".</fact>     <fact>src/main/resources/logback-spring.xml routes root logger... [truncated]
```

### config-cors

```text
<observation>   <type>change</type>   <title>Changed CorsConfig /api/** CORS policy to app.acme.com</title>   <subtitle>CORS access now permits GET and POST from the production web origin while rejecting other origins.</subtitle>   <facts>     <fact>CorsConfig registry.addMapping("/api/**") allows the https://app.acme.com origin.</fact>     <fact>CorsConfig registry.addMapping("/api/**") allows on... [truncated]
```

### config-timeout

```text
<observation>   <type>change</type>   <title>Changed WebClientConfig response timeout to 5 seconds</title>   <subtitle>WebClient requests now fail after five seconds without a response, with configuration covered by a test.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/WebClientConfig.java configures HttpClient.create() with responseTimeout(Duration.ofSeconds(5)).</fact>     <fact>WebC... [truncated]
```

### config-feature-flags

```text
<observation>   <type>change</type>   <title>Changed feature-flags.yml checkout-v2 and legacy-search values</title>   <subtitle>Feature flag configuration now enables checkout-v2 and disables legacy-search.</subtitle>   <facts>     <fact>src/main/resources/feature-flags.yml sets checkout-v2 to true.</fact>     <fact>src/main/resources/feature-flags.yml sets legacy-search to false.</fact>     <fact... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added Dockerfile HEALTHCHECK for /actuator/health</title>   <subtitle>Container health checks now probe the actuator health endpoint every 30 seconds.</subtitle>   <facts>     <fact>Dockerfile defines HEALTHCHECK --interval=30s for http://localhost:8080/actuator/health.</fact>     <fact>The Dockerfile health check runs curl -f and exits with status 1 wh... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 to pom.xml</title>   <subtitle>Maven verification now generates JaCoCo XML coverage at target/site/jacoco/jacoco.xml.</subtitle>   <facts>     <fact>pom.xml adds org.jacoco:jacoco-maven-plugin with version 0.8.12.</fact>     <fact>mvn verify writes coverage output to target/site/jacoco/jacoco.xml.</fact>   </facts>   <na... [truncated]
```

### docs-api-observations

```text
<observation>   <type>change</type>   <title>Changed docs/API.md /api/observations pagination contract</title>   <subtitle>API documentation now identifies the hasMore and items pagination fields returned by the observations endpoint.</subtitle>   <facts>     <fact>docs/API.md documents GET /api/observations as returning hasMore and items.</fact>     <fact>The documented /api/observations paginati... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Changed README.md setup with JDK 21 and postgres</title>   <subtitle>README.md now documents a three-step local Spring Boot setup.</subtitle>   <facts>     <fact>README.md specifies Install JDK 21 as the first setup step.</fact>     <fact>README.md specifies docker compose up -d postgres as the second setup step.</fact>     <fact>README.md specifies mvn... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Added queue recovery steps to docs/runbook.md</title>   <subtitle>The runbook now documents queue inspection, worker restart, and health verification.</subtitle>   <facts>     <fact>docs/runbook.md instructs operators to check pending_messages.</fact>     <fact>docs/runbook.md instructs operators to restart the worker.</fact>     <fact>docs/runbook.md i... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Added CHANGELOG.md 2.4.0 release entry</title>   <subtitle>CHANGELOG.md now documents offline replay evaluation and the V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md adds a 2.4.0 release section dated 2026-11-03.</fact>     <fact>CHANGELOG.md records "Added offline replay evaluation" under version 2.4.0.</fact>     <fact>CHANGE... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>decision</type>   <title>Added ADR 0007 extracted fact validation</title>   <subtitle>ADR 0007 requires extracted facts to be validated against tool output before persistence.</subtitle>   <facts>     <fact>docs/adr/0007-extraction-validation.md records the decision to validate extracted facts against tool output before persistence.</fact>     <fact>docs/adr/0007-extraction-v... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Changed docs/MIGRATION.md V20 prerequisites</title>   <subtitle>Documented the mem_sessions backup and manual sentinel review requirement for V20 migration.</subtitle>   <facts>     <fact>docs/MIGRATION.md specifies backing up mem_sessions before running the V20 migration.</fact>     <fact>docs/MIGRATION.md requires app.manual_sentinel_reviewed=true whe... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Added SECURITY.md vulnerability disclosure policy</title>   <subtitle>SECURITY.md directs vulnerability reports to security@acme.com and prohibits public issue submissions.</subtitle>   <facts>     <fact>SECURITY.md specifies security@acme.com as the vulnerability reporting channel.</fact>     <fact>SECURITY.md states that vulnerabilities must not be re... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Changed CONTRIBUTING.md pull request verification requirement</title>   <subtitle>Contributors must run the local verification script before opening pull requests.</subtitle>   <facts>     <fact>CONTRIBUTING.md requires running ./scripts/verify-local.sh before opening a pull request.</fact>     <fact>CONTRIBUTING.md names four test layers in the local v... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added readinessProbe /api/health to deploy/deployment.yaml</title>   <subtitle>Deployment readiness now checks the health endpoint after a 15-second initial delay.</subtitle>   <facts>     <fact>deploy/deployment.yaml configures readinessProbe.httpGet.path as /api/health and readinessProbe.httpGet.port as 37777.</fact>     <fact>deploy/deployment.yaml c... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Changed .github/workflows/ci.yml to test JDK 21 and 25</title>   <subtitle>The CI workflow now runs the backend against both JDK 21 and JDK 25.</subtitle>   <facts>     <fact>.github/workflows/ci.yml sets strategy.matrix.java to [21, 25].</fact>     <fact>.github/workflows/ci.yml runs the backend on JDK 21 and JDK 25.</fact>   </facts>   <narrative>The ... [truncated]
```

### deploy-docker-multistage

```text
<observation>   <type>change</type>   <title>Changed Dockerfile runtime to eclipse-temurin:21-jre</title>   <subtitle>Dockerfile now separates Maven build and JRE runtime stages, reducing image size from 780MB to 210MB.</subtitle>   <facts>     <fact>Dockerfile defines a multi-stage build with a Maven build stage and eclipse-temurin:21-jre runtime stage.</fact>     <fact>Dockerfile copies /app/tar... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Hardened deploy role with AmazonS3ReadOnlyAccess</title>   <subtitle>The Terraform deploy role now grants read-only S3 access instead of full S3 permissions.</subtitle>   <facts>     <fact>infra/main.tf resource aws_iam_role_policy.deploy sets policy_arn to arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess.</fact>     <fact>aws_iam_role_policy.deploy no lo... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Changed infra/values.yaml replicaCount to 3 with 2Gi memory limit</title>   <subtitle>Helm deployment configuration now renders three replicas with a 2Gi memory cap per pod.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount to 3.</fact>     <fact>infra/values.yaml sets resources.limits.memory to 2Gi.</fact>     <fact>helm template rende... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added scripts/rollback.sh for deployment/claude-mem rollback</title>   <subtitle>The rollback script undoes the claude-mem deployment and waits for rollout completion.</subtitle>   <facts>     <fact>scripts/rollback.sh runs `kubectl rollout undo deployment/claude-mem`.</fact>     <fact>scripts/rollback.sh runs `kubectl rollout status deployment/claude-... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Changed canary rollout thresholds in infra/canary.yaml</title>   <subtitle>Canary traffic is enabled at 10 percent with rollback above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true.</fact>     <fact>infra/canary.yaml sets canary.weight to 10 for a 10 percent canary rollout.</fact>     <fact>infra/ca... [truncated]
```

### skip-view-logs

```text
<observation>   <type>discovery</type>   <title>Verified ClaudeMemApplication startup logs</title>   <subtitle>The latest application log shows successful startup with no recent errors.</subtitle>   <facts>     <fact>logs/app.log reports “Started ClaudeMemApplication in 4.2 seconds”.</fact>     <fact>logs/app.log contains no errors across the last 100 lines.</fact>   </facts>   <narrative>The appl... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title hit requires every declared identifier verbatim and keyword coverage ≥ 0.8; keywords are normalized for case, punctuation, CamelCase and a small synonym table.
- Facts/concepts recall is the primary field signal; precision counts produced items that mention an expected keyword. Trace-support rate is measured independently against the recorded tool input/output.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
