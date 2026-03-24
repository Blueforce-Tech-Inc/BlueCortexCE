# Go SDK User Guide

[中文](go-sdk-guide-zh-CN.md) | English

## Overview

The **Cortex CE Go Client SDK** (`cortex-mem-go`) is a pure Go client library for the [Cortex CE](https://github.com/abforce/cortex-ce) persistent memory system. It provides a clean, idiomatic Go interface for:

- **Capturing** agent observations, user prompts, and session events into the memory backend
- **Retrieving** relevant historical experiences for In-Context Learning (ICL) / ExpRAG
- **Managing** memories through refinement, feedback, updates, and deletion
- **Extracting** structured data from conversation observations

**Key design goals:**

- **Zero mandatory dependencies** — only the Go standard library
- **Full API coverage** — 25+ methods covering all backend endpoints
- **Wire format compatible** — JSON field names match the backend API exactly
- **Fire-and-forget capture** — non-blocking recording with built-in retry
- **Framework integrations** — optional adapters for Eino, LangChainGo, and Genkit

## Installation

```bash
go get github.com/abforce/cortex-ce/cortex-mem-go
```

For framework integrations, also install the desired adapter:

```bash
# Eino
go get github.com/abforce/cortex-ce/cortex-mem-go/eino

# LangChainGo
go get github.com/abforce/cortex-ce/cortex-mem-go/langchaingo

# Genkit
go get github.com/abforce/cortex-ce/cortex-mem-go/genkit
```

**Import path:**

```go
import (
    cortexmem "github.com/abforce/cortex-ce/cortex-mem-go"
    "github.com/abforce/cortex-ce/cortex-mem-go/dto"
)
```

## Quick Start

```go
package main

import (
    "context"
    "fmt"
    "log"
    "time"

    cortexmem "github.com/abforce/cortex-ce/cortex-mem-go"
    "github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

func main() {
    // Create a client (defaults to http://127.0.0.1:37777)
    client := cortexmem.NewClient()
    defer client.Close()

    ctx := context.Background()

    // 1. Health check
    if err := client.HealthCheck(ctx); err != nil {
        log.Fatalf("Backend unhealthy: %v", err)
    }

    // 2. Start a session
    session, err := client.StartSession(ctx, dto.SessionStartRequest{
        SessionID:   fmt.Sprintf("demo-%d", time.Now().Unix()),
        ProjectPath: "/my-project",
    })
    if err != nil {
        log.Fatal(err)
    }

    // 3. Record an observation
    err = client.RecordObservation(ctx, dto.ObservationRequest{
        SessionID:   session.SessionID,
        ProjectPath: "/my-project",
        ToolName:    "Read",
        ToolInput:   map[string]any{"path": "config.yaml"},
        ToolResponse: map[string]any{"result": "file contents"},
    })
    if err != nil {
        log.Printf("Failed to record: %v", err)
    }

    // Wait for fire-and-forget ingestion
    time.Sleep(500 * time.Millisecond)

    // 4. Search memories
    result, err := client.Search(ctx, dto.SearchRequest{
        Project: "/my-project",
        Query:   "configuration",
        Limit:   5,
    })
    if err != nil {
        log.Fatal(err)
    }
    fmt.Printf("Found %d results\n", result.Count)
    for _, obs := range result.Observations {
        fmt.Printf("  [%s] %s\n", obs.Type, obs.Content)
    }
}
```

## Client Configuration

Configure the client using the **Option pattern**:

```go
client := cortexmem.NewClient(
    cortexmem.WithBaseURL("http://my-host:37777"),
    cortexmem.WithAPIKey("my-secret-key"),
    cortexmem.WithMaxRetries(5),
    cortexmem.WithHTTPClient(&http.Client{Timeout: 60 * time.Second}),
    cortexmem.WithLogger(myLogger),
)
```

### Available Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `WithBaseURL` | `string` | `http://127.0.0.1:37777` | Backend service URL |
| `WithAPIKey` | `string` | `""` (none) | API key for `Authorization: Bearer` header |
| `WithHTTPClient` | `*http.Client` | 30s timeout | Custom HTTP client |
| `WithMaxRetries` | `int` | `3` | Retry count for fire-and-forget operations |
| `WithLogger` | `Logger` | no-op | Custom logger (compatible with `*slog.Logger`) |

### Custom Logger

The `Logger` interface is compatible with `*slog.Logger`:

```go
import "log/slog"

client := cortexmem.NewClient(
    cortexmem.WithLogger(slog.Default()),
)
```

Or implement your own:

```go
type Logger interface {
    Debug(msg string, args ...any)
    Info(msg string, args ...any)
    Warn(msg string, args ...any)
    Error(msg string, args ...any)
}
```

## API Reference

All methods accept a `context.Context` as the first parameter. Use it for timeouts, cancellation, and deadline propagation.

### Health & Version

#### HealthCheck

Checks if the backend is healthy and returns `status: "ok"`.

```go
func HealthCheck(ctx context.Context) error
```

**Example:**

```go
if err := client.HealthCheck(ctx); err != nil {
    log.Fatalf("Backend unhealthy: %v", err)
}
```

**Backend endpoint:** `GET /api/health`

#### GetVersion

Returns backend version information.

```go
func GetVersion(ctx context.Context) (map[string]any, error)
```

**Example:**

```go
version, err := client.GetVersion(ctx)
if err != nil {
    log.Fatal(err)
}
fmt.Printf("Version: %s\n", version["version"])
```

**Backend endpoint:** `GET /api/version`

---

### Sessions

#### StartSession

Starts or resumes a session. Returns a session ID that should be used for subsequent observations.

```go
func StartSession(ctx context.Context, req dto.SessionStartRequest) (*dto.SessionStartResponse, error)
```

**Request (`dto.SessionStartRequest`):**

| Field | JSON Wire Name | Required | Description |
|-------|---------------|----------|-------------|
| `SessionID` | `session_id` | No | Custom session ID (auto-generated if empty) |
| `ProjectPath` | `project_path` | Yes | Project path for memory isolation |
| `UserID` | `user_id` | No | User identifier for multi-user isolation |

**Response (`dto.SessionStartResponse`):**

| Field | JSON Wire Name | Description |
|-------|---------------|-------------|
| `SessionDBID` | `session_db_id` | Database ID of the session |
| `SessionID` | `session_id` | Session ID to use |
| `Context` | `context` | Previous session context (if resuming) |
| `PromptNumber` | `prompt_number` | Current prompt number |

**Example:**

```go
resp, err := client.StartSession(ctx, dto.SessionStartRequest{
    SessionID:   "my-session-123",
    ProjectPath: "/my-project",
    UserID:      "alice",
})
if err != nil {
    log.Fatal(err)
}
fmt.Printf("Session: %s (DB: %s)\n", resp.SessionID, resp.SessionDBID)
```

**Backend endpoint:** `POST /api/session/start`

**Wire format note:** `ProjectPath` is sent as `"project_path"` (not `"cwd"`) in the session start request.

#### UpdateSessionUserId

Updates the user ID on an existing session. Useful for late binding of user identity.

```go
func UpdateSessionUserId(ctx context.Context, sessionID, userID string) (map[string]any, error)
```

**Example:**

```go
result, err := client.UpdateSessionUserId(ctx, "my-session-123", "bob")
if err != nil {
    log.Fatal(err)
}
fmt.Printf("Updated: %v\n", result)
```

**Backend endpoint:** `PATCH /api/session/{id}/user`

---

### Observations

#### RecordObservation

Records a tool-use observation. This is a **fire-and-forget** operation — it retries internally and swallows errors to avoid blocking the caller.

```go
func RecordObservation(ctx context.Context, req dto.ObservationRequest) error
```

**Request (`dto.ObservationRequest`):**

| Field | JSON Wire Name | Required | Description |
|-------|---------------|----------|-------------|
| `SessionID` | `session_id` | Yes | Session ID |
| `ProjectPath` | `cwd` | Yes | Project path (note: `"cwd"`, not `"project_path"`) |
| `ToolName` | `tool_name` | Yes | Name of the tool |
| `ToolInput` | `tool_input` | No | Tool input (any JSON-serializable value) |
| `ToolResponse` | `tool_response` | No | Tool output |
| `PromptNumber` | `prompt_number` | No | Prompt number in the session |
| `Source` | `source` | No | Source attribution (e.g., `"tool_result"`, `"user_statement"`) |
| `ExtractedData` | `extractedData` | No | Structured key-value data (note: **camelCase**) |

**Example:**

```go
err := client.RecordObservation(ctx, dto.ObservationRequest{
    SessionID:   "my-session-123",
    ProjectPath: "/my-project",
    ToolName:    "Edit",
    ToolInput:   map[string]any{"path": "main.go", "old": "foo", "new": "bar"},
    ToolResponse: map[string]any{"result": "success"},
    Source:      "tool_result",
    ExtractedData: map[string]any{
        "file": "main.go",
        "change_type": "replacement",
    },
})
```

**Backend endpoint:** `POST /api/ingest/tool-use`

**Wire format notes:**
- `ProjectPath` is sent as `"cwd"` (not `"project_path"`)
- `ToolName` is sent as `"tool_name"` (not `"type"`)
- `ExtractedData` is sent as `"extractedData"` (camelCase)

#### Search

Performs semantic search over observations.

```go
func Search(ctx context.Context, req dto.SearchRequest) (*dto.SearchResult, error)
```

**Request (`dto.SearchRequest`):**

| Field | Description |
|-------|-------------|
| `Project` | Project path (required) |
| `Query` | Search query text |
| `Type` | Filter by observation type |
| `Concept` | Filter by concept |
| `Source` | Filter by source |
| `Limit` | Max results (default: backend default) |
| `Offset` | Pagination offset |

**Response (`dto.SearchResult`):**

| Field | Description |
|-------|-------------|
| `Observations` | List of matching observations |
| `Strategy` | Search strategy used |
| `FellBack` | Whether the search fell back to a simpler strategy |
| `Count` | Number of results |

**Example:**

```go
result, err := client.Search(ctx, dto.SearchRequest{
    Project: "/my-project",
    Query:   "error handling patterns",
    Source:  "tool_result",
    Limit:   10,
})
if err != nil {
    log.Fatal(err)
}
fmt.Printf("Strategy: %s, Count: %d\n", result.Strategy, result.Count)
for _, obs := range result.Observations {
    fmt.Printf("  [%s] %s\n", obs.Type, obs.Content)
}
```

**Backend endpoint:** `GET /api/search?project=...&query=...&source=...&limit=...`

#### ListObservations

Lists observations with pagination.

```go
func ListObservations(ctx context.Context, req dto.ObservationsRequest) (*dto.ObservationsResponse, error)
```

**Request (`dto.ObservationsRequest`):**

| Field | Description |
|-------|-------------|
| `Project` | Project path (optional) |
| `Offset` | Pagination offset |
| `Limit` | Page size |

**Response (`dto.ObservationsResponse`):**

| Field | Description |
|-------|-------------|
| `Items` | List of observations |
| `HasMore` | Whether more pages exist |
| `Total` | Total count |
| `Offset` | Current offset |
| `Limit` | Current page size |

**Example:**

```go
resp, err := client.ListObservations(ctx, dto.ObservationsRequest{
    Project: "/my-project",
    Limit:   20,
    Offset:  0,
})
if err != nil {
    log.Fatal(err)
}
fmt.Printf("Total: %d, HasMore: %v\n", resp.Total, resp.HasMore)
```

**Backend endpoint:** `GET /api/observations?project=...&limit=...&offset=...`

#### GetObservationsByIds

Retrieves specific observations by their IDs.

```go
func GetObservationsByIds(ctx context.Context, ids []string) ([]dto.Observation, error)
```

**Example:**

```go
observations, err := client.GetObservationsByIds(ctx, []string{"obs-1", "obs-2", "obs-3"})
if err != nil {
    log.Fatal(err)
}
for _, obs := range observations {
    fmt.Printf("  %s: %s\n", obs.ID, obs.Content)
}
```

**Backend endpoint:** `POST /api/observations/batch` with body `{"ids": ["obs-1", "obs-2"]}`

#### UpdateObservation

Updates an existing observation. Fire-and-forget.

```go
func UpdateObservation(ctx context.Context, observationID string, update dto.ObservationUpdate) error
```

**Request (`dto.ObservationUpdate`):**

All fields are pointers/slices with `omitempty` — only included fields are updated.

| Field | JSON Wire Name | Type | Description |
|-------|---------------|------|-------------|
| `Title` | `title` | `*string` | New title |
| `Content` | `content` | `*string` | New content |
| `Facts` | `facts` | `[]string` | Facts list |
| `Concepts` | `concepts` | `[]string` | Concepts list |
| `Source` | `source` | `*string` | Source attribution |
| `ExtractedData` | `extractedData` | `map[string]any` | Structured data (camelCase) |

**Example:**

```go
title := "Updated title"
err := client.UpdateObservation(ctx, "obs-123", dto.ObservationUpdate{
    Title: &title,
    Source: strPtr("verified"),
})
```

**Backend endpoint:** `PATCH /api/memory/observations/{id}`

#### DeleteObservation

Deletes an observation. Fire-and-forget.

```go
func DeleteObservation(ctx context.Context, observationID string) error
```

**Example:**

```go
err := client.DeleteObservation(ctx, "obs-123")
```

**Backend endpoint:** `DELETE /api/memory/observations/{id}`

---

### Memory

#### RetrieveExperiences

Retrieves relevant past experiences for a task.

```go
func RetrieveExperiences(ctx context.Context, req dto.ExperienceRequest) ([]dto.Experience, error)
```

**Request (`dto.ExperienceRequest`):**

| Field | JSON Wire Name | Description |
|-------|---------------|-------------|
| `Task` | `task` | Task description |
| `Project` | `project` | Project path |
| `Count` | `count` | Max experiences to return |
| `Source` | `source` | Source filter |
| `RequiredConcepts` | `requiredConcepts` | Required concepts (camelCase) |
| `UserID` | `userId` | User filter (camelCase) |

**Response (`dto.Experience`):**

| Field | Description |
|-------|-------------|
| `ID` | Experience ID |
| `Task` | Original task |
| `Strategy` | Reuse strategy |
| `Outcome` | Expected outcome |
| `ReuseCondition` | When to reuse |
| `QualityScore` | Quality score (0-1) |
| `CreatedAt` | Creation timestamp |

**Example:**

```go
experiences, err := client.RetrieveExperiences(ctx, dto.ExperienceRequest{
    Task:    "fix authentication bug",
    Project: "/my-project",
    Count:   4,
    UserID:  "alice",
})
if err != nil {
    log.Fatal(err)
}
for _, exp := range experiences {
    fmt.Printf("  [%s] %s → %s\n", exp.Strategy, exp.Task, exp.Outcome)
}
```

**Backend endpoint:** `POST /api/memory/experiences`

#### BuildICLPrompt

Builds an In-Context Learning prompt from relevant experiences.

```go
func BuildICLPrompt(ctx context.Context, req dto.ICLPromptRequest) (*dto.ICLPromptResult, error)
```

**Request (`dto.ICLPromptRequest`):**

| Field | JSON Wire Name | Description |
|-------|---------------|-------------|
| `Task` | `task` | Task description |
| `Project` | `project` | Project path |
| `MaxChars` | `maxChars` | Max prompt characters (camelCase) |
| `UserID` | `userId` | User filter (camelCase) |

**Response (`dto.ICLPromptResult`):**

| Field | Description |
|-------|-------------|
| `Prompt` | The generated ICL prompt |
| `ExperienceCount` | Number of experiences used |

**Example:**

```go
result, err := client.BuildICLPrompt(ctx, dto.ICLPromptRequest{
    Task:     "recommend a phone",
    Project:  "/my-project",
    MaxChars: 2000,
    UserID:   "alice",
})
if err != nil {
    log.Fatal(err)
}
fmt.Printf("ICL Prompt (%s experiences):\n%s\n", result.ExperienceCount, result.Prompt)
```

**Backend endpoint:** `POST /api/memory/icl-prompt`

#### TriggerRefinement

Triggers memory refinement for a project. Fire-and-forget.

```go
func TriggerRefinement(ctx context.Context, projectPath string) error
```

**Example:**

```go
err := client.TriggerRefinement(ctx, "/my-project")
```

**Backend endpoint:** `POST /api/memory/refine?project=...`

#### GetQualityDistribution

Returns quality statistics for a project's observations.

```go
func GetQualityDistribution(ctx context.Context, projectPath string) (*dto.QualityDistribution, error)
```

**Response (`dto.QualityDistribution`):**

| Field | Description |
|-------|-------------|
| `Project` | Project path |
| `High` | High quality count |
| `Medium` | Medium quality count |
| `Low` | Low quality count |
| `Unknown` | Unknown quality count |
| `Total()` | Method returning total count |

**Example:**

```go
dist, err := client.GetQualityDistribution(ctx, "/my-project")
if err != nil {
    log.Fatal(err)
}
fmt.Printf("Quality: High=%d, Medium=%d, Low=%d, Total=%d\n",
    dist.High, dist.Medium, dist.Low, dist.Total())
```

**Backend endpoint:** `GET /api/memory/quality-distribution?project=...`

#### SubmitFeedback

Submits feedback for an observation. Fire-and-forget.

```go
func SubmitFeedback(ctx context.Context, observationID, feedbackType, comment string) error
```

**Example:**

```go
err := client.SubmitFeedback(ctx, "obs-123", "useful", "Very helpful observation")
```

**Backend endpoint:** `POST /api/memory/feedback` with body `{"observationId": "...", "feedbackType": "...", "comment": "..."}`

**Wire format note:** Field names use camelCase (`observationId`, `feedbackType`).

---

### Extraction

#### GetLatestExtraction

Gets the most recent extraction result for a template.

```go
func GetLatestExtraction(ctx context.Context, projectPath, templateName, userID string) (map[string]any, error)
```

**Example:**

```go
result, err := client.GetLatestExtraction(ctx, "/my-project", "user_preference", "alice")
if err != nil {
    log.Fatal(err)
}
fmt.Printf("Extraction: %v\n", result["data"])
```

**Backend endpoint:** `GET /api/extraction/{templateName}/latest?projectPath=...&userId=...`

#### GetExtractionHistory

Gets extraction history (all snapshots) for a template.

```go
func GetExtractionHistory(ctx context.Context, projectPath, templateName, userID string, limit int) ([]map[string]any, error)
```

**Example:**

```go
history, err := client.GetExtractionHistory(ctx, "/my-project", "user_preference", "alice", 10)
if err != nil {
    log.Fatal(err)
}
for _, snapshot := range history {
    fmt.Printf("  %v: %v\n", snapshot["extractedAt"], snapshot["data"])
}
```

**Backend endpoint:** `GET /api/extraction/{templateName}/history?projectPath=...&userId=...&limit=...`

---

### Management

#### GetProjects

Returns all projects.

```go
func GetProjects(ctx context.Context) (map[string]any, error)
```

**Backend endpoint:** `GET /api/projects`

#### GetStats

Returns project statistics. Pass empty string for global stats.

```go
func GetStats(ctx context.Context, projectPath string) (map[string]any, error)
```

**Backend endpoint:** `GET /api/stats?project=...`

#### GetModes

Returns memory mode settings.

```go
func GetModes(ctx context.Context) (map[string]any, error)
```

**Backend endpoint:** `GET /api/modes`

#### GetSettings

Returns current system settings.

```go
func GetSettings(ctx context.Context) (map[string]any, error)
```

**Backend endpoint:** `GET /api/settings`

---

### Ingest

#### RecordUserPrompt

Records a user prompt. Fire-and-forget.

```go
func RecordUserPrompt(ctx context.Context, req dto.UserPromptRequest) error
```

**Request (`dto.UserPromptRequest`):**

| Field | JSON Wire Name | Description |
|-------|---------------|-------------|
| `SessionID` | `session_id` | Session ID |
| `PromptText` | `prompt_text` | The user's prompt |
| `ProjectPath` | `cwd` | Project path (**note:** `"cwd"`) |
| `PromptNumber` | `prompt_number` | Prompt number in session |

**Example:**

```go
err := client.RecordUserPrompt(ctx, dto.UserPromptRequest{
    SessionID:   "my-session-123",
    PromptText:  "How do I fix this error?",
    ProjectPath: "/my-project",
    PromptNumber: 5,
})
```

**Backend endpoint:** `POST /api/ingest/user-prompt`

**Wire format note:** `ProjectPath` is sent as `"cwd"`.

#### RecordSessionEnd

Signals session end. Fire-and-forget.

```go
func RecordSessionEnd(ctx context.Context, req dto.SessionEndRequest) error
```

**Request (`dto.SessionEndRequest`):**

| Field | JSON Wire Name | Description |
|-------|---------------|-------------|
| `SessionID` | `session_id` | Session ID |
| `ProjectPath` | `cwd` | Project path (**note:** `"cwd"`) |
| `LastAssistantMessage` | `last_assistant_message` | Last assistant message |

**Example:**

```go
err := client.RecordSessionEnd(ctx, dto.SessionEndRequest{
    SessionID:           "my-session-123",
    ProjectPath:         "/my-project",
    LastAssistantMessage: "Task completed successfully.",
})
```

**Backend endpoint:** `POST /api/ingest/session-end`

**Wire format note:** `ProjectPath` is sent as `"cwd"`.

---

### Lifecycle

#### Close

Releases resources. Always call with `defer`:

```go
client := cortexmem.NewClient()
defer client.Close()
```

---

## Error Handling

### APIError

When the backend returns an HTTP error, the SDK returns an `*APIError`:

```go
type APIError struct {
    StatusCode int
    Message    string
}
```

`APIError` implements the `error` interface and supports `errors.As` / `errors.Is` unwrapping.

### Sentinel Errors

The SDK provides sentinel errors for common HTTP status codes:

| Variable | HTTP Status |
|----------|------------|
| `ErrBadRequest` | 400 |
| `ErrUnauthorized` | 401 |
| `ErrForbidden` | 403 |
| `ErrNotFound` | 404 |
| `ErrConflict` | 409 |
| `ErrUnprocessable` | 422 |
| `ErrRateLimited` | 429 |
| `ErrInternal` | 500 |
| `ErrBadGateway` | 502 |
| `ErrServiceUnavailable` | 503 |
| `ErrGatewayTimeout` | 504 |

### Helper Functions

Use these helpers for clean error type checking:

```go
result, err := client.Search(ctx, req)
if err != nil {
    switch {
    case cortexmem.IsNotFound(err):
        log.Println("Project not found")
    case cortexmem.IsBadRequest(err):
        log.Println("Invalid request parameters")
    case cortexmem.IsUnauthorized(err):
        log.Println("Authentication required")
    case cortexmem.IsRateLimited(err):
        log.Println("Rate limited, retry later")
    case cortexmem.IsInternal(err):
        log.Println("Server error")
    default:
        log.Printf("Unexpected error: %v", err)
    }
}
```

Available helpers: `IsNotFound`, `IsBadRequest`, `IsUnauthorized`, `IsConflict`, `IsRateLimited`, `IsInternal`.

### Fire-and-Forget Operations

The following methods use fire-and-forget semantics (retry + error swallowing):

- `RecordObservation`
- `RecordSessionEnd`
- `RecordUserPrompt`
- `TriggerRefinement`
- `SubmitFeedback`
- `UpdateObservation`
- `DeleteObservation`

These methods **always return `nil`** after exhausting retries. Errors are logged via the configured `Logger`. This matches the Java SDK's behavior: capture operations must never block the AI pipeline.

## DTO Reference

### Wire Format Conventions

The Go SDK uses **exact wire format matching** — JSON field names in struct tags match the backend API precisely. Key conventions:

| Convention | Examples |
|-----------|----------|
| **snake_case** | `session_id`, `project_path`, `tool_name`, `prompt_text`, `prompt_number`, `feedback_type`, `last_assistant_message` |
| **camelCase** | `extractedData`, `requiredConcepts`, `userId`, `observationId`, `feedbackType`, `maxChars` |
| **Special names** | `cwd` (not `project_path` for ingestion), `project` (not `project_path` for memory APIs) |

### Key Data Structures

#### Observation

```go
type Observation struct {
    ID            string         `json:"id"`
    SessionID     string         `json:"sessionId"`
    ProjectPath   string         `json:"projectPath"`
    Type          string         `json:"type"`
    Title         string         `json:"title,omitempty"`
    Content       string         `json:"content"`
    Facts         []string       `json:"facts,omitempty"`
    Concepts      []string       `json:"concepts,omitempty"`
    QualityScore  float32        `json:"qualityScore,omitempty"`
    Source        string         `json:"source,omitempty"`
    ExtractedData map[string]any `json:"extractedData,omitempty"`
    CreatedAt     string         `json:"createdAt,omitempty"`
}
```

**Response DTOs** use camelCase for field names (matching the backend's serialization).

#### Experience

```go
type Experience struct {
    ID              string  `json:"id"`
    Task            string  `json:"task"`
    Strategy        string  `json:"strategy"`
    Outcome         string  `json:"outcome"`
    ReuseCondition  string  `json:"reuseCondition"`
    QualityScore    float32 `json:"qualityScore"`
    CreatedAt       string  `json:"createdAt,omitempty"`
}
```

#### QualityDistribution

```go
type QualityDistribution struct {
    Project string `json:"project"`
    High    int64  `json:"high"`
    Medium  int64  `json:"medium"`
    Low     int64  `json:"low"`
    Unknown int64  `json:"unknown"`
}

func (q QualityDistribution) Total() int64 { ... }
```

## Integration Layers

The SDK provides optional adapters for three Go AI frameworks. Each adapter wraps the core `Client` interface.

### Eino Adapter

Adapts Cortex CE memory to Eino's Retriever interface.

```go
import "github.com/abforce/cortex-ce/cortex-mem-go/eino"

retriever := eino.NewRetriever(client,
    eino.WithRetrieverProject("/my-project"),
    eino.WithRetrieverSource("tool_result"),
    eino.WithRetrieverCount(10),
    eino.WithRetrieverUserID("alice"),
)

experiences, err := retriever.Retrieve(ctx, "What is Eino?")
```

**Options:** `WithRetrieverProject`, `WithRetrieverSource`, `WithRetrieverCount`, `WithRetrieverUserID`

### LangChainGo Adapter

Adapts Cortex CE memory to LangChainGo's Memory interface.

```go
import "github.com/abforce/cortex-ce/cortex-mem-go/langchaingo"

memory := langchaingo.NewMemory(client,
    langchaingo.WithMemoryProject("/my-project"),
    langchaingo.WithMemoryMaxChars(4000),
    langchaingo.WithMemoryKey("history"),
    langchaingo.WithMemoryUserID("alice"),
)

// Save conversation context
err := memory.SaveContext(ctx,
    map[string]any{"input": "What is your favorite language?"},
    map[string]any{"output": "I prefer Go."},
)

// Load memory variables
vars, err := memory.LoadMemoryVariables(ctx, map[string]any{"input": "programming"})
fmt.Println(vars["history"])
```

**Options:** `WithMemoryProject`, `WithMemoryMaxChars`, `WithMemoryKey`, `WithMemoryUserID`

### Genkit Adapter

Adapts Cortex CE memory to Genkit's Retriever interface.

```go
import "github.com/abforce/cortex-ce/cortex-mem-go/genkit"

retriever := genkit.NewRetriever(client,
    genkit.WithRetrieverProject("/my-project"),
    genkit.WithRetrieverCount(10),
    genkit.WithRetrieverSource("tool_result"),
    genkit.WithRetrieverUserID("alice"),
)

output, err := retriever.Retrieve(ctx, genkit.RetrieverInput{
    Query:   "What is Genkit?",
    Project: "/my-project",
    Count:   10,
})
for _, doc := range output.Documents {
    fmt.Println(doc.Content)
}
```

**Options:** `WithRetrieverProject`, `WithRetrieverCount`, `WithRetrieverSource`, `WithRetrieverUserID`

## Best Practices

### Context Usage

Always pass a `context.Context` with appropriate timeout:

```go
ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
defer cancel()

result, err := client.Search(ctx, req)
```

For fire-and-forget operations, use `context.Background()` to avoid premature cancellation:

```go
// Good: observation won't be cancelled when parent context ends
go func() {
    _ = client.RecordObservation(context.Background(), obsReq)
}()
```

### Graceful Shutdown

Always call `Close()` via `defer`:

```go
client := cortexmem.NewClient()
defer client.Close()
```

For HTTP servers, combine with OS signal handling:

```go
srv := &http.Server{Addr: ":8080", Handler: mux}

go func() {
    sigCh := make(chan os.Signal, 1)
    signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
    <-sigCh
    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()
    srv.Shutdown(ctx)
    client.Close()
}()

log.Fatal(srv.ListenAndServe())
```

### Error Handling Patterns

**Distinguish retrieval errors from empty results:**

```go
result, err := client.Search(ctx, req)
if err != nil {
    if cortexmem.IsNotFound(err) {
        // Project doesn't exist yet
        return nil, nil
    }
    return nil, fmt.Errorf("search failed: %w", err)
}
// result.Count == 0 means "no results" (not an error)
```

**Handle fire-and-forget gracefully:**

```go
// Fire-and-forget methods always return nil after retries.
// Use Logger to monitor failures:
client := cortexmem.NewClient(
    cortexmem.WithLogger(slog.Default()),
)

// Errors are logged automatically; return value is always nil
_ = client.RecordObservation(ctx, obsReq)
```

### Backward Compatibility

- `ProjectPath` uses different JSON wire names depending on the API:
  - Session start: `"project_path"`
  - Ingestion (observation, prompt, session-end): `"cwd"`
  - Memory APIs (experiences, ICL): `"project"`
- `ExtractedData` and similar fields use **camelCase** (`extractedData`, `requiredConcepts`, `userId`)
- Response DTOs (`Observation`, `Experience`) use camelCase field names

---

*For demo projects and runnable examples, see the [Go SDK Demo Guide](go-sdk-demo-guide.md).*
*For backend API details, see the [Cortex CE Backend README](../backend/README.md).*
