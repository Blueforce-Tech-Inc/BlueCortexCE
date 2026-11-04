# Provider outage behaviour, end to end — 2026-11-05

Live, not a gate. Two harnesses:

- `scripts/provider-outage-e2e.py` starts a throwaway database, an
  OpenAI-compatible stub that answers HTTP 500 to every chat and embedding call,
  and the real jar, then drives the write, search and ingest paths.
- `scripts/recheck-longcat-quota.py` makes one minimal non-streaming chat call
  against the provider configured in `backend/.env` and classifies the answer, so
  the real-model evaluation can be re-run once its quota comes back.

Raw results: `2026-11-05_provider-outage-behavior.json`.

## 1. LongCat quota: still exhausted

```
$ env -u CI -u GITHUB_ACTIONS python3 scripts/recheck-longcat-quota.py
provider: https://api.longcat.chat/openai model: LongCat-2.5
status: HTTP 402 in 295 ms -> quota or balance exhausted (HTTP 402)
        code=too_many_requests message=AppId:**Q02U Usage limit reached.
verdict: EXHAUSTED
follow-up: none; the real-model evaluation stays blocked
```

Exit code 3 (`EXHAUSTED`). The script never prints or writes the key, and it
does not block the gate. When it exits 0 it prints the follow-up command:

```bash
env -u CI -u GITHUB_ACTIONS EVAL_LIMIT=20 EVAL_MAX_CALLS=20 ./scripts/eval-model-quality.sh
```

The two earlier `eval-model-quality` runs (round 22 and the rerank measurement)
are still the last real-provider evidence; nothing here supersedes them.

## 2. What a user sees while the provider is down

Measured with the 500-stub, one observation, one search, four tool-use ingests.
"Before" is the jar as shipped in the previous commit; "after" is this batch.

| Path | Before | After |
|---|---|---|
| `POST /api/ingest/observation` | > 200 s, killed by the harness timeout (embedding retried on Spring AI's default ten-attempt template) | **1.39 s → 200**, row saved with `embedding_state=failed`, `embedding_error=TransientAiException` |
| `GET /api/search` | same unbounded retry on the query embedding | **1.09 s → 200**, `strategy=tsvector`, `results=1`, `fell_back=false` |
| `POST /api/ingest/tool-use` × 3 | 200 `{"status":"accepted"}` each, but the circuit opened and then **closed again** on a transport error classified `UNKNOWN`; 9 real provider calls | 200 `{"status":"accepted"}` each, **3** real provider calls, circuit opened once |
| `POST /api/ingest/tool-use` (4th) | provider called again | **200 `{"status":"accepted"}`, 0 extra provider calls**, fast-fail logged |
| `mem_pending_messages` | readable but the streak kept resetting | `failed/1/UNAVAILABLE` ×3 then `failed/1/CIRCUIT_OPEN` |
| `GET /api/health` | 200 | 200 (5 ms) |

Log evidence: `circuit_opened=1`, `circuit_closed=0`, `fast_fail=9`,
`observation_embedding_failed=1`; the stub counted `chat=3, embeddings=4`.

### Defects this measured and this batch fixes

1. **The embedding model used Spring AI's default retry template** (ten attempts,
   backoff capped at three minutes) while sitting on the synchronous request path.
   One observation write or one viewer search could hold a request for minutes.
   The embedding bean now builds its own bounded template and a JDK request
   factory with a read timeout, `claudemem.embedding.*`
   (`read-timeout-ms=15000`, `retry-max-attempts=2`, `retry-backoff-ms=1000`,
   `retry-max-backoff-ms=3000`). Measured 200 s → 1.39 s.
2. **A transport-level I/O error was classified `UNKNOWN`, and `UNKNOWN` closes
   the circuit.** The JDK client produced `I/O error on POST request … HTTP/1.1
   header parser received no bytes` between the 500s, so the breaker opened and
   immediately closed, and every later job paid a provider round trip. `classify`
   now maps an `IOException` in the cause chain to `UNAVAILABLE` (subtype timeouts
   keep `TIMEOUT`). Measured 9 calls for 3 jobs → 3 calls, then 0.

### Remaining readability gap (recorded, not changed)

With embeddings down the search envelope says `strategy=tsvector` but
`fell_back=false`, because no query vector was ever requested — the controller
falls back before `SearchService` sees a vector, and `fell_back` is computed as
"query and queryVector were both present". The WebUI does not read `fell_back`,
so leaving it is a deliberate choice: it is a documented wire field and changing
its meaning would break the contract this batch is otherwise protecting. The
strategy field is the honest signal, and the harness pins it.

`embedding_error` stores the exception class name (`TransientAiException`), which
is terse but stable and free of request text; the row's `embedding_state=failed`
is what the backfill and the UI act on.

## 3. Files

| File | Change |
|---|---|
| `scripts/provider-outage-e2e.py` | New harness (stub, throwaway DB, assertions on all three paths) |
| `scripts/recheck-longcat-quota.py` | New quota probe, exit 0/2/3/4, no key output |
| `backend/src/main/java/.../config/SpringAiConfig.java` | Embedding bean: bounded retry template + read-timeout request factory |
| `backend/src/main/resources/application.yml` | `claudemem.embedding.*` with the measured rationale |
| `backend/src/main/java/.../service/LlmService.java` | Transport `IOException` → `UNAVAILABLE` |
| `backend/src/test/java/.../service/LlmServiceClassificationTest.java` | 8 cases pinning the classification, including the exact observed message |
| `backend/src/test/java/.../config/SpringAiConfigProviderBranchTest.java` | Updated for the new embedding bean signature |

### Reproduction

```bash
docker run -d --name claude-mem-test-pg -p 5433:5432 \
  -e POSTGRES_PASSWORD=123456 pgvector/pgvector:pg16
python3 scripts/provider-outage-e2e.py --observation-timeout 120
env -u CI -u GITHUB_ACTIONS python3 scripts/recheck-longcat-quota.py; echo $?
```
