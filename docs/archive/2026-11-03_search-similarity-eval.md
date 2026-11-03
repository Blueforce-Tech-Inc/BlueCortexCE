# Semantic relevance floor evaluation

- Date: 2026-11-03
- Embedding model: `BAAI/bge-m3`
- Corpus: 15 relevant pairs; limit 5 per query
- Input: fixture replay

## Operating point

| metric | value |
|---|---|
| missed recall at 0.50 | 0.0% |
| data-chosen threshold | 0.50 |
| recall at chosen threshold | 100.0% |
| precision at chosen threshold | 83.3% |

## Recall / precision curve

| threshold | retrieved | relevant retrieved | recall | precision | missed recall |
|---|---|---|---|---|---|
| 0.30 | 70 | 15/15 | 100.0% | 21.4% | 0.0% |
| 0.35 | 70 | 15/15 | 100.0% | 21.4% | 0.0% |
| 0.40 | 55 | 15/15 | 100.0% | 27.3% | 0.0% |
| 0.45 | 28 | 15/15 | 100.0% | 53.6% | 0.0% |
| 0.50 | 18 | 15/15 | 100.0% | 83.3% | 0.0% |
| 0.55 | 11 | 11/15 | 73.3% | 100.0% | 26.7% |
| 0.60 | 7 | 7/15 | 46.7% | 100.0% | 53.3% |
| 0.65 | 5 | 5/15 | 33.3% | 100.0% | 66.7% |
| 0.70 | 3 | 3/15 | 20.0% | 100.0% | 80.0% |
| 0.75 | 1 | 1/15 | 6.7% | 100.0% | 93.3% |
| 0.80 | 0 | 0/15 | 0.0% | 0.0% | 100.0% |

## Decision rule

- Fixed before the run: choose the highest threshold whose micro recall is within 1.0% of the maximum recall at the lowest swept threshold, so precision is raised without discarding reachable rows.
- The rule is independent of the observed threshold values; it was written before the run and cannot be tuned afterwards.

## Decision

- The data-chosen threshold is `0.50`, identical to the production floor `cortexce.search.min-similarity`; no production change is applied.
- The next stricter step (`0.55`) recalls 73.3% and misses 26.7% of relevant pairs.
- At the production floor the missed recall is 0.0% with precision 83.3%.
