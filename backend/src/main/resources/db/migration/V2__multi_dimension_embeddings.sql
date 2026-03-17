-- V2: Multi-dimension embedding columns + embedding model tracking
-- Replaces the single embedding vector(768) with multiple dimension columns.
-- All embedding columns are nullable — populated based on the model used.

-- 1. Drop old HNSW index on the single embedding column
DROP INDEX IF EXISTS idx_obs_embedding;

-- 2. Drop old single embedding column
ALTER TABLE mem_observations DROP COLUMN IF EXISTS embedding;

-- 3. Add multi-dimension embedding columns (common sizes)
ALTER TABLE mem_observations ADD COLUMN embedding_768 vector(768);
ALTER TABLE mem_observations ADD COLUMN embedding_1024 vector(1024);
ALTER TABLE mem_observations ADD COLUMN embedding_1536 vector(1536);
ALTER TABLE mem_observations ADD COLUMN embedding_3072 vector(3072);

-- 4. Add embedding model tracking column
ALTER TABLE mem_observations ADD COLUMN embedding_model_id VARCHAR(255);

-- 5. Create indexes for each dimension (cosine distance)
-- HNSW for dims <= 2000 (pgvector limit: both HNSW and IVFFlat max 2000 dims)
CREATE INDEX idx_obs_embedding_768 ON mem_observations USING hnsw (embedding_768 vector_cosine_ops);
CREATE INDEX idx_obs_embedding_1024 ON mem_observations USING hnsw (embedding_1024 vector_cosine_ops);
CREATE INDEX idx_obs_embedding_1536 ON mem_observations USING hnsw (embedding_1536 vector_cosine_ops);
-- 3072 dims exceeds pgvector index limit (2000); relies on sequential scan
-- Can add an index later if pgvector lifts the limit or via dimensionality reduction

-- 6. Index on embedding_model_id for filtering
CREATE INDEX idx_obs_embedding_model ON mem_observations(embedding_model_id);
