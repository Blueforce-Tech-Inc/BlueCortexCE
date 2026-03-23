package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.ExtractionConfig;
import com.ablueforce.cortexce.config.ExtractionConfig.TemplateConfig;
import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Structured information extraction service (Phase 3).
 *
 * <p>Implements prompt-driven, configuration-based extraction of structured data
 * from observations. Uses LLM re-extraction: each run includes prior results
 * as context so the LLM can add, remove, or preserve items.</p>
 *
 * <p>Key design principles:</p>
 * <ul>
 *   <li>Append-only storage — every extraction run creates a new ObservationEntity</li>
 *   <li>User-scoped — results grouped by session → user_id</li>
 *   <li>Generic — extraction logic is template-agnostic; templates define behavior</li>
 * </ul>
 */
@Service
public class StructuredExtractionService {

    private static final Logger log = LoggerFactory.getLogger(StructuredExtractionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExtractionConfig extractionConfig;
    private final ObservationRepository observationRepository;
    private final SessionRepository sessionRepository;
    private final LlmService llmService;

    public StructuredExtractionService(ExtractionConfig extractionConfig,
                                       ObservationRepository observationRepository,
                                       SessionRepository sessionRepository,
                                       LlmService llmService) {
        this.extractionConfig = extractionConfig;
        this.observationRepository = observationRepository;
        this.sessionRepository = sessionRepository;
        this.llmService = llmService;
    }

    // ==========================================================================
    // Public API
    // ==========================================================================

    /**
     * Run extraction for all enabled templates on a project.
     * Called by DeepRefine or manual trigger.
     */
    public void runExtraction(String projectPath) {
        if (!extractionConfig.isEnabled()) {
            log.debug("Extraction disabled, skipping");
            return;
        }
        if (!llmService.isAvailable()) {
            log.warn("LLM not available, skipping extraction");
            return;
        }

        log.info("Starting extraction for project: {}", projectPath);

        for (TemplateConfig template : extractionConfig.getTemplates()) {
            if (!template.isEnabled()) {
                log.debug("Template '{}' disabled, skipping", template.getName());
                continue;
            }
            try {
                runTemplateExtraction(projectPath, template);
            } catch (Exception e) {
                log.error("Extraction failed for template '{}': {}", template.getName(), e.getMessage(), e);
                storeDLQ(projectPath, template.getName(), e.getMessage());
            }
        }

        log.info("Extraction completed for project: {}", projectPath);
    }

    /**
     * Re-extract for a specific session (triggered by PATCH userId).
     */
    public void reExtractForSession(String sessionId, String projectPath) {
        if (!extractionConfig.isEnabled() || !llmService.isAvailable()) {
            return;
        }
        log.info("Re-extracting for session: {} (project: {})", sessionId, projectPath);

        List<ObservationEntity> observations = observationRepository
            .findByContentSessionIdOrderByCreatedAtEpochAsc(sessionId);

        if (observations.isEmpty()) {
            log.debug("No observations for session {}, skipping re-extraction", sessionId);
            return;
        }

        for (TemplateConfig template : extractionConfig.getTemplates()) {
            if (!template.isEnabled()) continue;
            try {
                List<ObservationEntity> filtered = filterBySource(observations, template.getSourceFilter());
                if (filtered.isEmpty()) continue;

                String userId = resolveUserId(sessionId);
                String targetSessionId = resolveSessionId(template.getSessionIdPattern(), projectPath, userId);
                String priorJson = fetchPriorJson(targetSessionId, template.getName());
                Map<String, Object> result = extractByTemplate(template, filtered, priorJson);
                storeExtractionResult(template, result, filtered, targetSessionId, projectPath);
            } catch (Exception e) {
                log.error("Re-extraction failed for session {} template {}: {}",
                    sessionId, template.getName(), e.getMessage());
            }
        }
    }

    /**
     * Get latest extraction result for a template + project + optional userId.
     */
    public Optional<ObservationEntity> getLatestExtraction(String projectPath, String templateName, String userId) {
        String type = "extracted_" + templateName;
        Optional<TemplateConfig> tmpl = findTemplate(templateName);
        String pattern = tmpl.map(TemplateConfig::getSessionIdPattern).orElse(null);
        String prefix = resolveSessionId(pattern, projectPath, userId != null ? userId : "__unknown__");

        List<ObservationEntity> results = observationRepository.findByContentSessionIdAndType(prefix, type, 1);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Get extraction history for a template + project + optional userId.
     */
    public List<ObservationEntity> getExtractionHistory(String projectPath, String templateName,
                                                         String userId, int limit) {
        String type = "extracted_" + templateName;
        Optional<TemplateConfig> tmpl = findTemplate(templateName);
        String pattern = tmpl.map(TemplateConfig::getSessionIdPattern).orElse(null);
        String prefix = resolveSessionId(pattern, projectPath, userId != null ? userId : "__unknown__");

        return observationRepository.findByContentSessionIdAndType(prefix, type, limit);
    }

    // ==========================================================================
    // Core extraction logic
    // ==========================================================================

    /**
     * Run extraction for a single template across all users in the project.
     */
    private void runTemplateExtraction(String projectPath, TemplateConfig template) {
        log.info("Running extraction template '{}' for project {}", template.getName(), projectPath);

        List<String> sources = template.getSourceFilter();
        if (sources == null || sources.isEmpty()) {
            sources = List.of("user_statement", "manual");
        }

        // Get candidates (all matching observations, up to limit)
        List<ObservationEntity> candidates = observationRepository.findBySourceIn(
            projectPath, sources, extractionConfig.getInitialRunMaxCandidates());

        if (candidates.isEmpty()) {
            log.debug("No candidates for template '{}'", template.getName());
            return;
        }

        log.info("Found {} candidates for template '{}'", candidates.size(), template.getName());

        // Group observations by user (via session → user_id)
        Map<String, List<ObservationEntity>> grouped = groupByUser(candidates);

        for (Map.Entry<String, List<ObservationEntity>> entry : grouped.entrySet()) {
            String userId = entry.getKey();
            List<ObservationEntity> userObs = entry.getValue();

            try {
                String targetSessionId = resolveSessionId(template.getSessionIdPattern(), projectPath, userId);
                String priorJson = fetchPriorJson(targetSessionId, template.getName());

                // Batch if too many observations
                int batchSize = extractionConfig.getMaxObservationsPerBatch();
                int maxTotal = batchSize * extractionConfig.getMaxBatchesPerTemplate();
                for (int i = 0; i < userObs.size() && i < maxTotal; i += batchSize) {
                    int end = Math.min(i + batchSize, userObs.size());
                    List<ObservationEntity> batch = userObs.subList(i, end);

                    Map<String, Object> result = extractByTemplate(template, batch, priorJson);
                    storeExtractionResult(template, result, batch, targetSessionId, projectPath);

                    // After first batch, use latest result as prior
                    priorJson = toJson(result);
                }

                log.info("Extraction completed for user '{}' template '{}'", userId, template.getName());
            } catch (Exception e) {
                log.error("Extraction failed for user '{}' template '{}': {}",
                    userId, template.getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * Group observations by user ID (via session lookup).
     * Sessions without userId get "__unknown__" key.
     * Uses batch lookup to avoid N+1 queries.
     */
    private Map<String, List<ObservationEntity>> groupByUser(List<ObservationEntity> observations) {
        // Collect unique session IDs for batch lookup
        Set<String> sessionIds = observations.stream()
            .map(ObservationEntity::getContentSessionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // Batch-resolve userIds in a single query
        Map<String, String> sessionIdToUserId = sessionRepository.findByContentSessionIdIn(new ArrayList<>(sessionIds)).stream()
            .collect(Collectors.toMap(
                com.ablueforce.cortexce.entity.SessionEntity::getContentSessionId,
                s -> (s.getUserId() != null && !s.getUserId().isBlank()) ? s.getUserId() : "__unknown__"
            ));

        // Group observations by resolved userId
        Map<String, List<ObservationEntity>> grouped = new LinkedHashMap<>();
        for (ObservationEntity obs : observations) {
            String sessionId = obs.getContentSessionId();
            String userId = (sessionId != null) ? sessionIdToUserId.getOrDefault(sessionId, "__unknown__") : "__unknown__";
            grouped.computeIfAbsent(userId, k -> new ArrayList<>()).add(obs);
        }

        return grouped;
    }

    /**
     * Resolve userId from a single session. Returns "__unknown__" if not found.
     */
    private String resolveUserId(String sessionId) {
        if (sessionId == null) return "__unknown__";
        return sessionRepository.findByContentSessionId(sessionId)
            .map(s -> s.getUserId() != null && !s.getUserId().isBlank() ? s.getUserId() : "__unknown__")
            .orElse("__unknown__");
    }

    /**
     * Filter observations by source list. Returns all if sourceFilter is empty.
     */
    private List<ObservationEntity> filterBySource(List<ObservationEntity> obs, List<String> sourceFilter) {
        if (sourceFilter == null || sourceFilter.isEmpty()) return obs;
        return obs.stream()
            .filter(o -> o.getSource() != null && sourceFilter.contains(o.getSource()))
            .collect(Collectors.toList());
    }

    // ==========================================================================
    // LLM extraction
    // ==========================================================================

    /**
     * Call LLM with structured output to extract data from observations.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractByTemplate(TemplateConfig template,
                                                   List<ObservationEntity> candidates,
                                                   String priorJson) {
        String systemPrompt = buildSystemPrompt(template);
        String userPrompt = buildUserPrompt(candidates, priorJson);

        log.debug("Calling LLM for template '{}' with {} candidates", template.getName(), candidates.size());

        if (Map.class.getName().equals(template.getTemplateClass()) || "java.util.Map".equals(template.getTemplateClass())) {
            // Use Map output converter with JSON schema in prompt
            String fullSystemPrompt = systemPrompt;
            if (template.getOutputSchema() != null && !template.getOutputSchema().isBlank()) {
                fullSystemPrompt += "\n\nRespond with JSON matching this schema:\n" + template.getOutputSchema();
            }
            fullSystemPrompt += "\n\nRespond ONLY with valid JSON. No markdown, no explanation.";

            String response = llmService.chatCompletion(fullSystemPrompt, userPrompt);
            return parseJsonResponse(response);
        } else {
            // Use BeanOutputConverter with typed class
            try {
                Class<?> clazz = Class.forName(template.getTemplateClass());
                Object result = llmService.chatCompletionStructured(systemPrompt, userPrompt, (Class<Object>) clazz);
                if (result instanceof Map) {
                    return (Map<String, Object>) result;
                }
                return MAPPER.convertValue(result, Map.class);
            } catch (ClassNotFoundException e) {
                log.error("Template class not found: {}", template.getTemplateClass());
                return Map.of();
            }
        }
    }

    /**
     * Build system prompt for extraction.
     */
    private String buildSystemPrompt(TemplateConfig template) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a structured information extraction assistant.\n");
        sb.append("Extract information from the provided observations.\n\n");
        sb.append("TASK:\n").append(template.getPrompt()).append("\n\n");
        sb.append("RULES:\n");
        sb.append("- Return ALL extracted items, including items from prior extractions that are still valid\n");
        sb.append("- If prior items are contradicted by new observations, REMOVE them\n");
        sb.append("- If new observations add information, ADD new items\n");
        sb.append("- Be precise — do not hallucinate information not present in observations\n");
        return sb.toString();
    }

    /**
     * Build user prompt from observations + prior extraction.
     */
    private String buildUserPrompt(List<ObservationEntity> candidates, String priorJson) {
        StringBuilder sb = new StringBuilder();

        if (priorJson != null && !priorJson.isBlank()) {
            sb.append("PRIOR EXTRACTION (may need updating):\n");
            sb.append(priorJson).append("\n\n");
        }

        sb.append("NEW OBSERVATIONS:\n");
        for (int i = 0; i < candidates.size(); i++) {
            ObservationEntity obs = candidates.get(i);
            sb.append("[").append(i + 1).append("] ");
            if (obs.getContent() != null) {
                sb.append(obs.getContent());
            }
            if (obs.getSource() != null) {
                sb.append(" (source: ").append(obs.getSource()).append(")");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    // ==========================================================================
    // Storage
    // ==========================================================================

    /**
     * Store extraction result as a new ObservationEntity (append-only).
     */
    private void storeExtractionResult(TemplateConfig template,
                                        Map<String, Object> result,
                                        List<ObservationEntity> sourceObservations,
                                        String targetSessionId,
                                        String projectPath) {
        if (result == null || result.isEmpty()) {
            log.warn("Empty extraction result for template '{}', skipping storage", template.getName());
            return;
        }

        // Ensure target session exists (FK constraint: mem_observations.content_session_id → mem_sessions)
        sessionRepository.findByContentSessionId(targetSessionId)
            .orElseGet(() -> {
                log.info("Creating extraction session: {}", targetSessionId);
                com.ablueforce.cortexce.entity.SessionEntity session = new com.ablueforce.cortexce.entity.SessionEntity();
                session.setContentSessionId(targetSessionId);
                session.setProjectPath(projectPath);
                session.setStatus("extraction");
                session.setStartedAtEpoch(System.currentTimeMillis());
                return sessionRepository.save(session);
            });

        ObservationEntity extraction = new ObservationEntity();
        extraction.setContentSessionId(targetSessionId);
        extraction.setProjectPath(projectPath);
        extraction.setType("extracted_" + template.getName());
        extraction.setTitle("Extraction: " + template.getName());
        extraction.setContent("Structured extraction result for template: " + template.getName());
        extraction.setSource("llm_extraction");
        extraction.setExtractedData(result);
        extraction.setCreatedAt(Instant.now().atOffset(java.time.ZoneOffset.UTC));
        extraction.setCreatedAtEpoch(System.currentTimeMillis());
        extraction.setPromptNumber(0);
        extraction.setConcepts(List.of("extraction", template.getName()));

        // Link to source observations
        String sourceIds = sourceObservations.stream()
            .map(o -> o.getId().toString())
            .collect(Collectors.joining(","));
        extraction.setRefinedFromIds(sourceIds);

        observationRepository.save(extraction);
        log.info("Stored extraction result for template '{}' in session '{}' ({} source observations)",
            template.getName(), targetSessionId, sourceObservations.size());
    }

    /**
     * Store extraction failure in DLQ (dead letter queue).
     */
    private void storeDLQ(String projectPath, String templateName, String errorMsg) {
        try {
            ObservationEntity dlq = new ObservationEntity();
            dlq.setContentSessionId("dlq:extraction");
            dlq.setProjectPath(projectPath);
            dlq.setType("extraction_failed");
            dlq.setTitle("Extraction failed: " + templateName);
            dlq.setContent(errorMsg);
            dlq.setSource("system");
            dlq.setExtractedData(Map.of("template", templateName, "error", errorMsg));
            dlq.setCreatedAt(Instant.now().atOffset(java.time.ZoneOffset.UTC));
            dlq.setCreatedAtEpoch(System.currentTimeMillis());
            observationRepository.save(dlq);
        } catch (Exception e) {
            log.error("Failed to store DLQ entry: {}", e.getMessage());
        }
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    /**
     * Resolve session ID pattern with variable substitution.
     */
    private String resolveSessionId(String pattern, String projectPath, String userId) {
        if (pattern == null || pattern.isBlank()) {
            return "ext:" + hashProject(projectPath) + ":" + (userId != null ? userId : "__unknown__");
        }
        return pattern
            .replace("{project}", hashProject(projectPath))
            .replace("{userId}", userId != null ? userId : "__unknown__");
    }

    /**
     * Hash project path to a short, deterministic string.
     */
    private String hashProject(String projectPath) {
        if (projectPath == null) return "unknown";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(projectPath.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return projectPath.replaceAll("[^a-zA-Z0-9]", "_");
        }
    }

    /**
     * Fetch prior extraction JSON for re-extraction context.
     */
    private String fetchPriorJson(String targetSessionId, String templateName) {
        String type = "extracted_" + templateName;
        List<ObservationEntity> priors = observationRepository
            .findByContentSessionIdAndType(targetSessionId, type, 1);

        if (priors.isEmpty()) return null;

        Map<String, Object> data = priors.get(0).getExtractedData();
        return toJson(data);
    }

    /**
     * Parse JSON response from LLM, stripping markdown fences if present.
     * Throws IllegalStateException if parsing fails, so the caller can handle
     * the error via DLQ instead of silently storing invalid data.
     */
    private Map<String, Object> parseJsonResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("LLM returned empty response");
        }

        String json = response.trim();
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                json = json.substring(firstNewline + 1, lastFence).trim();
            }
        }

        try {
            return MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse LLM response as JSON: " + e.getMessage()
                + " | Response: " + (response.length() > 200 ? response.substring(0, 200) + "..." : response), e);
        }
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Optional<TemplateConfig> findTemplate(String name) {
        return extractionConfig.getTemplates().stream()
            .filter(t -> t.getName().equals(name))
            .findFirst();
    }
}
