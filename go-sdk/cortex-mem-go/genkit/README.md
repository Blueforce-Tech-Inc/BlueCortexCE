# Genkit Retriever Integration

This module provides a [Genkit](https://genkit.dev) Retriever adapter for Cortex CE memory.

## Installation

```bash
go get github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go/genkit
```

## Usage

```go
import (
    "context"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go/genkit"
)

// Create Cortex CE client
client := cortexmem.NewClient(
    cortexmem.WithBaseURL("http://127.0.0.1:37777"),
)

// Create Genkit Retriever (project is a required positional argument)
retriever := genkit.NewRetriever(client, "/my-project",
    genkit.WithRetrieverSource("tool_result"),
    genkit.WithRetrieverCount(4),
)

// Use with Genkit
output, err := retriever.Retrieve(ctx, genkit.RetrieverInput{
    Query:   "What files were read?",
    Project: "/my-project", // optional if set in NewRetriever
})
// output.Documents[i].Content - document text
// output.Documents[i].Metadata - metadata (id, task, qualityScore, reuseCondition, createdAt)
```

## Options

| Option | Default | Description |
|--------|---------|-------------|
| `WithRetrieverSource(source)` | *(none)* | Set source attribute filter |
| `WithRetrieverCount(n)` | `4` | Set maximum number of results |
| `WithRetrieverUserID(userID)` | *(none)* | Set user ID for user-scoped memory |
| `WithRetrieverLogger(logger)` | `slog` default | Custom logger (compatible with `*slog.Logger`) |

## Document Structure

```go
type Document struct {
    Content  string         `json:"content"`
    Metadata map[string]any `json:"metadata"`
}

// Metadata fields:
//   - id: experience ID
//   - task: the original task description
//   - quality_score: quality score of the experience
//   - reuse_condition: reuse condition
//   - created_at: creation timestamp
```

## Interface

```go
// Retrieve performs semantic search and returns Genkit-compatible documents.
func (r *Retriever) Retrieve(ctx context.Context, input RetrieverInput) (RetrieverOutput, error)
```
