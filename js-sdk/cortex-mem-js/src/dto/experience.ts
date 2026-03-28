// ============================================================
// Experience & ICL DTOs
// ============================================================

import { safeStringOr, safeNumberOr, safeString } from './wire-helpers';

/**
 * Request to retrieve relevant experiences.
 * POST /api/memory/experiences
 *
 * Wire format: requiredConcepts and userId are camelCase.
 */
export interface ExperienceRequest {
  task: string;
  project?: string;
  count?: number;
  source?: string;
  requiredConcepts?: string[];
  userId?: string;
}

/**
 * A retrieved experience from the backend.
 *
 * Wire format uses SNAKE_CASE (backend Jackson naming strategy).
 * Field names are normalized to camelCase via parseExperience.
 */
export interface Experience {
  id: string;
  task: string;
  strategy: string;
  outcome: string;
  /** Parsed from wire field "reuse_condition" */
  reuseCondition: string;
  /** Parsed from wire field "quality_score" */
  qualityScore: number;
  /** Parsed from wire field "created_at" */
  createdAt?: string;
}

/**
 * Parse a raw wire-format experience into the canonical Experience type.
 * Uses safe type conversion to handle null values and type mismatches gracefully.
 */
export function parseExperience(raw: Record<string, unknown>): Experience {
  return {
    id: safeStringOr(raw.id, ''),
    task: safeStringOr(raw.task, ''),
    strategy: safeStringOr(raw.strategy, ''),
    outcome: safeStringOr(raw.outcome, ''),
    reuseCondition: safeStringOr(raw.reuse_condition, ''),
    qualityScore: safeNumberOr(raw.quality_score, 0),
    createdAt: safeString(raw.created_at),
  };
}

/**
 * Request to build an ICL prompt.
 * POST /api/memory/icl-prompt
 *
 * Wire format: maxChars and userId are camelCase.
 */
export interface ICLPromptRequest {
  task: string;
  project?: string;
  maxChars?: number;
  userId?: string;
}

/**
 * Result from the ICL prompt builder.
 */
export interface ICLPromptResult {
  prompt: string;
  experienceCount: number;
  maxChars?: number;
}
