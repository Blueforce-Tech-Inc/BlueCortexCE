package com.ablueforce.cortexce.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Response DTO for extraction API endpoints.
 * Corresponds to backend {@code GetLatestExtractionResponse}.
 *
 * @param status Extraction status: "ok" or "not_found"
 * @param template Template name used for extraction. Must match one of the
 *                 {@code app.memory.extraction.templates[].name} entries configured on the
 *                 backend; {@code user_preference} is the only one shipped today, spelled with
 *                 an underscore. An unrecognised name is not rejected — the backend echoes it
 *                 back with {@code status = "not_found"}, which is indistinguishable from
 *                 "nothing extracted yet" unless the caller compares the name.
 * @param sessionId Content session ID that produced this extraction
 * @param extractedData Extracted structured data (template-specific fields)
 * @param createdAt Creation timestamp (epoch milliseconds)
 * @param observationId Observation UUID containing the extraction
 * @param message Status message (present when status is "not_found")
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
