// Package genkit provides Cortex CE integration for Google's Genkit (Go).
// It wraps the Cortex CE client to implement Genkit's Retriever pattern.
package genkit

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"strings"

	cortexmem "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
	"github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/dto"
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

// WithRetrieverSource sets the source filter.
func WithRetrieverSource(source string) RetrieverOption {
	return func(r *Retriever) { r.source = source }
}

// WithRetrieverCount sets the maximum number of results. Default: 4.
//
// Retrieve clamps a non-positive per-call input.Count back to this value, but
// this constructor value is NOT clamped: with a negative here, the fallback
// resolves to the same negative and it reaches the backend, which answers
// HTTP 200 with an empty list. The existing
// TestRetrieve_NegativeCount_FallsBackToDefault only covers the per-call path
// with a valid constructor count, so it does not exercise this case — see
// P2-36 in docs/drafts/backend-review-findings.md.
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
// project is required and sets the default project path.
func NewRetriever(client cortexmem.Client, project string, opts ...RetrieverOption) *Retriever {
	if client == nil {
		panic("genkit.NewRetriever: client must not be nil")
	}
	r := &Retriever{
		client:  client,
		project: project,
		count:   4,
		logger:  slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelDebug})),
	}
	for _, opt := range opts {
		opt(r)
	}
	return r
}

// Retrieve performs a semantic search and returns Genkit-compatible documents.
// This is designed to be compatible with Genkit Go's Retriever[In, Out] pattern.
//
// Per-call fields on input (project, count, source, userID) override the
// constructor defaults; empty values fall back to them.
//
// Error strategy: matches the Eino adapter — the failure is logged and then
// propagated rather than degraded to an empty result. The LangChainGo adapter
// deliberately does the opposite because it sits inside a prompt chain. Both
// behaviours are intentional; keep them in sync deliberately, not by accident.
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
		// Build content from task + strategy + outcome so the LLM has full context.
		parts := make([]string, 0, 3)
		if exp.Task != "" {
			parts = append(parts, "Task: "+exp.Task)
		}
		if exp.Strategy != "" {
			parts = append(parts, "Strategy: "+exp.Strategy)
		}
		if exp.Outcome != "" {
			parts = append(parts, "Outcome: "+exp.Outcome)
		}
		docs = append(docs, Document{
			Content: strings.Join(parts, "\n"),
			Metadata: map[string]any{
				"id":              exp.ID,
				"task":            exp.Task,
				"quality_score":   exp.QualityScore,
				"reuse_condition": exp.ReuseCondition,
				"created_at":      exp.CreatedAt,
			},
		})
	}

	return RetrieverOutput{Documents: docs}, nil
}
