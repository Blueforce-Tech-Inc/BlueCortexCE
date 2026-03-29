package dto

// ObservationsRequest lists observations with pagination.
// GET /api/observations?project=...&offset=...&limit=...
//
// Wire format: all fields are passed as URL query parameters (not JSON body).
// The client constructs query params manually from these fields (no json tags needed).
// Verified against backend ObservationController.java.
type ObservationsRequest struct {
	Project string // Optional: project path for scoping (omit for all projects)
	Offset  int    // Optional: pagination offset (0 = start)
	Limit   int    // Optional: max results per page (0 = backend default)
}

// ObservationsResponse is the paginated response from listing observations.
// GET /api/observations?project=...&offset=...&limit=...
//
// Backend PagedResponse record returns only "items" and "hasMore" (camelCase).
// The Total, Offset, and Limit fields are NOT populated by the API — they
// remain zero after deserialization. They exist only for local construction
// convenience in tests or wrapper implementations.
//
// Wire format (verified against backend ViewerController.PagedResponse):
//
//	{"items":[{...}, ...], "hasMore": true}
type ObservationsResponse struct {
	Items   []Observation `json:"items"`
	HasMore bool          `json:"hasMore"` // ⚠️ WebUI compat: backend uses "hasMore" (camelCase)
	Total   int64         `json:"total,omitempty"`  // Not returned by backend; for local use only
	Offset  int           `json:"offset,omitempty"` // Not returned by backend; for local use only
	Limit   int           `json:"limit,omitempty"`  // Not returned by backend; for local use only
}

// BatchObservationsRequest gets observations by IDs.
// POST /api/observations/batch
//
// Wire format (verified against backend ObservationController.java):
//
//	{"ids":["id1", "id2", ...]}
type BatchObservationsRequest struct {
	IDs []string `json:"ids"`
}

// BatchObservationsResponse is the response from batch observation retrieval.
//
// Wire format (verified against backend ObservationController.java):
//
//	{"observations":[...], "count":0}
type BatchObservationsResponse struct {
	Observations []Observation `json:"observations"`
	Count        int           `json:"count"`
}
