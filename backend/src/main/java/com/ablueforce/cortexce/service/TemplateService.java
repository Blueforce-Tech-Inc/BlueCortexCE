package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.common.LogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Template loading and management service.
 * <p>
 * Responsibilities:
 * - Load prompt templates from classpath
 * - Validate required placeholders
 * - Template escaping utilities
 *
 * @author Cortex CE Team
 */
@Service
public class TemplateService implements LogHelper {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    @Override
    public Logger getLogger() {
        return log;
    }

    // Runtime placeholders that MUST be present and replaced
    private static final Set<String> OBSERVATION_PLACEHOLDERS = Set.of(
        "{{toolName}}", "{{occurredAt}}", "{{cwd}}", "{{toolInput}}", "{{toolOutput}}"
    );
    private static final Set<String> CONTINUATION_PLACEHOLDERS = Set.of(
        "{{userPrompt}}", "{{date}}"
    );

    private String initPromptTemplate;
    private String observationPromptTemplate;
    private String summaryPromptTemplate;
    private String continuationPromptTemplate;

    @PostConstruct
    void loadPromptTemplates() {
        // Load prompts from synced resources directory
        // Prompts are synced from TS modes using: java/scripts/sync-prompts.sh
        initPromptTemplate = loadResource("prompts/init.txt");
        observationPromptTemplate = loadResource("prompts/observation.txt");
        summaryPromptTemplate = loadResource("prompts/summary.txt");
        continuationPromptTemplate = loadResource("prompts/continuation.txt");

        // Validate required placeholders (fail fast if missing)
        validatePlaceholders(observationPromptTemplate, "observation.txt", OBSERVATION_PLACEHOLDERS);
        validatePlaceholders(continuationPromptTemplate, "continuation.txt", CONTINUATION_PLACEHOLDERS);

        logSuccess("Prompt templates loaded and validated from synced TS resources");
    }

    private void validatePlaceholders(String template, String templateName, Set<String> required) {
        List<String> missing = new ArrayList<>();
        for (String placeholder : required) {
            if (!template.contains(placeholder)) {
                missing.add(placeholder);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Template '" + templateName + "' is missing required placeholders: " + missing
            );
        }
    }

    private String loadResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            logFailure("Failed to load prompt template: {}", path, e);
            throw new RuntimeException("Missing prompt template: " + path, e);
        }
    }

    // -------------------------------------------------------------------------
    // Getters for templates
    // -------------------------------------------------------------------------

    public String getInitPromptTemplate() {
        return initPromptTemplate;
    }

    public String getObservationPromptTemplate() {
        return observationPromptTemplate;
    }

    public String getSummaryPromptTemplate() {
        return summaryPromptTemplate;
    }

    public String getContinuationPromptTemplate() {
        return continuationPromptTemplate;
    }

    // -------------------------------------------------------------------------
    // Template utilities
    // -------------------------------------------------------------------------

    /**
     * Escape template placeholder syntax in user-controlled content.
     * P0: Prevents template injection attacks by escaping {{ and }} patterns.
     */
    public String escapeTemplateValue(String input) {
        if (input == null) return "";
        // Escape {{ and }} to prevent template injection
        return input
            .replace("{{", "&#123;&#123;")
            .replace("}}", "&#125;&#125;")
            .replace("{{{{", "&#123;&#123;&#123;&#123;")
            .replace("}}}}", "&#125;&#125;&#125;&#125;");
    }

    /**
     * Truncate a string to maxLen chars for prompt safety.
     */
    public String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "... [truncated]";
    }
}
