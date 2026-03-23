package com.ablueforce.cortexce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for building structured experience templates.
 * 
 * Based on Evo-Memory paper Section 6.1.3 - Experience Structured Templates.
 * 
 * Transforms raw observations into structured experiences with:
 * - Task description
 * - Reasoning process
 * - Action taken
 * - Outcome
 * - Key learnings
 * - Reuse conditions
 */
@Service
public class ExperienceTemplate {

    private static final Logger log = LoggerFactory.getLogger(ExperienceTemplate.class);

    /**
     * Build structured experience text from components.
     * Used when writing back observations to memory.
     */
    public String buildExperienceText(String taskInput,
                                     String reasoningTrace,
                                     String action,
                                     String outcome,
                                     List<String> keyLearnings) {
        
        StringBuilder sb = new StringBuilder();
        
        sb.append("## Task\n");
        sb.append(taskInput != null ? taskInput : "N/A");
        sb.append("\n\n");
        
        sb.append("## Reasoning Process\n");
        sb.append(reasoningTrace != null ? reasoningTrace : "N/A");
        sb.append("\n\n");
        
        sb.append("## Action Taken\n");
        sb.append(action != null ? action : "N/A");
        sb.append("\n\n");
        
        sb.append("## Outcome\n");
        sb.append(outcome != null ? outcome : "N/A");
        sb.append("\n\n");
        
        sb.append("## Key Learnings\n");
        if (keyLearnings != null && !keyLearnings.isEmpty()) {
            for (String learning : keyLearnings) {
                sb.append("- ").append(learning).append("\n");
            }
        } else {
            sb.append("No explicit learnings recorded.\n");
        }
        sb.append("\n");
        
        // Auto-generate reuse conditions
        sb.append("## When to Reuse\n");
        sb.append(generateReuseCondition(taskInput, action, outcome));
        
        return sb.toString();
    }

    /**
     * Build a simple structured experience from a single observation.
     */
    public String buildSimpleExperience(String content, String title) {
        return buildExperienceText(
            title,                      // task
            extractReasoning(content),  // reasoning
            extractAction(content),     // action
            extractOutcome(content),   // outcome
            extractLearnings(content)  // learnings
        );
    }

    /**
     * Generate reuse condition based on task/action/outcome.
     * This is a simple heuristic - could be enhanced with LLM.
     */
    private String generateReuseCondition(String task, String action, String outcome) {
        if (task == null && action == null) {
            return "General task - use when similar workflow is needed.\n";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("This experience is useful when:\n");
        
        // Extract keywords from task
        if (task != null && task.length() > 10) {
            sb.append("- Task involves: ").append(truncate(task, 100)).append("\n");
        }
        
        // Action-based conditions
        if (action != null) {
            if (action.contains("file") || action.contains("File")) {
                sb.append("- File operations are involved\n");
            }
            if (action.contains("debug") || action.contains("error")) {
                sb.append("- Debugging or error resolution is needed\n");
            }
            if (action.contains("test") || action.contains("Test")) {
                sb.append("- Testing or verification is required\n");
            }
            if (action.contains("refactor") || action.contains("Refactor")) {
                sb.append("- Code refactoring is needed\n");
            }
        }
        
        // Outcome-based conditions
        if (outcome != null) {
            if (outcome.toLowerCase().contains("success")) {
                sb.append("- Successful task completion pattern\n");
            }
            if (outcome.toLowerCase().contains("fail")) {
                sb.append("- Avoiding failure patterns\n");
            }
        }
        
        return sb.toString();
    }

    /**
     * Extract reasoning from content.
     */
    private String extractReasoning(String content) {
        if (content == null) return null;
        
        // Try to extract reasoning section
        int reasoningStart = content.indexOf("## Reasoning");
        if (reasoningStart >= 0) {
            int sectionEnd = content.indexOf("\n##", reasoningStart + 2);
            if (sectionEnd < 0) sectionEnd = content.length();
            return content.substring(reasoningStart + 12, sectionEnd).trim();
        }
        
        // Fallback: use first part of content
        return truncate(content, 500);
    }

    /**
     * Extract action from content.
     */
    private String extractAction(String content) {
        if (content == null) return null;
        
        int actionStart = content.indexOf("## Action");
        if (actionStart >= 0) {
            int sectionEnd = content.indexOf("\n##", actionStart + 2);
            if (sectionEnd < 0) sectionEnd = content.length();
            return content.substring(actionStart + 10, sectionEnd).trim();
        }
        
        return null;
    }

    /**
     * Extract outcome from content.
     */
    private String extractOutcome(String content) {
        if (content == null) return null;
        
        int outcomeStart = content.indexOf("## Outcome");
        if (outcomeStart >= 0) {
            int sectionEnd = content.indexOf("\n##", outcomeStart + 2);
            if (sectionEnd < 0) sectionEnd = content.length();
            return content.substring(outcomeStart + 11, sectionEnd).trim();
        }
        
        return null;
    }

    /**
     * Extract key learnings from content.
     */
    private java.util.List<String> extractLearnings(String content) {
        java.util.List<String> learnings = new java.util.ArrayList<>();
        
        if (content == null) return learnings;
        
        int learningsStart = content.indexOf("## Learnings");
        if (learningsStart >= 0) {
            int sectionEnd = content.indexOf("\n##", learningsStart + 2);
            if (sectionEnd < 0) sectionEnd = content.length();
            
            String learningsSection = content.substring(learningsStart + 13, sectionEnd);
            
            // Parse bullet points
            for (String line : learningsSection.split("\n")) {
                line = line.trim();
                if (line.startsWith("-") || line.startsWith("*")) {
                    learnings.add(line.substring(1).trim());
                }
            }
        }
        
        return learnings;
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}
