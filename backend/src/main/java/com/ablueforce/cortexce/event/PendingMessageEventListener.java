package com.ablueforce.cortexce.event;

import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.service.AgentService;
import com.ablueforce.cortexce.service.ConversationWriteService;
import com.ablueforce.cortexce.service.RetryPolicy;
import com.ablueforce.cortexce.service.SummaryGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final SummaryGenerationService summaryGenerationService;
    private final ConversationWriteService conversationWriteService;

    @Value("${claudemem.max-retries:3}")
    private int maxRetries;

    public PendingMessageEventListener(AgentService agentService,
                                       PendingMessageRepository pendingMessageRepository,
                                       SummaryGenerationService summaryGenerationService,
                                       ConversationWriteService conversationWriteService) {
        this.agentService = agentService;
        this.pendingMessageRepository = pendingMessageRepository;
        this.summaryGenerationService = summaryGenerationService;
        this.conversationWriteService = conversationWriteService;
    }

    /**
     * Handle pending message event asynchronously.
     *
     * <p>The catch block used to write {@code status='failed'} directly. That bypassed
     * the retry budget — one transient error surfaced here became a dead-letter — and
     * it ignored the row's current state, so an event delivered late could overwrite a
     * {@code processed} row. Failures now go through the same transition as a failure
     * inside the job, and a failure we cannot record leaves the row {@code processing}
     * for stale recovery instead of lying about its state.
     */
    @Async
    @EventListener
    public void handlePendingMessageEvent(PendingMessageEvent event) {
        log.info("Received PendingMessageEvent: {}", event);

        try {
            if ("observation".equals(event.getMessageType())) {
                agentService.processPendingMessage(event.getPendingMessageId());
                log.info("Processed pending message: {}", event.getPendingMessageId());
            } else if ("summarize".equals(event.getMessageType())) {
                summaryGenerationService.processSummaryJob(event.getPendingMessageId());
                log.info("Processed durable summarize job: {}", event.getPendingMessageId());
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
            if ("summarize".equals(event.getMessageType())) {
                // SummaryGenerationService records retry state under the Conversation lock.
                // Do not bypass that state machine with a blind status update here.
                return;
            }
            if (!"observation".equals(event.getMessageType())) {
                // Unknown job types have no retry budget; dead-letter them once.
                deadLetterUnsupported(event);
                return;
            }
            try {
                conversationWriteService.markObservationFailed(
                    event.getPendingMessageId(),
                    RetryPolicy.isRetryable(e) ? maxRetries : 1,
                    System.currentTimeMillis(),
                    RetryPolicy.failureKind(e));
            } catch (Exception ex) {
                // Leaving the row in 'processing' is the honest state: the stale-message
                // recovery task will return it to 'pending' after its threshold.
                log.error("Could not record failure for message {}; it stays processing "
                    + "until stale recovery", event.getPendingMessageId(), ex);
            }
        }
    }

    private void deadLetterUnsupported(PendingMessageEvent event) {
        try {
            pendingMessageRepository.findById(event.getPendingMessageId()).ifPresent(msg -> {
                if (!"failed".equals(msg.getStatus())) {
                    msg.setStatus("failed");
                    pendingMessageRepository.save(msg);
                }
            });
        } catch (Exception ex) {
            log.error("Failed to mark message {} as failed", event.getPendingMessageId(), ex);
        }
    }
}
