# Cortex CE Go SDK — HTTP Server Demo

A thin HTTP facade over every [Go SDK](../../README.md) API method. Each route
validates its own input, calls exactly one SDK method, and returns JSON.

It is also the reference for how a Cortex CE integration should surface
failures — see [Error responses](#error-responses).

## Prerequisites

- Go 1.22+
- A running Cortex CE backend on `http://127.0.0.1:37777`
  (`bash scripts/start.sh --background` from the repository root)

## Quick Start

```bash
cd go-sdk/cortex-mem-go/examples/http-server

# Optional overrides; the defaults below match the backend and the demo port table.
export CORTEX_BASE_URL=http://127.0.0.1:37777
export PORT=37779

go run .
```

The server prints its base URL and endpoint list on startup. It binds `:37779`
by default — this is a dedicated demo port, not a common development port like
8080.

Stop it with `Ctrl-C`; the process shuts down gracefully and ignores the
`http.ErrServerClosed` that `Shutdown` produces.

## API Endpoints

| Method | Path | Notes |
|--------|------|-------|
| GET | `/health` | Demo liveness; `503` when the SDK health check fails |
| POST | `/chat` | Records a tool observation, then returns a text completion |
| GET | `/version` | Backend version |
| GET | `/search` | `project` required; `limit` 0–100, `offset` ≥ 0; forwards `query`, `type`, `concept`, `source`, `orderBy` |
| GET | `/experiences` | `project` and `task` required; `count` 0–100 |
| GET | `/iclprompt` | `project` and `task` required; `maxChars` ≥ 0 |
| GET | `/observations` | `limit` 0–100, `offset` ≥ 0 |
| GET | `/observations/{id}` | `404` when the observation does not exist |
| PATCH | `/observations/{id}` | Body is an `ObservationUpdate` |
| DELETE | `/observations/{id}` | |
| POST | `/batch-observations` | `ids`, at most 100 |
| GET | `/projects` | |
| GET | `/stats` | Passes `project` through for project-scoped counts |
| GET | `/modes` | |
| GET | `/settings` | |
| GET | `/quality` | `project` required |
| GET | `/extraction/latest` | `template` and `project` required |
| GET | `/extraction/history` | `template` and `project` required; `limit` ≥ 0 |
| POST | `/extraction/run` | `project` required |
| POST | `/refine` | `project` required |
| POST | `/feedback` | `observationId` and `feedbackType` required |
| POST | `/session/start` | `session_id` and `project` required |
| PATCH | `/session/user` | `sessionId` and `userId` required |
| POST | `/create-observation` | Direct observation creation |
| POST | `/ingest/prompt` | Records a user prompt |
| POST | `/ingest/session-end` | Ends a session |

Request bodies are capped at 1 MB.

## Error responses

Every error is `{"error": "<message>"}`. The status code is the SDK's, not a
blanket 500:

| Situation | Status |
|-----------|--------|
| Local validation (`ValidationError`, bad JSON, missing/blank field, out-of-range `limit`/`offset`, unknown method) | `400` (or `405` for a wrong HTTP method) |
| Backend returned a status in `[400, 600)` | that status, passed through (`404`, `429`, `503`, …) |
| No usable status — connection refused, timeout, any other error | `500` |
| Demo health check reports the backend down | `503` |

The Python demo (`@app.errorhandler(APIError)`) and the JS demo (its express
error middleware) follow the same rule. Keeping the three aligned matters: a
client written against one demo should not see 500 where another returns 404.

`writeSDKError` in `main.go` is the single place this mapping happens;
`main_test.go` pins it, including the out-of-range case where an API status must
*not* be reflected verbatim.

Two routes keep bespoke messages on purpose, because their 404 means "no such
observation" rather than "the request was wrong":

- `GET /observations/{id}` — `{"error": "observation <id> not found"}`
- `POST /feedback` — `{"error": "observation <id> not found"}`

## Examples

```bash
# Health
curl http://localhost:37779/health

# Search with ordering
curl "http://localhost:37779/search?project=/tmp&query=auth&limit=5&orderBy=created_at"

# Project-scoped stats
curl "http://localhost:37779/stats?project=/tmp"

# Start a session
curl -X POST http://localhost:37779/session/start \
  -H 'Content-Type: application/json' \
  -d '{"session_id":"demo-1","project":"/tmp"}'

# Validation error surfaces as 400, not 500
curl -i "http://localhost:37779/search"          # project is required
```

## See also

- [Go SDK README](../../README.md) — methods, options, framework integrations
- [Go SDK demo guide](../../../../docs/go-sdk-demo-guide.md)
- [Go SDK design document](../../../../docs/drafts/go-sdk-design.md)
