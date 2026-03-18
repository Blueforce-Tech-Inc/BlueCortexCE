package com.ablueforce.cortexce.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Result from the ICL prompt builder endpoint.
 * Note: backend returns experienceCount as a String.
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
