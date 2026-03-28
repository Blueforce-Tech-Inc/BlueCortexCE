// ============================================================
// Observation DTOs
// ============================================================

import { safeString, safeStringOr, safeNumber, safeStringArray, safeRecord } from './wire-helpers';

/**
 * Check multiple key variants, return first non-null value.
 * Handles backend SNAKE_CASE output with camelCase fallback.
 */
function firstNonNullOr(raw: Record<string, unknown>, keys: string[]): unknown {
  for (const key of keys) {
    const val = raw[key];
    if (val !== null && val !== undefined) return val;
  }
  return undefined;
}

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
  /** Parsed from wire field "files_read" (SNAKE_CASE) */
  filesRead?: string[];
  /** Parsed from wire field "files_modified" (SNAKE_CASE) */
  filesModified?: string[];
  /** Parsed from wire field "quality_score" (SNAKE_CASE) */
  qualityScore?: number;
  /** Parsed from wire field "feedback_type" (SNAKE_CASE) — SUCCESS/PARTIAL/FAILURE/UNKNOWN */
  feedbackType?: string;
  /** Parsed from wire field "feedback_updated_at" (SNAKE_CASE) */
  feedbackUpdatedAt?: string;
  source?: string;
  /** Parsed from wire field "extractedData" (camelCase — @JsonProperty override).
   *  Always present (empty object when missing/invalid). */
  extractedData: Record<string, unknown>;
  /** Parsed from wire field "prompt_number" (SNAKE_CASE) */
  promptNumber?: number;
  /** Parsed from wire field "created_at" (SNAKE_CASE) */
  createdAt?: string;
  /** Parsed from wire field "created_at_epoch" (SNAKE_CASE) */
  createdAtEpoch?: number;
  /** Parsed from wire field "last_accessed_at" (SNAKE_CASE) */
  lastAccessedAt?: string;
}

/**
 * Parse a raw wire-format observation into the canonical Observation type.
 * Matches Go's dto.Observation.UnmarshalJSON and Python's Observation.from_wire.
 * Uses safe type conversion to handle null values and type mismatches gracefully.
 */
export function parseObservation(raw: Record<string, unknown>): Observation {
  return {
    id: safeStringOr(raw.id, ''),
    sessionId: safeStringOr(firstNonNullOr(raw, ['content_session_id', 'sessionId']) as string, ''),
    projectPath: safeStringOr(firstNonNullOr(raw, ['project', 'projectPath']) as string, ''),
    type: safeStringOr(raw.type, ''),
    title: safeString(raw.title),
    subtitle: safeString(raw.subtitle),
    content: safeStringOr(firstNonNullOr(raw, ['narrative', 'content']) as string, ''),
    facts: safeStringArray(raw.facts),
    concepts: safeStringArray(raw.concepts),
    filesRead: safeStringArray(firstNonNullOr(raw, ['files_read', 'filesRead'])),
    filesModified: safeStringArray(firstNonNullOr(raw, ['files_modified', 'filesModified'])),
    qualityScore: safeNumber(firstNonNullOr(raw, ['quality_score', 'qualityScore'])),
    feedbackType: safeString(firstNonNullOr(raw, ['feedback_type', 'feedbackType'])),
    feedbackUpdatedAt: safeString(firstNonNullOr(raw, ['feedback_updated_at', 'feedbackUpdatedAt'])),
    source: safeString(raw.source),
    extractedData: safeRecord(raw.extractedData) ?? {},
    promptNumber: safeNumber(firstNonNullOr(raw, ['prompt_number', 'promptNumber'])),
    createdAt: safeString(firstNonNullOr(raw, ['created_at', 'createdAt'])),
    createdAtEpoch: safeNumber(firstNonNullOr(raw, ['created_at_epoch', 'createdAtEpoch'])),
    lastAccessedAt: safeString(firstNonNullOr(raw, ['last_accessed_at', 'lastAccessedAt'])),
  };
}
