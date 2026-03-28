// ============================================================
// Management DTOs
// ============================================================

import { safeStringOr, safeNumberOr } from './wire-helpers';

/**
 * Quality distribution for a project.
 *
 * Use {@link parseQualityDistribution} to safely parse from wire format.
 */
export interface QualityDistribution {
  project: string;
  high: number;
  medium: number;
  low: number;
  unknown: number;
}

/**
 * Parse a raw wire-format quality distribution into the canonical type.
 * Handles null values and type mismatches gracefully.
 */
export function parseQualityDistribution(raw: Record<string, unknown>): QualityDistribution {
  return {
    project: safeStringOr(raw.project, ''),
    high: safeNumberOr(raw.high, 0),
    medium: safeNumberOr(raw.medium, 0),
    low: safeNumberOr(raw.low, 0),
    unknown: safeNumberOr(raw.unknown, 0),
  };
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
