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
        ORDER BY s.createdAtEpoch DESC
        """)
    Page<SummaryEntity> findAllPaged(
        @Param("project") String project,
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

    List<SummaryEntity> findByProjectPathOrderByCreatedAtDesc(String projectPath);

    /**
     * Find summaries by memory_session_id for duplicate checking.
     * Used by Import API to prevent duplicate imports.
     */
    @Query("SELECT s FROM SummaryEntity s WHERE s.memorySessionId = :memorySessionId")
    List<SummaryEntity> findByMemorySessionId(@Param("memorySessionId") String memorySessionId);
}
