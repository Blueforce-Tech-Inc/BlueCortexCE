package com.ablueforce.cortexce.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publisher for pending message events.
 */
@Component
public class PendingMessageEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PendingMessageEventPublisher.class);

    private final ApplicationEventPublisher eventPublisher;

    public PendingMessageEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Publish event to process a specific pending message.
     */
    public void publishPendingMessageEvent(UUID pendingMessageId, String messageType) {
        PendingMessageEvent event = new PendingMessageEvent(
            pendingMessageId,
            messageType,
            PendingMessageEvent.EventSource.API
        );
        
        log.debug("Publishing PendingMessageEvent for: {}", pendingMessageId);
        eventPublisher.publishEvent(event);
    }

    /**
     * Publish event from scheduled task.
     */
    public void publishScheduledPendingMessageEvent(UUID pendingMessageId, String messageType) {
        PendingMessageEvent event = new PendingMessageEvent(
            pendingMessageId,
            messageType,
            PendingMessageEvent.EventSource.SCHEDULED
        );
        
        log.debug("Publishing scheduled PendingMessageEvent for: {}", pendingMessageId);
        eventPublisher.publishEvent(event);
    }
}
