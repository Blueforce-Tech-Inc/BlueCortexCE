# Model output quality evaluation

- Date: 2026-11-03
- Phase: after
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 865181 ms; temperature: 0.0
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
| title exact-hit rate (legacy) | 50% |
| title normalized hit rate | 68% |
| title identifier hit rate | 87% |
| type accuracy | 87% |
| facts precision / recall | 66% / 91% |
| facts trace-support rate | 100% |
| concepts precision / recall | 58% / 77% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 7900 / 13091 / 17407 |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | facts P | facts R | format errors |
|---|---|---|---|---|---|---|
| 0 | 82 | 100% | 68% | 66% | 91% | 0 |
| 1 | 13 | 100% | 60% | 73% | 97% | 0 |
| 2 | 13 | 100% | 40% | 79% | 100% | 0 |

- average: skip 100%, title 56%, facts P/R 73%/96%
- worst run: skip 100%, title 40%, facts P/R 66%/91%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 11/12 | 9/12 |
| refactor | 9 | 9/9 | 8/9 | 6/9 |
| test-add | 9 | 9/9 | 8/9 | 9/9 |
| security-fix | 9 | 9/9 | 8/9 | 5/9 |
| config-change | 8 | 8/8 | 7/8 | 5/8 |
| docs-update | 8 | 8/8 | 7/8 | 4/8 |
| deploy-change | 7 | 7/7 | 5/7 | 4/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title exact | type | facts P/R | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 33%/50% | 11656 |
| bug-null-cache | bug-fix | observation | observation | pass | 75% | miss | match | 100%/100% | 67%/100% | 7899 |
| bug-timezone-report | bug-fix | observation | observation | pass | 67% | miss | MISS | 67%/100% | 67%/100% | 11045 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | match | 50%/100% | 100%/100% | 8469 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 11773 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | miss | match | 67%/67% | 67%/100% | 15004 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 50% | miss | match | 50%/50% | 67%/100% | 13496 |
| bug-race-counter | bug-fix | observation | observation | pass | 75% | miss | match | 67%/100% | 33%/50% | 11208 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 75% | miss | match | 67%/100% | 33%/50% | 9517 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | match | 75%/100% | 67%/100% | 7894 |
| bug-regex-redos | bug-fix | observation | observation | pass | 75% | miss | match | 67%/100% | 33%/50% | 6349 |
| bug-file-encoding | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 8026 |
| refactor-invoice | refactor | observation | observation | pass | 67% | miss | match | 67%/100% | 67%/100% | 12802 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 11224 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | miss | MISS | 67%/100% | 67%/100% | 9298 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 13044 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 10229 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | match | 67%/100% | 100%/100% | 8427 |
| refactor-sql-repository | refactor | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 8182 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | match | 33%/50% | 67%/100% | 8789 |
| refactor-feature-flag | refactor | observation | observation | pass | 67% | miss | match | 100%/100% | 100%/100% | 8381 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | match | 50%/100% | 67%/100% | 10835 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 9144 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | match | 33%/50% | 50%/50% | 12076 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | match | 50%/100% | 67%/100% | 8486 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | match | 33%/50% | 100%/100% | 6495 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | match | 60%/100% | 67%/100% | 6056 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | match | 75%/100% | 33%/50% | 6426 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | match | 33%/100% | 67%/100% | 9408 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | miss | MISS | 100%/67% | 67%/100% | 10825 |
| security-redact-log | security-fix | observation | observation | pass | 67% | miss | match | 67%/100% | 33%/50% | 17407 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | hit | match | 33%/50% | 67%/100% | 5711 |
| security-authz-admin | security-fix | observation | observation | pass | 33% | miss | MISS | 100%/100% | 0%/0% | 8155 |
| security-csrf | security-fix | observation | observation | pass | 100% | hit | match | 50%/50% | 33%/50% | 12306 |
| security-secret-config | security-fix | observation | observation | pass | 100% | hit | match | 50%/50% | 50%/50% | 10553 |
| security-xxe | security-fix | observation | observation | pass | 67% | miss | match | 100%/100% | 67%/100% | 8127 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | hit | match | 50%/100% | 67%/100% | 11245 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | miss | match | 67%/100% | 33%/50% | 8248 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | miss | match | 50%/100% | 33%/50% | 6673 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | miss | match | 67%/100% | 50%/50% | 6088 |
| config-flyway-baseline | config-change | observation | observation | pass | 75% | miss | match | 67%/100% | 33%/50% | 7900 |
| config-log-level | config-change | observation | observation | pass | 50% | miss | match | 67%/100% | 50%/50% | 10025 |
| config-cors | config-change | observation | observation | pass | 100% | hit | match | 67%/100% | 33%/50% | 11169 |
| config-timeout | config-change | observation | observation | pass | 75% | miss | MISS | 50%/100% | 33%/50% | 7865 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 5562 |
| config-docker-healthcheck | config-change | observation | observation | pass | 75% | miss | match | 100%/100% | 50%/50% | 8343 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 6653 |
| docs-api-observations | docs-update | observation | observation | pass | 75% | miss | match | 50%/100% | 50%/50% | 5652 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | miss | match | 75%/100% | 50%/50% | 5657 |
| docs-runbook | docs-update | observation | observation | pass | 100% | hit | match | 33%/100% | 100%/100% | 8420 |
| docs-changelog | docs-update | observation | observation | pass | 67% | miss | match | 100%/100% | 50%/50% | 7184 |
| docs-architecture-adr | docs-update | observation | observation | pass | 75% | miss | MISS | 33%/100% | 33%/50% | 6774 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | miss | match | 50%/50% | 67%/100% | 6930 |
| docs-security-policy | docs-update | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 5480 |
| docs-contributing | docs-update | observation | observation | pass | 67% | miss | match | 100%/100% | 100%/100% | 7576 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | hit | match | 67%/100% | 50%/50% | 5444 |
| deploy-github-actions | deploy-change | observation | observation | pass | 100% | hit | match | 50%/100% | 50%/50% | 9517 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | match | 33%/100% | 67%/100% | 8492 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 75% | miss | match | 67%/100% | 67%/100% | 13091 |
| deploy-helm-values | deploy-change | observation | observation | pass | 50% | miss | match | 67%/100% | 50%/50% | 5521 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 75% | miss | MISS | 67%/100% | 67%/100% | 7267 |
| deploy-canary | deploy-change | observation | observation | pass | 50% | miss | MISS | 0%/0% | 33%/50% | 10725 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | 2774 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | 3166 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | 2270 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | 3917 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | 1583 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | 3722 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | 13450 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | 3793 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | 3880 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | 4352 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | 5631 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | 2899 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | 7928 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | 6354 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | 2511 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | 4151 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | 3329 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | 2226 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | 2093 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | 3243 |

## Problems

- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [bugfix, problem-solution, gotcha])
- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed concurrent null placeholder race in UserCache.get')
- bug-timezone-report: title coverage 67% (expected keywords [fix, timezone, dailyreport])
- bug-timezone-report: type 'feature' != expected 'bugfix'
- bug-jwt-expiry: facts recall 67% (expected [expiration, isEqual, rejects], got [JwtVerifier.java changed the expiration check from `claims.getExpiration().before(now)` to `claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now)`., Under the old condition, a token whose expiration was exactly equal to the current instant was treated as valid because `.before(now)` returns false at equality., JwtVerifierTest#expiredAtExactBoundary passes with the updated condition.])
- bug-csv-crlf: title coverage 50% (expected keywords [fix, csv, line, endings])
- bug-csv-crlf: facts recall 50% (expected [CRLF, multilineFieldKeepsRowCount], got [CsvExporter.java line writing changed to normalize embedded line endings via `line.replaceAll("\\r?\\n", "\\r\\n")` before writing., CsvExporterTest#multilineFieldKeepsRowCount now passes, confirming row count is preserved for multiline fields.])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha, what-changed])
- bug-n-plus-one: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, how-it-works, gotcha])
- bug-regex-redos: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, security, what-changed])
- refactor-invoice: title coverage 67% (expected keywords [refactor, invoiceservice, delegates])
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: type 'feature' != expected 'refactor'
- refactor-event-bus: facts recall 50% (expected [listener map, event type], got [EventBus.java replaced its if/else dispatch chain with listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event))., Listener lookup is keyed by event type, removing the sequential conditional dispatch., EventBusTest continues to dispatch the same three events as before the change.])
- refactor-feature-flag: title coverage 67% (expected keywords [refactor, featureflagservice, delegates])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles uses @ParameterizedTest with @CsvSource values "1,100", "2,200", "3,400", Test asserts policy.delay(attempt) equals 100 ms for attempt 1, 200 ms for attempt 2, and 400 ms for attempt 3, All 3 parameterized test cases pass after adding RetryPolicyTest])
- test-unit-retry: concepts recall 50% (expected [what-changed, pattern], got [how-it-works, pattern])
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable is a @ParameterizedTest using @ValueSource(strings = {"a,b","a\"b","a\nb"})., The three test inputs cover comma, double-quote, and newline characters., All 3 parameterized cases pass.])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern, how-it-works])
- test-flaky-clock: type 'bugfix' != expected 'feature'
- test-flaky-clock: facts recall 67% (expected [Clock.fixed, Thread.sleep, 20 consecutive], got [SchedulerTest now sets scheduler.clock to Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)., After the change, SchedulerTest passes 20 consecutive runs without flakiness.])
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, problem-solution, pattern])
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail previously built its query by concatenating the email value directly into the SQL string, Query now uses `jdbcTemplate.query("select * from users where email = ?", mapper, email)` with a bound parameter, SqlInjectionTest#emailWithQuoteReturnsEmpty now passes after the change])
- security-authz-admin: title coverage 33% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: type 'feature' != expected 'bugfix'
- security-authz-admin: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, how-it-works, pattern])
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter.java now checks csrfToken.matches(request) and returns 403 on mismatch, CsrfFilterTest#postWithoutTokenIs403 passes, confirming rejection of requests without valid token])
- security-csrf: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, how-it-works])
- security-secret-config: title lost identifiers [application.yml, ACME_API_KEY] (got 'Removed hardcoded API key from application.yml config')
- security-secret-config: facts recall 50% (expected [ACME_API_KEY, SecretScanTest], got [application.yml previously contained hardcoded api-key value sk-live-123, application.yml now references environment variable ${ACME_API_KEY} instead of literal secret])
- security-secret-config: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, what-changed, gotcha])
- security-cookie-httponly: concepts recall 50% (expected [problem-solution, gotcha], got [security, what-changed, gotcha])
- config-hikari-pool: title lost identifiers [maximum-pool-size] (got 'Adjusted Hikari connection pool size in application.yml')
- config-hikari-pool: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-flyway-baseline: title lost identifiers [pom.xml] (got 'Configured Flyway baselineOnMigrate with baselineVersion 1')
- config-flyway-baseline: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, why-it-exists, pattern])
- config-log-level: title lost identifiers [logback-spring.xml] (got 'Configured root logger to INFO level with JSON appender')
- config-log-level: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-cors: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, security, configuration])
- config-timeout: type 'feature' != expected 'change'
- config-timeout: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern, how-it-works])
- config-feature-flags: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-api-observations: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- docs-changelog: title lost identifiers [CHANGELOG.md] (got 'Added 2.4.0 changelog entry documenting offline replay evaluation and V20 migration fix')
- docs-changelog: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- docs-architecture-adr: title lost identifiers [0007-extraction-validation.md] (got 'Added ADR 0007 documenting extraction validation decision')
- docs-architecture-adr: type 'change' != expected 'decision'
- docs-architecture-adr: concepts recall 50% (expected [why-it-exists, trade-off], got [why-it-exists, pattern, what-changed])
- docs-migration-guide: facts recall 50% (expected [mem_sessions, app.manual_sentinel_reviewed], got [docs/MIGRATION.md now contains a V20 section documenting migration prerequisites., V20 migration requires setting app.manual_sentinel_reviewed=true prior to running.])
- docs-contributing: title coverage 67% (expected keywords [update, contributing, verify-local])
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- deploy-terraform-iam: title lost identifiers [main.tf] (got 'Restricted aws_iam_role_policy deploy to AmazonS3ReadOnlyAccess')
- deploy-helm-values: title coverage 50% (expected keywords [change, helm, replicas, memory])
- deploy-helm-values: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 50% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: type 'feature' != expected 'change'
- deploy-canary: facts recall 0% (expected [10 percent, 2 percent], got [infra/canary.yaml configures canary.enabled as true with weight set to 10, Helm lint validation passes for the canary configuration])
- deploy-canary: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, pattern])

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed pagination offset off-by-one in OrderQuery.pageOffset</title>   <subtitle>OrderQuery.pageOffset no longer subtracts 1 from page * size, correcting pagination start index.</subtitle>   <facts>     <fact>OrderQuery.pageOffset previously returned `page * size - 1`, causing the first page (page=0) to start at offset -1 instead of 0.</fact>     <fact>O... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed concurrent null placeholder race in UserCache.get</title>   <subtitle>Replaced cache.get with computeIfAbsent to prevent duplicate concurrent loads and null placeholder writes.</subtitle>   <facts>     <fact>UserCache.get previously used cache.get(id), allowing two concurrent callers to both observe a miss and both write a null placeholder.</fact>... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai</title>   <subtitle>Test reproduces off-by-one-day bug by pinning UTC timestamp and asserting Shanghai business date</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins input 2026-11-01T23:30Z and expects business date 2026-11-02</fact>     <fact>Test validate... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed token expiration boundary check in JwtVerifier</title>   <subtitle>JwtVerifier now rejects tokens whose expiration equals the current instant instead of accepting them.</subtitle>   <facts>     <fact>JwtVerifier.java changed the expiration check from `claims.getExpiration().before(now)` to `claims.getExpiration().isBefore(now) || claims.getExpirat... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed CSV multiline field row count in CsvExporter</title>   <subtitle>Embedded line endings in CSV fields are now normalized to CRLF before quoting to preserve correct row counts.</subtitle>   <facts>     <fact>CsvExporter.java line writing changed to normalize embedded line endings via `line.replaceAll("\\r?\\n", "\\r\\n")` before writing.</fact>     ... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed race condition in CounterService.increment</title>   <subtitle>CounterService.increment now uses AtomicLong.incrementAndGet instead of a non-atomic get/set pair.</subtitle>   <facts>     <fact>CounterService.increment previously used `counter.set(counter.get() + 1)` followed by `counter.get()`, which is not atomic.</fact>     <fact>CounterService.... [truncated]
```

### bug-n-plus-one

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed N+1 query in OrderRepository.findAllByCustomerId</title>   <subtitle>Order items now fetched in the same query using @EntityGraph, eliminating N+1 SQL statements.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId annotated with @EntityGraph(attributePaths = "items")</fact>     <fact>QueryCountTest verifies the query count d... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed ReDoS in UrlValidator URL pattern</title>   <subtitle>Replaced nested-quantifier regex with a linear pattern to prevent catastrophic backtracking on long inputs.</subtitle>   <facts>     <fact>UrlValidator.java previously used a regex with a nested quantifier, causing catastrophic backtracking on long inputs.</fact>     <fact>UrlValidator now comp... [truncated]
```

### refactor-invoice

```text
<observation>   <type>refactor</type>   <title>Refactored InvoiceService to delegate to TaxCalculator, InvoiceRenderer, MailGateway</title>   <subtitle>InvoiceService became a thin coordinator delegating tax, rendering and mail to three collaborators without behaviour change.</subtitle>   <facts>     <fact>InvoiceService.java now calls `renderer.render(taxCalculator.calculate(invoice), invoice)`.<... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>feature</type>   <title>Added unmodifiable map and typed accessors to ConfigLoader</title>   <subtitle>ConfigLoader now returns an unmodifiable map and exposes typed accessors instead of raw property access</subtitle>   <facts>     <fact>ConfigLoader.java changed to return an unmodifiable map via `toUnmodifiableMap` collector on `properties.entrySet().stream()`</fact>     <fa... [truncated]
```

### refactor-event-bus

```text
```xml <observation>   <type>refactor</type>   <title>Refactored EventBus dispatch to listener map</title>   <subtitle>EventBus now dispatches events via a type-keyed listener map instead of an if/else chain.</subtitle>   <facts>     <fact>EventBus.java replaced its if/else dispatch chain with listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event)).</fact>     <fact>Listene... [truncated]
```

### refactor-feature-flag

```text
```xml <observation>   <type>refactor</type>   <title>Refactored FeatureFlagService to delegate to Flags.isEnabled</title>   <subtitle>FeatureFlagService now delegates flag evaluation to Flags.isEnabled while preserving existing on/off matrix behavior.</subtitle>   <facts>     <fact>FeatureFlagService.java in src/main/java/com/acme/flags/ was updated to delegate evaluation to Flags.isEnabled(flag,... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized delay test</title>   <subtitle>Parameterized test verifies exponential backoff delay doubles across attempts 1-3 with 100/200/400 ms</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles uses @ParameterizedTest with @CsvSource values "1,100", "2,200", "3,400"</fact>     <fact>Test asserts policy.de... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable parameterized quoting tests</title>   <subtitle>Parameterized test verifies CSV quoting handles commas, quotes, and newlines.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable is a @ParameterizedTest using @ValueSource(strings = {"a,b","a\"b","a\nb"}).</fact>     <fact>The three test inputs cover comma... [truncated]
```

### test-repro-null-cache

```text
```xml <observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull reproducing UserCache null</title>   <subtitle>New concurrent test with 1000 parallel readers reproduces the intermittent null returned by UserCache.get.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull runs 1000 parallel readers via IntStream.range(0, 1000... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>bugfix</type>   <title>Fixed SchedulerTest flakiness by replacing Thread.sleep with Clock.fixed</title>   <subtitle>SchedulerTest used Thread.sleep for timing-dependent assertions, causing intermittent failures.</subtitle>   <facts>     <fact>SchedulerTest previously used Thread.sleep to wait for scheduler ticks, making assertions timing-dependent and flaky.</fact>     <fact>... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed credential redaction in LogSanitizer</title>   <subtitle>LogSanitizer now masks password, token and secret values before they are written to logs.</subtitle>   <facts>     <fact>LogSanitizer applies regex `(password|token|secret)=\S+` and replaces matches with `$1=***` so credential values never reach log output.</fact>     <fact>LogSanitizerTest#... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed SQL injection in UserDao.findByEmail</title>   <subtitle>Replaced string concatenation with a JDBC parameter placeholder to prevent SQL injection.</subtitle>   <facts>     <fact>UserDao.findByEmail previously built its query by concatenating the email value directly into the SQL string</fact>     <fact>Query now uses `jdbcTemplate.query("select * ... [truncated]
```

### security-authz-admin

```text
```xml <observation>   <type>feature</type>   <title>Added @PreAuthorize ROLE_ADMIN to AdminController</title>   <subtitle>Every endpoint in AdminController now requires ROLE_ADMIN, enforced via method-level security.</subtitle>   <facts>     <fact>AdminController.java annotated with @PreAuthorize("hasRole('ADMIN')") at class level, applying to all endpoints.</fact>     <fact>AdminAuthorizationTes... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Fixed CSRF token validation in CsrfFilter</title>   <subtitle>CsrfFilter now rejects state-changing requests with mismatched tokens by returning 403.</subtitle>   <facts>     <fact>CsrfFilter.java now checks csrfToken.matches(request) and returns 403 on mismatch</fact>     <fact>CsrfFilterTest#postWithoutTokenIs403 passes, confirming rejection of reques... [truncated]
```

### security-secret-config

```text
```xml <observation>   <type>bugfix</type>   <title>Removed hardcoded API key from application.yml config</title>   <subtitle>Replaced literal secret sk-live-123 with environment variable reference ${ACME_API_KEY} in application.yml.</subtitle>   <facts>     <fact>application.yml previously contained hardcoded api-key value sk-live-123</fact>     <fact>application.yml now references environment va... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Fixed XXE vulnerability in XmlImportService DOCTYPE handling</title>   <subtitle>XmlImportService now disables DOCTYPE declarations to prevent XML External Entity attacks.</subtitle>   <facts>     <fact>XmlImportService.java added factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) to the XML parser factory</fact>     <fact>... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter to return 429 on exhausted token bucket</title>   <subtitle>RateLimiter now throws TooManyRequestsException when no tokens remain in the bucket.</subtitle>   <facts>     <fact>RateLimiter.java at src/main/java/com/acme/web/RateLimiter.java previously allowed requests through even when the token bucket was empty.</fact>     <fact>Added ... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>Session cookie now sets HttpOnly, Secure and SameSite=Strict to mitigate XSS and CSRF exposure.</subtitle>   <facts>     <fact>SessionCookieConfig.java sets the session cookie to HttpOnly, Secure, and SameSite=Strict.</fact>     <fact>SessionCookieTest#flagsArePresent passes, confi... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Adjusted Hikari connection pool size in application.yml</title>   <subtitle>Configured HikariCP maximum-pool-size to 20 and minimum-idle to 5 for database connection pooling.</subtitle>   <facts>     <fact>spring.datasource.hikari.maximum-pool-size set to 20 in src/main/resources/application.yml</fact>     <fact>spring.datasource.hikari.minimum-idle set... [truncated]
```

### config-flyway-baseline

```text
```xml <observation>   <type>change</type>   <title>Configured Flyway baselineOnMigrate with baselineVersion 1</title>   <subtitle>Added Flyway baseline properties to pom.xml so migrations baseline existing databases instead of failing.</subtitle>   <facts>     <fact>pom.xml now defines property flyway.baselineVersion with value 1.</fact>     <fact>pom.xml now defines property flyway.baselineOnMig... [truncated]
```

### config-log-level

```text
```xml <observation>   <type>change</type>   <title>Configured root logger to INFO level with JSON appender</title>   <subtitle>logback-spring.xml now references JSON appender at INFO level for root logger.</subtitle>   <facts>     <fact>logback-spring.xml root logger level changed to INFO</fact>     <fact>logback-spring.xml root logger now references the JSON appender</fact>     <fact>LogbackConf... [truncated]
```

### config-cors

```text
<observation>   <type>change</type>   <title>Restricted CORS origins for /api/** in CorsConfig</title>   <subtitle>CorsConfig now allows only https://app.acme.com with GET and POST on /api/** endpoints.</subtitle>   <facts>     <fact>CorsConfig.registry.addMapping("/api/**") restricts allowed origins to https://app.acme.com.</fact>     <fact>Only GET and POST methods are permitted for /api/** mapp... [truncated]
```

### config-timeout

```text
<observation>   <type>feature</type>   <title>Added 5 second response timeout to WebClientConfig</title>   <subtitle>WebClient HTTP calls now enforce a 5 second response timeout via HttpClient configuration.</subtitle>   <facts>     <fact>WebClientConfig sets HttpClient.create().responseTimeout(Duration.ofSeconds(5))</fact>     <fact>WebClientConfigTest reads the timeout value from the bean to ver... [truncated]
```

### config-feature-flags

```text
```xml <observation>   <type>change</type>   <title>Enabled checkout-v2 flag and disabled legacy-search in feature-flags.yml</title>   <subtitle>Feature flag configuration switched checkout-v2 to true and legacy-search to false.</subtitle>   <facts>     <fact>src/main/resources/feature-flags.yml now contains `checkout-v2: true`.</fact>     <fact>src/main/resources/feature-flags.yml now contains `l... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added HEALTHCHECK directive to Dockerfile</title>   <subtitle>Dockerfile now performs container health checks every 30 seconds against Spring Boot actuator health endpoint.</subtitle>   <facts>     <fact>Dockerfile contains HEALTHCHECK with 30s interval invoking curl against http://localhost:8080/actuator/health</fact>     <fact>docker build succeeds af... [truncated]
```

### config-maven-plugin

```text
```xml <observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 to pom.xml</title>   <subtitle>JaCoCo code coverage plugin added to Maven build producing XML reports.</subtitle>   <facts>     <fact>pom.xml now declares org.jacoco:jacoco-maven-plugin version 0.8.12 as a plugin</fact>     <fact>mvn verify now writes coverage output to target/site/jacoco/jacoco.xml</fact>     <fa... [truncated]
```

### docs-api-observations

```text
<observation>   <type>change</type>   <title>Added /api/observations pagination docs in API.md</title>   <subtitle>Documented that GET /api/observations returns hasMore and items pagination fields.</subtitle>   <facts>     <fact>docs/API.md now states GET /api/observations returns hasMore and items fields</fact>     <fact>The documented pagination contract matches the controller implementation</fa... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Added local setup instructions to README.md</title>   <subtitle>README now documents three-step local setup for the Spring Boot project.</subtitle>   <facts>     <fact>README.md gained a "## Setup" section with three steps</fact>     <fact>Step 1 installs JDK 21</fact>     <fact>Step 2 runs `docker compose up -d postgres`</fact>     <fact>Step 3 runs `m... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Added 2.4.0 changelog entry documenting offline replay evaluation and V20 migration fix</title>   <subtitle>CHANGELOG.md updated with version 2.4.0 released 2026-11-03 covering two changes.</subtitle>   <facts>     <fact>CHANGELOG.md adds a `## 2.4.0 - 2026-11-03` section.</fact>     <fact>The 2.4.0 entry lists "Added offline replay evaluation" as a new... [truncated]
```

### docs-architecture-adr

```text
```xml <observation>   <type>change</type>   <title>Added ADR 0007 documenting extraction validation decision</title>   <subtitle>Architecture decision record added to enforce validation of extracted facts against tool output before persistence.</subtitle>   <facts>     <fact>ADR 0007 was created at docs/adr/0007-extraction-validation.md</fact>     <fact>ADR 0007 documents the decision to validate... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisites in MIGRATION.md</title>   <subtitle>Added V20 section requiring mem_sessions backup and app.manual_sentinel_reviewed flag before running migration.</subtitle>   <facts>     <fact>docs/MIGRATION.md now contains a V20 section documenting migration prerequisites.</fact>     <fact>V20 migration requires backing up the ... [truncated]
```

### docs-contributing

```text
```xml <observation>   <type>change</type>   <title>Added verify-local.sh requirement to CONTRIBUTING.md</title>   <subtitle>CONTRIBUTING.md now requires running ./scripts/verify-local.sh before opening a pull request and names four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md was edited to require `./scripts/verify-local.sh` before opening a pull request.</fact>     <fact>The updat... [truncated]
```

### deploy-k8s-probe

```text
```xml <observation>   <type>change</type>   <title>Added readinessProbe to deploy/deployment.yaml on /api/health:37777</title>   <subtitle>Deployment manifest now includes HTTP readiness probe with 15-second initial delay.</subtitle>   <facts>     <fact>deploy/deployment.yaml adds a readinessProbe using httpGet against path /api/health and port 37777.</fact>     <fact>The readinessProbe sets init... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Added JDK 21 and 25 matrix strategy to ci.yml</title>   <subtitle>CI workflow now runs backend tests on both JDK 21 and 25 versions</subtitle>   <facts>     <fact>.github/workflows/ci.yml now includes a strategy matrix with java versions [21, 25]</fact>     <fact>Backend CI runs parallel jobs for each JDK version to ensure compatibility</fact>     <fact... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted aws_iam_role_policy deploy to AmazonS3ReadOnlyAccess</title>   <subtitle>Infrastructure deploy role downgraded from full S3 access to read-only S3 permissions.</subtitle>   <facts>     <fact>infra/main.tf resource aws_iam_role_policy with name deploy now sets policy_arn to arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess.</fact>     <fact>The d... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Scaled infra replicas to 3 with 2Gi memory limit in values.yaml</title>   <subtitle>Updated Helm values to run three replicas with a 2Gi memory cap, confirmed by helm template rendering three pods.</subtitle>   <facts>     <fact>infra/values.yaml replicaCount changed from previous value to 3.</fact>     <fact>infra/values.yaml resources.limits.memory se... [truncated]
```

### deploy-rollback-script

```text
```xml <observation>   <type>feature</type>   <title>Added rollback.sh for claude-mem deployment</title>   <subtitle>New scripts/rollback.sh undoes the claude-mem rollout and waits for completion.</subtitle>   <facts>     <fact>scripts/rollback.sh executes kubectl rollout undo deployment/claude-mem then kubectl rollout status deployment/claude-mem.</fact>     <fact>The script starts with set -euo ... [truncated]
```

### deploy-canary

```text
```xml <observation>   <type>feature</type>   <title>Enabled canary deployment in infra/canary.yaml</title>   <subtitle>Canary releases now roll out to 10% of traffic and auto-rollback above 2% error rate</subtitle>   <facts>     <fact>infra/canary.yaml configures canary.enabled as true with weight set to 10</fact>     <fact>Canary configuration sets errorThreshold to 0.02 for automatic rollback t... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title hit requires every declared identifier verbatim and keyword coverage ≥ 0.67; keywords are normalized for case, punctuation, CamelCase and a small synonym table.
- Facts/concepts recall is the primary field signal; precision counts produced items that mention an expected keyword. Trace-support rate is measured independently against the recorded tool input/output.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
