package com.ablueforce.cortexce.event;

import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event listener for processing pending messages.
 * 
 * Architecture:
 * - Scheduled task → finds pending messages → publishes event
 * - This listener handles it asynchronously (@Async)
 * - Ensures all pending messages are processed
 */
@Component
public class PendingMessageEventListener {

    private static final Logger log = LoggerFactory.getLogger(PendingMessageEventListener.class);

    private final AgentService agentService;
    private final PendingMessageRepository pendingMessageRepository;

    public PendingMessageEventListener(AgentService agentService,
                                       PendingMessageRepository pendingMessageRepository) {
        this.agentService = agentService;
        this.pendingMessageRepository = pendingMessageRepository;
    }

    /**
     * Handle pending message event asynchronously.
     */
    @Async
    @EventListener
    public void handlePendingMessageEvent(PendingMessageEvent event) {
        log.info("Received PendingMessageEvent: {}", event);

        try {
            if ("observation".equals(event.getMessageType())) {
                agentService.processPendingMessage(event.getPendingMessageId());
                log.info("Processed pending message: {}", event.getPendingMessageId());
            } else {
                // Mark unsupported message types as failed (dead-letter)
                log.warn("Unsupported pending message type: {}, marking as failed", event.getMessageType());
                pendingMessageRepository.findById(event.getPendingMessageId())
                    .ifPresent(msg -> {
                        msg.setStatus("failed");
                        pendingMessageRepository.save(msg);
                    });
            }
        } catch (Exception e) {
            log.error("Failed to process PendingMessageEvent: {}", event, e);
            // Mark as failed to prevent infinite retry by the scheduled task
            pendingMessageRepository.findById(event.getPendingMessageId())
                .ifPresent(msg -> {
                    msg.setStatus("failed");
                    pendingMessageRepository.save(msg);
                });
        }
    }
}
