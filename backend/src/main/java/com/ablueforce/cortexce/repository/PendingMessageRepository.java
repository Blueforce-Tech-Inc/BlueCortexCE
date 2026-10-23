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

    /** Summary jobs that need initial processing or crash recovery. */
    @Query("""
        SELECT p FROM PendingMessageEntity p
        WHERE p.messageType = 'summarize'
        AND (
            p.status IN ('pending', 'result_persisted')
            OR (p.status = 'processing'
                AND (p.startedProcessingAtEpoch IS NULL OR p.startedProcessingAtEpoch <= :staleBeforeEpoch))
        )
        ORDER BY p.createdAtEpoch ASC, p.id ASC
        """)
    List<PendingMessageEntity> findRecoverableSummaryJobs(
        @Param("staleBeforeEpoch") long staleBeforeEpoch);

    List<PendingMessageEntity> findBySessionDbIdAndStatusOrderByCreatedAtEpochAsc(UUID sessionDbId, String status);

    @Query("SELECT COUNT(p) FROM PendingMessageEntity p WHERE p.sessionDbId = :sessionDbId "
        + "AND p.messageType = 'observation' AND p.status IN ('pending', 'processing')")
    long countOpenObservationJobs(@Param("sessionDbId") UUID sessionDbId);

    /** Delete the queue rows owned by one conversation (used when the conversation goes). */
    @Modifying
    @Query("DELETE FROM PendingMessageEntity p WHERE p.sessionDbId = :sessionDbId")
    int deleteBySessionDbId(@Param("sessionDbId") UUID sessionDbId);

    long countByStatus(String status);

    // ========================================================================
    // V20 durable summary job queries
    // ========================================================================

    /** The canonical durable job for a conversation, if it exists. */
    @Query("""
        SELECT p FROM PendingMessageEntity p
        WHERE p.sessionDbId = :sessionDbId
        AND p.messageType = 'summarize'
        AND p.jobKey = :jobKey
        """)
    Optional<PendingMessageEntity> findSummaryJob(
        @Param("sessionDbId") UUID sessionDbId,
        @Param("jobKey") String jobKey);

    /**
     * Legacy {@code summarize} rows that carry no job key. Used only by the audited
     * promotion path; a row set larger than one is ambiguity, never a choice.
     */
    @Query("""
        SELECT p FROM PendingMessageEntity p
        WHERE p.sessionDbId = :sessionDbId
        AND p.messageType = 'summarize'
        AND p.jobKey IS NULL
        ORDER BY p.createdAtEpoch ASC, p.id ASC
        """)
    List<PendingMessageEntity> findUnkeyedSummarizeJobs(@Param("sessionDbId") UUID sessionDbId);

    /**
     * Does this conversation hold a summary job that makes deletion unsafe?
     *
     * <p>Deliberately keyed on {@code session_db_id}: the guard must apply to exactly one
     * conversation, so two platforms sharing an external id cannot block each other.
     */
    @Query("""
        SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
        FROM PendingMessageEntity p
        WHERE p.sessionDbId = :sessionDbId
        AND p.messageType = 'summarize'
        AND p.status IN :statuses
        """)
    boolean existsInFlightSummaryJob(
        @Param("sessionDbId") UUID sessionDbId,
        @Param("statuses") List<String> statuses);

    /** The job that owns a given result, for tombstoning on result deletion. */
    @Query("""
        SELECT p FROM PendingMessageEntity p
        WHERE p.resultSummaryId = :summaryId
        """)
    Optional<PendingMessageEntity> findByResultSummaryId(@Param("summaryId") UUID summaryId);

    /**
     * Correct dedup query: scoped to one Conversation by internal id.
     *
     * <p>Failed rows are excluded so a retry after failure is still allowed, matching the
     * existing "failed may be retried, everything else dedups" rule that an unconditional
     * unique constraint would have broken.
     */
    @Query("SELECT COUNT(p) FROM PendingMessageEntity p WHERE p.sessionDbId = :sessionDbId AND p.toolName = :toolName AND p.toolInputHash = :toolInputHash AND p.status <> 'failed'")
    long countBySessionDbIdAndTool(
        @Param("sessionDbId") UUID sessionDbId,
        @Param("toolName") String toolName,
        @Param("toolInputHash") String toolInputHash
    );

    /** Any pending row (any type) for a conversation, for the deletion guard. */
    @Query("SELECT COUNT(p) FROM PendingMessageEntity p WHERE p.sessionDbId = :sessionDbId AND p.status IN :statuses")
    long countPendingForSession(
        @Param("sessionDbId") UUID sessionDbId,
        @Param("statuses") List<String> statuses);

    // Find most recently completed message for health check
    @Query(value = "SELECT MAX(p.completedAtEpoch) FROM PendingMessageEntity p WHERE p.status = 'processed'")
    Long findLastProcessedEpoch();

    // Count stale messages
    @Query("SELECT COUNT(p) FROM PendingMessageEntity p WHERE p.messageType = 'observation' "
        + "AND p.status = 'processing' AND (p.startedProcessingAtEpoch IS NULL OR p.startedProcessingAtEpoch < :threshold)")
    long countStale(@Param("threshold") long thresholdEpoch);

    @Modifying
    @Query("UPDATE PendingMessageEntity p SET p.status = :newStatus, p.startedProcessingAtEpoch = NULL "
        + "WHERE p.messageType = 'observation' AND p.status = :oldStatus "
        + "AND (p.startedProcessingAtEpoch IS NULL OR p.startedProcessingAtEpoch < :beforeEpoch)")
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
