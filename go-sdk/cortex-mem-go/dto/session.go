package dto

// SessionStartRequest starts or resumes a session.
// POST /api/session/start
//
// Wire format (verified against backend SessionController.java):
//
//	{"session_id":"...", "project_path":"/path", "user_id":"..."}
type SessionStartRequest struct {
	SessionID   string `json:"session_id"`
	ProjectPath string `json:"project_path"` // Wire: "project_path" (NOT "cwd" for session start!)
	UserID      string `json:"user_id,omitempty"`
}

// SessionStartResponse is the response from starting a session.
type SessionStartResponse struct {
	SessionDBID  string `json:"session_db_id"`
	SessionID    string `json:"session_id"`
	Context      string `json:"context,omitempty"`
	PromptNumber int    `json:"prompt_number"`
}

// SessionEndRequest signals session end.
// POST /api/ingest/session-end
//
// Wire format (verified against backend IngestionController.java):
//
//	{"session_id":"...", "cwd":"/path", "last_assistant_message":"..."}
type SessionEndRequest struct {
	SessionID            string `json:"session_id"`
	ProjectPath          string `json:"cwd"` // Wire: "cwd"!
	LastAssistantMessage string `json:"last_assistant_message,omitempty"`
}

// UserPromptRequest records a user prompt.
// POST /api/ingest/user-prompt
//
// Wire format (verified against backend IngestionController.java):
//
//	{"session_id":"...", "prompt_text":"...", "prompt_number":1, "cwd":"/path"}
type UserPromptRequest struct {
	SessionID    string `json:"session_id"`
	PromptText   string `json:"prompt_text"`
	ProjectPath  string `json:"cwd"` // Wire: "cwd"!
	PromptNumber int    `json:"prompt_number,omitempty"`
}
