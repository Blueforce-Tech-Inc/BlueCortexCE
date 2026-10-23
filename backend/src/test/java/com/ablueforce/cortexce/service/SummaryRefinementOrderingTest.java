package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.entity.SummaryEntity;
import com.ablueforce.cortexce.event.MemoryRefineEventPublisher;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummaryRefinementOrderingTest {

    @Test
    void refinementWakeupIsPublishedAfterSummaryJobFinalizes() {
        SessionManagementService sessions = mock(SessionManagementService.class);
        ObservationRepository observations = mock(ObservationRepository.class);
        PendingMessageRepository pendingMessages = mock(PendingMessageRepository.class);
        SummaryRepository summaries = mock(SummaryRepository.class);
        MemoryRefineEventPublisher refinementEvents = mock(MemoryRefineEventPublisher.class);
        QualityScorer qualityScorer = mock(QualityScorer.class);
        SummaryJobService jobs = mock(SummaryJobService.class);
        SummaryResultPersistenceService resultPersistence = mock(SummaryResultPersistenceService.class);
        ConversationWriteService conversationWrites = mock(ConversationWriteService.class);
        LlmService llm = mock(LlmService.class);

        UUID conversationId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        SessionEntity session = new SessionEntity();
        session.setId(conversationId);
        session.setPlatformSource("claude");
        session.setContentSessionId("same-id");
        session.setProjectPath("/repo");
        session.setStatus("summarizing");
        PendingMessageEntity job = new PendingMessageEntity();
        job.setId(jobId);
        job.setSessionDbId(conversationId);
        job.setMessageType(SummaryJobService.TYPE_SUMMARIZE);
        job.setStatus(SummaryJobService.STATUS_PENDING);
        job.setSummaryInputSnapshot(Map.of("system_prompt", "system", "user_prompt", "user"));
        ObservationEntity observation = new ObservationEntity();
        observation.setId(UUID.randomUUID());
        observation.setConversationId(conversationId);
        observation.setPromptNumber(1);
        observation.setContent("observation content");
        observation.setType("discovery");
        observation.setTitle("title");
        SummaryEntity savedSummary = new SummaryEntity();
        savedSummary.setId(UUID.randomUUID());

        when(jobs.findJob(jobId)).thenReturn(Optional.of(job));
        when(jobs.claimForProcessing(eq(jobId), anyLong())).thenReturn(Optional.of(job));
        when(pendingMessages.countOpenObservationJobs(conversationId)).thenReturn(0L);
        when(sessions.findById(conversationId)).thenReturn(Optional.of(session));
        when(observations.findByConversationIdOrderByCreatedAtEpochAsc(conversationId))
            .thenReturn(List.of(observation));
        when(jobs.persistSnapshot(eq(jobId), eq(conversationId), anyMap(), anyString())).thenReturn(job);
        when(llm.chatCompletion("system", "user"))
            .thenReturn("<summary><request>r</request><learned>l</learned><completed>c</completed></summary>");
        when(resultPersistence.persist(eq(jobId), eq(conversationId), any(SummaryEntity.class)))
            .thenReturn(savedSummary);
        when(qualityScorer.isLlmAvailable()).thenReturn(false);
        when(qualityScorer.estimateQuality(any(QualityScorer.FeedbackType.class),
            anyString(), anyString(), anyInt())).thenReturn(0.8f);

        SummaryGenerationService service = new SummaryGenerationService(
            sessions, observations, pendingMessages, summaries, mock(SSEBroadcaster.class), llm,
            mock(TemplateService.class), mock(ContextCacheService.class), refinementEvents,
            qualityScorer, jobs, resultPersistence, conversationWrites);

        service.processSummaryJob(jobId);

        InOrder order = inOrder(jobs, refinementEvents);
        order.verify(jobs).finalizeResult(jobId, conversationId, true);
        order.verify(refinementEvents).publishRefineEvent("/repo", "same-id");
        verify(conversationWrites).saveSummaryQualityFeedback(conversationId, List.of(observation));
    }
}
