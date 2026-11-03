# Fault injection: pool exhaustion, embedding outage, kill during processing

- Date: 2026-11-04 01:18:53
- Database: cortexce_load on claude-mem-test-pg:5433 (disposable, Flyway-migrated)
- Service: packaged jar on http://127.0.0.1:37778, Hikari pool 3, connection timeout 2 s, stale threshold 1 min, recovery every 5 s
- Model: local OpenAI-compatible stub; chat delay 2 s during the kill, embedding endpoint stalls 3 s then answers 504 (provider-side timeout)

## 1. DB connection-pool exhaustion

| metric | value |
|---|---:|
| fault window seconds | 12.03 |
| ingest requests posted | 12 |
| ingest accepted | 9 |
| ingest error codes | [500, 500, 500] |
| search error codes during the lock | [500, 500, 500] |
| queue drained after release | True |
| accepted events persisted | 9 |
| accepted events lost | 0 |
| rows stuck in processing | 0 |
| duplicate content hashes | 0 |
| search status after recovery | 200 |

## 2. Embedding endpoint outage

`ready|` is the healthy baseline: the stub returns a 1024-wide vector and the row reaches `ready`.

| metric | value |
|---|---:|
| non-retryable rejection: row state | failed|NonTransientAiException |
| non-retryable rejection: backfill repair | ready| (2.13 s) |
| timeout: rows written while the endpoint was down | 0 |
| timeout: state after the endpoint recovered | ready| |
| timeout: outage start to persisted row (s) | 71.82 |
| timeout: rows written in total | 1 |

A provider-side timeout is retried by the OpenAI client, so the observation is not written until the retry budget is spent or the endpoint recovers; the event stays in the queue and is written exactly once. A non-retryable rejection is recorded as `failed` on the row and repaired by the embedding backfill sweep.

## 3. SIGKILL during processing, then restart

| metric | value |
|---|---:|
| events accepted before the kill | 15 |
| jobs processing at the kill | 10 |
| queue drained after restart | True |
| accepted events persisted | 15 |
| accepted events lost | 0 |
| rows stuck in processing | 0 |
| duplicate content hashes | 0 |
| search status after restart | 200 |
| recovery log | [2026-11-04 01:18:51.604] [WARN ] [c.a.c.s.StaleMessageRecoveryTask] Recovered 10 stale messages (processing > 1 min) |

Verdict: PASS. The pass condition is persistence and recovery, not the absence of errors: during an injected outage the API is allowed to return errors, but no accepted event may be lost and no queue row may stay in processing.

