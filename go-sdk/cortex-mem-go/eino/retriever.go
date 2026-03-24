package eino

import (
	"context"
	"fmt"

	cortexmem "github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

// Retriever adapts Cortex CE memory to Eino's Retriever interface.
// It wraps the Cortex CE client and implements the Eino retriever pattern.
type Retriever struct {
	client  cortexmem.Client
	project string
	source  string
	count   int
	userID  string
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

// WithRetrieverCount sets the number of results to retrieve.
func WithRetrieverCount(n int) RetrieverOption {
	return func(r *Retriever) { r.count = n }
}

// WithRetrieverUserID sets the user ID for user-scoped memory.
func WithRetrieverUserID(userID string) RetrieverOption {
	return func(r *Retriever) { r.userID = userID }
}

// NewRetriever creates a new Retriever for Cortex CE memory.
func NewRetriever(client cortexmem.Client, project string, opts ...RetrieverOption) *Retriever {
	r := &Retriever{
		client:  client,
		project: project,
		count:   4,
	}
	for _, opt := range opts {
		opt(r)
	}
	return r
}

// Retrieve performs a semantic search using Cortex CE and returns results
// as Experience objects compatible with Eino's retriever pattern.
func (r *Retriever) Retrieve(ctx context.Context, query string, opts ...any) ([]dto.Experience, error) {
	experiences, err := r.client.RetrieveExperiences(ctx, dto.ExperienceRequest{
		Task:    query,
		Project: r.project,
		Count:   r.count,
		Source:  r.source,
		UserID:  r.userID,
	})
	if err != nil {
		return nil, fmt.Errorf("cortex-ce retrieve: %w", err)
	}
	return experiences, nil
}
