package dto

// ExtractionResult contains a single extraction result.
// Used by both GET /api/extraction/{template}/latest and GET /api/extraction/{template}/history.
type ExtractionResult struct {
	Status        string         `json:"status,omitempty"`   // "ok" or "not_found" (latest only)
	Template      string         `json:"template,omitempty"` // template name (latest only)
	Message       string         `json:"message,omitempty"`  // error/not-found message (latest only)
	SessionID     string         `json:"sessionId"`
	ExtractedData map[string]any `json:"extractedData"`
	CreatedAt     int64          `json:"createdAt"` // epoch millis
	ObservationID string         `json:"observationId"`
}
