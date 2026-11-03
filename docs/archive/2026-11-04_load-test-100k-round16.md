# Load test: 100000 observations, search latency, index evidence, queue throughput

- Date: 2026-11-04 05:06:05
- Database: cortexce_load on claude-mem-test-pg:5433 (disposable, Flyway-migrated)
- Service: packaged jar on http://127.0.0.1:37778
- Workload: 100 sequential requests per search path; pages of 20 rows
- Model: local OpenAI-compatible stub; the query path therefore measures the hybrid SQL shape (pgvector + tsvector) without provider latency

## Search latency by table size

| rows | path | strategy | p50 ms | p95 ms | p99 ms | max ms |
|---:|---|---|---:|---:|---:|---:|
| 1000 | recent | recent | 5.71 | 10.65 | 12.55 | 28.21 |
| 1000 | deep_page | recent | 4.23 | 10.2 | 12.47 | 17.24 |
| 1000 | type_filter | filter | 4.81 | 9.39 | 13.95 | 15.81 |
| 1000 | query | hybrid | 6.05 | 12.61 | 21.96 | 23.2 |
| 5000 | recent | recent | 3.44 | 6.16 | 13.78 | 15.93 |
| 5000 | deep_page | recent | 3.1 | 6.62 | 8.7 | 13.72 |
| 5000 | type_filter | filter | 3.55 | 11.43 | 19.01 | 22.19 |
| 5000 | query | hybrid | 5.95 | 11.08 | 17.73 | 27.55 |
| 10000 | recent | recent | 2.02 | 3.39 | 4.68 | 7.45 |
| 10000 | deep_page | recent | 1.72 | 2.98 | 5.2 | 5.96 |
| 10000 | type_filter | filter | 1.65 | 3.16 | 4.89 | 21.15 |
| 10000 | query | hybrid | 4.19 | 8.2 | 43.39 | 46.42 |
| 100000 | recent | recent | 2.14 | 3.27 | 7.69 | 7.97 |
| 100000 | deep_page | recent | 2.07 | 3.46 | 7.53 | 7.55 |
| 100000 | type_filter | filter | 2.57 | 6.58 | 6.94 | 7.32 |
| 100000 | query | hybrid | 4.48 | 6.82 | 8.59 | 8.68 |

## Index evidence (100000 rows; V23 composites + V25 scope/type)

| query | index | index used | with index ms | without index ms | plan without index |
|---|---|---|---:|---:|---|
| scope_recent | idx_observations_scope_created | idx_observations_scope_created | 0.04 | 22.73 | Limit -> Gather Merge -> Sort -> Seq Scan |
| type_recent | idx_observations_type_created | idx_observations_type_created | 0.08 | 25.66 | Limit -> Sort -> Seq Scan |
| project_recent | idx_observations_project_created | idx_observations_project_created | 0.05 | 20.08 | Limit -> Gather Merge -> Sort -> Seq Scan |
| type_filter | idx_observations_scope_type_created | ux_memory_scopes_type_sha256, idx_observations_scope_type_created | 0.14 | 17.33 | Limit -> Limit -> Seq Scan -> Gather Merge -> Sort -> Seq Scan |

## Queue throughput (ingest -> observation persisted)

| metric | value |
|---|---:|
| events posted | 200 |
| accepted | 200 |
| enqueue seconds | 1.82 |
| enqueue events/s | 109.81 |
| enqueue p95 ms | 546.95 |
| queue drained | True |
| total seconds | 4.02 |
| persisted rows | 200 |
| persisted events/s | 49.69 |
| duplicate content hashes | 0 |
| rows stuck in processing | 0 |

## Degradation knee (p95 > 2x the 1k-row baseline)

| path | strategy | p95 at 1k | 2x threshold | first rows over 2x |
|---|---|---:|---:|---:|
| recent | recent | 10.65 | 21.3 | not crossed |
| deep_page | recent | 10.2 | 20.4 | not crossed |
| type_filter | filter | 9.39 | 18.78 | not crossed |
| query | hybrid | 12.61 | 25.22 | not crossed |

## Capacity conclusion

- No measured path crossed the 2x p95 threshold between 1000 and 100000 rows; every path stayed inside 8 ms p95.
- Queue: 200 of 200 events persisted (49.69 rows/s), 0 duplicate hashes, 0 rows left in processing.
- Highest observed p95 across all paths at 100000 rows: 6.82 ms (query).

