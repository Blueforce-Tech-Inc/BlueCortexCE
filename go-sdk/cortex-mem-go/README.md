# Cortex CE Go SDK

Go client library for [Cortex CE](https://github.com/abforce/cortex-ce) — a persistent memory system for AI assistants.

## Features

- **Zero mandatory dependencies** — only uses Go standard library
- **Full API coverage** — 26 methods covering Session, Capture, Retrieval, Management, Extraction
- **Framework integrations** — optional Eino, LangChainGo, and Genkit modules
- **Wire format compatible** — JSON field names match backend API exactly
- **Comprehensive tests** — 259 unit tests with wire format verification (main + integration layers)

## Installation

```bash
go get github.com/abforce/cortex-ce/cortex-mem-go
```

## Quick Start

```go
package main

import (
    "context"
    "fmt"
    "log"

    "github.com/abforce/cortex-ce/cortex-mem-go"
    "github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

func main() {
    client := cortexmem.NewClient(
        cortexmem.WithBaseURL("http://127.0.0.1:37777"),
    )
    defer client.Close()

    ctx := context.Background()

    // Start a session
    resp, err := client.StartSession(ctx, dto.SessionStartRequest{
        ProjectPath: "/my-project",
    })
    if err != nil {
        log.Fatal(err)
    }
    fmt.Printf("Session: %s\n", resp.SessionID)

    // Record an observation
    err = client.RecordObservation(ctx, dto.ObservationRequest{
        ProjectPath:  "/my-project",
        SessionID:    resp.SessionID,
        ToolName:     "Read",
        ToolInput:    map[string]any{"file_path": "file.txt"},
        ToolResponse: map[string]any{"content": "file contents..."},
    })
    if err != nil {
        log.Fatal(err)
    }

    // Search memories
    result, err := client.Search(ctx, dto.SearchRequest{
        Project: "/my-project",
        Query:   "file operations",
        Limit:   5,
    })
    if err != nil {
        log.Fatal(err)
    }
    fmt.Printf("Found %d results (strategy: %s)\n", result.Count, result.Strategy)
}
```

## API Coverage

| Category | Methods |
|----------|---------|
| Session | `StartSession`, `UpdateSessionUserId` |
| Capture | `RecordObservation`, `RecordSessionEnd`, `RecordUserPrompt` |
| Retrieval | `RetrieveExperiences`, `BuildICLPrompt`, `Search`, `ListObservations`, `GetObservationsByIds` |
| Management | `TriggerRefinement`, `SubmitFeedback`, `UpdateObservation`, `DeleteObservation`, `GetQualityDistribution` |
| Health | `HealthCheck` |
| Extraction | `TriggerExtraction`, `GetLatestExtraction`, `GetExtractionHistory` |
| Version | `GetVersion` |
| P1 | `GetProjects`, `GetStats`, `GetModes`, `GetSettings` |

## Option Pattern

Configure client behavior with options:

```go
client := cortexmem.NewClient(
    cortexmem.WithBaseURL("http://127.0.0.1:37777"),
    cortexmem.WithAPIKey("my-api-key"),
    cortexmem.WithTimeout(30*time.Second),       // overall request timeout (default: 30s)
    cortexmem.WithConnectTimeout(10*time.Second), // connection timeout (default: 10s)
    cortexmem.WithMaxRetries(5),
    cortexmem.WithRetryBackoff(500*time.Millisecond),
)
```

| Option | Default | Description |
|--------|---------|-------------|
| `WithBaseURL` | `http://127.0.0.1:37777` | Backend base URL |
| `WithAPIKey` | *(none)* | Bearer token for authentication |
| `WithTimeout` | `30s` | Overall request timeout (matches Java SDK `readTimeout`) |
| `WithConnectTimeout` | `10s` | Connection timeout (matches Java SDK `connectTimeout`) |
| `WithHTTPClient` | *(auto-built)* | Custom `http.Client` (overrides timeout options) |
| `WithMaxRetries` | `3` | Max retries for fire-and-forget operations |
| `WithRetryBackoff` | `500ms` | Base retry backoff (linear: `backoff × attempt`) |
| `WithLogger` | *(nop)* | Custom logger (compatible with `*slog.Logger`) |

## Framework Integrations

### Eino

```go
import (
    "github.com/abforce/cortex-ce/cortex-mem-go"
    "github.com/abforce/cortex-ce/cortex-mem-go/eino"
)

client := cortexmem.NewClient()
retriever := eino.NewRetriever(client,
    eino.WithRetrieverProject("/my-project"),
    eino.WithRetrieverSource("tool_result"),
)
```

### LangChainGo

```go
import (
    "github.com/abforce/cortex-ce/cortex-mem-go"
    "github.com/abforce/cortex-ce/cortex-mem-go/langchaingo"
)

client := cortexmem.NewClient()
memory := langchaingo.NewMemory(client,
    langchaingo.WithMemoryProject("/my-project"),
)
```

### Genkit

```go
import (
    "github.com/abforce/cortex-ce/cortex-mem-go"
    "github.com/abforce/cortex-ce/cortex-mem-go/genkit"
)

client := cortexmem.NewClient()
retriever := genkit.NewRetriever(client,
    genkit.WithRetrieverProject("/my-project"),
    genkit.WithRetrieverCount(20),
)
```

## Testing

```bash
# Run all tests
go test -v ./...

# Run with coverage
go test -cover ./...
```

## Demo Projects

See `examples/` for complete demo projects:
- `basic/` — Pure SDK usage
- `eino/` — Eino integration
- `langchaingo/` — LangChainGo integration
- `genkit/` — Genkit integration
- `http-server/` — HTTP server example

## Error Handling

```go
import "github.com/abforce/cortex-ce/cortex-mem-go"

result, err := client.Search(ctx, req)
if err != nil {
    if cortexmem.IsNotFound(err) {
        // Handle 404
    } else if cortexmem.IsBadRequest(err) {
        // Handle 400
    } else {
        // Handle other errors
    }
}
```

## Wire Format

The SDK uses JSON field names that match the backend API exactly:

- `session_id` (snake_case)
- `project_path` → `cwd` for tool observations
- `type` → `tool_name` for tool observations
- `requiredConcepts` (camelCase)
- `observationId` (camelCase)

See `dto/` package for full wire format details.

## License

MIT
