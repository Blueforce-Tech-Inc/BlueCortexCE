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
     * Build an In-Context Learning prompt that includes historical experiences.
     *
     * @param currentTask current task / user query
     * @param projectPath project path
     * @return formatted ICL prompt string, empty on failure
     */
    String buildICLPrompt(String currentTask, String projectPath);

    /**
     * Get memory quality distribution for a project.
     */
    QualityDistribution getQualityDistribution(String projectPath);
}
