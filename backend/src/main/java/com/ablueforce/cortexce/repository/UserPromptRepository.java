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
        AND (:platformSource IS NULL OR p.platformSource = :platformSource)
        ORDER BY p.createdAtEpoch DESC
        """)
    Page<UserPromptEntity> findAllPaged(@Param("project") String project, @Param("platformSource") String platformSource, Pageable pageable);

    Optional<UserPromptEntity> findByContentSessionIdAndPromptNumber(
        String contentSessionId, Integer promptNumber
    );

    List<UserPromptEntity> findByContentSessionIdOrderByPromptNumberAsc(String contentSessionId);

    long countByContentSessionId(String contentSessionId);

    /**
     * Batch fetch user prompts by session IDs (for bulk import duplicate detection).
     * Use to check for existing prompts in bulk before calling saveAll().
     */
    List<UserPromptEntity> findByContentSessionIdIn(List<String> sessionIds);
}
