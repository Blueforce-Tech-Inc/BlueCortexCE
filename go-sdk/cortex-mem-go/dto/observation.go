package dto

import (
	"encoding/json"
	"errors"
	"fmt"
)

// StringList is a []string that can unmarshal from both:
//   - JSON array: ["a", "b"]
//   - JSON string-encoded array: "[\"a\", \"b\"]" (backend serializes JSONB fields this way for WebUI)
//
// Marshal always produces a JSON array.
type StringList []string

func (sl *StringList) UnmarshalJSON(data []byte) error {
	// Try JSON array first
	var arr []string
	if err := json.Unmarshal(data, &arr); err == nil {
		*sl = arr
		return nil
	}
	// Try JSON string (backend serializes JSONB as string for WebUI JSON.parse())
	var str string
	if err := json.Unmarshal(data, &str); err != nil {
		return fmt.Errorf("StringList: cannot unmarshal %s", string(data))
	}
	if str == "" || str == "[]" {
		*sl = nil
		return nil
	}
	// The string is itself a JSON array
	if err := json.Unmarshal([]byte(str), &arr); err != nil {
		return fmt.Errorf("StringList: cannot parse string-encoded JSON: %w", err)
	}
	*sl = arr
	return nil
}

// ObservationRequest records a tool-use observation.
// POST /api/ingest/tool-use
//
// Wire format (verified against backend IngestionController.java):
//
//	{"session_id":"...", "cwd":"/path", "tool_name":"Edit", "tool_input":{...}, "tool_response":{...}, ...}
type ObservationRequest struct {
	SessionID     string         `json:"session_id"`
	ProjectPath   string         `json:"cwd"`       // Wire: "cwd" (not "project_path")
	ToolName      string         `json:"tool_name"` // Wire: "tool_name" (not "type") — required, no omitempty
	ToolInput     any            `json:"tool_input,omitempty"`
	ToolResponse  any            `json:"tool_response,omitempty"`
	PromptNumber  int            `json:"prompt_number,omitempty"`
	Source        string         `json:"source,omitempty"`        // V14: source attribution
	ExtractedData map[string]any `json:"extractedData,omitempty"` // V14: camelCase! (not extracted_data)
}

// ObservationUpdate updates an existing observation.
// PATCH /api/memory/observations/{id}
//
// Wire format (verified against backend MemoryController.java):
//
//	{"title":"...", "subtitle":"...", "content":"...", "narrative":"...", "facts":[...], "concepts":[...], "source":"...", "extractedData":{...}}
//	Pointer fields (*string) use "omitempty" — nil values are omitted from JSON.
//
// ⚠️ CONFLICT: "content" and "narrative" are aliases for the same backend field.
// Setting both will cause the backend to silently use "content" and ignore "narrative",
// resulting in unexpected behavior. Use HasConflict() to detect this before sending.
// (This mirrors the fix applied to Java SDK's Builder.build() — see Java SDK PR #25.)
type ObservationUpdate struct {
	Title         *string        `json:"title,omitempty"`
	Subtitle      *string        `json:"subtitle,omitempty"`
	Content       *string        `json:"content,omitempty"`
	Narrative     *string        `json:"narrative,omitempty"` // ⚠️ Alias for content — see HasConflict()
	Facts         []string       `json:"facts,omitempty"`
	Concepts      []string       `json:"concepts,omitempty"`
	Source        *string        `json:"source,omitempty"`
	ExtractedData map[string]any `json:"extractedData,omitempty"`
}

// IsEmpty returns true if no fields are set (all pointer/slice/map fields are nil).
// Use this to validate PATCH requests before sending to the backend.
func (u ObservationUpdate) IsEmpty() bool {
	return u.Title == nil && u.Subtitle == nil && u.Content == nil &&
		u.Narrative == nil && u.Facts == nil && u.Concepts == nil &&
		u.Source == nil && u.ExtractedData == nil
}

// HasConflict returns true if both Content and Narrative are set.
// The backend treats these as aliases — setting both causes silent data loss
// (backend uses "content" and ignores "narrative"). Call this before sending
// an update to detect the conflict.
func (u ObservationUpdate) HasConflict() bool {
	return u.Content != nil && u.Narrative != nil
}

// ValidationError represents a client-side validation failure in ObservationUpdate.
type ObservationUpdateValidationError struct {
	Field   string
	Message string
}

func (e *ObservationUpdateValidationError) Error() string {
	return "cortex-ce: ObservationUpdate validation error: " + e.Message
}

// IsObservationUpdateValidationError returns true if the error is a validation error
// specific to ObservationUpdate (content/narrative conflict or empty update).
// Use errors.As(err, &ObservationUpdateValidationError{}) for detailed field info.
func IsObservationUpdateValidationError(err error) bool {
	var e *ObservationUpdateValidationError
	return errors.As(err, &e)
}

// Validate returns an error if the update is invalid.
// Checks for:
//   - Empty update (IsEmpty)
//   - Content/Narrative conflict (HasConflict)
func (u ObservationUpdate) Validate() error {
	if u.IsEmpty() {
		return &ObservationUpdateValidationError{
			Field:   "update",
			Message: "at least one field must be provided for update",
		}
	}
	if u.HasConflict() {
		return &ObservationUpdateValidationError{
			Field:   "content|narrative",
			Message: "both Content and Narrative are set — they are aliases for the same backend field; the backend silently uses 'content' and ignores 'narrative'. Use only Content or only Narrative.",
		}
	}
	return nil
}

// Observation is a single observation record returned from the backend.
//
// Wire format (verified against backend ObservationEntity.java + SNAKE_CASE naming strategy):
//   - content_session_id (@JsonProperty override), project (@JsonProperty override),
//     narrative (@JsonProperty override), extractedData (@JsonProperty override)
//   - quality_score, prompt_number, created_at, created_at_epoch (SNAKE_CASE strategy)
type Observation struct {
	ID                string         `json:"id"`
	SessionID         string         `json:"content_session_id"` // @JsonProperty("content_session_id") on entity
	ProjectPath       string         `json:"project"`            // @JsonProperty("project") on entity
	Type              string         `json:"type"`
	Title             string         `json:"title,omitempty"`
	Subtitle          string         `json:"subtitle,omitempty"`
	Content           string         `json:"narrative"` // @JsonProperty("narrative") on entity
	Facts             StringList     `json:"facts,omitempty"`         // Backend serializes JSONB as string for WebUI
	Concepts          StringList     `json:"concepts,omitempty"`      // Backend serializes JSONB as string for WebUI
	FilesRead         StringList     `json:"files_read,omitempty"`    // Backend serializes JSONB as string for WebUI
	FilesModified     StringList     `json:"files_modified,omitempty"` // Backend serializes JSONB as string for WebUI
	QualityScore      float32        `json:"quality_score,omitempty"`       // SNAKE_CASE naming strategy
	FeedbackType      string         `json:"feedback_type,omitempty"`       // SUCCESS/PARTIAL/FAILURE/UNKNOWN
	FeedbackUpdatedAt string         `json:"feedback_updated_at,omitempty"` // SNAKE_CASE naming strategy
	Source            string         `json:"source,omitempty"`
	ExtractedData     map[string]any `json:"extractedData,omitempty"`    // @JsonProperty("extractedData") on entity
	PromptNumber      int            `json:"prompt_number,omitempty"`    // SNAKE_CASE naming strategy
	CreatedAt         string         `json:"created_at,omitempty"`       // SNAKE_CASE naming strategy
	CreatedAtEpoch    int64          `json:"created_at_epoch,omitempty"` // SNAKE_CASE naming strategy
	LastAccessedAt    string         `json:"last_accessed_at,omitempty"` // SNAKE_CASE naming strategy
	AccessCount       int            `json:"access_count,omitempty"`            // SNAKE_CASE naming strategy
	RefinedAt         string         `json:"refined_at,omitempty"`              // SNAKE_CASE naming strategy
	RefinedFromIds    StringList     `json:"refined_from_ids,omitempty"`        // SNAKE_CASE naming strategy
	UserComment       string         `json:"user_comment,omitempty"`            // SNAKE_CASE naming strategy
}
