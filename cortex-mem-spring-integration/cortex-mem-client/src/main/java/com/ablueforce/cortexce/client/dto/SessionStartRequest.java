package com.ablueforce.cortexce.client.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Request to start a new session or resume an existing one.
 * Sends to POST /api/session/start.
 */
public record SessionStartRequest(
    String sessionId,
    String projectPath,
    String userId
) {
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience constructor without userId (backward compatible).
     */
    public SessionStartRequest(String sessionId, String projectPath) {
        this(sessionId, projectPath, null);
    }

    public static class Builder {
        private String sessionId;
        private String projectPath;
        private String userId;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder projectPath(String projectPath) { this.projectPath = projectPath; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }

        public SessionStartRequest build() {
            return new SessionStartRequest(sessionId, projectPath, userId);
        }
    }

    public Map<String, Object> toWireFormat() {
        var map = new HashMap<String, Object>();
        map.put("session_id", sessionId);
        // Backend accepts project_path as primary field (falls back to cwd if absent).
        // Only send project_path to avoid redundant wire data (matches Go SDK behavior).
        map.put("project_path", projectPath);
        if (userId != null) {
            map.put("user_id", userId);
        }
        return map;
    }
}
