package dto

// ExperienceRequest retrieves relevant experiences from memory.
// POST /api/memory/experiences
//
// Wire format (verified against backend MemoryController.java):
//   {"task":"...", "project":"/path", "count":4, "source":"...", "requiredConcepts":[...], "userId":"..."}
type ExperienceRequest struct {
	Task             string   `json:"task"`
	Project          string   `json:"project,omitempty"`        // Wire: "project" (not "project_path"); omitempty matches Java SDK behavior
	Count            int      `json:"count,omitempty"`
	Source           string   `json:"source,omitempty"`
	RequiredConcepts []string `json:"requiredConcepts,omitempty"` // Wire: camelCase!
	UserID           string   `json:"userId,omitempty"`           // Wire: camelCase!
}

// Experience represents a retrieved experience from the backend.
type Experience struct {
	ID              string  `json:"id"`
	Task            string  `json:"task"`
	Strategy        string  `json:"strategy"`
	Outcome         string  `json:"outcome"`
	ReuseCondition  string  `json:"reuseCondition"`
	QualityScore    float32 `json:"qualityScore"`
	CreatedAt       string  `json:"createdAt,omitempty"`
}

// ICLPromptRequest builds an ICL prompt from historical experiences.
// POST /api/memory/icl-prompt
//
// Wire format (verified against backend MemoryController.java):
//   {"task":"...", "project":"/path", "maxChars":4000, "userId":"..."}
type ICLPromptRequest struct {
	Task     string `json:"task"`
	Project  string `json:"project,omitempty"`     // Wire: "project" (not "project_path"); omitempty matches Java SDK behavior
	MaxChars int    `json:"maxChars,omitempty"`    // Wire: camelCase!
	UserID   string `json:"userId,omitempty"`      // Wire: camelCase!
}

// ICLPromptResult is the result from the ICL prompt builder.
type ICLPromptResult struct {
	Prompt          string `json:"prompt"`
	ExperienceCount string `json:"experienceCount"`
}
