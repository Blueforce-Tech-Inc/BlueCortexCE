// ============================================================
// Miscellaneous DTOs
// ============================================================

/**
 * Backend version information.
 * GET /api/version
 */
export interface VersionResponse {
  version: string;
  service: string;
  java: string;
  springBoot: string;
}

/**
 * Projects list response.
 * GET /api/projects
 */
export interface ProjectsResponse {
  projects: string[];
}

/**
 * Statistics response.
 * GET /api/stats
 */
export interface StatsResponse {
  worker: WorkerStats;
  database: DatabaseStats;
}

export interface WorkerStats {
  isProcessing: boolean;
  queueDepth: number;
}

export interface DatabaseStats {
  totalObservations: number;
  totalSummaries: number;
  totalSessions: number;
  totalProjects: number;
}

/**
 * Modes response.
 * GET /api/modes
 */
export interface ModesResponse {
  id: string;
  name: string;
  description: string;
  version: string;
  observationTypes: string[];
  observationConcepts: string[];
}

/**
 * Health check response.
 * GET /api/health
 */
export interface HealthResponse {
  status: string;
  service: string;
  [key: string]: unknown;
}
