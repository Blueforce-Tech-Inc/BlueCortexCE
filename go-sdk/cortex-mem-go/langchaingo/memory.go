package langchaingo

import (
	"context"
	"fmt"

	"github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

// Memory adapts Cortex CE memory to LangChainGo's Memory interface.
type Memory struct {
	client    cortexmem.Client
	project   string
	userID    string
	sessionID string
}

// MemoryOption configures the Memory.
type MemoryOption func(*Memory)

// WithMemoryProject sets the project path.
func WithMemoryProject(project string) MemoryOption {
	return func(m *Memory) { m.project = project }
}

// WithMemoryUserID sets the user ID.
func WithMemoryUserID(userID string) MemoryOption {
	return func(m *Memory) { m.userID = userID }
}

// WithMemorySessionID sets the session ID.
func WithMemorySessionID(sessionID string) MemoryOption {
	return func(m *Memory) { m.sessionID = sessionID }
}

// NewMemory creates a new Memory for Cortex CE.
func NewMemory(client cortexmem.Client, opts ...MemoryOption) *Memory {
	m := &Memory{
		client:  client,
		project: "default",
	}
	for _, opt := range opts {
		opt(m)
	}
	return m
}

// LoadMemoryVars implements LangChainGo's Memory interface.
// It loads memory variables from Cortex CE.
func (m *Memory) LoadMemoryVars(ctx context.Context, inputs map[string]any) (map[string]any, error) {
	query, ok := inputs["input"].(string)
	if !ok || query == "" {
		return map[string]any{"history": ""}, nil
	}

	// Build ICL prompt
	iclReq := dto.ICLPromptRequest{
		ProjectPath: m.project,
		Task:       query,
		MaxChars:   2000,
		SessionID:  m.sessionID,
		UserID:     m.userID,
	}

	result, err := m.client.BuildICLPrompt(ctx, iclReq)
	if err != nil {
		return map[string]any{"history": ""}, nil
	}

	return map[string]any{
		"history": result.Prompt,
	}, nil
}

// SaveContext implements LangChainGo's Memory interface.
// It saves context to Cortex CE.
func (m *Memory) SaveContext(ctx context.Context, inputs map[string]any, outputs map[string]any) error {
	input, _ := inputs["input"].(string)
	output, _ := outputs["output"].(string)

	if input != "" {
		_ = m.client.RecordUserPrompt(ctx, dto.UserPromptRequest{
			ProjectPath: m.project,
			SessionID:   m.sessionID,
			Content:     input,
		})
	}

	if output != "" {
		_ = m.client.RecordObservation(ctx, dto.ObservationRequest{
			ProjectPath: m.project,
			SessionID:   m.sessionID,
			Type:        "response",
			Content:     output,
		})
	}

	return nil
}

// Clear implements LangChainGo's Memory interface.
func (m *Memory) Clear(ctx context.Context) error {
	// No-op for now
	return nil
}

// GetMemoryVariables returns the memory variables.
func (m *Memory) GetMemoryVariables() []string {
	return []string{"history"}
}

// Compile-time check
var _ Memory = Memory{}
