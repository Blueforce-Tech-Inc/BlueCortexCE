package cortexmem_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

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
		json.NewEncoder(w).Encode([]dto.Observation{
			{ID: "obs-1", Content: "first"},
			{ID: "obs-2", Content: "second"},
		})
	}))
	defer server.Close()

	client := newTestClient(server)
	obs, err := client.GetObservationsByIds(context.Background(), []string{"obs-1", "obs-2"})
	if err != nil {
		t.Fatalf("GetObservationsByIds failed: %v", err)
	}
	if len(obs) != 2 {
		t.Fatalf("expected 2 observations, got %d", len(obs))
	}
	if obs[0].ID != "obs-1" {
		t.Errorf("expected obs-1, got %s", obs[0].ID)
	}
	if obs[1].Content != "second" {
		t.Errorf("expected content=second, got %s", obs[1].Content)
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
