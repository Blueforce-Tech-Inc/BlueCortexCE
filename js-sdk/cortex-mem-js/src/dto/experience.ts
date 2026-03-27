// ============================================================
// Experience & ICL DTOs
// ============================================================

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
 * Field names match the wire format directly.
 */
export interface Experience {
  id: string;
  task: string;
  strategy: string;
  outcome: string;
  reuse_condition: string;
  quality_score: number;
  created_at?: string;
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
