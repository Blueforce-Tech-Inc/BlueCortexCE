package dto

// ObservationRequest records a tool-use observation.
// POST /api/ingest/tool-use
//
// Wire format (verified against backend IngestionController.java):
//   {"session_id":"...", "cwd":"/path", "tool_name":"Edit", "tool_input":{...}, "tool_response":{...}, ...}
type ObservationRequest struct {
	SessionID     string         `json:"session_id"`
	ProjectPath   string         `json:"cwd"`              // Wire: "cwd" (not "project_path")
	ToolName      string         `json:"tool_name"`        // Wire: "tool_name" (not "type")
	ToolInput     any            `json:"tool_input,omitempty"`
	ToolResponse  any            `json:"tool_response,omitempty"`
	PromptNumber  int            `json:"prompt_number,omitempty"`
	Source        string         `json:"source,omitempty"`         // V14: source attribution
	ExtractedData map[string]any `json:"extractedData,omitempty"` // V14: camelCase!
}

// ObservationUpdate updates an existing observation.
// PATCH /api/memory/observations/{id}
type ObservationUpdate struct {
	Title         *string        `json:"title,omitempty"`
	Content       *string        `json:"content,omitempty"`
	Facts         []string       `json:"facts,omitempty"`
	Concepts      []string       `json:"concepts,omitempty"`
	Source        *string        `json:"source,omitempty"`
	ExtractedData map[string]any `json:"extractedData,omitempty"`
}

// Observation is a single observation record returned from the backend.
type Observation struct {
	ID            string         `json:"id"`
	SessionID     string         `json:"sessionId"`
	ProjectPath   string         `json:"projectPath"`
	Type          string         `json:"type"`
	Title         string         `json:"title,omitempty"`
	Content       string         `json:"content"`
	Facts         []string       `json:"facts,omitempty"`
	Concepts      []string       `json:"concepts,omitempty"`
	QualityScore  float32        `json:"qualityScore,omitempty"`
	Source        string         `json:"source,omitempty"`
	ExtractedData map[string]any `json:"extractedData,omitempty"`
	CreatedAt     string         `json:"createdAt,omitempty"`
}
