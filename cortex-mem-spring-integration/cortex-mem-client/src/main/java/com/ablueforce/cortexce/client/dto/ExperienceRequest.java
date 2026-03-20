package com.ablueforce.cortexce.client.dto;

import java.util.List;

/**
 * Request for retrieving relevant experiences from memory.
 *
 * @param task Task description to search for
 * @param project Project path to scope the search
 * @param count Number of experiences to retrieve
 * @param source Optional: filter by source attribution (e.g., "tool_result", "user_statement")
 * @param requiredConcepts Optional: filter to experiences containing these concepts/tags
 */
public record ExperienceRequest(
    String task,
    String project,
    Integer count,
    String source,
    List<String> requiredConcepts
) {
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience constructor with basic fields.
     */
    public ExperienceRequest(String task, String project, Integer count) {
        this(task, project, count, null, null);
    }

    public static class Builder {
        private String task;
        private String project;
        private Integer count = 4;
        private String source;
        private List<String> requiredConcepts;

        public Builder task(String task) { this.task = task; return this; }
        public Builder project(String project) { this.project = project; return this; }
        public Builder count(Integer count) { this.count = count; return this; }
        
        /**
         * Filter by source attribution (e.g., "tool_result", "user_statement", "llm_inference", "manual").
         */
        public Builder source(String source) { this.source = source; return this; }
        
        /**
         * Filter to experiences containing all of these concepts/tags.
         */
        public Builder requiredConcepts(List<String> requiredConcepts) { this.requiredConcepts = requiredConcepts; return this; }

        public ExperienceRequest build() {
            return new ExperienceRequest(task, project, count, source, requiredConcepts);
        }
    }
}
