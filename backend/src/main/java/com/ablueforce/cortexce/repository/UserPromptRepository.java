package com.ablueforce.cortexce.repository;

import com.ablueforce.cortexce.entity.UserPromptEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPromptRepository extends JpaRepository<UserPromptEntity, UUID> {

    @Query("""
        SELECT p FROM UserPromptEntity p
        ORDER BY p.createdAtEpoch DESC
        """)
    Page<UserPromptEntity> findAllPaged(Pageable pageable);

    @Query("""
        SELECT p FROM UserPromptEntity p
        JOIN SessionEntity s ON s.contentSessionId = p.contentSessionId
        WHERE (:project IS NULL OR COALESCE(p.projectPath, s.projectPath) = :project)
        AND (:platformSource IS NULL OR COALESCE(s.platformSource, p.platformSource, 'claude') = :platformSource)
        AND (:contentSessionId IS NULL OR p.contentSessionId = :contentSessionId)
        ORDER BY p.createdAtEpoch DESC, p.id DESC
        """)
    Page<UserPromptEntity> findAllPaged(
        @Param("project") String project,
        @Param("platformSource") String platformSource,
        @Param("contentSessionId") String contentSessionId,
        Pageable pageable
    );

    Optional<UserPromptEntity> findByContentSessionIdAndPromptNumber(
        String contentSessionId, Integer promptNumber
    );

    List<UserPromptEntity> findByContentSessionIdOrderByPromptNumberAsc(String contentSessionId);

    long countByContentSessionId(String contentSessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserPromptEntity p WHERE p.contentSessionId = :contentSessionId")
    int deleteByContentSessionId(@Param("contentSessionId") String contentSessionId);

    @Query("""
        SELECT p FROM UserPromptEntity p
        WHERE p.contentSessionId = :contentSessionId
        ORDER BY p.createdAtEpoch DESC, p.id DESC
        """)
    List<UserPromptEntity> findByContentSessionIdOrderByCreatedAtEpochDesc(@Param("contentSessionId") String contentSessionId);

    /**
     * Batch fetch user prompts by session IDs (for bulk import duplicate detection).
     * Use to check for existing prompts in bulk before calling saveAll().
     */
    List<UserPromptEntity> findByContentSessionIdIn(List<String> sessionIds);
}
