# Model output quality evaluation

- Date: 2026-11-03
- Phase: after
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 909530 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 96% |
| skip accuracy | 98% |
|   skip confusion (TP/FP/FN/TN) | 18/0/2/62 |
| format errors | 0 |
| validation rejections | 1 |
| call failures | 0 |
| title exact-hit rate (legacy) | 62% |
| title normalized hit rate (legacy) | 61% |
| title verb-class hit rate | 79% |
| title subject hit rate | 85% |
| title contract hit rate (verb + subject + all identifiers) | 62% |
| title identifier hit rate | 92% |
| type accuracy | 93% |
| facts keyword precision (legacy) / recall | 72% / 91% |
| facts informative precision | 78% |
| facts trace-support rate | 100% |
| concepts precision / recall | 78% / 76% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 8201 / 15199 / 24786 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 98% | PASS |
| A3 | must-record samples skipped | = 0 | 2 | **FAIL** |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 78% | PASS |
| A6 | title contract hit (verb + subject + identifiers) | >= 80% | 62% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 82 | 98% | 61% | 62% | 72% | 78% | 91% | 0 |
| 1 | 13 | 100% | 70% | 70% | 85% | 74% | 100% | 0 |
| 2 | 13 | 100% | 80% | 80% | 87% | 75% | 100% | 0 |

- average: skip 99%, title 70%, title contract 71%, facts P/R 81%/97%, facts actionable 76%
- worst run: skip 98%, title 61%, title contract 62%, facts P/R 72%/91%, facts actionable 74%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 11/12 | 11/12 | 7/12 |
| refactor | 9 | 9/9 | 8/9 | 8/9 |
| test-add | 9 | 9/9 | 9/9 | 7/9 |
| security-fix | 9 | 9/9 | 9/9 | 5/9 |
| config-change | 8 | 8/8 | 7/8 | 5/8 |
| docs-update | 8 | 8/8 | 8/8 | 2/8 |
| deploy-change | 7 | 7/7 | 5/7 | 3/7 |
| skip-low-value | 20 | 18/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 9873 |
| bug-null-cache | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 8600 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 50%/50% | 17188 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 7590 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 14178 |
| bug-jwt-expiry | bug-fix | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 6930 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 50%/100% | 100% | 50%/50% | 8740 |
| bug-race-counter | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 8929 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 50%/50% | 8663 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 7292 |
| bug-regex-redos | bug-fix | observation | observation | pass | 50% | MISS | miss | match | 33%/50% | 33% | 100%/100% | 11779 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 7311 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 50% | 100%/100% | 12008 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 8974 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | MISS | miss | MISS | 100%/100% | 50% | 100%/100% | 12341 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 6960 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 0%/0% | 100% | 100%/100% | 8898 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 100%/100% | 9442 |
| refactor-sql-repository | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 24786 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 0%/0% | 50% | 100%/100% | 8275 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 50% | 100%/100% | 9468 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 8488 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 11029 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/50% | 100% | 100%/100% | 10168 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 7836 |
| test-parameterized-csv | test-add | observation | observation | pass | 75% | MISS | miss | match | 100%/50% | 33% | 50%/50% | 12292 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 15155 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/50% | 11827 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 50%/50% | 15226 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 11226 |
| security-redact-log | security-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 7348 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 6984 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 8231 |
| security-csrf | security-fix | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 6906 |
| security-secret-config | security-fix | observation | observation | pass | 100% | MISS | hit | match | 67%/100% | 100% | 100%/100% | 7248 |
| security-xxe | security-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 8201 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 7081 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS | miss | match | 33%/50% | 67% | 50%/50% | 10995 |
| security-cookie-httponly | security-fix | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 13762 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 67% | 50%/50% | 5401 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 100%/100% | 12544 |
| config-log-level | config-change | observation | observation | pass | 100% | MISS | hit | match | 33%/50% | 33% | 100%/50% | 15945 |
| config-cors | config-change | observation | observation | pass | 75% | MISS | miss | MISS | 67%/100% | 100% | 50%/50% | 7738 |
| config-timeout | config-change | observation | observation | pass | 75% | MISS | miss | match | 50%/100% | 50% | 100%/100% | 7421 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 8322 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 13590 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 6251 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | MISS | hit | match | 50%/100% | 50% | 50%/50% | 8237 |
| docs-readme-setup | docs-update | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 4650 |
| docs-runbook | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 75% | 50%/50% | 4772 |
| docs-changelog | docs-update | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 100%/50% | 8689 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | MISS | hit | match | 33%/100% | 67% | 50%/50% | 10861 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 67% | 100%/100% | 11184 |
| docs-security-policy | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 50% | 50%/50% | 5477 |
| docs-contributing | docs-update | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 50% | 100%/100% | 8570 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 8875 |
| deploy-github-actions | deploy-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 8186 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 50% | MISS | miss | match | 20%/100% | 80% | 50%/50% | 6845 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 50% | MISS | miss | MISS | 67%/100% | 67% | 50%/50% | 12151 |
| deploy-helm-values | deploy-change | observation | observation | pass | 25% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 14187 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | hit | MISS | 50%/100% | 25% | 100%/100% | 10380 |
| deploy-canary | deploy-change | observation | observation | pass | 75% | hit | miss | match | 50%/100% | 100% | 100%/100% | 15199 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2641 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3399 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3307 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2531 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2369 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3192 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3018 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4736 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 1787 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2879 |
| skip-grep-search | skip-low-value | skip | observation | FAIL | 0% | MISS | miss | MISS | 0%/0% | 0% | 0%/0% | 4501 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2792 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3149 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4749 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2799 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2085 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4271 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2303 |
| skip-read-config-to-answer | skip-low-value | skip | malformed | FAIL | - | - | - | - | - | - | - | 4295 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5356 |

## Problems

- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed UserCache.get concurrent null placeholder race')
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [what-changed, gotcha])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-jwt-expiry: validation rejected the observation: title too long: 103 chars > 100
- bug-csv-crlf: title coverage 75% (expected keywords [fix, csv, line, endings])
- bug-csv-crlf: title subject [line, endings] missing (got 'Fixed CsvExporter line ending normalization for multiline CSV fields')
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-n-plus-one: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-regex-redos: title coverage 50% (expected keywords [fix, redos, urlvalidator, regex])
- bug-regex-redos: title subject [redos] missing (got 'Fixed UrlValidator catastrophic backtracking with linear URL pattern')
- bug-regex-redos: facts recall 50% (expected [linear pattern, longInputFinishesUnder100ms], got [`UrlValidator` changed its URL pattern to `^https?://[^\s]+$`, replacing a regex with nested quantifiers., `UrlValidatorTest#longInputFinishesUnder100ms` passes after the change., The previous nested-quantifier regex caused slow processing on long input.])
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed FileReaderFactory to always open UTF-8 instead of platform default charset')
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title verb class 'refactor' missing (got 'Hardened ConfigLoader.java with toUnmodifiableMap and typed accessors')
- refactor-config-loader: type 'feature' != expected 'refactor'
- refactor-retry-policy: facts recall 0% (expected [BackoffStrategy, 100/200/400], got [src/main/java/com/acme/resilience/RetryPolicy.java now delegates delay calculation via `backoff.nextDelay(attempt)` instead of computing delays inline.])
- refactor-event-bus: facts recall 0% (expected [listener map, event type], got [EventBus.java now uses `listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event))` for dispatch instead of an if/else chain, EventBusTest verifies the same three events are dispatched correctly after the refactor])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles is annotated with @ParameterizedTest and @CsvSource({"1,100","2,200","3,400"}), RetryPolicyTest#delayDoubles asserts that policy.delay(attempt) equals the expected delay for each attempt, The test suite reports 3 tests passing for attempts 1, 2, and 3 with expected delays of 100, 200, and 400 ms])
- test-integration-order: concepts recall 50% (expected [what-changed, pattern], got [pattern, how-it-works])
- test-parameterized-csv: title coverage 75% (expected keywords [add, csv, parameterized, test])
- test-parameterized-csv: title subject [parameterized] missing (got 'Added CsvExporterTest#quotingIsStable for comma quote newline cases')
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [src/test/java/com/acme/export/CsvExporterTest.java defines `quotingIsStable` as a `@ParameterizedTest` with `@ValueSource(strings = {"a,b", "a\"b", "a\nb"})`., `quotingIsStable` verifies stable quoting for comma-separated, embedded-quote, and newline-containing CSV values., All 3 parameter cases in `CsvExporterTest#quotingIsStable` pass.])
- test-parameterized-csv: concepts recall 50% (expected [what-changed, pattern], got [what-changed, gotcha])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, gotcha])
- test-contract-openapi: concepts recall 50% (expected [what-changed, pattern], got [what-changed, gotcha])
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- test-flaky-clock: title subject [flaky, clock] missing (got 'Fixed SchedulerTest flakiness with Clock.fixed')
- test-flaky-clock: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: title verb class 'redact' missing (got 'Fixed LogSanitizer credential redaction for password token secret')
- security-redact-log: title subject [credentials] missing (got 'Fixed LogSanitizer credential redaction for password token secret')
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title subject [authorization] missing (got 'Hardened AdminController endpoints with @PreAuthorize hasRole('ADMIN')')
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter.java now calls csrfToken.matches(request) and rejects state-changing requests that fail validation, Test CsrfFilterTest#postWithoutTokenIs403 verifies that a POST without a valid CSRF token returns 403])
- security-secret-config: title lost identifiers [application.yml, ACME_API_KEY] (got 'Removed hardcoded API key sk-live-123 from application.yml')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title verb class 'add' missing (got 'Fixed RateLimiter to return 429 when token bucket is empty')
- security-rate-limit: facts recall 50% (expected [429, token bucket], got [src/main/java/com/acme/web/RateLimiter.java now calls bucket.tryConsume(1) and throws TooManyRequestsException when it returns false., RateLimiterTest#eleventhRequestIs429 passes after the change, confirming the 11th request is rejected with 429., The change is located in /repo/web at src/main/java/com/acme/web/RateLimiter.java.])
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- config-hikari-pool: title coverage 75% (expected keywords [change, hikari, pool, size])
- config-hikari-pool: title verb class 'change' missing (got 'Added Hikari maximum-pool-size and minimum-idle to application.yml')
- config-hikari-pool: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-log-level: title subject [log, level] missing (got 'Changed logback-spring.xml root logger to INFO with JSON appender')
- config-log-level: facts recall 50% (expected [INFO, JSON appender], got [logback-spring.xml root logger level set to INFO, Root logger references appender "JSON" via appender-ref, LogbackConfigTest parses the XML configuration file])
- config-log-level: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])
- config-cors: title coverage 75% (expected keywords [restrict, cors, corsconfig, origin])
- config-cors: title verb class 'restrict' missing (got 'Added CORS restriction for /api/** in CorsConfig.java')
- config-cors: type 'feature' != expected 'change'
- config-cors: concepts recall 50% (expected [what-changed, trade-off], got [problem-solution, what-changed])
- config-timeout: title coverage 75% (expected keywords [set, webclient, timeout, 5])
- config-timeout: title verb class 'set' missing (got 'Added 5 second response timeout to WebClientConfig')
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works])
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-api-observations: title lost identifiers [API.md, /api/observations] (got 'Documented GET /api/observations pagination fields hasMore and items')
- docs-api-observations: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-readme-setup: title coverage 75% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Added local setup steps to README.md with JDK 21 and docker compose postgres')
- docs-readme-setup: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-runbook: concepts recall 50% (expected [what-changed, pattern], got [what-changed, how-it-works])
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title verb class 'change' missing (got 'Added 2.4.0 CHANGELOG.md entry for offline replay evaluation and V20 migration conflict fix')
- docs-changelog: concepts recall 50% (expected [what-changed, pattern], got [what-changed, what-changed])
- docs-architecture-adr: title lost identifiers [0007-extraction-validation.md] (got 'Added ADR 0007 extraction validation decision record')
- docs-architecture-adr: title verb class 'record' missing (got 'Added ADR 0007 extraction validation decision record')
- docs-architecture-adr: concepts recall 50% (expected [why-it-exists, trade-off], got [why-it-exists, pattern])
- docs-migration-guide: title coverage 75% (expected keywords [document, v20, migration, sentinel])
- docs-security-policy: facts recall 50% (expected [security@acme.com, public issues], got [SECURITY.md was created in the repository root at /repo/SECURITY.md., SECURITY.md explicitly forbids opening public issues for vulnerabilities.])
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, why-it-exists])
- docs-contributing: title coverage 67% (expected keywords [update, contributing, verify-local])
- docs-contributing: title verb class 'change' missing (got 'Added verify-local.sh PR requirement to CONTRIBUTING.md')
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-github-actions: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-docker-multistage: title lost identifiers [Dockerfile, eclipse-temurin:21-jre] (got 'Refactored Dockerfile to multi-stage build reducing image size 780MB to 210MB')
- deploy-docker-multistage: title verb class 'convert' missing (got 'Refactored Dockerfile to multi-stage build reducing image size 780MB to 210MB')
- deploy-docker-multistage: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-terraform-iam: title coverage 50% (expected keywords [restrict, iam, terraform, s3])
- deploy-terraform-iam: title verb class 'restrict' missing (got 'Hardened aws_iam_role_policy.deploy to AmazonS3ReadOnlyAccess in main.tf')
- deploy-terraform-iam: type 'bugfix' != expected 'change'
- deploy-terraform-iam: concepts recall 50% (expected [what-changed, trade-off], got [problem-solution, trade-off])
- deploy-helm-values: title coverage 25% (expected keywords [change, helm, replicas, memory])
- deploy-helm-values: title verb class 'change' missing (got 'Raised replicaCount to 3 in infra/values.yaml with 2Gi memory cap')
- deploy-helm-values: title subject [helm, values] missing (got 'Raised replicaCount to 3 in infra/values.yaml with 2Gi memory cap')
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 75% (expected keywords [enable, canary, rollback, threshold])
- skip-grep-search: expected <skip> but produced an observation
- skip-read-config-to-answer: expected <skip> but produced malformed output: DataValidationException: No <observation> block found in XML

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset off-by-one in pagination calculation</title>   <subtitle>Removed erroneous -1 from page offset formula that shifted every page by one row.</subtitle>   <facts>     <fact>OrderQuery.pageOffset previously computed `page * size - 1`, yielding -1 for page 0 and shifting all subsequent pages by one row.</fact>     <fact>The fix re... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache.get concurrent null placeholder race</title>   <subtitle>UserCache.get now loads through computeIfAbsent, preventing two concurrent callers from writing a null placeholder.</subtitle>   <facts>     <fact>UserCache.get in src/main/java/com/acme/cache/UserCache.java changed from `return cache.get(id)` to `return cache.computeIfAbsent(id, l... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai day boundary</title>   <subtitle>Regression test pins 2026-11-01T23:30Z and asserts the Asia/Shanghai business date is 2026-11-02.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins the instant 2026-11-01T23:30Z and expects the Asia/Shanghai business date 202... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed PaymentService missing orderId null return with PaymentNotFoundException</title>   <subtitle>Missing orders now surface as PaymentNotFoundException instead of silently returning null.</subtitle>   <facts>     <fact>PaymentService.java now returns `repository.findByOrderId(orderId).orElseThrow(() -> new PaymentNotFoundException(orderId))` instead o... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JWT expiration boundary check in JwtVerifier to reject tokens expiring exactly at current instant</title>   <subtitle>JwtVerifier now treats expiration equal to the current time as expired, closing an off-by-one-in-time validity window.</subtitle>   <facts>     <fact>JwtVerifier expiration condition changed from claims.getExpiration().before(now) ... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsvExporter line ending normalization for multiline CSV fields</title>   <subtitle>Normalized embedded newlines to CRLF before quoting so multiline fields no longer inflate row counts.</subtitle>   <facts>     <fact>CsvExporter.java now replaces embedded line endings using `line.replaceAll("\\r?\\n", "\\r\\n")` before writing quoted output.</fact>... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment race condition using AtomicLong.incrementAndGet</title>   <subtitle>Replaced non-atomic get/set pair with AtomicLong.incrementAndGet to eliminate lost updates under concurrent access.</subtitle>   <facts>     <fact>CounterService.increment in src/main/java/com/acme/metrics/CounterService.java previously used `counter.set(c... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderRepository.findAllByCustomerId N+1 queries with @EntityGraph</title>   <subtitle>Order items are now fetched in the same query, cutting per-customer statement count from 1 + N to 1.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId was annotated with @EntityGraph(attributePaths = "items")</fact>     <fact>QueryCountTest now r... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator catastrophic backtracking with linear URL pattern</title>   <subtitle>Replaced nested-quantifier regex with linear `^https?://[^\s]+$` to prevent ReDoS on long input.</subtitle>   <facts>     <fact>`UrlValidator` changed its URL pattern to `^https?://[^\s]+$`, replacing a regex with nested quantifiers.</fact>     <fact>`UrlValidatorTe... [truncated]
```

### bug-file-encoding

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed FileReaderFactory to always open UTF-8 instead of platform default charset</title>   <subtitle>FileReaderFactory now uses StandardCharsets.UTF_8 explicitly, making accent handling deterministic across platform default charsets.</subtitle>   <facts>     <fact>src/main/java/com/acme/files/FileReaderFactory.java constructs InputStreamReader wi... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>feature</type>   <title>Hardened ConfigLoader.java with toUnmodifiableMap and typed accessors</title>   <subtitle>ConfigLoader now returns an unmodifiable property map and exposes typed accessor methods while preserving existing values.</subtitle>   <facts>     <fact>src/main/java/com/acme/config/ConfigLoader.java now builds its result via `properties.entrySet().stream().coll... [truncated]
```

### refactor-retry-policy

```text
```xml <observation>   <type>refactor</type>   <title>Refactored RetryPolicy delay calculation into BackoffStrategy.nextDelay</title>   <subtitle>Extracted backoff delay computation from RetryPolicy into BackoffStrategy, preserving the existing 100/200/400ms retry sequence.</subtitle>   <facts>     <fact>src/main/java/com/acme/resilience/RetryPolicy.java now delegates delay calculation via `backof... [truncated]
```

### refactor-event-bus

```text
```xml <observation>   <type>refactor</type>   <title>Refactored EventBus dispatch to use listener map instead of if/else chain</title>   <subtitle>Replaced conditional dispatch logic with a map-based listener lookup keyed by event type.</subtitle>   <facts>     <fact>EventBus.java now uses `listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event))` for dispatch instead of an... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai</title>   <subtitle>Reproduction test pins UTC 2026-11-01T23:30:00Z expecting business date 2026-11-02 in Asia/Shanghai.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone parses Instant "2026-11-01T23:30:00Z" and asserts report.dateAt(at, ZoneId.of("Asia/Shanghai... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 reproducing 401 instead of 423</title>   <subtitle>Test mocks a locked user and expects HTTP 423 but the endpoint currently returns 401</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 mocks `users.isLocked("bob")` to return `true`</fact>     <fact>The test expects `status().is... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized test</title>   <subtitle>New parameterized test verifies retry delay doubles across attempts 1-3.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles is annotated with @ParameterizedTest and @CsvSource({"1,100","2,200","3,400"})</fact>     <fact>RetryPolicyTest#delayDoubles asserts that policy.de... [truncated]
```

### test-integration-order

```text
```xml <observation>   <type>feature</type>   <title>Added OrderApiIT#createOrderReturns201 integration test with Testcontainers PostgreSQL 16</title>   <subtitle>New Spring Boot integration test verifies POST /orders returns 201 and Location header using real PostgreSQL.</subtitle>   <facts>     <fact>OrderApiIT is annotated with @SpringBootTest(webEnvironment = RANDOM_PORT) and lives in src/test... [truncated]
```

### test-parameterized-csv

```text
```xml <observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable for comma quote newline cases</title>   <subtitle>Parameterized CSV exporter test pins quoting behavior across three delimiter/quote edge-case inputs.</subtitle>   <facts>     <fact>src/test/java/com/acme/export/CsvExporterTest.java defines `quotingIsStable` as a `@ParameterizedTest` with `@ValueSource(strin... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull reproducing UserCache null race</title>   <subtitle>A parallel-read test now pins the intermittent null returned by UserCache under concurrent access.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull is defined in src/test/java/com/acme/cache/CacheConcurrencyTest.... [truncated]
```

### test-contract-openapi

```text
<observation>   <type>feature</type>   <title>Added OpenApiContractTest#generatedSpecMatchesCommittedFile contract test for docs/openapi.yaml</title>   <subtitle>New test diffs springdoc output against docs/openapi.yaml and fails on schema drift.</subtitle>   <facts>     <fact>Created src/test/java/com/acme/api/OpenApiContractTest.java in /repo/api containing test method OpenApiContractTest#genera... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Fixed SchedulerTest flakiness with Clock.fixed</title>   <subtitle>Replaced Thread.sleep with deterministic Clock.fixed to eliminate timing-dependent test failures.</subtitle>   <facts>     <fact>src/test/java/com/acme/scheduling/SchedulerTest.java now sets scheduler.clock using Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)</fact> ... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Fixed LogSanitizer credential redaction for password token secret</title>   <subtitle>LogSanitizer now masks password, token and secret values before they reach log output.</subtitle>   <facts>     <fact>LogSanitizer.java applies value.replaceAll("(password|token|secret)=\\S+", "$1=***") to redact credentials.</fact>     <fact>LogSanitizerTest#redactsCr... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Hardened AdminController endpoints with @PreAuthorize hasRole('ADMIN')</title>   <subtitle>Admin-only endpoints now enforce ROLE_ADMIN at the controller level, returning 403 for users lacking that role.</subtitle>   <facts>     <fact>AdminController.java now carries the annotation `@PreAuthorize("hasRole('ADMIN')")` applied to every endpoint.</fact>    ... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsrfFilter to reject state-changing requests with mismatched CSRF token</title>   <subtitle>CSRF protection now enforces token validation on POST requests, returning HTTP 403 for missing or mismatched tokens.</subtitle>   <facts>     <fact>CsrfFilter.java now calls csrfToken.matches(request) and rejects state-changing requests that fail validation... [truncated]
```

### security-secret-config

```text
```xml <observation>   <type>bugfix</type>   <title>Removed hardcoded API key sk-live-123 from application.yml</title>   <subtitle>Replaced literal secret with environment variable reference to prevent credential leakage.</subtitle>   <facts>     <fact>application.yml previously contained a literal API key value `sk-live-123`</fact>     <fact>application.yml now sets `api-key: ${ACME_API_KEY}` to ... [truncated]
```

### security-rate-limit

```text
<observation>   <type>bugfix</type>   <title>Fixed RateLimiter to return 429 when token bucket is empty</title>   <subtitle>RateLimiter now throws TooManyRequestsException on token bucket exhaustion instead of allowing the request through.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/RateLimiter.java now calls bucket.tryConsume(1) and throws TooManyRequestsException when it returns fa... [truncated]
```

### config-hikari-pool

```text
```xml <observation>   <type>change</type>   <title>Added Hikari maximum-pool-size and minimum-idle to application.yml</title>   <subtitle>Configured HikariCP connection pool sizing with maximum-pool-size set to 20 and minimum-idle set to 5.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets spring.datasource.hikari.maximum-pool-size to 20</fact>     <fact>src/main/resources/ap... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Changed logback-spring.xml root logger to INFO with JSON appender</title>   <subtitle>Root logger switched to INFO level and references the JSON appender for structured logging output.</subtitle>   <facts>     <fact>logback-spring.xml root logger level set to INFO</fact>     <fact>Root logger references appender "JSON" via appender-ref</fact>     <fact>... [truncated]
```

### config-cors

```text
<observation>   <type>feature</type>   <title>Added CORS restriction for /api/** in CorsConfig.java</title>   <subtitle>CorsConfig now restricts /api/** to https://app.acme.com with GET and POST methods only.</subtitle>   <facts>     <fact>CorsConfig.java registers CORS mapping for /api/** with allowedOrigins set to https://app.acme.com</fact>     <fact>CorsConfig.java restricts allowedMethods to ... [truncated]
```

### config-timeout

```text
<observation>   <type>change</type>   <title>Added 5 second response timeout to WebClientConfig</title>   <subtitle>WebClient HTTP calls now fail fast after 5 seconds instead of waiting indefinitely.</subtitle>   <facts>     <fact>WebClientConfig.java now configures HttpClient.create().responseTimeout(Duration.ofSeconds(5))</fact>     <fact>WebClientConfigTest reads the configured timeout value fr... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added HEALTHCHECK to Dockerfile targeting /actuator/health</title>   <subtitle>Dockerfile now runs a 30-second interval health check against the Spring Boot actuator endpoint.</subtitle>   <facts>     <fact>Dockerfile adds `HEALTHCHECK --interval=30s CMD curl -f http://localhost:8080/actuator/health || exit 1`</fact>     <fact>The health check interval ... [truncated]
```

### config-maven-plugin

```text
<observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 to pom.xml</title>   <subtitle>Maven build now runs JaCoCo code coverage during mvn verify.</subtitle>   <facts>     <fact>pom.xml declares org.jacoco:jacoco-maven-plugin version 0.8.12 as a build plugin.</fact>     <fact>Running mvn verify now produces a coverage report at target/site/jacoco/jacoco.xml.</fact>   </facts... [truncated]
```

### docs-api-observations

```text
<observation>   <type>change</type>   <title>Documented GET /api/observations pagination fields hasMore and items</title>   <subtitle>Added pagination contract documentation to docs/API.md matching the controller's response structure.</subtitle>   <facts>     <fact>docs/API.md now documents that GET /api/observations returns hasMore and items fields</fact>     <fact>The documented pagination field... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Added local setup steps to README.md with JDK 21 and docker compose postgres</title>   <subtitle>README.md now documents a three-step local setup for the Spring Boot project.</subtitle>   <facts>     <fact>README.md gained a "## Setup" section with three ordered steps.</fact>     <fact>Step 1 requires installing JDK 21.</fact>     <fact>Step 2 runs `doc... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Added docs/runbook.md documenting queue recovery procedure</title>   <subtitle>Runbook captures three-step queue recovery: inspect pending_messages, restart worker, verify /api/health.</subtitle>   <facts>     <fact>docs/runbook.md is a new runbook file covering queue recovery.</fact>     <fact>The runbook step 1 directs operators to check pending_messa... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Added 2.4.0 CHANGELOG.md entry for offline replay evaluation and V20 migration conflict fix</title>   <subtitle>CHANGELOG.md documents version 2.4.0 shipping offline replay evaluation and a V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md now contains a section headed "## 2.4.0 - 2026-11-03".</fact>     <fact>The 2.4.0 entry lists... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>decision</type>   <title>Added ADR 0007 extraction validation decision record</title>   <subtitle>Architecture decision record documenting validation of extracted facts against tool output before persistence.</subtitle>   <facts>     <fact>ADR 0007 was created at docs/adr/0007-extraction-validation.md</fact>     <fact>The ADR records the decision to validate extracted facts a... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 migration prerequisite in docs/MIGRATION.md</title>   <subtitle>Added V20 section requiring mem_sessions backup and app.manual_sentinel_reviewed=true before migration.</subtitle>   <facts>     <fact>docs/MIGRATION.md now contains a V20 section describing migration prerequisites.</fact>     <fact>The V20 migration requires backing up mem_s... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Added SECURITY.md with security@acme.com disclosure policy</title>   <subtitle>Created SECURITY.md establishing a private vulnerability disclosure channel and forbidding public issues.</subtitle>   <facts>     <fact>SECURITY.md was created in the repository root at /repo/SECURITY.md.</fact>     <fact>SECURITY.md directs vulnerability reports to the emai... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Added verify-local.sh PR requirement to CONTRIBUTING.md</title>   <subtitle>CONTRIBUTING.md now mandates running ./scripts/verify-local.sh before opening a pull request and names the four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md now requires contributors to run ./scripts/verify-local.sh before opening a pull request.</fact>     <fact>... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added readinessProbe /api/health:37777 to deployment.yaml</title>   <subtitle>Deployment now gates pod readiness on an HTTP health check against port 37777.</subtitle>   <facts>     <fact>deploy/deployment.yaml adds a readinessProbe using httpGet path /api/health on port 37777.</fact>     <fact>The readinessProbe sets initialDelaySeconds to 15.</fact>  ... [truncated]
```

### deploy-github-actions

```text
```xml <observation>   <type>change</type>   <title>Added JDK 21 and 25 matrix strategy to .github/workflows/ci.yml</title>   <subtitle>CI backend jobs now run against both JDK 21 and JDK 25 via a matrix strategy.</subtitle>   <facts>     <fact>.github/workflows/ci.yml now defines a strategy.matrix with java set to [21, 25]</fact>     <fact>The backend workflow executes against both JDK 21 and JDK... [truncated]
```

### deploy-docker-multistage

```text
<observation>   <type>change</type>   <title>Refactored Dockerfile to multi-stage build reducing image size 780MB to 210MB</title>   <subtitle>Dockerfile now separates Maven build stage from eclipse-temurin:21-jre runtime stage</subtitle>   <facts>     <fact>Dockerfile converted to multi-stage build separating build and runtime phases</fact>     <fact>Build stage uses maven to compile application ... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>bugfix</type>   <title>Hardened aws_iam_role_policy.deploy to AmazonS3ReadOnlyAccess in main.tf</title>   <subtitle>Replaced full S3 access on the deploy IAM role with the read-only managed policy to enforce least privilege.</subtitle>   <facts>     <fact>infra/main.tf's aws_iam_role_policy "deploy" now sets policy_arn to arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess.</fact>... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Raised replicaCount to 3 in infra/values.yaml with 2Gi memory cap</title>   <subtitle>Helm values scaled to three replicas with a 2Gi memory resource limit per pod.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount to 3</fact>     <fact>infra/values.yaml sets resources.limits.memory to 2Gi</fact>     <fact>helm template renders 3 pods w... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added scripts/rollback.sh for kubectl rollout undo of deployment/claude-mem</title>   <subtitle>New rollback script reverts the claude-mem deployment and waits for rollout completion.</subtitle>   <facts>     <fact>scripts/rollback.sh begins with `set -euo pipefail` for strict error handling.</fact>     <fact>scripts/rollback.sh runs `kubectl rollout u... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Enabled canary.weight 10 with errorThreshold 0.02 in canary.yaml</title>   <subtitle>Configured a 10 percent canary rollout that auto-rolls back above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true.</fact>     <fact>infra/canary.yaml sets canary.weight to 10, directing 10 percent of traffic to the ca... [truncated]
```

### skip-grep-search

```text
<observation>   <type>discovery</type>   <title>Located hasMore identifier in usePagination.ts and controllers</title>   <subtitle>Read-only search confirmed hasMore usage across pagination hook and three controllers.</subtitle>   <facts>     <fact>Search for pattern hasMore found matches in usePagination.ts and three controllers.</fact>     <fact>The search was read-only with no files modified.</... [truncated]
```

### skip-read-config-to-answer

```text
<skip>only read pom.xml to answer question, no changes made
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared verb class, (2) the declared subject tokens to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. The legacy keyword-coverage metric is reported alongside for before/after comparison.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
