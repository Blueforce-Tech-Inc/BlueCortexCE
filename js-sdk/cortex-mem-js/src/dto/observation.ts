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
 * Raw wire format from backend.
 * Backend uses Jackson SNAKE_CASE naming strategy with @JsonProperty overrides.
 */
interface ObservationWire {
  id: string;
  content_session_id: string;
  project: string;
  type: string;
  title?: string;
  subtitle?: string;
  narrative: string;
  facts?: string[];
  concepts?: string[];
  quality_score?: number;
  source?: string;
  extractedData?: Record<string, unknown>;
  prompt_number?: number;
  created_at?: string;
  created_at_epoch?: number;
}

/**
 * Parse a raw wire-format observation into the canonical Observation type.
 * Matches Go's dto.Observation.UnmarshalJSON and Python's Observation.from_wire.
 */
export function parseObservation(raw: Record<string, unknown>): Observation {
  return {
    id: (raw.id as string) ?? '',
    sessionId: (raw.content_session_id as string) ?? '',
    projectPath: (raw.project as string) ?? '',
    type: (raw.type as string) ?? '',
    title: raw.title as string | undefined,
    subtitle: raw.subtitle as string | undefined,
    content: (raw.narrative as string) ?? '',
    facts: raw.facts as string[] | undefined,
    concepts: raw.concepts as string[] | undefined,
    qualityScore: raw.quality_score as number | undefined,
    source: raw.source as string | undefined,
    extractedData: raw.extractedData as Record<string, unknown> | undefined,
    promptNumber: raw.prompt_number as number | undefined,
    createdAt: raw.created_at as string | undefined,
    createdAtEpoch: raw.created_at_epoch as number | undefined,
  };
}
