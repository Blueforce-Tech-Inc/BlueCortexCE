package com.ablueforce.cortexce.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Result from the ICL prompt builder endpoint.
 * Note: experienceCount is stored as String for backward compatibility.
 * The backend returns it as an integer (Jackson auto-converts to String).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ICLPromptResult(
    String prompt,
    String experienceCount
) {
    public int experienceCountAsInt() {
        try {
            return Integer.parseInt(experienceCount);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
