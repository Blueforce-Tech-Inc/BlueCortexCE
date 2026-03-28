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
  APIError,
  isBadRequest,
  isUnauthorized,
  isForbidden,
  isNotFound,
  isConflict,
  isUnprocessable,
  isRateLimited,
  isClientError,
  isServerError,
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
  HealthResponse,
} from './dto';

// Wire helpers (safe type conversion utilities)
export {
  safeString,
  safeStringOr,
  safeNumber,
  safeNumberOr,
  safeStringArray,
  safeRecord,
} from './dto';

// Re-export parse functions (runtime, not just types)
export { parseObservation, parseExperience, parseExtractionResult } from './dto';
