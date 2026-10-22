-- conversation-scope-preflight.sql
--
-- READ-ONLY preflight for the Conversation / Memory Scope identity refactor
-- (docs/drafts/conversation-memory-scope-refactor-plan.md, section 10).
--
-- Guarantees:
--   * Runs inside a READ ONLY transaction: any write attempt fails fast.
--   * Every constraint name is discovered from pg_catalog, never assumed from
--     the historical migration text (migration files use IF EXISTS / auto
--     generated names, so the deployed names are not knowable statically).
--   * Default output is aggregates and counts only. It prints NO project paths,
--     NO external session ids, NO platform values, NO row UUIDs and NO content.
--     Row-level detail requires the companion Java runner with an explicit
--     opt-in flag (see ConversationScopePreflightRunner), never this file.
--
-- Usage (operator, on an isolated copy or a read-replica only):
--   psql "$URL" -X -f scripts/db/conversation-scope-preflight.sql
--
-- Exit behaviour: the script never mutates state. A failing assertion raises an
-- exception, which psql reports; nothing is written either way.

\set ON_ERROR_STOP on
BEGIN READ ONLY;

\echo ''
\echo '==================== 1. TARGET IDENTITY ===================='
-- Sanity: prove which database this report describes. Never prints credentials.
SELECT current_database()                 AS database,
       current_user                       AS db_user,
       current_setting('server_version')  AS server_version,
       current_setting('server_encoding') AS server_encoding,
       -- Host/address suppressed on purpose; record a sanitized label instead.
       CASE WHEN inet_server_addr() IS NULL THEN 'local-socket' ELSE 'tcp-remote' END AS connection_scope,
       session_user                       AS session_user;
SELECT datcollate, datctype, datlocprovider
FROM pg_database WHERE datname = current_database();

\echo ''
\echo '==================== 2. FLYWAY STATE ===================='
SELECT installed_rank, version, description, type, success, installed_on, execution_time
FROM flyway_schema_history
ORDER BY installed_rank;

-- Duplicate / out-of-order history is a red flag: two operators may have raced.
SELECT count(*) AS failed_migrations FROM flyway_schema_history WHERE NOT success;

\echo ''
\echo '==================== 3. COLLATION OF IDENTITY / PATH COLUMNS ===================='
-- Exact-string equality is the identity contract. Every listed column must be
-- deterministic; a nondeterministic collation would change equality semantics
-- underneath the migration. Missing rows are a FAILURE (the plan requires full
-- coverage of this list), so the expected set is checked explicitly below.
SELECT t.relname  AS table_name,
       a.attname  AS column_name,
       c.collname, c.collprovider, c.collisdeterministic
FROM pg_attribute a
JOIN pg_class     t ON t.oid = a.attrelid
JOIN pg_namespace n ON n.oid = t.relnamespace
JOIN pg_collation c ON c.oid = a.attcollation
WHERE n.nspname = 'public'
  AND t.relkind = 'r'
  AND a.attnum > 0 AND NOT a.attisdropped
  AND (t.relname, a.attname) IN (
      ('mem_sessions','project_path'),     ('mem_sessions','platform_source'),     ('mem_sessions','content_session_id'),
      ('mem_observations','project_path'), ('mem_observations','platform_source'), ('mem_observations','content_session_id'),
      ('mem_summaries','project_path'),    ('mem_summaries','platform_source'),    ('mem_summaries','content_session_id'),
      ('mem_user_prompts','project_path'), ('mem_user_prompts','platform_source'), ('mem_user_prompts','content_session_id')
  )
ORDER BY 1, 2;

-- Coverage check: 12 rows expected (4 tables x 3 columns). Anything else means
-- the live schema differs from the plan's assumption and must be investigated.
SELECT count(*) AS identity_columns_found,
       CASE WHEN count(*) = 12 THEN 'OK' ELSE 'FAIL: expected 12' END AS coverage
FROM pg_attribute a
JOIN pg_class t ON t.oid = a.attrelid
JOIN pg_namespace n ON n.oid = t.relnamespace
WHERE n.nspname = 'public' AND t.relkind = 'r' AND a.attnum > 0 AND NOT a.attisdropped
  AND (t.relname, a.attname) IN (
      ('mem_sessions','project_path'),     ('mem_sessions','platform_source'),     ('mem_sessions','content_session_id'),
      ('mem_observations','project_path'), ('mem_observations','platform_source'), ('mem_observations','content_session_id'),
      ('mem_summaries','project_path'),    ('mem_summaries','platform_source'),    ('mem_summaries','content_session_id'),
      ('mem_user_prompts','project_path'), ('mem_user_prompts','platform_source'), ('mem_user_prompts','content_session_id')
  );

-- Any nondeterministic collation on a column that participates in identity is
-- a hard stop (the plan requires halting, not silently swapping collation).
SELECT t.relname AS table_name, a.attname AS column_name, c.collname
FROM pg_attribute a
JOIN pg_class t ON t.oid = a.attrelid
JOIN pg_namespace n ON n.oid = t.relnamespace
JOIN pg_collation c ON c.oid = a.attcollation
WHERE n.nspname = 'public' AND t.relkind = 'r' AND a.attnum > 0 AND NOT a.attisdropped
  AND c.collisdeterministic = false
ORDER BY 1,2;

\echo ''
\echo '==================== 4. DISCOVERED CONSTRAINTS (pg_catalog) ===================='
-- The real deployed names. The Go/Java migration runner resolves constraints
-- from this catalogue at execution time rather than trusting migration text.
SELECT conrelid::regclass::text AS table_name,
       conname                  AS constraint_name,
       contype                  AS kind,
       pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE connamespace = 'public'::regnamespace
  AND conrelid::regclass::text IN (
      'mem_sessions','mem_observations','mem_summaries',
      'mem_user_prompts','mem_pending_messages','observation_feedback')
ORDER BY 1, kind, 2;

-- Which single-column UNIQUE still guards content_session_id? It must remain
-- until Phase F (V22). If it is already absent, an earlier manual DDL change
-- happened and every assumption downstream needs re-review.
SELECT conname AS single_column_unique_on_content_session_id
FROM pg_constraint
WHERE connamespace = 'public'::regnamespace
  AND conrelid = 'mem_sessions'::regclass
  AND contype = 'u'
  AND pg_get_constraintdef(oid) = 'UNIQUE (content_session_id)';

-- Which FKs still point at mem_sessions.content_session_id (the legacy
-- external-id edge V21 removes)? Enumerate them by catalogue, not by name.
SELECT conrelid::regclass::text AS child_table,
       conname,
       pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE connamespace = 'public'::regnamespace
  AND contype = 'f'
  AND confrelid = 'mem_sessions'::regclass
ORDER BY 1, 2;

\echo ''
\echo '==================== 5. ROW COUNTS / STRUCTURAL TOTALS ===================='
SELECT 'mem_sessions'         AS table_name, count(*) AS rows FROM mem_sessions
UNION ALL SELECT 'mem_observations',    count(*) FROM mem_observations
UNION ALL SELECT 'mem_summaries',       count(*) FROM mem_summaries
UNION ALL SELECT 'mem_user_prompts',    count(*) FROM mem_user_prompts
UNION ALL SELECT 'mem_pending_messages',count(*) FROM mem_pending_messages
UNION ALL SELECT 'observation_feedback',count(*) FROM observation_feedback
ORDER BY table_name;

\echo ''
\echo '==================== 6. EXTERNAL IDENTITY DISTRIBUTION ===================='
-- Normalised platform expression used consistently below and in the Java
-- runner: trim ASCII spaces, fold ASCII A-Z only, empty -> 'claude'.
-- Unicode-aware lower()/trim() are deliberately NOT used (locale dependent).

-- Blank / ASCII-space-only external ids: these cannot form an identity.
SELECT count(*) AS blank_session_ids
FROM mem_sessions
WHERE content_session_id IS NULL OR btrim(content_session_id, ' ') = '';

-- Duplicate raw external ids (expected 0 while the single-column unique exists;
-- non-zero means a constraint was dropped out of band).
SELECT COALESCE(sum(row_count), 0) AS sessions_in_duplicate_groups
FROM (
    SELECT count(*) AS row_count
    FROM mem_sessions GROUP BY content_session_id COLLATE "C" HAVING count(*) > 1
) d;

-- Composite collisions that would appear once platform enters the key.
SELECT COALESCE(count(*), 0) AS composite_collision_groups,
       COALESCE(sum(row_count), 0) AS sessions_affected
FROM (
    SELECT count(*) AS row_count
    FROM mem_sessions
    GROUP BY COALESCE(NULLIF(translate(btrim(platform_source, ' '),
                    'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), ''), 'claude') COLLATE "C",
             content_session_id COLLATE "C"
    HAVING count(*) > 1
) c;

-- Per-table platform_source completeness (NULL / blank / populated).
SELECT 'mem_sessions' AS table_name,
       count(*) FILTER (WHERE platform_source IS NULL)                              AS null_rows,
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source,' ') = '')  AS blank_rows,
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source,' ') <> '') AS populated_rows
FROM mem_sessions
UNION ALL SELECT 'mem_observations',
       count(*) FILTER (WHERE platform_source IS NULL),
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source,' ') = ''),
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source,' ') <> '')
FROM mem_observations
UNION ALL SELECT 'mem_summaries',
       count(*) FILTER (WHERE platform_source IS NULL),
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source,' ') = ''),
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source,' ') <> '')
FROM mem_summaries
UNION ALL SELECT 'mem_user_prompts',
       count(*) FILTER (WHERE platform_source IS NULL),
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source,' ') = ''),
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source,' ') <> '')
FROM mem_user_prompts
ORDER BY table_name;

-- Values that cannot be normalised into the lowercase ASCII slug contract.
-- These are conflict-ledger rows, never auto-rewritten.
SELECT table_name, count(*) AS invalid_slug_rows
FROM (
    SELECT 'mem_sessions' AS table_name FROM mem_sessions
    WHERE COALESCE(NULLIF(translate(btrim(platform_source,' '),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),''),'claude')
          COLLATE "C" !~ '^[abcdefghijklmnopqrstuvwxyz0123456789][abcdefghijklmnopqrstuvwxyz0123456789._-]{0,49}$'
    UNION ALL SELECT 'mem_observations' FROM mem_observations
    WHERE COALESCE(NULLIF(translate(btrim(platform_source,' '),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),''),'claude')
          COLLATE "C" !~ '^[abcdefghijklmnopqrstuvwxyz0123456789][abcdefghijklmnopqrstuvwxyz0123456789._-]{0,49}$'
    UNION ALL SELECT 'mem_summaries' FROM mem_summaries
    WHERE COALESCE(NULLIF(translate(btrim(platform_source,' '),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),''),'claude')
          COLLATE "C" !~ '^[abcdefghijklmnopqrstuvwxyz0123456789][abcdefghijklmnopqrstuvwxyz0123456789._-]{0,49}$'
    UNION ALL SELECT 'mem_user_prompts' FROM mem_user_prompts
    WHERE COALESCE(NULLIF(translate(btrim(platform_source,' '),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),''),'claude')
          COLLATE "C" !~ '^[abcdefghijklmnopqrstuvwxyz0123456789][abcdefghijklmnopqrstuvwxyz0123456789._-]{0,49}$'
) s
GROUP BY table_name ORDER BY table_name;

\echo ''
\echo '==================== 7. ORPHANS / PARENT-CHILD CONSISTENCY ===================='
-- Children whose external id has no parent row. V13 deleted such rows once;
-- any that exist now arrived after V13 (or via a manual insert) and matter.
SELECT 'mem_observations' AS child_table, count(*) AS orphan_rows
FROM mem_observations o
WHERE NOT EXISTS (SELECT 1 FROM mem_sessions s WHERE s.content_session_id = o.content_session_id)
UNION ALL SELECT 'mem_summaries', count(*)
FROM mem_summaries m
WHERE NOT EXISTS (SELECT 1 FROM mem_sessions s WHERE s.content_session_id = m.content_session_id)
UNION ALL SELECT 'mem_user_prompts', count(*)
FROM mem_user_prompts p
WHERE NOT EXISTS (SELECT 1 FROM mem_sessions s WHERE s.content_session_id = p.content_session_id);

-- Child platform differs from its parent's (raw, before normalisation).
-- For Conversation-owned rows this is an identity conflict; only fully proven
-- standalone buckets are exempt, and this query cannot prove that.
SELECT count(*) AS raw_platform_mismatch_observations
FROM mem_observations o
JOIN mem_sessions s ON s.content_session_id COLLATE "C" = o.content_session_id COLLATE "C"
WHERE COALESCE(NULLIF(translate(btrim(o.platform_source,' '),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),''),'claude') COLLATE "C"
   <> COALESCE(NULLIF(translate(btrim(s.platform_source,' '),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),''),'claude') COLLATE "C";

\echo ''
\echo '==================== 8. MANUAL SENTINEL PROFILE ===================='
-- The manual marker shape. Deliberately aggregated: no marker => cannot be
-- classified as the compatibility bucket, so it becomes a conflict.
--
-- `source` is part of the marker, not a separate nicety. The classifier may
-- repair NULL/blank source to 'manual', but a non-empty value that is anything
-- else must go to the conflict ledger rather than be silently rewritten. A
-- profile that ignored source would report this bucket as clean, so the
-- `source` columns below are what prevent that false pass.
SELECT (o.type COLLATE "C" = 'discovery' AND o.subtitle COLLATE "C" = 'Manual memory') AS has_manual_marker,
       (o.project_path COLLATE "C" = 'manual-memories')                                AS has_reserved_project_path,
       CASE
           WHEN o.source IS NULL      THEN 'null'
           WHEN o.source = ''         THEN 'empty'
           WHEN o.source = 'manual'   THEN 'manual'
           ELSE                        'foreign'
       END                                                                            AS source_class,
       count(*) AS observation_rows
FROM mem_observations o
WHERE o.content_session_id COLLATE "C" = 'manual-memories'
GROUP BY 1, 2, 3 ORDER BY 1, 2, 3;

-- Conflict rows inside the sentinel: marked, but carrying a foreign non-empty
-- source. These block automatic classification of the whole bucket.
SELECT count(*) AS manual_marker_rows_with_foreign_source
FROM mem_observations
WHERE content_session_id COLLATE "C" = 'manual-memories'
  AND type COLLATE "C" = 'discovery'
  AND subtitle COLLATE "C" = 'Manual memory'
  AND source IS NOT NULL
  AND btrim(source, ' ') <> ''
  AND source COLLATE "C" <> 'manual';

-- Parent profile for the sentinel key. Blank checks approximate Java
-- String.isBlank(); the Java runner is authoritative for Unicode whitespace.
SELECT s.status,
       (s.project_path COLLATE "C" = 'manual-memories')            AS reserved_project_path,
       (NULLIF(btrim(s.user_prompt,' '),'') IS NULL)                AS no_user_prompt_ascii,
       (NULLIF(btrim(s.last_assistant_message,' '),'') IS NULL)     AS no_assistant_message_ascii,
       count(DISTINCT o.id)                                          AS observations,
       count(DISTINCT o.id) FILTER (WHERE o.type COLLATE "C" = 'discovery'
                                      AND o.subtitle COLLATE "C" = 'Manual memory') AS marker_observations,
       count(DISTINCT o.id) FILTER (WHERE NOT COALESCE((o.type COLLATE "C" = 'discovery'
                                      AND o.subtitle COLLATE "C" = 'Manual memory'), false)) AS non_marker_observations,
       count(DISTINCT sm.id) AS summaries,
       count(DISTINCT p.id)  AS prompts,
       count(DISTINCT pm.id) AS pending_rows
FROM mem_sessions s
LEFT JOIN mem_observations  o  ON o.content_session_id COLLATE "C" = s.content_session_id COLLATE "C"
LEFT JOIN mem_summaries     sm ON sm.content_session_id COLLATE "C" = s.content_session_id COLLATE "C"
LEFT JOIN mem_user_prompts  p  ON p.content_session_id COLLATE "C" = s.content_session_id COLLATE "C"
LEFT JOIN mem_pending_messages pm ON pm.session_db_id = s.id
WHERE s.content_session_id COLLATE "C" = 'manual-memories'
GROUP BY s.status, s.project_path, s.user_prompt, s.last_assistant_message;

\echo ''
\echo '==================== 9. EXTRACTION / DLQ BUCKET PROFILE ===================='
-- Literal prefixes (left(...)), never LIKE 'xxx_%' where _ is a wildcard.
WITH obs AS (
    SELECT content_session_id,
           count(*) AS observations,
           count(*) FILTER (WHERE source COLLATE "C" = 'llm_extraction'
                              AND left(type COLLATE "C", 10) = 'extracted_') AS extraction_markers,
           count(*) FILTER (WHERE source COLLATE "C" = 'dlq'
                              AND left(type COLLATE "C", 4) = 'dlq_')          AS dlq_markers,
           count(*) FILTER (WHERE source COLLATE "C" = 'llm_extraction'
                               OR left(type COLLATE "C", 10) = 'extracted_')   AS extraction_signals,
           count(*) FILTER (WHERE source COLLATE "C" = 'dlq'
                               OR left(type COLLATE "C", 4) = 'dlq_')          AS dlq_signals
    FROM mem_observations GROUP BY content_session_id
), smry AS (SELECT content_session_id, count(*) AS n FROM mem_summaries GROUP BY content_session_id),
   prm  AS (SELECT content_session_id, count(*) AS n FROM mem_user_prompts GROUP BY content_session_id),
   pnd  AS (SELECT session_db_id, count(*) AS n FROM mem_pending_messages GROUP BY session_db_id)
SELECT s.status AS parent_status,
       count(*) AS parent_sessions,
       count(*) FILTER (WHERE COALESCE(o.observations,0) = 0) AS empty_groups,
       count(*) FILTER (WHERE s.status = 'extraction' AND COALESCE(o.observations,0) > 0
                          AND o.extraction_markers = o.observations) AS all_extraction_marker_groups,
       count(*) FILTER (WHERE s.status = 'dlq' AND COALESCE(o.observations,0) > 0
                          AND o.dlq_markers = o.observations) AS all_dlq_marker_groups,
       count(*) FILTER (WHERE (COALESCE(o.extraction_signals,0) > 0 AND s.status <> 'extraction')
                           OR (COALESCE(o.dlq_signals,0) > 0 AND s.status <> 'dlq')
                           OR (COALESCE(o.extraction_signals,0) > 0 AND COALESCE(o.dlq_signals,0) > 0))
           AS marker_status_mismatch_groups,
       count(*) FILTER (WHERE COALESCE(smry.n,0) > 0) AS groups_with_summaries,
       count(*) FILTER (WHERE COALESCE(prm.n,0)  > 0) AS groups_with_prompts,
       count(*) FILTER (WHERE COALESCE(pnd.n,0)  > 0) AS groups_with_pending
FROM mem_sessions s
LEFT JOIN obs  o    ON o.content_session_id COLLATE "C" = s.content_session_id COLLATE "C"
LEFT JOIN smry smry ON smry.content_session_id COLLATE "C" = s.content_session_id COLLATE "C"
LEFT JOIN prm  prm  ON prm.content_session_id  COLLATE "C" = s.content_session_id COLLATE "C"
LEFT JOIN pnd  pnd  ON pnd.session_db_id = s.id
WHERE s.status IN ('extraction','dlq')
   OR COALESCE(o.extraction_signals,0) > 0
   OR COALESCE(o.dlq_signals,0) > 0
GROUP BY s.status ORDER BY 1;

\echo ''
\echo '==================== 10. FEEDBACK SOFT REFERENCE ===================='
-- observation_feedback.session_db_id has NO foreign key today. Count dangling
-- references: they must be audited and nulled before V21 adds the constraint.
SELECT count(*) FILTER (WHERE f.session_db_id IS NOT NULL)                       AS feedback_with_session_ref,
       count(*) FILTER (WHERE f.session_db_id IS NOT NULL AND s.id IS NULL)      AS dangling_session_refs
FROM observation_feedback f
LEFT JOIN mem_sessions s ON s.id = f.session_db_id;

\echo ''
\echo '==================== 11. SCOPE BACKFILL SCALE ===================='
-- Path values only ever appear as counts. No raw path is emitted.
SELECT count(*) AS observation_rows,
       count(DISTINCT project_path COLLATE "C")
           FILTER (WHERE project_path IS NOT NULL AND btrim(project_path,' ') <> '') AS distinct_exact_project_paths,
       count(*) FILTER (WHERE project_path IS NULL OR btrim(project_path,' ') = '')  AS blank_or_null_path_rows,
       count(*) FILTER (WHERE content_session_id COLLATE "C" = 'manual-memories'
                          AND type COLLATE "C" = 'discovery'
                          AND subtitle COLLATE "C" = 'Manual memory'
                          AND project_path COLLATE "C" = 'manual-memories')          AS manual_default_candidates
FROM mem_observations;

\echo ''
\echo '==================== 12. PENDING QUEUE PROFILE ===================='
SELECT message_type, status, count(*) AS rows, sum(retry_count) AS retries
FROM mem_pending_messages
GROUP BY message_type, status ORDER BY 1,2;

-- Legacy 'summarize' jobs that are NOT keyed. Under V20 these decide whether a
-- durable job can be promoted or must go to the conflict ledger.
SELECT message_type, status,
       count(*) FILTER (WHERE retry_count = 0)     AS zero_retry,
       count(*) FILTER (WHERE retry_count > 0)     AS retried,
       count(*) FILTER (WHERE started_processing_at_epoch IS NULL
                          AND completed_at_epoch IS NULL
                          AND failed_at_epoch IS NULL) AS untouched
FROM mem_pending_messages
WHERE message_type = 'summarize'
GROUP BY message_type, status ORDER BY 1,2;

\echo ''
\echo '==================== 13. SUMMARY HISTORY SHAPE ===================='
-- How many summaries per conversation. The plan explicitly rejects "one summary
-- per session" as an assumption, so this distribution is recorded, not enforced.
SELECT summaries_per_session, count(*) AS sessions
FROM (
    SELECT content_session_id, count(*) AS summaries_per_session
    FROM mem_summaries GROUP BY content_session_id
) s
GROUP BY 1 ORDER BY 1;

SELECT count(*) AS sessions_without_summary
FROM mem_sessions s
WHERE NOT EXISTS (SELECT 1 FROM mem_summaries m WHERE m.content_session_id = s.content_session_id);

SELECT count(*) AS sessions_without_observation
FROM mem_sessions s
WHERE NOT EXISTS (SELECT 1 FROM mem_observations o WHERE o.content_session_id = s.content_session_id);

\echo ''
\echo '==================== 14. SESSION STATUS DISTRIBUTION ===================='
SELECT COALESCE(status,'(null)') AS status, count(*) AS sessions
FROM mem_sessions GROUP BY 1 ORDER BY 2 DESC, 1;

\echo ''
\echo '==================== 15. PREFLIGHT VERDICT ===================='
-- A single row summarising whether the hard-stop classes are empty. The Java
-- runner repeats these after any normalisation and is authoritative for
-- Unicode whitespace and slug folding.
SELECT
    (SELECT count(*) FROM mem_sessions WHERE content_session_id IS NULL OR btrim(content_session_id,' ') = '')
      AS blank_session_id_rows,
    (SELECT count(*) FROM (
        SELECT 1 FROM mem_sessions
        GROUP BY COALESCE(NULLIF(translate(btrim(platform_source,' '),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),''),'claude') COLLATE "C",
                 content_session_id COLLATE "C"
        HAVING count(*) > 1) x) AS composite_collision_groups,
    (SELECT count(*) FROM mem_observations o
     WHERE NOT EXISTS (SELECT 1 FROM mem_sessions s WHERE s.content_session_id = o.content_session_id))
      AS orphan_observations,
    (SELECT count(*) FROM observation_feedback f
     LEFT JOIN mem_sessions s ON s.id = f.session_db_id
     WHERE f.session_db_id IS NOT NULL AND s.id IS NULL) AS dangling_feedback_refs,
    (SELECT count(*) FROM mem_observations WHERE project_path IS NULL OR btrim(project_path,' ') = '')
      AS blank_project_path_observations;

ROLLBACK;

\echo ''
\echo 'PREFLIGHT COMPLETE (read-only; transaction rolled back).'