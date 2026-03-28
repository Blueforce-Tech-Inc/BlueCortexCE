// ============================================================
// Error types for Cortex CE SDK
// ============================================================

/**
 * Client-side validation error (thrown before any HTTP request is made).
 * Use {@link isValidationError} to distinguish from API responses.
 */
export class ValidationError extends Error {
  /** The field that failed validation (e.g., "observationId"). */
  public readonly field: string;

  constructor(field: string, message: string) {
    super(`cortex-ce: validation error on ${field}: ${message}`);
    this.name = 'ValidationError';
    this.field = field;
    Object.setPrototypeOf(this, ValidationError.prototype);
  }

  /** Structured JSON representation for logging and serialization. */
  toJSON(): Record<string, unknown> {
    return {
      name: this.name,
      field: this.field,
      message: this.message,
    };
  }
}

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

  /** Structured JSON representation for logging and serialization. */
  toJSON(): Record<string, unknown> {
    return {
      name: this.name,
      statusCode: this.statusCode,
      message: this.message,
      body: this.body,
    };
  }
}

// Error predicate functions

/** Returns true if the error is a client-side validation error (no HTTP request was made). */
export function isValidationError(err: unknown): boolean {
  return err instanceof ValidationError;
}

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

/** Returns true if the error is a 502 Bad Gateway. */
export function isBadGateway(err: unknown): boolean {
  return err instanceof APIError && err.statusCode === 502;
}

/** Returns true if the error is a 503 Service Unavailable. */
export function isServiceUnavailable(err: unknown): boolean {
  return err instanceof APIError && err.statusCode === 503;
}

/** Returns true if the error is a 504 Gateway Timeout. */
export function isGatewayTimeout(err: unknown): boolean {
  return err instanceof APIError && err.statusCode === 504;
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
  // Timeout-triggered AbortController errors are retryable.
  // Check by name rather than instanceof DOMException for Node.js compatibility
  // (DOMException exists in Node 18+ but err.name === 'AbortError' is more portable).
  if (err instanceof Error && err.name === 'AbortError') {
    return true;
  }
  return false;
}
