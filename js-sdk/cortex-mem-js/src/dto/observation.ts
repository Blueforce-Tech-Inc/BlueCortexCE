// ============================================================
// Observation DTOs
// ============================================================

import { safeString, safeStringOr, safeNumber, safeRecord, safeStringOrStringList, firstNonNullOr } from './wire-helpers';

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
 * If both are provided, "content" takes priority over "narrative".
 *
 * PATCH has three states per field, and all three are spelled out in the types:
 * - **omit** the key (or pass `undefined`) — leave the field unchanged
 * - pass a value — set the field
 * - pass `null` — **clear** the field
 *
 * The `null` state is why every field is `| null` rather than just optional.
 * The backend honours it — measured live, a PATCH of
 * `{"title":null,"subtitle":null,"source":null,"facts":null,"concepts":null,"extractedData":null,"content":null}`
 * returned 200 and stored SQL NULL for all seven — and `CortexMemClient.updateObservation`
 * documents it, but the types used to say `string | undefined`, so under this package's
 * own `"strict": true` a caller had to write `null as unknown as string` to reach it
 * (which is what the tests had to do). JSON.stringify has no omitempty, so null always
 * reaches the wire; only the type was blocking it.
 *
 * This makes JS the only SDK that can clear a string field to NULL: Java omits nulls
 * via `@JsonInclude(NON_NULL)`, Go via `omitempty`, and Python skips `None`. See P2-27
 * for the four-way capability table and P2-26 for the facts/concepts split.
 */
export interface ObservationUpdate {
  /** `null` clears the title. */
  title?: string | null;
  /** `null` clears the subtitle. */
  subtitle?: string | null;
  /** Observation content/narrative. Alias for "narrative" — backend uses "narrative" wire field. `null` clears it. */
  content?: string | null;
  /** Observation content/narrative. Alias for "content". When both are set, backend processes whichever is present. `null` clears it. */
  narrative?: string | null;
  /** `[]` replaces the list; `null` clears the column. Both reach the wire (no omitempty). */
  facts?: string[] | null;
  /** `[]` replaces the list; `null` clears the column. Both reach the wire (no omitempty). */
  concepts?: string[] | null;
  /** `null` clears the source. */
  source?: string | null;
  /** `null` clears it; `{}` is stored as an empty object rather than NULL, so prefer `null`. */
  extractedData?: Record<string, unknown> | null;
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
  /** Parsed from wire field "access_count" (SNAKE_CASE) — how many times this observation was retrieved */
  accessCount?: number;
  /** Parsed from wire field "refined_at" (SNAKE_CASE) — when this observation was last refined/extracted */
  refinedAt?: string;
  /** Parsed from wire field "refined_from_ids" (SNAKE_CASE) — IDs of source observations this was refined from */
  refinedFromIds?: string[];
  /** Parsed from wire field "user_comment" (SNAKE_CASE) — user-provided comment/annotation */
  userComment?: string;
}

/**
 * Parse a raw wire-format observation into the canonical Observation type.
 * Matches Go's dto.Observation.UnmarshalJSON and Python's Observation.from_wire.
 * Uses safe type conversion to handle null values and type mismatches gracefully.
 */
export function parseObservation(raw: Record<string, unknown>): Observation {
  return {
    id: safeStringOr(raw.id, ''),
    sessionId: safeStringOr(firstNonNullOr(raw, ['content_session_id', 'sessionId']), ''),
    projectPath: safeStringOr(firstNonNullOr(raw, ['project', 'projectPath']), ''),
    type: safeStringOr(raw.type, ''),
    title: safeString(raw.title),
    subtitle: safeString(raw.subtitle),
    content: safeStringOr(firstNonNullOr(raw, ['narrative', 'content']), ''),
    // facts/concepts/files_read/files_modified are JSONB columns that the
    // backend serializes as JSON-encoded *strings* for the WebUI, so a live
    // observation carries concepts: "[\"auth\"]" rather than a JSON array.
    // safeStringArray only accepts real arrays and returned undefined for these,
    // silently dropping the data. safeStringOrStringList handles both shapes.
    facts: safeStringOrStringList(raw.facts),
    concepts: safeStringOrStringList(raw.concepts),
    filesRead: safeStringOrStringList(firstNonNullOr(raw, ['files_read', 'filesRead'])),
    filesModified: safeStringOrStringList(firstNonNullOr(raw, ['files_modified', 'filesModified'])),
    qualityScore: safeNumber(firstNonNullOr(raw, ['quality_score', 'qualityScore'])),
    feedbackType: safeString(firstNonNullOr(raw, ['feedback_type', 'feedbackType'])),
    feedbackUpdatedAt: safeString(firstNonNullOr(raw, ['feedback_updated_at', 'feedbackUpdatedAt'])),
    source: safeString(raw.source),
    extractedData: safeRecord(firstNonNullOr(raw, ['extractedData', 'extracted_data'])) ?? {},
    promptNumber: safeNumber(firstNonNullOr(raw, ['prompt_number', 'promptNumber'])),
    createdAt: safeString(firstNonNullOr(raw, ['created_at', 'createdAt'])),
    createdAtEpoch: safeNumber(firstNonNullOr(raw, ['created_at_epoch', 'createdAtEpoch'])),
    lastAccessedAt: safeString(firstNonNullOr(raw, ['last_accessed_at', 'lastAccessedAt'])),
    accessCount: safeNumber(firstNonNullOr(raw, ['access_count', 'accessCount'])),
    refinedAt: safeString(firstNonNullOr(raw, ['refined_at', 'refinedAt'])),
    refinedFromIds: safeStringOrStringList(firstNonNullOr(raw, ['refined_from_ids', 'refinedFromIds'])),
    userComment: safeString(firstNonNullOr(raw, ['user_comment', 'userComment'])),
  };
}
