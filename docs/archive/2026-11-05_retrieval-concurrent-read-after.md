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
| search_zh | 34667 | 0 | 0 | 0 | 113.59 | 264.19 | 383.09 | 1125.35 | 24.9 |
| search_en | 34717 | 0 | 0 | 0 | 113.38 | 264.52 | 381.07 | 1567.09 | 24.9 |
| inject | 26579 | 0 | 0 | 0 | 112.89 | 300.64 | 461.71 | 1193.37 | 13.7 |
| all | 95963 | 0 | 0 | 0 | 113.35 | 274.81 | 401.70 | 1567.09 | 21.8 |

Throughput: 532.83 req/s over 180.1s.

Search strategy distribution: `(not sampled)` x66610, `hybrid` x2774. Inject response shapes: (not sampled) x25518, context x1061.

## Resources

| metric | max | mean |
|---|---:|---:|
| database connections | 25 | 25.0 |
| active connections | 22 | 7.33 |
| lock waiters | 0 | - |
| app CPU % | 271.5 | 174.4 |

Sampled 90 times at 2s intervals.

Hikari pool metrics were not exposed; database connection counts above come from `pg_stat_activity`.

### Server-side search timer (Prometheus, hybrid path)

The timer covers `SearchService` only: query embedding is outside it and
HTTP serialization is outside it. Compare with the client table above to
see where the tail is spent.

| metric | value |
|---|---:|
| count | 75645 |
| mean | 90.4 ms |
| p50 | 89.5 ms |
| p95 | 223.7 ms |
| p99 | 357.9 ms |

## A/B control: the same database in the same hour

The 180 s run above is not comparable with the 2026-11-04 baseline: the host
and PostgreSQL cache state had drifted, and the 2026-11-04 run measured p95
242 ms while the first pre-change run in this session measured 332 ms. The
harness therefore gained `--jar`, and the pre-change jar was rebuilt from
`12f9ec8` into `/tmp/cortexce-before.jar` so both builds could be alternated
against one database, one embedding stub and one clock:

```
A1 before(jar 12f9ec8)  B1 after(@JsonIgnore)  A2 before  B2 after  A3 before  B3 after  A4 before
```

Each run: 50 concurrent search (limit 20) + 20 concurrent inject, 120 s,
20 s warmup, same 1,000,198-row database. A1 is the cold-start run (nothing
had touched the HNSW index yet in this session) and is reported but excluded
from the medians.

| run | jar | p50 ms | p95 ms | p99 ms | req/s | search p95 ms | inject p95 ms | search KB |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| A1 | `12f9ec8` | 125.17 | 332.31 | 517.34 | 468.7 | 333.9 | 328.6 | 125.9 |
| B1 | `@JsonIgnore` | 106.94 | 260.27 | 396.69 | 563.5 | 249.4 | 288.1 | 24.9 |
| A2 | `12f9ec8` | 108.62 | 250.21 | 368.76 | 563.2 | 250.5 | 249.1 | 125.8 |
| B2 | `@JsonIgnore` | 101.28 | 231.59 | 333.77 | 607.2 | 225.0 | 250.6 | 24.9 |
| A3 | `12f9ec8` | 125.66 | 285.87 | 397.76 | 495.6 | 290.3 | 273.4 | 125.9 |
| B3 | `@JsonIgnore` | 108.79 | 259.41 | 370.53 | 561.6 | 249.8 | 282.8 | 24.9 |
| A4 | `12f9ec8` | 113.41 | 282.01 | 404.77 | 532.5 | 277.6 | 291.8 | 125.8 |

Medians (A2–A4 vs B1–B3; `inject` never carried vectors and is the control):

| metric | before | after | delta |
|---|---:|---:|---:|
| all p50 | 113.4 | 106.9 | **−5.7%** |
| all p95 | 282.0 | 259.4 | **−8.0%** |
| all p99 | 397.8 | 370.5 | **−6.9%** |
| throughput | 532.5 req/s | 563.5 req/s | **+5.8%** |
| `/api/search` p50 (zh / en) | 114.7 / 114.2 | 106.4 / 105.8 | **−7.2% / −7.3%** |
| `/api/search` p95 (zh / en) | 278.0 / 277.2 | 250.6 / 246.9 | **−9.9% / −10.9%** |
| `/api/search` body | 125.8 KB | 24.9 KB | **−80.2%** |
| `/api/context/inject` p50 | 110.4 | 111.0 | +0.5% (control) |
| `/api/context/inject` p95 | 273.4 | 282.8 | +3.4% (control, inside the ±5% run noise) |

The inject control is flat at both percentiles (its p95 moves inside the run
noise), which is what rules out host drift as the explanation for the search
delta: the same process, database and client conditions that left inject
unchanged cut search p50 by ~7% and p95 by ~10%.

## Attribution after the change

- The serialization share is gone. Client p50 minus the server-side
  `SearchService` timer fell from 52–58 ms to **17 ms**, and the p95 gap from
  ~90 ms to **~35 ms**. That gap is embedding + Jackson + transfer.
- The remaining tail is below the client: `SearchService` p95 alone is
  179–268 ms before and 201–224 ms after. Hibernate still selects and
  hydrates `embedding_768/1024/1536` for every row in the page even though
  they are no longer serialised; the fix removes the outbound copy, not the
  database copy. That is the next lever, and it needs a read projection /
  DTO on the list path (not shipped here: it changes the repository read
  path and the item's scope was the response entity).
- The throughput rise (+5.8% overall, with inject p50/p95 unchanged) comes
  from spending less CPU and less client time per search at the same
  concurrency.
- On a real network the payload cut matters more than on localhost: a
  126 KB page takes ~100 ms at 10 Mbit/s, the 25 KB page ~20 ms.

## Verdict

**PASS** — 5xx 0, timeouts 0, client errors 0 (over 95963 requests in the
180 s run and 45,049 over the A/B series).

No request failed with 5xx or timed out. Search p50 fell ~7% and p95 ~10%
with the inject control flat, and the body is 80% smaller; the p95 tail
itself is database hydration, not response serialization.
