# SDK Improvement Research - Implementation Complete

**Date**: 2026-03-21
**Based on**: `docs/drafts/sdk-improvement-research.md`

## Status: ✅ ALL PHASES COMPLETE

---

## Phase 1: ✅ COMPLETED (2026-03-21)

### Backend Changes
- [x] Add `source` (String) field to `ObservationEntity.java`
- [x] Add `extractedData` (Map<String, Object>) JSONB field to `ObservationEntity.java`
- [x] Create Flyway migration: `V14__observation_source_and_extracted_data.sql`
- [x] Update `ObservationRepository` - `findBySource()` method
- [x] Implement `PATCH /api/memory/observations/{id}` endpoint
- [x] Implement `DELETE /api/memory/observations/{id}` endpoint
- [x] Update IngestionController to accept source and extractedData

### SDK Changes
- [x] Update `ObservationRequest` DTO with source and extractedData
- [x] Create `ObservationUpdate` DTO
- [x] Add `CortexMemClient.updateObservation()` method
- [x] Add `CortexMemClient.deleteObservation()` method

### Testing
- [x] End-to-end curl test passed
- [x] Regression tests: 21/22 (later fixed to 32/32)

**Commits**: `ba393f0`, `6b2b352`

---

## Phase 2: ✅ COMPLETED (2026-03-21)

### Adaptive Truncation
- [x] Add `maxChars` parameter to `ICLPromptRequest` (default 4000)
- [x] Update `ExpRagService.buildICLPrompt()` to accept maxChars and truncate
- [x] Update `MemoryController.icl-prompt` endpoint to accept maxChars
- [x] Add `maxIclChars` builder method to `CortexMemoryAdvisor`

### Memory Management Tools
- [x] Add `updateMemory()` tool to `CortexMemoryTools`
- [x] Add `deleteMemory()` tool to `CortexMemoryTools`

### Source-based Filtering (Experience API)
- [x] Add `source` and `requiredConcepts` to `ExperienceRequest`
- [x] Update `CortexMemClientImpl.retrieveExperiences()` to pass filters
- [x] Add `retrieveExperiences()` overload in `ExpRagService` with filters
- [x] Update `MemoryController.experiences` endpoint to accept filters

**Commit**: `3baba41`

---

## Phase 3: ⏳ DEFERRED (Future)

These are marked as "Future Considerations" in the research doc - only implement when needed:
- [ ] `UserProfile` entity - only if profile management is required
- [ ] Preference history tracking
- [ ] Memory conflict detection

---

## Phase 4: ✅ COMPLETED (2026-03-21)

### Source-based Filtering (Search API)
- [x] Add `source` parameter to `GET /api/search` endpoint
- [x] Add source filter to `SearchService.filterSearch()`
- [x] Update `SearchRequest` record with source field
- [x] Update `TimelineService` and `ClaudeMemMcpTools` callers

### Test 10 Fix
- [x] Replace `psql` with API calls for database state verification
- [x] Fixes 'psql: command not found' issue in CI/portable environments

**Commit**: `372a70d`

---

## Verification Results

```bash
# Source filtering in search API
GET /api/search?project=/tmp/test&source=manual_test
→ Returns only observations with source=manual_test

# PATCH update observation
curl -X PATCH /api/memory/observations/{id} -d '{"source":"patched"}'
→ {"status":"updated"}

# Adaptive truncation
POST /api/memory/icl-prompt {"task":"...", "maxChars": 2000}
→ Returns truncated prompt if exceeds limit

# Regression tests
✅ All 32 tests passed!
```

## All Commits

1. `ba393f0` - feat: Phase 1 - source and extractedData fields
2. `6b2b352` - fix: restore findByType method
3. `3baba41` - feat: Phase 2 - adaptive truncation, memory tools, source filtering
4. `372a70d` - feat: Phase 4 - source-based filtering in search API + Test 10 fix
