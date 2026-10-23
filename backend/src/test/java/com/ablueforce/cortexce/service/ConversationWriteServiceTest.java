package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.event.PendingMessageEventPublisher;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.repository.UserPromptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationWriteServiceTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private PendingMessageRepository pendingMessageRepository;
    @Mock private ObservationRepository observationRepository;
    @Mock private UserPromptRepository userPromptRepository;
    @Mock private SSEBroadcaster sseBroadcaster;
    @Mock private PendingMessageEventPublisher pendingMessageEventPublisher;

    private ConversationWriteService service;
    private UUID conversationId;
    private SessionEntity session;

    @BeforeEach
    void setUp() {
        service = new ConversationWriteService(sessionRepository, pendingMessageRepository,
            observationRepository, userPromptRepository, sseBroadcaster, pendingMessageEventPublisher);
        conversationId = UUID.randomUUID();
        session = new SessionEntity();
        session.setId(conversationId);
        session.setPlatformSource("claude");
        session.setContentSessionId("opaque/session-id");
        session.setStatus("summarizing");
        when(sessionRepository.lockSessionForUpdate(conversationId)).thenReturn(Optional.of(conversationId));
        when(sessionRepository.findById(conversationId)).thenReturn(Optional.of(session));
    }

    @Test
    void directObservationIsRejectedAfterSessionEndClosesAdmission() {
        ObservationEntity observation = observation(conversationId);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.saveObservation(conversationId, null, observation, 0));

        assertThat(error.getStatusCode().value()).isEqualTo(409);
        verifyNoInteractions(observationRepository);
        verify(pendingMessageRepository, never()).save(any());
    }

    @Test
    void admittedObservationCompletesAndWakesDeferredSummaryWhenQueueDrains() {
        UUID pendingId = UUID.randomUUID();
        UUID summaryId = UUID.randomUUID();
        PendingMessageEntity observationJob = new PendingMessageEntity();
        observationJob.setId(pendingId);
        observationJob.setSessionDbId(conversationId);
        observationJob.setMessageType("observation");
        observationJob.setStatus("processing");
        PendingMessageEntity summaryJob = new PendingMessageEntity();
        summaryJob.setId(summaryId);
        summaryJob.setStatus(SummaryJobService.STATUS_PENDING);
        ObservationEntity observation = observation(conversationId);

        when(pendingMessageRepository.findById(pendingId)).thenReturn(Optional.of(observationJob));
        when(pendingMessageRepository.countOpenObservationJobs(conversationId)).thenReturn(0L);
        when(pendingMessageRepository.findSummaryJob(
            conversationId, SummaryJobService.JOB_KEY_SESSION_SUMMARY)).thenReturn(Optional.of(summaryJob));
        when(observationRepository.findDuplicateByContentHash(eq("digest"), anyLong(), any(UUID.class)))
            .thenReturn(Optional.empty());
        when(observationRepository.saveAndFlush(observation)).thenReturn(observation);
        when(pendingMessageRepository.saveAndFlush(observationJob)).thenReturn(observationJob);

        ObservationEntity saved = service.saveObservation(conversationId, pendingId, observation, 0);

        assertThat(saved).isSameAs(observation);
        assertThat(observationJob.getStatus()).isEqualTo("processed");
        verify(pendingMessageEventPublisher).publishPendingMessageEvent(summaryId, "summarize");
        verify(sseBroadcaster).broadcast(anyMap(), eq("new_observation"));
    }

    private ObservationEntity observation(UUID conversationId) {
        ObservationEntity observation = new ObservationEntity();
        observation.setConversationId(conversationId);
        observation.setPlatformSource("claude");
        observation.setContentSessionId("opaque/session-id");
        observation.setMemoryScopeId(UUID.randomUUID());
        observation.setContentHash("digest");
        return observation;
    }
}
