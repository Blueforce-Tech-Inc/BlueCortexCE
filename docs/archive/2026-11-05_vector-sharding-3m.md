# 3M-scale vector path: shard-by-scope vs one graph — 2026-11-05

Question from P6: at 1M, one HNSW graph already spills
(`hnsw graph no longer fits into maintenance_work_mem after 898,013 tuples`) and
needs 7.6 GB at `m=16, ef_construction=64`. What is the cheapest way to keep
3M rows queryable without a 32 GB-plus index rebuild every time?

This report measures the **layout**, not the application: 3,000,000 rows are
split into 8 tables (`vec_p0 … vec_p7`, 375,000 rows each), each with its own
local HNSW index, then queried with the same construction the rows use.

```
python3 scripts/bench-vector-sharding.py            # 3M / 8 shards, ~15 min
python3 scripts/bench-vector-sharding.py --rows 1500000 --shards 4
```

Raw report: `2026-11-05_vector-sharding-3m.json`.

## Measured (shared 16 GB macOS host, container `pgvector/pgvector:pg16`)

| | Value |
|---|---|
| rows / layout | 3,000,000 in 8 × 375,000 |
| load | **84.1 s** (≈35,700 rows/s, `synchronous_commit=off`) |
| one shard build | **89.7–106.8 s** (mean 97.7 s) at `maintenance_work_mem=4GB`, 2 workers |
| all eight builds | **781.9 s** (13.0 min) |
| one shard index | **3,072,008,192 B** (2.86 GiB, exactly 8,192 B/row) |
| all eight indexes | **24,576,065,536 B** (22.9 GiB) |
| spill | **none** — each 375k graph (2.86 GiB) fits under 4 GB |

Query latency, timed with `clock_timestamp()` inside the server, 200 queries per
table, `hnsw.ef_search=40`:

| Table | p50 | p95 | max |
|---|---|---|---|
| vec_p0 | 76.6 ms | 156.2 ms | 583.5 ms |
| vec_p1 | 72.4 ms | 118.4 ms | 148.3 ms |
| vec_p2 | 67.4 ms | 101.1 ms | 143.4 ms |
| vec_p3 | 66.2 ms | 108.0 ms | 142.0 ms |
| vec_p4 | 100.3 ms | 337.4 ms | 907.7 ms |
| vec_p5 | 105.9 ms | 153.4 ms | 224.0 ms |
| vec_p6 | 94.0 ms | 166.7 ms | 469.6 ms |
| vec_p7 | 43.3 ms | 63.7 ms | 85.6 ms |
| **mean** | **78.1 ms** | **150.6 ms** | **907.7 ms** |
| **single 1M** (`cortexce_ops_1m`, same probe) | **83.7 ms** | **176.0 ms** | **261.6 ms** |

## What the numbers say

**Build cost per row drops ~2×.** 1M in one graph: 550.4 s (drill) / 801.6 s
(bench) = 550–802 µs per row, 7.58 GB, spills after 898k tuples at 4 GB. 3M in
eight graphs: 782 s = **261 µs per row**, no spill. The reason is mechanical:
a 375k graph is 2.86 GB, under the 4 GB budget, so the whole build stays in
`maintenance_work_mem`; a 1M+ graph outgrows it and starts merging on disk.

**Latency is flat, not better.** HNSW search is ~logarithmic, so an 8× smaller
graph is not 8× faster: p50 78 ms across shards vs 84 ms for 1M, p95 150 vs
176 ms. The spread (43–106 ms p50) is host noise, not shard size — the container
was shared with unrelated benchmarks (max 908 ms on `vec_p4`).

**Cross-scope search pays fan-out.** A query that must look at every project
becomes 8 index searches plus a merge. Sequentially that is ~8 × 78 ms ≈ 620 ms;
in parallel it is bounded by the slowest shard (~150–340 ms p95). A query scoped
to one project stays at one search — this is the case sharding is designed for.

## Decision

| | One table, one graph | Shard/partition per scope |
|---|---|---|
| Build at 3M | one ~23 GB index; ~40–90 min, spills at any realistic `maintenance_work_mem`, all-or-nothing | 8 × 90–110 s; each fits in 4 GB, rebuild one shard independently |
| Failure blast radius | whole index invalid until rebuilt | one shard; the other seven keep serving |
| Single-project query | one lookup | one lookup — same |
| Cross-project query | one lookup | fan-out over N, merge in app/DB |
| Operational cost | 1 index to monitor | N indexes, routing table, N-way retention/backfill |
| Backup/restore | unchanged `pg_dump` of one database | unchanged (one database, more tables) |

Recommendation: keep the single table up to **~1.5M rows** (index ≤ ~12 GB,
build ≤ ~15 min). At **3M+**, partition the observations by project scope
(`PARTITION BY LIST (project_path)` with one HNSW index per partition) and route
on `project`. Do not build a single 23 GB graph on a 16–32 GB host. Cross-project
vector search should either fan out with a hard deadline or stay on the text
(`tsvector`) path.

## Minimal verification, and what it does not cover

The harness above is the smallest repeatable check: it needs no application
change, runs in ~15 minutes, and prints build time, index bytes, and in-server
query percentiles. It does **not** test:

- the routing layer (`project → partition`), or the API contract for a
  cross-project search — those are application changes with their own tests;
- native `PARTITION BY LIST` DDL and per-partition index creation (the benchmark
  uses independent tables, which have the same index geometry but different DDL);
- retention/backfill across partitions.

Adopt partitioning in a follow-up only if the 3M projection holds; the numbers
here are sufficient to decide *whether* to, not *how* to wire it into
`SearchService`.
