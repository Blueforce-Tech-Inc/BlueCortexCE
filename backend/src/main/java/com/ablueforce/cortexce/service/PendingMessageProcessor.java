package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.event.PendingMessageEventPublisher;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service for processing pending messages.
 * 
 * Architecture:
 * - Scheduled task runs periodically to find and process pending messages
 * - Each message is processed via Spring Event
 * - Ensures no messages are left unprocessed
 */
@Service
public class PendingMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(PendingMessageProcessor.class);

    private final PendingMessageRepository pendingMessageRepository;
    private final PendingMessageEventPublisher eventPublisher;

    @Value("${app.pending-message.enabled:true}")
    private boolean enabled;

    public PendingMessageProcessor(PendingMessageRepository pendingMessageRepository,
                                  PendingMessageEventPublisher eventPublisher) {
        this.pendingMessageRepository = pendingMessageRepository;
        this.eventPublisher = eventPublisher;
        
        if (enabled) {
            log.info("PendingMessageProcessor initialized, enabled=true");
        } else {
            log.info("PendingMessageProcessor initialized, enabled=false");
        }
    }

    /**
     * Scheduled task - processes pending messages periodically.
     * Runs every 5 minutes (configurable).
     */
    @Scheduled(fixedRateString = "${app.pending-message.schedule-interval-ms:300000}")
    public void processPendingMessages() {
        if (!enabled) {
            log.debug("Pending message processing is disabled");
            return;
        }

        log.info("Starting scheduled pending message processing");

        try {
            // Find pending messages
            List<PendingMessageEntity> pending = pendingMessageRepository
                .findByStatusOrderByCreatedAtEpochAsc("pending");

            if (pending.isEmpty()) {
                log.debug("No pending messages to process");
                return;
            }

            log.info("Found {} pending messages to process", pending.size());

            // Process each pending message via event
            int processed = 0;
            for (PendingMessageEntity msg : pending) {
                try {
                    eventPublisher.publishScheduledPendingMessageEvent(
                        msg.getId(),
                        msg.getMessageType()
                    );
                    processed++;
                } catch (Exception e) {
                    log.error("Failed to publish event for pending message: {}", msg.getId(), e);
                }
            }

            log.info("Published {} pending messages for processing", processed);

        } catch (Exception e) {
            log.error("Failed to process pending messages", e);
        }
    }
}
