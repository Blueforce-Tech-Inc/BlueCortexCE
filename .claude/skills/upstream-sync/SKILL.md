---
name: upstream-sync
description: Sync BlueCortexCE with upstream improvements from claude-mem-java. Use whenever: user mentions syncing with upstream/claude-mem, updating BlueCortexCE to track new features, merging improvements from parent project, or WebUI submodule needs updating. Always prompts for claude-mem-java path and confirms WebUI alignment before proceeding.
---

# Upstream Sync Skill

Keep BlueCortexCE aligned with upstream claude-mem-java improvements.

## Quick Reference

| Task | Command |
|------|----------|
| Analyze upstream | `git log <ref>..HEAD --oneline` |
| Check submodule | `git submodule status` |
| Update submodule | `cd webui && git fetch origin main && git checkout origin/main` |
| Compile | `cd backend && ./mvnw clean compile` |

---

## Step 1: Gather Information (ALWAYS FIRST)

Do NOT proceed without these inputs:

### 1.1 Upstream Repository Path
Ask: "请提供 claude-mem-java 代码库的本地路径"

### 1.2 Reference Commit
Ask: "从哪个提交开始比较? (留空则使用当前 HEAD)"

### 1.3 WebUI Status
Ask: "WebUI 子模块 (github.com/Blueforce-Tech-Inc/claude-mem) 是否已更新到对应版本?"

**Only after all three are confirmed, proceed to Step 2.**

---

## Step 2: Analyze Upstream Changes

1. Navigate to upstream repo: `cd <user-provided-path>`
2. Get commit range: `git log <ref-commit>..HEAD --oneline`
3. For each commit:
   - Read the diff: `git show <commit> --stat`
   - Identify: migrations, entities, repositories, services, controllers
4. Create feature gap analysis

### Common Upstream Features to Look For

| Feature | Upstream Version | BlueCortexCE Version | Notes |
|---------|-----------------|---------------------|-------|
| Platform Source | V10 | V18 (new) | `platform_source` column |
| Observation Feedback | V9 | V17 (new) | `observation_feedback` table |
| Session Age Guard | V9 | Code | 4-hour wall-clock limit |
| Semantic Endpoint | V9 | Code | `/api/context/semantic` |

---

## Step 3: Create Sync Plan

Location: `docs/drafts/upstream-sync-plan.md`

### Plan Template

```markdown
# BlueCortexCE Upstream Sync Plan

**Date**: YYYY-MM-DD
**Status**: Draft v1
**Reference**: upstream commit <hash>

## Executive Summary
[What we're syncing]

## Current State
| Component | BlueCortexCE | Upstream |
|-----------|-------------|----------|
| Latest Migration | V16 | V10 |
| ... | ... | ... |

## Feature Gap Analysis
[What BlueCortexCE is missing]

## Implementation Plan
[Phased approach]

## Testing
[How to verify]

## Rollback
[How to undo]
```

### Plan Rules
- Use placeholders like `<upstream-commit>` not real hashes
- NO hardcoded paths like `/Users/name/...`
- NO developer-specific information
- Iterate 3+ times before presenting to user

---

## Step 4: Implement Changes

### Order of Operations

| Phase | Item | Files |
|-------|------|-------|
| 1 | Database Migrations | `V*.sql` in migration/ |
| 2 | Entity Updates | `*Entity.java` |
| 3 | Repository Updates | `*Repository.java` |
| 4 | Service Updates | `*Service.java` |
| 5 | Controller Updates | `*Controller.java` |
| 6 | WebUI Submodule | `webui/` |

### Migration Naming

BlueCortexCE continues from V16, so new migrations use V17+:
- Upstream V9 → BlueCortexCE V17
- Upstream V10 → BlueCortexCE V18

### Entity Patterns

```java
// Platform source field pattern
@Column(name = "platform_source")
@JsonProperty("platform_source")
private String platformSource = "claude";

// Feedback tracking fields
@Column(name = "generated_by_model")
@JsonProperty("generated_by_model")
private String generatedByModel;

@Column(name = "relevance_count")
@JsonProperty("relevance_count")
private Integer relevanceCount = 0;
```

### Repository Patterns

```java
// Platform source query
@Query("SELECT DISTINCT s.platformSource FROM SessionEntity s WHERE s.platformSource IS NOT NULL ORDER BY s.platformSource")
List<String> findAllPlatformSources();

// Paginated with platform filter
Page<Entity> findAllPaged(@Param("project") String project, @Param("platformSource") String platformSource, Pageable pageable);
```

### Controller Patterns

```java
// Optional platformSource parameter
@RequestParam(required = false) String platformSource

// Projects endpoint returns sources + projectsBySource
Map.of("projects", projects, "sources", sources, "projectsBySource", projectsBySource)
```

---

## Step 5: Verify

```bash
# 1. Compile
cd backend && ./mvnw clean compile

# 2. API tests (manual)
curl "http://localhost:37777/api/observations?platformSource=claude"
curl "http://localhost:37777/api/projects"
curl -X POST http://localhost:37777/api/context/semantic \
  -H "Content-Type: application/json" \
  -d '{"q": "What was implemented?", "limit": 3}'

# 3. Regression tests (if available)
./scripts/regression-test.sh
```

---

## Step 6: Document

Append to `docs/drafts/upstream-sync-plan.md`:

```markdown
## Implementation Summary (YYYY-MM-DD)

### Completed
| Feature | Status | Files |
|---------|--------|-------|
| ... | ✅ Done | ... |

### New Files
- `path/to/new/file.sql`
- `path/to/new/File.java`

### Build Status
```
./mvnw clean compile ✓
```

### Pending
- [ ] Run tests
- [ ] Deploy
- [ ] Commit
```

---

## Key File Locations

```
backend/src/main/
├── java/com/ablueforce/cortexce/
│   ├── entity/          # *Entity.java
│   ├── repository/       # *Repository.java
│   ├── service/          # *Service.java
│   └── controller/       # *Controller.java
└── resources/db/migration/  # V*.sql

webui/                    # Git submodule (Blueforce-Tech-Inc/claude-mem)
```

---

## Common Pitfalls

1. **Wrong migration numbers**: BlueCortexCE is at V16, use V17+ not V9/V10
2. **Missing JSON property**: Use `platform_source` (underscore) not camelCase
3. **Submodule detached HEAD**: Always `git checkout origin/main` not just `git pull`
4. **Missing dependencies**: When adding SessionRepository to AgentService, update constructor

---

## Questions to Ask Before Implementation

1. Upstream repo path: `________`
2. Reference commit: `________` (or HEAD)
3. WebUI aligned: `________` (yes/no)

**If any answer is missing, STOP and ask.**
