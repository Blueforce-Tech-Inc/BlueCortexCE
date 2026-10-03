> 中文版: [README-zh-CN.md](./README-zh-CN.md)

# Cortex CE Go SDK

Go client library for [Cortex CE](https://github.com/Blueforce-Tech-Inc/BlueCortexCE) — a persistent memory system for AI assistants.

## Features

- **Zero mandatory dependencies** — only uses Go standard library
- **Full API coverage** — 25 methods covering Session, Capture, Retrieval, Management, Extraction, Version, P1
- **Framework integrations** — optional Eino, LangChainGo, and Genkit modules
- **Wire format compatible** — JSON field names match backend API exactly
- **Comprehensive tests** — 359 tests with wire format verification. The root module runs 299 (core 232 + dto 67); the adapter and example modules add 60 more when run from their own directories (eino 8 + genkit 13 + langchaingo 12 + `examples/http-server` 27). See [Testing](#testing) for why `go test ./...` alone only reaches the root module.

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

**This SDK is 9 separate Go modules, not one.** `eino/`, `genkit/`, `langchaingo/`
and each directory under `examples/` carry their own `go.mod`, so a bare
`go test ./...` run from this directory only reaches the root module — 299 of
the 359 tests. The four adapter and example modules it skips are exactly the
ones most likely to rot against an upstream framework upgrade, so run all nine:

```bash
# Run all tests, one module at a time (stops at the first failure)
find . -name go.mod -exec dirname {} \; | sort | while read -r d; do
  (cd "$d" && go test ./... -count=1) || exit 1
done
```

Measured on 2026-10-03, all nine green: core 232 + dto 67 + eino 8 + genkit 13
+ langchaingo 12 + `examples/http-server` 27 = **359**. The other four
`examples/` modules have no test files and report `[no test files]`.

```bash
# Coverage for the root module only (the adapters need their own -cover run)
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

### Required Arguments Are Checked Client-Side

Every argument below must be non-empty. The SDK returns a `ValidationError` and sends
no request. The Java, Python and JS SDKs enforce exactly the same set.

| Method | Required arguments |
|--------|--------------------|
| `StartSession` | `req.SessionID`, `req.ProjectPath` |
| `UpdateSessionUserId` | `sessionID`, `userID` |
| `RecordObservation` | `req.SessionID`, `req.ProjectPath`, `req.ToolName` |
| `RecordSessionEnd` | `req.SessionID`, `req.ProjectPath` |
| `RecordUserPrompt` | `req.SessionID`, `req.PromptText`, `req.ProjectPath` |
| `RetrieveExperiences` | `req.Task` |
| `BuildICLPrompt` | `req.Task` |
| `Search` | `req.Project` |
| `GetObservation` | `id` |
| `GetObservationsByIds` | `ids` — non-empty, at most 100, no blank element |
| `TriggerRefinement` | `projectPath` |
| `SubmitFeedback` | `observationID`, `feedbackType` |
| `UpdateObservation` | `observationID`, plus at least one field to change |
| `DeleteObservation` | `observationID` |
| `GetQualityDistribution` | `projectPath` |
| `TriggerExtraction` | `projectPath` |
| `GetLatestExtraction` | `projectPath`, `templateName` |
| `GetExtractionHistory` | `projectPath`, `templateName`; `limit` must not be negative |

Most of these take a `dto.*Request` struct; the management and extraction methods
take the values positionally. Note that `Search` and `RetrieveExperiences` name
the scoping field `Project` rather than `ProjectPath`, because on those two
endpoints it is sent as `project` on the wire.

**A blank `Project` on `RetrieveExperiences` / `BuildICLPrompt` is the one
required-argument gap worth knowing about.** The table above is accurate —
neither method validates `project`, while `Search` does — but the table says
what is *checked*, not what *happens*, and here the two differ sharply.
`POST /api/memory/experiences` and `POST /api/memory/icl-prompt` pass the value
straight to the repository query and have **no cross-project branch**, so a
missing or empty project matches nothing and returns `200` with an empty result
rather than an error. Verified live: omitting `project`, sending `""`, and
sending a non-existent path all return `200 []`, while a real path returns
experiences. The SDK's own adapters inherit the trap — the `eino` and `genkit`
retrievers and the `langchaingo` memory all take a project string and none
validates it. So an unset project is indistinguishable from "this project
genuinely has no memories", which is worth guarding at the call site even though
the client will not guard it for you. The same applies to any blank scoping
value on these two endpoints, in all four SDKs.

The checks are not decoration, and the two capture methods are the clearest case.
`RecordObservation` is fire-and-forget, so it swallows whatever the backend replies:
an empty `ToolName` comes back as `400 Missing required field: tool_name`, the SDK
logs it and returns nil, and the caller concludes the observation was captured when
the server had just rejected it. An empty `ProjectPath` is quieter still, because the
backend *accepts* it — the record is queued against no project and then appears in no
project-scoped query, with no error anywhere.

`Search` has the same shape of hazard: the SDK always sends `project`, and
`GET /api/search?project=` answers `200` with an empty result set, so a caller who
forgot the argument would read "no matches" rather than "your call was malformed".

`ListObservations` and the argument-free getters (`GetStats`, `GetProjects`,
`GetModes`, `GetSettings`, `GetVersion`, `HealthCheck`) require nothing. `GetStats`
takes an optional project filter.

That is a statement about required arguments, not about blast radius, and on
this one method the two point in opposite directions. `ListObservations` is the
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
