package cortexmem

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"time"
)

// Option configures the client.
type Option func(*ClientConfig)

// ClientConfig holds client configuration.
type ClientConfig struct {
	BaseURL       string
	APIKey        string
	HTTPClient    *http.Client
	Timeout       time.Duration // Overall request timeout (default: 30s)
	ConnectTimeout time.Duration // Connection timeout via custom Transport (default: 10s)
	MaxRetries    int
	RetryBackoff  time.Duration // Base backoff duration for retries (default: 500ms)
	Logger        Logger
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
// Actual backoff per attempt = RetryBackoff * attempt (linear backoff, matching Java SDK).
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
	// If caller did not provide a custom http.Client, build one from timeout settings.
	if cfg.HTTPClient == nil {
		cfg.HTTPClient = &http.Client{
			Timeout: cfg.Timeout,
			Transport: &http.Transport{
				DialContext: (&net.Dialer{
					Timeout: cfg.ConnectTimeout,
				}).DialContext,
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

// httpClient is the HTTP implementation of Client.
type httpClient struct {
	config *ClientConfig
}

func (c *httpClient) doRequest(ctx context.Context, method, path string, body any, queryParams map[string]string) ([]byte, int, error) {
	u, err := url.Parse(c.config.BaseURL + path)
	if err != nil {
		return nil, 0, fmt.Errorf("cortex-ce: invalid URL: %w", err)
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
	if c.config.APIKey != "" {
		req.Header.Set("Authorization", "Bearer "+c.config.APIKey)
	}

	resp, err := c.config.HTTPClient.Do(req)
	if err != nil {
		return nil, 0, fmt.Errorf("cortex-ce: request failed: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, resp.StatusCode, fmt.Errorf("cortex-ce: failed to read response: %w", err)
	}

	return respBody, resp.StatusCode, nil
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
		return &APIError{StatusCode: status, Message: string(data)}
	}
	return nil
}

// doFireAndForget executes a capture operation with retry and error swallowing.
// Matches Java SDK's executeWithRetry behavior: retries internally, logs on failure.
func (c *httpClient) doFireAndForget(ctx context.Context, name string, fn func() error) error {
	var lastErr error
	for attempt := 1; attempt <= c.config.MaxRetries; attempt++ {
		lastErr = fn()
		if lastErr == nil {
			return nil
		}
		// Log intermediate retry failures
		if attempt < c.config.MaxRetries {
			c.config.Logger.Warn("cortex-ce: "+name+" failed, retrying",
				"error", lastErr,
				"attempt", attempt,
				"maxAttempts", c.config.MaxRetries,
			)
			// Linear backoff (matching Java SDK: backoff * attempt)
			delay := c.config.RetryBackoff * time.Duration(attempt)
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

func (c *httpClient) unmarshalJSON(data []byte, v any) error {
	return json.Unmarshal(data, v)
}
