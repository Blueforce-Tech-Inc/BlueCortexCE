package main

import (
	"context"
	"fmt"
	"log"
	"time"

	"github.com/abforce/cortex-ce/cortex-mem-go"
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

	// 1. Save context (simulate conversation)
	fmt.Println("Saving context...")
	inputs := map[string]any{"input": "What is your favorite programming language?"}
	outputs := map[string]any{"output": "I prefer Go because it's fast and concurrent."}
	if err := memory.SaveContext(ctx, inputs, outputs); err != nil {
		log.Printf("Failed to save context: %v", err)
	}

	// Allow time for fire-and-forget ingestion to complete
	time.Sleep(500 * time.Millisecond)

	// 2. Load memory variables
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
