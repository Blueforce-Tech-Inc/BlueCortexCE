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

// ModesResponse contains active mode configuration.
// GET /api/modes
type ModesResponse struct {
	ID                  string   `json:"id"`
	Name                string   `json:"name"`
	Description         string   `json:"description"`
	Version             string   `json:"version"`
	ObservationTypes    []string `json:"observation_types"`
	ObservationConcepts []string `json:"observation_concepts"`
}

// SessionUserUpdateResponse contains the result of updating a session's userId.
// PATCH /api/session/{sessionId}/user
type SessionUserUpdateResponse struct {
	Status    string `json:"status"`
	SessionID string `json:"sessionId"`
	UserID    string `json:"userId"`
}
