-- V8: Add content_hash column for observation deduplication + FK cascade
-- Migration 22 and 21 from TypeScript version
--
-- This migration adds:
-- 1. content_hash column for deduplication (16 chars, SHA-256 truncated)
-- 2. ON UPDATE CASCADE to FK constraint (if not already present)

-- Add content_hash column for observation deduplication
ALTER TABLE mem_observations ADD COLUMN IF NOT EXISTS content_hash VARCHAR(16);

-- Backfill existing rows with unique random hashes (16 chars, matching TS version)
-- This ensures existing observations don't block new inserts
UPDATE mem_observations
SET content_hash = substring(md5(random()::text) from 1 for 16)
WHERE content_hash IS NULL;

-- Create index for fast dedup lookups (content_hash + time window)
CREATE INDEX IF NOT EXISTS idx_obs_content_hash ON mem_observations(content_hash, created_at_epoch);

-- Add ON UPDATE CASCADE to FK constraint (migration 21 from TS)
-- PostgreSQL doesn't support ALTER CONSTRAINT, so we drop and recreate
DO $$
BEGIN
    -- Check if the old constraint exists (without ON UPDATE CASCADE)
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'mem_observations_memory_session_id_fkey'
        AND table_name = 'mem_observations'
    ) THEN
        ALTER TABLE mem_observations DROP CONSTRAINT mem_observations_memory_session_id_fkey;
    END IF;
END $$;

-- Add the new constraint with ON UPDATE CASCADE
ALTER TABLE mem_observations
ADD CONSTRAINT fk_obs_memory_session
FOREIGN KEY (memory_session_id) REFERENCES mem_sessions(memory_session_id)
ON UPDATE CASCADE ON DELETE CASCADE;
