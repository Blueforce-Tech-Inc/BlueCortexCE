// ============================================================
// Management DTOs
// ============================================================

/**
 * Quality distribution for a project.
 */
export interface QualityDistribution {
  project: string;
  high: number;
  medium: number;
  low: number;
  unknown: number;
}

/**
 * Request to submit feedback for an observation.
 * POST /api/memory/feedback
 *
 * Wire format: observationId and feedbackType are camelCase.
 */
export interface FeedbackRequest {
  observationId: string;
  feedbackType: string;
  comment?: string;
}
