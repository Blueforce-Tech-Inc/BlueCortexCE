package com.ablueforce.cortexce.config;

import com.ablueforce.cortexce.repository.PendingMessageRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * P1-2: Custom health indicator for message queue monitoring.
 * <p>
 * Reports queue depth, stale message count, and last successful processing time.
 * Appears under /actuator/health as "messageQueue".
 */
@Component("messageQueue")
public class QueueHealthIndicator implements HealthIndicator {

    private final PendingMessageRepository pendingMessageRepository;

    @Value("${claudemem.stale-message.threshold-minutes:10}")
    private int staleThresholdMinutes;

    @Value("${claudemem.stale-message.health-threshold:5}")
    private int healthThreshold;

    public QueueHealthIndicator(PendingMessageRepository pendingMessageRepository) {
        this.pendingMessageRepository = pendingMessageRepository;
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

        Health.Builder builder = (staleCount > healthThreshold) ? Health.down() : Health.up();

        return builder
            .withDetail("pending", pendingCount)
            .withDetail("processing", processingCount)
            .withDetail("stale", staleCount)
            .withDetail("last_processed", lastProcessed)
            .build();
    }
}
