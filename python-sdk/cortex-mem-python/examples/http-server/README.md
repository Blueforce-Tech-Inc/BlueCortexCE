# Python SDK HTTP Server Demo

A Flask HTTP server that exposes all 26 Cortex CE SDK methods as REST endpoints.

Mirrors the Go `http-server` example.

## Quick Start

```bash
# Install dependencies
pip install -r requirements.txt

# Set backend URL (optional, defaults to http://127.0.0.1:37777)
export CORTEX_BASE_URL=http://127.0.0.1:37777

# Set port (optional, defaults to 8080)
export PORT=8080

# Run
python app.py
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check |
| POST | `/chat` | Chat with memory |
| GET | `/search` | Search observations |
| GET | `/version` | Backend version |
| GET | `/experiences` | Retrieve experiences |
| GET | `/iclprompt` | Build ICL prompt |
| GET | `/observations` | List observations |
| POST | `/observations/batch` | Batch get observations by IDs |
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
| PATCH | `/session/user` | Update session user ID |
| PATCH | `/observations/{id}` | Update observation |
| DELETE | `/observations/{id}` | Delete observation |
| POST | `/observations/create` | Record observation |
| POST | `/ingest/prompt` | Ingest user prompt |
| POST | `/ingest/session-end` | Ingest session end |

## Examples

```bash
# Health check
curl http://localhost:8080/health

# Chat with memory
curl -X POST http://localhost:8080/chat \
  -H 'Content-Type: application/json' \
  -d '{"project": "/my/project", "message": "How do I parse JSON?"}'

# Search observations
curl "http://localhost:8080/search?project=/my/project&query=error+handling"

# Get experiences with source filter
curl "http://localhost:8080/experiences?project=/my/project&task=debugging&source=manual"

# Update observation
curl -X PATCH http://localhost:8080/observations/obs-123 \
  -H 'Content-Type: application/json' \
  -d '{"title": "Fixed", "source": "verified"}'
```
