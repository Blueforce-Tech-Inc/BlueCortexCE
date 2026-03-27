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
//
// Knowledge: HTTP status codes and retry strategy
//
//   - 429 (Too Many Requests): The server is rate-limiting us. This is explicitly transient —
//     retrying after a delay (ideally respecting Retry-After header) will likely succeed.
//
//   - 502 (Bad Gateway): The upstream server (e.g., a reverse proxy like Nginx) received an
//     invalid response from the backend server. This usually means the backend crashed or
//     restarted mid-request. Retrying will route to a (hopefully healthy) new instance.
//
//   - 503 (Service Unavailable): The server is temporarily unable to handle the request,
//     often due to being overloaded or undergoing maintenance. This is explicitly designed
//     to be temporary — RFC 7231 says clients SHOULD retry after the delay in Retry-After.
//
//   - 504 (Gateway Timeout): The upstream server didn't get a response from the backend
//     in time. Similar to 502, the backend may be slow or crashed. Retrying gives it
//     another chance to respond within the timeout window.
//
//   - 500 (Internal Server Error): NOT retryable by default. This is a code bug on the
//     server side — retrying the same request will almost certainly produce the same error.
//     Only retry 500 if you have specific knowledge that the error is transient (e.g.,
//     database connection pool exhaustion).
//
// Matches:
//   - Network/transport errors (non-HTTP, non-sentinel): connection refused, timeouts, DNS failures, etc.
//   - 429 (rate limited), 502 (bad gateway), 503 (service unavailable), 504 (gateway timeout).
//
// Does NOT match 500 (internal server error) — that's typically a code bug, not a transient failure.
func IsRetryable(err error) bool {
	if err == nil {
		return false
	}
	// Check for specific HTTP status errors that are transient.
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		// Retry on 429 (rate limited), 502 (bad gateway), 503 (unavailable), 504 (timeout).
		// Do NOT retry on 500 (code bug) or any 4xx client error.
		return apiErr.StatusCode == http.StatusTooManyRequests ||
			apiErr.StatusCode == http.StatusBadGateway ||
			apiErr.StatusCode == http.StatusServiceUnavailable ||
			apiErr.StatusCode == http.StatusGatewayTimeout
	}
	// Check retryable sentinel errors (reached via Unwrap chain or direct reference).
	if errors.Is(err, ErrRateLimited) || errors.Is(err, ErrBadGateway) ||
		errors.Is(err, ErrServiceUnavailable) || errors.Is(err, ErrGatewayTimeout) {
		return true
	}
	// Non-retryable sentinel errors (4xx, 500, etc.) are known HTTP errors.
	if isSentinelError(err) {
		return false
	}
	// Non-HTTP, non-sentinel errors are network/transport errors — always retryable.
	return true
}

// isSentinelError returns true if the error matches any of the known sentinel errors.
func isSentinelError(err error) bool {
	return errors.Is(err, ErrBadRequest) ||
		errors.Is(err, ErrUnauthorized) ||
		errors.Is(err, ErrForbidden) ||
		errors.Is(err, ErrNotFound) ||
		errors.Is(err, ErrConflict) ||
		errors.Is(err, ErrUnprocessable) ||
		errors.Is(err, ErrRateLimited) ||
		errors.Is(err, ErrInternal) ||
		errors.Is(err, ErrBadGateway) ||
		errors.Is(err, ErrServiceUnavailable) ||
		errors.Is(err, ErrGatewayTimeout)
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
