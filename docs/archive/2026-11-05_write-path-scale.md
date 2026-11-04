# Write path at scale — 2026-11-05

Live, not a gate. Two harnesses, both drop-and-recreate their database:

- `scripts/bench-write-path.py` starts a throwaway database and the real jar with
  no model provider configured (the import path needs none), then measures the
  default 2 MiB body limit, batch size, concurrent import throughput, and a fill
  to 1,000,000 rows followed by vector-bearing imports on that table.
- `scripts/bench-hnsw-build.py` builds `CREATE INDEX ... USING hnsw (m=16,
  ef_construction=64)` over 100k / 300k / 1M distinct 1024-d vectors under
  different `maintenance_work_mem` and worker settings, and probes how expensive
  adding 1,000 more vectors is once the graph is that large.

Raw results: `2026-11-05_write-path-scale.json`.

Machine: the same 16.8 GB Docker VM used by the other 2026-11-05 reports, one
dedicated container (`cortexce-hnsw-bench-pg`, `shm 6g`), app at `-Xmx1g`, JDBC
over `127.0.0.1`. Runs were serialised: nothing else ran while they measured.

**Why this run exists.** The first attempt (same script, before `69d3136`) died
at **~195,000 rows** with `OutOfMemoryError`: `ImportService` loaded every
observation of the touched conversation — three 1024-d vectors plus four JSON
columns — into heap just to deduplicate titles. There were no numbers at any
larger size to report. After the projection fix the fill reaches 1,000,000 rows
on the same 1 GB heap.

> The raw `steps.*.runs[].ms` key in the archived JSON holds **seconds**: the
> harness printed `ms` while `perf_counter` was already seconds. The key is
> renamed `seconds` in this commit; the archived file is kept as produced.

## 1. The default 2 MiB body limit

Increasing batches until the endpoint answers 413, starting from an empty table:

| payload | largest accepted | bytes | bytes/row | first rejection |
|---|---|---|---|---|
| no vector | 800 rows | 517,893 | 646.6 | — (probe stopped at 800) |
| 1024-d vector | 100 rows | 1,134,092 | 11,342.9 | 200 rows → **413** (2,268,090 B) |

The vector row is **17.5×** heavier, so the stock limit admits about **184**
vector rows (`2,097,152 / 11,343`) and rejects the 200-row batch outright. A
client that ships vectors in bulk must either raise
`cortexce.http.max-body-bytes` or keep batches ≤ 100.

## 2. Batch size (64 MiB limit, three repetitions each)

| batch | runs (rows/s) | median | request time |
|---|---|---|---|
| 100 | 333.3 / 500.0 / 1,000.0 | 500 | 0.1–0.3 s |
| 500 | 555.6 / 714.3 / 833.3 | 714 | 0.6–0.9 s |
| 1,000 | 833.3 / 1,111.1 / 909.1 | 909 | 0.9–1.2 s |
| 2,000 | 952.4 / 714.3 / 1,052.6 | 952 | 1.9–2.8 s |

Throughput keeps improving through 2,000 rows; the spread inside a size
(±30%) is larger than the gap between 1,000 and 2,000, so the practical
recommendation is **1,000–2,000 rows per request**.

## 3. Concurrent import (batch 2,000, 20 s window)

| clients | rows imported | wall time | rows/s | errors |
|---|---|---|---|---|
| 1 | 18,000 | 20.2 s | 892.2 | 0 |
| 4 | 32,000 | 24.3 s | **1,318.6** | 0 |
| 8 | 38,000 | 53.7 s | 707.6 | 0 |

Four concurrent clients beat one by **48%** with zero errors; eight fall back
**below one client** — the requests queue behind the shared write transaction
and the fixed pool rather than overlapping. Do not fan out beyond four writers.

## 4. Fill to one million rows

| | before (`69d3136` parent) | after |
|---|---|---|
| rows reached | ~195,000, then `OutOfMemoryError` | **1,000,570** |
| throughput | — (crashed) | **914.6 rows/s** (900,000 rows in 984.0 s) |
| heap | 1 GB | 1 GB |

The fill carried no vectors, so it measures the heap path plus duplicate
detection against a growing table, not HNSW upkeep.

## 5. Vector-bearing imports on the million-row table

| batch | rows/s | request time |
|---|---|---|
| 50 | 125.0 / 125.0 | 0.4 s |
| 100 | 166.7 / 200.0 | 0.5–0.6 s |
| 200 | 200.0 / 200.0 | 1.0 s |

**Limitation, stated rather than smoothed over:** only the ~700 vector-bearing
rows are in the HNSW graph at this point (`pg_relation_size('idx_obs_embedding_1024')`
= **7,088 kB**), so this number is the Java/JSON cost of a 1024-float row plus a
small-graph insert — not a million-node graph. The SQL probe below measures the
graph side directly.

## 6. HNSW build parameters

Distinct clustered vectors (256 centres + a 20,001-vector noise pool, both
advancing every row), `m=16`, `ef_construction=64`:

| rows | load | setting | build | index size | bytes/row |
|---|---|---|---|---|---|
| 100,000 | 4.1 s | 1 GB / 2 workers | **36.9 s** | 781 MB | 8,192 |
| 100,000 | 4.1 s | 64 MB (stock) / 0 | **199.6 s** (5.4×) | 781 MB | 8,192 |
| 300,000 | 11.3 s | 2 GB / 2 | 70.5 s | 2,344 MB | 8,192 |
| 300,000 | 11.3 s | 4 GB / 2 | 72.1 s (+2.3%) | 2,344 MB | 8,192 |
| 1,000,000 | 33.4 s | 4 GB / 2 | **801.6 s** | 7,813 MB | 8,192 |

Two facts drive the recommendation: the index is a flat **8,192 bytes per
vector row**, and `maintenance_work_mem` only buys speed until it covers the
graph — at 100k the stock 64 MB pays **5.4×**, while at 300k doubling 2 GB → 4 GB
changes nothing (the 2.3 GB graph already fits in 2 GB). The 1M build at 4 GB
sits *below* the 7.8 GB index, and its per-row cost (0.802 ms) is **3.4×** the
300k rate (0.235 ms): spilling, not a size effect.

## 7. HNSW upkeep: inserting into an existing graph

Two 1,000-row inserts into the freshly built index, `synchronous_commit`
untouched (durable, like an app write):

| graph size | insert time | rows/s | per row |
|---|---|---|---|
| 100,000 | 3.139 / 3.120 s | 318.6 / 320.5 | ~3.1 ms |
| 300,000 | 5.201 / 4.899 s | 192.3 / 204.1 | ~5.0 ms |
| 1,000,000 | 26.13 / 18.733 s | **38.3 / 53.4** | ~19–26 ms |

Upkeep scales worse than linearly in practice: 10× the rows costs ~8× the
per-row time between 100k and 1M. This is the number that makes "load first,
build the index once" the right operational answer: 1M rows land in ~18 min at
~900 rows/s vectorless, and the index then builds in 13.4 min; feeding the same
million rows into a live million-node graph at ~45 rows/s would take ~6 hours.

## 8. Operational parameters

| scale | index | `maintenance_work_mem` | workers | measured | vector load |
|---|---|---|---|---|---|
| 100k | 781 MB | **1 GB** | 2 | 36.9 s | ~320 rows/s into a live graph |
| 1M | 7.8 GB | **8 GB** | 2 | 801.6 s at 4 GB (spilling) | ~45 rows/s into a live graph |
| 3M | 23.4 GB | **24 GB** | 2 | **not measured** | derived only |

3M is **not** measured: the rows alone need 12.3 GB of vectors and the index
23.4 GB (≈36 GB), against a 16.8 GB VM; building it would either be OOM-killed or
measured in a spill state that says nothing about a properly sized host. The 3M
line is a capacity formula from the two measured constants (8,192 B/row index,
4,096 B/row vectors) plus the requirement that `maintenance_work_mem` cover the
graph, and is labelled as such — do not read it as a timing.

**Bulk ingest recipe** (from the measurements, not from theory):

1. Insert rows with no vector: 900–1,300 rows/s, batches of 1,000–2,000, at most
   four concurrent clients.
2. Create the HNSW index once with `maintenance_work_mem` ≥ index size
   (1 GB @100k, 8 GB @1M, 24 GB @3M) and 2 workers.
3. If vectors must arrive later, treat the backfill as a batch job and expect
   tens of rows per second once the graph is large — size the window accordingly.

## 9. What this does not cover

- The 1M-node *application* path (JSON parse + graph insert together) was not
  measured end to end: it needs a million vector rows, which the write bench's
  heap and this VM's memory do not allow. Section 5 and section 7 bracket it
  (~5 ms/row parse + ~20–26 ms/row graph at 1M).
- Query latency at these sizes is covered by
  `2026-11-05_retrieval-concurrent-read-after.md`.
- No new migration or configuration default changed in this batch; the numbers
  only size the advisory parameters.
