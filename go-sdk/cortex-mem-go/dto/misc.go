package dto

// QualityDistribution contains quality statistics.
type QualityDistribution struct {
	ProjectPath string `json:"project_path"`
	Total       int64  `json:"total"`
	High        int64  `json:"high"`
	Medium      int64  `json:"medium"`
	Low         int64  `json:"low"`
	Unknown     int64  `json:"unknown"`
}

// UserPromptRequest is sent when recording a user prompt.
// POST /api/ingest/user-prompt
type UserPromptRequest struct {
	ProjectPath string `json:"project_path"`
	SessionID   string `json:"session_id"`
	Content     string `json:"content"`
	Timestamp   int64  `json:"timestamp,omitempty"`
}

// SessionEndRequest is sent when a session ends.
// POST /api/ingest/session-end
type SessionEndRequest struct {
	ProjectPath string `json:"project_path"`
	SessionID   string `json:"session_id"`
	Reason      string `json:"reason,omitempty"`
	Timestamp   int64  `json:"timestamp,omitempty"`
}

// RefineRequest is sent when triggering refinement.
// POST /api/memory/refine
type RefineRequest struct {
	ProjectPath string `json:"project_path"`
	SessionID   string `json:"session_id,omitempty"`
}

// FeedbackRequest is sent when submitting feedback.
// POST /api/memory/feedback
type FeedbackRequest struct {
	ObservationID string `json:"observation_id"`
	FeedbackType  string `json:"feedback_type"`
	Comment       string `json:"comment,omitempty"`
}
