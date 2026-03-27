// ============================================================
// Client options and configuration
// ============================================================

import type { Logger } from './client';

/** Custom fetch function type */
type FetchFn = (input: URL | RequestInfo, init?: RequestInit) => Promise<Response>;

/**
 * Options for creating a CortexMemClient.
 */
export interface CortexMemClientOptions {
  /** Backend base URL (default: "http://127.0.0.1:37777") */
  baseURL?: string;

  /** API key for authentication (sent as Bearer token) */
  apiKey?: string;

  /** Request timeout in milliseconds (default: 30000) */
  timeout?: number;

  /** Maximum retries for fire-and-forget operations (default: 3) */
  maxRetries?: number;

  /** Base retry backoff in milliseconds (default: 500) */
  retryBackoff?: number;

  /** Custom logger */
  logger?: Logger;

  /** Custom fetch implementation (for testing or polyfills) */
  fetch?: FetchFn;

  /** Custom headers to include in every request */
  headers?: Record<string, string>;
}

/** Resolved client config with all defaults applied. */
export interface ResolvedClientConfig {
  baseURL: string;
  apiKey: string;
  timeout: number;
  maxRetries: number;
  retryBackoff: number;
  logger: Logger;
  fetch: FetchFn;
  headers: Record<string, string>;
}

/** SDK version */
export const SDK_VERSION = '1.0.0';

/**
 * Resolve options with defaults.
 */
export function resolveConfig(options?: CortexMemClientOptions): ResolvedClientConfig {
  const baseURL = (options?.baseURL ?? 'http://127.0.0.1:37777').replace(/\/+$/, '');
  const globalFetch = (globalThis as Record<string, unknown>).fetch;
  const fallbackFetch: FetchFn = typeof globalFetch === 'function'
    ? (globalFetch as FetchFn).bind(globalThis)
    : (() => { throw new Error('fetch is not available'); });
  return {
    baseURL,
    apiKey: options?.apiKey ?? '',
    timeout: options?.timeout ?? 30_000,
    maxRetries: Math.max(1, options?.maxRetries ?? 3),
    retryBackoff: options?.retryBackoff ?? 500,
    logger: options?.logger ?? { debug() {}, info() {}, warn() {}, error() {} },
    fetch: options?.fetch ?? fallbackFetch,
    headers: options?.headers ?? {},
  };
}
