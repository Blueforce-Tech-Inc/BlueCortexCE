# Model output quality evaluation

- Date: 2026-11-03
- Phase: after
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 1193048 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 98% |
| skip accuracy | 99% |
|   skip confusion (TP/FP/FN/TN) | 19/0/1/62 |
| format errors | 0 |
| validation rejections | 1 |
| call failures | 0 |
| title exact-hit rate (legacy) | 61% |
| title normalized hit rate (legacy) | 66% |
| title verb-class hit rate | 84% |
| title subject hit rate | 84% |
| title contract hit rate (verb + subject + all identifiers) | 70% |
| title identifier hit rate | 100% |
| type accuracy | 90% |
| facts keyword precision (legacy) / recall | 77% / 95% |
| facts informative precision | 83% |
| facts trace-support rate | 100% |
| concepts precision / recall | 83% / 83% |
| skip consistency across repeats | 92% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 10155 / 26297 / 62210 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 99% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 1 | PASS |
| A5 | facts informative precision | >= 75% | 83% | PASS |
| A6 | title contract hit (verb + subject + identifiers) | >= 80% | 70% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 82 | 99% | 66% | 70% | 77% | 83% | 95% | 0 |
| 1 | 13 | 100% | 80% | 80% | 82% | 75% | 97% | 0 |
| 2 | 13 | 100% | 60% | 60% | 93% | 82% | 100% | 0 |

- average: skip 100%, title 69%, title contract 70%, facts P/R 84%/97%, facts actionable 80%
- worst run: skip 99%, title 60%, title contract 60%, facts P/R 77%/95%, facts actionable 75%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 12/12 | 9/12 |
| refactor | 9 | 9/9 | 8/9 | 8/9 |
| test-add | 9 | 8/9 | 8/9 | 7/9 |
| security-fix | 9 | 9/9 | 7/9 | 4/9 |
| config-change | 8 | 8/8 | 7/8 | 5/8 |
| docs-update | 8 | 8/8 | 8/8 | 4/8 |
| deploy-change | 7 | 7/7 | 5/7 | 3/7 |
| skip-low-value | 20 | 19/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 9146 |
| bug-null-cache | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 10155 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 26297 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 13904 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 6857 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 67% | 100%/100% | 17069 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 7926 |
| bug-race-counter | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 10776 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 100%/100% | 10475 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 9199 |
| bug-regex-redos | bug-fix | observation | observation | pass | 100% | hit | miss | match | 50%/100% | 100% | 100%/100% | 8383 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 11165 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 100% | 100%/100% | 8506 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 8589 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | MISS | miss | MISS | 67%/100% | 100% | 100%/100% | 20068 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 6025 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 9664 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 7159 |
| refactor-sql-repository | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 15137 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 33% | 100%/100% | 7102 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 50% | 100%/100% | 7989 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 12524 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 75% | 50%/50% | 13838 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 100% | 100%/100% | 11812 |
| test-integration-order | test-add | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 12921 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 33% | 50%/50% | 11724 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 11151 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 18376 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 0% | 50%/50% | 11289 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 19111 |
| security-redact-log | security-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 13568 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 15708 |
| security-authz-admin | security-fix | observation | observation | pass | 33% | MISS | miss | MISS | 100%/100% | 100% | 0%/0% | 13115 |
| security-csrf | security-fix | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 100% | 50%/50% | 7467 |
| security-secret-config | security-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 62210 |
| security-xxe | security-fix | observation | observation | pass | 67% | MISS | miss | match | 67%/100% | 100% | 50%/50% | 14509 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 13531 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS | miss | match | 33%/100% | 100% | 100%/100% | 18360 |
| security-cookie-httponly | security-fix | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 0%/0% | 32879 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 8210 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 10656 |
| config-log-level | config-change | observation | observation | pass | 100% | MISS | hit | match | 50%/50% | 50% | 100%/100% | 10080 |
| config-cors | config-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 22038 |
| config-timeout | config-change | observation | observation | pass | 75% | MISS | miss | match | 50%/100% | 50% | 100%/100% | 9567 |
| config-feature-flags | config-change | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 5858 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | hit | hit | MISS | 100%/100% | 100% | 50%/50% | 13273 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 15733 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 10341 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | MISS | miss | match | 75%/100% | 75% | 100%/100% | 7993 |
| docs-runbook | docs-update | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 7821 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 15952 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 100% | 100%/100% | 11128 |
| docs-migration-guide | docs-update | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 6701 |
| docs-security-policy | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 50%/50% | 27664 |
| docs-contributing | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/50% | 100% | 100%/100% | 11375 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 67% | 50%/50% | 9842 |
| deploy-github-actions | deploy-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 12847 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 50%/50% | 13548 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 50% | 100%/100% | 11616 |
| deploy-helm-values | deploy-change | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 11580 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 75% | MISS | miss | MISS | 67%/100% | 0% | 100%/100% | 10578 |
| deploy-canary | deploy-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 12028 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2751 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3394 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 6690 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2479 |
| skip-multi-step-explore | skip-low-value | skip | malformed | FAIL | - | - | - | - | - | - | - | 3355 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2354 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 6792 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3725 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2031 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2421 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2982 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3832 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 26913 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3135 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2410 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3849 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3174 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2116 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 8268 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4503 |

## Problems

- bug-offbyone-pagination: title coverage 67% (expected keywords [fix, pagination, orderquery])
- bug-offbyone-pagination: title subject [pagination] missing (got 'Fixed OrderQuery.pageOffset off-by-one page calculation')
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed FileReaderFactory platform default charset to UTF-8')
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title verb class 'refactor' missing (got 'Hardened ConfigLoader unmodifiable map and typed accessors')
- refactor-config-loader: type 'change' != expected 'refactor'
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [UserRepository now declares `@Query("select u from User u where u.tenantId = :tenantId")` for the tenant lookup., UserRepositoryTest returns the same tenant rows before and after the change.])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles is annotated with @ParameterizedTest and @CsvSource({"1,100","2,200","3,400"}), The test asserts policy.delay(attempt) equals the expected delay for attempts 1, 2, and 3 with values 100, 200, and 400 milliseconds, All 3 parameterized test cases pass])
- test-integration-order: validation rejected the observation: title too long: 104 chars > 100
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable is a @ParameterizedTest using @ValueSource(strings = {"a,b","a\"b","a\nb"}), The test covers three special-character cases: comma, double-quote, and newline, All 3 parameterized cases pass])
- test-parameterized-csv: concepts recall 50% (expected [what-changed, pattern], got [feature, pattern])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-contract-openapi: concepts recall 50% (expected [what-changed, pattern], got [problem-solution, pattern])
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- test-flaky-clock: title verb class 'fix' missing (got 'Changed SchedulerTest time control to Clock.fixed from Thread.sleep')
- test-flaky-clock: title subject [flaky, clock] missing (got 'Changed SchedulerTest time control to Clock.fixed from Thread.sleep')
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: title subject [credentials] missing (got 'Redacted password, token and secret values in LogSanitizer')
- security-sql-injection: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-authz-admin: title coverage 33% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Restricted AdminController endpoints to ROLE_ADMIN')
- security-authz-admin: title subject [authorization] missing (got 'Restricted AdminController endpoints to ROLE_ADMIN')
- security-authz-admin: type 'feature' != expected 'bugfix'
- security-authz-admin: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, what-changed])
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter.java now checks `csrfToken.matches(request)` and calls `response.sendError(403)` when the token does not match., CsrfFilterTest#postWithoutTokenIs403 passes, confirming the 403 response for requests without a valid token.])
- security-csrf: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-secret-config: title coverage 75% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Removed literal api-key value, now ${ACME_API_KEY} in application.yml')
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-xxe: title verb class 'harden' missing (got 'Fixed XXE vulnerability in XmlImportService DOCTYPE handling')
- security-xxe: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title verb class 'add' missing (got 'Fixed RateLimiter empty bucket rejection in RateLimiter.java')
- security-cookie-httponly: type 'change' != expected 'bugfix'
- security-cookie-httponly: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, pattern])
- config-hikari-pool: title coverage 75% (expected keywords [change, hikari, pool, size])
- config-hikari-pool: title verb class 'change' missing (got 'Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml')
- config-log-level: title subject [log, level] missing (got 'Changed root logger level to INFO and appender to JSON in logback-spring.xml')
- config-log-level: facts recall 50% (expected [INFO, JSON appender], got [logback-spring.xml sets root logger level to INFO with appender-ref ref="JSON"., LogbackConfigTest parses the XML to verify the configuration.])
- config-timeout: title coverage 75% (expected keywords [set, webclient, timeout, 5])
- config-timeout: title verb class 'set' missing (got 'Changed HTTP client response timeout to 5 seconds in WebClientConfig')
- config-feature-flags: title coverage 50% (expected keywords [enable, checkout-v2, disable, legacy-search])
- config-feature-flags: title verb class 'enable' missing (got 'Changed feature flags checkout-v2 and legacy-search in feature-flags.yml')
- config-docker-healthcheck: type 'feature' != expected 'change'
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Added three-step local setup in README.md')
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-migration-guide: title coverage 50% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: title verb class 'document' missing (got 'Changed V20 migration prerequisites in docs/MIGRATION.md')
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [what-changed, pattern])
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 67% (expected keywords [update, contributing, verify-local])
- docs-contributing: facts recall 50% (expected [verify-local.sh, four test layers], got [CONTRIBUTING.md now requires contributors to run ./scripts/verify-local.sh before opening a pull request])
- deploy-k8s-probe: type 'feature' != expected 'change'
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [pattern, trade-off])
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-docker-multistage: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-terraform-iam: title coverage 50% (expected keywords [restrict, iam, terraform, s3])
- deploy-terraform-iam: title subject [iam, policy] missing (got 'Restricted deploy role S3 access to AmazonS3ReadOnlyAccess in main.tf')
- deploy-helm-values: title coverage 75% (expected keywords [change, helm, replicas, memory])
- deploy-helm-values: title subject [helm, values] missing (got 'Changed replicaCount to 3 and memory limit to 2Gi in values.yaml')
- deploy-rollback-script: title coverage 75% (expected keywords [add, rollback, script, deployment])
- deploy-rollback-script: title subject [rollback, script] missing (got 'Added rollback.sh for claude-mem deployment undo')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 75% (expected keywords [enable, canary, rollback, threshold])
- skip-multi-step-explore: expected <skip> but produced malformed output: DataValidationException: No <observation> block found in XML

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset off-by-one page calculation</title>   <subtitle>Removed the erroneous extra -1 from the pagination offset formula so page offsets start at zero.</subtitle>   <facts>     <fact>OrderQuery.pageOffset now returns (page * size) instead of (page * size - 1).</fact>     <fact>OrderQueryTest#firstPageStartsAtZero passes after the ch... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for off-by-one-day bug</title>   <subtitle>Added a regression test pinning the Asia/Shanghai business date for a UTC timestamp that falls on the next calendar day.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins the input timestamp 2026-11-01T23:30Z.</fact>     <fact>The te... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration boundary check rejecting isEqual instant in JwtVerifier.java</title>   <subtitle>JWT tokens expiring exactly at the current instant are now rejected instead of incorrectly accepted.</subtitle>   <facts>     <fact>JwtVerifier.java changed the expiry test from `claims.getExpiration().before(now)` to `claims.getExpiration().isB... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsvExporter line endings normalization for multiline fields</title>   <subtitle>CsvExporter now normalizes embedded line endings to CRLF before quoting, preserving row count for multiline fields.</subtitle>   <facts>     <fact>CsvExporter.java now writes `line.replaceAll("\\r?\\n", "\\r\\n")` so embedded newlines are normalized to CRLF before quot... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment race condition with AtomicLong.incrementAndGet</title>   <subtitle>Replaced non-atomic get/set pair with AtomicLong.incrementAndGet to prevent lost updates under concurrent access.</subtitle>   <facts>     <fact>CounterService.increment now uses AtomicLong.incrementAndGet() instead of counter.set(counter.get() + 1)</fact> ... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed FileReaderFactory platform default charset to UTF-8</title>   <subtitle>FileReaderFactory now explicitly opens streams as UTF-8 instead of relying on the platform default charset.</subtitle>   <facts>     <fact>FileReaderFactory.java now constructs InputStreamReader with StandardCharsets.UTF_8 instead of the platform default.</fact>     <fact>File... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>change</type>   <title>Hardened ConfigLoader unmodifiable map and typed accessors</title>   <subtitle>ConfigLoader now returns an unmodifiable map and exposes typed accessors; ConfigLoaderTest passes with identical values.</subtitle>   <facts>     <fact>src/main/java/com/acme/config/ConfigLoader.java now builds its return value with `properties.entrySet().stream().collect(toU... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>refactor</type>   <title>Refactored UserRepository tenant lookup to parameterized JPQL @Query</title>   <subtitle>Replaced string-concatenated SQL in UserRepository with a bound JPQL @Query while preserving tenant filtering behavior.</subtitle>   <facts>     <fact>UserRepository now declares `@Query("select u from User u where u.tenantId = :tenantId")` for the tenant lookup.<... [truncated]
```

### test-repro-timezone

```text
```xml <observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai UTC date bug</title>   <subtitle>New regression test reproduces the reported UTC-vs-business-date mismatch for Asia/Shanghai timezone.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone asserts report.dateAt(Instant.parse("2026-11-01T23:30:00Z"), ZoneId.of(... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 reproducing 401 for locked account</title>   <subtitle>A reproduction test pins the defect where locked accounts receive HTTP 401 instead of 423.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true.</fact>     <fact>The test performs POST... [truncated]
```

### test-unit-retry

```text
```xml <observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized backoff test</title>   <subtitle>Parameterized test verifies RetryPolicy.delay doubles for attempts 1-3 with 100/200/400 ms.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles is annotated with @ParameterizedTest and @CsvSource({"1,100","2,200","3,400"})</fact>     <fact>The test asserts ... [truncated]
```

### test-integration-order

```text
<observation>   <type>feature</type>   <title>Added OrderApiIT#createOrderReturns201 integration test in src/test/java/com/acme/orders/OrderApiIT.java</title>   <subtitle>New Spring Boot integration test posts a valid order and asserts HTTP 201 with a Location header using Testcontainers PostgreSQL 16.</subtitle>   <facts>     <fact>OrderApiIT#createOrderReturns201 posts a valid order and asserts ... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable parameterized test for CSV quoting</title>   <subtitle>Parameterized test pins CSV quoting stability across comma, double-quote, and newline values.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable is a @ParameterizedTest using @ValueSource(strings = {"a,b","a\"b","a\nb"})</fact>     <fact>The test co... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull for UserCache null</title>   <subtitle>A 1000-parallel-reader test reproduces the intermittent null returned by UserCache before the fix and passes after.</subtitle>   <facts>     <fact>New file `src/test/java/com/acme/cache/CacheConcurrencyTest.java` adds test `CacheConcurrencyTest#concurrentRea... [truncated]
```

### test-contract-openapi

```text
<observation>   <type>feature</type>   <title>Added OpenAPI contract test OpenApiContractTest#generatedSpecMatchesCommittedFile</title>   <subtitle>Test diffs springdoc-generated OpenAPI spec against committed docs/openapi.yaml and fails on schema drift.</subtitle>   <facts>     <fact>OpenApiContractTest#generatedSpecMatchesCommittedFile compares springdoc output to docs/openapi.yaml</fact>     <f... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Changed SchedulerTest time control to Clock.fixed from Thread.sleep</title>   <subtitle>Replaced flaky Thread.sleep usage with deterministic fixed clock in SchedulerTest.</subtitle>   <facts>     <fact>SchedulerTest now assigns `scheduler.clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)` instead of relying on Thread.sleep.</fa... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Redacted password, token and secret values in LogSanitizer</title>   <subtitle>LogSanitizer now masks credential values matching password, token and secret keys before logging.</subtitle>   <facts>     <fact>LogSanitizer applies `value.replaceAll("(password|token|secret)=\\S+", "$1=***")` to redact credential values before logging.</fact>     <fact>LogS... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed SQL injection in UserDao.findByEmail with parameter placeholder</title>   <subtitle>Replaced string concatenation with a prepared-statement placeholder in the user lookup query.</subtitle>   <facts>     <fact>UserDao.findByEmail now executes `jdbcTemplate.query("select * from users where email = ?", mapper, email)` using a `?` parameter placeholde... [truncated]
```

### security-authz-admin

```text
<observation>   <type>feature</type>   <title>Restricted AdminController endpoints to ROLE_ADMIN</title>   <subtitle>Every AdminController endpoint now requires ROLE_ADMIN via method security annotation.</subtitle>   <facts>     <fact>AdminController.java now carries `@PreAuthorize("hasRole('ADMIN')")`, enforcing ROLE_ADMIN on every endpoint.</fact>     <fact>AdminAuthorizationTest#userRoleGets403... [truncated]
```

### security-csrf

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed CsrfFilter token mismatch rejection returning 403</title>   <subtitle>CsrfFilter now rejects state-changing requests with a mismatched token by sending a 403 error.</subtitle>   <facts>     <fact>CsrfFilter.java now checks `csrfToken.matches(request)` and calls `response.sendError(403)` when the token does not match.</fact>     <fact>CsrfFi... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Removed literal api-key value, now ${ACME_API_KEY} in application.yml</title>   <subtitle>Hardcoded API key in application.yml replaced with environment variable reference; SecretScanTest now passes.</subtitle>   <facts>     <fact>`application.yml` `api-key` value changed from literal `sk-live-123` to `${ACME_API_KEY}` environment variable reference</fa... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Fixed XXE vulnerability in XmlImportService DOCTYPE handling</title>   <subtitle>Disabled DOCTYPE declarations in XmlImportService to block XXE attacks.</subtitle>   <facts>     <fact>XmlImportService sets factory feature "http://apache.org/xml/features/disallow-doctype-decl" to true</fact>     <fact>XxeTest#externalEntityIsRejected passes after the cha... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter empty bucket rejection in RateLimiter.java</title>   <subtitle>RateLimiter now throws TooManyRequestsException when token bucket is empty, returning HTTP 429.</subtitle>   <facts>     <fact>RateLimiter.java now throws TooManyRequestsException when bucket.tryConsume(1) returns false</fact>     <fact>RateLimiterTest#eleventhRequestIs429 ... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>change</type>   <title>Hardened session cookie HttpOnly Secure SameSite=Strict in SessionCookieConfig.java</title>   <subtitle>Session cookies now set HttpOnly, Secure, and SameSite=Strict to block script access, plaintext transmission, and cross-site request forgery.</subtitle>   <facts>     <fact>SessionCookieConfig.java now calls cookie.setHttpOnly(true), cookie.setSecure(... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml</title>   <subtitle>Configured Hikari connection pool sizing with maximum-pool-size 20 and minimum-idle 5.</subtitle>   <facts>     <fact>application.yml sets spring.datasource.hikari.maximum-pool-size to 20</fact>     <fact>application.yml sets spring.datasource.hikari.minimum-... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Changed root logger level to INFO and appender to JSON in logback-spring.xml</title>   <subtitle>Root logger reconfigured to emit JSON-formatted output at INFO level instead of previous settings.</subtitle>   <facts>     <fact>logback-spring.xml sets root logger level to INFO with appender-ref ref="JSON".</fact>     <fact>LogbackConfigTest parses the XM... [truncated]
```

### config-timeout

```text
```xml <observation>   <type>change</type>   <title>Changed HTTP client response timeout to 5 seconds in WebClientConfig</title>   <subtitle>WebClientConfig now enforces a 5 second response timeout on the shared HttpClient.</subtitle>   <facts>     <fact>WebClientConfig.java configures HttpClient.create().responseTimeout(Duration.ofSeconds(5))</fact>     <fact>WebClientConfigTest reads the configu... [truncated]
```

### config-feature-flags

```text
<observation>   <type>change</type>   <title>Changed feature flags checkout-v2 and legacy-search in feature-flags.yml</title>   <subtitle>Enabled checkout-v2 and disabled legacy-search via feature-flags.yml, with FlagBindingTest binding both booleans.</subtitle>   <facts>     <fact>src/main/resources/feature-flags.yml sets checkout-v2 to true.</fact>     <fact>src/main/resources/feature-flags.yml ... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>feature</type>   <title>Added HEALTHCHECK /actuator/health to Dockerfile</title>   <subtitle>Dockerfile now defines a container health check probing Spring Boot actuator every 30 seconds.</subtitle>   <facts>     <fact>Dockerfile includes `HEALTHCHECK --interval=30s CMD curl -f http://localhost:8080/actuator/health || exit 1`</fact>     <fact>The health check probes `/actuato... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Added three-step local setup in README.md</title>   <subtitle>README.md now documents local startup as JDK 21 install, Postgres via docker compose, and mvn spring-boot:run.</subtitle>   <facts>     <fact>README.md gained a new "## Setup" section with three numbered steps.</fact>     <fact>Step 1 of the README setup instructs installing JDK 21.</fact>   ... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Changed CHANGELOG.md 2.4.0 entry with offline replay evaluation and V20 migration conflict</title>   <subtitle>Added a new 2.4.0 changelog section documenting offline replay evaluation and the V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md now contains a section header `## 2.4.0 - 2026-11-03`.</fact>     <fact>The 2.4.0 entry li... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Changed V20 migration prerequisites in docs/MIGRATION.md</title>   <subtitle>Migration guide now documents backing up mem_sessions and setting app.manual_sentinel_reviewed=true for V20.</subtitle>   <facts>     <fact>docs/MIGRATION.md V20 section requires backing up mem_sessions before running the migration.</fact>     <fact>docs/MIGRATION.md V20 sectio... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Added SECURITY.md vulnerability disclosure policy</title>   <subtitle>New SECURITY.md establishes security@acme.com as the vulnerability reporting channel and bans public issues.</subtitle>   <facts>     <fact>SECURITY.md defines the vulnerability disclosure channel as security@acme.com.</fact>     <fact>SECURITY.md instructs reporters not to open publi... [truncated]
```

### docs-contributing

```text
```xml <observation>   <type>change</type>   <title>Changed pull request verification in CONTRIBUTING.md to require ./scripts/verify-local.sh</title>   <subtitle>Contributing guidelines now mandate running local verification script before submitting pull requests.</subtitle>   <facts>     <fact>CONTRIBUTING.md now requires contributors to run ./scripts/verify-local.sh before opening a pull request... [truncated]
```

### deploy-k8s-probe

```text
```xml <observation>   <type>feature</type>   <title>Added readinessProbe /api/health:37777 to deployment.yaml</title>   <subtitle>Kubernetes deployment now probes /api/health on port 37777 with 15-second initial delay.</subtitle>   <facts>     <fact>deploy/deployment.yaml now includes a readinessProbe using httpGet path /api/health and port 37777</fact>     <fact>readinessProbe.initialDelaySecond... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Added JDK matrix strategy to .github/workflows/ci.yml</title>   <subtitle>The CI workflow now tests the backend against both JDK 21 and JDK 25 using a matrix strategy.</subtitle>   <facts>     <fact>.github/workflows/ci.yml defines a strategy.matrix with java: [21, 25] to test multiple JDK versions.</fact>     <fact>The backend CI pipeline now runs on b... [truncated]
```

### deploy-docker-multistage

```text
<observation>   <type>change</type>   <title>Converted Dockerfile to multi-stage with eclipse-temurin:21-jre</title>   <subtitle>Multi-stage Docker build reduces final image size from 780MB to 210MB by separating build and runtime stages.</subtitle>   <facts>     <fact>Dockerfile now defines a multi-stage build with a maven-based build stage and a runtime stage using eclipse-temurin:21-jre</fact> ... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted deploy role S3 access to AmazonS3ReadOnlyAccess in main.tf</title>   <subtitle>IAM deploy policy switched from full S3 access to the read-only managed policy.</subtitle>   <facts>     <fact>aws_iam_role_policy "deploy" in infra/main.tf now references arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess instead of full S3 access</fact>     <fact>ter... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Changed replicaCount to 3 and memory limit to 2Gi in values.yaml</title>   <subtitle>Scaled the deployment to three replicas and capped pod memory at 2Gi via Helm values.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount to 3.</fact>     <fact>infra/values.yaml sets resources.limits.memory to 2Gi.</fact>     <fact>helm template renders ... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added rollback.sh for claude-mem deployment undo</title>   <subtitle>New shell script rolls back the claude-mem Kubernetes deployment and waits for rollout completion.</subtitle>   <facts>     <fact>scripts/rollback.sh executes `kubectl rollout undo deployment/claude-mem` followed by `kubectl rollout status deployment/claude-mem`</fact>     <fact>The s... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Enabled canary rollout with weight=10 and errorThreshold=0.02 in canary.yaml</title>   <subtitle>Canary deployment now routes 10 percent of traffic and auto-rolls back above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true</fact>     <fact>infra/canary.yaml sets canary.weight to 10 for a 10 percent tra... [truncated]
```

### skip-multi-step-explore

```text
<skip>read-only grep search for fetchSymbol identifier with no changes made
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared verb class, (2) the declared subject tokens to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. The legacy keyword-coverage metric is reported alongside for before/after comparison.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
