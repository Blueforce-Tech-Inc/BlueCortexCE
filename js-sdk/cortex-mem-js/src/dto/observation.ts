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
 */
export interface ObservationUpdate {
  title?: string;
  subtitle?: string;
  content?: string;
  facts?: string[];
  concepts?: string[];
  source?: string;
  extractedData?: Record<string, unknown>;
}

/**
 * A single observation record returned from the backend.
 * All fields are camelCase (matching backend JSON).
 */
export interface Observation {
  id: string;
  sessionId: string;
  projectPath: string;
  type: string;
  title?: string;
  subtitle?: string;
  content: string;
  facts?: string[];
  concepts?: string[];
  qualityScore?: number;
  source?: string;
  extractedData?: Record<string, unknown>;
  createdAt?: string;
}
