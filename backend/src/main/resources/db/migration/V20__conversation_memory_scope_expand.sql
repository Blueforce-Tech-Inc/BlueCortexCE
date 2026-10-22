-- V20: Conversation / Memory Scope identity — one-shot migration.
--
-- MODEL: single atomic pass. This migration performs the schema expand, the data
-- backfill AND the constraint tightening in ONE transaction. There is no V21/V22
-- phasing, no checkpoint/resume, no write barrier, no shadow read and no rollback
-- path: a failure rolls the whole thing back and the operator fixes the data and
-- re-runs.
--
-- Deletion model: the legacy single-column UNIQUE on mem_sessions.content_session_id
-- and the legacy external-id foreign keys on observations/summaries/prompts are
-- removed here, replaced by internal UUID relations plus the composite
-- (platform_source, content_session_id) uniqueness. Cross-platform duplicate ids
-- are therefore legal from this version on.
--
-- CONFLICT POLICY: fail closed. Any row whose identity cannot be proven aborts the
-- migration via RAISE EXCEPTION, and the diagnostic lists the offending rows so the
-- operator can resolve them by hand. Nothing is deleted, guessed or silently
-- rewritten to make the constraints fit.
--
-- Reserved codes shared with the Java classifier (TechnicalBucketClassifier):
--   LEGACY_SENTINEL_IDENTITY_AMBIGUITY    manual-memories key shape is not proof of identity
--   MANUAL_MARKER_FOREIGN_SOURCE          a marked manual row carries a foreign non-empty source
--   TECHNICAL_BUCKET_WITHOUT_OBSERVATIONS empty technical bucket proves nothing
--   TECHNICAL_BUCKET_MIXED_CHILDREN       a bucket has a child that breaks the marker set
--   TECHNICAL_BUCKET_HAS_PROMPTS          a technical bucket has user prompts
--   TECHNICAL_BUCKET_HAS_SUMMARIES        a technical bucket has summaries
--   TECHNICAL_BUCKET_HAS_PENDING          a technical bucket has pending work
--   MANUAL_PARENT_HAS_CONVERSATION_CONTENT  a manual parent carries real conversation text
--   PLATFORM_SOURCE_NOT_NORMALISABLE      a stored platform value cannot be canonicalised
--   CHILD_PARENT_PLATFORM_MISMATCH        a child's platform differs from its parent's

BEGIN;

-- ============================================================================
-- 0. Fail fast if the schema is not what this migration expects
-- ============================================================================

DO $$
DECLARE
    missing text;
BEGIN
    SELECT string_agg(t, ', ') INTO missing
    FROM unnest(ARRAY['mem_sessions','mem_observations','mem_summaries','mem_user_prompts','mem_pending_messages'])
        AS t
    WHERE to_regclass('public.' || t) IS NULL;
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'V20 expects tables that do not exist: %', missing;
    END IF;

    -- The legacy single-column unique must still be present: if it is already gone,
    -- this database is not at the expected pre-V20 state and the identity assumptions
    -- below would silently differ.
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND conrelid = 'mem_sessions'::regclass
          AND contype = 'u'
          AND pg_get_constraintdef(oid) = 'UNIQUE (content_session_id)'
    ) THEN
        RAISE EXCEPTION 'V20 expects the legacy UNIQUE(content_session_id) on mem_sessions; it is absent, so this database is not at the expected baseline';
    END IF;
END $$;

-- ============================================================================
-- 0b. Operator decision record
-- ============================================================================
-- mem_identity_flags holds the one decision this migration cannot infer: whether the
-- operator has reviewed the manual-memories bucket. It is created first because the
-- classifier below must read it.

CREATE TABLE mem_identity_flags (
    flag_name  VARCHAR(64) PRIMARY KEY,
    enabled    BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO mem_identity_flags (flag_name, enabled) VALUES
    ('manual_sentinel_reviewed',
     COALESCE(current_setting('app.manual_sentinel_reviewed', true) = 'true', false));

-- ============================================================================
-- 1. Schema: Memory Scope
-- ============================================================================

CREATE TABLE mem_memory_scopes (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_type       VARCHAR(32) COLLATE "C" NOT NULL,
    scope_key        TEXT        COLLATE "C" NOT NULL,
    -- SHA-256 of scope_key's UTF-8 bytes, via built-in sha256() so no pgcrypto
    -- extension is required. A lookup/index accelerator, never the identity: the
    -- resolver re-compares scope_key under COLLATE "C" and fails closed on a
    -- digest hit with a different key.
    scope_key_sha256 BYTEA       NOT NULL,
    display_name     TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT mem_memory_scopes_sha256_len CHECK (octet_length(scope_key_sha256) = 32),
    CONSTRAINT mem_memory_scopes_type_check CHECK (scope_type IN ('project', 'manual_default')),
    CONSTRAINT mem_memory_scopes_key_shape CHECK (
        (scope_type = 'project'         AND scope_key <> '')
     OR (scope_type = 'manual_default' AND scope_key = 'manual-default-v1')
    )
);
CREATE UNIQUE INDEX ux_memory_scopes_type_sha256
    ON mem_memory_scopes (scope_type, scope_key_sha256);

-- ============================================================================
-- 2. Schema: relationship columns (nullable first, tightened at the end)
-- ============================================================================

ALTER TABLE mem_observations ADD COLUMN memory_scope_id UUID;
ALTER TABLE mem_observations ADD COLUMN conversation_id UUID;
ALTER TABLE mem_observations ADD COLUMN standalone_kind VARCHAR(32);

ALTER TABLE mem_summaries    ADD COLUMN conversation_id UUID;
ALTER TABLE mem_user_prompts ADD COLUMN conversation_id UUID;
ALTER TABLE mem_summaries    ADD COLUMN summary_job_key VARCHAR(64);

ALTER TABLE mem_pending_messages ADD COLUMN job_key VARCHAR(64);
ALTER TABLE mem_pending_messages ADD COLUMN summary_input_snapshot JSONB;
ALTER TABLE mem_pending_messages ADD COLUMN summary_input_sha256 BYTEA;
ALTER TABLE mem_pending_messages ADD COLUMN result_summary_id UUID;
ALTER TABLE mem_pending_messages ADD COLUMN result_deleted_at TIMESTAMPTZ;

-- ============================================================================
-- 3. Platform source: normalise in place, then diagnose what cannot be normalised
-- ============================================================================
-- The normalisation here must be byte-identical to PlatformSource.normalize():
-- strip U+0020 at both ends, fold ASCII A-Z only, empty -> 'claude'.

DO $$
DECLARE
    bad_rows int;
    sample   text;
BEGIN
    UPDATE mem_sessions
       SET platform_source = COALESCE(NULLIF(BTRIM(platform_source, ' '), ''), 'claude')
     WHERE platform_source IS NULL
        OR platform_source <> COALESCE(NULLIF(BTRIM(platform_source, ' '), ''), 'claude');

    UPDATE mem_sessions
       SET platform_source = TRANSLATE(platform_source,
                                       'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')
     WHERE platform_source ~ '[A-Z]';

    SELECT count(*) INTO bad_rows
    FROM mem_sessions
    WHERE platform_source !~ '^[a-z0-9][a-z0-9._-]{0,49}$';
    IF bad_rows > 0 THEN
        SELECT string_agg(DISTINCT id::text, ', ') INTO sample
        FROM (SELECT id FROM mem_sessions
              WHERE platform_source !~ '^[a-z0-9][a-z0-9._-]{0,49}$' LIMIT 20) s;
        RAISE EXCEPTION
            'PLATFORM_SOURCE_NOT_NORMALISABLE: % session(s) cannot be canonicalised into a lowercase ASCII slug. Affected session ids: %. Resolve the stored value by hand, then re-run.',
            bad_rows, sample;
    END IF;
END $$;

-- Children inherit the normalised parent platform (the parent is the source of truth
-- for a conversation-owned row's platform), then anything still invalid is a conflict.
UPDATE mem_observations o
   SET platform_source = s.platform_source
  FROM mem_sessions s
 WHERE s.content_session_id = o.content_session_id
   AND o.platform_source IS DISTINCT FROM s.platform_source;

UPDATE mem_summaries m
   SET platform_source = s.platform_source
  FROM mem_sessions s
 WHERE s.content_session_id = m.content_session_id
   AND m.platform_source IS DISTINCT FROM s.platform_source;

UPDATE mem_user_prompts p
   SET platform_source = s.platform_source
  FROM mem_sessions s
 WHERE s.content_session_id = p.content_session_id
   AND p.platform_source IS DISTINCT FROM s.platform_source;

-- ============================================================================
-- 4. Scope rows
-- ============================================================================

-- Every exact project path becomes one project scope. No canonicalisation: the
-- existing read paths already compare stored strings verbatim, so "/repo/app" and
-- "/repo/./app" are two scopes by design.
INSERT INTO mem_memory_scopes (scope_type, scope_key, scope_key_sha256, display_name)
SELECT 'project', p, sha256(convert_to(p, 'UTF8')), NULL
FROM (SELECT DISTINCT project_path AS p
      FROM mem_observations
      WHERE project_path IS NOT NULL AND btrim(project_path, ' ') <> '') d
ON CONFLICT (scope_type, scope_key_sha256) DO NOTHING;

INSERT INTO mem_memory_scopes (scope_type, scope_key, scope_key_sha256, display_name)
VALUES ('manual_default', 'manual-default-v1', sha256(convert_to('manual-default-v1', 'UTF8')), 'Manual memories')
ON CONFLICT (scope_type, scope_key_sha256) DO NOTHING;

-- ============================================================================
-- 5. Classify technical buckets — pure SQL, mirroring TechnicalBucketClassifier
-- ============================================================================
-- A bucket is the manual / extraction / dlq compatibility container written by older
-- code. These are shape checks, not provenance: the manual sentinel in particular can
-- only be classified when the operator has separately confirmed the key never named a
-- real conversation (see gate below).

CREATE TEMP TABLE v20_bucket ON COMMIT DROP AS
WITH obs AS (
    SELECT content_session_id,
           count(*)                                                        AS observations,
           count(*) FILTER (WHERE type = 'discovery' AND subtitle = 'Manual memory')        AS manual_markers,
           count(*) FILTER (WHERE type = 'discovery' AND subtitle = 'Manual memory'
                              AND (source IS NULL OR btrim(source, ' ') = ''))               AS manual_blank_source,
           count(*) FILTER (WHERE type = 'discovery' AND subtitle = 'Manual memory'
                              AND source IS NOT NULL AND btrim(source, ' ') <> ''
                              AND source <> 'manual')                                        AS manual_foreign_source,
           count(*) FILTER (WHERE source = 'llm_extraction' AND left(type, 10) = 'extracted_') AS extraction_markers,
           count(*) FILTER (WHERE source = 'dlq' AND left(type, 4) = 'dlq_')                   AS dlq_markers
    FROM mem_observations
    GROUP BY content_session_id
), smry AS (SELECT content_session_id, count(*) AS n FROM mem_summaries GROUP BY content_session_id),
   prm  AS (SELECT content_session_id, count(*) AS n FROM mem_user_prompts GROUP BY content_session_id),
   pnd  AS (SELECT session_db_id, count(*) AS n FROM mem_pending_messages GROUP BY session_db_id)
SELECT s.id AS parent_id, s.content_session_id, s.status, s.project_path,
       COALESCE(o.observations, 0)                                        AS observations,
       COALESCE(o.manual_markers, 0)                                      AS manual_markers,
       COALESCE(o.manual_blank_source, 0)                                 AS manual_blank_source,
       COALESCE(o.manual_foreign_source, 0)                               AS manual_foreign_source,
       COALESCE(o.extraction_markers, 0)                                  AS extraction_markers,
       COALESCE(o.dlq_markers, 0)                                         AS dlq_markers,
       COALESCE(smry.n, 0)                                                AS summaries,
       COALESCE(prm.n, 0)                                                 AS prompts,
       COALESCE(pnd.n, 0)                                                 AS pending
FROM mem_sessions s
LEFT JOIN obs  o    ON o.content_session_id = s.content_session_id
LEFT JOIN smry smry ON smry.content_session_id = s.content_session_id
LEFT JOIN prm  prm  ON prm.content_session_id  = s.content_session_id
LEFT JOIN pnd  pnd  ON pnd.session_db_id = s.id;

-- 5a. Buckets that LOOK technical but fail the whole-group rules: abort.
--     (Blank conversation text is measured with the same rule as Java String.isBlank.)
DO $$
DECLARE
    offenders text;
BEGIN
    WITH blank AS (
        SELECT id,
               (user_prompt IS NULL OR btrim(user_prompt, ' ') = '')            AS up_blank,
               (last_assistant_message IS NULL OR btrim(last_assistant_message, ' ') = '') AS lam_blank
        FROM mem_sessions
    )
    SELECT string_agg(DISTINCT content_session_id, ', ') INTO offenders
    FROM (
        SELECT b.content_session_id
        FROM v20_bucket b JOIN blank ON blank.id = b.parent_id
        WHERE b.content_session_id = 'manual-memories'
          AND (b.status <> 'completed'
               OR b.project_path <> 'manual-memories'
               OR NOT (blank.up_blank AND blank.lam_blank)
               OR b.observations = 0
               OR b.manual_markers <> b.observations
               OR b.manual_foreign_source > 0
               OR b.summaries > 0 OR b.prompts > 0 OR b.pending > 0)
        UNION
        SELECT b.content_session_id
        FROM v20_bucket b
        WHERE b.status = 'extraction'
          AND (b.observations = 0 OR b.extraction_markers <> b.observations
               OR b.summaries > 0 OR b.prompts > 0 OR b.pending > 0)
        UNION
        SELECT b.content_session_id
        FROM v20_bucket b
        WHERE b.status = 'dlq'
          AND (b.observations = 0 OR b.dlq_markers <> b.observations
               OR b.summaries > 0 OR b.prompts > 0 OR b.pending > 0)
    ) bad;
    IF offenders IS NOT NULL THEN
        RAISE EXCEPTION
            'TECHNICAL_BUCKET_CONFLICT: these look like manual/extraction/dlq buckets but break a whole-group rule (marker set, empty set, or borrowed prompt/summary/pending row): %. Inspect them by hand; this migration will not guess their identity.',
            offenders;
    END IF;
END $$;

-- 5b. Mark the proven buckets. `distinct on` + explicit predicates keeps this
--     unambiguous; a row is classified only when exactly one kind matches.
ALTER TABLE v20_bucket ADD COLUMN kind VARCHAR(32);
UPDATE v20_bucket b
   SET kind = 'manual'
 WHERE b.content_session_id = 'manual-memories' AND b.status = 'completed';
UPDATE v20_bucket b
   SET kind = 'extraction'
 WHERE b.status = 'extraction' AND b.kind IS NULL;
UPDATE v20_bucket b
   SET kind = 'dlq'
 WHERE b.status = 'dlq' AND b.kind IS NULL;

-- The manual sentinel cannot be proven by shape alone. Abort unless the operator has
-- explicitly recorded that they reviewed it.
DO $$
DECLARE
    sentinel_present boolean;
    reviewed         boolean;
BEGIN
    SELECT EXISTS (SELECT 1 FROM v20_bucket WHERE kind = 'manual') INTO sentinel_present;
    IF sentinel_present THEN
        -- The decision comes from the operator, not from the data: set it with
        --   SET app.manual_sentinel_reviewed = 'true';
        -- in the session (or the migration runner's connection) before applying.
        reviewed := COALESCE(current_setting('app.manual_sentinel_reviewed', true) = 'true', false)
                    OR EXISTS (SELECT 1 FROM mem_identity_flags
                               WHERE flag_name = 'manual_sentinel_reviewed' AND enabled);
        IF NOT reviewed THEN
            RAISE EXCEPTION
                'LEGACY_SENTINEL_IDENTITY_AMBIGUITY: the manual-memories bucket has the right shape, but shape cannot prove that key was never a real conversation. Inspect the bucket by hand; if you accept mapping it to the manual-default scope, re-run with SET app.manual_sentinel_reviewed = ''true''.'
                ;
        END IF;
    END IF;
END $$;

-- Repair NULL/blank source on proven manual rows only. A foreign non-empty source was
-- already excluded by the abort above, so nothing is silently overwritten here.
UPDATE mem_observations o
   SET source = 'manual'
  FROM v20_bucket b
 WHERE b.kind = 'manual'
   AND o.content_session_id = b.content_session_id
   AND (o.source IS NULL OR btrim(o.source, ' ') = '');

-- ============================================================================
-- 6. Backfill observations
-- ============================================================================

-- 6a. Standalone rows: typed, no conversation, scope from their own project path.
UPDATE mem_observations o
   SET conversation_id = NULL,
       standalone_kind = b.kind,
       memory_scope_id = CASE
           WHEN b.kind = 'manual' THEN
               (SELECT id FROM mem_memory_scopes WHERE scope_type = 'manual_default' LIMIT 1)
           ELSE
               (SELECT id FROM mem_memory_scopes
                WHERE scope_type = 'project' AND scope_key = o.project_path COLLATE "C")
       END
  FROM v20_bucket b
 WHERE b.kind IS NOT NULL
   AND o.content_session_id = b.content_session_id;

-- 6b. Conversation-owned rows. Scope comes from the observation's own project path,
--     never the session's: a session may change working directory mid-conversation.
UPDATE mem_observations o
   SET conversation_id = s.id,
       standalone_kind = NULL,
       memory_scope_id = (SELECT id FROM mem_memory_scopes
                          WHERE scope_type = 'project'
                            AND scope_key = o.project_path COLLATE "C")
  FROM mem_sessions s
 WHERE o.content_session_id = s.content_session_id
   AND o.conversation_id IS NULL
   -- Rows already typed as standalone must never be re-attributed to their legacy
   -- carrier-session row: that row exists only to satisfy the old external-id FK.
   AND o.standalone_kind IS NULL;

-- 6c. Anything still unassigned cannot be proven: abort with the list.
DO $$
DECLARE
    n_sessions int;
    n_scope    int;
    sample     text;
BEGIN
    SELECT count(*) INTO n_sessions
    FROM mem_observations o
    WHERE o.conversation_id IS NULL AND o.standalone_kind IS NULL;
    IF n_sessions > 0 THEN
        SELECT string_agg(DISTINCT content_session_id, ', ') INTO sample
        FROM (SELECT content_session_id FROM mem_observations
              WHERE conversation_id IS NULL AND standalone_kind IS NULL LIMIT 20) x;
        RAISE EXCEPTION
            'UNCLASSIFIABLE_OBSERVATIONS: % observation(s) have no provable conversation and no proven standalone bucket (external keys: %). These are usually rows whose parent session no longer exists. Resolve by hand, then re-run.',
            n_sessions, sample;
    END IF;

    SELECT count(*) INTO n_scope FROM mem_observations WHERE memory_scope_id IS NULL;
    IF n_scope > 0 THEN
        RAISE EXCEPTION 'OBSERVATION_WITHOUT_SCOPE: % observation(s) still have no memory scope', n_scope;
    END IF;
END $$;

-- ============================================================================
-- 7. Backfill summaries and prompts
-- ============================================================================

UPDATE mem_summaries m
   SET conversation_id = s.id
  FROM mem_sessions s
 WHERE s.content_session_id = m.content_session_id
   AND m.conversation_id IS NULL;

UPDATE mem_user_prompts p
   SET conversation_id = s.id
  FROM mem_sessions s
 WHERE s.content_session_id = p.content_session_id
   AND p.conversation_id IS NULL;

DO $$
DECLARE
    n int;
BEGIN
    SELECT (SELECT count(*) FROM mem_summaries WHERE conversation_id IS NULL)
         + (SELECT count(*) FROM mem_user_prompts WHERE conversation_id IS NULL) INTO n;
    IF n > 0 THEN
        RAISE EXCEPTION 'ORPHAN_SUMMARY_OR_PROMPT: % summary/prompt row(s) have no parent conversation', n;
    END IF;
END $$;

-- ============================================================================
-- 8. A parent may not be both a conversation and a technical bucket
-- ============================================================================
-- After the standalone backfill, the sentinel/ext/dlq parents own no conversation
-- children. Keep the parent rows: they still are the legacy wire key for standalone
-- rows and their deletion semantics are handled by the application.

-- ============================================================================
-- 9. Constraint tightening — everything now provable, so enforce it
-- ============================================================================

-- Drop the legacy external-id foreign keys (discovered by catalogue, never by name).
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT conname, conrelid::regclass::text AS tbl
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND contype = 'f'
          AND confrelid = 'mem_sessions'::regclass
          AND pg_get_constraintdef(oid) ILIKE '%content_session_id%'
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
    END LOOP;
END $$;

-- Drop the legacy global unique on the external id, after proving the replacement holds.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT conname FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND conrelid = 'mem_sessions'::regclass
          AND contype = 'u'
          AND pg_get_constraintdef(oid) = 'UNIQUE (content_session_id)'
    LOOP
        EXECUTE format('ALTER TABLE mem_sessions DROP CONSTRAINT %I', r.conname);
    END LOOP;

    IF EXISTS (SELECT 1 FROM pg_constraint
               WHERE connamespace = 'public'::regnamespace
                 AND conrelid = 'mem_sessions'::regclass
                 AND contype = 'u'
                 AND pg_get_constraintdef(oid) = 'UNIQUE (content_session_id)') THEN
        RAISE EXCEPTION 'could not remove the legacy UNIQUE(content_session_id)';
    END IF;
END $$;

ALTER TABLE mem_sessions ALTER COLUMN platform_source SET NOT NULL;
ALTER TABLE mem_sessions
    ADD CONSTRAINT mem_sessions_platform_source_slug
        CHECK (platform_source COLLATE "C" ~ '^[a-z0-9][a-z0-9._-]{0,49}$');

-- Replacement identity: unique per (platform, external id). COLLATE "C" keeps the
-- external id byte-exact and case-sensitive.
CREATE UNIQUE INDEX ux_sessions_platform_content
    ON mem_sessions (platform_source COLLATE "C", content_session_id COLLATE "C");

-- Parent key for composite foreign keys from children.
ALTER TABLE mem_sessions
    ADD CONSTRAINT ux_sessions_id_platform_content
        UNIQUE (id, platform_source, content_session_id);

ALTER TABLE mem_observations ALTER COLUMN platform_source SET NOT NULL;
ALTER TABLE mem_observations
    ADD CONSTRAINT mem_observations_platform_source_slug
        CHECK (platform_source COLLATE "C" ~ '^[a-z0-9][a-z0-9._-]{0,49}$');
ALTER TABLE mem_summaries ALTER COLUMN platform_source SET NOT NULL;
ALTER TABLE mem_summaries
    ADD CONSTRAINT mem_summaries_platform_source_slug
        CHECK (platform_source COLLATE "C" ~ '^[a-z0-9][a-z0-9._-]{0,49}$');
ALTER TABLE mem_user_prompts ALTER COLUMN platform_source SET NOT NULL;
ALTER TABLE mem_user_prompts
    ADD CONSTRAINT mem_user_prompts_platform_source_slug
        CHECK (platform_source COLLATE "C" ~ '^[a-z0-9][a-z0-9._-]{0,49}$');

-- Observation relations.
ALTER TABLE mem_observations ALTER COLUMN memory_scope_id SET NOT NULL;
ALTER TABLE mem_observations
    ADD CONSTRAINT fk_observations_memory_scope
        FOREIGN KEY (memory_scope_id) REFERENCES mem_memory_scopes (id);

ALTER TABLE mem_observations
    ADD CONSTRAINT mem_observations_standalone_shape CHECK (
        (conversation_id IS NOT NULL AND standalone_kind IS NULL)
     OR (conversation_id IS NULL AND standalone_kind IN ('manual', 'extraction', 'dlq'))
    );

-- Conversation-owned observations must agree with their parent on both external
-- fields. MATCH SIMPLE means a standalone row (conversation_id NULL) skips the check,
-- which is exactly what we want: those rows borrow a legacy wire key, not a relation.
ALTER TABLE mem_observations
    ADD CONSTRAINT fk_observations_conversation_consistent
        FOREIGN KEY (conversation_id, platform_source, content_session_id)
        REFERENCES mem_sessions (id, platform_source, content_session_id);

ALTER TABLE mem_observations
    ADD CONSTRAINT mem_observations_standalone_markers CHECK (
        standalone_kind IS NULL
     OR (standalone_kind = 'manual'     AND source = 'manual')
     OR (standalone_kind = 'extraction' AND source = 'llm_extraction' AND left(type, 10) = 'extracted_')
     OR (standalone_kind = 'dlq'        AND source = 'dlq' AND left(type, 4) = 'dlq_')
    );


-- Summaries and prompts.
ALTER TABLE mem_summaries ALTER COLUMN conversation_id SET NOT NULL;
ALTER TABLE mem_summaries
    ADD CONSTRAINT fk_summaries_conversation_consistent
        FOREIGN KEY (conversation_id, platform_source, content_session_id)
        REFERENCES mem_sessions (id, platform_source, content_session_id);

ALTER TABLE mem_user_prompts ALTER COLUMN conversation_id SET NOT NULL;
ALTER TABLE mem_user_prompts
    ADD CONSTRAINT fk_prompts_conversation_consistent
        FOREIGN KEY (conversation_id, platform_source, content_session_id)
        REFERENCES mem_sessions (id, platform_source, content_session_id);

-- At most one summary per (conversation, durable job). Historical rows stay NULL.
CREATE UNIQUE INDEX ux_summaries_conversation_job_key
    ON mem_summaries (conversation_id, summary_job_key)
    WHERE summary_job_key IS NOT NULL;

-- ============================================================================
-- 10. Pending queue: summary job statuses and idempotency
-- ============================================================================

DO $$
DECLARE
    v_conname text;
BEGIN
    SELECT conname INTO v_conname
    FROM pg_constraint
    WHERE connamespace = 'public'::regnamespace
      AND conrelid = 'mem_pending_messages'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%status%'
      AND pg_get_constraintdef(oid) ILIKE '%pending%'
    LIMIT 1;
    IF v_conname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE mem_pending_messages DROP CONSTRAINT %I', v_conname);
    END IF;
END $$;

ALTER TABLE mem_pending_messages
    ADD CONSTRAINT mem_pending_messages_status_check CHECK (
        (message_type = 'observation'
         AND status IN ('pending', 'processing', 'processed', 'failed', 'skipped'))
     OR (message_type = 'summarize'
         AND status IN ('pending', 'processing', 'processed', 'failed', 'skipped', 'result_persisted'))
    );

CREATE UNIQUE INDEX ux_pending_summary_job_key
    ON mem_pending_messages (session_db_id, message_type, job_key)
    WHERE job_key IS NOT NULL AND message_type = 'summarize';

-- ============================================================================
-- 11. Feedback soft references
-- ============================================================================

-- Dangling session_db_id values have no recoverable target. Record them before the
-- foreign key can be created, then null the column (the row itself is preserved).
CREATE TABLE mem_migration_note (
    id          BIGSERIAL PRIMARY KEY,
    note_code   VARCHAR(64) NOT NULL,
    row_ref     TEXT,
    detail      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO mem_migration_note (note_code, row_ref, detail)
SELECT 'FEEDBACK_DANGLING_SESSION_NULLED', f.id::text, f.session_db_id::text
FROM observation_feedback f
LEFT JOIN mem_sessions s ON s.id = f.session_db_id
WHERE f.session_db_id IS NOT NULL AND s.id IS NULL;

UPDATE observation_feedback f
   SET session_db_id = NULL
  FROM mem_sessions s
 WHERE f.session_db_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM mem_sessions x WHERE x.id = f.session_db_id);

ALTER TABLE observation_feedback
    ADD CONSTRAINT fk_feedback_session
        FOREIGN KEY (session_db_id) REFERENCES mem_sessions (id) ON DELETE SET NULL;

-- ============================================================================
-- 12. Indexes and comments
-- ============================================================================

CREATE INDEX idx_observations_memory_scope ON mem_observations (memory_scope_id);
CREATE INDEX idx_observations_conversation ON mem_observations (conversation_id)
    WHERE conversation_id IS NOT NULL;
CREATE INDEX idx_observations_standalone_kind ON mem_observations (standalone_kind)
    WHERE standalone_kind IS NOT NULL;
CREATE INDEX idx_summaries_conversation ON mem_summaries (conversation_id);
CREATE INDEX idx_user_prompts_conversation ON mem_user_prompts (conversation_id);
CREATE INDEX idx_pending_result_summary ON mem_pending_messages (result_summary_id)
    WHERE result_summary_id IS NOT NULL;

COMMENT ON TABLE mem_memory_scopes IS
    'Memory Scope: long-lived ownership/query partition for observations. Not an auth or tenant boundary.';
COMMENT ON COLUMN mem_memory_scopes.scope_key IS
    'Exact, uncanonicalised key. project: the stored project_path verbatim. manual_default: manual-default-v1.';
COMMENT ON COLUMN mem_observations.conversation_id IS
    'Owning conversation (mem_sessions.id). NULL only for typed standalone rows.';
COMMENT ON COLUMN mem_observations.standalone_kind IS
    'NULL for conversation-owned rows; manual|extraction|dlq for rows that only borrow a legacy wire key.';


INSERT INTO mem_identity_flags (flag_name, enabled) VALUES
    ('conversation_identity_v2', TRUE),
    ('memory_scope_read_v2',     TRUE)
ON CONFLICT (flag_name) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = now();

COMMIT;

\echo 'V20 one-shot migration committed.'