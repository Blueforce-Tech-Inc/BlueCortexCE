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

    /**
     * Get memory quality distribution. Calls GET /api/memory/quality-distribution.
     */
    QualityDistribution getQualityDistribution(String projectPath);

    /**
     * Health check. Calls GET /api/health or GET /actuator/health.
     */
    boolean healthCheck();
}
