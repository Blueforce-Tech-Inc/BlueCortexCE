package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.repository.PendingMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stops this instance from taking new queue work on SIGTERM, waits a bounded
 * time for the jobs it already claimed, then puts the leftovers back in the
 * queue before the JVM exits.
 *
 * <p>Without this, a claimed row stays {@code processing} after a clean
 * shutdown until {@code StaleMessageRecoveryTask} reclaims it — ten minutes in
 * production. The row is not lost, but the work is invisible and the next
 * process on the same queue skips it. The heartbeat keeps the lease alive for
 * a live worker; it deliberately stops refreshing when the JVM exits, which is
 * exactly the "crashed instance" case the sweeper is designed for.
 *
 * <p>The phase is above the web server's own lifecycle, so this runs first:
 * new events stop being accepted, then the HTTP layer gets its graceful
 * shutdown window. Jobs that finish during the wait complete normally; only
 * rows still in {@code processing} when the deadline passes are requeued, and
 * the conditional UPDATE ignores rows another worker already finished.
 */
@Component
public class GracefulShutdownCoordinator implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownCoordinator.class);

    /**
     * Higher than the web server's lifecycle ({@code DEFAULT_PHASE - 1024}),
     * so this stop callback runs before the HTTP connector stops.
     */
    private static final int PHASE = Integer.MAX_VALUE - 1;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final ProcessingHeartbeat processingHeartbeat;
    private final PendingMessageRepository pendingMessageRepository;
    private final TransactionTemplate transactionTemplate;
    private volatile boolean running;

    @Value("${claudemem.shutdown.in-flight-timeout-ms:30000}")
    private long inFlightTimeoutMs;

    @Value("${claudemem.shutdown.poll-interval-ms:500}")
    private long pollIntervalMs;

    public GracefulShutdownCoordinator(ProcessingHeartbeat processingHeartbeat,
                                       PendingMessageRepository pendingMessageRepository,
                                       TransactionTemplate transactionTemplate) {
        this.processingHeartbeat = processingHeartbeat;
        this.pendingMessageRepository = pendingMessageRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /** True once shutdown started; callers must not claim new queue work. */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }

        long startedAt = System.currentTimeMillis();
        long deadline = startedAt + Math.max(0L, inFlightTimeoutMs);

        // New claims are refused from this point on (see isShuttingDown callers),
        // so the in-flight set can only shrink while we wait.
        while (System.currentTimeMillis() < deadline) {
            Set<UUID> ids = processingHeartbeat.inFlightIds();
            if (ids.isEmpty()) {
                log.info("Graceful shutdown: no in-flight jobs, drained in {} ms",
                    System.currentTimeMillis() - startedAt);
                return;
            }
            try {
                Thread.sleep(Math.min(Math.max(1L, pollIntervalMs), Math.max(1L, deadline - System.currentTimeMillis())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        Set<UUID> remaining = processingHeartbeat.inFlightIds();
        if (remaining.isEmpty()) {
            log.info("Graceful shutdown: in-flight jobs finished, drained in {} ms",
                System.currentTimeMillis() - startedAt);
            return;
        }

        // Requeue only rows this instance still owns. The UPDATE requires
        // status = 'processing', so a row that completed while we were counting
        // is left alone, and a row owned by another instance is never touched.
        int requeued;
        try {
            requeued = transactionTemplate.execute(status ->
                pendingMessageRepository.requeueProcessing(remaining));
        } catch (Exception e) {
            processingHeartbeat.stopAll();
            log.error("Graceful shutdown: could not requeue {} in-flight job(s); "
                    + "stale recovery will reclaim them", remaining.size(), e);
            return;
        }
        processingHeartbeat.stopAll();
        log.warn("Graceful shutdown: requeued {} of {} in-flight job(s) after {} ms "
                + "(waiting for the next instance to claim them)",
            requeued, remaining.size(), System.currentTimeMillis() - startedAt);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
