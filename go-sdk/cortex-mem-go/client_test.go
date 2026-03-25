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
		w.Write([]byte(`{"prompt":"test","experienceCount":"0"}`))
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
		w.Write([]byte(`{"prompt":"test","experienceCount":"0"}`))
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
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	version, err := client.GetVersion(context.Background())
	if err != nil {
		t.Fatalf("GetVersion failed: %v", err)
	}
	if version["version"] != "1.0.0" {
		t.Errorf("expected version=1.0.0, got %v", version["version"])
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
		json.NewEncoder(w).Encode(map[string]any{"result": "data"})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.GetLatestExtraction(context.Background(), "/project", "user-preferences", "alice")
	if err != nil {
		t.Fatalf("GetLatestExtraction failed: %v", err)
	}
	if result["result"] != "data" {
		t.Errorf("expected result=data, got %v", result["result"])
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
	err := client.UpdateObservation(context.Background(), "obs-1", dto.ObservationUpdate{
		Title:         &title,
		ExtractedData: map[string]any{"key": "value"},
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
			{"result": "v1"},
			{"result": "v2"},
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	history, err := client.GetExtractionHistory(context.Background(), "/project", "user-preferences", "alice", 10)
	if err != nil {
		t.Fatalf("GetExtractionHistory failed: %v", err)
	}
	if len(history) != 2 {
		t.Fatalf("expected 2 history entries, got %d", len(history))
	}
	if history[0]["result"] != "v1" {
		t.Errorf("expected first result=v1, got %v", history[0]["result"])
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
		json.NewEncoder(w).Encode(map[string]any{"status": "ok"})
	}))
	defer server.Close()

	client := newTestClient(server)
	resp, err := client.UpdateSessionUserId(context.Background(), "sess-1", "user-42")
	if err != nil {
		t.Fatalf("UpdateSessionUserId failed: %v", err)
	}
	if resp["status"] != "ok" {
		t.Errorf("expected status=ok, got %v", resp["status"])
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
	projects, ok := result["projects"].([]any)
	if !ok || len(projects) != 2 {
		t.Errorf("expected 2 projects, got %v", result["projects"])
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
			"totalObservations": float64(100),
			"totalExperiences":  float64(50),
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.GetStats(context.Background(), "/my-project")
	if err != nil {
		t.Fatalf("GetStats failed: %v", err)
	}
	if result["totalObservations"] != float64(100) {
		t.Errorf("expected totalObservations=100, got %v", result["totalObservations"])
	}
}

func TestGetStats_EmptyProject(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("project") != "" {
			t.Errorf("expected no project query param for empty projectPath")
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]any{"totalObservations": float64(0)})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.GetStats(context.Background(), "")
	if err != nil {
		t.Fatalf("GetStats failed: %v", err)
	}
	if result["totalObservations"] != float64(0) {
		t.Errorf("expected totalObservations=0, got %v", result["totalObservations"])
	}
}

func TestGetModes(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/modes" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]any{
			"modes": []map[string]any{
				{"name": "default", "enabled": true},
			},
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.GetModes(context.Background())
	if err != nil {
		t.Fatalf("GetModes failed: %v", err)
	}
	modes, ok := result["modes"].([]any)
	if !ok || len(modes) != 1 {
		t.Errorf("expected 1 mode, got %v", result["modes"])
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
			w.WriteHeader(http.StatusInternalServerError)
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
		w.WriteHeader(http.StatusInternalServerError)
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
}

func TestFireAndForget_CustomBackoff(t *testing.T) {
	attempts := 0
	var timestamps []time.Time
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		timestamps = append(timestamps, time.Now())
		if attempts < 3 {
			w.WriteHeader(http.StatusInternalServerError)
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
	// Verify backoff timing: attempt 1→2 delay should be ~100ms, attempt 2→3 delay should be ~200ms
	if len(timestamps) >= 3 {
		delay1 := timestamps[1].Sub(timestamps[0])
		delay2 := timestamps[2].Sub(timestamps[1])
		// First backoff: 100ms * 1 = 100ms
		if delay1 < 80*time.Millisecond || delay1 > 200*time.Millisecond {
			t.Errorf("expected first delay ~100ms, got %v", delay1)
		}
		// Second backoff: 100ms * 2 = 200ms
		if delay2 < 180*time.Millisecond || delay2 > 350*time.Millisecond {
			t.Errorf("expected second delay ~200ms, got %v", delay2)
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

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(dto.ICLPromptResult{
			Prompt:           "Here are relevant experiences...",
			ExperienceCount:  "3",
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	result, err := client.BuildICLPrompt(context.Background(), dto.ICLPromptRequest{
		Task:     "Write a parser",
		Project:  "/proj",
		MaxChars: 4000,
	})
	if err != nil {
		t.Fatalf("BuildICLPrompt failed: %v", err)
	}
	if result.Prompt != "Here are relevant experiences..." {
		t.Errorf("unexpected prompt: %s", result.Prompt)
	}
	if result.ExperienceCount != "3" {
		t.Errorf("expected experienceCount=3, got %s", result.ExperienceCount)
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
