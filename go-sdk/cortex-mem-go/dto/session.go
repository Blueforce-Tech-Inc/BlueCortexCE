package dto

// SessionStartRequest is sent when starting a new session.
// POST /api/session/start
type SessionStartRequest struct {
	ProjectPath string `json:"project_path"`
	SessionID   string `json:"session_id,omitempty"`
	UserID      string `json:"user_id,omitempty"`
	Timestamp   int64  `json:"timestamp,omitempty"`
}

// SessionStartResponse contains the started session info.
type SessionStartResponse struct {
	SessionID   string `json:"session_id"`
	ProjectPath string `json:"project_path"`
	UserID      string `json:"user_id,omitempty"`
	CreatedAt   int64  `json:"created_at,omitempty"`
}
