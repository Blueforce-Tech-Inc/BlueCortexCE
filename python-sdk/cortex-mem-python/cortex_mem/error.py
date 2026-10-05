"""Cortex CE SDK exception types."""

from __future__ import annotations

import json


class CortexError(Exception):
    """Base exception for Cortex CE SDK."""


class APIError(CortexError):
    """API error with HTTP status code."""

    def __init__(self, status_code: int, message: str) -> None:
        self.status_code = status_code
        self.message = message
        super().__init__(f"cortex-ce: API error {status_code}: {message}")


class ValidationError(CortexError, ValueError):
    """Client-side validation error (e.g., empty required field, batch size exceeded).

    Matches Go SDK's ValidationError for cross-SDK parity.
    Both Go and JS SDKs expose a ``field`` attribute for structured error handling.

    Also inherits from :class:`ValueError` for backward compatibility: earlier
    releases raised a bare ``ValueError`` from ``ObservationUpdate.__post_init__``,
    so existing ``except ValueError`` handlers must keep working. The SDK's own
    exception type is the documented one, and it now catches every validation
    failure regardless of which call path detected it.
    """

    def __init__(self, message: str, field: str = "") -> None:
        self.field = field
        self.message = message
        if field:
            super().__init__(f"cortex-ce: validation error on {field}: {message}")
        else:
            super().__init__(f"cortex-ce: validation error: {message}")


class AuthError(APIError):
    """401 Unauthorized."""

    def __init__(self, message: str = "unauthorized") -> None:
        super().__init__(401, message)


class BadRequestError(APIError):
    """400 Bad Request."""

    def __init__(self, message: str = "bad request") -> None:
        super().__init__(400, message)


class NotFoundError(APIError):
    """404 Not Found."""

    def __init__(self, message: str = "not found") -> None:
        super().__init__(404, message)


class ConflictError(APIError):
    """409 Conflict."""

    def __init__(self, message: str = "conflict") -> None:
        super().__init__(409, message)


class ForbiddenError(APIError):
    """403 Forbidden."""

    def __init__(self, message: str = "forbidden") -> None:
        super().__init__(403, message)


class MethodNotAllowedError(APIError):
    """405 Method Not Allowed."""

    def __init__(self, message: str = "method not allowed") -> None:
        super().__init__(405, message)


class UnprocessableError(APIError):
    """422 Unprocessable Entity."""

    def __init__(self, message: str = "unprocessable entity") -> None:
        super().__init__(422, message)


class RateLimitError(APIError):
    """429 Too Many Requests."""

    def __init__(self, message: str = "rate limited") -> None:
        super().__init__(429, message)


class ServerError(APIError):
    """5xx Server Error."""

    def __init__(self, status_code: int, message: str = "internal server error") -> None:
        super().__init__(status_code, message)


def raise_for_status(status_code: int, body: bytes) -> None:
    """Raise an appropriate APIError for non-2xx status codes."""
    if 200 <= status_code < 300:
        return

    message = _extract_error_message(body)

    if status_code == 400:
        raise BadRequestError(message)
    if status_code == 401:
        raise AuthError(message)
    if status_code == 403:
        raise ForbiddenError(message)
    if status_code == 404:
        raise NotFoundError(message)
    if status_code == 405:
        raise MethodNotAllowedError(message)
    if status_code == 409:
        raise ConflictError(message)
    if status_code == 422:
        raise UnprocessableError(message)
    if status_code == 429:
        raise RateLimitError(message)
    if status_code >= 500:
        raise ServerError(status_code, message)

    raise APIError(status_code, message)


def is_retryable(err_or_status: int | BaseException) -> bool:
    """Return True if the argument indicates a transient, retryable error.

    Accepts **either** a status code or an exception.

    <p>Accepting both is deliberate and was added after this was found to be a
    silent trap rather than a cosmetic mismatch. Go's ``IsRetryable(err)`` and
    JS's ``isRetryable(err)`` both take an <em>error</em> under exactly this
    name, but this function took only a status code. A retry loop ported
    mechanically from either of them — ``is_retryable(e)`` — therefore returned
    ``False`` for every error without raising, including the 429 and 503 that
    both of those answer ``True`` to. The caller's own retry loop simply never
    fired, with nothing to indicate why.

    <p>Status-code behaviour is unchanged, so existing callers are unaffected.
    Passing anything else returns ``False``, matching the fail-closed rule the
    rest of this module states. See P2-65.
    """
    if isinstance(err_or_status, BaseException):
        return is_retryable_error(err_or_status)
    return err_or_status in (429, 502, 503, 504)


def is_retryable_error(err: Exception) -> bool:
    """Return True if the error is likely transient and the request can be retried.

    Retryable: 429 (rate limited), 502 (bad gateway), 503 (unavailable), 504 (timeout),
    and network/transport errors (ConnectionError, Timeout, RequestException).
    NOT retryable: 500 (code bug), 4xx (client error), and client-side permanent
    failures such as ValidationError.

    Matches Go's IsRetryable(err) and JS's isRetryable(err) for cross-SDK parity.
    All three positively identify transient errors and return False for anything
    unrecognised, so a caller with its own retry loop is never told to retry a
    request that cannot succeed.
    Go matches net.Error plus io.ErrUnexpectedEOF for a connection dropped
    part-way through a response body; JS matches TypeError (fetch network errors)
    and AbortError (timeouts).
    """
    if isinstance(err, APIError):
        return is_retryable(err.status_code)
    # Network/transport errors are always retryable (matches Go's IsRetryable behavior)
    # requests.ConnectionError (includes ConnectionRefusedError, DNS failures)
    # requests.Timeout (includes ConnectTimeout, ReadTimeout)
    try:
        from requests import RequestException
        if isinstance(err, RequestException):
            return True
    except ImportError:
        pass
    # Fallback: common network error types from stdlib
    if isinstance(err, (ConnectionError, TimeoutError, OSError)):
        return True
    return False


def is_validation_error(err: Exception) -> bool:
    """Return True if the error is a client-side ValidationError. (Go: IsValidationError, JS: isValidationError)"""
    return isinstance(err, ValidationError)


def is_bad_request(err: Exception) -> bool:
    """Return True if the error is a 400 Bad Request. (Go: IsBadRequest, JS: isBadRequest)"""
    return isinstance(err, APIError) and err.status_code == 400


def is_not_found(err: Exception) -> bool:
    """Return True if the error is a 404 Not Found. (Go: IsNotFound, JS: isNotFound)"""
    return isinstance(err, APIError) and err.status_code == 404


def is_unauthorized(err: Exception) -> bool:
    """Return True if the error is a 401 Unauthorized. (Go: IsUnauthorized, JS: isUnauthorized)"""
    return isinstance(err, APIError) and err.status_code == 401


def is_forbidden(err: Exception) -> bool:
    """Return True if the error is a 403 Forbidden. (Go: IsForbidden, JS: isForbidden)"""
    return isinstance(err, APIError) and err.status_code == 403


def is_conflict(err: Exception) -> bool:
    """Return True if the error is a 409 Conflict. (Go: IsConflict, JS: isConflict)"""
    return isinstance(err, APIError) and err.status_code == 409


def is_unprocessable(err: Exception) -> bool:
    """Return True if the error is a 422 Unprocessable Entity. (Go: IsUnprocessable, JS: isUnprocessable)"""
    return isinstance(err, APIError) and err.status_code == 422


def is_rate_limited(err: Exception) -> bool:
    """Return True if the error is a 429 Too Many Requests. (Go: IsRateLimited, JS: isRateLimited)"""
    return isinstance(err, APIError) and err.status_code == 429


def is_bad_gateway(err: Exception) -> bool:
    """Return True if the error is a 502 Bad Gateway. (Go: IsBadGateway, JS: isBadGateway)"""
    return isinstance(err, APIError) and err.status_code == 502


def is_service_unavailable(err: Exception) -> bool:
    """Return True if the error is a 503 Service Unavailable. (Go: IsServiceUnavailable, JS: isServiceUnavailable)"""
    return isinstance(err, APIError) and err.status_code == 503


def is_gateway_timeout(err: Exception) -> bool:
    """Return True if the error is a 504 Gateway Timeout. (Go: IsGatewayTimeout, JS: isGatewayTimeout)"""
    return isinstance(err, APIError) and err.status_code == 504


def is_client_error(err: Exception) -> bool:
    """Return True if the error is a 4xx client error. (Go: IsClientError, JS: isClientError)"""
    return isinstance(err, APIError) and 400 <= err.status_code < 500


def is_server_error(err: Exception) -> bool:
    """Return True if the error is a 5xx server error. (Go: IsServerError, JS: isServerError)"""
    return isinstance(err, APIError) and err.status_code >= 500


def _extract_error_message(body: bytes) -> str:
    """Extract a human-readable message from an error response body.

    Handles three response formats:
    1. JSON object with "error", "message", or "detail" key
    2. JSON string (returned directly)
    3. Non-JSON body (decoded as UTF-8, truncated to 200 chars)
    4. Empty body (returns "(empty response body)")
    """
    if not body:
        return "(empty response body)"

    try:
        parsed = json.loads(body)
    except (json.JSONDecodeError, UnicodeDecodeError):
        s = body.decode("utf-8", errors="replace")
        return s[:200] if len(s) > 200 else s

    if isinstance(parsed, dict):
        for key in ("error", "message", "detail"):
            v = parsed.get(key)
            if isinstance(v, str) and v:
                return v
    if isinstance(parsed, str):
        return parsed
    # Handle JSON arrays (e.g., [{"error": "..."}]) — extract first error message
    if isinstance(parsed, list) and parsed:
        first = parsed[0]
        if isinstance(first, dict):
            for key in ("error", "message", "detail"):
                v = first.get(key)
                if isinstance(v, str) and v:
                    return v
    return str(parsed)
