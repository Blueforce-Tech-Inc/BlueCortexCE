package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
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

// readJSON decodes the request body into dst.
func readJSON(r *http.Request, dst any) error {
	return json.NewDecoder(r.Body).Decode(dst)
}

// checkMethod verifies the request method matches expected; returns false if not.
func checkMethod(w http.ResponseWriter, r *http.Request, expected string) bool {
	if r.Method != expected {
		writeJSONError(w, http.StatusMethodNotAllowed, "method not allowed: expected "+expected)
		return false
	}
	return true
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
			Project string `json:"project"`
			Message string `json:"message"`
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
		writeJSON(w, map[string]any{
			"response":  fmt.Sprintf("Received: %s", req.Message),
			"project":   req.Project,
			"timestamp": time.Now().Format(time.RFC3339),
		})
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
		query := r.URL.Query().Get("query")
		searchReq := dto.SearchRequest{
			Project: project,
			Query:   query,
			Limit:   10,
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
		exps, err := client.RetrieveExperiences(r.Context(), dto.ExperienceRequest{
			Project: project,
			Task:    task,
		})
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
		result, err := client.BuildICLPrompt(r.Context(), dto.ICLPromptRequest{
			Project:  project,
			Task:     task,
			MaxChars: 2000,
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
		result, err := client.ListObservations(r.Context(), dto.ObservationsRequest{
			Project: project,
			Limit:   10,
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
		limit := 5
		if l := r.URL.Query().Get("limit"); l != "" {
			fmt.Sscanf(l, "%d", &limit)
		}
		result, err := client.GetExtractionHistory(r.Context(), project, template, userId, limit)
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to get extraction history: %v", err))
			return
		}
		writeJSON(w, result)
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
			ObservationId string `json:"observation_id"`
			FeedbackType  string `json:"feedback_type"`
			Comment       string `json:"comment,omitempty"`
		}
		if err := readJSON(r, &req); err != nil {
			writeJSONError(w, http.StatusBadRequest, "invalid JSON body")
			return
		}
		if req.ObservationId == "" {
			writeJSONError(w, http.StatusBadRequest, "observation_id is required")
			return
		}
		if req.FeedbackType == "" {
			writeJSONError(w, http.StatusBadRequest, "feedback_type is required")
			return
		}
		if err := client.SubmitFeedback(r.Context(), req.ObservationId, req.FeedbackType, req.Comment); err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to submit feedback: %v", err))
			return
		}
		writeJSON(w, map[string]string{"status": "submitted"})
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

	// --- PATCH /observation/patch ---
	mux.HandleFunc("/observation/patch", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodPatch) {
			return
		}
		var req struct {
			Id      string `json:"id"`
			Title   string `json:"title,omitempty"`
			Content string `json:"content,omitempty"`
			Source  string `json:"source,omitempty"`
		}
		if err := readJSON(r, &req); err != nil {
			writeJSONError(w, http.StatusBadRequest, "invalid JSON body")
			return
		}
		if req.Id == "" {
			writeJSONError(w, http.StatusBadRequest, "id is required")
			return
		}
		update := dto.ObservationUpdate{}
		if req.Title != "" {
			update.Title = &req.Title
		}
		if req.Content != "" {
			update.Content = &req.Content
		}
		if req.Source != "" {
			update.Source = &req.Source
		}
		if err := client.UpdateObservation(r.Context(), req.Id, update); err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to update observation: %v", err))
			return
		}
		writeJSON(w, map[string]string{"status": "updated"})
	})

	// --- DELETE /observation/delete ---
	mux.HandleFunc("/observation/delete", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodDelete) {
			return
		}
		id := r.URL.Query().Get("id")
		if id == "" {
			writeJSONError(w, http.StatusBadRequest, "id is required")
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
			Project string `json:"project"`
			Prompt  string `json:"prompt"`
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
		if req.Prompt == "" {
			writeJSONError(w, http.StatusBadRequest, "prompt is required")
			return
		}
		if err := client.RecordUserPrompt(r.Context(), dto.UserPromptRequest{
			ProjectPath: req.Project,
			SessionID:   req.Session,
			PromptText:  req.Prompt,
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
	addr := ":8080"
	fmt.Printf("🚀 Go SDK HTTP server starting on %s\n", addr)
	fmt.Println("Endpoints:")
	fmt.Println("  GET    /health              - Health check")
	fmt.Println("  POST   /chat                - Chat with memory")
	fmt.Println("  GET    /search              - Search observations")
	fmt.Println("  GET    /version             - Backend version")
	fmt.Println("  GET    /experiences         - Retrieve experiences")
	fmt.Println("  GET    /iclprompt           - Build ICL prompt")
	fmt.Println("  GET    /observations        - List observations")
	fmt.Println("  POST   /observations/batch  - Batch get observations by IDs")
	fmt.Println("  GET    /projects            - Get projects")
	fmt.Println("  GET    /stats               - Get stats")
	fmt.Println("  GET    /modes               - Get modes")
	fmt.Println("  GET    /settings            - Get settings")
	fmt.Println("  GET    /quality             - Quality distribution")
	fmt.Println("  GET    /extraction/latest   - Latest extraction result")
	fmt.Println("  GET    /extraction/history  - Extraction history")
	fmt.Println("  POST   /refine              - Trigger memory refinement")
	fmt.Println("  POST   /feedback            - Submit observation feedback")
	fmt.Println("  PATCH  /session/user        - Update session user ID")
	fmt.Println("  PATCH  /observation/patch   - Update observation")
	fmt.Println("  DELETE /observation/delete  - Delete observation")
	fmt.Println("  POST   /ingest/prompt       - Ingest user prompt")
	fmt.Println("  POST   /ingest/session-end  - Ingest session end")

	srv := &http.Server{
		Addr:         addr,
		Handler:      mux,
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
