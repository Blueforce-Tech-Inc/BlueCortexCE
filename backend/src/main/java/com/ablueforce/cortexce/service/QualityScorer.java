package com.ablueforce.cortexce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for estimating memory quality scores.
 * 
 * Based on Evo-Memory paper Section 6.1.1 - Quality Scoring.
 * 
 * Quality score factors:
 * - Base score from feedback type (SUCCESS/PARTIAL/FAILURE/UNKNOWN)
 * - Efficiency bonus (fewer tool uses = higher score)
 * - Content quality bonus (length, structure)
 * - Optional LLM self-evaluation (when LlmQualityScorer is available)
 */
@Service
public class QualityScorer {

    private static final Logger log = LoggerFactory.getLogger(QualityScorer.class);

    // Base scores by feedback type
    private static final float SUCCESS_BASE = 0.75f;
    private static final float PARTIAL_BASE = 0.50f;
    private static final float FAILURE_BASE = 0.20f;
    private static final float UNKNOWN_BASE = 0.50f;

    // Efficiency bonus parameters
    private static final float EFFICIENCY_BONUS_MAX = 0.1f;
    private static final int EFFICIENCY_BASELINE_TOOLS = 3;
    private static final float EFFICIENCY_DECAY_PER_TOOL = 0.02f;

    // Content quality bonus parameters
    private static final float CONTENT_BONUS_MAX = 0.15f;
    private static final int MIN_CONTENT_LENGTH = 100;
    private static final int OPTIMAL_CONTENT_LENGTH = 500;
    
    // LLM-based scoring (optional)
    private final LlmQualityScorer llmQualityScorer;
    
    public QualityScorer() {
        this(null);
    }
    
    public QualityScorer(LlmQualityScorer llmQualityScorer) {
        this.llmQualityScorer = llmQualityScorer;
        if (llmQualityScorer != null) {
            log.info("QualityScorer initialized with LLM-based scoring");
        } else {
            log.info("QualityScorer initialized with rule-based scoring");
        }
    }

    /**
     * Feedback types for quality assessment.
     */
    public enum FeedbackType {
        SUCCESS,    // Task completed successfully
        PARTIAL,   // Task partially completed
        FAILURE,   // Task failed
        UNKNOWN    // No feedback information
    }

    /**
     * Estimate quality score based on available information.
     * 
     * @param feedback Feedback type (SUCCESS/PARTIAL/FAILURE/UNKNOWN)
     * @param reasoningTrace Reasoning process (optional, can be null)
     * @param output Agent output (optional, can be null)
     * @param toolUsageCount Number of tools used (optional, can be 0)
     * @return Quality score in range [0, 1]
     */
    public float estimateQuality(FeedbackType feedback,
                                  String reasoningTrace,
                                  String output,
                                  int toolUsageCount) {
        
        // 1. Base score from feedback type
        float baseScore = getBaseScore(feedback);
        
        // 2. Efficiency bonus (fewer tools = better efficiency)
        float efficiencyBonus = calculateEfficiencyBonus(toolUsageCount);
        
        // 3. Content quality bonus
        String content = (reasoningTrace != null ? reasoningTrace : "") + 
                         (output != null ? output : "");
        float contentBonus = calculateContentBonus(content);
        
        // 4. Calculate final score
        float finalScore = baseScore + efficiencyBonus + contentBonus;
        
        // Clamp to [0, 1] range
        finalScore = Math.max(0.0f, Math.min(1.0f, finalScore));
        
        log.debug("Quality score calculated: base={}, efficiency={}, content={}, final={}", 
                  baseScore, efficiencyBonus, contentBonus, finalScore);
        
        return finalScore;
    }

    /**
     * Simplified quality estimation without reasoning trace.
     */
    public float estimateQuality(FeedbackType feedback, int toolUsageCount) {
        return estimateQuality(feedback, null, null, toolUsageCount);
    }

    /**
     * Estimate quality using LLM for more accurate assessment.
     * 
     * This is the preferred method when LLM is available.
     * 
     * @param title Observation title
     * @param type Observation type
     * @param content Observation content
     * @param facts Facts extracted
     * @return Quality score in range [0, 1]
     */
    public float estimateQualityWithLlm(String title, String type, 
                                        String content, String facts) {
        // Check if LLM scorer is available
        if (llmQualityScorer == null || !llmQualityScorer.isAvailable()) {
            log.debug("LLM not available, falling back to rule-based scoring");
            return estimateQuality(FeedbackType.UNKNOWN, content, null, 0);
        }
        
        try {
            LlmQualityScorer.LlmQualityAnalysis analysis = 
                llmQualityScorer.analyzeQuality(title, type, content, facts);
            
            log.debug("LLM quality analysis: score={}, type={}", 
                analysis.qualityScore(), analysis.feedbackType());
            
            return (float) analysis.qualityScore();
            
        } catch (Exception e) {
            log.warn("LLM scoring failed, falling back to rule-based: {}", e.getMessage());
            return estimateQuality(FeedbackType.UNKNOWN, content, null, 0);
        }
    }

    /**
     * Infer feedback type using LLM.
     * 
     * @param sessionSummary Summary of the session
     * @param lastMessage Last assistant message
     * @param observationCount Number of observations
     * @return Inferred feedback type
     */
    public FeedbackType inferFeedbackWithLlm(String sessionSummary, 
                                             String lastMessage,
                                             int observationCount) {
        // Check if LLM scorer is available
        if (llmQualityScorer == null || !llmQualityScorer.isAvailable()) {
            return null; // Signal to use rule-based inference
        }
        
        try {
            LlmQualityScorer.FeedbackType llmFeedback = 
                llmQualityScorer.inferFeedbackLlm(sessionSummary, lastMessage, observationCount);
            
            if (llmFeedback == null) {
                return null; // Signal to use rule-based
            }
            
            // Convert to QualityScorer.FeedbackType
            return switch (llmFeedback) {
                case SUCCESS -> FeedbackType.SUCCESS;
                case FAILURE -> FeedbackType.FAILURE;
                case PARTIAL -> FeedbackType.PARTIAL;
            };
            
        } catch (Exception e) {
            log.warn("LLM feedback inference failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if LLM-based scoring is available.
     */
    public boolean isLlmAvailable() {
        return llmQualityScorer != null && llmQualityScorer.isAvailable();
    }

    /**
     * Recalculate quality with user feedback override.
     * Called when user provides explicit feedback via WebUI.
     */
    public float recalculateWithFeedback(FeedbackType feedback, String userComment) {
        float baseScore = getBaseScore(feedback);
        
        // User comment can provide additional context but doesn't significantly
        // alter the base quality - it's more for traceability
        float commentBonus = (userComment != null && !userComment.isEmpty()) 
                              ? 0.05f 
                              : 0.0f;
        
        return Math.max(0.0f, Math.min(1.0f, baseScore + commentBonus));
    }

    private float getBaseScore(FeedbackType feedback) {
        if (feedback == null) {
            return UNKNOWN_BASE;
        }
        
        return switch (feedback) {
            case SUCCESS -> SUCCESS_BASE;
            case PARTIAL -> PARTIAL_BASE;
            case FAILURE -> FAILURE_BASE;
            case UNKNOWN -> UNKNOWN_BASE;
        };
    }

    /**
     * Calculate efficiency bonus based on tool usage count.
     * Fewer tool uses indicates more efficient task completion.
     */
    private float calculateEfficiencyBonus(int toolUsageCount) {
        if (toolUsageCount <= 0) {
            return 0.0f;
        }
        
        // Tools used at or below baseline get max bonus
        if (toolUsageCount <= EFFICIENCY_BASELINE_TOOLS) {
            return EFFICIENCY_BONUS_MAX;
        }
        
        // Calculate decay
        int excessTools = toolUsageCount - EFFICIENCY_BASELINE_TOOLS;
        float decay = excessTools * EFFICIENCY_DECAY_PER_TOOL;
        
        return Math.max(0.0f, EFFICIENCY_BONUS_MAX - decay);
    }

    /**
     * Calculate content quality bonus based on reasoning/output length and structure.
     */
    private float calculateContentBonus(String content) {
        if (content == null || content.isEmpty()) {
            return 0.0f;
        }
        
        int length = content.length();
        
        // No content = no bonus
        if (length < MIN_CONTENT_LENGTH) {
            return 0.0f;
        }
        
        // Optimal length gets max bonus
        if (length >= OPTIMAL_CONTENT_LENGTH) {
            return CONTENT_BONUS_MAX;
        }
        
        // Linear interpolation between min and optimal
        float ratio = (float) (length - MIN_CONTENT_LENGTH) / 
                      (OPTIMAL_CONTENT_LENGTH - MIN_CONTENT_LENGTH);
        
        return CONTENT_BONUS_MAX * ratio;
    }

    /**
     * Parse feedback type from string (case-insensitive).
     */
    public FeedbackType parseFeedbackType(String feedbackType) {
        if (feedbackType == null || feedbackType.isEmpty()) {
            return FeedbackType.UNKNOWN;
        }
        
        try {
            return FeedbackType.valueOf(feedbackType.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown feedback type: {}", feedbackType);
            return FeedbackType.UNKNOWN;
        }
    }
}
