package cortexmem

import (
	"context"
	"fmt"
	"net/http"
	"net/url"

	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

// ==================== Session ====================

func (c *httpClient) StartSession(ctx context.Context, req dto.SessionStartRequest) (*dto.SessionStartResponse, error) {
	data, status, err := c.doRequest(ctx, http.MethodPost, "/api/session/start", req, nil)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp dto.SessionStartResponse
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse StartSession response: %w", err)
	}
	return &resp, nil
}

func (c *httpClient) UpdateSessionUserId(ctx context.Context, sessionID, userID string) (map[string]any, error) {
	path := fmt.Sprintf("/api/session/%s/user", sessionID)
	data, status, err := c.doRequest(ctx, http.MethodPatch, path, map[string]string{"user_id": userID}, nil)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp map[string]any
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse response: %w", err)
	}
	return resp, nil
}

// ==================== Capture (fire-and-forget) ====================

func (c *httpClient) RecordObservation(ctx context.Context, req dto.ObservationRequest) error {
	return c.doFireAndForget(ctx, "RecordObservation", func() error {
		return c.doRequestNoContent(ctx, http.MethodPost, "/api/ingest/tool-use", req)
	})
}

func (c *httpClient) RecordSessionEnd(ctx context.Context, req dto.SessionEndRequest) error {
	return c.doFireAndForget(ctx, "RecordSessionEnd", func() error {
		return c.doRequestNoContent(ctx, http.MethodPost, "/api/ingest/session-end", req)
	})
}

func (c *httpClient) RecordUserPrompt(ctx context.Context, req dto.UserPromptRequest) error {
	return c.doFireAndForget(ctx, "RecordUserPrompt", func() error {
		return c.doRequestNoContent(ctx, http.MethodPost, "/api/ingest/user-prompt", req)
	})
}

// ==================== Retrieval ====================

func (c *httpClient) RetrieveExperiences(ctx context.Context, req dto.ExperienceRequest) ([]dto.Experience, error) {
	data, status, err := c.doRequest(ctx, http.MethodPost, "/api/memory/experiences", req, nil)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp []dto.Experience
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse experiences: %w", err)
	}
	return resp, nil
}

func (c *httpClient) BuildICLPrompt(ctx context.Context, req dto.ICLPromptRequest) (*dto.ICLPromptResult, error) {
	data, status, err := c.doRequest(ctx, http.MethodPost, "/api/memory/icl-prompt", req, nil)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp dto.ICLPromptResult
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse ICL prompt result: %w", err)
	}
	return &resp, nil
}

func (c *httpClient) Search(ctx context.Context, req dto.SearchRequest) (*dto.SearchResult, error) {
	params := map[string]string{
		"project": req.Project,
		"query":   req.Query,
		"type":    req.Type,
		"concept": req.Concept,
		"source":  req.Source,
	}
	if req.Limit > 0 {
		params["limit"] = fmt.Sprintf("%d", req.Limit)
	}
	if req.Offset > 0 {
		params["offset"] = fmt.Sprintf("%d", req.Offset)
	}

	data, status, err := c.doRequest(ctx, http.MethodGet, "/api/search", nil, params)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp dto.SearchResult
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse search result: %w", err)
	}
	return &resp, nil
}

func (c *httpClient) ListObservations(ctx context.Context, req dto.ObservationsRequest) (*dto.ObservationsResponse, error) {
	params := map[string]string{
		"project": req.Project,
	}
	if req.Offset > 0 {
		params["offset"] = fmt.Sprintf("%d", req.Offset)
	}
	if req.Limit > 0 {
		params["limit"] = fmt.Sprintf("%d", req.Limit)
	}

	data, status, err := c.doRequest(ctx, http.MethodGet, "/api/observations", nil, params)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp dto.ObservationsResponse
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse observations: %w", err)
	}
	return &resp, nil
}

func (c *httpClient) GetObservationsByIds(ctx context.Context, ids []string) ([]dto.Observation, error) {
	req := dto.BatchObservationsRequest{IDs: ids}
	data, status, err := c.doRequest(ctx, http.MethodPost, "/api/observations/batch", req, nil)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp []dto.Observation
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse batch observations: %w", err)
	}
	return resp, nil
}

// ==================== Management ====================

func (c *httpClient) TriggerRefinement(ctx context.Context, projectPath string) error {
	// Backend expects "project" as QUERY PARAM (not body).
	// Verified: POST /api/memory/refine?project=/path
	return c.doFireAndForget(ctx, "TriggerRefinement", func() error {
		path := "/api/memory/refine?project=" + url.QueryEscape(projectPath)
		_, status, err := c.doRequest(ctx, http.MethodPost, path, nil, nil)
		if err != nil {
			return err
		}
		if status >= 400 {
			return &APIError{StatusCode: status, Message: fmt.Sprintf("trigger refinement failed with status %d", status)}
		}
		return nil
	})
}

func (c *httpClient) SubmitFeedback(ctx context.Context, observationID, feedbackType, comment string) error {
	// Backend expects camelCase: observationId, feedbackType
	req := dto.FeedbackRequest{
		ObservationID: observationID,
		FeedbackType:  feedbackType,
		Comment:       comment,
	}
	return c.doFireAndForget(ctx, "SubmitFeedback", func() error {
		return c.doRequestNoContent(ctx, http.MethodPost, "/api/memory/feedback", req)
	})
}

func (c *httpClient) UpdateObservation(ctx context.Context, observationID string, update dto.ObservationUpdate) error {
	path := fmt.Sprintf("/api/memory/observations/%s", observationID)
	return c.doFireAndForget(ctx, "UpdateObservation", func() error {
		return c.doRequestNoContent(ctx, http.MethodPatch, path, update)
	})
}

func (c *httpClient) DeleteObservation(ctx context.Context, observationID string) error {
	path := fmt.Sprintf("/api/memory/observations/%s", observationID)
	return c.doFireAndForget(ctx, "DeleteObservation", func() error {
		return c.doRequestNoContent(ctx, http.MethodDelete, path, nil)
	})
}

func (c *httpClient) GetQualityDistribution(ctx context.Context, projectPath string) (*dto.QualityDistribution, error) {
	// Backend expects "project" as QUERY PARAM
	params := map[string]string{"project": projectPath}
	data, status, err := c.doRequest(ctx, http.MethodGet, "/api/memory/quality-distribution", nil, params)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp dto.QualityDistribution
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse quality distribution: %w", err)
	}
	return &resp, nil
}

// ==================== Health ====================

func (c *httpClient) HealthCheck(ctx context.Context) error {
	data, status, err := c.doRequest(ctx, http.MethodGet, "/api/health", nil, nil)
	if err != nil {
		return err
	}
	if status >= 400 {
		return &APIError{StatusCode: status, Message: string(data)}
	}
	// Verify response body contains status:ok
	var resp map[string]any
	if err := c.unmarshalJSON(data, &resp); err == nil {
		if s, ok := resp["status"].(string); !ok || s != "ok" {
			return fmt.Errorf("cortex-ce: unhealthy: %v", resp)
		}
	}
	return nil
}

// ==================== Extraction ====================

func (c *httpClient) GetLatestExtraction(ctx context.Context, projectPath, templateName, userID string) (map[string]any, error) {
	path := fmt.Sprintf("/api/extraction/%s/latest", templateName)
	params := map[string]string{
		"projectPath":  projectPath,
		"templateName": templateName,
	}
	if userID != "" {
		params["userId"] = userID
	}
	data, status, err := c.doRequest(ctx, http.MethodGet, path, nil, params)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp map[string]any
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse extraction: %w", err)
	}
	return resp, nil
}

func (c *httpClient) GetExtractionHistory(ctx context.Context, projectPath, templateName, userID string, limit int) ([]map[string]any, error) {
	path := fmt.Sprintf("/api/extraction/%s/history", templateName)
	params := map[string]string{
		"projectPath": projectPath,
		"limit":       fmt.Sprintf("%d", limit),
	}
	if userID != "" {
		params["userId"] = userID
	}
	data, status, err := c.doRequest(ctx, http.MethodGet, path, nil, params)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp []map[string]any
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse extraction history: %w", err)
	}
	return resp, nil
}

// ==================== Version ====================

func (c *httpClient) GetVersion(ctx context.Context) (map[string]any, error) {
	data, status, err := c.doRequest(ctx, http.MethodGet, "/api/version", nil, nil)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp map[string]any
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse version: %w", err)
	}
	return resp, nil
}

// ==================== P1 Management ====================

func (c *httpClient) GetProjects(ctx context.Context) (map[string]any, error) {
	data, status, err := c.doRequest(ctx, http.MethodGet, "/api/projects", nil, nil)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp map[string]any
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse projects: %w", err)
	}
	return resp, nil
}

func (c *httpClient) GetStats(ctx context.Context, projectPath string) (map[string]any, error) {
	params := map[string]string{}
	if projectPath != "" {
		params["project"] = projectPath
	}
	data, status, err := c.doRequest(ctx, http.MethodGet, "/api/stats", nil, params)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp map[string]any
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse stats: %w", err)
	}
	return resp, nil
}

func (c *httpClient) GetModes(ctx context.Context) (map[string]any, error) {
	data, status, err := c.doRequest(ctx, http.MethodGet, "/api/modes", nil, nil)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp map[string]any
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse modes: %w", err)
	}
	return resp, nil
}

func (c *httpClient) GetSettings(ctx context.Context) (map[string]any, error) {
	data, status, err := c.doRequest(ctx, http.MethodGet, "/api/settings", nil, nil)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, &APIError{StatusCode: status, Message: string(data)}
	}
	var resp map[string]any
	if err := c.unmarshalJSON(data, &resp); err != nil {
		return nil, fmt.Errorf("cortex-ce: failed to parse settings: %w", err)
	}
	return resp, nil
}

// ==================== Lifecycle ====================

func (c *httpClient) Close() error {
	// HTTP client doesn't need explicit close in stdlib
	return nil
}

