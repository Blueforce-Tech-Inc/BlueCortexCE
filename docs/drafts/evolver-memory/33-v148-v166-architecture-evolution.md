# Evolver v1.48–v1.66 Architecture Evolution

**Scope**: Delta analysis from v1.47.0 to v1.66.0 (29 commits, 100 files changed, ~10K insertions, ~12K deletions).
**Key constraint**: v1.66.0 `evolve.js` and `memoryGraph.js` are **heavily obfuscated** (JavaScript string encoding). Analysis below is based on diff statistics, readable new modules, and signals.js expansion.

## 1. Structural Overhaul Summary

| Module | v1.47 | v1.66 | Change |
|--------|-------|-------|--------|
| `src/evolve.js` | ~2176 lines (readable) | 0 lines (obfuscated, moved logic) | **Massive refactor** |
| `src/gep/memoryGraph.js` | 787 lines | **0 lines (removed)** | Gutted entirely |
| `src/gep/signals.js` | ~360 lines | 660 lines | +302 lines (Layer 2) |
| `src/gep/selector.js` | ~420 lines | ~420-418=2 net | Minor change |
| `src/adapters/` | **NEW** | 4 files + 3 scripts | Platform integration |
| `src/atp/` | **NEW** | 6 files | Agent commerce |
| `src/config.js` | **NEW** | 18 lines (50+ constants) | Centralized config |

### 1.1 memoryGraph.js Removal

The 787-line `memoryGraph.js` was completely gutted. Key functions that existed in v1.47:
- `appendJsonl()`, `readJsonl()` — JSONL append/read
- `normalizeErrorSignature()`, `computeSignalKey()` — Error signal normalization
- `memoryGraphPath()`, `memoryGraphStatePath()` — File path resolution
- `stableHash()` — FNV-1a hash for signal keys

**Hypothesis**: These functions were either inlined into the obfuscated `evolve.js` or moved to a different module. The memory graph data format (JSONL append-only) likely persists but the implementation is now hidden.

### 1.2 Centralized Configuration (`config.js`)

New `src/config.js` extracts 50+ hardcoded values into environment-overridable constants:

```
Network:    HELLO_TIMEOUT_MS, HEARTBEAT_*, EVENT_POLL_TIMEOUT_MS, HUB_SEARCH_TIMEOUT_MS
Solidify:   VALIDATION_TIMEOUT_MS, CANARY_TIMEOUT_MS, MIN_PUBLISH_SCORE (0.78)
Evolution:  REPAIR_LOOP_THRESHOLD (3), IDLE_FETCH_INTERVAL_MS (600K), PROMPT_MAX_CHARS (24K)
Ops:        MAX_SILENCE_MS (30min), CLEANUP_MAX_AGE_MS (24h), LOCK_MAX_AGE_MS (10min)
Self-PR:    SELF_PR_MIN_SCORE (0.85), SELF_PR_MIN_STREAK (3), SELF_PR_MAX_FILES (3)
Security:   LEAK_CHECK_MODE ('warn')
```

**Design insight**: Every threshold is env-overridable via `envInt()`/`envFloat()`/`envStr()` helpers. This enables runtime tuning without code changes — directly applicable to BlueCortexCE's configuration strategy.

## 2. Platform Adapter Architecture (`src/adapters/`)

### 2.1 hookAdapter.js (205 lines)

Platform detection and hook installation for Cursor, Claude Code, and Codex:

```javascript
const PLATFORMS = {
  cursor: { name: 'Cursor', configDir: '.cursor', detector: '.cursor' },
  'claude-code': { name: 'Claude Code', configDir: '.claude', detector: '.claude' },
  codex: { name: 'Codex', configDir: '.codex', detector: '.codex' },
};
```

**Key functions**:
- `detectPlatform(cwd)` — Walk project root then home dir for platform markers
- `resolveConfigRoot(platformId, cwd)` — Find config root for a platform
- `loadAdapter(platformId)` — Dynamic adapter dispatch
- `mergeJsonFile(filePath, patch)` — Deep-merge JSON config with marker key `_evolver_managed`
- `copyHookScripts(destDir, evolverRoot)` — Install session-start/signal-detect/session-end scripts

### 2.2 Platform-Specific Adapters

| File | Lines | Purpose |
|------|-------|---------|
| `claudeCode.js` | 145 | Claude Code session log parsing, hook injection |
| `codex.js` | 172 | Codex adapter (session format, config) |
| `cursor.js` | 89 | Cursor IDE adapter |

### 2.3 Hook Scripts (`src/adapters/scripts/`)

| Script | Lines | Purpose |
|--------|-------|---------|
| `evolver-session-start.js` | 93 | Hook: runs at session init |
| `evolver-signal-detect.js` | 69 | Hook: real-time signal detection |
| `evolver-session-end.js` | 194 | Hook: session cleanup + outcome recording |

**Design insight for BlueCortexCE**: The adapter pattern separates "platform detection" from "hook logic" from "platform-specific config". CE could adopt a similar `HookAdapter` for different agent runtimes (Claude Code, Cursor, OpenClaw agents).

## 3. Agent Transaction Protocol (ATP) (`src/atp/`)

A new inter-agent commerce layer:

| Module | Purpose |
|--------|---------|
| `hubClient.js` (171 lines) | Hub API client for ATP endpoints |
| `merchantAgent.js` (118 lines) | Ready-to-use merchant agent template |
| `consumerAgent.js` (157 lines) | Ready-to-use consumer agent template |
| `serviceHelper.js` (99 lines) | Service publishing helper |
| `defaultHandler.js` (69 lines) | Default order handler + auto-ATP config |
| `index.js` (23 lines) | Module aggregation |

**Key insight**: ATP enables agents to **sell services to each other** — a "low-commission agent-to-agent transaction network." This extends the A2A protocol from v1.47's hub events into actual commerce.

**BlueCortexCE relevance**: While CE doesn't need agent commerce, the pattern of structured inter-agent communication (hub client, service registration, order handling) could inform CE's MCP tool marketplace or skill sharing.

## 4. Signal Extraction: Weighted Keyword Scoring (Layer 2)

`s/signals.js` gained 300+ lines implementing a **two-layer signal extraction**:

### Layer 1: Regex (existing, binary hit/miss)
### Layer 2: Weighted Keyword Scoring (new, confidence-based)

```javascript
var SIGNAL_PROFILES = {
  perf_bottleneck: {
    keywords: { 'slow': 3, 'timeout': 4, 'bottleneck': 5, 'oom': 5, ... },
    threshold: 6,  // Fire when accumulated score >= 6
  },
  capability_gap: { keywords: {...}, threshold: 5 },
  user_feature_request: { keywords: {...}, threshold: 6 },
  user_improvement_suggestion: { keywords: {...}, threshold: 5 },
  recurring_error: { keywords: {...}, threshold: 7 },
  tool_bypass: { keywords: {...}, threshold: 6 },
  evolution_stagnation_detected: { keywords: {...}, threshold: 6 },
};
```

**How it works**:
1. Each keyword has a weight (1-5)
2. Scan text for all keywords, accumulate scores per signal profile
3. If total score >= threshold → emit signal
4. Catches fuzzy/distributed patterns that no single regex can match

**BlueCortexCE application**: This is directly applicable to CE's observation classification. Instead of relying solely on LLM-based classification (expensive), a weighted keyword scorer could pre-filter/classify observations before LLM processing, reducing cost and latency.

### 4.1 New Opportunity Signals (7 added)

```
issue_already_resolved          — GitHub issue was already closed
openclaw_self_healed            — OpenClaw recovered from error
empty_cycle_loop_detected       — Evolver producing zero-change cycles
explore_opportunity             — Novel exploration direction found
hub_search_miss_with_problem    — Hub search failed with context
plateau_pivot_required          — Stagnation requires strategy change
plateau_pivot_suggested         — Stagnation suggests strategy change
```

### 4.2 Enhanced analyzeRecentHistory()

New tracking fields added to the history analysis:
- `emptyCycleCount` — Total empty cycles in last 8 events
- `consecutiveEmptyCycles` — Consecutive empty cycles at tail (saturation detection)
- `consecutiveFailureCount` — Consecutive failures at tail
- `recentFailureCount` / `recentFailureRatio` — Failure density

## 5. Self-PR System

New auto-contribution system that creates PRs back to the public repo:

```
SELF_PR_MIN_SCORE: 0.85      — Only contribute high-quality mutations
SELF_PR_MIN_STREAK: 3        — Need 3 consecutive successes
SELF_PR_MAX_FILES: 3         — Limit blast radius
SELF_PR_MAX_LINES: 100       — Keep changes small
SELF_PR_COOLDOWN_MS: 24h     — Rate limit
```

**Design insight**: The Self-PR system is a "quality gate + rate limiter" pattern. Only mutations that pass both quality (score >= 0.85) and consistency (3+ streak) checks get auto-contributed.

## 6. Leak Check System

New `LEAK_CHECK_MODE` (values: 'warn', 'error', 'off') provides runtime security scanning for sensitive data leakage in mutations. This addresses the security concern from v1.47's privacy computing work.

## 7. BlueCortexCE Implications

### Direct Applicability

| Evolver Pattern | CE Application | Priority |
|----------------|----------------|----------|
| Centralized config with env overrides | CE `application.properties` → env var binding | P1 |
| Weighted keyword scoring (Layer 2) | Pre-LLM observation classification | P1 |
| Platform adapter architecture | Multi-runtime hook support | P2 |
| analyzeRecentHistory() enhancements | Session-level pattern detection | P1 |
| Self-PR quality gate pattern | Mutation quality gates | P2 |
| Leak check system | Sensitive data detection in observations | P2 |

### Architecture Lessons

1. **Memory graph removal**: The fact that memoryGraph.js was completely gutted suggests the memory layer was simplified or merged into the main loop. CE should avoid over-engineering the memory graph — a simpler append-only JSONL may suffice.

2. **Obfuscation as protection**: The heavy obfuscation of v1.66 core logic suggests commercial/marketplace intent. CE's open-source approach is fundamentally different but should note that evolvers are becoming competitive products.

3. **Config extraction trend**: Moving from hardcoded values to env-overridable config is a maturity signal. CE should audit its own hardcoded values and extract them.

4. **Two-layer signal extraction**: The regex + weighted keyword approach is a practical middle ground between pure regex (brittle) and pure LLM (expensive). CE could adopt this for cost-sensitive deployments.
