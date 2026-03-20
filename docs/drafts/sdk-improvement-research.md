# SDK Improvement Research: Addressing Spring AI Developer Pain Points

> **Date**: 2026-03-20 (research) → 2026-03-21 (implementation complete)
> **Status**: ✅ All Phases 1, 2, and 4 implemented. Phase 3 deferred.
> **Objective**: Analyze Spring AI developer pain points, evaluate how Cortex CE addresses them, and provide **honest implementation records** of what was done
> **Key Principle**: Prefer extensible fields (tags, JSONB) over new entities/enums; new entities only when independent CRUD lifecycle or complex relationships exist
> **Session ID naming**: Persistence and APIs use `contentSessionId` / `content_session_id` only (Flyway V13). Examples below follow that naming.
> **Reference**: `spring-ai-skills-demo/docs/drafts/memory-system-improvement-plan.md`

---

## 1. Background

Spring AI developers face **eight critical pain points** when implementing memory systems. This document analyzes:

1. How our current Client SDK addresses each pain point
2. What was implemented to address each gap
3. How to use the implemented features

**Implementation Summary**:
- ✅ Phase 1: `source` + `extractedData` fields, PATCH/DELETE endpoints
- ✅ Phase 2: Adaptive truncation, MemoryManagementTools, source filtering
- ✅ Phase 4: Search API source filtering
- ⏳ Phase 3: Deferred (UserProfile, preference history, conflict detection)

---

## 2. Pain Point Analysis - Implementation Records

### 2.1 Passive Injection vs. Active Retrieval

**Problem**: `VectorStoreChatMemoryAdvisor` auto-injects retrieved memories every request. Agent cannot decide "I need to check my memory."

| Aspect | Current Implementation | Status |
|--------|----------------------|--------|
| **Spring AI Default** | Passive injection via Advisor | ❌ |
| **Cortex CE Advisor** | `CortexMemoryAdvisor` - injects on every call | ⚠️ Passive |
| **Cortex CE Tools** | `CortexMemoryTools.searchMemories()` | ✅ Agent decides |

**How to Use**:
```java
// Agent actively decides when to search memory
chatClient.prompt()
    .tools(cortexMemoryTools)  // Not auto-injected
    .user("How did I fix login before?")
    .call();
// The AI decides to call searchMemories() tool
```

**Assessment**: **Partially addressed** - passive injection (advisor) and active retrieval (tools) both available. Agent can choose which to use.

---

### 2.2 Memory Only Stores, No Management

**Problem**: Dialog history only grows. No update, delete, or weight mechanisms.

| Aspect | Implementation | Status |
|--------|----------------|--------|
| **Append-only storage** | ObservationEntity supports creation | ✅ |
| **Memory editing** | `PATCH /api/memory/observations/{id}` | ✅ Implemented |
| **Memory deletion** | `DELETE /api/memory/observations/{id}` | ✅ Implemented |
| **Importance marking** | Use `concepts` field with tags | ✅ Available |
| **TTL/Expiration** | No time-based cleanup | ❌ Not implemented |

**How to Use**:
```bash
# Update an observation
curl -X PATCH http://localhost:37777/api/memory/observations/{id} \
  -H 'Content-Type: application/json' \
  -d '{"title": "Updated Title", "source": "manual", "concepts": ["important", "verified"]}'

# Delete an observation
curl -X DELETE http://localhost:37777/api/memory/observations/{id}

# SDK usage
client.updateObservation(id, ObservationUpdate.builder()
    .title("Updated Title")
    .concepts(List.of("important", "verified"))
    .build());
client.deleteObservation(id);
```

**Assessment**: **Fully addressed** (except TTL) - Update and delete endpoints implemented. Importance marking available via `concepts` field.

---

### 2.3 Raw Text vs. Structured Knowledge

**Problem**: Cannot reason about "user's budget is $3000" from raw embeddings.

| Aspect | Implementation | Status |
|--------|----------------|--------|
| **Storage format** | Structured fields (facts, concepts) | ✅ |
| **Structured extraction** | LLM extracts facts/concepts | ✅ |
| **Preference queries** | Experience.task/strategy/outcome | ✅ |
| **extractedData field** | JSONB Map for structured key-value data | ✅ Implemented |
| **Preference API** | Use `/api/memory/experiences` with filters | ✅ Available |

**How to Use**:
```bash
# Create observation with extracted structured data
curl -X POST http://localhost:37777/api/ingest/observation \
  -H 'Content-Type: application/json' \
  -d '{
    "session_id": "session-123",
    "project_path": "/project",
    "title": "User preference",
    "source": "user_statement",
    "extractedData": {
      "price_range": "3000",
      "brands": ["sony", "bose"],
      "category": "headphones"
    }
  }'

# Query with source filter
curl -X POST http://localhost:37777/api/memory/experiences \
  -H 'Content-Type: application/json' \
  -d '{"task": "headphone recommendation", "source": "user_statement"}'
```

**Assessment**: **Fully addressed** - `extractedData` JSONB field enables structured key-value storage. `source` field enables attribution.

---

### 2.4 No Memory Confidence/Priority

**Problem**: All memories have equal weight.

| Aspect | Implementation | Status |
|--------|----------------|--------|
| **Quality scoring** | `QualityScorer` assigns 0.0-1.0 | ✅ Implemented |
| **ExpRAG uses quality** | Filters by quality threshold | ✅ Implemented |
| **User-settable importance** | Use `concepts` tags (e.g., "important") | ✅ Available |

**How to Use**:
```bash
# Mark as important via concepts
curl -X PATCH http://localhost:37777/api/memory/observations/{id} \
  -H 'Content-Type: application/json' \
  -d '{"concepts": ["important", "verified"]}'
```

**Assessment**: **Partially addressed** - System quality scoring exists. User can mark importance via `concepts` field, but no dedicated `important` flag.

---

### 2.5 No Memory Consistency Maintenance

**Problem**: New dialog appends independently. Contradictions undetected.

| Aspect | Implementation | Status |
|--------|----------------|--------|
| **Conflict detection** | No mechanism | ❌ |
| **Memory merging** | No consolidation | ❌ |
| **Preference evolution** | "Sony → Bose" not tracked | ❌ |

**Assessment**: **Not addressed** - This requires more sophisticated AI logic. Consider for future work.

---

### 2.6 Underutilized Metadata

| Aspect | Implementation | Status |
|--------|----------------|--------|
| **Time-based filtering** | `startEpoch`/`endEpoch` in search | ✅ |
| **Type/concept filtering** | `/api/search?concept=X` | ✅ |
| **Quality-based filtering** | ExpRAG filters by quality | ✅ |
| **Recency weighting** | 90-day window | ✅ |
| **Source attribution** | `source` field + search filter | ✅ Implemented |

**How to Use**:
```bash
# Search with source filter
curl "http://localhost:37777/api/search?project=/project&source=manual_test"

# Search with concept filter
curl "http://localhost:37777/api/search?project=/project&concept=important"

# Experiences with filters
curl -X POST http://localhost:37777/api/memory/experiences \
  -H 'Content-Type: application/json' \
  -d '{"task": "fix bug", "source": "llm_inference", "requiredConcepts": ["verified"]}'
```

**Assessment**: **Fully addressed** - All metadata filters implemented.

---

### 2.7 No User Profile Mechanism

| Aspect | Implementation | Status |
|--------|----------------|--------|
| **User identification** | `contentSessionId` in session | ✅ |
| **Cross-session profiles** | Session-based isolation | ✅ Available |
| **User preference store** | Use `extractedData` in observations | ✅ Available |

**Design Pattern**:
```bash
# External user as special session
contentSessionId = "blue-cortex:ext-user-id:USER_123"

# Store user preferences as observations
curl -X POST http://localhost:37777/api/ingest/observation \
  -d '{
    "session_id": "blue-cortex:ext-user-id:USER_123",
    "project_path": "/user-profile",
    "type": "user_preference",
    "extractedData": {"preference_type": "brand", "value": "sony"}
  }'
```

**Assessment**: **Available via session pattern** - No dedicated UserProfile entity needed for basic use cases.

---

### 2.8 Context Window Pressure

**Problem**: All memories injected causing token overflow.

| Aspect | Implementation | Status |
|--------|----------------|--------|
| **Token tracking** | `TokenService` calculates | ✅ |
| **Token savings display** | Visible in context | ✅ |
| **Adaptive truncation** | `maxChars` parameter in ICL prompt | ✅ Implemented |
| **Smart selection** | ExpRAG by quality | ✅ |

**How to Use**:
```bash
# ICL prompt with truncation
curl -X POST http://localhost:37777/api/memory/icl-prompt \
  -H 'Content-Type: application/json' \
  -d '{"task": "fix bug", "project": "/project", "maxChars": 2000}'

# SDK usage
var result = client.buildICLPrompt(ICLPromptRequest.builder()
    .task("fix bug")
    .project("/project")
    .maxChars(2000)  // Truncate to 2000 chars
    .build());

# CortexMemoryAdvisor with maxIclChars
CortexMemoryAdvisor.builder(client)
    .maxIclChars(4000)  // Default
    .build();
```

**Assessment**: **Fully addressed** - `maxChars` parameter enables adaptive truncation.

---

## 3. Core Problem: How Not to Proliferate Entities

### Decision Record

| Proposed Entity/Field | Decision | Reason |
|----------------------|----------|--------|
| `tags` field | ❌ Rejected | Use existing `concepts` field |
| `MemoryImportance` enum | ❌ Rejected | Use `concepts` tags |
| `ObservationSource` enum | ❌ Rejected | Use `source` String field |
| `extractedData` (JSONB Map) | ✅ Implemented | Needed for true structured data |
| `UserPreference` table | ❌ Rejected | Use `extractedData` in observations |
| `UserProfile` table | ⏳ Deferred | Only if profile management needed |
| `userId` field | ❌ Rejected | Session-based approach sufficient |

---

## 4. Gap Implementation Records

### Gap 1: User-Settable Importance ✅ SOLVED

**Solution**: Use existing `concepts` field

**Implementation**:
```java
// concepts already exists: List<String>
observation.setConcepts(List.of("important", "verified", "bugfix"));
```

**Usage**: No new field needed. Tag importance via concepts.

---

### Gap 2: Source Attribution ✅ SOLVED

**Solution**: `source` String field on ObservationEntity

**Implementation** (Flyway V14):
```sql
ALTER TABLE mem_observations ADD COLUMN source TEXT;
CREATE INDEX idx_obs_source ON mem_observations(source);
```

**Usage**:
```bash
curl -X POST /api/ingest/observation \
  -d '{"source": "tool_result", ...}'

curl "http://localhost:37777/api/search?source=manual"
```

---

### Gap 3: Structured Preference Extraction ✅ SOLVED

**Solution**: `extractedData` JSONB Map field

**Implementation** (Flyway V14):
```sql
ALTER TABLE mem_observations ADD COLUMN extracted_data JSONB;
CREATE INDEX idx_obs_extracted_data_gin ON mem_observations USING GIN (extracted_data jsonb_path_ops);
```

**Usage**:
```bash
curl -X POST /api/ingest/observation \
  -d '{"extractedData": {"price_range": "3000", "brands": ["sony"]}, ...}'
```

---

### Gap 4: Multi-User Data Isolation ✅ SOLVED (via session pattern)

**Solution**: Session-based isolation, no new entity

**Usage**:
```bash
# External user as special session ID
session_id = "blue-cortex:ext-user-id:USER_XXX"
```

---

### Gap 5: Memory Update/Delete ✅ SOLVED

**Solution**: PATCH/DELETE endpoints

**Implementation**:
```
PATCH /api/memory/observations/{id}
DELETE /api/memory/observations/{id}
```

**SDK**:
```java
client.updateObservation(id, ObservationUpdate.builder()
    .title("Updated")
    .source("manual")
    .extractedData(Map.of("key", "value"))
    .build());
client.deleteObservation(id);
```

---

## 5. API Reference

### Core Endpoints

| Method | Endpoint | Description | V14 Features |
|--------|----------|-------------|--------------|
| POST | `/api/ingest/observation` | Create observation | `source`, `extractedData` |
| PATCH | `/api/memory/observations/{id}` | Update observation | `source`, `extractedData`, `concepts` |
| DELETE | `/api/memory/observations/{id}` | Delete observation | - |
| GET | `/api/search` | Search with filters | `source`, `concept` |
| POST | `/api/memory/experiences` | Get ICL experiences | `source`, `requiredConcepts` |
| POST | `/api/memory/icl-prompt` | Build ICL prompt | `maxChars` |

### Query Parameters

| Parameter | Endpoint | Description |
|-----------|----------|-------------|
| `source` | `/api/search` | Filter by source attribution |
| `concept` | `/api/search` | Filter by concept tag |
| `source` | `/api/memory/experiences` | Filter experiences by source |
| `requiredConcepts` | `/api/memory/experiences` | Filter by required concepts |
| `maxChars` | `/api/memory/icl-prompt` | Max ICL prompt length |

---

## 6. Implementation Status

### ✅ Phase 1: Complete (V14)
- [x] `source` field on ObservationEntity
- [x] `extractedData` JSONB field
- [x] `PATCH /api/memory/observations/{id}`
- [x] `DELETE /api/memory/observations/{id}`
- [x] SDK updated (ObservationRequest, ObservationUpdate)

### ✅ Phase 2: Complete
- [x] Adaptive truncation (`maxChars` in ICL prompt)
- [x] `MemoryManagementTools.updateMemory()`
- [x] `MemoryManagementTools.deleteMemory()`
- [x] Source filtering in `/api/memory/experiences`
- [x] `requiredConcepts` filter in experiences

### ✅ Phase 4: Complete
- [x] `source` filter in `/api/search`

### ⏳ Phase 3: Deferred
- [ ] `UserProfile` entity
- [ ] Preference history tracking
- [ ] Memory conflict detection

---

## 7. SDK Usage Examples

### Creating Observation with V14 Fields
```java
ObservationRequest request = ObservationRequest.builder()
    .sessionId(sessionId)
    .projectPath(projectPath)
    .source("tool_result")
    .extractedData(Map.of(
        "price_range", "3000",
        "brands", List.of("sony", "bose")
    ))
    .build();
client.recordObservation(request);
```

### Updating Observation
```java
client.updateObservation(observationId, ObservationUpdate.builder()
    .title("Updated Title")
    .source("manual")
    .concepts(List.of("important", "verified"))
    .extractedData(Map.of("key", "value"))
    .build());
```

### Filtering Experiences
```java
List<Experience> experiences = client.retrieveExperiences(
    ExperienceRequest.builder()
        .task("fix login bug")
        .project("/my-project")
        .source("llm_inference")
        .requiredConcepts(List.of("verified"))
        .count(4)
        .build()
);
```

### Adaptive Truncation
```java
ICLPromptResult result = client.buildICLPrompt(
    ICLPromptRequest.builder()
        .task("fix bug")
        .project("/project")
        .maxChars(2000)  // Truncate if exceeds 2000 chars
        .build()
);
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
- `implementation-progress.md` - Implementation tracking (temporary)
- [Spring AI Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- [Mem0: Universal memory layer for AI Agents](https://github.com/mem0ai/mem0)
- [LangMem: Managing User Profiles](https://langchain-ai.github.io/langmem/guides/manage_user_profile/)

---

## 10. Changelog

- **2026-03-21**: Updated to honest implementation record. Phases 1, 2, 4 complete. Phase 3 deferred.
- **2026-03-20**: Initial research document with pain point analysis and proposed solutions.
