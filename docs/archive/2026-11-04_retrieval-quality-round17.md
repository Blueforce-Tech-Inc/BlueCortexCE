# Retrieval quality: real session-history queries

- Date: 2026-11-04 11:06:30
- Corpus: `backend/src/test/resources/eval/retrieval-corpus-real.json` (188 observations, 90 queries)
- Embedding model: BAAI/bge-m3 (1024 dimensions), real provider calls
- Database: disposable cortexce_retrieval_1793761471 on claude-mem-test-pg:5433
- Ground truth: temporal attribution (the observations produced while the assistant answered the real prompt); rationale stored per query.

## hit@1 / hit@5 / MRR

| path | hit@1 | hit@5 | MRR | empty results |
|---|---:|---:|---:|---:|
| vector | 0.689 | 0.889 | 0.761 | 0 |
| lexical (V24 token coverage) | 0.756 | 0.844 | 0.785 | 0 |
| lexical_ts_rank | 0.744 | 0.867 | 0.788 | 0 |
| english tsvector (replaced by V24) | 0.000 | 0.000 | 0.000 | 90 |
| hybrid (production RRF) | 0.767 | 0.900 | 0.817 | 0 |
| additive w0.25 (rejected) | 0.733 | 0.889 | 0.796 | 0 |
| additive w0.75 (rejected) | 0.767 | 0.889 | 0.813 | 0 |
| additive w1.0 (rejected) | 0.778 | 0.889 | 0.817 | 0 |
| hybrid_no_floor | 0.767 | 0.889 | 0.815 | 0 |
| rrf | 0.733 | 0.911 | 0.799 | 0 |
| identifier_or | 0.633 | 0.767 | 0.683 | 7 |
| identifier_or_rrf | 0.689 | 0.856 | 0.756 | 0 |
| vector_head120 | 0.611 | 0.822 | 0.685 | 0 |
| vector_tail120 | 0.422 | 0.678 | 0.521 | 0 |
| hybrid + recency 7 d half-life | 0.833 | 0.911 | 0.866 | 0 |
| hybrid + recency 30 d half-life | 0.811 | 0.900 | 0.849 | 0 |
| hybrid + recency 90 d half-life | 0.778 | 0.900 | 0.831 | 0 |
| hybrid + recency 365 d half-life | 0.767 | 0.900 | 0.817 | 0 |
| hybrid + near-duplicate merge | 0.789 | 0.922 | 0.843 | 0 |
| merge + recency 30 d | 0.822 | 0.922 | 0.869 | 0 |
| decay 7d + intent/kind boost | 0.700 | 0.911 | 0.770 | 0 |
| synonym expansion + decay 7d | 0.844 | 0.911 | 0.872 | 0 |
| synonym + intent + decay 7d | 0.700 | 0.911 | 0.771 | 0 |
| synonym (lexical+vector) + decay 7d | 0.844 | 0.911 | 0.872 | 0 |
| synonym both + merge + decay 7d | 0.867 | 0.933 | 0.894 | 0 |
| decay 7d + body embedding rerank | 0.678 | 0.878 | 0.761 | 0 |
| decay 7d + title embedding rerank | 0.456 | 0.622 | 0.536 | 0 |
| decay 7d + title RRF blend | 0.667 | 0.856 | 0.744 | 0 |

## Language split (round 16 contrast set)

| language | queries | hybrid hit@1 | hybrid hit@5 | hybrid MRR | merge+recency hit@1 |
|---|---:|---:|---:|---:|---:|
| mixed | 83 | 0.795 | 0.904 | 0.835 | 0.831 |
| zh | 7 | 0.429 | 0.857 | 0.607 | 0.714 |

## Diagnosis

Corpus split: mixed 83, zh 7. V24 lexical path reaches hit@1 0.756, hit@5 0.844, MRR 0.785 with 0 empty results; the English tsvector it replaced was empty for 90 of 90 queries (hit@5 0.000). Production hybrid (weighted RRF, lexical weight 2.0) reaches hit@1 0.767, hit@5 0.900, MRR 0.817 against pure vector 0.689 / 0.889 / 0.761. Additive score fusion froze hit@5 at 0.889 for every weight (MRR 0.796 / 0.813 / 0.817 at w=0.25/0.75/1.0) because cosine (0.6-0.8) swamps coverage (0.05-0.2) — rank fusion removes that scale mismatch. Ordering the same tokens with ts_rank_cd gives MRR 0.788 versus 0.785 for coverage. Identifier-OR ranking scores 0.683 MRR. Recency rerank on the fused pool: best half-life 7d gives hit@1 0.833, hit@5 0.911, MRR 0.866; the 30-day variant alone is 0.811 / 0.900 / 0.849. Near-duplicate merge (newest of each normalized title) gives hit@1 0.789, hit@5 0.922, MRR 0.843; it hid a relevant row for 76 queries. Merge then 30-day decay gives 0.822 / 0.922 / 0.869. Round 17 starts from the 7-day-decayed pool (0.833 / 0.911 / 0.866). Intent split is 19 write / 38 edit / 33 neutral; intent-aware kind boost (1.1x) gives 0.700 / 0.911 / 0.770. Bilingual synonym expansion fired on 62 of 90 queries and gives 0.844 / 0.911 / 0.872; with the intent boost 0.700 / 0.911 / 0.771. Expanding the vector half too (lexical+vector) gives 0.844 / 0.911 / 0.872; adding the near-duplicate merge 0.867 / 0.933 / 0.894. Embedding rerank of the fused top-20: body cosine 0.678 / 0.878 / 0.761, title cosine 0.456 / 0.622 / 0.536, title blend 0.667 / 0.856 / 0.744.

## Per-query first relevant rank

| query | relevant | vector | vector-head120 | vector-tail120 | lexical | lexical-ts-rank | english-tsvector | add-w0.25 | hybrid (RRF w2) | add-w0.75 | add-w1.0 | rrf-k60 | identifier-or | decay30d | merge | merge+decay | intent | synonym7d | body-rerank | title-rerank | title-blend |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| q-ecacff80-6 | 3 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-13 | 1 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 1 | 1 | 1 |
| q-ecacff80-142 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 18 | 1 |
| q-ecacff80-9 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-38f778cd-2 | 1 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-3643d387-3 | 3 | 1 | 1 | 1 | 3 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 1 | 1 | 1 |
| q-3643d387-23 | 3 | 4 | 4 | 4 | 3 | 4 | - | 2 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | edit | 1 | 4 | 1 | 1 |
| q-863b3b8d-2 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-863b3b8d-4 | 4 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 1 | 1 | 1 |
| q-ecacff80-12 | 3 | 1 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 2 | 1 |
| q-ecacff80-16 | 1 | 1 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 3 | 1 | 1 | 1 | write | 1 | 1 | 1 | 1 |
| q-ecacff80-584 | 2 | - | - | - | - | - | - | - | 3 | - | - | 4 | - | 2 | 3 | 2 | neutral | 1 | 2 | 24 | 4 |
| q-ecacff80-11 | 1 | 2 | 1 | - | 1 | 1 | - | 2 | 1 | 2 | 2 | 2 | 2 | 1 | 1 | 1 | neutral | 1 | 2 | 5 | 2 |
| q-3643d387-6 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | neutral | 1 | 1 | 4 | 1 |
| q-3643d387-26 | 4 | - | - | - | 4 | 4 | - | - | 4 | - | - | 5 | - | 1 | 5 | 1 | edit | 1 | 14 | 14 | 1 |
| q-863b3b8d-5 | 1 | 3 | 3 | 3 | 1 | 2 | - | 2 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 3 | 6 | 2 |
| q-863b3b8d-7 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-ecacff80-39 | 2 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 1 | 24 | 2 |
| q-ecacff80-17 | 1 | 4 | 4 | 2 | 1 | 1 | - | 2 | 1 | 2 | 1 | 2 | 3 | 1 | 1 | 1 | neutral | 1 | 4 | 35 | - |
| q-ecacff80-587 | 1 | 1 | 5 | - | 1 | 2 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 10 | 3 |
| q-ecacff80-18 | 1 | 4 | - | 2 | 1 | 1 | - | 4 | 1 | 3 | 3 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 4 | 2 | 1 |
| q-3643d387-14 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-3643d387-31 | 1 | 1 | 3 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 5 | 1 | 1 | 1 | edit | 1 | 1 | 3 | 1 |
| q-863b3b8d-9 | 1 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-ecacff80-111 | 4 | - | - | 1 | 1 | 1 | - | - | 1 | - | - | 3 | 1 | 1 | 3 | 2 | edit | 1 | 9 | 1 | 1 |
| q-ecacff80-21 | 1 | 3 | - | 1 | 1 | 1 | - | 2 | 1 | 2 | 2 | 2 | - | 1 | 1 | 1 | edit | 1 | 3 | 1 | 1 |
| q-ecacff80-19 | 1 | 1 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-3643d387-28 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-863b3b8d-11 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 2 | 1 |
| q-ecacff80-117 | 2 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-23 | 1 | 2 | 1 | 2 | 1 | 1 | - | 2 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | edit | 1 | 2 | 21 | 2 |
| q-ecacff80-20 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 1 | 1 | 1 |
| q-863b3b8d-13 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 2 | 1 | 1 | 1 | neutral | 1 | 1 | 5 | 2 |
| q-ecacff80-154 | 2 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 1 | 9 | 1 |
| q-ecacff80-35 | 2 | 1 | 1 | 2 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 16 | 2 |
| q-ecacff80-24 | 2 | 3 | 4 | 3 | 2 | 4 | - | 3 | 2 | 3 | 3 | 3 | 1 | 2 | 2 | 2 | edit | 2 | 3 | 6 | 1 |
| q-863b3b8d-16 | 1 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 7 | 1 |
| q-ecacff80-185 | 2 | 1 | - | 1 | 3 | 1 | - | 1 | 3 | 2 | 3 | 2 | - | 3 | 3 | 3 | write | 3 | 1 | 1 | 1 |
| q-ecacff80-36 | 2 | 1 | 1 | 2 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 18 | 2 |
| q-ecacff80-26 | 1 | 1 | 1 | 4 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 5 | 1 |
| q-863b3b8d-18 | 4 | 2 | 2 | 1 | 1 | 1 | - | 2 | 1 | 2 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 2 | 1 | 1 |
| q-ecacff80-191 | 4 | 1 | 5 | 3 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-38 | 1 | 1 | 2 | - | - | 5 | - | 1 | 4 | 1 | 2 | 2 | - | 4 | 3 | 3 | write | 3 | 1 | 6 | 3 |
| q-ecacff80-50 | 1 | 3 | - | 2 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 3 | 13 | 3 |
| q-863b3b8d-24 | 4 | 4 | 4 | 4 | - | - | - | 5 | 6 | 5 | 5 | 5 | - | 6 | 1 | 1 | neutral | 5 | 4 | 1 | - |
| q-ecacff80-227 | 2 | 1 | 1 | - | - | - | - | 1 | 3 | 1 | 1 | 2 | 1 | 3 | 2 | 2 | write | 2 | 1 | 1 | 2 |
| q-ecacff80-44 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-55 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-863b3b8d-25 | 2 | 1 | 1 | 5 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 1 | 1 |
| q-ecacff80-273 | 2 | 1 | 1 | - | - | - | - | 1 | - | 1 | 1 | 5 | - | - | - | - | write | - | - | - | - |
| q-ecacff80-109 | 2 | - | - | 1 | 1 | 1 | - | - | 4 | - | - | 4 | 4 | 1 | 3 | 1 | edit | 1 | 11 | 1 | 1 |
| q-ecacff80-98 | 1 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 5 | 1 |
| q-ecacff80-305 | 4 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-135 | 3 | 2 | 2 | - | 1 | 2 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | write | 1 | 2 | 21 | 5 |
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
| q-ecacff80-136 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 2 | 1 | 1 | 1 | edit | 1 | 1 | 6 | 3 |
| q-ecacff80-419 | 2 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 1 | 3 | 1 |
| q-ecacff80-158 | 1 | - | - | - | - | - | - | - | 11 | - | - | - | 5 | 9 | 5 | 5 | write | 9 | 16 | 24 | - |
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
| q-ecacff80-184 | 4 | 2 | 1 | - | 1 | 2 | - | 2 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | neutral | 1 | 2 | 13 | 3 |
| q-831ea6d5-5 | 2 | 1 | 1 | 1 | - | - | - | 1 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-186 | 4 | 1 | 1 | - | - | - | - | 1 | 3 | 1 | 1 | 3 | 1 | 3 | 2 | 2 | edit | 1 | 1 | 1 | 1 |
| q-ecacff80-197 | 2 | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | write | - | - | - | - |
| q-ecacff80-200 | 4 | 1 | 1 | - | - | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 2 | 2 | write | 1 | 1 | 1 | 1 |

## Round 17 assessment

Baseline: the shipped configuration, hybrid fusion + 7-day recency half-life, at
hit@1 0.833 / hit@5 0.911 / MRR 0.866. Every experiment below starts from that same
7-day-decayed 40-row pool, so the delta belongs to the new rule.

| experiment | hit@1 | hit@5 | MRR | vs baseline |
|---|---:|---:|---:|---:|
| baseline (decay 7 d) | 0.833 | 0.911 | 0.866 | — |
| A. intent/type boost (1.10x) | 0.700 | 0.911 | 0.770 | **−0.133** |
| B. synonym expansion (lexical) | 0.844 | 0.911 | 0.872 | +0.011 |
| B'. synonym expansion (lexical+vector) | 0.844 | 0.911 | 0.872 | +0.011 |
| C. body-embedding rerank of top-20 | 0.678 | 0.878 | 0.761 | −0.155 |
| C'. title-embedding rerank of top-20 | 0.456 | 0.622 | 0.536 | −0.377 |
| C''. title-embedding RRF blend | 0.667 | 0.856 | 0.744 | −0.166 |
| B + near-duplicate merge + decay 7 d | **0.867** | **0.933** | **0.894** | **+0.034** |

### A. Intent/type weighting: rejected

The query intent was classified from generic task verbs (19 write / 38 edit / 33
neutral), and rows whose title verb matched the intent were multiplied by 1.10.
It cost 12 hit@1 queries: the corpus title only records *which tool call* produced
the row ("Created" vs "Changed"), which is a weak proxy for what the request asked
for, and boosting 40% of the pool reorders away the recency signal. Production has a
real `type` column, but mapping an intent to that taxonomy needs a trained
classifier; a verb lexicon mislabels a third of the requests. Cost is not the
blocker — the signal is.

### B. Synonym expansion: +1 query

A generic bilingual software vocabulary (37 terms, 38→ English equivalents, capped
at 12 extra tokens) fired on 62 of 90 requests. Adding the equivalents to the
*lexical* half gave +1 hit@1 and +0.006 MRR, with no query regressing; adding them
to the *vector* half changed nothing, so the gain is entirely from token coverage.
The target 0.85 needs 77 of 90 and this reaches 76.

Production cost: a Java-side term map plus expanding the query before token
matching (~40 lines, one config flag), and nothing on the storage side. The measured
gain is one query, which is inside the ±1-query run-to-run noise (see below), so it
was **not shipped**: a code path whose effect cannot be separated from embedding
noise is not worth the maintenance.

### C. Embedding rerank of the fused top-20: rejected

Body cosine rerank (the "use the real embedding to reorder" literal form) drops to
0.678 because the cosine is already the vector half of the fusion; reordering by it
discards the lexical evidence that RRF exists to preserve. Title cosine is far
worse (0.456): corpus titles are file paths ("Created src/..."), nearly identical
across observations, so the title embedding cannot separate them. A 0.5-weighted
title RRF blend still lost 0.166. Cost is low (one extra ranking key, no extra
embedding if title vectors are stored), but there is no configuration that helps.

### Combination that clears 0.85, and why it was not shipped

Synonym expansion on both halves plus the near-duplicate merge (newest of each
normalized title) and the 7-day decay reaches **0.867 / 0.933 / 0.894**: it fixes 3
baseline misses and breaks 0. But the merge is the rule round 16 rejected: it
drops older copies of the same file and hides at least one relevant row for
**76 of 90** queries. The metric improves because the newest copy often is also
relevant under temporal attribution, but a user looking for an older decision
would lose it. Shipping it is a product decision (dedupe vs. recall), not a
retrieval-tuning decision.

### Where the remaining misses are

Of the 15 baseline misses, **5 have no relevant row anywhere in the 40-candidate
pool** — candidate generation, not ranking, is the floor there; synonym expansion
does not recover any of them (the observation text shares no token or semantic
space with the Chinese request). The other 10 are ranking ambiguity among
same-file edits from adjacent sessions, where several near-duplicate rows are
plausibly correct. Further gains need either a wider candidate pool (cost: latency
and more false candidates) or a cross-encoder/LLM reranker over the top-20
(cost: one model call per search, hundreds of ms), neither of which is justified
by a 1-2 query delta.

### Measurement noise

Two runs on the same corpus moved pure-vector hit@1 between 0.689 and 0.700 (±1
query) because the provider embeddings are not bit-identical; the shipped baseline
(0.833) was stable across both. Differences smaller than one query (0.011) should
not be treated as a signal — which is exactly why experiment B was reported but
not adopted.
