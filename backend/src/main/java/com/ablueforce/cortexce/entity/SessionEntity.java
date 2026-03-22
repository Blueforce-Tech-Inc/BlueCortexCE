package com.ablueforce.cortexce.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ablueforce.cortexce.util.SessionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Session Entity — Claude Code session.
 * <p>
 * Identifiers:
 * <ul>
 *   <li>{@code id} — UUID primary key; used by {@code mem_pending_messages.session_db_id}</li>
 *   <li>{@code contentSessionId} — business key from Claude Code; used for prompts,
 *       observations, and summaries (FK on {@code mem_sessions.content_session_id})</li>
 * </ul>
 */
@Entity
@Table(name = "mem_sessions")
public class SessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    /**
     * Business key from Claude Code - the session identifier passed to hooks.
     * Unique across all sessions, provided by Claude Code CLI.
     */
    @Column(name = "content_session_id", unique = true, nullable = false)
    @NotBlank(message = "Content session ID cannot be blank")
    @JsonProperty("session_id")
    private String contentSessionId;

    @Column(name = "project_path", nullable = false)
    @NotBlank(message = "Project path cannot be blank")
    @JsonProperty("project")
    private String projectPath;

    /**
     * User identifier for multi-user support. Nullable — null means single-user mode
     * (e.g., Claude Code hooks). Set by SDK callers to associate sessions with users.
     * Phase 3: Used by extraction to group observations by user.
     */
    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_prompt")
    private String userPrompt;

    @Column(name = "last_assistant_message", columnDefinition = "TEXT")
    // P1: Use TEXT type for large content instead of VARCHAR with length limit
    private String lastAssistantMessage;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "started_at_epoch", nullable = false)
    private Long startedAtEpoch;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completed_at_epoch")
    private Long completedAtEpoch;

    @Column(name = "status")
    private String status = SessionStatus.ACTIVE;

    // P2-1: Context caching fields
    @Column(name = "cached_context", columnDefinition = "TEXT")
    private String cachedContext;

    @Column(name = "context_refreshed_at_epoch")
    private Long contextRefreshedAtEpoch;

    @Column(name = "needs_context_refresh")
    private Boolean needsContextRefresh = false;

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getContentSessionId() { return contentSessionId; }
    public void setContentSessionId(String contentSessionId) { this.contentSessionId = contentSessionId; }

    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserPrompt() { return userPrompt; }
    public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }

    public String getLastAssistantMessage() { return lastAssistantMessage; }
    public void setLastAssistantMessage(String lastAssistantMessage) { this.lastAssistantMessage = lastAssistantMessage; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public Long getStartedAtEpoch() { return startedAtEpoch; }
    public void setStartedAtEpoch(Long startedAtEpoch) { this.startedAtEpoch = startedAtEpoch; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public Long getCompletedAtEpoch() { return completedAtEpoch; }
    public void setCompletedAtEpoch(Long completedAtEpoch) { this.completedAtEpoch = completedAtEpoch; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // P2-1: Context caching getters and setters

    public String getCachedContext() { return cachedContext; }
    public void setCachedContext(String cachedContext) { this.cachedContext = cachedContext; }

    public Long getContextRefreshedAtEpoch() { return contextRefreshedAtEpoch; }
    public void setContextRefreshedAtEpoch(Long contextRefreshedAtEpoch) { this.contextRefreshedAtEpoch = contextRefreshedAtEpoch; }

    public Boolean getNeedsContextRefresh() { return needsContextRefresh; }
    public void setNeedsContextRefresh(Boolean needsContextRefresh) { this.needsContextRefresh = needsContextRefresh; }
}
