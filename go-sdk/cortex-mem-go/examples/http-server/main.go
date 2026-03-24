package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"time"

	"github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

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
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"service": "go-sdk-http-server",
			"status":  "ok",
			"time":    time.Now().Format(time.RFC3339),
		})
	})

	// Chat endpoint
	mux.HandleFunc("/chat", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		var req struct {
			Project string `json:"project"`
			Message string `json:"message"`
		}

		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "Invalid JSON", http.StatusBadRequest)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"response": fmt.Sprintf("Received: %s", req.Message),
			"project":  req.Project,
			"timestamp": time.Now().Format(time.RFC3339),
		})
	})

	// Search endpoint
	mux.HandleFunc("/search", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		project := r.URL.Query().Get("project")
		if project == "" {
			http.Error(w, "project is required", http.StatusBadRequest)
			return
		}
		query := r.URL.Query().Get("query")

		searchReq := dto.SearchRequest{
			Project: project,
			Query:  query,
			Limit:  10,
		}

		result, err := client.Search(r.Context(), searchReq)
		if err != nil {
			http.Error(w, fmt.Sprintf("Search failed: %v", err), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(result)
	})

	// Version endpoint
	mux.HandleFunc("/version", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		version, err := client.GetVersion(r.Context())
		if err != nil {
			http.Error(w, fmt.Sprintf("Failed to get version: %v", err), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(version)
	})

	// Experiences endpoint
	mux.HandleFunc("/experiences", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		project := r.URL.Query().Get("project")
		query := r.URL.Query().Get("query")
		if project == "" {
			http.Error(w, "project is required", http.StatusBadRequest)
			return
		}

		exps, err := client.RetrieveExperiences(r.Context(), dto.ExperienceRequest{
			Project: project,
			Task:    query,
		})
		if err != nil {
			http.Error(w, fmt.Sprintf("Failed: %v", err), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{"experiences": exps, "count": len(exps)})
	})

	// ICL Prompt endpoint
	mux.HandleFunc("/iclprompt", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		project := r.URL.Query().Get("project")
		task := r.URL.Query().Get("task")
		if project == "" || task == "" {
			http.Error(w, "project and task are required", http.StatusBadRequest)
			return
		}

		result, err := client.BuildICLPrompt(r.Context(), dto.ICLPromptRequest{
			Project:  project,
			Task:     task,
			MaxChars: 2000,
		})
		if err != nil {
			http.Error(w, fmt.Sprintf("Failed: %v", err), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(result)
	})

	// Observations list endpoint
	mux.HandleFunc("/observations", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		project := r.URL.Query().Get("project")
		if project == "" {
			http.Error(w, "project is required", http.StatusBadRequest)
			return
		}
		limit := 10

		result, err := client.ListObservations(r.Context(), dto.ObservationsRequest{
			Project: project,
			Limit:   limit,
		})
		if err != nil {
			http.Error(w, fmt.Sprintf("Failed: %v", err), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(result)
	})

	// Projects endpoint
	mux.HandleFunc("/projects", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		result, err := client.GetProjects(r.Context())
		if err != nil {
			http.Error(w, fmt.Sprintf("Failed: %v", err), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(result)
	})

	// Stats endpoint
	mux.HandleFunc("/stats", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		project := r.URL.Query().Get("project")

		result, err := client.GetStats(r.Context(), project)
		if err != nil {
			http.Error(w, fmt.Sprintf("Failed: %v", err), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(result)
	})

	// Modes endpoint
	mux.HandleFunc("/modes", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		result, err := client.GetModes(r.Context())
		if err != nil {
			http.Error(w, fmt.Sprintf("Failed: %v", err), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(result)
	})

	// Settings endpoint
	mux.HandleFunc("/settings", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		result, err := client.GetSettings(r.Context())
		if err != nil {
			http.Error(w, fmt.Sprintf("Failed: %v", err), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(result)
	})

	// Quality distribution endpoint
	mux.HandleFunc("/quality", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		project := r.URL.Query().Get("project")
		if project == "" {
			http.Error(w, "project is required", http.StatusBadRequest)
			return
		}

		result, err := client.GetQualityDistribution(r.Context(), project)
		if err != nil {
			http.Error(w, fmt.Sprintf("Failed: %v", err), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(result)
	})

	// Start HTTP server
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

	log.Fatal(http.ListenAndServe(addr, mux))
}
