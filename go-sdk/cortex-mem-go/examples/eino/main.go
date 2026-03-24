package main

import (
	"context"
	"fmt"
	"log"

	"github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/eino"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

func main() {
	// Create a new client
	client := cortexmem.NewClient(
		cortexmem.WithBaseURL("http://127.0.0.1:37777"),
	)
	defer client.Close()

	ctx := context.Background()

	// Create Eino Retriever
	retriever := eino.NewRetriever(client,
		eino.WithRetrieverProject("/tmp/eino-demo"),
	)

	fmt.Println("=== Eino Retriever Demo ===")

	// 1. Start session
	startReq := dto.SessionStartRequest{
		ProjectPath: "/tmp/eino-demo",
	}
	startResp, err := client.StartSession(ctx, startReq)
	if err != nil {
		log.Printf("Failed to start session: %v", err)
	}

	// 2. Record some observations
	observations := []dto.ObservationRequest{
		{
			ProjectPath: "/tmp/eino-demo",
			SessionID:   startResp.SessionID,
			Type:        "fact",
			Content:     "Eino is a Go AI framework",
		},
		{
			ProjectPath: "/tmp/eino-demo",
			SessionID:   startResp.SessionID,
			Type:        "fact",
			Content:     "Cortex CE provides persistent memory for AI",
		},
		{
			ProjectPath: "/tmp/eino-demo",
			SessionID:   startResp.SessionID,
			Type:        "preference",
			Content:     "User prefers Go over Python",
		},
	}

	fmt.Println("Recording observations...")
	for _, obs := range observations {
		if err := client.RecordObservation(ctx, obs); err != nil {
			log.Printf("Failed to record: %v", err)
		}
	}

	// 3. Use Eino Retriever
	fmt.Println("\nRetrieving with Eino Retriever...")
	experiences, err := retriever.Retrieve(ctx, "What is Eino?")
	if err != nil {
		log.Printf("Retrieve failed: %v", err)
	} else {
		fmt.Printf("Found %d experiences:\n", len(experiences))
		for _, exp := range experiences {
			fmt.Printf("  - %s: %s\n", exp.Type, exp.Content)
		}
	}

	fmt.Println("\n✅ Eino demo completed!")
}
