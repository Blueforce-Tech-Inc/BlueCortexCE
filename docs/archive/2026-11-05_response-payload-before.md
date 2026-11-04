# Response payload probe (before)

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
| search_zh | 10 | 126.2 | 49.75 | 72.61 | 72.61 | 72.61 | 20.0 | 79.3% | 10/10 |
| search_en | 10 | 126.3 | 48.15 | 128.49 | 128.49 | 128.49 | 20.0 | 79.4% | 10/10 |
| observations | 10 | 123.1 | 245.20 | 293.85 | 293.85 | 293.85 | 20.0 | 79.2% | 10/10 |
| all | 30 | 125.2 | 71.68 | 268.53 | 293.85 | 293.85 | 20.0 | 79.3% | 30/30 |

`vector share of body` is measured by re-serialising every vector array found
in the parsed response (compact separators) and dividing the average array
bytes by the average raw body bytes.
