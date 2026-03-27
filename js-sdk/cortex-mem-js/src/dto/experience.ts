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
 * All fields are camelCase.
 */
export interface Experience {
  id: string;
  task: string;
  strategy: string;
  outcome: string;
  reuseCondition: string;
  qualityScore: number;
  createdAt?: string;
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
