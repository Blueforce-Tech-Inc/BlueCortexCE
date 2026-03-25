package dto

// SearchRequest performs semantic search over observations.
// GET /api/search?project=...&query=...&type=...&concept=...&source=...&limit=...&offset=...
//
// Wire format: all fields are passed as URL query parameters (not JSON body).
// The client constructs query params manually from these fields (no json tags needed).
// Verified against backend SearchController.java.
type SearchRequest struct {
	Project string // Required: project path for scoping
	Query   string // Optional: semantic search query
	Type    string // Optional: observation type filter
	Concept string // Optional: concept filter
	Source  string // Optional: source filter (V14)
	Limit   int    // Optional: max results to return (0 = backend default)
	Offset  int    // Optional: pagination offset
}

// SearchResult is the response from the search API.
type SearchResult struct {
	Observations []Observation `json:"observations"`
	Strategy     string        `json:"strategy"`
	FellBack     bool          `json:"fell_back"`
	Count        int           `json:"count"`
}
