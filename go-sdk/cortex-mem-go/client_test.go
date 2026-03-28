package cortexmem_test

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	cortexmem "github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

// newTestClient creates a client pointing at the given test server.
func newTestClient(server *httptest.Server) cortexmem.Client {
	return cortexmem.NewClient(cortexmem.WithBaseURL(server.URL))
}

// ==================== Wire Format Tests ====================
// These tests verify that JSON field names match backend expectations exactly.

func TestObservationRequest_WireFormat(t *testing.T) {
	// Backend expects: session_id, cwd, tool_name, tool_input, tool_response
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if body["session_id"] != "sess-1" {
			t.Errorf("expected session_id=sess-1, got %v", body["session_id"])
		}
		if body["cwd"] != "/path/to/project" {
			t.Errorf("expected cwd=/path/to/project, got %v", body["cwd"])
		}
		if body["tool_name"] != "Read" {
			t.Errorf("expected tool_name=Read, got %v", body["tool_name"])
		}
		if body["tool_input"] == nil {
			t.Error("expected tool_input to be present")
		}
		if body["tool_response"] == nil {
			t.Error("expected tool_response to be present")
		}
		// These should NOT exist
		if body["project_path"] != nil {
			t.Error("project_path should not be in wire format (should be 'cwd')")
		}
		if body["type"] != nil {
			t.Error("'type' should not be in wire format (should be 'tool_name')")
		}
		if body["content"] != nil {
			t.Error("'content' should not be in wire format (should be 'tool_input'/'tool_response')")
		}

		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	req := dto.ObservationRequest{
		SessionID:    "sess-1",
		ProjectPath:  "/path/to/project",
		ToolName:     "Read",
		ToolInput:    map[string]any{"file": "main.go"},
		ToolResponse: map[string]any{"content": "..."},
	}
	err := client.RecordObservation(context.Background(), req)
	if err != nil {
		t.Fatalf("RecordObservation failed: %v", err)
	}
}

func TestUserPromptRequest_WireFormat(t *testing.T) {
	// Backend expects: session_id, prompt_text, prompt_number, cwd
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if body["session_id"] != "sess-1" {
			t.Errorf("expected session_id, got %v", body["session_id"])
		}
		if body["prompt_text"] != "Hello world" {
			t.Errorf("expected prompt_text=Hello world, got %v", body["prompt_text"])
		}
		if body["cwd"] != "/project" {
			t.Errorf("expected cwd=/project, got %v", body["cwd"])
		}
		// Should NOT have these
		if body["content"] != nil {
			t.Error("'content' should not be in wire format (should be 'prompt_text')")
		}
		if body["project_path"] != nil {
			t.Error("project_path should not be in wire format (should be 'cwd')")
		}

		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	req := dto.UserPromptRequest{
		SessionID:   "sess-1",
		PromptText:  "Hello world",
		ProjectPath: "/project",
	}
	err := client.RecordUserPrompt(context.Background(), req)
	if err != nil {
		t.Fatalf("RecordUserPrompt failed: %v", err)
	}
}

func TestSessionEndRequest_WireFormat(t *testing.T) {
	// Backend expects: session_id, cwd, last_assistant_message
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if body["session_id"] != "sess-1" {
			t.Errorf("expected session_id, got %v", body["session_id"])
		}
		if body["cwd"] != "/project" {
			t.Errorf("expected cwd, got %v", body["cwd"])
		}
		if body["last_assistant_message"] != "Done!" {
			t.Errorf("expected last_assistant_message=Done!, got %v", body["last_assistant_message"])
		}
		// Should NOT have these
		if body["reason"] != nil {
			t.Error("'reason' should not be in wire format (should be 'last_assistant_message')")
		}
		if body["project_path"] != nil {
			t.Error("project_path should not be in wire format (should be 'cwd')")
		}

		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	req := dto.SessionEndRequest{
		SessionID:            "sess-1",
		ProjectPath:          "/project",
		LastAssistantMessage: "Done!",
	}
	err := client.RecordSessionEnd(context.Background(), req)
	if err != nil {
		t.Fatalf("RecordSessionEnd failed: %v", err)
	}
}

func TestExperienceRequest_WireFormat(t *testing.T) {
	// Backend expects: task, project, count, source, requiredConcepts, userId (camelCase!)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if body["task"] != "How to parse JSON?" {
			t.Errorf("expected task, got %v", body["task"])
		}
		if body["project"] != "/project" {
			t.Errorf("expected project, got %v", body["project"])
		}
		// Should NOT have these
		if body["project_path"] != nil {
			t.Error("project_path should not be in wire format (should be 'project')")
		}
		if body["required_concepts"] != nil {
			t.Error("required_concepts should not be in wire format (should be 'requiredConcepts')")
		}

		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`[]`))
	}))
	defer server.Close()

	client := newTestClient(server)
	req := dto.ExperienceRequest{
		Task:             "How to parse JSON?",
		Project:          "/project",
		Count:            4,
		Source:           "tool_result",
		RequiredConcepts: []string{"json"},
		UserID:           "user-1",
	}
	exps, err := client.RetrieveExperiences(context.Background(), req)
	if err != nil {
		t.Fatalf("RetrieveExperiences failed: %v", err)
	}
	if exps == nil {
		t.Fatal("experiences should not be nil")
	}
}

func TestICLPromptRequest_WireFormat(t *testing.T) {
	// Backend expects: task, project, maxChars (camelCase!), userId
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if body["project"] != "/project" {
			t.Errorf("expected project, got %v", body["project"])
		}
		if body["maxChars"] == nil {
			t.Error("expected maxChars (camelCase)")
		}
		// Should NOT have these
		if body["project_path"] != nil {
			t.Error("project_path should not be in wire format (should be 'project')")
		}
		if body["max_chars"] != nil {
			t.Error("max_chars should not be in wire format (should be 'maxChars')")
		}

		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"prompt":"test","experienceCount":0}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	req := dto.ICLPromptRequest{
		Task:     "test",
		Project:  "/project",
		MaxChars: 4000,
	}
	result, err := client.BuildICLPrompt(context.Background(), req)
	if err != nil {
		t.Fatalf("BuildICLPrompt failed: %v", err)
	}
	if result.Prompt != "test" {
		t.Errorf("expected prompt=test, got %s", result.Prompt)
	}
}

func TestExperienceRequest_OmitsEmptyProject(t *testing.T) {
	// When project is empty, it should be omitted from JSON (matches Java SDK behavior)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if _, exists := body["project"]; exists {
			t.Error("empty project should be omitted from wire format")
		}
		if body["task"] != "test" {
			t.Errorf("expected task, got %v", body["task"])
		}

		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`[]`))
	}))
	defer server.Close()

	client := newTestClient(server)
	req := dto.ExperienceRequest{
		Task:    "test",
		Project: "", // empty project should be omitted
	}
	_, err := client.RetrieveExperiences(context.Background(), req)
	if err != nil {
		t.Fatalf("RetrieveExperiences failed: %v", err)
	}
}

func TestICLPromptRequest_OmitsEmptyProject(t *testing.T) {
	// When project is empty, it should be omitted from JSON (matches Java SDK behavior)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if _, exists := body["project"]; exists {
			t.Error("empty project should be omitted from wire format")
		}
		if body["task"] != "test" {
			t.Errorf("expected task, got %v", body["task"])
		}

		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"prompt":"test","experienceCount":0}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	req := dto.ICLPromptRequest{
		Task:    "test",
		Project: "", // empty project should be omitted
	}
	_, err := client.BuildICLPrompt(context.Background(), req)
	if err != nil {
		t.Fatalf("BuildICLPrompt failed: %v", err)
	}
}

func TestTriggerRefinement_QueryParam(t *testing.T) {
	// Backend expects project as QUERY PARAM, not body
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		project := r.URL.Query().Get("project")
		if project != "/path/to/project" {
			t.Errorf("expected project query param=/path/to/project, got %s", project)
		}
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.TriggerRefinement(context.Background(), "/path/to/project")
	if err != nil {
		t.Fatalf("TriggerRefinement failed: %v", err)
	}
}

func TestFeedbackRequest_CamelCase(t *testing.T) {
	// Backend expects: observationId, feedbackType (camelCase)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if body["observationId"] != "obs-1" {
			t.Errorf("expected observationId=obs-1, got %v", body["observationId"])
		}
		if body["feedbackType"] != "SUCCESS" {
			t.Errorf("expected feedbackType=SUCCESS, got %v", body["feedbackType"])
		}
		// Should NOT have snake_case
		if body["observation_id"] != nil {
			t.Error("observation_id should not be in wire format (should be 'observationId')")
		}
		if body["feedback_type"] != nil {
			t.Error("feedback_type should not be in wire format (should be 'feedbackType')")
		}

		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.SubmitFeedback(context.Background(), "obs-1", "SUCCESS", "great")
	if err != nil {
		t.Fatalf("SubmitFeedback failed: %v", err)
	}
}

// ==================== API Method Tests ====================

func TestSessionStartRequest_WireFormat(t *testing.T) {
	// Backend expects: session_id, project_path, user_id (NOT cwd for session start!)
	// Verified: SessionStartRequest uses "project_path" while SessionEndRequest/UserPromptRequest use "cwd"
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		if r.URL.Path != "/api/session/start" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if body["session_id"] != "sess-1" {
			t.Errorf("expected session_id=sess-1, got %v", body["session_id"])
		}
		// Session start uses "project_path", NOT "cwd"!
		if body["project_path"] != "/project" {
			t.Errorf("expected project_path=/project, got %v", body["project_path"])
		}
		if body["user_id"] != "user-42" {
			t.Errorf("expected user_id=user-42, got %v", body["user_id"])
		}
		// Should NOT have these
		if body["sessionId"] != nil {
			t.Error("sessionId should not be in wire format (should be 'session_id')")
		}
		if body["cwd"] != nil {
			t.Error("cwd should not be in wire format for session start (should be 'project_path')")
		}
		if body["userId"] != nil {
			t.Error("userId should not be in wire format (should be 'user_id')")
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]any{
			"session_db_id": "db-123",
			"session_id":    "sess-1",
			"prompt_number": 0,
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	resp, err := client.StartSession(context.Background(), dto.SessionStartRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
		UserID:      "user-42",
	})
	if err != nil {
		t.Fatalf("StartSession failed: %v", err)
	}
	if resp.SessionDBID != "db-123" {
		t.Errorf("expected session_db_id=db-123, got %s", resp.SessionDBID)
	}
}

func TestStartSession(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/session/start" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]any{
			"session_db_id": "db-123",
			"session_id":    "sess-1",
			"prompt_number": 0,
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	resp, err := client.StartSession(context.Background(), dto.SessionStartRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
	})
	if err != nil {
		t.Fatalf("StartSession failed: %v", err)
	}
	if resp.SessionID != "sess-1" {
		t.Errorf("expected session_id=sess-1, got %s", resp.SessionID)
	}
	if resp.SessionDBID != "db-123" {
		t.Errorf("expected session_db_id=db-123, got %s", resp.SessionDBID)
	}
}

func TestSearch(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/search" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		if r.Method != http.MethodGet {
			t.Errorf("expected GET, got %s", r.Method)
		}
		project := r.URL.Query().Get("project")
		if project != "/project" {
			t.Errorf("expected project=/project, got %s", project)
		}
		source := r.URL.Query().Get("source")
		if source != "tool_result" {
			t.Errorf("expected source=tool_result, got %s", source)
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(dto.SearchResult{
			Observations: []dto.Observation{
				{ID: "obs-1", Content: "test"},
			},
			Strategy: "hybrid",
			FellBack: false,
			Count:    1,
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.Search(context.Background(), dto.SearchRequest{
		Project: "/project",
		Query:   "test",
		Source:  "tool_result",
	})
	if err != nil {
		t.Fatalf("Search failed: %v", err)
	}
	if result.Count != 1 {
		t.Errorf("expected count=1, got %d", result.Count)
	}
	if result.Strategy != "hybrid" {
		t.Errorf("expected strategy=hybrid, got %s", result.Strategy)
	}
	if result.FellBack {
		t.Error("expected fell_back=false for hybrid strategy")
	}
}

func TestSearch_FellBack_True(t *testing.T) {
	// When vector search fails, backend falls back to keyword search (fell_back=true)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(dto.SearchResult{
			Observations: []dto.Observation{
				{ID: "obs-1", Content: "keyword match"},
			},
			Strategy: "keyword",
			FellBack: true,
			Count:    1,
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.Search(context.Background(), dto.SearchRequest{
		Project: "/project",
		Query:   "test",
	})
	if err != nil {
		t.Fatalf("Search failed: %v", err)
	}
	if !result.FellBack {
		t.Error("expected fell_back=true when vector search falls back to keyword")
	}
	if result.Strategy != "keyword" {
		t.Errorf("expected strategy=keyword, got %s", result.Strategy)
	}
}

func TestListObservations(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/observations" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		if r.Method != http.MethodGet {
			t.Errorf("expected GET, got %s", r.Method)
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(dto.ObservationsResponse{
			Items:   []dto.Observation{{ID: "obs-1"}},
			Total:   1,
			HasMore: false,
			Offset:  0,
			Limit:   20,
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	resp, err := client.ListObservations(context.Background(), dto.ObservationsRequest{
		Project: "/project",
		Limit:   20,
	})
	if err != nil {
		t.Fatalf("ListObservations failed: %v", err)
	}
	if resp.Total != 1 {
		t.Errorf("expected total=1, got %d", resp.Total)
	}
}

func TestListObservations_QueryParams(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		if q.Get("project") != "/my-proj" {
			t.Errorf("expected project=/my-proj, got %s", q.Get("project"))
		}
		if q.Get("limit") != "50" {
			t.Errorf("expected limit=50, got %s", q.Get("limit"))
		}
		if q.Get("offset") != "10" {
			t.Errorf("expected offset=10, got %s", q.Get("offset"))
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(dto.ObservationsResponse{
			Items:   []dto.Observation{},
			Total:   0,
			HasMore: false,
			Offset:  10,
			Limit:   50,
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.ListObservations(context.Background(), dto.ObservationsRequest{
		Project: "/my-proj",
		Limit:   50,
		Offset:  10,
	})
	if err != nil {
		t.Fatalf("ListObservations failed: %v", err)
	}
}

func TestListObservations_OmitsZeroParams(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		// Zero-value limit and offset should be omitted
		if q.Get("limit") != "" {
			t.Errorf("expected no limit param for zero value, got %s", q.Get("limit"))
		}
		if q.Get("offset") != "" {
			t.Errorf("expected no offset param for zero value, got %s", q.Get("offset"))
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(dto.ObservationsResponse{Items: []dto.Observation{}})
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.ListObservations(context.Background(), dto.ObservationsRequest{
		Project: "/proj",
		// Limit and Offset left as zero values
	})
	if err != nil {
		t.Fatalf("ListObservations failed: %v", err)
	}
}

func TestHealthCheck(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{
			"service": "claude-mem-java",
			"status":  "ok",
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.HealthCheck(context.Background())
	if err != nil {
		t.Fatalf("HealthCheck failed: %v", err)
	}
}

func TestGetQualityDistribution(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		project := r.URL.Query().Get("project")
		if project != "/project" {
			t.Errorf("expected project=/project, got %s", project)
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(dto.QualityDistribution{
			Project: "/project",
			High:    10,
			Medium:  5,
			Low:     2,
			Unknown: 1,
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	dist, err := client.GetQualityDistribution(context.Background(), "/project")
	if err != nil {
		t.Fatalf("GetQualityDistribution failed: %v", err)
	}
	if dist.Total() != 18 {
		t.Errorf("expected total=18, got %d", dist.Total())
	}
}

func TestGetVersion(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/version" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]any{
			"version": "1.0.0",
			"service": "claude-mem-java",
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	version, err := client.GetVersion(context.Background())
	if err != nil {
		t.Fatalf("GetVersion failed: %v", err)
	}
	if version.Version != "1.0.0" {
		t.Errorf("expected version=1.0.0, got %v", version.Version)
	}
	if version.Service != "claude-mem-java" {
		t.Errorf("expected service=claude-mem-java, got %v", version.Service)
	}
}

// ==================== Error Handling Tests ====================

func TestAPIError_StatusCode(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		w.Write([]byte(`{"error":"not found"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.StartSession(context.Background(), dto.SessionStartRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
	})
	if err == nil {
		t.Fatal("expected error for 404")
	}
	if !cortexmem.IsNotFound(err) {
		t.Errorf("expected IsNotFound, got: %v", err)
	}
}

func TestAPIError_ExtractsErrorMessage(t *testing.T) {
	// Verify that JSON error responses have their "error" field extracted into Message
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte(`{"error":"project is required","status":400}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.Search(context.Background(), dto.SearchRequest{Project: "/test"})
	if err == nil {
		t.Fatal("expected error for 400")
	}
	var apiErr *cortexmem.APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected APIError, got: %T", err)
	}
	// Message should be the extracted "error" field, not the raw JSON
	if apiErr.Message != "project is required" {
		t.Errorf("expected extracted message 'project is required', got: %q", apiErr.Message)
	}
}

func TestAPIError_ExtractsMessageField(t *testing.T) {
	// Verify that "message" field is extracted when "error" is absent
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte(`{"message":"service temporarily unavailable"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.HealthCheck(context.Background())
	if err == nil {
		t.Fatal("expected error for 503")
	}
	var apiErr *cortexmem.APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected APIError, got: %T", err)
	}
	if apiErr.Message != "service temporarily unavailable" {
		t.Errorf("expected extracted message, got: %q", apiErr.Message)
	}
}

func TestAPIError_FallbackForNonJSON(t *testing.T) {
	// Non-JSON response body should be returned as-is
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("Internal Server Error"))
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.HealthCheck(context.Background())
	if err == nil {
		t.Fatal("expected error for 500")
	}
	var apiErr *cortexmem.APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected APIError, got: %T", err)
	}
	if apiErr.Message != "Internal Server Error" {
		t.Errorf("expected raw body, got: %q", apiErr.Message)
	}
}

func TestAPIError_FallbackForEmptyBody(t *testing.T) {
	// Server returns error status with no body — should produce a meaningful error message
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadGateway)
		// No body written
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.HealthCheck(context.Background())
	if err == nil {
		t.Fatal("expected error for 502")
	}
	var apiErr *cortexmem.APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected APIError, got: %T", err)
	}
	if apiErr.StatusCode != http.StatusBadGateway {
		t.Errorf("expected status 502, got %d", apiErr.StatusCode)
	}
	if apiErr.Message == "" {
		t.Error("error message should not be empty for empty response body")
	}
	if !strings.Contains(apiErr.Message, "empty") {
		t.Errorf("expected message to mention empty body, got: %q", apiErr.Message)
	}
}

func TestObservationRequest_V14Fields_WireFormat(t *testing.T) {
	// Verify source, prompt_number, and extractedData are correctly sent
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if body["source"] != "tool_result" {
			t.Errorf("expected source=tool_result, got %v", body["source"])
		}
		if body["prompt_number"] != float64(5) {
			t.Errorf("expected prompt_number=5, got %v", body["prompt_number"])
		}
		ed, ok := body["extractedData"].(map[string]any)
		if !ok || ed["allergies"] == nil {
			t.Error("expected extractedData with allergies")
		}

		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.RecordObservation(context.Background(), dto.ObservationRequest{
		SessionID:     "sess-1",
		ProjectPath:   "/project",
		ToolName:      "Edit",
		Source:        "tool_result",
		PromptNumber:  5,
		ExtractedData: map[string]any{"allergies": []string{"peanuts"}},
	})
	if err != nil {
		t.Fatalf("RecordObservation failed: %v", err)
	}
}

func TestExtractedData_CamelCase(t *testing.T) {
	// Verify extractedData is camelCase in wire format
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if body["extractedData"] == nil {
			t.Error("expected extractedData (camelCase)")
		}
		if body["extracted_data"] != nil {
			t.Error("extracted_data should not exist (should be 'extractedData')")
		}

		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	req := dto.ObservationRequest{
		SessionID:    "sess-1",
		ProjectPath:  "/project",
		ToolName:     "Read",
		ExtractedData: map[string]any{"key": "value"},
	}
	err := client.RecordObservation(context.Background(), req)
	if err != nil {
		t.Fatalf("RecordObservation failed: %v", err)
	}
}

func TestTriggerExtraction_PathAndParams(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/extraction/run" {
			t.Errorf("expected /api/extraction/run, got %s", r.URL.Path)
		}
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		if r.URL.Query().Get("projectPath") != "/my-project" {
			t.Errorf("expected projectPath=/my-project, got %s", r.URL.Query().Get("projectPath"))
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.TriggerExtraction(context.Background(), "/my-project")
	if err != nil {
		t.Fatalf("TriggerExtraction failed: %v", err)
	}
}

func TestTriggerExtraction_PropagatesError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte(`{"error":"extraction failed"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.TriggerExtraction(context.Background(), "/project")
	if err == nil {
		t.Fatal("TriggerExtraction should propagate error")
	}
	if !cortexmem.IsInternal(err) {
		t.Errorf("expected IsInternal, got: %v", err)
	}
}

func TestGetLatestExtraction_PathAndParams(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Template name should be in path
		if !strings.Contains(r.URL.Path, "user-preferences") {
			t.Errorf("expected template in path, got %s", r.URL.Path)
		}
		// projectPath and templateName should also be in query params
		if r.URL.Query().Get("projectPath") != "/project" {
			t.Errorf("expected projectPath query param, got %s", r.URL.Query().Get("projectPath"))
		}
		if r.URL.Query().Get("userId") != "alice" {
			t.Errorf("expected userId query param, got %s", r.URL.Query().Get("userId"))
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]any{
			"status":        "ok",
			"template":      "user-preferences",
			"sessionId":     "sess-1",
			"extractedData": map[string]any{"name": "Alice"},
			"createdAt":     1700000000000,
			"observationId": "obs-1",
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.GetLatestExtraction(context.Background(), "/project", "user-preferences", "alice")
	if err != nil {
		t.Fatalf("GetLatestExtraction failed: %v", err)
	}
	if result.Status != "ok" {
		t.Errorf("expected status=ok, got %v", result.Status)
	}
	if result.ExtractedData["name"] != "Alice" {
		t.Errorf("expected extractedData.name=Alice, got %v", result.ExtractedData["name"])
	}
}

func TestGetObservationsByIds(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/observations/batch" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)
		ids, ok := body["ids"].([]any)
		if !ok || len(ids) != 2 {
			t.Errorf("expected ids array with 2 elements, got %v", body["ids"])
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(dto.BatchObservationsResponse{
			Observations: []dto.Observation{
				{ID: "obs-1", Content: "first"},
				{ID: "obs-2", Content: "second"},
			},
			Count: 2,
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	resp, err := client.GetObservationsByIds(context.Background(), []string{"obs-1", "obs-2"})
	if err != nil {
		t.Fatalf("GetObservationsByIds failed: %v", err)
	}
	if resp.Count != 2 {
		t.Errorf("expected count=2, got %d", resp.Count)
	}
	if len(resp.Observations) != 2 {
		t.Fatalf("expected 2 observations, got %d", len(resp.Observations))
	}
	if resp.Observations[0].ID != "obs-1" {
		t.Errorf("expected obs-1, got %s", resp.Observations[0].ID)
	}
	if resp.Observations[1].Content != "second" {
		t.Errorf("expected content=second, got %s", resp.Observations[1].Content)
	}
}

func TestIsBadRequest(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte(`{"error":"invalid input"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.StartSession(context.Background(), dto.SessionStartRequest{
		SessionID:   "",
		ProjectPath: "",
	})
	if err == nil {
		t.Fatal("expected error for 400")
	}
	if !cortexmem.IsBadRequest(err) {
		t.Errorf("expected IsBadRequest, got: %v", err)
	}
}

func TestUpdateObservation_WireFormat(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPatch {
			t.Errorf("expected PATCH, got %s", r.Method)
		}
		if !strings.HasSuffix(r.URL.Path, "/obs-1") {
			t.Errorf("expected path to end with /obs-1, got %s", r.URL.Path)
		}
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if body["title"] != "Updated Title" {
			t.Errorf("expected title=Updated Title, got %v", body["title"])
		}
		if body["subtitle"] != "A new subtitle" {
			t.Errorf("expected subtitle=A new subtitle, got %v", body["subtitle"])
		}
		if body["extractedData"] == nil {
			t.Error("expected extractedData (camelCase)")
		}
		if body["extracted_data"] != nil {
			t.Error("extracted_data should not exist (should be 'extractedData')")
		}
		// Nil pointer fields should NOT appear
		if body["content"] != nil {
			t.Error("nil content should be omitted")
		}
		if body["source"] != nil {
			t.Error("nil source should be omitted")
		}

		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	title := "Updated Title"
	subtitle := "A new subtitle"
	err := client.UpdateObservation(context.Background(), "obs-1", dto.ObservationUpdate{
		Title:         &title,
		Subtitle:      &subtitle,
		ExtractedData: map[string]any{"key": "value"},
	})
	if err != nil {
		t.Fatalf("UpdateObservation failed: %v", err)
	}
}

func TestObservationUpdate_NarrativeField(t *testing.T) {
	// Verify the Narrative field is sent as "narrative" in wire format
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)
		if body["narrative"] != "updated narrative" {
			t.Errorf("expected narrative='updated narrative', got %v", body["narrative"])
		}
		if _, hasContent := body["content"]; hasContent {
			t.Error("nil content should be omitted when only narrative is set")
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	narrative := "updated narrative"
	err := client.UpdateObservation(context.Background(), "obs-1", dto.ObservationUpdate{
		Narrative: &narrative,
	})
	if err != nil {
		t.Fatalf("UpdateObservation with narrative failed: %v", err)
	}
}

func TestObservationUpdate_PointerFieldsVsSlices(t *testing.T) {
	// Pointer fields (*string) with empty values ARE sent (allows clearing)
	// Slice fields ([]string) with empty values are OMITTED (Go omitempty behavior)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		// Pointer source with empty string should be sent (clearing the field)
		source, hasSource := body["source"]
		if !hasSource {
			t.Error("pointer source should be present even when empty string")
		}
		if source != "" {
			t.Errorf("expected empty source, got %v", source)
		}
		// Slice facts with empty value is omitted by Go's omitempty (expected behavior)
		if _, hasFacts := body["facts"]; hasFacts {
			t.Error("empty facts slice should be omitted by omitempty (Go behavior)")
		}
		// Non-empty concepts should be present
		concepts, hasConcepts := body["concepts"]
		if !hasConcepts {
			t.Error("non-empty concepts should be present")
		}
		if c, ok := concepts.([]any); !ok || len(c) != 1 {
			t.Errorf("expected 1 concept, got %v", concepts)
		}

		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	emptyStr := ""
	err := client.UpdateObservation(context.Background(), "obs-1", dto.ObservationUpdate{
		Source:   &emptyStr,
		Facts:    []string{},  // will be omitted
		Concepts: []string{"test"},
	})
	if err != nil {
		t.Fatalf("UpdateObservation failed: %v", err)
	}
}

func TestDeleteObservation(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodDelete {
			t.Errorf("expected DELETE, got %s", r.Method)
		}
		if !strings.HasSuffix(r.URL.Path, "/obs-1") {
			t.Errorf("expected path to end with /obs-1, got %s", r.URL.Path)
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.DeleteObservation(context.Background(), "obs-1")
	if err != nil {
		t.Fatalf("DeleteObservation failed: %v", err)
	}
}

func TestGetExtractionHistory_PathAndParams(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.Contains(r.URL.Path, "user-preferences/history") {
			t.Errorf("expected template/history in path, got %s", r.URL.Path)
		}
		if r.URL.Query().Get("projectPath") != "/project" {
			t.Errorf("expected projectPath query param, got %s", r.URL.Query().Get("projectPath"))
		}
		if r.URL.Query().Get("limit") != "10" {
			t.Errorf("expected limit=10, got %s", r.URL.Query().Get("limit"))
		}
		if r.URL.Query().Get("userId") != "alice" {
			t.Errorf("expected userId=alice, got %s", r.URL.Query().Get("userId"))
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode([]map[string]any{
			{"sessionId": "sess-1", "extractedData": map[string]any{"key": "v1"}, "createdAt": 1000, "observationId": "obs-1"},
			{"sessionId": "sess-2", "extractedData": map[string]any{"key": "v2"}, "createdAt": 2000, "observationId": "obs-2"},
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	history, err := client.GetExtractionHistory(context.Background(), "/project", "user-preferences", "alice", 10)
	if err != nil {
		t.Fatalf("GetExtractionHistory failed: %v", err)
	}
	if len(history) != 2 {
		t.Fatalf("expected 2 results, got %d", len(history))
	}
	if history[0].ExtractedData["key"] != "v1" {
		t.Errorf("expected first result key=v1, got %v", history[0].ExtractedData["key"])
	}
	if history[1].ObservationID != "obs-2" {
		t.Errorf("expected second result observationId=obs-2, got %v", history[1].ObservationID)
	}
}

// ==================== P1 Management & Additional Tests ====================

func TestUpdateSessionUserId(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPatch {
			t.Errorf("expected PATCH, got %s", r.Method)
		}
		if !strings.HasSuffix(r.URL.Path, "/sess-1/user") {
			t.Errorf("expected path to end with /sess-1/user, got %s", r.URL.Path)
		}
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)
		if body["user_id"] != "user-42" {
			t.Errorf("expected user_id=user-42, got %v", body["user_id"])
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]any{"status": "ok", "sessionId": "sess-1", "userId": "user-42"})
	}))
	defer server.Close()

	client := newTestClient(server)
	resp, err := client.UpdateSessionUserId(context.Background(), "sess-1", "user-42")
	if err != nil {
		t.Fatalf("UpdateSessionUserId failed: %v", err)
	}
	if resp.Status != "ok" {
		t.Errorf("expected status=ok, got %v", resp.Status)
	}
	if resp.UserID != "user-42" {
		t.Errorf("expected userId=user-42, got %v", resp.UserID)
	}
}

func TestGetProjects(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/projects" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		if r.Method != http.MethodGet {
			t.Errorf("expected GET, got %s", r.Method)
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]any{
			"projects": []string{"/proj-a", "/proj-b"},
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.GetProjects(context.Background())
	if err != nil {
		t.Fatalf("GetProjects failed: %v", err)
	}
	if len(result.Projects) != 2 {
		t.Errorf("expected 2 projects, got %d", len(result.Projects))
	}
}

func TestGetStats(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/stats" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		project := r.URL.Query().Get("project")
		if project != "/my-project" {
			t.Errorf("expected project=/my-project, got %s", project)
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]any{
			"worker":   map[string]any{"isProcessing": false, "queueDepth": 0},
			"database": map[string]any{"totalObservations": float64(100), "totalSummaries": float64(5), "totalSessions": float64(10), "totalProjects": float64(2)},
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.GetStats(context.Background(), "/my-project")
	if err != nil {
		t.Fatalf("GetStats failed: %v", err)
	}
	if result.Database.TotalObservations != 100 {
		t.Errorf("expected totalObservations=100, got %v", result.Database.TotalObservations)
	}
}

func TestGetStats_EmptyProject(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("project") != "" {
			t.Errorf("expected no project query param for empty projectPath")
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]any{
			"worker":   map[string]any{"isProcessing": false, "queueDepth": 0},
			"database": map[string]any{"totalObservations": float64(0), "totalSummaries": float64(0), "totalSessions": float64(0), "totalProjects": float64(0)},
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.GetStats(context.Background(), "")
	if err != nil {
		t.Fatalf("GetStats failed: %v", err)
	}
	if result.Database.TotalObservations != 0 {
		t.Errorf("expected totalObservations=0, got %v", result.Database.TotalObservations)
	}
}

func TestGetModes(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/modes" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]any{
			"id":                    "default",
			"name":                  "Default Mode",
			"description":           "Standard mode",
			"version":               "1.0",
			"observation_types":     []string{"tool-use", "user-prompt"},
			"observation_concepts":  []string{"code", "test"},
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.GetModes(context.Background())
	if err != nil {
		t.Fatalf("GetModes failed: %v", err)
	}
	if result.ID != "default" {
		t.Errorf("expected id=default, got %v", result.ID)
	}
	if len(result.ObservationTypes) != 2 {
		t.Errorf("expected 2 observation types, got %d", len(result.ObservationTypes))
	}
}

func TestGetSettings(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/settings" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]any{
			"embeddingModel": "text-embedding-3-small",
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.GetSettings(context.Background())
	if err != nil {
		t.Fatalf("GetSettings failed: %v", err)
	}
	if result["embeddingModel"] != "text-embedding-3-small" {
		t.Errorf("expected embeddingModel=text-embedding-3-small, got %v", result["embeddingModel"])
	}
}

func TestIsRateLimited(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusTooManyRequests)
		w.Write([]byte(`{"error":"rate limited"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 429")
	}
	if !cortexmem.IsRateLimited(err) {
		t.Errorf("expected IsRateLimited, got: %v", err)
	}
}

func TestIsInternal(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte(`{"error":"internal"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 500")
	}
	if !cortexmem.IsInternal(err) {
		t.Errorf("expected IsInternal, got: %v", err)
	}
}

func TestFireAndForget_RetryOnFailure(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		if attempts < 3 {
			w.WriteHeader(http.StatusServiceUnavailable) // 503 — transient, retryable
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithMaxRetries(3),
	)

	err := client.RecordObservation(context.Background(), dto.ObservationRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
		ToolName:    "Read",
	})
	if err != nil {
		t.Fatalf("RecordObservation should not return error (fire-and-forget): %v", err)
	}
	if attempts != 3 {
		t.Errorf("expected 3 attempts, got %d", attempts)
	}
}

func TestFireAndForget_ExhaustsRetries(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.WriteHeader(http.StatusServiceUnavailable) // 503 — transient, retryable
	}))
	defer server.Close()

	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithMaxRetries(3),
	)

	// Fire-and-forget should NOT return error even after exhausting retries
	err := client.RecordObservation(context.Background(), dto.ObservationRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
		ToolName:    "Read",
	})
	if err != nil {
		t.Fatalf("fire-and-forget should swallow error: %v", err)
	}
	if attempts != 3 {
		t.Errorf("expected 3 attempts, got %d", attempts)
	}
}

func TestFireAndForget_NoRetryOn4xx(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.WriteHeader(http.StatusBadRequest) // 400 — non-retryable client error
		w.Write([]byte("bad request"))
	}))
	defer server.Close()

	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithMaxRetries(3),
	)

	// Fire-and-forget should NOT retry on 4xx client errors
	err := client.RecordObservation(context.Background(), dto.ObservationRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
		ToolName:    "Read",
	})
	if err != nil {
		t.Fatalf("fire-and-forget should swallow error: %v", err)
	}
	if attempts != 1 {
		t.Errorf("expected 1 attempt (no retry on 4xx), got %d", attempts)
	}
}

func TestFireAndForget_RetryOn429(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		if attempts < 3 {
			w.WriteHeader(http.StatusTooManyRequests) // 429 — retryable
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithMaxRetries(3),
		cortexmem.WithRetryBackoff(1*time.Millisecond), // Fast backoff for test
	)

	err := client.RecordObservation(context.Background(), dto.ObservationRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
		ToolName:    "Read",
	})
	if err != nil {
		t.Fatalf("fire-and-forget should succeed: %v", err)
	}
	if attempts != 3 {
		t.Errorf("expected 3 attempts (retry on 429), got %d", attempts)
	}
}

func TestFireAndForget_NoRetryOn500(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.WriteHeader(http.StatusInternalServerError) // 500 — code bug, NOT retryable
		w.Write([]byte("internal error"))
	}))
	defer server.Close()

	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithMaxRetries(3),
	)

	err := client.RecordObservation(context.Background(), dto.ObservationRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
		ToolName:    "Read",
	})
	if err != nil {
		t.Fatalf("fire-and-forget should swallow error: %v", err)
	}
	if attempts != 1 {
		t.Errorf("expected 1 attempt (no retry on 500), got %d", attempts)
	}
}

func TestHealthCheck_Success(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/health" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.HealthCheck(context.Background())
	if err != nil {
		t.Fatalf("HealthCheck should succeed: %v", err)
	}
}

func TestFireAndForget_ContextCancellation(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		// Always fail to force retries
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithMaxRetries(5),
	)

	// Create a context that gets cancelled immediately
	ctx, cancel := context.WithCancel(context.Background())
	cancel() // Cancel immediately

	err := client.RecordObservation(ctx, dto.ObservationRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
		ToolName:    "Read",
	})
	// Fire-and-forget should NEVER return an error, even on context cancellation
	if err != nil {
		t.Fatalf("fire-and-forget should swallow context cancellation: %v", err)
	}
	// With the pre-cancelled context optimization, no HTTP request should be made
	if attempts != 0 {
		t.Errorf("expected 0 attempts for pre-cancelled context, got %d", attempts)
	}
}

func TestDoRequest_ContextCancelledBeforeMarshal(t *testing.T) {
	// Verify doRequest fast-fails on cancelled context without making HTTP requests
	requestsReceived := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requestsReceived++
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
	}))
	defer server.Close()

	client := newTestClient(server)

	// Pre-cancelled context should fail immediately without hitting the server
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := client.StartSession(ctx, dto.SessionStartRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
	})
	if err == nil {
		t.Fatal("expected error for cancelled context")
	}
	if !errors.Is(err, context.Canceled) {
		t.Errorf("expected context.Canceled, got: %v", err)
	}
	if requestsReceived != 0 {
		t.Errorf("expected 0 HTTP requests for pre-cancelled context, got %d", requestsReceived)
	}
}

func TestFireAndForget_CustomBackoff(t *testing.T) {
	attempts := 0
	var timestamps []time.Time
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		timestamps = append(timestamps, time.Now())
		if attempts < 3 {
			w.WriteHeader(http.StatusServiceUnavailable) // 503 — transient, retryable
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	// Use 100ms base backoff (instead of default 500ms)
	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithMaxRetries(3),
		cortexmem.WithRetryBackoff(100*time.Millisecond),
	)

	err := client.RecordObservation(context.Background(), dto.ObservationRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
		ToolName:    "Read",
	})
	if err != nil {
		t.Fatalf("RecordObservation should not return error: %v", err)
	}
	if attempts != 3 {
		t.Errorf("expected 3 attempts, got %d", attempts)
	}
	// Verify backoff timing with jitter: base delays are 100ms and 200ms,
	// jittered to [75ms, 125ms] and [150ms, 250ms] respectively.
	if len(timestamps) >= 3 {
		delay1 := timestamps[1].Sub(timestamps[0])
		delay2 := timestamps[2].Sub(timestamps[1])
		// First backoff: 100ms * 1 = 100ms, jittered to [75ms, 125ms]
		if delay1 < 60*time.Millisecond || delay1 > 160*time.Millisecond {
			t.Errorf("expected first delay ~100ms (±jitter), got %v", delay1)
		}
		// Second backoff: 100ms * 2 = 200ms, jittered to [150ms, 250ms]
		if delay2 < 130*time.Millisecond || delay2 > 300*time.Millisecond {
			t.Errorf("expected second delay ~200ms (±jitter), got %v", delay2)
		}
	}
}

func TestHealthCheck_Unhealthy(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"status": "degraded"})
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.HealthCheck(context.Background())
	if err == nil {
		t.Fatal("HealthCheck should fail for degraded status")
	}
	if !strings.Contains(err.Error(), "unhealthy") {
		t.Errorf("expected unhealthy error, got: %v", err)
	}
}

// ==================== Retrieval Method Tests ====================

func TestRetrieveExperiences_PathAndBody(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/memory/experiences" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if body["task"] != "How to fix null pointer?" {
			t.Errorf("expected task, got %v", body["task"])
		}
		if body["project"] != "/proj" {
			t.Errorf("expected project=/proj, got %v", body["project"])
		}
		if body["count"] != float64(5) {
			t.Errorf("expected count=5, got %v", body["count"])
		}
		if body["source"] != "tool_result" {
			t.Errorf("expected source=tool_result, got %v", body["source"])
		}
		// requiredConcepts should be camelCase
		if body["required_concepts"] != nil {
			t.Error("required_concepts should not be in wire format (should be 'requiredConcepts')")
		}
		rc, ok := body["requiredConcepts"].([]any)
		if !ok || len(rc) != 2 {
			t.Errorf("expected requiredConcepts array with 2 elements, got %v", body["requiredConcepts"])
		}
		// userId should be camelCase (not user_id)
		if body["user_id"] != nil {
			t.Error("user_id should not be in wire format (should be 'userId')")
		}
		if body["userId"] != "alice" {
			t.Errorf("expected userId=alice, got %v", body["userId"])
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode([]dto.Experience{
			{ID: "exp-1", Task: "How to fix null pointer?", Strategy: "check-for-null", Outcome: "Added null check", QualityScore: 0.95},
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	exps, err := client.RetrieveExperiences(context.Background(), dto.ExperienceRequest{
		Task:             "How to fix null pointer?",
		Project:          "/proj",
		Count:            5,
		Source:           "tool_result",
		RequiredConcepts: []string{"null-safety", "error-handling"},
		UserID:           "alice",
	})
	if err != nil {
		t.Fatalf("RetrieveExperiences failed: %v", err)
	}
	if len(exps) != 1 {
		t.Fatalf("expected 1 experience, got %d", len(exps))
	}
	if exps[0].ID != "exp-1" {
		t.Errorf("expected ID=exp-1, got %s", exps[0].ID)
	}
	if exps[0].QualityScore != 0.95 {
		t.Errorf("expected qualityScore=0.95, got %f", exps[0].QualityScore)
	}
}

func TestBuildICLPrompt_PathAndBody(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/memory/icl-prompt" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if body["task"] != "Write a parser" {
			t.Errorf("expected task=Write a parser, got %v", body["task"])
		}
		if body["project"] != "/proj" {
			t.Errorf("expected project=/proj, got %v", body["project"])
		}
		// maxChars should be camelCase (not max_chars)
		if body["max_chars"] != nil {
			t.Error("max_chars should not be in wire format (should be 'maxChars')")
		}
		// userId should be camelCase (not user_id)
		if body["user_id"] != nil {
			t.Error("user_id should not be in wire format (should be 'userId')")
		}
		if body["userId"] != "alice" {
			t.Errorf("expected userId=alice, got %v", body["userId"])
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(dto.ICLPromptResult{
			Prompt:           "Here are relevant experiences...",
			ExperienceCount:  3,
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.BuildICLPrompt(context.Background(), dto.ICLPromptRequest{
		Task:     "Write a parser",
		Project:  "/proj",
		MaxChars: 4000,
		UserID:   "alice",
	})
	if err != nil {
		t.Fatalf("BuildICLPrompt failed: %v", err)
	}
	if result.Prompt != "Here are relevant experiences..." {
		t.Errorf("unexpected prompt: %s", result.Prompt)
	}
	if result.ExperienceCount != 3 {
		t.Errorf("expected experienceCount=3, got %d", result.ExperienceCount)
	}
}

func TestGetQualityDistribution_Path(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/memory/quality-distribution" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		if r.Method != http.MethodGet {
			t.Errorf("expected GET, got %s", r.Method)
		}
		if r.URL.Query().Get("project") != "/my/proj" {
			t.Errorf("expected project=/my/proj, got %s", r.URL.Query().Get("project"))
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(dto.QualityDistribution{
			Project: "/my/proj",
			High:    20,
			Medium:  10,
			Low:     5,
			Unknown: 2,
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	dist, err := client.GetQualityDistribution(context.Background(), "/my/proj")
	if err != nil {
		t.Fatalf("GetQualityDistribution failed: %v", err)
	}
	if dist.Total() != 37 {
		t.Errorf("expected total=37, got %d", dist.Total())
	}
	if dist.Project != "/my/proj" {
		t.Errorf("expected project=/my/proj, got %s", dist.Project)
	}
}

func TestSearch_WithOffset(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		if q.Get("project") != "/proj" {
			t.Errorf("expected project=/proj, got %s", q.Get("project"))
		}
		if q.Get("limit") != "10" {
			t.Errorf("expected limit=10, got %s", q.Get("limit"))
		}
		if q.Get("offset") != "20" {
			t.Errorf("expected offset=20, got %s", q.Get("offset"))
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(dto.SearchResult{Count: 0})
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.Search(context.Background(), dto.SearchRequest{
		Project: "/proj",
		Limit:   10,
		Offset:  20,
	})
	if err != nil {
		t.Fatalf("Search failed: %v", err)
	}
}

func TestSearch_OmitsEmptyParams(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		// Only "project" should be present (other fields are empty and should be omitted)
		if q.Get("project") != "/proj" {
			t.Errorf("expected project=/proj, got %s", q.Get("project"))
		}
		if q.Get("query") != "" {
			t.Errorf("expected no query param, got %s", q.Get("query"))
		}
		if q.Get("type") != "" {
			t.Errorf("expected no type param, got %s", q.Get("type"))
		}
		if q.Get("concept") != "" {
			t.Errorf("expected no concept param, got %s", q.Get("concept"))
		}
		if q.Get("source") != "" {
			t.Errorf("expected no source param, got %s", q.Get("source"))
		}
		if q.Get("limit") != "" {
			t.Errorf("expected no limit param (zero value), got %s", q.Get("limit"))
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(dto.SearchResult{Count: 0})
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.Search(context.Background(), dto.SearchRequest{
		Project: "/proj",
		// All other fields left as zero values
	})
	if err != nil {
		t.Fatalf("Search failed: %v", err)
	}
}

func TestStartSession_ErrorHandling(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte(`{"error":"service temporarily unavailable"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.StartSession(context.Background(), dto.SessionStartRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
	})
	if err == nil {
		t.Fatal("expected error for 503")
	}
	if !cortexmem.IsServiceUnavailable(err) {
		t.Errorf("expected IsServiceUnavailable, got: %v", err)
	}
	if !cortexmem.IsInternal(err) {
		t.Errorf("503 should also match IsInternal")
	}
}

// ==================== Error Helper Tests ====================

func TestIsForbidden(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusForbidden)
		w.Write([]byte(`{"error":"forbidden"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 403")
	}
	if !cortexmem.IsForbidden(err) {
		t.Errorf("expected IsForbidden, got: %v", err)
	}
	if cortexmem.IsNotFound(err) {
		t.Error("403 should not match IsNotFound")
	}
}

func TestIsUnprocessable(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(422)
		w.Write([]byte(`{"error":"unprocessable"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 422")
	}
	if !cortexmem.IsUnprocessable(err) {
		t.Errorf("expected IsUnprocessable, got: %v", err)
	}
}

func TestIsBadGateway(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadGateway)
		w.Write([]byte(`{"error":"bad gateway"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 502")
	}
	if !cortexmem.IsBadGateway(err) {
		t.Errorf("expected IsBadGateway, got: %v", err)
	}
	if !cortexmem.IsInternal(err) {
		t.Errorf("502 should also match IsInternal")
	}
}

func TestIsServiceUnavailable(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte(`{"error":"unavailable"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 503")
	}
	if !cortexmem.IsServiceUnavailable(err) {
		t.Errorf("expected IsServiceUnavailable, got: %v", err)
	}
	if !cortexmem.IsInternal(err) {
		t.Errorf("503 should also match IsInternal")
	}
}

func TestIsGatewayTimeout(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusGatewayTimeout)
		w.Write([]byte(`{"error":"timeout"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 504")
	}
	if !cortexmem.IsGatewayTimeout(err) {
		t.Errorf("expected IsGatewayTimeout, got: %v", err)
	}
	if !cortexmem.IsInternal(err) {
		t.Errorf("504 should also match IsInternal")
	}
}

func TestIsClientError(t *testing.T) {
	// 400 should match IsClientError
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte(`{"error":"bad"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 400")
	}
	if !cortexmem.IsClientError(err) {
		t.Error("400 should match IsClientError")
	}
	if cortexmem.IsServerError(err) {
		t.Error("400 should NOT match IsServerError")
	}
}

func TestIsServerError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte(`{"error":"internal"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 500")
	}
	if !cortexmem.IsServerError(err) {
		t.Error("500 should match IsServerError")
	}
	if cortexmem.IsClientError(err) {
		t.Error("500 should NOT match IsClientError")
	}
}

// ==================== IsRetryable Tests ====================

func TestIsRetryable_RateLimited(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusTooManyRequests)
		w.Write([]byte(`{"error":"rate limited"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 429")
	}
	if !cortexmem.IsRetryable(err) {
		t.Error("429 should be retryable")
	}
}

func TestIsRetryable_BadGateway(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadGateway)
		w.Write([]byte(`{"error":"bad gateway"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 502")
	}
	if !cortexmem.IsRetryable(err) {
		t.Error("502 should be retryable")
	}
}

func TestIsRetryable_ServiceUnavailable(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte(`{"error":"unavailable"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 503")
	}
	if !cortexmem.IsRetryable(err) {
		t.Error("503 should be retryable")
	}
}

func TestIsRetryable_GatewayTimeout(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusGatewayTimeout)
		w.Write([]byte(`{"error":"timeout"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 504")
	}
	if !cortexmem.IsRetryable(err) {
		t.Error("504 should be retryable")
	}
}

func TestIsRetryable_NotRetryable_ClientErrors(t *testing.T) {
	testCases := []struct {
		status int
		name   string
	}{
		{400, "BadRequest"},
		{401, "Unauthorized"},
		{403, "Forbidden"},
		{404, "NotFound"},
		{409, "Conflict"},
		{422, "Unprocessable"},
	}
	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.WriteHeader(tc.status)
				w.Write([]byte(`{"error":"test"}`))
			}))
			defer server.Close()

			client := newTestClient(server)
			_, err := client.GetVersion(context.Background())
			if cortexmem.IsRetryable(err) {
				t.Errorf("status %d should NOT be retryable", tc.status)
			}
		})
	}
}

func TestIsRetryable_NotRetryable_Internal500(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte(`{"error":"internal"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 500")
	}
	// 500 is NOT retryable (it's a code bug, not a transient failure)
	if cortexmem.IsRetryable(err) {
		t.Error("500 should NOT be retryable (code bug, not transient)")
	}
}

func TestIsRetryable_SentinelFallback(t *testing.T) {
	// Direct sentinel errors should also match
	if !cortexmem.IsRetryable(cortexmem.ErrRateLimited) {
		t.Error("ErrRateLimited should be retryable")
	}
	if !cortexmem.IsRetryable(cortexmem.ErrBadGateway) {
		t.Error("ErrBadGateway should be retryable")
	}
	if !cortexmem.IsRetryable(cortexmem.ErrServiceUnavailable) {
		t.Error("ErrServiceUnavailable should be retryable")
	}
	if !cortexmem.IsRetryable(cortexmem.ErrGatewayTimeout) {
		t.Error("ErrGatewayTimeout should be retryable")
	}
	// Non-retryable sentinels
	if cortexmem.IsRetryable(cortexmem.ErrInternal) {
		t.Error("ErrInternal should NOT be retryable")
	}
	if cortexmem.IsRetryable(cortexmem.ErrNotFound) {
		t.Error("ErrNotFound should NOT be retryable")
	}
	if cortexmem.IsRetryable(cortexmem.ErrBadRequest) {
		t.Error("ErrBadRequest should NOT be retryable")
	}
}

func TestIsRetryable_NilError(t *testing.T) {
	if cortexmem.IsRetryable(nil) {
		t.Error("IsRetryable(nil) should be false")
	}
}

func TestIsRetryable_GenericError(t *testing.T) {
	// Generic (non-API) errors are network/transport errors — always retryable
	if !cortexmem.IsRetryable(errors.New("connection refused")) {
		t.Error("IsRetryable should return true for generic network error")
	}
}

func TestIsRetryable_NetworkErrors(t *testing.T) {
	// Network errors like timeouts, DNS failures, connection refused are all retryable
	testCases := []string{
		"connection refused",
		"no such host",
		"i/o timeout",
		"dial tcp: lookup failed",
	}
	for _, msg := range testCases {
		if !cortexmem.IsRetryable(errors.New(msg)) {
			t.Errorf("IsRetryable should return true for network error: %q", msg)
		}
	}
}

// ==================== APIError.Unwrap() Tests ====================
// These tests verify that errors.Is() and errors.As() work correctly
// through the APIError → sentinel error chain.

func TestAPIError_Unwrap_IsNotFound(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		w.Write([]byte(`{"error":"not found"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 404")
	}
	// errors.Is should match through Unwrap()
	if !errors.Is(err, cortexmem.ErrNotFound) {
		t.Error("errors.Is(err, ErrNotFound) should be true for 404")
	}
	// Should NOT match other sentinel errors
	if errors.Is(err, cortexmem.ErrBadRequest) {
		t.Error("404 should not match ErrBadRequest")
	}
	if errors.Is(err, cortexmem.ErrInternal) {
		t.Error("404 should not match ErrInternal")
	}
}

func TestAPIError_Unwrap_IsRateLimited(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusTooManyRequests)
		w.Write([]byte(`{"error":"rate limited"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 429")
	}
	if !errors.Is(err, cortexmem.ErrRateLimited) {
		t.Error("errors.Is(err, ErrRateLimited) should be true for 429")
	}
}

func TestAPIError_Unwrap_IsServiceUnavailable(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte(`{"error":"unavailable"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 503")
	}
	if !errors.Is(err, cortexmem.ErrServiceUnavailable) {
		t.Error("errors.Is(err, ErrServiceUnavailable) should be true for 503")
	}
	// errors.Is through Unwrap should NOT match ErrInternal (that's mapped from 500 only)
	// But IsInternal() helper should match (it checks >= 500)
	if errors.Is(err, cortexmem.ErrInternal) {
		t.Error("503 should not match ErrInternal through Unwrap (ErrInternal maps to 500 only)")
	}
	if !cortexmem.IsInternal(err) {
		t.Error("503 should match IsInternal() helper (checks >= 500)")
	}
}

func TestAPIError_AsExtractsStatusCode(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusForbidden)
		w.Write([]byte(`{"error":"forbidden"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 403")
	}
	// errors.As should extract the APIError
	var apiErr *cortexmem.APIError
	if !errors.As(err, &apiErr) {
		t.Fatal("errors.As should extract APIError")
	}
	if apiErr.StatusCode != http.StatusForbidden {
		t.Errorf("expected status code 403, got %d", apiErr.StatusCode)
	}
	if !strings.Contains(apiErr.Message, "forbidden") {
		t.Errorf("expected message to contain 'forbidden', got %s", apiErr.Message)
	}
}

func TestAPIError_Unwrap_UnknownStatusCode(t *testing.T) {
	// 418 I'm a teapot — not a mapped sentinel error
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(418)
		w.Write([]byte(`{"error":"teapot"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 418")
	}
	// Should NOT match any sentinel error
	if errors.Is(err, cortexmem.ErrNotFound) {
		t.Error("418 should not match ErrNotFound")
	}
	if errors.Is(err, cortexmem.ErrInternal) {
		t.Error("418 should not match ErrInternal (418 is 4xx)")
	}
	// But errors.As should still work
	var apiErr *cortexmem.APIError
	if !errors.As(err, &apiErr) {
		t.Fatal("errors.As should still extract APIError for unmapped status codes")
	}
	if apiErr.StatusCode != 418 {
		t.Errorf("expected status code 418, got %d", apiErr.StatusCode)
	}
}

func TestAPIError_ErrorString(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		w.Write([]byte(`resource not found`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 404")
	}

	// APIError.Error() should include status code and message
	errStr := err.Error()
	if !strings.Contains(errStr, "404") {
		t.Errorf("Error() should contain status code 404, got: %s", errStr)
	}
	if !strings.Contains(errStr, "resource not found") {
		t.Errorf("Error() should contain the response body, got: %s", errStr)
	}
}

func TestIsUnauthorized(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Authorization", r.Header.Get("Authorization"))
		w.WriteHeader(http.StatusUnauthorized)
		w.Write([]byte(`{"error":"unauthorized"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 401")
	}
	if !cortexmem.IsUnauthorized(err) {
		t.Errorf("expected IsUnauthorized, got: %v", err)
	}
	if cortexmem.IsNotFound(err) {
		t.Error("401 should not match IsNotFound")
	}
	// Verify errors.Is also works through Unwrap
	if !errors.Is(err, cortexmem.ErrUnauthorized) {
		t.Error("errors.Is(err, ErrUnauthorized) should be true for 401")
	}
}

func TestIsConflict(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusConflict)
		w.Write([]byte(`{"error":"conflict"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for 409")
	}
	if !cortexmem.IsConflict(err) {
		t.Errorf("expected IsConflict, got: %v", err)
	}
	if cortexmem.IsNotFound(err) {
		t.Error("409 should not match IsNotFound")
	}
	// Verify errors.Is also works through Unwrap
	if !errors.Is(err, cortexmem.ErrConflict) {
		t.Error("errors.Is(err, ErrConflict) should be true for 409")
	}
}

func TestAPIError_Unwrap_AllSentinelErrors(t *testing.T) {
	// Verify all sentinel errors are reachable through Unwrap()
	testCases := []struct {
		status   int
		expected error
	}{
		{400, cortexmem.ErrBadRequest},
		{401, cortexmem.ErrUnauthorized},
		{403, cortexmem.ErrForbidden},
		{404, cortexmem.ErrNotFound},
		{409, cortexmem.ErrConflict},
		{422, cortexmem.ErrUnprocessable},
		{429, cortexmem.ErrRateLimited},
		{500, cortexmem.ErrInternal},
		{502, cortexmem.ErrBadGateway},
		{503, cortexmem.ErrServiceUnavailable},
		{504, cortexmem.ErrGatewayTimeout},
	}

	for _, tc := range testCases {
		t.Run(fmt.Sprintf("status_%d", tc.status), func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.WriteHeader(tc.status)
				w.Write([]byte(`{"error":"test"}`))
			}))
			defer server.Close()

			client := newTestClient(server)
			_, err := client.GetVersion(context.Background())
			if err == nil {
				t.Fatalf("expected error for %d", tc.status)
			}
			if !errors.Is(err, tc.expected) {
				t.Errorf("errors.Is(err, %v) should be true for status %d", tc.expected, tc.status)
			}
		})
	}
}

// ==================== Edge Case Tests ====================

func TestRetrieveExperiences_PropagatesError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte(`{"error":"internal"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.RetrieveExperiences(context.Background(), dto.ExperienceRequest{
		Task:    "test",
		Project: "/proj",
	})
	if err == nil {
		t.Fatal("RetrieveExperiences should propagate error")
	}
	if !cortexmem.IsInternal(err) {
		t.Errorf("expected IsInternal, got: %v", err)
	}
}

func TestBuildICLPrompt_PropagatesError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte(`{"error":"unavailable"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.BuildICLPrompt(context.Background(), dto.ICLPromptRequest{
		Task:    "test",
		Project: "/proj",
	})
	if err == nil {
		t.Fatal("BuildICLPrompt should propagate error")
	}
	if !cortexmem.IsServiceUnavailable(err) {
		t.Errorf("expected IsServiceUnavailable, got: %v", err)
	}
}

func TestSearch_PropagatesError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadGateway)
		w.Write([]byte(`{"error":"bad gateway"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.Search(context.Background(), dto.SearchRequest{
		Project: "/proj",
		Query:   "test",
	})
	if err == nil {
		t.Fatal("Search should propagate error")
	}
	if !cortexmem.IsBadGateway(err) {
		t.Errorf("expected IsBadGateway, got: %v", err)
	}
}

func TestDoRequest_MalformedJSONResponse(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`not valid json`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected error for malformed JSON")
	}
	if !strings.Contains(err.Error(), "failed to parse") {
		t.Errorf("expected parse error, got: %v", err)
	}
}

func TestDoRequest_EmptyResponse(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		// Empty body
	}))
	defer server.Close()

	client := newTestClient(server)
	// Empty body with a method that expects JSON should return parse error
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("expected parse error for empty JSON body")
	}
	if !strings.Contains(err.Error(), "failed to parse") {
		t.Errorf("expected parse error, got: %v", err)
	}
}

// ==================== Lifecycle & Header Tests ====================

func TestClose_CleansUpIdleConnections(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
	}))
	defer server.Close()

	// Create client with custom transport to verify Close works
	transport := &http.Transport{}
	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithHTTPClient(&http.Client{Transport: transport}),
	)

	// Make a request first to establish idle connections
	_ = client.HealthCheck(context.Background())

	// Close should not panic and should clean up idle connections
	err := client.Close()
	if err != nil {
		t.Errorf("Close should not return error: %v", err)
	}

	// Calling Close again should not panic (idle connections already closed)
	err = client.Close()
	if err != nil {
		t.Errorf("second Close should not return error: %v", err)
	}
}

func TestString_ReturnsDebugRepresentation(t *testing.T) {
	client := cortexmem.NewClient(cortexmem.WithBaseURL("http://localhost:37777"))
	s := client.String()
	if !strings.Contains(s, "CortexCEClient") {
		t.Errorf("String() should contain 'CortexCEClient', got %q", s)
	}
	if !strings.Contains(s, "http://localhost:37777") {
		t.Errorf("String() should contain base URL, got %q", s)
	}
}

func TestDoRequest_SetsAcceptHeader(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		accept := r.Header.Get("Accept")
		if accept != "application/json" {
			t.Errorf("expected Accept: application/json, got %q", accept)
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
	}))
	defer server.Close()

	client := newTestClient(server)
	_ = client.HealthCheck(context.Background())
}

func TestDoRequest_NoContentTypeForGet(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// GET requests should NOT have Content-Type header
		if r.Method == http.MethodGet && r.Header.Get("Content-Type") != "" {
			t.Errorf("GET request should not have Content-Type, got %q", r.Header.Get("Content-Type"))
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
	}))
	defer server.Close()

	client := newTestClient(server)
	_ = client.HealthCheck(context.Background())
}

func TestDoRequest_SetsContentTypeForPost(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			ct := r.Header.Get("Content-Type")
			if ct != "application/json" {
				t.Errorf("POST request should have Content-Type: application/json, got %q", ct)
			}
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	_ = client.RecordObservation(context.Background(), dto.ObservationRequest{
		SessionID:   "sess-1",
		ProjectPath: "/proj",
		ToolName:    "Read",
	})
}

func TestWithTimeout_AppliedToClient(t *testing.T) {
	// Verify that WithTimeout is applied as http.Client.Timeout
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
	}))
	defer server.Close()

	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithTimeout(5*time.Second),
	)

	err := client.HealthCheck(context.Background())
	if err != nil {
		t.Errorf("HealthCheck should succeed with custom timeout: %v", err)
	}
}

func TestWithConnectTimeout_AppliedToClient(t *testing.T) {
	// Verify that WithConnectTimeout creates a working client
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
	}))
	defer server.Close()

	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithConnectTimeout(5*time.Second),
	)

	err := client.HealthCheck(context.Background())
	if err != nil {
		t.Errorf("HealthCheck should succeed with custom connect timeout: %v", err)
	}
}

func TestWithTimeout_ExpiresOnSlowServer(t *testing.T) {
	// Server that responds slower than the client timeout
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(2 * time.Second)
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
	}))
	defer server.Close()

	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithTimeout(100*time.Millisecond),
	)

	err := client.HealthCheck(context.Background())
	if err == nil {
		t.Error("HealthCheck should fail when server is slower than timeout")
	}
}

func TestVersion_Exported(t *testing.T) {
	if cortexmem.Version == "" {
		t.Error("Version constant should not be empty")
	}
	if cortexmem.Version != "1.0.0" {
		t.Errorf("expected Version=1.0.0, got %s", cortexmem.Version)
	}
}

func TestDefaultTimeouts_MatchJavaSDK(t *testing.T) {
	cfg := cortexmem.DefaultClientConfig()
	if cfg.Timeout != 30*time.Second {
		t.Errorf("Default Timeout should be 30s (matching Java SDK readTimeout), got %v", cfg.Timeout)
	}
	if cfg.ConnectTimeout != 10*time.Second {
		t.Errorf("Default ConnectTimeout should be 10s (matching Java SDK connectTimeout), got %v", cfg.ConnectTimeout)
	}
}

// ==================== User-Agent & Configuration Tests ====================

func TestDoRequest_SetsUserAgent(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ua := r.Header.Get("User-Agent")
		if !strings.HasPrefix(ua, "cortex-mem-go/") {
			t.Errorf("expected User-Agent starting with cortex-mem-go/, got %q", ua)
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
	}))
	defer server.Close()

	client := newTestClient(server)
	_ = client.HealthCheck(context.Background())
}

func TestDoRequest_SetsUserAgentOnPost(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ua := r.Header.Get("User-Agent")
		if ua != "cortex-mem-go/1.0.0" {
			t.Errorf("expected User-Agent=cortex-mem-go/1.0.0, got %q", ua)
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	_ = client.RecordObservation(context.Background(), dto.ObservationRequest{
		SessionID:   "sess-1",
		ProjectPath: "/proj",
		ToolName:    "Read",
	})
}

func TestMaxRetries_ZeroClampsToOne(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	// MaxRetries=0 should be clamped to 1 (single attempt, no retries)
	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithMaxRetries(0),
	)

	_ = client.RecordObservation(context.Background(), dto.ObservationRequest{
		SessionID:   "sess-1",
		ProjectPath: "/proj",
		ToolName:    "Read",
	})
	if attempts != 1 {
		t.Errorf("MaxRetries=0 should clamp to 1 attempt, got %d", attempts)
	}
}

func TestMaxRetries_NegativeClampsToOne(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	// MaxRetries=-5 should be clamped to 1
	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithMaxRetries(-5),
	)

	_ = client.RecordObservation(context.Background(), dto.ObservationRequest{
		SessionID:   "sess-1",
		ProjectPath: "/proj",
		ToolName:    "Read",
	})
	if attempts != 1 {
		t.Errorf("MaxRetries=-5 should clamp to 1 attempt, got %d", attempts)
	}
}

// ==================== Management Methods: Error Propagation ====================
// Management methods (TriggerRefinement, SubmitFeedback, UpdateObservation,
// DeleteObservation) are NOT fire-and-forget — they must propagate errors.

func TestTriggerRefinement_PropagatesError(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte(`{"error":"refinement failed"}`))
	}))
	defer server.Close()

	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithMaxRetries(3),
	)

	err := client.TriggerRefinement(context.Background(), "/project")
	if err == nil {
		t.Fatal("TriggerRefinement should return error on failure")
	}
	if !cortexmem.IsInternal(err) {
		t.Errorf("expected IsInternal, got: %v", err)
	}
	// Should NOT retry — management methods don't use doFireAndForget
	if attempts != 1 {
		t.Errorf("expected 1 attempt (no retry), got %d", attempts)
	}
}

func TestSubmitFeedback_PropagatesError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		w.Write([]byte(`{"error":"observation not found"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.SubmitFeedback(context.Background(), "nonexistent", "SUCCESS", "comment")
	if err == nil {
		t.Fatal("SubmitFeedback should return error on 404")
	}
	if !cortexmem.IsNotFound(err) {
		t.Errorf("expected IsNotFound, got: %v", err)
	}
}

func TestUpdateObservation_PropagatesError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusForbidden)
		w.Write([]byte(`{"error":"insufficient permissions"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	title := "Updated"
	err := client.UpdateObservation(context.Background(), "obs-1", dto.ObservationUpdate{Title: &title})
	if err == nil {
		t.Fatal("UpdateObservation should return error on 403")
	}
	if !cortexmem.IsForbidden(err) {
		t.Errorf("expected IsForbidden, got: %v", err)
	}
}

func TestDeleteObservation_PropagatesError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		w.Write([]byte(`{"error":"observation not found"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.DeleteObservation(context.Background(), "nonexistent")
	if err == nil {
		t.Fatal("DeleteObservation should return error on 404")
	}
	if !cortexmem.IsNotFound(err) {
		t.Errorf("expected IsNotFound, got: %v", err)
	}
}

func TestNewClient_TrailingSlashNormalization(t *testing.T) {
	// Trailing slash in BaseURL should be stripped to prevent //api/ paths
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Verify no double-slash in URL
		if strings.Contains(r.URL.Path, "//") {
			t.Errorf("URL path should not contain double-slash: %s", r.URL.Path)
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
	}))
	defer server.Close()

	// Pass URL with trailing slash — should be normalized
	client := cortexmem.NewClient(cortexmem.WithBaseURL(server.URL + "/"))
	defer client.Close()

	err := client.HealthCheck(context.Background())
	if err != nil {
		t.Fatalf("HealthCheck should succeed with trailing slash: %v", err)
	}
}

func TestNewClient_EmptyBaseURL_UsesDefault(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"service":"claude-mem-java","status":"ok"}`))
	}))
	defer server.Close()

	// With empty BaseURL, client should use default (and still work if we pass the server URL)
	client := cortexmem.NewClient(cortexmem.WithBaseURL(""))
	// The client should have defaulted to http://127.0.0.1:37777
	// This won't connect to our test server, but at least it shouldn't panic
	_ = client
	client.Close()
}

// ==================== Response Body Limit Tests ====================

func TestDoRequest_ResponseBodyLimit(t *testing.T) {
	// Server sends a response larger than MaxResponseBytes.
	// The client should still succeed (reads up to the limit), but the response
	// should be truncated. In practice, the JSON parse will fail on truncated data.
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		// Write a 11MB response (> 10MB MaxResponseBytes)
		chunk := make([]byte, 1024*1024) // 1MB
		for i := range chunk {
			chunk[i] = 'x'
		}
		for i := 0; i < 11; i++ {
			w.Write(chunk)
		}
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	// Should get a parse error because the truncated response isn't valid JSON
	if err == nil {
		t.Fatal("expected parse error for oversized response")
	}
	if !strings.Contains(err.Error(), "failed to parse") {
		t.Errorf("expected parse error, got: %v", err)
	}
}

func TestDoRequest_ResponseWithinLimit(t *testing.T) {
	// Verify normal responses within the limit still work fine
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{
			"version": "2.0.0",
			"status":  "ok",
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.GetVersion(context.Background())
	if err != nil {
		t.Fatalf("GetVersion should succeed for normal response: %v", err)
	}
	if result.Version != "2.0.0" {
		t.Errorf("expected version=2.0.0, got %v", result.Version)
	}
}

// ==================== Context Cancellation During Retry ====================

func TestFireAndForget_ContextCancelledDuringRetry(t *testing.T) {
	// Test that context cancellation during retry backoff is handled gracefully.
	// The first request fails, then the context is cancelled during backoff.
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.WriteHeader(http.StatusInternalServerError) // Always fail
	}))
	defer server.Close()

	// Use a long backoff so we can cancel during the sleep
	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithMaxRetries(5),
		cortexmem.WithRetryBackoff(5*time.Second),
	)

	ctx, cancel := context.WithTimeout(context.Background(), 200*time.Millisecond)
	defer cancel()

	err := client.RecordObservation(ctx, dto.ObservationRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
		ToolName:    "Read",
	})

	// Fire-and-forget should NEVER return error
	if err != nil {
		t.Fatalf("fire-and-forget should swallow context cancellation during retry: %v", err)
	}
	// Should have attempted 1 request (failed), then cancelled during backoff
	if attempts != 1 {
		t.Errorf("expected 1 attempt before context cancellation, got %d", attempts)
	}
}

// ==================== Additional Coverage Tests ====================

func TestSearch_WithTypeParam(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		if q.Get("type") != "tool_result" {
			t.Errorf("expected type=tool_result, got %s", q.Get("type"))
		}
		if q.Get("concept") != "error-handling" {
			t.Errorf("expected concept=error-handling, got %s", q.Get("concept"))
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(dto.SearchResult{Count: 0})
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.Search(context.Background(), dto.SearchRequest{
		Project: "/proj",
		Type:    "tool_result",
		Concept: "error-handling",
	})
	if err != nil {
		t.Fatalf("Search failed: %v", err)
	}
}

func TestGetObservationsByIds_PropagatesError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte(`{"error":"internal"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetObservationsByIds(context.Background(), []string{"obs-1"})
	if err == nil {
		t.Fatal("GetObservationsByIds should propagate error")
	}
	if !cortexmem.IsInternal(err) {
		t.Errorf("expected IsInternal, got: %v", err)
	}
}

func TestGetExtractionHistory_NegativeLimit(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.GetExtractionHistory(context.Background(), "/project", "user-prefs", "user-1", -1)
	if err == nil {
		t.Fatal("GetExtractionHistory should fail with negative limit")
	}
	if !strings.Contains(err.Error(), "limit must not be negative") {
		t.Errorf("expected negative limit error, got: %v", err)
	}
}

func TestGetExtractionHistory_ZeroLimit(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// limit=0 should be omitted (backend clamps 0→1, so we let it use the default)
		if r.URL.Query().Has("limit") {
			t.Errorf("limit should be omitted when 0, got limit=%s", r.URL.Query().Get("limit"))
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode([]dto.ExtractionResult{})
	}))
	defer server.Close()

	client := newTestClient(server)
	history, err := client.GetExtractionHistory(context.Background(), "/project", "user-prefs", "user-1", 0)
	if err != nil {
		t.Fatalf("GetExtractionHistory failed: %v", err)
	}
	if history == nil {
		t.Fatal("history should not be nil")
	}
}

func TestGetLatestExtraction_OmitsEmptyUserId(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// When userId is empty, it should NOT be in query params
		if r.URL.Query().Get("userId") != "" {
			t.Errorf("expected no userId param for empty userId, got %s", r.URL.Query().Get("userId"))
		}
		if r.URL.Query().Get("projectPath") != "/project" {
			t.Errorf("expected projectPath=/project, got %s", r.URL.Query().Get("projectPath"))
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]any{
			"status":        "ok",
			"template":      "user-prefs",
			"sessionId":     "sess-1",
			"extractedData": map[string]any{"result": "data"},
			"createdAt":     1700000000000,
			"observationId": "obs-1",
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.GetLatestExtraction(context.Background(), "/project", "user-prefs", "")
	if err != nil {
		t.Fatalf("GetLatestExtraction failed: %v", err)
	}
	if result.ExtractedData["result"] != "data" {
		t.Errorf("expected extractedData.result=data, got %v", result.ExtractedData["result"])
	}
}

func TestRecordUserPrompt_UsesCorrectEndpoint(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/ingest/user-prompt" {
			t.Errorf("expected /api/ingest/user-prompt, got %s", r.URL.Path)
		}
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)
		if body["prompt_text"] != "test prompt" {
			t.Errorf("expected prompt_text=test prompt, got %v", body["prompt_text"])
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.RecordUserPrompt(context.Background(), dto.UserPromptRequest{
		SessionID:   "sess-1",
		PromptText:  "test prompt",
		ProjectPath: "/proj",
	})
	if err != nil {
		t.Fatalf("RecordUserPrompt failed: %v", err)
	}
}

func TestRecordSessionEnd_UsesCorrectEndpoint(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/ingest/session-end" {
			t.Errorf("expected /api/ingest/session-end, got %s", r.URL.Path)
		}
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)
		if body["last_assistant_message"] != "Goodbye" {
			t.Errorf("expected last_assistant_message=Goodbye, got %v", body["last_assistant_message"])
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.RecordSessionEnd(context.Background(), dto.SessionEndRequest{
		SessionID:            "sess-1",
		ProjectPath:          "/proj",
		LastAssistantMessage: "Goodbye",
	})
	if err != nil {
		t.Fatalf("RecordSessionEnd failed: %v", err)
	}
}

func TestGetQualityDistribution_PropagatesError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusForbidden)
		w.Write([]byte(`{"error":"forbidden"}`))
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetQualityDistribution(context.Background(), "/proj")
	if err == nil {
		t.Fatal("GetQualityDistribution should propagate error")
	}
	if !cortexmem.IsForbidden(err) {
		t.Errorf("expected IsForbidden, got: %v", err)
	}
}

func TestMaxResponseBytes_ConstantValue(t *testing.T) {
	expected := int64(10 << 20) // 10MB
	if cortexmem.MaxResponseBytes != expected {
		t.Errorf("expected MaxResponseBytes=%d, got %d", expected, cortexmem.MaxResponseBytes)
	}
}

// ==================== Is* Helper Sentinel Fallback Tests ====================
// These tests verify that Is* helpers work with direct sentinel errors
// (not just APIError-wrapped errors), exercising the second `return` path.

func TestIsNotFound_SentinelFallback(t *testing.T) {
	// Direct sentinel error (not wrapped in APIError) should match IsNotFound
	if !cortexmem.IsNotFound(cortexmem.ErrNotFound) {
		t.Error("IsNotFound(ErrNotFound) should be true")
	}
	// Should not match other sentinels
	if cortexmem.IsNotFound(cortexmem.ErrBadRequest) {
		t.Error("IsNotFound(ErrBadRequest) should be false")
	}
}

func TestIsBadRequest_SentinelFallback(t *testing.T) {
	if !cortexmem.IsBadRequest(cortexmem.ErrBadRequest) {
		t.Error("IsBadRequest(ErrBadRequest) should be true")
	}
	if cortexmem.IsBadRequest(cortexmem.ErrNotFound) {
		t.Error("IsBadRequest(ErrNotFound) should be false")
	}
}

func TestIsUnauthorized_SentinelFallback(t *testing.T) {
	if !cortexmem.IsUnauthorized(cortexmem.ErrUnauthorized) {
		t.Error("IsUnauthorized(ErrUnauthorized) should be true")
	}
	if cortexmem.IsUnauthorized(cortexmem.ErrForbidden) {
		t.Error("IsUnauthorized(ErrForbidden) should be false")
	}
}

func TestIsForbidden_SentinelFallback(t *testing.T) {
	if !cortexmem.IsForbidden(cortexmem.ErrForbidden) {
		t.Error("IsForbidden(ErrForbidden) should be true")
	}
	if cortexmem.IsForbidden(cortexmem.ErrUnauthorized) {
		t.Error("IsForbidden(ErrUnauthorized) should be false")
	}
}

func TestIsConflict_SentinelFallback(t *testing.T) {
	if !cortexmem.IsConflict(cortexmem.ErrConflict) {
		t.Error("IsConflict(ErrConflict) should be true")
	}
	if cortexmem.IsConflict(cortexmem.ErrNotFound) {
		t.Error("IsConflict(ErrNotFound) should be false")
	}
}

func TestIsRateLimited_SentinelFallback(t *testing.T) {
	if !cortexmem.IsRateLimited(cortexmem.ErrRateLimited) {
		t.Error("IsRateLimited(ErrRateLimited) should be true")
	}
	if cortexmem.IsRateLimited(cortexmem.ErrInternal) {
		t.Error("IsRateLimited(ErrInternal) should be false")
	}
}

func TestIsInternal_SentinelFallback(t *testing.T) {
	if !cortexmem.IsInternal(cortexmem.ErrInternal) {
		t.Error("IsInternal(ErrInternal) should be true")
	}
	if cortexmem.IsInternal(cortexmem.ErrBadRequest) {
		t.Error("IsInternal(ErrBadRequest) should be false")
	}
}

func TestIsServiceUnavailable_SentinelFallback(t *testing.T) {
	if !cortexmem.IsServiceUnavailable(cortexmem.ErrServiceUnavailable) {
		t.Error("IsServiceUnavailable(ErrServiceUnavailable) should be true")
	}
}

func TestIsBadGateway_SentinelFallback(t *testing.T) {
	if !cortexmem.IsBadGateway(cortexmem.ErrBadGateway) {
		t.Error("IsBadGateway(ErrBadGateway) should be true")
	}
}

func TestIsGatewayTimeout_SentinelFallback(t *testing.T) {
	if !cortexmem.IsGatewayTimeout(cortexmem.ErrGatewayTimeout) {
		t.Error("IsGatewayTimeout(ErrGatewayTimeout) should be true")
	}
}

func TestIsUnprocessable_SentinelFallback(t *testing.T) {
	if !cortexmem.IsUnprocessable(cortexmem.ErrUnprocessable) {
		t.Error("IsUnprocessable(ErrUnprocessable) should be true")
	}
}

// ==================== Is* Helper Nil/Error Tests ====================

func TestIsHelpers_NilError(t *testing.T) {
	// All Is* helpers should return false for nil errors
	if cortexmem.IsNotFound(nil) {
		t.Error("IsNotFound(nil) should be false")
	}
	if cortexmem.IsBadRequest(nil) {
		t.Error("IsBadRequest(nil) should be false")
	}
	if cortexmem.IsInternal(nil) {
		t.Error("IsInternal(nil) should be false")
	}
	if cortexmem.IsRateLimited(nil) {
		t.Error("IsRateLimited(nil) should be false")
	}
	if cortexmem.IsClientError(nil) {
		t.Error("IsClientError(nil) should be false")
	}
	if cortexmem.IsServerError(nil) {
		t.Error("IsServerError(nil) should be false")
	}
}

func TestIsHelpers_GenericError(t *testing.T) {
	// Generic errors (not APIError or sentinel) should return false for all Is* helpers
	genericErr := errors.New("some random error")
	if cortexmem.IsNotFound(genericErr) {
		t.Error("IsNotFound should return false for generic error")
	}
	if cortexmem.IsBadRequest(genericErr) {
		t.Error("IsBadRequest should return false for generic error")
	}
	if cortexmem.IsInternal(genericErr) {
		t.Error("IsInternal should return false for generic error")
	}
	if cortexmem.IsClientError(genericErr) {
		t.Error("IsClientError should return false for generic error")
	}
	if cortexmem.IsServerError(genericErr) {
		t.Error("IsServerError should return false for generic error")
	}
}

// ==================== Input Validation Tests ====================

func TestRecordObservation_Validation_EmptySessionID(t *testing.T) {
	client := cortexmem.NewClient()
	err := client.RecordObservation(context.Background(), dto.ObservationRequest{
		SessionID:   "", // empty should fail
		ProjectPath: "/proj",
		ToolName:    "Read",
	})
	if err == nil {
		t.Fatal("RecordObservation should fail with empty sessionID")
	}
	if !strings.Contains(err.Error(), "SessionID is required") {
		t.Errorf("expected validation error about SessionID, got: %v", err)
	}
}

func TestRecordSessionEnd_Validation_EmptySessionID(t *testing.T) {
	client := cortexmem.NewClient()
	err := client.RecordSessionEnd(context.Background(), dto.SessionEndRequest{
		SessionID:   "", // empty should fail
		ProjectPath: "/proj",
	})
	if err == nil {
		t.Fatal("RecordSessionEnd should fail with empty sessionID")
	}
	if !strings.Contains(err.Error(), "SessionID is required") {
		t.Errorf("expected validation error about SessionID, got: %v", err)
	}
}

func TestRecordUserPrompt_Validation_EmptySessionID(t *testing.T) {
	client := cortexmem.NewClient()
	err := client.RecordUserPrompt(context.Background(), dto.UserPromptRequest{
		SessionID:   "", // empty should fail
		PromptText:  "test",
		ProjectPath: "/proj",
	})
	if err == nil {
		t.Fatal("RecordUserPrompt should fail with empty sessionID")
	}
	if !strings.Contains(err.Error(), "SessionID is required") {
		t.Errorf("expected validation error about SessionID, got: %v", err)
	}
}

func TestRecordUserPrompt_Validation_EmptyPromptText(t *testing.T) {
	client := cortexmem.NewClient()
	err := client.RecordUserPrompt(context.Background(), dto.UserPromptRequest{
		SessionID:   "sess-1",
		PromptText:  "", // empty should fail
		ProjectPath: "/proj",
	})
	if err == nil {
		t.Fatal("RecordUserPrompt should fail with empty promptText")
	}
	if !strings.Contains(err.Error(), "PromptText is required") {
		t.Errorf("expected validation error about PromptText, got: %v", err)
	}
}

func TestSearch_Validation_EmptyProject(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.Search(context.Background(), dto.SearchRequest{
		Project: "", // empty project should fail client-side
		Query:   "test",
	})
	if err == nil {
		t.Fatal("Search should fail with empty project")
	}
	if !strings.Contains(err.Error(), "Project is required") {
		t.Errorf("expected validation error about Project, got: %v", err)
	}
}

func TestRetrieveExperiences_Validation_EmptyTask(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.RetrieveExperiences(context.Background(), dto.ExperienceRequest{
		Task:    "", // empty task should fail client-side
		Project: "/proj",
	})
	if err == nil {
		t.Fatal("RetrieveExperiences should fail with empty task")
	}
	if !strings.Contains(err.Error(), "Task is required") {
		t.Errorf("expected validation error about Task, got: %v", err)
	}
}

func TestBuildICLPrompt_Validation_EmptyTask(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.BuildICLPrompt(context.Background(), dto.ICLPromptRequest{
		Task:    "", // empty task should fail client-side
		Project: "/proj",
	})
	if err == nil {
		t.Fatal("BuildICLPrompt should fail with empty task")
	}
	if !strings.Contains(err.Error(), "Task is required") {
		t.Errorf("expected validation error about Task, got: %v", err)
	}
}

func TestListObservations_EmptyProject_OmitsParam(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("project") != "" {
			t.Errorf("project param should be omitted when empty, got: %q", r.URL.Query().Get("project"))
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"items":[],"total":0,"hasMore":false}`))
	}))
	defer server.Close()

	client := cortexmem.NewClient(cortexmem.WithBaseURL(server.URL))
	_, err := client.ListObservations(context.Background(), dto.ObservationsRequest{
		Project: "", // empty project should succeed, omitting project query param
	})
	if err != nil {
		t.Fatalf("ListObservations with empty project should succeed: %v", err)
	}
}

func TestListObservations_HasMore_CamelCase(t *testing.T) {
	// Backend returns "hasMore" (camelCase) via Map.of() for WebUI compatibility.
	// Verify the Go SDK correctly parses this field.
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"items":[{"id":"o1"}],"total":50,"hasMore":true,"offset":0,"limit":20}`))
	}))
	defer server.Close()

	client := cortexmem.NewClient(cortexmem.WithBaseURL(server.URL))
	resp, err := client.ListObservations(context.Background(), dto.ObservationsRequest{
		Project: "/p",
		Limit:   20,
	})
	if err != nil {
		t.Fatalf("ListObservations failed: %v", err)
	}
	if !resp.HasMore {
		t.Error("expected HasMore=true from camelCase backend response")
	}
	if resp.Total != 50 {
		t.Errorf("expected total=50, got %d", resp.Total)
	}
	if len(resp.Items) != 1 || resp.Items[0].ID != "o1" {
		t.Errorf("expected 1 item with id=o1, got %v", resp.Items)
	}
}

func TestGetObservationsByIds_Validation_EmptyIDs(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.GetObservationsByIds(context.Background(), []string{})
	if err == nil {
		t.Fatal("GetObservationsByIds should fail with empty IDs")
	}
	if !strings.Contains(err.Error(), "ids must not be empty") {
		t.Errorf("expected validation error about ids, got: %v", err)
	}
}

func TestGetObservationsByIds_Validation_NilIDs(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.GetObservationsByIds(context.Background(), nil)
	if err == nil {
		t.Fatal("GetObservationsByIds should fail with nil IDs")
	}
	if !strings.Contains(err.Error(), "ids must not be empty") {
		t.Errorf("expected validation error about ids, got: %v", err)
	}
}

func TestGetObservationsByIds_Validation_EmptyStringInIDs(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.GetObservationsByIds(context.Background(), []string{"obs-1", "", "obs-3"})
	if err == nil {
		t.Fatal("GetObservationsByIds should fail with empty string in IDs")
	}
	if !cortexmem.IsValidationError(err) {
		t.Errorf("expected ValidationError, got: %v", err)
	}
	if !strings.Contains(err.Error(), "ids[1]") {
		t.Errorf("expected error mentioning ids[1], got: %v", err)
	}
}

func TestGetObservationsByIds_Validation_WhitespaceOnlyInIDs(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.GetObservationsByIds(context.Background(), []string{"obs-1", "   ", "obs-3"})
	if err == nil {
		t.Fatal("GetObservationsByIds should fail with whitespace-only string in IDs")
	}
	if !cortexmem.IsValidationError(err) {
		t.Errorf("expected ValidationError, got: %v", err)
	}
	if !strings.Contains(err.Error(), "ids[1]") {
		t.Errorf("expected error mentioning ids[1], got: %v", err)
	}
}

func TestGetObservationsByIds_Validation_ExceedsMaxBatch(t *testing.T) {
	client := cortexmem.NewClient()
	ids := make([]string, 101)
	for i := range ids {
		ids[i] = fmt.Sprintf("obs-%d", i)
	}
	_, err := client.GetObservationsByIds(context.Background(), ids)
	if err == nil {
		t.Fatal("GetObservationsByIds should fail with >100 IDs")
	}
	if !strings.Contains(err.Error(), "batch size exceeds maximum of 100") {
		t.Errorf("expected batch size error, got: %v", err)
	}
}

func TestGetObservationsByIds_Validation_ExactlyMaxBatch(t *testing.T) {
	// 100 IDs should be accepted (passes client-side validation)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(dto.BatchObservationsResponse{Count: 0, Observations: []dto.Observation{}})
	}))
	defer server.Close()

	client := newTestClient(server)
	ids := make([]string, 100)
	for i := range ids {
		ids[i] = fmt.Sprintf("obs-%d", i)
	}
	_, err := client.GetObservationsByIds(context.Background(), ids)
	if err != nil {
		t.Fatalf("GetObservationsByIds should succeed with exactly 100 IDs: %v", err)
	}
}

func TestTriggerRefinement_Validation_EmptyProjectPath(t *testing.T) {
	client := cortexmem.NewClient()
	err := client.TriggerRefinement(context.Background(), "")
	if err == nil {
		t.Fatal("TriggerRefinement should fail with empty projectPath")
	}
	if !strings.Contains(err.Error(), "projectPath is required") {
		t.Errorf("expected validation error about projectPath, got: %v", err)
	}
}

func TestTriggerRefinement_Validation_WhitespaceProjectPath(t *testing.T) {
	client := cortexmem.NewClient()
	err := client.TriggerRefinement(context.Background(), "   ")
	if err == nil {
		t.Fatal("TriggerRefinement should fail with whitespace-only projectPath")
	}
	if !strings.Contains(err.Error(), "projectPath is required") {
		t.Errorf("expected validation error about projectPath, got: %v", err)
	}
}

func TestGetQualityDistribution_Validation_EmptyProjectPath(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.GetQualityDistribution(context.Background(), "")
	if err == nil {
		t.Fatal("GetQualityDistribution should fail with empty projectPath")
	}
	if !strings.Contains(err.Error(), "projectPath is required") {
		t.Errorf("expected validation error about projectPath, got: %v", err)
	}
}

// ==================== DefaultConfig Verification ====================

// ==================== WithAPIKey Verification ====================

func TestWithAPIKey_SetsAuthorizationHeader(t *testing.T) {
	receivedAuth := ""
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedAuth = r.Header.Get("Authorization")
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"status":"ok"}`)
	}))
	defer server.Close()

	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithAPIKey("my-secret-key"),
	)
	err := client.HealthCheck(context.Background())
	if err != nil {
		t.Fatalf("HealthCheck failed: %v", err)
	}
	if receivedAuth != "Bearer my-secret-key" {
		t.Errorf("expected Authorization header 'Bearer my-secret-key', got %q", receivedAuth)
	}
}

func TestWithAPIKey_EmptyKey_OmitsAuthHeader(t *testing.T) {
	receivedAuth := ""
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedAuth = r.Header.Get("Authorization")
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"status":"ok"}`)
	}))
	defer server.Close()

	client := cortexmem.NewClient(
		cortexmem.WithBaseURL(server.URL),
		cortexmem.WithAPIKey(""), // Explicitly empty
	)
	_ = client.HealthCheck(context.Background())
	if receivedAuth != "" {
		t.Errorf("expected no Authorization header for empty API key, got %q", receivedAuth)
	}
}

// ==================== Extraction Validation ====================

func TestTriggerExtraction_Validation_EmptyProjectPath(t *testing.T) {
	client := cortexmem.NewClient()
	err := client.TriggerExtraction(context.Background(), "")
	if err == nil {
		t.Fatal("TriggerExtraction should fail with empty projectPath")
	}
	if !strings.Contains(err.Error(), "projectPath is required") {
		t.Errorf("expected validation error about projectPath, got: %v", err)
	}
}

func TestTriggerExtraction_Validation_WhitespaceProjectPath(t *testing.T) {
	client := cortexmem.NewClient()
	err := client.TriggerExtraction(context.Background(), "   ")
	if err == nil {
		t.Fatal("TriggerExtraction should fail with whitespace-only projectPath")
	}
	if !strings.Contains(err.Error(), "projectPath is required") {
		t.Errorf("expected validation error about projectPath, got: %v", err)
	}
}

func TestGetLatestExtraction_Validation_EmptyProjectPath(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.GetLatestExtraction(context.Background(), "", "template", "")
	if err == nil {
		t.Fatal("GetLatestExtraction should fail with empty projectPath")
	}
	if !strings.Contains(err.Error(), "projectPath is required") {
		t.Errorf("expected validation error about projectPath, got: %v", err)
	}
}

func TestGetLatestExtraction_Validation_EmptyTemplateName(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.GetLatestExtraction(context.Background(), "/project", "", "")
	if err == nil {
		t.Fatal("GetLatestExtraction should fail with empty templateName")
	}
	if !strings.Contains(err.Error(), "templateName is required") {
		t.Errorf("expected validation error about templateName, got: %v", err)
	}
}

func TestGetExtractionHistory_Validation_EmptyProjectPath(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.GetExtractionHistory(context.Background(), "", "template", "", 10)
	if err == nil {
		t.Fatal("GetExtractionHistory should fail with empty projectPath")
	}
	if !strings.Contains(err.Error(), "projectPath is required") {
		t.Errorf("expected validation error about projectPath, got: %v", err)
	}
}

func TestGetExtractionHistory_Validation_EmptyTemplateName(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.GetExtractionHistory(context.Background(), "/project", "", "", 10)
	if err == nil {
		t.Fatal("GetExtractionHistory should fail with empty templateName")
	}
	if !strings.Contains(err.Error(), "templateName is required") {
		t.Errorf("expected validation error about templateName, got: %v", err)
	}
}

func TestGetExtractionHistory_Validation_NegativeLimit(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.GetExtractionHistory(context.Background(), "/project", "template", "", -1)
	if err == nil {
		t.Fatal("GetExtractionHistory should fail with negative limit")
	}
	if !strings.Contains(err.Error(), "limit must not be negative") {
		t.Errorf("expected validation error about negative limit, got: %v", err)
	}
}

func TestUpdateObservation_Error_Response(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		fmt.Fprint(w, `{"error":"observation not found"}`)
	}))
	defer server.Close()

	client := newTestClient(server)
	title := "New Title"
	err := client.UpdateObservation(context.Background(), "nonexistent", dto.ObservationUpdate{Title: &title})
	if err == nil {
		t.Fatal("UpdateObservation should fail on 404")
	}
	if !cortexmem.IsNotFound(err) {
		t.Errorf("expected IsNotFound, got: %v", err)
	}
}

func TestUpdateObservation_EmptyUpdate_Rejected(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("server should not be called for empty update validation")
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.UpdateObservation(context.Background(), "obs-123", dto.ObservationUpdate{})
	if err == nil {
		t.Fatal("UpdateObservation should fail with empty update")
	}
	if !strings.Contains(err.Error(), "at least one field") {
		t.Errorf("expected 'at least one field' error, got: %v", err)
	}
}

func TestDeleteObservation_Error_Response(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		fmt.Fprint(w, `{"error":"observation not found"}`)
	}))
	defer server.Close()

	client := newTestClient(server)
	err := client.DeleteObservation(context.Background(), "nonexistent")
	if err == nil {
		t.Fatal("DeleteObservation should fail on 404")
	}
	if !cortexmem.IsNotFound(err) {
		t.Errorf("expected IsNotFound, got: %v", err)
	}
}

func TestGetSettings_Error_Response(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		fmt.Fprint(w, `{"error":"internal error"}`)
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetSettings(context.Background())
	if err == nil {
		t.Fatal("GetSettings should fail on 500")
	}
	if !cortexmem.IsInternal(err) {
		t.Errorf("expected IsInternal, got: %v", err)
	}
}

func TestGetVersion_Error_Response(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadGateway)
		fmt.Fprint(w, `{"error":"bad gateway"}`)
	}))
	defer server.Close()

	client := newTestClient(server)
	_, err := client.GetVersion(context.Background())
	if err == nil {
		t.Fatal("GetVersion should fail on 502")
	}
	if !cortexmem.IsBadGateway(err) {
		t.Errorf("expected IsBadGateway, got: %v", err)
	}
}

// ==================== Whitespace Validation Tests ====================

func TestSubmitFeedback_Validation_WhitespaceObservationID(t *testing.T) {
	client := cortexmem.NewClient()
	err := client.SubmitFeedback(context.Background(), "   ", "SUCCESS", "")
	if err == nil {
		t.Fatal("SubmitFeedback should fail with whitespace-only observationID")
	}
	if !strings.Contains(err.Error(), "observationID is required") {
		t.Errorf("expected validation error about observationID, got: %v", err)
	}
}

func TestSubmitFeedback_Validation_WhitespaceFeedbackType(t *testing.T) {
	client := cortexmem.NewClient()
	err := client.SubmitFeedback(context.Background(), "obs-1", "   ", "")
	if err == nil {
		t.Fatal("SubmitFeedback should fail with whitespace-only feedbackType")
	}
	if !strings.Contains(err.Error(), "feedbackType is required") {
		t.Errorf("expected validation error about feedbackType, got: %v", err)
	}
}

func TestUpdateObservation_Validation_WhitespaceObservationID(t *testing.T) {
	client := cortexmem.NewClient()
	err := client.UpdateObservation(context.Background(), "   ", dto.ObservationUpdate{})
	if err == nil {
		t.Fatal("UpdateObservation should fail with whitespace-only observationID")
	}
	if !strings.Contains(err.Error(), "observationID is required") {
		t.Errorf("expected validation error about observationID, got: %v", err)
	}
}

func TestDeleteObservation_Validation_WhitespaceObservationID(t *testing.T) {
	client := cortexmem.NewClient()
	err := client.DeleteObservation(context.Background(), "   ")
	if err == nil {
		t.Fatal("DeleteObservation should fail with whitespace-only observationID")
	}
	if !strings.Contains(err.Error(), "observationID is required") {
		t.Errorf("expected validation error about observationID, got: %v", err)
	}
}

func TestUpdateSessionUserId_Validation_WhitespaceSessionID(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.UpdateSessionUserId(context.Background(), "   ", "user-1")
	if err == nil {
		t.Fatal("UpdateSessionUserId should fail with whitespace-only sessionID")
	}
	if !strings.Contains(err.Error(), "sessionID is required") {
		t.Errorf("expected validation error about sessionID, got: %v", err)
	}
}

func TestUpdateSessionUserId_Validation_WhitespaceUserID(t *testing.T) {
	client := cortexmem.NewClient()
	_, err := client.UpdateSessionUserId(context.Background(), "sess-1", "   ")
	if err == nil {
		t.Fatal("UpdateSessionUserId should fail with whitespace-only userID")
	}
	if !strings.Contains(err.Error(), "userID is required") {
		t.Errorf("expected validation error about userID, got: %v", err)
	}
}

// ==================== DefaultConfig Verification ====================

func TestDefaultClientConfig_VerifyAllDefaults(t *testing.T) {
	cfg := cortexmem.DefaultClientConfig()
	if cfg.BaseURL != "http://127.0.0.1:37777" {
		t.Errorf("expected default BaseURL, got %s", cfg.BaseURL)
	}
	if cfg.Timeout != 30*time.Second {
		t.Errorf("expected 30s timeout, got %v", cfg.Timeout)
	}
	if cfg.ConnectTimeout != 10*time.Second {
		t.Errorf("expected 10s connect timeout, got %v", cfg.ConnectTimeout)
	}
	if cfg.MaxRetries != 3 {
		t.Errorf("expected 3 max retries, got %d", cfg.MaxRetries)
	}
	if cfg.RetryBackoff != 500*time.Millisecond {
		t.Errorf("expected 500ms retry backoff, got %v", cfg.RetryBackoff)
	}
	if cfg.APIKey != "" {
		t.Errorf("expected empty API key, got %s", cfg.APIKey)
	}
	if cfg.HTTPClient != nil {
		t.Error("expected nil HTTPClient in default config")
	}
}

// ==================== ValidationError Tests ====================

func TestIsValidationError_TrueForValidationErrors(t *testing.T) {
	client := cortexmem.NewClient()

	// Test various methods that produce ValidationError
	tests := []struct {
		name  string
		err   error
		field string
	}{
		{"RecordObservation", client.RecordObservation(context.Background(), dto.ObservationRequest{}), "SessionID"},
		{"Search", func() error { _, err := client.Search(context.Background(), dto.SearchRequest{}); return err }(), "Project"},
		{"RetrieveExperiences", func() error { _, err := client.RetrieveExperiences(context.Background(), dto.ExperienceRequest{}); return err }(), "Task"},
		{"DeleteObservation", client.DeleteObservation(context.Background(), ""), "observationID"},
		{"TriggerRefinement", client.TriggerRefinement(context.Background(), ""), "projectPath"},
		{"GetObservationsByIds", func() error { _, err := client.GetObservationsByIds(context.Background(), nil); return err }(), "ids"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if !cortexmem.IsValidationError(tt.err) {
				t.Errorf("expected IsValidationError=true for %s, got false (err: %v)", tt.name, tt.err)
			}
			if !strings.Contains(tt.err.Error(), tt.field) {
				t.Errorf("expected error to mention field %q, got: %v", tt.field, tt.err)
			}
		})
	}
}

func TestIsValidationError_FalseForNil(t *testing.T) {
	if cortexmem.IsValidationError(nil) {
		t.Error("IsValidationError should return false for nil")
	}
}

func TestIsValidationError_FalseForAPIError(t *testing.T) {
	apiErr := &cortexmem.APIError{StatusCode: 400, Message: "bad request"}
	if cortexmem.IsValidationError(apiErr) {
		t.Error("IsValidationError should return false for APIError")
	}
}

func TestValidationError_ErrorMessage(t *testing.T) {
	ve := &cortexmem.ValidationError{Field: "observationID", Message: "observationID is required"}
	expected := "cortex-ce: validation error on observationID: observationID is required"
	if ve.Error() != expected {
		t.Errorf("expected %q, got %q", expected, ve.Error())
	}
}

func TestValidationError_EmptyField(t *testing.T) {
	ve := &cortexmem.ValidationError{Message: "something went wrong"}
	expected := "cortex-ce: validation error: something went wrong"
	if ve.Error() != expected {
		t.Errorf("expected %q, got %q", expected, ve.Error())
	}
}

func TestUpdateObservation_UsesValidationError(t *testing.T) {
	client := cortexmem.NewClient()

	// Empty update should produce ValidationError
	err := client.UpdateObservation(context.Background(), "obs-1", dto.ObservationUpdate{})
	if !cortexmem.IsValidationError(err) {
		t.Errorf("expected ValidationError for empty update, got: %v", err)
	}

	// Empty observationID should produce ValidationError
	err = client.UpdateObservation(context.Background(), "", dto.ObservationUpdate{Title: strPtr("test")})
	if !cortexmem.IsValidationError(err) {
		t.Errorf("expected ValidationError for empty observationID, got: %v", err)
	}
}

// strPtr is a helper for tests that need *string.
func strPtr(s string) *string { return &s }
