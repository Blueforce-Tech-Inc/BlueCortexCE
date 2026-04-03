package com.ablueforce.cortexce.event;

import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PendingMessageEventListenerTest {

    @Mock
    private PendingMessageRepository pendingMessageRepository;

    // Stub AgentService that does nothing (avoids ByteBuddy Java 24 issue)
    private com.ablueforce.cortexce.service.AgentService noopAgentService;

    private PendingMessageEventListener listener;
    private UUID messageId;

    @BeforeEach
    void setUp() {
        // Create a no-op AgentService subclass that doesn't do anything
        noopAgentService = new com.ablueforce.cortexce.service.AgentService(
            null, null, null, null, null, null, null, null, null
        ) {
            @Override
            public void processPendingMessage(UUID pendingMessageId) {
                // NOOP — do nothing, prevents NPE from null dependencies
            }
        };
        listener = new PendingMessageEventListener(noopAgentService, pendingMessageRepository);
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
}
