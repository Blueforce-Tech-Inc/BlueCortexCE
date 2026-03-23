# SDK Improvement Implementation Progress

**Date**: 2026-03-23
**Note**: This is a **temporary tracking document**. For authoritative implementation records, see `sdk-improvement-research.md`.

## Status: ✅ ALL PHASES COMPLETE (Phase 1-4 + Phase 3 Steps 1-14)

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
- [x] Regression tests: 21/22 (later fixed to 43/43)

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

## Phase 3: ✅ COMPLETED (2026-03-22)

### Step 1-4: Database & Service Layer
- [x] V15 Migration + SessionEntity.userId
- [x] SessionRepository user query methods
- [x] ObservationRepository 5 new query methods
- [x] LlmService.chatCompletionStructured()

**Commit**: `cca84e0`

### Step 5-8: Core Implementation
- [x] Session API — userId support (SessionController.startSession + PATCH)
- [x] StructuredExtractionService + ExtractionConfig
- [x] DeepRefine integration (MemoryRefineService)
- [x] Extraction Query API (ExtractionController: latest/history/run)

**Commit**: `b337e60`

### Step 9-11: Configuration & Testing
- [x] YAML configuration + EXTRACTION_ENABLED flag
- [x] SDK Client update (userId + extraction query methods)
- [x] E2E acceptance test (phase3-acceptance-test.sh: 15 test functions)

**Commits**: `ca8d719`, `7173d7a`, `53d75c8`, `1e51737`, `b340986`

### Step 12-14: EXTRACTION_ENABLED Coverage + Input Validation
- [x] EXTRACTION_ENABLED=true E2E test coverage (5 compilation fixes + SDK compatibility)
- [x] Demo V15 extraction endpoints integration test
- [x] Extraction API input validation + Test 15 (empty projectPath → 400, negative limit → clamped)

**Commits**: `05c122e`, `f09f692`, `cccb92a`

### Phase 3 Features
- Multi-user session isolation (userId-based)
- Structured data extraction (configurable templates)
- Extraction query API (latest/history/run)
- Backward compatible (works without userId)

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

## Phase 5: ✅ COMPLETED (2026-03-23)

### Section 24.6: Append-only Extraction (Solution D)
- [x] `extractAppendOnly()` — LLM only outputs add/remove/keep_hint
- [x] `mergeAppendOnly()` — Service merges with full prior from DB
- [x] `buildItemKey()` — Deduplication by category+value
- [x] Modified `extractByTemplate()` to use append-only when prior exists
- [x] Token cost: ~2000 (append-only) vs ~7000 (full prior)

**Commit**: `8880a3a`

### Code Review Improvements (2026-03-23)
- [x] Type-safe extractedData handling in PATCH handler
- [x] Fix double-save in MemoryRefineService
- [x] Improve JSON fence parsing
- [x] Fix saveAll on deleted entities in refineObservations
- [x] Hoist resolveUserId out of template loop (N+1 fix)
- [x] Add null-check for extractionService in MemoryRefineService
- [x] Fix N+1 query in groupByUser
- [x] Strict JSON parse error handling
- [x] Remove dead code buildItemKey (old version)

**Commits**: `7df565b`, `7cbaacc`, `af4c310`, `9398862`, `f4dd148`

---

## Verification Results

```bash
# Regression tests
bash scripts/regression-test.sh
✅ 43/43 tests passed

# Phase 3 acceptance test (EXTRACTION_ENABLED=true)
EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh
✅ 22/22 passed (ALL EXTRACTION TESTS PASSING!)

# Phase 3 acceptance test (EXTRACTION_ENABLED=false)
bash scripts/phase3-acceptance-test.sh
✅ 18/18 passed, 4 skipped

# Demo V14 test
bash scripts/demo-v14-test.sh
✅ 4/4 tests passed

# SDK build
cd cortex-mem-spring-integration && mvn clean install -DskipTests
✅ Build successful

# Demo compile
cd examples/cortex-mem-demo && mvn clean compile -Plocal
✅ Compilation successful
```

## All Commits

1. `ba393f0` - feat: Phase 1 - source and extractedData fields
2. `6b2b352` - fix: restore findByType method
3. `3baba41` - feat: Phase 2 - adaptive truncation, memory tools, source filtering
4. `372a70d` - feat: Phase 4 - source-based filtering in search API + Test 10 fix
5. `cca84e0` - feat: Phase 3 Steps 1-5 - V15 migration + userId support
6. `b337e60` - feat: Phase 3 Steps 6-8 - extraction service + query API
7. `ca8d719` - feat: Phase 3 Step 11 - E2E acceptance test
8. `7173d7a` - feat: Phase 3 Step 10 - SDK client update
9. `53d75c8` - fix: Phase 3 extraction FK constraint + configurable key-fields
10. `1e51737` - docs: SDK + Demo README Phase 3 features
11. `b340986` - test: Phase 3 E2E re-extraction tests
12. `05c122e` - fix: resolve 5 compilation errors + SDK compatibility improvements
13. `f09f692` - feat: Demo V15 extraction endpoints + EXTRACTION_ENABLED test coverage
14. `cccb92a` - feat: Extraction API input validation + Test 15
