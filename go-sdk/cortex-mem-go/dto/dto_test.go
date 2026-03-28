package dto

import (
	"encoding/json"
	"testing"
)

// stringPtr returns a pointer to the given string value.
func stringPtr(s string) *string { return &s }

// ==================== SearchResult Wire Format Tests ====================

func TestSearchResult_FellBack_Deserialization(t *testing.T) {
	jsonData := `{"observations":[],"strategy":"keyword","fell_back":true,"count":0}`
	var result SearchResult
	if err := json.Unmarshal([]byte(jsonData), &result); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if !result.FellBack {
		t.Error("expected fell_back=true from JSON deserialization")
	}
	if result.Strategy != "keyword" {
		t.Errorf("expected strategy=keyword, got %s", result.Strategy)
	}
}

func TestSearchResult_FellBack_False_Default(t *testing.T) {
	jsonData := `{"observations":[],"strategy":"hybrid","count":0}`
	var result SearchResult
	if err := json.Unmarshal([]byte(jsonData), &result); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if result.FellBack {
		t.Error("expected fell_back=false when not present in JSON")
	}
}

// ==================== QualityDistribution Tests ====================

func TestQualityDistribution_Total(t *testing.T) {
	dist := QualityDistribution{
		Project: "/project",
		High:    10,
		Medium:  5,
		Low:     3,
		Unknown: 2,
	}
	if got := dist.Total(); got != 20 {
		t.Errorf("Total() = %d, want 20", got)
	}
}

func TestQualityDistribution_Total_Zeros(t *testing.T) {
	dist := QualityDistribution{}
	if got := dist.Total(); got != 0 {
		t.Errorf("Total() = %d, want 0", got)
	}
}

func TestQualityDistribution_Total_SingleCategory(t *testing.T) {
	dist := QualityDistribution{High: 42}
	if got := dist.Total(); got != 42 {
		t.Errorf("Total() = %d, want 42", got)
	}
}

func TestQualityDistribution_WireFormat(t *testing.T) {
	dist := QualityDistribution{
		Project: "/proj",
		High:    10,
		Medium:  5,
		Low:     2,
		Unknown: 1,
	}
	data, err := json.Marshal(dist)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	if decoded["project"] != "/proj" {
		t.Errorf("expected project=/proj, got %v", decoded["project"])
	}
	if decoded["high"] != float64(10) {
		t.Errorf("expected high=10, got %v", decoded["high"])
	}
}

// ==================== FeedbackRequest Wire Format Tests ====================

func TestFeedbackRequest_CamelCase(t *testing.T) {
	req := FeedbackRequest{
		ObservationID: "obs-1",
		FeedbackType:  "SUCCESS",
		Comment:       "great work",
	}
	data, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	// Must be camelCase for Java backend
	if decoded["observationId"] != "obs-1" {
		t.Errorf("expected observationId=obs-1, got %v", decoded["observationId"])
	}
	if decoded["feedbackType"] != "SUCCESS" {
		t.Errorf("expected feedbackType=SUCCESS, got %v", decoded["feedbackType"])
	}
	// Should NOT have snake_case
	if decoded["observation_id"] != nil {
		t.Error("observation_id should not exist (should be 'observationId')")
	}
	if decoded["feedback_type"] != nil {
		t.Error("feedback_type should not exist (should be 'feedbackType')")
	}
}

// ==================== ObservationRequest Wire Format Tests ====================

func TestObservationRequest_UsesCWD(t *testing.T) {
	req := ObservationRequest{
		SessionID:   "sess-1",
		ProjectPath: "/path/to/project",
		ToolName:    "Read",
	}
	data, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	// ProjectPath should serialize as "cwd", not "project_path"
	if decoded["cwd"] != "/path/to/project" {
		t.Errorf("expected cwd=/path/to/project, got %v", decoded["cwd"])
	}
	if decoded["project_path"] != nil {
		t.Error("project_path should not exist (should be 'cwd')")
	}
	if decoded["projectPath"] != nil {
		t.Error("projectPath should not exist (should be 'cwd')")
	}
}

func TestObservationRequest_OmitsEmptyFields(t *testing.T) {
	req := ObservationRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
		ToolName:    "Read",
		// ToolInput, ToolResponse, Source, ExtractedData all zero values
	}
	data, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	if _, ok := decoded["tool_input"]; ok {
		t.Error("empty tool_input should be omitted")
	}
	if _, ok := decoded["tool_response"]; ok {
		t.Error("empty tool_response should be omitted")
	}
	if _, ok := decoded["source"]; ok {
		t.Error("empty source should be omitted")
	}
	if _, ok := decoded["extractedData"]; ok {
		t.Error("empty extractedData should be omitted")
	}
}

// ==================== ObservationUpdate Wire Format Tests ====================

func TestObservationUpdate_SubtitleField(t *testing.T) {
	subtitle := "A subtitle"
	update := ObservationUpdate{
		Subtitle: &subtitle,
	}
	data, err := json.Marshal(update)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	if decoded["subtitle"] != "A subtitle" {
		t.Errorf("expected subtitle=A subtitle, got %v", decoded["subtitle"])
	}
	// Other nil pointer fields should be omitted
	if _, ok := decoded["title"]; ok {
		t.Error("nil title should be omitted")
	}
}

func TestObservationUpdate_NilSubtitleOmitted(t *testing.T) {
	update := ObservationUpdate{
		Title: stringPtr("has title"),
		// Subtitle is nil — should be omitted
	}
	data, err := json.Marshal(update)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	if _, ok := decoded["subtitle"]; ok {
		t.Error("nil subtitle should be omitted")
	}
	if decoded["title"] != "has title" {
		t.Errorf("expected title=has title, got %v", decoded["title"])
	}
}

func TestObservationUpdate_NilPointerFields(t *testing.T) {
	update := ObservationUpdate{
		ExtractedData: map[string]any{"key": "value"},
		// Title, Content, Source are nil pointers
	}
	data, err := json.Marshal(update)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	if _, ok := decoded["title"]; ok {
		t.Error("nil title pointer should be omitted")
	}
	if _, ok := decoded["content"]; ok {
		t.Error("nil content pointer should be omitted")
	}
	if decoded["extractedData"] == nil {
		t.Error("non-nil extractedData should be present")
	}
}

func TestObservationUpdate_EmptyStringPointerIsSent(t *testing.T) {
	// Empty string through pointer should be sent (allows clearing)
	emptyStr := ""
	update := ObservationUpdate{
		Source: &emptyStr,
	}
	data, err := json.Marshal(update)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	source, ok := decoded["source"]
	if !ok {
		t.Error("pointer source with empty string should be present")
	}
	if source != "" {
		t.Errorf("expected empty string source, got %v", source)
	}
}

// ==================== ObservationUpdate.IsEmpty() Tests ====================

func TestObservationUpdate_IsEmpty_AllNil(t *testing.T) {
	update := ObservationUpdate{}
	if !update.IsEmpty() {
		t.Error("expected IsEmpty()=true for zero-value ObservationUpdate")
	}
}

func TestObservationUpdate_IsEmpty_TitleSet(t *testing.T) {
	update := ObservationUpdate{Title: stringPtr("test")}
	if update.IsEmpty() {
		t.Error("expected IsEmpty()=false when Title is set")
	}
}

func TestObservationUpdate_IsEmpty_SourceSet(t *testing.T) {
	update := ObservationUpdate{Source: stringPtr("manual")}
	if update.IsEmpty() {
		t.Error("expected IsEmpty()=false when Source is set")
	}
}

func TestObservationUpdate_IsEmpty_FactsSet(t *testing.T) {
	update := ObservationUpdate{Facts: []string{"a", "b"}}
	if update.IsEmpty() {
		t.Error("expected IsEmpty()=false when Facts is set")
	}
}

func TestObservationUpdate_IsEmpty_ExtractedDataSet(t *testing.T) {
	update := ObservationUpdate{ExtractedData: map[string]any{"key": "val"}}
	if update.IsEmpty() {
		t.Error("expected IsEmpty()=false when ExtractedData is set")
	}
}

func TestObservationUpdate_IsEmpty_EmptyStringPointerNotEmpty(t *testing.T) {
	// Empty string pointer is still "set" — allows clearing a field
	empty := ""
	update := ObservationUpdate{Source: &empty}
	if update.IsEmpty() {
		t.Error("expected IsEmpty()=false when Source is non-nil (even if empty string)")
	}
}

// ==================== Session DTO Wire Format Tests ====================

func TestSessionStartRequest_UsesProjectPath(t *testing.T) {
	req := SessionStartRequest{
		SessionID:   "sess-1",
		ProjectPath: "/project",
		UserID:      "user-1",
	}
	data, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	// Session start uses "project_path", NOT "cwd"
	if decoded["project_path"] != "/project" {
		t.Errorf("expected project_path=/project, got %v", decoded["project_path"])
	}
	if decoded["cwd"] != nil {
		t.Error("cwd should not exist for session start (should be 'project_path')")
	}
	if decoded["sessionId"] != nil {
		t.Error("sessionId should not exist (should be 'session_id')")
	}
}

func TestSessionEndRequest_UsesCWD(t *testing.T) {
	req := SessionEndRequest{
		SessionID:            "sess-1",
		ProjectPath:          "/project",
		LastAssistantMessage: "Done!",
	}
	data, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	if decoded["cwd"] != "/project" {
		t.Errorf("expected cwd=/project, got %v", decoded["cwd"])
	}
	if decoded["last_assistant_message"] != "Done!" {
		t.Errorf("expected last_assistant_message=Done!, got %v", decoded["last_assistant_message"])
	}
}

func TestUserPromptRequest_UsesCWD(t *testing.T) {
	req := UserPromptRequest{
		SessionID:   "sess-1",
		PromptText:  "Hello",
		ProjectPath: "/project",
	}
	data, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	if decoded["cwd"] != "/project" {
		t.Errorf("expected cwd=/project, got %v", decoded["cwd"])
	}
	if decoded["prompt_text"] != "Hello" {
		t.Errorf("expected prompt_text=Hello, got %v", decoded["prompt_text"])
	}
}

// ==================== Experience Wire Format Tests ====================

func TestExperienceRequest_OmitsEmptyProject(t *testing.T) {
	req := ExperienceRequest{
		Task:    "test",
		Project: "", // empty should be omitted
	}
	data, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	if _, ok := decoded["project"]; ok {
		t.Error("empty project should be omitted")
	}
}

func TestExperienceRequest_RequiredConcepts_CamelCase(t *testing.T) {
	req := ExperienceRequest{
		Task:             "test",
		RequiredConcepts: []string{"json", "parsing"},
	}
	data, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	// Must be camelCase
	if _, ok := decoded["required_concepts"]; ok {
		t.Error("required_concepts should not exist (should be 'requiredConcepts')")
	}
	rc, ok := decoded["requiredConcepts"].([]any)
	if !ok || len(rc) != 2 {
		t.Errorf("expected requiredConcepts with 2 elements, got %v", decoded["requiredConcepts"])
	}
}

// ==================== BatchObservations Wire Format Tests ====================

func TestBatchObservationsRequest_WireFormat(t *testing.T) {
	req := BatchObservationsRequest{
		IDs: []string{"obs-1", "obs-2", "obs-3"},
	}
	data, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	ids, ok := decoded["ids"].([]any)
	if !ok {
		t.Fatal("expected ids array")
	}
	if len(ids) != 3 {
		t.Errorf("expected 3 ids, got %d", len(ids))
	}
}

func TestBatchObservationsRequest_EmptyIDs(t *testing.T) {
	req := BatchObservationsRequest{
		IDs: []string{},
	}
	data, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	// Empty slice should still produce "ids": [] (not omitted)
	ids, ok := decoded["ids"]
	if !ok {
		t.Error("ids field should be present even when empty")
	}
	if arr, ok := ids.([]any); ok && len(arr) != 0 {
		t.Errorf("expected empty array, got %v", arr)
	}
}

// ==================== ExtractionResult Wire Format Tests ====================

func TestExtractionResult_LatestDeserialization(t *testing.T) {
	jsonData := `{"status":"ok","template":"user-preferences","sessionId":"sess-1","extractedData":{"name":"Alice","allergies":["peanuts"]},"createdAt":1700000000000,"observationId":"obs-1"}`
	var result ExtractionResult
	if err := json.Unmarshal([]byte(jsonData), &result); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if result.Status != "ok" {
		t.Errorf("expected status=ok, got %s", result.Status)
	}
	if result.Template != "user-preferences" {
		t.Errorf("expected template=user-preferences, got %s", result.Template)
	}
	if result.SessionID != "sess-1" {
		t.Errorf("expected sessionId=sess-1, got %s", result.SessionID)
	}
	if result.ExtractedData["name"] != "Alice" {
		t.Errorf("expected extractedData.name=Alice, got %v", result.ExtractedData["name"])
	}
	if result.CreatedAt != 1700000000000 {
		t.Errorf("expected createdAt=1700000000000, got %d", result.CreatedAt)
	}
	if result.ObservationID != "obs-1" {
		t.Errorf("expected observationId=obs-1, got %s", result.ObservationID)
	}
}

func TestExtractionResult_NotFoundDeserialization(t *testing.T) {
	jsonData := `{"status":"not_found","message":"no extraction found for template"}`
	var result ExtractionResult
	if err := json.Unmarshal([]byte(jsonData), &result); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if result.Status != "not_found" {
		t.Errorf("expected status=not_found, got %s", result.Status)
	}
	if result.Message != "no extraction found for template" {
		t.Errorf("expected message, got %s", result.Message)
	}
}

func TestExtractionResult_HistoryDeserialization(t *testing.T) {
	// History results may not have status/template fields
	jsonData := `{"sessionId":"sess-2","extractedData":{"key":"v2"},"createdAt":1700001000000,"observationId":"obs-2"}`
	var result ExtractionResult
	if err := json.Unmarshal([]byte(jsonData), &result); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if result.SessionID != "sess-2" {
		t.Errorf("expected sessionId=sess-2, got %s", result.SessionID)
	}
	if result.ExtractedData["key"] != "v2" {
		t.Errorf("expected extractedData.key=v2, got %v", result.ExtractedData["key"])
	}
	if result.ObservationID != "obs-2" {
		t.Errorf("expected observationId=obs-2, got %s", result.ObservationID)
	}
	// Status and Template should be empty for history results
	if result.Status != "" {
		t.Errorf("expected empty status for history result, got %s", result.Status)
	}
}

func TestExtractionResult_CamelCaseFields(t *testing.T) {
	// Verify the wire format uses camelCase for sessionId, extractedData, createdAt, observationId
	req := ExtractionResult{
		SessionID:     "sess-1",
		ExtractedData: map[string]any{"pref": "value"},
		CreatedAt:     1700000000000,
		ObservationID: "obs-1",
	}
	data, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	// Must be camelCase
	if decoded["session_id"] != nil {
		t.Error("session_id should not exist (should be 'sessionId')")
	}
	if decoded["extracted_data"] != nil {
		t.Error("extracted_data should not exist (should be 'extractedData')")
	}
	if decoded["created_at"] != nil {
		t.Error("created_at should not exist (should be 'createdAt')")
	}
	if decoded["observation_id"] != nil {
		t.Error("observation_id should not exist (should be 'observationId')")
	}
	if decoded["sessionId"] != "sess-1" {
		t.Errorf("expected sessionId=sess-1, got %v", decoded["sessionId"])
	}
	if decoded["observationId"] != "obs-1" {
		t.Errorf("expected observationId=obs-1, got %v", decoded["observationId"])
	}
}

// ==================== SessionUserUpdateResponse Wire Format Tests ====================

func TestSessionUserUpdateResponse_Deserialization(t *testing.T) {
	jsonData := `{"status":"ok","sessionId":"sess-1","userId":"user-42"}`
	var resp SessionUserUpdateResponse
	if err := json.Unmarshal([]byte(jsonData), &resp); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if resp.Status != "ok" {
		t.Errorf("expected status=ok, got %s", resp.Status)
	}
	if resp.SessionID != "sess-1" {
		t.Errorf("expected sessionId=sess-1, got %s", resp.SessionID)
	}
	if resp.UserID != "user-42" {
		t.Errorf("expected userId=user-42, got %s", resp.UserID)
	}
}

// ==================== Experience Wire Format Tests ====================

func TestExperience_Deserialization(t *testing.T) {
	// Backend uses SNAKE_CASE naming strategy for Experience fields
	jsonData := `{"id":"exp-1","task":"handle auth","strategy":"use JWT","outcome":"success","reuse_condition":"similar auth flows","quality_score":0.85,"created_at":"2026-03-28T10:00:00Z"}`
	var exp Experience
	if err := json.Unmarshal([]byte(jsonData), &exp); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if exp.ID != "exp-1" {
		t.Errorf("expected id=exp-1, got %s", exp.ID)
	}
	if exp.ReuseCondition != "similar auth flows" {
		t.Errorf("expected reuse_condition='similar auth flows', got %q", exp.ReuseCondition)
	}
	if exp.QualityScore != 0.85 {
		t.Errorf("expected quality_score=0.85, got %f", exp.QualityScore)
	}
	if exp.CreatedAt != "2026-03-28T10:00:00Z" {
		t.Errorf("expected created_at, got %s", exp.CreatedAt)
	}
}

// ==================== ExtractionResult Zero-Value Tests ====================

func TestExtractionResult_ZeroValue_Marshal(t *testing.T) {
	// Zero-value ExtractionResult should not include omitempty fields as empty
	result := ExtractionResult{}
	data, err := json.Marshal(result)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}
	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	// sessionId, extractedData, createdAt, observationId are NOT omitempty — always present
	if _, ok := decoded["sessionId"]; !ok {
		t.Error("sessionId should always be present (not omitempty)")
	}
	if _, ok := decoded["extractedData"]; !ok {
		t.Error("extractedData should always be present (not omitempty)")
	}
	if _, ok := decoded["observationId"]; !ok {
		t.Error("observationId should always be present (not omitempty)")
	}
	// status, template, message ARE omitempty — should be absent when empty
	if _, ok := decoded["status"]; ok {
		t.Error("empty status should be omitted (omitempty)")
	}
	if _, ok := decoded["template"]; ok {
		t.Error("empty template should be omitted (omitempty)")
	}
	if _, ok := decoded["message"]; ok {
		t.Error("empty message should be omitted (omitempty)")
	}
}

// ==================== Observation FeedbackType Tests ====================

func TestObservation_FeedbackType_Deserialization(t *testing.T) {
	// Backend exposes feedback_type on observations
	jsonData := `{"id":"obs-1","content_session_id":"sess-1","project":"/proj","type":"tool-use","narrative":"test content","quality_score":0.8,"feedback_type":"SUCCESS","created_at":"2026-03-28T10:00:00Z","created_at_epoch":1700000000000}`
	var obs Observation
	if err := json.Unmarshal([]byte(jsonData), &obs); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if obs.FeedbackType != "SUCCESS" {
		t.Errorf("expected feedback_type=SUCCESS, got %s", obs.FeedbackType)
	}
	if obs.QualityScore != 0.8 {
		t.Errorf("expected quality_score=0.8, got %f", obs.QualityScore)
	}
}

func TestObservation_FeedbackType_OmittedWhenAbsent(t *testing.T) {
	// When feedback_type is not present, it should be empty string
	jsonData := `{"id":"obs-2","content_session_id":"sess-1","project":"/proj","type":"tool-use","narrative":"test"}`
	var obs Observation
	if err := json.Unmarshal([]byte(jsonData), &obs); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if obs.FeedbackType != "" {
		t.Errorf("expected empty feedback_type when absent, got %s", obs.FeedbackType)
	}
}

func TestObservation_FilesReadDeserialization(t *testing.T) {
	jsonData := `{"id":"obs-1","content_session_id":"sess-1","project":"/proj","type":"tool-use","narrative":"read main.go","files_read":["main.go","util.go"]}`
	var obs Observation
	if err := json.Unmarshal([]byte(jsonData), &obs); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if len(obs.FilesRead) != 2 {
		t.Fatalf("expected 2 files_read, got %d", len(obs.FilesRead))
	}
	if obs.FilesRead[0] != "main.go" || obs.FilesRead[1] != "util.go" {
		t.Errorf("expected [main.go, util.go], got %v", obs.FilesRead)
	}
}

func TestObservation_FilesModifiedDeserialization(t *testing.T) {
	jsonData := `{"id":"obs-1","content_session_id":"sess-1","project":"/proj","type":"tool-use","narrative":"edit main.go","files_modified":["main.go"]}`
	var obs Observation
	if err := json.Unmarshal([]byte(jsonData), &obs); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if len(obs.FilesModified) != 1 {
		t.Fatalf("expected 1 files_modified, got %d", len(obs.FilesModified))
	}
	if obs.FilesModified[0] != "main.go" {
		t.Errorf("expected files_modified=[main.go], got %v", obs.FilesModified)
	}
}

func TestObservation_FeedbackUpdatedAtDeserialization(t *testing.T) {
	jsonData := `{"id":"obs-1","content_session_id":"sess-1","project":"/proj","type":"tool-use","narrative":"test","feedback_type":"SUCCESS","feedback_updated_at":"2026-03-28T10:00:00Z"}`
	var obs Observation
	if err := json.Unmarshal([]byte(jsonData), &obs); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if obs.FeedbackUpdatedAt != "2026-03-28T10:00:00Z" {
		t.Errorf("expected feedback_updated_at=2026-03-28T10:00:00Z, got %s", obs.FeedbackUpdatedAt)
	}
}

func TestObservation_LastAccessedAtDeserialization(t *testing.T) {
	jsonData := `{"id":"obs-1","content_session_id":"sess-1","project":"/proj","type":"tool-use","narrative":"test","last_accessed_at":"2026-03-28T12:00:00Z"}`
	var obs Observation
	if err := json.Unmarshal([]byte(jsonData), &obs); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if obs.LastAccessedAt != "2026-03-28T12:00:00Z" {
		t.Errorf("expected last_accessed_at=2026-03-28T12:00:00Z, got %s", obs.LastAccessedAt)
	}
}

func TestObservation_NewFields_OmittedWhenAbsent(t *testing.T) {
	jsonData := `{"id":"obs-2","content_session_id":"sess-1","project":"/proj","type":"tool-use","narrative":"test"}`
	var obs Observation
	if err := json.Unmarshal([]byte(jsonData), &obs); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if obs.FilesRead != nil {
		t.Error("files_read should be nil when absent")
	}
	if obs.FilesModified != nil {
		t.Error("files_modified should be nil when absent")
	}
	if obs.FeedbackUpdatedAt != "" {
		t.Errorf("feedback_updated_at should be empty when absent, got %s", obs.FeedbackUpdatedAt)
	}
	if obs.LastAccessedAt != "" {
		t.Errorf("last_accessed_at should be empty when absent, got %s", obs.LastAccessedAt)
	}
}

// ==================== Observation V14 Fields Deserialization ====================

func TestObservation_SourceDeserialization(t *testing.T) {
	jsonData := `{"id":"obs-1","content_session_id":"sess-1","project":"/proj","type":"tool-use","narrative":"test","source":"manual"}`
	var obs Observation
	if err := json.Unmarshal([]byte(jsonData), &obs); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if obs.Source != "manual" {
		t.Errorf("expected source=manual, got %s", obs.Source)
	}
}

func TestObservation_ExtractedDataDeserialization(t *testing.T) {
	jsonData := `{"id":"obs-1","content_session_id":"sess-1","project":"/proj","type":"tool-use","narrative":"test","extractedData":{"price_range":"3000","brands":["sony","bose"]}}`
	var obs Observation
	if err := json.Unmarshal([]byte(jsonData), &obs); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if obs.ExtractedData == nil {
		t.Fatal("extractedData should not be nil")
	}
	if obs.ExtractedData["price_range"] != "3000" {
		t.Errorf("expected extractedData.price_range=3000, got %v", obs.ExtractedData["price_range"])
	}
	brands, ok := obs.ExtractedData["brands"].([]any)
	if !ok || len(brands) != 2 {
		t.Errorf("expected extractedData.brands=[sony, bose], got %v", obs.ExtractedData["brands"])
	}
}

func TestObservation_ExtractedData_OmittedWhenAbsent(t *testing.T) {
	jsonData := `{"id":"obs-1","content_session_id":"sess-1","project":"/proj","type":"tool-use","narrative":"test"}`
	var obs Observation
	if err := json.Unmarshal([]byte(jsonData), &obs); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if obs.ExtractedData != nil {
		t.Error("extractedData should be nil when absent")
	}
}

func TestObservation_FullV14Deserialization(t *testing.T) {
	// Complete V14 observation with all new fields
	jsonData := `{"id":"obs-1","content_session_id":"sess-1","project":"/proj","type":"tool-use","title":"Test","subtitle":"Sub","narrative":"content","facts":["f1"],"concepts":["c1"],"files_read":["a.go"],"files_modified":["b.go"],"quality_score":0.9,"feedback_type":"SUCCESS","feedback_updated_at":"2026-03-28T10:00:00Z","source":"tool_result","extractedData":{"key":"val"},"prompt_number":5,"created_at":"2026-03-28T09:00:00Z","created_at_epoch":1700000000,"last_accessed_at":"2026-03-28T12:00:00Z"}`
	var obs Observation
	if err := json.Unmarshal([]byte(jsonData), &obs); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if obs.ID != "obs-1" {
		t.Errorf("expected id=obs-1, got %s", obs.ID)
	}
	if obs.SessionID != "sess-1" {
		t.Errorf("expected content_session_id=sess-1, got %s", obs.SessionID)
	}
	if obs.ProjectPath != "/proj" {
		t.Errorf("expected project=/proj, got %s", obs.ProjectPath)
	}
	if obs.Type != "tool-use" {
		t.Errorf("expected type=tool-use, got %s", obs.Type)
	}
	if obs.Title != "Test" {
		t.Errorf("expected title=Test, got %s", obs.Title)
	}
	if obs.Subtitle != "Sub" {
		t.Errorf("expected subtitle=Sub, got %s", obs.Subtitle)
	}
	if obs.Content != "content" {
		t.Errorf("expected narrative=content, got %s", obs.Content)
	}
	if len(obs.Facts) != 1 || obs.Facts[0] != "f1" {
		t.Errorf("expected facts=[f1], got %v", obs.Facts)
	}
	if len(obs.Concepts) != 1 || obs.Concepts[0] != "c1" {
		t.Errorf("expected concepts=[c1], got %v", obs.Concepts)
	}
	if len(obs.FilesRead) != 1 || obs.FilesRead[0] != "a.go" {
		t.Errorf("expected files_read=[a.go], got %v", obs.FilesRead)
	}
	if len(obs.FilesModified) != 1 || obs.FilesModified[0] != "b.go" {
		t.Errorf("expected files_modified=[b.go], got %v", obs.FilesModified)
	}
	if obs.QualityScore != 0.9 {
		t.Errorf("expected quality_score=0.9, got %f", obs.QualityScore)
	}
	if obs.FeedbackType != "SUCCESS" {
		t.Errorf("expected feedback_type=SUCCESS, got %s", obs.FeedbackType)
	}
	if obs.FeedbackUpdatedAt != "2026-03-28T10:00:00Z" {
		t.Errorf("expected feedback_updated_at, got %s", obs.FeedbackUpdatedAt)
	}
	if obs.Source != "tool_result" {
		t.Errorf("expected source=tool_result, got %s", obs.Source)
	}
	if obs.ExtractedData["key"] != "val" {
		t.Errorf("expected extractedData.key=val, got %v", obs.ExtractedData["key"])
	}
	if obs.PromptNumber != 5 {
		t.Errorf("expected prompt_number=5, got %d", obs.PromptNumber)
	}
	if obs.CreatedAt != "2026-03-28T09:00:00Z" {
		t.Errorf("expected created_at, got %s", obs.CreatedAt)
	}
	if obs.CreatedAtEpoch != 1700000000 {
		t.Errorf("expected created_at_epoch=1700000000, got %d", obs.CreatedAtEpoch)
	}
	if obs.LastAccessedAt != "2026-03-28T12:00:00Z" {
		t.Errorf("expected last_accessed_at, got %s", obs.LastAccessedAt)
	}
}

func TestSessionUserUpdateResponse_CamelCaseFields(t *testing.T) {
	resp := SessionUserUpdateResponse{
		Status:    "ok",
		SessionID: "sess-1",
		UserID:    "user-42",
	}
	data, err := json.Marshal(resp)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	// Must be camelCase
	if decoded["session_id"] != nil {
		t.Error("session_id should not exist (should be 'sessionId')")
	}
	if decoded["user_id"] != nil {
		t.Error("user_id should not exist (should be 'userId')")
	}
	if decoded["sessionId"] != "sess-1" {
		t.Errorf("expected sessionId=sess-1, got %v", decoded["sessionId"])
	}
	if decoded["userId"] != "user-42" {
		t.Errorf("expected userId=user-42, got %v", decoded["userId"])
	}
}
