package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Timeline service for anchor-based context queries.
 * P0: Extracted from ViewerController to keep REST layer thin.
 * This service is shared by REST Controller and MCP Handler layers.
 *
 * E.5 Fix: Provides both ResponseEntity (for REST) and Map (for MCP) return types
 * to eliminate code duplication between Controller and MCP layers.
 */
@Service
public class TimelineService {

    private static final Logger log = LoggerFactory.getLogger(TimelineService.class);

    private final ObservationRepository observationRepository;
    private final EmbeddingService embeddingService;
    private final SearchService searchService;

    public TimelineService(ObservationRepository observationRepository,
                           EmbeddingService embeddingService,
                           SearchService searchService) {
        this.observationRepository = observationRepository;
        this.embeddingService = embeddingService;
        this.searchService = searchService;
    }

    /**
     * Get timeline context around an anchor point (REST endpoint wrapper).
     * Delegates to getTimelineMap() and wraps result in ResponseEntity.
     *
     * @param project The project path
     * @param anchorId The anchor observation ID (optional)
     * @param query Query to find anchor by semantic search (optional)
     * @param depthBefore Number of items before anchor (default: 5)
     * @param depthAfter Number of items after anchor (default: 5)
     * @return ResponseEntity with timeline observations or error
     */
    public ResponseEntity<?> getTimelineByAnchor(
            String project,
            String anchorId,
            String query,
            Integer depthBefore,
            Integer depthAfter) {

        Map<String, Object> result = getTimelineMap(project, anchorId, query, depthBefore, depthAfter);

        // Check if it's an error response
        if (result.containsKey("error") && !"Anchor observation not found".equals(result.get("error"))) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Get timeline context around an anchor point (direct Map return for MCP tools).
     * E.5 Fix: Eliminates code duplication by providing a single implementation
     * that both REST Controller and MCP tools can use.
     *
     * @param project The project path
     * @param anchorId The anchor observation ID (optional)
     * @param query Query to find anchor by semantic search (optional)
     * @param depthBefore Number of items before anchor (default: 5)
     * @param depthAfter Number of items after anchor (default: 5)
     * @return Map with timeline observations or error
     */
    public Map<String, Object> getTimelineMap(
            String project,
            String anchorId,
            String query,
            Integer depthBefore,
            Integer depthAfter) {

        int before = depthBefore != null ? depthBefore : 5;
        int after = depthAfter != null ? depthAfter : 5;

        // If query is provided, search for the best anchor
        UUID anchorUuid = null;
        if (anchorId != null) {
            try {
                anchorUuid = UUID.fromString(anchorId);
            } catch (IllegalArgumentException e) {
                return Map.of("error", "Invalid anchor ID format", "observations", List.of());
            }
        } else if (query != null && !query.isBlank()) {
            // Search for the best matching observation to use as anchor
            anchorUuid = findAnchorByQuery(project, query);
        }

        if (anchorUuid == null) {
            return Map.of("error", "No anchor found", "observations", List.of());
        }

        // Get the anchor observation
        ObservationEntity anchor = observationRepository.findById(anchorUuid).orElse(null);
        if (anchor == null) {
            return Map.of("error", "Anchor observation not found", "observations", List.of());
        }

        // Query observations and find anchor position
        List<ObservationEntity> allObs = observationRepository.findByProjectPathOrderByCreatedAtDesc(project);
        int anchorIndex = findAnchorIndex(allObs, anchorUuid);

        if (anchorIndex < 0) {
            return Map.of("observations", List.of(), "anchor_id", anchorId != null ? anchorId : "");
        }

        // Extract window around anchor
        List<ObservationEntity> window = extractWindow(allObs, anchorIndex, before, after);

        Map<String, Object> response = new HashMap<>();
        response.put("observations", window);
        response.put("anchor_id", anchorId != null ? anchorId : anchorUuid.toString());
        response.put("anchor_index", anchorIndex);
        response.put("count", window.size());
        return response;
    }

    /**
     * Find anchor observation by semantic search query.
     *
     * @param project The project path
     * @param query The search query
     * @return UUID of the best matching observation, or null if not found
     */
    private UUID findAnchorByQuery(String project, String query) {
        try {
            float[] queryVector = embeddingService.embed(query);
            SearchService.SearchResult result = searchService.search(
                new SearchService.SearchRequest(project, query, queryVector, null, null, null, null, null, 1, 0)
            );
            if (!result.observations().isEmpty()) {
                return result.observations().get(0).getId();
            }
        } catch (Exception e) {
            log.warn("Failed to find anchor by query: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Find the index of an anchor observation in a list.
     *
     * @param observations List of observations (sorted)
     * @param anchorUuid The anchor UUID to find
     * @return Index of the anchor, or -1 if not found
     */
    private int findAnchorIndex(List<ObservationEntity> observations, UUID anchorUuid) {
        for (int i = 0; i < observations.size(); i++) {
            if (observations.get(i).getId().equals(anchorUuid)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Extract a window of observations around an anchor index.
     *
     * @param observations Full list of observations
     * @param anchorIndex Index of the anchor
     * @param before Number of items before anchor
     * @param after Number of items after anchor
     * @return Sublist window around the anchor
     */
    private List<ObservationEntity> extractWindow(
            List<ObservationEntity> observations,
            int anchorIndex,
            int before,
            int after) {
        int startIdx = Math.max(0, anchorIndex - before);
        int endIdx = Math.min(observations.size(), anchorIndex + after + 1);
        return observations.subList(startIdx, endIdx);
    }
}
