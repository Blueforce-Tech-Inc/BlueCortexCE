-- V3: Add 'skipped' to pending_messages status CHECK constraint.
-- LLM may decide a tool-use event is trivial and skip observation generation.

ALTER TABLE mem_pending_messages DROP CONSTRAINT IF EXISTS mem_pending_messages_status_check;
ALTER TABLE mem_pending_messages ADD CONSTRAINT mem_pending_messages_status_check
    CHECK (status IN ('pending', 'processing', 'processed', 'failed', 'skipped'));
