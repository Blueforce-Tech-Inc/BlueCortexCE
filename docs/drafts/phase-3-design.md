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

## 7. Open Questions

1. Should extraction run on every `deepRefine` or be scheduled separately?
2. Should conflicts auto-resolve or require human approval?
3. What is acceptable latency for extraction?
4. How to handle cross-project extractions (e.g., user preferences)?

---

## Changelog

- **2026-03-21 v2**: Generalized approach - extracted data type is determined by prompt, not code. Renamed from "PreferenceExtractionService" to "StructuredExtractionService". Added allergy_info example.
- **2026-03-21 v1**: Initial design document created
