# Session Search Tool — FTS5 + LLM Long-Term Recall

> **Source**: `tools/session_search_tool.py` (554 lines)
> **Last updated**: 2026-04-23
> **Status**: Upstream analysis (hermes-agent `HEAD` as of 2026-04-19)

---

## 1. Problem Statement

The agent needs to recall information from **past sessions** — conversations that happened days, weeks, or months ago. This is distinct from:

- **In-session memory** (current conversation context)
- **Structured memory** (MemoryProvider / MemoryStore — facts, preferences)
- **File-based memory** (CLAUDE.md, project files)

Session Search solves: *"What did we do about X last month?"*

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                   Agent Loop                            │
│  (user says "remember when we fixed the Docker issue?") │
└──────────────────┬──────────────────────────────────────┘
                   │ calls session_search(query="Docker issue")
                   ▼
┌─────────────────────────────────────────────────────────┐
│  Mode Router                                            │
│  ├─ Empty query → _list_recent_sessions()               │
│  │   (zero LLM cost, just DB metadata)                  │
│  └─ With query → FTS5 search + summarize pipeline       │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│  FTS5 Search (db.search_messages)                       │
│  - Full-text search across all session transcripts      │
│  - Ranked by relevance                                  │
│  - Returns top 50 matches                               │
└──────────────────┬──────────────────────────────────────┘
                   │ raw matches
                   ▼
┌─────────────────────────────────────────────────────────┐
│  Session Dedup & Lineage Resolution                     │
│  - Resolve child sessions to parent (delegation chain)  │
│  - Skip current session lineage                         │
│  - Deduplicate by parent session ID                     │
│  - Take top N unique sessions (default 3, max 5)        │
└──────────────────┬──────────────────────────────────────┘
                   │ unique sessions
                   ▼
┌─────────────────────────────────────────────────────────┐
│  Parallel Summarization (asyncio.gather)                │
│  - For each session:                                    │
│    1. Load full conversation transcript                 │
│    2. Truncate around match positions (smart window)    │
│    3. Send to auxiliary LLM (cheap model)               │
│    4. Generate focused summary                          │
└──────────────────┬──────────────────────────────────────┘
                   │ per-session summaries
                   ▼
┌─────────────────────────────────────────────────────────┐
│  Result Assembly                                        │
│  - Session metadata (when, source, model)               │
│  - LLM-generated summary (or raw preview fallback)      │
│  - JSON response to agent                               │
└─────────────────────────────────────────────────────────┘
```

---

## 3. Two Modes

### 3.1 Recent Sessions Mode (No Query)

**Trigger**: Empty or missing `query` parameter.

**Behavior**:
- Zero LLM cost — pure DB query
- Returns metadata: title, preview, timestamps, message count
- Excludes current session lineage (already in context)
- Excludes child/delegation sessions (show parent only)
- Excludes sessions with `source="tool"` (third-party integrations)

**Use case**: "What were we working on recently?"

### 3.2 Keyword Search Mode (With Query)

**Trigger**: Non-empty `query` parameter.

**Behavior**:
- FTS5 full-text search across all session transcripts
- Deduplicates by parent session
- Summarizes top matches with auxiliary LLM
- Returns focused summaries + metadata

**Use case**: "What did we do about the Docker networking issue?"

---

## 4. Smart Truncation Strategy

The `_truncate_around_matches()` function is particularly clever. When a conversation transcript is too large (MAX_SESSION_CHARS = 100K), it:

### 4.1 Position Discovery (Priority Order)

1. **Full-phrase search**: Find exact matches of the query string
2. **Proximity co-occurrence**: Find positions where all query terms appear within 200 chars of each other
3. **Individual terms**: Fall back to finding any term occurrence

### 4.2 Window Selection

- Collect all candidate positions
- For each position, calculate a window (25% before, 75% after)
- Pick the window that covers the most match positions
- Ensure the window doesn't exceed MAX_SESSION_CHARS

**Result**: The truncated transcript maximizes coverage of relevant content around the search query.

---

## 5. Parallel Summarization

**Key design decision**: Summarize all sessions **in parallel** using `asyncio.gather()`.

**Why**:
- LLM calls are I/O-bound (network latency dominates)
- 3-5 sessions × 1 serial call each = too slow
- Parallel execution reduces wall-clock time significantly

**Implementation detail**:
- Uses `_run_async()` instead of `asyncio.run()` to avoid event loop conflicts
- Previous pattern (`asyncio.run()` in ThreadPoolExecutor) caused deadlocks in gateway mode (#2681)
- 60-second timeout with graceful fallback

**Fallback on failure**:
- If summarization fails → return raw 500-char preview
- If no auxiliary model available → return raw preview
- Never silently drop a matching session

---

## 6. Auxiliary Model Pattern

**Function**: `async_call_llm(task="session_search", ...)`

**Key insight**: Session summarization uses a **cheap/fast auxiliary model**, not the main agent model.

**Benefits**:
- Summarization is a well-defined task — doesn't need the most capable model
- Cost: ~$0.001 per summary vs ~$0.01+ for main model
- Speed: Auxiliary models (e.g., Gemini Flash) are typically faster
- Isolation: Summarization failures don't affect the main agent's reasoning

**CE analogy**: Could use a smaller/cheaper model for context generation, observation summarization, etc.

---

## 7. Delegation Chain Resolution

**Problem**: Delegation and compression create child sessions. The user's conversation is in the parent, but detailed content may be in children.

**Solution**: `_resolve_to_parent()` walks the `parent_session_id` chain to find the root session.

```python
def _resolve_to_parent(session_id: str) -> str:
    visited = set()
    sid = session_id
    while sid and sid not in visited:
        visited.add(sid)
        session = db.get_session(sid)
        parent = session.get("parent_session_id") if session else None
        sid = parent if parent else None
    return max(visited, key=len) if visited else session_id
```

**Why `max(visited, key=len)`**: In case of cycles or data issues, pick the longest session ID (most likely to be the real parent).

---

## 8. Tool Schema Design

The tool schema is carefully designed for **proactive use**:

```python
SESSION_SEARCH_SCHEMA = {
    "name": "session_search",
    "description": (
        "Search your long-term memory of past conversations... "
        "USE THIS PROACTIVELY when: "
        "- The user says 'we did this before', 'remember when', 'last time' "
        "- The user references a project, person, or concept that seems familiar "
        "- You want to check if you've solved a similar problem before "
        "..."
    ),
    "parameters": {
        "query": {"description": "...Omit this parameter entirely to browse recent sessions..."},
        "role_filter": {"description": "Optional: only search specific roles..."},
        "limit": {"default": 3, "description": "Max sessions to summarize (default: 3, max: 5)"},
    },
    "required": [],  # query is OPTIONAL — empty = recent mode
}
```

**Key design decisions**:
- `query` is **optional** (not in `required`) — enables recent sessions mode
- Description explicitly tells the agent **when to use proactively**
- Limit capped at 5 to prevent excessive LLM calls
- Search syntax documented: `OR` for broad recall, phrases for exact match, boolean

---

## 9. Hidden Sessions Filter

```python
_HIDDEN_SESSION_SOURCES = ("tool",)
```

Third-party integrations (e.g., Paperclip agents) tag their sessions with `HERMES_SESSION_SOURCE=tool`. These are excluded from search results to avoid cluttering the user's session history.

**CE implication**: If CE supports multi-agent or integration scenarios, consider session source tagging for similar filtering.

---

## 10. CE Implications

### 10.1 Direct Applicability

| Concept | CE Translation |
|---------|---------------|
| FTS5 search | PostgreSQL `tsvector` + `GIN` index on `UserPromptEntity` / `ObservationEntity` |
| Smart truncation | Position-aware windowing in context generation |
| Parallel summarization | Async context generation with multiple LLM calls |
| Auxiliary model | Use cheaper model for summarization tasks |
| Delegation chain | Session hierarchy with parent linkage |
| Recent sessions mode | Zero-cost metadata endpoint (`/api/sessions/recent`) |
| Hidden sessions filter | Session source tagging for multi-agent scenarios |

### 10.2 Key Insight: "Persist + Summarize" Pattern

Hermes doesn't just search and return raw transcripts. It:
1. Finds matching sessions (FTS5)
2. Truncates smartly around matches
3. Summarizes with a cheap LLM
4. Returns focused summaries

This is a **retrieval-augmented generation** pattern applied to conversation history. CE could apply the same pattern to its observation/search pipeline.

### 10.3 Two-Mode Design

The "empty query = recent sessions" pattern is elegant:
- Zero cost when user just wants to browse
- Full search when user has a specific query
- Same tool, different behavior based on input

CE could implement a similar pattern in its search API:
- `GET /api/search` (no query) → recent observations
- `GET /api/search?q=...` → full semantic search

---

## 11. Comparison with CE's Current Approach

| Aspect | Hermes | CE (Current) |
|--------|--------|--------------|
| Long-term recall | FTS5 + LLM summarization | Semantic search (`/api/memory/search`) |
| Recent browsing | Zero-cost metadata mode | No equivalent (must query) |
| Smart truncation | Position-aware windowing | Simple pagination |
| Auxiliary model | Cheap model for summaries | Main model for all tasks |
| Session lineage | Parent resolution + dedup | Flat session model |
| Proactive use | Tool schema encourages it | No proactive guidance |

**Gap**: CE's semantic search is powerful but lacks the "recent sessions browsing" mode and the "summarize before returning" pattern.

---

## 12. References

- `tools/session_search_tool.py` — Session search implementation (554 lines)
- `tools/budget_config.py` — Related: tool result budget management
- Related: `60-evolution/12-upstream-hermes-agent-memory-snapshot.md` (MemoryStore context)
- Related: `60-evolution/13-run-agent-memory-wiring-snapshot.md` (run_agent integration)
- Related: `20-recommendations/02-bluecortexce-recommendations.md` (CE recommendations)
