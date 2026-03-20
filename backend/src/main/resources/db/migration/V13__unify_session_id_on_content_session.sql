-- Unify session linkage: observations and summaries reference mem_sessions(content_session_id).
-- Historical data: child.memory_session_id may match either parent.memory_session_id (Java/TS FK)
-- or parent.content_session_id (TS observation storage pattern). Both are handled before fallback.

ALTER TABLE mem_observations ADD COLUMN content_session_id VARCHAR(255);
ALTER TABLE mem_summaries ADD COLUMN content_session_id VARCHAR(255);

-- mem_observations: resolve to canonical content_session_id
UPDATE mem_observations o
SET content_session_id = s.content_session_id
FROM mem_sessions s
WHERE o.memory_session_id IS NOT DISTINCT FROM s.memory_session_id
  AND o.content_session_id IS NULL;

UPDATE mem_observations o
SET content_session_id = s.content_session_id
FROM mem_sessions s
WHERE o.content_session_id IS NULL
  AND o.memory_session_id = s.content_session_id;

UPDATE mem_observations
SET content_session_id = memory_session_id
WHERE content_session_id IS NULL;

-- mem_summaries
UPDATE mem_summaries s2
SET content_session_id = s.content_session_id
FROM mem_sessions s
WHERE s2.memory_session_id IS NOT DISTINCT FROM s.memory_session_id
  AND s2.content_session_id IS NULL;

UPDATE mem_summaries s2
SET content_session_id = s.content_session_id
FROM mem_sessions s
WHERE s2.content_session_id IS NULL
  AND s2.memory_session_id = s.content_session_id;

UPDATE mem_summaries
SET content_session_id = memory_session_id
WHERE content_session_id IS NULL;

-- Drop child rows that cannot reference an existing session (invalid FK targets)
DELETE FROM mem_observations o
WHERE NOT EXISTS (SELECT 1 FROM mem_sessions s WHERE s.content_session_id = o.content_session_id);

DELETE FROM mem_summaries s2
WHERE NOT EXISTS (SELECT 1 FROM mem_sessions s WHERE s.content_session_id = s2.content_session_id);

ALTER TABLE mem_observations ALTER COLUMN content_session_id SET NOT NULL;
ALTER TABLE mem_summaries ALTER COLUMN content_session_id SET NOT NULL;

ALTER TABLE mem_observations DROP CONSTRAINT IF EXISTS fk_obs_memory_session;
ALTER TABLE mem_observations DROP CONSTRAINT IF EXISTS mem_observations_memory_session_id_fkey;

ALTER TABLE mem_summaries DROP CONSTRAINT IF EXISTS mem_summaries_memory_session_id_fkey;

DROP INDEX IF EXISTS idx_summaries_session;

ALTER TABLE mem_observations
    ADD CONSTRAINT fk_obs_content_session
        FOREIGN KEY (content_session_id) REFERENCES mem_sessions (content_session_id);

ALTER TABLE mem_summaries
    ADD CONSTRAINT fk_sum_content_session
        FOREIGN KEY (content_session_id) REFERENCES mem_sessions (content_session_id);

ALTER TABLE mem_observations DROP COLUMN memory_session_id;
ALTER TABLE mem_summaries DROP COLUMN memory_session_id;

DROP INDEX IF EXISTS idx_sessions_memory_session;
ALTER TABLE mem_sessions DROP COLUMN memory_session_id;

CREATE INDEX idx_summaries_session ON mem_summaries (content_session_id);
