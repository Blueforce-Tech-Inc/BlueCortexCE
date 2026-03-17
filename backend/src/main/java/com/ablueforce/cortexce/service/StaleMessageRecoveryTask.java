package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.repository.PendingMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;

/**
 * P1-1: Stale message recovery task.
 * <p>
 * Runs on startup and periodically. Resets messages stuck in "processing" for over threshold
 * back to "pending" so they can be retried.
 */
@Component
public class StaleMessageRecoveryTask {

    private static final Logger log = LoggerFactory.getLogger(StaleMessageRecoveryTask.class);

    private final PendingMessageRepository pendingMessageRepository;

    @Value("${claudemem.stale-message.threshold-minutes:10}")
    private int staleThresholdMinutes;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public StaleMessageRecoveryTask(PendingMessageRepository pendingMessageRepository) {
        this.pendingMessageRepository = pendingMessageRepository;
    }

    /**
     * Run immediately on startup to recover any stale messages.
     * Note: @PostConstruct cannot use @Transactional directly, so we use TransactionTemplate.
     */
    @PostConstruct
    public void recoverStaleMessagesOnStartup() {
        transactionTemplate.executeWithoutResult(status -> {
            // P3: Use Duration for readable threshold calculation
            Duration threshold = Duration.ofMinutes(staleThresholdMinutes);
            long thresholdEpoch = Instant.now().minus(threshold).toEpochMilli();
            int recovered = pendingMessageRepository.updateStaleMessages("processing", "pending", thresholdEpoch);

            if (recovered > 0) {
                log.warn("Startup: Recovered {} stale messages from previous run", recovered);
            }
        });
    }

    /**
     * Every N milliseconds: find messages stuck in "processing" for >threshold, reset to "pending".
     */
    @Scheduled(fixedRateString = "${claudemem.stale-message.recovery-interval-ms:300000}", initialDelay = 60000)
    @Transactional
    public void recoverStaleMessages() {
        long thresholdEpoch = Instant.now().minusSeconds(staleThresholdMinutes * 60L).toEpochMilli();
        int recovered = pendingMessageRepository.updateStaleMessages("processing", "pending", thresholdEpoch);

        if (recovered > 0) {
            log.warn("Recovered {} stale messages (processing > {} min)", recovered, staleThresholdMinutes);
        } else {
            log.trace("Stale message check: no stale messages found");
        }
    }
}
