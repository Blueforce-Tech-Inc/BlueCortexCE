package com.ablueforce.cortexce.repository;

import com.ablueforce.cortexce.entity.ObservationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ObservationRepository extends JpaRepository<ObservationEntity, UUID> {

    String OBSERVATION_ENTITY_SELECT = """
        SELECT o.*,
               (SELECT ms.scope_type FROM mem_memory_scopes ms WHERE ms.id = o.memory_scope_id)
                   AS "memoryScopeType"
        FROM mem_observations o
        """;

    /**
     * Scope predicate for one project.
     *
     * <p>The previous form was {@code memory_scope_id IN (SELECT ... WHERE a OR b)}. That
     * made the subquery a hash-join member, so the scope column was never visible as an
     * index condition: a type-filtered page scanned every row of the type (20% of the
     * table), sorted them, and only then applied {@code LIMIT} — 29 ms p95 at 100k rows.
     *
     * <p>Each branch is now its own scalar {@code InitPlan}, so the planner sees a constant
     * scope id and can use the V25 composite index {@code (memory_scope_id, type,
     * created_at_epoch, id)} as both filter and ordering. Semantics are unchanged, including
     * the {@code manual-memories} sentinel, which still matches the project scope and the
     * manual-default scope. {@code LIMIT 1} keeps the scalar subquery single-valued even if
     * a digest collision ever put two rows under one key.
     */
    String PROJECT_SCOPE_FILTER = "\n" + """
        (
            memory_scope_id = (
                SELECT id FROM mem_memory_scopes
                WHERE scope_type = 'project'
                  AND scope_key = cast(:project AS text) COLLATE "C"
                LIMIT 1)
            OR (cast(:project AS text) COLLATE "C" = 'manual-memories' COLLATE "C"
                AND memory_scope_id = (
                    SELECT id FROM mem_memory_scopes
                    WHERE scope_type = 'manual_default'
                      AND scope_key = 'manual-default-v1'
                    LIMIT 1))
        )
        """;

    String MULTI_PROJECT_SCOPE_FILTER = "\n" + """
        memory_scope_id IN (
            SELECT id FROM mem_memory_scopes
            WHERE scope_type = 'project' AND scope_key COLLATE "C" IN (:projects)
            UNION ALL
            SELECT id FROM mem_memory_scopes
            WHERE scope_type = 'manual_default'
              AND scope_key = 'manual-default-v1'
              AND 'manual-memories' COLLATE "C" IN (:projects)
        )
        """;

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
        WHERE (
                 (:project IS NULL AND :includeManualDefault = false)
                 OR (:project IS NOT NULL AND o.memoryScopeId IN (
                     SELECT ms.id FROM com.ablueforce.cortexce.entity.MemoryScopeEntity ms
                     WHERE ms.scopeType = 'project'
                       AND ms.scopeKey = :project))
                 OR (:includeManualDefault = true AND o.memoryScopeId IN (
                     SELECT ms.id FROM com.ablueforce.cortexce.entity.MemoryScopeEntity ms
                     WHERE ms.scopeType = 'manual_default'
                       AND ms.scopeKey = 'manual-default-v1')))
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
        @Param("includeManualDefault") boolean includeManualDefault,
        Pageable pageable
    );

    List<ObservationEntity> findAllByIdInAndMemoryScopeIdIn(
        List<UUID> ids,
        List<UUID> memoryScopeIds
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByProjectLimited(
        @Param("project") String project,
        @Param("limit") int limit
    );

    // Find recent observations for a project with offset
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
        ORDER BY created_at_epoch DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<ObservationEntity> findByProjectLimitedWithOffset(
        @Param("project") String project,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    // Find by multiple projects (worktree support)
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + MULTI_PROJECT_SCOPE_FILTER + """
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByProjectsLimited(
        @Param("projects") List<String> projects,
        @Param("limit") int limit
    );

    // pgvector semantic search on embedding_768
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
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

    /**
     * Lexical search returning {@code [id, matched_token_count]} rows.
     *
     * <p>Both halves use {@code cortex_tokens()} (V24): ASCII identifiers stay whole and CJK
     * runs become overlapping character bigrams, which is what makes a Chinese query able to
     * match at all — the previous {@code to_tsvector('english')} column produced no lexeme for
     * an unspaced Chinese paragraph, so the lexical path was empty for every Chinese query.
     *
     * <p>The score is the number of distinct query tokens the row contains. This is a
     * coverage metric, not {@code ts_rank}: ranking by {@code ts_rank} over the same tokens
     * measured hit@5 0.550 against 0.867 for coverage on the 60-query corpus, because
     * {@code ts_rank} rewards repetition and document length instead of query coverage.
     *
     * <p>Each token is looked up through the GIN index on {@code search_tokens}, so the
     * aggregate is index-driven rather than a sequential {@code LIKE} scan (a
     * {@code LIKE ANY(<bigrams>)} filter over gin_trgm_ops matched every row and took 7.6 s
     * on 100k rows; this form takes 0.3 s).
     */
    @Query(value = """
        WITH qb AS (
            SELECT DISTINCT unnest(string_to_array(cortex_tokens(:query), ' ')) AS tok
        )
        SELECT o.id, count(*)::double precision AS score
        FROM qb
        JOIN mem_observations o
          ON o.search_tokens @@ to_tsquery('simple', quote_literal(qb.tok))
        WHERE """ + PROJECT_SCOPE_FILTER + """
        GROUP BY o.id
        ORDER BY score DESC, o.id
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> fullTextSearchScored(
        @Param("project") String project,
        @Param("query") String query,
        @Param("limit") int limit
    );

    // Filter by source
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
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
    @Query(value = """
        SELECT count(*) FROM mem_observations
        WHERE """ + PROJECT_SCOPE_FILTER + """
        """, nativeQuery = true)
    long countByProjectPath(@Param("project") String projectPath);

    /**
     * Predicate for "this row still needs an embedding", shared by the backlog queries and
     * the conditional writes of the repair sweep.
     *
     * <p>It is intentionally repeated inside every UPDATE: a sweep and a worker (or two
     * sweeps) can touch the same row, and the write must be a claim, not a blind overwrite.
     * Rows with {@code embedding_state='skipped'} are excluded because they carry no text
     * to embed; rows written before V21 have a NULL state and are claimed exactly once.
     */
    String EMBEDDING_MISSING_PREDICATE = """
        embedding_768 IS NULL
          AND embedding_1024 IS NULL
          AND embedding_1536 IS NULL
          AND (embedding_state IS NULL OR embedding_state = 'failed')
        """;

    /**
     * Rows whose embedding attempt failed or never happened, newest first.
     */
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE
        """ + EMBEDDING_MISSING_PREDICATE + """
        ORDER BY o.created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findMissingEmbeddings(@Param("limit") int limit);

    /** How many of those rows one id still needs. A 0 means "already done, do not embed". */
    @Query(value = """
        SELECT count(*) FROM mem_observations
        WHERE id = :id
          AND
        """ + EMBEDDING_MISSING_PREDICATE, nativeQuery = true)
    long countEmbeddingMissingById(@Param("id") UUID id);

    /**
     * Claim one row by writing only its vector columns.
     *
     * <p>The UPDATE re-checks {@link #EMBEDDING_MISSING_PREDICATE}: whichever writer first
     * moves the row out of that state wins, and the loser gets 0 affected rows instead of
     * overwriting a completed row (or the body a worker just edited).
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE mem_observations
        SET embedding_768 = cast(:vector as vector),
            embedding_model_id = :model,
            embedding_state = 'ready',
            embedding_error = NULL
        WHERE id = :id
          AND
        """ + EMBEDDING_MISSING_PREDICATE, nativeQuery = true)
    int applyEmbedding768IfMissing(@Param("id") UUID id,
                                   @Param("vector") String vector,
                                   @Param("model") String model);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE mem_observations
        SET embedding_1024 = cast(:vector as vector),
            embedding_model_id = :model,
            embedding_state = 'ready',
            embedding_error = NULL
        WHERE id = :id
          AND
        """ + EMBEDDING_MISSING_PREDICATE, nativeQuery = true)
    int applyEmbedding1024IfMissing(@Param("id") UUID id,
                                    @Param("vector") String vector,
                                    @Param("model") String model);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE mem_observations
        SET embedding_1536 = cast(:vector as vector),
            embedding_model_id = :model,
            embedding_state = 'ready',
            embedding_error = NULL
        WHERE id = :id
          AND
        """ + EMBEDDING_MISSING_PREDICATE, nativeQuery = true)
    int applyEmbedding1536IfMissing(@Param("id") UUID id,
                                    @Param("vector") String vector,
                                    @Param("model") String model);

    /**
     * Record a terminal-for-now outcome without touching the observation body.
     *
     * <p>Only {@code embedding_state}/{@code embedding_error} change, and only while the row
     * is still unembedded, so a repeat call cannot resurrect a row another writer completed.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE mem_observations
        SET embedding_state = :state,
            embedding_error = :error
        WHERE id = :id
          AND
        """ + EMBEDDING_MISSING_PREDICATE, nativeQuery = true)
    int markEmbeddingOutcomeIfMissing(@Param("id") UUID id,
                                      @Param("state") String state,
                                      @Param("error") String error);

    /** Count of rows awaiting an embedding retry. Exposed through the queue health indicator. */
    @Query(value = """
        SELECT count(*) FROM mem_observations o
        WHERE
        """ + EMBEDDING_MISSING_PREDICATE, nativeQuery = true)
    long countMissingEmbeddings();

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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
        ORDER BY created_at_epoch DESC
        """, nativeQuery = true)
    List<ObservationEntity> findByProjectPathOrderByCreatedAtEpochDesc(
        @Param("project") String projectPath);

    // Find recent observations for a project with pagination (avoids loading all into memory)
    // Sorted on created_at_epoch for the same reason as above.
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
        ORDER BY created_at_epoch DESC
        """,
        countQuery = """
        SELECT count(*) FROM mem_observations
        WHERE """ + PROJECT_SCOPE_FILTER + """
        """,
        nativeQuery = true)
    Page<ObservationEntity> findByProjectPathOrderByCreatedAtEpochDesc(
        @Param("project") String projectPath,
        Pageable pageable);

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
        SELECT o.*,
               (SELECT scope.scope_type FROM mem_memory_scopes scope WHERE scope.id = o.memory_scope_id)
                   AS "memoryScopeType"
        FROM mem_observations o
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE conversation_id IN (:conversationIds)
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findByConversationIdInOrderByCreatedAtEpochDesc(
        @Param("conversationIds") List<UUID> conversationIds,
        @Param("limit") int limit);

    /**
     * Hybrid search returning {@code [id, fused_score, text_score]} rows.
     *
     * <p>Each half produces a ranked list: the semantic half by cosine (floored at
     * {@code :minSimilarity}), the lexical half by V24 query-token coverage. The lists are
     * fused with weighted reciprocal-rank fusion,
     * {@code 1/(60+vector_rank) + lexicalWeight/(60+lexical_rank)}.
     *
     * <p>Rank fusion instead of score fusion is measured, not stylistic: cosine lives around
     * 0.6-0.8 while coverage of a long prompt is often 0.05-0.2, so an additive score lets
     * cosine decide every position. On the 60-query corpus additive fusion froze hit@5 at the
     * vector number (0.817) while rank fusion at lexicalWeight 2.0 reached 0.883 with MRR
     * 0.809 against vector's 0.817 / 0.696. See
     * {@code docs/archive/2026-11-04_retrieval-quality-real.md}.
     *
     * <p>Both halves are cut at {@code :limit}, so a row only enters the fusion through the
     * best {@code :limit} of at least one strategy.
     *
     * <p>The fused score is multiplied by {@code 0.5^(age_days / :recencyHalfLifeDays)} so the
     * hybrid prefers the recent copy of a memory. Round-16 measurement on 90 real queries:
     * the plain fusion scored hit@1 0.778 / MRR 0.821; a 7-day half-life lifted it to
     * 0.833 / 0.866, and the 30-day variant to 0.811 / 0.849. A near-duplicate merge
     * (newest title wins) was measured and rejected: it hid a relevant row for 76 of the
     * 90 queries. See {@code docs/archive/2026-11-04_retrieval-quality-round16.md}.
     */
    @Query(value = """
        WITH qb AS (
            SELECT DISTINCT unnest(string_to_array(cortex_tokens(:query), ' ')) AS tok
        ),
        qtotal AS (SELECT greatest(count(*), 1) AS n FROM qb),
        text_cov AS (
            SELECT o.id, count(*) AS matched
            FROM qb
            JOIN mem_observations o
              ON o.search_tokens @@ to_tsquery('simple', quote_literal(qb.tok))
            WHERE """ + PROJECT_SCOPE_FILTER + """
            AND o.created_at_epoch > :minEpoch
            GROUP BY o.id
        ),
        semantic_ranked AS (
            SELECT o.id,
                   row_number() OVER (
                       ORDER BY o.embedding_1024 <=> cast(:queryVector as vector)) AS rank
            FROM mem_observations o
            WHERE """ + PROJECT_SCOPE_FILTER + """
            AND o.embedding_1024 IS NOT NULL
            AND o.created_at_epoch > :minEpoch
            AND (1.0 - (o.embedding_1024 <=> cast(:queryVector as vector))) >= :minSimilarity
            ORDER BY o.embedding_1024 <=> cast(:queryVector as vector)
            LIMIT :limit
        ),
        lexical_ranked AS (
            SELECT id, row_number() OVER (ORDER BY matched DESC, id) AS rank
            FROM text_cov
            LIMIT :limit
        ),
        fused AS (
            SELECT id, SUM(score) AS score
            FROM (
                SELECT id, 1.0 / (60 + rank) AS score FROM semantic_ranked
                UNION ALL
                SELECT id, cast(:lexicalWeight as double precision) / (60 + rank) AS score
                FROM lexical_ranked
            ) parts
            GROUP BY id
        )
        SELECT f.id,
               f.score * power(0.5::double precision,
                   greatest(extract(epoch from now()) * 1000 - o.created_at_epoch, 0)::double precision
                   / 86400000.0
                   / cast(:recencyHalfLifeDays as double precision)) AS fused_score,
               coalesce(t.matched, 0)::double precision / (SELECT n FROM qtotal) AS text_score
        FROM fused f
        JOIN mem_observations o ON o.id = f.id
        LEFT JOIN text_cov t ON t.id = f.id
        ORDER BY fused_score DESC, f.id
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> hybridSearchScored(
        @Param("project") String project,
        @Param("query") String query,
        @Param("queryVector") String queryVector,
        @Param("minEpoch") long minEpoch,
        @Param("minSimilarity") double minSimilarity,
        @Param("lexicalWeight") double lexicalWeight,
        @Param("recencyHalfLifeDays") double recencyHalfLifeDays,
        @Param("limit") int limit
    );

    // P2: Timeline aggregation by date (group observations by date)
    @Query(value = """
        SELECT
            DATE(to_timestamp(created_at_epoch / 1000)) as obs_date,
            COUNT(*) as count,
            ARRAY_AGG(id ORDER BY created_at_epoch DESC) as ids
        FROM mem_observations
        WHERE """ + PROJECT_SCOPE_FILTER + """
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
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

    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + MULTI_PROJECT_SCOPE_FILTER + """
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
        SELECT o.*,
               (SELECT scope.scope_type FROM mem_memory_scopes scope WHERE scope.id = o.memory_scope_id)
                   AS "memoryScopeType"
        FROM mem_observations o
        WHERE o.memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        AND o.type IN (:types)
        AND (:conceptsEmpty = true OR EXISTS (
            SELECT 1 FROM jsonb_array_elements_text(o.concepts::jsonb) elem
            WHERE elem IN (:concepts)
        ))
        AND o.conversation_id IN (
            SELECT conversation_id FROM mem_observations
            WHERE conversation_id IS NOT NULL
              AND memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
            GROUP BY conversation_id
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
        SELECT o.*,
               (SELECT scope.scope_type FROM mem_memory_scopes scope WHERE scope.id = o.memory_scope_id)
                   AS "memoryScopeType"
        FROM mem_observations o
        WHERE o.memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
        AND o.type IN (:types)
        AND COALESCE(o.platform_source, 'claude') = :platformSource
        AND (:conceptsEmpty = true OR EXISTS (
            SELECT 1 FROM jsonb_array_elements_text(o.concepts::jsonb) elem
            WHERE elem IN (:concepts)
        ))
        AND o.conversation_id IN (
            SELECT conversation_id FROM mem_observations
            WHERE conversation_id IS NOT NULL
              AND memory_scope_id IN (SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'project' AND scope_key = cast(:project AS text) COLLATE "C")
              AND COALESCE(platform_source, 'claude') = :platformSource
            GROUP BY conversation_id
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

    /**
     * Most recent observations in one scope. Used by the deterministic validation layer for the
     * duplicate-title check; the caller compares normalized titles in Java so no database
     * expression index is required.
     */
    @Query("""
        SELECT o FROM ObservationEntity o
        WHERE o.memoryScopeId = :memoryScopeId
        ORDER BY o.createdAtEpoch DESC, o.id DESC
        """)
    List<ObservationEntity> findRecentByMemoryScopeId(
        @Param("memoryScopeId") UUID memoryScopeId,
        org.springframework.data.domain.Pageable pageable
    );

    Optional<ObservationEntity> findByMemoryScopeIdAndStandaloneKindAndPlatformSourceAndContentSessionIdAndTitleAndCreatedAtEpoch(
        UUID memoryScopeId,
        String standaloneKind,
        String platformSource,
        String contentSessionId,
        String title,
        long createdAtEpoch
    );

    List<ObservationEntity> findByMemoryScopeIdInAndStandaloneKindIn(
        List<UUID> memoryScopeIds,
        List<String> standaloneKinds
    );

    // ==========================================================================
    // Observation Deduplication (Migration 22 alignment)
    // ==========================================================================

    /**
     * Find duplicate observation by content_hash within a time window, scoped to one
     * conversation. Aligns with TypeScript version's DEDUP_WINDOW_MS = 30000 (30 seconds).
     *
     * <p>The conversation scope is required, not an optional refinement: two sessions
     * running against the same project write into the same memory scope, so filtering on
     * the scope alone would let one session's observation suppress an identical
     * observation from another session and hand the caller a row it does not own. The
     * scope filter stays as well, so identical content in two different projects can
     * never suppress each other.
     *
     * @param contentHash SHA-256 hash of observation content (16 chars)
     * @param windowStart Epoch timestamp for the start of the dedup window
     * @param memoryScopeId Resolved scope id scoping the search — required
     * @param conversationId Conversation that owns the candidate duplicate
     * @return Existing observation if found within the window, null otherwise
     */
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE content_hash = :contentHash
        AND created_at_epoch > :windowStart
        AND memory_scope_id = :memoryScopeId
        AND conversation_id = :conversationId
        ORDER BY created_at_epoch DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<ObservationEntity> findDuplicateByContentHash(
        @Param("contentHash") String contentHash,
        @Param("windowStart") long windowStart,
        @Param("memoryScopeId") UUID memoryScopeId,
        @Param("conversationId") UUID conversationId
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
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
        WHERE """ + PROJECT_SCOPE_FILTER + """
        """, nativeQuery = true)
    Object[] getQualityDistribution(@Param("project") String project);

    /**
     * Find all distinct project paths that have observations.
     * Used by scheduled refinement fallback.
     */
    @Query(value = """
        SELECT DISTINCT CASE WHEN ms.scope_type = 'manual_default'
                             THEN 'manual-memories' ELSE ms.scope_key END AS project_path
        FROM mem_observations o
        JOIN mem_memory_scopes ms ON ms.id = o.memory_scope_id
        WHERE ms.scope_type IN ('project', 'manual_default')
        ORDER BY project_path
        """, nativeQuery = true)
    List<String> findDistinctProjects();

    // ===== Phase 3: Structured Extraction Methods =====

    /**
     * Find observations where source is IN a list of values.
     * Required for extraction source filtering (findBySource takes single String).
     */
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
        AND source IN (:sources)
        AND (:includeStandaloneManual = true OR standalone_kind IS DISTINCT FROM 'manual')
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationEntity> findBySourceIn(
        @Param("project") String project,
        @Param("sources") List<String> sources,
        @Param("includeStandaloneManual") boolean includeStandaloneManual,
        @Param("limit") int limit);

    /**
     * Find new observations since a given epoch for incremental extraction.
     */
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
        WHERE """ + PROJECT_SCOPE_FILTER + """
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
    @Query(value = OBSERVATION_ENTITY_SELECT + """
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
