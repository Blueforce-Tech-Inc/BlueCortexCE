package com.ablueforce.cortexce.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ablueforce.cortexce.util.SessionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Session Entity - represents a Claude Code session.
 *
 * NOTE: Session ID Architecture History
 * ================================
 * This entity has THREE identifier columns due to TypeScript original design:
 *
 * 1. id (UUID) - Physical primary key, stable across migrations
 *    - Used by: mem_pending_messages.session_db_id (FK)
 *    - Design: ✅ Correct - UUID FK is more stable than business keys
 *
 * 2. contentSessionId - Business key from Claude Code
 *    - Used by: mem_user_prompts.content_session_id (FK)
 *    - Design: ✅ Correct - Natural key from external system
 *
 * 3. memorySessionId - Legacy field from @anthropic-ai/claude-agent-sdk
 *    - Used by: mem_observations.memory_session_id (FK)
 *    - Used by: mem_summaries.memory_session_id (FK)
 *    - Design: ⚠️ REDUNDANT - In Java Port, this equals contentSessionId
 *    - History: In TypeScript SDK, memorySessionId is generated LATER (after first response)
 *              requiring complex dual-ID handling. Java Port simplified this by
 *              setting memorySessionId = contentSessionId at initialization.
 *
 * WHY THE REDUNDANCY EXISTS:
 * -------------------------
 * The TypeScript original uses @anthropic-ai/claude-agent-sdk which internally
 * generates its own session_id (memorySessionId) AFTER the first LLM response.
 * This created complex dual-ID architecture requiring careful handling:
 *   - contentSessionId: Available immediately from Claude Code
 *   - memorySessionId: NULL initially, captured after first response
 *   - Observation storage: Must use contentSessionId until memorySessionId is available
 *
 * Java Port Decision:
 * Since we don't use the SDK (direct DeepSeek API calls), there's no SDK-internal
 * session_id concept. We set memorySessionId = contentSessionId at initialization.
 * This maintains API compatibility with TypeScript version while simplifying logic.
 *
 * FUTURE CLEANUP (v2.0 or later):
 * -------------------------------
 * Consider removing memorySessionId and migrating FK references:
 *   1. Add content_session_id to mem_observations/summaries
 *   2. Copy data from memory_session_id to content_session_id
 *   3. Create FK to mem_sessions(content_session_id)
 *   4. Drop memory_session_id column and FKs
 *
 * Impact: ~9 files (Entity, Repository, Service layers) + 2 DB migrations
 * Risk: High - involves FK constraints and data migration
 * Recommendation: Defer until major version upgrade
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

    /**
     * Legacy field from TypeScript SDK architecture.
     *
     * In Java Port: This ALWAYS equals contentSessionId (set at initialization).
     * No dual-ID complexity because we don't use @anthropic-ai/claude-agent-sdk.
     *
     * Referenced by:
     *   - mem_observations.memory_session_id (FK)
     *   - mem_summaries.memory_session_id (FK)
     *
     * TODO: Consider migrating to content_session_id in future version
     */
    @Column(name = "memory_session_id")
    private String memorySessionId;

    @Column(name = "project_path", nullable = false)
    @NotBlank(message = "Project path cannot be blank")
    @JsonProperty("project")
    private String projectPath;

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

    public String getMemorySessionId() { return memorySessionId; }
    public void setMemorySessionId(String memorySessionId) { this.memorySessionId = memorySessionId; }

    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }

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
