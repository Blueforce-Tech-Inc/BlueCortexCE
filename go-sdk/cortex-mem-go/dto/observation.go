package dto

// ObservationRequest is sent when recording a tool-use observation.
// POST /api/ingest/tool-use
type ObservationRequest struct {
	ProjectPath  string                 `json:"project_path"`
	SessionID    string                 `json:"session_id"`
	Type         string                 `json:"type"`
	Content      string                 `json:"content"`
	Metadata     map[string]interface{} `json:"metadata,omitempty"`
	Source       string                 `json:"source,omitempty"`
	Quality      string                 `json:"quality,omitempty"`
	Concepts     []string               `json:"concepts,omitempty"`
	ParentID     string                 `json:"parent_id,omitempty"`
	ObservationID string               `json:"observation_id,omitempty"`
}
