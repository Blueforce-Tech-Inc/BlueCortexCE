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

**Naive Solution**: Add `MemoryImportance enum { LOW, MEDIUM, HIGH }`

**Problem with Naive Solution**: What when we need "verified", "unverified", "pending-review", "deprecated"? Another enum? Another field?

**Generalized Solution**: **Add `tags` field to `ObservationEntity`** (mem_observations table)

```java
// In ObservationEntity.java (mem_observations table)
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "tags", columnDefinition = "jsonb")
private List<String> tags;
// Usage: tags = ["important", "user-statement", "verified"]
```

**Why Generalized**:
- Users define their own tags (no code change for new tag types)
- One observation can have multiple tags
- Tags are filterable in search
- Future-proof: "deprecated", "needs-review", "core-requirement" all work

**Trade-off**: No compile-time type safety (but tags are user-defined, not system enum)

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

**Problem with Naive Solution**: 
- Preference is essentially a structured observation
- New table = new entity complexity
- Preference can be derived from existing `facts` field

**Generalized Solution**: **Add `extractedPreferences` JSONB field to `ObservationEntity`** (mem_observations table)

```java
// In ObservationEntity.java (mem_observations table)
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "extracted_preferences", columnDefinition = "jsonb")
private Map<String, Object> extractedPreferences;
// extractedPreferences = {"price_range": "3000", "brands": ["sony", "bose"], "category": "headphones"}
```

**Why Generalized**:
- Extends existing `ObservationEntity` (no new table)
- Preferences are extracted from observations, not separate concept
- Query by JSONB operators if needed

**Trade-off**: Preference queries need JSONB parsing (acceptable)

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
    List<String> facts,
    List<String> tags,           // Updated tags
    Map<String, Object> extractedPreferences  // Updated preferences
) {}

void updateObservation(String id, ObservationUpdate update);
void deleteObservation(String id);
```

**Why not a new entity**: This is an operation DTO, not a domain entity.

---

## 5. Final Recommendation: Minimal Entity Extension

### 5.1 Required Changes to ObservationEntity

**Table**: `mem_observations` (via `ObservationEntity.java`)

| Field | Type | Purpose | Gap Addressed |
|-------|------|---------|---------------|
| `tags` | `List<String>` (JSONB) | User-defined labels | Gap 1 (Importance) |
| `source` | `String` | Source attribution | Gap 2 |
| `userId` | `String` | Multi-user isolation | Gap 4 |
| `extractedPreferences` | `Map<String, Object>` (JSONB) | Structured preferences | Gap 3 |

### 5.2 No New Entities Required

**All fields go to `mem_observations` table (ObservationEntity)**

| Proposed Entity | Decision | Reason |
|-----------------|----------|--------|
| `MemoryImportance` enum | ❌ Rejected | Use `tags` field on `ObservationEntity` instead |
| `ObservationSource` enum | ❌ Rejected | Use `source` String field on `ObservationEntity` instead |
| `UserPreference` table | ❌ Rejected | Use `extractedPreferences` JSONB on `ObservationEntity` instead |
| `UserProfile` table | ❌ Deferred | Use `userId` field on `ObservationEntity` now; add entity only when profile management needed |

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
1. Add `tags` (JSONB List) to `ObservationEntity` (mem_observations)
2. Add `source` (String) field to `ObservationEntity` (mem_observations)
3. Add `userId` (String) field to `ObservationEntity` (mem_observations)
4. Implement `PATCH /api/memory/observations/{id}` with `ObservationUpdate` DTO
5. Add tags/source to `ObservationRequest` in SDK

### Phase 2: Enhanced Capabilities (2-4 weeks)
6. Add `extractedPreferences` (JSONB Map) to `ObservationEntity` (mem_observations)
7. Implement adaptive truncation in `CortexMemoryAdvisor`
8. Add `MemoryManagementTools` for active memory edit/delete
9. Add tag-based filtering to search API

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
    
    // NEW: Generalized fields
    List<String> tags;                          // User-defined labels
    String source;                              // "tool_result", "user_statement", etc.
    String userId;                              // For multi-user isolation
    Map<String, Object> extractedPreferences;   // Structured preferences
}
```

### 7.2 ObservationUpdate DTO

```java
public record ObservationUpdate(
    String title,
    String content,
    List<String> facts,
    List<String> tags,           // Replace/add tags
    String source,
    Map<String, Object> extractedPreferences
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
