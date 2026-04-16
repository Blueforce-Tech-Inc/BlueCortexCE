# BlueCortexCE Upstream Sync Plan

**Date**: 2026-04-16
**Status**: Draft v6 (Iteration 5 - Final)
**Reference**: claude-mem-java commits `7ba455cb4b8ef2a30e659681261dae0c7cfa1db5` onwards

---

## Executive Summary

This plan syncs BlueCortexCE with upstream improvements from claude-mem-java (located at `/Users/yangjiefeng/Documents/claude-mem`).

| Feature | Description | Migration | Risk |
|---------|-------------|-----------|------|
| Platform Source | Multi-platform tracking (claude/codex) | V18 | Low |
| Observation Feedback | Thompson Sampling foundation | V17 | Low |
| Session Age Guard | 4-hour wall-clock limit | Code | Low |
| Semantic Endpoint | Per-prompt context injection | Code | Low |

**Execution Order**: V18 → Entities/Repos → V17 → Services → Controllers → WebUI

---

## Background

### Repository Relationships

```
claude-mem (upstream)
    └── claude-mem-java (fork, commits 7ba455cb4... onwards)
            └── Blueforce-Tech-Inc/claude-mem (WebUI submodule source)
                    └── BlueCortexCE webui/ (Git submodule)
```

BlueCortexCE is a fork of claude-mem-java. We need to sync:
1. Backend improvements from claude-mem-java (V9, V10 features)
2. WebUI improvements from Blueforce-Tech-Inc/claude-mem

---

## Feature 1: Platform Source Support (V18)

### 1.1 Migration: V18__add_platform_source.sql

**Path**: `backend/src/main/resources/db/migration/V18__add_platform_source.sql`

```sql
-- V18: Add platform_source column for multi-platform tracking
-- Supports claude/codex filtering in WebUI

ALTER TABLE mem_sessions ADD COLUMN IF NOT EXISTS platform_source VARCHAR(50) DEFAULT 'claude';
CREATE INDEX IF NOT EXISTS idx_sessions_platform_source ON mem_sessions(platform_source);

ALTER TABLE mem_observations ADD COLUMN IF NOT EXISTS platform_source VARCHAR(50) DEFAULT 'claude';
CREATE INDEX IF NOT EXISTS idx_observations_platform_source ON mem_observations(platform_source);

ALTER TABLE mem_summaries ADD COLUMN IF NOT EXISTS platform_source VARCHAR(50) DEFAULT 'claude';
CREATE INDEX IF NOT EXISTS idx_summaries_platform_source ON mem_summaries(platform_source);

ALTER TABLE mem_user_prompts ADD COLUMN IF NOT EXISTS platform_source VARCHAR(50) DEFAULT 'claude';
CREATE INDEX IF NOT EXISTS idx_user_prompts_platform_source ON mem_user_prompts(platform_source);

COMMENT ON COLUMN mem_sessions.platform_source IS 'Source platform (claude, codex, etc.)';
```

### 1.2 Entity Updates

**SessionEntity.java** - Add after `status` field:
```java
@Column(name = "platform_source")
@JsonProperty("platform_source")
private String platformSource = "claude";
```

Add getter/setter after `setStatus()`:
```java
public String getPlatformSource() { return platformSource; }
public void setPlatformSource(String platformSource) { this.platformSource = platformSource; }
```

**ObservationEntity.java**, **SummaryEntity.java**, **UserPromptEntity.java** - Same pattern.

### 1.3 Repository Updates

**SessionRepository.java** - Add methods:
```java
@Query("SELECT DISTINCT s.platformSource FROM SessionEntity s WHERE s.platformSource IS NOT NULL ORDER BY s.platformSource")
List<String> findAllPlatformSources();

@Query("SELECT s.platformSource, s.projectPath FROM SessionEntity s WHERE s.platformSource IS NOT NULL GROUP BY s.platformSource, s.projectPath ORDER BY s.platformSource, s.projectPath")
List<Object[]> findProjectsByPlatformSource();
```

**ObservationRepository.java**, **SummaryRepository.java**, **UserPromptRepository.java** - Update `findAllPaged` to accept `String platformSource`.

### 1.4 Controller Updates

**ViewerController.java**:
- Add `@RequestParam(required = false) String platformSource` to `/observations`, `/summaries`, `/prompts`
- Update `/projects` to return `{"projects": [...], "sources": [...], "projectsBySource": {...}}`

**StreamController.java**:
- Update SSE `initial_load` event to include `sources` and `projectsBySource`

---

## Feature 2: Observation Feedback (V17)

### 2.1 Migration: V17__observation_feedback.sql

**Path**: `backend/src/main/resources/db/migration/V17__observation_feedback.sql`

```sql
CREATE TABLE IF NOT EXISTS observation_feedback (
    id BIGSERIAL PRIMARY KEY,
    observation_id UUID NOT NULL,
    signal_type VARCHAR(50) NOT NULL,
    session_db_id UUID,
    created_at_epoch BIGINT NOT NULL,
    metadata TEXT,
    CONSTRAINT fk_feedback_observation
        FOREIGN KEY (observation_id) REFERENCES mem_observations(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_feedback_observation ON observation_feedback(observation_id);
CREATE INDEX IF NOT EXISTS idx_feedback_signal ON observation_feedback(signal_type);
CREATE INDEX IF NOT EXISTS idx_feedback_session ON observation_feedback(session_db_id);

ALTER TABLE mem_observations ADD COLUMN IF NOT EXISTS generated_by_model VARCHAR(100);
ALTER TABLE mem_observations ADD COLUMN IF NOT EXISTS relevance_count INTEGER DEFAULT 0;
```

### 2.2 New Files

**ObservationFeedbackEntity.java** and **ObservationFeedbackRepository.java** - Create based on upstream templates.

### 2.3 ObservationEntity Updates

Add fields:
```java
@Column(name = "generated_by_model")
@JsonProperty("generated_by_model")
private String generatedByModel;

@Column(name = "relevance_count")
@JsonProperty("relevance_count")
private Integer relevanceCount = 0;
```

---

## Feature 3: Session Age Guard

### 3.1 AgentService.java Changes

Add constant:
```java
private static final long MAX_SESSION_AGE_MS = 4 * 60 * 60 * 1000;
```

Add methods:
```java
private boolean isSessionTooOld(SessionEntity session) {
    if (session == null || session.getStartedAtEpoch() == null) return false;
    return Instant.now().toEpochMilli() - session.getStartedAtEpoch() > MAX_SESSION_AGE_MS;
}

private void markSessionExpired(SessionEntity session, String reason) {
    if (session == null) return;
    session.setStatus("expired");
    sessionRepository.save(session);
}
```

Add guards in `processToolUseAsync()` and `handleSummarize()`.

---

## Feature 4: Semantic Endpoint

### 4.1 ContextController.java

Add `POST /api/context/semantic` endpoint using existing `EmbeddingService` and `SearchService`.

---

## Implementation Checklist

- [ ] Create V18__add_platform_source.sql
- [ ] Update SessionEntity, ObservationEntity, SummaryEntity, UserPromptEntity
- [ ] Update SessionRepository, ObservationRepository, SummaryRepository, UserPromptRepository
- [ ] Create V17__observation_feedback.sql
- [ ] Create ObservationFeedbackEntity.java
- [ ] Create ObservationFeedbackRepository.java
- [ ] Update ObservationEntity with new fields
- [ ] Update AgentService with session age guard
- [ ] Update ViewerController with platformSource params
- [ ] Update StreamController SSE events
- [ ] Add semantic endpoint to ContextController
- [ ] Compile: `./mvnw clean compile`
- [ ] Test: `./scripts/regression-test.sh`
- [ ] Update WebUI submodule

---

## Testing

```bash
# API tests
curl "http://localhost:37777/api/observations?platformSource=claude"
curl "http://localhost:37777/api/projects"
curl -X POST http://localhost:37777/api/context/semantic \
  -H "Content-Type: application/json" \
  -d '{"q": "What was implemented for authentication?", "limit": 3}'
```

---

## Rollback

```bash
./mvnw flyway:undo -Dflyway.targetVersion=V16
```

---

## Document History

| Version | Date | Changes |
|---------|------|---------|
| v1-v5 | 2026-04-16 | Iterations 1-5 |
| v6 | 2026-04-16 | Final version with simplified structure |

---

## Implementation Summary (2026-04-16)

All planned features have been **fully implemented** and **compiled successfully**.

### Completed

| Feature | Status | Files |
|---------|--------|-------|
| V18: Platform Source | ✅ Done | `V18__add_platform_source.sql` (NEW) |
| V17: Observation Feedback | ✅ Done | `V17__observation_feedback.sql` (NEW) |
| Session Age Guard | ✅ Done | `AgentService.java` |
| Semantic Endpoint | ✅ Done | `ContextController.java` |
| API Updates | ✅ Done | `ViewerController.java`, `StreamController.java` |
| WebUI Submodule | ✅ Done | Updated to `origin/main` |

### New Files
- `backend/src/main/resources/db/migration/V18__add_platform_source.sql`
- `backend/src/main/resources/db/migration/V17__observation_feedback.sql`
- `backend/src/main/java/.../entity/ObservationFeedbackEntity.java`
- `backend/src/main/java/.../repository/ObservationFeedbackRepository.java`

### Build Status
```
./mvnw clean compile ✓
```

### Pending
- [ ] Run regression tests: `./scripts/regression-test.sh`
- [ ] Deploy with `docker compose -f docker-compose.prod.yml up -d`
- [ ] Commit changes
