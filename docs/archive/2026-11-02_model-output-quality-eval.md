# Model output quality evaluation

- Date: 2026-11-02
- Provider: OpenAI-compatible, model `LongCat-2.5`, base URL `https://api.longcat.chat/openai`
- Samples: 12; real calls used: 12 (cap 20); temperature: 0.0
- Parser: production `XmlParser` on the raw answer; skip detection via `<skip>`.

## Aggregate

| metric | value |
|---|---|
| contract pass rate (observation parsed or skip when expected) | 92% |
| skip accuracy | 92% |
|   skip confusion (TP/FP/FN/TN) | 2/1/0/9 |
| format errors | 0 |
| call failures | 0 |
| title full-hit rate | 44% |
| type accuracy | 100% |
| facts precision / recall | 54% / 94% |
| concepts precision / recall | 57% / 83% |

## Per sample

| id | category | expected | actual | contract | title | type | facts P/R | concepts P/R | ms |
|---|---|---|---|---|---|---|---|---|---|
| bugfix-null-pointer | bug-fix | observation | observation | pass | 50% | match | 50%/100% | 67%/100% | 11087 |
| bugfix-cache-race | bug-fix | observation | observation | pass | 100% | match | 75%/100% | 67%/100% | 15219 |
| bugfix-pagination-off-by-one | bug-fix | observation | observation | pass | 50% | match | 60%/100% | 67%/100% | 9892 |
| refactor-validation-helper | refactor | observation | observation | pass | 100% | match | 67%/100% | 67%/100% | 15777 |
| refactor-split-service | refactor | observation | observation | pass | 100% | match | 40%/100% | 67%/100% | 8817 |
| test-timezone-regression | test-add | observation | skip | FAIL | - | - | - | - | 7372 |
| config-connection-pool | config-change | observation | observation | pass | 0% | match | 25%/100% | 50%/50% | 18132 |
| security-sql-injection | security-fix | observation | observation | pass | 100% | match | 67%/50% | 33%/50% | 7490 |
| security-log-redaction | security-fix | observation | observation | pass | 50% | match | 50%/100% | 67%/100% | 7448 |
| docs-api-errors | docs-update | observation | observation | pass | 50% | match | 50%/100% | 33%/50% | 8969 |
| skip-read-for-question | skip-low-value | skip | skip | pass | - | - | - | - | 4072 |
| skip-file-listing | skip-low-value | skip | skip | pass | - | - | - | - | 20619 |

## Problems

- bugfix-null-pointer: title coverage 50% (expected keywords [NullPointerException, PaymentService])
- bugfix-pagination-off-by-one: title coverage 50% (expected keywords [pagination, off-by-one])
- test-timezone-regression: expected an observation but model skipped
- config-connection-pool: title coverage 0% (expected keywords [connection pool, DB_POOL_SIZE])
- config-connection-pool: concepts recall 50% (expected [what-changed, trade-off], got [what-changed, pattern])
- security-sql-injection: facts recall 50% (expected [UserSearchDao, prepared], got [UserSearchDao.java previously built SQL via string concatenation: "SELECT * FROM users WHERE name LIKE '%" + keyword + "%'", Query now uses placeholder "SELECT * FROM users WHERE name LIKE ?" with jdbc.query(sql, rowMapper, "%" + keyword + "%"), New test UserSearchDaoTest#keywordCannotInject verifies a quote payload is treated as data, not SQL syntax])
- security-sql-injection: concepts recall 50% (expected [problem-solution, gotcha], got [problem-solution, what-changed, pattern])
- security-log-redaction: title coverage 50% (expected keywords [password, redact])
- docs-api-errors: title coverage 50% (expected keywords [error codes, docs])
- docs-api-errors: concepts recall 50% (expected [why-it-exists, what-changed], got [what-changed, pattern, how-it-works])

## Raw answers behind problems

### bugfix-null-pointer

```text
<observation>   <type>bugfix</type>   <title>PaymentService throws PaymentNotFoundException for missing orders</title>   <subtitle>Replaced generic NoSuchElementException with domain-specific PaymentNotFoundException when orderId lookup fails.</subtitle>   <facts>     <fact>PaymentService.java line changed from repository.findByOrderId(orderId).orElseThrow().getAmount() to use .map(Payment::getAmo... [truncated]
```

### bugfix-pagination-off-by-one

```text
<observation>   <type>bugfix</type>   <title>Pagination offset corrected to use 1-based page numbers</title>   <subtitle>Fixed OrderQuery so page 1 returns first results instead of skipping the initial page.</subtitle>   <facts>     <fact>File modified: src/main/java/com/acme/order/OrderQuery.java</fact>     <fact>Offset calculation changed from `page * size` to `Math.max(0, page - 1) * size`</fac... [truncated]
```

### test-timezone-regression

```text
<skip>Single regression test addition for timezone handling; test passes immediately with no system changes or new capability revealed</skip>
```

### config-connection-pool

```text
```xml <observation>   <type>change</type>   <title>Gateway Hikari pool size increased to 25 and made configurable</title>   <subtitle>Database connection pool default raised from 10 to 25 with environment variable override support.</subtitle>   <facts>     <fact>Gateway Hikari maximum-pool-size changed from hardcoded 10 to 25</fact>     <fact>Pool size now overridable via DB_POOL_SIZE environment... [truncated]
```

### security-sql-injection

```text
<observation>   <type>bugfix</type>   <title>SQL injection fixed in UserSearchDao via parameterized query</title>   <subtitle>Replaced string-concatenated LIKE clause with a JDBC placeholder and bound keyword parameter.</subtitle>   <facts>     <fact>UserSearchDao.java previously built SQL via string concatenation: "SELECT * FROM users WHERE name LIKE '%" + keyword + "%'"</fact>     <fact>Query no... [truncated]
```

### security-log-redaction

```text
<observation>   <type>bugfix</type>   <title>Removed raw password logging from LoginController</title>   <subtitle>Login attempt logging no longer exposes plaintext passwords; a redacted marker is used instead.</subtitle>   <facts>     <fact>src/main/java/com/acme/auth/LoginController.java previously logged raw passwords via log.info("login attempt password={}", password)</fact>     <fact>Logging ... [truncated]
```

### docs-api-errors

```text
<observation>   <type>change</type>   <title>Added error codes documentation</title>   <subtitle>Created docs/errors.md with retry guidance for 429 and 409 HTTP status codes.</subtitle>   <facts>     <fact>New file docs/errors.md added to the public-api repository</fact>     <fact>Documents 429 rate limited error with retry allowed honouring Retry-After header</fact>     <fact>Documents 409 idempo... [truncated]
```

## Method and limitations

- The prompt is the production `prompts/init.txt` system message plus `prompts/observation.txt` filled with each sample's tool name, cwd, input and output.
- Each sample costs exactly one chat call; embeddings are not exercised, so this measures extraction quality only, not retrieval quality.
- `facts`/`concepts` precision counts actual items that mention any expected keyword; recall counts expected keywords found in the concatenated list. Keyword matching is case-insensitive substring matching, so paraphrase misses count as errors.
- The golden set is small (12 samples) and authored by hand; numbers describe this set, not the full distribution of real tool-use traffic.
- Real calls are manual-only: `scripts/eval-model-quality.sh` refuses to run under CI and enforces the `EVAL_MAX_CALLS` cap.
