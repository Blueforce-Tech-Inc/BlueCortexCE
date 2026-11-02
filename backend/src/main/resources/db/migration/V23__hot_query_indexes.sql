-- =============================================================================
-- V23: composite indexes for the list/scan queries that filter on one dimension
-- and then order by time.
--
-- Why these exist: V20 added only single-column indexes (memory_scope_id,
-- conversation_id, standalone_kind). Every paged list and every refine scan
-- filters on one of those first and then sorts by created_at_epoch DESC, id DESC.
-- With a single-column index PostgreSQL still has to sort the filtered set,
-- and the plan flips to a time-ordered scan over the whole table once the
-- filter is not selective. A composite index makes the filter and the ordering
-- one index range.
--
-- ADD-only: no existing index is dropped here. The single-column indexes remain
-- useful for callers that filter without ordering, and dropping them would be a
-- separate, measurable decision.
--
-- IF NOT EXISTS keeps this re-runnable after a manual partial application
-- (Flyway guarantees all-or-nothing per migration; this is for operators who
-- had to apply statements by hand before re-running).
-- =============================================================================

-- Paginated observation lists / refine scans: scope filter + newest-first order.
CREATE INDEX IF NOT EXISTS idx_observations_scope_created
    ON mem_observations (memory_scope_id, created_at_epoch DESC, id DESC);

-- Type-filtered lists (extraction history, DLQ scans): type + newest-first order.
CREATE INDEX IF NOT EXISTS idx_observations_type_created
    ON mem_observations (type, created_at_epoch DESC, id DESC);

-- Legacy project-path queries (V1-era columns still used by import/stats paths).
CREATE INDEX IF NOT EXISTS idx_observations_project_created
    ON mem_observations (project_path, created_at_epoch DESC, id DESC);

-- Session list for one project (Viewer project page, context cache owner lookup).
CREATE INDEX IF NOT EXISTS idx_sessions_project_started
    ON mem_sessions (project_path, started_at_epoch DESC, id DESC);

COMMENT ON INDEX idx_observations_scope_created IS
    'Scope + created_at ordering: serves findAllPaged and the refine/quality scans.';
COMMENT ON INDEX idx_observations_type_created IS
    'Type + created_at ordering: serves extraction history and DLQ type scans.';
COMMENT ON INDEX idx_observations_project_created IS
    'Legacy project_path + created_at ordering for import and stats queries.';
COMMENT ON INDEX idx_sessions_project_started IS
    'Project + started_at ordering: serves findByProjectPathOrderByStartedAtEpochDesc.';
