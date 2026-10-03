package com.ablueforce.cortexce.client.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Request for retrieving relevant experiences from memory.
 *
 * @param task Task description to search for
 * @param project Project path to scope the search
 * @param count Number of experiences to retrieve
 * @param source Optional: filter by source attribution (e.g., "tool_result", "user_statement")
 * @param requiredConcepts Optional: filter to experiences containing these concepts/tags
 * @param userId Optional: user ID for user-scoped memory retrieval
 */
public record ExperienceRequest(
    String task,
    String project,
    Integer count,
    String source,
    List<String> requiredConcepts,
    String userId
) {
    /**
     * Enforces the same rule on every construction path.
     *
     * <p>Until this existed the check lived only in {@link Builder#count(Integer)}, so
     * {@code new ExperienceRequest("t", "/p", 0)} slipped past it and
     * {@link #toWireFormat()} emitted {@code "count": 0}. The backend answers
     * {@code count <= 0} with an empty list and HTTP 200
     * ({@code ExpRagService} short-circuits), so the caller received zero experiences
     * with no error — indistinguishable from a project that genuinely has none.
     * A compact constructor covers the canonical constructor, both convenience
     * constructors and the builder, so no path can bypass it.
     *
     * <p>{@code null} stays legal: {@link #toWireFormat()} maps it to the backend default
     * of 4. Only a non-positive value is rejected. No valid input changes on the wire.
     */
    public ExperienceRequest {
        requirePositiveCount(count);
    }

    private static void requirePositiveCount(Integer count) {
        if (count != null && count <= 0) {
            throw new IllegalArgumentException("count must be positive (got " + count + ")");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience constructor with basic fields.
     */
    public ExperienceRequest(String task, String project, Integer count) {
        this(task, project, count, null, null, null);
    }

    /**
     * Convenience constructor without userId.
     */
    public ExperienceRequest(String task, String project, Integer count, String source, List<String> requiredConcepts) {
        this(task, project, count, source, requiredConcepts, null);
    }

    public static class Builder {
        private String task;
        private String project;
        private Integer count = 4;
        private String source;
        private List<String> requiredConcepts;
        private String userId;

        public Builder task(String task) { this.task = task; return this; }
        public Builder project(String project) { this.project = project; return this; }
        /**
         * Number of experiences to retrieve. Must be positive; {@code null} means "let the
         * backend choose" and is sent as its default of 4. A non-positive value is rejected
         * here for early feedback, and again by the compact constructor, which is what
         * actually makes the rule hold for the direct constructors too.
         */
        public Builder count(Integer count) {
            requirePositiveCount(count);
            this.count = count;
            return this;
        }

        /**
         * Filter by source attribution (e.g., "tool_result", "user_statement", "llm_inference", "manual").
         */
        public Builder source(String source) { this.source = source; return this; }
        
        /**
         * Filter to experiences containing all of these concepts/tags.
         */
        public Builder requiredConcepts(List<String> requiredConcepts) { this.requiredConcepts = requiredConcepts; return this; }

        /**
         * Set user ID for user-scoped memory retrieval.
         */
        public Builder userId(String userId) { this.userId = userId; return this; }

        public ExperienceRequest build() {
            return new ExperienceRequest(task, project, count, source, requiredConcepts, userId);
        }
    }

    /**
     * Convert to the wire format expected by /api/memory/experiences.
     * Null/blank fields are omitted from the resulting map.
     */
    public Map<String, Object> toWireFormat() {
        var map = new HashMap<String, Object>();
        if (task != null) {
            map.put("task", task);
        }
        if (project != null && !project.isBlank()) {
            map.put("project", project);
        }
        map.put("count", count != null ? count : 4);
        if (source != null) {
            map.put("source", source);
        }
        if (requiredConcepts != null && !requiredConcepts.isEmpty()) {
            map.put("requiredConcepts", requiredConcepts);
        }
        if (userId != null) {
            map.put("userId", userId);
        }
        return map;
    }
}
