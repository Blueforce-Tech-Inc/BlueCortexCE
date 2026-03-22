# Phase 3 Architecture Walkthrough: Decision Log

**Date**: 2026-03-21 → 2026-03-22
**Purpose**: Record the decision process that led to the final Phase 3.1 design.
**Full design reference**: [phase-3-design.md](phase-3-design.md)

---

## 1. Core Question: Can Generic Extraction Actually Work?

**Method**: Pseudocode walkthrough with realistic scenario.

**Scenario**: User says three things in a conversation:
- "我不喜欢苹果手机" 
- "我更喜欢小米"
- "预算3000-4000"

### Walkthrough Flow

```
Template config → Find candidates → Build prompt → LLM call → Parse → Store → Query
```

### Key Finding: Single-Object Schema Loses Data

The original schema defined a single `{category, value, confidence}` object. With 3 preferences in one conversation, the LLM could only return one result — losing the other two.

**Resolution**: Array-wrapped schema with `{preferences: [{...}, {...}]}`.
**Documented in**: Section 2.2 of [phase-3-design.md](phase-3-design.md#22-configuration-model-yaml)

---

## 2. Multi-User Problem: Who Does This Preference Belong To?

**Finding**: The data model had NO user identification. `project_path` was the only isolation boundary.

**Scenario**: Alice and Bob both in `/my-project`. Extraction would mix their data.

### Decision Process

| Option | Pros | Cons |
|--------|------|------|
| A. Add `user_id` to SessionEntity | Clean, explicit | Requires migration |
| B. Encode user in project path | No migration | Fragile convention |
| C. Infer from session history | No schema change | Unreliable |

**Resolution**: Option A — `user_id` field in `SessionEntity` + Flyway V15.
**Documented in**: Section 20.2 of [phase-3-design.md](phase-3-design.md)

---

## 3. Special Session ID: How Does Generic System Know?

**Finding**: Extraction results for user preferences need a special session ID (e.g., `pref:/project:alice`), but the generic extraction service doesn't know about specific use cases.

**Resolution**: `sessionIdPattern` field in template configuration:
```yaml
session-id-pattern: "pref:{project}:{userId}"  # Special session
session-id-pattern: null                         # Inherit source session
```

The generic system interprets the pattern via variable substitution — it doesn't need to understand what `pref:` means.
**Documented in**: Section 20.3 of [phase-3-design.md](phase-3-design.md)

---

## 4. Incremental Extraction: Duplicate Results Problem

**Finding**: Re-running extraction on overlapping observations creates duplicate `extracted_*` observations.

**Resolution**: `mergeExtractedData()` method with composite key deduplication + sentiment-aware overwrite.
**Documented in**: Section 2.3 of [phase-3-design.md](phase-3-design.md)

---

## 5. SDK API Walkthrough: Does userId Flow Through All APIs?

**Finding**: Only 3 of 10 SDK APIs need userId support:
- `POST /api/session/start` — pass userId (already designed)
- `POST /api/memory/experiences` — filter by userId (missing!)
- `POST /api/memory/icl-prompt` — filter by userId (missing!)

**Resolution**: Add `userId` field to `ExperienceRequest` and `ICLPromptRequest` DTOs.
**Documented in**: Section 22 of [phase-3-design.md](phase-3-design.md)

---

## 6. Two Usage Modes

| Mode | Caller | userId | Behavior |
|------|--------|--------|----------|
| Hook mode | wrapper.js (Claude Code/Cursor) | null | Single-user, project isolation |
| SDK mode | CortexMemClient (web app) | Set by app | Multi-user, per-user isolation |

Both call the same API. The design is backward compatible.
**Documented in**: Section 20.9 of [phase-3-design.md](phase-3-design.md)

---

## 7. Final Verification

**All 8 walkthrough issues resolved. All 10 SDK API issues resolved.**

| Question | Answer |
|----------|--------|
| Can generic extraction work? | ✅ Yes — prompt-driven, config-driven |
| Can it handle multiple preferences? | ✅ Yes — array-wrapped schema |
| Can it support multi-user? | ✅ Yes — user_id + session grouping |
| Can it avoid duplicates? | ✅ Yes — merge with dedup |
| Can it work with existing hook mode? | ✅ Yes — userId is optional |
| Is token cost acceptable? | ✅ Yes — extraction is cheap, refinement dominates |

**Phase 3.1 design is COMPLETE.**
