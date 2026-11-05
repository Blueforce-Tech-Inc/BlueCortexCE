# Audit re-scan: untested and unmonitored items — 2026-11-05

Second pass over `docs/drafts/backend-review-findings.md`, this time asking two
questions the first pass (`2026-11-05_audit-test-gaps.md`) did not separate:

1. Is there a **behaviour** that no test would catch if it regressed?
2. Is there a **state an operator must notice** that nothing exports?

The first pass answered (1) for the fixed entries. This pass covers what the
recent batches added (provider outage, response payload, read-only database,
vector-index rebuild, 1M ops loop) plus the monitoring surface.

## Gaps found and closed

| Gap | Why it mattered | Fix |
|---|---|---|
| Circuit-breaker state was log-only | `ProviderCircuitBreaker` opened after three unreachable outcomes and logged a WARN, but nothing in `/actuator/prometheus` or `/api/health` said so. An operator could only find out by grepping logs after requests already failed fast. | `CortexMetrics` registers `cortexce_llm_circuit_state{state=closed|half_open|open}` (exactly one series is `1`, all three present at idle). `L2MetricsEndpointTest.circuitStateTracksTheBreaker` asserts closed=1 at idle, opens the breaker through the same `onOutcome` path the worker uses, and asserts open=1/closed=0; `@AfterEach` resets the singleton so later classes are unaffected. |
| A write to a read-only database looked like a crash | SQLSTATE 25006 came back as a generic 500 with `internal_error`, indistinguishable from an application bug. | Mapped to **503 `service_unavailable`** with a message naming the cause; `L2ReadOnlyDatabaseTest` pins both the write (503 + readable message) and the read path (200, count 0). |
| `fell_back` was `true` for any search with a query vector | The flag meant "a vector was used", not "the embedding call failed", so a healthy hybrid search reported a degradation that had not happened. | `SearchRequest.embeddingFailed`; `fell_back` now reflects only the failed call. Pinned by `LlmProviderFailureTest` (true on circuit open) and `OfflineAiChainIntegrationTest` (false for a text-only query). |

## Reviewed, deliberately left as-is

| Item | Why no test / no metric |
|---|---|
| HNSW index validity (`indisvalid`) | The only way to leave an invalid index is a build that exits non-zero; `rebuild-vector-index.sh` drops the partial index on failure and `test-vector-index-rebuild.sh` proves it (exit 3, state `absent`). A scrape-time `pg_index` probe would add a per-request catalog read to every scrape to detect a state the build path cannot leave behind silently. |
| Backup-drill success | The drill is an external scheduled script whose contract is the exit code (2–11) and the JSON report; an alert belongs on the scheduler's non-zero exit, not inside the application. The 1M run and the retention run both exercise it end to end. |
| Retention pruning of dumps | Same shape: a shell loop with `KEEP_DUMP` / `DUMP_KEEP_DAYS`; verified with two stale files pruned and a fresh dump kept (`2026-11-05_ops-loop-1m.md`). |
| Queue depth / stale / embedding backlog | Already gauges, already asserted in `L2MetricsEndpointTest`; not re-added. |
| Documentation-only findings | Unchanged from the first pass: a test would assert wording, and the two mechanical numbers (endpoint surface, SDK case counts) are already guarded by `scripts/check-api-doc-surface.py` and `run-all-e2e.sh`. |
| The 71 `⏸` entries | No behaviour changed, so there is nothing to pin; each keeps its measurement. |

## Verification

`L2MetricsEndpointTest`, `L2ReadOnlyDatabaseTest`, `LlmProviderFailureTest` and
`OfflineAiChainIntegrationTest` pass against the rehearsal database; the circuit
test log shows `Provider circuit opened: 3 consecutive unreachable outcomes`
before the scrape, so the gauge was read from the live breaker rather than a
mock. Baseline count is raised by the two new L2 cases (see
`scripts/test-baseline.json`).
