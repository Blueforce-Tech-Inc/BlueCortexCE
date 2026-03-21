# Phase 3 Design Proposal: Structured Information Extraction & Memory Conflict Detection

**Date**: 2026-03-22
**Status**: Design proposal - iteration 12 (Implementation bootstrap checklist, DLQ fix, config binding strategy)
**Related to**: `sdk-improvement-research.md` Phase 3 deferred items

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
      description: "Extract user preferences (brand, price, style)"
      trigger-keywords: ["prefer", "like", "更喜欢", "倾向于"]
      source-filter: ["user_statement", "manual"]
      prompt: |   # Maps to promptTemplate field in Java record
        From the following conversation, extract user preferences.
        Look for: brands they like/dislike, budget constraints, style preferences.
      output-schema: |   # Required for Map templates; BeanOutputConverter<Map> uses this
        {
          "type": "object",
          "properties": {
            "category": {"type": "string"},
            "value": {"type": "string"},
            "confidence": {"type": "number"}
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
     * Generic extraction - runs all templates.
     * What is extracted is determined by the template prompts.
     */
    public void runExtraction(String projectPath) {
        for (ExtractionTemplate template : templates) {
            if (!template.enabled()) {  // Skip disabled templates
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
     * Extract by specific template using Spring AI structured output.
     * Supports two patterns: POJO (type-safe) and Map (flexible schema).
     * 
     * Requires LlmService.chatCompletionStructured() — see Bug 2 fix in section 0.1.
     */
    @SuppressWarnings("unchecked")
    public <T> ExtractionResult<T> extractByTemplate(
        String projectPath, 
        ExtractionTemplate template) {
        
        // 1. Resolve output type from templateClass
        Class<T> outputClass = resolveOutputClass(template.templateClass());
        
        // 2. Find candidate observations (requires new findBySourceIn method)
        List<ObservationEntity> candidates = observationRepository
            .findBySourceIn(projectPath, template.sourceFilter(), 100);
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        // 3. Build prompt (schema source depends on template type)
        String schemaHint = buildSchemaHint(template, outputClass);
        String prompt = buildPrompt(template, candidates, schemaHint);
        
        // 4. Call LLM with Spring AI structured output via LlmService
        // NOTE: This requires LlmService.chatCompletionStructured() to be implemented.
        T result = llmService.chatCompletionStructured(
            template.promptTemplate(),  // system prompt
            prompt,                    // user prompt
            outputClass                // target class for structured parsing
        );
        
        // 5. Store result
        return storeExtractionResult(template, result, candidates);
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
     * Template extraction runner with state tracking.
     */
    private void runTemplateExtraction(String projectPath, ExtractionTemplate template) {
        // Check extraction state for incremental extraction
        ExtractionState state = getExtractionState(projectPath, template.name());
        
        // Find NEW candidates since last extraction
        List<ObservationEntity> candidates = (state == null)
            ? observationRepository.findBySourceIn(projectPath, template.sourceFilter(), 100)
            : observationRepository.findNewObservations(projectPath, template.sourceFilter(), state.lastExtractedAt(), 100);
        
        if (candidates.isEmpty()) {
            return;
        }
        
        // Process and store
        extractByTemplate(projectPath, template);
        updateExtractionState(projectPath, template.name(), OffsetDateTime.now());
    }
    
    private String buildPrompt(ExtractionTemplate template, 
                               List<ObservationEntity> candidates,
                               String schemaHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("Extract structured information from the following observations.\n\n");
        sb.append("Observations:\n");
        
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
    
    private <T> ExtractionResult<T> storeExtractionResult(
        ExtractionTemplate template, 
        T result,
        List<ObservationEntity> sourceObservations) {
        
        ObservationEntity extractionObs = new ObservationEntity();
        extractionObs.setType("extracted_" + template.name());
        extractionObs.setSource("extraction:" + template.name());
        extractionObs.setExtractedData(convertToMap(result));
        extractionObs.setConcepts(List.of("extracted", template.name()));
        
        if (template.trackEvolution()) {
            checkAndUpdateEvolution(template, result);
        }
        
        return new ExtractionResult<>(result, extractionObs);
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
| **Phase 3.1** | StructuredExtractionService + templates | Generic extraction engine |
| **Phase 3.2** | Template configurations | YAML configs for various extraction types |
| **Phase 3.3** | ConflictDetector | Conflict detection during evolution |
| **Phase 3.4** | DeepRefine integration | Run extraction during deep refine |
| **Phase 3.5** | Manual review (optional) | API + UI for conflict review |

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

**⚠️ New repository methods STILL NOT IMPLEMENTED** (v11 status: pending):

```java
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
| 4 | Add `chatCompletionStructured()` | `LlmService.java` | Uses `BeanOutputConverter<T>` from `org.springframework.ai.converter` |

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

---

## Changelog

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
