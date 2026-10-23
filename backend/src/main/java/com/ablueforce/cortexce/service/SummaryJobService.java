package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the durable SessionEnd summary job carried in {@code mem_pending_messages}.
 *
 * <p>Why a durable row instead of the in-memory {@code summarizingSessions} set: the set
 * disappears on restart. A restarted process would then consider a conversation safe to
 * delete while a summary was mid-flight, or would regenerate a summary it had already
 * written. The row, its result pointer and its tombstone are the only truths that survive.
 *
 * <p>State machine (plan §4.2):
 *
 * <pre>
 *   pending -> processing -> result_persisted -> processed
 *                                   |
 *                                   +--(result deleted)--> processed + tombstone
 *   any -> skipped   (nothing to summarise; terminal, releases delete protection)
 *   any -> failed    (retries exhausted; still delete-protected until terminal)
 * </pre>
 *
 * <p>{@code result_persisted} is deliberately <b>not</b> terminal: the Summary row exists but
 * finalisation (quality scoring, parent status) has not finished, so the conversation must
 * still refuse deletion.
 *
 * <p><b>Parent-row locking.</b> Every method that mutates a job takes the
 * {@code mem_sessions} row lock first ({@code SELECT ... FOR UPDATE}) so that SessionEnd,
 * the worker, and deletion serialise on one anchor. Callers must not hold that lock across
 * an LLM call; they commit {@code processing} first, call out, then re-lock to finish.
 */
@Service
public class SummaryJobService {

    private static final Logger log = LoggerFactory.getLogger(SummaryJobService.class);

    /** Fixed idempotency key of the canonical SessionEnd job. */
    public static final String JOB_KEY_SESSION_SUMMARY = "session-summary-v1";

    public static final String TYPE_SUMMARIZE = "summarize";

    /** Statuses that make a conversation unsafe to delete. */
    public static final List<String> IN_FLIGHT_STATUSES =
        List.of("pending", "processing", "result_persisted");

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_PROCESSED = "processed";
    public static final String STATUS_SKIPPED = "skipped";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_RESULT_PERSISTED = "result_persisted";

    private final PendingMessageRepository pendingMessageRepository;
    private final SessionRepository sessionRepository;

    public SummaryJobService(PendingMessageRepository pendingMessageRepository,
                             SessionRepository sessionRepository) {
        this.pendingMessageRepository = pendingMessageRepository;
        this.sessionRepository = sessionRepository;
    }

    /** Raised when the conversation row vanished between lock acquisition and write. */
    public static final class ConversationGoneException extends IllegalStateException {
        public ConversationGoneException(UUID id) {
            super("conversation no longer exists: " + id);
        }
    }

    /** Raised when a job is not in the state the caller expects. */
    public static final class IllegalJobStateException extends IllegalStateException {
        public IllegalJobStateException(String message) {
            super(message);
        }
    }

    /**
     * Take the Conversation row lock. Returns {@code false} when the row is gone, which is a
     * normal race outcome for deletion, not an error.
     */
    public boolean lockConversation(UUID conversationId) {
        Optional<UUID> locked = sessionRepository.lockSessionForUpdate(conversationId);
        return locked.isPresent();
    }

    /**
     * Close admission for a Conversation and enqueue its one canonical SessionEnd job.
     * The parent update and job creation share the same row lock and transaction, so a
     * deletion cannot pass its safety check between the two writes.
     */
    @Transactional
    public PendingMessageEntity enqueueSessionSummaryJob(UUID conversationId,
                                                         String lastAssistantMessage) {
        SessionEntity session = lockAndLoadConversation(conversationId);
        Optional<PendingMessageEntity> existing = pendingMessageRepository
            .findSummaryJob(conversationId, JOB_KEY_SESSION_SUMMARY);

        if (existing.isEmpty()) {
            List<PendingMessageEntity> legacy = pendingMessageRepository
                .findUnkeyedSummarizeJobs(conversationId);
            if (legacy.size() > 1) {
                throw new IllegalJobStateException(
                    "multiple unkeyed summarize jobs exist for conversation " + conversationId
                        + "; refusing to choose one");
            }
            if (legacy.size() == 1) {
                PendingMessageEntity legacyJob = legacy.get(0);
                if (!isUntouchedLegacyJob(legacyJob)) {
                    throw new IllegalJobStateException(
                        "legacy summarize job " + legacyJob.getId()
                            + " has processing history; refusing to promote");
                }
                legacyJob.setJobKey(JOB_KEY_SESSION_SUMMARY);
                existing = Optional.of(pendingMessageRepository.save(legacyJob));
            }
        }

        Instant now = Instant.now();
        if (existing.isPresent() && isTerminal(existing.get().getStatus())) {
            if (!STATUS_COMPLETED.equals(session.getStatus())) {
                session.setStatus(STATUS_COMPLETED);
            }
            if (session.getCompletedAtEpoch() == null) {
                session.setCompletedAtEpoch(now.toEpochMilli());
                session.setCompletedAt(OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC));
            }
            if (lastAssistantMessage != null && !lastAssistantMessage.isEmpty()
                && session.getLastAssistantMessage() == null) {
                session.setLastAssistantMessage(lastAssistantMessage);
            }
            sessionRepository.save(session);
            return existing.get();
        }
        if (!STATUS_COMPLETED.equals(session.getStatus())) {
            session.setStatus("summarizing");
        }
        if (session.getCompletedAtEpoch() == null) {
            session.setCompletedAtEpoch(now.toEpochMilli());
            session.setCompletedAt(OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC));
        }
        if (lastAssistantMessage != null && !lastAssistantMessage.isEmpty()
            && session.getLastAssistantMessage() == null) {
            session.setLastAssistantMessage(lastAssistantMessage);
        }
        sessionRepository.save(session);

        if (existing.isPresent()) {
            return existing.get();
        }

        PendingMessageEntity job = new PendingMessageEntity();
        job.setSessionDbId(conversationId);
        job.setContentSessionId(session.getContentSessionId());
        job.setMessageType(TYPE_SUMMARIZE);
        job.setJobKey(JOB_KEY_SESSION_SUMMARY);
        job.setStatus(STATUS_PENDING);
        job.setRetryCount(0);
        job.setCreatedAtEpoch(now.toEpochMilli());
        PendingMessageEntity saved = pendingMessageRepository.save(job);
        log.info("Created durable summary job {} for conversation {}", saved.getId(), conversationId);
        return saved;
    }

    /**
     * Create the canonical SessionEnd job if it does not exist, else return the existing one.
     * Must be called while holding the conversation lock (see {@link #lockConversation}).
     *
     * @return the job row; {@code null} never (throws if the parent is gone)
     */
    @Transactional
    public PendingMessageEntity ensureSessionSummaryJob(UUID conversationId, String contentSessionId) {
        SessionEntity session = lockAndLoadConversation(conversationId);
        Optional<PendingMessageEntity> existing = pendingMessageRepository
            .findSummaryJob(conversationId, JOB_KEY_SESSION_SUMMARY);
        if (existing.isPresent()) {
            return existing.get();
        }
        PendingMessageEntity job = new PendingMessageEntity();
        job.setSessionDbId(conversationId);
        job.setContentSessionId(session.getContentSessionId() != null
            ? session.getContentSessionId() : contentSessionId);
        job.setMessageType(TYPE_SUMMARIZE);
        job.setJobKey(JOB_KEY_SESSION_SUMMARY);
        job.setStatus("pending");
        job.setRetryCount(0);
        job.setCreatedAtEpoch(Instant.now().toEpochMilli());
        PendingMessageEntity saved = pendingMessageRepository.save(job);
        log.info("Created durable summary job {} for conversation {}", saved.getId(), conversationId);
        return saved;
    }

    /**
     * Freeze the immutable input snapshot before the first LLM call.
     *
     * <p>If a snapshot already exists with the same hash this is a no-op replay. A different
     * hash means the inputs changed underneath a committed job: that is a hard failure, not
     * something to paper over by re-sending new input.
     *
     * @return the persisted job
     */
    @Transactional
    public PendingMessageEntity persistSnapshot(UUID jobId, UUID conversationId,
                                                Map<String, Object> snapshot,
                                                String canonicalSnapshotJson) {
        lockAndLoadConversation(conversationId);
        PendingMessageEntity job = requireJob(jobId);
        byte[] hash = sha256(canonicalSnapshotJson);
        if (job.getSummaryInputSha256() != null) {
            if (!MessageDigest.isEqual(job.getSummaryInputSha256(), hash)) {
                throw new IllegalJobStateException(
                    "summary input snapshot hash mismatch for job " + jobId
                        + "; refusing to re-run with different input");
            }
            return job;
        }
        if (!STATUS_PENDING.equals(job.getStatus())
            && !STATUS_PROCESSING.equals(job.getStatus())
            && !STATUS_FAILED.equals(job.getStatus())) {
            throw new IllegalJobStateException(
                "cannot freeze snapshot for job " + jobId + " in status " + job.getStatus());
        }
        job.setSummaryInputSnapshot(snapshot);
        job.setSummaryInputSha256(hash);
        job.setStatus(STATUS_PROCESSING);
        job.setStartedProcessingAtEpoch(Instant.now().toEpochMilli());
        return pendingMessageRepository.save(job);
    }

    /** Backward-compatible hash-only helper for callers outside the SessionEnd worker. */
    @Transactional
    public PendingMessageEntity persistSnapshot(UUID jobId, String canonicalSnapshotJson) {
        PendingMessageEntity job = requireJob(jobId);
        return persistSnapshot(jobId, job.getSessionDbId(), Map.of(), canonicalSnapshotJson);
    }

    /** Persist the snapshot hash alone (no body), for callers that store the body separately. */
    @Transactional
    public PendingMessageEntity persistSnapshotHash(UUID jobId, String canonicalSnapshotJson) {
        PendingMessageEntity job = requireJob(jobId);
        byte[] hash = sha256(canonicalSnapshotJson);
        if (job.getSummaryInputSha256() != null
            && !MessageDigest.isEqual(job.getSummaryInputSha256(), hash)) {
            throw new IllegalJobStateException(
                "summary input snapshot hash mismatch for job " + jobId);
        }
        job.setSummaryInputSha256(hash);
        return pendingMessageRepository.save(job);
    }

    /** Verify a replayed request against the committed snapshot hash. */
    public boolean snapshotMatches(PendingMessageEntity job, String canonicalSnapshotJson) {
        return job.getSummaryInputSha256() != null
            && MessageDigest.isEqual(job.getSummaryInputSha256(), sha256(canonicalSnapshotJson));
    }

    /**
     * Atomically record that a Summary row was produced.
     *
     * <p>The result row, the job's result pointer and the status transition all happen in one
     * transaction. After a crash the worker finds the pointer and finalises; it must never
     * insert a second Summary for the same job.
     */
    @Transactional
    public PendingMessageEntity markResultPersisted(UUID jobId, UUID conversationId, UUID summaryId) {
        lockAndLoadConversation(conversationId);
        PendingMessageEntity job = requireJob(jobId);
        if (job.getResultSummaryId() != null && !job.getResultSummaryId().equals(summaryId)) {
            throw new IllegalJobStateException(
                "job " + jobId + " already points at a different result");
        }
        job.setResultSummaryId(summaryId);
        job.setStatus(STATUS_RESULT_PERSISTED);
        return pendingMessageRepository.save(job);
    }

    /**
     * Finalise a job whose result is committed. Idempotent.
     *
     * @param terminal true to move to {@code processed}; false keeps it delete-protected
     *                 until downstream (quality) work finishes
     */
    @Transactional
    public PendingMessageEntity finalizeResult(UUID jobId, UUID conversationId, boolean terminal) {
        SessionEntity session = lockAndLoadConversation(conversationId);
        PendingMessageEntity job = requireJob(jobId);
        if (STATUS_PROCESSED.equals(job.getStatus())) {
            return job;
        }
        if (job.getResultSummaryId() == null) {
            throw new IllegalJobStateException(
                "cannot finalise job " + jobId + " without a persisted result");
        }
        if (terminal) {
            job.setStatus(STATUS_PROCESSED);
            job.setCompletedAtEpoch(Instant.now().toEpochMilli());
            session.setStatus("completed");
            sessionRepository.save(session);
        } else {
            job.setStatus(STATUS_RESULT_PERSISTED);
        }
        return pendingMessageRepository.save(job);
    }

    /**
     * Terminal "nothing to summarise". Persisted, not just dropped from memory, so delete
     * protection is released by a durable fact.
     */
    @Transactional
    public PendingMessageEntity markSkipped(UUID jobId, UUID conversationId, String reason) {
        SessionEntity session = lockAndLoadConversation(conversationId);
        PendingMessageEntity job = requireJob(jobId);
        if (STATUS_PROCESSED.equals(job.getStatus()) || STATUS_SKIPPED.equals(job.getStatus())) {
            return job;
        }
        job.setStatus(STATUS_SKIPPED);
        job.setCompletedAtEpoch(Instant.now().toEpochMilli());
        session.setStatus("completed");
        sessionRepository.save(session);
        log.info("Summary job {} skipped: {}", jobId, reason);
        return pendingMessageRepository.save(job);
    }

    /** Release a claimed summary job until all previously admitted Observation work drains. */
    @Transactional
    public PendingMessageEntity deferUntilObservationQueueDrains(UUID jobId, UUID conversationId) {
        lockAndLoadConversation(conversationId);
        PendingMessageEntity job = requireJob(jobId);
        if (STATUS_PROCESSED.equals(job.getStatus()) || STATUS_SKIPPED.equals(job.getStatus())
            || STATUS_FAILED.equals(job.getStatus()) || job.getResultSummaryId() != null) {
            return job;
        }
        job.setStatus(STATUS_PENDING);
        job.setStartedProcessingAtEpoch(null);
        return pendingMessageRepository.save(job);
    }

    /**
     * Tombstone a deleted result. The terminal job row stays; only the pointer is cleared.
     *
     * <p>Without this, a replayed SessionEnd would see "no result" and generate the summary
     * again, resurrecting a deletion the user made.
     */
    @Transactional
    public PendingMessageEntity tombstoneResult(UUID jobId, UUID conversationId) {
        SessionEntity session = lockAndLoadConversation(conversationId);
        PendingMessageEntity job = requireJob(jobId);
        if (job.getResultSummaryId() == null) {
            return job;
        }
        job.setResultSummaryId(null);
        job.setResultDeletedAt(java.time.OffsetDateTime.now());
        // A deleted result does not un-finish the job.
        if (!STATUS_PROCESSED.equals(job.getStatus()) && !STATUS_SKIPPED.equals(job.getStatus())) {
            job.setStatus(STATUS_PROCESSED);
            job.setCompletedAtEpoch(Instant.now().toEpochMilli());
        }
        session.setStatus("completed");
        sessionRepository.save(session);
        return pendingMessageRepository.save(job);
    }

    /** Claim one pending job. Duplicate events observe processing and become no-ops. */
    @Transactional
    public Optional<PendingMessageEntity> claimForProcessing(UUID jobId) {
        return claimForProcessing(jobId, Long.MIN_VALUE);
    }

    /**
     * Claim a pending job or reclaim a processing job left behind by a crashed worker.
     * The parent lock serialises duplicate events before the stale decision is made.
     */
    @Transactional
    public Optional<PendingMessageEntity> claimForProcessing(UUID jobId, long staleBeforeEpoch) {
        PendingMessageEntity current = pendingMessageRepository.findById(jobId).orElse(null);
        if (current == null) {
            return Optional.empty();
        }
        lockAndLoadConversation(current.getSessionDbId());
        PendingMessageEntity job = requireJob(jobId);
        boolean pending = STATUS_PENDING.equals(job.getStatus());
        boolean staleProcessing = STATUS_PROCESSING.equals(job.getStatus())
            && (job.getStartedProcessingAtEpoch() == null
                || job.getStartedProcessingAtEpoch() <= staleBeforeEpoch);
        if (!pending && !staleProcessing) {
            return Optional.empty();
        }
        job.setStatus(STATUS_PROCESSING);
        job.setStartedProcessingAtEpoch(Instant.now().toEpochMilli());
        return Optional.of(pendingMessageRepository.save(job));
    }

    /** Read a durable job for recovery without exposing the repository to controllers. */
    @Transactional(readOnly = true)
    public Optional<PendingMessageEntity> findJob(UUID jobId) {
        return pendingMessageRepository.findById(jobId);
    }

    /** Persist a retryable/final failure under the same Conversation lock. */
    @Transactional
    public void markFailed(UUID jobId, UUID conversationId, int maxRetries, String reason) {
        SessionEntity session = lockAndLoadConversation(conversationId);
        PendingMessageEntity job = requireJob(jobId);
        if (STATUS_PROCESSED.equals(job.getStatus()) || STATUS_SKIPPED.equals(job.getStatus())) {
            return;
        }
        int nextRetry = (job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1;
        job.setRetryCount(nextRetry);
        job.setStartedProcessingAtEpoch(null);
        if (nextRetry < Math.max(1, maxRetries)) {
            job.setStatus(STATUS_PENDING);
            job.setFailedAtEpoch(null);
        } else {
            job.setStatus(STATUS_FAILED);
            job.setFailedAtEpoch(Instant.now().toEpochMilli());
            session.setStatus("completed");
            sessionRepository.save(session);
        }
        pendingMessageRepository.save(job);
        log.warn("Summary job {} failed (attempt {}): {}", jobId, nextRetry, reason);
    }

    /**
     * Is this conversation delete-protected by an in-flight summary job?
     *
     * <p>Reads the persisted rows, not the in-memory set, so a restart cannot lose the guard.
     */
    @Transactional(readOnly = true)
    public boolean hasInFlightSummaryJob(UUID conversationId) {
        return pendingMessageRepository.existsInFlightSummaryJob(conversationId, IN_FLIGHT_STATUSES);
    }

    /**
     * Promote exactly one untouched legacy {@code summarize} pending row to the durable key.
     *
     * <p>Only a single row that is {@code pending}, has never been retried and carries no
     * timestamps can be promoted: anything else may already have had side effects. Zero rows
     * means "create a new job"; more than one is ambiguity and must not be guessed.
     */
    @Transactional
    public Optional<PendingMessageEntity> promoteLegacySummarizeJob(UUID conversationId) {
        if (!lockConversation(conversationId)) {
            throw new ConversationGoneException(conversationId);
        }
        List<PendingMessageEntity> legacy = pendingMessageRepository
            .findUnkeyedSummarizeJobs(conversationId);
        if (legacy.isEmpty()) {
            return Optional.empty();
        }
        if (legacy.size() > 1) {
            throw new IllegalJobStateException(
                "multiple unkeyed summarize jobs exist for conversation " + conversationId
                    + "; refusing to choose one");
        }
        PendingMessageEntity job = legacy.get(0);
        boolean untouched = "pending".equals(job.getStatus())
            && (job.getRetryCount() == null || job.getRetryCount() == 0)
            && job.getStartedProcessingAtEpoch() == null
            && job.getCompletedAtEpoch() == null
            && job.getFailedAtEpoch() == null;
        if (!untouched) {
            throw new IllegalJobStateException(
                "legacy summarize job " + job.getId() + " has processing history; refusing to promote");
        }
        job.setJobKey(JOB_KEY_SESSION_SUMMARY);
        return Optional.of(pendingMessageRepository.save(job));
    }

    private PendingMessageEntity requireJob(UUID jobId) {
        return pendingMessageRepository.findById(jobId)
            .orElseThrow(() -> new IllegalJobStateException("summary job not found: " + jobId));
    }

    private SessionEntity lockAndLoadConversation(UUID conversationId) {
        if (!lockConversation(conversationId)) {
            throw new ConversationGoneException(conversationId);
        }
        return sessionRepository.findById(conversationId)
            .orElseThrow(() -> new ConversationGoneException(conversationId));
    }

    private boolean isUntouchedLegacyJob(PendingMessageEntity job) {
        return STATUS_PENDING.equals(job.getStatus())
            && (job.getRetryCount() == null || job.getRetryCount() == 0)
            && job.getStartedProcessingAtEpoch() == null
            && job.getCompletedAtEpoch() == null
            && job.getFailedAtEpoch() == null;
    }

    private boolean isTerminal(String status) {
        return STATUS_PROCESSED.equals(status)
            || STATUS_SKIPPED.equals(status)
            || STATUS_FAILED.equals(status);
    }

    private static final String STATUS_COMPLETED = "completed";

    static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
