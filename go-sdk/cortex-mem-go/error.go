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

// IsInternal returns true if the error is a 5xx server error.
func IsInternal(err error) bool {
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		return apiErr.StatusCode >= 500
	}
	return errors.Is(err, ErrInternal)
}
