package com.ablueforce.cortexce.repository;

import com.ablueforce.cortexce.entity.PendingMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingMessageRepository extends JpaRepository<PendingMessageEntity, UUID> {

    List<PendingMessageEntity> findByStatusOrderByCreatedAtEpochAsc(String status);

    List<PendingMessageEntity> findBySessionDbIdAndStatusOrderByCreatedAtEpochAsc(UUID sessionDbId, String status);

    long countByStatus(String status);

    // Dedup: check if a message with same session + tool already exists (not failed)
    // P1: Using full SHA-256 hash string for collision-resistant deduplication
    @Query("SELECT COUNT(p) FROM PendingMessageEntity p WHERE p.contentSessionId = :sessionId AND p.toolName = :toolName AND p.toolInputHash = :toolInputHash AND p.status <> 'failed'")
    long countBySessionAndTool(
        @Param("sessionId") String sessionId,
        @Param("toolName") String toolName,
        @Param("toolInputHash") String toolInputHash
    );

    /**
     * P1: Check if duplicate exists (for deduplication).
     * Uses full SHA-256 hash string for collision-resistant deduplication.
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PendingMessageEntity p " +
           "WHERE p.contentSessionId = :sessionId AND p.toolName = :toolName " +
           "AND p.toolInputHash = :toolInputHash AND p.status <> 'failed'")
    boolean existsBySessionAndTool(
        @Param("sessionId") String sessionId,
        @Param("toolName") String toolName,
        @Param("toolInputHash") String toolInputHash
    );

    // Find most recently completed message for health check
    @Query(value = "SELECT MAX(p.completedAtEpoch) FROM PendingMessageEntity p WHERE p.status = 'processed'")
    Long findLastProcessedEpoch();

    // Count stale messages
    @Query("SELECT COUNT(p) FROM PendingMessageEntity p WHERE p.status = 'processing' AND p.createdAtEpoch < :threshold")
    long countStale(@Param("threshold") long thresholdEpoch);

    @Modifying
    @Query("UPDATE PendingMessageEntity p SET p.status = :newStatus WHERE p.status = :oldStatus AND p.createdAtEpoch < :beforeEpoch")
    int updateStaleMessages(
        @Param("oldStatus") String oldStatus,
        @Param("newStatus") String newStatus,
        @Param("beforeEpoch") long beforeEpoch
    );

    /**
     * P1: Mark message as failed with retry logic.
     * If retry_count < maxRetries, moves back to 'pending' for retry.
     * Otherwise marks as 'failed' permanently.
     *
     * BUG FIX: Must check retry_count + 1 < maxRetries because retry_count
     * is incremented in the same statement. Original bug: used retry_count < maxRetries
     * which resulted in maxRetries + 1 actual attempts.
     */
    @Modifying
    @Query(value = """
        UPDATE mem_pending_messages
        SET status = CASE
            WHEN retry_count + 1 < :maxRetries THEN 'pending'
            ELSE 'failed'
            END,
            retry_count = retry_count + 1,
            started_processing_at_epoch = NULL,
            failed_at_epoch = CASE
                WHEN retry_count + 1 >= :maxRetries THEN :now
                ELSE NULL
                END
        WHERE id = :id AND status IN ('pending', 'processing')
        """, nativeQuery = true)
    int markFailedWithRetry(
        @Param("id") UUID id,
        @Param("maxRetries") int maxRetries,
        @Param("now") long now
    );

    /**
     * Get retry count for a message
     */
    @Query("SELECT p.retryCount FROM PendingMessageEntity p WHERE p.id = :id")
    Optional<Integer> findRetryCountById(@Param("id") UUID id);
}
