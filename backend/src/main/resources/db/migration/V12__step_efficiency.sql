-- V12__step_efficiency.sql
-- Step efficiency tracking for performance analysis
-- Reference: Evo-Memory paper Section 6.2.3 - Step Efficiency Tracking

-- Add efficiency metrics to sessions table
ALTER TABLE mem_sessions
ADD COLUMN IF NOT EXISTS total_steps INT DEFAULT 0;

ALTER TABLE mem_sessions
ADD COLUMN IF NOT EXISTS avg_steps_per_task FLOAT;

-- Add step count to observations for fine-grained tracking
ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS step_number INT;

-- Create index for efficiency queries
CREATE INDEX IF NOT EXISTS idx_sessions_avg_steps ON mem_sessions(avg_steps_per_task DESC);

COMMENT ON COLUMN mem_sessions.total_steps IS 'Total steps taken in this session';
COMMENT ON COLUMN mem_sessions.avg_steps_per_task IS 'Average steps per task in this session';
COMMENT ON COLUMN mem_observations.step_number IS 'Step number within the session';
