# Phase 1 Implementation Progress

**Date**: 2026-03-21
**Based on**: `docs/drafts/sdk-improvement-research.md`

## Tasks

### Backend (cortex-mem-java)

- [x] 1. Add `source` (String) field to `ObservationEntity.java`
- [x] 2. Add `extractedData` (Map<String, Object>) JSONB field to `ObservationEntity.java`
- [x] 3. Create Flyway migration: V14__observation_source_and_extracted_data.sql
- [x] 4. Update `ObservationRepository` - allow filtering by source (`findBySource`)
- [x] 5. Implement `PATCH /api/memory/observations/{id}` endpoint
- [x] 6. Update IngestionController to accept source and extractedData

### Client SDK (cortex-mem-spring-integration)

- [x] 7. Update `ObservationRequest` DTO with source and extractedData
- [x] 8. Update `ObservationUpdate` DTO
- [x] 9. Add `CortexMemClient.updateObservation()` method
- [x] 10. Add `CortexMemClient.deleteObservation()` method

### Demo (examples/cortex-mem-demo)

- [ ] 11. Test new fields end-to-end

### Testing

- [ ] 12. Run regression tests: `scripts/run-all-e2e.sh`
