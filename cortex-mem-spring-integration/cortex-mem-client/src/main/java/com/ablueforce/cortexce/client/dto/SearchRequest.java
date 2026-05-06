package com.ablueforce.cortexce.client.dto;

/**
 * Request for searching observations.
 * Calls GET /api/search
 *
 * @param project Project path (required)
 * @param query Search query text (optional, semantic search)
 * @param type Filter by observation type (optional)
 * @param concept Filter by concept (optional)
 * @param source Filter by source attribution (optional, e.g., "tool_result", "user_statement")
 * @param limit Maximum results (default 20, max 100)
 * @param offset Pagination offset (default 0)
 * @param orderBy Sort order (optional, e.g., "created_at_epoch" for newest-first)
 */
public record SearchRequest(
    String project,
    String query,
    String type,
    String concept,
    String source,
    Integer limit,
    Integer offset,
    String orderBy
) {
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Compact convenience constructor: validates project before delegating to the
     * canonical constructor. Fails fast if project is null or blank rather than
     * deferring detection to call time (see {@link CortexMemClientImpl#search}).
     *
     * @param project Project path (required)
     * @throws IllegalArgumentException if project is null or blank
     */
    public SearchRequest {
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("project must not be null or blank");
        }
    }

    public static class Builder {
        private String project;
        private String query;
        private String type;
        private String concept;
        private String source;
        private Integer limit = 20;
        private Integer offset = 0;
        private String orderBy;

        public Builder project(String project) { this.project = project; return this; }
        public Builder query(String query) { this.query = query; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder concept(String concept) { this.concept = concept; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder limit(Integer limit) {
            // limit=0 is intentionally allowed: SDK will omit it from the request,
            // letting the backend use its default. limit<0 throws.
            if (limit != null && limit < 0) {
                throw new IllegalArgumentException("limit must not be negative (got " + limit + ")");
            }
            if (limit != null && limit > 100) {
                throw new IllegalArgumentException("limit must not exceed 100 (got " + limit + ")");
            }
            this.limit = limit;
            return this;
        }
        public Builder offset(Integer offset) {
            if (offset != null && offset < 0) {
                throw new IllegalArgumentException("offset must not be negative (got " + offset + ")");
            }
            // No upper bound: extremely large offsets are naturally rejected by the backend.
            // Matches the behavior of {@link ObservationsRequest.Builder#offset()}.
            this.offset = offset;
            return this;
        }
        public Builder orderBy(String orderBy) { this.orderBy = orderBy; return this; }

        public SearchRequest build() {
            // Fail-fast on missing required project, rather than deferring to the record compact
            // constructor (which would surface a confusing "project must not be null or blank" message).
            if (project == null || project.isBlank()) {
                throw new IllegalArgumentException("project is required (set via project())");
            }
            // Silently nullify blank orderBy to avoid sending empty string to backend.
            String resolvedOrderBy = (orderBy != null && orderBy.isBlank()) ? null : orderBy;
            return new SearchRequest(project, query, type, concept, source, limit, offset, resolvedOrderBy);
        }
    }
}
