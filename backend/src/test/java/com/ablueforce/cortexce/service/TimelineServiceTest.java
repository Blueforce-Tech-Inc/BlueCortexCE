package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TimelineService.
 * Covers: anchor-based timeline retrieval, query-based anchor search,
 * window extraction, edge cases (anchor not found, invalid UUID).
 * Uses Mockito for ObservationRepository, EmbeddingService, SearchService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimelineServiceTest {

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private SearchService searchService;

    private TimelineService timelineService;

    private ObservationEntity obs1, obs2, obs3;

    @BeforeEach
    void setUp() {
        timelineService = new TimelineService(observationRepository, embeddingService, searchService);

        obs1 = makeObs(1, 1000L);  // oldest
        obs2 = makeObs(2, 2000L);  // middle
        obs3 = makeObs(3, 3000L);  // newest
    }

    // ===== getTimelineMap =====

    @Test
    void getTimelineMap_withValidAnchor_returnsWindow() {
        // Given: anchor at index 1, depth 1 before/after
        when(observationRepository.findByProjectPathOrderByCreatedAtDesc(
                eq("/tmp/test"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(obs3, obs2, obs1)));
        when(observationRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000002")))
            .thenReturn(Optional.of(obs2));

        // When
        Map<String, Object> result = timelineService.getTimelineMap(
            "/tmp/test", "00000000-0000-0000-0000-000000000002", null, 1, 1);

        // Then
        assertThat(result).containsKey("observations");
        assertThat(result).containsKey("anchor_index");
        assertThat(result.get("count")).isEqualTo(3); // obs1, obs2, obs3
        assertThat(result.get("anchor_id")).isEqualTo("00000000-0000-0000-0000-000000000002");
    }

    @Test
    void getTimelineMap_withDepthZero_returnsOnlyAnchor() {
        // Given: anchor at index 1, depth 0
        when(observationRepository.findByProjectPathOrderByCreatedAtDesc(
                eq("/tmp/test"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(obs3, obs2, obs1)));
        when(observationRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000002")))
            .thenReturn(Optional.of(obs2));

        // When
        Map<String, Object> result = timelineService.getTimelineMap(
            "/tmp/test", "00000000-0000-0000-0000-000000000002", null, 0, 0);

        // Then
        @SuppressWarnings("unchecked")
        List<ObservationEntity> window = (List<ObservationEntity>) result.get("observations");
        assertThat(window).hasSize(1);
        assertThat(window.get(0).getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    }

    @Test
    void getTimelineMap_anchorAtStart_respectsWindowBoundaries() {
        // Given: anchor at index 0 (newest), depth 2 before
        when(observationRepository.findByProjectPathOrderByCreatedAtDesc(
                eq("/tmp/test"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(obs3, obs2, obs1)));
        when(observationRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000003")))
            .thenReturn(Optional.of(obs3));

        // When: ask for 2 before, 1 after
        Map<String, Object> result = timelineService.getTimelineMap(
            "/tmp/test", "00000000-0000-0000-0000-000000000003", null, 2, 1);

        // Then: start is clamped to 0, so we get obs3, obs2 (not obs1 as "before")
        @SuppressWarnings("unchecked")
        List<ObservationEntity> window = (List<ObservationEntity>) result.get("observations");
        assertThat(window).hasSize(2); // obs3, obs2 (only 1 after available due to clamping)
        assertThat(result.get("anchor_index")).isEqualTo(0);
    }

    @Test
    void getTimelineMap_anchorAtEnd_respectsWindowBoundaries() {
        // Given: anchor at last index
        when(observationRepository.findByProjectPathOrderByCreatedAtDesc(
                eq("/tmp/test"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(obs3, obs2, obs1)));
        when(observationRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001")))
            .thenReturn(Optional.of(obs1));

        // When: ask for 2 before, 1 after
        Map<String, Object> result = timelineService.getTimelineMap(
            "/tmp/test", "00000000-0000-0000-0000-000000000001", null, 2, 1);

        // Then: end is clamped to size, so we get obs3, obs2, obs1
        @SuppressWarnings("unchecked")
        List<ObservationEntity> window = (List<ObservationEntity>) result.get("observations");
        assertThat(window).hasSize(3); // only obs1 available as "after"
    }

    @Test
    void getTimelineMap_invalidAnchorUuid_returnsError() {
        // Given: invalid UUID string
        // When
        Map<String, Object> result = timelineService.getTimelineMap(
            "/tmp/test", "not-a-uuid", null, 5, 5);

        // Then
        assertThat(result.get("error")).isEqualTo("Invalid anchor ID format");
        @SuppressWarnings("unchecked")
        List<?> obs = (List<?>) result.get("observations");
        assertThat(obs).isEmpty();
    }

    @Test
    void getTimelineMap_anchorNotFoundInRepo_returnsAnchorNotFoundError() {
        // Given: valid UUID but not in DB
        String uuid = "00000000-0000-0000-0000-000000000001";
        when(observationRepository.findByProjectPathOrderByCreatedAtDesc(
                eq("/tmp/test"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(obs3, obs2, obs1)));
        when(observationRepository.findById(UUID.fromString(uuid)))
            .thenReturn(Optional.empty());

        // When
        Map<String, Object> result = timelineService.getTimelineMap(
            "/tmp/test", uuid, null, 5, 5);

        // Then
        assertThat(result.get("error")).isEqualTo("Anchor observation not found");
    }

    @Test
    void getTimelineMap_anchorNotInList_returnsEmptyObservations() {
        // Given: anchor exists in DB but not in the paginated list
        when(observationRepository.findByProjectPathOrderByCreatedAtDesc(
                eq("/tmp/test"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(obs2, obs3))); // obs1 not in this list
        when(observationRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001")))
            .thenReturn(Optional.of(obs1));

        // When
        Map<String, Object> result = timelineService.getTimelineMap(
            "/tmp/test", "00000000-0000-0000-0000-000000000001", null, 5, 5);

        // Then
        @SuppressWarnings("unchecked")
        List<?> obs = (List<?>) result.get("observations");
        assertThat(obs).isEmpty();
        assertThat(result.get("anchor_id")).isEqualTo("00000000-0000-0000-0000-000000000001");
    }

    @Test
    void getTimelineMap_defaultDepthValues() {
        // Given
        when(observationRepository.findByProjectPathOrderByCreatedAtDesc(
                eq("/tmp/test"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(obs3, obs2, obs1)));
        when(observationRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000002")))
            .thenReturn(Optional.of(obs2));

        // When: null depth defaults to 5
        Map<String, Object> result = timelineService.getTimelineMap(
            "/tmp/test", "00000000-0000-0000-0000-000000000002", null, null, null);

        // Then: should not throw, window size should reflect default depth 5
        assertThat(result).containsKey("observations");
    }

    // ===== query-based anchor =====

    @Test
    void getTimelineMap_withQuery_findsAnchorViaSemanticSearch() throws Exception {
        // Given: no anchorId, but query provided
        when(observationRepository.findByProjectPathOrderByCreatedAtDesc(
                eq("/tmp/test"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(obs2)));
        when(observationRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000002")))
            .thenReturn(Optional.of(obs2));
        when(embeddingService.embed("find me"))
            .thenReturn(new float[]{0.1f, 0.2f});
        when(searchService.search(any(SearchService.SearchRequest.class)))
            .thenReturn(new SearchService.SearchResult(List.of(obs2), "vector", false));

        // When
        Map<String, Object> result = timelineService.getTimelineMap(
            "/tmp/test", null, "find me", 5, 5);

        // Then: should have used semantic search to find anchor
        verify(embeddingService).embed("find me");
        assertThat(result).containsKey("observations");
    }

    @Test
    void getTimelineMap_querySearchFindsNothing_returnsNoAnchorFound() throws Exception {
        // Given: query search returns empty
        when(embeddingService.embed("nonexistent"))
            .thenReturn(new float[]{0.1f});

        SearchService.SearchResult emptyResult =
            new SearchService.SearchResult(List.of(), "vector", false);
        when(searchService.search(any(SearchService.SearchRequest.class)))
            .thenReturn(emptyResult);

        // When
        Map<String, Object> result = timelineService.getTimelineMap(
            "/tmp/test", null, "nonexistent", 5, 5);

        // Then
        assertThat(result.get("error")).isEqualTo("No anchor found");
    }

    @Test
    void getTimelineMap_embedFailure_returnsNoAnchorFound() throws Exception {
        // Given: embedding service throws
        when(embeddingService.embed("broken query"))
            .thenThrow(new RuntimeException("Model not available"));

        // When
        Map<String, Object> result = timelineService.getTimelineMap(
            "/tmp/test", null, "broken query", 5, 5);

        // Then
        assertThat(result.get("error")).isEqualTo("No anchor found");
    }

    // ===== Helper =====

    private ObservationEntity makeObs(int index, long epoch) {
        ObservationEntity obs = new ObservationEntity();
        obs.setId(UUID.fromString(String.format("00000000-0000-0000-0000-0000000000%02d", index)));
        obs.setProjectPath("/tmp/test");
        obs.setType("test");
        obs.setTitle("Observation " + index);
        obs.setContent("Content " + index);
        obs.setSource("test");
        obs.setCreatedAt(OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), java.time.ZoneOffset.UTC));
        obs.setCreatedAtEpoch(epoch);
        return obs;
    }
}
