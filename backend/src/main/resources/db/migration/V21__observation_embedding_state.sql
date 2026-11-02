-- Persist the outcome of the embedding attempt.
--
-- Before this column the only signal was `embedding_model_id IS NULL`, which the
-- application could not distinguish from "never attempted" or "no text to embed".
-- A provider outage therefore degraded semantic search silently and permanently.
--
--   ready   : a vector is stored in one of the three columns
--   failed  : the provider call or the dimension check failed; the row is queued for
--             the backfill sweep
--   skipped : there was no text to embed; the backfill sweep must not retry it
--   NULL    : written before this migration; the backfill sweep claims it once
ALTER TABLE mem_observations
    ADD COLUMN IF NOT EXISTS embedding_state VARCHAR(16);

ALTER TABLE mem_observations
    ADD COLUMN IF NOT EXISTS embedding_error VARCHAR(64);

COMMENT ON COLUMN mem_observations.embedding_state IS
    'ready | failed | skipped; NULL for rows written before this column existed';
COMMENT ON COLUMN mem_observations.embedding_error IS
    'Short failure classification (exception simple name or transport); never message text';

-- Rows that already carry a vector are ready. Everything else keeps NULL state so the
-- first backfill pass classifies it exactly once (skipped when there is no text).
UPDATE mem_observations
SET embedding_state = 'ready'
WHERE embedding_state IS NULL
  AND (embedding_768 IS NOT NULL
       OR embedding_1024 IS NOT NULL
       OR embedding_1536 IS NOT NULL);

-- Backfill sweep: newest first, only rows with no vector that still deserve a retry.
CREATE INDEX IF NOT EXISTS idx_mem_observations_embedding_backlog
    ON mem_observations (created_at_epoch DESC)
    WHERE embedding_768 IS NULL
      AND embedding_1024 IS NULL
      AND embedding_1536 IS NULL
      AND (embedding_state IS NULL OR embedding_state = 'failed');
