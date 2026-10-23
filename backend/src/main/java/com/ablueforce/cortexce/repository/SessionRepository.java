package com.ablueforce.cortexce.repository;

import com.ablueforce.cortexce.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    /**
     * Unambiguous external identity lookup: {@code (platform_source, content_session_id)}.
     *
     * <p>{@code platformSource} is compared after the same normalisation the writer applied
     * (NULL/blank legacy rows count as {@code claude}), so a legacy row is still found.
     */
    @Query(value = """
        SELECT * FROM mem_sessions s
        WHERE s.platform_source COLLATE "C" = CAST(:platformSource AS text) COLLATE "C"
          AND s.content_session_id COLLATE "C" = CAST(:contentSessionId AS text) COLLATE "C"
        """, nativeQuery = true)
    Optional<SessionEntity> findByPlatformSourceAndContentSessionId(
        @Param("platformSource") String platformSource,
        @Param("contentSessionId") String contentSessionId);

    /** Insert a Conversation only if its exact composite external identity is absent. */
    @Modifying
    @Query(value = """
        INSERT INTO mem_sessions (
            content_session_id, project_path, user_prompt, started_at, started_at_epoch,
            completed_at, completed_at_epoch, status, platform_source
        ) VALUES (
            :contentSessionId, :projectPath, :userPrompt, :startedAt, :startedAtEpoch,
            :completedAt, :completedAtEpoch, :status, :platformSource
        )
        ON CONFLICT (platform_source COLLATE "C", content_session_id COLLATE "C") DO NOTHING
        """, nativeQuery = true)
    int insertIfIdentityAbsent(
        @Param("contentSessionId") String contentSessionId,
        @Param("projectPath") String projectPath,
        @Param("userPrompt") String userPrompt,
        @Param("startedAt") OffsetDateTime startedAt,
        @Param("startedAtEpoch") long startedAtEpoch,
        @Param("completedAt") OffsetDateTime completedAt,
        @Param("completedAtEpoch") Long completedAtEpoch,
        @Param("status") String status,
        @Param("platformSource") String platformSource
    );

    @Query(value = """
        SELECT * FROM mem_sessions s
        WHERE s.content_session_id COLLATE "C" = CAST(:contentSessionId AS text) COLLATE "C"
          AND COALESCE(s.platform_source, 'claude') COLLATE "C" = CAST(:platformSource AS text) COLLATE "C"
        FOR UPDATE
        """, nativeQuery = true)
    Optional<SessionEntity> findForViewerDeletion(
        @Param("contentSessionId") String contentSessionId,
        @Param("platformSource") String platformSource
    );

    /**
     * Take a pessimistic row lock on one Conversation, the shared anchor for every
     * conversation-lifecycle mutation (SessionEnd, job state changes, deletion).
     *
     * <p>Uses the internal UUID, never the external id: two platforms may legitimately share
     * an external id, and locking on that would make them contend with — or worse, serialise
     * against — each other.
     *
     * @return the id if the row exists and is now locked; empty if it does not exist
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s.id FROM SessionEntity s WHERE s.id = :id")
    Optional<UUID> lockSessionForUpdate(@Param("id") UUID id);

    @Query("SELECT DISTINCT s.projectPath FROM SessionEntity s ORDER BY s.projectPath")
    List<String> findAllProjects();

    List<SessionEntity> findByProjectPathOrderByStartedAtEpochDesc(String projectPath);

    @Query("SELECT s FROM SessionEntity s WHERE s.status = :status ORDER BY s.startedAtEpoch DESC")
    List<SessionEntity> findByStatus(@Param("status") String status);

    // P2-1: Context caching queries
    //
    // Ordered deliberately: both the context-cache read (ContextCacheService.getContextIfFresh)
    // and its write (SessionController.cacheContextForProject) take element 0 of this result
    // to decide which session owns the project-level cached context. Without an ORDER BY,
    // SQL gives no row-order guarantee and an UPDATE elsewhere in mem_sessions can change
    // which row comes first, so the two sides could land on different sessions. Newest-first
    // matches findByProjectPathOrderByStartedAtEpochDesc above.
    @Query("SELECT s FROM SessionEntity s WHERE s.projectPath = :projectPath AND s.status = :status ORDER BY s.startedAtEpoch DESC")
    List<SessionEntity> findByProjectPathAndStatus(@Param("projectPath") String projectPath,
                                                  @Param("status") String status);

    @Query("SELECT s FROM SessionEntity s WHERE s.needsContextRefresh = true AND s.status = 'active'")
    List<SessionEntity> findByNeedsContextRefreshTrue();

    @Query(value = """
        SELECT COUNT(DISTINCT project_path) FROM mem_sessions
        """, nativeQuery = true)
    long countDistinctProjects();

    // P0: Prior Messages - get last completed session's assistant message
    @Query("SELECT s FROM SessionEntity s WHERE s.projectPath = :projectPath AND s.status = 'completed' " +
           "AND s.completedAtEpoch IS NOT NULL AND s.lastAssistantMessage IS NOT NULL " +
           "AND s.lastAssistantMessage != '' " +
           "ORDER BY s.completedAtEpoch DESC")
    List<SessionEntity> findLastCompletedSessionWithMessage(@Param("projectPath") String projectPath);

    // Stats support: count sessions for a specific project
    long countByProjectPath(String projectPath);

    // Phase 3: Multi-user support — find sessions by userId
    List<SessionEntity> findByUserId(String userId);

    /** Conversation UUIDs for a user within a project (ExpRag user-scoped branch). */
    @Query("SELECT s.id FROM SessionEntity s WHERE s.userId = :userId AND s.projectPath = :project")
    List<UUID> findConversationIdsByUserIdAndProject(@Param("userId") String userId, @Param("project") String project);

    // V18: Platform source support
    @Query("SELECT DISTINCT COALESCE(s.platformSource, 'claude') FROM SessionEntity s ORDER BY COALESCE(s.platformSource, 'claude')")
    List<String> findAllPlatformSources();

    @Query("""
        SELECT COALESCE(s.platformSource, 'claude'), s.projectPath
        FROM SessionEntity s
        GROUP BY COALESCE(s.platformSource, 'claude'), s.projectPath
        ORDER BY COALESCE(s.platformSource, 'claude'), s.projectPath
        """)
    List<Object[]> findProjectsByPlatformSource();
}
