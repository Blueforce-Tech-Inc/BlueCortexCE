-- V14: Add source and extracted_data fields to mem_observations
--
-- Gap 2: Source attribution (tool_result, user_statement, llm_inference, manual)
-- Gap 3: Structured data storage for typed key-value preferences

-- Add source column (String)
ALTER TABLE mem_observations
ADD COLUMN source TEXT;

-- Add extracted_data column (JSONB)
ALTER TABLE mem_observations
ADD COLUMN extracted_data JSONB;

-- Index for source-based filtering (useful for queries)
CREATE INDEX idx_obs_source ON mem_observations(source);

-- GIN index for JSONB queries on extracted_data
CREATE INDEX idx_obs_extracted_data_gin ON mem_observations USING GIN (extracted_data jsonb_path_ops);
