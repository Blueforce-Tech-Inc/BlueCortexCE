-- =============================================================================
-- V25: composite index for the type-filtered list, which was the one measured
-- path that degraded with table size.
--
-- Why this exists: at 100k observations `type_filter` (project + `type` +
-- newest-first, 20 rows) measured p95 29.38 ms against 4.94 ms at 1k rows. The
-- plan was `Limit -> Sort -> Hash Join -> Bitmap Heap Scan (type)` over every
-- row of the type (20% of the table), then a top-N sort: the type index alone
-- gives the filter but not the scope predicate, so the planner could not stop
-- early. V23's `idx_observations_type_created` therefore only helps a global
-- (cross-project) type scan.
--
-- Adding `memory_scope_id` as the leading column lets one index range satisfy
-- both predicates and still deliver rows in `created_at_epoch DESC` order, so
-- the limit stops the scan after the first page instead of sorting the type's
-- whole row set.
--
-- The scope predicate in `ObservationRepository` was rewritten from
-- `IN (SELECT ... WHERE a OR b)` to `IN (SELECT ... UNION ALL SELECT ...)`
-- in the same change. The `OR` form made the subquery a hash-join member, so
-- the scope columns were never available as index conditions; the UNION ALL
-- form is semantically identical (the `manual-memories` sentinel still matches
-- both the project scope and the manual-default scope) and lets the planner
-- treat it as a semijoin that stops at the limit.
--
-- Rollback:
--   DROP INDEX IF EXISTS idx_observations_scope_type_created;
-- Building this index is a plain `CREATE INDEX` (ShareLock): reads continue,
-- writes to mem_observations are blocked for the build. On a 100k-row table
-- the measured build is ~0.4 s; on a much larger table use
-- `CREATE INDEX CONCURRENTLY` (see docs/archive/2026-11-04_migration-cost-100k.md).
-- It is ADD-only; `idx_observations_type_created` stays for global type scans.
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_observations_scope_type_created
    ON mem_observations (memory_scope_id, type, created_at_epoch DESC, id DESC);

COMMENT ON INDEX idx_observations_scope_type_created IS
    'Scope + type + created_at ordering: serves the paged type filter behind /api/search?type=.';
