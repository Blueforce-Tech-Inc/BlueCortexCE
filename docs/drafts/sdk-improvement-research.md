# SDK Improvement Research: Addressing Spring AI Developer Pain Points

> **Date**: 2026-03-20  
> **Objective**: Analyze Spring AI developer pain points, evaluate how Cortex CE addresses them, and propose **generalized** improvements that avoid entity proliferation  
> **Key Principle**: Prefer extensible fields (tags, JSONB) over new entities/enums; new entities only when independent CRUD lifecycle or complex relationships exist  
> **Reference**: `spring-ai-skills-demo/docs/drafts/memory-system-improvement-plan.md`

---

## 1. Background

Spring AI developers face **eight critical pain points** when implementing memory systems. This document analyzes:

1. How our current Client SDK addresses each pain point
2. Gaps in the current implementation
3. **Generalized improvements** that avoid entity/enum proliferation

---

## 2. Pain Point Analysis

### 2.1 Passive Injection vs. Active Retrieval

**Problem**: `VectorStoreChatMemoryAdvisor` auto-injects retrieved memories every request. Agent cannot decide "I need to check my memory."

| Aspect | Current State | Assessment |
|--------|--------------|------------|
| **Spring AI Default** | Passive injection via Advisor | ❌ |
| **Cortex CE Advisor** | `CortexMemoryAdvisor` - same pattern | ⚠️ Passive |
| **Cortex CE Tools** | `CortexMemoryTools.searchMemories()` | ✅ Agent decides |

**Verdict**: **Partially addressed**.

---

### 2.2 Memory Only Stores, No Management

**Problem**: Dialog history only grows. No update, delete, or weight mechanisms.

| Aspect | Current State | Assessment |
|--------|--------------|------------|
| **Append-only storage** | Observations only added | ❌ No update path |
| **Memory editing** | No API to modify | ❌ |
| **Importance marking** | No `important` vs `transient` | ❌ |
| **TTL/Expiration** | No time-based cleanup | ❌ |

**Verdict**: **Not addressed**.

---

### 2.3 Raw Text vs. Structured Knowledge

**Problem**: Cannot reason about "user's budget is $3000" from raw embeddings.

| Aspect | Current State | Assessment |
|--------|--------------|------------|
| **Storage format** | Structured fields (facts, concepts) | ✅ Partial |
| **Structured extraction** | LLM extracts facts/concepts | ✅ Implemented |
| **Preference queries** | Experience.task/strategy/outcome | ✅ Implemented |
| **Preference API** | No dedicated extraction endpoint | ❌ Gap |

**Verdict**: **Partially addressed** - structured fields exist but preference extraction not exposed.

---

### 2.4 No Memory Confidence/Priority

**Problem**: All memories have equal weight.

| Aspect | Current State | Assessment |
|--------|--------------|------------|
| **Quality scoring** | `QualityScorer` assigns 0.0-1.0 | ✅ Implemented |
| **ExpRAG uses quality** | Filters by quality | ✅ Implemented |
| **User-settable importance** | No `important` flag | ❌ Gap |

**Verdict**: **Partially addressed** - system quality score exists, but user cannot mark importance.

---

### 2.5 No Memory Consistency Maintenance

**Problem**: New dialog appends independently. Contradictions undetected.

| Aspect | Current State | Assessment |
|--------|--------------|------------|
| **Conflict detection** | No mechanism | ❌ |
| **Memory merging** | No consolidation | ❌ |
| **Preference evolution** | "Sony → Bose" not tracked | ❌ |

**Verdict**: **Not addressed**.

---

### 2.6 Underutilized Metadata

| Aspect | Current State | Assessment |
|--------|--------------|------------|
| **Time-based filtering** | `startEpoch`/`endEpoch` | ✅ |
| **Type/concept filtering** | Supported | ✅ |
| **Quality-based filtering** | Supported | ✅ |
| **Recency weighting** | 90-day window | ✅ |
| **Source attribution** | No source field | ❌ Gap |

**Verdict**: **Well addressed** except source.

---

### 2.7 No User Profile Mechanism

| Aspect | Current State | Assessment |
|--------|--------------|------------|
| **User identification** | `conversationId` only | ⚠️ Implicit |
| **Cross-session profiles** | Per-session, not per-user | ❌ |
| **User preference store** | No dedicated entity | ❌ |

**Verdict**: **Not addressed**.

---

### 2.8 Context Window Pressure

| Aspect | Current State | Assessment |
|--------|--------------|------------|
| **Token tracking** | `TokenService` calculates | ✅ |
| **Token savings display** | Visible in context | ✅ |
| **Adaptive truncation** | No automatic truncation | ❌ Gap |
| **Smart selection** | ExpRAG by quality | ✅ |

**Verdict**: **Partially addressed**.

---

## 3. Core Problem: How Not to Proliferate Entities

Before proposing solutions, we must establish **when new entities are truly necessary**.

### 3.1 Principles for New Entity Decision

| Condition | New Entity Needed? | Rationale |
|-----------|------------------|-----------|
| Data is **attribute** of existing entity | ❌ No | Use field extension (JSONB, tags) |
| Concept is **taggable/labelable** | ❌ No | Use tagging system |
| Data needs **independent CRUD** lifecycle | ✅ Yes | UserProfile when user manages it |
| Data has **complex relationships** (1:N, M:N) | ✅ Yes | Junction tables |
| Data has **independent business logic** | ✅ Yes | Separate service/domain |

### 3.2 Entity vs. Field Extension Decision Tree

```
Is this a new concept with independent lifecycle?
├── NO → Is it a label/tag (can have multiple)?
│       ├── YES → Use Tags (List<String>)
│       └── NO → Is it a flexible key-value property?
│               ├── YES → Use JSONB field
│               └── NO → Is it a simple scalar value?
│                       ├── YES → Use String/Int field
│                       └── NO → Consider new entity
└── YES → Does it have complex relationships or independent CRUD?
          ├── NO → Consider if existing entity can extend
          └── YES → New entity is warranted
```

---

## 4. Gap Analysis with Generalized Solutions

### Gap 1: User-Settable Importance

**Problem**: User wants to mark "this is critical" vs "this is casual".

**Naive Solution**: Add new `tags` field or `MemoryImportance enum { LOW, MEDIUM, HIGH }`

**Decision**: **Use existing `concepts` field**

`ObservationEntity.concepts` is already a JSONB `List<String>`. Use `concepts` to store importance tags:

```java
// In ObservationEntity.java (mem_observations table)
// concepts is already: @Column(name = "concepts", columnDefinition = "jsonb") private List<String> concepts;

// Usage: concepts = ["important", "user-statement", "verified"]
// Usage: concepts = ["core-requirement", "bugfix"]
```

**Why Reuse `concepts`**:
- `concepts` is semantically appropriate for "labels/categories describing this observation"
- Existing field, no schema migration needed
- One observation can have multiple concept tags

**Trade-off**: No compile-time type safety (acceptable for user-defined tags)

---

### Gap 2: Source Attribution

**Problem**: Need to know if observation came from "tool result", "user statement", "LLM inference".

**Naive Solution**: `enum ObservationSource { TOOL_RESULT, USER_STATEMENT, ... }`

**Problem with Naive Solution**: Source types may grow (what about "extracted-from-document", " imported", "copied-from-context"?). Enum expansion = code change.

**Generalized Solution**: **Add `source` String field to `ObservationEntity`** (mem_observations table)

```java
// In ObservationEntity.java (mem_observations table)
@Column(name = "source")
private String source;  // Convention: "tool_result", "user_statement", "llm_inference", "manual"
// Or use tags: tags = ["source:user_statement"]
```

**Why Generalized**:
- String is flexible (no enum expansion)
- Convention documented, not enforced
- Can later add structured source with JSONB if needed

**Trade-off**: No type safety (acceptable for source attribution)

---

### Gap 3: Structured Preference Extraction

**Problem**: Want to answer "what is user's budget?" not "find memories about budget".

**Naive Solution**: New `UserPreference` table

**Decision**: **Add `extractedData` JSONB Map field**

`facts` and `concepts` are both `List<String>` — **flat string lists**. Using flat strings for structured data like `["price_range:3000", "brand:sony"]` causes problems:

- **Confusion**: Cannot distinguish "this is a structured fact" vs "this is a plain string"
- **Parsing complexity**: Java code needs polymorphic handling to interpret "key:value" strings
- **No type safety**: Cannot store typed values (integers, booleans, nested objects)

**Solution**: Add `extractedData` (JSONB Map<String, Object>) to `ObservationEntity`:

```java
// In ObservationEntity.java (mem_observations table)
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "extracted_data", columnDefinition = "jsonb")
private Map<String, Object> extractedData;
// extractedData = {"price_range": "3000", "brands": ["sony", "bose"], "category": "headphones"}
```

**Why This Field**:
- True structured storage with typed values (String, Integer, List, Map)
- Cannot be replaced by `facts` (flat strings) or `concepts` (labels)
- Enables PostgreSQL JSONB queries: ` WHERE extracted_data->>'price_range' = '3000'`

**Trade-off**: Additional JSONB column (but no entity complexity)

---

### Gap 4: Multi-User Data Isolation

**Problem**: Different users should not see each other's memories.

**Naive Solution**: New `UserProfile` table

**Problem with Naive Solution**: For simple data isolation, just need a `userId` field. `UserProfile` entity is only needed if we need to manage user profiles as first-class objects (display name, settings, etc.).

**Generalized Solution**: **Add `userId` field to `ObservationEntity`** (mem_observations table)

```java
// In ObservationEntity.java (mem_observations table)
@Column(name = "user_id")
private String userId;  // External user identifier
// Filter: WHERE user_id = 'user-123'
```

**When to elevate to `UserProfile` entity**: Only when:
- User has profile settings (display name, preferences, avatar)
- User needs to manage their own profile
- Profile has independent CRUD operations

**Trade-off**: Complex user management needs separate entity later

---

### Gap 5: Memory Update/Delete

**Problem**: Append-only limits utility for long-term agents.

**Solution**: **PATCH API + `ObservationUpdate` DTO** (not a new entity)

```java
public record ObservationUpdate(
    String title,
    String content,
    List<String> facts,      // Updated facts (use key:value convention)
    List<String> tags        // Updated tags
) {}

void updateObservation(String id, ObservationUpdate update);
void deleteObservation(String id);
```

**Why not a new entity**: This is an operation DTO, not a domain entity.

---

## 5. Final Recommendation

### 5.1 Required Changes to ObservationEntity

**Table**: `mem_observations` (via `ObservationEntity.java`)

| Field | Type | Purpose | Gap Addressed | Status |
|-------|------|---------|---------------|--------|
| `source` | `String` | Source attribution | Gap 2 | ✅ Add |
| `extractedData` | `Map<String, Object>` (JSONB) | Structured data (preferences, key-value) | Gap 3 | ✅ Add |

**Already solved without new fields**:
- **Gap 1 (Importance)**: Use existing `concepts` field with tag convention
- **Gap 4 (Multi-user)**: TBD — depends on whether `memorySessionId` is insufficient

### 5.2 No New Entities Required

**All fields go to `mem_observations` table (ObservationEntity)**

| Proposed Entity/Field | Decision | Reason |
|----------------------|----------|--------|
| `tags` field | ❌ Rejected | Use existing `concepts` field instead |
| `MemoryImportance` enum | ❌ Rejected | Use `concepts` tags |
| `ObservationSource` enum | ❌ Rejected | Use `source` String field |
| `extractedData` (JSONB Map) | ✅ Keep | Needed for true structured data (flat strings insufficient) |
| `UserPreference` table | ❌ Rejected | Use `extractedData` field |
| `UserProfile` table | ⏳ Deferred | TBD — depends on use case |
| `userId` field | ⏳ Deferred | TBD — depends on use case | |

### 5.3 When to Add Entities (Future Decision)

| Condition | Add Entity? |
|-----------|------------|
| Need to manage user profiles (display name, settings) | ✅ Yes - `UserProfile` |
| Need to track preference changes over time (history) | ✅ Yes - `PreferenceHistory` |
| Need complex many-to-many relationships | ✅ Yes - Junction tables |
| Need to query across observation types with different schemas | ✅ Yes - Separate entities |

---

## 6. Implementation Plan

### Phase 1: Minimal Extension (1-2 weeks)
All changes are **field extensions to `mem_observations` table** (ObservationEntity).
1. Add `source` (String) field to `ObservationEntity` (mem_observations)
2. Add `extractedData` (JSONB Map<String, Object>) to `ObservationEntity` (mem_observations)
3. Implement `PATCH /api/memory/observations/{id}` with `ObservationUpdate` DTO (include source and extractedData)
4. Add `source` and `extractedData` to `ObservationRequest` in SDK

### Phase 2: Enhanced Capabilities (2-4 weeks)
5. Implement adaptive truncation in `CortexMemoryAdvisor`
6. Add `MemoryManagementTools` for active memory edit/delete
7. Add `source`-based filtering to search API
8. (Gap 1 resolved with existing `concepts` field — no new field needed)
9. (Gap 4 TBD — depends on multi-user use case)

### Phase 3: Future Considerations (when needed)
10. `UserProfile` **entity** - only if profile management is a requirement
11. Preference history tracking
12. Memory conflict detection

---

## 7. SDK Enhancement Summary

### 7.1 Extended ObservationRequest

```java
public record ObservationRequest {
    // ... existing fields ...
    
    // NEW fields
    String source;                              // "tool_result", "user_statement", etc.
    Map<String, Object> extractedData;          // Structured data (preferences, key-value pairs)
    // Note: Use concepts for tags/labels (no new tags field needed)
}
```

### 7.2 ObservationUpdate DTO

```java
public record ObservationUpdate(
    String title,
    String content,
    List<String> facts,
    List<String> concepts,      // Use concepts for tags/labels
    String source,
    Map<String, Object> extractedData  // Structured key-value data
) {}
```

### 7.3 CortexMemClient Extension

```java
public interface CortexMemClient {
    // ... existing methods ...
    
    // Memory management
    void updateObservation(String id, ObservationUpdate update);
    void deleteObservation(String id);
    
    // Tag-based filtering (add to search)
    List<Experience> retrieveExperiences(
        ExperienceRequest request,
        List<String> requiredTags  // NEW: filter by tags
    );
}
```

---

## 8. Architecture Principles Applied

| Principle | Application |
|-----------|-------------|
| **Open/Closed** | Tags system open for extension (new tags), closed for code change |
| **Composition over inheritance** | Extending fields vs new entities |
| **YAGNI** | Don't add `UserProfile` until profile management is needed |
| **Minimal entities** | Only add entities with independent CRUD or complex relationships |

---

## 9. References

- `memory-system-improvement-plan.md` - Spring AI developer pain points
- `spring-ai-integration-plan.md` - Current integration architecture
- `spring-ai-integration-progress.md` - Implementation status
- `cortex-mem-integration-capture-analysis.md` - Capture coverage analysis
- [Spring AI Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- [Mem0: Universal memory layer for AI Agents](https://github.com/mem0ai/mem0)
- [LangMem: Managing User Profiles](https://langchain-ai.github.io/langmem/guides/manage_user_profile/)
