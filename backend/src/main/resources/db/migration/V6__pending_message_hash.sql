-- Add tool_input_hash column for deduplication
ALTER TABLE mem_pending_messages ADD COLUMN IF NOT EXISTS tool_input_hash VARCHAR(64);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_pending_messages_hash ON mem_pending_messages(content_session_id, tool_name, tool_input_hash);
