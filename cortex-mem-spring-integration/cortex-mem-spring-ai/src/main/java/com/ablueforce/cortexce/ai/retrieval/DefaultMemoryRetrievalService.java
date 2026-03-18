package com.ablueforce.cortexce.ai.retrieval;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.Experience;
import com.ablueforce.cortexce.client.dto.ExperienceRequest;
import com.ablueforce.cortexce.client.dto.ICLPromptRequest;
import com.ablueforce.cortexce.client.dto.QualityDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Default implementation that delegates to {@link CortexMemClient}.
 */
public class DefaultMemoryRetrievalService implements MemoryRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryRetrievalService.class);

    private final CortexMemClient client;
    private final int defaultCount;

    public DefaultMemoryRetrievalService(CortexMemClient client, int defaultCount) {
        this.client = client;
        this.defaultCount = defaultCount;
    }

    @Override
    public List<Experience> retrieveExperiences(String currentTask, String projectPath, int count) {
        return client.retrieveExperiences(ExperienceRequest.builder()
            .task(currentTask)
            .project(projectPath)
            .count(count > 0 ? count : defaultCount)
            .build());
    }

    @Override
    public String buildICLPrompt(String currentTask, String projectPath) {
        var result = client.buildICLPrompt(ICLPromptRequest.builder()
            .task(currentTask)
            .project(projectPath)
            .build());
        return result.prompt();
    }

    @Override
    public QualityDistribution getQualityDistribution(String projectPath) {
        return client.getQualityDistribution(projectPath);
    }
}
