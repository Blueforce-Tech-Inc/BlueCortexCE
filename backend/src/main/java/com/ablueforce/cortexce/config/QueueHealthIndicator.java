package com.ablueforce.cortexce.config;

import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P1-2: Custom health indicator for message queue monitoring.
 * <p>
 * Reports queue depth, stale message count, and last successful processing time.
 * Appears under /actuator/health as "messageQueue".
 * <p>
 * "depth" counts pending <em>and</em> processing rows: a worker stuck on a row used to
 * disappear from the old pending-only number, so a jammed queue reported the same depth
 * as an empty one. Failure counts and their classifications are reported here so an
 * operator can tell "slow" from "dead" without reading application logs.
 */
@Component("messageQueue")
public class QueueHealthIndicator implements HealthIndicator {

    private static final long RECENT_FAILURE_WINDOW_MS = 24L * 60 * 60 * 1000;

    private final PendingMessageRepository pendingMessageRepository;
    private final ObservationRepository observationRepository;

    @Value("${claudemem.stale-message.threshold-minutes:10}")
    private int staleThresholdMinutes;

    @Value("${claudemem.stale-message.health-threshold:5}")
    private int healthThreshold;

    public QueueHealthIndicator(PendingMessageRepository pendingMessageRepository,
                                ObservationRepository observationRepository) {
        this.pendingMessageRepository = pendingMessageRepository;
        this.observationRepository = observationRepository;
    }

    @Override
    public Health health() {
        long pendingCount = pendingMessageRepository.countByStatus("pending");
        long processingCount = pendingMessageRepository.countByStatus("processing");

        long staleThreshold = Instant.now().minusSeconds(staleThresholdMinutes * 60L).toEpochMilli();
        long staleCount = pendingMessageRepository.countStale(staleThreshold);

        Long lastProcessedEpoch = pendingMessageRepository.findLastProcessedEpoch();
        String lastProcessed = lastProcessedEpoch != null
            ? Instant.ofEpochMilli(lastProcessedEpoch).toString()
            : "never";

        long failedCount = pendingMessageRepository.countByStatus("failed");
        long recentFailures = pendingMessageRepository.countFailedSince(
            Instant.now().minusMillis(RECENT_FAILURE_WINDOW_MS).toEpochMilli());
        Map<String, Integer> recentFailureKinds = recentFailureKinds();

        Health.Builder builder = (staleCount > healthThreshold) ? Health.down() : Health.up();

        return builder
            .withDetail("pending", pendingCount)
            .withDetail("processing", processingCount)
            .withDetail("depth", pendingCount + processingCount)
            .withDetail("failed", failedCount)
            .withDetail("failed_last_24h", recentFailures)
            .withDetail("recent_failure_kinds", recentFailureKinds)
            .withDetail("embedding_backlog", observationRepository.countMissingEmbeddings())
            .withDetail("stale", staleCount)
            .withDetail("last_processed", lastProcessed)
            .build();
    }

    private Map<String, Integer> recentFailureKinds() {
        List<String> kinds = pendingMessageRepository.findRecentFailureKinds(
            Instant.now().minusMillis(RECENT_FAILURE_WINDOW_MS).toEpochMilli());
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String kind : kinds) {
            String key = kind == null || kind.isBlank() ? "UNKNOWN" : kind;
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }
}
