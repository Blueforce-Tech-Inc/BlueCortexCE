package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	cortexmem "github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

// writeJSON encodes v as JSON and sets the Content-Type header.
func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(v); err != nil {
		log.Printf("failed to encode JSON response: %v", err)
	}
}

// writeJSONError writes a JSON error response with the given status code.
func writeJSONError(w http.ResponseWriter, status int, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(map[string]string{"error": message})
}

// maxRequestBodySize is the maximum request body size (1 MB).
const maxRequestBodySize = 1 << 20

// readJSON decodes the request body into dst, enforcing a size limit.
func readJSON(r *http.Request, dst any) error {
	r.Body = http.MaxBytesReader(nil, r.Body, maxRequestBodySize)
	if err := json.NewDecoder(r.Body).Decode(dst); err != nil {
		var maxBytesErr *http.MaxBytesError
		if errors.As(err, &maxBytesErr) {
			return fmt.Errorf("request body too large (max %d bytes)", maxRequestBodySize)
		}
		return err
	}
	return nil
}

// checkMethod verifies the request method matches expected; returns false if not.
func checkMethod(w http.ResponseWriter, r *http.Request, expected string) bool {
	if r.Method != expected {
		writeJSONError(w, http.StatusMethodNotAllowed, "method not allowed: expected "+expected)
		return false
	}
	return true
}

// recovery wraps an http.Handler to catch panics and return 500 instead of crashing.
func recovery(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if err := recover(); err != nil {
				log.Printf("PANIC recovered: %v", err)
				writeJSONError(w, http.StatusInternalServerError, "internal server error")
			}
		}()
		next.ServeHTTP(w, r)
	})
}

// statusRecorder wraps http.ResponseWriter to capture the response status code.
type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (sr *statusRecorder) WriteHeader(code int) {
	sr.status = code
	sr.ResponseWriter.WriteHeader(code)
}

// requestLogger wraps an http.Handler to log request method, path, status, and duration.
func requestLogger(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		start := time.Now()
		next.ServeHTTP(rec, r)
		log.Printf("%s %s %d (%s)", r.Method, r.URL.Path, rec.status, time.Since(start))
	})
}

func main() {
	// Create a new client
	client := cortexmem.NewClient(
		cortexmem.WithBaseURL("http://127.0.0.1:37777"),
	)
	defer client.Close()

	// Setup HTTP handlers
	mux := http.NewServeMux()

	// --- GET /health ---
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		if err := client.HealthCheck(r.Context()); err != nil {
			writeJSONError(w, http.StatusServiceUnavailable, fmt.Sprintf("unhealthy: %v", err))
			return
		}
		writeJSON(w, map[string]any{
			"service": "go-sdk-http-server",
			"status":  "ok",
			"time":    time.Now().Format(time.RFC3339),
		})
	})

	// --- POST /chat ---
	mux.HandleFunc("/chat", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodPost) {
			return
		}
		var req struct {
			Project  string `json:"project"`
			Message  string `json:"message"`
			UserId   string `json:"userId,omitempty"`
			MaxChars int    `json:"maxChars,omitempty"`
		}
		if err := readJSON(r, &req); err != nil {
			writeJSONError(w, http.StatusBadRequest, "invalid JSON body")
			return
		}
		if req.Project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}
		if req.Message == "" {
			writeJSONError(w, http.StatusBadRequest, "message is required")
			return
		}

		// Build ICL prompt from memory to augment the response with context.
		iclResult, iclErr := client.BuildICLPrompt(r.Context(), dto.ICLPromptRequest{
			Task:     req.Message,
			Project:  req.Project,
			MaxChars: req.MaxChars,
			UserID:   req.UserId,
		})

		resp := map[string]any{
			"response":  fmt.Sprintf("Received: %s", req.Message),
			"project":   req.Project,
			"timestamp": time.Now().Format(time.RFC3339),
		}
		if iclErr == nil && iclResult.Prompt != "" {
			resp["memoryContext"] = iclResult.Prompt
			resp["experienceCount"] = iclResult.ExperienceCount
		}
		writeJSON(w, resp)
	})

	// --- GET /search ---
	mux.HandleFunc("/search", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		project := r.URL.Query().Get("project")
		if project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}
		limit := 0 // 0 = backend default
		if l := r.URL.Query().Get("limit"); l != "" {
			parsed, err := strconv.Atoi(l)
			if err != nil || parsed < 0 || parsed > 100 {
				writeJSONError(w, http.StatusBadRequest, "limit must be a non-negative integer (0=default) up to 100")
				return
			}
			limit = parsed
		}
		offset := 0
		if o := r.URL.Query().Get("offset"); o != "" {
			parsed, err := strconv.Atoi(o)
			if err != nil || parsed < 0 {
				writeJSONError(w, http.StatusBadRequest, "offset must be a non-negative integer")
				return
			}
			offset = parsed
		}
		searchReq := dto.SearchRequest{
			Project: project,
			Query:   r.URL.Query().Get("query"),
			Type:    r.URL.Query().Get("type"),
			Concept: r.URL.Query().Get("concept"),
			Source:  r.URL.Query().Get("source"),
			Limit:   limit,
			Offset:  offset,
		}
		result, err := client.Search(r.Context(), searchReq)
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("search failed: %v", err))
			return
		}
		writeJSON(w, result)
	})

	// --- GET /version ---
	mux.HandleFunc("/version", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		result, err := client.GetVersion(r.Context())
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to get version: %v", err))
			return
		}
		writeJSON(w, result)
	})

	// --- GET /experiences ---
	mux.HandleFunc("/experiences", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		project := r.URL.Query().Get("project")
		if project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}
		task := r.URL.Query().Get("task")
		if task == "" {
			writeJSONError(w, http.StatusBadRequest, "task is required")
			return
		}
		count := 4
		if c := r.URL.Query().Get("count"); c != "" {
			parsed, err := strconv.Atoi(c)
			if err != nil || parsed < 1 || parsed > 100 {
				writeJSONError(w, http.StatusBadRequest, "count must be an integer between 1 and 100")
				return
			}
			count = parsed
		}
		req := dto.ExperienceRequest{
			Project: project,
			Task:    task,
			Count:   count,
			Source:  r.URL.Query().Get("source"),
			UserID:  r.URL.Query().Get("userId"),
		}
		if concepts := r.URL.Query().Get("requiredConcepts"); concepts != "" {
			parts := strings.Split(concepts, ",")
			// Filter out empty strings from split (e.g., "a,,b" → ["a","b"])
			filtered := make([]string, 0, len(parts))
			for _, p := range parts {
				if p = strings.TrimSpace(p); p != "" {
					filtered = append(filtered, p)
				}
			}
			if len(filtered) > 0 {
				req.RequiredConcepts = filtered
			}
		}
		exps, err := client.RetrieveExperiences(r.Context(), req)
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to retrieve experiences: %v", err))
			return
		}
		writeJSON(w, map[string]any{"experiences": exps, "count": len(exps)})
	})

	// --- GET /iclprompt ---
	mux.HandleFunc("/iclprompt", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		project := r.URL.Query().Get("project")
		if project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}
		task := r.URL.Query().Get("task")
		if task == "" {
			writeJSONError(w, http.StatusBadRequest, "task is required")
			return
		}
		maxChars := 0 // 0 = backend default
		if mc := r.URL.Query().Get("maxChars"); mc != "" {
			parsed, err := strconv.Atoi(mc)
			if err != nil || parsed < 0 {
				writeJSONError(w, http.StatusBadRequest, "maxChars must be a non-negative integer")
				return
			}
			maxChars = parsed
		}
		result, err := client.BuildICLPrompt(r.Context(), dto.ICLPromptRequest{
			Project:  project,
			Task:     task,
			MaxChars: maxChars,
			UserID:   r.URL.Query().Get("userId"),
		})
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to build ICL prompt: %v", err))
			return
		}
		writeJSON(w, result)
	})

	// --- GET /observations ---
	mux.HandleFunc("/observations", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		project := r.URL.Query().Get("project")
		if project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}
		limit := 0 // 0 = backend default
		if l := r.URL.Query().Get("limit"); l != "" {
			parsed, err := strconv.Atoi(l)
			if err != nil || parsed < 0 || parsed > 100 {
				writeJSONError(w, http.StatusBadRequest, "limit must be a non-negative integer (0=default) up to 100")
				return
			}
			limit = parsed
		}
		offset := 0
		if o := r.URL.Query().Get("offset"); o != "" {
			parsed, err := strconv.Atoi(o)
			if err != nil || parsed < 0 {
				writeJSONError(w, http.StatusBadRequest, "offset must be a non-negative integer")
				return
			}
			offset = parsed
		}
		result, err := client.ListObservations(r.Context(), dto.ObservationsRequest{
			Project: project,
			Limit:   limit,
			Offset:  offset,
		})
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to list observations: %v", err))
			return
		}
		writeJSON(w, result)
	})

	// --- POST /observations/batch ---
	mux.HandleFunc("/observations/batch", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodPost) {
			return
		}
		var req struct {
			Ids []string `json:"ids"`
		}
		if err := readJSON(r, &req); err != nil {
			writeJSONError(w, http.StatusBadRequest, "invalid JSON body")
			return
		}
		if len(req.Ids) == 0 {
			writeJSONError(w, http.StatusBadRequest, "ids is required")
			return
		}
		if len(req.Ids) > 100 {
			writeJSONError(w, http.StatusBadRequest, "batch size exceeds maximum of 100")
			return
		}
		result, err := client.GetObservationsByIds(r.Context(), req.Ids)
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to get observations: %v", err))
			return
		}
		writeJSON(w, result)
	})

	// --- GET /projects ---
	mux.HandleFunc("/projects", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		result, err := client.GetProjects(r.Context())
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to get projects: %v", err))
			return
		}
		writeJSON(w, result)
	})

	// --- GET /stats ---
	mux.HandleFunc("/stats", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		project := r.URL.Query().Get("project")
		result, err := client.GetStats(r.Context(), project)
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to get stats: %v", err))
			return
		}
		writeJSON(w, result)
	})

	// --- GET /modes ---
	mux.HandleFunc("/modes", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		result, err := client.GetModes(r.Context())
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to get modes: %v", err))
			return
		}
		writeJSON(w, result)
	})

	// --- GET /settings ---
	mux.HandleFunc("/settings", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		result, err := client.GetSettings(r.Context())
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to get settings: %v", err))
			return
		}
		writeJSON(w, result)
	})

	// --- GET /quality ---
	mux.HandleFunc("/quality", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		project := r.URL.Query().Get("project")
		if project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}
		result, err := client.GetQualityDistribution(r.Context(), project)
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to get quality distribution: %v", err))
			return
		}
		writeJSON(w, result)
	})

	// --- GET /extraction/latest ---
	mux.HandleFunc("/extraction/latest", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		template := r.URL.Query().Get("template")
		if template == "" {
			writeJSONError(w, http.StatusBadRequest, "template is required")
			return
		}
		project := r.URL.Query().Get("project")
		if project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}
		userId := r.URL.Query().Get("userId")
		result, err := client.GetLatestExtraction(r.Context(), project, template, userId)
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to get extraction: %v", err))
			return
		}
		writeJSON(w, result)
	})

	// --- GET /extraction/history ---
	mux.HandleFunc("/extraction/history", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		template := r.URL.Query().Get("template")
		if template == "" {
			writeJSONError(w, http.StatusBadRequest, "template is required")
			return
		}
		project := r.URL.Query().Get("project")
		if project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}
		userId := r.URL.Query().Get("userId")
		limit := 0 // 0 = backend default (10)
		if l := r.URL.Query().Get("limit"); l != "" {
			parsed, err := strconv.Atoi(l)
			if err != nil || parsed < 1 || parsed > 100 {
				writeJSONError(w, http.StatusBadRequest, "limit must be an integer between 1 and 100")
				return
			}
			limit = parsed
		}
		result, err := client.GetExtractionHistory(r.Context(), project, template, userId, limit)
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to get extraction history: %v", err))
			return
		}
		writeJSON(w, result)
	})

	// --- POST /extraction/run ---
	mux.HandleFunc("/extraction/run", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodPost) {
			return
		}
		project := r.URL.Query().Get("projectPath")
		if project == "" {
			writeJSONError(w, http.StatusBadRequest, "projectPath is required")
			return
		}
		if err := client.TriggerExtraction(r.Context(), project); err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to trigger extraction: %v", err))
			return
		}
		writeJSON(w, map[string]string{"status": "extraction triggered"})
	})

	// --- POST /refine ---
	mux.HandleFunc("/refine", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodPost) {
			return
		}
		project := r.URL.Query().Get("project")
		if project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}
		if err := client.TriggerRefinement(r.Context(), project); err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to trigger refinement: %v", err))
			return
		}
		writeJSON(w, map[string]string{"status": "refined"})
	})

	// --- POST /feedback ---
	mux.HandleFunc("/feedback", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodPost) {
			return
		}
		var req struct {
			ObservationId string `json:"observationId"`
			FeedbackType  string `json:"feedbackType"`
			Comment       string `json:"comment,omitempty"`
		}
		if err := readJSON(r, &req); err != nil {
			writeJSONError(w, http.StatusBadRequest, "invalid JSON body")
			return
		}
		if req.ObservationId == "" {
			writeJSONError(w, http.StatusBadRequest, "observationId is required")
			return
		}
		if req.FeedbackType == "" {
			writeJSONError(w, http.StatusBadRequest, "feedbackType is required")
			return
		}
		if err := client.SubmitFeedback(r.Context(), req.ObservationId, req.FeedbackType, req.Comment); err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to submit feedback: %v", err))
			return
		}
		writeJSON(w, map[string]string{"status": "submitted"})
	})

	// --- POST /session/start ---
	mux.HandleFunc("/session/start", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodPost) {
			return
		}
		var req struct {
			SessionID string `json:"session_id"`
			Project   string `json:"project"`
			UserId    string `json:"user_id,omitempty"`
		}
		if err := readJSON(r, &req); err != nil {
			writeJSONError(w, http.StatusBadRequest, "invalid JSON body")
			return
		}
		if req.SessionID == "" {
			writeJSONError(w, http.StatusBadRequest, "session_id is required")
			return
		}
		if req.Project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}
		result, err := client.StartSession(r.Context(), dto.SessionStartRequest{
			SessionID:   req.SessionID,
			ProjectPath: req.Project,
			UserID:      req.UserId,
		})
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to start session: %v", err))
			return
		}
		writeJSON(w, result)
	})

	// --- PATCH /session/user ---
	mux.HandleFunc("/session/user", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodPatch) {
			return
		}
		var req struct {
			SessionId string `json:"session_id"`
			UserId    string `json:"user_id"`
		}
		if err := readJSON(r, &req); err != nil {
			writeJSONError(w, http.StatusBadRequest, "invalid JSON body")
			return
		}
		if req.SessionId == "" {
			writeJSONError(w, http.StatusBadRequest, "session_id is required")
			return
		}
		if req.UserId == "" {
			writeJSONError(w, http.StatusBadRequest, "user_id is required")
			return
		}
		result, err := client.UpdateSessionUserId(r.Context(), req.SessionId, req.UserId)
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to update session user: %v", err))
			return
		}
		writeJSON(w, result)
	})

	// --- POST /observations/create ---
	mux.HandleFunc("/observations/create", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodPost) {
			return
		}
		var req struct {
			Project       string         `json:"project"`
			SessionID     string         `json:"session_id"`
			ToolName      string         `json:"tool_name"`
			ToolInput     any            `json:"tool_input,omitempty"`
			ToolResponse  any            `json:"tool_response,omitempty"`
			PromptNumber  int            `json:"prompt_number,omitempty"`
			Source        string         `json:"source,omitempty"`
			ExtractedData map[string]any `json:"extractedData,omitempty"`
		}
		if err := readJSON(r, &req); err != nil {
			writeJSONError(w, http.StatusBadRequest, "invalid JSON body")
			return
		}
		if req.Project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}
		if req.SessionID == "" {
			writeJSONError(w, http.StatusBadRequest, "session_id is required")
			return
		}
		if req.ToolName == "" {
			writeJSONError(w, http.StatusBadRequest, "tool_name is required")
			return
		}
		if err := client.RecordObservation(r.Context(), dto.ObservationRequest{
			ProjectPath:   req.Project,
			SessionID:     req.SessionID,
			ToolName:      req.ToolName,
			ToolInput:     req.ToolInput,
			ToolResponse:  req.ToolResponse,
			PromptNumber:  req.PromptNumber,
			Source:        req.Source,
			ExtractedData: req.ExtractedData,
		}); err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to record observation: %v", err))
			return
		}
		writeJSON(w, map[string]string{"status": "recorded"})
	})

	// --- PATCH /observations/{id} ---
	mux.HandleFunc("PATCH /observations/{id}", func(w http.ResponseWriter, r *http.Request) {
		id := r.PathValue("id")
		if id == "" {
			writeJSONError(w, http.StatusBadRequest, "observation id is required")
			return
		}
		// Reuse dto.ObservationUpdate directly — pointer fields (*string) with omitempty
		// naturally distinguish absent (nil) from present (non-nil), which is exactly
		// the PATCH semantics we need.
		var update dto.ObservationUpdate
		if err := readJSON(r, &update); err != nil {
			writeJSONError(w, http.StatusBadRequest, "invalid JSON body")
			return
		}
		if err := client.UpdateObservation(r.Context(), id, update); err != nil {
			// SDK validation errors (empty update, missing ID) should be 400, not 500
			if cortexmem.IsValidationError(err) {
				writeJSONError(w, http.StatusBadRequest, err.Error())
				return
			}
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to update observation: %v", err))
			return
		}
		writeJSON(w, map[string]string{"status": "updated"})
	})

	// --- DELETE /observations/{id} ---
	mux.HandleFunc("DELETE /observations/{id}", func(w http.ResponseWriter, r *http.Request) {
		id := r.PathValue("id")
		if id == "" {
			writeJSONError(w, http.StatusBadRequest, "observation id is required")
			return
		}
		if err := client.DeleteObservation(r.Context(), id); err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to delete observation: %v", err))
			return
		}
		w.WriteHeader(http.StatusNoContent)
	})

	// --- POST /ingest/prompt ---
	mux.HandleFunc("/ingest/prompt", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodPost) {
			return
		}
		var req struct {
			Project       string `json:"project"`
			Prompt        string `json:"prompt"`
			Session       string `json:"session_id"`
			PromptNumber  int    `json:"prompt_number,omitempty"`
		}
		if err := readJSON(r, &req); err != nil {
			writeJSONError(w, http.StatusBadRequest, "invalid JSON body")
			return
		}
		if req.Project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}
		if req.Session == "" {
			writeJSONError(w, http.StatusBadRequest, "session_id is required")
			return
		}
		if req.Prompt == "" {
			writeJSONError(w, http.StatusBadRequest, "prompt is required")
			return
		}
		if err := client.RecordUserPrompt(r.Context(), dto.UserPromptRequest{
			ProjectPath: req.Project,
			SessionID:   req.Session,
			PromptText:  req.Prompt,
			PromptNumber: req.PromptNumber,
		}); err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to record prompt: %v", err))
			return
		}
		writeJSON(w, map[string]string{"status": "recorded"})
	})

	// --- POST /ingest/session-end ---
	mux.HandleFunc("/ingest/session-end", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodPost) {
			return
		}
		var req struct {
			Project string `json:"project"`
			Session string `json:"session_id"`
		}
		if err := readJSON(r, &req); err != nil {
			writeJSONError(w, http.StatusBadRequest, "invalid JSON body")
			return
		}
		if req.Project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}
		if req.Session == "" {
			writeJSONError(w, http.StatusBadRequest, "session_id is required")
			return
		}
		if err := client.RecordSessionEnd(r.Context(), dto.SessionEndRequest{
			ProjectPath: req.Project,
			SessionID:   req.Session,
		}); err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to record session end: %v", err))
			return
		}
		writeJSON(w, map[string]string{"status": "ended"})
	})

	// Start HTTP server with timeouts
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	addr := ":" + port
	fmt.Printf("🚀 Go SDK HTTP server starting on %s\n", addr)
	fmt.Println("Endpoints:")
	fmt.Println("  GET    /health              - Health check")
	fmt.Println("  POST   /chat                - Chat with memory (project, message, userId?, maxChars?)")
	fmt.Println("  GET    /search              - Search observations")
	fmt.Println("  GET    /version             - Backend version")
	fmt.Println("  GET    /experiences         - Retrieve experiences")
	fmt.Println("  GET    /iclprompt           - Build ICL prompt")
	fmt.Println("  GET    /observations        - List observations (limit, offset)")
	fmt.Println("  POST   /observations/batch  - Batch get observations by IDs")
	fmt.Println("  GET    /projects            - Get projects")
	fmt.Println("  GET    /stats               - Get stats")
	fmt.Println("  GET    /modes               - Get modes")
	fmt.Println("  GET    /settings            - Get settings")
	fmt.Println("  GET    /quality             - Quality distribution")
	fmt.Println("  GET    /extraction/latest   - Latest extraction result")
	fmt.Println("  GET    /extraction/history  - Extraction history")
	fmt.Println("  POST   /extraction/run      - Trigger extraction")
	fmt.Println("  POST   /refine              - Trigger memory refinement")
	fmt.Println("  POST   /feedback            - Submit observation feedback")
	fmt.Println("  POST   /session/start       - Start/resume session")
	fmt.Println("  PATCH  /session/user        - Update session user ID")
	fmt.Println("  PATCH  /observations/{id}   - Update observation")
	fmt.Println("  DELETE /observations/{id}   - Delete observation")
	fmt.Println("  POST   /observations/create - Record observation")
	fmt.Println("  POST   /ingest/prompt       - Ingest user prompt")
	fmt.Println("  POST   /ingest/session-end  - Ingest session end")

	srv := &http.Server{
		Addr:         addr,
		Handler:      requestLogger(recovery(mux)),
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// Graceful shutdown on SIGINT/SIGTERM
	go func() {
		sigCh := make(chan os.Signal, 1)
		signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
		<-sigCh
		fmt.Println("\n🛑 Shutting down gracefully...")
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		srv.Shutdown(shutdownCtx)
	}()

	log.Fatal(srv.ListenAndServe())
}
