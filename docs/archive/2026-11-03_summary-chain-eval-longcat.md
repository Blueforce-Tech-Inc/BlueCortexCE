# Session-summary quality evaluation

- Provider: `openai`
- Model: `deepseek-chat`
- Date: 2026-11-03
- Input: fixture replay
- Duration: 37 ms

## Headline

| metric | value |
|---|---|
| samples | 58 |
| call failures | 0 |
| format errors | 0 |
| request keyword coverage | 86% |
| request identifier hit rate | 81% |
| request trace-support rate | 93% |
| learned recall | 80% |
| learned identifier hit rate | 38% |
| learned trace-support rate | 65% |
| p50 / p95 / max ms | 6128 / 10650 / 11351 |

## Gates

| A7 | request keyword coverage | >= 80% | 86% | PASS |
| A8 | request trace support | >= 75% | 93% | PASS |
| A9 | learned recall | >= 70% | 80% | PASS |
| A10 | learned trace support | >= 65% | 65% | FAIL |

## Per sample

| id | category | req cov | req ids | req support | learned recall | learned ids | learned support | ms |
|---|---|---|---|---|---|---|---|---|
| sum-bug-offbyone-pagination-single | bug-fix | 100% | hit | 100% | 100% | MISS | 100% | 8736 |
| sum-bug-offbyone-pagination-single | bug-fix | 100% | hit | 100% | 100% | MISS | 0% | 5177 |
| sum-bug-offbyone-pagination-single | bug-fix | 50% | hit | 100% | 100% | hit | 67% | 6412 |
| sum-bug-offbyone-pagination-chain | bug-fix | 0% | hit | 100% | 100% | MISS | 0% | 5867 |
| sum-bug-null-cache-single | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 4623 |
| sum-bug-null-cache-chain | bug-fix | 50% | hit | 100% | 67% | MISS | 100% | 5759 |
| sum-bug-null-cache-chain | bug-fix | 100% | hit | 100% | 100% | MISS | 100% | 5797 |
| sum-bug-null-cache-chain | bug-fix | 50% | hit | 100% | 100% | hit | 50% | 7874 |
| sum-bug-timezone-report-single | bug-fix | 100% | MISS | 100% | 100% | MISS | 0% | 10650 |
| sum-bug-timezone-report-chain | bug-fix | 100% | MISS | 100% | 100% | MISS | 33% | 6403 |
| sum-bug-payment-missing-order-single | bug-fix | 100% | hit | 100% | 100% | hit | 50% | 9242 |
| sum-bug-payment-missing-order-chain | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 7016 |
| sum-bug-csv-crlf-single | bug-fix | 50% | hit | 100% | 100% | hit | 50% | 6790 |
| sum-bug-csv-crlf-chain | bug-fix | 100% | hit | 100% | 100% | hit | 0% | 5646 |
| sum-bug-race-counter-single | bug-fix | 50% | hit | 100% | 100% | hit | 100% | 4566 |
| sum-bug-race-counter-chain | bug-fix | 50% | hit | 100% | 100% | MISS | 100% | 5438 |
| sum-bug-n-plus-one-single | bug-fix | 100% | hit | 100% | 67% | MISS | 100% | 5617 |
| sum-bug-n-plus-one-single | bug-fix | 50% | hit | 100% | 0% | MISS | 50% | 5256 |
| sum-bug-n-plus-one-single | bug-fix | 100% | hit | 100% | 67% | MISS | 100% | 7520 |
| sum-bug-n-plus-one-chain | bug-fix | 50% | hit | 100% | 33% | MISS | 0% | 5005 |
| sum-refactor-invoice-single | refactor | 33% | hit | 100% | 100% | hit | 100% | 6097 |
| sum-refactor-invoice-chain | refactor | 100% | hit | 100% | 100% | hit | 50% | 3999 |
| sum-refactor-invoice-chain | refactor | 100% | hit | 100% | 100% | hit | 100% | 6733 |
| sum-refactor-invoice-chain | refactor | 100% | hit | 100% | 100% | hit | 50% | 6128 |
| sum-refactor-config-loader-single | refactor | 100% | hit | 100% | 67% | hit | 50% | 7798 |
| sum-refactor-config-loader-chain | refactor | 100% | hit | 100% | 67% | hit | 50% | 7616 |
| sum-refactor-sql-repository-single | refactor | 67% | hit | 100% | 67% | hit | 67% | 8485 |
| sum-refactor-sql-repository-chain | refactor | 100% | hit | 100% | 100% | hit | 100% | 7292 |
| sum-test-repro-timezone-single | test-add | 100% | MISS | 100% | 100% | MISS | 100% | 11351 |
| sum-test-repro-timezone-chain | test-add | 100% | MISS | 100% | 100% | MISS | 100% | 6971 |
| sum-test-repro-login-single | test-add | 67% | MISS | 0% | 100% | MISS | 0% | 11246 |
| sum-test-repro-login-chain | test-add | 100% | hit | 0% | 100% | MISS | 50% | 9130 |
| sum-test-integration-order-single | test-add | 100% | hit | 100% | 100% | MISS | 50% | 5155 |
| sum-test-integration-order-single | test-add | 100% | hit | 100% | 100% | MISS | 50% | 5880 |
| sum-test-integration-order-single | test-add | 100% | hit | 100% | 100% | MISS | 50% | 6702 |
| sum-test-integration-order-chain | test-add | 100% | hit | 100% | 33% | MISS | 50% | 9582 |
| sum-security-redact-log-single | security-fix | 67% | hit | 100% | 100% | MISS | 50% | 5489 |
| sum-security-redact-log-chain | security-fix | 100% | hit | 100% | 100% | MISS | 100% | 6173 |
| sum-security-redact-log-chain | security-fix | 100% | hit | 100% | 100% | MISS | 100% | 5368 |
| sum-security-redact-log-chain | security-fix | 100% | hit | 100% | 100% | MISS | 50% | 7387 |
| sum-security-sql-injection-single | security-fix | 100% | hit | 0% | 100% | hit | 0% | 7487 |
| sum-security-sql-injection-chain | security-fix | 100% | hit | 0% | 67% | MISS | 0% | 5531 |
| sum-security-path-traversal-single | security-fix | 100% | hit | 100% | 33% | MISS | 50% | 7136 |
| sum-security-path-traversal-chain | security-fix | 100% | hit | 100% | 33% | MISS | 100% | 7887 |
| sum-security-secret-config-single | security-fix | 100% | hit | 100% | 33% | MISS | 50% | 8232 |
| sum-security-secret-config-chain | security-fix | 100% | hit | 100% | 100% | hit | 100% | 6300 |
| sum-config-hikari-pool-single | config-change | 67% | hit | 100% | 67% | MISS | 0% | 6471 |
| sum-config-hikari-pool-chain | config-change | 100% | hit | 100% | 67% | MISS | 100% | 5555 |
| sum-config-feature-flags-single | config-change | 100% | MISS | 100% | 67% | hit | 100% | 4369 |
| sum-config-feature-flags-single | config-change | 100% | hit | 100% | 0% | MISS | 100% | 4555 |
| sum-config-feature-flags-single | config-change | 100% | hit | 100% | 0% | MISS | 50% | 5802 |
| sum-config-feature-flags-chain | config-change | 100% | MISS | 100% | 67% | hit | 100% | 5079 |
| sum-deploy-k8s-probe-single | deploy-change | 67% | MISS | 100% | 67% | MISS | 100% | 6386 |
| sum-deploy-k8s-probe-chain | deploy-change | 67% | MISS | 100% | 100% | MISS | 50% | 4910 |
| sum-deploy-k8s-probe-chain | deploy-change | 67% | MISS | 100% | 33% | MISS | 100% | 4284 |
| sum-deploy-k8s-probe-chain | deploy-change | 100% | MISS | 100% | 100% | hit | 50% | 5408 |
| sum-deploy-docker-multistage-single | deploy-change | 100% | hit | 100% | 67% | MISS | 100% | 5113 |
| sum-deploy-docker-multistage-chain | deploy-change | 100% | hit | 100% | 100% | hit | 100% | 5379 |

## Problems

- sum-bug-offbyone-pagination-single: learned lost identifiers [OrderQuery]
- sum-bug-offbyone-pagination-single: learned lost identifiers [OrderQuery]
- sum-bug-offbyone-pagination-single: learned not fully trace-grounded 0%
- sum-bug-offbyone-pagination-single: request coverage 50% (expected [offset, page])
- sum-bug-offbyone-pagination-single: learned not fully trace-grounded 67%
- sum-bug-offbyone-pagination-chain: request coverage 0% (expected [offset, page])
- sum-bug-offbyone-pagination-chain: learned lost identifiers [OrderQuery]
- sum-bug-offbyone-pagination-chain: learned not fully trace-grounded 0%
- sum-bug-null-cache-chain: request coverage 50% (expected [cache, concurrent])
- sum-bug-null-cache-chain: learned recall 67% (expected [computeIfAbsent, concurrent, null])
- sum-bug-null-cache-chain: learned lost identifiers [UserCache, computeIfAbsent]
- sum-bug-null-cache-chain: learned lost identifiers [UserCache, computeIfAbsent]
- sum-bug-null-cache-chain: request coverage 50% (expected [cache, concurrent])
- sum-bug-null-cache-chain: learned not fully trace-grounded 50%
- sum-bug-timezone-report-single: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-bug-timezone-report-single: learned lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-bug-timezone-report-single: learned not fully trace-grounded 0%
- sum-bug-timezone-report-chain: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-bug-timezone-report-chain: learned lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-bug-timezone-report-chain: learned not fully trace-grounded 33%
- sum-bug-payment-missing-order-single: learned not fully trace-grounded 50%
- sum-bug-csv-crlf-single: request coverage 50% (expected [csv, line endings])
- sum-bug-csv-crlf-single: learned not fully trace-grounded 50%
- sum-bug-csv-crlf-chain: learned not fully trace-grounded 0%
- sum-bug-race-counter-single: request coverage 50% (expected [counter, AtomicLong])
- sum-bug-race-counter-chain: request coverage 50% (expected [counter, AtomicLong])
- sum-bug-race-counter-chain: learned lost identifiers [CounterService, AtomicLong]
- sum-bug-n-plus-one-single: learned recall 67% (expected [EntityGraph, SQL, 1 + N])
- sum-bug-n-plus-one-single: learned lost identifiers [OrderRepository, EntityGraph]
- sum-bug-n-plus-one-single: request coverage 50% (expected [query, items])
- sum-bug-n-plus-one-single: learned recall 0% (expected [EntityGraph, SQL, 1 + N])
- sum-bug-n-plus-one-single: learned lost identifiers [OrderRepository, EntityGraph]
- sum-bug-n-plus-one-single: learned not fully trace-grounded 50%
- sum-bug-n-plus-one-single: learned recall 67% (expected [EntityGraph, SQL, 1 + N])
- sum-bug-n-plus-one-single: learned lost identifiers [OrderRepository, EntityGraph]
- sum-bug-n-plus-one-chain: request coverage 50% (expected [query, items])
- sum-bug-n-plus-one-chain: learned recall 33% (expected [EntityGraph, SQL, 1 + N])
- sum-bug-n-plus-one-chain: learned lost identifiers [OrderRepository, EntityGraph]
- sum-bug-n-plus-one-chain: learned not fully trace-grounded 0%
- sum-refactor-invoice-single: request coverage 33% (expected [invoice, tax, rendering])
- sum-refactor-invoice-chain: learned not fully trace-grounded 50%
- sum-refactor-invoice-chain: learned not fully trace-grounded 50%
- sum-refactor-config-loader-single: learned recall 67% (expected [unmodifiable, map, typed accessors])
- sum-refactor-config-loader-single: learned not fully trace-grounded 50%
- sum-refactor-config-loader-chain: learned recall 67% (expected [unmodifiable, map, typed accessors])
- sum-refactor-config-loader-chain: learned not fully trace-grounded 50%
- sum-refactor-sql-repository-single: request coverage 67% (expected [jpql, sql, repository])
- sum-refactor-sql-repository-single: learned recall 67% (expected [JPQL, @Query, tenant])
- sum-refactor-sql-repository-single: learned not fully trace-grounded 67%
- sum-test-repro-timezone-single: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-test-repro-timezone-single: learned lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-test-repro-timezone-chain: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-test-repro-timezone-chain: learned lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-test-repro-login-single: request coverage 67% (expected [login, locked, test])
- sum-test-repro-login-single: request lost identifiers [LoginControllerTest]
- sum-test-repro-login-single: request not fully trace-grounded 0%
- sum-test-repro-login-single: learned lost identifiers [LoginControllerTest, lockedAccountReturns423]
- sum-test-repro-login-single: learned not fully trace-grounded 0%
- sum-test-repro-login-chain: request not fully trace-grounded 0%
- sum-test-repro-login-chain: learned lost identifiers [LoginControllerTest, lockedAccountReturns423]
- sum-test-repro-login-chain: learned not fully trace-grounded 50%
- sum-test-integration-order-single: learned lost identifiers [OrderApiIT, createOrderReturns201]
- sum-test-integration-order-single: learned not fully trace-grounded 50%
- sum-test-integration-order-single: learned lost identifiers [OrderApiIT, createOrderReturns201]
- sum-test-integration-order-single: learned not fully trace-grounded 50%
- sum-test-integration-order-single: learned lost identifiers [OrderApiIT, createOrderReturns201]
- sum-test-integration-order-single: learned not fully trace-grounded 50%
- sum-test-integration-order-chain: learned recall 33% (expected [201, Location, PostgreSQL 16])
- sum-test-integration-order-chain: learned lost identifiers [OrderApiIT, createOrderReturns201]
- sum-test-integration-order-chain: learned not fully trace-grounded 50%
- sum-security-redact-log-single: request coverage 67% (expected [credentials, redact, log])
- sum-security-redact-log-single: learned lost identifiers [LogSanitizer, redactsCredentials]
- sum-security-redact-log-single: learned not fully trace-grounded 50%
- sum-security-redact-log-chain: learned lost identifiers [LogSanitizer, redactsCredentials]
- sum-security-redact-log-chain: learned lost identifiers [LogSanitizer, redactsCredentials]
- sum-security-redact-log-chain: learned lost identifiers [LogSanitizer, redactsCredentials]
- sum-security-redact-log-chain: learned not fully trace-grounded 50%
- sum-security-sql-injection-single: request not fully trace-grounded 0%
- sum-security-sql-injection-single: learned not fully trace-grounded 0%
- sum-security-sql-injection-chain: request not fully trace-grounded 0%
- sum-security-sql-injection-chain: learned recall 67% (expected [placeholder, parameter, UserDao])
- sum-security-sql-injection-chain: learned lost identifiers [UserDao]
- sum-security-sql-injection-chain: learned not fully trace-grounded 0%
- sum-security-path-traversal-single: learned recall 33% (expected [normalize, base directory, InvalidPathException])
- sum-security-path-traversal-single: learned lost identifiers [FileDownloadController]
- sum-security-path-traversal-single: learned not fully trace-grounded 50%
- sum-security-path-traversal-chain: learned recall 33% (expected [normalize, base directory, InvalidPathException])
- sum-security-path-traversal-chain: learned lost identifiers [FileDownloadController]
- sum-security-secret-config-single: learned recall 33% (expected [ACME_API_KEY, application.yml, environment])
- sum-security-secret-config-single: learned lost identifiers [application.yml, ACME_API_KEY]
- sum-security-secret-config-single: learned not fully trace-grounded 50%
- sum-config-hikari-pool-single: request coverage 67% (expected [hikari, pool, size])
- sum-config-hikari-pool-single: learned recall 67% (expected [maximum-pool-size, minimum-idle, 20])
- sum-config-hikari-pool-single: learned lost identifiers [maximum-pool-size, application.yml]
- sum-config-hikari-pool-single: learned not fully trace-grounded 0%
- sum-config-hikari-pool-chain: learned recall 67% (expected [maximum-pool-size, minimum-idle, 20])
- sum-config-hikari-pool-chain: learned lost identifiers [maximum-pool-size, application.yml]
- sum-config-feature-flags-single: request lost identifiers [checkout-v2, legacy-search]
- sum-config-feature-flags-single: learned recall 67% (expected [checkout-v2, legacy-search, false])
- sum-config-feature-flags-single: learned recall 0% (expected [checkout-v2, legacy-search, false])
- sum-config-feature-flags-single: learned lost identifiers [checkout-v2, legacy-search]
- sum-config-feature-flags-single: learned recall 0% (expected [checkout-v2, legacy-search, false])
- sum-config-feature-flags-single: learned lost identifiers [checkout-v2, legacy-search]
- sum-config-feature-flags-single: learned not fully trace-grounded 50%
- sum-config-feature-flags-chain: request lost identifiers [checkout-v2, legacy-search]
- sum-config-feature-flags-chain: learned recall 67% (expected [checkout-v2, legacy-search, false])
- sum-deploy-k8s-probe-single: request coverage 67% (expected [readiness, probe, health])
- sum-deploy-k8s-probe-single: request lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-single: learned recall 67% (expected [readinessProbe, /api/health, initialDelaySeconds])
- sum-deploy-k8s-probe-single: learned lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: request coverage 67% (expected [readiness, probe, health])
- sum-deploy-k8s-probe-chain: request lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: learned lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: learned not fully trace-grounded 50%
- sum-deploy-k8s-probe-chain: request coverage 67% (expected [readiness, probe, health])
- sum-deploy-k8s-probe-chain: request lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: learned recall 33% (expected [readinessProbe, /api/health, initialDelaySeconds])
- sum-deploy-k8s-probe-chain: learned lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: request lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: learned not fully trace-grounded 50%
- sum-deploy-docker-multistage-single: learned recall 67% (expected [eclipse-temurin:21-jre, multi-stage, 780MB])
- sum-deploy-docker-multistage-single: learned lost identifiers [Dockerfile, eclipse-temurin:21-jre]

## Method and limitations

- The system prompt is the production observation digest; the user prompt is the production `prompts/summary.txt` with `{{lastAssistantMessage}}` filled.
- `request` coverage and identifiers measure whether the summary names what the session was about; `learned` recall measures whether it kept the technical anchors.
- Trace support splits `request`/`learned` into clauses. A clause is grounded when every concrete anchor it names (CamelCase identifier, path/URL, number) occurs verbatim in the recorded tool trace; a clause that names no anchor must pass the facts-support rule (>= half of its significant tokens present). Paraphrasing a traced change is allowed; an invented object or number is not. `request` may additionally use the last assistant message as evidence.
- The golden set is hand-authored for this prompt, not a sample of production traffic.
