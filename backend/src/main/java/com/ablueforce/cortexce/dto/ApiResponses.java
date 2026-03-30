package com.ablueforce.cortexce.dto;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Strong-typed API response DTOs for Swagger documentation.
 *
 * All field names and @JsonProperty values MUST match the exact Map keys
 * currently returned by controllers to preserve API compatibility with
 * WebUI and proxy consumers.
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
        @JsonProperty("error")
        @Schema(description = "Error message", example = "Missing required field: session_id")
        String error
    ) {}

    // =========================================================================
    // Session API Responses (Batch 2)
    // =========================================================================

    @Schema(description = "Session start response with context for Claude Code injection")
    public record StartSessionResponse(
        @JsonProperty("context")
        @Schema(description = "Generated context text for Claude Code injection")
        String context,
        @JsonProperty("updateFiles")
        @Schema(description = "Files to update (CLAUDE.md). MUST stay camelCase for proxy.js compatibility.")
        List<Map<String, String>> updateFiles,
        @JsonProperty("session_db_id")
        @Schema(description = "Database UUID of the session", example = "550e8400-e29b-41d4-a716-446655440000")
        String sessionDbId,
        @JsonProperty("prompt_number")
        @Schema(description = "Current prompt number in the session", example = "1")
        int promptNumber
    ) {}

    @Schema(description = "Session details response")
    public record GetSessionResponse(
        @JsonProperty("session_db_id")
        @Schema(description = "Database UUID of the session")
        String sessionDbId,
        @JsonProperty("content_session_id")
        @Schema(description = "Claude Code content session ID")
        String contentSessionId,
        @JsonProperty("project_path")
        @Schema(description = "Project path for the session")
        String projectPath,
        @JsonProperty("status")
        @Schema(description = "Session status (e.g., active, completed)")
        String status,
        @JsonProperty("started_at")
        @Schema(description = "Session start time (ISO 8601)")
        String startedAt
    ) {}

    @Schema(description = "Session user ID update response")
    public record UpdateSessionUserIdResponse(
        @JsonProperty("status")
        @Schema(description = "Operation status", example = "ok")
        String status,
        @JsonProperty("sessionId")
        @Schema(description = "Content session ID")
        String sessionId,
        @JsonProperty("userId")
        @Schema(description = "Updated user ID")
        String userId
    ) {}

    // =========================================================================
    // Extraction API Responses (Batch 2)
    // =========================================================================

    @Schema(description = "Latest extraction result response")
    public record GetLatestExtractionResponse(
        @JsonProperty("status")
        @Schema(description = "Extraction status: 'ok' or 'not_found'", example = "ok")
        String status,
        @JsonProperty("template")
        @Schema(description = "Template name used for extraction", example = "user-preferences")
        String template,
        @JsonProperty("sessionId")
        @Schema(description = "Content session ID that produced this extraction")
        String sessionId,
        @JsonProperty("extractedData")
        @Schema(description = "Extracted structured data (template-specific fields)")
        Map<String, Object> extractedData,
        @JsonProperty("createdAt")
        @Schema(description = "Creation timestamp (epoch milliseconds)")
        Long createdAt,
        @JsonProperty("observationId")
        @Schema(description = "Observation UUID containing the extraction")
        String observationId,
        @JsonProperty("message")
        @Schema(description = "Status message (present when status is 'not_found')")
        String message
    ) {}

    @Schema(description = "Manual extraction trigger response")
    public record TriggerExtractionResponse(
        @JsonProperty("status")
        @Schema(description = "Extraction status", example = "ok")
        String status,
        @JsonProperty("projectPath")
        @Schema(description = "Project path extraction was run for")
        String projectPath,
        @JsonProperty("message")
        @Schema(description = "Human-readable status message")
        String message
    ) {}

    // =========================================================================
    // Search API Responses (Batch 2)
    // =========================================================================

    @Schema(description = "Semantic search results with metadata")
    public record SearchResponse(
        @JsonProperty("observations")
        @Schema(description = "List of matching observations")
        List<ObservationEntity> observations,
        @JsonProperty("strategy")
        @Schema(description = "Search strategy used (e.g., 'vector', 'text')", example = "vector")
        String strategy,
        @JsonProperty("fell_back")
        @Schema(description = "Whether the search fell back to a secondary strategy", example = "false")
        boolean fellBack,
        @JsonProperty("count")
        @Schema(description = "Number of observations returned", example = "5")
        int count
    ) {}

    @Schema(description = "Search results for file-based observation lookup")
    public record SearchByFileResponse(
        @JsonProperty("observations")
        @Schema(description = "List of matching observations")
        List<ObservationEntity> observations,
        @JsonProperty("count")
        @Schema(description = "Number of observations returned", example = "3")
        int count,
        @JsonProperty("filePath")
        @Schema(description = "File path that was searched for")
        String filePath,
        @JsonProperty("isFolder")
        @Schema(description = "Whether the path was treated as a folder prefix")
        boolean isFolder
    ) {}

    @Schema(description = "Batch observation retrieval results")
    public record BatchGetObservationsResponse(
        @JsonProperty("observations")
        @Schema(description = "List of retrieved observations")
        List<ObservationEntity> observations,
        @JsonProperty("count")
        @Schema(description = "Number of observations returned", example = "5")
        int count
    ) {}

    // =========================================================================
    // Import API Responses (Batch 2)
    // =========================================================================

    @Schema(description = "Bulk import response with per-type statistics")
    public record BulkImportResponse(
        @JsonProperty("success")
        @Schema(description = "Whether the import completed without fatal errors", example = "true")
        boolean success,
        @JsonProperty("stats")
        @Schema(description = "Import statistics broken down by data type")
        ImportStatsData stats
    ) {}

    @Schema(description = "Import statistics for bulk import")
    public record ImportStatsData(
        @JsonProperty("sessionsImported")
        @Schema(description = "Number of sessions successfully imported", example = "5")
        int sessionsImported,
        @JsonProperty("sessionsSkipped")
        @Schema(description = "Number of sessions skipped (duplicates)", example = "2")
        int sessionsSkipped,
        @JsonProperty("observationsImported")
        @Schema(description = "Number of observations successfully imported", example = "10")
        int observationsImported,
        @JsonProperty("observationsSkipped")
        @Schema(description = "Number of observations skipped (duplicates)", example = "3")
        int observationsSkipped,
        @JsonProperty("summariesImported")
        @Schema(description = "Number of summaries successfully imported", example = "4")
        int summariesImported,
        @JsonProperty("summariesSkipped")
        @Schema(description = "Number of summaries skipped (duplicates)", example = "1")
        int summariesSkipped,
        @JsonProperty("promptsImported")
        @Schema(description = "Number of prompts successfully imported", example = "8")
        int promptsImported,
        @JsonProperty("promptsSkipped")
        @Schema(description = "Number of prompts skipped (duplicates)", example = "2")
        int promptsSkipped,
        @JsonProperty("errors")
        @Schema(description = "Total number of errors encountered", example = "0")
        int errors
    ) {}

    @Schema(description = "Session import response")
    public record ImportSessionsResponse(
        @JsonProperty("success")
        @Schema(description = "Whether the import completed", example = "true")
        boolean success,
        @JsonProperty("imported")
        @Schema(description = "Number of sessions imported", example = "5")
        int imported,
        @JsonProperty("skipped")
        @Schema(description = "Number of sessions skipped (duplicates)", example = "2")
        int skipped,
        @JsonProperty("errors")
        @Schema(description = "Number of errors encountered", example = "0")
        int errors,
        @JsonProperty("errorMessages")
        @Schema(description = "List of error messages")
        List<String> errorMessages
    ) {}

    @Schema(description = "Observation import response")
    public record ImportObservationsResponse(
        @JsonProperty("success")
        @Schema(description = "Whether the import completed", example = "true")
        boolean success,
        @JsonProperty("imported")
        @Schema(description = "Number of observations imported", example = "10")
        int imported,
        @JsonProperty("skipped")
        @Schema(description = "Number of observations skipped (duplicates)", example = "3")
        int skipped,
        @JsonProperty("errors")
        @Schema(description = "Number of errors encountered", example = "0")
        int errors,
        @JsonProperty("errorMessages")
        @Schema(description = "List of error messages")
        List<String> errorMessages
    ) {}

    @Schema(description = "Summary import response")
    public record ImportSummariesResponse(
        @JsonProperty("success")
        @Schema(description = "Whether the import completed", example = "true")
        boolean success,
        @JsonProperty("imported")
        @Schema(description = "Number of summaries imported", example = "4")
        int imported,
        @JsonProperty("skipped")
        @Schema(description = "Number of summaries skipped (duplicates)", example = "1")
        int skipped,
        @JsonProperty("errors")
        @Schema(description = "Number of errors encountered", example = "0")
        int errors,
        @JsonProperty("errorMessages")
        @Schema(description = "List of error messages")
        List<String> errorMessages
    ) {}

    @Schema(description = "User prompt import response")
    public record ImportPromptsResponse(
        @JsonProperty("success")
        @Schema(description = "Whether the import completed", example = "true")
        boolean success,
        @JsonProperty("imported")
        @Schema(description = "Number of prompts imported", example = "8")
        int imported,
        @JsonProperty("skipped")
        @Schema(description = "Number of prompts skipped (duplicates)", example = "2")
        int skipped,
        @JsonProperty("errors")
        @Schema(description = "Number of errors encountered", example = "0")
        int errors,
        @JsonProperty("errorMessages")
        @Schema(description = "List of error messages")
        List<String> errorMessages
    ) {}

    // =========================================================================
    // Cursor API Responses (Batch 2)
    // =========================================================================

    @Schema(description = "Cursor project registration response")
    public record RegisterProjectResponse(
        @JsonProperty("success")
        @Schema(description = "Whether registration succeeded", example = "true")
        boolean success,
        @JsonProperty("projectName")
        @Schema(description = "Registered project name", example = "my-project")
        String projectName,
        @JsonProperty("workspacePath")
        @Schema(description = "Workspace path for context file updates", example = "/Users/dev/my-project")
        String workspacePath
    ) {}

    @Schema(description = "Cursor context update response")
    public record UpdateContextResponse(
        @JsonProperty("success")
        @Schema(description = "Whether the context file was updated", example = "true")
        boolean success,
        @JsonProperty("projectName")
        @Schema(description = "Project name")
        String projectName,
        @JsonProperty("workspacePath")
        @Schema(description = "Workspace path where context was written")
        String workspacePath
    ) {}

    // =========================================================================
    // Memory API Responses (Batch 1 refinements)
    // =========================================================================

    @Schema(description = "ICL prompt build response")
    public record ICLPromptResponse(
        @JsonProperty("prompt")
        @Schema(description = "Built ICL prompt text")
        String prompt,
        @JsonProperty("experienceCount")
        @Schema(description = "Number of experiences included in the prompt", example = "4")
        int experienceCount,
        @JsonProperty("maxChars")
        @Schema(description = "Maximum character limit applied", example = "4000")
        int maxChars
    ) {}
}
