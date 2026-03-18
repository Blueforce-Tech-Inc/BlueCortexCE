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
            var body = Map.of(
                "task", request.task(),
                "project", request.project() != null ? request.project() : "",
                "count", request.count() != null ? request.count() : properties.getDefaultExperienceCount()
            );
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
            var body = Map.of(
                "task", request.task(),
                "project", request.project() != null ? request.project() : ""
            );
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

    @Override
    public boolean healthCheck() {
        try {
            restClient.get()
                .uri("/actuator/health")
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug("Health check failed: {}", e.getMessage());
            return false;
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
