package com.ablueforce.cortexce.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * Experience retrieved from the Cortex CE memory system.
 * Mirrors {@code ExpRagService.Experience} on the backend.
 * <p>
 * The backend uses global SNAKE_CASE Jackson naming strategy,
 * so fields are mapped via @JsonProperty (snake_case) with @JsonAlias (camelCase fallback).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Experience(
    String id,
    String task,
    String strategy,
    String outcome,
    @JsonProperty("reuse_condition") @JsonAlias("reuseCondition") String reuseCondition,
    @JsonProperty("quality_score") @JsonAlias("qualityScore") float qualityScore,
    @JsonProperty("created_at") @JsonAlias("createdAt") OffsetDateTime createdAt
) {}
