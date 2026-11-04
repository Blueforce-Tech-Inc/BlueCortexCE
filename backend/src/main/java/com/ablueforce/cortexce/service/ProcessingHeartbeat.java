package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.repository.PendingMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the lease on Observation jobs this instance is still working on.
 *
 * <p>Lease ownership is per instance: the set below only contains ids this JVM
 * claimed and has not finished. {@link StaleMessageRecoveryTask} on any instance
 * reclaims rows whose {@code started_processing_at_epoch} is older than the threshold,
 * and it deliberately cannot distinguish a crashed worker from a slow one. Without
 * this heartbeat a long provider call on instance A (3 x 120 s reads plus retries)
 * can exceed the 10-minute threshold, and instance B would process the same trace
 * again. Refreshing the timestamp while the job is alive removes that window.
 *
 * <p>A crashed instance stops refreshing, so its rows still become recoverable after
 * the threshold. The heartbeat never touches completed rows: the repository update
 * requires {@code status = 'processing'}.
 */
@Component
public class ProcessingHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(ProcessingHeartbeat.class);

    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final PendingMessageRepository pendingMessageRepository;
    private final TransactionTemplate transactionTemplate;

    @Value("${claudemem.processing-heartbeat.interval-ms:30000}")
    private long intervalMs;

    public ProcessingHeartbeat(PendingMessageRepository pendingMessageRepository,
                               TransactionTemplate transactionTemplate) {
        this.pendingMessageRepository = pendingMessageRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /** Register a job this instance just claimed. */
    public void start(UUID pendingMessageId) {
        inFlight.add(pendingMessageId);
    }

    /** Drop a job this instance finished (success, failure, or skipped). */
    public void stop(UUID pendingMessageId) {
        inFlight.remove(pendingMessageId);
    }

    /** Snapshot of the jobs this instance currently owns, safe to iterate. */
    public Set<UUID> inFlightIds() {
        return Set.copyOf(inFlight);
    }

    /** Number of jobs this instance currently owns. */
    public int inFlightCount() {
        return inFlight.size();
    }

    /**
     * Refresh every lease this instance still holds. Runs independently on each
     * instance; there is no cross-instance state and no coordination needed.
     */
    @Scheduled(fixedRateString = "${claudemem.processing-heartbeat.interval-ms:30000}",
        initialDelayString = "${claudemem.processing-heartbeat.interval-ms:30000}")
    public void beat() {
        List<UUID> ids = List.copyOf(inFlight);
        if (ids.isEmpty()) {
            return;
        }
        try {
            long now = Instant.now().toEpochMilli();
            transactionTemplate.executeWithoutResult(status ->
                pendingMessageRepository.heartbeatProcessing(ids, now));
        } catch (Exception e) {
            // A missed beat only shortens the lease; stale recovery still has the
            // production threshold to wait. Never let it kill the worker thread.
            log.warn("Could not refresh {} in-flight job leases", ids.size(), e);
        }
    }

    @PreDestroy
    void stopAll() {
        inFlight.clear();
    }
}
