package com.ablueforce.cortexce.client;

import com.ablueforce.cortexce.client.dto.*;

import java.util.List;
import java.util.Map;

/**
 * Unified client interface for the Cortex CE memory system.
 * <p>
 * Encapsulates all memory operations: capture, retrieval, and management.
 */
public interface CortexMemClient {

    // ==================== Capture ====================

    /**
     * Start a session (or resume existing). Sends to POST /api/session/start.
     * Returns backend response (session_db_id, context, prompt_number, etc.).
     */
    Map<String, Object> startSession(SessionStartRequest request);

    /**
     * Record a tool-use observation. Sends to POST /api/ingest/tool-use.
     */
    void recordObservation(ObservationRequest request);

    /**
     * Signal session end. Sends to POST /api/ingest/session-end.
     */
    void recordSessionEnd(SessionEndRequest request);

    /**
     * Record a user prompt. Sends to POST /api/ingest/user-prompt.
     */
    void recordUserPrompt(UserPromptRequest request);

    // ==================== Retrieval ====================

    /**
     * Retrieve relevant experiences via ExpRAG. Calls POST /api/memory/experiences.
     */
    List<Experience> retrieveExperiences(ExperienceRequest request);

    /**
     * Build an ICL prompt with historical experiences. Calls POST /api/memory/icl-prompt.
     */
    ICLPromptResult buildICLPrompt(ICLPromptRequest request);

    // ==================== Management ====================

    /**
     * Trigger memory refinement for a project. Calls POST /api/memory/refine.
     */
    void triggerRefinement(String projectPath);

    /**
     * Submit feedback for an observation. Calls POST /api/memory/feedback.
     *
     * @param observationId observation UUID
     * @param feedbackType  e.g. "SUCCESS", "FAILURE", "USEFUL", "NOT_USEFUL"
     * @param comment       optional free-text comment
     */
    void submitFeedback(String observationId, String feedbackType, String comment);

    // ==================== Observation Management (V14) ====================

    /**
     * Update an existing observation. Calls PATCH /api/memory/observations/{id}.
     * V14: Supports source and extractedData fields.
     */
    void updateObservation(String observationId, ObservationUpdate update);

    /**
     * Delete an observation. Calls DELETE /api/memory/observations/{id}.
     */
    void deleteObservation(String observationId);

    /**
     * Get memory quality distribution. Calls GET /api/memory/quality-distribution.
     */
    QualityDistribution getQualityDistribution(String projectPath);

    /**
     * Health check. Calls GET /api/health.
     */
    boolean healthCheck();

    // ==================== Extraction (Phase 3) ====================

    /**
     * Get latest extraction result for a template and user.
     * Calls GET /api/extraction/{templateName}/latest?projectPath=...&userId=...
     *
     * @return ExtractionResponse with status, template, extractedData, etc.
     *         Check {@code response.isFound()} to determine if extraction exists.
     */
    ExtractionResponse getLatestExtraction(String projectPath, String templateName, String userId);

    /**
     * Get extraction history for a template and user.
     * Calls GET /api/extraction/{templateName}/history?projectPath=...&userId=...&limit=...
     *
     * @param limit maximum number of history entries (1-100). If limit is 0, the parameter is
     *              omitted from the request and the backend default (10) is used.
     *              Note: the backend clamps limit=0 to 1, so this SDK correctly omits it to
     *              get the default behavior.
     * @throws IllegalArgumentException if limit is negative
     */
    List<Map<String, Object>> getExtractionHistory(String projectPath, String templateName, String userId, int limit);

    /**
     * Update session userId. Calls PATCH /api/session/{sessionId}/user.
     */
    Map<String, Object> updateSessionUserId(String sessionId, String userId);

    /**
     * Manually trigger extraction for a project.
     * Calls POST /api/extraction/run?projectPath=...
     *
     * <p><b>Fire-and-forget:</b> This method uses silent retry with final-error swallowing.
     * Failures are logged but never thrown to the caller. This is intentional: extraction
     * is an async background task and its outcome should not block the caller's pipeline.
     *
     * @param projectPath project path to extract for
     */
    void triggerExtraction(String projectPath);

    // ==================== Search & List (P0) ====================

    /**
     * Search observations by query, type, source, or concept.
     * Calls GET /api/search
     *
     * @param request search parameters
     * @return search result with observations, strategy, and metadata
     */
    Map<String, Object> search(SearchRequest request);

    /**
     * List observations with pagination.
     * Calls GET /api/observations
     *
     * @param request list parameters with pagination
     * @return paginated list of observations (includes 4 extended backend fields:
     *         accessCount, refinedAt, refinedFromIds, userComment)
     */
    PagedObservationResponse listObservations(ObservationsRequest request);

    /**
     * Get a single observation by ID.
     * Convenience method wrapping {@code getObservationsByIds}.
     *
     * Cross-SDK parity: Go GetObservation(id), Python get_observation(id), JS getObservation(id).
     *
     * @param observationId observation UUID
     * @return observation data (includes 4 extended backend fields:
     *         accessCount, refinedAt, refinedFromIds, userComment), or null if not found
     */
    ObservationResponse getObservation(String observationId);

    /**
     * Get observations by IDs.
     * Calls POST /api/observations/batch
     *
     * @param ids list of observation IDs (max 100)
     * @return list of observations (includes 4 extended backend fields:
     *         accessCount, refinedAt, refinedFromIds, userComment)
     * @throws IllegalArgumentException if ids is empty or exceeds 100
     */
    List<ObservationResponse> getObservationsByIds(List<String> ids);

    // ==================== P1 Management APIs ====================

    /**
     * Get backend version info.
     * Calls GET /api/version
     *
     * @return version info including build time, Java version, etc.
     */
    Map<String, Object> getVersion();

    /**
     * Get all projects.
     * Calls GET /api/projects
     *
     * @return list of projects
     */
    Map<String, Object> getProjects();

    /**
     * Get service statistics.
     * Calls GET /api/stats.
     * When projectPath is provided, returns project-scoped statistics (totalObservations,
     * totalSummaries, totalSessions for that project). When projectPath is null/blank,
     * returns global statistics for the entire service.
     * Matches Go/Python SDK behavior.
     *
     * @param projectPath optional project path for project-scoped stats; null/blank for global stats
     * @return service statistics (worker + database, optionally scoped to project)
     */
    Map<String, Object> getStats(String projectPath);

    /**
     * Get memory mode settings.
     * Calls GET /api/modes
     *
     * @return list of memory modes
     */
    Map<String, Object> getModes();

    /**
     * Get settings.
     * Calls GET /api/settings
     *
     * @return current settings
     */
    Map<String, Object> getSettings();
}
