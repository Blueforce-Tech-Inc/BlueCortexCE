package com.ablueforce.cortexce.repository;

import com.ablueforce.cortexce.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    Optional<SessionEntity> findByContentSessionId(String contentSessionId);

    @Query("SELECT DISTINCT s.projectPath FROM SessionEntity s ORDER BY s.projectPath")
    List<String> findAllProjects();

    List<SessionEntity> findByProjectPathOrderByStartedAtEpochDesc(String projectPath);

    @Query("SELECT s FROM SessionEntity s WHERE s.status = :status ORDER BY s.startedAtEpoch DESC")
    List<SessionEntity> findByStatus(@Param("status") String status);

    // P2-1: Context caching queries
    List<SessionEntity> findByProjectPathAndStatus(String projectPath, String status);

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

    // Export support: batch query sessions by content session IDs
    List<SessionEntity> findByContentSessionIdIn(List<String> contentSessionIds);
}
