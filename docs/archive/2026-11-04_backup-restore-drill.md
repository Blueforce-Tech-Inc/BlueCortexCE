# Backup / restore drill on a 100k-observation database

- Date: 2026-11-04 05:28
- Source: `cortexce_r16_backup_source` on claude-mem-test-pg:5433 (137 MB, 100,000 observations, Flyway head V25)
- Container: `claude-mem-test-pg` (PostgreSQL 16 + pgvector)
- Command: `SOURCE_DB=cortexce_r16_backup_source scripts/backup-restore-drill.sh`

## Procedure

```bash
# Script (committed): dumps, restores into a new database, boots the service
# against the copy to run Flyway validation, compares counts, then spot-checks
# a Chinese lexical query and the observation it returns.
SOURCE_DB=<database> scripts/backup-restore-drill.sh
# KEEP=1 keeps the restored database and the dump for inspection.
```

The drill is self-contained: it creates an empty target database, restores into it,
starts the packaged jar with `SPRING_DATASOURCE_URL` pointing at the copy, waits for
`Successfully validated N migration`, and drops both the copy and the dump on exit
unless `KEEP=1`.

## Timings

| step | measured |
|---|---:|
| `pg_dump -Fc -Z6` | 478 ms |
| dump size | 4,211,990 B (4.0 MiB from 137 MB, ~32× compression) |
| `pg_restore --no-owner --no-privileges --exit-on-error` | 5,744 ms |
| Flyway validation (service boot) | 2,176 ms |
| total | 8,398 ms |

Restore dominates: `pg_restore` rebuilds the 28 indexes on `mem_observations`
(including `idx_obs_search_tokens` and V25's `idx_observations_scope_type_created`)
in series, which is the expected cost of a logical restore.

## Checks

| check | result |
|---|---|
| `mem_observations` count | 100,000 = 100,000 |
| `mem_sessions` count | 0 = 0 |
| `mem_pending_messages` count | 0 = 0 |
| `mem_memory_scopes` count | 2 = 2 |
| database size | 137 MB source, 137 MB restore |
| `flyway_schema_history` rows / max version | 23 / V25 on both |
| Flyway validate against the copy | passed (23 migrations, no pending) |
| Chinese query `检索` (`search_tokens @@ plainto_tsquery('simple', ...)`) | 100,000 hits on both |
| probe observation `a697ad9d-77f5-4fa8-8a7e-4980a9f5b4c2` | title restored: `迁移测试标题 100000 migration row` |

Verdict: PASS. Counts, schema history and the Chinese lexical path agree between
the source and the restored copy, and the restored copy validates as a current
schema without applying anything.
