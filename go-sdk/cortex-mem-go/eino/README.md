# Eino Retriever Integration

This module provides a [Eino](https://github.com/cloudengineai/eino) Retriever adapter for Cortex CE memory.

## Installation

```bash
go get github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go/eino
```

## Usage

```go
import (
    "context"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go/eino"
)

// Create Cortex CE client
client := cortexmem.NewClient(
    cortexmem.WithBaseURL("http://127.0.0.1:37777"),
)

// Create Eino Retriever (project is a required positional argument)
retriever := eino.NewRetriever(client, "/my-project",
    eino.WithRetrieverSource("tool_result"),
)

// Use with Eino
results, err := retriever.Retrieve(ctx, "What files were read?")
```

## Options

| Option | Default | Description |
|--------|---------|-------------|
| `WithRetrieverSource(source)` | *(none)* | Set source attribute filter |
| `WithRetrieverCount(n)` | `4` | Set maximum number of results |
| `WithRetrieverUserID(userID)` | *(none)* | Set user ID for user-scoped memory |
| `WithRetrieverLogger(logger)` | `slog` default | Custom logger (compatible with `*slog.Logger`) |

## Interface

Implements Eino's Retriever interface:

```go
type Retriever interface {
    Retrieve(ctx context.Context, query string, opts ...any) ([]dto.Experience, error)
}
```
