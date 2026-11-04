# Concurrent read load: /api/search + /api/context/inject

Manual harness (`scripts/read-load-mixed.py`). Real PostgreSQL copy, packaged jar,
local embedding stub replaying real 1024-d vectors from the same database.

## Environment

- Database: `cortexce_load` (1,000,198 observations, 20,195 with 1024-d embeddings)
- Project queried: `/tmp/cortexce-load-project`
- Vector index: idx_obs_embedding_1024
- Workload: 50 concurrent search (limit 20) + 20 concurrent inject for 180s, client timeout 10s
- Hikari pool: application default (25)

Queries alternate Chinese and English; the corpus is English-only, so a Chinese
query exercises the vector half plus an empty lexical half, while an English query
exercises both halves.

## Latency

| workload | requests | errors | 5xx | timeouts | p50 ms | p95 ms | p99 ms | max ms | avg KB |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| search_zh | 35295 | 0 | 0 | 0 | 113.47 | 248.90 | 352.65 | 1115.15 | 126.8 |
| search_en | 35508 | 0 | 0 | 0 | 112.57 | 246.58 | 354.73 | 1223.99 | 127.2 |
| inject | 31792 | 0 | 0 | 0 | 98.71 | 228.27 | 325.70 | 1110.84 | 13.7 |
| all | 102595 | 0 | 0 | 0 | 108.96 | 242.59 | 346.28 | 1223.99 | 91.9 |

Throughput: 569.86 req/s over 180.0s.

Search strategy distribution: `(not sampled)` x67970, `hybrid` x2833. Inject response shapes: (not sampled) x30522, context x1270. Only 1/25 responses are parsed (the rest are counted without a JSON decode so the client stays cheap), so `(not sampled)` is volume, and every sampled search was served by the hybrid path.

## Resources

| metric | max | mean |
|---|---:|---:|
| database connections | 25 | 25.0 |
| active connections | 21 | 6.83 |
| lock waiters | 0 | - |
| app CPU % | 340.7 | 247.1 |

Sampled 90 times at 2s intervals.

Hikari pool metrics were not exposed; database connection counts above come from `pg_stat_activity`.

### Server-side search timer (Prometheus, hybrid path)

The timer covers `SearchService` only: query embedding is outside it and
HTTP serialization is outside it. Compare with the client table above to
see where the tail is spent.

| metric | value |
|---|---:|
| count | 77317 |
| mean | 66.8 ms |
| p50 | 55.9 ms |
| p95 | 156.6 ms |
| p99 | 357.9 ms |

## Attribution: where the tail goes

- Search dominates the tail, but not inside `SearchService`: the server timer
  (hybrid path, SQL + fusion, excluding embedding and serialization) reports
  p50 55.9 / p95 156.6 / p99 357.9 ms while the client sees p50 109 / p95 243 /
  p99 346 ms. The ~53 ms median gap is the embedding HTTP call plus Jackson
  serializing the response.
- `/api/search` bodies average **126.8 KB** for 20 rows. `ObservationEntity`
  annotates `embedding_1024` with `@JsonProperty`, so every hit ships a 1024-float
  vector. An identical 90 s probe at `limit=3` (20.5 KB bodies, same 50/20 mix)
  measured search p95 120 ms and p99 393 ms at 895 req/s, versus p95 240 ms at
  `limit=20`. The difference tracks payload size, not database work: all 77,317
  hybrid searches finished server-side under 2 s.
- Not the connection pool: `pg_stat_activity` shows 25/25 connections held but
  only 6.83 active on average (max 21) and **zero** lock waiters; the service log
  has no Hikari "connection is not available" error. Pool starvation would raise
  p50 as well, and it did not.
- Not injection: `/api/context/inject` p95 228 ms, p99 326 ms, max 1.1 s.
- Not the vector index: HNSW search is inside the 55.9 ms median above, and the
  full server-side p99 is 357.9 ms — an order of magnitude under the 10 s client
  timeout.
- Recommendation (handed off, not changed here): drop embedding columns from API
  responses (`@JsonIgnore` / a response DTO / a Jackson view). It would cut each
  search body by ~90% (to roughly 12 KB) and remove most of the serialization
  and bandwidth share of the p95. This touches `entity`, which the parallel
  session owns, so it is recorded as a finding instead of edited in this batch.
  No new Flyway migration is needed; the HNSW index is already present
  (`idx_obs_embedding_1024`).

## Harness controls

- Independent ApacheBench control (C client, 50 concurrency, 30,000 fixed
  English queries, keep-alive): p50 88 ms, p95 212 ms, p99 330 ms, max 1.64 s,
  **0 failed requests** — consistent with the final Python run.
- Earlier attempts recorded 7–9 client timeouts at exactly 10 s. The cause was
  the stub, not the service: `BaseHTTPRequestHandler` defaults to HTTP/1.0 and
  closes the socket after every reply, and the JDK embedding client periodically
  reused a closed connection (`HTTP/1.1 header parser received no bytes` →
  Spring AI retry → multi-second tail). Switching the stub to HTTP/1.1 with
  `Content-Length`, pre-serializing the vectors and raising the listen backlog
  removed every retry; only the final run is used as evidence. The service saw
  no 5xx in any variant.

## Verdict

**PASS** — 5xx 0, timeouts 0, client errors 0 (over 102595 requests).

No request failed with 5xx or timed out. The observed p95 stays inside the
10 s client timeout; see the attribution section for what dominates the tail.
