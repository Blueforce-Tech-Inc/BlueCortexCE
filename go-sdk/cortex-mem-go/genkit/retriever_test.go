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
		{ID: "1", Task: "test", Strategy: "use Go", Outcome: "works well", QualityScore: 0.9, CreatedAt: "2026-03-26T10:00:00Z"},
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
	NewRetriever(nil, "/tmp/test")
}

func TestNewRetriever_DefaultCount(t *testing.T) {
	r := NewRetriever(&mockClient{}, "/tmp/test")
	if r.count != 4 {
		t.Errorf("expected default count 4, got %d", r.count)
	}
}

func TestNewRetriever_OptionsApplied(t *testing.T) {
	r := NewRetriever(&mockClient{}, "/tmp/test",
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
	r := NewRetriever(&mockClient{}, "/tmp/test")
	output, err := r.Retrieve(context.Background(), RetrieverInput{})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if len(output.Documents) != 0 {
		t.Errorf("expected 0 documents for empty query, got %d", len(output.Documents))
	}
}

func TestRetrieve_Success(t *testing.T) {
	r := NewRetriever(&mockClient{}, "/tmp/test")
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
	if doc.Metadata["id"] != "1" {
		t.Errorf("expected id '1' in metadata, got %v", doc.Metadata["id"])
	}
	if doc.Metadata["qualityScore"] != float32(0.9) {
		t.Errorf("expected qualityScore 0.9 in metadata, got %v", doc.Metadata["qualityScore"])
	}
	if doc.Metadata["createdAt"] != "2026-03-26T10:00:00Z" {
		t.Errorf("expected createdAt in metadata, got %v", doc.Metadata["createdAt"])
	}
}

func TestRetrieve_PerCallOverride(t *testing.T) {
	r := NewRetriever(&mockClient{}, "/tmp/default")
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
	r := NewRetriever(mock, "/tmp/test")
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
	r := NewRetriever(mock, "/tmp/test", WithRetrieverLogger(logger))
	r.Retrieve(context.Background(), RetrieverInput{Query: "q"})
	if len(logger.msgs) == 0 {
		t.Error("expected error to be logged")
	}
}

func TestRetrieve_ZeroCount_FallsBackToDefault(t *testing.T) {
	// When Count <= 0 in input, it should fall back to the retriever's default count.
	var capturedReq dto.ExperienceRequest
	mock := &captureClient{req: &capturedReq}
	r := NewRetriever(mock, "/tmp/test", WithRetrieverCount(7))
	output, err := r.Retrieve(context.Background(), RetrieverInput{
		Query: "test",
		Count: 0, // Should fall back to retriever default (7)
	})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if capturedReq.Count != 7 {
		t.Errorf("expected count=7 (fallback to default), got %d", capturedReq.Count)
	}
	if len(output.Documents) != 1 {
		t.Errorf("expected 1 document, got %d", len(output.Documents))
	}
}

func TestRetrieve_NegativeCount_FallsBackToDefault(t *testing.T) {
	var capturedReq dto.ExperienceRequest
	mock := &captureClient{req: &capturedReq}
	r := NewRetriever(mock, "/tmp/test", WithRetrieverCount(3))
	output, err := r.Retrieve(context.Background(), RetrieverInput{
		Query: "test",
		Count: -5, // Negative should fall back to default
	})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if capturedReq.Count != 3 {
		t.Errorf("expected count=3 (fallback to default), got %d", capturedReq.Count)
	}
	if len(output.Documents) != 1 {
		t.Errorf("expected 1 document, got %d", len(output.Documents))
	}
}

func TestRetrieve_EmptyStrategy_HandlesGracefully(t *testing.T) {
	// When Strategy is empty, content should just be Outcome (no leading newline)
	mock := &emptyStrategyClient{}
	r := NewRetriever(mock, "/tmp/test")
	output, err := r.Retrieve(context.Background(), RetrieverInput{Query: "test"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(output.Documents) != 1 {
		t.Fatalf("expected 1 document, got %d", len(output.Documents))
	}
	doc := output.Documents[0]
	if doc.Content != "outcome only" {
		t.Errorf("expected 'outcome only', got %q", doc.Content)
	}
}

func TestRetrieve_BothEmpty_ProducesEmptyContent(t *testing.T) {
	mock := &bothEmptyClient{}
	r := NewRetriever(mock, "/tmp/test")
	output, err := r.Retrieve(context.Background(), RetrieverInput{Query: "test"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if output.Documents[0].Content != "" {
		t.Errorf("expected empty content, got %q", output.Documents[0].Content)
	}
}

func TestRetrieve_InputOverridesEmptyRetrieverFields(t *testing.T) {
	// When retriever has empty fields, input should provide values
	var capturedReq dto.ExperienceRequest
	mock := &captureClient{req: &capturedReq}
	r := NewRetriever(mock, "/tmp/test") // project set, but input overrides it
	output, err := r.Retrieve(context.Background(), RetrieverInput{
		Query:   "test",
		Project: "/input/project",
		Source:  "input-source",
		UserID:  "input-user",
		Count:   5,
	})
	if err != nil {
		t.Errorf("unexpected error: %v", err)
	}
	if capturedReq.Project != "/input/project" {
		t.Errorf("expected project='/input/project', got %q", capturedReq.Project)
	}
	if capturedReq.Source != "input-source" {
		t.Errorf("expected source='input-source', got %q", capturedReq.Source)
	}
	if capturedReq.UserID != "input-user" {
		t.Errorf("expected userID='input-user', got %q", capturedReq.UserID)
	}
	if capturedReq.Count != 5 {
		t.Errorf("expected count=5, got %d", capturedReq.Count)
	}
	if len(output.Documents) != 1 {
		t.Errorf("expected 1 document, got %d", len(output.Documents))
	}
}

// captureClient captures the ExperienceRequest for assertion.
type captureClient struct {
	cortexmem.Client
	req *dto.ExperienceRequest
}

func (m *captureClient) RetrieveExperiences(_ context.Context, req dto.ExperienceRequest) ([]dto.Experience, error) {
	*m.req = req
	return []dto.Experience{{ID: "1", Task: "test", Strategy: "s", Outcome: "o", QualityScore: 0.9, CreatedAt: "2026-03-26T10:00:00Z"}}, nil
}

func (m *captureClient) Close() error { return nil }

// emptyStrategyClient returns an experience with empty Strategy.
type emptyStrategyClient struct {
	cortexmem.Client
}

func (m *emptyStrategyClient) RetrieveExperiences(_ context.Context, _ dto.ExperienceRequest) ([]dto.Experience, error) {
	return []dto.Experience{{ID: "1", Task: "test", Strategy: "", Outcome: "outcome only", QualityScore: 0.9, CreatedAt: "2026-03-26T10:00:00Z"}}, nil
}

func (m *emptyStrategyClient) Close() error { return nil }

// bothEmptyClient returns an experience with both Strategy and Outcome empty.
type bothEmptyClient struct {
	cortexmem.Client
}

func (m *bothEmptyClient) RetrieveExperiences(_ context.Context, _ dto.ExperienceRequest) ([]dto.Experience, error) {
	return []dto.Experience{{ID: "1", Task: "test", Strategy: "", Outcome: "", QualityScore: 0.5, CreatedAt: "2026-03-26T10:00:00Z"}}, nil
}

func (m *bothEmptyClient) Close() error { return nil }
