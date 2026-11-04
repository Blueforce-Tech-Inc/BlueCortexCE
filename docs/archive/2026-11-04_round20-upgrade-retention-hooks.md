# Round 20: old-database upgrade, retention, and real hook payloads

- Date: 2026-11-04
- Scope: the three paths that had never been exercised — an existing V12 database
  upgraded in place, age-based deletion, and real Claude Code hook payloads flowing
  through `proxy/wrapper.js`.

## 1. V12 → V25 in place (`scripts/upgrade-rehearsal.sh`)

The docker E2E starts from an empty volume (23 migrations straight to V25), and
`backup-restore-drill.sh` dumps and restores an already-V25 database. Neither covers
the common deployment case: a database that stopped at an older schema.

The rehearsal:

1. booted the jar with `SPRING_FLYWAY_TARGET=12`, so Flyway applied exactly the
   first **10** migrations (V9/V10 do not exist in this repository);
2. seeded **2 sessions, 2 observations, 2 summaries, 2 prompts and 2 queue rows**
   in the pre-V13 shape — children reference `mem_sessions.memory_session_id`,
   observations carry 1024-dim vectors and Chinese text, timestamps inside the
   search window, queue rows already `processed`/`failed` so the upgrade boot
   cannot change counts;
3. took a `pg_dump -Fc` of the V12 database (the rollback artifact);
4. booted the current jar without a target: Flyway applied V13..V25 (23 successful
   migrations total) and the service became healthy;
5. compared state, then restored the dump into a second database.

Results:

- row counts and every observation id were identical before and after;
- every observation ends with a resolvable `content_session_id` (V13 rewrite),
  `memory_session_id` is gone and `search_tokens` exists (V24/V25 shape);
- Chinese lexical search (`search_tokens @@ plainto_tsquery('simple','检索')`)
  returned the seeded row, and `GET /api/search?project=…&query=检索` returned HTTP
  200 with the same observation id;
- the rollback restore produced a **V12** database with identical counts, the
  pre-V13 columns and the seeded title intact — the dump is a complete rollback
  artifact.

The anchor `max(version)` was found to be wrong (Flyway stores `version` as
varchar, so `max('8')` beats `'25'`); the script uses the last successful
`installed_rank` instead.

Verdict: PASS. No row loss, search works after the upgrade, and the pre-upgrade
dump is a verified rollback.

## 2. Retention: dry-run first, 0 = keep forever

`RetentionService` + `cortexce.retention.*` (documented in DEPLOYMENT EN/ZH).
Defaults are deliberately inert: job disabled, run dry-run, every window `0`.

- Tables: `mem_user_prompts`, `mem_observations`, `mem_summaries` by
  `created_at_epoch < cutoff`; rolled log files by mtime.
- A run logs
  `Retention dry run: would delete prompts=… observations=… summaries=… logFiles=…`
  without writing; `dry-run=false` performs the deletes.
- `project` is an exact `project_path` predicate present in both the count and the
  DELETE, so a scoped run cannot touch another project.
- Log cleanup only considers files under the configured directory whose name
  starts with `claude-mem`; the active file is newer than the cutoff.

`RetentionServiceTest` (8 tests): defaults keep everything with zero JDBC
interactions, negative/zero windows are disabled, dry-run never issues DELETE,
the real scoped delete carries `created_at_epoch < :cutoff` (strict boundary) plus
`project_path = :project`, an unscoped run has no project predicate, old rolled
logs are deleted while the active and unrelated files stay, log dry-run keeps the
file, and the scheduled job is a no-op while disabled.

## 3. Real hook payloads through `proxy/wrapper.js` (`scripts/hook-payload-e2e.sh`)

Payloads use the documented Claude Code shapes (`hook_event_name`, `session_id`,
`cwd`; prompt text in `prompt`, tool name/input/response on PostToolUse,
`transcript_path` and `reason` on SessionEnd).

Observed:

- UserPromptSubmit persisted exactly one prompt row with the payload text when sent
  in the `prompt` field, and mapped `cwd` to `project_path`;
- PostToolUse delivered **twice** produced **one** queue row (the tool-input hash
  dedupe) and the `tool_input` JSON was stored verbatim; one observation was
  extracted from it;
- SessionEnd marked the conversation `completed` and produced a summary row;
- a delivery against an unreachable port logged `Cannot connect` and exited **0**
  instead of crashing or hanging.

Verdict: PASS for field mapping, persistence, duplicate delivery and failure.
