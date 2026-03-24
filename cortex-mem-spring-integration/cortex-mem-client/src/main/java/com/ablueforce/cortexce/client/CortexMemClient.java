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
     */
    Map<String, Object> getLatestExtraction(String projectPath, String templateName, String userId);

    /**
     * Get extraction history for a template and user.
     * Calls GET /api/extraction/{templateName}/history?projectPath=...&userId=...&limit=...
     */
    List<Map<String, Object>> getExtractionHistory(String projectPath, String templateName, String userId, int limit);

    /**
     * Update session userId. Calls PATCH /api/session/{sessionId}/user.
     */
    Map<String, Object> updateSessionUserId(String sessionId, String userId);

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
     * @return paginated list of observations
     */
    Map<String, Object> listObservations(ObservationsRequest request);

    /**
     * Get observations by IDs.
     * Calls POST /api/observations/batch
     *
     * @param ids list of observation IDs
     * @return list of observations
     */
    Map<String, Object> getObservationsByIds(java.util.List<String> ids);

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
     * Get project statistics.
     * Calls GET /api/stats
     *
     * @param projectPath optional project path filter
     * @return statistics for the project
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
