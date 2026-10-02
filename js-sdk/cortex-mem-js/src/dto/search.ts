// ============================================================
// Search DTOs
// ============================================================

import type { Observation } from './observation';

/**
 * Request for semantic search.
 * GET /api/search?project=...&query=...&type=...&concept=...&source=...&limit=...&offset=...&orderBy=...
 *
 * All fields are passed as URL query parameters (not JSON body).
 *
 * orderBy supports: "created_at_epoch" / "createdAtEpoch" — orders by observation
 * creation time descending (newest first). Without it, results are ordered by
 * vector similarity score.
 */
export interface SearchRequest {
  project: string;
  query?: string;
  type?: string;
  concept?: string;
  source?: string;
  limit?: number;
  offset?: number;
  /** Sort order field (e.g., "created_at_epoch"). Backend accepts both snake_case and camelCase. */
  orderBy?: string;
}

/**
 * Response from the search API.
 */
export interface SearchResult {
  observations: Observation[];
  strategy: string;
  /** Parsed from wire field "fell_back" (SNAKE_CASE) */
  fellBack: boolean;
  count: number;
}

/**
 * Request to list observations with pagination.
 * GET /api/observations?project=...&offset=...&limit=...
 *
 * All fields are passed as URL query parameters.
 */
export interface ObservationsRequest {
  project?: string;
  offset?: number;
  limit?: number;
}

/**
 * Paginated response from listing observations.
 *
 * The backend returns only `{items, hasMore}`. It does not echo `total`,
 * `offset` or `limit` back, so all three are optional here: a present value
 * comes from the server, an absent one means "the server did not say".
 * Treat them as undefined rather than assuming a number -- the Go and Python
 * SDKs carry the same three fields for the same reason.
 */
export interface ObservationsResponse {
  items: Observation[];
  hasMore: boolean;
  total?: number;
  offset?: number;
  limit?: number;
}

/**
 * Response from batch observation retrieval.
 */
export interface BatchObservationsResponse {
  observations: Observation[];
  count: number;
}
