# Phase 3 Design Proposal: Structured Information Extraction & Memory Conflict Detection

**Date**: 2026-03-22
**Status**: Design proposal - iteration 15 (Implementation readiness, cost control, API spec, conflict resolution)
**Related to**: `sdk-improvement-research.md` Phase 3 deferred items

---

## Quick Reference (TL;DR)

**What**: Generic, prompt-driven structured information extraction from observations.
**How**: YAML templates define what to extract + output schema. Code is generic.
**Storage**: Results stored as `ObservationEntity` with `type="extracted_{template}"` + `extractedData` JSONB.
**When**: Last step of `deepRefineProjectMemories()` (non-blocking) or scheduled daily.
**Prerequisites**: 4 new methods (findBySourceIn, findNewObservations, findByTypeGlobal, chatCompletionStructured).
**Key insight**: `BeanOutputConverter<T>` needs Java `Class<T>`, not JSON Schema string. Use `templateClass` field.

```
┌─────────────────────────────────────────────────────────┐
│ Extraction Pipeline (per template per project)          │
├─────────────────────────────────────────────────────────┤
│ 1. Get incremental candidates (since last extraction)   │
│ 2. Chunk by token count (respect context window)        │
│ 3. Build prompt (template.prompt + candidate data)      │
│ 4. Call LLM via BeanOutputConverter<T> (schema-enforced)│
│ 5. Validate result → store as ObservationEntity         │
│ 6. Update extraction state (transactional)              │
│ 7. On failure → DLQ (type=extraction_failed)            │
└─────────────────────────────────────────────────────────┘
```

---

## 0. Design Correction: Generalized Approach

**Issue in v1**: Named "Preference Extraction" but this is too specific.

**Insight**: The extraction process is determined by **prompt + output schema**, not by the data type. The service should be **generic** and **configuration-driven**.

**Examples of what can be extracted**:
- User preferences (brand, price range, style)
- Allergy information ("can't eat peanuts")
- Important dates (birthdays, anniversaries)
- Contact information (job titles, relationships)
- Any structured information determined by prompt

**Design Principle**: 
- **Bottom layer**: Generic LLM Structured Extraction Service
- **Top layer**: Configuration-driven extraction templates (wrappers)
- Prompt = configuration, not code

---

## 0.1 Critical Bugs Fixed in v7

### Bug 1: `findBySource` Cannot Accept `List<String>` (Code-Writing Error)

**Location**: Section 2.3, `extractByTemplate()` method.

**Broken code**:
```java
List<ObservationEntity> candidates = observationRepository
    .findBySource(projectPath, template.sourceFilter());  // ❌ sourceFilter is List<String>, findBySource expects String
```

**Root cause**: `ExtractionTemplate.sourceFilter` is `List<String>`, but `ObservationRepository.findBySource(project, source, limit)` takes a single `String source`. This will not compile.

**Fix**: Add a new repository method that accepts a list of sources:

```java
// New repository method needed
@Query(value = """
    SELECT * FROM mem_observations
    WHERE project_path = :project
    AND source IN (:sources)
    ORDER BY created_at_epoch DESC
    LIMIT :limit
    """, nativeQuery = true)
List<ObservationEntity> findBySourceIn(
    @Param("project") String project,
    @Param("sources") List<String> sources,
    @Param("limit") int limit
);
```

**Updated code**:
```java
List<ObservationEntity> candidates = observationRepository
    .findBySourceIn(projectPath, template.sourceFilter(), 100);
```

### Bug 2: Manual JSON Parsing Instead of Spring AI Structured Output

**Location**: Section 2.3, `extractByTemplate()` method.

**Suboptimal approach** (v1-v6):
```java
String llmResponse = llmService.chatCompletion(systemPrompt, prompt);
T result = objectMapper.readValue(llmResponse, outputType);  // ❌ Manual parsing, no schema enforcement
```

**Problem**: 
- Manual JSON parsing has no schema validation guarantee
- LLM may return non-compliant JSON despite the prompt instruction
- No type safety at compile time

**Correct approach for Spring AI 1.1.2**: Use `ChatClient.call().entity()` with `BeanOutputConverter`

Spring AI 1.1.2 provides structured output converters in `org.springframework.ai.converter`:
- `BeanOutputConverter` - for POJO output
- `MapOutputConverter` - for Map/JSON object output
- `ListOutputConverter` - for list/array output

**Important**: `JacksonOutputConverter` does NOT exist in Spring AI 1.1.2 — this was an incorrect reference in earlier versions.

**Implementation Option A: Add structured method to LlmService** (Recommended - keeps existing pattern)

```java
// In LlmService.java - add new method
public <T> T chatCompletionStructured(String systemPrompt, String userPrompt, Class<T> outputType) {
    ChatClient chatClient = this.chatClient.orElseThrow(() ->
        new IllegalStateException("AI not configured."));

    // Use BeanOutputConverter for schema-enforced parsing
    BeanOutputConverter<T> converter = new BeanOutputConverter<>(outputType);

    return chatClient.prompt()
        .system(systemPrompt + "\n\nOutput format: " + converter.getFormat())
        .user(userPrompt)
        .call()
        .entity(converter);
}
```

**Why `BeanOutputConverter` is the right choice**:
- Automatically generates JSON Schema from the target POJO class
- Instructs the LLM via the system prompt to output matching JSON
- Provides parse-with-validation via `converter.toCharFlux()` parsing
- Works with any Spring AI compatible model (OpenAI, Anthropic, etc.)
- Keeps extraction logic in StructuredExtractionService, not scattered

**Note**: Spring AI 1.1.2 does NOT provide true schema enforcement at the API level — it relies on:
1. `BeanOutputConverter` generating schema instructions in the system prompt
2. LLM compliance with those instructions
3. The converter parsing the response (may fail if LLM doesn't comply)

For stricter enforcement, implement validation + retry logic in the caller.

### Bug 3: Integration Ambiguity — "Add extraction call after existing refinement"

**Location**: Section 9.2.

**Problem**: The statement "Add extraction call after existing refinement" is ambiguous. It could mean:
1. Call extraction inside `deepRefineProjectMemories()` after `refineObservations()`
2. Call extraction as a separate step in the same scheduled task

**Recommendation**: **Option 2 — separate pipeline**. Extraction and refinement serve different purposes:

| Concern | MemoryRefineService | StructuredExtractionService |
|---------|---------------------|----------------------------|
| Purpose | Prune/merge/rewrite memory | Extract structured facts from memory |
| Trigger | SessionEnd + scheduled | Scheduled (daily/hourly) |
| LLM calls | Moderate (merge/rewrite) | High (per-template per-project) |
| Risk | Data loss (deletion) | Cost overrun |

**Mixing them risks**: Extraction failures blocking refinement, or refinement deleting candidates before extraction runs.

**Correct approach**:
```java
// Separate pipeline, separate trigger
@Scheduled(cron = "${app.memory.extraction.schedule:0 0 2 * * ?}")  // Daily at 2am
public void scheduledExtraction() {
    List<String> projects = observationRepository.findDistinctProjects();
    for (String project : projects) {
        extractionService.runExtraction(project);  // Separate from refineMemory()
    }
}
```

**Where refinement integrates**: `deepRefineProjectMemories()` should call extraction as the **last step**, AFTER all refinement is done, so extraction sees the refined (not raw) memory state:

```java
public void deepRefineProjectMemories(String projectPath) {
    // Step 1: Refine existing memories
    List<ObservationEntity> candidates = findRefineCandidates(projectPath);
    if (!candidates.isEmpty()) {
        refineObservations(candidates, projectPath);
    }
    
    // Step 2: Run extraction on refined state (NEW)
    // Only if all refinement steps succeeded
    if (refineEnabled) {
        extractionService.runExtraction(projectPath);
    }
}
```

---

## 0.2 Critical Design Gaps in v10 (New in v11)

### Gap 1: `outputSchema` (String) Cannot Directly Feed `BeanOutputConverter<T>` (Class)

**Problem**: Section 2.1 defines `outputSchema` as a `String` (JSON Schema text), but `BeanOutputConverter<T>` requires a **Java `Class<T>`** at construction time. There is no built-in bridge from JSON Schema string → Java Class.

```java
// What the design shows (conceptually):
BeanOutputConverter<T> converter = new BeanOutputConverter<>(outputType);  // ❌ outputType is String, needs Class!
```

**Root cause**: `BeanOutputConverter` generates format instructions from a **compile-time Java class**, not from a runtime JSON Schema string. Its `getFormat()` method introspects the class fields to produce the JSON Schema for the prompt.

**Three practical solutions**:

| Solution | Pros | Cons |
|----------|------|------|
| **A. Predefined POJO classes** | Type-safe, works with `BeanOutputConverter` | Must define class per template, harder to add new templates dynamically |
| **B. `Map<String, Object>` fallback** | Flexible, any schema works | No type safety, post-processing needed to extract fields |
| **C. Dynamic class generation** | Most flexible | Complex, fragile, security concerns |

**Recommended**: **Solution A + B hybrid** — Use `template-class` field for known templates, fall back to `Map<String, Object>` for unknown/generic schemas.

```yaml
templates:
  - name: "allergy_info"
    template-class: "com.example.AllergyInfo"   # Maps to AllergyInfo.class
    # output-schema is generated from the Java class by BeanOutputConverter
    
  - name: "generic_preference"
    template-class: "java.util.Map"             # Use Map<String, Object> fallback
```

### Gap 2: Array Schema Handling Is Not Addressed

**Problem**: The design shows array output examples (`"type": "array", "items": {...}`), but `ListOutputConverter` in Spring AI 1.1.2 returns `List<String>`, NOT `List<MyObject>`.

```java
// ListOutputConverter converts ["a", "b", "c"] → List<String>
// It does NOT handle: [{"name": "x"}, {"name": "y"}] → List<MyObject>
```

**Solution for arrays**:
1. For arrays of primitives (`["string1", "string2"]`): `ListOutputConverter` works
2. For arrays of objects (`[{"key": "val"}, ...]`): Use `MapOutputConverter` → `List<Map<String, Object>>`, then post-process
3. Store array results in `extractedData` as JSON array (ObjectMapper handles serialization)

### Gap 3: Repository Methods Listed as "New" Are Still NOT Implemented

**Status**: The following methods are referenced throughout the design but **do not exist** in `ObservationRepository`:

| Method | Status | Purpose |
|--------|--------|---------|
| `findBySourceIn(project, List<String>, limit)` | ❌ Not implemented | Bug 1 fix - filter by multiple sources |
| `findNewObservations(project, sources, sinceEpoch, limit)` | ❌ Not implemented | Incremental extraction |

**Action required**: Add these methods before Phase 3.1 implementation.

### Gap 4: `LlmService.chatCompletionStructured()` Method Does Not Exist

**Status**: The design references `llmService.chatCompletionStructured(systemPrompt, userPrompt, outputType)` but this method is **not implemented** in `LlmService`.

**Action required**: Implement this method before Phase 3.1 implementation.

### Gap 5: `outputSchema` Is Redundant When Using `template-class`

**Problem**: If `template-class` points to a Java class, `BeanOutputConverter` **auto-generates** the JSON Schema from the class at runtime via `getJsonSchema()`. The `output-schema` field in YAML becomes redundant for POJO templates.

**Resolution**: Keep `output-schema` in YAML only for `Map`-type templates (where no class exists). For POJO templates, `output-schema` is optional or can be auto-derived.

---

## 1. Existing Architecture Analysis

### 1.1 MemoryRefineService Capabilities

| Method | Function | LLM Call |
|--------|----------|----------|
| `refineMemory()` | SessionEnd triggered, async refine | No |
| `quickRefine()` | Lightweight, delete low-quality | No |
| `deepRefineProjectMemories()` | Deep refine, merge/rewrite | ✅ Yes |
| `mergeObservations()` | Merge same-session observations | ✅ Yes |
| `rewriteObservation()` | Single observation rewrite | ✅ Yes |

### 1.2 V14 Infrastructure Available

| Field | Purpose | Phase 3 Reuse |
|-------|---------|---------------|
| `source` | Source attribution | Extraction source marking |
| `extractedData` (JSONB) | Structured data | Extraction results storage |
| `qualityScore` | Quality scoring | Extraction confidence |
| `refinedAt` | Refinement timestamp | Extraction timeline |

---

## 2. Generalized Structured Extraction Design

**Core Idea**: A **prompt-driven** extraction service where what to extract and how to output is determined by configuration, not code.

### 2.1 Core Abstraction: ExtractionTemplate

```java
/**
 * Extraction template - defines WHAT to extract and HOW to output.
 * The service interprets the prompt and schema to extract structured data.
 * 
 * NOTE: sourceFilter is List<String> — requires findBySourceIn(), not findBySource().
 * This is a common design error: findBySource(project, source, limit) takes String,
 * so a new findBySourceIn(project, List<String>, limit) repository method is needed.
 * 
 * NOTE (v11): templateClass is REQUIRED. BeanOutputConverter<T> needs a Java Class<T>,
 * not a JSON Schema string. outputSchema is only used for Map<String,Object> templates.
 */
public record ExtractionTemplate(
    String name,                    // Template identifier
    boolean enabled,                // Per-template enable flag (default: true)
    String templateClass,           // Java class name for output (e.g., "com.example.AllergyInfo" or "java.util.Map")
    String description,             // Human-readable description
    List<String> triggerKeywords,   // Keywords to filter candidates
    List<String> sourceFilter,      // Which sources to consider (⚠️ List, not String)
    String promptTemplate,           // System prompt for extraction instruction
    String outputSchema,            // JSON Schema (only for Map templates; auto-derived from templateClass for POJOs)
    boolean trackEvolution,          // Whether to track value changes over time
    boolean conflictEnabled          // Whether to detect conflicts
) {}
```

### 2.2 Configuration Model (YAML)

**Note**: The YAML key `prompt` maps to the Java record field `promptTemplate` via `@JsonProperty("prompt")` annotation or Spring Boot relaxed binding.

```yaml
app.memory.extraction:
  enabled: true
  templates:
    - name: "user_preference"
      enabled: true
      template-class: "java.util.Map"        # Flexible schema → Map<String, Object> output
      session-id-pattern: "pref:{project}:{userId}"  # Special session for user preferences
      description: "Extract user preferences (brand, price, style)"
      trigger-keywords: ["prefer", "like", "更喜欢", "倾向于"]
      source-filter: ["user_statement", "manual"]
      prompt: |   # Maps to promptTemplate field in Java record
        From the following conversation, extract user preferences.
        Look for: brands they like/dislike, budget constraints, style preferences.
        Return ALL preferences found, not just one.
      output-schema: |   # Array-wrapped schema for multiple preferences (see Section 20.1)
        {
          "type": "object",
          "properties": {
            "preferences": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "category": {"type": "string"},
                  "value": {"type": "string"},
                  "sentiment": {"type": "string", "enum": ["positive", "negative", "neutral"]},
                  "confidence": {"type": "number"}
                }
              }
            }
          }
        }
      track-evolution: true
      conflict-enabled: true
      
    - name: "allergy_info"
      enabled: true
      template-class: "com.example.AllergyInfo"   # Predefined POJO class
      description: "Extract allergy and dietary information"
      trigger-keywords: ["过敏", "不能吃", "吃了会", "allergic"]
      source-filter: ["user_statement", "manual", "llm_inference"]
      prompt: |
        From the conversation, extract allergy information:
        - Who has the allergy (person)
        - What allergens
        - Severity if mentioned
      output-schema: |   # Optional for POJO templates (auto-derived from class), shown for clarity
        {
          "type": "object", 
          "properties": {
            "person": {"type": "string"},
            "allergens": {"type": "array", "items": {"type": "string"}},
            "severity": {"type": "string"}
          }
        }
      track-evolution: false
      conflict-enabled: true
      
    - name: "important_dates"
      template-class: "java.util.Map"            # Array results stored in Map["dates"]
      description: "Extract important dates and events"
      trigger-keywords: ["生日", "anniversary", "纪念日", "记得"]
      source-filter: ["user_statement", "manual"]
      prompt: |
        Extract important dates mentioned: birthdays, anniversaries, events.
        Include: date, occasion, who's involved.
      output-schema: |
        {
          "type": "object",
          "properties": {
            "dates": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "date": {"type": "string"},
                  "occasion": {"type": "string"},
                  "person": {"type": "string"}
                }
              }
            }
          }
        }
      track-evolution: false
      conflict-enabled: false
```

### 2.3 Generic StructuredExtractionService

**Integration note**: This service uses `LlmService` (not raw `ChatClient`) to stay consistent with `MemoryRefineService`. The `LlmService.chatCompletionStructured()` method must be added — see section 0.1 Bug 2.

**Two Converter Patterns** (v11 clarification):

| templateClass | Converter | Use outputSchema? | Notes |
|---------------|-----------|-------------------|-------|
| `"java.util.Map"` | `BeanOutputConverter<Map>` | ✅ Yes (required) | Flexible, any schema works |
| `"com.example.MyPojo"` | `BeanOutputConverter<MyPojo>` | ❌ No (auto-derived) | Type-safe, schema from class |

```java
@Service
public class StructuredExtractionService {
    
    @Value("${app.memory.extraction.templates}")
    private List<ExtractionTemplate> templates;
    
    private final LlmService llmService;           // ✅ Uses LlmService (consistent with MemoryRefineService)
    private final ObservationRepository observationRepository;
    private final ObjectMapper objectMapper;
    
    // Classloader for resolving templateClass string → Class<?>
    private final ClassLoader classLoader = getClass().getClassLoader();
    
    /**
     * Generic extraction - runs all templates, grouped by user.
     * What is extracted is determined by the template prompts.
     * 
     * Flow:
     * 1. Get all candidate observations for project
     * 2. Group observations by user (via session → user_id)
     * 3. For each user, run extraction per template
     * 4. Store results in user-scoped or session-scoped target
     */
    public void runExtraction(String projectPath) {
        for (ExtractionTemplate template : templates) {
            if (!template.enabled()) {
                continue;
            }
            try {
                runTemplateExtraction(projectPath, template);
            } catch (Exception e) {
                log.error("Extraction failed for template {}: {}", template.name(), e.getMessage());
            }
        }
    }
    
    /**
     * Template extraction runner with user grouping.
     * Groups observations by user_id (from SessionEntity), then extracts per user.
     */
    private void runTemplateExtraction(String projectPath, ExtractionTemplate template) {
        // Check extraction state for incremental extraction
        ExtractionState state = getExtractionState(projectPath, template.name());
        
        // Find candidates (new or all)
        List<ObservationEntity> candidates = (state == null)
            ? getCandidatesInitial(projectPath, template)
            : observationRepository.findNewObservations(
                projectPath, template.sourceFilter(), 
                state.lastExtractedAt().toEpochSecond() * 1000L, 1000);
        
        if (candidates.isEmpty()) {
            return;
        }
        
        // Group observations by user (via session → user_id)
        Map<String, List<ObservationEntity>> byUser = groupByUser(candidates);
        
        // Extract per user
        for (Map.Entry<String, List<ObservationEntity>> entry : byUser.entrySet()) {
            String userId = entry.getKey();
            List<ObservationEntity> userObs = entry.getValue();
            String targetSessionId = resolveSessionId(
                template.sessionIdPattern(), projectPath, userId);
            
            // LLM re-extraction: fetch prior result as context for LLM
            String priorJson = null;
            if (targetSessionId != null) {
                List<ObservationEntity> prior = observationRepository
                    .findByContentSessionIdAndType(targetSessionId, 
                        "extracted_" + template.name(), 1);
                if (!prior.isEmpty()) {
                    priorJson = objectMapper.writeValueAsString(prior.get(0).getExtractedData());
                }
            }
            
            Object result = extractByTemplate(projectPath, template, userObs, priorJson);
            if (result != null) {
                storeExtractionResult(template, result, userObs, targetSessionId);
            }
        }
        
        updateExtractionState(projectPath, template.name(), OffsetDateTime.now());
    }
    
    /**
     * Group observations by user_id via session lookup.
     * Observations without a session or user_id are grouped under "__unknown__".
     */
    private Map<String, List<ObservationEntity>> groupByUser(List<ObservationEntity> observations) {
        Map<String, List<ObservationEntity>> byUser = new HashMap<>();
        
        for (ObservationEntity obs : observations) {
            String sessionId = obs.getContentSessionId();
            String userId = "__unknown__";
            
            if (sessionId != null) {
                try {
                    SessionEntity session = sessionRepository.findByContentSessionId(sessionId);
                    if (session != null && session.getUserId() != null) {
                        userId = session.getUserId();
                    }
                } catch (Exception e) {
                    log.warn("Failed to lookup session for user grouping: {}", e.getMessage());
                }
            }
            
            byUser.computeIfAbsent(userId, k -> new ArrayList<>()).add(obs);
        }
        
        return byUser;
    }
    
    /**
     * Resolve target session ID from pattern.
     * - null pattern → inherit from source observation (handled by caller)
     * - pattern with {project}/{userId} → substitute variables
     */
    private String resolveSessionId(String pattern, String projectPath, String userId) {
        if (pattern == null) {
            return null;
        }
        return pattern
            .replace("{project}", projectPath)
            .replace("{userId}", userId);
    }
    
    /**
     * Get initial candidates with cost cap (see Section 19.3).
     */
    private List<ObservationEntity> getCandidatesInitial(
            String projectPath, ExtractionTemplate template) {
        return observationRepository.findBySourceIn(
            projectPath, template.sourceFilter(), initialRunMaxCandidates);
    }
    
    /**
     * Resolve templateClass string to Java Class.
     * Supports: "java.util.Map", "com.example.AllergyInfo", etc.
     */
    @SuppressWarnings("unchecked")
    private <T> Class<T> resolveOutputClass(String templateClass) {
        try {
            if ("java.util.Map".equals(templateClass)) {
                return (Class<T>) java.util.Map.class;
            }
            return (Class<T>) classLoader.loadClass(templateClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot load template class: " + templateClass, e);
        }
    }
    
    /**
     * Build schema hint for the user prompt.
     * - For Map templates: include outputSchema from YAML
     * - For POJO templates: BeanOutputConverter handles this via getFormat() in the system prompt
     */
    private <T> String buildSchemaHint(ExtractionTemplate template, Class<T> outputClass) {
        if (java.util.Map.class.isAssignableFrom(outputClass)) {
            // Map template: schema is in YAML, include it in the user prompt
            return template.outputSchema() != null ? template.outputSchema() : "{}";
        }
        // POJO template: schema is auto-derived by BeanOutputConverter.getFormat()
        return null;
    }
    
    /**
     * Extract by specific template using Spring AI structured output.
     * Supports two patterns: POJO (type-safe) and Map (flexible schema).
     * 
     * LLM re-extraction approach: If priorJson is provided, the LLM receives the
     * previous extraction result as context. It produces a complete current state,
     * deciding what to keep/remove based on new observations. Old extractions are
     * preserved as history (new observation always created, never merged).
     * 
     * Requires LlmService.chatCompletionStructured() — see Bug 2 fix in section 0.1.
     */
    @SuppressWarnings("unchecked")
    public <T> T extractByTemplate(
        String projectPath, 
        ExtractionTemplate template,
        List<ObservationEntity> candidates,
        String priorJson) {
        
        // 1. Resolve output type from templateClass
        Class<T> outputClass = resolveOutputClass(template.templateClass());
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        // 2. Build prompt (schema source depends on template type)
        String schemaHint = buildSchemaHint(template, outputClass);
        String prompt = buildPrompt(template, candidates, schemaHint, priorJson);
        
        // 3. Call LLM with Spring AI structured output via LlmService
        // NOTE: This requires LlmService.chatCompletionStructured() to be implemented.
        T result = llmService.chatCompletionStructured(
            template.promptTemplate(),  // system prompt
            prompt,                    // user prompt
            outputClass                // target class for structured parsing
        );
        
        return result;
    }
    
    private String buildPrompt(ExtractionTemplate template, 
                               List<ObservationEntity> candidates,
                               String schemaHint,
                               String priorJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("Extract structured information from the following observations.\n\n");
        
        // Include prior extraction as context (LLM re-extraction approach)
        if (priorJson != null) {
            sb.append("Previous extraction result (update based on new observations):\n");
            sb.append(priorJson).append("\n\n");
            sb.append("Instructions: Based on the previous result and new observations below, ");
            sb.append("produce a complete updated state. If an item is no longer valid ");
            sb.append("(explicitly rejected), remove it. If new items are mentioned, add them. ");
            sb.append("Optionally include removed items in a 'removed' field with reason.\n\n");
        }
        
        sb.append("New observations:\n");
        for (ObservationEntity obs : candidates) {
            sb.append(String.format("- [%s] %s\n  %s\n",
                sanitize(obs.getSource()),
                obs.getTitle() != null ? sanitize(obs.getTitle()) : "",
                obs.getContent() != null ? sanitize(obs.getContent()) : ""));
        }
        
        // Include schema hint only for Map templates (POJO templates get schema via BeanOutputConverter)
        if (schemaHint != null) {
            sb.append("\nOutput JSON according to this schema:\n");
            sb.append(schemaHint);
        }
        
        return sb.toString();
    }
    
    /**
     * Sanitize user content to prevent prompt injection.
     */
    private String sanitize(String content) {
        if (content == null) return "";
        return content
            .replace("SYSTEM:", "\\[SYSTEM\\]")
            .replace("OBSERVATIONS:", "\\[OBSERVATIONS\\]")
            .replace("Output:", "\\[Output:\\]");
    }
    
    /**
     * Store extraction result with merge logic and user-scoped session ID.
     * 
     * Merge behavior:
     * - If template has sessionIdPattern and an existing extraction exists for that session,
     *   merge new results with existing ones (deduplicate by category+value).
     * Store extraction result. Always creates a new observation — old extractions
     * are preserved as history. The LLM re-extraction approach means each run
     * produces a complete current state (including prior results as context),
     * so no programmatic merge is needed.
     * 
     * @param template Extraction template configuration
     * @param result LLM extraction result (complete current state)
     * @param sourceObservations Source observations used for extraction
     * @param targetSessionId Target session ID (from resolveSessionId), or null to inherit
     */
    private <T> void storeExtractionResult(
            ExtractionTemplate template, 
            T result,
            List<ObservationEntity> sourceObservations,
            String targetSessionId) {
        
        Map<String, Object> newExtractedData = convertToMap(result);
        
        // Always create new observation — old one becomes history
        ObservationEntity extractionObs = new ObservationEntity();
        extractionObs.setType("extracted_" + template.name());
        extractionObs.setSource("extraction:" + template.name());
        extractionObs.setExtractedData(newExtractedData);
        extractionObs.setConcepts(List.of("extracted", template.name()));
        
        if (targetSessionId != null) {
            extractionObs.setContentSessionId(targetSessionId);
            extractionObs.setProjectPath(sourceObservations.get(0).getProjectPath());
        }
        
        observationRepository.save(extractionObs);
    }
    
    /**
     * Convert extraction result to Map<String, Object> for storage in JSONB column.
     * Handles: POJOs (via ObjectMapper), Maps, Lists, and primitives.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        try {
            // ObjectMapper is available as a field in this service
            return objectMapper.convertValue(result, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to convert extraction result to Map, storing as raw JSON string: {}", e.getMessage());
            // Fallback: store as single-field map with JSON string
            Map<String, Object> fallback = new java.util.HashMap<>();
            try {
                fallback.put("_raw", objectMapper.writeValueAsString(result));
            } catch (Exception ex) {
                fallback.put("_raw", result.toString());
            }
            return fallback;
        }
    }
    
    /**
     * Format extracted data as human-readable text for ICL prompts.
     * Recursively handles nested Maps, Lists, and primitives.
     * 
     * Example output:
     *   preferences:
     *     - category: 手机品牌(排斥), value: 苹果, sentiment: negative
     *     - category: 手机品牌(偏好), value: 小米, sentiment: positive
     */
    public static String formatExtractedData(Map<String, Object> extractedData) {
        if (extractedData == null) return "";
        StringBuilder sb = new StringBuilder();
        formatMap(extractedData, sb, 0);
        return sb.toString();
    }
    
    private static void formatMap(Map<?, ?> map, StringBuilder sb, int indent) {
        String indentStr = "  ".repeat(indent);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            
            if (value instanceof List) {
                sb.append(indentStr).append(key).append(":\n");
                formatList((List<?>) value, sb, indent + 1);
            } else if (value instanceof Map) {
                sb.append(indentStr).append(key).append(":\n");
                formatMap((Map<?, ?>) value, sb, indent + 1);
            } else {
                sb.append(indentStr).append(key).append(": ").append(value).append("\n");
            }
        }
    }
    
    private static void formatList(List<?> list, StringBuilder sb, int indent) {
        String indentStr = "  ".repeat(indent);
        for (Object item : list) {
            if (item instanceof Map) {
                Map<?, ?> itemMap = (Map<?, ?>) item;
                sb.append(indentStr).append("- ");
                // Inline format for compact display
                boolean first = true;
                for (Map.Entry<?, ?> e : itemMap.entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(e.getKey()).append(": ").append(e.getValue());
                    first = false;
                }
                sb.append("\n");
            } else {
                sb.append(indentStr).append("- ").append(item).append("\n");
            }
        }
    }
}
```

### 2.4 Example: Allergy Extraction (from user's example)

**Two ways to configure** (v11):

**Option A: POJO Template** (type-safe, requires predefined class)

```java
// com.example.AllergyInfo.java
public class AllergyInfo {
    public String person;
    public List<String> allergens;
    public String severity;
}

// Template YAML
templates:
  - name: "allergy_info"
    template-class: "com.example.AllergyInfo"   # Predefined POJO
    trigger-keywords: ["过敏", "allergic", "不能吃"]
    prompt: |
      从对话中提取过敏信息：
      - 谁过敏 (person)
      - 过敏原 (allergens) 
      - 严重程度（如有）
    # output-schema: auto-derived from AllergyInfo.class by BeanOutputConverter

// Usage
var result = extractionService.extractByTemplate(projectPath, allergyTemplate);
// result.content() is AllergyInfo: {person: "孩子", allergens: ["花生", "虾"], severity: "严重"}
```

**Option B: Map Template** (flexible, no predefined class needed)

```java
// Template YAML
templates:
  - name: "allergy_info"
    template-class: "java.util.Map"              # Flexible Map output
    trigger-keywords: ["过敏", "allergic", "不能吃"]
    prompt: |
      从对话中提取过敏信息：
      - 谁过敏 (person)
      - 过敏原 (allergens) 
      - 严重程度（如有）
    output-schema: |
      {"type": "object", "properties": {"person": {"type": "string"}, "allergens": {"type": "array", "items": {"type": "string"}}, "severity": {"type": "string"}}}

// Usage
var result = extractionService.extractByTemplate(projectPath, allergyTemplate);
// result.content() is Map<String, Object>: {person="孩子", allergens=[花生, 虾], severity="严重"}
```

---

## 3. Memory Conflict Detection Design

**⚠️ SUPERSEDED**: This section describes a programmatic `ConflictDetector` class. After the walkthrough analysis (2026-03-22), this approach is no longer needed for Phase 3.1. The **LLM re-extraction** approach (Section 2.3) handles conflict detection implicitly — the LLM understands semantics and produces a correct current state. This section is retained for reference only.

**Core Idea**: Detect contradictions when storing extraction results with `track-evolution: true`.

### 3.1 Conflict Detection Flow

Conflict detection is triggered when a new extraction result differs from the stored one.

```java
private void checkAndUpdateEvolution(String projectPath, ExtractionTemplate template, Object newResult) {
    // FIXED: Include projectPath to avoid cross-project pollution
    List<ObservationEntity> existing = observationRepository
        .findByType(projectPath, "extracted_" + template.name(), 1);  // Most recent
    
    if (existing.isEmpty()) {
        return;
    }
    
    Object oldResult = parseExtractedData(existing.get(0).getExtractedData());
    
    if (!valuesEqual(oldResult, newResult)) {
        if (template.conflictEnabled()) {
            ConflictResult conflict = conflictDetector.detect(template, oldResult, newResult);
            handleConflict(conflict);
        } else {
            updateExtractionResult(existing.get(0), newResult);
        }
    }
}
```

### 3.2 ConflictDetector

```java
public class ConflictDetector {
    
    private final LlmService llmService;
    
    public ConflictResult detect(ExtractionTemplate template, Object oldResult, Object newResult) {
        String prompt = buildConflictDetectionPrompt(template, oldResult, newResult);
        
        String response = llmService.chatCompletion(
            "You are a memory consistency analyst. Detect contradictions.",
            prompt
        );
        
        return parseConflictResponse(response);
    }
    
    private String buildConflictDetectionPrompt(
        ExtractionTemplate template, Object oldResult, Object newResult) {
        
        return String.format("""
            Analyze extraction results for conflicts.
            
            Template: %s
            Extraction type: %s
            
            Old result: %s
            New result: %s
            
            Determine:
            1. Is there a conflict or just normal evolution?
            2. Conflict type: DIRECT_CONTRADICTION, EVOLUTION, or NONE
            3. If conflict, suggest resolution
            
            Response format:
            {"conflict_type": "NONE|DIRECT_CONTRADICTION|EVOLUTION", 
             "resolution": "keep_new|keep_old|merge|manual_review",
             "reasoning": "..."}
            """,
            template.name(),
            template.description(),
            oldResult,
            newResult
        );
    }
}
```

### 3.3 Conflict Types

| Type | Example | Handling |
|------|---------|----------|
| **Direct Contradiction** | "budget is $3000" vs "budget is $5000" | LLM arbitration, keep newer |
| **Evolution** | "likes Sony" → "now likes Bose" | Record history, keep both |
| **Implicit Conflict** | Preference vs system limitation | Flag for manual review |

---

## 4. UserProfile Design (Deferred)

**Current Solution is Sufficient**: Session-based isolation

Extractions of type "user_profile" can be stored with source="profile_update".

---

## 5. Implementation Roadmap

| Phase | Content | Changes |
|-------|---------|---------|
| **Phase 3.1** | StructuredExtractionService + templates + user_id | Generic extraction engine with LLM re-extraction |
| **Phase 3.2** | Template configurations + keyword trigger | YAML configs, trigger enhancement |
| **Phase 3.3** | DeepRefine integration | Run extraction during deep refine |
| **Phase 3.4** | Manual review (optional) | API + UI for reviewing extraction history |

---

## 6. Key Design Principles

| Principle | Application |
|-----------|-------------|
| **Prompt-driven** | What to extract = prompt template (configuration) |
| **Generic core** | `StructuredExtractionService` works for any extraction type |
| **Specific wrappers** | Templates configure specific extraction tasks |
| **Configuration over code** | Add new extraction type = add YAML config |

---

## 7. Design Deep Dive: Additional Considerations

### 7.1 Incremental Extraction

**Issue**: Currently, every extraction run processes ALL matching observations. This is inefficient and may produce duplicate results.

**Solution**: Track extraction state per template per project.

```java
/**
 * Track what has been extracted to avoid re-extraction.
 * Stored as an ObservationEntity with type="extraction_state" (no separate table needed).
 * Uses projectPath + source + extractedData to encode state fields.
 */
public record ExtractionState(
    String projectPath,
    String templateName,
    OffsetDateTime lastExtractedAt,
    UUID lastExtractedObservationId,
    int totalExtracted
) {}

/**
 * Store extraction state using existing ObservationEntity infrastructure.
 * Avoids a separate database table.
 */
private void updateExtractionState(String projectPath, String templateName, OffsetDateTime now) {
    ObservationEntity stateObs = new ObservationEntity();
    stateObs.setProjectPath(projectPath);
    stateObs.setType("extraction_state");
    stateObs.setSource("state:" + templateName);
    stateObs.setCreatedAt(now);
    stateObs.setCreatedAtEpoch(now.toEpochSecond() * 1000L);
    stateObs.setExtractedData(Map.of(
        "template", templateName,
        "lastExtractedAt", now.toEpochSecond()
    ));
    // Use upsert: delete old state for this project+template, then insert new
    observationRepository.findByType(projectPath, "extraction_state", 1).stream()
        .filter(o -> o.getSource().equals("state:" + templateName))
        .forEach(o -> observationRepository.deleteById(o.getId()));
    observationRepository.save(stateObs);
}

private ExtractionState getExtractionState(String projectPath, String templateName) {
    List<ObservationEntity> states = observationRepository.findByType(projectPath, "extraction_state", 1).stream()
        .filter(o -> o.getSource().equals("state:" + templateName))
        .toList();
    if (states.isEmpty()) return null;
    
    Map<String, Object> data = states.get(0).getExtractedData();
    return new ExtractionState(
        projectPath,
        templateName,
        OffsetDateTime.ofEpochSecond(((Number) data.get("lastExtractedAt")).longValue(), 0, ZoneOffset.UTC),
        null,  // lastExtractedObservationId not stored in simple version
        0      // totalExtracted not tracked in simple version
    );
}
```

**Incremental extraction flow**:
```java
public void runIncrementalExtraction(String projectPath, ExtractionTemplate template) {
    // Get last extraction state
    ExtractionState state = getExtractionState(projectPath, template.name());
    
    // Only process NEW observations since last extraction
    List<ObservationEntity> newCandidates = observationRepository
        .findNewObservations(projectPath, state.lastExtractedAt());
    
    if (newCandidates.isEmpty()) {
        return; // Nothing new to extract
    }
    
    // Extract from new candidates only
    var result = extractByTemplate(projectPath, template, newCandidates);
    
    // Update state
    updateExtractionState(projectPath, template.name(), OffsetDateTime.now());
}
```

### 7.2 Extraction Query API

**Issue**: How to query/retrieve extracted information later?

**Proposed API**:

```java
/**
 * Query extracted information by template name.
 */
@GetMapping("/api/extraction/{templateName}")
public List<ExtractionInfo> getExtractions(
    @PathVariable String templateName,
    @RequestParam String projectPath,
    @RequestParam(required = false) OffsetDateTime since) {
    
    // Find all observations of type "extracted_{templateName}"
    List<ObservationEntity> extractions = observationRepository
        .findByType("extracted_" + templateName);
    
    return extractions.stream()
        .map(this::toExtractionInfo)
        .collect(Collectors.toList());
}

/**
 * Query extraction by specific field value.
 */
@GetMapping("/api/extraction/{templateName}/search")
public List<ExtractionInfo> searchExtractions(
    @PathVariable String templateName,
    @RequestParam String projectPath,
    @RequestParam String fieldPath,    // e.g., "allergens"
    @RequestParam String value         // e.g., "花生"
) {
    // Use JSON path query on extractedData
    return observationRepository
        .findByExtractedDataPath(projectPath, "extracted_" + templateName, fieldPath, value);
}
```

**Example queries**:
```bash
# Get all allergy extractions
GET /api/extraction/allergy_info?project=/my-project

# Find who is allergic to peanuts
GET /api/extraction/allergy_info/search?project=/my-project&fieldPath=allergens&value=花生
```

### 7.3 Template Versioning

**Issue**: What happens when template schema changes?

**Considerations**:
- Schema changes should not automatically re-extract old data
- Version the template in storage
- Allow explicit "re-extract" operation

```java
public record ExtractionTemplate(
    String name,
    int version,                    // Schema version
    String description,
    List<String> triggerKeywords,
    String promptTemplate,
    String outputSchema,
    boolean trackEvolution,
    boolean conflictEnabled
) {}

// Store with version
extractionObs.setSource("extraction:" + template.name() + ":v" + template.version());

// Re-extract only if explicitly requested
public void reExtract(String projectPath, String templateName, int version) { ... }
```

### 7.4 LLM Output Validation

**Issue**: What if LLM returns invalid JSON or doesn't follow schema?

**Solution**: Validate and retry

```java
private <T> T extractWithRetry(ExtractionTemplate template, 
                                List<ObservationEntity> candidates,
                                Class<T> outputType,
                                int maxRetries) {
    for (int i = 0; i < maxRetries; i++) {
        String response = llmService.chatCompletion(...);
        try {
            T result = objectMapper.readValue(response, outputType);
            if (validateOutput(result, template.outputSchema())) {
                return result;
            }
        } catch (JsonProcessingException e) {
            log.warn("Invalid JSON from LLM, attempt {}/{}", i+1, maxRetries);
        }
    }
    throw new ExtractionException("Failed to get valid output after " + maxRetries + " attempts");
}

private boolean validateOutput(Object output, String schema) {
    // Use JSON Schema validator
    // Return false if doesn't match schema
}
```

### 7.5 Cascading Extractions

**Issue**: Can extractions depend on other extractions?

**Example**: First extract "user_preference", then use that to filter "product_recommendation" extractions.

**Design**:
```yaml
templates:
  - name: "user_preference"
    # ... basic extraction
    
  - name: "product_recommendation"
    depends-on: ["user_preference"]    # Depends on user_preference extraction
    prompt: |
      Based on user's preference: {extracted_user_preference}
      Extract product recommendations...
```

```java
public void runCascadingExtraction(String projectPath, ExtractionTemplate template) {
    // First, run dependencies
    for (String depName : template.dependsOn()) {
        ExtractionTemplate dep = getTemplate(depName);
        runExtraction(projectPath, dep);  // Recursive
    }
    
    // Get dependency results
    Map<String, Object> context = getDependencyResults(projectPath, template.dependsOn());
    
    // Build prompt with context
    String prompt = buildPrompt(template, candidates, context);
    
    // Proceed with extraction
}
```

---

## 8. Open Questions

1. Should extraction run on every `deepRefine` or be scheduled separately?
2. Should conflicts auto-resolve or require human approval?
3. What is acceptable latency for extraction?
4. How to handle cross-project extractions (e.g., user preferences)?
5. Should we support incremental extraction by default? (Performance vs freshness tradeoff)
6. How to handle template schema evolution without re-extracting all history?
7. Do we need cascading extractions or is flat extraction sufficient?
8. **Spring AI 1.1.2 schema enforcement**: ✅ Answered in v10 — `JacksonOutputConverter` does NOT exist in Spring AI 1.1.2. Correct approach is `BeanOutputConverter` from `org.springframework.ai.converter`. Note: Spring AI 1.1.2 does not provide true schema enforcement at the API level — schema compliance relies on prompt engineering + LLM compliance + retry on parse failure.
9. **Schema-to-Class bridge (v11)**: ✅ Answered — Use `templateClass` field to specify Java class name. For flexible schemas, use `java.util.Map` with explicit `output-schema`. POJO templates auto-derive schema from class.
10. **Array schema handling (v11)**: ✅ Answered — `ListOutputConverter` returns `List<String>`, NOT `List<MyObject>`. For arrays of objects, use `MapOutputConverter` → `List<Map<String, Object>>` and post-process, or restructure schema as `{"type": "object", "properties": {"items": {"type": "array", ...}}}`.
11. **Which templates should be POJO vs Map?**: Open — POJO gives type safety but requires class per template. Map is flexible but loses type safety. Recommend: stable/important data (allergies) = POJO; experimental/new data = Map.

---

## 9. Implementation Feasibility Check

### 9.1 Existing Repository Methods

**Confirmed**: The following methods already exist in `ObservationRepository`:
- `findBySource(project, source, limit)` - For filtering by source
- `findByType(project, type, limit)` - For finding extraction results
- `findDistinctProjects()` - For iterating all projects in scheduled tasks

**⚠️ New repository methods STILL NOT IMPLEMENTED** (v14 status: pending):

```java
// v14 NEW: Find observations by type using LIKE pattern.
// Required because findByType uses exact match (type = :type), NOT LIKE.
// findByType(project, "extracted_%", 50) will return ZERO results — it looks for type exactly equal to "extracted_%".
// This method is needed for ICL prompt integration (section 15.8) and experience API.
// Alternative: iterate known template names with findByType(project, "extracted_" + name, limit) — no new method needed but more queries.
@Query(value = """
    SELECT * FROM mem_observations
    WHERE project_path = :project
    AND type LIKE :typePattern
    ORDER BY created_at_epoch DESC
    LIMIT :limit
    """, nativeQuery = true)
List<ObservationEntity> findByTypeLike(
    @Param("project") String project,
    @Param("typePattern") String typePattern,  // e.g. "extracted_%"
    @Param("limit") int limit
);

// Bug 1 fix: find observations where source is IN a list
// ⚠️ NOT YET ADDED to ObservationRepository — must be implemented before Phase 3.1
@Query(value = """
    SELECT * FROM mem_observations
    WHERE project_path = :project
    AND source IN (:sources)
    ORDER BY created_at_epoch DESC
    LIMIT :limit
    """, nativeQuery = true)
List<ObservationEntity> findBySourceIn(
    @Param("project") String project,
    @Param("sources") List<String> sources,
    @Param("limit") int limit
);

// Incremental extraction: find observations newer than lastExtractedAt
// ⚠️ NOT YET ADDED to ObservationRepository — must be implemented before Phase 3.1
@Query(value = """
    SELECT * FROM mem_observations
    WHERE project_path = :project
    AND source IN (:sources)
    AND created_at_epoch > :sinceEpoch
    ORDER BY created_at_epoch ASC
    LIMIT :limit
    """, nativeQuery = true)
List<ObservationEntity> findNewObservations(
    @Param("project") String project,
    @Param("sources") List<String> sources,
    @Param("sinceEpoch") Long sinceEpoch,  // OffsetDateTime.toEpochSecond() * 1000
    @Param("limit") int limit
);

// DLQ retry: global (cross-project) query by type
// ⚠️ NOT YET ADDED — findByType(project, type, limit) requires non-null project
// This method is needed for the dead letter queue retry scheduled task
@Query(value = """
    SELECT * FROM mem_observations
    WHERE type = :type
    ORDER BY created_at_epoch DESC
    LIMIT :limit
    """, nativeQuery = true)
List<ObservationEntity> findByTypeGlobal(
    @Param("type") String type,
    @Param("limit") int limit
);
```

```java
// Bug 1 fix: find observations where source is IN a list
@Query(value = """
    SELECT * FROM mem_observations
    WHERE project_path = :project
    AND source IN (:sources)
    ORDER BY created_at_epoch DESC
    LIMIT :limit
    """, nativeQuery = true)
List<ObservationEntity> findBySourceIn(
    @Param("project") String project,
    @Param("sources") List<String> sources,
    @Param("limit") int limit
);

// Incremental extraction: find observations newer than lastExtractedAt
// Uses created_at_epoch (Long) for efficient comparison
@Query(value = """
    SELECT * FROM mem_observations
    WHERE project_path = :project
    AND source IN (:sources)
    AND created_at_epoch > :sinceEpoch
    ORDER BY created_at_epoch ASC
    LIMIT :limit
    """, nativeQuery = true)
List<ObservationEntity> findNewObservations(
    @Param("project") String project,
    @Param("sources") List<String> sources,
    @Param("sinceEpoch") Long sinceEpoch,  // OffsetDateTime.toEpochSecond() * 1000
    @Param("limit") int limit
);
```

### 9.2 Integration Points with MemoryRefineService

**Where to integrate**:
1. `deepRefineProjectMemories()` - Add extraction call after existing refinement
2. New scheduled task for periodic extraction (separate from refinement)

**Recommended approach**:
- Extraction runs as separate phase during `deepRefine`
- Track extraction state in dedicated table or observation metadata
- Don't mix extraction with existing merge/rewrite logic (separation of concerns)

---

## 10. Critical Implementation Considerations

### 10.1 Context Window & Batching

**Issue**: Many observations may exceed LLM context window.

**Solution**: Chunk observations and process in batches.

```java
@Service
public class StructuredExtractionService {
    
    @Value("${app.memory.extraction.max-tokens-per-call:8000}")
    private int maxTokensPerCall;
    
    @Value("${app.memory.extraction.batch-size:20}")
    private int batchSize;
    
    public <T> List<T> extractByTemplate(String projectPath, ExtractionTemplate template, Class<T> outputType) {
        // 1. Get all candidate observations (paginated)
        List<ObservationEntity> allCandidates = observationRepository
            .findBySourceIn(projectPath, template.sourceFilter(), 1000);
        
        // 2. Chunk into batches (by token count, not by count)
        List<List<ObservationEntity>> batches = chunkByTokenCount(allCandidates, maxTokensPerCall);
        
        // 3. Process each batch
        List<T> results = new ArrayList<>();
        for (List<ObservationEntity> batch : batches) {
            T result = extractSingleBatch(template, batch, outputType);
            results.add(result);
        }
        
        // 4. Merge results (if multiple batches)
        return mergeResults(results, template);
    }
    
    private <T> List<ObservationEntity> chunkByTokenCount(List<ObservationEntity> observations, int maxTokens) {
        // Group observations until token count would exceed limit
        // Use TokenService.calculateObservationTokens() for estimation
        // Note: tokenService.estimateTokens() does NOT exist - inject TokenService bean instead
    }
}
```

### 10.2 Prompt Injection Prevention

**Issue**: User content may contain malicious instructions to manipulate extraction.

**Solution**: Separate user content from system instructions.

```java
public class StructuredExtractionService {
    
    private String buildPrompt(ExtractionTemplate template, List<ObservationEntity> candidates) {
        StringBuilder sb = new StringBuilder();
        
        // System instruction (from template, trusted)
        sb.append("SYSTEM: ").append(template.promptTemplate()).append("\n\n");
        
        // User content (observations, potentially malicious - sanitize)
        sb.append("OBSERVATIONS:\n");
        for (ObservationEntity obs : candidates) {
            // Sanitize user content - escape special characters
            String sanitizedContent = sanitize(obs.getContent());
            String sanitizedTitle = sanitize(obs.getTitle());
            sb.append(String.format("- [%s] %s\n  %s\n",
                sanitize(obs.getSource()),
                sanitizedTitle,
                sanitizedContent));
        }
        
        sb.append("\nOutput according to this schema:\n");
        sb.append(template.outputSchema());
        
        return sb.toString();
    }
    
    /**
     * Sanitize user content to prevent prompt injection.
     */
    private String sanitize(String content) {
        if (content == null) return "";
        // Remove or escape potential instruction patterns
        return content
            .replace("SYSTEM:", "\\[SYSTEM\\]")
            .replace("OBSERVATIONS:", "\\[OBSERVATIONS\\]")
            .replace("Output:", "\\[Output:\\]");
    }
}
```

### 10.3 Cost Control

**Issue**: LLM calls can become expensive.

**Solution**: Caching, rate limiting, and dry-run mode.

```yaml
app.memory.extraction:
  enabled: true
  cost-control:
    # Dry run mode - log what would be extracted but don't call LLM
    dry-run: false
    # Maximum LLM calls per extraction run
    max-calls-per-run: 10
    # Cache extraction results (by observation hash)
    cache-enabled: true
    cache-ttl-hours: 24
    # Rate limiting
    rate-limit:
      calls-per-minute: 20
      burst-size: 5
```

```java
@Service
public class StructuredExtractionService {
    
    @Value("${app.memory.extraction.cost-control.dry-run:false}")
    private boolean dryRun;
    
    @Value("${app.memory.extraction.cost-control.max-calls-per-run:10}")
    private int maxCallsPerRun;
    
    private int callCount = 0;
    
    public <T> T extractWithCostControl(...) {
        if (dryRun) {
            log.info("Dry run mode - would extract: {}", template.name());
            return null;
        }
        
        if (callCount >= maxCallsPerRun) {
            throw new ExtractionException("Max LLM calls reached: " + maxCallsPerRun);
        }
        
        callCount++;
        return extract(...);
    }
}
```

### 10.4 Observability & Debugging

**Issue**: How to debug what LLM extracted? Did it follow the schema?

**Solution**: Structured logging and extraction audit trail.

```java
@Service
public class StructuredExtractionService {
    
    private static final Logger log = LoggerFactory.getLogger(StructuredExtractionService.class);
    
    public <T> ExtractionResult<T> extractWithAudit(...) {
        String requestId = UUID.randomUUID().toString();
        
        log.info("Extraction started: requestId={}, template={}, candidateCount={}", 
            requestId, template.name(), candidates.size());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Build and log prompt (truncated for safety)
            String prompt = buildPrompt(template, candidates);
            log.debug("Extraction prompt (truncated): {}...", 
                prompt.substring(0, Math.min(500, prompt.length())));
            
            // Call LLM
            String response = llmService.chatCompletion(...);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Extraction completed: requestId={}, duration={}ms, responseLength={}", 
                requestId, duration, response.length());
            
            // Audit log
            saveExtractionAudit(requestId, template, candidates, response, duration);
            
            return parseResult(response);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Extraction failed: requestId={}, duration={}ms, error={}", 
                requestId, duration, e.getMessage());
            
            saveExtractionAudit(requestId, template, candidates, null, duration, e);
            throw e;
        }
    }
    
    private void saveExtractionAudit(String requestId, ExtractionTemplate template,
                                    List<ObservationEntity> candidates,
                                    String response, long durationMs, Exception error) {
        // Save to audit table or observation metadata
        ObservationEntity audit = new ObservationEntity();
        audit.setType("extraction_audit");
        audit.setSource("audit:" + template.name());
        audit.setExtractedData(Map.of(
            "requestId", requestId,
            "template", template.name(),
            "candidateCount", candidates.size(),
            "durationMs", durationMs,
            "responseLength", response != null ? response.length() : 0,
            "error", error != null ? error.getMessage() : null
        ));
        observationRepository.save(audit);
    }
}
```

### 10.5 Multi-language Support

**Issue**: Observations may be in Chinese, English, or mixed.

**Solution**: Include language hint in prompt.

```yaml
templates:
  - name: "user_preference"
    language: "auto"  # or "zh", "en", "ja"
    prompt: |
      [Language: {{language}}]
      Extract user preferences from the conversation.
```

```java
private String buildPrompt(ExtractionTemplate template, List<ObservationEntity> candidates) {
    String language = detectLanguage(candidates);
    String prompt = template.promptTemplate()
        .replace("{{language}}", language);  // Or "auto" for LLM to detect
    
    // ... rest of prompt building
}

private String detectLanguage(List<ObservationEntity> candidates) {
    // Simple heuristic: check character ranges
    // Or use LLM to detect
}
```

---

## 11. Error Handling & Recovery Strategies

### 11.1 Failure Modes

| Failure Mode | Impact | Recovery Strategy |
|--------------|--------|-------------------|
| LLM timeout | Extraction fails | Retry with exponential backoff, mark as failed |
| Invalid JSON output | Cannot parse result | Retry up to N times, fallback to raw text |
| Schema validation fails | Result doesn't match schema | Log warning, store raw result, continue |
| Network error | Cannot reach LLM | Circuit breaker, queue for retry |
| Rate limit exceeded | Too many calls | Backoff, queue for later |

### 11.2 Retry with Circuit Breaker

```java
@Service
public class StructuredExtractionService {
    
    private final CircuitBreaker circuitBreaker = CircuitBreaker.of("llm", 
        CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .build());
    
    public <T> T extractWithRetry(ExtractionTemplate template, 
                                   List<ObservationEntity> candidates,
                                   Class<T> outputType) {
        return circuitBreaker.executeSupplier(() -> {
            for (int attempt = 0; attempt < maxRetries; attempt++) {
                try {
                    return extract(template, candidates, outputType);
                } catch (LLMTimeoutException e) {
                    long backoff = calculateBackoff(attempt);
                    log.warn("LLM call failed, retrying in {}ms: {}", backoff, e.getMessage());
                    sleep(backoff);
                } catch (InvalidJsonException e) {
                    log.warn("Invalid JSON from LLM, attempt {}/{}: {}", 
                        attempt + 1, maxRetries, e.getMessage());
                }
            }
            throw new ExtractionException("Failed after " + maxRetries + " attempts");
        });
    }
    
    private long calculateBackoff(int attempt) {
        // Exponential backoff: 1s, 2s, 4s, 8s...
        return (long) Math.pow(2, attempt) * 1000;
    }
}
```

### 11.3 Dead Letter Queue for Failed Extractions

**Implementation**: Store failed extractions as `ObservationEntity` records with type=`extraction_failed`. This avoids a separate database table or in-memory queue.

```java
/**
 * Failed extraction requests are queued for manual review or retry.
 * Stored as ObservationEntity with type="extraction_failed" for persistence.
 */
public record FailedExtraction(
    String requestId,
    String projectPath,
    String templateName,
    List<UUID> observationIds,
    String errorMessage,
    int attemptCount,
    OffsetDateTime failedAt
) {}

// On failure, save to dead letter queue as ObservationEntity
public void handleExtractionFailure(String requestId, String projectPath, ExtractionTemplate template,
                                    List<UUID> observationIds, Exception error, int attemptCount) {
    ObservationEntity failedObs = new ObservationEntity();
    failedObs.setType("extraction_failed");
    failedObs.setSource("dlq:" + template.name());
    failedObs.setProjectPath(projectPath);
    failedObs.setExtractedData(Map.of(
        "requestId", requestId,
        "template", template.name(),
        "observationIds", observationIds.stream().map(UUID::toString).toList(),
        "error", error.getMessage(),
        "attemptCount", attemptCount,
        "failedAt", OffsetDateTime.now().toEpochSecond()
    ));
    observationRepository.save(failedObs);
    
    // Alert if too many failures accumulate
    long failureCount = observationRepository.findByType(projectPath, "extraction_failed", 1000).size();
    if (failureCount > threshold) {
        alertService.alert("Extraction failure rate exceeds threshold: " + failureCount);
    }
}

// Scheduled task to retry dead letter items
// ⚠️ FIX: findByType(null, ...) will NOT work — @Param("project") is non-null.
// Use findByTypeGlobal() (new method) or findDistinctProjects() + per-project findByType().
@Scheduled(fixedRate = 3600000)  // Every hour
public void retryDeadLetterExtractions() {
    // Option A: Use new findByTypeGlobal method (preferred)
    List<ObservationEntity> failedObs = observationRepository.findByTypeGlobal("extraction_failed", 100);
    
    // Option B (fallback): Iterate projects
    // for (String project : observationRepository.findDistinctProjects()) {
    //     List<ObservationEntity> failedObs = observationRepository.findByType(project, "extraction_failed", 100);
    //     // ... process each
    // }
    
    for (ObservationEntity obs : failedObs) {
        Map<String, Object> data = obs.getExtractedData();
        int attemptCount = ((Number) data.get("attemptCount")).intValue();
        if (attemptCount < maxRetries) {
            retryExtraction(obs);
        }
    }
}
```

### 11.4 Schema Validation Failure Handling

```java
public <T> ExtractionResult<T> extractWithValidation(...) {
    String rawResponse = llmService.chatCompletion(...);
    
    try {
        T result = objectMapper.readValue(rawResponse, outputType);
        
        // Validate against schema
        if (!schemaValidator.validate(result, template.outputSchema())) {
            log.warn("Result doesn't match schema, storing raw: requestId={}", requestId);
            // Store as raw text observation instead of structured
            storeRawResult(template, rawResponse);
            return null;  // Or throw
        }
        
        return new ExtractionResult<>(result, extractionObservation);
        
    } catch (JsonProcessingException e) {
        // Try to extract partial information
        T partial = extractPartial(rawResponse, outputType);
        if (partial != null) {
            log.warn("Partial extraction succeeded: requestId={}", requestId);
            return new ExtractionResult<>(partial, extractionObservation);
        }
        throw new ExtractionException("Cannot parse LLM response", e);
    }
}
```

### 11.5 Graceful Degradation

```java
/**
 * If extraction completely fails, the system should still function.
 * Memory refinement should not be blocked by extraction failures.
 */
public void deepRefineWithGracefulDegradation(String projectPath) {
    try {
        // Run existing refinement (must succeed)
        refineObservations(projectPath);
    } catch (Exception e) {
        log.error("Refinement failed, but continuing: {}", e.getMessage());
        // Don't fail the whole process
    }

    try {
        // Run extraction (can fail gracefully)
        runExtraction(projectPath);
    } catch (Exception e) {
        log.warn("Extraction failed, will retry later: {}", e.getMessage());
        // Queue for retry, don't fail the whole process
    }
}
```

---

## 12. Template Lifecycle Management

### 12.1 Hot Reload Without Restart

**Issue**: How to update templates without restarting the application?

**Solution**: Use Spring's `@RefreshScope` or watch configuration files.

```java
@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "app.memory.extraction")
public class ExtractionConfig {

    private boolean enabled = true;
    private List<ExtractionTemplateConfig> templates = new ArrayList<>();

    @PostConstruct
    public void validateTemplates() {
        for (ExtractionTemplateConfig template : templates) {
            validateTemplate(template);
        }
    }
}
```

```yaml
# application.yml
app:
  memory:
    extraction:
      enabled: true
      templates:
        - name: "user_preference"
          # ...
```

**Or use external config file** (recommended for production):

```yaml
# extraction-templates.yml (external, watched by Spring Cloud Config)
app.memory.extraction:
  templates:
    - name: "user_preference"
      # ...
```

### 12.2 Template Enable/Disable Per Template

```java
public record ExtractionTemplate(
    String name,
    boolean enabled,                    // NEW: per-template enable flag
    String description,
    List<String> triggerKeywords,
    List<String> sourceFilter,
    String promptTemplate,
    String outputSchema,
    boolean trackEvolution,
    boolean conflictEnabled
) {}

// In runExtraction
for (ExtractionTemplate template : templates) {
    if (!template.enabled()) {
        log.debug("Template {} is disabled, skipping", template.name());
        continue;
    }
    // ... run extraction
}
```

### 12.3 Template Schema Migration

**Issue**: When template schema changes, how to migrate existing extractions?

**Solution**: Version-based migration with explicit re-extract flag.

```java
public record ExtractionTemplate(
    String name,
    int version,                    // Schema version
    // ...
) {}

// When schema changes, increment version
- name: "user_preference"
  version: 2                       // Incremented from 1
  prompt: |
    [v2] Extract user preferences with new fields...

// Existing v1 extractions are NOT automatically migrated
// Re-extract only when explicitly requested
@PostMapping("/api/extraction/{templateName}/migrate")
public MigrationResult migrateExtractions(
    @PathVariable String templateName,
    @RequestParam(defaultValue = "false") boolean force) {

    ExtractionTemplate newTemplate = getTemplate(templateName);
    List<ObservationEntity> oldExtractions = observationRepository
        .findByType(projectPath, "extracted_" + templateName);

    // Only migrate if versions differ
    for (ObservationEntity extraction : oldExtractions) {
        if (extraction.getSource().endsWith(":v" + newTemplate.version())) {
            continue; // Already migrated
        }

        if (!force && !needsMigration(extraction)) {
            continue;
        }

        // Re-extract with new template
        reExtract(extraction, newTemplate);
    }
}
```

---

## 13. Extraction Result Usage

### 13.1 How Agent Retrieves Extracted Data

**Issue**: After extraction, how does the agent actually use the extracted structured data?

**Solution**: Dedicated extraction retrieval API + integration with prompts.

```java
/**
 * Agent queries extracted data directly.
 */
@RestController
@RequestMapping("/api/extraction")
public class ExtractionController {

    private final ObservationRepository observationRepository;

    @GetMapping("/{templateName}/latest")
    public ExtractionInfo getLatestExtraction(
            @PathVariable String templateName,
            @RequestParam String projectPath) {

        List<ObservationEntity> extractions = observationRepository
            .findByType(projectPath, "extracted_" + templateName, 1);

        if (extractions.isEmpty()) {
            return null;
        }

        return toExtractionInfo(extractions.get(0));
    }

    @GetMapping("/{templateName}/history")
    public List<ExtractionInfo> getExtractionHistory(
            @PathVariable String templateName,
            @RequestParam String projectPath,
            @RequestParam(defaultValue = "10") int limit) {

        List<ObservationEntity> extractions = observationRepository
            .findByType(projectPath, "extracted_" + templateName, limit);

        return extractions.stream()
            .map(this::toExtractionInfo)
            .collect(Collectors.toList());
    }
}
```

### 13.2 Integration with ChatClient Prompts

**Issue**: How to include extracted data in AI prompts?

**Solution**: Provide extracted data as system context.

```java
@Service
public class ExtractionPromptService {

    public String buildExtractionContext(String projectPath, List<String> templateNames) {
        StringBuilder context = new StringBuilder();
        context.append("=== EXTRACTED INFORMATION ===\n\n");

        for (String templateName : templateNames) {
            List<ObservationEntity> extractions = observationRepository
                .findByType(projectPath, "extracted_" + templateName, 5);

            if (!extractions.isEmpty()) {
                context.append(String.format("[%s]\n", templateName));
                for (ObservationEntity ext : extractions) {
                    context.append(formatExtraction(ext));
                }
                context.append("\n");
            }
        }

        return context.toString();
    }

    // Agent uses this in prompt
    public void chatWithContext(String projectPath, String userMessage) {
        String extractionContext = buildExtractionContext(
            projectPath,
            List.of("allergy_info", "important_dates", "user_preference")
        );

        String prompt = String.format("""
            %s

            USER: %s
            """,
            extractionContext,
            userMessage
        );

        // Call AI with enriched context
        chatClient.prompt().system(prompt).user(userMessage).call();
    }
}
```

### 13.3 Structured Extraction for Specific Use Cases

```java
// Example: ChatBot for family assistant
@RestController
class FamilyAssistantController {

    @GetMapping("/allergies")
    public String getAllergies(@RequestParam String family) {
        // Query extracted allergy info
        List<ObservationEntity> allergies = observationRepository
            .findByType("/family/" + family, "extracted_allergy_info", 10);

        return allergies.stream()
            .map(this::formatAllergy)
            .collect(Collectors.joining("\n"));
    }

    @GetMapping("/dates")
    public String getImportantDates(@RequestParam String family) {
        List<ObservationEntity> dates = observationRepository
            .findByType("/family/" + family, "extracted_important_dates", 10);

        return dates.stream()
            .map(this::formatDate)
            .collect(Collectors.joining("\n"));
    }
}
```

---

## 14. Testing Strategy

### 14.1 Unit Tests

```java
@ExtendWith(MockitoExtension.class)
class StructuredExtractionServiceTest {

    @Mock
    private LlmService llmService;

    @Mock
    private ObservationRepository observationRepository;

    @InjectMocks
    private StructuredExtractionService service;

    @Test
    void shouldExtractWithValidTemplate() {
        // Given
        ExtractionTemplate template = new ExtractionTemplate(
            "test", true, "Test extraction",
            List.of("test"),
            List.of("user_statement"),
            "Extract test info",
            "{\"type\": \"object\"}",
            false, false
        );

        when(observationRepository.findBySourceIn(anyString(), anyList(), anyInt()))
            .thenReturn(List.of(createTestObservation()));
        when(llmService.chatCompletionStructured(eq(template), any()))
            .thenReturn(Map.of("result", "value"));

        // When
        var result = service.extractByTemplate("/test", template, Map.class);

        // Then
        assertNotNull(result);
        verify(observationRepository).save(any());
    }

    @Test
    void shouldSkipDisabledTemplate() {
        ExtractionTemplate template = new ExtractionTemplate(
            "disabled", false, "Disabled", // disabled=true
            List.of(), List.of(), "Prompt", "{}", false, false
        );

        service.runExtraction("/test");

        verifyNoInteractions(llmService);
    }
}
```

### 14.2 Integration Tests with Mock LLM

```java
@SpringBootTest
class ExtractionIntegrationTest {

    @Autowired
    private StructuredExtractionService service;

    @MockBean
    private LlmService llmService;  // Mock LLM

    @Test
    void shouldHandleLLMTimeout() {
        // Given
        when(llmService.chatCompletionStructured(any(), any()))
            .thenThrow(new LLMTimeoutException("timeout"));

        ExtractionTemplate template = createTemplate();

        // When & Then
        assertThrows(ExtractionException.class, () ->
            service.extractByTemplate("/test", template, Map.class)
        );

        // Verify dead letter queue
        List<ObservationEntity> failed = observationRepository
            .findByType("/test", "extraction_failed");
        assertEquals(1, failed.size());
    }
}
```

### 14.3 Template Validation Tests

```java
@Test
void shouldRejectInvalidTemplate() {
    ExtractionTemplate invalid = new ExtractionTemplate(
        "",  // Invalid: empty name
        true, "", List.of(), List.of(), "", "", false, false
    );

    assertThrows(IllegalArgumentException.class, () ->
        validateTemplate(invalid)
    );
}

@Test
void shouldValidateOutputSchema() {
    String validSchema = """
        {
            "type": "object",
            "properties": {
                "name": {"type": "string"}
            }
        }
        """;

    assertTrue(isValidJsonSchema(validSchema));
}
```

---

## 15. Implementation Bootstrap Checklist (Phase 3.1)

This section provides a concrete, step-by-step guide for implementing Phase 3.1.

### 15.1 Prerequisites (Must Complete First)

| # | Task | File | Notes |
|---|------|------|-------|
| 1 | Add `findBySourceIn()` | `ObservationRepository.java` | `List<String> sources` param, native SQL with `IN (:sources)` |
| 2 | Add `findNewObservations()` | `ObservationRepository.java` | `Long sinceEpoch` param for incremental extraction |
| 3 | Add `findByTypeGlobal()` | `ObservationRepository.java` | Cross-project query for DLQ — `findByType(null, ...)` will NOT work because `@Param("project")` is non-null |
| 4 | Add `findByTypeLike()` | `ObservationRepository.java` | **NEW (v14)**: Wildcard type query using `LIKE`. `findByType` uses exact match (`type = :type`), so `findByType(project, "extracted_%", 50)` does NOT match `extracted_user_preference`. Either add this method or iterate known template names (see section 15.8). |
| 5 | Add `chatCompletionStructured()` | `LlmService.java` | Uses `BeanOutputConverter<T>` from `org.springframework.ai.converter` |
| 6 | Add `findByContentSessionIdAndType()` | `ObservationRepository.java` | **NEW (v17)**: Find existing extraction by session ID + type for merge logic |
| 7 | Add `findByUserId()` | `SessionRepository.java` | **NEW (v17)**: Find sessions by user_id for user grouping |
| 8 | Add `findSessionIdsByUserIdAndProject()` | `SessionRepository.java` | **NEW (v17)**: Find session IDs by user + project for extraction grouping |

**New repository method #6** (for merge logic):
```java
// Find existing extraction result by session ID + type (for merge)
@Query(value = """
    SELECT * FROM mem_observations
    WHERE content_session_id = :sessionId
    AND type = :type
    ORDER BY created_at_epoch DESC
    LIMIT :limit
    """, nativeQuery = true)
List<ObservationEntity> findByContentSessionIdAndType(
    @Param("sessionId") String sessionId,
    @Param("type") String type,
    @Param("limit") int limit
);
```

**New repository methods #7-8** (SessionRepository, for user grouping):
```java
// Find all sessions for a user
List<SessionEntity> findByUserId(String userId);

// Find session IDs for a user within a project
@Query("SELECT s.contentSessionId FROM SessionEntity s WHERE s.userId = :userId AND s.projectPath = :project")
List<String> findSessionIdsByUserIdAndProject(@Param("userId") String userId, @Param("project") String project);
```

**Flyway Migration V15** (for user_id field):
```sql
ALTER TABLE mem_sessions ADD COLUMN user_id VARCHAR(255);
CREATE INDEX idx_mem_sessions_user_id ON mem_sessions(user_id);
```

**Critical fix for DLQ (section 11.3)**: The current dead letter queue retry code uses `findByType(null, "extraction_failed", 100)` — this will fail because `findByType` requires a non-null `@Param("project")`. Two options:

```java
// Option A: New repository method for global (cross-project) queries
@Query(value = """
    SELECT * FROM mem_observations
    WHERE type = :type
    ORDER BY created_at_epoch DESC
    LIMIT :limit
    """, nativeQuery = true)
List<ObservationEntity> findByTypeGlobal(
    @Param("type") String type,
    @Param("limit") int limit
);

// Option B: Use findDistinctProjects() + per-project findByType() (existing methods)
// Less efficient but no new repository code needed
```

### 15.2 New Files to Create

| File | Purpose |
|------|---------|
| `service/StructuredExtractionService.java` | Core extraction engine |
| `model/ExtractionTemplate.java` | Record or POJO for template config |
| `model/ExtractionResult.java` | Extraction result wrapper |
| `model/ExtractionState.java` | Incremental extraction state |
| `config/ExtractionConfig.java` | `@ConfigurationProperties` for templates |
| `controller/ExtractionController.java` | Query API for extracted data |

### 15.3 Record vs POJO for ExtractionTemplate

**Important architectural decision**: `ExtractionTemplate` as a Java `record` has implications for Spring Boot configuration binding.

**Problem**: Spring Boot `@ConfigurationProperties` with records requires **constructor parameter names** to match YAML keys. With nested lists of records, this can be fragile.

**Recommended approach**: Use a POJO with `@ConfigurationProperties` at the config level, not the record directly.

```java
// config/ExtractionConfig.java
@Configuration
@ConfigurationProperties(prefix = "app.memory.extraction")
public class ExtractionConfig {
    private boolean enabled = true;
    private List<ExtractionTemplate> templates = new ArrayList<>();
    private CostControl costControl = new CostControl();
    
    // Getters/setters required for @ConfigurationProperties binding
    
    public static class ExtractionTemplate {
        private String name;
        private boolean enabled = true;
        private String templateClass = "java.util.Map";  // Default to flexible Map
        private String description;
        private List<String> triggerKeywords = List.of();
        private List<String> sourceFilter = List.of();
        private String prompt;              // Maps from YAML "prompt"
        private String outputSchema;        // Maps from YAML "output-schema"
        private boolean trackEvolution = false;
        private boolean conflictEnabled = false;
        
        // Getters/setters...
        
        /** Convert to internal model (record) if needed */
        public com.ablueforce.cortexce.model.ExtractionTemplate toTemplate() {
            return new com.ablueforce.cortexce.model.ExtractionTemplate(
                name, enabled, templateClass, description,
                triggerKeywords, sourceFilter, prompt, outputSchema,
                trackEvolution, conflictEnabled
            );
        }
    }
    
    public static class CostControl {
        private boolean dryRun = false;
        private int maxCallsPerRun = 10;
        private boolean cacheEnabled = true;
        private int cacheTtlHours = 24;
        // getters/setters...
    }
}
```

**Why not use record directly?**
- Spring Boot 3.3 supports records in `@ConfigurationProperties`, but nested `List<Record>` binding is fragile
- YAML `kebab-case` keys (e.g., `template-class`) need `@JsonProperty` or relaxed binding magic
- POJO approach is more debuggable and allows default values

### 15.4 LlmService.chatCompletionStructured() Implementation

```java
// Add to LlmService.java
@SuppressWarnings("unchecked")
public <T> T chatCompletionStructured(String systemPrompt, String userPrompt, Class<T> outputType) {
    ChatClient chatClient = this.chatClient.orElseThrow(() ->
        new IllegalStateException("AI not configured."));

    if (Map.class.isAssignableFrom(outputType)) {
        // Flexible Map output — use MapOutputConverter
        MapOutputConverter converter = new MapOutputConverter();
        String response = chatClient.prompt()
            .system(systemPrompt + "\n\n" + converter.getFormat())
            .user(userPrompt)
            .call()
            .content();
        return (T) converter.convert(response);
    } else {
        // POJO output — use BeanOutputConverter
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(outputType);
        String response = chatClient.prompt()
            .system(systemPrompt + "\n\n" + converter.getFormat())
            .user(userPrompt)
            .call()
            .content();
        return converter.convert(response);
    }
}
```

**Note**: Both converters' `getFormat()` returns a JSON Schema string that gets appended to the system prompt. The LLM sees the schema and (ideally) produces matching JSON. The converter then parses the response.

### 15.5 DeepRefine Integration: Execution Order

**Critical**: Extraction must NOT run during `quickRefine()` — only during `deepRefineProjectMemories()`.

```
deepRefineProjectMemories(projectPath)
├── Step 1: Find refinement candidates
├── Step 2: refineObservations() — merge/rewrite existing memories
├── Step 3: [NEW] Run structured extraction on refined state
│   └── StructuredExtractionService.runExtraction(projectPath)
│       ├── For each enabled template:
│       │   ├── Get incremental candidates (since last extraction)
│       │   ├── Call LLM with structured output
│       │   ├── Store results as extracted_observations
│       │   └── Update extraction state
│       └── On failure: log + queue for retry (don't block refinement)
└── Step 4: Return — extraction failures are non-blocking
```

```java
// In MemoryRefineService.deepRefineProjectMemories()
public void deepRefineProjectMemories(String projectPath) {
    // ... existing refinement steps ...
    
    // Phase 3: Structured extraction (non-blocking)
    if (extractionConfig.isEnabled()) {
        try {
            extractionService.runExtraction(projectPath);
            log.info("Extraction completed for project: {}", projectPath);
        } catch (Exception e) {
            log.warn("Extraction failed for project {}, queued for retry: {}", 
                projectPath, e.getMessage());
            // Non-blocking: refinement succeeded even if extraction failed
        }
    }
}
```

### 15.6 Validation Checklist Before Implementation

Before writing any code, verify:

- [ ] Spring AI 1.1.2 includes `org.springframework.ai.converter` package (check Maven dependency tree)
- [ ] `BeanOutputConverter` and `MapOutputConverter` are available in the classpath
- [ ] `ObservationEntity.getExtractedData()` returns `Map<String, Object>` (confirmed in V14)
- [ ] `ObservationEntity.setExtractedData(Map<String, Object>)` setter exists
- [ ] `ObjectMapper` bean is available in the Spring context (for `convertToMap()`)
- [ ] YAML configuration loading works with the chosen config binding approach

### 15.6 Transaction Safety for Extraction State

**Problem**: Section 7.1's `updateExtractionState()` uses delete-then-save without `@Transactional`. If save fails after delete succeeds, extraction state is lost and the next run re-processes everything.

**Fix**: Use `@Transactional` on the state management method. Also consider upsert SQL for atomicity.

```java
/**
 * Update extraction state atomically.
 * Without @Transactional, delete-then-save can corrupt state if save fails.
 */
@Transactional
private void updateExtractionState(String projectPath, String templateName, OffsetDateTime now) {
    // Delete old state for this project+template
    observationRepository.findByType(projectPath, "extraction_state", 100).stream()
        .filter(o -> o.getSource() != null && o.getSource().equals("state:" + templateName))
        .forEach(o -> observationRepository.deleteById(o.getId()));
    
    // Flush delete before insert (ensures delete is committed within same transaction)
    observationRepository.flush();
    
    // Insert new state
    ObservationEntity stateObs = new ObservationEntity();
    stateObs.setProjectPath(projectPath);
    stateObs.setType("extraction_state");
    stateObs.setSource("state:" + templateName);
    stateObs.setCreatedAt(now);
    stateObs.setCreatedAtEpoch(now.toEpochSecond() * 1000L);
    stateObs.setExtractedData(Map.of(
        "template", templateName,
        "lastExtractedAt", now.toEpochSecond()
    ));
    observationRepository.save(stateObs);
}
```

**Alternative (preferred for production)**: Use raw SQL upsert to avoid the delete-then-save race entirely:

```java
// Requires new repository method
@Modifying
@Query(value = """
    INSERT INTO mem_observations (id, project_path, type, source, created_at, created_at_epoch, extracted_data)
    VALUES (gen_random_uuid(), :project, 'extraction_state', :source, :now, :nowEpoch, CAST(:data AS jsonb))
    ON CONFLICT (source) WHERE type = 'extraction_state'
    DO UPDATE SET
        extracted_data = CAST(:data AS jsonb),
        created_at = :now,
        created_at_epoch = :nowEpoch
    """, nativeQuery = true)
void upsertExtractionState(
    @Param("project") String project,
    @Param("source") String source,
    @Param("now") OffsetDateTime now,
    @Param("nowEpoch") Long nowEpoch,
    @Param("data") String extractedDataJson
);
```

**NOTE**: The upsert approach requires a partial unique index on `(source) WHERE type = 'extraction_state'` in the database. This is a DB migration step.

### 15.7 Concurrency Control

**Problem**: Extraction could be triggered by both `deepRefine` (on SessionEnd) and the scheduled task simultaneously. Without protection, the same observations get processed twice, producing duplicate or conflicting results.

**Options**:

| Strategy | Mechanism | Complexity | Recommended |
|----------|-----------|------------|-------------|
| **A. Spring `@Scheduler` lock** | `@SchedulerLock` (ShedLock) | Medium | ✅ For multi-instance |
| **B. In-memory `ReentrantLock`** | Per-project lock map | Low | ✅ For single-instance |
| **C. DB advisory lock** | `pg_try_advisory_lock()` | Medium | ✅ For DB-centric |
| **D. Optimistic: dedup by observation ID** | Track processed IDs | Low | ❌ Doesn't prevent double work |

**Recommended**: **Option B** for single-instance deployment (current architecture). **Option A** if future multi-instance.

```java
@Service
public class StructuredExtractionService {

    // Per-project lock to prevent concurrent extraction
    private final ConcurrentHashMap<String, ReentrantLock> projectLocks = new ConcurrentHashMap<>();

    public void runExtraction(String projectPath) {
        ReentrantLock lock = projectLocks.computeIfAbsent(projectPath, k -> new ReentrantLock());
        
        if (!lock.tryLock()) {
            log.info("Extraction already running for project: {}, skipping", projectPath);
            return;
        }
        
        try {
            for (ExtractionTemplate template : templates) {
                if (!template.enabled()) continue;
                runTemplateExtraction(projectPath, template);
            }
        } finally {
            lock.unlock();
        }
    }
}
```

### 15.8 ICL Prompt Integration

**Issue**: How does the ICL prompt system (`/api/memory/icl-prompt`) incorporate extracted data?

**Current flow**: ICL prompt builds context from raw observations. Extraction produces structured facts that should be surfaced separately.

**Design**: Add extracted data as a dedicated section in the ICL prompt, separate from raw observations.

```java
// In ContextService.java (ICL prompt generation)
public String buildIclPrompt(String projectPath, String task, int maxChars) {
    StringBuilder context = new StringBuilder();
    
    // Section 1: Extracted structured facts (from Phase 3 extraction)
    // ⚠️ BUG FIX (v14): findByType uses exact match (type = :type), NOT LIKE.
    // findByType(project, "extracted_%", 50) will NOT match "extracted_user_preference"!
    // Solution: Use new findByTypeLike() method with LIKE pattern, OR iterate known templates.
    List<ObservationEntity> extractions = observationRepository
        .findByTypeLike(projectPath, "extracted_%", 50)  // Uses LIKE, not exact match
        .stream()
        .filter(o -> o.getSource() != null && o.getSource().startsWith("extraction:"))
        .toList();
    
    if (!extractions.isEmpty()) {
        context.append("=== EXTRACTED FACTS ===\n");
        for (ObservationEntity ext : extractions) {
            String templateName = ext.getSource().replace("extraction:", "");
            context.append(String.format("[%s]: %s\n", templateName, formatExtractedData(ext.getExtractedData())));
        }
        context.append("\n");
    }
    
    // Section 2: Raw observations (existing behavior)
    context.append("=== RELEVANT MEMORIES ===\n");
    // ... existing observation search + formatting
    
    // Truncate to maxChars
    return truncateToMaxChars(context.toString(), maxChars);
}
```

**Alternative approach** (no new repository method): Iterate known template names instead of wildcard:

```java
// If findByTypeLike is not added, use template name iteration:
List<String> templateNames = extractionConfig.getTemplates().stream()
    .map(ExtractionConfig.ExtractionTemplate::getName)
    .toList();
for (String templateName : templateNames) {
    List<ObservationEntity> extractions = observationRepository
        .findByType(projectPath, "extracted_" + templateName, 10);  // Exact match, works today
    // ... append to context
}
```

**Also**: Expose extraction data through the existing experience API (`/api/memory/experiences`) by treating `extracted_*` types as a first-class observation category.

```java
// Add to ExperienceRequest
public record ExperienceRequest(
    String project,
    String task,
    List<String> requiredConcepts,
    String source,
    boolean includeExtractions  // NEW: include extracted facts in response
) {}

// In MemoryManagementService.getExperiences()
if (request.includeExtractions()) {
    // Use findByTypeLike (not findByType) for wildcard pattern
    List<ObservationEntity> extractions = observationRepository
        .findByTypeLike(project, "extracted_%", 20);
    experiences.addAll(extractions.stream().map(this::toExperience).toList());
}
```

### 15.9 Token Counting Without TokenService

**Problem**: Section 10.1 references `TokenService.calculateObservationTokens()` but this method does NOT exist. How to estimate tokens for batching?

**Pragmatic solution**: Use character-based estimation (1 token ≈ 4 chars for English, 1.5-2 chars for Chinese).

```java
/**
 * Estimate token count for an observation.
 * Uses character-based heuristic since TokenService.calculateObservationTokens() doesn't exist.
 * 
 * Formula: ~4 chars per token for ASCII, ~2 chars per CJK character.
 */
private int estimateTokens(ObservationEntity obs) {
    String text = (obs.getTitle() != null ? obs.getTitle() : "") + " " + 
                  (obs.getContent() != null ? obs.getContent() : "");
    
    int cjkCount = 0;
    int asciiCount = 0;
    for (char c : text.toCharArray()) {
        if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
            cjkCount++;
        } else {
            asciiCount++;
        }
    }
    
    return (int) (cjkCount / 1.5 + asciiCount / 4.0);
}

private List<List<ObservationEntity>> chunkByTokenCount(List<ObservationEntity> observations, int maxTokens) {
    List<List<ObservationEntity>> batches = new ArrayList<>();
    List<ObservationEntity> currentBatch = new ArrayList<>();
    int currentTokens = 0;
    
    for (ObservationEntity obs : observations) {
        int tokens = estimateTokens(obs);
        if (currentTokens + tokens > maxTokens && !currentBatch.isEmpty()) {
            batches.add(currentBatch);
            currentBatch = new ArrayList<>();
            currentTokens = 0;
        }
        currentBatch.add(obs);
        currentTokens += tokens;
    }
    
    if (!currentBatch.isEmpty()) {
        batches.add(currentBatch);
    }
    
    return batches;
}
```

**NOTE**: This is a pragmatic heuristic. For production, invest in proper tokenization via tiktoken-java or JTokkit.

### 15.10 Validation Checklist Before Implementation (Updated)

Before writing any code, verify:

- [ ] Spring AI 1.1.2 includes `org.springframework.ai.converter` package (check Maven dependency tree)
- [ ] `BeanOutputConverter` and `MapOutputConverter` are available in the classpath
- [ ] `ObservationEntity.getExtractedData()` returns `Map<String, Object>` (confirmed in V14)
- [ ] `ObservationEntity.setExtractedData(Map<String, Object>)` setter exists
- [ ] `ObjectMapper` bean is available in the Spring context (for `convertToMap()`)
- [ ] YAML configuration loading works with the chosen config binding approach
- [ ] `ObservationRepository` has `findByTypeLike(project, "extracted_%", limit)` with LIKE support — OR use iteration over known template names with existing `findByType` (see section 15.8 for both approaches)
- [ ] `@Transactional` support is available (Spring Data JPA, no custom transaction manager)

---

## 16. Architecture Decision Records (ADRs)

Captured for future reference — decisions that shaped the design.

### ADR-1: Store extractions as ObservationEntity (not separate table)

**Decision**: Use existing `ObservationEntity` with `type="extracted_{template}"` for extraction results.

**Rationale**:
- No new database table needed (avoids schema migration risk)
- Existing repository methods (`findByType`, `findBySource`) work immediately
- `extractedData` JSONB column already supports arbitrary structured data
- Consistent with existing "observation = unit of memory" model

**Tradeoff**: Observation table grows with both raw observations and extraction results. May need partitioning at scale (>10M rows).

### ADR-2: BeanOutputConverter<T> over raw JSON parsing

**Decision**: Use Spring AI's `BeanOutputConverter<T>` instead of manual `ObjectMapper.readValue()`.

**Rationale**:
- Auto-generates JSON Schema from Java class → better LLM compliance
- `getFormat()` injects schema instructions into system prompt
- Handles type coercion and nested objects automatically
- Industry-standard approach for Spring AI structured output

**Tradeoff**: Schema enforcement is prompt-based, not API-level. LLM may still return non-compliant JSON → needs retry logic.

### ADR-3: Separate pipeline from MemoryRefineService

**Decision**: Extraction runs as a separate step, NOT mixed with refinement logic.

**Rationale**:
- Refinement and extraction serve different purposes (prune vs extract)
- Extraction failures should not block refinement (and vice versa)
- Different cost profiles (refinement = moderate LLM, extraction = high LLM)
- Independent scheduling (refinement on SessionEnd, extraction on schedule)

**Tradeoff**: No shared context between refinement and extraction. Each runs independently.

### ADR-4: POJO + Map hybrid over dynamic class generation

**Decision**: Use predefined POJO classes for stable templates, `Map<String, Object>` for flexible/experimental schemas.

**Rationale**:
- Dynamic class generation (bytecode) is fragile and hard to debug
- POJO gives type safety for critical data (allergies, medical info)
- Map fallback provides maximum flexibility without code changes
- YAML `template-class` field controls the pattern per template

**Tradeoff**: New stable template requires a new Java class + recompilation.

---

## 17. Extraction Idempotency (v14)

### 17.1 Problem: Duplicate Results from Re-running Extraction

**Issue**: If extraction runs twice on the same observations (e.g., due to crash recovery or manual re-trigger), it produces duplicate `extracted_{template}` observations. Over time, this pollutes the observation store with redundant data.

**Current gap**: `runTemplateExtraction()` calls `storeExtractionResult()` which always creates a new `ObservationEntity`. There is no deduplication check.

### 17.2 Idempotency Strategy

**Approach**: Deduplicate by content hash + template name within a time window (aligned with existing `findDuplicateByContentHash` pattern from Migration 22).

```java
/**
 * Check if an extraction result already exists for this template + source observations.
 * Uses observation content hash for deduplication.
 */
private boolean extractionAlreadyExists(ExtractionTemplate template, 
                                         List<ObservationEntity> sourceObservations) {
    // Build a composite hash from source observation IDs
    String compositeHash = computeCompositeHash(template.name(), sourceObservations);
    
    // Check if an extraction with this hash already exists (within 24h window)
    long windowStart = OffsetDateTime.now().minusHours(24).toEpochSecond() * 1000L;
    return observationRepository
        .findDuplicateByContentHash(compositeHash, windowStart)
        .isPresent();
}

/**
 * Store extraction result with idempotency check.
 */
private <T> ExtractionResult<T> storeExtractionResultIdempotent(
        ExtractionTemplate template, T result, List<ObservationEntity> sourceObservations) {
    
    if (extractionAlreadyExists(template, sourceObservations)) {
        log.info("Extraction already exists for template {}, skipping", template.name());
        return null;
    }
    
    return storeExtractionResult(template, result, sourceObservations);
}
```

**Alternative (simpler)**: Use extraction state (section 7.1) as the idempotency boundary — if state's `lastExtractedAt` is newer than all source observations, skip. This is already partially implemented via incremental extraction, but doesn't protect against re-running within the same time window.

### 17.3 Idempotency for State Updates

The `updateExtractionState()` method (section 15.6) uses delete-then-save, which is NOT idempotent. If called twice rapidly, it could delete and re-create the state unnecessarily. The `@Transactional` wrapper prevents corruption but not duplicate work.

**Recommendation**: Add a guard clause:

```java
@Transactional
private void updateExtractionState(String projectPath, String templateName, OffsetDateTime now) {
    // Guard: skip if state already reflects this timestamp (within 1 second tolerance)
    ExtractionState existing = getExtractionState(projectPath, templateName);
    if (existing != null && existing.lastExtractedAt().isAfter(now.minusSeconds(1))) {
        return; // Already up-to-date
    }
    // ... delete-then-save logic
}
```

---

## 18. Observation Type Namespace Reservation (v14)

### 18.1 Problem: User-Created Types Collide with System Types

**Issue**: The system uses observation `type` values with special prefixes for internal purposes:
- `extracted_{template}` — extraction results
- `extraction_state` — incremental extraction state tracking
- `extraction_failed` — dead letter queue
- `extraction_audit` — audit trail

If a user or agent creates observations with these type values, it causes data pollution and query conflicts. For example, `findByType(project, "extraction_state", 100)` would return both system state records AND user-created observations.

### 18.2 Namespace Convention

**Convention**: Reserve the `extraction_*` prefix for system use. User observations should NOT use types starting with `extraction_`.

| Type Pattern | Owner | Purpose |
|-------------|-------|---------|
| `extracted_{template}` | System | Extraction results |
| `extraction_state` | System | Incremental state |
| `extraction_failed` | System | Dead letter queue |
| `extraction_audit` | System | Audit trail |
| Any other type | User/Agent | Normal observations |

### 18.3 Enforcement Options

| Strategy | Implementation | Strictness |
|----------|---------------|------------|
| **A. Convention only** | Document in API docs, rely on agent behavior | Soft |
| **B. Validation in `POST /api/ingest/observation`** | Reject `type` starting with `extraction_` | Hard |
| **C. Separate prefix** | Use `__system__` prefix instead of `extraction_` | Harder to collide |

**Recommendation**: **Option B** — add validation in the observation creation endpoint to reject reserved type prefixes. This prevents accidental collisions without being overly restrictive.

```java
// In ObservationController or IngestionService
private static final Set<String> RESERVED_TYPE_PREFIXES = Set.of("extraction_");

private void validateObservationType(String type) {
    if (type != null) {
        for (String prefix : RESERVED_TYPE_PREFIXES) {
            if (type.startsWith(prefix)) {
                throw new IllegalArgumentException(
                    "Type prefix '" + prefix + "' is reserved for system use. " +
                    "Use a different type name.");
            }
        }
    }
}
```

---

## 19. Implementation Readiness & Practical Gaps (v15)

This section addresses practical issues discovered during implementation preparation that aren't covered by the design above.

### 19.1 Flyway Migration for Upsert Support

**Issue**: Section 15.6 proposes SQL upsert for atomic state updates, but this requires a **partial unique index** that doesn't exist yet:

```sql
-- Required: partial unique index for upsert ON CONFLICT clause
-- Without this, the upsert SQL in section 15.6 will fail
CREATE UNIQUE INDEX IF NOT EXISTS idx_extraction_state_source
ON mem_observations (source) WHERE type = 'extraction_state';
```

**Decision**: This should be a new Flyway migration (V15 or later). However, this is only needed if we choose the upsert approach. The `@Transactional` delete-then-save approach (section 15.6 alternative) works WITHOUT any schema changes.

**Recommendation**: Start with `@Transactional` approach for Phase 3.1. Add upsert migration only if state corruption is observed in testing.

### 19.2 Prerequisite Implementation Sequencing

**Current state**: Section 15.1 lists 5 prerequisites but doesn't specify implementation order or dependencies.

**Recommended order** (least dependencies first):

| Order | Prerequisite | Dependencies | Effort |
|-------|-------------|--------------|--------|
| 1 | `findBySourceIn()` | None (new query) | 30 min |
| 2 | `findByTypeGlobal()` | None (new query) | 15 min |
| 3 | `findByTypeLike()` | None (new query) | 15 min |
| 4 | `findNewObservations()` | None (new query) | 30 min |
| 5 | `chatCompletionStructured()` | Spring AI converters on classpath | 1-2 hours |

**Total estimated effort**: 2.5-3 hours for all prerequisites.

**Note**: Items 1-4 are pure repository methods (add `@Query` annotation to `ObservationRepository`). Item 5 requires understanding Spring AI's converter API and testing with actual LLM calls.

**Verification step**: After implementing prerequisites, run the existing regression test (43/43) to confirm nothing breaks, THEN start StructuredExtractionService.

### 19.3 Initial Run Cost Explosion Prevention

**Issue NOT addressed in current design**: When a new template is added or extraction runs for the first time, `state.lastExtractedAt` is null, so ALL matching observations become candidates. For projects with thousands of observations, this triggers a massive number of LLM calls.

**Example**: Project with 5000 observations matching `sourceFilter: ["user_statement"]` + template with no prior state = 5000 observations ÷ 20 per batch = 250 LLM calls at ~$0.01 each = $2.50 per template. With 5 templates = $12.50 for a single initial run.

**Solution**: Cap initial run size and use progressive extraction.

```java
@Value("${app.memory.extraction.initial-run-max-candidates:100}")
private int initialRunMaxCandidates;

private List<ObservationEntity> getCandidates(String projectPath, ExtractionTemplate template, ExtractionState state) {
    if (state == null) {
        // First run: cap candidates to prevent cost explosion
        log.info("First extraction run for template {}, limiting to {} candidates",
            template.name(), initialRunMaxCandidates);
        return observationRepository.findBySourceIn(projectPath, template.sourceFilter(), initialRunMaxCandidates);
    }
    // Subsequent runs: only new observations
    return observationRepository.findNewObservations(
        projectPath, template.sourceFilter(), state.lastExtractedAt().toEpochSecond() * 1000L, 1000);
}
```

**Add to YAML config**:
```yaml
app.memory.extraction:
  initial-run-max-candidates: 100    # Cap first run to prevent cost explosion
```

### 19.4 Extraction Query API Endpoint Specification

**Issue**: Section 7.2 shows conceptual API design but lacks concrete endpoint specification for Phase 3.1.

**Proposed endpoints for ExtractionController**:

```
GET  /api/extraction/{templateName}/latest?projectPath=...
     → Returns most recent extraction result for template

GET  /api/extraction/{templateName}/history?projectPath=...&limit=10
     → Returns extraction history (all extracted_observations of this type)

GET  /api/extraction/{templateName}/search?projectPath=...&field=allergens&value=花生
     → JSONB path query on extractedData

POST /api/extraction/run?projectPath=...
     → Manually trigger extraction for a project

GET  /api/extraction/status?projectPath=...
     → Show extraction state per template (lastExtractedAt, candidate count)
```

**JSONB search implementation** (for field/value queries):
```java
// Requires native SQL with JSONB operator
@Query(value = """
    SELECT * FROM mem_observations
    WHERE project_path = :project
    AND type = :type
    AND extracted_data ->> :field = :value
    ORDER BY created_at_epoch DESC
    LIMIT :limit
    """, nativeQuery = true)
List<ObservationEntity> findByExtractedDataField(
    @Param("project") String project,
    @Param("type") String type,
    @Param("field") String field,
    @Param("value") String value,
    @Param("limit") int limit
);
```

**NOTE**: `extracted_data ->> :field` works for top-level string fields. For nested queries or array containment (`allergens @> '["花生"]'`), use `@>` JSONB containment operator with a separate method.

### 19.5 Conflict Resolution: Auto vs Manual

**Issue**: Section 3.3 defines conflict types but doesn't specify resolution strategy. The design says "handleConflict" but doesn't answer: auto-resolve or require manual review?

**Recommendation**: **Default to auto-resolve with audit trail**. Manual review adds UX complexity that isn't justified for Phase 3.1.

| Conflict Type | Auto Resolution | Audit |
|---------------|----------------|-------|
| DIRECT_CONTRADICTION | Keep newer (most recent extraction) | Log old → new transition |
| EVOLUTION | Record history, keep both (latest is current) | Log evolution event |
| NONE | No action | No log |

**Rationale**: Observations already have `qualityScore` and `refinedAt` fields. Auto-resolution with audit logging preserves history while keeping the system simple. Manual review can be added in Phase 3.5 if needed.

### 19.6 `findByTypeLike` vs Template Iteration: Decision

**Issue**: Section 15.8 presents two alternatives for ICL prompt integration: wildcard `findByTypeLike` vs iterating known template names.

**Analysis**:

| Approach | Queries | Flexibility | Complexity |
|----------|---------|-------------|------------|
| `findByTypeLike(project, "extracted_%")` | 1 query | Catches ALL extractions including unknown | Requires new method |
| Template name iteration | N queries (N = template count) | Only known templates | No new method needed |

**Decision**: **Use `findByTypeLike` for ICL prompt integration**. Reasons:
- ICL prompt needs ALL extracted facts, not just known templates
- Single query is more efficient than N queries
- The `findByTypeLike` method is already a prerequisite (section 15.1 #4)

Template iteration is a valid fallback if `findByTypeLike` is delayed, but should be replaced once the method exists.

### 19.7 Implementation Ready Checklist (Consolidated)

Final pre-implementation verification — all items must pass before writing `StructuredExtractionService.java`:

**Prerequisites**:
- [ ] `findBySourceIn(project, List<String>, limit)` added to `ObservationRepository`
- [ ] `findByTypeGlobal(type, limit)` added to `ObservationRepository`
- [ ] `findByTypeLike(project, typePattern, limit)` added with `LIKE` support
- [ ] `findNewObservations(project, sources, sinceEpoch, limit)` added
- [ ] `chatCompletionStructured(systemPrompt, userPrompt, outputType)` added to `LlmService`

**Classpath verification**:
- [ ] `BeanOutputConverter` in `spring-ai-model` jar (verified in v14 ✅)
- [ ] `MapOutputConverter` in `spring-ai-model` jar (verified in v14 ✅)

**Configuration**:
- [ ] `ExtractionConfig` class with `@ConfigurationProperties(prefix = "app.memory.extraction")`
- [ ] YAML template loading with POJO+Map dual pattern
- [ ] `initial-run-max-candidates` config field

**Integration**:
- [ ] `deepRefineProjectMemories()` modified to call extraction as last step
- [ ] Non-blocking: extraction failures don't propagate to refinement

**Testing**:
- [ ] Existing regression test passes (43/43)
- [ ] Unit test with mocked LLM for structured extraction
- [ ] Integration test for full template → LLM → store pipeline

---

## 20. Walkthrough Findings (v16)

This section documents findings from pseudocode walkthroughs of the Phase 3 design. Walkthroughs expose gaps between design intent and actual implementation feasibility.

---

### 20.1 Issue: Array Schema Handling for Multiple Preferences

**Scenario**: User provides multiple preferences in a single conversation:
- "我不喜欢苹果手机" → category: "手机品牌(排斥)", value: "苹果"
- "我更喜欢小米" → category: "手机品牌(偏好)", value: "小米"  
- "预算3000-4000" → category: "价格预算", value: "3000-4000"

**Current Schema (Problematic)**:
```yaml
output-schema: |
  {
    "type": "object",
    "properties": {
      "category": {"type": "string"},
      "value": {"type": "string"},
      "confidence": {"type": "number"}
    }
  }
```

**Problem**: This schema represents a **single** preference object. LLM can only return one preference, losing the other two.

**LLM Response** (following schema):
```json
{"category": "手机品牌", "value": "喜欢小米/安卓，不喜欢苹果，预算3000-4000", "confidence": 0.9}
```
→ All preferences merged into single string (no structured data)

**Correct Schema (Array Wrapper)**:
```yaml
output-schema: |
  {
    "type": "object",
    "properties": {
      "preferences": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "category": {"type": "string"},
            "value": {"type": "string"},
            "sentiment": {"type": "string"},
            "confidence": {"type": "number"}
          }
        }
      }
    }
  }
```

**LLM Response** (following array schema):
```json
{
  "preferences": [
    {"category": "手机品牌(排斥)", "value": "苹果", "sentiment": "negative", "confidence": 0.95},
    {"category": "手机品牌(偏好)", "value": "小米/安卓", "sentiment": "positive", "confidence": 0.9},
    {"category": "价格预算", "value": "3000-4000", "sentiment": "neutral", "confidence": 0.85}
  ]
}
```

**Resolution**: Update all template examples in Section 2.2 to use array-wrapped schema for preference-like extractions. Document this pattern in template configuration guidelines.

---

### 20.2 Issue: Multi-User Session Aggregation Problem — ✅ RESOLVED

**Scenario**: Project `/my-project` has multiple users:
- Alice (Session A): "我不喜欢苹果手机", "预算3000"
- Bob (Session B): "我老婆对花生过敏", "她喜欢华为"

**Current Design Limitation**:
- Observations have `content_session_id` (random UUID) and `project_path`
- **No `user_id` field** — cannot distinguish Alice vs Bob

**Root Cause**: Project-based isolation (`project_path`) is insufficient for multi-user scenarios.

**DECISION (2026-03-22)**: Add `user_id` field to `SessionEntity`.

**Data Model Change**:
```java
@Entity
@Table(name = "mem_sessions")
public class SessionEntity {
    @Column(name = "content_session_id")
    private String contentSessionId;

    @Column(name = "project_path")
    private String projectPath;

    @Column(name = "user_id")          // ← NEW
    private String userId;
    
    // ... existing fields
}
```

**New Repository Methods**:
```java
public interface SessionRepository extends JpaRepository<SessionEntity, String> {
    // Find all session IDs for a user within a project
    @Query("SELECT s.contentSessionId FROM SessionEntity s WHERE s.userId = :userId AND s.projectPath = :project")
    List<String> findSessionIdsByUserIdAndProject(@Param("userId") String userId, @Param("project") String project);
    
    // Find all sessions for a user (cross-project)
    List<SessionEntity> findByUserId(String userId);
}
```

**Flyway Migration**:
```sql
-- V15: Add user_id column to mem_sessions
ALTER TABLE mem_sessions ADD COLUMN user_id VARCHAR(255);
CREATE INDEX idx_mem_sessions_user_id ON mem_sessions(user_id);
```

**Resolution Path**:
1. **Phase 3.1**: Add `user_id` field + Flyway migration + repository methods
2. **Phase 3.1**: Update ingestion to set `userId` from caller context
3. **Phase 3.1**: Update extraction to group by `userId`

---

### 20.3 Issue: Special Session ID for Preference Storage — ✅ RESOLVED

**DECISION (2026-03-22)**: Add `sessionIdPattern` to template configuration + `user_id` in SessionEntity.

**Template Configuration**:
```yaml
templates:
  - name: "user_preference"
    session-id-pattern: "pref:{project}:{userId}"  # ← Special session
    # ...

  - name: "allergy_info"
    session-id-pattern: null  # ← null = inherit source session
```

**Extraction Flow (RESOLVED)**:
```java
// Extraction runs per-project, groups by userId
runExtraction(projectPath) {
    // 1. Get all candidate observations
    allCandidates = observationRepository.findBySourceIn(projectPath, sources, 1000)
    
    // 2. Group by user (via session → user_id)
    Map<String, List<ObservationEntity>> byUser = new HashMap()
    for (obs : allCandidates) {
        SessionEntity session = sessionRepository.findBySessionId(obs.getContentSessionId())
        String userId = session.getUserId()  // ✅ Now available!
        byUser.computeIfAbsent(userId, k -> new ArrayList()).add(obs)
    }
    
    // 3. Extract per user
    for (entry : byUser.entrySet()) {
        String userId = entry.getKey()
        List<ObservationEntity> userObs = entry.getValue()
        
        for (template : templates) {
            if (!template.enabled()) continue
            
            // 4. LLM extraction
            result = extractByTemplate(template, userObs)
            
            // 5. Resolve target session ID
            targetSessionId = resolveSessionId(template.sessionIdPattern(), projectPath, userId)
            
            // 6. Store
            storeExtractionResult(template, result, targetSessionId)
        }
    }
}

private String resolveSessionId(String pattern, String projectPath, String userId) {
    if (pattern == null) {
        return null;  // Inherit from source observation
    }
    return pattern
        .replace("{project}", projectPath)
        .replace("{userId}", userId);
}

// Storage:
private void storeExtractionResult(ExtractionTemplate template, Object result, String targetSessionId) {
    ObservationEntity obs = new ObservationEntity();
    obs.setType("extracted_" + template.name());
    obs.setSource("extraction:" + template.name());
    obs.setExtractedData(convertToMap(result));
    
    if (targetSessionId != null) {
        obs.setContentSessionId(targetSessionId);  // "pref:/project:alice"
    }
    // else: content_session_id inherited from first source observation (handled by caller)
    
    observationRepository.save(obs);
}
```

**Query Flow**:
```java
// Agent needs Alice's preferences
List<ObservationEntity> alicePrefs = observationRepository
    .findBySessionId("pref:/my-project:alice")
// ✅ Returns only Alice's preferences, no contamination from Bob
```

---

### 20.4 Issue: Incremental Extraction Result Merging

**Scenario**: 
- First extraction: Alice's preferences extracted from sessions A, C
- Second extraction (new observations): Additional preferences from session D

**Current Design Gap**:
```java
// First extraction
result1 = extractByTemplate(project, template)  // Returns {preferences: [{苹果, negative}, {小米, positive}]}

// Second extraction (incremental)
result2 = extractByTemplate(project, template)  // Returns {preferences: [{手机, new-value}]}

// Problem: Both stored as separate observations
// Query: "What are Alice's preferences?" → Returns 2 results!
```

**Current Storage Logic**:
```java
// Each extraction creates a NEW ObservationEntity
obs = new ObservationEntity()
obs.setType("extracted_user_preference")
obs.setExtractedData({preferences: [...]})  // Each call creates new record
observationRepository.save(obs)  // ❌ Creates duplicate observations
```

**Required Logic**:
1. **Merge** new preferences with existing ones
2. **Handle conflicts** (same category, different values)
3. **Deduplicate** by category + value

**Pseudocode for Merge**:
```java
private Map<String, Object> mergePreferences(
        Map<String, Object> oldExtractedData, 
        Map<String, Object> newExtractedData) {
    
    List<Map<String, Object>> oldPrefs = 
        (List<Map<String, Object>>) oldExtractedData.get("preferences");
    List<Map<String, Object>> newPrefs = 
        (List<Map<String, Object>>) newExtractedData.get("preferences");
    
    // Merge: keep old, add new (unless duplicate)
    Map<String, Map<String, Object>> merged = new HashMap<>();
    
    for (Map<String, Object> pref : oldPrefs) {
        String key = pref.get("category") + ":" + pref.get("value");
        merged.put(key, pref);
    }
    
    for (Map<String, Object> pref : newPrefs) {
        String key = pref.get("category") + ":" + pref.get("value");
        // If same category exists with different value → conflict detection
        if (merged.containsKey(key)) {
            // Trigger conflict detection or keep newer
        } else {
            merged.put(key, pref);
        }
    }
    
    return Map.of("preferences", new ArrayList<>(merged.values()));
}
```

**Resolution**: Update `storeExtractionResult()` to check if extraction result for same template already exists, and merge rather than create duplicate.

---

### 20.5 Issue: ICL Prompt Extracted Data Formatting

**Scenario**: ICL prompt needs to display extracted preferences in readable format.

**Current Code Reference** (Section 15.8):
```java
context.append(String.format("[%s]: %s\n", templateName, formatExtractedData(ext.getExtractedData())));
```

**Problem**: `formatExtractedData()` method not defined in design.

**Required Implementation**:
```java
private String formatExtractedData(Map<String, Object> extractedData) {
    StringBuilder sb = new StringBuilder();
    
    for (Map.Entry<String, Object> entry : extractedData.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        
        if (value instanceof List) {
            // Array values (e.g., preferences, allergens)
            List<?> list = (List<?>) value;
            for (Object item : list) {
                if (item instanceof Map) {
                    // Each item is a map (e.g., preference object)
                    Map<?, ?> itemMap = (Map<?, ?>) item;
                    sb.append("  - ");
                    itemMap.forEach((k, v) -> sb.append(k).append(": ").append(v).append(", "));
                    sb.setLength(sb.length() - 2); // Remove trailing comma+space
                    sb.append("\n");
                } else {
                    sb.append("  - ").append(item).append("\n");
                }
            }
        } else if (value instanceof Map) {
            // Nested map
            Map<?, ?> map = (Map<?, ?>) value;
            sb.append(key).append(":\n");
            map.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
        } else {
            // Simple value
            sb.append(key).append(": ").append(value).append("\n");
        }
    }
    
    return sb.toString();
}
```

**Resolution**: Document `formatExtractedData()` utility method. Required for ICL prompt integration.

---

### 20.6 Issue: Multi-Level Conflict Detection for Arrays — ✅ RESOLVED

**Resolution**: The LLM re-extraction approach (Section 2.3) eliminates the need for a separate `ConflictDetector`. When the LLM receives the prior extraction result + new observations, it naturally understands semantics and handles:
- Value changes ("Sony" → "Bose")
- Sentiment changes ("喜欢苹果" → "不喜欢苹果")
- Context-aware non-conflicts ("安静餐厅" vs "吵闹酒吧" — different contexts)

The prompt instruction "If new observations contradict previous preferences, update to reflect the latest stated preference" handles real contradictions. Removed items can be tracked via the `removed` metadata field.

**No `ConflictDetector` class needed. Phase 3.3 conflict detection phase removed from roadmap.**

---

### 20.7 Cross-Project User Identification — ✅ DECIDED

**DECISION (2026-03-22)**: User ID is **project-scoped** (Option A).

```
Option A: Project-scoped userId (CHOSEN)
  - User identifier: {project}:{userId}
  - Alice in project A: userId = "alice" (different from Alice in project B)
  - Preference session: "pref:/project-a:alice"
  - Matches existing project-based isolation
```

**Rationale**:
- Simpler implementation — no global user management needed
- Consistent with existing `project_path` isolation model
- Each project can have its own user namespace
- Can upgrade to global userId later if needed

**Implication**: If Alice is a user in both `/project-a` and `/project-b`, she has two separate preference profiles: `pref:/project-a:alice` and `pref:/project-b:alice`.

---

### 20.8 Summary of Walkthrough Findings

| # | Issue | Severity | Resolution | Status |
|---|-------|----------|------------|--------|
| 1 | Array schema for multiple preferences | 🔴 Critical | Use array-wrapped schema | ✅ Resolved (Section 2.2) |
| 2 | Multi-user session aggregation | 🔴 Critical | `user_id` field in SessionEntity | ✅ Resolved (Section 2.3 + V15) |
| 3 | Special session ID discovery | 🟡 Medium | `sessionIdPattern` + `user_id` | ✅ Resolved (Section 2.3) |
| 4 | Incremental extraction merging | 🔴 Critical | Merge logic for duplicate detection | ✅ Resolved (Section 2.3) |
| 5 | ICL prompt data formatting | 🟡 Medium | Add `formatExtractedData()` utility | ✅ Resolved (Section 2.3) |
| 6 | Array-level conflict detection | 🟡 Medium | Superseded — LLM re-extraction handles semantics | ✅ Resolved (Section 2.3) |
| 7 | Cross-project user identification | 🟡 Medium | Project-scoped userId | ✅ Decided (Section 20.7) |
| 8 | Ingestion API user_id passing | 🟡 Medium | Option B: session creation + PATCH API | ✅ Resolved (Section 20.9) |

**All 8 issues resolved. Phase 3.1 design is complete.**

**Confirmed for Phase 3.1**:
1. ✅ Array-wrapped schema in Section 2.2
2. ✅ User grouping via `groupByUser()` in Section 2.3
3. ✅ Merge logic with sentiment-aware conflict handling in Section 2.3
4. ✅ `formatExtractedData()` for ICL prompts in Section 2.3
5. ✅ `user_id` in SessionEntity (Flyway V15)
6. ✅ Session creation with optional userId + PATCH update API
7. ✅ 8 repository prerequisites (Section 15.1)

---

### 20.9 Issue: Ingestion API user_id Passing — ✅ RESOLVED

**DECISION (2026-03-22)**: **Option B** — set `userId` at session creation (optional field), plus an update API for existing sessions.

**Session Creation API**:
```java
// POST /api/ingest/session
public SessionEntity createSession(@RequestBody CreateSessionRequest request) {
    SessionEntity session = new SessionEntity();
    session.setContentSessionId(request.getSessionId());
    session.setProjectPath(request.getProjectPath());
    session.setUserId(request.getUserId());  // ← Optional, can be null
    return sessionRepository.save(session);
}
```

**Update API** (new):
```java
// PATCH /api/ingest/session/{sessionId}/userId
public SessionEntity updateSessionUserId(
        @PathVariable String sessionId,
        @RequestBody UpdateUserIdRequest request) {
    SessionEntity session = sessionRepository.findByContentSessionId(sessionId);
    if (session == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
    }
    session.setUserId(request.getUserId());
    return sessionRepository.save(session);
}
```

**Design**:
- `userId` is **optional** on session creation (can be null)
- If not set initially, can be set later via PATCH API
- Observation ingestion API does NOT need `userId` — it's derived from session
- Backward compatible (existing sessions have null userId, extraction skips them or groups under `__unknown__`)

**New API Endpoints**:
```
POST   /api/ingest/session                    # Create session (with optional userId)
PATCH  /api/ingest/session/{sessionId}/userId  # Set/update userId for existing session
```

**New DTO**:
```java
public class UpdateUserIdRequest {
    private String userId;
    // getter/setter
}
```

**Impact**: 
- Adds `userId` field to `CreateSessionRequest` DTO
- New `UpdateUserIdRequest` DTO
- New PATCH endpoint in `IngestionController`
- Backward compatible (userId optional)
- Extraction groups by userId via session lookup

---

## 21. Implementation Inspection Findings (v19)

This section documents gaps found during the 2026-03-22 code inspection by comparing the design against the actual codebase state.

### 21.1 Verified Prerequisites Status (2026-03-22)

| # | Prerequisite | Design Reference | Actual Status | File |
|---|-------------|-----------------|---------------|------|
| 1 | `findBySourceIn(project, List<String>, limit)` | Section 9.1 | ❌ NOT implemented | ObservationRepository.java |
| 2 | `findNewObservations(project, sources, sinceEpoch, limit)` | Section 9.1 | ❌ NOT implemented | ObservationRepository.java |
| 3 | `findByTypeGlobal(type, limit)` | Section 15.1 #3 | ❌ NOT implemented | ObservationRepository.java |
| 4 | `findByTypeLike(project, typePattern, limit)` | Section 15.1 #4 | ❌ NOT implemented | ObservationRepository.java |
| 5 | `findByContentSessionIdAndType(sessionId, type, limit)` | Section 15.1 #6 | ❌ NOT implemented | ObservationRepository.java |
| 6 | `chatCompletionStructured(systemPrompt, userPrompt, outputType)` | Section 15.4 | ❌ NOT implemented | LlmService.java |
| 7 | `user_id` field in SessionEntity | Section 20.2 | ❌ NOT implemented | SessionEntity.java |
| 8 | Flyway V15 migration (user_id) | Section 20.2 | ❌ NOT implemented | db/migration/ (latest: V14) |
| 9 | `findByUserId(userId)` | Section 15.1 #7 | ❌ NOT implemented | SessionRepository.java |
| 10 | `findSessionIdsByUserIdAndProject(userId, project)` | Section 15.1 #8 | ❌ NOT implemented | SessionRepository.java |

**Conclusion**: ALL 10 prerequisites remain unimplemented. The design is comprehensive but no code has been written for Phase 3.1.

### 21.2 LlmService Availability Guard Missing

**Issue**: The design shows `StructuredExtractionService` calling `llmService.chatCompletionStructured()`, but doesn't address what happens when no ChatClient is configured.

**Current behavior** (LlmService.java line 43-44):
```java
public String chatCompletion(String systemPrompt, String userPrompt) {
    return chatCompletionWithUsage(systemPrompt, userPrompt).content();
    // ↑ Throws IllegalStateException("AI not configured") if chatClient.isEmpty()
}
```

**Problem**: If the application starts without an API key, `LlmService.isAvailable()` returns false, but `chatCompletion()` throws `IllegalStateException`. The extraction service doesn't check availability before calling.

**Fix**: Add availability guard in `runExtraction()`:

```java
public void runExtraction(String projectPath) {
    if (!llmService.isAvailable()) {
        log.debug("LLM not available, skipping extraction for project: {}", projectPath);
        return;
    }
    // ... rest of extraction
}
```

### 21.3 ~~mergeExtractedData() Type Safety Issue~~ — SUPERSEDED

**Status**: This issue no longer applies. `mergeExtractedData()` has been removed in favor of the LLM re-extraction approach (Section 2.3). The LLM handles all data merging semantically.

```java
List<Map<String, Object>> existingList = (List<Map<String, Object>>) existingValue;
List<Map<String, Object>> newList = (List<Map<String, Object>>) newValue;
```

**Problem**: If `extractedData` contains a list of primitives (e.g., `{"allergens": ["peanut", "shellfish"]}`), this cast throws `ClassCastException` because the items are `String`, not `Map<String, Object>`.

**Fix**: Add type check before cast:

```java
if (newValue instanceof List && existingValue instanceof List) {
    // Check if list elements are Maps (structured data) or primitives
    List<?> rawExisting = (List<?>) existingValue;
    List<?> rawNew = (List<?>) newValue;
    
    if (!rawExisting.isEmpty() && rawExisting.get(0) instanceof Map) {
        // Structured list: merge by composite key
        List<Map<String, Object>> existingList = (List<Map<String, Object>>) existingValue;
        List<Map<String, Object>> newList = (List<Map<String, Object>>) newValue;
        // ... existing merge logic
    } else {
        // Primitive list: deduplicate by value
        Set<Object> combined = new LinkedHashSet<>(rawExisting);
        combined.addAll(rawNew);
        merged.put(key, new ArrayList<>(combined));
    }
}
```

### 21.4 Transactional Scope on Extraction Storage

**Issue**: `storeExtractionResult()` with merge logic performs: (1) query existing, (2) update extractedData, (3) save. Without `@Transactional`, if step 3 fails, the extraction result is lost but source observations are marked as "processed" (via state update).

**Problem**: The `updateExtractionState()` has `@Transactional` (section 15.6), but `storeExtractionResult()` does not. State update and result storage are separate transactions — inconsistency possible.

**Fix**: Option A (recommended) — wrap the entire template extraction in a single transaction:

```java
@Transactional
private void runTemplateExtraction(String projectPath, ExtractionTemplate template) {
    // 1. Get candidates
    // 2. Extract via LLM
    // 3. Store result (with merge)
    // 4. Update state
    
    // All-or-nothing: if any step fails, rollback everything
}
```

**Tradeoff**: LLM call (step 2) happens inside a transaction. If the LLM call is slow, the DB transaction stays open. Mitigate by separating LLM call (outside transaction) from storage (inside transaction):

```java
public void runTemplateExtraction(String projectPath, ExtractionTemplate template) {
    // Step 1+2: Get candidates + LLM call (NO transaction)
    List<ObservationEntity> candidates = getCandidates(projectPath, template, state);
    Object result = extractByTemplate(projectPath, template, candidates);
    
    if (result == null) return;
    
    // Step 3+4: Store result + update state (IN transaction)
    storeAndAdvanceState(projectPath, template, result, candidates);
}

@Transactional
private void storeAndAdvanceState(...) {
    storeExtractionResult(template, result, candidates, targetSessionId);
    updateExtractionState(projectPath, template.name(), OffsetDateTime.now());
}
```

### 21.5 ReentrantLock Memory Leak

**Issue**: Section 15.7 uses `ConcurrentHashMap.computeIfAbsent()` to create per-project locks. Locks are never removed from the map.

**Problem**: In long-running applications where projects are created and abandoned, the lock map grows indefinitely. Not a critical leak (locks are small objects) but poor hygiene.

**Fix**: Use `WeakHashMap` wrapper or periodic cleanup:

```java
// Option A: Cleanup after extraction completes
public void runExtraction(String projectPath) {
    ReentrantLock lock = projectLocks.computeIfAbsent(projectPath, k -> new ReentrantLock());
    lock.lock();
    try {
        // ... extraction logic
    } finally {
        lock.unlock();
        // Don't remove — another thread may be waiting
    }
}

// Option B: Scheduled cleanup (every hour)
@Scheduled(fixedRate = 3600000)
public void cleanupStaleLocks() {
    projectLocks.entrySet().removeIf(e -> {
        ReentrantLock lock = e.getValue();
        return !lock.isLocked() && lock.getQueueLength() == 0;
    });
}
```

### 21.6 ExtractionConfig.ExtractionTemplate vs Record Mismatch

**Issue**: Section 15.3 defines `ExtractionConfig.ExtractionTemplate` as a POJO with `toTemplate()` conversion method, but the extraction service code in section 2.3 uses the `ExtractionTemplate` record directly.

**Problem**: Two different `ExtractionTemplate` types exist:
- `com.ablueforce.cortexce.config.ExtractionConfig.ExtractionTemplate` (POJO, for YAML binding)
- `com.ablueforce.cortexce.model.ExtractionTemplate` (record, for service logic)

The `toTemplate()` conversion must happen somewhere, but the design doesn't specify where.

**Fix**: Choose ONE approach. Recommended: **Use the POJO directly** in the service (drop the record):

```java
// In StructuredExtractionService
@Autowired
private ExtractionConfig extractionConfig;

public void runExtraction(String projectPath) {
    for (ExtractionConfig.ExtractionTemplate template : extractionConfig.getTemplates()) {
        // Use POJO directly — no conversion needed
        if (!template.isEnabled()) continue;
        runTemplateExtraction(projectPath, template);
    }
}
```

This eliminates the record entirely and avoids the conversion layer. The POJO is already Spring-managed and configuration-bound.

### 21.7 Post-Extraction Schema Validation Gap

**Issue**: `BeanOutputConverter` injects schema instructions into the system prompt, but doesn't validate the result. If the LLM returns non-compliant JSON, `converter.convert(response)` may throw or return partially-parsed data.

**Current design** (section 7.4): Shows `extractWithRetry()` with validation, but section 2.3's `extractByTemplate()` doesn't include this.

**Problem**: The retry logic in section 7.4 is designed for manual JSON parsing (`objectMapper.readValue()`), not for `BeanOutputConverter` flow. `BeanOutputConverter.convert()` handles parsing internally — we can't intercept its output for validation.

**Fix**: Add try-catch around `chatCompletionStructured()` with retry:

```java
private <T> T extractWithRetry(ExtractionTemplate template, 
                                List<ObservationEntity> candidates,
                                Class<T> outputType,
                                int maxRetries) {
    for (int attempt = 0; attempt < maxRetries; attempt++) {
        try {
            return llmService.chatCompletionStructured(
                template.promptTemplate(), 
                buildPrompt(template, candidates, null),
                outputType);
        } catch (Exception e) {
            log.warn("Extraction attempt {}/{} failed for template {}: {}", 
                attempt + 1, maxRetries, template.name(), e.getMessage());
            if (attempt == maxRetries - 1) {
                throw new ExtractionException("Failed after " + maxRetries + " attempts", e);
            }
        }
    }
    throw new ExtractionException("Unreachable"); // satisfy compiler
}
```

### 21.8 Null Observation Content Handling

**Issue**: `buildPrompt()` in section 2.3 calls `sanitize(obs.getContent())` and `sanitize(obs.getTitle())`, but `ObservationEntity.content` and `title` can be null.

**Current code**:
```java
sb.append(String.format("- [%s] %s\n  %s\n",
    sanitize(obs.getSource()),
    obs.getTitle() != null ? sanitize(obs.getTitle()) : "",
    obs.getContent() != null ? sanitize(obs.getContent()) : ""));
```

**Analysis**: The null checks exist in the design ✅ — but `sanitize()` returns `""` for null input, so the redundant `obs.getTitle() != null ? ... : ""` check could be simplified to just `sanitize(obs.getTitle())`. Minor style issue, not a bug.

**Real concern**: `obs.getSource()` is called with `sanitize()` but no null check. If source is null (possible for legacy observations before V14), `sanitize(null)` returns `""` — which is safe. No fix needed.

### 21.9 `@Async` + Extraction Concurrency Interaction

**Issue**: `MemoryRefineService.deepRefineProjectMemories()` is annotated with `@Async` (confirmed in code). If extraction is called as the last step of `deepRefine`, it runs in a separate thread pool.

**Problem**: Section 15.7's `ReentrantLock` is in-memory, bound to the JVM. If `@Async` runs in a different thread, the lock works correctly (same JVM). But if the application has multiple instances or thread pool configuration issues, the lock may not work as expected.

**Mitigation**: This is acceptable for single-instance deployment. Document the assumption:

```java
// NOTE: ReentrantLock is JVM-local. If running multiple instances,
// use ShedLock or DB advisory locks instead.
private final ConcurrentHashMap<String, ReentrantLock> projectLocks = new ConcurrentHashMap<>();
```

### 21.10 ExtractionConfig Conditional Bean Missing

**Issue**: The design assumes `ExtractionConfig` is always loaded. If `app.memory.extraction.enabled=false` or the config section is missing entirely, Spring may fail to bind.

**Fix**: Add `@ConditionalOnProperty` to the configuration class:

```java
@Configuration
@ConditionalOnProperty(prefix = "app.memory.extraction", name = "enabled", havingValue = "true", matchIfMissing = false)
@ConfigurationProperties(prefix = "app.memory.extraction")
public class ExtractionConfig {
    // ...
}
```

And add `@ConditionalOnBean` to StructuredExtractionService:

```java
@Service
@ConditionalOnBean(ExtractionConfig.class)
public class StructuredExtractionService {
    // Only created if ExtractionConfig exists
}
```

---

## 22. SDK API Walkthrough Findings (v21)

This section documents findings from the full SDK API walkthrough. The walkthrough checks every SDK API endpoint to ensure userId filtering works correctly across all memory operations.

### 22.1 SDK API Endpoint Analysis

| # | API | Function | Needs userId? | Reason |
|---|-----|----------|---------------|--------|
| 1 | `POST /api/session/start` | Create session | ✅ Needs | Already designed |
| 2 | `POST /api/ingest/tool-use` | Record observation | ❌ No | Derived from session |
| 3 | `POST /api/ingest/session-end` | End session | ❌ No | Derived from session |
| 4 | `POST /api/ingest/user-prompt` | Record prompt | ❌ No | Derived from session |
| 5 | `POST /api/memory/experiences` | Retrieve experiences | ⚠️ Needs | See analysis below |
| 6 | `POST /api/memory/icl-prompt` | Build ICL prompt | ⚠️ Needs | See analysis below |
| 7 | `POST /api/memory/refine` | Trigger refinement | ❌ No | Project-level operation |
| 8 | `POST /api/memory/feedback` | Submit feedback | ❌ No | Uses observation ID |
| 9 | `PATCH /api/memory/observations/{id}` | Update observation | ❌ No | Uses observation ID |
| 10 | `PATCH /api/session/{sessionId}/user` | Set userId | ✅ New | Already designed |

### 22.2 Issue: Experiences API Not Filtering by User

**Current Design**:
```java
POST /api/memory/experiences
{
  "task": "...",
  "project": "/my-project",
  "count": 4
}
```

**Problem**: `ExpRagService.retrieveExperiences()` searches by project only. Returns observations from ALL users in the project. Alice would see Bob's experiences.

**Required Change**: Add `userId` parameter:
```java
POST /api/memory/experiences
{
  "task": "有什么推荐的零食？",
  "project": "/customer-service",
  "userId": "alice",  // ← NEW
  "count": 4
}
```

**SDK DTO Change**:
```java
// ExperienceRequest — add userId
public record ExperienceRequest(
    String task,
    String project,
    Integer count,
    String source,
    List<String> requiredConcepts,
    String userId  // ← NEW, optional
) {}
```

### 22.3 Issue: ICL Prompt API Not Filtering by User

**Current Design**:
```java
POST /api/memory/icl-prompt
{
  "task": "...",
  "project": "/my-project",
  "maxChars": 4000
}
```

**Problem**: ICL prompt includes:
1. Extracted facts from ALL users → Alice sees Bob's allergies
2. Relevant experiences from ALL users

**Required Change**: Add `userId` parameter:
```java
POST /api/memory/icl-prompt
{
  "task": "有什么推荐的零食？",
  "project": "/customer-service",
  "userId": "alice",  // ← NEW
  "maxChars": 4000
}
```

**SDK DTO Change**:
```java
// ICLPromptRequest — add userId
public record ICLPromptRequest(
    String task,
    String project,
    Integer maxChars,
    String userId  // ← NEW, optional
) {}
```

**Internal Logic**:
```java
// Section 1: This user's extracted facts (from pref:session)
List<ObservationEntity> userExtractions = observationRepository
    .findByContentSessionId("pref:" + project + ":" + userId);

// Section 2: Relevant experiences (from regular observations)
// Can still include project-level context
```

### 22.4 Issue: Session Updated After Observations Created

**Scenario**: Observations exist, then session userId is set via PATCH API.

**Timeline**:
1. Session created with `userId = null` (hook mode or missing userId)
2. User speaks → observations created, linked to session
3. Extraction runs → `groupByUser()` returns `__unknown__`
4. Results stored at `pref:/project:__unknown__`
5. Later: PATCH `/api/session/{id}/user` sets `userId = "alice"`
6. Next extraction → correct grouping, but **old results still in `__unknown__`**

**Solution**: On PATCH `/api/session/{sessionId}/user`, trigger re-extraction for that session.

```java
// PATCH /api/session/{sessionId}/user
public SessionEntity updateSessionUserId(String sessionId, String userId) {
    SessionEntity session = sessionRepository.findByContentSessionId(sessionId);
    String oldUserId = session.getUserId();
    session.setUserId(userId);
    sessionRepository.save(session);

    // If userId changed from null to non-null, trigger re-extraction
    if (oldUserId == null && userId != null) {
        extractionService.reExtractForSession(sessionId, session.getProjectPath());
    }

    return session;
}
```

```java
// New method: re-extract for a single session after userId assignment
public void reExtractForSession(String sessionId, String projectPath) {
    // 1. Find all observations for this session
    List<ObservationEntity> sessionObs = observationRepository
        .findByContentSessionId(sessionId);

    // 2. Cleanup old __unknown__ extractions from this session
    cleanupUnknownExtractions(sessionId, projectPath);

    // 3. Re-extract with correct userId
    String userId = sessionRepository.findByContentSessionId(sessionId).getUserId();
    for (ExtractionTemplate template : templates) {
        Object result = extractByTemplate(projectPath, template, sessionObs);
        String targetSessionId = resolveSessionId(
            template.sessionIdPattern(), projectPath, userId);
        storeExtractionResult(template, result, sessionObs, targetSessionId);
    }
}
```

**Status**: ✅ Resolved — PATCH API triggers re-extraction, no data loss.

### 22.5 Summary

| # | Issue | Status | Solution |
|---|-------|--------|----------|
| 1-4 | Session/Observation API | ✅ | Already designed |
| 5 | Experiences API user filtering | ❌ New | Add `userId` to `ExperienceRequest` |
| 6 | ICL Prompt API user filtering | ❌ New | Add `userId` to `ICLPromptRequest` |
| 7 | Session userId update timing | ✅ Resolved | PATCH API triggers `reExtractForSession()` |
| 8-9 | Feedback/Update API | ✅ | No change needed |
| 10 | PATCH session userId | ✅ | Already designed |

**All 10 SDK API issues resolved or designed.**

---

## 23. Token Cost Analysis & Cost Control (v23)

This section provides a comprehensive token consumption analysis for both extraction and refinement pipelines, and proposes cost control strategies.

### 23.1 Token Consumption Model

**Per LLM call breakdown (extraction):**

| Component | Tokens | Notes |
|-----------|--------|-------|
| System prompt (template) | ~300 | Template prompt + instructions |
| Schema hint | ~200 | JSON Schema in user prompt |
| Per observation | ~100-200 | Title + content + metadata |
| Batch of 20 observations | ~2000-4000 | 20 × 100-200 |
| **Total input per batch call** | **~2500-4500** | System + schema + observations |
| **LLM output per batch** | **~300-800** | Structured JSON response |

**Per LLM call breakdown (refinement):**

| Component | Tokens | Notes |
|-----------|--------|-------|
| System prompt | ~200 | Refinement instructions |
| Observation content | ~100-500 | Single observation to refine |
| **Total input per call** | **~300-700** | |
| **LLM output per call** | **~100-300** | Refined content |

### 23.2 Extraction Cost Estimates

**Scenario: 5 templates × 3 projects, medium usage**

| Phase | Observations | Batches/Template | Total Calls | Frequency |
|-------|-------------|-----------------|-------------|-----------|
| Initial run (capped) | 100/project | 5 calls | 5 × 5 × 3 = 75 | Once |
| Daily incremental | ~10 new/project | 1 call | 5 × 3 × 1 = 15/day | Daily |

**Cost per day (incremental only, GPT-4o pricing):**

| Model | Cost/Call | Daily Cost | Monthly Cost |
|-------|-----------|------------|--------------|
| SiliconFlow (cheap) | ~$0.0001 | $0.0015 | $0.045 |
| GPT-4o-mini | ~$0.0005 | $0.0075 | $0.23 |
| GPT-4o | ~$0.01 | $0.15 | $4.50 |
| Claude 3.5 Sonnet | ~$0.008 | $0.12 | $3.60 |

**Cost for initial run (one-time):**

| Model | 75 calls | Cost |
|-------|----------|------|
| SiliconFlow | 75 × $0.0001 | $0.0075 |
| GPT-4o-mini | 75 × $0.0005 | $0.0375 |
| GPT-4o | 75 × $0.01 | $0.75 |

### 23.3 Refinement Cost Estimates

**Scenario: 100 observations/session, 10 sessions/day**

| Operation | Calls/Observation | Calls/Session | Calls/Day |
|-----------|-------------------|---------------|-----------|
| Merge (2 obs merged into 1) | 0.5 | 50 | 500 |
| Rewrite | 1 | 10-20 | 100-200 |
| Quality scoring | 0 (no LLM) | 0 | 0 |
| **Total refinement** | | **60-70** | **600-700** |

**Daily refinement cost:**

| Model | Cost/Call | Daily Cost | Monthly Cost |
|-------|-----------|------------|--------------|
| SiliconFlow | ~$0.0001 | $0.06 | $1.80 |
| GPT-4o-mini | ~$0.0005 | $0.30 | $9.00 |
| GPT-4o | ~$0.01 | $6.00 | $180 |

### 23.4 Total Monthly Cost (Combined)

**Scenario: 5 templates × 3 projects × 10 sessions/day × 100 obs/session**

| Component | GPT-4o-mini | GPT-4o |
|-----------|-------------|--------|
| Extraction (incremental) | $0.23 | $4.50 |
| Refinement | $9.00 | $180 |
| **Total** | **$9.23** | **$184.50** |

**Key insight**: Refinement dominates the cost (97%+). Extraction is cheap by comparison.

### 23.5 Cost Control Strategies

**Strategy 1: Frequency Control**

| Mechanism | Default | Low-Cost | High-Frequency |
|-----------|---------|----------|----------------|
| Extraction schedule | Daily (2am) | Weekly | Every 6 hours |
| Refinement trigger | Every SessionEnd | Only on deep refine | Every SessionEnd |
| Deep refine schedule | Daily | Weekly | Daily |

```yaml
app.memory.extraction:
  schedule: "0 0 2 * * ?"          # Daily at 2am (default)
  # schedule: "0 0 2 * * 1"        # Weekly on Monday (low-cost)
  # schedule: "0 0 */6 * * ?"      # Every 6 hours (high-frequency)

app.memory.refine:
  deep-refine-schedule: "0 0 3 * * ?"  # Daily at 3am
  # deep-refine-schedule: "0 0 3 * * 1"  # Weekly (low-cost)
```

**Strategy 2: Batch Size Control**

```yaml
app.memory.extraction:
  initial-run-max-candidates: 100   # Cap first run
  max-observations-per-batch: 20    # Batch size for LLM calls
  max-batches-per-template: 10      # Cap per template per run
```

**Strategy 3: Model Selection**

Use cheaper models for extraction (structured output is schema-driven, less quality-sensitive):

```yaml
app.memory.extraction:
  model: "gpt-4o-mini"              # Cheaper for extraction
  # model: "gpt-4o"                 # Higher quality, higher cost

app.memory.refine:
  model: "gpt-4o"                   # Higher quality for refinement
```

**Strategy 4: Incremental Extraction (Already Designed)**

Section 7.1 already implements incremental extraction. Only new observations since last extraction are processed. This is the primary cost reduction mechanism.

**Strategy 5: Cost Budget & Alerting**

```yaml
app.memory.cost:
  monthly-budget-usd: 50.00
  alert-threshold-percent: 80       # Alert at 80% of budget
  pause-at-budget: true             # Pause extraction when budget exceeded
```

```java
// Cost tracker
private final AtomicReference<BigDecimal> monthlyCost = new AtomicReference<>(BigDecimal.ZERO);

private void checkBudget() {
    BigDecimal budget = new BigDecimal(costConfig.getMonthlyBudgetUsd());
    BigDecimal threshold = budget.multiply(new BigDecimal("0.8"));
    
    if (monthlyCost.get().compareTo(threshold) > 0) {
        log.warn("Monthly cost ${} approaching budget ${}", monthlyCost.get(), budget);
        if (costConfig.isPauseAtBudget() && monthlyCost.get().compareTo(budget) > 0) {
            log.error("Monthly budget exceeded, pausing extraction");
            throw new BudgetExceededException("Monthly cost budget exceeded");
        }
    }
}
```

### 23.6 Cost-Effectiveness Analysis

**Question: Is extraction worth the cost?**

**Extraction value**:
- ICL prompt includes structured preferences → better AI responses
- Personalization without re-reading all conversations
- One-time extraction cost, repeated use in every ICL prompt

**ROI per extraction call**:
- Cost: $0.0005 (GPT-4o-mini)
- ICL context: ~500 tokens of structured preferences
- Used in: Every subsequent conversation (10-100 calls/day)
- Value: 500 tokens × 10-100 conversations = 5000-50000 tokens of context value

**Conclusion**: Extraction is highly cost-effective. The initial cost is amortized over many subsequent uses.

**Refinement value**:
- Keeps memory quality high (removes duplicates, merges related observations)
- Reduces ICL prompt size (fewer redundant observations)
- Prevents memory pollution

**ROI per refinement call**:
- Cost: $0.005 (GPT-4o-mini)
- Value: Removes 1-2 low-quality observations
- Saves: ~100-200 tokens per future ICL prompt

**Conclusion**: Refinement has moderate cost-effectiveness. Deep refinement should be infrequent (weekly) to balance cost vs quality.

### 23.7 Recommended Cost Configuration

**Default (balanced)**:

```yaml
app.memory.extraction:
  enabled: true
  schedule: "0 0 2 * * ?"              # Daily at 2am
  initial-run-max-candidates: 100
  max-observations-per-batch: 20
  max-batches-per-template: 10
  cost-control:
    max-calls-per-run: 50
    dry-run: false

app.memory.refine:
  deep-refine-schedule: "0 0 3 * * ?"  # Daily at 3am
  quick-refine: true                    # Every session end
```

**Low-cost profile**:

```yaml
app.memory.extraction:
  schedule: "0 0 2 * * 1"              # Weekly on Monday
  initial-run-max-candidates: 50
  max-batches-per-template: 5

app.memory.refine:
  deep-refine-schedule: "0 0 3 * * 1"  # Weekly
  quick-refine: true
```

**High-frequency profile**:

```yaml
app.memory.extraction:
  schedule: "0 0 */6 * * ?"            # Every 6 hours
  initial-run-max-candidates: 200
  max-batches-per-template: 20

app.memory.refine:
  deep-refine-schedule: "0 0 3 * * ?"  # Daily
  quick-refine: true
```

---

## Changelog

- **2026-03-22 v23**: (1) **Section 23**: Added comprehensive token cost analysis — per-call breakdown, extraction cost estimates, refinement cost estimates, combined monthly cost projection. (2) **Section 23.5**: Cost control strategies — frequency control, batch size control, model selection, incremental extraction, cost budget & alerting. (3) **Section 23.6**: Cost-effectiveness analysis — extraction ROI is high (amortized over many conversations), refinement ROI is moderate (deep refine should be weekly). (4) **Section 23.7**: Three recommended cost profiles — default (balanced), low-cost (weekly), high-frequency (6-hourly). Key finding: refinement dominates cost (97%+), extraction is cheap by comparison.

- **2026-03-22 v22**: (1) **Section 22.4**: Resolved session userId update timing issue — PATCH API now triggers `reExtractForSession()` to re-extract observations after userId is assigned. (2) **Section 22.5**: Updated summary — all 10 SDK API issues resolved, no known limitations remain.

- **2026-03-22 v21**: (1) **Section 22**: Added SDK API walkthrough findings. (2) **Section 22.2**: Experiences API needs `userId` parameter — added `ExperienceRequest.userId` field. (3) **Section 22.3**: ICL Prompt API needs `userId` parameter — added `ICLPromptRequest.userId` field. (4) **Section 22.4**: Documented known limitation — session userId update after observations created won't migrate old extractions. (5) Updated Section 20.8 summary table to reflect 8 issues resolved (was 6). (6) **Section 22.5**: All 7 SDK walkthrough issues documented.

- **2026-03-22 v20**: (1) **Section 20.8**: All 8 walkthrough issues now resolved — no more deferred items. (2) **Section 20.9**: Ingestion API user_id resolved — Option B: session creation with optional userId + PATCH API to update existing sessions. (3) **Section 2.3**: Fixed `mergeExtractedData()` sentiment handling — composite key uses category+value only, sentiment changes trigger overwrite (not skip). (4) Confirmed Phase 3.1 design is complete with all issues resolved.
- **2026-03-22 v18**: (1) **Section 2.2**: Updated `user_preference` template to use array-wrapped schema (was single-object — critical fix from walkthrough). (2) **Section 2.3**: Refactored `runExtraction()` to include user grouping via `groupByUser()` method. (3) **Section 2.3**: Refactored `extractByTemplate()` to accept candidates parameter. (4) **Section 2.3**: Rewrote `storeExtractionResult()` with merge logic (`mergeExtractedData()`) and user-scoped session ID resolution. (5) **Section 2.3**: Added `formatExtractedData()` for ICL prompt integration. (6) **Section 15.1**: Added prerequisites #6-8: `findByContentSessionIdAndType()`, `findByUserId()`, `findSessionIdsByUserIdAndProject()`. (7) **Section 20.9**: Added ingestion API user_id passing design. (8) **Section 20.8**: Updated summary — 6 issues resolved, 2 deferred.
- **2026-03-22 v14**: (1) **Section 15.8 Bug Fix**: Fixed `findByType(project, "extracted_%", 50)` wildcard bug — `findByType` uses exact match (`type = :type`), NOT LIKE. Added `findByTypeLike()` repository method as prerequisite #4, plus alternative approach (iterate known template names). Updated ICL prompt and experience API code examples. (2) **Section 9.1**: Added `findByTypeLike()` to pending repository methods list. (3) **Section 15.1**: Added `findByTypeLike()` as prerequisite #5. (4) **Section 15.10**: Updated validation checklist with `findByTypeLike` or template-iteration alternative. (5) **Section 17**: Extraction idempotency — prevents duplicate `extracted_{template}` observations from re-running extraction. Composite hash deduplication + state guard clause. (6) **Section 18**: Observation type namespace reservation — `extraction_*` prefix reserved for system use. Added validation option to prevent user-created type collisions. (7) **Verified**: Spring AI 1.1.2 classpath includes `BeanOutputConverter`, `MapOutputConverter`, `ListOutputConverter` in `spring-ai-model` jar. Confirmed 5 pending prerequisites still not implemented: `findBySourceIn`, `findNewObservations`, `findByTypeGlobal`, `findByTypeLike`, `chatCompletionStructured`.
- **2026-03-22 v13**: (1) **Quick Reference**: Added TL;DR summary at top — what, how, when, prerequisites, pipeline diagram. (2) **Section 15.6**: Transaction safety for extraction state management — `@Transactional` wrapper for delete-then-save pattern, plus SQL upsert alternative for production. (3) **Section 15.7**: Concurrency control — per-project `ReentrantLock` to prevent duplicate extraction runs from deepRefine + scheduled task overlap. (4) **Section 15.8**: ICL prompt integration — how extracted data surfaces in `/api/memory/icl-prompt` and experience API (`includeExtractions` flag). (5) **Section 15.9**: Token counting without TokenService — character-based heuristic (CJK-aware) for batching observations. (6) **Section 16**: Architecture Decision Records — 4 key decisions (store as ObservationEntity, BeanOutputConverter, separate pipeline, POJO+Map hybrid) captured with rationale and tradeoffs. (7) **Section 15.10**: Updated validation checklist with `@Transactional` and wildcard `findByType` support.
- **2026-03-22 v12**: (1) **Section 15**: Added Implementation Bootstrap Checklist — step-by-step Phase 3.1 guide with prerequisites, new files, and validation checklist. (2) **Section 15.1**: Fixed DLQ bug — `findByType(null, ...)` won't work because `@Param("project")` is non-null; added `findByTypeGlobal()` repository method as prerequisite. (3) **Section 15.3**: Added Record vs POJO analysis for `ExtractionTemplate` configuration binding — recommended POJO approach via `@ConfigurationProperties` for Spring Boot 3.3 compatibility. (4) **Section 15.4**: Added concrete `LlmService.chatCompletionStructured()` implementation with `BeanOutputConverter`/`MapOutputConverter` dual pattern. (5) **Section 15.5**: Clarified extraction integration order — must NOT run during `quickRefine()`, only as last step of `deepRefineProjectMemories()`. (6) **Section 11.3**: Fixed DLQ retry code to use `findByTypeGlobal()` instead of broken `findByType(null, ...)`.
- **2026-03-22 v11**: (1) **Section 0.2**: Added critical design gaps documentation — `outputSchema` (String) cannot feed `BeanOutputConverter<T>` (Class) without a bridge; added three practical solutions (predefined POJO, Map fallback, dynamic generation). (2) **Gap 2**: Clarified `ListOutputConverter` limitation — returns `List<String>`, not `List<MyObject>`; arrays of objects must use `MapOutputConverter` or post-processing. (3) **Gap 3**: Confirmed `findBySourceIn` and `findNewObservations` repository methods are still NOT implemented (status: pending). (4) **Gap 4**: Confirmed `LlmService.chatCompletionStructured()` method does NOT exist (status: pending). (5) **Section 2.1**: Added `templateClass` field to `ExtractionTemplate` record — required for resolving Java Class at runtime. (6) **Section 2.2**: Updated YAML examples to include `template-class` field; clarified `output-schema` is only needed for Map templates. (7) **Section 2.3**: Refactored `extractByTemplate()` to use `resolveOutputClass()` and `buildSchemaHint()` for two-pattern support (POJO vs Map). (8) **Section 2.4**: Updated allergy example to show both POJO and Map template patterns.
- **2026-03-21 v10**: Critical fix in section 0.1 Bug 2: `JacksonOutputConverter` does NOT exist in Spring AI 1.1.2. Replaced with correct `BeanOutputConverter` approach. The correct Spring AI 1.1.2 structured output converters are `BeanOutputConverter`, `MapOutputConverter`, and `ListOutputConverter` from `org.springframework.ai.converter`. Updated implementation options to reflect actual Spring AI 1.1.2 API.
- **2026-03-21 v9**: (1) Added missing `convertToMap()` utility method in section 2.3 — handles POJOs, Maps, Lists, and primitives for `extractedData` JSONB storage. (2) Fixed `StructuredExtractionService` to use `LlmService` (consistent with `MemoryRefineService`) instead of raw `ChatClient`. (3) Added `findNewObservations()` repository method to section 9.1 with epoch-based comparison for incremental extraction. (4) Clarified `ExtractionState` persistence — stores as `ObservationEntity` with `type="extraction_state"` to avoid a separate database table.
- **2026-03-21 v8**: (1) Clarified Spring AI structured output: `ChatClient.call().entity()` does NOT auto-enforce schema - must use `JacksonOutputConverter` for true schema enforcement. Added implementation options for `LlmService.chatCompletionStructured()`. (2) Fixed `tokenService.estimateTokens()` → should be `TokenService.calculateObservationTokens()` (method doesn't exist, must inject TokenService bean). (3) Clarified dead letter queue implementation: store as `ObservationEntity` with type=`extraction_failed` rather than separate queue infrastructure.
- **2026-03-21 v7**: Fixed 3 critical bugs: (1) `findBySource(project, sourceFilter List)` → must use new `findBySourceIn(project, List<String>, limit)` method (findBySource takes String only). (2) Manual JSON parsing → replaced with Spring AI structured output via `ChatClient.call().entity(outputType)`. (3) Integration ambiguity → clarified extraction is a separate pipeline from refinement, only called as last step of deepRefineProjectMemories. Updated all `template.prompt()` references to `template.promptTemplate()`. Added `@JsonProperty` mapping note for YAML.
- **2026-03-21 v6**: Fixed findByType missing projectPath (cross-project pollution bug). Added error handling & recovery strategies: retry with circuit breaker, dead letter queue, schema validation failure handling, graceful degradation.
- **2026-03-21 v5**: Added critical implementation considerations: context window batching, prompt injection prevention, cost control (dry-run, rate limiting, caching), observability & audit trail, multi-language support
- **2026-03-21 v4**: Confirmed existing repository methods (findBySource, findByType) are reusable. Added integration points section.
- **2026-03-21 v3**: Added incremental extraction, query API, template versioning, LLM validation, cascading extractions considerations
- **2026-03-21 v2**: Generalized approach - extracted data type is determined by prompt, not code
- **2026-03-21 v1**: Initial design document created
