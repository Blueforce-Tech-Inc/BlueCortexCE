package com.ablueforce.cortexce.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * DTO for updating an existing observation.
 * V14: Supports source and extractedData fields.
 * <p>
 * Null fields are omitted from the JSON body (via @JsonInclude NON_NULL),
 * so PATCH requests only modify explicitly set fields.
 * <p>
 * The backend accepts both "content" and "narrative" as aliases for the observation body text.
 * This DTO supports both fields for cross-SDK consistency (Go/JS/Python use "narrative").
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ObservationUpdate(
    String title,
    String subtitle,
    String content,
    String narrative,
    List<String> facts,
    List<String> concepts,
    String source,
    Map<String, Object> extractedData
) {
    /**
     * Check if all fields are null (no update intended).
     * Used by the client to reject no-op PATCH requests.
     */
    public boolean isEmpty() {
        return title == null && subtitle == null && content == null &&
               narrative == null && facts == null && concepts == null &&
               source == null && extractedData == null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String title;
        private String subtitle;
        private String content;
        private String narrative;
        private List<String> facts;
        private List<String> concepts;
        private String source;
        private Map<String, Object> extractedData;

        public Builder title(String title) { this.title = title; return this; }
        public Builder subtitle(String subtitle) { this.subtitle = subtitle; return this; }
        public Builder content(String content) { this.content = content; return this; }

        /**
         * Set the narrative field. The backend accepts both "content" and "narrative"
         * as aliases. Use narrative() to match Go/JS/Python SDK convention.
         */
        public Builder narrative(String narrative) { this.narrative = narrative; return this; }
        public Builder facts(List<String> facts) { this.facts = facts; return this; }
        public Builder concepts(List<String> concepts) { this.concepts = concepts; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder extractedData(Map<String, Object> extractedData) { this.extractedData = extractedData; return this; }

        public ObservationUpdate build() {
            return new ObservationUpdate(title, subtitle, content, narrative, facts, concepts, source, extractedData);
        }
    }
}
