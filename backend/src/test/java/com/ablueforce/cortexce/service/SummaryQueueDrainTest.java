package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummaryQueueDrainTest {

    @Test
    void summaryDefersWithoutReadingObservationsOrCallingLlmWhileQueueIsOpen() {
        SessionManagementService sessions = mock(SessionManagementService.class);
        ObservationRepository observations = mock(ObservationRepository.class);
        PendingMessageRepository pendingMessages = mock(PendingMessageRepository.class);
        SummaryRepository summaries = mock(SummaryRepository.class);
        SummaryJobService jobs = mock(SummaryJobService.class);
        SummaryResultPersistenceService resultPersistence = mock(SummaryResultPersistenceService.class);
        LlmService llm = mock(LlmService.class);
        SummaryGenerationService service = new SummaryGenerationService(
            sessions, observations, pendingMessages, summaries, mock(SSEBroadcaster.class), llm,
            mock(TemplateService.class), mock(ContextCacheService.class),
            mock(com.ablueforce.cortexce.event.MemoryRefineEventPublisher.class),
            mock(QualityScorer.class), jobs, resultPersistence,
            mock(ConversationWriteService.class));

        UUID jobId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        PendingMessageEntity job = new PendingMessageEntity();
        job.setId(jobId);
        job.setSessionDbId(conversationId);
        job.setMessageType(SummaryJobService.TYPE_SUMMARIZE);
        job.setStatus(SummaryJobService.STATUS_PENDING);
        when(jobs.findJob(jobId)).thenReturn(Optional.of(job));
        when(jobs.claimForProcessing(org.mockito.ArgumentMatchers.eq(jobId),
            org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.of(job));
        when(pendingMessages.countOpenObservationJobs(conversationId)).thenReturn(1L);

        service.processSummaryJob(jobId);

        verify(jobs).deferUntilObservationQueueDrains(jobId, conversationId);
        verify(observations, never()).findByConversationIdOrderByCreatedAtEpochAsc(conversationId);
        verify(llm, never()).chatCompletion(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString());
    }
}
