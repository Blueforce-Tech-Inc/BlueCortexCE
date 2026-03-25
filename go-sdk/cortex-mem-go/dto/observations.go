package dto

// ObservationsRequest lists observations with pagination.
// GET /api/observations?project=...&offset=...&limit=...
//
// Wire format: all fields are passed as URL query parameters (not JSON body).
// The json tags are for documentation only — the client builds query params manually.
// Verified against backend ObservationController.java.
type ObservationsRequest struct {
	Project string // Optional: project path filter
	Offset  int    // Optional: pagination offset (0 = start)
	Limit   int    // Optional: max results per page (0 = backend default)
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
//
// Wire format (verified against backend ObservationController.java):
//   {"ids":["id1", "id2", ...]}
type BatchObservationsRequest struct {
	IDs []string `json:"ids"`
}

// BatchObservationsResponse is the response from batch observation retrieval.
//
// Wire format (verified against backend ObservationController.java):
//   {"observations":[...], "count":0}
type BatchObservationsResponse struct {
	Observations []Observation `json:"observations"`
	Count        int           `json:"count,omitempty"`
}
