# Model output quality evaluation

- Date: 2026-11-03
- Phase: after
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 82 unique; runs scored: 3; key repeat: 3; real calls: 108 (cap none); duration: 1540003 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 98% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 20/0/0/62 |
| format errors | 0 |
| validation rejections | 2 |
| call failures | 0 |
| title exact-hit rate (legacy) | 60% |
| title normalized hit rate (legacy) | 65% |
| title action-class hit rate (A6) | 77% (60 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 82% |
| title subject hit rate | 87% |
| title contract hit rate (action + subject + all identifiers) | 65% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 73% |
| title identifier hit rate | 100% |
| type accuracy | 92% |
| facts keyword precision (legacy) / recall | 74% / 93% |
| facts informative precision | 81% |
| facts trace-support rate | 100% |
| concepts precision / recall | 85% / 83% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 15432 / 31396 / 37705 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 81% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 77% | **FAIL** |
| A6b | title subject | >= 90% | 87% | **FAIL** |
| A6c | title identifiers | = 100% | 100% | PASS |
| A6 | all three title dimensions together | all pass | 65% | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 82 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 82 | 100% | 65% | 65% | 74% | 81% | 93% | 0 |
| 1 | 13 | 100% | 67% | 67% | 89% | 78% | 100% | 0 |
| 2 | 13 | 100% | 70% | 70% | 93% | 84% | 100% | 0 |

- average: skip 100%, title 67%, title contract 67%, facts P/R 85%/98%, facts actionable 81%
- worst run: skip 100%, title 65%, title contract 65%, facts P/R 74%/93%, facts actionable 78%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 11/12 | 11/12 | 7/12 |
| refactor | 9 | 8/9 | 7/9 | 6/9 |
| test-add | 9 | 9/9 | 9/9 | 8/9 |
| security-fix | 9 | 9/9 | 7/9 | 3/9 |
| config-change | 8 | 8/8 | 7/8 | 7/8 |
| docs-update | 8 | 8/8 | 8/8 | 4/8 |
| deploy-change | 7 | 7/7 | 6/7 | 4/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 10026 |
| bug-null-cache | bug-fix | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 7497 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 37705 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 100%/100% | 16611 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 15742 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 17851 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 50% | MISS | miss | match | 50%/50% | 100% | 50%/50% | 23379 |
| bug-race-counter | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 9546 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 10755 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 8395 |
| bug-regex-redos | bug-fix | observation | observation | pass | 75% | hit | miss | match | 50%/50% | 100% | 100%/100% | 22394 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 18224 |
| refactor-invoice | refactor | observation | observation | pass | 67% | MISS | miss | match | 67%/100% | 67% | 100%/100% | 22970 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 14965 |
| refactor-config-loader | refactor | observation | observation | pass | 33% | MISS | miss | MISS | 67%/100% | 67% | 50%/50% | 18937 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 7926 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 15890 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 100%/100% | 16615 |
| refactor-sql-repository | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 100% | 100%/50% | 11104 |
| refactor-event-bus | refactor | observation | validation-rejected | FAIL | - | - | - | - | - | - | - | 22639 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 50%/50% | 50% | 100%/100% | 16155 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 16711 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 31396 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 25%/50% | 100% | 100%/100% | 8539 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 18590 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/50% | 67% | 100%/100% | 17537 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 20020 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 9814 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 100%/100% | 15497 |
| test-flaky-clock | test-add | observation | observation | pass | 75% | MISS | miss | match | 67%/100% | 100% | 100%/100% | 15445 |
| security-redact-log | security-fix | observation | observation | pass | 100% | MISS | miss | MISS | 100%/100% | 50% | 50%/50% | 35688 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 100% | 50%/50% | 10772 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 50%/50% | 31642 |
| security-csrf | security-fix | observation | observation | pass | 67% | hit | miss | MISS | 50%/50% | 100% | 0%/0% | 19060 |
| security-secret-config | security-fix | observation | observation | pass | 50% | MISS | miss | match | 50%/50% | 100% | 50%/50% | 16730 |
| security-xxe | security-fix | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 11259 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | MISS | hit | match | 67%/100% | 100% | 100%/100% | 8389 |
| security-rate-limit | security-fix | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 50%/50% | 17683 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS | miss | match | 50%/100% | 100% | 100%/100% | 7534 |
| config-hikari-pool | config-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 31464 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 12744 |
| config-log-level | config-change | observation | observation | pass | 100% | MISS | hit | match | 33%/50% | 33% | 100%/100% | 17570 |
| config-cors | config-change | observation | observation | pass | 75% | hit | miss | match | 75%/100% | 75% | 100%/100% | 25767 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 7445 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 22367 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | MISS | hit | MISS | 100%/100% | 100% | 100%/100% | 18974 |
| config-maven-plugin | config-change | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 100% | 50%/50% | 6921 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 8206 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 18519 |
| docs-runbook | docs-update | observation | observation | pass | 100% | MISS | hit | match | 100%/100% | 0% | 100%/100% | 10613 |
| docs-changelog | docs-update | observation | observation | pass | 67% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 5306 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 100% | 100%/100% | 26141 |
| docs-migration-guide | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 10764 |
| docs-security-policy | docs-update | observation | observation | pass | 100% | MISS | hit | match | 67%/100% | 67% | 50%/50% | 23080 |
| docs-contributing | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 50% | 100%/100% | 8789 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | MISS | hit | match | 67%/100% | 67% | 100%/50% | 12090 |
| deploy-github-actions | deploy-change | observation | observation | pass | 75% | MISS | miss | match | 100%/100% | 100% | 100%/100% | 15432 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 100% | 50%/50% | 9654 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 67% | 100%/100% | 14784 |
| deploy-helm-values | deploy-change | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 100% | 100%/100% | 17043 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 33% | 100%/100% | 17886 |
| deploy-canary | deploy-change | observation | observation | pass | 50% | hit | miss | match | 67%/100% | 100% | 100%/100% | 10156 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 18450 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4178 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 21706 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4740 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 18197 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2483 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3226 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3284 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2356 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2342 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 17519 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3581 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2885 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2838 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2169 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 19348 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3074 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4338 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 20140 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4055 |

## Problems

- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-null-cache: validation rejected the observation: no verifiable facts after evidence check
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-csv-crlf: title coverage 50% (expected keywords [fix, csv, line, endings])
- bug-csv-crlf: title subject [line, endings] missing (got 'Fixed CsvExporter multiline field row count with CRLF normalisation')
- bug-csv-crlf: facts recall 50% (expected [CRLF, multilineFieldKeepsRowCount], got [CsvExporter now calls `writer.write(line.replaceAll("\\r?\\n", "\\r\\n"))` instead of writing the raw line., CsvExporterTest#multilineFieldKeepsRowCount passes after the change.])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-regex-redos: title coverage 75% (expected keywords [fix, redos, urlvalidator, regex])
- bug-regex-redos: facts recall 50% (expected [linear pattern, longInputFinishesUnder100ms], got [UrlValidator now compiles URL as Pattern.compile("^https?://[^\\s]+$") instead of the previous nested-quantifier regex, UrlValidatorTest#longInputFinishesUnder100ms passes after the change, confirming long-input performance is within the 100 ms budget])
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed FileReaderFactory platform-default charset by forcing UTF-8')
- refactor-invoice: title coverage 67% (expected keywords [refactor, invoiceservice, delegates])
- refactor-invoice: title subject [delegation] missing (got 'Refactored InvoiceService tax and rendering via TaxCalculator and InvoiceRenderer')
- refactor-config-loader: title coverage 33% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title action 'modify' missing: title leads 'secure' (got 'Hardened ConfigLoader property map immutability in ConfigLoader.java')
- refactor-config-loader: title verb class 'refactor' missing (got 'Hardened ConfigLoader property map immutability in ConfigLoader.java')
- refactor-config-loader: title subject [unmodifiable, map] missing (got 'Hardened ConfigLoader property map immutability in ConfigLoader.java')
- refactor-config-loader: type 'change' != expected 'refactor'
- refactor-config-loader: concepts recall 50% (expected [what-changed, pattern], got [what-changed, gotcha])
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [UserRepository.java now declares @Query("select u from User u where u.tenantId = :tenantId") for the tenant lookup.])
- refactor-sql-repository: concepts recall 50% (expected [what-changed, pattern], got [what-changed, what-changed])
- refactor-event-bus: validation rejected the observation: no verifiable facts after evidence check
- refactor-feature-flag: facts recall 50% (expected [Flags.isEnabled, on/off matrix], got [FeatureFlagService now returns `flags.isEnabled(flag, context)`, delegating evaluation to the Flags component., The edited file is src/main/java/com/acme/flags/FeatureFlagService.java.])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [src/test/java/com/acme/resilience/RetryPolicyTest.java contains a @ParameterizedTest method named delayDoubles, @CsvSource supplies inputs "1,100", "2,200", "3,400" mapping attempt numbers to expected delay values in milliseconds, The assertion assertThat(policy.delay(attempt)).isEqualTo(expected) verifies each attempt's computed delay, All 3 parameterized tests pass after writing the test class])
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable uses @ParameterizedTest with @ValueSource(strings = {"a,b", "a\"b", "a\nb"}) to cover three quoting scenarios, The test verifies quoting is stable for values containing commas, double-quotes, and newlines, All 3 parameterized cases pass against the current CsvExporter implementation])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-flaky-clock: title coverage 75% (expected keywords [fix, flaky, scheduler, clock])
- test-flaky-clock: title verb class 'fix' missing (got 'Replaced Thread.sleep with Clock.fixed in SchedulerTest')
- test-flaky-clock: title subject [flaky, clock] missing (got 'Replaced Thread.sleep with Clock.fixed in SchedulerTest')
- security-redact-log: title action 'secure' missing: title leads 'create' (got 'Added credential redaction in LogSanitizer.java')
- security-redact-log: title verb class 'redact' missing (got 'Added credential redaction in LogSanitizer.java')
- security-redact-log: type 'feature' != expected 'bugfix'
- security-redact-log: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, gotcha])
- security-sql-injection: title action 'secure' missing: title leads 'modify' (got 'Fixed SQL injection in UserDao.findByEmail with parameter placeholder')
- security-sql-injection: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Restricted AdminController endpoints authorization to ROLE_ADMIN')
- security-authz-admin: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-csrf: title coverage 67% (expected keywords [fix, csrf, csrffilter])
- security-csrf: title verb class 'fix' missing (got 'Hardened CsrfFilter token mismatch rejection in CsrfFilter.java')
- security-csrf: type 'feature' != expected 'bugfix'
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter.java now calls response.sendError(403) and returns early when csrfToken.matches(request) is false., CsrfFilterTest#postWithoutTokenIs403 passes after the change.])
- security-csrf: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, what-changed])
- security-secret-config: title coverage 50% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: title verb class 'remove' missing (got 'Redacted ACME_API_KEY in application.yml replacing sk-live-123 with ${ACME_API_KEY}')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Redacted ACME_API_KEY in application.yml replacing sk-live-123 with ${ACME_API_KEY}')
- security-secret-config: facts recall 50% (expected [ACME_API_KEY, SecretScanTest], got [src/main/resources/application.yml previously contained the literal API key `sk-live-123`., The `api-key` field now reads `${ACME_API_KEY}` from the environment instead of a hardcoded value.])
- security-secret-config: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-xxe: title coverage 67% (expected keywords [harden, xmlimportservice, xxe])
- security-xxe: title action 'secure' missing: title leads 'modify' (got 'Fixed XXE vulnerability in XmlImportService by disallowing DOCTYPE declarations')
- security-xxe: title verb class 'harden' missing (got 'Fixed XXE vulnerability in XmlImportService by disallowing DOCTYPE declarations')
- security-path-traversal: title action 'secure' missing: title leads 'modify' (got 'Fixed path traversal in FileDownloadController path resolution')
- security-rate-limit: title coverage 75% (expected keywords [add, rate, limit, ratelimiter])
- security-rate-limit: title action 'secure' missing: title leads 'modify' (got 'Fixed RateLimiter missing 429 response on exhausted token bucket')
- security-rate-limit: title verb class 'add' missing (got 'Fixed RateLimiter missing 429 response on exhausted token bucket')
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- config-log-level: title subject [log, level] missing (got 'Changed root logger to INFO with JSON appender in logback-spring.xml')
- config-log-level: facts recall 50% (expected [INFO, JSON appender], got [logback-spring.xml root logger level set to INFO, logback-spring.xml root logger appender-ref changed to JSON, LogbackConfigTest parses the logback-spring.xml configuration])
- config-cors: title coverage 75% (expected keywords [restrict, cors, corsconfig, origin])
- config-docker-healthcheck: title action 'modify' missing: title leads 'create' (got 'Added Docker HEALTHCHECK against /actuator/health in Dockerfile')
- config-docker-healthcheck: type 'feature' != expected 'change'
- config-maven-plugin: title action 'modify' missing: title leads 'create' (got 'Added jacoco-maven-plugin 0.8.12 in pom.xml')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title action 'document' missing: title leads 'create' (got 'Added local setup instructions in README.md')
- docs-readme-setup: title verb class 'change' missing (got 'Added local setup instructions in README.md')
- docs-runbook: title action 'document' missing: title leads 'create' (got 'Added queue recovery runbook in docs/runbook.md')
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title action 'document' missing: title leads 'modify' (got 'Changed CHANGELOG.md with 2.4.0 release notes')
- docs-migration-guide: title coverage 75% (expected keywords [document, v20, migration, sentinel])
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [what-changed, pattern])
- docs-security-policy: title action 'document' missing: title leads 'create' (got 'Added SECURITY.md vulnerability disclosure policy')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 67% (expected keywords [update, contributing, verify-local])
- docs-contributing: title verb class 'change' missing (got 'Documented pre-PR ./scripts/verify-local.sh requirement in CONTRIBUTING.md')
- deploy-k8s-probe: title action 'modify' missing: title leads 'create' (got 'Added readinessProbe on /api/health:37777 to deployment.yaml')
- deploy-k8s-probe: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])
- deploy-github-actions: title coverage 75% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: title action 'modify' missing: title leads 'create' (got 'Added Java version matrix [21, 25] in .github/workflows/ci.yml')
- deploy-docker-multistage: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- deploy-terraform-iam: title coverage 75% (expected keywords [restrict, iam, terraform, s3])
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 50% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: title verb class 'enable' missing (got 'Set canary weight to 10 and errorThreshold to 0.02 in infra/canary.yaml')

## Raw answers behind problems

### bug-offbyone-pagination

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset off-by-one in pagination</title>   <subtitle>Removed the erroneous -1 from the offset calculation so pagination starts at the correct row.</subtitle>   <facts>     <fact>OrderQuery.pageOffset in src/main/java/com/acme/orders/OrderQuery.java now returns `page * size` instead of `page * size - 1`.</fact>     <fact>OrderQ... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache.get concurrent null-placeholder race with computeIfAbsent in UserCache.java</title>   <subtitle>Replaced direct map get with computeIfAbsent so concurrent callers cannot both store null values.</subtitle>   <facts>     UserCache.get previously executed `return cache.get(id);` and now executes `return cache.computeIfAbsent(id, loader::loa... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai off-by-one-day bug</title>   <subtitle>New JUnit test pins 2026-11-01T23:30Z and expects the Asia/Shanghai business date 2026-11-02.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins the UTC timestamp 2026-11-01T23:30Z and expects the Asia/Shanghai business ... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration boundary in src/main/java/com/acme/auth/JwtVerifier.java</title>   <subtitle>JWT tokens expiring exactly at the current instant were accepted; now rejected by an inclusive boundary check.</subtitle>   <facts>     <fact>JwtVerifier changed the expiration check from claims.getExpiration().before(now) to claims.getExpiration().... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsvExporter multiline field row count with CRLF normalisation</title>   <subtitle>CsvExporter now rewrites embedded line endings to CRLF before quoting so multiline fields keep their row count.</subtitle>   <facts>     <fact>CsvExporter now calls `writer.write(line.replaceAll("\\r?\\n", "\\r\\n"))` instead of writing the raw line.</fact>     <fact... [truncated]
```

### bug-regex-redos

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed UrlValidator catastrophic backtracking in URL pattern</title>   <subtitle>Replaced nested-quantifier regex with linear pattern to eliminate exponential time on long inputs.</subtitle>   <facts>     <fact>UrlValidator now compiles URL as Pattern.compile("^https?://[^\\s]+$") instead of the previous nested-quantifier regex</fact>     <fact>Ur... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed FileReaderFactory platform-default charset by forcing UTF-8</title>   <subtitle>FileReaderFactory now always decodes input streams as UTF-8, preventing mojibake on non-UTF-8 JVM defaults.</subtitle>   <facts>     <fact>FileReaderFactory constructs InputStreamReader with StandardCharsets.UTF_8 instead of the platform default charset.</fact>     <fa... [truncated]
```

### refactor-invoice

```text
<observation>   <type>refactor</type>   <title>Refactored InvoiceService tax and rendering via TaxCalculator and InvoiceRenderer</title>   <subtitle>InvoiceService now delegates tax calculation, rendering, and mail to dedicated collaborators with no behaviour change.</subtitle>   <facts>     <fact>InvoiceService now calls renderer.render(taxCalculator.calculate(invoice), invoice)</fact>     <fact>... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>change</type>   <title>Hardened ConfigLoader property map immutability in ConfigLoader.java</title>   <subtitle>ConfigLoader now returns an unmodifiable properties map and adds typed accessors, leaving tested values unchanged.</subtitle>   <facts>     <fact>ConfigLoader builds its returned map with `entrySet().stream().collect(toUnmodifiableMap(...))` so callers cannot mutate... [truncated]
```

### refactor-sql-repository

```text
```xml <observation>   <type>refactor</type>   <title>Refactored UserRepository tenant lookup with JPQL @Query</title>   <subtitle>String-concatenated SQL in UserRepository replaced by a parameterized JPQL @Query selecting users by tenantId.</subtitle>   <facts>     <fact>UserRepository.java now declares @Query("select u from User u where u.tenantId = :tenantId") for the tenant lookup.</fact>     ... [truncated]
```

### refactor-event-bus

```text
<observation>   <type>refactor</type>   <title>Refactored EventBus dispatch with listener map keyed by event type</title>   <subtitle>Replaced the if/else dispatch chain with a listeners map lookup so each event type routes to its registered handlers.</subtitle>   <facts>     EventBus.java now uses `listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event))` for dispatch.     ... [truncated]
```

### refactor-feature-flag

```text
<observation>   <type>refactor</type>   <title>Refactored FeatureFlagService evaluation to delegate to Flags.isEnabled</title>   <subtitle>Flag evaluation logic moved from FeatureFlagService to Flags.isEnabled, preserving existing on/off behavior.</subtitle>   <facts>     <fact>FeatureFlagService now returns `flags.isEnabled(flag, context)`, delegating evaluation to the Flags component.</fact>    ... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone reproducing UTC date bug</title>   <subtitle>A new test pins the reported bug where reports date by UTC instead of the configured business timezone.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone parses Instant.parse("2026-11-01T23:30:00Z") and asserts report.dateAt(at, ZoneId.... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 test reproducing locked account 401</title>   <subtitle>Regression test pins the wrong HTTP status returned for locked accounts on POST /login.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true and performs POST /login expecting status ... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized cases for exponential backoff</title>   <subtitle>A parameterized JUnit test pins RetryPolicy delay values for attempts 1-3 at 100, 200 and 400 milliseconds.</subtitle>   <facts>     <fact>src/test/java/com/acme/resilience/RetryPolicyTest.java contains a @ParameterizedTest method named delayDoubles</fact... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable parameterized cases for CSV quoting</title>   <subtitle>New parameterized test pins stable quoting behavior for commas, quotes, and newlines in CsvExporter.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable uses @ParameterizedTest with @ValueSource(strings = {"a,b", "a\"b", "a\nb"}) to cover three quot... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull test reproducing UserCache null</title>   <subtitle>New concurrency regression test asserting UserCache never returns null under 1000 parallel readers.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull uses IntStream.range(0, 1000).parallel() to issue 1000 concurre... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Replaced Thread.sleep with Clock.fixed in SchedulerTest</title>   <subtitle>SchedulerTest now injects a deterministic fixed Clock to eliminate timing flakiness from 20 consecutive passing runs.</subtitle>   <facts>     <fact>SchedulerTest sets `scheduler.clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)` instead of relying on T... [truncated]
```

### security-redact-log

```text
<observation>   <type>feature</type>   <title>Added credential redaction in LogSanitizer.java</title>   <subtitle>LogSanitizer now masks password, token, and secret values with `***` before logging.</subtitle>   <facts>     <fact>`LogSanitizer.java` applies regex `(password|token|secret)=\S+` to detect credential keys in log values</fact>     <fact>Matched credential values are replaced with `***`... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed SQL injection in UserDao.findByEmail with parameter placeholder</title>   <subtitle>Replaced string concatenation with a parameterized query placeholder to prevent SQL injection.</subtitle>   <facts>     <fact>UserDao.findByEmail now calls jdbcTemplate.query with a parameter placeholder: jdbcTemplate.query("select * from users where email = ?", ma... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Restricted AdminController endpoints authorization to ROLE_ADMIN</title>   <subtitle>Class-level @PreAuthorize enforces ROLE_ADMIN on every AdminController endpoint, returning HTTP 403 for lower-privileged roles.</subtitle>   <facts>     <fact>AdminController.java now carries the annotation `@PreAuthorize("hasRole('ADMIN')")`, applying ROLE_ADMIN enforc... [truncated]
```

### security-csrf

```text
<observation>   <type>feature</type>   <title>Hardened CsrfFilter token mismatch rejection in CsrfFilter.java</title>   <subtitle>CsrfFilter now returns HTTP 403 when a state-changing request has a mismatched CSRF token.</subtitle>   <facts>     <fact>CsrfFilter.java now calls response.sendError(403) and returns early when csrfToken.matches(request) is false.</fact>     <fact>CsrfFilterTest#postWi... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Redacted ACME_API_KEY in application.yml replacing sk-live-123 with ${ACME_API_KEY}</title>   <subtitle>Removed hardcoded live API key from application.yml and switched to environment variable substitution.</subtitle>   <facts>     <fact>src/main/resources/application.yml previously contained the literal API key `sk-live-123`.</fact>     <fact>The `api-... [truncated]
```

### security-xxe

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed XXE vulnerability in XmlImportService by disallowing DOCTYPE declarations</title>   <subtitle>Disabling DOCTYPE in the XML parser factory blocks external entity injection attacks.</subtitle>   <facts>     <fact>XmlImportService sets factory feature "http://apache.org/xml/features/disallow-doctype-decl" to true</fact>     <fact>Setting disal... [truncated]
```

### security-path-traversal

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed path traversal in FileDownloadController path resolution</title>   <subtitle>FileDownloadController now rejects path names that escape the base directory via normalize and startsWith check.</subtitle>   <facts>     <fact>FileDownloadController resolves requested name with base.resolve(name).normalize() before use.</fact>     <fact>FileDownl... [truncated]
```

### security-rate-limit

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed RateLimiter missing 429 response on exhausted token bucket</title>   <subtitle>RateLimiter now throws TooManyRequestsException when tryConsume(1) fails, returning HTTP 429.</subtitle>   <facts>     <fact>RateLimiter.java now checks if (!bucket.tryConsume(1)) and throws TooManyRequestsException when the token bucket is empty</fact>     <fact... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>Session cookie now sets HttpOnly, Secure, and SameSite=Strict to prevent XSS and CSRF exposure.</subtitle>   <facts>     <fact>SessionCookieConfig sets cookie.setHttpOnly(true) on the session cookie.</fact>     <fact>SessionCookieConfig sets cookie.setSecure(true) on the session co... [truncated]
```

### config-log-level

```text
```xml <observation>   <type>change</type>   <title>Changed root logger to INFO with JSON appender in logback-spring.xml</title>   <subtitle>Switched root logging level from previous setting to INFO and bound it to the JSON appender.</subtitle>   <facts>     <fact>logback-spring.xml root logger level set to INFO</fact>     <fact>logback-spring.xml root logger appender-ref changed to JSON</fact>   ... [truncated]
```

### config-cors

```text
<observation>   <type>change</type>   <title>Restricted CORS /api/** to https://app.acme.com GET/POST in CorsConfig</title>   <subtitle>CorsConfig restricts /api/** cross-origin access to https://app.acme.com for GET and POST only; CorsConfigTest verifies a rejected origin.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/CorsConfig.java applies registry.addMapping("/api/**").allowedOrigi... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>feature</type>   <title>Added Docker HEALTHCHECK against /actuator/health in Dockerfile</title>   <subtitle>Docker image now performs a 30-second health check against the Spring Boot actuator health endpoint.</subtitle>   <facts>     <fact>Dockerfile now contains `HEALTHCHECK --interval=30s CMD curl -f http://localhost:8080/actuator/health || exit 1`.</fact>     <fact>The hea... [truncated]
```

### config-maven-plugin

```text
```xml <observation>   <type>change</type>   <title>Added jacoco-maven-plugin 0.8.12 in pom.xml</title>   <subtitle>Build now produces JaCoCo code coverage XML during the verify phase.</subtitle>   <facts>     <fact>pom.xml declares org.jacoco:jacoco-maven-plugin with version 0.8.12.</fact>     <fact>mvn verify writes the coverage report to target/site/jacoco/jacoco.xml.</fact>   </facts>   <narra... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Added local setup instructions in README.md</title>   <subtitle>README.md now documents a three-step local setup: JDK 21, docker compose postgres, and mvn spring-boot:run.</subtitle>   <facts>     <fact>README.md now contains a "## Setup" section with three numbered steps</fact>     <fact>Step 1 is "Install JDK 21"</fact>     <fact>Step 2 is "docker com... [truncated]
```

### docs-runbook

```text
<observation>   <type>change</type>   <title>Added queue recovery runbook in docs/runbook.md</title>   <subtitle>Documents three-step procedure for recovering a stalled queue worker: inspect pending messages, restart worker, verify health.</subtitle>   <facts>     <fact>docs/runbook.md documents queue recovery procedure with three steps: check pending_messages, restart worker, verify /api/health</... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Changed CHANGELOG.md with 2.4.0 release notes</title>   <subtitle>Changelog now documents offline replay evaluation feature and V20 migration conflict fix under version 2.4.0.</subtitle>   <facts>     <fact>CHANGELOG.md adds a 2.4.0 entry dated 2026-11-03</fact>     <fact>The 2.4.0 entry lists "Added offline replay evaluation" as a new capability</fact>... [truncated]
```

### docs-migration-guide

```text
```xml <observation>   <type>change</type>   <title>Documented V20 migration prerequisites in docs/MIGRATION.md</title>   <subtitle>Added V20 section requiring mem_sessions backup and app.manual_sentinel_reviewed=true before running migration.</subtitle>   <facts>     <fact>docs/MIGRATION.md documents V20 prerequisite to back up mem_sessions</fact>     <fact>docs/MIGRATION.md documents V20 prerequ... [truncated]
```

### docs-security-policy

```text
<observation>   <type>change</type>   <title>Added SECURITY.md vulnerability disclosure policy</title>   <subtitle>New security policy document establishes the private reporting channel and forbids public issue reports.</subtitle>   <facts>     <fact>SECURITY.md defines the vulnerability disclosure channel as security@acme.com.</fact>     <fact>SECURITY.md forbids opening public issues for vulnera... [truncated]
```

### docs-contributing

```text
<observation>   <type>change</type>   <title>Documented pre-PR ./scripts/verify-local.sh requirement in CONTRIBUTING.md</title>   <subtitle>Contributing guide now mandates running ./scripts/verify-local.sh before opening a pull request.</subtitle>   <facts>     <fact>CONTRIBUTING.md now states: "Run ./scripts/verify-local.sh before opening a pull request."</fact>     <fact>The requirement names fo... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added readinessProbe on /api/health:37777 to deployment.yaml</title>   <subtitle>Deployment manifest now gates pod readiness on an HTTP health check before routing traffic.</subtitle>   <facts>     <fact>deployment.yaml adds a readinessProbe using httpGet on path /api/health and port 37777.</fact>     <fact>readinessProbe sets initialDelaySeconds to 15.... [truncated]
```

### deploy-github-actions

```text
<observation>   <type>change</type>   <title>Added Java version matrix [21, 25] in .github/workflows/ci.yml</title>   <subtitle>Backend CI now tests against both JDK 21 and JDK 25 via a matrix strategy.</subtitle>   <facts>     <fact>.github/workflows/ci.yml defines a strategy.matrix with java: [21, 25]</fact>     <fact>The backend workflow executes on both JDK 21 and JDK 25</fact>     <fact>Workf... [truncated]
```

### deploy-docker-multistage

```text
```xml <observation>   <type>change</type>   <title>Converted Dockerfile to multi-stage build with eclipse-temurin:21-jre runtime</title>   <subtitle>Dockerfile split into maven build stage and eclipse-temurin:21-jre runtime stage, shrinking image from 780MB to 210MB.</subtitle>   <facts>     <fact>Dockerfile now builds in a build stage using maven and copies target/app.jar into a runtime stage.</... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>change</type>   <title>Restricted deploy role IAM policy to AmazonS3ReadOnlyAccess in infra/main.tf</title>   <subtitle>Deploy IAM role now attaches read-only S3 policy instead of full S3 access.</subtitle>   <facts>     <fact>infra/main.tf attaches policy ARN arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess to the aws_iam_role_policy resource named deploy.</fact>     <fact>The... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added rollback script for claude-mem deployment in scripts/rollback.sh</title>   <subtitle>New shell script rolls back the claude-mem Kubernetes deployment and waits for rollout status.</subtitle>   <facts>     <fact>scripts/rollback.sh runs `kubectl rollout undo deployment/claude-mem` followed by `kubectl rollout status deployment/claude-mem`</fact>  ... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Set canary weight to 10 and errorThreshold to 0.02 in infra/canary.yaml</title>   <subtitle>Enabled a 10 percent canary rollout with automatic rollback above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true, canary.weight to 10, and canary.errorThreshold to 0.02</fact>     <fact>The canary.weight value... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared verb class, (2) the declared subject tokens to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. The legacy keyword-coverage metric is reported alongside for before/after comparison.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
