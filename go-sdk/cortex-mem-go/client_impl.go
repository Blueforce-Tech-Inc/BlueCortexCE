package cortexmem

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/rand"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// Option configures the client.
type Option func(*ClientConfig)

// ClientConfig holds client configuration.
type ClientConfig struct {
	BaseURL        string
	APIKey         string
	HTTPClient     *http.Client
	Timeout        time.Duration // Overall request timeout (default: 30s)
	ConnectTimeout time.Duration // Connection timeout via custom Transport (default: 10s)
	MaxRetries     int
	RetryBackoff   time.Duration // Base backoff duration for retries (default: 500ms)
	Logger         Logger
}

// Logger is the logging interface. Compatible with *slog.Logger.
type Logger interface {
	Debug(msg string, args ...any)
	Info(msg string, args ...any)
	Warn(msg string, args ...any)
	Error(msg string, args ...any)
}

// WithBaseURL sets the base URL of the backend.
func WithBaseURL(baseURL string) Option {
	return func(c *ClientConfig) { c.BaseURL = baseURL }
}

// WithAPIKey sets the API key for authentication.
func WithAPIKey(apiKey string) Option {
	return func(c *ClientConfig) { c.APIKey = apiKey }
}

// WithHTTPClient sets a custom HTTP client.
// When set, Timeout and ConnectTimeout options are ignored (caller owns the http.Client).
func WithHTTPClient(client *http.Client) Option {
	return func(c *ClientConfig) { c.HTTPClient = client }
}

// WithTimeout sets the overall request timeout (default: 30s, matching Java SDK readTimeout).
// Ignored if WithHTTPClient is used.
func WithTimeout(d time.Duration) Option {
	return func(c *ClientConfig) { c.Timeout = d }
}

// WithConnectTimeout sets the connection timeout (default: 10s, matching Java SDK connectTimeout).
// Applied via a custom http.Transport.DialContext. Ignored if WithHTTPClient is used.
func WithConnectTimeout(d time.Duration) Option {
	return func(c *ClientConfig) { c.ConnectTimeout = d }
}

// WithMaxRetries sets the maximum number of retries for fire-and-forget operations.
func WithMaxRetries(n int) Option {
	return func(c *ClientConfig) { c.MaxRetries = n }
}

// WithRetryBackoff sets the base retry backoff duration.
// Actual backoff per attempt = RetryBackoff * attempt (linear backoff with ±25% jitter).
// Jitter prevents thundering herd when multiple clients retry simultaneously.
func WithRetryBackoff(d time.Duration) Option {
	return func(c *ClientConfig) { c.RetryBackoff = d }
}

// WithLogger sets a custom logger.
func WithLogger(logger Logger) Option {
	return func(c *ClientConfig) { c.Logger = logger }
}

// nopLogger is a no-op logger for when no logger is provided.
type nopLogger struct{}

func (nopLogger) Debug(string, ...any) {}
func (nopLogger) Info(string, ...any)  {}
func (nopLogger) Warn(string, ...any)  {}
func (nopLogger) Error(string, ...any) {}

// DefaultClientConfig returns the default configuration.
// Timeouts match Java SDK defaults: connectTimeout=10s, readTimeout=30s.
func DefaultClientConfig() *ClientConfig {
	return &ClientConfig{
		BaseURL:        "http://127.0.0.1:37777",
		Timeout:        30 * time.Second,
		ConnectTimeout: 10 * time.Second,
		MaxRetries:     3,
		RetryBackoff:   500 * time.Millisecond,
		Logger:         nopLogger{},
	}
}

// NewClient creates a new Cortex CE client.
func NewClient(opts ...Option) Client {
	cfg := DefaultClientConfig()
	for _, opt := range opts {
		opt(cfg)
	}
	// Validate configuration
	if cfg.BaseURL == "" {
		cfg.BaseURL = "http://127.0.0.1:37777"
	}
	// Normalize: strip trailing slash to prevent double-slash in URLs (e.g., //api/health)
	cfg.BaseURL = strings.TrimSuffix(cfg.BaseURL, "/")
	if cfg.MaxRetries < 1 {
		cfg.MaxRetries = 1 // At least one attempt (no retries is valid)
	}
	if cfg.Timeout < 100*time.Millisecond {
		cfg.Timeout = 30 * time.Second // Reset to default if unreasonably low
	}
	if cfg.ConnectTimeout < 100*time.Millisecond {
		cfg.ConnectTimeout = 10 * time.Second // Reset to default if unreasonably low
	}

	// If caller did not provide a custom http.Client, build one from timeout settings.
	if cfg.HTTPClient == nil {
		cfg.HTTPClient = &http.Client{
			Timeout: cfg.Timeout,
			Transport: &http.Transport{
				DialContext: (&net.Dialer{
					Timeout: cfg.ConnectTimeout,
				}).DialContext,
				// Deprecated since Go 1.21; replacement would require DialTLSContext
				// which adds unnecessary complexity for a simple timeout wrapper.
				TLSHandshakeTimeout: 10 * time.Second,
				IdleConnTimeout:     90 * time.Second,
				MaxIdleConnsPerHost: 10,
				TLSClientConfig: &tls.Config{
					InsecureSkipVerify: false,
				},
			},
		}
	}
	return &httpClient{config: cfg}
}

const (
	// Version is the SDK version, used in User-Agent header.
	Version = "1.0.0"

	// MaxResponseBytes is the maximum response body size (10 MB).
	// Prevents OOM from malicious or broken servers.
	MaxResponseBytes = 10 << 20
)

// httpClient is the HTTP implementation of Client.
type httpClient struct {
	config *ClientConfig
}

func (c *httpClient) doRequest(ctx context.Context, method, path string, body any, queryParams map[string]string) ([]byte, int, error) {
	// Fast-fail: avoid JSON marshaling if context is already cancelled
	select {
	case <-ctx.Done():
		return nil, 0, ctx.Err()
	default:
	}

	u, err := url.Parse(c.config.BaseURL + path)
	if err != nil {
		return nil, 0, fmt.Errorf("cortex-ce: invalid URL %q: %w", c.config.BaseURL+path, err)
	}

	if queryParams != nil {
		q := u.Query()
		for k, v := range queryParams {
			if v != "" {
				q.Set(k, v)
			}
		}
		u.RawQuery = q.Encode()
	}

	var reqBody io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return nil, 0, fmt.Errorf("cortex-ce: failed to marshal body: %w", err)
		}
		reqBody = bytes.NewReader(data)
	}

	req, err := http.NewRequestWithContext(ctx, method, u.String(), reqBody)
	if err != nil {
		return nil, 0, fmt.Errorf("cortex-ce: failed to create request: %w", err)
	}

	// Only set Content-Type when there's a body (not for GET/DELETE with no body)
	if reqBody != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", "cortex-mem-go/"+Version)
	if c.config.APIKey != "" {
		req.Header.Set("Authorization", "Bearer "+c.config.APIKey)
	}

	resp, err := c.config.HTTPClient.Do(req)
	if err != nil {
		return nil, 0, fmt.Errorf("cortex-ce: request failed: %w", err)
	}
	defer resp.Body.Close()

	// Limit response body to MaxResponseBytes to prevent OOM from misbehaving servers.
	respBody, err := io.ReadAll(io.LimitReader(resp.Body, MaxResponseBytes))
	if err != nil {
		return nil, resp.StatusCode, fmt.Errorf("cortex-ce: failed to read response: %w", err)
	}

	return respBody, resp.StatusCode, nil
}

// doRequestJSON makes a request and unmarshals the JSON response body into T.
// Returns an APIError for 4xx/5xx status codes.
// This eliminates the repeated doRequest → status check → unmarshal pattern.
func doRequestJSON[T any](c *httpClient, ctx context.Context, method, path string, body any, queryParams map[string]string) (*T, error) {
	data, status, err := c.doRequest(ctx, method, path, body, queryParams)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: extractErrorMessage(data)}
	}
	var resp T
	if err := json.Unmarshal(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse %s response: %w", path, err)
	}
	return &resp, nil
}

// doRequestNoContent makes a request and checks for success (no response body needed).
func (c *httpClient) doRequestNoContent(ctx context.Context, method, path string, body any) error {
	return c.doRequestNoContentWithParams(ctx, method, path, body, nil)
}

// doRequestNoContentWithParams makes a request with optional query params and checks for success.
func (c *httpClient) doRequestNoContentWithParams(ctx context.Context, method, path string, body any, queryParams map[string]string) error {
	data, status, err := c.doRequest(ctx, method, path, body, queryParams)
	if err != nil {
		return err
	}
	if status >= 400 {
		return &APIError{StatusCode: status, Message: extractErrorMessage(data)}
	}
	return nil
}

// extractErrorMessage parses a JSON error response body and extracts a human-readable message.
// Falls back to raw body string if parsing fails.
// Supports common patterns:
//   - {"error":"..."}, {"message":"..."}, {"detail":"..."}
//   - [{"error":"..."}]  (JSON array with error objects)
//   - "not found"        (plain JSON string)
func extractErrorMessage(data []byte) string {
	// Handle empty response body (server returned status code with no body)
	if len(data) == 0 {
		return "(empty response body)"
	}

	// Try JSON object first: {"error":"..."}
	var parsed map[string]any
	if err := json.Unmarshal(data, &parsed); err == nil {
		// Try common error field names (in priority order)
		for _, key := range []string{"error", "message", "detail"} {
			if v, ok := parsed[key]; ok {
				if s, ok := v.(string); ok && s != "" {
					return s
				}
			}
		}
		// Fallback: compact JSON representation
		data2, err := json.Marshal(parsed)
		if err != nil {
			return string(data)
		}
		return string(data2)
	}

	// Try JSON array: [{"error":"..."}]
	var arr []any
	if err := json.Unmarshal(data, &arr); err == nil && len(arr) > 0 {
		if first, ok := arr[0].(map[string]any); ok {
			for _, key := range []string{"error", "message", "detail"} {
				if v, ok := first[key]; ok {
					if s, ok := v.(string); ok && s != "" {
						return s
					}
				}
			}
		}
		return string(data)
	}

	// Try plain JSON string: "not found"
	var str string
	if err := json.Unmarshal(data, &str); err == nil && str != "" {
		return str
	}

	// Not JSON — return raw body (truncated to 200 chars for readability)
	s := string(data)
	if len(s) > 200 {
		return s[:200] + "..."
	}
	return s
}

// doFireAndForget executes a capture operation with retry and error swallowing.
// Matches Java SDK's executeWithRetry behavior: retries internally, logs on failure.
// Retries on network errors, 429, 502, 503, 504. Does NOT retry on 4xx or 500.
// If the context is already cancelled, skips execution entirely (fire-and-forget optimization).
func (c *httpClient) doFireAndForget(ctx context.Context, name string, fn func() error) error {
	// Check context before wasting effort on an already-cancelled request
	select {
	case <-ctx.Done():
		c.config.Logger.Warn("cortex-ce: " + name + " skipped, context already cancelled")
		return nil
	default:
	}

	var lastErr error
	for attempt := 1; attempt <= c.config.MaxRetries; attempt++ {
		lastErr = fn()
		if lastErr == nil {
			return nil
		}
		// Don't retry on non-retryable errors (4xx client errors, etc.)
		if !isTransient(lastErr) {
			c.config.Logger.Warn("cortex-ce: "+name+" failed with non-retryable error, giving up",
				"error", lastErr,
				"attempt", attempt,
			)
			return nil // Fire-and-forget: swallow error
		}
		// Log intermediate retry failures
		if attempt < c.config.MaxRetries {
			c.config.Logger.Warn("cortex-ce: "+name+" failed, retrying",
				"error", lastErr,
				"attempt", attempt,
				"maxAttempts", c.config.MaxRetries,
			)
			// Linear backoff with jitter (±25%) to prevent thundering herd.
			// Base delay = RetryBackoff * attempt, jittered to [0.75x, 1.25x].
			baseDelay := c.config.RetryBackoff * time.Duration(attempt)
			jitterRange := int64(baseDelay) / 2
			var jitter time.Duration
			if jitterRange > 0 {
				jitter = time.Duration(rand.Int63n(jitterRange)) - baseDelay/4
			}
			delay := baseDelay + jitter
			if delay < 0 {
				delay = 0
			}
			select {
			case <-ctx.Done():
				// Fire-and-forget: swallow context cancellation
				c.config.Logger.Warn("cortex-ce: "+name+" cancelled during retry",
					"attempt", attempt,
					"attempts", c.config.MaxRetries,
				)
				return nil
			case <-time.After(delay):
			}
		}
	}
	c.config.Logger.Warn("cortex-ce: "+name+" failed after retries",
		"error", lastErr,
		"attempts", c.config.MaxRetries,
	)
	return nil // Fire-and-forget: swallow all errors (including ctx.Err) after retries
}

// isTransient returns true if the error is likely transient and worth retrying.
// Consistent with IsRetryable(): retries on 429, 502, 503, 504 and network errors.
// Does NOT retry on 500 (typically a code bug, not a transient failure) or 4xx.
//
// Why these specific codes are retryable:
//   - 429: Rate limiting — back off and retry after delay.
//   - 502: Reverse proxy got invalid response from backend (crash/restart).
//   - 503: Server temporarily overloaded or in maintenance (RFC 7231: SHOULD retry).
//   - 504: Backend didn't respond in time (slow/crashed) — retry gives another chance.
func isTransient(err error) bool {
	if err == nil {
		return false
	}
	// Network/transport errors (no HTTP status) are always worth retrying
	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		return true
	}
	// Retry on 429 (rate limited), 502, 503, 504 — NOT 500
	return apiErr.StatusCode == http.StatusTooManyRequests ||
		apiErr.StatusCode == http.StatusBadGateway ||
		apiErr.StatusCode == http.StatusServiceUnavailable ||
		apiErr.StatusCode == http.StatusGatewayTimeout
}
