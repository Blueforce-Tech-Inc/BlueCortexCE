golden set: main (87 samples)

# Model output quality evaluation

- Date: 2026-11-03
- Phase: r10-c3-main
- Provider: openai, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Prompt source: classpath `prompts/` (current production prompt)
- Samples: 20 unique; runs scored: 3; key repeat: 3; real calls: 90 (cap none); duration: 683681 ms; temperature: 0.0
- Parser: production `XmlParser`; skip detection via `<skip>`.

## Headline (first run of every sample)

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 100% |
| skip accuracy | 100% |
|   skip confusion (TP/FP/FN/TN) | 0/0/0/20 |
| format errors | 0 |
| validation rejections | 0 |
| call failures | 0 |
| title exact-hit rate (legacy) | 55% |
| title normalized hit rate (legacy) | 55% |
| title action-class hit rate (A6) | 95% (20 scored, 0 unclassifiable) |
| title verb-class hit rate (diagnostic, retired) | 90% |
| title subject hit rate (legacy, diagnostic) | 85% |
| title trace-visible subject hit rate (A6b) | 92% (13 scored, 7 unreachable/excluded) |
| title contract hit rate (legacy definition, diagnostic) | 75% |
| title trace contract hit rate (A6: action + reachable subject + identifiers) | 85% |
| title verb contract hit rate (verb + subject + identifiers, diagnostic) | 70% |
| title identifier hit rate | 95% |
| type accuracy | 95% |
| facts keyword precision (legacy) / recall | 78% / 93% |
| facts informative precision | 86% |
| facts trace-support rate | 100% |
| concepts precision / recall | 80% / 80% |
| skip consistency across repeats | 100% (5 repeated samples) |
| latency p50 / p95 / max (ms) | 17913 / 38470 / 92621 |
| tokens p50 / p95 / max / mean per call | 13500 / 16085 / 18168 / 13920 |

## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)

| id | criterion | threshold | observed | status |
|---|---|---|---|---|
| A1 | format errors | = 0 | 0 | PASS |
| A2 | skip accuracy | >= 95% | 100% | PASS |
| A3 | must-record samples skipped | = 0 | 0 | PASS |
| A4 | low-value samples recorded | <= max(1, TP/20) | 0 | PASS |
| A5 | facts informative precision | >= 75% | 86% | PASS |
| A6a | title action class (trace-derived) | >= 80% | 95% | PASS |
| A6b | title trace-visible subject | >= 90% | 92% | PASS |
| A6c | title identifiers | = 100% | 95% | **FAIL** |
| A6 | all three title dimensions together | all pass | 85% | **FAIL** |
| A11 | p95 latency vs baseline | <= 1.5x baseline | 38470 ms (baseline 33716 ms) | PASS |
| A12 | p95 tokens vs baseline | <= 1.5x baseline | 16085 tokens (baseline 5763) | **FAIL** |

## Status distribution

| status | calls |
|---|---|
| 200 | 20 |

## Per run (single vs average vs worst)

| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |
|---|---|---|---|---|---|---|---|---|
| 0 | 20 | 100% | 55% | 75% | 78% | 86% | 93% | 0 |
| 1 | 5 | 100% | 80% | 80% | 73% | 80% | 93% | 0 |
| 2 | 5 | 100% | 80% | 80% | 93% | 87% | 100% | 0 |

- average: skip 100%, title 72%, title contract 78%, facts P/R 82%/95%, facts actionable 84%
- worst run: skip 100%, title 55%, title contract 75%, facts P/R 73%/93%, facts actionable 80%

## Per type

| category | samples | contract pass | type match | title normalized |
|---|---|---|---|---|
| bug-fix | 12 | 12/12 | 12/12 | 6/12 |
| refactor | 8 | 8/8 | 7/8 | 5/8 |

## Run-to-run variance (repeated samples)

| metric | min | max | mean | std dev |
|---|---|---|---|---|
| type accuracy | 95% | 100% | 98% | 2% |
| title action class | 95% | 100% | 98% | 2% |
| title trace-visible subject | 92% | 100% | 97% | 4% |
| title identifiers | 95% | 100% | 98% | 2% |
| title trace contract | 85% | 100% | 95% | 7% |
| facts informative | 80% | 87% | 84% | 3% |
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

| cluster | misses | share of scored (20) |
|---|---|---|
| identifier missing | 1 | 5% |
| action class wrong | 1 | 5% |
| subject wrong | 1 | 5% |
| type wrong | 1 | 5% |

### identifier missing (1)

- bug-null-cache — expected identifiers [UserCache, computeIfAbsent], title 'Fixed UserCache.get concurrent null placeholder race in UserCache.java'

### action class wrong (1)

- refactor-config-loader — expected [modify], got 'secure' from title 'Hardened ConfigLoader property map to unmodifiable map with typed accessors in ConfigLoader.java'

### subject wrong (1)

- refactor-invoice — expected subject tokens [delegation], title 'Refactored tax, rendering and mail via TaxCalculator, InvoiceRenderer, MailGateway in InvoiceService'

### type wrong (1)

- refactor-sql-repository — expected 'refactor', got 'bugfix' (title 'Fixed SQL injection in UserRepository tenant query with JPQL @Query')

## Per sample

| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| bug-offbyone-pagination | bug-fix | observation | observation | pass | 67% | hit | miss | match | 100%/100% | 100% | 100%/100% | 28301 |
| bug-null-cache | bug-fix | observation | observation | pass | 75% | MISS (subject unreachable, id MISS, action hit) | miss | match | 100%/100% | 67% | 50%/50% | 15898 |
| bug-timezone-report | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 38470 |
| bug-payment-missing-order | bug-fix | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 100% | 50%/50% | 19141 |
| bug-connection-leak | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 15054 |
| bug-jwt-expiry | bug-fix | observation | observation | pass | 75% | hit | miss | match | 67%/100% | 100% | 100%/100% | 12038 |
| bug-csv-crlf | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 50%/50% | 17913 |
| bug-race-counter | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 50%/50% | 20709 |
| bug-n-plus-one | bug-fix | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 67% | 50%/50% | 14846 |
| bug-dateformat-locale | bug-fix | observation | observation | pass | 100% | hit | hit | match | 100%/100% | 100% | 100%/100% | 26306 |
| bug-regex-redos | bug-fix | observation | observation | pass | 75% | hit | miss | match | 50%/100% | 100% | 100%/100% | 27978 |
| bug-file-encoding | bug-fix | observation | observation | pass | 75% | hit | miss | match | 100%/100% | 100% | 100%/100% | 13538 |
| refactor-invoice | refactor | observation | observation | pass | 67% | MISS (subject MISS, id hit, action hit) | miss | match | 67%/100% | 67% | 100%/100% | 92621 |
| refactor-auth-controller | refactor | observation | observation | pass | 100% | hit | hit | match | 100%/50% | 100% | 100%/100% | 12977 |
| refactor-config-loader | refactor | observation | observation | pass | 67% | MISS (subject hit, id hit, action MISS) | miss | match | 67%/100% | 67% | 100%/100% | 28165 |
| refactor-order-mapper | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/100% | 50% | 100%/100% | 16791 |
| refactor-retry-policy | refactor | observation | observation | pass | 100% | hit | hit | match | 50%/50% | 100% | 100%/100% | 12792 |
| refactor-logging-filter | refactor | observation | observation | pass | 100% | hit | hit | match | 67%/100% | 33% | 100%/100% | 17926 |
| refactor-sql-repository | refactor | observation | observation | pass | 67% | hit | miss | MISS | 100%/50% | 100% | 0%/0% | 24443 |
| refactor-event-bus | refactor | observation | observation | pass | 100% | hit | hit | match | 33%/100% | 67% | 100%/100% | 17371 |

## Problems

- bug-offbyone-pagination: title coverage 67% (expected keywords [fix, pagination, orderquery])
- bug-offbyone-pagination: title subject [pagination] missing (got 'Fixed OrderQuery.pageOffset off-by-one in OrderQuery.java')
- bug-null-cache: title lost identifiers [UserCache, computeIfAbsent] (got 'Fixed UserCache.get concurrent null placeholder race in UserCache.java')
- bug-null-cache: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, pattern])
- bug-timezone-report: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, pattern])
- bug-payment-missing-order: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-jwt-expiry: title coverage 75% (expected keywords [fix, jwt, expiry, boundary])
- bug-csv-crlf: concepts recall 50% (expected [problem-solution, what-changed], got [problem-solution, gotcha])
- bug-race-counter: title coverage 75% (expected keywords [fix, race, counter, atomiclong])
- bug-race-counter: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-n-plus-one: concepts recall 50% (expected [problem-solution, pattern], got [problem-solution, gotcha])
- bug-regex-redos: title coverage 75% (expected keywords [fix, redos, urlvalidator, regex])
- bug-file-encoding: title coverage 75% (expected keywords [fix, encoding, filereaderfactory, utf-8])
- bug-file-encoding: title subject [encoding] missing (got 'Fixed FileReaderFactory platform default charset by forcing UTF-8 in FileReaderFactory')
- refactor-invoice: title coverage 67% (expected keywords [refactor, invoiceservice, delegates])
- refactor-invoice: title subject [delegation] missing (got 'Refactored tax, rendering and mail via TaxCalculator, InvoiceRenderer, MailGateway in InvoiceService')
- refactor-invoice: title trace-visible subject [delegation] missing (got 'Refactored tax, rendering and mail via TaxCalculator, InvoiceRenderer, MailGateway in InvoiceService')
- refactor-auth-controller: facts recall 50% (expected [TokenService.issue, JWT], got [AuthController.java now returns `tokenService.issue(authentication)` instead of constructing JWTs inline])
- refactor-config-loader: title coverage 67% (expected keywords [refactor, configloader, unmodifiable])
- refactor-config-loader: title action [modify] missing: title leads 'secure' (got 'Hardened ConfigLoader property map to unmodifiable map with typed accessors in ConfigLoader.java')
- refactor-config-loader: title verb class 'refactor' missing (got 'Hardened ConfigLoader property map to unmodifiable map with typed accessors in ConfigLoader.java')
- refactor-retry-policy: facts recall 50% (expected [BackoffStrategy, 100/200/400], got [RetryPolicy.java now returns `backoff.nextDelay(attempt)` instead of computing the delay inline., RetryPolicyTest asserts the same 100/200/400 delay sequence after the refactor, confirming behavior is unchanged.])
- refactor-sql-repository: title coverage 67% (expected keywords [refactor, userrepository, jpql])
- refactor-sql-repository: title verb class 'refactor' missing (got 'Fixed SQL injection in UserRepository tenant query with JPQL @Query')
- refactor-sql-repository: type 'bugfix' != expected 'refactor'
- refactor-sql-repository: facts recall 50% (expected [JPQL, @Query], got [UserRepository now uses @Query("select u from User u where u.tenantId = :tenantId") instead of string-concatenated SQL.])
- refactor-sql-repository: concepts recall 0% (expected [what-changed, pattern], got [problem-solution, gotcha])

## Raw answers behind problems

### bug-offbyone-pagination

```text
<observation>   <type>bugfix</type>   <title>Fixed OrderQuery.pageOffset off-by-one in OrderQuery.java</title>   <subtitle>Removed the extra -1 from the pagination offset calculation so page 0 starts at index 0.</subtitle>   <facts>     <fact>OrderQuery.java changed `int offset = page * size - 1` to `int offset = page * size`, removing the off-by-one subtraction.</fact>     <fact>OrderQuery.pageOf... [truncated]
```

### bug-null-cache

```text
<observation>   <type>bugfix</type>   <title>Fixed UserCache.get concurrent null placeholder race in UserCache.java</title>   <subtitle>Concurrent callers could both write a null placeholder; get now uses computeIfAbsent for atomic load-and-insert.</subtitle>   <facts>     <fact>UserCache.get now returns cache.computeIfAbsent(id, loader::load) instead of cache.get(id)</fact>     <fact>The previous... [truncated]
```

### bug-timezone-report

```text
<observation>   <type>feature</type>   <title>Added DailyReportTest#reportUsesBusinessTimezone reproducing off-by-one-day bug</title>   <subtitle>New timezone test pins 2026-11-01T23:30Z input and expects Asia/Shanghai business date 2026-11-02.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone pins input timestamp 2026-11-01T23:30Z.</fact>     <fact>The test expects the Asi... [truncated]
```

### bug-payment-missing-order

```text
<observation>   <type>bugfix</type>   <title>Fixed PaymentService returning null for missing orderId in PaymentService.java</title>   <subtitle>PaymentService now throws PaymentNotFoundException instead of returning null when an orderId has no payment record.</subtitle>   <facts>     <fact>PaymentService.java now returns `repository.findByOrderId(orderId).orElseThrow(() -> new PaymentNotFoundExcep... [truncated]
```

### bug-jwt-expiry

```text
<observation>   <type>bugfix</type>   <title>Fixed token expiration boundary in JwtVerifier</title>   <subtitle>JwtVerifier now rejects tokens whose expiration equals the current instant, closing an off-by-one acceptance window.</subtitle>   <facts>     <fact>JwtVerifier previously checked only `claims.getExpiration().before(now)`, so a token expiring at the exact current instant was accepted.</fa... [truncated]
```

### bug-csv-crlf

```text
<observation>   <type>bugfix</type>   <title>Fixed embedded line endings in CsvExporter CSV quoting</title>   <subtitle>CsvExporter now normalises embedded line endings to CRLF before quoting so multiline fields keep row count.</subtitle>   <facts>     <fact>CsvExporter.java now writes `line.replaceAll("\\r?\\n", "\\r\\n")` to normalise embedded line endings to CRLF before quoting.</fact>     <fac... [truncated]
```

### bug-race-counter

```text
<observation>   <type>bugfix</type>   <title>Fixed CounterService.increment race condition in CounterService.java</title>   <subtitle>Replaced non-atomic get/set pair with AtomicLong.incrementAndGet to eliminate lost updates under concurrency.</subtitle>   <facts>     <fact>CounterService.increment now uses AtomicLong.incrementAndGet instead of counter.set(counter.get() + 1)</fact>     <fact>Count... [truncated]
```

### bug-n-plus-one

```text
<observation>   <type>bugfix</type>   <title>Fixed N+1 query in OrderRepository.findAllByCustomerId with @EntityGraph</title>   <subtitle>Order items are now fetched in the same SQL query instead of one query per order.</subtitle>   <facts>     <fact>OrderRepository.findAllByCustomerId now carries @EntityGraph(attributePaths = "items")</fact>     <fact>QueryCountTest now reports 1 SQL statement in... [truncated]
```

### bug-regex-redos

```text
<observation>   <type>bugfix</type>   <title>Fixed UrlValidator catastrophic backtracking in UrlValidator.java</title>   <subtitle>Replaced nested-quantifier regex with linear pattern to prevent ReDoS on long inputs.</subtitle>   <facts>     <fact>UrlValidator.java now compiles the URL pattern as `^https?://[^\\s]+$` (linear) instead of a nested-quantifier regex</fact>     <fact>UrlValidatorTest#l... [truncated]
```

### bug-file-encoding

```text
<observation>   <type>bugfix</type>   <title>Fixed FileReaderFactory platform default charset by forcing UTF-8 in FileReaderFactory</title>   <subtitle>FileReaderFactory now always decodes input streams as UTF-8, preventing accent corruption on non-UTF-8-default JVMs.</subtitle>   <facts>     <fact>FileReaderFactory constructs InputStreamReader with StandardCharsets.UTF_8 instead of the platform d... [truncated]
```

### refactor-invoice

```text
<observation>   <type>refactor</type>   <title>Refactored tax, rendering and mail via TaxCalculator, InvoiceRenderer, MailGateway in InvoiceService</title>   <subtitle>InvoiceService now delegates tax, rendering and mail to dedicated collaborators with no behaviour change.</subtitle>   <facts>     <fact>InvoiceService delegates tax calculation to TaxCalculator and rendering to InvoiceRenderer via ... [truncated]
```

### refactor-auth-controller

```text
<observation>   <type>refactor</type>   <title>Refactored AuthController JWT issuance with TokenService delegation</title>   <subtitle>AuthController now delegates token creation to TokenService.issue instead of building JWTs inline</subtitle>   <facts>     <fact>AuthController.java now returns `tokenService.issue(authentication)` instead of constructing JWTs inline</fact>     <fact>AuthController... [truncated]
```

### refactor-config-loader

```text
<observation>   <type>refactor</type>   <title>Hardened ConfigLoader property map to unmodifiable map with typed accessors in ConfigLoader.java</title>   <subtitle>ConfigLoader now returns an unmodifiable map and exposes typed accessors while preserving existing values.</subtitle>   <facts>     <fact>ConfigLoader.java now returns an unmodifiable map via `properties.entrySet().stream().collect(toUn... [truncated]
```

### refactor-retry-policy

```text
<observation>   <type>refactor</type>   <title>Refactored RetryPolicy delay calculation with BackoffStrategy delegation</title>   <subtitle>RetryPolicy now delegates delay computation to BackoffStrategy, preserving the 100/200/400 sequence.</subtitle>   <facts>     <fact>RetryPolicy.java now returns `backoff.nextDelay(attempt)` instead of computing the delay inline.</fact>     <fact>RetryPolicyTes... [truncated]
```

### refactor-sql-repository

```text
```xml <observation>   <type>bugfix</type>   <title>Fixed SQL injection in UserRepository tenant query with JPQL @Query</title>   <subtitle>Replaced string-concatenated SQL with parameterized JPQL @Query to prevent injection in tenant lookup.</subtitle>   <facts>     <fact>UserRepository now uses @Query("select u from User u where u.tenantId = :tenantId") instead of string-concatenated SQL.</fact>... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus the filled `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.
- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate lookup), so the “after” numbers include the guard and the “before” numbers do not.
- Title contract hit requires (1) the leading verb to belong to the declared action class, (2) the declared subject tokens that actually occur in the trace to reach coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject tokens the trace never mentions are unreachable labels: they leave the A6b denominator and are counted, while the legacy subject rate is reported alongside.
- Facts informative precision (A5) counts produced facts that name a concrete object (identifier, path, number, status code or quoted value), state an actionable outcome, and are trace-grounded; legacy keyword precision is reported alongside. Concepts recall is the primary concepts signal.
- The golden set is hand-authored and describes this set, not all production traffic.
- Real calls are manual-only and never retried; CI only replays fixtures.
