package com.ablueforce.cortexce.event;

import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.service.ConversationWriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PendingMessageEventListener.
 * Note: AgentService cannot be mocked due to Java 24 + ByteBuddy incompatibility
 * with its class hierarchy (LogHelper interface + final classes).
 * Tests cover the code paths that do NOT call AgentService:
 * - unsupported message type → marks as failed
 * - message not found → no-ops
 * - exception during save → does not propagate
 * - exception during find → does not propagate
 * - exception escaping the worker → goes through the retry budget, not a blind dead-letter
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PendingMessageEventListenerTest {

    private static final int CONFIGURED_MAX_RETRIES = 3;

    @Mock
    private PendingMessageRepository pendingMessageRepository;

    @Mock
    private ConversationWriteService conversationWriteService;

    // Stub AgentService that does nothing (avoids ByteBuddy Java 24 issue)
    private com.ablueforce.cortexce.service.AgentService noopAgentService;
    private com.ablueforce.cortexce.service.SummaryGenerationService noopSummaryService;

    private PendingMessageEventListener listener;
    private UUID messageId;

    @BeforeEach
    void setUp() {
        // Create a no-op AgentService subclass that doesn't do anything
        noopAgentService = new com.ablueforce.cortexce.service.AgentService(
            null, null, null, null, null, null, null, null, null, null, null
        ) {
            @Override
            public void processPendingMessage(UUID pendingMessageId) {
                // NOOP — do nothing, prevents NPE from null dependencies
            }
        };
        noopSummaryService = new com.ablueforce.cortexce.service.SummaryGenerationService(
            null, null, null, null, null, null, null, null, null, null, null, null, null
        ) {
            @Override
            public void processSummaryJob(UUID pendingMessageId) {
                // NOOP for unsupported-type tests.
            }
        };
        listener = new PendingMessageEventListener(
            noopAgentService, pendingMessageRepository, noopSummaryService, conversationWriteService);
        ReflectionTestUtils.setField(listener, "maxRetries", CONFIGURED_MAX_RETRIES);
        messageId = UUID.randomUUID();
    }

    // ===== unsupported message type =====

    @Test
    void handleUnsupportedType_marksAsFailed() {
        // Given: non-observation message type
        PendingMessageEntity entity = new PendingMessageEntity();
        entity.setId(messageId);
        entity.setStatus("pending");

        when(pendingMessageRepository.findById(messageId))
            .thenReturn(Optional.of(entity));

        PendingMessageEvent event = new PendingMessageEvent(
            messageId, "unknown_type", PendingMessageEvent.EventSource.API);

        // When
        listener.handlePendingMessageEvent(event);

        // Then: message should be marked as failed
        verify(pendingMessageRepository).save(entity);
        assertThat(entity.getStatus()).isEqualTo("failed");
    }

    @Test
    void handleUnsupportedType_messageNotFound_noOps() {
        // Given: entity not found in DB
        when(pendingMessageRepository.findById(messageId))
            .thenReturn(Optional.empty());

        PendingMessageEvent event = new PendingMessageEvent(
            messageId, "random_type", PendingMessageEvent.EventSource.SCHEDULED);

        // When/Then: should NOT throw
        listener.handlePendingMessageEvent(event); // must not throw
        verify(pendingMessageRepository, never()).save(any());
    }

    // ===== exception handling =====

    @Test
    void handleUnsupportedType_saveException_doesNotPropagate() {
        // Given: find succeeds but save throws
        PendingMessageEntity entity = new PendingMessageEntity();
        entity.setId(messageId);
        entity.setStatus("pending");

        when(pendingMessageRepository.findById(messageId))
            .thenReturn(Optional.of(entity));
        when(pendingMessageRepository.save(any()))
            .thenThrow(new RuntimeException("DB error"));

        PendingMessageEvent event = new PendingMessageEvent(
            messageId, "unknown_type", PendingMessageEvent.EventSource.SCHEDULED);

        // When/Then: should NOT throw — self-protected in catch block
        listener.handlePendingMessageEvent(event);
    }

    @Test
    void handleUnsupportedType_findException_doesNotPropagate() {
        // Given: findById throws
        when(pendingMessageRepository.findById(messageId))
            .thenThrow(new RuntimeException("DB connection lost"));

        PendingMessageEvent event = new PendingMessageEvent(
            messageId, "unknown_type", PendingMessageEvent.EventSource.SCHEDULED);

        // When/Then: should NOT throw — self-protected
        listener.handlePendingMessageEvent(event);
    }

    // ===== observation failures escaping the worker =====

    @Test
    void retryableFailureEscapingWorkerUsesFullRetryBudget() {
        PendingMessageEventListener retryListener = listenerWithAgentFailingWith(
            new RuntimeException("LLM provider returned 429"));

        retryListener.handlePendingMessageEvent(observationEvent());

        verify(conversationWriteService).markObservationFailed(
            eq(messageId), eq(CONFIGURED_MAX_RETRIES), anyLong(), anyString());
        // The old implementation dead-lettered here without consulting the budget.
        verify(pendingMessageRepository, never()).save(any());
    }

    @Test
    void terminalFailureEscapingWorkerDoesNotConsumeTheBudget() {
        PendingMessageEventListener retryListener = listenerWithAgentFailingWith(
            new RuntimeException("Gemini API error: 401 Unauthorized"));

        retryListener.handlePendingMessageEvent(observationEvent());

        verify(conversationWriteService).markObservationFailed(eq(messageId), eq(1), anyLong(), anyString());
        verify(pendingMessageRepository, never()).save(any());
    }

    @Test
    void unrecordableFailureLeavesRowProcessingAndDoesNotPropagate() {
        PendingMessageEventListener retryListener = listenerWithAgentFailingWith(
            new RuntimeException("LLM provider unavailable"));
        doThrow(new RuntimeException("lock timeout"))
            .when(conversationWriteService).markObservationFailed(any(), anyInt(), anyLong(), anyString());

        // Must not throw: the async executor would otherwise lose the only record.
        retryListener.handlePendingMessageEvent(observationEvent());

        verify(pendingMessageRepository, never()).save(any());
    }

    private PendingMessageEventListener listenerWithAgentFailingWith(RuntimeException failure) {
        com.ablueforce.cortexce.service.AgentService failingAgent =
            new com.ablueforce.cortexce.service.AgentService(
                null, null, null, null, null, null, null, null, null, null, null
            ) {
                @Override
                public void processPendingMessage(UUID pendingMessageId) {
                    throw failure;
                }
            };
        PendingMessageEventListener retryListener = new PendingMessageEventListener(
            failingAgent, pendingMessageRepository, noopSummaryService, conversationWriteService);
        ReflectionTestUtils.setField(retryListener, "maxRetries", CONFIGURED_MAX_RETRIES);
        return retryListener;
    }

    private PendingMessageEvent observationEvent() {
        return new PendingMessageEvent(
            messageId, "observation", PendingMessageEvent.EventSource.SCHEDULED);
    }
}
