// ============================================================
// Error types for Cortex CE SDK
// ============================================================

/**
 * API error returned by the Cortex CE backend.
 */
export class APIError extends Error {
  public readonly statusCode: number;
  public readonly body?: string;

  constructor(statusCode: number, message: string, body?: string) {
    super(`cortex-ce: API error ${statusCode}: ${message}`);
    this.name = 'APIError';
    this.statusCode = statusCode;
    this.body = body;
    // Required for instanceof to work correctly when compiled to CJS
    Object.setPrototypeOf(this, APIError.prototype);
  }
}

// Error predicate functions

/** Returns true if the error is a 400 Bad Request. */
export function isBadRequest(err: unknown): boolean {
  return err instanceof APIError && err.statusCode === 400;
}

/** Returns true if the error is a 401 Unauthorized. */
export function isUnauthorized(err: unknown): boolean {
  return err instanceof APIError && err.statusCode === 401;
}

/** Returns true if the error is a 403 Forbidden. */
export function isForbidden(err: unknown): boolean {
  return err instanceof APIError && err.statusCode === 403;
}

/** Returns true if the error is a 404 Not Found. */
export function isNotFound(err: unknown): boolean {
  return err instanceof APIError && err.statusCode === 404;
}

/** Returns true if the error is a 409 Conflict. */
export function isConflict(err: unknown): boolean {
  return err instanceof APIError && err.statusCode === 409;
}

/** Returns true if the error is a 422 Unprocessable Entity. */
export function isUnprocessable(err: unknown): boolean {
  return err instanceof APIError && err.statusCode === 422;
}

/** Returns true if the error is a 429 Rate Limited. */
export function isRateLimited(err: unknown): boolean {
  return err instanceof APIError && err.statusCode === 429;
}

/** Returns true if the error is a 4xx client error. */
export function isClientError(err: unknown): boolean {
  return err instanceof APIError && err.statusCode >= 400 && err.statusCode < 500;
}

/** Returns true if the error is a 5xx server error. */
export function isServerError(err: unknown): boolean {
  return err instanceof APIError && err.statusCode >= 500;
}

/**
 * Returns true if the error is likely transient and the request can be retried.
 * Retryable: 429, 502, 503, 504, network errors (TypeError), and timeout aborts.
 * NOT retryable: 500 (code bug), 4xx (client error).
 */
export function isRetryable(err: unknown): boolean {
  if (err instanceof APIError) {
    return err.statusCode === 429 ||
      err.statusCode === 502 ||
      err.statusCode === 503 ||
      err.statusCode === 504;
  }
  // Network errors (fetch TypeError) are retryable
  if (err instanceof TypeError) {
    return true;
  }
  // Timeout-triggered AbortController errors are retryable
  if (err instanceof DOMException && err.name === 'AbortError') {
    return true;
  }
  return false;
}
