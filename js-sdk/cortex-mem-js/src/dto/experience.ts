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
  // Safe conversion helpers (local to avoid import overhead)
  const safeStr = (v: unknown, fallback: string): string => {
    if (v === null || v === undefined) return fallback;
    if (typeof v === 'string') return v;
    return String(v);
  };
  const safeNum = (v: unknown, fallback: number): number => {
    if (v === null || v === undefined) return fallback;
    if (typeof v === 'number' && !Number.isNaN(v)) return v;
    if (typeof v === 'string') {
      const n = Number(v);
      if (!Number.isNaN(n)) return n;
    }
    return fallback;
  };
  const safeOptStr = (v: unknown): string | undefined => {
    if (v === null || v === undefined) return undefined;
    if (typeof v === 'string') return v;
    return String(v);
  };

  return {
    id: safeStr(raw.id, ''),
    task: safeStr(raw.task, ''),
    strategy: safeStr(raw.strategy, ''),
    outcome: safeStr(raw.outcome, ''),
    reuseCondition: safeStr(raw.reuse_condition, ''),
    qualityScore: safeNum(raw.quality_score, 0),
    createdAt: safeOptStr(raw.created_at),
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
