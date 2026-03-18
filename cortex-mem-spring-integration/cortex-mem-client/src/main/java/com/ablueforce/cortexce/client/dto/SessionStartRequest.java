package com.ablueforce.cortexce.client.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Request to start a new session or resume an existing one.
 * Sends to POST /api/session/start.
 */
public record SessionStartRequest(
    String sessionId,
    String projectPath
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private String projectPath;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder projectPath(String projectPath) { this.projectPath = projectPath; return this; }

        public SessionStartRequest build() {
            return new SessionStartRequest(sessionId, projectPath);
        }
    }

    public Map<String, Object> toWireFormat() {
        var map = new HashMap<String, Object>();
        map.put("session_id", sessionId);
        map.put("project_path", projectPath);
        map.put("cwd", projectPath);
        return map;
    }
}
