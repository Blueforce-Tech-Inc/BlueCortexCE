package com.ablueforce.cortexce.client.dto;

import java.util.HashMap;
import java.util.Map;

public record SessionEndRequest(
    String sessionId,
    String projectPath,
    String lastAssistantMessage
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private String projectPath;
        private String lastAssistantMessage;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder projectPath(String projectPath) { this.projectPath = projectPath; return this; }
        public Builder lastAssistantMessage(String msg) { this.lastAssistantMessage = msg; return this; }

        public SessionEndRequest build() {
            return new SessionEndRequest(sessionId, projectPath, lastAssistantMessage);
        }
    }

    public Map<String, Object> toWireFormat() {
        var map = new HashMap<String, Object>();
        map.put("session_id", sessionId);
        map.put("cwd", projectPath);
        if (lastAssistantMessage != null) {
            map.put("last_assistant_message", lastAssistantMessage);
        }
        return map;
    }
}
