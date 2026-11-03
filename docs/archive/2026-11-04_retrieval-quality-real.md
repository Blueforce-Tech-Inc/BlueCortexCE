# Retrieval quality: real session-history queries

- Date: 2026-11-04 02:43:35
- Corpus: `backend/src/test/resources/eval/retrieval-corpus-real.json` (126 observations, 60 queries)
- Embedding model: BAAI/bge-m3 (1024 dimensions), real provider calls
- Database: disposable cortexce_retrieval_1793731342 on claude-mem-test-pg:5433
- Ground truth: temporal attribution (the observations produced while the assistant answered the real prompt); rationale stored per query.

## hit@1 / hit@5 / MRR

| path | hit@1 | hit@5 | MRR | empty results |
|---|---:|---:|---:|---:|
| vector | 0.600 | 0.817 | 0.688 | 0 |
| tsvector | 0.000 | 0.000 | 0.000 | 60 |
| hybrid | 0.600 | 0.817 | 0.688 | 0 |
| hybrid_no_floor | 0.600 | 0.817 | 0.688 | 0 |
| rrf | 0.600 | 0.817 | 0.688 | 0 |
| simple_lexical | 0.000 | 0.000 | 0.000 | 60 |
| hybrid_simple | 0.600 | 0.817 | 0.688 | 0 |
| hybrid_simple_rrf | 0.600 | 0.817 | 0.688 | 0 |
| identifier_or | 0.517 | 0.650 | 0.565 | 5 |
| identifier_or_rrf | 0.583 | 0.767 | 0.660 | 0 |
| vector_head120 | 0.500 | 0.733 | 0.595 | 0 |
| vector_tail120 | 0.417 | 0.667 | 0.512 | 0 |

## Diagnosis

Measured hybrid ties pure vector at MRR 0.688. The English tsvector half returns nothing for these Chinese queries (60 of 60 empty). Identifier OR reaches 0.565; focused query prefixes reach 0.595/0.512. Vector remains the best single signal on this corpus.

## Per-query first relevant rank

| query | relevant | vector | vector-head120 | vector-tail120 | tsvector | hybrid | hybrid-no-floor | rrf | simple-lexical | hybrid-simple | hybrid-simple-rrf | identifier-or | identifier-or-rrf |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| q-ecacff80-6 | 3 | 1 | 1 | - | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-13 | 1 | 1 | 2 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-142 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-9 | 3 | 2 | - | 3 | - | 2 | 2 | 2 | - | 2 | 2 | - | 2 |
| q-38f778cd-2 | 1 | 1 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-3643d387-3 | 3 | 1 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-3643d387-23 | 3 | - | - | - | - | - | - | - | - | - | - | - | - |
| q-863b3b8d-2 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | - | 1 |
| q-863b3b8d-4 | 4 | 2 | 2 | 2 | - | 2 | 2 | 2 | - | 2 | 2 | - | 2 |
| q-ecacff80-12 | 3 | 1 | - | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-16 | 1 | 1 | - | 1 | - | 1 | 1 | 1 | - | 1 | 1 | - | 1 |
| q-ecacff80-584 | 2 | - | - | - | - | - | - | - | - | - | - | - | - |
| q-ecacff80-11 | 1 | - | 1 | - | - | - | - | - | - | - | - | - | 3 |
| q-3643d387-6 | 3 | 1 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | - | 1 |
| q-3643d387-26 | 4 | - | - | - | - | - | - | - | - | - | - | - | - |
| q-863b3b8d-5 | 1 | - | - | - | - | - | - | - | - | - | - | - | - |
| q-863b3b8d-7 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | - | 2 |
| q-ecacff80-39 | 2 | 1 | 1 | - | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-17 | 1 | - | - | - | - | - | - | - | - | - | - | - | - |
| q-ecacff80-587 | 1 | 1 | 1 | - | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-18 | 1 | - | - | 3 | - | - | - | - | - | - | - | 1 | 4 |
| q-3643d387-14 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 2 | 1 |
| q-3643d387-31 | 1 | 1 | 3 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 5 | 3 |
| q-863b3b8d-9 | 1 | 2 | 2 | 2 | - | 2 | 2 | 2 | - | 2 | 2 | - | - |
| q-ecacff80-111 | 4 | - | - | 1 | - | - | - | - | - | - | - | 3 | - |
| q-ecacff80-21 | 1 | 3 | - | 1 | - | 3 | 3 | 3 | - | 3 | 3 | - | - |
| q-ecacff80-19 | 1 | 1 | 2 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-3643d387-28 | 3 | 1 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-863b3b8d-11 | 3 | 1 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 2 |
| q-ecacff80-117 | 2 | 1 | 1 | - | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-23 | 1 | 4 | 5 | - | - | 4 | 4 | 4 | - | 4 | 4 | 5 | 2 |
| q-ecacff80-20 | 3 | 1 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-863b3b8d-13 | 2 | 2 | 2 | 2 | - | 2 | 2 | 2 | - | 2 | 2 | 2 | 1 |
| q-ecacff80-154 | 2 | 1 | 1 | - | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-35 | 2 | 1 | 5 | 4 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-24 | 2 | 4 | 4 | 3 | - | 4 | 4 | 4 | - | 4 | 4 | 1 | 2 |
| q-863b3b8d-16 | 1 | 2 | 2 | 2 | - | 2 | 2 | 2 | - | 2 | 2 | 2 | 1 |
| q-ecacff80-185 | 2 | 1 | - | 1 | - | 1 | 1 | 1 | - | 1 | 1 | - | - |
| q-ecacff80-36 | 2 | 1 | 1 | 2 | - | 1 | 1 | 1 | - | 1 | 1 | 3 | 1 |
| q-ecacff80-26 | 1 | 1 | 1 | 4 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-863b3b8d-18 | 4 | 2 | 2 | 3 | - | 2 | 2 | 2 | - | 2 | 2 | 1 | 1 |
| q-ecacff80-191 | 4 | 1 | 1 | 3 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-38 | 1 | 4 | 5 | - | - | 4 | 4 | 4 | - | 4 | 4 | - | - |
| q-ecacff80-50 | 1 | 3 | - | 2 | - | 3 | 3 | 3 | - | 3 | 3 | 1 | 1 |
| q-863b3b8d-24 | 4 | - | - | - | - | - | - | - | - | - | - | - | - |
| q-ecacff80-227 | 2 | 1 | 1 | - | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-44 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-55 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-863b3b8d-25 | 2 | 1 | 1 | 5 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-273 | 2 | 1 | 1 | - | - | 1 | 1 | 1 | - | 1 | 1 | - | 3 |
| q-ecacff80-109 | 2 | - | - | 1 | - | - | - | - | - | - | - | - | - |
| q-ecacff80-98 | 1 | 1 | 1 | - | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-305 | 4 | 1 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-135 | 3 | 2 | 2 | - | - | 2 | 2 | 2 | - | 2 | 2 | 1 | 1 |
| q-ecacff80-116 | 4 | 3 | 1 | 1 | - | 3 | 3 | 3 | - | 3 | 3 | 3 | 3 |
| q-ecacff80-345 | 2 | - | - | - | - | - | - | - | - | - | - | - | - |
| q-ecacff80-138 | 1 | 1 | 2 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-121 | 1 | 1 | 1 | 3 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
| q-ecacff80-367 | 2 | 1 | 1 | - | - | 1 | 1 | 1 | - | 1 | 1 | - | - |
| q-ecacff80-149 | 2 | 1 | 1 | 1 | - | 1 | 1 | 1 | - | 1 | 1 | 1 | 1 |
