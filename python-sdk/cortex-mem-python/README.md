> 中文版: [README-zh-CN.md](./README-zh-CN.md)

# Cortex CE Python SDK

Python SDK for the [Cortex CE](https://github.com/Blueforce-Tech-Inc/BlueCortexCE) persistent memory system.

## Features

- **Zero forced dependencies** — only `requests` required
- **Full API coverage** — 25 methods covering Session, Capture, Retrieval, Management, Extraction
- **435 unit tests** — Comprehensive coverage of client, DTO, and demo integration (211 client + 140 DTO + 84 demo)
- **Idiomatic Python** — dataclasses, kwargs, context manager
- **Wire format compatible** — JSON field names match backend API exactly
- **Fire-and-forget capture** — non-blocking observation recording with internal retry

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
| `get_observation(observation_id)` | Get a single observation by ID |
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

### Lifecycle

| Method | Description |
|--------|-------------|
| `close()` | Mark the client closed and release its HTTP session (see below for borrowed sessions) |

After `close()`, every method raises `CortexError("client is closed")`. The client
also works as a context manager, so the usual form needs no explicit call:

```python
with CortexMemClient(base_url="http://localhost:37777") as client:
    result = client.search(project="/my-project", query="auth", limit=5)
```

### Session ownership

`CortexMemClient(..., session=your_session)` borrows a `requests.Session` you
already have, which is the way to share a connection pool with the rest of your
application. Two things follow from that, and both are deliberate:

- **Headers are per-request, never written into the session.** The SDK sends
  `Accept`, `User-Agent` and `Authorization` on each call instead of mutating
  `session.headers`. A borrowed session is shared with all of your other HTTP
  traffic, so writing the API key into it would send that key to unrelated
  hosts. Your own session headers are never overwritten, and the owned session
  takes the same code path, so requests are byte-identical either way.
- **`close()` does not close a borrowed session.** It marks the client closed
  and releases the connection pool only when the SDK created the session. Closing
  a session you still own is your call.

### Configuration

All constructor arguments, with the defaults and floors applied in `client.py`:

| Argument | Default | Notes |
|----------|---------|-------|
| `base_url` | `http://127.0.0.1:37777` | A trailing slash is stripped |
| `timeout` | `30.0` | Per-request, **in seconds**; floored at `0.1` |
| `max_retries` | `3` | Total **attempts**, not retries (3 = 3 requests); floored at `1` |
| `retry_backoff` | `0.5` | Base backoff, **in seconds**; floored at `0.1` |
| `api_key` | *(none)* | Sent as `Authorization: Bearer <key>` on every request |
| `session` | *(new `requests.Session`)* | Borrowed if you pass one — see above |

**Units differ from the other SDKs, so a ported config value needs converting.**
Timeouts and backoff are seconds here; Go and Java take a `time.Duration` /
Spring `Duration` and JS takes milliseconds. `500ms` is `0.5` here, not `500`.
`max_retries` counts attempts, matching Java's `retry.max-attempts` and the Go
and JS names — all four default to 3.

Retries apply to the **fire-and-forget captures only** — `record_observation`,
`record_session_end`, `record_user_prompt`. The 3-attempt budget is spent on
transient failures and the error is then swallowed. No other method retries, so a
backend blip surfaces on the first attempt for the rest of the API. Go and JS
retry that same set of three and nothing else; the Java SDK retries **10** of its
25 methods, adding the two extraction reads, three mutations, and
`trigger_refinement` / `trigger_extraction`. That difference matters if you are
porting retry-avoidance code between SDKs.

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

### Malformed Response Bodies

`APIError` is raised only for HTTP statuses. A response whose body is **present
but unparseable** — an HTML error page served with a 200, which is what a reverse
proxy or gateway returns on failure — raises `CortexError` instead:

```python
from cortex_mem import CortexMemClient, APIError, CortexError

try:
    page = client.list_observations("myproject")
except APIError as e:
    print(f"backend returned {e.status_code}: {e.message}")
except CortexError as e:
    print(f"backend response was not usable: {e}")
```

This is deliberate. Returning an empty result instead made a failure
indistinguishable from "the backend genuinely has nothing": `start_session` handed
back a response with an empty `session_id` that a caller would then use for the
rest of the session, and every read method returned a well-formed empty object.
Go and JS raise on the same input.

A **genuinely empty** body (204 No Content, or zero-length content) is not an
error — it still yields each method's documented default.

### Response Size Limit

There is **no** response size cap in this SDK. `requests` buffers the whole body
before `resp.json()` parses it, so a very large response is bounded only by the
memory available to your process.

This is a deliberate difference from the Go and JS SDKs, which cap at 10 MiB and
raise an explicit error; the Python SDK does not, because `requests` gives no
portable hook for a streaming size check. Keep `limit` modest when searching or
listing large observation sets.

## Design Principles

1. **Zero forced dependencies** — only `requests` required
2. **Idiomatic Python** — dataclasses, kwargs, context manager
3. **Compatible with Go/Java SDK** — all 25 API methods covered
4. **Fire-and-forget capture** — capture operations retry internally and swallow errors

### Empty Updates Are Rejected

`update_observation` raises `ValidationError` when the update sets no field, and no
request is sent. The same rule and message apply in the Go, Java and JS SDKs:

```
cortex-ce: validation error on update: at least one field must be provided for update
```

It matters because a PATCH that sets nothing is a silent no-op on the wire: without
the check, a caller who assembled an empty update from user input would see the call
return normally and could not tell that nothing was written. Pass `extracted_data={}`
as a keyword argument to send an explicitly empty JSONB value — the dataclass form
`ObservationUpdate(extracted_data={})` omits it from the wire and is therefore empty.

### Required Arguments Are Checked Client-Side

Every argument below must be non-empty. The SDK raises `ValidationError` and sends no
request. The Go, Java and JS SDKs enforce exactly the same set.

| Method | Required arguments |
|--------|--------------------|
| `start_session` | `session_id`, `project_path` |
| `update_session_user_id` | `session_id`, `user_id` |
| `record_observation` | `session_id`, `project_path`, `tool_name` |
| `record_session_end` | `session_id`, `project_path` |
| `record_user_prompt` | `session_id`, `prompt_text`, `project_path` |
| `retrieve_experiences` | `task` |
| `build_icl_prompt` | `task` |
| `search` | `project` |
| `get_observation` | `observation_id` |
| `get_observations_by_ids` | `ids` — non-empty, at most 100, no blank element |
| `trigger_refinement` | `project_path` |
| `submit_feedback` | `observation_id`, `feedback_type` |
| `update_observation` | `observation_id`, plus at least one field to change |
| `delete_observation` | `observation_id` |
| `get_quality_distribution` | `project_path` |
| `trigger_extraction` | `project_path` |
| `get_latest_extraction` | `project_path`, `template_name` |
| `get_extraction_history` | `project_path`, `template_name`; `limit` must not be negative |

`record_user_prompt` requires `project_path` positionally or by keyword — it has no
default, so omitting it raises `TypeError` at the call site rather than quietly
sending an empty `cwd`.

The checks are not decoration, and the capture methods are the clearest case.
`record_observation` is fire-and-forget, so it swallows whatever the backend replies:
an empty `tool_name` comes back as `400 Missing required field: tool_name`, the SDK
logs it and returns `None`, and the caller concludes the observation was captured when
the server had just rejected it. An empty `project_path` is quieter still, because the
backend *accepts* it — the record is queued against no project and then appears in no
project-scoped query, with no error anywhere.

`search` has the same shape of hazard: the SDK always sends `project`, and
`GET /api/search?project=` answers `200` with an empty result set, so a caller who
forgot the argument would read "no matches" rather than "your call was malformed".

`retrieve_experiences` and `build_icl_prompt` are the one place where the required
table above is not a complete guide, because the hazard is on the *other* field.
Both signatures default `project` to `""` and neither validates it, while
`search` does validate its project. That is the wrong way round for these two:
`POST /api/memory/experiences` and `POST /api/memory/icl-prompt` pass the value
straight into the repository query with no cross-project branch, so an empty or
missing project matches nothing and returns `200` with an empty result instead of
an error. Verified live: omitting `project`, sending `""`, and sending a
non-existent path all return `200 []`, while a real path returns experiences. The
default makes it easy to write `retrieve_experiences(task="...")` and believe you
are searching globally; you are not.

`list_observations` and the argument-free getters (`get_stats`, `get_projects`,
`get_modes`, `get_settings`, `get_version`, `health_check`) require nothing.
`get_stats` takes an optional project filter.

That is a statement about required arguments, not about blast radius, and on
this one method the two point in opposite directions. `list_observations` is the
only retrieval method whose project filter **widens** instead of emptying: omit
`project` — and pass an empty string if you like, because all four SDKs convert
that to omission — and no `project` is sent at all, so the backend answers with
observations from **every project on the instance**. Verified live against a
populated backend: `GET /api/observations` with no `project` returned 16
distinct projects inside a single 100-item page. A *direct* HTTP call with a
literal `?project=` is the opposite case and returns nothing, because the
repository query tests `IS NULL` rather than blank — worth knowing before you
bypass the SDK.

So this is the mirror image of the two ICL endpoints above: there a blank
project silently empties the result, here it silently over-broadens it. In a
multi-tenant deployment that is cross-tenant exposure rather than a missing
answer, and no client-side check will catch it, because the call is
well-formed.

## Wire Format

The SDK handles wire format differences automatically:

- `project_path` → `cwd` in observation/session-end endpoints
- `project_path` → `project_path` in session-start endpoint
- `extracted_data` → `extractedData` (camelCase)
- `required_concepts` → `requiredConcepts` (camelCase)
- JSON-encoded string list fields (`facts`, `concepts`, `files_read`,
  `files_modified`) → Python `list[str]`
- Comma-separated string field (`refined_from_ids`) → Python `list[str]`

That first one is worth spelling out because it is not a guess: the backend
serializes those four JSONB columns as **strings**, so a live observation arrives
as `concepts: '["allergy","peanut"]'` rather than a JSON array. The SDK decodes
both shapes, so `observation.concepts` is always a real list. A string that is
not valid JSON degrades to a comma-separated split, matching the JS SDK.

`refined_from_ids` is a different case and is deliberately listed on its own: it
is a `TEXT` column, not JSONB, and holds comma-separated UUIDs
(`"uuid-1,uuid-2"`). Nothing JSON-encodes it — the backend joins the ids with
`,` — so the comma-separated split is the only path that applies. Grouping it
with the four above would misdescribe what the backend does.

See [design document](../../docs/drafts/python-sdk-design.md) for details.

## License

MIT
