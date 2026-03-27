// ============================================================
// Search DTOs
// ============================================================

import type { Observation } from './observation';

/**
 * Request for semantic search.
 * GET /api/search?project=...&query=...&type=...&concept=...&source=...&limit=...&offset=...
 *
 * All fields are passed as URL query parameters (not JSON body).
 */
export interface SearchRequest {
  project: string;
  query?: string;
  type?: string;
  concept?: string;
  source?: string;
  limit?: number;
  offset?: number;
}

/**
 * Response from the search API.
 */
export interface SearchResult {
  observations: Observation[];
  strategy: string;
  fell_back: boolean;
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
 */
export interface ObservationsResponse {
  items: Observation[];
  hasMore: boolean;
  total?: number;
  offset: number;
  limit: number;
}

/**
 * Request to get observations by IDs.
 * POST /api/observations/batch
 *
 * Wire format: {"ids":["id1", "id2", ...]}
 */
export interface BatchObservationsRequest {
  ids: string[];
}

/**
 * Response from batch observation retrieval.
 */
export interface BatchObservationsResponse {
  observations: Observation[];
  count: number;
}
