-- V15: Add user_id column to mem_sessions for multi-user support
-- userId is nullable (null = single-user/hook mode, non-null = SDK multi-user mode)
-- Phase 3: Structured extraction groups observations by user via session → user_id

ALTER TABLE mem_sessions ADD COLUMN user_id VARCHAR(255);
CREATE INDEX idx_mem_sessions_user_id ON mem_sessions(user_id);
