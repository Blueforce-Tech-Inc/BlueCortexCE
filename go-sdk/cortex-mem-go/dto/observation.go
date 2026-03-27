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
//
// Wire format (verified against backend MemoryController.java):
//   {"title":"...", "subtitle":"...", "content":"...", "facts":[...], "concepts":[...], "source":"...", "extractedData":{...}}
//   Pointer fields (*string) use "omitempty" — nil values are omitted from JSON.
type ObservationUpdate struct {
	Title         *string        `json:"title,omitempty"`
	Subtitle      *string        `json:"subtitle,omitempty"`
	Content       *string        `json:"content,omitempty"`
	Facts         []string       `json:"facts,omitempty"`
	Concepts      []string       `json:"concepts,omitempty"`
	Source        *string        `json:"source,omitempty"`
	ExtractedData map[string]any `json:"extractedData,omitempty"`
}

// Observation is a single observation record returned from the backend.
//
// Wire format (verified against backend ObservationEntity.java + SNAKE_CASE naming strategy):
//   - content_session_id (@JsonProperty override), project (@JsonProperty override),
//     narrative (@JsonProperty override), extractedData (@JsonProperty override)
//   - quality_score, prompt_number, created_at, created_at_epoch (SNAKE_CASE strategy)
type Observation struct {
	ID              string         `json:"id"`
	SessionID       string         `json:"content_session_id"` // @JsonProperty("content_session_id") on entity
	ProjectPath     string         `json:"project"`             // @JsonProperty("project") on entity
	Type            string         `json:"type"`
	Title           string         `json:"title,omitempty"`
	Subtitle        string         `json:"subtitle,omitempty"`
	Content         string         `json:"narrative"` // @JsonProperty("narrative") on entity
	Facts           []string       `json:"facts,omitempty"`
	Concepts        []string       `json:"concepts,omitempty"`
	QualityScore    float32        `json:"quality_score,omitempty"`    // SNAKE_CASE naming strategy
	Source          string         `json:"source,omitempty"`
	ExtractedData   map[string]any `json:"extractedData,omitempty"`    // @JsonProperty("extractedData") on entity
	PromptNumber    int            `json:"prompt_number,omitempty"`    // SNAKE_CASE naming strategy
	CreatedAt       string         `json:"created_at,omitempty"`       // SNAKE_CASE naming strategy
	CreatedAtEpoch  int64          `json:"created_at_epoch,omitempty"` // SNAKE_CASE naming strategy
}
