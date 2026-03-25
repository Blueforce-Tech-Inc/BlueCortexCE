---
name: doc-management
description: Manage project documentation systematically. Activate when creating, reviewing, moving, or archiving any .md files in the project. Ensures docs are placed correctly, avoids duplication, and maintains the archive.
metadata: {"openclaw": {"emoji": "📚"}}
---

# Documentation Management Skill

## Overview

This skill enforces a systematic documentation structure for the Cortex CE project. It prevents the "docs scattered everywhere" problem by defining clear rules for where documents belong.

## Directory Structure

```
project-root/
├── docs/
│   ├── archive/          # Historical snapshots (read-only after archival)
│   │   └── README.md     # Index of archived documents
│   ├── drafts/           # Active design documents under iteration
│   │   └── *.md          # Phase designs, research docs, etc.
│   ├── *.md              # Stable reference documentation (API, Architecture, etc.)
│   └── performance-test-results.md  # Live: auto-updated by cron
├── *.md                  # Root-level: only essential project docs
│   ├── README.md         # Project overview (REQUIRED)
│   ├── CHANGELOG.md      # Release history (REQUIRED)
│   ├── CONTRIBUTING.md   # Contribution guide
│   ├── SECURITY.md       # Security policy
│   └── CODE_OF_CONDUCT.md
└── skills/               # Agent skills
```

## Rules

### Rule 1: Classify Before Creating

Before creating any `.md` file, classify it:

| Type | Location | Example |
|------|----------|---------|
| **Live** (periodically updated) | Appropriate dir + add header | `docs/performance-test-results.md` |
| **Historical** (point-in-time snapshot) | `docs/archive/YYYY-MM-DD_<name>.md` | Test reports, completed trackers |
| **Draft** (under active iteration) | `docs/drafts/` | Design docs, research |
| **Reference** (stable documentation) | `docs/` | API docs, architecture |
| **Root** (project-essential only) | `./` | README, CHANGELOG, CONTRIBUTING |

### Rule 2: Live Documents Must Have a Purpose Header

Every document that gets periodically updated MUST start with:

```markdown
# Title

> **用途**: [What this document is for]
> **维护者**: [Who/what updates it - cron ID or manual]
> **规则**: [How it gets updated - overwrite vs append]
```

### Rule 3: Archive, Don't Delete

When a document becomes outdated:
1. Copy to `docs/archive/YYYY-MM-DD_<descriptive-name>.md`
2. Update `docs/archive/README.md` table
3. Then remove the original

### Rule 4: Never Scatter Duplicates

If you need to record performance results, test results, or progress:
- Check if a file already exists for this purpose
- Update it in place (for live docs) or create a new archive entry (for historical)
- NEVER create `backend/TASK_PROGRESS.md` when `docs/drafts/TASK_PROGRESS.md` exists

### Rule 5: Root Directory Is Sacred

Only these files belong in the project root:
- `README.md`, `README-zh-CN.md`
- `CHANGELOG.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`
- `CLAUDE.md` (Claude Code config)
- `AGENTS.md`, `SOUL.md`, `IDENTITY.md`, `USER.md`, `TOOLS.md` (OpenClaw config)
- `HEARTBEAT.md`, `MEMORY.md` (Agent memory — .gitignored)
- `docker-compose.yml`, `Dockerfile`, etc.

Everything else: use `docs/`, `docs/drafts/`, or `docs/archive/`.

## Audit Checklist

When reviewing documentation health:

1. **Scan for orphaned files**: `find . -name "*.md" -not -path "*/node_modules/*" -not -path "*/.git/*" -not -path "*/target/*"`
2. **Check for duplicates**: Same topic in multiple locations?
3. **Check staleness**: Open each doc, compare "last updated" with actual code state
4. **Verify headers**: Live docs have purpose headers?
5. **Verify archive index**: `docs/archive/README.md` table is current?

## Common Mistakes to Avoid

❌ Creating `backend/TASK_PROGRESS.md` for a new task
❌ Placing test reports in `backend/` or root
❌ Leaving completed progress reports in `docs/drafts/`
❌ Creating a second file for data that should update an existing one
❌ Putting temporary notes in root directory
❌ Forgetting to add purpose headers to live documents

✅ Classify first → choose location → add header if live → create/update
✅ Completed work → archive with date prefix → update archive index
