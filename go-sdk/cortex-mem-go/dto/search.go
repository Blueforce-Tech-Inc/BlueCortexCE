package dto

// SearchRequest is used for semantic search.
// GET /api/search
type SearchRequest struct {
	Project string `json:"project" url:"project"`
	Query  string `json:"query,omitempty" url:"query,omitempty"`
	Type   string `json:"type,omitempty" url:"type,omitempty"`
	Concept string `json:"concept,omitempty" url:"concept,omitempty"`
	Source  string `json:"source,omitempty" url:"source,omitempty"`
	Limit   int    `json:"limit,omitempty" url:"limit,omitempty"`
	Offset   int    `json:"offset,omitempty" url:"offset,omitempty"`
}

// SearchResult contains the search response.
type SearchResult struct {
	Observations []Observation `json:"observations"`
	Strategy     string        `json:"strategy"`
	FellBack     bool          `json:"fell_back"`
	Count        int           `json:"count"`
}
