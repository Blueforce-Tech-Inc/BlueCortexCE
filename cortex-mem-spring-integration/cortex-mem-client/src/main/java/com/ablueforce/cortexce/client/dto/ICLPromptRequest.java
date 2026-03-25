package com.ablueforce.cortexce.client.dto;

/**
 * Request for building an ICL (In-Context Learning) prompt from memory.
 *
 * @param task The current task to find relevant memories for
 * @param project Project path to scope the search
 * @param maxChars Maximum characters for the ICL prompt. If null, the backend
 *                 decides the default. Configure based on your model's context
 *                 window size (e.g., 8000-12000 for 128K models).
 * @param userId Optional user ID for user-scoped memory retrieval
 */
public record ICLPromptRequest(
    String task,
    String project,
    Integer maxChars,
    String userId
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String task;
        private String project;
        private Integer maxChars; // null by default — let the backend decide
        private String userId;

        public Builder task(String task) { this.task = task; return this; }
        public Builder project(String project) { this.project = project; return this; }

        /**
         * Set maximum characters for the ICL prompt.
         * Configure based on your model's context window.
         * For 128K context models: 8000-12000
         * For 32K context models: 4000-6000
         * For 8K context models: 2000-3000
         *
         * If not set, the backend uses its own default.
         */
        public Builder maxChars(Integer maxChars) { this.maxChars = maxChars; return this; }

        /**
         * Set user ID for user-scoped memory retrieval.
         */
        public Builder userId(String userId) { this.userId = userId; return this; }

        public ICLPromptRequest build() {
            return new ICLPromptRequest(task, project, maxChars, userId);
        }
    }
}
