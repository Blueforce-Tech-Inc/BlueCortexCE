// Package genkit provides Cortex CE integration for Google's Genkit (Go).
// Status: placeholder — waiting for Genkit Go API stabilization.
package genkit

import (
	"context"
	"fmt"
	"log/slog"
	"os"

	cortexmem "github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

// RetrieverInput is the input for Cortex CE retriever.
type RetrieverInput struct {
	Query   string
	Project string
	Count   int
	Source  string
	UserID  string
}

// Document represents a retrieved document for Genkit.
type Document struct {
	Content  string         `json:"content"`
	Metadata map[string]any `json:"metadata"`
}

// RetrieverOutput is the output from Cortex CE retriever.
type RetrieverOutput struct {
	Documents []Document `json:"documents"`
}

// Retriever adapts Cortex CE memory to Genkit's Retriever pattern.
type Retriever struct {
	client  cortexmem.Client
	project string
	source  string
	count   int
	userID  string
	logger  cortexmem.Logger
}

// RetrieverOption configures the Retriever.
type RetrieverOption func(*Retriever)

// WithRetrieverProject sets the project path.
func WithRetrieverProject(project string) RetrieverOption {
	return func(r *Retriever) { r.project = project }
}

// WithRetrieverSource sets the source filter.
func WithRetrieverSource(source string) RetrieverOption {
	return func(r *Retriever) { r.source = source }
}

// WithRetrieverCount sets the maximum number of results.
func WithRetrieverCount(n int) RetrieverOption {
	return func(r *Retriever) { r.count = n }
}

// WithRetrieverUserID sets the user ID for user-scoped memory.
func WithRetrieverUserID(userID string) RetrieverOption {
	return func(r *Retriever) { r.userID = userID }
}

// WithRetrieverLogger sets a custom logger for error reporting.
// The logger must implement the cortexmem.Logger interface (compatible with *slog.Logger).
func WithRetrieverLogger(l cortexmem.Logger) RetrieverOption {
	return func(r *Retriever) { r.logger = l }
}

// NewRetriever creates a new Retriever for Cortex CE memory.
func NewRetriever(client cortexmem.Client, opts ...RetrieverOption) *Retriever {
	if client == nil {
		panic("genkit.NewRetriever: client must not be nil")
	}
	r := &Retriever{
		client: client,
		count:  4,
		logger: slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelDebug})),
	}
	for _, opt := range opts {
		opt(r)
	}
	return r
}

// Retrieve performs a semantic search and returns Genkit-compatible documents.
// This is designed to be compatible with Genkit Go's Retriever[In, Out] pattern.
func (r *Retriever) Retrieve(ctx context.Context, input RetrieverInput) (RetrieverOutput, error) {
	if input.Query == "" {
		return RetrieverOutput{}, nil
	}
	project := input.Project
	if project == "" {
		project = r.project
	}
	count := input.Count
	if count <= 0 {
		count = r.count
	}
	source := input.Source
	if source == "" {
		source = r.source
	}
	userID := input.UserID
	if userID == "" {
		userID = r.userID
	}

	experiences, err := r.client.RetrieveExperiences(ctx, dto.ExperienceRequest{
		Task:    input.Query,
		Project: project,
		Count:   count,
		Source:  source,
		UserID:  userID,
	})
	if err != nil {
		r.logger.Warn("RetrieveExperiences failed", "project", project, "error", err)
		return RetrieverOutput{}, fmt.Errorf("cortex-ce retrieve: %w", err)
	}

	docs := make([]Document, 0, len(experiences))
	for _, exp := range experiences {
		docs = append(docs, Document{
			Content: exp.Strategy + "\n" + exp.Outcome,
			Metadata: map[string]any{
				"id":             exp.ID,
				"task":           exp.Task,
				"qualityScore":   exp.QualityScore,
				"reuseCondition": exp.ReuseCondition,
				"createdAt":      exp.CreatedAt,
			},
		})
	}

	return RetrieverOutput{Documents: docs}, nil
}
