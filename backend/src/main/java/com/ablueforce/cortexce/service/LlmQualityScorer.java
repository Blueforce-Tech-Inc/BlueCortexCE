package com.ablueforce.cortexce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private final DirectLlmService directLlmService;
    
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
        
        Respond in JSON format:
        {"quality_score": 0.0-1.0, "feedback_type": "SUCCESS|PARTIAL|FAILURE", "reasoning": "..."}
        """;

    public LlmQualityScorer(LlmService llmService, DirectLlmService directLlmService) {
        this.llmService = llmService;
        this.directLlmService = directLlmService;
        log.info("LlmQualityScorer initialized, LLM available: {}", isAvailable());
    }

    /**
     * Check if LLM-based scoring is available.
     */
    public boolean isAvailable() {
        // Check direct LLM first
        if (directLlmService != null) {
            boolean available = directLlmService.isAvailable();
            log.info("DirectLlmService availability: {}", available);
            return available;
        }
        log.info("DirectLlmService is null, LLM not available");
        return false;
    }

    /**
     * Analyze observation quality using LLM.
     */
    public LlmQualityAnalysis analyzeQuality(String title, String type, 
                                             String content, String facts) {
        // Try direct LLM first (more reliable)
        if (directLlmService != null && directLlmService.isAvailable()) {
            try {
                log.debug("Using DirectLlmService for quality analysis");
                String response = directLlmService.analyzeQuality(
                    title != null ? title : "N/A",
                    type != null ? type : "N/A",
                    content != null ? content : "N/A",
                    facts != null ? facts : "[]"
                );
                
                if (response != null) {
                    log.debug("Direct LLM response: {}", response);
                    return parseAnalysisResponse(response);
                }
            } catch (Exception e) {
                log.warn("Direct LLM failed: {}", e.getMessage());
            }
        }
        
        log.warn("No LLM available, returning default analysis");
        return LlmQualityAnalysis.defaultAnalysis();
    }

    /**
     * Infer feedback type from session context using LLM.
     */
    public FeedbackType inferFeedbackLlm(String sessionSummary, 
                                          String lastMessage,
                                          int observationCount) {
        // Try direct LLM first
        if (directLlmService != null && directLlmService.isAvailable()) {
            try {
                String prompt = String.format("""
                    Analyze this session and determine the outcome.
                    
                    Session Summary: %s
                    Last Message: %s
                    Observations: %d
                    
                    Respond with ONLY one word: SUCCESS, PARTIAL, or FAILURE
                    """, 
                    sessionSummary != null ? sessionSummary : "N/A",
                    lastMessage != null ? lastMessage : "N/A",
                    observationCount
                );
                
                String response = directLlmService.chat(
                    "You are a session outcome analyzer.", 
                    prompt
                );
                
                if (response != null) {
                    String trimmed = response.trim().toUpperCase();
                    log.debug("Direct LLM feedback inference: {}", trimmed);
                    if (trimmed.contains("SUCCESS")) return FeedbackType.SUCCESS;
                    if (trimmed.contains("FAILURE")) return FeedbackType.FAILURE;
                    return FeedbackType.PARTIAL;
                }
            } catch (Exception e) {
                log.warn("Direct LLM feedback inference failed: {}", e.getMessage());
            }
        }
        
        return null; // Fall back to rule-based
    }

    private LlmQualityAnalysis parseAnalysisResponse(String response) {
        try {
            double score = 0.5;
            String feedbackType = "UNKNOWN";
            String reasoning = "";
            
            if (response.contains("quality_score")) {
                int start = response.indexOf("quality_score") + 15;
                int end = response.indexOf(",", start);
                if (end < 0) end = response.indexOf("}", start);
                if (end > start) {
                    String scoreStr = response.substring(start, end).trim().replaceAll("[^0-9.]", "");
                    score = Double.parseDouble(scoreStr);
                }
            }
            
            if (response.contains("feedback_type")) {
                int start = response.indexOf("feedback_type") + 14;
                int end = response.indexOf("}", start);
                if (end < 0) end = response.length();
                String type = response.substring(start, end).trim().replaceAll("[\":,]", "").toUpperCase();
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
