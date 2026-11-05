# Project State & Handover

> **Purpose**: Current, verified state of this repository so the next agent or contributor can continue without archaeology or trusting stale notes.
> **Updated by**: Manual/cron maintenance at the end of each gate-green batch.
> **Update rule**: Overwrite with the latest verified facts (HEAD, test counts, migration version). Do not append history — that belongs in `docs/drafts/backend-fix-progress.md` and `docs/archive/`.

## Snapshot

Verified on 2026-11-05 at commit `bdee0a3` (the state the gate below ran against,
before this document was added):

| Item | Value |
|------|-------|
| Tests | **601** total, 0 skipped |
| L1 unit/integration | 565 |
| L2 HTTP regression | 29 |
| L3 acceptance (incl. proxy forwarding) | 6 |
| L4 end-to-end | 1 |
| Flyway | **V25** (`backend/src/main/resources/db/migration/`) |
| PostgreSQL / pgvector | 16 / **0.8.7** |
| Architecture baselines | **0** controller→repository, **0** entity-returning controller methods |

The ratchet lives in `scripts/test-baseline.json`; it must be raised in the same
commit as new tests, never lowered. The same floor is repeated in
`scripts/verify-local.sh`, `.github/workflows/backend-tests.yml`,
`docs/TESTING.md` and `docs/TESTING-zh-CN.md`.

## Run the full gate

One command proves the test base is green **from an empty database**:

```bash
PG_CONTAINER=cortexce-flaky-pg PG_PORT=15433 REQUIRE_BROWSER_E2E=1 ./scripts/verify-local.sh
```

Preconditions and behaviour:

- A running PostgreSQL 16 container with pgvector is required; a missing container
  is an error, never a silent skip. `PG_CONTAINER` / `PG_PORT` (and optionally
  `PG_USER` / `PG_PASSWORD`) point at it.
- Each run creates a uniquely named database (`cortexce_verify_<timestamp>_<pid>_flyway_rehearsal`)
  and drops it on exit. The `_flyway_rehearsal` suffix is mandatory — DB-gated
  tests skip without it. `TEST_DB=<name>` pins it, `VERIFY_KEEP_DB=1` keeps it.
- `REQUIRE_BROWSER_E2E=1` turns the Playwright/Chromium and pinned-toolchain SKIPs
  into failures. Without it those two checks may print SKIP locally; a CI run
  must set the flag.
- Logs are written to `VERIFY_LOG_DIR` (default `/tmp/cortexce-verify-*`).

## What the gate guards

| Step | Guards | If it is red, look at |
|------|--------|------------------------|
| 5 | Full backend suite, all four layers in one Maven run | `backend/target/surefire-reports/` |
| 6 | Zero skips, minimum 601 tests, all four layers present, per-layer baseline not lowered | `scripts/check-test-results.py`, `scripts/test-baseline.json` |
| 6b | Evaluation floors are append-only (cannot be lowered) | `scripts/check-eval-gates.py` |
| 6c | Recorded retrieval-quality ranking still replays | `scripts/check-retrieval-quality.py` |
| 7 | API reference checker self-test, then EN/ZH docs vs the live server (paths, codes, examples) | `scripts/check-api-doc-surface.py`, `docs/API.md`, `docs/API-zh-CN.md` |
| 8 | Migration safety: 23 legacy migrations are reported only; a **new** migration with `DROP`/`TRUNCATE`/narrowing type/non-concurrent index needs a `-- rollback:` note | `scripts/check-migrations.py`, `scripts/migration-baseline.json` |
| 9 | Proxy E2E forwarding, zero skipped | `proxy/`, `scripts/` proxy E2E |
| 9b | OpenClaw plugin: placeholder filtering + session lifecycle, zero skipped | `openclaw-plugin/` |
| 10 | Viewer bundle equals a fresh build of `webui/src`, then Playwright: rendering, files, `hasMore`, SSE-only row, refresh, settings, 401, outage recovery (21 checks) | `scripts/viewer-build`, `webui/` |

`webui/` is a git submodule. **Do not modify it in this repo**; backend API
response field names are a hard contract with it (`hasMore`, `updateFiles`,
`CLAUDE_MEM_*`).

## Database and schema decisions

- **Chinese lexical search** (V24): `search_vector` (`to_tsvector('english', …)`)
  was replaced by `search_tokens`, a generated `tsvector` over `cortex_tokens()`.
  The tokenizer keeps whole ASCII identifiers and emits CJK character bigrams;
  GIN-indexed, file paths at weight C. `pg_bigm` / `zhparser` are not in the
  image and were not adopted. Measured: 0.3 s and hit@5 0.867 at the tested size.
- **Type-filtered list** (V25): `idx_observations_scope_type_created`
  (`memory_scope_id`, `type`, `created_at_epoch DESC`) so the limit stops the scan
  instead of sorting the whole type. Before it, `type_filter` p95 was 29.38 ms at
  100k rows versus 4.94 ms at 1k (V25 header); the scope predicate uses `UNION ALL`
  (not `OR`) so the planner can use it as an index condition.
- **Vector indexes**: HNSW on the embedding columns; validity is exposed as
  `cortexce_vector_indexes_valid` (1 = valid) and asserted by the metrics test.
  Rebuild with `scripts/rebuild-vector-index.sh`.
- Restore tuning is not a schema decision but matters for operations: see
  `docs/DEPLOYMENT.md` ("default value cost" table).

## Known boundaries and data-rejected approaches

Do not retry these without new evidence:

- **3M+ rows in one HNSW graph**: rejected. Sharding by scope (8 × 375k) built at
  261 µs/row with no spill, versus 550–802 µs/row and spill after ~900k in one
  1M-row graph. Policy: keep a single DB up to ~1.5M rows; above that,
  `PARTITION BY LIST (project_path)` with one local HNSW per partition.
  Evidence: `docs/archive/2026-11-05_vector-sharding-3m.md`.
- **Default restore GUCs for 1M rows**: rejected on latency. With server defaults
  (64 MB / 0 workers) a restore took 2,447.9 s plus ~550 s HNSW rebuild; with
  `RESTORE_WORK_MEM=1GB RESTORE_WORKERS=2` the whole drill was 99.8 s.
  Evidence: `docs/archive/2026-11-05_ops-loop-1m.md`, `docs/DEPLOYMENT.md`.
- **Architecture exceptions**: eliminated. Controllers no longer import repositories
  and no controller returns entity rows; the only accepted package cycle is
  `config`/`entity`/`event`/`repository`/`service`/`util` (Spring wiring).
  Adding a new exception requires editing `ArchitectureGuardTest` and
  `docs/ARCHITECTURE*.md` with a reason.
- **Text-only fallback semantics**: `fell_back` is true only when the embedding
  call fails, not for deliberate text-only searches; a read-only database returns
  503 `service_unavailable` while search keeps returning 200.
  Evidence: `docs/drafts/backend-fix-progress.md`.

## Not yet verified / next inputs

These were explicitly not started when this document was written; do not assume
they exist:

- A scripted replay of the README quick start (deps → start → health → write → retrieve).
- CI per-job duration/caching measurements and a 3-consecutive-run flaky check.
- Executable Prometheus alert rules for queue backlog, failure rate, circuit-open,
  embedding backlog, retrieval p95 and SSE connections. No `promtool` is assumed
  locally; rules would be marked unverified until `promtool check rules` runs.
- The remaining "not doing / deferred" audit items re-check.

## Where the evidence lives

- `docs/archive/YYYY-MM-DD_<topic>.md` — immutable point-in-time reports
  (benchmarks, outage drills, audits). `YYYY-MM-DD` is the date the content was
  last relevant, not the archival date. Index: `docs/archive/README.md`.
- `docs/drafts/backend-fix-progress.md` — per-batch implementation log
  (newest first) with measured numbers and commit ranges.
- `docs/performance-test-results.md` — the live performance summary.
- `docs/TESTING.md` / `docs/TESTING-zh-CN.md` — how layers are defined and run.
- `docs/ARCHITECTURE.md` / `docs/ARCHITECTURE-zh-CN.md` — enforced architecture
  rules and their baselines.
