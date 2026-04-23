# Tool Result Persistence — 3-Layer Context Budget Defense

> **Source**: `tools/tool_result_storage.py` (226 lines) + `tools/budget_config.py` (62 lines)
> **Last updated**: 2026-04-23
> **Status**: Upstream analysis (hermes-agent `HEAD` as of 2026-04-19)

---

## 1. Problem Statement

Tool outputs can be enormous (multi-MB file contents, long search results). If injected raw into the conversation context, they cause:

1. **Context window overflow** — exceeds model token limit
2. **Cost explosion** — every token in context is billed per-turn
3. **Attention dilution** — important information buried in noise

Hermes addresses this with a **3-layer defense** that operates at different granularities.

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                   Tool Execution                        │
│  (search_files, read_file, exec, etc.)                  │
└──────────────────┬──────────────────────────────────────┘
                   │ raw output
                   ▼
┌─────────────────────────────────────────────────────────┐
│  Layer 1: Per-tool self-truncation                      │
│  (inside each tool implementation)                      │
│  - Tools pre-truncate before returning                  │
│  - First line of defense; controlled by tool author     │
└──────────────────┬──────────────────────────────────────┘
                   │ potentially still large
                   ▼
┌─────────────────────────────────────────────────────────┐
│  Layer 2: Per-result persistence (maybe_persist)        │
│  - If output > tool's threshold → write to sandbox     │
│  - Replace in-context with preview + file path          │
│  - Model can read_file to access full output            │
└──────────────────┬──────────────────────────────────────┘
                   │ all results in a single turn
                   ▼
┌─────────────────────────────────────────────────────────┐
│  Layer 3: Per-turn aggregate budget (enforce_turn_budget)│
│  - If total chars > TURN_BUDGET (200K)                 │
│  - Persist largest non-persisted results until under    │
│  - Catches "death by a thousand cuts" scenario          │
└─────────────────────────────────────────────────────────┘
```

---

## 3. Layer 1: Per-Tool Self-Truncation

**Where**: Inside each tool implementation (e.g., `search_files`, `exec`).

**Pattern**: Tools pre-truncate their output before returning to the agent loop. This is the only layer the tool author controls.

**Example**: `search_files` might limit results to N matches; `exec` might cap stdout at a configured size.

**CE analogy**: Equivalent to capping `Observation` content size at the point of creation.

---

## 4. Layer 2: Per-Result Persistence

**Function**: `maybe_persist_tool_result(content, tool_name, tool_use_id, env, config, threshold)`

**Flow**:

1. Resolve effective threshold for this tool name:
   - `PINNED_THRESHOLDS` (e.g., `read_file = inf` — prevents persist→read→persist loops)
   - Tool-specific overrides from config
   - Registry's `get_max_result_size()`
   - Default: `DEFAULT_RESULT_SIZE_CHARS` = **100,000 chars**

2. If `len(content) <= threshold` → return as-is (no persistence needed)

3. Otherwise:
   - Generate preview (first `DEFAULT_PREVIEW_SIZE_CHARS` = **1,500 chars**, truncated at last newline)
   - Write full content to sandbox via `env.execute()` (heredoc pattern)
   - Storage path: `$TMPDIR/hermes-results/{tool_use_id}.txt`
   - Return `<persisted-output>` block with:
     - Size info (KB/MB)
     - File path
     - Instructions to use `read_file` with offset/limit
     - Preview snippet

4. If sandbox write fails → fallback to inline truncation (preview + size warning)

**Key design decisions**:

- **`read_file` pinned at `inf`**: Prevents infinite persist→read→persist loops. If read_file output were persisted, the agent would need to read_file again to see it, creating infinite recursion.
- **Sandbox-based storage**: File is written via `env.execute()`, making it accessible from any backend (local, Docker, SSH, Modal, Daytona).
- **Preview preserves context model**: The agent still sees *something* in-context, maintaining conversation flow while giving it a path to access the full output on demand.

---

## 5. Layer 3: Per-Turn Aggregate Budget

**Function**: `enforce_turn_budget(tool_messages, env, config)`

**Trigger**: After all tool results in a single assistant turn are collected.

**Budget**: `DEFAULT_TURN_BUDGET_CHARS` = **200,000 chars** (~50K tokens)

**Algorithm**:

1. Calculate total size of all tool results in the turn
2. If `total <= budget` → no action needed
3. Otherwise:
   - Collect all non-persisted results (candidates)
   - Sort by size descending
   - Persist largest candidates one by one until total is under budget
   - Skip already-persisted results (from Layer 2)

**Why descending order**: Persists the biggest offenders first for maximum budget recovery per operation.

**Edge case**: Handles the "death by a thousand cuts" scenario where many medium-sized results individually pass Layer 2 but collectively exceed the turn budget.

---

## 6. Budget Configuration

**File**: `tools/budget_config.py`

```python
@dataclass(frozen=True)
class BudgetConfig:
    default_result_size: int = 100_000      # chars
    turn_budget: int = 200_000              # chars
    preview_size: int = 1_500               # chars
    tool_overrides: Dict[str, int] = {}     # per-tool thresholds
```

**Threshold resolution order**:
1. `PINNED_THRESHOLDS` (hardcoded, cannot override)
2. `config.tool_overrides` (env-configurable)
3. `registry.get_max_result_size()` (tool-registered)
4. `config.default_result_size` (fallback)

**Pinned thresholds**:
- `read_file` → `float("inf")` — **Never persist read_file output**. Critical for preventing loops.

---

## 7. Persisted Output Block Format

```xml
<persisted-output>
This tool result was too large (1,234,567 characters, 1.2 MB).
Full output saved to: /tmp/hermes-results/abc123.txt
Use the read_file tool with offset and limit to access specific sections of this output.

Preview (first 1500 chars):
[preview content here]...
</persisted-output>
```

The model sees this and understands:
- The output was large (why it was persisted)
- Where to find the full output (how to access it)
- How to read specific parts (offset/limit pattern)
- A preview of the content (immediate context)

---

## 8. CE Implications

### 8.1 Direct Applicability

| Concept | CE Translation |
|---------|---------------|
| Per-tool self-truncation | Cap `Observation` content at creation time |
| Per-result persistence | Store large tool outputs in DB, inject summary + ID into context |
| Per-turn aggregate budget | Context generation step enforces total size limit |
| Preview pattern | Always include a snippet when truncating; don't silently drop |
| `read_file` pinning at `inf` | Prevent recursive read→persist→read cycles in CE tooling |

### 8.2 Key Insight: Content Is Never Lost

The system **never silently drops content**. It always:
1. Writes the full output somewhere accessible
2. Provides a preview in-context
3. Gives the model a path to retrieve the full output

This is a UX pattern, not just a technical one. The model *knows* it can access the full output, so it doesn't hallucinate or guess.

### 8.3 Sandbox Abstraction

Hermes writes to `$TMPDIR/hermes-results/` via `env.execute()`. CE could:
- Store large outputs in a dedicated table (e.g., `ToolResultEntity`)
- Reference by ID in context injection
- Provide a retrieval endpoint (`/api/tool-results/{id}`)

This aligns with CE's existing pattern of storing structured data in PostgreSQL and injecting summaries into context.

---

## 9. Comparison with CE's Current Approach

| Aspect | Hermes | CE (Current) |
|--------|--------|--------------|
| Context budget | 200K chars per turn | No explicit budget (configurable max tokens) |
| Large output handling | Persist + preview | Truncation at Observation creation |
| Retrieval path | `read_file` tool | N/A (truncated content is gone) |
| Per-tool thresholds | Configurable + pinned | One-size-fits-all |
| Aggregate budget | Yes (Layer 3) | No |

**Gap**: CE currently truncates large observations without storing the full content. The "persist + preview" pattern could be valuable for CE's context generation.

---

## 10. References

- `tools/tool_result_storage.py` — 3-layer persistence implementation
- `tools/budget_config.py` — Budget configuration dataclass
- `tools/registry.py` — Tool registry with per-tool `max_result_size`
- Related: `60-evolution/08-builtin-memory-tool-bounded-snapshot.md` (memory tool's own output management)
- Related: `40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md` (context injection)
