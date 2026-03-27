// ============================================================
// Extraction DTOs
// ============================================================

/**
 * Extraction result from the backend.
 * Used by both latest and history endpoints.
 */
export interface ExtractionResult {
  status?: string;
  template?: string;
  message?: string;
  sessionId: string;
  extractedData: Record<string, unknown>;
  createdAt: number;
  observationId: string;
}
