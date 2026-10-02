# Go SDK Demo Guide

English

## Overview

The Go SDK ships with five demo projects that demonstrate different usage patterns. Each demo is a self-contained Go program in the `go-sdk/cortex-mem-go/examples/` directory.

| Demo | Directory | Description |
|------|-----------|-------------|
| **HTTP Server** | `http-server/` | Full REST API wrapping all SDK methods — 24 endpoints |
| **Basic** | `basic/` | Minimal SDK usage — session, observation, search |
| **Eino** | `eino/` | Eino framework retriever integration |
| **LangChainGo** | `langchaingo/` | LangChainGo memory integration |
| **Genkit** | `genkit/` | Genkit framework retriever integration |

**Prerequisites for all demos:**
- Cortex CE backend running on port 37777
- Go 1.21+

```bash
# Start the backend
cd backend
java -jar target/cortex-ce-*.jar

# Verify backend is healthy
curl http://127.0.0.1:37777/api/health
# Expected: {"service":"cortex-ce","status":"ok",...}
```

## HTTP Server Demo

The most comprehensive demo — a full HTTP server that wraps all 27 methods of the Go SDK `Client` interface, of which 25 are API methods served over 24 REST routes (`Close` and `String` are lifecycle/debug and have no route). It serves as a testing harness, development tool, and reference implementation.

### How to Run

```bash
cd go-sdk/cortex-mem-go/examples/http-server
go run .
```

**Output:**
```
🚀 Go SDK HTTP server starting on :37779 (backend: http://127.0.0.1:37777)
Endpoints:
  GET    /health              - Health check
  POST   /chat                - Chat with memory
  GET    /search              - Search observations
  ...
```

The server binds **37779** by default — a dedicated demo port, not a common
development port — with graceful shutdown on SIGINT/SIGTERM. Override it with
`PORT` (and the backend with `CORTEX_BASE_URL`) if 37779 is taken:

```bash
PORT=38080 CORTEX_BASE_URL=http://127.0.0.1:37777 go run .
```

Every `curl` example below uses 37779; substitute your `PORT` if you changed it.

### All 24 Endpoints

#### Health & Version

**GET /health** — Health check (proxies to backend)

```bash
curl http://localhost:37779/health
```

```json
{"service":"go-sdk-http-server","status":"ok","time":"2026-03-25T10:30:00+08:00"}
```

**GET /version** — Backend version info

```bash
curl http://localhost:37779/version
```

```json
{"version":"1.0.0","build":"abc1234"}
```

---

#### Session

**PATCH /session/user** — Update session user ID

```bash
curl -X PATCH http://localhost:37779/session/user \
  -H "Content-Type: application/json" \
  -d '{"session_id": "my-session", "user_id": "alice"}'
```

```json
{"sessionId":"my-session","userId":"alice"}
```

---

#### Search & Observations

**GET /search** — Semantic search over observations

```bash
curl "http://localhost:37779/search?project=/my-project&query=error+handling&limit=5"
```

```json
{
  "observations": [...],
  "strategy": "semantic",
  "fell_back": false,
  "count": 3
}
```

Query parameters:
- `project` (required) — project path
- `query` — search query
- `limit` — max results (default: 10)

**GET /observations** — List observations with pagination

```bash
curl "http://localhost:37779/observations?project=/my-project&limit=10"
```

```json
{
  "items": [...],
  "hasMore": false,
  "total": 42,
  "offset": 0,
  "limit": 10
}
```

**POST /observations/batch** — Get observations by IDs

```bash
curl -X POST http://localhost:37779/observations/batch \
  -H "Content-Type: application/json" \
  -d '{"ids": ["obs-1", "obs-2", "obs-3"]}'
```

```json
[{"id":"obs-1","type":"tool_use","content":"..."}, ...]
```

**PATCH /observation/patch** — Update an observation

```bash
curl -X PATCH http://localhost:37779/observation/patch \
  -H "Content-Type: application/json" \
  -d '{"id": "obs-123", "title": "Updated title", "source": "verified"}'
```

```json
{"status": "updated"}
```

**DELETE /observation/delete** — Delete an observation

```bash
curl -X DELETE "http://localhost:37779/observation/delete?id=obs-123"
# Returns: 204 No Content
```

---

#### Memory

**GET /experiences** — Retrieve relevant experiences

```bash
curl "http://localhost:37779/experiences?project=/my-project&task=fix+authentication+bug"
```

```json
{
  "experiences": [
    {"id":"exp-1","task":"fix authentication bug","strategy":"reuse","outcome":"Fixed token validation",...}
  ],
  "count": 1
}
```

**GET /iclprompt** — Build ICL prompt from experiences

```bash
curl "http://localhost:37779/iclprompt?project=/my-project&task=recommend+phone"
```

```json
{
  "prompt": "## Relevant Past Experiences\n\n...",
  "experienceCount": "3"
}
```

**GET /quality** — Quality distribution stats

```bash
curl "http://localhost:37779/quality?project=/my-project"
```

```json
{"project":"/my-project","high":15,"medium":8,"low":2,"unknown":5}
```

**POST /refine** — Trigger memory refinement

```bash
curl -X POST "http://localhost:37779/refine?project=/my-project"
```

```json
{"status": "refined"}
```

**POST /feedback** — Submit observation feedback

```bash
curl -X POST http://localhost:37779/feedback \
  -H "Content-Type: application/json" \
  -d '{"observation_id": "obs-123", "feedback_type": "useful", "comment": "Very helpful"}'
```

```json
{"status": "submitted"}
```

---

#### Extraction

**GET /extraction/latest** — Latest extraction result for a template

```bash
curl "http://localhost:37779/extraction/latest?template=user_preference&project=/my-project&userId=alice"
```

```json
{
  "templateName": "user_preference",
  "data": {
    "preferences": [
      {"category": "phone_brand", "value": "小米", "sentiment": "positive"}
    ]
  }
}
```

**GET /extraction/history** — Extraction history (all snapshots)

```bash
curl "http://localhost:37779/extraction/history?template=user_preference&project=/my-project&userId=alice&limit=5"
```

```json
[
  {"extractedAt":"2026-03-25T02:00:00Z","data":{...}},
  {"extractedAt":"2026-03-24T02:00:00Z","data":{...}}
]
```

**POST /extraction/run** — Manually trigger structured data extraction

```bash
curl -X POST "http://localhost:37779/extraction/run?project=/my-project"
```

```json
{"status":"triggered"}
```

---

#### Management

**GET /projects** — List all projects

```bash
curl http://localhost:37779/projects
```

**GET /stats** — Project statistics

```bash
curl "http://localhost:37779/stats?project=/my-project"
```

**GET /modes** — Memory mode settings

```bash
curl http://localhost:37779/modes
```

**GET /settings** — Current system settings

```bash
curl http://localhost:37779/settings
```

---

#### Ingest

**POST /chat** — Simulated chat (demonstrates StartSession + RecordObservation)

```bash
curl -X POST http://localhost:37779/chat \
  -H "Content-Type: application/json" \
  -d '{"project": "/my-project", "message": "Hello, remember this!"}'
```

```json
{"response":"Received: Hello, remember this!","project":"/my-project","timestamp":"2026-03-25T10:30:00+08:00"}
```

**POST /ingest/prompt** — Record a user prompt

```bash
curl -X POST http://localhost:37779/ingest/prompt \
  -H "Content-Type: application/json" \
  -d '{"project": "/my-project", "prompt": "How do I fix this?", "session_id": "my-session"}'
```

```json
{"status": "recorded"}
```

**POST /ingest/session-end** — Signal session end

```bash
curl -X POST http://localhost:37779/ingest/session-end \
  -H "Content-Type: application/json" \
  -d '{"project": "/my-project", "session_id": "my-session"}'
```

```json
{"status": "ended"}
```

### Use Cases

1. **Testing SDK functionality** — All 27 SDK methods are exposed as HTTP endpoints, making it easy to test with `curl`, Postman, or scripts
2. **E2E test target** — The `scripts/go-sdk-e2e-test.sh` script tests all endpoints against this server
3. **Development proxy** — Run the HTTP server alongside the backend to have a clean API surface
4. **Cross-language testing** — Use curl/HTTP to verify SDK behavior from non-Go environments

## Basic Demo

A minimal example demonstrating core SDK operations: start session, record observation, search, list, health check, and version.

### How to Run

```bash
cd go-sdk/cortex-mem-go/examples/basic
go run .
```

**Expected output:**
```
=== Starting session ===
Session started: go-demo-session-1711345200

=== Recording observation ===
Observation recorded successfully

=== Searching ===
Found 1 results (strategy: semantic)
  - demo_tool: ...

=== Listing observations ===
Total observations: 1

=== Getting version ===
Backend version: map[version:1.0.0 ...]

=== Health check ===
Health check passed!

✅ Go SDK basic demo completed!
```

### Code Walkthrough

The basic demo performs these steps:

1. **Create client** with default configuration (`http://127.0.0.1:37777`)
2. **Start session** with a timestamped session ID
3. **Record observation** — tool_use type with input/output data
4. **Wait 500ms** — allows fire-and-forget ingestion to complete
5. **Search** — find the recorded observation
6. **List observations** — paginated listing
7. **Get version** — backend version info
8. **Health check** — verify backend is healthy

This is the smallest complete example of SDK usage.

## Integration Demos

### Eino Demo

Demonstrates the Eino Retriever adapter — Cortex CE memory as an Eino-compatible retriever.

**How to run:**

```bash
cd go-sdk/cortex-mem-go/examples/eino
go run .
```

**What it does:**
1. Creates a `Retriever` with project path `/tmp/eino-demo`
2. Records three observations (facts about Eino, Cortex CE, and user preferences)
3. Waits for ingestion
4. Uses `retriever.Retrieve(ctx, "What is Eino?")` to find relevant experiences

**Code snippet:**

```go
retriever := eino.NewRetriever(client, "/tmp/eino-demo")
experiences, err := retriever.Retrieve(ctx, "What is Eino?")
```

### LangChainGo Demo

Demonstrates the LangChainGo Memory adapter — Cortex CE memory as a LangChainGo-compatible memory.

**How to run:**

```bash
cd go-sdk/cortex-mem-go/examples/langchaingo
go run .
```

**What it does:**
1. Creates a `Memory` with project path `/tmp/langchaingo-demo`
2. Saves a conversation context (input + output pair)
3. Waits for ingestion
4. Loads memory variables using `memory.LoadMemoryVariables()`
5. Prints the memory key names

**Code snippet:**

```go
memory := langchaingo.NewMemory(client, "/tmp/langchaingo-demo")

// Save context
err := memory.SaveContext(ctx,
    map[string]any{"input": "What is your favorite programming language?"},
    map[string]any{"output": "I prefer Go because it's fast and concurrent."},
)

// Load memory
vars, err := memory.LoadMemoryVariables(ctx, map[string]any{"input": "programming"})
fmt.Println(vars["history"])
```

### Genkit Demo

Demonstrates the Genkit Retriever adapter — Cortex CE memory as a Genkit-compatible retriever.

**How to run:**

```bash
cd go-sdk/cortex-mem-go/examples/genkit
go run .
```

**What it does:**
1. Creates a `Retriever` with options (project, count)
2. Records observations about Genkit
3. Waits for ingestion
4. Uses `retriever.Retrieve(ctx, genkit.RetrieverInput{...})` to find documents

**Code snippet:**

```go
retriever := genkit.NewRetriever(client,
    genkit.WithRetrieverProject("/tmp/genkit-demo"),
    genkit.WithRetrieverCount(10),
)

output, err := retriever.Retrieve(ctx, genkit.RetrieverInput{
    Query:   "What is Genkit?",
    Project: "/tmp/genkit-demo",
    Count:   10,
})
for _, doc := range output.Documents {
    fmt.Println(doc.Content)
}
```

## E2E Testing

The Go SDK includes a comprehensive E2E test script that validates the full chain: **Test Script → HTTP Demo → Go SDK → Backend**.

### Prerequisites

1. Backend running on port 37777
2. HTTP server demo running on port 37779

```bash
# Terminal 1: Start backend
cd backend && java -jar target/cortex-ce-*.jar

# Terminal 2: Start HTTP demo
cd go-sdk/cortex-mem-go/examples/http-server && go run .
```

### Running the E2E Test

```bash
cd BlueCortexCE
bash scripts/go-sdk-e2e-test.sh
```

### What It Tests

The E2E script runs 36 tests covering:

| Category | Tests |
|----------|-------|
| Pre-checks | Backend health, Demo health |
| Data preparation | Write observation to backend |
| Demo endpoints | Chat, Search, Source filter, Version |
| Backend direct | Health, Version, Search, Observations, Projects, Stats, Modes, Settings |
| Chain verification | Test → Demo → Go SDK → Backend data flow |
| SDK methods | UpdateSessionUserId, RetrieveExperiences, BuildICLPrompt, GetQualityDistribution |
| All 24 endpoints | Every HTTP Demo endpoint is tested |
| Ingest | User prompt, Session end |

**Expected output:**
```
==========================================
Go SDK E2E Final Summary: 36/36 passed, 0 failed
==========================================

🎉 Go SDK Demo E2E test all passed! (36 tests)
```

### Coverage Checklist

After E2E testing, the following SDK methods are verified:

**Indirectly covered (via HTTP Demo endpoints):**
- ✅ StartSession (via /chat)
- ✅ RecordObservation (via /chat)
- ✅ Search (via /search)
- ✅ GetVersion (via /version)
- ✅ HealthCheck (via /health)
- ✅ RetrieveExperiences (via /experiences)
- ✅ BuildICLPrompt (via /iclprompt)
- ✅ ListObservations (via /observations)
- ✅ GetObservationsByIds (via /observations/batch)
- ✅ GetProjects (via /projects)
- ✅ GetStats (via /stats)
- ✅ GetModes (via /modes)
- ✅ GetSettings (via /settings)
- ✅ GetQualityDistribution (via /quality)
- ✅ GetLatestExtraction (via /extraction/latest)
- ✅ GetExtractionHistory (via /extraction/history)
- ✅ TriggerExtraction (via /extraction/run)
- ✅ TriggerRefinement (via /refine)
- ✅ SubmitFeedback (via /feedback)
- ✅ UpdateSessionUserId (via /session/user)
- ✅ UpdateObservation (via /observation/patch)
- ✅ DeleteObservation (via /observation/delete)
- ✅ RecordUserPrompt (via /ingest/prompt)
- ✅ RecordSessionEnd (via /ingest/session-end)

## Troubleshooting

### Demo Won't Start

**Problem:** `go run .` fails with import errors

**Solution:** Ensure you're in the Go module directory and have internet access for dependency download:

```bash
cd go-sdk/cortex-mem-go/examples/http-server
go mod tidy
go run .
```

### Backend Connection Refused

**Problem:** `connection refused` errors when running demos

**Solution:** Verify the backend is running and accessible:

```bash
curl http://127.0.0.1:37777/api/health
```

If not running:

```bash
cd backend
java -jar target/cortex-ce-*.jar
```

### Search Returns Empty Results

**Problem:** Search returns 0 results even after recording observations

**Solution:** Fire-and-forget operations have a built-in delay. Add a `time.Sleep(500 * time.Millisecond)` after recording observations before searching:

```go
_ = client.RecordObservation(ctx, obsReq)
time.Sleep(500 * time.Millisecond) // Wait for ingestion
result, _ := client.Search(ctx, searchReq)
```

### E2E Test Fails

**Problem:** Some tests fail with `connection failed or timed out`

**Solution:** Check that both services are running:
1. Backend on port 37777: `curl http://127.0.0.1:37777/api/health`
2. HTTP Demo on port 37779: `curl http://localhost:37779/health`

If the Demo returns `connection refused`, start it:

```bash
cd go-sdk/cortex-mem-go/examples/http-server && go run .
```

### Port Conflict

**Problem:** Port 37779 is already in use

**Solution:** The port is not hardcoded — `main.go` reads it from the `PORT`
environment variable and falls back to 37779. Pick another one at launch:

```bash
cd go-sdk/cortex-mem-go/examples/http-server
PORT=38080 go run .
```

No source change is needed. If you drive the E2E script, point it at the port you
chose:

```bash
DEMO_BASE="http://localhost:38080"
```

### Fire-and-Forget Errors Not Visible

**Problem:** Capture operations silently fail without visible errors

**Solution:** Enable logging to see retry failures:

```go
import "log/slog"

client := cortexmem.NewClient(
    cortexmem.WithLogger(slog.Default()),
    cortexmem.WithMaxRetries(5),
)
```

With logging enabled, you'll see messages like:
```
WARN cortex-ce: RecordObservation failed after retries error="..." attempts=5
```

---

*For API details, see the [Go SDK User Guide](go-sdk-guide.md).*
*For backend API documentation, see the [Backend README](../backend/README.md).*
