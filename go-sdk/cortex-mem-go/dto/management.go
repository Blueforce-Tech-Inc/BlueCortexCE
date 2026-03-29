package dto

// VersionResponse contains backend version information.
// GET /api/version
type VersionResponse struct {
	Version    string `json:"version"`
	Service    string `json:"service"`
	Java       string `json:"java"`
	SpringBoot string `json:"springBoot"`
}

// ProjectsResponse lists all known projects.
// GET /api/projects
type ProjectsResponse struct {
	Projects []string `json:"projects"`
}

// StatsResponse contains worker and database statistics.
// GET /api/stats
type StatsResponse struct {
	Worker   WorkerStats   `json:"worker"`
	Database DatabaseStats `json:"database"`
}

// WorkerStats contains processing state information.
type WorkerStats struct {
	IsProcessing bool `json:"isProcessing"`
	QueueDepth   int  `json:"queueDepth"`
}

// DatabaseStats contains database entity counts.
type DatabaseStats struct {
	TotalObservations int64 `json:"totalObservations"`
	TotalSummaries    int64 `json:"totalSummaries"`
	TotalSessions     int64 `json:"totalSessions"`
	TotalProjects     int64 `json:"totalProjects"`
}

// ObservationType describes a type of observation (e.g., bugfix, feature).
type ObservationType struct {
	ID          string `json:"id"`
	Label       string `json:"label"`
	Description string `json:"description"`
	Emoji       string `json:"emoji,omitempty"`
	WorkEmoji   string `json:"work_emoji,omitempty"`
}

// ObservationConcept describes a concept for observations (e.g., how-it-works, pattern).
type ObservationConcept struct {
	ID          string `json:"id"`
	Label       string `json:"label"`
	Description string `json:"description"`
}

// ModesResponse contains active mode configuration.
// GET /api/modes
type ModesResponse struct {
	ID                  string               `json:"id"`
	Name                string               `json:"name"`
	Description         string               `json:"description"`
	Version             string               `json:"version"`
	ObservationTypes    []ObservationType    `json:"observation_types"`
	ObservationConcepts []ObservationConcept `json:"observation_concepts"`
}

// SessionUserUpdateResponse contains the result of updating a session's userId.
// PATCH /api/session/{sessionId}/user
type SessionUserUpdateResponse struct {
	Status    string `json:"status"`
	SessionID string `json:"sessionId"`
	UserID    string `json:"userId"`
}
