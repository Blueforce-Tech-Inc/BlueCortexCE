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

	"github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

// writeJSON encodes v as JSON and sets the Content-Type header.
// Returns true on success; on failure it writes a JSON error response.
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

	// Health endpoint
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		writeJSON(w, map[string]any{
			"service": "go-sdk-http-server",
			"status":  "ok",
			"time":    time.Now().Format(time.RFC3339),
		})
	})

	// Chat endpoint
	mux.HandleFunc("/chat", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodPost) {
			return
		}

		var req struct {
			Project string `json:"project"`
			Message string `json:"message"`
		}

		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
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

	// Search endpoint
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

	// Version endpoint
	mux.HandleFunc("/version", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}

		version, err := client.GetVersion(r.Context())
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed to get version: %v", err))
			return
		}

		writeJSON(w, version)
	})

	// Experiences endpoint
	mux.HandleFunc("/experiences", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}

		project := r.URL.Query().Get("project")
		query := r.URL.Query().Get("query")
		if project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}

		exps, err := client.RetrieveExperiences(r.Context(), dto.ExperienceRequest{
			Project: project,
			Task:    query,
		})
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed: %v", err))
			return
		}

		writeJSON(w, map[string]any{"experiences": exps, "count": len(exps)})
	})

	// ICL Prompt endpoint
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
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed: %v", err))
			return
		}

		writeJSON(w, result)
	})

	// Observations list endpoint
	mux.HandleFunc("/observations", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}

		project := r.URL.Query().Get("project")
		if project == "" {
			writeJSONError(w, http.StatusBadRequest, "project is required")
			return
		}
		limit := 10

		result, err := client.ListObservations(r.Context(), dto.ObservationsRequest{
			Project: project,
			Limit:   limit,
		})
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed: %v", err))
			return
		}

		writeJSON(w, result)
	})

	// Projects endpoint
	mux.HandleFunc("/projects", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}

		result, err := client.GetProjects(r.Context())
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed: %v", err))
			return
		}

		writeJSON(w, result)
	})

	// Stats endpoint
	mux.HandleFunc("/stats", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}

		project := r.URL.Query().Get("project")

		result, err := client.GetStats(r.Context(), project)
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed: %v", err))
			return
		}

		writeJSON(w, result)
	})

	// Modes endpoint
	mux.HandleFunc("/modes", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}

		result, err := client.GetModes(r.Context())
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed: %v", err))
			return
		}

		writeJSON(w, result)
	})

	// Settings endpoint
	mux.HandleFunc("/settings", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}

		result, err := client.GetSettings(r.Context())
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed: %v", err))
			return
		}

		writeJSON(w, result)
	})

	// Quality distribution endpoint
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
			writeJSONError(w, http.StatusInternalServerError, fmt.Sprintf("failed: %v", err))
			return
		}

		writeJSON(w, result)
	})

	// Start HTTP server with timeouts
	addr := ":8080"
	fmt.Printf("🚀 Go SDK HTTP server starting on %s\n", addr)
	fmt.Println("Endpoints:")
	fmt.Println("  GET  /health        - Health check")
	fmt.Println("  POST /chat          - Chat with memory")
	fmt.Println("  GET  /search        - Search observations")
	fmt.Println("  GET  /version       - Backend version")
	fmt.Println("  GET  /experiences   - Retrieve experiences")
	fmt.Println("  GET  /iclprompt     - Build ICL prompt")
	fmt.Println("  GET  /observations  - List observations")
	fmt.Println("  GET  /projects      - Get projects")
	fmt.Println("  GET  /stats         - Get stats")
	fmt.Println("  GET  /modes         - Get modes")
	fmt.Println("  GET  /settings      - Get settings")
	fmt.Println("  GET  /quality       - Quality distribution")

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
