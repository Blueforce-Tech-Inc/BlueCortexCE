-- V18: Add platform_source column for multi-platform tracking
-- Supports claude/codex filtering in WebUI

-- Sessions
ALTER TABLE mem_sessions ADD COLUMN IF NOT EXISTS platform_source VARCHAR(50) DEFAULT 'claude';
CREATE INDEX IF NOT EXISTS idx_sessions_platform_source ON mem_sessions(platform_source);

-- Observations
ALTER TABLE mem_observations ADD COLUMN IF NOT EXISTS platform_source VARCHAR(50) DEFAULT 'claude';
CREATE INDEX IF NOT EXISTS idx_observations_platform_source ON mem_observations(platform_source);

-- Summaries
ALTER TABLE mem_summaries ADD COLUMN IF NOT EXISTS platform_source VARCHAR(50) DEFAULT 'claude';
CREATE INDEX IF NOT EXISTS idx_summaries_platform_source ON mem_summaries(platform_source);

-- User Prompts
ALTER TABLE mem_user_prompts ADD COLUMN IF NOT EXISTS platform_source VARCHAR(50) DEFAULT 'claude';
CREATE INDEX IF NOT EXISTS idx_user_prompts_platform_source ON mem_user_prompts(platform_source);

COMMENT ON COLUMN mem_sessions.platform_source IS 'Source platform (claude, codex, etc.)';
COMMENT ON COLUMN mem_observations.platform_source IS 'Source platform (claude, codex, etc.)';
COMMENT ON COLUMN mem_summaries.platform_source IS 'Source platform (claude, codex, etc.)';
COMMENT ON COLUMN mem_user_prompts.platform_source IS 'Source platform (claude, codex, etc.)';
