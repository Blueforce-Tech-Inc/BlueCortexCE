package com.ablueforce.cortexce.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publisher for memory refinement events.
 * 
 * Used by AgentService to publish events when session ends.
 */
@Component
public class MemoryRefineEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MemoryRefineEventPublisher.class);

    private final ApplicationEventPublisher eventPublisher;

    public MemoryRefineEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Publish event to trigger memory refinement (real-time path).
     */
    public void publishRefineEvent(String projectPath, String sessionId) {
        MemoryRefineEvent event = new MemoryRefineEvent(
            projectPath, 
            sessionId, 
            MemoryRefineEvent.RefineType.SESSION_END
        );
        
        log.info("Publishing MemoryRefineEvent for session: {}", sessionId);
        eventPublisher.publishEvent(event);
    }

    /**
     * Publish manual refinement event.
     */
    public void publishManualRefineEvent(String projectPath) {
        MemoryRefineEvent event = new MemoryRefineEvent(
            projectPath, 
            null,
            MemoryRefineEvent.RefineType.MANUAL
        );
        
        log.info("Publishing manual MemoryRefineEvent for project: {}", projectPath);
        eventPublisher.publishEvent(event);
    }
}
