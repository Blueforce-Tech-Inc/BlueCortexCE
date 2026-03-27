# Cortex CE Python SDK

Python SDK for the [Cortex CE](https://github.com/abforce/cortex-ce) persistent memory system.

## Installation

```bash
pip install -e ./python-sdk/cortex-mem-python
```

## Quick Start

```python
from cortex_mem import CortexMemClient

with CortexMemClient(base_url="http://localhost:37777") as client:
    # Start session
    session = client.start_session("my-session", "/path/to/project")

    # Record observation (fire-and-forget)
    client.record_observation(
        session_id=session.session_id,
        project_path="/path/to/project",
        tool_name="Read",
        tool_input={"file": "main.py"},
    )

    # Retrieve experiences
    experiences = client.retrieve_experiences(
        task="How to handle errors?",
        project="/path/to/project",
        count=3,
    )

    # Build ICL prompt
    result = client.build_icl_prompt(
        task="How to handle errors?",
        project="/path/to/project",
    )

    # End session
    client.record_session_end(session_id=session.session_id, project_path="/path/to/project")
```

## API Reference

### Session

| Method | Description |
|--------|-------------|
| `start_session(session_id, project_path, user_id=None)` | Start or resume a session |
| `update_session_user_id(session_id, user_id)` | Update session user ID |

### Capture (fire-and-forget)

| Method | Description |
|--------|-------------|
| `record_observation(session_id, project_path, tool_name, **kwargs)` | Record a tool-use observation |
| `record_session_end(session_id, project_path, ...)` | Signal session end |
| `record_user_prompt(session_id, prompt_text, ...)` | Record a user prompt |

### Retrieval

| Method | Description |
|--------|-------------|
| `retrieve_experiences(task, project, **kwargs)` | Retrieve relevant experiences |
| `build_icl_prompt(task, project, **kwargs)` | Build an ICL prompt |
| `search(project, **kwargs)` | Semantic search |
| `list_observations(project, **kwargs)` | List observations with pagination |
| `get_observations_by_ids(ids)` | Batch get observations by IDs |

### Management

| Method | Description |
|--------|-------------|
| `trigger_refinement(project_path)` | Trigger memory refinement |
| `submit_feedback(observation_id, feedback_type, comment="")` | Submit feedback |
| `update_observation(observation_id, update=None, **kwargs)` | Update an observation (supports dataclass or kwargs) |
| `delete_observation(observation_id)` | Delete an observation |
| `get_quality_distribution(project_path)` | Get quality distribution |

#### ObservationUpdate — Dual-Mode Support

The `update_observation` method supports two calling styles:

```python
from cortex_mem import ObservationUpdate

# Style 1: Dataclass (recommended — IDE autocomplete + type checking)
update = ObservationUpdate(title="New Title", source="manual", extracted_data={"pref": "dark"})
client.update_observation("obs-123", update)

# Style 2: Kwargs (convenience)
client.update_observation("obs-123", title="New Title", source="manual")

# Style 3: Both (kwargs override dataclass fields)
update = ObservationUpdate(title="From Dataclass")
client.update_observation("obs-123", update, title="From Kwargs")
```

Supported fields: `title`, `subtitle`, `content`, `narrative`, `facts`, `concepts`, `source`, `extracted_data`.
Only non-None fields are sent to the backend (PATCH semantics).

### Health / Extraction / Version

| Method | Description |
|--------|-------------|
| `health_check()` | Check backend health |
| `trigger_extraction(project_path)` | Trigger extraction |
| `get_latest_extraction(project_path, template_name, ...)` | Get latest extraction |
| `get_extraction_history(project_path, template_name, ...)` | Get extraction history |
| `get_version()` | Get backend version |
| `get_projects()` | Get all projects |
| `get_stats(project_path="")` | Get statistics |
| `get_modes()` | Get mode settings |
| `get_settings()` | Get current settings |

## Error Handling

```python
from cortex_mem import CortexMemClient, NotFoundError, RateLimitError, APIError

try:
    client.delete_observation("nonexistent")
except NotFoundError:
    print("Observation not found")
except RateLimitError:
    print("Rate limited, retry later")
except APIError as e:
    print(f"API error {e.status_code}: {e.message}")
```

## Design Principles

1. **Zero forced dependencies** — only `requests` required
2. **Idiomatic Python** — dataclasses, kwargs, context manager
3. **Compatible with Go/Java SDK** — all 26 API methods covered
4. **Fire-and-forget capture** — capture operations retry internally and swallow errors

## Wire Format

The SDK handles wire format differences automatically:

- `project_path` → `cwd` in observation/session-end endpoints
- `project_path` → `project_path` in session-start endpoint
- `extracted_data` → `extractedData` (camelCase)
- `required_concepts` → `requiredConcepts` (camelCase)

See [design document](../../docs/drafts/python-sdk-design.md) for details.
