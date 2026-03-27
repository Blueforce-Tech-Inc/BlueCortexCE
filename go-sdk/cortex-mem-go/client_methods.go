package cortexmem

import (
	"context"
	"fmt"
	"net/http"

	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

// ==================== Session ====================

func (c *httpClient) StartSession(ctx context.Context, req dto.SessionStartRequest) (*dto.SessionStartResponse, error) {
	return doRequestJSON[dto.SessionStartResponse](c, ctx, http.MethodPost, "/api/session/start", req, nil)
}

func (c *httpClient) UpdateSessionUserId(ctx context.Context, sessionID, userID string) (*dto.SessionUserUpdateResponse, error) {
	path := fmt.Sprintf("/api/session/%s/user", sessionID)
	return doRequestJSON[dto.SessionUserUpdateResponse](c, ctx, http.MethodPatch, path, map[string]string{"user_id": userID}, nil)
}

// ==================== Capture (fire-and-forget) ====================

func (c *httpClient) RecordObservation(ctx context.Context, req dto.ObservationRequest) error {
	if req.SessionID == "" {
		return fmt.Errorf("cortex-ce: ObservationRequest.SessionID is required")
	}
	return c.doFireAndForget(ctx, "RecordObservation", func() error {
		return c.doRequestNoContent(ctx, http.MethodPost, "/api/ingest/tool-use", req)
	})
}

func (c *httpClient) RecordSessionEnd(ctx context.Context, req dto.SessionEndRequest) error {
	if req.SessionID == "" {
		return fmt.Errorf("cortex-ce: SessionEndRequest.SessionID is required")
	}
	return c.doFireAndForget(ctx, "RecordSessionEnd", func() error {
		return c.doRequestNoContent(ctx, http.MethodPost, "/api/ingest/session-end", req)
	})
}

func (c *httpClient) RecordUserPrompt(ctx context.Context, req dto.UserPromptRequest) error {
	if req.SessionID == "" {
		return fmt.Errorf("cortex-ce: UserPromptRequest.SessionID is required")
	}
	if req.PromptText == "" {
		return fmt.Errorf("cortex-ce: UserPromptRequest.PromptText is required")
	}
	return c.doFireAndForget(ctx, "RecordUserPrompt", func() error {
		return c.doRequestNoContent(ctx, http.MethodPost, "/api/ingest/user-prompt", req)
	})
}

// ==================== Retrieval ====================

func (c *httpClient) RetrieveExperiences(ctx context.Context, req dto.ExperienceRequest) ([]dto.Experience, error) {
	if req.Task == "" {
		return nil, fmt.Errorf("cortex-ce: ExperienceRequest.Task is required")
	}
	result, err := doRequestJSON[[]dto.Experience](c, ctx, http.MethodPost, "/api/memory/experiences", req, nil)
	if err != nil {
		return nil, err
	}
	return *result, nil
}

func (c *httpClient) BuildICLPrompt(ctx context.Context, req dto.ICLPromptRequest) (*dto.ICLPromptResult, error) {
	if req.Task == "" {
		return nil, fmt.Errorf("cortex-ce: ICLPromptRequest.Task is required")
	}
	return doRequestJSON[dto.ICLPromptResult](c, ctx, http.MethodPost, "/api/memory/icl-prompt", req, nil)
}

func (c *httpClient) Search(ctx context.Context, req dto.SearchRequest) (*dto.SearchResult, error) {
	if req.Project == "" {
		return nil, fmt.Errorf("cortex-ce: SearchRequest.Project is required")
	}
	// Build query params — only include fields that are set.
	// Backend accepts: project (required), query, type, concept, source, limit, offset.
	params := map[string]string{"project": req.Project}
	if req.Query != "" {
		params["query"] = req.Query
	}
	if req.Type != "" {
		params["type"] = req.Type
	}
	if req.Concept != "" {
		params["concept"] = req.Concept
	}
	if req.Source != "" {
		params["source"] = req.Source
	}
	if req.Limit > 0 {
		params["limit"] = fmt.Sprintf("%d", req.Limit)
	}
	if req.Offset > 0 {
		params["offset"] = fmt.Sprintf("%d", req.Offset)
	}
	return doRequestJSON[dto.SearchResult](c, ctx, http.MethodGet, "/api/search", nil, params)
}

func (c *httpClient) ListObservations(ctx context.Context, req dto.ObservationsRequest) (*dto.ObservationsResponse, error) {
	if req.Project == "" {
		return nil, fmt.Errorf("cortex-ce: ObservationsRequest.Project is required")
	}
	// Build query params — only include optional fields when set.
	params := map[string]string{"project": req.Project}
	if req.Offset > 0 {
		params["offset"] = fmt.Sprintf("%d", req.Offset)
	}
	if req.Limit > 0 {
		params["limit"] = fmt.Sprintf("%d", req.Limit)
	}
	return doRequestJSON[dto.ObservationsResponse](c, ctx, http.MethodGet, "/api/observations", nil, params)
}

func (c *httpClient) GetObservationsByIds(ctx context.Context, ids []string) (*dto.BatchObservationsResponse, error) {
	if len(ids) == 0 {
		return nil, fmt.Errorf("cortex-ce: ids must not be empty")
	}
	if len(ids) > 100 {
		return nil, fmt.Errorf("cortex-ce: batch size exceeds maximum of 100 (got %d)", len(ids))
	}
	for i, id := range ids {
		if id == "" {
			return nil, fmt.Errorf("cortex-ce: ids[%d] is empty", i)
		}
	}
	req := dto.BatchObservationsRequest{IDs: ids}
	return doRequestJSON[dto.BatchObservationsResponse](c, ctx, http.MethodPost, "/api/observations/batch", req, nil)
}

// ==================== Management ====================

func (c *httpClient) TriggerRefinement(ctx context.Context, projectPath string) error {
	if projectPath == "" {
		return fmt.Errorf("cortex-ce: projectPath is required")
	}
	// Backend expects "project" as QUERY PARAM (not body).
	// Verified: POST /api/memory/refine?project=/path
	// NOT fire-and-forget: this is an explicit user action, errors must propagate.
	return c.doRequestNoContentWithParams(ctx, http.MethodPost, "/api/memory/refine", nil,
		map[string]string{"project": projectPath})
}

func (c *httpClient) SubmitFeedback(ctx context.Context, observationID, feedbackType, comment string) error {
	if observationID == "" {
		return fmt.Errorf("cortex-ce: observationID is required")
	}
	if feedbackType == "" {
		return fmt.Errorf("cortex-ce: feedbackType is required")
	}
	// Backend expects camelCase: observationId, feedbackType
	// NOT fire-and-forget: explicit user action, errors must propagate.
	req := dto.FeedbackRequest{
		ObservationID: observationID,
		FeedbackType:  feedbackType,
		Comment:       comment,
	}
	return c.doRequestNoContent(ctx, http.MethodPost, "/api/memory/feedback", req)
}

func (c *httpClient) UpdateObservation(ctx context.Context, observationID string, update dto.ObservationUpdate) error {
	if observationID == "" {
		return fmt.Errorf("cortex-ce: observationID is required")
	}
	// NOT fire-and-forget: explicit user action, errors must propagate.
	path := fmt.Sprintf("/api/memory/observations/%s", observationID)
	return c.doRequestNoContent(ctx, http.MethodPatch, path, update)
}

func (c *httpClient) DeleteObservation(ctx context.Context, observationID string) error {
	if observationID == "" {
		return fmt.Errorf("cortex-ce: observationID is required")
	}
	// NOT fire-and-forget: explicit user action, errors must propagate.
	path := fmt.Sprintf("/api/memory/observations/%s", observationID)
	return c.doRequestNoContent(ctx, http.MethodDelete, path, nil)
}

func (c *httpClient) GetQualityDistribution(ctx context.Context, projectPath string) (*dto.QualityDistribution, error) {
	if projectPath == "" {
		return nil, fmt.Errorf("cortex-ce: projectPath is required")
	}
	return doRequestJSON[dto.QualityDistribution](c, ctx, http.MethodGet, "/api/memory/quality-distribution", nil,
		map[string]string{"project": projectPath})
}

// ==================== Health ====================

func (c *httpClient) HealthCheck(ctx context.Context) error {
	data, status, err := c.doRequest(ctx, http.MethodGet, "/api/health", nil, nil)
	if err != nil {
		return err
	}
	if status >= 400 {
		return &APIError{StatusCode: status, Message: extractErrorMessage(data)}
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

func (c *httpClient) TriggerExtraction(ctx context.Context, projectPath string) error {
	if projectPath == "" {
		return fmt.Errorf("cortex-ce: projectPath is required")
	}
	return c.doRequestNoContentWithParams(ctx, http.MethodPost, "/api/extraction/run", nil,
		map[string]string{"projectPath": projectPath})
}

func (c *httpClient) GetLatestExtraction(ctx context.Context, projectPath, templateName, userID string) (*dto.ExtractionResult, error) {
	if projectPath == "" {
		return nil, fmt.Errorf("cortex-ce: projectPath is required")
	}
	if templateName == "" {
		return nil, fmt.Errorf("cortex-ce: templateName is required")
	}
	path := fmt.Sprintf("/api/extraction/%s/latest", templateName)
	params := map[string]string{"projectPath": projectPath}
	if userID != "" {
		params["userId"] = userID
	}
	return doRequestJSON[dto.ExtractionResult](c, ctx, http.MethodGet, path, nil, params)
}

func (c *httpClient) GetExtractionHistory(ctx context.Context, projectPath, templateName, userID string, limit int) ([]dto.ExtractionResult, error) {
	if projectPath == "" {
		return nil, fmt.Errorf("cortex-ce: projectPath is required")
	}
	if templateName == "" {
		return nil, fmt.Errorf("cortex-ce: templateName is required")
	}
	if limit < 0 {
		return nil, fmt.Errorf("cortex-ce: limit must not be negative")
	}
	path := fmt.Sprintf("/api/extraction/%s/history", templateName)
	params := map[string]string{
		"projectPath": projectPath,
	}
	// Only send limit when > 0; omitting it lets the backend use its default (10).
	// Sending limit=0 would be clamped to 1 by the backend (not "use default").
	if limit > 0 {
		params["limit"] = fmt.Sprintf("%d", limit)
	}
	if userID != "" {
		params["userId"] = userID
	}
	result, err := doRequestJSON[[]dto.ExtractionResult](c, ctx, http.MethodGet, path, nil, params)
	if err != nil {
		return nil, err
	}
	return *result, nil
}

// ==================== Version ====================

func (c *httpClient) GetVersion(ctx context.Context) (*dto.VersionResponse, error) {
	return doRequestJSON[dto.VersionResponse](c, ctx, http.MethodGet, "/api/version", nil, nil)
}

// ==================== P1 Management ====================

func (c *httpClient) GetProjects(ctx context.Context) (*dto.ProjectsResponse, error) {
	return doRequestJSON[dto.ProjectsResponse](c, ctx, http.MethodGet, "/api/projects", nil, nil)
}

func (c *httpClient) GetStats(ctx context.Context, projectPath string) (*dto.StatsResponse, error) {
	params := map[string]string{}
	if projectPath != "" {
		params["project"] = projectPath
	}
	return doRequestJSON[dto.StatsResponse](c, ctx, http.MethodGet, "/api/stats", nil, params)
}

func (c *httpClient) GetModes(ctx context.Context) (*dto.ModesResponse, error) {
	return doRequestJSON[dto.ModesResponse](c, ctx, http.MethodGet, "/api/modes", nil, nil)
}

func (c *httpClient) GetSettings(ctx context.Context) (map[string]any, error) {
	result, err := doRequestJSON[map[string]any](c, ctx, http.MethodGet, "/api/settings", nil, nil)
	if err != nil {
		return nil, err
	}
	return *result, nil
}

// ==================== Lifecycle ====================

func (c *httpClient) Close() error {
	if c.config.HTTPClient != nil {
		if t, ok := c.config.HTTPClient.Transport.(*http.Transport); ok {
			t.CloseIdleConnections()
		}
	}
	return nil
}

