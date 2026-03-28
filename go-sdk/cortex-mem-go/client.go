package cortexmem

import (
	"context"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

// Client is the unified interface for the Cortex CE memory system.
type Client interface {
	// ==================== Session ====================

	// StartSession starts or resumes a session.
	StartSession(ctx context.Context, req dto.SessionStartRequest) (*dto.SessionStartResponse, error)

	// UpdateSessionUserId updates session userId.
	UpdateSessionUserId(ctx context.Context, sessionID, userID string) (*dto.SessionUserUpdateResponse, error)

	// ==================== Capture ====================

	// RecordObservation records a tool-use observation (fire-and-forget).
	RecordObservation(ctx context.Context, req dto.ObservationRequest) error

	// RecordSessionEnd signals session end.
	RecordSessionEnd(ctx context.Context, req dto.SessionEndRequest) error

	// RecordUserPrompt records a user prompt.
	RecordUserPrompt(ctx context.Context, req dto.UserPromptRequest) error

	// ==================== Retrieval ====================

	// RetrieveExperiences retrieves relevant experiences.
	RetrieveExperiences(ctx context.Context, req dto.ExperienceRequest) ([]dto.Experience, error)

	// BuildICLPrompt builds an ICL prompt.
	BuildICLPrompt(ctx context.Context, req dto.ICLPromptRequest) (*dto.ICLPromptResult, error)

	// Search performs semantic search.
	Search(ctx context.Context, req dto.SearchRequest) (*dto.SearchResult, error)

	// ListObservations lists observations with pagination.
	ListObservations(ctx context.Context, req dto.ObservationsRequest) (*dto.ObservationsResponse, error)

	// GetObservation retrieves a single observation by ID.
	// Returns nil and no error if the observation does not exist.
	GetObservation(ctx context.Context, id string) (*dto.Observation, error)

	// GetObservationsByIds retrieves observations by their IDs.
	// Max 100 IDs per call.
	GetObservationsByIds(ctx context.Context, ids []string) (*dto.BatchObservationsResponse, error)

	// ==================== Management ====================

	// TriggerRefinement triggers memory refinement.
	// projectPath is required.
	TriggerRefinement(ctx context.Context, projectPath string) error

	// SubmitFeedback submits feedback.
	// observationID and feedbackType are required.
	SubmitFeedback(ctx context.Context, observationID, feedbackType, comment string) error

	// UpdateObservation updates an observation.
	// observationID is required.
	UpdateObservation(ctx context.Context, observationID string, update dto.ObservationUpdate) error

	// DeleteObservation deletes an observation.
	// observationID is required.
	DeleteObservation(ctx context.Context, observationID string) error

	// GetQualityDistribution gets quality distribution.
	GetQualityDistribution(ctx context.Context, projectPath string) (*dto.QualityDistribution, error)

	// ==================== Health ====================

	// HealthCheck checks backend health.
	HealthCheck(ctx context.Context) error

	// ==================== Extraction ====================

	// TriggerExtraction manually triggers extraction for a project. POST /api/extraction/run
	// projectPath is required.
	TriggerExtraction(ctx context.Context, projectPath string) error

	// GetLatestExtraction gets latest extraction result.
	// projectPath and templateName are required.
	GetLatestExtraction(ctx context.Context, projectPath, templateName, userID string) (*dto.ExtractionResult, error)

	// GetExtractionHistory gets extraction history.
	// projectPath and templateName are required. limit <= 0 means use backend default (10).
	GetExtractionHistory(ctx context.Context, projectPath, templateName, userID string, limit int) ([]dto.ExtractionResult, error)

	// ==================== Version ====================

	// GetVersion gets backend version info.
	GetVersion(ctx context.Context) (*dto.VersionResponse, error)

	// ==================== P1 Management ====================

	// GetProjects gets all projects.
	GetProjects(ctx context.Context) (*dto.ProjectsResponse, error)

	// GetStats gets project statistics.
	GetStats(ctx context.Context, projectPath string) (*dto.StatsResponse, error)

	// GetModes gets memory mode settings.
	GetModes(ctx context.Context) (*dto.ModesResponse, error)

	// GetSettings gets current settings. Returns the raw map since settings are dynamic.
	GetSettings(ctx context.Context) (map[string]any, error)

	// ==================== Lifecycle ====================

	// Close releases resources.
	Close() error

	// String returns a debug representation of the client.
	String() string
}
