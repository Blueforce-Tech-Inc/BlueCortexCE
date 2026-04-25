package com.ablueforce.cortexce.client.dto;

/**
 * Request for listing observations with pagination.
 * Calls GET /api/observations
 *
 * @param project Project path (optional, null for all projects)
 * @param offset Pagination offset (default 0)
 * @param limit Maximum results (default 20, max 100)
 */
public record ObservationsRequest(
    String project,
    Integer offset,
    Integer limit
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String project;
        private Integer offset = 0;
        private Integer limit = 20;

        public Builder project(String project) { this.project = project; return this; }
        public Builder offset(Integer offset) {
            if (offset != null && offset < 0) {
                throw new IllegalArgumentException("offset must not be negative (got " + offset + ")");
            }
            this.offset = offset;
            return this;
        }
        public Builder limit(Integer limit) {
            // limit=0 is intentionally allowed: SDK will omit it from the request,
            // letting the backend use its default.
            if (limit != null && limit < 0) {
                throw new IllegalArgumentException("limit must not be negative (got " + limit + ")");
            }
            if (limit != null && limit > 100) {
                throw new IllegalArgumentException("limit must not exceed 100 (got " + limit + ")");
            }
            this.limit = limit;
            return this;
        }

        public ObservationsRequest build() {
            return new ObservationsRequest(project, offset, limit);
        }
    }
}
