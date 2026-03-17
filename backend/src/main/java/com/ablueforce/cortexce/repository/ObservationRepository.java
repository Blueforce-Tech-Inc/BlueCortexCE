package com.ablueforce.cortexce.repository;

import com.ablueforce.cortexce.entity.ObservationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ObservationRepository extends JpaRepository<ObservationEntity, UUID> {

    // Paged query with optional project filter
    @Query("""
        SELECT o FROM ObservationEntity o
        WHERE (:project IS NULL OR o.projectPath = :project)
        ORDER BY o.createdAtEpoch DESC
        """)
    Page<ObservationEntity> findAllPaged(
        @Param("project") String project,
        Pageable pageable
    );

    // Find by project, ordered by creation time (descending), limited
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path = :project
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByProjectLimited(
        @Param("project") String project,
        @Param("limit") int limit
    );

    // Find by multiple projects (worktree support)
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path IN (:projects)
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByProjectsLimited(
        @Param("projects") List<String> projects,
        @Param("limit") int limit
    );

    // pgvector semantic search on embedding_768
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path = :project
        AND embedding_768 IS NOT NULL
        AND created_at_epoch > :minEpoch
        ORDER BY embedding_768 <=> cast(:queryVector as vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> semanticSearch768(
        @Param("project") String project,
        @Param("queryVector") String queryVector,
        @Param("minEpoch") long minEpoch,
        @Param("limit") int limit
    );

    // pgvector semantic search on embedding_1024 (default for bge-m3)
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path = :project
        AND embedding_1024 IS NOT NULL
        AND created_at_epoch > :minEpoch
        ORDER BY embedding_1024 <=> cast(:queryVector as vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> semanticSearch1024(
        @Param("project") String project,
        @Param("queryVector") String queryVector,
        @Param("minEpoch") long minEpoch,
        @Param("limit") int limit
    );

    // pgvector semantic search on embedding_1536
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path = :project
        AND embedding_1536 IS NOT NULL
        AND created_at_epoch > :minEpoch
        ORDER BY embedding_1536 <=> cast(:queryVector as vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> semanticSearch1536(
        @Param("project") String project,
        @Param("queryVector") String queryVector,
        @Param("minEpoch") long minEpoch,
        @Param("limit") int limit
    );

    // tsvector full-text search
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path = :project
        AND search_vector @@ plainto_tsquery('english', :query)
        ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> fullTextSearch(
        @Param("project") String project,
        @Param("query") String query,
        @Param("limit") int limit
    );

    // Filter by type
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path = :project
        AND type = :type
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByType(
        @Param("project") String project,
        @Param("type") String type,
        @Param("limit") int limit
    );

    // Filter by concept (JSONB array exact match) - aligned with TS SessionSearch.ts buildFilterClause
    // Uses jsonb_array_elements_text for proper text comparison (exact match, not LIKE)
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path = :project
        AND EXISTS (
            SELECT 1 FROM jsonb_array_elements_text(concepts::jsonb) elem
            WHERE elem = :concept
        )
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByConceptContaining(
        @Param("project") String project,
        @Param("concept") String concept,
        @Param("limit") int limit
    );

    // Advanced combined filter
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path = :project
        AND (:type IS NULL OR type = :type)
        AND created_at_epoch BETWEEN :startEpoch AND :endEpoch
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> advancedSearch(
        @Param("project") String project,
        @Param("type") String type,
        @Param("startEpoch") long startEpoch,
        @Param("endEpoch") long endEpoch,
        @Param("limit") int limit
    );

    // Count by project
    long countByProjectPath(String projectPath);

    // Find all observations for a project, ordered by creation time (descending)
    List<ObservationEntity> findByProjectPathOrderByCreatedAtDesc(String projectPath);

    // Find observations for a session, ordered ascending (for summary generation)
    List<ObservationEntity> findByMemorySessionIdOrderByCreatedAtEpochAsc(String memorySessionId);

    // P2: Hybrid search combining pgvector semantic + tsvector full-text
    @Query(value = """
        WITH semantic_results AS (
            SELECT *, 1.0 as score_type
            FROM mem_observations
            WHERE project_path = :project
            AND embedding_1024 IS NOT NULL
            AND created_at_epoch > :minEpoch
            ORDER BY embedding_1024 <=> cast(:queryVector as vector)
            LIMIT :limit
        ),
        fulltext_results AS (
            SELECT *, 0.8 as score_type
            FROM mem_observations
            WHERE project_path = :project
            AND search_vector @@ plainto_tsquery('english', :query)
            LIMIT :limit
        )
        SELECT * FROM (
            SELECT * FROM semantic_results
            UNION ALL
            SELECT * FROM fulltext_results
        ) combined
        ORDER BY
            CASE WHEN score_type = 1.0 THEN
                (1.0 - (embedding_1024 <=> cast(:queryVector as vector)))
            ELSE
                ts_rank(search_vector, plainto_tsquery('english', :query))
            END DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> hybridSearch(
        @Param("project") String project,
        @Param("query") String query,
        @Param("queryVector") String queryVector,
        @Param("minEpoch") long minEpoch,
        @Param("limit") int limit
    );

    // P2: Timeline aggregation by date (group observations by date)
    @Query(value = """
        SELECT
            DATE(to_timestamp(created_at_epoch / 1000)) as obs_date,
            COUNT(*) as count,
            ARRAY_AGG(id ORDER BY created_at_epoch DESC) as ids
        FROM mem_observations
        WHERE project_path = :project
        AND created_at_epoch BETWEEN :startEpoch AND :endEpoch
        GROUP BY DATE(to_timestamp(created_at_epoch / 1000))
        ORDER BY obs_date DESC
        """, nativeQuery = true)
    List<Object[]> findTimelineByDate(
        @Param("project") String project,
        @Param("startEpoch") long startEpoch,
        @Param("endEpoch") long endEpoch
    );

    // P0: Query observations with type and concept filtering (aligned with TS queryObservations)
    // Filters by type IN (:types) AND (concepts is empty OR concepts JSONB contains any of (:concepts))
    // P1: Uses parameterized binding - concepts list is validated before query
    // FIX: Using jsonb_array_elements_text for proper text comparison
    // FIX: When concepts is empty, skip the concepts filter entirely
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path = :project
        AND type IN (:types)
        AND (:conceptsEmpty = true OR EXISTS (
            SELECT 1 FROM jsonb_array_elements_text(concepts::jsonb) elem
            WHERE elem IN (:concepts)
        ))
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByTypeAndConcepts(
        @Param("project") String project,
        @Param("types") List<String> types,
        @Param("concepts") List<String> concepts,
        @Param("conceptsEmpty") boolean conceptsEmpty,
        @Param("limit") int limit
    );

    // P0: Query observations for multiple projects with type and concept filtering (worktree support)
    // FIX: Using jsonb_array_elements_text for proper text comparison
    // FIX: When concepts is empty, skip the concepts filter entirely
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path IN (:projects)
        AND type IN (:types)
        AND (:conceptsEmpty = true OR EXISTS (
            SELECT 1 FROM jsonb_array_elements_text(concepts::jsonb) elem
            WHERE elem IN (:concepts)
        ))
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByProjectsTypesAndConcepts(
        @Param("projects") List<String> projects,
        @Param("types") List<String> types,
        @Param("concepts") List<String> concepts,
        @Param("conceptsEmpty") boolean conceptsEmpty,
        @Param("limit") int limit
    );

    /**
     * Find observations with type/concept filtering, limited to recent sessions.
     * Used by Preview API to respect sessionCount setting.
     */
    @Query(value = """
        SELECT o.* FROM mem_observations o
        WHERE o.project_path = :project
        AND o.type IN (:types)
        AND (:conceptsEmpty = true OR EXISTS (
            SELECT 1 FROM jsonb_array_elements_text(o.concepts::jsonb) elem
            WHERE elem IN (:concepts)
        ))
        AND o.memory_session_id IN (
            SELECT memory_session_id FROM mem_observations
            WHERE project_path = :project
            GROUP BY memory_session_id
            ORDER BY MAX(created_at_epoch) DESC
            LIMIT :sessionLimit
        )
        ORDER BY o.created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByTypeAndConceptsWithSessionLimit(
        @Param("project") String project,
        @Param("types") List<String> types,
        @Param("concepts") List<String> concepts,
        @Param("conceptsEmpty") boolean conceptsEmpty,
        @Param("limit") int limit,
        @Param("sessionLimit") int sessionLimit
    );

    // ==========================================================================
    // Import API - Duplicate Checking
    // ==========================================================================

    /**
     * Find observation by session ID, title, and created_at_epoch for duplicate checking.
     * Used by Import API to prevent duplicate imports.
     */
    @Query("""
        SELECT o FROM ObservationEntity o
        WHERE o.memorySessionId = :sessionId
        AND o.title = :title
        AND o.createdAtEpoch = :createdAtEpoch
        """)
    Optional<ObservationEntity> findBySessionIdAndTitleAndCreatedAtEpoch(
        @Param("sessionId") String sessionId,
        @Param("title") String title,
        @Param("createdAtEpoch") long createdAtEpoch
    );

    // ==========================================================================
    // Observation Deduplication (Migration 22 alignment)
    // ==========================================================================

    /**
     * Find duplicate observation by content_hash within a time window.
     * Aligns with TypeScript version's DEDUP_WINDOW_MS = 30000 (30 seconds).
     *
     * @param contentHash SHA-256 hash of observation content (16 chars)
     * @param windowStart Epoch timestamp for the start of the dedup window
     * @return Existing observation if found within window, null otherwise
     */
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE content_hash = :contentHash
        AND created_at_epoch > :windowStart
        ORDER BY created_at_epoch DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<ObservationEntity> findDuplicateByContentHash(
        @Param("contentHash") String contentHash,
        @Param("windowStart") long windowStart
    );

    // ==========================================================================
    // Folder CLAUDE.md Support - Search by file path
    // ==========================================================================

    /**
     * Find observations that have files_read or files_modified matching a folder path.
     * Used for folder-level CLAUDE.md generation (TS alignment).
     *
     * @param project Project path
     * @param folderPath Folder path to match (with trailing slash for prefix matching)
     * @param limit Maximum results
     * @return Observations related to the folder
     */
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path = :project
        AND (
            EXISTS (
                SELECT 1 FROM jsonb_array_elements_text(files_read::jsonb) elem
                WHERE elem LIKE CONCAT(:folderPath, '%')
                   OR elem = LEFT(:folderPath, LENGTH(:folderPath) - 1)
            )
            OR EXISTS (
                SELECT 1 FROM jsonb_array_elements_text(files_modified::jsonb) elem
                WHERE elem LIKE CONCAT(:folderPath, '%')
                   OR elem = LEFT(:folderPath, LENGTH(:folderPath) - 1)
            )
        )
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByFolderPath(
        @Param("project") String project,
        @Param("folderPath") String folderPath,
        @Param("limit") int limit
    );

    // ==========================================================================
    // Quality Score Support (V11)
    // ==========================================================================

    /**
     * Find observations with quality score above threshold.
     * Used for quality-aware retrieval.
     *
     * @param project Project path
     * @param minQuality Minimum quality score [0, 1]
     * @param limit Maximum results
     * @return High-quality observations
     */
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path = :project
        AND quality_score >= :minQuality
        ORDER BY quality_score DESC, created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findHighQualityObservations(
        @Param("project") String project,
        @Param("minQuality") float minQuality,
        @Param("limit") int limit
    );

    /**
     * Find observations with low quality score (candidates for deletion during refine).
     * Used by MemoryRefineService.
     *
     * @param project Project path
     * @param threshold Quality threshold for deletion
     * @return Low-quality observations
     */
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path = :project
        AND quality_score < :threshold
        ORDER BY quality_score ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findLowQualityObservations(
        @Param("project") String project,
        @Param("threshold") float threshold,
        @Param("limit") int limit
    );

    /**
     * Find stale observations (not accessed for specified days, quality below threshold).
     * Used for cleanup during refine.
     *
     * @param project Project path
     * @param daysThreshold Days since last access
     * @param qualityThreshold Quality threshold
     * @return Stale observations
     */
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path = :project
        AND (last_accessed_at IS NULL OR last_accessed_at < :accessThreshold)
        AND (quality_score IS NULL OR quality_score < :qualityThreshold)
        ORDER BY quality_score ASC NULLS FIRST
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findStaleObservations(
        @Param("project") String project,
        @Param("accessThreshold") OffsetDateTime accessThreshold,
        @Param("qualityThreshold") float qualityThreshold,
        @Param("limit") int limit
    );

    /**
     * Find observations overdue for refinement (not refined within cooldown period).
     * Cooldown period is 7 days.
     *
     * @param project Project path
     * @param cooldownThreshold Timestamp for cooldown threshold
     * @return Observations ready for refinement
     */
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE project_path = :project
        AND (refined_at IS NULL OR refined_at < :cooldownThreshold)
        ORDER BY refined_at ASC NULLS FIRST, quality_score ASC NULLS FIRST
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findOverdueForRefine(
        @Param("project") String project,
        @Param("cooldownThreshold") OffsetDateTime cooldownThreshold,
        @Param("limit") int limit
    );

    /**
     * Count observations by quality range.
     * Used for quality distribution analysis.
     */
    @Query(value = """
        SELECT
            COUNT(CASE WHEN quality_score >= 0.7 THEN 1 END) as high,
            COUNT(CASE WHEN quality_score >= 0.4 AND quality_score < 0.7 THEN 1 END) as medium,
            COUNT(CASE WHEN quality_score < 0.4 AND quality_score IS NOT NULL THEN 1 END) as low,
            COUNT(CASE WHEN quality_score IS NULL THEN 1 END) as unknown
        FROM mem_observations
        WHERE project_path = :project
        """, nativeQuery = true)
    Object[] getQualityDistribution(@Param("project") String project);

    /**
     * Find all distinct project paths that have observations.
     * Used by scheduled refinement fallback.
     */
    @Query("SELECT DISTINCT o.projectPath FROM ObservationEntity o WHERE o.projectPath IS NOT NULL")
    List<String> findDistinctProjects();
}
