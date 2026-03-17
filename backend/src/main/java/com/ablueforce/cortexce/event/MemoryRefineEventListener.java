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
     */
    @Async
    @EventListener
    public void handleMemoryRefineEvent(MemoryRefineEvent event) {
        log.info("Received MemoryRefineEvent: {}", event);

        try {
            if (event.getRefineType() == MemoryRefineEvent.RefineType.SESSION_END) {
                // Real-time: process immediately after session ends
                memoryRefineService.refineMemory(event.getProjectPath());
                log.info("Real-time refinement completed for project: {}", event.getProjectPath());
            } else if (event.getRefineType() == MemoryRefineEvent.RefineType.MANUAL) {
                // Manual trigger via API
                memoryRefineService.refineMemory(event.getProjectPath());
                log.info("Manual refinement completed for project: {}", event.getProjectPath());
            }
        } catch (Exception e) {
            log.error("Failed to process MemoryRefineEvent: {}", event, e);
            // Scheduled task will handle this as fallback
        }
    }
}
