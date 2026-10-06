-- V19: Indexes for the paired WebUI session catalog and feed queries.

CREATE INDEX IF NOT EXISTS idx_sessions_viewer_order
    ON mem_sessions (started_at_epoch DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_observations_content_session_created
    ON mem_observations (content_session_id, created_at_epoch DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_summaries_content_session_created
    ON mem_summaries (content_session_id, created_at_epoch DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_prompts_content_session_created
    ON mem_user_prompts (content_session_id, created_at_epoch DESC, id DESC);
