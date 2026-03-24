package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"time"

	"github.com/abforce/cortex-ce/cortex-mem-go"
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
		query := r.URL.Query().Get("query")

		searchReq := cortexmem.SearchRequest{
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

	// Start HTTP server
	addr := ":8080"
	fmt.Printf("🚀 Go SDK HTTP server starting on %s\n", addr)
	fmt.Println("Endpoints:")
	fmt.Println("  GET  /health   - Health check")
	fmt.Println("  POST /chat     - Chat with memory")
	fmt.Println("  GET  /search   - Search observations")
	fmt.Println("  GET  /version  - Backend version")

	log.Fatal(http.ListenAndServe(addr, mux))
}
