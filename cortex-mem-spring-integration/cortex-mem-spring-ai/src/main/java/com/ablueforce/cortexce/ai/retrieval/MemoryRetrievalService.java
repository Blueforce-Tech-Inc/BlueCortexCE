package com.ablueforce.cortexce.ai.retrieval;

import com.ablueforce.cortexce.client.dto.Experience;
import com.ablueforce.cortexce.client.dto.QualityDistribution;

import java.util.List;

/**
 * Memory retrieval service — fetches relevant historical experiences
 * from the Cortex CE memory system.
 */
public interface MemoryRetrievalService {

    /**
     * Retrieve experiences relevant to the given task description.
     *
     * @param currentTask current task / user query
     * @param projectPath project path (memory isolation scope)
     * @param count       max number of experiences to return
     * @return list of relevant experiences, empty on failure
     */
    List<Experience> retrieveExperiences(String currentTask, String projectPath, int count);

    /**
     * Retrieve experiences with additional filtering options (V14).
     *
     * @param currentTask      current task / user query
     * @param projectPath      project path (memory isolation scope)
     * @param count            max number of experiences to return
     * @param source           optional source filter (e.g., "tool_result", "user_statement")
     * @param requiredConcepts optional concepts that must be present
     * @param userId           optional user ID for user-scoped retrieval
     * @return list of relevant experiences, empty on failure
     */
    default List<Experience> retrieveExperiences(String currentTask, String projectPath, int count,
                                                  String source, List<String> requiredConcepts, String userId) {
        // Default: delegate to the basic method (filters are ignored)
        return retrieveExperiences(currentTask, projectPath, count);
    }

    /**
     * Build an In-Context Learning prompt that includes historical experiences.
     *
     * @param currentTask current task / user query
     * @param projectPath project path
     * @return formatted ICL prompt string, empty on failure
     */
    String buildICLPrompt(String currentTask, String projectPath);

    /**
     * Build an ICL prompt with customization options (V14).
     *
     * @param currentTask current task / user query
     * @param projectPath project path
     * @param maxChars    max characters for the prompt (null for backend default)
     * @param userId      optional user ID for user-scoped retrieval
     * @return formatted ICL prompt string, empty on failure
     */
    default String buildICLPrompt(String currentTask, String projectPath, Integer maxChars, String userId) {
        // Default: delegate to the basic method (options are ignored)
        return buildICLPrompt(currentTask, projectPath);
    }

    /**
     * Get memory quality distribution for a project.
     */
    QualityDistribution getQualityDistribution(String projectPath);
}
