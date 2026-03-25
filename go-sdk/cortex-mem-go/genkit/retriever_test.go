package genkit

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
	retrieveErr error
}

func (m *mockClient) RetrieveExperiences(_ context.Context, _ dto.ExperienceRequest) ([]dto.Experience, error) {
	if m.retrieveErr != nil {
		return nil, m.retrieveErr
	}
	return []dto.Experience{
		{ID: "1", Task: "test", Strategy: "use Go", Outcome: "works well", QualityScore: 0.9},
	}, nil
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

func TestNewRetriever_NilClient_Panics(t *testing.T) {
	defer func() {
		if r := recover(); r == nil {
			t.Error("expected panic for nil client")
		}
	}()
	NewRetriever(nil)
}

func TestNewRetriever_DefaultCount(t *testing.T) {
	r := NewRetriever(&mockClient{})
	if r.count != 4 {
		t.Errorf("expected default count 4, got %d", r.count)
	}
}

func TestNewRetriever_OptionsApplied(t *testing.T) {
	r := NewRetriever(&mockClient{},
		WithRetrieverProject("/tmp/test"),
		WithRetrieverCount(10),
		WithRetrieverSource("manual"),
		WithRetrieverUserID("user1"),
	)
	if r.project != "/tmp/test" {
		t.Errorf("expected project '/tmp/test', got %q", r.project)
	}
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

func TestRetrieve_EmptyQuery_ReturnsEmpty(t *testing.T) {
	r := NewRetriever(&mockClient{})
	output, err := r.Retrieve(context.Background(), RetrieverInput{})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if len(output.Documents) != 0 {
		t.Errorf("expected 0 documents for empty query, got %d", len(output.Documents))
	}
}

func TestRetrieve_Success(t *testing.T) {
	r := NewRetriever(&mockClient{}, WithRetrieverProject("/tmp/test"))
	output, err := r.Retrieve(context.Background(), RetrieverInput{Query: "What is Go?"})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if len(output.Documents) != 1 {
		t.Errorf("expected 1 document, got %d", len(output.Documents))
	}
	doc := output.Documents[0]
	if doc.Content == "" {
		t.Error("expected non-empty content")
	}
	if doc.Metadata["task"] != "test" {
		t.Errorf("expected task 'test' in metadata, got %v", doc.Metadata["task"])
	}
}

func TestRetrieve_PerCallOverride(t *testing.T) {
	r := NewRetriever(&mockClient{}, WithRetrieverProject("/tmp/default"))
	output, err := r.Retrieve(context.Background(), RetrieverInput{
		Query:   "test",
		Project: "/tmp/override",
		Count:   2,
		Source:  "api",
		UserID:  "user2",
	})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if len(output.Documents) != 1 {
		t.Errorf("expected 1 document, got %d", len(output.Documents))
	}
}

func TestRetrieve_Error_ReturnsError(t *testing.T) {
	mock := &mockClient{retrieveErr: errors.New("backend unavailable")}
	r := NewRetriever(mock)
	output, err := r.Retrieve(context.Background(), RetrieverInput{Query: "test"})
	if err == nil {
		t.Error("expected error")
	}
	if len(output.Documents) != 0 {
		t.Errorf("expected 0 documents on error, got %d", len(output.Documents))
	}
}

func TestRetrieve_CustomLogger(t *testing.T) {
	logger := &testLogger{}
	mock := &mockClient{retrieveErr: errors.New("fail")}
	r := NewRetriever(mock, WithRetrieverLogger(logger))
	r.Retrieve(context.Background(), RetrieverInput{Query: "q"})
	if len(logger.msgs) == 0 {
		t.Error("expected error to be logged")
	}
}
