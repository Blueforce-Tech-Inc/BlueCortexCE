package com.ablueforce.cortexce.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Result from the ICL prompt builder endpoint.
 * experienceCount is an integer matching the backend wire format and Go SDK.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ICLPromptResult(
    String prompt,
    int experienceCount
) {
    /**
     * Convenience constructor for fallback/default values.
     */
    public ICLPromptResult(String prompt) {
        this(prompt, 0);
    }
}
