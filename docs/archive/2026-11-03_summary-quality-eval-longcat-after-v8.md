# Session-summary quality evaluation

- Provider: `openai`
- Model: `LongCat-2.5`
- Date: 2026-11-03
- Input: live provider calls
- Duration: 650348 ms

## Headline

| metric | value |
|---|---|
| samples | 58 |
| call failures | 0 |
| format errors | 0 |
| request keyword coverage | 90% |
| request identifier hit rate | 79% |
| request trace-support rate | 91% |
| learned recall | 91% |
| learned identifier hit rate | 91% |
| learned trace-support rate | 80% |
| p50 / p95 / max ms | 8157 / 22558 / 27462 |

## Gates

| A7 | request keyword coverage | >= 80% | 90% | PASS |
| A8 | request trace support | >= 75% | 91% | PASS |
| A9 | learned recall | >= 70% | 91% | PASS |
| A10a | learned trace support | >= 60% | 80% | PASS |
| A10b | learned identifier hit | >= 90% | 91% | PASS |

## Per sample

| id | category | req cov | req ids | req support | learned recall | learned ids | learned support | ms |
|---|---|---|---|---|---|---|---|---|
| sum-bug-offbyone-pagination-single | bug-fix | 100% | hit | 100% | 100% | hit | 50% | 9096 |
| sum-bug-offbyone-pagination-single | bug-fix | 50% | hit | 100% | 100% | hit | 67% | 7265 |
| sum-bug-offbyone-pagination-single | bug-fix | 50% | hit | 100% | 100% | hit | 0% | 21726 |
| sum-bug-offbyone-pagination-chain | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 5599 |
| sum-bug-null-cache-single | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 6761 |
| sum-bug-null-cache-chain | bug-fix | 50% | hit | 100% | 100% | hit | 100% | 7451 |
| sum-bug-null-cache-chain | bug-fix | 100% | hit | 100% | 67% | MISS | 100% | 8157 |
| sum-bug-null-cache-chain | bug-fix | 50% | hit | 100% | 100% | hit | 100% | 6676 |
| sum-bug-timezone-report-single | bug-fix | 100% | MISS | 100% | 100% | hit | 0% | 16032 |
| sum-bug-timezone-report-chain | bug-fix | 100% | MISS | 100% | 100% | hit | 0% | 7254 |
| sum-bug-payment-missing-order-single | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 17538 |
| sum-bug-payment-missing-order-chain | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 12211 |
| sum-bug-csv-crlf-single | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 17267 |
| sum-bug-csv-crlf-chain | bug-fix | 100% | hit | 100% | 100% | hit | 50% | 7407 |
| sum-bug-race-counter-single | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 15524 |
| sum-bug-race-counter-chain | bug-fix | 50% | hit | 100% | 0% | MISS | 100% | 4850 |
| sum-bug-n-plus-one-single | bug-fix | 100% | hit | 100% | 67% | MISS | 100% | 5895 |
| sum-bug-n-plus-one-single | bug-fix | 50% | hit | 100% | 67% | MISS | 100% | 7918 |
| sum-bug-n-plus-one-single | bug-fix | 100% | hit | 100% | 67% | MISS | 100% | 20831 |
| sum-bug-n-plus-one-chain | bug-fix | 100% | hit | 100% | 100% | hit | 100% | 15864 |
| sum-refactor-invoice-single | refactor | 100% | hit | 100% | 100% | hit | 100% | 7740 |
| sum-refactor-invoice-chain | refactor | 33% | hit | 100% | 100% | hit | 100% | 5001 |
| sum-refactor-invoice-chain | refactor | 33% | hit | 100% | 100% | hit | 100% | 5268 |
| sum-refactor-invoice-chain | refactor | 33% | hit | 100% | 100% | hit | 100% | 4444 |
| sum-refactor-config-loader-single | refactor | 100% | hit | 100% | 100% | hit | 100% | 9294 |
| sum-refactor-config-loader-chain | refactor | 100% | hit | 100% | 100% | hit | 100% | 16176 |
| sum-refactor-sql-repository-single | refactor | 100% | hit | 100% | 100% | hit | 100% | 6252 |
| sum-refactor-sql-repository-chain | refactor | 100% | hit | 100% | 100% | hit | 100% | 15154 |
| sum-test-repro-timezone-single | test-add | 100% | MISS | 100% | 100% | hit | 100% | 7708 |
| sum-test-repro-timezone-chain | test-add | 100% | MISS | 100% | 100% | hit | 100% | 5800 |
| sum-test-repro-login-single | test-add | 100% | hit | 100% | 100% | hit | 0% | 27462 |
| sum-test-repro-login-chain | test-add | 100% | hit | 0% | 100% | hit | 0% | 8391 |
| sum-test-integration-order-single | test-add | 100% | hit | 0% | 100% | hit | 50% | 4853 |
| sum-test-integration-order-single | test-add | 100% | hit | 0% | 100% | hit | 50% | 4264 |
| sum-test-integration-order-single | test-add | 100% | hit | 100% | 100% | hit | 50% | 4126 |
| sum-test-integration-order-chain | test-add | 100% | hit | 100% | 100% | hit | 67% | 16539 |
| sum-security-redact-log-single | security-fix | 100% | hit | 100% | 100% | hit | 100% | 7157 |
| sum-security-redact-log-chain | security-fix | 100% | hit | 100% | 100% | hit | 100% | 18724 |
| sum-security-redact-log-chain | security-fix | 100% | hit | 100% | 100% | hit | 100% | 16477 |
| sum-security-redact-log-chain | security-fix | 100% | hit | 100% | 100% | hit | 100% | 8103 |
| sum-security-sql-injection-single | security-fix | 100% | hit | 0% | 33% | hit | 50% | 4981 |
| sum-security-sql-injection-chain | security-fix | 100% | hit | 0% | 100% | hit | 0% | 4839 |
| sum-security-path-traversal-single | security-fix | 100% | hit | 100% | 33% | hit | 100% | 22631 |
| sum-security-path-traversal-chain | security-fix | 100% | hit | 100% | 67% | hit | 0% | 6868 |
| sum-security-secret-config-single | security-fix | 100% | hit | 100% | 100% | hit | 100% | 21194 |
| sum-security-secret-config-chain | security-fix | 100% | hit | 100% | 100% | hit | 100% | 20470 |
| sum-config-hikari-pool-single | config-change | 100% | hit | 100% | 100% | hit | 100% | 8917 |
| sum-config-hikari-pool-chain | config-change | 100% | MISS | 100% | 100% | hit | 100% | 8522 |
| sum-config-feature-flags-single | config-change | 100% | MISS | 100% | 67% | hit | 100% | 5404 |
| sum-config-feature-flags-single | config-change | 100% | MISS | 100% | 67% | hit | 100% | 6421 |
| sum-config-feature-flags-single | config-change | 100% | hit | 100% | 67% | hit | 100% | 20528 |
| sum-config-feature-flags-chain | config-change | 100% | MISS | 100% | 100% | hit | 100% | 15473 |
| sum-deploy-k8s-probe-single | deploy-change | 100% | MISS | 100% | 100% | hit | 50% | 9286 |
| sum-deploy-k8s-probe-chain | deploy-change | 67% | MISS | 100% | 100% | hit | 100% | 4380 |
| sum-deploy-k8s-probe-chain | deploy-change | 100% | MISS | 100% | 100% | hit | 100% | 22558 |
| sum-deploy-k8s-probe-chain | deploy-change | 67% | MISS | 100% | 100% | hit | 100% | 22403 |
| sum-deploy-docker-multistage-single | deploy-change | 100% | hit | 100% | 100% | hit | 50% | 8363 |
| sum-deploy-docker-multistage-chain | deploy-change | 100% | hit | 100% | 100% | hit | 100% | 16436 |

## Problems

- sum-bug-offbyone-pagination-single: learned not fully trace-grounded 50%
- sum-bug-offbyone-pagination-single: request coverage 50% (expected [offset, page])
- sum-bug-offbyone-pagination-single: learned not fully trace-grounded 67%
- sum-bug-offbyone-pagination-single: request coverage 50% (expected [offset, page])
- sum-bug-offbyone-pagination-single: learned not fully trace-grounded 0%
- sum-bug-null-cache-chain: request coverage 50% (expected [cache, concurrent])
- sum-bug-null-cache-chain: learned recall 67% (expected [computeIfAbsent, concurrent, null])
- sum-bug-null-cache-chain: learned lost identifiers [UserCache, computeIfAbsent]
- sum-bug-null-cache-chain: request coverage 50% (expected [cache, concurrent])
- sum-bug-timezone-report-single: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-bug-timezone-report-single: learned not fully trace-grounded 0%
- sum-bug-timezone-report-chain: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-bug-timezone-report-chain: learned not fully trace-grounded 0%
- sum-bug-csv-crlf-chain: learned not fully trace-grounded 50%
- sum-bug-race-counter-chain: request coverage 50% (expected [counter, AtomicLong])
- sum-bug-race-counter-chain: learned recall 0% (expected [AtomicLong, incrementAndGet])
- sum-bug-race-counter-chain: learned lost identifiers [CounterService, AtomicLong]
- sum-bug-n-plus-one-single: learned recall 67% (expected [EntityGraph, SQL, 1 + N])
- sum-bug-n-plus-one-single: learned lost identifiers [OrderRepository, EntityGraph]
- sum-bug-n-plus-one-single: request coverage 50% (expected [query, items])
- sum-bug-n-plus-one-single: learned recall 67% (expected [EntityGraph, SQL, 1 + N])
- sum-bug-n-plus-one-single: learned lost identifiers [OrderRepository, EntityGraph]
- sum-bug-n-plus-one-single: learned recall 67% (expected [EntityGraph, SQL, 1 + N])
- sum-bug-n-plus-one-single: learned lost identifiers [OrderRepository, EntityGraph]
- sum-refactor-invoice-chain: request coverage 33% (expected [invoice, tax, rendering])
- sum-refactor-invoice-chain: request coverage 33% (expected [invoice, tax, rendering])
- sum-refactor-invoice-chain: request coverage 33% (expected [invoice, tax, rendering])
- sum-test-repro-timezone-single: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-test-repro-timezone-chain: request lost identifiers [DailyReportTest, reportUsesBusinessTimezone]
- sum-test-repro-login-single: learned not fully trace-grounded 0%
- sum-test-repro-login-chain: request not fully trace-grounded 0%
- sum-test-repro-login-chain: learned not fully trace-grounded 0%
- sum-test-integration-order-single: request not fully trace-grounded 0%
- sum-test-integration-order-single: learned not fully trace-grounded 50%
- sum-test-integration-order-single: request not fully trace-grounded 0%
- sum-test-integration-order-single: learned not fully trace-grounded 50%
- sum-test-integration-order-single: learned not fully trace-grounded 50%
- sum-test-integration-order-chain: learned not fully trace-grounded 67%
- sum-security-sql-injection-single: request not fully trace-grounded 0%
- sum-security-sql-injection-single: learned recall 33% (expected [placeholder, parameter, UserDao])
- sum-security-sql-injection-single: learned not fully trace-grounded 50%
- sum-security-sql-injection-chain: request not fully trace-grounded 0%
- sum-security-sql-injection-chain: learned not fully trace-grounded 0%
- sum-security-path-traversal-single: learned recall 33% (expected [normalize, base directory, InvalidPathException])
- sum-security-path-traversal-chain: learned recall 67% (expected [normalize, base directory, InvalidPathException])
- sum-security-path-traversal-chain: learned not fully trace-grounded 0%
- sum-config-hikari-pool-chain: request lost identifiers [application.yml]
- sum-config-feature-flags-single: request lost identifiers [checkout-v2, legacy-search]
- sum-config-feature-flags-single: learned recall 67% (expected [checkout-v2, legacy-search, false])
- sum-config-feature-flags-single: request lost identifiers [checkout-v2, legacy-search]
- sum-config-feature-flags-single: learned recall 67% (expected [checkout-v2, legacy-search, false])
- sum-config-feature-flags-single: learned recall 67% (expected [checkout-v2, legacy-search, false])
- sum-config-feature-flags-chain: request lost identifiers [checkout-v2, legacy-search]
- sum-deploy-k8s-probe-single: request lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-single: learned not fully trace-grounded 50%
- sum-deploy-k8s-probe-chain: request coverage 67% (expected [readiness, probe, health])
- sum-deploy-k8s-probe-chain: request lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: request lost identifiers [deployment.yaml, /api/health]
- sum-deploy-k8s-probe-chain: request coverage 67% (expected [readiness, probe, health])
- sum-deploy-k8s-probe-chain: request lost identifiers [deployment.yaml, /api/health]
- sum-deploy-docker-multistage-single: learned not fully trace-grounded 50%

## Method and limitations

- The system prompt is the production observation digest; the user prompt is the production `prompts/summary.txt` with `{{lastAssistantMessage}}` filled.
- `request` coverage and identifiers measure whether the summary names what the session was about; `learned` recall measures whether it kept the technical anchors.
- Trace support splits `request`/`learned` into clauses. A clause is grounded when every concrete anchor it names (CamelCase identifier, path/URL, number) occurs verbatim in the recorded tool trace; a clause that names no anchor must pass the facts-support rule (>= half of its significant tokens present). Paraphrasing a traced change is allowed; an invented object or number is not. `request` may additionally use the last assistant message as evidence.
- The golden set is hand-authored for this prompt, not a sample of production traffic.
