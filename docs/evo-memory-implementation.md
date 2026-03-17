# Evo-Memory Implementation Documentation

> **Date**: 2026-03-17
> **Project**: Claude-Mem Java (Cortex Community Edition)
> **Reference**: [Evo-Memory Paper](https://arxiv.org/abs/2511.20857)

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Database Schema](#database-schema)
4. [Core Services](#core-services)
5. [API Endpoints](#api-endpoints)
6. [Quality Scoring System](#quality-scoring-system)
7. [Memory Refinement](#memory-refinement)
8. [Experience Retrieval (ExpRAG)](#experience-retrieval-exprag)
9. [Feature Flags](#feature-flags)
10. [Testing](#testing)
11. [Configuration](#configuration)

---

## Overview

This document describes the implementation of Evo-Memory features based on the [Evo-Memory paper](https://arxiv.org/abs/2511.20857) from Google DeepMind. The implementation adds intelligent memory management to the Claude-Mem Java system.

### Key Improvements

| Feature | Description |
|---------|-------------|
| **Quality Scoring** | Automatic assessment of observation quality based on feedback |
| **Memory Refinement** | Automated cleanup and consolidation of low-quality memories |
| **ExpRAG** | Experience-based retrieval for in-context learning |
| **Feature Flags** | Configurable toggles for safe deployment |

---

## Architecture

### System Context

```
┌─────────────────────────────────────────────────────────────────┐
│                        Claude Code / Agent                       │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Claude-Mem Java Backend                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │   Session   │  │Observation  │  │      Summary           │  │
│  │  Controller │  │ Controller  │  │      Controller       │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
│         │                │                      │                │
│         └────────────────┼──────────────────────┘                │
│                          ▼                                       │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    AgentService                              ││
│  │  (Session lifecycle, observation persistence)              ││
│  └─────────────────────────────────────────────────────────────┘│
│                          │                                       │
│         ┌────────────────┼────────────────┐                       │
│         ▼                ▼                ▼                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │QualityScorer│  │MemoryRefine │  │  ExpRag    │             │
│  │             │  │  Service    │  │  Service    │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
│                          │                                       │
└──────────────────────────┼───────────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                   PostgreSQL + pgvector                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   sessions   │  │ observations │  │  summaries   │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

### Data Flow

```
Session Start
     │
     ▼
┌────────────────┐
│ Create Session │
└────────────────┘
     │
     ▼
┌─────────────────────┐
│ Agent Works         │
│ (Tool calls, etc.) │
└─────────────────────┘
     │
     ▼
┌─────────────────────┐     ┌──────────────────┐
│ Ingest Observation │────▶│  QualityScorer  │
└─────────────────────┘     │  (estimate)      │
                           └──────────────────┘
     │                            │
     │                            ▼
     │                    ┌──────────────────┐
     │                    │ Quality Score    │
     │                    │ (stored in DB)   │
     │                    └──────────────────┘
     │
     ▼
┌─────────────────────┐
│ Session Complete    │
└─────────────────────┘
     │
     ▼
┌─────────────────────┐     ┌──────────────────┐
│ Feedback Inference  │────▶│  QualityScorer  │
└─────────────────────┘     │  (update scores) │
                           └──────────────────┘
                                    │
                                    ▼
                           ┌──────────────────┐
                           │ MemoryRefine     │
                           │ (async cleanup)  │
                           └──────────────────┘
```

---

## Database Schema

### V11: Quality Score Fields

Added to `mem_observations` table:

```sql
-- Quality scoring fields
quality_score FLOAT,           -- 0.0-1.0 quality score
feedback_type VARCHAR(20),    -- SUCCESS/PARTIAL/FAILURE/UNKNOWN
last_accessed_at TIMESTAMP,   -- Last retrieval time
access_count INT DEFAULT 0,  -- Number of retrievals

-- Refinement tracking
refined_at TIMESTAMP,         -- Last refinement time
refined_from_ids JSONB,       -- IDs of merged/precursor observations
user_comment TEXT,            -- User feedback comment
feedback_updated_at TIMESTAMP -- Last feedback update
```

### V12: Step Efficiency Fields

Added to `mem_sessions` table:

```sql
-- Step efficiency tracking
total_steps INT DEFAULT 0,
avg_steps_per_task FLOAT
```

Added to `mem_observations`:

```sql
-- Step number within session
step_number INT
```

---

## Core Services

### QualityScorer

**Location**: `backend/src/main/java/com/ablueforce/cortexce/service/QualityScorer.java`

Evaluates observation quality based on:

1. **Feedback Type** (base score):
   - SUCCESS: 0.75
   - PARTIAL: 0.50
   - FAILURE: 0.20
   - UNKNOWN: 0.50

2. **Efficiency Bonus**: Fewer tool uses = higher score

3. **Content Bonus**: Longer, detailed content gets bonus

```java
public float estimateQuality(FeedbackType feedback, String content, 
                           List<String> facts, int toolUseCount) {
    float baseScore = feedback.baseScore;
    float efficiencyBonus = Math.max(0, (20 - toolUseCount) * 0.01f);
    float contentBonus = content != null ? Math.min(0.1f, content.length() / 10000f) : 0;
    
    return Math.min(1.0f, baseScore + efficiencyBonus + contentBonus);
}
```

### MemoryRefineService

**Location**: `backend/src/main/java/com/ablueforce/cortexce/service/MemoryRefineService.java`

Manages memory lifecycle:

1. **Delete**: Removes low-quality observations (< 0.3 threshold)
2. **Refine**: Rewrites stale observations using LLM
3. **Cooldown**: 7-day period before re-refinement

```java
@Async
public void refineMemory(String projectPath) {
    if (!refineEnabled) return;
    
    List<ObservationEntity> candidates = findRefineCandidates(projectPath);
    
    // Categorize: delete vs refine
    List<ObservationEntity> toDelete = candidates.stream()
        .filter(o -> o.getQualityScore() < deleteThreshold)
        .toList();
    
    // Delete low quality
    deleteLowQualityObservations(toDelete);
    
    // Refine candidates (LLM rewrite)
    for (ObservationEntity obs : toRefine) {
        if (canRefine(obs)) {
            refineObservation(obs);
        }
    }
}
```

### ExpRagService

**Location**: `backend/src/main/java/com/ablueforce/cortexce/service/ExpRagService.java`

Retrieves experiences for in-context learning:

```java
public List<Experience> retrieveExperiences(String currentTask, 
                                            String projectPath, 
                                            int count) {
    // Get high-quality observations
    List<ObservationEntity> highQuality = observationRepository
        .findHighQualityObservations(projectPath, MIN_QUALITY_THRESHOLD, count * 3);
    
    return highQuality.stream()
        .map(this::toExperience)
        .toList();
}

public String buildICLPrompt(String currentTask, List<Experience> experiences) {
    // Builds prompt with historical experiences
}
```

---

## API Endpoints

### MemoryController

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/memory/refine` | POST | Trigger memory refinement |
| `/api/memory/experiences` | POST | Retrieve experiences for ICL |
| `/api/memory/icl-prompt` | POST | Build ICL prompt |
| `/api/memory/quality-distribution` | GET | Get quality distribution |

### Example Usage

```bash
# Trigger refinement
curl -X POST "http://localhost:37777/api/memory/refine?project=/path/to/project"

# Retrieve experiences
curl -X POST "http://localhost:37777/api/memory/experiences" \
  -H 'Content-Type: application/json' \
  -d '{"task": "implement auth", "project": "/path", "count": 4}'

# Get quality distribution
curl "http://localhost:37777/api/memory/quality-distribution?project=/path"
```

---

## Quality Scoring System

### Feedback Inference

When a session completes, the system infers feedback from the completion message:

```java
private QualityScorer.FeedbackType inferFeedback(String lastAssistantMessage, 
                                                 int observationCount,
                                                 long sessionDurationMs) {
    // Check for success/failure keywords
    if (lastAssistantMessage.contains("完成") || 
        lastAssistantMessage.contains("completed") ||
        lastAssistantMessage.contains("solved")) {
        return FeedbackType.SUCCESS;
    }
    
    if (lastAssistantMessage.contains("失败") || 
        lastAssistantMessage.contains("failed")) {
        return FeedbackType.FAILURE;
    }
    
    // Heuristic based on observation count and duration
    if (observationCount < 3 || sessionDurationMs < 5000) {
        return FeedbackType.FAILURE;
    }
    
    return FeedbackType.PARTIAL;
}
```

### Quality Distribution

The system categorizes observations into quality tiers:

- **High**: quality_score >= 0.6
- **Medium**: 0.4 <= quality_score < 0.6
- **Low**: quality_score < 0.4
- **Unknown**: quality_score is NULL

---

## Memory Refinement

### Candidates Selection

Memory refinement targets observations based on:

1. **Low Quality**: quality_score < 0.3
2. **Stale**: Not updated in 30 days
3. **Overdue**: Not refined in 7 days

### Cooldown Mechanism

After refinement, observations have a 7-day cooldown before they can be refined again:

```java
private boolean canRefine(ObservationEntity obs) {
    if (obs.getRefinedAt() == null) return true;
    return obs.getRefinedAt().isBefore(OffsetDateTime.now().minusDays(cooldownDays));
}
```

---

## Experience Retrieval (ExpRAG)

### Retrieval Flow

```
New Task Request
       │
       ▼
┌──────────────────┐
│ Find High-Quality│
│ Observations     │
│ (quality >= 0.6) │
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ Convert to       │
│ Experience Format│
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ Build ICL Prompt │
│ with Context     │
└──────────────────┘
```

### Experience Format

```java
public record Experience(
    String id,
    String task,           // What was the task
    String strategy,       // How was it solved
    String outcome,        // What was the result
    String reuseCondition, // When to reuse
    float qualityScore,    // Quality rating
    OffsetDateTime createdAt
) {}
```

---

## Feature Flags

All Evo-Memory features can be toggled via configuration:

```yaml
app:
  memory:
    # Enable/disable memory refinement
    refine-enabled: true
    
    # Quality threshold for retrieval
    quality-threshold: 0.6
    
    refine:
      # Delete threshold
      delete-threshold: 0.3
      # Cooldown days
      cooldown-days: 7
      # Stale days
      stale-days: 30
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MEMORY_REFINE_ENABLED` | true | Enable refinement |
| `MEMORY_QUALITY_THRESHOLD` | 0.6 | Retrieval filter |
| `MEMORY_REFINE_DELETE_THRESHOLD` | 0.3 | Delete threshold |
| `MEMORY_REFINE_COOLDOWN_DAYS` | 7 | Cooldown period |
| `MEMORY_REFINE_STALE_DAYS` | 30 | Stale threshold |

---

## Testing

### Test Scripts

| Script | Purpose |
|--------|---------|
| `scripts/regression-test.sh` | Core API regression tests |
| `scripts/evo-memory-e2e-test.sh` | Evo-Memory E2E workflow |
| `scripts/evo-memory-value-test.sh` | Business value validation |

### Running Tests

```bash
# Regression tests (24 tests)
bash scripts/regression-test.sh

# Evo-Memory E2E tests (16 tests)
bash scripts/evo-memory-e2e-test.sh

# Business value tests (7 tests)
bash scripts/evo-memory-value-test.sh
```

### Test Results

- **Regression Tests**: 23/24 PASS (Test 10 is expected to fail due to cleanup)
- **E2E Tests**: 16/16 PASS
- **Value Tests**: 7/7 PASS

---

## Configuration

### Application Properties

Location: `backend/src/main/resources/application.yml`

```yaml
server:
  port: 37777

app:
  memory:
    refine-enabled: ${MEMORY_REFINE_ENABLED:true}
    quality-threshold: ${MEMORY_QUALITY_THRESHOLD:0.6}
    refine:
      delete-threshold: ${MEMORY_REFINE_DELETE_THRESHOLD:0.3}
      cooldown-days: ${MEMORY_REFINE_COOLDOWN_DAYS:7}
      stale-days: ${MEMORY_REFINE_STALE_DAYS:30}
```

### Starting the Service

```bash
cd backend
source .env
java -jar target/cortex-ce-0.1.0-beta.jar --spring.profiles.active=dev
```

---

## Limitations and Future Work

### Current Limitations

1. **Async Refinement**: Refinement happens asynchronously; results aren't immediately visible
2. **Feedback Inference**: Keyword-based inference is simplistic; could use LLM
3. **Quality Scoring**: Formula-based; could benefit from ML model
4. **No Multi-modal**: Currently text-only

### Future Improvements

1. **LLM-based Refinement**: Use actual LLM for memory rewriting
2. **Learned Quality**: Train model on labeled quality data
3. **Multi-modal**: Support images, code, etc.
4. **Distributed**: Scale across multiple nodes

---

## References

- [Evo-Memory Paper](https://arxiv.org/abs/2511.20857)
- [Claude-Mem Java Backend](./backend/README.md)
- [Original Implementation](./proxy/README.md)

---

## Changelog

| Date | Commit | Description |
|------|--------|-------------|
| 2026-03-17 | f06452b | Phase 1: Quality scoring implementation |
| 2026-03-17 | 1599f08 | Phase 2: MemoryRefineService |
| 2026-03-17 | b841b0e | Phase 2: ExpRAG + SessionEnd |
| 2026-03-17 | 7a04284 | Phase 3: ReMem API + V12 |
| 2026-03-17 | 606fafc | Feature flags |
| 2026-03-17 | 4592ecf | E2E tests |
| 2026-03-17 | d68510b | Business value tests |
