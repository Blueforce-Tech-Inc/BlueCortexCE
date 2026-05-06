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

import java.net.URL;
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

    /**
     * SDK version for User-Agent header. Read dynamically from JAR manifest (Implementation-Version)
     * to stay in sync with the project version. Falls back to "unknown" if not available (e.g., tests).
     */
    private static final String SDK_VERSION = resolveSdkVersion();

    private final RestClient restClient;
    private final CortexMemProperties properties;
    private final int maxRetries;
    private final Duration retryBackoff;

    public CortexMemClientImpl(CortexMemProperties properties) {
        this(properties, null);
    }

    public CortexMemClientImpl(CortexMemProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.maxRetries = Math.max(1, properties.getRetry().getMaxAttempts());
        Duration backoff = properties.getRetry().getBackoff();
        this.retryBackoff = backoff != null && !backoff.isNegative() && !backoff.isZero()
            ? backoff : Duration.ofMillis(500);

        if (restClientBuilder == null) {
            restClientBuilder = RestClient.builder();
        }

        // Validate configuration
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be null or blank");
        }
        Duration connectTimeout = properties.getConnectTimeout();
        if (connectTimeout == null || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("connectTimeout must not be null or negative");
        }
        Duration readTimeout = properties.getReadTimeout();
        long readTimeoutMs = readTimeout != null ? readTimeout.toMillis() : 30_000L;
        if (readTimeoutMs > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("readTimeout exceeds maximum supported value (24.8 days)");
        }

        // Apply timeout configuration from properties
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout((int) readTimeoutMs);

        var builder = restClientBuilder
            .requestFactory(requestFactory)
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("User-Agent", "cortex-mem-java/" + SDK_VERSION);

        // Bearer token auth when apiKey is configured (matches JS/Go SDK behavior)
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.getApiKey());
        }

        this.restClient = builder.build();

        log.info("CortexMemClient initialized → {} (SDK {})", properties.getBaseUrl(), SDK_VERSION);
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
        requireAbsolutePath(request.projectPath(), "projectPath");
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
        requireNonBlank(request.projectPath(), "projectPath");
        requireAbsolutePath(request.projectPath(), "projectPath");
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
        requireNonBlank(request.projectPath(), "projectPath");
        requireAbsolutePath(request.projectPath(), "projectPath");
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
        executeWithRetrySilent("triggerRefinement", () ->
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
    public ExtractionResponse getLatestExtraction(String projectPath, String templateName, String userId) {
        requireNonBlank(projectPath, "projectPath");
        requireNonBlank(templateName, "templateName");
        return executeWithRetryReturn("getLatestExtraction", () ->
            restClient.get()
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
                .body(ExtractionResponse.class)
        );
    }

    @Override
    public List<Map<String, Object>> getExtractionHistory(String projectPath, String templateName, String userId, int limit) {
        requireNonBlank(projectPath, "projectPath");
        requireNonBlank(templateName, "templateName");
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        return executeWithRetryReturn("getExtractionHistory", () -> {
            List<Map<String, Object>> result = restClient.get()
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
            return result != null ? result : List.of();
        });
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
        executeWithRetrySilent("triggerExtraction", () ->
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
                    // Only send limit when > 0; omitting lets the backend use its default (20).
                    // Sending 0 would override the backend's @RequestParam(defaultValue="20").
                    if (request.limit() != null && request.limit() > 0) {
                        builder.queryParam("limit", request.limit());
                    }
                    if (request.offset() != null && request.offset() > 0) {
                        builder.queryParam("offset", request.offset());
                    }
                    if (request.orderBy() != null && !request.orderBy().isBlank()) {
                        builder.queryParam("orderBy", request.orderBy());
                    }
                    return builder.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to search: {}", e.getMessage());
            return Map.of("observations", List.of(), "strategy", "none", "fell_back", true, "count", 0, "error", "search failed: " + e.getMessage());
        }
    }

    @Override
    public PagedObservationResponse listObservations(ObservationsRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        // project is optional per DTO contract — null means all projects
        try {
            Map<String, Object> raw = restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/observations");
                    if (request.project() != null && !request.project().isBlank()) {
                        builder.queryParam("project", request.project());
                    }
                    if (request.offset() != null && request.offset() > 0) {
                        builder.queryParam("offset", request.offset());
                    }
                    // Only send limit when > 0; omitting lets the backend use its default (20).
                    // Sending 0 would override the backend's @RequestParam(defaultValue="20").
                    if (request.limit() != null && request.limit() > 0) {
                        builder.queryParam("limit", request.limit());
                    }
                    return builder.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (raw == null) {
                return new PagedObservationResponse(List.of(), false);
            }
            List<?> itemsRaw = (List<?>) raw.getOrDefault("items", List.of());
            List<ObservationResponse> items = itemsRaw.stream()
                .filter(Map.class::isInstance)
                .map(o -> mapToObservationResponse((Map<String, Object>) o))
                .toList();
            Boolean hasMore = (Boolean) raw.getOrDefault("hasMore", false);
            return new PagedObservationResponse(items, hasMore != null && hasMore);
        } catch (Exception e) {
            log.warn("Failed to list observations: {}", e.getMessage());
            return new PagedObservationResponse(List.of(), false);
        }
    }

    @Override
    public ObservationResponse getObservation(String observationId) {
        requireNonBlank(observationId, "observationId");
        List<ObservationResponse> observations = getObservationsByIds(List.of(observationId));
        if (observations == null || observations.isEmpty()) {
            return null;
        }
        return observations.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ObservationResponse> getObservationsByIds(List<String> ids) {
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
            Map<String, Object> raw = restClient.post()
                .uri("/api/observations/batch")
                .body(Map.of("ids", ids))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (raw == null) {
                return List.of();
            }
            List<?> obsRaw = (List<?>) raw.getOrDefault("observations", List.of());
            return obsRaw.stream()
                .filter(Map.class::isInstance)
                .map(o -> mapToObservationResponse((Map<String, Object>) o))
                .toList();
        } catch (Exception e) {
            log.warn("Failed to get observations by IDs: {}", e.getMessage());
            return List.of();
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
        // Note: /api/stats supports project-scoped stats when projectPath is provided (backend B11-1 fix).
        // When projectPath is null/blank, returns global stats. Matches Go SDK behavior.
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
        } catch (RestClientResponseException httpEx) {
            // Extract meaningful error from HTTP response body (e.g., {"error": "..."}).
            String errorDetail = tryExtractErrorMessage(httpEx);
            log.warn("Failed to get stats (HTTP {}): {}", httpEx.getStatusCode().value(), errorDetail);
            return Map.of("error", errorDetail, "fell_back", true);
        } catch (Exception e) {
            log.warn("Failed to get stats: {}", e.getMessage());
            return Map.of("error", e.getMessage(), "fell_back", true);
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
     * Execute a value-returning operation with retry.
     * Propagates exception on final failure (consistent with executeWithRetry).
     *
     * @param operation Operation name for logging
     * @param supplier  The HTTP call to execute
     * @return The result of the supplier
     * @param <T>       Return type
     */
    private <T> T executeWithRetryReturn(String operation, java.util.function.Supplier<T> supplier) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return supplier.get();
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
                        log.warn("[{}] Interrupted during retry sleep, giving up", operation);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Check if an error is transient and worth retrying.
     * Transient: network errors (non-HTTP), 429 (rate limited), 502/503/504 (server errors).
     * Non-transient: 4xx client errors (bad request, unauthorized, forbidden, not found, etc.).
     * <p>
     * <b>Note on 500:</b> HTTP 500 is intentionally excluded from retry because it typically
     * indicates a code bug rather than a transient condition. If the backend returns 500 for
     * transient reasons (e.g., DB connection pool exhaustion), retrying would likely produce
     * the same error. This matches the Go SDK's {@code isTransient()} behavior.
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

    /**
     * Validate that a path is an absolute path (starts with / on Unix or a drive letter on Windows).
     *
     * @param value     the path value to check
     * @param fieldName the field name for the error message
     * @throws IllegalArgumentException if value is not an absolute path
     */
    private static void requireAbsolutePath(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return; // requireNonBlank already handles null/blank
        }
        if (!java.nio.file.Paths.get(value).isAbsolute()) {
            throw new IllegalArgumentException(fieldName + " must be an absolute path (got: " + value + ")");
        }
    }

    /**
     * Extract a human-readable error message from a RestClientResponseException.
     * Tries to parse the response body as JSON and extract an "error" field,
     * falling back to the HTTP status line if parsing fails.
     */
    private static String tryExtractErrorMessage(RestClientResponseException httpEx) {
        try {
            byte[] body = httpEx.getResponseBodyAsByteArray();
            if (body != null && body.length > 0) {
                var node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(body);
                var err = node.get("error");
                if (err != null && !err.isNull()) {
                    return err.asText();
                }
            }
        } catch (Exception parseEx) {
            // Fall through to status line
        }
        return httpEx.getStatusText() + " (HTTP " + httpEx.getStatusCode().value() + ")";
    }

    /**
     * Convert a raw Map (deserialized from JSON) to an ObservationResponse DTO.
     * Handles all field mappings including the 4 extended backend fields
     * (accessCount, refinedAt, refinedFromIds, userComment).
     * <p>
     * Note: When Jackson deserializes with JavaTimeModule, temporal fields
     * (OffsetDateTime) may be stored as OffsetDateTime objects, not Strings.
     * This method handles both cases via Object-to-String conversion.
     */
    @SuppressWarnings("unchecked")
    private static ObservationResponse mapToObservationResponse(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        return new ObservationResponse(
            str(raw.get("id")),
            str(raw.get("content_session_id")),
            str(raw.get("project")),
            str(raw.get("type")),
            str(raw.get("title")),
            str(raw.get("subtitle")),
            str(raw.get("narrative")),
            (List<String>) raw.get("facts"),
            (List<String>) raw.get("concepts"),
            (List<String>) raw.get("files_read"),
            (List<String>) raw.get("files_modified"),
            raw.get("quality_score") != null ? ((Number) raw.get("quality_score")).floatValue() : null,
            str(raw.get("feedback_type")),
            str(raw.get("feedback_updated_at")),
            str(raw.get("source")),
            (Map<String, Object>) raw.get("extractedData"),
            raw.get("prompt_number") != null ? ((Number) raw.get("prompt_number")).intValue() : null,
            str(raw.get("created_at")),
            raw.get("created_at_epoch") != null ? ((Number) raw.get("created_at_epoch")).longValue() : null,
            str(raw.get("last_accessed_at")),
            raw.get("access_count") != null ? ((Number) raw.get("access_count")).intValue() : null,
            str(raw.get("refined_at")),
            str(raw.get("refined_from_ids")),
            str(raw.get("user_comment"))
        );
    }

    /**
     * Resolve SDK version from JAR manifest's Implementation-Version entry.
     * Falls back to "unknown" when running from IDE, test classpath, or any non-packaged context.
     */
    private static String resolveSdkVersion() {
        try {
            var cl = CortexMemClientImpl.class;
            String className = cl.getSimpleName() + ".class";
            URL classUrl = cl.getResource(className);
            if (classUrl == null) {
                return "unknown";
            }
            String jarPath = classUrl.getPath();
            // Only resolve from a JAR file (jar:file:///path/to/artifact.jar!/...), not from directory
            if (!jarPath.startsWith("jar:")) {
                return "unknown";
            }
            var manifest = new java.util.jar.Manifest(
                new URL(jarPath.substring(4, jarPath.lastIndexOf("!")) + "!/META-INF/MANIFEST.MF").openStream()
            );
            var attr = manifest.getMainAttributes();
            String version = attr.getValue("Implementation-Version");
            return (version != null && !version.isBlank()) ? version : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Safely convert an Object to String.
     * Handles both String and temporal types (OffsetDateTime, LocalDateTime, etc.)
     * that Jackson deserializes temporal JSON values as objects, not strings.
     */
    private static String str(Object v) {
        if (v == null) return null;
        return v.toString();
    }
}
