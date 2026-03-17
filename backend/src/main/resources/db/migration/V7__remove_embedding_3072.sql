-- V7: Remove 3072-dimension embedding column
-- Rationale: pgvector HNSW/IVFFlat indexes max at 2000 dims
-- 3072-dim vectors cannot be indexed, leading to poor query performance

-- Drop the 3072-dimension embedding column
ALTER TABLE mem_observations DROP COLUMN IF EXISTS embedding_3072;
