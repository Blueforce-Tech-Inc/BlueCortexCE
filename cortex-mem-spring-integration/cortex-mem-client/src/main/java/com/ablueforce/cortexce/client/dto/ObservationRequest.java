package com.ablueforce.cortexce.client.dto;

import java.util.Map;

public record ObservationRequest(
    String sessionId,
    String projectPath,
    String toolName,
    Object toolInput,
    Object toolResponse,
    Integer promptNumber,
    // V14: source attribution — accepted by the backend, but the tool-use path
    // (POST /api/ingest/tool-use) never reads it, so it is discarded. Persist it
    // with updateObservation (PATCH /api/memory/observations/{id}), which does.
    String source,
    // V14: structured key-value data — same story as source above: accepted and
    // discarded by the tool-use path; updateObservation persists it.
    Map<String, Object> extractedData,
    String platformSource
) {
    public ObservationRequest(String sessionId, String projectPath, String toolName, Object toolInput,
                              Object toolResponse, Integer promptNumber, String source,
                              Map<String, Object> extractedData) {
        this(sessionId, projectPath, toolName, toolInput, toolResponse, promptNumber, source,
            extractedData, null);
    }

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
        private String platformSource;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder projectPath(String projectPath) { this.projectPath = projectPath; return this; }
        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder toolInput(Object toolInput) { this.toolInput = toolInput; return this; }
        public Builder toolResponse(Object toolResponse) { this.toolResponse = toolResponse; return this; }
        public Builder promptNumber(Integer promptNumber) { this.promptNumber = promptNumber; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder extractedData(Map<String, Object> extractedData) { this.extractedData = extractedData; return this; }
        public Builder platformSource(String platformSource) { this.platformSource = platformSource; return this; }

        public ObservationRequest build() {
            return new ObservationRequest(sessionId, projectPath, toolName, toolInput, toolResponse,
                promptNumber, source, extractedData, platformSource);
        }
    }

    /**
     * Convert to the wire format expected by /api/ingest/tool-use.
     * Only non-null fields are included (matches Go omitempty / Python conditional behavior).
     * <p>
     * The backend's tool-use handler reads only {@code session_id}, {@code cwd},
     * {@code tool_name}, {@code tool_input}, {@code tool_response} and
     * {@code platform_source}. {@code source}, {@code extractedData} and
     * {@code prompt_number} are accepted and then discarded on this endpoint —
     * use {@code updateObservation} (PATCH /api/memory/observations/{id}) to
     * persist them.
     */
    public Map<String, Object> toWireFormat() {
        var map = new java.util.HashMap<String, Object>();
        if (sessionId != null) {
            map.put("session_id", sessionId);
        }
        if (toolName != null) {
            map.put("tool_name", toolName);
        }
        if (projectPath != null) {
            map.put("cwd", projectPath);
        }
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
        if (platformSource != null) {
            map.put("platform_source", platformSource);
        }
        return map;
    }
}
