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
	UpdateSessionUserId(ctx context.Context, sessionID, userID string) (map[string]any, error)

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

	// GetObservationsByIds retrieves observations by their IDs.
	GetObservationsByIds(ctx context.Context, ids []string) (*dto.BatchObservationsResponse, error)

	// ==================== Management ====================

	// TriggerRefinement triggers memory refinement.
	TriggerRefinement(ctx context.Context, projectPath string) error

	// SubmitFeedback submits feedback.
	SubmitFeedback(ctx context.Context, observationID, feedbackType, comment string) error

	// UpdateObservation updates an observation.
	UpdateObservation(ctx context.Context, observationID string, update dto.ObservationUpdate) error

	// DeleteObservation deletes an observation.
	DeleteObservation(ctx context.Context, observationID string) error

	// GetQualityDistribution gets quality distribution.
	GetQualityDistribution(ctx context.Context, projectPath string) (*dto.QualityDistribution, error)

	// ==================== Health ====================

	// HealthCheck checks backend health.
	HealthCheck(ctx context.Context) error

	// ==================== Extraction ====================

	// TriggerExtraction manually triggers extraction for a project. POST /api/extraction/run
	TriggerExtraction(ctx context.Context, projectPath string) error

	// GetLatestExtraction gets latest extraction result.
	GetLatestExtraction(ctx context.Context, projectPath, templateName, userID string) (map[string]any, error)

	// GetExtractionHistory gets extraction history.
	GetExtractionHistory(ctx context.Context, projectPath, templateName, userID string, limit int) ([]map[string]any, error)

	// ==================== Version ====================

	// GetVersion gets backend version info.
	GetVersion(ctx context.Context) (map[string]any, error)

	// ==================== P1 Management ====================

	// GetProjects gets all projects.
	GetProjects(ctx context.Context) (map[string]any, error)

	// GetStats gets project statistics.
	GetStats(ctx context.Context, projectPath string) (map[string]any, error)

	// GetModes gets memory mode settings.
	GetModes(ctx context.Context) (map[string]any, error)

	// GetSettings gets current settings.
	GetSettings(ctx context.Context) (map[string]any, error)

	// ==================== Lifecycle ====================

	// Close releases resources.
	Close() error
}
