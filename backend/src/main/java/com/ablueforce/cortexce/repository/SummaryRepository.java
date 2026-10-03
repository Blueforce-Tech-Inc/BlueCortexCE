package com.ablueforce.cortexce.repository;

import com.ablueforce.cortexce.entity.SummaryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SummaryRepository extends JpaRepository<SummaryEntity, UUID> {

    @Query("""
        SELECT s FROM SummaryEntity s
        WHERE (:project IS NULL OR s.projectPath = :project)
        AND (:platformSource IS NULL OR s.platformSource = :platformSource)
        ORDER BY s.createdAtEpoch DESC
        """)
    Page<SummaryEntity> findAllPaged(
        @Param("project") String project,
        @Param("platformSource") String platformSource,
        Pageable pageable
    );

    @Query(value = """
        SELECT * FROM mem_summaries
        WHERE project_path = :project
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<SummaryEntity> findByProjectLimited(
        @Param("project") String project,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT * FROM mem_summaries
        WHERE project_path IN (:projects)
        ORDER BY created_at_epoch DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<SummaryEntity> findByProjectsLimited(
        @Param("projects") List<String> projects,
        @Param("limit") int limit
    );

    long countByProjectPath(String projectPath);

    // Sorted on created_at_epoch, not created_at: the timestamp column is
    // nullable and is NULL for every summary written by the normal capture path
    // (only ImportService sets it), so ordering by created_at put the NULL rows
    // last and returned the oldest summaries instead of the newest. The two
    // hand-written queries above this method already order by the epoch column.
    List<SummaryEntity> findByProjectPathOrderByCreatedAtEpochDesc(String projectPath);

    /**
     * Find summaries by content session id for duplicate checking.
     * Used by Import API to prevent duplicate imports.
     *
     * <p>Ordered newest-first so the caller's {@code get(0)} is deterministic.
     * {@code mem_summaries.content_session_id} carries a plain index, not a unique
     * constraint, so a session can legitimately have many summaries — measured on
     * this instance: 212 of 896 distinct session ids had more than one, up to 33
     * rows each. Without ORDER BY, PostgreSQL guarantees no row order and
     * {@code get(0)} can return a different id between calls. The DESC direction
     * also matches the existing {@code idx_summaries_created (created_at_epoch DESC)}
     * index, so the sort is served from the index.
     */
    @Query("SELECT s FROM SummaryEntity s WHERE s.contentSessionId = :contentSessionId ORDER BY s.createdAtEpoch DESC")
    List<SummaryEntity> findByContentSessionId(@Param("contentSessionId") String contentSessionId);
}
