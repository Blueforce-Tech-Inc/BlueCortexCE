# Genkit Retriever Integration

This module provides a [Genkit](https://genkit.dev) Retriever adapter for Cortex CE memory.

## Installation

```bash
go get github.com/abforce/cortex-ce/cortex-mem-go/genkit
```

## Usage

```go
import (
    "github.com/abforce/cortex-ce/cortex-mem-go"
    "github.com/abforce/cortex-ce/cortex-mem-go/genkit"
)

// Create Cortex CE client
client := cortexmem.NewClient(
    cortexmem.WithBaseURL("http://127.0.0.1:37777"),
)

// Create Genkit Retriever
retriever := genkit.NewRetriever(client,
    genkit.WithRetrieverProject("/my-project"),
    genkit.WithRetrieverSource("tool_result"),
    genkit.WithMaxResults(20),
)

// Use with Genkit
docs, err := retriever.Retrieve(ctx, "What files were read?")
// docs[i].Content - document text
// docs[i].Meta - metadata (id, type, source, concepts)
```

## Options

| Option | Description |
|--------|-------------|
| `WithRetrieverProject(project)` | Set project path filter |
| `WithRetrieverSource(source)` | Set source attribute filter |
| `WithMaxResults(max)` | Set maximum results (default: 20) |

## Document Structure

```go
type Document struct {
    Content string                 `json:"content"`
    Meta    map[string]interface{} `json:"meta"`
}

// Meta fields:
//   - id: observation ID
//   - type: observation type
//   - source: attribution source
//   - concepts: extracted concepts
```

## Interface

Implements Genkit's Retriever interface:

```go
type Retriever interface {
    Retrieve(ctx context.Context, query string) ([]Document, error)
}
```
