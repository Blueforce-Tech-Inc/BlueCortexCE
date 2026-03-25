package eino

import (
	"context"
	"errors"
	"log"
	"testing"

	cortexmem "github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

// mockClient is a minimal mock for testing integration layers.
type mockClient struct {
	cortexmem.Client
	retrieveErr error
}

func (m *mockClient) RetrieveExperiences(_ context.Context, _ dto.ExperienceRequest) ([]dto.Experience, error) {
	if m.retrieveErr != nil {
		return nil, m.retrieveErr
	}
	return []dto.Experience{{Task: "test", Strategy: "s", Outcome: "o"}}, nil
}

func (m *mockClient) Close() error { return nil }

// testWriter captures log output for assertions.
type testWriter struct {
	output *string
}

func (w *testWriter) Write(p []byte) (int, error) {
	*w.output += string(p)
	return len(p), nil
}

func TestNewRetriever_NilClient_Panics(t *testing.T) {
	defer func() {
		if r := recover(); r == nil {
			t.Error("expected panic for nil client")
		}
	}()
	NewRetriever(nil, "/tmp/test")
}

func TestNewRetriever_DefaultCount(t *testing.T) {
	r := NewRetriever(&mockClient{}, "/tmp/test")
	if r.count != 4 {
		t.Errorf("expected default count 4, got %d", r.count)
	}
	if r.project != "/tmp/test" {
		t.Errorf("expected project '/tmp/test', got %q", r.project)
	}
}

func TestNewRetriever_OptionsApplied(t *testing.T) {
	r := NewRetriever(&mockClient{}, "/tmp/test",
		WithRetrieverCount(10),
		WithRetrieverSource("manual"),
		WithRetrieverUserID("user1"),
	)
	if r.count != 10 {
		t.Errorf("expected count 10, got %d", r.count)
	}
	if r.source != "manual" {
		t.Errorf("expected source 'manual', got %q", r.source)
	}
	if r.userID != "user1" {
		t.Errorf("expected userID 'user1', got %q", r.userID)
	}
}

func TestRetrieve_EmptyQuery_ReturnsNil(t *testing.T) {
	r := NewRetriever(&mockClient{}, "/tmp/test")
	result, err := r.Retrieve(context.Background(), "")
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if result != nil {
		t.Errorf("expected nil for empty query, got %v", result)
	}
}

func TestRetrieve_Success(t *testing.T) {
	r := NewRetriever(&mockClient{}, "/tmp/test", WithRetrieverCount(5))
	result, err := r.Retrieve(context.Background(), "test query")
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if len(result) != 1 {
		t.Errorf("expected 1 result, got %d", len(result))
	}
	if result[0].Task != "test" {
		t.Errorf("expected task 'test', got %q", result[0].Task)
	}
}

func TestRetrieve_Error_ReturnsError(t *testing.T) {
	mock := &mockClient{retrieveErr: errors.New("backend unavailable")}
	r := NewRetriever(mock, "/tmp/test")
	result, err := r.Retrieve(context.Background(), "test query")
	if err == nil {
		t.Error("expected error")
	}
	if result != nil {
		t.Errorf("expected nil result on error, got %v", result)
	}
	if !errors.Is(err, mock.retrieveErr) {
		t.Errorf("expected wrapped error, got: %v", err)
	}
}

func TestRetrieve_CustomLogger(t *testing.T) {
	var logged string
	customLogger := log.New(&testWriter{output: &logged}, "", 0)
	mock := &mockClient{retrieveErr: errors.New("fail")}
	r := NewRetriever(mock, "/tmp/test", WithRetrieverLogger(customLogger))
	r.Retrieve(context.Background(), "q")
	if logged == "" {
		t.Error("expected error to be logged")
	}
}
