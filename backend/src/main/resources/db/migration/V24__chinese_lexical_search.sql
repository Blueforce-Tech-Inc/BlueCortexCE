-- =============================================================================
-- V24: make the lexical half of search work for Chinese.
--
-- Why this exists: `search_vector` was built with `to_tsvector('english', ...)`.
-- Chinese text has no spaces, so the English analyzer produced one lexeme per
-- whole paragraph and `plainto_tsquery` (conjunctive) could never match it.
-- Measured on the 60-query real-history corpus, the lexical path returned an
-- empty result for 60/60 Chinese queries, which silently turned "hybrid" into
-- "pure vector".
--
-- What replaced it: `search_tokens`, a generated tsvector over tokens produced
-- by `cortex_tokens()`. The tokenizer keeps ASCII identifiers whole (so
-- `AssetRepository.findProjectMedia` still matches) and splits CJK runs into
-- overlapping character bigrams (a standard, dictionary-free way to index
-- Chinese). The query side runs the same function, then scores each row by how
-- many of the query's tokens it contains. File paths are indexed at weight C:
-- measured hit@1 0.767 / MRR 0.805 against 0.733 / 0.788 without them, and a
-- query that names a file should be able to match it.
--
-- Why not pg_trgm: it is installable in this image (1.6) and was measured.
-- `word_similarity()` scored hit@5 0.20 (a whole long prompt is not similar to
-- one document), and a `LIKE ANY(<query bigrams>)` candidate filter over a
-- gin_trgm_ops index matched essentially every row and took 7.6 s on a 100k-row
-- table, because "match at least one common bigram" is not selective. The
-- tsvector + GIN coverage query below returns in 0.3 s at the same size and
-- reaches hit@5 0.867. `pg_bigm` and `zhparser` are not present in the image and
-- require a compile-time install, so they were excluded.
--
-- Rollback: this is a one-way migration in the same sense as V13/V16.
--   ALTER TABLE mem_observations ADD COLUMN search_vector tsvector
--     GENERATED ALWAYS AS (
--       setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
--       setweight(to_tsvector('english', coalesce(content, '')), 'B')
--     ) STORED;
--   CREATE INDEX idx_obs_search ON mem_observations USING gin (search_vector);
--   DROP INDEX IF EXISTS idx_obs_search_tokens;
--   ALTER TABLE mem_observations DROP COLUMN IF EXISTS search_tokens;
--   DROP FUNCTION IF EXISTS cortex_tokens(text);
-- Dropping the generated column rewrites the table; on a large database expect
-- a full table rewrite in both directions.
-- =============================================================================

-- Tokenizer used by both the stored column and the query side, so the two can
-- never drift. Immutable because a generated column may only call immutable
-- functions; it is deterministic (pure string/regex work on its argument).
-- It returns '' (never NULL) for input with no tokens: a NULL operand would
-- poison the whole `||` chain and leave the column empty.
CREATE OR REPLACE FUNCTION cortex_tokens(input text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT AS $$
    WITH words AS (
        SELECT unnest(regexp_split_to_array(lower($1), '[^a-zA-Z0-9一-鿿]+')) AS w
    ), parts AS (
        -- A run of CJK characters is indexed as overlapping bigrams; everything
        -- else (identifiers, numbers, single characters) is kept as one token.
        SELECT CASE WHEN w ~ '[一-鿿]'
                    THEN (SELECT string_agg(substr(w, i, 2), ' ')
                          FROM generate_series(1, length(w) - 1) AS i)
                    ELSE w END AS p
        FROM words WHERE length(w) >= 2
        UNION ALL
        SELECT w FROM words WHERE length(w) = 1
    )
    SELECT coalesce(string_agg(p, ' '), '') FROM parts;
$$;

COMMENT ON FUNCTION cortex_tokens(text) IS
    'CJK character bigrams + whole ASCII tokens; shared by the stored column and query side.';

-- The English tsvector had no matching path for Chinese and is superseded.
-- Dropping it also drops idx_obs_search (Postgres drops indexes that depend on
-- the column).
ALTER TABLE mem_observations DROP COLUMN IF EXISTS search_vector;

ALTER TABLE mem_observations ADD COLUMN IF NOT EXISTS search_tokens tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', cortex_tokens(coalesce(title, ''))), 'A') ||
        setweight(to_tsvector('simple', cortex_tokens(coalesce(content, ''))), 'B') ||
        setweight(to_tsvector('simple', cortex_tokens(coalesce(files_modified::text, ''))), 'C')
    ) STORED;

CREATE INDEX IF NOT EXISTS idx_obs_search_tokens
    ON mem_observations USING gin (search_tokens);

COMMENT ON INDEX idx_obs_search_tokens IS
    'GIN over CJK bigram tokens: serves fullTextSearchScored token-coverage ranking.';
