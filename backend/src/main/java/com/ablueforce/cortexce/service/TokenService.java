package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Token economics calculator.
 * <p>
 * Replicates the original TS formula exactly:
 * Token count = (title + subtitle + narrative + JSON.stringify(facts)) / 4
 * Only title, subtitle, narrative (content), and facts are counted.
 * concepts, files_read, files_modified are NOT counted.
 */
@Service
public class TokenService {

    private static final double CHARS_PER_TOKEN = 4.0;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ModeService modeService;

    public TokenService(ModeService modeService) {
        this.modeService = modeService;
    }

    /**
     * Calculate token count for a single observation.
     * Uses Jackson ObjectMapper for accurate JSON serialization of facts.
     */
    public int calculateObservationTokens(ObservationEntity obs) {
        long size = 0;
        size += obs.getTitle() != null ? obs.getTitle().length() : 0;
        size += obs.getSubtitle() != null ? obs.getSubtitle().length() : 0;
        size += obs.getContent() != null ? obs.getContent().length() : 0;
        // Use Jackson ObjectMapper to replicate TypeScript's JSON.stringify behavior
        // TypeScript: JSON.stringify(obs.facts || []) - when null, returns "[]" (2 chars)
        try {
            String factsJson = obs.getFacts() != null
                ? OBJECT_MAPPER.writeValueAsString(obs.getFacts())
                : "[]";
            size += factsJson.length();
        } catch (JsonProcessingException e) {
            // Fallback to empty string if serialization fails
            size += 2; // "[]"
        }
        // Clamp to Integer.MAX_VALUE to prevent overflow (use long literal to avoid overflow)
        size = Math.min(size, 2L * Integer.MAX_VALUE);
        return (int) Math.ceil(size / CHARS_PER_TOKEN);
    }

    /**
     * Calculate overall token economics for a list of observations.
     */
    public TokenEconomics calculateEconomics(List<ObservationEntity> observations) {
        int totalReadTokens = observations.stream()
            .mapToInt(this::calculateObservationTokens)
            .sum();

        int totalDiscoveryTokens = observations.stream()
            .mapToInt(obs -> obs.getDiscoveryTokens() != null ? obs.getDiscoveryTokens() : 0)
            .sum();

        int savings = totalDiscoveryTokens - totalReadTokens;
        double savingsPercent = totalDiscoveryTokens > 0
            ? Math.round((double) savings / totalDiscoveryTokens * 100)
            : 0;

        return new TokenEconomics(
            observations.size(),
            totalReadTokens,
            totalDiscoveryTokens,
            savings,
            savingsPercent
        );
    }

    /**
     * Get the display emoji for an observation type.
     * Uses ModeService to get emoji from the active mode configuration.
     * Falls back to defaults if mode is not available.
     */
    public String getWorkEmoji(String obsType) {
        if (modeService != null) {
            return modeService.getWorkEmoji(obsType);
        }
        // Fallback defaults (same as code.json)
        return switch (obsType) {
            case "bugfix" -> "🛠️";
            case "decision" -> "⚖️";
            case "feature" -> "🛠️";
            case "refactor" -> "🛠️";
            case "discovery" -> "🔍";
            case "change" -> "🛠️";
            default -> "📝";
        };
    }

    /**
     * Token economics data record.
     */
    public record TokenEconomics(
        int totalObservations,
        int totalReadTokens,
        int totalDiscoveryTokens,
        int savings,
        double savingsPercent
    ) {}
}
