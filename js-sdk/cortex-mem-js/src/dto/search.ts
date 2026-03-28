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
 */
export interface ObservationsResponse {
  items: Observation[];
  hasMore: boolean;
  total?: number;
  offset: number;
  limit: number;
}

/**
 * Response from batch observation retrieval.
 */
export interface BatchObservationsResponse {
  observations: Observation[];
  count: number;
}
