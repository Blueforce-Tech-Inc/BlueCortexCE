package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-level contract of the repair sweep, with the database replaced by a repository double.
 *
 * <p>These branches decide whether the sweep spends a provider call and what it is allowed
 * to write, so they are pinned independently of the PostgreSQL integration test.</p>
 */
class EmbeddingBackfillServiceTest {

    private ObservationRepository repository;
    private EmbeddingService embeddingService;
    private EmbeddingBackfillService service;

    @BeforeEach
    void setUp() {
        repository = mock(ObservationRepository.class);
        embeddingService = mock(EmbeddingService.class);
        service = new EmbeddingBackfillService(repository, embeddingService, true);
    }

    @Test
    @DisplayName("a row that no longer needs a vector is not sent to the provider")
    void skipsProviderWhenRowIsAlreadyDone() {
        ObservationEntity observation = observation();

        when(repository.countEmbeddingMissingById(observation.getId())).thenReturn(0L);

        assertThat(service.repair(observation)).isFalse();
        verify(embeddingService, never()).embed(anyString());
        verify(repository, never()).applyEmbedding1024IfMissing(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("a blank row is marked skipped without a provider call")
    void blankRowIsMarkedSkipped() {
        ObservationEntity observation = observation();
        observation.setTitle(" ");
        observation.setContent(null);
        observation.setFacts(List.of());

        when(repository.countEmbeddingMissingById(observation.getId())).thenReturn(1L);
        when(repository.markEmbeddingOutcomeIfMissing(
            observation.getId(), EmbeddingService.STATE_SKIPPED, null)).thenReturn(1);

        assertThat(service.repair(observation)).isFalse();
        assertThat(observation.getEmbeddingState()).isEqualTo(EmbeddingService.STATE_SKIPPED);
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    @DisplayName("an unsupported dimension is recorded instead of saved as a silent success")
    void unsupportedDimensionIsRecordedAsFailure() {
        ObservationEntity observation = observation();
        float[] unsupported = new float[7];

        when(repository.countEmbeddingMissingById(observation.getId())).thenReturn(1L);
        when(embeddingService.embed(anyString())).thenReturn(unsupported);
        when(embeddingService.applyVector(observation, unsupported)).thenReturn(false);
        when(repository.markEmbeddingOutcomeIfMissing(
            observation.getId(), EmbeddingService.STATE_FAILED, "unsupported-dimension"))
            .thenReturn(1);

        assertThat(service.repair(observation)).isFalse();
        assertThat(observation.getEmbeddingState()).isEqualTo(EmbeddingService.STATE_FAILED);
        assertThat(observation.getEmbeddingError()).isEqualTo("unsupported-dimension");
        verify(repository, never()).applyEmbedding768IfMissing(any(), anyString(), anyString());
        verify(repository, never()).applyEmbedding1024IfMissing(any(), anyString(), anyString());
        verify(repository, never()).applyEmbedding1536IfMissing(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("a provider failure records only the classification")
    void providerFailureRecordsClassification() {
        ObservationEntity observation = observation();

        when(repository.countEmbeddingMissingById(observation.getId())).thenReturn(1L);
        when(embeddingService.embed(anyString()))
            .thenThrow(new IllegalStateException("503 Service Unavailable secret body"));
        when(repository.markEmbeddingOutcomeIfMissing(
            observation.getId(), EmbeddingService.STATE_FAILED, "IllegalStateException"))
            .thenReturn(1);

        assertThat(service.repair(observation)).isFalse();
        assertThat(observation.getEmbeddingError()).isEqualTo("IllegalStateException");
        assertThat(observation.getEmbeddingError()).doesNotContain("secret body");
    }

    @Test
    @DisplayName("losing the conditional write returns false instead of claiming a repair")
    void lostClaimReturnsFalse() {
        ObservationEntity observation = observation();
        float[] vector = new float[1024];

        when(repository.countEmbeddingMissingById(observation.getId())).thenReturn(1L);
        when(embeddingService.embed(anyString())).thenReturn(vector);
        when(embeddingService.applyVector(eq(observation), any())).thenAnswer(invocation -> {
            observation.setEmbeddingModelId("stub-model");
            return true;
        });
        when(repository.applyEmbedding1024IfMissing(
            eq(observation.getId()), anyString(), anyString())).thenReturn(0);

        assertThat(service.repair(observation)).isFalse();
    }

    @Test
    @DisplayName("a successful claim writes the 1024-dim column and reports the repair")
    void successfulClaimWritesTheMatchingColumn() {
        ObservationEntity observation = observation();
        float[] vector = new float[1024];
        vector[3] = 0.25f;

        when(repository.countEmbeddingMissingById(observation.getId())).thenReturn(1L);
        when(embeddingService.embed(anyString())).thenReturn(vector);
        when(embeddingService.applyVector(eq(observation), any())).thenAnswer(invocation -> {
            observation.setEmbeddingModelId("stub-model");
            return true;
        });
        when(repository.applyEmbedding1024IfMissing(
            eq(observation.getId()), anyString(), anyString())).thenReturn(1);

        assertThat(service.repair(observation)).isTrue();
        verify(repository).applyEmbedding1024IfMissing(
            eq(observation.getId()), eq(EmbeddingService.toVectorLiteral(vector)),
            eq("stub-model"));
    }

    @Test
    @DisplayName("an unsaved row never reaches the database or the provider")
    void unsavedRowIsIgnored() {
        ObservationEntity observation = observation();
        observation.setId(null);

        assertThat(service.repair(observation)).isFalse();
        verifyNoInteractions(repository, embeddingService);
    }

    private ObservationEntity observation() {
        ObservationEntity observation = new ObservationEntity();
        observation.setId(UUID.randomUUID());
        observation.setTitle("pagination offset regression");
        observation.setContent("Query returned the same page twice.");
        observation.setFacts(List.of("Offset is applied before limit."));
        observation.setEmbeddingState(EmbeddingService.STATE_FAILED);
        return observation;
    }
}
