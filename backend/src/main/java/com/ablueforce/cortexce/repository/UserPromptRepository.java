package com.ablueforce.cortexce.repository;

import com.ablueforce.cortexce.entity.UserPromptEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
        WHERE (:project IS NULL OR p.projectPath = :project)
        ORDER BY p.createdAtEpoch DESC
        """)
    Page<UserPromptEntity> findAllPaged(@Param("project") String project, Pageable pageable);

    Optional<UserPromptEntity> findByContentSessionIdAndPromptNumber(
        String contentSessionId, Integer promptNumber
    );

    /**
     * Find user prompt by content_session_id and prompt number for duplicate checking.
     * Used by Import API to prevent duplicate imports.
     */
    @Query("SELECT p FROM UserPromptEntity p WHERE p.contentSessionId = :contentSessionId AND p.promptNumber = :promptNumber")
    Optional<UserPromptEntity> findByContentSessionIdAndPromptNumberQuery(
        @Param("contentSessionId") String contentSessionId,
        @Param("promptNumber") Integer promptNumber
    );

    List<UserPromptEntity> findByContentSessionIdOrderByPromptNumberAsc(String contentSessionId);

    long countByContentSessionId(String contentSessionId);
}
