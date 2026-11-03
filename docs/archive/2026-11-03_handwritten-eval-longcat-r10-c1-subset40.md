golden set: main (87 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: r10-c1-main
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 40 unique; runs scored: 3; key repeat: 3; real calls: 58 (cap none); duration: 1019217 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 100% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 0/0/0/40 |
| format errors | 0 |
| validation rejections | 0 |
| call failures | 0 |
| title exact-hit rate (legacy) | 63% |
| title normalized hit rate (legacy) | 70% |
| title action-class hit rate (A6) | 90% (40 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 88% |
| title subject hit rate (legacy, diagnostic) | 83% |
| title trace-visible subject hit rate (A6b) | 88% (33 scored, 7 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 73% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 78% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 75% |
| title identifier hit rate | 98% |
| type accuracy | 93% |
| facts keyword precision (legacy) / recall | 72% / 87% |
| facts informative precision | 81% |
| facts trace-support rate | 100% |
| concepts precision / recall | 78% / 78% |
| skip consistency across repeats | 100% (9 repeated samples) |
| latency p50 / p95 / max (ms) | 13395 / 40410 / 48999 |
| tokens p50 / p95 / max / mean per call | 4539 / 5678 / 6609 / 4743 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 81% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 90% | PASS |
| A6b | title trace-visible subject | >= 90% | 88% | **FAIL** |
| A6c | title identifiers | = 100% | 98% | **FAIL** |
| A6 | all three title dimensions together | all pass | 78% | **FAIL** |
| A11 | p95 latency vs baseline | <= 1.5x baseline | 40410 ms (baseline 33716 ms) | PASS |
| A12 | p95 tokens vs baseline | <= 1.5x baseline | 5678 tokens (baseline 5763) | PASS |

## Status distribution

| status | calls |
|---|---|
| 200 | 40 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 40 | 100% | 70% | 73% | 72% | 81% | 87% | 0 |
| 1 | 9 | 100% | 44% | 56% | 81% | 78% | 100% | 0 |
| 2 | 9 | 100% | 78% | 89% | 87% | 81% | 100% | 0 |

- average: skip 100%, title 64%, title contract 72%, facts P/R 80%/96%, facts actionable 80%
- worst run: skip 100%, title 44%, title contract 56%, facts P/R 72%/87%, facts actionable 78%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 12/12 | 7/12 |
| refactor | 9 | 9/9 | 7/9 | 7/9 |
| test-add | 9 | 9/9 | 9/9 | 9/9 |
| security-fix | 9 | 9/9 | 8/9 | 4/9 |
| config-change | 1 | 1/1 | 1/1 | 1/1 |

## Run-to-run variance (repeated samples)

| metric | min | max | mean | std dev |
|---|---|---|---|---|
| type accuracy | 93% | 100% | 98% | 4% |
| title action class | 90% | 100% | 97% | 5% |
| title trace-visible subject | 83% | 88% | 85% | 2% |
| title identifiers | 89% | 100% | 95% | 5% |
| title trace contract | 78% | 89% | 81% | 5% |
| facts informative | 78% | 81% | 80% | 2% |
| skip accuracy | 100% | 100% | 100% | 0% |

## Inconsistent samples (type or action changed between runs)

None: every repeated sample kept the same type and action class.

## Unreachable subject labels (excluded from A6b)

7 sample(s) declare subject tokens the recorded trace never contains:

- bug-offbyone-pagination: [pagination]
- bug-null-cache: [race]
- bug-race-counter: [race]
- bug-n-plus-one: [n+1]
- bug-regex-redos: [redos]
- bug-file-encoding: [encoding]
- refactor-auth-controller: [delegation]

## Failure clusters (first run)

| cluster | misses | share of scored (40) |
|---|---|---|
| identifier missing | 1 | 3% |
| action class wrong | 4 | 10% |
| subject wrong | 4 | 10% |
| type wrong | 3 | 8% |

### identifier missing (1)

- security-secret-config — expected identifiers [application.yml, ACME_API_KEY], title 'Redacted literal API key sk-live-123 in application.yml'

### action class wrong (4)

- refactor-config-loader — expected [modify], got 'secure' from title 'Hardened ConfigLoader to return unmodifiable map in ConfigLoader.java'
- security-sql-injection — expected [secure], got 'modify' from title 'Fixed UserDao.findByEmail SQL injection with parameter placeholder'
- security-path-traversal — expected [secure], got 'modify' from title 'Fixed path traversal in FileDownloadController.java'
- security-rate-limit — expected [secure], got 'create' from title 'Added rate limit enforcement throwing TooManyRequestsException in RateLimiter.java'

### subject wrong (4)

- bug-payment-missing-order — expected subject tokens [missing, order], title 'Fixed PaymentService findByOrderId null return with PaymentNotFoundException in PaymentService.java'
- refactor-sql-repository — expected subject tokens [jpql], title 'Fixed SQL injection in UserRepository tenant query with parameterized @Query'
- security-redact-log — expected subject tokens [credentials], title 'Redacted password, token and secret values in LogSanitizer.java'
- security-cookie-httponly — expected subject tokens [httponly, cookie], title 'Hardened session cookie flags in SessionCookieConfig'

### type wrong (3)

- refactor-config-loader — expected 'refactor', got 'change' (title 'Hardened ConfigLoader to return unmodifiable map in ConfigLoader.java')
- refactor-sql-repository — expected 'refactor', got 'bugfix' (title 'Fixed SQL injection in UserRepository tenant query with parameterized @Query')
- security-rate-limit — expected 'bugfix', got 'feature' (title 'Added rate limit enforcement throwing TooManyRequestsException in RateLimiter.java')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 13383 |
| bug-null-cache | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/50% | 30228 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 50%/50% | 13651 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 67%/100% | 100% | 50%/50% | 15831 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 11876 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 67%/67% | 67% | 100%/100% | 9331 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 20054 |
| bug-race-counter | bug-fix | observation | observation | pass | 75% | hit | miss | match | 67%/67% | 100% | 50%/50% | 15314 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 100%/100% | 8983 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 10650 |
| bug-regex-redos | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 16772 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 67% | 100%/100% | 10574 |
| refactor-invoice | refactor | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 67% | 100%/100% | 12581 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/50% | 67% | 100%/100% | 13197 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | MISS (subject hit, id hit, action MISS) | miss | MISS | 67%/100% | 67% | 100%/100% | 28330 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 13661 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 13223 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 100%/100% | 12642 |
| refactor-sql-repository | refactor | observation | observation | pass | 33% | MISS (subject MISS, id hit, action hit) | miss | MISS | 50%/50% | 100% | 0%/0% | 42073 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 67% | 100%/100% | 13546 |
| refactor-feature-flag | refactor | observation | observation | pass | 100% | hit | miss | match | 100%/50% | 100% | 100%/100% | 12066 |
| test-repro-timezone | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 67% | 50%/50% | 18198 |
| test-repro-login | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 50%/50% | 12238 |
| test-unit-retry | test-add | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 100% | 100%/100% | 13462 |
| test-integration-order | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 12649 |
| test-parameterized-csv | test-add | observation | observation | pass | 100% | hit | hit | match | 33%/50% | 33% | 100%/100% | 18837 |
| test-security-headers | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 13395 |
| test-repro-null-cache | test-add | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 50%/50% | 12825 |
| test-contract-openapi | test-add | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 28821 |
| test-flaky-clock | test-add | observation | observation | pass | 100% | hit | miss | match | 67%/100% | 100% | 100%/100% | 39135 |
| security-redact-log | security-fix | observation | observation | pass | 67% | MISS (subject MISS, id hit, action hit) | miss | match | 100%/100% | 100% | 50%/50% | 32488 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 100%/100% | 100% | 50%/50% | 12221 |
| security-authz-admin | security-fix | observation | observation | pass | 67% | hit | miss | match | 100%/50% | 100% | 50%/50% | 13263 |
| security-csrf | security-fix | observation | observation | pass | 67% | hit | miss | match | 100%/50% | 100% | 50%/50% | 48999 |
| security-secret-config | security-fix | observation | observation | pass | 50% | MISS (subject hit, id MISS, action hit) | miss | match | 100%/100% | 100% | 100%/100% | 40410 |
| security-xxe | security-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 100% | 100%/100% | 34980 |
| security-path-traversal | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | match | 33%/100% | 67% | 100%/100% | 12284 |
| security-rate-limit | security-fix | observation | observation | pass | 100% | MISS (subject hit, id hit, action MISS) | hit | MISS | 67%/100% | 67% | 50%/50% | 17957 |
| security-cookie-httponly | security-fix | observation | observation | pass | 75% | MISS (subject MISS, id hit, action hit) | miss | match | 50%/100% | 100% | 0%/0% | 13275 |
| config-hikari-pool | config-change | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 100%/100% | 12713 |

## Problems

- bug-offbyone-pagination: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-payment-missing-order: title coverage 75% (expected keywords [fix, payment, missing, order])
- bug-payment-missing-order: title subject [missing, order] missing (got 'Fixed PaymentService findByOrderId null return with PaymentNotFoundException in PaymentService.java')
- bug-payment-missing-order: title trace-visible subject [missing, order] missing (got 'Fixed PaymentService findByOrderId null return with PaymentNotFoundException in PaymentService.java')
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-jwt-expiry: facts recall 67% (expected [expiration, isEqual, rejects], got [JwtVerifier changed `claims.getExpiration().before(now)` to `claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now)`, A token whose expiration equals the current instant was previously accepted as valid, JwtVerifierTest#expiredAtExactBoundary passes after the change])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: title coverage 75% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: facts recall 67% (expected [incrementAndGet, AtomicLong, 1000], got [CounterService.increment previously called counter.set(counter.get() + 1) then returned counter.get(), a non-atomic read-modify-write., CounterService.increment now returns counter.incrementAndGet() directly., CounterServiceConcurrencyTest passes with 1000 threads producing a final counter value of 1000.])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-regex-redos: title coverage 75% (expected keywords [fix, redos, urlvalidator, regex])
- bug-regex-redos: title subject [redos] missing (got 'Fixed UrlValidator nested-quantifier regex causing long-input latency in UrlValidator.java')
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed FileReaderFactory charset to UTF-8 in FileReaderFactory.java')
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [AuthController.java no longer builds JWTs inline and now calls tokenService.issue(authentication)., TokenService.issue(authentication) is the new single entry point for token issuance., AuthControllerTest remained unchanged and green after the refactor.])
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title action [modify] missing: title leads 'secure' (got 'Hardened ConfigLoader to return unmodifiable map in ConfigLoader.java')
- refactor-config-loader: title verb class 'refactor' missing (got 'Hardened ConfigLoader to return unmodifiable map in ConfigLoader.java')
- refactor-config-loader: type 'change' != expected 'refactor'
- refactor-retry-policy: facts recall 50% (expected [BackoffStrategy, 100/200/400], got [src/main/java/com/acme/resilience/RetryPolicy.java now returns backoff.nextDelay(attempt) instead of computing the delay inline., RetryPolicyTest continues to assert the 100/200/400 millisecond delay sequence after the extraction.])
- refactor-sql-repository: title coverage 33% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title verb class 'refactor' missing (got 'Fixed SQL injection in UserRepository tenant query with parameterized @Query')
- refactor-sql-repository: title subject [jpql] missing (got 'Fixed SQL injection in UserRepository tenant query with parameterized @Query')
- refactor-sql-repository: title trace-visible subject [jpql] missing (got 'Fixed SQL injection in UserRepository tenant query with parameterized @Query')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [UserRepository.java now declares `@Query("select u from User u where u.tenantId = :tenantId")` for the tenant lookup., UserRepositoryTest returns the same tenant rows after the change, confirming behavior is preserved.])
- refactor-sql-repository: concepts recall 0% (expected [what-changed, pattern], got [problem-solution, gotcha])
- refactor-event-bus: facts recall 50% (expected [listener map, event type], got [EventBus.java now uses listeners.getOrDefault(type, List.of()) to dispatch events by type., The if/else dispatch chain in EventBus was replaced with a map-based lookup keyed by event type., EventBusTest dispatches the same three events after the refactor, confirming behavior is preserved.])
- refactor-feature-flag: facts recall 50% (expected [Flags.isEnabled, on/off matrix], got [FeatureFlagService evaluation now returns flags.isEnabled(flag, context) instead of inline logic.])
- test-repro-timezone: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-repro-login: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- test-unit-retry: facts recall 50% (expected [100/200/400, delayDoubles], got [RetryPolicyTest#delayDoubles is a @ParameterizedTest using @CsvSource with three cases: (1,100), (2,200), (3,400)])
- test-parameterized-csv: facts recall 50% (expected [quotingIsStable, 3 cases], got [CsvExporterTest#quotingIsStable is a @ParameterizedTest using @ValueSource, Test cases cover comma ("a,b"), quote ("a\"b"), and newline ("a\nb") characters, All 3 parameterized cases pass])
- test-repro-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-redact-log: title coverage 67% (expected keywords [redact, credentials, logsanitizer])
- security-redact-log: title subject [credentials] missing (got 'Redacted password, token and secret values in LogSanitizer.java')
- security-redact-log: title trace-visible subject [credentials] missing (got 'Redacted password, token and secret values in LogSanitizer.java')
- security-redact-log: concepts recall 50% (expected [problem-solution, gotcha], got [what-changed, gotcha])
- security-sql-injection: title action [secure] missing: title leads 'modify' (got 'Fixed UserDao.findByEmail SQL injection with parameter placeholder')
- security-sql-injection: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- security-authz-admin: title coverage 67% (expected keywords [harden, admincontroller, authorization])
- security-authz-admin: title verb class 'harden' missing (got 'Restricted AdminController endpoints authorization to ROLE_ADMIN')
- security-authz-admin: facts recall 50% (expected [ROLE_ADMIN, 403], got [AdminController.java now has @PreAuthorize("hasRole('ADMIN')") applied at class level, All AdminController endpoints now require ROLE_ADMIN authorization])
- security-authz-admin: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- security-csrf: title coverage 67% (expected keywords [fix, csrf, csrffilter])
- security-csrf: title verb class 'fix' missing (got 'Hardened CSRF token validation in CsrfFilter.java')
- security-csrf: facts recall 50% (expected [403, mismatched token], got [CsrfFilter.java now checks <code>csrfToken.matches(request)</code> and calls <code>response.sendError(403)</code> with an early return when the token does not match, CsrfFilterTest#postWithoutTokenIs403 verifies the 403 rejection behavior and passes])
- security-csrf: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed])
- security-secret-config: title lost identifiers [application.yml, ACME_API_KEY] (got 'Redacted literal API key sk-live-123 in application.yml')
- security-secret-config: title verb class 'remove' missing (got 'Redacted literal API key sk-live-123 in application.yml')
- security-secret-config: title subject [hardcoded, api, key] missing (got 'Redacted literal API key sk-live-123 in application.yml')
- security-path-traversal: title action [secure] missing: title leads 'modify' (got 'Fixed path traversal in FileDownloadController.java')
- security-rate-limit: title action [secure] missing: title leads 'create' (got 'Added rate limit enforcement throwing TooManyRequestsException in RateLimiter.java')
- security-rate-limit: type 'feature' != expected 'bugfix'
- security-rate-limit: concepts recall 50% (expected [problem-solution, pattern], got [what-changed, pattern])
- security-cookie-httponly: title coverage 75% (expected keywords [harden, session, cookie, httponly])
- security-cookie-httponly: title subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- security-cookie-httponly: title trace-visible subject [httponly, cookie] missing (got 'Hardened session cookie flags in SessionCookieConfig')
- security-cookie-httponly: concepts recall 0% (expected [problem-solution, gotcha], got [what-changed, what-changed])

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset off-by-one in pagination</title>   <subtitle>pageOffset now returns (page * size) without the extra -1, so page 1 starts at row 0 instead of row -1.</subtitle>   <facts>     <fact>OrderQuery.pageOffset returned (page * size - 1), producing a negative offset for page 0</fact>     <fact>OrderQuery.pageOffset now returns (page *... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache.get null placeholder race with computeIfAbsent</title>   <subtitle>Replaced plain cache.get(id) with atomic computeIfAbsent so concurrent callers cannot both write a null placeholder.</subtitle>   <facts>     <fact>UserCache.get changed from `cache.get(id)` to `cache.computeIfAbsent(id, loader::load)` in src/main/java/com/acme/cache/User... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone reproducing off-by-one-day bug</title>   <subtitle>New test pins 2026-11-01T23:30Z and expects Asia/Shanghai business date 2026-11-02.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins input 2026-11-01T23:30Z and expects the Asia/Shanghai business date 2026-11-02.</fact>     ... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed PaymentService findByOrderId null return with PaymentNotFoundException in PaymentService.java</title>   <subtitle>PaymentService now throws PaymentNotFoundException instead of returning null when orderId is missing.</subtitle>   <facts>     <fact>PaymentService.findByOrderId now throws PaymentNotFoundException for a missing orderId</fact>     <fac... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed JwtVerifier expiration boundary in JwtVerifier.java</title>   <subtitle>JwtVerifier now rejects tokens whose expiration equals the current instant instead of accepting them.</subtitle>   <facts>     <fact>JwtVerifier changed `claims.getExpiration().before(now)` to `claims.getExpiration().isBefore(now) || claims.getExpiration().isEqual(now)`</fact>... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed CsvExporter multiline field line endings normalization in CsvExporter.java</title>   <subtitle>Embedded newlines in CSV fields are now normalized to CRLF before quoting, preserving row count integrity.</subtitle>   <facts>     <fact>CsvExporter.java now calls line.replaceAll("\r?\n", "\r\n") before writing each quoted line</fact>     <fact>The reg... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment race condition in CounterService.java</title>   <subtitle>Replaced non-atomic get/set pair with AtomicLong.incrementAndGet to eliminate lost updates under concurrent calls.</subtitle>   <facts>     <fact>CounterService.increment previously called counter.set(counter.get() + 1) then returned counter.get(), a non-atomic read... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator nested-quantifier regex causing long-input latency in UrlValidator.java</title>   <subtitle>Replaced a nested-quantifier URL regex with a linear pattern so long inputs finish under 100ms.</subtitle>   <facts>     <fact>UrlValidator replaces the nested-quantifier regex with the linear pattern `^https?://[^\\s]+$`.</fact>     <fact>UrlV... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed FileReaderFactory charset to UTF-8 in FileReaderFactory.java</title>   <subtitle>FileReaderFactory now always decodes input as UTF-8 instead of the platform default charset.</subtitle>   <facts>     <fact>FileReaderFactory now builds InputStreamReader(in, StandardCharsets.UTF_8) instead of using the platform default charset.</fact>     <fact>FileR... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored AuthController JWT issuance with TokenService delegation in AuthController.java</title>   <subtitle>AuthController now delegates token creation to TokenService.issue instead of building JWTs inline.</subtitle>   <facts>     <fact>AuthController.java no longer builds JWTs inline and now calls tokenService.issue(authentication).</fact>     <f... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>change</type>   <title>Hardened ConfigLoader to return unmodifiable map in ConfigLoader.java</title>   <subtitle>ConfigLoader now returns an unmodifiable config map and exposes typed accessors, preserving existing values.</subtitle>   <facts>     <fact>ConfigLoader returns an unmodifiable map via `properties.entrySet().stream().collect(toUnmodifiableMap(...))`</fact>     <fac... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Refactored RetryPolicy delay calculation via BackoffStrategy in RetryPolicy.java</title>   <subtitle>RetryPolicy now delegates delay computation to BackoffStrategy, preserving the existing 100/200/400 delay sequence.</subtitle>   <facts>     <fact>src/main/java/com/acme/resilience/RetryPolicy.java now returns backoff.nextDelay(attempt) instead of comp... [truncated]
```

### refactor-sql-repository

```text
<observation>   <type>bugfix</type>   <title>Fixed SQL injection in UserRepository tenant query with parameterized @Query</title>   <subtitle>Replaced string-concatenated SQL in UserRepository with a JPQL @Query binding :tenantId as a parameter.</subtitle>   <facts>     <fact>UserRepository.java now declares `@Query("select u from User u where u.tenantId = :tenantId")` for the tenant lookup.</fact... [truncated]
```

### refactor-event-bus

```text
<observation>   <type>refactor</type>   <title>Refactored EventBus dispatch with listener map keyed by event type</title>   <subtitle>Replaced if/else dispatch chain in EventBus with a listener map lookup using getOrDefault.</subtitle>   <facts>     <fact>EventBus.java now uses listeners.getOrDefault(type, List.of()) to dispatch events by type.</fact>     <fact>The if/else dispatch chain in EventB... [truncated]
```

### refactor-feature-flag

```text
<observation>   <type>refactor</type>   <title>Refactored FeatureFlagService to delegate evaluation to Flags.isEnabled</title>   <subtitle>FeatureFlagService now delegates flag evaluation to Flags.isEnabled while preserving existing on/off behavior.</subtitle>   <facts>     <fact>FeatureFlagService evaluation now returns flags.isEnabled(flag, context) instead of inline logic.</fact>     <fact>Feat... [truncated]
```

### test-repro-timezone

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone reproducing DailyReport dateAt UTC bug</title>   <subtitle>Reproduction test pins the reported UTC-vs-business-date mismatch for Asia/Shanghai in DailyReport.dateAt.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone asserts DailyReport.dateAt(Instant.parse("2026-11-01T23:30:00Z"),... [truncated]
```

### test-repro-login

```text
<observation>   <type>feature</type>   <title>Added LoginControllerTest#lockedAccountReturns423 reproducing locked account status defect</title>   <subtitle>A reproduction test was written that pins the wrong HTTP status returned for locked accounts on /login.</subtitle>   <facts>     <fact>LoginControllerTest#lockedAccountReturns423 stubs users.isLocked("bob") to return true.</fact>     <fact>The... [truncated]
```

### test-unit-retry

```text
<observation>   <type>feature</type>   <title>Added RetryPolicyTest#delayDoubles parameterized cases for exponential backoff</title>   <subtitle>Parameterized test verifies RetryPolicy delay doubles per attempt: 100ms, 200ms, 400ms for attempts 1-3.</subtitle>   <facts>     <fact>RetryPolicyTest#delayDoubles is a @ParameterizedTest using @CsvSource with three cases: (1,100), (2,200), (3,400)</fact... [truncated]
```

### test-parameterized-csv

```text
<observation>   <type>feature</type>   <title>Added CsvExporterTest#quotingIsStable parameterized cases for CSV quoting</title>   <subtitle>Parameterized test verifies CSV export stability across commas, quotes, and newlines.</subtitle>   <facts>     <fact>CsvExporterTest#quotingIsStable is a @ParameterizedTest using @ValueSource</fact>     <fact>Test cases cover comma ("a,b"), quote ("a\"b"), and... [truncated]
```

### test-repro-null-cache

```text
<observation>   <type>feature</type>   <title>Added CacheConcurrencyTest#concurrentReadersNeverSeeNull reproducing UserCache null</title>   <subtitle>New reproduction test launches 1000 parallel readers to expose intermittent null returns from UserCache.</subtitle>   <facts>     <fact>CacheConcurrencyTest#concurrentReadersNeverSeeNull uses IntStream.range(0, 1000).parallel() to drive concurrent ca... [truncated]
```

### security-redact-log

```text
<observation>   <type>bugfix</type>   <title>Redacted password, token and secret values in LogSanitizer.java</title>   <subtitle>Log output now masks credential values before they are written to logs.</subtitle>   <facts>     <fact>LogSanitizer.java applies `value.replaceAll("(password|token|secret)=\\S+", "$1=***")` to redact credential values before logging.</fact>     <fact>LogSanitizerTest#red... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Fixed UserDao.findByEmail SQL injection with parameter placeholder</title>   <subtitle>Replaced string concatenation in the email lookup query with a jdbcTemplate parameter placeholder.</subtitle>   <facts>     <fact>UserDao.findByEmail now executes jdbcTemplate.query("select * from users where email = ?", mapper, email) using a parameter placeholder in... [truncated]
```

### security-authz-admin

```text
<observation>   <type>bugfix</type>   <title>Restricted AdminController endpoints authorization to ROLE_ADMIN</title>   <subtitle>Added method-level @PreAuthorize annotation enforcing ROLE_ADMIN on all AdminController endpoints.</subtitle>   <facts>     <fact>AdminController.java now has @PreAuthorize("hasRole('ADMIN')") applied at class level</fact>     <fact>All AdminController endpoints now req... [truncated]
```

### security-csrf

```text
<observation>   <type>bugfix</type>   <title>Hardened CSRF token validation in CsrfFilter.java</title>   <subtitle>CsrfFilter now rejects state-changing requests with mismatched CSRF tokens using HTTP 403.</subtitle>   <facts>     <fact>CsrfFilter.java now checks <code>csrfToken.matches(request)</code> and calls <code>response.sendError(403)</code> with an early return when the token does not matc... [truncated]
```

### security-secret-config

```text
<observation>   <type>bugfix</type>   <title>Redacted literal API key sk-live-123 in application.yml</title>   <subtitle>Replaced hardcoded API key with environment variable reference so secrets no longer live in config.</subtitle>   <facts>     <fact>application.yml `api-key` value changed from literal `sk-live-123` to `${ACME_API_KEY}`.</fact>     <fact>SecretScanTest passes after the substituti... [truncated]
```

### security-path-traversal

```text
<observation>   <type>bugfix</type>   <title>Fixed path traversal in FileDownloadController.java</title>   <subtitle>FileDownloadController now rejects names escaping the base directory by normalizing resolved paths.</subtitle>   <facts>     <fact>FileDownloadController now computes `base.resolve(name).normalize()` and throws `InvalidPathException` when the result does not start with `base`.</fact... [truncated]
```

### security-rate-limit

```text
```xml <observation>   <type>feature</type>   <title>Added rate limit enforcement throwing TooManyRequestsException in RateLimiter.java</title>   <subtitle>RateLimiter now returns HTTP 429 when the token bucket is exhausted.</subtitle>   <facts>     <fact>RateLimiter calls bucket.tryConsume(1) and throws TooManyRequestsException when it returns false</fact>     <fact>RateLimiterTest#eleventhReques... [truncated]
```

### security-cookie-httponly

```text
<observation>   <type>bugfix</type>   <title>Hardened session cookie flags in SessionCookieConfig</title>   <subtitle>Session cookie now sets HttpOnly, Secure, and SameSite=Strict to mitigate XSS and CSRF exposure.</subtitle>   <facts>     <fact>SessionCookieConfig sets the session cookie with HttpOnly=true, Secure=true, and SameSite=Strict.</fact>     <fact>SessionCookieTest#flagsArePresent passe... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
