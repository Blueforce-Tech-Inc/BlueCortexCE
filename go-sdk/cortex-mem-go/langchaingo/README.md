# LangChainGo Memory Integration

This module provides a [LangChainGo](https://github.com/tmc/langchaingo) Memory adapter for Cortex CE memory.

## Installation

```bash
go get github.com/abforce/cortex-ce/cortex-mem-go/langchaingo
```

## Usage

```go
import (
    "github.com/abforce/cortex-ce/cortex-mem-go"
    "github.com/abforce/cortex-ce/cortex-mem-go/langchaingo"
)

// Create Cortex CE client
client := cortexmem.NewClient(
    cortexmem.WithBaseURL("http://127.0.0.1:37777"),
)

// Create LangChainGo Memory
memory := langchaingo.NewMemory(client,
    langchaingo.WithMemoryProject("/my-project"),
    langchaingo.WithMemoryUserID("user-123"),
    langchaingo.WithMemorySessionID("session-456"),
)

// Load memory variables for LLM context
vars, err := memory.LoadMemoryVars(ctx, map[string]any{"input": "hello"})
// vars["history"] contains the ICL prompt

// Save context after LLM response
err = memory.SaveContext(ctx, 
    map[string]any{"input": "hello"},
    map[string]any{"output": "Hi there!"},
)
```

## Options

| Option | Description |
|--------|-------------|
| `WithMemoryProject(project)` | Set project path |
| `WithMemoryUserID(userID)` | Set user ID for extraction |
| `WithMemorySessionID(sessionID)` | Set session ID |

## Interface

Implements LangChainGo's Memory interface:

```go
type Memory interface {
    LoadMemoryVars(ctx context.Context, inputs map[string]any) (map[string]any, error)
    SaveContext(ctx context.Context, inputs map[string]any, outputs map[string]any) error
    Clear(ctx context.Context) error
}
```
