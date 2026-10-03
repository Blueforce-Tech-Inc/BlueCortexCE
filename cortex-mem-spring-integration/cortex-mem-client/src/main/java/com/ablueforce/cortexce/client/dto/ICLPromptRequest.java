package com.ablueforce.cortexce.client.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Request for building an ICL (In-Context Learning) prompt from memory.
 *
 * @param task The current task to find relevant memories for
 * @param project Project path to scope the search
 * @param maxChars Maximum characters for the ICL prompt. If null, 0 or negative,
 *                 the backend applies its own default (~4000) and the field is
 *                 omitted from the wire format. Configure based on your model's
 *                 context window size (e.g., 8000-12000 for 128K models).
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
         * If not set — or set to 0 or a negative value — the field is omitted
         * from the wire format and the backend uses its own default.
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

    /**
     * Convert to the wire format expected by /api/memory/icl-prompt.
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
        // A non-positive maxChars is treated as "unset" and omitted, so the backend
        // applies its own default. The backend resolves the field as
        // maxChars != null ? Math.max(100, maxChars) : 4000 — it has no zero-means-
        // default branch, so sending an explicit 0 or a negative value would clamp
        // to 100 characters and silently truncate the injected memory context.
        // Measured live with one fixed task: omitting the field yields a 564-
        // character prompt, while both 0 and -5 yield 53.
        // Parity: the JS SDK (<= 0) and the Python SDK (max_chars > 0) apply the
        // same rule. Go does NOT — json:"maxChars,omitempty" omits 0 for the same
        // structural reason but has no way to express "negative means unset"
        // without a custom marshaller, so Go still puts a negative on the wire
        // and gets the 100-character clamp. Tracked as P2-31; do not describe the
        // four SDKs as agreeing until that is closed.
        if (maxChars != null && maxChars > 0) {
            map.put("maxChars", maxChars);
        }
        if (userId != null) {
            map.put("userId", userId);
        }
        return map;
    }
}
