package com.ablueforce.cortexce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for structured information extraction (Phase 3).
 *
 * <p>Extraction templates define what to extract from observations,
 * how to group results, and which LLM prompt to use.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "app.memory.extraction")
public class ExtractionConfig {

    /** Whether extraction is enabled. */
    private boolean enabled = false;

    /** Max candidates per first-run extraction batch. */
    private int initialRunMaxCandidates = 100;

    /** Max observations per LLM prompt batch. */
    private int maxObservationsPerBatch = 20;

    /** Max batches per template (safety limit). */
    private int maxBatchesPerTemplate = 10;

    /** Extraction templates. */
    private List<TemplateConfig> templates = new ArrayList<>();

    // --- Getters / Setters ---

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getInitialRunMaxCandidates() { return initialRunMaxCandidates; }
    public void setInitialRunMaxCandidates(int v) { this.initialRunMaxCandidates = v; }

    public int getMaxObservationsPerBatch() { return maxObservationsPerBatch; }
    public void setMaxObservationsPerBatch(int v) { this.maxObservationsPerBatch = v; }

    public int getMaxBatchesPerTemplate() { return maxBatchesPerTemplate; }
    public void setMaxBatchesPerTemplate(int v) { this.maxBatchesPerTemplate = v; }

    public List<TemplateConfig> getTemplates() { return templates; }
    public void setTemplates(List<TemplateConfig> templates) { this.templates = templates; }

    // --- Inner class: single template ---

    public static class TemplateConfig {
        /** Unique template name, e.g. "user_preference". */
        private String name;
        /** Whether this template is active. */
        private boolean enabled = true;
        /** Java class name for BeanOutputConverter, e.g. "java.util.Map". */
        private String templateClass = "java.util.Map";
        /**
         * Session ID pattern for storing results.
         * Variables: {project} (hashed), {userId}
         * Example: "pref:{project}:{userId}"
         */
        private String sessionIdPattern;
        /** Only extract from observations with these source values. */
        private List<String> sourceFilter = new ArrayList<>();
        /** LLM system prompt for extraction. */
        private String prompt;
        /** JSON Schema string for structured output (used with Map.class). */
        private String outputSchema;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getTemplateClass() { return templateClass; }
        public void setTemplateClass(String tc) { this.templateClass = tc; }

        public String getSessionIdPattern() { return sessionIdPattern; }
        public void setSessionIdPattern(String p) { this.sessionIdPattern = p; }

        public List<String> getSourceFilter() { return sourceFilter; }
        public void setSourceFilter(List<String> sf) { this.sourceFilter = sf; }

        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }

        public String getOutputSchema() { return outputSchema; }
        public void setOutputSchema(String s) { this.outputSchema = s; }
    }
}
