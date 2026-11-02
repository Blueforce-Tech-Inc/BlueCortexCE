-- Make queue failures queryable.
--
-- Before this column a failed job could only be diagnosed by reading the application
-- log, and `/api/readiness` could not distinguish "slow" from "dead": nothing counted
-- the failed rows. The value is a short classification (LLM_RATE_LIMIT, TIMEOUT,
-- VALIDATION, ...), never exception message text, so it is safe to expose.
ALTER TABLE mem_pending_messages
    ADD COLUMN IF NOT EXISTS failure_kind VARCHAR(64);

COMMENT ON COLUMN mem_pending_messages.failure_kind IS
    'Short classification of the last failure; NULL while pending/processing/succeeded';

-- Recent failures, newest first (readiness + health details).
CREATE INDEX IF NOT EXISTS idx_mem_pending_messages_failed
    ON mem_pending_messages (failed_at_epoch DESC)
    WHERE status = 'failed';

-- Oldest pending row: the "is the queue actually moving?" signal.
CREATE INDEX IF NOT EXISTS idx_mem_pending_messages_pending_age
    ON mem_pending_messages (created_at_epoch)
    WHERE status = 'pending';
