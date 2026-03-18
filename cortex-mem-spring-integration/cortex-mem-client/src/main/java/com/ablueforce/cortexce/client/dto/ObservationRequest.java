package com.ablueforce.cortexce.client.dto;

import java.util.Map;

public record ObservationRequest(
    String sessionId,
    String projectPath,
    String toolName,
    Object toolInput,
    Object toolResponse,
    Integer promptNumber
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private String projectPath;
        private String toolName;
        private Object toolInput;
        private Object toolResponse;
        private Integer promptNumber;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder projectPath(String projectPath) { this.projectPath = projectPath; return this; }
        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder toolInput(Object toolInput) { this.toolInput = toolInput; return this; }
        public Builder toolResponse(Object toolResponse) { this.toolResponse = toolResponse; return this; }
        public Builder promptNumber(Integer promptNumber) { this.promptNumber = promptNumber; return this; }

        public ObservationRequest build() {
            return new ObservationRequest(sessionId, projectPath, toolName, toolInput, toolResponse, promptNumber);
        }
    }

    /**
     * Convert to the wire format expected by /api/ingest/tool-use
     */
    public Map<String, Object> toWireFormat() {
        var map = new java.util.HashMap<String, Object>();
        map.put("session_id", sessionId);
        map.put("tool_name", toolName);
        map.put("tool_input", toolInput);
        map.put("tool_response", toolResponse);
        map.put("cwd", projectPath);
        if (promptNumber != null) {
            map.put("prompt_number", promptNumber);
        }
        return map;
    }
}
