package langchaingo

import (
	"context"
	"fmt"
	"log/slog"
	"os"

	cortexmem "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
	"github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/dto"
)

// Memory adapts Cortex CE memory to LangChainGo's Memory interface.
// It provides ICL prompt generation from historical experiences.
type Memory struct {
	client    cortexmem.Client
	project   string
	maxChars  int
	memoryKey string
	userID    string
	logger    cortexmem.Logger
}

// MemoryOption configures the Memory.
type MemoryOption func(*Memory)

// WithMemoryProject sets the project path.
func WithMemoryProject(project string) MemoryOption {
	return func(m *Memory) { m.project = project }
}

// WithMemoryMaxChars sets the maximum ICL prompt characters. Default: 4000.
//
// Non-positive values are NOT clamped. 0 is dropped by the wire format's
// omitempty, so the backend applies its own 4000 default; a negative value is
// sent as-is and the backend clamps it to 100, truncating the prompt to roughly
// 53 characters while still reporting HTTP 200 and a non-zero experienceCount.
// The backend echoes the value it actually applied in ICLPromptResult.MaxChars,
// but this adapter keeps only result.Prompt and discards that signal, so the
// truncation is invisible to callers — see P2-36 in
// docs/drafts/backend-review-findings.md.
func WithMemoryMaxChars(n int) MemoryOption {
	return func(m *Memory) { m.maxChars = n }
}

// WithMemoryKey sets the memory variable key (default: "history").
func WithMemoryKey(key string) MemoryOption {
	return func(m *Memory) { m.memoryKey = key }
}

// WithMemoryUserID sets the user ID for user-scoped memory.
func WithMemoryUserID(userID string) MemoryOption {
	return func(m *Memory) { m.userID = userID }
}

// WithMemoryLogger sets a custom logger for error reporting.
// The logger must implement the cortexmem.Logger interface (compatible with *slog.Logger).
func WithMemoryLogger(l cortexmem.Logger) MemoryOption {
	return func(m *Memory) { m.logger = l }
}

// NewMemory creates a new Memory backed by Cortex CE.
func NewMemory(client cortexmem.Client, project string, opts ...MemoryOption) *Memory {
	if client == nil {
		panic("langchaingo.NewMemory: client must not be nil")
	}
	m := &Memory{
		client:    client,
		project:   project,
		maxChars:  4000,
		memoryKey: "history",
		logger:    slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelDebug})),
	}
	for _, opt := range opts {
		opt(m)
	}
	return m
}

// GetMemoryKey returns the key used for memory variables.
func (m *Memory) GetMemoryKey(_ context.Context) string {
	return m.memoryKey
}

// MemoryVariables returns the list of memory variable keys.
func (m *Memory) MemoryVariables(_ context.Context) []string {
	return []string{m.memoryKey}
}

// LoadMemoryVariables loads memory variables by building an ICL prompt
// from Cortex CE's historical experiences.
// The "input" key from inputs is used as the search query.
//
// Error strategy: this adapter degrades to an empty memory instead of
// propagating, because it is invoked inside a prompt-assembly chain where an
// error would break the whole call. The Eino and Genkit retrievers do the
// opposite on purpose. Both behaviours are intentional.
func (m *Memory) LoadMemoryVariables(ctx context.Context, inputs map[string]any) (map[string]any, error) {
	query := ""
	if t, ok := inputs["input"]; ok {
		query = fmt.Sprintf("%v", t)
	}

	if query == "" {
		return map[string]any{m.memoryKey: ""}, nil
	}

	result, err := m.client.BuildICLPrompt(ctx, dto.ICLPromptRequest{
		Task:     query,
		Project:  m.project,
		MaxChars: m.maxChars,
		UserID:   m.userID,
	})
	if err != nil {
		// Log the error for observability — callers can't distinguish
		// empty memory from "backend unavailable" without this.
		m.logger.Warn("BuildICLPrompt failed", "project", m.project, "error", err)
		// Return empty memory on error — don't break the chain.
		// Downstream AI models will operate without memory context,
		// but at least the error is visible in logs.
		return map[string]any{m.memoryKey: ""}, nil
	}

	return map[string]any{m.memoryKey: result.Prompt}, nil
}

// SaveContext is a no-op. Cortex CE captures experiences via its own
// session lifecycle (RecordObservation), not via SaveContext calls.
func (m *Memory) SaveContext(_ context.Context, _, _ map[string]any) error {
	return nil
}

// Clear is a no-op. Memory clearing is managed by Cortex CE's refine cycle.
func (m *Memory) Clear(_ context.Context) error {
	return nil
}
