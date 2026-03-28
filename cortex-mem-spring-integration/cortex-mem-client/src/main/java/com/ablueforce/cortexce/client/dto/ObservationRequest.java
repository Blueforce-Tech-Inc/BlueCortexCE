package com.ablueforce.cortexce.client.dto;

import java.util.Map;

public record ObservationRequest(
    String sessionId,
    String projectPath,
    String toolName,
    Object toolInput,
    Object toolResponse,
    Integer promptNumber,
    // V14: source attribution
    String source,
    // V14: structured key-value data
    Map<String, Object> extractedData
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
        private String source;
        private Map<String, Object> extractedData;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder projectPath(String projectPath) { this.projectPath = projectPath; return this; }
        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder toolInput(Object toolInput) { this.toolInput = toolInput; return this; }
        public Builder toolResponse(Object toolResponse) { this.toolResponse = toolResponse; return this; }
        public Builder promptNumber(Integer promptNumber) { this.promptNumber = promptNumber; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder extractedData(Map<String, Object> extractedData) { this.extractedData = extractedData; return this; }

        public ObservationRequest build() {
            return new ObservationRequest(sessionId, projectPath, toolName, toolInput, toolResponse, promptNumber, source, extractedData);
        }
    }

    /**
     * Convert to the wire format expected by /api/ingest/tool-use.
     * Only non-null fields are included (matches Go omitempty / Python conditional behavior).
     */
    public Map<String, Object> toWireFormat() {
        var map = new java.util.HashMap<String, Object>();
        map.put("session_id", sessionId);
        map.put("tool_name", toolName);
        map.put("cwd", projectPath);
        if (toolInput != null) {
            map.put("tool_input", toolInput);
        }
        if (toolResponse != null) {
            map.put("tool_response", toolResponse);
        }
        if (promptNumber != null) {
            map.put("prompt_number", promptNumber);
        }
        if (source != null) {
            map.put("source", source);
        }
        if (extractedData != null) {
            map.put("extractedData", extractedData);
        }
        return map;
    }
}
