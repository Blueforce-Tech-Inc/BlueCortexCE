-- conversation-scope-fixture.sql
--
-- Synthetic, NON-PRODUCTION data for rehearsing the Conversation / Memory
-- Scope refactor on an isolated database copy. It deliberately covers every
-- shape the migration classifier must decide on:
--
--   * ordinary conversation with observations/summary/prompt/pending/feedback
--   * two platforms sharing one content_session_id (only possible post-V22,
--     staged here behind a temporary constraint drop so the code paths exist)
--   * the manual sentinel bucket: fully marked, mixed-marker, non-blank parent
--   * manual-shaped row whose source is a foreign non-empty value (conflict)
--   * extraction bucket: clean, empty (no observations), mixed-marker
--   * DLQ bucket: clean and one with a stray prompt
--   * project path variants that must NOT be merged: "/repo/app", "/repo/./app",
--     trailing slash, case差异
--   * dangling observation_feedback.session_db_id
--   * legacy unkeyed 'summarize' pending in several states
--   * multiple unkeyed historical summaries for one conversation
--
-- Usage (isolated copy only):
--   psql "$URL" -X -f scripts/db/conversation-scope-fixture.sql
--
-- This file is TEST-ONLY. It must never be pointed at production.

\set ON_ERROR_STOP on
BEGIN;

-- Clean slate within the current schema (idempotent re-run on an isolated copy).
-- Scope rows are recreated by the backfill, so clear them too or a second run
-- would hit the scope uniqueness index with stale digests.
DELETE FROM mem_migration_audit;
DELETE FROM mem_migration_conflicts;
DELETE FROM mem_migration_checkpoints;
DELETE FROM mem_migration_runs;
DELETE FROM mem_compat_parent_archive;
DELETE FROM observation_feedback;
DELETE FROM mem_pending_messages;
DELETE FROM mem_observations;
DELETE FROM mem_summaries;
DELETE FROM mem_user_prompts;
DELETE FROM mem_sessions;
DELETE FROM mem_memory_scopes;

-- ============================================================
-- 1. Ordinary conversation (claude / conv-ordinary)
-- ============================================================
INSERT INTO mem_sessions (id, content_session_id, project_path, platform_source, status,
                          user_prompt, last_assistant_message, started_at_epoch, user_id)
VALUES ('11111111-1111-1111-1111-111111111111', 'conv-ordinary', '/work/alpha', 'claude', 'completed',
        'fix the login bug', 'done', 1700000000000, 'user-a');

INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title, subtitle,
                              content, prompt_number, created_at_epoch, source)
VALUES
 ('11111111-1111-1111-1111-000000000001', 'conv-ordinary', '/work/alpha', 'claude', 'bugfix',
  'Fixed null deref', NULL, 'npe in login', 1, 1700000001000, 'tool_result'),
 ('11111111-1111-1111-1111-000000000002', 'conv-ordinary', '/work/alpha', 'claude', 'feature',
  'Added retry', NULL, 'retry on timeout', 1, 1700000002000, 'llm_inference'),
 -- same project path, different textual spelling: must remain a distinct Scope
 ('11111111-1111-1111-1111-000000000003', 'conv-ordinary', '/work/./alpha', 'claude', 'change',
  'Dot path row', NULL, 'same dir, different stored string', 2, 1700000003000, 'tool_result'),
 ('11111111-1111-1111-1111-000000000004', 'conv-ordinary', '/work/alpha/', 'claude', 'change',
  'Trailing slash row', NULL, 'trailing slash variant', 2, 1700000004000, 'tool_result'),
 ('11111111-1111-1111-1111-000000000005', 'conv-ordinary', '/work/Alpha', 'claude', 'change',
  'Case variant row', NULL, 'case differs', 2, 1700000005000, 'tool_result');

INSERT INTO mem_summaries (id, content_session_id, project_path, platform_source, request, completed,
                           prompt_number, created_at_epoch)
VALUES
 ('11111111-1111-1111-1111-0000000000a1', 'conv-ordinary', '/work/alpha', 'claude', 'fix login', 'done', 1, 1700000006000),
 -- historical multi-summary: the plan explicitly forbids assuming 1:1
 ('11111111-1111-1111-1111-0000000000a2', 'conv-ordinary', '/work/alpha', 'claude', 'fix login (2)', 'done', 2, 1700000007000);

INSERT INTO mem_user_prompts (id, content_session_id, project_path, platform_source, prompt_number,
                             prompt_text, created_at_epoch)
VALUES ('11111111-1111-1111-1111-0000000000b1', 'conv-ordinary', '/work/alpha', 'claude', 1, 'fix the login bug', 1700000000000);

INSERT INTO mem_pending_messages (id, session_db_id, content_session_id, message_type, tool_name,
                                  tool_input_hash, status, retry_count, created_at_epoch)
VALUES
 ('11111111-1111-1111-1111-0000000000c1', '11111111-1111-1111-1111-111111111111', 'conv-ordinary',
  'observation', 'Bash', 'hash-a', 'processed', 0, 1700000001000),
 -- legacy unkeyed summarize job in a promotable state (single, untouched)
 ('11111111-1111-1111-1111-0000000000c2', '11111111-1111-1111-1111-111111111111', 'conv-ordinary',
  'summarize', NULL, NULL, 'pending', 0, 1700000008000),
 ('11111111-1111-1111-1111-0000000000c3', '11111111-1111-1111-1111-111111111111', 'conv-ordinary',
  'observation', 'Read', 'hash-b', 'skipped', 0, 1700000009000);

INSERT INTO observation_feedback (observation_id, signal_type, session_db_id, created_at_epoch)
VALUES ('11111111-1111-1111-1111-000000000001', 'search_hit', '11111111-1111-1111-1111-111111111111', 1700000010000);

-- ============================================================
-- 2. Second platform, unrelated id
-- ============================================================
INSERT INTO mem_sessions (id, content_session_id, project_path, platform_source, status,
                          user_prompt, last_assistant_message, started_at_epoch, user_id)
VALUES ('22222222-2222-2222-2222-222222222222', 'conv-codex-1', '/work/alpha', 'codex', 'completed',
        'refactor parser', 'ok', 1700000100000, 'user-b');

INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title,
                              content, prompt_number, created_at_epoch, source)
VALUES ('22222222-2222-2222-2222-000000000001', 'conv-codex-1', '/work/alpha', 'codex', 'refactor',
        'Split parser', 'split parser into modules', 1, 1700000101000, 'tool_result');

-- ============================================================
-- 3. Cross-platform identity shapes that only become legal after V22.
--
--    (a) Two platforms whose raw ids DIFFER only by ASCII case. The legacy
--        single-column unique collation is deterministic but case-insensitive
--        under en_US, so "Shared-Session" and "shared-session" are distinct
--        rows here today but MUST become distinct identities, not one merged
--        conversation. This also proves the "opaque, case-sensitive" rule.
--    (b) Two platforms with genuinely different ids but one exact project path:
--        the correct outcome is two Conversations sharing one project Scope.
-- ============================================================
INSERT INTO mem_sessions (id, content_session_id, project_path, platform_source, status,
                          started_at_epoch)
VALUES
 ('33333333-3333-3333-3333-333333333333', 'Shared-Session', '/work/beta', 'claude', 'completed', 1700000200000),
 ('44444444-4444-4444-4444-444444444444', 'shared-session', '/work/beta', 'codex',  'completed', 1700000300000);

INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title,
                              content, prompt_number, created_at_epoch, source)
VALUES
 ('33333333-3333-3333-3333-000000000001', 'Shared-Session', '/work/beta', 'claude', 'feature', 'Claude side', 'c', 1, 1700000201000, 'tool_result'),
 ('44444444-4444-4444-4444-000000000001', 'shared-session', '/work/beta', 'codex',  'feature', 'Codex side',  'x', 1, 1700000301000, 'tool_result');

-- ============================================================
-- 4. Manual sentinel: fully marked, clean parent (the compatibility bucket)
-- ============================================================
INSERT INTO mem_sessions (id, content_session_id, project_path, platform_source, status,
                          user_prompt, last_assistant_message, started_at_epoch)
VALUES ('55555555-5555-5555-5555-555555555555', 'manual-memories', 'manual-memories', 'claude', 'completed',
        '', '', 1700000400000);

INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title, subtitle,
                              content, prompt_number, created_at_epoch, source)
VALUES
 ('55555555-5555-5555-5555-000000000001', 'manual-memories', 'manual-memories', 'claude', 'discovery',
  'Manual Memory', 'Manual memory', 'remember the wifi password', 0, 1700000401000, NULL),
 ('55555555-5555-5555-5555-000000000002', 'manual-memories', 'manual-memories', 'claude', 'discovery',
  'Manual Memory', 'Manual memory', 'remember the deploy command', 0, 1700000402000, '');

-- ============================================================
-- 5. Manual-shaped row with a FOREIGN non-empty source: conflict, must not be
--    silently rewritten to 'manual'.
-- ============================================================
INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title, subtitle,
                              content, prompt_number, created_at_epoch, source)
VALUES ('55555555-5555-5555-5555-000000000003', 'manual-memories', 'manual-memories', 'claude', 'discovery',
        'Manual Memory', 'Manual memory', 'someone else set source', 0, 1700000403000, 'llm_extraction');

-- ============================================================
-- 6. Manual bucket with a non-blank parent user_prompt: shape failure.
-- ============================================================
INSERT INTO mem_sessions (id, content_session_id, project_path, platform_source, status,
                          user_prompt, started_at_epoch)
VALUES ('66666666-6666-6666-6666-666666666666', 'manual-memories-b', 'manual-memories', 'claude', 'completed',
        'a real prompt slipped in', 1700000500000);
INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title, subtitle,
                              content, prompt_number, created_at_epoch, source)
VALUES ('66666666-6666-6666-6666-000000000001', 'manual-memories-b', 'manual-memories', 'claude', 'discovery',
        'Manual Memory', 'Manual memory', 'looks manual but parent is not', 0, 1700000501000, NULL);

-- ============================================================
-- 7. Extraction buckets: clean / empty / mixed
-- ============================================================
INSERT INTO mem_sessions (id, content_session_id, project_path, platform_source, status, started_at_epoch)
VALUES
 ('77777777-7777-7777-7777-777777777777', 'ext:aaaa:user-a', '/work/alpha', 'claude', 'extraction', 1700000600000),
 ('88888888-8888-8888-8888-888888888888', 'ext:bbbb:user-a', '/work/alpha', 'claude', 'extraction', 1700000700000),
 ('99999999-9999-9999-9999-999999999999', 'ext:cccc:user-a', '/work/alpha', 'claude', 'extraction', 1700000800000);

-- clean: every child is an extraction marker
INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title,
                              content, prompt_number, created_at_epoch, source)
VALUES ('77777777-7777-7777-7777-000000000001', 'ext:aaaa:user-a', '/work/alpha', 'claude',
        'extracted_user_preference', 'Extraction: user_preference', 'pref', 0, 1700000601000, 'llm_extraction');

-- mixed: one marker + one ordinary row (must not be classified wholesale)
INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title,
                              content, prompt_number, created_at_epoch, source)
VALUES
 ('99999999-9999-9999-9999-000000000001', 'ext:cccc:user-a', '/work/alpha', 'claude',
  'extracted_user_preference', 'Extraction: user_preference', 'pref', 0, 1700000801000, 'llm_extraction'),
 ('99999999-9999-9999-9999-000000000002', 'ext:cccc:user-a', '/work/alpha', 'claude',
  'bugfix', 'Not an extraction', 'ordinary row', 1, 1700000802000, 'tool_result');

-- ============================================================
-- 8. DLQ buckets: one clean bucket, and the shared extraction DLQ bucket carrying a
--    stray prompt (which must block classification of the shared bucket).
-- ============================================================
INSERT INTO mem_sessions (id, content_session_id, project_path, platform_source, status, started_at_epoch)
VALUES
 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'dlq:extraction', '/work/beta', 'claude', 'dlq', 1700000900000),
 ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'dlq:with-prompt', '/work/beta', 'claude', 'dlq', 1700000950000);

INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title,
                              content, prompt_number, created_at_epoch, source)
VALUES
 ('aaaaaaaa-aaaa-aaaa-aaaa-000000000001', 'dlq:extraction', '/work/beta', 'claude',
  'dlq_user_preference', 'DLQ: user_preference', 'failed', 0, 1700000901000, 'dlq'),
 ('bbbbbbbb-bbbb-bbbb-bbbb-000000000001', 'dlq:with-prompt', '/work/beta', 'claude',
  'dlq_user_preference', 'DLQ: user_preference', 'failed', 0, 1700000951000, 'dlq');

-- The KEY of the stray prompt is `dlq:extraction` on purpose: that is the shared DLQ
-- bucket the DLQ writer always uses, so this row must block classification of *that*
-- bucket. Putting it under a second, unused key would have proved nothing.
INSERT INTO mem_user_prompts (id, content_session_id, project_path, prompt_number, prompt_text, created_at_epoch)
VALUES ('aaaaaaa1-aaaa-aaaa-aaaa-0000000000b1', 'dlq:extraction', '/work/beta', 1, 'stray prompt', 1700000952000);

-- ============================================================
-- 9. Dangling feedback soft reference (no FK exists today)
-- ============================================================
INSERT INTO observation_feedback (observation_id, signal_type, session_db_id, created_at_epoch)
VALUES ('11111111-1111-1111-1111-000000000002', 'explicit_retrieval',
        'dddddddd-dddd-dddd-dddd-dddddddddddd', 1700001000000);

-- ============================================================
-- 10. Legacy summarize pending in non-promotable states
-- ============================================================
INSERT INTO mem_pending_messages (id, session_db_id, content_session_id, message_type, status,
                                  retry_count, created_at_epoch, started_processing_at_epoch)
VALUES
 ('22222222-2222-2222-2222-0000000000c1', '22222222-2222-2222-2222-222222222222', 'conv-codex-1',
  'summarize', 'failed', 3, 1700000101000, 1700000102000),
 ('22222222-2222-2222-2222-0000000000c2', '22222222-2222-2222-2222-222222222222', 'conv-codex-1',
  'summarize', 'pending', 0, 1700000103000, NULL);

-- ============================================================
-- 11. Observation with an unresolvable normalised platform (bad slug)
-- ============================================================
INSERT INTO mem_sessions (id, content_session_id, project_path, platform_source, status, started_at_epoch)
VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'conv-bad-slug', '/work/gamma', 'Claude Code', 'completed', 1700001100000);
INSERT INTO mem_observations (id, content_session_id, project_path, platform_source, type, title,
                              content, prompt_number, created_at_epoch, source)
VALUES ('cccccccc-cccc-cccc-cccc-000000000001', 'conv-bad-slug', '/work/gamma', 'Claude Code', 'change',
        'bad slug row', 'platform has an inner space', 1, 1700001101000, 'tool_result');

COMMIT;

\echo 'Fixture loaded.'
SELECT 'mem_sessions' AS t, count(*) FROM mem_sessions
UNION ALL SELECT 'mem_observations', count(*) FROM mem_observations
UNION ALL SELECT 'mem_summaries', count(*) FROM mem_summaries
UNION ALL SELECT 'mem_user_prompts', count(*) FROM mem_user_prompts
UNION ALL SELECT 'mem_pending_messages', count(*) FROM mem_pending_messages
UNION ALL SELECT 'observation_feedback', count(*) FROM observation_feedback
ORDER BY 1;