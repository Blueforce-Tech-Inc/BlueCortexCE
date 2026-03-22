# Phase 3 Architecture Walkthrough: Scenarios & Decision Log

**Date**: 2026-03-21 → 2026-03-22
**Purpose**: Test the generalization capability of the extraction architecture through diverse scenarios.
**Full design reference**: [phase-3-design.md](phase-3-design.md)

---

## Part A: Scenario Walkthroughs

Each scenario tests whether the generic extraction architecture can handle a specific use case.

---

### Scenario 1: User Preference Extraction (Primary Scenario)

**Situation**: User says "我不喜欢苹果手机", "我更喜欢小米", "预算3000-4000" in one conversation.

**Challenge**: Single-object schema loses multiple preferences.

**Walkthrough**:
```
Template config → Find candidates → Build prompt → LLM call → Parse → Store
```

**Finding**: Original schema defined single `{category, value, confidence}` object. LLM could only return one result.

**Resolution**: Array-wrapped schema: `{preferences: [{...}, {...}]}`.
**Design location**: Section 2.2 of [phase-3-design.md](phase-3-design.md)

**Status**: ✅ Resolved

---

### Scenario 2: Family Assistant with Multiple Members

**Situation**: Family "Zhang" has 4 members. Dad prefers Chinese food, Mom is allergic to seafood, Son likes games, Daughter allergic to peanuts.

**Challenge**: Observation says "Mom can't eat shrimp" — who is "Mom"? The observation has no user attribution.

**Key Insight**: The memory system doesn't need to understand WHO "妈妈" is. It just stores the extracted data. The external application interprets the `person` field.

**Walkthrough**:
```
LLM input: ["妈妈对虾过敏", "爸爸爱吃辣"]
LLM output: {items: [{person: "妈妈", allergens: ["虾"]}, {person: "爸爸", preferences: {food: "辣"}}]}
Storage: content_session_id = owner's session, extractedData contains person field
External app: interprets "妈妈" → maps to family member record
```

**Resolution**: Entity naming via `person` field in schema — no cascading extraction needed. The memory system is a generic storage/retrieval layer. External systems handle semantic interpretation.

**Status**: ✅ Resolved — works within current architecture (person field in array schema)

---

### Scenario 3: User with Multiple Sessions (Work vs Personal)

**Situation**: User "chen" has work sessions ("I prefer minimalist design") and personal sessions ("I hate mornings"). Should preferences be session-scoped or user-scoped?

**Challenge**: Some preferences should be aggregated across all sessions (global), while others should stay within session groups (work-specific).

**Walkthrough against current design**:
```
Current design: sessionIdPattern determines where extraction is stored
  - null → inherit source session
  - "pref:{project}:{userId}" → user-global

Missing: session group scoping (e.g., "pref:{project}:{userId}:work")
```

**Gap**: The `sessionIdPattern` doesn't support session group variables like `{sessionGroup}`. No concept of session grouping exists in the data model.

**Resolution needed**: 
1. Add `sessionGroup` field to `SessionEntity` (e.g., "work", "personal")
2. Add `{sessionGroup}` variable to `sessionIdPattern`
3. Template config: `session-id-pattern: "pref:{project}:{userId}:{sessionGroup}"`

**Status**: ✅ Resolved — `projectPath` serves as natural scope boundary. Application uses different projectPath for work vs personal. No `sessionGroup` field needed.

---

### Scenario 4: Temporal Preference Evolution

**Situation**: 
- 2025-01: "I love Sony headphones"
- 2025-06: "Actually, Bose is better"
- 2026-01: "AirPods are more convenient"

**Challenge**: Track how preferences change over time, not just current value.

**Walkthrough against current design**:
```
Current design: mergeExtractedData() overwrites old value with new
  → "Bose" overwrites "Sony" → "AirPods" overwrites "Bose"
  → History is lost!
```

**Finding**: The current `mergeExtractedData()` uses composite key (`category:value`) for dedup. When value changes, the old entry is removed (overwritten), not appended.

**Resolution**: 
- `trackEvolution: true` in template already exists
- Need to store evolution history in `extractedData` alongside current value:
```json
{
  "preferences": [{"category": "headphones", "value": "AirPods", ...}],
  "evolution": [
    {"value": "Sony", "from": "2025-01"},
    {"value": "Bose", "from": "2025-06"},
    {"value": "AirPods", "from": "2026-01"}
  ]
}
```

**Status**: ✅ Resolved — each extraction includes prior result as context, LLM produces complete current state. Old extractions preserved as history. Timestamp distinguishes current vs historical. No merge logic needed.

---

### Scenario 5: Conflict Detection

**Situation**: 
- Session 1: "I prefer quiet restaurants"
- Session 2: "I don't mind loud bars for drinks"

**Challenge**: Are these conflicts? Not necessarily — context matters (restaurant vs bar).

**Walkthrough against current design**:
```
Current design: mergeExtractedData() does exact-key comparison
  - category: "restaurant preference" vs category: "bar preference" 
  - Different keys → no conflict → both stored

Problem: Semantic similarity isn't detected
  "quiet restaurant" and "loud bar" aren't exact-key conflicts but could be semantically related
```

**Gap**: The current merge logic uses exact string comparison on composite keys. It cannot detect semantic conflicts.

**Resolution**: This is the `ConflictDetector` class from Section 3 — LLM-based semantic comparison. Already designed but deferred to Phase 3.3.

**Status**: ⏳ Deferred to Phase 3.3. Design exists in Section 3.2 of [phase-3-design.md](phase-3-design.md).

---

### Scenario 6: Extraction Trigger Timing

**Challenge**: When should extraction run?

**Options analyzed**:
```
A. After each observation → Too expensive, many LLM calls
B. Scheduled batch (daily 2am) → Efficient, might delay extraction  
C. After session end → Balanced, but session end ≠ conversation end
D. On-demand → Manual, forgettable
```

**Walkthrough against current design**:
```
Current design: Two triggers
  1. Last step of deepRefineProjectMemories() (Section 15.5)
  2. Scheduled daily at 2am (Section 9.1)

Missing: Keyword-triggered extraction (on-demand when trigger keywords appear)
```

**Gap**: No keyword-based trigger. The `triggerKeywords` field in template exists but isn't used for real-time triggering.

**Status**: ⏳ Keyword trigger deferred. Current design uses scheduled + deepRefine integration, which is sufficient for Phase 3.1.

---

### Scenario 7: Privacy and Access Control

**Situation**: Family members have different access levels. Dad can see financial preferences, teenager cannot.

**Challenge**: Extraction results need access control.

**Walkthrough against current design**:
```
Current design: Observations are project-scoped, no access control layer
  - All users in same project can query all observations
  - userId isolation only prevents cross-user data mixing
  - No fine-grained access control within a user's data
```

**Gap**: No access control concept in the architecture. This is beyond Phase 3.1 scope.

**Status**: ⏳ Deferred — out of scope for Phase 3.1. Can be added via template `accessLevel` field in future.

---

### Scenario 8: Zero-Shot Bootstrap

**Situation**: First conversation with new user. No history, no preferences known.

**Challenge**: How to bootstrap preference extraction with no prior data?

**Walkthrough against current design**:
```
Current design: Extraction runs on existing observations
  - If no observations match sourceFilter → candidates is empty → extraction skips
  
This is correct behavior for zero-shot: nothing to extract yet.
```

**Resolution**: The current design handles zero-shot gracefully — no extraction until sufficient data exists. The `initialRunMaxCandidates` cap (Section 19.3) prevents over-extraction on first run.

**Status**: ✅ Already supported — extraction skips when no candidates exist.

---

## Part B: Decision Log

### Decision 1: User Identification

| Option | Decision |
|--------|----------|
| A. `user_id` in SessionEntity | ✅ Chosen |
| B. Encode in project path | Rejected (fragile) |
| C. Infer from history | Rejected (unreliable) |

**Documented in**: Section 20.2 of [phase-3-design.md](phase-3-design.md)

### Decision 2: Special Session ID

**Decision**: `sessionIdPattern` in template config — generic system interprets pattern, doesn't need to understand semantics.

**Documented in**: Section 20.3 of [phase-3-design.md](phase-3-design.md)

### Decision 3: Schema Design

**Decision**: Array-wrapped schema for multi-item extraction (preference, allergy list, etc.)

**Documented in**: Section 2.2 of [phase-3-design.md](phase-3-design.md)

### Decision 4: Incremental Merge

**Decision**: `mergeExtractedData()` with composite key dedup + sentiment-aware overwrite.

**Documented in**: Section 2.3 of [phase-3-design.md](phase-3-design.md)

### Decision 5: Usage Modes

| Mode | userId | Isolation |
|------|--------|-----------|
| Hook (wrapper.js) | null | Project-level |
| SDK (CortexMemClient) | Set by app | User-level |

**Documented in**: Section 20.9 of [phase-3-design.md](phase-3-design.md)

### Decision 6: Cost Control

**Decision**: Scheduled extraction (not real-time), incremental processing, batch size caps.

**Documented in**: Section 23 of [phase-3-design.md](phase-3-design.md)

---

## Part C: Generalization Assessment

| Scenario | Can Architecture Handle? | Gap | Priority |
|----------|-------------------------|-----|----------|
| 1. User Preference | ✅ Yes | None | — |
| 2. Family Assistant | ✅ Yes | Person field in schema, external interpretation | — |
| 3. Multi-session Scope | ✅ Yes | projectPath as scope boundary | — |
| 4. Temporal Evolution | ✅ Yes | Prior result as LLM context, no merge needed | — |
| 5. Conflict Detection | ⏳ Deferred | LLM-based detection | Phase 3.3 |
| 6. Trigger Timing | ⏳ Deferred | Keyword trigger | Low |
| 7. Privacy Control | ⏳ Deferred | Access control layer | Phase 3.4+ |
| 8. Zero-shot Bootstrap | ✅ Yes | None | — |

**Architecture generalization: STRONG. 6/8 fully supported, 0/8 need extension, 2/8 deferred for later phases.**

The architecture's prompt-driven, config-driven design correctly separates "what to extract" (template) from "how to extract" (service). All core scenarios work within the current framework. Evolution tracking uses prior extraction as LLM context (no merge logic needed). External systems interpret extracted data semantics.
