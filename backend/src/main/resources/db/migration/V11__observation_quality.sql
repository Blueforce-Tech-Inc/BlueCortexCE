-- V11__observation_quality.sql
-- 添加记忆质量评分相关字段
-- 参考: docs/drafts/evo-memory-paper-analysis.md Section 6.1.1

-- 1. 添加质量评分字段
ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS quality_score FLOAT;

-- 2. 添加反馈类型字段
ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS feedback_type VARCHAR(20);

-- 3. 添加最后访问时间
ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS last_accessed_at TIMESTAMP WITH TIME ZONE;

-- 4. 添加访问次数
ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS access_count INT DEFAULT 0;

-- 5. 添加最后精炼时间
ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS refined_at TIMESTAMP WITH TIME ZONE;

-- 6. 添加精炼来源ID（合并前的原始记录ID）
ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS refined_from_ids TEXT;

-- 7. 添加用户评论
ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS user_comment TEXT;

-- 8. 添加反馈更新时间
ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS feedback_updated_at TIMESTAMP WITH TIME ZONE;

-- 9. 创建索引优化检索性能
CREATE INDEX IF NOT EXISTS idx_obs_quality_score ON mem_observations(quality_score DESC);
CREATE INDEX IF NOT EXISTS idx_obs_last_accessed ON mem_observations(last_accessed_at);
CREATE INDEX IF NOT EXISTS idx_obs_refined_at ON mem_observations(refined_at);
CREATE INDEX IF NOT EXISTS idx_obs_feedback_type ON mem_observations(feedback_type);

-- 10. 为 sessions 表添加相关字段（用于步骤效率追踪）
ALTER TABLE mem_sessions
ADD COLUMN IF NOT EXISTS total_steps INT DEFAULT 0;

ALTER TABLE mem_sessions
ADD COLUMN IF NOT EXISTS avg_steps_per_task FLOAT;

COMMENT ON COLUMN mem_observations.quality_score IS 'Quality score [0, 1] - higher is better';
COMMENT ON COLUMN mem_observations.feedback_type IS 'Feedback type: SUCCESS/PARTIAL/FAILURE/UNKNOWN';
COMMENT ON COLUMN mem_observations.last_accessed_at IS 'Last accessed timestamp for recency scoring';
COMMENT ON COLUMN mem_observations.access_count IS 'Number of times this memory was retrieved';
COMMENT ON COLUMN mem_observations.refined_at IS 'Last refinement timestamp';
COMMENT ON COLUMN mem_observations.refined_from_ids IS 'Comma-separated IDs of merged observations';
COMMENT ON COLUMN mem_observations.user_comment IS 'User comment from WebUI feedback';
COMMENT ON COLUMN mem_observations.feedback_updated_at IS 'Timestamp when feedback was last updated';
