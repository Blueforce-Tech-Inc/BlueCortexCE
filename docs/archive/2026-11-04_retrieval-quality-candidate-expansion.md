# Retrieval quality: real session-history queries

- Date: 2026-11-04 23:07:25
- Corpus: `backend/src/test/resources/eval/retrieval-corpus-real.json` (188 observations, 90 queries)
- Embedding model: BAAI/bge-m3 (1024 dimensions), real provider calls
- Database: disposable cortexce_retrieval_1793804638 on claude-mem-test-pg:5433
- Ground truth: temporal attribution (the observations produced while the assistant answered the real prompt); rationale stored per query.

## hit@1 / hit@5 / MRR

| path | hit@1 | hit@5 | MRR | empty results |
|---|---:|---:|---:|---:|
| vector | 0.700 | 0.889 | 0.767 | 0 |
| lexical (V24 token coverage) | 0.756 | 0.844 | 0.785 | 0 |
| lexical_ts_rank | 0.744 | 0.867 | 0.788 | 0 |
| english tsvector (replaced by V24) | 0.000 | 0.000 | 0.000 | 90 |
| hybrid (production RRF) | 0.778 | 0.900 | 0.821 | 0 |
| additive w0.25 (rejected) | 0.733 | 0.889 | 0.797 | 0 |
| additive w0.75 (rejected) | 0.767 | 0.889 | 0.813 | 0 |
| additive w1.0 (rejected) | 0.778 | 0.889 | 0.817 | 0 |
| hybrid_no_floor | 0.767 | 0.889 | 0.815 | 0 |
| rrf | 0.722 | 0.911 | 0.794 | 0 |
| identifier_or | 0.633 | 0.767 | 0.683 | 7 |
| identifier_or_rrf | 0.689 | 0.856 | 0.756 | 0 |
| vector_head120 | 0.622 | 0.822 | 0.691 | 0 |
| vector_tail120 | 0.422 | 0.678 | 0.521 | 0 |
| hybrid + recency 7 d half-life | 0.833 | 0.911 | 0.866 | 0 |
| hybrid + recency 30 d half-life | 0.811 | 0.900 | 0.849 | 0 |
| hybrid + recency 90 d half-life | 0.789 | 0.900 | 0.836 | 0 |
| hybrid + recency 365 d half-life | 0.778 | 0.900 | 0.822 | 0 |
| hybrid + near-duplicate merge | 0.789 | 0.922 | 0.842 | 0 |
| merge + recency 30 d | 0.822 | 0.922 | 0.869 | 0 |
| decay 7d + intent/kind boost | 0.700 | 0.911 | 0.770 | 0 |
| synonym expansion + decay 7d | 0.844 | 0.911 | 0.872 | 0 |
| synonym + intent + decay 7d | 0.700 | 0.911 | 0.772 | 0 |
| synonym (lexical+vector) + decay 7d | 0.844 | 0.911 | 0.872 | 0 |
| synonym both + merge + decay 7d | 0.867 | 0.933 | 0.894 | 0 |
| decay 7d + body embedding rerank | 0.689 | 0.878 | 0.766 | 0 |
| decay 7d + title embedding rerank | 0.444 | 0.622 | 0.531 | 0 |
| decay 7d + title RRF blend | 0.667 | 0.856 | 0.743 | 0 |
| pool_40 | 0.778 | 0.900 | 0.821 | 0 |
| pool_100 | 0.778 | 0.900 | 0.822 | 0 |
| pool_200 | 0.778 | 0.900 | 0.822 | 0 |
| pool_40_decay7d | 0.833 | 0.911 | 0.866 | 0 |
| pool_100_decay7d | 0.833 | 0.911 | 0.867 | 0 |
| pool_200_decay7d | 0.833 | 0.911 | 0.868 | 0 |
| vecdeep_100 | 0.778 | 0.900 | 0.821 | 0 |
| vecdeep_200 | 0.778 | 0.900 | 0.821 | 0 |
| vecdeep_400 | 0.778 | 0.900 | 0.821 | 0 |
| vecdeep_100_decay7d | 0.833 | 0.911 | 0.866 | 0 |
| vecdeep_200_decay7d | 0.833 | 0.911 | 0.866 | 0 |
| vecdeep_400_decay7d | 0.833 | 0.911 | 0.866 | 0 |
| cluster_session_0.5 | 0.833 | 0.911 | 0.862 | 0 |
| cluster_session_0.75 | 0.833 | 0.911 | 0.862 | 0 |
| cluster_session_1.0 | 0.344 | 0.889 | 0.556 | 0 |
| cluster_file_0.5 | 0.833 | 0.911 | 0.862 | 0 |
| cluster_file_0.75 | 0.833 | 0.911 | 0.862 | 0 |
| cluster_file_1.0 | 0.711 | 0.889 | 0.785 | 0 |
| cluster_both_0.5 | 0.833 | 0.911 | 0.862 | 0 |
| cluster_both_0.75 | 0.833 | 0.911 | 0.862 | 0 |
| cluster_both_1.0 | 0.311 | 0.878 | 0.526 | 0 |
| hybrid_queryident_decay7d | 0.822 | 0.900 | 0.851 | 0 |
| hybrid_queryident_cluster_decay7d | 0.822 | 0.911 | 0.851 | 0 |

## Candidate pool depth (round 18)

| pool | mean rows fetched | hit@1 (fusion) | hit@5 (fusion) | MRR (fusion) | hit@1 (+7d) | hit@5 (+7d) | MRR (+7d) |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 40 | 35.5 | 0.778 | 0.900 | 0.821 | 0.833 | 0.911 | 0.866 |
| 100 | 73.0 | 0.778 | 0.900 | 0.822 | 0.833 | 0.911 | 0.867 |
| 200 | 91.0 | 0.778 | 0.900 | 0.822 | 0.833 | 0.911 | 0.868 |

## Vector-only candidate expansion (round 20)

Semantic list widened; lexical list and fused cut stay at production depth 40.

| vector depth | mean rows | hit@1 (fusion) | hit@5 (fusion) | MRR (fusion) | hit@1 (+7d) | hit@5 (+7d) | MRR (+7d) | mean ms | p95 ms |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | 35.5 | 0.778 | 0.900 | 0.821 | 0.833 | 0.911 | 0.866 | 73.2 | 113.0 |
| 200 | 35.5 | 0.778 | 0.900 | 0.821 | 0.833 | 0.911 | 0.866 | 77.2 | 119.7 |
| 400 | 35.5 | 0.778 | 0.900 | 0.821 | 0.833 | 0.911 | 0.866 | 75.7 | 115.7 |

## Language split (round 16 contrast set)

| language | queries | hybrid hit@1 | hybrid hit@5 | hybrid MRR | merge+recency hit@1 |
|---|---:|---:|---:|---:|---:|
| mixed | 83 | 0.795 | 0.904 | 0.835 | 0.831 |
| zh | 7 | 0.571 | 0.857 | 0.667 | 0.714 |

## Diagnosis

Corpus split: mixed 83, zh 7. V24 lexical path reaches hit@1 0.756, hit@5 0.844, MRR 0.785 with 0 empty results; the English tsvector it replaced was empty for 90 of 90 queries (hit@5 0.000). Production hybrid (weighted RRF, lexical weight 2.0) reaches hit@1 0.778, hit@5 0.900, MRR 0.821 against pure vector 0.700 / 0.889 / 0.767. Additive score fusion froze hit@5 at 0.889 for every weight (MRR 0.797 / 0.813 / 0.817 at w=0.25/0.75/1.0) because cosine (0.6-0.8) swamps coverage (0.05-0.2) — rank fusion removes that scale mismatch. Ordering the same tokens with ts_rank_cd gives MRR 0.788 versus 0.785 for coverage. Identifier-OR ranking scores 0.683 MRR. Recency rerank on the fused pool: best half-life 7d gives hit@1 0.833, hit@5 0.911, MRR 0.866; the 30-day variant alone is 0.811 / 0.900 / 0.849. Near-duplicate merge (newest of each normalized title) gives hit@1 0.789, hit@5 0.922, MRR 0.842; it hid a relevant row for 76 queries. Merge then 30-day decay gives 0.822 / 0.922 / 0.869. Round 17 starts from the 7-day-decayed pool (0.833 / 0.911 / 0.866). Intent split is 19 write / 38 edit / 33 neutral; intent-aware kind boost (1.1x) gives 0.700 / 0.911 / 0.770. Bilingual synonym expansion fired on 62 of 90 queries and gives 0.844 / 0.911 / 0.872; with the intent boost 0.700 / 0.911 / 0.772. Expanding the vector half too (lexical+vector) gives 0.844 / 0.911 / 0.872; adding the near-duplicate merge 0.867 / 0.933 / 0.894. Embedding rerank of the fused top-20: body cosine 0.689 / 0.878 / 0.766, title cosine 0.444 / 0.622 / 0.531, title blend 0.667 / 0.856 / 0.743.  Round 18 candidate-pool sweep: pool 40: 0.778 / 0.900 / 0.821 fused, 0.833 / 0.911 / 0.866 after 7-day decay; pool 100: 0.778 / 0.900 / 0.822 fused, 0.833 / 0.911 / 0.867 after 7-day decay; pool 200: 0.778 / 0.900 / 0.822 fused, 0.833 / 0.911 / 0.868 after 7-day decay. A 90-query corpus moves by one query per 0.011 hit@1, so any delta below 0.02 is inside one-query noise. Round 19 cluster expansion from the fused pool plus 7-day decay: session x0.5: 0.833 / 0.911 / 0.862; session x0.75: 0.833 / 0.911 / 0.862; session x1.0: 0.344 / 0.889 / 0.556; file x0.5: 0.833 / 0.911 / 0.862; file x0.75: 0.833 / 0.911 / 0.862; file x1.0: 0.711 / 0.889 / 0.785; both x0.5: 0.833 / 0.911 / 0.862; both x0.75: 0.833 / 0.911 / 0.862; both x1.0: 0.311 / 0.878 / 0.526. Query-side identifier expansion fired on 90 of 90 queries (up to 3 tokens from the last 30 days) and gives 0.822 / 0.900 / 0.851; combined with the 0.5 cluster rule 0.822 / 0.911 / 0.851.  Round 20 vector-only expansion: 8 production misses, 6 of them had a relevant row outside the fused top-40; 13 of 90 queries had any relevant row outside it (22 rows in total). Vector list reachable relevant rows at depths 100: 186/188, 200: 188/188, 400: 188/188. Widening only the vector half: 100: hit@1 0.778, hit@5 0.900, MRR 0.821, after 7-day decay 0.833 / 0.911 / 0.866, mean 73.2 ms; 200: hit@1 0.778, hit@5 0.900, MRR 0.821, after 7-day decay 0.833 / 0.911 / 0.866, mean 77.2 ms; 400: hit@1 0.778, hit@5 0.900, MRR 0.821, after 7-day decay 0.833 / 0.911 / 0.866, mean 75.7 ms. A 90-query corpus moves by one query per 0.011, so a delta below 0.02 is inside one-query noise; client wall time includes a fixed psql process cost, so differences under a few milliseconds are not attributable to the scan.

## Round 20 production misses

| query | relevant | relevant outside pool | vector reachable @max | recovered by vecdeep |
|---|---:|---:|---:|---:|
| q-ecacff80-273 | 2 | 2 | 2 | no |
| q-ecacff80-345 | 2 | 2 | 2 | no |
| q-ecacff80-384 | 2 | 1 | 2 | no |
| q-ecacff80-156 | 1 | 0 | 1 | no |
| q-ecacff80-158 | 1 | 0 | 1 | no |
| q-ecacff80-181 | 1 | 1 | 1 | no |
| q-ecacff80-183 | 1 | 1 | 1 | no |
| q-ecacff80-197 | 2 | 2 | 2 | no |

## Per-query first relevant rank

| query | relevant | vector | vector-head120 | vector-tail120 | lexical | lexical-ts-rank | english-tsvector | add-w0.25 | hybrid (RRF w2) | add-w0.75 | add-w1.0 | rrf-k60 | identifier-or | decay30d | merge | merge+decay | intent | synonym7d | body-rerank | title-rerank | title-blend |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| q-ecacff80-6 | 3 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-13 | 1 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 1 | 1 | 1 |
| q-ecacff80-142 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 18 | 1 |
| q-ecacff80-9 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 3 | 1 |
| q-38f778cd-2 | 1 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-3643d387-3 | 3 | 1 | 1 | 1 | 3 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 1 | 1 | 1 |
| q-3643d387-23 | 3 | 4 | 4 | 4 | 3 | 4 | - | 2 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | edit | 1 | 4 | 1 | 1 |
| q-863b3b8d-2 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-863b3b8d-4 | 4 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 1 | 1 | 1 |
| q-ecacff80-12 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 2 | 1 |
| q-ecacff80-16 | 1 | 1 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 3 | 1 | 1 | 1 | write | 1 | 1 | 1 | 1 |
| q-ecacff80-584 | 2 | - | - | - | - | - | - | - | 4 | - | - | 4 | - | 2 | 4 | 2 | neutral | 1 | 2 | 24 | 4 |
| q-ecacff80-11 | 1 | 2 | 1 | - | 1 | 1 | - | 2 | 1 | 2 | 2 | 2 | 2 | 1 | 1 | 1 | neutral | 1 | 2 | 6 | 2 |
| q-3643d387-6 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-3643d387-26 | 4 | - | - | - | 4 | 4 | - | - | 4 | - | - | 5 | - | 1 | 5 | 1 | edit | 1 | 14 | 14 | 1 |
| q-863b3b8d-5 | 1 | 3 | 3 | 3 | 1 | 2 | - | 2 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 3 | 6 | 2 |
| q-863b3b8d-7 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-ecacff80-39 | 2 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 1 | 24 | 2 |
| q-ecacff80-17 | 1 | 4 | 4 | 2 | 1 | 1 | - | 2 | 1 | 2 | 1 | 2 | 3 | 1 | 1 | 1 | neutral | 1 | 4 | 35 | - |
| q-ecacff80-587 | 1 | 1 | 5 | - | 1 | 2 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 10 | 3 |
| q-ecacff80-18 | 1 | 4 | - | 2 | 1 | 1 | - | 3 | 1 | 3 | 3 | 2 | 1 | 1 | 1 | 1 | edit | 1 | 4 | 1 | 1 |
| q-3643d387-14 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-3643d387-31 | 1 | 1 | 3 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 5 | 1 | 1 | 1 | edit | 1 | 1 | 3 | 1 |
| q-863b3b8d-9 | 1 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-ecacff80-111 | 4 | - | - | 1 | 1 | 1 | - | - | 1 | - | - | 3 | 1 | 1 | 3 | 2 | edit | 1 | 9 | 1 | 1 |
| q-ecacff80-21 | 1 | 3 | - | 1 | 1 | 1 | - | 2 | 1 | 2 | 2 | 2 | - | 1 | 1 | 1 | edit | 1 | 3 | 1 | 1 |
| q-ecacff80-19 | 1 | 1 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 2 | 1 |
| q-3643d387-28 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-863b3b8d-11 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 2 | 1 |
| q-ecacff80-117 | 2 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-23 | 1 | 2 | 1 | 2 | 1 | 1 | - | 2 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | edit | 1 | 2 | 22 | 5 |
| q-ecacff80-20 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 1 | 1 | 1 |
| q-863b3b8d-13 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 2 | 1 | 1 | 1 | neutral | 1 | 1 | 5 | 2 |
| q-ecacff80-154 | 2 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 1 | 9 | 1 |
| q-ecacff80-35 | 2 | 1 | 1 | 2 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 15 | 2 |
| q-ecacff80-24 | 2 | 3 | 4 | 3 | 2 | 4 | - | 3 | 2 | 3 | 3 | 3 | 1 | 2 | 2 | 2 | edit | 2 | 3 | 6 | 1 |
| q-863b3b8d-16 | 1 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 7 | 1 |
| q-ecacff80-185 | 2 | 1 | - | 1 | 3 | 1 | - | 1 | 3 | 2 | 3 | 2 | - | 3 | 3 | 3 | write | 3 | 1 | 1 | 1 |
| q-ecacff80-36 | 2 | 1 | 1 | 2 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 18 | 2 |
| q-ecacff80-26 | 1 | 1 | 1 | 4 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 5 | 1 |
| q-863b3b8d-18 | 4 | 2 | 2 | 1 | 1 | 1 | - | 2 | 1 | 2 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 2 | 1 | 1 |
| q-ecacff80-191 | 4 | 1 | 5 | 3 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-38 | 1 | 1 | 2 | - | - | 5 | - | 1 | 4 | 1 | 2 | 2 | - | 4 | 3 | 3 | write | 3 | 1 | 5 | 2 |
| q-ecacff80-50 | 1 | 3 | - | 2 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 3 | 15 | 3 |
| q-863b3b8d-24 | 4 | 4 | 4 | 4 | - | - | - | 5 | 6 | 5 | 5 | 5 | - | 6 | 1 | 1 | neutral | 5 | 4 | 2 | - |
| q-ecacff80-227 | 2 | 1 | 1 | - | - | - | - | 1 | 3 | 1 | 1 | 2 | 1 | 3 | 2 | 2 | write | 2 | 1 | 1 | 2 |
| q-ecacff80-44 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-55 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-863b3b8d-25 | 2 | 1 | 1 | 5 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-ecacff80-273 | 2 | 1 | 1 | - | - | - | - | 1 | - | 1 | 1 | 5 | - | - | - | - | write | - | - | - | - |
| q-ecacff80-109 | 2 | - | - | 1 | 1 | 1 | - | - | 4 | - | - | 4 | 4 | 1 | 3 | 1 | edit | 1 | 11 | 1 | 1 |
| q-ecacff80-98 | 1 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 5 | 1 |
| q-ecacff80-305 | 4 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-135 | 3 | 1 | 2 | - | 1 | 2 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 1 | 21 | 5 |
| q-ecacff80-116 | 4 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 3 | 1 |
| q-ecacff80-345 | 2 | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | edit | - | - | - | - |
| q-ecacff80-138 | 1 | 2 | 4 | 2 | 1 | 1 | - | 1 | 1 | 1 | 1 | 2 | 1 | 1 | 1 | 1 | edit | 1 | 2 | 38 | - |
| q-ecacff80-121 | 1 | 1 | 1 | 3 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-367 | 2 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-ecacff80-149 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 3 | 1 |
| q-ecacff80-384 | 2 | - | - | - | - | - | - | - | 19 | - | - | - | - | 18 | 8 | 7 | write | 13 | 15 | 15 | - |
| q-ecacff80-152 | 2 | 3 | 5 | 2 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 2 | 22 | 1 |
| q-ecacff80-131 | 2 | 1 | 1 | 3 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 2 | 1 | 1 | 1 | edit | 1 | 1 | 9 | 1 |
| q-ecacff80-416 | 3 | 5 | 1 | 1 | 3 | 3 | - | 5 | 5 | 5 | 5 | - | 1 | 3 | 2 | 1 | edit | 1 | 5 | 7 | 1 |
| q-ecacff80-156 | 1 | - | - | - | 3 | - | - | - | 12 | - | - | - | 3 | 9 | 7 | 6 | neutral | 9 | 26 | 13 | - |
| q-ecacff80-136 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 2 | 1 | 1 | 1 | edit | 1 | 1 | 6 | 2 |
| q-ecacff80-419 | 2 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 3 | 1 |
| q-ecacff80-158 | 1 | - | - | - | - | - | - | - | 11 | - | - | - | 5 | 9 | 5 | 5 | write | 9 | 17 | 23 | - |
| q-ecacff80-137 | 1 | 1 | 1 | 2 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 2 | 1 | 1 | 1 | write | 1 | 1 | 27 | - |
| q-ecacff80-539 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 6 | 2 |
| q-ecacff80-164 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-ecacff80-141 | 2 | 1 | 1 | 2 | 1 | 3 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 28 | - |
| q-ecacff80-590 | 2 | 1 | 1 | 5 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-167 | 2 | 1 | 1 | 4 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | write | 1 | 1 | 1 | 1 |
| q-ecacff80-146 | 2 | 1 | 1 | 3 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 3 | 1 |
| q-ecacff80-168 | 4 | 3 | - | - | 5 | 1 | - | 3 | 3 | 3 | 3 | 3 | 1 | 3 | 1 | 1 | neutral | 3 | 2 | 1 | 3 |
| q-ecacff80-150 | 2 | 1 | 3 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 3 | 1 | 1 | 1 | edit | 1 | 1 | 10 | 1 |
| q-ecacff80-177 | 2 | 1 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-558 | 1 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-ecacff80-180 | 2 | 1 | 1 | 1 | 1 | 3 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-570 | 1 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-ecacff80-181 | 1 | 3 | - | - | - | 1 | - | 3 | - | 4 | 4 | - | - | - | - | - | neutral | - | - | - | - |
| q-ecacff80-575 | 1 | 1 | 1 | 2 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 3 | 1 |
| q-ecacff80-183 | 1 | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | edit | - | - | - | - |
| q-ecacff80-576 | 1 | 1 | 3 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 5 | 1 |
| q-ecacff80-184 | 4 | 2 | 1 | - | 1 | 2 | - | 2 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 2 | 13 | 4 |
| q-831ea6d5-5 | 2 | 1 | 1 | 1 | - | - | - | 1 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-186 | 4 | 1 | 1 | - | - | - | - | 1 | 3 | 1 | 1 | 3 | 1 | 3 | 2 | 2 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-197 | 2 | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | write | - | - | - | - |
| q-ecacff80-200 | 4 | 1 | 1 | - | - | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 2 | 2 | write | 1 | 1 | 1 | 1 |
