package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

/**
 * Hybrid search service.
 * <p>
 * Strategy selection tree (per cookbook):
 * 1. No query → PostgreSQL filter search (type/concepts/files/dateRange via tsvector)
 * 2. Has query + embeddings available → pgvector semantic search
 * 3. pgvector fails → fallback to tsvector full-text search
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final int DEFAULT_LIMIT = 20;

    @Autowired
    private ObservationRepository observationRepository;

    /**
     * Main search entry point.
     */
    public SearchResult search(SearchRequest request) {
        String project = request.project();
        String query = request.query();
        // P2: Handle Integer.MIN_VALUE edge case with robust limit handling
        int rawLimit = request.limit();
        int limit = rawLimit <= 0 ? DEFAULT_LIMIT : Math.max(1, rawLimit);

        // PATH 1: Filter-only (no query text)
        if (query == null || query.isBlank()) {
            log.debug("Filter-only search for project={}", project);
            return filterSearch(request, limit);
        }

        // PATH 2: Semantic search with pgvector (dimension-aware)
        if (request.queryVector() != null) {
            int dim = request.queryVector().length;
            log.debug("Semantic search with pgvector for project={}, dim={}", project, dim);
            long minEpoch = Instant.now().minus(90, ChronoUnit.DAYS).toEpochMilli();
            String vectorStr = vectorToString(request.queryVector());

            if (com.ablueforce.cortexce.util.VectorValidator.isValidVector(vectorStr)) {
                try {
                    log.debug("Using hybrid search (pgvector + tsvector) for project={}", project);
                    List<ObservationEntity> results = observationRepository.hybridSearch(
                        project, query, vectorStr, minEpoch, limit
                    );
                    return new SearchResult(results, "hybrid", false);
                } catch (IllegalArgumentException e) {
                    log.warn("Hybrid search rejected for project={}, falling back to tsvector: {}",
                        project, e.getMessage());
                } catch (Exception e) {
                    boolean isRetryable = isRetryableException(e);
                    if (isRetryable) {
                        log.warn("pgvector transient error for project={}, will retry: {}", project, e.getMessage());
                        throw e;
                    }
                    log.warn("pgvector search failed for project={}, falling back to tsvector: {}",
                        project, e.getMessage());
                }
            } else {
                log.debug("Query vector failed validation, falling back to tsvector for project={}", project);
            }
        }

        // PATH 3: Full-text search fallback
        log.debug("Full-text search fallback for project={}", project);
        try {
            List<ObservationEntity> results = observationRepository.fullTextSearch(project, query, limit);
            return new SearchResult(results, "tsvector", query != null && request.queryVector() != null);
        } catch (Exception e) {
            // P1: Use WARN level - fallback failure is expected behavior but worth noting
            log.warn("Full-text search also failed for project={}: {}", project, e.getMessage());
            return new SearchResult(Collections.emptyList(), "none", true);
        }
    }

    /**
     * P1: Classify exception for retry decision (network/timeouts vs. business/DB errors).
     */
    private boolean isRetryableException(Exception e) {
        if (e instanceof java.net.SocketTimeoutException ||
            e instanceof java.net.ConnectException ||
            e instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        // Database connection errors
        if (e instanceof org.springframework.jdbc.CannotGetJdbcConnectionException) {
            return true;
        }
        return false;
    }

    /**
     * Filter search with composable AND conditions.
     * All non-null/non-blank filters are combined: type AND concept AND source AND dateRange.
     * Uses database-level filtering for better performance.
     */
    private SearchResult filterSearch(SearchRequest request, int limit) {
        String project = request.project();

        boolean hasAnyFilter = (request.type() != null && !request.type().isBlank())
            || (request.source() != null && !request.source().isBlank())
            || (request.concept() != null && !request.concept().isBlank())
            || request.startEpoch() != null || request.endEpoch() != null;

        if (hasAnyFilter) {
            // Use database-level composable AND filter
            List<ObservationEntity> results = observationRepository.findByAllFilters(
                project,
                blankToNull(request.type()),
                blankToNull(request.source()),
                blankToNull(request.concept()),
                request.startEpoch(),
                request.endEpoch(),
                limit
            );
            return new SearchResult(results, "filter", false);
        }

        // Default: recent observations
        List<ObservationEntity> results = observationRepository.findByProjectLimited(project, limit);
        return new SearchResult(results, "recent", false);
    }

    /**
     * Convert float array to PostgreSQL vector string format: [0.1,0.2,...].
     */
    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            // P3: Format with 4 decimal places for readability and storage efficiency
            sb.append(String.format("%.4f", vector[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    /** Convert blank strings to null for composable AND filter. */
    private static String blankToNull(String s) {
        return (s != null && !s.isBlank()) ? s : null;
    }

    /**
     * Search request parameters.
     */
    public record SearchRequest(
        String project,
        String query,
        float[] queryVector,
        String type,
        String concept,
        String source,
        Long startEpoch,
        Long endEpoch,
        int limit
    ) {}

    /**
     * Search result with metadata.
     */
    public record SearchResult(
        List<ObservationEntity> observations,
        String strategy,
        boolean fellBack
    ) {}

}
