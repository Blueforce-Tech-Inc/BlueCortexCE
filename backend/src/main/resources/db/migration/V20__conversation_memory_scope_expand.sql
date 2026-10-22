-- V20: Conversation / Memory Scope identity — additive expansion only.
--
-- Part of docs/drafts/conversation-memory-scope-refactor-plan.md §8.4 step 2.
--
-- RULES THIS MIGRATION OBEYS
--   * Additive only. No column, constraint, index or row is dropped here.
--     The legacy single-column UNIQUE (content_session_id) and the legacy
--     external-id foreign keys all survive V20 on purpose: they are the
--     concurrency guard during the window where old binaries still run.
--   * Every new column is nullable. Nothing is backfilled here; bulk data
--     movement belongs to the resumable Java runner (V21 validates, not fills).
--   * Constraint names used for *existing* objects are resolved from
--     pg_constraint at execution time, never assumed from earlier migration text.
--   * All identity/scope text comparisons are COLLATE "C" so that equality is
--     byte-exact and independent of the database's locale provider.
--
-- WHAT IS *NOT* HERE
--   NOT NULL constraints, composite foreign keys, the composite unique
--   (platform_source, content_session_id), removal of the legacy unique, and all
--   CHECK constraints that need every row migrated first. Those are V21/V22 and
--   are gated on a clean conflict ledger.

-- ============================================================================
-- 1. mem_memory_scopes — the ownership/query partition
-- ============================================================================

CREATE TABLE IF NOT EXISTS mem_memory_scopes (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_type       VARCHAR(32) COLLATE "C" NOT NULL,
    scope_key        TEXT        COLLATE "C" NOT NULL,
    -- SHA-256 of scope_key's UTF-8 bytes. Fixed width so the uniqueness index
    -- stays small even for very long project paths. This digest is a *lookup
    -- accelerator*, never the identity: the resolver re-compares scope_key with
    -- COLLATE "C" and fails closed on a digest hit with a different key.
    scope_key_sha256 BYTEA       NOT NULL,
    display_name     TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT mem_memory_scopes_sha256_len CHECK (octet_length(scope_key_sha256) = 32),
    CONSTRAINT mem_memory_scopes_type_check CHECK (scope_type IN ('project', 'manual_default')),
    -- A project scope needs a real path; the manual default needs its fixed key.
    -- The service layer additionally rejects all-whitespace paths but must never
    -- trim a non-blank path, so the DB only enforces "not empty".
    CONSTRAINT mem_memory_scopes_key_shape CHECK (
        (scope_type = 'project'        AND scope_key <> '')
     OR (scope_type = 'manual_default' AND scope_key = 'manual-default-v1')
    )
);

-- Uniqueness is scoped by type: a project path "manual-default-v1" must not
-- collide with the manual-default scope.
CREATE UNIQUE INDEX IF NOT EXISTS ux_memory_scopes_type_sha256
    ON mem_memory_scopes (scope_type, scope_key_sha256);

COMMENT ON TABLE  mem_memory_scopes IS
    'Memory Scope: long-lived ownership/query partition for observations. Not an auth or tenant boundary.';
COMMENT ON COLUMN mem_memory_scopes.scope_key IS
    'Exact, uncanonicalised key. project: the stored project_path verbatim. manual_default: manual-default-v1.';
COMMENT ON COLUMN mem_memory_scopes.scope_key_sha256 IS
    'SHA-256(UTF-8(scope_key)). Lookup accelerator only; identity requires an exact "C" comparison of scope_key.';

-- ============================================================================
-- 2. mem_observations — relationship truth + record kind
-- ============================================================================

ALTER TABLE mem_observations ADD COLUMN IF NOT EXISTS memory_scope_id         UUID;
ALTER TABLE mem_observations ADD COLUMN IF NOT EXISTS conversation_id         UUID;
ALTER TABLE mem_observations ADD COLUMN IF NOT EXISTS standalone_kind         VARCHAR(32);

COMMENT ON COLUMN mem_observations.memory_scope_id IS
    'Every observation has exactly one scope. Assigned by the backfill runner, then V21 enforces NOT NULL.';
COMMENT ON COLUMN mem_observations.conversation_id IS
    'Internal conversation relation (mem_sessions.id). NULL only for typed standalone rows.';
COMMENT ON COLUMN mem_observations.standalone_kind IS
    'NULL for conversation-owned rows; manual|extraction|dlq for rows that merely borrow a legacy wire key.';

-- Indexes for the new relationship columns. Partial indexes keep the common
-- "conversation-owned rows" scan small; the standalone index supports the
-- compat-window protection queries that must exclude those rows.
CREATE INDEX IF NOT EXISTS idx_observations_memory_scope
    ON mem_observations (memory_scope_id) WHERE memory_scope_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_observations_conversation
    ON mem_observations (conversation_id) WHERE conversation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_observations_standalone_kind
    ON mem_observations (standalone_kind) WHERE standalone_kind IS NOT NULL;

-- ============================================================================
-- 3. mem_summaries / mem_user_prompts — internal conversation relation
-- ============================================================================

ALTER TABLE mem_summaries    ADD COLUMN IF NOT EXISTS conversation_id UUID;
ALTER TABLE mem_user_prompts ADD COLUMN IF NOT EXISTS conversation_id UUID;

-- Internal idempotency key for the durable SessionEnd summary job. Historical
-- summaries keep NULL on purpose: binding them to a job by count/similarity
-- would fabricate lineage the data never had.
ALTER TABLE mem_summaries ADD COLUMN IF NOT EXISTS summary_job_key VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_summaries_conversation
    ON mem_summaries (conversation_id) WHERE conversation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_user_prompts_conversation
    ON mem_user_prompts (conversation_id) WHERE conversation_id IS NOT NULL;

-- At most one summary per (conversation, durable job). Partial so the many
-- unkeyed historical rows are unaffected. This bounds a single job's output;
-- it does NOT cap how many historical summaries a conversation may have.
CREATE UNIQUE INDEX IF NOT EXISTS ux_summaries_conversation_job_key
    ON mem_summaries (conversation_id, summary_job_key)
    WHERE summary_job_key IS NOT NULL;

COMMENT ON COLUMN mem_summaries.summary_job_key IS
    'Internal only, never serialised. ''session-summary-v1'' for durable SessionEnd results; NULL for history.';

-- ============================================================================
-- 4. mem_pending_messages — durable summary job carrier
-- ============================================================================

ALTER TABLE mem_pending_messages ADD COLUMN IF NOT EXISTS job_key                VARCHAR(64);
ALTER TABLE mem_pending_messages ADD COLUMN IF NOT EXISTS summary_input_snapshot JSONB;
ALTER TABLE mem_pending_messages ADD COLUMN IF NOT EXISTS summary_input_sha256   BYTEA;
ALTER TABLE mem_pending_messages ADD COLUMN IF NOT EXISTS result_summary_id      UUID;
ALTER TABLE mem_pending_messages ADD COLUMN IF NOT EXISTS result_deleted_at      TIMESTAMPTZ;

-- The status CHECK must be replaced so 'summarize' jobs may carry the new
-- non-terminal 'result_persisted'. The existing name is discovered from the
-- catalogue: V1 created an inline CHECK, V3 dropped it by an assumed name and
-- added one explicitly, so the deployed name cannot be taken on faith.
DO $$
DECLARE
    v_conname text;
BEGIN
    SELECT c.conname INTO v_conname
    FROM pg_constraint c
    WHERE c.connamespace = 'public'::regnamespace
      AND c.conrelid = 'mem_pending_messages'::regclass
      AND c.contype = 'c'
      AND pg_get_constraintdef(c.oid) ILIKE '%status%'
      AND pg_get_constraintdef(c.oid) ILIKE '%pending%'
    LIMIT 1;

    IF v_conname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE mem_pending_messages DROP CONSTRAINT %I', v_conname);
    END IF;
END $$;

ALTER TABLE mem_pending_messages
    ADD CONSTRAINT mem_pending_messages_status_check
    CHECK (
        -- Observation jobs keep the V3 semantics exactly, including 'skipped'
        -- (the LLM decided the tool event was trivial).
        (message_type = 'observation'
         AND status IN ('pending', 'processing', 'processed', 'failed', 'skipped'))
        -- Summary jobs may additionally hold a persisted result that is not yet
        -- finalised. 'result_persisted' is NOT terminal: deletion must still be
        -- refused until finalisation completes.
     OR (message_type = 'summarize'
         AND status IN ('pending', 'processing', 'processed', 'failed', 'skipped', 'result_persisted'))
    );

COMMENT ON COLUMN mem_pending_messages.job_key IS
    'Durable job idempotency key. ''session-summary-v1'': at most one canonical SessionEnd job per conversation.';
COMMENT ON COLUMN mem_pending_messages.summary_input_snapshot IS
    'Immutable rendered LLM request for a summary job. SENSITIVE (same class as observation bodies): never log or export.';
COMMENT ON COLUMN mem_pending_messages.summary_input_sha256 IS
    'SHA-256 of the canonical snapshot serialisation. Retries must match before calling the LLM.';
COMMENT ON COLUMN mem_pending_messages.result_summary_id IS
    'Result pointer set atomically with the summary row; survives crash recovery so no second LLM call happens.';
COMMENT ON COLUMN mem_pending_messages.result_deleted_at IS
    'Tombstone written when the keyed result is deleted; the terminal job row is retained.';

-- One canonical durable job per conversation. Partial on message_type so the
-- ordinary observation queue is untouched, and on job_key so legacy rows in the
-- compatibility window do not collide with each other.
CREATE UNIQUE INDEX IF NOT EXISTS ux_pending_summary_job_key
    ON mem_pending_messages (session_db_id, message_type, job_key)
    WHERE job_key IS NOT NULL AND message_type = 'summarize';

CREATE INDEX IF NOT EXISTS idx_pending_result_summary
    ON mem_pending_messages (result_summary_id) WHERE result_summary_id IS NOT NULL;

-- ============================================================================
-- 5. Migration audit / conflict ledger / runner checkpoints
-- ============================================================================
-- These tables are the only durable record of what the backfill decided. They
-- are deliberately narrow (ids, codes, counts) and must never hold observation
-- bodies, embeddings, project paths or external session ids.

CREATE TABLE IF NOT EXISTS mem_migration_runs (
    run_id             UUID        PRIMARY KEY,
    purpose            VARCHAR(64) NOT NULL,
    source_schema_version VARCHAR(32),
    status             VARCHAR(32) NOT NULL,
    started_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at        TIMESTAMPTZ,
    operator_label     VARCHAR(128),
    notes              TEXT,
    CONSTRAINT mem_migration_runs_status_check
        CHECK (status IN ('running', 'completed', 'aborted', 'failed'))
);

-- One row per (run, step, shard). The last committed cursor here is the ONLY
-- resume authority; a local progress file is diagnostic and must not be trusted.
CREATE TABLE IF NOT EXISTS mem_migration_checkpoints (
    id                 BIGSERIAL PRIMARY KEY,
    run_id             UUID        NOT NULL REFERENCES mem_migration_runs(run_id) ON DELETE CASCADE,
    step               VARCHAR(64) NOT NULL,
    shard              VARCHAR(64) NOT NULL DEFAULT '_default',
    last_key           TEXT,
    rows_processed     BIGINT      NOT NULL DEFAULT 0,
    rows_changed       BIGINT      NOT NULL DEFAULT 0,
    batches_committed  INT         NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_migration_checkpoints UNIQUE (run_id, step, shard)
);

-- Row-level audit: what each migrated row was before and after. Reversible by
-- construction, so a disputed mapping can be explained rather than guessed.
CREATE TABLE IF NOT EXISTS mem_migration_audit (
    id                 BIGSERIAL PRIMARY KEY,
    run_id             UUID    NOT NULL,
    batch_no           INT     NOT NULL,
    table_name         VARCHAR(64) NOT NULL,
    row_id             UUID    NOT NULL,
    resolution_code    VARCHAR(64) NOT NULL,
    old_conversation_id UUID,
    old_content_session_id VARCHAR(255),
    old_platform_source    VARCHAR(50),
    old_source         TEXT,
    old_project_path   TEXT,
    new_conversation_id UUID,
    new_memory_scope_id UUID,
    new_platform_source VARCHAR(50),
    new_standalone_kind VARCHAR(32),
    new_source         TEXT,
    reviewer_label     VARCHAR(128),
    reviewed_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_migration_audit_row
    ON mem_migration_audit (table_name, row_id);
CREATE INDEX IF NOT EXISTS idx_migration_audit_run
    ON mem_migration_audit (run_id, batch_no);

-- Conflict ledger: every row the classifier could NOT prove, with the reason.
-- Nothing in the ledger is ever auto-resolved; the constraints in V21 are only
-- created when this table is empty or every row carries an explicit resolution.
CREATE TABLE IF NOT EXISTS mem_migration_conflicts (
    id                 BIGSERIAL PRIMARY KEY,
    run_id             UUID,
    table_name         VARCHAR(64) NOT NULL,
    row_id             UUID    NOT NULL,
    conflict_code      VARCHAR(64) NOT NULL,
    detail             TEXT,
    status             VARCHAR(32) NOT NULL DEFAULT 'open',
    resolution_code    VARCHAR(64),
    reviewer_label     VARCHAR(128),
    resolved_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT mem_migration_conflicts_status_check
        CHECK (status IN ('open', 'investigating', 'resolved', 'accepted_legacy'))
);
-- One ledger entry per (table, row, reason). The runner upserts onto this key so a
-- resumed or repeated run refreshes the entry instead of appending duplicates.
CREATE UNIQUE INDEX IF NOT EXISTS ux_migration_conflicts_row_code
    ON mem_migration_conflicts (table_name, row_id, conflict_code);
CREATE INDEX IF NOT EXISTS idx_migration_conflicts_open
    ON mem_migration_conflicts (conflict_code) WHERE status IN ('open', 'investigating');
CREATE INDEX IF NOT EXISTS idx_migration_conflicts_row
    ON mem_migration_conflicts (table_name, row_id);

-- Archive of parent rows proven to be compatibility technical buckets before
-- they are removed in V21 cleanup. Full row snapshots, access-restricted.
CREATE TABLE IF NOT EXISTS mem_compat_parent_archive (
    id                 BIGSERIAL PRIMARY KEY,
    run_id             UUID,
    session_id         UUID NOT NULL,
    content_session_id VARCHAR(255) NOT NULL,
    platform_source    VARCHAR(50),
    status             VARCHAR(50),
    project_path       TEXT,
    bucket_kind        VARCHAR(32) NOT NULL,
    row_snapshot       JSONB NOT NULL,
    archived_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT mem_compat_parent_archive_kind_check
        CHECK (bucket_kind IN ('manual', 'extraction', 'dlq'))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_compat_parent_archive_session
    ON mem_compat_parent_archive (session_id);

COMMENT ON TABLE mem_migration_audit IS
    'Reversible per-row record of what the identity backfill changed. Retention: keep through the rollback window.';
COMMENT ON TABLE mem_migration_conflicts IS
    'Fail-closed ledger. Constraints are only added once this is empty or every row is explicitly resolved.';
COMMENT ON TABLE mem_compat_parent_archive IS
    'Snapshots of technical parent sessions proven to be manual/extraction/dlq buckets before V21 cleanup.';

-- ============================================================================
-- 6. Feature flag state (read by the bridge at write time)
-- ============================================================================
CREATE TABLE IF NOT EXISTS mem_identity_flags (
    flag_name   VARCHAR(64) PRIMARY KEY,
    enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO mem_identity_flags (flag_name, enabled) VALUES
    ('conversation_identity_v2', FALSE),
    ('memory_scope_read_v2',     FALSE)
ON CONFLICT (flag_name) DO NOTHING;

COMMENT ON TABLE mem_identity_flags IS
    'Cutover flags. Both stay FALSE through V20/backfill; flipping them is a separate, gated release step.';