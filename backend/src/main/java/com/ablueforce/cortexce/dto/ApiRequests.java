package com.ablueforce.cortexce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * Request DTOs for API endpoints.
 * <p>
 * IMPORTANT: Each field uses @JsonProperty to ensure exact wire format matching.
 * The backend uses SNAKE_CASE globally, but some fields are camelCase in the wire format.
 * DO NOT remove @JsonProperty annotations — they guarantee backward compatibility.
 */
public class ApiRequests {

    // ==================== Session ====================

    @Schema(description = "Session start request")
    public record SessionStartRequest(
        @Schema(description = "Unique session identifier", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("session_id") String sessionId,
        @Schema(description = "Absolute project path")
        @JsonProperty("project_path") String projectPath,
        @Schema(description = "Alternative project path (alias for project_path)")
        @JsonProperty("cwd") String cwd,
        @Schema(description = "User ID for multi-user isolation")
        @JsonProperty("user_id") String userId,
        @Schema(description = "Comma-separated project paths for worktree support")
        @JsonProperty("projects") String projects,
        @Schema(description = "Flag indicating worktree mode (rare, internal use)")
        @JsonProperty("is_worktree") Boolean isWorktree,
        @Schema(description = "Parent project path for worktree (rare, internal use)")
        @JsonProperty("parent_project") String parentProject
    ) {}

    @Schema(description = "Update session user ID request")
    public record SessionUserUpdateRequest(
        @Schema(description = "New user identifier", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("user_id") String userId
    ) {}

    // ==================== Ingestion ====================

    @Schema(description = "Tool use event for observation recording")
    public record ToolUseRequest(
        @Schema(description = "Session ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("session_id") String sessionId,
        @Schema(description = "Project path")
        @JsonProperty("cwd") String cwd,
        @Schema(description = "Tool name (e.g., 'Read', 'Edit', 'Bash')", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("tool_name") String toolName,
        @Schema(description = "Tool input JSON")
        @JsonProperty("tool_input") Object toolInput,
        @Schema(description = "Tool response JSON")
        @JsonProperty("tool_response") Object toolResponse,
        @Schema(description = "Source attribution (e.g., 'manual', 'tool_result')")
        @JsonProperty("source") String source,
        @Schema(description = "Structured extracted data (JSON object)")
        @JsonProperty("extractedData") Map<String, Object> extractedData,
        @Schema(description = "Prompt number for ordering")
        @JsonProperty("prompt_number") Integer promptNumber
    ) {}

    @Schema(description = "Session end event")
    public record SessionEndRequest(
        @Schema(description = "Session ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("session_id") String sessionId,
        @Schema(description = "Project path")
        @JsonProperty("cwd") String cwd,
        @Schema(description = "Last assistant message for summary generation")
        @JsonProperty("last_assistant_message") String lastAssistantMessage
    ) {}

    @Schema(description = "User prompt event")
    public record UserPromptRequest(
        @Schema(description = "Session ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("session_id") String sessionId,
        @Schema(description = "User prompt text", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("prompt_text") String promptText,
        @Schema(description = "Project path")
        @JsonProperty("cwd") String cwd,
        @Schema(description = "Prompt number for ordering")
        @JsonProperty("prompt_number") Integer promptNumber
    ) {}

    @Schema(description = "Direct observation creation request (V14)")
    public record ObservationCreateRequest(
        @Schema(description = "Content session ID (or session_id alias)", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("content_session_id") String contentSessionId,
        @Schema(description = "Session ID alias (alternative to content_session_id)")
        @JsonProperty("session_id") String sessionId,
        @Schema(description = "Project path (or cwd alias)", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("project_path") String projectPath,
        @Schema(description = "Project path alias (alternative to project_path)")
        @JsonProperty("cwd") String cwd,
        @Schema(description = "Observation type (e.g., 'feature', 'bugfix')")
        @JsonProperty("type") String type,
        @Schema(description = "Observation title")
        @JsonProperty("title") String title,
        @Schema(description = "Observation subtitle")
        @JsonProperty("subtitle") String subtitle,
        @Schema(description = "Observation narrative (or content alias)")
        @JsonProperty("narrative") String narrative,
        @Schema(description = "Content alias for narrative")
        @JsonProperty("content") String content,
        @Schema(description = "List of factual statements")
        @JsonProperty("facts") List<String> facts,
        @Schema(description = "List of concept tags")
        @JsonProperty("concepts") List<String> concepts,
        @Schema(description = "Source attribution")
        @JsonProperty("source") String source,
        @Schema(description = "Structured extracted data")
        @JsonProperty("extractedData") Map<String, Object> extractedData,
        @Schema(description = "List of files read")
        @JsonProperty("files_read") List<String> filesRead,
        @Schema(description = "List of files modified")
        @JsonProperty("files_modified") List<String> filesModified,
        @Schema(description = "Prompt number")
        @JsonProperty("prompt_number") Integer promptNumber
    ) {}

    // ==================== Memory ====================

    @Schema(description = "Experience retrieval request")
    public record ExperienceRequest(
        @Schema(description = "Task or question to find relevant experiences for", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("task") String task,
        @Schema(description = "Project path for scoping")
        @JsonProperty("project") String project,
        @Schema(description = "Max experiences to return")
        @JsonProperty("count") Integer count,
        @Schema(description = "Filter by source (e.g., 'manual', 'tool_result')")
        @JsonProperty("source") String source,
        @Schema(description = "Filter to experiences containing these concepts")
        @JsonProperty("requiredConcepts") List<String> requiredConcepts,
        @Schema(description = "User ID for multi-user isolation")
        @JsonProperty("userId") String userId
    ) {}

    @Schema(description = "ICL prompt build request")
    public record ICLPromptRequest(
        @Schema(description = "Current task/question for context retrieval", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("task") String task,
        @Schema(description = "Project path for scoping")
        @JsonProperty("project") String project,
        @Schema(description = "Max prompt length (0 = backend default ~4000)")
        @JsonProperty("maxChars") Integer maxChars,
        @Schema(description = "User ID for multi-user isolation")
        @JsonProperty("userId") String userId
    ) {}

    @Schema(description = "Feedback submission request (not yet implemented)")
    public record FeedbackRequest(
        @Schema(description = "UUID of the observation to provide feedback for", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("observationId") String observationId,
        @Schema(description = "Feedback type (e.g., 'SUCCESS', 'FAILURE')", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("feedbackType") String feedbackType,
        @Schema(description = "Optional feedback comment")
        @JsonProperty("comment") String comment
    ) {}

    @Schema(description = "Partial observation update (PATCH semantics)")
    public record ObservationUpdateRequest(
        @Schema(description = "Observation title")
        @JsonProperty("title") String title,
        @Schema(description = "Observation subtitle")
        @JsonProperty("subtitle") String subtitle,
        @Schema(description = "Observation content/narrative")
        @JsonProperty("content") String content,
        @Schema(description = "List of factual statements")
        @JsonProperty("facts") List<String> facts,
        @Schema(description = "List of concept tags")
        @JsonProperty("concepts") List<String> concepts,
        @Schema(description = "Source attribution")
        @JsonProperty("source") String source,
        @Schema(description = "Structured extracted data")
        @JsonProperty("extractedData") Map<String, Object> extractedData
    ) {}

    // ==================== Cursor ====================

    @Schema(description = "Cursor project registration request")
    public record CursorRegisterRequest(
        @Schema(description = "Unique project identifier", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("projectName") String projectName,
        @Schema(description = "Absolute path to project root", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("workspacePath") String workspacePath
    ) {}

    @Schema(description = "Custom context update request")
    public record CursorCustomContextRequest(
        @Schema(description = "Custom context string to write to Cursor rules file")
        @JsonProperty("context") String context
    ) {}

    // ==================== Batch ====================

    @Schema(description = "Batch observation retrieval request")
    public record BatchObservationsRequest(
        @Schema(description = "List of observation UUIDs to retrieve", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("ids") List<String> ids,
        @Schema(description = "Optional project filter")
        @JsonProperty("project") String project,
        @Schema(description = "Sort order (e.g., 'created_at')")
        @JsonProperty("orderBy") String orderBy,
        @Schema(description = "Max results to return")
        @JsonProperty("limit") Integer limit
    ) {}

    @Schema(description = "Batch session retrieval request")
    public record BatchSessionsRequest(
        @Schema(description = "List of Claude Code session ID strings to retrieve", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("contentSessionIds") List<String> contentSessionIds
    ) {}

    // ==================== Viewer ====================

    @Schema(description = "Mode switch request")
    public record ModeSwitchRequest(
        @Schema(description = "Mode ID to activate (e.g., 'code', 'code--zh')", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("mode") String mode
    ) {}

    @Schema(description = "Context generation request")
    public record ContextGenerateRequest(
        @Schema(description = "Project path (defaults to current working directory if empty)")
        @JsonProperty("project_path") String projectPath
    ) {}
}
