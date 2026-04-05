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
     * Convenience constructor with required fields only.
     */
    public SearchRequest(String project) {
        this(project, null, null, null, null, 20, 0, null);
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
        public Builder limit(Integer limit) { this.limit = limit; return this; }
        public Builder offset(Integer offset) { this.offset = offset; return this; }
        public Builder orderBy(String orderBy) { this.orderBy = orderBy; return this; }

        public SearchRequest build() {
            return new SearchRequest(project, query, type, concept, source, limit, offset, orderBy);
        }
    }
}
