package com.ablueforce.cortexce.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/**
 * Experience retrieved from the Cortex CE memory system.
 * Mirrors {@code ExpRagService.Experience} on the backend.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Experience(
    String id,
    String task,
    String strategy,
    String outcome,
    String reuseCondition,
    float qualityScore,
    OffsetDateTime createdAt
) {}
