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

    /** Everything that is still work: pending rows plus rows currently being processed. */
    @Query("SELECT COUNT(p) FROM PendingMessageEntity p WHERE p.status IN ('pending', 'processing')")
    long countOpenJobs();

    /** Failed rows since an epoch, used for the "is the queue failing right now?" signal. */
    @Query("""
        SELECT COUNT(p) FROM PendingMessageEntity p
        WHERE p.status = 'failed' AND p.failedAtEpoch >= :sinceEpoch
        """)
    long countFailedSince(@Param("sinceEpoch") long sinceEpoch);

    /** Recent failure classifications, newest first (bounded so health checks stay cheap). */
    @Query(value = """
        SELECT failure_kind FROM mem_pending_messages
        WHERE status = 'failed' AND failed_at_epoch >= :sinceEpoch
        ORDER BY failed_at_epoch DESC
        LIMIT 100
        """, nativeQuery = true)
    List<String> findRecentFailureKinds(@Param("sinceEpoch") long sinceEpoch);

    /** Oldest row still waiting, or null when the queue is empty. */
    @Query("SELECT MIN(p.createdAtEpoch) FROM PendingMessageEntity p WHERE p.status = 'pending'")
    Long findOldestPendingEpoch();

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

    // ========================================================================
    // Atomic Observation job state transitions
    //
    // Every transition is a single conditional UPDATE that names the exact state it
    // requires. The Java layer never decides "this row is still mine" from an earlier
    // read: two workers can read the same `pending` row, and only the UPDATE can tell
    // them apart. A zero row count means "somebody else owns it now", never "retry".
    // ========================================================================

    /**
     * Claim one Observation job. Exactly one concurrent caller can win:
     * the row is moved to {@code processing} only while it is still {@code pending}.
     *
     * @return 1 when this caller owns the job, 0 when another worker already did
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE mem_pending_messages
        SET status = 'processing',
            started_processing_at_epoch = :now
        WHERE id = :id
          AND message_type = 'observation'
          AND status = 'pending'
        """, nativeQuery = true)
    int claimPendingObservation(
        @Param("id") UUID id,
        @Param("now") long now
    );

    /**
     * Close a freshly claimed job because its Conversation no longer accepts work.
     * Requires {@code processing}, so it cannot rewrite a completed or unclaimed row.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE mem_pending_messages
        SET status = 'failed',
            failed_at_epoch = :now,
            completed_at_epoch = :now,
            started_processing_at_epoch = NULL
        WHERE id = :id
          AND message_type = 'observation'
          AND status = 'processing'
        """, nativeQuery = true)
    int failClaimedObservation(
        @Param("id") UUID id,
        @Param("now") long now
    );

    /**
     * Record one failed attempt and apply the retry budget.
     *
     * <p>Reads {@code retry_count} only from the row it is currently updating, and
     * requires {@code processing}: a job that another worker already completed or
     * re-queued is never touched, so a late failure cannot resurrect it.
     *
     * <p>{@code retry_count + 1 < maxRetries} is deliberate: the counter is incremented
     * in the same statement, so this gives exactly {@code maxRetries} attempts.
     *
     * @return 1 when the row was still owned by this caller, 0 otherwise
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
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
            END,
            completed_at_epoch = CASE
                WHEN retry_count + 1 >= :maxRetries THEN :now
                ELSE completed_at_epoch
            END,
            failure_kind = CASE
                WHEN retry_count + 1 >= :maxRetries THEN :failureKind
                ELSE failure_kind
            END
        WHERE id = :id
          AND message_type = 'observation'
          AND status = 'processing'
        """, nativeQuery = true)
    int failProcessingObservation(
        @Param("id") UUID id,
        @Param("maxRetries") int maxRetries,
        @Param("now") long now,
        @Param("failureKind") String failureKind
    );

    /**
     * Close a job that this caller still owns ({@code processing}) as
     * {@code processed} or {@code skipped}.
     *
     * @return 1 when the row was still owned by this caller, 0 otherwise
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE mem_pending_messages
        SET status = :status,
            completed_at_epoch = :now,
            started_processing_at_epoch = NULL
        WHERE id = :id
          AND session_db_id = :sessionDbId
          AND message_type = 'observation'
          AND status = 'processing'
        """, nativeQuery = true)
    int completeProcessingObservation(
        @Param("id") UUID id,
        @Param("sessionDbId") UUID sessionDbId,
        @Param("status") String status,
        @Param("now") long now
    );

    /**
     * Refresh the lease on jobs this worker is still processing.
     *
     * <p>Stale recovery reclaims rows whose {@code started_processing_at_epoch} is older
     * than the threshold, and it cannot tell "crashed" from "still working". Without a
     * heartbeat a slow LLM call (up to 3 x 120 s reads plus retries) can outlive the
     * 10-minute threshold, and a second instance would then process the same trace again.
     * The caller only ever passes ids from its own in-flight set, so a heartbeat can
     * never extend another instance's lease, and the {@code processing} guard means it
     * cannot resurrect a row that already finished.
     *
     * @return number of rows still held in {@code processing} by this worker
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE mem_pending_messages
        SET started_processing_at_epoch = :now
        WHERE message_type = 'observation'
          AND status = 'processing'
          AND id IN (:ids)
        """, nativeQuery = true)
    int heartbeatProcessing(
        @Param("ids") java.util.Collection<UUID> ids,
        @Param("now") long now);

    /**
     * Put jobs this instance still owns back into the queue on shutdown.
     *
     * <p>Called only after a bounded wait, with ids taken from this instance's own
     * in-flight set — never a scan of every {@code processing} row, which could
     * requeue another instance's live work. Clearing {@code started_processing_at_epoch}
     * removes the lease so the next instance can claim the row immediately; the
     * {@code processing} guard means a job that finished during the wait is untouched.
     *
     * <p>{@code retry_count} is deliberately left alone: a shutdown is not an
     * attempt, and a crash-looping deployment must not exhaust the retry budget
     * before the provider is ever reachable.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE mem_pending_messages
        SET status = 'pending',
            started_processing_at_epoch = NULL
        WHERE message_type = 'observation'
          AND status = 'processing'
          AND id IN (:ids)
        """, nativeQuery = true)
    int requeueProcessing(@Param("ids") java.util.Collection<UUID> ids);

    /**
     * Get retry count for a message
     */
    @Query("SELECT p.retryCount FROM PendingMessageEntity p WHERE p.id = :id")
    Optional<Integer> findRetryCountById(@Param("id") UUID id);
}
