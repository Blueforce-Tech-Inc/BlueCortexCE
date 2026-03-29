package com.ablueforce.cortexce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Strong-typed API response DTOs for Swagger documentation.
 */
public final class ApiResponses {

    private ApiResponses() {}

    // =========================================================================
    // Context API Responses
    // =========================================================================

    @Schema(description = "Context injection response with generated context and optional CLAUDE.md updates")
    public record ContextInjectResponse(
        @JsonProperty("context")
        @Schema(description = "Generated context text for stdout injection", example = "## Recent Observations\n...")
        String context,
        @JsonProperty("updateFiles")
        @Schema(description = "Files to update (CLAUDE.md paths and contents). MUST stay camelCase for proxy.js compatibility.")
        List<UpdateFileEntry> updateFiles
    ) {}

    @Schema(description = "A file to update (CLAUDE.md)")
    public record UpdateFileEntry(
        @JsonProperty("path")
        @Schema(description = "Absolute file path", example = "/Users/dev/project/CLAUDE.md")
        String path,
        @JsonProperty("content")
        @Schema(description = "New file content")
        String content
    ) {}

    @Schema(description = "Recent session context for a project")
    public record RecentContextResponse(
        @JsonProperty("content")
        @Schema(description = "Formatted content blocks for display")
        List<ContentBlock> content,
        @JsonProperty("count")
        @Schema(description = "Number of sessions returned", example = "3")
        Integer count
    ) {}

    @Schema(description = "A content block (text or other)")
    public record ContentBlock(
        @JsonProperty("type")
        @Schema(description = "Block type", example = "text")
        String type,
        @JsonProperty("text")
        @Schema(description = "Block text content")
        String text
    ) {}

    @Schema(description = "Prior messages from the last completed session")
    public record PriorMessagesResponse(
        @JsonProperty("user_message")
        @Schema(description = "User's last prompt from prior session", example = "How do I optimize this query?")
        String userMessage,
        @JsonProperty("assistant_message")
        @Schema(description = "Assistant's last response from prior session", example = "You can optimize by adding an index...")
        String assistantMessage
    ) {}

    @Schema(description = "Context generation response")
    public record GenerateContextResponse(
        @JsonProperty("context")
        @Schema(description = "Generated context text")
        String context
    ) {}

    // =========================================================================
    // Error Response
    // =========================================================================

    @Schema(description = "Standard error response")
    public record ErrorResponse(
        @Schema(description = "Error message", example = "Missing required field: session_id")
        String error
    ) {}
}
