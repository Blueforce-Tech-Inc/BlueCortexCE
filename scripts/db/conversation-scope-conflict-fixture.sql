-- conversation-scope-conflict-fixture.sql
--
-- Negative-path fixture for the one-shot V20 migration. Each scenario is a class of
-- row the migration is REQUIRED to reject: it must abort, roll back entirely, and name
-- the offending rows. This file is a template, not a suite: a psql script cannot
-- rewind a committed migration, so the rehearsal driver loads ONE scenario at a time
-- onto a fresh V1-V19 database.
--
-- Usage (isolated copy only):
--   psql "$URL" -X -v scenario=manual_foreign_source \
--        -f scripts/db/conversation-scope-fixture.sql \
--        -f scripts/db/conversation-scope-conflict-fixture.sql
--
-- The base fixture must be loaded first so the scenarios have a realistic neighbour
-- population; these rows are added on top of it.

\set ON_ERROR_STOP on

BEGIN;

-- Common parent helper: every scenario needs a parent row because the observations
-- still carry the legacy external-id foreign key at this point in the migration.
INSERT INTO mem_sessions (id, content_session_id, project_path, platform_source, status,
                          user_prompt, last_assistant_message, started_at_epoch)
VALUES
 ('f0000000-0000-0000-0000-000000000001', 'conflict-manual-foreign', 'manual-memories', 'claude', 'completed', '', '', 1800000000000),
 ('f0000000-0000-0000-0000-000000000002', 'manual-memories-conflict-b', 'manual-memories', 'claude', 'completed', 'a real prompt', '', 1800000001000),
 ('f0000000-0000-0000-0000-000000000003', 'conflict-ext-mixed', '/work/delta', 'claude', 'extraction', NULL, NULL, 1800000002000),
 ('f0000000-0000-0000-0000-000000000004', 'conflict-ext-empty', '/work/delta', 'claude', 'extraction', NULL, NULL, 1800000003000),
 ('f0000000-0000-0000-0000-000000000005', 'conflict-dlq-prompt', '/work/delta', 'claude', 'dlq', NULL, NULL, 1800000004000),
 ('f0000000-0000-0000-0000-000000000006', 'conflict-bad-platform', '/work/delta', 'Claude Code', 'completed', NULL, NULL, 1800000005000),
 ('f0000000-0000-0000-0000-000000000007', 'conflict-orphan-window', '/work/delta', 'claude', 'completed', NULL, NULL, 1800000006000)
ON CONFLICT (id) DO NOTHING;

-- Scenario 1: manual marker carrying a foreign non-empty source.
INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title,
                              subtitle, content, prompt_number, created_at_epoch, source)
VALUES ('f0000000-0000-0000-0000-00000000a001', 'conflict-manual-foreign', 'manual-memories',
        'claude', 'discovery', 'Manual Memory', 'Manual memory', 'foreign source', 0, 1800000000000,
        'llm_extraction');

-- Scenario 2: manual-shaped bucket whose parent carries real conversation text.
INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title,
                              subtitle, content, prompt_number, created_at_epoch, source)
VALUES ('f0000000-0000-0000-0000-00000000a002', 'manual-memories-conflict-b', 'manual-memories',
        'claude', 'discovery', 'Manual Memory', 'Manual memory', 'looks manual', 0, 1800000001000, NULL);

-- Scenario 3: extraction bucket with a child that is not an extraction marker.
INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title,
                              content, prompt_number, created_at_epoch, source)
VALUES
 ('f0000000-0000-0000-0000-00000000a003', 'conflict-ext-mixed', '/work/delta', 'claude',
  'extracted_user_preference', 'Extraction', 'pref', 0, 1800000002000, 'llm_extraction'),
 ('f0000000-0000-0000-0000-00000000a004', 'conflict-ext-mixed', '/work/delta', 'claude',
  'bugfix', 'Not an extraction', 'ordinary', 1, 1800000002100, 'tool_result');

-- Scenario 4: extraction bucket with NO observations at all. An empty set proves nothing.
--   (parent row 'conflict-ext-empty' already has zero children)

-- Scenario 5: DLQ bucket that borrowed a user prompt.
INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title,
                              content, prompt_number, created_at_epoch, source)
VALUES ('f0000000-0000-0000-0000-00000000a005', 'conflict-dlq-prompt', '/work/delta', 'claude',
        'dlq_user_preference', 'DLQ', 'failed', 0, 1800000004000, 'dlq');
INSERT INTO mem_user_prompts (id, content_session_id, project_path, prompt_number, prompt_text,
                              created_at_epoch)
VALUES ('f0000000-0000-0000-0000-00000000b001', 'conflict-dlq-prompt', '/work/delta', 1,
        'stray prompt', 1800000004100);

-- Scenario 6: a stored platform that cannot be canonicalised without losing information
--   (inner space). Surrounding ASCII space WOULD be trimmed, so this value must stay
--   distinguishable from a repairable one.
INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title,
                              content, prompt_number, created_at_epoch, source)
VALUES ('f0000000-0000-0000-0000-00000000a006', 'conflict-bad-platform', '/work/delta',
        'Claude Code', 'change', 'bad platform', 'row', 1, 1800000005000, 'tool_result');

COMMIT;

\echo 'Conflict fixture loaded.'
\echo 'Expected: the V20 migration aborts, rolls back completely, and names these rows.'
\echo 'Scenarios:'
\echo '  MANUAL_MARKER_FOREIGN_SOURCE      -> conflict-manual-foreign'
\echo '  MANUAL_PARENT_HAS_CONVERSATION_CONTENT / TECHNICAL_BUCKET_CONFLICT -> manual-memories-conflict-b'
\echo '  TECHNICAL_BUCKET_CONFLICT (mixed) -> conflict-ext-mixed'
\echo '  TECHNICAL_BUCKET_CONFLICT (empty) -> conflict-ext-empty'
\echo '  TECHNICAL_BUCKET_CONFLICT (prompt)-> conflict-dlq-prompt'
\echo '  PLATFORM_SOURCE_NOT_NORMALISABLE  -> conflict-bad-platform'