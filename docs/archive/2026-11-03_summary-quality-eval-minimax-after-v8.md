# Session-summary quality evaluation

- Provider: `anthropic`
- Model: `MiniMax-M3`
- Date: 2026-11-03
- Input: live provider calls
- Duration: 160670 ms

## Headline

| metric | value |
|---|---|
| samples | 58 |
| call failures | 0 |
| format errors | 4 |
| request keyword coverage | 97% |
| request identifier hit rate | 87% |
| request trace-support rate | 83% |
| learned recall | 90% |
| learned identifier hit rate | 93% |
| learned trace-support rate | 87% |
| p50 / p95 / max ms | 2000 / 8210 / 10776 |

## Gates

| A7 | request keyword coverage | >= 80% | 97% | PASS |
| A8 | request trace support | >= 75% | 83% | PASS |
| A9 | learned recall | >= 70% | 90% | PASS |
| A10 | learned trace support | >= 60% | 87% | PASS |

## Per sample

| id | category | req cov | req ids | req support | learned recall | learned ids | learned support | ms |
|---|---|---|---|---|---|---|---|---|
| sum-bug-offbyone-pagination-single | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 2632 |
| sum-bug-offbyone-pagination-single | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 3221 |
| sum-bug-offbyone-pagination-single | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 3006 |
| sum-bug-offbyone-pagination-chain | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 8740 |
| sum-bug-null-cache-single | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 2353 |
| sum-bug-null-cache-chain | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 1778 |
| sum-bug-null-cache-chain | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 1690 |
| sum-bug-null-cache-chain | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 1739 |
| sum-bug-timezone-report-single | bug-fix | 100% | MISS | 100% | 100% | hit | 100% | 2656 |
| sum-bug-timezone-report-chain | bug-fix | 100% | MISS | 100% | 100% | hit | 100% | 1555 |
| sum-bug-payment-missing-order-single | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 1855 |
| sum-bug-payment-missing-order-chain | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 1303 |
| sum-bug-csv-crlf-single | bug-fix | 100% | hit | 0% | 100% | hit | 50% | 1879 |
| sum-bug-csv-crlf-chain | bug-fix | 100% | hit | 0% | 100% | hit | 0% | 3536 |
| sum-bug-race-counter-single | bug-fix | 50% | hit | 100% | 100% | hit | 100% | 1955 |
| sum-bug-race-counter-chain | bug-fix | 50% | hit | 100% | 100% | hit | 100% | 2390 |
| sum-bug-n-plus-one-single | bug-fix | 100% | hit | 100% | 0% | MISS | 100% | 2220 |
| sum-bug-n-plus-one-single | bug-fix | 100% | hit | 100% | 0% | MISS | 100% | 2150 |
| sum-bug-n-plus-one-single | bug-fix | 50% | hit | 100% | 67% | MISS | 100% | 2249 |
| sum-bug-n-plus-one-chain | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 1914 |
| sum-refactor-invoice-single | refactor | 100% | hit | 100% | 100% | hit | 100% | 1696 |
| sum-refactor-invoice-chain | refactor | 100% | hit | 100% | 100% | hit | 100% | 1832 |
| sum-refactor-invoice-chain | refactor | 100% | hit | 100% | 100% | hit | 100% | 1621 |
| sum-refactor-invoice-chain | refactor | 100% | hit | 100% | 100% | hit | 100% | 2247 |
| sum-refactor-config-loader-single | refactor | 100% | hit | 100% | 100% | hit | 100% | 2395 |
| sum-refactor-config-loader-chain | refactor | 100% | hit | 100% | 67% | hit | 100% | 1601 |
| sum-refactor-sql-repository-single | refactor | 100% | hit | 100% | 100% | hit | 100% | 1301 |
| sum-refactor-sql-repository-chain | refactor | 100% | hit | 100% | 67% | hit | 100% | 2691 |
| sum-test-repro-timezone-single | test-add | 100% | MISS | 100% | 100% | hit | 100% | 8210 |
| sum-test-repro-timezone-chain | test-add | 100% | MISS | 100% | 100% | hit | 100% | 2000 |
| sum-test-repro-login-single | test-add | 100% | hit | 100% | 100% | hit | 0% | 6199 |
| sum-test-repro-login-chain | test-add | 100% | MISS | 0% | 100% | hit | 0% | 2048 |
| sum-test-integration-order-single | test-add | 100% | hit | 0% | 100% | hit | 50% | 1741 |
| sum-test-integration-order-single | test-add | 100% | hit | 100% | 100% | hit | 100% | 2055 |
| sum-test-integration-order-single | test-add | 100% | hit | 0% | 100% | hit | 50% | 2013 |
| sum-test-integration-order-chain | test-add | 100% | hit | 100% | 100% | hit | 100% | 1976 |
| sum-security-redact-log-single | security-fix | 100% | hit | 100% | 100% | hit | 100% | 2183 |
| sum-security-redact-log-chain | security-fix | 100% | hit | 100% | 100% | hit | 100% | 1711 |
| sum-security-redact-log-chain | security-fix | 100% | hit | 100% | 100% | MISS | 100% | 2215 |
| sum-security-redact-log-chain | security-fix | 100% | hit | 100% | 100% | hit | 100% | 1723 |
| sum-security-sql-injection-single | security-fix | 100% | hit | 0% | 100% | hit | 0% | 1791 |
| sum-security-sql-injection-chain | security-fix | 100% | hit | 0% | 67% | hit | 0% | 10776 |
| sum-security-path-traversal-single | security-fix | 100% | hit | 0% | 33% | hit | 50% | 2127 |
| sum-security-path-traversal-chain | security-fix | FAIL | MISS | - | - | MISS | - | 4322 |
| sum-security-secret-config-single | security-fix | 100% | hit | 100% | 100% | hit | 100% | 1637 |
| sum-security-secret-config-chain | security-fix | 100% | hit | 100% | 100% | hit | 100% | 1760 |
| sum-config-hikari-pool-single | config-change | 100% | MISS | 0% | 100% | hit | 100% | 2689 |
| sum-config-hikari-pool-chain | config-change | 67% | hit | 100% | 100% | hit | 100% | 1975 |
| sum-config-feature-flags-single | config-change | 100% | hit | 100% | 67% | hit | 100% | 1488 |
| sum-config-feature-flags-single | config-change | 100% | hit | 100% | 67% | hit | 100% | 1634 |
| sum-config-feature-flags-single | config-change | 100% | hit | 100% | 67% | hit | 100% | 1876 |
| sum-config-feature-flags-chain | config-change | 100% | hit | 100% | 100% | hit | 100% | 1181 |
| sum-deploy-k8s-probe-single | deploy-change | 100% | MISS | 100% | 100% | hit | 100% | 3799 |
| sum-deploy-k8s-probe-chain | deploy-change | FAIL | MISS | - | - | MISS | - | 7143 |
| sum-deploy-k8s-probe-chain | deploy-change | FAIL | MISS | - | - | MISS | - | 7796 |
| sum-deploy-k8s-probe-chain | deploy-change | FAIL | MISS | - | - | MISS | - | 4416 |
| sum-deploy-docker-multistage-single | deploy-change | 100% | hit | 100% | 67% | hit | 100% | 1417 |
| sum-deploy-docker-multistage-chain | deploy-change | 100% | hit | 100% | 67% | hit | 100% | 1930 |

## Problems

- sum-bug-timezone-report-single: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-bug-timezone-report-chain: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-bug-csv-crlf-single: request not fully trace-grounded 0%
- sum-bug-csv-crlf-single: learned not fully trace-grounded 50%
- sum-bug-csv-crlf-chain: request not fully trace-grounded 0%
- sum-bug-csv-crlf-chain: learned not fully trace-grounded 0%
- sum-bug-race-counter-single: request coverage 50% (expected [counter, AtomicLong])
- sum-bug-race-counter-chain: request coverage 50% (expected [counter, AtomicLong])
- sum-bug-n-plus-one-single: learned recall 0% (expected [EntityGraph, SQL, 1 + N])
- sum-bug-n-plus-one-single: learned lost identifiers [OrderRepository, EntityGraph]
- sum-bug-n-plus-one-single: learned recall 0% (expected [EntityGraph, SQL, 1 + N])
- sum-bug-n-plus-one-single: learned lost identifiers [OrderRepository, EntityGraph]
- sum-bug-n-plus-one-single: request coverage 50% (expected [query, items])
- sum-bug-n-plus-one-single: learned recall 67% (expected [EntityGraph, SQL, 1 + N])
- sum-bug-n-plus-one-single: learned lost identifiers [OrderRepository, EntityGraph]
- sum-refactor-config-loader-chain: learned recall 67% (expected [unmodifiable, map, typed accessors])
- sum-refactor-sql-repository-chain: learned recall 67% (expected [JPQL, @Query, tenant])
- sum-test-repro-timezone-single: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-test-repro-timezone-chain: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-test-repro-login-single: learned not fully trace-grounded 0%
- sum-test-repro-login-chain: request lost identifiers [LoginControllerTest]
- sum-test-repro-login-chain: request not fully trace-grounded 0%
- sum-test-repro-login-chain: learned not fully trace-grounded 0%
- sum-test-integration-order-single: request not fully trace-grounded 0%
- sum-test-integration-order-single: learned not fully trace-grounded 50%
- sum-test-integration-order-single: request not fully trace-grounded 0%
- sum-test-integration-order-single: learned not fully trace-grounded 50%
- sum-security-redact-log-chain: learned lost identifiers [LogSanitizer, redactsCredentials]
- sum-security-sql-injection-single: request not fully trace-grounded 0%
- sum-security-sql-injection-single: learned not fully trace-grounded 0%
- sum-security-sql-injection-chain: request not fully trace-grounded 0%
- sum-security-sql-injection-chain: learned recall 67% (expected [placeholder, parameter, UserDao])
- sum-security-sql-injection-chain: learned not fully trace-grounded 0%
- sum-security-path-traversal-single: request not fully trace-grounded 0%
- sum-security-path-traversal-single: learned recall 33% (expected [normalize, base directory, InvalidPathException])
- sum-security-path-traversal-single: learned not fully trace-grounded 50%
- sum-security-path-traversal-chain: format error: No <summary> block found in XML
- sum-config-hikari-pool-single: request lost identifiers [application.yml]
- sum-config-hikari-pool-single: request not fully trace-grounded 0%
- sum-config-hikari-pool-chain: request coverage 67% (expected [hikari, pool, size])
- sum-config-feature-flags-single: learned recall 67% (expected [checkout-v2, legacy-search, false])
- sum-config-feature-flags-single: learned recall 67% (expected [checkout-v2, legacy-search, false])
- sum-config-feature-flags-single: learned recall 67% (expected [checkout-v2, legacy-search, false])
- sum-deploy-k8s-probe-single: request lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: format error: No <summary> block found in XML
- sum-deploy-k8s-probe-chain: format error: No <summary> block found in XML
- sum-deploy-k8s-probe-chain: format error: No <summary> block found in XML
- sum-deploy-docker-multistage-single: learned recall 67% (expected [eclipse-temurin:21-jre, multi-stage, 780MB])
- sum-deploy-docker-multistage-chain: learned recall 67% (expected [eclipse-temurin:21-jre, multi-stage, 780MB])

## Method and limitations

- The system prompt is the production observation digest; the user prompt is the production `prompts/summary.txt` with `{{lastAssistantMessage}}` filled.
- `request` coverage and identifiers measure whether the summary names what the session was about; `learned` recall measures whether it kept the technical anchors.
- Trace support splits `request`/`learned` into clauses. A clause is grounded when every concrete anchor it names (CamelCase identifier, path/URL, number) occurs verbatim in the recorded tool trace; a clause that names no anchor must pass the facts-support rule (>= half of its significant tokens present). Paraphrasing a traced change is allowed; an invented object or number is not. `request` may additionally use the last assistant message as evidence.
- The golden set is hand-authored for this prompt, not a sample of production traffic.
