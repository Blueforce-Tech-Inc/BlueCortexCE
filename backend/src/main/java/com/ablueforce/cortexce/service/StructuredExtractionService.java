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

        // Resolve userId once (avoids N queries when iterating templates)
        String userId = resolveUserId(sessionId);

        for (TemplateConfig template : extractionConfig.getTemplates()) {
            if (!template.isEnabled()) continue;
            try {
                List<ObservationEntity> filtered = filterBySource(observations, template.getSourceFilter());
                if (filtered.isEmpty()) continue;

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
     * Uses append-only extraction when prior data exists (Section 24.6).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractByTemplate(TemplateConfig template,
                                                   List<ObservationEntity> candidates,
                                                   String priorJson) {
        log.debug("Calling LLM for template '{}' with {} candidates", template.getName(), candidates.size());

        // Section 24.6: Use append-only extraction when prior data exists
        if (priorJson != null && !priorJson.isBlank()) {
            return extractAppendOnly(template, candidates, priorJson);
        }

        // First extraction (no prior) — use full-state extraction
        String systemPrompt = buildSystemPrompt(template);
        String userPrompt = buildUserPrompt(candidates, null);

        if (Map.class.getName().equals(template.getTemplateClass()) || "java.util.Map".equals(template.getTemplateClass())) {
            String fullSystemPrompt = systemPrompt;
            if (template.getOutputSchema() != null && !template.getOutputSchema().isBlank()) {
                fullSystemPrompt += "\n\nRespond with JSON matching this schema:\n" + template.getOutputSchema();
            }
            fullSystemPrompt += "\n\nRespond ONLY with valid JSON. No markdown, no explanation.";

            String response = llmService.chatCompletion(fullSystemPrompt, userPrompt);
            return parseJsonResponse(response);
        } else {
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
     * Section 24.6: Append-only extraction with explicit removal.
     * LLM only outputs add/remove/keep_hint, service merges with full prior from DB.
     * This prevents silent data loss from truncation while keeping token costs low.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAppendOnly(TemplateConfig template,
                                                   List<ObservationEntity> candidates,
                                                   String priorJson) {
        String systemPrompt = buildAppendOnlySystemPrompt(template);
        String userPrompt = buildAppendOnlyUserPrompt(candidates);

        String fullSystemPrompt = systemPrompt;
        if (template.getOutputSchema() != null && !template.getOutputSchema().isBlank()) {
            fullSystemPrompt += "\n\nEach item in add/remove/keep_hint arrays should match this structure:\n"
                + template.getOutputSchema();
        }
        fullSystemPrompt += "\n\nRespond ONLY with valid JSON. No markdown, no explanation.";

        String response = llmService.chatCompletion(fullSystemPrompt, userPrompt);
        Map<String, Object> appendResult = parseJsonResponse(response);

        // Merge with full prior from DB
        Map<String, Object> fullPriorData;
        try {
            fullPriorData = MAPPER.readValue(priorJson, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse prior JSON, treating as empty: {}", e.getMessage());
            fullPriorData = new HashMap<>();
        }

        return mergeAppendOnly(appendResult, fullPriorData, template);
    }

    /**
     * Build system prompt for append-only extraction (Section 24.6).
     *
     * <p>Each item in add/remove/keep_hint arrays MUST include a "_field" hint
     * indicating which top-level list field the item belongs to. This enables
     * correct merge into multi-field schemas (e.g. separate "preferences" and
     * "allergies" lists).</p>
     */
    private String buildAppendOnlySystemPrompt(TemplateConfig template) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a structured information extraction assistant.\n");
        sb.append("Extract information from the following observations.\n\n");
        sb.append("TASK:\n").append(template.getPrompt()).append("\n\n");
        sb.append("OUTPUT FORMAT:\n");
        sb.append("{\n");
        sb.append("  \"add\": [ /* NEW items found in observations */ ],\n");
        sb.append("  \"remove\": [ /* items EXPLICITLY rejected (user said they don't like X) */ ],\n");
        sb.append("  \"keep_hint\": [ /* items mentioned positively that should be retained */ ]\n");
        sb.append("}\n\n");
        sb.append("RULES:\n");
        sb.append("- 'add': only items EXPLICITLY stated in observations\n");
        sb.append("- 'remove': only items EXPLICITLY rejected (e.g., 'I don't like X anymore')\n");
        sb.append("- 'keep_hint': items mentioned positively or in passing that should NOT be removed\n");
        sb.append("- Do NOT infer, generalize, or fabricate\n");
        sb.append("- Each item MUST have 'category' and 'value' fields (used for deduplication)\n");
        sb.append("- Each item MUST include '_field' indicating which list it belongs to\n");
        sb.append("  (e.g. {\"_field\": \"preferences\", \"category\": \"food\", \"value\": \"sushi\"})\n");
        sb.append("  If the schema has a single list, use its field name.\n");
        sb.append("  If the schema is flat (no lists), omit '_field'.\n");
        return sb.toString();
    }

    /**
     * Build user prompt for append-only extraction (no prior context).
     */
    private String buildAppendOnlyUserPrompt(List<ObservationEntity> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("Observations:\n");
        for (ObservationEntity obs : candidates) {
            sb.append(String.format("- [%s] %s\n  %s\n",
                obs.getSource() != null ? obs.getSource() : "unknown",
                obs.getTitle() != null ? obs.getTitle() : "",
                obs.getContent() != null ? obs.getContent() : ""));
        }
        return sb.toString();
    }

    /**
     * Merge append-only extraction result with full prior data from DB (Section 24.6).
     * This ensures no data loss from truncation while keeping LLM token costs low.
     *
     * <p>Items with a "_field" hint are routed to the specified list only.
     * Items without "_field" are removed from / added to ALL list fields
     * (legacy behavior for single-field schemas).</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeAppendOnly(Map<String, Object> appendResult,
                                                 Map<String, Object> fullPriorData,
                                                 TemplateConfig template) {
        List<Map<String, Object>> addItems = (List<Map<String, Object>>)
            appendResult.getOrDefault("add", List.of());
        List<Map<String, Object>> removeItems = (List<Map<String, Object>>)
            appendResult.getOrDefault("remove", List.of());
        List<Map<String, Object>> keepHint = (List<Map<String, Object>>)
            appendResult.getOrDefault("keep_hint", List.of());

        // Resolve key fields from template config (fallback: category + value)
        List<String> keyFields = resolveKeyFields(template);

        // Start with full prior
        Map<String, Object> merged = new HashMap<>(fullPriorData);

        // Build keep_hint key set (items that should NOT be removed)
        Set<String> keepHintKeys = keepHint.stream()
            .map(item -> buildItemKey(item, keyFields))
            .collect(Collectors.toSet());

        // Build remove key set, excluding keep_hint items (protect from removal)
        Set<String> removeKeys = removeItems.stream()
            .map(item -> buildItemKey(item, keyFields))
            .filter(key -> !keepHintKeys.contains(key))
            .collect(Collectors.toSet());

        if (!removeItems.isEmpty() && removeKeys.size() < removeItems.size()) {
            log.debug("Protected {} items from removal due to keep_hint",
                removeItems.size() - removeKeys.size());
        }

        // Partition remove/add items by _field hint
        Map<String, Set<String>> removeKeysByField = partitionKeysByField(removeItems, keyFields, removeKeys);
        Map<String, List<Map<String, Object>>> addItemsByField = partitionItemsByField(addItems);

        // Remove explicitly rejected items (minus keep_hint protected) from list fields
        for (Map.Entry<String, Object> entry : merged.entrySet()) {
            if (entry.getValue() instanceof List<?> list) {
                String fieldName = entry.getKey();
                Set<String> applicableRemoveKeys = getApplicableKeys(removeKeysByField, removeKeys, fieldName);
                List<Map<String, Object>> filtered = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?>) {
                        Map<String, Object> mapItem = (Map<String, Object>) item;
                        if (!applicableRemoveKeys.contains(buildItemKey(mapItem, keyFields))) {
                            filtered.add(mapItem);
                        }
                    }
                }
                entry.setValue(filtered);
            }
        }

        // Add new items to matching list fields, with deduplication
        for (Map.Entry<String, Object> entry : merged.entrySet()) {
            if (entry.getValue() instanceof List<?> existingList) {
                String fieldName = entry.getKey();
                List<Map<String, Object>> applicableAddItems = getApplicableAddItems(addItemsByField, addItems, fieldName);
                if (applicableAddItems.isEmpty()) continue;

                List<Map<String, Object>> combined = new ArrayList<>();
                for (Object item : existingList) {
                    if (item instanceof Map<?, ?>) {
                        combined.add((Map<String, Object>) item);
                    }
                }

                Set<String> existingKeys = combined.stream()
                    .map(item -> buildItemKey(item, keyFields))
                    .collect(Collectors.toSet());
                for (Map<String, Object> newItem : applicableAddItems) {
                    if (!existingKeys.contains(buildItemKey(newItem, keyFields))) {
                        combined.add(newItem);
                    }
                }
                entry.setValue(combined);
            }
        }

        // Store removal metadata for audit trail
        if (!removeItems.isEmpty()) {
            merged.put("removed", removeItems);
        }

        log.debug("Append-only merge: +{} add, -{} remove, {} keep_hint (keyFields={})",
            addItems.size(), removeItems.size(), keepHint.size(), keyFields);

        return merged;
    }

    /**
     * Partition remove keys by _field hint. Items without _field go to "__all__" bucket.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> partitionKeysByField(List<Map<String, Object>> items,
                                                           List<String> keyFields,
                                                           Set<String> allKeys) {
        Map<String, Set<String>> byField = new HashMap<>();
        for (Map<String, Object> item : items) {
            String field = (String) item.get("_field");
            String key = buildItemKey(item, keyFields);
            String bucket = (field != null && !field.isBlank()) ? field : "__all__";
            byField.computeIfAbsent(bucket, k -> new HashSet<>()).add(key);
        }
        return byField;
    }

    /**
     * Partition add items by _field hint. Items without _field go to "__all__" bucket.
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<Map<String, Object>>> partitionItemsByField(List<Map<String, Object>> items) {
        Map<String, List<Map<String, Object>>> byField = new HashMap<>();
        for (Map<String, Object> item : items) {
            String field = (String) item.get("_field");
            String bucket = (field != null && !field.isBlank()) ? field : "__all__";
            byField.computeIfAbsent(bucket, k -> new ArrayList<>()).add(item);
        }
        return byField;
    }

    /**
     * Get applicable remove keys for a field: items targeting this field + items targeting all fields.
     */
    private Set<String> getApplicableKeys(Map<String, Set<String>> byField, Set<String> allKeys, String fieldName) {
        Set<String> result = new HashSet<>();
        // Items explicitly targeting this field
        if (byField.containsKey(fieldName)) {
            result.addAll(byField.get(fieldName));
        }
        // Items without _field hint (apply to all)
        if (byField.containsKey("__all__")) {
            result.addAll(byField.get("__all__"));
        }
        // If no _field hints used at all, fall back to legacy behavior (apply to all)
        if (byField.isEmpty()) {
            result.addAll(allKeys);
        }
        return result;
    }

    /**
     * Get applicable add items for a field: items targeting this field + items targeting all fields.
     */
    private List<Map<String, Object>> getApplicableAddItems(Map<String, List<Map<String, Object>>> byField,
                                                             List<Map<String, Object>> allItems,
                                                             String fieldName) {
        List<Map<String, Object>> result = new ArrayList<>();
        // Items explicitly targeting this field
        if (byField.containsKey(fieldName)) {
            result.addAll(byField.get(fieldName));
        }
        // Items without _field hint (apply to all)
        if (byField.containsKey("__all__")) {
            result.addAll(byField.get("__all__"));
        }
        // If no _field hints used at all, fall back to legacy behavior (add to all)
        if (byField.isEmpty()) {
            result.addAll(allItems);
        }
        return result;
    }

    /**
     * Resolve key fields from template config. Falls back to [category, value] if not configured.
     */
    private List<String> resolveKeyFields(TemplateConfig template) {
        if (template.getKeyFields() != null && !template.getKeyFields().isEmpty()) {
            return template.getKeyFields();
        }
        return List.of("category", "value");
    }

    /**
     * Build deduplication key from item fields using configured key fields.
     * Used by mergeAppendOnly to prevent duplicate entries.
     */
    private String buildItemKey(Map<String, Object> item, List<String> keyFields) {
        StringBuilder key = new StringBuilder();
        boolean allEmpty = true;
        for (String field : keyFields) {
            Object val = item.get(field);
            String str = val != null ? val.toString() : "";
            if (!str.isEmpty()) allEmpty = false;
            if (key.length() > 0) key.append("::");
            key.append(str);
        }
        // Fallback: use full JSON if all key fields are empty (prevents "null::null" collisions)
        if (allEmpty) {
            try {
                return "hash::" + Integer.toHexString(MAPPER.writeValueAsString(item).hashCode());
            } catch (JsonProcessingException e) {
                return "hash::" + item.hashCode();
            }
        }
        return key.toString();
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
            // Ensure DLQ session exists (FK constraint)
            String dlqSessionId = "dlq:extraction";
            sessionRepository.findByContentSessionId(dlqSessionId)
                .orElseGet(() -> {
                    log.info("Creating DLQ session: {}", dlqSessionId);
                    com.ablueforce.cortexce.entity.SessionEntity session = new com.ablueforce.cortexce.entity.SessionEntity();
                    session.setContentSessionId(dlqSessionId);
                    session.setProjectPath(projectPath);
                    session.setStatus("dlq");
                    session.setStartedAtEpoch(System.currentTimeMillis());
                    return sessionRepository.save(session);
                });

            ObservationEntity dlq = new ObservationEntity();
            dlq.setContentSessionId(dlqSessionId);
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
            // Handle both ```json\n...\n``` and ```\n...\n``` (with or without language tag)
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
