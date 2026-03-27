// ============================================================
// Session DTOs
// ============================================================

/**
 * Request to start or resume a session.
 * POST /api/session/start
 *
 * Wire format: {"session_id":"...", "project_path":"/path", "user_id":"..."}
 * Note: uses "project_path" (NOT "cwd") for session start.
 */
export interface SessionStartRequest {
  session_id: string;
  project_path: string;
  user_id?: string;
}

/**
 * Response from starting a session.
 */
export interface SessionStartResponse {
  session_db_id: string;
  session_id: string;
  context?: string;
  prompt_number: number;
}

/**
 * Request to end a session.
 * POST /api/ingest/session-end
 *
 * Wire format: {"session_id":"...", "cwd":"/path", "last_assistant_message":"..."}
 * Note: uses "cwd" (NOT "project_path") for session end.
 */
export interface SessionEndRequest {
  session_id: string;
  cwd: string;
  last_assistant_message?: string;
}

/**
 * Request to record a user prompt.
 * POST /api/ingest/user-prompt
 *
 * Wire format: {"session_id":"...", "prompt_text":"...", "cwd":"/path", "prompt_number":1}
 * Note: uses "cwd" (NOT "project_path") for user prompt.
 */
export interface UserPromptRequest {
  session_id: string;
  prompt_text: string;
  cwd: string;
  prompt_number?: number;
}

/**
 * Response from updating session userId.
 * PATCH /api/session/{sessionId}/user
 */
export interface SessionUserUpdateResponse {
  status: string;
  sessionId: string;
  userId: string;
}
