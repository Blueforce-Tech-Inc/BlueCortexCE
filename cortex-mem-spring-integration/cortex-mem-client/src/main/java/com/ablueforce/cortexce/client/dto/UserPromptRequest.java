package com.ablueforce.cortexce.client.dto;

import java.util.HashMap;
import java.util.Map;

public record UserPromptRequest(
    String sessionId,
    String projectPath,
    String promptText,
    Integer promptNumber,
    String platformSource
) {
    public UserPromptRequest(String sessionId, String projectPath, String promptText, Integer promptNumber) {
        this(sessionId, projectPath, promptText, promptNumber, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private String projectPath;
        private String promptText;
        private Integer promptNumber;
        private String platformSource;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder projectPath(String projectPath) { this.projectPath = projectPath; return this; }
        public Builder promptText(String promptText) { this.promptText = promptText; return this; }
        public Builder promptNumber(Integer promptNumber) { this.promptNumber = promptNumber; return this; }
        public Builder platformSource(String platformSource) { this.platformSource = platformSource; return this; }

        public UserPromptRequest build() {
            return new UserPromptRequest(sessionId, projectPath, promptText, promptNumber, platformSource);
        }
    }

    public Map<String, Object> toWireFormat() {
        var map = new HashMap<String, Object>();
        if (sessionId != null) {
            map.put("session_id", sessionId);
        }
        if (promptText != null) {
            map.put("prompt_text", promptText);
        }
        if (projectPath != null) {
            map.put("cwd", projectPath);
        }
        if (promptNumber != null) {
            map.put("prompt_number", promptNumber);
        }
        if (platformSource != null) {
            map.put("platform_source", platformSource);
        }
        return map;
    }
}
