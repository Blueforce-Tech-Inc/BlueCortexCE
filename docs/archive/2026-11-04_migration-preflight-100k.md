# Migration preflight — calibrated on a 100k-row database

**Date:** 2026-11-04
**Tool:** `scripts/migration-preflight.sh`
**Database:** `cortexce_load`, PostgreSQL 16.12 + pgvector, schema V25
**Why:** V24 (`search_tokens` generated column + GIN) is the expensive migration
shape in this service: `ADD COLUMN ... GENERATED ... STORED` rewrites the table
under `ACCESS EXCLUSIVE`, and a plain `CREATE INDEX` holds `SHARE`. Operators need
a number before the change window, not after.

## What the tool reports

For a given database it prints the row count and the heap/index split of
`mem_observations`, then estimates two plans:

| Plan | Work | Lock / blocking window |
|------|------|------------------------|
| Exact (as written) | full table rewrite + plain GIN build | `ACCESS EXCLUSIVE` for rewrite, `SHARE` for index; reads and writes stopped for the whole window |
| Phased online | metadata-only `ADD COLUMN`, batched backfill, `CREATE INDEX CONCURRENTLY`, `VACUUM ANALYZE` | longest block is the metadata-only `ADD COLUMN` (~1 ms) |

It then names the path the database can afford. Thresholds are
`--block-threshold-seconds` (default 30 s, "a maintenance window is required")
and `--hard-threshold-seconds` (default 300 s, "do not run the exact plan").

## Calibration (100,001 rows, 213.4 MB total = 97.7 MB heap + 115.7 MB indexes)

The calibration clones the real table **with every index**, drops the generated
column, copies real rows, and times the two plans. Cloning the index set matters:
an earlier synthetic scratch table with only a primary key measured 1,307 ms for a
rewrite that costs 6.6 s on the real shape — a 5x under-report.

Final verification run (`--calibrate --calibrate-rows 100000`):

| Segment | Measured | Model | Error |
|---------|----------|-------|-------|
| Full rewrite | 7,201 ms | 7,655 ms | +6.3% |
| Phased add | 200 ms (psql startup dominated) | 1 ms | — |
| Phased backfill (10 × 10k) | 8,690 ms | 10,189 ms | — |
| `CREATE INDEX CONCURRENTLY` | 2,925 ms | 1,400 ms | — |
| Phased total | 11,815 ms | 11,590 ms | −1.9% |

Both plans are inside the ±20% band. The concurrent-index estimate is the noisiest
segment (1,400–2,925 ms across runs); the phased total still landed within 2%
because the backfill dominates.

## Coefficients in the script (per 100,000 rows, V25 index set)

```
full rewrite          6,657 ms
plain GIN build         998 ms
phased backfill      10,189 ms   (10k batches)
CREATE INDEX CONCURRENTLY 1,400 ms
plain column add        0.89 ms   (constant)
```

## Projections and the path decision

Linear in rows at this row width and index count:

| Rows | Exact blocked window | Phased total | Recommendation |
|------|----------------------|--------------|----------------|
| 100k | 7.7 s | 11.6 s | direct, short maintenance window |
| 392k | 30 s | 45 s | crossing point: maintenance window required |
| 1M | 76 s | 116 s | maintenance window (announce ≥ 2× the window) |
| 3.9M | 300 s | 453 s | crossing point: exact plan no longer acceptable |
| 10M | 766 s | 1,159 s | phased online only |

For the current schema (V25, V24 already applied) this is the template for the
next table-rewriting migration. If `flyway version < 24`, V24 itself is the
pending migration and the same decision applies. At 100k rows the exact plan is
safe; the phased plan is not worth the operational complexity. Above ~392k rows
the exact plan needs an announced window; above ~3.9M rows it must be phased.

## Caveats

- Row width and index count drive the result. Re-run `--calibrate` after adding
  indexes or wide columns; the script fails if either plan is outside ±20%.
- `psql` process startup adds ~40–60 ms per timed statement; negligible at 7 s,
  dominant for the metadata-only `ADD COLUMN` (reported as ~200 ms).
- Calibration creates and drops `_migration_preflight_calibration`. Use a
  throwaway database, never production.
- The estimate covers the rewrite and index build only. A follow-up
  `VACUUM (ANALYZE)` on a 100k table is sub-second; on 1M it is part of the
  phased maintenance budget, not the blocking window.
- Lock levels were measured in round 16: `ADD COLUMN ... GENERATED` took
  `ACCESS EXCLUSIVE` and blocked a concurrent write for the full 4.3 s;
  `CREATE INDEX` took `SHARE` and blocked a write for 209 ms.

## Usage

```bash
scripts/migration-preflight.sh \
  --database-url postgresql://db-host:5432/cortexce \
  --user postgres --password "$PGPASSWORD" \
  --table mem_observations \
  --calibrate --calibrate-rows 100000
```

Without `--calibrate` the script only reads row counts and sizes and applies the
committed coefficients; it never writes to the target table.
