golden set: main (87 samples)

# Model output quality evaluation

- Date: 2026-11-04
- Phase: prompt-slim-v2
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 87 unique; runs scored: 3; key repeat: 3; real calls: 113 (cap none); duration: 1829353 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 100% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 20/0/0/67 |
| format errors | 0 |
| validation rejections | 0 |
| call failures | 0 |
| call success rate | 100% (0/113 failed) |
| title exact-hit rate (legacy) | 55% |
| title normalized hit rate (legacy) | 60% |
| title action-class hit rate (A6) | 87% (67 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 73% |
| title subject hit rate (legacy, diagnostic) | 82% |
| title trace-visible subject hit rate (A6b) | 89% (57 scored, 10 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 67% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 76% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 57% |
| title identifier hit rate | 97% |
| type accuracy (raw) | 91% |
| type accuracy (auditable labels only) | 89% (11 unverifiable label(s) excluded) |
| facts keyword precision (legacy) / recall | 76% / 90% |
| facts informative precision | 84% |
| facts trace-support rate | 100% |
| concepts precision / recall | 83% / 79% |
| skip consistency across repeats | 100% (13 repeated samples) |
| latency p50 / p95 / max (ms) | 12412 / 34666 / 95051 |
| tokens p50 / p95 / max / mean per call | 4055 / 5077 / 7957 / 4132 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 84% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 87% | PASS |
| A6b | title trace-visible subject | >= 90% | 89% | **FAIL** |
| A6c | title identifiers | = 100% | 97% | **FAIL** |
| A6 | all three title dimensions together | all pass | 76% | **FAIL** |
| A11 | p95 latency vs baseline | <= 1.5x baseline | 34666 ms (baseline 33716 ms) | PASS |
| A12 | p95 tokens vs baseline | <= 1.5x baseline | 5077 tokens (baseline 5763) | PASS |
| A13 | provider call success rate | >= 99% (at most 1 failed call(s) per run) | 100% (0/113 failed) | PASS |

## Status distribution

| status | calls |
|---|---|
| 200 | 87 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 87 | 100% | 60% | 67% | 76% | 84% | 90% | 0 |
| 1 | 13 | 100% | 50% | 60% | 75% | 82% | 100% | 0 |
| 2 | 13 | 100% | 40% | 40% | 83% | 83% | 100% | 0 |

- average: skip 100%, title 50%, title contract 56%, facts P/R 78%/97%, facts actionable 83%
- worst run: skip 100%, title 40%, title contract 40%, facts P/R 75%/90%, facts actionable 82%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 12/12 | 8/12 |
| refactor | 10 | 10/10 | 8/10 | 7/10 |
| test-add | 9 | 9/9 | 9/9 | 9/9 |
| security-fix | 9 | 9/9 | 8/9 | 3/9 |
| config-change | 12 | 12/12 | 11/12 | 9/12 |
| docs-update | 8 | 8/8 | 8/8 | 3/8 |
| deploy-change | 7 | 7/7 | 5/7 | 1/7 |
| skip-low-value | 20 | 20/20 | 0/20 | 0/20 |

## Run-to-run variance (repeated samples)

| metric | min | max | mean | std dev |
|---|---|---|---|---|
| type accuracy | 91% | 100% | 97% | 4% |
| title action class | 87% | 90% | 89% | 2% |
| title trace-visible subject | 71% | 89% | 77% | 9% |
| title identifiers | 90% | 97% | 92% | 3% |
| title trace contract | 60% | 76% | 69% | 7% |
| facts informative | 82% | 84% | 83% | 1% |
| skip accuracy | 100% | 100% | 100% | 0% |

## Inconsistent samples (type or action changed between runs)

2 of 13 repeated samples disagreed:

- bug-offbyone-pagination: action {null, modify}
- deploy-k8s-probe: action {create, modify}

## Unreachable subject labels (excluded from A6b)

10 sample(s) declare subject tokens the recorded trace never contains:

- bug-offbyone-pagination: [pagination]
- bug-null-cache: [race]
- bug-race-counter: [race]
- bug-n-plus-one: [n+1]
- bug-regex-redos: [redos]
- bug-file-encoding: [encoding]
- refactor-auth-controller: [delegation]
- remove-dead-code: [dead, code]
- remove-stale-config: [stale, setting]
- remove-deprecated-endpoint: [deprecated, endpoint]

## Unverifiable type labels (excluded from the auditable type accuracy)

11 sample(s) expect a type the recorded trace cannot evidence:

- bug-offbyone-pagination: expected bugfix but the trace shows no defect or security evidence
- bug-null-cache: expected bugfix but the trace shows no defect or security evidence
- bug-payment-missing-order: expected bugfix but the trace shows no defect or security evidence
- bug-connection-leak: expected bugfix but the trace shows no defect or security evidence
- bug-csv-crlf: expected bugfix but the trace shows no defect or security evidence
- bug-race-counter: expected bugfix but the trace shows no defect or security evidence
- bug-n-plus-one: expected bugfix but the trace shows no defect or security evidence
- bug-dateformat-locale: expected bugfix but the trace shows no defect or security evidence
- bug-regex-redos: expected bugfix but the trace shows no defect or security evidence
- bug-file-encoding: expected bugfix but the trace shows no defect or security evidence
- security-xxe: expected bugfix but the trace shows no defect or security evidence

## Failure clusters (first run)

| cluster | misses | share of scored (67) |
|---|---|---|
| identifier missing | 2 | 3% |
| action class wrong | 9 | 13% |
| subject wrong | 6 | 9% |
| type wrong | 6 | 9% |

### identifier missing (2)

- bug-null-cache — expected identifiers [UserCache, computeIfAbsent], title 'Fixed concurrent null placeholder race in UserCache'
- deploy-docker-multistage — expected identifiers [Dockerfile, eclipse-temurin:21-jre], title 'Converted build image to multi-stage runtime in Dockerfile'

### action class wrong (9)

- bug-offbyone-pagination — expected [modify], got 'null' from title '**title**: Fixed pageOffset off-by-one in pagination in OrderQuery'
- refactor-logging-filter — expected [modify], got 'null' from title '**title**: Refactored LoggingFilter logExchange to own timing and header capture in LoggingFilter'
- refactor-sql-repository — expected [modify], got 'secure' from title 'Hardened UserRepository tenant lookup against SQL injection in UserRepository'
- security-sql-injection — expected [secure], got 'modify' from title 'Fixed SQL injection in findByEmail with parameter placeholder in UserDao'
- security-rate-limit — expected [secure], got 'create' from title 'Added token bucket rate limiting returning 429 in RateLimiter'
- config-docker-healthcheck — expected [modify], got 'create' from title 'Added HEALTHCHECK probing /actuator/health in Dockerfile'
- docs-runbook — expected [document], got 'null' from title '**title**: Documented queue recovery steps in docs/runbook.md'
- docs-architecture-adr — expected [document], got 'null' from title '**title**: Recorded ADR extraction validation decision in 0007-extraction-validation.md'
- deploy-k8s-probe — expected [modify], got 'create' from title 'Added readinessProbe on /api/health:37777 to deployment.yaml'

### subject wrong (6)

- refactor-sql-repository — expected subject tokens [jpql], title 'Hardened UserRepository tenant lookup against SQL injection in UserRepository'
- security-redact-log — expected subject tokens [credentials], title 'Redacted password token secret values in LogSanitizer'
- security-authz-admin — expected subject tokens [authorization], title 'Restricted AdminController endpoints to ROLE_ADMIN in AdminController'
- security-cookie-httponly — expected subject tokens [httponly, cookie], title 'Hardened session cookie flags in SessionCookieConfig'
- config-log-level — expected subject tokens [level], title 'Changed root logger to INFO and JSON appender in logback-spring.xml'
- deploy-helm-values — expected subject tokens [helm, values], title 'Changed replicaCount to 3 and memory limit to 2Gi in values.yaml'

### type wrong (6)

- refactor-config-loader — expected 'refactor', got 'feature' (title 'Extended typed accessors and unmodifiable map return in ConfigLoader')
- refactor-sql-repository — expected 'refactor', got 'bugfix' (title 'Hardened UserRepository tenant lookup against SQL injection in UserRepository')
- security-rate-limit — expected 'bugfix', got 'feature' (title 'Added token bucket rate limiting returning 429 in RateLimiter')
- config-cors — expected 'change', got 'bugfix' (title 'Restricted CORS origins for /api/** to https://app.acme.com in CorsConfig')
- deploy-terraform-iam — expected 'change', got 'bugfix' (title 'Restricted deploy role IAM policy to AmazonS3ReadOnlyAccess in main.tf')
- deploy-rollback-script — expected 'change', got 'feature' (title 'Added claude-mem rollback script in rollback.sh')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | MISS (subject unreachable, id hit, action MISS) | hit | match | 100%/100% | 100% | 50%/50% | 15190 |
| bug-null-cache | bug-fix | observation | observation | pass | 75% | MISS (subject unreachable, id MISS, action hit) | miss | match | 100%/100% | 100% | 100%/100% | 10852 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 50%/50% | 11780 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 50%/50% | 9359 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 22336 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 19694 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 16254 |
| bug-race-counter | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 17701 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 50% | 50%/50% | 18713 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 18436 |
| bug-regex-redos | bug-fix | observation | observation | pass | 100% | hit | miss | match | 100%/100% | 100% | 100%/100% | 12773 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 10365 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 50%/100% | 50% | 100%/100% | 30303 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 50% | 100%/100% | 12335 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | hit | miss | MISS | 67%/100% | 67% | 100%/100% | 95051 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 11851 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 22790 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 67%/100% | 33% | 100%/100% | 10813 |
| refactor-sql-repository | refactor | observation | observation | pass | 33% | MISS (subject MISS, id hit, action MISS) | miss | MISS | 50%/100% | 100% | 50%/50% | 27963 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 0%/0% | 50% | 100%/100% | 10251 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 50%/50% | 50% | 100%/100% | 10613 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 12427 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 50%/50% | 16333 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 67% | 100%/100% | 32717 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 16750 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 15990 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 31585 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 13479 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 17778 |
| test-flaky-clock | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 24769 |
| security-redact-log | security-fix | observation | observation | pass | 67% | MISS (subject MISS, id hit, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 36668 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 100%/100% | 100% | 100%/100% | 23657 |
| security-authz-admin | security-fix | observation | observation | pass | 33% | MISS (subject MISS, id hit, action hit) | miss | match | 100%/100% | 100% | 0%/0% | 15604 |
| security-csrf | security-fix | observation | observation | pass | 67% | hit | miss | match | 50%/50% | 100% | 50%/50% | 34908 |
| security-secret-config | security-fix | observation | observation | pass | 50% | hit | miss | match | 50%/50% | 100% | 100%/100% | 20417 |
| security-xxe | security-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 50806 |
| security-path-traversal | security-fix | observation | observation | pass | 75% | hit | miss | match | 50%/100% | 100% | 100%/100% | 22673 |
| security-rate-limit | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | MISS | 100%/50% | 100% | 50%/50% | 8678 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 50%/100% | 100% | 50%/50% | 19292 |
| config-hikari-pool | config-change | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 67% | 100%/100% | 10655 |
| config-flyway-baseline | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 50% | 100%/100% | 11022 |
| config-log-level | config-change | observation | observation | pass | 100% | MISS (subject MISS, id hit, action hit) | hit | match | 50%/50% | 50% | 100%/50% | 16050 |
| config-cors | config-change | observation | observation | pass | 100% | hit | hit | MISS | 67%/100% | 67% | 100%/100% | 34666 |
| config-timeout | config-change | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 9423 |
| config-feature-flags | config-change | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 9946 |
| config-docker-healthcheck | config-change | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 100%/100% | 100% | 50%/50% | 20745 |
| config-maven-plugin | config-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 10253 |
| docs-api-observations | docs-update | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 8408 |
| docs-readme-setup | docs-update | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 100%/100% | 9329 |
| docs-runbook | docs-update | observation | observation | pass | 75% | MISS (subject hit, id hit, action MISS) | miss | match | 100%/100% | 50% | 100%/100% | 12412 |
| docs-changelog | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 11502 |
| docs-architecture-adr | docs-update | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 50%/100% | 100% | 100%/100% | 24502 |
| docs-migration-guide | docs-update | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 29726 |
| docs-security-policy | docs-update | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 50% | 50%/50% | 10300 |
| docs-contributing | docs-update | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 10148 |
| deploy-k8s-probe | deploy-change | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 67%/100% | 67% | 100%/100% | 12536 |
| deploy-github-actions | deploy-change | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 18506 |
| deploy-docker-multistage | deploy-change | observation | observation | pass | 75% | MISS (subject hit, id MISS, action hit) | miss | match | 33%/100% | 67% | 100%/100% | 10566 |
| deploy-terraform-iam | deploy-change | observation | observation | pass | 75% | hit | miss | MISS | 100%/100% | 50% | 100%/100% | 17132 |
| deploy-helm-values | deploy-change | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 67%/100% | 100% | 100%/100% | 8832 |
| deploy-rollback-script | deploy-change | observation | observation | pass | 75% | hit | miss | MISS | 67%/100% | 33% | 100%/100% | 13929 |
| deploy-canary | deploy-change | observation | observation | pass | 50% | hit | miss | match | 0%/0% | 100% | 100%/100% | 13332 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4099 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4435 |
| skip-git-status | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4024 |
| skip-view-logs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 9441 |
| skip-multi-step-explore | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 8276 |
| skip-repeated-confirmation | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2652 |
| skip-package-install | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4422 |
| skip-format-only | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3992 |
| skip-abandoned-exploration | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2309 |
| skip-read-docs | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2962 |
| skip-grep-search | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3201 |
| skip-open-file-to-explain | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2723 |
| skip-test-output-view | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 5012 |
| skip-docker-ps | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2578 |
| skip-empty-diff | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 6343 |
| skip-mvn-version | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2248 |
| skip-cat-file | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 2490 |
| skip-ls-tree | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 6035 |
| skip-read-config-to-answer | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 3606 |
| skip-no-conclusion | skip-low-value | skip | skip | pass | - | - | - | - | - | - | - | 4703 |
| remove-dependency | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/50% | 22438 |
| remove-dead-code | refactor | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 100%/50% | 24479 |
| remove-stale-config | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 10715 |
| remove-deprecated-endpoint | config-change | observation | observation | pass | 50% | hit | miss | match | 100%/100% | 100% | 100%/50% | 14659 |
| remove-feature-flag | config-change | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/50% | 28535 |

## Problems

- bug-offbyone-pagination: title action [modify] missing: title leads 'null' (got '**title**: Fixed pageOffset off-by-one in pagination in OrderQuery')
- bug-offbyone-pagination: title verb class 'fix' missing (got '**title**: Fixed pageOffset off-by-one in pagination in OrderQuery')
- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed concurrent null placeholder race in UserCache')
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-n-plus-one: title coverage 75% (expected keywords [fix, n+1, orderrepository, entitygraph])
- bug-n-plus-one: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, what-changed])
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed UTF-8 decoding corruption from platform default charset in FileReaderFactory')
- refactor-auth-controller: title subject [delegation] missing (got 'Refactored AuthController JWT issuance to TokenService.issue')
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [AuthController now calls `tokenService.issue(authentication)` instead of building JWTs inline., AuthControllerTest was left unchanged and remains green after the delegation edit.])
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title verb class 'refactor' missing (got 'Extended typed accessors and unmodifiable map return in ConfigLoader')
- refactor-config-loader: type 'feature' != expected 'refactor'
- refactor-logging-filter: title action [modify] missing: title leads 'null' (got '**title**: Refactored LoggingFilter logExchange to own timing and header capture in LoggingFilter')
- refactor-logging-filter: title verb class 'refactor' missing (got '**title**: Refactored LoggingFilter logExchange to own timing and header capture in LoggingFilter')
- refactor-sql-repository: title coverage 33% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title action [modify] missing: title leads 'secure' (got 'Hardened UserRepository tenant lookup against SQL injection in UserRepository')
- refactor-sql-repository: title verb class 'refactor' missing (got 'Hardened UserRepository tenant lookup against SQL injection in UserRepository')
- refactor-sql-repository: title subject [jpql] missing (got 'Hardened UserRepository tenant lookup against SQL injection in UserRepository')
- refactor-sql-repository: title trace-visible subject [jpql] missing (got 'Hardened UserRepository tenant lookup against SQL injection in UserRepository')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: concepts recall 50% (expected [what-changed, pattern], got [what-changed, gotcha])
- refactor-event-bus: facts recall 0% (expected [listener map, event type], got [EventBus replaces the if/else dispatch chain with `listeners.getOrDefault(type, List.of()).forEach(listener -> listener.on(event))`., EventBusTest dispatches the same three events, confirming the refactored dispatch preserves behavior.])
- refactor-feature-flag: facts recall 50% (expected [Flags.isEnabled, on/off matrix], got [FeatureFlagService.isEnabled now returns flags.isEnabled(flag, context) instead of performing its own evaluation., FeatureFlagServiceTest retains the same on/off flag matrix, confirming behavior is unchanged.])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-repro-login: facts recall 50% (expected [lockedAccountReturns423, 401], got [LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true and performs POST /login expecting status 423 Locked., The test file src/test/java/com/acme/auth/LoginControllerTest.java was added without any change to production source.])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles uses @CsvSource({"1,100","2,200","3,400"}) and asserts policy.delay(attempt) equals expected., The new file src/test/java/com/acme/resilience/RetryPolicyTest.java contains 3 parameterized cases and all 3 tests pass., The test targets RetryPolicy.delay(int) exponential backoff timing.])
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable uses @ParameterizedTest with @ValueSource strings "a,b", "a\"b" and "a\nb"., All 3 parameterized cases pass, covering comma, quote and newline quoting stability.])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- test-flaky-clock: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: title subject [credentials] missing (got 'Redacted password token secret values in LogSanitizer')
- security-redact-log: title trace-visible subject [credentials] missing (got 'Redacted password token secret values in LogSanitizer')
- security-redact-log: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, pattern])
- security-sql-injection: title action [secure] missing: title leads 'modify' (got 'Fixed SQL injection in findByEmail with parameter placeholder in UserDao')
- security-authz-admin: title coverage 33% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Restricted AdminController endpoints to ROLE_ADMIN in AdminController')
- security-authz-admin: title subject [authorization] missing (got 'Restricted AdminController endpoints to ROLE_ADMIN in AdminController')
- security-authz-admin: title trace-visible subject [authorization] missing (got 'Restricted AdminController endpoints to ROLE_ADMIN in AdminController')
- security-authz-admin: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, what-changed])
- security-csrf: title coverage 67% (expected keywords [fix, csrf, csrffilter])
- security-csrf: title verb class 'fix' missing (got 'Hardened CsrfFilter state-changing request CSRF check with 403 in CsrfFilter')
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter.java calls response.sendError(403) and returns when csrfToken.matches(request) is false., CsrfFilterTest#postWithoutTokenIs403 passes after the token check was added.])
- security-csrf: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- security-secret-config: title coverage 50% (expected keywords [remove, hardcoded, api, key])
- security-secret-config: title verb class 'remove' missing (got 'Redacted API key as ACME_API_KEY in application.yml')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Redacted API key as ACME_API_KEY in application.yml')
- security-secret-config: facts recall 50% (expected [ACME_API_KEY, SecretScanTest], got [src/main/resources/application.yml previously contained the literal value sk-live-123 for the api-key setting., The api-key setting now resolves to ${ACME_API_KEY} from the environment instead of a hardcoded credential.])
- security-xxe: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, gotcha])
- security-path-traversal: title coverage 75% (expected keywords [fix, path, traversal, filedownloadcontroller])
- security-path-traversal: title verb class 'fix' missing (got 'Hardened FileDownloadController path traversal validation')
- security-rate-limit: title action [secure] missing: title leads 'create' (got 'Added token bucket rate limiting returning 429 in RateLimiter')
- security-rate-limit: type 'feature' != expected 'bugfix'
- security-rate-limit: facts recall 50% (expected [429, token bucket], got [RateLimiter throws TooManyRequestsException when bucket.tryConsume(1) returns false, producing HTTP 429., RateLimiterTest#eleventhRequestIs429 passes, pinning the 429 response on the eleventh request.])
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [pattern, what-changed])
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- security-cookie-httponly: title trace-visible subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- security-cookie-httponly: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- config-hikari-pool: title coverage 75% (expected keywords [change, hikari, pool, size])
- config-hikari-pool: title verb class 'change' missing (got 'Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml')
- config-log-level: title subject [log, level] missing (got 'Changed root logger to INFO and JSON appender in logback-spring.xml')
- config-log-level: title trace-visible subject [level] missing (got 'Changed root logger to INFO and JSON appender in logback-spring.xml')
- config-log-level: facts recall 50% (expected [INFO, JSON appender], got [logback-spring.xml sets root level="INFO" with appender-ref ref="JSON"., LogbackConfigTest parses the logback-spring.xml configuration.])
- config-log-level: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])
- config-cors: type 'bugfix' != expected 'change'
- config-docker-healthcheck: title action [modify] missing: title leads 'create' (got 'Added HEALTHCHECK probing /actuator/health in Dockerfile')
- config-docker-healthcheck: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- config-maven-plugin: title coverage 75% (expected keywords [add, jacoco, plugin, pom])
- config-maven-plugin: title verb class 'add' missing (got 'Extended build coverage reporting with jacoco-maven-plugin 0.8.12 in pom.xml')
- config-maven-plugin: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- docs-readme-setup: title coverage 50% (expected keywords [update, readme, setup, docker])
- docs-readme-setup: title verb class 'change' missing (got 'Documented local setup steps JDK 21 postgres in README.md')
- docs-runbook: title coverage 75% (expected keywords [add, runbook, queue, recovery])
- docs-runbook: title action [document] missing: title leads 'null' (got '**title**: Documented queue recovery steps in docs/runbook.md')
- docs-runbook: title verb class 'add' missing (got '**title**: Documented queue recovery steps in docs/runbook.md')
- docs-changelog: title coverage 67% (expected keywords [update, changelog, 2.4.0])
- docs-changelog: title verb class 'change' missing (got 'Documented 2.4.0 release notes in CHANGELOG.md')
- docs-architecture-adr: title action [document] missing: title leads 'null' (got '**title**: Recorded ADR extraction validation decision in 0007-extraction-validation.md')
- docs-architecture-adr: title verb class 'record' missing (got '**title**: Recorded ADR extraction validation decision in 0007-extraction-validation.md')
- docs-migration-guide: concepts recall 50% (expected [what-changed, gotcha], got [what-changed, pattern])
- docs-security-policy: title coverage 75% (expected keywords [add, security, policy, disclosure])
- docs-security-policy: title verb class 'add' missing (got 'Documented vulnerability disclosure policy in SECURITY.md')
- docs-security-policy: concepts recall 50% (expected [why-it-exists, pattern], got [what-changed, pattern])
- docs-contributing: title coverage 67% (expected keywords [update, contributing, verify-local])
- docs-contributing: title verb class 'change' missing (got 'Documented verify-local.sh prerequisite and four test layers in CONTRIBUTING.md')
- deploy-k8s-probe: title action [modify] missing: title leads 'create' (got 'Added readinessProbe on /api/health:37777 to deployment.yaml')
- deploy-github-actions: title coverage 75% (expected keywords [add, ci, matrix, jdk])
- deploy-github-actions: title verb class 'add' missing (got 'Extended CI matrix with JDK 21 and 25 in ci.yml')
- deploy-docker-multistage: title lost identifiers [Dockerfile, eclipse-temurin:21-jre] (got 'Converted build image to multi-stage runtime in Dockerfile')
- deploy-terraform-iam: title coverage 75% (expected keywords [restrict, iam, terraform, s3])
- deploy-terraform-iam: type 'bugfix' != expected 'change'
- deploy-helm-values: title coverage 75% (expected keywords [change, helm, replicas, memory])
- deploy-helm-values: title subject [helm, values] missing (got 'Changed replicaCount to 3 and memory limit to 2Gi in values.yaml')
- deploy-helm-values: title trace-visible subject [helm, values] missing (got 'Changed replicaCount to 3 and memory limit to 2Gi in values.yaml')
- deploy-rollback-script: title coverage 75% (expected keywords [add, rollback, script, deployment])
- deploy-rollback-script: type 'feature' != expected 'change'
- deploy-canary: title coverage 50% (expected keywords [enable, canary, rollback, threshold])
- deploy-canary: title verb class 'enable' missing (got 'Set canary weight to 10 and errorThreshold to 0.02 in canary.yaml')
- deploy-canary: facts recall 0% (expected [10 percent, 2 percent], got [infra/canary.yaml sets canary.enabled to true, canary.weight to 10, and canary.errorThreshold to 0.02.])
- remove-dependency: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])
- remove-dead-code: title coverage 50% (expected keywords [remove, dead, code, orderservice])
- remove-dead-code: title subject [dead, code] missing (got 'Removed legacySync from OrderService')
- remove-dead-code: concepts recall 50% (expected [what-changed, pattern], got [what-changed, what-changed])
- remove-stale-config: title subject [stale, setting] missing (got 'Removed legacy-search.enabled flag from application.yml')
- remove-deprecated-endpoint: title coverage 50% (expected keywords [remove, deprecated, endpoint, usercontroller])
- remove-deprecated-endpoint: title subject [deprecated, endpoint] missing (got 'Removed listLegacy /v1/users mapping in UserController')
- remove-deprecated-endpoint: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])
- remove-feature-flag: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, what-changed])

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>**title**: Fixed pageOffset off-by-one in pagination in OrderQuery</title>   <subtitle>**subtitle**: Removed the extra -1 from the offset calculation so page zero starts at record zero.</subtitle>   <facts>     <fact>OrderQuery.pageOffset changed from `page * size - 1` to `page * size`, eliminating the off-by-one error.</fact>     <fact>OrderQueryTest#f... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed concurrent null placeholder race in UserCache</title>   <subtitle>UserCache.get now loads via computeIfAbsent so concurrent callers cannot both write a null placeholder; CacheConcurrencyTest passes.</subtitle>   <facts>     <fact>UserCache.get previously returned cache.get(id), which let two concurrent callers both write a null placeholder for the... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added reportUsesBusinessTimezone test pinning Asia/Shanghai business date in DailyReportTest</title>   <subtitle>New test reproduces the off-by-one-day reporting bug and now passes after the fix.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins input 2026-11-01T23:30Z and expects Asia/Shanghai business date 2026-11-02.</fa... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed PaymentService missing orderId null return in PaymentService</title>   <subtitle>PaymentService now throws PaymentNotFoundException for a missing orderId instead of returning null.</subtitle>   <facts>     <fact>PaymentService replaces null return with repository.findByOrderId(orderId).orElseThrow(() -> new PaymentNotFoundException(orderId)).</fac... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JWT expiration boundary accepting token expiring at current instant in JwtVerifier</title>   <subtitle>JwtVerifier previously accepted a token whose expiration equals the current instant, now rejects it as expired.</subtitle>   <facts>     <fact>JwtVerifier previously checked `claims.getExpiration().before(now)`, which accepted a token whose expir... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed multiline field line endings to CRLF in CsvExporter</title>   <subtitle>CsvExporter now normalises embedded line endings to CRLF before quoting so multiline fields keep the CSV row count.</subtitle>   <facts>     <fact>CsvExporter writes line.replaceAll("\\r?\\n", "\\r\\n") to normalise embedded line endings to CRLF before quoting.</fact>     <fac... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService increment race condition with AtomicLong.incrementAndGet in CounterService</title>   <subtitle>Replaced non-atomic get/set read-modify-write with atomic incrementAndGet, eliminating lost updates under contention.</subtitle>   <facts>     <fact>CounterService.increment previously executed `counter.set(counter.get() + 1); return count... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>bugfix</type>   <title>Fixed N+1 query loading order items in OrderRepository</title>   <subtitle>Added @EntityGraph to fetch items eagerly, reducing SQL statements from 1 + N to a single query.</subtitle>   <facts>     <fact>`OrderRepository.findAllByCustomerId(String customerId)` is annotated with `@EntityGraph(attributePaths = "items")` to fetch order items in the same que... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed UTF-8 decoding corruption from platform default charset in FileReaderFactory</title>   <subtitle>FileReaderFactory now always opens streams as UTF-8, so accented text survives on JVMs whose default charset is GBK.</subtitle>   <facts>     <fact>FileReaderFactory constructs InputStreamReader with StandardCharsets.UTF_8 instead of the platform defau... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored AuthController JWT issuance to TokenService.issue</title>   <subtitle>AuthController no longer builds JWTs inline and delegates token creation to TokenService.</subtitle>   <facts>     <fact>AuthController now calls `tokenService.issue(authentication)` instead of building JWTs inline.</fact>     <fact>AuthControllerTest was left unchanged a... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>feature</type>   <title>Extended typed accessors and unmodifiable map return in ConfigLoader</title>   <subtitle>ConfigLoader now returns an unmodifiable map and exposes typed accessors for configuration values.</subtitle>   <facts>     <fact>ConfigLoader now returns an unmodifiable map via `toUnmodifiableMap` instead of a mutable map.</fact>     <fact>ConfigLoader exposes ty... [truncated]
```

### refactor-logging-filter

```text
<observation>   <type>refactor</type>   <title>**title**: Refactored LoggingFilter logExchange to own timing and header capture in LoggingFilter</title>   <subtitle>**subtitle**: LoggingFilter now delegates timing and header capture to logExchange while keeping the access-log line unchanged.</subtitle>   <facts>     <fact>src/main/java/com/acme/web/LoggingFilter.java now calls logExchange(exchange... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Hardened UserRepository tenant lookup against SQL injection in UserRepository</title>   <subtitle>UserRepository now uses a parameterized JPQL @Query for tenant filtering instead of string-concatenated SQL.</subtitle>   <facts>     <fact>src/main/java/com/acme/users/UserRepository.java replaces string-concatenated SQL with the JPQL query `select u from ... [truncated]
```

### refactor-event-bus

```text
<observation>   <type>refactor</type>   <title>Refactored EventBus if/else dispatch into a listener map keyed by event type in EventBus</title>   <subtitle>Event dispatch now uses a per-type listener map instead of a branching if/else chain, preserving behavior.</subtitle>   <facts>     <fact>EventBus replaces the if/else dispatch chain with `listeners.getOrDefault(type, List.of()).forEach(listene... [truncated]
```

### refactor-feature-flag

```text
<observation>   <type>refactor</type>   <title>Refactored flag evaluation delegation in FeatureFlagService</title>   <subtitle>FeatureFlagService now delegates evaluation directly to Flags.isEnabled with unchanged on/off behavior.</subtitle>   <facts>     <fact>FeatureFlagService.isEnabled now returns flags.isEnabled(flag, context) instead of performing its own evaluation.</fact>     <fact>Feature... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added reportUsesBusinessTimezone reproduction test in DailyReportTest</title>   <subtitle>Test pins the UTC-vs-business-date defect for Asia/Shanghai and currently fails with the reported wrong date.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone parses 2026-11-01T23:30:00Z and asserts report.dateAt(at, ZoneId.of("Asia/Shangh... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added lockedAccountReturns423 reproducing locked account 401 in LoginControllerTest</title>   <subtitle>New test pins that a locked user must return HTTP 423, but LoginController currently returns 401.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true and performs POST /login expecting... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized exponential backoff cases in RetryPolicyTest</title>   <subtitle>Parameterized test pins retry delays of 100, 200, and 400 ms for attempts 1-3.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles uses @CsvSource({"1,100","2,200","3,400"}) and asserts policy.delay(attempt) equals expected.</fact> ... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added quotingIsStable parameterized cases for comma, quote and newline to CsvExporterTest</title>   <subtitle>Parameterized test pins CSV quoting stability for commas, embedded quotes and newlines; all three cases pass.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable uses @ParameterizedTest with @ValueSource strings "a,b", "a\"b" and "a\... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added concurrentReadersNeverSeeNull reproducing UserCache null in CacheConcurrencyTest</title>   <subtitle>Reproduction test pins the intermittent null UserCache returns under parallel reads.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull launches 1000 parallel readers via IntStream.range(0, 1000).parallel() and asser... [truncated]
```

### test-flaky-clock

```text
<observation>   <type>feature</type>   <title>Fixed flaky timing by replacing Thread.sleep with Clock.fixed in SchedulerTest</title>   <subtitle>SchedulerTest now uses an injected fixed Clock instead of Thread.sleep, eliminating timing flakiness.</subtitle>   <facts>     <fact>SchedulerTest sets scheduler.clock to Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC).</fact>     <fact... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Redacted password token secret values in LogSanitizer</title>   <subtitle>LogSanitizer now masks password, token and secret values in log output before emission.</subtitle>   <facts>     <fact>LogSanitizer applies the regex `(password|token|secret)=\\S+` and replaces matches with `$1=***` to redact credential values before logging.</fact>     <fact>LogS... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed SQL injection in findByEmail with parameter placeholder in UserDao</title>   <subtitle>UserDao.findByEmail no longer concatenates the email into the SQL string, closing an injection hole.</subtitle>   <facts>     <fact>UserDao.findByEmail now calls jdbcTemplate.query("select * from users where email = ?", mapper, email) with a parameter placeholde... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Restricted AdminController endpoints to ROLE_ADMIN in AdminController</title>   <subtitle>Class-level authorization now requires ADMIN role on every endpoint, closing an open access hole.</subtitle>   <facts>     <fact>`@PreAuthorize("hasRole('ADMIN')")` was added to `AdminController`, requiring ROLE_ADMIN for every endpoint.</fact>     <fact>`AdminAuth... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Hardened CsrfFilter state-changing request CSRF check with 403 in CsrfFilter</title>   <subtitle>CsrfFilter now rejects state-changing requests whose token does not match with HTTP 403.</subtitle>   <facts>     <fact>CsrfFilter.java calls response.sendError(403) and returns when csrfToken.matches(request) is false.</fact>     <fact>CsrfFilterTest#postWi... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Redacted API key as ACME_API_KEY in application.yml</title>   <subtitle>Removed hardcoded sk-live-123 API key from application.yml; configuration now reads ACME_API_KEY from the environment.</subtitle>   <facts>     <fact>src/main/resources/application.yml previously contained the literal value sk-live-123 for the api-key setting.</fact>     <fact>The a... [truncated]
```

### security-xxe

```text
<observation>   <type>bugfix</type>   <title>Hardened XML parser against XXE in XmlImportService</title>   <subtitle>XmlImportService now rejects DOCTYPE declarations to prevent XML external entity injection.</subtitle>   <facts>     <fact>XmlImportService calls factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) to block DOCTYPE declarations.</fact>     <fact>XxeTest#... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Hardened FileDownloadController path traversal validation</title>   <subtitle>FileDownloadController now rejects path names that escape the base directory by throwing InvalidPathException.</subtitle>   <facts>     <fact>FileDownloadController resolves user-supplied names against the base directory and throws InvalidPathException when the normalized path... [truncated]
```

### security-rate-limit

```text
<observation>   <type>feature</type>   <title>Added token bucket rate limiting returning 429 in RateLimiter</title>   <subtitle>RateLimiter now rejects requests with TooManyRequestsException when the token bucket is empty.</subtitle>   <facts>     <fact>RateLimiter throws TooManyRequestsException when bucket.tryConsume(1) returns false, producing HTTP 429.</fact>     <fact>RateLimiterTest#eleventh... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>SessionCookieConfig now marks the session cookie HttpOnly, Secure and SameSite=Strict to block client-side access and CSRF.</subtitle>   <facts>     <fact>SessionCookieConfig sets cookie.setHttpOnly(true), cookie.setSecure(true) and cookie.setSameSite(Strict) on the session cookie.... [truncated]
```

### config-hikari-pool

```text
<observation>   <type>change</type>   <title>Set Hikari maximum-pool-size to 20 and minimum-idle to 5 in application.yml</title>   <subtitle>Hikari connection pool sized to 20 maximum connections and 5 idle connections in the datasource configuration.</subtitle>   <facts>     <fact>application.yml sets spring.datasource.hikari.maximum-pool-size to 20.</fact>     <fact>application.yml sets spring.d... [truncated]
```

### config-log-level

```text
<observation>   <type>change</type>   <title>Changed root logger to INFO and JSON appender in logback-spring.xml</title>   <subtitle>Root logging level switched to INFO and bound to the JSON appender in logback-spring.xml.</subtitle>   <facts>     <fact>logback-spring.xml sets root level="INFO" with appender-ref ref="JSON".</fact>     <fact>LogbackConfigTest parses the logback-spring.xml configura... [truncated]
```

### config-cors

```text
<observation>   <type>bugfix</type>   <title>Restricted CORS origins for /api/** to https://app.acme.com in CorsConfig</title>   <subtitle>CorsConfig narrows /api/** to a single HTTPS origin with GET and POST only; CorsConfigTest covers a rejected origin.</subtitle>   <facts>     <fact>CorsConfig maps /api/** with allowedOrigins restricted to https://app.acme.com.</fact>     <fact>CorsConfig permi... [truncated]
```

### config-docker-healthcheck

```text
<observation>   <type>change</type>   <title>Added HEALTHCHECK probing /actuator/health in Dockerfile</title>   <subtitle>Dockerfile now probes the Spring Boot actuator health endpoint every 30 seconds.</subtitle>   <facts>     <fact>Dockerfile adds `HEALTHCHECK --interval=30s CMD curl -f http://localhost:8080/actuator/health || exit 1`</fact>     <fact>docker build succeeds with the new HEALTHCHE... [truncated]
```

### config-maven-plugin

```text
```xml <observation>   <type>change</type>   <title>Extended build coverage reporting with jacoco-maven-plugin 0.8.12 in pom.xml</title>   <subtitle>Jacoco coverage plugin added to the Maven build so mvn verify emits a Jacoco XML report.</subtitle>   <facts>     <fact>pom.xml build plugins now include org.jacoco:jacoco-maven-plugin version 0.8.12.</fact>     <fact>Running mvn verify writes the cov... [truncated]
```

### docs-readme-setup

```text
<observation>   <type>change</type>   <title>Documented local setup steps JDK 21 postgres in README.md</title>   <subtitle>README.md now includes a three-step local development setup procedure using JDK 21, docker compose and Maven.</subtitle>   <facts>     <fact>README.md setup section lists three steps: install JDK 21, run `docker compose up -d postgres`, and run `mvn spring-boot:run`.</fact>   ... [truncated]
```

### docs-runbook

```text
```xml <observation>   <type>change</type>   <title>**title**: Documented queue recovery steps in docs/runbook.md</title>   <subtitle>**subtitle**: Runbook documents queue recovery: inspect pending_messages, restart the worker, verify /api/health.</subtitle>   <facts>     <fact>docs/runbook.md defines a three-step queue recovery procedure: check pending_messages, restart worker, verify /api/health... [truncated]
```

### docs-changelog

```text
<observation>   <type>change</type>   <title>Documented 2.4.0 release notes in CHANGELOG.md</title>   <subtitle>CHANGELOG gains a 2.4.0 entry dated 2026-11-03 covering offline replay evaluation and a V20 migration conflict fix.</subtitle>   <facts>     <fact>CHANGELOG.md adds a "## 2.4.0 - 2026-11-03" section with two bullet items: offline replay evaluation and V20 migration conflict fix.</fact>  ... [truncated]
```

### docs-architecture-adr

```text
<observation>   <type>decision</type>   <title>**title**: Recorded ADR extraction validation decision in 0007-extraction-validation.md</title>   <subtitle>**subtitle**: ADR 0007 documents that extracted facts must be validated against tool output before persistence, with a rejection path for mismatches.</subtitle>   <facts>     <fact>ADR 0007 records the decision to validate extracted facts agains... [truncated]
```

### docs-migration-guide

```text
<observation>   <type>change</type>   <title>Documented V20 mem_sessions backup and manual_sentinel_reviewed flag in MIGRATION.md</title>   <subtitle>Added a V20 migration section instructing operators to back up mem_sessions before enabling manual_sentinel_reviewed.</subtitle>   <facts>     <fact>docs/MIGRATION.md documents a V20 migration prerequisite requiring a backup of the mem_sessions table... [truncated]
```

### docs-security-policy

```text
```xml <observation>   <type>change</type>   <title>Documented vulnerability disclosure policy in SECURITY.md</title>   <subtitle>SECURITY.md establishes security@acme.com as the vulnerability reporting channel and prohibits public issues.</subtitle>   <facts>     <fact>SECURITY.md defines the disclosure channel security@acme.com for reporting vulnerabilities.</fact>     <fact>SECURITY.md forbids ... [truncated]
```

### docs-contributing

```text
```xml <observation>   <type>change</type>   <title>Documented verify-local.sh prerequisite and four test layers in CONTRIBUTING.md</title>   <subtitle>CONTRIBUTING.md now requires running ./scripts/verify-local.sh before opening a pull request and names the four test layers.</subtitle>   <facts>     <fact>CONTRIBUTING.md requires running ./scripts/verify-local.sh before opening a pull request.</f... [truncated]
```

### deploy-k8s-probe

```text
<observation>   <type>change</type>   <title>Added readinessProbe on /api/health:37777 to deployment.yaml</title>   <subtitle>Deployment now gates pod readiness on the /api/health endpoint with a 15-second initial delay.</subtitle>   <facts>     <fact>deploy/deployment.yaml adds a readinessProbe using httpGet path /api/health on port 37777</fact>     <fact>The readinessProbe sets initialDelaySecon... [truncated]
```

### deploy-github-actions

```text
```xml <observation>   <type>change</type>   <title>Extended CI matrix with JDK 21 and 25 in ci.yml</title>   <subtitle>The backend workflow now builds and tests on both Java 21 and Java 25 via a matrix strategy.</subtitle>   <facts>     <fact>.github/workflows/ci.yml sets `strategy.matrix.java` to `[21, 25]`.</fact>     <fact>The backend CI pipeline now runs on JDK 21 and JDK 25 in parallel inste... [truncated]
```

### deploy-docker-multistage

```text
```xml <observation>   <type>change</type>   <title>Converted build image to multi-stage runtime in Dockerfile</title>   <subtitle>Dockerfile now builds with maven and runs on eclipse-temurin:21-jre, cutting image size from 780MB to 210MB.</subtitle>   <facts>     <fact>Dockerfile is now multi-stage: a build stage uses maven, and the runtime stage uses eclipse-temurin:21-jre.</fact>     <fact>Runt... [truncated]
```

### deploy-terraform-iam

```text
<observation>   <type>bugfix</type>   <title>Restricted deploy role IAM policy to AmazonS3ReadOnlyAccess in main.tf</title>   <subtitle>Deploy role now has read-only S3 access instead of full S3 permissions.</subtitle>   <facts>     <fact>main.tf attaches `arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess` to the `deploy` role instead of full S3 access</fact>     <fact>terraform validate succeeds aft... [truncated]
```

### deploy-helm-values

```text
<observation>   <type>change</type>   <title>Changed replicaCount to 3 and memory limit to 2Gi in values.yaml</title>   <subtitle>Helm values scale the deployment to 3 replicas with a 2Gi memory cap; helm template renders 3 pods.</subtitle>   <facts>     <fact>infra/values.yaml sets replicaCount to 3.</fact>     <fact>infra/values.yaml sets resources.limits.memory to 2Gi.</fact>     <fact>helm tem... [truncated]
```

### deploy-rollback-script

```text
<observation>   <type>feature</type>   <title>Added claude-mem rollback script in rollback.sh</title>   <subtitle>New operational script undoes the claude-mem deployment and waits for its rollout status.</subtitle>   <facts>     <fact>scripts/rollback.sh runs `kubectl rollout undo deployment/claude-mem` followed by `kubectl rollout status deployment/claude-mem`</fact>     <fact>scripts/rollback.sh... [truncated]
```

### deploy-canary

```text
<observation>   <type>change</type>   <title>Set canary weight to 10 and errorThreshold to 0.02 in canary.yaml</title>   <subtitle>Canary rollout is enabled with a 10 percent traffic weight and automatic rollback above a 2 percent error rate.</subtitle>   <facts>     <fact>infra/canary.yaml sets canary.enabled to true, canary.weight to 10, and canary.errorThreshold to 0.02.</fact>     <fact>The 10... [truncated]
```

### remove-dependency

```text
<observation>   <type>change</type>   <title>Removed commons-lang3 dependency from pom.xml</title>   <subtitle>Deleted the unused org.apache.commons:commons-lang3 declaration; the build still resolves.</subtitle>   <facts>     <fact>pom.xml no longer declares org.apache.commons:commons-lang3</fact>     <fact>Build resolves successfully without the commons-lang3 dependency</fact>   </facts>   <narr... [truncated]
```

### remove-dead-code

```text
<observation>   <type>refactor</type>   <title>Removed legacySync from OrderService</title>   <subtitle>Deleted the unreferenced legacySync method from OrderService, leaving no remaining callers.</subtitle>   <facts>     <fact>src/main/java/com/acme/orders/OrderService.java no longer defines the public method legacySync(Order order), which previously called warehouse.push(order).</fact>     <fact>... [truncated]
```

### remove-stale-config

```text
<observation>   <type>change</type>   <title>Removed legacy-search.enabled flag from application.yml</title>   <subtitle>Search module now reads only search-v2 because the legacy-search.enabled key was deleted from application.yml.</subtitle>   <facts>     <fact>src/main/resources/application.yml no longer defines legacy-search.enabled, which was previously set to true.</fact>     <fact>The search... [truncated]
```

### remove-deprecated-endpoint

```text
<observation>   <type>change</type>   <title>Removed listLegacy /v1/users mapping in UserController</title>   <subtitle>Deleted the legacy list endpoint and its service call while keeping the /v2/users mapping.</subtitle>   <facts>     <fact>UserController.listLegacy and its @GetMapping("/v1/users") mapping were removed from src/main/java/com/acme/users/UserController.java.</fact>     <fact>The /v... [truncated]
```

### remove-feature-flag

```text
<observation>   <type>change</type>   <title>Removed LEGACY_SEARCH constant from FeatureFlags</title>   <subtitle>Deleted the legacy-search feature flag constant; callers now reference SEARCH_V2.</subtitle>   <facts>     <fact>FeatureFlags.LEGACY_SEARCH constant with value "legacy-search" was removed from src/main/java/com/acme/flags/FeatureFlags.java.</fact>     <fact>Callers that previously refe... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
