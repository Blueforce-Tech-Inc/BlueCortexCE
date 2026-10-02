## Quick Reference (TL;DR)

**What**: Generic, prompt-driven structured information extraction from observations.
**How**: YAML templates define what to extract + output schema. Code is generic.
**Storage**: Results stored as `ObservationEntity` with `type="extracted_{template}"` + `extractedData` JSONB.
**When**: Event-driven, not scheduled. Two call sites actually run: `POST /api/extraction/run`, and the re-extraction on `PATCH /api/session/{id}/user`. The previously documented triggers — the last step of `deepRefineProjectMemories()` and a daily 2am job — **do not exist**: that method has no callers anywhere in the codebase, and there is no cron schedule in the backend at all. See [23.md §23.7](23.md).
**Implementation status**: All 10 Phase 3.1 prerequisites are implemented. See [15.md](15.md) for the bootstrap checklist and [21.md](21.md) for the verified implementation inventory.
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
│ 7. On failure → DLQ (type=dlq_{template}, src=dlq)      │
└─────────────────────────────────────────────────────────┘
```

---
