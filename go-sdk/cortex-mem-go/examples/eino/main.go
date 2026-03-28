package main

import (
	"context"
	"fmt"
	"log"
	"time"

	"github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
	"github.com/abforce/cortex-ce/cortex-mem-go/eino"
)

func main() {
	// Create a new client
	client := cortexmem.NewClient(
		cortexmem.WithBaseURL("http://127.0.0.1:37777"),
	)
	defer client.Close()

	ctx := context.Background()

	// Create Eino Retriever
	retriever := eino.NewRetriever(client, "/tmp/eino-demo")

	fmt.Println("=== Eino Retriever Demo ===")

	// 1. Start session
	startReq := dto.SessionStartRequest{
		ProjectPath: "/tmp/eino-demo",
	}
	startResp, err := client.StartSession(ctx, startReq)
	if err != nil {
		log.Fatalf("Failed to start session: %v", err)
	}

	// 2. Record some observations
	observations := []dto.ObservationRequest{
		{
			ProjectPath:  "/tmp/eino-demo",
			SessionID:    startResp.SessionID,
			ToolName:     "fact_record",
			ToolInput:    map[string]any{"topic": "Eino"},
			ToolResponse: map[string]any{"fact": "Eino is a Go AI framework"},
			Source:       "documentation",
		},
		{
			ProjectPath:   "/tmp/eino-demo",
			SessionID:     startResp.SessionID,
			ToolName:      "fact_record",
			ToolInput:     map[string]any{"topic": "Cortex CE"},
			ToolResponse:  map[string]any{"fact": "Cortex CE provides persistent memory for AI"},
			Source:        "documentation",
			ExtractedData: map[string]any{"category": "architecture"},
		},
		{
			ProjectPath:  "/tmp/eino-demo",
			SessionID:    startResp.SessionID,
			ToolName:     "preference_record",
			ToolInput:    map[string]any{"topic": "language"},
			ToolResponse: map[string]any{"preference": "User prefers Go over Python"},
			Source:       "user_input",
			ExtractedData: map[string]any{
				"language": "Go",
				"reason":   "concurrency",
			},
		},
	}

	fmt.Println("Recording observations...")
	for _, obs := range observations {
		if err := client.RecordObservation(ctx, obs); err != nil {
			log.Printf("Failed to record: %v", err)
		}
	}

	// Allow time for fire-and-forget ingestion to complete
	time.Sleep(500 * time.Millisecond)

	// 3. Use Eino Retriever
	fmt.Println("\nRetrieving with Eino Retriever...")
	experiences, err := retriever.Retrieve(ctx, "What is Eino?")
	if err != nil {
		log.Printf("Retrieve failed: %v", err)
	} else {
		fmt.Printf("Found %d experiences:\n", len(experiences))
		for _, exp := range experiences {
			fmt.Printf("  - %s: %s\n", exp.Task, exp.Outcome)
		}
	}

	fmt.Println("\n✅ Eino demo completed!")
}
