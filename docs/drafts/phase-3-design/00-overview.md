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
