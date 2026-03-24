package genkit

import (
	"context"
	"fmt"

	"github.com/abforce/cortex-ce/cortex-mem-go"
	"github.com/abforce/cortex-ce/cortex-mem-go/dto"
)

// Retriever adapts Cortex CE memory to Genkit's Retriever interface.
type Retriever struct {
	client    cortexmem.Client
	project   string
	source    string
	maxResults int
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

// WithMaxResults sets the maximum number of results.
func WithMaxResults(max int) RetrieverOption {
	return func(r *Retriever) { r.maxResults = max }
}

// NewRetriever creates a new Retriever for Cortex CE memory.
func NewRetriever(client cortexmem.Client, opts ...RetrieverOption) *Retriever {
	r := &Retriever{
		client:     client,
		project:    "default",
		maxResults: 20,
	}
	for _, opt := range opts {
		opt(r)
	}
	return r
}

// Document represents a retrieved document for Genkit.
type Document struct {
	Content string                 `json:"content"`
	Meta    map[string]interface{} `json:"meta"`
}

// Retrieve implements Genkit's Retriever interface.
func (r *Retriever) Retrieve(ctx context.Context, query string) ([]Document, error) {
	// Build search request
	searchReq := dto.SearchRequest{
		Project: r.project,
		Query:  query,
		Source:  r.source,
		Limit:   r.maxResults,
	}

	// Perform search
	result, err := r.client.Search(ctx, searchReq)
	if err != nil {
		return nil, fmt.Errorf("search failed: %w", err)
	}

	// Convert to Genkit documents
	docs := make([]Document, 0, len(result.Observations))
	for _, obs := range result.Observations {
		docs = append(docs, Document{
			Content: obs.Content,
			Meta: map[string]interface{}{
				"id":       obs.ID,
				"type":     obs.Type,
				"source":   obs.Source,
				"concepts": obs.Concepts,
			},
		})
	}

	return docs, nil
}

// Compile-time check
var _ Retriever = Retriever{}
