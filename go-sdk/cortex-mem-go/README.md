> 中文版: [README-zh-CN.md](./README-zh-CN.md)

# Cortex CE Go SDK

Go client library for [Cortex CE](https://github.com/Blueforce-Tech-Inc/BlueCortexCE) — a persistent memory system for AI assistants.

## Features

- **Zero mandatory dependencies** — only uses Go standard library
- **Full API coverage** — 25 methods covering Session, Capture, Retrieval, Management, Extraction, Version, P1
- **Framework integrations** — optional Eino, LangChainGo, and Genkit modules
- **Wire format compatible** — JSON field names match backend API exactly
- **Comprehensive tests** — 297 unit tests with wire format verification (client 230 + dto 67); integration packages add 33 more (genkit 13 + langchaingo 12 + eino 8) when run from their own directories

## Installation

```bash
go get github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go
```

## Quick Start

```go
package main

import (
    "context"
    "fmt"
    "log"

    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/dto"
)

func main() {
    client := cortexmem.NewClient(
        cortexmem.WithBaseURL("http://127.0.0.1:37777"),
    )
    defer client.Close()

    ctx := context.Background()

    // Start a session
    resp, err := client.StartSession(ctx, dto.SessionStartRequest{
        SessionID:   "my-session-001",
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
| Retrieval | `RetrieveExperiences`, `BuildICLPrompt`, `Search`, `ListObservations`, `GetObservation`, `GetObservationsByIds` |
| Management | `TriggerRefinement`, `SubmitFeedback`, `UpdateObservation`, `DeleteObservation`, `GetQualityDistribution` |
| Health | `HealthCheck` |
| Extraction | `TriggerExtraction`, `GetLatestExtraction`, `GetExtractionHistory` |
| Version | `GetVersion` |
| P1 | `GetProjects`, `GetStats`, `GetModes`, `GetSettings` |
| Lifecycle | `Close` (releases the SDK-owned connection pool; see below), `String` (debug representation) |

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
| `WithHTTPClient` | *(auto-built)* | Custom `http.Client` (overrides timeout options; caller owns it — see below) |
| `WithMaxRetries` | `3` | Total **attempts** for fire-and-forget ops (3 = 3 requests, i.e. 2 retries) |
| `WithRetryBackoff` | `500ms` | Base retry backoff (linear: `backoff × attempt`) |
| `WithLogger` | *(nop)* | Custom logger (compatible with `*slog.Logger`) |

### HTTP client ownership

`WithHTTPClient` hands the SDK a client you own. The SDK never writes to it and
never closes it: `Close()` only releases idle connections on a client the SDK
built itself, because draining a pool you are still sharing with your own
traffic would throw away warm connections and force a re-dial on your next call.

Go's `http.Client` has no closed state, so the client stays usable after
`Close()` either way. The difference is only whether the connection pool
survives — assert on that, not on an error from the next call.

## Framework Integrations

> **Error handling differs by adapter on purpose**: the Eino and Genkit retrievers log the
> failure and then return it to the caller, because an empty result would be
> indistinguishable from "no relevant memories". The LangChainGo `Memory` instead degrades
> to an empty memory string so a prompt chain is never broken, and logs the error so it is
> still observable.

### Eino

```go
import (
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/eino"
)

client := cortexmem.NewClient()
retriever := eino.NewRetriever(client, "/my-project",
    eino.WithRetrieverSource("tool_result"),
)
```

### LangChainGo

```go
import (
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/langchaingo"
)

client := cortexmem.NewClient()
memory := langchaingo.NewMemory(client, "/my-project")
```

### Genkit

```go
import (
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/genkit"
)

client := cortexmem.NewClient()
retriever := genkit.NewRetriever(client, "/my-project",
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
import "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"

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

### Response Size Limit

The SDK never buffers a response body larger than **10 MiB**
(`cortexmem.MaxResponseBytes`, `10 << 20`). The body is read with a limit of
`MaxResponseBytes + 1` bytes, so an oversized response is reported as an
explicit error instead of being silently truncated and then failing to parse:

```
cortex-ce: response body exceeds 10485760 byte limit (raise the page size or split the query)
```

The server's status code is returned alongside that error by the internal
`doRequest`, but the public methods return only `error` — so an oversized
*successful* response (`200`) surfaces as a plain error, not as an empty result
and not as an `APIError`. Keep `limit` on retrieval calls below the cap, or
narrow the query.

### Empty Updates Are Rejected

`UpdateObservation` returns a `ValidationError` when the update sets no field, and no
request is sent. The same rule and message apply in the Java, Python and JS SDKs:

```
cortex-ce: ObservationUpdate validation error: at least one field must be provided for update
```

It matters because a PATCH that sets nothing is a silent no-op on the wire: without
the check, a caller who assembled an empty update from user input would see the call
succeed and could not tell that nothing was written.

## Wire Format

The SDK uses JSON field names that match the backend API exactly:

- `session_id` (snake_case)
- `project_path` → `cwd` for tool observations
- `type` → `tool_name` for tool observations
- `requiredConcepts` (camelCase)
- `observationId` (camelCase)

**List columns do not all arrive the same way.** `Facts`, `Concepts`, `FilesRead` and
`FilesModified` are JSONB columns that the backend serializes as **JSON-encoded strings**
for the WebUI, so a live observation carries `concepts: "[\"allergy\",\"peanut\"]"` rather
than a JSON array. `RefinedFromIds` is different: it is a `TEXT` column holding
comma-separated UUIDs (`"uuid-1,uuid-2"`), and nothing JSON-encodes it — the backend joins
the ids with `,`. `dto.StringList` accepts all three shapes (real array, JSON-encoded array,
comma-separated string) and never returns an error, so one unexpected list column can never
invalidate the observation carrying it.

See `dto/` package for full wire format details.

See [Go SDK Design Document](../../docs/drafts/go-sdk-design.md) for architecture and implementation details.

## License

MIT
