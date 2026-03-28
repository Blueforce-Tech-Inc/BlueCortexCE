package main

import (
	"context"
	"fmt"
	"log"
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

	ctx := context.Background()

	// 1. Start session
	fmt.Println("=== Starting session ===")
	startReq := dto.SessionStartRequest{
		ProjectPath: "/tmp/go-demo-project",
		SessionID:   "go-demo-session-" + fmt.Sprintf("%d", time.Now().Unix()),
	}
	startResp, err := client.StartSession(ctx, startReq)
	if err != nil {
		log.Fatalf("Failed to start session: %v", err)
	}
	fmt.Printf("Session started: %s\n", startResp.SessionID)

	// 2. Record observation with V14 features (source + extractedData)
	fmt.Println("\n=== Recording observation (V14: source + extractedData) ===")
	obsReq := dto.ObservationRequest{
		ProjectPath: "/tmp/go-demo-project",
		SessionID:   startResp.SessionID,
		ToolName:    "demo_tool",
		ToolInput:   map[string]any{"action": "demo"},
		Source:      "manual",
		ExtractedData: map[string]any{
			"category": "demo",
			"priority": "high",
		},
	}
	if err := client.RecordObservation(ctx, obsReq); err != nil {
		log.Printf("Failed to record observation: %v", err)
	} else {
		fmt.Println("Observation recorded successfully (with source='manual' and extractedData)")
	}

	// Allow time for fire-and-forget ingestion to complete
	time.Sleep(500 * time.Millisecond)

	// 3. Search
	fmt.Println("\n=== Searching ===")
	searchReq := dto.SearchRequest{
		Project: "/tmp/go-demo-project",
		Query:   "demo",
		Limit:   5,
	}
	searchResp, err := client.Search(ctx, searchReq)
	if err != nil {
		log.Printf("Failed to search: %v", err)
	} else {
		fmt.Printf("Found %d results (strategy: %s)\n", searchResp.Count, searchResp.Strategy)
		for _, obs := range searchResp.Observations {
			fmt.Printf("  - %s: %s\n", obs.Type, obs.Content)
		}
	}

	// 4. Search with source filter (V14)
	fmt.Println("\n=== Searching with source filter (V14) ===")
	filteredSearch := dto.SearchRequest{
		Project: "/tmp/go-demo-project",
		Query:   "demo",
		Source:  "manual",
		Limit:   5,
	}
	filteredResp, err := client.Search(ctx, filteredSearch)
	if err != nil {
		log.Printf("Failed filtered search: %v", err)
	} else {
		fmt.Printf("Source-filtered results: %d (strategy: %s)\n", filteredResp.Count, filteredResp.Strategy)
	}

	// 5. List observations
	fmt.Println("\n=== Listing observations ===")
	listReq := dto.ObservationsRequest{
		Project: "/tmp/go-demo-project",
		Limit:   10,
	}
	listResp, err := client.ListObservations(ctx, listReq)
	if err != nil {
		log.Printf("Failed to list observations: %v", err)
	} else {
		fmt.Printf("Total observations: %d\n", listResp.Total)
	}

	// 6. Get version
	fmt.Println("\n=== Getting version ===")
	version, err := client.GetVersion(ctx)
	if err != nil {
		log.Printf("Failed to get version: %v", err)
	} else {
		fmt.Printf("Backend version: %v\n", version)
	}

	// 7. Health check
	fmt.Println("\n=== Health check ===")
	if err := client.HealthCheck(ctx); err != nil {
		log.Printf("Health check failed: %v", err)
	} else {
		fmt.Println("Health check passed!")
	}

	fmt.Println("\n✅ Go SDK basic demo completed!")
}
