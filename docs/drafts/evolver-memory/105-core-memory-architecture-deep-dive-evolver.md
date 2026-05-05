# 105 - EvoMap Memory System: Core Architecture Deep Dive

> **Doc**: 105 | **Status**: Draft | **Author**: PM Agent | **Date**: 2026-05-05
> **Source**: `/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/`
> **Modules analyzed**: `memoryGraph.js`, `memoryGraphAdapter.js`, `narrativeMemory.js`, `signals.js`, `learningSignals.js`, `localStateAwareness.js`, `reflection.js`

---

## 1. Design Philosophy

Evolver's memory system is built on three core principles:

1. **Append-only event log** — The primary store is a JSONL file (`memory_graph.jsonl`). State is derived from replaying events. This makes the system audit-able, replay-able, and corruption-resistant.
2. **Signal-driven context** — Rather than storing raw conversation text, the system extracts structured *signals* (semantic tags) from logs and uses them for retrieval and gene selection.
3. **Dual-stack architecture** — A local JSONL implementation is the source of truth; an optional remote SaaS adapter wraps it with graceful offline fallback.

---

## 2. Storage Architecture

### 2.1 Files

| File | Purpose | Type |
|------|---------|------|
| `memory_graph.jsonl` | Append-only event log | JSONL |
| `memory_graph_state.json` | Mutable snapshot of last action (single record) | JSON |
| `evolution_narrative.md` | Rolling human-readable journal | Markdown |
| `reflection_log.jsonl` | Periodic strategic reflection records | JSONL |

**Location**: `{MEMORY_DIR}/evolution/` (default) or `{MEMORY_DIR}/evolution/scopes/{scope}/` when `EVOLVER_SESSION_SCOPE` is set.

### 2.2 Session Scope Isolation

When `EVOLVER_SESSION_SCOPE` env var is set, all evolution state (memory graph, assets, skills) is isolated under a per-scope subdirectory. This prevents cross-channel/cross-project memory contamination — e.g., a Discord channel or a specific git repo gets its own isolated memory.

Sanitization: scope string is restricted to `[a-zA-Z0-9_\-\.]` max 128 chars, path traversal is blocked.

### 2.3 Write Strategy: Atomic + Append-only

- **Graph events**: Appended one-per-line to JSONL (never overwrite).
- **State**: Written atomically via rename-on-write (`writeJsonAtomic`) — write to `.tmp`, then `rename`.
- **Narrative**: Append entry, then trim to max 30 entries / 12000 chars.
- **Reflection log**: Append one JSON object per line.

---

## 3. Event Model (MemoryGraphEvent)

The entire memory is built from events of kind:

```typescript
type MemoryGraphEventKind =
  | 'signal'          // Periodic snapshot of current signals
  | 'hypothesis'      // Planned approach before attempt
  | 'attempt'         // Action taken (mutation applied)
  | 'outcome'         // Result of an attempt (inferred)
  | 'confidence_edge' // Signal→Gene edge confidence (derived)
  | 'confidence_gene_outcome' // Gene→Outcome confidence (derived)
  | 'external_candidate' // External asset entered as candidate
```

### 3.1 Signal Event

```json
{
  "type": "MemoryGraphEvent",
  "kind": "signal",
  "id": "mge_{timestamp}_{hash}",
  "ts": "2026-05-05T...",
  "signal": {
    "key": "stableHash_of_normalized_signals",
    "signals": ["log_error", "errsig:TypeError...", "perf_bottleneck"],
    "error_signature": "normalized_errsig_or_null"
  },
  "observed": { ... }  // baseline snapshot (scan_ms, recent_error_count, etc.)
}
```

### 3.2 Hypothesis Event

Records what the evolver *plans* to do before taking action. Captures: signal key, mutation, personality state, selected gene, capsules used, observations at planning time.

### 3.3 Attempt Event

Records the action taken. Mirrors hypothesis but with `action.id` added. Also writes to the mutable `memory_graph_state.json` as `last_action`.

### 3.4 Outcome Event

```json
{
  "kind": "outcome",
  "signal": { "key": "...", "signals": [...] },
  "gene": { "id": "gene_xxx", "category": "..." },
  "action": { "id": "act_..." },
  "outcome": {
    "status": "success" | "failed",
    "score": 0.0-1.0,
    "note": "error_cleared | error_persisted | new_error_appeared | stable_no_error | evolutionevent_observed"
  },
  "confidence": { "half_life_days": 30 }
}
```

### 3.5 Confidence Edge Events

After recording an outcome, two derived events are appended:
- `confidence_edge`: Signal×Gene pair → P(success) with half-life decay
- `confidence_gene_outcome`: Gene alone → P(success) with half-life decay (broader prior)

These are **materialized aggregates** stored for auditability; the actual aggregation is re-computed at read time from raw outcome events.

---

## 4. Signal Processing Pipeline

### 4.1 Extraction (`signals.js`)

`extractSignals({ recentSessionTranscript, todayLog, memorySnippet, userSnippet, recentEvents })`

Corpus = concatenate all text inputs, then scan with regex + heuristics across **4 languages** (EN, ZH-CN, ZH-TW, JA):

**Defensive signals** (errors, missing resources):
- `log_error` — via `[error]`, `error:`, `exception:`, JSON `iserror":true`, etc.
- `errsig:{clipped_line}` — normalized error signature (paths/hex/numbers replaced with `<path>`, `<hex>`, `<n>`)
- `recurring_error` + `recurring_errsig(Nx):{sig}` — errors seen ≥3× in corpus
- `memory_missing`, `user_missing`, `session_logs_missing`, `windows_shell_incompatible`, `path_outside_workspace`

**Opportunity signals** (innovation space):
- `user_feature_request` + snippet — 4-language feature request detection
- `user_improvement_suggestion` + snippet — 4-language improvement detection
- `perf_bottleneck` — slow, timeout, latency, oom keywords
- `capability_gap` — "not supported", "cannot", "unsupported"

**Tool analytics**:
- `high_tool_usage:{tool}` — tool used ≥10×
- `repeated_tool_usage:exec` — exec used ≥5×
- `tool_bypass` — direct node/python/curl in exec instead of registered tools

### 4.2 History-Aware De-duplication

`analyzeRecentHistory(recentEvents)` computes from last 10 events:
- **Signal suppression**: signals appearing in ≥3 of last 8 events → suppressed
- **Consecutive repair count**: 3+ consecutive repair intent → forced innovation
- **Empty cycle count**: blast_radius.files===0 in ≥50% of last 8 → innovation stall
- **Consecutive failure count**: 5+ consecutive failures → `failure_loop_detected` + gene ban
- **Saturation**: 5+ consecutive empty cycles → `force_steady_state` + `evolution_saturation`

### 4.3 Signal Expansion (`learningSignals.js`)

`expandSignals(signals, extraText)` converts raw signal strings into structured tag vocabulary:

```
log_error/error/exception  → problem:reliability, action:repair
protocol/prompt/drift       → problem:protocol, action:optimize, area:prompt
perf/bottleneck             → problem:performance, action:optimize
feature/capability_gap      → problem:capability, action:innovate
stagnation/plateau          → problem:stagnation, action:innovate
```

`scoreTagOverlap(gene, signals)` — computes Jaccard-like overlap between gene tags and expanded signal tags, used in gene selection.

### 4.4 Signal Key

`computeSignalKey(signals)` — stable hash of normalized, deduplicated, sorted signal list. Used as the lookup key in the memory graph for signal→gene edge lookups.

Normalization: `errsig:` variants are hash-normalized (paths/numbers stripped) before hashing so the same error type matches across runs.

---

## 5. Confidence Scoring & Gene Selection

### 5.1 Edge Probability (Laplace Smoothed)

```javascript
p = (success + 1) / (total + 2)  // Laplace smoothing avoids 0/1 extremes
```

### 5.2 Half-Life Decay

```javascript
decayWeight(updatedAt, halfLifeDays) = 0.5 ^ (ageDays / halfLifeDays)
```

- Signal×Gene edges: half-life = 30 days
- Gene→Outcome prior: half-life = 45 days

### 5.3 Combined Score

```
gene_score = (signal_edge_value × signal_similarity) + (gene_prior × 0.12)
signal_edge_value = P(success) × decay_weight
```

Jaccard similarity threshold for considering a historical signal key: ≥0.34

### 5.4 Gene Banning (Low-Efficiency Suppression)

Genes are banned from selection when:
- attempts≥2 AND best_score < 0.18 (drift disabled) — persistent low signal-edge efficiency
- attempts<2 AND prior_attempts≥3 AND prior<0.12 (drift disabled) — consistently poor global outcomes with sparse signal edges

---

## 6. Outcome Inference (No Explicit Labels)

Evolver does **not** require an external label for outcome. It infers from:

### 6.1 Error State Transition

| prevHadError | currentHasError | Inferred |
|---|---|---|
| true | false | success (error_cleared, score=0.85) |
| true | true | failed (error_persisted, score=0.20) |
| false | true | failed (new_error_appeared, score=0.15) |
| false | false | success (stable_no_error, score=0.60) |

### 6.2 Heuristic Delta Enhancement

Score is then adjusted by:
- **Error count delta**: `±max(0.12, delta/50)` — if error count dropped, bonus
- **Scan time delta**: `±max(0.06, (prevScan - curScan) / prevScan)` — if faster, bonus

### 6.3 EvolutionEvent Observation

Before falling back to heuristic, the system scans the last 400 lines of session+log tail for a `{"type":"EvolutionEvent"...}` JSON blob and extracts its `outcome.status` and `outcome.score` directly.

---

## 7. Adapter Pattern: Local / Remote Dual-Stack

```
memoryGraphAdapter.js
├── localAdapter (default)
│   └── Wraps memoryGraph.js directly — full offline capability
└── remoteAdapter (opt-in via MEMORY_GRAPH_PROVIDER=remote)
    └── Writes locally first, async-syncs to remote SaaS
        Falls back to local on any remote failure
```

**Key design**: Remote is purely additive. Local JSONL is always the source of truth. `getAdvice()` is the primary candidate for remote enhancement (richer graph reasoning); write operations always go to local first.

**Env vars**:
- `MEMORY_GRAPH_PROVIDER=local|remote` (default: local)
- `MEMORY_GRAPH_REMOTE_URL`
- `MEMORY_GRAPH_REMOTE_KEY`
- `MEMORY_GRAPH_REMOTE_TIMEOUT_MS=5000`

---

## 8. Narrative Memory

`narrativeMemory.js` — Rolling human-readable journal in Markdown.

**Entry format**:
```markdown
### [2026-05-05 15:13] REPAIR - success
- Gene: gene_xxx | Score: 0.85 | Scope: 3 files, 47 lines
- Signals: [log_error, errsig:TypeError...]
- Why: null_risk_ref actor..."
- Result: TypeError fixed by adding null check
```

**Trim policy**: max 30 entries, max 12000 chars. Enforced atomically.

**Use**: Loaded as context in the evolution loop prompt (last 8 entries, max 4000 chars).

---

## 9. Reflection System

`reflection.js` — Periodic strategic review separate from the per-cycle signal extraction.

### 9.1 Adaptive Interval

| Recent outcomes | Interval |
|---|---|
| All success (last 3) | 8 cycles |
| All failed (last 3) | 3 cycles |
| Mixed | 5 cycles (default) |

**Cooldown**: Once a reflection fires, 30 minutes must pass before the next one.

### 9.2 Reflection Context

Built from: last 10 cycle stats (success/fail counts, intent distribution, gene usage), current signals, memory graph advice, recent narrative → formatted as a strategic Q&A prompt for the LLM.

### 9.3 Output

LLM responds with JSON: `{ insights: [...], strategy_adjustment: "...", priority_signals: [...] }`

Recorded to `reflection_log.jsonl` and used to generate mutation suggestions (`buildSuggestedMutations`):
- stagnation → creativity +0.05
- errors → rigor +0.05
- capability gap → risk_tolerance +0.05

---

## 10. Local State Awareness

`localStateAwareness.js` — Captures first-class observability signals about the evolver's own infrastructure state.

**Captured dimensions**:
- **Node Identity**: A2A node ID (registered vs. not), secret presence
- **Environment Config**: A2A env vars configured vs. missing, .env file presence
- **Evolution State**: cycle count, last run timestamp, active task, personality state
- **Memory State**: MEMORY.md size, memory_graph.jsonl size, narrative existence
- **Skills State**: installed skill count

**Output format**: Structured sections in plain text, ready to inject into evolver prompt context.

---

## 11. BlueCortexCE Comparison Map

| Aspect | EvoMap | BlueCortexCE (claude-mem-java) |
|---|---|---|
| Primary store | JSONL append-only + JSON state | PostgreSQL + pgvector |
| Event model | 7 MemoryGraphEvent kinds | 4 ObservationEntity kinds |
| Signal extraction | Text regex + 4-lang heuristics | Prompt-driven structured extraction |
| Outcome inference | Error state transition + delta | LLM-generated summary |
| Confidence scoring | Laplace + half-life decay | Vector similarity |
| Narrative memory | Rolling markdown journal | SSE-pushed observations |
| Session scope isolation | `EVOLVER_SESSION_SCOPE` env var | `sessionId` in DB |
| Adapter pattern | Local/Remote dual-stack | `CortexMemClient` interface |
| Reflection system | Adaptive interval + cooldown | `/api/context/generate` hook |
| State awareness | `localStateAwareness.js` | Health check endpoint |

### 11.1 Key Differentiators Worth Borrowing

1. **Signal taxonomy + de-duplication**: EvoMap's `analyzeRecentHistory` suppressing over-processed signals (≥3 in last 8) is a powerful mechanism BlueCortexCE lacks. A similar saturation detection could prevent the CE system from re-extracting the same observations repeatedly.

2. **Outcome inference without labels**: CE's LLM-generated summaries are richer but require an extra LLM call. EvoMap's error-transition inference is stateless and instant.

3. **Narrative memory**: CE lacks a chronological narrative journal. A lightweight markdown narrative would complement the structured DB observations.

4. **Adapter pattern**: The `memoryGraphAdapter.js` dual-stack (local-first, remote-additive) is a clean pattern for CE's SDK — allowing local-only mode while enabling future cloud sync.

5. **Half-life decay on embedding relevance**: CE's pgvector stores don't decay. Adding temporal decay to similarity scores would better surface recent observations.

---

## Changelog

| Date | Entry |
|------|-------|
| 2026-05-05 | Initial doc #105: Core memory architecture deep dive |
