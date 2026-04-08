# LangChainGo Memory Integration

This module provides a [LangChainGo](https://github.com/tmc/langchaingo) Memory adapter for Cortex CE memory.

## Installation

```bash
go get github.com/abforce/cortex-ce/cortex-mem-go/langchaingo
```

## Usage

```go
import (
    "context"
    "github.com/abforce/cortex-ce/cortex-mem-go"
    "github.com/abforce/cortex-ce/cortex-mem-go/langchaingo"
)

// Create Cortex CE client
client := cortexmem.NewClient(
    cortexmem.WithBaseURL("http://127.0.0.1:37777"),
)

// Create LangChainGo Memory (project is a required positional argument)
memory := langchaingo.NewMemory(client, "/my-project",
    langchaingo.WithMemoryUserID("user-123"),
)

// Load memory variables for LLM context
vars, err := memory.LoadMemoryVariables(ctx, map[string]any{"input": "hello"})
// vars["history"] contains the ICL prompt

// Save context after LLM response (no-op, Cortex CE captures via session lifecycle)
err = memory.SaveContext(ctx, 
    map[string]any{"input": "hello"},
    map[string]any{"output": "Hi there!"},
)
```

## Options

| Option | Default | Description |
|--------|---------|-------------|
| `WithMemoryUserID(userID)` | *(none)* | Set user ID for user-scoped memory |
| `WithMemoryMaxChars(n)` | `4000` | Set maximum ICL prompt characters |
| `WithMemoryKey(key)` | `"history"` | Set memory variable key |
| `WithMemoryLogger(logger)` | `slog` default | Custom logger (compatible with `*slog.Logger`) |

## Interface

Implements LangChainGo's Memory interface:

```go
type Memory interface {
    LoadMemoryVariables(ctx context.Context, inputs map[string]any) (map[string]any, error)
    SaveContext(ctx context.Context, inputs map[string]any, outputs map[string]any) error
    Clear(ctx context.Context) error
}
```
