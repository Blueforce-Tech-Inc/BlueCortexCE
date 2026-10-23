package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceAdmissionTest {

    @Mock private PendingMessageRepository pendingMessageRepository;
    @Mock private SessionRepository sessionRepository;

    private AgentService service;
    private UUID conversationId;
    private SessionEntity session;

    @BeforeEach
    void setUp() {
        service = new AgentService(null, null, pendingMessageRepository, sessionRepository,
            null, null, null, null, null, null);
        conversationId = UUID.randomUUID();
        session = new SessionEntity();
        session.setId(conversationId);
        session.setContentSessionId("opaque/session-id");
        session.setStatus("active");
        session.setStartedAtEpoch(Instant.now().toEpochMilli());
        when(sessionRepository.lockSessionForUpdate(conversationId)).thenReturn(Optional.of(conversationId));
        when(sessionRepository.findById(conversationId)).thenReturn(Optional.of(session));
    }

    @Test
    void toolUseIsDurablyEnqueuedWhileConversationIsActive() {
        UUID pendingId = UUID.randomUUID();
        when(pendingMessageRepository.countBySessionDbIdAndTool(any(), any(), any())).thenReturn(0L);
        when(pendingMessageRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            PendingMessageEntity pending = invocation.getArgument(0);
            pending.setId(pendingId);
            return pending;
        });

        Optional<UUID> accepted = service.enqueueToolUse(conversationId, "opaque/session-id",
            "Edit", "{}", "{}", "/repo", null);

        assertThat(accepted).contains(pendingId);
        verify(sessionRepository).lockSessionForUpdate(conversationId);
        verify(pendingMessageRepository).saveAndFlush(any(PendingMessageEntity.class));
    }

    @Test
    void toolUseIsRejectedAfterSessionEnd() {
        session.setStatus("summarizing");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.enqueueToolUse(conversationId, "opaque/session-id",
                "Edit", "{}", "{}", "/repo", null));

        assertThat(error.getStatusCode().value()).isEqualTo(409);
        verify(pendingMessageRepository, never()).save(any());
    }
}
