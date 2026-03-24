package main

import (
	"context"
	"fmt"
	"log"

	"github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/genkit"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

func main() {
	// Create a new client
	client := cortexmem.NewClient(
		cortexmem.WithBaseURL("http://127.0.0.1:37777"),
	)
	defer client.Close()

	ctx := context.Background()

	// Create Genkit Retriever
	retriever := genkit.NewRetriever(client,
		genkit.WithRetrieverProject("/tmp/genkit-demo"),
		genkit.WithMaxResults(10),
	)

	fmt.Println("=== Genkit Retriever Demo ===")

	// 1. Start session
	startReq := dto.SessionStartRequest{
		ProjectPath: "/tmp/genkit-demo",
	}
	startResp, err := client.StartSession(ctx, startReq)
	if err != nil {
		log.Fatalf("Failed to start session: %v", err)
	}

	// 2. Record some observations
	fmt.Println("Recording observations...")
	if err := client.RecordObservation(ctx, dto.ObservationRequest{
		ProjectPath:  "/tmp/genkit-demo",
		SessionID:    startResp.SessionID,
		ToolName:     "fact_record",
		ToolInput:    map[string]any{"topic": "Genkit"},
		ToolResponse: map[string]any{"fact": "Genkit is Firebase's AI framework for building AI-powered apps"},
	}); err != nil {
		log.Printf("Failed to record: %v", err)
	}

	if err := client.RecordObservation(ctx, dto.ObservationRequest{
		ProjectPath:  "/tmp/genkit-demo",
		SessionID:    startResp.SessionID,
		ToolName:     "fact_record",
		ToolInput:    map[string]any{"topic": "Genkit languages"},
		ToolResponse: map[string]any{"fact": "Genkit supports Go and JavaScript"},
	}); err != nil {
		log.Printf("Failed to record: %v", err)
	}

	// 3. Use Genkit Retriever
	fmt.Println("\nRetrieving with Genkit Retriever...")
	docs, err := retriever.Retrieve(ctx, "What is Genkit?")
	if err != nil {
		log.Printf("Retrieve failed: %v", err)
	} else {
		fmt.Printf("Found %d documents:\n", len(docs))
		for _, doc := range docs {
			fmt.Printf("  - Content: %s\n", doc.Content)
			fmt.Printf("    Meta: %v\n", doc.Meta)
		}
	}

	fmt.Println("\n✅ Genkit demo completed!")
}
