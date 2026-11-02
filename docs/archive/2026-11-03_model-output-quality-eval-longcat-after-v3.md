# Model output quality evaluation

- Date: 2026-11-03
- Phase: current
- Provider: openai, model `deepseek-chat`, base URL `replay`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 0 (cap none); duration: 296 ms; temperature: 0.0
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
| title exact-hit rate (legacy) | 56% |
| title normalized hit rate | 60% |
| title identifier hit rate | 100% |
| type accuracy | 90% |
| facts precision / recall | 73% / 92% |
| facts trace-support rate | 100% |
| concepts precision / recall | 65% / 71% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 8739 / 13110 / 18197 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts precision | >= 75% | 73% | **FAIL** |
| A6 | normalized title hit | >= 80% | 60% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | facts P | facts R | format errors |
|---|---|---|---|---|---|---|
| 0 | 82 | 100% | 60% | 73% | 92% | 0 |
| 1 | 13 | 100% | 60% | 87% | 100% | 0 |
| 2 | 13 | 100% | 70% | 85% | 97% | 0 |

- average: skip 100%, title 63%, facts P/R 82%/96%
- worst run: skip 100%, title 60%, facts P/R 73%/92%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 12/12 | 10/12 |
| refactor | 9 | 9/9 | 7/9 | 7/9 |
| test-add | 9 | 9/9 | 9/9 | 8/9 |
| security-fix | 9 | 9/9 | 7/9 | 3/9 |
| config-change | 8 | 8/8 | 7/8 | 4/8 |
| docs-update | 8 | 8/8 | 8/8 | 2/8 |
| deploy-change | 7 | 7/7 | 6/7 | 3/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title exact | type | facts P/R | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 100%/100% | 8413 |
| bug-null-cache | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 9138 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 100%/100% | 13074 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 100%/100% | 11869 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 50%/50% | 7646 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | miss | match | 67%/67% | 67%/100% | 18197 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 10985 |
| bug-race-counter | bug-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 33%/50% | 10452 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 10310 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | match | 100%/100% | 67%/100% | 11186 |
| bug-regex-redos | bug-fix | observation | observation | pass | 100% | hit | match | 50%/50% | 100%/100% | 8176 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | miss | match | 100%/100% | 100%/100% | 8098 |
| refactor-invoice | refactor | observation | observation | pass | 100% | miss | match | 67%/100% | 100%/100% | 9868 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | match | 100%/100% | 100%/100% | 5080 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | miss | MISS | 67%/100% | 100%/100% | 10204 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | match | 50%/100% | 100%/100% | 7493 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | match | 67%/100% | 100%/100% | 8524 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | match | 100%/100% | 100%/100% | 8739 |
| refactor-sql-repository | refactor | observation | observation | pass | 67% | miss | MISS | 67%/100% | 50%/50% | 12959 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | match | 33%/100% | 100%/100% | 13110 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | miss | match | 100%/100% | 100%/100% | 9101 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 16472 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | match | 67%/100% | 67%/100% | 13843 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | match | 33%/50% | 50%/50% | 10776 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | match | 50%/100% | 100%/100% | 10046 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | match | 67%/50% | 67%/100% | 8926 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | match | 75%/100% | 100%/100% | 12948 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | match | 100%/100% | 0%/0% | 11592 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | match | 67%/100% | 100%/100% | 8616 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | miss | match | 100%/67% | 100%/100% | 11282 |
| security-redact-log | security-fix | observation | observation | pass | 67% | miss | match | 67%/100% | 50%/50% | 12332 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | hit | match | 33%/50% | 67%/100% | 6254 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | miss | match | 100%/100% | 33%/50% | 16838 |
| security-csrf | security-fix | observation | observation | pass | 67% | miss | MISS | 67%/50% | 0%/0% | 9975 |
| security-secret-config | security-fix | observation | observation | pass | 75% | miss | match | 67%/100% | 33%/50% | 12368 |
| security-xxe | security-fix | observation | observation | pass | 67% | miss | match | 100%/100% | 67%/100% | 11576 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | hit | match | 67%/100% | 50%/50% | 10105 |
| security-rate-limit | security-fix | observation | observation | pass | 100% | hit | MISS | 50%/50% | 50%/50% | 11830 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | miss | match | 50%/100% | 33%/50% | 8779 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | miss | match | 67%/100% | 50%/50% | 6957 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | match | 67%/100% | 50%/50% | 10559 |
| config-log-level | config-change | observation | observation | pass | 75% | miss | match | 67%/100% | 50%/50% | 11710 |
| config-cors | config-change | observation | observation | pass | 75% | miss | match | 67%/100% | 50%/50% | 11684 |
| config-timeout | config-change | observation | observation | pass | 75% | miss | MISS | 50%/100% | 50%/50% | 7389 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 6389 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 8379 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 6652 |
| docs-api-observations | docs-update | observation | observation | pass | 75% | miss | match | 50%/100% | 50%/50% | 8702 |
| docs-readme-setup | docs-update | observation | observation | pass | 75% | miss | match | 100%/100% | 50%/50% | 9377 |
| docs-runbook | docs-update | observation | observation | pass | 100% | hit | match | 50%/100% | 100%/100% | 7241 |
| docs-changelog | docs-update | observation | observation | pass | 67% | miss | match | 100%/100% | 50%/50% | 12112 |
| docs-architecture-adr | docs-update | observation | observation | pass | 75% | miss | match | 33%/100% | 50%/50% | 12169 |
| docs-migration-guide | docs-update | observation | observation | pass | 50% | miss | match | 50%/50% | 50%/50% | 7547 |
| docs-security-policy | docs-update | observation | observation | pass | 100% | hit | match | 50%/50% | 50%/50% | 6858 |
| docs-contributing | docs-update | observation | observation | pass | 67% | miss | match | 100%/100% | 50%/50% | 9678 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | hit | match | 67%/100% | 50%/50% | 11423 |
| deploy-github-actions | deploy-change | observation | observation | pass | 100% | hit | match | 100%/100% | 50%/50% | 11065 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 75% | miss | match | 33%/100% | 67%/100% | 10340 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 50% | miss | match | 100%/50% | 50%/50% | 8721 |
| deploy-helm-values | deploy-change | observation | observation | pass | 25% | miss | match | 67%/100% | 50%/50% | 7453 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | MISS | 67%/100% | 100%/100% | 9013 |
| deploy-canary | deploy-change | observation | observation | pass | 75% | miss | match | 67%/100% | 50%/50% | 10222 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | 2025 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | 2608 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | 3248 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | 7800 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | 2236 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | 4448 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | 2611 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | 3599 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | 2182 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | 3730 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | 3532 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | 2167 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | 3429 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | 4849 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | 2871 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | 1759 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | 2767 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | 4822 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | 4514 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | 2272 |

## Problems

- bug-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- bug-connection-leak: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-jwt-expiry: facts recall 67% (expected [expiration, isEqual, rejects], got [src/main/java/com/acme/auth/JwtVerifier.java changed the expiry check from claims.getExpiration().before(now) to claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now)., Before the change, a token whose claims.getExpiration() equaled now was treated as still valid., JwtVerifierTest#expiredAtExactBoundary passes after the change.])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha, how-it-works])
- bug-regex-redos: facts recall 50% (expected [linear pattern, longInputFinishesUnder100ms], got [src/main/java/com/acme/validation/UrlValidator.java now compiles the constant Pattern URL as `^https?://[^\s]+$`., UrlValidatorTest#longInputFinishesUnder100ms passes after the change.])
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: type 'feature' != expected 'refactor'
- refactor-sql-repository: title coverage 67% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, what-changed])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles uses @CsvSource with pairs "1,100", "2,200", "3,400" to verify attempt-to-delay mapping., policy.delay(attempt) returns 100 ms for attempt 1, 200 ms for attempt 2, and 400 ms for attempt 3., RetryPolicyTest.java located at src/test/java/com/acme/resilience/RetryPolicyTest.java contains 3 passing parameterized test cases.])
- test-unit-retry: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable is a @ParameterizedTest driven by @ValueSource(strings = {"a,b","a\"b","a\nb"}), The test covers three CSV-breaking characters: comma, double-quote, and newline, All 3 parameterized cases of quotingIsStable pass])
- test-repro-null-cache: concepts recall 0% (expected [problem-solution, gotcha], got [pattern, how-it-works])
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- test-flaky-clock: facts recall 67% (expected [Clock.fixed, Thread.sleep, 20 consecutive], got [`src/test/java/com/acme/scheduling/SchedulerTest.java` now sets `scheduler.clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)`., After the change, 20 consecutive test runs of `SchedulerTest` pass.])
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- security-sql-injection: facts recall 50% (expected [parameter placeholder, SqlInjectionTest], got [UserDao.findByEmail previously built its SQL via string concatenation, allowing injected email input to alter the query., The query now uses "select * from users where email = ?" with jdbcTemplate.query(mapper, email) binding the value as a parameter., SqlInjectionTest#emailWithQuoteReturnsEmpty passes after the change.])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, why-it-exists])
- security-csrf: title coverage 67% (expected keywords [fix, csrf, csrffilter])
- security-csrf: type 'feature' != expected 'bugfix'
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter.java in src/main/java/com/acme/web/ now calls csrfToken.matches(request) and sends HTTP 403 via response.sendError(403) on mismatch., CsrfFilterTest#postWithoutTokenIs403 passes, confirming requests without a valid token are rejected with status 403., The validation is applied to state-changing requests before processing continues (early return after sendError).])
- security-csrf: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, how-it-works])
- security-secret-config: title coverage 75% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, security, what-changed])
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-path-traversal: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- security-rate-limit: type 'feature' != expected 'bugfix'
- security-rate-limit: facts recall 50% (expected [429, token bucket], got [RateLimiter.java now calls `bucket.tryConsume(1)` and throws TooManyRequestsException when it returns false., RateLimiterTest#eleventhRequestIs429 passes, confirming the 429 behaviour at the request boundary.])
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [what-changed, problem-solution])
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, security-hardening, what-changed])
- config-hikari-pool: title coverage 75% (expected keywords [change, hikari, pool, size])
- config-hikari-pool: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-flyway-baseline: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, why-it-exists])
- config-log-level: title coverage 75% (expected keywords [change, logback, root, json])
- config-log-level: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-cors: title coverage 75% (expected keywords [restrict, cors, corsconfig, origin])
- config-cors: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-timeout: title coverage 75% (expected keywords [set, webclient, timeout, 5])
- config-timeout: type 'feature' != expected 'change'
- config-timeout: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-feature-flags: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-api-observations: title coverage 75% (expected keywords [document, observations, pagination, api])
- docs-api-observations: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- docs-readme-setup: title coverage 75% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- docs-architecture-adr: title coverage 75% (expected keywords [record, adr, extraction, validation])
- docs-architecture-adr: concepts recall 50% (expected [why-it-exists, trade-off], got [why-it-exists, what-changed])
- docs-migration-guide: title coverage 50% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: facts recall 50% (expected [mem_sessions, app.manual_sentinel_reviewed], got [docs/MIGRATION.md gained a new "## V20" section documenting the migration prerequisite., The V20 note requires setting app.manual_sentinel_reviewed=true prior to migration.])
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [what-changed, how-it-works])
- docs-security-policy: facts recall 50% (expected [security@acme.com, public issues], got [SECURITY.md was created at the repository root defining the security disclosure policy., SECURITY.md explicitly forbids opening public issues for vulnerabilities.])
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, why-it-exists])
- docs-contributing: title coverage 67% (expected keywords [update, contributing, verify-local])
- docs-contributing: concepts recall 50% (expected [what-changed, pattern], got [what-changed, why-it-exists])
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-docker-multistage: title coverage 75% (expected keywords [convert, dockerfile, multi-stage, jre])
- deploy-terraform-iam: title coverage 50% (expected keywords [restrict, iam, terraform, s3])
- deploy-terraform-iam: facts recall 50% (expected [AmazonS3ReadOnlyAccess, terraform validate], got [infra/main.tf attaches the AWS-managed policy arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess to the deploy role through aws_iam_role_policy "deploy".])
- deploy-terraform-iam: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-helm-values: title coverage 25% (expected keywords [change, helm, replicas, memory])
- deploy-helm-values: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 75% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])

## Raw answers behind problems

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache.get null placeholder race with computeIfAbsent</title>   <subtitle>Replaced cache.get with atomic computeIfAbsent so concurrent callers cannot both write a null placeholder.</subtitle>   <facts>     <fact>`UserCache.get` in `src/main/java/com/acme/cache/UserCache.java` changed from `cache.get(id)` to `cache.computeIfAbsent(id, loader::lo... [truncated]
```

### bug-connection-leak

```text
<observation>   <type>bugfix</type>   <title>Fixed ConnectionPool.borrow connection leak on caller exception</title>   <subtitle>borrow now closes connections via try-with-resources when the caller throws.</subtitle>   <facts>     <fact>ConnectionPool.borrow was leaking connections when the caller threw an exception.</fact>     <fact>ConnectionPool.borrow now closes the connection with try-with-re... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration boundary so expiredAtExactBoundary passes</title>   <subtitle>JwtVerifier now rejects tokens whose expiration equals the current instant instead of treating them as valid.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/JwtVerifier.java changed the expiry check from claims.getExpiration().before(now) to claims.get... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment race condition using AtomicLong.incrementAndGet</title>   <subtitle>Replaced non-atomic get/set pair with atomic incrementAndGet to fix lost-update race in concurrent increments.</subtitle>   <facts>     <fact>CounterService.increment previously called counter.set(counter.get() + 1) followed by counter.get(), which is not ... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator nested-quantifier ReDoS in URL regex</title>   <subtitle>Replaced catastrophic-backtracking URL regex with a linear pattern so long inputs finish under 100ms.</subtitle>   <facts>     <fact>src/main/java/com/acme/validation/UrlValidator.java now compiles the constant Pattern URL as `^https?://[^\s]+$`.</fact>     <fact>The previous re... [truncated]
```

### bug-file-encoding

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed FileReaderFactory to always use UTF-8 instead of platform default charset</title>   <subtitle>FileReaderFactory now explicitly opens streams with StandardCharsets.UTF_8, preventing mojibake on non-UTF-8 default JVMs.</subtitle>   <facts>     <fact>FileReaderFactory.java now constructs InputStreamReader with StandardCharsets.UTF_8 instead of... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>feature</type>   <title>Added typed accessors and unmodifiable map to ConfigLoader</title>   <subtitle>ConfigLoader now returns an unmodifiable map and exposes typed accessors while preserving existing values.</subtitle>   <facts>     <fact>ConfigLoader.java now returns an unmodifiable map built via `properties.entrySet().stream().collect(toUnmodifiableMap(...))`.</fact>     ... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Fixed UserRepository SQL injection via JPQL @Query parameterization</title>   <subtitle>Replaced string-concatenated SQL in UserRepository with a parameterized JPQL @Query annotation.</subtitle>   <facts>     <fact>UserRepository.java now declares @Query("select u from User u where u.tenantId = :tenantId")</fact>     <fact>The query binds tenantId as a ... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai</title>   <subtitle>Reproduction test proves report.dateAt computes the business date in UTC instead of the requested ZoneId.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins Instant 2026-11-01T23:30:00Z and expects LocalDate 2026-11-02 for ZoneId Asia/Shan... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized test for exponential backoff</title>   <subtitle>Parameterized test verifies RetryPolicy.delay returns 100, 200, 400 ms for attempts 1, 2, 3.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles uses @CsvSource with pairs "1,100", "2,200", "3,400" to verify attempt-to-delay mapping.</fact>     <fa... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable parameterized CSV quoting test</title>   <subtitle>New parameterized test verifies stable CSV quoting for values containing commas, quotes, and newlines.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable is a @ParameterizedTest driven by @ValueSource(strings = {"a,b","a\"b","a\nb"})</fact>     <fact>Th... [truncated]
```

### test-repro-null-cache

```text
```xml <observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull for UserCache null race</title>   <subtitle>Reproduction test uses 1000 parallel readers to expose intermittent null returns from UserCache.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull is located at src/test/java/com/acme/cache/CacheConcurrencyTest.jav... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Replaced Thread.sleep with Clock.fixed in SchedulerTest</title>   <subtitle>SchedulerTest now injects a fixed Clock, eliminating timing flakiness and making runs deterministic.</subtitle>   <facts>     <fact>`src/test/java/com/acme/scheduling/SchedulerTest.java` now sets `scheduler.clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.U... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed LogSanitizer credential redaction for password token secret</title>   <subtitle>LogSanitizer now masks password, token and secret values before they are written to logs.</subtitle>   <facts>     <fact>LogSanitizer.java applies value.replaceAll("(password|token|secret)=\\S+", "$1=***") to redact credentials.</fact>     <fact>The redaction pattern c... [truncated]
```

### security-sql-injection

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed SQL injection in UserDao.findByEmail with parameter placeholder</title>   <subtitle>Replaced string concatenation in findByEmail SQL query with a bound parameter placeholder.</subtitle>   <facts>     <fact>UserDao.findByEmail previously built its SQL via string concatenation, allowing injected email input to alter the query.</fact>     <fac... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Fixed AdminController authorization by adding @PreAuthorize hasRole('ADMIN')</title>   <subtitle>Added class-level @PreAuthorize to AdminController so every endpoint requires ROLE_ADMIN, verified by AdminAuthorizationTest#userRoleGets403 returning 403.</subtitle>   <facts>     <fact>src/main/java/com/acme/admin/AdminController.java now declares @PreAuth... [truncated]
```

### security-csrf

```text
```xml <observation>   <type>feature</type>   <title>Added CSRF token validation to CsrfFilter returning HTTP 403</title>   <subtitle>CsrfFilter now rejects state-changing requests when the CSRF token does not match, returning 403.</subtitle>   <facts>     <fact>CsrfFilter.java in src/main/java/com/acme/web/ now calls csrfToken.matches(request) and sends HTTP 403 via response.sendError(403) on mis... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Fixed hardcoded api-key secret leak in application.yml via ACME_API_KEY</title>   <subtitle>Replaced a literal API key in application.yml with an environment variable reference to stop secret leakage.</subtitle>   <facts>     <fact>src/main/resources/application.yml previously contained a literal credential value `api-key: sk-live-123`.</fact>     <fact... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Fixed XXE vulnerability by disabling DOCTYPE declarations in XmlImportService</title>   <subtitle>XmlImportService now rejects DOCTYPE declarations, preventing XML External Entity injection attacks.</subtitle>   <facts>     <fact>XmlImportService.java calls factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) on the XML parse... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Fixed FileDownloadController path traversal via normalize + startsWith(base) guard</title>   <subtitle>FileDownloadController now rejects names escaping the base directory by validating the normalized resolved path.</subtitle>   <facts>     <fact>FileDownloadController computes Path resolved = base.resolve(name).normalize() before using a user-supplied ... [truncated]
```

### security-rate-limit

```text
<observation>   <type>feature</type>   <title>Added 429 response in RateLimiter when token bucket is empty</title>   <subtitle>RateLimiter now throws TooManyRequestsException to return HTTP 429 when tokens are exhausted.</subtitle>   <facts>     <fact>RateLimiter.java now calls `bucket.tryConsume(1)` and throws TooManyRequestsException when it returns false.</fact>     <fact>The empty-bucket path ... [truncated]
```

### security-cookie-httponly

```text
```xml <observation>   <type>bugfix</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>Session cookie now sets HttpOnly, Secure, and SameSite=Strict to mitigate XSS and CSRF attacks</subtitle>   <facts>     <fact>SessionCookieConfig.java sets cookie.setHttpOnly(true) to prevent JavaScript access to the session cookie</fact>     <fact>SessionCookieConfig.java s... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Configured Hikari maximum-pool-size 20 and minimum-idle 5 in application.yml</title>   <subtitle>Database connection pool sizing set to 20 max connections with 5 minimum idle connections.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20</fact>     <fact>src/main/resources/application... [truncated]
```

### config-flyway-baseline

```text
<observation>   <type>change</type>   <title>Enabled flyway.baselineOnMigrate with flyway.baselineVersion 1 in pom.xml</title>   <subtitle>Flyway now baselines an existing schema at version 1 before applying migrations.</subtitle>   <facts>     <fact>pom.xml sets the property flyway.baselineOnMigrate to true.</fact>     <fact>pom.xml sets the property flyway.baselineVersion to 1.</fact>     <fact>... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Changed root logger level and appender in logback-spring.xml</title>   <subtitle>Root logger switched to INFO level with structured JSON appender output.</subtitle>   <facts>     <fact>In src/main/resources/logback-spring.xml, the root logger element now sets level to INFO.</fact>     <fact>The root logger references the JSON appender via appender-ref r... [truncated]
```

### config-cors

```text
<observation>   <type>change</type>   <title>Restricted CORS /api/** to https://app.acme.com with GET and POST in CorsConfig</title>   <subtitle>CorsConfig now allows only a single origin and two HTTP methods for API routes.</subtitle>   <facts>     <fact>CorsConfig registers `/api/**` with `allowedOrigins("https://app.acme.com")`.</fact>     <fact>CorsConfig restricts allowed methods to `GET` and... [truncated]
```

### config-timeout

```text
```xml <observation>   <type>feature</type>   <title>Added 5 second response timeout to WebClientConfig HttpClient</title>   <subtitle>WebClientConfig now sets a 5 second response timeout on its HttpClient bean.</subtitle>   <facts>     <fact>WebClientConfig configures HttpClient with `responseTimeout(Duration.ofSeconds(5))`</fact>     <fact>WebClientConfigTest verifies the timeout value is read f... [truncated]
```

### config-feature-flags

```text
```xml <observation>   <type>change</type>   <title>Enabled checkout-v2 and disabled legacy-search in feature-flags.yml</title>   <subtitle>Feature flags toggled to roll out checkout-v2 while retiring legacy-search.</subtitle>   <facts>     <fact>src/main/resources/feature-flags.yml sets checkout-v2 to true</fact>     <fact>src/main/resources/feature-flags.yml sets legacy-search to false</fact>   ... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added HEALTHCHECK to Dockerfile against /actuator/health</title>   <subtitle>Docker image now runs a 30-second health check against the Spring Boot actuator endpoint.</subtitle>   <facts>     <fact>Dockerfile now contains a HEALTHCHECK instruction with --interval=30s</fact>     <fact>HEALTHCHECK executes curl -f http://localhost:8080/actuator/health and... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 to pom.xml</title>   <subtitle>Maven build now generates JaCoCo code coverage reports during verify phase.</subtitle>   <facts>     <fact>pom.xml declares org.jacoco:jacoco-maven-plugin with version 0.8.12</fact>     <fact>mvn verify now writes coverage report to target/site/jacoco/jacoco.xml</fact>   </facts>   <narrati... [truncated]
```

### docs-api-observations

```text
```xml <observation>   <type>change</type>   <title>Added hasMore and items pagination contract to GET /api/observations in docs/API.md</title>   <subtitle>API documentation now describes the pagination response shape returned by the observations endpoint.</subtitle>   <facts>     <fact>docs/API.md documents GET /api/observations as returning hasMore and items fields</fact>     <fact>The paginatio... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Added three-step local setup to README.md (JDK 21, docker compose postgres)</title>   <subtitle>README.md now documents a three-step local setup using JDK 21, docker compose postgres, and mvn spring-boot:run.</subtitle>   <facts>     <fact>README.md gained a "## Setup" section containing three numbered steps.</fact>     <fact>Step 1 instructs installing... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Added CHANGELOG.md 2.4.0 entry for offline replay evaluation and V20 migration conflict fix</title>   <subtitle>Documented two release changes in the 2.4.0 changelog entry dated 2026-11-03.</subtitle>   <facts>     <fact>CHANGELOG.md adds a new section `## 2.4.0 - 2026-11-03`</fact>     <fact>The 2.4.0 entry lists `- Added offline replay evaluation`</fa... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>decision</type>   <title>Added ADR 0007-extraction-validation.md fact validation decision</title>   <subtitle>Architecture Decision Record 0007 mandates validating extracted facts against tool output before persistence.</subtitle>   <facts>     <fact>ADR 0007 is documented in docs/adr/0007-extraction-validation.md.</fact>     <fact>The decision states extracted facts must be ... [truncated]
```

### docs-migration-guide

```text
```xml <observation>   <type>change</type>   <title>Added V20 migration prerequisite to docs/MIGRATION.md</title>   <subtitle>Documentation now instructs users to back up mem_sessions and set app.manual_sentinel_reviewed=true before the V20 migration.</subtitle>   <facts>     <fact>docs/MIGRATION.md gained a new "## V20" section documenting the migration prerequisite.</fact>     <fact>The V20 note... [truncated]
```

### docs-security-policy

```text
```xml <observation>   <type>change</type>   <title>Added SECURITY.md with security@acme.com disclosure policy</title>   <subtitle>Created a security policy document directing vulnerability reports to security@acme.com and prohibiting public issues.</subtitle>   <facts>     <fact>SECURITY.md was created at the repository root defining the security disclosure policy.</fact>     <fact>SECURITY.md di... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Added verify-local.sh requirement to CONTRIBUTING.md</title>   <subtitle>CONTRIBUTING.md now mandates running ./scripts/verify-local.sh before opening a pull request and documents four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md now instructs contributors to run ./scripts/verify-local.sh before opening a pull request.</fact>     <fact>CO... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added readinessProbe /api/health:37777 to deployment.yaml</title>   <subtitle>Deployment now declares an HTTP readiness probe so Kubernetes gates traffic on app readiness.</subtitle>   <facts>     <fact>deploy/deployment.yaml adds a readinessProbe with httpGet path /api/health and port 37777.</fact>     <fact>The readinessProbe sets initialDelaySeconds ... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Added JDK 21 and 25 matrix to .github/workflows/ci.yml</title>   <subtitle>Backend CI now builds and tests against two Java versions via a strategy matrix.</subtitle>   <facts>     <fact>.github/workflows/ci.yml introduces `strategy.matrix` with `java: [21, 25]`.</fact>     <fact>The backend CI job now runs against JDK 21 and JDK 25.</fact>     <fact>Th... [truncated]
```

### deploy-docker-multistage

```text
<observation>   <type>change</type>   <title>Changed Dockerfile to multi-stage build with eclipse-temurin:21-jre runtime</title>   <subtitle>Refactored Dockerfile into separate build and runtime stages, reducing image size from 780MB to 210MB.</subtitle>   <facts>     <fact>Dockerfile now uses a multi-stage build with a build stage using maven and a runtime stage using eclipse-temurin:21-jre</fact... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Hardened deploy IAM role to AmazonS3ReadOnlyAccess in main.tf</title>   <subtitle>Reduced the deploy role's S3 permissions from full access to read-only via aws_iam_role_policy.</subtitle>   <facts>     <fact>infra/main.tf attaches the AWS-managed policy arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess to the deploy role through aws_iam_role_policy "deplo... [truncated]
```

### deploy-helm-values

```text
```xml <observation>   <type>change</type>   <title>Raised replicaCount to 3 and capped memory at 2Gi in values.yaml</title>   <subtitle>Helm values now run three replicas with a 2Gi memory limit per pod.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount to 3, up from the previous value.</fact>     <fact>infra/values.yaml sets resources.limits.memory to 2Gi.</fact>     <fact>Runni... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added rollback.sh kubectl rollout undo script for claude-mem deployment</title>   <subtitle>New scripts/rollback.sh reverts the claude-mem Kubernetes deployment and waits for rollout status.</subtitle>   <facts>     <fact>scripts/rollback.sh contains the commands `kubectl rollout undo deployment/claude-mem` and `kubectl rollout status deployment/claude... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Enabled canary.weight 10 and errorThreshold 0.02 in canary.yaml</title>   <subtitle>Infrastructure canary rollout configured with automatic rollback on elevated error rates.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true</fact>     <fact>canary.weight is set to 10 for a 10 percent traffic allocation to the canary</fact>    ... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title hit requires every declared identifier verbatim and keyword coverage ≥ 0.8; keywords are normalized for case, punctuation, CamelCase and a small synonym table.
- Facts/concepts recall is the primary field signal; precision counts produced items that mention an expected keyword. Trace-support rate is measured independently against the recorded tool input/output.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
