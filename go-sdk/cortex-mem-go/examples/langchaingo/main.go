package main

import (
	"context"
	"fmt"
	"log"
	"time"

	"github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
	"github.com/abforce/cortex-ce/cortex-mem-go/langchaingo"
)

func main() {
	// Create a new client
	client := cortexmem.NewClient(
		cortexmem.WithBaseURL("http://127.0.0.1:37777"),
	)
	defer client.Close()

	ctx := context.Background()

	// Create LangChainGo Memory
	memory := langchaingo.NewMemory(client, "/tmp/langchaingo-demo")

	fmt.Println("=== LangChainGo Memory Demo ===")

	// 1. Start session and record observations via the client directly.
	//    Note: Memory.SaveContext is a no-op — Cortex CE captures experiences
	//    through its own session lifecycle (RecordObservation), not via LangChain's
	//    SaveContext. This is intentional: LangChainGo Memory is only used for
	//    retrieval (LoadMemoryVariables), not for persistence.
	fmt.Println("Starting session...")
	startResp, err := client.StartSession(ctx, dto.SessionStartRequest{
		ProjectPath: "/tmp/langchaingo-demo",
	})
	if err != nil {
		log.Fatalf("Failed to start session: %v", err)
	}

	fmt.Println("Recording observations...")
	if err := client.RecordObservation(ctx, dto.ObservationRequest{
		ProjectPath:  "/tmp/langchaingo-demo",
		SessionID:    startResp.SessionID,
		ToolName:     "fact_record",
		ToolInput:    map[string]any{"topic": "programming"},
		ToolResponse: map[string]any{"fact": "I prefer Go because it's fast and concurrent."},
		Source:       "user_input",
		ExtractedData: map[string]any{"language": "Go"},
	}); err != nil {
		log.Printf("Failed to record observation: %v", err)
	}

	// SaveContext is a no-op (see langchaingo.Memory.SaveContext docs).
	// It exists only for LangChainGo interface compatibility.
	memory.SaveContext(ctx,
		map[string]any{"input": "What is your favorite programming language?"},
		map[string]any{"output": "I prefer Go because it's fast and concurrent."},
	)

	// Allow time for fire-and-forget ingestion to complete
	time.Sleep(500 * time.Millisecond)

	// 2. Load memory variables (retrieval via ICL prompt)
	fmt.Println("\nLoading memory variables...")
	loaded, err := memory.LoadMemoryVariables(ctx, map[string]any{"input": "programming"})
	if err != nil {
		log.Printf("Failed to load memory: %v", err)
	} else {
		fmt.Printf("History: %s\n", loaded["history"])
	}

	// 3. Get memory variables
	fmt.Println("\nMemory variables:", memory.MemoryVariables(ctx))

	fmt.Println("\n✅ LangChainGo Memory demo completed!")
}
