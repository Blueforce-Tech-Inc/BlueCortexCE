---
name: doc-management
description: Systematic documentation placement, archival, and lifecycle management for the project.
requires:
  bins:
    - find   # for scanning .md files
metadata: {"openclaw": {"emoji": "📚"}}
---

# Documentation Management Skill

## Purpose

Prevent the "docs scattered everywhere" problem. Every `.md` file has a clear home based on its lifecycle stage. This skill defines where docs go, when to archive them, and how to keep the project navigable.

## When to Activate

Activate this skill when the agent:
- Creates a new `.md` file anywhere in the project
- Moves, renames, or reorganizes documentation
- Completes a task that produced progress reports or test results
- Runs a documentation audit or health check
- Notices stale or duplicate documents
- Reviews a PR that adds or moves `.md` files

## Quick Reference

```
Component README?        → <component>/README.md
Project-essential?       → Root (README, CHANGELOG, CONTRIBUTING, SECURITY, CODE_OF_CONDUCT only)
One-time snapshot?       → docs/archive/YYYY-MM-DD_<name>.md
Under active iteration?  → docs/drafts/<name>.md
Periodically updated?    → appropriate dir + purpose header (mandatory)
Stable reference?        → docs/<name>.md
```

## Directory Map

```
project-root/
├── docs/
│   ├── archive/              # Historical snapshots (read-only after archival)
│   │   └── README.md         # Index: date, origin path, description
│   ├── drafts/               # Active design docs under iteration
│   └── *.md                  # Stable reference docs (API, Architecture, etc.)
├── *.md                      # Root: essential project files only (see Rule 5)
├── backend/README.md         # Component README (allowed)
├── proxy/README.md           # Component README (allowed)
├── examples/*/README.md      # Example project README (allowed)
├── go-sdk/*/README.md        # SDK README (allowed)
├── scripts/README.md         # Scripts README (allowed)
├── skills/*/SKILL.md         # Skill definitions (allowed)
├── openclaw-plugin/skills/*/SKILL.md  # Plugin skill definitions (allowed)
└── memory/*.md               # Agent memory (.gitignored, not tracked)
```

## Rules

### Rule 1: Classify Before Creating

Before creating ANY `.md` file, classify it using the Quick Reference above.

| Type | Lifecycle | Location |
|------|-----------|----------|
| **Live** | Periodically overwritten | Appropriate dir + purpose header |
| **Historical** | Written once, never changed | `docs/archive/YYYY-MM-DD_<name>.md` |
| **Draft** | Actively iterated | `docs/drafts/<name>.md` |
| **Reference** | Stable, updated with releases | `docs/<name>.md` |
| **Component README** | Tied to a component | `<component>/README.md` |
| **Root** | Project-essential | `./<name>.md` |

### Rule 2: Live Documents Must Have a Purpose Header

Every document that gets periodically updated MUST include this header immediately after the title:

```markdown
# Title

> **Purpose**: What this document tracks.
> **Updated by**: Who/what updates it (cron ID, manual process, etc.).
> **Update rule**: How it changes (overwrite with latest / append entries).
```

This header is mandatory. No exceptions. It answers "why does this file exist and who keeps it current?"

### Rule 3: Archive, Don't Delete

When a document's content becomes a historical record:

1. Identify the last-relevant date for the content
2. Copy the file to `docs/archive/YYYY-MM-DD_<descriptive-name>.md`
   - `YYYY-MM-DD` = last-relevant date (not today's date)
   - `<descriptive-name>` = clear description of what was captured
3. Add a row to the `docs/archive/README.md` table:
   ```
   | `YYYY-MM-DD_<name>.md` | `<original-path>` | YYYY-MM-DD | <brief description> |
   ```
4. Remove the original file

**Archive size**: If `docs/archive/` exceeds 30 files, review whether any very old entries can be removed (they remain in git history regardless).

**When to archive a draft**: When the feature it describes is fully implemented AND the design is no longer referenced for active work. If the draft still contains design rationale useful for future reference, promote it to `docs/` instead of archiving.

### Rule 4: No Duplicate Purpose

Before creating a new file, ask: "Does a file already exist for this purpose?"
- **Yes** → Update it in place (live docs) or create a new archive entry (historical)
- **No** → Create the file in the correct location per Rule 1

Never have two files tracking the same thing.

### Rule 5: Root Directory Is Sacred

Only these files may live in the project root:

| File | Required? | Notes |
|------|-----------|-------|
| `README.md` | Yes | Project overview |
| `README-zh-CN.md` | Optional | Chinese version |
| `CHANGELOG.md` | Yes | Release history |
| `CONTRIBUTING.md` | Optional | Contribution guide |
| `CODE_OF_CONDUCT.md` | Optional | Community standards |
| `SECURITY.md` | Optional | Security policy |
| `RELEASE.md` | Optional | Release process |
| `DOCKER_README.md` | Optional | Docker quick-start (when `docker-compose.yml` is at root) |
| `CLAUDE.md` | Optional | Claude Code config |
| `AGENTS.md`, `SOUL.md`, `IDENTITY.md`, `USER.md`, `TOOLS.md` | OpenClaw | Agent config |
| `HEARTBEAT.md`, `MEMORY.md` | Agent | `.gitignored` memory |
| `docker-compose.yml`, `Dockerfile`, etc. | Infra | Non-`.md` files are fine |

**Everything else** → `docs/`, `docs/drafts/`, or `docs/archive/`.

### Rule 6: Language Consistency

- Reference docs: Maintain paired versions — `<name>.md` (English) + `<name>-zh-CN.md` (Chinese)
- When updating a doc that has only one language version: update it as-is; note in the commit that a counterpart is missing
- Drafts: Any language is fine; clarity matters most
- Archive: Keep original language; do not translate after archival
- Code comments: English preferred

## Draft Lifecycle

Drafts in `docs/drafts/` are **living documents** during active development. Follow this lifecycle:

```
Created → Iterated → Implemented → Decision point
                                           ├─ Still useful as reference? → Promote to docs/
                                           └─ No longer needed?          → Archive to docs/archive/
```

**Signs a draft should be archived or promoted**:
- The feature it describes is fully implemented and tested
- The design has been stable for 2+ weeks without changes
- New team members would find the final `docs/` version more useful than the draft

## Audit Checklist

Run this periodically (at least weekly) or before major commits.

**Step 1**: Scan all `.md` files:
```bash
find . -name "*.md" \
  -not -path "*/node_modules/*" \
  -not -path "*/.git/*" \
  -not -path "*/target/*" \
  -not -path "*/.mvn/*" \
  -not -path "*/memory/*" \
  | sort
```

**Step 2**: For each file, verify:

- [ ] In correct location per Rule 1?
- [ ] If live: has purpose header per Rule 2?
- [ ] No duplicate purpose file exists?
- [ ] If completed tracker in `docs/drafts/`: archived?
- [ ] `docs/archive/README.md` table is current?
- [ ] If stale: has archive entry?
- [ ] If in root: on the allowlist?

**All checkboxes pass** → Documentation health is good.
**Any fails** → Fix immediately per the rules above.

## Common Violations

| Violation | Correct Action |
|-----------|---------------|
| `backend/TASK_PROGRESS.md` for a new task | Use `docs/drafts/<task-name>.md` |
| Test report left in `backend/` or root | Archive to `docs/archive/YYYY-MM-DD_test-report.md` |
| Completed progress in `docs/drafts/` | Archive to `docs/archive/YYYY-MM-DD_<name>.md` |
| Duplicate file for same data | Keep one, delete/archive the other |
| Temporary notes in root | Move to `docs/drafts/` or delete |
| Live doc missing purpose header | Add header per Rule 2 |
| New root-level `.md` not in allowlist | Move to `docs/` |
| Draft never archived after completion | Archive or promote per Draft Lifecycle |
