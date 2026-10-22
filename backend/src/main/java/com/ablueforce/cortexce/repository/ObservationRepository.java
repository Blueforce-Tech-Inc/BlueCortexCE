package com.ablueforce.cortexce.repository;

import com.ablueforce.cortexce.entity.ObservationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ObservationRepository extends JpaRepository<ObservationEntity, UUID> {

    // Paged query with optional project and platformSource filter
    /**
     * Paged observation list with the frozen identity precedence.
     *
     * <ul>
     *   <li>{@code conversationId} set: only that conversation's rows, never standalone.</li>
     *   <li>else {@code standaloneKind} set: only typed standalone rows, scoped to the legacy
     *       correlation key when {@code contentSessionId} is also given.</li>
     *   <li>else {@code contentSessionId} set: standalone rows borrowing that key. A caller
     *       wanting a real conversation resolves the UUID first (see ViewerController).</li>
     * </ul>
     *
     * <p>The legacy sentinel {@code manual-memories} lands on the standalone branch, which is
     * its documented meaning: manual rows, never a conversation.
     */
    @Query("""
        SELECT o FROM ObservationEntity o
        WHERE (:project IS NULL OR o.memoryScopeId IN (
                 SELECT ms.id FROM com.ablueforce.cortexce.entity.MemoryScopeEntity ms
                 WHERE ms.scopeType = 'project'
                   AND ms.scopeKey = :project))
        AND (:platformSource IS NULL OR COALESCE(o.platformSource, 'claude') = :platformSource)
        AND (:conversationId IS NULL OR o.conversationId = :conversationId)
        AND (:standaloneKind IS NULL OR o.standaloneKind = :standaloneKind)
        AND ((:conversationId IS NOT NULL)
             OR (:standaloneKind IS NULL AND :contentSessionId IS NULL)
             OR o.contentSessionId = :contentSessionId)
        ORDER BY o.createdAtEpoch DESC, o.id DESC
        """)
    Page<ObservationEntity> findAllPaged(
        @Param("project") String project,
        @Param("platformSource") String platformSource,
        @Param("conversationId") UUID conversationId,
        @Param("contentSessionId") String contentSessionId,
        @Param("standaloneKind") String standaloneKind,
        Pageable pageable
    );

    /**
     * Delete every observation owned by one conversation.
     *
     * <p>Keyed on the internal UUID. Standalone rows have {@code conversation_id = NULL} and
     * can never match, so deleting a conversation cannot destroy manual memories.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ObservationEntity o WHERE o.conversationId = :conversationId")
    int deleteByConversationId(@Param("conversationId") UUID conversationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ObservationEntity o WHERE o.id = :id")
    int deleteByIdForViewer(@Param("id") UUID id);

    // Paged query with userId-based filtering (Phase 3 multi-user support)
    /**
     * Find observations by project path and content session IDs.
     * Used for userId-based filtering (Phase 3 multi-user support).
     *
     * @param projectPath Project path
     * @param sessionIds List of content session IDs
     * @param pageable Pagination
     * @return Page of observations
     */
    @Query("""
        SELECT o FROM ObservationEntity o
        WHERE o.memoryScopeId IN (SELECT ms.id FROM com.ablueforce.cortexce.entity.MemoryScopeEntity ms
                          WHERE ms.scopeType = 'project'
                            AND ms.scopeKey = :projectPath)
        AND o.conversationId IN :conversationIds
        ORDER BY o.createdAtEpoch DESC
        """)
    Page<ObservationEntity> findByProjectPathAndConversationIdIn(
        @Param("projectPath") String projectPath,
        @Param("conversationIds") List<UUID> conversationIds,
        Pageable pageable
    );

    /**
     * Find high-quality observations by project path and content session IDs.
     * Used for userId-based experience retrieval (Phase 3 multi-user support).
     *
     * @param projectPath Project path
     * @param sessionIds List of content session IDs
     * @param minQuality Minimum quality score
     * @param pageable Pagination
     * @return Page of observations
     */
    @Query("""
        SELECT o FROM ObservationEntity o
        WHERE o.memoryScopeId IN (SELECT ms.id FROM com.ablueforce.cortexce.entity.MemoryScopeEntity ms
                          WHERE ms.scopeType = 'project'
                            AND ms.scopeKey = :projectPath)
        AND o.conversationId IN :conversationIds
        AND o.qualityScore >= :minQuality
        ORDER BY o.createdAtEpoch DESC
        """)
    Page<ObservationEntity> findByProjectPathAndConversationIdInWithQuality(
        @Param("projectPath") String projectPath,
        @Param("conversationIds") List<UUID> conversationIds,
        @Param("minQuality") double minQuality,
        Pageable pageable
    );

    // Find by project, ordered by creation time (descending), limited
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByProjectLimited(
        @Param("project") String project,
        @Param("limit") int limit
    );

    // Find recent observations for a project with offset
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        ORDER BY created_at_epoch DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<ObservationEntity> findByProjectLimitedWithOffset(
        @Param("project") String project,
        @Param("limit") int limit,
        @Param("offset") int offset
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
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
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
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
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
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
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
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        AND search_vector @@ plainto_tsquery('english', :query)
        ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> fullTextSearch(
        @Param("project") String project,
        @Param("query") String query,
        @Param("limit") int limit
    );

    // Filter by source
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        AND source = :source
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findBySource(
        @Param("project") String project,
        @Param("source") String source,
        @Param("limit") int limit
    );

    // Composable AND filter with offset
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        AND (:type IS NULL OR type = :type)
        AND (:source IS NULL OR source = :source)
        AND (:concept IS NULL OR concepts @> to_jsonb(:concept))
        AND (:startEpoch IS NULL OR created_at_epoch >= :startEpoch)
        AND (:endEpoch IS NULL OR created_at_epoch <= :endEpoch)
        ORDER BY created_at_epoch DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<ObservationEntity> findByAllFiltersWithOffset(
        @Param("project") String project,
        @Param("type") String type,
        @Param("source") String source,
        @Param("concept") String concept,
        @Param("startEpoch") Long startEpoch,
        @Param("endEpoch") Long endEpoch,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    // Count by project
    long countByProjectPath(String projectPath);

    // Find all observations for a project, ordered by creation time (descending)
    //
    // Ordered by created_at_epoch, not created_at. Every hand-written @Query in
    // this repository already sorts on the epoch column, and it has to: the
    // timestamp column is nullable and in practice usually NULL, because only
    // ImportService assigns it explicitly while AgentService (the main capture
    // path) sets just createdAtEpoch, and there is no @PrePersist or JPA
    // auditing to fill it in. In PostgreSQL NULLs sort last under DESC, so
    // ordering by created_at silently returned the oldest non-NULL rows instead
    // of the newest ones.
    List<ObservationEntity> findByProjectPathOrderByCreatedAtEpochDesc(String projectPath);

    // Find recent observations for a project with pagination (avoids loading all into memory)
    // Sorted on created_at_epoch for the same reason as above.
    Page<ObservationEntity> findByProjectPathOrderByCreatedAtEpochDesc(String projectPath, Pageable pageable);

    // Find observations for a session, ordered ascending (for summary generation)
    /**
     * Observations of one conversation, oldest first (summary input).
     *
     * <p>Keyed on the internal UUID. The legacy external id stopped being a valid selector
     * as soon as two platforms could share it, and it also matched the compatibility parent
     * rows that standalone observations still reference.
     */
    @Query("""
        SELECT o FROM ObservationEntity o
        WHERE o.conversationId = :conversationId
        ORDER BY o.createdAtEpoch ASC, o.id ASC
        """)
    List<ObservationEntity> findByConversationIdOrderByCreatedAtEpochAsc(
        @Param("conversationId") UUID conversationId);

    /**
     * Standalone observations of a given kind, selected by project scope + legacy correlation
     * key + type. Used for extraction prior/latest/history, where the correlation key is a
     * compatibility value rather than a conversation identity.
     */
    @Query(value = """
        SELECT o.* FROM mem_observations o
        JOIN mem_memory_scopes ms ON ms.id = o.memory_scope_id
        WHERE ms.scope_type = 'project'
          AND ms.scope_key = cast(:project AS text) COLLATE "C"
          AND o.standalone_kind = :kind
          AND o.content_session_id = cast(:correlationKey AS text) COLLATE "C"
          AND o.type = :type
        ORDER BY o.created_at_epoch DESC, o.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findStandaloneByCorrelationKeyAndType(
        @Param("project") String project,
        @Param("kind") String kind,
        @Param("correlationKey") String correlationKey,
        @Param("type") String type,
        @Param("limit") int limit);

    // Batch: find observations across multiple sessions (avoids N+1 query)
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE conversation_id IN (:conversationIds)
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByConversationIdInOrderByCreatedAtEpochDesc(
        @Param("conversationIds") List<UUID> conversationIds,
        @Param("limit") int limit);

    // P2: Hybrid search combining pgvector semantic + tsvector full-text
    @Query(value = """
        WITH semantic_results AS (
            SELECT *, 1.0 as score_type
            FROM mem_observations
            WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
            AND embedding_1024 IS NOT NULL
            AND created_at_epoch > :minEpoch
            ORDER BY embedding_1024 <=> cast(:queryVector as vector)
            LIMIT :limit
        ),
        fulltext_results AS (
            SELECT *, 0.8 as score_type
            FROM mem_observations
            WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
            AND search_vector @@ plainto_tsquery('english', :query)
            LIMIT :limit
        )
        SELECT * FROM (
            SELECT DISTINCT ON (id) *
            FROM (
                SELECT * FROM semantic_results
                UNION ALL
                SELECT * FROM fulltext_results
            ) combined_inner
            ORDER BY id, score_type DESC
        ) deduped
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
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
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
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
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

    @Query(value = """
        SELECT * FROM mem_observations
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        AND type IN (:types)
        AND COALESCE(platform_source, 'claude') = :platformSource
        AND (:conceptsEmpty = true OR EXISTS (
            SELECT 1 FROM jsonb_array_elements_text(concepts::jsonb) elem
            WHERE elem IN (:concepts)
        ))
        ORDER BY created_at_epoch DESC, id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByTypeAndConceptsForPlatformSource(
        @Param("project") String project,
        @Param("types") List<String> types,
        @Param("concepts") List<String> concepts,
        @Param("conceptsEmpty") boolean conceptsEmpty,
        @Param("limit") int limit,
        @Param("platformSource") String platformSource
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
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        AND o.type IN (:types)
        AND (:conceptsEmpty = true OR EXISTS (
            SELECT 1 FROM jsonb_array_elements_text(o.concepts::jsonb) elem
            WHERE elem IN (:concepts)
        ))
        AND o.content_session_id IN (
            SELECT content_session_id FROM mem_observations
            WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
            GROUP BY content_session_id
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

    @Query(value = """
        SELECT o.* FROM mem_observations o
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        AND o.type IN (:types)
        AND COALESCE(o.platform_source, 'claude') = :platformSource
        AND (:conceptsEmpty = true OR EXISTS (
            SELECT 1 FROM jsonb_array_elements_text(o.concepts::jsonb) elem
            WHERE elem IN (:concepts)
        ))
        AND o.content_session_id IN (
            SELECT content_session_id FROM mem_observations
            WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
              AND COALESCE(platform_source, 'claude') = :platformSource
            GROUP BY content_session_id
            ORDER BY MAX(created_at_epoch) DESC
            LIMIT :sessionLimit
        )
        ORDER BY o.created_at_epoch DESC, o.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByTypeAndConceptsWithSessionLimitForPlatformSource(
        @Param("project") String project,
        @Param("types") List<String> types,
        @Param("concepts") List<String> concepts,
        @Param("conceptsEmpty") boolean conceptsEmpty,
        @Param("limit") int limit,
        @Param("sessionLimit") int sessionLimit,
        @Param("platformSource") String platformSource
    );

    // ==========================================================================
    // Import API - Duplicate Checking
    // ==========================================================================

    /**
     * Find observation by conversation UUID, title, and created_at_epoch for duplicate
     * checking. Keyed on the UUID so two platforms sharing an external id do not dedupe
     * against each other.
     */
    @Query("""
        SELECT o FROM ObservationEntity o
        WHERE o.conversationId = :conversationId
        AND o.title = :title
        AND o.createdAtEpoch = :createdAtEpoch
        """)
    Optional<ObservationEntity> findBySessionIdAndTitleAndCreatedAtEpoch(
        @Param("conversationId") UUID conversationId,
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
     * @param memoryScopeId Resolved scope id scoping the search — required. The backing
     *                    native query filters {@code memory_scope_id = :memoryScopeId}, so it
     *                    is not an optional refinement: omitting it would widen the search
     *                    across every scope on the instance. Two projects with the same
     *                    content hash must not suppress each other.
     * @return Existing observation if found within window, null otherwise
     */
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE content_hash = :contentHash
        AND created_at_epoch > :windowStart
        AND memory_scope_id = :memoryScopeId
        ORDER BY created_at_epoch DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<ObservationEntity> findDuplicateByContentHash(
        @Param("contentHash") String contentHash,
        @Param("windowStart") long windowStart,
        @Param("memoryScopeId") UUID memoryScopeId
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
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
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
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
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
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        AND quality_score < :threshold
        AND type NOT LIKE 'extracted_%'
        -- Standalone rows (manual/extraction/dlq) never enter destruction or rewriting.
        -- This is a hard kind check, not a source heuristic: manual memories are user-saved
        -- content and must not be deleted, merged or reworded by generic refinement.
        AND standalone_kind IS NULL
        -- Keep dead-letter records out of the refine pipeline. This used to be
        -- `type != 'extraction_failed'`, but ExtractionStorageService.storeDLQ now
        -- writes type = 'dlq_' + templateName with source = 'dlq', so that condition
        -- matched nothing and DLQ records were being returned as refinement
        -- candidates — LLM-rewritten, merged, or deleted, which defeats the point of
        -- a dead letter queue. COALESCE is required: mem_observations.source is
        -- nullable (V14) and about half of all observations have no source, so a bare
        -- `source != 'dlq'` would evaluate to NULL for them and silently drop every
        -- ordinary observation from refinement as well.
        AND COALESCE(source, '') != 'dlq'
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
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        AND (last_accessed_at IS NULL OR last_accessed_at < :accessThreshold)
        AND (quality_score IS NULL OR quality_score < :qualityThreshold)
        AND type NOT LIKE 'extracted_%'
        -- Standalone rows (manual/extraction/dlq) never enter destruction or rewriting.
        -- This is a hard kind check, not a source heuristic: manual memories are user-saved
        -- content and must not be deleted, merged or reworded by generic refinement.
        AND standalone_kind IS NULL
        -- Keep dead-letter records out of the refine pipeline. This used to be
        -- `type != 'extraction_failed'`, but ExtractionStorageService.storeDLQ now
        -- writes type = 'dlq_' + templateName with source = 'dlq', so that condition
        -- matched nothing and DLQ records were being returned as refinement
        -- candidates — LLM-rewritten, merged, or deleted, which defeats the point of
        -- a dead letter queue. COALESCE is required: mem_observations.source is
        -- nullable (V14) and about half of all observations have no source, so a bare
        -- `source != 'dlq'` would evaluate to NULL for them and silently drop every
        -- ordinary observation from refinement as well.
        AND COALESCE(source, '') != 'dlq'
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
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        AND (refined_at IS NULL OR refined_at < :cooldownThreshold)
        AND type NOT LIKE 'extracted_%'
        -- Standalone rows (manual/extraction/dlq) never enter destruction or rewriting.
        -- This is a hard kind check, not a source heuristic: manual memories are user-saved
        -- content and must not be deleted, merged or reworded by generic refinement.
        AND standalone_kind IS NULL
        -- Keep dead-letter records out of the refine pipeline. This used to be
        -- `type != 'extraction_failed'`, but ExtractionStorageService.storeDLQ now
        -- writes type = 'dlq_' + templateName with source = 'dlq', so that condition
        -- matched nothing and DLQ records were being returned as refinement
        -- candidates — LLM-rewritten, merged, or deleted, which defeats the point of
        -- a dead letter queue. COALESCE is required: mem_observations.source is
        -- nullable (V14) and about half of all observations have no source, so a bare
        -- `source != 'dlq'` would evaluate to NULL for them and silently drop every
        -- ordinary observation from refinement as well.
        AND COALESCE(source, '') != 'dlq'
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
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        """, nativeQuery = true)
    Object[] getQualityDistribution(@Param("project") String project);

    /**
     * Find all distinct project paths that have observations.
     * Used by scheduled refinement fallback.
     */
    @Query("SELECT DISTINCT o.projectPath FROM ObservationEntity o WHERE o.projectPath IS NOT NULL")
    List<String> findDistinctProjects();

    // ===== Phase 3: Structured Extraction Methods =====

    /**
     * Find observations where source is IN a list of values.
     * Required for extraction source filtering (findBySource takes single String).
     */
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        AND source IN (:sources)
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findBySourceIn(
        @Param("project") String project,
        @Param("sources") List<String> sources,
        @Param("limit") int limit);

    /**
     * Find new observations since a given epoch for incremental extraction.
     */
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        AND source IN (:sources)
        AND created_at_epoch > :sinceEpoch
        ORDER BY created_at_epoch ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findNewObservations(
        @Param("project") String project,
        @Param("sources") List<String> sources,
        @Param("sinceEpoch") Long sinceEpoch,
        @Param("limit") int limit);

    /**
     * Global type query (cross-project) for dead letter queue retry.
     */
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE type = :type
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByTypeGlobal(
        @Param("type") String type,
        @Param("limit") int limit);

    /**
     * Wildcard type query using LIKE for ICL integration.
     * findByAllFiltersWithOffset uses exact match (=), so it won't work for patterns like "extracted_%".
     */
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        AND type LIKE :typePattern
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByTypeLike(
        @Param("project") String project,
        @Param("typePattern") String typePattern,
        @Param("limit") int limit);

    /**
     * Find existing extraction by session ID + type for LLM re-extraction prior context.
     */
    /**
     * Standalone observations of one kind plus legacy correlation key and type.
     *
     * <p>Extraction output keeps its synthetic key as a compatibility value, so it is looked
     * up by that key together with the kind — never treated as a conversation identity.
     */
    @Query(value = """
        SELECT * FROM mem_observations
        WHERE standalone_kind = :kind
        AND content_session_id = cast(:correlationKey AS text) COLLATE "C"
        AND type = :type
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findStandaloneByCorrelationKeyAndType(
        @Param("correlationKey") String correlationKey,
        @Param("kind") String kind,
        @Param("type") String type,
        @Param("limit") int limit);

    /**
     * Batch fetch observations by session IDs (for bulk import duplicate detection).
     * Use to check for existing observations in bulk before calling saveAll().
     */
    /** Batch lookup by conversation UUID (import duplicate detection). */
    List<ObservationEntity> findByConversationIdIn(List<UUID> conversationIds);
}
