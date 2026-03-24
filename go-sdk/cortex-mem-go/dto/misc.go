package dto

// QualityDistribution contains quality statistics for a project.
type QualityDistribution struct {
	Project string `json:"project"`
	High    int64  `json:"high"`
	Medium  int64  `json:"medium"`
	Low     int64  `json:"low"`
	Unknown int64  `json:"unknown"`
}

// Total returns the total number of observations.
func (q QualityDistribution) Total() int64 {
	return q.High + q.Medium + q.Low + q.Unknown
}

// FeedbackRequest submits feedback for an observation.
// POST /api/memory/feedback
//
// Wire format (verified against backend MemoryController.java):
//   {"observationId":"...", "feedbackType":"SUCCESS", "comment":"..."}
type FeedbackRequest struct {
	ObservationID string `json:"observationId"`  // Wire: camelCase!
	FeedbackType  string `json:"feedbackType"`   // Wire: camelCase!
	Comment       string `json:"comment,omitempty"`
}
