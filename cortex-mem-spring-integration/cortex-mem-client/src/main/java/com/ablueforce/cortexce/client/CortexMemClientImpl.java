package com.ablueforce.cortexce.client;

import com.ablueforce.cortexce.client.config.CortexMemProperties;
import com.ablueforce.cortexce.client.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

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

        // Apply timeout configuration from properties
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.getConnectTimeout())
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout((int) properties.getReadTimeout().toMillis());

        var builder = restClientBuilder
            .requestFactory(requestFactory)
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("User-Agent", "cortex-mem-java/1.0.0");

        // Bearer token auth when apiKey is configured (matches JS/Go SDK behavior)
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.getApiKey());
        }

        this.restClient = builder.build();

        log.info("CortexMemClient initialized → {}", properties.getBaseUrl());
    }

    // ==================== Capture ====================

    @Override
    public Map<String, Object> startSession(SessionStartRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireNonBlank(request.sessionId(), "sessionId");
        requireNonBlank(request.projectPath(), "projectPath");
        // Propagates errors (not fire-and-forget): the caller MUST know the session
        // was created successfully to obtain session_db_id and prompt_number.
        // Matches Go SDK behavior: StartSession propagates errors.
        Map<String, Object> result = restClient.post()
            .uri("/api/session/start")
            .body(request.toWireFormat())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        if (result == null) {
            throw new IllegalStateException("startSession returned null response body");
        }
        return result;
    }

    @Override
    public void recordObservation(ObservationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireNonBlank(request.sessionId(), "sessionId");
        requireNonBlank(request.projectPath(), "projectPath");
        requireNonBlank(request.toolName(), "toolName");
        executeWithRetrySilent("recordObservation", () ->
            restClient.post()
                .uri("/api/ingest/tool-use")
                .body(request.toWireFormat())
                .retrieve()
                .toBodilessEntity()
        );
    }

    @Override
    public void recordSessionEnd(SessionEndRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireNonBlank(request.sessionId(), "sessionId");
        executeWithRetrySilent("recordSessionEnd", () ->
            restClient.post()
                .uri("/api/ingest/session-end")
                .body(request.toWireFormat())
                .retrieve()
                .toBodilessEntity()
        );
    }

    @Override
    public void recordUserPrompt(UserPromptRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireNonBlank(request.sessionId(), "sessionId");
        requireNonBlank(request.promptText(), "promptText");
        executeWithRetrySilent("recordUserPrompt", () ->
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
        Objects.requireNonNull(request, "request must not be null");
        requireNonBlank(request.task(), "task");
        try {
            List<Experience> result = restClient.post()
                .uri("/api/memory/experiences")
                .body(request.toWireFormat())
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
        Objects.requireNonNull(request, "request must not be null");
        requireNonBlank(request.task(), "task");
        try {
            ICLPromptResult result = restClient.post()
                .uri("/api/memory/icl-prompt")
                .body(request.toWireFormat())
                .retrieve()
                .body(ICLPromptResult.class);
            return result != null ? result : new ICLPromptResult("", 0);
        } catch (Exception e) {
            log.warn("Failed to build ICL prompt: {}", e.getMessage());
            return new ICLPromptResult("", 0);
        }
    }

    // ==================== Management ====================

    @Override
    public void triggerRefinement(String projectPath) {
        requireNonBlank(projectPath, "projectPath");
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
        requireNonBlank(observationId, "observationId");
        requireNonBlank(feedbackType, "feedbackType");
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
        requireNonBlank(projectPath, "projectPath");
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
        requireNonBlank(observationId, "observationId");
        Objects.requireNonNull(update, "update must not be null");
        // Validate at least one field is set (PATCH semantics: empty update is a no-op).
        if (update.isEmpty()) {
            throw new IllegalArgumentException("at least one field must be provided for update");
        }
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
        requireNonBlank(observationId, "observationId");
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
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restClient.get()
                .uri("/api/health")
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (resp == null) {
                log.debug("Health check returned null body");
                return false;
            }
            Object status = resp.get("status");
            if (!"ok".equals(status)) {
                log.debug("Health check returned degraded status: {}", status);
                return false;
            }
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
        requireNonBlank(projectPath, "projectPath");
        requireNonBlank(templateName, "templateName");
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
            return Map.of("error", e.getMessage(), "template", templateName);
        }
    }

    @Override
    public List<Map<String, Object>> getExtractionHistory(String projectPath, String templateName, String userId, int limit) {
        requireNonBlank(projectPath, "projectPath");
        requireNonBlank(templateName, "templateName");
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        try {
            return restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                        .path("/api/extraction/{template}/history")
                        .queryParam("projectPath", projectPath);
                    // Only send limit when > 0; omitting lets the backend use its default (10).
                    // Sending 0 would be clamped to 1 by the backend (not "use default").
                    if (limit > 0) {
                        builder.queryParam("limit", limit);
                    }
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
        requireNonBlank(sessionId, "sessionId");
        requireNonBlank(userId, "userId");
        // Propagates errors: caller needs to know if the update succeeded.
        Map<String, Object> result = restClient.patch()
            .uri("/api/session/{sessionId}/user", sessionId)
            .body(Map.of("user_id", userId))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        if (result == null) {
            throw new IllegalStateException("updateSessionUserId returned null response body");
        }
        return result;
    }

    @Override
    public void triggerExtraction(String projectPath) {
        requireNonBlank(projectPath, "projectPath");
        executeWithRetry("triggerExtraction", () ->
            restClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/extraction/run")
                    .queryParam("projectPath", projectPath)
                    .build())
                .retrieve()
                .toBodilessEntity()
        );
    }

    // ==================== Search & List (P0) ====================

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> search(SearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireNonBlank(request.project(), "project");
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
        Objects.requireNonNull(request, "request must not be null");
        // project is optional per DTO contract — null means all projects
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
    public Map<String, Object> getObservation(String observationId) {
        requireNonBlank(observationId, "observationId");
        Map<String, Object> result = getObservationsByIds(List.of(observationId));
        List<Map<String, Object>> observations = (List<Map<String, Object>>) result.get("observations");
        if (observations == null || observations.isEmpty()) {
            return null;
        }
        return observations.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getObservationsByIds(List<String> ids) {
        Objects.requireNonNull(ids, "ids must not be null");
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("ids must not be empty");
        }
        if (ids.size() > 100) {
            throw new IllegalArgumentException("batch size exceeds maximum of 100 (got " + ids.size() + ")");
        }
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i) == null || ids.get(i).isBlank()) {
                throw new IllegalArgumentException("ids[" + i + "] is empty");
            }
        }
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
            return Map.of("service", "unknown", "version", "unknown");
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
            return Map.of("settings", Map.of(), "error", e.getMessage());
        }
    }

    // ==================== Internal ====================

    /**
     * Execute with retry. On final failure, throws the last exception.
     * Use for explicit user actions where the caller needs to know the outcome.
     * Only retries on transient errors (network failures, 429 rate limited, 5xx server errors).
     * Skips retry on 4xx client errors (bad request, unauthorized, forbidden, etc.).
     * Backoff includes ±25% jitter to prevent thundering herd.
     */
    private void executeWithRetry(String operation, Runnable action) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                action.run();
                return;
            } catch (Exception e) {
                lastException = e;
                if (!isRetryable(e)) {
                    log.debug("[{}] Non-retryable error ({}), giving up", operation, e.getMessage());
                    break;
                }
                if (attempt < maxRetries) {
                    log.debug("[{}] Attempt {}/{} failed, retrying...", operation, attempt, maxRetries);
                    long jitteredMs = jitteredBackoff(attempt);
                    try {
                        Thread.sleep(jitteredMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                }
            }
        }
        log.warn("[{}] Failed after attempts: {}", operation, lastException.getMessage());
        throw new RuntimeException(operation + " failed", lastException);
    }

    /**
     * Execute with retry. On final failure, logs a warning and swallows the error.
     * Use for background/hook operations where fire-and-forget is appropriate.
     * Only retries on transient errors (network failures, 429 rate limited, 5xx server errors).
     * Skips retry on 4xx client errors (bad request, unauthorized, forbidden, etc.).
     * Backoff includes ±25% jitter to prevent thundering herd.
     */
    private void executeWithRetrySilent(String operation, Runnable action) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                action.run();
                return;
            } catch (Exception e) {
                if (!isRetryable(e)) {
                    log.warn("[{}] Failed with non-retryable error: {}", operation, e.getMessage());
                    return;
                }
                if (attempt == maxRetries) {
                    log.warn("[{}] Failed after {} attempts: {}", operation, maxRetries, e.getMessage());
                } else {
                    log.debug("[{}] Attempt {}/{} failed, retrying...", operation, attempt, maxRetries);
                    long jitteredMs = jitteredBackoff(attempt);
                    try {
                        Thread.sleep(jitteredMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    /**
     * Check if an error is transient and worth retrying.
     * Transient: network errors (non-HTTP), 429 (rate limited), 5xx (server errors).
     * Non-transient: 4xx client errors (bad request, unauthorized, forbidden, not found, etc.).
     */
    private static boolean isRetryable(Exception e) {
        if (e instanceof RestClientResponseException httpEx) {
            int code = httpEx.getStatusCode().value();
            // Retry on 429 (rate limited), 502 (bad gateway), 503 (unavailable), 504 (timeout).
            // Do NOT retry on 500 (code bug) or other 5xx/4xx.
            // Matches Go SDK isTransient() for consistent behavior across SDKs.
            return code == 429 || code == 502 || code == 503 || code == 504;
        }
        // Non-HTTP errors (network failures, timeouts) are always worth retrying
        return true;
    }

    /**
     * Calculate jittered backoff: base = backoff * attempt, jittered to [0.75x, 1.25x].
     * Jitter = random(0, baseMs/2) - baseMs/4, giving range [-25%, +25%].
     * Minimum 1ms to avoid zero-delay busy loops.
     * Matches Go SDK jitter calculation for consistent behavior across SDKs.
     */
    private long jitteredBackoff(int attempt) {
        long baseMs = retryBackoff.toMillis() * attempt;
        long jitterRange = baseMs / 2;
        long jitter = jitterRange > 0
            ? ThreadLocalRandom.current().nextLong(jitterRange) - baseMs / 4
            : 0;
        return Math.max(1, baseMs + jitter);
    }

    /**
     * Validate that a required string field is not null or blank.
     *
     * @param value     the value to check
     * @param fieldName the field name for the error message
     * @throws IllegalArgumentException if value is null or blank
     */
    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
    }
}
