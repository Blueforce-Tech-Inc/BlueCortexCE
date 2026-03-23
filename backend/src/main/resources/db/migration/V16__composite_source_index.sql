-- V16: Replace single-column source index with composite (project_path, source) index
--
-- Issue: idx_obs_source only covers the source column, but all queries that filter
-- by source also filter by project_path. A composite index enables index-only scans
-- and avoids filtering rows from different projects.
--
-- Affected queries:
--   ObservationRepository.findBySource(project, source, limit)
--   ObservationRepository.findByAllFilters(project, type, source, concept, ...)
--   ObservationRepository.findBySourceIn(project, sources, limit)
--   ObservationRepository.findNewObservations(project, sources, sinceEpoch, limit)

DROP INDEX IF EXISTS idx_obs_source;

CREATE INDEX idx_obs_project_source
    ON mem_observations (project_path, source);
