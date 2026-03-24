package dto

// ExperienceRequest is used for retrieving experiences.
// POST /api/memory/experiences
type ExperienceRequest struct {
	ProjectPath    string   `json:"project_path"`
	Query          string   `json:"query,omitempty"`
	Concept        string   `json:"concept,omitempty"`
	Type           string   `json:"type,omitempty"`
	Source         string   `json:"source,omitempty"`
	RequiredConcepts []string `json:"required_concepts,omitempty"`
	Limit          int      `json:"limit,omitempty"`
	Offset         int      `json:"offset,omitempty"`
	SessionID      string   `json:"session_id,omitempty"`
	UserID         string   `json:"user_id,omitempty"`
}

// Experience represents a retrieved experience.
type Experience struct {
	SessionID      string   `json:"session_id"`
	Type           string   `json:"type"`
	Content        string   `json:"content"`
	Quality        string   `json:"quality,omitempty"`
	Source         string   `json:"source,omitempty"`
	CreatedAt      int64    `json:"created_at,omitempty"`
	Similarity     float64  `json:"similarity,omitempty"`
	Concepts       []string `json:"concepts,omitempty"`
	ObservationID  string   `json:"observation_id,omitempty"`
}

// ICLPromptRequest is used for building an ICL prompt.
// POST /api/memory/icl-prompt
type ICLPromptRequest struct {
	ProjectPath string   `json:"project_path"`
	Task        string   `json:"task"`
	MaxChars    int      `json:"max_chars,omitempty"`
	Types       []string `json:"types,omitempty"`
	Concepts    []string `json:"concepts,omitempty"`
	SessionID   string   `json:"session_id,omitempty"`
	UserID      string   `json:"user_id,omitempty"`
	IncludeInsights bool `json:"include_insights,omitempty"`
}

// ICLPromptResult contains the generated ICL prompt.
type ICLPromptResult struct {
	Prompt       string   `json:"prompt"`
	Observations []Experience `json:"observations"`
	TotalChars   int      `json:"total_chars"`
	Strategy     string   `json:"strategy"`
}
