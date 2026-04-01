package com.ablueforce.cortexce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * LLM-based quality scoring service.
 *
 * Uses LLM to analyze observations and infer quality scores.
 * Delegates to existing LlmService for actual LLM calls.
 */
@Service
public class LlmQualityScorer {

    private static final Logger log = LoggerFactory.getLogger(LlmQualityScorer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final LlmService llmService;

    public LlmQualityScorer(LlmService llmService) {
        this.llmService = llmService;
        log.info("LlmQualityScorer initialized, LlmService available: {}", llmService != null);
    }

    /**
     * Check if LLM-based scoring is available.
     */
    public boolean isAvailable() {
        return llmService != null;
    }

    /**
     * Analyze observation quality using LLM.
     */
    public LlmQualityAnalysis analyzeQuality(String title, String type,
                                             String content, String facts) {
        if (llmService == null) {
            log.warn("LlmService not available, returning default analysis");
            return LlmQualityAnalysis.defaultAnalysis();
        }

        try {
            // Build prompt manually to avoid String.format issues with % characters in content
            String prompt = "Analyze this observation and provide a quality score.\n\n"
                + "Title: " + (title != null ? title : "N/A") + "\n"
                + "Type: " + (type != null ? type : "N/A") + "\n"
                + "Content: " + (content != null ? content : "N/A") + "\n"
                + "Facts: " + (facts != null ? facts : "[]") + "\n\n"
                + "Respond in JSON format:\n"
                + "{\"quality_score\": 0.0-1.0, \"feedback_type\": \"SUCCESS|PARTIAL|FAILURE\", \"reasoning\": \"...\"}";

            String response = llmService.chatCompletion(
                "You are a software engineering quality analyst.",
                prompt
            );

            return parseAnalysisResponse(response);

        } catch (Exception e) {
            log.error("Failed to analyze quality with LLM: {}", e.getMessage());
            return LlmQualityAnalysis.defaultAnalysis();
        }
    }

    /**
     * Infer feedback type from session context using LLM.
     */
    public FeedbackType inferFeedbackLlm(String sessionSummary,
                                          String lastMessage,
                                          int observationCount) {
        if (llmService == null) {
            log.warn("LlmService not available, using rule-based inference");
            return null;
        }

        try {
            // Build prompt manually to avoid String.format issues with % characters in content
            String prompt = "Analyze this session and determine the outcome.\n\n"
                + "Session Summary: " + (sessionSummary != null ? sessionSummary : "N/A") + "\n"
                + "Last Message: " + (lastMessage != null ? lastMessage : "N/A") + "\n"
                + "Observations: " + observationCount + "\n\n"
                + "Respond with ONLY one word: SUCCESS, PARTIAL, or FAILURE";

            String response = llmService.chatCompletion(
                "You are a session outcome analyzer.",
                prompt
            );

            if (response == null) return null;
            
            // Use exact matching on trimmed response to avoid false positives
            // (e.g., "Overall success with partial improvements" should not match SUCCESS)
            String trimmed = response.trim().toUpperCase();
            // Check for exact single-word match first
            if (trimmed.equals("SUCCESS")) return FeedbackType.SUCCESS;
            if (trimmed.equals("FAILURE")) return FeedbackType.FAILURE;
            if (trimmed.equals("PARTIAL")) return FeedbackType.PARTIAL;
            // Fallback: check if response contains JSON or natural language with the keywords
            if (trimmed.contains("SUCCESS")) return FeedbackType.SUCCESS;
            if (trimmed.contains("FAILURE")) return FeedbackType.FAILURE;
            return FeedbackType.PARTIAL;

        } catch (Exception e) {
            log.error("Failed to infer feedback with LLM: {}", e.getMessage());
            return null;
        }
    }

    private LlmQualityAnalysis parseAnalysisResponse(String response) {
        try {
            double score = 0.5;
            String feedbackType = "UNKNOWN";
            String reasoning = "";

            if (response != null && !response.isBlank()) {
                // Extract JSON from response (handles cases where LLM wraps JSON in markdown)
                String jsonStr = response.trim();
                int jsonStart = jsonStr.indexOf('{');
                int jsonEnd = jsonStr.lastIndexOf('}');
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    jsonStr = jsonStr.substring(jsonStart, jsonEnd + 1);
                }

                JsonNode root = objectMapper.readTree(jsonStr);

                if (root.has("quality_score")) {
                    score = root.get("quality_score").asDouble(0.5);
                    score = Math.max(0.0, Math.min(1.0, score));
                }

                if (root.has("feedback_type")) {
                    feedbackType = root.get("feedback_type").asText("UNKNOWN").toUpperCase();
                }

                if (root.has("reasoning")) {
                    reasoning = root.get("reasoning").asText("");
                }
            }

            return new LlmQualityAnalysis(score, feedbackType, reasoning);

        } catch (Exception e) {
            log.warn("Failed to parse LLM response: {}", e.getMessage());
            return LlmQualityAnalysis.defaultAnalysis();
        }
    }

    public record LlmQualityAnalysis(
        double qualityScore,
        String feedbackType,
        String reasoning
    ) {
        public static LlmQualityAnalysis defaultAnalysis() {
            return new LlmQualityAnalysis(0.5, "UNKNOWN", "LLM not available");
        }

        public QualityScorer.FeedbackType toFeedbackType() {
            return switch (feedbackType.toUpperCase()) {
                case "SUCCESS" -> QualityScorer.FeedbackType.SUCCESS;
                case "FAILURE" -> QualityScorer.FeedbackType.FAILURE;
                default -> QualityScorer.FeedbackType.PARTIAL;
            };
        }
    }

    public enum FeedbackType {
        SUCCESS,
        PARTIAL,
        FAILURE
    }
}
