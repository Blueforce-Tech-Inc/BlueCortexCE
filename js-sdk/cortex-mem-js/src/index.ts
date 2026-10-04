// ============================================================
// Public API exports
// ============================================================

// Client
export { CortexMemClient } from './client';
export type { Logger } from './client';

// Options
export type { CortexMemClientOptions } from './client-options';
export { SDK_VERSION } from './client-options';

// Errors
export {
  ValidationError,
  APIError,
  isValidationError,
  isBadRequest,
  isUnauthorized,
  isForbidden,
  isNotFound,
  isConflict,
  isUnprocessable,
  isRateLimited,
  isClientError,
  isServerError,
  isBadGateway,
  isServiceUnavailable,
  isGatewayTimeout,
  isRetryable,
} from './errors';

// DTOs — re-export all types
export type {
  // Session
  SessionStartRequest,
  SessionStartResponse,
  SessionEndRequest,
  UserPromptRequest,
  SessionUserUpdateResponse,
  // Observation
  ObservationRequest,
  ObservationUpdate,
  Observation,
  // Experience & ICL
  ExperienceRequest,
  Experience,
  ICLPromptRequest,
  ICLPromptResult,
  // Search
  SearchRequest,
  SearchResult,
  ObservationsRequest,
  ObservationsResponse,
  BatchObservationsResponse,
  // Management
  QualityDistribution,
  FeedbackRequest,
  // Extraction
  ExtractionResult,
  // Misc
  VersionResponse,
  ProjectsResponse,
  StatsResponse,
  WorkerStats,
  DatabaseStats,
  ModesResponse,
  ObservationType,
  ObservationConcept,
  HealthResponse,
} from './dto';

// Wire helpers (safe type conversion utilities)
export {
  safeString,
  safeStringOr,
  safeNumber,
  safeNumberOr,
  safeStringArray,
  // Exported alongside its siblings because it is the one that handles the wire
  // shape list columns actually arrive in: a JSON array or a comma-separated
  // string (refined_from_ids). parseObservation uses it for facts, concepts,
  // files_read, files_modified and refined_from_ids, so it is what a caller
  // needs to read those fields the same way the SDK does.
  safeStringOrStringList,
  safeRecord,
} from './dto';

// Re-export parse functions (runtime, not just types)
export { parseObservation, parseExperience, parseExtractionResult, parseICLPromptResult, parseStatsResponse, parseWorkerStats, parseDatabaseStats, parseVersionResponse, parseQualityDistribution, parseObservationType, parseObservationConcept } from './dto';
