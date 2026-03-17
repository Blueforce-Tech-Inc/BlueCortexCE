-- P2-1: Context caching - add cached context columns to sessions table
-- Adds ability to cache generated context for faster session-start responses

ALTER TABLE mem_sessions ADD COLUMN IF NOT EXISTS cached_context TEXT;
ALTER TABLE mem_sessions ADD COLUMN IF NOT EXISTS context_refreshed_at_epoch BIGINT;
ALTER TABLE mem_sessions ADD COLUMN IF NOT EXISTS needs_context_refresh BOOLEAN DEFAULT FALSE;
