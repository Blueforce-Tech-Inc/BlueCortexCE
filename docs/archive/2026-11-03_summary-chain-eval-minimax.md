# Session-summary quality evaluation

- Provider: `openai`
- Model: `deepseek-chat`
- Date: 2026-11-03
- Input: fixture replay
- Duration: 34 ms

## Headline

| metric | value |
|---|---|
| samples | 58 |
| call failures | 0 |
| format errors | 0 |
| request keyword coverage | 94% |
| request identifier hit rate | 74% |
| request trace-support rate | 76% |
| learned recall | 72% |
| learned identifier hit rate | 24% |
| learned trace-support rate | 72% |
| p50 / p95 / max ms | 1883 / 5223 / 8788 |

## Gates

| A7 | request keyword coverage | >= 80% | 94% | PASS |
| A8 | request trace support | >= 75% | 76% | PASS |
| A9 | learned recall | >= 70% | 72% | PASS |
| A10 | learned trace support | >= 65% | 72% | PASS |

## Per sample

| id | category | req cov | req ids | req support | learned recall | learned ids | learned support | ms |
|---|---|---|---|---|---|---|---|---|
| sum-bug-offbyone-pagination-single | bug-fix | 100% | hit | 0% | 100% | hit | 100% | 2029 |
| sum-bug-offbyone-pagination-single | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 1818 |
| sum-bug-offbyone-pagination-single | bug-fix | 100% | hit | 100% | 67% | MISS | 100% | 1564 |
| sum-bug-offbyone-pagination-chain | bug-fix | 100% | hit | 100% | 100% | MISS | 100% | 1600 |
| sum-bug-null-cache-single | bug-fix | 100% | hit | 100% | 100% | MISS | 100% | 1778 |
| sum-bug-null-cache-chain | bug-fix | 100% | hit | 100% | 100% | MISS | 50% | 2089 |
| sum-bug-null-cache-chain | bug-fix | 100% | hit | 100% | 100% | MISS | 100% | 1883 |
| sum-bug-null-cache-chain | bug-fix | 100% | hit | 100% | 100% | MISS | 50% | 1712 |
| sum-bug-timezone-report-single | bug-fix | 100% | MISS | 0% | 100% | MISS | 50% | 2357 |
| sum-bug-timezone-report-chain | bug-fix | 100% | MISS | 100% | 100% | MISS | 50% | 2430 |
| sum-bug-payment-missing-order-single | bug-fix | 100% | hit | 100% | 50% | hit | 100% | 1353 |
| sum-bug-payment-missing-order-chain | bug-fix | 100% | hit | 0% | 100% | hit | 100% | 5223 |
| sum-bug-csv-crlf-single | bug-fix | 50% | hit | 0% | 100% | MISS | 50% | 2045 |
| sum-bug-csv-crlf-chain | bug-fix | 100% | MISS | 0% | 100% | MISS | 50% | 1772 |
| sum-bug-race-counter-single | bug-fix | 50% | hit | 100% | 100% | MISS | 100% | 1672 |
| sum-bug-race-counter-chain | bug-fix | 50% | hit | 100% | 100% | MISS | 100% | 2513 |
| sum-bug-n-plus-one-single | bug-fix | 100% | hit | 100% | 67% | MISS | 100% | 2700 |
| sum-bug-n-plus-one-single | bug-fix | 100% | hit | 100% | 67% | MISS | 50% | 2386 |
| sum-bug-n-plus-one-single | bug-fix | 100% | hit | 100% | 67% | MISS | 100% | 5095 |
| sum-bug-n-plus-one-chain | bug-fix | 100% | hit | 100% | 33% | MISS | 0% | 1551 |
| sum-refactor-invoice-single | refactor | 100% | hit | 100% | 100% | hit | 50% | 1907 |
| sum-refactor-invoice-chain | refactor | 100% | hit | 100% | 100% | hit | 100% | 1980 |
| sum-refactor-invoice-chain | refactor | 100% | hit | 100% | 0% | hit | 100% | 2059 |
| sum-refactor-invoice-chain | refactor | 100% | hit | 100% | 100% | hit | 100% | 2291 |
| sum-refactor-config-loader-single | refactor | 100% | hit | 100% | 33% | hit | 100% | 1587 |
| sum-refactor-config-loader-chain | refactor | 100% | hit | 100% | 67% | MISS | 100% | 1412 |
| sum-refactor-sql-repository-single | refactor | 100% | hit | 100% | 100% | MISS | 100% | 1733 |
| sum-refactor-sql-repository-chain | refactor | 100% | hit | 100% | 100% | MISS | 100% | 1993 |
| sum-test-repro-timezone-single | test-add | 100% | MISS | 100% | 100% | hit | 50% | 2976 |
| sum-test-repro-timezone-chain | test-add | 100% | MISS | 100% | 50% | MISS | 100% | 3580 |
| sum-test-repro-login-single | test-add | 100% | hit | 0% | 100% | MISS | 0% | 1646 |
| sum-test-repro-login-chain | test-add | 67% | MISS | 0% | 100% | MISS | 0% | 1694 |
| sum-test-integration-order-single | test-add | 100% | MISS | 0% | 67% | MISS | 0% | 1560 |
| sum-test-integration-order-single | test-add | 100% | hit | 0% | 33% | MISS | 100% | 1883 |
| sum-test-integration-order-single | test-add | 100% | MISS | 0% | 100% | MISS | 50% | 1613 |
| sum-test-integration-order-chain | test-add | 100% | hit | 0% | 100% | MISS | 50% | 2759 |
| sum-security-redact-log-single | security-fix | 67% | hit | 100% | 0% | MISS | 100% | 8788 |
| sum-security-redact-log-chain | security-fix | 100% | hit | 100% | 100% | MISS | 50% | 1890 |
| sum-security-redact-log-chain | security-fix | 100% | hit | 100% | 100% | MISS | 50% | 3023 |
| sum-security-redact-log-chain | security-fix | 100% | hit | 100% | 100% | MISS | 100% | 2057 |
| sum-security-sql-injection-single | security-fix | 100% | hit | 0% | 67% | MISS | 50% | 3696 |
| sum-security-sql-injection-chain | security-fix | 100% | hit | 0% | 33% | MISS | 0% | 1724 |
| sum-security-path-traversal-single | security-fix | 100% | hit | 100% | 33% | MISS | 0% | 1531 |
| sum-security-path-traversal-chain | security-fix | 100% | hit | 100% | 67% | MISS | 0% | 2300 |
| sum-security-secret-config-single | security-fix | 100% | hit | 100% | 100% | hit | 100% | 1405 |
| sum-security-secret-config-chain | security-fix | 100% | hit | 100% | 67% | MISS | 100% | 1695 |
| sum-config-hikari-pool-single | config-change | 100% | MISS | 100% | 0% | MISS | 100% | 1625 |
| sum-config-hikari-pool-chain | config-change | 67% | MISS | 100% | 67% | MISS | 100% | 1836 |
| sum-config-feature-flags-single | config-change | 100% | hit | 100% | 0% | MISS | 100% | 1650 |
| sum-config-feature-flags-single | config-change | 100% | MISS | 100% | 67% | hit | 100% | 1849 |
| sum-config-feature-flags-single | config-change | 50% | hit | 100% | 67% | hit | 100% | 1673 |
| sum-config-feature-flags-chain | config-change | 100% | hit | 100% | 67% | hit | 100% | 1344 |
| sum-deploy-k8s-probe-single | deploy-change | 100% | MISS | 100% | 33% | MISS | 100% | 2388 |
| sum-deploy-k8s-probe-chain | deploy-change | 67% | MISS | 100% | 33% | MISS | 50% | 1491 |
| sum-deploy-k8s-probe-chain | deploy-change | 67% | MISS | 100% | 33% | MISS | 50% | 1926 |
| sum-deploy-k8s-probe-chain | deploy-change | 100% | MISS | 100% | 33% | MISS | 100% | 1759 |
| sum-deploy-docker-multistage-single | deploy-change | 100% | hit | 100% | 33% | MISS | 0% | 2946 |
| sum-deploy-docker-multistage-chain | deploy-change | 100% | hit | 0% | 67% | MISS | 100% | 6801 |

## Problems

- sum-bug-offbyone-pagination-single: request not fully trace-grounded 0%
- sum-bug-offbyone-pagination-single: learned recall 67% (expected [page, size, offset])
- sum-bug-offbyone-pagination-single: learned lost identifiers [OrderQuery]
- sum-bug-offbyone-pagination-chain: learned lost identifiers [OrderQuery]
- sum-bug-null-cache-single: learned lost identifiers [UserCache, computeIfAbsent]
- sum-bug-null-cache-chain: learned lost identifiers [UserCache, computeIfAbsent]
- sum-bug-null-cache-chain: learned not fully trace-grounded 50%
- sum-bug-null-cache-chain: learned lost identifiers [UserCache, computeIfAbsent]
- sum-bug-null-cache-chain: learned lost identifiers [UserCache, computeIfAbsent]
- sum-bug-null-cache-chain: learned not fully trace-grounded 50%
- sum-bug-timezone-report-single: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-bug-timezone-report-single: request not fully trace-grounded 0%
- sum-bug-timezone-report-single: learned lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-bug-timezone-report-single: learned not fully trace-grounded 50%
- sum-bug-timezone-report-chain: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-bug-timezone-report-chain: learned lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-bug-timezone-report-chain: learned not fully trace-grounded 50%
- sum-bug-payment-missing-order-single: learned recall 50% (expected [PaymentNotFoundException, orderId])
- sum-bug-payment-missing-order-chain: request not fully trace-grounded 0%
- sum-bug-csv-crlf-single: request coverage 50% (expected [csv, line endings])
- sum-bug-csv-crlf-single: request not fully trace-grounded 0%
- sum-bug-csv-crlf-single: learned lost identifiers [CsvExporter]
- sum-bug-csv-crlf-single: learned not fully trace-grounded 50%
- sum-bug-csv-crlf-chain: request lost identifiers [CsvExporter]
- sum-bug-csv-crlf-chain: request not fully trace-grounded 0%
- sum-bug-csv-crlf-chain: learned lost identifiers [CsvExporter]
- sum-bug-csv-crlf-chain: learned not fully trace-grounded 50%
- sum-bug-race-counter-single: request coverage 50% (expected [counter, AtomicLong])
- sum-bug-race-counter-single: learned lost identifiers [CounterService, AtomicLong]
- sum-bug-race-counter-chain: request coverage 50% (expected [counter, AtomicLong])
- sum-bug-race-counter-chain: learned lost identifiers [CounterService, AtomicLong]
- sum-bug-n-plus-one-single: learned recall 67% (expected [EntityGraph, SQL, 1 + N])
- sum-bug-n-plus-one-single: learned lost identifiers [OrderRepository, EntityGraph]
- sum-bug-n-plus-one-single: learned recall 67% (expected [EntityGraph, SQL, 1 + N])
- sum-bug-n-plus-one-single: learned lost identifiers [OrderRepository, EntityGraph]
- sum-bug-n-plus-one-single: learned not fully trace-grounded 50%
- sum-bug-n-plus-one-single: learned recall 67% (expected [EntityGraph, SQL, 1 + N])
- sum-bug-n-plus-one-single: learned lost identifiers [OrderRepository, EntityGraph]
- sum-bug-n-plus-one-chain: learned recall 33% (expected [EntityGraph, SQL, 1 + N])
- sum-bug-n-plus-one-chain: learned lost identifiers [OrderRepository, EntityGraph]
- sum-bug-n-plus-one-chain: learned not fully trace-grounded 0%
- sum-refactor-invoice-single: learned not fully trace-grounded 50%
- sum-refactor-invoice-chain: learned recall 0% (expected [TaxCalculator, InvoiceRenderer, MailGateway])
- sum-refactor-config-loader-single: learned recall 33% (expected [unmodifiable, map, typed accessors])
- sum-refactor-config-loader-chain: learned recall 67% (expected [unmodifiable, map, typed accessors])
- sum-refactor-config-loader-chain: learned lost identifiers [ConfigLoader]
- sum-refactor-sql-repository-single: learned lost identifiers [UserRepository, @Query]
- sum-refactor-sql-repository-chain: learned lost identifiers [UserRepository, @Query]
- sum-test-repro-timezone-single: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-test-repro-timezone-single: learned not fully trace-grounded 50%
- sum-test-repro-timezone-chain: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-test-repro-timezone-chain: learned recall 50% (expected [Asia/Shanghai, 2026-11-02])
- sum-test-repro-timezone-chain: learned lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-test-repro-login-single: request not fully trace-grounded 0%
- sum-test-repro-login-single: learned lost identifiers [LoginControllerTest, lockedAccountReturns423]
- sum-test-repro-login-single: learned not fully trace-grounded 0%
- sum-test-repro-login-chain: request coverage 67% (expected [login, locked, test])
- sum-test-repro-login-chain: request lost identifiers [LoginControllerTest]
- sum-test-repro-login-chain: request not fully trace-grounded 0%
- sum-test-repro-login-chain: learned lost identifiers [LoginControllerTest, lockedAccountReturns423]
- sum-test-repro-login-chain: learned not fully trace-grounded 0%
- sum-test-integration-order-single: request lost identifiers [OrderApiIT]
- sum-test-integration-order-single: request not fully trace-grounded 0%
- sum-test-integration-order-single: learned recall 67% (expected [201, Location, PostgreSQL 16])
- sum-test-integration-order-single: learned lost identifiers [OrderApiIT, createOrderReturns201]
- sum-test-integration-order-single: learned not fully trace-grounded 0%
- sum-test-integration-order-single: request not fully trace-grounded 0%
- sum-test-integration-order-single: learned recall 33% (expected [201, Location, PostgreSQL 16])
- sum-test-integration-order-single: learned lost identifiers [OrderApiIT, createOrderReturns201]
- sum-test-integration-order-single: request lost identifiers [OrderApiIT]
- sum-test-integration-order-single: request not fully trace-grounded 0%
- sum-test-integration-order-single: learned lost identifiers [OrderApiIT, createOrderReturns201]
- sum-test-integration-order-single: learned not fully trace-grounded 50%
- sum-test-integration-order-chain: request not fully trace-grounded 0%
- sum-test-integration-order-chain: learned lost identifiers [OrderApiIT, createOrderReturns201]
- sum-test-integration-order-chain: learned not fully trace-grounded 50%
- sum-security-redact-log-single: request coverage 67% (expected [credentials, redact, log])
- sum-security-redact-log-single: learned recall 0% (expected [password, token, secret])
- sum-security-redact-log-single: learned lost identifiers [LogSanitizer, redactsCredentials]
- sum-security-redact-log-chain: learned lost identifiers [LogSanitizer, redactsCredentials]
- sum-security-redact-log-chain: learned not fully trace-grounded 50%
- sum-security-redact-log-chain: learned lost identifiers [LogSanitizer, redactsCredentials]
- sum-security-redact-log-chain: learned not fully trace-grounded 50%
- sum-security-redact-log-chain: learned lost identifiers [LogSanitizer, redactsCredentials]
- sum-security-sql-injection-single: request not fully trace-grounded 0%
- sum-security-sql-injection-single: learned recall 67% (expected [placeholder, parameter, UserDao])
- sum-security-sql-injection-single: learned lost identifiers [UserDao]
- sum-security-sql-injection-single: learned not fully trace-grounded 50%
- sum-security-sql-injection-chain: request not fully trace-grounded 0%
- sum-security-sql-injection-chain: learned recall 33% (expected [placeholder, parameter, UserDao])
- sum-security-sql-injection-chain: learned lost identifiers [UserDao]
- sum-security-sql-injection-chain: learned not fully trace-grounded 0%
- sum-security-path-traversal-single: learned recall 33% (expected [normalize, base directory, InvalidPathException])
- sum-security-path-traversal-single: learned lost identifiers [FileDownloadController]
- sum-security-path-traversal-single: learned not fully trace-grounded 0%
- sum-security-path-traversal-chain: learned recall 67% (expected [normalize, base directory, InvalidPathException])
- sum-security-path-traversal-chain: learned lost identifiers [FileDownloadController]
- sum-security-path-traversal-chain: learned not fully trace-grounded 0%
- sum-security-secret-config-chain: learned recall 67% (expected [ACME_API_KEY, application.yml, environment])
- sum-security-secret-config-chain: learned lost identifiers [application.yml, ACME_API_KEY]
- sum-config-hikari-pool-single: request lost identifiers [application.yml]
- sum-config-hikari-pool-single: learned recall 0% (expected [maximum-pool-size, minimum-idle, 20])
- sum-config-hikari-pool-single: learned lost identifiers [maximum-pool-size, application.yml]
- sum-config-hikari-pool-chain: request coverage 67% (expected [hikari, pool, size])
- sum-config-hikari-pool-chain: request lost identifiers [application.yml]
- sum-config-hikari-pool-chain: learned recall 67% (expected [maximum-pool-size, minimum-idle, 20])
- sum-config-hikari-pool-chain: learned lost identifiers [maximum-pool-size, application.yml]
- sum-config-feature-flags-single: learned recall 0% (expected [checkout-v2, legacy-search, false])
- sum-config-feature-flags-single: learned lost identifiers [checkout-v2, legacy-search]
- sum-config-feature-flags-single: request lost identifiers [checkout-v2, legacy-search]
- sum-config-feature-flags-single: learned recall 67% (expected [checkout-v2, legacy-search, false])
- sum-config-feature-flags-single: request coverage 50% (expected [feature, flags])
- sum-config-feature-flags-single: learned recall 67% (expected [checkout-v2, legacy-search, false])
- sum-config-feature-flags-chain: learned recall 67% (expected [checkout-v2, legacy-search, false])
- sum-deploy-k8s-probe-single: request lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-single: learned recall 33% (expected [readinessProbe, /api/health, initialDelaySeconds])
- sum-deploy-k8s-probe-single: learned lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: request coverage 67% (expected [readiness, probe, health])
- sum-deploy-k8s-probe-chain: request lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: learned recall 33% (expected [readinessProbe, /api/health, initialDelaySeconds])
- sum-deploy-k8s-probe-chain: learned lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: learned not fully trace-grounded 50%
- sum-deploy-k8s-probe-chain: request coverage 67% (expected [readiness, probe, health])
- sum-deploy-k8s-probe-chain: request lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: learned recall 33% (expected [readinessProbe, /api/health, initialDelaySeconds])
- sum-deploy-k8s-probe-chain: learned lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: learned not fully trace-grounded 50%
- sum-deploy-k8s-probe-chain: request lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: learned recall 33% (expected [readinessProbe, /api/health, initialDelaySeconds])
- sum-deploy-k8s-probe-chain: learned lost identifiers [deployment.yaml, /api/health]
- sum-deploy-docker-multistage-single: learned recall 33% (expected [eclipse-temurin:21-jre, multi-stage, 780MB])
- sum-deploy-docker-multistage-single: learned lost identifiers [Dockerfile, eclipse-temurin:21-jre]
- sum-deploy-docker-multistage-single: learned not fully trace-grounded 0%
- sum-deploy-docker-multistage-chain: request not fully trace-grounded 0%
- sum-deploy-docker-multistage-chain: learned recall 67% (expected [eclipse-temurin:21-jre, multi-stage, 780MB])
- sum-deploy-docker-multistage-chain: learned lost identifiers [Dockerfile, eclipse-temurin:21-jre]

## Method and limitations

- The system prompt is the production observation digest; the user prompt is the production `prompts/summary.txt` with `{{lastAssistantMessage}}` filled.
- `request` coverage and identifiers measure whether the summary names what the session was about; `learned` recall measures whether it kept the technical anchors.
- Trace support splits `request`/`learned` into clauses. A clause is grounded when every concrete anchor it names (CamelCase identifier, path/URL, number) occurs verbatim in the recorded tool trace; a clause that names no anchor must pass the facts-support rule (>= half of its significant tokens present). Paraphrasing a traced change is allowed; an invented object or number is not. `request` may additionally use the last assistant message as evidence.
- The golden set is hand-authored for this prompt, not a sample of production traffic.
