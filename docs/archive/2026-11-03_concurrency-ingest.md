# Concurrency: 20 sessions ingesting while session-end closes them

- Date: 2026-11-04 00:26:18
- Database: cortexce_load on claude-mem-test-pg:5433 (disposable, Flyway-migrated)
- Service: packaged jar on http://127.0.0.1:37778
- Model: local OpenAI-compatible stub, so the queue is the only bottleneck

| metric | value |
|---|---:|
| sessions | 20 |
| events per session | 10 |
| ingest requests posted | 200 |
| ingest requests accepted | 161 |
| ingest requests rejected (409 after session end) | 39 |
| accepted events expected to persist | 161 |
| session-end requests accepted | 20 |
| sessions marked completed | 20 |
| queue drained | True |
| elapsed seconds | 7.05 |
| observations persisted | 161 |
| observations lost | 0 |
| duplicate content hashes | 0 |
| rows stuck in processing | 0 |

Verdict: PASS. A job is lost when session-end cancels it; the observed count above must equal the accepted ingest count.

