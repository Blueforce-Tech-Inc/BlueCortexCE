package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only operational counters for the readiness endpoint.
 *
 * <p>Batching these three queries behind one service keeps HealthController free of
 * repository imports, and gives a future metric exporter the same definition of
 * "failed", "recent failure" and "embedding backlog" instead of a second copy.
 */
@Service
public class OperationalStatusService {

    private static final long RECENT_FAILURE_WINDOW_MS = 24L * 60 * 60 * 1000;

    private final PendingMessageRepository pendingMessageRepository;
    private final ObservationRepository observationRepository;

    public OperationalStatusService(PendingMessageRepository pendingMessageRepository,
                                    ObservationRepository observationRepository) {
        this.pendingMessageRepository = pendingMessageRepository;
        this.observationRepository = observationRepository;
    }

    public record QueueStatus(
        long failed,
        long failedLast24h,
        long embeddingBacklog) {
    }

    @Transactional(readOnly = true)
    public QueueStatus queueStatus() {
        long failed = pendingMessageRepository.countByStatus("failed");
        long recentFailures = pendingMessageRepository.countFailedSince(
            System.currentTimeMillis() - RECENT_FAILURE_WINDOW_MS);
        long embeddingBacklog = observationRepository.countMissingEmbeddings();
        return new QueueStatus(failed, recentFailures, embeddingBacklog);
    }
}
