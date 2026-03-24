package dto

// ObservationsRequest lists observations with pagination.
// GET /api/observations?project=...&offset=...&limit=...
type ObservationsRequest struct {
	Project string `json:"project,omitempty"`
	Offset  int    `json:"offset,omitempty"`
	Limit   int    `json:"limit,omitempty"`
}

// ObservationsResponse is the paginated response from listing observations.
type ObservationsResponse struct {
	Items   []Observation `json:"items"`
	HasMore bool          `json:"hasMore"`
	Total   int64         `json:"total,omitempty"`
	Offset  int           `json:"offset"`
	Limit   int           `json:"limit"`
}

// BatchObservationsRequest gets observations by IDs.
// POST /api/observations/batch
type BatchObservationsRequest struct {
	IDs []string `json:"ids"`
}
