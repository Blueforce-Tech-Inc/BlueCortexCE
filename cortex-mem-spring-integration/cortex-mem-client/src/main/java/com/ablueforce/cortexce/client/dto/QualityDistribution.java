package com.ablueforce.cortexce.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Memory quality distribution from the backend.
 * Matches the flat response format: {"project":"...", "high":0, "medium":0, "low":0, "unknown":0}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QualityDistribution(
    String project,
    long high,
    long medium,
    long low,
    long unknown
) {
    public long total() {
        return high + medium + low + unknown;
    }
}
