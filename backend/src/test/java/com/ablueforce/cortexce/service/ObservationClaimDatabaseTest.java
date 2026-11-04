package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.event.PendingMessageEventPublisher;
import com.ablueforce.cortexce.identity.ExternalSessionRef;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the ownership rules for Observation queue rows against a real PostgreSQL.
 *
 * <p>Why a database test: the bug this covers is invisible to a mock. The previous
 * implementation read the row, took the Conversation lock, and then read the row
 * again. Under one transaction the second read is served from the first-level cache,
 * so a worker that lost the race still saw {@code pending} and moved the row to
 * {@code processing} a second time. Only concurrent transactions can exercise that.
 *
 * <p>The invariants asserted here:
 * <ol>
 *   <li><b>One winner.</b> N concurrent claims of the same pending row produce exactly
 *       one success and N-1 empty results.</li>
 *   <li><b>No re-claim.</b> A claimed row is invisible to the next worker.</li>
 *   <li><b>No resurrection.</b> A late failure cannot move a completed row back into
 *       the queue.</li>
 *   <li><b>No missed work.</b> A retryable failure returns the row to {@code pending}
 *       exactly once per attempt, and the budget ends in {@code failed}.</li>
 * </ol>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ConversationWriteService.class, SessionManagementService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(
    named = "MEMSCOPE_TEST_DB_URL",
    matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?"
)
class ObservationClaimDatabaseTest {

    private static final int CONFIGURED_MAX_RETRIES = 3;

    @Autowired private ConversationWriteService writer;
    @Autowired private SessionManagementService sessionManagementService;
    @Autowired private PendingMessageRepository pendingMessageRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @MockBean private SSEBroadcaster sseBroadcaster;
    @MockBean private PendingMessageEventPublisher pendingMessageEventPublisher;

    private final List<UUID> conversationIds = new ArrayList<>();
    private final List<UUID> pendingIds = new ArrayList<>();

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> env("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> env("MEMSCOPE_TEST_DB_PASSWORD", "postgres"));
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @BeforeEach
    void requireV20Schema() {
        Integer present = jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.tables
            WHERE table_name = 'mem_pending_messages'
            """, Integer.class);
        assertThat(present).isEqualTo(1);
    }

    @AfterEach
    void cleanup() {
        pendingIds.forEach(id -> jdbc.update(
            "DELETE FROM mem_pending_messages WHERE id = ?", id));
        conversationIds.forEach(id -> jdbc.update(
            "DELETE FROM mem_sessions WHERE id = ?", id));
        pendingIds.clear();
        conversationIds.clear();
    }

    // ------------------------------------------------------------------
    // One winner under real concurrency
    // ------------------------------------------------------------------

    @Test
    @DisplayName("8 concurrent workers claim one pending row: exactly one wins")
    void concurrentClaimsHaveExactlyOneWinner() throws Exception {
        UUID pendingId = enqueueOne();
        int workers = 8;

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Optional<PendingMessageEntity>>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("claim start timed out");
                    }
                    return writer.claimObservationJob(pendingId);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int winners = 0;
            for (Future<Optional<PendingMessageEntity>> future : futures) {
                if (future.get(30, TimeUnit.SECONDS).isPresent()) {
                    winners++;
                }
            }
            assertThat(winners).as("exactly one worker must own the job").isEqualTo(1);
            assertThat(rowStatus(pendingId)).isEqualTo("processing");
            assertThat(jdbc.queryForObject(
                "SELECT started_processing_at_epoch FROM mem_pending_messages WHERE id = ?",
                Long.class, pendingId)).isNotNull();
            assertThat(jdbc.queryForObject(
                "SELECT retry_count FROM mem_pending_messages WHERE id = ?",
                Integer.class, pendingId)).isZero();

            // The losing workers must not have touched the row's attempt counter.
            Integer observationRows = jdbc.queryForObject("""
                SELECT count(*) FROM mem_observations WHERE conversation_id = ?
                """, Integer.class, conversationIdOf(pendingId));
            assertThat(observationRows).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("20 queued jobs claimed by 8 workers while sessions close: no duplicate, no loss")
    void concurrentClaimsAcrossJobsResolveEveryRowExactlyOnce() throws Exception {
        int jobs = 20;
        int workers = 8;
        List<UUID> queued = new ArrayList<>();
        for (int i = 0; i < jobs; i++) {
            queued.add(enqueueOne());
        }
        // SessionEnd closes half the conversations while the claims are running.
        List<UUID> closed = queued.subList(0, jobs / 2).stream()
            .map(this::conversationIdOf)
            .toList();

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<UUID> claimed = java.util.Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                int offset = i;
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    for (int index = offset; index < jobs; index += workers) {
                        writer.claimObservationJob(queued.get(index)).ifPresent(job -> {
                            claimed.add(job.getId());
                            writer.markObservationSkipped(job.getId(), job.getSessionDbId());
                        });
                    }
                    return null;
                }));
            }
            start.countDown();
            for (UUID conversationId : closed) {
                jdbc.update("UPDATE mem_sessions SET status = 'completed' WHERE id = ?",
                    conversationId);
            }
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        String idList = queued.stream()
            .map(id -> "'" + id + "'")
            .collect(java.util.stream.Collectors.joining(","));
        assertThat(claimed).as("each job is won by at most one worker")
            .hasSizeLessThanOrEqualTo(jobs)
            .doesNotHaveDuplicates();
        int pendingLeft = jdbc.queryForObject(
            "SELECT count(*) FROM mem_pending_messages WHERE id IN (" + idList + ")"
                + " AND status = 'pending'", Integer.class);
        assertThat(pendingLeft).as("no job may be lost or left pending").isZero();
        int failed = jdbc.queryForObject(
            "SELECT count(*) FROM mem_pending_messages WHERE id IN (" + idList + ")"
                + " AND status = 'failed'", Integer.class);
        assertThat(failed + claimed.size()).as("every job resolves to skipped or failed")
            .isEqualTo(jobs);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM mem_observations WHERE project_path LIKE '/claim-test/%'
            """, Integer.class)).isZero();
    }

    @Test
    @DisplayName("a claimed row is invisible to the next worker")
    void claimedRowCannotBeClaimedAgain() {
        UUID pendingId = enqueueOne();

        assertThat(writer.claimObservationJob(pendingId)).isPresent();
        assertThat(writer.claimObservationJob(pendingId)).isEmpty();

        assertThat(rowStatus(pendingId)).isEqualTo("processing");
        assertThat(jdbc.queryForObject(
            "SELECT retry_count FROM mem_pending_messages WHERE id = ?",
            Integer.class, pendingId)).isZero();
    }

    @Test
    @DisplayName("a completed row is never resurrected by a late failure")
    void lateFailureDoesNotResurrectCompletedRow() {
        UUID pendingId = enqueueOne();
        UUID conversationId = conversationIdOf(pendingId);

        assertThat(writer.claimObservationJob(pendingId)).isPresent();
        writer.markObservationSkipped(pendingId, conversationId);
        assertThat(rowStatus(pendingId)).isEqualTo("skipped");

        long now = System.currentTimeMillis();
        writer.markObservationFailed(pendingId, CONFIGURED_MAX_RETRIES, now, "TEST");

        assertThat(rowStatus(pendingId)).isEqualTo("skipped");
        assertThat(jdbc.queryForObject(
            "SELECT retry_count FROM mem_pending_messages WHERE id = ?",
            Integer.class, pendingId)).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT failed_at_epoch FROM mem_pending_messages WHERE id = ?",
            Long.class, pendingId)).isNull();
    }

    // ------------------------------------------------------------------
    // Lease heartbeat vs stale recovery
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a heartbeat keeps a running job out of stale recovery and cannot revive a finished row")
    void heartbeatKeepsRunningJobOutOfStaleRecovery() {
        UUID pendingId = enqueueOne();
        assertThat(writer.claimObservationJob(pendingId)).isPresent();

        // A job that has already run for 11 minutes: without a heartbeat the recovery
        // sweep reclaims it, and a second instance then processes the same trace again.
        jdbc.update("UPDATE mem_pending_messages SET started_processing_at_epoch = ? WHERE id = ?",
            System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(11), pendingId);

        long now = System.currentTimeMillis();
        int beaten = transactionTemplate.execute(status ->
            pendingMessageRepository.heartbeatProcessing(List.of(pendingId), now));
        assertThat(beaten).isEqualTo(1);

        int recovered = transactionTemplate.execute(status ->
            pendingMessageRepository.updateStaleMessages(
                "processing", "pending", now - TimeUnit.MINUTES.toMillis(10)));
        assertThat(recovered).as("a lease that keeps beating must never be reclaimed").isZero();
        assertThat(rowStatus(pendingId)).isEqualTo("processing");
        assertThat(jdbc.queryForObject(
            "SELECT started_processing_at_epoch FROM mem_pending_messages WHERE id = ?",
            Long.class, pendingId)).isEqualTo(now);

        // Once the job resolves, the heartbeat must not touch it: a late beat cannot
        // turn a skipped row back into a live lease.
        writer.markObservationSkipped(pendingId, conversationIdOf(pendingId));
        int lateBeat = transactionTemplate.execute(status ->
            pendingMessageRepository.heartbeatProcessing(List.of(pendingId), now + 1));
        assertThat(lateBeat).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT started_processing_at_epoch FROM mem_pending_messages WHERE id = ?",
            Long.class, pendingId)).isNull();
    }

    // ------------------------------------------------------------------
    // Shutdown requeue
    // ------------------------------------------------------------------

    @Test
    @DisplayName("shutdown requeue returns owned rows to pending without spending a retry")
    void shutdownRequeueReturnsOwnedRowsToPending() {
        UUID pendingId = enqueueOne();
        assertThat(writer.claimObservationJob(pendingId)).isPresent();
        jdbc.update("UPDATE mem_pending_messages SET started_processing_at_epoch = ? WHERE id = ?",
            System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1), pendingId);

        int requeued = transactionTemplate.execute(status ->
            pendingMessageRepository.requeueProcessing(List.of(pendingId)));

        assertThat(requeued).isEqualTo(1);
        assertThat(rowStatus(pendingId)).isEqualTo("pending");
        assertThat(jdbc.queryForObject(
            "SELECT started_processing_at_epoch FROM mem_pending_messages WHERE id = ?",
            Long.class, pendingId)).isNull();
        assertThat(jdbc.queryForObject(
            "SELECT retry_count FROM mem_pending_messages WHERE id = ?",
            Integer.class, pendingId)).as("a shutdown is not an attempt").isZero();
        assertThat(jdbc.queryForObject(
            "SELECT failed_at_epoch FROM mem_pending_messages WHERE id = ?",
            Long.class, pendingId)).isNull();

        // The row is claimable again immediately, which is the whole point of the requeue.
        assertThat(writer.claimObservationJob(pendingId)).isPresent();
    }

    @Test
    @DisplayName("shutdown requeue never rewrites a row that already finished")
    void shutdownRequeueLeavesFinishedRowsAlone() {
        UUID pendingId = enqueueOne();
        assertThat(writer.claimObservationJob(pendingId)).isPresent();
        writer.markObservationSkipped(pendingId, conversationIdOf(pendingId));
        assertThat(rowStatus(pendingId)).isEqualTo("skipped");

        int requeued = transactionTemplate.execute(status ->
            pendingMessageRepository.requeueProcessing(List.of(pendingId)));

        assertThat(requeued).isZero();
        assertThat(rowStatus(pendingId)).isEqualTo("skipped");
    }

    // ------------------------------------------------------------------
    // Retry budget: one increment per attempt, terminal at the bound
    // ------------------------------------------------------------------

    @Test
    @DisplayName("retryable failures return the row to pending exactly once per attempt, then fail")
    void retryBudgetIsAppliedOncePerAttempt() {
        UUID pendingId = enqueueOne();
        long now = System.currentTimeMillis();

        for (int attempt = 1; attempt < CONFIGURED_MAX_RETRIES; attempt++) {
            assertThat(writer.claimObservationJob(pendingId)).as("attempt %s must be claimable", attempt)
                .isPresent();
            writer.markObservationFailed(pendingId, CONFIGURED_MAX_RETRIES, now, "TEST");
            assertThat(rowStatus(pendingId)).isEqualTo("pending");
            assertThat(jdbc.queryForObject(
                "SELECT retry_count FROM mem_pending_messages WHERE id = ?",
                Integer.class, pendingId)).isEqualTo(attempt);
            assertThat(jdbc.queryForObject(
                "SELECT failed_at_epoch FROM mem_pending_messages WHERE id = ?",
                Long.class, pendingId)).isNull();
        }

        // The final attempt exhausts the budget.
        assertThat(writer.claimObservationJob(pendingId)).isPresent();
        writer.markObservationFailed(pendingId, CONFIGURED_MAX_RETRIES, now, "TEST");

        assertThat(rowStatus(pendingId)).isEqualTo("failed");
        assertThat(jdbc.queryForObject(
            "SELECT retry_count FROM mem_pending_messages WHERE id = ?",
            Integer.class, pendingId)).isEqualTo(CONFIGURED_MAX_RETRIES);
        assertThat(jdbc.queryForObject(
            "SELECT failed_at_epoch FROM mem_pending_messages WHERE id = ?",
            Long.class, pendingId)).isNotNull();
        assertThat(jdbc.queryForObject(
            "SELECT completed_at_epoch FROM mem_pending_messages WHERE id = ?",
            Long.class, pendingId)).isNotNull();

        // Terminal means terminal: a later event cannot claim or re-fail it.
        assertThat(writer.claimObservationJob(pendingId)).isEmpty();
        writer.markObservationFailed(pendingId, CONFIGURED_MAX_RETRIES, now, "TEST");
        assertThat(rowStatus(pendingId)).isEqualTo("failed");
        assertThat(jdbc.queryForObject(
            "SELECT retry_count FROM mem_pending_messages WHERE id = ?",
            Integer.class, pendingId)).isEqualTo(CONFIGURED_MAX_RETRIES);
    }

    @Test
    @DisplayName("a row whose Conversation closed is failed rather than processed")
    void closedConversationJobIsFailedNotClaimed() {
        UUID pendingId = enqueueOne();
        UUID conversationId = conversationIdOf(pendingId);
        jdbc.update("UPDATE mem_sessions SET status = 'completed' WHERE id = ?", conversationId);

        assertThat(writer.claimObservationJob(pendingId)).isEmpty();

        assertThat(rowStatus(pendingId)).isEqualTo("failed");
        assertThat(jdbc.queryForObject(
            "SELECT completed_at_epoch FROM mem_pending_messages WHERE id = ?",
            Long.class, pendingId)).isNotNull();
        assertThat(jdbc.queryForObject(
            "SELECT failed_at_epoch FROM mem_pending_messages WHERE id = ?",
            Long.class, pendingId)).isNotNull();
    }

    // ------------------------------------------------------------------
    // Observability: queue depth and failure reasons are queryable
    // ------------------------------------------------------------------

    @Test
    @DisplayName("queue depth counts a claimed (processing) job as open work")
    void queueDepthIncludesProcessingRows() {
        long before = pendingMessageRepository.countOpenJobs();
        UUID first = enqueueOne();
        UUID second = enqueueOne();
        assertThat(pendingMessageRepository.countOpenJobs()).isEqualTo(before + 2);

        assertThat(writer.claimObservationJob(first)).isPresent();
        assertThat(pendingMessageRepository.countOpenJobs())
            .as("moving pending -> processing must not make a job disappear from the depth")
            .isEqualTo(before + 2);

        writer.markObservationSkipped(first, conversationIdOf(first));
        assertThat(pendingMessageRepository.countOpenJobs()).isEqualTo(before + 1);
        assertThat(second).isNotNull();
    }

    @Test
    @DisplayName("a terminal failure is countable and its reason stays queryable")
    void failedJobsAndReasonsAreQueryable() {
        long failedBefore = pendingMessageRepository.countByStatus("failed");
        long since = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
        long recentBefore = pendingMessageRepository.countFailedSince(since);

        UUID pendingId = enqueueOne();
        for (int attempt = 0; attempt < CONFIGURED_MAX_RETRIES; attempt++) {
            assertThat(writer.claimObservationJob(pendingId)).isPresent();
            writer.markObservationFailed(
                pendingId, CONFIGURED_MAX_RETRIES, System.currentTimeMillis(), "TEST");
        }

        assertThat(rowStatus(pendingId)).isEqualTo("failed");
        assertThat(pendingMessageRepository.countByStatus("failed")).isEqualTo(failedBefore + 1);
        assertThat(pendingMessageRepository.countFailedSince(since)).isEqualTo(recentBefore + 1);
        assertThat(pendingMessageRepository.findRecentFailureKinds(since))
            .as("the bounded failure vocabulary is what operators query")
            .contains("TEST");
        assertThat(jdbc.queryForObject(
            "SELECT failure_kind FROM mem_pending_messages WHERE id = ?", String.class, pendingId))
            .isEqualTo("TEST");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private UUID enqueueOne() {
        String contentSessionId = "claim-test-" + UUID.randomUUID();
        SessionEntity session = sessionManagementService.getOrCreate(
            ExternalSessionRef.of("claude", contentSessionId),
            "/claim-test/" + UUID.randomUUID(), "claim test");
        conversationIds.add(session.getId());

        PendingMessageEntity pending = new PendingMessageEntity();
        pending.setSessionDbId(session.getId());
        pending.setContentSessionId(contentSessionId);
        pending.setMessageType("observation");
        pending.setToolName("Bash");
        pending.setToolInput("echo claim");
        pending.setToolInputHash("hash-" + UUID.randomUUID());
        pending.setToolResponse("claim output");
        pending.setStatus("pending");
        pending.setRetryCount(0);
        pending.setCreatedAtEpoch(System.currentTimeMillis());
        UUID id = pendingMessageRepository.saveAndFlush(pending).getId();
        pendingIds.add(id);
        return id;
    }

    private String rowStatus(UUID pendingId) {
        return jdbc.queryForObject(
            "SELECT status FROM mem_pending_messages WHERE id = ?", String.class, pendingId);
    }

    private UUID conversationIdOf(UUID pendingId) {
        return jdbc.queryForObject(
            "SELECT session_db_id FROM mem_pending_messages WHERE id = ?", UUID.class, pendingId);
    }

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required for claim concurrency tests");
        }
        return value;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
