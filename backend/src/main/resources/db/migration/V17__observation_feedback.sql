-- V17: Observation Feedback Tracking + Extended Observation Columns
-- Foundation for Thompson Sampling optimization

-- Create observation_feedback table
CREATE TABLE IF NOT EXISTS observation_feedback (
    id BIGSERIAL PRIMARY KEY,
    observation_id UUID NOT NULL,
    signal_type VARCHAR(50) NOT NULL,
    session_db_id UUID,
    created_at_epoch BIGINT NOT NULL,
    metadata TEXT,
    CONSTRAINT fk_feedback_observation
        FOREIGN KEY (observation_id) REFERENCES mem_observations(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_feedback_observation ON observation_feedback(observation_id);
CREATE INDEX IF NOT EXISTS idx_feedback_signal ON observation_feedback(signal_type);
CREATE INDEX IF NOT EXISTS idx_feedback_session ON observation_feedback(session_db_id);

-- Add columns to mem_observations
ALTER TABLE mem_observations ADD COLUMN IF NOT EXISTS generated_by_model VARCHAR(100);
ALTER TABLE mem_observations ADD COLUMN IF NOT EXISTS relevance_count INTEGER DEFAULT 0;

COMMENT ON TABLE observation_feedback IS 'Tracks observation usage signals';
COMMENT ON COLUMN observation_feedback.signal_type IS 'semantic_inject, search_hit, explicit_retrieval';
COMMENT ON COLUMN mem_observations.generated_by_model IS 'Model that generated this observation (e.g., claude-sonnet-4-20250514)';
COMMENT ON COLUMN mem_observations.relevance_count IS 'Times observation was reused in context injection or search';
