# Retrieval quality: real session-history queries

- Date: 2026-11-04 04:26:20
- Corpus: `backend/src/test/resources/eval/retrieval-corpus-real.json` (126 observations, 60 queries)
- Embedding model: BAAI/bge-m3 (1024 dimensions), real provider calls
- Database: disposable cortexce_retrieval_1793737515 on claude-mem-test-pg:5433
- Ground truth: temporal attribution (the observations produced while the assistant answered the real prompt); rationale stored per query.

## hit@1 / hit@5 / MRR

| path | hit@1 | hit@5 | MRR | empty results |
|---|---:|---:|---:|---:|
| vector | 0.617 | 0.817 | 0.696 | 0 |
| lexical (V24 token coverage) | 0.767 | 0.867 | 0.805 | 0 |
| lexical_ts_rank | 0.683 | 0.817 | 0.738 | 0 |
| english tsvector (replaced by V24) | 0.000 | 0.000 | 0.000 | 60 |
| hybrid (production RRF) | 0.733 | 0.883 | 0.799 | 0 |
| additive w0.25 (rejected) | 0.700 | 0.817 | 0.750 | 0 |
| additive w0.75 (rejected) | 0.733 | 0.817 | 0.768 | 0 |
| additive w1.0 (rejected) | 0.733 | 0.817 | 0.768 | 0 |
| hybrid_no_floor | 0.733 | 0.883 | 0.797 | 0 |
| rrf | 0.683 | 0.900 | 0.763 | 0 |
| identifier_or | 0.517 | 0.650 | 0.565 | 5 |
| identifier_or_rrf | 0.583 | 0.767 | 0.658 | 0 |
| vector_head120 | 0.500 | 0.733 | 0.595 | 0 |
| vector_tail120 | 0.417 | 0.667 | 0.512 | 0 |

## Diagnosis

V24 lexical path reaches hit@1 0.767, hit@5 0.867, MRR 0.805 with 0 empty results; the English tsvector it replaced was empty for 60 of 60 queries (hit@5 0.000). Production hybrid (weighted RRF, lexical weight 2.0) reaches hit@1 0.733, hit@5 0.883, MRR 0.799 against pure vector 0.617 / 0.817 / 0.696. The stored column also indexes file paths (weight C): that lifted the lexical half from hit@1 0.733 / MRR 0.788 to 0.767 / 0.805 in the same run, so a query that names a file matches it. Additive score fusion froze hit@5 at 0.817 for every weight (MRR 0.750 / 0.768 / 0.768 at w=0.25/0.75/1.0) because cosine (0.6-0.8) swamps coverage (0.05-0.2) — rank fusion removes that scale mismatch. Ordering the same tokens with ts_rank_cd gives MRR 0.738 versus 0.805 for coverage. Identifier-OR ranking scores 0.565 MRR.

## Per-query first relevant rank

| query | relevant | vector | vector-head120 | vector-tail120 | lexical | lexical-ts-rank | english-tsvector | add-w0.25 | hybrid (RRF w2) | add-w0.75 | add-w1.0 | rrf-k60 | identifier-or |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| q-ecacff80-6 | 3 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-13 | 1 | 1 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-142 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-9 | 3 | 2 | - | 3 | 1 | 2 | - | 1 | 1 | 1 | 1 | 1 | - |
| q-38f778cd-2 | 1 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-3643d387-3 | 3 | 1 | 1 | 1 | - | - | - | 1 | 6 | 1 | 1 | 4 | 1 |
| q-3643d387-23 | 3 | - | - | - | 5 | - | - | - | 4 | - | - | 4 | - |
| q-863b3b8d-2 | 2 | 1 | 1 | 1 | 1 | 4 | - | 1 | 1 | 1 | 1 | 1 | - |
| q-863b3b8d-4 | 4 | 2 | 2 | 2 | 1 | 1 | - | 2 | 1 | 1 | 1 | 2 | - |
| q-ecacff80-12 | 3 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-16 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | - |
| q-ecacff80-584 | 2 | - | - | - | - | - | - | - | 3 | - | - | 2 | - |
| q-ecacff80-11 | 1 | - | 1 | - | 1 | 2 | - | - | 2 | - | - | 3 | - |
| q-3643d387-6 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | - |
| q-3643d387-26 | 4 | - | - | - | - | - | - | - | 18 | - | - | - | - |
| q-863b3b8d-5 | 1 | - | - | - | 1 | 5 | - | - | 3 | - | - | 3 | - |
| q-863b3b8d-7 | 2 | 1 | 1 | 1 | 1 | 2 | - | 1 | 1 | 1 | 1 | 1 | - |
| q-ecacff80-39 | 2 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-17 | 1 | - | - | - | 1 | 2 | - | - | 3 | - | - | 3 | - |
| q-ecacff80-587 | 1 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-18 | 1 | - | - | 3 | 1 | 1 | - | - | 3 | - | - | 4 | 1 |
| q-3643d387-14 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 2 |
| q-3643d387-31 | 1 | 1 | 3 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 5 |
| q-863b3b8d-9 | 1 | 2 | 2 | 2 | 2 | - | - | 1 | 1 | 1 | 1 | 1 | - |
| q-ecacff80-111 | 4 | - | - | 1 | 1 | 1 | - | - | 3 | - | - | - | 3 |
| q-ecacff80-21 | 1 | 3 | - | 1 | 1 | 1 | - | 2 | 1 | 2 | 2 | 2 | - |
| q-ecacff80-19 | 1 | 1 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-3643d387-28 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-863b3b8d-11 | 3 | 1 | 1 | 1 | 2 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-117 | 2 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-23 | 1 | 4 | 5 | - | 1 | 1 | - | 2 | 1 | 1 | 1 | 1 | 5 |
| q-ecacff80-20 | 3 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-863b3b8d-13 | 2 | 2 | 2 | 2 | 1 | 1 | - | 2 | 1 | 2 | 2 | 2 | 2 |
| q-ecacff80-154 | 2 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-35 | 2 | 1 | 5 | 4 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-24 | 2 | 4 | 4 | 3 | 2 | - | - | 4 | 2 | 3 | 3 | 3 | 1 |
| q-863b3b8d-16 | 1 | 2 | 2 | 2 | 3 | 3 | - | 1 | 2 | 1 | 1 | 1 | 2 |
| q-ecacff80-185 | 2 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | - |
| q-ecacff80-36 | 2 | 1 | 1 | 2 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 3 |
| q-ecacff80-26 | 1 | 1 | 1 | 4 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-863b3b8d-18 | 4 | 2 | 2 | 3 | 1 | 1 | - | 2 | 1 | 2 | 1 | 2 | 1 |
| q-ecacff80-191 | 4 | 1 | 1 | 3 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-38 | 1 | 4 | 5 | - | - | - | - | 4 | 8 | 4 | 4 | 5 | - |
| q-ecacff80-50 | 1 | 3 | - | 2 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-863b3b8d-24 | 4 | - | - | - | - | - | - | - | 15 | - | - | - | - |
| q-ecacff80-227 | 2 | 1 | 1 | - | - | - | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-44 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-55 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-863b3b8d-25 | 2 | 1 | 1 | 5 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-273 | 2 | 1 | 1 | - | - | - | - | 1 | - | 1 | 2 | - | - |
| q-ecacff80-109 | 2 | - | - | 1 | 4 | 1 | - | - | 9 | - | - | - | - |
| q-ecacff80-98 | 1 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-305 | 4 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-135 | 3 | 1 | 2 | - | 1 | 2 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-116 | 4 | 3 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 3 |
| q-ecacff80-345 | 2 | - | - | - | - | - | - | - | - | - | - | - | - |
| q-ecacff80-138 | 1 | 1 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-121 | 1 | 1 | 1 | 3 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
| q-ecacff80-367 | 2 | 1 | 1 | - | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | - |
| q-ecacff80-149 | 2 | 1 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 | 1 | 1 |
