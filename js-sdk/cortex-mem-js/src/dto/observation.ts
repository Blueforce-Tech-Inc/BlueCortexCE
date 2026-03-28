// ============================================================
// Wire format safe type conversion helpers
// ============================================================

/** Safely convert unknown value to string. Returns undefined for null/undefined/non-string. */
function safeString(v: unknown): string | undefined {
  if (v === null || v === undefined) return undefined;
  if (typeof v === 'string') return v;
  return String(v);
}

/** Safely convert unknown value to string with default. */
function safeStringOr(v: unknown, fallback: string): string {
  return safeString(v) ?? fallback;
}

/** Safely convert unknown value to number. Returns undefined for null/undefined/non-number. */
function safeNumber(v: unknown): number | undefined {
  if (v === null || v === undefined) return undefined;
  if (typeof v === 'number' && !Number.isNaN(v)) return v;
  if (typeof v === 'string') {
    const n = Number(v);
    if (!Number.isNaN(n)) return n;
  }
  return undefined;
}

/** Safely convert unknown value to string array. Returns undefined for null/undefined. */
function safeStringArray(v: unknown): string[] | undefined {
  if (v === null || v === undefined) return undefined;
  if (!Array.isArray(v)) return undefined;
  const result: string[] = [];
  for (const item of v) {
    if (typeof item === 'string') result.push(item);
    else if (item !== null && item !== undefined) result.push(String(item));
  }
  return result;
}

// ============================================================
// Observation DTOs
// ============================================================

/**
 * Request to record a tool-use observation.
 * POST /api/ingest/tool-use
 *
 * Wire format: {"session_id":"...", "cwd":"/path", "tool_name":"Edit", ...}
 * Note: uses "cwd" (NOT "project_path"). extractedData is camelCase.
 */
export interface ObservationRequest {
  session_id: string;
  cwd: string;
  tool_name: string;
  tool_input?: unknown;
  tool_response?: unknown;
  prompt_number?: number;
  source?: string;
  extractedData?: Record<string, unknown>;
}

/**
 * Request to update an existing observation.
 * PATCH /api/memory/observations/{id}
 *
 * Wire format: extractedData is camelCase.
 * Both "content" and "narrative" are accepted by the backend for the narrative field.
 */
export interface ObservationUpdate {
  title?: string;
  subtitle?: string;
  content?: string;
  /** Alias for content — backend accepts both "content" and "narrative" */
  narrative?: string;
  facts?: string[];
  concepts?: string[];
  source?: string;
  extractedData?: Record<string, unknown>;
}

/**
 * A single observation record returned from the backend.
 *
 * Field names are the parsed/canonical form (NOT raw wire format).
 * Use {@link parseObservation} to convert from wire format.
 */
export interface Observation {
  id: string;
  /** Parsed from wire field "content_session_id" */
  sessionId: string;
  /** Parsed from wire field "project" */
  projectPath: string;
  type: string;
  title?: string;
  subtitle?: string;
  /** Parsed from wire field "narrative" */
  content: string;
  facts?: string[];
  concepts?: string[];
  /** Parsed from wire field "quality_score" (SNAKE_CASE) */
  qualityScore?: number;
  source?: string;
  /** Parsed from wire field "extractedData" (camelCase — @JsonProperty override) */
  extractedData?: Record<string, unknown>;
  /** Parsed from wire field "prompt_number" (SNAKE_CASE) */
  promptNumber?: number;
  /** Parsed from wire field "created_at" (SNAKE_CASE) */
  createdAt?: string;
  /** Parsed from wire field "created_at_epoch" (SNAKE_CASE) */
  createdAtEpoch?: number;
}

/**
 * Parse a raw wire-format observation into the canonical Observation type.
 * Matches Go's dto.Observation.UnmarshalJSON and Python's Observation.from_wire.
 * Uses safe type conversion to handle null values and type mismatches gracefully.
 */
export function parseObservation(raw: Record<string, unknown>): Observation {
  return {
    id: safeStringOr(raw.id, ''),
    sessionId: safeStringOr(raw.content_session_id, ''),
    projectPath: safeStringOr(raw.project, ''),
    type: safeStringOr(raw.type, ''),
    title: safeString(raw.title),
    subtitle: safeString(raw.subtitle),
    content: safeStringOr(raw.narrative, ''),
    facts: safeStringArray(raw.facts),
    concepts: safeStringArray(raw.concepts),
    qualityScore: safeNumber(raw.quality_score),
    source: safeString(raw.source),
    extractedData: (raw.extractedData !== null && raw.extractedData !== undefined)
      ? raw.extractedData as Record<string, unknown>
      : undefined,
    promptNumber: safeNumber(raw.prompt_number),
    createdAt: safeString(raw.created_at),
    createdAtEpoch: safeNumber(raw.created_at_epoch),
  };
}
