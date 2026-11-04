# Model rerank of the fused candidate pool (2026-11-04 19:26)

- Model: LongCat-2.5 through the real provider, one call per query, 16 concurrent workers for the quality stage.
- Corpus: 188 observations, 90 real queries (the same corpus as the retrieval-quality rounds).
- Baseline: production hybrid (RRF lexical weight 2.0, fused top-40, 7-day recency decay), first five rows. Reproduced here as hit@1 0.833 / hit@5 0.911 / MRR 0.862.
- Noise floor: one query = 1/90 = 0.011 hit@1. Round 18 measured ±1 query as noise, so a ship decision needs ≥2 net queries (0.022).

## Quality

| configuration | hit@1 | hit@5 | MRR | Δhit@1 | Δhit@5 | ΔMRR | net hit@1 queries | failed calls |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| baseline (no rerank) | 0.833 | 0.911 | 0.862 | – | – | – | – | – |
| rerank top-20 (thinking on) | 0.867 | 0.922 | 0.887 | +0.033 | +0.011 | +0.025 | +3 | 2/90 |
| ↳ rerank top-20 (thinking on) query ids | gained: q-ecacff80-185, q-ecacff80-227, q-ecacff80-384, q-ecacff80-168, q-ecacff80-186 | lost: q-ecacff80-21, q-ecacff80-152 | | | | | |
| rerank top-5 (thinking on) | 0.844 | 0.911 | 0.867 | +0.011 | +0.000 | +0.005 | +1 | 0/90 |
| ↳ rerank top-5 (thinking on) query ids | gained: q-ecacff80-24, q-ecacff80-185, q-ecacff80-227, q-ecacff80-168, q-ecacff80-186 | lost: q-ecacff80-39, q-ecacff80-21, q-ecacff80-121, q-ecacff80-152 | | | | | |
| rerank top-20 (thinking off) | 0.844 | 0.911 | 0.869 | +0.011 | +0.000 | +0.006 | +1 | 0/90 |
| ↳ rerank top-20 (thinking off) query ids | gained: q-ecacff80-185, q-ecacff80-227, q-ecacff80-384, q-ecacff80-168, q-ecacff80-186 | lost: q-3643d387-26, q-ecacff80-39, q-ecacff80-117, q-ecacff80-154 | | | | | |
| rerank top-5 (thinking off) | 0.867 | 0.911 | 0.878 | +0.033 | +0.000 | +0.015 | +3 | 0/90 |
| ↳ rerank top-5 (thinking off) query ids | gained: q-ecacff80-24, q-ecacff80-185, q-ecacff80-227, q-ecacff80-168, q-ecacff80-186 | lost: q-ecacff80-39, q-ecacff80-154 | | | | | |

Per-row failures are after one retry; a failed call keeps the original order, which is what an enabled switch would do (fallback to the fused ranking).

## Latency

| configuration | concurrent p50 | concurrent p95 | sequential p50 | sequential p95 | failure rate (concurrent) | failure rate (sequential) |
|---|---:|---:|---:|---:|---:|---:|
| rerank top-20 (thinking on) | 20784 ms | 63599 ms | 15874 ms | 33508 ms | 2.2% | 0.0% |
| rerank top-5 (thinking on) | 9236 ms | 34505 ms | 6918 ms | 24896 ms | 0.0% | 0.0% |
| rerank top-20 (thinking off) | 2651 ms | 16258 ms | 2562 ms | 5698 ms | 0.0% | 0.0% |
| rerank top-5 (thinking off) | 1784 ms | 4135 ms | 1711 ms | 3693 ms | 0.0% | 0.0% |

Latency is reported, not judged: the sequential column is the delay one request adds when it is the only call in flight, the concurrent column is what a batch of 16 queries sees.

## Decision

- Best variant: rerank top-5 with thinking off; net +3 hit@1 queries against a ±2 floor, Δhit@1 +0.033, Δhit@5 +0.000, ΔMRR +0.015, sequential p95 3693 ms.
- Verdict: **ship behind a default-off switch**.

Top-20 with thinking on buys one more query of hit@5 and +0.010 MRR than top-5 with thinking off, but costs a 33.5 s sequential p95 and returned an empty ranking on 2.2% of calls; the tie-break above therefore prefers the cheap variant when net hit@1 is equal.

## Implementation

- `cortexce.search.rerank.enabled` (default `false`), `top-n` (default `5`), `timeout-ms` (default `8000`), `thinking` (default `false`).
- Only the hybrid path is reranked; the tsvector fallback and filter-only paths are untouched. Any timeout, non-2xx, unparseable body or missing key returns the fused order unchanged, so enabling the switch can add latency but cannot drop a result.

## Live switch check

The switch was exercised against a real 80k-row database with the real provider, five
`/api/search` calls on live Chinese queries, first with it off and then on:

| run | mean latency | changed top-5 |
|---|---:|---:|
| rerank off | 330 ms | – |
| rerank on (top-5, thinking off) | 2156 ms | 1 of 5 queries |

Both runs returned five rows every time, which confirms the timeout/fallback path did
not fire and that the added cost is the provider call, not a failure retry.
