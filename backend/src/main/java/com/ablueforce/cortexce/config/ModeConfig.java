package com.ablueforce.cortexce.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Mode configuration classes for the mode profile system.
 * <p>
 * Mode profiles define observation types, concepts, and prompts for different use cases.
 * Default mode is 'code' (software development). Other modes like 'email-investigation'
 * can be selected via CLAUDE_MEM_MODE setting.
 * <p>
 * Aligned with TS src/services/domain/types.ts
 */
public class ModeConfig {

    /**
     * Observation type definition (e.g., bugfix, feature, decision).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObservationType(
        String id,
        String label,
        String description,
        String emoji,
        String work_emoji
    ) {}

    /**
     * Observation concept definition (e.g., how-it-works, why-it-exists).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObservationConcept(
        String id,
        String label,
        String description
    ) {}

    /**
     * Mode prompts configuration.
     * Contains all prompt templates for observation and summary generation.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModePrompts(
        String system_identity,
        String language_instruction,
        String spatial_awareness,
        String observer_role,
        String recording_focus,
        String skip_guidance,
        String type_guidance,
        String concept_guidance,
        String field_guidance,
        String output_format_header,
        String format_examples,
        String footer,

        // Observation XML placeholders
        String xml_title_placeholder,
        String xml_subtitle_placeholder,
        String xml_fact_placeholder,
        String xml_narrative_placeholder,
        String xml_concept_placeholder,
        String xml_file_placeholder,

        // Summary XML placeholders
        String xml_summary_request_placeholder,
        String xml_summary_investigated_placeholder,
        String xml_summary_learned_placeholder,
        String xml_summary_completed_placeholder,
        String xml_summary_next_steps_placeholder,
        String xml_summary_notes_placeholder,

        // Section headers
        String header_memory_start,
        String header_memory_continued,
        String header_summary_checkpoint,

        // Continuation prompts
        String continuation_greeting,
        String continuation_instruction,

        // Summary prompts
        String summary_instruction,
        String summary_context_label,
        String summary_format_instruction,
        String summary_footer
    ) {}

    /**
     * Complete mode configuration.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mode(
        String name,
        String description,
        String version,
        List<ObservationType> observation_types,
        List<ObservationConcept> observation_concepts,
        ModePrompts prompts
    ) {
        /**
         * Get observation type by ID.
         */
        public ObservationType getType(String typeId) {
            if (observation_types == null) return null;
            return observation_types.stream()
                .filter(t -> t.id().equals(typeId))
                .findFirst()
                .orElse(null);
        }

        /**
         * Get observation concept by ID.
         */
        public ObservationConcept getConcept(String conceptId) {
            if (observation_concepts == null) return null;
            return observation_concepts.stream()
                .filter(c -> c.id().equals(conceptId))
                .findFirst()
                .orElse(null);
        }

        /**
         * Get all observation type IDs.
         */
        public List<String> getTypeIds() {
            if (observation_types == null) return List.of();
            return observation_types.stream()
                .map(ObservationType::id)
                .toList();
        }

        /**
         * Get all observation concept IDs.
         */
        public List<String> getConceptIds() {
            if (observation_concepts == null) return List.of();
            return observation_concepts.stream()
                .map(ObservationConcept::id)
                .toList();
        }

        /**
         * Get emoji for a specific observation type.
         */
        public String getTypeEmoji(String typeId) {
            ObservationType type = getType(typeId);
            return type != null && type.emoji() != null ? type.emoji() : "📝";
        }

        /**
         * Get work emoji for a specific observation type.
         */
        public String getWorkEmoji(String typeId) {
            ObservationType type = getType(typeId);
            return type != null && type.work_emoji() != null ? type.work_emoji() : "📝";
        }

        /**
         * Get label for a specific observation type.
         */
        public String getTypeLabel(String typeId) {
            ObservationType type = getType(typeId);
            return type != null && type.label() != null ? type.label() : typeId;
        }

        /**
         * Validate that a type ID exists in this mode.
         */
        public boolean isValidType(String typeId) {
            return observation_types != null &&
                   observation_types.stream().anyMatch(t -> t.id().equals(typeId));
        }

        /**
         * Validate that a concept ID exists in this mode.
         */
        public boolean isValidConcept(String conceptId) {
            return observation_concepts != null &&
                   observation_concepts.stream().anyMatch(c -> c.id().equals(conceptId));
        }
    }

    private ModeConfig() {
        // Utility class - no instances
    }
}
