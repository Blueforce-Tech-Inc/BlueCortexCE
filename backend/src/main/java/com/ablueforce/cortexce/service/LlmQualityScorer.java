package com.ablueforce.cortexce.service;

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

    private final LlmService llmService;
    
    private static final String QUALITY_ANALYSIS_PROMPT = """
        Analyze this observation and provide a quality score.
        
        Title: %s
        Type: %s
        Content: %s
        Facts: %s
        
        Respond in JSON format:
        {"quality_score": 0.0-1.0, "feedback_type": "SUCCESS|PARTIAL|FAILURE", "reasoning": "..."}
        """;

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
     */
    public FeedbackType inferFeedbackLlm(String sessionSummary, 
                                          String lastMessage,
                                          int observationCount) {
        if (llmService == null) {
            log.warn("LlmService not available, using rule-based inference");
            return null;
        }
        
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
            
            String response = llmService.chatCompletion(
                "You are a session outcome analyzer.", 
                prompt
            );
            
            if (response == null) return null;
            
            String trimmed = response.trim().toUpperCase();
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
            
            if (response != null) {
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
            }
            
            return new LlmQualityAnalysis(score, feedbackType, "");
            
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
