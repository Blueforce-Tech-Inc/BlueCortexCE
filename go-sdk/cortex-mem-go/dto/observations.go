package dto

// ObservationsRequest is used for listing observations with pagination.
// GET /api/observations
type ObservationsRequest struct {
	Project string `json:"project,omitempty" url:"project,omitempty"`
	Offset  int    `json:"offset,omitempty" url:"offset,omitempty"`
	Limit   int    `json:"limit,omitempty" url:"limit,omitempty"`
}

// PagedResponse is a generic paginated response.
type PagedResponse[T any] struct {
	Items    []T    `json:"items"`
	Total    int64  `json:"total"`
	Offset   int    `json:"offset"`
	Limit    int    `json:"limit"`
	HasMore  bool   `json:"has_more"`
}

// ObservationsResponse is the response from list observations.
type ObservationsResponse struct {
	Observations []Observation `json:"observations"`
	Total        int64         `json:"total"`
}

// Observation is a single observation record.
type Observation struct {
	ID           string                 `json:"id"`
	ProjectPath  string                 `json:"project_path"`
	SessionID    string                 `json:"session_id"`
	Type         string                 `json:"type"`
	Content      string                 `json:"content"`
	Metadata     map[string]interface{} `json:"metadata,omitempty"`
	Source       string                 `json:"source,omitempty"`
	Quality      string                 `json:"quality,omitempty"`
	Concepts     []string               `json:"concepts,omitempty"`
	CreatedAt    int64                  `json:"created_at,omitempty"`
	ParentID     string                 `json:"parent_id,omitempty"`
}

// ObservationUpdate is used to update an observation.
// PATCH /api/memory/observations/{id}
type ObservationUpdate struct {
	Source       *string `json:"source,omitempty"`
	Quality      *string `json:"quality,omitempty"`
	Content      *string `json:"content,omitempty"`
	Concepts     []string `json:"concepts,omitempty"`
}

// BatchObservationsRequest is used to get observations by IDs.
// POST /api/observations/batch
type BatchObservationsRequest struct {
	IDs []string `json:"ids"`
}
