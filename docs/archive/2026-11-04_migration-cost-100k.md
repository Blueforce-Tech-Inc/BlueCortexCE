# V24 migration cost on 100,000 existing rows

- Date: 2026-11-04
- Host: `claude-mem-test-pg` (PostgreSQL 16.12), database `cortexce_r16_migration_cost`
- Fixture: schema migrated to **V23** first, then 100,000 `mem_observations` rows
  (mixed Chinese/English title + content, 5 types, one project scope, 100 sessions).
  Heap 60 MB, table + indexes 141 MB before V24.
- Goal: answer two questions with numbers — what V24 costs on existing data,
  and whether it can run online.

## Exact V24 (`ALTER TABLE ... ADD COLUMN ... GENERATED ... STORED` + plain GIN)

| step | measured time | lock | writes during step |
|---|---:|---|---|
| `DROP COLUMN search_vector` (drops its GIN too) | < 20 ms | `ACCESS EXCLUSIVE` (metadata only) | allowed |
| `ADD COLUMN search_tokens ... GENERATED ... STORED` | **4,337 ms** | `ACCESS EXCLUSIVE` for the **whole rewrite** | **blocked** |
| `CREATE INDEX idx_obs_search_tokens USING gin` | **731 ms** | `SHARE` | **blocked** (observed insert 209 ms) |
| function `cortex_tokens` + comments | < 5 ms | none | allowed |

Total Flyway migration time on 100k rows: **≈ 5.1 s**, of which **≈ 5.07 s blocks
both reads and writes** (the rewrite holds `ACCESS EXCLUSIVE`, the index build
holds `SHARE`, which conflicts with `ROW EXCLUSIVE`).

Empirical block evidence, same database:

- Concurrent `INSERT` started 0.5 s into the `ADD COLUMN` rewrite: **3,324 ms**
  (waited out the remaining rewrite). Writes are **not** merely slowed; they stop.
- Concurrent `INSERT` started 0.4 s into the plain GIN build: **209 ms**.
- `pg_locks` sampled every 50 ms during the GIN build: `ShareLock` on
  `mem_observations` (plus `ShareUpdateExclusiveLock` from autovacuum).
- After migration: total 121 MB, heap 72 MB (rewrite compacts the heap).

At this size V24 is a 5-second maintenance window. Extrapolating linearly, 1M
rows is ≈ 45–50 s of blocked access — still a maintenance window, not an online
migration.

## Variant A — generated column + `CREATE INDEX CONCURRENTLY`

Same `ADD COLUMN` (cannot be made concurrent for a `STORED` generated column),
then build the GIN index concurrently.

| step | measured time | lock | writes during step |
|---|---:|---|---|
| `ADD COLUMN ... GENERATED ... STORED` | 4,391 ms | `ACCESS EXCLUSIVE` | blocked |
| `CREATE INDEX CONCURRENTLY` | **909 ms** | `ShareUpdateExclusiveLock` | **allowed** (insert 5.4 ms) |

`pg_locks` sampled during the concurrent build: `ShareUpdateExclusiveLock` only —
no `ShareLock`. Total **≈ 5.3 s**, but the rewrite still blocks for ≈ 4.4 s.

Conclusion: `CONCURRENTLY` removes the index-build blocking (731 → 909 ms, writes
stay online) but **cannot remove the generated-column rewrite**, which is the
dominant cost. Note that a failed concurrent build leaves an `INVALID` index; the
migration must check `pg_index.indisvalid` before trusting it.

## Variant B — phased online plan (no long lock at all)

Maintain the column in application/trigger code instead of `GENERATED ... STORED`:
add a plain nullable `search_tokens` column (metadata only), backfill in batches,
then build the index concurrently.

| step | measured time | lock | writes |
|---|---:|---|---|
| `ADD COLUMN search_tokens tsvector` (plain) | **0.89 ms** | `ACCESS EXCLUSIVE` (brief) | blocked < 1 ms |
| backfill 10 × 10,000 rows (batched `UPDATE`) | **7,187 ms** total (~719 ms / 10k) | none between batches | allowed between batches |
| `CREATE INDEX CONCURRENTLY` | **734 ms** | `ShareUpdateExclusiveLock` | allowed |

Total wall time **≈ 8 s**, longest single blocking event **0.89 ms**.
Cost: the column is no longer generated, so every insert path must populate it
(a trigger is the smallest change; app-side population avoids the trigger but
needs a code change). The batched backfill is also slower (7.2 s vs 4.3 s) because
it writes WAL and bloats the heap; a follow-up `VACUUM` is prudent.

## Recommendation

- **≤ 100k rows (current target):** ship V24 as written and schedule a ~5 s
  maintenance window. It is the simplest and gives a true generated column, so
  the write path cannot forget to populate it.
- **≥ 1M rows, or a zero-downtime requirement:** use Variant B. Keep the column
  plain, populate it on write, backfill in 10k batches, and build the index
  `CONCURRENTLY`. Verify `indisvalid` afterwards, then `VACUUM ANALYZE`.
- Variant A is the middle option: it keeps the generated column but only removes
  the smaller half of the blocking time.

## Reproduction

```bash
# 1. database at V23, then seed 100k rows
SPRING_FLYWAY_TARGET=23 SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/cortexce_r16_migration_cost \
SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=123456 SERVER_PORT=37901 \
java -jar backend/target/cortex-ce-*.jar   # stop once flyway_schema_history max(version)=23

# 2. run V24 exactly, timed
psql "$PGURL" -f backend/src/main/resources/db/migration/V24__chinese_lexical_search.sql

# 3. or only the index concurrently
psql "$PGURL" -c "CREATE INDEX CONCURRENTLY idx_obs_search_tokens ON mem_observations USING gin (search_tokens)"
```

Raw timings for this run: `ADD COLUMN` 4,337 ms / `CREATE INDEX` 731 ms (exact V24);
`ADD COLUMN` 4,391 ms / `CONCURRENTLY` 909 ms (Variant A); plain column 0.89 ms /
backfill 7,187 ms / `CONCURRENTLY` 734 ms (Variant B).
