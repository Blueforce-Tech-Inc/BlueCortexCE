package eino

import (
	"context"
	"fmt"

	"github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

// Retriever adapts Cortex CE memory to Eino's Retriever interface.
type Retriever struct {
	client    cortexmem.Client
	project   string
	source    string
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

// NewRetriever creates a new Retriever for Cortex CE memory.
func NewRetriever(client cortexmem.Client, opts ...RetrieverOption) *Retriever {
	r := &Retriever{
		client: client,
		project: "default",
	}
	for _, opt := range opts {
		opt(r)
	}
	return r
}

// Retrieve implements Eino's Retriever interface.
// It searches for relevant experiences based on the query.
func (r *Retriever) Retrieve(ctx context.Context, query string, opts ...any) ([]*dto.Experience, error) {
	// Build search request
	searchReq := dto.SearchRequest{
		Project: r.project,
		Query:  query,
		Source:  r.source,
		Limit:   20,
	}

	// Perform search
	result, err := r.client.Search(ctx, searchReq)
	if err != nil {
		return nil, fmt.Errorf("search failed: %w", err)
	}

	// Convert to experiences
	experiences := make([]*dto.Experience, 0, len(result.Observations))
	for i := range result.Observations {
		obs := result.Observations[i]
		experiences = append(experiences, &dto.Experience{
			ObservationID: obs.ID,
			Content:       obs.Content,
			Type:          obs.Type,
			Source:        obs.Source,
			Concepts:      obs.Concepts,
			CreatedAt:     obs.CreatedAt,
		})
	}

	return experiences, nil
}

// Compile-time check for Retriever interface
var _ Retriever = Retriever{}
