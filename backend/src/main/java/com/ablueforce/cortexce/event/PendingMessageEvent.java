package com.ablueforce.cortexce.event;

import java.util.UUID;

/**
 * Event fired to process pending messages.
 * 
 * This follows the architecture: Spring Event → @Async EventListener → @Scheduled fallback.
 */
public class PendingMessageEvent {

    private final UUID pendingMessageId;
    private final String messageType;
    private final EventSource source;

    public enum EventSource {
        API,        // Direct API trigger
        SCHEDULED   // Scheduled task fallback
    }

    public PendingMessageEvent(UUID pendingMessageId, String messageType, EventSource source) {
        this.pendingMessageId = pendingMessageId;
        this.messageType = messageType;
        this.source = source;
    }

    public UUID getPendingMessageId() {
        return pendingMessageId;
    }

    public String getMessageType() {
        return messageType;
    }

    public EventSource getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "PendingMessageEvent{" +
                "pendingMessageId=" + pendingMessageId +
                ", messageType='" + messageType + '\'' +
                ", source=" + source +
                '}';
    }
}
