package com.ablueforce.cortexce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * LLM-based quality scoring service.
 * 
 * Uses LLM to analyze observations and infer quality scores.
 * This is more accurate than rule-based scoring.
 */
@Service
public class LlmQualityScorer {

    private static final Logger log = LoggerFactory.getLogger(LlmQualityScorer.class);

    private final LlmService llmService;
    
    // Prompt template for quality analysis
    private static final String QUALITY_ANALYSIS_PROMPT = """
        Analyze the following observation and provide a quality score.
        
        Observation:
        - Title: %s
        - Type: %s
        - Content: %s
        - Facts: %s
        
        Consider:
        1. Task completion: Was the task successfully completed?
        2. Technical depth: How detailed and accurate is the solution?
        3. Reusability: Can this experience be reused for similar tasks?
        4. Error handling: Are edge cases covered?
        
        Respond in JSON format:
        {
            "quality_score": 0.0-1.0,
            "feedback_type": "SUCCESS|PARTIAL|FAILURE",
            "reasoning": "brief explanation"
        }
        """;

    public LlmQualityScorer(LlmService llmService) {
        this.llmService = llmService;
    }

    /**
     * Analyze observation quality using LLM.
     * 
     * @param title Observation title
     * @param type Observation type (feature, bugfix, etc.)
     * @param content Observation content
     * @param facts Facts extracted
     * @return Quality analysis result
     */
    public LlmQualityAnalysis analyzeQuality(String title, String type, 
                                             String content, String facts) {
        // Check if LLM is available
        if (llmService == null) {
            log.warn("LlmService not available, returning default analysis");
            return LlmQualityAnalysis.defaultAnalysis();
        }
        
        try {
            String prompt = String.format(QUALITY_ANALYSIS_PROMPT,
                title != null ? title : "N/A",
                type != null ? type : "N/A",
                content != null ? content : "N/A",
                facts != null ? facts : "[]");
            
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
     * 
     * @param sessionSummary Summary of the session
     * @param lastMessage Last assistant message
     * @param observationCount Number of observations
     * @return Inferred feedback type
     */
    public FeedbackType inferFeedbackLlm(String sessionSummary, 
                                          String lastMessage,
                                          int observationCount) {
        if (llmService == null) {
            log.warn("LlmService not available, using rule-based inference");
            return null; // Fall back to rule-based
        }
        
        try {
            String prompt = String.format("""
                Analyze this session and determine the outcome.
                
                Session Summary: %s
                Last Message: %s
                Observations: %d
                
                Determine if the session was successful, partially successful, or failed.
                
                Respond with ONLY one word: SUCCESS, PARTIAL, or FAILURE
                """, 
                sessionSummary != null ? sessionSummary : "N/A",
                lastMessage != null ? lastMessage : "N/A",
                observationCount
            );
            
            String response = llmService.chatCompletion(
                "You are a session outcome analyzer.", 
                prompt
            );
            
            String trimmed = response.trim().toUpperCase();
            if (trimmed.contains("SUCCESS")) {
                return FeedbackType.SUCCESS;
            } else if (trimmed.contains("FAILURE")) {
                return FeedbackType.FAILURE;
            } else {
                return FeedbackType.PARTIAL;
            }
            
        } catch (Exception e) {
            log.error("Failed to infer feedback with LLM: {}", e.getMessage());
            return null; // Fall back to rule-based
        }
    }

    private LlmQualityAnalysis parseAnalysisResponse(String response) {
        try {
            // Simple parsing - look for JSON-like content
            double score = 0.5; // default
            String feedbackType = "UNKNOWN";
            String reasoning = "";
            
            // Try to extract quality score
            if (response.contains("quality_score")) {
                int start = response.indexOf("quality_score") + 15;
                int end = response.indexOf(",", start);
                if (end < 0) end = response.indexOf("}", start);
                if (end > start) {
                    String scoreStr = response.substring(start, end).trim();
                    score = Double.parseDouble(scoreStr.replaceAll("[^0-9.]", ""));
                }
            }
            
            // Try to extract feedback type
            if (response.contains("feedback_type")) {
                int start = response.indexOf("feedback_type") + 14;
                int end = response.indexOf("}", start);
                if (end < 0) end = response.length();
                String type = response.substring(start, end).trim().replaceAll("[\":,]", "");
                if (type.contains("SUCCESS")) feedbackType = "SUCCESS";
                else if (type.contains("FAILURE")) feedbackType = "FAILURE";
                else feedbackType = "PARTIAL";
            }
            
            return new LlmQualityAnalysis(score, feedbackType, reasoning);
            
        } catch (Exception e) {
            log.warn("Failed to parse LLM response: {}", e.getMessage());
            return LlmQualityAnalysis.defaultAnalysis();
        }
    }

    /**
     * Check if LLM-based scoring is available.
     */
    public boolean isAvailable() {
        return llmService != null;
    }

    /**
     * Quality analysis result from LLM.
     */
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
