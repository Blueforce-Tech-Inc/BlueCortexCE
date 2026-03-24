package com.ablueforce.cortexce.client;

import com.ablueforce.cortexce.client.config.CortexMemProperties;
import com.ablueforce.cortexce.client.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * REST-based implementation of {@link CortexMemClient}.
 * <p>
 * Uses Spring 6's {@link RestClient} for synchronous HTTP calls.
 * All capture operations are fire-and-forget (failures logged, not thrown)
 * to avoid blocking the caller's AI pipeline.
 */
public class CortexMemClientImpl implements CortexMemClient {

    private static final Logger log = LoggerFactory.getLogger(CortexMemClientImpl.class);

    private final RestClient restClient;
    private final CortexMemProperties properties;
    private final int maxRetries;
    private final Duration retryBackoff;

    public CortexMemClientImpl(CortexMemProperties properties) {
        this(properties, null);
    }

    public CortexMemClientImpl(CortexMemProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.maxRetries = properties.getRetry().getMaxAttempts();
        this.retryBackoff = properties.getRetry().getBackoff();

        if (restClientBuilder == null) {
            restClientBuilder = RestClient.builder();
        }
        this.restClient = restClientBuilder
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();

        log.info("CortexMemClient initialized → {}", properties.getBaseUrl());
    }

    // ==================== Capture ====================

    @Override
    public Map<String, Object> startSession(SessionStartRequest request) {
        try {
            return restClient.post()
                .uri("/api/session/start")
                .body(request.toWireFormat())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to start session: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    @Override
    public void recordObservation(ObservationRequest request) {
        executeWithRetry("recordObservation", () ->
            restClient.post()
                .uri("/api/ingest/tool-use")
                .body(request.toWireFormat())
                .retrieve()
                .toBodilessEntity()
        );
    }

    @Override
    public void recordSessionEnd(SessionEndRequest request) {
        executeWithRetry("recordSessionEnd", () ->
            restClient.post()
                .uri("/api/ingest/session-end")
                .body(request.toWireFormat())
                .retrieve()
                .toBodilessEntity()
        );
    }

    @Override
    public void recordUserPrompt(UserPromptRequest request) {
        executeWithRetry("recordUserPrompt", () ->
            restClient.post()
                .uri("/api/ingest/user-prompt")
                .body(request.toWireFormat())
                .retrieve()
                .toBodilessEntity()
        );
    }

    // ==================== Retrieval ====================

    @Override
    public List<Experience> retrieveExperiences(ExperienceRequest request) {
        try {
            var body = new java.util.HashMap<String, Object>();
            body.put("task", request.task());
            if (request.project() != null && !request.project().isBlank()) {
                body.put("project", request.project());
            }
            body.put("count", request.count() != null ? request.count() : properties.getDefaultExperienceCount());
            if (request.source() != null) {
                body.put("source", request.source());
            }
            if (request.requiredConcepts() != null && !request.requiredConcepts().isEmpty()) {
                body.put("requiredConcepts", request.requiredConcepts());
            }
            if (request.userId() != null) {
                body.put("userId", request.userId());
            }
            List<Experience> result = restClient.post()
                .uri("/api/memory/experiences")
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
            return result != null ? result : List.of();
        } catch (Exception e) {
            log.warn("Failed to retrieve experiences: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public ICLPromptResult buildICLPrompt(ICLPromptRequest request) {
        try {
            var body = new java.util.HashMap<String, Object>();
            body.put("task", request.task());
            if (request.project() != null && !request.project().isBlank()) {
                body.put("project", request.project());
            }
            if (request.maxChars() != null) {
                body.put("maxChars", request.maxChars());
            }
            if (request.userId() != null) {
                body.put("userId", request.userId());
            }
            ICLPromptResult result = restClient.post()
                .uri("/api/memory/icl-prompt")
                .body(body)
                .retrieve()
                .body(ICLPromptResult.class);
            return result != null ? result : new ICLPromptResult("", "0");
        } catch (Exception e) {
            log.warn("Failed to build ICL prompt: {}", e.getMessage());
            return new ICLPromptResult("", "0");
        }
    }

    // ==================== Management ====================

    @Override
    public void triggerRefinement(String projectPath) {
        executeWithRetry("triggerRefinement", () ->
            restClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/memory/refine")
                    .queryParam("project", projectPath)
                    .build())
                .retrieve()
                .toBodilessEntity()
        );
    }

    @Override
    public void submitFeedback(String observationId, String feedbackType, String comment) {
        executeWithRetry("submitFeedback", () ->
            restClient.post()
                .uri("/api/memory/feedback")
                .body(Map.of(
                    "observationId", observationId,
                    "feedbackType", feedbackType,
                    "comment", comment != null ? comment : ""
                ))
                .retrieve()
                .toBodilessEntity()
        );
    }

    @Override
    public QualityDistribution getQualityDistribution(String projectPath) {
        try {
            QualityDistribution result = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/memory/quality-distribution")
                    .queryParam("project", projectPath)
                    .build())
                .retrieve()
                .body(QualityDistribution.class);
            return result != null ? result : new QualityDistribution(projectPath, 0, 0, 0, 0);
        } catch (Exception e) {
            log.warn("Failed to get quality distribution: {}", e.getMessage());
            return new QualityDistribution(projectPath, 0, 0, 0, 0);
        }
    }

    // ==================== Observation Management (V14) ====================

    @Override
    public void updateObservation(String observationId, ObservationUpdate update) {
        executeWithRetry("updateObservation", () ->
            restClient.patch()
                .uri("/api/memory/observations/{id}", observationId)
                .body(update)
                .retrieve()
                .toBodilessEntity()
        );
    }

    @Override
    public void deleteObservation(String observationId) {
        executeWithRetry("deleteObservation", () ->
            restClient.delete()
                .uri("/api/memory/observations/{id}", observationId)
                .retrieve()
                .toBodilessEntity()
        );
    }

    @Override
    public boolean healthCheck() {
        try {
            restClient.get()
                .uri("/api/health")
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    // ==================== Extraction (Phase 3) ====================

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getLatestExtraction(String projectPath, String templateName, String userId) {
        try {
            return restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                        .path("/api/extraction/{template}/latest")
                        .queryParam("projectPath", projectPath);
                    if (userId != null) {
                        builder.queryParam("userId", userId);
                    }
                    return builder.build(templateName);
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to get latest extraction: {}", e.getMessage());
            return Map.of();
        }
    }

    @Override
    public List<Map<String, Object>> getExtractionHistory(String projectPath, String templateName, String userId, int limit) {
        try {
            return restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                        .path("/api/extraction/{template}/history")
                        .queryParam("projectPath", projectPath)
                        .queryParam("limit", limit);
                    if (userId != null) {
                        builder.queryParam("userId", userId);
                    }
                    return builder.build(templateName);
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to get extraction history: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateSessionUserId(String sessionId, String userId) {
        try {
            return restClient.patch()
                .uri("/api/session/{sessionId}/user", sessionId)
                .body(Map.of("user_id", userId))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to update session userId: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    // ==================== Search & List (P0) ====================

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> search(SearchRequest request) {
        try {
            return restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                        .path("/api/search")
                        .queryParam("project", request.project());
                    if (request.query() != null && !request.query().isBlank()) {
                        builder.queryParam("query", request.query());
                    }
                    if (request.type() != null && !request.type().isBlank()) {
                        builder.queryParam("type", request.type());
                    }
                    if (request.concept() != null && !request.concept().isBlank()) {
                        builder.queryParam("concept", request.concept());
                    }
                    if (request.source() != null && !request.source().isBlank()) {
                        builder.queryParam("source", request.source());
                    }
                    if (request.limit() != null) {
                        builder.queryParam("limit", request.limit());
                    }
                    if (request.offset() != null && request.offset() > 0) {
                        builder.queryParam("offset", request.offset());
                    }
                    return builder.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to search: {}", e.getMessage());
            return Map.of("observations", List.of(), "strategy", "none", "fell_back", true, "count", 0);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> listObservations(ObservationsRequest request) {
        try {
            return restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/observations");
                    if (request.project() != null && !request.project().isBlank()) {
                        builder.queryParam("project", request.project());
                    }
                    if (request.offset() != null && request.offset() > 0) {
                        builder.queryParam("offset", request.offset());
                    }
                    if (request.limit() != null) {
                        builder.queryParam("limit", request.limit());
                    }
                    return builder.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to list observations: {}", e.getMessage());
            return Map.of("observations", List.of(), "total", 0);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getObservationsByIds(java.util.List<String> ids) {
        try {
            return restClient.post()
                .uri("/api/observations/batch")
                .body(Map.of("ids", ids))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to get observations by IDs: {}", e.getMessage());
            return Map.of("observations", List.of());
        }
    }

    // ==================== P1 Management APIs ====================

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getVersion() {
        try {
            return restClient.get()
                .uri("/api/version")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to get version: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getProjects() {
        try {
            return restClient.get()
                .uri("/api/projects")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to get projects: {}", e.getMessage());
            return Map.of("projects", List.of());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStats(String projectPath) {
        try {
            return restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/stats");
                    if (projectPath != null && !projectPath.isBlank()) {
                        builder.queryParam("project", projectPath);
                    }
                    return builder.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to get stats: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getModes() {
        try {
            return restClient.get()
                .uri("/api/modes")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to get modes: {}", e.getMessage());
            return Map.of("modes", List.of());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSettings() {
        try {
            return restClient.get()
                .uri("/api/settings")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to get settings: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    // ==================== Internal ====================

    private void executeWithRetry(String operation, Runnable action) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                action.run();
                return;
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.warn("[{}] Failed after {} attempts: {}", operation, maxRetries, e.getMessage());
                } else {
                    log.debug("[{}] Attempt {}/{} failed, retrying...", operation, attempt, maxRetries);
                    try {
                        Thread.sleep(retryBackoff.toMillis() * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}
