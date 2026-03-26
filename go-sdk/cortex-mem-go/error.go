package cortexmem

import (
	"errors"
	"fmt"
	"net/http"
)

// APIError represents an error response from the Cortex CE backend.
type APIError struct {
	StatusCode int
	Message    string
}

func (e *APIError) Error() string {
	return fmt.Sprintf("cortex-ce: API error %d: %s", e.StatusCode, e.Message)
}

func (e *APIError) Unwrap() error {
	return statusCodeToError(e.StatusCode)
}

// Sentinel errors for common HTTP status codes.
var (
	ErrBadRequest          = errors.New("cortex-ce: bad request")
	ErrUnauthorized        = errors.New("cortex-ce: unauthorized")
	ErrForbidden           = errors.New("cortex-ce: forbidden")
	ErrNotFound            = errors.New("cortex-ce: not found")
	ErrConflict            = errors.New("cortex-ce: conflict")
	ErrUnprocessable       = errors.New("cortex-ce: unprocessable entity")
	ErrRateLimited         = errors.New("cortex-ce: rate limited")
	ErrInternal            = errors.New("cortex-ce: internal server error")
	ErrBadGateway          = errors.New("cortex-ce: bad gateway")
	ErrServiceUnavailable  = errors.New("cortex-ce: service unavailable")
	ErrGatewayTimeout      = errors.New("cortex-ce: gateway timeout")
)

func statusCodeToError(code int) error {
	switch code {
	case http.StatusBadRequest:
		return ErrBadRequest
	case http.StatusUnauthorized:
		return ErrUnauthorized
	case http.StatusForbidden:
		return ErrForbidden
	case http.StatusNotFound:
		return ErrNotFound
	case http.StatusConflict:
		return ErrConflict
	case 422:
		return ErrUnprocessable
	case http.StatusTooManyRequests:
		return ErrRateLimited
	case http.StatusInternalServerError:
		return ErrInternal
	case http.StatusBadGateway:
		return ErrBadGateway
	case http.StatusServiceUnavailable:
		return ErrServiceUnavailable
	case http.StatusGatewayTimeout:
		return ErrGatewayTimeout
	default:
		return fmt.Errorf("cortex-ce: unknown error %d", code)
	}
}

// IsNotFound returns true if the error is a 404.
func IsNotFound(err error) bool {
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		return apiErr.StatusCode == http.StatusNotFound
	}
	return errors.Is(err, ErrNotFound)
}

// IsBadRequest returns true if the error is a 400.
func IsBadRequest(err error) bool {
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		return apiErr.StatusCode == http.StatusBadRequest
	}
	return errors.Is(err, ErrBadRequest)
}

// IsUnauthorized returns true if the error is a 401.
func IsUnauthorized(err error) bool {
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		return apiErr.StatusCode == http.StatusUnauthorized
	}
	return errors.Is(err, ErrUnauthorized)
}

// IsConflict returns true if the error is a 409.
func IsConflict(err error) bool {
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		return apiErr.StatusCode == http.StatusConflict
	}
	return errors.Is(err, ErrConflict)
}

// IsRateLimited returns true if the error is a 429.
func IsRateLimited(err error) bool {
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		return apiErr.StatusCode == http.StatusTooManyRequests
	}
	return errors.Is(err, ErrRateLimited)
}

// IsForbidden returns true if the error is a 403.
func IsForbidden(err error) bool {
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		return apiErr.StatusCode == http.StatusForbidden
	}
	return errors.Is(err, ErrForbidden)
}

// IsUnprocessable returns true if the error is a 422.
func IsUnprocessable(err error) bool {
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		return apiErr.StatusCode == 422
	}
	return errors.Is(err, ErrUnprocessable)
}

// IsInternal returns true if the error is a 5xx server error (500, 502, 503, 504, etc).
func IsInternal(err error) bool {
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		return apiErr.StatusCode >= 500
	}
	return errors.Is(err, ErrInternal)
}

// IsRetryable returns true if the error is likely transient and the request can be retried.
// Matches: 429 (rate limited), 502 (bad gateway), 503 (service unavailable), 504 (gateway timeout).
// Does NOT match 500 (internal server error) — that's typically a code bug, not a transient failure.
func IsRetryable(err error) bool {
	return IsRateLimited(err) || IsBadGateway(err) || IsServiceUnavailable(err) || IsGatewayTimeout(err)
}

// IsBadGateway returns true if the error is a 502.
func IsBadGateway(err error) bool {
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		return apiErr.StatusCode == http.StatusBadGateway
	}
	return errors.Is(err, ErrBadGateway)
}

// IsServiceUnavailable returns true if the error is a 503.
func IsServiceUnavailable(err error) bool {
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		return apiErr.StatusCode == http.StatusServiceUnavailable
	}
	return errors.Is(err, ErrServiceUnavailable)
}

// IsGatewayTimeout returns true if the error is a 504.
func IsGatewayTimeout(err error) bool {
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		return apiErr.StatusCode == http.StatusGatewayTimeout
	}
	return errors.Is(err, ErrGatewayTimeout)
}

// IsClientError returns true if the error is a 4xx client error.
func IsClientError(err error) bool {
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		return apiErr.StatusCode >= 400 && apiErr.StatusCode < 500
	}
	return false
}

// IsServerError returns true if the error is a 5xx server error.
// This is an alias for IsInternal — prefer IsInternal for clarity.
func IsServerError(err error) bool {
	return IsInternal(err)
}
