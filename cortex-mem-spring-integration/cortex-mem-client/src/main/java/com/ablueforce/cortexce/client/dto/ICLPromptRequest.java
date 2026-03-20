package com.ablueforce.cortexce.client.dto;

/**
 * Request for building an ICL (In-Context Learning) prompt from memory.
 * 
 * @param task The current task to find relevant memories for
 * @param project Project path to scope the search
 * @param maxChars Maximum characters for the ICL prompt (default 4000).
 *                 If the combined memory context exceeds this, it will be truncated.
 *                 Configure based on your model's context window size.
 */
public record ICLPromptRequest(
    String task,
    String project,
    Integer maxChars
) {
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience constructor with default maxChars (4000).
     */
    public ICLPromptRequest(String task, String project) {
        this(task, project, 4000);
    }

    public static class Builder {
        private String task;
        private String project;
        private Integer maxChars = 4000;

        public Builder task(String task) { this.task = task; return this; }
        public Builder project(String project) { this.project = project; return this; }
        
        /**
         * Set maximum characters for the ICL prompt.
         * Configure based on your model's context window.
         * For 128K context models: 8000-12000
         * For 32K context models: 4000-6000
         * For 8K context models: 2000-3000
         */
        public Builder maxChars(Integer maxChars) { this.maxChars = maxChars; return this; }

        public ICLPromptRequest build() {
            return new ICLPromptRequest(task, project, maxChars);
        }
    }
}
