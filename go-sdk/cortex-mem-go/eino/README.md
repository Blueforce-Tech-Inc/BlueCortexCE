# Eino Retriever Integration

This module provides a [Eino](https://github.com/cloudengineai/eino) Retriever adapter for Cortex CE memory.

## Installation

```bash
go get github.com/abforce/cortex-ce/cortex-mem-go/eino
```

## Usage

```go
import (
    "github.com/abforce/cortex-ce/cortex-mem-go"
    "github.com/abforce/cortex-ce/cortex-mem-go/eino"
)

// Create Cortex CE client
client := cortexmem.NewClient(
    cortexmem.WithBaseURL("http://127.0.0.1:37777"),
)

// Create Eino Retriever
retriever := eino.NewRetriever(client,
    eino.WithRetrieverProject("/my-project"),
    eino.WithRetrieverSource("tool_result"),
)

// Use with Eino
results, err := retriever.Retrieve(ctx, "What files were read?")
```

## Options

| Option | Description |
|--------|-------------|
| `WithRetrieverProject(project)` | Set project path filter |
| `WithRetrieverSource(source)` | Set source attribute filter |

## Interface

Implements Eino's Retriever interface:

```go
type Retriever interface {
    Retrieve(ctx context.Context, query string, opts ...any) ([]*dto.Experience, error)
}
```
