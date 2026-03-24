package cortexmem

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"
)

// Option configures the client.
type Option func(*ClientConfig)

// ClientConfig holds client configuration.
type ClientConfig struct {
	BaseURL    string
	APIKey     string
	HTTPClient *http.Client
	MaxRetries int
	Logger     Logger
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
func WithHTTPClient(client *http.Client) Option {
	return func(c *ClientConfig) { c.HTTPClient = client }
}

// WithMaxRetries sets the maximum number of retries for fire-and-forget operations.
func WithMaxRetries(n int) Option {
	return func(c *ClientConfig) { c.MaxRetries = n }
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
func DefaultClientConfig() *ClientConfig {
	return &ClientConfig{
		BaseURL:    "http://127.0.0.1:37777",
		MaxRetries: 3,
		HTTPClient: &http.Client{
			Timeout: 30 * time.Second,
		},
		Logger: nopLogger{},
	}
}

// NewClient creates a new Cortex CE client.
func NewClient(opts ...Option) Client {
	cfg := DefaultClientConfig()
	for _, opt := range opts {
		opt(cfg)
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

	req.Header.Set("Content-Type", "application/json")
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
	data, status, err := c.doRequest(ctx, method, path, body, nil)
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
		// Linear backoff (matching Java SDK: backoff * attempt)
		if attempt < c.config.MaxRetries {
			delay := time.Duration(500*attempt) * time.Millisecond
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(delay):
			}
		}
	}
	c.config.Logger.Warn("cortex-ce: "+name+" failed after retries",
		"error", lastErr,
		"attempts", c.config.MaxRetries,
	)
	return nil // Fire-and-forget: swallow error after retries
}

func (c *httpClient) unmarshalJSON(data []byte, v any) error {
	return json.Unmarshal(data, v)
}
