# 56 — signals.js Reality Check: Doc 55 vs. Actual v1.47 Code

> ⚠️ **2026-05-03 时间戳勘误**：本文结论**对 v1.47 正确**（Layer 2/3 确实不存在于 v1.47）。但 v1.78 已引入三层架构，详见 [`73 三层信号提取架构现实核查 + 新机会信号`](./73-reality-check-three-layer-signals-and-new-opportunity-signals.md)。
>
> **Status**: ⚠️ **Doc 55 is inaccurate** — describes a v1.66 three-layer extraction that does NOT exist in the actual codebase.
> **Actual version**: signals.js last updated at `fbca5ab` (v1.39.0, pre-v1.47)
> **Filed**: 2026-04-25

---

## 1. The Problem: Doc 55 Does Not Match signals.js

Doc 55 (`55-signals-v166-three-layer-extraction.md`) was written based on a **different/future version** of `signals.js` that has **three layers**:

| Layer | Doc 55 Claims | signals.js Reality |
|-------|---------------|-------------------|
| Layer 1 | `_extractRegex()` — binary hit/miss | ✅ Single regex section (lines 50–160) |
| Layer 2 | `_extractKeywordScore()` — weighted scoring with `SIGNAL_PROFILES` | ❌ **Not present** |
| Layer 3 | `_extractLLM()` — LLM semantic extraction | ❌ **Not present** |
| Merge | `_mergeSignals(regexSignals, scoreSignals, llmSignals)` | ❌ **Not present** |
| Profiles | `SIGNAL_PROFILES` object (perf_bottleneck, capability_gap, etc.) | ❌ **Not present** |

**Git history confirms** signals.js has NOT been updated to a three-layer architecture:

```
$ git log --follow --oneline src/gep/signals.js | head -5
fbca5ab Release v1.39.0           ← last change to signals.js
39104b9 feat: Auto Skills upgrade
d665b4a feat(gep): add tool_bypass signal
17301b1 Release v1.36.0
dfc3de1 chore(release): prepare v1.36.0
```

The current signals.js is at **v1.39.0** code, NOT v1.66. The `SIGNAL_PROFILES`, `_extractKeywordScore`, `_extractLLM`, and `_mergeSignals` functions described in doc 55 **do not exist in the codebase**.

---

## 2. What signals.js Actually Contains (v1.39/v1.47)

The actual `signals.js` is a **single-layer, regex-heavy** extraction engine. Here is what it actually does:

### 2.1 Architecture Overview

```
Corpus (session + today log + memory + user)
    │
    ▼
analyzeRecentHistory(recentEvents)      ← de-duplication + history analysis
    │
    ▼
extractSignals({ recentSessionTranscript, todayLog, memorySnippet, userSnippet, recentEvents })
    │
    ├─ Defensive signals (errors, missing resources, Windows compat)
    ├─ Recurring error detection (pattern frequency ≥ 3)
    ├─ Opportunity signals (feature requests, improvements, perf, capability gaps)
    ├─ Tool usage analytics (high/repeated usage → signals)
    ├─ Tool bypass detection (exec → node/npx/curl/python)
    │
    ├─ History-aware suppression (signals in ≥3 of last 8 events suppressed)
    ├─ Repair loop injection (3+ consecutive repairs → force innovation)
    ├─ Empty cycle detection (≥50% of last 8 cycles zero-change → degrade)
    ├─ Saturation detection (5+ consecutive empty → steady-state mode)
    ├─ Failure streak awareness (3+ failures → conservative mode; 5+ → ban top gene)
    └─ Default fallback (no signals → `stable_success_plateau`)
```

**Key insight**: Despite the single-layer design, the signal prioritization is **sophisticated** — it uses:
- History-aware suppression (avoid over-processing same signals)
- Saturation detection (graceful degradation when evolver exhausts innovation space)
- Failure streak tracking (ban dominant gene after 5+ failures)
- Multi-language support (EN/ZH-CN/ZH-TW/JA for feature requests and improvements)

### 2.2 Signal Taxonomy (Actual)

#### Defensive / Error Signals
| Signal | Trigger | Regex Pattern |
|--------|---------|--------------|
| `log_error` | Error markers in corpus | `[error]\|error:\|exception:\|iserror":true\|status":"error"\|...` |
| `errsig:<line>` | First error line, clipped to 260 chars | `\b(typeerror\|referenceerror\|syntaxerror)\b\s:` |
| `recurring_error` | Same error pattern ≥3 times | pattern frequency analysis |
| `recurring_errsig(Nx):<key>` | Top recurring error with count | N≥3 |
| `memory_missing` | `memory.md missing` | literal |
| `user_missing` | `user.md missing` | literal |
| `session_logs_missing` | `no session logs found\|no jsonl files` | literal |
| `windows_shell_incompatible` | Win + `pgrep\|ps aux\|cat >\|heredoc` | platform check |
| `path_outside_workspace` | `path.resolve(__dirname, '../../../` | literal |
| `protocol_drift` | `prompt` without `evolutionevent` | pattern |
| `unsupported_input_type` | `unsupported mime\|invalid mime` | regex |

#### Opportunity Signals
| Signal | Trigger | Multi-language |
|--------|---------|----------------|
| `user_feature_request` | "add\|implement\|create\|build..." + feature keywords | EN/ZH-CN/ZH-TW/JA |
| `user_improvement_suggestion` | "should be\|could be better\|improve\|enhance..." | EN/ZH-CN/ZH-TW/JA |
| `perf_bottleneck` | `slow\|timeout\|latency\|bottleneck\|high cpu\|oom...` | EN only |
| `capability_gap` | `not supported\|cannot\|unsupported\|not implemented...` | EN only |

#### Tool Analytics Signals
| Signal | Trigger |
|--------|---------|
| `high_tool_usage:<tool>` | Tool used ≥10 times in corpus |
| `repeated_tool_usage:exec` | exec tool used ≥5 times |
| `tool_bypass` | exec contains `node .js\|npx \|curl \|python .py` |

#### Injected Signals (History-Aware)
| Signal | Trigger | Count |
|--------|---------|-------|
| `evolution_stagnation_detected` | All signals suppressed by history | — |
| `stable_success_plateau` | No actionable signals | — |
| `repair_loop_detected` | 3+ consecutive repair intent | ≥3 |
| `force_innovation_after_repair_loop` | After repair loop detection | — |
| `empty_cycle_loop_detected` | ≥50% empty cycles in last 8 | ≥4 |
| `force_steady_state` | 5+ consecutive empty cycles | ≥5 |
| `evolution_saturation` | 3+ or 5+ consecutive empty cycles | ≥3 |
| `consecutive_failure_streak_N` | N consecutive failures | ≥3 |
| `failure_loop_detected` | 5+ consecutive failures | ≥5 |
| `ban_gene:<gene>` | Top gene after 5+ failures | — |
| `high_failure_ratio` | ≥75% failure rate in last 8 | — |

### 2.3 History Analysis: analyzeRecentHistory()

This function (lines 26–106) is the **only** form of multi-pass processing:

```javascript
function analyzeRecentHistory(recentEvents) {
  // Only last 10 events considered
  var recent = recentEvents.slice(-10);
  
  // Count consecutive repair/failure/empty at tail
  var consecutiveRepairCount = 0;
  var consecutiveEmptyCycles = 0;
  var consecutiveFailureCount = 0;
  
  // Frequency: signal key → count in last 8 events
  var signalFreq = {};
  var geneFreq = {};
  
  // Suppress signals appearing in ≥3 of last 8 events
  var suppressedSignals = new Set();
  
  // Empty cycle count / ratio in last 8 events
  var emptyCycleCount = 0;
  
  // Failure ratio in last 8 events
  var recentFailureRatio = 0;
}
```

Returns 12 metrics used by `extractSignals` to make injection decisions. **This is the closest thing to "Layer 2 scoring" in the actual codebase** — but it's scoring signals from recent history, not from the corpus itself.

### 2.4 Saturation Detection (Key Design Pattern)

The actual signals.js has sophisticated **graceful degradation**:

```javascript
// 5+ consecutive empty cycles → force steady-state mode
if (history.consecutiveEmptyCycles >= 5) {
  signals.push('force_steady_state');
  signals.push('evolution_saturation');
}

// 3+ consecutive empty cycles → signal saturation
else if (history.consecutiveEmptyCycles >= 3) {
  signals.push('evolution_saturation');
}

// ≥50% empty in last 8 cycles → strip repair signals, force innovate
if (history.emptyCycleCount >= 4) {
  signals = signals.filter(s => s !== 'log_error' && !s.startsWith('errsig:'));
  signals.push('empty_cycle_loop_detected');
  signals.push('stable_success_plateau');
}
```

**This directly addresses the Echo-MingXuan failure** (Cycle #55 hit "no committable code changes" and load spiked to 1.30).

---

## 3. Why This Matters for BlueCortexCE

Doc 55 describes a sophisticated **three-layer extraction pipeline** that does not exist. If CE tries to implement "Layer 2 weighted keyword scoring" or "Layer 3 LLM extraction" based on doc 55, it would be implementing something not actually present in evolver.

**What IS present and worth borrowing**:
1. **History-aware suppression** (`analyzeRecentHistory`) — avoids over-processing same signals
2. **Saturation detection** — graceful degradation when innovation space is exhausted
3. **Failure streak → gene ban** — prevents repeating failed strategies
4. **Multi-language signal extraction** (EN/ZH-CN/ZH-TW/JA) — already 4 languages
5. **Saturation metric: consecutiveEmptyCycles** — better than simple count

**What is NOT real** (do not implement based on doc 55):
- `SIGNAL_PROFILES` with `keywords` + `threshold` weighted scoring
- `_extractLLM()` semantic extraction layer
- Three-layer merge with overlap analysis

---

## 4. Action Items

- [ ] **Correct doc 55** — either mark it as aspirational/future or delete it
- [ ] **Update doc 36** (memory-architecture-synthesis) signal taxonomy section to match actual signals.js
- [ ] **Consider implementing** `analyzeRecentHistory` equivalent in CE's signal extraction (history-aware suppression)
- [ ] **Multi-language expansion** — CE currently supports fewer languages for feature request detection

---

## Appendix: Actual signals.js Full Code Summary

| Section | Lines | Purpose |
|---------|-------|---------|
| `OPPORTUNITY_SIGNALS` constant | 1–14 | 14 opportunity signal names |
| `hasOpportunitySignal()` | 15–27 | Check if signals array contains opportunity signals |
| `analyzeRecentHistory()` | 29–106 | History analysis (de-duplication + streak detection) |
| `extractSignals()` | 108–400+ | Main extraction function |
| Module exports | last line | `{ extractSignals, hasOpportunitySignal, analyzeRecentHistory, OPPORTUNITY_SIGNALS }` |

**Total: ~444 lines** (not the v1.66 version doc 55 implies)
