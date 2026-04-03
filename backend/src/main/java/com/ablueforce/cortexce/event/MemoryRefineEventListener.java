package com.ablueforce.cortexce.event;

import com.ablueforce.cortexce.service.MemoryRefineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event listener for memory refinement events.
 * 
 * Architecture:
 * - SessionEnd → publishes MemoryRefineEvent
 * - This listener handles it asynchronously (@Async)
 * - Scheduled task provides fallback if this fails
 */
@Component
public class MemoryRefineEventListener {

    private static final Logger log = LoggerFactory.getLogger(MemoryRefineEventListener.class);

    private final MemoryRefineService memoryRefineService;

    public MemoryRefineEventListener(MemoryRefineService memoryRefineService) {
        this.memoryRefineService = memoryRefineService;
    }

    /**
     * Handle memory refinement event asynchronously.
     * This is the "real-time" processing path.
     *
     * Both SESSION_END and MANUAL trigger the same refinement logic.
     * Differentiated behavior is determined by the event source and
     * logged separately for observability.
     */
    @Async
    @EventListener
    public void handleMemoryRefineEvent(MemoryRefineEvent event) {
        log.info("Received MemoryRefineEvent: type={}, project={}",
                event.getRefineType(), event.getProjectPath());

        try {
            memoryRefineService.refineMemory(event.getProjectPath());
            log.info("Refinement completed (type={}) for project: {}",
                    event.getRefineType(), event.getProjectPath());
        } catch (Exception e) {
            log.error("Failed to process MemoryRefineEvent: type={}, project={}",
                    event.getRefineType(), event.getProjectPath(), e);
            // Scheduled task will handle this as fallback
        }
    }
}
