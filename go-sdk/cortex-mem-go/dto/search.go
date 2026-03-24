package dto

// SearchRequest performs semantic search over observations.
// GET /api/search?project=...&query=...&type=...&concept=...&source=...&limit=...&offset=...
type SearchRequest struct {
	Project string `json:"project"`
	Query   string `json:"query,omitempty"`
	Type    string `json:"type,omitempty"`
	Concept string `json:"concept,omitempty"`
	Source  string `json:"source,omitempty"`
	Limit   int    `json:"limit,omitempty"`
	Offset  int    `json:"offset,omitempty"`
}

// SearchResult is the response from the search API.
type SearchResult struct {
	Observations []Observation `json:"observations"`
	Strategy     string        `json:"strategy"`
	FellBack     bool          `json:"fell_back"`
	Count        int           `json:"count"`
}
