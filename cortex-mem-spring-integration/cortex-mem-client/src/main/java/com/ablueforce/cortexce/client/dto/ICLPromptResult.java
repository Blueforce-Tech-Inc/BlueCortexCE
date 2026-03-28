package com.ablueforce.cortexce.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result from the ICL prompt builder endpoint.
 * Mirrors the backend response fields: prompt, experienceCount, maxChars.
 * <p>
 * The backend uses SNAKE_CASE naming strategy but overrides with @JsonProperty (camelCase).
 * Use @JsonAlias for both snake_case and camelCase wire formats.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ICLPromptResult(
    String prompt,
    @JsonProperty("experienceCount") @JsonAlias("experience_count") int experienceCount,
    @JsonProperty("maxChars") @JsonAlias("max_chars") int maxChars
) {
    /**
     * Convenience constructor for fallback/default values (without maxChars).
     */
    public ICLPromptResult(String prompt, int experienceCount) {
        this(prompt, experienceCount, 0);
    }

    /**
     * Convenience constructor for minimal fallback values.
     */
    public ICLPromptResult(String prompt) {
        this(prompt, 0, 0);
    }
}
