package langchaingo

import (
	"context"
	"errors"
	"testing"

	cortexmem "github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

// mockClient is a minimal mock for testing.
type mockClient struct {
	cortexmem.Client
	iclErr error
}

func (m *mockClient) BuildICLPrompt(_ context.Context, _ dto.ICLPromptRequest) (*dto.ICLPromptResult, error) {
	if m.iclErr != nil {
		return nil, m.iclErr
	}
	return &dto.ICLPromptResult{Prompt: "test prompt", ExperienceCount: "3"}, nil
}

func (m *mockClient) Close() error { return nil }

// testLogger captures log messages for assertions.
type testLogger struct {
	msgs []string
}

func (l *testLogger) Debug(msg string, args ...any) { l.msgs = append(l.msgs, msg) }
func (l *testLogger) Info(msg string, args ...any)  { l.msgs = append(l.msgs, msg) }
func (l *testLogger) Warn(msg string, args ...any)  { l.msgs = append(l.msgs, msg) }
func (l *testLogger) Error(msg string, args ...any)  { l.msgs = append(l.msgs, msg) }

func TestNewMemory_NilClient_Panics(t *testing.T) {
	defer func() {
		if r := recover(); r == nil {
			t.Error("expected panic for nil client")
		}
	}()
	NewMemory(nil, "/tmp/test")
}

func TestNewMemory_Defaults(t *testing.T) {
	m := NewMemory(&mockClient{}, "/tmp/test")
	if m.maxChars != 4000 {
		t.Errorf("expected default maxChars 4000, got %d", m.maxChars)
	}
	if m.memoryKey != "history" {
		t.Errorf("expected default memoryKey 'history', got %q", m.memoryKey)
	}
}

func TestNewMemory_OptionsApplied(t *testing.T) {
	m := NewMemory(&mockClient{}, "/tmp/test",
		WithMemoryMaxChars(2000),
		WithMemoryKey("ctx"),
		WithMemoryUserID("user1"),
	)
	if m.maxChars != 2000 {
		t.Errorf("expected maxChars 2000, got %d", m.maxChars)
	}
	if m.memoryKey != "ctx" {
		t.Errorf("expected memoryKey 'ctx', got %q", m.memoryKey)
	}
	if m.userID != "user1" {
		t.Errorf("expected userID 'user1', got %q", m.userID)
	}
}

func TestLoadMemoryVariables_EmptyInput(t *testing.T) {
	m := NewMemory(&mockClient{}, "/tmp/test")
	result, err := m.LoadMemoryVariables(context.Background(), map[string]any{})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result["history"] != "" {
		t.Errorf("expected empty history for empty input, got %q", result["history"])
	}
}

func TestLoadMemoryVariables_Success(t *testing.T) {
	m := NewMemory(&mockClient{}, "/tmp/test")
	result, err := m.LoadMemoryVariables(context.Background(), map[string]any{"input": "test query"})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result["history"] != "test prompt" {
		t.Errorf("expected 'test prompt', got %q", result["history"])
	}
}

func TestLoadMemoryVariables_Error_ReturnsEmpty(t *testing.T) {
	mock := &mockClient{iclErr: errors.New("backend unavailable")}
	logger := &testLogger{}
	m := NewMemory(mock, "/tmp/test", WithMemoryLogger(logger))

	result, err := m.LoadMemoryVariables(context.Background(), map[string]any{"input": "test query"})
	// Should NOT return error (graceful degradation)
	if err != nil {
		t.Errorf("expected no error on backend failure, got: %v", err)
	}
	// Should return empty memory
	if result["history"] != "" {
		t.Errorf("expected empty history on error, got %q", result["history"])
	}
	// Should log the error
	if len(logger.msgs) == 0 {
		t.Error("expected error to be logged")
	}
}

func TestMemoryVariables(t *testing.T) {
	m := NewMemory(&mockClient{}, "/tmp/test", WithMemoryKey("ctx"))
	vars := m.MemoryVariables(context.Background())
	if len(vars) != 1 || vars[0] != "ctx" {
		t.Errorf("expected ['ctx'], got %v", vars)
	}
}

func TestGetMemoryKey(t *testing.T) {
	m := NewMemory(&mockClient{}, "/tmp/test", WithMemoryKey("ctx"))
	key := m.GetMemoryKey(context.Background())
	if key != "ctx" {
		t.Errorf("expected 'ctx', got %q", key)
	}
}

func TestSaveContext_NoOp(t *testing.T) {
	m := NewMemory(&mockClient{}, "/tmp/test")
	err := m.SaveContext(context.Background(), map[string]any{}, map[string]any{})
	if err != nil {
		t.Errorf("expected nil error, got: %v", err)
	}
}

func TestClear_NoOp(t *testing.T) {
	m := NewMemory(&mockClient{}, "/tmp/test")
	err := m.Clear(context.Background())
	if err != nil {
		t.Errorf("expected nil error, got: %v", err)
	}
}
