# Phase 3 Design Proposal: Preference Extraction & Memory Conflict Detection

**Date**: 2026-03-21
**Status**: Design proposal - awaiting review
**Related to**: `sdk-improvement-research.md` Phase 3 deferred items

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
| `source` | Source attribution | Preference source marking |
| `extractedData` (JSONB) | Structured data | Preference storage, conflict records |
| `qualityScore` | Quality scoring | Conflict detection辅助 |
| `refinedAt` | Refinement timestamp | Preference evolution timeline |

---

## 2. Preference Extraction Design

**Core Idea**: Add **configuration-driven extraction rules** to `deepRefineProjectMemories()`.

### 2.1 Configuration Model

```yaml
app.memory.preference:
  enabled: true
  extraction-rules:
    - name: "brand_preference"
      description: "Extract user brand preferences from statements"
      source-filter: ["user_statement", "manual", "llm_inference"]
      pattern-keywords: ["prefer", "like", "更喜欢", "品牌", "brand"]
      output-field: "preferred_brand"
      conflict-enabled: true
      
    - name: "price_range"
      description: "Extract budget/price constraints"
      source-filter: ["user_statement", "manual"]
      pattern-keywords: ["budget", "预算", "价格", "cost"]
      output-field: "price_range"
      conflict-enabled: true
```

### 2.2 PreferenceExtractionService Design

```java
@Service
public class PreferenceExtractionService {
    
    // Configuration-driven extraction rules
    @Value("${app.memory.preference.extraction-rules}")
    private List<ExtractionRule> rules;
    
    // Dependencies
    private final LlmService llmService;
    private final ObservationRepository observationRepository;
    
    /**
     * Extract preference information from observations.
     * Runs during deepRefine phase.
     */
    public void extractPreferences(String projectPath) {
        for (ExtractionRule rule : rules) {
            // 1. Find matching observations
            List<ObservationEntity> candidates = observationRepository
                .findBySource(projectPath, rule.getSourceFilter());
            
            // 2. Use LLM to extract
            List<ExtractedPreference> extracted = llmService.extractByRule(
                rule, candidates);
            
            // 3. Store to extractedData
            for (ExtractedPreference pref : extracted) {
                storePreference(pref, projectPath);
            }
        }
    }
    
    /**
     * Store preference, detect evolution.
     */
    private void storePreference(ExtractedPreference pref, String projectPath) {
        // Find existing preference
        List<ObservationEntity> existing = observationRepository
            .findByExtractedDataKey(projectPath, pref.getFieldName());
        
        if (existing.isEmpty()) {
            // New preference, store directly
            createPreferenceObservation(pref, projectPath);
        } else {
            // Already exists, detect evolution
            ExtractedPreference oldPref = parseExisting(existing.get(0));
            if (!oldPref.getValue().equals(pref.getValue())) {
                // Preference changed, record evolution history
                pref.setEvolutionHistory(List.of(
                    Map.of("value", oldPref.getValue(), 
                           "extractedAt", oldPref.getExtractedAt())
                ));
                // Update or create new observation
                updatePreferenceObservation(existing.get(0), pref);
            }
        }
    }
}
```

### 2.3 Preference Observation Structure

```json
{
  "type": "preference",
  "title": "Brand Preference: Sony",
  "source": "preference_extracted",
  "extractedData": {
    "preference_type": "brand",
    "value": "sony",
    "confidence": 0.85,
    "evolution_history": [
      {"value": "bose", "extractedAt": "2026-03-15T10:00:00Z"}
    ],
    "original_observations": ["obs-id-1", "obs-id-2"]
  },
  "concepts": ["preference", "brand", "verified"],
  "qualityScore": 0.8
}
```

---

## 3. Memory Conflict Detection Design

**Core Idea**: Add conflict detection step to `deepRefineProjectMemories()`.

### 3.1 Conflict Detection Flow

```java
/**
 * Deep refine with conflict detection.
 */
public void deepRefineWithConflictDetection(String projectPath) {
    // 1. Existing: Find candidates for refinement
    List<ObservationEntity> candidates = findRefineCandidates(projectPath);
    
    // 2. New: Detect conflicts
    List<ConflictGroup> conflicts = detectConflicts(candidates);
    
    for (ConflictGroup conflict : conflicts) {
        // 3. Resolve or flag
        if (conflict.isAutoResolvable()) {
            autoResolveConflict(conflict);
        } else {
            flagForReview(conflict);
        }
    }
    
    // 4. Continue existing refine logic
    refineObservations(candidates, projectPath);
}

/**
 * Detect conflict groups.
 */
private List<ConflictGroup> detectConflicts(List<ObservationEntity> candidates) {
    List<ConflictGroup> conflicts = new ArrayList<>();
    
    // Group by semantic similarity (using existing embeddings or keywords)
    Map<String, List<ObservationEntity>> groups = groupBySemanticSimilarity(candidates);
    
    for (List<ObservationEntity> group : groups.values()) {
        if (group.size() >= 2) {
            // Detect conflicts within group
            ConflictDetector detector = new ConflictDetector(llmService);
            Optional<ConflictGroup> conflict = detector.detect(group);
            conflict.ifPresent(conflicts::add);
        }
    }
    
    return conflicts;
}
```

### 3.2 ConflictDetector Implementation

```java
public class ConflictDetector {
    
    private final LlmService llmService;
    
    /**
     * LLM judges if contradiction exists.
     */
    public Optional<ConflictGroup> detect(List<ObservationEntity> observations) {
        String prompt = buildConflictDetectionPrompt(observations);
        
        String response = llmService.chatCompletion(
            "You are a memory consistency analyst. Detect contradictions in observations.",
            prompt
        );
        
        return parseLLMResponse(response, observations);
    }
    
    private String buildConflictDetectionPrompt(List<ObservationEntity> obs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze these observations for contradictions:\n\n");
        
        for (int i = 0; i < obs.size(); i++) {
            ObservationEntity o = obs.get(i);
            sb.append(String.format("[%d] %s\n%s\nsource: %s\nextractedData: %s\n\n",
                i + 1,
                o.getTitle() != null ? o.getTitle() : "Untitled",
                o.getContent() != null ? o.getContent() : "",
                o.getSource(),
                o.getExtractedDataJson()));
        }
        
        sb.append("Detect: 1) Direct contradictions 2) Preference evolution 3) Implicit conflicts\n");
        sb.append("Response format: {\"has_conflict\": true/false, \"type\": \"...\", \"resolution\": \"...\"}");
        
        return sb.toString();
    }
}
```

### 3.3 Conflict Types

| Type | Example | Handling |
|------|---------|----------|
| **Direct Contradiction** | "budget is $3000" vs "budget is $5000" | LLM arbitration, keep newer |
| **Preference Evolution** | "likes Sony" → "now likes Bose" | Record history, don't delete old |
| **Implicit Conflict** | System limits vs user expectations | Flag for manual review |
| **Fact Error** | Incorrect technical statement | Correct and record original error |

### 3.4 Conflict Resolution Result Storage

```json
{
  "type": "conflict_resolution",
  "title": "Conflict Resolved: Price Range",
  "source": "conflict_resolved",
  "extractedData": {
    "conflict_type": "direct_contradiction",
    "resolution": "Updated to $5000 based on recent context",
    "original_values": ["$3000", "$5000"],
    "resolved_value": "$5000",
    "confidence": 0.9,
    "involved_observations": ["obs-id-1", "obs-id-2"]
  },
  "refinedFromIds": "obs-id-1,obs-id-2",
  "qualityScore": 0.85
}
```

---

## 4. UserProfile Design (Deferred Recommendation)

**Current Solution is Sufficient**: Session-based isolation

```java
// External user as special session
contentSessionId = "blue-cortex:ext-user-id:USER_123"

// Store user preferences
curl -X POST /api/ingest/observation \
  -d '{"session_id": "blue-cortex:ext-user-id:USER_123",
       "type": "user_profile",
       "source": "profile_update",
       "extractedData": {"display_name": "John", "settings": {...}}}'
```

**When Independent Entity is Needed**:
- Profile-level CRUD operations required
- Profile inheritance/relationships needed
- Profile version control required

---

## 5. Implementation Roadmap

| Phase | Content | Changes |
|-------|---------|---------|
| **Phase 3.1** | PreferenceExtractionService + config | New Service + config |
| **Phase 3.2** | Preference Evolution History | Extend existing storage |
| **Phase 3.3** | ConflictDetector | New Detector class |
| **Phase 3.4** | DeepRefine integration | Modify MemoryRefineService |
| **Phase 3.5** | Manual review mechanism (optional) | New API + WebUI |

---

## 6. Risks and Challenges

| Risk | Impact | Mitigation |
|------|--------|------------|
| LLM extraction accuracy | Wrong extraction causes misjudgment | Confidence threshold + manual review |
| False conflict detection | Normal changes flagged as conflicts | Refine conflict type definitions |
| Performance impact | Deep refine becomes slower | Incremental processing + caching |
| Configuration complexity | Too many rules hard to maintain | Minimal configuration principle |

---

## 7. Design Principles Applied

| Principle | Application |
|-----------|-------------|
| **Open/Closed** | ExtractedData JSONB open for new fields, closed for code change |
| **Composition over inheritance** | Extraction rules as configuration vs new entities |
| **YAGNI** | Don't add UserProfile until explicitly needed |
| **Minimal entities** | Reuse existing ObservationEntity with type="preference" |

---

## Changelog

- **2026-03-21**: Initial design document created

---

## 8. Design Iterations & Open Questions

### 8.1 Preference Storage Structure

**Issue**: Using `output-field` as top-level key in `extractedData` may conflict with existing data.

**Better approach**: Use nested structure:
```json
{
  "extractedData": {
    "preference": {
      "type": "brand",
      "value": "sony",
      "confidence": 0.85,
      "extracted_at": "2026-03-21T10:00:00Z"
    }
  }
}
```

### 8.2 Conflict Detection Trigger Timing

**Current**: Runs only during `deepRefineProjectMemories()`

**Alternative consideration**: Run lightweight conflict detection during regular `refineMemory()`:
- Quick check: Compare newly created observation with recent observations
- Only invoke LLM for deep analysis when quick check flags potential conflict

### 8.3 LLM Call Reliability

**Missing in current design**:
- Retry mechanism for failed LLM calls
- Timeout handling (configurable)
- Partial failure handling (some extractions succeed, some fail)
- Circuit breaker pattern for LLM service

**Proposed addition**:
```java
@Value("${app.memory.llm.retry.max-attempts:3}")
private int maxRetryAttempts;

@Value("${app.memory.llm.timeout-seconds:30}")
private int llmTimeoutSeconds;
```

### 8.4 Repository Method Requirements

**Methods that need to be added to ObservationRepository**:

| Method | Purpose | Note |
|--------|---------|------|
| `findByExtractedDataKey(projectPath, key)` | Find observations with specific extractedData key | Not yet implemented |
| `findByTypeAndProject(type, projectPath)` | Find preference observations | Already exists as `findByType` |
| `findRecentBySource(projectPath, source, limit)` | Find recent observations by source | Could leverage existing |

### 8.5 Testing Strategy

**Unit tests needed**:
- `PreferenceExtractionServiceTest`: Mock LLM responses
- `ConflictDetectorTest`: Various conflict scenarios
- `ExtractionRuleTest`: Rule parsing and matching

**Integration tests needed**:
- End-to-end extraction with real LLM (mock or test provider)
- Conflict detection accuracy evaluation
- Evolution history tracking

### 8.6 Configuration Complexity vs Flexibility

**Trade-off**: More flexible rules = more complex configuration

**Minimal viable product (MVP)** approach:
1. Start with simple keyword-based extraction (no LLM)
2. Add LLM extraction as opt-in feature
3. Default to low confidence threshold for first release

### 8.7 Open Questions for Review

1. Should preference extraction run on every `deepRefine` or be triggered separately?
2. Should conflicts automatically resolve or always require human approval?
3. What is the acceptable latency for extraction/conflict detection?
4. Should we store failed extraction attempts for later review?
5. How to handle cross-project preference aggregation (e.g., user preferences across projects)?
