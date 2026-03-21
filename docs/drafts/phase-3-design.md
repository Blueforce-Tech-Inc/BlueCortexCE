# Phase 3 Design Proposal: Structured Information Extraction & Memory Conflict Detection

**Date**: 2026-03-21
**Status**: Design proposal - iteration 7 (critical code-bug fixes + Spring AI structured output)
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

**Better approach**: Use Spring AI's structured output support:

```java
// Option A: Use Spring AI ChatClient with structured output
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;

public <T> T extractByTemplate(String projectPath, ExtractionTemplate template, Class<T> outputType) {
    // Build prompt with schema
    String prompt = buildPrompt(template, candidates);
    
    // Use Spring AI structured output converter
    StructuredOutputConverter<T> converter = new JacksonOutputConverter<>(outputType, template.outputSchema());
    PromptTemplate promptTemplate = new PromptTemplate(prompt);
    
    T result = chatClient.prompt()
        .system(template.promptTemplate())
        .user(promptTemplate.render())
        .advisors() // optional: withAdapter
        .call()
        .entity(outputType);  // Spring AI parses directly into the type
    
    return result;
}

// Option B: With explicit output converter
ChatClient.ChatClientRequestSpec request = chatClient.prompt()
    .system(s -> s.text(template.prompt()))
    .user(prompt);

StructuredOutputConverter<T> converter = JacksonOutputConverter.builder()
    .schema(template.outputSchema())
    .build(outputType);

T result = request.call().entity(converter);
```

**Why this is better**:
- Spring AI validates the output against the schema
- Direct type-safe object binding, no raw JSON parsing
- Supports JSON Schema as input for output constraints
- Works with any Spring AI compatible model (OpenAI, Anthropic, etc.)

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
 */
public record ExtractionTemplate(
    String name,                    // Template identifier
    String description,             // Human-readable description
    List<String> triggerKeywords,   // Keywords to filter candidates
    List<String> sourceFilter,      // Which sources to consider (⚠️ List, not String)
    String promptTemplate,           // System prompt for extraction instruction
    String outputSchema,            // JSON Schema for output format
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
      description: "Extract user preferences (brand, price, style)"
      trigger-keywords: ["prefer", "like", "更喜欢", "倾向于"]
      source-filter: ["user_statement", "manual"]
      prompt: |   # Maps to promptTemplate field in Java record
        From the following conversation, extract user preferences.
        Look for: brands they like/dislike, budget constraints, style preferences.
      output-schema: |
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
      description: "Extract allergy and dietary information"
      trigger-keywords: ["过敏", "不能吃", "吃了会", "allergic"]
      source-filter: ["user_statement", "manual", "llm_inference"]
      prompt: |
        From the conversation, extract allergy information:
        - Who has the allergy (person)
        - What allergens
        - Severity if mentioned
      output-schema: |
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
      description: "Extract important dates and events"
      trigger-keywords: ["生日", "anniversary", "纪念日", "记得"]
      source-filter: ["user_statement", "manual"]
      prompt: |
        Extract important dates mentioned: birthdays, anniversaries, events.
        Include: date, occasion, who's involved.
      output-schema: |
        {
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
      track-evolution: false
      conflict-enabled: false
```

### 2.3 Generic StructuredExtractionService

```java
@Service
public class StructuredExtractionService {
    
    @Value("${app.memory.extraction.templates}")
    private List<ExtractionTemplate> templates;
    
    private final ChatClient chatClient;  // Spring AI ChatClient (not LlmService)
    private final ObservationRepository observationRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Generic extraction - runs all templates.
     * What is extracted is determined by the template prompts.
     */
    public void runExtraction(String projectPath) {
        for (ExtractionTemplate template : templates) {
            try {
                runTemplateExtraction(projectPath, template);
            } catch (Exception e) {
                log.error("Extraction failed for template {}: {}", template.name(), e.getMessage());
            }
        }
    }
    
    /**
     * Extract by specific template using Spring AI structured output.
     */
    public <T> ExtractionResult<T> extractByTemplate(
        String projectPath, 
        ExtractionTemplate template,
        Class<T> outputType) {
        
        // 1. Find candidate observations (requires new findBySourceIn method)
        List<ObservationEntity> candidates = observationRepository
            .findBySourceIn(projectPath, template.sourceFilter(), 100);
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        // 2. Build prompt
        String prompt = buildPrompt(template, candidates);
        
        // 3. Call LLM with Spring AI structured output
        T result = chatClient.prompt()
            .system(s -> s.text(template.promptTemplate()))  // Extraction instruction
            .user(prompt)                                    // Observations + schema
            .call()
            .entity(outputType);                            // Direct typed parsing
        
        // 4. Store result
        return storeExtractionResult(template, result, candidates);
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
        extractByTemplate(projectPath, template, candidates);
        updateExtractionState(projectPath, template.name(), OffsetDateTime.now());
    }
    
    private String buildPrompt(ExtractionTemplate template, 
                               List<ObservationEntity> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("Extract structured information from the following observations.\n\n");
        sb.append("Observations:\n");
        
        for (ObservationEntity obs : candidates) {
            sb.append(String.format("- [%s] %s\n  %s\n",
                sanitize(obs.getSource()),
                obs.getTitle() != null ? sanitize(obs.getTitle()) : "",
                obs.getContent() != null ? sanitize(obs.getContent()) : ""));
        }
        
        sb.append("\nOutput JSON according to this schema:\n");
        sb.append(template.outputSchema());
        
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
}
```

### 2.4 Example: Allergy Extraction (from user's example)

```java
// Template configuration (YAML)
templates:
  - name: "allergy_info"
    trigger-keywords: ["过敏", "allergic", "不能吃"]
    prompt: |
      从对话中提取过敏信息：
      - 谁过敏 (person)
      - 过敏原 (allergens) 
      - 严重程度（如有）
    output-schema: |
      {"type": "object", "properties": {"person": "string", "allergens": ["string"], "severity": "string"}}

// Usage
ExtractionTemplate allergyTemplate = templates.get("allergy_info");
var result = extractionService.extractByTemplate(projectPath, allergyTemplate, AllergyInfo.class);

// result.content() contains:
// {"person": "孩子", "allergens": ["花生", "虾"], "severity": "严重"}
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
 */
public record ExtractionState(
    String projectPath,
    String templateName,
    OffsetDateTime lastExtractedAt,
    UUID lastExtractedObservationId,
    int totalExtracted
) {}

// Repository method to find new observations since last extraction
List<ObservationEntity> findNewObservations(
    String projectPath, 
    OffsetDateTime since
);
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

---

## 9. Implementation Feasibility Check

### 9.1 Existing Repository Methods

**Confirmed**: The following methods already exist in `ObservationRepository`:
- `findBySource(project, source, limit)` - For filtering by source
- `findByType(project, type, limit)` - For finding extraction results

**No new repository methods needed** for basic extraction functionality.

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
        // Use tokenService.estimateTokens() for estimation
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

```java
/**
 * Failed extraction requests are queued for manual review or retry.
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

// On failure, save to dead letter queue
public void handleExtractionFailure(...) {
    FailedExtraction failed = new FailedExtraction(
        requestId, projectPath, template.name(), observationIds,
        error.getMessage(), attemptCount, OffsetDateTime.now()
    );
    deadLetterQueue.add(failed);
    
    // Alert if threshold exceeded
    if (deadLetterQueue.size() > threshold) {
        alertService.alert("Extraction failure rate exceeds threshold");
    }
}

// Scheduled task to retry dead letter items
@Scheduled(fixedRate = 3600000)  // Every hour
public void retryDeadLetterExtractions() {
    List<FailedExtraction> toRetry = deadLetterQueue.getPending();
    for (FailedExtraction failed : toRetry) {
        if (failed.attemptCount() < maxRetries) {
            retryExtraction(failed);
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

## Changelog

- **2026-03-21 v7**: Fixed 3 critical bugs: (1) `findBySource(project, sourceFilter List)` → must use new `findBySourceIn(project, List<String>, limit)` method (findBySource takes String only). (2) Manual JSON parsing → replaced with Spring AI structured output via `ChatClient.call().entity(outputType)`. (3) Integration ambiguity → clarified extraction is a separate pipeline from refinement, only called as last step of deepRefineProjectMemories. Updated all `template.prompt()` references to `template.promptTemplate()`. Added `@JsonProperty` mapping note for YAML.
- **2026-03-21 v6**: Fixed findByType missing projectPath (cross-project pollution bug). Added error handling & recovery strategies: retry with circuit breaker, dead letter queue, schema validation failure handling, graceful degradation.
- **2026-03-21 v5**: Added critical implementation considerations: context window batching, prompt injection prevention, cost control (dry-run, rate limiting, caching), observability & audit trail, multi-language support
- **2026-03-21 v4**: Confirmed existing repository methods (findBySource, findByType) are reusable. Added integration points section.
- **2026-03-21 v3**: Added incremental extraction, query API, template versioning, LLM validation, cascading extractions considerations
- **2026-03-21 v2**: Generalized approach - extracted data type is determined by prompt, not code
- **2026-03-21 v1**: Initial design document created
