package com.ablueforce.cortexce.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Response DTO for extraction API endpoints.
 * Corresponds to backend {@code GetLatestExtractionResponse}.
 *
 * @param status Extraction status: "ok" or "not_found"
 * @param template Template name used for extraction (e.g., "user-preferences")
 * @param sessionId Content session ID that produced this extraction
 * @param extractedData Extracted structured data (template-specific fields)
 * @param createdAt Creation timestamp (epoch milliseconds)
 * @param observationId Observation UUID containing the extraction
 * @param message Status message (present when status is "not_found")
 */
public record ExtractionResponse(
    @JsonProperty("status")
    String status,

    @JsonProperty("template")
    String template,

    @JsonProperty("sessionId")
    String sessionId,

    @JsonProperty("extractedData")
    Map<String, Object> extractedData,

    @JsonProperty("createdAt")
    Long createdAt,

    @JsonProperty("observationId")
    String observationId,

    @JsonProperty("message")
    String message
) {
    /**
     * Returns true if extraction was found (status = "ok").
     */
    public boolean isFound() {
        return "ok".equals(status);
    }
}
