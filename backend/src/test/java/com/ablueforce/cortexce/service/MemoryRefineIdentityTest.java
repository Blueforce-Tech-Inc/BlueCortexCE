package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryRefineIdentityTest {

    @Test
    void sameExternalIdOnDifferentPlatformsIsRewrittenNotMerged() {
        ObservationRepository observations = mock(ObservationRepository.class);
        ConversationWriteService writes = mock(ConversationWriteService.class);
        LlmService llm = mock(LlmService.class);
        MemoryRefineService service = new MemoryRefineService(observations, writes,
            mock(QualityScorer.class), llm, true, null);

        ObservationEntity claude = observation(UUID.randomUUID(), "claude");
        ObservationEntity codex = observation(UUID.randomUUID(), "codex");
        when(observations.findLowQualityObservations(eq("/repo"), anyFloat(), anyInt()))
            .thenReturn(List.of());
        when(observations.findStaleObservations(eq("/repo"), any(OffsetDateTime.class), anyFloat(), anyInt()))
            .thenReturn(List.of());
        when(observations.findOverdueForRefine(eq("/repo"), any(OffsetDateTime.class), anyInt()))
            .thenReturn(List.of(claude, codex));
        when(llm.chatCompletion(anyString(), anyString())).thenReturn("rewritten");

        service.refineMemory("/repo");

        verify(writes).persistRefinedObservation(eq(claude.getId()), eq(claude.getConversationId()),
            eq(claude.getTitle()), eq(claude.getContent()), eq("rewritten"), any(OffsetDateTime.class));
        verify(writes).persistRefinedObservation(eq(codex.getId()), eq(codex.getConversationId()),
            eq(codex.getTitle()), eq(codex.getContent()), eq("rewritten"), any(OffsetDateTime.class));
        verify(writes, never()).mergeRefinedObservations(any(), any(), anyString(), any(OffsetDateTime.class));
    }

    private ObservationEntity observation(UUID conversationId, String platform) {
        ObservationEntity observation = new ObservationEntity();
        observation.setId(UUID.randomUUID());
        observation.setConversationId(conversationId);
        observation.setPlatformSource(platform);
        observation.setContentSessionId("shared-external-id");
        observation.setProjectPath("/repo");
        observation.setTitle(platform + " title");
        observation.setContent(platform + " content");
        observation.setQualityScore(0.9f);
        return observation;
    }
}
