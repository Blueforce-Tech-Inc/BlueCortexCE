package com.ablueforce.cortexce.client.dto;

import java.util.HashMap;
import java.util.Map;

public record UserPromptRequest(
    String sessionId,
    String projectPath,
    String promptText,
    Integer promptNumber
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private String projectPath;
        private String promptText;
        private Integer promptNumber;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder projectPath(String projectPath) { this.projectPath = projectPath; return this; }
        public Builder promptText(String promptText) { this.promptText = promptText; return this; }
        public Builder promptNumber(Integer promptNumber) { this.promptNumber = promptNumber; return this; }

        public UserPromptRequest build() {
            return new UserPromptRequest(sessionId, projectPath, promptText, promptNumber);
        }
    }

    public Map<String, Object> toWireFormat() {
        var map = new HashMap<String, Object>();
        map.put("session_id", sessionId);
        map.put("prompt_text", promptText);
        map.put("cwd", projectPath);
        if (promptNumber != null) {
            map.put("prompt_number", promptNumber);
        }
        return map;
    }
}
