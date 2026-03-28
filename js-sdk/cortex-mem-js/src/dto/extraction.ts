// ============================================================
// Extraction DTOs
// ============================================================

import { safeString, safeStringOr, safeNumberOr, safeRecord } from './wire-helpers';

/**
 * Extraction result from the backend.
 * Used by both latest and history endpoints.
 *
 * Wire format uses camelCase (backend Map.of).
 * Use {@link parseExtractionResult} to safely parse from wire format.
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

/**
 * Parse a raw wire-format extraction result into the canonical type.
 * Handles null values and type mismatches gracefully.
 */
export function parseExtractionResult(raw: Record<string, unknown>): ExtractionResult {
  return {
    status: safeString(raw.status),
    template: safeString(raw.template),
    message: safeString(raw.message),
    sessionId: safeStringOr(raw.sessionId, ''),
    extractedData: safeRecord(raw.extractedData) ?? {},
    createdAt: safeNumberOr(raw.createdAt, 0),
    observationId: safeStringOr(raw.observationId, ''),
  };
}
