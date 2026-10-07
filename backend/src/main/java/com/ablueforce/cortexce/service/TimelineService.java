package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

        // Clamp both depths to >= 0 before they reach the window arithmetic below.
        //
        // The window is `subList(max(0, anchorIndex - before), min(size, anchorIndex + after + 1))`,
        // and the same two depths also size the *fetch*: `windowSize = (before + after) * 2 + 1`,
        // capped at 500, is what PageRequest is built from.
        //
        // A negative depth therefore fails one step earlier than the subList arithmetic would
        // suggest. With before = -1 and after = -1, windowSize is -3, so `PageRequest.of(0, -3)`
        // throws `IllegalArgumentException: Page size must not be less than one!` — and that
        // happens *before* extractWindow is ever reached. The subList inversion is real
        // (`subList(1, 0)` does throw `fromIndex(1) > toIndex(0)`), but it is unreachable here:
        // any (before, after) that could invert the range sums to less than 0, which makes the
        // page size less than 1, which throws first. Either way it was an unhandled 500 on what
        // is plainly a client input error. Both timeline entry points (GET /api/context/timeline
        // and GET /api/timeline) funnel through this method via getTimelineByAnchor, so flooring
        // here covers both; the MCP `timeline` tool calls this method directly and is covered too.
        //
        // Floored at 0, not 1, because 0 already has a working meaning on this endpoint, and
        // because flooring at 1 would *change* it. What depth 0 actually returns depends on where
        // the anchor sits: the fetch is only (0 + 0) * 2 + 1 = 1 row wide, so the anchor is in that
        // fetch only when it is the project's newest observation. Measured live on one project
        // (1362 observations, anchor at each of four positions in the newest-first list):
        // newest -> 1 observation, 2nd -> 0, 6th -> 0, 11th -> 0. Flooring at 0 preserves all four;
        // flooring at 1 would turn the three empty ones into 3-observation windows, i.e. a
        // behaviour change rather than a crash fix. Values of 1, 10 and 5000 are untouched, and
        // only the two negative inputs change.
        int before = depthBefore != null ? Math.max(0, depthBefore) : 5;
        int after = depthAfter != null ? Math.max(0, depthAfter) : 5;

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

        // Query observations using a bounded window around the anchor to prevent OOM
        final int windowSize = (before + after) * 2 + 1;
        final int maxObs = Math.min(windowSize, 500);
        Pageable limitOne = PageRequest.of(0, maxObs);
        List<ObservationEntity> allObs = observationRepository.findByProjectPathOrderByCreatedAtEpochDesc(project, limitOne).getContent();
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
                new SearchService.SearchRequest(project, query, queryVector, null, null, null, null, null, 1, 0, null)
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
