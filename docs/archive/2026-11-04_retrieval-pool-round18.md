# Retrieval candidate-pool sweep — round 18

Date: 2026-11-04
Status: **rejected** — the wider pool does not move the hit rate outside noise.

## Question

Round 17 found that 5 of the 15 remaining hit@1 misses had their relevant rows
outside the fused top-40 candidates. Production fetches `limit * 2` rows
(`SearchService` passes `limit * 2`, i.e. 40 for the default 20-result page).
This round asks whether widening that pool to 100 or 200 can turn those misses
into hits, at an acceptable latency cost.

## Method

`scripts/eval-retrieval-quality.py` gained a `--pool-depths` sweep. For each of
the 90 real-history queries on the 188-observation corpus (real BAAI/bge-m3
embeddings, 1024 dimensions, `CURRENT_CUTOFF` prevents future leakage) it runs
the production `hybridSearchScored` SQL three times — `LIMIT 40`, `LIMIT 100`,
`LIMIT 200` — and applies the same 7-day recency rerank production applies.

Noise floor: one query out of 90 is 0.011 hit@1, so deltas below 0.02 are
inside one-query noise.

## Result — quality

| pool | mean rows fetched | hit@1 (fusion) | hit@5 (fusion) | MRR (fusion) | hit@1 (+7d decay) | hit@5 (+7d decay) | MRR (+7d decay) |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 40  | 35.5 | 0.778 | 0.900 | 0.822 | 0.833 | 0.911 | 0.866 |
| 100 | 73.0 | 0.778 | 0.900 | 0.823 | 0.833 | 0.911 | 0.867 |
| 200 | 91.0 | 0.778 | 0.900 | 0.823 | 0.833 | 0.911 | 0.868 |

- hit@1 and hit@5 are **identical** at all three depths, with and without decay.
- MRR moves by +0.001 (pool 100) and +0.002 (pool 200) after decay — an order of
  magnitude below the 0.011 one-query noise floor.
- Pool 200 fetch saturates at 91 rows because the 188-row corpus has only 91
  fused candidates; at production scale the pool would not be saturating, but
  the ranking of the top rows is unchanged by the deeper tail.

## Result — latency at 1,000,198 rows

Measured with the production SQL against the round-17 `cortexce_load` database
(1,000,198 rows; 20,195 of them carry 1024-dim embeddings — the bulk rows from
the capacity run were inserted without embeddings). 45 timed iterations per
depth after 5 warm-ups, one psql session:

| pool | p50 | p95 | p99 | max |
|---:|---:|---:|---:|---:|
| 40  | 1.73 ms | 2.00 ms | 2.38 ms | 2.38 ms |
| 100 | 1.68 ms | 2.11 ms | 2.22 ms | 2.22 ms |
| 200 | 1.91 ms | 2.32 ms | 3.38 ms | 3.38 ms |

Latency is comfortably inside the 50 ms budget. Caveat: the vector half only
sees 20,195 embedded rows at this scale, so the measurement bounds the lexical
half (which does scan all 1M rows) and the fusion/LIMIT cost, not a 1M-entry
HNSW traversal. Even if the HNSW half cost 10× the observed value, p95 stays
below the budget.

## Decision

**Do not widen the pool.** The latency criterion passes (p95 2.3 ms at pool 200
vs the 50 ms budget), but the hit-rate improvement is inside noise: `0.833 → 0.833`
hit@1, `0.911 → 0.911` hit@5, `0.866 → 0.868` MRR after decay. Shipping a
5× larger fetch for a delta indistinguishable from one query is unjustified.

The round-17 root cause stands: the missing relevant records are outside *any*
reasonable fused pool, so the fix has to improve candidate generation
(e.g. a second retrieval shape), not deepen the existing list. That remains
open work.

## Reproduction

```bash
python3 scripts/eval-retrieval-quality.py \
    --pool-depths 40,100,200 \
    --report /tmp/pool.md --metrics-out /tmp/pool.json
```
