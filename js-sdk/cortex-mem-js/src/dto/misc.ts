// ============================================================
// Miscellaneous DTOs
// ============================================================

import { safeStringOr, safeNumberOr, safeStringArray, safeRecord, firstNonNullOr } from './wire-helpers';

/**
 * Backend version information.
 * GET /api/version
 *
 * Use {@link parseVersionResponse} to safely parse from wire format.
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
 *
 * Use {@link parseStatsResponse} to safely parse from wire format.
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

// ============================================================
// Parse functions for defensive wire format handling
// ============================================================

/**
 * Parse raw worker stats from wire format.
 */
export function parseWorkerStats(raw: Record<string, unknown>): WorkerStats {
  return {
    isProcessing: Boolean(raw.isProcessing ?? raw.is_processing ?? false),
    queueDepth: safeNumberOr(firstNonNullOr(raw, ['queueDepth', 'queue_depth']), 0),
  };
}

/**
 * Parse raw database stats from wire format.
 */
export function parseDatabaseStats(raw: Record<string, unknown>): DatabaseStats {
  return {
    totalObservations: safeNumberOr(firstNonNullOr(raw, ['totalObservations', 'total_observations']), 0),
    totalSummaries: safeNumberOr(firstNonNullOr(raw, ['totalSummaries', 'total_summaries']), 0),
    totalSessions: safeNumberOr(firstNonNullOr(raw, ['totalSessions', 'total_sessions']), 0),
    totalProjects: safeNumberOr(firstNonNullOr(raw, ['totalProjects', 'total_projects']), 0),
  };
}

/**
 * Parse raw stats response from wire format.
 * Handles null nested objects and type mismatches gracefully.
 */
export function parseStatsResponse(raw: Record<string, unknown>): StatsResponse {
  const workerRaw = safeRecord(raw.worker) ?? {};
  const databaseRaw = safeRecord(raw.database) ?? {};
  return {
    worker: parseWorkerStats(workerRaw),
    database: parseDatabaseStats(databaseRaw),
  };
}

/**
 * Parse raw version response from wire format.
 * Handles null values and type mismatches gracefully.
 */
export function parseVersionResponse(raw: Record<string, unknown>): VersionResponse {
  return {
    version: safeStringOr(raw.version, ''),
    service: safeStringOr(raw.service, ''),
    java: safeStringOr(raw.java, ''),
    springBoot: safeStringOr(firstNonNullOr(raw, ['springBoot', 'spring_boot']), ''),
  };
}
