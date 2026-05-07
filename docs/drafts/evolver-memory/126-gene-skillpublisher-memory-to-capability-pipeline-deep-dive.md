# Gene → SKILL.md Transformation: How EvoMap Closes the Memory-to-Capability Loop

**Source**: `src/gep/skillPublisher.js` (352 lines, v1.47.0, readable)
**Date**: 2026-05-07
**Related**: [`48`](./48-gene-as-compressed-memory-closed-loop-architecture.md) (Gene as compressed memory) · [`84`](./84-skilldistiller-full-pipeline-deep-dive.md) (SkillDistiller) · [`34`](./34-solidify-pipeline-end-to-end.md) (Solidify pipeline)

---

## 1. Why This Document Exists

Doc 48 covers Gene as "compressed memory" (semantic abstraction + time-decay). Doc 34 covers the Solidify pipeline (commit mechanism). Doc 84 covers SkillDistiller (reverse-engineering from SKILL.md to Gene). But none provide a **source-level walkthrough of `skillPublisher.js`** — the module that **completes** the loop by converting a validated Gene into a distributable `SKILL.md` and publishing it to the Hub.

This doc fills that gap: how a Gene (an in-memory graph entity) becomes a first-class, reusable capability artifact.

---

## 2. Gene Structure (from `skillPublisher.js` perspective)

```javascript
// skillPublisher.js receives a gene object with this shape:
{
  id: "gene_<hash>",           // stable hash ID
  summary: "Retry failed API calls with exponential backoff", // 1-line description
  signals_match: ["recurring_error", "retry_pattern"],      // matched signal types
  capsule_id: "capsule_<hash>", // validated execution proof
  content_hash: "<sha256>",     // content-addressable gene content
  gene_type: "skill"            // vs "insight" / "strategy"
}
```

**Key design insight**: A Gene is **not** a blob of text. It is a structured record whose `summary` + `signals_match` drive naming, whose `capsule_id` provides provenance, and whose `content_hash` enables deduplication and versioning.

---

## 3. skillPublisher.js Pipeline (352 lines, 4 stages)

### Stage 1: Gene → Human-Readable Name (`sanitizeSkillName` + `deriveFallbackName`)

```javascript
// Input: raw gene id like "gene_<hash>" or "gene_distilled_<hash>"
// Output: kebab-case skill name, e.g. "retry-with-backoff"

function sanitizeSkillName(rawName) {
  var name = rawName
    .replace(/[\r\n]+/g, '-')
    .replace(/^gene_distilled_/, '')
    .replace(/^gene_/, '')
    .replace(/_/g, '-')
    // Strip embedded timestamps (10+ digit sequences)
    .replace(/-?\d{10,}-?/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');

  // Reject tool names and too-short identifiers
  if (/^\d{8,}/.test(name)) return null;
  if (/^(cursor|vscode|vim|emacs|windsurf|copilot|cline|codex)[-]?\d*$/i.test(name)) return null;
  if (name.replace(/[-]/g, '').length < 6) return null;
  return name;
}

// Fallback: derive name from signals_match + summary when id is unusable
function deriveFallbackName(gene) {
  // Extract up to 5 meaningful words from signals_match[0:3] + summary
  // Excludes stopwords (the, and, for, with, from, that, this, into, when, are, was, has, had, not, but, its)
}
```

**Design pattern**: Two-tier naming — primary (hash-derived ID → kebab-case), fallback (signals + summary → semantic keywords). Never relies on LLM for naming.

### Stage 2: Name → Title Case Display (`toTitleCase`)

```javascript
"retry-with-backoff" → "Retry With Backoff"
```

### Stage 3: SKILL.md Content Assembly

The module assembles a standard SKILL.md with these fields:
- `name`: Title Case display name
- `gene_id`: Original gene ID (provenance)
- `summary`: Gene summary (what it does)
- `signals_match`: Which signal types triggered this gene
- `content_hash`: Content-addressable hash (versioning, deduplication)
- `created_at`: Timestamp
- `skill_content`: The actual capability (from capsule's execution trace)

### Stage 4: Hub Publishing

```javascript
// Uses a2aProtocol for Hub communication
var { getHubUrl, buildHubHeaders, getNodeId } = require('./a2aProtocol');
// POST to Hub with gene metadata + SKILL.md content
```

---

## 4. Key Design Patterns

### 4.1 Content-Addressable Naming
Gene IDs are content hashes (`gene_<sha256>`). Two identical Genes → same ID. This makes deduplication trivial and versioning automatic.

### 4.2 Two-Tier Name Resolution
1. Try: sanitize gene ID → kebab-case
2. Fallback: extract keywords from signals_match + summary
3. Guard: reject if too short, numeric, or tool-name-like

### 4.3 Hub as Capability Registry
The Hub is not just a search index — it stores published SKILL.md files as distributable assets. The Gene/SKILL.md lifecycle closes when another agent pulls from the Hub.

---

## 5. Comparison: Gene/SKILL.md vs. BlueCortexCE SummaryEntity

| Aspect | EvoMap Gene → SKILL.md | BlueCortexCE SummaryEntity |
|--------|------------------------|----------------------------|
| **Trigger** | Solidify success (validated execution) | Periodic session summary |
| **Naming** | Hash-derived ID → semantic name | Generated summary text |
| **Content** | Execution trace + signals + provenance | Compressed conversation |
| **Versioning** | Content hash (automatic deduplication) | No versioning |
| **Distribution** | Hub publish → other agents pull | Single-node, no distribution |
| **Provenance** | capsule_id, signals_match | No explicit provenance |
| **Reuse mechanism** | SKILL.md as readable skill file | SummaryEntity as API response |

---

## 6. BlueCortexCE Translation Proposal

### P1 — Content-Addressable Observation IDs
```sql
-- Add content_hash to observations
ALTER TABLE mem_observations
  ADD COLUMN content_hash TEXT
  GENERATED ALWAYS AS (encode(sha256(content::text), 'hex')) STORED;

-- Ensure idempotent ingestion via unique constraint on content_hash
CREATE UNIQUE INDEX idx_obs_content_hash ON mem_observations(project_id, content_hash);
```

### P1 — Provenance Metadata
```sql
-- Add signal/provenance fields
ALTER TABLE mem_observations
  ADD COLUMN signal_types TEXT[],       -- e.g. ['recurring_error', 'retry_pattern']
  ADD COLUMN trigger_source TEXT,       -- 'solidify' | 'hook' | 'cron' | 'api'
  ADD COLUMN capsule_id TEXT;           -- Link to validation proof if applicable
```

### P2 — Gene-like Structured Extraction (Phase 3)
The Phase 3 StructuredExtraction (doc `docs/drafts/phase-3-design.md`) is the equivalent of the Gene concept: validated, structured, content-addressable memory artifacts. When the extraction pipeline is complete, each successful extraction becomes a candidate for "Gene-like" promotion (cross-session reuse with provenance).

### P3 — Hub Publish (long-term)
BlueCortexCE currently has no distribution mechanism. The Gene/SKILL.md pipeline suggests: if CE had a Hub, successful StructuredExtractions could be published as reusable knowledge artifacts. This requires: content-addressable naming, semantic search, and a publish protocol.

---

## 7. Source Anchor

```
src/gep/skillPublisher.js
  L12–L28   sanitizeSkillName()  — primary naming
  L30–L36   toTitleCase()        — display formatting
  L38–L62   deriveFallbackName() — signal/summary fallback
  L64–L75   assembleSkillMd()    — SKILL.md content assembly
  L200+     Hub publishing        — a2aProtocol integration
```

---

## 8. Summary

`skillPublisher.js` completes the EvoMap memory-to-capability loop by converting a validated Gene (in-memory graph entity) into a distributable, human-readable `SKILL.md`. Key takeaways:

1. **Content-addressable naming** — Gene IDs are content hashes, enabling automatic deduplication and versioning
2. **Two-tier name resolution** — hash-derived ID first, semantic fallback second
3. **Structured provenance** — `signals_match` + `capsule_id` preserve why the capability was created
4. **Hub as registry** — the capability is only "complete" when published and pullable by other agents

BlueCortexCE already has `SummaryEntity` (periodic session compression) and Phase 3 will add `StructuredExtraction` (validated extraction artifacts). The Gene/SKILL.md model suggests a **P2 enhancement**: add content hashing, signal provenance, and a distribution protocol to make CE memory artifacts reusable across sessions and nodes.
