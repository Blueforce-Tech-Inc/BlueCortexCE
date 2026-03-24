package cortexmem

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"slices"
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

// WithMaxRetries sets the maximum number of retries.
func WithMaxRetries(n int) Option {
	return func(c *ClientConfig) { c.MaxRetries = n }
}

// DefaultClientConfig returns the default configuration.
func DefaultClientConfig() *ClientConfig {
	return &ClientConfig{
		BaseURL:    "http://127.0.0.1:37777",
		MaxRetries: 3,
		HTTPClient: &http.Client{
			Timeout: 30 * time.Second,
		},
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
	baseURL := c.config.BaseURL
	if !slices.Contains([]string{"http://", "https://"}, baseURL[:7]) {
		baseURL = "http://" + baseURL
	}
	u, err := url.Parse(baseURL + path)
	if err != nil {
		return nil, 0, fmt.Errorf("invalid URL: %w", err)
	}

	q := u.Query()
	for k, v := range queryParams {
		if v != "" {
			q.Set(k, v)
		}
	}
	u.RawQuery = q.Encode()

	var reqBody io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return nil, 0, fmt.Errorf("failed to marshal body: %w", err)
		}
		reqBody = bytes.NewReader(data)
	}

	req, err := http.NewRequestWithContext(ctx, method, u.String(), reqBody)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	if c.config.APIKey != "" {
		req.Header.Set("Authorization", "Bearer "+c.config.APIKey)
	}

	resp, err := c.config.HTTPClient.Do(req)
	if err != nil {
		return nil, 0, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, resp.StatusCode, fmt.Errorf("failed to read response: %w", err)
	}

	return respBody, resp.StatusCode, nil
}

func (c *httpClient) doRequestNoContent(ctx context.Context, method, path string, body any) error {
	_, status, err := c.doRequest(ctx, method, path, body, nil)
	if err != nil {
		return err
	}
	if status >= 400 {
		return fmt.Errorf("request failed with status %d", status)
	}
	return nil
}

func (c *httpClient) unmarshalJSON(data []byte, v any) error {
	return json.Unmarshal(data, v)
}
