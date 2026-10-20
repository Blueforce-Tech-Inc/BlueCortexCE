# Python SDK HTTP Server Demo

A Flask HTTP server exposing 26 REST endpoints, including its own `/health` endpoint, backed by the Cortex CE SDK.

Mirrors the Go `http-server` example.

## Quick Start

```bash
# Install dependencies
pip install -r requirements.txt

# Set backend URL (optional, defaults to http://127.0.0.1:37777)
export CORTEX_BASE_URL=http://127.0.0.1:37777

# The example uses a dedicated port to avoid common development ports.
export PORT=37780

# Run
python3 app.py
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check |
| POST | `/chat` | `project` and `message` required; body `maxChars` ≥ 0 (0 = backend default ~4000), non-integer or negative returns `400` |
| GET | `/search` | Search observations |
| GET | `/version` | Backend version |
| GET | `/experiences` | Retrieve experiences |
| GET | `/iclprompt` | Build ICL prompt |
| GET | `/observations` | List observations |
| GET | `/observations/{id}` | Get observation by ID |
| POST | `/observations/batch` | Batch get observations by IDs |
| POST | `/observations/create` | Record observation (tool-use path) |
| PATCH | `/observations/{id}` | Update observation |
| DELETE | `/observations/{id}` | Delete observation |
| GET | `/projects` | Get projects |
| GET | `/stats` | Get stats |
| GET | `/modes` | Get modes |
| GET | `/settings` | Get settings |
| GET | `/quality` | Quality distribution |
| GET | `/extraction/latest` | Latest extraction result |
| GET | `/extraction/history` | Extraction history |
| POST | `/extraction/run` | Trigger extraction |
| POST | `/refine` | Trigger memory refinement |
| POST | `/feedback` | Submit observation feedback |
| POST | `/session/start` | Start or resume session |
| PATCH | `/session/user` | Update session user ID |
| POST | `/ingest/prompt` | Ingest user prompt |
| POST | `/ingest/session-end` | Ingest session end |

> **`/observations/create` does not persist `source` or `extractedData`.** It calls
> the SDK's `record_observation`, which posts to `POST /api/ingest/tool-use`; the
> backend accepts those two fields there and then discards them, because its
> tool-use handler reads only `session_id`, `cwd`, `tool_name`, `tool_input`,
> `tool_response` and `platform_source`. Use `PATCH /observations/{id}` to set
> `source` and `extractedData` on an existing observation — that path does
> persist them.

## Examples

```bash
# Health check
curl http://localhost:37780/health

# Chat with memory
curl -X POST http://localhost:37780/chat \
  -H 'Content-Type: application/json' \
  -d '{"project": "/my/project", "message": "How do I parse JSON?"}'

# Search observations
curl "http://localhost:37780/search?project=/my/project&query=error+handling"

# Get experiences with source filter
curl "http://localhost:37780/experiences?project=/my/project&task=debugging&source=manual"

# Update observation
curl -X PATCH http://localhost:37780/observations/obs-123 \
  -H 'Content-Type: application/json' \
  -d '{"title": "Fixed", "source": "verified"}'
```
