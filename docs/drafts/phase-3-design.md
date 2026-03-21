# Phase 3 Design Proposal: Structured Information Extraction & Memory Conflict Detection

**Date**: 2026-03-21
**Status**: Design proposal - iteration 2 (generalized approach)
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
 */
public record ExtractionTemplate(
    String name,                    // Template identifier
    String description,             // Human-readable description
    List<String> triggerKeywords,   // Keywords to filter candidates
    List<String> sourceFilter,      // Which sources to consider
    String promptTemplate,          // Prompt for LLM (what to extract)
    String outputSchema,             // JSON Schema for output format
    boolean trackEvolution,          // Whether to track value changes over time
    boolean conflictEnabled          // Whether to detect conflicts
) {}
```

### 2.2 Configuration Model (YAML)

```yaml
app.memory.extraction:
  enabled: true
  templates:
    - name: "user_preference"
      description: "Extract user preferences (brand, price, style)"
      trigger-keywords: ["prefer", "like", "更喜欢", "倾向于"]
      source-filter: ["user_statement", "manual"]
      prompt: |
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
    
    private final LlmService llmService;
    private final ObservationRepository observationRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Generic extraction - runs all templates.
     * What is extracted is determined by the template prompts.
     */
    public void runExtraction(String projectPath) {
        for (ExtractionTemplate template : templates) {
            try {
                extractByTemplate(projectPath, template);
            } catch (Exception e) {
                log.error("Extraction failed for template {}: {}", template.name(), e.getMessage());
            }
        }
    }
    
    /**
     * Extract by specific template.
     */
    public <T> ExtractionResult<T> extractByTemplate(
        String projectPath, 
        ExtractionTemplate template,
        Class<T> outputType) {
        
        // 1. Find candidate observations
        List<ObservationEntity> candidates = observationRepository
            .findBySource(projectPath, template.sourceFilter());
        
        // 2. Build prompt
        String prompt = buildPrompt(template, candidates);
        
        // 3. Call LLM
        String llmResponse = llmService.chatCompletion(
            "You are a structured information extraction expert.",
            prompt
        );
        
        // 4. Parse output
        T result = objectMapper.readValue(llmResponse, outputType);
        
        // 5. Store result
        return storeExtractionResult(template, result, candidates);
    }
    
    private String buildPrompt(ExtractionTemplate template, 
                               List<ObservationEntity> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append(template.prompt()).append("\n\n");
        sb.append("Observations:\n");
        
        for (ObservationEntity obs : candidates) {
            sb.append(String.format("- [%s] %s\n  %s\n",
                obs.getSource(),
                obs.getTitle() != null ? obs.getTitle() : "",
                obs.getContent() != null ? obs.getContent() : ""));
        }
        
        sb.append("\nOutput according to this schema:\n");
        sb.append(template.outputSchema());
        
        return sb.toString();
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
private void checkAndUpdateEvolution(ExtractionTemplate template, Object newResult) {
    List<ObservationEntity> existing = observationRepository
        .findByType("extracted_" + template.name());
    
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

## Changelog

- **2026-03-21 v4**: Confirmed existing repository methods (findBySource, findByType) are reusable. Added integration points section.
- **2026-03-21 v3**: Added incremental extraction, query API, template versioning, LLM validation, cascading extractions considerations
- **2026-03-21 v2**: Generalized approach - extracted data type is determined by prompt, not code
- **2026-03-21 v1**: Initial design document created
