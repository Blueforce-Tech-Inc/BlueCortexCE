# JS SDK HTTP Server Demo

An Express HTTP server exposing 26 REST endpoints, including its own `/health` endpoint, backed by the Cortex CE SDK.

Mirrors the Go `http-server` and Python Flask demos.

## Quick Start

```bash
# Install dependencies (from SDK root)
npm install express
npm install -D @types/express

# Set backend URL (optional, defaults to http://127.0.0.1:37777)
export CORTEX_BASE_URL=http://127.0.0.1:37777

# The example uses a dedicated port to avoid common development ports.
export PORT=37781

# Run
npx tsx examples/http-server/app.ts
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check |
| POST | `/chat` | `project` and `message` required; body `maxChars` 0–100000 (0 = backend default ~4000), non-integer or negative returns `400` |
| GET | `/search` | Search observations |
| GET | `/version` | Backend version |
| GET | `/experiences` | Retrieve experiences |
| GET | `/iclprompt` | Build ICL prompt |
| GET | `/observations` | List observations |
| POST | `/observations/batch` | Batch get observations by IDs |
| POST | `/observations/create` | Record observation (tool-use path) |
| GET | `/observations/:id` | Get observation by ID |
| PATCH | `/observations/:id` | Update observation |
| DELETE | `/observations/:id` | Delete observation |
| POST | `/session/start` | Start/resume session |
| PATCH | `/session/user` | Update session user ID |
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
| POST | `/ingest/prompt` | Ingest user prompt |
| POST | `/ingest/session-end` | Ingest session end |

> **`/observations/create` does not persist `source` or `extractedData`.** It calls
> the SDK's `recordObservation`, which posts to `POST /api/ingest/tool-use`; the
> backend accepts those two fields there and then discards them, because its
> tool-use handler reads only `session_id`, `cwd`, `tool_name`, `tool_input`,
> `tool_response` and `platform_source`. Use `PATCH /observations/:id` to set
> `source` and `extractedData` on an existing observation — that path does
> persist them.

## Examples

```bash
# Health check
curl http://localhost:37781/health

# Chat with memory
curl -X POST http://localhost:37781/chat \
  -H 'Content-Type: application/json' \
  -d '{"project": "/my/project", "message": "How do I parse JSON?"}'

# Search observations
curl "http://localhost:37781/search?project=/my/project&query=error+handling"

# Get experiences with source filter
curl "http://localhost:37781/experiences?project=/my/project&task=debugging&source=manual"

# Update observation
curl -X PATCH http://localhost:37781/observations/obs-123 \
  -H 'Content-Type: application/json' \
  -d '{"title": "Fixed", "source": "verified"}'
```

## Error responses

Every error is `{"error": "<message>"}`, and every error is JSON — including
the ones a routing mistake produces. The status code is the SDK's, not a
blanket 500:

| Situation | Status |
|-----------|--------|
| Local validation (`ValidationError`, bad JSON, missing/blank field, out-of-range `limit`/`offset`) | `400` |
| Wrong HTTP method on a path that exists | `405`, with an `Allow` header listing the methods that would have worked |
| Unknown path | `404` — `{"error": "not found: <path>"}` |
| Backend returned a status in `[400, 600)` | that status, passed through (`404`, `429`, `503`, …) |
| No usable status — connection refused, timeout, any other error | `500` |
| Demo health check reports the backend down | `503` |

The Go, Python and Java demos answer the same statuses for the same mistakes.
Keeping the four aligned matters: a client written against one demo should not
see 500 where another returns 404.
