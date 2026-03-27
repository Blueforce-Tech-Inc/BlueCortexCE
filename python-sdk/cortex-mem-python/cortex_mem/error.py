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


class AuthError(APIError):
    """401 Unauthorized."""

    def __init__(self, message: str = "unauthorized") -> None:
        super().__init__(401, message)


class NotFoundError(APIError):
    """404 Not Found."""

    def __init__(self, message: str = "not found") -> None:
        super().__init__(404, message)


class ConflictError(APIError):
    """409 Conflict."""

    def __init__(self, message: str = "conflict") -> None:
        super().__init__(409, message)


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

    if status_code == 401:
        raise AuthError(message)
    if status_code == 404:
        raise NotFoundError(message)
    if status_code == 409:
        raise ConflictError(message)
    if status_code == 429:
        raise RateLimitError(message)
    if status_code >= 500:
        raise ServerError(status_code, message)

    raise APIError(status_code, message)


def is_retryable(status_code: int) -> bool:
    """Return True if the status code indicates a transient, retryable error."""
    return status_code in (429, 502, 503, 504)


def _extract_error_message(body: bytes) -> str:
    """Extract a human-readable message from an error response body."""
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
    return str(parsed)
