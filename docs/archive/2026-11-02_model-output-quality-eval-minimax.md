# Model output quality evaluation

- Date: 2026-11-02
- Provider: anthropic, model `MiniMax-M3`, base URL `https://api.minimaxi.com/anthropic`
- Samples: 12; real calls used: 12 (cap 20); temperature: 0.0
- Parser: production `XmlParser` on the raw answer; skip detection via `<skip>`.

## Aggregate

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 83% |
| skip accuracy | 83% |
|   skip confusion (TP/FP/FN/TN) | 0/0/2/10 |
| format errors | 0 |
| call failures | 0 |
| title full-hit rate | 20% |
| type accuracy | 90% |
| facts precision / recall | 65% / 90% |
| concepts precision / recall | 52% / 80% |

## Per sample

| id | category | expected | actual | contract | title | type | facts P/R | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|
| bugfix-null-pointer | bug-fix | observation | observation | pass | 0% | match | 33%/100% | 33%/50% | 3795 |
| bugfix-cache-race | bug-fix | observation | observation | pass | 0% | match | 67%/100% | 50%/100% | 2201 |
| bugfix-pagination-off-by-one | bug-fix | observation | observation | pass | 50% | match | 100%/100% | 67%/100% | 7396 |
| refactor-validation-helper | refactor | observation | observation | pass | 50% | match | 100%/100% | 67%/100% | 2858 |
| refactor-split-service | refactor | observation | observation | pass | 50% | match | 33%/100% | 67%/100% | 4984 |
| test-timezone-regression | test-add | observation | observation | pass | 50% | MISS | 25%/50% | 33%/50% | 2757 |
| config-connection-pool | config-change | observation | observation | pass | 0% | match | 60%/100% | 33%/50% | 2679 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | match | 100%/50% | 33%/50% | 2276 |
| security-log-redaction | security-fix | observation | observation | pass | 100% | match | 67%/100% | 67%/100% | 2234 |
| docs-api-errors | docs-update | observation | observation | pass | 0% | match | 67%/100% | 67%/100% | 1913 |
| skip-read-for-question | skip-low-value | skip | observation | FAIL | 0% | MISS | 0%/0% | 0%/0% | 8634 |
| skip-file-listing | skip-low-value | skip | observation | FAIL | 0% | MISS | 0%/0% | 0%/0% | 1708 |

## Problems

- bugfix-null-pointer: title coverage 0% (expected keywords [NullPointerException, PaymentService])
- bugfix-null-pointer: concepts recall 50% (expected [problem-solution, pattern], got [what-changed, problem-solution, how-it-works])
- bugfix-cache-race: title coverage 0% (expected keywords [race, UserCache])
- bugfix-pagination-off-by-one: title coverage 50% (expected keywords [pagination, off-by-one])
- refactor-validation-helper: title coverage 50% (expected keywords [SignupValidator, validation])
- refactor-split-service: title coverage 50% (expected keywords [InvoiceService, split])
- test-timezone-regression: title coverage 50% (expected keywords [timezone, DailyReport])
- test-timezone-regression: type 'bugfix' != expected 'feature'
- test-timezone-regression: facts recall 50% (expected [Asia/Shanghai, Clock.fixed], got [DailyReportTest#reportUsesBusinessTimezone was added in src/test/java/com/acme/reporting/DailyReportTest.java., The test fixes the clock at 2026-11-01T23:30:00Z using ZoneOffset.UTC., The test asserts report.date() equals LocalDate.of(2026, 11, 2) for the Asia/Shanghai business timezone., The new test passes and covers a DST/timezone regression.])
- test-timezone-regression: concepts recall 50% (expected [pattern, gotcha], got [problem-solution, what-changed, gotcha])
- config-connection-pool: title coverage 0% (expected keywords [connection pool, DB_POOL_SIZE])
- config-connection-pool: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, how-it-works, pattern])
- security-sql-injection: facts recall 50% (expected [UserSearchDao, prepared], got [src/main/java/com/acme/admin/UserSearchDao.java replaces concatenated SQL with `SELECT * FROM users WHERE name LIKE ?`., UserSearchDao now calls `jdbc.query(sql, (rs, i) -> map(rs), "%" + keyword + "%")` with the keyword bound as a query parameter., UserSearchDaoTest#keywordCannotInject was added and passes with a quote payload.])
- security-sql-injection: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, how-it-works])
- docs-api-errors: title coverage 0% (expected keywords [error codes, docs])
- skip-read-for-question: expected <skip> but produced an observation
- skip-file-listing: expected <skip> but produced an observation

## Raw answers behind problems

### bugfix-null-pointer

```text
<observation>   <type>bugfix</type>   <title>Missing payments now raise PaymentNotFoundException</title>   <subtitle>PaymentService lookup failures now expose the missing order ID through a domain-specific exception.</subtitle>   <facts>     <fact>PaymentService.java now maps repository results with Payment::getAmount before unwrapping.</fact>     <fact>PaymentService throws PaymentNotFoundExcepti... [truncated]
```

### bugfix-cache-race

```text
<observation>   <type>bugfix</type>   <title>Prevented concurrent cache misses from overwriting entries</title>   <subtitle>UserCache now uses atomic computeIfAbsent loading, and concurrency testing passes across 16 threads for 100 iterations.</subtitle>   <facts>     <fact>src/main/java/com/acme/cache/UserCache.java replaced separate cache.get, load, and cache.put operations with cache.computeIfA... [truncated]
```

### bugfix-pagination-off-by-one

```text
<observation>   <type>bugfix</type>   <title>Fixed pagination offset for one-based page numbers</title>   <subtitle>Page 1 now returns the first results instead of skipping the newest orders.</subtitle>   <facts>     <fact>src/main/java/com/acme/order/OrderQuery.java calculates offset with Math.max(0, page - 1) * size.</fact>     <fact>Pagination treats page numbers as 1-based.</fact>     <fact>Or... [truncated]
```

### refactor-validation-helper

```text
<observation>   <type>refactor</type>   <title>Centralized signup request validation</title>   <subtitle>SignupController now delegates email and name validation to SignupValidator without changing behavior.</subtitle>   <facts>     <fact>src/main/java/com/acme/signup/SignupController.java replaced inline email and name checks with SignupValidator.isValid(request).</fact>     <fact>SignupValidator... [truncated]
```

### refactor-split-service

```text
<observation>   <type>refactor</type>   <title>InvoiceService decomposed into focused billing collaborators</title>   <subtitle>Tax, PDF rendering, and email responsibilities moved from InvoiceService into three injected components without changing the public API.</subtitle>   <facts>     <fact>src/main/java/com/acme/billing/InvoiceService.java now accepts TaxCalculator, InvoiceRenderer, and Invoi... [truncated]
```

### test-timezone-regression

```text
<observation>   <type>bugfix</type>   <title>Added regression coverage for business-timezone report dates</title>   <subtitle>DailyReportTest verifies UTC instants map to the expected Asia/Shanghai business date.</subtitle>   <facts>     <fact>DailyReportTest#reportUsesBusinessTimezone was added in src/test/java/com/acme/reporting/DailyReportTest.java.</fact>     <fact>The test fixes the clock at ... [truncated]
```

### config-connection-pool

```text
<observation>   <type>change</type>   <title>Gateway database pool size made configurable</title>   <subtitle>HikariCP now defaults to 25 connections and accepts DB_POOL_SIZE overrides.</subtitle>   <facts>     <fact>src/main/resources/application.yml sets maximum-pool-size to ${DB_POOL_SIZE:25}.</fact>     <fact>Gateway database connection pool defaults to 25 maximum connections.</fact>     <fact... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>Parameterized user search prevents SQL injection</title>   <subtitle>UserSearchDao now binds keyword search values through JDBC parameters instead of concatenating SQL strings.</subtitle>   <facts>     <fact>src/main/java/com/acme/admin/UserSearchDao.java replaces concatenated SQL with `SELECT * FROM users WHERE name LIKE ?`.</fact>     <fact>UserSearch... [truncated]
```

### docs-api-errors

```text
<observation>   <type>change</type>   <title>Documented API error retry behavior</title>   <subtitle>Added a concise error-code reference distinguishing retryable rate limits from non-retryable idempotency conflicts.</subtitle>   <facts>     <fact>docs/errors.md defines HTTP 429 as rate limited and retryable after honoring the Retry-After header.</fact>     <fact>docs/errors.md defines HTTP 409 as... [truncated]
```

### skip-read-for-question

```text
<observation>   <type>discovery</type>   <title>Button component renders styled variant buttons</title>   <subtitle>Button.tsx contains a 42-line React button component supporting visual variants.</subtitle>   <facts>     <fact>src/components/Button.tsx renders a styled button element.</fact>     <fact>Button.tsx accepts variant props for visual presentation.</fact>   </facts>   <narrative>The exi... [truncated]
```

### skip-file-listing

```text
<observation>   <type>discovery</type>   <title>Scripts directory contains build and deployment utilities</title>   <subtitle>The scripts directory includes build.sh, deploy.sh, and documentation alongside additional files.</subtitle>   <facts>     <fact>/repo/scripts/build.sh provides a build utility.</fact>     <fact>/repo/scripts/deploy.sh provides a deployment utility.</fact>     <fact>/repo/s... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus `prompts/observation.txt` filled with each sample's tool name, cwd, input and output.
- Each sample costs exactly one chat call; embeddings are not exercised, so this measures extraction quality only, not retrieval quality.
- `facts`/`concepts` precision counts actual items that mention any expected keyword; recall counts expected keywords found in the concatenated list. Keyword matching is case-insensitive substring matching, so paraphrase misses count as errors.
- The golden set is small (12 samples) and authored by hand; numbers describe this set, not the full distribution of real tool-use traffic.
- Real calls are manual-only: `scripts/eval-model-quality.sh` refuses to run under CI and enforces the `EVAL_MAX_CALLS` cap.
