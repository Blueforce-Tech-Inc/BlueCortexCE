package com.ablueforce.cortexce.ai.retrieval;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.Experience;
import com.ablueforce.cortexce.client.dto.QualityDistribution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultMemoryRetrievalServiceTest {

    @Mock
    private CortexMemClient client;

    private DefaultMemoryRetrievalService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new DefaultMemoryRetrievalService(client, 4);
    }

    @Test
    void retrieveExperiences_delegatesToClientWithCorrectCount() {
        var exp = new Experience("id", "task", "strat", "out", "", 0.9f, OffsetDateTime.now());
        when(client.retrieveExperiences(any())).thenReturn(List.of(exp));

        List<Experience> result = service.retrieveExperiences("fix bug", "/proj", 6);

        assertThat(result).containsExactly(exp);
        ArgumentCaptor<com.ablueforce.cortexce.client.dto.ExperienceRequest> cap =
            ArgumentCaptor.forClass(com.ablueforce.cortexce.client.dto.ExperienceRequest.class);
        verify(client).retrieveExperiences(cap.capture());
        assertThat(cap.getValue().task()).isEqualTo("fix bug");
        assertThat(cap.getValue().project()).isEqualTo("/proj");
        assertThat(cap.getValue().count()).isEqualTo(6);
    }

    @Test
    void retrieveExperiences_usesDefaultCountWhenZero() {
        when(client.retrieveExperiences(any())).thenReturn(List.of());
        service.retrieveExperiences("x", "/p", 0);
        ArgumentCaptor<com.ablueforce.cortexce.client.dto.ExperienceRequest> cap =
            ArgumentCaptor.forClass(com.ablueforce.cortexce.client.dto.ExperienceRequest.class);
        verify(client).retrieveExperiences(cap.capture());
        assertThat(cap.getValue().count()).isEqualTo(4);
    }

    @Test
    void buildICLPrompt_delegatesAndReturnsPrompt() {
        when(client.buildICLPrompt(any())).thenReturn(
            new com.ablueforce.cortexce.client.dto.ICLPromptResult("Relevant...", 3));
        String result = service.buildICLPrompt("task", "/proj");
        assertThat(result).isEqualTo("Relevant...");
    }

    @Test
    void getQualityDistribution_delegatesToClient() {
        var dist = new QualityDistribution("/p", 10, 5, 2, 1);
        when(client.getQualityDistribution("/p")).thenReturn(dist);
        assertThat(service.getQualityDistribution("/p")).isEqualTo(dist);
    }
}
