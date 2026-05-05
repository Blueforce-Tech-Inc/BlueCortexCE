## Quick Reference (TL;DR)

**What**: Generic, prompt-driven structured information extraction from observations.
**How**: YAML templates define what to extract + output schema. Code is generic.
**Storage**: Results stored as `ObservationEntity` with `type="extracted_{template}"` + `extractedData` JSONB.
**When**: Last step of `deepRefineProjectMemories()` (non-blocking) or scheduled daily.
**Prerequisites**: 4 new methods (findBySourceIn, findNewObservations, findByTypeGlobal, chatCompletionStructured).
**Key insight**: `BeanOutputConverter<T>` needs Java `Class<T>`, not JSON Schema string. Use `templateClass` field.

```
┌─────────────────────────────────────────────────────────┐
│ Extraction Pipeline (per template per project)          │
├─────────────────────────────────────────────────────────┤
│ 1. Get incremental candidates (since last extraction)   │
│ 2. Chunk by token count (respect context window)        │
│ 3. Build prompt (template.prompt + candidate data)      │
│ 4. Call LLM via BeanOutputConverter<T> (schema-enforced)│
│ 5. Validate result → store as ObservationEntity         │
│ 6. Update extraction state (transactional)              │
│ 7. On failure → DLQ (type=extraction_failed)            │
└─────────────────────────────────────────────────────────┘
```

---
