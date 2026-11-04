# Response payload probe (after)

Manual harness (`scripts/probe-response-payload.py`). Packaged jar, real
PostgreSQL database `cortexce_load`, local embedding stub replaying real
1024-d vectors. Requests are issued sequentially so the numbers describe the
payload and the endpoint, not client concurrency.

## Environment

- Observations: 1,000,198 (20,195 with 1024-d embeddings)
- Project: `/tmp/cortexce-load-project`
- Search page size: 20, repeats per kind: 10
- Database-side vector text size: avg 6,021 B, max 6,075 B per `embedding_1024::text`

## Results

| endpoint | requests | avg KB | p50 ms | p95 ms | p99 ms | max ms | avg items | vector share of body | responses with vectors |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| search_zh | 10 | 24.9 | 64.81 | 155.07 | 155.07 | 155.07 | 20.0 | 0.0% | 0/10 |
| search_en | 10 | 24.9 | 57.30 | 123.12 | 123.12 | 123.12 | 20.0 | 0.0% | 0/10 |
| observations | 10 | 24.5 | 316.30 | 398.08 | 398.08 | 398.08 | 20.0 | 0.0% | 0/10 |
| all | 30 | 24.7 | 85.43 | 372.14 | 398.08 | 398.08 | 20.0 | 0.0% | 0/30 |

`vector share of body` is measured by re-serialising every vector array found
in the parsed response (compact separators) and dividing the average array
bytes by the average raw body bytes.

Latency here is sequential with 10 samples per kind, so p95/p99 is effectively
the slowest sample and moves with unrelated host load (this run overlapped a
surefire run from another checkout). The before/after latency claim comes from
the 180 s concurrent harness (`scripts/read-load-mixed.py`), not from this
table; the unambiguous part is the body size and the vector share:
**125.2 KB → 24.7 KB (−80.3%)** and **79.3% → 0.0%** vector bytes.
